// The bridge in the app (docs/plans/05-built/phone-runtime.md): the same stores, features, routes, tools and guide as a
// node's (build.ts), in QuickJS inside the app's process (si.lanisce.lani.phone.PhoneBridge), bundled by
// companion/bridge/phone/build.ts into bridge-phone.js. What a node has and the phone hasn't: the voice store (no
// voices: voice.ts here), the MCP server and its control socket (the in-app tutor is the channel: channel.ts here),
// the HTTP server (the app calls the routes in-process: handle), the pairing QR code, tailscale and git.
//
// The host (Kotlin) calls the functions of globalThis.__laniPhone, each a short evaluation; what takes longer answers
// through the host: a request's answer (host.respond, an event stream part by part: host.chunk, host.end), a tool's
// result (host.toolDone), an event for the tutor (host.channel). The node's built-ins are the shims of phone/shims.
import '../../phone/shims/prelude' // first: process, Buffer, URL, fetch, the timers … over the host
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'
import { fromHost, host, toHost } from '../../phone/shims/host'
import { registerInline, registerScript, registeredScripts } from '../../phone/shims/scripts'
import { appApi, buildBridge, type BuiltBridge } from '../build'
import { cultureFor } from '../cultures'
import { log, type Config } from '../config'
import { dataDirFor, run as runDb, SCRIPTS, type Script } from '../db/cli'
import { errorText, router } from '../http'
import { GENDERS, initLearnerData, learnerFacts } from '../learners'
import { fail, listed, type ToolResult } from '../tool'
import { PhoneChannel } from './channel'
import { phoneVoices } from './voice'

// The learner-data scripts: a node runs .claude/hooks/{read-db,update-db,card,validate-data,ensure_data_dir}.py with
// Python; the phone runs their TypeScript port (src/db/cli.ts, docs/plans/05-built/db-port.md), synchronously, in the
// script's own working directory and environment (the bridge's spawn passes them). Where the Python commits a data
// repository (the hook afterWrite), the phone tells the app (host.dataWritten): its sync pushes the data a moment later
// (the hosted mode: DataSync.touched, docs/plans/05-built/hosted-mode.md), when git is connected.
const written = (script: Script) => ({
  afterWrite: (e: { paths: string[] }) => {
    try {
      host().dataWritten(JSON.stringify({ script, paths: e.paths }))
    } catch {}
  },
})
for (const s of SCRIPTS) registerScript(s, (argv, stdin, env, cwd) => runDb(s, argv, stdin, env, { cwd, hooks: written(s) }))
// learners.ts asks lani_paths.data_dir() with `python3 -c …`: the port's answer, with the newline print() adds
registerInline(/from lani_paths import data_dir; print\(data_dir\(\)\)/, (_argv, _stdin, env, cwd) => ({ status: 0, stdout: `${dataDirFor(env, { cwd })}\n`, stderr: '' }))

type Phone = {
  bridge: BuiltBridge<PhoneChannel>
  route: (req: Request) => Promise<Response>
  exits: (() => void)[]
  started: boolean
}

let phone: Phone | undefined
/** The event streams being sent: a request's id → how to stop it. */
const streams = new Map<number, { abort: () => void }>()

const need = (): Phone => {
  if (!phone) throw new Error('the phone bridge is not booted (__laniPhone.boot)')
  return phone
}

/** Builds the bridge (nothing started yet): the config from the host, the phone's channel and voices. */
function boot(): string {
  if (phone) return summary(phone)
  const c = JSON.parse(host().config()) as { projectDir: string; dataDir: string }
  const appDir = join(c.dataDir, 'app')
  mkdirSync(appDir, { recursive: true })
  // the village of the language the learner learns (on a node lani-setup writes it into lani.env; the phone has none),
  // unless the host set one: an Italian learner's export imported plays friuli
  if (!process.env.LANI_CULTURE) {
    const code = learnerFacts(c.dataDir).targetCode
    const culture = code ? cultureFor(join(c.projectDir, 'companion/cultures'), code) : undefined
    if (culture) process.env.LANI_CULTURE = culture
  }
  const cfg: Config = {
    projectDir: c.projectDir,
    host: '127.0.0.1',
    port: 0,
    // the app's API is always served (in-process): the features' jobs for the app run, as a bridge service's
    serveApp: true,
    dataDir: c.dataDir,
    appDir,
    webDir: join(c.projectDir, 'companion/bridge/web/family'),
  }
  const exits: (() => void)[] = []
  const bridge = buildBridge({
    cfg,
    channel: new PhoneChannel(),
    voices: phoneVoices,
    onExit: fn => void exits.push(fn),
    // the APK's content was checked when it was built; the check would be most of the boot here
    checkCulture: process.env.LANI_CULTURE_CHECK === '1',
    // and it can't change while the bridge runs: GET /changes walks the learner's data alone
    contentChanges: false,
  })
  // what the app hasn't fetched yet, kept across the app's restarts (as the bridge service keeps it)
  bridge.ctx.events.persist(join(appDir, 'events.json'), log)
  const { routes, roleOf } = appApi(bridge)
  phone = { bridge, route: router(routes, roleOf, log), exits, started: false }
  return summary(phone)
}

const summary = (p: Phone) =>
  JSON.stringify({
    culture: p.bridge.ctx.culture.id,
    language: p.bridge.ctx.dictionary.language,
    profile: p.bridge.profile.id,
    features: p.bridge.named.map(f => f.name),
    tools: p.bridge.tools.length,
    scripts: registeredScripts(),
  })

/** The features' background work (timers, the variants, the dictionary) and the town's mail on the relay: once. */
function start() {
  const p = need()
  if (p.started) return
  p.started = true
  for (const f of p.bridge.features) {
    try {
      f.start?.()
    } catch (e) {
      log(`a feature's start failed: ${errorText(e)}`)
    }
  }
  // the town on the relay (relay.ts), when the hosted setup gives it one (LANI_RELAY_URL; LANI_TOWN_URL=relay): the
  // phone's bridge serves the app, so it answers the town's mail, as a node's that serves the app does
  p.bridge.ctx.relay?.start()
}

/** The bridge ends: the events kept, the devices' last seen written, the streams closed. */
function stop() {
  if (!phone) return
  for (const s of streams.values()) s.abort()
  streams.clear()
  phone.bridge.ctx.relay?.stop()
  phone.bridge.ctx.events.flush()
  for (const f of phone.exits) {
    try {
      f()
    } catch {}
  }
}

const headersOf = (h: Headers) => {
  const out: [string, string][] = []
  h.forEach((v, k) => out.push([k, v]))
  return out
}

/** The app's request [id] (its method, URL, headers and body from the host): the router's answer goes to host.respond. */
async function handle(id: number) {
  const p = need()
  const r = JSON.parse(host().request(id)) as { method: string; url: string; headers: [string, string][] }
  const raw = host().requestBody(id)
  const ac = new AbortController()
  let res: Response
  try {
    const req = new Request(r.url, { method: r.method, headers: r.headers, body: raw && raw.length ? (fromHost(raw) as Uint8Array<ArrayBuffer>) : undefined, signal: ac.signal })
    res = await p.route(req)
  } catch (e) {
    log(`request ${r.method} ${r.url}: ${errorText(e)}`)
    res = Response.json({ error: 'internal error' }, { status: 500 })
  }
  const held = (res as unknown as { _held?: () => unknown })._held?.() as { text?: string; bytes?: Uint8Array; stream?: ReadableStream; file?: { bytes: () => Uint8Array } } | null
  const head = (stream: boolean) => JSON.stringify({ status: res.status, headers: headersOf(res.headers), stream })
  if (held?.stream) return pump(id, head(true), held.stream, () => ac.abort())
  if (!held) return host().respond(id, head(false), null, null)
  if (held.text !== undefined) return host().respond(id, head(false), held.text, null)
  if (held.bytes) return host().respond(id, head(false), null, toHost(held.bytes))
  try {
    return host().respond(id, head(false), null, toHost(held.file!.bytes()))
  } catch (e) {
    return host().respond(id, JSON.stringify({ status: 404, headers: [['content-type', 'application/json;charset=utf-8']], stream: false }), JSON.stringify({ error: 'not found' }), null)
  }
}

/** An answer that streams (/events): its head now, then each part as it comes, until it ends or the app lets it go. */
async function pump(id: number, head: string, stream: ReadableStream, abort: () => void) {
  const reader = stream.getReader()
  let open = true
  streams.set(id, {
    abort: () => {
      if (!open) return
      open = false
      abort()
      void reader.cancel().catch(() => {})
    },
  })
  host().respond(id, head, null, null)
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done || !open) break
      if (typeof value === 'string') host().chunk(id, value, null)
      else host().chunk(id, null, toHost(value as Uint8Array))
    }
    host().end(id, null)
  } catch (e) {
    host().end(id, String((e as Error)?.message ?? e))
  } finally {
    open = false
    streams.delete(id)
  }
}

/** The app let go of stream [id] (the screen closed, the transport stopped): it ends as a closed connection would. */
function cancel(id: number) {
  streams.get(id)?.abort()
}

/** The in-app tutor's tool call [id] (its name and arguments from the host): the result goes to host.toolDone. */
async function call(id: number) {
  const p = need()
  const { name, args } = JSON.parse(host().toolCall(id)) as { name: string; args?: Record<string, unknown> }
  const tool = p.bridge.tools.find(t => t.name === name)
  let result: ToolResult
  if (!tool) result = fail(`unknown tool: ${name}`)
  else {
    try {
      result = await tool.handle((args ?? {}) as Record<string, any>)
    } catch (e) {
      // what Claude Code shows of a tool that threw (the MCP server's error answer): its message
      log(`tool ${name}: ${errorText(e)}`)
      result = fail(String((e as Error)?.message ?? e))
    }
  }
  host().toolDone(id, JSON.stringify(result))
}

/** What the hosted mode's onboarding asks (the app's setup, as lani-setup asks it: cli/setup.ts). */
type NewLearnerAnswers = { name: string; gender?: string; target: string; base: string; level?: string; goal?: number }

/**
 * A new learner's data in the app's data directory, before the bridge is booted (its stores read the profile when they
 * are built): the six databases from data-examples' templates as lani-setup makes them (learners.ts initLearnerData),
 * in the culture pack of the language learned (cultures.ts cultureFor, as lani-setup picks it). JSON {culture, language}; throws for a directory
 * that holds a learner already, or a language no village speaks.
 */
function initLearner(answers: string): string {
  const c = JSON.parse(host().config()) as { projectDir: string; dataDir: string }
  const a = JSON.parse(answers) as NewLearnerAnswers
  const name = String(a.name ?? '').trim()
  if (!name) throw new Error('a name, please')
  if (!a.target || !a.base || a.target === a.base) throw new Error('two different languages: the one learned and the one explanations are in')
  const culture = cultureFor(join(c.projectDir, 'companion/cultures'), a.target)
  if (!culture) throw new Error(`no village speaks ${a.target} yet`)
  const gender = (GENDERS as readonly string[]).includes(a.gender ?? '') ? (a.gender as (typeof GENDERS)[number]) : undefined
  initLearnerData(c.projectDir, c.dataDir, {
    name, target: a.target, base: a.base, level: a.level || 'A1', goal: a.goal && a.goal > 0 ? a.goal : undefined,
    ...(gender ? { gender } : {}), culture, results: join(c.dataDir, 'results'),
  })
  return JSON.stringify({ culture, language: a.target })
}

/**
 * One of the learner-data scripts on its own (the app's import checks an export with validate-data and read-db before it
 * takes its place): JSON {script, argv, stdin?, env?, cwd?} → JSON {status, stdout, stderr}, as `python3
 * .claude/hooks/<script>.py` answers on a node. [env] over the bridge's own.
 */
function db(call: string): string {
  const r = JSON.parse(call) as { script: Script; argv?: string[]; stdin?: string; env?: Record<string, string>; cwd?: string }
  if (!SCRIPTS.includes(r.script)) return JSON.stringify({ status: 2, stdout: '', stderr: `no script ${r.script}\n` })
  return JSON.stringify(runDb(r.script, r.argv ?? [], r.stdin, { ...process.env, ...(r.env ?? {}) }, { cwd: r.cwd }))
}

/** A request that failed before it had an answer (the bridge not booted, the host's request gone): a 500, never a hang. */
function failed(id: number, e: unknown) {
  log(`request ${id}: ${errorText(e)}`)
  try {
    host().respond(id, JSON.stringify({ status: 500, headers: [['content-type', 'application/json;charset=utf-8']], stream: false }), JSON.stringify({ error: 'internal error' }), null)
  } catch {}
}

const api = {
  boot,
  start,
  stop,
  initLearner,
  db,
  handle: (id: number) => void handle(id).catch(e => failed(id, e)),
  cancel,
  call: (id: number) =>
    void call(id).catch(e => {
      log(`tool call ${id}: ${errorText(e)}`)
      try {
        host().toolDone(id, JSON.stringify(fail(String((e as Error)?.message ?? e))))
      } catch {}
    }),
  /** The tools as the session sees them: JSON [{name, description, inputSchema}]. */
  tools: () => JSON.stringify(listed(need().bridge.tools)),
  /** The channel's instructions as Claude Code gets them (the rules that fit its limit; read again for each session). */
  instructions: () => need().bridge.rulesNow(),
  /** The whole guide (the channel_guide tool's text). */
  guide: () => need().bridge.guide(),
  /** A new tutor session: its first conversation's event asks for the guide again. */
  attach: () => need().bridge.ctx.channel.attach(),
  /** What waits in the loop: timers, fetches, streams. */
  pending: () => JSON.stringify({ ...(globalThis as unknown as { __laniLoop: { pending: () => object } }).__laniLoop.pending(), streams: streams.size }),
}

;(globalThis as unknown as { __laniPhone: typeof api }).__laniPhone = api
