// The app's own tutor's inbox (docs/plans/05-built/tutor-live.md): while the in-app tutor is attached (POST
// /tutor/attach), every notification the channel would give the Claude Code session waits here instead, until the app
// reads it (GET /tutor/inbox, a long poll) and acknowledges it (POST /tutor/inbox/ack). Kept in a file beside the
// channel's queue (app/tutor-inbox.json, out of the data repository like channel-queue.json), so a restart of the
// bridge loses neither the items nor who is attached.
import { existsSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import type { Log } from './config'

/** A notification as the app's tutor gets it: an [id] that only grows, the channel's [content] and [meta], when it came. */
export type InboxItem = { id: number; content: string; meta: Record<string, string>; at: number }

/** Who is attached ([who]: "app"), since when, which phone asked (its device name, for the status), and its last poll. */
export type AppTutor = { who: string; since: string; device?: string; seen: number }

type Saved = { attached: AppTutor | null; items: InboxItem[]; next: number }

export class TutorInbox {
  private items: InboxItem[] = []
  private next: number
  private waiters = new Set<() => void>()
  /** The app's tutor attached now (persisted with the items), or undefined. */
  attached?: AppTutor

  constructor(
    private readonly path: string,
    private readonly log: Log,
    private readonly max = 500,
    private readonly maxAgeMs = 3 * 86_400_000,
  ) {
    let saved: Partial<Saved> = {}
    try {
      if (existsSync(path)) saved = JSON.parse(readFileSync(path, 'utf8'))
    } catch (e) {
      log(`tutor inbox: ${path} unreadable (${(e as Error).message}): starting empty`)
    }
    this.items = (Array.isArray(saved.items) ? saved.items : []).filter(
      (q: any) => Number.isSafeInteger(q?.id) && typeof q?.content === 'string' && q.meta && typeof q.at === 'number',
    )
    const a = saved.attached
    if (a && typeof a.who === 'string' && typeof a.since === 'string') this.attached = { who: a.who, since: a.since, ...(typeof a.device === 'string' ? { device: a.device } : {}), seen: Number(a.seen) || Date.now() }
    const last = this.items.at(-1)?.id ?? 0
    // ids keep growing across restarts (the app reads after the last one it has), seeded from the clock as the events'
    this.next = Math.max(Date.now(), Number(saved.next) || 0, last + 1)
  }

  get size() {
    return this.items.length
  }

  /** The newest id given out (0: none yet). */
  get lastId() {
    return this.next - 1
  }

  /** Adds a notification; the oldest goes when [max] are waiting (logged). */
  push(content: string, meta: Record<string, string>, at = Date.now()): InboxItem {
    const item = { id: this.next++, content, meta, at }
    this.items.push(item)
    if (this.items.length > this.max) this.log(`tutor inbox: full, the oldest event is dropped (${this.items.shift()!.meta.kind ?? 'event'})`)
    this.save()
    this.wake()
    return item
  }

  /** The items after [id], oldest first; the stale ones (older than [maxAgeMs]) are dropped first (logged). */
  after(id: number): InboxItem[] {
    this.prune()
    return this.items.filter(q => q.id > id)
  }

  /** Waits up to [ms] for an item after [id] (or until [stop] says so); the items after [id] then. */
  async wait(id: number, ms: number, stop: () => boolean = () => false): Promise<InboxItem[]> {
    const now = this.after(id)
    if (now.length || ms <= 0 || stop()) return now
    await new Promise<void>(resolve => {
      const done = () => {
        clearTimeout(timer)
        this.waiters.delete(done)
        resolve()
      }
      const timer = setTimeout(done, ms)
      this.waiters.add(done)
    })
    return this.after(id)
  }

  /** Ends every wait now (an item came, the bridge stops). */
  wake() {
    for (const w of [...this.waiters]) w()
  }

  /** Removes the items [ids], and with [upto] every item up to that id. Returns how many went. */
  ack(ids: number[] = [], upto?: number): number {
    const drop = new Set(ids)
    const before = this.items.length
    this.items = this.items.filter(q => !drop.has(q.id) && !(upto !== undefined && q.id <= upto))
    const gone = before - this.items.length
    if (gone) this.save()
    return gone
  }

  /** Every item still waiting, in order, and the inbox emptied (a detach gives them back to the session). */
  takeAll(): InboxItem[] {
    this.prune()
    const all = this.items
    this.items = []
    this.save()
    return all
  }

  /** The app's tutor attached ([a]) or not (undefined), kept. */
  setAttached(a: AppTutor | undefined) {
    this.attached = a
    this.save()
  }

  /** The app read the inbox: its lease holds (kept at most once a minute). */
  seen(now = Date.now()) {
    if (!this.attached) return
    const before = this.attached.seen
    this.attached.seen = now
    if (now - before > 60_000) this.save()
  }

  private prune() {
    const now = Date.now()
    const fresh = this.items.filter(q => now - q.at <= this.maxAgeMs)
    if (fresh.length < this.items.length) {
      this.log(`tutor inbox: ${this.items.length - fresh.length} event(s) older than ${this.maxAgeMs / 86_400_000} days dropped`)
      this.items = fresh
      this.save()
    }
  }

  private save() {
    try {
      const saved: Saved = { attached: this.attached ?? null, items: this.items, next: this.next }
      writeFileSync(`${this.path}.tmp`, JSON.stringify(saved), { mode: 0o600 })
      renameSync(`${this.path}.tmp`, this.path)
    } catch (e) {
      this.log(`tutor inbox not saved: ${(e as Error).message}`)
    }
  }
}
