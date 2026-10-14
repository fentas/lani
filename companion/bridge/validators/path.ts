// node:path for the validators' bundle (validators/build.ts): the POSIX functions the validators' modules import.

export const sep = '/'

function normalize(p: string): string {
  const abs = p.startsWith('/')
  const parts: string[] = []
  for (const s of p.split('/')) {
    if (!s || s === '.') continue
    if (s === '..') {
      if (parts.length && parts[parts.length - 1] !== '..') parts.pop()
      else if (!abs) parts.push('..')
    } else parts.push(s)
  }
  const out = (abs ? '/' : '') + parts.join('/')
  return out || (abs ? '/' : '.')
}

export const join = (...ps: string[]) => normalize(ps.filter(p => p !== '').join('/'))

export function resolve(...ps: string[]): string {
  let out = ''
  for (const p of ps) out = p.startsWith('/') ? p : out ? `${out}/${p}` : p
  return normalize(out.startsWith('/') ? out : `/${out}`)
}

export function dirname(p: string): string {
  const n = normalize(p)
  const at = n.lastIndexOf('/')
  return at < 0 ? '.' : at === 0 ? '/' : n.slice(0, at)
}

export function basename(p: string, ext?: string): string {
  const b = normalize(p).split('/').pop() ?? ''
  return ext && b.endsWith(ext) ? b.slice(0, -ext.length) : b
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

export default { sep, join, resolve, dirname, basename, extname, relative, isAbsolute }
