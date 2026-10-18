// The `Bun` global for the phone's bridge (prelude.ts installs it): what the bridge's code calls of Bun outside a node's
// own parts. file (a file as a body or read: the clips, the recordings), sleep and sleepSync, which (no programs: null),
// spawnSync over the dispatcher (scripts.ts: the learner-data scripts, git); spawn, serve and listen aren't on the
// phone and say so.
import { Buffer } from './buffer'
import { existsSync, readFileSync, statSync, writeFileSync } from './fs'
import { copyOf, fromHost, host, toHost } from './host'
import { dispatch, enoent } from './scripts'
import { ReadableStream, type FileBody } from './web'

const TYPES: Record<string, string> = {
  mp3: 'audio/mpeg', m4a: 'audio/mp4', ogg: 'audio/ogg', webm: 'audio/webm', wav: 'audio/wav', json: 'application/json;charset=utf-8',
  html: 'text/html;charset=utf-8', js: 'text/javascript;charset=utf-8', css: 'text/css;charset=utf-8', png: 'image/png', jpg: 'image/jpeg',
  jpeg: 'image/jpeg', webp: 'image/webp', svg: 'image/svg+xml', txt: 'text/plain;charset=utf-8', apk: 'application/vnd.android.package-archive',
  onnx: 'application/octet-stream', gz: 'application/gzip',
}

type BunFile = FileBody & {
  name: string
  readonly size: number
  readonly lastModified: number
  exists(): Promise<boolean>
  text(): Promise<string>
  json(): Promise<unknown>
  arrayBuffer(): Promise<ArrayBuffer>
  slice(start?: number, end?: number): BunFile
  stream(): ReadableStream<Uint8Array>
}

function file(path: string, range?: [number, number | undefined]): BunFile {
  const ext = path.split('.').pop()?.toLowerCase() ?? ''
  const bytes = () => {
    const all = readFileSync(path) as Buffer
    return range ? all.subarray(range[0], range[1]) : all
  }
  return {
    _laniFile: true,
    path,
    name: path,
    type: TYPES[ext] ?? 'application/octet-stream',
    bytes,
    get size() {
      if (!existsSync(path)) return 0
      const n = statSync(path).size
      return range ? Math.max(0, Math.min(range[1] ?? n, n) - range[0]) : n
    },
    get lastModified() {
      return existsSync(path) ? statSync(path).mtimeMs : 0
    },
    exists: async () => existsSync(path),
    text: async () => bytes().toString(),
    json: async () => JSON.parse(bytes().toString()),
    arrayBuffer: async () => copyOf(bytes()).buffer as ArrayBuffer,
    slice: (start = 0, end?: number) => file(path, [(range?.[0] ?? 0) + start, end === undefined ? range?.[1] : (range?.[0] ?? 0) + end]),
    stream: () => ReadableStream.of<Uint8Array>([bytes()]),
  }
}

type SpawnOpts = { cwd?: string; env?: Record<string, string | undefined>; stdin?: unknown; stdout?: unknown; stderr?: unknown }

function spawnSync(cmd: string[] | { cmd: string[] } & SpawnOpts, o: SpawnOpts = {}) {
  const argv = Array.isArray(cmd) ? cmd : cmd.cmd
  const opts = Array.isArray(cmd) ? o : cmd
  const env = opts.env ?? (globalThis as unknown as { process: { env: Record<string, string> } }).process.env
  const stdin = opts.stdin instanceof Uint8Array ? Buffer.view(opts.stdin).toString() : typeof opts.stdin === 'string' ? opts.stdin : undefined
  const r = dispatch(argv[0], argv.slice(1), stdin, env)
  if ('missing' in r) throw enoent(argv[0])
  return { exitCode: r.status, success: r.status === 0, signalCode: null, pid: 0, stdout: Buffer.from(r.stdout), stderr: Buffer.from(r.stderr), resourceUsage: undefined }
}

/**
 * Bun.spawn over the dispatcher: a registered script or git answers at once (its output as streams, its exit code as
 * exited); a program that isn't there throws ENOENT, as Bun's does.
 */
function spawn(cmd: string[] | ({ cmd: string[] } & SpawnOpts), o: SpawnOpts = {}) {
  const r = spawnSync(cmd, o)
  const stream = (b: Buffer) => ReadableStream.of<Uint8Array>(b.length ? [b] : [])
  return {
    pid: 0,
    exitCode: r.exitCode,
    signalCode: null,
    killed: false,
    exited: Promise.resolve(r.exitCode),
    stdout: stream(r.stdout),
    stderr: stream(r.stderr),
    stdin: undefined,
    kill: () => undefined,
    ref: () => undefined,
    unref: () => undefined,
  }
}

const notHere = (what: string) => () => {
  throw new Error(`Bun.${what}: not on the phone`)
}

export const Bun = {
  version: '1.3.13',
  env: new Proxy({}, { get: (_t, k) => (globalThis as unknown as { process: { env: Record<string | symbol, string> } }).process.env[k] }),
  file: (path: string | URL) => file(typeof path === 'string' ? path : decodeURIComponent(path.pathname)),
  sleep: (ms: number | Date) => new Promise<void>(r => setTimeout(r, typeof ms === 'number' ? ms : Math.max(0, ms.getTime() - Date.now()))),
  sleepSync: (ms: number) => host().sleep(ms),
  which: (_bin: string) => null,
  spawnSync,
  spawn,
  serve: notHere('serve'),
  listen: notHere('listen'),
  connect: notHere('connect'),
  write: async (path: string, d: string | Uint8Array) => {
    writeFileSync(path, d)
    return typeof d === 'string' ? Buffer.byteLength(d) : d.byteLength
  },
  nanoseconds: () => Math.round((globalThis as unknown as { performance?: { now(): number } }).performance?.now?.() ?? Date.now()) * 1e6,
  gzipSync: (b: Uint8Array) => Buffer.view(fromHost(host().gzip(toHost(b)))),
  gunzipSync: (b: Uint8Array) => Buffer.view(fromHost(host().gunzip(toHost(b)))),
}
