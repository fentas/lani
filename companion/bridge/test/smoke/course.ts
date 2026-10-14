// Smoke checks: the school's course (companion/COURSE.md: lani.course/v0, lani.unit/v0, lani.worksheet/v0). Every course
// file of the repository; the format's refusals; the fixture course (course-fixture.ts) served; the tutor's homework
// (publish_worksheet, remove_worksheet); where the learner is (get_course, get_village) from the village state, what they
// looked up on the cheat sheet (sheet.lookups); the guide.
import { existsSync, mkdirSync, readdirSync, readFileSync, unlinkSync, utimesSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { sheetLookups } from '../../src/cards'
import { CourseStore, grammarNamed, sheetExercise, validateCourse, validateUnit, type Course } from '../../src/course'
import { validateGrammar } from '../../src/grammar'
import { localDate } from '../../src/rhythm'
import { auth, base, check, client, dataDir, guideOf, tempDir } from './harness'
import { gaptext, outline, sort, table, transform, unit } from './course-fixture'

const repo = resolve(import.meta.dir, '../../..')
const text = (r: any): string => r?.content?.[0]?.text ?? ''
const call = (name: string, args: object = {}) => client.callTool({ name, arguments: args }) as Promise<any>
const errors = (v: { ok: boolean }) => (v.ok ? '' : (v as { errors: string }).errors)

/** The fixture unit with its second worksheet's exercises as [exercises]. */
const withSheet = (...exercises: object[]) => ({ ...unit, worksheets: [unit.worksheets[0], { ...unit.worksheets[1], exercises }] })

export default async function course() {
  // --- the repository's course: every language's outline and units, what they name -------------------------------
  const root = join(repo, 'course')
  const langs = existsSync(root) ? readdirSync(root).filter(d => existsSync(join(root, d, 'course.json')) || existsSync(join(root, d, 'units'))) : []
  for (const lang of langs) {
    const dir = join(root, lang)
    const o = existsSync(join(dir, 'course.json')) ? validateCourse(JSON.parse(readFileSync(join(dir, 'course.json'), 'utf8'))) : undefined
    check(`course/${lang}: course.json validates and is of ${lang}`, !!o?.ok && o.course.language === lang, o && errors(o))
    const files = existsSync(join(dir, 'units')) ? readdirSync(join(dir, 'units')).filter(f => f.endsWith('.json')).sort() : []
    const read = files.map(f => ({ f, v: validateUnit(JSON.parse(readFileSync(join(dir, 'units', f), 'utf8'))) }))
    check(`course/${lang}: every unit validates, is of ${lang}, in the file of its id`, read.every(r => r.v.ok && r.v.unit.language === lang && `${r.v.unit.id}.json` === r.f), read.filter(r => !r.v.ok || r.v.unit.language !== lang || `${r.v.unit.id}.json` !== r.f).map(r => [r.f, errors(r.v) || (r.v.ok && [r.v.unit.id, r.v.unit.language])]))
    const units = read.flatMap(r => (r.v.ok ? [r.v.unit] : []))
    const levels: Course['levels'] = o?.ok ? o.course.levels : []
    const levelOf = new Map(levels.flatMap(l => l.units.map(u => [u.id, l.level] as const)))
    check(`course/${lang}: each unit is of its outline's level`, units.every(u => !levelOf.has(u.id) || levelOf.get(u.id) === u.level), units.filter(u => levelOf.has(u.id) && levelOf.get(u.id) !== u.level).map(u => [u.id, u.level, levelOf.get(u.id)]))
    const ids = new Set(units.map(u => u.id))
    const missing = levels.flatMap(l => l.units.filter(u => !u.planned && !ids.has(u.id)).map(u => `${l.level}/${u.id}`))
    check(`course/${lang}: every unit of the outline that isn't planned has its file`, missing.length === 0, missing)
    const gdir = join(repo, 'grammar', lang)
    const pages = new Set(existsSync(gdir) ? readdirSync(gdir).filter(f => f.endsWith('.json')).flatMap(f => {
      const v = validateGrammar(JSON.parse(readFileSync(join(gdir, f), 'utf8')))
      return v.ok ? [v.page.id] : []
    }) : [])
    const lacking = [...units.flatMap(u => grammarNamed(u).filter(g => !pages.has(g)).map(g => `${u.id}: ${g}`)), ...levels.flatMap(l => l.units.flatMap(u => u.grammar.filter(g => !pages.has(g)).map(g => `outline ${u.id}: ${g}`)))]
    check(`course/${lang}: every page a unit (or the outline) names is a page of the ${lang} grammar book`, lacking.length === 0, lacking)
    check(`course/${lang}: each unit is machine-written and says who checked it`, units.every(u => u.machine_written && u.review?.includes('not reviewed by a native speaker')), units.filter(u => !u.review).map(u => u.id))
  }
  check(`the repository's course languages checked: ${langs.join(', ') || 'none yet'}`, true)

  // --- the format ----------------------------------------------------------------------------------------------------
  const fixture = validateUnit(unit)
  check('the fixture unit validates: all four sheet types, an intro with rows and questions, two worksheets, a test', fixture.ok, errors(fixture))
  check('the fixture outline validates', validateCourse(outline).ok, errors(validateCourse(outline)))
  const refused: [string, unknown, string?][] = [
    ['a gap text whose gaps are fewer than its ___', withSheet({ ...gaptext, gaps: gaptext.gaps.slice(0, 2) }), '2 gaps for the 3 ___'],
    ['distractors without a word bank', withSheet({ ...gaptext, bank: false }), 'only with a word bank'],
    ["a distractor that is a gap's answer", withSheet({ ...gaptext, distractors: ['Je'] }), 'is a right answer of a gap'],
    ['a gap text with one gap', withSheet({ ...gaptext, text: 'Danes ___ doma.', gaps: [gaptext.gaps[0]] })],
    ['a table row shorter than its columns', withSheet({ ...table, rows: [...table.rows, { label: '—', cells: ['x'] }] }), '1 cells, the table has 2 columns'],
    ['a table with nothing to fill', withSheet({ ...table, rows: [{ label: '1. oseba', cells: ['sem', 'smo'] }] }), 'nothing to fill'],
    ["a cell's form that isn't a forms book key", withSheet({ ...table, rows: [{ label: '1. oseba', cells: ['sem', { accept: ['smo'], form: 'first plural' }] }] }), 'forms book key'],
    ['a sort with a word twice', withSheet({ ...sort, columns: [sort.columns[0], { ...sort.columns[1], words: ['smo', 'SEM'] }] }), 'a word twice: sem'],
    ['a sort with 3 words', withSheet({ ...sort, columns: [{ label: 'a', words: ['sem', 'si'] }, { label: 'b', words: ['smo'] }] }), '3 words'],
    ['a transform without its instruction', withSheet({ ...transform, instruction: undefined })],
    ['an unknown key (strict)', withSheet({ ...transform, colour: 'red' }), 'Unrecognized key'],
    ['an intro row longer than the head', { ...unit, intro: { ...unit.intro, steps: [{ ...unit.intro.steps[0], rows: [['jaz', 'sem', 'extra']] }] } }, '3 cells, the head has 2'],
    ['intro rows without a table', { ...unit, intro: { steps: unit.intro.steps } }, 'rows need the table'],
    ["an intro example without the course's language", { ...unit, intro: { ...unit.intro, steps: [{ ...unit.intro.steps[0], examples: [{ en: 'I am.' }] }] } }, 'course\'s language ("sl")'],
    ['a unit with one worksheet', { ...unit, worksheets: [unit.worksheets[0]] }],
    ['a test with pass 40', { ...unit, test: { ...unit.test, pass: 40 } }],
    ['a worksheet called "test"', { ...unit, worksheets: [unit.worksheets[0], { ...unit.worksheets[1], id: 'test' }] }, 'the unit\'s test'],
    ['a worksheet id twice', { ...unit, worksheets: [unit.worksheets[0], { ...unit.worksheets[1], id: 'biti-1' }] }, 'a worksheet id twice'],
    ['a unit without a grammar page', { ...unit, grammar: [] }],
  ]
  for (const [what, input, says] of refused) {
    const v = validateUnit(input)
    check(`the format refuses ${what}`, !v.ok && (!says || errors(v).includes(says)), v.ok ? 'accepted' : errors(v))
  }
  check('an outline with its levels out of order is refused', !validateCourse({ ...outline, levels: [outline.levels[1], outline.levels[0]] }).ok)
  check('an outline with a unit twice is refused', !validateCourse({ ...outline, levels: [outline.levels[0], { level: 'A2', units: [outline.levels[0].units[0]] }] }).ok)

  // the lani-studio reference: its examples of the four types validate
  const reference = readFileSync(resolve(repo, '../.claude/skills/lani-studio/reference/module-spec.md'), 'utf8')
  const examples = [...reference.matchAll(/```json\n(\{\n  "type": "(?:gaptext|table|transform|sort)"[\s\S]*?\n\})\n```/g)].map(m => JSON.parse(m[1]))
  check("module-spec.md's examples of the school's four types validate", examples.length === 4 && examples.every(e => sheetExercise.safeParse(e).success), examples.map(e => [e.type, sheetExercise.safeParse(e).success]))

  // --- the store: a unit the outline lacks after its level's, a broken file skipped, a unit of the wrong level --------
  {
    const tmp = tempDir('lani-smoke-course-store-')
    mkdirSync(join(tmp, 'sl/units'), { recursive: true })
    writeFileSync(join(tmp, 'sl/course.json'), JSON.stringify(outline))
    writeFileSync(join(tmp, 'sl/units/biti.json'), JSON.stringify(unit))
    writeFileSync(join(tmp, 'sl/units/extra.json'), JSON.stringify({ ...unit, id: 'extra', level: 'B1' }))
    writeFileSync(join(tmp, 'sl/units/broken.json'), '{nope')
    writeFileSync(join(tmp, 'sl/units/osebni-zaimki.json'), JSON.stringify({ ...unit, id: 'osebni-zaimki', level: 'A2' }))
    const logged: string[] = []
    const store = new CourseStore(join(tmp, 'sl'), 'sl', join(tmp, 'tutor'), (...a) => logged.push(a.join(' ')))
    const s = store.served()
    check(
      "the store: a unit file the outline lacks comes after its level's units; one of another level than the outline's and a broken one are skipped, logged",
      s?.levels.find(l => l.level === 'B1')?.units.map(u => u.id).join() === 'extra' && s.units.map(u => u.id).join() === 'biti,extra' && s.levels[0].units[1].planned === true && logged.some(l => l.includes('broken.json')) && logged.some(l => l.includes('the outline has it in A1')),
      { levels: s?.levels, logged },
    )
    store.served()
    check('… each logged once', logged.filter(l => l.includes('broken.json')).length === 1, logged)
    check('a language without a course has none', new CourseStore(join(tmp, 'it'), 'it', join(tmp, 'tutor-it')).served() === undefined)
  }

  // --- served --------------------------------------------------------------------------------------------------------
  const got = await fetch(`${base}/course`, { headers: auth })
  const served = await got.json()
  check(
    'GET /course serves the course: its levels in order, the written unit, the planned one marked',
    got.ok && served.schema === 'lani.course/v0' && served.language === 'sl' && served.levels.map((l: any) => l.level).join() === 'A1,A2,B1,B2' &&
      JSON.stringify(served.levels[0].units.map((u: any) => [u.id, u.planned])) === '[["biti",false],["osebni-zaimki",true]]' &&
      served.units.length === 1 && served.units[0].id === 'biti' && JSON.stringify(served.units[0].homework) === '[]' && served.machine_written === true,
    served,
  )
  const b = served.units?.[0]
  check(
    'GET /course: the unit as written, rendered for the learner ({learner} → Jan)',
    b?.intro.steps[0].examples[0].sl === 'Jaz sem Jan.' && b.intro.steps[0].ask.answer === 2 && b.worksheets.map((w: any) => w.exercises.map((e: any) => e.type).join('+')).join() === 'sort+table,gaptext+transform' && b.test.pass === 80 && b.worksheets[1].exercises[0].bank === true,
    b,
  )
  check('GET /course needs the token', (await fetch(`${base}/course`)).status === 401)

  // --- the tutor's homework ------------------------------------------------------------------------------------------
  const homework = {
    schema: 'lani.worksheet/v0',
    unit: 'biti',
    id: 'biti-domaca-1',
    title: { sl: 'Domača naloga: biti', en: 'Homework: biti' },
    intro: { steps: [{ say: { en: 'Once more: **je** for one person, **so** for three or more.' }, examples: [{ sl: 'Luka je na vrtu.', en: 'Luka is in the garden.' }] }] },
    exercises: [
      { ...gaptext, gaps: [gaptext.gaps[0], { ...gaptext.gaps[1], grammar: 'no-such-rule' }, gaptext.gaps[2]] },
      { type: 'cloze', grammar: 'biti', text: '{learner} ___ v šoli.', accept: ['je'] },
    ],
  }
  const pub = await call('publish_worksheet', { worksheet: homework, note: 'Homework on biti' })
  check('publish_worksheet publishes homework into a unit, the language taken from the village', !pub.isError && text(pub).startsWith('published homework biti/biti-domaca-1 (2 exercises)'), pub)
  check('publish_worksheet says which page the book lacks, not the ones it has', text(pub).includes('no grammar page "no-such-rule"') && !text(pub).includes('"biti"'), text(pub))
  check("the homework is written to <data>/app/course/<unit>/<id>.json", existsSync(join(dataDir, 'app/course/biti/biti-domaca-1.json')))
  const withHw = await (await fetch(`${base}/course`, { headers: auth })).json()
  const hw = withHw.units?.[0]?.homework?.[0]
  check("GET /course serves the homework under its unit, the tutor's, machine-written, rendered", hw?.id === 'biti-domaca-1' && hw.source === 'tutor' && typeof hw.published_at === 'number' && hw.machine_written === true && hw.exercises[1].text === 'Jan ___ v šoli.', hw)
  await Bun.sleep(100)
  const backlog = (await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as any[]
  const published = backlog.find(e => e.event.type === 'worksheet_published' && e.event.id === 'biti-domaca-1')?.event
  check("worksheet_published event: the unit, the id, the title in the learner's pair, the note", published?.unit === 'biti' && published.title === 'Domača naloga: biti · Homework: biti' && published.note === 'Homework on biti', published)
  const again = await call('publish_worksheet', { worksheet: { ...hw } })
  check('publishing the served homework again replaces it', !again.isError && text(again).startsWith('replaced homework biti/biti-domaca-1'), again)
  // a second sheet: the newest first
  const second = await call('publish_worksheet', { worksheet: { ...homework, id: 'biti-domaca-2' } })
  const later = join(dataDir, 'app/course/biti/biti-domaca-2.json')
  utimesSync(later, new Date(), new Date(Date.now() + 5_000))
  const both = (await (await fetch(`${base}/course`, { headers: auth })).json()).units[0].homework.map((h: any) => h.id)
  check("a unit's homework is served the newest first", !second.isError && both.join() === 'biti-domaca-2,biti-domaca-1', both)

  const refusals: [string, object, string][] = [
    ['a unit the course lacks', { ...homework, unit: 'no-such-unit' }, 'no unit "no-such-unit"'],
    ['a planned unit', { ...homework, unit: 'osebni-zaimki' }, 'is planned'],
    ["an id of the unit's own worksheet", { ...homework, id: 'biti-1' }, 'is a worksheet of the unit'],
    ['an invalid sheet', { ...homework, exercises: [] }, 'worksheet invalid'],
    ['homework of another language', { ...homework, language: 'it', title: { it: 'Compiti', en: 'Homework' } }, '"it"'],
    ['homework called "test"', { ...homework, id: 'test' }, "the unit's test"],
  ]
  for (const [what, ws, says] of refusals) {
    const r = await call('publish_worksheet', { worksheet: ws })
    check(`publish_worksheet refuses ${what}`, r.isError && text(r).includes(says), text(r))
  }
  check('the refused sheets are not written', readdirSync(join(dataDir, 'app/course/biti')).sort().join() === 'biti-domaca-1.json,biti-domaca-2.json', readdirSync(join(dataDir, 'app/course/biti')))

  const removed = await call('remove_worksheet', { unit: 'biti', id: 'biti-domaca-2' })
  const left = (await (await fetch(`${base}/course`, { headers: auth })).json()).units[0].homework.map((h: any) => h.id)
  check('remove_worksheet removes the homework', !removed.isError && left.join() === 'biti-domaca-1' && !existsSync(later), [text(removed), left])
  const removedEvent = ((await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as any[]).find(e => e.event.type === 'worksheet_removed')?.event
  check('worksheet_removed event: the unit and the id', removedEvent?.unit === 'biti' && removedEvent.id === 'biti-domaca-2', removedEvent)
  check('remove_worksheet of homework there is not is an error', (await call('remove_worksheet', { unit: 'biti', id: 'biti-domaca-2' })).isError)

  // --- where the learner is: the village state's course ----------------------------------------------------------------
  const gamePath = join(dataDir, 'app/game.json')
  const existed = existsSync(gamePath)
  const saved = existed ? readFileSync(gamePath, 'utf8') : ''
  const put = async (state: object) => {
    const rev = existsSync(gamePath) ? JSON.parse(readFileSync(gamePath, 'utf8')).rev : 0
    return fetch(`${base}/game`, { method: 'PUT', headers: auth, body: JSON.stringify({ rev, state }) })
  }
  const school = { id: 'b-school', type: 'SCHOOL', plot: 4, level: 2 }
  const started = {
    v: 1,
    classroom: '2026-10-01',
    opened: { 'sl/A1': '2026-10-01' },
    units: { 'sl/biti': { started: '2026-10-01', intro: 2, introDone: true, sheets: { 'biti-1': { right: 9, almost: 1, of: 10, day: '2026-10-02', tries: 1 } }, testedOut: false } },
  }
  const first = await put({ age: 'VAS', buildings: [school], course: started })
  const now = JSON.parse(text(await call('get_course')))
  const a1 = now.levels?.[0]
  const biti = a1?.units?.[0]
  check(
    'get_course: the classroom, the school, an open level, the unit in progress and next',
    first.ok && now.language === 'sl' && now.classroom === '2026-10-01' && now.school === 2 && a1.open === true && a1.opened === '2026-10-01' && a1.units_done === '0/1' &&
      biti.id === 'biti' && biti.title === 'To be: sem, si, je' && biti.done === null && biti.intro === '2/2' && biti.intro_done === true && biti.sheets === '1/2' &&
      biti.best?.['biti-1'] === '9/10, 1 almost' && biti.test.pass === 80 && biti.test.tries === 0 && JSON.stringify(biti.homework) === '["biti-domaca-1"]' &&
      JSON.stringify(a1.units[1]) === '{"id":"osebni-zaimki","title":"I, you, he, she","planned":true}' && now.levels[1].open === false &&
      JSON.stringify(now.next) === '{"level":"A1","id":"biti","title":"To be: sem, si, je"}',
    now,
  )
  const inProgress = JSON.parse(text(await call('get_village'))).course
  check('get_village: the course block with the unit in progress', JSON.stringify(inProgress) === '{"classroom":"2026-10-01","done":[],"in_progress":["biti"]}', inProgress)

  const doneState = { ...started, units: { 'sl/biti': { ...started.units['sl/biti'], test: { right: 9, almost: 0, of: 10, day: '2026-10-03', tries: 2 }, done: '2026-10-03' } } }
  await put({ age: 'VAS', buildings: [school], course: doneState })
  const after = JSON.parse(text(await call('get_course')))
  check('get_course: the unit done, its test, the level done, nothing next', after.levels[0].units_done === '1/1' && after.levels[0].units[0].done === '2026-10-03' && after.levels[0].units[0].test.best === '9/10' && after.levels[0].units[0].test.tries === 2 && after.next === null, after)
  const doneSummary = JSON.parse(text(await call('get_village'))).course
  check('get_village: the unit done', JSON.stringify(doneSummary) === '{"classroom":"2026-10-01","done":["biti"],"in_progress":[]}', doneSummary)
  check('get_course and get_village without lookups: no looked_up', !('looked_up' in after) && !('looked_up' in JSON.parse(text(await call('get_village')))))

  // what Jan looked up on the cheat sheet during exercises (the village state's sheet.lookups: per page, each day's answers
  // given with one of its cards open; the last 14 days count): get_course names the units that teach each page
  const today = localDate(new Date())
  const day = (d: number) => new Date(Date.parse(today) + d * 86_400_000).toISOString().slice(0, 10)
  const lookups = {
    // 14 days back and a day ahead left out, a day that isn't one and a count that isn't one too
    'sl/biti': { [day(0)]: 2, [day(-3)]: 1, [day(-13)]: 1, [day(-14)]: 9, [day(1)]: 5, yesterday: 3, [day(-1)]: -2 },
    'sl/zanikanje': { [day(-1)]: 'x', [day(-2)]: 3, [day(-4)]: 1.5 },
    'sl/osebni-zaimki': { [day(-5)]: 1 },
    // only days too old: left out; another language's; a key that isn't a page; not days
    'sl/rodilnik': { [day(-20)]: 4 },
    'it/essere': { [day(0)]: 7 },
    'sl/Not A Page': { [day(0)]: 4 },
    'sl/tozilnik': 'often',
  }
  const want = [{ page: 'biti', n: 4, last: today }, { page: 'zanikanje', n: 3, last: day(-2) }, { page: 'osebni-zaimki', n: 1, last: day(-5) }]
  check('sheetLookups: the last 14 days up to today, most first; older, later and malformed days and counts left out; a village from before: none',
    JSON.stringify(sheetLookups({ lookups }, 'sl', today)) === JSON.stringify(want) && sheetLookups(undefined, 'sl', today).length === 0 && sheetLookups({ lookups: [1] }, 'sl', today).length === 0 &&
      JSON.stringify(sheetLookups({ lookups }, 'it', today)) === JSON.stringify([{ page: 'essere', n: 7, last: today }]), sheetLookups({ lookups }, 'sl', today))
  const looking = await put({ age: 'VAS', buildings: [school], course: doneState, sheet: { pinned: [], opens: {}, lookups } })
  const courseLooked = JSON.parse(text(await call('get_course'))).looked_up
  const villageLooked = JSON.parse(text(await call('get_village'))).looked_up
  const bitiTitle = JSON.parse(readFileSync(join(repo, 'grammar/sl/biti.json'), 'utf8')).title.en
  check("get_course: looked_up, the pages Jan looked up most first, each with its title, count, last day and the course's units that teach it",
    looking.ok && JSON.stringify(courseLooked?.map((l: any) => [l.page, l.n, l.last, l.units])) === JSON.stringify([['biti', 4, today, ['biti']], ['zanikanje', 3, day(-2), ['biti']], ['osebni-zaimki', 1, day(-5), []]]) &&
      courseLooked[0].title === bitiTitle && typeof courseLooked[2].title === 'string', courseLooked)
  check('get_village: looked_up, the same pages with their titles (the units are get_course\'s)',
    JSON.stringify(villageLooked) === JSON.stringify(courseLooked?.map(({ units, ...l }: any) => l)), villageLooked)

  // an app from before the school leaves the course out of its state: it stays
  const older = await put({ age: 'VAS', buildings: [school] })
  const kept = await (await fetch(`${base}/game`, { headers: auth })).json()
  check("PUT /game keeps the learner's course when an older app's state leaves it out", older.ok && kept.state.course?.units?.['sl/biti']?.done === '2026-10-03' && kept.state.course.classroom === '2026-10-01', kept.state)
  await put({ age: 'TABOR', buildings: [] })
  const noSchool = JSON.parse(text(await call('get_course')))
  check('get_course without a school building: school null, the course as kept', noSchool.school === null && noSchool.classroom === '2026-10-01', noSchool)
  // the village as it was (none yet: the game section starts without one)
  if (existed) writeFileSync(gamePath, saved)
  else unlinkSync(gamePath)
  const empty = JSON.parse(text(await call('get_course')))
  check('get_course without a village: no classroom, no level open, nothing next', empty.classroom === null && empty.school === null && empty.levels[0].open === false && empty.next === null, empty)

  // --- the guide -------------------------------------------------------------------------------------------------------
  const guide = await guideOf()
  check('the channel guide tells the tutor about the school: publish_worksheet, get_course, a session_end\'s course', guide.includes('publish_worksheet') && guide.includes('get_course') && guide.includes('course.unit_done'), guide.slice(guide.indexOf('The school'), guide.indexOf('The school') + 300))

  // the homework a later section would see: none (as the bridge started)
  await call('remove_worksheet', { unit: 'biti', id: 'biti-domaca-1' })
}
