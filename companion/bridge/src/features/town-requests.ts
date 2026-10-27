// A child's town without the PC (../town-requests.ts; companion/README.md, "A child's town"; docs/plans/05-built/
// invitations.md): its links wait for the parent, who says yes or no in their own app, on the family page, or on the node.
// - between bridges (signed, as every /town/… route): GET /town/guardian/requests and POST /town/guardian/decide (the
//   child's side: only a linked town that is the child's guardian, 403 otherwise), POST /town/guardian/notify (the
//   guardian's side: a child's town tells its parent's of a link that waits) and POST /town/approved (the other town's
//   side: the child's town linked it now);
// - for the learner's own app (the app token): GET /towns/requests and POST /towns/requests/:id/withdraw (a child's own
//   links that wait), GET /towns/children (a parent's: their children's towns, each with what waits for them, and the
//   children of this node they may link), POST /towns/children/:id/link (their own child's town of this node, linked as
//   its parent) and POST /towns/children/:id/requests/:request (yes or no, sent to the child's town, signed);
// - the family page (the family token only for a decision: the child holds the app token): GET /family/api/towns and
//   POST /family/api/towns/requests/:id.
import { join } from 'node:path'
import { z } from 'zod'
import type { FeatureFactory } from '../feature'
import { json, readText, type RouteCtx } from '../http'
import {
  LinkRequests,
  REQUEST_ID,
  linkLocalChild,
  localChildren,
  localDisplay,
  localHello,
  previewOf,
  requestView,
  settle,
  statusOf,
  type LinkRequest,
} from '../town-requests'
import {
  TOWN_ID,
  TOWN_VERSION,
  Nonces,
  TownError,
  Towns,
  checkSigned,
  cleanDisplay,
  cleanText,
  selfDisplay,
  selfUrl,
  signedJson,
  townRequest,
  type TownLink,
} from '../towns'

const AGES = ['OGENJ', 'TABOR', 'ZASELEK', 'VAS', 'TRG', 'MESTO']
const decideIn = z.object({ v: z.number().int().min(1), request: z.string().regex(REQUEST_ID), approve: z.boolean(), town: z.string().regex(TOWN_ID).optional() })
const appDecideIn = z.object({ approve: z.boolean(), town: z.string().regex(TOWN_ID).optional() })
const familyDecideIn = z.object({ approve: z.boolean(), guardian: z.boolean().optional(), town: z.string().regex(TOWN_ID).optional() })
/** A child's town may tell its guardian's this often an hour of links that wait (each is a notice on the parent's phone). */
const NOTIFY_PER_HOUR = Number(process.env.LANI_TOWN_NOTIFY_PER_HOUR ?? 12)

/** A request as another town's bridge sent it (the child's, to its guardian's): every field checked and cleaned again. */
function cleanRequest(q: any) {
  if (!q || typeof q !== 'object' || typeof q.id !== 'string' || !REQUEST_ID.test(q.id) || typeof q.town?.id !== 'string' || !TOWN_ID.test(q.town.id)) return undefined
  const p = q.preview
  const buildings: Record<string, number> = {}
  for (const [k, n] of Object.entries(p?.buildings && typeof p.buildings === 'object' ? p.buildings : {}).slice(0, 40)) {
    if (/^[A-Z_]{1,32}$/.test(k) && Number.isInteger(n) && (n as number) > 0) buildings[k] = Math.min(99, n as number)
  }
  const iso = (v: unknown) => (typeof v === 'string' && !Number.isNaN(Date.parse(v)) ? v.slice(0, 30) : '')
  return {
    id: q.id,
    town: { id: q.town.id, ...cleanDisplay(q.town) },
    via: q.via === 'accept' ? 'accept' : 'invite',
    at: iso(q.at),
    expires_at: iso(q.expires_at),
    ...(q.theirs_pending === true ? { theirs_pending: true } : {}),
    ...(p && typeof p === 'object'
      ? { preview: { age: AGES.includes(p.age) ? p.age : null, villagers: Number.isInteger(p.villagers) ? Math.max(0, Math.min(999, p.villagers)) : 0, buildings } }
      : {}),
  }
}

export const townRequests: FeatureFactory = ({ cfg, townKey: key, profile, culture, events, relay, log }) => {
  const store = new Towns(cfg.appDir, log)
  const requests = new LinkRequests(cfg.appDir)
  const nonces = new Nonces()
  const self = key.fingerprint
  const url = selfUrl(cfg.port, process.env, self)
  const relayAddr = relay?.townUrl
  const display = () => selfDisplay({ dataDir: cfg.dataDir, profile, culture: culture.id, manifest: culture.manifest, towns: store })
  /** The node's owner (its default learner, an adult) links their own children's towns of this node directly. */
  const owner = profile.isDefault && !profile.child
  const notified = new Map<string, number[]>()

  async function signed(c: RouteCtx, keyOf: (id: string) => string | undefined) {
    const body = await readText(c.req, 16 * 1024)
    const r = checkSigned(c.req.headers, { method: c.req.method, path: c.url.pathname + c.url.search, body, self, keyOf, nonces })
    return { r, body, refused: r.ok ? undefined : json({ error: 'unauthorized', code: r.code }, 401) }
  }
  const linkedKey = (id: string) => store.get(id)?.public_key
  /** The child's guardian asks: a linked town whose link says `guardian`; anyone else is refused (403). */
  const guardianOnly = (from: string) =>
    !profile.child ? json({ error: "this isn't a child's town", code: 'not_a_child' }, 403)
    : store.get(from)?.guardian !== true ? json({ error: "only the child's guardian (a parent's town) decides", code: 'not_guardian' }, 403)
    : undefined
  const parse = <T>(text: string, schema: z.ZodType<T>): T | undefined => {
    try {
      const r = schema.safeParse(JSON.parse(text))
      return r.success ? r.data : undefined
    } catch {
      return undefined
    }
  }
  async function appBody<T>(c: RouteCtx, schema: z.ZodType<T>): Promise<T | undefined> {
    return parse(await readText(c.req, 4 * 1024), schema)
  }

  /** A decision was made here: the child's app hears how it went, the relay of a new reader. */
  function decided(r: LinkRequest) {
    const d = r.town.display
    const kind = r.status === 'approved' ? 'linked' : r.status === 'withdrawn' ? 'withdrawn' : 'declined'
    events.emit({ type: 'town_link', kind, request: r.id, town: { id: r.town.id, name: d.name, learner: d.learner } })
    if (r.status === 'approved') relay?.nudge()
    log(`towns: ${d.name} (${d.learner}) ${r.status === 'approved' ? 'is linked now' : r.status === 'withdrawn' ? "isn't asked for any more" : 'was declined'} (${r.by}${r.guardian ? ` ${r.guardian.slice(0, 8)}` : ''})`)
  }

  /** The requests that wait here, each with how its town looks now (it linked this child already, so it answers). */
  const openWithPreviews = () => Promise.all(requests.open().map(async q => ({ ...requestView(q), preview: await previewOf(key, q.town) })))

  /** A child of a parent's town: one of this node's, or a ward it is linked with; with what waits for its parent. */
  type Child = {
    id: string
    name: string
    learner: string
    culture: string
    language: string
    /** On this node (its profile), so this town may link it directly. */
    profile?: string
    linked: boolean
    /** This town is its guardian: its links wait for this one's yes. */
    guardian: boolean
    can_link: boolean
    status: 'ok' | 'unreachable' | 'refused' | 'unsupported' | 'bad_answer'
    requests: NonNullable<ReturnType<typeof cleanRequest>>[]
  }

  /** What waits for a ward's parent, asked of its town (signed): its requests, or why not. */
  async function wardRequests(l: TownLink): Promise<Pick<Child, 'status' | 'guardian' | 'requests'>> {
    try {
      const r = await townRequest(key, l, 'GET', '/town/guardian/requests', undefined, { timeoutMs: 6_000, maxBytes: 128 * 1024 })
      if (r.status === 200) return { status: 'ok', guardian: true, requests: (Array.isArray(r.body?.requests) ? r.body.requests : []).slice(0, 50).map(cleanRequest).filter((q: unknown): q is NonNullable<ReturnType<typeof cleanRequest>> => !!q) }
      if (r.status === 403) {
        // not (or no longer) its guardian: not asked again
        store.patch(l.id, ({ ward: _, ...x }) => x)
        return { status: 'ok', guardian: false, requests: [] }
      }
      return { status: r.status === 401 ? 'refused' : r.status === 404 ? 'unsupported' : 'bad_answer', guardian: true, requests: [] }
    } catch (e) {
      return { status: e instanceof TownError ? e.code : 'unreachable', guardian: true, requests: [] }
    }
  }

  return {
    routes: [
      // --- between bridges: the child's side ------------------------------------------------------------------------
      {
        // the links that wait for this child's parent, for its guardian's town: who, how, and how their village looks
        method: 'GET',
        path: '/town/guardian/requests',
        access: 'public',
        handle: async c => {
          const { r, refused } = await signed(c, linkedKey)
          if (!r.ok) return refused!
          const no = guardianOnly(r.from)
          if (no) return no
          return signedJson(key, r.nonce, { v: TOWN_VERSION, town: self, requests: await openWithPreviews() })
        },
      },
      {
        // the guardian's yes or no to one of them: final (a request decided can't be decided again)
        method: 'POST',
        path: '/town/guardian/decide',
        access: 'public',
        handle: async c => {
          const { r, body, refused } = await signed(c, linkedKey)
          if (!r.ok) return refused!
          const no = guardianOnly(r.from)
          if (no) return no
          const b = parse(body, decideIn)
          if (!b) return json({ error: 'expected {v, request, approve, town?}', code: 'bad_request' }, 400)
          const done = await settle({ key, towns: store, requests, id: b.request, approve: b.approve, by: 'guardian', guardian: r.from, town: b.town, log })
          if (!done.ok) return json({ error: done.error, code: done.code }, done.status)
          decided(done.request)
          return signedJson(key, r.nonce, { ok: true, status: done.request.status, town: requestView(done.request).town, told: done.told })
        },
      },
      // --- between bridges: the guardian's side and the other town's ---------------------------------------------------
      {
        // a child's town (linked) tells its parent's of a link that waits: a notice in the parent's app
        method: 'POST',
        path: '/town/guardian/notify',
        access: 'public',
        handle: async c => {
          const { r, body, refused } = await signed(c, linkedKey)
          if (!r.ok) return refused!
          const now = Date.now()
          const times = (notified.get(r.from) ?? []).filter(t => now - t < 3_600_000)
          if (times.length >= NOTIFY_PER_HOUR) return json({ error: 'too many notices from that town', code: 'rate_limited' }, 429)
          notified.set(r.from, [...times, now])
          let q: ReturnType<typeof cleanRequest>
          try {
            q = cleanRequest(JSON.parse(body)?.request)
          } catch {}
          if (!q) return json({ error: 'expected {v, request: {id, town}}', code: 'bad_request' }, 400)
          // that child's town says this one is its guardian: it is asked for what waits (a lie only makes it be asked)
          const child = store.patch(r.from, l => ({ ...l, ward: true }))
          events.emit({ type: 'town_link', kind: 'request', request: q.id, child: { id: r.from, name: child?.display?.name ?? '', learner: child?.display?.learner ?? '' }, town: { name: q.town.name, learner: q.town.learner } })
          log(`towns: ${child?.display?.learner ?? r.from} would like to link with ${q.town.name} (${q.town.learner}): the parent decides`)
          return signedJson(key, r.nonce, { ok: true })
        },
      },
      {
        // the child's town linked this one now (its parent said yes): this town's waiting link is whole
        method: 'POST',
        path: '/town/approved',
        access: 'public',
        handle: async c => {
          // a linked town's key, or (from one child's town to another) that of a request that waits here
          const { r, refused } = await signed(c, id => linkedKey(id) ?? requests.openFor(id)?.town.public_key)
          if (!r.ok) return refused!
          const l = store.get(r.from)
          if (l) {
            if (l.pending) store.patch(r.from, ({ pending: _, ...x }) => x)
            events.emit({ type: 'town_link', kind: 'linked', town: { id: r.from, name: l.display?.name ?? '', learner: l.display?.learner ?? '' } })
            log(`towns: ${l.display?.name ?? r.from} linked back: its parent said yes`)
          } else requests.theirsApproved(r.from)
          return signedJson(key, r.nonce, { ok: true })
        },
      },

      // --- the learner's app: a child's own links that wait ------------------------------------------------------------
      {
        method: 'GET',
        path: '/towns/requests',
        handle: () => json({ requests: requests.all().map(q => requestView(q)) }),
      },
      {
        // the child takes one back (the other town lets go of it): a child may always link less
        method: 'POST',
        path: /^\/towns\/requests\/(lr_[0-9a-f]{16})\/withdraw$/,
        handle: async ({ params: [id] }) => {
          const done = await settle({ key, towns: store, requests, id, approve: false, by: 'child', log })
          if (!done.ok) return json({ error: done.error, code: done.code }, done.status)
          decided(done.request)
          return json({ ok: true, status: done.request.status })
        },
      },

      // --- the learner's app: a parent's children ---------------------------------------------------------------------
      {
        method: 'GET',
        path: '/towns/children',
        handle: async () => {
          if (profile.child) return json({ children: [], can_link_local: false })
          const out = new Map<string, Child>()
          // this node's children, when this town is its owner's: linked or not
          if (owner) {
            const kids = localChildren(cfg.projectDir)
            const hellos = await Promise.all(kids.map(c => localHello(c)))
            kids.forEach((c, i) => {
              const hello = hellos[i]
              const d = localDisplay(c, culture.dir)
              const l = hello ? store.get(hello.id) : undefined
              const theirs = hello ? new Towns(join(c.dataDir, 'app')).get(self) : undefined
              out.set(hello?.id ?? `profile:${c.profile}`, {
                id: hello?.id ?? '', ...d, profile: c.profile, linked: !!l && !l.pending, guardian: theirs?.guardian === true,
                can_link: !!hello, status: hello ? 'ok' : 'unreachable', requests: [],
              })
            })
          }
          // the children whose guardian this town is (or says it is): what waits for their parent
          const wards = store.links().filter(l => !l.pending && (l.ward || l.theirs?.guardian || out.get(l.id)?.guardian))
          await Promise.all(wards.map(async l => {
            const w = await wardRequests(l)
            const had = out.get(l.id)
            if (!w.guardian && !had) return
            const d = l.display ?? cleanDisplay({})
            out.set(l.id, { ...(had ?? { id: l.id, ...d, linked: true, can_link: false }), linked: true, guardian: w.guardian, status: w.status, requests: w.requests })
          }))
          return json({ children: [...out.values()], can_link_local: owner })
        },
      },
      {
        // a parent links their own child's town of this node, as its guardian (the app's lani-town link)
        method: 'POST',
        path: /^\/towns\/children\/([0-9a-f]{32})\/link$/,
        handle: async ({ params: [id] }) => {
          if (profile.child) return json({ error: "a child's town doesn't link children", code: 'child' }, 403)
          if (!owner) return json({ error: "only the town of this node's owner (its default learner) links the node's children: others ask the child's parent", code: 'not_owner' }, 403)
          for (const c of localChildren(cfg.projectDir)) {
            const hello = await localHello(c)
            if (hello?.id !== id) continue
            const done = await linkLocalChild({ self: { key, towns: store, url, relay: relayAddr, display: display() }, child: c, culturesDir: culture.dir })
            if (!done) return json({ error: "the child's town doesn't answer: its bridge isn't running", code: 'unreachable' }, 502)
            relay?.nudge()
            log(`towns: linked ${done.link.display?.learner}'s town (${c.profile}) as its parent's`)
            return json({ ok: true, town: { id: done.child, ...done.link.display }, guardian: true })
          }
          return json({ error: "no child's town of this node has that id", code: 'not_local' }, 404)
        },
      },
      {
        // the parent's yes or no to a link that waits for them, sent to the child's town (signed by this town)
        method: 'POST',
        path: /^\/towns\/children\/([0-9a-f]{32})\/requests\/(lr_[0-9a-f]{16})$/,
        handle: async c => {
          const [id, rid] = c.params
          if (profile.child) return json({ error: "a child's town decides nothing for others", code: 'child' }, 403)
          const l = store.get(id)
          if (!l) return json({ error: 'no such town', code: 'unknown_town' }, 404)
          const b = await appBody(c, appDecideIn)
          if (!b) return json({ error: 'expected {approve, town?}', code: 'bad_request' }, 400)
          let r: { status: number; body: any }
          try {
            r = await townRequest(key, l, 'POST', '/town/guardian/decide', { v: TOWN_VERSION, request: rid, approve: b.approve, ...(b.town ? { town: b.town } : {}) }, { timeoutMs: 8_000 })
          } catch (e) {
            return json({ error: (e as Error).message, code: e instanceof TownError ? e.code : 'unreachable' }, 502)
          }
          if (r.status === 200) return json({ ok: true, status: r.body?.status === 'approved' ? 'approved' : 'declined', town: { id: r.body?.town?.id, ...cleanDisplay(r.body?.town) } })
          const code = typeof r.body?.code === 'string' && /^[a-z_]{1,40}$/.test(r.body.code) ? r.body.code : 'refused'
          return json({ error: cleanText(r.body?.error, 200) || `the town answered ${r.status}`, code }, [403, 404, 409, 410].includes(r.status) ? r.status : 502)
        },
      },

      // --- the family page ---------------------------------------------------------------------------------------
      {
        // what waits for this child's parent, the towns it is linked with, and who its guardians are
        method: 'GET',
        path: '/family/api/towns',
        access: 'any',
        handle: async () => {
          const me = display()
          return json({
            child: profile.child,
            learner: me.learner,
            town: { name: me.name, language: me.language },
            requests: profile.child ? await openWithPreviews() : [],
            decided: profile.child ? requests.all().filter(q => statusOf(q) !== 'pending').slice(-10).reverse().map(q => requestView(q)) : [],
            towns: store.links().map(l => ({ id: l.id, name: l.display?.name ?? '', learner: l.display?.learner ?? '', language: l.display?.language ?? '', guardian: l.guardian === true, pending: l.pending === 'guardian' })),
          })
        },
      },
      {
        // the family's yes or no (the family token only: the child's own app can't approve its links); [guardian]: that
        // town is a parent's, and becomes the child's guardian
        method: 'POST',
        path: /^\/family\/api\/towns\/requests\/(lr_[0-9a-f]{16})$/,
        access: 'any',
        handle: async c => {
          if (c.role !== 'family') return json({ error: "a child's links are approved by a parent: on the family page, in the parent's app, or on the node", code: 'family_only' }, 403)
          if (!profile.child) return json({ error: "this isn't a child's town", code: 'not_a_child' }, 403)
          const b = await appBody(c, familyDecideIn)
          if (!b) return json({ error: 'expected {approve, guardian?, town?}', code: 'bad_request' }, 400)
          const done = await settle({ key, towns: store, requests, id: c.params[0], approve: b.approve, by: 'family', markGuardian: b.approve && b.guardian === true, town: b.town, log })
          if (!done.ok) return json({ error: done.error, code: done.code }, done.status)
          decided(done.request)
          return json({ ok: true, status: done.request.status, guardian: done.request.made_guardian === true, told: done.told })
        },
      },
    ],
  }
}
