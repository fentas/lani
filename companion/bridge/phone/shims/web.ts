// The web platform's classes the bridge uses, for QuickJS (prelude.ts makes them globals): Headers, Request, Response
// (the router's and the features' answers, fetch's), a ReadableStream enough for the event stream (/events) and the
// bodies, AbortController and AbortSignal (a request's end, fetch's timeouts), TextEncoder and TextDecoder, Event,
// EventTarget, DOMException. What the bridge doesn't use (FormData, Blob's whole API, streams' piping) isn't here: a
// multipart body (the family page's upload) is refused as it would be unreadable.
import { Buffer, utf8Bytes, utf8Text } from './buffer'
import { copyOf } from './host'
import { URLSearchParams } from './url'

export class DOMException extends Error {
  constructor(message = '', readonly name0: string = 'Error') {
    super(message)
    this.name = name0
  }
  get code() {
    return this.name === 'AbortError' ? 20 : this.name === 'TimeoutError' ? 23 : 0
  }
}

type Listener = { fn: (e: Event) => void; once: boolean }

export class Event {
  readonly timeStamp = Date.now()
  defaultPrevented = false
  target: EventTarget | null = null
  constructor(readonly type: string) {}
  preventDefault() {
    this.defaultPrevented = true
  }
  stopPropagation() {}
  stopImmediatePropagation() {}
}

export class EventTarget {
  private ls = new Map<string, Listener[]>()
  addEventListener(type: string, fn: ((e: Event) => void) | null, o?: boolean | { once?: boolean }) {
    if (!fn) return
    const list = this.ls.get(type) ?? []
    if (!list.some(l => l.fn === fn)) list.push({ fn, once: typeof o === 'object' && !!o?.once })
    this.ls.set(type, list)
  }
  removeEventListener(type: string, fn: (e: Event) => void) {
    this.ls.set(type, (this.ls.get(type) ?? []).filter(l => l.fn !== fn))
  }
  dispatchEvent(e: Event): boolean {
    e.target = this
    const handler = (this as unknown as Record<string, unknown>)[`on${e.type}`]
    if (typeof handler === 'function') handler.call(this, e)
    for (const l of [...(this.ls.get(e.type) ?? [])]) {
      if (l.once) this.removeEventListener(e.type, l.fn)
      try {
        l.fn.call(this, e)
      } catch (err) {
        console.error('listener failed:', err)
      }
    }
    return !e.defaultPrevented
  }
}

export class AbortSignal extends EventTarget {
  aborted = false
  reason: unknown = undefined
  onabort: ((e: Event) => void) | null = null
  /** When it aborts by itself (AbortSignal.timeout): fetch hands it to the host as the call's timeout. */
  _timeoutMs?: number

  static abort(reason?: unknown): AbortSignal {
    const s = new AbortSignal()
    s._abort(reason ?? new DOMException('This operation was aborted', 'AbortError'))
    return s
  }

  static timeout(ms: number): AbortSignal {
    const s = new AbortSignal()
    s._timeoutMs = ms
    const t = setTimeout(() => s._abort(new DOMException('The operation timed out.', 'TimeoutError')), ms) as unknown as { unref?: () => void }
    t.unref?.()
    return s
  }

  static any(signals: AbortSignal[]): AbortSignal {
    const s = new AbortSignal()
    for (const x of signals) {
      if (x.aborted) {
        s._abort(x.reason)
        break
      }
      x.addEventListener('abort', () => s._abort(x.reason), { once: true })
    }
    return s
  }

  throwIfAborted() {
    if (this.aborted) throw this.reason
  }

  _abort(reason: unknown) {
    if (this.aborted) return
    this.aborted = true
    this.reason = reason
    this.dispatchEvent(new Event('abort'))
  }
}

export class AbortController {
  readonly signal = new AbortSignal()
  abort(reason?: unknown) {
    this.signal._abort(reason ?? new DOMException('This operation was aborted', 'AbortError'))
  }
}

export class TextEncoder {
  readonly encoding = 'utf-8'
  encode(s = ''): Uint8Array {
    const b = utf8Bytes(String(s))
    return new Uint8Array(b.buffer, b.byteOffset, b.byteLength)
  }
  encodeInto(s: string, dest: Uint8Array) {
    const b = utf8Bytes(s)
    const n = Math.min(b.length, dest.length)
    dest.set(b.subarray(0, n))
    return { read: s.length, written: n }
  }
}

export class TextDecoder {
  readonly encoding: string
  readonly fatal: boolean
  constructor(label = 'utf-8', o: { fatal?: boolean } = {}) {
    const l = label.toLowerCase()
    if (l !== 'utf-8' && l !== 'utf8') throw new RangeError(`The "${label}" encoding is not supported`)
    this.encoding = 'utf-8'
    this.fatal = !!o.fatal
  }
  decode(input?: ArrayBuffer | ArrayBufferView): string {
    if (!input) return ''
    const u = input instanceof ArrayBuffer ? new Uint8Array(input) : new Uint8Array(input.buffer, input.byteOffset, input.byteLength)
    // a BOM is not part of the text
    const body = u.length >= 3 && u[0] === 0xef && u[1] === 0xbb && u[2] === 0xbf ? u.subarray(3) : u
    const text = utf8Text(body)
    // invalid UTF-8 came out as U+FFFD: unless the bytes held U+FFFD themselves (EF BF BD), that is an error for a fatal decoder
    if (this.fatal && text.includes('\ufffd') && !/\xef\xbf\xbd/.test(String.fromCharCode(...body.subarray(0, 65536)))) throw new TypeError('The encoded data was not valid for encoding utf-8')
    return text
  }
}

// --- Headers -------------------------------------------------------------------------------------------------------

type HeadersInit = Headers | Record<string, string> | [string, string][] | Iterable<[string, string]>

export class Headers {
  private m = new Map<string, string>()
  constructor(init?: HeadersInit) {
    if (!init) return
    if (init instanceof Headers) init.forEach((v, k) => this.m.set(k, v))
    else if (Symbol.iterator in Object(init)) for (const [k, v] of init as Iterable<[string, string]>) this.append(k, v)
    else for (const [k, v] of Object.entries(init)) if (v !== undefined) this.append(k, v)
  }
  append(k: string, v: string) {
    const key = String(k).toLowerCase()
    const val = String(v).trim()
    const had = this.m.get(key)
    this.m.set(key, had === undefined ? val : `${had}, ${val}`)
  }
  set(k: string, v: string) {
    this.m.set(String(k).toLowerCase(), String(v).trim())
  }
  get(k: string): string | null {
    return this.m.get(String(k).toLowerCase()) ?? null
  }
  getSetCookie(): string[] {
    const c = this.get('set-cookie')
    return c ? [c] : []
  }
  has(k: string) {
    return this.m.has(String(k).toLowerCase())
  }
  delete(k: string) {
    this.m.delete(String(k).toLowerCase())
  }
  forEach(f: (v: string, k: string, h: Headers) => void) {
    for (const [k, v] of this.sorted()) f(v, k, this)
  }
  private sorted() {
    return [...this.m].sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
  }
  keys() {
    return this.sorted().map(([k]) => k)[Symbol.iterator]()
  }
  values() {
    return this.sorted().map(([, v]) => v)[Symbol.iterator]()
  }
  entries() {
    return this.sorted()[Symbol.iterator]()
  }
  [Symbol.iterator]() {
    return this.entries()
  }
  /** Bun's: the headers as a plain object. */
  toJSON() {
    return Object.fromEntries(this.sorted())
  }
  get count() {
    return this.m.size
  }
}

// --- ReadableStream: what a body and the event stream need ----------------------------------------------------------

type Source<T> = {
  start?: (c: Controller<T>) => void | Promise<void>
  pull?: (c: Controller<T>) => void | Promise<void>
  cancel?: (reason?: unknown) => void | Promise<void>
}

type Controller<T> = { enqueue(chunk: T): void; close(): void; error(e: unknown): void; readonly desiredSize: number | null }

export class ReadableStream<T = Uint8Array> {
  private queue: T[] = []
  private queued = 0
  private state: 'open' | 'closed' | 'errored' = 'open'
  private err: unknown
  private waiting: { resolve: (r: ReadableStreamReadResult<T>) => void; reject: (e: unknown) => void }[] = []
  private pulling = false
  locked = false
  private readonly hwm: number
  private readonly size: (c: T) => number

  constructor(
    private readonly source: Source<T> = {},
    strategy: { highWaterMark?: number; size?: (c: T) => number } = {},
  ) {
    this.hwm = strategy.highWaterMark ?? 1
    this.size = strategy.size ?? (() => 1)
    const self = this
    const c: Controller<T> = {
      enqueue: chunk => this.enqueue(chunk),
      close: () => this.close(),
      error: e => this.fail(e),
      get desiredSize() {
        return self.state === 'errored' ? null : self.state === 'closed' ? 0 : self.hwm - self.queued
      },
    }
    this.controller = c
    try {
      const r = source.start?.(c)
      if (r && typeof (r as Promise<void>).then === 'function') (r as Promise<void>).catch(e => this.fail(e))
    } catch (e) {
      this.fail(e)
    }
  }

  private readonly controller: Controller<T>

  /** A stream of [chunks], then its end. */
  static of<T>(chunks: T[]): ReadableStream<T> {
    return new ReadableStream<T>({
      start(c) {
        for (const x of chunks) c.enqueue(x)
        c.close()
      },
    })
  }

  private enqueue(chunk: T) {
    if (this.state !== 'open') throw new TypeError('Invalid state: the stream is closed')
    const w = this.waiting.shift()
    if (w) return w.resolve({ done: false, value: chunk })
    this.queue.push(chunk)
    this.queued += this.size(chunk)
  }

  private close() {
    if (this.state !== 'open') throw new TypeError('Invalid state: the stream is closed')
    this.state = 'closed'
    if (!this.queue.length) for (const w of this.waiting.splice(0)) w.resolve({ done: true, value: undefined })
  }

  private fail(e: unknown) {
    if (this.state !== 'open') return
    this.state = 'errored'
    this.err = e
    this.queue = []
    this.queued = 0
    for (const w of this.waiting.splice(0)) w.reject(e)
  }

  /** The next chunk, or the end. */
  _read(): Promise<ReadableStreamReadResult<T>> {
    if (this.queue.length) {
      const value = this.queue.shift()!
      this.queued -= this.size(value)
      if (this.state === 'closed' && !this.queue.length) for (const w of this.waiting.splice(0)) w.resolve({ done: true, value: undefined })
      return Promise.resolve({ done: false, value })
    }
    if (this.state === 'closed') return Promise.resolve({ done: true, value: undefined })
    if (this.state === 'errored') return Promise.reject(this.err)
    const p = new Promise<ReadableStreamReadResult<T>>((resolve, reject) => this.waiting.push({ resolve, reject }))
    this.pull()
    return p
  }

  private pull() {
    if (!this.source.pull || this.pulling || this.state !== 'open') return
    this.pulling = true
    Promise.resolve()
      .then(() => this.source.pull!(this.controller))
      .catch(e => this.fail(e))
      .finally(() => {
        this.pulling = false
        if (this.waiting.length) this.pull()
      })
  }

  async cancel(reason?: unknown) {
    if (this.state === 'open') {
      this.state = 'closed'
      this.queue = []
      this.queued = 0
      for (const w of this.waiting.splice(0)) w.resolve({ done: true, value: undefined })
    }
    await this.source.cancel?.(reason)
  }

  getReader() {
    if (this.locked) throw new TypeError('ReadableStream is locked')
    this.locked = true
    const stream = this
    return {
      read: () => stream._read(),
      cancel: (reason?: unknown) => stream.cancel(reason),
      releaseLock: () => void (stream.locked = false),
      get closed() {
        return Promise.resolve(undefined)
      },
    }
  }

  async *[Symbol.asyncIterator](): AsyncGenerator<T> {
    const r = this.getReader()
    try {
      for (;;) {
        const { done, value } = await r.read()
        if (done) return
        yield value as T
      }
    } finally {
      r.releaseLock()
    }
  }
}

type ReadableStreamReadResult<T> = { done: false; value: T } | { done: true; value: undefined }

// --- bodies ------------------------------------------------------------------------------------------------------------

/** A file's body (Bun.file's, bun.ts): read when the answer goes out. */
export type FileBody = { _laniFile: true; path: string; type: string; bytes: () => Uint8Array }

export type BodyInit = string | Uint8Array | ArrayBuffer | ArrayBufferView | ReadableStream<unknown> | URLSearchParams | FileBody | null | undefined

/** What a body holds: text, bytes, a stream, a file, or nothing. */
type Held = { text: string } | { bytes: Uint8Array } | { stream: ReadableStream<unknown> } | { file: FileBody } | null

function hold(b: BodyInit): { held: Held; type?: string } {
  if (b == null) return { held: null }
  if (typeof b === 'string') return { held: { text: b }, type: 'text/plain;charset=utf-8' }
  if (b instanceof URLSearchParams) return { held: { text: b.toString() }, type: 'application/x-www-form-urlencoded;charset=UTF-8' }
  if (b instanceof ReadableStream) return { held: { stream: b } }
  if (b instanceof ArrayBuffer) return { held: { bytes: new Uint8Array(b) } }
  if (ArrayBuffer.isView(b)) return { held: { bytes: new Uint8Array(b.buffer, b.byteOffset, b.byteLength) } }
  if ((b as FileBody)._laniFile) return { held: { file: b as FileBody }, type: (b as FileBody).type || undefined }
  return { held: { text: String(b) }, type: 'text/plain;charset=utf-8' }
}

async function drain(s: ReadableStream<unknown>): Promise<Uint8Array> {
  const parts: Uint8Array[] = []
  const r = s.getReader()
  for (;;) {
    const { done, value } = await r.read()
    if (done) break
    parts.push(typeof value === 'string' ? utf8Bytes(value) : value instanceof Uint8Array ? value : new Uint8Array(value as ArrayBuffer))
  }
  return Buffer.concat(parts)
}

class Body {
  protected held: Held
  private used = false
  readonly headers: Headers

  constructor(body: BodyInit, headers: Headers | Record<string, string> | [string, string][] | undefined) {
    const { held, type } = hold(body)
    this.held = held
    this.headers = new Headers(headers as HeadersInit)
    if (type && !this.headers.has('content-type')) this.headers.set('content-type', type)
  }

  get bodyUsed() {
    return this.used
  }

  /** The body as it is (entry.ts sends text as text, bytes as bytes, and a stream part by part). */
  _held(): Held {
    return this.held
  }

  get body(): ReadableStream<Uint8Array> | null {
    const h = this.held
    if (!h) return null
    if ('stream' in h) return h.stream as unknown as ReadableStream<Uint8Array>
    const bytes = 'text' in h ? utf8Bytes(h.text) : 'bytes' in h ? h.bytes : h.file.bytes()
    const s = ReadableStream.of<Uint8Array>([bytes])
    this.held = { stream: s as unknown as ReadableStream<unknown> }
    return s
  }

  private take(): Held {
    if (this.used) throw new TypeError('Body already used')
    this.used = true
    return this.held
  }

  async bytes(): Promise<Uint8Array> {
    const h = this.take()
    if (!h) return new Uint8Array(0)
    if ('text' in h) return utf8Bytes(h.text)
    if ('bytes' in h) return h.bytes
    if ('file' in h) return h.file.bytes()
    return drain(h.stream)
  }

  async arrayBuffer(): Promise<ArrayBuffer> {
    const b = await this.bytes()
    return b.byteOffset === 0 && b.byteLength === b.buffer.byteLength ? (b.buffer as ArrayBuffer) : (copyOf(b).buffer as ArrayBuffer)
  }

  async text(): Promise<string> {
    const h = this.held
    if (h && 'text' in h) {
      this.take()
      return h.text
    }
    return utf8Text(await this.bytes())
  }

  async json(): Promise<any> {
    return JSON.parse(await this.text())
  }

  async formData(): Promise<never> {
    this.take()
    throw new TypeError('FormData is not available in the phone bridge')
  }

  async blob(): Promise<{ size: number; type: string; arrayBuffer: () => Promise<ArrayBuffer>; text: () => Promise<string> }> {
    const b = await this.bytes()
    const type = this.headers.get('content-type') ?? ''
    return { size: b.length, type, arrayBuffer: async () => copyOf(b).buffer as ArrayBuffer, text: async () => utf8Text(b) }
  }
}

const METHODS = new Set(['DELETE', 'GET', 'HEAD', 'OPTIONS', 'POST', 'PUT', 'PATCH'])

export type RequestInit = {
  method?: string
  headers?: Headers | Record<string, string> | [string, string][]
  body?: BodyInit
  signal?: AbortSignal | null
  redirect?: 'follow' | 'manual' | 'error'
}

export class Request extends Body {
  readonly method: string
  readonly url: string
  readonly signal: AbortSignal
  readonly redirect: string

  constructor(input: string | Request | { href: string }, init: RequestInit = {}) {
    const from = input instanceof Request ? input : undefined
    const headers = init.headers ?? from?.headers
    super(init.body !== undefined ? init.body : null, headers as Headers)
    if (init.body === undefined && from) this.held = from._held()
    const m = (init.method ?? from?.method ?? 'GET').toUpperCase()
    this.method = METHODS.has(m) ? m : init.method ?? from?.method ?? 'GET'
    const url = from ? from.url : typeof input === 'string' ? input : (input as { href: string }).href
    // a request's URL is absolute, as Bun's (new URL throws for a relative one)
    this.url = new (globalThis as unknown as { URL: new (u: string) => { href: string } }).URL(url).href
    this.signal = init.signal ?? from?.signal ?? new AbortSignal()
    this.redirect = init.redirect ?? from?.redirect ?? 'follow'
    if ((this.method === 'GET' || this.method === 'HEAD') && this.held) throw new TypeError('Request with GET/HEAD method cannot have body.')
  }

  clone(): Request {
    return new Request(this)
  }
}

export type ResponseInit = { status?: number; statusText?: string; headers?: Headers | Record<string, string> | [string, string][] }

const STATUS: Record<number, string> = {
  200: 'OK', 201: 'Created', 202: 'Accepted', 204: 'No Content', 206: 'Partial Content', 301: 'Moved Permanently', 302: 'Found', 304: 'Not Modified',
  400: 'Bad Request', 401: 'Unauthorized', 403: 'Forbidden', 404: 'Not Found', 405: 'Method Not Allowed', 409: 'Conflict', 410: 'Gone', 413: 'Payload Too Large',
  415: 'Unsupported Media Type', 416: 'Range Not Satisfiable', 422: 'Unprocessable Entity', 429: 'Too Many Requests', 500: 'Internal Server Error',
  501: 'Not Implemented', 502: 'Bad Gateway', 503: 'Service Unavailable', 504: 'Gateway Timeout',
}

export class Response extends Body {
  readonly status: number
  readonly statusText: string
  readonly type = 'default'
  url = ''
  redirected = false

  constructor(body?: BodyInit, init: ResponseInit = {}) {
    super(body ?? null, init.headers as Headers)
    this.status = init.status ?? 200
    if (this.status < 200 || this.status > 599) throw new RangeError(`The status provided (${this.status}) must be 200 to 599`)
    this.statusText = init.statusText ?? STATUS[this.status] ?? ''
  }

  get ok() {
    return this.status >= 200 && this.status < 300
  }

  /** Bun's Response.json: the JSON text, content-type application/json;charset=utf-8. */
  static json(data: unknown, init: ResponseInit = {}): Response {
    const headers = new Headers(init.headers as HeadersInit)
    if (!headers.has('content-type')) headers.set('content-type', 'application/json;charset=utf-8')
    return new Response(JSON.stringify(data), { ...init, headers })
  }

  static redirect(url: string, status = 302): Response {
    return new Response(null, { status, headers: { location: String(url) } })
  }

  static error(): Response {
    const r = new Response(null, { status: 200 })
    ;(r as { status: number }).status = 0
    return r
  }

  clone(): Response {
    const h = this._held()
    if (h && 'stream' in h) throw new TypeError('a streaming response cannot be cloned in the phone bridge')
    const body = !h ? null : 'text' in h ? h.text : 'bytes' in h ? h.bytes : (h.file as FileBody)
    return new Response(body as BodyInit, { status: this.status, statusText: this.statusText, headers: this.headers })
  }
}
