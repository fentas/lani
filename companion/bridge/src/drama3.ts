// The Drama 3 worker on demand, for voice-build --drama3 (companion/voice-drama3; Built with Fish Audio).
//
// The worker holds about 7 GB of VRAM next to Gepard and the STT worker and is far too slow for live lines, so it is no
// service the bridge keeps: voice-build starts it for a batch (unless one answers at LANI_DRAMA3_URL already), voices
// the clips and stops it. The worker loads its model only once the host's heavy lock (LANI_HEAVY_LOCK, a file it
// flocks and holds while it lives) is its own and 20 GB are available. While the batch runs, voice-build pauses below
// LANI_DRAMA3_MIN_FREE_GB available (20; `free -g`'s "available", MemAvailable), stops the worker when the pause goes on
// (LANI_DRAMA3_PAUSE_STOP_S), and starts it again once memory is back. The batch can stop at any time: what is still
// Gepard's is what the next run makes.
import { closeSync, existsSync, mkdirSync, openSync, readFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'
import { DRAMA3_URL, type Drama3 } from './voice'

/**
 * Seconds of the batch per second of audio: Drama 3 on the RX 9070 in safe mode, the CPU codec, the checks (Whisper
 * large-v3 on the CPU, overlapped with the next take's generation) and the retries. Measured 2026-10-19 over the
 * consistency run (README.md, "Speed"); LANI_DRAMA3_RTF overrides it.
 */
export const DRAMA3_RTF = 11
/** Letters a second Drama 3's voices speak (the median of the consistency run): a clip's length from its text. */
export const DRAMA3_LETTERS_PER_SECOND = 8.5
/** What a clip costs besides its audio: the prefill of the reference and the line, and the file's encode and level. */
export const DRAMA3_CLIP_OVERHEAD_S = 6

/** The audio [texts] would make, and the batch's time for them at [rtf] (seconds). */
export function drama3Estimate(texts: string[], rtf = DRAMA3_RTF): { audio: number; seconds: number } {
  let audio = 0
  for (const t of texts) audio += Math.max(0.6, t.replace(/[^\p{L}]/gu, '').length / DRAMA3_LETTERS_PER_SECOND + 0.3)
  return { audio, seconds: audio * rtf + texts.length * DRAMA3_CLIP_OVERHEAD_S }
}

/** "3 h 20 min", "12 min", "40 s". */
export function duration(s: number): string {
  if (s < 60) return `${Math.round(s)} s`
  const m = Math.round(s / 60)
  return m < 60 ? `${m} min` : `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min`
}

/** MemAvailable in GB ([meminfo]: /proc/meminfo, LANI_DRAMA3_MEMINFO in tests); undefined when it can't be read. */
export function availableGb(meminfo = '/proc/meminfo'): number | undefined {
  try {
    const m = /^MemAvailable:\s+(\d+)\s*kB/m.exec(readFileSync(meminfo, 'utf8'))
    return m ? Number(m[1]) / 2 ** 20 : undefined
  } catch {
    return undefined
  }
}

export type Drama3Settings = {
  /** The worker's command: LANI_DRAMA3_CMD (words split on spaces), else <home>/run.sh (install.sh writes it). */
  cmd: string[]
  /** Whether the command is there (the worker is installed). */
  installed: boolean
  url: string
  /** The host's heavy lock the worker holds while it runs (LANI_HEAVY_LOCK; /tmp/lani-heavy.lock). */
  lock: string
  /** The worker's output (LANI_DRAMA3_LOG; ~/.cache/lani/drama3-worker.log). */
  log: string
  meminfo: string
  /** The batch pauses below this many GB available (LANI_DRAMA3_MIN_FREE_GB; 20). */
  minFreeGb: number
  pollMs: number
  /** A pause longer than this stops the worker (its memory and the lock freed) until memory is back. */
  stopAfterMs: number
  /** How long the worker may take to come up: waiting for the lock, for memory, the model's load. */
  startTimeoutMs: number
  /** The batch's speed (DRAMA3_RTF; LANI_DRAMA3_RTF). */
  rtf: number
}

export function drama3Settings(env: Record<string, string | undefined> = process.env): Drama3Settings {
  const num = (name: string, d: number) => {
    const v = Number(env[name])
    return env[name] !== undefined && env[name] !== '' && Number.isFinite(v) && v >= 0 ? v : d
  }
  const home = env.LANI_DRAMA3_HOME || join(homedir(), '.local/share/lani-drama3')
  const cmd = env.LANI_DRAMA3_CMD?.trim() ? env.LANI_DRAMA3_CMD.trim().split(/\s+/) : [join(home, 'run.sh')]
  const installed = cmd[0].includes('/') ? existsSync(cmd[0]) : !!Bun.which(cmd[0])
  return {
    cmd, installed,
    url: (env.LANI_DRAMA3_URL || DRAMA3_URL).replace(/\/$/, ''),
    lock: env.LANI_HEAVY_LOCK || '/tmp/lani-heavy.lock',
    log: env.LANI_DRAMA3_LOG || join(env.XDG_CACHE_HOME || join(homedir(), '.cache'), 'lani/drama3-worker.log'),
    meminfo: env.LANI_DRAMA3_MEMINFO || '/proc/meminfo',
    minFreeGb: num('LANI_DRAMA3_MIN_FREE_GB', 20),
    pollMs: num('LANI_DRAMA3_PAUSE_POLL_S', 60) * 1000,
    stopAfterMs: num('LANI_DRAMA3_PAUSE_STOP_S', 300) * 1000,
    startTimeoutMs: num('LANI_DRAMA3_START_TIMEOUT_S', 4 * 3600) * 1000,
    rtf: num('LANI_DRAMA3_RTF', DRAMA3_RTF) || DRAMA3_RTF,
  }
}

/** The worker for one batch: started when none answers, stopped after (only one this batch started). */
export class Drama3Worker {
  private proc?: ReturnType<typeof Bun.spawn>
  /** Whether this batch started the worker (and so stops it). */
  ours = false
  starts = 0

  constructor(
    private readonly engine: Drama3,
    readonly s: Drama3Settings,
    private readonly out: (line: string) => void = () => {},
  ) {}

  get running(): boolean { return !!this.proc && this.proc.exitCode === null && !this.proc.killed }

  /** Up at its URL: one that answers already (a service, started by hand), else the command, waited for. */
  async start(): Promise<void> {
    if (this.running) return
    if (await this.engine.up()) {
      if (!this.starts) this.out(`The Drama 3 worker answers at ${this.s.url} already: used as it is, not stopped after.`)
      return
    }
    if (!this.s.installed) throw new Error(`the Drama 3 worker is not installed (${this.s.cmd[0]}): companion/voice-drama3/install.sh, or LANI_DRAMA3_CMD`)
    const u = new URL(this.s.url)
    mkdirSync(dirname(this.s.log), { recursive: true })
    const fd = openSync(this.s.log, 'a')
    try {
      this.proc = Bun.spawn(this.s.cmd, {
        env: { ...process.env, LANI_DRAMA3_HOST: u.hostname, LANI_DRAMA3_PORT: u.port || '8785', LANI_DRAMA3_LOCK: this.s.lock, PYTHONUNBUFFERED: '1' },
        stdin: 'ignore', stdout: fd, stderr: fd,
      })
    } finally {
      closeSync(fd)
    }
    this.ours = true
    this.starts++
    this.out(`Starting the Drama 3 worker (${this.s.cmd.join(' ')}; its output: ${this.s.log}) …`)
    const until = Date.now() + this.s.startTimeoutMs
    let said = ''
    for (;;) {
      if (this.proc.exitCode !== null) throw new Error(`the Drama 3 worker stopped (exit ${this.proc.exitCode}); its output: ${this.s.log}`)
      const h = await this.engine.health()
      if (h?.ok === true) break
      if (h?.error) {
        await this.stop()
        throw new Error(`the Drama 3 worker could not load: ${String(h.error)}`)
      }
      const waiting = h ? String(h.waiting ?? 'starting') : 'starting'
      if (waiting !== said) this.out(`  the worker waits for ${waiting}`)
      said = waiting
      if (Date.now() > until) {
        await this.stop()
        throw new Error(`the Drama 3 worker did not come up in ${Math.round(this.s.startTimeoutMs / 60_000)} min (it waited for ${waiting})`)
      }
      await Bun.sleep(Math.min(5_000, Math.max(100, this.s.pollMs / 4)))
    }
    this.out('The Drama 3 worker is up.')
  }

  /** Stops the worker this batch started (SIGTERM, then SIGKILL after 30 s): its VRAM, memory and the lock are freed. */
  async stop(): Promise<void> {
    const p = this.proc
    if (!p || !this.ours) return
    this.proc = undefined
    if (p.exitCode !== null) return
    p.kill('SIGTERM')
    const done = await Promise.race([p.exited.then(() => true), Bun.sleep(30_000).then(() => false)])
    if (!done) {
      p.kill('SIGKILL')
      await p.exited
    }
    this.out('The Drama 3 worker is stopped.')
  }
}

/**
 * The batch's memory pause: true to go on (memory is there, or came back), false to stop (a signal meanwhile). Below
 * [s].minFreeGb available it waits, checking every [s].pollMs. The batch's [lanes] share one pause; once all of them wait
 * in it (no clip in flight) and it outlasts [s].stopAfterMs, the worker the batch started is stopped, and started again
 * when memory is back.
 */
export function memoryGate(worker: Drama3Worker, s: Drama3Settings, stopping: () => boolean, out: (line: string) => void = () => {}, lanes = 2) {
  let pause: Promise<boolean> | undefined
  let waiting = 0
  const free = () => availableGb(s.meminfo) ?? Infinity
  return async (): Promise<boolean> => {
    if (stopping()) return false
    if (free() >= s.minFreeGb && !pause) return true
    pause ??= (async () => {
      out(`Paused: ${free().toFixed(1)} GB available, under ${s.minFreeGb} GB (LANI_DRAMA3_MIN_FREE_GB); checking every ${Math.round(s.pollMs / 1000)} s …`)
      const since = Date.now()
      let stopped = false
      while (!stopping() && free() < s.minFreeGb) {
        if (!stopped && worker.ours && worker.running && waiting >= lanes && Date.now() - since >= s.stopAfterMs) {
          out('  the pause goes on: the worker is stopped for now (its memory and the heavy lock freed)')
          await worker.stop()
          stopped = true
        }
        await Bun.sleep(s.pollMs)
      }
      if (stopping()) return false
      out(`Resumed: ${free().toFixed(1)} GB available.`)
      if (stopped) await worker.start()
      return true
    })().finally(() => {
      pause = undefined
    })
    waiting++
    try {
      return await pause
    } finally {
      waiting--
    }
  }
}
