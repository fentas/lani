// The phone's offline voice: a Piper voice the app runs itself (sherpa-onnx's VITS/Piper engine) when a line has no clip
// on the phone and the node can't be reached. The model isn't in the APK: the bridge fetches it once per language from
// Hugging Face (LANI_PIPER_URL; a local directory of the same layout for a test), checks every file against the pins of
// the catalog below (LANI_PIPER_CATALOG: a JSON file that replaces it), turns it into what sherpa-onnx reads (the
// model with its metadata, tokens.txt, the espeak-ng data of its language), keeps it in
// ${XDG_CACHE_HOME:-~/.cache}/lani/piper/<voice id>/ (LANI_PIPER_CACHE) and serves the files to the app, which checks
// each file's sha256 again (features/offline-voice.ts).
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { open, type FileHandle } from 'node:fs/promises'
import { homedir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { z } from 'zod'
import type { Log } from './config'
import { laniDir } from './env'

const sha = z.string().regex(/^[0-9a-f]{64}$/, 'a sha256 in hex')
/** A file's size and sha256, as measured once: the download must be exactly this. */
const pin = z.object({ bytes: z.number().int().positive(), sha256: sha })
/** A relative path of plain segments (none empty, none starting with a dot): never a way out of a directory. */
const relPath = z
  .string()
  .max(200)
  .regex(/^[A-Za-z0-9_+-][A-Za-z0-9_.+-]*(?:\/[A-Za-z0-9_+-][A-Za-z0-9_.+-]*)*$/, 'a relative path of plain segments')
const pathPin = pin.extend({ path: relPath })
const segment = /[A-Za-z0-9_-][A-Za-z0-9_.-]*/
const repoRef = {
  /** A Hugging Face repository ("rhasspy/piper-voices"). */
  repo: z.string().regex(new RegExp(`^${segment.source}/${segment.source}$`), 'owner/name'),
  /** The commit the pins were measured at: a file is fetched from …/resolve/<revision>/<path>. */
  revision: z.string().regex(/^[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}$/),
}

export const piperCatalog = z
  .object({
    /** espeak-ng's data, which sherpa-onnx's Piper frontend reads (from a repository that has it, under [dir]). */
    espeak: z.object({
      ...repoRef,
      dir: relPath,
      /** The files every voice needs (the phoneme tables); each voice adds its language's. */
      shared: z.array(pathPin),
    }),
    voices: z.array(
      z.object({
        language: z.string().regex(/^[a-z]{2,3}$/),
        /** Piper's name of the voice ("sl_SI-artur-medium"): its files are <dir>/<id>.onnx, <id>.onnx.json and MODEL_CARD. */
        id: z.string().regex(/^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$/),
        name: z.string().trim().min(1).max(80),
        quality: z.string().trim().min(1).max(20),
        ...repoRef,
        dir: relPath,
        model: pin,
        config: pin,
        card: pin,
        /** The model with sherpa-onnx's metadata (as its scripts/vits-piper.py writes it): checked when given. */
        converted: pin.optional(),
        /** The espeak-ng files of the voice's language (its dictionary, its lang/ file), under the catalog's espeak dir. */
        espeak: z.array(pathPin),
        licence: z.string().trim().min(1),
        attribution: z.string().trim().min(1),
      }),
    ),
  })
  .superRefine((c, x) => {
    const seen = (key: (v: (typeof c.voices)[number]) => string, what: string) => {
      const keys = c.voices.map(key)
      const twice = keys.find((k, i) => keys.indexOf(k) !== i)
      if (twice) x.addIssue({ code: 'custom', message: `two voices with the ${what} "${twice}"`, path: ['voices'] })
    }
    seen(v => v.language, 'language')
    seen(v => v.id, 'id')
  })
export type PiperCatalog = z.infer<typeof piperCatalog>
export type PiperVoice = PiperCatalog['voices'][number]
type Espeak = PiperCatalog['espeak']

const PIPER = { repo: 'rhasspy/piper-voices', revision: 'c10ece1aade47bb51c153c893d14e5bf8e5b7117' }

/**
 * One voice per village language, pinned (measured on 2026-10-10): the model, its config and card from
 * rhasspy/piper-voices; the espeak-ng data from sherpa-onnx's own conversion of the Slovene voice (it carries the whole
 * espeak-ng-data; each voice takes only its language's files). The Slovene conversion was checked byte for byte against
 * sherpa-onnx's converted model.
 */
export const BUILTIN_CATALOG: PiperCatalog = {
  espeak: {
    repo: 'csukuangfj/vits-piper-sl_SI-artur-medium',
    revision: '6d8fe8757edf4d3481543621d645ab87fd09538b',
    dir: 'espeak-ng-data',
    shared: [
      { path: 'phontab', bytes: 55796, sha256: '886f3fa402cb0ba73d483aa8ad000af47a6b7cc06293c75a97913fba68a530f6' },
      { path: 'phonindex', bytes: 39074, sha256: '3ca7b8fa3b42624e4b0f152707e7a39245fce569aa99ea47c055d9e622fcf0c4' },
      { path: 'phondata', bytes: 550424, sha256: '4e0288957874029a8c3c9f41a8f517ad4bf18127046decbdd4b9d1d6807ce3a3' },
      { path: 'intonations', bytes: 2040, sha256: '3f8af65fd3eda9759a10f021d61361c120871f463515229c925995c7f90918cc' },
    ],
  },
  voices: [
    {
      language: 'sl',
      id: 'sl_SI-artur-medium',
      name: 'Artur',
      quality: 'medium',
      ...PIPER,
      dir: 'sl/sl_SI/artur/medium',
      model: { bytes: 63200492, sha256: '9222ed93ef425524ad4be0b083369af8ea8db18455576a6016b154192f4ed38c' },
      config: { bytes: 4970, sha256: '741283430f1fa2be5c61717c6f1fe795a7b9f537491927340dd12f90f3b3cc04' },
      card: { bytes: 329, sha256: 'fe8431d0830a41260379ba7f8813e3dfb515c02684a0663702f45e047e31bba9' },
      converted: { bytes: 63200622, sha256: '7e0c703d8cd2577e669e1dcb95d1cf47b06a8abfdd08c50890508d69f6528c70' },
      espeak: [
        { path: 'sl_dict', bytes: 45047, sha256: '9cb2bcbf616a5e7dd7ca769e7d222a48c613299ef1d76e44c08b002f8c22861a' },
        { path: 'lang/zls/sl', bytes: 43, sha256: 'e1c2bd1f75c0eeb86f64182472d5f597ce23e64ad7478c248c2f75b694655942' },
      ],
      licence: 'CC BY 4.0',
      attribution: 'Piper voice sl_SI-artur-medium, trained by ppisljar on the ARTUR studio TTS dataset (https://huggingface.co/datasets/ppisljar/artur_studio_tts/)',
    },
    {
      language: 'it',
      id: 'it_IT-paola-medium',
      name: 'Paola',
      quality: 'medium',
      ...PIPER,
      dir: 'it/it_IT/paola/medium',
      model: { bytes: 63511038, sha256: '6fc918b5a0ea6137382833dddfa567bffbe6a5060c02043c87192ee59c04210c' },
      config: { bytes: 7099, sha256: 'aea19c0a7fce29fbc359b93f10e7902854401e4c95ae2ea328ae516b15d296cf' },
      card: { bytes: 303, sha256: 'c53fd0070dc93b438edbce06728eb83759ba030fff0cce523209ee4a0a6363c0' },
      converted: { bytes: 63511166, sha256: 'cbe315465f8a0be7db8b40f922fe906cf202996b0e15d388c540e9ac3d4b7fd7' },
      espeak: [
        { path: 'it_dict', bytes: 152889, sha256: '111f968359c9ebe36b107a2565ba9f258bab337d3cbe37e44871980a8f21ce6b' },
        { path: 'lang/roa/it', bytes: 109, sha256: '0d9069eb9a96db1c55c131b2bb7d1f5255c68fbddc2199ffd3295b52519a3256' },
      ],
      licence: 'CC0 1.0',
      attribution: 'Piper voice it_IT-paola-medium, trained on the dataset paolapersico1/Voice-Dataset-Italian',
    },
    {
      language: 'de',
      id: 'de_DE-thorsten-medium',
      name: 'Thorsten',
      quality: 'medium',
      ...PIPER,
      dir: 'de/de_DE/thorsten/medium',
      model: { bytes: 63201294, sha256: '7e64762d8e5118bb578f2eea6207e1a35a8e0c30595010b666f983fc87bb7819' },
      config: { bytes: 4819, sha256: '974adee790533adb273a1ac88f49027d2a1b8f0f2cf4905954a4791e79264e85' },
      card: { bytes: 285, sha256: '5196b5ab0794e6056263a1f37c18bec407b61ac187529bee29d1c366871e5c9e' },
      converted: { bytes: 63201421, sha256: '1c305abe13dda2f50a9783da3e7645396833cdf2d09f24fa9a823c91293d65a6' },
      espeak: [
        { path: 'de_dict', bytes: 68276, sha256: 'e9ac048df5cde03b74907d591c7b29b45ee9ec5fcd4592b0b894f0083572fcfd' },
        { path: 'lang/gmw/de', bytes: 42, sha256: 'f3cca92f94b70f8c25a29ee0a4c9ce4c7f1022241532e0647fa2b7f698bf104e' },
      ],
      licence: 'CC0',
      attribution: 'Piper voice de_DE-thorsten-medium, trained on Thorsten-Voice (https://github.com/thorstenMueller/Thorsten-Voice)',
    },
    {
      language: 'en',
      id: 'en_GB-northern_english_male-medium',
      name: 'Northern English male',
      quality: 'medium',
      ...PIPER,
      dir: 'en/en_GB/northern_english_male/medium',
      model: { bytes: 63201294, sha256: '57a219ae8e638873db7d18893304be5069c42868f392bb95c3ff17f0690d0689' },
      config: { bytes: 4847, sha256: '69557ed3d974463453e9b0c09dd99a7ed0e52b8b87b64b357dbeeb2540a97d47' },
      card: { bytes: 305, sha256: '75661c3a50c0ba82b32b3a438e54103d8dfe5bbe8fbb864ac687a040e79948bb' },
      converted: { bytes: 63201430, sha256: 'd23e7891af7062eb188283dba94866e25ffd5b01a0d9fb9a23c71a39b75b2308' },
      espeak: [
        { path: 'en_dict', bytes: 166944, sha256: '71bd330ba8a2e3e8076e631508208ef49449d6147c17b7bd2b4b1e1468292e35' },
        { path: 'lang/gmw/en-GB-x-rp', bytes: 249, sha256: 'd0625af7f58561b1b8cf96fd7f93eee6553bcb3eadb9020ae0757bf96e5115e5' },
        { path: 'lang/gmw/en', bytes: 140, sha256: '4605d5330801de3641c6e366d15f129ea1f5ffbce8722642aba01ace07ab9c83' },
      ],
      licence: 'CC BY-SA 4.0',
      attribution: 'Piper voice en_GB-northern_english_male-medium, trained on OpenSLR 83 (http://www.openslr.org/83/)',
    },
  ],
}

/** Where the offline voices are kept: $LANI_PIPER_CACHE, else ~/.cache/lani/piper (one directory per voice). */
export function piperCacheDir(env: Record<string, string | undefined> = process.env): string {
  if (env.LANI_PIPER_CACHE) return resolve(env.LANI_PIPER_CACHE)
  return join(laniDir(env.XDG_CACHE_HOME || join(homedir(), '.cache')), 'piper')
}

/** Where the voices come from: $LANI_PIPER_URL (an https:// base, or a local directory of the same layout), else Hugging Face. */
export const piperSource = (env: Record<string, string | undefined> = process.env) => env.LANI_PIPER_URL?.trim() || 'https://huggingface.co'

export type Catalog = { espeak: Espeak; voices: PiperVoice[]; error?: string }

/**
 * The catalog: the built-in one, or [file]'s (LANI_PIPER_CATALOG), read again when it changes. A file that isn't a
 * catalog leaves no voices at all (logged), never the built-in ones: a test's bridge must not reach Hugging Face.
 */
export function catalogFrom(file: string | undefined, log: Log): () => Catalog {
  if (!file) return () => BUILTIN_CATALOG
  let had: { stamp: string; catalog: Catalog } | undefined
  return () => {
    let stamp = 'missing'
    try {
      const st = statSync(file)
      stamp = `${st.mtimeMs}:${st.size}`
    } catch {}
    if (had?.stamp === stamp) return had.catalog
    let catalog: Catalog
    try {
      const r = piperCatalog.safeParse(JSON.parse(readFileSync(file, 'utf8')))
      catalog = r.success ? r.data : { ...BUILTIN_CATALOG, voices: [], error: `${file}: ${z.prettifyError(r.error)}` }
    } catch (e) {
      catalog = { ...BUILTIN_CATALOG, voices: [], error: `${file}: ${(e as Error).message}` }
    }
    if (catalog.error) log(`offline voice: LANI_PIPER_CATALOG isn't usable, no voices: ${catalog.error}`)
    had = { stamp, catalog }
    return catalog
  }
}

// --- the conversion (what sherpa-onnx's scripts/vits-piper.py does) -------------------------------------------------

/** What of a Piper voice's <id>.onnx.json the conversion and the app need. */
export const piperConfig = z.looseObject({
  audio: z.looseObject({ sample_rate: z.number().int().positive() }),
  espeak: z.looseObject({ voice: z.string().min(1) }),
  language: z.looseObject({ name_english: z.string().min(1) }),
  num_speakers: z.number().int().positive(),
  inference: z.looseObject({ noise_scale: z.number(), length_scale: z.number(), noise_w: z.number() }),
  phoneme_id_map: z.record(z.string(), z.array(z.number().int().nonnegative()).min(1)),
})
export type PiperConfig = z.infer<typeof piperConfig>

/** The metadata sherpa-onnx's VITS engine reads from a Piper model, in the order scripts/vits-piper.py adds it. */
export function piperMetadata(c: PiperConfig): [string, string][] {
  return [
    ['model_type', 'vits'],
    ['comment', 'piper'],
    ['language', c.language.name_english],
    ['voice', c.espeak.voice],
    ['has_espeak', '1'],
    ['n_speakers', String(c.num_speakers)],
    ['sample_rate', String(c.audio.sample_rate)],
  ]
}

function varint(n: number): number[] {
  const out: number[] = []
  while (n > 0x7f) {
    out.push((n & 0x7f) | 0x80)
    n >>>= 7
  }
  out.push(n)
  return out
}

/** [entries] as ONNX ModelProto.metadata_props (field 14), each a StringStringEntryProto {key: 1, value: 2}. */
export function onnxMetadata(entries: [string, string][]): Uint8Array {
  const enc = new TextEncoder()
  const out: number[] = []
  for (const [k, v] of entries) {
    const key = enc.encode(k)
    const value = enc.encode(v)
    const inner = [0x0a, ...varint(key.length), ...key, 0x12, ...varint(value.length), ...value]
    out.push(0x72, ...varint(inner.length), ...inner)
  }
  return new Uint8Array(out)
}

/**
 * [model] with [entries] as its metadata: protobuf merges a repeated field wherever it is, and onnx.save writes the
 * fields in their order, so a model without fields after 14 (Piper's exports) comes out of onnx.load, metadata_props.add()
 * and onnx.save as its own bytes followed by these entries. The catalog's converted pin checks it.
 */
export function appendOnnxMetadata(model: Uint8Array, entries: [string, string][]): Uint8Array {
  const meta = onnxMetadata(entries)
  const out = new Uint8Array(model.length + meta.length)
  out.set(model)
  out.set(meta, model.length)
  return out
}

/**
 * tokens.txt: a line "<symbol> <id>" per entry of the phoneme map (the space's line is "  3"). sherpa-onnx builds a map
 * of it, so the order doesn't matter; it is the map's order as JSON.parse keeps it.
 */
export const tokensTxt = (c: PiperConfig) =>
  Object.entries(c.phoneme_id_map)
    .map(([symbol, ids]) => `${symbol} ${ids[0]}\n`)
    .join('')

// --- the cache --------------------------------------------------------------------------------------------------------

export type ServedFile = { path: string; bytes: number; sha256: string }

/** <cache>/<id>/voice.json, written last: a voice counts as fetched only when it is there and its files have their sizes. */
const voiceJson = z.object({
  id: z.string(),
  language: z.string(),
  /** What the catalog pinned when it was fetched (pinsOf): another pin, and the voice is fetched again. */
  pins: sha,
  files: z.array(pathPin),
  inference: z.object({ noise_scale: z.number(), length_scale: z.number(), noise_w: z.number() }),
  sample_rate: z.number().int().positive(),
  espeak: z.string(),
  licence: z.string(),
  attribution: z.string(),
  fetched_at: z.string(),
})
type VoiceInfo = z.infer<typeof voiceJson>

/** The served files' total before a fetch: tokens.txt is a line per phoneme, a kilobyte or so. */
export const TOKENS_ESTIMATE = 1_000
/** The metadata's size before the config is read (about 130 bytes; the catalog's converted pin says it exactly). */
const METADATA_ESTIMATE = 130
/** A download that gets no bytes for this long is given up (the next POST tries again). */
const IDLE_MS = 60_000
/** A .part file nobody wrote to for this long was left by a bridge that stopped; another bridge's download writes to its own. */
const STALE_PART_MS = 10 * 60_000

/** The served name of the voice's espeak-ng files: sherpa-onnx is given the directory espeak-ng-data. */
const ESPEAK_DIR = 'espeak-ng-data'

const hex = (b: Uint8Array) => createHash('sha256').update(b).digest('hex')
const mb = (n: number) => `${(n / 1024 / 1024).toFixed(1)} MB`
const sum = (files: { bytes: number }[]) => files.reduce((n, f) => n + f.bytes, 0)

/** One fingerprint of everything [v] is pinned to (not its texts: a licence line fixed doesn't fetch 63 MB again). */
function pinsOf(v: PiperVoice, e: Espeak): string {
  const files = [...e.shared, ...v.espeak].map(f => `${f.path} ${f.sha256}`)
  return hex(new TextEncoder().encode([v.id, v.repo, v.revision, v.dir, v.model.sha256, v.config.sha256, v.card.sha256, v.converted?.sha256 ?? '', e.repo, e.revision, e.dir, ...files].join('\n')))
}

/** A file of a repository at a revision. */
type Remote = { repo: string; revision: string; path: string }

/**
 * The bytes of [r] at [source]: a URL (…/<repo>/resolve/<revision>/<path>, redirects followed: Hugging Face answers with
 * its CDN; given up when no bytes come for [idleMs]) or a local directory of the same layout.
 */
async function* chunks(source: string, r: Remote, idleMs: number): AsyncGenerator<Uint8Array> {
  if (!/^https?:\/\//i.test(source)) {
    const root = source.startsWith('file://') ? fileURLToPath(source) : source
    const p = join(root, r.repo, 'resolve', r.revision, r.path)
    if (!existsSync(p)) throw new Error(`${r.path}: not in ${root}`)
    for await (const c of Bun.file(p).stream()) yield c
    return
  }
  const ctrl = new AbortController()
  let timer: ReturnType<typeof setTimeout> | undefined
  const idle = () => {
    clearTimeout(timer)
    timer = setTimeout(() => ctrl.abort(), idleMs)
  }
  idle()
  try {
    const res = await fetch(`${source.replace(/\/+$/, '')}/${r.repo}/resolve/${r.revision}/${r.path}`, { signal: ctrl.signal, redirect: 'follow' })
    if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`)
    for await (const c of res.body) {
      idle()
      yield c
    }
  } catch (e) {
    throw new Error(`${r.path}: ${ctrl.signal.aborted ? `no bytes for ${idleMs >= 1000 ? `${Math.round(idleMs / 1000)} s` : `${idleMs} ms`}` : (e as Error).message}`)
  } finally {
    clearTimeout(timer)
  }
}

async function writeAll(fh: FileHandle, b: Uint8Array) {
  for (let at = 0; at < b.length; ) {
    const { bytesWritten } = await fh.write(b, at, b.length - at)
    if (!bytesWritten) throw new Error('nothing written')
    at += bytesWritten
  }
}

/** Something to write after the download's own bytes (the model's metadata), and the pin of the result, if any. */
type Append = { bytes: Uint8Array; pin?: { bytes: number; sha256: string } }

/**
 * [to], when it is there already and checked: its bytes are the pin's (then [append]'s), so a retry or another voice's
 * fetch doesn't download it again. Read once, hashed as it is read.
 */
async function kept(to: string, p: z.infer<typeof pin>, append?: Append): Promise<ServedFile | null> {
  const size = p.bytes + (append?.bytes.length ?? 0)
  if (!existsSync(to) || statSync(to).size !== size || (append?.pin && append.pin.bytes !== size)) return null
  const head = createHash('sha256')
  const all = createHash('sha256')
  let at = 0
  for await (const c of Bun.file(to).stream()) {
    all.update(c)
    if (at < p.bytes) head.update(at + c.length <= p.bytes ? c : c.subarray(0, p.bytes - at))
    at += c.length
  }
  if (head.digest('hex') !== p.sha256) return null
  if (append && !Buffer.from(await Bun.file(to).slice(p.bytes).arrayBuffer()).equals(append.bytes)) return null
  const sha256 = all.digest('hex')
  if (append?.pin && append.pin.sha256 !== sha256) return null
  return { path: to, bytes: size, sha256 }
}

/**
 * Downloads [r] into [to]: streamed into a .part file of this process, hashed as it comes, renamed into place only once
 * its size and sha256 are the pin's (and, with [append], the result's are its pin's). [progress] hears every chunk.
 */
async function save(source: string, r: Remote, to: string, p: z.infer<typeof pin>, idleMs: number, progress: (n: number) => void, append?: Append): Promise<ServedFile> {
  mkdirSync(dirname(to), { recursive: true })
  const part = `${to}.${process.pid}.part`
  const fh = await open(part, 'w')
  let done = false
  try {
    const raw = createHash('sha256')
    const all = createHash('sha256')
    let n = 0
    for await (const c of chunks(source, r, idleMs)) {
      n += c.length
      if (n > p.bytes) throw new Error(`${r.path}: more than the ${p.bytes} bytes of its pin`)
      raw.update(c)
      all.update(c)
      await writeAll(fh, c)
      progress(c.length)
    }
    if (n !== p.bytes) throw new Error(`${r.path}: ${n} bytes, want ${p.bytes}`)
    const got = raw.digest('hex')
    if (got !== p.sha256) throw new Error(`${r.path}: checksum mismatch (sha256 ${got.slice(0, 12)}…, want ${p.sha256.slice(0, 12)}…)`)
    let bytes = n
    if (append) {
      await writeAll(fh, append.bytes)
      all.update(append.bytes)
      bytes += append.bytes.length
    }
    const sha256 = all.digest('hex')
    if (append?.pin && (append.pin.bytes !== bytes || append.pin.sha256 !== sha256)) {
      throw new Error(`${r.path} converted: checksum mismatch (${bytes} bytes, sha256 ${sha256.slice(0, 12)}…; want ${append.pin.bytes}, ${append.pin.sha256.slice(0, 12)}…)`)
    }
    await fh.close()
    renameSync(part, to)
    done = true
    return { path: to, bytes, sha256 }
  } finally {
    if (!done) {
      await fh.close().catch(() => {})
      rmSync(part, { force: true })
    }
  }
}

/** [data] into [to] through a .part file: never half a file under its name. */
function writeAtomic(to: string, data: string | Uint8Array) {
  const part = `${to}.${process.pid}.part`
  writeFileSync(part, data)
  renameSync(part, to)
}

/** The .part files under [dir] a stopped bridge left behind. */
function sweep(dir: string) {
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, e.name)
    if (e.isDirectory()) sweep(p)
    else if (e.name.endsWith('.part') && Date.now() - statSync(p).mtimeMs > STALE_PART_MS) rmSync(p, { force: true })
  }
}

// --- the voices -------------------------------------------------------------------------------------------------------

export type OfflineVoiceStatus = 'missing' | 'fetching' | 'ready' | 'failed'

/** What GET /voice/offline says of a voice. */
export type OfflineVoiceEntry = {
  language: string
  id: string
  name: string
  quality: string
  /** The served files' total: exact once ready, else estimated from the pins. */
  bytes: number
  status: OfflineVoiceStatus
  /** While fetching: the bytes downloaded (or found checked in the cache) of all to download. */
  progress?: { done: number; total: number }
  /** Why the last fetch failed. */
  error?: string
  licence: string
  attribution: string
  /** The voice's page on Hugging Face, at the pinned revision. */
  source: string
  /** Ready only: what the app downloads (GET /voice/offline/<language>/<path>) and checks. */
  files?: { path: string; bytes: number; sha256: string }[]
  /** Ready only: what the app passes to sherpa-onnx (its noise scale, length scale and noise width; the espeak voice). */
  inference?: { noise_scale: number; length_scale: number; noise_w: number }
  sample_rate?: number
  espeak?: string
}

/** [fetched]: the bytes downloaded (not found checked in the cache), for the log. */
type Fetching = { status: 'fetching'; pins: string; done: number; total: number; fetched: number }
type State = Fetching | { status: 'ready'; pins: string; info: VoiceInfo } | { status: 'failed'; pins: string; error: string }

export class OfflineVoices {
  /** What this bridge knows of each voice (by id) beyond the disk: its fetch, its failure, its voice.json. */
  private states = new Map<string, State>()

  /** [idleMs]: how long a download may get no bytes (60 s; a test's less). */
  constructor(private o: { cacheDir: string; source: string; catalog: () => Catalog; log: Log; idleMs?: number }) {}

  /** Every voice of the catalog, and the catalog's error when LANI_PIPER_CATALOG isn't usable. */
  list(): { voices: OfflineVoiceEntry[]; error?: string } {
    const c = this.o.catalog()
    return { voices: c.voices.map(v => this.entryOf(v, c.espeak)), ...(c.error ? { error: c.error } : {}) }
  }

  /** The voice of [language], or null when the catalog has none. */
  entry(language: string): OfflineVoiceEntry | null {
    const c = this.o.catalog()
    const v = c.voices.find(x => x.language === language)
    return v ? this.entryOf(v, c.espeak) : null
  }

  /** Starts fetching the voice of [language] unless it is ready or being fetched (a second ask joins the first). */
  start(language: string): OfflineVoiceEntry | null {
    const c = this.o.catalog()
    const v = c.voices.find(x => x.language === language)
    if (!v) return null
    const e = c.espeak
    const s = this.stateOf(v, e)
    if (s?.status === 'ready' || s?.status === 'fetching') return this.entryOf(v, e)
    const fetching: Fetching = { status: 'fetching', pins: pinsOf(v, e), done: 0, total: v.model.bytes + v.config.bytes + v.card.bytes + sum([...e.shared, ...v.espeak]), fetched: 0 }
    this.states.set(v.id, fetching)
    const started = Date.now()
    const secs = () => ((Date.now() - started) / 1000).toFixed(1)
    this.o.log(`offline voice ${v.id}: fetching ${mb(fetching.total)} from ${this.o.source} into ${this.dir(v)}`)
    this.build(v, e, fetching).then(
      info => {
        this.states.set(v.id, { status: 'ready', pins: fetching.pins, info })
        this.o.log(`offline voice ${v.id}: ready, ${mb(sum(info.files))} in ${info.files.length} files (${mb(fetching.fetched)} downloaded), after ${secs()} s`)
      },
      err => {
        const error = (err as Error).message
        this.states.set(v.id, { status: 'failed', pins: fetching.pins, error })
        this.o.log(`offline voice ${v.id}: fetch failed after ${secs()} s: ${error}`)
      },
    )
    return this.entryOf(v, e)
  }

  /** A file of the ready voice of [language], when [path] is exactly one of its files (never a path made of the input). */
  file(language: string, path: string): { path: string; bytes: number } | null {
    const c = this.o.catalog()
    const v = c.voices.find(x => x.language === language)
    const s = v && this.stateOf(v, c.espeak)
    if (!v || s?.status !== 'ready') return null
    const f = s.info.files.find(x => x.path === path)
    if (!f) return null
    const p = join(this.dir(v), f.path)
    // the cache emptied under a running bridge: the voice is looked for on disk again
    if (!existsSync(p) || statSync(p).size !== f.bytes) {
      this.states.delete(v.id)
      return null
    }
    return { path: p, bytes: f.bytes }
  }

  private dir(v: PiperVoice) {
    return join(this.o.cacheDir, v.id)
  }

  /**
   * What is known of [v]: its fetch, else its voice.json (fetched before this start, or by another learner's bridge
   * sharing the cache: every listed file there with its size, no re-hash), else its last failure; undefined: missing.
   */
  private stateOf(v: PiperVoice, e: Espeak): State | undefined {
    const pins = pinsOf(v, e)
    const s = this.states.get(v.id)
    if (s?.status === 'fetching' || (s?.status === 'ready' && s.pins === pins)) return s
    const info = this.onDisk(v, pins)
    if (info) {
      const ready: State = { status: 'ready', pins, info }
      this.states.set(v.id, ready)
      return ready
    }
    return s?.status === 'failed' && s.pins === pins ? s : undefined
  }

  private onDisk(v: PiperVoice, pins: string): VoiceInfo | null {
    const dir = this.dir(v)
    const file = join(dir, 'voice.json')
    if (!existsSync(file)) return null
    try {
      const info = voiceJson.parse(JSON.parse(readFileSync(file, 'utf8')))
      if (info.id !== v.id || info.pins !== pins) return null
      return info.files.every(f => {
        const p = join(dir, f.path)
        return existsSync(p) && statSync(p).size === f.bytes
      })
        ? info
        : null
    } catch {
      return null
    }
  }

  private entryOf(v: PiperVoice, e: Espeak): OfflineVoiceEntry {
    const s = this.stateOf(v, e)
    const estimate = (v.converted?.bytes ?? v.model.bytes + METADATA_ESTIMATE) + TOKENS_ESTIMATE + v.card.bytes + sum([...e.shared, ...v.espeak])
    const info = s?.status === 'ready' ? s.info : undefined
    return {
      language: v.language,
      id: v.id,
      name: v.name,
      quality: v.quality,
      bytes: info ? sum(info.files) : estimate,
      status: s?.status ?? 'missing',
      ...(s?.status === 'fetching' ? { progress: { done: s.done, total: s.total } } : {}),
      ...(s?.status === 'failed' ? { error: s.error } : {}),
      licence: v.licence,
      attribution: v.attribution,
      source: `https://huggingface.co/${v.repo}/tree/${v.revision}/${v.dir}`,
      ...(info
        ? { files: info.files.map(f => ({ path: f.path, bytes: f.bytes, sha256: f.sha256 })), inference: info.inference, sample_rate: info.sample_rate, espeak: info.espeak }
        : {}),
    }
  }

  /**
   * Fetches [v] into its directory: the config first (the model's metadata and tokens.txt are made of it), the card and
   * the espeak-ng files, the model (with its metadata appended as it is saved), tokens.txt, and voice.json last. A file
   * already there and checked is kept: a retry fetches only what is missing.
   */
  private async build(v: PiperVoice, e: Espeak, st: Fetching): Promise<VoiceInfo> {
    const dir = this.dir(v)
    mkdirSync(dir, { recursive: true })
    sweep(dir)
    const progress = (n: number) => void (st.done += n)
    const get = async (r: Remote, name: string, p: z.infer<typeof pin>, append?: Append): Promise<ServedFile> => {
      const to = join(dir, name)
      const had = await kept(to, p, append)
      if (had) progress(p.bytes)
      const f = had ?? (await save(this.o.source, r, to, p, this.o.idleMs ?? IDLE_MS, progress, append))
      if (!had) st.fetched += p.bytes
      return { path: name, bytes: f.bytes, sha256: f.sha256 }
    }
    const ofVoice = (file: string): Remote => ({ repo: v.repo, revision: v.revision, path: `${v.dir}/${file}` })

    // the config isn't served: the app gets what it needs of it in the entry
    const configName = `${v.id}.onnx.json`
    await get(ofVoice(configName), configName, v.config)
    let config: PiperConfig
    try {
      const r = piperConfig.safeParse(JSON.parse(readFileSync(join(dir, configName), 'utf8')))
      if (!r.success) throw new Error(z.prettifyError(r.error))
      config = r.data
    } catch (err) {
      throw new Error(`${configName}: not a Piper config: ${(err as Error).message}`)
    }
    const card = await get(ofVoice('MODEL_CARD'), 'MODEL_CARD', v.card)
    const espeak: ServedFile[] = []
    for (const f of [...e.shared, ...v.espeak]) espeak.push(await get({ repo: e.repo, revision: e.revision, path: `${e.dir}/${f.path}` }, `${ESPEAK_DIR}/${f.path}`, f))
    const model = await get(ofVoice(`${v.id}.onnx`), 'model.onnx', v.model, { bytes: onnxMetadata(piperMetadata(config)), pin: v.converted })
    const tokens = new TextEncoder().encode(tokensTxt(config))
    writeAtomic(join(dir, 'tokens.txt'), tokens)

    const info: VoiceInfo = {
      id: v.id,
      language: v.language,
      pins: st.pins,
      files: [model, { path: 'tokens.txt', bytes: tokens.length, sha256: hex(tokens) }, card, ...espeak],
      inference: { noise_scale: config.inference.noise_scale, length_scale: config.inference.length_scale, noise_w: config.inference.noise_w },
      sample_rate: config.audio.sample_rate,
      espeak: config.espeak.voice,
      licence: v.licence,
      attribution: v.attribution,
      fetched_at: new Date().toISOString(),
    }
    writeAtomic(join(dir, 'voice.json'), JSON.stringify(info, null, 2) + '\n')
    return info
  }
}
