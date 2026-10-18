// Node's Buffer for the phone's bridge (prelude.ts makes it the global): a Uint8Array with what the bridge uses of it
// (from, alloc, concat, byteLength, isBuffer; toString and from in utf8, hex, base64, base64url, latin1; equals,
// compare, readUInt32BE, slice sharing its memory as node's does). UTF-8 and base64 are the host's (fast, in Kotlin).
import { fromHost, host, toHost } from './host'

type Encoding = 'utf8' | 'utf-8' | 'hex' | 'base64' | 'base64url' | 'latin1' | 'binary' | 'ascii' | 'ucs2' | 'utf16le'

const norm = (e?: string): Encoding => (e ? (e.toLowerCase() as Encoding) : 'utf8')

export function utf8Bytes(s: string): Uint8Array {
  if (!s.length) return new Uint8Array(0)
  // ASCII only: no need to cross to the host
  if (s.length < 64 && /^[\x00-\x7f]*$/.test(s)) {
    const out = new Uint8Array(s.length)
    for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i)
    return out
  }
  return fromHost(host().utf8Encode(s))
}

export function utf8Text(b: Uint8Array): string {
  if (!b.length) return ''
  if (b.length < 64) {
    let ascii = true
    for (let i = 0; i < b.length; i++) if (b[i] > 0x7f) { ascii = false; break }
    if (ascii) return String.fromCharCode(...b)
  }
  return host().utf8Decode(toHost(b))
}

function fromString(s: string, enc: Encoding): Uint8Array {
  switch (enc) {
    case 'utf8':
    case 'utf-8':
      return utf8Bytes(s)
    case 'hex': {
      const n = s.length >> 1
      const out = new Uint8Array(n)
      for (let i = 0; i < n; i++) {
        const v = parseInt(s.substr(i * 2, 2), 16)
        if (Number.isNaN(v)) return out.subarray(0, i)
        out[i] = v
      }
      return out
    }
    case 'base64':
    case 'base64url':
      return fromHost(host().base64Decode(s))
    case 'latin1':
    case 'binary':
    case 'ascii': {
      const out = new Uint8Array(s.length)
      for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i) & 0xff
      return out
    }
    case 'ucs2':
    case 'utf16le': {
      const out = new Uint8Array(s.length * 2)
      for (let i = 0; i < s.length; i++) {
        const c = s.charCodeAt(i)
        out[i * 2] = c & 0xff
        out[i * 2 + 1] = c >> 8
      }
      return out
    }
    default:
      throw new TypeError(`Unknown encoding: ${enc}`)
  }
}

const HEX = Array.from({ length: 256 }, (_, i) => i.toString(16).padStart(2, '0'))

function toText(b: Uint8Array, enc: Encoding): string {
  switch (enc) {
    case 'utf8':
    case 'utf-8':
      return utf8Text(b)
    case 'hex': {
      let s = ''
      for (let i = 0; i < b.length; i++) s += HEX[b[i]]
      return s
    }
    case 'base64':
      return b.length ? host().base64Encode(toHost(b), false) : ''
    case 'base64url':
      return b.length ? host().base64Encode(toHost(b), true) : ''
    case 'latin1':
    case 'binary': {
      let s = ''
      for (let i = 0; i < b.length; i += 8192) s += String.fromCharCode(...b.subarray(i, i + 8192))
      return s
    }
    case 'ascii': {
      let s = ''
      for (let i = 0; i < b.length; i++) s += String.fromCharCode(b[i] & 0x7f)
      return s
    }
    case 'ucs2':
    case 'utf16le': {
      let s = ''
      for (let i = 0; i + 1 < b.length; i += 2) s += String.fromCharCode(b[i] | (b[i + 1] << 8))
      return s
    }
    default:
      throw new TypeError(`Unknown encoding: ${enc}`)
  }
}

// @ts-ignore: node's Buffer.from (a string and its encoding, an ArrayBuffer and a range) isn't Uint8Array.from's signature
export class Buffer extends Uint8Array {
  static from(value: string | ArrayBuffer | ArrayLike<number> | Uint8Array | Iterable<number>, encOrOffset?: string | number, length?: number): Buffer {
    if (typeof value === 'string') return Buffer.view(fromString(value, norm(encOrOffset as string | undefined)))
    if (value instanceof ArrayBuffer) {
      const off = (encOrOffset as number | undefined) ?? 0
      return new Buffer(value, off, length ?? value.byteLength - off)
    }
    if (ArrayBuffer.isView(value)) {
      const v = value as Uint8Array
      const copy = new Buffer(v.byteLength)
      copy.set(new Uint8Array(v.buffer, v.byteOffset, v.byteLength))
      return copy
    }
    if (value && typeof (value as { type?: string }).type === 'string' && Array.isArray((value as { data?: number[] }).data)) return Buffer.from((value as unknown as { data: number[] }).data)
    const arr = Array.from(value as ArrayLike<number>)
    const out = new Buffer(arr.length)
    for (let i = 0; i < arr.length; i++) out[i] = arr[i] & 0xff
    return out
  }

  /** A Buffer over [u]'s memory. */
  static view(u: Uint8Array): Buffer {
    return u instanceof Buffer ? u : new Buffer(u.buffer as ArrayBuffer, u.byteOffset, u.byteLength)
  }

  static alloc(n: number, fill?: number | string): Buffer {
    const b = new Buffer(n)
    if (typeof fill === 'number') b.fill(fill)
    else if (typeof fill === 'string' && fill.length) {
      const f = utf8Bytes(fill)
      for (let i = 0; i < n; i++) b[i] = f[i % f.length]
    }
    return b
  }

  static allocUnsafe(n: number): Buffer {
    return new Buffer(n)
  }

  static isBuffer(x: unknown): x is Buffer {
    return x instanceof Buffer
  }

  static isEncoding(e: string): boolean {
    return ['utf8', 'utf-8', 'hex', 'base64', 'base64url', 'latin1', 'binary', 'ascii', 'ucs2', 'utf16le'].includes(e.toLowerCase())
  }

  static byteLength(x: string | ArrayBuffer | ArrayBufferView, enc?: string): number {
    if (typeof x !== 'string') return x.byteLength
    const e = norm(enc)
    if (e === 'utf8' || e === 'utf-8') {
      let n = 0
      for (let i = 0; i < x.length; i++) {
        const c = x.charCodeAt(i)
        if (c < 0x80) n += 1
        else if (c < 0x800) n += 2
        else if (c >= 0xd800 && c <= 0xdbff && i + 1 < x.length && (x.charCodeAt(i + 1) & 0xfc00) === 0xdc00) {
          n += 4
          i++
        } else n += 3
      }
      return n
    }
    return fromString(x, e).length
  }

  static concat(list: readonly Uint8Array[], total?: number): Buffer {
    const n = total ?? list.reduce((s, b) => s + b.length, 0)
    const out = new Buffer(n)
    let at = 0
    for (const b of list) {
      if (at >= n) break
      out.set(b.subarray(0, n - at), at)
      at += b.length
    }
    return out
  }

  static compare(a: Uint8Array, b: Uint8Array): number {
    const n = Math.min(a.length, b.length)
    for (let i = 0; i < n; i++) if (a[i] !== b[i]) return a[i] < b[i] ? -1 : 1
    return a.length === b.length ? 0 : a.length < b.length ? -1 : 1
  }

  override toString(enc?: string, start = 0, end = this.length): string {
    const part = start === 0 && end >= this.length ? this : this.subarray(start, end)
    return toText(part, norm(enc))
  }

  toJSON() {
    return { type: 'Buffer', data: Array.from(this) }
  }

  equals(other: Uint8Array): boolean {
    return Buffer.compare(this, other) === 0
  }

  compare(other: Uint8Array): number {
    return Buffer.compare(this, other)
  }

  /** As node's: a view over the same memory (Uint8Array's slice copies). */
  override slice(start?: number, end?: number): Buffer {
    return this.subarray(start, end) as Buffer
  }

  override subarray(start?: number, end?: number): Buffer {
    const u = super.subarray(start, end)
    return new Buffer(u.buffer as ArrayBuffer, u.byteOffset, u.byteLength)
  }

  copy(target: Uint8Array, targetStart = 0, start = 0, end = this.length): number {
    const part = this.subarray(start, Math.min(end, start + (target.length - targetStart)))
    target.set(part, targetStart)
    return part.length
  }

  write(s: string, offset = 0, enc?: string): number {
    const b = fromString(s, norm(enc)).subarray(0, this.length - offset)
    this.set(b, offset)
    return b.length
  }

  readUInt8(o = 0) {
    return this[o]
  }

  readUInt16BE(o = 0) {
    return (this[o] << 8) | this[o + 1]
  }

  readUInt32BE(o = 0) {
    return ((this[o] << 24) >>> 0) + ((this[o + 1] << 16) | (this[o + 2] << 8) | this[o + 3])
  }

  readUInt32LE(o = 0) {
    return ((this[o + 3] << 24) >>> 0) + ((this[o + 2] << 16) | (this[o + 1] << 8) | this[o])
  }

  writeUInt32BE(v: number, o = 0) {
    this[o] = v >>> 24
    this[o + 1] = v >>> 16
    this[o + 2] = v >>> 8
    this[o + 3] = v
    return o + 4
  }

  indexOf(v: number | string | Uint8Array, from = 0): number {
    if (typeof v === 'number') return super.indexOf(v, from)
    const needle = typeof v === 'string' ? utf8Bytes(v) : v
    outer: for (let i = from; i <= this.length - needle.length; i++) {
      for (let j = 0; j < needle.length; j++) if (this[i + j] !== needle[j]) continue outer
      return i
    }
    return -1
  }

  includes(v: number | string | Uint8Array, from = 0): boolean {
    return this.indexOf(v, from) >= 0
  }
}

export default { Buffer }
