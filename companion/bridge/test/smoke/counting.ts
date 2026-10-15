// Smoke checks: counting cards (counting.ts, features/counting.ts; companion/README.md "Counting cards"): a noun's card
// made from the dictionary's forms (the repo's lexicon/sl.json), m, f and n, the irregular ones as they are, none without
// a gender or a form; POST /counting making a few a day of the learner's learned nouns, kept with their forms; a review's
// counting steps (POST /reviews `counting`) written as mistakes by kind, and the tutor's note.
import { mkdtempSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { COUNTING_PATTERNS, countingErrors, countingNote, countingOf, countingRuns, isCard, NUMERALS, type CountingCard } from '../../src/counting'
import { FormsBook, formsOf, LineIndex } from '../../src/forms'
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

export default async function counting() {
  // --- a card from the dictionary ----------------------------------------------------------------------------------
  const repo = resolve(import.meta.dir, '../../../lexicon')
  const dict = new Lexicon({ dir: repo, language: 'sl', tutorFile: join(mkdtempSync(join(tmpdir(), 'lani-smoke-counting-')), 'lexicon.json') })
  const c = { dict, book: new FormsBook({ dir: repo, language: 'sl' }), pages: new Set(['dvojina', 'mnozina', 'rodilnik', 'imenovalnik', 'tozilnik', 'mestnik', 'orodnik', 'dajalnik']), lines: new LineIndex([]), language: 'sl' }
  const card = (w: string) => {
    const tries = formsOf(c, w).filter(e => e.pos === 'noun').map(e => countingOf(e))
    return (tries.find(isCard) ?? tries[0]) as CountingCard | { why: string } | undefined
  }
  const lines = (w: string) => {
    const k = card(w)
    return k && isCard(k) ? k.lines.join(', ') : `none: ${(k as { why?: string })?.why ?? 'no noun'}`
  }
  check(
    'countingOf: a masculine, a feminine and a neuter noun counted 1, 2, 3, 5 with the numeral of their gender, from the dictionary',
    lines('sin') === 'en sin, dva sinova, trije sinovi, pet sinov' && lines('kava') === 'ena kava, dve kavi, tri kave, pet kav' &&
      lines('jajce') === 'eno jajce, dve jajci, tri jajca, pet jajc',
    ['sin', 'kava', 'jajce'].map(lines),
  )
  check(
    'countingOf: the irregular ones as the dictionary has them: oče → dva očeta, dan → dva dneva (pet dni or dnevov), pesem → dve pesmi',
    lines('oče').startsWith('en oče, dva očeta, ') && /^en dan, dva dneva, trije dnevi, pet (dni|dnevov)$/.test(lines('dan')) &&
      lines('pesem') === 'ena pesem, dve pesmi, tri pesmi, pet pesmi' && lines('človek') === 'en človek, dva človeka, trije ljudje, pet ljudi',
    ['oče', 'dan', 'pesem', 'človek'].map(lines),
  )
  const sestra = card('sestra') as CountingCard
  check(
    "countingOf: of a slot's forms the line shows one that isn't the slot it is told from (dve sestri, not sestre; pet sester, not sestra); all are kept, and right",
    sestra.lines[1] === 'dve sestri' && sestra.lines[3] === 'pet sester' && sestra.forms['nom.du'].includes('sestre') && sestra.forms['gen.pl'].includes('sestra'),
    sestra,
  )
  check(
    'countingOf: no card without one of the four forms (mleko: no plural; vrata: no singular), nor of a word that is no noun',
    lines('mleko').startsWith('none: no nom.du') && lines('vrata').startsWith('none: no nom.sg') && card('sesti') === undefined,
    ['mleko', 'vrata', 'sesti'].map(lines),
  )
  check('the numerals agree with the gender: en/dva/trije, ena/dve/tri, eno/dve/tri, pet', NUMERALS.m.join() === 'en,dva,trije,pet' && NUMERALS.f.join() === 'ena,dve,tri,pet' && NUMERALS.n.join() === 'eno,dve,tri,pet')

  // --- POST /counting: the learner's learned nouns, a few a day -------------------------------------------------------
  const today = new Date().toLocaleDateString('sv')
  const word = (id: string, content: string, answer: string, extra: object = {}) => ({ item_id: id, item_type: 'vocabulary', content, answer, initial_quality: 3, ...extra })
  update({
    record_session: false, date: today,
    new_vocabulary: [word('vocab_word_kava', 'kava', 'coffee'), word('vocab_word_oce', 'oče', 'father'), word('vocab_word_pesem', 'pesem', 'song'),
      word('vocab_word_ovca', 'ovca', 'sheep'), word('vocab_word_sin', 'sin', 'son', { source: 'lookup' }), word('vocab_word_mleko2', 'mleko', 'milk')],
  })
  const first = await post('/counting', { words: [{ word: 'sin' }, { word: 'mleko' }, { word: 'nebesedica' }, { word: 'ovca' }, { word: 'kava', item_id: 'vocab_word_kava' }, { word: 'Kava' }, { word: 'oče' }, { word: 'pesem' }] })
  const fb = (await first.json()) as any
  const items = db('spaced-repetition.json').items
  const why = (w: string) => fb.skipped?.find((s: any) => s.word === w)?.why ?? ''
  check(
    'POST /counting: the first three learned nouns that make a card (the day\'s), the others skipped with why: not learned (a lookup), no plural, not a word, the day\'s made',
    first.ok && fb.added?.map((a: any) => a.lemma).join() === 'ovca,kava,oče' && fb.made_today === 3 && fb.per_day === 3 &&
      why('sin').includes('not a word the learner has learned') && why('mleko').includes('no nom.du') && why('nebesedica').includes('not a word') &&
      why('pesem').includes("today's 3"),
    fb,
  )
  const ovca = items.counting_ovca
  check(
    "POST /counting: a card is a review item of its own, due today, its forms kept (the shown form the content says: pet ovc, Wiktionary's ovac also right)",
    ovca?.type === 'counting' && ovca.content === 'ovca' && ovca.answer === 'ena ovca · dve ovci · tri ovce · pet ovc' && ovca.due_date === today &&
      ovca.counting?.gender === 'f' && ovca.counting.forms['gen.pl'].includes('ovac') && ovca.counting.meaning === 'sheep' && ovca.category === 'counting' &&
      db('spaced-repetition.json').review_queue.today.includes('counting_ovca'),
    ovca,
  )
  const again = (await (await post('/counting', { words: [{ word: 'kava' }, { word: 'pesem' }] })).json()) as any
  check('POST /counting: a noun with a card is skipped, and the day\'s cards are made', again.added?.length === 0 && again.skipped?.[0]?.why === 'it has a counting card' && again.skipped?.[1]?.why.includes("today's 3"), again)
  const backlog = (await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as { event: any }[]
  check('POST /counting: the app hears words_added with the new cards', backlog.some(b => b.event.type === 'words_added' && b.event.item_ids?.join() === 'counting_ovca,counting_kava,counting_oce'))
  const bad = await Promise.all([post('/counting', {}), post('/counting', { words: [] }), post('/counting', { words: [{ word: 'x'.repeat(41) }] })])
  check('POST /counting: 400 without words, or one too long', bad.every(r => r.status === 400), bad.map(r => r.status))
  const state = (await (await fetch(`${base}/state`, { headers: auth })).json()) as any
  check('GET /state says the bridge makes counting cards (features: "counting")', state.features?.includes('counting'), state.features)

  // --- a review with counting cards: the wrong steps as mistakes by kind ---------------------------------------------
  const runs = [
    { item_id: 'counting_kava', lemma: 'kava', typed: false, steps: [
      { n: 1, right: true, answer: 'ena', expected: 'ena kava' }, { n: 2, right: false, kind: 'dual', answer: 'dve kave', expected: 'dve kavi' },
      { n: 3, right: true, answer: 'tri kave', expected: 'tri kave' }, { n: 5, right: false, kind: 'genitive_plural', answer: 'pet kave', expected: 'pet kav' },
    ] },
    { item_id: 'counting_oce', lemma: 'oče', typed: true, steps: [
      { n: 1, right: false, kind: 'gender', answer: 'eno', expected: 'en oče' }, { n: 2, right: true, answer: 'dva očeta', expected: 'dva očeta' },
      { n: 3, right: true, answer: 'trije očetje', expected: 'trije očeti' }, { n: 5, right: true, answer: 'pet očetov', expected: 'pet očetov' },
    ] },
    { item_id: 'counting_bad', steps: 'nonsense' },
  ]
  check('countingRuns: a card that doesn\'t read is left out', countingRuns(runs).length === 2 && countingRuns('x').length === 0)
  check('countingErrors: one mistake per wrong step, of its kind', countingErrors(countingRuns(runs)).map(e => e.pattern_id).join() === 'counting_dual,counting_from_five,counting_noun_gender')
  check('the patterns name their pages by tag (counting; noun_gender, dual, plural), never the genitive\'s', Object.values(COUNTING_PATTERNS).every(p => p.id.startsWith('counting_') && !p.id.includes('genitive')))
  const rv = await post('/reviews', { results: [{ item_id: 'counting_kava', quality: 2 }, { item_id: 'counting_oce', quality: 3 }], duration_minutes: 2, from: 'review', counting: runs })
  const rb = (await rv.json()) as any
  const mistakes = db('mistakes-db.json').error_patterns
  check(
    'POST /reviews with counting: the review persisted, each wrong step a mistake of its kind with what was said and what was right',
    rv.ok && rb.total === 2 && mistakes.counting_dual?.examples?.at(-1)?.incorrect === 'dve kave' && mistakes.counting_dual.examples.at(-1).correct === 'dve kavi' &&
      mistakes.counting_from_five?.frequency >= 1 && mistakes.counting_noun_gender?.examples?.at(-1)?.incorrect === 'eno' && mistakes.counting_dual.subcategory === 'counting',
    { rb, mistakes: Object.keys(mistakes) },
  )
  await Bun.sleep(100)
  const note = String(channelEvents.filter(n => n.params?.meta?.kind === 'review_done').at(-1)?.params?.content ?? '')
  check('review_done names the counting cards and their wrong steps, ALREADY persisted', note.includes('Counting cards: 2 (kava 2/4; oče 3/4, typed)') && note.includes('kava 2 «dve kave» for «dve kavi» (counting_dual)') && note.includes('ALREADY'), note)
  check('countingNote: nothing without counting cards', countingNote([]) === '')
  const guide = await guideOf()
  check('the guide tells the tutor about counting cards: their steps, the mistake kinds, the unit stevila', guide.includes('Counting cards') && guide.includes('counting_from_five') && guide.includes('stevila'))
}
