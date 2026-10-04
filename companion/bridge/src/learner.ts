// The learner databases, through the repo's helper scripts (read-db.py, update-db.py), and the
// spaced-repetition items the bridge reads directly.
//
// One set of databases per language (docs/DB_SCRIPTS.md, "Languages"): the home language's (the learner's town's
// target) in the data directory, as always; another language's (practised on visits) in languages/<code>/. Every
// method takes an optional language code; none, or the home language's, is the home language.
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { run, spawn } from './config'
import { LANG } from './langs'
import { learnerFacts } from './learners'
import { cardTexts } from './voice'

export type SrItem = { type?: string; content?: string; answer?: string }

/** A request's optional language: a code (it, sl …); none means the home language. */
export const languageParam = z.string().regex(LANG, 'a language code, e.g. "it"').optional()

export class Learner {
  constructor(
    private readonly projectDir: string,
    readonly dataDir: string,
    /** The village's language: the home language when the learner profile doesn't say (a template's placeholders). */
    private readonly villageLanguage = 'sl',
  ) {}

  /** The home language: the profile's home_language (the scripts migrate it in), else its target language. */
  home(): string {
    try {
      const h = JSON.parse(readFileSync(join(this.dataDir, 'learner-profile.json'), 'utf8'))?.home_language
      if (typeof h === 'string' && LANG.test(h)) return h
    } catch {
      // no profile yet: its target, else the village's
    }
    return learnerFacts(this.dataDir).targetCode ?? this.villageLanguage
  }

  /** [language] when it is another language than the home one; undefined for the home language. */
  other(language?: string): string | undefined {
    return language && language !== this.home() ? language : undefined
  }

  /** read-db.py's output as JSON text: all 6 databases of [language] (default home) plus computed fields and every language's summary. Throws when it fails. */
  stateJson(language?: string): string {
    const l = this.other(language)
    return run(this.projectDir, ['python3', '.claude/hooks/read-db.py', ...(l ? ['--language', l] : [])])
  }

  state(language?: string): any {
    return JSON.parse(this.stateJson(language))
  }

  /** Persists a session report with update-db.py (atomic, with backup); a report's `language` says whose data it is. */
  update(payload: unknown): { ok: boolean; output: string } {
    const p = spawn(this.projectDir, ['python3', '.claude/hooks/update-db.py'], JSON.stringify(payload))
    return { ok: p.exitCode === 0, output: (p.stdout + p.stderr).trim() }
  }

  /** The spaced-repetition file of [language]: the data directory's for the home language, languages/<code>/'s for another. */
  srPath(language?: string): string {
    const l = this.other(language)
    return l ? join(this.dataDir, 'languages', l, 'spaced-repetition.json') : join(this.dataDir, 'spaced-repetition.json')
  }

  /** The spaced-repetition items of [language] (default home) by id; {} when the file is missing or unreadable. */
  srItems(language?: string): Record<string, SrItem> {
    const path = this.srPath(language)
    if (!existsSync(path)) return {}
    try {
      return JSON.parse(readFileSync(path, 'utf8')).items ?? {}
    } catch {
      return {}
    }
  }

  /** The learner's review cards (spaced repetition, the home language's), as speakable texts. */
  reviewCards(): string[] {
    return cardTexts(this.srItems())
  }
}
