// Python's semantics, as far as the learner-data scripts (.claude/hooks/*.py) rely on them: the TypeScript port in this
// directory is a line-by-line translation of the scripts onto these helpers, so that it prints, writes and fails as they
// do (docs/plans/05-built/db-port.md).
//
// - Values: a JSON value as Python's json module reads it. A dict is a PyDict (insertion order, as a Python dict keeps
//   it: a JS object would put "0".."9" keys first). An int is a JS number (a bigint past 2^53), a float a PyFloat, so
//   that 2.0 stays 2.0 when it is written back. None is null; bool, str and list are JS's.
// - Operators: add, sub, mul, truediv, lt …, eq, truthy, round, max, int, str and repr follow Python's rules, its
//   result types (int + float is a float, round(x) an int, round(x, 2) a float) and its errors (TypeError,
//   KeyError …, with Python's messages: they end up on stderr as Python's do).
// - Strings: lengths, slices and comparisons by code point; Python's whitespace for strip() and \s.
// - Dates: strptime('%Y-%m-%d') and date arithmetic as datetime does them.

export class PyFloat {
  constructor(readonly v: number) {}
}

/** A Python float. */
export const F = (v: number) => new PyFloat(v)

export type Py = null | boolean | number | bigint | PyFloat | string | Py[] | PyDict

/**
 * A Python dict: insertion order kept, as Python keeps it. Its keys are strs (JSON's); a report can bring another key
 * (an int item_id), which is kept as Python keeps it (1, 1.0 and True are one key; json.dump writes it as "1").
 */
export class PyDict {
  readonly m = new Map<string, Py>()
  /** The keys that aren't strs, by their place in [m]. */
  private odd?: Map<string, Py>

  constructor(entries?: Iterable<readonly [string, Py]>) {
    if (entries) for (const [k, v] of entries) this.m.set(k, v)
  }

  /** A dict of [o]'s entries in their order (keys that aren't array indices: "id", "type" …). */
  static of(o: { [k: string]: Py }): PyDict {
    return new PyDict(Object.entries(o))
  }

  /** Where a key that isn't a str is kept in [m] (never a key JSON can bring: it starts with NUL and U+FFFF). */
  static slot(k: Py): string {
    if (typeof k === 'string') return k
    if (k === null) return '\u0000\uffffNone'
    if (isNum(k)) {
      const v = num(k)
      return `\u0000\uffff${typeof k === 'bigint' ? k.toString() : Number.isInteger(v) && Math.abs(v) < 2 ** 53 ? String(v) : floatRepr(v)}`
    }
    throw typeErr(`cannot use '${typeName(k)}' as a dict key (unhashable type: '${typeName(k)}')`)
  }

  /** The key kept at [slot]. */
  keyAt(slot: string): Py {
    return this.odd?.has(slot) ? (this.odd.get(slot) as Py) : slot
  }
  hasKey(k: Py): boolean {
    return this.m.has(PyDict.slot(k))
  }
  getKey(k: Py, d: Py = null): Py {
    const at = PyDict.slot(k)
    return this.m.has(at) ? (this.m.get(at) as Py) : d
  }
  setKey(k: Py, v: Py): void {
    const at = PyDict.slot(k)
    if (typeof k !== 'string' && !this.m.has(at)) (this.odd ??= new Map()).set(at, k)
    this.m.set(at, v)
  }
  deleteKey(k: Py): void {
    const at = PyDict.slot(k)
    if (!this.m.delete(at)) throw new PyError('KeyError', repr(k))
    this.odd?.delete(at)
  }

  get size(): number {
    return this.m.size
  }
  has(k: string): boolean {
    return this.m.has(k)
  }
  get(k: string, d: Py = null): Py {
    return this.m.has(k) ? (this.m.get(k) as Py) : d
  }
  set(k: string, v: Py): void {
    this.m.set(k, v)
  }
  setdefault(k: string, d: Py): Py {
    if (!this.m.has(k)) this.m.set(k, d)
    return this.m.get(k) as Py
  }
  delete(k: string): void {
    this.deleteKey(k)
  }
  /** The keys (a key that isn't a str comes as what it is, typed as a str). */
  keys(): string[] {
    return this.odd ? [...this.m.keys()].map(k => this.keyAt(k) as string) : [...this.m.keys()]
  }
  values(): Py[] {
    return [...this.m.values()]
  }
  items(): [string, Py][] {
    return this.odd ? [...this.m.entries()].map(([k, v]) => [this.keyAt(k) as string, v]) : [...this.m.entries()]
  }
  copy(): PyDict {
    const out = new PyDict(this.m)
    if (this.odd) out.odd = new Map(this.odd)
    return out
  }
  /** dict.update: existing keys keep their place, new ones go at the end. */
  update(o: PyDict | Iterable<readonly [string, Py]>): void {
    if (o instanceof PyDict) {
      for (const [at, v] of o.m) this.setKey(o.keyAt(at), v)
    } else for (const [k, v] of o) this.m.set(k, v)
  }
}

// --- Exceptions --------------------------------------------------------------------------------------------------

const BASES: Record<string, string[]> = {
  ValueError: [],
  TypeError: [],
  AttributeError: [],
  KeyError: ['LookupError'],
  IndexError: ['LookupError'],
  OverflowError: ['ArithmeticError'],
  ZeroDivisionError: ['ArithmeticError'],
  StopIteration: [],
  JSONDecodeError: ['ValueError'],
  UnicodeDecodeError: ['UnicodeError', 'ValueError'],
  UnicodeEncodeError: ['UnicodeError', 'ValueError'],
  OSError: [],
  FileNotFoundError: ['OSError'],
  FileExistsError: ['OSError'],
  IsADirectoryError: ['OSError'],
  NotADirectoryError: ['OSError'],
  PermissionError: ['OSError'],
}

/** The name Python's traceback gives an exception's type, where it isn't the plain one. */
const QUALNAMES: Record<string, string> = { JSONDecodeError: 'json.decoder.JSONDecodeError' }

/** A Python exception: its type, and str(e). */
export class PyError extends Error {
  constructor(
    readonly type: string,
    readonly msg: string,
  ) {
    super(`${type}: ${msg}`)
  }
  /** isinstance(e, [t]). */
  is(t: string): boolean {
    return t === 'Exception' || t === this.type || (BASES[this.type] ?? []).includes(t)
  }
}

/** sys.exit(code). */
export class SysExit {
  constructor(readonly code: number) {}
}

export const pyErr = (type: string, msg: string) => new PyError(type, msg)
export const typeErr = (msg: string) => new PyError('TypeError', msg)
export const valueErr = (msg: string) => new PyError('ValueError', msg)

/** isinstance(e, [t]) for anything thrown; a JS error (a bug of the port) is an Exception only. */
export function isExc(e: unknown, t: string): boolean {
  if (e instanceof PyError) return e.is(t)
  return t === 'Exception' && !(e instanceof SysExit)
}

/** str(e). */
export function excStr(e: unknown): string {
  if (e instanceof PyError) return e.msg
  return e instanceof Error ? e.message : String(e)
}

/** The last line of the traceback Python prints for [e] ("KeyError: 'correct'"). */
export function excLine(e: unknown): string {
  if (e instanceof PyError) {
    const name = QUALNAMES[e.type] ?? e.type
    return e.msg ? `${name}: ${e.msg}` : name
  }
  return `PortError: ${e instanceof Error ? e.message : String(e)}`
}

/** What Python prints for an exception nothing catches (or traceback.print_exc()): the frames can't be the same; the
 *  last line is. */
export function traceback(e: unknown): string {
  return `Traceback (most recent call last):\n  File "<lani db port>", line 0, in <module>\n${excLine(e)}\n`
}

// --- Types ---------------------------------------------------------------------------------------------------------

export function typeName(x: Py): string {
  if (x === null || x === undefined) return 'NoneType'
  if (typeof x === 'boolean') return 'bool'
  if (typeof x === 'number') return Number.isInteger(x) ? 'int' : 'float'
  if (typeof x === 'bigint') return 'int'
  if (x instanceof PyFloat) return 'float'
  if (typeof x === 'string') return 'str'
  if (Array.isArray(x)) return 'list'
  if (x instanceof PyDict) return 'dict'
  return 'object'
}

export const isDict = (x: Py): x is PyDict => x instanceof PyDict
export const isList = (x: Py): x is Py[] => Array.isArray(x)
export const isStr = (x: Py): x is string => typeof x === 'string'
export const isFloat = (x: Py): boolean => x instanceof PyFloat || (typeof x === 'number' && !Number.isInteger(x))
/** isinstance(x, int): bool counts, as in Python. */
export const isInt = (x: Py): boolean =>
  (typeof x === 'number' && Number.isInteger(x)) || typeof x === 'bigint' || typeof x === 'boolean'
/** isinstance(x, (int, float)). */
export const isNum = (x: Py): boolean => typeof x === 'number' || typeof x === 'bigint' || typeof x === 'boolean' || x instanceof PyFloat

/** A number's value as a JS number. */
export function num(x: Py): number {
  if (typeof x === 'number') return x
  if (typeof x === 'boolean') return x ? 1 : 0
  if (typeof x === 'bigint') return Number(x)
  if (x instanceof PyFloat) return x.v
  throw typeErr(`must be real number, not ${typeName(x)}`)
}

const big = (x: Py): bigint => (typeof x === 'bigint' ? x : BigInt(num(x)))
/** An int: a number while it is exact, else a bigint. */
export const intOf = (b: bigint): number | bigint => (b >= -9007199254740991n && b <= 9007199254740991n ? Number(b) : b)

function intOp(a: Py, b: Py, f: (x: number, y: number) => number, g: (x: bigint, y: bigint) => bigint): number | bigint {
  if (typeof a !== 'bigint' && typeof b !== 'bigint') {
    const r = f(num(a), num(b))
    if (Number.isSafeInteger(r)) return r
  }
  return intOf(g(big(a), big(b)))
}

// --- Operators -----------------------------------------------------------------------------------------------------

const unsupported = (op: string, a: Py, b: Py) =>
  typeErr(`unsupported operand type(s) for ${op}: '${typeName(a)}' and '${typeName(b)}'`)

/** a + b */
export function add(a: Py, b: Py): Py {
  if (isNum(a) && isNum(b)) {
    if (isFloat(a) || isFloat(b)) return F(num(a) + num(b))
    return intOp(a, b, (x, y) => x + y, (x, y) => x + y)
  }
  if (typeof a === 'string') {
    if (typeof b === 'string') return a + b
    throw typeErr(`can only concatenate str (not "${typeName(b)}") to str`)
  }
  if (Array.isArray(a)) {
    if (Array.isArray(b)) return [...a, ...b]
    throw typeErr(`can only concatenate list (not "${typeName(b)}") to list`)
  }
  throw unsupported('+', a, b)
}

/** a - b */
export function sub(a: Py, b: Py): Py {
  if (isNum(a) && isNum(b)) {
    if (isFloat(a) || isFloat(b)) return F(num(a) - num(b))
    return intOp(a, b, (x, y) => x - y, (x, y) => x - y)
  }
  throw unsupported('-', a, b)
}

/** a * b */
export function mul(a: Py, b: Py): Py {
  if (isNum(a) && isNum(b)) {
    if (isFloat(a) || isFloat(b)) return F(num(a) * num(b))
    return intOp(a, b, (x, y) => x * y, (x, y) => x * y)
  }
  const seq = (s: Py, n: Py): Py => {
    if (!isInt(n)) throw typeErr(`can't multiply sequence by non-int of type '${typeName(n)}'`)
    const k = Math.max(0, num(n))
    if (typeof s === 'string') return s.repeat(k)
    const out: Py[] = []
    for (let i = 0; i < k; i++) out.push(...(s as Py[]))
    return out
  }
  if (typeof a === 'string' || Array.isArray(a)) return seq(a, b)
  if ((typeof b === 'string' || Array.isArray(b)) && isInt(a)) return seq(b, a)
  throw unsupported('*', a, b)
}

/** a / b (true division: a float) */
export function truediv(a: Py, b: Py): PyFloat {
  if (!(isNum(a) && isNum(b))) throw unsupported('/', a, b)
  if (num(b) === 0) throw pyErr('ZeroDivisionError', 'division by zero')
  return F(num(a) / num(b))
}

/** a // b (floor division) */
export function floordiv(a: Py, b: Py): Py {
  if (!(isNum(a) && isNum(b))) throw unsupported('//', a, b)
  if (num(b) === 0) throw pyErr('ZeroDivisionError', isFloat(a) || isFloat(b) ? 'float floor division by zero' : 'integer division or modulo by zero')
  if (isFloat(a) || isFloat(b)) return F(Math.floor(num(a) / num(b)))
  const x = big(a)
  const y = big(b)
  const q = x / y
  return intOf(q * y !== x && (x < 0n) !== (y < 0n) ? q - 1n : q)
}

/** sum(f(x) for x in xs): each term made and added in turn. */
export function sumOf<T>(xs: T[], f: (x: T) => Py): Py {
  let total: Py = 0
  for (const x of xs) total = add(total, f(x))
  return total
}

/** Code-point order of two strings (JS compares UTF-16 units: they differ past U+FFFF). */
export function strCmp(a: string, b: string): number {
  if (a === b) return 0
  const n = Math.min(a.length, b.length)
  for (let i = 0; i < n; i++) {
    const x = a.charCodeAt(i)
    const y = b.charCodeAt(i)
    if (x === y) continue
    if (x >= 0xd800 || y >= 0xd800) {
      const cx = a.codePointAt(i) as number
      const cy = b.codePointAt(i) as number
      if (cx !== cy) return cx < cy ? -1 : 1
      continue
    }
    return x < y ? -1 : 1
  }
  return a.length < b.length ? -1 : a.length > b.length ? 1 : 0
}

function order(a: Py, b: Py, op: string): number {
  if (isNum(a) && isNum(b)) {
    if (typeof a === 'bigint' || typeof b === 'bigint') {
      if (!isFloat(a) && !isFloat(b)) return big(a) < big(b) ? -1 : big(a) > big(b) ? 1 : 0
    }
    const x = num(a)
    const y = num(b)
    return x < y ? -1 : x > y ? 1 : x === y ? 0 : NaN
  }
  if (typeof a === 'string' && typeof b === 'string') return strCmp(a, b)
  if (Array.isArray(a) && Array.isArray(b)) {
    const n = Math.min(a.length, b.length)
    for (let i = 0; i < n; i++) if (!eq(a[i], b[i])) return order(a[i], b[i], op)
    return a.length - b.length
  }
  throw typeErr(`'${op}' not supported between instances of '${typeName(a)}' and '${typeName(b)}'`)
}

export const lt = (a: Py, b: Py) => order(a, b, '<') < 0
export const le = (a: Py, b: Py) => order(a, b, '<=') <= 0
export const gt = (a: Py, b: Py) => order(a, b, '>') > 0
export const ge = (a: Py, b: Py) => order(a, b, '>=') >= 0

/** a == b */
export function eq(a: Py, b: Py): boolean {
  if (a === b) return true
  if (isNum(a) && isNum(b)) {
    if (typeof a === 'bigint' || typeof b === 'bigint') return !isFloat(a) && !isFloat(b) ? big(a) === big(b) : num(a) === num(b)
    return num(a) === num(b)
  }
  if (typeof a === 'string' || typeof b === 'string') return false
  if (Array.isArray(a) && Array.isArray(b)) return a.length === b.length && a.every((x, i) => eq(x, b[i]))
  if (a instanceof PyDict && b instanceof PyDict) {
    if (a.size !== b.size) return false
    for (const [k, v] of a.m) if (!b.m.has(k) || !eq(v, b.m.get(k) as Py)) return false
    return true
  }
  return false
}

/** bool(x) */
export function truthy(x: Py): boolean {
  if (x === null || x === undefined) return false
  if (typeof x === 'boolean') return x
  if (typeof x === 'number') return x !== 0
  if (typeof x === 'bigint') return x !== 0n
  if (x instanceof PyFloat) return x.v !== 0
  if (typeof x === 'string' || Array.isArray(x)) return x.length > 0
  if (x instanceof PyDict) return x.size > 0
  return true
}

/** a or b (Python's: the first truthy one, else the last) */
export const or = (...xs: Py[]): Py => {
  for (const x of xs.slice(0, -1)) if (truthy(x)) return x
  return xs[xs.length - 1]
}

// --- Rounding (exact, as CPython's: on the float's exact binary value, ties to even) --------------------------------

/** x = m * 2^e exactly (x finite, not 0). */
function parts(x: number): { m: bigint; e: number } {
  const dv = new DataView(new ArrayBuffer(8))
  dv.setFloat64(0, Math.abs(x))
  const hi = dv.getUint32(0)
  const lo = dv.getUint32(4)
  const exp = (hi >>> 20) & 0x7ff
  let m = (BigInt(hi & 0xfffff) << 32n) | BigInt(lo)
  if (exp === 0) return { m, e: -1074 }
  m |= 1n << 52n
  return { m, e: exp - 1075 }
}

/** round_half_even(|x| * 10^nd) as an exact integer (x finite). */
function scaledHalfEven(x: number, nd: number): bigint {
  if (x === 0) return 0n
  const { m, e } = parts(x)
  let n = m * 10n ** BigInt(nd)
  if (e >= 0) return n << BigInt(e)
  const d = 1n << BigInt(-e)
  const q = n / d
  const r2 = (n % d) * 2n
  n = q
  if (r2 > d || (r2 === d && q % 2n === 1n)) n += 1n
  return n
}

/** The decimal string of round_half_even(x, nd) (nd >= 0), signed: what '%.{nd}f' % x prints. */
export function fixed(x: number, nd: number): string {
  if (Number.isNaN(x)) return 'nan'
  if (!Number.isFinite(x)) return x > 0 ? 'inf' : '-inf'
  const q = scaledHalfEven(x, nd).toString().padStart(nd + 1, '0')
  const s = nd ? `${q.slice(0, q.length - nd)}.${q.slice(q.length - nd)}` : q
  return (x < 0 || Object.is(x, -0) ? '-' : '') + s
}

/** round(x) and round(x, nd), with Python's result types. */
export function round(x: Py, nd?: number): Py {
  if (typeof x === 'boolean') return x ? 1 : 0
  if (isInt(x)) return x
  if (!isFloat(x)) throw typeErr(`type ${typeName(x)} doesn't define __round__ method`)
  const v = num(x)
  if (nd === undefined) {
    if (Number.isNaN(v)) throw valueErr('cannot convert float NaN to integer')
    if (!Number.isFinite(v)) throw pyErr('OverflowError', 'cannot convert float infinity to integer')
    const q = scaledHalfEven(v, 0)
    return intOf(v < 0 ? -q : q)
  }
  if (!Number.isFinite(v) || v === 0) return F(v)
  return F(Number(fixed(v, nd)))
}

/** math.ceil(x): an int. */
export function ceil(x: Py): Py {
  if (isInt(x)) return typeof x === 'boolean' ? (x ? 1 : 0) : x
  if (!isFloat(x)) throw typeErr(`must be real number, not ${typeName(x)}`)
  const v = num(x)
  if (Number.isNaN(v)) throw valueErr('cannot convert float NaN to integer')
  if (!Number.isFinite(v)) throw pyErr('OverflowError', 'cannot convert float infinity to integer')
  const c = Math.ceil(v)
  return Number.isSafeInteger(c) ? c : intOf(BigInt(c))
}

/** max(a, b, …): the first of the greatest (Python's: a later one only when it is greater). */
export function max(...xs: Py[]): Py {
  let best = xs[0]
  for (const x of xs.slice(1)) if (gt(x, best)) best = x
  return best
}

/** min(a, b, …): the first of the least. */
export function min(...xs: Py[]): Py {
  let best = xs[0]
  for (const x of xs.slice(1)) if (lt(x, best)) best = x
  return best
}

// --- Conversions ---------------------------------------------------------------------------------------------------

/** Python's whitespace (str.isspace, and \s in a str pattern). */
export const WS = '\\t\\n\\v\\f\\r\\x1c-\\x20\\x85\\xa0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000'
const STRIP = new RegExp(`^[${WS}]+|[${WS}]+$`, 'gu')

/** The value of a decimal digit (Unicode category Nd: runs of ten, 0 to 9). */
function digitValue(c: string): number {
  const cp = c.codePointAt(0) as number
  if (cp >= 0x30 && cp <= 0x39) return cp - 0x30
  let start = cp
  while (start > 0 && /\p{Nd}/u.test(String.fromCodePoint(start - 1))) start--
  return (cp - start) % 10
}
/** Unicode decimal digits as ASCII ones. */
export const asciiDigits = (s: string) => s.replace(/\p{Nd}/gu, c => String(digitValue(c)))

/** int(x) */
export function int(x: Py): Py {
  if (typeof x === 'boolean') return x ? 1 : 0
  if (isInt(x)) return x
  if (isFloat(x)) {
    const v = num(x)
    if (Number.isNaN(v)) throw valueErr('cannot convert float NaN to integer')
    if (!Number.isFinite(v)) throw pyErr('OverflowError', 'cannot convert float infinity to integer')
    const t = Math.trunc(v)
    return Number.isSafeInteger(t) ? t : intOf(BigInt(t))
  }
  if (typeof x === 'string') {
    const s = x.replace(STRIP, '')
    const m = /^([+-]?)(\p{Nd}(?:_?\p{Nd})*)$/u.exec(s)
    if (!m) throw valueErr(`invalid literal for int() with base 10: ${repr(x)}`)
    const v = BigInt(asciiDigits(m[2].replace(/_/g, '')))
    return intOf(m[1] === '-' ? -v : v)
  }
  throw typeErr(`int() argument must be a string, a bytes-like object or a real number, not '${typeName(x)}'`)
}

/** float(x) */
export function float(x: Py): PyFloat {
  if (isNum(x)) return F(num(x))
  if (typeof x === 'string') {
    const s = x.replace(STRIP, '')
    const d = '\\p{Nd}(?:_?\\p{Nd})*'
    const m = new RegExp(`^([+-]?)(?:((?:${d})?\\.${d}|${d}\\.?)((?:[eE][+-]?${d})?)|(inf|infinity)|(nan))$`, 'iu').exec(s)
    if (!m) throw valueErr(`could not convert string to float: ${repr(x)}`)
    const sign = m[1] === '-' ? -1 : 1
    if (m[4]) return F(sign * Infinity)
    if (m[5]) return F(NaN)
    return F(sign * Number(asciiDigits((m[2] + m[3]).replace(/_/g, ''))))
  }
  throw typeErr(`float() argument must be a string or a real number, not '${typeName(x)}'`)
}

/** repr(float): the shortest digits that read back, as CPython writes them ('2.0', '1e-05', '1e+16'). */
export function floatRepr(v: number): string {
  if (Number.isNaN(v)) return 'nan'
  if (!Number.isFinite(v)) return v > 0 ? 'inf' : '-inf'
  if (v === 0) return Object.is(v, -0) ? '-0.0' : '0.0'
  const [mant, ex] = Math.abs(v).toExponential().split('e')
  const digits = mant.replace('.', '')
  const decpt = Number(ex) + 1
  const sign = v < 0 ? '-' : ''
  if (decpt <= -4 || decpt > 16) {
    const e = decpt - 1
    const m = digits.length > 1 ? `${digits[0]}.${digits.slice(1)}` : digits
    return `${sign}${m}e${e < 0 ? '-' : '+'}${String(Math.abs(e)).padStart(2, '0')}`
  }
  if (decpt <= 0) return `${sign}0.${'0'.repeat(-decpt)}${digits}`
  if (decpt >= digits.length) return `${sign}${digits}${'0'.repeat(decpt - digits.length)}.0`
  return `${sign}${digits.slice(0, decpt)}.${digits.slice(decpt)}`
}

const NONPRINTABLE = /[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Cn}\p{Zl}\p{Zp}\p{Zs}]/u

/** repr(str) */
export function strRepr(s: string): string {
  const q = s.includes("'") && !s.includes('"') ? '"' : "'"
  let out = q
  for (const c of s) {
    if (c === q || c === '\\') out += '\\' + c
    else if (c === '\t') out += '\\t'
    else if (c === '\n') out += '\\n'
    else if (c === '\r') out += '\\r'
    else if (c !== ' ' && NONPRINTABLE.test(c)) {
      const cp = c.codePointAt(0) as number
      out += cp < 0x100 ? `\\x${hex(cp, 2)}` : cp < 0x10000 ? `\\u${hex(cp, 4)}` : `\\U${hex(cp, 8)}`
    } else out += c
  }
  return out + q
}
const hex = (n: number, w: number) => n.toString(16).padStart(w, '0')

/** repr(x) */
export function repr(x: Py): string {
  if (typeof x === 'string') return strRepr(x)
  if (Array.isArray(x)) return `[${x.map(repr).join(', ')}]`
  if (x instanceof PyDict) return `{${x.items().map(([k, v]) => `${repr(k)}: ${repr(v)}`).join(', ')}}`
  return str(x)
}

/** str(x): what an f-string prints. */
export function str(x: Py): string {
  if (x === null || x === undefined) return 'None'
  if (typeof x === 'boolean') return x ? 'True' : 'False'
  if (typeof x === 'number') return Number.isInteger(x) ? String(x) : floatRepr(x)
  if (typeof x === 'bigint') return x.toString()
  if (x instanceof PyFloat) return floatRepr(x.v)
  if (typeof x === 'string') return x
  return repr(x)
}

// --- Containers ----------------------------------------------------------------------------------------------------

/** len(x) */
export function len(x: Py): number {
  if (typeof x === 'string') return cpLen(x)
  if (Array.isArray(x)) return x.length
  if (x instanceof PyDict) return x.size
  throw typeErr(`object of type '${typeName(x)}' has no len()`)
}

/** x in c */
export function contains(c: Py, x: Py): boolean {
  if (c instanceof PyDict) return c.hasKey(x)
  if (Array.isArray(c)) return c.some(e => e === x || eq(e, x))
  if (typeof c === 'string') {
    if (typeof x !== 'string') throw typeErr(`'in <string>' requires string as left operand, not ${typeName(x)}`)
    return c.includes(x)
  }
  throw typeErr(`argument of type '${typeName(c)}' is not a container or iterable`)
}

/** for x in c */
export function iter(c: Py): Py[] {
  if (Array.isArray(c)) return c
  if (c instanceof PyDict) return c.keys()
  if (typeof c === 'string') return [...c]
  throw typeErr(`'${typeName(c)}' object is not iterable`)
}

/** o[k] */
export function getItem(o: Py, k: Py): Py {
  if (o instanceof PyDict) {
    if (o.hasKey(k)) return o.getKey(k)
    throw new PyError('KeyError', repr(k))
  }
  if (Array.isArray(o)) {
    if (!isInt(k)) throw typeErr(`list indices must be integers or slices, not ${typeName(k)}`)
    const i = num(k) < 0 ? o.length + num(k) : num(k)
    if (i < 0 || i >= o.length) throw pyErr('IndexError', 'list index out of range')
    return o[i]
  }
  if (typeof o === 'string') {
    if (!isInt(k)) throw typeErr(`string indices must be integers, not '${typeName(k)}'`)
    const cs = [...o]
    const i = num(k) < 0 ? cs.length + num(k) : num(k)
    if (i < 0 || i >= cs.length) throw pyErr('IndexError', 'string index out of range')
    return cs[i]
  }
  throw typeErr(`'${typeName(o)}' object is not subscriptable`)
}

/** o[k] = v */
export function setItem(o: Py, k: Py, v: Py): void {
  if (o instanceof PyDict) return o.setKey(k, v)
  if (Array.isArray(o)) {
    if (!isInt(k)) throw typeErr(`list indices must be integers or slices, not ${typeName(k)}`)
    const i = num(k) < 0 ? o.length + num(k) : num(k)
    if (i < 0 || i >= o.length) throw pyErr('IndexError', 'list assignment index out of range')
    o[i] = v
    return
  }
  throw typeErr(`'${typeName(o)}' object does not support item assignment`)
}

/** del o[k] */
export function delItem(o: Py, k: Py): void {
  if (o instanceof PyDict) return o.deleteKey(k)
  throw typeErr(`'${typeName(o)}' object doesn't support item deletion`)
}

const noAttr = (o: Py, a: string) => new PyError('AttributeError', `'${typeName(o)}' object has no attribute '${a}'`)

/** A dict, for a method call on it (o.get(…), o.setdefault(…) …): AttributeError for anything else. */
export function dict(o: Py, method: string): PyDict {
  if (o instanceof PyDict) return o
  throw noAttr(o, method)
}

/** o.get(k, d) */
export const get = (o: Py, k: string, d: Py = null): Py => dict(o, 'get').get(k, d)
/** o.setdefault(k, d) */
export const setdefault = (o: Py, k: string, d: Py): Py => dict(o, 'setdefault').setdefault(k, d)
/** o.items() */
export const items = (o: Py): [string, Py][] => dict(o, 'items').items()
/** o.values() */
export const values = (o: Py): Py[] => dict(o, 'values').values()
/** list(o.keys()) */
export const keysOf = (o: Py): string[] => dict(o, 'keys').keys()
/** o.append(v) */
export function append(o: Py, v: Py): void {
  if (!Array.isArray(o)) throw noAttr(o, 'append')
  o.push(v)
}

/** {**a, **b, …}: a TypeError for what isn't a mapping. */
export function merge(...ds: Py[]): PyDict {
  const out = new PyDict()
  for (const d of ds) {
    if (!(d instanceof PyDict)) throw typeErr(`'${typeName(d)}' object is not a mapping`)
    out.update(d)
  }
  return out
}

/** x[start:end] of a list or str (end undefined: to the end), Python's clamping and negative indices. */
export function slice<T extends Py[] | string>(x: T, start?: number, end?: number): T {
  const arr: Py[] = typeof x === 'string' ? [...x] : (x as Py[])
  const n = arr.length
  const at = (i: number | undefined, d: number) => (i === undefined ? d : i < 0 ? Math.max(0, n + i) : Math.min(n, i))
  const s = arr.slice(at(start, 0), Math.max(at(start, 0), at(end, n)))
  return (typeof x === 'string' ? s.join('') : s) as T
}

/** copy.deepcopy */
export function deepcopy<T extends Py>(x: T): T {
  if (Array.isArray(x)) return x.map(deepcopy) as T
  if (x instanceof PyDict) {
    const out = new PyDict()
    for (const [k, v] of x.items()) out.setKey(k, deepcopy(v))
    return out as T
  }
  return x
}

/** dict.fromkeys(xs): xs without repeats, in their first order. */
export function unique<T extends Py>(xs: Iterable<T>): T[] {
  const out: T[] = []
  for (const x of xs) if (!out.some(o => eq(o, x))) out.push(x)
  return out
}

/** sorted(xs) of strs (code point order). */
export const sortedStrs = (xs: Iterable<string>): string[] => [...xs].sort(strCmp)

// --- Strings -------------------------------------------------------------------------------------------------------

export const cpLen = (s: string): number => {
  let n = 0
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i)
    if (c >= 0xd800 && c <= 0xdbff && i + 1 < s.length) {
      const d = s.charCodeAt(i + 1)
      if (d >= 0xdc00 && d <= 0xdfff) i++
    }
    n++
  }
  return n
}

/** A str, for a method call on it: AttributeError for anything else. */
export function S(o: Py, method: string): string {
  if (typeof o === 'string') return o
  throw noAttr(o, method)
}

/** s.strip() */
export const strip = (s: Py): string => S(s, 'strip').replace(STRIP, '')

/** s.strip(chars) */
export function stripChars(s: string, chars: string): string {
  const cs = [...s]
  let a = 0
  let b = cs.length
  while (a < b && chars.includes(cs[a])) a++
  while (b > a && chars.includes(cs[b - 1])) b--
  return cs.slice(a, b).join('')
}

export const lower = (s: Py): string => S(s, 'lower').toLowerCase()
export const upper = (s: Py): string => S(s, 'upper').toUpperCase()

/** s.count(sub) */
export const count = (s: Py, subs: string): number => S(s, 'count').split(subs).length - 1

/** s.endswith(x) */
export const endswith = (s: Py, x: string): boolean => S(s, 'endswith').endsWith(x)

const COMBINING_RANGES =
  '300-34e,350-36f,483-487,591-5bd,5bf,5c1-5c2,5c4-5c5,5c7,610-61a,64b-65f,670,6d6-6dc,6df-6e4,6e7-6e8,6ea-6ed,71' +
  '1,730-74a,7eb-7f3,7fd,816-819,81b-823,825-827,829-82d,859-85b,897-89f,8ca-8e1,8e3-8ff,93c,94d,951-954,9bc,9cd,' +
  '9fe,a3c,a4d,abc,acd,b3c,b4d,bcd,c3c,c4d,c55-c56,cbc,ccd,d3b-d3c,d4d,dca,e38-e3a,e48-e4b,eb8-eba,ec8-ecb,f18-f1' +
  '9,f35,f37,f39,f71-f72,f74,f7a-f7d,f80,f82-f84,f86-f87,fc6,1037,1039-103a,108d,135d-135f,1714-1715,1734,17d2,17' +
  'dd,18a9,1939-193b,1a17-1a18,1a60,1a75-1a7c,1a7f,1ab0-1abd,1abf-1ace,1b34,1b44,1b6b-1b73,1baa-1bab,1be6,1bf2-1b' +
  'f3,1c37,1cd0-1cd2,1cd4-1ce0,1ce2-1ce8,1ced,1cf4,1cf8-1cf9,1dc0-1dff,20d0-20dc,20e1,20e5-20f0,2cef-2cf1,2d7f,2d' +
  'e0-2dff,302a-302f,3099-309a,a66f,a674-a67d,a69e-a69f,a6f0-a6f1,a806,a82c,a8c4,a8e0-a8f1,a92b-a92d,a953,a9b3,a9' +
  'c0,aab0,aab2-aab4,aab7-aab8,aabe-aabf,aac1,aaf6,abed,fb1e,fe20-fe2f,101fd,102e0,10376-1037a,10a0d,10a0f,10a38-' +
  '10a3a,10a3f,10ae5-10ae6,10d24-10d27,10d69-10d6d,10eab-10eac,10efd-10eff,10f46-10f50,10f82-10f85,11046,11070,11' +
  '07f,110b9-110ba,11100-11102,11133-11134,11173,111c0,111ca,11235-11236,112e9-112ea,1133b-1133c,1134d,11366-1136' +
  'c,11370-11374,113ce-113d0,11442,11446,1145e,114c2-114c3,115bf-115c0,1163f,116b6-116b7,1172b,11839-1183a,1193d-' +
  '1193e,11943,119e0,11a34,11a47,11a99,11c3f,11d42,11d44-11d45,11d97,11f41-11f42,1612f,16af0-16af4,16b30-16b36,16' +
  'ff0-16ff1,1bc9e,1d165-1d169,1d16d-1d172,1d17b-1d182,1d185-1d18b,1d1aa-1d1ad,1d242-1d244,1e000-1e006,1e008-1e01' +
  '8,1e01b-1e021,1e023-1e024,1e026-1e02a,1e08f,1e130-1e136,1e2ae,1e2ec-1e2ef,1e4ec-1e4ef,1e5ee-1e5ef,1e8d0-1e8d6,' +
  '1e944-1e94a'
let combining: number[][] | undefined

/** unicodedata.combining(c) != 0: a canonical combining class (Python's Unicode 16.0 tables). */
export function isCombining(c: string): boolean {
  combining ??= COMBINING_RANGES.split(',').map(r => r.split('-').map(h => parseInt(h, 16))).map(([a, b]) => [a, b ?? a])
  const cp = c.codePointAt(0) as number
  let lo = 0
  let hi = combining.length - 1
  while (lo <= hi) {
    const mid = (lo + hi) >> 1
    const [a, b] = combining[mid]
    if (cp < a) hi = mid - 1
    else if (cp > b) lo = mid + 1
    else return true
  }
  return false
}

// --- Dates (datetime's proleptic Gregorian calendar) ---------------------------------------------------------------

export type Ymd = { y: number; m: number; d: number }
/** A naive local datetime, as datetime.now() gives it. */
export type DateTime = Ymd & { H: number; M: number; S: number; us: number }

const DIM = [0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
const leap = (y: number) => y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0)
const daysIn = (y: number, m: number) => (m === 2 && leap(y) ? 29 : DIM[m])

/** datetime(y, m, d)'s checks. */
export function ymd(y: number, m: number, d: number): Ymd {
  if (y < 1 || y > 9999) throw valueErr(`year must be in 1..9999, not ${y}`)
  if (m < 1 || m > 12) throw valueErr(`month must be in 1..12, not ${m}`)
  if (d < 1 || d > daysIn(y, m)) throw valueErr(`day ${d} must be in range 1..${daysIn(y, m)} for month ${m} in year ${y}`)
  return { y, m, d }
}

/** date.toordinal() */
export function ordinal({ y, m, d }: Ymd): number {
  const p = y - 1
  let days = p * 365 + Math.floor(p / 4) - Math.floor(p / 100) + Math.floor(p / 400)
  for (let i = 1; i < m; i++) days += daysIn(y, i)
  return days + d
}

/** date.fromordinal(n) */
export function fromOrdinal(n: number): Ymd {
  let n0 = n - 1
  const n400 = Math.floor(n0 / 146097)
  n0 -= n400 * 146097
  const n100 = Math.floor(n0 / 36524)
  n0 -= n100 * 36524
  const n4 = Math.floor(n0 / 1461)
  n0 -= n4 * 1461
  const n1 = Math.floor(n0 / 365)
  n0 -= n1 * 365
  let y = n400 * 400 + n100 * 100 + n4 * 4 + n1 + 1
  if (n1 === 4 || n100 === 4) return { y: y - 1, m: 12, d: 31 }
  let m = 1
  while (n0 >= daysIn(y, m)) {
    n0 -= daysIn(y, m)
    m++
  }
  return { y, m, d: n0 + 1 }
}

/** datetime.strptime(s, '%Y-%m-%d') (a date at midnight): its regex, Unicode digits and errors. */
export function strptime(s: Py): Ymd {
  if (typeof s !== 'string') throw typeErr(`strptime() argument 1 must be str, not ${typeName(s)}`)
  const m = /^(\p{Nd}\p{Nd}\p{Nd}\p{Nd})-(1[0-2]|0[1-9]|[1-9])-(3[0-1]|[1-2]\p{Nd}|0[1-9]|[1-9]| [1-9])/u.exec(s)
  if (!m) throw valueErr(`time data ${strRepr(s)} does not match format '%Y-%m-%d'`)
  if (m[0].length !== s.length) throw valueErr(`unconverted data remains: ${s.slice(m[0].length)}`)
  return ymd(Number(asciiDigits(m[1])), Number(asciiDigits(m[2])), Number(asciiDigits(m[3].trim())))
}

const pad = (n: number, w: number) => String(n).padStart(w, '0')

/** strftime('%Y-%m-%d') */
export const fmtDate = (d: Ymd) => `${pad(d.y, 4)}-${pad(d.m, 2)}-${pad(d.d, 2)}`
/** strftime('%Y%m%d-%H%M%S') */
export const fmtStamp = (t: DateTime) => `${pad(t.y, 4)}${pad(t.m, 2)}${pad(t.d, 2)}-${pad(t.H, 2)}${pad(t.M, 2)}${pad(t.S, 2)}`

/** date + timedelta(days=n): n an int or a float (its fraction a time of day), datetime's errors. */
export function plusDays(d: Ymd, n: Py): Ymd {
  if (!isNum(n)) throw typeErr(`unsupported type for timedelta days component: ${typeName(n)}`)
  let days = num(n)
  if (isFloat(n)) days = Math.floor(Math.round(days * 86400e6) / 86400e6)
  if (Math.abs(days) > 999999999) throw pyErr('OverflowError', `days=${days}; must have magnitude <= 999999999`)
  const o = ordinal(d) + days
  if (o < 1 || o > 3652059) throw pyErr('OverflowError', 'date value out of range')
  return fromOrdinal(o)
}

/** date.weekday(): Monday 0. */
export const weekday = (d: Ymd) => (ordinal(d) + 6) % 7
