// Smoke checks: a dictionary's meanings in the learner's base language (bridge/src/lexicon-meanings.ts,
// companion/bin/lexicon-build --index/--meanings): reading Wiktionary's extracts, picking a meaning (the base's own
// Wiktionary, a table between the two languages, through English), and the Lexicon serving them with the English as
// the marked fallback. No bridge: the functions, and the builder run on tiny extracts (lexicon-fixture.ts).
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { Lexicon, lookup, validateExtra, type LexiconFile } from '../../src/lexicon'
import { fetchLexicon, parseLexicon } from '../../src/lexicon-download'
import type { LoadedPack } from '../../src/packs'
import {
  chooseMeaning, cleanGloss, englishHeads, glossesOf, indexRow, listedBy, meaningPart, pivot, plainWord, tablesOf, together, translationWords, unshare,
  type IndexRow, type Table,
} from '../../src/lexicon-meanings'
import { check, dataDir, tempDir } from './harness'
import { lexiconFixture } from './lexicon-fixture'

export default async function meanings() {
  // --- reading an extract ---------------------------------------------------------------------------------------
  check('plainWord: Slovene without tonal accents, the caron kept, pronunciation letters spelled: dóber → dober, híša → hiša, nəčke → nečke', plainWord('dóber', 'sl') === 'dober' && plainWord('híša', 'sl') === 'hiša' && plainWord('nəčke', 'sl') === 'nečke' && plainWord('Haus', 'de') === 'Haus')
  const words = translationWords('work , be operable', 'en')
  check('translationWords: "work , be operable" is two; no numbers, no long phrases, no trailing "!"', JSON.stringify(words) === '["work","be operable"]' && translationWords('auf Wiedersehen!', 'de')[0] === 'auf Wiedersehen' && !translationWords('1st', 'en').length && !translationWords('a b c d e', 'en').length, words)
  const de = tablesOf({
    word: 'Bank', pos: 'noun', lang_code: 'de',
    translations: [
      { lang_code: 'it', word: 'banca', sense: 'Geldinstitut', sense_index: '2' },
      { lang_code: 'sl', word: 'klop', sense: 'Sitzgelegenheit', sense_index: '1' },
      { lang_code: 'it', word: 'panca', sense: 'Sitzgelegenheit', sense_index: '1' },
      { lang_code: 'fr', word: 'banc', sense: 'Sitzgelegenheit', sense_index: '1' },
      { lang_code: 'en', word: 'bench', sense: 'Sitzgelegenheit', sense_index: '1', raw_tags: ['besonders Schottland'] },
      { lang_code: 'it', word: 'banco', sense: 'Geldinstitut', sense_index: '2', tags: ['obsolete'] },
    ],
  }, 'de')
  check("tablesOf: German Wiktionary's tables by sense number, in order; only Lani's languages; no dialect, region or obsolete word", de.length === 2 && de[0].s === 'Sitzgelegenheit' && JSON.stringify(de[0].x) === '{"sl":["klop"],"it":["panca"]}' && JSON.stringify(de[1].x) === '{"it":["banca"]}', de)
  const itNoun = tablesOf({ word: 'andare', pos: 'noun', lang_code: 'it', translations: [{ lang_code: 'de', word: 'gehen', sense: '(verbo) muoversi' }, { lang_code: 'de', word: 'Gang', sense: 'modo di incedere' }] }, 'it')
  check("tablesOf: Italian Wiktionary's \"(verbo) …\" tables go only to the verb", itNoun.length === 1 && itNoun[0].x.de?.[0] === 'Gang', itNoun)
  const en = tablesOf({ word: 'house', pos: 'noun', lang_code: 'en', translations: [{ lang_code: 'sl', word: 'híša', sense: 'human abode' }], senses: [{ glosses: ['A dynasty.'], translations: [{ lang_code: 'de', word: 'Haus', sense: 'dynasty' }] }] }, 'en')
  check("tablesOf: English Wiktionary's tables, attached to a sense (with its gloss) or not", en.length === 2 && en[0].x.sl?.[0] === 'hiša' && en[1].g === 'A dynasty.', en)
  const glosses = glossesOf({ word: 'casa', pos: 'noun', lang_code: 'it', senses: [{ glosses: ['Genitiv Singular von X'], tags: ['form-of'], form_of: [{}] }, { glosses: ['old house'], tags: ['archaic'] }, { glosses: ['Taking the form of:', 'a house'] }, { glosses: ['definizione mancante; se vuoi, aggiungila tu'] }] })
  check('glossesOf: meanings only, rare ones last, a heading as its subsense, no placeholder', JSON.stringify(glosses) === '["a house","old house"]', glosses)
  const row = indexRow({ word: 'hiša', pos: 'noun', lang_code: 'sl', senses: [{ glosses: ['Haus'] }] }, 'de')
  const own = indexRow({ word: 'Haus', pos: 'noun', lang_code: 'de', senses: [{ glosses: ['Gebäude'] }], translations: [{ lang_code: 'sl', word: 'hiša', sense: 'Gebäude' }] }, 'de')
  check("indexRow: another language's word gives its glosses; the Wiktionary's own word its tables and senses; other languages nothing", row?.g?.[0] === 'Haus' && !row.t && own?.t?.length === 1 && own.n === 1 && !indexRow({ word: 'maison', pos: 'noun', lang_code: 'fr' }, 'de'), { row, own })
  const shared = { s: 'grazie', x: { sl: ['hvala'] } }
  const rows: IndexRow[] = [{ w: 'grazie', l: 'it', p: 'intj', t: [shared], n: 2 }, { w: 'grazie', l: 'it', p: 'noun', t: [shared, { s: 'favore', x: { de: ['Gunst'] } }], n: 1 }]
  const kept = unshare(rows)
  check('unshare: a table copied to every part of speech of a page stays with the one of the most senses', kept[0].t?.length === 1 && kept[1].t?.length === 1 && kept[1].t[0].s === 'favore', kept)

  // --- picking a meaning ----------------------------------------------------------------------------------------
  check('cleanGloss: no articles, gender marks or explanations: "das Haus, das Zuhause" → Haus, Zuhause; "Engel m"; "Bergsteigerin: weibliche Person"', cleanGloss('das Haus, das Zuhause', 'de') === 'Haus, Zuhause' && cleanGloss('Engel m', 'de') === 'Engel' && cleanGloss('Bergsteigerin: weibliche Person', 'de') === 'Bergsteigerin' && cleanGloss("l'acqua", 'it') === 'acqua')
  check('meaningPart: German Wiktionary\'s "definition; translation" → the translation; "Haus; Heim" stays', meaningPart('größeres fließendes Gewässer; Fluss') === 'Fluss' && meaningPart('Haus; Heim') === 'Haus; Heim')
  check('englishHeads: a gloss\'s English headwords, "to" left out of a verb\'s', JSON.stringify(englishHeads('to talk, to speak (a language)', 'verb')) === '["talk","speak"]' && JSON.stringify(englishHeads('bank (financial institution)', 'noun')) === '["bank"]')
  const tables = new Map<string, Table[]>([
    ['bank|noun', [{ s: 'institution', x: { it: ['banca'], sl: ['banka'] } }, { s: 'edge of river', x: { it: ['riva'], sl: ['breg'] } }]],
    ['present|noun', [{ s: 'current time', x: { sl: ['sedanjost'] } }]],
    ['gift|noun', [{ s: 'present', x: { it: ['regalo'], sl: ['darilo'] } }]],
  ])
  const through = (lemma: string, gloss: string) => pivot('it', 'sl', lemma, 'noun', [gloss], tables)
  const [banca, sponda, banco, regalo] = [through('banca', 'bank'), through('sponda', 'bank (of a river)'), through('banco', 'bank'), through('regalo', 'present, gift')]
  check('pivot: the table that lists the lemma (banca → banka), else the one its gloss singles out (sponda, "of a river" → breg)', banca.confirmed && banca.words[0] === 'banka' && !sponda.confirmed && sponda.words[0] === 'breg', { banca, sponda })
  check('pivot: never a guess between two "bank"s (banco → none), nor an English word\'s only table ("present": the time, for regalo → darilo)', !banco.words.length && regalo.words.join() === 'darilo', { banco, regalo })
  check('listedBy: the base words whose table lists the word first, the first sense and the most translated first (danke before merci)', JSON.stringify(listedBy([{ w: 'laufen', sense: 3, place: 1, exact: true, size: 900 }, { w: 'merci', sense: 0, place: 0, exact: true, size: 3 }, { w: 'danke', sense: 0, place: 0, exact: false, size: 200 }])) === '["danke","merci"]')
  check('together: the base words met with the word in tables, the most often first', JSON.stringify(together([{ w: 'gozd', score: 1 }, { w: 'les', score: 0.5 }, { w: 'gozd', score: 0.5 }])) === '["gozd","les"]')
  const order = chooseMeaning({ direct: ['männliche oder weibliche Person'], own: [{ s: '1', x: { de: ['Bank'] } }], pivot: () => ({ words: ['Geldinstitut'], confirmed: true }) }, 'de')
  const direct = chooseMeaning({ direct: ['das Haus, das Zuhause'], pivot: () => ({ words: ['Haus'], confirmed: true }) }, 'de')
  check('chooseMeaning: the base\'s own gloss first when it reads like a translation; a definition only last; then the confirmed pivot, then the tables', direct?.via === 'direct' && direct.gloss[0] === 'Haus, Zuhause' && order?.via === 'pivot' && order.gloss[0] === 'Geldinstitut', { direct, order })

  // --- the builder on tiny extracts ---------------------------------------------------------------------------------
  const built = lexiconFixture(tempDir)
  const it = JSON.parse(readFileSync(built.it, 'utf8')) as LexiconFile
  const lemma = (f: LexiconFile, w: string) => f.lemmas.find(l => l.lemma === w)!
  const m = (w: string, b: string) => lemma(it, w)?.meanings?.[b]
  check('lexicon-build: it←sl: casa is hiša and dom, through English (the tables that list casa)', m('casa', 'sl')?.gloss.join() === 'hiša,dom' && m('casa', 'sl')?.via === 'pivot', lemma(it, 'casa'))
  check("lexicon-build: it←de: casa is German Wiktionary's own \"Haus, Zuhause\" (direct); banca's German definition loses to the pivot's Bank", m('casa', 'de')?.gloss[0] === 'Haus, Zuhause' && m('casa', 'de')?.via === 'direct' && m('banca', 'de')?.gloss[0] === 'Bank', [lemma(it, 'casa'), lemma(it, 'banca')])
  check('lexicon-build: only clear matches: banco ("bank") has none; regalo is darilo, not the time; sponda is breg by its "of a river"; no dialect word', !lemma(it, 'banco').meanings && m('regalo', 'sl')?.gloss.join() === 'darilo' && m('regalo', 'de')?.gloss.join() === 'Geschenk' && m('sponda', 'sl')?.gloss[0] === 'breg' && !JSON.stringify(it).includes('Huus'), it.lemmas)
  check('lexicon-build: essere has no Slovene meaning (English "be" lists none), a German one', !m('essere', 'sl') && m('essere', 'de')?.gloss[0] === 'sein', lemma(it, 'essere'))
  const grammarOf = (form: string) => it.forms[form]?.map(([i, g]) => `${it.lemmas[i].lemma}: ${g === undefined ? null : it.grammar[g]}`)
  check(
    "lexicon-build: an Italian verb's present, the indicative left out: mangio is mangiare's first-person singular present, mangia also its imperative; mangiare is essen (German Wiktionary's own) and jesti (through English)",
    JSON.stringify(grammarOf('mangio')) === '["mangiare: first-person singular present"]' && JSON.stringify(grammarOf('mangia')) === '["mangiare: third-person singular present; second-person singular imperative"]' && m('mangiare', 'de')?.gloss[0] === 'essen' && m('mangiare', 'de')?.via === 'direct' && m('mangiare', 'sl')?.gloss[0] === 'jesti',
    { mangio: grammarOf('mangio'), mangia: grammarOf('mangia'), mangiare: lemma(it, 'mangiare') },
  )
  const sl = JSON.parse(readFileSync(built.sl, 'utf8')) as LexiconFile
  check("lexicon-build: sl←de: hiša is Haus (German Wiktionary: \"…; Haus\"); sl←it: casa", lemma(sl, 'hiša')?.meanings?.de?.gloss[0] === 'Haus' && lemma(sl, 'hiša')?.meanings?.de?.via === 'direct' && lemma(sl, 'hiša')?.meanings?.it?.gloss[0] === 'casa', lemma(sl, 'hiša'))
  const manifest = JSON.parse(readFileSync(join(built.dir, 'it.manifest.json'), 'utf8'))
  check('lexicon-build --manifest: the bases with their coverage, of the lemmas and of the content\'s words, and how found, the sources and the attribution', existsSync(join(built.dir, 'it.json.gz')) && manifest.language === 'it' && manifest.bases?.sl?.lemmas === 7 && manifest.bases.en.lemmas === 9 && manifest.bases.de.via.direct === 2 && manifest.bases.de.content?.words > 0 && typeof manifest.bases.de.content.share === 'number' && manifest.sources?.some((s: any) => s.edition === 'de' && s.urls[0] === 'https://kaikki.org/dewiktionary/') && manifest.attribution === 'Wiktionary (CC BY-SA 4.0) via kaikki.org' && manifest.licence === 'CC BY-SA 4.0', manifest)
  check('lexicon-build: an old dictionary\'s rows stay as they were, the meanings added at their end (sl.json\'s diff: one line a lemma)', /^\{"lemma":"casa","pos":"noun","gloss":\["house","home"\],"gender":"f","meanings":/m.test(readFileSync(built.it, 'utf8')))

  // a local LANI_LEXICON_URL written with ~ (a profile's env: "~/.local/share/lani/lexicon") is the home's
  const home = tempDir('lani-smoke-lexicon-home-')
  mkdirSync(join(home, 'lex'))
  for (const f of ['it.json.gz', 'it.manifest.json']) writeFileSync(join(home, 'lex', f), readFileSync(join(built.dir, f)))
  const fetched = await fetchLexicon({ url: '~/lex', language: 'it', cacheDir: tempDir('lani-smoke-lexicon-tilde-cache-'), home }, parseLexicon)
  check('fetchLexicon: a local directory written ~/lex is the home\'s: the dictionary is read and cached', !('error' in fetched) && fetched.from === 'download' && fetched.file.lemmas.length === 9, 'error' in fetched ? fetched.error : fetched.from)

  // --- the Lexicon serves them ------------------------------------------------------------------------------------
  const dir = tempDir('lani-smoke-meanings-dict-')
  Bun.write(join(dir, 'it.json'), readFileSync(built.it))
  const tutorFile = join(dataDir, 'fixture-glosses.json')
  const d = new Lexicon({ dir, language: 'it', base: 'sl', tutorFile })
  const [casa] = d.readings('case')
  check('Lexicon.readings: in the base asked for: case → casa, hiša (sl, pivot), the English kept; in German: Haus', casa?.lemma === 'casa' && casa.gloss[0] === 'hiša' && casa.glossLang === 'sl' && casa.via === 'pivot' && casa.en?.[0] === 'house' && d.readings('casa', 'de')[0]?.gloss[0] === 'Haus, Zuhause', casa)
  const [sono] = d.readings('sono')
  check('Lexicon.readings: no meaning in the base: the English, marked fallback; English asked for: no mark', sono?.lemma === 'essere' && sono.gloss[0] === 'to be' && sono.fallback === true && !sono.glossLang && !d.readings('sono', 'en')[0]?.fallback, sono)
  d.publish({ form: 'sono', lemma: 'essere', pos: 'verb', gloss: ['biti'], gloss_lang: 'sl' })
  const filled = d.readings('sono')
  check("Lexicon.readings: the tutor's gloss in the base fills the gap: essere is biti (claude), with the dictionary's grammar, no entry of its own", filled.length === 1 && filled[0].gloss[0] === 'biti' && filled[0].via === 'claude' && filled[0].glossLang === 'sl' && filled[0].source === 'wiktionary' && filled[0].grammar !== null && !filled[0].fallback, filled)
  check("Lexicon.readings: the tutor's Slovene gloss isn't a German one: in German essere stays sein", d.readings('sono', 'de')[0]?.gloss[0] === 'sein')
  check('Lexicon.status: the bases the dictionary has meanings in, and how many in the learner\'s', JSON.stringify(d.status().bases) === '["en","sl","de"]' && d.status().base_meanings === 7 && d.meaningsIn('de') === 8, d.status())
  // a learner of Italian from German (the second learner's son): a lemma, an inflected form, their German meaning
  const fromGerman = new Lexicon({ dir, language: 'it', base: 'de', tutorFile: join(dataDir, 'fixture-glosses-de.json') })
  const eats = lookup('mangio', { line: 'Io mangio il frico.', en: 'I eat frico.' }, { lexicon: fromGerman, base: 'de', packs: [], people: [], items: {} })
  const house = lookup('casa', {}, { lexicon: fromGerman, base: 'de', packs: [], people: [], items: {} }).entries[0]
  check(
    'lookup, Italian from German: "mangio" is mangiare, first-person singular present, essen (German Wiktionary, direct); casa is Haus; the attribution with them',
    eats.entries[0]?.lemma === 'mangiare' && eats.entries[0].grammar === 'first-person singular present' && eats.entries[0].gloss[0] === 'essen' && eats.entries[0].gloss_lang === 'de' && eats.entries[0].gloss_via === 'direct' && eats.entries[0].gloss_en?.[0] === 'to eat' && !eats.entries[0].fallback && eats.entries[0].item_id === 'vocab_word_mangiare' && eats.attribution === 'Wiktionary (CC BY-SA 4.0) via kaikki.org' && house?.gloss[0] === 'Haus, Zuhause' && house.gloss_lang === 'de',
    { eats, house },
  )
  // the supplement's own meanings (it.extra.json: a word Wiktionary lacks, meant in German by the one who wrote it)
  const withExtra = tempDir('lani-smoke-extra-meanings-')
  writeFileSync(join(withExtra, 'it.json'), readFileSync(built.it))
  writeFileSync(join(withExtra, 'it.extra.json'), JSON.stringify({
    meta: { language: 'it', reviewed: false, note: 'machine-written' },
    entries: [
      { lemma: 'frico', pos: 'noun', gender: 'm', gloss: ['frico (a Friulian dish of cheese and potatoes)'], meanings: { de: ['Frico (Käse-Kartoffel-Gericht)'] }, forms: { frico: 'singular' } },
      { lemma: 'casa', pos: 'noun', gender: 'f', gloss: ['house; home'], override: true, meanings: { de: ['Haus; Zuhause'] }, forms: { casa: null } },
      { lemma: 'eh', pos: 'intj', gloss: ['eh; well'], forms: { eh: null }, meanings: { de: 'nicht eine Liste' } },
    ],
  }))
  const x = new Lexicon({ dir: withExtra, language: 'it', base: 'de', tutorFile: join(dataDir, 'fixture-glosses-x.json') })
  const [frico] = x.readings('frico', 'de')
  const [fricoSl] = x.readings('frico', 'sl')
  const [casaDe] = x.readings('case', 'de')
  check(
    "Lexicon: the supplement's own meaning in the base, flagged machine-written (claude): frico is Frico in German; in Slovene, which it hasn't, the English (fallback)",
    frico?.source === 'extra' && frico.gloss[0] === 'Frico (Käse-Kartoffel-Gericht)' && frico.glossLang === 'de' && frico.via === 'claude' && frico.en?.[0].startsWith('frico (a Friulian') && fricoSl?.fallback === true && fricoSl.gloss[0].startsWith('frico (a Friulian'),
    { frico, fricoSl },
  )
  check("Lexicon: an override's own meaning replaces the dictionary's (casa: Haus; Zuhause), Wiktionary's grammar kept", casaDe?.gloss[0] === 'Haus; Zuhause' && casaDe.via === 'claude' && casaDe.grammar === 'plural', casaDe)
  // an elided word is the word after it, unless the whole is an entry of its own: a name only when written as one
  const elided = tempDir('lani-smoke-elided-')
  writeFileSync(join(elided, 'it.json'), JSON.stringify({
    meta: { language: 'it', source: 'fixture', licence: 'CC BY-SA 4.0', url: '', built: '', counts: {} }, grammar: [],
    lemmas: [{ lemma: 'acqua', pos: 'noun', gloss: ['water'] }, { lemma: "Dell'Acqua", pos: 'name', gloss: ['a surname'] }],
    forms: { acqua: [[0]], "dell'acqua": [[1]] },
  }))
  const el = new Lexicon({ dir: elided, language: 'it', tutorFile: join(dataDir, 'fixture-glosses-el.json') })
  const small = lookup("dell'acqua", { line: "Un bicchiere dell'acqua." }, { lexicon: el, base: 'en', packs: [], people: [], items: {} }).entries
  const named = lookup("Dell'Acqua", { line: "Il signor Dell'Acqua." }, { lexicon: el, base: 'en', packs: [], people: [], items: {} }).entries
  check("lookup: dell'acqua is acqua, not the surname Dell'Acqua; written capitalized, the surname", small[0]?.lemma === 'acqua' && small.length === 1 && named[0]?.lemma === "Dell'Acqua", { small, named })
  check('validateExtra: an entry whose meanings are not a list is left out, the others kept', validateExtra(JSON.parse(readFileSync(join(withExtra, 'it.extra.json'), 'utf8'))).entries.length === 2 && !x.readings('eh', 'de').length)
  const noSlovene = { id: 'casa-pack', language: 'it', level: 'A1', title: 'Casa', source: 'curated', words: [{ id: 'casa', it: 'la casa', en: 'house', emoji: '🏠', plural: 'le case' }] } as unknown as LoadedPack
  const [pack] = lookup('case', {}, { lexicon: d, base: 'sl', packs: [noSlovene], people: [], items: {} }).entries
  check("lookup: a pack word without the learner's base (Italian and English only) takes the dictionary's meaning in it: la casa → hiša", pack?.source === 'pack' && pack.emoji === '🏠' && pack.gloss[0] === 'hiša' && pack.gloss_lang === 'sl' && pack.gloss_en?.[0] === 'house' && !pack.fallback, pack)
  const [english] = lookup('case', {}, { lexicon: new Lexicon({ dir: tempDir('lani-smoke-no-dict-'), language: 'it', base: 'sl', tutorFile }), base: 'sl', packs: [noSlovene], people: [], items: {} }).entries
  check('lookup: without a dictionary meaning either, the pack\'s English, marked fallback', english?.gloss[0] === 'house' && english.gloss_lang === 'en' && english.fallback === true, english)
}
