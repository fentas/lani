// Versioned module storage under <data>/app/modules/<id>/v<N>.json, plus current.json.
// Curated modules (companion/modules/*.json, another language's in companion/modules/<language>/, in the repo) are served
// read-only as version 1, their texts per base in the learner's; a module the tutor publishes with the same id takes
// precedence.
import { mkdirSync, readdirSync, readFileSync, renameSync, writeFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'
import { BASED_KEYS, basedErrors, inBase } from './course'
import { validateModule, type ModuleSpec, type Validation } from './spec'

/** Module ids as the spec allows them: they name directories, so nothing else gets near a path. */
const ID = /^[a-z0-9][a-z0-9-]{1,62}$/
const isVersion = (v: unknown): v is number => Number.isInteger(v) && (v as number) >= 1

export type ModuleInfo = { id: string; version: number; title: string; level: string; targets: string[]; tags: string[]; quest?: ModuleSpec['quest'] }

/**
 * Where a curated module's texts may be per base ({"de": …, "sl": …, "en": …}, English always; the Italian ones are): what
 * a sheet's may (COURSE.md "Texts in the learner's language"), and its title, description, a quest's story, a flashcard's
 * back and note. A tutor's module is written for this learner and keeps plain strings.
 */
export const MODULE_BASED_KEYS: ReadonlySet<string> = new Set([...BASED_KEYS, 'title', 'description', 'story', 'note', 'back'])

/** A curated module as the learner reads it in [base]: an app of any age gets plain strings. */
export function validateCuratedModule(input: unknown, base: string): Validation {
  const missing = basedErrors(input, MODULE_BASED_KEYS)
  if (missing.length) return { ok: false, errors: missing.join('\n') }
  return validateModule(inBase(input, base, MODULE_BASED_KEYS))
}

/** The CEFR levels in order: a curated module above the learner's isn't offered ([ModuleStore.offered]). */
const LEVEL_ORDER = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']

function writeAtomic(path: string, data: unknown) {
  const tmp = `${path}.tmp`
  writeFileSync(tmp, JSON.stringify(data, null, 2) + '\n')
  renameSync(tmp, path)
}

export class ModuleStore {
  /** The curated files as written, by id; [resolved] the ones read so far in the base [resolvedIn]. */
  private raw = new Map<string, unknown>()
  private resolved = new Map<string, ModuleSpec & { version: number }>()
  private resolvedIn?: string
  private readonly curatedDir?: string

  constructor(
    private root: string,
    curatedDir?: string,
    log: (...a: unknown[]) => void = () => {},
    /** The learner's base language: a curated module's texts per base are served in it. */
    private base: () => string = () => 'en',
  ) {
    mkdirSync(root, { recursive: true })
    this.curatedDir = curatedDir
    if (!curatedDir || !existsSync(curatedDir)) return
    for (const f of readdirSync(curatedDir).filter(f => f.endsWith('.json'))) {
      const input = JSON.parse(readFileSync(join(curatedDir, f), 'utf8'))
      const v = validateCuratedModule(input, 'en')
      if (v.ok) this.raw.set(v.spec.id, input)
      else log(`skipping curated module ${f}: ${v.errors}`)
    }
  }

  /** Curated module [id] in the learner's base. */
  private curated(id: string): (ModuleSpec & { version: number }) | undefined {
    if (!this.raw.has(id)) return undefined
    const base = this.base()
    if (base !== this.resolvedIn) {
      this.resolved.clear()
      this.resolvedIn = base
    }
    const had = this.resolved.get(id)
    if (had) return had
    const v = validateCuratedModule(this.raw.get(id), base)
    if (!v.ok) return undefined
    const m = { ...v.spec, version: 1 }
    this.resolved.set(id, m)
    return m
  }

  private dir(id: string) {
    return join(this.root, id)
  }

  /** Where the modules are (the overrides' check, overrides.ts): the curated directory (read once, at start) and the tutor's root. */
  dirs(): { curated: string[]; tutor: string } {
    return { curated: this.curatedDir ? [this.curatedDir] : [], tutor: this.root }
  }

  isCurated(id: string): boolean {
    return this.raw.has(id)
  }

  versions(id: string): number[] {
    if (!ID.test(id) || !existsSync(this.dir(id))) return []
    return readdirSync(this.dir(id))
      .map(f => /^v(\d+)\.json$/.exec(f)?.[1])
      .filter((v): v is string => v !== undefined)
      .map(Number)
      .sort((a, b) => a - b)
  }

  current(id: string): number | undefined {
    if (!ID.test(id)) return undefined
    const p = join(this.dir(id), 'current.json')
    return existsSync(p) ? JSON.parse(readFileSync(p, 'utf8')).version : undefined
  }

  get(id: string, requested?: number): (ModuleSpec & { version: number }) | undefined {
    if (!ID.test(id)) return undefined
    const version = requested ?? this.current(id)
    if (version === undefined) return this.curated(id)
    if (!isVersion(version)) return undefined
    const p = join(this.dir(id), `v${version}.json`)
    if (existsSync(p)) return JSON.parse(readFileSync(p, 'utf8'))
    return version === 1 ? this.curated(id) : undefined
  }

  publish(spec: ModuleSpec): number {
    if (!ID.test(spec.id)) throw new Error(`invalid module id ${JSON.stringify(spec.id)}`)
    mkdirSync(this.dir(spec.id), { recursive: true })
    // A curated module counts as v1, so the tutor's first rewrite of it is v2 and shows as new.
    const version = (this.versions(spec.id).at(-1) ?? (this.raw.has(spec.id) ? 1 : 0)) + 1
    writeAtomic(join(this.dir(spec.id), `v${version}.json`), { ...spec, version })
    writeAtomic(join(this.dir(spec.id), 'current.json'), { version })
    return version
  }

  rollback(id: string, version: number): boolean {
    if (!ID.test(id) || !isVersion(version) || !this.versions(id).includes(version)) return false
    writeAtomic(join(this.dir(id), 'current.json'), { version })
    return true
  }

  list(): ModuleInfo[] {
    const published = readdirSync(this.root, { withFileTypes: true }).filter(d => d.isDirectory()).map(d => d.name)
    const ids = [...new Set([...published, ...this.raw.keys()])]
    return ids.flatMap(id => {
      const m = this.get(id)
      return m ? [{ id: m.id, version: m.version, title: m.title, level: m.level, targets: m.targets, tags: m.tags, quest: m.quest }] : []
    })
  }

  /**
   * What the app is offered (GET /modules) for a learner at [level]: every module, less the curated ones above their level
   * (a congiuntivo for a child at A1 would come up in every run and as a villager's request; they come with the level-up).
   * What the tutor published is always offered: the tutor chose it for this learner. The tutor's own list has them all.
   */
  offered(level: string): ModuleInfo[] {
    const rank = LEVEL_ORDER.indexOf(level)
    return this.list().filter(m => !(m.version === 1 && this.raw.has(m.id) && LEVEL_ORDER.indexOf(m.level) > rank))
  }
}
