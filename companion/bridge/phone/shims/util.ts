// node:util for the phone's bridge: what its modules import (stripVTControlCharacters; parseArgs only in the CLIs, which
// aren't on the phone), and format/inspect for the console (prelude.ts).

export const stripVTControlCharacters = (s: string) => s.replace(/\u001b\[[0-9;?]*[ -\/]*[@-~]|\u001b\][^\u0007]*\u0007/g, '')

export function parseArgs(): never {
  throw new Error('util.parseArgs: the command-line tools are not on the phone')
}

/** A value as the console writes it: a string as it is, an error with its stack, anything else as JSON where it can be. */
export function inspect(v: unknown): string {
  if (typeof v === 'string') return v
  if (v instanceof Error) return v.stack ? `${v.name}: ${v.message}\n${v.stack}` : `${v.name}: ${v.message}`
  if (typeof v === 'function') return `[Function ${v.name || '(anonymous)'}]`
  if (typeof v === 'bigint') return `${v}n`
  if (typeof v === 'symbol' || v === undefined) return String(v)
  try {
    const seen = new WeakSet()
    return JSON.stringify(v, (_k, x) => {
      if (typeof x === 'bigint') return `${x}n`
      if (x && typeof x === 'object') {
        if (seen.has(x)) return '[Circular]'
        seen.add(x)
        if (x instanceof Map) return Object.fromEntries(x)
        if (x instanceof Set) return [...x]
      }
      return x
    }) ?? String(v)
  } catch {
    return String(v)
  }
}

/** printf-like as node's: %s %d %i %f %j %o %O %%, the rest joined with spaces. */
export function format(f: unknown, ...a: unknown[]): string {
  if (typeof f !== 'string') return [f, ...a].map(inspect).join(' ')
  let i = 0
  const out = f.replace(/%[sdifjoO%]/g, m => {
    if (m === '%%') return '%'
    if (i >= a.length) return m
    const x = a[i++]
    switch (m) {
      case '%s': return typeof x === 'string' ? x : inspect(x)
      case '%d':
      case '%i': return String(m === '%i' ? Math.trunc(Number(x)) : Number(x))
      case '%f': return String(Number(x))
      default: return inspect(x)
    }
  })
  return [out, ...a.slice(i).map(inspect)].join(' ')
}

export const promisify = <T extends (...a: any[]) => void>(f: T) => (...a: unknown[]) =>
  new Promise((resolve, reject) => f(...a, (e: unknown, v: unknown) => (e ? reject(e) : resolve(v))))

export const types = { isPromise: (v: unknown) => v instanceof Promise }

export default { stripVTControlCharacters, parseArgs, inspect, format, promisify, types }
