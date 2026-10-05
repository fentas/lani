// lani.story/v0 — the storyteller's evening stories (companion/SCENES.md, "Stories: the evening story"): a legend
// the village's storyteller (Stari Janez, Nonno Bepi) tells by the fire, one an evening, at the learner's level. A story
// has level variants (A1, A2, B1 …), each a dialog: the teller's narration in short parts and the learner's turns
// between them (what happened, how they feel about it); a long legend is told in chapters, one an evening. The curated
// ones are a culture pack's (companion/cultures/<id>/stories/*.json); the tutor publishes more to <data>/app/stories
// (a tutor story with a curated id replaces it). The bridge serves each scene where someone tells stories with the
// stories of that teller in the scene's language (attachStories); the app picks tonight's (game/scene/Stories.kt).
// Every story heard is written down in the learner's story notebook (game/scene/Notebook.kt): its notes (the story as
// prose, per telling) or, without them, its telling, with its pictures from the vignette library (VIGNETTES) as sketches;
// a story may go on from another (continues). When the stories left at the learner's level run low, the tutor is asked
// for more (features/stories.ts, stories_low).
// Keep in sync with .claude/skills/lani-studio/reference/module-spec.md ("Stories").
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, statSync, unlinkSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { learnerErrorsIn } from './addressee'
import { schemaField } from './schema'
import { label, labelEn, labelTarget, langText, type Label } from './langs'
import { dialogLine, offStage, reactionErrors, reply, SCENE_ART, SCENE_LANGUAGES, staysErrors, type ResolvedScene, type SceneRefs } from './scenes'

export const STORY_SCHEMA = 'lani.story/v0'
/** The levels a story can be told at, easiest first. */
export const LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'] as const
export type Level = (typeof LEVELS)[number]
/** Stories are told by the fire: their fx cues are the campfire's (the fire flares, the kettle steams, a distant bell). */
export const STORY_ART = 'campfire'
/** The levels every curated story has, in every chapter (the tutor's may have one: the learner's). */
export const CURATED_LEVELS: Level[] = ['A1', 'A2']

const id = z.string().regex(/^[a-z0-9][a-z0-9-]{0,62}$/, 'must be kebab-case, 1-63 chars')

/**
 * The vignette library of the stories' pictures (companion/SCENES.md, "The story notebook"): what a story's
 * pictures can be made of, as the app paints them in ink (game/scene/Vignettes.kt; the smoke test parses it and
 * compares, so the two can't drift).
 */
export const VIGNETTES = {
  backgrounds: ['mountains', 'lake', 'castle', 'village', 'forest', 'church', 'sea', 'cave', 'river', 'bridge', 'meadow', 'town'],
  figures: [
    'king', 'girl', 'woman', 'boy', 'old-man', 'peasant', 'hunter', 'knight', 'soldier', 'giant', 'fairy', 'water-man',
    'devil', 'goblin', 'dragon', 'chamois', 'horse', 'ox', 'boar', 'dog', 'cat', 'bird', 'boat', 'sheep',
  ],
  props: [
    'crown', 'sword', 'bell', 'tree', 'spruce', 'cloud', 'sun', 'moon', 'stars', 'flower', 'rock', 'barrel', 'chest',
    'sack', 'club', 'table', 'fire', 'house', 'chapel', 'wall',
  ],
} as const
/** At most this many figures and props in a picture (Vignettes.MAX_FIGURES, MAX_PROPS). */
export const MAX_FIGURES = 6
export const MAX_PROPS = 8
/** How many pictures a curated story has: 2-4 told in one evening, one or two an evening in chapters. */
export const CURATED_PICTURES = { min: 2, max: 4, perChapter: 2 }

const unit = z.number().min(0).max(1)
/** A figure or a prop where it stands: x across (0 left … 1 right), y up from the ground (0 in front … 1 at the top). */
const thing = (ids: readonly [string, ...string[]]) =>
  z.strictObject({ id: z.enum(ids), x: unit, y: unit.optional(), size: z.number().min(0.5).max(2).optional(), flip: z.boolean().optional(), gold: z.boolean().optional() })
/**
 * A picture of the story's book: in evening `chapter` (from 1; a story of one evening: 1), after the telling's `after`th
 * paragraph (the teller's lines between two of the learner's turns; 0: at the chapter's head), a background, figures
 * and props from the library, and its caption in the story's language and English (and the title's other languages).
 */
const picture = z.strictObject({
  chapter: z.number().int().min(1).max(8).optional(),
  after: z.number().int().min(0).max(16),
  bg: z.enum(VIGNETTES.backgrounds),
  night: z.boolean().optional(),
  figures: z.array(thing(VIGNETTES.figures)).max(MAX_FIGURES).default([]),
  props: z.array(thing(VIGNETTES.props)).max(MAX_PROPS).default([]),
  caption: z.strictObject({ sl: z.string().trim().min(1).max(160).optional(), en: z.string().trim().min(1).max(160).optional(), it: z.string().trim().min(1).max(160).optional(), de: z.string().trim().min(1).max(160).optional() }),
}).refine(p => p.figures.length + p.props.length > 0, 'a picture has a figure or a prop at least')

/** A paragraph of the notebook: the story's language and English (and the others), a few sentences. */
const paragraph = z.strictObject({
  sl: z.string().trim().min(1).max(600).optional(),
  en: z.string().trim().min(1).max(600).optional(),
  it: z.string().trim().min(1).max(600).optional(),
  de: z.string().trim().min(1).max(600).optional(),
})
/**
 * One telling written down (companion/SCENES.md, "The story notebook"): evening `chapter` (from 1; a story of one evening:
 * 1) told at `level`, as the learner would have written it down listening: the story as prose in the past tense, without
 * the teller's asides and the learner's turns, at the telling's level; `after`: the paragraph each of the evening's
 * pictures follows, in their order (0: at the head). A story without notes is written down from its telling. A bridge
 * from before the notebook leaves them out.
 */
const note = z.strictObject({
  chapter: z.number().int().min(1).max(8).optional(),
  level: z.enum(LEVELS),
  after: z.array(z.number().int().min(0).max(12)).max(4).optional(),
  text: z.array(paragraph).min(1).max(12),
})

/** One telling at one level: the dialog, and whether the sky and the effects it leaves stay (as a scene dialog's). */
const variant = z.strictObject({
  lines: z.array(dialogLine).min(3).max(16),
  sky_stays: z.boolean().optional(),
  fx_stays: z.boolean().optional(),
})
const levels = z
  .partialRecord(z.enum(LEVELS), variant)
  .refine(l => Object.keys(l).length > 0, 'at least one level: {"A1": {"lines": […]}}')
/** One evening of a long legend: what it's about (the teaser "Jutri: …" names it) and its levels. */
const chapter = z.strictObject({ title: label(80).optional(), teaser: reply, levels })
/** A word the story teaches: its id in the story's pack, or {word, pack} from another pack. */
const word = z.union([id, z.strictObject({ word: id, pack: id })])

export const storySpec = z
  .object({
    schema: schemaField(STORY_SCHEMA),
    id: z.string().regex(/^[a-z0-9][a-z0-9-]{1,62}$/, 'id must be kebab-case, 2-63 chars'),
    /** The language it is told in: the village's (a Slovene story {"sl", "en"}; an Italian one {"it", "sl", "en"}). */
    language: z.enum(SCENE_LANGUAGES).default('sl'),
    /** "Zlatorog · Goldhorn", or per language. */
    title: label(80),
    /** Who tells it: a villager id of the cast, the storyteller (Stari Janez, Nonno Bepi). */
    teller: id,
    emoji: z.string().trim().min(1).max(16).default('📖'),
    /** Where it comes in the rotation (the curated ones); the tutor's come after them, oldest first. */
    order: z.number().int().min(0).max(999).optional(),
    /** For writers and the tutor: where the legend comes from, what it is. */
    about: langText(400).optional(),
    /** What the teller tells, a whole sentence for "Jutri: …" / "Nocoj: …": "Janez pove, kako je Krpan srečal velikana". */
    teaser: reply,
    /** What the teller remembers afterwards, fitting their remember lines: "zgodbo o Zlatorogu, ki sem ti jo povedal". */
    memory: reply,
    /** The word pack its words are in. */
    pack: id.optional(),
    words: z.array(word).max(24).default([]),
    /** Told in one evening: its levels. */
    levels: levels.optional(),
    /** Told over several evenings, one chapter an evening, in order. */
    chapters: z.array(chapter).min(2).max(8).optional(),
    /**
     * The story it goes on from (its id): a new chapter or a sequel of one the learner liked ("Martin Krpan se vrne"). It
     * is told after that one, and its book joins that one's. A bridge from before the books leaves it out.
     */
    continues: id.optional(),
    /** Its pictures, from the vignette library (the notebook sketches them). A bridge from before the books leaves them out. */
    pictures: z.array(picture).max(16).optional(),
    /** What the learner would have written down of each telling: the story notebook's text. */
    notes: z.array(note).max(64).optional(),
    /** Who wrote or checked what ("notes: machine-written (Claude) …; not reviewed by a native speaker"), for writers. */
    review: z.string().trim().min(1).max(240).optional(),
  })
  .refine(s => !!s.levels !== !!s.chapters, 'a story has its levels (told in one evening) or its chapters (one an evening), not both')

export type Story = z.infer<typeof storySpec>
export type Chapter = z.infer<typeof chapter>
export type Picture = z.infer<typeof picture>
export type Note = z.infer<typeof note>
export type StorySource = 'curated' | 'tutor'
/** [culture]: the culture pack a curated story comes from (companion/cultures/<id>/stories). */
export type LoadedStory = Story & { source: StorySource; published_at?: number; culture?: string }

/** A story's evenings: its chapters, or its one telling. */
export const chaptersOf = (s: Pick<Story, 'chapters' | 'teaser' | 'levels'>): Chapter[] => s.chapters ?? [{ teaser: s.teaser, levels: s.levels ?? {} }]

/** The levels a story can be told at (every chapter has them), easiest first. */
export const levelsOf = (s: Story): Level[] => LEVELS.filter(l => chaptersOf(s).every(c => c.levels[l]))

/**
 * How many paragraphs a telling's book has: the runs of the teller's lines between two of the learner's turns
 * (StoryBooks.paragraphs in the app).
 */
export function paragraphsOf(lines: { choices?: unknown[] }[]): number {
  let n = 0
  let run = false
  for (const l of lines) {
    if (l.choices?.length) run = false
    else if (!run) { run = true; n++ }
  }
  return n
}

/** The problems of [s]'s pictures: the evening each stands in, where in it, its caption's languages. */
function pictureErrors(s: Story): string[] {
  const errs: string[] = []
  const chapters = chaptersOf(s)
  ;(s.pictures ?? []).forEach((p, i) => {
    const at = `pictures[${i}]`
    const ch = (p.chapter ?? 1) - 1
    if (ch >= chapters.length) {
      errs.push(`${at}.chapter: ${p.chapter}, but the story has ${chapters.length === 1 ? 'one evening' : `${chapters.length} chapters`}`)
      return
    }
    const most = Math.max(...Object.values(chapters[ch].levels).map(v => paragraphsOf(v!.lines)))
    if (p.after > most) errs.push(`${at}.after: ${p.after}, but its evening's tellings have at most ${most} paragraphs (the teller's lines between the learner's turns)`)
    const lang = s.language
    if (!p.caption[lang]) errs.push(`${at}.caption: no "${lang}" text (the story's language)`)
    if (!p.caption.en) errs.push(`${at}.caption: no "en" text (the English translation)`)
  })
  return errs
}

/**
 * The problems of [s]'s notes: each of a telling the story has (its evening and level), once; every paragraph in the
 * story's language and English; its `after` a paragraph for each of the evening's pictures.
 */
export function noteErrors(s: Story): string[] {
  const errs: string[] = []
  const chapters = chaptersOf(s)
  const seen = new Set<string>()
  ;(s.notes ?? []).forEach((n, i) => {
    const at = `notes[${i}]`
    const ch = (n.chapter ?? 1) - 1
    if (ch >= chapters.length) {
      errs.push(`${at}.chapter: ${n.chapter}, but the story has ${chapters.length === 1 ? 'one evening' : `${chapters.length} chapters`}`)
      return
    }
    if (!chapters[ch].levels[n.level]) errs.push(`${at}.level: ${n.level}, but its evening isn't told at ${n.level}`)
    const key = `${ch + 1}/${n.level}`
    if (seen.has(key)) errs.push(`${at}: evening ${ch + 1} at ${n.level} has notes already`)
    seen.add(key)
    n.text.forEach((p, j) => {
      if (!p[s.language]) errs.push(`${at}.text[${j}]: no "${s.language}" text (the story's language)`)
      if (!p.en) errs.push(`${at}.text[${j}]: no "en" text (the English translation)`)
    })
    const pictures = (s.pictures ?? []).filter(p => (p.chapter ?? 1) === ch + 1).length
    if (n.after) {
      if (n.after.length !== pictures) errs.push(`${at}.after: ${n.after.length} places, but its evening has ${pictures} pictures`)
      n.after.forEach((a, k) => { if (a > n.text.length) errs.push(`${at}.after[${k}]: ${a}, but it has ${n.text.length} paragraphs`) })
    }
  })
  return errs
}

/**
 * The pictures a curated story wants (a culture's check): 2-4 for a story of one evening, and in chapters one at least
 * and two at most an evening.
 */
export function curatedPictureErrors(s: Story): string[] {
  const pics = s.pictures ?? []
  const chapters = chaptersOf(s)
  const { min, max, perChapter } = CURATED_PICTURES
  if (!s.chapters) return pics.length < min || pics.length > max ? [`pictures: ${pics.length}, a curated story has ${min}-${max}`] : []
  return chapters.flatMap((_, i) => {
    const n = pics.filter(p => (p.chapter ?? 1) === i + 1).length
    return n < 1 || n > perChapter ? [`pictures: chapter ${i + 1} has ${n}, a curated story has 1-${perChapter} an evening`] : []
  })
}

/** What the validator looks references up in, when given: the packs (the words) and the cast (the teller, with their role). */
export type StoryRefs = { packs?: SceneRefs['packs']; villagers?: { id: string; role?: Label }[] }

/** The references and the rules the format can't say: the teller's lines, the turns, the texts' languages, the words. */
export function crossCheck(s: Story, refs?: StoryRefs): string[] {
  const errs: string[] = []
  const lang = s.language
  const inLang = (t: Partial<Record<string, unknown>> | undefined, at: string) => {
    if (!t) return
    if (typeof t[lang] !== 'string') errs.push(`${at}: no "${lang}" text (the story's language)`)
    if (typeof t.en !== 'string') errs.push(`${at}: no "en" text (the English translation)`)
  }
  const titled = (l: Label | undefined, at: string) => {
    if (l !== undefined && typeof l !== 'string' && !labelTarget(l, lang)) errs.push(`${at}: no "${lang}" text (the story's language)`)
  }
  titled(s.title, 'title')
  inLang(s.teaser, 'teaser')
  inLang(s.memory, 'memory')
  const effects = SCENE_ART.effects[STORY_ART]
  const effectsOf = (fx: Record<string, unknown> | undefined, at: string) => {
    for (const name of Object.keys(fx ?? {}))
      if (!effects.includes(name)) errs.push(`${at}: stories are told by the fire: no effect "${name}" (effects: ${effects.join(', ')})`)
  }
  const chapters = chaptersOf(s)
  const sets = chapters.map(c => LEVELS.filter(l => c.levels[l]).join(','))
  if (new Set(sets).size > 1) errs.push(`every chapter needs the same levels (they have ${sets.map(x => `[${x}]`).join(', ')})`)
  chapters.forEach((c, ci) => {
    const where = s.chapters ? `chapters[${ci}]` : 'levels'
    if (s.chapters) {
      titled(c.title, `${where}.title`)
      inLang(c.teaser, `${where}.teaser`)
    }
    for (const lv of LEVELS) {
      const v = c.levels[lv]
      if (!v) continue
      const at = `${s.chapters ? `${where}.levels` : 'levels'}.${lv}`
      v.lines.forEach((l, li) => {
        const here = `${at}.lines[${li}]`
        effectsOf(l.fx, here)
        l.choices.forEach((ch, chi) => effectsOf(ch.reply?.fx, `${here}.choices[${chi}].reply`))
        if (l.choices.length) {
          if (l.who || SCENE_LANGUAGES.some(k => l[k])) errs.push(`${here}: a choice turn has no who/${lang}/en`)
          if (l.choices.length < 2) errs.push(`${here}: a choice turn needs 2-3 choices`)
          if (!l.choices.some(ch => ch.ok)) errs.push(`${here}: no choice is ok`)
          l.choices.forEach((ch, chi) => {
            if (!ch.ok && !ch.why) errs.push(`${here}.choices[${chi}]: a wrong choice needs a why`)
            errs.push(...reactionErrors(ch, `${here}.choices[${chi}]`))
            inLang(ch, `${here}.choices[${chi}]`)
            inLang(ch.reply, `${here}.choices[${chi}].reply`)
          })
        } else {
          if (!l[lang] || !l.en) errs.push(`${here}: a spoken line needs ${lang} and en`)
          if (l.who && l.who !== s.teller) errs.push(`${here}: who "${l.who}": the teller tells the story (leave who out, or "${s.teller}")`)
        }
      })
      if (v.lines[0]?.choices.length) errs.push(`${at}: the first line must be the teller's, not the learner's choices`)
      if (!v.lines.some(l => l.choices.length)) errs.push(`${at}: no turn for the learner (a question about the story, or a reaction, between the parts)`)
      errs.push(...staysErrors(v, at))
      errs.push(...offStage(v, at, 'a story'))
    }
  })

  // its words, in their packs
  const packs = refs?.packs && new Map(refs.packs.map(p => [p.id, new Set(p.words.map(w => w.id))]))
  const seen = new Set<string>()
  s.words.forEach((w, i) => {
    const { word, pack } = typeof w === 'string' ? { word: w, pack: s.pack } : w
    const at = `words[${i}] (${word})`
    if (seen.has(`${pack}/${word}`)) errs.push(`${at}: twice`)
    seen.add(`${pack}/${word}`)
    if (!pack) errs.push(`${at}: no pack (set the story's pack, or {"word", "pack"})`)
    else if (packs) {
      const words = packs.get(pack)
      if (!words) errs.push(`${at}: unknown pack "${pack}"`)
      else if (!words.has(word)) errs.push(`${at}: pack "${pack}" has no word "${word}"`)
    }
  })
  // its pictures, its notes, and the story it goes on from
  errs.push(...pictureErrors(s))
  errs.push(...noteErrors(s))
  if (s.continues === s.id) errs.push(`continues: "${s.id}" is the story itself (name the story it goes on from)`)
  // the teller: someone of the cast, whose role is the storyteller's
  const teller = refs?.villagers?.find(v => v.id === s.teller)
  if (refs?.villagers && !teller) errs.push(`teller: "${s.teller}" is not in the cast (list_villagers shows the ids)`)
  if (teller?.role !== undefined && !/storyteller/i.test(labelEn(teller.role))) errs.push(`teller: "${s.teller}" isn't the village's storyteller (their role is "${labelEn(teller.role)}")`)
  return errs
}

export function validateStory(input: unknown, refs?: StoryRefs): { ok: true; story: Story } | { ok: false; errors: string } {
  const r = storySpec.safeParse(input)
  if (!r.success) return { ok: false, errors: z.prettifyError(r.error) }
  const errs = [...learnerErrorsIn(r.data), ...crossCheck(r.data, refs)]
  return errs.length ? { ok: false, errors: errs.map(e => `✖ ${e}`).join('\n') } : { ok: true, story: r.data }
}

/** A word of a story as the app gets it: its id and pack, and the pack word's texts in every language it has. */
export type ResolvedWord = { id: string; pack?: string; en: string; sl?: string; it?: string; de?: string; emoji?: string; gender?: string }
export type ResolvedStory = Omit<LoadedStory, 'words'> & { words: ResolvedWord[] }

/**
 * The story with its words resolved from the packs: each with the pack word's texts. A word no pack has is left out: the
 * story is told all the same (the words are what its end offers to learn).
 */
export function resolveStory(s: LoadedStory, packs: SceneRefs['packs']): ResolvedStory {
  const byId = new Map(packs.map(p => [p.id, p]))
  return {
    ...s,
    words: s.words.flatMap(w => {
      const { word, pack } = typeof w === 'string' ? { word: w, pack: s.pack } : w
      const pw = byId.get(pack ?? '')?.words.find(x => x.id === word) as (Record<string, string | undefined> & { en: string }) | undefined
      if (!pw) return []
      const texts = Object.fromEntries(SCENE_LANGUAGES.filter(k => k !== 'en' && pw[k]).map(k => [k, pw[k]]))
      return [{ id: word, pack, ...texts, en: pw.en, emoji: pw.emoji, gender: pw.gender }]
    }),
  }
}

/**
 * [scene] as the app gets it: when someone in it tells stories (a happening with `stories`), with the stories of those
 * tellers (their villager ids) in the scene's language, in the rotation's order ([stories] is); otherwise as it is.
 */
export function attachStories<T extends ResolvedScene>(scene: T, stories: ResolvedStory[]): T & { stories?: ResolvedStory[] } {
  const tellers = new Set(scene.happenings.filter(h => h.stories).flatMap(h => scene.people.find(p => p.id === h.who)?.villager ?? []))
  if (!tellers.size) return scene
  return { ...scene, stories: stories.filter(s => tellers.has(s.teller) && s.language === scene.language) }
}

/** The first of [scenes] where [teller] tells stories: where a new story of theirs is heard. */
export function storyScene(scenes: ResolvedScene[], teller: string): ResolvedScene | undefined {
  return scenes.find(s => s.happenings.some(h => h.stories && s.people.find(p => p.id === h.who)?.villager === teller))
}

export class StoryStore {
  private warned = new Set<string>()
  private curatedDirs: { dir: string; culture?: string }[]

  /** [curated]: the directories of the curated stories, in order (the village's culture pack's stories/); the first with an id wins. */
  constructor(
    curated: string | { dir: string; culture?: string }[],
    private tutorDir: string,
    private refs: () => StoryRefs,
    private log: (...a: unknown[]) => void = () => {},
  ) {
    this.curatedDirs = typeof curated === 'string' ? [{ dir: curated }] : curated
    mkdirSync(tutorDir, { recursive: true })
  }

  private read(dir: string, source: StorySource, culture?: string): LoadedStory[] {
    if (!existsSync(dir)) return []
    // checked against the cast (the teller); its words are looked up when it is served (resolveStory): a word gone from
    // its pack doesn't silence the story (publish_story and the culture's check want them all)
    const refs = { villagers: this.refs().villagers }
    return readdirSync(dir)
      .filter(f => f.endsWith('.json'))
      .sort()
      .flatMap(f => {
        const path = join(dir, f)
        try {
          const v = validateStory(JSON.parse(readFileSync(path, 'utf8')), refs)
          if (!v.ok) throw new Error(v.errors)
          const published_at = source === 'tutor' ? statSync(path).mtimeMs : undefined
          return [{ ...v.story, source, published_at, ...(culture ? { culture } : {}) }]
        } catch (e) {
          const key = `${path}:${statSync(path).mtimeMs}`
          if (!this.warned.has(key)) this.log(`skipping story ${path}: ${(e as Error).message}`)
          this.warned.add(key)
          return []
        }
      })
  }

  /** The curated stories, one per id (the first directory with it wins), in their order. */
  private curated(): LoadedStory[] {
    const seen = new Set<string>()
    return this.curatedDirs
      .flatMap(({ dir, culture }) => this.read(dir, 'curated', culture).filter(s => !seen.has(s.id) && seen.add(s.id)))
      .sort((a, b) => (a.order ?? 999) - (b.order ?? 999) || a.id.localeCompare(b.id))
  }

  /** Every story in the rotation's order: the curated ones by their order, then the tutor's, oldest first. A tutor story replaces a curated one with its id. */
  all(): LoadedStory[] {
    const tutor = this.read(this.tutorDir, 'tutor').sort((a, b) => (a.published_at ?? 0) - (b.published_at ?? 0))
    const ids = new Set(tutor.map(s => s.id))
    return [...this.curated().filter(s => !ids.has(s.id)), ...tutor]
  }

  get(id: string): LoadedStory | undefined {
    return this.all().find(s => s.id === id)
  }

  /** Every story as the app gets it, words resolved. */
  resolved(): ResolvedStory[] {
    const packs = this.refs().packs ?? []
    return this.all().map(s => resolveStory(s, packs))
  }

  isCurated(id: string): boolean {
    return this.curated().some(s => s.id === id)
  }

  /** Writes a tutor story (same id = replace; a curated id is replaced too, until remove()). */
  publish(s: Story): void {
    const path = join(this.tutorDir, `${s.id}.json`)
    writeFileSync(`${path}.tmp`, JSON.stringify(s, null, 2) + '\n')
    renameSync(`${path}.tmp`, path)
  }

  /** Removes a tutor story (a curated one with that id is told again). False when there is none. */
  remove(id: string): boolean {
    if (!/^[a-z0-9][a-z0-9-]{1,62}$/.test(id)) return false
    const path = join(this.tutorDir, `${id}.json`)
    if (!existsSync(path)) return false
    unlinkSync(path)
    return true
  }
}

// --- never out of stories ------------------------------------------------------------------------------------------

/** What the app's village state says the learner has heard of each story (GameState.stories: told, chapter, levels). */
export type Heard = Record<string, { told?: number; chapter?: number; level?: string; on?: string; levels?: Record<string, number> } | undefined>

/**
 * Fewer than this many stories the learner hasn't heard left at their level, and the tutor is asked for another (a new
 * legend or a continuation), once a day at most: a buffer, so a day the tutor misses never shows. With none left, the
 * teller retells the old ones a level up, never a silent evening.
 */
export const STORIES_LOW = 3

/** Of [stories], those the learner hasn't heard to their end yet ([heard]) that are told at [level] (they have that telling). */
export const storiesLeft = <S extends Story>(stories: S[], heard: Heard, level: string): S[] =>
  stories.filter(s => (heard[s.id]?.told ?? 0) === 0 && (levelsOf(s) as string[]).includes(level))

/** The day the tutor was last asked for a story ([localDate]), kept in <data>/app so a restart doesn't ask twice a day. */
export class StoryAsks {
  constructor(private path: string) {}

  last(): { date?: string; left?: string[]; level?: string } {
    try {
      return JSON.parse(readFileSync(this.path, 'utf8'))
    } catch {
      return {}
    }
  }

  mark(date: string, left: string[], level: string) {
    writeFileSync(`${this.path}.tmp`, JSON.stringify({ date, left, level }) + '\n')
    renameSync(`${this.path}.tmp`, this.path)
  }
}

/** Where the curated stories of a village are: its culture pack's stories/ (companion/cultures/<id>/stories). */
export function storiesDirs(culturesDir: string, cultureId: string): { dir: string; culture: string }[] {
  return [{ dir: join(culturesDir, cultureId, 'stories'), culture: cultureId }]
}
