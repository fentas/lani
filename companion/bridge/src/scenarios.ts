// lani.scenario/v0 — role-play conversations: the tutor plays a character, Jan talks (by voice).
// Curated scenarios live in companion/scenarios/*.json (Slovene) and companion/scenarios/<language>/*.json (another
// language's: companion/scenarios/it for an Italian village); the tutor publishes more to <data>/app/scenarios.
// Keep in sync with .claude/skills/lani-studio/reference/module-spec.md ("Role-play scenarios").
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, statSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { learnerErrorsIn } from './addressee'
import { label, labelShown, langText, textIn, type Label, type LangText } from './langs'
import { schemaField } from './schema'
import { SPEAKER_ID } from './cast'

const text = z.string().trim().min(1)

/** The languages a scenario can be played in (the app's, l10n/Lang.kt): the village's. */
export const SCENARIO_LANGUAGES = ['sl', 'it', 'de', 'en'] as const
export type ScenarioLanguage = (typeof SCENARIO_LANGUAGES)[number]

/** The stage's places a role-play may stand in (the app's StagePlace): else its character's home. */
export const SCENARIO_PLACES = ['fire', 'kitchen', 'forest', 'square', 'field', 'smithy', 'school'] as const

/**
 * A text the learner reads in their own language (the setting, the opener's and the hints' meanings): one string
 * (English, as the Slovene scenarios have it), or a text per base, {"de": …, "sl": …, "en": …} (English always: the
 * tutor reads it, and it is the fallback).
 */
const based = (max: number) => z.union([text.max(max), langText(max).refine(t => typeof t.en === 'string', 'a text per base needs its English ("en")')])
/**
 * A label in the learner's pair (the title, a goal, who the character is): one string ("Na tržnici · At the market",
 * a goal in English), or per language with the scenario's own and English, {"it": "Al mercato", "de": "Auf dem Markt",
 * "en": "At the market"}, shown as "Al mercato · Auf dem Markt".
 */
const shown = (max: number) => label(max).refine(l => typeof l === 'string' || typeof l.en === 'string', 'a text per language needs its English ("en")')

export const scenarioSpec = z
  .object({
    schema: schemaField('lani.scenario/v0'),
    id: z.string().regex(/^[a-z0-9][a-z0-9-]{1,62}$/, 'id must be kebab-case, 2-63 chars'),
    /**
     * The language it is played in: the village's. A curated one is its directory's (companion/scenarios: Slovene,
     * companion/scenarios/it: Italian); a tutor's without one is the village's.
     */
    language: z.enum(SCENARIO_LANGUAGES).optional(),
    /** child: written for a child; adult: a grown-up's situation (the wine at the inn), never offered to a child. */
    audience: z.enum(['child', 'adult']).optional(),
    title: shown(80),
    emoji: text.default('🗣️'),
    level: z.enum(['A1', 'A2', 'B1', 'B2', 'C1', 'C2']),
    setting: based(400), // 1-2 sentences, English (or per base)
    role: text.max(300), // who the tutor plays, in English for the tutor: the character's name, then a comma
    /** Who the character is, as the learner reads it: {"it": "La fruttivendola", "de": "Die Obstverkäuferin", "en": …}. */
    who: langText(60).refine(t => typeof t.en === 'string', 'who needs its English ("en")').optional(),
    /** Where the app's stage stands behind the character (its backdrop): the market and a shop on the square, lunch in the kitchen. */
    place: z.enum(SCENARIO_PLACES).optional(),
    voice: z.enum(['female', 'male']).optional(), // the character's gender: how their lines sound in the app (default female)
    speaker: z.string().regex(SPEAKER_ID).optional(), // a character voice from the cast (companion/voice-cast.json); voice stays the gender and the fallback
    goals: z.array(shown(80)).min(3).max(5),
    opener_sl: text.max(200), // the character's first line, in the scenario's language (the key is sl in every language)
    opener_en: based(200),
    vocabulary_hints: z.array(z.object({ sl: text.max(80), en: based(80) })).max(12).default([]),
    /** The word packs whose words it uses (the village's packs: companion/packs/<language>, the culture pack's). */
    packs: z.array(z.string().regex(/^[a-z0-9][a-z0-9-]{1,62}$/)).max(6).optional(),
    /** Who wrote which of its languages and who checked them: "it, de, sl: machine-written, not reviewed by a native speaker". */
    review: text.max(200).optional(),
  })
  .superRefine((s, ctx) => {
    // a text per language names the scenario's own language (what the character says it in)
    const lang = s.language
    if (!lang) return
    const own = (l: Label, path: (string | number)[]) => {
      if (typeof l !== 'string' && typeof l[lang] !== 'string') ctx.addIssue({ code: 'custom', message: `no "${lang}" text (the scenario's language)`, path })
    }
    own(s.title, ['title'])
    if (s.who) own(s.who, ['who'])
  })

/** A scenario as written (its texts maybe per language). */
export type Scenario = z.infer<typeof scenarioSpec>
export type LoadedScenario = Scenario & { language: ScenarioLanguage; source: 'curated' | 'tutor'; published_at?: number }

export function validateScenario(input: unknown): { ok: true; scenario: Scenario } | { ok: false; errors: string } {
  const r = scenarioSpec.safeParse(input)
  if (!r.success) return { ok: false, errors: z.prettifyError(r.error) }
  const learner = learnerErrorsIn(r.data)
  return learner.length ? { ok: false, errors: learner.map(e => `✖ ${e}`).join('\n') } : { ok: true, scenario: r.data }
}

/** A text per base in [base] (else English); a string as it is. */
const inBase = (t: string | LangText, base: string): string => (typeof t === 'string' ? t : textIn(t, base))
/** In English, for the tutor: a string as it is, a text per language its English. */
const inEnglish = (t: string | LangText): string => inBase(t, 'en')
/** A label in the pair: "target · base" when it has the target ("Saluta · Grüße"), else in the base alone. */
const inPair = (l: Label, target: string, base: string): string =>
  typeof l === 'string' ? l : typeof l[target] === 'string' ? labelShown(l, target, base) : textIn(l, base)

/**
 * [s] as the app gets it, for a learner of [target] (the village's language) who explains in [base]: its title, goals and
 * who the character is in their pair ("Al mercato · Auf dem Markt"), its setting and the meanings of its opener and hints
 * in their base, each a string (an app reads them as it always did).
 */
export function servedScenario<S extends Scenario>(s: S, target: string, base: string): S {
  return {
    ...s,
    title: inPair(s.title, target, base),
    setting: inBase(s.setting, base),
    ...(s.who ? { who: inPair(s.who, target, base) } : {}),
    goals: s.goals.map(g => inPair(g, target, base)),
    opener_en: inBase(s.opener_en, base),
    vocabulary_hints: s.vocabulary_hints.map(h => ({ sl: h.sl, en: inBase(h.en, base) })),
  }
}

/** What a child's role-play adds to its brief on every turn (learners.ts CHILD_INSTRUCTIONS, in a line). */
export const CHILD_BRIEF =
  'A child is talking: stay kind, patient and playful; one or two short, simple sentences a turn; praise what they manage. Never ask for personal data (full name, address, school, phone, where they are), and keep none they give. Nothing frightening.'

/**
 * What the tutor needs on every roleplay turn, so the character survives a compacted context: in English (the title, the
 * setting, the goals), the opener in the scenario's language, which [language] it is played in, the packs whose words it
 * uses, and for a child ([child], or a scenario written for one) how to talk to them.
 */
export function scenarioBrief(s: Scenario, child = false) {
  const title = typeof s.title === 'string' ? s.title : [s.language && s.title[s.language], s.title.en].filter(Boolean).join(' · ')
  return {
    title,
    role: s.role,
    setting: inEnglish(s.setting),
    level: s.level,
    goals: s.goals.map(g => (typeof g === 'string' ? g : textIn(g, 'en'))),
    opener_sl: s.opener_sl,
    ...(s.language ? { language: s.language } : {}),
    ...(s.packs?.length ? { packs: s.packs } : {}),
    ...(child || s.audience === 'child' ? { child: CHILD_BRIEF } : {}),
  }
}

/** The packs [s] names that the village doesn't have ([packs]: its packs' ids). */
export function scenarioPackErrors(s: Pick<Scenario, 'id' | 'packs'>, packs: Iterable<string>): string[] {
  const have = new Set(packs)
  return (s.packs ?? []).filter(p => !have.has(p)).map(p => `${s.id}: pack "${p}" is not one of the village's`)
}

/**
 * The role-plays of a village: the curated ones of its language ([curatedDir]: companion/scenarios for Slovene,
 * companion/scenarios/<language> for another), then the tutor's (<data>/app/scenarios). Only [language]'s are served:
 * a file in another language is skipped and logged (a Slovene scenario never reaches an Italian village, nor the other
 * way round); a tutor's without a language is the village's, and is written with it.
 */
export class ScenarioStore {
  private warned = new Set<string>()

  constructor(
    private curatedDir: string,
    private tutorDir: string,
    private log: (...a: unknown[]) => void = () => {},
    readonly language: ScenarioLanguage | string = 'sl',
  ) {
    mkdirSync(tutorDir, { recursive: true })
  }

  private read(dir: string, source: LoadedScenario['source']): LoadedScenario[] {
    if (!existsSync(dir)) return []
    return readdirSync(dir)
      .filter(f => f.endsWith('.json'))
      .sort()
      .flatMap(f => {
        const path = join(dir, f)
        try {
          const v = validateScenario(JSON.parse(readFileSync(path, 'utf8')))
          if (!v.ok) throw new Error(v.errors)
          const language = (v.scenario.language ?? this.language) as ScenarioLanguage
          if (language !== this.language) throw new Error(`it is in "${language}", this village speaks "${this.language}"`)
          const published_at = source === 'tutor' ? statSync(path).mtimeMs : undefined
          return [{ ...v.scenario, language, source, published_at }]
        } catch (e) {
          const key = `${path}:${statSync(path).mtimeMs}`
          if (!this.warned.has(key)) this.log(`skipping scenario ${path}: ${(e as Error).message}`)
          this.warned.add(key)
          return []
        }
      })
  }

  /** Curated first; a tutor scenario never shadows a curated id. */
  all(): LoadedScenario[] {
    const curated = this.read(this.curatedDir, 'curated')
    const ids = new Set(curated.map(s => s.id))
    return [...curated, ...this.read(this.tutorDir, 'tutor').filter(s => !ids.has(s.id))]
  }

  /** The role-plays offered to the learner: a child ([child]) is never offered a grown-up's (audience adult). */
  offered(child: boolean): LoadedScenario[] {
    return this.all().filter(s => !(child && s.audience === 'adult'))
  }

  get(id: string): LoadedScenario | undefined {
    return this.all().find(s => s.id === id)
  }

  /** Whether [id] is a curated scenario's (a tutor scenario can't take it). */
  isCurated(id: string): boolean {
    return this.read(this.curatedDir, 'curated').some(c => c.id === id)
  }

  /** Why the village can't play [s]: another language than its own; undefined when it can. */
  languageError(s: Pick<Scenario, 'language'>): string | undefined {
    if (s.language && s.language !== this.language) return `language: this village speaks "${this.language}", the scenario is "${s.language}"`
  }

  /** Writes a tutor scenario (same id = replace), in the village's language. Returns an error for curated ids and another language. */
  publish(s: Scenario): string | undefined {
    if (this.read(this.curatedDir, 'curated').some(c => c.id === s.id)) return `"${s.id}" is a curated scenario; pick another id`
    const wrong = this.languageError(s)
    if (wrong) return wrong
    const path = join(this.tutorDir, `${s.id}.json`)
    writeFileSync(`${path}.tmp`, JSON.stringify({ ...s, language: this.language }, null, 2) + '\n')
    renameSync(`${path}.tmp`, path)
  }
}
