// Smoke checks: the placement test's result (POST /placement, src/placement.ts; companion/COURSE.md "The placement
// test"): taken above the learner's level, persisted via update-db.py with the new level (current_level and level_since)
// as a session /lani-app-placement with a milestone, once per client_id, the tutor told (placement); kept their place, or
// taken at or below their level, a session and nothing else (never lowered); another language its own; a bad body
// refused, nothing written. Runs after the level-up's checks (the learner at A2 then).
import { auth, base, channelEvents, check } from './harness'

const probes = [
  { level: 'A2', part: 3, parts: 3, right: 8, almost: 0, of: 8, passed: true },
  { level: 'B1', part: 3, parts: 3, right: 2, almost: 1, of: 8, passed: false },
  { level: 'B1', part: 2, parts: 3, right: 5, almost: 2, of: 8, passed: false },
  { level: 'B1', part: 1, parts: 3, right: 7, almost: 1, of: 9, passed: true },
]
const post = (body: object) => fetch(`${base}/placement`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
const state = async (q = '') => (await fetch(`${base}/state${q}`, { headers: auth })).json()
const heardSince = (since: number) => channelEvents.slice(since).filter(n => n.params?.meta?.kind === 'placement')

export default async function placement() {
  const heard = channelEvents.length
  const s0 = await state()
  const sessions0 = s0.databases.session_log.sessions.length
  const was = s0.databases.learner_profile.learner.current_level
  check('placement: the learner is at A2 before (the level-up ran first)', was === 'A2', was)

  // --- taken, above the learner's level: the new level ---------------------------------------------------------------
  const taken = { from: 'a2', level: 'B1', applied: true, start: { level: 'B1', part: 2, parts: 3 }, units: 37, probes, duration_minutes: 11, client_id: 'smoke-placement-0001' }
  const r = await post(taken)
  const b = await r.json()
  check('POST /placement: taken above the level, persisted via update-db.py, the level raised', r.ok && typeof b.session_id === 'string' && b.level === 'B1' && b.raised === true, b)
  const s = await state()
  const lp = s.databases.learner_profile.learner
  check('… GET /state: the level is B1, since today', lp.current_level === 'B1' && lp.level_since === s.computed.today, lp)
  const entry = s.databases.session_log.sessions.find((x: any) => x.session_id === b.session_id)
  check(
    '… a session of its own: /lani-app-placement, its items as grammar, its minutes, what was asked',
    entry?.command_used === '/lani-app-placement' && entry.exercises_completed === 33 && entry.duration_minutes === 11 && entry.skills_practiced.includes('grammar') &&
      entry.notes.includes('from A2: A2 3/3 passed 8/8, B1 3/3 not 2.5/8') && entry.notes.includes('37 units marked done') && entry.notes.includes('start at B1 2/3'),
    entry,
  )
  check('… and a milestone', s.databases.session_log.milestones.some((m: any) => m.session_id === b.session_id && /^Placed at B1 in \w+: the placement test$/.test(m.milestone)), s.databases.session_log.milestones)
  await Bun.sleep(100)
  const note = heardSince(heard)
  const text: string = note[0]?.params?.content ?? ''
  check(
    'placement reaches Claude: already persisted, what was asked, where the school goes on, the level',
    note.length === 1 && note[0].params.meta.msg_id === b.session_id && text.includes('ALREADY persisted') && text.includes(b.session_id) && text.includes('A2 3/3 passed 8/8') &&
      text.includes('37 units done') && text.includes('start at B1 2/3') && text.includes('their level is now B1') && text.includes('get_course'),
    text,
  )

  // --- sent again: once ----------------------------------------------------------------------------------------------
  const replay = await post(taken)
  const rb = await replay.json()
  check('… once per client_id: a repeat gets the first answer', replay.headers.get('x-lani-replay') === '1' && rb.session_id === b.session_id)
  check('… nothing written twice', (await state()).databases.session_log.sessions.length === sessions0 + 1)

  // --- kept their place; taken below the level: a session, the level as it was ----------------------------------------
  const kept = await post({ from: 'B1', level: 'B2', applied: false, probes: probes.slice(0, 1), client_id: 'smoke-placement-0002' })
  const kb = await kept.json()
  const sk = await state()
  check('… kept their place: a session, the level as it was', kept.ok && kb.raised === false && kb.level === 'B1' && sk.databases.learner_profile.learner.current_level === 'B1' && sk.databases.learner_profile.learner.level_since === lp.level_since, kb)
  await Bun.sleep(100)
  const keptText: string = heardSince(heard)[1]?.params?.content ?? ''
  check('… Claude hears they kept their place, not to press it', keptText.includes('kept their place') && keptText.includes('stays B1') && keptText.includes("Don't press it"), keptText)
  const lower = await post({ from: 'B1', level: 'A2', applied: true, start: { level: 'A2', part: 3, parts: 3 }, units: 4, probes: probes.slice(0, 2), child: true, client_id: 'smoke-placement-0003' })
  const lb = await lower.json()
  const sl = await state()
  check('… taken at a level below theirs: never lowered', lower.ok && lb.raised === false && lb.level === 'B1' && sl.databases.learner_profile.learner.current_level === 'B1', lb)
  await Bun.sleep(100)
  const lowerText: string = heardSince(heard)[2]?.params?.content ?? ''
  check("… a child's version says so, and the level stays", lowerText.includes("the child's version") && lowerText.includes('their level stays B1') && lowerText.includes('4 units done'), lowerText)
  check('… two sessions more', sl.databases.session_log.sessions.length === sessions0 + 3 && sl.databases.session_log.sessions.at(-1)?.notes.includes("(a child's version)"))

  // --- refused --------------------------------------------------------------------------------------------------------
  const refused: [string, object, string][] = [
    ['not a level', { from: 'A1', level: 'A3', applied: true, probes }, 'CEFR'],
    ['no probes', { from: 'A1', level: 'A2', applied: true, probes: [] }, 'probes'],
    ['nine probes', { from: 'A1', level: 'A2', applied: true, probes: [...probes, ...probes, probes[0]] }, 'probes'],
    ['more right than asked', { from: 'A1', level: 'A2', applied: true, probes: [{ ...probes[0], right: 9 }] }, 'at most of'],
    ['a part past its parts', { from: 'A1', level: 'A2', applied: true, probes: [{ ...probes[0], part: 4 }] }, 'at most parts'],
    ['units marked though kept', { from: 'A1', level: 'A2', applied: false, units: 3, probes }, 'none marked'],
    ['no applied', { from: 'A1', level: 'A2', probes }, 'applied'],
  ]
  for (const [label, body, mention] of refused) {
    const res = await post(body)
    const e = await res.json()
    check(`POST /placement refuses ${label} (400)`, res.status === 400 && String(e.error).includes(mention), e)
  }
  check('… and wrote nothing', (await state()).databases.session_log.sessions.length === sessions0 + 3)

  // --- another language -----------------------------------------------------------------------------------------------
  const its0 = (await state('?language=it')).databases.learner_profile.learner.current_level
  const it = await post({ language: 'it', from: its0, level: 'B1', applied: true, start: { level: 'B1', part: 1, parts: 3 }, units: 36, probes: probes.slice(0, 1), duration_minutes: 9 })
  const ib = await it.json()
  const its = await state('?language=it')
  const home = await state()
  check(
    'POST /placement in another language: its own level and level_since, the home language\'s as it was',
    it.ok && ib.language === 'it' && ib.raised === true && its.databases.learner_profile.learner.current_level === 'B1' && its.databases.learner_profile.learner.level_since === its.computed.today &&
      home.databases.learner_profile.learner.current_level === 'B1' && its.databases.session_log.sessions.at(-1)?.duration_minutes === 9,
    { ib, it: its.databases.learner_profile.learner },
  )
  await Bun.sleep(100)
  const itText: string = heardSince(heard)[3]?.params?.content ?? ''
  check('… Claude hears it in Italian', itText.includes('in Italian (language "it")') && itText.includes('their level is now B1'), itText)
}
