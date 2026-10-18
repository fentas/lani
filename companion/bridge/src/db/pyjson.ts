// Python's json module, as the learner-data scripts use it: loads() as CPython's C scanner reads (the same values: ints
// and floats apart, dicts in their order; the same errors, "Expecting ',' delimiter: line 3 column 5 (char 41)"), and
// dump(indent=2, ensure_ascii=False) and dumps(ensure_ascii=False) as it writes. A file written here and one written
// by the Python are the same bytes.
import { F, floatRepr, PyDict, PyError, PyFloat, strRepr, type Py } from './py'

// --- loads ---------------------------------------------------------------------------------------------------------

/** The JSONDecodeError of [msg] at UTF-16 index [pos] of [doc]: line, column and char counted in code points. */
function decodeError(msg: string, doc: string, pos: number): PyError {
  let cp = 0
  let line = 1
  let lastNl = -1
  for (let i = 0; i < pos && i < doc.length; i++) {
    const c = doc.charCodeAt(i)
    if (c >= 0xd800 && c <= 0xdbff && i + 1 < pos) {
      const d = doc.charCodeAt(i + 1)
      if (d >= 0xdc00 && d <= 0xdfff) i++
    }
    if (c === 10) {
      line++
      lastNl = cp
    }
    cp++
  }
  return new PyError('JSONDecodeError', `${msg}: line ${line} column ${cp - lastNl} (char ${cp})`)
}

class StopIteration {
  constructor(readonly idx: number) {}
}

const WS = (c: string) => c === ' ' || c === '\t' || c === '\n' || c === '\r'

function skipWs(s: string, i: number): number {
  while (i < s.length && WS(s[i])) i++
  return i
}

const BACKSLASH: Record<string, string> = { '"': '"', '\\': '\\', '/': '/', b: '\b', f: '\f', n: '\n', r: '\r', t: '\t' }

function hex4(s: string, i: number): number | null {
  const h = s.slice(i, i + 4)
  return /^[0-9A-Fa-f]{4}$/.test(h) ? parseInt(h, 16) : null
}

/** _json.scanstring: [end] is just after the opening quote. */
function scanString(s: string, end: number): [string, number] {
  const begin = end - 1
  let out = ''
  for (;;) {
    let i = end
    while (i < s.length) {
      const c = s.charCodeAt(i)
      if (c === 0x22 || c === 0x5c || c <= 0x1f) break
      i++
    }
    if (i >= s.length) throw decodeError('Unterminated string starting at', s, begin)
    out += s.slice(end, i)
    const c = s[i]
    if (c === '"') return [out, i + 1]
    if (c !== '\\') throw decodeError('Invalid control character at', s, i)
    // a backslash
    if (i + 1 >= s.length) throw decodeError('Unterminated string starting at', s, begin)
    const esc = s[i + 1]
    if (esc !== 'u') {
      const ch = BACKSLASH[esc]
      if (ch === undefined) throw decodeError('Invalid \\escape', s, i)
      out += ch
      end = i + 2
      continue
    }
    let u = hex4(s, i + 2)
    if (u === null) throw decodeError('Invalid \\uXXXX escape', s, i + 1)
    end = i + 6
    if (u >= 0xd800 && u <= 0xdbff && s[end] === '\\' && s[end + 1] === 'u') {
      const u2 = hex4(s, end + 2)
      if (u2 === null) throw decodeError('Invalid \\uXXXX escape', s, end + 1)
      if (u2 >= 0xdc00 && u2 <= 0xdfff) {
        u = 0x10000 + (((u - 0xd800) << 10) | (u2 - 0xdc00))
        end += 6
      }
    }
    out += String.fromCodePoint(u)
  }
}

const NUMBER = /-?(?:0|[1-9][0-9]*)(\.[0-9]+)?([eE][-+]?[0-9]+)?/y

function scanOnce(s: string, idx: number): [Py, number] {
  if (idx >= s.length) throw new StopIteration(idx)
  const c = s[idx]
  if (c === '"') return scanString(s, idx + 1)
  if (c === '{') return scanObject(s, idx + 1)
  if (c === '[') return scanArray(s, idx + 1)
  if (c === 'n' && s.startsWith('null', idx)) return [null, idx + 4]
  if (c === 't' && s.startsWith('true', idx)) return [true, idx + 4]
  if (c === 'f' && s.startsWith('false', idx)) return [false, idx + 5]
  if (c === 'N' && s.startsWith('NaN', idx)) return [F(NaN), idx + 3]
  if (c === 'I' && s.startsWith('Infinity', idx)) return [F(Infinity), idx + 8]
  if (c === '-' && s.startsWith('-Infinity', idx)) return [F(-Infinity), idx + 9]
  NUMBER.lastIndex = idx
  const m = NUMBER.exec(s)
  if (!m) throw new StopIteration(idx)
  const text = m[0]
  if (m[1] !== undefined || m[2] !== undefined) return [F(Number(text)), idx + text.length]
  const n = Number(text)
  return [Number.isSafeInteger(n) ? (n === 0 ? 0 : n) : BigInt(text), idx + text.length]
}

function value(s: string, idx: number): [Py, number] {
  try {
    return scanOnce(s, idx)
  } catch (e) {
    if (e instanceof StopIteration) throw decodeError('Expecting value', s, e.idx)
    throw e
  }
}

function scanObject(s: string, end: number): [PyDict, number] {
  const out = new PyDict()
  let next = s[end] ?? ''
  if (next !== '"') {
    if (next && WS(next)) {
      end = skipWs(s, end)
      next = s[end] ?? ''
    }
    if (next === '}') return [out, end + 1]
    if (next !== '"') throw decodeError('Expecting property name enclosed in double quotes', s, end)
  }
  end += 1
  for (;;) {
    let key: string
    ;[key, end] = scanString(s, end)
    if (s[end] !== ':') {
      end = skipWs(s, end)
      if (s[end] !== ':') throw decodeError("Expecting ':' delimiter", s, end)
    }
    end = skipWs(s, end + 1)
    let v: Py
    ;[v, end] = value(s, end)
    out.set(key, v)
    end = skipWs(s, end)
    next = s[end] ?? ''
    end += 1
    if (next === '}') break
    if (next !== ',') throw decodeError("Expecting ',' delimiter", s, end - 1)
    const comma = end - 1
    end = skipWs(s, end)
    next = s[end] ?? ''
    end += 1
    if (next !== '"') {
      if (next === '}') throw decodeError('Illegal trailing comma before end of object', s, comma)
      throw decodeError('Expecting property name enclosed in double quotes', s, end - 1)
    }
  }
  return [out, end]
}

function scanArray(s: string, end: number): [Py[], number] {
  const out: Py[] = []
  end = skipWs(s, end)
  if (s[end] === ']') return [out, end + 1]
  for (;;) {
    let v: Py
    ;[v, end] = value(s, end)
    out.push(v)
    end = skipWs(s, end)
    const next = s[end] ?? ''
    end += 1
    if (next === ']') break
    if (next !== ',') throw decodeError("Expecting ',' delimiter", s, end - 1)
    const comma = end - 1
    end = skipWs(s, end)
    if (s[end] === ']') throw decodeError('Illegal trailing comma before end of array', s, comma)
  }
  return [out, end]
}

/** json.loads(s) */
export function loads(s: string): Py {
  if (s.charCodeAt(0) === 0xfeff) throw decodeError('Unexpected UTF-8 BOM (decode using utf-8-sig)', s, 0)
  let [v, end] = value(s, skipWs(s, 0))
  end = skipWs(s, end)
  if (end !== s.length) throw decodeError('Extra data', s, end)
  return v
}

// --- dump ----------------------------------------------------------------------------------------------------------

function encodeStr(s: string): string {
  let out = '"'
  let from = 0
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i)
    if (c > 0x22 && c !== 0x5c) continue
    if (c !== 0x22 && c !== 0x5c && c >= 0x20) continue
    out += s.slice(from, i)
    from = i + 1
    if (c === 0x22) out += '\\"'
    else if (c === 0x5c) out += '\\\\'
    else if (c === 0x0a) out += '\\n'
    else if (c === 0x0d) out += '\\r'
    else if (c === 0x09) out += '\\t'
    else if (c === 0x08) out += '\\b'
    else if (c === 0x0c) out += '\\f'
    else out += '\\u' + c.toString(16).padStart(4, '0')
  }
  return out + s.slice(from) + '"'
}

function encodeScalar(v: Py): string {
  if (v === null || v === undefined) return 'null'
  if (v === true) return 'true'
  if (v === false) return 'false'
  if (typeof v === 'string') return encodeStr(v)
  if (typeof v === 'bigint') return v.toString()
  if (typeof v === 'number' || v instanceof PyFloat) {
    const x = typeof v === 'number' ? v : v.v
    if (typeof v === 'number' && Number.isInteger(v)) return String(v === 0 ? 0 : v)
    if (Number.isNaN(x)) return 'NaN'
    if (!Number.isFinite(x)) return x > 0 ? 'Infinity' : '-Infinity'
    return floatRepr(x)
  }
  throw new PyError('TypeError', `Object of type ${typeof v} is not JSON serializable`)
}

/** A key as json.dump writes it: a str; an int, float, bool or None as its JSON text. */
function jsonKey(k: Py): string {
  if (typeof k === 'string') return k
  if (k === null) return 'null'
  if (typeof k === 'boolean') return k ? 'true' : 'false'
  return encodeScalar(k)
}

function encode(v: Py, indent: string | null, level: number, out: string[]): void {
  if (Array.isArray(v)) {
    if (!v.length) return void out.push('[]')
    const nl = indent === null ? '' : '\n' + indent.repeat(level + 1)
    const sep = indent === null ? ', ' : ',' + nl
    out.push('[' + nl)
    v.forEach((x, i) => {
      if (i) out.push(sep)
      encode(x, indent, level + 1, out)
    })
    out.push(indent === null ? ']' : '\n' + indent.repeat(level) + ']')
    return
  }
  if (v instanceof PyDict) {
    if (!v.size) return void out.push('{}')
    const nl = indent === null ? '' : '\n' + indent.repeat(level + 1)
    const sep = indent === null ? ', ' : ',' + nl
    out.push('{' + nl)
    let first = true
    for (const [at, x] of v.m) {
      if (!first) out.push(sep)
      first = false
      out.push(encodeStr(jsonKey(v.keyAt(at))), ': ')
      encode(x, indent, level + 1, out)
    }
    out.push(indent === null ? '}' : '\n' + indent.repeat(level) + '}')
    return
  }
  out.push(encodeScalar(v))
}

/** json.dumps(v, indent=2, ensure_ascii=False): what json.dump writes (without the newline the scripts add). */
export function dumpIndented(v: Py): string {
  const out: string[] = []
  encode(v, '  ', 0, out)
  return out.join('')
}

/** json.dumps(v, ensure_ascii=False): one line, ", " and ": " between items. */
export function dumpsCompact(v: Py): string {
  const out: string[] = []
  encode(v, null, 0, out)
  return out.join('')
}

/** A lone surrogate can't be written as UTF-8: Python's UnicodeEncodeError (the position is the whole text's). */
export function checkEncodable(text: string): void {
  let cp = 0
  for (let i = 0; i < text.length; i++, cp++) {
    const c = text.charCodeAt(i)
    if (c < 0xd800 || c > 0xdfff) continue
    const d = text.charCodeAt(i + 1)
    if (c <= 0xdbff && d >= 0xdc00 && d <= 0xdfff) {
      i++
      continue
    }
    throw new PyError('UnicodeEncodeError', `'utf-8' codec can't encode character ${strRepr(text[i])} in position ${cp}: surrogates not allowed`)
  }
}
