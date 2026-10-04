// Versioned module storage under <data>/app/modules/<id>/v<N>.json, plus current.json.
// Curated modules (companion/modules/*.json, in the repo) are served read-only as version 1;
// a module the tutor publishes with the same id takes precedence.
import { mkdirSync, readdirSync, readFileSync, renameSync, writeFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'
import { validateModule, type ModuleSpec } from './spec'

/** Module ids as the spec allows them: they name directories, so nothing else gets near a path. */
const ID = /^[a-z0-9][a-z0-9-]{1,62}$/
const isVersion = (v: unknown): v is number => Number.isInteger(v) && (v as number) >= 1

export type ModuleInfo = { id: string; version: number; title: string; level: string; targets: string[]; tags: string[]; quest?: ModuleSpec['quest'] }

function writeAtomic(path: string, data: unknown) {
  const tmp = `${path}.tmp`
  writeFileSync(tmp, JSON.stringify(data, null, 2) + '\n')
  renameSync(tmp, path)
}

export class ModuleStore {
  private curated = new Map<string, ModuleSpec & { version: number }>()

  constructor(private root: string, curatedDir?: string, log: (...a: unknown[]) => void = () => {}) {
    mkdirSync(root, { recursive: true })
    if (!curatedDir || !existsSync(curatedDir)) return
    for (const f of readdirSync(curatedDir).filter(f => f.endsWith('.json'))) {
      const v = validateModule(JSON.parse(readFileSync(join(curatedDir, f), 'utf8')))
      if (v.ok) this.curated.set(v.spec.id, { ...v.spec, version: 1 })
      else log(`skipping curated module ${f}: ${v.errors}`)
    }
  }

  private dir(id: string) {
    return join(this.root, id)
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
    if (version === undefined) return this.curated.get(id)
    if (!isVersion(version)) return undefined
    const p = join(this.dir(id), `v${version}.json`)
    if (existsSync(p)) return JSON.parse(readFileSync(p, 'utf8'))
    return version === 1 ? this.curated.get(id) : undefined
  }

  publish(spec: ModuleSpec): number {
    if (!ID.test(spec.id)) throw new Error(`invalid module id ${JSON.stringify(spec.id)}`)
    mkdirSync(this.dir(spec.id), { recursive: true })
    // A curated module counts as v1, so the tutor's first rewrite of it is v2 and shows as new.
    const version = (this.versions(spec.id).at(-1) ?? (this.curated.has(spec.id) ? 1 : 0)) + 1
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
    const ids = [...new Set([...published, ...this.curated.keys()])]
    return ids.flatMap(id => {
      const m = this.get(id)
      return m ? [{ id: m.id, version: m.version, title: m.title, level: m.level, targets: m.targets, tags: m.tags, quest: m.quest }] : []
    })
  }
}
