// The phone bridge's event loop, driven by the host: QuickJS runs only while the app calls into it, so what waits (a
// timer, a fetch) waits in Kotlin, and Kotlin calls back here when it's time (`__laniLoop.timer(id)`,
// `__laniLoop.fetched(id)`): each call a short evaluation of its own, after which QuickJS runs the promise jobs it made.
// So nothing ever blocks the bridge's thread, and an event stream, a request and a tutor's tool call interleave as on
// a node. setTimeout, setInterval, their clear*, setImmediate, queueMicrotask and fetch are the globals over it.
import { Buffer } from './buffer'
import { fromHost, host, toHost } from './host'
import { AbortSignal, DOMException, Headers, Request, Response, type BodyInit, type RequestInit } from './web'

type Timer = { fn: (...a: unknown[]) => void; args: unknown[]; every?: number }

const timers = new Map<number, Timer>()
let nextTimer = 1

/** What setTimeout returns: a number to the eye (node's Timeout is one through Symbol.toPrimitive), with ref and unref. */
class Timeout {
  constructor(readonly id: number) {}
  ref() {
    return this
  }
  unref() {
    return this
  }
  hasRef() {
    return true
  }
  refresh() {
    const t = timers.get(this.id)
    if (t) host().setTimer(this.id, t.every ?? 0)
    return this
  }
  close() {
    clear(this)
  }
  [Symbol.toPrimitive]() {
    return this.id
  }
}

function schedule(fn: (...a: unknown[]) => void, ms: unknown, args: unknown[], every: boolean): Timeout {
  if (typeof fn !== 'function') throw new TypeError('The "callback" argument must be of type function')
  const id = nextTimer++
  const delay = Math.max(0, Number(ms) || 0)
  timers.set(id, { fn, args, ...(every ? { every: Math.max(1, delay) } : {}) })
  host().setTimer(id, every ? Math.max(1, delay) : delay)
  return new Timeout(id)
}

function clear(t: unknown) {
  const id = t instanceof Timeout ? t.id : Number(t)
  if (!timers.delete(id)) return
  host().clearTimer(id)
}

export const setTimeout = (fn: (...a: unknown[]) => void, ms?: number, ...args: unknown[]) => schedule(fn, ms, args, false)
export const setInterval = (fn: (...a: unknown[]) => void, ms?: number, ...args: unknown[]) => schedule(fn, ms, args, true)
export const setImmediate = (fn: (...a: unknown[]) => void, ...args: unknown[]) => schedule(fn, 0, args, false)
export const clearTimeout = clear
export const clearInterval = clear
export const clearImmediate = clear

/** The host's timer [id] is due: its function runs (an interval's is asked for again first). */
function fire(id: number) {
  const t = timers.get(id)
  if (!t) return
  if (t.every) host().setTimer(id, t.every)
  else timers.delete(id)
  t.fn(...t.args)
}

export const queueMicrotask = (fn: () => void) => void Promise.resolve().then(fn)

// --- fetch ---------------------------------------------------------------------------------------------------------

type Pending = { resolve: (r: Response) => void; reject: (e: unknown) => void; signal?: AbortSignal }

const fetches = new Map<number, Pending>()
let nextFetch = 1

async function bodyBytes(b: BodyInit): Promise<Uint8Array | null> {
  if (b == null) return null
  return new Uint8Array(await new Response(b).arrayBuffer())
}

/** fetch over the host (OkHttp in the app): the whole answer at once, a signal's timeout as the call's. */
export async function fetch(input: string | Request | { href: string }, init: RequestInit = {}): Promise<Response> {
  const req = input instanceof Request ? input : undefined
  const url = req ? req.url : typeof input === 'string' ? input : (input as { href: string }).href
  const method = (init.method ?? req?.method ?? 'GET').toUpperCase()
  const headers = new Headers((init.headers ?? req?.headers) as Headers)
  const signal = init.signal ?? req?.signal ?? undefined
  const body = init.body !== undefined ? init.body : req ? await req.arrayBuffer().then(b => (b.byteLength ? new Uint8Array(b) : null)) : null
  const r = new Request(url, { method, headers, body: body as BodyInit })
  const bytes = await bodyBytes(body as BodyInit)
  // a body's own content type (a string's text/plain) when the caller gave none, as fetch sends it
  if (bytes && !headers.has('content-type') && r.headers.has('content-type')) headers.set('content-type', r.headers.get('content-type')!)
  if (signal?.aborted) throw signal.reason ?? new DOMException('This operation was aborted', 'AbortError')
  const id = nextFetch++
  const done = new Promise<Response>((resolve, reject) => fetches.set(id, { resolve, reject, signal: signal ?? undefined }))
  const onAbort = () => {
    const p = fetches.get(id)
    if (!p) return
    fetches.delete(id)
    host().fetchCancel(id)
    p.reject(signal?.reason ?? new DOMException('This operation was aborted', 'AbortError'))
  }
  signal?.addEventListener('abort', onAbort, { once: true })
  host().fetch(id, method, r.url, JSON.stringify([...headers]), bytes ? toHost(bytes) : null, (signal as AbortSignal | undefined)?._timeoutMs ?? 0, init.redirect ?? req?.redirect ?? 'follow')
  try {
    return await done
  } finally {
    signal?.removeEventListener('abort', onAbort)
  }
}

/** The host's answer to fetch [id] is there. */
function fetched(id: number) {
  const p = fetches.get(id)
  const head = JSON.parse(host().fetchResult(id)) as { status?: number; statusText?: string; headers?: [string, string][]; url?: string; redirected?: boolean; error?: string; timeout?: boolean }
  const raw = host().fetchBody(id)
  if (!p) return
  fetches.delete(id)
  if (head.error !== undefined) {
    const e = head.timeout ? new DOMException('The operation timed out.', 'TimeoutError') : new TypeError(`fetch failed: ${head.error}`)
    return p.reject(e)
  }
  const res = new Response(raw ? Buffer.view(fromHost(raw)) : null, { status: head.status || 200, statusText: head.statusText, headers: head.headers })
  // a status the Response class refuses (1xx, 0) can't come from OkHttp; the URL after redirects and whether there were any
  res.url = head.url ?? ''
  res.redirected = !!head.redirected
  p.resolve(res)
}

/** What the host calls: a timer that's due, a fetch that's done. */
export const loop = { timer: fire, fetched, pending: () => ({ timers: timers.size, fetches: fetches.size }) }
