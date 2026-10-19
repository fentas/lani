// Smoke checks, the last section: without app/lease.json (every node's data until `lani handover` writes one) the bridge
// writes as it always did: a write route of the app (POST /readings/done, persisted through update-db.py) and a tutor's
// publishing tool (publish_gloss) both write, and nothing makes a lease. At the end, so the reading and the gloss it
// adds change nothing a later section counts.
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { auth, base, check, client, dataDir } from './harness'

export default async function leaseNone() {
  const lease = join(dataDir, 'app/lease.json')
  check('no lease: app/lease.json is not there (as on every node before `lani handover`)', !existsSync(lease))
  const mastery = () => JSON.parse(readFileSync(join(dataDir, 'mastery-db.json'), 'utf8')).skills.reading
  const before = mastery()
  const r = await fetch(`${base}/readings/done`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ kind: 'reading', id: 'smoke-jutro', title: 'Jutro v vasi', level: 'A1', exercises: 2, correct: 2, duration_minutes: 1, questions: [], client_id: 'smoke-lease-none-01' }),
  })
  const b: any = await r.json()
  const after = mastery()
  check('no lease: a write route of the app writes (POST /readings/done, update-db.py)', r.status === 200 && typeof b.session_id === 'string' && after.practice_count === (before.practice_count ?? 0) + 2, [r.status, b, after])
  const pub: any = await client.callTool({ name: 'publish_gloss', arguments: { form: 'zakupnina', lemma: 'zakupnina', pos: 'noun', gloss: ['lease fee'] } })
  const tutorGlosses = readFileSync(join(dataDir, 'app/lexicon.json'), 'utf8')
  check("no lease: the tutor's publishing tools write (publish_gloss)", !pub.isError && tutorGlosses.includes('zakupnina'), pub.content?.[0]?.text)
  const look: any = await (await fetch(`${base}/lookup?w=zakupnina`, { headers: auth })).json()
  check('… and the app reads it back', JSON.stringify(look).includes('lease fee'), look)
  check('no lease: nothing made one', !existsSync(lease))
}
