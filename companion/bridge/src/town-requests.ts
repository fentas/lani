// Links that wait for a child's parent (companion/README.md, "A child's town"; docs/plans/05-built/invitations.md).
// Shared by the bridge (features/towns.ts, features/town-requests.ts) and the CLI (cli/town.ts).
//
// A child's app shows and scans invitations like any other, but nothing links without the parent:
// - the child's invitation accepted by another town (it joins), or another town's invitation accepted by the child (the
//   child's town joins it with `pending: "guardian"`): the other town links, marked as waiting ([TownLink.pending]); the
//   child's town keeps a request here (<data>/app/town-requests.json) and links nothing;
// - its guardian's town (the parent's: the child's link to it says `guardian`) approves or declines it from the parent's
//   app: signed requests between the bridges (GET /town/guardian/requests, POST /town/guardian/decide), which the
//   child's bridge takes only from a linked town that is its guardian; or the family page does (the family token, never
//   the app's: the child holds the app token); or a parent on the node (lani-town approve | decline --profile <child>);
// - approved: the child's town links the other and tells it (POST /town/approved), which then counts it linked;
//   declined (or withdrawn by the child): the child's town tells it to let go (POST /town/unlink), and keeps nothing;
// - a decision is final: one decided can't be decided again (409), and every signed request is taken once (its nonce).
//
// A parent whose town runs on the same node as the child's (the node's owner: the default profile) links it directly,
// as `lani-town link` does: both towns' links written, theirs a guardian's ([linkLocalChild]).
import { randomBytes } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { readManifest, tutorOf } from './cultures'
import { entryDataDir, learnerFacts, profilesDir, readRegistry, type ProfileEntry } from './learners'
import { fingerprintOf, withLock, writeAtomic, type BridgeKey } from './pairing'
import {
  TOWN_ID,
  TOWN_VERSION,
  TownError,
  Towns,
  cleanDisplay,
  cleanPublicState,
  cleanText,
  selfUrl,
  townRequest,
  type TownDisplay,
  type TownLink,
} from './towns'

/** A request waits this long for the parent; then it is gone (the other town lets go of its waiting link a day later). */
export const REQUEST_TTL_MS = Number(process.env.LANI_TOWN_REQUEST_DAYS ?? 14) * 86_400_000
/** Decided requests are kept this long (the child's app says how it went; a late replay finds it decided). */
const KEEP_DECIDED_MS = 30 * 86_400_000
/** At most this many requests wait at once: a join beyond is refused (429). */
export const MAX_OPEN_REQUESTS = Number(process.env.LANI_TOWN_OPEN_REQUESTS ?? 20)
export const REQUEST_ID = /^lr_[0-9a-f]{16}$/

/** The other town of a request: what linking it takes (its key, URL, relay address) and how it showed itself. */
export type RequestTown = { id: string; public_key: string; url: string; relay?: string; display: TownDisplay }

export type RequestStatus = 'pending' | 'approved' | 'declined' | 'withdrawn' | 'expired'

export type LinkRequest = {
  id: string
  town: RequestTown
  /** `invite`: that town accepted this child's invitation; `accept`: the child accepted that town's. */
  via: 'invite' | 'accept'
  at: string
  expires_at: string
  status: Exclude<RequestStatus, 'expired'>
  /** That town is a child's too and links back once its own parent says yes: until then, the link here waits as well. */
  theirs_pending?: true
  /** That town's parent said yes already (POST /town/approved came first): the link here is whole once this one's does. */
  theirs_approved?: true
  decided_at?: string
  /** Who decided: the guardian's town ([guardian] its id), the family page, a parent on the node, the child or the other town (withdrawn). */
  by?: 'guardian' | 'family' | 'node' | 'child' | 'them'
  guardian?: string
  /** The approval made that town the child's guardian (the family page's "this is the parent's town"). */
  made_guardian?: true
}

type RequestsFile = { schema: 'lani.town-requests/v1'; requests: LinkRequest[] }

/** A request as it is now: [RequestStatus] `expired` for one that waited too long. */
export const statusOf = (r: LinkRequest, now = Date.now()): RequestStatus => (r.status === 'pending' && Date.parse(r.expires_at) <= now ? 'expired' : r.status)

/** <appDir>/town-requests.json: the links that wait for this child's parent, and those decided lately. Under a lock (the CLI too). */
export class LinkRequests {
  readonly file: string

  constructor(appDir: string) {
    this.file = join(appDir, 'town-requests.json')
  }

  private read(): RequestsFile {
    if (!existsSync(this.file)) return { schema: 'lani.town-requests/v1', requests: [] }
    try {
      const d = JSON.parse(readFileSync(this.file, 'utf8'))
      const requests = (Array.isArray(d?.requests) ? d.requests : []).filter(
        (r: LinkRequest) => r && REQUEST_ID.test(r.id) && TOWN_ID.test(r.town?.id ?? '') && typeof r.town.public_key === 'string' && typeof r.town.url === 'string',
      )
      return { schema: 'lani.town-requests/v1', requests }
    } catch {
      return { schema: 'lani.town-requests/v1', requests: [] }
    }
  }

  /** Changes the file under its lock, from what is on disk now; requests decided or run out long ago are swept. */
  private change<T>(fn: (rs: LinkRequest[]) => { requests: LinkRequest[]; out: T }, now = Date.now()): T {
    return withLock(`${this.file}.lock`, () => {
      const { requests, out } = fn(this.read().requests)
      const kept = requests.filter(r => Date.parse(r.decided_at ?? r.expires_at) > now - KEEP_DECIDED_MS)
      writeAtomic(this.file, JSON.stringify({ schema: 'lani.town-requests/v1', requests: kept }, null, 2) + '\n')
      return out
    })
  }

  all(): LinkRequest[] {
    return this.read().requests
  }

  get(id: string): LinkRequest | undefined {
    return this.all().find(r => r.id === id)
  }

  /** The requests that wait for the parent now, oldest first. */
  open(now = Date.now()): LinkRequest[] {
    return this.all().filter(r => statusOf(r, now) === 'pending')
  }

  /** The request that waits for town [town], if any. */
  openFor(town: string, now = Date.now()): LinkRequest | undefined {
    return this.open(now).find(r => r.town.id === town)
  }

  /**
   * A new request to link [town] (one waits per town: a second one from it replaces the first, and its time starts
   * again); undefined when [MAX_OPEN_REQUESTS] wait already.
   */
  add(town: RequestTown, via: LinkRequest['via'], o: { theirsPending?: boolean; now?: number } = {}): LinkRequest | undefined {
    const now = o.now ?? Date.now()
    return this.change(rs => {
      const others = rs.filter(r => !(r.town.id === town.id && statusOf(r, now) === 'pending'))
      if (others.filter(r => statusOf(r, now) === 'pending').length >= MAX_OPEN_REQUESTS) return { requests: rs, out: undefined }
      const r: LinkRequest = {
        id: `lr_${randomBytes(8).toString('hex')}`,
        town: { id: town.id, public_key: town.public_key, url: town.url, ...(town.relay ? { relay: town.relay } : {}), display: cleanDisplay(town.display) },
        via,
        at: new Date(now).toISOString(),
        expires_at: new Date(now + REQUEST_TTL_MS).toISOString(),
        status: 'pending',
        ...(o.theirsPending ? { theirs_pending: true as const } : {}),
      }
      return { requests: [...others, r], out: r }
    }, now)
  }

  /**
   * Decides request [id] once: approved, declined or withdrawn, by whom. Refused when there is none (`unknown_request`), it
   * was decided before (`decided`: a decision is final, so a replay can't turn it), or it waited too long (`expired`).
   */
  decide(id: string, status: 'approved' | 'declined' | 'withdrawn', by: NonNullable<LinkRequest['by']>, o: { guardian?: string; madeGuardian?: boolean; now?: number } = {}): { ok: true; request: LinkRequest } | { ok: false; code: 'unknown_request' | 'decided' | 'expired' } {
    const now = o.now ?? Date.now()
    return this.change<{ ok: true; request: LinkRequest } | { ok: false; code: 'unknown_request' | 'decided' | 'expired' }>(rs => {
      const r = rs.find(x => x.id === id)
      if (!r) return { requests: rs, out: { ok: false, code: 'unknown_request' } }
      const st = statusOf(r, now)
      if (st === 'expired') return { requests: rs, out: { ok: false, code: 'expired' } }
      if (st !== 'pending') return { requests: rs, out: { ok: false, code: 'decided' } }
      const done: LinkRequest = { ...r, status, decided_at: new Date(now).toISOString(), by, ...(o.guardian ? { guardian: o.guardian } : {}), ...(o.madeGuardian ? { made_guardian: true as const } : {}) }
      return { requests: rs.map(x => (x.id === id ? done : x)), out: { ok: true, request: done } }
    }, now)
  }

  /** That town's parent said yes first (child to child): kept on its open request. Whether there was one. */
  theirsApproved(town: string, now = Date.now()): boolean {
    return this.change(rs => {
      const r = rs.find(x => x.town.id === town && statusOf(x, now) === 'pending')
      return r ? { requests: rs.map(x => (x === r ? { ...x, theirs_approved: true as const } : x)), out: true } : { requests: rs, out: false }
    }, now)
  }
}

/** A request as an app, a guardian's town or the family page sees it: never the other town's key or address. */
export function requestView(r: LinkRequest, now = Date.now()) {
  const d = r.town.display
  return {
    id: r.id,
    town: { id: r.town.id, name: d.name, learner: d.learner, culture: d.culture, language: d.language },
    via: r.via,
    at: r.at,
    expires_at: r.expires_at,
    status: statusOf(r, now),
    ...(r.decided_at ? { decided_at: r.decided_at, by: r.by } : {}),
    ...(r.theirs_pending && !r.theirs_approved ? { theirs_pending: true } : {}),
  }
}
export type RequestView = ReturnType<typeof requestView>

/** How a request's town looks now, for its preview: its age, how many live there, its buildings by type. */
export type TownPreview = { age: string | null; villagers: number; buildings: Record<string, number>; date: string }

const previews = new Map<string, { at: number; p: TownPreview | null }>()

/**
 * The village of a request's town, for the parent to see who it is (the child's town asks it, signed: that town linked
 * the child already); kept for a minute, null when it doesn't answer.
 */
export async function previewOf(key: BridgeKey, t: RequestTown, timeoutMs = 3_000): Promise<TownPreview | null> {
  const hit = previews.get(t.id)
  if (hit && Date.now() - hit.at < 60_000) return hit.p
  let p: TownPreview | null = null
  try {
    const r = await townRequest(key, { id: t.id, public_key: t.public_key, url: t.url, ...(t.relay ? { relay: t.relay } : {}) }, 'GET', '/town/public', undefined, { timeoutMs })
    const s = r.status === 200 ? cleanPublicState(r.body, t.id) : undefined
    if (s) {
      const buildings: Record<string, number> = {}
      for (const b of s.buildings) buildings[b.type] = (buildings[b.type] ?? 0) + 1
      p = { age: s.age, villagers: s.villagers, buildings, date: s.date }
    }
  } catch {}
  previews.set(t.id, { at: Date.now(), p })
  return p
}

/** The request's town as a peer to ask. */
const peerOf = (t: RequestTown) => ({ id: t.id, public_key: t.public_key, url: t.url, ...(t.relay ? { relay: t.relay } : {}) })

export type SettleResult =
  | { ok: true; request: LinkRequest; link?: TownLink; told: boolean }
  | { ok: false; status: number; code: string; error: string }

/**
 * Decides request [id] of the child's town ([key], [towns], [requests]) and does what it says: approved, the child's town
 * links the other ([markGuardian]: as its guardian; still waiting when that town is a child's whose parent hasn't said yes)
 * and tells it (POST /town/approved); declined or withdrawn, it tells the other town to let go (POST /town/unlink). [told]:
 * whether the other town heard it now (it finds out by itself otherwise: GET /towns asks it).
 */
export async function settle(o: {
  key: BridgeKey
  towns: Towns
  requests: LinkRequests
  id: string
  approve: boolean
  by: NonNullable<LinkRequest['by']>
  guardian?: string
  markGuardian?: boolean
  /** The request must be for this town (what the decider saw): a mismatch is refused. */
  town?: string
  timeoutMs?: number
  log?: (...a: unknown[]) => void
}): Promise<SettleResult> {
  const had = o.requests.get(o.id)
  if (had && o.town && had.town.id !== o.town) return { ok: false, status: 409, code: 'mismatch', error: 'that request is for another town' }
  const status = o.approve ? 'approved' : o.by === 'child' ? 'withdrawn' : 'declined'
  const d = o.requests.decide(o.id, status, o.by, { guardian: o.guardian, madeGuardian: o.approve && o.markGuardian })
  if (!d.ok) {
    const why = { unknown_request: [404, 'no such request'], decided: [409, 'that request was decided already'], expired: [410, 'that request waited too long'] } as const
    return { ok: false, status: why[d.code][0], code: d.code, error: why[d.code][1] }
  }
  const r = d.request
  const peer = peerOf(r.town)
  let told = false
  if (o.approve) {
    const link: TownLink = {
      id: r.town.id,
      public_key: r.town.public_key,
      url: r.town.url,
      ...(r.town.relay && r.town.relay !== r.town.url ? { relay: r.town.relay } : {}),
      linked_at: new Date().toISOString(),
      by: o.by === 'node' ? 'cli' : 'app',
      via: r.via,
      display: r.town.display,
      ...(o.markGuardian ? { guardian: true as const } : {}),
      ...(r.theirs_pending && !r.theirs_approved ? { pending: 'guardian' as const } : {}),
    }
    o.towns.upsert(link)
    try {
      told = (await townRequest(o.key, peer, 'POST', '/town/approved', { v: TOWN_VERSION }, { timeoutMs: o.timeoutMs ?? 5_000 })).status === 200
    } catch (e) {
      o.log?.(`towns: ${r.town.display.name} couldn't be told it is linked now (${(e as Error).message}); it finds out when it asks`)
    }
    return { ok: true, request: r, link, told }
  }
  try {
    told = (await townRequest(o.key, peer, 'POST', '/town/unlink', {}, { timeoutMs: o.timeoutMs ?? 5_000 })).status === 200
  } catch (e) {
    if (!(e instanceof TownError)) throw e
    o.log?.(`towns: ${r.town.display.name} couldn't be told to let go (${e.message}); its waiting link runs out`)
  }
  return { ok: true, request: r, told }
}

/**
 * Tells this child's guardians' towns (the links that say `guardian`) of request [r], so the parent hears of it (their
 * app's notice); in the background, each on its own. Nothing goes to any other town.
 */
export function notifyGuardians(key: BridgeKey, towns: Towns, r: LinkRequest, log?: (...a: unknown[]) => void) {
  const body = { v: TOWN_VERSION, request: requestView(r) }
  for (const g of towns.links().filter(l => l.guardian === true)) {
    townRequest(key, g, 'POST', '/town/guardian/notify', body, { timeoutMs: 5_000 }).catch(e => log?.(`towns: ${g.display?.name ?? g.id} (a guardian) wasn't told of a link that waits: ${(e as Error).message}`))
  }
}

// --- a parent's own child on this node ------------------------------------------------------------------------------

/** A child's profile of this node, as a parent's town there sees it: where its bridge answers and where its data is. */
export type LocalChild = { profile: string; port: number; dataDir: string; culture: string; env: Record<string, string> }

/** The children's profiles of this node (profiles.json, `lani-profile add --child`). */
export function localChildren(projectDir: string): LocalChild[] {
  let profiles: ProfileEntry[] = []
  try {
    const dir = profilesDir(projectDir)
    profiles = readRegistry(dir).profiles.filter(e => e?.child === true && typeof e.port === 'number').map(e => ({ ...e, data_dir: entryDataDir(dir, e) }))
  } catch {
    return []
  }
  return profiles.map(e => ({ profile: e.id, port: e.port, dataDir: e.data_dir, culture: e.culture ?? 'primorska', env: { ...(e.env ?? {}) } }))
}

/** A child's town of this node: its id and key, as its bridge says them (GET /town/hello on its port: no key is read here). */
export async function localHello(c: LocalChild, timeoutMs = 3_000): Promise<{ id: string; public_key: string } | undefined> {
  try {
    const r = await fetch(`http://127.0.0.1:${c.port}/town/hello`, { signal: AbortSignal.timeout(timeoutMs), redirect: 'manual' })
    const h = (await r.json()) as { id?: unknown; public_key?: unknown }
    const pk = typeof h.public_key === 'string' ? h.public_key : ''
    return r.ok && typeof h.id === 'string' && TOWN_ID.test(h.id) && pk && fingerprintOf(Buffer.from(pk, 'base64')) === h.id ? { id: h.id, public_key: pk } : undefined
  } catch {
    return undefined
  }
}

/** How a child's town of this node shows itself: its village's name (the child's choice, else its culture pack's), the child's first name, its culture and language. */
export function localDisplay(c: LocalChild, culturesDir: string): TownDisplay {
  const m = readManifest(culturesDir, c.culture)
  const towns = new Towns(join(c.dataDir, 'app'))
  return cleanDisplay({
    name: towns.name() || (m.ok ? tutorOf(m.manifest).village : '') || c.profile,
    learner: cleanText(learnerFacts(c.dataDir).name.split(/\s+/)[0] ?? '', 40) || c.profile,
    culture: c.culture,
    language: m.ok ? m.manifest.language : '',
  })
}

/**
 * A parent links their own child's town of this node directly (the app's `lani-town link`): both links written, the
 * child's to this town as its guardian, this town's to the child as its ward; what was set on them stays (what each
 * shares, a guardian's floor). A request of the child's that waits for this town is approved by it. Undefined when the
 * child's bridge doesn't answer (its id and key come from it).
 */
export async function linkLocalChild(o: {
  self: { key: BridgeKey; towns: Towns; url: string; relay?: string; display: TownDisplay }
  child: LocalChild
  culturesDir: string
}): Promise<{ link: TownLink; child: string } | undefined> {
  const hello = await localHello(o.child)
  if (!hello) return undefined
  const now = new Date().toISOString()
  const childTowns = new Towns(join(o.child.dataDir, 'app'))
  const url = selfUrl(o.child.port, o.child.env, hello.id)
  const display = localDisplay(o.child, o.culturesDir)
  const link: TownLink = { id: hello.id, public_key: hello.public_key, url, linked_at: now, by: 'app', via: 'link', display, ward: true }
  o.self.towns.upsert(link)
  childTowns.upsert({
    id: o.self.key.fingerprint,
    public_key: o.self.key.spki.toString('base64'),
    url: o.self.url,
    ...(o.self.relay && o.self.relay !== o.self.url ? { relay: o.self.relay } : {}),
    linked_at: now,
    by: 'app',
    via: 'link',
    display: o.self.display,
    guardian: true,
  })
  const asked = new LinkRequests(join(o.child.dataDir, 'app'))
  const open = asked.openFor(o.self.key.fingerprint)
  if (open) asked.decide(open.id, 'approved', 'node', { guardian: o.self.key.fingerprint, madeGuardian: true })
  return { link, child: hello.id }
}
