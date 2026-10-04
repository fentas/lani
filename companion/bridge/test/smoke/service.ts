// Smoke checks: the bridge as a service and the tutor session's shim (docs/plans/04-bridge-service.md). A service runs on
// its own (test/fixtures/service-probe.ts: the real one with probe tools), and a scripted MCP client plays Claude Code,
// spawning the shim as .mcp.json does in a tutor session started in the service mode (src/index.ts with
// LANI_BRIDGE_SHIM=1): initialize, the tools, a call, the channel both ways, the permission relay, a restart mid-way
// (the drain, the reconnect, list_changed), a call during the outage, the queue for the tutor, the event buffer across
// restarts, a newer session taking over, a shim started while the service is down, a child's service beside it, and
// lani-bridge's loop and CLI (without tmux: the loop runs here). Ports: LANI_SMOKE_SERVICE_PORT (by default the
// smoke's + 8) and the next two.
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { ToolListChangedNotificationSchema } from '@modelcontextprotocol/sdk/types.js'
import type { Subprocess } from 'bun'
import { existsSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { loadConfig } from '../../src/config'
import { channelFile, statusFile } from '../../src/control'
import { initLearnerData } from '../../src/learners'
import { bridgeWhisper, check, culturesDir, curatedDir, modulesDir, port as smokePort, scenesDir, tempDir } from './harness'

const repo = resolve(import.meta.dir, '../../../..')
const src = resolve(import.meta.dir, '../../src')
const bin = join(repo, 'companion/bin')
const probe = resolve(import.meta.dir, '../fixtures/service-probe.ts')
const first = Number(process.env.LANI_SMOKE_SERVICE_PORT ?? smokePort + 8)
const token = 'service-smoke-token'
const auth = { authorization: `Bearer ${token}`, 'content-type': 'application/json' }
/** How long the shim's calls wait for a service that is down (the shim's default is 10 s). */
const CALL_WAIT_MS = 3_000

type Learner = { name: string; port: number; base: string; dataDir: string; socket: string; env: Record<string, string> }
type Tutor = { client: Client; notes: any[]; changed: () => number; pid?: number }

/** A learner of their own: data from the templates, a port, a short socket path (unix sockets allow 107 bytes). */
function learner(name: string, port: number, extra: Record<string, string>, child = false): Learner {
  const dataDir = join(tempDir(`lani-smoke-svc-${name.toLowerCase()}-`), 'data')
  initLearnerData(repo, dataDir, { name, target: 'sl', base: 'en', child })
  const socket = join(tempDir('fsvc-'), 'bridge.sock')
  const env: Record<string, string> = {}
  for (const [k, v] of Object.entries(process.env)) if (v !== undefined && !['TMUX', 'LANI_TUTOR', 'LANI_BRIDGE_SHIM', 'LANI_PROFILE', 'LANI_CHILD'].includes(k)) env[k] = v
  Object.assign(env, {
    LANI_DATA_DIR: dataDir, LANI_BRIDGE_PORT: String(port), LANI_BRIDGE_TOKEN: token, LANI_BRIDGE_SOCKET: socket, LANI_PACKS_DIR: curatedDir,
    LANI_MODULES_DIR: modulesDir, LANI_SCENES_DIR: scenesDir, LANI_CULTURES_DIR: culturesDir, LANI_RHYTHM: 'off', ELEVENLABS_API_KEY: '',
    LANI_GEPARD_URL: 'http://127.0.0.1:9', LANI_STT_URL: bridgeWhisper.url, LANI_DRAIN_MS: '3000', ...extra,
  })
  return { name, port, base: `http://127.0.0.1:${port}`, dataDir, socket, env }
}

const status = (l: { socket: string }) => JSON.parse(readFileSync(statusFile(l.socket), 'utf8'))
const post = (l: Learner, path: string, body: unknown) => fetch(`${l.base}${path}`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
const backlog = async (l: Learner, since = 0): Promise<{ id: number; event: any }[]> => (await fetch(`${l.base}/events/backlog?since=${since}`, { headers: auth })).json()

async function until(done: () => unknown, ms: number): Promise<boolean> {
  const end = Date.now() + ms
  for (;;) {
    if (await done()) return true
    if (Date.now() >= end) return false
    await Bun.sleep(50)
  }
}

const running: Subprocess[] = []
/** The service of [l] (the probe's), once its status says it runs and its API answers. */
async function startService(l: Learner, extra: Record<string, string> = {}): Promise<Subprocess> {
  const proc = Bun.spawn(['bun', probe], { env: { ...l.env, ...extra }, stdout: 'inherit', stderr: 'inherit' })
  running.push(proc)
  const up = await until(async () => {
    if (!existsSync(statusFile(l.socket))) return false
    const st = status(l)
    return st.pid === proc.pid && st.state === 'running' && (await fetch(`${l.base}/health`).then(r => r.ok, () => false))
  }, 15_000)
  if (!up) throw new Error(`the service of ${l.name} didn't start`)
  return proc
}

async function stopService(proc: Subprocess) {
  proc.kill('SIGTERM')
  return proc.exited
}

const tutors: Client[] = []
/** A scripted Claude Code: the shim spawned from src/index.ts with LANI_BRIDGE_SHIM=1, as .mcp.json in a service-mode session. */
async function shim(env: Record<string, string>, extra: Record<string, string> = {}): Promise<Tutor> {
  const notes: any[] = []
  let changed = 0
  const client = new Client({ name: 'claude-code-smoke', version: '0' })
  client.fallbackNotificationHandler = async n => void notes.push(n)
  client.setNotificationHandler(ToolListChangedNotificationSchema, async () => void changed++)
  const transport = new StdioClientTransport({ command: 'bun', args: [join(src, 'index.ts')], env: { ...env, LANI_BRIDGE_SHIM: '1', LANI_SHIM_CALL_WAIT_MS: String(CALL_WAIT_MS), ...extra }, stderr: 'inherit' })
  await client.connect(transport)
  tutors.push(client)
  return { client, notes, changed: () => changed, pid: transport.pid ?? undefined }
}

const text = (r: any): string => r?.content?.[0]?.text ?? ''
const heard = (t: Tutor, conversation: string) => t.notes.find(n => n.method === 'notifications/claude/channel' && n.params?.meta?.conversation_id === conversation)?.params

export default async function service() {
  const sam = learner('Sam', first, {})
  const kid = learner('Kid', first + 1, { LANI_PROFILE: 'kid', LANI_CHILD: '1' }, true)
  /** Stops lani-bridge's loop and its service when a check threw before `stop` ran. */
  let stopLoop = () => {}
  try {
    // --- the service alone ---------------------------------------------------------------------------------------
    // The default learner's service has neither LANI_TUTOR nor a port in its environment: it serves the app all the
    // same, and so runs the features' jobs for the app (scene variants, the dictionary download).
    const bare = { HOME: process.env.HOME, PATH: process.env.PATH, LANI_DATA_DIR: sam.dataDir }
    check('service: it serves the app without LANI_TUTOR or a port (the classic bridge then keeps only its tools)', loadConfig(bare, { service: true }).serveApp && !loadConfig(bare).serveApp)
    let svc = await startService(sam)
    const st0 = status(sam)
    check('service: it answers /health and writes its status beside the socket: running, its pid and port, no tutor yet, its 41 tools', st0.state === 'running' && st0.pid === svc.pid && st0.port === sam.port && st0.session === null && st0.tools.length === 41 && st0.tools[0] === 'channel_guide', st0)
    const early = await post(sam, '/message', { text: 'Dober dan, is anyone there?', conversation_id: 'early' })
    const earlyBody = await early.json()
    const noVerdict = await post(sam, '/permission', { request_id: 'abcde', behavior: 'allow' })
    check('service: with no tutor attached, a message waits for it (200, queued) and a permission answer is a 503', early.status === 200 && earlyBody.queued === true && typeof earlyBody.msg_id === 'string' && noVerdict.status === 503, { earlyBody, verdict: noVerdict.status })
    const kept = JSON.parse(readFileSync(join(sam.dataDir, 'app/channel-queue.json'), 'utf8'))
    check('service: the queue is kept in the data (channel-queue.json), and the status counts it', kept.length === 1 && kept[0].meta.conversation_id === 'early' && status(sam).queued === 1, kept)

    // --- a tutor session with the shim ---------------------------------------------------------------------------
    const t = await shim(sam.env)
    const caps = t.client.getServerCapabilities() as any
    check("shim: it declares the channel, the permission relay and tools.listChanged (not the service's own hello)", !!caps?.experimental?.['claude/channel'] && !!caps.experimental['claude/channel/permission'] && caps.tools?.listChanged === true && !caps.experimental['lani/service'], caps)
    const snapshot = JSON.parse(readFileSync(channelFile(sam.socket), 'utf8'))
    const instructions = t.client.getInstructions() ?? ''
    check("shim: its instructions are the service's channel rules, as in the snapshot beside the socket", instructions === snapshot.instructions && instructions.includes("Jan's Lani language tutor") && instructions.includes('channel_guide'), instructions.slice(0, 200))
    const tools = (await t.client.listTools()).tools
    check("shim: tools/list is the service's, the guide first", tools.length === 41 && tools[0]?.name === 'channel_guide' && tools.some(x => x.name === 'reply') && tools.some(x => x.name === 'smoke_slow'), tools.map(x => x.name))
    await until(() => heard(t, 'early'), 3_000)
    const queued = heard(t, 'early')
    check('shim: the message that waited arrives when the session attaches, with queued_at and the hint to read the guide first', !!queued && /^\d{4}-\d\d-\d\dT/.test(queued.meta.queued_at ?? '') && queued.content.includes('Dober dan') && queued.content.includes('call the channel_guide tool') && queued.meta.kind === 'chat', queued)
    await until(() => status(sam).session, 3_000)
    const st1 = status(sam)
    check("service: the status shows the tutor attached (the shim, its pid) and the queue empty", st1.session?.client?.startsWith('lani-shim') && st1.session.pid === t.pid && st1.queued === 0 && JSON.parse(readFileSync(join(sam.dataDir, 'app/channel-queue.json'), 'utf8')).length === 0, st1)

    const replied = await t.client.callTool({ name: 'reply', arguments: { conversation_id: 'early', text: 'Tukaj sem! 👋' } })
    check('shim: a tool call reaches the service: the reply is an event for the app', !replied.isError && (await backlog(sam)).some(b => b.event.type === 'reply' && b.event.text === 'Tukaj sem! 👋'), replied)
    const guide = text(await t.client.callTool({ name: 'channel_guide', arguments: {} }))
    check('shim: the whole guide comes through (far beyond the instructions)', guide.length > 10 * instructions.length && guide.includes('reply tool'), guide.length)
    await post(sam, '/message', { text: 'Kako se reče "thank you"?', conversation_id: 'c2' })
    await until(() => heard(t, 'c2'), 3_000)
    const live = heard(t, 'c2')
    check('channel, app → session: a message comes at once, without queued_at, and no longer asks for the guide', !!live && !live.meta.queued_at && live.meta.kind === 'chat' && live.content.includes('thank you') && !live.content.includes('call the channel_guide tool'), live)
    await t.client.notification({ method: 'notifications/claude/channel/permission_request', params: { request_id: 'pqrst', tool_name: 'Bash', description: 'List files', input_preview: 'ls' } })
    const prompted = await until(async () => (await backlog(sam)).some(b => b.event.type === 'permission_request' && b.event.request_id === 'pqrst' && b.event.tool_name === 'Bash'), 3_000)
    check('permission relay, session → app: the prompt is an event for the app', prompted)
    const answered = await post(sam, '/permission', { request_id: 'pqrst', behavior: 'allow' })
    const verdict = await until(() => t.notes.some(n => n.method === 'notifications/claude/channel/permission' && n.params?.request_id === 'pqrst' && n.params?.behavior === 'allow'), 3_000)
    check("permission relay, app → session: the app's answer reaches the session", answered.status === 200 && verdict)

    // --- a restart mid-way: the drain, the reconnect, list_changed, the app's events kept ------------------------------
    const lastSeen = (await backlog(sam)).at(-1)!.id
    // the phone is away: this reply waits in the event buffer
    await t.client.callTool({ name: 'reply', arguments: { conversation_id: 'c2', text: 'Hvala! 🙏' } })
    const slow = t.client.callTool({ name: 'smoke_slow', arguments: { ms: 1200 } })
    await Bun.sleep(150)
    const t0 = Date.now()
    svc.kill('SIGTERM')
    await Bun.sleep(150)
    // comes while the service is stopping: refused as restarting, and the shim retries it on the next service
    const during = t.client.callTool({ name: 'reply', arguments: { conversation_id: 'c2', text: 'Med ponovnim zagonom.' } })
    const exit = await svc.exited
    const drained = Date.now() - t0
    const slowResult = await slow
    check('restart: a stop lets a running tool call finish, then exits 0 and removes its socket', exit === 0 && drained >= 900 && text(slowResult) === 'slept 1200' && !slowResult.isError && !existsSync(sam.socket), { exit, drained, slowResult })
    const down = await fetch(`${sam.base}/health`).then(() => false, () => true)
    check("restart: the status says stopped, and the app's API is down meanwhile (the phone retries)", status(sam).state === 'stopped' && down)
    svc = await startService(sam, { LANI_PROBE_NEW: '1' })
    const nextStarted = Date.parse(status(sam).started_at)
    const retried = await during
    const ranOn = (await backlog(sam)).find(b => b.event.text === 'Med ponovnim zagonom.')
    check('restart: the call that came during the stop was refused there and ran on the next service (the shim retried it once)', !retried.isError && !!ranOn && ranOn.event.at >= nextStarted, { retried, ranOn, nextStarted })
    await until(() => t.changed() > 0, 5_000)
    const after = (await t.client.listTools()).tools
    check('restart: the shim reconnected and sent tools/list_changed; the new tool is listed and callable', t.changed() === 1 && after.length === 42 && after.some(x => x.name === 'smoke_new') && text(await t.client.callTool({ name: 'smoke_new', arguments: {} })) === 'new', { changed: t.changed(), tools: after.length })
    const replay = await backlog(sam, lastSeen)
    check("restart: the reply the phone hadn't fetched is still there for it (events.json), with ids after the last it saw", replay.some(b => b.event.type === 'reply' && b.event.text === 'Hvala! 🙏') && replay.every(b => b.id > lastSeen), replay.map(b => [b.id, b.event.type]))
    await post(sam, '/message', { text: 'Še eno vprašanje.', conversation_id: 'c3' })
    await until(() => heard(t, 'c3'), 3_000)
    const again = heard(t, 'c3')
    check('restart: the channel works again, and the session is not asked for the guide again (its hello says it heard)', !!again && !again.content.includes('call the channel_guide tool') && status(sam).session?.pid === t.pid, again)

    // --- an outage: a call fails clearly; the session's prompt waits ------------------------------------------------
    await stopService(svc)
    const o0 = Date.now()
    const failed = await t.client.callTool({ name: 'list_modules', arguments: {} })
    const waited = Date.now() - o0
    check(`outage: a call while the service is down waits ${CALL_WAIT_MS / 1000} s, then fails clearly (isError, nothing done)`, !!failed.isError && text(failed).includes("isn't running") && text(failed).includes('Nothing was done') && waited >= CALL_WAIT_MS - 100 && waited < CALL_WAIT_MS + 2_000, { waited, failed })
    const refused = await post(sam, '/message', { text: 'x' }).then(r => r.status, () => 'refused')
    check("outage: the app's messages are refused meanwhile (its outbox sends them again)", refused === 'refused', refused)
    await t.client.notification({ method: 'notifications/claude/channel/permission_request', params: { request_id: 'vwxyz', tool_name: 'Edit', description: 'Edit a file', input_preview: 'x' } })
    const changedBefore = t.changed()
    svc = await startService(sam, { LANI_PROBE_NEW: '1' })
    const held = await until(async () => (await backlog(sam)).some(b => b.event.type === 'permission_request' && b.event.request_id === 'vwxyz'), 5_000)
    check('outage: a permission prompt the session made meanwhile reaches the app once the service is back', held)
    check('outage: the same tools after this restart: no list_changed', t.changed() === changedBefore && (await t.client.listTools()).tools.length === 42)

    // --- the session ends; what comes meanwhile waits, across a restart too --------------------------------------------
    const c0 = Date.now()
    await t.client.close()
    check('shim: it exits when Claude Code closes the channel', Date.now() - c0 < 1_500, Date.now() - c0)
    await until(() => !status(sam).session, 3_000)
    const waiting = await (await post(sam, '/message', { text: 'Si tam?', conversation_id: 'c4' })).json()
    check('queue: the service notices the session left, and queues what comes', !status(sam).session && waiting.queued === true, waiting)
    await stopService(svc)
    svc = await startService(sam, { LANI_PROBE_NEW: '1' })
    // more than the shim keeps for the service: all of them come, in order, before and after Claude Code is ready
    for (let i = 0; i < 60; i++) await post(sam, '/message', { text: `Vrsta ${i}`, conversation_id: `q${i}` })
    const t2 = await shim(sam.env)
    await until(() => heard(t2, 'c4') && heard(t2, 'q59'), 5_000)
    const c4 = heard(t2, 'c4')
    check("queue: kept across a restart of the service, delivered to the next session with queued_at, with the guide hint (it's a new session)", !!c4 && !!c4.meta.queued_at && c4.content.includes('Si tam?') && c4.content.includes('call the channel_guide tool'), c4)
    const order = t2.notes.filter(n => n.method === 'notifications/claude/channel').map(n => n.params.meta.conversation_id)
    check('queue: 61 events waiting when the session attached all reach it, in order', order.length === 61 && order[0] === 'c4' && order.slice(1).every((c: string, i: number) => c === `q${i}`), order)

    // --- a newer session takes over ----------------------------------------------------------------------------------
    const t3 = await shim(sam.env)
    await until(() => status(sam).session?.pid === t3.pid, 3_000)
    const older = await t2.client.callTool({ name: 'list_modules', arguments: {} })
    const newer = await t3.client.callTool({ name: 'list_modules', arguments: {} })
    await Bun.sleep(1_000) // the older one doesn't reconnect and take it back
    check("replaced: the newest session is the channel; the older one's calls fail clearly and it doesn't reconnect", !!older.isError && text(older).includes('took over') && !newer.isError && status(sam).session?.pid === t3.pid, { older, st: status(sam).session })
    await t2.client.close()
    await t3.client.close()

    // --- a shim that starts while the service is down ---------------------------------------------------------------
    await stopService(svc)
    const t4 = await shim(sam.env, { LANI_SHIM_START_WAIT_MS: '300' })
    const t4caps = t4.client.getServerCapabilities() as any
    check("shim start, service down: from the service's last channel: its instructions, the permission relay, its 42 tools", t4.client.getInstructions() === snapshot.instructions && !!t4caps?.experimental?.['claude/channel/permission'] && (await t4.client.listTools()).tools.length === 42)
    svc = await startService(sam, { LANI_PROBE_NEW: '1' })
    await until(() => status(sam).session?.pid === t4.pid, 5_000)
    check('shim start, service down: it connects once the service is up; its calls work, the tools unchanged', status(sam).session?.pid === t4.pid && text(await t4.client.callTool({ name: 'smoke_new', arguments: {} })) === 'new' && t4.changed() === 0)
    const nowhere = join(tempDir('fsvc-'), 'none.sock')
    const t5 = await shim({ ...kid.env, LANI_BRIDGE_SOCKET: nowhere }, { LANI_SHIM_START_WAIT_MS: '200' })
    const t5caps = t5.client.getServerCapabilities() as any
    check("shim start, no service ever: a minimal channel without tools; a child's has the child's rule and no permission relay", (t5.client.getInstructions() ?? '').includes("wasn't running") && (t5.client.getInstructions() ?? '').includes('child') && !t5caps?.experimental?.['claude/channel/permission'] && !!t5caps?.experimental?.['claude/channel'] && (await t5.client.listTools()).tools.length === 0)
    await t5.client.close()

    // --- one service per learner and port -----------------------------------------------------------------------------
    const twin = Bun.spawn(['bun', probe], { env: { ...sam.env, LANI_BRIDGE_PORT: String(first + 2) }, stdout: 'ignore', stderr: 'pipe' })
    const twinExit = await twin.exited
    const twinErr = await new Response(twin.stderr).text()
    check("one service per learner: a second one on the same socket exits (1) and says so; the first keeps it", twinExit === 1 && twinErr.includes('another bridge service already listens') && existsSync(sam.socket) && !(await t4.client.callTool({ name: 'list_modules', arguments: {} })).isError, twinErr.slice(-300))
    const clash = Bun.spawn(['bun', probe], { env: { ...kid.env, LANI_BRIDGE_SOCKET: join(tempDir('fsvc-'), 'b.sock'), LANI_BRIDGE_PORT: String(sam.port) }, stdout: 'ignore', stderr: 'pipe' })
    const clashExit = await clash.exited
    const clashErr = await new Response(clash.stderr).text()
    check('a service whose port is taken exits (1), for its supervisor to try again', clashExit === 1 && clashErr.includes(`port ${sam.port} is taken`), clashErr.slice(-300))

    // --- a child's service beside it ------------------------------------------------------------------------------------
    await startService(kid)
    const k = await shim(kid.env)
    const kcaps = k.client.getServerCapabilities() as any
    check("profiles: a child's service: its own socket; no permission relay, instructions with the child's name and rules", kid.socket !== sam.socket && !kcaps?.experimental?.['claude/channel/permission'] && !!kcaps?.experimental?.['claude/channel'] && (k.client.getInstructions() ?? '').includes("Kid's Lani language tutor") && (k.client.getInstructions() ?? '').includes('Child:'), k.client.getInstructions())
    await post(kid, '/message', { text: 'Živjo!', conversation_id: 'k1' })
    await until(() => heard(k, 'k1'), 3_000)
    await Bun.sleep(200)
    check("profiles: each service's events reach only its own session", !!heard(k, 'k1') && !heard(t4, 'k1') && !t4.notes.some(n => n.method === 'notifications/claude/channel' && n.params?.content?.includes('Živjo')))
    check("profiles: the child's app can't answer permission prompts (403)", (await post(kid, '/permission', { request_id: 'abcde', behavior: 'allow' })).status === 403)
    await k.client.close()
    await t4.client.close()

    // --- lani-bridge: the loop and the CLI (no tmux: the loop runs right here) ----------------------------------------
    const cliEnv: Record<string, string> = { ...sam.env, LANI_PROFILES_DIR: tempDir('lani-smoke-svc-profiles-'), XDG_STATE_HOME: tempDir('fsvs-'), LANI_KEYS_FILE: '/nonexistent' }
    cliEnv.TMUX_TMPDIR = cliEnv.XDG_STATE_HOME // any tmux call asks a server of its own (there is none)
    for (const k of ['LANI_DATA_DIR', 'LANI_BRIDGE_PORT', 'LANI_BRIDGE_SOCKET', 'LANI_BRIDGE_TOKEN']) delete cliEnv[k]
    const cli = (args: string[]) => {
      const p = Bun.spawnSync(args, { env: cliEnv, cwd: repo })
      return { code: p.exitCode, out: p.stdout.toString(), err: p.stderr.toString() }
    }
    const fb = (...args: string[]) => cli(['bash', join(bin, 'lani-bridge'), ...args, '--profile', 'cli'])
    const added = cli(['bun', join(src, 'cli/profile.ts'), 'add', 'cli', '--name', 'Cli', '--target', 'sl', '--base', 'en', '--port', String(first + 2), '--https-port', '19443'])
    const socket = fb('socket').out.trim()
    const loop = Bun.spawn(['bash', join(bin, 'lani-bridge'), 'run', '--profile', 'cli'], { env: cliEnv, cwd: repo, stdout: 'ignore', stderr: 'ignore' })
    running.push(loop)
    stopLoop = () => void fb('stop')
    const up = await until(() => fb('status', '--quiet').code === 0, 20_000)
    check('lani-bridge run: the loop starts the service of a profile (lani-profile add), its socket in the state directory', added.code === 0 && up && socket.startsWith(cliEnv.XDG_STATE_HOME) && existsSync(socket), added.out + added.err)
    const tc = await shim({ ...cliEnv, LANI_BRIDGE_SOCKET: socket })
    await until(() => fb('status').out.includes('the tutor is attached'), 5_000)
    const st = fb('status')
    check('lani-bridge status: running, its port and commit, the tutor attached, run by the loop', st.code === 0 && st.out.includes('running since') && st.out.includes(`127.0.0.1:${first + 2}`) && st.out.includes('the tutor is attached') && st.out.includes('the lani-bridge loop'), st.out + st.err)
    const restarted = fb('restart')
    check('lani-bridge restart: the code builds, the loop starts it again, the tutor reconnects; it says how long each took', restarted.code === 0 && /restarted/.test(restarted.out) && /the app's API back after \d+\.\d s/.test(restarted.out) && /the tutor reconnected after/.test(restarted.out), restarted.out + restarted.err)
    check('… and the session works on', !(await tc.client.callTool({ name: 'list_modules', arguments: {} })).isError)
    const stopped = fb('stop')
    stopLoop = () => {}
    const loopExit = await Promise.race([loop.exited, Bun.sleep(10_000).then(() => 'timeout')])
    check('lani-bridge stop: the service and its loop end, the socket goes; it says so', stopped.code === 0 && stopped.out.includes('stopped') && loopExit === 0 && !existsSync(socket) && fb('status', '--quiet').code === 1, stopped.out + stopped.err + String(loopExit))
    await tc.client.close()
    const printed = fb('start', '--print')
    check('lani-bridge start --print: its own tmux session runs the loop', printed.out.startsWith("tmux new-session -d -s 'lani-bridge-cli' ") && printed.out.includes('companion/bin/lani-bridge run --profile'), printed.out)
    const session = cli(['bash', join(bin, 'lani-session'), '--profile', 'cli', '--service', '--print'])
    const spec = cli(['bun', join(src, 'cli/profile.ts'), 'session', 'cli', '--keys', '/k.env', '--mode', 'service']).out.split('\n')
    check(
      'lani-session --service: the bridge service first, then the tutor with the shim and its socket, without LANI_TUTOR and the port',
      session.code === 0 && session.out.startsWith('companion/bin/lani-bridge start --profile cli\ntmux new-session -A -s lani-cli ') && spec[1].includes(`LANI_BRIDGE_SHIM='1'`) && spec[1].includes(`LANI_BRIDGE_SOCKET='${socket}'`) && spec[1].includes('LANI_DATA_DIR=') && !spec[1].includes('LANI_TUTOR') && !spec[1].includes('LANI_BRIDGE_PORT'),
      session.out + session.err + spec.join('\n'),
    )
    const classic = cli(['bun', join(src, 'cli/profile.ts'), 'session', 'cli', '--keys', '/k.env']).out.split('\n')
    check('lani-profile session without --mode: the classic session, as before', classic[1].includes(`LANI_TUTOR='1'`) && classic[1].includes(`LANI_BRIDGE_PORT='${first + 2}'`) && !classic[1].includes('LANI_BRIDGE_SHIM'), classic[1])
  } finally {
    stopLoop()
    for (const c of tutors) await c.close().catch(() => {})
    for (const p of running) p.kill('SIGTERM')
    await Promise.all(running.map(p => p.exited))
  }
}
