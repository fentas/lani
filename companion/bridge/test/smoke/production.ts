// Smoke checks: speaking and free writing in a unit (src/production.ts; companion/COURSE.md, "Speaking and writing"). The
// rules' fixture (the app reads it too), the items' refusals, what an older app gets on GET /course (none of them: a sheet
// of only them left out) and what an app that says ?types=2 gets, the print's part, the teacher's voice, and every
// speaking and writing item of the repository's course: no avoid on a form people say, a child's version in Italian.
import { existsSync, readdirSync, readFileSync, rmSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { heldBack, inBase, validateUnit, type ServedCourse } from '../../src/course'
import { courseTexts } from '../../src/course-speech'
import { COURSE_TYPES, forChild, matches, parseRule, speaking, writing } from '../../src/production'
import { chooseProduce, inBase as printBase } from '../../../prints/sheet'
import { unitHtml } from '../../../prints/html'
import { auth, base, check, client, dataDir } from './harness'
import { unit } from './course-fixture'

const repo = resolve(import.meta.dir, '../../..')
const text = (r: any): string => r?.content?.[0]?.text ?? ''
const call = (name: string, args: object = {}) => client.callTool({ name, arguments: args }) as Promise<any>
const errs = (r: { success: boolean; error?: { issues: { message: string }[] } }) => (r.success ? [] : r.error!.issues.map(i => i.message))

/** A speaking item of the fixture's language (Slovene): answered aloud, the conditional. */
export const sayItem = {
  type: 'speaking',
  grammar: 'biti',
  instruction: { en: 'Odgovori na glas · Answer aloud', de: 'Odgovori na glas · Antworte laut' },
  prompt: { en: 'Mojca asks what you would do on a free day. Answer with **bi**.', de: 'Mojca fragt, was du an einem freien Tag machen würdest. Antworte mit **bi**.' },
  ask: 'Kaj bi {m:naredil|f:naredila} na prost dan?',
  say: 'Na prost dan bi {m:šel|f:šla} v hribe.',
  accept: ['Šel bi v hribe.'],
  spoken: ['Na prost dan bi {m:šu|f:šla} v hribe.'],
  must: [{ label: { en: 'bi + -l (bi šel, bi brala)', de: 'bi + -l (bi šel, bi brala)' }, any: ['bi *l', 'bi *la', 'bi *li', '*l bi', '*la bi', 'bi … *l', 'bi … *la'] }],
  min_words: 3,
  explain: '**bi** + the -l form: bi šel, bi šla.',
}

/** A writing item: a few lines with the conditional and če. */
export const writeItem = {
  type: 'writing',
  grammar: 'biti',
  instruction: { en: 'Napiši · Write', de: 'Napiši · Schreib' },
  prompt: { en: 'Write Mojca three sentences: what would you do if you had a free week?', de: 'Schreib Mojca drei Sätze: Was würdest du tun, wenn du eine Woche frei hättest?' },
  model: 'Če bi {m:imel|f:imela} prost teden, bi {m:šel|f:šla} v hribe. {m:Spal|f:Spala} bi dolgo. Zvečer bi {m:bral|f:brala} knjigo.',
  must: [
    { label: { en: 'twice the conditional: bi + -l', de: 'zweimal den Konditional: bi + -l' }, any: ['bi *l', 'bi *la', '*l bi', '*la bi'], n: 2 },
    { label: { en: 'če (if)', de: 'če (wenn)' }, any: ['če'] },
  ],
  avoid: [{ any: ['bom *l', 'bom *la'], explain: { en: '**bom šel** is the future: here **bi šel**.', de: '**bom šel** ist Futur: hier **bi šel**.' } }],
  length: { min: 12, max: 60 },
  child: { prompt: { en: 'Say one thing you would do tomorrow.', de: 'Sag eine Sache, die du morgen machen würdest.' }, model: 'Jutri bi se {m:igral|f:igrala}.', must: [{ label: { en: 'bi + -l', de: 'bi + -l' }, any: ['bi *l', 'bi *la', 'bi se *l', 'bi se *la'] }], length: { min: 3, max: 12 } },
}

export default async function production() {
  // --- the rules: the fixture both read ------------------------------------------------------------------------------
  const fixture = JSON.parse(readFileSync(join(repo, 'bridge/test/fixtures/production-rules.json'), 'utf8'))
  const off = fixture.cases.filter((c: any) => JSON.stringify(matches(c.text, c.rules)) !== JSON.stringify(c.matches) || c.matches.length !== c.n)
  check(`rules: the fixture's ${fixture.cases.length} cases match as written (a word, stem*, *ending, … for up to three words; folded; non-overlapping)`, fixture.cases.length >= 15 && off.length === 0, off.map((c: any) => [c.text, matches(c.text, c.rules)]))
  const kept = fixture.refused.filter((r: string) => typeof parseRule(r) !== 'string')
  check(`rules: the fixture's ${fixture.refused.length} bad rules are refused (… at an end, twice, a / or |, a lone *, * inside, placeholders, brackets)`, kept.length === 0, kept)

  // --- the items -----------------------------------------------------------------------------------------------------
  const en = (x: object) => inBase(x, 'en')
  check('a speaking item and a writing item (with a child\'s version) validate', speaking.safeParse(en(sayItem)).success && writing.safeParse(en(writeItem)).success, [errs(speaking.safeParse(en(sayItem))), errs(writing.safeParse(en(writeItem)))])
  const refusals: [string, object, string][] = [
    ['a model that misses its own must', { ...writeItem, model: 'Če imam čas, grem v hribe. Spim dolgo.' }, "doesn't meet its own must"],
    ['a model with what it avoids', { ...writeItem, model: 'Če bi {m:imel|f:imela} čas, bi {m:šel|f:šla} v hribe. Jutri bom {m:šel|f:šla} domov.' }, 'has what it avoids'],
    ['a model shorter than its length', { ...writeItem, length: { min: 40 } }, 'the length asks 40 at least'],
    ['nothing to check', { ...writeItem, must: [], avoid: [], length: undefined }, 'nothing to check'],
    ['a bad rule', { ...writeItem, must: [{ label: 'x', any: ['bi … …'] }] }, 'two … in a row'],
    ["a child's writing too long", { ...writeItem, child: { ...writeItem.child, length: { min: 3, max: 40 } } }, 'a child writes at most'],
    ['a key the type lacks', { ...writeItem, rubric: 'x' }, ''],
  ]
  for (const [what, item, says] of refusals) {
    const r = writing.safeParse(en(item))
    check(`writing refuses ${what}`, !r.success && errs(r).some(m => m.includes(says)), errs(r))
  }
  const sayRefusals: [string, object, string][] = [
    ['a spoken form that is the model', { ...sayItem, spoken: [sayItem.say] }, 'spoken lists what people say beside them'],
    ['an avoid that matches a spoken form', { ...sayItem, avoid: [{ any: ['šu'], explain: 'x' }] }, 'no avoid may match it'],
    ['an accepted answer that misses the must', { ...sayItem, accept: ['V hribe.'] }, "doesn't meet its own must"],
    ["a child's sentence too long", { ...sayItem, child: { say: 'Jutri bi šel z dedkom in babico in mamo in očetom zelo daleč v hribe na izlet.' } }, "a child's sentence of"],
  ]
  for (const [what, item, says] of sayRefusals) {
    const r = speaking.safeParse(en(item))
    check(`speaking refuses ${what}`, !r.success && errs(r).some(m => m.includes(says)), errs(r))
  }
  const kid = forChild(writing.parse(en(writeItem)))
  check("forChild: a child's version's keys over the item's, the rest kept", kid.model === 'Jutri bi se {m:igral|f:igrala}.' && kid.must.length === 1 && kid.avoid.length === 1 && !('child' in kid), kid)

  // --- GET /course: an older app gets none of them; ?types=2 gets them -----------------------------------------------
  check('COURSE_TYPES: an app says ?types=2 to get speaking and writing', COURSE_TYPES === 2)
  const homework = {
    schema: 'lani.worksheet/v0',
    unit: 'biti',
    id: 'biti-govori',
    title: { sl: 'Povej in napiši', en: 'Speak and write' },
    exercises: [sayItem, writeItem, { type: 'cloze', grammar: 'biti', text: '{learner} ___ doma.', accept: ['je'] }],
  }
  const only = { ...homework, id: 'biti-govori-2', exercises: [sayItem, writeItem] }
  const pub = await call('publish_worksheet', { worksheet: homework })
  const pub2 = await call('publish_worksheet', { worksheet: only })
  check('publish_worksheet takes speaking and writing in homework', !pub.isError && !pub2.isError, [text(pub), text(pub2)])
  const old = await (await fetch(`${base}/course`, { headers: auth })).json()
  const now = await (await fetch(`${base}/course?types=${COURSE_TYPES}`, { headers: auth })).json()
  const hw = (c: any, id: string) => c.units[0].homework.find((h: any) => h.id === id)
  check('GET /course (an older app, 0.2.698 on Teo\'s tablet): no speaking or writing; a sheet of only them left out; the rest as it was',
    hw(old, 'biti-govori')?.exercises.map((e: any) => e.type).join() === 'cloze' && !hw(old, 'biti-govori-2') && old.units[0].worksheets.length === unit.worksheets.length &&
      old.units[0].test.exercises.length === unit.test.exercises.length,
    old.units[0].homework.map((h: any) => [h.id, h.exercises.map((e: any) => e.type)]))
  check('GET /course?types=2: speaking and writing served, rendered for the learner (Jan: "šel")',
    hw(now, 'biti-govori')?.exercises.map((e: any) => e.type).join() === 'speaking,writing,cloze' && hw(now, 'biti-govori-2')?.exercises.length === 2 &&
      hw(now, 'biti-govori').exercises[0].say === 'Na prost dan bi šel v hribe.' && hw(now, 'biti-govori').exercises[1].child.model === 'Jutri bi se igral.',
    hw(now, 'biti-govori'))
  check('GET /course?types=garbage reads as an older app', (await (await fetch(`${base}/course?types=x`, { headers: auth })).json()).units[0].homework.every((h: any) => h.exercises.every((e: any) => e.type !== 'speaking')))
  for (const id of ['biti-govori', 'biti-govori-2']) await call('remove_worksheet', { unit: 'biti', id })
  const dir = join(dataDir, 'app/course/biti')
  check('…the smoke\'s homework removed again', !existsSync(join(dir, 'biti-govori.json')) && !existsSync(join(dir, 'biti-govori-2.json')))
  if (existsSync(join(dir, 'biti-govori.json'))) rmSync(join(dir, 'biti-govori.json'))

  // heldBack on a unit's own sheets and test
  const v = validateUnit({ ...unit, worksheets: [...unit.worksheets, { id: 'biti-4', title: { sl: 'Povej in napiši', en: 'Speak and write' }, exercises: [sayItem, writeItem] }], test: { ...unit.test, exercises: [...unit.test.exercises, sayItem, writeItem] } })
  check('a unit validates with a sheet of speaking and writing and both in its test', v.ok, !v.ok && v.errors)
  if (v.ok) {
    const c: ServedCourse = { schema: 'lani.course/v0', language: 'sl', levels: [], units: [{ ...v.unit, homework: [] }], machine_written: true }
    const h = heldBack(c, 1).units[0]
    check('heldBack: the sheet of only speaking and writing left out, the test without them, the unit kept', h.worksheets.length === unit.worksheets.length && h.test.exercises.length === unit.test.exercises.length && heldBack(c, 2) === c)

    // --- the print: speak and write on the sheet ---------------------------------------------------------------------
    const raw = JSON.parse(JSON.stringify({ ...unit, worksheets: [...unit.worksheets, { id: 'biti-4', title: { sl: 'Povej in napiši', en: 'Speak and write' }, exercises: [sayItem, writeItem] }] }))
    const p = chooseProduce(printBase(raw, 'en'), 'en', { base: 'en', learner: 'Jan', gender: 'm' }, true)
    const pc = chooseProduce(printBase(raw, 'de'), 'de', { base: 'de', learner: 'Teo', gender: 'm', child: true }, true)
    check("the print's speak and write: the task in the base, what the teacher asks, the must to tick, the length, the models; a child's sheet the child's version",
      p.say.length === 1 && p.say[0].ask === 'Kaj bi naredil na prost dan?' && p.say[0].a === 'Na prost dan bi šel v hribe.' && p.write.length === 1 && p.write[0].must.length === 2 && p.write[0].min === 12 && p.write[0].max === 60 &&
        pc.write[0].task.startsWith('Sag eine Sache') && pc.write[0].a === 'Jutri bi se igral.', [p, pc])
    const both = chooseProduce(printBase(raw, 'en'), 'en', { base: 'en', learner: 'Jan', gender: 'both' }, true)
    check("…for both genders, the short pairs both (šel/šla)", both.say[0]?.a === 'Na prost dan bi šel/šla v hribe.', both)
    const html = unitHtml({ id: 'biti', n: 1, of: 1, level: 'A1', language: 'sl', emoji: '🟰', title: { sl: 'Biti', tr: 'To be' }, rule: [], tables: [], examples: [], watch: [], words: [], check: [], ...p, unreviewed: true, wiktionary: false, notes: [] }, 'en', 'A1', 'sl', new Map())
    check('…laid out: "Povej in napiši · Speak and write", 🗣️ and ✍️, a box to tick for each must (in either layout), the models with the answers (the writing\'s left out first to fit)',
      html.includes('Povej in napiši') && html.includes('Speak and write') && html.includes('🗣️') && (html.match(/class="box"/g) ?? []).length === 2 * 2 /* the one-page layout and the two-page one */ && html.includes('(12–60 words)') && html.includes('<span class="a opt"><b>✍️</b>'))

    // --- the teacher's voice ---------------------------------------------------------------------------------------
    const ct = courseTexts({ units: [v.unit], bases: ['en'] }, 'sl')
    check("the course's corpus: what the teacher asks and the speaking item's model, the child's version's too, in her voice",
      ['Kaj bi {m:naredil|f:naredila} na prost dan?', 'Na prost dan bi {m:šel|f:šla} v hribe.'].every(t => ct.lines.some(l => l.text === t)), ct.lines.slice(-6))
  }

  // --- the repository's course: every speaking and writing item ------------------------------------------------------
  const grading = (lang: string) => {
    const p = join(repo, `android/app/src/main/resources/grading/${lang}.json`)
    return existsSync(p) ? JSON.parse(readFileSync(p, 'utf8')) : {}
  }
  let items = 0
  const problems: string[] = []
  for (const lang of ['sl', 'it', 'de', 'en']) {
    const dir = join(repo, 'course', lang, 'units')
    if (!existsSync(dir)) continue
    // what people say (the table's nonstandard, the item's spoken) is never something to avoid (COURSE.md "Spoken forms")
    const said: string[] = (grading(lang).nonstandard ?? []).flatMap((n: any) => String(n.word).split('|')).filter((w: string) => /^[\p{L} ]+$/u.test(w))
    for (const f of readdirSync(dir).filter(x => x.endsWith('.json'))) {
      const u = JSON.parse(readFileSync(join(dir, f), 'utf8'))
      const all = [...(u.worksheets ?? []).flatMap((w: any) => (w.exercises ?? []).map((e: any) => [w.id, e])), ...(u.test?.exercises ?? []).map((e: any) => ['test', e])]
      for (const [where, e] of all) {
        if (e.type !== 'speaking' && e.type !== 'writing') continue
        items++
        const at = `${lang}/${u.id} ${where}`
        for (const variant of [e, e.child ? { ...e, ...e.child } : null].filter(Boolean)) {
          for (const a of variant.avoid ?? []) {
            const hit = [...said, ...(variant.spoken ?? [])].filter(s => matches(s, a.any).length)
            if (hit.length) problems.push(`${at}: avoid ${JSON.stringify(a.any)} matches what people say: ${hit.join(', ')}`)
          }
        }
        // Friuli's school is a child's (Teo, 7): every speaking and writing item has its child's version
        if (lang === 'it' && !e.child) problems.push(`${at}: an Italian ${e.type} item without its child's version`)
        if (!['A1', 'A2'].includes(u.level) && e.type === 'writing' && !e.length) problems.push(`${at}: a ${u.level} writing item without its length`)
      }
    }
  }
  check(`the course's speaking and writing items (${items}): no avoid on a form people say (the grading table's nonstandard, the item's spoken); Italian's each with a child's version; a length on each writing`, problems.length === 0, problems)
}
