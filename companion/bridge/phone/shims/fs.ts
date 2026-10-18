// node:fs for the phone's bridge, over the host's files (si.lanisce.lani.phone.HostFs): the bridge reads and writes only
// under the app's <filesDir>/lani (its data and the curated content unpacked from the APK); the host refuses anything
// else (EACCES) and a write into the read-only content (EROFS). Synchronous as node's sync calls are; the errors are
// node's (code, errno, syscall, path, its message). A relative path is the project directory's, as the bridge's cwd is.
import { Buffer } from './buffer'
import { fromHost, host, sys, toHost } from './host'
import { resolve } from './path'

type Enc = BufferEncoding | null | undefined
type BufferEncoding = 'utf8' | 'utf-8' | 'hex' | 'base64' | 'base64url' | 'latin1' | 'binary' | 'ascii'
type PathLike = string | Buffer | URL

const p = (path: PathLike): string => resolve(typeof path === 'string' ? path : path instanceof Uint8Array ? path.toString() : decodeURIComponent((path as URL).pathname))
const encOf = (o: unknown): Enc => (typeof o === 'string' ? (o as Enc) : (o as { encoding?: Enc } | undefined)?.encoding)

export function existsSync(path: PathLike): boolean {
  try {
    return host().fsExists(p(path))
  } catch {
    return false
  }
}

export function readFileSync(path: PathLike | number, o?: Enc | { encoding?: Enc; flag?: string }): any {
  if (typeof path === 'number') throw new Error('readFileSync of a descriptor is not available in the phone bridge')
  const at = p(path)
  const enc = encOf(o)
  if (enc === 'utf8' || enc === 'utf-8') return sys('open', at, () => host().fsReadText(at))
  const b = Buffer.view(fromHost(sys('open', at, () => host().fsReadBytes(at))))
  return enc ? b.toString(enc) : b
}

function data(d: string | ArrayBufferView, enc?: Enc): { text?: string; bytes?: Int8Array } {
  if (typeof d === 'string') return enc && enc !== 'utf8' && enc !== 'utf-8' ? { bytes: toHost(Buffer.from(d, enc)) } : { text: d }
  return { bytes: toHost(new Uint8Array(d.buffer, d.byteOffset, d.byteLength)) }
}

function write(path: PathLike, d: string | ArrayBufferView, o: unknown, append: boolean) {
  const at = p(path)
  const flag = (o as { flag?: string } | undefined)?.flag
  const add = append || flag === 'a' || flag === 'as'
  const exclusive = flag === 'wx' || flag === 'ax'
  const { text, bytes } = data(d, encOf(o))
  sys('open', at, () => (text !== undefined ? host().fsWriteText(at, text, add, exclusive) : host().fsWriteBytes(at, bytes!, add, exclusive)))
}

export const writeFileSync = (path: PathLike, d: string | ArrayBufferView, o?: unknown) => write(path, d, o, false)
export const appendFileSync = (path: PathLike, d: string | ArrayBufferView, o?: unknown) => write(path, d, o, true)

export function mkdirSync(path: PathLike, o?: { recursive?: boolean; mode?: number } | number): string | undefined {
  const at = p(path)
  const recursive = typeof o === 'object' && !!o?.recursive
  const existed = recursive && existsSync(at)
  sys('mkdir', at, () => host().fsMkdir(at, recursive))
  return recursive && !existed ? at : undefined
}

export class Stats {
  readonly dev = 0
  readonly ino = 0
  readonly nlink = 1
  readonly uid = 0
  readonly gid = 0
  readonly rdev = 0
  readonly blksize = 4096
  readonly blocks: number
  readonly size: number
  readonly mode: number
  readonly atimeMs: number
  readonly mtimeMs: number
  readonly ctimeMs: number
  readonly birthtimeMs: number
  constructor(private readonly type: string, s: { size: number; mtimeMs: number; ctimeMs?: number; atimeMs?: number; birthtimeMs?: number; mode?: number }) {
    this.size = s.size
    this.blocks = Math.ceil(s.size / 512)
    this.mode = s.mode ?? (type === 'dir' ? 0o40755 : 0o100644)
    this.mtimeMs = s.mtimeMs
    this.ctimeMs = s.ctimeMs ?? s.mtimeMs
    this.atimeMs = s.atimeMs ?? s.mtimeMs
    this.birthtimeMs = s.birthtimeMs ?? s.mtimeMs
  }
  isFile() {
    return this.type === 'file'
  }
  isDirectory() {
    return this.type === 'dir'
  }
  isSymbolicLink() {
    return false
  }
  isFIFO() {
    return false
  }
  isSocket() {
    return false
  }
  isBlockDevice() {
    return false
  }
  isCharacterDevice() {
    return false
  }
  get mtime() {
    return new Date(this.mtimeMs)
  }
  get ctime() {
    return new Date(this.ctimeMs)
  }
  get atime() {
    return new Date(this.atimeMs)
  }
  get birthtime() {
    return new Date(this.birthtimeMs)
  }
}

export function statSync(path: PathLike, o?: { throwIfNoEntry?: boolean }): Stats {
  const at = p(path)
  try {
    const s = JSON.parse(sys('stat', at, () => host().fsStat(at)))
    return new Stats(s.type, s)
  } catch (e) {
    if (o?.throwIfNoEntry === false && (e as { code?: string }).code === 'ENOENT') return undefined as unknown as Stats
    throw e
  }
}

export const lstatSync = statSync

export class Dirent {
  constructor(
    readonly name: string,
    private readonly type: string,
    readonly parentPath: string,
  ) {}
  get path() {
    return this.parentPath
  }
  isFile() {
    return this.type === 'file'
  }
  isDirectory() {
    return this.type === 'dir'
  }
  isSymbolicLink() {
    return false
  }
}

export function readdirSync(path: PathLike, o?: { withFileTypes?: boolean; encoding?: Enc; recursive?: boolean } | Enc): any[] {
  const at = p(path)
  const entries = JSON.parse(sys('scandir', at, () => host().fsReaddir(at))) as [string, string][]
  const withTypes = typeof o === 'object' && !!o?.withFileTypes
  if (typeof o === 'object' && o?.recursive) {
    const out: (string | Dirent)[] = []
    const walk = (dir: string, rel: string, list: [string, string][]) => {
      for (const [name, type] of list) {
        const r = rel ? `${rel}/${name}` : name
        out.push(withTypes ? new Dirent(name, type, dir) : r)
        if (type === 'dir') walk(`${dir}/${name}`, r, JSON.parse(sys('scandir', `${dir}/${name}`, () => host().fsReaddir(`${dir}/${name}`))))
      }
    }
    walk(at, '', entries)
    return out
  }
  return withTypes ? entries.map(([name, type]) => new Dirent(name, type, at)) : entries.map(([name]) => name)
}

export function renameSync(from: PathLike, to: PathLike) {
  const a = p(from)
  const b = p(to)
  sys('rename', a, () => host().fsRename(a, b), b)
}

export function unlinkSync(path: PathLike) {
  const at = p(path)
  sys('unlink', at, () => host().fsUnlink(at))
}

export function rmdirSync(path: PathLike, o?: { recursive?: boolean }) {
  const at = p(path)
  if (o?.recursive) return rmSync(at, { recursive: true, force: true })
  sys('rmdir', at, () => host().fsRmdir(at))
}

export function rmSync(path: PathLike, o?: { recursive?: boolean; force?: boolean }) {
  const at = p(path)
  sys('rm', at, () => host().fsRm(at, !!o?.recursive, !!o?.force))
}

export function copyFileSync(from: PathLike, to: PathLike, mode = 0) {
  const a = p(from)
  const b = p(to)
  // COPYFILE_EXCL (1): fails when [to] is there
  sys('copyfile', a, () => host().fsCopy(a, b, (mode & 1) === 1), b)
}

export function cpSync(from: PathLike, to: PathLike, o?: { recursive?: boolean; force?: boolean; errorOnExist?: boolean }) {
  const a = p(from)
  const b = p(to)
  const s = statSync(a)
  if (!s.isDirectory()) return copyFileSync(a, b, o?.errorOnExist && o?.force === false ? 1 : 0)
  if (!o?.recursive) throw Object.assign(new Error(`Recursive option is required to copy a directory: ${a}`), { code: 'ERR_FS_EISDIR' })
  mkdirSync(b, { recursive: true })
  for (const name of readdirSync(a)) cpSync(`${a}/${name}`, `${b}/${name}`, o)
}

/** A hard link's semantics, made by a copy: EEXIST when [to] is there (pairing.ts makes its key with it). */
export function linkSync(from: PathLike, to: PathLike) {
  const a = p(from)
  const b = p(to)
  sys('link', a, () => host().fsCopy(a, b, true), b)
}

export function realpathSync(path: PathLike): string {
  const at = p(path)
  return sys('realpath', at, () => host().fsRealpath(at))
}
realpathSync.native = realpathSync

export function chmodSync(path: PathLike, _mode: number) {
  const at = p(path)
  sys('chmod', at, () => host().fsChmod(at))
}

export function mkdtempSync(prefix: string): string {
  for (let i = 0; i < 100; i++) {
    const dir = `${prefix}${Math.random().toString(36).slice(2, 8)}`
    if (existsSync(dir)) continue
    mkdirSync(dir)
    return p(dir)
  }
  throw new Error(`mkdtemp: no free name for ${prefix}`)
}

// --- descriptors: only what a lock file needs (pairing.ts withLock: open 'wx', close) ---------------------------------

const fds = new Map<number, string>()
let nextFd = 100

export function openSync(path: PathLike, flags: string | number = 'r', _mode?: number): number {
  const at = p(path)
  if (flags === 'wx' || flags === 'ax') sys('open', at, () => host().fsWriteText(at, '', false, true))
  else if (flags === 'w') sys('open', at, () => host().fsWriteText(at, '', false, false))
  else if (flags === 'a') sys('open', at, () => host().fsWriteText(at, '', true, false))
  else if (!existsSync(at)) throw sys('open', at, () => host().fsStat(at)) // ENOENT as node's
  const fd = nextFd++
  fds.set(fd, at)
  return fd
}

export function closeSync(fd: number) {
  fds.delete(fd)
}

export function writeSync(fd: number, d: string | ArrayBufferView): number {
  const at = fds.get(fd)
  if (!at) throw Object.assign(new Error('EBADF: bad file descriptor, write'), { code: 'EBADF' })
  appendFileSync(at, d)
  return typeof d === 'string' ? Buffer.byteLength(d) : d.byteLength
}

export function fsyncSync(_fd: number) {}

/** No file watching on the phone: nothing there changes the bridge's files behind its back. */
export function watch(_path: PathLike, _o?: unknown, _l?: unknown) {
  return { close() {}, on() { return this }, ref() { return this }, unref() { return this } }
}

export function watchFile() {}
export function unwatchFile() {}

export const constants = { F_OK: 0, R_OK: 4, W_OK: 2, X_OK: 1, COPYFILE_EXCL: 1, O_CREAT: 64, O_EXCL: 128, O_WRONLY: 1, O_RDONLY: 0 }

export function accessSync(path: PathLike, _mode?: number) {
  if (!existsSync(path)) statSync(path)
}

// --- the callback-less promises (node:fs/promises) ---------------------------------------------------------------------

const later = <A extends unknown[], R>(f: (...a: A) => R) => async (...a: A): Promise<R> => f(...a)

export const promises = {
  readFile: later(readFileSync),
  writeFile: later(writeFileSync),
  appendFile: later(appendFileSync),
  mkdir: later(mkdirSync),
  readdir: later(readdirSync),
  stat: later(statSync),
  lstat: later(statSync),
  rename: later(renameSync),
  unlink: later(unlinkSync),
  rm: later(rmSync),
  rmdir: later(rmdirSync),
  copyFile: later(copyFileSync),
  cp: later(cpSync),
  realpath: later(realpathSync),
  chmod: later(chmodSync),
  mkdtemp: later(mkdtempSync),
  access: later(accessSync),
  /** A file handle: what the offline voice's download writes with (offline-voice.ts), by appends and positioned writes. */
  open: async (path: PathLike, flags: string = 'r') => {
    const at = p(path)
    if (flags.startsWith('w')) writeFileSync(at, '')
    else if (flags.startsWith('a') && !existsSync(at)) writeFileSync(at, '')
    else if (!existsSync(at)) statSync(at)
    return {
      fd: openSync(at, 'r'),
      async write(b: Uint8Array | string, offset = 0, length?: number) {
        const bytes = typeof b === 'string' ? Buffer.from(b) : b.subarray(offset, length === undefined ? undefined : offset + length)
        appendFileSync(at, bytes)
        return { bytesWritten: bytes.length, buffer: b }
      },
      async read(): Promise<never> {
        throw new Error('FileHandle.read is not available in the phone bridge')
      },
      async readFile(o?: Enc | { encoding?: Enc }) {
        return readFileSync(at, o)
      },
      async writeFile(d: string | Uint8Array) {
        writeFileSync(at, d)
      },
      async stat() {
        return statSync(at)
      },
      async truncate(n = 0) {
        const b = readFileSync(at) as Buffer
        writeFileSync(at, b.subarray(0, n))
      },
      async sync() {},
      async datasync() {},
      async close() {},
    }
  },
}

export default {
  existsSync, readFileSync, writeFileSync, appendFileSync, mkdirSync, statSync, lstatSync, readdirSync, renameSync, unlinkSync, rmdirSync, rmSync,
  copyFileSync, cpSync, linkSync, realpathSync, chmodSync, mkdtempSync, openSync, closeSync, writeSync, fsyncSync, watch, watchFile, unwatchFile,
  accessSync, constants, promises, Stats, Dirent,
}
