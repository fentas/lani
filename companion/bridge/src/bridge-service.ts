// The bridge as a service of its own (docs/plans/04-bridge-service.md): the app's HTTP API on the learner's port, the
// tutor session's shim on the control socket, and a clean stop, so `lani-bridge restart` deploys new code in a few
// seconds without a tutor restart.
import { join } from 'node:path'
import { ChannelQueue, channelCapabilities, listed, type ChannelOptions } from './channel'
import { spawn } from './config'
import { channelFile, controlSocket, listenControl, PROTOCOL, statusFile, writeJson, type ChannelSnapshot, type ServiceHello } from './control'
import type { Ctx, Feature } from './feature'
import { router, serve, type Role, type Route } from './http'

export type ServiceParts = {
  ctx: Ctx
  features: Feature[]
  channel: ChannelOptions
  routes: Route[]
  roleOf: (req: Request) => Role
  /** The data, the culture and the profile, for the log. */
  where: string
}

/** How long a stop waits for running requests and tool calls. */
const drainMs = () => Number(process.env.LANI_DRAIN_MS ?? 5_000)

export async function runService(p: ServiceParts) {
  const { ctx } = p
  const { cfg, log, profile } = ctx
  // stderr is the tmux pane or the journal: one that is gone must not turn every log line into an error
  process.stderr.on('error', () => {})
  const socket = controlSocket(cfg.appDir)
  const startedAt = new Date().toISOString()
  const code = commit(cfg.projectDir)
  const supervisor = process.env.LANI_BRIDGE_SUPERVISOR || (process.env.INVOCATION_ID ? 'systemd' : undefined)
  const instructions = () => (typeof p.channel.instructions === 'function' ? p.channel.instructions() : p.channel.instructions)

  // What the phone hasn't fetched yet, and what waits for the tutor, from before the restart.
  ctx.events.persist(join(cfg.appDir, 'events.json'), log)
  ctx.channel.useQueue(new ChannelQueue(join(cfg.appDir, 'channel-queue.json'), log))

  // The app's API first. When its port is taken (the classic bridge still runs, or another service), stop here: the
  // supervisor tries again, so the service takes the port as soon as it is free.
  let requests = 0
  const route = router(p.routes, p.roleOf, log)
  const http = serve({
    host: cfg.host,
    port: cfg.port,
    log,
    fetch: async req => {
      requests++
      try {
        return await route(req)
      } finally {
        requests-- // an event stream counts until its answer starts, not while it streams
      }
    },
  })
  if (!http) {
    log(`port ${cfg.port} is taken: not starting (the supervisor tries again)`)
    process.exit(1)
  }

  const hello: ServiceHello = { protocol: PROTOCOL, pid: process.pid, started_at: startedAt, profile: profile.id }
  let control: { close: () => void }
  try {
    control = await listenControl(socket, t => void ctx.channel.accept(t, p.channel, hello, log).catch(e => log(`control connection: ${(e as Error).message}`)))
  } catch (e) {
    log(`control socket: ${(e as Error).message}`)
    process.exit(1)
  }

  // For a shim that starts while this service is down: the channel as it was.
  const tools = listed(p.channel.tools)
  const save = (path: string, value: unknown) => {
    try {
      writeJson(path, value)
    } catch (e) {
      log(`${path} not written: ${(e as Error).message}`)
    }
  }
  save(channelFile(socket), { instructions: instructions(), experimental: channelCapabilities(p.channel.permissionRelay), tools, at: startedAt } satisfies ChannelSnapshot)

  let state: 'running' | 'stopping' | 'stopped' = 'running'
  const status = () =>
    save(statusFile(socket), {
      schema: 'lani.bridge-status/v1',
      state,
      pid: process.pid,
      profile: profile.id,
      port: cfg.port,
      socket,
      data: cfg.dataDir,
      started_at: startedAt,
      ...(code ? { code } : {}),
      // who starts it again after a stop: lani-bridge's loop ("run"), systemd, or nobody (run by hand)
      ...(supervisor ? { supervisor } : {}),
      session: ctx.channel.attached ?? null,
      queued: ctx.channel.queued,
      tools: tools.map(t => t.name),
      updated_at: new Date().toISOString(),
    })
  ctx.channel.onChange = status
  status()
  for (const f of p.features) f.start?.()
  log(`bridge service${code ? ` ${code}` : ''}: app API on http://${cfg.host}:${cfg.port}, the tutor's shim on ${socket}, ${p.where}`)

  // A stop (lani-bridge restart or stop, systemd, Ctrl-C in its tmux pane): no new connections or tool calls (the shim
  // retries those on the next service), the running ones finish, the buffers are saved, the shim is let go.
  let stopping = false
  const stop = async (why: string) => {
    if (stopping) return
    stopping = true
    log(`${why}: stopping`)
    state = 'stopping'
    ctx.channel.draining = true
    control.close()
    void http.stop()
    status()
    const until = Date.now() + drainMs()
    while ((requests > 0 || ctx.channel.inflight > 0) && Date.now() < until) await Bun.sleep(25)
    if (requests || ctx.channel.inflight) log(`stopping with ${requests} request(s) and ${ctx.channel.inflight} tool call(s) still running`)
    ctx.events.flush()
    ctx.channel.onChange = undefined
    await ctx.channel.detach()
    state = 'stopped'
    status()
    process.exit(0)
  }
  for (const sig of ['SIGTERM', 'SIGINT', 'SIGHUP'] as const) process.on(sig, () => void stop(sig))
}

/** The commit the checkout is at (what this service runs), when git knows. */
function commit(projectDir: string): string | undefined {
  const p = spawn(projectDir, ['git', 'rev-parse', '--short', 'HEAD'])
  return p.exitCode === 0 ? p.stdout.trim() || undefined : undefined
}
