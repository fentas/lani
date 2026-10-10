// Voice prebuild (companion/bin/voice-build): what is voiced, what is missing, and generating the
// missing clips in priority order within the ElevenLabs quota guard, with the local worker for the rest.
import { isNarrator } from './cast'
import { normalizeText } from './family'
import { PRIORITY_LABELS, type CorpusItem, type ElevenLabs, type EngineName, type Gepard, type VoiceStore } from './voice'

export { PRIORITY_LABELS }

type Count = { texts: number; chars: number }

export type BuildPlan = {
  texts: number
  chars: number
  cached: number
  by_engine: Record<string, number>
  missing: number
  missing_chars: number
  rows: { label: string; texts: number; chars: number; cached: number; missing: number; missing_chars: number }[]
  /** Per speaker: the corpus texts, and how many have an ElevenLabs clip in that voice. */
  by_voice: Record<string, { texts: number; chars: number; elevenlabs: number }>
  quota: { limit: number; used: number; remaining: number; reserve: number; resets_at?: number } | null
  /** Whether the local worker voices this village (Slovene only), and whether it is up. */
  local: boolean
  gepard_up: boolean
  /** What --run would do now: ElevenLabs within budget (new clips, and replacements with --upgrade/--revoice), then the local worker, then later. */
  would: { elevenlabs: Count & { replace: number; replace_chars: number }; gepard: Count; later: Count }
  /** The short narrator words in the store: in their carrier sentence, and old-style (said plainly: they may sound English). */
  carrier: ReturnType<VoiceStore['carrierStats']>
  /** --revoice-words: the old-style ones it would re-voice now within the budget (and --max), and those left for later. */
  revoice_words?: { texts: number; chars: number; later: Count }
}

export type BuildOptions = {
  maxChars?: number
  /** Also replace local (Gepard) clips with ElevenLabs ones. */
  upgrade?: boolean
  /** Speakers whose clips are made again with ElevenLabs, whatever made them (their voice was swapped). */
  revoice?: string[]
  /** --revoice-words: the old-style short narrator words again in their carrier sentence, at most [max]. */
  revoiceWords?: boolean
  max?: number
}

/** The ElevenLabs budget for a prebuild: what is left above the reserve, capped by --max-chars. */
export function budget(q: BuildPlan['quota'], maxChars?: number): number {
  const b = q ? Math.max(0, q.remaining - q.reserve) : 0
  return maxChars !== undefined ? Math.min(b, maxChars) : b
}

/**
 * Todo items for --run: missing ones, plus local clips to replace with [upgrade] (not a narrator's whose lines come from
 * the local voice: a Slovene village's), plus every clip of a [revoice] speaker.
 */
export function todo(items: CorpusItem[], store: VoiceStore, upgrade = false, revoice: string[] = []): CorpusItem[] {
  return items.filter(i => {
    const c = store.get(normalizeText(i.text), i.voice)
    return !c || (upgrade && c.engine !== 'elevenlabs' && !store.localNarrator(i.voice)) || revoice.includes(i.voice)
  })
}

export async function plan(items: CorpusItem[], store: VoiceStore, eleven: ElevenLabs, gepard: Gepard, o: BuildOptions = {}): Promise<BuildPlan> {
  const rows = PRIORITY_LABELS.map(label => ({ label, texts: 0, chars: 0, cached: 0, missing: 0, missing_chars: 0 }))
  const by_engine: Record<string, number> = {}
  const by_voice: BuildPlan['by_voice'] = {}
  for (const i of items) {
    const r = rows[i.priority]
    r.texts++
    r.chars += i.text.length
    const c = store.get(normalizeText(i.text), i.voice)
    const v = (by_voice[i.voice] ??= { texts: 0, chars: 0, elevenlabs: 0 })
    v.texts++
    v.chars += i.text.length
    if (c?.engine === 'elevenlabs') v.elevenlabs++
    if (c) {
      r.cached++
      by_engine[c.engine] = (by_engine[c.engine] ?? 0) + 1
    } else {
      r.missing++
      r.missing_chars += i.text.length
    }
  }
  const s = await eleven.subscription()
  const quota = s ? { limit: s.limit, used: s.used, remaining: Math.max(0, s.limit - s.used), reserve: eleven.reserve, resets_at: s.resets_at } : null
  // only the engines the store voices with, as --run: a village in another language has no local worker (it speaks
  // Slovene only), and none at all while its voice cast has no voices
  const uses = (name: EngineName) => store.engines.some(e => e.name === name)
  const worker = uses('gepard')
  const gepard_up = worker && (await gepard.up())
  let left = uses('elevenlabs') ? budget(quota, o.maxChars) : 0
  const would = { elevenlabs: { texts: 0, chars: 0, replace: 0, replace_chars: 0 }, gepard: { texts: 0, chars: 0 }, later: { texts: 0, chars: 0 } }
  // --revoice-words --run does only that, within the same budget
  let revoice_words: BuildPlan['revoice_words']
  if (o.revoiceWords) {
    revoice_words = { texts: 0, chars: 0, later: { texts: 0, chars: 0 } }
    let words = left
    for (const c of store.revoiceQueue(false)) {
      const n = store.cost(c.text, c.voice)
      const now = n <= words && (o.max === undefined || revoice_words.texts < o.max)
      if (now) words -= n
      const to = now ? revoice_words : revoice_words.later
      to.texts++
      to.chars += n
    }
  }
  for (const i of todo(items, store, o.upgrade, o.revoice)) {
    const n = i.text.length
    // a narrator whose lines come from the local voice (a Slovene village): the worker, else a native voice the cast may
    // have, never the English narrators
    if (store.localNarrator(i.voice)) {
      const to = gepard_up ? 'gepard' : store.nativeOf(i.voice) && n <= left ? 'elevenlabs' : 'later'
      if (to === 'elevenlabs') left -= n
      would[to].texts++
      would[to].chars += n
      continue
    }
    const cost = eleven.cost(i.text, i.voice) // a short narrator word: its carrier sentence
    const norm = normalizeText(i.text)
    const had = store.get(norm, i.voice) // a replacement: it has a clip already
    const local = !had && gepard_up && !store.waitsForElevenLabs(norm, i.voice)
    const to = cost <= left ? 'elevenlabs' : local ? 'gepard' : 'later'
    const spent = to === 'elevenlabs' ? cost : n
    if (to === 'elevenlabs') {
      left -= cost
      if (had) {
        would.elevenlabs.replace++
        would.elevenlabs.replace_chars += cost
      }
    }
    would[to].texts++
    would[to].chars += spent
  }
  const sum = (k: 'texts' | 'chars' | 'cached' | 'missing' | 'missing_chars') => rows.reduce((n, r) => n + r[k], 0)
  return {
    texts: sum('texts'), chars: sum('chars'), cached: sum('cached'), by_engine, missing: sum('missing'), missing_chars: sum('missing_chars'), rows, by_voice, quota, local: worker, gepard_up, would,
    carrier: store.carrierStats(), ...(revoice_words ? { revoice_words } : {}),
  }
}

const n = (x: number) => x.toLocaleString('en-US')

export function formatPlan(p: BuildPlan, dir: string): string {
  const pad = (s: string | number, w: number) => String(s).padStart(w)
  const characters = Object.entries(p.by_voice).filter(([v]) => !isNarrator(v))
  const lines = [
    `Voice store: ${dir}`,
    `Corpus: ${n(p.texts)} texts, ${n(p.chars)} characters`,
    '',
    `  ${'source'.padEnd(11)}${pad('texts', 7)}${pad('chars', 8)}${pad('cached', 8)}${pad('missing', 9)}${pad('missing chars', 15)}`,
    ...p.rows.map(r => `  ${r.label.padEnd(11)}${pad(n(r.texts), 7)}${pad(n(r.chars), 8)}${pad(n(r.cached), 8)}${pad(n(r.missing), 9)}${pad(n(r.missing_chars), 15)}`),
    '',
    `Cached: ${n(p.cached)}${Object.keys(p.by_engine).length ? ` (${Object.entries(p.by_engine).map(([e, c]) => `${e} ${n(c)}`).join(', ')})` : ''}`,
    `Missing: ${n(p.missing)} texts, ${n(p.missing_chars)} characters`,
    ...(characters.length
      ? [`Character voices (texts with an ElevenLabs clip / all): ${characters.map(([v, c]) => `${v} ${n(c.elevenlabs)}/${n(c.texts)}`).join(', ')}`]
      : []),
    p.quota
      ? `ElevenLabs: ${n(p.quota.remaining)} of ${n(p.quota.limit)} characters left this period, ${n(p.quota.reserve)} kept for live lines` +
        (p.quota.resets_at ? `, resets ${new Date(p.quota.resets_at * 1000).toISOString().slice(0, 10)}` : '')
      : 'ElevenLabs: not available (no ELEVENLABS_API_KEY, or the subscription could not be read)',
    `Local Gepard worker: ${!p.local ? 'not for this village (it speaks Slovene only)' : p.gepard_up ? 'up' : 'down'}`,
    `--run would voice: ElevenLabs ${n(p.would.elevenlabs.texts)} texts (${n(p.would.elevenlabs.chars)} chars` +
      (p.would.elevenlabs.replace ? `; ${n(p.would.elevenlabs.replace)} of them replace a clip, ${n(p.would.elevenlabs.replace_chars)} chars` : '') +
      `), Gepard ${n(p.would.gepard.texts)} (${n(p.would.gepard.chars)} chars), later ${n(p.would.later.texts)} (${n(p.would.later.chars)} chars)`,
    ...formatCarrier(p),
  ]
  return lines.join('\n')
}

/** The subscription counts about half a character per character of eleven_v3 text (README, "Costs"). */
const credits = (chars: number) => Math.round(chars / 2)

/** The short narrator words: said in their carrier sentence, and the old-style ones and what re-voicing them costs. */
function formatCarrier(p: BuildPlan): string[] {
  const c = p.carrier
  const out = [
    `Short narrator words (1–3 words): ${n(c.carrier)} in their carrier sentence, ${n(c.old)} old-style (said plainly: they may sound English; ` +
      `${n(c.asked)} asked for by the app)${c.plain ? `, ${n(c.plain)} kept plain (the word could not be cut out)` : ''}`,
    `  re-voicing all old-style: ${n(c.old_chars)} characters sent (≈ ${n(credits(c.old_chars))} on the subscription); ` +
      `the bridge re-voices those the app asks for and Jan's re-records (a long press on 🔊), ${n(c.daily)} a day, ${n(c.kept)} of them kept for re-records ` +
      `(${n(c.today)} today, ${n(c.redos)} re-records)` + (c.cut ? '' : '; no ffmpeg here: no carrier sentence'),
  ]
  const w = p.revoice_words
  if (w) {
    out.push(
      `--revoice-words --run would re-voice ${n(w.texts)} (${n(w.chars)} characters, ≈ ${n(credits(w.chars))} on the subscription), ` +
        `later ${n(w.later.texts)} (${n(w.later.chars)} characters)`,
    )
  }
  return out
}

/** Generates the todo items in priority order. Stops when no engine can take anything more. */
export async function run(
  items: CorpusItem[],
  store: VoiceStore,
  o: BuildOptions & { out?: (line: string) => void } = {},
): Promise<{ made: Record<EngineName, number>; replaced: number; chars: number; left: number }> {
  const made: Record<EngineName, number> = { elevenlabs: 0, gepard: 0 }
  let replaced = 0
  const anyReady = async () => {
    for (const e of store.engines) if (await e.ready(1, false)) return true
    return false
  }
  let spent = 0
  const list = todo(items, store, o.upgrade, o.revoice)
  for (const it of list) {
    const norm = normalizeText(it.text)
    const had = store.get(norm, it.voice)
    const overCap = o.maxChars !== undefined && spent + store.cost(it.text, it.voice) > o.maxChars
    let engines: EngineName[] | undefined
    if (had) {
      if (overCap) continue
      // --upgrade: replace a local clip; --revoice: any clip of that speaker (a local narrator's: in the local voice)
      engines = store.localNarrator(it.voice) ? undefined : ['elevenlabs']
    } else if (overCap) {
      if (store.waitsForElevenLabs(norm, it.voice)) continue
      engines = ['gepard']
    }
    const c = await store.voice(it.text, it.voice, it.source, { engines, replace: !!had, force: !!had && !!o.revoice?.includes(it.voice) })
    if (!c || c.file === had?.file && c.created_at === had.created_at) {
      // Nothing took this one; a shorter text may still fit the quota.
      if (!(await anyReady())) break
      continue
    }
    made[c.engine]++
    if (had) replaced++
    if (c.engine === 'elevenlabs') spent += c.chars
    o.out?.(`  ✓ ${c.engine.padEnd(10)} ${c.voice.padEnd(11)} ${c.text}`)
  }
  return { made, replaced, chars: spent, left: list.length - made.elevenlabs - made.gepard }
}
