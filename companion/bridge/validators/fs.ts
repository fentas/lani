// node:fs for the validators' bundle (validators/build.ts): an in-memory file tree, for a JS engine without Node (the app's
// QuickJS or JavaScriptEngine: docs/plans/05-phase-0.md, check 4). The validators read; a culture pack's check and the
// dictionary read whole directories, so the app mounts the files first (lani.mount, validate-entry.ts), at the paths it
// names in the call. Writing is the stores', never a validator's: it throws.

const files = new Map<string, string>()
/** Each directory with its children's names (a file or a directory), made as files are mounted. */
const dirs = new Map<string, Set<string>>()
/** A stand-in modification time per path: the stores cache by it (FormsBook, the cast); a mount changes it. */
const mtimes = new Map<string, number>()
let clock = 1

const norm = (p: string) => {
  const parts: string[] = []
  for (const s of String(p).split('/')) {
    if (!s || s === '.') continue
    if (s === '..') parts.pop()
    else parts.push(s)
  }
  return '/' + parts.join('/')
}

function enoent(syscall: string, p: string): Error {
  const e = new Error(`ENOENT: no such file or directory, ${syscall} '${p}'`) as Error & { code: string; syscall: string; path: string }
  e.code = 'ENOENT'
  e.syscall = syscall
  e.path = p
  return e
}

/** Puts [text] at [path], its directories made. */
export function mount(path: string, text: string) {
  const p = norm(path)
  files.set(p, text)
  mtimes.set(p, clock++)
  let child = p
  for (let at = p.lastIndexOf('/'); at >= 0; at = child.lastIndexOf('/')) {
    const parent = at === 0 ? '/' : child.slice(0, at)
    const name = child.slice(at + 1)
    const set = dirs.get(parent) ?? new Set<string>()
    set.add(name)
    dirs.set(parent, set)
    if (parent === '/') break
    child = parent
  }
}

/** Takes the file at [path] away (its directories stay); whether there was one. */
export function unmount(path: string): boolean {
  const p = norm(path)
  if (!files.delete(p)) return false
  mtimes.delete(p)
  const at = p.lastIndexOf('/')
  dirs.get(at === 0 ? '/' : p.slice(0, at))?.delete(p.slice(at + 1))
  return true
}

/** Mounts a copy of each file [rels] names under [from] at the same place under [to]; how many there were. */
export function copy(from: string, to: string, rels: string[]): number {
  let n = 0
  for (const r of rels) {
    const t = files.get(norm(`${from}/${r}`))
    if (t === undefined) continue
    mount(`${to}/${r}`, t)
    n++
  }
  return n
}

/** Forgets every file. */
export function unmountAll() {
  files.clear()
  dirs.clear()
  mtimes.clear()
}

/** How many files are mounted. */
export const mounted = () => files.size

export function existsSync(path: string): boolean {
  const p = norm(path)
  return files.has(p) || dirs.has(p)
}

export function readFileSync(path: string, _enc?: unknown): string {
  const p = norm(path)
  const t = files.get(p)
  if (t === undefined) throw enoent('open', String(path))
  return t
}

export function readdirSync(path: string, _o?: unknown): string[] {
  const set = dirs.get(norm(path))
  if (!set) throw enoent('scandir', String(path))
  return [...set].sort()
}

export function statSync(path: string, _o?: unknown) {
  const p = norm(path)
  const file = files.has(p)
  if (!file && !dirs.has(p)) throw enoent('stat', String(path))
  const mtimeMs = mtimes.get(p) ?? 0
  return { isFile: () => file, isDirectory: () => !file, mtimeMs, size: file ? files.get(p)!.length : 0 }
}

const readOnly = (what: string) => () => {
  throw new Error(`${what}: the validators' file system is read-only`)
}
export const writeFileSync = readOnly('writeFileSync')
export const renameSync = readOnly('renameSync')
export const unlinkSync = readOnly('unlinkSync')
export const appendFileSync = readOnly('appendFileSync')
export const rmSync = readOnly('rmSync')
export const copyFileSync = readOnly('copyFileSync')
/** The stores make their tutor directories when they're built; the validators never build one. */
export const mkdirSync = (_p: string, _o?: unknown) => undefined

export default { existsSync, readFileSync, readdirSync, statSync, writeFileSync, renameSync, unlinkSync, appendFileSync, rmSync, copyFileSync, mkdirSync }
