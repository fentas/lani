// Smoke checks: forms cards (forms-cards.ts, features/forms-cards.ts; companion/README.md "Forms cards"): a word's whole
// table from the dictionary's forms (the repo's lexicon/sl.json and the forms book): a noun's, a verb's with its future
// joined from biti's and the l-form, the irregular ones as they are, an adjective's genders and cases, none of a word
// with too few forms; what's tested by the pages introduced; POST /forms-cards making two a day of the learner's learned
// words (the core verbs first) and one at once on demand, bringing back soon the cards a newly introduced page tests
// more of (three a day), as new learning with their history kept; a review's forms cards (POST /reviews `forms_cards`)
// written as mistakes by kind, and the tutor's note.
import { mkdtempSync, readdirSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { adjectiveForms, FormsBook, formsOf, LineIndex } from '../../src/forms'
import {
  FORMS_PATTERNS, formsCardOf, formsCardsNote, formsErrors, formsRuns, isCard, resurfacePlan, testedOf,
  type CardContext, type FormsCardData,
} from '../../src/forms-cards'
import { Lexicon } from '../../src/lexicon'
import { auth, base, channelEvents, check, dataDir, guideOf } from './harness'

const db = (f: string) => JSON.parse(readFileSync(join(dataDir, f), 'utf8'))
const post = (path: string, body: object) => fetch(`${base}${path}`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
const update = (payload: object) => {
  const p = Bun.spawnSync(['python3', resolve(import.meta.dir, '../../../../.claude/hooks/update-db.py')], {
    stdin: new TextEncoder().encode(JSON.stringify(payload)), env: { ...process.env, LANI_DATA_DIR: dataDir },
  })
  if (p.exitCode !== 0) throw new Error(p.stderr.toString())
}
const plus = (day: string, n: number) => {
  const d = new Date(`${day}T12:00:00Z`)
  d.setUTCDate(d.getUTCDate() + n)
  return d.toISOString().slice(0, 10)
}

export default async function formsCards() {
  // --- a card from the dictionary ----------------------------------------------------------------------------------
  const repo = resolve(import.meta.dir, '../../../lexicon')
  const dict = new Lexicon({ dir: repo, language: 'sl', tutorFile: join(mkdtempSync(join(tmpdir(), 'lani-smoke-forms-cards-')), 'lexicon.json') })
  const book = new FormsBook({ dir: repo, language: 'sl' })
  const pages = new Set(readdirSync(resolve(import.meta.dir, '../../../grammar/sl')).filter(f => f.endsWith('.json')).map(f => f.replace(/\.json$/, '')))
  const c = { dict, book, pages, lines: new LineIndex([]), language: 'sl' }
  const biti = book.table('biti', 'verb')!
  const bitiFuture: CardContext['bitiFuture'] = Object.fromEntries(Object.entries(biti.forms).filter(([k]) => k.startsWith('fut.')).map(([k, v]) => [k, { forms: typeof v === 'string' ? [v] : v!, also: biti.also?.[k] ?? [] }]))
  const k: CardContext = { bitiFuture, adjective: l => adjectiveForms(c, l), pages, language: 'sl' }
  const card = (w: string, pos?: string, kk: CardContext = k) => {
    const tries = formsOf(c, w).filter(e => !pos || e.pos === pos).map(e => formsCardOf(e, kk))
    return (tries.find(isCard) ?? tries[0]) as FormsCardData | { why: string } | undefined
  }
  const slot = (x: unknown, key: string) => (x && isCard(x as FormsCardData) ? (x as FormsCardData).slots.find(s => s.key === key) : undefined)
  const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b)

  const hisa = card('hiša', 'noun') as FormsCardData
  check(
    "formsCardOf: a noun's six cases by number from the dictionary, each with its page (hišo: tožilnik; hišah: mestnik after množina), the lemma's own kept",
    hisa?.pos === 'noun' && hisa.gender === 'f' && hisa.slots.length === 18 && same(slot(hisa, 'acc.sg')?.forms, ['hišo']) && slot(hisa, 'acc.sg')?.page === 'tozilnik' &&
      same(slot(hisa, 'loc.pl')?.forms, ['hišah']) && same(slot(hisa, 'loc.pl')?.needs, ['mnozina']) && slot(hisa, 'nom.du')?.page === 'dvojina' &&
      same(slot(hisa, 'nom.sg')?.forms, ['hiša']) && same(hisa.tested, []),
    hisa,
  )
  const delati = card('delati', 'verb') as FormsCardData
  const fut = (key: string) => slot(delati, key)
  check(
    "formsCardOf: a verb's future joined from biti's and its l-form (bom delal, also bom delala; bodo delali, also bojo delali; the third person masculine), marked composed",
    same(fut('fut.1sg')?.forms, ['bom delal']) && fut('fut.1sg')?.also?.includes('bom delala') && fut('fut.1sg')?.composed === true &&
      same(fut('fut.3pl')?.forms, ['bodo delali']) && same(fut('fut.3pl')?.also, ['bojo delali']) && same(fut('fut.3sg')?.forms, ['bo delal']) && !fut('fut.3sg')?.also &&
      same(fut('fut.1du')?.forms, ['bova delala']) && fut('fut.1du')?.page === 'dvojina' && same(fut('fut.1du')?.needs, ['prihodnjik']) &&
      same(slot(delati, 'pres.3pl')?.forms, ['delajo']) && same(slot(delati, 'past.f.sg')?.forms, ['delala']) && same(slot(delati, 'imp.2pl')?.forms, ['delajte']) &&
      delati.slots.findIndex(s => s.key === 'fut.1sg') < delati.slots.findIndex(s => s.key === 'past.m.sg'),
    delati?.slots?.filter(s => s.key.startsWith('fut.')),
  )
  const female = card('delati', 'verb', { ...k, female: true }) as FormsCardData
  check('formsCardOf: for a woman the first person\'s future shows the feminine first (bom delala, also bom delal); the second stays masculine first',
    same(slot(female, 'fut.1sg')?.forms, ['bom delala']) && slot(female, 'fut.1sg')?.also?.includes('bom delal') && same(slot(female, 'fut.2sg')?.forms, ['boš delal']),
    slot(female, 'fut.1sg'))
  const b = card('biti', 'verb') as FormsCardData
  const iti = card('iti', 'verb') as FormsCardData
  const clovek = card('človek', 'noun') as FormsCardData
  check(
    "formsCardOf: the irregular ones as the forms book and the dictionary have them: biti's own future (bom, bodo also bojo) and negative (nisem); iti → grem, šel, bom šel; človek → ljudje",
    same(slot(b, 'fut.1sg')?.forms, ['bom']) && !slot(b, 'fut.1sg')?.composed && same(slot(b, 'fut.3pl')?.also, ['bojo']) && same(slot(b, 'neg.1sg')?.forms, ['nisem']) &&
      same(slot(iti, 'pres.1sg')?.forms, ['grem']) && same(slot(iti, 'past.m.sg')?.forms, ['šel']) && same(slot(iti, 'fut.1sg')?.forms, ['bom šel']) &&
      same(slot(clovek, 'nom.pl')?.forms, ['ljudje']) && same(slot(clovek, 'gen.pl')?.forms, ['ljudi']),
    { biti: slot(b, 'fut.3pl'), iti: slot(iti, 'fut.1sg'), clovek: slot(clovek, 'nom.pl') },
  )
  const dober = card('dober', 'adj') as FormsCardData
  check(
    "formsCardOf: an adjective's genders, then its cases from the dictionary (dobrega, dobri), the accusative masculine singular left out (animacy), no comparative",
    same(slot(dober, 'adj.f.sg')?.forms, ['dobra']) && same(slot(dober, 'adj.n.pl')?.forms, ['dobra']) && same(slot(dober, 'adj.f.du')?.forms, ['dobri']) &&
      same(slot(dober, 'adj.gen.m.sg')?.forms, ['dobrega']) && same(slot(dober, 'adj.dat.f.sg')?.forms, ['dobri']) && same(slot(dober, 'adj.ins.f.pl')?.forms, ['dobrimi']) &&
      slot(dober, 'adj.gen.m.sg')?.page === 'pridevniki-ujemanje' && same(slot(dober, 'adj.gen.m.sg')?.needs, ['rodilnik']) && same(slot(dober, 'adj.loc.n.pl')?.needs, ['mestnik', 'mnozina']) &&
      !slot(dober, 'adj.acc.m.sg') && !slot(dober, 'cmp.m.sg'),
    dober?.slots?.map(s => `${s.key}=${s.forms.join('/')}`).join(' '),
  )
  const why = (w: string) => (card(w) as { why?: string } | undefined)?.why ?? 'a card'
  check('formsCardOf: no card of a word with too few forms (glad: 3 besides itself) or too few different ones (deset), nor of a word the dictionary lacks',
    why('glad').startsWith('too few forms') && why('deset').startsWith('too few different') && card('nebesedica') === undefined, [why('glad'), why('deset')])
  check(
    'testedOf: a slot is tested once its page and every page it needs are introduced, never the word itself',
    same(testedOf(hisa.slots, new Set(['tozilnik'])), ['acc.sg']) && same(testedOf(hisa.slots, new Set(['tozilnik', 'mnozina'])), ['acc.sg', 'nom.pl', 'acc.pl']) &&
      testedOf(hisa.slots, new Set(['tozilnik', 'dvojina'])).includes('acc.du') && !testedOf(hisa.slots, new Set(['imenovalnik', 'tozilnik'])).includes('nom.sg'),
    testedOf(hisa.slots, new Set(['tozilnik', 'mnozina'])),
  )
  const today = new Date().toLocaleDateString('sv')
  const planItems = Object.fromEntries(['a', 'b', 'c', 'd', 'e'].map(id => [id, { type: 'forms', due_date: plus(today, 9), repetitions: 3, forms: { ...hisa, lemma: id, tested: ['acc.sg'] } }]))
  const plan = resurfacePlan(planItems, new Set(['tozilnik', 'mestnik']), today, 3, f => -f.lemma.charCodeAt(0))
  check('resurfacePlan: the cards that test more come back, the most useful first, three a day, the new keys fresh',
    same(plan.map(p => `${p.lemma}:${p.due_date === today ? 0 : p.due_date === plus(today, 1) ? 1 : '?'}`), ['a:0', 'b:0', 'c:0', 'd:1', 'e:1']) && same(plan[0].fresh, ['loc.sg']) &&
      same(plan[0].forms.tested, ['acc.sg', 'loc.sg']) && resurfacePlan(planItems, new Set(['tozilnik']), today, 3, () => 0).length === 0,
    plan.map(p => [p.lemma, p.due_date, p.fresh]))

  // --- POST /forms-cards: two a day of the learner's learned words, the core verbs first --------------------------------
  const word = (id: string, content: string, answer: string, extra: object = {}) => ({ item_id: id, item_type: 'vocabulary', content, answer, initial_quality: 3, ...extra })
  update({
    record_session: false, date: today,
    new_vocabulary: [word('vocab_word_hisa', 'hiša', 'house'), word('vocab_word_delati', 'delati', 'to work'), word('vocab_word_biti', 'biti', 'to be'),
      word('vocab_word_glad', 'glad', 'hunger'), word('vocab_word_dober', 'dober', 'good')],
  })
  const I0 = ['tozilnik', 'mestnik', 'mnozina', 'glagoli-sedanjik', 'biti', 'dvojina']
  const first = await post('/forms-cards', { words: [{ word: 'hiša' }, { word: 'glad' }, { word: 'delati' }, { word: 'nebesedica' }, { word: 'biti' }, { word: 'sin' }, { word: 'dober' }], introduced: I0 })
  const fb = (await first.json()) as any
  const skip = (w: string) => fb.skipped?.find((s: any) => s.word === w)?.why ?? ''
  check(
    "POST /forms-cards: the day's two, the core verbs first (biti, delati), the others skipped with why: the day's made, too few forms, not learned (no such word, a lookup), too little introduced",
    first.ok && fb.added?.map((a: any) => a.lemma).join() === 'biti,delati' && fb.made_today === 2 && fb.per_day === 2 && fb.resurface_per_day === 3 &&
      skip('hiša').startsWith("today's 2") && skip('glad').startsWith('too few forms') && skip('nebesedica') === 'not a word the learner has learned' &&
      skip('sin') === 'not a word the learner has learned' && skip('dober').startsWith("fewer than 2") && fb.added[0].tested === 9,
    fb,
  )
  const items = () => db('spaced-repetition.json').items
  const dItem = items().forms_delati
  check(
    "POST /forms-cards: a card is a review item of its own, due today, its table kept with what's tested now and the learner's meaning",
    dItem?.type === 'forms' && dItem.content === 'delati' && dItem.due_date === today && dItem.category === 'forms' && dItem.answer.startsWith('delam · delaš · dela') &&
      dItem.forms?.pos === 'verb' && dItem.forms.meaning === 'to work' && dItem.forms.tested.length === 9 && dItem.forms.tested.includes('pres.1du') && !dItem.forms.asked &&
      db('spaced-repetition.json').review_queue.today.includes('forms_delati'),
    dItem,
  )
  const backlog = (await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as { event: any }[]
  check('POST /forms-cards: the app hears words_added with the new cards', backlog.some(bl => bl.event.type === 'words_added' && bl.event.item_ids?.join() === 'forms_biti,forms_delati'))
  const asked = (await (await post('/forms-cards', { words: [{ word: 'hiša', item_id: 'vocab_word_hisa' }], introduced: I0, asked: true })).json()) as any
  const mesto = (await (await post('/forms-cards', { words: [{ word: 'mesto' }], introduced: I0, asked: true })).json()) as any
  const again = (await (await post('/forms-cards', { words: [{ word: 'hiša' }], introduced: I0, asked: true })).json()) as any
  const none = (await (await post('/forms-cards', { words: [{ word: 'nebesedica' }], introduced: I0, asked: true })).json()) as any
  const early = (await (await post('/forms-cards', { words: [{ word: 'dober' }], introduced: ['tozilnik'], asked: true })).json()) as any
  check(
    "POST /forms-cards asked: one word's card at once, beyond the day's cap and whether it was learned (mesto); a word with a card already: existing; none of a word the dictionary lacks, or with nothing introduced",
    asked.added?.[0]?.item_id === 'forms_hisa' && !asked.added[0].existing && asked.added[0].tested === 8 && asked.made_today === 2 &&
      mesto.added?.[0]?.lemma === 'mesto' && again.added?.[0]?.existing === true && again.added[0].item_id === 'forms_hisa' &&
      items().forms_hisa?.forms?.asked === true && items().forms_hisa.forms.meaning === 'house' &&
      none.skipped?.[0]?.why?.startsWith('no noun, verb or adjective') && early.skipped?.[0]?.why === 'nothing of its table is introduced yet',
    { asked, mesto, again, none, early },
  )
  const auto = (await (await post('/forms-cards', { words: [{ word: 'delati' }], introduced: I0 })).json()) as any
  check("POST /forms-cards: after the day's two, a word by itself waits for tomorrow", auto.added?.length === 0 && auto.skipped?.[0]?.why?.startsWith("today's 2"), auto)
  const bad = await Promise.all([post('/forms-cards', {}), post('/forms-cards', { words: [{ word: 'x'.repeat(41) }] }), post('/forms-cards', { words: [], introduced: ['x'.repeat(61)] })])
  check('POST /forms-cards: 400 without words, or one too long', bad.every(r => r.status === 400), bad.map(r => r.status))
  const state = (await (await fetch(`${base}/state`, { headers: auth })).json()) as any
  check('GET /state says the bridge makes forms cards (features: "forms-cards")', state.features?.includes('forms-cards'), state.features)

  // --- a review with forms cards: the wrong cells as mistakes by kind ------------------------------------------------
  const runs = [
    { item_id: 'forms_hisa', lemma: 'hiša', typed: false, cells: [
      { key: 'acc.sg', right: true, answer: 'hišo', expected: 'hišo' }, { key: 'loc.pl', right: false, kind: 'case_locative', answer: 'hiše', expected: 'hišah' },
      { key: 'nom.du', right: false, kind: 'nonsense', answer: 'hiše', expected: 'hiši' }, 'not a cell',
    ] },
    { item_id: 'forms_delati', lemma: 'delati', typed: true, fresh: true, cells: [
      { key: 'pres.3pl', right: false, kind: 'verb_person_3pl', answer: 'delaju', expected: 'delajo' }, { key: 'pres.1du', right: true, answer: 'delava', expected: 'delava' },
    ] },
    { item_id: 'forms_bad', cells: 'nonsense' },
  ]
  check("formsRuns: a card or a cell that doesn't read is left out, a kind this bridge doesn't know dropped", formsRuns(runs).length === 2 && formsRuns(runs)[0].cells.length === 3 && formsRuns(runs)[0].cells[2].kind === undefined && formsRuns('x').length === 0)
  check('formsErrors: one mistake per wrong cell with a kind, its pattern forms_<kind>', formsErrors(formsRuns(runs)).map(e => e.pattern_id).join() === 'forms_case_locative,forms_verb_person_3pl')
  check('the patterns are forms_<kind>, their words the grammar pages\' tags', Object.entries(FORMS_PATTERNS).every(([kind, p]) => p.id === `forms_${kind}`) && FORMS_PATTERNS.number_dual.id === 'forms_number_dual')
  const rv = await post('/reviews', {
    results: [{ item_id: 'forms_biti', quality: 4 }, { item_id: 'forms_delati', quality: 3 }, { item_id: 'forms_hisa', quality: 4 }, { item_id: 'forms_mesto', quality: 4 }],
    duration_minutes: 2, from: 'review', forms_cards: runs,
  })
  const rb = (await rv.json()) as any
  const mistakes = db('mistakes-db.json').error_patterns
  const log = db('session-log.json').sessions.at(-1)
  check(
    'POST /reviews with forms_cards: the review persisted, each wrong cell a mistake of its kind with what was said and what was right, the cells grammar practice',
    rv.ok && rb.total === 4 && mistakes.forms_case_locative?.examples?.at(-1)?.incorrect === 'hiše' && mistakes.forms_case_locative.examples.at(-1).correct === 'hišah' &&
      mistakes.forms_verb_person_3pl?.frequency >= 1 && mistakes.forms_case_locative.subcategory === 'forms' && !mistakes.forms_nonsense &&
      String(log?.notes).includes('2 forms card(s), 3 wrong cell(s)') && log?.skills_practiced?.includes('grammar'),
    { rb, mistakes: Object.keys(mistakes), log },
  )
  await Bun.sleep(100)
  const note = String(channelEvents.filter(n => n.params?.meta?.kind === 'review_done').at(-1)?.params?.content ?? '')
  check('review_done names the forms cards and their wrong cells, ALREADY persisted',
    note.includes('Forms cards: 2 (hiša 1/3; delati 1/2, typed, its new part)') && note.includes('hiša loc.pl «hiše» for «hišah» (forms_case_locative)') && note.includes('ALREADY'), note)
  check('formsCardsNote: nothing without forms cards', formsCardsNote([]) === '')

  // --- a page introduced: the cards that test more of it come back soon --------------------------------------------
  const before = items()
  const I1 = [...I0, 'velelnik', 'pretekli-cas', 'prihodnjik', 'rodilnik']
  const rs = (await (await post('/forms-cards', { words: [], introduced: I1 })).json()) as any
  const after = items()
  const due = (id: string) => after[id]?.due_date
  const tomorrow = plus(today, 1)
  const dd = after.forms_delati
  check(
    "POST /forms-cards, a page introduced: the four cards that test more come back, three today (the core verbs first), the fourth tomorrow, as they were due",
    rs.added?.length === 0 && rs.resurfaced?.length === 4 && rs.resurfaced[0].lemma === 'biti' && rs.resurfaced[1].lemma === 'delati' &&
      rs.resurfaced.filter((x: any) => x.due_date === today).length === 3 && rs.resurfaced.filter((x: any) => x.due_date === tomorrow).length === 1 &&
      ['forms_biti', 'forms_delati', 'forms_hisa', 'forms_mesto'].every(id => due(id) === today || due(id) === tomorrow),
    rs.resurfaced,
  )
  check(
    'resurfaced: new learning (repetitions 0, interval 1), its history kept (easiness, total reviews, the review), the new part fresh (the imperative, the past, the future)',
    dd.repetitions === 0 && dd.interval_days === 1 && dd.total_reviews === before.forms_delati.total_reviews && dd.total_reviews === 1 &&
      dd.easiness_factor === before.forms_delati.easiness_factor && dd.review_history?.length === 1 && dd.forms.unlocked === today &&
      ['imp.2sg', 'past.m.sg', 'fut.1sg'].every(x => dd.forms.fresh.includes(x)) && !dd.forms.fresh.includes('pres.1sg') && dd.forms.tested.includes('imp.2sg') &&
      dd.resurfaced?.length === 1 && after.forms_hisa.forms.fresh.includes('gen.sg'),
    { before: before.forms_delati, after: dd },
  )
  const raw = readFileSync(join(dataDir, 'spaced-repetition.json'), 'utf8')
  const same1 = (await (await post('/forms-cards', { words: [], introduced: I1 })).json()) as any
  const fewer = (await (await post('/forms-cards', { words: [], introduced: I0 })).json()) as any
  check('POST /forms-cards: the same pages again, or fewer, bring nothing back and write nothing',
    same1.resurfaced?.length === 0 && fewer.resurfaced?.length === 0 && readFileSync(join(dataDir, 'spaced-repetition.json'), 'utf8') === raw, { same1, fewer })
  const guide = await guideOf()
  check('the guide tells the tutor about forms cards: what is tested, the mistake kinds, what to suggest', guide.includes('Forms cards') && guide.includes('forms_case_') && guide.includes('introduced') && guide.includes('get_course'))
}
