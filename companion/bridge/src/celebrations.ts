// lani.celebration/v0: how a village celebrates reaching an age (companion/GAME.md "Ages and their celebrations"). In
// Primorska the first camp's evening at the fire, the likof when the new roof is up, the veselica, the first semenj on the
// square, the town's feast with its procession. One file per age in a culture pack's celebrations/ (<age>.json), in the
// pack's language: the tradition's name, what it is, what the moment says as the decorations go up, the chronicle's line
// once the party was had, how the people remember it; the decorations, the music, where the party is; and the party that
// evening: its host and guests (villager ids), its word pack and the words its turns test, its dialog at each level (a scene
// dialog's lines: a toast, a song, a dance, a small task), played over the village with all the village on its stage.
//
// The app bundles the files with the pack (app/build.gradle.kts, CulturePacks) and plays them (game/AgeUps.kt); an age
// without a file has a gathering of the village, with no dialog. The bridge checks them with the pack (validateCulture). A
// bridge from before them never reads the directory: the content can go out before the code that plays it.
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { learnerErrorsIn, render, addressee as addresseeFor } from './addressee'
import { PACK_LANGUAGES } from './arrivals'
import { wordsIn } from './dialog-words'
import { lexiconLemmas, linesErrors, looksLikeFormOf, speakerGendered, testedIn, type Lemmas, type StepPack } from './project-steps'
import { schemaField } from './schema'
import { dialogLine, SCENE_LANGUAGES } from './scenes'
import { LEVELS } from './stories'

export const CELEBRATION_SCHEMA = 'lani.celebration/v0'
/** The ages a village celebrates reaching (the first, the campfire, it starts with). */
export const CELEBRATION_AGES = ['tabor', 'zaselek', 'vas', 'trg', 'mesto'] as const
/** What decorates the village (the app's Decor). */
export const DECOR = ['torches', 'spruce', 'bunting', 'floor', 'stalls', 'banners'] as const
/** The music of the moment (the app's Music: the harmonika's waltz and polka, the band's polka). */
export const MUSIC = ['valcek', 'harmonika', 'godba'] as const
/** The levels every curated party is written for. */
export const PARTY_LEVELS = ['A1', 'A2'] as const
/** A party's dialog: 4 to 10 lines, 2 to 4 turns for the learner (a toast, a song, a dance, a small task). */
export const PARTY_LINES = { min: 4, max: 10 }
export const PARTY_TURNS = { min: 2, max: 4 }
/** A party's turns test 1 to 4 words of its pack. */
export const PARTY_WORDS = 4

const id = z.string().regex(/^[a-z0-9][a-z0-9-]{0,62}$/, 'kebab-case, 1-63 chars')
const villager = z.string().regex(/^[a-z][a-z0-9_-]{0,39}$/, 'a villager id')
const str = z.string().trim().min(1)
/** {"sl": "…", "en": "…", "de": "…", "it": "…"}: a language missing is named by the check (every language the pack serves). */
const text = z.partialRecord(z.enum(SCENE_LANGUAGES), str).refine(t => Object.keys(t).length > 0, 'a text in at least one language')

/** One level's party: its lines, as a scene dialog's. */
const telling = z.strictObject({ lines: z.array(dialogLine).min(PARTY_LINES.min).max(PARTY_LINES.max) })

export const celebrationSpec = z.strictObject({
  schema: schemaField(CELEBRATION_SCHEMA),
  age: z.enum(CELEBRATION_AGES),
  language: z.enum(SCENE_LANGUAGES),
  review: str.optional(),
  emoji: str.default('🎉'),
  name: text,
  about: text.optional(),
  moment: text.optional(),
  line: text.optional(),
  memory: text.optional(),
  decor: z.array(z.enum(DECOR)).max(DECOR.length).default([]),
  music: z.enum(MUSIC).optional(),
  place: z.enum(['fire', 'square']).optional(),
  host: villager.optional(),
  guests: z.array(villager).max(8).default([]),
  pack: id.optional(),
  words: z.array(id).max(PARTY_WORDS).default([]),
  levels: z.partialRecord(z.enum(LEVELS), telling).default({}),
})
export type Celebration = z.infer<typeof celebrationSpec>

/** What the check of a pack's celebrations reads: its language, its cast, the packs and grammar pages it may name. */
export type CelebrationRefs = {
  /** The pack's language: every text has it, and every language the pack serves. */
  lang: string
  /** The cast's ids (its villagers directory); without them, the host and guests aren't checked. */
  cast?: Set<string>
  /** The word packs the village is served; without them, the words aren't checked against their pack. */
  packs?: StepPack[]
  /** The grammar book's pages of the pack's language; without them, a turn's page isn't checked. */
  pages?: Set<string>
  /** Curated: the party written at A1 and A2, its words declared. */
  curated?: boolean
  /** The dictionary's lemmas of a word ([lexiconLemmas]); without it a word's forms are guessed by their stem. */
  lemmas?: Lemmas
}

const MAN = addresseeFor({ name: 'Learner' })

/**
 * The problems of one age's celebration [f] (in [r.lang], the pack's): every text in every language the pack serves, the
 * host and the guests of the cast, and a party only with a host; each level's lines as a dialog of the pack (a reaction and
 * a why to every wrong choice, 2-4 turns), said by the host (at least once) and the guests, a guest's lines that anyone
 * could say (one away is stood in for); no cue (it is played over the village: no picture, no weather, no stage); its words
 * in its pack, each tested by a turn at every level; the grammar pages its turns name.
 */
export function celebrationErrors(f: Celebration, r: CelebrationRefs): string[] {
  const errs: string[] = [...learnerErrorsIn(f)]
  if (f.language !== r.lang) errs.push(`language: "${f.language}", not the pack's "${r.lang}"`)
  const langs = [...new Set([r.lang, ...PACK_LANGUAGES])]
  for (const k of ['name', 'about', 'moment', 'line', 'memory'] as const) {
    const t = f[k]
    if (!t) {
      if (r.curated && k !== 'memory') errs.push(`${k}: missing (a curated celebration says it)`)
      continue
    }
    const missing = langs.filter(x => typeof (t as Record<string, string>)[x] !== 'string')
    if (missing.length) errs.push(`${k}: no ${missing.map(x => `"${x}"`).join(', ')} text (every language the pack serves)`)
  }
  const party = Object.keys(f.levels).length > 0
  if (party && !f.host) errs.push('host: a party needs its host (a villager of the cast)')
  if (r.cast) for (const v of [f.host, ...f.guests].filter((x): x is string => !!x)) if (!r.cast.has(v)) errs.push(`host, guests: no villager "${v}" in the pack's villagers`)
  if (f.guests.includes(f.host ?? '')) errs.push('guests: the host is among the guests')
  const pack = f.pack ? r.packs?.find(x => x.id === f.pack) : undefined
  if (f.pack && r.packs && !pack) errs.push(`pack: no word pack "${f.pack}" in the packs the village is served`)
  if (!f.pack && f.words.length) errs.push('pack: it declares words, but names no pack')
  if (r.curated && party) {
    const missing = PARTY_LEVELS.filter(l => !f.levels[l])
    if (missing.length) errs.push(`levels: no ${missing.join(', ')} (a curated party is written for ${PARTY_LEVELS.join(' and ')} at least)`)
    if (!f.words.length) errs.push(`words: none (a party's turns test 1-${PARTY_WORDS} of its pack's words)`)
    if (!f.pack) errs.push('pack: none (a curated party has its word pack)')
  }
  const speakers = new Set([f.host, ...f.guests].filter((x): x is string => !!x))
  const guests = new Set(f.guests)
  const words = f.words.map(w => ({ id: w, word: pack?.words.find(x => x.id === w) }))
  for (const w of words) if (pack && !w.word) errs.push(`words: no word "${w.id}" in pack "${pack.id}"`)
  for (const [lv, t] of Object.entries(f.levels)) {
    if (!t) continue
    const here = `levels.${lv}`
    errs.push(...linesErrors(t.lines, r.lang, speakers, here, PARTY_TURNS.max, 'the host nor a guest'))
    const turns = t.lines.filter(l => l.choices.length).length
    if (turns < PARTY_TURNS.min) errs.push(`${here}: ${turns} turn(s): a party has ${PARTY_TURNS.min} at least (a toast, a dance …)`)
    if (f.host && !t.lines.some(l => l.who === f.host)) errs.push(`${here}: the host (${f.host}) says nothing`)
    t.lines.forEach((l, li) => {
      const ln = `${here}.lines[${li}]`
      if (l.act || l.choices.some(c => c.reply?.act)) errs.push(`${ln}: a party's people are who is here tonight: no act cue`)
      if (l.sky || l.fx || l.choices.some(c => c.reply?.sky || c.reply?.fx)) errs.push(`${ln}: a party is played over the village: no sky or fx cue`)
      if (l.choices.some(c => c.tap !== undefined)) errs.push(`${ln}: a party has no picture to tap in: no tap turn`)
      if (l.who && guests.has(l.who) && r.lang === 'sl') {
        const said = render(String((l as Record<string, unknown>).sl ?? ''), MAN)
        if (speakerGendered(said)) errs.push(`${ln}: a guest's line says the speaker's gender ("${said}"): another may say it when they're away; say it in the plural or the present`)
      }
    })
    // its words: each tested by a turn (a wrong choice differs from the right one in it)
    const tested = testedIn(t.lines, r.lang)
    for (const w of words) {
      const lemma = w.word ? String(w.word[r.lang] ?? '') : ''
      if (!lemma) continue
      const parts = wordsIn(lemma)
      const formOf = (x: string) => (r.lemmas ? r.lemmas(x).includes(parts[0].toLowerCase()) : looksLikeFormOf(x, parts[0]))
      if (parts.length === 1 && tested.words.some(formOf)) continue
      const near = parts.length === 1 && r.lemmas ? tested.words.find(x => looksLikeFormOf(x, parts[0])) : undefined
      errs.push(`${here}: the party's word "${lemma}" (${w.id}) is in no turn's test: a wrong choice differs from the right one in it ("Daj mi kozarec." / "Daj mi žlico.")` +
        (near ? `; "${near}" is tested, but the dictionary doesn't know it as a form of "${lemma}" (companion/lexicon/${r.lang}.extra.json, or another form)` : ''))
    }
    if (r.pages) for (const [li, l] of t.lines.entries()) {
      const named = [l.grammar, ...l.choices.map(c => c.grammar)].filter((g): g is string => !!g)
      for (const g of named) if (!r.pages.has(g)) errs.push(`${here}.lines[${li}]: no grammar page "${g}" in companion/grammar/${r.lang}`)
    }
  }
  return errs
}

/** The directory of a pack's celebrations. */
export const celebrationsDir = (culturesDir: string, cultureId: string) => join(culturesDir, cultureId, 'celebrations')

/**
 * Pack [cultureId]'s celebrations (celebrations/<age>.json), read and checked: each named for its age, valid
 * ([celebrationErrors]; curated), and every problem with its file. None when the pack has no such directory.
 */
export function cultureCelebrations(
  culturesDir: string, cultureId: string, lang: string, o: { cast?: Set<string>; packs?: StepPack[]; pages?: Set<string>; lemmas?: Lemmas } = {},
): { files: Celebration[]; errors: string[] } {
  const dir = celebrationsDir(culturesDir, cultureId)
  if (!existsSync(dir)) return { files: [], errors: [] }
  const files: Celebration[] = []
  const errors: string[] = []
  for (const name of readdirSync(dir).filter(x => x.endsWith('.json')).sort()) {
    const where = `${cultureId}/celebrations/${name}`
    try {
      const r = celebrationSpec.safeParse(JSON.parse(readFileSync(join(dir, name), 'utf8')))
      if (!r.success) { errors.push(`${where}:\n${z.prettifyError(r.error)}`); continue }
      if (`${r.data.age}.json` !== name) errors.push(`${where}: age "${r.data.age}", but the file is ${name}`)
      const lemmas = o.lemmas ?? lexiconLemmas(join(culturesDir, '..', 'lexicon'), lang)
      errors.push(...celebrationErrors(r.data, { lang, cast: o.cast, packs: o.packs, pages: o.pages, curated: true, lemmas }).map(e => `${where}: ${e}`))
      files.push(r.data)
    } catch (e) {
      errors.push(`${where}: ${(e as Error).message}`)
    }
  }
  return { files, errors }
}

/** Every telling of [f]'s party: its level and its lines (voice-build voices them, each in its speaker's voice). */
export function partyTellings(f: Celebration): { level: string; lines: z.infer<typeof dialogLine>[] }[] {
  return LEVELS.flatMap(lv => (f.levels[lv] ? [{ level: lv, lines: f.levels[lv]!.lines }] : []))
}

/**
 * Pack [cultureId]'s celebrations as files, read without their check (the bridge's check at start says what's wrong):
 * what voice-build and the voice corpus voice. None when the pack has no such directory.
 */
export function cultureCelebrationFiles(culturesDir: string, cultureId: string): Celebration[] {
  const dir = celebrationsDir(culturesDir, cultureId)
  if (!existsSync(dir)) return []
  return readdirSync(dir).filter(x => x.endsWith('.json')).sort().flatMap(name => {
    try {
      const r = celebrationSpec.safeParse(JSON.parse(readFileSync(join(dir, name), 'utf8')))
      return r.success ? [r.data] : []
    } catch {
      return []
    }
  })
}
