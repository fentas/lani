// Smoke checks: GET /changes, the version of what the app reads (src/changes.ts; docs/plans/05-built/nav-perf.md), its
// areas' versions and what each read says of them (docs/plans/05-built/changes-areas.md), and the answers' ETags: the same
// answer asked again with its ETag is a 304 without a body.
import { existsSync, mkdirSync, readFileSync, unlinkSync, utimesSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { auth, base, check, client, dataDir, modulesDir } from './harness'

const AREAS = ['learner', 'village', 'towns', 'family', 'content'] as const
type Areas = Record<(typeof AREAS)[number], string>
type Version = { version: string; day: string; areas: Areas }
const version = async (): Promise<Version> => (await (await fetch(`${base}/changes`, { headers: auth })).json()) as Version
const later = (path: string) => {
  const t = new Date(Date.now() + 60_000)
  utimesSync(path, t, t)
}

export default async function changes() {
  check('GET /changes needs the app token', (await fetch(`${base}/changes`)).status === 401)
  const a = await version()
  check('/changes: a version and the day it counts', typeof a.version === 'string' && a.version.length >= 16 && /^\d{4}-\d{2}-\d{2}$/.test(a.day), a)

  // what Home reads again after a Back, the reads themselves move nothing (no GET writes what /changes walks)
  const reads = ['/state', '/modules', '/grammar', '/cards', '/course', '/readings', '/cases', '/packs', '/scenes?arts=1', '/villagers?time=day', '/arrivals', '/culture', '/towns', '/towns/questions', '/voice/index', '/voice/profiles', '/family/challenges']
  const statuses = await Promise.all(reads.map(async p => [p, (await fetch(`${base}${p}`, { headers: auth })).status] as const))
  const b = await version()
  check("/changes: the same version after Home's reads (none writes)", a.version === b.version, { a, b, statuses })

  // the bridge's own bookkeeping (the events kept for the app, the outbox's client ids) moves nothing
  for (const f of ['app/events.json', 'app/client-ids.json']) {
    writeFileSync(join(dataDir, f), existsSync(join(dataDir, f)) ? readFileSync(join(dataDir, f)) : '[]')
    later(join(dataDir, f))
  }
  check("/changes: the bridge's own bookkeeping moves nothing (the events kept, the client ids)", (await version()).version === b.version)

  // a session's update-db.py writes the databases beside the bridge, card.py fixes a card
  later(join(dataDir, 'mastery-db.json'))
  const c = await version()
  check('/changes moves when a database is written beside the bridge (update-db.py, card.py)', c.version !== b.version)
  // an event alone moves nothing (the app handles each as it comes; what one says changed is in a file): a reply
  await client.callTool({ name: 'reply', arguments: { conversation_id: 'c1', text: 'changes' } })
  const d = await version()
  check('/changes: an event alone moves nothing (a reply)', d.version === c.version)
  // content changed under the running bridge (a merge on main): a curated module's file (the smoke's content directory,
  // LANI_MODULES_DIR, set apart from the project's companion/)
  later(join(modulesDir, 'curated-demo.json'))
  check('/changes moves when the content changes under the bridge (a merge on main)', (await version()).version !== d.version)

  // the ETags
  const first = await fetch(`${base}/modules`, { headers: auth })
  const tag = first.headers.get('etag')
  const body = await first.text()
  check("a GET's JSON answer has its ETag", !!tag && /^"[A-Za-z0-9_-]{20,}"$/.test(tag), tag)
  const again = await fetch(`${base}/modules`, { headers: { ...auth, 'if-none-match': tag ?? '' } })
  check('the same answer asked with its ETag is a 304 without a body', again.status === 304 && (await again.text()) === '' && again.headers.get('etag') === tag, again.status)
  const weak = await fetch(`${base}/modules`, { headers: { ...auth, 'if-none-match': `"x", W/${tag}` } })
  check('a list of ETags, the weak form among them, matches too', weak.status === 304, weak.status)
  const other = await fetch(`${base}/modules`, { headers: { ...auth, 'if-none-match': '"other"' } })
  check('another ETag gets the whole answer', other.status === 200 && (await other.text()) === body && other.headers.get('etag') === tag)
  // what speaks to the learner is rendered first: its ETag is the rendered answer's
  const state = await fetch(`${base}/state`, { headers: auth })
  const stateTag = state.headers.get('etag')
  const stateBody = await state.text()
  check("/state's ETag is its body's (304 with it)", !!stateTag && (await fetch(`${base}/state`, { headers: { ...auth, 'if-none-match': stateTag! } })).status === 304 && JSON.parse(stateBody).computed)
  // never a stream, nor a write's answer
  const ctl = new AbortController()
  const stream = await fetch(`${base}/events`, { headers: auth, signal: ctl.signal })
  check('the event stream has no ETag and still streams', stream.status === 200 && stream.headers.get('etag') === null && (stream.headers.get('content-type') ?? '').includes('event-stream'))
  ctl.abort()
  const missing = await fetch(`${base}/modules/no-such-module`, { headers: auth })
  check('an error has no ETag', missing.status === 404 && missing.headers.get('etag') === null, missing.status)
  await areas()
}

/** The areas a read's answer says (lani-areas: `learner=…, content=…`), by name; null without the header. */
const said = (res: Response): Record<string, string> | null => {
  const h = res.headers.get('lani-areas')
  return h === null ? null : Object.fromEntries(h.split(',').map(p => p.trim().split('=') as [string, string]))
}
/** Which areas moved from [a] to [b]. */
const moved = (a: Version, b: Version) => AREAS.filter(x => a.areas[x] !== b.areas[x])
const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b)

/** The areas (changes-areas.md): each moves with its own files only, and each read says the versions it was made at. */
async function areas() {
  const a = await version()
  check('/changes: a version of each area (learner, village, towns, family, content)', AREAS.every(x => typeof a.areas?.[x] === 'string' && a.areas[x].length >= 16) && new Set(AREAS.map(x => a.areas[x])).size === AREAS.length, a)

  // each read the app makes again on a Back says the areas it depends on, at their versions now
  const reads: [string, string[]][] = [
    ['/state', ['learner']], ['/packs', ['learner', 'content']], ['/modules', ['content']], ['/grammar', ['content']], ['/cards', ['content']],
    ['/course', ['content']], ['/readings', ['content']], ['/cases', ['content']], ['/scenes?arts=1', ['content']], ['/villagers?time=day', ['content']],
    ['/arrivals', ['content']], ['/culture', ['content']], ['/scenarios', ['content']], ['/stories', ['content']], ['/drills', ['content']],
    ['/voice/index', ['content']], ['/voice/profiles', ['content']], ['/towns', ['towns', 'content']], ['/towns/guests', ['towns', 'content']],
    ['/towns/children', ['towns', 'content']], ['/towns/questions', ['towns', 'content']], ['/towns/friendship', ['learner', 'village', 'towns', 'content']],
    ['/audio/index', ['family', 'content']], ['/family/api/settings', ['family', 'content']], ['/family/api/challenges', ['family', 'content']],
  ]
  const answers = await Promise.all(reads.map(async ([p]) => {
    const r = await fetch(`${base}${p}`, { headers: auth })
    return { p, status: r.status, areas: said(r), etag: r.headers.get('etag'), body: await r.text() }
  }))
  const now = await version()
  check("Home's reads move no area", same(now.areas, a.areas), moved(a, now))
  const wrong = answers.filter((x, i) => !same(Object.keys(x.areas ?? {}).sort(), [...reads[i][1]].sort()) || reads[i][1].some(n => x.areas?.[n] !== now.areas[n as keyof Areas]))
  check('each read says the areas it depends on, at their versions now (lani-areas)', !wrong.length, wrong.map(x => [x.p, x.status, x.areas]))
  const state = answers[0]
  const again = await fetch(`${base}/state`, { headers: { ...auth, 'if-none-match': state.etag ?? '' } })
  check('a 304 says its areas too', again.status === 304 && same(said(again), state.areas), [again.status, said(again)])
  check('a read without areas says none (the app reads it again whenever anything moved)', said(await fetch(`${base}/game`, { headers: auth })) === null && said(await fetch(`${base}/modules/curated-demo`, { headers: auth })) === null)

  // each area moves with its own files only
  const after = async (what: string, change: () => void, want: string[]) => {
    const before = await version()
    change()
    const b = await version()
    check(`/changes: ${what} moves ${want.join(' and ') || 'nothing'}${want.length ? ' only' : ''}`, same(moved(before, b), want) && (b.version !== before.version) === want.length > 0, moved(before, b))
    return b
  }
  const touch = (rel: string, body?: string) => {
    const p = join(dataDir, rel)
    if (body !== undefined || !existsSync(p)) writeFileSync(p, body ?? '{}')
    later(p)
  }
  await after('a database written beside the bridge (update-db.py, card.py)', () => touch('progress-db.json'), ['learner'])
  await after("the answers' log (a review's answers)", () => touch('app/answers.json', existsSync(join(dataDir, 'app/answers.json')) ? undefined : '{"answers":[]}'), ['learner'])
  const village = join(dataDir, 'app/game.json')
  const hadVillage = existsSync(village)
  if (!hadVillage) {
    const v0 = await after('the village (app/game.json)', () => writeFileSync(village, JSON.stringify({ rev: 1, state: {} })), ['village'])
    unlinkSync(village)
    check('/changes: the village gone again is its version before', (await version()).areas.village === a.areas.village, v0.areas.village)
  } else await after('the village (app/game.json)', () => touch('app/game.json'), ['village'])
  await after("a town's guests or friendships (app/town-*)", () => touch('app/town-smoke.json'), ['towns'])
  unlinkSync(join(dataDir, 'app/town-smoke.json'))
  mkdirSync(join(dataDir, 'app/family'), { recursive: true })
  await after("the family's challenges (app/family)", () => touch('app/family/smoke.json'), ['family'])
  unlinkSync(join(dataDir, 'app/family/smoke.json'))
  await after('a curated module changed under the bridge (a merge on main)', () => later(join(modulesDir, 'curated-demo.json')), ['content'])
  await after("the bridge's bookkeeping (the paired phones, the notifications waiting for the tutor)", () => {
    touch('app/devices-seen.json', existsSync(join(dataDir, 'app/devices-seen.json')) ? undefined : '{}')
    touch('app/channel-queue.json', existsSync(join(dataDir, 'app/channel-queue.json')) ? undefined : '[]')
  }, [])
  const profilePath = join(dataDir, 'learner-profile.json')
  const profile = readFileSync(profilePath, 'utf8')
  const p = JSON.parse(profile)
  await after("the profile's counts (a session's streak)", () => writeFileSync(profilePath, JSON.stringify({ ...p, current_streak_days: (p.current_streak_days ?? 0) + 1 }, null, 2)), ['learner'])
  await after("the learner's name in the profile (what the content says to them)", () => writeFileSync(profilePath, JSON.stringify({ ...p, learner: { ...p.learner, name: 'Janez' } }, null, 2)), ['learner', 'content'])
  writeFileSync(profilePath, profile)

  // what depends on the content only doesn't change with what the learner did: their cards, their mistakes, their streak
  const contentOnly = reads.filter(([, n]) => same(n, ['content'])).map(([p]) => p)
  const tags = async () => Object.fromEntries(await Promise.all([...contentOnly, '/state', '/packs'].map(async p => [p, (await fetch(`${base}${p}`, { headers: auth })).headers.get('etag')] as const)))
  const t0 = await tags()
  const srPath = join(dataDir, 'spaced-repetition.json')
  const srKept = readFileSync(srPath, 'utf8')
  const sr = JSON.parse(srKept)
  const kept = Object.fromEntries(['mistakes-db.json', 'learner-profile.json'].map(f => [f, readFileSync(join(dataDir, f), 'utf8')]))
  const first = Object.values(sr.items ?? {})[0] as Record<string, unknown> | undefined
  writeFileSync(srPath, JSON.stringify({ ...sr, items: { ...sr.items, vocab_smoke_areas: { ...(first ?? {}), item_id: 'vocab_smoke_areas', content: 'kozolec', answer: 'hayrack', due_date: a.day } } }))
  const m = JSON.parse(kept['mistakes-db.json'])
  writeFileSync(join(dataDir, 'mistakes-db.json'), JSON.stringify({ ...m, smoke_areas: { frequency: 3 } }))
  writeFileSync(profilePath, JSON.stringify({ ...p, current_streak_days: 7, total_sessions: (p.total_sessions ?? 0) + 3 }))
  const t1 = await tags()
  const changed = Object.keys(t0).filter(k => t0[k] !== t1[k])
  check("the content's reads say the same after what the learner did (a card, a mistake, a streak): only /state and /packs differ", same(changed.sort(), ['/packs', '/state']), changed)
  writeFileSync(srPath, srKept)
  for (const [f, body] of Object.entries(kept)) writeFileSync(join(dataDir, f), body)

  // a version walked for an answer is taken again for the next ones (two seconds), but never after a write through the bridge
  const w0 = await version()
  later(join(dataDir, 'mastery-db.json')) // beside the bridge: the next answers may still say the version before
  const level = await fetch(`${base}/level`, { method: 'POST', headers: auth, body: '{}' }) // a write's request (refused: nothing written)
  const w1 = said(await fetch(`${base}/state`, { headers: auth }))
  const w2 = await version()
  check('after a write through the bridge, the next answer says the versions walked anew', level.status === 400 && w1?.learner === w2.areas.learner && w2.areas.learner !== w0.areas.learner, { w0: w0.areas.learner, w1, w2: w2.areas.learner, status: level.status })
}
