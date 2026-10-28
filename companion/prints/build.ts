// The printed sheets as documents (companion/COURSE.md, "Printed sheets"): a unit's sheet and a level's booklet, each
// one self-contained HTML page (the CSS, the fonts and fit.js inlined), made from the content as it is. One source for
// both who print them: the generator (bin/school-prints writes them and renders the PDFs with Chromium) and the bridge
// (GET /course/sheet/<unit>, GET /course/booklet/<level>: the app prints them through Android's print dialog).
import { existsSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { ASSETS, coverHtml, contentsHtml, documentHtml, LANGUAGE_NAMES, unitHtml } from './html'
import { buildSheet, plain, tr, unitsOf, type Content, type ContentDirs, type Options, type Sheet } from './sheet'

/**
 * The languages a sheet explains in (html.ts has their headings); another base prints in English. Slovene explains an
 * Italian course (a learner of Italian from Slovene); a course is never explained in its own language.
 */
export const PRINT_BASES = ['en', 'de', 'it', 'sl'] as const

/** A document to print: its file name without the extension, its title, the page, and the sheets in it. */
export type Doc = { name: string; title: string; html: string; sheets: Sheet[] }

const pad = (n: number) => String(n).padStart(2, '0')

/** The level's sheets in the course's order, each numbered in the level (of [only] when given, in that order of the level's). */
export function sheetsOf(c: Content, level: string, o: Options, only?: string[]): Sheet[] {
  const all = unitsOf(c, level)
  return all.filter(u => !only || only.includes(u.id)).map(u => buildSheet(c, u, all.indexOf(u) + 1, all.length, o))
}

/** The level a unit is taught in, when it is one of the course's units (not a planned one). */
export function levelOfUnit(c: Content, id: string): string | undefined {
  const level = c.units.get(id)?.level
  return level && unitsOf(c, level).some(u => u.id === id) ? level : undefined
}

/** The grammar pages' titles, for a table's caption ("📖 Biti · from the grammar book"). */
function titlesOf(c: Content, base: string): Map<string, { sl: string; tr: string }> {
  const titles = new Map<string, { sl: string; tr: string }>()
  for (const [id, p] of c.pages) titles.set(id, { sl: tr(p.title, c.language), tr: tr(p.title, base) })
  return titles
}

/** A unit's sheet as its own page: "01-biti", "1. Biti: sem, si, je · Lani A1". */
export function unitDoc(c: Content, s: Sheet, base: string): Doc {
  const body = unitHtml(s, base, s.level, c.language, titlesOf(c, base))
  const title = `${s.n}. ${plain(s.title.sl)} · Lani ${s.level}`
  return { name: `${pad(s.n)}-${s.id}`, title, html: documentHtml({ title, lang: base, mode: 'unit', body }), sheets: [s] }
}

/** A level's booklet: the cover (with [date]), the contents, every sheet; "lani-sl-A1-en", "Slovenščina A1 · Lani". */
export function bookletDoc(c: Content, level: string, sheets: Sheet[], base: string, date: string): Doc {
  const titles = titlesOf(c, base)
  const body = coverHtml(sheets, base, level, c.language, date) + contentsHtml(sheets, base, level, c.language) + sheets.map(s => unitHtml(s, base, level, c.language, titles)).join('\n')
  const title = `${LANGUAGE_NAMES[c.language] ?? c.language} ${level} · Lani`
  return { name: `lani-${c.language}-${level}-${base}`, title, html: documentHtml({ title, lang: base, mode: 'booklet', body }), sheets }
}

/**
 * What the documents are made of as it is on disk: each file's name, size and time of the course, the grammar book (its
 * pages' cards too), the word packs, the dictionary, and the print's own CSS, fit.js and fonts. A document made when the
 * stamp was the same is the same document (the bridge keeps it until the stamp changes).
 */
export function contentStamp(d: ContentDirs, language: string): string {
  const parts: string[] = []
  const add = (path: string) => {
    try {
      const st = statSync(path)
      parts.push(`${path}:${st.size}:${st.mtimeMs}`)
    } catch {
      parts.push(`${path}:-`)
    }
  }
  const json = (dir: string) => {
    add(dir)
    if (existsSync(dir)) for (const f of readdirSync(dir).sort()) if (f.endsWith('.json')) add(join(dir, f))
  }
  add(join(d.course, 'course.json'))
  json(join(d.course, 'units'))
  json(d.grammar)
  json(d.packs)
  for (const dir of d.morePacks ?? []) json(dir)
  add(join(d.lexicon, `${language}.json`))
  add(join(d.lexicon, `${language}.extra.json`))
  for (const f of ASSETS()) add(f)
  return Bun.hash(parts.join('\n')).toString(36)
}
