// The school's printed sheets (companion/COURSE.md, "Printed sheets"): what goes on a unit's A4 page, taken from the
// course (its outline and the unit), the grammar pages the unit teaches, their cheat-sheet cards, and the word packs
// and dictionary for the words. Nothing here is written for the print: each part is chosen from the content, so a unit
// changed (a native speaker's fix) or added (A2 …) prints as it is then. html.ts lays it out, fit.js fits it to the page.
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

/** A text per language ({"sl", "en", "de", "it"}), or one string (the language learned). */
export type L10n = Record<string, string>
type Text = string | L10n
// the content is validated by the bridge (course.ts, grammar.ts); here it is only read
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Json = any

/** A table cell as printed: Slovene (forms, the endings marked ⟦…⟧), a gloss in the base, or both (a head, a label). */
export type PCell = { sl?: string; tr?: string }
export type PTable = { head: PCell[]; rows: PCell[][]; from: string; optional?: boolean }
export type PExample = { sl: string; tr: string }
export type PWatch = { wrong: string; right: string; why?: string; from: string }
export type PWord = { word: string; lemma?: string; gender?: string; gloss: string; wiktionary?: boolean }
export type PCheck = { task?: string; q: string; hint?: string; a: string; line: boolean; from: string }

export type Sheet = {
  id: string
  n: number
  of: number
  level: string
  language: string
  emoji: string
  title: { sl: string; tr: string }
  goal?: string
  rule: string[]
  tables: PTable[]
  examples: PExample[]
  watch: PWatch[]
  words: PWord[]
  check: PCheck[]
  unreviewed: boolean
  wiktionary: boolean
  /** What couldn't be found (a word without a meaning …), for the generator's report. */
  notes: string[]
}

export type Options = {
  base: string
  /** {learner} in the content. */
  learner: string
  /** {m:…|f:…}: the learner's form, or both ("utrujen/utrujena") where they are short. */
  gender: 'm' | 'f' | 'both'
}

// --- text helpers ------------------------------------------------------------------------------------------------

/** As the bridge compares forms (lexicon.ts normalizeForm, Slovene): lowercase, NFC, tonal accents dropped. */
export function norm(s: string): string {
  return s.trim().toLocaleLowerCase('sl').normalize('NFD').replace(/(?!̌)\p{M}/gu, '').normalize('NFC').replace(/ł/g, 'l').replace(/ə/g, 'e')
}

export const plain = (md: string) => md.replace(/\*\*|\*|⟦|⟧/g, '').replace(/\s+/g, ' ').trim()
const wordsOf = (s: string): string[] => s.match(/[\p{L}\p{M}]+/gu) ?? []

/** The text in the base, else English, else Slovene. */
export function tr(t: Text | undefined, base: string): string {
  if (t === undefined || t === null) return ''
  if (typeof t === 'string') return t
  return t[base] ?? t.en ?? t.sl ?? Object.values(t)[0] ?? ''
}

function lev(a: string, b: string): number {
  if (a === b) return 0
  const m = a.length
  const n = b.length
  if (!m) return n
  if (!n) return m
  let prev = Array.from({ length: n + 1 }, (_, j) => j)
  for (let i = 1; i <= m; i++) {
    const cur = [i]
    for (let j = 1; j <= n; j++) cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1))
    prev = cur
  }
  return prev[n]
}

/** How alike two Slovene words or phrases are, 0..1 (1: the same). */
function alike(a: string, b: string): number {
  const x = norm(plain(a)).replace(/[^\p{L} ]/gu, '')
  const y = norm(plain(b)).replace(/[^\p{L} ]/gu, '')
  if (!x || !y) return 0
  return 1 - lev(x, y) / Math.max(x.length, y.length)
}

/**
 * The sentences of a Markdown text, split at . ! ? … followed by a space and a new sentence, never inside a **bold**
 * span ("**Ne vem. Ne delam.**" stays in its sentence), an *italic* one or »quoted speech« ("»Dober dan! Kako ste?«").
 */
export function sentences(md: string): string[] {
  const out: string[] = []
  let bold = false
  let italic = false
  let quoted = 0
  let start = 0
  const stops = '.!?…'
  const splitAt = (j: number) => {
    if (j >= md.length || !/\s/.test(md[j])) return false
    const next = md.slice(j).trimStart()
    if (!/^[\p{Lu}*»„"(\d_]/u.test(next)) return false
    out.push(md.slice(start, j).trim())
    start = j
    return true
  }
  for (let i = 0; i < md.length; i++) {
    const ch = md[i]
    if (md.startsWith('**', i)) {
      bold = !bold
      i++
      continue
    }
    if (ch === '*') {
      italic = !italic
      continue
    }
    if (ch === '»' || ch === '„') {
      quoted++
      continue
    }
    if (ch === '«' || ch === '“') {
      quoted = Math.max(0, quoted - 1)
      if (!quoted && !bold && !italic && stops.includes(md[i - 1] ?? '')) splitAt(i + 1)
      continue
    }
    if (bold || italic || quoted || !stops.includes(ch)) continue
    let j = i + 1
    while (j < md.length && '*"”)'.includes(md[j])) j += md.startsWith('**', j) ? 2 : 1
    if (splitAt(j)) i = j - 1
  }
  const last = md.slice(start).trim()
  if (last) out.push(last)
  return out
}

/**
 * A step of the intro in a line or two: its first sentence, and the ones after it that carry the forms (bold, an
 * arrow, a colon), in their order, while the whole stays short.
 */
export function condense(all: string[], budget = 200): string {
  const len = (x: string) => plain(x).length
  const score = (x: string) => Math.min(3, (x.match(/\*\*[^*]+\*\*/g) ?? []).length) + (/→/.test(x) ? 1 : 0) + (/:/.test(x) ? 0.5 : 0)
  const keep = new Set([0])
  let total = len(all[0])
  const rest = all
    .map((s, i) => ({ s, i }))
    .slice(1)
    .sort((a, b) => score(b.s) - score(a.s) || a.i - b.i)
  for (const { s, i } of rest) {
    if (keep.size >= 3 || score(s) < 1.5) continue
    if (total + len(s) > budget) continue
    keep.add(i)
    total += len(s)
  }
  return [...keep]
    .sort((a, b) => a - b)
    .map(i => all[i])
    .join(' ')
}

/** {learner} and {m:…|f:…} as the options say; with both forms wanted, a sentence that needs one or the other goes. */
export function fillLearner(s: string, o: Options): string {
  const filled = (s ?? '')
    .replace(/\{learner\}/g, o.learner)
    .replace(/\s*\{m:([^|}]*)\|f:([^}]*)\}/g, (all: string, m: string, f: string) => {
      const lead = all.match(/^\s*/)?.[0] ?? ''
      if (o.gender === 'm') return lead + m
      if (o.gender === 'f') return lead + f
      const short = (x: string) => wordsOf(plain(x)).length <= 2 && !/[.;:]/.test(x)
      if (short(m) && short(f)) return `${lead}${m}/${f}`
      // a sentence of its own for each (You say … / Zala says …) goes; inside a sentence, that sentence goes
      return /[.!?]\s*$/.test(m) && /[.!?]\s*$/.test(f) ? '' : `${lead}\u0000`
    })
  if (!filled.includes('\u0000')) return filled
  return filled
    .split('\n')
    .map(line => sentences(line).filter(x => !x.includes('\u0000')).join(' '))
    .join('\n')
}

// --- the content -----------------------------------------------------------------------------------------------

export type Content = {
  language: string
  /** Where it was read from. */
  dirs: ContentDirs
  course: Json
  units: Map<string, Json>
  pages: Map<string, Json>
  dictionary: Dictionary
}

/**
 * Where the content is: the language's course (its course.json and units/), its grammar book, the word packs and the
 * dictionary (companion/lexicon). The repository's by default; the bridge passes its own (LANI_COURSE_DIR …).
 */
export type ContentDirs = { course: string; grammar: string; packs: string; lexicon: string }

export function contentDirs(root: string, language: string, over: Partial<ContentDirs> = {}): ContentDirs {
  return {
    course: over.course ?? join(root, 'companion/course', language),
    grammar: over.grammar ?? join(root, 'companion/grammar', language),
    packs: over.packs ?? join(root, 'companion/packs'),
    lexicon: over.lexicon ?? join(root, 'companion/lexicon'),
  }
}

export function loadContent(root: string, language: string, over: Partial<ContentDirs> = {}): Content {
  const read = (p: string) => JSON.parse(readFileSync(p, 'utf8'))
  const dirs = contentDirs(root, language, over)
  const courseDir = dirs.course
  const course = read(join(courseDir, 'course.json'))
  const units = new Map<string, Json>()
  const unitsDir = join(courseDir, 'units')
  for (const f of existsSync(unitsDir) ? readdirSync(unitsDir) : []) {
    if (!f.endsWith('.json')) continue
    const u = read(join(unitsDir, f))
    if (u?.id) units.set(u.id, u)
  }
  const pages = new Map<string, Json>()
  const grammarDir = dirs.grammar
  for (const f of existsSync(grammarDir) ? readdirSync(grammarDir) : []) {
    if (!f.endsWith('.json')) continue
    const p = read(join(grammarDir, f))
    if (p?.id) pages.set(p.id, p)
  }
  return { language, dirs, course, units, pages, dictionary: new Dictionary(dirs, language) }
}

/** The units of a level in the outline's order (planned ones left out), then the level's unit files it doesn't list. */
export function unitsOf(c: Content, level: string): Json[] {
  const lv = (c.course.levels ?? []).find((l: Json) => l.level === level)
  const listed: Json[] = []
  for (const o of lv?.units ?? []) {
    if (o.planned) continue
    const u = c.units.get(o.id)
    if (u && u.level === level) listed.push(u)
  }
  const extra = [...c.units.values()].filter(u => u.level === level && !listed.includes(u)).sort((a, b) => a.id.localeCompare(b.id))
  return [...listed, ...extra]
}

export const levelsOf = (c: Content): string[] => (c.course.levels ?? []).map((l: Json) => l.level)

// --- the words: the packs, the unit's own glosses, the supplement, Wiktionary ----------------------------------

type Meaning = { lemma: string; pos: string; gloss: string; gender?: string; wiktionary?: boolean }
/** Where a word is used: the unit's Slovene (a form's lemma there wins: pekel → peči) and its English (peti: fifth). */
export type WordContext = { sl: Set<string>; en: Set<string>; glosses: Map<string, string>; lemmaOf: Map<string, string> }

const POS_RANK = ['pron', 'noun', 'verb', 'adj', 'adv', 'num', 'prep', 'conj', 'particle', 'phrase', 'intj']
/** A Wiktionary gloss that describes a form ("second-person plural imperative of oprostiti"), not a meaning. */
const FORM_GLOSS = /\b(singular|plural|dual|person|imperative|genitive|dative|locative|accusative|instrumental|nominative|participle|inflection|form)\b.*\bof\b/i

/** A word's meaning in the base: a pack's word (written for the learner), the unit's own gloss, the supplement, Wiktionary. */
export class Dictionary {
  private packs = new Map<string, Json>()
  private extra = new Map<string, Json[]>()
  private file: Json | null = null
  private loaded = false

  constructor(
    private readonly dirs: Pick<ContentDirs, 'packs' | 'lexicon'>,
    private readonly language: string,
  ) {}

  private load() {
    if (this.loaded) return
    this.loaded = true
    const packsDir = this.dirs.packs
    for (const f of existsSync(packsDir) ? readdirSync(packsDir) : []) {
      if (!f.endsWith('.json')) continue
      try {
        const p = JSON.parse(readFileSync(join(packsDir, f), 'utf8'))
        for (const w of p.words ?? []) {
          const key = w[this.language] ? norm(w[this.language]) : null
          if (key && !this.packs.has(key)) this.packs.set(key, w)
        }
      } catch {
        // a pack that doesn't parse is the bridge's to report
      }
    }
    const dir = this.dirs.lexicon
    try {
      const x = JSON.parse(readFileSync(join(dir, `${this.language}.extra.json`), 'utf8'))
      for (const e of x.entries ?? []) {
        for (const form of new Set([e.lemma, ...Object.keys(e.forms ?? {})])) {
          const k = norm(form)
          this.extra.set(k, [...(this.extra.get(k) ?? []), e])
        }
      }
    } catch {
      // no supplement
    }
    try {
      this.file = JSON.parse(readFileSync(join(dir, `${this.language}.json`), 'utf8'))
    } catch {
      this.file = null
    }
  }

  meaning(word: string, base: string, ctx: WordContext, depth = 0): Meaning | null {
    this.load()
    const key = norm(word)
    const pack = this.packs.get(key)
    if (pack && (pack[base] || pack.en)) return { lemma: pack[this.language], pos: pack.gender ? 'noun' : 'word', gloss: pack[base] ?? pack.en, gender: pack.gender }
    // a form the unit makes from its lemma ("piti → **pil**"): the lemma's meaning
    const lemma = ctx.lemmaOf.get(key)
    if (lemma && norm(lemma) !== key && depth === 0) {
      const m = this.meaning(lemma, base, ctx, 1)
      if (m) return { ...m, lemma }
    }
    // the unit's own "**dopoldne** (…)" only when nothing else knows the word: its brackets are often a note (rad: a man)
    const own = ctx.glosses.get(key)
    const note = /\b(feminine|masculine|neuter|singular|plural|dual|genitive|accusative|locative|a man|a woman|men|women|several|polite|speech|standard|going|being)\b/i
    const fallback = own && depth === 0 && !note.test(own) ? { lemma: word, pos: 'word', gloss: own } : null
    // every reading: the supplement's, Wiktionary's (the lemma itself, or a form of another)
    type Cand = Meaning & { score: number; rank: number }
    const cands: Cand[] = []
    const glossIn = (l: Json) => (base === 'en' ? l.gloss?.[0] : (l.meanings?.[base]?.gloss?.[0] ?? l.gloss?.[0]))
    const f = this.file
    const wik: Json[] = []
    if (f && Object.hasOwn(f.forms, key)) for (const [i] of f.forms[key]) if (f.lemmas[i]) wik.push(f.lemmas[i])
    for (const e of this.extra.get(key) ?? []) {
      const same = wik.find(l => norm(l.lemma) === norm(e.lemma) && l.pos === e.pos)
      const gloss = base === 'en' ? e.gloss?.[0] : (same?.meanings?.[base]?.gloss?.[0] ?? e.gloss?.[0])
      if (gloss) cands.push({ lemma: e.lemma, pos: e.pos, gloss, gender: e.gender, score: 0.5, rank: 0 })
    }
    for (const l of wik) {
      if (l.pos === 'name') continue
      const gloss = glossIn(l)
      if (!gloss) continue
      cands.push({ lemma: l.lemma, pos: l.pos, gloss, gender: l.gender, wiktionary: true, score: 0, rank: 1 })
    }
    if (!cands.length) {
      // a verb with se (igrati se): the verb's meaning
      const verb = word.match(/^(\S+)\s+(?:se|si)$/)
      if (verb && depth === 0) return this.meaning(verb[1], base, ctx, 1) ?? fallback
      return fallback
    }
    for (const x of cands) {
      // the word itself first; a form of a lemma the unit uses (roke → roka) before one it doesn't (pil → pila);
      // a gloss the unit's English has (peti → fifth, not to sing)
      if (norm(x.lemma) === key) x.score += 1.5
      else x.score += ctx.sl.has(norm(x.lemma)) ? 1 : -0.5
      if (wordsOf(x.gloss).some(w => w.length > 2 && ctx.en.has(stem(w)))) x.score += 1
      if (FORM_GLOSS.test(x.gloss)) x.score -= 3
    }
    const rank = (p: string) => (POS_RANK.indexOf(p) < 0 ? 50 : POS_RANK.indexOf(p))
    cands.sort((a, b) => b.score - a.score || a.rank - b.rank || rank(a.pos) - rank(b.pos))
    const best = cands[0]
    if (FORM_GLOSS.test(best.gloss)) {
      // "… of oprostiti": the meaning of that lemma
      const of = best.gloss.match(/\bof\s+([\p{L}]+)\s*$/u)?.[1]
      if (of && depth === 0) {
        const m = this.meaning(of, base, ctx, 1)
        if (m) return { ...m, lemma: of }
      }
      return fallback
    }
    if (best.score < -0.5) return fallback
    return best
  }
}

/** An English word without its -s, -es, -ed, -ing (hands → hand, baking → bak, bake → bak). */
const stem = (w: string) => w.toLowerCase().replace(/(ing|ed|es|e|s)$/, '')

/** A gloss short enough for the word box: its first sense, without brackets, at most about 26 characters. */
function shortGloss(g: string): string {
  let s = g.replace(/\s*\([^)]*\)/g, '').replace(/\s+/g, ' ').trim()
  if (s.length > 24) s = s.split(/[;,]/)[0].trim()
  if (s.length > 30) s = `${s.slice(0, 28).replace(/\s+\S*$/, '')} …`
  return s
}

// --- one unit's sheet --------------------------------------------------------------------------------------------

export function buildSheet(c: Content, unit: Json, n: number, of: number, o: Options): Sheet {
  const base = o.base
  const notes: string[] = []
  const fill = (s: string) => fillLearner(s, o)
  const pages = (unit.grammar ?? []).map((id: string) => c.pages.get(id)).filter(Boolean) as Json[]
  const steps: Json[] = unit.intro?.steps ?? []

  // what the unit is about, to keep to it: its intro, examples, table, words, exercises
  const corpus = new Set<string>()
  const english = new Set<string>()
  const addCorpus = (s: string | undefined) => {
    for (const w of wordsOf(plain(s ?? ''))) corpus.add(norm(w))
  }
  const addEnglish = (s: string | undefined) => {
    for (const w of wordsOf(plain(s ?? ''))) english.add(stem(w))
  }
  for (const s of steps) {
    addCorpus(tr(s.say, 'en'))
    addEnglish(tr(s.say, base))
    for (const e of s.examples ?? []) {
      addCorpus(e.sl)
      addEnglish(tr(e, base))
    }
    for (const r of s.rows ?? []) for (const cell of r) if (typeof cell === 'string') addCorpus(cell)
  }
  for (const w of unit.words ?? []) addCorpus(w)
  for (const sheet of [...(unit.worksheets ?? []), unit.test ?? {}]) addCorpus(JSON.stringify(sheet.exercises ?? []).replace(/[{}[\]":,]/g, ' '))

  // --- the rule: each step of the intro in a line or two ---
  const rule: string[] = []
  for (const s of steps) {
    const all = sentences(fill(tr(s.say, base))).filter(x => x.trim())
    if (all.length) rule.push(condense(all))
  }

  // --- the tables ---
  const tables = chooseTables(unit, pages, o, corpus)

  // the forms the tables show, bold in the examples: the endings marked, and the forms that are a cell of one word
  const rowsWith = new Map<string, number>()
  const formWords = new Set<string>()
  for (const t of tables.filter(t => !t.optional)) {
    for (const r of t.rows) {
      const inRow = new Set<string>()
      for (const cell of r.slice(1)) {
        if (!cell.sl || cell.tr) continue
        for (const item of cell.sl.split(/,\s*|\s+\/\s+/)) {
          const ws = item.match(/[\p{L}\p{M}⟦⟧]+/gu) ?? []
          for (const w of ws) {
            const k = norm(plain(w))
            inRow.add(k)
            if (w.includes('⟦') || ws.length === 1) formWords.add(k)
          }
        }
      }
      for (const k of inRow) rowsWith.set(k, (rowsWith.get(k) ?? 0) + 1)
    }
  }
  const forms = new Set([...formWords].filter(k => (rowsWith.get(k) ?? 0) < 3 || k.length > 3))
  const boldForms = (s: string) => s.replace(/[\p{L}\p{M}]+/gu, w => (forms.has(norm(w)) ? `**${w}**` : w)).replace(/\*\*(\s+)\*\*/g, '$1')

  // --- the examples: the first of each step, then the second ones, at most six, in the intro's order ---
  const exampleList: (PExample & { at: number })[] = []
  const all = steps.flatMap((s, i) => (s.examples ?? []).map((e: Json, k: number) => ({ e, at: i * 10 + k, k })))
  for (const { e, at } of [...all.filter(x => x.k === 0), ...all.filter(x => x.k > 0)]) {
    if (exampleList.length >= 6) break
    const sl = fill(e.sl)
    if (!sl || sl.includes('\u0000') || exampleList.some(x => plain(x.sl) === sl)) continue
    const t = tr(e, base)
    exampleList.push({ sl: boldForms(sl), tr: fill(t === e.sl ? (e.en ?? '') : t), at })
  }
  exampleList.sort((a, b) => a.at - b.at)
  const examples: PExample[] = exampleList.map(({ sl, tr }) => ({ sl, tr }))

  // --- check yourself: from the worksheets (the test stays the app's), else the test ---
  const check = chooseCheck(unit, base, o)
  const used = new Set(check.map(x => norm(plain(x.q.replace(/_+/g, '')))))

  // --- watch out ---
  const watch = chooseWatch(unit, pages, steps, base, corpus, used, o)

  // --- the words ---
  // the unit's own glosses: "**miza** (table)" in its intro
  const glosses = new Map<string, string>()
  for (const s of steps) {
    for (const m of tr(s.say, base).matchAll(/\*\*([^*]+)\*\*\s*\(([^)]{2,40})\)/g)) {
      const k = norm(m[1])
      if (!glosses.has(k) && !/[,.;]/.test(m[1]) && !/\*\*/.test(m[2])) glosses.set(k, m[2].trim())
    }
  }
  const cellItems = new Set<string>()
  const tableWords = new Set<string>()
  for (const t of tables) {
    for (const r of t.rows) {
      for (const cell of r) {
        if (!cell.sl) continue
        for (const item of plain(cell.sl).split(/[,?!.]\s*|\s+\/\s+|\s*·\s*/)) if (item.trim()) cellItems.add(norm(item))
        for (const w of wordsOf(plain(cell.sl))) tableWords.add(norm(w))
      }
    }
  }
  // the forms the intro makes from a lemma: "piti → **pil**", "iti → **šel, šla**"
  const lemmaOf = new Map<string, string>()
  for (const s of steps) {
    for (const m of tr(s.say, 'en').matchAll(/([\p{L}]+)\s*→\s*\*\*([^*]+)\*\*/gu)) {
      for (const f of m[2].split(/,\s*/)) if (/^[\p{L}]+$/u.test(f.trim())) lemmaOf.set(norm(f), m[1])
    }
  }
  const ctx: WordContext = { sl: corpus, en: english, glosses, lemmaOf }
  const words: PWord[] = []
  let wiktionary = false
  for (const w of unit.words ?? []) {
    // names (Gorica, Nova Gorica) are places of the village's map, not words to learn here
    if (/^\p{Lu}/u.test(w)) continue
    // the table shows it already (jaz, kdo?, ne morem, imenovati se)
    if (cellItems.has(norm(w))) continue
    const single = !/\s/.test(w)
    const m = c.dictionary.meaning(w, base, ctx)
    if (!m) {
      notes.push(`no meaning for the word "${w}"`)
      continue
    }
    // a form, or a little word, the table has (bom, boš …; v, na in "v šoli")
    if (single && tableWords.has(norm(w)) && (norm(m.lemma) !== norm(w) || !['noun', 'adj'].includes(m.pos))) continue
    if (m.wiktionary) wiktionary = true
    words.push({
      word: w,
      ...(norm(m.lemma) !== norm(w) ? { lemma: m.lemma } : {}),
      ...(m.gender && m.pos === 'noun' ? { gender: m.gender } : {}),
      gloss: shortGloss(m.gloss),
      ...(m.wiktionary ? { wiktionary: true } : {}),
    })
  }

  const review = String(unit.review ?? '')
  const reviewed = /\breviewed by\b/i.test(review) && !/\bnot reviewed\b/i.test(review)
  return {
    id: unit.id,
    n,
    of,
    level: unit.level,
    language: unit.language ?? c.language,
    emoji: unit.emoji ?? '📘',
    title: { sl: tr(unit.title, unit.language ?? c.language), tr: tr(unit.title, base) },
    goal: unit.goal ? fill(tr(unit.goal, base)) : undefined,
    rule,
    tables,
    examples,
    watch,
    words,
    check,
    unreviewed: unit.machine_written !== false || !reviewed,
    wiktionary,
    notes,
  }
}

// --- tables ------------------------------------------------------------------------------------------------------

function cellOf(cell: Text, o: Options): PCell {
  if (typeof cell === 'string') return { sl: fillLearner(cell, o) }
  const sl = cell.sl
  const t = cell[o.base] ?? cell.en
  if (sl && t && norm(sl) !== norm(t)) return { sl, tr: t }
  if (sl) return { sl }
  return { tr: t ?? '' }
}

function tableOf(t: Json, o: Options, from: string): PTable | null {
  if (!t?.head?.length || !t?.rows?.length) return null
  // rows about later levels ("orodnik (A2)") belong on their level's sheet
  const rows = (t.rows as Text[][]).filter(r => !r.some(cell => /\b(A2|B1|B2|C1)\b/.test(typeof cell === 'string' ? cell : JSON.stringify(cell))))
  if (!rows.length) return null
  return { head: t.head.map((h: Text) => cellOf(h, o)), rows: rows.map(r => r.map(cell => cellOf(cell, o))), from }
}

/** The Slovene words of a table's cells, normalized. */
function wordsOfTable(t: PTable): Set<string> {
  const out = new Set<string>()
  for (const r of t.rows) for (const cell of r) if (cell.sl) for (const w of wordsOf(plain(cell.sl))) out.add(norm(w))
  return out
}

const covered = (a: Set<string>, b: Set<string>) => (a.size ? [...a].filter(x => b.has(x)).length / a.size : 1)

/**
 * The unit's table (its intro's, built step by step), or a grammar page's when that shows the same in fewer rows
 * (biti: the page's persons × yes/no instead of the intro's list); and one more of the unit's pages when it adds forms
 * the unit uses (spol: the adjectives; stevila: the ordinals), marked optional (the first thing left out to fit).
 */
function chooseTables(unit: Json, pages: Json[], o: Options, corpus: Set<string>): PTable[] {
  const steps: Json[] = unit.intro?.steps ?? []
  const intro = unit.intro?.table?.head ? tableOf({ head: unit.intro.table.head, rows: steps.flatMap(s => s.rows ?? []) }, o, 'intro') : null
  const pageTables: PTable[] = []
  for (const p of pages) {
    if ((p.level ?? 'A1') > unit.level) continue
    const t = tableOf(p.table, o, `grammar:${p.id}`)
    if (t) pageTables.push(t)
    const ct = tableOf(p.card?.table, o, `card:${p.id}`)
    if (ct) pageTables.push(ct)
  }
  let primary = intro
  if (intro) {
    const f = wordsOfTable(intro)
    const better = pageTables
      .filter(t => t.rows.length < intro.rows.length && t.head.length >= intro.head.length && covered(f, wordsOfTable(t)) >= 0.9)
      .sort((a, b) => a.rows.length - b.rows.length)[0]
    if (better) primary = better
  } else primary = pageTables[0] ?? null
  if (!primary) return []
  const out: PTable[] = [primary]
  const shown = wordsOfTable(primary)
  const pageOf = (t: PTable) => t.from.split(':')[1]
  const relevance = (t: PTable) => covered(wordsOfTable(t), corpus)
  const extra = pageTables
    .filter(t => t !== primary && (primary.from === 'intro' || pageOf(t) !== pageOf(primary)))
    .filter(t => covered(wordsOfTable(t), shown) < 0.5 && relevance(t) >= 0.5 && t.rows.length <= 12)
    // the page's own table before its card's
    .sort((a, b) => relevance(b) - relevance(a) || (a.from.startsWith('grammar') ? -1 : 1))[0]
  if (extra) out.push({ ...extra, optional: true })
  for (const t of out) markEndings(t)
  return out
}

/**
 * Marks the endings in a table's Slovene cells ⟦…⟧: by a column of endings (-a, -o, -e: miz⟦a⟧); down a column of one
 * word's forms (ima⟦m⟧, ima⟦š⟧; ovc⟦i⟧, ovc⟦e⟧; delal⟦a⟧ sem); or against the row's first form when the table changes
 * one form into another (kava → kav⟦o⟧, sem → ⟦ni⟧sem, pes → p⟦sa⟧, Imam čas. → ⟦N⟧imam čas⟦a⟧.).
 */
export function markEndings(t: PTable) {
  const isEnding = (s: string) => /^(-[\p{L}]+|—)(\s*,\s*-[\p{L}]+)*$/u.test(s.trim())
  const width = t.head.length
  const slOnly = (cell: PCell | undefined) => !!cell?.sl && !cell.tr

  // 1. a column of endings
  const endingCol = [...Array(width).keys()].find(j => t.rows.filter(r => r[j]?.sl && isEnding(r[j].sl!)).length >= Math.max(1, t.rows.length * 0.6))
  if (endingCol !== undefined) {
    for (const r of t.rows) {
      const ends = (r[endingCol]?.sl ?? '').split(/,\s*/).map(e => e.replace(/^-/, '')).filter(e => e && e !== '—')
      r.forEach((cell, j) => {
        if (j === endingCol || j === 0 || !slOnly(cell) || !ends.length) return
        cell.sl = cell
          .sl!.split(/,\s*/)
          .map(item =>
            item.replace(/([\p{L}]+)(?=[^\p{L}]*$)/u, w => {
              const e = ends.filter(x => w.endsWith(x) && w.length > x.length).sort((a, b) => b.length - a.length)[0]
              return e ? `${w.slice(0, w.length - e.length)}⟦${e}⟧` : w
            }),
          )
          .join(', ')
      })
    }
    return
  }

  // the row's form a cell is changed from: the nearest Slovene cell to its left (sem → nisem, delam → delaj)
  const pairMark = (baseWord: string, w: string): string | null | '' => {
    const a = norm(baseWord)
    const b = norm(w)
    if (a === b) return ''
    if (a.length < 2) return null
    if (b.endsWith(a) && b.length - a.length <= 3) return `⟦${w.slice(0, w.length - baseWord.length)}⟧${w.slice(w.length - baseWord.length)}`
    let k = 0
    while (k < a.length && k < b.length && a[k] === b[k]) k++
    if (k >= 2 && k >= Math.min(a.length, b.length) / 2 && b.length - k <= 4 && b.length > k) return `${w.slice(0, k)}⟦${w.slice(k)}⟧`
    return null
  }
  /** A cell against its row's form to the left: its words paired from the end, the words added in front marked whole. */
  const againstRow = (i: number, j: number): { next: string; related: boolean; added: boolean } | null => {
    const r = t.rows[i]
    let b = j - 1
    while (b >= 0 && !slOnly(r[b])) b--
    if (b < 0 || !slOnly(r[j])) return null
    const baseItems = plain(r[b].sl!).split(/,\s*/)
    const its = r[j].sl!.split(/,\s*/)
    if (its.length !== baseItems.length) return { next: r[j].sl!, related: false, added: false }
    let relatedItems = 0
    let added = false
    const next = its.map((item, k) => {
      const bw = wordsOf(baseItems[k])
      const parts = item.split(/(\s+)/)
      const at = parts.map((x, q) => (/[\p{L}]/u.test(x) ? q : -1)).filter(q => q >= 0)
      const out = [...parts]
      // the head word (the last) is the same word or a form of it: kruh | kruha, šola | v šoli (not ena ovca | en brat)
      const lastBase = bw[bw.length - 1]
      const lastWord = parts[at[at.length - 1]]?.match(/[\p{L}\p{M}]+/u)?.[0]
      if (!lastBase || !lastWord || pairMark(lastBase, lastWord) === null) return item
      relatedItems++
      for (let p = 1; p <= at.length; p++) {
        const q = at[at.length - p]
        const word = parts[q].match(/[\p{L}\p{M}]+/u)?.[0]
        if (!word) continue
        const bword = bw[bw.length - p]
        if (bword === undefined) {
          if (at.length - bw.length <= 2) {
            out[q] = parts[q].replace(word, `⟦${word}⟧`)
            added = true
          }
          continue
        }
        const m = pairMark(bword, word)
        if (m) out[q] = parts[q].replace(word, m)
      }
      return out.join('')
    })
    // a cell of several forms counts when most of them are made from theirs (pes → psa is not, brat → brata is)
    return { next: next.join(', '), related: relatedItems >= Math.ceil(its.length / 2), added }
  }

  /** A column of one word's forms: the stem most of its cells share, in their first or last word (ima-m, ima-š …). */
  const downColumn = (j: number): Map<number, string> => {
    const out = new Map<number, string>()
    const cells = t.rows.map((r, i) => ({ i, w: slOnly(r[j]) && !r[j].sl!.includes(',') ? r[j].sl! : '' })).filter(x => x.w && wordsOf(x.w).length <= 2 && !/[.!?]$/.test(x.w))
    if (cells.length < 3) return out
    const need = Math.max(2, Math.ceil(cells.length * 0.66))
    let best = { stem: '', at: 0 as 0 | -1, share: 0 }
    for (const at of [0, -1] as const) {
      const pick = (w: string) => {
        const ws = wordsOf(w)
        return ws[at === 0 ? 0 : ws.length - 1] ?? ''
      }
      for (const { w } of cells) {
        const word = norm(pick(w))
        for (let k = 3; k <= word.length; k++) {
          const p = word.slice(0, k)
          const share = cells.filter(x => norm(pick(x.w)).startsWith(p)).length
          if (share >= need && (p.length > best.stem.length || (p.length === best.stem.length && share > best.share))) best = { stem: p, at, share }
        }
      }
    }
    if (!best.stem) return out
    for (const { i, w } of cells) {
      const ws = wordsOf(w)
      const word = ws[best.at === 0 ? 0 : ws.length - 1]
      if (!word || !norm(word).startsWith(best.stem)) continue
      const rest = word.slice(best.stem.length)
      if (!rest || rest.length > 3) continue
      const idx = best.at === 0 ? w.indexOf(word) : w.lastIndexOf(word)
      out.set(i, `${w.slice(0, idx)}${word.slice(0, best.stem.length)}⟦${rest}⟧${w.slice(idx + word.length)}`)
    }
    return out
  }

  for (let j = 1; j < width; j++) {
    // 2. a column of forms made from the column before (nikalno from pritrdilno, the object from the base form), or
    // 3. a column of one word's forms; whichever marks more of its cells. On a tie the row's form wins (sem → ⟦ni⟧sem,
    // not nis⟦em⟧), unless it only relates by a word added in front (sva delala: delal⟦a⟧, not ⟦sva⟧ dela⟦la⟧)
    const plans = t.rows.map((_, i) => ({ i, p: againstRow(i, j) })).filter(x => x.p) as { i: number; p: { next: string; related: boolean; added: boolean } }[]
    const byRow = plans.length && plans.filter(x => x.p.related).length >= Math.max(1, plans.length * 0.6) ? plans.filter(x => x.p.related && x.p.next.includes('⟦')) : []
    const byColumn = downColumn(j)
    const rowAdds = byRow.some(x => x.p.added)
    if (byRow.length && (byColumn.size < byRow.length || (byColumn.size === byRow.length && !rowAdds))) {
      for (const { i, p } of byRow) t.rows[i][j].sl = p.next
    } else for (const [i, next] of byColumn) t.rows[i][j].sl = next
  }
}

// --- check yourself ----------------------------------------------------------------------------------------------

/** "Dopolni · Fill in" → its English half for an English sheet, else its Slovene one (the content has no other). */
function taskOf(instruction: string | undefined, base: string): string | undefined {
  if (!instruction) return undefined
  const parts = instruction.split(' · ')
  return base === 'en' ? (parts[1] ?? parts[0]) : parts[0]
}

/** One gap a sentence: each gap of a gap text in its sentence, the sentence's other gaps filled (their cue dropped). */
function gapItems(e: Json, o: Options): { q: string; a: string; hint?: string }[] {
  const text = fillLearner(e.text, o)
  const gaps: Json[] = e.gaps ?? []
  const out: { q: string; a: string; hint?: string }[] = []
  let g = 0
  for (const s of sentences(text)) {
    const count = (s.match(/_{3,}/g) ?? []).length
    for (let k = 0; k < count; k++) {
      let j = 0
      const q = s.replace(/_{3,}(\s*\([^)]*\))?/g, (all: string) => {
        const here = j++
        return here === k ? all.replace(/_{3,}/, '___') : fillLearner(gaps[g + here]?.accept?.[0] ?? '…', o)
      })
      const gap = gaps[g + k]
      if (gap?.accept?.[0]) out.push({ q, a: fillLearner(gap.accept[0], o), hint: gap.hint })
    }
    g += count
  }
  return out
}

function chooseCheck(unit: Json, base: string, o: Options): PCheck[] {
  type Cand = PCheck & { len: number }
  const lists: Cand[][] = []
  // the hints are English (COURSE.md): on another base's sheet the question stands without them
  const hintOf = (h: string | undefined) => (h && h.length <= 26 && base === 'en' ? h : undefined)
  const fromExercises = (exercises: Json[], where: string) => {
    for (const e of exercises) {
      const task = taskOf(e.instruction, base)
      const list: Cand[] = []
      if (e.type === 'gaptext') {
        for (const x of gapItems(e, o)) list.push({ task, q: x.q, hint: hintOf(x.hint), a: x.a, line: false, from: where, len: x.q.length })
      } else if (e.type === 'transform') {
        for (const it of e.items ?? []) {
          if (!it.accept?.[0]) continue
          const q = fillLearner(it.from, o)
          list.push({ task, q, a: fillLearner(it.accept[0], o), line: true, from: where, len: q.length + it.accept[0].length })
        }
      } else if (e.type === 'choice' && /_{3,}/.test(e.prompt ?? '')) {
        const opts: string[] = e.options ?? []
        const q = fillLearner(e.prompt, o).replace(/_{3,}/, '___')
        list.push({ task, q, hint: opts.join(' / '), a: opts[e.answer] ?? '', line: false, from: where, len: q.length })
      } else if (e.type === 'cloze' && e.text) {
        const q = fillLearner(e.text, o).replace(/_{3,}/, '___')
        list.push({ task, q, hint: hintOf(e.hint), a: fillLearner(e.accept?.[0] ?? '', o), line: false, from: where, len: q.length })
      }
      const fit = list.filter(x => x.a && !x.q.includes('\u0000') && x.len <= 80 && x.a.length <= 42)
      if (fit.length) lists.push(fit)
    }
  }
  const sheets: Json[] = unit.worksheets ?? []
  // the later sheets first: the forms in sentences and the transforms, before the tables
  for (const s of [...sheets].reverse()) fromExercises(s.exercises ?? [], `sheet:${s.id}`)
  if (lists.flat().length < 5) fromExercises(unit.test?.exercises ?? [], 'test')
  const picked: Cand[] = []
  // round robin over the exercises, so the first five are of different kinds; ten for a sheet of two pages
  for (let round = 0; picked.length < 10 && lists.some(l => l.length > round); round++) {
    for (const l of lists) {
      if (picked.length >= 10) break
      const x = l[round * 2] ?? l[round * 2 + 1]
      if (x && !picked.some(y => y.q === x.q)) picked.push(x)
    }
  }
  // as on a worksheet: the items of one task together, under one instruction (the first five, then the rest)
  const group = (xs: Cand[]) => {
    const tasks = [...new Set(xs.map(x => x.task ?? ''))]
    return tasks.flatMap(t => xs.filter(x => (x.task ?? '') === t))
  }
  return [...group(picked.slice(0, 5)), ...group(picked.slice(5))].map(({ len: _len, ...item }) => item)
}

// --- watch out ---------------------------------------------------------------------------------------------------

function chooseWatch(unit: Json, pages: Json[], steps: Json[], base: string, corpus: Set<string>, used: Set<string>, o: Options): PWatch[] {
  const out: PWatch[] = []
  const add = (w: PWatch) => {
    if (out.length >= 3) return
    const k = norm(plain(w.wrong))
    const r = norm(plain(w.right))
    if (!k || r === k || out.some(x => norm(plain(x.wrong)) === k || norm(plain(x.right)) === r)) return
    if (used.has(r.replace(/_+/g, ''))) return
    out.push(w)
  }
  const inUnit = (s: string) => {
    const ws = wordsOf(plain(s)).map(norm)
    return ws.length > 0 && ws.filter(w => corpus.has(w)).length >= Math.ceil(ws.length / 2)
  }
  // the unit's sentences, to show a lone form in one ("ne sem" → "Ne sem utrujen.")
  const sentencesOfUnit: string[] = [
    ...steps.flatMap(s => (s.examples ?? []).map((e: Json) => fillLearner(e.sl, o))),
    ...pages.flatMap(p => (p.examples ?? []).map((e: Json) => fillLearner(e.sl, o))),
  ].filter(s => s && !s.includes('\u0000'))

  /** The bold spans of a text: a list ("**nisem, nisi, ni**") split into its items, sentences into sentences. */
  const boldsOf = (md: string) =>
    [...md.matchAll(/\*\*([^*]+)\*\*/g)].flatMap(m => {
      const s = m[1].trim()
      const sents = s.split(/(?<=[.!?])\s+/)
      return sents.length > 1 ? sents : s.includes(',') && !/[.!?]$/.test(s) ? s.split(/,\s*/) : [s]
    })
  /** The why of a pattern: its sentence, or, when that is only "Never ne sem.", what the sentence before says. */
  const whyOf = (sentence: string, before?: string) => {
    if (base !== 'en') return undefined
    let p = plain(sentence)
      .replace(/\s*\((?:never|not)\s+[^)]*\)/gi, '')
      .replace(/([?!])\./g, '$1')
    if (p.length < 40 && before) {
      const lead = plain(before).split(':')[0].trim()
      if (lead.length <= 90) p = `${lead}.`
    }
    return p.length > 120 ? `${p.slice(0, 117).replace(/\s+\S*$/, '')} …` : p
  }

  /** The right form for a wrong one: the most alike bold of the text, put in a sentence of the unit when it's one word. */
  const pairUp = (wrong: string, md: string, sentence: string, before: string | undefined, from: string) => {
    const ws = wordsOf(wrong)
    let best = ''
    let score = 0
    for (const b of boldsOf(md)) {
      const bw = wordsOf(b)
      // a one-word wrong form against the most alike word of a phrase (imenujem in "se imenuje Maja")
      const s = ws.length === 1 && bw.length > 1 ? Math.max(...bw.map(x => alike(wrong, x))) - 0.05 : alike(wrong, b)
      if (s > score) {
        score = s
        best = b
      }
    }
    if (!best || score < 0.5 || !inUnit(best)) return
    let right = best.replace(/^[,;:\s]+|[,;:\s]+$/g, '')
    let wrongText = wrong
    const bw = wordsOf(right)
    if (ws.length === 1 && bw.length > 1) {
      // the phrase with the wrong word in place of its alike one
      const near = bw.reduce((a, x) => (alike(wrong, x) > alike(wrong, a) ? x : a), bw[0])
      wrongText = right.replace(new RegExp(`(?<![\\p{L}])${near}(?![\\p{L}])`, 'u'), wrong)
    } else if (bw.length === 1) {
      // a lone form: shown in a sentence of the unit that has it
      const ex = sentencesOfUnit.find(s => wordsOf(s).some(x => norm(x) === norm(right)) && s.length <= 48)
      if (ex) {
        const w = wordsOf(ex).find(x => norm(x) === norm(right))!
        const cap = (x: string) => (w[0] === w[0].toUpperCase() ? x[0].toUpperCase() + x.slice(1) : x)
        wrongText = ex.replace(w, cap(wrong))
        right = ex
      }
    }
    if (/[.!?]$/.test(right) && !/[.!?]$/.test(wrongText)) wrongText += right.slice(-1)
    add({ wrong: wrongText, right, why: whyOf(sentence, before), from })
  }

  /** "**R** (never *W*)", "never *W*", "*W* is wrong", "not *W*" in a text. */
  const explicit = (md: string, from: string) => {
    if (!md) return
    for (const para of fillLearner(md, o).split(/\n+/)) {
      const ss = sentences(para)
      ss.forEach((s, k) => {
        for (const m of s.matchAll(/\*\*([^*]+)\*\*\s*\((?:never|not)\s+\*([^*]+)\*\)/gi)) {
          if (inUnit(m[1])) {
            const right = m[1].trim()
            let wrong = m[2].trim()
            if (/[.!?]$/.test(right) && !/[.!?]$/.test(wrong)) wrong += right.slice(-1)
            add({ wrong, right, why: whyOf(s.replace(m[0], m[1]), ss[k - 1]), from })
          }
        }
        if (/\((?:never|not)\s+\*/i.test(s)) return
        for (const m of s.matchAll(/\b(?:never|not)\s+\*([^*]+)\*(?:\s+or\s+\*([^*]+)\*)?/gi)) {
          for (const w of [m[1], m[2]].filter(Boolean)) pairUp(w, para, s, ss[k - 1], from)
        }
        for (const m of s.matchAll(/\*([^*]+)\*\s+is wrong/gi)) pairUp(m[1], para, s, ss[k - 1], from)
      })
    }
  }
  for (const s of steps) explicit(tr(s.say, 'en'), 'intro')
  for (const p of pages) {
    explicit(tr(p.rule, 'en'), `grammar:${p.id}`)
    explicit(tr(p.card?.rule, 'en'), `card:${p.id}`)
    explicit(tr(p.card?.tip, 'en'), `card:${p.id}`)
  }

  // a choice's wrong option, the one its why names or else the most alike, in its sentence
  const choices: { e: Json; from: string }[] = [
    ...steps.filter(s => s.ask?.type === 'choice').map(s => ({ e: s.ask, from: 'intro' })),
    ...(unit.worksheets ?? []).flatMap((w: Json) => (w.exercises ?? []).filter((e: Json) => e.type === 'choice').map((e: Json) => ({ e, from: `sheet:${w.id}` }))),
  ]
  const named = (e: Json, opt: string) => new RegExp(`(?<![\\p{L}])\\*?${opt}\\*?(?![\\p{L}])`, 'iu').test((e.explain ?? '').replace(/\*\*[^*]+\*\*/g, ''))
  const scored = choices
    .map(({ e, from }) => {
      const prompt = fillLearner(e.prompt ?? '', o)
      const answer = e.options?.[e.answer]
      if (!answer || !/_{3,}/.test(prompt) || wordsOf(prompt).length < 2 || prompt.includes('\u0000')) return null
      // an almost-right option (a spoken form, a near form) is never shown as a mistake
      const wrongs = (e.options as string[]).filter((_, i) => i !== e.answer && !(e.almost ?? []).includes(i))
      if (!wrongs.length) return null
      const byName = wrongs.find(x => named(e, x))
      const wrong = byName ?? [...wrongs].sort((a, b) => alike(b, answer) - alike(a, answer))[0]
      const sentence = (x: string) => {
        const s = prompt.replace(/\s*\((?![^)]*___)[^)]*\)\s*$/, '').replace(/_{3,}/, x)
        return prompt.trimStart().startsWith('___') ? s[0].toUpperCase() + s.slice(1) : s
      }
      const why = base === 'en' ? plain((e.explain ?? '').split('\n')[0]) : undefined
      return { w: { wrong: sentence(wrong), right: sentence(answer), why, from }, rank: byName ? 0 : 1 }
    })
    .filter(Boolean) as { w: PWatch; rank: number }[]
  for (const { w } of scored.sort((a, b) => a.rank - b.rank)) add(w)

  // a gap with the word to change in brackets: left as it is, it's the mistake ("To je ___ (moj) hiša." → moj hiša)
  for (const sheet of unit.worksheets ?? []) {
    for (const e of (sheet.exercises ?? []).filter((x: Json) => x.type === 'gaptext')) {
      gapItems(e, o).forEach((x, k) => {
        if (used.has(norm(plain(x.q.replace(/_+/g, ''))))) return
        const m = x.q.match(/_{3,}\s*\(([^)]+)\)/)
        const cue = m?.[1]?.trim()
        if (!m || !cue || /\s/.test(cue) || norm(cue) === norm(x.a) || alike(cue, x.a) < 0.4) return
        const gap = (e.gaps ?? [])[k]
        const why = base === 'en' ? plain(gap?.explain ?? gap?.hint ?? '') || undefined : undefined
        // a gap that opens the sentence opens it with a capital, the wrong form too
        const first = /^[»„"]?\s*_{3,}/.test(x.q)
        const cap = (w: string) => (first ? w[0].toUpperCase() + w.slice(1) : w)
        add({ wrong: x.q.replace(m[0], cap(cue)), right: x.q.replace(m[0], cap(x.a)), why, from: `sheet:${sheet.id}` })
      })
    }
  }
  return out
}
