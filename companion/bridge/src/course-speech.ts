// The school's introductions as the app reads them aloud (companion/COURSE.md, "Read aloud"), so voice-build makes the
// teacher's clips ahead: a port of the app's IntroSpeech (data/IntroSpeech.kt), its check for the language learned
// (Speaker.isSlovene: Clips.has, Voice.has, Spoken.looks in data/Slovene.kt) and who teaches (SchoolController.teacher:
// Residents.brings(SCHOOL)). Keep the rules identical to the Kotlin: bridge/test/fixtures/intro-speech.json holds inputs
// and what both read from them, checked by the app's IntroSpeechParityTest and the smoke's course-speech section.
//
// A step reads its explanation (Markdown in the learner's base language, the Slovene forms marked) as one clip in the
// teacher's voice, keyed "<base>|<normalized text>" ([key]); without that clip, in its runs: the base-language parts by the
// phone's voice, the Slovene spans in the teacher's; then its examples, then its quick question as far as it gives nothing
// away ([questionLines]), and once it is answered, the sentence with the gap filled ([answer]). The summary at the end
// reads the unit's grammar page's rule (else its goal) as a step's explanation.
import type { Unit } from './course'
import { normalizeText, phrases } from './family'
import type { GrammarPage } from './grammar'
import { textIn } from './langs'

// --- the Kotlin's character classes -------------------------------------------------------------------------------
//
// The app's regexes are java.util.regex: `.` matches anything but a line terminator (\n, \r, U+0085, U+2028, U+2029),
// `\s` is ASCII whitespace, `\d` ASCII digits, and `$` (without MULTILINE) matches at the end or before a final line
// terminator. Kotlin's trim() and isBlank() take Char.isWhitespace: Unicode's space separators and the ASCII controls.

const DOT = '[^\\n\\r\\u0085\\u2028\\u2029]'
const JSPACE = '[ \\t\\n\\x0B\\f\\r]'
/** Java's `$` without MULTILINE: at the end, or before a final line terminator. */
const JEND = '(?=(?:\\r\\n|[\\n\\r\\u0085\\u2028\\u2029])?$)'
const KT_WS = '[\\p{Z}\\t\\n\\x0B\\f\\r\\x1C-\\x1F]'
const KT_TRIM = new RegExp(`^${KT_WS}+|${KT_WS}+$`, 'gu')
const KT_TRIM_START = new RegExp(`^${KT_WS}+`, 'u')
const KT_TRIM_END = new RegExp(`${KT_WS}+$`, 'u')
const KT_BLANK = new RegExp(`^${KT_WS}*$`, 'u')

/** Kotlin's String.trim(). */
export const ktTrim = (s: string) => s.replace(KT_TRIM, '')
const ktTrimStart = (s: string) => s.replace(KT_TRIM_START, '')
const ktTrimEnd = (s: string) => s.replace(KT_TRIM_END, '')
const ktBlank = (s: string) => KT_BLANK.test(s)
/** Kotlin's lines(): split at \r\n, \n and \r. */
const ktLines = (s: string) => s.split(/\r\n|\n|\r/)
/** [s] with every match of [re] replaced by [to], literally (Kotlin's replace with a plain answer). */
const replaceAll = (s: string, re: RegExp, to: string) => s.replace(re, () => to)

/** The inline Markdown the app draws (ui/Markdown.kt): **bold**, *italic*, `code`, „quotes“. */
const INLINE = () => new RegExp(`\\*\\*(${DOT}+?)\\*\\*|\\*(${DOT}+?)\\*|\`(${DOT}+?)\`|„(${DOT}+?)[“”]`, 'gu')
const RULE = /^(-{3,}|\*{3,}|_{3,})$/
const NUMBERED = new RegExp(`^(\\d+)[.)]${JSPACE}+(${DOT}*)$`, 'u')
const SPACE = new RegExp(`${JSPACE}+`, 'g')
const TRAILING_BRACKETS = new RegExp(`${JSPACE}*\\([^()]*\\)${JSPACE}*${JEND}`, 'u')
/** A gap of a quick question (Sheets.GAP): three underscores or more. */
const GAP = /_{3,}/g
const WORD = /\p{L}+/gu
const SYMBOL = /^\p{So}$/u
const LETTER_OR_DIGIT = /[\p{L}\p{Nd}]/u

// --- IntroSpeech ---------------------------------------------------------------------------------------------------

export type SegmentKind = 'plain' | 'bold' | 'italic' | 'code' | 'quoted'
/** A piece of a line's inline Markdown: plain text, or a span ([kind]); [body] its text without the marks. */
export type Segment = { body: string; kind: SegmentKind }
/** Bold, italic or quoted: what the app looks at for Slovene. */
const isSpan = (s: Segment) => s.kind === 'bold' || s.kind === 'italic' || s.kind === 'quoted'

/** A run read in one voice: [text] as said, [slovene] (the teacher's voice) or not (the phone's), on source [line] as its [segments] (from, to). */
export type Run = { text: string; slovene: boolean; line: number; segments: [number, number] }

/** What is being read: the explanation as a whole, a run of it, an example, the quick question, the answer's sentence. */
export type Mark = { kind: 'whole' } | { kind: 'part'; line: number; segments: [number, number] } | { kind: 'example'; index: number } | { kind: 'question' } | { kind: 'answer' }
/** A line read in one voice: [text], [slovene] (the teacher's voice) or in the learner's base (the phone's), [mark]. */
export type Line = { text: string; slovene: boolean; mark: Mark }
/** The explanation of a step: [plain] (in [lang], as the node voices it in one go) and its [runs], read without its clip. */
export type Explanation = { plain: string; lang: string; runs: Run[] }
/** What a step reads, in order: its [explanation], then its [lines] (the examples, the quick question). */
export type Script = { id: string; explanation?: Explanation; lines: Line[] }

/** A quick question (spec.ts choice or cloze), as an intro step has it. */
export type Ask =
  | { type: 'choice'; prompt: string; options: string[]; answer: number; say_options?: boolean; instruction?: string }
  | { type: 'cloze'; text: string; accept: string[]; instruction?: string }
/** A cell of the intro's table: words of the language learned (a string), or a text per base. */
type Cell = string | Record<string, string>
export type IntroStep = { say: Record<string, string>; examples?: Record<string, unknown>[]; rows?: Cell[][]; ask?: Ask }
export type Intro = { steps: IntroStep[] }

/** [s]'s inline Markdown in its segments, in order: the plain text between the spans (when not empty) and each span. */
export function segments(s: string): Segment[] {
  const out: Segment[] = []
  let i = 0
  for (const m of s.matchAll(INLINE())) {
    const at = m.index!
    if (at > i) out.push({ body: s.substring(i, at), kind: 'plain' })
    const [, bold, italic, code, quoted] = m
    out.push(bold ? { body: bold, kind: 'bold' } : italic ? { body: italic, kind: 'italic' } : code ? { body: code, kind: 'code' } : { body: quoted ?? '', kind: 'quoted' })
    i = at + m[0].length
  }
  if (i < s.length) out.push({ body: s.substring(i), kind: 'plain' })
  return out
}

/**
 * The lines of [markdown] that are read, by their index in its trimmed lines, each as the Markdown draws its text: a
 * heading without its #, a quote without its >, a list item without its bullet or number. Blank lines, rules and
 * tables aren't read.
 */
export function lines(markdown: string): [number, string][] {
  const out: [number, string][] = []
  ktLines(ktTrim(markdown)).forEach((raw, i) => {
    const t = ktTrimEnd(raw)
    let text: string
    if (ktBlank(t)) return
    if (ktTrimStart(t).startsWith('|')) return
    if (RULE.test(ktTrim(t))) return
    if (t.startsWith('#')) text = t.replace(/^[# ]+/, '')
    else if (t.startsWith('>')) text = ktTrim(t.slice(1))
    else if (t.startsWith('- ') || t.startsWith('• ') || t.startsWith('* ')) text = t.slice(2)
    else if (NUMBERED.test(t)) text = NUMBERED.exec(t)![2]
    else text = t
    if (!ktBlank(text)) out.push([i, text])
  })
  return out
}

/** A Markdown body without the marks of the spans inside it. */
const bare = (s: string) => s.replace(INLINE(), (_m, ...g: (string | undefined)[]) => g.slice(0, 4).find(x => !!x) ?? '')

/**
 * [s] as a voice says it: emoji and other symbols out, an arrow a pause, whitespace one space; undefined when nothing
 * is left to say.
 */
export function said(s: string): string | undefined {
  let t = ''
  // as the Kotlin walks it: UTF-16 units, a surrogate (an emoji's half) left out
  for (const ch of replaceAll(s, /→/g, ', ').split('')) {
    const u = ch.charCodeAt(0)
    if ((u >= 0xd800 && u <= 0xdfff) || ch === '️' || ch === '‍' || SYMBOL.test(ch)) continue
    t += ch
  }
  t = ktTrim(t.replace(SPACE, ' '))
  return [...t].some(c => c.length === 1 && LETTER_OR_DIGIT.test(c)) ? t : undefined
}

/**
 * [markdown] in the runs it is read in, in order: a span [isSlovene] says is Slovene on its own (its text without the
 * marks), what lies between such spans together in the base language; a run with nothing to say is left out.
 */
export function runs(markdown: string, isSlovene: (text: string) => boolean): Run[] {
  const out: Run[] = []
  for (const [line, text] of lines(markdown)) {
    const segs = segments(text)
    let from = -1
    let base = ''
    const flush = (to: number) => {
      if (from < 0) return
      const t = said(base)
      if (t !== undefined) out.push({ text: t, slovene: false, line, segments: [from, to] })
      from = -1
      base = ''
    }
    segs.forEach((s, k) => {
      const body = bare(s.body)
      if (isSpan(s) && isSlovene(body)) {
        flush(k - 1)
        const t = said(body)
        if (t !== undefined) out.push({ text: t, slovene: true, line, segments: [k, k] })
      } else {
        if (from < 0) from = k
        base += s.kind === 'plain' ? s.body : body
      }
    })
    flush(segs.length - 1)
  }
  return out
}

/** The explanation as the node voices it in one go: each line's text without its marks, without emoji, a line each. */
export function plain(markdown: string): string {
  return lines(markdown)
    .flatMap(([, t]) => {
      const x = said(segments(t).map(s => (s.kind === 'plain' ? s.body : bare(s.body))).join(''))
      return x === undefined ? [] : [x]
    })
    .join('\n')
}

/** The voice store's key of an explanation in [lang] ([plain]): the language and its text as the store keys a line. */
export const key = (lang: string, text: string) => `${lang}|${normalizeText(text)}`

/**
 * A quick question's prompt as read before it is answered, giving nothing away: the sentence with its gap as a pause,
 * without a translation in brackets at its end. Undefined for another kind of question.
 */
export function question(ask: Ask | undefined): string | undefined {
  if (!ask) return undefined
  const text = ask.type === 'choice' ? ask.prompt : ask.text
  return said(ktTrim(replaceAll(replaceAll(text, GAP, '…'), TRAILING_BRACKETS, '')))
}

/** A sentence with its gap filled with [answer], without a translation in brackets at its end; undefined when there's no gap. */
export function gapFilled(text: string, answer: string | undefined): string | undefined {
  if (answer === undefined || !new RegExp(GAP.source).test(text)) return undefined
  return ktTrim(replaceAll(replaceAll(text, GAP, answer), TRAILING_BRACKETS, ''))
}

/** The sentence of a quick question's answer, read once it is answered: its prompt with the gap filled with the right answer. */
export function answer(ask: Ask | undefined): string | undefined {
  if (!ask) return undefined
  return ask.type === 'choice' ? gapFilled(ask.prompt, ask.options[ask.answer]) : gapFilled(ask.text, ask.accept[0])
}

/** The words of [t], lower case (Kotlin's lowercase(): the root locale). */
const wordsOf = (t: string) => [...t.toLowerCase().matchAll(WORD)].map(m => m[0])

/**
 * The words of the language learned that [intro] shows, lower case: its examples in [language], its table's words, its
 * questions' Slovene, and the unit's [title] in [language]. A span of only these is Slovene ([within]).
 */
export function vocabulary(intro: Intro, language: string, title: string | undefined, isSlovene: (text: string) => boolean = () => false): Set<string> {
  const texts: string[] = []
  if (title !== undefined) texts.push(title)
  for (const s of intro.steps) {
    for (const e of s.examples ?? []) texts.push(typeof e[language] === 'string' ? (e[language] as string) : '')
    for (const c of (s.rows ?? []).flat()) if (typeof c === 'string') texts.push(c)
    const a = s.ask
    if (a?.type === 'choice') {
      const p = replaceAll(a.prompt, GAP, ' ')
      if (isSlovene(p)) texts.push(p)
      texts.push(...a.options.filter(o => !!a.say_options || isSlovene(o)))
    } else if (a?.type === 'cloze') {
      const p = replaceAll(a.text, GAP, ' ')
      if (isSlovene(p)) texts.push(p)
      texts.push(...a.accept)
    }
  }
  return new Set(texts.flatMap(wordsOf))
}

/** [text] has words, and all of them are [known] ones ([vocabulary]). */
export function within(text: string, known: Set<string>): boolean {
  const w = wordsOf(text)
  return w.length > 0 && w.every(x => known.has(x))
}

/**
 * A quick question as read before it's answered: its instruction ([instructionLines]), then its prompt with the gap a
 * pause, in the teacher's voice when it is in the language learned (a sentence with a gap to fill and no Markdown, a word
 * named in »quotes«, or what [isSlovene] says is), else in its runs (a question in the base marks its Slovene: "Which form
 * goes with **mi**? ___"). Nothing for another kind of question.
 */
export function questionLines(ask: Ask | undefined, isSlovene: (text: string) => boolean): Line[] {
  const q = question(ask)
  if (q === undefined) return []
  const mark: Mark = { kind: 'question' }
  const gapped = ask!.type === 'cloze' || new RegExp(GAP.source).test(ask!.prompt)
  const first = ask!.instruction !== undefined ? instructionLines(ask!.instruction, isSlovene) : []
  const learned = (gapped && !new RegExp(INLINE().source, 'u').test(q)) || NAMED.test(q) || isSlovene(q)
  const prompt = learned ? [{ text: q, slovene: true, mark }] : runs(q, isSlovene).map(r => ({ text: r.text, slovene: r.slovene, mark }))
  return [...first, ...prompt]
}

/** A label: the language learned, then the base ("Izberi · Pick"). */
const LABEL = /^([^·]+) · ([^·]+)$/
/** A word or phrase named in »quotes« (»smreka«, the prompt of "which gender?"). */
const NAMED = /^»[^»«]+«$/

/**
 * A quick question's instruction as read: a label as the app writes them, the language learned and then the base
 * ("Kakšnega spola? · Which gender?", no Markdown), its first part in the teacher's voice and its second in the base;
 * else in its runs.
 */
export function instructionLines(text: string, isSlovene: (text: string) => boolean): Line[] {
  const mark: Mark = { kind: 'question' }
  const label = !new RegExp(INLINE().source, 'u').test(text) ? LABEL.exec(text) : null
  if (label) {
    const a = said(label[1])
    const b = said(label[2])
    return [...(a !== undefined ? [{ text: a, slovene: true, mark }] : []), ...(b !== undefined ? [{ text: b, slovene: false, mark }] : [])]
  }
  return runs(text, isSlovene).map(r => ({ text: r.text, slovene: r.slovene, mark }))
}

/**
 * What a step reads when it appears: the explanation [say] (in [base]), its [examples] in the language learned, then its
 * quick question as far as it gives nothing away.
 */
export function step(id: string, say: string, base: string, examples: string[], ask: Ask | undefined, isSlovene: (text: string) => boolean): Script {
  const p = plain(say)
  const lines: Line[] = examples.flatMap((e, i) => {
    const t = said(e)
    return t === undefined ? [] : [{ text: t, slovene: true, mark: { kind: 'example', index: i } as Mark }]
  })
  return { id, ...(p ? { explanation: { plain: p, lang: base, runs: runs(say, isSlovene) } } : {}), lines: [...lines, ...questionLines(ask, isSlovene)] }
}

/** The explanation's runs as lines, as they're read without its clip. */
export const stitched = (s: Script): Line[] =>
  (s.explanation?.runs ?? []).map(r => ({ text: r.text, slovene: r.slovene, mark: { kind: 'part', line: r.line, segments: r.segments } }))

/**
 * What step [i] of [intro] reads (ui/school/IntroScreen, the day's audio): its explanation in [base], its examples in
 * [language], its quick question; Slovene what [isSlovene] (the app's check) says is, and the words the intro itself shows.
 */
export function introStep(id: string, intro: Intro, i: number, language: string, base: string, title: string | undefined, isSlovene: (text: string) => boolean): Script {
  const s = intro.steps[i]
  const known = vocabulary(intro, language, title, isSlovene)
  return step(id, textIn(s.say, base), base, (s.examples ?? []).map(e => (typeof e[language] === 'string' ? (e[language] as string) : '')), s.ask, t => isSlovene(t) || within(t, known))
}

/** What the summary reads: the unit's grammar page's [rule] in [base] (its table is to look at), else its [goal]; undefined with neither. */
export function summarySay(rule: Record<string, string> | undefined, goal: Record<string, string> | undefined, base: string): string | undefined {
  const r = rule ? textIn(rule, base) : undefined
  if (r !== undefined && !ktBlank(r)) return r
  const g = goal ? textIn(goal, base) : undefined
  return g !== undefined && !ktBlank(g) ? g : undefined
}

/** The summary read as a step: [say] ([summarySay]) its explanation, nothing else; the intro's words count as Slovene as in its steps. */
export function summary(id: string, say: string, base: string, intro: Intro, language: string, title: string | undefined, isSlovene: (text: string) => boolean): Script {
  const known = vocabulary(intro, language, title, isSlovene)
  return step(id, say, base, [], undefined, t => isSlovene(t) || within(t, known))
}

// --- the app's check for the language learned (Speaker.isSlovene) ---------------------------------------------------

const LETTERS: Record<string, RegExp> = { sl: /[čšžČŠŽ]/, it: /[àèéìòùÀÈÉÌÒÙ]/, de: /[äöüßÄÖÜ]/ }
const WORDS: Record<string, Set<string>> = {
  sl: new Set([
    'je', 'sem', 'si', 'smo', 'ste', 'so', 'sva', 'sta', 'se', 'ga', 'jo', 'mu', 'ji', 'jih',
    'da', 'ne', 'ni', 'nisem', 'pa', 'ali', 'ki', 'kaj', 'kako', 'kje', 'kdo', 'kdaj', 'zakaj', 'koliko',
    'moj', 'moja', 'moje', 'tvoj', 'tvoja', 'tvoje', 'njegov', 'njen', 'naš', 'vaš',
    'dober', 'dobra', 'dobro', 'lep', 'lepa', 'lepo', 'hvala', 'prosim', 'oprostite', 'živjo', 'nasvidenje',
    'iz', 'na', 'za', 'po', 'od', 'do', 'pri', 'brez', 'ob', 'tudi', 'ampak', 'zelo', 'že', 'še', 'lahko',
    'bi', 'bo', 'bom', 'imam', 'ima', 'imaš', 'nimam', 'grem', 'gre', 'greš', 'danes', 'jutri', 'včeraj',
    'tukaj', 'tam', 'jaz', 'ona', 'ono', 'vi', 'oni', 'rad', 'rada', 'dan', 'jutro', 'večer', 'kruh', 'kava',
  ]),
  en: new Set([
    'the', 'is', 'are', 'a', 'an', 'and', 'of', 'to', 'in', 'on', 'at', 'you', 'i', 'it', 'this', 'that',
    'what', 'how', 'my', 'your', 'for', 'with', 'be', 'do', 'does', 'not', 'from', 'by', 'or', 'was',
    'were', 'has', 'have', 'say', 'word', 'means', 'mean', 'he', 'she', 'we', 'they', 'good', 'day',
  ]),
  it: new Set([
    'il', 'lo', 'la', 'le', 'gli', 'un', 'una', 'di', 'del', 'della', 'nel', 'nella', 'al', 'alla', 'che', 'non',
    'sono', 'sei', 'siamo', 'ho', 'hai', 'ha', 'mi', 'ti', 'ci', 'per', 'con', 'come', 'dove', 'quando', 'perché',
    'questo', 'questa', 'anche', 'molto', 'grazie', 'prego', 'ciao', 'buongiorno', 'buonasera', 'io', 'tu', 'lui',
    'noi', 'voi', 'loro', 'ma', 'più', 'oggi', 'domani', 'ieri', 'casa', 'bene', 'sì', 'no', 'cosa',
  ]),
  de: new Set([
    'der', 'die', 'das', 'den', 'dem', 'des', 'ein', 'eine', 'einen', 'und', 'ist', 'sind', 'bin', 'bist', 'nicht',
    'ich', 'du', 'er', 'sie', 'wir', 'ihr', 'mit', 'auf', 'für', 'von', 'zu', 'im', 'am', 'auch', 'sehr', 'danke',
    'bitte', 'hallo', 'guten', 'tag', 'morgen', 'abend', 'wie', 'was', 'wo', 'wann', 'warum', 'heute', 'gestern',
    'ja', 'nein', 'gut', 'haus', 'kein', 'keine', 'mein', 'meine', 'dein', 'deine', 'es', 'geht', 'habe', 'hast',
  ]),
}

/**
 * Whether a short text reads as [target] rather than [base] (the app's Spoken.looks): the target's own letters (č/š/ž,
 * à/è/é, ä/ö/ü/ß), or more of its frequent words than of the base's.
 */
export function spokenLooks(text: string, target: string, base: string): boolean {
  if (LETTERS[target]?.test(text)) return true
  if (LETTERS[base]?.test(text)) return false
  const found = wordsOf(text)
  if (!found.length) return false
  const t = found.filter(w => WORDS[target]?.has(w)).length
  const b = found.filter(w => WORDS[base]?.has(w)).length
  return t > b && t * 3 >= Math.min(found.length, 6)
}

/** The voice store's index as the app gets it (/voice/index): normalized text → voice → file URL. */
export type ClipIndex = Record<string, Record<string, string>>

/** The norms that say [text] in [voice] of [index]: the whole text's, or each phrase's ("Kako ste? / Kako si?"); undefined without. */
function resolveIn(index: ClipIndex, text: string, voice: string): boolean {
  if (index[normalizeText(text)]?.[voice] !== undefined) return true
  const ps = phrases(text)
  return ps.length >= 2 && ps.every(p => index[normalizeText(p)]?.[voice] !== undefined)
}

/** The app's Clips.has: the node has a clip of [text] in any voice, or of each of its phrases in a narrator's. */
export function indexHas(index: ClipIndex, text: string): boolean {
  const at = index[normalizeText(text)]
  return (!!at && Object.keys(at).length > 0) || resolveIn(index, text, 'female') || resolveIn(index, text, 'male')
}

/** The app's Voice.has: a family recording of [text], or of each of its phrases (the family's audio/index.json). */
export function familyHas(index: Record<string, unknown[]>, text: string): boolean {
  if ((index[normalizeText(text)]?.length ?? 0) > 0) return true
  const ps = phrases(text)
  return ps.length >= 2 && ps.every(p => (index[normalizeText(p)]?.length ?? 0) > 0)
}

/** The app's Speaker.isSlovene for a learner of [target] explaining in [base]: a clip or a recording of it ([has]), or it reads so. */
export const looksLearned = (target: string, base: string, has: (text: string) => boolean = () => false) => (text: string) => has(text) || spokenLooks(text, target, base)

// --- who teaches -------------------------------------------------------------------------------------------------

type CastMember = { id: string; voice: string; speaker?: string; home?: string[]; since?: string; extra?: boolean; order?: number }
/** The app's dwellings: a roof to live under, no workplace. */
const DWELLINGS = new Set(['tent', 'hut', 'house'])

/** Where [v] works (the first roof of their home that is no dwelling), as the app's Residents.workplace. */
function workplace(v: CastMember): string | undefined {
  if (v.extra || v.since == null) return undefined
  const h = v.home?.[0]
  if (!h || h.startsWith('spot:')) return undefined
  const b = h.toLowerCase()
  return DWELLINGS.has(b) ? undefined : b
}

/**
 * The school's person (the app's SchoolController.teacher: Residents.brings(SCHOOL)): the first of the cast, by order,
 * whose workplace is the school. Učiteljica Mojca in Primorska.
 */
export function schoolTeacher<V extends CastMember>(cast: V[]): V | undefined {
  return cast
    .map((v, i) => ({ v, i }))
    .filter(x => workplace(x.v) === 'school')
    .sort((a, b) => (a.v.order ?? 100) - (b.v.order ?? 100) || a.i - b.i)[0]?.v
}

/**
 * The voice the app reads the course in (ui/school/IntroScreen's introReader, Speaker.voices): the teacher's own voice
 * when it is ready ("@mojca": [ownVoice]), else her speaker ([speakerOf]: "teacher"), else the female narrator.
 */
export function teacherVoice(cast: CastMember[], speakerOf: (v: CastMember) => string, ownVoice: (id: string) => boolean = () => false): string {
  const t = schoolTeacher(cast)
  if (!t) return 'female'
  return ownVoice(t.id) ? `@${t.id}` : speakerOf(t)
}

// --- what voice-build makes ahead ---------------------------------------------------------------------------------

/** A text of the course the teacher reads: a line of the language learned, or an explanation in [lang] (a learner's base). */
export type CourseText = { text: string; source: string; lang?: string }

export type CourseInput = {
  units: Pick<Unit, 'id' | 'language' | 'title' | 'goal' | 'grammar' | 'intro'>[]
  /** The grammar book's pages: a unit's first page's rule is its summary. */
  pages?: Pick<GrammarPage, 'id' | 'rule'>[]
  /** The learners' base languages: each explanation in each, and the runs read without it. */
  bases: string[]
  /** The app's check besides how a text reads: a clip of it on the node, a family recording ([indexHas], [familyHas]). */
  has?: (text: string) => boolean
}

/**
 * What the app reads of the course of [language] in the teacher's voice, in the course's order: every step's examples,
 * its quick question (the instruction's Slovene, the prompt or its Slovene), the sentence of its answer, and the Slovene
 * runs of its explanation and of the summary (read when the explanation's one clip can't be had), for each base
 * ([lines]); and the explanations themselves, each in each base ([explanations]: the step's and the summary's).
 */
export function courseTexts(c: CourseInput, language: string): { lines: CourseText[]; explanations: CourseText[] } {
  const lines: CourseText[] = []
  const explanations: CourseText[] = []
  const pages = new Map((c.pages ?? []).map(p => [p.id, p]))
  for (const u of c.units) {
    if (u.language !== language) continue
    const source = `course:${u.id}`
    const intro = u.intro as unknown as Intro
    const title = u.title[language]
    for (const base of c.bases) {
      const isSlovene = looksLearned(language, base, c.has)
      const read = (s: Script, ask?: Ask) => {
        if (s.explanation) explanations.push({ text: s.explanation.plain, source, lang: base })
        // said without a Markdown mark the app leaves in a prompt ("**Micka** … doma."): the same clip's key, a clean take
        for (const l of [...stitched(s), ...s.lines]) if (l.slovene) lines.push({ text: bare(l.text), source })
        const a = answer(ask)
        if (a !== undefined) lines.push({ text: bare(a), source })
      }
      intro.steps.forEach((s, i) => read(introStep(`${u.id}/${i}`, intro, i, language, base, title, isSlovene), s.ask))
      const page = u.grammar.map(g => pages.get(g)).find(Boolean)
      const say = summarySay(page?.rule, u.goal, base)
      if (say !== undefined) read(summary(`${u.id}/summary`, say, base, intro, language, title, isSlovene))
    }
  }
  return { lines, explanations }
}
