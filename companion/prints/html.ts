// The printed sheets as HTML (companion/COURSE.md, "Printed sheets"): a unit's A4 page from its Sheet (sheet.ts), the
// booklet's cover and contents. Each unit is written twice, as one page and as two; fit.js keeps the one that fits.
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import type { PCell, PTable, Sheet } from './sheet'

/** The headings: Slovene first (the language learned), then the base. */
export const SL = {
  rule: 'Pravilo',
  table: 'Tabela',
  tables: 'Tabeli',
  examples: 'Primeri',
  watch: 'Pazi',
  words: 'Besede',
  check: 'Preveri se',
  answers: 'Rešitve',
  notes: 'Zapiski',
  unit: 'Enota',
  contents: 'Vsebina',
  continued: 'nadaljevanje',
  machine: 'Strojno napisano, preveri z naravnim govorcem',
  sheets: 'Učni listi',
  school: 'Šola',
  units: (n: number) => (n === 1 ? '1 enota' : n === 2 ? '2 enoti' : n <= 4 ? `${n} enote` : `${n} enot`),
}

export type Base = typeof EN
const EN = {
  rule: 'The rule',
  table: 'The table',
  tables: 'The tables',
  examples: 'Examples',
  watch: 'Watch out',
  words: 'Words',
  check: 'Check yourself',
  answers: 'Answers',
  notes: 'Notes',
  unit: 'Unit',
  contents: 'Contents',
  continued: 'continued',
  machine: 'machine-written, to be checked by a native speaker',
  sheets: 'Reference sheets',
  units: (n: number) => `${n} unit${n === 1 ? '' : 's'}`,
  from: 'from the grammar book',
  how: 'One sheet per unit of the school, in the order of the course: the rule as the teacher brings it, its table with the endings marked, examples from the village, the typical mistakes, the unit’s words, and five questions to check yourself. The answers are at the foot of the page, upside down.',
  howTitle: 'How the sheets work',
  wiktionary: 'Some meanings: Wiktionary (CC BY-SA 4.0) via kaikki.org',
  genders: 'm masculine · f feminine · n neuter',
  page: 'Page',
}
const DE: Base = {
  rule: 'Die Regel',
  table: 'Die Tabelle',
  tables: 'Die Tabellen',
  examples: 'Beispiele',
  watch: 'Achtung',
  words: 'Wörter',
  check: 'Teste dich',
  answers: 'Lösungen',
  notes: 'Notizen',
  unit: 'Lektion',
  contents: 'Inhalt',
  continued: 'Fortsetzung',
  machine: 'maschinell geschrieben, von Muttersprachlern zu prüfen',
  sheets: 'Lernblätter',
  units: (n: number) => `${n} Lektion${n === 1 ? '' : 'en'}`,
  from: 'aus der Grammatik',
  how: 'Ein Blatt pro Lektion der Schule, in der Reihenfolge des Kurses: die Regel, wie die Lehrerin sie bringt, ihre Tabelle mit markierten Endungen, Beispiele aus dem Dorf, die typischen Fehler, die Wörter der Lektion und fünf Fragen zum Selbsttest. Die Lösungen stehen unten auf der Seite, auf dem Kopf.',
  howTitle: 'So funktionieren die Blätter',
  wiktionary: 'Einige Bedeutungen: Wiktionary (CC BY-SA 4.0) via kaikki.org',
  genders: 'm männlich · f weiblich · n sächlich',
  page: 'Seite',
}
const IT: Base = {
  rule: 'La regola',
  table: 'La tabella',
  tables: 'Le tabelle',
  examples: 'Esempi',
  watch: 'Attenzione',
  words: 'Parole',
  check: 'Mettiti alla prova',
  answers: 'Soluzioni',
  notes: 'Appunti',
  unit: 'Unità',
  contents: 'Indice',
  continued: 'continua',
  machine: 'scritto a macchina, da far controllare a un madrelingua',
  sheets: 'Schede di riferimento',
  units: (n: number) => `${n} unità`,
  from: 'dalla grammatica',
  how: 'Una scheda per ogni unità della scuola, nell’ordine del corso: la regola come la porta la maestra, la sua tabella con le desinenze segnate, esempi dal paese, gli errori tipici, le parole dell’unità e cinque domande per verificarti. Le soluzioni sono in fondo alla pagina, capovolte.',
  howTitle: 'Come funzionano le schede',
  wiktionary: 'Alcuni significati: Wiktionary (CC BY-SA 4.0) via kaikki.org',
  genders: 'm maschile · f femminile · n neutro',
  page: 'Pagina',
}
export const BASES: Record<string, Base> = { en: EN, de: DE, it: IT }

export const LANGUAGE_NAMES: Record<string, string> = { sl: 'Slovenščina', it: 'Italiano', de: 'Deutsch', en: 'English' }

// --- text --------------------------------------------------------------------------------------------------------

export const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

/** The content's Markdown as HTML: **bold**, *italic*, ⟦an ending⟧; the rest as text. */
export function md(s: string): string {
  return esc(s)
    .replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>')
    .replace(/\*([^*]+)\*/g, '<i>$1</i>')
    .replace(/⟦([^⟧]*)⟧/g, '<b class="end">$1</b>')
    .replace(/ → /g, '&nbsp;→ ')
}

/** A gap of a question: ___ as a line to write on. */
const gapped = (s: string) => md(s).replace(/_{3,}/g, '<span class="gap"></span>')

/** The Lani mark: the app's house, in its pixels (site/favicon.svg). */
export const HOUSE = `<svg class="house" viewBox="0 0 16 16" shape-rendering="crispEdges" aria-hidden="true"><rect x="7" y="1" width="2" height="1" fill="#7a2a1c"/><rect x="5" y="2" width="6" height="1" fill="#b83a26"/><rect x="3" y="3" width="10" height="1" fill="#b83a26"/><rect x="1" y="4" width="14" height="2" fill="#d4502f"/><rect x="2" y="6" width="12" height="8" fill="#f6e7c8"/><rect x="2" y="13" width="12" height="1" fill="#d8c29a"/><rect x="7" y="9" width="3" height="5" fill="#7b4a26"/><rect x="3" y="8" width="3" height="3" fill="#ffc94a"/><rect x="11" y="8" width="2" height="2" fill="#ffc94a"/><rect x="0" y="14" width="16" height="2" fill="#5f8f3a"/></svg>`

// --- the parts of a sheet ----------------------------------------------------------------------------------------

type Ctx = { s: Sheet; b: Base; level: string; language: string }

export const h2 = (kind: string, sl: string, tr: string) => `<h2 class="h-${kind}"><i class="dot"></i><span class="sl">${esc(sl)}</span><span class="tr">${esc(tr)}</span></h2>`

function header({ s, b, level, language }: Ctx, mini = false): string {
  const mark = `<div class="mark">${HOUSE}<div><span class="lani">Lani</span><span class="school">${esc(SL.school)} · ${esc(LANGUAGE_NAMES[language] ?? language)}</span></div><span class="level">${esc(level)}</span></div>`
  if (mini) {
    return `<header class="head mini"><div class="num">${s.n}</div><div class="titles"><h1><span class="emoji">${s.emoji}</span>${md(s.title.sl)}</h1><div class="sub">${esc(SL.continued)} · ${esc(b.continued)}</div></div>${mark}</header>`
  }
  const sub = s.title.tr && s.title.tr !== s.title.sl ? `<div class="sub">${md(s.title.tr)}</div>` : ''
  const goal = s.goal ? `<div class="goal">${md(s.goal)}</div>` : ''
  return `<header class="head"><div class="num"><small>${esc(SL.unit)}</small>${s.n}</div><div class="titles"><h1><span class="emoji">${s.emoji}</span>${md(s.title.sl)}</h1>${sub}${goal}</div>${mark}</header>`
}

const rule = ({ s, b }: Ctx) => (s.rule.length ? `<section class="sec rule">${h2('rule', SL.rule, b.rule)}<ol>${s.rule.map(r => `<li>${md(r)}</li>`).join('')}</ol></section>` : '')

export function cellHtml(c: PCell, kind: 'head' | 'label' | 'cell'): string {
  if (kind === 'head') {
    if (c.sl && c.tr) return `<span class="h-sl">${md(c.sl)}</span><span class="h-tr">${md(c.tr)}</span>`
    return `<span class="h-sl">${md(c.sl ?? c.tr ?? '')}</span>`
  }
  if (c.sl && c.tr) return `<span class="f">${md(c.sl)}</span><span class="g">${md(c.tr)}</span>`
  if (c.sl) return `<span class="f">${md(c.sl)}</span>`
  return `<span class="g only">${md(c.tr ?? '')}</span>`
}

function table(t: PTable, b: Base, titles: Map<string, { sl: string; tr: string }>): string {
  const page = t.from.split(':')[1]
  const title = page ? titles.get(page) : undefined
  const caption = title && t.from !== 'intro' ? `<caption>📖 ${md(title.sl)}<span> · ${esc(b.from)}</span></caption>` : ''
  // a column of glosses (what a form means) is set smaller than the forms
  const glossCol = t.head.map((_, j) => j > 0 && t.rows.every(r => !r[j]?.sl))
  const head = `<thead><tr>${t.head.map((h, j) => `<th${glossCol[j] ? ' class="gc"' : ''}>${cellHtml(h, 'head')}</th>`).join('')}</tr></thead>`
  const body = t.rows
    .map(r => `<tr>${r.map((c, j) => (j === 0 ? `<th scope="row">${cellHtml(c, 'label')}</th>` : `<td${glossCol[j] ? ' class="gc"' : ''}>${cellHtml(c, 'cell')}</td>`)).join('')}</tr>`)
    .join('')
  return `<div class="tbl${t.optional ? ' opt' : ''}"><table class="forms cols-${t.head.length}">${caption}${head}<tbody>${body}</tbody></table></div>`
}

function tables(c: Ctx, titles: Map<string, { sl: string; tr: string }>): string {
  const ts = c.s.tables
  if (!ts.length) return ''
  // "Tabeli" for two, "Tabela" once the optional one is left out to fit
  const heading =
    ts.length > 1
      ? (ts.some(t => t.optional) ? `<div class="when-opt">${h2('table', SL.tables, c.b.tables)}</div><div class="when-no-opt">${h2('table', SL.table, c.b.table)}</div>` : h2('table', SL.tables, c.b.tables))
      : h2('table', SL.table, c.b.table)
  return `<section class="sec tables">${heading}<div class="tbls">${ts.map(t => table(t, c.b, titles)).join('')}</div></section>`
}

const examples = ({ s, b }: Ctx, grid = false) =>
  s.examples.length
    ? `<section class="sec examples">${h2('examples', SL.examples, b.examples)}<ul class="ex${grid ? ' grid' : ''}">${s.examples
        .map((e, i) => `<li${i >= 5 ? ' class="opt"' : ''}><span class="ex-sl">${md(e.sl)}</span><span class="ex-tr">${md(e.tr)}</span></li>`)
        .join('')}</ul></section>`
    : ''

const watch = ({ s, b }: Ctx, grid = false) =>
  s.watch.length
    ? `<section class="sec watch">${h2('watch', SL.watch, b.watch)}<ul class="wo${grid ? ' grid' : ''}">${s.watch
        .map(
          w =>
            `<li><span class="wrong"><b class="sym">✗</b><s>${md(w.wrong)}</s></span><span class="right"><b class="sym">✓</b>${md(w.right)}</span>${w.why ? `<span class="why">${md(w.why)}</span>` : ''}</li>`,
        )
        .join('')}</ul></section>`
    : ''

function words({ s, b }: Ctx, wide = false): string {
  if (!s.words.length) return ''
  const items = s.words
    .map(w => {
      const g = w.gender ? `<span class="gen">${esc(w.gender === 'n' ? 'n' : w.gender)}</span>` : ''
      const lemma = w.lemma ? `<span class="lem">(${esc(w.lemma)})</span>` : ''
      return `<li><b>${esc(w.word)}</b>${g}${lemma} <span class="gl">${esc(w.gloss)}</span></li>`
    })
    .join('')
  const legend = s.words.some(w => w.gender) ? `<div class="legend">${esc(b.genders)}</div>` : ''
  return `<section class="sec words">${h2('words', SL.words, b.words)}<ul class="wl${wide ? ' wide' : ''}">${items}</ul>${legend}</section>`
}

function check({ s, b }: Ctx, max: number): string {
  const list = s.check.slice(0, max)
  if (!list.length) return ''
  const items = list
    .map((x, i) => {
      // one instruction over the items of a task in a row
      const task = x.task && x.task !== list[i - 1]?.task ? `<li class="tk">${esc(x.task)}</li>` : ''
      const hint = x.hint ? ` <span class="hint">(${md(x.hint)})</span>` : ''
      const q = x.line ? `${md(x.q)} <span class="arrow">→</span> <span class="line"></span>` : gapped(x.q)
      return `${task}<li><span class="q">${q}${hint}</span></li>`
    })
    .join('')
  return `<section class="sec check">${h2('check', SL.check, b.check)}<ol class="ck">${items}</ol></section>`
}

const answers = ({ s, b }: Ctx, max: number) =>
  s.check.length
    ? `<div class="answers" aria-label="${esc(b.answers)}"><span class="a-t">${esc(SL.answers)} · ${esc(b.answers)}:</span> ${s.check
        .slice(0, max)
        .map((x, i) => `<span class="a"><b>${i + 1}</b> ${md(x.a)}</span>`)
        .join(' ')}</div>`
    : ''

const notes = (b: Base) => `<div class="notes"><span class="n-t">${esc(SL.notes)} · ${esc(b.notes)}</span></div>`

function footer({ s, b, level, language }: Ctx): string {
  const left = `Lani · ${esc(SL.school)} · ${esc(LANGUAGE_NAMES[language] ?? language)} ${esc(level)} · ${esc(SL.unit)} ${s.n}/${s.of}`
  const mid = [s.unreviewed ? `${esc(SL.machine)} · ${esc(b.machine)}` : '', s.wiktionary ? esc(b.wiktionary) : ''].filter(Boolean).join('<br>')
  return `<footer class="foot"><span class="f-l">${left}</span><span class="f-m">${mid}</span><span class="pn"></span></footer>`
}

/** A unit: its one-page layout and its two-page one; fit.js keeps the first that fits and drops the other. */
export function unitHtml(s: Sheet, base: string, level: string, language: string, titles: Map<string, { sl: string; tr: string }>): string {
  const b = BASES[base] ?? EN
  const c: Ctx = { s, b, level, language }
  const one = `<div class="layout one"><div class="sheet"><div class="body">${header(c)}<div class="top">${rule(c)}${tables(c, titles)}</div><div class="duo"><div class="col">${examples(c)}${check(c, 5)}${notes(b)}</div><div class="col">${watch(c)}${words(c)}${notes(b)}</div></div>${answers(c, 5)}</div>${footer(c)}</div></div>`
  const two = `<div class="layout two" hidden><div class="sheet"><div class="body">${header(c)}<div class="top">${rule(c)}${tables(c, titles)}</div>${examples(c, true)}${notes(b)}</div>${footer(c)}</div><div class="sheet"><div class="body">${header(c, true)}${watch(c, true)}${words(c, true)}${check(c, 10)}${notes(b)}${answers(c, 10)}</div>${footer(c)}</div></div>`
  return `<section class="unit" data-unit="${esc(s.id)}" data-n="${s.n}">${one}${two}</section>`
}

// --- the booklet's cover and contents -------------------------------------------------------------------------------

export function coverHtml(sheets: Sheet[], base: string, level: string, language: string, date: string): string {
  const b = BASES[base] ?? EN
  const name = LANGUAGE_NAMES[language] ?? language
  const unreviewed = sheets.some(s => s.unreviewed)
  return `<section class="cover"><div class="sheet"><div class="body">
<div class="c-top">${HOUSE}<div class="c-lani">Lani</div><div class="c-school">${esc(SL.school)} · ${esc(b === EN ? 'The school' : b === DE ? 'Die Schule' : 'La scuola')}</div></div>
<div class="c-mid"><div class="c-level">${esc(level)}</div><h1 class="c-title">${esc(name)} ${esc(level)}</h1><div class="c-sub">${esc(SL.sheets)} · ${esc(b.sheets)}</div><div class="c-count">${esc(SL.units(sheets.length))} · ${esc(b.units(sheets.length))}</div></div>
<div class="c-emoji">${sheets.map(s => `<span>${s.emoji}</span>`).join('')}</div>
<div class="c-foot">${unreviewed ? `<div class="c-machine">${esc(SL.machine)}<br><span>${esc(b.machine)}</span></div>` : ''}<div class="c-date">${esc(date)}</div></div>
<div class="ground"></div>
</div></div></section>`
}

export function contentsHtml(sheets: Sheet[], base: string, level: string, language: string): string {
  const b = BASES[base] ?? EN
  const rows = sheets
    .map(
      s =>
        `<li><span class="t-n">${s.n}</span><span class="t-e">${s.emoji}</span><span class="t-t"><b>${md(s.title.sl)}</b>${s.title.tr && s.title.tr !== s.title.sl ? `<span>${md(s.title.tr)}</span>` : ''}</span><span class="t-d"></span><span class="t-p" data-unit="${esc(s.id)}"></span></li>`,
    )
    .join('')
  const parts: [string, string, string][] = [
    ['rule', SL.rule, b.rule],
    ['table', SL.table, b.table],
    ['examples', SL.examples, b.examples],
    ['watch', SL.watch, b.watch],
    ['words', SL.words, b.words],
    ['check', SL.check, b.check],
  ]
  return `<section class="contents"><div class="sheet"><div class="body">
<header class="head toc-head"><div class="titles"><h1>${esc(SL.contents)}</h1><div class="sub">${esc(b.contents)} · ${esc(LANGUAGE_NAMES[language] ?? language)} ${esc(level)}</div></div><div class="mark">${HOUSE}<div><span class="lani">Lani</span><span class="school">${esc(SL.school)}</span></div><span class="level">${esc(level)}</span></div></header>
<ol class="toc">${rows}</ol>
<div class="how"><h2 class="h-rule"><i class="dot"></i><span class="sl">${esc(b.howTitle)}</span></h2><p>${esc(b.how)}</p><ul>${parts.map(([k, sl, tr]) => `<li>${h2(k, sl, tr)}</li>`).join('')}</ul></div>
</div><footer class="foot"><span class="f-l">Lani · ${esc(SL.school)} · ${esc(LANGUAGE_NAMES[language] ?? language)} ${esc(level)}</span><span class="f-m"></span><span class="pn"></span></footer></div></section>`
}

// --- the document --------------------------------------------------------------------------------------------------

/** The print's own files next to this one (sheet.css, fit.js), and the fonts the site ships (site/fonts). */
const HERE = import.meta.dir
const FONTS = join(HERE, '../../site/fonts')

/** The fonts the site ships (site/fonts: Nunito, Pixelify Sans, Kalam; SIL OFL), inlined so the file stands alone. */
const FACES: [family: string, style: string, weight: string, file: string, ext: boolean][] = [
  ['Nunito', 'normal', '400 800', 'nunito-normal-400-800-latin-ext.woff2', true],
  ['Nunito', 'normal', '400 800', 'nunito-normal-400-800-latin.woff2', false],
  ['Nunito', 'italic', '400 600', 'nunito-italic-400-600-latin-ext.woff2', true],
  ['Nunito', 'italic', '400 600', 'nunito-italic-400-600-latin.woff2', false],
  ['Pixelify Sans', 'normal', '400 700', 'pixelify-sans-normal-400-700-latin-ext.woff2', true],
  ['Pixelify Sans', 'normal', '400 700', 'pixelify-sans-normal-400-700-latin.woff2', false],
  ['Kalam', 'normal', '400', 'kalam-normal-400-latin-ext.woff2', true],
  ['Kalam', 'normal', '400', 'kalam-normal-400-latin.woff2', false],
]

/** Every file a document inlines besides the content: the CSS, fit.js and the fonts (build.ts stamps them). */
export const ASSETS = () => [join(HERE, 'sheet.css'), join(HERE, 'fit.js'), ...FACES.map(f => join(FONTS, f[3]))]

function fontFaces(): string {
  const ext = 'U+0100-02BA, U+02BD-02C5, U+02C7-02CC, U+02CE-02D7, U+02DD-02FF, U+0304, U+0308, U+0329, U+1D00-1DBF, U+1E00-1E9F, U+1EF2-1EFF, U+2020, U+20A0-20AB, U+20AD-20C0, U+2113, U+2C60-2C7F, U+A720-A7FF'
  const lat = 'U+0000-00FF, U+0131, U+0152-0153, U+02BB-02BC, U+02C6, U+02DA, U+02DC, U+0304, U+0308, U+0329, U+2000-206F, U+20AC, U+2122, U+2191, U+2193, U+2212, U+2215, U+FEFF, U+FFFD'
  return FACES.map(([family, style, weight, file, isExt]) => {
    const data = readFileSync(join(FONTS, file)).toString('base64')
    return `@font-face{font-family:'${family}';font-style:${style};font-weight:${weight};src:url(data:font/woff2;base64,${data}) format('woff2');unicode-range:${isExt ? ext : lat}}`
  }).join('\n')
}

/** A whole page: the CSS, the fonts and fit.js inlined, so it prints as it is from a file, Chromium's or a WebView's. */
export function documentHtml(o: { title: string; lang: string; mode: 'unit' | 'booklet' | 'overview'; body: string }): string {
  const css = readFileSync(join(HERE, 'sheet.css'), 'utf8')
  const js = readFileSync(join(HERE, 'fit.js'), 'utf8')
  return `<!doctype html>
<html lang="${esc(o.lang)}" data-mode="${o.mode}">
<head>
<meta charset="utf-8">
<title>${esc(o.title)}</title>
<style>
${fontFaces()}
${css}
</style>
</head>
<body>
${o.body}
<script id="fit-report" type="application/json">null</script>
<script>
${js}
</script>
</body>
</html>
`
}
