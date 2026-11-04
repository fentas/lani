// Smoke checks: the learner's aim (src/aim.ts; companion/GAME.md "Ages"; docs/plans/05-built/target-next-age.md).
// - the shared cases of test/fixtures/target-next-age.json, as the app's AimsParityTest reads them: the words each age needs
//   on the way to a target, the target a step counts with, a raise waiting, a new learner's target;
// - the rules: a raise from the next age waits, a lower target applies at once, the current one again takes a raise back,
//   a raise at the town applies at once; the pending target applied exactly once, never before its age;
// - the bridge: set_target_level sets the pending target for a raise, PUT /game applies it once the village reaches the age
//   (and never before, nor twice), get_village's aim says the step now and the next one; the app's POST /learner/target
//   (the setup's and the settings' aim) by the same rule, at once without a village;
// - lani-profile: add's default B2 and --target-level, set-target with and without --from-next-age, on a synthetic child at
//   Zaselek: the age-up then counts B1's words.
import { existsSync, mkdirSync, readFileSync, renameSync, statSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { ageWords, aimAt, applyDue, applyPendingTarget, defaultTarget, describeAim, pendingOf, readProfile, setTarget, villageAge, type VillageAge } from '../../src/aim'
import { initLearnerData } from '../../src/learners'
import { localDate } from '../../src/rhythm'
import { auth, base, check, client, culturesDir, dataDir, tempDir } from './harness'

const text = (r: any) => (r.content ?? []).map((c: any) => c.text ?? '').join('\n')
const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v))

export default async function aim() {
  // --- the shared cases (the app's AimsParityTest reads the same file) -------------------------------------------------
  const fx = JSON.parse(readFileSync(resolve(import.meta.dir, '../fixtures/target-next-age.json'), 'utf8'))
  const table = Object.fromEntries(Object.keys(fx.age_words).map(t => [t, ageWords(t)]))
  check("aim: each age's words on the way to each target, as the app's Catalog.ageWords", JSON.stringify(table) === JSON.stringify(fx.age_words), table)
  const wrong = (fx.aims as any[]).filter(c => {
    const d = describeAim(c.learner, c.age)
    return aimAt(c.learner, c.age) !== c.target || d.target !== c.target || (d.words_for_next_age ?? null) !== c.words_next_age || (d.from_next_age?.level ?? null) !== c.waiting
  })
  check(`aim: the ${fx.aims.length} shared cases (the target a step counts with, the next age's words, a raise waiting)`, fx.aims.length >= 10 && wrong.length === 0, wrong.map(c => c.why))
  const defaults = Object.fromEntries(Object.keys(fx.defaults).map(l => [l, defaultTarget(l)]))
  check("aim: a new learner's target: B2, or the level above theirs from B2 on", JSON.stringify(defaults) === JSON.stringify(fx.defaults), defaults)

  // --- the rules ------------------------------------------------------------------------------------------------------
  const today = '2026-10-31'
  const teo = () => ({ current_level: 'A1', target_level: 'A2' }) as any
  {
    const l = teo()
    const c = setTarget(l, 'B1', 'ZASELEK', { fromNextAge: true, today })
    check(
      'aim: a raise from the next age waits for it: the target stays, target_level_next {B1, VAS, set}',
      c.mode === 'next_age' && c.from_age === 'VAS' && l.target_level === 'A2' && JSON.stringify(l.target_level_next) === JSON.stringify({ level: 'B1', from_age: 'VAS', set: today }),
      { c, l },
    )
    check('aim: … the step to Vas keeps 45 words until then, and Trg asks B1\'s 500 after', ageWords(aimAt(l, 'ZASELEK')).VAS === 45 && ageWords(aimAt(l, 'VAS')).TRG === 500)
    check('aim: … asked again, nothing changes', setTarget(l, 'B1', 'ZASELEK', { fromNextAge: true, today: '2026-11-01' }).mode === 'unchanged' && l.target_level_next.set === today)
    const before = clone(l)
    check('aim: … not applied before its age (Tabor, Zaselek)', !applyDue(l, 'TABOR', today) && !applyDue(l, 'ZASELEK', today) && JSON.stringify(l) === JSON.stringify(before), l)
    const applied = applyDue(l, 'VAS', '2026-11-02')
    check('aim: … applied at the age: target B1, since the day, the pending one gone', applied && l.target_level === 'B1' && l.target_level_since === '2026-11-02' && l.target_level_next === undefined, l)
    const after = clone(l)
    check('aim: … exactly once: applied again, nothing changes', !applyDue(l, 'VAS', '2026-11-03') && !applyDue(l, 'TRG', '2026-11-04') && JSON.stringify(l) === JSON.stringify(after), l)
  }
  {
    const l = teo()
    setTarget(l, 'B1', 'ZASELEK', { fromNextAge: true, today })
    const c = setTarget(l, 'A1', 'ZASELEK', { fromNextAge: true, today })
    check('aim: a lower target applies at once (even asked from the next age), the raise waiting dropped', c.mode === 'now' && c.why === 'lower' && l.target_level === 'A1' && l.target_level_next === undefined, { c, l })
  }
  {
    const l = teo()
    setTarget(l, 'B1', 'ZASELEK', { fromNextAge: true, today })
    const c = setTarget(l, 'A2', 'ZASELEK', { fromNextAge: true, today })
    check('aim: the current target again takes a waiting raise back', c.mode === 'now' && c.why === 'same' && l.target_level === 'A2' && l.target_level_next === undefined, { c, l })
  }
  {
    const l = teo()
    const c = setTarget(l, 'B1', 'ZASELEK', { fromNextAge: false, today })
    check('aim: a raise without --from-next-age applies at once', c.mode === 'now' && c.why === 'asked' && l.target_level === 'B1' && l.target_level_since === today && l.target_level_next === undefined, { c, l })
    const town = teo()
    const t = setTarget(town, 'B1', 'MESTO', { fromNextAge: true, today })
    check('aim: a raise at the town (no next age) applies at once', t.mode === 'now' && t.why === 'last-age' && town.target_level === 'B1', t)
  }
  {
    const l = { target_level: 'A2', target_level_next: 'B1' } as any
    check('aim: a pending target that can\'t be read is no raise, and is dropped where it is seen', pendingOf(l) === undefined && applyDue(l, 'ZASELEK', today) && l.target_level === 'A2' && !('target_level_next' in l), l)
  }

  // --- the bridge: the tool, the village's save, get_village ------------------------------------------------------------
  const profilePath = join(dataDir, 'learner-profile.json')
  const gamePath = join(dataDir, 'app/game.json')
  const savedProfile = readFileSync(profilePath, 'utf8')
  const savedGame = existsSync(gamePath) ? readFileSync(gamePath, 'utf8') : undefined
  const learnerNow = () => JSON.parse(readFileSync(profilePath, 'utf8')).learner
  const setLearner = (l: Record<string, unknown>) => {
    const p = JSON.parse(savedProfile)
    writeFileSync(profilePath, JSON.stringify({ ...p, learner: { ...p.learner, ...l } }, null, 2))
  }
  const village = async () => JSON.parse(text(await client.callTool({ name: 'get_village', arguments: {} })))
  let rev = (await (await fetch(`${base}/game`, { headers: auth })).json())?.rev ?? 0
  const save = async (age: VillageAge) => {
    const r = await fetch(`${base}/game`, { method: 'PUT', headers: auth, body: JSON.stringify({ rev, state: { age, buildings: [], log: [] } }) })
    if (r.ok) rev = (await r.json()).rev
    return r.ok
  }
  try {
    setLearner({ current_level: 'A1', target_level: 'A2' })
    const atHamlet = await save('ZASELEK')
    const set = text(await client.callTool({ name: 'set_target_level', arguments: { level: 'B1' } }))
    const l1 = learnerNow()
    check(
      'set_target_level B1 at Zaselek: the pending target, not the target (the step to Vas keeps 45 words, Trg then 500)',
      atHamlet && l1.target_level === 'A2' && l1.target_level_next?.level === 'B1' && l1.target_level_next?.from_age === 'VAS' && set.includes('keeps 45 words') && set.includes('Trg needs 500 words'),
      { set, l1 },
    )
    const v1 = (await village()).aim
    check(
      "get_village's aim: A2 for the step to Vas (45 words), B1 from Vas on (Trg 500)",
      v1?.target === 'A2' && v1.next_age === 'VAS' && v1.words_for_next_age === 45 && v1.from_next_age?.level === 'B1' && v1.from_next_age.from_age === 'VAS' && v1.from_next_age.then_age === 'TRG' && v1.from_next_age.then_words === 500,
      v1,
    )
    const again = await save('ZASELEK')
    check('PUT /game at Zaselek again: the raise still waits (never before its age)', again && learnerNow().target_level === 'A2' && learnerNow().target_level_next?.level === 'B1', learnerNow())
    const up = await save('VAS')
    const l2 = learnerNow()
    check('PUT /game at Vas (the age-up): the target is B1 now, since today, the pending one gone', up && l2.target_level === 'B1' && l2.target_level_since === localDate(new Date()) && l2.target_level_next === undefined, l2)
    const v2 = (await village()).aim
    check("get_village's aim after the age-up: B1, Trg needs 500, nothing waits", v2?.target === 'B1' && v2.next_age === 'TRG' && v2.words_for_next_age === 500 && v2.from_next_age === undefined, v2)
    const stamp = statSync(profilePath).mtimeMs
    const kept = readFileSync(profilePath, 'utf8')
    await save('VAS')
    await save('TRG')
    check('PUT /game again (Vas, then Trg): applied exactly once, the profile not written again', readFileSync(profilePath, 'utf8') === kept && statSync(profilePath).mtimeMs === stamp)
    const lower = text(await client.callTool({ name: 'set_target_level', arguments: { level: 'A2' } }))
    check('set_target_level A2 (lower): at once', learnerNow().target_level === 'A2' && learnerNow().target_level_next === undefined && lower.includes('is A2 now'), lower)
    const bad = await client.callTool({ name: 'set_target_level', arguments: { level: 'D1' } })
    check('set_target_level D1: refused, the levels named', bad.isError && text(bad).includes('A1, A2, B1, B2, C1, C2'), bad)

    // the app's "🎯 Cilj · Aim" (the setup's and the settings'): POST /learner/target by the same rule
    const target = (level: string) => fetch(`${base}/learner/target`, { method: 'POST', headers: auth, body: JSON.stringify({ level }) }).then(r => r.json())
    setLearner({ current_level: 'A1', target_level: 'A2' })
    await save('ZASELEK')
    const raised = await target('B1')
    const got = await (await fetch(`${base}/learner/target`, { headers: auth })).json()
    check(
      'POST /learner/target B1 with a village at Zaselek: waits for the next age (target A2, next B1 from Vas); GET says so',
      raised.change === 'next_age' && raised.target === 'A2' && raised.next?.level === 'B1' && raised.next?.from_age === 'VAS' && got.target === 'A2' && got.next?.level === 'B1' && learnerNow().target_level_next?.from_age === 'VAS',
      { raised, got },
    )
    const same = await target('B1')
    check('… asked again: already, nothing changed', same.already === true && same.change === 'unchanged' && same.next?.level === 'B1', same)
    const back = await target('A2')
    // what the first run left at its defaults (docs/plans/05-built/first-minutes.md): POST /learner/about
    const about = (b: object) => fetch(`${base}/learner/about`, { method: 'POST', headers: auth, body: JSON.stringify(b) })
    const aboutBefore = { gender: learnerNow().gender, goal: learnerNow().daily_goal_minutes }
    const said = await (await about({ gender: 'female', goal: 20 })).json()
    check('POST /learner/about: the gender and the minutes kept at once', said.gender === 'female' && said.goal === 20 && learnerNow().gender === 'female' && learnerNow().daily_goal_minutes === 20, said)
    check('GET /learner/about says them', (await (await fetch(`${base}/learner/about`, { headers: auth })).json()).goal === 20)
    check('POST /learner/about: nothing to keep, a bad gender, a goal out of range refused', (await about({})).status === 400 && (await about({ gender: 'x' })).status === 400 && (await about({ goal: 1 })).status === 400)
    await about({ goal: aboutBefore.goal ?? 30, ...(aboutBefore.gender === 'male' ? { gender: 'male' } : {}) })
    check('POST /learner/target A2 (the current aim): the raise taken back, at once', back.change === 'now' && back.target === 'A2' && back.next === null && learnerNow().target_level_next === undefined, back)
    setLearner({ current_level: 'A1', target_level: 'B2', target_level_next: { level: 'C1', from_age: 'VAS' } })
    const lowered = await target('A2')
    check('POST /learner/target A2 for a learner aiming at B2 (C1 waiting): lower, at once, the raise dropped', lowered.change === 'now' && lowered.target === 'A2' && lowered.next === null, lowered)
    // no village yet (the setup's case): a raise at once
    renameSync(gamePath, `${gamePath}.away`)
    try {
      const atSetup = await target('B2')
      check('POST /learner/target B2 without a village (the setup): at once', atSetup.change === 'now' && atSetup.target === 'B2' && atSetup.next === null && learnerNow().target_level === 'B2', atSetup)
    } finally {
      renameSync(`${gamePath}.away`, gamePath)
    }
  } finally {
    writeFileSync(profilePath, savedProfile)
    if (savedGame !== undefined) writeFileSync(gamePath, savedGame)
  }

  // --- a new learner's target -------------------------------------------------------------------------------------------
  const project = resolve(import.meta.dir, '../../../..')
  const made = (level: string, targetLevel?: string) => {
    const d = tempDir('lani-smoke-aim-learner-')
    initLearnerData(project, join(d, 'data'), { name: 'Ana', target: 'sl', base: 'en', level, ...(targetLevel ? { targetLevel } : {}) })
    return readProfile(join(d, 'data')).learner.target_level
  }
  const fresh = { A1: made('A1'), A2: made('A2'), B2: made('B2'), C2: made('C2'), chosen: made('A1', 'b1'), odd: made('A1', 'Z9') }
  check("a new learner's target: B2 from A1 and A2, C1 from B2, C2 from C2; a chosen one kept; one that isn't a level: the default", JSON.stringify(fresh) === JSON.stringify({ A1: 'B2', A2: 'B2', B2: 'C1', C2: 'C2', chosen: 'B1', odd: 'B2' }), fresh)

  // --- lani-profile: add, set-target, on a synthetic child at Zaselek -------------------------------------------------
  const cli = resolve(import.meta.dir, '../../src/cli/profile.ts')
  const profiles = tempDir('lani-smoke-aim-profiles-')
  const env = { ...process.env, LANI_PROFILES_DIR: profiles, LANI_CULTURES_DIR: culturesDir, LANI_TAILNET_HOST: 'smoke.example.ts.net', LANI_KEYS_FILE: '/nonexistent', LANI_LEXICON_URL: '/srv/lexicon' } as Record<string, string>
  const run = (...args: string[]) => {
    const r = Bun.spawnSync(['bun', cli, ...args], { env })
    return { code: r.exitCode, out: `${r.stdout}${r.stderr}` }
  }
  const added = run('add', 'teo', '--name', 'Teo', '--target', 'it', '--base', 'de', '--child', '--gender', 'male', '--port', '18961', '--https-port', '19961')
  const teoData = join(profiles, 'teo/data')
  check('lani-profile add (a child, A1): aims at B2, and says so', added.code === 0 && readProfile(teoData).learner.target_level === 'B2' && added.out.includes('level A1, aims at B2'), added.out)
  const chose = run('add', 'mia', '--name', 'Mia', '--target', 'it', '--base', 'sl', '--gender', 'f', '--target-level', 'a2', '--port', '18962', '--https-port', '19962')
  const odd = run('add', 'zed', '--name', 'Zed', '--target', 'it', '--base', 'sl', '--target-level', 'B3', '--port', '18963', '--https-port', '19963')
  const below = run('add', 'ida', '--name', 'Ida', '--target', 'it', '--base', 'sl', '--level', 'B1', '--target-level', 'A2', '--port', '18964', '--https-port', '19964')
  check(
    'lani-profile add --target-level A2: kept; B3, and A2 for a learner at B1, refused',
    chose.code === 0 && readProfile(join(profiles, 'mia/data')).learner.target_level === 'A2' && odd.code !== 0 && odd.out.includes('--target-level: A1, A2, B1, B2, C1 or C2') && below.code !== 0 && below.out.includes('above the level (B1'),
    { chose: chose.out, odd: odd.out, below: below.out },
  )
  // Teo's village at Zaselek, aiming at A2 (setup's old default)
  mkdirSync(join(teoData, 'app'), { recursive: true })
  writeFileSync(join(teoData, 'app/game.json'), JSON.stringify({ rev: 3, state: { age: 'ZASELEK', buildings: [] } }))
  const toA2 = run('set-target', 'teo', 'A2')
  check('lani-profile set-target teo A2 (lower than B2): at once, says what the step to Vas needs', toA2.code === 0 && readProfile(teoData).learner.target_level === 'A2' && toA2.out.includes('The target is A2 now (it was B2') && toA2.out.includes('45 words for Vas'), toA2.out)
  const toB1 = run('set-target', 'teo', 'B1', '--from-next-age')
  const t1 = readProfile(teoData).learner
  check(
    'lani-profile set-target teo B1 --from-next-age: pending from Vas; the step to Vas keeps 45 words, Trg then 500 (it needed 150)',
    toB1.code === 0 && t1.target_level === 'A2' && t1.target_level_next?.level === 'B1' && t1.target_level_next?.from_age === 'VAS' &&
      toB1.out.includes('keeps 45 words') && toB1.out.includes('Trg needs 500 words (it needed 150)') && toB1.out.includes('target_level_next {"level":"B1","from_age":"VAS"}'),
    toB1.out,
  )
  // the age-up, as the bridge's save of the village applies it
  writeFileSync(join(teoData, 'app/game.json'), JSON.stringify({ rev: 4, state: { age: 'VAS', buildings: [] } }))
  const applied = applyPendingTarget(teoData, villageAge(teoData), '2026-11-02')
  const t2 = readProfile(teoData).learner
  const next = describeAim(t2, 'VAS')
  check('the synthetic child at Vas: target B1, the next step (Trg) asks B1\'s 500 words', applied === 'B1' && t2.target_level === 'B1' && t2.target_level_next === undefined && next.next_age === 'TRG' && next.words_for_next_age === 500, { t2, next })
  check('… and again: nothing (exactly once)', applyPendingTarget(teoData, 'VAS', '2026-11-03') === undefined && readProfile(teoData).learner.target_level_since === '2026-11-02')
  const usage = run('set-target', 'teo')
  const nope = run('set-target', 'nobody', 'B1')
  check('lani-profile set-target: usage without a level; an unknown profile refused', usage.code !== 0 && usage.out.includes('usage: lani-profile set-target') && nope.code !== 0 && nope.out.includes('no profile "nobody"'), { usage: usage.out, nope: nope.out })
  // the lease: the phone holds the data, the node doesn't write
  writeFileSync(join(teoData, 'app/lease.json'), JSON.stringify({ schema: 'lani.lease/v1', holder: { side: 'phone', device: 'tablet', name: 'tablet', mode: 'hosted' }, offer: null, since: '2026-10-30T10:00:00Z', renewed: null, expires: null }))
  const leased = run('set-target', 'teo', 'C1', '--from-next-age')
  check("lani-profile set-target while the phone holds the data: refused, nothing written", leased.code !== 0 && leased.out.includes("isn't this node's to write") && readProfile(teoData).learner.target_level === 'B1', leased.out)
}
