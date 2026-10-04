// Smoke checks: word lookup in the dialogs (GET /lookup), adding a word (POST /words) and the tutor's glosses
// (publish_gloss). Runs after the packs section: its packs (kruh, mleko …) are the curated ones here.
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { Lexicon, lookup as lookUp, normalizeForm, validateExtra, wordItemId, type LexiconFile } from '../../src/lexicon'
import { fetchLexicon, parseLexicon } from '../../src/lexicon-download'
import { validatePack, type LoadedPack } from '../../src/packs'
import { auth, base, channelEvents, check, client, dataDir, guideOf, publishLexicon, tempDir } from './harness'

const lookup = async (w: string, line?: string, en?: string) => {
  const q = new URLSearchParams({ w, ...(line ? { line } : {}), ...(en ? { en } : {}) })
  const r = await fetch(`${base}/lookup?${q}`, { headers: auth })
  return { status: r.status, body: (await r.json()) as any }
}
const words = (body: object) => fetch(`${base}/words`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
const db = (name: string) => JSON.parse(readFileSync(join(dataDir, name), 'utf8'))

export default async function lexicon() {
  const repo = resolve(import.meta.dir, '../../../lexicon')
  const meta = JSON.parse(readFileSync(join(repo, 'sl.json'), 'utf8')).meta
  check('companion/lexicon/sl.json says where it is from and its licence', meta.language === 'sl' && meta.source === 'Wiktionary via kaikki.org' && meta.licence === 'CC BY-SA 4.0' && meta.counts.lemmas > 5000, meta)
  const extra = JSON.parse(readFileSync(join(repo, 'sl.extra.json'), 'utf8'))
  const checked = validateExtra(extra)
  check('the supplement (sl.extra.json) validates, every entry, and says it is not reviewed', checked.errors.length === 0 && checked.entries.length > 300 && extra.meta.reviewed === false && /not reviewed/.test(extra.meta.note), checked.errors)
  check('normalizing drops tonal accents, keeps the caron: potọ́ka → potoka, žȅna → žena', normalizeForm('potọ́ka', 'sl') === 'potoka' && normalizeForm('Žȅna', 'sl') === 'žena' && normalizeForm('bȋł', 'sl') === 'bil')
  check("normalizing Italian: the stress marks inside a word go, the written accent stays, one apostrophe: casìna → casina, città, L’acqua → l'acqua", normalizeForm('casìna', 'it') === 'casina' && normalizeForm('Città', 'it') === 'città' && normalizeForm('perché', 'it') === 'perché' && normalizeForm('L’acqua', 'it') === "l'acqua" && normalizeForm("perche'", 'it') === "perche'")
  check('a word\'s review item: vocab_word_<lemma>, č š ž as c s z', wordItemId('Na svidenje') === 'vocab_word_na-svidenje' && wordItemId('čebela') === 'vocab_word_cebela')

  // --- lookups ---------------------------------------------------------------------------------------------------
  const gozdu = await lookup('gozdu', 'V gozdu je tiho.', "It's quiet in the forest.")
  const g0 = gozdu.body.entries?.[0]
  check('GET /lookup: gozdu is gozd, forest, locative singular after "v", from Wiktionary with the attribution', gozdu.status === 200 && gozdu.body.word === 'gozdu' && g0?.lemma === 'gozd' && g0.pos === 'noun' && g0.gloss.includes('forest') && g0.grammar === 'locative singular' && g0.gender === 'm' && g0.source === 'wiktionary' && g0.item_id === 'vocab_word_gozd' && g0.known === false && g0.example === null && gozdu.body.attribution === 'Wiktionary (CC BY-SA 4.0) via kaikki.org', gozdu.body)
  const bare = (await lookup('gozdu,')).body
  check('GET /lookup: without the line, both readings; punctuation around the word is ignored', bare.word === 'gozdu' && bare.entries[0]?.grammar === 'dative/locative singular', bare)

  const kruh = (await lookup('kruh', 'Kruh je še topel.', 'The bread is still warm.')).body
  const k0 = kruh.entries?.[0]
  check('GET /lookup: a pack word comes first, with its pack item, gloss, emoji and example, known once learned', k0?.source === 'pack' && k0.lemma === 'kruh' && k0.item_id === 'vocab_smoke-kitchen_kruh' && k0.known === true && k0.gloss[0] === 'kruh (en)' && k0.emoji === '🍞' && k0.example?.sl === 'Kruh je še topel.' && k0.gender === 'm' && k0.pos === 'noun', kruh)
  check('GET /lookup: one entry per lemma and part of speech', kruh.entries.filter((e: any) => e.lemma === 'kruh' && e.pos === 'noun').length === 1, kruh.entries)
  const plural = (await lookup('kruhi')).body
  check('GET /lookup: a pack word found by its plural', plural.entries[0]?.source === 'pack' && plural.entries[0].lemma === 'kruh' && /plural/.test(plural.entries[0].grammar ?? ''), plural)

  const village = (await lookup('vas', 'Naša vas je majhna.', 'Our village is small.')).body
  const you = (await lookup('vas', 'Vidim vas.', 'I see you.')).body
  check('GET /lookup: "vas" in a line about the village is the village first', village.entries[0]?.lemma === 'vas' && village.entries[0].pos === 'noun' && village.entries[0].gloss.includes('village'), village.entries)
  check('GET /lookup: "vas" in "I see you" is the pronoun first', you.entries[0]?.pos === 'pron' && you.entries[0].gloss.some((g: string) => /you/.test(g)) && you.entries.some((e: any) => e.lemma === 'vas' && e.pos === 'noun'), you.entries)

  const tone = (await lookup('Tone', 'Tone kuje podkve.', 'Tone is forging horseshoes.')).body
  const t0 = tone.entries?.[0]
  check("GET /lookup: a villager's name is who they are, no review item", t0?.source === 'village' && t0.pos === 'name' && t0.lemma === 'Kovač Tone' && /^Tone — the blacksmith/i.test(t0.gloss[0]) && t0.item_id === null && t0.known === false && t0.emoji === '⚒️', tone)
  const tonetu = (await lookup('Tonetu', 'Pojdi k Tonetu.', "Go to Tone's.")).body
  check('GET /lookup: a declined name too (Tonetu), and the learner (Jan)', tonetu.entries[0]?.lemma === 'Kovač Tone' && (await lookup('Jan')).body.entries[0]?.gloss[0] === 'Jan — you', tonetu)

  const svidenje = (await lookup('svidenje', 'Na svidenje!', 'Goodbye!')).body
  const s0 = svidenje.entries?.[0]
  check('GET /lookup: a word Wiktionary lacks comes from the supplement (svidenje → na svidenje)', s0?.source === 'extra' && s0.lemma === 'na svidenje' && s0.gloss.some((g: string) => /goodbye/i.test(g)) && s0.item_id === 'vocab_word_na-svidenje', svidenje)
  const na = (await lookup('na', 'Na svidenje!', 'Goodbye!')).body
  check('GET /lookup: the phrase around a tapped word counts (na in "Na svidenje" → na svidenje)', na.entries.some((e: any) => e.lemma === 'na svidenje'), na.entries)

  // --- the right reading first -----------------------------------------------------------------------------------
  const prav = (await lookup('prav')).body.entries
  check('GET /lookup: prav alone is the adverb first, before a genitive plural of pravo', prav[0]?.lemma === 'prav' && prav[0].pos === 'adv' && prav[0].source === 'extra' && prav[0].gloss.some((g: string) => /right/.test(g)) && prav.some((e: any) => e.lemma === 'pravo'), prav)
  const ravnoPrav = (await lookup('prav', 'Jan! Ravno prav. Si videl Belo?', 'Jan! Just in time. Have you seen Bela?')).body.entries
  check('GET /lookup: prav in "Ravno prav." is the expression first (ravno prav: just in time), then the adverb', ravnoPrav[0]?.lemma === 'ravno prav' && ravnoPrav[0].gloss.includes('just in time') && ravnoPrav[1]?.lemma === 'prav', ravnoPrav)
  const ravno = (await lookup('Ravno', 'Jan! Ravno prav. Si videl Belo?')).body.entries
  check('GET /lookup: ravno in the same line, without a translation: the expression, then the adverb before raven', ravno[0]?.lemma === 'ravno prav' && ravno[1]?.lemma === 'ravno' && ravno[1].pos === 'adv', ravno)
  const expressions = await Promise.all([lookup('ti', 'Kaj pa ti?', 'And you?'), lookup('vem', 'Ne vem.'), lookup('noč', 'Lahko noč, Jan!', 'Good night, Jan!'), lookup('kaj', 'Ni za kaj.')])
  const firsts = expressions.map(x => x.body.entries[0]?.lemma)
  check('GET /lookup: an expression around the word comes first: kaj pa ti, ne vem, lahko noč, ni za kaj', JSON.stringify(firsts) === JSON.stringify(['kaj pa ti', 'ne vem', 'lahko noč', 'ni za kaj']), firsts)
  const noc = expressions[2].body.entries
  check("GET /lookup: an expression is one entry (the supplement's phrase and Wiktionary's)", noc.filter((e: any) => e.lemma === 'lahko noč').length === 1 && noc[0].gloss[0] === 'good night', noc)
  const others = await Promise.all([lookup('pod', 'Pod lipo sedim.'), lookup('da', 'Dobro, da si tu.'), lookup('samo', 'Samo eno!'), lookup('ljudi')])
  const other = others.map(x => `${x.body.entries[0]?.lemma}/${x.body.entries[0]?.pos}`)
  check('GET /lookup: words Wiktionary knew only otherwise: pod (under, not floor), da (that), samo (only), ljudi (people)', JSON.stringify(other) === JSON.stringify(['pod/prep', 'da/conj', 'samo/adv', 'ljudje/noun']), other)
  const lepa = (await lookup('lepa')).body.entries
  const samo = (await lookup('Samo', 'Pozdravi Samo in Majo.')).body.entries
  check("GET /lookup: Wiktionary's names come last (lepa: nice, not a cat's name), first when capitalized inside the line", lepa[0]?.lemma === 'lep' && lepa.at(-1)?.pos === 'name' && samo[0]?.pos === 'name' && samo[0].lemma === 'Samo', { lepa, samo })
  const babici = (await lookup('babici')).body
  check("GET /lookup: the supplement's gloss of a lemma (override) with Wiktionary's grammar and attribution: babici → grandmother, not midwife", babici.entries[0]?.lemma === 'babica' && /grandmother/.test(babici.entries[0].gloss[0]) && babici.entries[0].grammar === 'nominative/accusative dual; dative/locative singular' && babici.attribution !== null, babici)
  const packPhrase = { id: 'smoke-phrases', language: 'sl', level: 'A1', title: 'Phrases', source: 'curated', words: [{ id: 'lahko-noc', sl: 'Lahko noč!', en: 'Good night!', emoji: '🌙' }] } as unknown as LoadedPack
  const dict = new Lexicon({ dir: repo, language: 'sl', tutorFile: join(dataDir, 'no-glosses.json') })
  const inPack = lookUp('noč', { line: 'Lahko noč, Jan!' }, { lexicon: dict, base: 'en', packs: [packPhrase], people: [], items: {} }).entries
  check('lookup: a pack phrase with its punctuation ("Lahko noč!") is found in the line and comes first, one entry with the dictionary\'s', inPack[0]?.source === 'pack' && inPack[0].lemma === 'Lahko noč!' && inPack.filter(e => /^lahko noč/i.test(e.lemma)).length === 1 && inPack[1]?.lemma === 'noč', inPack)
  // the wild animals on the village map (the app's game/render/Wildlife.kt): a tap looks up the animal's word without a
  // line, and the Forest animals pack has every one, with the emoji that shows it, so the card and the pack agree
  const forest = validatePack(JSON.parse(readFileSync(resolve(import.meta.dir, '../../../packs/zivali-v-gozdu.json'), 'utf8')))
  const zivali = { ...(forest.ok ? forest.pack : {}), source: 'curated' } as LoadedPack
  const animals = ['veverica', 'zajec', 'ptica', 'srna', 'lisica', 'jež', 'netopir', 'divji prašič', 'jazbec', 'sova', 'medved', 'sled']
  const onMap = animals.map(w => lookUp(w, {}, { lexicon: dict, base: 'en', packs: [zivali], people: [], items: {} }).entries[0])
  check(
    'lookup: every animal on the map is a word of the Forest animals pack, first, with its emoji and review item',
    forest.ok && onMap.every((e, i) => e?.source === 'pack' && e.lemma === animals[i] && !!e.emoji && e.item_id?.startsWith('vocab_zivali-v-gozdu_')),
    onMap.map(e => [e?.lemma, e?.source, e?.emoji, e?.item_id]),
  )

  // --- grammar written readably ----------------------------------------------------------------------------------
  const junk = /\b(?:singular|dual|plural) (?:singular|dual|plural)\b|\b(?:masculine|feminine|neuter) (?:masculine|feminine|neuter)\b|\b(?:first|second|third)-person (?:first|second|third)-person\b|table-tags|inflection-template|error-/
  const grammar: string[] = JSON.parse(readFileSync(join(repo, 'sl.json'), 'utf8')).grammar
  check('sl.json: no grammar like "genitive dual plural", "masculine feminine" or table tags', grammar.every(g => !junk.test(g)), grammar.filter(g => junk.test(g)))
  const tags = await Promise.all(['bo', 'kdo', 'pridi'].map(w => lookup(w)))
  const read = tags.map(x => x.body.entries[0]?.grammar)
  check('GET /lookup: prav → pravo "genitive dual/plural"; bo → future, not "negative present"; kdo → "nominative masculine/feminine"; pridi has no gender', prav.find((e: any) => e.lemma === 'pravo')?.grammar === 'genitive dual/plural' && JSON.stringify(read) === JSON.stringify(['third-person singular future', 'nominative masculine/feminine', 'second-person singular imperative']), read)

  const none = await lookup('xyzzyq')
  check('GET /lookup: an unknown word: 200, no entries, no attribution', none.status === 200 && Array.isArray(none.body.entries) && none.body.entries.length === 0 && none.body.attribution === null, none)
  const empty = await fetch(`${base}/lookup?w=%20`, { headers: auth })
  const missing = await fetch(`${base}/lookup`, { headers: auth })
  const long = await fetch(`${base}/lookup?w=${'a'.repeat(41)}`, { headers: auth })
  check('GET /lookup: 400 without a word, for an empty or a too long one', empty.status === 400 && missing.status === 400 && long.status === 400, [empty.status, missing.status, long.status])
  check('GET /lookup: the app token only', (await fetch(`${base}/lookup?w=gozd`)).status === 401)

  // --- adding a word --------------------------------------------------------------------------------------------
  const before = { profile: db('learner-profile.json'), progress: db('progress-db.json'), log: db('session-log.json') }
  const add = { client_id: 'words-gozd-0001', sl: 'gozd', en: 'forest', pos: 'noun', item_id: 'vocab_word_gozd', example_sl: 'V gozdu je tiho.', example_en: "It's quiet in the forest.", from: 'scene' }
  const firstAdd = Date.now()
  const w1r = await words(add)
  const w1 = await w1r.json()
  const w1again = await words(add)
  const w1againBody = await w1again.json()
  check('POST /words adds the word as a review item', w1r.ok && w1.item_id === 'vocab_word_gozd' && w1.added === true, w1)
  check('POST /words: a repeated client_id returns the same answer', w1again.headers.get('x-lani-replay') === '1' && JSON.stringify(w1againBody) === JSON.stringify(w1), w1againBody)
  const w2 = await (await words({ sl: 'Gozd', en: 'forest' })).json()
  check('POST /words: a word already there is not added again', w2.item_id === 'vocab_word_gozd' && w2.added === false, w2)
  const item = db('spaced-repetition.json').items.vocab_word_gozd
  check('POST /words: the item is a vocabulary card due tomorrow, with the sentence it was met in', item?.type === 'vocabulary' && item.content === 'gozd' && item.answer === 'forest' && item.category === 'dialog' && item.example === 'V gozdu je tiho.' && item.repetitions === 0, item)
  check('POST /words: the item says it came from a lookup (added, not answered)', item?.source === 'lookup' && item.total_reviews === 0, item)
  const after = { profile: db('learner-profile.json'), progress: db('progress-db.json'), log: db('session-log.json') }
  check(
    'POST /words records no practice: no session, no minutes, no streak day',
    after.log.sessions.length === before.log.sessions.length && after.progress.overall_stats.total_study_minutes === before.progress.overall_stats.total_study_minutes && after.profile.total_sessions === before.profile.total_sessions && after.profile.current_streak_days === before.profile.current_streak_days,
    { before: before.progress.overall_stats, after: after.progress.overall_stats },
  )
  const known = (await lookup('gozdu')).body
  check('GET /lookup: the word added is known now', known.entries[0]?.item_id === 'vocab_word_gozd' && known.entries[0].known === true, known.entries[0])

  // a pack word: the same card as learning it from its pack; a word already learned from another pack is known
  const noz = await (await words({ sl: 'nož', en: 'knife', item_id: 'vocab_smoke-kitchen_noz', from: 'scene' })).json()
  const nozItem = db('spaced-repetition.json').items['vocab_smoke-kitchen_noz']
  check("POST /words: a pack word becomes its pack's card, from a lookup too", noz.added === true && noz.item_id === 'vocab_smoke-kitchen_noz' && nozItem?.answer === 'nož (en)' && nozItem.category === 'smoke-kitchen' && nozItem.source === 'lookup', { noz, nozItem })
  // a word from a reading says so; GET /words tells the app it may (an older bridge answers 404: "villager" then)
  const sources = await fetch(`${base}/words`, { headers: auth })
  const sourcesBody = await sources.json()
  check('GET /words lists the origins POST /words takes, a reading among them', sources.ok && ['scene', 'roleplay', 'villager', 'chat', 'reading'].every(s => sourcesBody.from?.includes(s)), sourcesBody)
  const pratika = await (await words({ sl: 'pratika', en: 'almanac', from: 'reading' })).json()
  check('POST /words: a word from a reading is added', pratika.added === true && pratika.item_id === 'vocab_word_pratika', pratika)
  const mleko = await (await words({ sl: 'mleko', en: 'milk', item_id: 'vocab_curated-greetings_g-mleko' })).json()
  check('POST /words: a word learned from another pack answers with that item', mleko.added === false && mleko.item_id === 'vocab_smoke-kitchen_mleko', mleko)
  const bad = await Promise.all([
    words({ sl: 'gozd', en: 'forest', item_id: 'vocab_other_thing' }),
    words({ sl: 'x'.repeat(81), en: 'long' }),
    words({ sl: 'gozd' }),
    words({ sl: 'gozd', en: 'forest', from: 'nowhere' }),
  ])
  check('POST /words: 400 for an item id not from a lookup, a word too long, no meaning, an unknown origin', bad.every(r => r.status === 400), bad.map(r => r.status))
  // the words reach the tutor gathered, LANI_WORDS_NOTE_MS (1 s in the harness) after the first
  await Bun.sleep(Math.max(0, 1300 - (Date.now() - firstAdd)))
  const backlog = (await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as { event: any }[]
  check('POST /words: the app hears words_added', backlog.some(b => b.event.type === 'words_added' && b.event.item_ids?.[0] === 'vocab_word_gozd'))
  const note = channelEvents.filter(n => n.params?.meta?.kind === 'words_added')
  check('POST /words: the tutor hears the words added, gathered in one line', note.length === 1 && note[0].params.content.includes('gozd (forest) from a scene') && note[0].params.content.includes('nož (knife)') && note[0].params.content.includes('pratika (almanac) from a reading') && note[0].params.content.includes('ALREADY persisted'), note.map(n => n.params.content))

  // --- the tutor's glosses --------------------------------------------------------------------------------------
  const badGloss = await client.callTool({ name: 'publish_gloss', arguments: { form: 'prinesel', lemma: 'prinesti', gloss: [] } })
  check('publish_gloss refuses a gloss without meanings', badGloss.isError, badGloss)
  const pub = await client.callTool({ name: 'publish_gloss', arguments: { form: 'Prinesel', lemma: 'prinesti', pos: 'verb', gloss: ['to bring'], grammar: 'masculine singular l-participle', note: 'perfective; nositi is the imperfective' } })
  check('publish_gloss keeps the gloss in <data>/app/lexicon.json', !pub.isError && existsSync(join(dataDir, 'app/lexicon.json')), pub)
  const brought = (await lookup('prinesel', 'Luka je prinesel mleko.', 'Luka brought milk.')).body
  const b0 = brought.entries?.[0]
  check("GET /lookup: the tutor's gloss comes first", b0?.source === 'tutor' && b0.lemma === 'prinesti' && b0.gloss[0] === 'to bring' && b0.grammar === 'masculine singular l-participle' && b0.note?.startsWith('perfective') && b0.item_id === 'vocab_word_prinesti', brought)
  check("GET /lookup: the tutor's lemma finds it too", (await lookup('prinesti')).body.entries[0]?.source === 'tutor')
  const events = (await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as { event: any }[]
  check('publish_gloss: the app hears lexicon_updated', events.some(b => b.event.type === 'lexicon_updated' && b.event.word === 'Prinesel'))
  const guide = await guideOf()
  check('the guide tells the tutor about lookups: words_added, data.lookup and publish_gloss', guide.includes('words_added') && guide.includes('lookup {word, line, en') && guide.includes('publish_gloss'))

  // --- a dictionary as a download: checked against its manifest, cached, the cache when the source fails ----------
  const status = await (await fetch(`${base}/lexicon/status`, { headers: auth })).json()
  check('GET /lexicon/status: Slovene is the repository\'s dictionary, nothing to download', status.language === 'sl' && status.from === 'bundled' && status.wiktionary === true && status.lemmas > 5000 && status.download === 'off' && status.base === 'en', status)
  check("GET /lookup: Wiktionary's meaning is English (gloss_lang en), a pack word's the learner's base", g0?.gloss_lang === 'en' && k0?.gloss_lang === 'en')
  const src = tempDir('lani-smoke-dl-src-')
  const cache = tempDir('lani-smoke-dl-cache-')
  const small = { meta: { language: 'it', source: 'x', licence: 'CC BY-SA 4.0', url: '', built: '2026-09-26', counts: {} }, grammar: [], lemmas: [{ lemma: 'ciao', pos: 'intj', gloss: ['hello'] }], forms: { ciao: [[0]] } }
  const v1 = publishLexicon(src, 'it', small, false, 'v1')
  const get = (url = src, cacheDir = cache, language = 'it') => fetchLexicon({ url, language, cacheDir }, parseLexicon)
  const first = await get()
  check('fetchLexicon: from a local directory, checked and cached', !('error' in first) && first.from === 'download' && first.manifest.version === 'v1' && first.file.lemmas[0].lemma === 'ciao' && existsSync(join(cache, 'it.json')), first)
  const second = await get()
  check('fetchLexicon: the same version again comes from the cache', !('error' in second) && second.from === 'cache')
  const fileUrl = await get(`file://${src}`, tempDir('lani-smoke-dl-cache2-'))
  check('fetchLexicon: a file:// URL works too', !('error' in fileUrl) && fileUrl.from === 'download')
  // a new version whose file doesn't match its manifest: refused; the cached copy stays
  writeFileSync(join(src, 'it.manifest.json'), JSON.stringify({ ...v1, version: 'v2', sha256: 'f'.repeat(64) }))
  const tampered = await get()
  const fresh = await get(src, tempDir('lani-smoke-dl-cache3-'))
  check("fetchLexicon: a file that doesn't match its manifest is refused, the cached copy kept", !('error' in tampered) && tampered.from === 'cache' && tampered.manifest.version === 'v1' && 'error' in fresh && /doesn't match its manifest/.test(fresh.error), { tampered, fresh })
  const offline = await get(join(src, 'nowhere'))
  const offlineEmpty = await get(join(src, 'nowhere'), tempDir('lani-smoke-dl-cache4-'))
  check('fetchLexicon: an unreachable source: the cached copy, else an error', !('error' in offline) && offline.from === 'cache' && 'error' in offlineEmpty, { offline, offlineEmpty })
  publishLexicon(src, 'it', small, true, 'v3')
  const gz = await get()
  check('fetchLexicon: a gzipped new version replaces the cached one', !('error' in gz) && gz.from === 'download' && gz.manifest.version === 'v3' && gz.file.forms.ciao !== undefined && existsSync(join(cache, 'it.json.gz')), gz)
  const wrongLang = await get(src, tempDir('lani-smoke-dl-cache5-'), 'de')
  check('fetchLexicon: no manifest for the language: an error', 'error' in wrongLang)
  const d = new Lexicon({ dir: tempDir('lani-smoke-dl-dict-'), language: 'it', base: 'sl', tutorFile: join(dataDir, 'no-glosses.json') })
  check('Lexicon.use: a downloaded dictionary serves lookups', !d.wiktionary && !('error' in gz) && d.use(gz.file, 'download', 'v3') && d.readings('ciao', 'en')[0]?.gloss[0] === 'hello' && d.status().from === 'download')
  const old = { ...small, lemmas: [{ lemma: 'ciao', pos: 'intj', gloss: ['hello'], meanings: { sl: { gloss: ['živijo'], via: 'pivot' } } }] }
  check("Lexicon: a dictionary without meanings (an older build) is read as before; one with them explains in the base, the English kept", d.readings('ciao')[0]?.fallback === true && d.use(old as LexiconFile, 'download', 'v4') && d.readings('ciao')[0]?.gloss[0] === 'živijo' && d.readings('ciao')[0]?.en?.[0] === 'hello')

  // --- meanings in the learner's base language -------------------------------------------------------------------
  check('companion/lexicon/sl.json has meanings in Italian and German, with their coverage and sources', meta.bases?.it?.share > 0.7 && meta.bases?.de?.share > 0.7 && meta.sources?.some((s: any) => s.edition === 'de'), meta.bases)
  const houses = (await lookup2('hiše', { base: 'de', line: 'Hiše so stare.' })).body
  const h0 = houses.entries[0]
  check('GET /lookup?base=de: sl←de: hiše is hiša, Haus (German Wiktionary\'s own), with the English and the attribution', h0?.lemma === 'hiša' && h0.gloss[0] === 'Haus' && h0.gloss_lang === 'de' && h0.gloss_via === 'direct' && h0.gloss_en?.[0] === 'house' && !h0.fallback && h0.grammar && houses.attribution !== null, houses)
  const own = (await lookup('hiše')).body.entries[0]
  check("GET /lookup: without a base asked for, the learner's own (English here)", own?.lemma === 'hiša' && own.gloss[0] === 'house' && own.gloss_lang === 'en' && !own.fallback, own)
  const thanks = (await lookup2('hvala', { base: 'de', line: 'Hvala!' })).body.entries[0]
  check("GET /lookup?base=de: the supplement's entry of a word Wiktionary has too takes its meaning: hvala, danke", thanks?.lemma === 'hvala' && thanks.pos === 'intj' && thanks.gloss.includes('danke') && thanks.gloss_lang === 'de' && !thanks.fallback, thanks)
  const hi = (await lookup2('hiša', { base: 'it' })).body.entries[0]
  check('GET /lookup?base=it: sl←it: hiša is casa', hi?.gloss[0] === 'casa' && hi.gloss_lang === 'it' && hi.gloss_via, hi)
  const gapsBefore = channelEvents.filter(n => n.params?.meta?.kind === 'gloss_gaps').length
  const bye = (await lookup2('svidenje', { base: 'de', line: 'Na svidenje!' })).body.entries[0]
  check('GET /lookup?base=de: no German meaning (the supplement\'s na svidenje): the English, marked fallback', bye?.lemma === 'na svidenje' && bye.gloss[0] === 'goodbye' && bye.gloss_lang === 'en' && bye.fallback === true, bye)
  await Bun.sleep(700)
  const gaps = channelEvents.filter(n => n.params?.meta?.kind === 'gloss_gaps').slice(gapsBefore)
  check('the tutor hears of the gap, once, gathered, with what to publish: gloss_gaps', gaps.length === 1 && gaps[0].params.content.includes('na svidenje') && gaps[0].params.content.includes('German') && gaps[0].params.content.includes('gloss_lang: "de"') && gaps[0].params.content.includes('English: goodbye'), gaps.map(g => g.params.content))
  await lookup2('svidenje', { base: 'de' })
  await Bun.sleep(700)
  check('a gap the tutor heard of is not asked again', channelEvents.filter(n => n.params?.meta?.kind === 'gloss_gaps').length === gapsBefore + 1)
  const fill = await client.callTool({ name: 'publish_gloss', arguments: { form: 'svidenje', lemma: 'na svidenje', gloss: ['auf Wiedersehen'], gloss_lang: 'de' } })
  const filled = (await lookup2('svidenje', { base: 'de' })).body.entries
  check("publish_gloss with gloss_lang: the tutor's German fills the gap, flagged machine-written (claude), one entry, the English kept", !fill.isError && filled[0]?.lemma === 'na svidenje' && filled[0].gloss[0] === 'auf Wiedersehen' && filled[0].gloss_lang === 'de' && filled[0].gloss_via === 'claude' && filled[0].gloss_en?.[0] === 'goodbye' && !filled[0].fallback && filled.filter((e: any) => e.lemma === 'na svidenje').length === 1, filled)
  check("publish_gloss with gloss_lang: the learner's English is unchanged", (await lookup('svidenje')).body.entries[0]?.gloss[0] === 'goodbye')
  const wrong = await Promise.all([lookup2('hiša', { language: 'xx' }), lookup2('hiša', { base: 'sl' }), lookup2('hiša', { base: 'klingon' })])
  check('GET /lookup: 400 for an unknown language, a base the same as the word\'s, an unknown base', wrong.every(r => r.status === 400), wrong.map(r => r.status))

  // --- on a visit: another town's language ---------------------------------------------------------------------
  const gull = (await lookup2('gabbiano', { language: 'it', line: 'Il gabbiano vola.' })).body.entries[0]
  const galeb = (await lookup2('gabbiano', { language: 'it', base: 'sl' })).body.entries[0]
  check("GET /lookup?language=it: a visit's lookup is in the town's language, its culture's pack words, in the learner's base (en) or the one asked (sl)", gull?.source === 'pack' && gull.lemma === 'il gabbiano' && gull.gloss[0] === 'gull' && galeb?.gloss[0] === 'galeb' && galeb.gloss_lang === 'sl', { gull, galeb })
  const ciao = await client.callTool({ name: 'publish_gloss', arguments: { form: 'ciao', lemma: 'ciao', pos: 'intj', gloss: ['hi', 'bye'], language: 'it' } })
  const c0 = (await lookup2('ciao', { language: 'it' })).body.entries[0]
  check("publish_gloss with language: an Italian word's gloss is kept with the Italian dictionary (app/lexicon.it.json), found on the visit", !ciao.isError && existsSync(join(dataDir, 'app/lexicon.it.json')) && c0?.source === 'tutor' && c0.gloss[0] === 'hi' && c0.gloss_via === 'claude' && (await lookup('ciao')).body.entries.every((e: any) => e.source !== 'tutor'), { ciao, c0 })
  const visited = await (await fetch(`${base}/lexicon/status`, { headers: auth })).json()
  check('GET /lexicon/status lists the languages looked up on visits', visited.visits?.some((v: any) => v.language === 'it') && JSON.stringify(visited.bases) === '["en","it","de"]', visited)
  const guided = await guideOf()
  check('the guide tells the tutor about meanings in the base: gloss_gaps, gloss_lang, language', guided.includes('gloss_gaps') && guided.includes('gloss_lang') && guided.includes('data.lookup.language'))
}

/** GET /lookup with the other parameters: base, language, line. */
async function lookup2(w: string, o: { base?: string; language?: string; line?: string }) {
  const q = new URLSearchParams({ w, ...Object.fromEntries(Object.entries(o).filter(([, v]) => v)) as Record<string, string> })
  const r = await fetch(`${base}/lookup?${q}`, { headers: auth })
  return { status: r.status, body: (await r.json()) as any }
}
