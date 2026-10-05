// The curated content as each learner reads it (companion/SCENES.md, "The learner in the content"): every JSON file of
// companion/scenes, cultures, packs, modules, scenarios, grammar and drills, its texts rendered for a learner. The smoke's
// learner section checks that, said to Jan (a man: the name the content had before its placeholders), every text reads
// exactly as it did at the commit the placeholders came in after ([BASE]): the same text is the same voice clip, and
// nothing changes for him. test/fixtures/learner-neutral.json keeps, for each file, a hash of its texts at [BASE] (said to
// Jan too: culture.json's role-play setting had {learner} already) and one of the file as the placeholders left it; a file
// edited since is someone else's change, and isn't compared.
//
//   bun test/learner-content.ts --write     the fixture again (the files as they are now, and [BASE] from git)
//   bun test/learner-content.ts             the check alone
//   bun test/learner-content.ts --all       the check of every file, those edited since too (while the placeholders go in)
import { createHash } from 'node:crypto'
import { existsSync, lstatSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'
import { addressee, renderDeep, type Addressee } from '../src/addressee'

/** The commit the content had its learner's name and gender written out at (the branch point of the placeholders). */
export const BASE = '854f737'

export const companionDir = resolve(import.meta.dir, '../..')
export const repoDir = resolve(companionDir, '..')
export const FIXTURE = join(import.meta.dir, 'fixtures/learner-neutral.json')

/** The directories of the curated content, under companion/. */
export const CONTENT_DIRS = ['scenes', 'cultures', 'packs', 'modules', 'scenarios', 'grammar', 'drills']

/** Every JSON file of the curated content, as a path from the repository's root ("companion/scenes/ob-ognju.json"). */
export function contentFiles(): string[] {
  const out: string[] = []
  const walk = (dir: string) => {
    for (const e of readdirSync(dir).sort()) {
      const p = join(dir, e)
      const st = lstatSync(p)
      if (st.isSymbolicLink()) continue
      if (st.isDirectory()) walk(p)
      else if (e.endsWith('.json')) out.push(relative(repoDir, p))
    }
  }
  for (const d of CONTENT_DIRS) if (existsSync(join(companionDir, d))) walk(join(companionDir, d))
  return out
}

/** A JSON value's texts, one "path<TAB>text" line each, its keys sorted: what two files must agree on to read the same. */
export function canonical(v: unknown): string {
  const lines: string[] = []
  const walk = (x: unknown, at: string) => {
    if (typeof x === 'string') lines.push(`${at}\t${x}`)
    else if (Array.isArray(x)) x.forEach((y, i) => walk(y, `${at}/${i}`))
    else if (x && typeof x === 'object') for (const k of Object.keys(x).sort()) walk((x as Record<string, unknown>)[k], `${at}/${k}`)
  }
  walk(v, '')
  return lines.join('\n')
}

export const sha256 = (s: string) => createHash('sha256').update(s).digest('hex')

/** Jan, as the content said his name and his gender before the placeholders. */
export const JAN: Addressee = addressee({ name: 'Jan', gender: 'male' })

export type Fixture = { about: string; base: string; files: Record<string, { before: string; after: string }> }

/** The file at [BASE], from git; undefined when git doesn't have it (a shallow clone, a file new since). */
export function atBase(path: string): string | undefined {
  const r = Bun.spawnSync(['git', '-C', repoDir, 'show', `${BASE}:${path}`], { stderr: 'ignore' })
  return r.exitCode === 0 ? r.stdout.toString() : undefined
}

/**
 * The comparison: for each file the fixture has and that wasn't edited since, whether its texts said to Jan are its texts
 * at [BASE]. [differ]: the first texts that don't (when git has [BASE] to show them), by file.
 */
export function compare(fx: Fixture, all = false): { compared: number; edited: string[]; missing: string[]; differ: Record<string, string[]> } {
  const out = { compared: 0, edited: [] as string[], missing: [] as string[], differ: {} as Record<string, string[]> }
  for (const [path, h] of Object.entries(fx.files)) {
    const file = join(repoDir, path)
    if (!existsSync(file)) {
      out.missing.push(path)
      continue
    }
    const raw = readFileSync(file, 'utf8')
    if (!all && sha256(raw) !== h.after) {
      out.edited.push(path)
      continue
    }
    out.compared++
    const said = canonical(renderDeep(JSON.parse(raw), JAN))
    if (sha256(said) === h.before) continue
    const old = atBase(path)
    const was = old === undefined ? [] : canonical(renderDeep(JSON.parse(old), JAN)).split('\n')
    const now = said.split('\n')
    out.differ[path] = now.filter((l, i) => l !== was[i]).slice(0, 3).map(l => `now: ${l}`)
    if (!out.differ[path].length) out.differ[path] = ['(the texts differ from the fixture\'s hash)']
  }
  return out
}

if (import.meta.main) {
  if (process.argv.includes('--write')) {
    const old: Fixture | undefined = existsSync(FIXTURE) ? JSON.parse(readFileSync(FIXTURE, 'utf8')) : undefined
    const files: Fixture['files'] = {}
    for (const path of contentFiles()) {
      const before = atBase(path)
      if (before === undefined) continue // new since: nothing it read like before
      files[path] = { before: old?.files[path]?.before ?? sha256(canonical(renderDeep(JSON.parse(before), JAN))), after: sha256(readFileSync(join(repoDir, path), 'utf8')) }
    }
    const fx: Fixture = {
      about: `The curated content's texts at ${BASE} (before: a hash of canonical(), test/learner-content.ts) and each file as its learner's placeholders left it (after: a hash of the file). Said to Jan, a file not edited since reads as before (the smoke's learner section). Made by: bun test/learner-content.ts --write`,
      base: BASE,
      files,
    }
    writeFileSync(FIXTURE, JSON.stringify(fx, null, 1) + '\n')
    console.log(`${FIXTURE}: ${Object.keys(files).length} files`)
  }
  const r = compare(JSON.parse(readFileSync(FIXTURE, 'utf8')), process.argv.includes('--all'))
  console.log(`compared ${r.compared}, edited since ${r.edited.length}, missing ${r.missing.length}, differ ${Object.keys(r.differ).length}`)
  for (const [p, d] of Object.entries(r.differ)) console.log(`${p}\n  ${d.join('\n  ')}`)
  process.exit(Object.keys(r.differ).length ? 1 : 0)
}
