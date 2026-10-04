#!/usr/bin/env bun
// companion/bin/lani-bridge: the bridge service of each learner (docs/plans/04-bridge-service.md).
//
//   lani-bridge start   [--profile <id>] [--print]   the service in tmux "lani-bridge" (-<id>), unless it runs
//   lani-bridge restart [--profile <id> | --all]     deploy the current code: a few seconds of downtime for the app
//   lani-bridge status  [--profile <id> | --all] [--quiet]
//   lani-bridge stop    [--profile <id>]
//   lani-bridge spec    [--profile <id>]             for companion/bin/lani-bridge: the log, the stop mark, the environment
//   lani-bridge socket  [--profile <id>]             the service's control socket (lani-session gives it to the shim)
// `run`, `exec` and `logs` are the shell script's own.
import '../env' // first: LANI_* from the FLUENT_* names of a node set up before the rename
import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmdirSync, unlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { parseArgs } from 'node:util'
import { readJson, stateDir, statusFile } from '../control'
import { laniDir } from '../env'
import { allProfiles, bridgeEnv, bridgeUp, die, projectDir, q, registryDir, resolveProfile, serviceSocket, serviceTmux, short, tmuxUp, type Resolved } from './common'

const [cmd, ...args] = process.argv.slice(2)
const { values } = parseArgs({
  args,
  options: { profile: { type: 'string', short: 'p' }, all: { type: 'boolean', default: false }, print: { type: 'boolean', default: false }, quiet: { type: 'boolean', default: false } },
})

/** What the service writes beside its socket (bridge-service.ts). */
type Status = {
  state: 'running' | 'stopping' | 'stopped'
  pid: number
  profile: string
  port: number
  socket: string
  started_at: string
  code?: string
  supervisor?: string
  session: { since: string; client: string; pid?: number } | null
  queued: number
  tools: string[]
}

type Service = { p: Resolved; tmux: string; socket: string; status: string; log: string; stop: string }

function serviceOf(p: Resolved): Service {
  const socket = serviceSocket(p)
  return {
    p,
    tmux: serviceTmux(p),
    socket,
    status: statusFile(socket),
    log: join(laniDir(stateDir()), p.isDefault ? 'bridge.log' : `bridge-${p.id}.log`),
    stop: socket.replace(/\.sock$/, '.stop'),
  }
}

/** Whether [pid] is a bridge service (not a process that got its number since). */
function alive(pid: number | undefined): boolean {
  if (!pid) return false
  try {
    process.kill(pid, 0)
  } catch {
    return false
  }
  try {
    return readFileSync(`/proc/${pid}/cmdline`, 'utf8').includes('service.ts')
  } catch {
    return true // no /proc: trust the signal
  }
}

const statusOf = (s: Service) => readJson<Status>(s.status)
/** The service's status when it runs now. */
function running(s: Service): Status | undefined {
  const st = statusOf(s)
  return st && st.state !== 'stopped' && alive(st.pid) ? st : undefined
}

const git = (...a: string[]) => {
  const r = spawnSync('git', ['-C', projectDir, ...a], { encoding: 'utf8' })
  return r.status === 0 ? r.stdout.trim() : undefined
}
const secs = (ms: number) => `${(ms / 1000).toFixed(1)} s`
const clock = (iso: string) => new Date(iso).toLocaleString('sv-SE').slice(0, 16)

async function until<T>(get: () => T | undefined | Promise<T | undefined>, ms: number): Promise<T | undefined> {
  const end = Date.now() + ms
  for (;;) {
    const v = await get()
    if (v || Date.now() >= end) return v
    await Bun.sleep(100)
  }
}

/** Whether the code the service would start builds (syntax, imports): a restart into broken code is refused. */
function builds(): string | undefined {
  const dir = mkdtempSync(join(tmpdir(), 'lani-bridge-check-'))
  const out = join(dir, 'service.js')
  const r = spawnSync('bun', ['build', join(projectDir, 'companion/bridge/src/service.ts'), '--target=bun', `--outfile=${out}`], { encoding: 'utf8' })
  try {
    unlinkSync(out)
    rmdirSync(dir)
  } catch {} // nothing was built
  return r.status === 0 ? undefined : (r.stderr || r.stdout).trim()
}

function describe(s: Service, st: Status | undefined): string {
  const name = s.p.isDefault ? 'default' : s.p.id
  if (!st) {
    const why = tmuxUp(s.tmux) ? `its tmux session ${s.tmux} is up, but the service isn't running: see companion/bin/lani-bridge logs${s.p.isDefault ? '' : ` --profile ${s.p.id}`}` : 'start it with companion/bin/lani-bridge start' + (s.p.isDefault ? '' : ` --profile ${s.p.id}`)
    const last = statusOf(s)
    const queued = last?.queued ? ` ${last.queued} event(s) wait for the tutor.` : ''
    return `${name}: the bridge service is not running (${why}).${queued}`
  }
  const head = git('rev-parse', '--short', 'HEAD')
  const deploy = st.code && head && head !== st.code ? `; the checkout is at ${head}: lani-bridge restart deploys it` : ''
  const tutor = st.session
    ? `the tutor is attached since ${clock(st.session.since)} (${st.session.client}${st.session.pid ? `, pid ${st.session.pid}` : ''})`
    : `no tutor session is attached${st.queued ? `: ${st.queued} event(s) wait for it` : ''}`
  return [
    `${name}: ${st.state === 'stopping' ? 'stopping' : 'running'} since ${clock(st.started_at)}, pid ${st.pid}${st.code ? `, code ${st.code}` : ''}${deploy}`,
    `  app API   127.0.0.1:${st.port}`,
    `  tutor     ${tutor}`,
    `  tools     ${st.tools.length}`,
    `  run by    ${st.supervisor === 'systemd' ? 'systemd' : st.supervisor === 'run' ? `the lani-bridge loop${tmuxUp(s.tmux) ? ` in tmux ${s.tmux}` : ''}` : 'nobody (started by hand: a restart would stop it)'}`,
    `  log       ${short(s.log)}`,
    `  socket    ${short(s.socket)}`,
  ].join('\n')
}

/** The service in its own tmux session (the supervisor loop), unless it runs; then waits until it answers. */
async function start(p: Resolved) {
  const s = serviceOf(p)
  // tmux doesn't pass this shell's environment on: what decides the profile, the keys and the socket's place goes along
  const pass = { LANI_PROFILES_DIR: registryDir, LANI_KEYS_FILE: process.env.LANI_KEYS_FILE, XDG_STATE_HOME: process.env.XDG_STATE_HOME }
  const env = Object.entries(pass).flatMap(([k, v]) => (v ? [`${k}=${q(v)}`] : [])).join(' ')
  const command = `cd ${q(projectDir)} && exec env ${env} companion/bin/lani-bridge run${p.isDefault ? '' : ` --profile ${q(p.id)}`}`
  if (values.print) {
    console.log(`tmux new-session -d -s ${q(s.tmux)} ${q(command)}`)
    return
  }
  if (!tmuxUp(s.tmux)) {
    if (existsSync(s.stop)) unlinkSync(s.stop) // a stop's mark the loop never read
    const r = spawnSync('tmux', ['new-session', '-d', '-s', s.tmux, command], { encoding: 'utf8' })
    if (r.status !== 0) die(`tmux new-session ${s.tmux} failed: ${r.stderr.trim()}`)
    console.log(`started the bridge service of ${p.isDefault ? 'the default learner' : p.id} in tmux ${s.tmux}`)
  }
  const up = await until(async () => (running(s) && (await bridgeUp(p.port)) ? running(s) : undefined), 30_000)
  if (!up) {
    console.error(`the bridge service of ${p.id} doesn't answer on port ${p.port} yet (is the port still taken, e.g. by a tutor session in the classic mode?). See: companion/bin/lani-bridge logs${p.isDefault ? '' : ` --profile ${p.id}`}`)
    process.exit(1)
  }
  console.log(describe(s, up))
}

/** Deploys the current code: checks it builds, stops the service (it drains), waits for the next one and the tutor. */
async function restart(p: Resolved, checked: { done?: boolean }) {
  const s = serviceOf(p)
  const before = running(s)
  if (!before) {
    console.error(describe(s, undefined))
    return false
  }
  if (!before.supervisor) {
    console.error(`${p.id}: the service runs without a supervisor (started by hand), so a restart would only stop it. Stop it, then: companion/bin/lani-bridge start${p.isDefault ? '' : ` --profile ${p.id}`}`)
    return false
  }
  if (!checked.done) {
    const broken = builds()
    if (broken) die(`the bridge's code doesn't build, so the running service is left alone:\n${broken}`)
    checked.done = true
  }
  const t0 = Date.now()
  process.kill(before.pid, 'SIGTERM')
  if (!(await until(() => !alive(before.pid) || undefined, 15_000))) {
    console.error(`the service (pid ${before.pid}) didn't stop within 15 s: killing it`)
    process.kill(before.pid, 'SIGKILL')
  }
  const stopped = Date.now() - t0
  const next = await until(async () => {
    const st = running(s)
    return st && st.pid !== before.pid && (await bridgeUp(p.port)) ? st : undefined
  }, 30_000)
  if (!next) {
    console.error(`the service didn't come back within 30 s: is its supervisor running? (companion/bin/lani-bridge status${p.isDefault ? '' : ` --profile ${p.id}`}, logs)`)
    return false
  }
  const back = Date.now() - t0
  const tutor = before.session ? await until(() => running(s)?.session ?? undefined, 15_000) : undefined
  const added = next.tools.filter(t => !before.tools.includes(t))
  const gone = before.tools.filter(t => !next.tools.includes(t))
  console.log(
    [
      `${p.isDefault ? 'default' : p.id}: restarted${next.code ? ` with ${next.code}` : ''}: stopped in ${secs(stopped)}, the app's API back after ${secs(back)}`,
      before.session ? (tutor ? `, the tutor reconnected after ${secs(Date.now() - t0)}` : ', the tutor has not reconnected yet (lani-bridge status)') : ', no tutor session was attached',
      added.length || gone.length ? `; tools ${[...added.map(t => `+${t}`), ...gone.map(t => `-${t}`)].join(' ')}` : '',
    ].join(''),
  )
  return true
}

/** Stops the service and its supervisor; says how many events were still waiting for the tutor. */
async function stop(p: Resolved) {
  const s = serviceOf(p)
  const st = running(s)
  if (st?.supervisor === 'systemd') die(`systemd runs it: systemctl --user stop lani-bridge@${p.id}`)
  mkdirSync(dirname(s.stop), { recursive: true, mode: 0o700 })
  writeFileSync(s.stop, `${new Date().toISOString()}\n`) // the loop ends instead of starting it again
  if (st) {
    process.kill(st.pid, 'SIGTERM')
    if (!(await until(() => !alive(st.pid) || undefined, 15_000))) process.kill(st.pid, 'SIGKILL')
  }
  if (!(await until(() => !tmuxUp(s.tmux) || undefined, 5_000))) spawnSync('tmux', ['kill-session', '-t', `=${s.tmux}`])
  const queued = statusOf(s)?.queued ?? 0
  console.log(`${p.isDefault ? 'default' : p.id}: the bridge service is stopped${st ? '' : ' (it was not running)'}.${queued ? ` ${queued} event(s) wait for the tutor: a classic session won't get them.` : ''}`)
}

const targets = (): Resolved[] => (values.all ? allProfiles() : [resolveProfile(values.profile)])

switch (cmd) {
  case 'start':
    await start(resolveProfile(values.profile))
    break
  case 'restart': {
    const checked = {}
    let ok = true
    for (const p of targets()) {
      if (values.all && !running(serviceOf(p))) continue // --all: the running ones
      ok = (await restart(p, checked)) && ok
    }
    process.exit(ok ? 0 : 1)
  }
  case 'stop':
    await stop(resolveProfile(values.profile))
    break
  case 'status':
  case undefined: {
    const all = targets().map(p => serviceOf(p))
    if (values.quiet) process.exit(all.every(s => running(s)) ? 0 : 1)
    console.log(all.map(s => describe(s, running(s))).join('\n\n'))
    break
  }
  case 'spec': {
    // for the shell script's loop: read again before each start (a changed registry entry counts at the next start)
    const s = serviceOf(resolveProfile(values.profile))
    const env = { ...bridgeEnv(s.p), LANI_BRIDGE_SOCKET: s.socket }
    console.log(`log=${s.log}`)
    console.log(`stop=${s.stop}`)
    console.log(`env=export ${Object.entries(env).map(([k, v]) => `${k}=${q(v)}`).join(' ')}`)
    break
  }
  case 'socket':
    console.log(serviceSocket(resolveProfile(values.profile)))
    break
  default:
    die(`unknown command: ${cmd}\nusage: lani-bridge start|restart|status|stop|logs|run|exec [--profile <id>] (see companion/bin/lani-bridge --help)`)
}
