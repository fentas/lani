// The MCP side: the channel server, its instructions (a paragraph per feature) and the tool registry. Everything the
// session hears from the app goes through notify().
//
// Two ways to reach the session (docs/plans/04-bridge-service.md):
// - classic: Claude Code spawns the bridge over stdio (connect()), one server for the bridge's life;
// - service: the bridge runs on its own and the session's shim connects over a unix socket (accept()), a server per
//   connection; the newest is the channel. While no session is attached, events wait in a queue (useQueue()).
import { readFileSync, renameSync, writeFileSync } from 'node:fs'
import { Server } from '@modelcontextprotocol/sdk/server/index.js'
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js'
import type { Transport } from '@modelcontextprotocol/sdk/shared/transport.js'
import { CallToolRequestSchema, ListToolsRequestSchema, McpError } from '@modelcontextprotocol/sdk/types.js'
import { z } from 'zod'
import type { Log } from './config'
import { REPLACED, RESTARTING, SERVICE_CAPABILITY, SHIM_CAPABILITY, type ServiceHello, type ShimHello } from './control'
import { HttpError } from './http'
import { channelEvent, listed, runTool, withHint, type AppInbox, type ChannelPort, type PermissionRequest, type Tool, type ToolResult } from './tool'

// the MCP-free parts (src/tool.ts), where they were before
export { badNote, fail, listed, neutralizeTags, noteIn, ok, replyHint, type PermissionRequest, type Tool, type ToolResult } from './tool'

const permissionRequest = z.object({
  method: z.literal('notifications/claude/channel/permission_request'),
  params: z.object({
    request_id: z.string(),
    tool_name: z.string(),
    description: z.string(),
    input_preview: z.string(),
  }),
})

export type ChannelOptions = {
  /** The channel's instructions; a function in the service, asked again for every session that attaches. */
  instructions: string | (() => string)
  tools: Tool[]
  /** Tool approvals the session asks for. */
  onPermissionRequest: (p: PermissionRequest) => void
  permissionRelay?: boolean
}

/** The experimental capabilities the channel declares: without the permission one, Claude Code asks for tool approvals only in the terminal. */
export const channelCapabilities = (permissionRelay?: boolean): Record<string, object> => ({
  'claude/channel': {},
  ...(permissionRelay === false ? {} : { 'claude/channel/permission': {} }),
})

/**
 * What a Claude Code session that starts while the app's own tutor is attached reads at the end of its instructions:
 * the channel's events go to the app (features/tutor.ts, docs/plans/05-built/tutor-live.md), so it hears nothing new
 * until that tutor detaches.
 */
export const APP_TUTOR_NOTE =
  "\n\nRight now the Lani app's own tutor is attached (the learner chose \"In the app\" as who answers): the app's events go to it, and this session gets none until the learner switches back to \"Claude Code on my node\". The tools still work."

/** The service's session: the shim attached now (from its hello). */
export type Attached = { since: string; client: string; session?: string; pid?: number; protocol?: number }

type Queued = { content: string; meta: Record<string, string>; at: number }

/**
 * Events for the session while none is attached (the service between tutor sessions): kept in order in [path], at
 * most [max], at most [maxAgeMs] old when delivered.
 */
export class ChannelQueue {
  private items: Queued[] = []

  constructor(
    private readonly path: string,
    private readonly log: Log,
    private readonly max = 200,
    private readonly maxAgeMs = 3 * 86_400_000,
  ) {
    try {
      const kept = JSON.parse(readFileSync(path, 'utf8'))
      this.items = (Array.isArray(kept) ? kept : []).filter((q: any) => typeof q?.content === 'string' && q.meta && typeof q.at === 'number')
    } catch {} // none
  }

  get size() {
    return this.items.length
  }

  push(q: Queued) {
    this.items.push(q)
    if (this.items.length > this.max) this.log(`channel queue: full, the oldest event is dropped (${this.items.shift()!.meta.kind ?? 'event'})`)
    this.save()
  }

  /** The events to deliver now, oldest first; the stale ones are dropped (logged). */
  take(): Queued[] {
    const now = Date.now()
    const fresh = this.items.filter(q => now - q.at <= this.maxAgeMs)
    if (fresh.length < this.items.length) this.log(`channel queue: ${this.items.length - fresh.length} event(s) older than ${this.maxAgeMs / 86_400_000} days dropped`)
    this.items = []
    this.save()
    return fresh
  }

  /** Puts [qs] back in front (a delivery that failed). */
  unshift(qs: Queued[]) {
    this.items = [...qs, ...this.items].slice(-this.max)
    this.save()
  }

  save() {
    try {
      writeFileSync(`${this.path}.tmp`, JSON.stringify(this.items))
      renameSync(`${this.path}.tmp`, this.path)
    } catch (e) {
      this.log(`channel queue not saved: ${(e as Error).message}`)
    }
  }
}

export class Channel implements ChannelPort {
  /** The session's server: the stdio one (classic), or the shim's that attached last (service). */
  private server?: Server
  private queue?: ChannelQueue
  /** Service: the session attached now, for the status. */
  attached?: Attached
  /** Tool calls running now (the service waits for them before it stops). */
  inflight = 0
  private stopping = false
  /** Told when the service starts stopping (the app tutor's long polls end at once). */
  private readonly onDrain = new Set<() => void>()
  /** Service: stopping; new tool calls are refused as RESTARTING, and the shim retries them on the next service. */
  get draining() {
    return this.stopping
  }
  set draining(on: boolean) {
    this.stopping = on
    if (on) for (const f of this.onDrain) f()
  }
  /** Runs [f] when the service starts stopping. */
  whenDraining(f: () => void) {
    this.onDrain.add(f)
  }
  /**
   * The app's own tutor's inbox while it is attached (features/tutor.ts, POST /tutor/attach): every notification goes
   * there instead of to the session, until it detaches.
   */
  private app?: AppInbox
  /** Service: a session attached or left, or the queue changed. */
  onChange?: () => void
  /** Whether the session has had a conversation's event from this bridge yet (the first one also asks for the guide). */
  private heard = false

  private createServer(o: ChannelOptions, service?: ServiceHello): Server {
    const server = new Server(
      { name: 'lani', version: '0.1.0' },
      {
        capabilities: {
          experimental: { ...channelCapabilities(o.permissionRelay), ...(service ? { [SERVICE_CAPABILITY]: service } : {}) },
          tools: {},
        },
        instructions: (typeof o.instructions === 'function' ? o.instructions() : o.instructions) + (this.app ? APP_TUTOR_NOTE : ''),
      },
    )
    const byName = new Map(o.tools.map(t => [t.name, t]))
    server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools: listed(o.tools) }))
    server.setRequestHandler(CallToolRequestSchema, async req => this.call(byName, req.params.name, (req.params.arguments ?? {}) as Record<string, any>))
    // Permission relay: tool approvals show up in the app; the app answers via POST /permission.
    server.setNotificationHandler(permissionRequest, async ({ params }) => o.onPermissionRequest(params))
    return server
  }

  /**
   * Runs tool [name] of [byName] with [args], as a session's tool call: refused while the service is stopping
   * (RESTARTING: the caller tries again on the next one), counted in [inflight] while it runs. The session's calls (MCP)
   * and the app tutor's (POST /tutor/tools/:name) both come here.
   */
  call = async (byName: Map<string, Tool>, name: string, args: Record<string, any>): Promise<ToolResult> => {
    if (this.draining) throw new McpError(RESTARTING, 'the bridge service is restarting')
    if (!byName.has(name)) return runTool(byName, name, args)
    this.inflight++
    try {
      return await runTool(byName, name, args)
    } finally {
      this.inflight--
    }
  }

  /** Classic: starts the channel on stdio (Claude Code spawned this bridge). */
  async connect(o: ChannelOptions, transport: Transport = new StdioServerTransport()) {
    const server = this.createServer(o)
    this.server = server
    await server.connect(transport)
  }

  /** Service: events wait in [queue] while no session is attached. */
  useQueue(queue: ChannelQueue) {
    this.queue = queue
    if (this.app) this.moveQueued()
  }

  /**
   * The app's own tutor attaches ([inbox], who is attached set in it): from now on every notification waits in [inbox]
   * instead of reaching the session, and what the queue held for the next session moves there too. Returns how many
   * moved. The session (a shim, or Claude Code on stdio) stays connected and its tools keep working; it hears nothing
   * new until [undivert].
   */
  divert = (inbox: AppInbox, log: Log): number => {
    this.app = inbox
    const moved = this.moveQueued()
    log(`the app's own tutor is attached (${inbox.attached?.who ?? 'app'}${inbox.attached?.device ? `, ${inbox.attached.device}` : ''}): the channel's events go to its inbox, the Claude Code session gets none until it detaches${moved ? `; ${moved} queued event(s) moved to it` : ''}`)
    this.onChange?.()
    return moved
  }

  /**
   * The app's tutor detached: notifications reach the session again, and what its inbox still held (not acknowledged)
   * goes to the session now (or the queue, with none attached), in order. Returns how many were given back.
   */
  undivert = async (log: Log): Promise<number> => {
    const inbox = this.app
    if (!inbox) return 0
    this.app = undefined
    const back = inbox.takeAll()
    log(`the app's own tutor detached: the channel's events go to the Claude Code session again${back.length ? `; ${back.length} unanswered event(s) given back to it` : ''}`)
    for (const q of back) {
      try {
        await this.notify(q.content, { ...q.meta, queued_at: q.meta.queued_at ?? new Date(q.at).toISOString() })
      } catch (e) {
        log(`an event of the app tutor's inbox couldn't be given back (${q.meta.kind ?? 'event'}): ${(e as Error).message}`)
      }
    }
    this.onChange?.()
    return back.length
  }

  /** The app's own tutor attached now (who, since, its phone), or undefined: for the service's status. */
  get appTutor(): AppInbox['attached'] {
    return this.app?.attached
  }

  /** The queue's events into the app tutor's inbox (they keep their time). */
  private moveQueued(): number {
    const inbox = this.app
    if (!inbox || !this.queue?.size) return 0
    const waiting = this.queue.take()
    for (const q of waiting) inbox.push(q.content, q.meta, q.at)
    return waiting.length
  }

  get queued() {
    return this.queue?.size ?? 0
  }

  /**
   * Service: serves one connection of a shim. Once it has initialized it is the channel; the one before (another tutor
   * session of this learner) is told it was replaced and closed. The queued events go to it.
   */
  async accept(transport: Transport, o: ChannelOptions, service: ServiceHello, log: Log) {
    const server = this.createServer(o, service)
    server.oninitialized = () => {
      const before = this.server
      const hello = server.getClientCapabilities()?.experimental?.[SHIM_CAPABILITY] as Partial<ShimHello> | undefined
      const client = server.getClientVersion()
      this.server = server
      this.heard = !!hello?.heard
      this.attached = {
        since: new Date().toISOString(),
        client: `${client?.name ?? '?'} ${client?.version ?? ''}`.trim(),
        ...(typeof hello?.session === 'string' ? { session: hello.session } : {}),
        ...(Number.isSafeInteger(hello?.pid) ? { pid: hello!.pid } : {}),
        ...(Number.isSafeInteger(hello?.protocol) ? { protocol: hello!.protocol } : {}),
      }
      log(`the tutor session attached (${this.attached.client}${this.attached.pid ? `, pid ${this.attached.pid}` : ''})`)
      if (before) {
        log('the session before it was replaced')
        before.notification({ method: REPLACED, params: {} }).catch(() => {})
        setTimeout(() => void before.close().catch(() => {}), 100)
      }
      this.onChange?.()
      void this.deliverQueued(log)
    }
    server.onclose = () => {
      if (this.server !== server) return
      this.server = undefined
      this.attached = undefined
      log('the tutor session left')
      this.onChange?.()
    }
    await server.connect(transport)
  }

  /** Service: closes the attached session's connection (the service is stopping; its shim reconnects to the next one). */
  async detach() {
    const s = this.server
    this.server = undefined
    this.attached = undefined
    await s?.close().catch(() => {})
  }

  private async deliverQueued(log: Log) {
    if (this.app) return // the app's own tutor has them (moveQueued)
    const waiting = this.queue?.take() ?? []
    if (!waiting.length) return
    log(`channel queue: ${waiting.length} event(s) to the session`)
    for (let i = 0; i < waiting.length; i++) {
      const q = waiting[i]
      try {
        await this.send(q.content, { ...q.meta, queued_at: new Date(q.at).toISOString() })
      } catch {
        this.queue!.unshift(waiting.slice(i)) // the session left again: they wait for the next
        break
      }
    }
    this.onChange?.()
  }

  private async send(content: string, meta: Record<string, string>) {
    if (!this.server) throw new Error('channel not connected')
    const text = withHint(content, meta, this.heard)
    await this.server.notification({ method: 'notifications/claude/channel', params: { content: text, meta } })
    if (meta.conversation_id) this.heard = true
  }

  /**
   * An event into the session: <channel source="lani" kind=… conversation_id=… msg_id=…>content</channel>.
   * The meta values become tag attributes, so they are limited to id characters; the content can't
   * open or close a <channel> tag of its own. In the service with no session attached, it waits in the queue
   * ('queued').
   */
  notify = async (content: string, meta: Record<string, string>): Promise<'sent' | 'queued' | 'app'> => {
    const { content: safe, meta: safeMeta } = channelEvent(content, meta)
    if (this.app) {
      // the app's own tutor answers now: its inbox, never the session (features/tutor.ts)
      this.app.push(safe, safeMeta)
      this.onChange?.()
      return 'app'
    }
    if (this.queue && !this.server) return this.enqueue(safe, safeMeta)
    try {
      await this.send(safe, safeMeta)
      return 'sent'
    } catch (e) {
      if (!this.queue) throw e
      return this.enqueue(safe, safeMeta) // the connection broke under it: the next session gets it
    }
  }

  private enqueue(content: string, meta: Record<string, string>): 'queued' {
    this.queue!.push({ content, meta, at: Date.now() })
    this.onChange?.()
    return 'queued'
  }

  /** Jan's answer to a tool approval prompt; 503 in the service when no session is attached. */
  permission = async (verdict: { request_id: string; behavior: 'allow' | 'deny' }): Promise<void> => {
    if (!this.server) {
      if (this.queue) throw new HttpError(503, 'the tutor session is not connected: answer the prompt in the terminal')
      throw new Error('channel not connected')
    }
    await this.server.notification({ method: 'notifications/claude/channel/permission', params: verdict })
  }
}
