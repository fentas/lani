// Smoke checks: a word's forms (GET /forms, forms.ts): its slots and their grammar book pages from Wiktionary's
// dictionary (the repo's lexicon/sl.json), the content's lines a form stands in as that word, the forms book's hand-
// checked tables and verb pairs (lexicon/sl.forms.json), the pairs the word lookup marks, and a review's forms.
import { mkdtempSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { contentLines, FormsBook, formsOf, LineIndex, moodInEnglish, slotsOf, validateFormsBook, type LemmaForms } from '../../src/forms'
import { Lexicon } from '../../src/lexicon'
import { auth, base, channelEvents, check } from './harness'

const forms = async (query: string) => {
  const r = await fetch(`${base}/forms?${query}`, { headers: auth })
  return { status: r.status, body: (await r.json()) as any }
}
const slot = (e: LemmaForms | undefined, key: string) => e?.slots.find(s => s.key === key)
const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b)

export default async function formsSection() {
  const repo = resolve(import.meta.dir, '../../../lexicon')

  // --- grammar strings read back into slots ----------------------------------------------------------------------
  check(
    'slotsOf: Wiktionary\'s grammar strings become slots (persons, l-forms, cases; the neuter plural l-form, the same as the feminine singular, none)',
    same(slotsOf('verb', 'second/third-person dual present'), { keys: ['pres.2du', 'pres.3du'], other: false }) &&
      same(slotsOf('verb', 'feminine singular l-participle; masculine dual l-participle; neuter plural l-participle'), { keys: ['past.f.sg', 'past.m.du'], other: true }) &&
      same(slotsOf('verb', 'first-person singular negative present'), { keys: ['neg.1sg'], other: false }) &&
      same(slotsOf('noun', 'nominative/accusative dual; genitive singular'), { keys: ['nom.du', 'acc.du', 'gen.sg'], other: false }) &&
      same(slotsOf('noun', 'genitive dual/plural'), { keys: ['gen.du', 'gen.pl'], other: false }),
    [slotsOf('verb', 'feminine singular l-participle; masculine dual l-participle; neuter plural l-participle'), slotsOf('noun', 'nominative/accusative dual; genitive singular')],
  )
  check(
    'slotsOf: an infinitive, a supine, an imperative with a gender and no person are no slot; "first/second/third-person singular imperative" is the second person\'s',
    same(slotsOf('verb', 'infinitive; supine'), { keys: [], other: true }) &&
      same(slotsOf('verb', 'feminine singular imperative'), { keys: [], other: true }) &&
      same(slotsOf('verb', 'first/second/third-person singular imperative').keys, ['imp.2sg']) &&
      same(slotsOf('adj', 'nominative masculine singular/plural; dative/locative feminine singular').keys, ['adj.m.sg', 'adj.m.pl']),
    slotsOf('verb', 'first/second/third-person singular imperative'),
  )
  check(
    'moodInEnglish: "The king sits" and "is sitting" are a present, "Then sit down", "Sit closer, lad" an imperative, "Does the owl see" neither',
    moodInEnglish('The king sits at the table.', ['to sit']) === 'pres' && moodInEnglish('The cat is sitting on the windowsill.', ['to sit']) === 'pres' &&
      moodInEnglish('Then sit down by the fire.', ['to sit down']) === 'imp' && moodInEnglish('Good. Sit closer, lad.', ['to sit']) === 'imp' &&
      moodInEnglish('Does the owl see well at night?', ['to see']) === null && moodInEnglish("Let's go!", ['to go'], true) === 'imp' && moodInEnglish('See you tomorrow!', ['to see'], true) === null,
  )

  // --- GET /forms ----------------------------------------------------------------------------------------------
  const r = await forms('w=sesti&w=hi%C5%A1a&w=nebesedica')
  const [sesti, hisa, none] = r.body.words ?? []
  const s0 = sesti?.entries?.find((e: LemmaForms) => e.pos === 'verb') as LemmaForms | undefined
  check(
    'GET /forms: sesti, its present (sedem, the page of the present\'s endings), its l-form (sedla, the past) and imperative (sedite), from Wiktionary with the attribution',
    r.status === 200 && r.body.language === 'sl' && sesti?.word === 'sesti' && s0?.table === 'wiktionary' && same(s0.gloss, ['to sit down']) &&
      same(slot(s0, 'pres.1sg')?.forms, ['sedem']) && slot(s0, 'pres.1sg')?.page === 'glagoli-sedanjik' && !slot(s0, 'pres.1sg')?.needs &&
      slot(s0, 'pres.1sg')?.grammar === 'first-person singular present' &&
      same(slot(s0, 'past.f.sg')?.forms, ['sedla']) && slot(s0, 'past.f.sg')?.page === 'pretekli-cas' &&
      same(slot(s0, 'imp.2pl')?.forms, ['sedite']) && slot(s0, 'imp.2pl')?.page === 'velelnik' &&
      slot(s0, 'pres.1du')?.page === 'dvojina' && same(slot(s0, 'pres.1du')?.needs, ['glagoli-sedanjik']) &&
      r.body.attribution === 'Wiktionary (CC BY-SA 4.0) via kaikki.org',
    { status: r.status, sesti: s0?.slots.slice(0, 3) },
  )
  const h0 = hisa?.entries?.[0] as LemmaForms | undefined
  check(
    'GET /forms: hiša, its six cases in the singular, plural and dual: hiši the locative (mestnik), hišami the instrumental plural (orodnik, needing mnozina), hiš the genitive dual (dvojina, needing rodilnik)',
    h0?.lemma === 'hiša' && h0.pos === 'noun' && h0.gender === 'f' && same(slot(h0, 'nom.sg')?.forms, ['hiša']) &&
      same(slot(h0, 'loc.sg')?.forms, ['hiši']) && slot(h0, 'loc.sg')?.page === 'mestnik' &&
      same(slot(h0, 'ins.pl')?.forms, ['hišami']) && slot(h0, 'ins.pl')?.page === 'orodnik' && same(slot(h0, 'ins.pl')?.needs, ['mnozina']) &&
      same(slot(h0, 'gen.du')?.forms, ['hiš']) && slot(h0, 'gen.du')?.page === 'dvojina' && same(slot(h0, 'gen.du')?.needs, ['rodilnik']) &&
      h0.slots.length === 18 && h0.slots.map(s => s.key).join(' ').startsWith('nom.sg gen.sg dat.sg acc.sg loc.sg ins.sg nom.pl'),
    h0?.slots.map(s => `${s.key}:${s.forms.join('/')}:${s.page}`),
  )
  check('GET /forms: a word the dictionary doesn\'t know has no entries', none?.word === 'nebesedica' && same(none.entries, []), none)
  const lines = [...(r.body.words ?? [])].flatMap((w: any) => w.entries.flatMap((e: LemmaForms) => e.slots.flatMap(s => (s.lines ?? []).map(l => ({ s, l })))))
  check(
    'GET /forms: a slot\'s lines (at most 3, from the content) have its form once, as a word, with their translation',
    lines.length > 0 &&
      lines.every(({ s, l }) => (s.lines?.length ?? 0) <= 3 && [...s.forms, ...(s.also ?? [])].includes(l.form) && typeof l.en === 'string' && /^(scene|story|reading|grammar|pack):/.test(l.from) &&
        (l.sl.toLocaleLowerCase('sl').match(/\p{L}+/gu) ?? []).filter((t: string) => t === l.form).length === 1),
    lines.slice(0, 4),
  )
  const bad = await Promise.all([forms(''), forms(Array.from({ length: 41 }, (_, i) => `w=a${i}`).join('&')), forms('w=sesti&language=it'), forms(`w=${'a'.repeat(81)}`)])
  check('GET /forms: no word, more than 40, another language than the village\'s, a word too long: 400', bad.every(b => b.status === 400 && typeof b.body.error === 'string'), bad.map(b => b.status))

  // --- which lines a form stands in as that word (pure, on lines made up for it) -----------------------------------------
  const dict = new Lexicon({ dir: repo, language: 'sl', tutorFile: join(mkdtempSync(join(tmpdir(), 'lani-smoke-forms-')), 'lexicon.json') })
  const made = contentLines({
    language: 'sl',
    scenes: [{
      id: 'test', dialogs: [{
        lines: [
          { sl: 'Kralj sedi za mizo.', en: 'The king sits at the table.' },
          { sl: 'Mačka sedi na oknu.', en: 'The cat is sitting on the windowsill.' },
          { sl: 'Sedi, prosim!', en: 'Sit down, please!', choices: [{ sl: 'Sedim na klopi.', en: 'I am sitting on the bench.', ok: false }] },
          { sl: 'Krpan je pojedel kruh.', en: 'Krpan ate the bread.' },
          { sl: 'Muca spi pri hiši.', en: 'The cat sleeps by the house.' },
          { sl: 'Je {n} ovc.', en: 'There are {n} sheep.' },
        ],
      }],
    }],
    stories: [], readings: [], grammar: [], packs: [],
  })
  const c = { dict, book: new FormsBook({ dir: mkdtempSync(join(tmpdir(), 'lani-smoke-forms-book-')), language: 'sl' }), pages: new Set(['glagoli-sedanjik', 'velelnik', 'pretekli-cas', 'dvojina', 'mestnik', 'dajalnik', 'mnozina', 'imenovalnik', 'rodilnik', 'tozilnik', 'orodnik', 'biti', 'imeti-iti', 'zanikanje', 'prihodnjik']), lines: new LineIndex(made), language: 'sl' }
  const texts = (e: LemmaForms | undefined, key: string) => (slot(e, key)?.lines ?? []).map(l => l.sl)
  const sesti2 = formsOf(c, 'sesti')[0]
  const sedeti = formsOf(c, 'sedeti')[0]
  const jesti = formsOf(c, 'jesti')[0]
  const hisa2 = formsOf(c, 'hiša')[0]
  check(
    'lines: "Kralj sedi za mizo" (The king sits) is sedeti\'s present, "Sedi, prosim!" (Sit down) sesti\'s imperative, never the other way; no wrong choice, no placeholder',
    made.length === 5 && same(texts(sesti2, 'imp.2sg'), ['Sedi, prosim!']) && !sesti2.slots.some(s => (s.lines ?? []).some(l => /Kralj|Mačka/.test(l.sl))) &&
      same(texts(sedeti, 'pres.3sg'), ['Kralj sedi za mizo.', 'Mačka sedi na oknu.']) && texts(sedeti, 'imp.2sg').length === 0 && texts(sedeti, 'pres.1sg').length === 0,
    { sesti: sesti2.slots.filter(s => s.lines).map(s => [s.key, s.lines]), sedeti: sedeti.slots.filter(s => s.lines).map(s => [s.key, s.lines]) },
  )
  check(
    'lines: "je" beside an l-form is biti\'s, not jesti\'s; "pri hiši" is the locative (mestnik), not the dative',
    !jesti.slots.some(s => s.lines?.length) && same(texts(hisa2, 'loc.sg'), ['Muca spi pri hiši.']) && texts(hisa2, 'dat.sg').length === 0,
    { jesti: jesti.slots.filter(s => s.lines).map(s => s.key), hisa: hisa2.slots.filter(s => s.lines).map(s => s.key) },
  )

  // --- a review's forms ----------------------------------------------------------------------------------------
  const before = channelEvents.length
  const rv = await fetch(`${base}/reviews`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({
      results: [{ item_id: 'vocab_word_sesti', quality: 3 }],
      duration_minutes: 1,
      forms: [{ item_id: 'vocab_word_sesti', key: 'pres.1sg', page: 'glagoli-sedanjik', right: false, answer: 'Jaz sedi na klop.', expected: 'Jaz sedem na klop.' }],
    }),
  })
  await Bun.sleep(100)
  const note = channelEvents.slice(before).find(n => n.params?.meta?.kind === 'review_done')?.params?.content ?? ''
  check('POST /reviews: a review\'s forms reach the tutor in its note, the wrong ones with what was right and the page', rv.ok && /Forms of words asked: 1, 1 wrong: "Jaz sedi na klop\." for "Jaz sedem na klop\." \(glagoli-sedanjik/.test(note), note)

  // --- the forms book (lexicon/sl.forms.json): hand-checked tables and the verb pairs --------------------------------
  const file = JSON.parse(readFileSync(join(repo, 'sl.forms.json'), 'utf8'))
  const v = validateFormsBook(file)
  check('the forms book (sl.forms.json) validates, every entry, and says a machine wrote it, not reviewed', v.errors.length === 0 && v.tables.length >= 13 && v.partners.length >= 20 && file.meta.reviewed === false && file.meta.machine_written === true && /not reviewed/.test(file.meta.review), v.errors)
  const b = await forms('w=biti&w=stati&w=sesti')
  const biti = b.body.words?.[0]?.entries?.[0] as LemmaForms | undefined
  check(
    'GET /forms: biti\'s hand-checked table (je, bom; nisem on zanikanje, needing biti; bodo, also bojo), without biti, to beat\'s bije',
    biti?.table === 'checked' && same(biti.gloss, ['to be']) && biti.aspect === 'impf' && same(slot(biti, 'pres.3sg')?.forms, ['je']) && slot(biti, 'pres.3sg')?.page === 'biti' &&
      same(slot(biti, 'fut.1sg')?.forms, ['bom']) && slot(biti, 'fut.1sg')?.page === 'prihodnjik' &&
      same(slot(biti, 'neg.1sg')?.forms, ['nisem']) && slot(biti, 'neg.1sg')?.page === 'zanikanje' && same(slot(biti, 'neg.1sg')?.needs, ['biti']) &&
      same(slot(biti, 'fut.3pl')?.forms, ['bodo']) && same(slot(biti, 'fut.3pl')?.also, ['bojo']) &&
      !biti.slots.some(s => [...s.forms, ...(s.also ?? [])].includes('bije')),
    biti?.slots.map(s => `${s.key}:${s.forms.join('/')}${s.also ? `+${s.also}` : ''}`),
  )
  const stati = b.body.words?.[1]
  const sesti3 = b.body.words?.[2]?.entries?.[0] as LemmaForms | undefined
  check('GET /forms: stati (to cost and to stand in one entry) is left out; sesti is perfective, its partner sedeti (a state)', same(stati?.entries, []) && sesti3?.aspect === 'pf' && sesti3.partner?.lemma === 'sedeti' && sesti3.partner.aspect === 'impf' && sesti3.partner.kind === 'position', { stati, partner: sesti3?.partner })
  const look = (await (await fetch(`${base}/lookup?${new URLSearchParams({ w: 'sedite', line: 'Sedite, sedite.', en: 'Sit down, sit down' })}`, { headers: auth })).json()) as any
  const [first, second] = look.entries ?? []
  check(
    'GET /lookup: "sedite" in "Sit down, sit down" is sesti here, its partner sedeti (a state) marked as its partner',
    first?.lemma === 'sesti' && first.here === true && first.aspect === 'pf' && first.partner?.lemma === 'sedeti' && first.partner.kind === 'position' &&
      second?.lemma === 'sedeti' && !second.here && second.partner?.lemma === 'sesti' && second.aspect === 'impf',
    look.entries?.map((e: any) => [e.lemma, e.here, e.aspect, e.partner]),
  )
  const king = (await (await fetch(`${base}/lookup?${new URLSearchParams({ w: 'sedi', line: 'Kralj sedi za mizo.', en: 'The king sits at the table.' })}`, { headers: auth })).json()) as any
  check('GET /lookup: "sedi" in "The king sits at the table" isn\'t sesti here', !king.entries?.some((e: any) => e.lemma === 'sesti' && e.here), king.entries?.map((e: any) => [e.lemma, e.here]))
}
