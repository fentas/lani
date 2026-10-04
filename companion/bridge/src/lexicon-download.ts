// A word lookup dictionary as a download (companion/README.md, "Word lookup"): the large ones (Italian, German,
// English) aren't in the repository. companion/bin/lexicon-build --manifest writes <language>.manifest.json next to the
// file, with the bases the dictionary has meanings in (Slovene, Italian, German, English), how many of its lemmas
// have one, and the sources and attribution its licence asks for; LANI_LEXICON_URL names where both are (an
// https:// base URL, or a local directory). The bridge asks for its village's language at start (and for another
// town's on a visit, at its first lookup), checks the file (size and sha256), keeps it in
// ${XDG_CACHE_HOME:-~/.cache}/lani/lexicon/ (LANI_LEXICON_CACHE; …/fluent/lexicon/ while only that one exists), and reads it from there when the source can't be
// reached. Without LANI_LEXICON_URL nothing is downloaded.
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { join, resolve } from 'node:path'
import { gunzipSync } from 'node:zlib'
import { z } from 'zod'
import { schemaField } from './schema'
import type { Log } from './config'
import { laniDir } from './env'
import type { LexiconFile } from './lexicon'

export const lexiconManifest = z.object({
  schema: schemaField('lani.lexicon/v0'),
  language: z.string().regex(/^[a-z]{2,3}$/),
  /** The build: its date and the start of its sha256 ("2026-09-26-1a2b3c4d"). */
  version: z.string().min(1).max(80),
  /** The file next to the manifest: <language>.json, or <language>.json.gz. */
  file: z.string().regex(/^[a-z]{2,3}(?:\.[a-z0-9-]+)*\.json(?:\.gz)?$/),
  sha256: z.string().regex(/^[0-9a-f]{64}$/),
  size: z.number().int().positive(),
  licence: z.string().optional(),
  licence_url: z.string().optional(),
  source: z.string().optional(),
  attribution: z.string().optional(),
  counts: z.record(z.string(), z.number()).optional(),
  /** The languages the lemmas have meanings in: how many have one (share of all), and how they were found. */
  bases: z.record(z.string(), z.looseObject({ lemmas: z.number(), share: z.number() })).optional(),
  /** The Wiktionaries the meanings come from (their kaikki.org extracts). */
  sources: z.array(z.looseObject({ edition: z.string(), name: z.string(), urls: z.array(z.string()) })).optional(),
})
export type LexiconManifest = z.infer<typeof lexiconManifest>

/** Where downloaded dictionaries are kept. */
export function lexiconCacheDir(env: Record<string, string | undefined> = process.env): string {
  if (env.LANI_LEXICON_CACHE) return resolve(env.LANI_LEXICON_CACHE)
  return join(laniDir(env.XDG_CACHE_HOME || join(homedir(), '.cache')), 'lexicon')
}

export const sha256 = (b: Uint8Array) => createHash('sha256').update(b).digest('hex')

/** A file's JSON as the manifest names it (gzipped or not). */
export function parseJsonFile(bytes: Uint8Array, file: string): unknown {
  return JSON.parse(new TextDecoder().decode(file.endsWith('.gz') ? gunzipSync(bytes) : bytes))
}

/** A dictionary (companion/lexicon/<language>.json's shape), or an error. */
export function parseLexicon(bytes: Uint8Array, file: string): LexiconFile {
  const f = parseJsonFile(bytes, file) as LexiconFile
  if (!f || typeof f !== 'object' || !f.meta || !Array.isArray(f.lemmas) || !f.forms || typeof f.forms !== 'object') throw new Error('not a lexicon file')
  return f
}

export type Fetched<T> = { file: T; manifest: LexiconManifest; from: 'download' | 'cache'; path: string }

export type LexiconSource = {
  /** LANI_LEXICON_URL: an https:// base URL or a local directory (a path or file://). */
  url: string
  language: string
  cacheDir: string
  log?: Log
  timeoutMs?: number
}

const isHttp = (u: string) => /^https?:\/\//i.test(u)

/** Reads [name] at [base]: a URL (base + "/" + name) or a local directory (file:// or a path). */
async function read(base: string, name: string, timeoutMs: number): Promise<Uint8Array> {
  if (isHttp(base)) {
    const r = await fetch(`${base.replace(/\/+$/, '')}/${name}`, { signal: AbortSignal.timeout(timeoutMs) })
    if (!r.ok) throw new Error(`HTTP ${r.status} for ${name}`)
    return new Uint8Array(await r.arrayBuffer())
  }
  const dir = base.startsWith('file://') ? new URL(base).pathname : base
  return new Uint8Array(readFileSync(join(dir, name)))
}

const fits = (m: LexiconManifest, o: LexiconSource) => m.language === o.language

/** The cached copy of [name], if its manifest and a file matching it are there. */
function cached(o: LexiconSource, name: string): { manifest: LexiconManifest; bytes: Uint8Array; path: string } | null {
  try {
    const m = lexiconManifest.parse(JSON.parse(readFileSync(join(o.cacheDir, `${name}.manifest.json`), 'utf8')))
    const path = join(o.cacheDir, m.file)
    if (!fits(m, o) || !existsSync(path)) return null
    const bytes = new Uint8Array(readFileSync(path))
    return bytes.length === m.size && sha256(bytes) === m.sha256 ? { manifest: m, bytes, path } : null
  } catch {
    return null
  }
}

/**
 * The dictionary of [o.language] from [o.url]: the manifest first; the cached copy when
 * it is that version, else the file, checked and cached; the cached copy (any version) when the source can't be
 * reached or sends something else. [parse] reads the file; an error says why there is none.
 */
export async function fetchLexicon<T>(o: LexiconSource, parse: (bytes: Uint8Array, file: string) => T): Promise<Fetched<T> | { error: string }> {
  const { url, cacheDir, log } = o
  const name = o.language
  const timeoutMs = o.timeoutMs ?? 60_000
  const have = cached(o, name)
  const fromCache = (why?: string): Fetched<T> => {
    if (why) log?.(`lexicon ${name}: ${why}; the cached ${have!.manifest.version}`)
    return { file: parse(have!.bytes, have!.manifest.file), manifest: have!.manifest, from: 'cache', path: have!.path }
  }
  let manifest: LexiconManifest
  try {
    const m = lexiconManifest.safeParse(JSON.parse(new TextDecoder().decode(await read(url, `${name}.manifest.json`, Math.min(timeoutMs, 15_000)))))
    if (!m.success) throw new Error(`manifest: ${z.prettifyError(m.error)}`)
    if (!fits(m.data, o)) throw new Error(`manifest is for ${m.data.language}, not ${name}`)
    manifest = m.data
  } catch (e) {
    if (have) return fromCache(`${url} not usable (${(e as Error).message})`)
    return { error: `${url}: ${(e as Error).message}` }
  }
  if (have && have.manifest.sha256 === manifest.sha256) return fromCache()
  let bytes: Uint8Array
  try {
    bytes = await read(url, manifest.file, timeoutMs)
  } catch (e) {
    if (have) return fromCache(`${manifest.file} not reachable (${(e as Error).message})`)
    return { error: `${manifest.file}: ${(e as Error).message}` }
  }
  if (bytes.length !== manifest.size || sha256(bytes) !== manifest.sha256) {
    const why = `${manifest.file} doesn't match its manifest (${bytes.length} bytes, sha256 ${sha256(bytes).slice(0, 12)}…; want ${manifest.size}, ${manifest.sha256.slice(0, 12)}…)`
    if (have) return fromCache(why)
    return { error: why }
  }
  const file = parse(bytes, manifest.file)
  mkdirSync(cacheDir, { recursive: true })
  const path = join(cacheDir, manifest.file)
  writeFileSync(`${path}.part`, bytes)
  renameSync(`${path}.part`, path)
  writeFileSync(join(cacheDir, `${name}.manifest.json`), JSON.stringify(manifest, null, 2) + '\n')
  log?.(`lexicon ${name}: ${manifest.version} downloaded from ${url} (${(bytes.length / 1024 / 1024).toFixed(1)} MB), cached in ${cacheDir}`)
  return { file, manifest, from: 'download', path }
}
