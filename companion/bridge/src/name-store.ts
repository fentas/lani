// Name lines in the voice store (docs/plans/05-built/audio-names.md): the shared pieces of a line, made the first time anyone
// reaches it, and the name clips, made the first time anyone of that name needs one. Both stay for everyone. A learner's line
// is a manifest of them, joined on the phone; an older app gets one finished file at the same URL.
//
// Off unless the node sets LANI_VOICE_NAMES: until then nothing here runs and every line is voiced whole, as before. A name
// clip's row is its language, voice, normalized name and kind: no learner, node or device, here or in a log line.
import { createHash } from 'node:crypto'
import { existsSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import type { Addressee } from './addressee'
import { elevenlabsOf } from './cast'
import { normalizeText } from './family'
import { activeDb, cutLine, fades, medianPitch, nameCut, phraseCut, SR, slice, tracks, type Span } from './name-cut'
import { FILLERS, NAME_MARK, nameOk, saysName, spokenName, type KnownLine, type LineType, type NameCatalogue, type Template } from './name-lines'
import { ffmpegMp3Writer, ffmpegPcm, fileUrl, TURN, type Alignment, type PcmReader, type PcmWriter, type TimedTake, type Turn, type VoiceName, type VoiceStore } from './voice'

/** The level a name clip of a template is stored at ([activeDb]), so a line's one gain fits every name. */
export const NAME_ACTIVE_DB = -20
/** The most a line's gain moves a name, as the test's did. */
export const NAME_GAIN_MAX = 8

export type NameSettings = {
  /** Lines with the learner's name are built from pieces (LANI_VOICE_NAMES); off, every line is voiced whole, as before. */
  on: boolean
  /** The characters pieces and name clips may cost a day (LANI_VOICE_NAMES_DAILY). */
  daily: number
  /** The names a node may have voiced anew in a calendar month: the bound on a name field (LANI_VOICE_NAMES_PER_LEARNER). */
  names: number
  /** Takes of a name, the lower kept: the test's rule; they stop paying at 2-3 (LANI_VOICE_NAME_TAKES, 1 to 3). */
  takes: number
}

const whole = (env: Record<string, string | undefined>, name: string, fallback: number, max = Infinity) => {
  const v = Number(env[name]?.trim() || NaN)
  return Number.isInteger(v) && v >= 0 ? Math.min(v, max) : fallback
}

/** The node's settings for name lines. */
export const nameSettings = (env: Record<string, string | undefined> = process.env): NameSettings => ({
  on: /^(on|1|true|yes)$/i.test(env.LANI_VOICE_NAMES?.trim() ?? ''),
  daily: whole(env, 'LANI_VOICE_NAMES_DAILY', 3_000),
  names: whole(env, 'LANI_VOICE_NAMES_PER_LEARNER', 3),
  takes: Math.max(1, whole(env, 'LANI_VOICE_NAME_TAKES', 2, 3)),
})

/** A part of a line's manifest: the name, a pause, or a shared piece. */
export type LinePart = { kind: 'name' } | { kind: 'gap'; ms: number } | { kind: 'piece'; part: 'head' | 'tail'; text: string; file: string; ms: number }

/** A line's manifest, shared, with no name in it. A line whose cut can't be trusted is 'whole' and has no parts. */
export type LineManifest = {
  v: 1; line: string; language: string; voice: VoiceName; said: string; type: LineType; template: Template
  kind: string; phrase?: string; gain: number; parts: LinePart[]; why?: string
}

/** A name said by a voice in a kind. No [file]: it couldn't be cut from what was paid for, and isn't tried again. */
export type NameClip = { language: string; voice: VoiceName; name: string; kind: string; phrase?: string; file: string | null; ms: number; engine_voice: string; model: string }

/** The manifest the phone gets: a line's parts with the name clip's URL in them. */
export type PhonePart = { kind: 'name' | 'piece'; url: string; ms: number; gain: number } | { kind: 'gap'; ms: number }
export type PhoneManifest = { v: 1; id: string; text: string; voice: VoiceName; parts: PhonePart[] }

/** A line's URL, for the phone and an older app alike: its last segment is any clip's name. */
export const lineUrl = (id: string) => `/voice/line/${id}.mp3`

/**
 * What to do with a line: 'before', voice it as ever (no name line here, or one voiced whole); 'phone', nothing may be voiced
 * for it now, so the phone's voice says it; 'joined', the line's URL.
 */
export type NameAnswer = { say: 'before' } | { say: 'phone' } | { say: 'joined'; url: string; engine: string }
const BEFORE: NameAnswer = { say: 'before' }
const PHONE: NameAnswer = { say: 'phone' }

const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const sha1 = (s: string) => createHash('sha1').update(s).digest('hex')
const clamp = (v: number, lim: number) => Math.max(-lim, Math.min(lim, v))
const scaled = (a: Alignment, tempo: number): Alignment =>
  tempo === 1 ? a : { characters: a.characters, character_start_times_seconds: a.character_start_times_seconds?.map(t => t / tempo), character_end_times_seconds: a.character_end_times_seconds?.map(t => t / tempo) }

/** A line's id: its language, its voice and what is said. */
export const lineId = (language: string, voice: VoiceName, said: string) => sha1(`${language}|${voice}|${said}`).slice(0, 16)

export class NameStore {
  private readonly running = new Map<string, Promise<unknown>>()
  private readonly pcm?: PcmReader
  private readonly mp3?: PcmWriter
  /** Told once a day that the day's characters are spent, not at every line. */
  private capLogged = ''

  constructor(
    private readonly voice: VoiceStore,
    private readonly o: { settings: NameSettings; catalogue: () => NameCatalogue | undefined; log?: (...a: unknown[]) => void; pcm?: PcmReader; mp3?: PcmWriter },
  ) {
    if (!o.settings.on) return
    this.pcm = o.pcm ?? ffmpegPcm()
    this.mp3 = o.mp3 ?? ffmpegMp3Writer()
    const db = voice.database
    db.exec(`CREATE TABLE IF NOT EXISTS name_lines (
      line TEXT PRIMARY KEY, language TEXT NOT NULL, voice TEXT NOT NULL, said TEXT NOT NULL, type TEXT NOT NULL, template TEXT NOT NULL,
      kind TEXT NOT NULL, phrase TEXT, gain REAL NOT NULL, parts TEXT NOT NULL, why TEXT, chars INTEGER NOT NULL,
      engine_voice TEXT NOT NULL, model TEXT, take TEXT, alignment TEXT, created_at TEXT NOT NULL
    )`)
    db.exec(`CREATE TABLE IF NOT EXISTS name_clips (
      language TEXT NOT NULL, voice TEXT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL, phrase TEXT, file TEXT, ms INTEGER NOT NULL,
      chars INTEGER NOT NULL, engine_voice TEXT NOT NULL, model TEXT, created_at TEXT NOT NULL, PRIMARY KEY (language, voice, name, kind)
    )`)
    db.exec(`CREATE TABLE IF NOT EXISTS name_joined (
      id TEXT PRIMARY KEY, key TEXT NOT NULL, norm TEXT NOT NULL, voice TEXT NOT NULL, text TEXT NOT NULL, parts TEXT NOT NULL,
      engine_voice TEXT NOT NULL, created_at TEXT NOT NULL
    )`)
    db.exec('CREATE INDEX IF NOT EXISTS name_joined_key ON name_joined (key)')
    db.exec('CREATE TABLE IF NOT EXISTS name_seen (month TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY (month, name))')
  }

  get on(): boolean {
    return this.o.settings.on && !!this.pcm && !!this.mp3 && !!this.voice.eleven?.configured
  }

  /** The node set LANI_VOICE_NAMES: the name is checked, whatever else can or can't run. */
  get guarding(): boolean { return this.o.settings.on }

  /**
   * Whether [text] says [a]'s name and that name may not be voiced ([nameOk]). Any text: the learner's own lines, a declined form,
   * a re-record and a line the catalogue doesn't know have their name just the same. The phone's voice says them.
   */
  refuses(text: string, a: Addressee): boolean {
    return !nameOk(a.name) && saysName(text, a.name, a.forms)
  }

  private get db() { return this.voice.database }
  private get language() { return this.voice.language }

  // --- the bounds -----------------------------------------------------------------------------------------------------

  /** The characters pieces and name clips cost today. */
  spentToday(): number {
    return (this.db.query('SELECT chars FROM spend WHERE month = ?').get(`names:${today()}`) as { chars: number } | null)?.chars ?? 0
  }

  private spend(chars: number) {
    this.db.query('INSERT INTO spend (month, chars) VALUES (?, ?) ON CONFLICT (month) DO UPDATE SET chars = chars + excluded.chars').run(`names:${today()}`, chars)
  }

  /**
   * Whether [chars] more may be spent now: 'cap' when the day's characters are gone (voiced whole, the line would spend them all
   * the same, so the phone's voice says it); 'engine' when ElevenLabs can't take them, which leaves the line to what else can
   * say it, the local worker first, as ever.
   */
  private async room(chars: number, turn: Turn): Promise<true | 'cap' | 'engine'> {
    if (this.spentToday() + chars > this.o.settings.daily) {
      if (this.capLogged !== today()) {
        this.capLogged = today()
        this.o.log?.(`voice: the day's characters for name lines are spent (${this.spentToday()} of ${this.o.settings.daily}): the phone's voice says the rest`)
      }
      return 'cap'
    }
    return (await this.voice.eleven?.ready(chars, turn === TURN.live)) === true || 'engine'
  }

  /** Whether another name may be voiced this month, on this node: a name already counted may, and so may a few new ones. */
  private nameAllowed(name: string): boolean {
    const month = today().slice(0, 7)
    if (this.db.query('SELECT 1 FROM name_seen WHERE month = ? AND name = ?').get(month, name)) return true
    const n = (this.db.query('SELECT COUNT(*) AS n FROM name_seen WHERE month = ?').get(month) as { n: number }).n
    if (n >= this.o.settings.names) return false
    this.db.query('INSERT INTO name_seen (month, name) VALUES (?, ?)').run(month, name)
    return true
  }

  // --- the line ---------------------------------------------------------------------------------------------------------

  /**
   * What to do with [text] said in [voice] to [a]. A clip of the whole line stays the line's clip, and a text that is no line
   * of the content goes on as ever. Never throws.
   */
  async line(text: string, voice: VoiceName, a: Addressee, turn: Turn = TURN.live): Promise<NameAnswer> {
    try {
      return await this.answer(text, voice, a, turn)
    } catch (e) {
      this.o.log?.(`voice: a name line failed: ${(e as Error).message}`)
      return BEFORE
    }
  }

  private async answer(text: string, voice: VoiceName, a: Addressee, turn: Turn): Promise<NameAnswer> {
    if (!this.guarding) return BEFORE
    // before anything else of the line: voiced whole, it would say the name just the same
    if (this.refuses(text, a)) return PHONE
    if (!this.on) return BEFORE
    const norm = normalizeText(text)
    if (this.voice.best(norm, voice)) return BEFORE
    const line = this.o.catalogue()?.find(text, a.name)
    const name = nameOk(a.name)
    if (!line || !name || line.type === 'whole') return BEFORE
    // a Slovene narrator speaks through their Slovene voice, when that comes first; else their clips cost nothing
    if (this.voice.sloveneNarrator(voice) && !this.voice.elevenlabsFirst(voice)) return BEFORE
    const speaker = this.voice.slVoiceOf(voice) ?? voice
    const own = elevenlabsOf(speaker)
    if (!own) return BEFORE
    const key = `${voice}:${norm}`
    const done = this.db.query('SELECT id FROM name_joined WHERE key = ? AND engine_voice = ? ORDER BY created_at DESC').get(key, own) as { id: string } | null
    if (done) return { say: 'joined', url: lineUrl(done.id), engine: 'elevenlabs' }
    // the rate limit of live lines is on what is voiced: a line of parts all here costs nothing
    if (turn === TURN.live && !this.paidFor(line, voice, name, own) && !this.voice.liveSlot()) return PHONE
    const spec = await this.once(`line|${voice}|${line.said}`, () => this.manifest(line, voice, speaker, own, turn))
    if (spec === 'cap') return PHONE
    if (!spec || spec.type === 'whole') return BEFORE
    const clip = await this.once(`name|${voice}|${name}|${spec.kind}`, () => this.nameClip(name, line, voice, speaker, own, turn))
    if (clip === 'cap') return PHONE
    if (!clip?.file) return BEFORE
    return { say: 'joined', url: lineUrl(this.joined(text, voice, own, spec, clip)), engine: 'elevenlabs' }
  }

  /** Whether [line]'s manifest and its name clip are here in the voice now: joining it voices nothing. */
  private paidFor(line: KnownLine, voice: VoiceName, name: string, own: string): boolean {
    return !!this.db.query('SELECT 1 FROM name_lines WHERE line = ? AND engine_voice = ?').get(lineId(this.language, voice, line.said), own) &&
      !!this.db.query('SELECT 1 FROM name_clips WHERE language = ? AND voice = ? AND name = ? AND kind = ? AND engine_voice = ?').get(this.language, voice, name, line.kind, own)
  }

  private once<T>(key: string, make: () => Promise<T>): Promise<T> {
    const busy = this.running.get(key) as Promise<T> | undefined
    if (busy) return busy
    const job = make().finally(() => this.running.delete(key))
    this.running.set(key, job)
    return job
  }

  /** The line's shared manifest in [voice]: the one there, made if it isn't (a voice swapped under it counts as none). */
  private async manifest(line: KnownLine, voice: VoiceName, speaker: VoiceName, own: string, turn: Turn): Promise<LineManifest | 'cap' | null> {
    const id = lineId(this.language, voice, line.said)
    const row = this.db.query('SELECT * FROM name_lines WHERE line = ? AND engine_voice = ?').get(id, own) as (Omit<LineManifest, 'parts' | 'v'> & { parts: string }) | null
    if (row) return { ...row, v: 1, parts: JSON.parse(row.parts) as LinePart[] }
    const base = { v: 1 as const, line: id, language: this.language, voice, said: line.said, type: line.type, template: line.template, kind: line.kind, ...(line.type === 'phrase' ? { phrase: line.carrier } : {}) }
    // the name or its phrase is the whole line: no take of it, nothing shared
    if (!line.head && !line.tail) return this.keep({ ...base, gain: 0, parts: [{ kind: 'name' }] }, own)
    const filler = FILLERS[this.language]?.[line.woman ? 'f' : 'm']
    if (!filler) return null
    const said = line.said.replace(NAME_MARK, filler)
    const room = await this.room(said.length, turn)
    if (room !== true) return room === 'cap' ? 'cap' : null
    const take = await this.voice.eleven!.timed(said, speaker, turn)
    if (!take) return null
    this.spend(take.chars)
    const ready = await this.voice.cuttable(take.bytes, speaker)
    const x = ready ? await this.pcm!(ready.bytes) : null
    if (!ready || !x?.length) return null
    const tr = tracks(x)
    const cut = cutLine(x, scaled(take.alignment, ready.tempo), line, filler, tr, !line.woman)
    if ('why' in cut) {
      this.o.log?.(`voice: "${line.said}" (${voice}) is voiced whole for each learner: ${cut.why}`)
      return this.keep({ ...base, type: 'whole', kind: '', gain: 0, parts: [], why: cut.why }, own, take)
    }
    const parts: LinePart[] = []
    const piece = async (part: 'head' | 'tail', text: string, span: Span, fin: number, fout: number) => {
      const y = fades(slice(x, span[0], span[1]), fin, fout)
      const bytes = await this.mp3!(y)
      if (!bytes) throw new Error('ffmpeg could not write a piece')
      const file = this.write(`piece|${id}|${part}|${own}|${take.model}`, bytes)
      parts.push({ kind: 'piece', part, text, file, ms: Math.round((y.length / SR) * 1000) })
    }
    const gap = (s: number) => {
      const ms = Math.round(s * 1000)
      if (ms > 0) parts.push({ kind: 'gap', ms })
    }
    if (cut.head) {
      await piece('head', line.head, cut.head, 0, 0.01)
      gap(cut.gapLeft)
    }
    parts.push({ kind: 'name' })
    if (cut.tail) {
      gap(cut.gapRight)
      await piece('tail', line.tail, cut.tail, 0.005, 0)
    }
    // the name where the filler stood in its own take; a phrase needs none
    const gain = line.type === 'phrase' ? 0 : Math.round(clamp(activeDb(slice(x, cut.filler[0], cut.filler[1])) - NAME_ACTIVE_DB, NAME_GAIN_MAX) * 10) / 10
    return this.keep({ ...base, gain, parts }, own, take)
  }

  private keep(m: LineManifest, own: string, take?: TimedTake): LineManifest {
    const file = take ? this.write(`take|${m.line}|${own}|${take.model}`, take.bytes) : null
    this.db
      .query('INSERT OR REPLACE INTO name_lines (line, language, voice, said, type, template, kind, phrase, gain, parts, why, chars, engine_voice, model, take, alignment, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)')
      .run(m.line, m.language, m.voice, m.said, m.type, m.template, m.kind, m.phrase ?? null, m.gain, JSON.stringify(m.parts), m.why ?? null, take?.chars ?? 0, own, take?.model ?? null, file, take ? JSON.stringify(take.alignment) : null, new Date().toISOString())
    return m
  }

  /** [bytes] in the store under a name of [seed], as a clip's. */
  private write(seed: string, bytes: Uint8Array): string {
    const file = `${sha1(seed)}.mp3`
    const p = join(this.voice.filesDir, file)
    writeFileSync(`${p}.tmp`, bytes)
    renameSync(`${p}.tmp`, p)
    return file
  }

  // --- the name ---------------------------------------------------------------------------------------------------------

  /** [name] in [voice] as [line] wants it: the clip there (a voice swapped under it counts as none), made if it isn't. */
  private async nameClip(name: string, line: KnownLine, voice: VoiceName, speaker: VoiceName, own: string, turn: Turn): Promise<NameClip | 'cap' | null> {
    const had = this.db.query('SELECT * FROM name_clips WHERE language = ? AND voice = ? AND name = ? AND kind = ? AND engine_voice = ?').get(this.language, voice, name, line.kind, own) as NameClip | null
    if (had) return had
    if (!this.nameAllowed(name)) return 'cap'
    const spoken = spokenName(name)
    const carrier = line.carrier.replace(NAME_MARK, spoken)
    const takes = this.o.settings.takes
    const room = await this.room(carrier.length * takes, turn)
    if (room !== true) return room === 'cap' ? 'cap' : null
    const phrase = line.type === 'phrase'
    const cuts: { y: Float32Array; pitch: number; model: string }[] = []
    let chars = 0
    let model = this.voice.eleven!.model
    // something outside the take went wrong (ElevenLabs, ffmpeg under load): no verdict on this name, it is tried again
    let passing = false
    for (let i = 0; i < takes; i++) {
      const take = await this.voice.eleven!.timed(carrier, speaker, turn)
      if (!take) {
        passing = true
        break
      }
      this.spend(take.chars)
      chars += take.chars
      model = take.model
      const ready = await this.voice.cuttable(take.bytes, speaker)
      const x = ready ? await this.pcm!(ready.bytes) : null
      if (!ready || !x?.length) {
        passing = true
        continue
      }
      const tr = tracks(x)
      const a = scaled(take.alignment, ready.tempo)
      const said = nameCut(x, a, spoken, tr)
      const span = phrase ? phraseCut(x, a, spoken, tr) : said
      if (!span || !said) continue
      const y = slice(x, span[0], span[1])
      const secs = y.length / SR
      const long = phrase ? line.phrase.length - NAME_MARK.length + spoken.length : spoken.length
      if (secs < 0.1 + 0.02 * long || secs > 0.5 + 0.16 * long) continue
      // how high the name is said, not the phrase around it: the words before it would decide otherwise
      cuts.push({ y, pitch: medianPitch(slice(x, said[0], said[1])), model: take.model })
    }
    if (!chars || (passing && !cuts.length)) return null
    // of the takes that cut, the lower: a voice said high stands out of the line around it
    const best = cuts.reduce<(typeof cuts)[number] | undefined>((b, c) => (!b || (c.pitch > 0 && (b.pitch === 0 || c.pitch < b.pitch)) ? c : b), undefined)
    const row: NameClip = { language: this.language, voice, name, kind: line.kind, ...(phrase ? { phrase: line.carrier } : {}), file: null, ms: 0, engine_voice: own, model }
    if (best) {
      let y = fades(best.y)
      if (!phrase) {
        const g = 10 ** (clamp(NAME_ACTIVE_DB - activeDb(y), 20) / 20)
        y = y.map(v => v * g)
      }
      const bytes = await this.mp3!(y)
      if (!bytes) return null
      row.file = this.write(`name|${this.language}|${voice}|${name}|${line.kind}|${own}|${model}`, bytes)
      row.ms = Math.round((y.length / SR) * 1000)
    }
    if (!row.file) this.o.log?.(`voice: a name in "${line.carrier}" (${voice}) could not be cut: its lines are voiced whole`)
    this.db
      .query('INSERT OR REPLACE INTO name_clips (language, voice, name, kind, phrase, file, ms, chars, engine_voice, model, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)')
      .run(row.language, row.voice, row.name, row.kind, row.phrase ?? null, row.file, row.ms, chars, own, model, new Date().toISOString())
    return row
  }

  // --- the joined line --------------------------------------------------------------------------------------------------

  /** The learner's line, its parts with the name clip in them, kept: its id. The same parts are the same id. */
  private joined(text: string, voice: VoiceName, own: string, spec: LineManifest, clip: NameClip): string {
    const parts: PhonePart[] = spec.parts.map(p =>
      p.kind === 'gap' ? p : p.kind === 'name' ? { kind: 'name', url: fileUrl(clip.file!), ms: clip.ms, gain: spec.gain } : { kind: 'piece', url: fileUrl(p.file), ms: p.ms, gain: 0 })
    const id = sha1(JSON.stringify(parts.map(p => (p.kind === 'gap' ? p.ms : [p.url, p.gain]))))
    const norm = normalizeText(text)
    this.db
      .query('INSERT OR REPLACE INTO name_joined (id, key, norm, voice, text, parts, engine_voice, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)')
      .run(id, `${voice}:${norm}`, norm, voice, text.trim(), JSON.stringify(parts), own, new Date().toISOString())
    return id
  }

  private row(id: string) {
    return this.db.query('SELECT * FROM name_joined WHERE id = ?').get(id) as { id: string; text: string; voice: VoiceName; parts: string } | null
  }

  /** The phone's manifest of line [id], undefined when there is none. */
  phone(id: string): PhoneManifest | undefined {
    if (!this.o.settings.on || !/^[a-f0-9]{40}$/.test(id)) return undefined
    const r = this.row(id)
    return r ? { v: 1, id: r.id, text: r.text, voice: r.voice, parts: JSON.parse(r.parts) as PhonePart[] } : undefined
  }

  /** Line [id] as one finished MP3 for an app that can't join: made the first time it is asked for, then kept. Its path. */
  finished(id: string): Promise<string | undefined> {
    const m = this.phone(id)
    if (!m) return Promise.resolve(undefined)
    const p = join(this.voice.filesDir, `${id}.mp3`)
    if (existsSync(p)) return Promise.resolve(p)
    return this.once(`finished|${id}`, async () => {
      const parts: Float32Array[] = []
      for (const part of m.parts) {
        if (part.kind === 'gap') {
          parts.push(new Float32Array(Math.round((part.ms / 1000) * SR)))
          continue
        }
        const file = this.voice.path(part.url.slice(part.url.lastIndexOf('/') + 1))
        const x = file ? await this.pcm!(new Uint8Array(readFileSync(file))) : null
        if (!x) return undefined
        const g = 10 ** (part.gain / 20)
        parts.push(g === 1 ? x : x.map(v => v * g))
      }
      const all = new Float32Array(parts.reduce((n, x) => n + x.length, 0))
      let at = 0
      for (const x of parts) {
        all.set(x, at)
        at += x.length
      }
      const bytes = await this.mp3!(all)
      if (!bytes) return undefined
      writeFileSync(`${p}.tmp`, bytes)
      renameSync(`${p}.tmp`, p)
      return p
    })
  }

  /** [index] (GET /voice/index) with the lines joined here: a whole clip of a line stays its clip. */
  withLines<T extends Record<string, Record<string, string>>>(index: T): T {
    if (!this.o.settings.on) return index
    const now = new Map<string, string | undefined>()
    const ownOf = (v: string) => {
      if (!now.has(v)) now.set(v, elevenlabsOf(this.voice.slVoiceOf(v) ?? v))
      return now.get(v)
    }
    for (const r of this.db.query('SELECT id, norm, voice, engine_voice FROM name_joined ORDER BY created_at').all() as { id: string; norm: string; voice: string; engine_voice: string }[]) {
      // a voice swapped under it: left out, so the app asks for the line and it is made again in the voice now
      if (ownOf(r.voice) !== r.engine_voice) continue
      const at = ((index as Record<string, Record<string, string>>)[r.norm] ??= {})
      if (!at[r.voice] || at[r.voice].startsWith('/voice/line/')) at[r.voice] = lineUrl(r.id)
    }
    return index
  }

  /** What the store holds of name lines, for voice_status. */
  stats() {
    if (!this.o.settings.on) return { on: false }
    const n = (sql: string) => (this.db.query(sql).get() as { n: number }).n
    return {
      on: this.on,
      lines: n("SELECT COUNT(*) AS n FROM name_lines WHERE type != 'whole'"),
      lines_whole: n("SELECT COUNT(*) AS n FROM name_lines WHERE type = 'whole'"),
      name_clips: n('SELECT COUNT(*) AS n FROM name_clips WHERE file IS NOT NULL'),
      joined: n('SELECT COUNT(*) AS n FROM name_joined'),
      chars: n('SELECT COALESCE(SUM(chars), 0) AS n FROM name_lines') + n('SELECT COALESCE(SUM(chars), 0) AS n FROM name_clips'),
      today: this.spentToday(),
      daily: this.o.settings.daily,
    }
  }
}
