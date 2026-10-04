// Readings (companion/GAME.md, "Readings" and "The reading corner"): short texts in the village's language with a
// translation of every line and a few questions. The culture pack's (companion/cultures/<id>/readings/, what the
// chest's things hold and more) and the tutor's (<data>/app/readings/, publish_reading), listed by level in the app's
// "Branje · Reading" corner. A reading done, a story heard to its end and a reading aloud report as reading practice
// (POST /readings/done: update-db.py, no LLM), so the learner's reading skill counts what they read.
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, statSync, unlinkSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'

const LANG = /^[a-z]{2,3}$/
const str = z.string().trim().min(1)
/** {"sl": "…", "en": "…"} */
const text = z.record(z.string().regex(LANG, 'a language code (sl, en, it …)'), str).refine(t => Object.keys(t).length > 0, 'a text in at least one language')
const id = z.string().regex(/^[a-z0-9][a-z0-9_-]{0,62}$/, 'lowercase letters, digits, - and _')
const obj = <T extends z.ZodRawShape>(shape: T) => z.strictObject(shape)

/** Mirrored from the app's game/Readings.kt (KINDS, LEVELS, QUESTION_TYPES). */
export const READING_KINDS = ['recipe', 'proverbs', 'letter', 'page', 'story', 'article'] as const
export const READING_LEVELS = ['A1', 'A2', 'B1', 'B2'] as const
/**
 * What a question asks: the text's main idea, a detail, a word in context (its [word] as it stands in the text), whether
 * a statement is true (true_false: [answer], no options), or what follows from the text (inference). None: a plain
 * question, as the first readings have them.
 */
export const QUESTION_TYPES = ['main_idea', 'detail', 'word', 'true_false', 'inference'] as const
/** At most this many questions close a reading (the first readings had 1 to 3). */
export const MAX_QUESTIONS = 5
/** At most this many new words a reading offers for the reviews. */
export const MAX_WORDS = 12

const question = obj({
  type: z.enum(QUESTION_TYPES).optional(),
  ask: text,
  word: str.optional(),
  options: z.array(str).min(3).max(4).refine(o => new Set(o).size === o.length, 'an option twice').optional(),
  answer: z.boolean().optional(),
  explain: text,
}).superRefine((q, ctx) => {
  const say = (message: string, path: string[] = []) => ctx.addIssue({ code: 'custom', message, path })
  if (q.type === 'true_false') {
    if (q.answer === undefined) say('a true/false question says whether its statement (ask) holds: "answer": true or false', ['answer'])
    if (q.options) say('a true/false question has no options (the app shows true and false)', ['options'])
  } else {
    if (!q.options) say('3 or 4 options in the pack\'s language, the answer first', ['options'])
    if (q.answer !== undefined) say('only a true/false question has an answer (the others\' answer is their first option)', ['answer'])
  }
  if (q.type === 'word' && !q.word) say('a word question names its word, as it stands in the text', ['word'])
  if (q.word && q.type !== 'word') say('only a word question names a word', ['word'])
})

/**
 * A reading (companion/GAME.md, "Readings"): a recipe, a book's page, proverbs, a letter, a story, an article, in the
 * pack's language with a translation of every line. [by]: who reads it aloud (a villager of the cast; a tool's reading:
 * its giver). A recipe has its [servings], [ingredients] (an [amount] as written, a [unit], the [item] as it reads after
 * them: "moke", "di farina", "flour"; a [part] starts a group, "Testo · The dough") and numbered [steps]; the others
 * have [lines] (a proverb's with what it [means]). The closing [questions] (1 to 5): 3 or 4 options each, the answer
 * first, or a true/false statement. [words]: new words it offers for the reviews (the [word] as a dictionary has it, its
 * [form] in the text, what it [means] in the other languages). [topics]: what it is about, for the tutor.
 */
export const readingSpec = obj({
  id,
  kind: z.enum(READING_KINDS),
  level: z.enum(READING_LEVELS),
  title: text,
  by: id.optional(),
  intro: text.optional(),
  servings: text.optional(),
  ingredients: z.array(obj({ part: text.optional(), amount: str.optional(), unit: text.optional(), item: text })).default([]),
  steps: z.array(text).default([]),
  lines: z.array(obj({ text, means: text.optional() })).default([]),
  questions: z.array(question).min(1).max(MAX_QUESTIONS),
  words: z.array(obj({ word: str, form: str.optional(), pos: str.optional(), means: text })).max(MAX_WORDS).default([]),
  topics: z.array(z.string().trim().min(1).max(40)).max(8).default([]),
  /** Written by a machine (the tutor's always are); [review]: who wrote which languages and who checked them. */
  machine_written: z.boolean().optional(),
  review: str.optional(),
}).superRefine((r, ctx) => {
  const say = (message: string, path: (string | number)[] = []) => ctx.addIssue({ code: 'custom', message, path })
  if (r.kind === 'recipe') {
    if (!r.servings) say('a recipe says how much it makes', ['servings'])
    if (!r.ingredients.length) say('a recipe has its ingredients', ['ingredients'])
    if (!r.steps.length) say('a recipe has its steps', ['steps'])
    if (r.lines.length) say('a recipe has ingredients and steps, not lines', ['lines'])
  } else {
    if (!r.lines.length) say(`a ${r.kind} has its lines`, ['lines'])
    if (r.ingredients.length || r.steps.length || r.servings) say('only a recipe has servings, ingredients and steps')
  }
  r.lines.forEach((l, i) => {
    if (r.kind === 'proverbs' && !l.means) say('a proverb says what it means', ['lines', i, 'means'])
    if (r.kind !== 'proverbs' && l.means) say('only a proverb has a meaning', ['lines', i, 'means'])
  })
})
export type Reading = z.infer<typeof readingSpec>

/** Every text (an object of language codes → strings) in [v], with its path. */
function texts(v: unknown, path: string, out: [string, Record<string, string>][] = []) {
  if (Array.isArray(v)) v.forEach((x, i) => texts(x, `${path}[${i}]`, out))
  else if (v && typeof v === 'object') {
    const entries = Object.entries(v)
    if (entries.length && entries.every(([k, x]) => LANG.test(k) && typeof x === 'string')) out.push([path, v as Record<string, string>])
    else for (const [k, x] of entries) texts(x, `${path}.${k}`, out)
  }
  return out
}

/** A new word's meaning is in the other languages: the word itself is the pack's. */
const MEANING = /\.words\[\d+\]\.means$/

/** The lines of [r] in [lang], as they are read (intro, a recipe's servings, ingredients and steps, or the lines). */
export function readingTexts(r: Reading, lang: string): string[] {
  const of = (t: Record<string, string> | undefined) => (t && t[lang]) || ''
  return [
    of(r.intro),
    ...r.ingredients.map(g => [g.amount, of(g.unit), of(g.item)].filter(Boolean).join(' ')),
    ...r.steps.map(of),
    ...r.lines.map(l => of(l.text)),
  ].filter(Boolean)
}

/** How many words [r] has to read in [lang] (its intro, steps and lines; not the title). */
export function readingLength(r: Reading, lang: string): number {
  return readingTexts(r, lang).join(' ').split(/\s+/).filter(w => /[\p{L}\p{N}]/u.test(w)).length
}

/** [word] stands in [texts] as a word of its own (any case). */
function standsIn(word: string, all: string): boolean {
  const w = word.trim().toLowerCase().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return new RegExp(`(^|[^\\p{L}])${w}($|[^\\p{L}])`, 'u').test(all.toLowerCase())
}

/**
 * What the shape alone can't say about reading [r]: every text in the pack's language [lang] and in every language its
 * title has (a translation of every line; a new word's meaning in those but the pack's), a word question's word and a
 * new word's form in the text, who reads it among the [cast] (named [castWhere]). Each problem with [where] (the file).
 */
export function checkReading(r: Reading, lang: string, where: string, cast?: { id: string }[], castWhere = 'the cast'): string[] {
  const errors: string[] = []
  const langs = Object.keys(r.title)
  if (!langs.some(l => l !== lang)) errors.push(`${where}: title: no translation (a language besides "${lang}")`)
  for (const [path, t] of texts(r, 'reading')) {
    if (MEANING.test(path)) {
      const missing = langs.filter(l => l !== lang && !(l in t))
      if (missing.length) errors.push(`${where}: ${path}: no "${missing.join('", "')}" meaning (the title has it)`)
      continue
    }
    if (!(lang in t)) errors.push(`${where}: ${path}: no "${lang}" text (the pack's language)`)
    const missing = langs.filter(l => !(l in t))
    if (missing.length) errors.push(`${where}: ${path}: no "${missing.join('", "')}" text (the title has it)`)
  }
  const all = readingTexts(r, lang).join('\n')
  r.questions.forEach((q, i) => {
    if (q.word && !standsIn(q.word, all)) errors.push(`${where}: reading.questions[${i}].word: "${q.word}" isn't in the text`)
  })
  r.words.forEach((w, i) => {
    const form = w.form ?? w.word
    if (!standsIn(form, all)) errors.push(`${where}: reading.words[${i}]: "${form}" isn't in the text (give its form as it stands there)`)
  })
  const ids = cast && new Set(cast.map(c => c.id))
  if (r.by && ids && !ids.has(r.by)) errors.push(`${where}: by: no villager "${r.by}" in ${castWhere}`)
  return errors
}

/**
 * The readings in [dir] (<id>.json each), checked: the shape, named for its id, [checkReading]. [where] names the
 * directory in the problems ("primorska/readings").
 */
export function readReadings(dir: string, lang: string, where: string, cast?: { id: string }[], castWhere?: string): { readings: Map<string, Reading>; errors: string[] } {
  const readings = new Map<string, Reading>()
  const errors: string[] = []
  if (!existsSync(dir)) return { readings, errors }
  for (const f of readdirSync(dir).filter(f => f.endsWith('.json')).sort()) {
    const at = `${where}/${f}`
    try {
      const r = readingSpec.safeParse(JSON.parse(readFileSync(join(dir, f), 'utf8')))
      if (!r.success) {
        errors.push(`${at}:\n${z.prettifyError(r.error)}`)
        continue
      }
      const reading = r.data
      if (`${reading.id}.json` !== f) errors.push(`${at}: id "${reading.id}", but the file is ${f}`)
      errors.push(...checkReading(reading, lang, at, cast, castWhere))
      readings.set(reading.id, reading)
    } catch (e) {
      errors.push(`${at}: ${(e as Error).message}`)
    }
  }
  return { readings, errors }
}

export type ReadingSource = 'curated' | 'tutor'
/** A reading as the app gets it: [source], the culture pack it's of (curated), when the tutor published it. */
export type ServedReading = Reading & { source: ReadingSource; culture?: string; published_at?: number }

/**
 * The readings of the village's language: its culture pack's (readings/) and the tutor's (<data>/app/readings/). A
 * tutor's reading never takes a curated one's id (the chest's things hold those).
 */
export class ReadingStore {
  private warned = new Set<string>()

  constructor(
    private readonly curatedDir: string,
    private readonly culture: string,
    private readonly tutorDir: string,
    private readonly language: string,
    private readonly cast: () => { id: string }[] = () => [],
    private readonly log: (...a: unknown[]) => void = () => {},
  ) {
    mkdirSync(tutorDir, { recursive: true })
  }

  private read(dir: string, source: ReadingSource): ServedReading[] {
    const r = readReadings(dir, this.language, source === 'curated' ? `${this.culture}/readings` : 'app/readings', this.cast())
    for (const e of r.errors) {
      if (this.warned.has(e)) continue
      this.warned.add(e)
      this.log(`reading: ${e}`)
    }
    // a reading with problems beyond its shape is still served (the culture's check and publish_reading name them)
    return [...r.readings.values()].map(x => ({
      ...x,
      source,
      ...(source === 'curated' ? { culture: this.culture } : { published_at: statSync(join(dir, `${x.id}.json`)).mtimeMs }),
    }))
  }

  curated(): ServedReading[] {
    return this.read(this.curatedDir, 'curated')
  }

  /** The culture pack's readings, then the tutor's, oldest first. */
  all(): ServedReading[] {
    const curated = this.curated()
    const ids = new Set(curated.map(r => r.id))
    const tutor = this.read(this.tutorDir, 'tutor').filter(r => !ids.has(r.id)).sort((a, b) => (a.published_at ?? 0) - (b.published_at ?? 0))
    return [...curated, ...tutor]
  }

  get(id: string): ServedReading | undefined {
    return this.all().find(r => r.id === id)
  }

  isCurated(id: string): boolean {
    return this.curated().some(r => r.id === id)
  }

  /** Writes a tutor reading (the same id replaces it), flagged machine-written. */
  publish(r: Reading): void {
    const path = join(this.tutorDir, `${r.id}.json`)
    writeFileSync(`${path}.tmp`, JSON.stringify({ ...r, machine_written: true }, null, 2) + '\n')
    renameSync(`${path}.tmp`, path)
  }

  /** Removes a tutor reading; false when there is none. */
  remove(readingId: string): boolean {
    if (!/^[a-z0-9][a-z0-9_-]{0,62}$/.test(readingId)) return false
    const path = join(this.tutorDir, `${readingId}.json`)
    if (!existsSync(path)) return false
    unlinkSync(path)
    return true
  }
}

// --- reading practice, reported by the app ----------------------------------------------------------------------

/** What the app read: a reading's questions, a story heard to its end, or a reading aloud. */
export const READ_KINDS = ['reading', 'story', 'aloud'] as const

/**
 * POST /readings/done: a reading's questions answered ([questions]: whether each was right the first time, by type), a
 * story heard to its end (its turns), or a reading aloud ([aloud]: its words read right, misread and skipped, the pace).
 * [exercises] and [correct]: the questions, the turns, the sentences read aloud and those read well. [looks]: how often the
 * text, read first and hidden while its questions were asked, was shown again while one was open (GAME.md, "Read first,
 * then answer"); an app from before sends none.
 */
export const readingDone = z.object({
  kind: z.enum(READ_KINDS),
  id: z.string().trim().min(1).max(80),
  title: z.string().trim().max(200).optional(),
  level: z.string().regex(/^[ABC][12]$/).optional(),
  exercises: z.number().int().min(1).max(200),
  correct: z.number().int().min(0).max(200),
  duration_minutes: z.number().int().min(1).max(240),
  questions: z.array(z.object({ type: z.string().max(20).optional(), right: z.boolean() })).max(10).optional(),
  looks: z.number().int().min(0).max(99).optional(),
  aloud: z.object({
    words: z.number().int().min(0).max(5000),
    right: z.number().int().min(0).max(5000),
    misread: z.number().int().min(0).max(5000),
    skipped: z.number().int().min(0).max(5000),
    wpm: z.number().int().min(0).max(400).optional(),
    recognizer: z.enum(['node', 'phone']).optional(),
    /** A few words read wrong: the text's word and what was heard (none when skipped). */
    misses: z.array(z.object({ word: z.string().max(60), heard: z.string().max(60).optional() })).max(20).optional(),
  }).optional(),
  language: z.string().regex(LANG, 'a language code, e.g. "it"').optional(),
}).refine(d => d.correct <= d.exercises, 'correct: at most the exercises')
export type ReadingDone = z.infer<typeof readingDone>

const COMMANDS: Record<ReadingDone['kind'], string> = { reading: '/lani-app-reading', story: '/lani-app-story', aloud: '/lani-app-read-aloud' }

/** What a reading done says in a sentence, for the session's notes and the tutor. */
export function readingNote(d: ReadingDone): string {
  const what = d.title ? `"${d.title}"` : d.id
  const level = d.level ? ` (${d.level})` : ''
  switch (d.kind) {
    case 'reading': {
      const byType = (d.questions ?? []).map(q => `${q.type ?? 'question'} ${q.right ? '✓' : '✗'}`).join(', ')
      const looks = d.looks ? `; looked back at the text ${d.looks === 1 ? 'once' : `${d.looks} times`} while answering` : ''
      return `Read ${what}${level} in the Lani app: ${d.correct}/${d.exercises} questions right the first time${byType ? ` (${byType})` : ''}${looks}.`
    }
    case 'story':
      return `Heard the story ${what}${level} to its end by the fire in the Lani app: ${d.correct}/${d.exercises} turns right the first time.`
    case 'aloud': {
      const a = d.aloud
      const words = a ? ` ${a.right}/${a.words} words right (${a.misread} misread, ${a.skipped} skipped)${a.wpm ? `, ${a.wpm} words a minute` : ''}` : ''
      const misses = a?.misses?.length ? `; ${a.misses.map(m => (m.heard ? `${m.word} → ${m.heard}` : `${m.word} (skipped)`)).join(', ')}` : ''
      const by = a?.recognizer === 'phone' ? ", heard by the phone's recognizer" : a?.recognizer === 'node' ? ", heard by the node's Whisper" : ''
      return `Read ${what}${level} aloud in the Lani app${by}:${words}; ${d.correct}/${d.exercises} sentences read well${misses}.`
    }
  }
}

/**
 * The update-db.py report of reading practice [d] (docs/DB_SCRIPTS.md): a reading, its questions; a story, its turns;
 * a reading aloud counts as reading and as speaking, its sentences. In [session]'s data (another language's, with its
 * code).
 */
export function readingPayload(d: ReadingDone, session: { session_id: string; date: string; language?: string }) {
  const score = { exercises: d.exercises, correct: d.correct, time_minutes: d.duration_minutes }
  const skills = d.kind === 'aloud' ? ['reading', 'speaking'] : ['reading']
  return {
    session_id: session.session_id,
    date: session.date,
    ...(session.language ? { language: session.language } : {}),
    duration_minutes: d.duration_minutes,
    command_used: COMMANDS[d.kind],
    skills_practiced: skills,
    skill_scores: Object.fromEntries(skills.map(s => [s, score])),
    topics_covered: [d.id],
    session_notes: readingNote(d),
  }
}
