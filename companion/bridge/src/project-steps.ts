// lani.project-steps/v0: a village project's steps as short scenes that do what the step says (companion/GAME.md
// "Village projects", companion/SCENES.md "Project steps"). Choosing the spruce for the maypole is a dialog with Luka in
// the forest, the learner tapping the tallest spruce; raising it is one on the village square. One file per project in
// a culture pack's project-steps/ (<project>.json), in the pack's language: per step the scene it is played in (when the
// village has it open; else over the village, as a keeper's talk is), the words of the project's word pack its turns
// test, and its dialog at each level (a scene dialog's lines, said by the project's leader and helpers).
//
// The app bundles the files with the pack (app/build.gradle.kts, CulturePacks) and plays them (game/Projects.kt); a step
// without a dialog (a null step, a file without it, a pack with none) keeps the practice it always had. The bridge
// checks them with the pack (validateCulture), and voice-build voices their lines. A bridge from before them never reads
// the directory: the content can go out before the code that plays it.
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { learnerErrors, learnerErrorsIn, render, addressee as addresseeFor, sameFor, withoutLearner } from './addressee'
import { PACK_LANGUAGES } from './arrivals'
import { lcsPairs, wordsIn } from './dialog-words'
import { schemaField } from './schema'
import { dialogLine, SCENE_ART, SCENE_LANGUAGES, SCENE_STAGE, scenesDirs, sceneSpec, type Scene } from './scenes'
import { LEVELS } from './stories'

export const PROJECT_STEPS_SCHEMA = 'lani.project-steps/v0'
/** The levels every curated step is written for. */
export const STEP_LEVELS = ['A1', 'A2'] as const
/** A step's dialog: 4 to 7 lines, someone's and the learner's turns (1-3 of them). */
export const STEP_LINES = { min: 4, max: 7 }
/** A step's turns test 1 to 3 of its project's words. */
export const STEP_WORDS = 3

const id = z.string().regex(/^[a-z0-9][a-z0-9-]{0,62}$/, 'kebab-case, 1-63 chars')
const str = z.string().trim().min(1)

/** One level's telling of a step: its lines, as a scene dialog's. */
const telling = z.strictObject({ lines: z.array(dialogLine).min(STEP_LINES.min).max(STEP_LINES.max) })

/**
 * One step: the [scene] it plays in (a scene id of the village's language or of the pack; none: over the village), the
 * [words] of the project's pack its turns test (word ids), its dialog per level, and (optional) the sky or the effects it
 * leaves for the day ([sky_stays], [fx_stays], as a scene dialog's).
 */
const step = z.strictObject({
  scene: z.string().regex(/^[a-z0-9][a-z0-9-]{1,62}$/, 'a scene id').optional(),
  words: z.array(id).max(STEP_WORDS).default([]),
  levels: z.partialRecord(z.enum(LEVELS), telling).refine(l => Object.keys(l).length > 0, 'at least one level: {"A1": {"lines": […]}}'),
  sky_stays: z.boolean().optional(),
  fx_stays: z.boolean().optional(),
})
export type ProjectStep = z.infer<typeof step>

/**
 * A project's steps ([project]: its id in projects.json), in order, as many as it has (null: a step played as the
 * practice it always was); [pack]: the word pack of its words (the pack's packs/ or the language's), [review]: who wrote
 * it and who checked it.
 */
export const projectStepsSpec = z.strictObject({
  schema: schemaField(PROJECT_STEPS_SCHEMA),
  project: z.string().regex(/^[a-z][a-z0-9_]{0,39}$/, 'a project id, as in projects.json'),
  language: z.enum(SCENE_LANGUAGES),
  review: str.optional(),
  pack: id.optional(),
  steps: z.array(step.nullable()).min(1).max(20),
})
export type ProjectSteps = z.infer<typeof projectStepsSpec>

/** A project as its steps' check needs it (projects.json): who leads it, who helps, how many steps it has. */
export type StepProject = { id: string; leader: string; helpers: string[]; steps: number }
/** A scene a step may play in: its art, and its things (a tap turn's thing is tested as its word). */
export type StepScene = Pick<Scene, 'id' | 'art' | 'language'> & { objects: { slot: string; word: string; pack?: string }[] }
/** A word pack as the check needs it. */
export type StepPack = { id: string; language?: string; words: { id: string; [k: string]: unknown }[] }

/**
 * The things a step's tap turn may not stand for: what a painter draws only once it is built or at a room's level (a
 * scene's fixtures: the well, the church tower, the mill, the kozolec …), and the animals, which come and go. A step
 * plays in the village as it is: its picture may not show them.
 */
export const NOT_ALWAYS: Record<string, string[]> = {
  campfire: ['tent', 'moon', 'stars'],
  kitchen: ['stove', 'table', 'chair', 'bread', 'plate', 'bowl', 'glass', 'cup', 'spoon', 'fork', 'knife', 'salt', 'clock', 'cupboard', 'corner', 'hearth', 'bed', 'shelf', 'curtains'],
  forest: ['owl', 'deer', 'squirrel', 'bird', 'sheep', 'hare', 'butterfly', 'fox', 'stag', 'hedgehog', 'boar', 'badger', 'dormouse', 'bat'],
  square: ['well', 'tower', 'dog', 'cat'],
  field: ['kozolec', 'hay', 'deer', 'hare', 'butterfly', 'fox', 'boar', 'bat', 'horse'],
  smithy: ['bellows', 'chain', 'axe', 'wheel'],
  school: ['map', 'globe', 'clock'],
  stream: ['mill', 'trout', 'dragonfly', 'fox', 'hedgehog', 'butterfly', 'bat', 'sheep'],
  pond: ['frog', 'ducks', 'fish', 'heron', 'deer', 'fox', 'butterfly', 'bat', 'dragonfly'],
  apiary: ['bee'],
  market: ['hen', 'pigeon'],
  watchtower: ['crow'],
  alps: ['cow', 'chamois', 'ibex'],
  hills: [],
  church: [],
  tent: [],
  livingroom: ['dog', 'chair', 'curtains', 'rug', 'lamp', 'clock', 'corner', 'photo', 'glasses', 'book'],
  workshop: ['saw', 'tools', 'ladder', 'rake', 'wheelbarrow', 'horse', 'lantern'],
  cellar: ['shelf', 'bottle', 'grapes', 'wine', 'ham', 'cheese'],
  attic: ['wardrobe', 'rug', 'lamp', 'ball', 'mirror', 'toy'],
}

/** What the check of a pack's project steps reads: its language, its projects, the scenes and packs it may name. */
export type StepRefs = {
  /** The pack's language: every text has it, and every language the pack serves. */
  lang: string
  projects: StepProject[]
  /** The scenes of the village's language and the pack's own (by id); without them, a scene isn't checked. */
  scenes?: Map<string, StepScene>
  /** The word packs the village is served; without them, the words aren't checked against their pack. */
  packs?: StepPack[]
  /** The grammar book's pages of the pack's language; without them, a turn's page isn't checked. */
  pages?: Set<string>
  /** Curated: every step it has written at A1 and A2, each declaring its words. */
  curated?: boolean
  /** The dictionary's lemmas of a word ([lexiconLemmas]); without it a word's forms are guessed by their stem. */
  lemmas?: Lemmas
}

const MAN = addresseeFor({ name: 'Learner' })

/**
 * Whether [word] (of a sentence) looks like a form of [lemma] without a dictionary: the same, or the same stem with
 * another ending (smreko, smreke for smreka; visoko for visok). The app checks with the dictionary's forms besides.
 */
export function looksLikeFormOf(word: string, lemma: string): boolean {
  const w = word.toLowerCase()
  const l = lemma.toLowerCase()
  if (w === l) return true
  const stem = (s: string) => (s.length > 4 ? s.slice(0, s.length - 2) : s.length > 3 ? s.slice(0, s.length - 1) : s)
  const a = stem(w)
  const b = stem(l)
  return a.length >= 3 && b.length >= 3 && (w.startsWith(b) || l.startsWith(a)) && Math.abs(w.length - l.length) <= 3
}

/** The lemmas a word (lower case) may be a form of, by the dictionary; none known: the word alone. */
export type Lemmas = (word: string) => string[]

const lexicons = new Map<string, Lemmas>()

/**
 * The dictionary's lemmas of a word in [lang] (companion/lexicon/<lang>.json, Wiktionary's forms, and <lang>.extra.json,
 * the supplement), read once; the word alone where there is no dictionary. What the phone counts a tested word by: the
 * card's forms (GET /forms) come from the same files.
 */
export function lexiconLemmas(lexiconDir: string, lang: string): Lemmas {
  const key = `${lexiconDir}|${lang}`
  const had = lexicons.get(key)
  if (had) return had
  const byForm = new Map<string, Set<string>>()
  const add = (form: string, lemma: string) => {
    const f = form.toLowerCase()
    const set = byForm.get(f) ?? new Set<string>()
    set.add(lemma.toLowerCase())
    byForm.set(f, set)
  }
  // Wiktionary's lemmas by part of speech: the supplement's forms of a lemma it has with the same part of speech don't
  // count (lexicon.ts lemmasOf leaves that entry out, so GET /forms never lists them)
  const wiktionary = new Set<string>()
  try {
    const d = JSON.parse(readFileSync(join(lexiconDir, `${lang}.json`), 'utf8')) as { lemmas: { lemma: string; pos?: string }[]; forms: Record<string, [number, ...unknown[]][]> }
    for (const l of d.lemmas) wiktionary.add(`${l.lemma.toLowerCase()}|${l.pos ?? ''}`)
    for (const [form, rs] of Object.entries(d.forms)) for (const r of rs) { const l = d.lemmas[r[0]]?.lemma; if (l) add(form, l) }
  } catch {
    // no dictionary of the language
  }
  try {
    const e = JSON.parse(readFileSync(join(lexiconDir, `${lang}.extra.json`), 'utf8')) as { entries: { lemma: string; pos?: string; forms?: Record<string, string> }[] }
    for (const x of e.entries) {
      add(x.lemma, x.lemma)
      if (wiktionary.has(`${x.lemma.toLowerCase()}|${x.pos ?? ''}`)) continue
      for (const form of Object.keys(x.forms ?? {})) add(form, x.lemma)
    }
  } catch {
    // no supplement
  }
  const lemmas: Lemmas = w => [w.toLowerCase(), ...(byForm.get(w.toLowerCase()) ?? [])]
  lexicons.set(key, lemmas)
  return lemmas
}

/**
 * The words of the lines' turns that a wrong choice tests (dialog-words.ts: one or two words differ, the rest shared), as
 * said to a man; and each tap turn's right thing (its slot).
 */
export function testedIn(lines: z.infer<typeof dialogLine>[], lang: string): { words: string[]; taps: string[] } {
  const words: string[] = []
  const taps: string[] = []
  for (const l of lines) {
    if (!l.choices.length) continue
    if (l.choices.every(c => c.tap !== undefined)) {
      const right = l.choices.find(c => c.ok)?.tap
      if (right) taps.push(right)
      continue
    }
    const say = (c: (typeof l.choices)[number]) => wordsIn(render(((c as Record<string, unknown>)[lang] as string | undefined) ?? '', MAN))
    for (const r of l.choices.filter(c => c.ok)) {
      const rw = say(r)
      for (const w of l.choices.filter(c => !c.ok)) {
        const matched = new Set(lcsPairs(rw, say(w)).map(([i]) => i))
        const off = rw.map((_, i) => i).filter(i => !matched.has(i))
        if (off.length >= 1 && off.length <= 2 && (rw.length === 1 || rw.length - off.length >= off.length)) words.push(...off.map(i => rw[i]))
      }
    }
  }
  return { words, taps }
}

/**
 * A helper's line that would read wrong in another's mouth: a first person that agrees with the speaker ("Prinesel
 * sem", "sem utrujena"). A helper may be away, and another says their lines then (the app: Projects.cast): theirs say
 * nothing of the speaker's gender. Slovene only (the curated steps' language).
 */
export function speakerGendered(text: string): boolean {
  const t = text.toLowerCase()
  return /\bsem\s+\p{L}*(l|la|lo|en|na)\b/u.test(t) || /\b\p{L}{2,}(l|la)\s+sem\b/u.test(t) || /\b(sam|sama|bil|bila)\b/u.test(t)
}

/**
 * The problems of one project's steps [f] (in [r.lang], the pack's): the project of the pack, as many steps as it has,
 * each level's lines as a dialog of the pack (every text in the languages it serves, a why and a reaction to every wrong
 * choice, 1-3 turns), said by the project's leader and helpers (the leader at least once), a helper's lines that anyone
 * could say; its scene one of the village (its art's effects, a tap turn's places there, never a thing that comes and
 * goes), none: no sky, fx or tap; its words in the project's pack, each tested by a turn at every level; the grammar
 * pages its turns name.
 */
export function projectStepsErrors(f: ProjectSteps, r: StepRefs): string[] {
  const errs: string[] = [...learnerErrorsIn(f)]
  if (f.language !== r.lang) errs.push(`language: "${f.language}", not the pack's "${r.lang}"`)
  const p = r.projects.find(x => x.id === f.project)
  if (!p) return [...errs, `project: no project "${f.project}" in projects.json (${r.projects.map(x => x.id).join(', ')})`]
  if (f.steps.length > p.steps) errs.push(`steps: ${f.steps.length}, but the project has ${p.steps}`)
  const pack = f.pack ? r.packs?.find(x => x.id === f.pack) : undefined
  if (f.pack && r.packs && !pack) errs.push(`pack: no word pack "${f.pack}" in the packs the village is served`)
  if (!f.pack && f.steps.some(s => s?.words.length)) errs.push('pack: a step declares words, but the file names no pack')
  const speakers = new Set([p.leader, ...p.helpers])
  const helpers = new Set(p.helpers)
  f.steps.forEach((s, i) => {
    const at = `steps[${i}]`
    if (!s) return // played as the practice it always was
    const scene = s.scene ? r.scenes?.get(s.scene) : undefined
    if (s.scene && r.scenes && !scene) errs.push(`${at}.scene: no scene "${s.scene}" in the village's language or the pack`)
    if (r.curated) {
      const missing = STEP_LEVELS.filter(l => !s.levels[l])
      if (missing.length) errs.push(`${at}.levels: no ${missing.join(', ')} (a curated step is written for ${STEP_LEVELS.join(' and ')} at least)`)
      if (!s.words.length) errs.push(`${at}.words: none (a step's turns test 1-${STEP_WORDS} of the project's words)`)
    }
    const words = s.words.map(w => ({ id: w, word: pack?.words.find(x => x.id === w) }))
    for (const w of words) if (pack && !w.word) errs.push(`${at}.words: no word "${w.id}" in pack "${pack.id}"`)
    for (const [lv, t] of Object.entries(s.levels)) {
      const here = `${at}.levels.${lv}`
      errs.push(...linesErrors(t.lines, r.lang, speakers, here))
      if (!t.lines.some(l => l.who === p.leader)) errs.push(`${here}: the leader (${p.leader}) says nothing`)
      t.lines.forEach((l, li) => {
        const ln = `${here}.lines[${li}]`
        if (l.act || l.choices.some(c => c.reply?.act)) errs.push(`${ln}: a step's people are who is here today: no act cue`)
        if (l.who && helpers.has(l.who) && r.lang === 'sl') {
          const said = render(String((l as Record<string, unknown>).sl ?? ''), MAN)
          if (speakerGendered(said)) errs.push(`${ln}: a helper's line says the speaker's gender ("${said}"): another may say it when they're away; say it in the plural or the present`)
        }
        // cues and taps need the scene's picture
        const cued = l.sky || l.fx || l.choices.some(c => c.reply?.sky || c.reply?.fx)
        if (cued && !s.scene) errs.push(`${ln}: a sky or fx cue needs the step's scene`)
        const taps = l.choices.map(c => c.tap)
        if (taps.some(x => x !== undefined)) {
          if (!s.scene) errs.push(`${ln}: a tap turn needs the step's scene (its picture to tap in)`)
          if (taps.some(x => x === undefined)) errs.push(`${ln}: in a tap turn every choice stands for a place in the picture (tap), or none does`)
          if (new Set(taps).size !== taps.length) errs.push(`${ln}: a tap turn's choices each stand for their own place`)
        } else if (l.choices.length > 3) errs.push(`${ln}: a turn needs 2-3 choices (a tap turn, 2-4)`)
        if (!scene) return
        const art = scene.art
        const effects = SCENE_ART.effects[art] ?? []
        const fx = [l.fx, ...l.choices.map(c => c.reply?.fx)].flatMap(x => Object.keys(x ?? {}))
        for (const e of fx) if (!effects.includes(e)) errs.push(`${ln}: the art "${art}" has no effect "${e}" (effects: ${effects.join(', ') || 'none'})`)
        l.choices.forEach((c, ci) => {
          if (c.tap === undefined) return
          const slots = SCENE_ART.objects[art] ?? []
          const ok = slots.includes(c.tap) || !!SCENE_STAGE[art]?.[c.tap] || c.tap === p.leader
          if (!ok) errs.push(`${ln}.choices[${ci}].tap: "${c.tap}" is no slot of the art "${art}", no spot of its stage and not the leader`)
          else if (NOT_ALWAYS[art]?.includes(c.tap)) errs.push(`${ln}.choices[${ci}].tap: "${c.tap}" isn't always in the picture (built later, or an animal that comes and goes): tap something that is`)
          if (!c.ok && c.reply?.sky) errs.push(`${ln}.choices[${ci}].reply: a reaction passes: no sky cue`)
        })
      })
      // its words: each tested by a turn (a wrong choice differs from the right one in it, or its thing is tapped)
      const tested = testedIn(t.lines, r.lang)
      for (const w of words) {
        const lemma = w.word ? String(w.word[r.lang] ?? '') : ''
        if (!lemma) continue
        const parts = wordsIn(lemma)
        const formOf = (x: string) => (r.lemmas ? r.lemmas(x).includes(parts[0].toLowerCase()) : looksLikeFormOf(x, parts[0]))
        const byTurn = parts.length === 1 && tested.words.some(formOf)
        const byTap = !!scene && tested.taps.some(slot => {
          const o = scene.objects.find(x => x.slot === slot)
          const said = o ? String((o as Record<string, unknown>)[r.lang] ?? o.word) : ''
          return said.toLowerCase() === lemma.toLowerCase() || o?.word === w.id
        })
        if (!byTurn && !byTap) {
          const near = parts.length === 1 && r.lemmas ? tested.words.find(x => looksLikeFormOf(x, parts[0])) : undefined
          errs.push(`${here}: the step's word "${lemma}" (${w.id}) is in no turn's test: a wrong choice differs from the right one in it ("Primi vrv." / "Primi verigo."), or its thing is tapped` +
            (near ? `; "${near}" is tested, but the dictionary doesn't know it as a form of "${lemma}" (companion/lexicon/${r.lang}.extra.json, or another form)` : ''))
        }
      }
      if (r.pages) for (const [li, l] of t.lines.entries()) {
        const named = [l.grammar, ...l.choices.map(c => c.grammar)].filter((g): g is string => !!g)
        for (const g of named) if (!r.pages.has(g)) errs.push(`${here}.lines[${li}]: no grammar page "${g}" in companion/grammar/${r.lang}`)
      }
    }
    const cues = Object.values(s.levels).some(t => t.lines.some(l => l.sky || l.choices.some(c => c.ok && c.reply?.sky)))
    if (s.sky_stays !== undefined && !cues) errs.push(`${at}: sky_stays without a sky cue on any line or reply`)
    const fx = Object.values(s.levels).some(t => t.lines.some(l => l.fx || l.choices.some(c => c.ok && c.reply?.fx)))
    if (s.fx_stays !== undefined && !fx) errs.push(`${at}: fx_stays without an fx cue on any line or reply to a right choice`)
  })
  return errs
}

type Line = z.infer<typeof dialogLine>
type Texts = Partial<Record<string, unknown>>

/**
 * The problems of a step's [lines] as a dialog of the pack (in [lang]): every text in every language the pack serves
 * (PACK_LANGUAGES), a why in each but the pack's (the learner reads it in their base), a reaction to every wrong choice
 * (its reply: what they make of it, never the right form), a right one and a wrong one at every turn, 1-3 turns, the first
 * line someone's, every line said by one of [speakers]; no placeholder but the learner's ({learner}, {m:…|f:…}).
 */
function linesErrors(lines: Line[], lang: string, speakers: Set<string>, at: string): string[] {
  const errs: string[] = []
  const langs = [...new Set([lang, ...PACK_LANGUAGES])]
  const placeholders = (s: string, p: string) => {
    for (const e of learnerErrors(s)) errs.push(`${p}: ${e}`)
    if (/[{}]/.test(withoutLearner(s))) errs.push(`${p}: no {…} but the learner's ({learner}, {m:…|f:…})`)
  }
  const inLangs = (t: Texts | undefined, p: string) => {
    if (!t) return
    const missing = langs.filter(x => typeof t[x] !== 'string')
    if (missing.length) errs.push(`${p}: no ${missing.map(x => `"${x}"`).join(', ')} text (every language the pack serves)`)
    for (const l of SCENE_LANGUAGES) if (typeof t[l] === 'string') placeholders(t[l] as string, `${p}.${l}`)
  }
  if (lines[0]?.choices.length) errs.push(`${at}.lines[0]: the first line is someone's, not the learner's choices`)
  let turns = 0
  lines.forEach((l, li) => {
    const here = `${at}.lines[${li}]`
    if (!l.choices.length) {
      if (!l.who) errs.push(`${here}: who says it? (${[...speakers].join(', ')})`)
      else if (!speakers.has(l.who)) errs.push(`${here}: who "${l.who}" is neither the project's leader nor a helper (${[...speakers].join(', ')})`)
      if (l.grammar || l.puzzled) errs.push(`${here}: a grammar page and a puzzled reaction are a learner's turn's, not a spoken line's`)
      inLangs(l, here)
      return
    }
    turns++
    if (l.who || SCENE_LANGUAGES.some(x => (l as Texts)[x])) errs.push(`${here}: a choice turn has no who and no text of its own`)
    if (l.choices.length < 2) errs.push(`${here}: a turn needs 2-3 choices`)
    if (!l.choices.some(c => c.ok)) errs.push(`${here}: no choice is ok`)
    if (l.choices.every(c => c.ok)) errs.push(`${here}: every choice is ok: a turn keeps a wrong one`)
    const same = sameFor(l.choices.map(c => (c as Texts)[lang] as string | undefined))
    if (same) errs.push(`${here}: two choices read the same to a ${same} learner ({m:…|f:…}): a turn never tests the learner's own gender`)
    inLangs(l.puzzled, `${here}.puzzled`)
    l.choices.forEach((c, ci) => {
      const ch = `${here}.choices[${ci}]`
      inLangs(c, ch)
      if (c.reply) inLangs(c.reply, `${ch}.reply`)
      if (c.ok) return
      if (!c.why) errs.push(`${ch}: a wrong choice needs a why`)
      else if (typeof c.why === 'string') errs.push(`${ch}.why: a curated why is per language ({"en": …, …}: the learner reads it in their base)`)
      else {
        const missing = langs.filter(x => x !== lang && typeof (c.why as Texts)[x] !== 'string')
        if (missing.length) errs.push(`${ch}.why: no ${missing.map(x => `"${x}"`).join(', ')} why`)
        for (const [x, s] of Object.entries(c.why)) if (typeof s === 'string') placeholders(s, `${ch}.why.${x}`)
      }
      if (!c.reply) errs.push(`${ch}: a wrong choice needs a reaction (its reply: what they make of it, never the right form)`)
    })
  })
  if (!turns) errs.push(`${at}: no turn for the learner`)
  if (turns > 3) errs.push(`${at}: ${turns} turns: at most 3 (a step is short)`)
  return errs
}

/** The directory of a pack's project steps. */
export const stepsDir = (culturesDir: string, cultureId: string) => join(culturesDir, cultureId, 'project-steps')

/** Every scene a step of pack [cultureId] may name: the curated scenes of [language] and the pack's own, by id. */
export function stepScenes(culturesDir: string, cultureId: string, language: string): Map<string, StepScene> {
  const out = new Map<string, StepScene>()
  for (const { dir } of scenesDirs(join(culturesDir, '..', 'scenes'), culturesDir, cultureId, language)) {
    if (!existsSync(dir)) continue
    for (const f of readdirSync(dir).filter(x => x.endsWith('.json'))) {
      try {
        const r = sceneSpec.safeParse(JSON.parse(readFileSync(join(dir, f), 'utf8')))
        if (r.success && r.data.language === language) out.set(r.data.id, r.data)
      } catch {
        // a broken scene is the scenes' check's to report
      }
    }
  }
  return out
}

/**
 * Pack [cultureId]'s project steps (project-steps/*.json), read and checked: each named for its project, valid
 * ([projectStepsErrors]; curated: every step at A1 and A2), its scenes' things resolved from their packs ([packs]), and
 * every problem with its file. None when the pack has no such directory.
 */
export function cultureProjectSteps(
  culturesDir: string, cultureId: string, lang: string, projects: StepProject[], o: { packs?: StepPack[]; pages?: Set<string>; scenes?: Map<string, StepScene>; lemmas?: Lemmas } = {},
): { files: ProjectSteps[]; errors: string[] } {
  const dir = stepsDir(culturesDir, cultureId)
  if (!existsSync(dir)) return { files: [], errors: [] }
  const files: ProjectSteps[] = []
  const errors: string[] = []
  // the scenes' things with their words (a tap turn's thing is tested as its word)
  const raw = o.scenes ?? stepScenes(culturesDir, cultureId, lang)
  const scenes = new Map([...raw].map(([k, s]) => [k, {
    ...s,
    objects: s.objects.map(x => {
      const pack = o.packs?.find(p => p.id === (x.pack ?? (s as { pack?: string }).pack))
      const w = pack?.words.find(y => y.id === x.word)
      return { ...x, ...(w ? { [lang]: w[lang] } : {}) }
    }),
  }]))
  for (const name of readdirSync(dir).filter(x => x.endsWith('.json')).sort()) {
    const where = `${cultureId}/project-steps/${name}`
    try {
      const r = projectStepsSpec.safeParse(JSON.parse(readFileSync(join(dir, name), 'utf8')))
      if (!r.success) { errors.push(`${where}:\n${z.prettifyError(r.error)}`); continue }
      if (`${r.data.project}.json` !== name) errors.push(`${where}: project "${r.data.project}", but the file is ${name}`)
      const lemmas = o.lemmas ?? lexiconLemmas(join(culturesDir, '..', 'lexicon'), lang)
      errors.push(...projectStepsErrors(r.data, { lang, projects, scenes, packs: o.packs, pages: o.pages, curated: true, lemmas }).map(e => `${where}: ${e}`))
      files.push(r.data)
    } catch (e) {
      errors.push(`${where}: ${(e as Error).message}`)
    }
  }
  return { files, errors }
}

/** Every telling of [f]'s steps: the step, its level and its lines (voice-build voices them, each in its speaker's voice). */
export function stepTellings(f: ProjectSteps): { step: number; level: string; lines: z.infer<typeof dialogLine>[] }[] {
  return f.steps.flatMap((s, i) => (s ? LEVELS.flatMap(lv => (s.levels[lv] ? [{ step: i, level: lv, lines: s.levels[lv]!.lines }] : [])) : []))
}

/**
 * Pack [cultureId]'s project steps as files, read without their check (the bridge's check at start says what's wrong):
 * what voice-build and the voice corpus voice. None when the pack has no such directory.
 */
export function cultureStepFiles(culturesDir: string, cultureId: string): ProjectSteps[] {
  const dir = stepsDir(culturesDir, cultureId)
  if (!existsSync(dir)) return []
  return readdirSync(dir).filter(x => x.endsWith('.json')).sort().flatMap(name => {
    try {
      const r = projectStepsSpec.safeParse(JSON.parse(readFileSync(join(dir, name), 'utf8')))
      return r.success ? [r.data] : []
    } catch {
      return []
    }
  })
}
