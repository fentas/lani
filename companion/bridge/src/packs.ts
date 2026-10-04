// lani.pack/v0 — word packs: new vocabulary the app introduces, then turns into SR items.
// Curated packs live in companion/packs/*.json; the tutor publishes more to <data>/app/packs.
// Keep in sync with .claude/skills/lani-studio/reference/module-spec.md ("Word packs").
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, statSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { schemaField } from './schema'
import { LANG, label, labelEn, labelShown, type Label } from './langs'

const text = z.string().trim().min(1)
const kebab = (max: number) => z.string().regex(new RegExp(`^[a-z0-9][a-z0-9-]{0,${max - 1}}$`), `must be kebab-case, 1-${max} chars`)

/** The languages a pack's words can be in (the app's, l10n/Lang.kt). */
export const WORD_LANGUAGES = ['sl', 'en', 'it', 'de'] as const
export type WordLanguage = (typeof WORD_LANGUAGES)[number]

// A word: in the pack's language (what is learned and typed; "sl" in a Slovene pack, "it" in an Italian one) and its
// translations (English always; Slovene for a learner who explains in Slovene), an example in each, and a note (in
// English, "note"; in another base language, "note_sl" …).
const word = z.object({
  id: kebab(63),
  sl: text.max(60).optional(),
  en: text.max(80),
  it: text.max(60).optional(),
  de: text.max(60).optional(),
  emoji: text.optional(),
  gender: z.enum(['m', 'f', 'n']).optional(),
  plural: text.optional(),
  example_sl: text.optional(),
  example_en: text.optional(),
  example_it: text.optional(),
  example_de: text.optional(),
  note: text.optional(),
  note_sl: text.optional(),
  note_it: text.optional(),
  note_de: text.optional(),
})
export type PackWord = z.infer<typeof word>

export const packSpec = z
  .object({
    schema: schemaField('lani.pack/v0'),
    id: z.string().regex(/^[a-z0-9][a-z0-9-]{1,62}$/, 'id must be kebab-case, 2-63 chars'),
    /** The language the words are in (what is learned): Slovene unless it says otherwise. */
    language: z.enum(WORD_LANGUAGES).default('sl'),
    /** "Družina · Family" (target · English), or per language: {"it": "La famiglia", "sl": "Družina", "en": "Family"}. */
    title: label(80),
    emoji: text.default('📚'),
    level: z.enum(['A1', 'A2', 'B1', 'B2', 'C1', 'C2']),
    /** In English, or per language: {"sl": "…", "en": "…"}. */
    description: label(400).optional(),
    /** Who wrote which of its languages and who checked them: "de, it: machine-written, not reviewed by a native speaker". */
    review: text.max(200).optional(),
    giver: z.object({ name: text, emoji: text.default('🧑') }).optional(),
    // The calendar festival whose words these are (a culture pack's festival ids, companion/cultures/<id>/festivals.json:
    // "martinovo", "novo_leto"):
    // celebrating it in the village records them, and the packs screen shows it as a feast.
    festival: z.string().regex(/^[a-z][a-z_]{1,39}$/, 'festival must be a calendar festival id (lowercase, a-z and _)').optional(),
    words: z.array(word).min(8).max(24),
  })
  .superRefine((p, ctx) => {
    const seen = new Set<string>()
    p.words.forEach((w, i) => {
      if (seen.has(w.id)) ctx.addIssue({ code: 'custom', message: `duplicate word id "${w.id}"`, path: ['words', i, 'id'] })
      seen.add(w.id)
      if (!w[p.language]) ctx.addIssue({ code: 'custom', message: `no "${p.language}" (the pack's language: what is learned)`, path: ['words', i, p.language] })
    })
  })

export type Pack = z.infer<typeof packSpec>

/** What [w] teaches, in its pack's language. */
export const wordOf = (p: Pick<Pack, 'language'>, w: PackWord): string => w[p.language] ?? w.sl ?? w.en

/**
 * What [w] means for a learner who explains in [base] (the pack's other languages; English when it lacks [base]). An
 * English pack's "en" is the word itself, so its fallback is another of its translations.
 */
export function meaningOf(p: Pick<Pack, 'language'>, w: PackWord, base = 'en'): string {
  const fallback = p.language === 'en' ? (WORD_LANGUAGES.filter(l => l !== 'en').map(l => w[l]).find(t => !!t) ?? w.en) : w.en
  return base !== p.language && (WORD_LANGUAGES as readonly string[]).includes(base) ? (w[base as WordLanguage] ?? fallback) : fallback
}

/** The language meaningOf's answer is in: [base] when the word has it, else English (an English pack's: another). */
export function meaningLangOf(p: Pick<Pack, 'language'>, w: PackWord, base = 'en'): string {
  if (base !== p.language && (WORD_LANGUAGES as readonly string[]).includes(base) && w[base as WordLanguage]) return base
  return p.language === 'en' ? (WORD_LANGUAGES.find(l => l !== 'en' && !!w[l]) ?? 'en') : 'en'
}

/** An example sentence in its pack's language. */
export const exampleOf = (p: Pick<Pack, 'language'>, w: PackWord): string | undefined => w[`example_${p.language}`]

/** The pack's title for a learner of [target] explaining in [base]: "Družina · Family", "La famiglia · Družina". */
export const titleFor = (p: Pick<Pack, 'title'>, target: string, base: string): string => labelShown(p.title, target, base)

/** The pack's title in English, for the tutor. */
export const titleEn = (p: Pick<Pack, 'title'>): string => (typeof p.title === 'string' ? p.title : labelEn(p.title))

export type { Label }
export type PackSource = 'curated' | 'tutor'
export type LoadedPack = Pack & { source: PackSource; published_at?: number }

export function validatePack(input: unknown): { ok: true; pack: Pack } | { ok: false; errors: string } {
  const r = packSpec.safeParse(input)
  return r.success ? { ok: true, pack: r.data } : { ok: false, errors: z.prettifyError(r.error) }
}

/** The spaced-repetition item a pack word becomes. */
export const itemId = (packId: string, wordId: string) => `vocab_${packId}_${wordId}`

type SrItem = { type?: string; content?: string }

/** "Hvala." / "hvala" → "hvala": how a word is compared with what Jan already has. */
export const normWord = (s: string) => s.toLocaleLowerCase('sl').replace(/[.!?¿¡,]/g, '').replace(/\s+/g, ' ').trim()

/**
 * The words Jan already has as vocabulary items, normalized, each with the id of the item that has it: every
 * alternative of an item's content ("dober dan / živjo (informal)" → "dober dan", "živjo").
 */
export function vocabularyWords(items: Record<string, SrItem & { id?: string }>): Map<string, string> {
  const known = new Map<string, string>()
  for (const [id, it] of Object.entries(items)) {
    if ((it.type ?? 'vocabulary') !== 'vocabulary' || !it.content) continue
    for (const alt of it.content.split(' / ')) {
      const w = normWord(alt.replace(/\s*\([^)]*\)/g, ''))
      if (!known.has(w)) known.set(w, id)
    }
  }
  return known
}

/**
 * Word ids of [pack] Jan already has: its own SR item exists, or another vocabulary item has the
 * same Slovene (e.g. "hvala" from lesson 1), so a pack never re-teaches a known word.
 */
export function learnedWords(pack: Pack, items: Record<string, SrItem>): string[] {
  const known = vocabularyWords(items)
  return pack.words.filter(w => items[itemId(pack.id, w.id)] || known.has(normWord(wordOf(pack, w)))).map(w => w.id)
}

/**
 * Where the curated packs of a learner are, in order: their village's language's ([packsRoot] itself for Slovene, as
 * Jan's always were; packsRoot/<language> for another, companion/packs/it), then their culture pack's own
 * (companion/cultures/<id>/packs: its festivals' words).
 */
export function packsDirs(packsRoot: string, culturesDir: string, cultureId: string, language: string): string[] {
  return [language === 'sl' ? packsRoot : join(packsRoot, language), join(culturesDir, cultureId, 'packs')]
}

export class PackStore {
  private warned = new Set<string>() // path:mtime of invalid files already logged
  private curatedDirs: string[]

  /**
   * [curated]: the directories of the curated packs, in order (the learner's target language's packs, then their
   * culture's: packsDirs); the first with an id wins.
   */
  constructor(
    curated: string | string[],
    private tutorDir: string,
    private log: (...a: unknown[]) => void = () => {},
  ) {
    this.curatedDirs = Array.isArray(curated) ? curated : [curated]
    mkdirSync(tutorDir, { recursive: true })
  }

  private curated(): LoadedPack[] {
    const seen = new Set<string>()
    return this.curatedDirs.flatMap(d => this.read(d, 'curated')).filter(p => !seen.has(p.id) && !!seen.add(p.id))
  }

  private read(dir: string, source: PackSource): LoadedPack[] {
    if (!existsSync(dir)) return []
    return readdirSync(dir)
      .filter(f => f.endsWith('.json'))
      .sort()
      .flatMap(f => {
        const path = join(dir, f)
        try {
          const v = validatePack(JSON.parse(readFileSync(path, 'utf8')))
          if (!v.ok) throw new Error(v.errors)
          const published_at = source === 'tutor' ? statSync(path).mtimeMs : undefined
          return [{ ...v.pack, source, published_at }]
        } catch (e) {
          const key = `${path}:${statSync(path).mtimeMs}`
          if (!this.warned.has(key)) this.log(`skipping pack ${path}: ${(e as Error).message}`)
          this.warned.add(key)
          return []
        }
      })
  }

  /** Curated packs first; a tutor pack never shadows a curated id. */
  all(): LoadedPack[] {
    const curated = this.curated()
    const ids = new Set(curated.map(p => p.id))
    return [...curated, ...this.read(this.tutorDir, 'tutor').filter(p => !ids.has(p.id))]
  }

  get(id: string): LoadedPack | undefined {
    return this.all().find(p => p.id === id)
  }

  /** The curated packs alone (what a visiting town's scenes are resolved with: visits.ts). */
  curatedOnly(): LoadedPack[] {
    return this.curated()
  }

  /** Writes a tutor pack (replacing an earlier one with the same id). Returns an error for curated ids. */
  publish(pack: Pack): string | undefined {
    if (this.curated().some(p => p.id === pack.id)) return `"${pack.id}" is a curated pack; pick another id`
    const path = join(this.tutorDir, `${pack.id}.json`)
    writeFileSync(`${path}.tmp`, JSON.stringify(pack, null, 2) + '\n')
    renameSync(`${path}.tmp`, path)
  }
}

export const learnRun = z.object({
  results: z.array(z.object({ word_id: z.string().min(1), quality: z.number().int().min(0).max(5) })).min(1).max(24),
  duration_minutes: z.number().int().min(1).max(240),
  // whose words they become: another language's (a visit's), the home language's by default (docs/DB_SCRIPTS.md)
  language: z.string().regex(LANG, 'a language code, e.g. "it"').optional(),
})

/**
 * The update-db.py payload for words learned from [pack]; only [fresh] words are added: the word in the pack's language,
 * its meaning in the learner's [base] (English unless the pack has theirs). A session's [language] (another than the
 * home language) puts them into that language's data.
 */
export function learnPayload(
  pack: Pack,
  fresh: { word_id: string; quality: number }[],
  session: { session_id: string; date: string; duration_minutes: number; language?: string },
  base = 'en',
) {
  const byId = new Map(pack.words.map(w => [w.id, w]))
  const { language, ...rest } = session
  return {
    ...rest,
    ...(language ? { language } : {}),
    command_used: '/lani-app-words',
    skills_practiced: ['vocabulary'],
    skill_scores: {
      vocabulary: { exercises: fresh.length, correct: fresh.filter(r => r.quality >= 3).length, time_minutes: session.duration_minutes },
    },
    new_vocabulary: fresh.map(r => {
      const w = byId.get(r.word_id)!
      return {
        item_id: itemId(pack.id, w.id),
        item_type: 'vocabulary',
        content: wordOf(pack, w),
        answer: meaningOf(pack, w, base),
        category: pack.id,
        difficulty: pack.level,
        initial_quality: r.quality,
        priority: 'medium',
      }
    }),
    topics_covered: [pack.id],
    session_notes: `Learned ${fresh.length} new word(s) from the pack "${titleEn(pack)}" in the Lani app.`,
  }
}
