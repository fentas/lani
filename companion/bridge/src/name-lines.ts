// Name lines (docs/plans/05-built/audio-names.md): a line of the content with the learner's name in it, voiced once with a
// filler name, cut, and the learner's name joined in. This file reads a line's text and nothing else: where the name stands
// says how the line is built (its type) and how the name is said in it (its template), in any of the app's four languages.
// The splice test of 2026-11-04 found the rules: the text predicts the template 80-86 % of the time, `face` adds nothing.
//
// A line is rendered for the learner's gender first, so a {m:…|f:…} pair needs no cut: it makes two lines.
import { createHash } from 'node:crypto'
import { normalizeText } from './family'

/** Where the name stands in a line, marked: "Hvala, {n}. Ti si zlat." */
export const NAME_MARK = '{n}'

/**
 * How a line is built. 'first': the name opens a sentence, only the name is new ("{n}! Pridi, juha je vroča."). 'phrase': the
 * short phrase before the name is voiced per name, so no seam stands left of it ("Hvala, {n}. Ti si zlat."). 'splice': the name
 * stands between two real pauses ("Še enkrat, {n}, in na glas."). 'whole': none of these, voiced whole per learner, as before.
 */
export type LineType = 'first' | 'phrase' | 'splice' | 'whole'
export const LINE_TYPES: LineType[] = ['first', 'phrase', 'splice', 'whole']

/** How the name is said in a line: the three templates of the test's data (arch 59 %, ending 36 %, rise 5 %). */
export type Template = 'arch' | 'ending' | 'rise'
export const TEMPLATES: Template[] = ['arch', 'ending', 'rise']

export type NameLine = {
  /** The line with the name marked ([NAME_MARK]), as it is said. */
  said: string
  type: LineType
  template: Template
  /** What is said before the name or its phrase, shared by everyone ('' when the line starts with it). */
  head: string
  /** What is voiced per name, the name marked: "{n}", or the phrase "Hvala, {n}." */
  phrase: string
  /** What is said after it, shared by everyone ('' when the line ends with it). */
  tail: string
  /** The name clip this line wants ([kindOf]): its template, or "phrase:<id>". */
  kind: string
  /** What is voiced to get that clip, the name marked ([carrierOf]): "{n}! Pridi sem.", "Hvala, {n}. Zdaj vem." */
  carrier: string
  /** Why a 'whole' line is whole. */
  why?: string
}

/** A phrase voiced per name is at most this long before the name: the test's "short head"; longer costs what the whole line does. */
export const PHRASE_CHARS = 18

/**
 * The filler names, a man's and a woman's, in the language's spelling. A line that reads the same for both takes the man's:
 * "Marko" has the closure of its k to anchor a cut on, and "Ana" none, so a woman's line that needs the name's left edge inside
 * a sentence ('splice') is voiced whole.
 */
export const FILLERS: Record<string, { m: string; f: string }> = {
  sl: { m: 'Marko', f: 'Ana' },
  it: { m: 'Marco', f: 'Anna' },
  de: { m: 'Marko', f: 'Anna' },
  en: { m: 'Marco', f: 'Anna' },
}

/**
 * The sentences a name is said in, by language and template. The name is never the last word ("Jan." alone is read as the
 * month), and a word before it ends in a voiceless sound, so the name starts at a clean onset. Only the name is kept of them.
 * Machine-written but for Slovene's, which the test used: on the native-check list.
 */
export const NAME_CARRIERS: Record<string, Record<Template, string>> = {
  sl: { arch: '{n}! Pridi sem.', ending: 'Vidiš, {n}. Zdaj vem.', rise: '{n}? Si tu?' },
  it: { arch: '{n}! Vieni qui.', ending: 'Psst, {n}. Adesso lo so.', rise: '{n}? Sei qui?' },
  de: { arch: '{n}! Komm her.', ending: 'Ach, {n}. Jetzt weiß ich es.', rise: '{n}? Bist du da?' },
  en: { arch: '{n}! Come here.', ending: 'Look, {n}. Now I know.', rise: '{n}? Are you there?' },
}

/**
 * The words after a phrase voiced per name, by what follows the name: the phrase is said leading on, and the clip keeps it up
 * to the pause after the name. A phrase that ends the line has none: it would be said as if more were coming.
 */
export const PHRASE_TAILS: Record<string, Record<'!' | '.' | ',' | '?', string>> = {
  sl: { '!': ' Pridi sem.', '.': ' Zdaj vem.', ',': ' in tiho.', '?': ' Povej.' },
  it: { '!': ' Vieni qui.', '.': ' Adesso lo so.', ',': ' e piano.', '?': ' Dimmi.' },
  de: { '!': ' Komm her.', '.': ' Jetzt weiß ich es.', ',': ' und leise.', '?': ' Sag es.' },
  en: { '!': ' Come here.', '.': ' Now I know.', ',': ' and quietly.', '?': ' Tell me.' },
}

/** What a thanks or a goodbye starts with, by language: a name after them ends a thought, even with "!". */
const CLOSINGS: Record<string, string[]> = {
  sl: ['hvala', 'najlepša hvala', 'tisočkrat hvala', 'adijo', 'na svidenje', 'lahko noč', 'srečno', 'lep dan', 'se vidimo', 'nasvidenje', 'lovski blagor'],
  it: ['grazie', 'mille grazie', 'arrivederci', 'buonanotte', 'buona notte', 'a presto', 'a domani', 'addio', 'buona giornata', 'buona serata'],
  de: ['danke', 'vielen dank', 'dankeschön', 'auf wiedersehen', 'gute nacht', 'tschüss', 'bis bald', 'bis morgen', 'pfiat', 'schönen tag'],
  en: ['thanks', 'thank you', 'goodbye', 'good night', 'goodnight', 'bye', 'see you', 'take care', 'cheers', 'ta'],
}

const WORD = '\\p{L}\\p{M}\\p{N}'
/** The end of a sentence inside a line, with what closes after it. */
const SENTENCE_END = /[.!?…]+["»”’)]*\s+/g
/** What may follow the name and leave a pause: a sentence's end or a comma. */
const AFTER_NAME = /^[.!?…,:;]+["»”’)]*/

/** [text] with every whole-word [name] marked, and how many there are; a declined form ("Jana" for Jan) is left as it is. */
export function markName(text: string, name: string): { said: string; count: number } {
  const n = name.normalize('NFC').trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  if (!n) return { said: text, count: 0 }
  let count = 0
  const said = text.normalize('NFC').replace(new RegExp(`(?<![${WORD}])${n}(?![${WORD}])`, 'giu'), () => {
    count++
    return NAME_MARK
  })
  return { said, count }
}

/** The template of a name with [before] it in its sentence and [punct] after it: the test's rule, in any language. */
function templateOf(before: string, punct: string, language: string): Template {
  const p = punct[0] ?? '.'
  if (p === '?') return 'rise'
  const b = before.trim()
  if (!b) return 'arch'
  const low = b.toLocaleLowerCase(language).replace(/,$/, '')
  const closing = (CLOSINGS[language] ?? []).some(w => low === w || low.startsWith(`${w} `) || low.startsWith(`${w},`))
  if (closing || p === '.' || p === '…') return 'ending'
  return p === '!' ? 'arch' : 'ending'
}

const whole = (said: string, template: Template, why: string): NameLine => ({ said, type: 'whole', template, head: '', phrase: '', tail: '', kind: '', carrier: '', why })

/** The id of a phrase's name clip: what is voiced, in [language]. */
export const phraseId = (language: string, carrier: string) => createHash('sha1').update(`${language}|${carrier}`).digest('hex').slice(0, 12)

/** What is voiced for a phrase with the name marked: the phrase, with the words that lead on from it unless it ends the line. */
function phraseCarrier(language: string, phrase: string, punct: string, final: boolean): string | undefined {
  if (final) return phrase
  const tails = PHRASE_TAILS[language]
  if (!tails) return undefined
  const p = punct[0] ?? '.'
  return phrase + (p === '?' ? tails['?'] : p === '!' ? tails['!'] : p === '.' || p === '…' ? tails['.'] : tails[','])
}

/**
 * The flags of a line of [language] with its name marked ([said], one or more [NAME_MARK]). Never undefined: what can't be cut
 * is 'whole', with why. [others]: the line holds another form of the name too ("{learner:dat}"), which only a whole take says.
 */
export function flagLine(said: string, language: string, others = false): NameLine {
  const at = said.indexOf(NAME_MARK)
  const pre = at < 0 ? said : said.slice(0, at)
  const post = at < 0 ? '' : said.slice(at + NAME_MARK.length)
  const ends = [...pre.matchAll(SENTENCE_END)]
  const last = ends[ends.length - 1]
  const start = last ? last.index + last[0].length : 0
  const earlier = pre.slice(0, start).trim()
  const before = pre.slice(start)
  const punct = AFTER_NAME.exec(post)?.[0] ?? ''
  const rest = post.slice(punct.length).trim()
  const template = templateOf(before, punct, language)
  if (at < 0) return whole(said, template, 'no name')
  if (!NAME_CARRIERS[language]) return whole(said, template, `no carrier sentences in ${language}`)
  if (others) return whole(said, template, 'another form of the name')
  if (said.indexOf(NAME_MARK, at + 1) >= 0) return whole(said, template, 'the name twice')
  if (!punct && post.trim()) return whole(said, template, 'a word right after the name')
  if (before.trim() && !/\p{L}/u.test(before)) return whole(said, template, 'the name in quotes')
  const b = before.trim()
  const comma = /[,;:–—]$/.test(b)
  const inside = comma && /^[,:;]/.test(punct) && !!rest
  const namePart = (type: LineType, head: string, tail: string): NameLine =>
    ({ said, type, template, head, phrase: NAME_MARK, tail, kind: template, carrier: NAME_CARRIERS[language][template] })
  if (!b) return namePart('first', earlier, rest)
  if (inside) return namePart('splice', pre.trim(), rest)
  if (b.length <= PHRASE_CHARS) {
    const phrase = `${b} ${NAME_MARK}${punct}`
    const carrier = phraseCarrier(language, phrase, punct, !rest)
    if (!carrier) return whole(said, template, `no phrase tails in ${language}`)
    return { said, type: 'phrase', template, head: earlier, phrase, tail: rest, kind: `phrase:${phraseId(language, carrier)}`, carrier }
  }
  if (comma) return namePart('splice', pre.trim(), rest)
  return whole(said, template, 'a long sentence with no pause before the name')
}

// --- the name, which is the learner's own text -------------------------------------------------------------------------
//
// A name field must not become a way to have anything voiced. A name that fails here is said by the phone's voice, silently.

/** The most letters and words of a name voiced, and of one word. */
export const NAME_LIMITS = { chars: 24, words: 2, word: 16, min: 2 }

/** Latin letters with their marks, a hyphen or an apostrophe inside a word, nothing else: what the four languages' voices can say. */
const NAME_WORD = /^\p{Script=Latin}[\p{Script=Latin}\p{M}]*(?:['’-]\p{Script=Latin}[\p{Script=Latin}\p{M}]*)*$/u

/** The same letter four times running. */
const STUTTER = /(\p{L})\1{3}/u

/** Words no voice says as a name, in the app's languages. Small on purpose: the check's real bound is the shape of a name. */
const NOT_NAMES = new Set([
  'admin', 'test', 'null', 'none', 'undefined', 'hitler', 'nazi',
  'fuck', 'shit', 'bitch', 'cunt', 'asshole', 'nigger',
  'kurac', 'pizda', 'jebi', 'jebem', 'peder',
  'cazzo', 'stronzo', 'vaffanculo', 'puttana', 'merda',
  'scheiße', 'scheisse', 'fotze', 'hurensohn', 'arschloch', 'wichser',
])

/** [name] as the library keys it: NFC, lower case, single spaces. */
export const normalizeName = (name: string) => name.normalize('NFC').trim().replace(/\s+/g, ' ').toLocaleLowerCase('sl')

/** The normalized [name] as it is said in a carrier sentence: each word with its capital ("ana marija" → "Ana Marija"). */
export const spokenName = (name: string) => name.split(/([ '’-])/).map(w => w.charAt(0).toLocaleUpperCase('sl') + w.slice(1)).join('')

/** The normalized name when [name] may be voiced as one (length, letters, words), else undefined. */
export function nameOk(name: string): string | undefined {
  const n = normalizeName(name)
  const words = n.split(' ')
  if (n.length < NAME_LIMITS.min || n.length > NAME_LIMITS.chars || words.length > NAME_LIMITS.words) return undefined
  for (const w of words) {
    if (w.length > NAME_LIMITS.word || !NAME_WORD.test(w) || STUTTER.test(w) || NOT_NAMES.has(w)) return undefined
    if (!/[aeiouyàáâäãåæèéêëìíîïòóôöõøùúûüýÿ]/u.test(w)) return undefined
  }
  return n
}

// --- the content's name lines ------------------------------------------------------------------------------------------

/** The name the content is rendered with to find its name lines: no word of any language, and no learner's. */
export const PROBE_NAME = 'Zzqname'

/** A text said with the name replaced by what a catalogue is keyed by, so a line matches however the learner is called. */
const keyOf = (said: string) => normalizeText(said.split(NAME_MARK).join(PROBE_NAME))

/** A line of the content, as a catalogue knows it: its flags, and whether only a woman's rendering says it. */
export type KnownLine = NameLine & { woman: boolean }

/**
 * The name lines of a village's content: its texts rendered with [PROBE_NAME] for a man and for a woman ([men], [women]),
 * each with the name marked and flagged. A text not in here is no name line (the tutor's words in a chat, anything else): it
 * is voiced whole, and nothing of it goes into the shared pieces.
 */
export class NameCatalogue {
  private readonly lines = new Map<string, KnownLine>()

  constructor(readonly language: string, men: Iterable<string>, women: Iterable<string>) {
    const add = (text: string, woman: boolean) => {
      if (!text.includes(PROBE_NAME)) return
      const { said, count } = markName(text, PROBE_NAME)
      const key = keyOf(said)
      const had = this.lines.get(key)
      if (had) {
        if (!woman) had.woman = false
        return
      }
      this.lines.set(key, { ...flagLine(said, language, !count || said.includes(PROBE_NAME)), woman })
    }
    for (const t of men) add(t, false)
    for (const t of women) add(t, true)
    // a woman's line whose name stands where only "Marko" gives a left edge
    for (const l of this.lines.values()) if (l.woman && l.type === 'splice') Object.assign(l, { type: 'whole', head: '', phrase: '', tail: '', kind: '', carrier: '', why: "a woman's line with no left edge for its filler" })
  }

  get size(): number { return this.lines.size }

  all(): KnownLine[] { return [...this.lines.values()] }

  /** The line of the content [text] is, said with [name]; undefined when it isn't one. */
  find(text: string, name: string): KnownLine | undefined {
    const { said, count } = markName(text, name)
    return count ? this.lines.get(keyOf(said)) : undefined
  }
}
