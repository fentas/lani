// Voice profiles (companion/README.md, "Voice"): one voice per person, kept for good. Everyone who speaks in
// the village has one: the cast speaker they stand in for (their archetype: grandma, young-man), a pitch and a
// pace of their own that the app plays that voice with (so three grandfathers never sound alike), and, while the
// ElevenLabs account has room, a voice of their own: designed with Voice Design v3 from a Slovene sample of their
// own lines, the clearest of three takes (the local Whisper listens), saved in the account. Their lines are then
// voiced in it on demand, the first time they are heard ("@micka" is her speaker in the voice store).
//
// Room: a voice of one's own takes one of the plan's voice slots (Starter: 10). The cast gets them first, in the
// order they arrive, and only once they live in the village; one slot stays free for a redesign. Everyone else
// speaks with their archetype's voice at their own pitch and pace.
import type { Database } from 'bun:sqlite'
import { createHash } from 'node:crypto'
import { cast, elevenlabsOf, type Gender, type PersonVoice, usePersonVoices } from './cast'
import type { Log } from './config'
import { PRIMORSKA_TUTOR } from './cultures'
import { labelEn, said, type Label, type SpokenLine } from './langs'
import type { ElevenLabs } from './voice'

export type ProfileStatus = 'shared' | 'designing' | 'own' | 'failed'

export type Profile = {
  person: string
  name: string
  gender: Gender
  /** The cast speaker they stand in for until (unless) they have a voice of their own. */
  archetype: string
  /** child | youth | adult | old: who the voice is for. */
  stage: string
  /** 1 for a villager of the cast (the culture pack's, companion/cultures/<culture>/villagers), who get their own voices first. */
  cast: number
  order: number
  /** They live in the village now (the app says so): only they get a voice designed. */
  present: number
  /** What Voice Design is asked for, and the Slovene it is heard speaking. */
  description: string
  sample: string
  seed: number
  elevenlabs: string | null
  pitch: number
  rate: number
  status: ProfileStatus
  error: string | null
  updated_at: string
}

/** Someone the app knows: a villager of the cast or a resident (someone who moved in, or was born). */
export type PersonIn = { id: string; name: string; gender: Gender; speaker: string; stage?: string; cast?: boolean; order?: number; present?: boolean }

/** What the app plays someone's lines with: their speaker (their own "@id", else the archetype) at their pitch and pace. */
export type ProfileView = { name: string; speaker: string; archetype: string; pitch: number; rate: number; status: ProfileStatus }

/** A villager of the cast, as far as a voice goes. */
/** The kinds of a villager's lines a voice sample is made of (lines.gift and the rest aren't). */
export type SampleLines = Partial<Record<'greet' | 'idle' | 'thanks' | 'cheer' | 'bye', SpokenLine[]>>

export type CastPerson = { id: string; name: string; voice: Gender; speaker?: string; role?: Label; personality?: string; order?: number; lines?: SampleLines }

/** Slots kept free, so a redesign always has room for the new voice before the old one goes. */
export const SPARE_SLOTS = 1
/** Designs a day at most: spreads the characters and the account's monthly voice edits. */
export const DESIGNS_PER_DAY = 4
/** Monthly voice edits kept for other uses (a redesign, a voice added by hand). */
export const SPARE_EDITS = 10

/** How each archetype sounds, for Voice Design. */
const VOICES: Record<string, string> = {
  grandma: 'an old woman in her seventies with a warm, slightly raspy old voice',
  grandpa: 'an old man in his seventies with a kind, slightly hoarse old voice',
  'young-man': 'a young man in his twenties with a friendly, bright voice',
  'young-woman': 'a young woman in her twenties with a light, friendly voice',
  woman: 'a woman in her forties with a warm, full voice',
  teacher: 'a woman in her thirties with a clear, calm voice, used to speaking to a class',
  gruff: 'a big man in his fifties with a deep, gruff, rough voice',
  girl: "a girl of about nine with a bright, lively child's voice",
  boy: "a boy of about nine with a lively child's voice",
  female: 'a woman with a warm, natural voice',
  male: 'a man with a warm, natural voice',
}

/** What anyone is heard saying when they have no lines of their own, in the village's language. */
const PLAIN_SAMPLES: Record<string, string> = {
  sl: 'Dober dan! Lepo, da si prišel. Danes je v vasi veliko dela, pa tudi dovolj časa za klepet. Kako si kaj? Pridi, sedi malo k meni.',
  it: 'Buongiorno! Che bello che sei venuto. Oggi in paese c\'è tanto lavoro, ma anche tempo per fare due chiacchiere. Come stai? Vieni, siediti un po\' qui con me.',
}
const plainSample = (language = 'sl') => PLAIN_SAMPLES[language] ?? PLAIN_SAMPLES.sl

export function hashOf(s: string): number {
  return createHash('sha1').update(s).digest().readUInt32BE(0)
}

/** A person's own pitch (0.93–1.07) and pace (0.96–1.04) for the archetype's voice, the same every time. */
export function variationOf(person: string): { pitch: number; rate: number } {
  const h = hashOf(`voice:${person}`)
  return { pitch: Math.round((0.93 + ((h & 0xff) / 255) * 0.14) * 100) / 100, rate: Math.round((0.96 + (((h >>> 8) & 0xff) / 255) * 0.08) * 100) / 100 }
}

/**
 * What Voice Design is asked for: who they are, how old they sound, and the [accent] of the village's culture pack
 * (its `tutor.voice`: a native speaker of Slovene from Primorska for Jan's).
 */
export function describe(p: { name: string; archetype: string; gender: Gender; role?: Label; personality?: string }, accent = PRIMORSKA_TUTOR.voice): string {
  const who = VOICES[p.archetype] ?? VOICES[p.gender]
  const role = p.role ? `${p.name}, ${labelEn(p.role).trim().toLowerCase()}. ` : ''
  const character = (p.personality ?? '').split(/(?<=[.!?])\s+/).slice(0, 2).join(' ')
  return `${role}${who[0].toUpperCase()}${who.slice(1)}. ${accent} ${character}`.trim().slice(0, 900)
}

/** What Voice Design hears them speak, in the village's [language]: their own lines (no placeholders), 110–400 characters. */
export function sampleOf(lines?: SampleLines, language = 'sl'): string {
  const out: string[] = []
  let n = 0
  for (const kind of ['greet', 'idle', 'thanks', 'cheer', 'bye'] as const) {
    for (const l of lines?.[kind] ?? []) {
      const t = said(l, language)
      if (t.includes('{') || n + t.length > 400) continue
      out.push(t)
      n += t.length + 1
    }
    if (n >= 160) break
  }
  const s = out.join(' ')
  return s.length >= 110 ? s : `${s} ${plainSample(language)}`.trim().slice(0, 400)
}

/** How much of [said] the transcript [heard] got right, word by word (0..1). */
export function wordMatch(said: string, heard: string): number {
  const words = (t: string) => t.toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, ' ').split(/\s+/).filter(Boolean)
  const a = words(said); const b = words(heard)
  if (!a.length || !b.length) return 0
  // longest common subsequence of words, over the longer length
  const dp = Array.from({ length: a.length + 1 }, () => new Array<number>(b.length + 1).fill(0))
  for (let i = 1; i <= a.length; i++) for (let j = 1; j <= b.length; j++) dp[i][j] = a[i - 1] === b[j - 1] ? dp[i - 1][j - 1] + 1 : Math.max(dp[i - 1][j], dp[i][j - 1])
  return dp[a.length][b.length] / Math.max(a.length, b.length)
}

/**
 * The voices of their own that are ready (as [Profiles] has them: status own, with an ElevenLabs voice), read from the
 * voice store's database without writing to it (voice-build opens it read-only): person → their voice. None in a store
 * from before the profiles.
 */
export function ownVoicesIn(db: Database): Map<string, PersonVoice> {
  try {
    const rows = db.query("SELECT person, archetype, elevenlabs FROM profiles WHERE status = 'own' AND elevenlabs IS NOT NULL").all() as { person: string; archetype: string; elevenlabs: string }[]
    return new Map(rows.map(r => [r.person, { elevenlabs: r.elevenlabs, archetype: r.archetype }]))
  } catch {
    return new Map()
  }
}

export class Profiles {
  private designing = false
  private designedToday: { day: string; n: number } = { day: '', n: 0 }

  constructor(
    private readonly db: Database,
    private readonly o: {
      eleven: ElevenLabs
      /** The local Whisper worker, to hear which take speaks the clearest Slovene. */
      sttUrl?: string
      cast: () => CastPerson[]
      /** The village's language (its culture pack's) and how its native speakers sound (the pack's tutor.voice). */
      language?: string
      accent?: string
      log?: Log
      /** Designs voices by itself (off in dry runs and tests that want to call [designNext] themselves). */
      auto?: boolean
      /** Someone's own voice changed: their old clips must go. */
      onVoiceChanged?: (voice: string) => void
      /** Called when someone got a voice of their own. */
      onUpdated?: (person: string) => void
    },
  ) {
    db.exec(`CREATE TABLE IF NOT EXISTS profiles (
      person TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      gender TEXT NOT NULL,
      archetype TEXT NOT NULL,
      stage TEXT NOT NULL,
      "cast" INTEGER NOT NULL,
      "order" INTEGER NOT NULL,
      present INTEGER NOT NULL,
      description TEXT NOT NULL,
      sample TEXT NOT NULL,
      seed INTEGER NOT NULL,
      elevenlabs TEXT,
      pitch REAL NOT NULL,
      rate REAL NOT NULL,
      status TEXT NOT NULL,
      error TEXT,
      updated_at TEXT NOT NULL
    )`)
    // a design cut short (the bridge stopped) is over: back to what they had
    db.exec(`UPDATE profiles SET status = CASE WHEN elevenlabs IS NOT NULL THEN 'own' ELSE 'shared' END WHERE status = 'designing'`)
    usePersonVoices(id => {
      const p = this.get(id)
      return p?.status === 'own' && p.elevenlabs ? { elevenlabs: p.elevenlabs, archetype: p.archetype } : undefined
    })
  }

  get(person: string): Profile | undefined {
    return (this.db.query('SELECT * FROM profiles WHERE person = ?').get(person) as Profile | null) ?? undefined
  }

  all(): Profile[] {
    return this.db.query('SELECT * FROM profiles ORDER BY "cast" DESC, "order", person').all() as Profile[]
  }

  /** What the app plays [p]'s lines with. */
  view(p: Profile): ProfileView {
    const own = p.status === 'own' && !!p.elevenlabs
    return { name: p.name, speaker: own ? `@${p.person}` : p.archetype, archetype: p.archetype, pitch: own ? 1 : p.pitch, rate: own ? 1 : p.rate, status: p.status }
  }

  views(): Record<string, ProfileView> {
    return Object.fromEntries(this.all().map(p => [p.person, this.view(p)]))
  }

  private write(p: Profile) {
    this.db
      .query(`INSERT OR REPLACE INTO profiles (person, name, gender, archetype, stage, "cast", "order", present, description, sample, seed, elevenlabs, pitch, rate, status, error, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`)
      .run(p.person, p.name, p.gender, p.archetype, p.stage, p.cast, p.order, p.present, p.description, p.sample, p.seed, p.elevenlabs, p.pitch, p.rate, p.status, p.error, p.updated_at)
  }

  /**
   * The cast's profiles from their villager files (new villagers get one; a changed archetype or text is taken
   * over while they have no voice of their own). The first of each archetype, by order, keeps its voice as it
   * is; the others get their own pitch and pace.
   */
  syncCast() {
    const firsts = new Map<string, string>()
    for (const v of [...this.o.cast()].sort((a, b) => (a.order ?? 100) - (b.order ?? 100))) {
      const archetype = v.speaker && cast()[v.speaker] ? v.speaker : v.voice
      if (!firsts.has(archetype)) firsts.set(archetype, v.id)
      const variation = firsts.get(archetype) === v.id ? { pitch: 1, rate: 1 } : variationOf(v.id)
      const old = this.get(v.id)
      const stage = /^(girl|boy)$/.test(archetype) ? 'child' : /^grand/.test(archetype) ? 'old' : 'adult'
      const fresh: Profile = {
        person: v.id, name: v.name, gender: v.voice, archetype, stage, cast: 1, order: v.order ?? 100, present: old?.present ?? 0,
        description: describe({ name: v.name, archetype, gender: v.voice, role: v.role, personality: v.personality }, this.o.accent),
        sample: sampleOf(v.lines, this.o.language), seed: old?.seed ?? hashOf(v.id) % 100_000,
        elevenlabs: old?.elevenlabs ?? null, pitch: variation.pitch, rate: variation.rate,
        status: old?.status ?? 'shared', error: old?.error ?? null, updated_at: new Date().toISOString(),
      }
      if (old && old.status === 'own') { fresh.description = old.description; fresh.sample = old.sample; fresh.archetype = old.archetype }
      if (!old || JSON.stringify({ ...old, updated_at: '' }) !== JSON.stringify({ ...fresh, updated_at: '' })) this.write(fresh)
    }
  }

  /**
   * The people the app knows: the cast who live in the village and the residents (who moved in or were born).
   * New ones get a profile; everyone listed is present, anyone the app no longer lists is not.
   */
  upsert(people: PersonIn[]) {
    const listed = new Set(people.map(p => p.id))
    for (const p of this.all()) if (p.present && !listed.has(p.person)) this.write({ ...p, present: 0 })
    for (const pi of people) {
      const old = this.get(pi.id)
      const archetype = cast()[pi.speaker] ? pi.speaker : pi.gender
      if (old) {
        // the cast's own texts come from their files; a resident may have grown up into another archetype
        const next = { ...old, name: pi.name || old.name, present: pi.present === false ? 0 : 1, stage: pi.stage ?? old.stage }
        if (!old.cast && old.status !== 'own' && old.archetype !== archetype) {
          next.archetype = archetype
          next.description = describe({ name: next.name, archetype, gender: pi.gender }, this.o.accent)
        }
        if (JSON.stringify(next) !== JSON.stringify(old)) this.write({ ...next, updated_at: new Date().toISOString() })
        continue
      }
      const v = variationOf(pi.id)
      this.write({
        person: pi.id, name: pi.name, gender: pi.gender, archetype, stage: pi.stage ?? 'adult', cast: pi.cast ? 1 : 0, order: pi.order ?? 500,
        present: pi.present === false ? 0 : 1, description: describe({ name: pi.name, archetype, gender: pi.gender }, this.o.accent), sample: plainSample(this.o.language),
        seed: hashOf(pi.id) % 100_000, elevenlabs: null, pitch: v.pitch, rate: v.rate, status: 'shared', error: null, updated_at: new Date().toISOString(),
      })
    }
    if (this.o.auto !== false) void this.designNext()
  }

  /**
   * Who gets a voice of their own next: the cast who live here, by order; then residents, but only while the
   * slots left are more than the cast still waiting (the cast always comes first). A failed design waits a day.
   */
  candidate(freeSlots: number): Profile | undefined {
    const waiting = this.all().filter(p => p.status !== 'own')
    const castWaiting = waiting.filter(p => p.cast).length
    const retry = (p: Profile) => p.status !== 'failed' || Date.now() - Date.parse(p.updated_at) > 86_400_000
    const present = waiting.filter(p => p.present && p.status !== 'designing' && retry(p))
    const castNext = present.find(p => p.cast)
    if (castNext) return castNext
    return freeSlots > castWaiting ? present.find(p => !p.cast) : undefined
  }

  /** Room for another voice: free slots beyond the spare, voice edits left, characters for the sample, today's count. */
  private async room(chars: number): Promise<{ free: number } | { why: string }> {
    const q = await this.o.eleven.subscription(true)
    if (!q) return { why: 'ElevenLabs is not configured or not answering' }
    if (q.voice_limit === undefined || q.voice_slots_used === undefined) return { why: 'the subscription shows no voice slots' }
    const free = q.voice_limit - q.voice_slots_used - SPARE_SLOTS
    if (free <= 0) return { why: `no voice slot free (${q.voice_slots_used}/${q.voice_limit}, one kept for a redesign)` }
    if (q.voice_edits !== undefined && q.voice_edits_max !== undefined && q.voice_edits_max - q.voice_edits <= SPARE_EDITS) return { why: `few voice edits left this month (${q.voice_edits}/${q.voice_edits_max})` }
    if (!(await this.o.eleven.ready(chars, false))) return { why: 'not enough characters left above the reserve' }
    const day = new Date().toISOString().slice(0, 10)
    if (this.designedToday.day === day && this.designedToday.n >= DESIGNS_PER_DAY) return { why: `${DESIGNS_PER_DAY} voices designed today already` }
    return { free }
  }

  /** Designs the next candidate's voice, one at a time, then the next; stops when there's no room or no one waiting. */
  async designNext(): Promise<string | undefined> {
    if (this.designing) return undefined
    this.designing = true
    try {
      for (;;) {
        const r = await this.room(200)
        if ('why' in r) return r.why
        const p = this.candidate(r.free)
        if (!p) return 'no one waiting for a voice'
        await this.design(p)
      }
    } finally {
      this.designing = false
    }
  }

  /**
   * Makes [p] a voice of their own: three takes of Voice Design for their description, speaking their sample;
   * the one Whisper understands best is saved in the account. [redesign]: a new voice for someone who has one
   * (a new seed, maybe a new description); the old voice and its clips go once the new one is there.
   */
  async design(p: Profile, redesign?: { description?: string; reference?: Uint8Array }): Promise<Profile> {
    const day = new Date().toISOString().slice(0, 10)
    if (this.designedToday.day !== day) this.designedToday = { day, n: 0 }
    this.designedToday.n++
    const description = redesign?.description?.trim() || p.description
    const seed = redesign ? (p.seed + 1 + (hashOf(`${Date.now()}`) % 997)) % 100_000 : p.seed
    this.write({ ...p, status: 'designing', error: null, updated_at: new Date().toISOString() })
    const fail = (why: string) => {
      const back = { ...p, status: (p.status === 'own' ? 'own' : 'failed') as ProfileStatus, error: why, updated_at: new Date().toISOString() }
      this.write(back)
      this.o.log?.(`voice profiles: ${p.person}: ${why}`)
      return back
    }
    const takes = await this.o.eleven.design(description, p.sample, seed, redesign?.reference)
    if (!takes) return fail('Voice Design gave no takes')
    const best = await this.clearest(takes, p.sample)
    const id = await this.o.eleven.createVoice(`Lani @${p.person} (${p.name})`, description, best.id, takes.filter(t => t !== best).map(t => t.id))
    if (!id) return fail('the voice could not be saved (slots?)')
    const old = p.status === 'own' ? p.elevenlabs : null
    const done: Profile = { ...p, description, seed, elevenlabs: id, status: 'own', error: null, updated_at: new Date().toISOString() }
    this.write(done)
    if (old && old !== id) {
      this.o.onVoiceChanged?.(`@${p.person}`)
      await this.o.eleven.deleteVoice(old)
    }
    this.o.log?.(`voice profiles: ${p.person} has a voice of their own`)
    this.o.onUpdated?.(p.person)
    return done
  }

  /** Of Voice Design's takes, the one Whisper hears speaking [sample] best; the first when Whisper isn't there. */
  private async clearest(takes: { id: string; audio: Uint8Array }[], sample: string) {
    if (!this.o.sttUrl || takes.length < 2) return takes[0]
    let best = takes[0]
    let bestScore = -1
    for (const t of takes) {
      try {
        const r = await fetch(`${this.o.sttUrl.replace(/\/$/, '')}/transcribe?language=${this.o.language ?? 'sl'}`, { method: 'POST', headers: { 'content-type': 'audio/mpeg' }, body: t.audio as unknown as BodyInit, signal: AbortSignal.timeout(60_000) })
        if (!r.ok) continue
        const heard = ((await r.json()) as { text?: string }).text ?? ''
        const score = wordMatch(sample, heard)
        if (score > bestScore) { best = t; bestScore = score }
      } catch {}
    }
    return best
  }

  /**
   * A new voice for [person] (optionally from a new [description], and sounding [like] another voice: a cast
   * speaker, someone's own "@id", or an ElevenLabs voice id, whose sample lends the timbre): for when their voice
   * doesn't suit them.
   */
  async redesign(person: string, description?: string, like?: string): Promise<Profile | { error: string }> {
    const p = this.get(person)
    if (!p) return { error: `no voice profile for "${person}"` }
    let reference: Uint8Array | undefined
    if (like) {
      const id = elevenlabsOf(like) ?? (/^[A-Za-z0-9]{10,40}$/.test(like) ? like : undefined)
      if (!id) return { error: `no voice "${like}": a cast speaker (grandpa), someone's own (@micka) or an ElevenLabs voice id` }
      reference = (await this.o.eleven.preview(id)) ?? undefined
      if (!reference) return { error: `no sample recording for the voice "${like}" to sound like` }
    }
    if (p.status === 'designing' || this.designing) return { error: 'a voice is being designed right now; try again in a minute' }
    const q = await this.o.eleven.subscription(true)
    if (!q || q.voice_limit === undefined || (q.voice_slots_used ?? 0) >= q.voice_limit) return { error: 'no voice slot free for the new voice' }
    this.designing = true
    try {
      return await this.design(p, { description, reference })
    } finally {
      this.designing = false
    }
  }
}
