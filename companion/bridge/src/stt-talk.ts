// Speech recognition in a talk (docs/plans/05-built/stt-talk.md): what the learner says in a free conversation, heard
// twice by the node's Whisper, with the conversation as its context (the prompt) and without (plain), and the two
// readings weighed: which one lands in the draft, whether it is sure enough to send by itself, the other readings for
// the tutor (the plain one, and the one with a heard non-word matched to a word of the conversation: amigo → amico), and
// a take of noise heard as nothing.
// Pure; features/stt.ts runs the two hearings and this.
import type { SttResult, SttWord } from './features/stt'

/** The prompt's limit: the worker takes 300 characters (Whisper itself keeps its last 223 tokens). */
export const MAX_PROMPT = 300

/** What a talk tells the recognizer, in the course's language: what came just before the learner's line. */
export type TalkContext = {
  /** The last lines said, oldest first: the character's last line or two. */
  said: string[]
  /** The scenario's useful phrases and goal words. */
  words: string[]
  /** The learner's name and the names in the scene. */
  names: string[]
}

const ENDS = /[.!?…:;»"”)]$/
const squash = (s: string) => s.replace(/\s+/g, ' ').trim()

/**
 * Whisper's initial prompt for a talk: a short transcript of what came before, as if the recording went on from it. The
 * names first (they spell the people of the scene), then the useful phrases, then the character's last lines, which
 * Whisper weighs most, being nearest. Within [max] characters: phrases go first, then older lines, then the start of
 * the oldest line kept.
 */
export function talkPrompt(c: TalkContext, max = MAX_PROMPT): string {
  const names = [...new Set(c.names.map(squash).filter(Boolean))]
  // a line as a sentence of the transcript: Whisper writes as its prompt does
  const said = c.said.map(squash).filter(Boolean).map(s => (ENDS.test(s) ? s : `${s}.`))
  const words = [...new Set(c.words.map(squash).filter(Boolean))].filter(w => !said.some(s => s.toLowerCase().includes(w.toLowerCase().replace(/[.!?…]+$/, ''))))
  const sentence = (parts: string[]) => {
    // phrases that are sentences stay as they are; bare words go in a list ("Le pecore, i campanacci.")
    const out: string[] = []
    let list: string[] = []
    const flush = () => {
      if (list.length) out.push(`${list.join(', ')}.`.replace(/^\p{Ll}/u, m => m.toUpperCase()))
      list = []
    }
    for (const p of parts) {
      if (ENDS.test(p)) {
        flush()
        out.push(p)
      } else list.push(p)
    }
    flush()
    return out.join(' ')
  }
  const head = names.length ? `${names.join(', ')}.` : ''
  const join = (...parts: string[]) => parts.filter(Boolean).join(' ')
  let lines = said.slice(-2)
  let phrases = words
  const build = () => join(head, sentence(phrases), lines.join(' '))
  let p = build()
  while (p.length > max && phrases.length) {
    phrases = phrases.slice(0, -1)
    p = build()
  }
  while (p.length > max && lines.length > 1) {
    lines = lines.slice(1)
    p = build()
  }
  if (p.length > max) {
    // the last line alone is too long: its end, from a word's start
    const last = lines[lines.length - 1] ?? ''
    const cut = last.slice(Math.max(0, last.length - (max - head.length - 1)))
    p = join(head, cut.replace(/^\S*\s/, ''))
  }
  return p.slice(-max)
}

// --- the two readings ------------------------------------------------------------------------------------------------

/** A word as readings are compared: lower case, without accents, apostrophes or punctuation ("Perché?" → "perche"). */
export function key(w: string): string {
  return w.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '')
}

/** A text's words as keys; an elided article or preposition is a word of its own ("l'acqua" → l, acqua). */
export function keys(text: string): string[] {
  return text
    .normalize('NFC')
    .split(/[\s'’ʼ]+/)
    .map(key)
    .filter(Boolean)
}

/** The readings say the same words (spacing aside: "buona notte" is "buonanotte"). */
export function agree(a: string, b: string): boolean {
  const ka = keys(a).join('')
  return ka.length > 0 && ka === keys(b).join('')
}

const wordsOf = (r: SttResult): SttWord[] => r.segments.flatMap(s => s.words)

/** Whisper's mean word probability, 0..1 (null: none reported). */
export function meanProb(r: SttResult): number | null {
  const p = wordsOf(r)
    .filter(w => key(w.word))
    .map(w => w.prob)
    .filter((x): x is number => x !== null)
  return p.length ? p.reduce((a, b) => a + b, 0) / p.length : null
}

/**
 * A reading Whisper made up rather than heard: a word or a phrase over and over ("non, non, non, non", "E tu? E tu? E
 * tu?", "la mia cosa è che la mia cosa è che …"), a letter held ("Mmmmmmmm"), more words than [seconds] of audio can
 * hold (four a second and three more), a web address, or one of its stock lines for a silence (the end of a video:
 * "Grazie a tutti.", "Sottotitoli creati dalla comunità Amara.org").
 */
export function madeUp(text: string, language: string, seconds: number | null = null): boolean {
  if (/(\p{L})\1{4,}/u.test(text) || /\bwww\.|https?:|\p{L}\.(com|org|net|info)\b/iu.test(text)) return true
  const k = keys(text)
  if (!k.length) return false
  if (seconds !== null && seconds > 0 && k.length > 3 + 4 * seconds) return true
  for (let n = 1; n <= 6; n++) {
    for (let i = 0; i + n * 3 <= k.length; i++) {
      const g = k.slice(i, i + n).join(' ')
      let r = 1
      while (k.slice(i + r * n, i + (r + 1) * n).join(' ') === g) r++
      if (r >= (n === 1 ? 4 : 3)) return true
    }
  }
  const whole = k.join(' ')
  return (STOCK[language] ?? []).some(s => whole === s || (s.split(' ').length >= 3 && whole.includes(s)))
}

/**
 * What Whisper writes for a silence or noise, learned from its subtitles: whole takes that are only this are noise.
 * Only lines a learner wouldn't say as a whole line in a talk ("Grazie." alone stays a line: a learner says it).
 */
const STOCK: Record<string, string[]> = {
  it: [
    'grazie a tutti', 'grazie per la visione', 'grazie per l attenzione', 'grazie per aver guardato', 'guardato il video',
    'sottotitoli creati dalla comunita amara org', 'sottotitoli e revisione a cura di qtss', 'sottotitoli a cura di',
    'iscriviti al canale', 'a tutti', 'alla prossima',
  ],
  sl: ['hvala za ogled', 'hvala vsem', 'podnapisi', 'naroci se na kanal'],
  de: ['vielen dank fur ihre aufmerksamkeit', 'untertitel im auftrag des zdf', 'untertitel der amara org community', 'danke furs zuschauen'],
  en: ['thank you for watching', 'thanks for watching', 'subtitles by the amara org community'],
}

/**
 * The [prompt]'s words the reading has in a row, as a share of the reading's words: 1 when the reading is the prompt's
 * own text (or a stretch of it), as a prompt makes Whisper write over a silence.
 */
export function copied(text: string, prompt: string): number {
  const k = keys(text)
  const p = keys(prompt)
  if (!k.length || !p.length) return 0
  let best = 0
  for (let i = 0; i < p.length; i++) {
    let n = 0
    while (n < k.length && i + n < p.length && p[i + n] === k[n]) n++
    best = Math.max(best, n)
  }
  return best / k.length
}

/**
 * The least lemmas a dictionary needs to tell a near-miss from a word: Italian's Wiktionary has 201k, Slovene's in the
 * repository 5.8k ("kam" is missing): with that one, a real word it lacks would be "matched" to the conversation.
 */
export const MIN_LEMMAS = 50_000

/** Whether a word as heard is one of the language (the dictionary's; null when there is none: nothing is matched then). */
export type Knows = ((form: string) => boolean) | null

/** The context's words by key, as written in it (names keep their capital): what a non-word may be matched to. */
export function contextWords(c: TalkContext): Map<string, string> {
  const out = new Map<string, string>()
  for (const t of [...c.names, ...c.said, ...c.words]) {
    for (const w of t.split(/[\s'’ʼ]+/)) {
      const core = w.match(/[\p{L}\p{M}][\p{L}\p{M}\p{N}-]*[\p{L}\p{M}]|[\p{L}\p{M}]/u)?.[0]
      if (core && key(core).length >= 2 && !out.has(key(core))) out.set(key(core), core)
    }
  }
  return out
}

/** Letters apart, by edits (Levenshtein). */
export function distance(a: string, b: string): number {
  if (a === b) return 0
  const d = Array.from({ length: b.length + 1 }, (_, j) => j)
  for (let i = 1; i <= a.length; i++) {
    let prev = d[0]
    d[0] = i
    for (let j = 1; j <= b.length; j++) {
      const t = d[j]
      d[j] = Math.min(d[j] + 1, d[j - 1] + 1, prev + (a[i - 1] === b[j - 1] ? 0 : 1))
      prev = t
    }
  }
  return d[b.length]
}

/** A heard word matched to the conversation's: [from] as heard, [to] as the context has it. */
export type Snap = { from: string; to: string }

/**
 * The reading with its near-misses matched to the conversation: a heard word the language doesn't have ([knows] says
 * no, and it isn't a word of the context) becomes the one context word one edit away (two for words of seven letters
 * or more; "amigo" → "amico"), when exactly one is that close. A real word is never changed into another, nor is a
 * word of under three letters, an elided one ("dov'") or a number. [weigh] gives the matched reading to the tutor as an
 * alternative, never to the draft: on the test set every match changed a word the learner hadn't meant, or one they
 * had said wrong ("Uovi?" → "Uova", docs/plans/05-built/stt-talk.md, "Near-misses").
 */
export function snap(text: string, ctx: Map<string, string>, knows: Knows): { text: string; snaps: Snap[] } {
  if (!knows || !ctx.size) return { text, snaps: [] }
  const snaps: Snap[] = []
  const out = text.replace(/[\p{L}\p{M}]+(?:[-][\p{L}\p{M}]+)*(['’ʼ])?/gu, (w, elided) => {
    if (elided) return w
    const k = key(w)
    if (k.length < 3 || ctx.has(k) || knows(w)) return w
    const room = k.length >= 7 ? 2 : 1
    let best: string | null = null
    let bestD = Infinity
    let ties = 0
    for (const [ck, cw] of ctx) {
      if (Math.abs(ck.length - k.length) > room) continue
      const d = distance(k, ck)
      if (d > room) continue
      if (d < bestD) {
        bestD = d
        best = cw
        ties = 0
      } else if (d === bestD && cw !== best) ties++
    }
    if (!best || ties) return w
    // cased as heard ("Amigo" → "Amico"), a name as the context writes it
    const to = /^\p{Lu}/u.test(best) ? best : /^\p{Lu}/u.test(w) ? best[0].toUpperCase() + best.slice(1) : best
    snaps.push({ from: w, to })
    return to
  })
  return { text: out, snaps }
}

/** How a take is weighed (the defaults: docs/plans/05-built/stt-talk.md, "Measurements"). */
export type Weighing = {
  /** The plain reading wins a disagreement when its words fit the audio this much better (mean probability). */
  margin: number
  /** Sent by itself only with every word at least this sure. */
  sureWord: number
  /** Less voiced time than this (seconds, the worker's "voice") is no voice: nothing heard. */
  minVoiced: number
  /** The prompted reading copying its prompt is believed under this mean probability only when the plain one agrees. */
  copyProb: number
  /** In a disagreement, the reading with fewer words the language doesn't have wins (with a dictionary). */
  words: boolean
}

export const WEIGHING: Weighing = { margin: 0.1, sureWord: 0.85, minVoiced: 0.1, copyProb: 0.5, words: true }

/** The worker's measure of the take's voice ([voice_activity] in companion/stt-local/server.py): seconds. */
export type Voice = { voiced: number; loud: number }

/** What a talk's take was heard as: the reading that goes to the draft and what the tutor gets besides. */
export type TalkHearing = {
  /** What lands in the draft; empty when nothing was heard ([noise]). */
  text: string
  /** The reading [text] comes from: its words with their probabilities (for the draft's unclear words). */
  result: SttResult
  /** Which reading: both said the same, the one with the context, or the plain one. */
  by: 'both' | 'context' | 'plain' | null
  /**
   * The other readings, when they say other words: for the tutor (heard may be a mishearing of them). The plain
   * reading (or the prompted one, when the plain one was chosen), then the chosen one with its near-misses matched.
   */
  alternatives: string[]
  /** The heard non-words one edit from a word of the conversation, and that word ([snap]): the matched alternative's. */
  near: Snap[]
  /**
   * Sure enough to send by itself: both readings say the same words, each one clear and a word of the language or of
   * the talk, and the worker measured a voice (without that measure nothing is sure: a room's noise heard as "Grazie."
   * agrees in both readings).
   */
  sure: boolean
  /** No line: no voice, nothing but noise, Whisper's stock lines or a sound ("Ooooh"); nothing goes to the draft. */
  noise: boolean
}

/** A sound rather than a word: only vowels, h and m ("ooo", "Ooooh", "mmm", "hmm"), at least three letters. */
const SOUND = /^[aeiouyhm]{3,}$/

/**
 * The two readings of a take weighed: [prompted] (with the talk's context as the prompt, [prompt]) and [plain]
 * (without; null when its hearing failed), with the worker's measure of the voice in it ([voice], when it sent one).
 * Noise when there was no voice or neither reading heard a line (empty, made up, sounds, or the prompted one copying
 * its prompt while the plain one heard nothing real). The same words in both is the prompted reading (it spells as the
 * conversation does). Otherwise the one with fewer words the language doesn't have (with a dictionary), then the one
 * whose words fit the audio better: the word probabilities are Whisper's of the text without the prompt, so the two
 * compare; the plain one needs [Weighing.margin] more, and a copy of the prompt loses. The other reading goes along as an alternative, and so does the chosen one with its near-misses matched to
 * the context ([snap]); the draft keeps what was heard.
 */
export function weigh(
  prompted: SttResult, plain: SttResult | null, c: TalkContext, prompt: string, language: string, knows: Knows,
  voice: Voice | null = null, o: Weighing = WEIGHING,
): TalkHearing {
  const ctx = contextWords(c)
  const isWord = (w: string) => {
    const k = key(w)
    return k.length < 2 || ctx.has(k) || (knows ? knows(w.replace(/^[^\p{L}]+|[^\p{L}]+$/gu, '')) : !SOUND.test(k))
  }
  const sounds = (r: SttResult) => keys(r.text).every(k => SOUND.test(k) && !isWord(k))
  const nonWords = (r: SttResult) => wordsOf(r).filter(w => key(w.word).length >= 2 && !/['’ʼ]$/.test(w.word) && !isWord(w.word)).length
  const real = (r: SttResult | null): r is SttResult => !!r && keys(r.text).length > 0 && !madeUp(r.text, language, r.duration) && !sounds(r)
  const nothing = (): TalkHearing => ({ text: '', result: { ...prompted, text: '', segments: [] }, by: null, alternatives: [], near: [], sure: false, noise: true })
  if (voice && voice.voiced < o.minVoiced) return nothing()
  const pOk = real(prompted)
  const nOk = real(plain)
  const both = pOk && nOk && agree(prompted.text, plain.text)
  // the prompt's own words over a sound or a mumble: believed only when they fit the audio (their probability is
  // Whisper's without the prompt) or the plain hearing heard them too
  const pulled = pOk && !both && copied(prompted.text, prompt) >= 0.99 && (meanProb(prompted) ?? 0) < o.copyProb
  if (!both && (!pOk || pulled) && !nOk) return nothing()
  let chosen: SttResult
  let other: SttResult | null
  let by: TalkHearing['by']
  if (both) {
    chosen = prompted
    other = null
    by = 'both'
  } else if (!nOk) {
    chosen = prompted
    other = null
    by = 'context'
  } else if (!pOk || pulled || (o.words && knows && nonWords(plain) < nonWords(prompted)) ||
    (!(o.words && knows && nonWords(plain) > nonWords(prompted)) && (meanProb(plain) ?? 0) > (meanProb(prompted) ?? 0) + o.margin)) {
    chosen = plain
    other = pOk && !pulled ? prompted : null
    by = 'plain'
  } else {
    chosen = prompted
    other = plain
    by = 'context'
  }
  const matched = snap(chosen.text, ctx, knows)
  const words = wordsOf(chosen).filter(w => key(w.word).length > 0)
  const sure = by === 'both' && !!voice && matched.snaps.length === 0 && words.length > 0 && words.every(w => (w.prob ?? 0) >= o.sureWord && isWord(w.word))
  const alternatives: string[] = []
  for (const t of [other && real(other) ? other.text : null, matched.snaps.length ? matched.text : null]) {
    if (t && !agree(t, chosen.text) && !alternatives.some(a => agree(a, t))) alternatives.push(t.trim())
  }
  return {
    text: chosen.text.trim(),
    result: chosen,
    by,
    alternatives,
    near: matched.snaps,
    sure,
    noise: false,
  }
}
