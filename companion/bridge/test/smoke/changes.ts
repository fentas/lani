// Smoke checks: GET /changes, the version of what the app reads (src/changes.ts; docs/plans/05-built/nav-perf.md), and
// the answers' ETags: the same answer asked again with its ETag is a 304 without a body.
import { existsSync, readFileSync, utimesSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { auth, base, check, client, dataDir, modulesDir } from './harness'

type Version = { version: string; day: string }
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
  // an event: the tutor did something (here a reply)
  await client.callTool({ name: 'reply', arguments: { conversation_id: 'c1', text: 'changes' } })
  const d = await version()
  check('/changes moves with each event', d.version !== c.version)
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
}
