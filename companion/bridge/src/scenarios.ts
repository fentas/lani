// lani.scenario/v0 — role-play conversations: the tutor plays a character, Jan talks (by voice).
// Curated scenarios live in companion/scenarios/*.json; the tutor publishes more to <data>/app/scenarios.
// Keep in sync with .claude/skills/lani-studio/reference/module-spec.md ("Role-play scenarios").
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, statSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { schemaField } from './schema'
import { SPEAKER_ID } from './cast'

const text = z.string().trim().min(1)

export const scenarioSpec = z.object({
  schema: schemaField('lani.scenario/v0'),
  id: z.string().regex(/^[a-z0-9][a-z0-9-]{1,62}$/, 'id must be kebab-case, 2-63 chars'),
  title: text.max(80),
  emoji: text.default('🗣️'),
  level: z.enum(['A1', 'A2', 'B1', 'B2', 'C1', 'C2']),
  setting: text.max(400), // 1-2 sentences, English
  role: text.max(300), // who the tutor plays
  voice: z.enum(['female', 'male']).optional(), // the character's gender: how their lines sound in the app (default female)
  speaker: z.string().regex(SPEAKER_ID).optional(), // a character voice from the cast (companion/voice-cast.json); voice stays the gender and the fallback
  goals: z.array(text.max(80)).min(3).max(5),
  opener_sl: text.max(200),
  opener_en: text.max(200),
  vocabulary_hints: z.array(z.object({ sl: text.max(80), en: text.max(80) })).max(12).default([]),
})

export type Scenario = z.infer<typeof scenarioSpec>
export type LoadedScenario = Scenario & { source: 'curated' | 'tutor'; published_at?: number }

export function validateScenario(input: unknown): { ok: true; scenario: Scenario } | { ok: false; errors: string } {
  const r = scenarioSpec.safeParse(input)
  return r.success ? { ok: true, scenario: r.data } : { ok: false, errors: z.prettifyError(r.error) }
}

/** What the tutor needs on every roleplay turn, so the character survives a compacted context. */
export function scenarioBrief(s: Scenario) {
  return { title: s.title, role: s.role, setting: s.setting, level: s.level, goals: s.goals, opener_sl: s.opener_sl }
}

export class ScenarioStore {
  private warned = new Set<string>()

  constructor(
    private curatedDir: string,
    private tutorDir: string,
    private log: (...a: unknown[]) => void = () => {},
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
          const published_at = source === 'tutor' ? statSync(path).mtimeMs : undefined
          return [{ ...v.scenario, source, published_at }]
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

  get(id: string): LoadedScenario | undefined {
    return this.all().find(s => s.id === id)
  }

  /** Writes a tutor scenario (same id = replace). Returns an error for curated ids. */
  publish(s: Scenario): string | undefined {
    if (this.read(this.curatedDir, 'curated').some(c => c.id === s.id)) return `"${s.id}" is a curated scenario; pick another id`
    const path = join(this.tutorDir, `${s.id}.json`)
    writeFileSync(`${path}.tmp`, JSON.stringify(s, null, 2) + '\n')
    renameSync(`${path}.tmp`, path)
  }
}
