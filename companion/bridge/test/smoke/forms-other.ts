// Smoke checks: Italian's, German's and English's word forms (paradigms.ts; companion/README.md, "Word forms", "Other
// languages"): Wiktionary's table rows read into each language's slots, the grammar book page each slot counts on, the
// paradigms files (lexicon/<language>.paradigms.json, companion/bin/forms-build), and a word's forms with the content's
// lines. The routes (GET /forms with v=2, an older app's answer without it) are checked in the culture villages' sections.
import { readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { contentLines, formsOf, LineIndex, type LemmaForms } from '../../src/forms'
import { LANG_KEYS, pageOfIn, Paradigms, slotOfTags, validateParadigms, type ParadigmEntry } from '../../src/paradigms'
import { check } from './harness'

const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b)
const slot = (e: LemmaForms | undefined, key: string) => e?.slots.find(s => s.key === key)
const formsAt = (e: ParadigmEntry) => (k: string) => {
  const v = e.forms[k]
  return v === undefined ? [] : typeof v === 'string' ? [v.toLowerCase()] : v.map(f => f.toLowerCase())
}

export default async function formsOtherSection() {
  const repo = resolve(import.meta.dir, '../../../lexicon')
  const grammarDir = resolve(import.meta.dir, '../../../grammar')

  // --- Wiktionary's rows into slots ----------------------------------------------------------------------------------
  check(
    "slotOfTags (it): the present, the subjunctive, the imperative (not the negative, not Lei's), the participle and the gerund; a compound tense's row none",
    slotOfTags('it', 'verb', ['first-person', 'indicative', 'present', 'singular']) === 'pres.1sg' &&
      slotOfTags('it', 'verb', ['present', 'singular', 'subjunctive', 'third-person']) === 'subj.3sg' &&
      slotOfTags('it', 'verb', ['imperfect', 'plural', 'subjunctive', 'third-person']) === 'subji.3pl' &&
      slotOfTags('it', 'verb', ['historic', 'indicative', 'past', 'singular', 'third-person']) === 'rem.3sg' &&
      slotOfTags('it', 'verb', ['imperative', 'second-person', 'singular']) === 'imp.2sg' &&
      slotOfTags('it', 'verb', ['imperative', 'negative', 'second-person', 'singular']) === null &&
      slotOfTags('it', 'verb', ['formal', 'imperative', 'second-person-semantically', 'singular', 'third-person']) === null &&
      slotOfTags('it', 'verb', ['participle', 'past']) === 'pp' &&
      slotOfTags('it', 'verb', ['gerund']) === 'ger' &&
      slotOfTags('it', 'verb', ['conditional', 'first-person', 'singular']) === 'cond.1sg' &&
      slotOfTags('it', 'verb', ['auxiliary']) === null &&
      slotOfTags('it', 'adj', ['feminine']) === 'adj.f.sg' &&
      slotOfTags('it', 'adj', ['masculine', 'plural']) === 'adj.m.pl' &&
      slotOfTags('it', 'noun', ['plural']) === 'pl',
  )
  check(
    "slotOfTags (de, en): German's preterite, Konjunktiv II, Partizip II, a noun's cases, the bare comparative (not a declined one), a subordinate clause's row none; English's be (am, is, was, were), past, participles, plural",
    slotOfTags('de', 'verb', ['past', 'singular', 'third-person']) === 'pret.3sg' &&
      slotOfTags('de', 'verb', ['first-person', 'singular', 'subjunctive-ii']) === 'konj2.1sg' &&
      slotOfTags('de', 'verb', ['first-person', 'singular', 'subjunctive-i']) === null &&
      slotOfTags('de', 'verb', ['present', 'singular', 'subordinate-clause', 'third-person']) === null &&
      slotOfTags('de', 'verb', ['participle', 'past']) === 'pp' &&
      slotOfTags('de', 'verb', ['imperative', 'singular']) === 'imp.2sg' &&
      slotOfTags('de', 'noun', ['dative', 'definite', 'plural']) === 'dat.pl' &&
      slotOfTags('de', 'adj', ['comparative']) === 'cmp' &&
      slotOfTags('de', 'adj', ['comparative', 'masculine', 'nominative', 'singular', 'strong']) === null &&
      slotOfTags('en', 'verb', ['first-person', 'present', 'singular']) === 'pres.1sg' &&
      slotOfTags('en', 'verb', ['present', 'singular', 'third-person']) === 'pres.3sg' &&
      slotOfTags('en', 'verb', ['first-person', 'past', 'singular']) === 'past.1sg' &&
      slotOfTags('en', 'verb', ['past', 'plural']) === 'past.2sg' &&
      slotOfTags('en', 'verb', ['past']) === 'past' &&
      slotOfTags('en', 'verb', ['participle', 'past']) === 'pp' &&
      slotOfTags('en', 'verb', ['participle', 'present']) === 'ing' &&
      slotOfTags('en', 'noun', ['plural']) === 'pl',
  )

  // --- pages ------------------------------------------------------------------------------------------------------
  const it = (lemma: string, forms: Record<string, string>) => (k: string) => (forms[k] ? [forms[k]] : [])
  const parlare = it('parlare', { 'pres.1sg': 'parlo', 'pres.2sg': 'parli', 'pres.3sg': 'parla', 'pres.1pl': 'parliamo', 'pres.2pl': 'parlate', 'pres.3pl': 'parlano', pp: 'parlato' })
  const cercare = it('cercare', { 'pres.1sg': 'cerco', 'pres.2sg': 'cerchi', 'pres.3sg': 'cerca', 'pres.1pl': 'cerchiamo', 'pres.2pl': 'cercate', 'pres.3pl': 'cercano', pp: 'cercato' })
  const finire = it('finire', { 'pres.1sg': 'finisco', 'pres.2sg': 'finisci', 'pres.3sg': 'finisce', 'pres.1pl': 'finiamo', 'pres.2pl': 'finite', 'pres.3pl': 'finiscono', pp: 'finito' })
  const fare = it('fare', { 'pres.1sg': 'faccio', 'pres.2sg': 'fai', 'pres.3sg': 'fa', 'pres.1pl': 'facciamo', 'pres.2pl': 'fate', 'pres.3pl': 'fanno', pp: 'fatto' })
  check(
    "pageOfIn (it): a regular present (parlare, cercare's cerchi, finire's -isc-) on presente-regolare, fare's on verbi-irregolari, essere's on essere-avere, potere's on modali; fatto on participi-irregolari (needing passato-prossimo)",
    pageOfIn('it', 'parlare', 'verb', 'pres.1sg', parlare)?.page === 'presente-regolare' &&
      pageOfIn('it', 'cercare', 'verb', 'pres.2sg', cercare)?.page === 'presente-regolare' &&
      pageOfIn('it', 'finire', 'verb', 'pres.3pl', finire)?.page === 'presente-regolare' &&
      pageOfIn('it', 'fare', 'verb', 'pres.1sg', fare)?.page === 'verbi-irregolari' &&
      pageOfIn('it', 'essere', 'verb', 'pres.3sg', () => [])?.page === 'essere-avere' &&
      pageOfIn('it', 'potere', 'verb', 'pres.1sg', () => [])?.page === 'modali' &&
      pageOfIn('it', 'parlare', 'verb', 'pp', parlare)?.page === 'passato-prossimo' &&
      same(pageOfIn('it', 'fare', 'verb', 'pp', fare), { page: 'participi-irregolari', needs: ['passato-prossimo'] }) &&
      pageOfIn('it', 'casa', 'noun', 'pl', () => [])?.page === 'plurale',
  )
  const fahren = (k: string) => ({ 'pres.2sg': ['fährst'], 'pres.3sg': ['fährt'], 'pres.1sg': ['fahre'] })[k] ?? []
  const abfahren = (k: string) => ({ 'pres.3sg': ['fährt ab'] })[k] ?? []
  const spielen = (k: string) => ({ 'pres.3sg': ['spielt'] })[k] ?? []
  const junge = (k: string) => ({ 'nom.sg': ['junge'], 'acc.sg': ['jungen'], 'dat.pl': ['jungen'] })[k] ?? []
  const haus = (k: string) => ({ 'nom.sg': ['haus'], 'gen.sg': ['hauses'], 'dat.pl': ['häusern'] })[k] ?? []
  check(
    "pageOfIn (de, en): fahren's du and er forms (and abfahren's) on vokalwechsel, its ich on praesens, spielen's on praesens; Junge's den Jungen on n-deklination (needing akkusativ), Häusern on dativ (needing plural); be's on to-be and was-were, a participle on present-perfect",
    same(pageOfIn('de', 'fahren', 'verb', 'pres.3sg', fahren), { page: 'vokalwechsel', needs: ['praesens'] }) &&
      pageOfIn('de', 'abfahren', 'verb', 'pres.3sg', abfahren)?.page === 'vokalwechsel' &&
      pageOfIn('de', 'fahren', 'verb', 'pres.1sg', fahren)?.page === 'praesens' &&
      pageOfIn('de', 'spielen', 'verb', 'pres.3sg', spielen)?.page === 'praesens' &&
      pageOfIn('de', 'sein', 'verb', 'pret.1sg', () => [])?.page === 'praeteritum-kurz' &&
      pageOfIn('de', 'gehen', 'verb', 'pret.1sg', () => [])?.page === 'praeteritum' &&
      pageOfIn('de', 'gehen', 'verb', 'konj2.1sg', () => []) === null &&
      same(pageOfIn('de', 'Junge', 'noun', 'acc.sg', junge, 'm'), { page: 'n-deklination', needs: ['akkusativ'] }) &&
      same(pageOfIn('de', 'Haus', 'noun', 'dat.pl', haus, 'n'), { page: 'dativ', needs: ['plural'] }) &&
      pageOfIn('de', 'Haus', 'noun', 'gen.sg', haus, 'n')?.page === 'genitiv' &&
      pageOfIn('en', 'be', 'verb', 'pres.1sg', () => [])?.page === 'to-be' &&
      pageOfIn('en', 'be', 'verb', 'past.2sg', () => [])?.page === 'was-were' &&
      pageOfIn('en', 'walk', 'verb', 'past', () => [])?.page === 'past-simple' &&
      pageOfIn('en', 'walk', 'verb', 'pp', () => [])?.page === 'present-perfect' &&
      pageOfIn('en', 'walk', 'verb', 'pres.1sg', () => []) === null,
  )
  const pagesOf = (lang: string) => new Set(readGrammarIds(grammarDir, lang))
  for (const lang of ['it', 'de', 'en'] as const) {
    const book = pagesOf(lang)
    const used = new Set<string>()
    for (const pos of ['verb', 'noun', 'adj'] as const)
      for (const k of LANG_KEYS[lang][pos]) for (const lemma of ['x', 'essere', 'sein', 'be', 'have', 'werden', 'können', 'potere']) {
        const pg = pageOfIn(lang, lemma, pos, k, () => [], 'm')
        if (pg) [pg.page, ...pg.needs].forEach(p => used.add(p))
      }
    const missing = [...used].filter(p => !book.has(p))
    check(`pageOfIn (${lang}): every page a slot counts on is in the ${lang} grammar book`, missing.length === 0, missing)
  }

  // --- the files --------------------------------------------------------------------------------------------------
  const wrong = validateParadigms({ meta: { language: 'it' }, entries: [{ lemma: 'casa', pos: 'noun', forms: { sg: 'casa', pl: 'case' } }, { lemma: 'gatto', pos: 'noun', forms: { 'loc.sg': 'gatto' } }, { lemma: '', pos: 'noun', forms: {} }] }, 'it')
  check('validateParadigms: a slot the language lacks (an Italian locative) and an empty lemma are reported and left out, the rest kept', wrong.entries.length === 1 && wrong.entries[0].lemma === 'casa' && wrong.errors.length === 2, wrong.errors)
  const files: Record<string, Paradigms> = {}
  for (const lang of ['it', 'de', 'en'] as const) {
    const raw = JSON.parse(readFileSync(join(repo, `${lang}.paradigms.json`), 'utf8'))
    const v = validateParadigms(raw, lang)
    files[lang] = new Paradigms({ dir: repo, language: lang })
    check(
      `lexicon/${lang}.paradigms.json: every entry valid, its meta names Wiktionary's licence and attribution, at least 2,000 entries`,
      v.errors.length === 0 && v.entries.length >= 2000 && raw.meta.licence === 'CC BY-SA 4.0' && raw.meta.attribution === 'Wiktionary (CC BY-SA 4.0) via kaikki.org' && files[lang].size() === v.entries.length,
      { errors: v.errors.slice(0, 5), entries: v.entries.length, meta: raw.meta },
    )
  }
  const one = (lang: string, lemma: string, pos: string) => files[lang].entriesOf(lemma).find(e => e.pos === pos)
  const andare = one('it', 'andare', 'verb')
  const essere = one('it', 'essere', 'verb')
  const bello = one('it', 'bello', 'adj')
  check(
    "it.paradigms.json: andare's va (no stress mark), vado alone (not the old vo), essere's sono alone (not sò), its auxiliary essere; bello's bella and belli, bei, begli; uovo's plural uova",
    andare?.forms['pres.3sg'] === 'va' && andare.forms['pres.1sg'] === 'vado' && same(andare.aux, ['essere']) && essere?.forms['pres.1sg'] === 'sono' && essere.forms['pres.3sg'] === 'è' &&
      bello?.forms['adj.f.sg'] === 'bella' && same(bello.forms['adj.m.pl'], ['belli', 'bei', 'begli']) && one('it', 'uovo', 'noun')?.forms.pl === 'uova',
    { andare, essere, bello },
  )
  const hausE = one('de', 'Haus', 'noun')
  const fahrenE = one('de', 'fahren', 'verb')
  check(
    "de.paradigms.json: Haus neuter, Häusern its dative plural, dem Hause accepted (not shown); fahren's fährt, fuhr, gefahren with sein (and haben); sein's Konjunktiv II wäre",
    hausE?.gender === 'n' && hausE.forms['dat.pl'] === 'Häusern' && hausE.forms['dat.sg'] === 'Haus' && hausE.also?.['dat.sg'] === 'Hause' &&
      fahrenE?.forms['pres.3sg'] === 'fährt' && fahrenE.forms['pret.3sg'] === 'fuhr' && fahrenE.forms.pp === 'gefahren' && !!fahrenE.aux?.includes('sein') &&
      one('de', 'sein', 'verb')?.forms['konj2.1sg'] === 'wäre' && pageOfIn('de', 'fahren', 'verb', 'pres.3sg', formsAt(fahrenE))?.page === 'vokalwechsel',
    { hausE, fahrenE },
  )
  const go = one('en', 'go', 'verb')
  const be = one('en', 'be', 'verb')
  check(
    "en.paradigms.json: go's goes, went, gone, going; be's am, is, are, was, were; child's children; good's better, best",
    go?.forms['pres.3sg'] === 'goes' && go.forms.past === 'went' && go.forms.pp === 'gone' && go.forms.ing === 'going' &&
      be?.forms['pres.1sg'] === 'am' && be.forms['pres.3sg'] === 'is' && be.forms['pres.2sg'] === 'are' && be.forms['past.1sg'] === 'was' && be.forms['past.2sg'] === 'were' &&
      one('en', 'child', 'noun')?.forms.pl === 'children' && one('en', 'good', 'adj')?.forms.cmp === 'better' && one('en', 'good', 'adj')?.forms.sup === 'best',
    { go, be },
  )

  // --- a word's forms, with the content's lines -------------------------------------------------------------------
  const pages = (lang: string) => readGrammarIds(grammarDir, lang)
  const ctx = (lang: 'it' | 'de' | 'en', lines: { text: string; en: string; de?: string; sl?: string }[]) => ({
    dict: undefined as never, book: undefined as never, language: lang, paradigms: files[lang], pages: new Set(pages(lang)),
    lines: new LineIndex(contentLines({ language: lang, scenes: [{ id: 't', language: lang, dialogs: [{ lines: lines.map(l => ({ [lang]: l.text, en: l.en, ...(l.de ? { de: l.de } : {}), ...(l.sl ? { sl: l.sl } : {}) })) }] }], stories: [], readings: [], grammar: [], packs: [] })),
  })
  const mangiare = formsOf(ctx('it', [
    { text: 'Io mangio una mela.', en: 'I eat an apple.', de: 'Ich esse einen Apfel.', sl: 'Jem jabolko.' },
    { text: 'Penso che lui mangi troppo.', en: 'I think he eats too much.' },
    { text: 'Mangia la minestra!', en: 'Eat the soup!' },
  ]), 'mangiare')[0]
  check(
    "formsOf (it): mangiare's slots in the table's order with their pages, the present's line with its translations (sl too, for a learner reading Slovene); mangi after che the subjunctive's line, not the present's; Mangia! the imperative's",
    mangiare?.language === 'it' && mangiare.slots[0].key === 'pres.1sg' && slot(mangiare, 'pres.1sg')?.page === 'presente-regolare' && slot(mangiare, 'pres.1sg')?.lines?.[0]?.text === 'Io mangio una mela.' &&
      slot(mangiare, 'pres.1sg')?.lines?.[0]?.sl === 'Jem jabolko.' && slot(mangiare, 'subj.3sg')?.lines?.[0]?.form === 'mangi' && !slot(mangiare, 'pres.2sg')?.lines &&
      slot(mangiare, 'imp.2sg')?.lines?.[0]?.form === 'mangia' && !slot(mangiare, 'pres.3sg')?.lines && same(mangiare.aux, ['avere']),
    mangiare?.slots.filter(s => s.lines),
  )
  const eat = formsOf(ctx('en', [
    { text: 'She has eaten the cake.', en: 'She has eaten the cake.' },
    { text: 'She ate the cake.', en: 'She ate the cake.' },
    { text: 'They are eating now.', en: 'They are eating now.' },
    { text: 'I like eating apples.', en: 'I like eating apples.' },
  ]), 'eat').find(e => e.pos === 'verb')
  check(
    'formsOf (en): eaten after has the present perfect\'s line, ate the past\'s, are eating the continuous\'s; "I like eating" no line (a gerund)',
    slot(eat, 'pp')?.lines?.[0]?.form === 'eaten' && slot(eat, 'past')?.lines?.[0]?.form === 'ate' && slot(eat, 'ing')?.lines?.length === 1 && slot(eat, 'ing')?.lines?.[0]?.text === 'They are eating now.',
    eat?.slots.filter(s => s.lines),
  )
  const noBook = formsOf({ ...ctx('it', []), pages: new Set(['presente-regolare']) }, 'mangiare')[0]
  check('formsOf (it): only the slots whose page the book has (a book of one page: the present alone)', !!noBook && noBook.slots.every(s => s.page === 'presente-regolare') && noBook.slots.length === 6, noBook?.slots.map(s => s.key))
}

/** The ids of a language's curated grammar book pages (companion/grammar/<language>/). */
function readGrammarIds(dir: string, lang: string): string[] {
  return readdirSync(join(dir, lang)).filter(f => f.endsWith('.json')).map(f => f.replace(/\.json$/, ''))
}
