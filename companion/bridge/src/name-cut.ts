// Where to cut a take of a name line (docs/plans/05-built/audio-names.md): the splice test's cuts of 2026-11-04, seeing only the
// take itself and its timestamps, never a whole-voiced line, which a new learner won't have. The timestamps alone don't cut
// mid-line: at a comma the voice runs through and the next letters come up to 280 ms late, so they are a first guess, and the
// sound decides. Plain samples in, seconds out; the ffmpeg around it is in name-store.ts.
import type { Alignment } from './voice'

/** The sample rate takes are read at: ElevenLabs' (mp3_44100_128). */
export const SR = 44_100
/** Seconds between two frames of a level track. */
export const HOP = 0.005

/** Filter sections (b0 b1 b2, 1 a1 a2) of a 4th-order Butterworth band-pass at 44.1 kHz, as the test's scipy design gave them. */
type Sos = readonly (readonly number[])[]
const MID_BAND: Sos = [[0.000987594722589, 0.00197518944518, 0.000987594722589, 1, -1.47302100311, 0.578264893128], [1, 2, 1, 1, -1.57658569235, 0.782114232918], [1, -2, 1, 1, -1.79898837541, 0.815914017345], [1, -2, 1, 1, -1.93740645963, 0.947659674214]]
const LOW_BAND: Sos = [[7.41992856364e-7, 1.48398571273e-6, 7.41992856364e-7, 1, -1.91458135865, 0.917340115478], [1, 2, 1, 1, -1.95687981102, 0.961594223241], [1, -2, 1, 1, -1.97572809303, 0.975954467369], [1, -2, 1, 1, -1.99326779752, 0.993404457672]]
const HIGH_BAND: Sos = [[0.0175352145922, 0.0350704291843, 0.0175352145922, 1, -0.564499883092, 0.316947734055], [1, 2, 1, 1, -1.15651411319, 0.474712050957], [1, -2, 1, 1, -0.289222749055, 0.654913692576], [1, -2, 1, 1, -1.57823783681, 0.811626690201]]

function filterOnce(x: Float64Array, sos: Sos): Float64Array {
  let y = x
  for (const [b0, b1, b2, , a1, a2] of sos) {
    const out = new Float64Array(y.length)
    let z1 = 0
    let z2 = 0
    for (let i = 0; i < y.length; i++) {
      const v = y[i]
      const o = b0 * v + z1
      z1 = b1 * v - a1 * o + z2
      z2 = b2 * v - a2 * o
      out[i] = o
    }
    y = out
  }
  return y
}

/** [x] through [sos], forwards and backwards (no phase shift), its ends mirrored so the filter's start-up stays out of the take. */
function zeroPhase(x: Float32Array, sos: Sos): Float64Array {
  const pad = Math.min(x.length - 1, 4_000)
  const e = new Float64Array(x.length + 2 * pad)
  for (let i = 0; i < pad; i++) {
    e[i] = 2 * x[0] - x[pad - i]
    e[e.length - 1 - i] = 2 * x[x.length - 1] - x[x.length - 1 - pad + i]
  }
  e.set(x, pad)
  const f = filterOnce(e, sos).reverse()
  return filterOnce(f, sos).reverse().subarray(pad, pad + x.length)
}

/** The level of [x] in dBFS: a [win] s window every [HOP] s, frame k centred at k × HOP. */
export function rmsDb(x: ArrayLike<number>, win = 0.02): Float32Array {
  const n = Math.trunc(win * SR)
  const h = Math.trunc(HOP * SR)
  const half = n >> 1
  const len = x.length + 2 * half
  const sum = new Float64Array(len + 1)
  for (let i = 0; i < len; i++) {
    const k = i - half
    const v = k >= 0 && k < x.length ? x[k] : 0
    sum[i + 1] = sum[i] + v * v
  }
  const m = Math.max(0, Math.trunc((len - n) / h) + 1)
  const out = new Float32Array(m)
  for (let k = 0; k < m; k++) out[k] = 10 * Math.log10((sum[k * h + n] - sum[k * h]) / n + 1e-12)
  return out
}

/** The [q]th percentile of [a], between the two nearest values. */
export function percentile(a: ArrayLike<number>, q: number): number {
  const s = Float64Array.from(a as ArrayLike<number>).sort()
  if (!s.length) return -90
  const at = (q / 100) * (s.length - 1)
  const i = Math.floor(at)
  return i + 1 < s.length ? s[i] + (s[i + 1] - s[i]) * (at - i) : s[i]
}

/** What counts as a pause in this take: 28 dB under its loud frames, at least −55 dBFS. */
export const speechThreshold = (db: ArrayLike<number>) => Math.max(-55, percentile(db, 95) - 28)

export type Span = [start: number, end: number]

/** The pauses of a level track, in seconds: where it stays under [thr] for [minLen]. */
export function pauses(db: ArrayLike<number>, thr: number, minLen = 0.03): Span[] {
  const out: Span[] = []
  let a = -1
  for (let i = 0; i < db.length; i++) {
    const quiet = db[i] < thr
    if (quiet && a < 0) a = i
    if (!quiet && a >= 0) {
      if ((i - a) * HOP >= minLen) out.push([a * HOP, i * HOP])
      a = -1
    }
  }
  if (a >= 0 && (db.length - a) * HOP >= minLen) out.push([a * HOP, db.length * HOP])
  return out
}

const frame = (t: number) => Math.round(t / HOP)

/** The first pause of [minLen] from [t] on, its end being the take's for one reaching it. */
function nextPause(db: ArrayLike<number>, thr: number, t: number, minLen = 0.04): Span | undefined {
  return pauses(db, thr, minLen).find(([p, q]) => q > t && p >= t - 0.02)
}

/** The next sound after [t], reaching back while the level stays within 12 dB under the threshold (a soft h, v, s). */
function onsetAfter(db: ArrayLike<number>, thr: number, t: number): number | undefined {
  let k = frame(t)
  while (k < db.length && db[k] < thr) k++
  if (k >= db.length) return undefined
  let j = k
  while (j > frame(t) && db[j - 1] > thr - 12 && (k - j) * HOP < 0.04) j--
  return j * HOP
}

/** The level tracks a cut reads: the whole sound, and the mid band's and the low band's. */
export type Tracks = { db: Float32Array; mid: Float32Array; low: Float32Array }

export const tracks = (x: Float32Array): Tracks => ({ db: rmsDb(x), mid: rmsDb(zeroPhase(x, MID_BAND), 0.015), low: rmsDb(zeroPhase(x, LOW_BAND), 0.015) })

/** A take's alignment with its characters as one string, the last spelling of [word] in it. */
function spanOf(a: Alignment | undefined, word: string): { start: number; end: number; at: number } | undefined {
  const ch = a?.characters
  const s = a?.character_start_times_seconds
  const e = a?.character_end_times_seconds
  if (!ch?.length || s?.length !== ch.length || e?.length !== ch.length) return undefined
  const at = ch.join('').lastIndexOf(word)
  if (at < 0) return undefined
  return { start: s[at], end: e[at + word.length - 1], at }
}

/** Where the filler stands in a take: its span, and what stands either side of it. */
export type FillerCut = {
  /** The end of what is said before it (undefined when the filler opens the take), and the pause from there to the name. */
  aEnd?: number
  aGap: number
  nameStart: number
  nameEnd: number
  /** Where what is said after it starts (undefined when nothing follows). */
  bStart?: number
}

/**
 * A filler with a stop in it ("Marko", the k at [closure]). The closure is the anchor: a deep short dip the timestamps sit near
 * even when they are 200 ms off at the name's start. Back from it the a, then the M: mid band down, low band up. On from it the
 * vowel and the pause. Undefined when no such dip stands near the timestamps.
 */
export function closureCut(x: Float32Array, a: Alignment, word: string, tr: Tracks, closure = 3): FillerCut | undefined {
  const { db, mid } = tr
  const span = spanOf(a, word)
  if (!span) return undefined
  const kt = a.character_start_times_seconds![span.at + closure]
  const top = percentile(mid, 95)
  const lo = frame(Math.max(0, kt - 0.28))
  const hi = Math.min(frame(kt + 0.22), mid.length - 2)
  const runs: [number, number, number][] = []
  for (let k = lo + 1; k < hi; k++) {
    if (!(mid[k] < top - 32 && mid[k] <= mid[k - 1] && mid[k] <= mid[k + 1])) continue
    let s = k
    let e = k
    while (s > 0 && mid[s - 1] < mid[k] + 16) s--
    while (e < mid.length - 1 && mid[e + 1] < mid[k] + 16) e++
    if ((e - s) * HOP >= 0.02 && (e - s) * HOP <= 0.16 && !runs.some(r => r[0] === s && r[1] === e)) runs.push([s, e, k])
  }
  if (!runs.length) return undefined
  const [s0, e0] = runs.reduce((b, r) => (Math.abs(r[2] * HOP - kt) < Math.abs(b[2] * HOP - kt) ? r : b))
  const cs = s0 * HOP
  const ce = e0 * HOP
  const thr = speechThreshold(db)
  // right: the vowel and the pause after it
  const p = nextPause(db, thr, ce + 0.02)
  const end = x.length / SR
  const nameEnd = p ? p[0] : end
  const bStart = p && p[1] < end - 0.02 ? onsetAfter(db, thr, p[1] - 0.01) : undefined
  // left: the vowel's rise out of the M, the steepest rise of the mid band before the closure
  const w0 = frame(Math.max(0, cs - 0.3))
  const w1 = frame(cs - 0.05)
  if (w1 - w0 < 4) return undefined
  const sm = new Float32Array(mid.length)
  for (let i = 0; i < mid.length; i++) sm[i] = ((mid[i - 1] ?? 0) + mid[i] + (mid[i + 1] ?? 0)) / 3
  let r = w0
  let best = -Infinity
  for (let i = 0; i < w1 - w0 - 3; i++) {
    const d = sm[w0 + 3 + i] - sm[w0 + i]
    if (d > best) {
      best = d
      r = w0 + i + 2
    }
  }
  // back through the M: how far: a vowel before it shows where the M starts, and a comma can stretch it to 0.26 s; after a
  // nasal nothing shows it, so the word before keeps its own n
  const before = a.characters!.slice(Math.max(0, span.at - 3), span.at)
  const last = [...before].reverse().find(c => /\p{L}/u.test(c)) ?? ''
  const limit = 'nm'.includes(last.toLowerCase()) && last ? 0.13 : 0.26
  const v0 = Math.max(0, r - Math.trunc(limit / HOP))
  let kmin = v0
  for (let i = v0; i <= r; i++) if (sm[i] < sm[kmin]) kmin = i
  let m = kmin
  while (m > v0 && sm[m - 1] < sm[kmin] + 8 && db[m - 1] > thr) m--
  const first = m * HOP
  // the first sound of the take is the M itself when the name opens the line
  const head = onsetAfter(db, thr, 0) ?? 0
  if (span.at === 0 || first - head < 0.05) return { aGap: 0, nameStart: head, nameEnd, bStart }
  // a real pause before the M: the piece ends where the pause starts, and the pause is the line's own
  const prev = pauses(db, thr, 0.03).filter(([q0, q1]) => q0 > head + 0.05 && Math.abs(q1 - first) < 0.07)
  if (prev.length) {
    const q = prev[prev.length - 1]
    return { aEnd: q[0], aGap: first - q[0], nameStart: first, nameEnd, bStart }
  }
  return { aEnd: first, aGap: 0, nameStart: first, nameEnd, bStart }
}

/** A filler without a stop to anchor on ("Ana"): only its end and what follows, which a line that needs no left edge reads. */
export function rightCut(x: Float32Array, a: Alignment, word: string, tr: Tracks): FillerCut | undefined {
  const span = spanOf(a, word)
  if (!span) return undefined
  const { db } = tr
  const thr = speechThreshold(db)
  const q = nextPause(db, thr, Math.max(0.1, span.end - 0.12))
  const end = x.length / SR
  return { aGap: 0, nameStart: onsetAfter(db, thr, 0) ?? 0, nameEnd: q ? q[0] : end, bStart: q && q[1] < end - 0.02 ? onsetAfter(db, thr, q[1] - 0.01) : undefined }
}

/**
 * A name's edges in a take of its carrier sentence. Start: the first sound when it opens the sentence, else after the pause
 * behind the prefix's comma, else the voicing onset after the voiceless prefix (the low band rising). End: the pause nearest
 * the timestamps' end of the name, 50 ms or more, so a closure inside the name (Matej's t, Aleksandra's k, s, d) isn't taken for it.
 */
export function nameCut(x: Float32Array, a: Alignment, name: string, tr: Tracks): Span | undefined {
  const span = spanOf(a, name)
  if (!span) return undefined
  const { db, low } = tr
  const thr = speechThreshold(db)
  const all = pauses(db, thr, 0.05)
  let ps = all.filter(q => q[0] > span.start + 0.05)
  const near = ps.filter(q => Math.abs(q[0] - span.end) < 0.35)
  if (near.length) ps = near
  const end = ps.length ? ps.reduce((b, q) => (Math.abs(q[0] - span.end) < Math.abs(b[0] - span.end) ? q : b))[0] : x.length / SR
  if (span.at === 0) return [onsetAfter(db, thr, 0) ?? 0, end]
  // after the prefix's comma the voice pauses: the name starts where that pause ends
  const before = all.filter(q => span.start - 0.35 < q[1] && q[1] < end - 0.12)
  if (before.length) {
    const q = before[before.length - 1]
    return [onsetAfter(db, thr, q[1] - 0.01) ?? q[1], end]
  }
  // no pause: the frication of the voiceless prefix is over and the low band comes up
  const hi = rmsDb(zeroPhase(x, HIGH_BAND), 0.015)
  const s = frame(Math.max(0, span.start - 0.32))
  const e = frame(Math.min(span.start + 0.18, end - 0.1))
  let kp = s
  for (let i = s; i < e; i++) if (hi[i] > hi[kp]) kp = i
  let k = kp
  while (k < e && hi[k] > hi[kp] - 15) k++
  let peak = -Infinity
  for (let i = k; i < frame(end) && i < low.length; i++) peak = Math.max(peak, low[i])
  while (k < frame(end) && low[k] < peak - 18) k++
  return [k * HOP, end]
}

/** A phrase in a take of its carrier sentence: from its first sound to the pause after the name. */
export function phraseCut(x: Float32Array, a: Alignment, name: string, tr: Tracks): Span | undefined {
  const c = nameCut(x, a, name, tr)
  if (!c) return undefined
  return [onsetAfter(tr.db, speechThreshold(tr.db), 0) ?? 0, c[1]]
}

/** The pause nearest the moment [t] something starts, 60 ms or more, in a line: where a sentence before it ends. */
export function pauseBefore(tr: Tracks, t: number): Span | undefined {
  const near = pauses(tr.db, speechThreshold(tr.db), 0.06).filter(q => q[0] > 0.1 && Math.abs(q[1] - t) < 0.5)
  return near.length ? near.reduce((b, q) => (Math.abs(q[1] - t) < Math.abs(b[1] - t) ? q : b)) : undefined
}

/** [y] with fades at its ends of [fin] and [fout] seconds, a sine's square: a cut edge doesn't click. */
export function fades(y: Float32Array, fin = 0.005, fout = 0.01): Float32Array {
  const out = Float32Array.from(y)
  const a = Math.min(Math.trunc(fin * SR), out.length)
  const b = Math.min(Math.trunc(fout * SR), out.length)
  for (let i = 0; i < a; i++) out[i] *= Math.sin((Math.PI / 2) * (i / Math.max(1, a - 1))) ** 2
  for (let i = 0; i < b; i++) out[out.length - b + i] *= Math.cos((Math.PI / 2) * (i / Math.max(1, b - 1))) ** 2
  return out
}

/** The level of a piece's voice, dB: the mean power of its frames within 20 dB of its loudest. */
export function activeDb(x: Float32Array): number {
  if (x.length < Math.trunc(0.02 * SR)) return -90
  const d = rmsDb(x)
  const top = percentile(d, 98)
  let sum = 0
  let n = 0
  for (const v of d) {
    if (v > top - 20) {
      sum += 10 ** (v / 10)
      n++
    }
  }
  return n ? 10 * Math.log10(sum / n) : -90
}

/** [x] between two times, as samples. */
export const slice = (x: Float32Array, from: number, to: number) => x.subarray(Math.max(0, Math.trunc(from * SR)), Math.min(x.length, Math.trunc(to * SR)))

/**
 * How high [x] is said, in Hz: the median of its voiced 10 ms frames (normalized autocorrelation over 70–500 Hz); 0 when
 * nothing in it is voiced. Of two takes of a name the lower is the better: the test's one rule for picking a take without
 * any whole line to compare with.
 */
export function medianPitch(x: Float32Array): number {
  const win = Math.trunc(0.04 * SR)
  const step = Math.trunc(0.01 * SR)
  const minLag = Math.trunc(SR / 500)
  const maxLag = Math.trunc(SR / 70)
  const f: number[] = []
  for (let s = 0; s + win + maxLag < x.length; s += step) {
    let e0 = 0
    for (let i = 0; i < win; i++) e0 += x[s + i] * x[s + i]
    if (e0 < 1e-5) continue
    let bestLag = 0
    let best = 0.5
    for (let lag = minLag; lag <= maxLag; lag++) {
      let c = 0
      let e1 = 0
      for (let i = 0; i < win; i++) {
        c += x[s + i] * x[s + i + lag]
        e1 += x[s + i + lag] * x[s + i + lag]
      }
      const r = c / Math.sqrt(e0 * e1 + 1e-12)
      if (r > best) {
        best = r
        bestLag = lag
      }
    }
    if (bestLag) f.push(SR / bestLag)
  }
  return f.length ? percentile(f, 50) : 0
}

/**
 * The largest sample step at [joins] of [x], in dB over the step the signal takes around it (the test's measure of a click):
 * 0 is a step like its neighbours', a hard cut at a vowel's peak +20 and more, a faded join far under 0. Floored at −20.
 */
export function clickDb(x: ArrayLike<number>, joins: number[]): number {
  let worst = -20
  const w = Math.trunc(0.005 * SR)
  for (const k of joins) {
    if (k <= 0 || k >= x.length) continue
    const d = Math.abs(x[k] - x[k - 1])
    const steps: number[] = []
    for (let i = Math.max(1, k - w); i < Math.min(x.length, k + w); i++) {
      const s = Math.abs(x[i] - x[i - 1])
      if (s > 1e-7) steps.push(s)
    }
    const typical = steps.length ? percentile(steps, 50) : 1e-5
    worst = Math.max(worst, 20 * Math.log10(Math.max(d, 1e-7) / Math.max(typical, 1e-5)))
  }
  return worst
}

/** The moment the last sound of a take ends. */
function lastSound(db: ArrayLike<number>, thr: number): number {
  for (let k = db.length - 1; k >= 0; k--) if (db[k] >= thr) return (k + 1) * HOP
  return 0
}

/** A line's take, cut: what is kept before and after the filler (or the phrase holding it), and the pauses that stand beside it. */
export type LineCut = { head?: Span; gapLeft: number; filler: Span; gapRight: number; tail?: Span }

/** Seconds kept before a take's first sound and after its last, as a whole clip's edges are (voice.ts EDGES). */
const KEEP = { lead: 0.05, tail: 0.1 }
/** The shortest piece worth a cut, and how far from the timestamps a pause may stand and still be the one meant. */
const TRUST = { piece: 0.12, reach: 0.35, pause: 0.03 }

/**
 * Where to cut [x], a take of a line said with [filler], or why the cut can't be trusted (the line is then voiced whole):
 * the filler isn't in the timestamps, no pause stands where the line needs one, or the pause found is too far from where the
 * timestamps put the next word to be the one meant. [anchored]: the filler has a stop to anchor on ([closureCut]); only then is
 * its left edge known, which a 'splice' needs.
 */
export function cutLine(x: Float32Array, a: Alignment, line: { said: string; type: string; head: string; tail: string }, filler: string, tr: Tracks, anchored: boolean): LineCut | { why: string } {
  const c = anchored ? closureCut(x, a, filler, tr) : rightCut(x, a, filler, tr)
  if (!c) return { why: 'no edge of the filler in the take' }
  const said = (a.characters ?? []).join('')
  const starts = a.character_start_times_seconds ?? []
  const fillerAt = said.lastIndexOf(filler)
  const thr = speechThreshold(tr.db)
  const end = x.length / SR
  let tail: Span | undefined
  let gapRight = 0
  if (line.tail) {
    if (c.bStart === undefined) return { why: 'no pause after the filler' }
    const at = said.indexOf(line.tail.slice(0, 12), fillerAt + filler.length)
    if (at < 0) return { why: 'the words after the filler are not in the timestamps' }
    if (Math.abs(c.bStart - starts[at]) > TRUST.reach) return { why: 'the pause after the filler is far from the next word' }
    tail = [c.bStart, Math.min(end, lastSound(tr.db, thr) + KEEP.tail)]
    if (tail[1] - tail[0] < TRUST.piece) return { why: 'too little after the filler' }
    gapRight = c.bStart - c.nameEnd
  }
  let head: Span | undefined
  let gapLeft = 0
  let nameStart = c.nameStart
  if (line.head) {
    const lead = Math.max(0, (onsetAfter(tr.db, thr, 0) ?? 0) - KEEP.lead)
    if (line.type === 'splice') {
      if (!anchored || c.aEnd === undefined || c.aGap < TRUST.pause) return { why: 'no pause before the name' }
      head = [lead, c.aEnd]
      gapLeft = c.aGap
    } else {
      // the sentence before the name or its phrase: its end is a real pause near where the next one starts
      const said0 = said.indexOf(line.head.slice(-12))
      const next = said0 < 0 ? -1 : said0 + Math.min(12, line.head.length)
      const first = next < 0 ? undefined : starts[Math.min(starts.length - 1, next + (said.slice(next).match(/^\s*/)?.[0].length ?? 0))]
      const q = first === undefined ? undefined : pauseBefore(tr, first)
      if (!q) return { why: 'no pause before the name' }
      head = [lead, q[0]]
      gapLeft = q[1] - q[0]
      nameStart = q[1]
    }
    if (head[1] - head[0] < TRUST.piece) return { why: 'too little before the name' }
  }
  if (c.nameEnd - nameStart < 0.1) return { why: 'the filler is too short' }
  return { head, gapLeft, filler: [nameStart, c.nameEnd], gapRight, tail }
}
