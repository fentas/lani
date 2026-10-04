// Outbound events for the app: a replay buffer and the SSE streams reading it.
import { readFileSync, renameSync, writeFileSync } from 'node:fs'
import type { Log } from './config'

export type AppEvent = { type: string; [k: string]: unknown }

type Listener = (id: number, e: AppEvent) => void

/**
 * Characters a stream may have waiting for its client before the client counts as stalled and is
 * dropped. Well above a full replay (200 events).
 */
const MAX_QUEUED = 16 * 1024 * 1024

export class Events {
  private readonly buffer: { id: number; event: AppEvent }[] = []
  private readonly listeners = new Set<Listener>()
  private readonly taps = new Set<(e: AppEvent) => void>()
  // Seeded from the clock so ids keep increasing across bridge restarts (clients resume by id).
  private nextId = Date.now()

  /** Where the buffer is kept across restarts (the bridge service, persist()); none in the classic mode. */
  private saved?: { path: string; log: Log; timer?: ReturnType<typeof setTimeout> }

  /**
   * Keeps the buffer in [path] across restarts (the bridge service restarts at every deploy): read now, written 2 s after
   * an event and by flush(). A phone that was away gets the replies of before the restart. Ids go on from the last one.
   */
  persist(path: string, log: Log) {
    this.saved = { path, log }
    try {
      const kept = JSON.parse(readFileSync(path, 'utf8'))
      const weekAgo = Date.now() - 7 * 86_400_000
      const valid = (Array.isArray(kept) ? kept : []).filter(
        (b: any) => Number.isSafeInteger(b?.id) && typeof b?.event?.type === 'string' && (b.event.at ?? 0) > weekAgo,
      )
      this.buffer.splice(0, this.buffer.length, ...valid.slice(-200))
      const last = this.buffer.at(-1)?.id ?? 0
      if (last >= this.nextId) this.nextId = last + 1
      if (this.buffer.length) log(`events: ${this.buffer.length} kept from before the restart`)
    } catch {} // none yet, or unreadable: a fresh buffer
  }

  /** Writes the buffer now (at a restart); nothing in the classic mode. */
  flush() {
    const s = this.saved
    if (!s) return
    clearTimeout(s.timer)
    s.timer = undefined
    try {
      writeFileSync(`${s.path}.tmp`, JSON.stringify(this.buffer))
      renameSync(`${s.path}.tmp`, s.path)
    } catch (e) {
      s.log(`events not saved: ${(e as Error).message}`)
    }
  }

  emit = (e: AppEvent) => {
    const id = this.nextId++
    const event = { ...e, at: Date.now() } // lets the app skip stale permission prompts on replay
    this.buffer.push({ id, event })
    if (this.buffer.length > 200) this.buffer.shift()
    if (this.saved && !this.saved.timer) this.saved.timer = setTimeout(() => this.flush(), 2_000)
    for (const t of this.taps) {
      try {
        t(event)
      } catch {} // a feature's tap never keeps the event from the app
    }
    for (const l of this.listeners) l(id, event)
  }

  /**
   * Runs [fn] on every event emitted from now on, in the bridge: a feature keeping what the tutor replied to the
   * conversations it started (features/questions.ts).
   */
  tap(fn: (e: AppEvent) => void) {
    this.taps.add(fn)
  }

  /** Whether an app is listening right now. */
  get connected() {
    return this.listeners.size > 0
  }

  /** The buffered events after [since]. */
  backlog(since: number) {
    return this.buffer.filter(b => b.id > since)
  }

  /**
   * An SSE stream: the buffered events after Last-Event-ID (or ?since=), then live ones. A stream
   * that can't be written to (closed, or a client that stopped reading) is dropped.
   */
  stream(req: Request): Response {
    const since = Number(req.headers.get('last-event-id') ?? new URL(req.url).searchParams.get('since') ?? 0)
    let cleanup = () => {}
    const stream = new ReadableStream<string>({
      start: ctrl => {
        let open = true
        let ping: ReturnType<typeof setInterval> | undefined
        cleanup = () => {
          open = false
          clearInterval(ping)
          this.listeners.delete(send)
        }
        const write = (chunk: string) => {
          if (!open) return
          try {
            if ((ctrl.desiredSize ?? 0) < -MAX_QUEUED) throw new Error('client stopped reading')
            ctrl.enqueue(chunk)
          } catch {
            cleanup()
            try {
              ctrl.close()
            } catch {}
          }
        }
        const send = (id: number, e: AppEvent) => write(`id: ${id}\ndata: ${JSON.stringify(e)}\n\n`)
        write(': connected\n\n')
        for (const b of this.buffer) if (b.id > since) send(b.id, b.event)
        if (!open) return
        this.listeners.add(send)
        ping = setInterval(() => write(': ping\n\n'), 25_000)
        req.signal.addEventListener('abort', cleanup)
      },
      cancel: () => cleanup(),
    }, { highWaterMark: 0, size: chunk => chunk?.length ?? 0 })
    return new Response(stream, { headers: { 'content-type': 'text/event-stream', 'cache-control': 'no-cache' } })
  }
}
