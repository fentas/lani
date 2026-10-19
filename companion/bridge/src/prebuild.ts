// Voice prebuild (companion/bin/voice-build): what is voiced, what is missing, and generating the
// missing clips in priority order within the ElevenLabs quota guard, with the local worker for the rest. A Slovene
// narrator's line wants its clip in the narrator's Slovene ElevenLabs voice (Nina, Luka) where that comes first, also
// when its local clip is there (which stays, as the fallback).
import { isNarrator, languageVoiceName } from './cast'
import { normalizeText } from './family'
import { creditsFor, PRIORITY_LABELS, type CorpusItem, type ElevenLabs, type EngineName, type Gepard, type VoiceStore } from './voice'

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
  /**
   * Per speaker: the corpus texts, and how many have an ElevenLabs clip in that voice (a Slovene narrator: in its Slovene
   * ElevenLabs voice, and how many a local clip only).
   */
  by_voice: Record<string, { texts: number; chars: number; elevenlabs: number; local?: number }>
  /** The Slovene narrators' ElevenLabs voices (female: Nina), where they come first; empty elsewhere. */
  narrator_voices: Record<string, string>
  quota: { limit: number; used: number; remaining: number; reserve: number; resets_at?: number } | null
  /** Whether the local worker voices this village (Slovene only), and whether it is up. */
  local: boolean
  gepard_up: boolean
  /**
   * What --run would do now: ElevenLabs within budget (new clips, and replacements with --upgrade/--revoice; [narrators]:
   * the Slovene narrators' lines in their ElevenLabs voices, of them [over_local] next to a local clip), then the local
   * worker, then later.
   */
  would: { elevenlabs: Count & { replace: number; replace_chars: number; narrators: Count; over_local: number }; gepard: Count; later: Count }
  /** The short narrator words in the store: in their carrier sentence, and old-style (said plainly: they may sound English). */
  carrier: ReturnType<VoiceStore['carrierStats']>
  /** --revoice-words: the old-style ones it would re-voice now within the budget (and --max), and those left for later. */
  revoice_words?: { texts: number; chars: number; later: Count }
  /**
   * The ElevenLabs model new clips are made with (LANI_ELEVENLABS_MODEL), and the store's ElevenLabs clips by the model
   * that made them (one from before it was recorded: eleven_v3); voice-build --upgrade-model makes the older ones again.
   */
  models: { now: string; by: Record<string, { clips: number; chars: number }> }
  /**
   * The stale clips (with ElevenLabs set up): of a swapped voice and of an older model, all and those the app asked for
   * (the lazy re-voice makes those again, the day's cap), and what making them all again would cost.
   */
  stale?: ReturnType<VoiceStore['staleStats']>
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
 * Todo items for --run: missing ones, plus local clips to replace with [upgrade], plus every clip of a [revoice] speaker.
 * A Slovene narrator's line is to do while its clip in the narrator's Slovene ElevenLabs voice is wanted (it comes first)
 * and missing, its local clip there or not; [upgrade] doesn't touch a Slovene narrator's local clips otherwise.
 */
export function todo(items: CorpusItem[], store: VoiceStore, upgrade = false, revoice: string[] = []): CorpusItem[] {
  return items.filter(i => {
    const norm = normalizeText(i.text)
    if (store.sloveneNarrator(i.voice)) return !store.best(norm, i.voice) || store.wantsElevenLabs(norm, i.voice) || revoice.includes(i.voice)
    const c = store.get(norm, i.voice)
    return !c || (upgrade && c.engine !== 'elevenlabs') || revoice.includes(i.voice)
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
    const norm = normalizeText(i.text)
    const c = store.best(norm, i.voice)
    const v = (by_voice[i.voice] ??= { texts: 0, chars: 0, elevenlabs: 0 })
    v.texts++
    v.chars += i.text.length
    // a Slovene narrator's: in its Slovene ElevenLabs voice, or a local clip only
    const sl = store.slVoiceOf(i.voice)
    if (sl) {
      if (store.get(norm, sl)) v.elevenlabs++
      else if (c) v.local = (v.local ?? 0) + 1
    } else if (c?.engine === 'elevenlabs') v.elevenlabs++
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
  const would = { elevenlabs: { texts: 0, chars: 0, replace: 0, replace_chars: 0, narrators: { texts: 0, chars: 0 }, over_local: 0 }, gepard: { texts: 0, chars: 0 }, later: { texts: 0, chars: 0 } }
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
    // a Slovene narrator's line, never in the English voice: its Slovene ElevenLabs voice first within the budget (a local
    // clip there stays), else the worker for a line without a clip; or ('gepard') the worker first, else its Slovene
    // ElevenLabs voice
    if (store.sloveneNarrator(i.voice)) {
      const norm = normalizeText(i.text)
      const had = store.best(norm, i.voice)
      const sl = store.slVoiceOf(i.voice)
      const cost = sl && uses('elevenlabs') ? eleven.cost(i.text, sl) : Infinity
      const first = store.elevenlabsFirst(i.voice)
      const to = first ? (cost <= left ? 'elevenlabs' : !had && gepard_up ? 'gepard' : 'later') : gepard_up ? 'gepard' : cost <= left ? 'elevenlabs' : 'later'
      if (to === 'elevenlabs') {
        left -= cost
        would.elevenlabs.narrators.texts++
        would.elevenlabs.narrators.chars += cost
        if (had) {
          would.elevenlabs.replace++
          would.elevenlabs.replace_chars += cost
          if (had.engine === 'gepard') would.elevenlabs.over_local++
        }
      }
      would[to].texts++
      would[to].chars += to === 'elevenlabs' ? cost : n
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
  const narrator_voices: Record<string, string> = {}
  for (const v of ['female', 'male']) {
    const sl = store.elevenlabsFirst(v) ? store.slVoiceOf(v) : undefined
    if (sl) narrator_voices[v] = languageVoiceName(sl) ?? sl
  }
  return {
    texts: sum('texts'), chars: sum('chars'), cached: sum('cached'), by_engine, missing: sum('missing'), missing_chars: sum('missing_chars'), rows, by_voice, narrator_voices, quota, local: worker, gepard_up, would,
    carrier: store.carrierStats(), ...(revoice_words ? { revoice_words } : {}), models: { now: eleven.model, by: store.modelStats() },
    ...(eleven.configured ? { stale: store.staleStats() } : {}),
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
    ...(Object.keys(p.narrator_voices).length
      ? [`Narrators in their Slovene ElevenLabs voices (texts in it / all; a local clip only meanwhile): ${Object.entries(p.narrator_voices).map(([v, name]) => `${v} (${name}) ${n(p.by_voice[v]?.elevenlabs ?? 0)}/${n(p.by_voice[v]?.texts ?? 0)} (local ${n(p.by_voice[v]?.local ?? 0)})`).join(', ')}`]
      : []),
    p.quota
      ? `ElevenLabs: ${n(p.quota.remaining)} of ${n(p.quota.limit)} characters left this period, ${n(p.quota.reserve)} kept for live lines` +
        (p.quota.resets_at ? `, resets ${new Date(p.quota.resets_at * 1000).toISOString().slice(0, 10)}` : '')
      : 'ElevenLabs: not available (no ELEVENLABS_API_KEY, or the subscription could not be read)',
    `Local Gepard worker: ${!p.local ? 'not for this village (it speaks Slovene only)' : p.gepard_up ? 'up' : 'down'}`,
    `--run would voice: ElevenLabs ${n(p.would.elevenlabs.texts)} texts (${n(p.would.elevenlabs.chars)} chars` +
      (p.would.elevenlabs.replace ? `; ${n(p.would.elevenlabs.replace)} of them replace a clip, ${n(p.would.elevenlabs.replace_chars)} chars` : '') +
      (p.would.elevenlabs.narrators.texts ? `; the narrators in Slovene ${n(p.would.elevenlabs.narrators.texts)}, ${n(p.would.elevenlabs.narrators.chars)} chars, ${n(p.would.elevenlabs.over_local)} of them next to a local clip, which stays` : '') +
      `), Gepard ${n(p.would.gepard.texts)} (${n(p.would.gepard.chars)} chars), later ${n(p.would.later.texts)} (${n(p.would.later.chars)} chars)`,
    ...formatCarrier(p),
    ...formatModels(p),
  ]
  return lines.join('\n')
}

/** The credits [chars] characters cost on the subscription with the model new clips are made with (MODEL_CREDITS; README, "Costs"). */
const credits = (chars: number, p: BuildPlan) => creditsFor(chars, p.models.now)

/** The store's ElevenLabs clips by model, and the model new ones are made with; the stale clips and their lazy re-voice. */
function formatModels(p: BuildPlan): string[] {
  const older = Object.entries(p.models.by).filter(([m]) => m !== p.models.now)
  const st = p.stale
  return [
    `ElevenLabs model: ${p.models.now} (LANI_ELEVENLABS_MODEL); the clips by model: ${Object.entries(p.models.by).map(([m, x]) => `${m} ${n(x.clips)}`).join(', ') || 'none yet'}` +
      (older.length ? ` (voice-build --upgrade-model makes the ${n(older.reduce((k, [, x]) => k + x.clips, 0))} of an older one again)` : ''),
    ...(st
      ? [
          `Stale clips, made again as the app asks for them (the day's cap, never the reserve): a swapped voice ${n(st.voice.clips)} / asked ${n(st.voice.asked)}; ` +
            `an older model ${n(st.model.clips)} / asked ${n(st.model.asked)}${st.model.lazy ? '' : ' (not lazily: LANI_VOICE_UPGRADE_MODEL_LAZY=off)'}; ` +
            `all of them again: ${n(st.all.chars)} characters, ≈ ${n(st.all.credits)} credits with ${st.model.now}`,
        ]
      : []),
  ]
}

/** The short narrator words: said in their carrier sentence, and the old-style ones and what re-voicing them costs. */
function formatCarrier(p: BuildPlan): string[] {
  const c = p.carrier
  const out = [
    `Short narrator words (1–3 words): ${n(c.carrier)} in their carrier sentence, ${n(c.old)} old-style (said plainly: they may sound English; ` +
      `${n(c.asked)} asked for by the app)${c.plain ? `, ${n(c.plain)} kept plain (the word could not be cut out)` : ''}`,
    `  re-voicing all old-style: ${n(c.old_chars)} characters sent (≈ ${n(credits(c.old_chars, p))} credits); ` +
      `the bridge re-voices those the app asks for, ${n(c.daily)} a day (${n(c.today)} today), and Jan's re-records (a long press on 🔊), ` +
      `${n(c.redo_daily)} a day on their own (${n(c.redos)} today)` + (c.cut ? '' : '; no ffmpeg here: no carrier sentence'),
  ]
  const w = p.revoice_words
  if (w) {
    out.push(
      `--revoice-words --run would re-voice ${n(w.texts)} (${n(w.chars)} characters, ≈ ${n(credits(w.chars, p))} credits), ` +
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
  const made: Record<EngineName, number> = { elevenlabs: 0, gepard: 0, drama3: 0 }
  let replaced = 0
  const anyReady = async () => {
    for (const e of store.engines) if (await e.ready(1, false)) return true
    return false
  }
  let spent = 0
  const list = todo(items, store, o.upgrade, o.revoice)
  for (const it of list) {
    const norm = normalizeText(it.text)
    const narrator = store.sloveneNarrator(it.voice)
    const had = narrator ? store.best(norm, it.voice) : store.get(norm, it.voice)
    const overCap = o.maxChars !== undefined && spent + store.cost(it.text, it.voice) > o.maxChars
    const revoiced = !!had && !!o.revoice?.includes(it.voice)
    let engines: EngineName[] | undefined
    if (narrator) {
      // a Slovene narrator's line: in its Slovene ElevenLabs voice where that comes first (a local clip there stays), the
      // worker for a line without a clip; or ('gepard') in the local voice first (--revoice: again in it)
      if (store.elevenlabsFirst(it.voice)) {
        if (overCap && had) continue
        engines = overCap ? ['gepard'] : had ? ['elevenlabs'] : undefined
      } else if (overCap) engines = ['gepard']
    } else if (had) {
      if (overCap) continue
      // --upgrade: replace a local clip; --revoice: any clip of that speaker
      engines = ['elevenlabs']
    } else if (overCap) {
      if (store.waitsForElevenLabs(norm, it.voice)) continue
      engines = ['gepard']
    }
    const c = await store.voice(it.text, it.voice, it.source, { engines, replace: !!had, force: revoiced })
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
