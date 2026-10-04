// The tutor session's shim (docs/plans/04-bridge-service.md): what Claude Code spawns over stdio when the bridge runs
// as a service (src/index.ts with LANI_BRIDGE_SHIM=1). It forwards everything to the service over the control socket
// (src/control.ts) and holds almost nothing itself: the last tool list, the session's messages while the service is
// down, and whether the session has had a conversation yet.
//
// It is loaded once for the whole tutor session: a change here needs a tutor restart, so keep it small. It never exits
// on its own (Claude Code doesn't start a stdio server again); it ends when Claude Code closes its stdin.
import { randomUUID } from 'node:crypto'
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { Server } from '@modelcontextprotocol/sdk/server/index.js'
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js'
import { CallToolRequestSchema, ErrorCode, ListToolsRequestSchema, type CallToolRequest, type Notification, type Tool } from '@modelcontextprotocol/sdk/types.js'
import { loadConfig } from './config'
import { channelFile, connectSocket, controlSocket, FORWARDED, PROTOCOL, readJson, REPLACED, RESTARTING, SHIM_CAPABILITY, type ChannelSnapshot, type ShimHello } from './control'

const VERSION = '1.0.0'
const env = process.env
const log = (...a: unknown[]) => console.error('[lani-shim]', ...a)
const num = (v: string | undefined, fallback: number) => (v && Number.isFinite(Number(v)) ? Number(v) : fallback)
/** How long the shim waits for the service before it answers Claude Code's initialize from the cached channel. */
const START_WAIT_MS = num(env.LANI_SHIM_START_WAIT_MS, 8_000)
/** How long a tool call waits for a service that is down (a restart takes a few seconds) before it fails. */
const CALL_WAIT_MS = num(env.LANI_SHIM_CALL_WAIT_MS, 10_000)
/** How long a tool call may run in the service; Claude Code has its own limit. */
const CALL_TIMEOUT_MS = 30 * 60_000
/** The session's messages kept for the service while it is down (permission prompts). */
const MAX_HELD = 50
/** Events kept for a session that hasn't initialized yet: the service's whole queue (200) comes at once when the shim connects. */
const MAX_EARLY = 500

// A long-running stdio server: nothing may end it but Claude Code closing its stdin.
process.on('unhandledRejection', e => log(`unhandled rejection: ${(e as Error)?.stack ?? e}`))
process.on('uncaughtException', e => log(`uncaught exception: ${e.stack ?? e}`))

const socket = env.LANI_BRIDGE_SOCKET?.trim() || controlSocket(loadConfig(env).appDir, env)
const session = randomUUID()
const child = ['1', 'true', 'yes'].includes((env.LANI_CHILD ?? '').toLowerCase())

/** The service now (undefined while it is down). */
let conn: Client | undefined
/** The tools as the service last listed them. */
let tools: Tool[] = []
/** Whether the session has had a conversation's event (the service asks for the guide in the first one only). */
let heard = false
/** Another tutor session took over this learner's channel: this one stays, without the service. */
let replaced = false
/** Claude Code has initialized: events go to it; before that they wait in toSession. */
let ready = false
const toSession: Notification[] = []
const toService: Notification[] = []
/** The instructions this session started with (they can't change mid-session). */
let instructions: string | undefined
let server: Server | undefined

const keep = (list: Notification[], n: Notification, max = MAX_HELD) => {
  list.push(n)
  if (list.length > max) list.shift()
}
const failed = (text: string) => ({ content: [{ type: 'text' as const, text }], isError: true })
const same = (a: Tool[], b: Tool[]) => JSON.stringify(a) === JSON.stringify(b)

/** Waits until [done] (checked every 50 ms), at most [ms]. */
async function until(done: () => boolean, ms: number, signal?: AbortSignal): Promise<boolean> {
  const end = Date.now() + ms
  while (!done()) {
    if (Date.now() >= end || signal?.aborted) return false
    await Bun.sleep(50)
  }
  return true
}

/** One connection to the service: the handshake with this session's hello, then its tools. */
async function connect(): Promise<Client> {
  const hello: ShimHello = { protocol: PROTOCOL, session, heard, pid: process.pid }
  const client = new Client({ name: 'lani-shim', version: VERSION }, { capabilities: { experimental: { [SHIM_CAPABILITY]: hello } } })
  client.fallbackNotificationHandler = async n => fromService(n)
  client.onclose = () => lost(client)
  await client.connect(await connectSocket(socket, 1_000))
  const next = (await client.listTools()).tools
  if (instructions !== undefined && client.getInstructions() !== instructions) log("the service's channel instructions changed: this session keeps the ones it started with (the channel_guide tool is live)")
  const changed = !same(tools, next)
  tools = next
  conn = client
  if (changed && ready) {
    log(`the tools changed (${next.length} now): telling the session`)
    server?.sendToolListChanged().catch(e => log(`list_changed: ${(e as Error).message}`))
  }
  for (const n of toService.splice(0)) client.notification(n).catch(() => keep(toService, n))
  log(`connected to the bridge service on ${socket}`)
  return client
}

function lost(client: Client) {
  if (conn !== client) return
  conn = undefined
  if (replaced) return
  log('the bridge service went away: reconnecting')
  void reconnect()
}

let reconnecting = false
/**
 * Tries every 0.25 s for 10 s (a restart takes a second or two), then doubling to 5 s at most, until the service is back
 * or another session took over.
 */
async function reconnect() {
  if (reconnecting) return
  reconnecting = true
  const since = Date.now()
  let delay = 250
  while (!conn && !replaced) {
    try {
      await connect()
    } catch {
      await Bun.sleep(delay)
      if (Date.now() - since > 10_000) delay = Math.min(delay * 2, 5_000)
    }
  }
  reconnecting = false
}

async function fromService(n: Notification) {
  if (n.method === REPLACED) {
    replaced = true
    log("another tutor session took over this learner's channel: this one no longer reconnects")
    return
  }
  if (!FORWARDED.test(n.method)) return
  if (n.method === 'notifications/claude/channel' && (n.params as any)?.meta?.conversation_id) heard = true
  const out = { method: n.method, params: n.params }
  if (!ready || !server) return keep(toSession, out, MAX_EARLY)
  server.notification(out).catch(e => log(`to the session: ${(e as Error).message}`))
}

async function call(params: CallToolRequest['params'], signal: AbortSignal, retried = false): Promise<any> {
  if (replaced) return failed(`Another tutor session took over this learner's channel, so this session's lani tools no longer work. ${params.name} didn't run.`)
  if (!conn) await until(() => !!conn || replaced, CALL_WAIT_MS, signal)
  const client = conn
  if (!client) {
    return failed(
      `The Lani bridge service isn't running (it may be restarting). Nothing was done: ${params.name} didn't run. Try again in a minute; if it stays down, Jan can check it with companion/bin/lani-bridge status.`,
    )
  }
  try {
    return await client.callTool(params, undefined, { signal, timeout: CALL_TIMEOUT_MS })
  } catch (e) {
    if (signal.aborted) throw e
    const code = (e as { code?: number }).code
    if (code === RESTARTING && !retried) {
      // The service is stopping and didn't run it: once more, on the next one.
      await until(() => conn !== client, CALL_WAIT_MS, signal)
      return call(params, signal, true)
    }
    if (code === ErrorCode.ConnectionClosed) {
      return failed(`The Lani bridge service stopped while ${params.name} ran, so it may or may not have taken effect. Check (with a list_ or get_ tool) before you repeat it.`)
    }
    throw e
  }
}

// --- start: the channel as the service gives it, else as it last gave it, else a minimal one --------------------------

let delay = 100
const end = Date.now() + START_WAIT_MS
while (!conn && Date.now() < end) {
  try {
    await connect()
  } catch {
    await Bun.sleep(delay)
    delay = Math.min(delay * 2, 1_000)
  }
}
let experimental: Record<string, object> | undefined
if (conn) {
  instructions = conn.getInstructions() ?? ''
  experimental = conn.getServerCapabilities()?.experimental
} else {
  const snapshot = readJson<ChannelSnapshot>(channelFile(socket))
  log(`the bridge service isn't answering on ${socket}: starting from ${snapshot ? `its channel of ${snapshot.at}` : 'a minimal channel'}; reconnecting`)
  if (snapshot) {
    instructions = snapshot.instructions
    experimental = snapshot.experimental
    tools = snapshot.tools as Tool[]
  } else {
    instructions = [
      "You are the learner's Lani language tutor. This channel connects you to the Lani Android app.",
      "The bridge service wasn't running when this session started: its tools appear once it is up.",
      'The app never sees terminal output. Answer every <channel source="lani"> message with the reply tool, passing the conversation_id from its tag. Call the channel_guide tool before your first reply.',
      ...(child ? ['The learner is a child: keep everything age-appropriate, simple and encouraging, and never ask for personal data.'] : []),
    ].join('\n\n')
    experimental = { 'claude/channel': {}, ...(child ? {} : { 'claude/channel/permission': {} }) }
  }
}
// Claude Code gets the channel's own capabilities (claude/*), not the service's hello.
const claudeCaps = Object.fromEntries(Object.entries(experimental ?? {}).filter(([k]) => k.startsWith('claude/')))

server = new Server({ name: 'lani', version: VERSION }, { capabilities: { experimental: claudeCaps, tools: { listChanged: true } }, instructions })
server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools }))
server.setRequestHandler(CallToolRequestSchema, async (req, extra) => call(req.params, extra.signal))
// The session's channel messages (the permission prompts) go to the service, or wait for it.
server.fallbackNotificationHandler = async n => {
  if (!FORWARDED.test(n.method)) return
  const out = { method: n.method, params: n.params }
  if (!conn) return keep(toService, out)
  conn.notification(out).catch(() => keep(toService, out))
}
server.oninitialized = () => {
  ready = true
  for (const n of toSession.splice(0)) server!.notification(n).catch(e => log(`to the session: ${(e as Error).message}`))
}
await server.connect(new StdioServerTransport())
// Claude Code closed the channel: the session is over.
process.stdin.on('end', () => process.exit(0))
if (!conn) void reconnect()
