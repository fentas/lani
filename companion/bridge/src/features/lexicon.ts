// Word lookup (lexicon.ts): Jan taps a word in a dialog and sees what it means (GET /lookup), in their base language
// when the dictionary has it, adds it to their words (POST /words: a vocabulary review item, without a practice
// session), and the tutor explains the words the dictionary doesn't know, or doesn't know in the base (publish_gloss).
// On a visit the words are the host town's language, explained in the visit's base: that language's dictionary is
// loaded (or downloaded) at the first lookup. A word's forms (GET /forms, forms.ts): its slots with the grammar book's
// page of each and the content's lines they stand in, for the review's form questions and the word card's table.
import { existsSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { fail, ok } from '../channel'
import { cultureIds, readManifest } from '../cultures'
import { renderDeep } from '../addressee'
import type { FeatureFactory } from '../feature'
import { json, readJson } from '../http'
import { languageParam, type SrItem } from '../learner'
import { LANGUAGES, learnerFacts, learnerLangs } from '../learners'
import { ATTRIBUTION, glossIn, Lexicon, lookup, wordItemId, type LookupResult, type Person } from '../lexicon'
import { contentLines, FormsBook, formsOf, LineIndex, partnerOf } from '../forms'
import { fetchLexicon, lexiconCacheDir, parseLexicon } from '../lexicon-download'
import { itemId, meaningOf, normWord, PackStore, packsDirs, vocabularyWords, wordOf, type LoadedPack } from '../packs'
import { labelEn } from '../langs'

const instructions = `
Word lookup: in the app's dialogs Jan taps a word and sees its base form, meaning and grammar (the bridge looks it up
in the word packs, your glosses, the villagers, Wiktionary and a supplement), and can add it to their words. Meanings
are in Jan's base language when the dictionary has one there (from Wiktionary: its own, a translation table, or
through English); else the card shows the English and says so. words_added lists the words Jan added that way:
ALREADY persisted as vocabulary review items, don't persist them again; reply only if you have something useful to
say. A chat whose JSON block has lookup {word, line, en, language?} is a word the dictionary didn't know: explain it
briefly with reply (base form, meaning, what this form is in the line) AND call publish_gloss with the form as it
appears, its lemma, pos, 1-3 short glosses (in the language you explain in) and the grammar, so the next lookup finds
it. gloss_gaps lists words whose lookup had no meaning in Jan's base, with the English: for each, call publish_gloss
{form, lemma, pos, gloss: 1-3 short meanings in that base, gloss_lang, language when the note names one}; the app
shows them flagged machine-written, and the card open on the word updates. No reply needed for gloss_gaps.
publish_gloss's gloss_lang is the language your glosses are in (default: Jan's base); language is the word's (default:
the village's; on a visit, the town's, as the note or data.lookup.language says).
`.trim()

/**
 * Where in the app a word was added (POST /words `from`, for the tutor's note): a scene's dialog, a role-play, a
 * villager's line, the chat, a reading (the reading corner's, a book in the chest, a story's). GET /words lists them, so
 * an app sends "reading" only to a bridge that takes it (an older one answers 404: "villager" then).
 */
export const WORD_SOURCES = ['scene', 'roleplay', 'villager', 'chat', 'reading'] as const

const wordIn = z.object({
  client_id: z.string().optional(),
  sl: z.string().trim().min(1).max(80),
  en: z.string().trim().min(1).max(200),
  pos: z.string().trim().max(24).optional(),
  // the review item GET /lookup named: a pack word's own, or vocab_word_<lemma>
  item_id: z.string().regex(/^vocab_[a-z0-9][a-z0-9_-]{0,127}$/, 'the item_id GET /lookup gave').optional(),
  example_sl: z.string().trim().max(400).optional(),
  example_en: z.string().trim().max(400).optional(),
  from: z.enum(WORD_SOURCES).optional(),
  // whose word it becomes: another language's (a visit's), the home language's by default (docs/DB_SCRIPTS.md)
  language: languageParam,
})

/** How long the words Jan adds are gathered before the tutor hears of them, in one line. */
const NOTE_DELAY_MS = Number(process.env.LANI_WORDS_NOTE_MS ?? 5 * 60_000)
/** How long the words without a meaning in the base are gathered before the tutor hears of them (LANI_GLOSS_GAPS=off: never). */
const GAP_DELAY_MS = Number(process.env.LANI_GLOSS_GAP_MS ?? 3_000)
/** At most this many words a gap note asks about in an hour: each wakes the session. */
const GAPS_PER_HOUR = 30
/** How long a lookup on a visit waits for the town's dictionary to arrive the first time. */
const WAIT_MS = Number(process.env.LANI_LEXICON_WAIT_MS ?? 20_000)

const LANG = /^[a-z]{2,3}$/

/** At most this many words GET /forms answers at once. */
const FORMS_WORDS = 40
/** How long the content's lines are kept before they are read again (sooner when the tutor publishes something). */
const LINES_TTL_MS = 10 * 60_000
/** What the tutor publishes or takes back that has lines in it: the lines are read again at the next ask. */
const CONTENT_EVENTS = new Set(['scene_published', 'scene_removed', 'story_published', 'story_removed', 'reading_published', 'reading_removed', 'variant_published', 'variant_removed', 'grammar_published', 'pack_published'])

export const lexicon: FeatureFactory = ({ cfg, learner, channel, events, outbox, packs, villagers, culture, profile, log, scenes, stories, readings, grammar, addressee }) => {
  const language = culture.manifest?.language ?? 'sl'
  const lexiconDir = process.env.LANI_LEXICON_DIR || join(cfg.projectDir, 'companion/lexicon')
  const { base } = learnerLangs(cfg.dataDir, language)
  const dict = new Lexicon({ dir: lexiconDir, language, base, tutorFile: join(cfg.appDir, 'lexicon.json'), log })
  // the forms book: the hand-checked tables of the irregular verbs and the verb pairs (lexicon/<language>.forms.json)
  const book = new FormsBook({ dir: lexiconDir, language, log })
  const partners = (lemma: string) => partnerOf(book, dict, lemma)

  // The content's lines a form may be asked in, by their words: made at the first ask, again after LINES_TTL_MS or when
  // the tutor published something with lines in it.
  let lineIndex: { at: number; index: LineIndex; pages: Set<string> } | null = null
  events.tap(e => {
    if (CONTENT_EVENTS.has(e.type)) lineIndex = null
  })
  function content(): { index: LineIndex; pages: Set<string> } {
    if (lineIndex && Date.now() - lineIndex.at < LINES_TTL_MS) return lineIndex
    const pages = grammar.all()
    // as the app says them: rendered for the learner ({learner}, {m:…|f:…}: addressee.ts)
    const lines = contentLines(renderDeep({ language, scenes: scenes.all(), stories: stories.all(), readings: readings.all(), grammar: pages, packs: packs.all() }, addressee()))
    lineIndex = { at: Date.now(), index: new LineIndex(lines), pages: new Set(pages.map(p => p.id)) }
    return lineIndex
  }
  // A dictionary the repository doesn't have comes from LANI_LEXICON_URL (lexicon-download.ts): the village's once
  // the bridge is up, another town's at the first lookup of a visit there.
  const source = process.env.LANI_LEXICON_URL?.trim() || undefined
  type Download = { state: 'off' | 'bundled' | 'loading' | 'ready' | 'failed'; error?: string }
  let download: Download = { state: 'off' }
  async function fetchDictionary(d: Lexicon, set: (s: Download) => void) {
    if (!source) return
    if (d.bundled) return set({ state: 'bundled' })
    set({ state: 'loading' })
    try {
      const r = await fetchLexicon({ url: source, language: d.language, cacheDir: lexiconCacheDir(), log }, parseLexicon)
      if ('error' in r) {
        set({ state: 'failed', error: r.error })
        log(`lexicon ${d.language}: no dictionary from LANI_LEXICON_URL: ${r.error}`)
        return
      }
      d.use(r.file, r.from, r.manifest.version)
      set({ state: 'ready' })
      log(`lexicon ${d.language}: ${r.manifest.version} (${r.from}), ${r.file.lemmas.length} lemmas`)
    } catch (e) {
      set({ state: 'failed', error: (e as Error).message })
      log(`lexicon ${d.language}: download failed: ${(e as Error).message}`)
    }
  }

  /** The base a word of [lang] is explained in: the learner's; on a visit to a town of their base, their own target. */
  function baseFor(lang: string): string {
    const own = learnerLangs(cfg.dataDir, language)
    return lang === language || own.base !== lang ? own.base : own.target
  }

  // Other towns' languages (visits): their dictionary, the tutor's glosses of their words, their curated word packs.
  type Visited = { dict: Lexicon; download: Download; ready: Promise<void>; packs: PackStore }
  const visited = new Map<string, Visited>()
  function visitedOf(lang: string): Visited {
    let v = visited.get(lang)
    if (v) return v
    const d = new Lexicon({ dir: lexiconDir, language: lang, base: baseFor(lang), tutorFile: join(cfg.appDir, `lexicon.${lang}.json`), log })
    const cultures = cultureIds(culture.dir).filter(id => {
      const m = readManifest(culture.dir, id)
      return m.ok && m.manifest.language === lang
    })
    const packsRoot = process.env.LANI_PACKS_DIR ?? join(cfg.projectDir, 'companion/packs')
    const dirs = [...new Set((cultures.length ? cultures : ['']).flatMap(id => packsDirs(packsRoot, culture.dir, id, lang)))]
    // only its curated packs are read (curatedOnly): the tutor's are the village's language's
    const entry: Visited = { dict: d, download: { state: 'off' }, ready: Promise.resolve(), packs: new PackStore(dirs, join(cfg.appDir, 'packs'), log) }
    entry.ready = fetchDictionary(d, s => (entry.download = s))
    visited.set(lang, entry)
    return entry
  }

  // A lookup is a tap: the packs and the people are read again at most every few seconds, the review items when
  // their file changes.
  let shelf: { at: number; packs: LoadedPack[]; people: Person[] } = { at: 0, packs: [], people: [] }
  function lookupShelf() {
    if (Date.now() - shelf.at > 5_000) shelf = { at: Date.now(), packs: packs.all(), people: people() }
    return shelf
  }
  const sr = new Map<string, { mtime: number; items: Record<string, SrItem> }>()
  function srItems(lang: string = language) {
    const path = learner.srPath(lang)
    const mtime = existsSync(path) ? statSync(path).mtimeMs : 0
    const had = sr.get(lang)
    if (had?.mtime === mtime) return had.items
    const items = learner.srItems(lang)
    sr.set(lang, { mtime, items })
    return items
  }

  /** The village's people by name ("Tone — the blacksmith"), and the learner ("Jan — you"). */
  function people(): Person[] {
    const out: Person[] = villagers.all().map(v => {
      const given = v.name.trim().split(/\s+/).at(-1) ?? v.name
      const role = labelEn(v.role)
      return { name: v.name, given, gloss: role ? `${given} — the ${role.charAt(0).toLowerCase()}${role.slice(1)}` : `${given} — lives in the village`, emoji: v.emoji ?? null }
    })
    const me = profile.isDefault ? 'Jan' : learnerFacts(cfg.dataDir).name
    if (me) out.push({ name: me, given: me, gloss: `${me} — you`, emoji: null })
    return out
  }

  // The words added from dialogs reach the tutor gathered, in one short line: each note wakes the session.
  let noted: string[] = []
  let noteTimer: ReturnType<typeof setTimeout> | undefined
  function noteLater(word: string) {
    noted.push(word)
    noteTimer ??= setTimeout(() => {
      const words = noted
      noted = []
      noteTimer = undefined
      channel
        .notify(`App: words added from dialogs as vocabulary review items, ALREADY persisted: ${words.join(', ')}.`, { kind: 'words_added', conversation_id: 'main', msg_id: `w${Date.now().toString(36)}` })
        .catch(e => log(`words_added not sent: ${(e as Error).message}`))
    }, NOTE_DELAY_MS)
  }

  // The words a lookup had no meaning of in the learner's base: the tutor writes one (publish_gloss), gathered a few
  // seconds; each lemma once, and not more than GAPS_PER_HOUR an hour.
  type Gap = { language: string; base: string; form: string; lemma: string; pos: string; en: string[] }
  const asked = new Set<string>()
  const askedAt: number[] = []
  let gaps: Gap[] = []
  let gapTimer: ReturnType<typeof setTimeout> | undefined
  function noteGaps(r: LookupResult, lang: string, b: string) {
    if (b === 'en' || process.env.LANI_GLOSS_GAPS === 'off') return
    // the first entries a card shows: those of the dictionary, or the supplement, that are English
    const shown = r.entries.slice(0, 2).filter(e => e.fallback && (e.source === 'wiktionary' || e.source === 'extra'))
    const hourAgo = Date.now() - 3_600_000
    while (askedAt.length && askedAt[0] < hourAgo) askedAt.shift()
    for (const e of shown) {
      const key = `${lang}|${b}|${e.lemma}|${e.pos}`
      if (asked.has(key) || askedAt.length >= GAPS_PER_HOUR) continue
      asked.add(key)
      askedAt.push(Date.now())
      gaps.push({ language: lang, base: b, form: r.word, lemma: e.lemma, pos: e.pos, en: e.gloss })
    }
    if (!gaps.length) return
    gapTimer ??= setTimeout(() => {
      const all = gaps
      gaps = []
      gapTimer = undefined
      const groups = new Map<string, Gap[]>()
      for (const g of all) groups.set(`${g.language}|${g.base}`, [...(groups.get(`${g.language}|${g.base}`) ?? []), g])
      for (const list of groups.values()) {
        const { language: l, base: gb } = list[0]
        const name = LANGUAGES[gb] ?? gb
        const words = list.map(g => `${g.form} → ${g.lemma} (${g.pos}; English: ${g.en.join('; ')})`).join(', ')
        const where = l === language ? '' : `, language: "${l}"`
        channel
          .notify(
            `App: word lookup has no ${name} meaning for these words, so the card shows the English. For each, call publish_gloss {form, lemma, pos, gloss: 1-3 short ${name} meanings, gloss_lang: "${gb}"${where}}; no reply needed: ${words}.`,
            { kind: 'gloss_gaps', conversation_id: 'main', msg_id: `g${Date.now().toString(36)}` },
          )
          .catch(e => log(`gloss_gaps not sent: ${(e as Error).message}`))
      }
    }, GAP_DELAY_MS)
  }

  /** Adds a word as a vocabulary review item (update-db.py, no session: no minutes, no streak day). */
  async function handleWords(req: Request): Promise<Response> {
    const r = wordIn.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    const b = r.data
    const { base } = learnerLangs(cfg.dataDir, language)
    // a pack word becomes the same card as learning it from its pack
    const packWord = b.item_id ? packs.all().flatMap(p => p.words.map(w => ({ p, w }))).find(({ p, w }) => itemId(p.id, w.id) === b.item_id) : undefined
    if (b.item_id && !packWord && !b.item_id.startsWith('vocab_word_')) return json({ error: 'item_id: a pack word\'s or vocab_word_…, as GET /lookup gave it' }, 400)
    const id = packWord ? itemId(packWord.p.id, packWord.w.id) : (b.item_id ?? wordItemId(b.sl))
    const content = packWord ? wordOf(packWord.p, packWord.w) : b.sl
    // known already: its own item, or another vocabulary item with the same word (packs.ts learnedWords), in the
    // language it is added to
    const other = learner.other(b.language)
    const items = learner.srItems(other)
    const same = items[id] ? id : vocabularyWords(items).get(normWord(content))
    if (same) return json({ item_id: same, added: false })
    const u = learner.update({
      record_session: false,
      date: new Date().toLocaleDateString('sv'), // YYYY-MM-DD, local, as read-db.py's today
      ...(other ? { language: other } : {}),
      new_vocabulary: [{
        item_id: id,
        item_type: 'vocabulary',
        content,
        answer: packWord ? meaningOf(packWord.p, packWord.w, base) : b.en,
        category: packWord ? packWord.p.id : 'dialog',
        difficulty: packWord?.p.level ?? '',
        initial_quality: 3,
        priority: 'medium',
        // added from the word card, not answered: the app never counts it as learned from its last_quality
        source: 'lookup',
        ...(b.example_sl ? { example: b.example_sl } : {}),
        ...(b.example_en ? { example_translation: b.example_en } : {}),
      }],
    })
    if (!u.ok) return json({ error: u.output }, 500)
    events.emit({ type: 'words_added', item_ids: [id] })
    noteLater(`${content} (${b.en})${b.from ? ` from a ${b.from}` : ''}${other ? ` [language "${other}"]` : ''}`)
    return json({ item_id: id, added: true })
  }

  return {
    instructions,
    start: () => {
      if (cfg.serveApp) void fetchDictionary(dict, s => (download = s))
    },
    routes: [
      {
        method: 'GET',
        path: '/lexicon/status',
        handle: () =>
          json({
            ...dict.status(),
            download: download.state,
            ...(download.error ? { error: download.error } : {}),
            source: source ?? null,
            // the other towns' languages looked up on visits
            visits: [...visited.values()].map(v => ({ ...v.dict.status(), download: v.download.state, ...(v.download.error ? { error: v.download.error } : {}) })),
          }),
      },
      {
        method: 'GET',
        path: '/lookup',
        handle: async ({ url }) => {
          const w = url.searchParams.get('w')?.trim() ?? ''
          if (!w || w.length > 40) return json({ error: 'w: the word, 1-40 characters' }, 400)
          const at = { line: url.searchParams.get('line')?.slice(0, 1000) ?? undefined, en: url.searchParams.get('en')?.slice(0, 1000) ?? undefined }
          // on a visit: the town's language, explained in the visit's base (or the one the app asks for)
          const lang = url.searchParams.get('language')?.trim().toLowerCase() || language
          const asks = url.searchParams.get('base')?.trim().toLowerCase()
          if (!LANG.test(lang) || !LANGUAGES[lang]) return json({ error: 'language: a language code, e.g. "it"' }, 400)
          if (asks && (!LANG.test(asks) || !LANGUAGES[asks] || asks === lang)) return json({ error: 'base: a language code other than the word\'s, e.g. "sl"' }, 400)
          const b = asks ?? baseFor(lang)
          let r: LookupResult
          if (lang === language) {
            const { packs: loaded, people: known } = lookupShelf()
            r = lookup(w, at, { lexicon: dict, base: b, packs: loaded, people: known, items: srItems(), partners })
          } else {
            const v = visitedOf(lang)
            await Promise.race([v.ready, Bun.sleep(WAIT_MS)])
            r = lookup(w, at, { lexicon: v.dict, base: b, packs: v.packs.curatedOnly(), people: [], items: srItems(lang) })
          }
          noteGaps(r, lang, b)
          return json(r)
        },
      },
      { method: 'POST', path: '/words', handle: ({ req, url }) => outbox.once(req, url.pathname, handleWords) },
      {
        method: 'GET',
        path: '/forms',
        // ?w=sesti&w=hiša: each word's forms by slot, the page each counts on, and the content's lines with them
        handle: ({ url }) => {
          const words = url.searchParams.getAll('w').map(w => w.trim())
          if (!words.length || words.length > FORMS_WORDS || words.some(w => !w || w.length > 80)) return json({ error: `w: 1-${FORMS_WORDS} words, each 1-80 characters` }, 400)
          const asks = url.searchParams.get('language')?.trim().toLowerCase()
          if (asks && asks !== language) return json({ error: `language: the village's language only ("${language}")` }, 400)
          const { index, pages } = content()
          const c = { dict, book, pages, lines: index, language }
          const out = words.map(word => ({ word, entries: formsOf(c, word) }))
          const wiktionary = out.some(w => w.entries.some(e => e.table === 'wiktionary'))
          return json({ language, words: out, attribution: wiktionary ? ATTRIBUTION : null })
        },
      },
      // what POST /words takes as its `from`: the app sends "reading" only when it is here
      { method: 'GET', path: '/words', handle: () => json({ from: WORD_SOURCES }) },
    ],
    tools: [
      {
        name: 'publish_gloss',
        description:
          "Explain a word for the app's word lookup: one the dictionary didn't know (Jan asked about it), or one it knew only in English (a gloss_gaps note). The form as it appears in the line, its lemma (base form), part of speech, 1-3 short glosses, the language they are in (gloss_lang, default Jan's base) and the grammar of this form. The next lookup of the form or the lemma shows it: as the meaning of the dictionary's entry of that lemma, or as an entry of its own, flagged machine-written. Replaces your earlier gloss of the same form, lemma and gloss language.",
        inputSchema: {
          type: 'object',
          properties: {
            form: { type: 'string', description: 'The word as it appears in the line, e.g. "prinesel"' },
            lemma: { type: 'string', description: 'Its base form, e.g. "prinesti"' },
            pos: { type: 'string', description: 'Part of speech: noun, verb, adj, adv, pron, prep, conj, particle, intj, phrase' },
            gloss: { type: 'array', items: { type: 'string' }, description: '1-3 short meanings, e.g. ["to bring"]' },
            grammar: { type: 'string', description: 'What this form is, e.g. "masculine singular l-participle"' },
            note: { type: 'string', description: 'Optional usage note, one line' },
            gloss_lang: { type: 'string', description: 'The language the glosses are in: "sl", "de", "it" or "en"; default Jan\'s base' },
            language: { type: 'string', description: "The word's language, e.g. \"it\" on a visit to an Italian town; default the village's" },
          },
          required: ['form', 'lemma', 'gloss'],
        },
        handle: args => {
          const r = glossIn.safeParse(args)
          if (!r.success) return fail(`gloss invalid, nothing kept:\n${z.prettifyError(r.error)}`)
          const lang = r.data.language ?? language
          const glossLang = r.data.gloss_lang ?? baseFor(lang)
          if (!LANGUAGES[lang] || !LANGUAGES[glossLang]) return fail(`language and gloss_lang are language codes (${Object.keys(LANGUAGES).join(', ')}); nothing kept`)
          const d = lang === language ? dict : visitedOf(lang).dict
          const g = d.publish({ ...r.data, gloss_lang: glossLang })
          events.emit({ type: 'lexicon_updated', word: g.form, ...(lang !== language ? { language: lang } : {}) })
          return ok(`kept: ${g.form} → ${g.lemma} (${g.gloss.join('; ')}; ${LANGUAGES[glossLang]}${lang !== language ? `, a ${LANGUAGES[lang]} word` : ''}); the next lookup shows it`)
        },
      },
    ],
  }
}
