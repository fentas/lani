// node:path for the phone's bridge: POSIX paths; a relative path resolves against the bridge's cwd (process.cwd(): the
// project directory, as a node's bridge runs in its checkout).

export const sep = '/'
export const delimiter = ':'

export function normalize(p: string): string {
  const abs = p.startsWith('/')
  const trailing = p.length > 1 && p.endsWith('/')
  const parts: string[] = []
  for (const s of p.split('/')) {
    if (!s || s === '.') continue
    if (s === '..') {
      if (parts.length && parts[parts.length - 1] !== '..') parts.pop()
      else if (!abs) parts.push('..')
    } else parts.push(s)
  }
  const out = (abs ? '/' : '') + parts.join('/')
  return (out || (abs ? '/' : '.')) + (trailing && parts.length ? '/' : '')
}

export function join(...ps: string[]): string {
  for (const x of ps) if (typeof x !== 'string') throw new TypeError(`The "path" argument must be of type string. Received ${typeof x}`)
  const joined = ps.filter(x => x !== '').join('/')
  return joined ? normalize(joined) : '.'
}

const cwd = () => (globalThis as { process?: { cwd?: () => string } }).process?.cwd?.() ?? '/'

export function resolve(...ps: string[]): string {
  let out = ''
  for (let i = ps.length - 1; i >= 0 && !out.startsWith('/'); i--) {
    const x = ps[i]
    if (typeof x !== 'string') throw new TypeError(`The "path" argument must be of type string. Received ${typeof x}`)
    if (x) out = out ? `${x}/${out}` : x
  }
  if (!out.startsWith('/')) out = `${cwd()}/${out}`
  const n = normalize(out)
  return n.length > 1 && n.endsWith('/') ? n.slice(0, -1) : n
}

export function dirname(p: string): string {
  if (!p) return '.'
  const s = p.length > 1 ? p.replace(/\/+$/, '') : p
  const at = s.lastIndexOf('/')
  return at < 0 ? '.' : at === 0 ? '/' : s.slice(0, at)
}

export function basename(p: string, ext?: string): string {
  const s = p.length > 1 ? p.replace(/\/+$/, '') : p
  const b = s.slice(s.lastIndexOf('/') + 1)
  return ext && b !== ext && b.endsWith(ext) ? b.slice(0, -ext.length) : b
}

export function extname(p: string): string {
  const b = basename(p)
  const at = b.lastIndexOf('.')
  return at <= 0 ? '' : b.slice(at)
}

export function relative(from: string, to: string): string {
  const a = resolve(from).split('/').filter(Boolean)
  const b = resolve(to).split('/').filter(Boolean)
  let i = 0
  while (i < a.length && i < b.length && a[i] === b[i]) i++
  return [...a.slice(i).map(() => '..'), ...b.slice(i)].join('/')
}

export const isAbsolute = (p: string) => p.startsWith('/')

export function parse(p: string) {
  const root = p.startsWith('/') ? '/' : ''
  const base = basename(p)
  const ext = extname(p)
  return { root, dir: dirname(p), base, ext, name: ext ? base.slice(0, -ext.length) : base }
}

export function format(o: { root?: string; dir?: string; base?: string; name?: string; ext?: string }): string {
  const base = o.base ?? `${o.name ?? ''}${o.ext ?? ''}`
  const dir = o.dir ?? o.root ?? ''
  return dir ? (dir.endsWith('/') ? `${dir}${base}` : `${dir}/${base}`) : base
}

export const toNamespacedPath = (p: string) => p

const path = { sep, delimiter, normalize, join, resolve, dirname, basename, extname, relative, isAbsolute, parse, format, toNamespacedPath }
export const posix = path
export default { ...path, posix: path }
