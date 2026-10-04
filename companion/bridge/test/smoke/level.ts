// Smoke checks: the level-up (features/level.ts): the treasure map passed in the app (POST /level) is persisted via
// update-db.py (current_level and level_since; once, by client_id and by the level), the tutor hears level_up with the
// new level's grammar pages; a level that isn't the next one, or a bad station, is refused.
import { auth, base, channelEvents, check, guideOf } from './harness'

const stations = [
  { id: 'letter', skill: 'writing', right: 4, total: 5, minutes: 4 },
  { id: 'dialog', skill: 'speaking', right: 5, total: 6, minutes: 5 },
  { id: 'listen', skill: 'listening', right: 3, total: 4, minutes: 3 },
  { id: 'read', skill: 'reading', right: 4, total: 4 },
  { id: 'words', skill: 'vocabulary', right: 9, total: 10, minutes: 2 },
]
const post = (body: object) => fetch(`${base}/level`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
const state = async (q = '') => (await fetch(`${base}/state${q}`, { headers: auth })).json()
const levelUps = (since: number) => channelEvents.slice(since).filter(n => n.params?.meta?.kind === 'level_up')

export default async function level() {
  const heard = channelEvents.length
  const home0 = await state()
  const sessions0 = home0.databases.session_log.sessions.length

  // --- passed: the new level, persisted ------------------------------------------------------------------------------
  const r = await post({ from: 'a1', to: 'A2', stations, client_id: 'smoke-level-0001' })
  const b = await r.json()
  check('POST /level: the treasure map passed, persisted via update-db.py', r.ok && typeof b.session_id === 'string' && b.level === 'A2' && b.already === undefined, b)
  const s = await state()
  const lp = s.databases.learner_profile.learner
  check('… GET /state: the level is A2, since today', lp.current_level === 'A2' && lp.level_since === s.computed.today, lp)
  const entry = s.databases.session_log.sessions.find((x: any) => x.session_id === b.session_id)
  check(
    '… a session of its own: /lani-app-treasure, the stations as skill scores, their minutes',
    entry?.command_used === '/lani-app-treasure' && entry.exercises_completed === 29 && entry.duration_minutes === 14 &&
      ['writing', 'speaking', 'listening', 'reading', 'vocabulary'].every(k => entry.skills_practiced.includes(k)) && entry.score_breakdown.writing === 0.8 &&
      entry.notes.includes('the level test at the end of A1 passed (letter 4/5, dialog 5/6'),
    entry,
  )
  check('… and a milestone', s.databases.session_log.milestones.some((m: any) => m.session_id === b.session_id && /^Reached A2 in \w+: the treasure hunt$/.test(m.milestone)), s.databases.session_log.milestones)
  await Bun.sleep(100)
  const note = levelUps(heard)
  const text: string = note[0]?.params?.content ?? ''
  check(
    'level_up reaches Claude: already persisted, the new level and its grammar pages',
    note.length === 1 && note[0].params.meta.msg_id === b.session_id && text.includes('is now A2') && text.includes('ALREADY persisted') && text.includes(b.session_id) && text.includes('letter 4/5, dialog 5/6') && /dajalnik \(The dative/.test(text) && text.includes('orodnik (') && !text.includes('odvisni-govor'),
    text,
  )

  // --- sent again: nothing twice ------------------------------------------------------------------------------------
  const replay = await post({ from: 'A1', to: 'A2', stations, client_id: 'smoke-level-0001' })
  const rb = await replay.json()
  check('… once per client_id: a repeat gets the first answer', replay.headers.get('x-lani-replay') === '1' && rb.session_id === b.session_id)
  const twice = await post({ from: 'A1', to: 'A2', stations, client_id: 'smoke-level-0002' })
  const tb = await twice.json()
  const s2 = await state()
  check('… the level already reached: 200 already, nothing written', twice.ok && tb.already === true && tb.level === 'A2' && s2.databases.session_log.sessions.length === sessions0 + 1 && s2.databases.learner_profile.learner.level_since === lp.level_since, tb)
  await Bun.sleep(100)
  check('… and nothing more said to Claude', levelUps(heard).length === 1)

  // --- refused ------------------------------------------------------------------------------------------------------
  const refused: [string, object, string][] = [
    ['a level that isn\'t the next one', { from: 'A1', to: 'B1', stations }, 'the level after A1 is A2'],
    ['a level down', { from: 'A2', to: 'A1', stations }, 'the level after A2 is B1'],
    ['none after C2', { from: 'C2', to: 'C2', stations }, 'last level'],
    ['not a level', { from: 'A1', to: 'A3', stations }, 'CEFR'],
    ['more right than asked', { from: 'A2', to: 'B1', stations: [{ ...stations[0], right: 6 }] }, 'at most the total'],
    ['a skill it does not know', { from: 'A2', to: 'B1', stations: [{ ...stations[0], skill: 'dancing' }] }, 'skill'],
    ['a station id with spaces', { from: 'A2', to: 'B1', stations: [{ ...stations[0], id: 'The Letter' }] }, 'station id'],
    ['no stations', { from: 'A2', to: 'B1', stations: [] }, 'stations'],
    ['nine stations', { from: 'A2', to: 'B1', stations: [...stations, ...stations] }, 'stations'],
  ]
  for (const [label, body, mention] of refused) {
    const res = await post(body)
    const e = await res.json()
    check(`POST /level refuses ${label} (400)`, res.status === 400 && String(e.error).includes(mention), e)
  }
  check('… and wrote nothing', (await state()).databases.session_log.sessions.length === sessions0 + 1)

  // --- another language ---------------------------------------------------------------------------------------------
  const it = await post({ from: 'A1', to: 'A2', language: 'it', stations: stations.slice(0, 2), duration_minutes: 12 })
  const ib = await it.json()
  const its = await state('?language=it')
  const home = await state()
  check(
    "POST /level in another language: its own level and level_since, the home language's as it was",
    it.ok && ib.language === 'it' && ib.level === 'A2' && its.databases.learner_profile.learner.current_level === 'A2' && its.databases.learner_profile.learner.level_since === its.computed.today &&
      home.databases.learner_profile.learner.current_level === 'A2' && home.languages.find((l: any) => l.code === 'it')?.level === 'A2' &&
      its.databases.session_log.sessions.at(-1)?.duration_minutes === 12,
    { ib, it: its.databases.learner_profile.learner, languages: home.languages },
  )
  await Bun.sleep(100)
  const itText: string = levelUps(heard)[1]?.params?.content ?? ''
  check('… Claude hears it, without the grammar book\'s pages', itText.includes('in Italian (language "it") is now A2') && !itText.includes('dajalnik'), itText)

  const guide = await guideOf()
  check('the channel guide says how level-ups happen: the treasure map, level_up already persisted', guide.includes('Zemljevid zaklada') && guide.includes('level_up') && guide.includes('Sem pripravljen'))
}
