// The process a script runs in, as the Python sees it: its environment (os.environ, which lani_paths changes on
// import), its working directory, stdout and stderr, the clock (datetime.now(), or LANI_NOW), and the file system
// through node:fs's sync calls, with Python's errors ("[Errno 2] No such file or directory: '/x'") and pathlib's
// paths. Only node:fs is used, and only sync calls: the phone shims them (docs/plans/05-built/db-port.md lists them).
import * as fs from 'node:fs'
import { pyErr, PyError, strRepr, valueErr, type DateTime } from './py'

export type Env = Record<string, string | undefined>

/** A node:fs call a host may lack (the phone's shim): looked up by name, so that a bundler doesn't insist on it. */
const optional = <K extends keyof typeof fs>(name: K): (typeof fs)[K] | undefined => {
  const f = (fs as Record<string, unknown>)[name]
  return typeof f === 'function' ? (f as (typeof fs)[K]) : undefined
}

/** What the host may do after a script wrote files (on a node: commit the data repository; on the phone: nothing). */
export interface DbHooks {
  /**
   * After a script wrote [paths]. [commit] is set where the Python commits a data repository (update-db.py after a
   * session: lani_data_repo.autocommit(data dir, message)); a host with git may commit then. What it returns goes to
   * stderr (the Python's "📚 data repository: …" line).
   */
  afterWrite?(e: { script: string; dataDir: string; paths: string[]; commit?: string }): string | void
}

export class Ctx {
  readonly out: string[] = []
  readonly err: string[] = []
  readonly env: Env
  readonly cwd: string
  readonly hooks: DbHooks
  /** The files written by this run (the databases, their backups), for the hooks. */
  readonly written: string[] = []
  /** Set by update-db where the Python commits the data repository (lani_data_repo.autocommit). */
  commit?: { dataDir: string; message: string }
  /** The data directory the script worked on, once it knows it. */
  dataRoot?: string
  private clock?: DateTime

  constructor(env: Env, o: { cwd?: string; hooks?: DbHooks } = {}) {
    this.env = { ...env }
    this.cwd = o.cwd ?? env.PWD ?? '/'
    this.hooks = o.hooks ?? {}
  }

  print(...xs: string[]) {
    this.out.push(xs.join(' ') + '\n')
  }
  eprint(...xs: string[]) {
    this.err.push(xs.join(' ') + '\n')
  }

  /** datetime.now(): LANI_NOW (YYYY-MM-DD[THH:MM[:SS]], a frozen clock for tests) when set, else the local time. */
  now(): DateTime {
    const frozen = frozenNow(this.env.LANI_NOW)
    if (frozen) return frozen
    if (!this.clock) {
      const d = new Date()
      this.clock = {
        y: d.getFullYear(), m: d.getMonth() + 1, d: d.getDate(),
        H: d.getHours(), M: d.getMinutes(), S: d.getSeconds(), us: d.getMilliseconds() * 1000,
      }
    }
    return { ...this.clock }
  }
}

/** lani_paths.now()'s LANI_NOW: the same format, the same leniency (anything else is the real clock). */
export function frozenNow(v: string | undefined): DateTime | undefined {
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?)?$/.exec(v ?? '')
  if (!m) return undefined
  const [y, mo, d, H, M, S] = m.slice(1).map(x => Number(x ?? 0))
  const dim = [31, y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  if (y < 1 || mo < 1 || mo > 12 || d < 1 || d > dim[mo - 1] || H > 23 || M > 59 || S > 59) return undefined
  return { y, m: mo, d, H, M, S, us: 0 }
}

// --- Paths (pathlib.PurePosixPath, as strings) ---------------------------------------------------------------------

/** str(PurePosixPath(p)) */
export function pnorm(p: string): string {
  const lead = p.startsWith('//') && !p.startsWith('///') ? '//' : p.startsWith('/') ? '/' : ''
  const parts = p.split('/').filter(s => s && s !== '.')
  return lead + parts.join('/') || '.'
}

/** str(PurePosixPath(a, b, …)) */
export function pjoin(...ps: string[]): string {
  let out = ''
  for (const p of ps) out = p.startsWith('/') ? p : out === '' ? p : `${out}/${p}`
  return pnorm(out)
}

/** PurePath.parent */
export function parent(p: string): string {
  const n = pnorm(p)
  const at = n.lastIndexOf('/')
  if (at < 0) return '.'
  const head = n.slice(0, at)
  return head === '' || head === '/' ? (n.startsWith('//') && at === 1 ? '//' : '/') : head
}

/** PurePath.name */
export function baseName(p: string): string {
  const n = pnorm(p)
  return n === '/' || n === '//' || n === '.' ? '' : n.slice(n.lastIndexOf('/') + 1)
}

/** PurePath.with_suffix(s) */
export function withSuffix(p: string, s: string): string {
  const name = baseName(p)
  const at = name.lastIndexOf('.')
  const stem = at > 0 && at < name.length - 1 ? name.slice(0, at) : name
  return pjoin(parent(p), stem + s)
}

export const isAbs = (p: string) => p.startsWith('/')

/** os.path.normpath */
export function normpath(p: string): string {
  if (p === '') return '.'
  const lead = p.startsWith('//') && !p.startsWith('///') ? '//' : p.startsWith('/') ? '/' : ''
  const out: string[] = []
  for (const c of p.split('/')) {
    if (!c || c === '.') continue
    if (c !== '..' || (!lead && !out.length) || (out.length && out[out.length - 1] === '..')) out.push(c)
    else if (out.length) out.pop()
  }
  return lead + out.join('/') || '.'
}

/** os.path.abspath, against [cwd] */
export const abspath = (p: string, cwd: string) => normpath(isAbs(p) ? p : `${cwd}/${p}`)

/** os.path.expanduser: "~" and "~/x" under HOME (a "~user" is left as it is: no password database here). */
export function expanduser(p: string, env: Env): string {
  if (!p.startsWith('~')) return p
  let i = p.indexOf('/', 1)
  if (i < 0) i = p.length
  if (i !== 1 || env.HOME === undefined) return p
  const home = env.HOME.replace(/\/+$/, '')
  return home + p.slice(i) || '/'
}

/** Path.home() */
export const home = (env: Env) => expanduser('~', env)

/** Path(p).resolve(): absolute (against [cwd]) and its symlinks resolved, as posixpath.realpath does (non-strict). */
export function resolve(p: string, cwd: string): string {
  const name = pnorm(p)
  const lstat = optional('lstatSync')
  const readlink = optional('readlinkSync')
  const rest = name.split('/').reverse() as (string | null)[]
  let count = rest.length
  let path = name.startsWith('/') ? '/' : pnorm(cwd)
  const seen = new Map<string, string | null>()
  while (count) {
    const part = rest.pop() as string | null
    if (part === null) {
      seen.set(rest.pop() as string, path)
      continue
    }
    count--
    if (!part || part === '.') continue
    if (part === '..') {
      path = path.slice(0, path.lastIndexOf('/')) || '/'
      continue
    }
    const next = path === '/' ? `/${part}` : `${path}/${part}`
    let target: string | undefined
    try {
      if (!lstat || !readlink || !lstat(next).isSymbolicLink()) {
        path = next
        continue
      }
      if (seen.has(next)) {
        const known = seen.get(next)
        path = known ?? next
        continue
      }
      target = String(readlink(next))
    } catch {
      path = next
      continue
    }
    if (target.startsWith('/')) path = '/'
    seen.set(next, null)
    rest.push(next, null)
    const tp = target.split('/').reverse()
    rest.push(...tp)
    count += tp.length
  }
  return path
}

/** PurePath.relative_to: whether [p] is [base] or under it (lexically). */
export function isUnder(p: string, base: string): boolean {
  const a = pnorm(p).split('/')
  const b = pnorm(base).split('/')
  if (b.length === 2 && b[0] === '' && b[1] === '') return a[0] === ''
  return b.length <= a.length && b.every((x, i) => x === a[i])
}

// --- Errors --------------------------------------------------------------------------------------------------------

const ERRNO: Record<string, [number, string, string]> = {
  EPERM: [1, 'Operation not permitted', 'PermissionError'],
  ENOENT: [2, 'No such file or directory', 'FileNotFoundError'],
  EIO: [5, 'Input/output error', 'OSError'],
  EACCES: [13, 'Permission denied', 'PermissionError'],
  EBUSY: [16, 'Device or resource busy', 'OSError'],
  EEXIST: [17, 'File exists', 'FileExistsError'],
  EXDEV: [18, 'Invalid cross-device link', 'OSError'],
  ENOTDIR: [20, 'Not a directory', 'NotADirectoryError'],
  EISDIR: [21, 'Is a directory', 'IsADirectoryError'],
  EINVAL: [22, 'Invalid argument', 'OSError'],
  EMFILE: [24, 'Too many open files', 'OSError'],
  ENOSPC: [28, 'No space left on device', 'OSError'],
  EROFS: [30, 'Read-only file system', 'OSError'],
  ENAMETOOLONG: [36, 'File name too long', 'OSError'],
  ENOTEMPTY: [39, 'Directory not empty', 'OSError'],
  ELOOP: [40, 'Too many levels of symbolic links', 'OSError'],
  EDQUOT: [122, 'Disk quota exceeded', 'OSError'],
}

/** An OSError as Python's: "[Errno 2] No such file or directory: '/x'" (or "'/a' -> '/b'"). */
export function osError(e: unknown, path: string, path2?: string): PyError {
  if (e instanceof PyError) return e
  const code = (e as { code?: string })?.code ?? ''
  const known = ERRNO[code]
  if (!known) return pyErr('OSError', (e as Error)?.message ?? String(e))
  const [n, text, type] = known
  return pyErr(type, `[Errno ${n}] ${text}: ${strRepr(path)}${path2 !== undefined ? ` -> ${strRepr(path2)}` : ''}`)
}

const errnoError = (code: string, path: string) => osError({ code }, path)

// --- Files ---------------------------------------------------------------------------------------------------------

/** Where the paths a run writes go (its Ctx.written; one run at a time: run() is synchronous). */
let recorder: string[] | null = null
export function recordWrites(into: string[] | null) {
  recorder = into
}
const wrote = (p: string) => recorder?.push(p)

function stat(p: string): fs.Stats | undefined {
  try {
    return fs.statSync(p)
  } catch {
    return undefined
  }
}

/** Path.exists() */
export const exists = (p: string) => stat(p) !== undefined
/** Path.is_file() */
export const isFile = (p: string) => stat(p)?.isFile() ?? false
/** Path.is_dir() */
export const isDir = (p: string) => stat(p)?.isDirectory() ?? false

/** Strict UTF-8 (Python's decoder: an invalid byte is a UnicodeDecodeError, never a replacement). */
export function utf8(b: Uint8Array): string {
  const bad = (i: number, n: number, why: string) => {
    const where = n > 1 ? `bytes in position ${i}-${i + n - 1}` : `byte 0x${b[i].toString(16).padStart(2, '0')} in position ${i}`
    return new PyError('UnicodeDecodeError', `'utf-8' codec can't decode ${where}: ${why}`)
  }
  const out: string[] = []
  let chunk: number[] = []
  for (let i = 0; i < b.length; ) {
    const c = b[i]
    let cp: number
    let n: number
    if (c < 0x80) {
      cp = c
      n = 1
    } else if (c >= 0xc2 && c <= 0xdf) {
      n = 2
      cp = c & 0x1f
    } else if (c >= 0xe0 && c <= 0xef) {
      n = 3
      cp = c & 0x0f
    } else if (c >= 0xf0 && c <= 0xf4) {
      n = 4
      cp = c & 0x07
    } else throw bad(i, 1, 'invalid start byte')
    for (let k = 1; k < n; k++) {
      const d = b[i + k]
      if (d === undefined) throw bad(i, b.length - i, 'unexpected end of data')
      const lo = k === 1 && c === 0xe0 ? 0xa0 : k === 1 && c === 0xed ? 0x80 : k === 1 && c === 0xf0 ? 0x90 : 0x80
      const hi = k === 1 && c === 0xed ? 0x9f : k === 1 && c === 0xf4 ? 0x8f : 0xbf
      if (d < lo || d > hi) throw bad(i, k, 'invalid continuation byte')
      cp = (cp << 6) | (d & 0x3f)
    }
    chunk.push(cp)
    if (chunk.length >= 4096) {
      out.push(String.fromCodePoint(...chunk))
      chunk = []
    }
    i += n
  }
  out.push(String.fromCodePoint(...chunk))
  return out.join('')
}

/** What a text-mode read sees: universal newlines ("\r\n" and "\r" are "\n"). */
export const universalNewlines = (s: string) => (s.includes('\r') ? s.replace(/\r\n?/g, '\n') : s)

/** open(p, encoding="utf-8").read() */
export function readText(p: string): string {
  let b: Uint8Array | string
  try {
    if (isDir(p)) throw { code: 'EISDIR' }
    b = fs.readFileSync(p) as Uint8Array | string
  } catch (e) {
    throw osError(e, p)
  }
  return universalNewlines(typeof b === 'string' ? b : utf8(b))
}

/** open(path, "w", encoding="utf-8"), write, flush, fsync: the file the atomic writes rename. */
export function writeText(p: string, text: string): void {
  try {
    if (isDir(p)) throw { code: 'EISDIR' }
    const open = optional('openSync')
    const write = optional('writeSync')
    const fsync = optional('fsyncSync')
    const close = optional('closeSync')
    if (open && write && fsync && close) {
      const fd = open(p, 'w')
      try {
        write(fd, text)
        fsync(fd)
      } finally {
        close(fd)
      }
    } else fs.writeFileSync(p, text)
  } catch (e) {
    throw osError(e, p)
  }
}

/** os.replace(a, b) */
export function replace(a: string, b: string): void {
  try {
    fs.renameSync(a, b)
  } catch (e) {
    throw osError(e, a, b)
  }
  wrote(b)
}

/** Path.mkdir(parents=True, exist_ok=True), as pathlib does it (its errors name the same paths). */
export function mkdirp(p: string): void {
  try {
    fs.mkdirSync(p)
  } catch (e) {
    const code = (e as { code?: string })?.code
    if (code === 'ENOENT') {
      const up = parent(p)
      if (up === pnorm(p)) throw osError(e, p)
      mkdirp(up)
      return mkdirp1(p)
    }
    if (!isDir(p)) throw osError(e, p)
  }
}

function mkdirp1(p: string) {
  try {
    fs.mkdirSync(p)
  } catch (e) {
    if (!isDir(p)) throw osError(e, p)
  }
}

/** shutil.copy2(src, dst): the bytes, then the times (where the host can set them). */
export function copy2(src: string, dst: string): void {
  if (isDir(dst)) dst = pjoin(dst, baseName(src))
  const st = stat(src)
  if (!st) throw errnoError('ENOENT', src)
  if (st.isDirectory()) throw errnoError('EISDIR', src)
  try {
    fs.copyFileSync(src, dst)
  } catch (e) {
    throw osError(e, dst)
  }
  wrote(dst)
  const utimes = optional('utimesSync')
  try {
    utimes?.(dst, st.atime, st.mtime)
  } catch {
    // the times are a courtesy
  }
}

/** os.listdir(p): names (an OSError when it can't be read). */
export function listdir(p: string): string[] {
  try {
    return (fs.readdirSync(p) as unknown[]).map(String)
  } catch (e) {
    throw osError(e, p)
  }
}

/** Path(dir).glob("*<suffix>") as pathlib matches it: every entry (dot files and directories too) ending so. */
export function globSuffix(dir: string, suffix: string): string[] {
  if (!isDir(dir)) return []
  let names: string[]
  try {
    names = listdir(dir)
  } catch {
    return []
  }
  return names.filter(n => n.endsWith(suffix)).map(n => pjoin(dir, n))
}

/** Path(dir).glob(f"{prefix}*"): the entries whose name starts so. */
export function globPrefix(dir: string, prefix: string): string[] {
  if (!isDir(dir)) return []
  try {
    return listdir(dir)
      .filter(n => n.startsWith(prefix))
      .map(n => pjoin(dir, n))
  } catch {
    return []
  }
}

/** Path.unlink() */
export function unlink(p: string): void {
  try {
    fs.unlinkSync(p)
  } catch (e) {
    throw osError(e, p)
  }
  wrote(p)
}

/** A path the Python would refuse to open (an embedded NUL byte). */
export function checkPath(p: string): void {
  if (p.includes('\0')) throw valueErr('embedded null byte')
}
