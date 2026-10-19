// Local overrides of curated content (docs/plans/05-built/studio.md, plan 5 "After all phases"): a fix the tutor makes
// to a curated module, scene, story or villager applies on the learner's side at once, as the tutor's own item under the
// curated id (the stores already prefer it: a tutor module's versions over the curated v1, a tutor scene, story or
// villager over the curated one). What this adds is the way back: the registry (app/overrides.json) keeps, for each such
// item, the curated file it replaces and that file's hash when the override was first seen. When the curated file
// changes upstream (a fix shipped: a git pull and a restart on a node, an app update on the phone), the override
// retires: the tutor's file(s) move to app/overrides/retired/<kind>/<id>-<stamp>…, the curated item plays again, the
// report that named it is resolved, and the channel hears of it (kind override_retired).
//
// The check runs at the bridge's start, every 15 minutes, after a publish, remove or rollback through the app tutor's
// tool route, and after a write of the file tools (features/tutor.ts). A Claude Code publish is seen by the next one.
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { writeAtomic } from './pairing'

export const OVERRIDE_KINDS = ['module', 'scene', 'story', 'villager'] as const
export type OverrideKind = (typeof OVERRIDE_KINDS)[number]

/** Where a kind's items are: its curated directories in order (the first with an id wins), the tutor's directory. */
export type OverrideSource = {
  dirs: () => { curated: string[]; tutor: string }
  /** 'file': one <file>.json per item, its id inside; 'dir': a module's <id>/ with its versions and current.json. */
  layout: 'file' | 'dir'
}

export type OverrideEntry = {
  kind: OverrideKind
  id: string
  /** The curated file it replaces, relative to the project (or absolute, outside it). */
  curated: string
  /** sha256 of that file's text when the override was first seen. */
  hash: string
  since: string
  state: 'active' | 'retired'
  retired_at?: string
  why?: string
  /** Where the tutor's file(s) went, relative to the app directory (app/). */
  moved_to?: string
}

export type OverrideRegistry = { schema: 'lani.overrides/v1'; items: OverrideEntry[] }

const sha256 = (s: string) => createHash('sha256').update(s).digest('hex')
/** "20261019T204512" */
const stamp = (d = new Date()) => d.toISOString().replace(/[-:]/g, '').replace(/\.\d+Z$/, '')
/** The retired entries kept (the active ones are all kept). */
const RETIRED_KEPT = 100

/** The id of a content file ([path]: JSON with an `id`), or undefined when it doesn't read. */
function idOf(path: string): string | undefined {
  try {
    const o = JSON.parse(readFileSync(path, 'utf8'))
    return typeof o?.id === 'string' ? o.id : undefined
  } catch {
    return undefined
  }
}

/** The JSON files of [dir] with their ids, in name order. */
function idFiles(dir: string): { id: string; path: string }[] {
  if (!existsSync(dir)) return []
  return readdirSync(dir)
    .filter(f => f.endsWith('.json'))
    .sort()
    .flatMap(f => {
      const path = join(dir, f)
      const id = idOf(path)
      return id ? [{ id, path }] : []
    })
}

export class Overrides {
  private readonly file: string

  constructor(
    private readonly o: {
      appDir: string
      projectDir: string
      sources: Record<OverrideKind, OverrideSource>
      log: (...a: unknown[]) => void
      /** An override retired (after the registry is saved): the report it resolved, if any, by the caller. */
      onRetired?: (e: OverrideEntry) => void
    },
  ) {
    this.file = join(o.appDir, 'overrides.json')
  }

  read(): OverrideRegistry {
    try {
      const r = JSON.parse(readFileSync(this.file, 'utf8'))
      if (r?.schema === 'lani.overrides/v1' && Array.isArray(r.items)) return r
    } catch {} // none yet
    return { schema: 'lani.overrides/v1', items: [] }
  }

  private save(r: OverrideRegistry) {
    mkdirSync(this.o.appDir, { recursive: true })
    const retired = r.items.filter(e => e.state === 'retired')
    const keep = new Set(retired.slice(-RETIRED_KEPT))
    writeAtomic(this.file, JSON.stringify({ ...r, items: r.items.filter(e => e.state === 'active' || keep.has(e)) }, null, 2) + '\n')
  }

  /** The curated files of [kind] by id: the first curated directory with an id wins. */
  curatedFiles(kind: OverrideKind): Map<string, string> {
    const out = new Map<string, string>()
    for (const dir of this.o.sources[kind].dirs().curated) for (const f of idFiles(dir)) if (!out.has(f.id)) out.set(f.id, f.path)
    return out
  }

  /** The curated file of [kind]'s [id]: the first curated directory with it. */
  curatedFile(kind: OverrideKind, id: string): string | undefined {
    return this.curatedFiles(kind).get(id)
  }

  /** The tutor's items of [kind]: a file each (its id inside), or a module's directory with a current.json. */
  tutorItems(kind: OverrideKind): { id: string; path: string }[] {
    const s = this.o.sources[kind]
    const dir = s.dirs().tutor
    if (s.layout === 'file') return idFiles(dir)
    if (!existsSync(dir)) return []
    return readdirSync(dir)
      .filter(name => {
        try {
          return statSync(join(dir, name)).isDirectory() && existsSync(join(dir, name, 'current.json'))
        } catch {
          return false
        }
      })
      .sort()
      .map(name => ({ id: name, path: join(dir, name) }))
  }

  private rel(path: string) {
    const r = relative(this.o.projectDir, path)
    return r.startsWith('..') ? path : r
  }

  /**
   * Records the overrides not yet known, retires those whose curated file changed, drops those whose tutor item is gone.
   * [only]: one kind (or one item of it) after a publish; all of them by default. What changed.
   */
  check(only?: { kind?: OverrideKind; id?: string }): { recorded: OverrideEntry[]; retired: OverrideEntry[]; dropped: OverrideEntry[] } {
    const reg = this.read()
    const recorded: OverrideEntry[] = []
    const retired: OverrideEntry[] = []
    const dropped: OverrideEntry[] = []
    const now = new Date()
    const kinds = only?.kind ? [only.kind] : [...OVERRIDE_KINDS]
    for (const kind of kinds) {
      let items: { id: string; path: string }[]
      try {
        items = this.tutorItems(kind)
      } catch (e) {
        this.o.log(`overrides: the tutor's ${kind}s don't read: ${(e as Error).message}`)
        continue
      }
      const present = new Set<string>()
      const curatedOf = items.length ? this.curatedFiles(kind) : new Map<string, string>()
      for (const item of items) {
        if (only?.id && item.id !== only.id) {
          present.add(item.id)
          continue
        }
        const curated = curatedOf.get(item.id)
        if (!curated) continue
        present.add(item.id)
        let hash: string
        try {
          hash = sha256(readFileSync(curated, 'utf8'))
        } catch {
          continue
        }
        const entry = reg.items.find(e => e.state === 'active' && e.kind === kind && e.id === item.id)
        if (!entry) {
          const e: OverrideEntry = { kind, id: item.id, curated: this.rel(curated), hash, since: now.toISOString(), state: 'active' }
          reg.items.push(e)
          recorded.push(e)
          this.o.log(`overrides: the tutor's ${kind} ${item.id} replaces the curated one (${e.curated}): it retires when that file changes`)
          continue
        }
        if (entry.hash === hash) continue
        // the curated file changed upstream: the curated item plays again, the tutor's is kept aside
        const dest = join(this.o.appDir, 'overrides', 'retired', kind, `${item.id}-${stamp(now)}${this.o.sources[kind].layout === 'file' ? '.json' : ''}`)
        try {
          mkdirSync(join(this.o.appDir, 'overrides', 'retired', kind), { recursive: true })
          renameSync(item.path, dest)
        } catch (e) {
          this.o.log(`overrides: the tutor's ${kind} ${item.id} couldn't be moved aside: ${(e as Error).message}`)
          continue
        }
        Object.assign(entry, { state: 'retired', retired_at: now.toISOString(), why: `the curated file changed (${entry.hash.slice(0, 8)} → ${hash.slice(0, 8)})`, moved_to: relative(this.o.appDir, dest) })
        retired.push(entry)
        present.delete(item.id)
        this.o.log(`overrides: the curated ${kind} ${item.id} changed upstream: the tutor's override retired (kept in app/${entry.moved_to})`)
      }
      // an active override whose tutor item is gone (remove_scene, a rollback to the curated v1): nothing to retire
      for (const e of reg.items.filter(x => x.state === 'active' && x.kind === kind && !present.has(x.id) && (!only?.id || x.id === only.id))) {
        reg.items.splice(reg.items.indexOf(e), 1)
        dropped.push(e)
      }
    }
    if (recorded.length || retired.length || dropped.length) this.save(reg)
    for (const e of retired) {
      try {
        this.o.onRetired?.(e)
      } catch (err) {
        this.o.log(`overrides: ${(err as Error).message}`)
      }
    }
    return { recorded, retired, dropped }
  }
}
