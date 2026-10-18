// What the phone's bridge needs of a JavaScript runtime that QuickJS doesn't have, installed as globals before any of
// the bridge's modules runs (src/phone/entry.ts imports this first): process (its env from the host), console (to the
// app's log), Buffer, URL and URLSearchParams, Headers, Request, Response, ReadableStream, AbortController and
// AbortSignal, TextEncoder and TextDecoder, Event, EventTarget, DOMException, the timers, queueMicrotask, fetch, Bun,
// crypto's randomUUID and getRandomValues, structuredClone (by JSON: plain data) and performance.now;
// and two of ICU's answers QuickJS gives differently: toLocaleDateString('sv') (the local YYYY-MM-DD the bridge reads
// "today" by) and localeCompare (a collator's order, the host's ICU, for anything but plain ids).
import { Buffer } from './buffer'
import { Bun } from './bun'
import { host } from './host'
import { clearImmediate, clearInterval, clearTimeout, fetch, loop, queueMicrotask, setImmediate, setInterval, setTimeout } from './loop'
import { URL, URLSearchParams } from './url'
import { format } from './util'
import { getRandomValues, randomUUID } from './crypto'
import { AbortController, AbortSignal, DOMException, Event, EventTarget, Headers, ReadableStream, Request, Response, TextDecoder, TextEncoder } from './web'

const g = globalThis as unknown as Record<string, unknown>

// --- process ---------------------------------------------------------------------------------------------------------

type Handler = (...a: unknown[]) => void

const handlers = new Map<string, Handler[]>()
const config = JSON.parse(host().config()) as { projectDir: string; dataDir: string; home: string; pid?: number }
const env = JSON.parse(host().env()) as Record<string, string>
const write = (level: string) => ({
  write: (s: string | Uint8Array) => {
    host().log(level, typeof s === 'string' ? s.replace(/\n$/, '') : Buffer.view(s).toString().replace(/\n$/, ''))
    return true
  },
  on: () => undefined,
  once: () => undefined,
  isTTY: false,
})

/** Thrown by process.exit: the phone's bridge never ends the app's process; the host logs it and stops the bridge. */
export class ProcessExit extends Error {
  constructor(readonly code: number) {
    super(`process.exit(${code})`)
  }
}

export const process = {
  env,
  argv: ['lani-phone'],
  execArgv: [] as string[],
  pid: config.pid ?? 1,
  ppid: 0,
  platform: 'android',
  arch: 'arm64',
  version: 'v22.0.0',
  versions: { node: '22.0.0', bun: Bun.version, quickjs: '1' },
  release: { name: 'lani-phone' },
  exitCode: undefined as number | undefined,
  cwd: () => config.projectDir,
  chdir: () => {
    throw new Error('process.chdir: not on the phone')
  },
  on(ev: string, f: Handler) {
    handlers.set(ev, [...(handlers.get(ev) ?? []), f])
    return process
  },
  once(ev: string, f: Handler) {
    const once: Handler = (...a) => {
      process.off(ev, once)
      f(...a)
    }
    return process.on(ev, once)
  },
  off(ev: string, f: Handler) {
    handlers.set(ev, (handlers.get(ev) ?? []).filter(h => h !== f))
    return process
  },
  removeListener(ev: string, f: Handler) {
    return process.off(ev, f)
  },
  listeners: (ev: string) => [...(handlers.get(ev) ?? [])],
  emit(ev: string, ...a: unknown[]) {
    const hs = handlers.get(ev) ?? []
    for (const h of hs) h(...a)
    return hs.length > 0
  },
  exit(code = 0): never {
    process.emit('exit', code)
    throw new ProcessExit(code)
  },
  nextTick: (f: Handler, ...a: unknown[]) => queueMicrotask(() => f(...a)),
  hrtime: Object.assign((prev?: [number, number]) => {
    const ns = Bun.nanoseconds()
    const t: [number, number] = [Math.floor(ns / 1e9), ns % 1e9]
    return prev ? [t[0] - prev[0], t[1] - prev[1]] : t
  }, { bigint: () => BigInt(Bun.nanoseconds()) }),
  uptime: () => (Date.now() - started) / 1000,
  memoryUsage: () => ({ rss: 0, heapTotal: 0, heapUsed: 0, external: 0, arrayBuffers: 0 }),
  stdout: write('info'),
  stderr: write('error'),
  stdin: { on: () => undefined, once: () => undefined, isTTY: false },
  umask: () => 0o022,
  getuid: () => 0,
  kill: () => false,
}
const started = Date.now()

// --- console ---------------------------------------------------------------------------------------------------------

/** The bridge's own log (config.ts log: console.error with its "[lani-bridge]" tag) is its info, the tag left out. */
const say = (level: string) => (...a: unknown[]) =>
  a[0] === '[lani-bridge]' ? host().log(level === 'error' ? 'info' : level, format(...(a.slice(1) as [unknown, ...unknown[]]))) : host().log(level, format(...(a as [unknown, ...unknown[]])))
export const console = {
  log: say('info'),
  info: say('info'),
  debug: say('debug'),
  warn: say('warn'),
  error: say('error'),
  trace: say('debug'),
  dir: say('info'),
  table: say('info'),
  assert: (ok: unknown, ...a: unknown[]) => void (ok || say('error')('Assertion failed', ...a)),
  time: () => undefined,
  timeEnd: () => undefined,
  group: () => undefined,
  groupEnd: () => undefined,
}

// --- what ICU answers on a node ----------------------------------------------------------------------------------------

const pad = (n: number) => String(n).padStart(2, '0')
const toLocaleDateString = Date.prototype.toLocaleDateString
/** 'sv' (and sv-SE) without options: the local date as YYYY-MM-DD, as ICU's Swedish date is. */
Date.prototype.toLocaleDateString = function (this: Date, locales?: string | string[], options?: Intl.DateTimeFormatOptions) {
  const l = Array.isArray(locales) ? locales[0] : locales
  if ((l === 'sv' || l === 'sv-SE') && !options) return `${this.getFullYear()}-${pad(this.getMonth() + 1)}-${pad(this.getDate())}`
  return toLocaleDateString.call(this, locales as string, options)
}

const PLAIN = /^[a-z0-9-]*$/
/**
 * A collator's order (ICU's root, or [locales]'s), as node's localeCompare gives it: the host's. Two plain ids (lowercase
 * letters, digits, hyphens) are compared here: their order is the same as the code points'.
 */
String.prototype.localeCompare = function (this: string, that: string, locales?: string | string[]) {
  const a = String(this)
  const b = String(that)
  if (a === b) return 0
  if (PLAIN.test(a) && PLAIN.test(b)) return a < b ? -1 : 1
  return host().collate(a, b, (Array.isArray(locales) ? locales[0] : locales) ?? '')
}

// --- the globals --------------------------------------------------------------------------------------------------------

const globals: Record<string, unknown> = {
  process, console, Buffer, Bun, URL, URLSearchParams, Headers, Request, Response, ReadableStream, AbortController, AbortSignal, TextEncoder,
  TextDecoder, Event, EventTarget, DOMException, setTimeout, setInterval, setImmediate, clearTimeout, clearInterval, clearImmediate, fetch,
  __laniLoop: loop,
}
for (const [k, v] of Object.entries(globals)) g[k] = v
if (typeof g.queueMicrotask !== 'function') g.queueMicrotask = queueMicrotask
if (typeof g.structuredClone !== 'function') g.structuredClone = (v: unknown) => (v === undefined ? undefined : JSON.parse(JSON.stringify(v)))
if (typeof g.performance !== 'object') g.performance = { now: () => Date.now() - started, timeOrigin: started }
// the web's crypto, its two calls (the host's random)
if (typeof g.crypto !== 'object') g.crypto = { randomUUID, getRandomValues }
g.global = globalThis
// the repository's root on the phone: import.meta.dir in the bundled modules is a path under it (phone/build.ts)
g.__laniProject = config.projectDir
