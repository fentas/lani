// A printed overview (companion/COURSE.md, "Printed sheets", "The cases overview"): the six Slovene cases on three A4
// pages, made from the grammar book's pages as they are (skloni, the six case pages' notes, the numbers, the declension
// tables, the pronouns, what the genitive, the dative and the instrumental do) and, where they hold what no page has, the
// course's units' tables (kje-kam-od-kod: Kje? Kam? Od kod?; pridevniki-skloni: an adjective through the cases). Nothing
// is written for it but its headings: a page fixed or a unit added prints as it is the next time. fit.js fits it to its
// pages as it fits a unit (tighter, then without its optional parts).
import { levelOfUnit, type Doc } from './build'
import { BASES, cellHtml, documentHtml, esc, h2, HOUSE, LANGUAGE_NAMES, md, SL } from './html'
import { condense, fillLearner, norm, plain, sentences, tr, type Content, type Options, type PCell, type PTable } from './sheet'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Json = any

/** The overviews there are, by the grammar page they start from: its file's name and what it is made of. */
export const OVERVIEWS: Record<string, { name: string }> = {
  skloni: { name: 'pregled-sklonov' },
}

/** The overview's headings (the only words written for it): Slovene first, then the base. */
const HEAD = {
  sl: {
    overview: 'Pregled',
    glance: 'Šest sklonov',
    use: 'raba',
    howto: 'Kako najdeš sklon',
    numbers: 'Števila',
    where: 'Kje? Kam? Od kod?',
    endings: 'Končnice',
    adjectives: 'Pridevniki',
    pronouns: 'Osebni zaimki',
    uses: 'Rodilnik, dajalnik, orodnik',
    school: 'Šola',
  },
  en: {
    overview: 'Overview',
    glance: 'The cases at a glance',
    use: 'what it does',
    howto: 'How to find the case',
    numbers: 'Numbers and the noun',
    where: 'Where? Where to? From where?',
    endings: 'The endings: singular, dual, plural',
    adjectives: 'Adjectives through the cases',
    pronouns: 'Personal pronouns',
    uses: 'What the genitive, dative and instrumental do',
    book: 'from the grammar book',
    unit: 'from the school’s unit',
  },
  de: {
    overview: 'Überblick',
    glance: 'Die Fälle auf einen Blick',
    use: 'wozu',
    howto: 'So findest du den Fall',
    numbers: 'Zahlen und Nomen',
    where: 'Wo? Wohin? Woher?',
    endings: 'Die Endungen: Singular, Dual, Plural',
    adjectives: 'Adjektive in den Fällen',
    pronouns: 'Personalpronomen',
    uses: 'Wozu Genitiv, Dativ und Instrumental',
    book: 'aus der Grammatik',
    unit: 'aus der Lektion der Schule',
  },
  it: {
    overview: 'Sintesi',
    glance: 'I casi in breve',
    use: 'a che serve',
    howto: 'Come trovare il caso',
    numbers: 'I numeri e il nome',
    where: 'Dove? Verso dove? Da dove?',
    endings: 'Le desinenze: singolare, duale, plurale',
    adjectives: 'Gli aggettivi nei casi',
    pronouns: 'I pronomi personali',
    uses: 'A che cosa servono genitivo, dativo e strumentale',
    book: 'dalla grammatica',
    unit: 'dall’unità della scuola',
  },
}
type Heads = (typeof HEAD)['en']

/** A row of the cases at a glance: the case, its question, what it does, its endings, its prepositions, an example. */
export type GlanceRow = { n: string; name: PCell; question: string; does: string; endings: string; preps: PCell; example?: { sl: string; tr: string } }
/** A table with where it comes from (a page or a unit), its caption's title, and lines that go with it. */
export type OTable = { table: PTable; title: { sl: string; tr: string }; source: 'book' | 'unit'; notes: string[]; optional?: boolean }

export type Overview = {
  id: string
  name: string
  base: string
  language: string
  emoji: string
  title: { sl: string; tr: string }
  /** The levels of the pages it draws on: "A1–B1". */
  levels: string
  glance: { head: PCell[][]; rows: GlanceRow[] }
  howto: { lines: string[]; tip?: string }
  numbers?: OTable
  where?: OTable
  endings: OTable[]
  adjectives?: OTable
  pronouns?: OTable
  uses: OTable[]
  /** What it was made of: grammar:<id> and unit:<id>, in order (the generator's --explain, the checks). */
  sources: string[]
  unreviewed: boolean
}

const LEVEL_TALK = /\b(A1|A2|B1|B2|C1)\b/
const cell = (t: Json, base: string, o: Options): PCell => {
  if (typeof t === 'string') return { sl: fillLearner(t, o) }
  const sl = t?.sl
  // "singular (ednina)" under "ednina": the translation alone
  const x = sl ? (t?.[base] ?? t?.en)?.replace(new RegExp(`\\s*\\(${sl.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\)\\s*$`), '') : (t?.[base] ?? t?.en)
  if (sl && x && norm(sl) !== norm(x)) return { sl, tr: x }
  return sl ? { sl } : { tr: x ?? '' }
}
const cap = (s: string) => (s ? s[0].toLocaleUpperCase() + s.slice(1) : s)
/** A rule's paragraphs in the base, without the ones about which level learns what. */
const paragraphs = (rule: Json, base: string, o: Options) =>
  fillLearner(tr(rule, base), o)
    .split(/\n+/)
    .map(p => p.trim())
    .filter(p => p && !LEVEL_TALK.test(p))

/** A grammar page's or a unit's table as printed; rows about a later level left out, as on a unit's sheet. */
function tableOf(t: Json, from: string, base: string, o: Options): PTable | null {
  if (!t?.head?.length || !t?.rows?.length) return null
  const rows = (t.rows as Json[][]).filter(r => !r.some(c => LEVEL_TALK.test(typeof c === 'string' ? c : JSON.stringify(c))))
  if (!rows.length) return null
  return { head: t.head.map((h: Json) => cell(h, base, o)), rows: rows.map(r => r.map(c => cell(c, base, o))), from }
}

/**
 * The endings of a declension table marked against its words (the first row's first cell: "stol, brat"): each form's
 * part after the word's stem (the word less its final vowel), where the form starts with the stem: brat⟦je⟧, vas⟦jo⟧,
 * knjig⟦ama⟧; a form whose stem changes (oken, sester) or has no ending (knjig) is left as it is.
 */
export function markDeclension(t: PTable) {
  const words = (t.rows[0]?.[1]?.sl ?? '').split(/,\s*/).map(w => w.trim()).filter(Boolean)
  const stems = words.map(w => (/[aeo]$/i.test(w) ? w.slice(0, -1) : w))
  if (!stems.length) return
  for (const r of t.rows) {
    r.forEach((c, j) => {
      if (j === 0 || !c.sl || c.tr) return
      c.sl = c.sl
        .split(/,\s*/)
        .map((item, k) => {
          const stem = stems[k % stems.length]
          return item.replace(/([\p{L}]+)(?=[^\p{L}]*$)/u, w => (norm(w).startsWith(norm(stem)) && w.length > stem.length ? `${w.slice(0, stem.length)}⟦${w.slice(stem.length)}⟧` : w))
        })
        .join(', ')
    })
  }
}

/**
 * An adjective's endings in a table of it through the cases, marked against its base form, the first row's first word
 * of each column less its final vowel (velik, velika, veliko → velik): velik⟦ega⟧ gozda, v velik⟦i⟧ hiši, z velik⟦imi⟧;
 * a form whose stem changes (dober → dobrega) is left as it is.
 */
export function markAdjective(t: PTable) {
  for (let j = 1; j < t.head.length; j++) {
    const first = (t.rows[0]?.[j]?.sl ?? '').match(/[\p{L}]+/u)?.[0] ?? ''
    const stem = /[aeo]$/i.test(first) ? first.slice(0, -1) : first
    if (stem.length < 3) continue
    for (const r of t.rows) {
      const c = r[j]
      if (!c?.sl || c.tr) continue
      c.sl = c.sl.replace(/[\p{L}]+/gu, w => {
        const rest = w.slice(stem.length)
        return norm(w).startsWith(norm(stem)) && rest && rest.length <= 4 ? `${w.slice(0, stem.length)}⟦${rest}⟧` : w
      })
    }
  }
}

/** The case's form ("knjige", "o knjigi") bold in the example's sentence. */
function boldForm(sentence: string, form: string): string {
  const f = form.trim()
  if (!f) return sentence
  const re = new RegExp(`(?<![\\p{L}])(${f.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})(?![\\p{L}])`, 'iu')
  return re.test(sentence) ? sentence.replace(re, '**$1**') : sentence
}

/** The case's note among the skloni page's examples: the one whose note opens with the case's name in the base. */
function noteOf(examples: Json[], caseName: string, base: string): { e: Json; does: string } | undefined {
  for (const e of examples) {
    const note = tr(e.note, base)
    if (!note || !norm(note).startsWith(norm(caseName))) continue
    const rest = note.slice(note.indexOf('):') >= 0 ? note.indexOf('):') + 2 : caseName.length).trim()
    return { e, does: cap(sentences(rest)[0] ?? rest) }
  }
  return undefined
}

/** The overview [id] (a key of [OVERVIEWS]) of the content, in [o]'s base; null without it or without its pages. */
export function buildOverview(c: Content, id: string, o: Options): Overview | null {
  const spec = OVERVIEWS[id]
  const sk = c.pages.get(id)
  if (!spec || !sk || id !== 'skloni') return null
  const base = BASES[o.base] ? o.base : 'en'
  const sources: string[] = [`grammar:${id}`]
  const used: Json[] = [sk]
  const page = (pid: string) => {
    const p = c.pages.get(pid)
    if (p) {
      if (!sources.includes(`grammar:${pid}`)) sources.push(`grammar:${pid}`)
      used.push(p)
    }
    return p
  }
  const unit = (uid: string) => {
    const u = c.units.get(uid)
    // a unit of the course's (written, not planned), with its intro's table
    if (!u?.intro?.table?.head || !levelOfUnit(c, uid)) return undefined
    sources.push(`unit:${uid}`)
    used.push(u)
    return u
  }
  const titled = (p: Json) => ({ sl: tr(p.title, c.language), tr: tr(p.title, base) })

  // --- the cases at a glance: the page's table (case, question, its form, prepositions), its card's (endings, the
  // question's short form) and its examples' notes (what each case does, a sentence with its form)
  const t = sk.table
  const cardRows: Json[][] = sk.card?.table?.rows ?? []
  const rows: GlanceRow[] = (t?.rows ?? []).map((r: Json[], i: number) => {
    const sl = tr(r[0], 'sl')
    const name = tr(r[0], base).replace(/\s*\([^)]*\)\s*$/, '')
    const cr = cardRows.find(x => norm(tr(x[0], 'sl')).includes(norm(sl)))
    const note = noteOf(sk.examples ?? [], name, base)
    const form = tr(r[2], 'sl')
    return {
      n: tr(cr?.[0], 'sl').match(/^\d+/)?.[0] ?? String(i + 1),
      name: { sl, tr: name },
      question: cr ? tr(cr[1], 'sl') : tr(r[1], 'sl'),
      does: note?.does ?? '',
      endings: cr ? tr(cr[2], 'sl') : '',
      preps: cell(r[3], base, o),
      ...(note ? { example: { sl: boldForm(fillLearner(note.e.sl, o), form), tr: fillLearner(tr(note.e, base), o) } } : {}),
    }
  })
  // the case, its question with its endings under it, what it does, its prepositions, an example
  const head: PCell[][] = [
    [cell(t.head[0], base, o)],
    [cell(t.head[1], base, o), cell(sk.card?.table?.head?.[2] ?? { sl: 'končnice', en: 'endings' }, base, o)],
    [{ sl: HEAD.sl.use, tr: (HEAD as Json)[base].use }],
    [cell(t.head[3], base, o)],
    [cell(t.head[2], base, o)],
  ]

  // --- how to find the case: the page's rule in a line or two a paragraph (not which level learns what), its card's tip
  const lines = paragraphs(sk.rule, base, o).map(p => condense(sentences(p), 330))
  const tipMd = sk.card?.tip ? tr(sk.card.tip, base).replace(/^\p{Extended_Pictographic}️?\s*/u, '').trim() : undefined

  // --- the numbers: their page's table, and what it says of the verb (its last paragraph)
  const sn = page('stevila-samostalniki')
  const nt = sn && tableOf(sn.table, 'grammar:stevila-samostalniki', base, o)
  const numbers: OTable | undefined = nt ? { table: nt, title: titled(sn), source: 'book', notes: paragraphs(sn.rule, base, o).slice(-1) } : undefined

  // --- Kje? Kam? Od kod?: the unit's table (one place, three questions), else the page on where to
  const kk = unit('kje-kam-od-kod')
  let where: OTable | undefined
  if (kk) {
    const wt = tableOf({ head: kk.intro.table.head, rows: kk.intro.steps.flatMap((s: Json) => s.rows ?? []) }, 'unit:kje-kam-od-kod', base, o)
    if (wt) where = { table: wt, title: { sl: tr(kk.title, c.language), tr: tr(kk.title, base) }, source: 'unit', notes: [] }
  } else {
    const kt = page('kam-tozilnik')
    const wt = kt && tableOf(kt.table, 'grammar:kam-tozilnik', base, o)
    if (wt) where = { table: wt, title: titled(kt), source: 'book', notes: [] }
  }

  // --- the endings: the three declension tables, each with what its page says beyond them (its first paragraph names
  // the words, the last which level learns what)
  const endings: OTable[] = []
  for (const pid of ['sklanjatev-moski', 'sklanjatev-zenski', 'sklanjatev-srednji']) {
    const p = page(pid)
    const dt = p && tableOf(p.table, `grammar:${pid}`, base, o)
    if (!dt) continue
    for (const r of dt.rows) if (r[0]?.sl) r[0] = { sl: r[0].sl.replace(/\s*\([^)]*\)\s*$/, '') }
    markDeclension(dt)
    endings.push({ table: dt, title: titled(p), source: 'book', notes: paragraphs(p.rule, base, o).slice(1).map(x => condense(sentences(x), 170)) })
  }

  // --- an adjective through the cases: the unit's table
  const pa = unit('pridevniki-skloni')
  let adjectives: OTable | undefined
  if (pa) {
    const at = tableOf({ head: pa.intro.table.head, rows: pa.intro.steps.flatMap((s: Json) => s.rows ?? []) }, 'unit:pridevniki-skloni', base, o)
    if (at) {
      markAdjective(at)
      adjectives = { table: at, title: { sl: tr(pa.title, c.language), tr: tr(pa.title, base) }, source: 'unit', notes: [] }
    }
  }

  // --- the pronouns: their page's table, and when the short form is used (its second and third paragraphs)
  const oz = page('osebni-zaimki')
  const pt = oz && tableOf(oz.table, 'grammar:osebni-zaimki', base, o)
  const pronouns: OTable | undefined = pt ? { table: pt, title: titled(oz), source: 'book', notes: paragraphs(oz.rule, base, o).slice(1, 3).map(x => condense(sentences(x), 150)) } : undefined

  // --- what the genitive, the dative and the instrumental do: their pages' tables
  const uses: OTable[] = []
  for (const pid of ['rodilnik', 'dajalnik', 'orodnik']) {
    const p = page(pid)
    const ut = p && tableOf(p.table, `grammar:${pid}`, base, o)
    if (ut) uses.push({ table: ut, title: titled(p), source: 'book', notes: [] })
  }

  const order = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']
  const lv = [...new Set(used.map(p => p.level).filter((l: Json) => order.includes(l)))].sort((a, b) => order.indexOf(a) - order.indexOf(b))
  const unreviewed = used.some(p => {
    const review = String(p.review ?? '')
    return p.machine_written !== false || !/\breviewed by\b/i.test(review) || /\bnot reviewed\b/i.test(review)
  })
  return {
    id,
    name: spec.name,
    base,
    language: c.language,
    emoji: sk.emoji ?? '🧩',
    title: { sl: tr(sk.card?.title ?? sk.title, c.language), tr: tr(sk.title, base) },
    levels: lv.length > 1 ? `${lv[0]}–${lv[lv.length - 1]}` : (lv[0] ?? ''),
    glance: { head, rows },
    howto: { lines, ...(tipMd ? { tip: tipMd } : {}) },
    ...(numbers ? { numbers } : {}),
    ...(where ? { where } : {}),
    endings,
    ...(adjectives ? { adjectives } : {}),
    ...(pronouns ? { pronouns } : {}),
    uses,
    sources,
    unreviewed,
  }
}

// --- HTML ------------------------------------------------------------------------------------------------------------

function otable(x: OTable, h: Heads, cls = ''): string {
  const t = x.table
  const glossCol = t.head.map((_, j) => j > 0 && t.rows.every(r => !r[j]?.sl))
  const head = `<thead><tr>${t.head.map((c, j) => `<th${glossCol[j] ? ' class="gc"' : ''}>${cellHtml(c, 'head')}</th>`).join('')}</tr></thead>`
  const body = t.rows
    .map(r => `<tr>${r.map((c, j) => (j === 0 ? `<th scope="row">${cellHtml(c, 'label')}</th>` : `<td${glossCol[j] ? ' class="gc"' : ''}>${cellHtml(c, 'cell')}</td>`)).join('')}</tr>`)
    .join('')
  const from = x.source === 'unit' ? `🏫 ${md(x.title.sl)}<span> · ${esc(h.unit)}</span>` : `📖 ${md(x.title.sl)}<span> · ${esc(h.book)}</span>`
  return `<div class="tbl${cls ? ` ${cls}` : ''}${x.optional ? ' opt' : ''}"><table class="forms o-t cols-${t.head.length}"><caption>${from}</caption>${head}<tbody>${body}</tbody></table></div>`
}

const notes = (xs: string[], cls = '') => (xs.length ? `<ul class="o-notes${cls ? ` ${cls}` : ''}">${xs.map(x => `<li>${md(x)}</li>`).join('')}</ul>` : '')

function header(v: Overview, h: Heads, mini: boolean): string {
  const mark = `<div class="mark">${HOUSE}<div><span class="lani">Lani</span><span class="school">${esc(SL.school)} · ${esc(LANGUAGE_NAMES[v.language] ?? v.language)}</span></div><span class="level">${esc(v.levels)}</span></div>`
  const num = `<div class="num"><small>${esc(HEAD.sl.overview)}</small>${v.glance.rows.length}</div>`
  if (mini) return `<header class="head mini"><div class="num">${v.glance.rows.length}</div><div class="titles"><h1><span class="emoji">${v.emoji}</span>${md(v.title.sl)}</h1><div class="sub">${esc(SL.continued)} · ${esc((BASES[v.base] ?? BASES.en).continued)}</div></div>${mark}</header>`
  const sub = v.title.tr && v.title.tr !== v.title.sl ? `<div class="sub">${md(v.title.tr)}</div>` : ''
  return `<header class="head">${num}<div class="titles"><h1><span class="emoji">${v.emoji}</span>${md(v.title.sl)}</h1>${sub}</div>${mark}</header>`
}

function glance(v: Overview, h: Heads): string {
  const g = v.glance
  const headRow = `<tr>${g.head.map((cs, j) => `<th class="g${j}">${cs.map(c => cellHtml(c, 'head')).join('')}</th>`).join('')}</tr>`
  const rows = g.rows
    .map(
      r =>
        `<tr><th scope="row" class="g0"><span class="o-n">${esc(r.n)}</span><span class="f">${md(r.name.sl ?? '')}</span>${r.name.tr ? `<span class="g">${md(r.name.tr)}</span>` : ''}</th>` +
        `<td class="g1"><span class="f q">${md(r.question)}</span>${r.endings ? `<span class="f e">${md(r.endings)}</span>` : ''}</td>` +
        `<td class="g2">${md(r.does)}</td>` +
        `<td class="g3">${r.preps.sl ? `<span class="f">${md(r.preps.sl)}</span>` : `<span class="g">${md(r.preps.tr ?? '')}</span>`}</td>` +
        `<td class="g4">${r.example ? `<span class="ex-sl">${md(r.example.sl)}</span><span class="ex-tr">${md(r.example.tr)}</span>` : ''}</td></tr>`,
    )
    .join('')
  return `<section class="sec o-glance">${h2('rule', HEAD.sl.glance, h.glance)}<table class="forms o-g"><thead>${headRow}</thead><tbody>${rows}</tbody></table></section>`
}

function footer(v: Overview, h: Heads): string {
  const b = BASES[v.base] ?? BASES.en
  const left = `Lani · ${esc(SL.school)} · ${esc(LANGUAGE_NAMES[v.language] ?? v.language)} · ${esc(HEAD.sl.overview)}: ${md(v.title.sl)}`
  const mid = v.unreviewed ? `${esc(SL.machine)} · ${esc(b.machine)}` : ''
  return `<footer class="foot"><span class="f-l">${left}</span><span class="f-m">${mid}</span><span class="pn"></span></footer>`
}

/** The overview's three pages: the cases and how to find them; the endings; the adjectives, pronouns and uses. */
export function overviewHtml(v: Overview): string {
  const h = ((HEAD as Json)[v.base] ?? HEAD.en) as Heads
  const howto = `<section class="sec o-howto">${h2('rule', HEAD.sl.howto, h.howto)}<ol class="o-how">${v.howto.lines.map(x => `<li>${md(x)}</li>`).join('')}${v.howto.tip ? `<li class="tip opt">${md(v.howto.tip)}</li>` : ''}</ol></section>`
  const numbers = v.numbers ? `<section class="sec o-numbers">${h2('table', HEAD.sl.numbers, h.numbers)}${otable(v.numbers, h)}${notes(v.numbers.notes)}</section>` : ''
  const where = v.where ? `<section class="sec o-where">${h2('examples', HEAD.sl.where, h.where)}${otable(v.where, h, 'wide')}</section>` : ''
  const one = `<div class="sheet"><div class="body">${header(v, h, false)}${glance(v, h)}<div class="o-duo">${howto}${numbers}</div>${where}</div>${footer(v, h)}</div>`

  const endings = v.endings.length
    ? `<section class="sec o-endings">${h2('table', HEAD.sl.endings, h.endings)}${v.endings
        .map(e => `<div class="o-decl">${otable(e, h)}${notes(e.notes)}</div>`)
        .join('')}</section>`
    : ''
  const adjectives = v.adjectives ? `<section class="sec o-adj">${h2('words', HEAD.sl.adjectives, h.adjectives)}${otable(v.adjectives, h, 'wide')}</section>` : ''
  const two = `<div class="sheet"><div class="body">${header(v, h, true)}${endings}${adjectives}</div>${footer(v, h)}</div>`

  const pronouns = v.pronouns ? `<section class="sec o-pron">${h2('watch', HEAD.sl.pronouns, h.pronouns)}<div class="o-decl">${otable(v.pronouns, h)}${notes(v.pronouns.notes)}</div></section>` : ''
  // the uses in two columns about as long, the longest table first (a row with a gloss under its label counts double),
  // each column in the book's order
  const rowsOf = (u: OTable) => u.table.rows.reduce((n, r) => n + (r[0]?.tr ? 2 : 1), 2)
  const cols: OTable[][] = [[], []]
  const size = (x: OTable[]) => x.reduce((n, u) => n + rowsOf(u), 0)
  for (const u of [...v.uses].sort((a, b) => rowsOf(b) - rowsOf(a))) cols[size(cols[0]) <= size(cols[1]) ? 0 : 1].push(u)
  for (const c of cols) c.sort((a, b) => v.uses.indexOf(a) - v.uses.indexOf(b))
  if (cols[1].length && v.uses.indexOf(cols[1][0]) < v.uses.indexOf(cols[0][0] ?? cols[1][0])) cols.reverse()
  const uses = v.uses.length ? `<section class="sec o-uses">${h2('check', HEAD.sl.uses, h.uses)}<div class="o-cols">${cols.map(c => `<div class="o-col">${c.map(u => otable(u, h)).join('')}</div>`).join('')}</div></section>` : ''
  const three = `<div class="sheet"><div class="body">${header(v, h, true)}${pronouns}${uses}</div>${footer(v, h)}</div>`
  return `<section class="overview" data-unit="${esc(v.id)}">${one}${two}${three}</section>`
}

/** The overview as its own document: "pregled-sklonov", "Šest sklonov · Lani". Null when the content lacks it. */
export function overviewDoc(c: Content, id: string, o: Options): (Doc & { overview: Overview }) | null {
  const v = buildOverview(c, id, o)
  if (!v) return null
  const title = `${plain(v.title.sl)} · Lani`
  return { name: v.name, title, html: documentHtml({ title, lang: v.base, mode: 'overview', body: overviewHtml(v) }), sheets: [], overview: v }
}
