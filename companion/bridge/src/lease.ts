// Who writes the learner's data repository: the lease, app/lease.json (docs/plans/05-built/git-sync.md, "The lease").
// The rules are .claude/hooks/lani_lease.py's, which also hands the data over (lani handover); the app has them too
// (companion/android/.../sync/Lease.kt). Keep the three in step.
//
// The bridge honours the lease: while it isn't this side's (the phone holds the data after `lani handover`), the app's
// writes are refused with 423 and the tutor's writing tools with an error, each saying why; reads go on. A node goes by
// its own copy of the lease, or by the repository's when a refused push fetched it (.git/lani-lease-remote.json,
// lani_data_repo.py push). The phone's bridge (the hosted mode) runs the same guard as side 'phone', with its device id
// (LANI_SIDE=phone, LANI_LEASE_DEVICE).
import { readFileSync, statSync } from 'node:fs'
import { hostname } from 'node:os'
import { join } from 'node:path'
import { json, type Route } from './http'

export const LEASE_FILE = 'app/lease.json'
export const REMOTE_NOTE = 'lani-lease-remote.json'

export type Side = 'node' | 'phone'
export type Party = { side: Side; device: string; name: string; mode: 'self-hosted' | 'hosted' }
export type Offer = Party & { by: Party | null; at: string | null }
export type Lease =
  | { schema: 'lani.lease/v1'; holder: Party | null; offer: Offer | null; since: string | null; renewed: string | null; expires: string | null; seen_remote?: boolean }
  | { invalid: true; seen_remote?: boolean }
/** What a lease is for one side (lani_lease.py TAKEABLE has what each allows). */
export type LeaseState = 'mine' | 'offered' | 'offered-away' | 'free' | 'expired' | 'unclaimed' | 'other'

const MODES = { node: 'self-hosted', phone: 'hosted' } as const

const text = (v: unknown, n: number) => (typeof v === 'string' ? v.replace(/[\u0000-\u001f\u007f]/g, '').trim().slice(0, n) : '')
const time = (v: unknown) => (typeof v === 'string' && !Number.isNaN(Date.parse(v)) ? v : null)

export function party(p: unknown): Party | null {
  const o = (p && typeof p === 'object' ? p : {}) as Record<string, unknown>
  if (o.side !== 'node' && o.side !== 'phone') return null
  const mode = o.mode === 'self-hosted' || o.mode === 'hosted' ? o.mode : MODES[o.side]
  return { side: o.side, device: text(o.device, 64), name: text(o.name, 60), mode }
}

export function parseLease(raw: string): Lease {
  let d: any
  try {
    d = JSON.parse(raw)
  } catch {
    return { invalid: true }
  }
  if (!d || typeof d !== 'object' || !String(d.schema ?? '').startsWith('lani.lease/')) return { invalid: true }
  const o = party(d.offer)
  return {
    schema: 'lani.lease/v1',
    holder: party(d.holder),
    offer: o ? { ...o, by: party(d.offer.by), at: time(d.offer.at) } : null,
    since: time(d.since),
    renewed: time(d.renewed),
    expires: time(d.expires),
  }
}

const same = (a: Party | null | undefined, me: Party) => !!a && a.side === me.side && (me.side === 'node' || a.device === me.device)
const offeredTo = (o: Offer, me: Party) => o.side === me.side && (!o.device || me.side === 'node' || o.device === me.device)
const expired = (l: { expires: string | null }, now: number) => l.expires !== null && now > Date.parse(l.expires)

export function leaseState(lease: Lease | null, me: Party, now = Date.now(), hasData = true): LeaseState {
  if (!lease) return me.side === 'node' ? 'mine' : hasData ? 'unclaimed' : 'free'
  if ('invalid' in lease) return 'other'
  if (lease.holder) return same(lease.holder, me) ? 'mine' : expired(lease, now) ? 'expired' : 'other'
  if (lease.offer) {
    if (offeredTo(lease.offer, me)) return 'offered'
    if (same(lease.offer.by, me)) return 'offered-away'
    return expired(lease, now) ? 'expired' : 'other'
  }
  return 'free'
}

const who = (p: Party | null | undefined) => (p ? `the ${p.side}${p.name || p.device ? ` (${p.name || p.device})` : ''}` : 'nobody')

/** One line: who writes the data, since when (lani_lease.describe). */
export function describeLease(lease: Lease | null): string {
  if (!lease) return 'no lease: the node writes the data (as before the lease)'
  if ('invalid' in lease) return `${LEASE_FILE} can't be read: nobody may write until it is fixed (or removed, on the side that writes)`
  const since = (lease.since ?? '?').replace('T', ' ').replace(/Z$/, '')
  const out = lease.holder
    ? `${who(lease.holder)} writes the data, since ${since} UTC`
    : lease.offer
      ? `handed over to ${who(lease.offer)} by ${who(lease.offer.by)} on ${since} UTC: waiting for ${who(lease.offer)} to take it`
      : 'released: nobody writes the data until a side takes it'
  return lease.seen_remote ? `${out} (the repository's lease: a push of this node's was refused)` : out
}

/** This bridge as a party of the lease: the node by default; the phone's bridge says LANI_SIDE=phone and its device id. */
export function selfParty(env: Record<string, string | undefined> = process.env): Party {
  const side: Side = env.LANI_SIDE === 'phone' ? 'phone' : 'node'
  const device = text(env.LANI_LEASE_DEVICE || (side === 'node' ? hostname() : ''), 64) || side
  return { side, device, name: device, mode: MODES[side] }
}

/** The lease of the data directory [dataDir] as this side goes by it, read again whenever its files changed. */
export class LeaseGuard {
  private stamp = ''
  private lease: Lease | null = null

  constructor(
    readonly dataDir: string,
    readonly me: Party = selfParty(),
    private readonly env: Record<string, string | undefined> = process.env,
  ) {}

  private read(path: string): { lease: Lease | null; stamp: string } {
    try {
      const st = statSync(path)
      return { lease: parseLease(readFileSync(path, 'utf8')), stamp: `${st.ino}:${st.mtimeMs}:${st.size}` }
    } catch (e) {
      // no file, or a parent that is a file (a .git of a worktree): none; anything else can't be read
      return ['ENOENT', 'ENOTDIR'].includes(String((e as NodeJS.ErrnoException).code)) ? { lease: null, stamp: 'none' } : { lease: { invalid: true }, stamp: 'bad' }
    }
  }

  current(): Lease | null {
    const local = this.read(join(this.dataDir, LEASE_FILE))
    const note = this.me.side === 'node' ? this.read(join(this.dataDir, '.git', REMOTE_NOTE)) : { lease: null, stamp: 'none' }
    const stamp = `${local.stamp}|${note.stamp}`
    if (stamp !== this.stamp) {
      this.stamp = stamp
      // a note that can't be read is no note
      this.lease = note.lease && !('invalid' in note.lease) && leaseState(note.lease, this.me) !== 'mine' ? { ...note.lease, seen_remote: true } : local.lease
    }
    return this.lease
  }

  state(): LeaseState {
    return leaseState(this.current(), this.me)
  }

  /**
   * Why this side may not write now, or undefined when it may (LANI_LEASE=ignore: it may). Without a lease every side
   * writes, as before the lease: a node as always, a phone without git.
   */
  refusal(): string | undefined {
    if ((this.env.LANI_LEASE ?? '').toLowerCase() === 'ignore') return undefined
    const lease = this.current()
    if (lease === null) return undefined
    const st = leaseState(lease, this.me)
    if (st === 'mine') return undefined
    const back = this.me.side === 'node' ? 'lani handover --take on the node takes it back once it is handed over' : 'take it over in the app (Podatki · Data)'
    return `Lani's data isn't this ${this.me.side}'s to write now: ${describeLease(lease)}. Reads work; ${back}.`
  }
}

/**
 * Paths of the app's API that write no learner data: pairing a phone, the tutor's permission prompts, the developer's
 * files, speech recognition and the voice store (a cache of clips).
 */
const NOT_DATA = [
  /^\/pair$/, /^\/permission$/, /^\/health$/, /^\/dev\//, /^\/stt$/, /^\/voice\//,
  // a visit's voices (features/visit-voices.ts): clips of the visited town kept in the voice store, not the learner's data
  /^\/towns\/[0-9a-f]{32}\/voice\//,
  // the app's own tutor's session (features/tutor.ts): attaching and its inbox are the channel's, not the learner's data;
  // its tool calls are guarded tool by tool (the session's by guardTools, the app session's own in features/tutor.ts)
  /^\/tutor\/(attach|detach|inbox\/ack)$/, /^\/tutor\/tools\/[\w-]+$/,
  // a draft checked by its validator (tutor-studio.ts): nothing is written
  /^\/tutor\/check$/,
]
const READS = new Set(['GET', 'HEAD', 'OPTIONS'])

/**
 * [routes], where a write (any method but GET, HEAD and OPTIONS, except [NOT_DATA]'s paths) is refused with 423 while the
 * lease isn't this side's: `{ error: 'read_only', message, lease }`. The app keeps the write queued and says so.
 */
export function guardRoutes(routes: Route[], guard: LeaseGuard): Route[] {
  return routes.map(r => ({
    ...r,
    handle: c => {
      if (READS.has(c.req.method) || NOT_DATA.some(p => p.test(c.url.pathname))) return r.handle(c)
      const why = guard.refusal()
      return why ? json({ error: 'read_only', message: why, lease: guard.current() }, 423) : r.handle(c)
    },
  }))
}

/** The tutor's tools that write the learner's data: publishing, removing, rolling back, setting the rhythm. */
export const WRITING_TOOL = /^(publish|remove|rollback|set)_/

/**
 * What [guardTools] needs of a tool: its name and handler (channel.ts's Tool; no import of it, so the phone's bundle,
 * which has no MCP SDK, can hold this file).
 */
type ToolLike = { name: string; handle: (args: Record<string, any>) => unknown }

/** [tools], where a writing tool ([WRITING_TOOL]) fails with the reason (a tool error) while the lease isn't this side's. */
export function guardTools<T extends ToolLike>(tools: T[], guard: LeaseGuard): T[] {
  return tools.map(t =>
    WRITING_TOOL.test(t.name)
      ? {
          ...t,
          handle: (args: Record<string, any>) => {
            const why = guard.refusal()
            return why ? { content: [{ type: 'text' as const, text: `Nothing written. ${why}` }], isError: true } : t.handle(args)
          },
        }
      : t,
  )
}
