// The school's printed sheets for the app (companion/COURSE.md, "Printed sheets"): a unit's sheet, a level's booklet and
// an overview (the cases overview: companion/prints/overview.ts) as the generator makes them (companion/prints/build.ts,
// one source: the same HTML, its CSS, fonts and fit.js inlined), for the learner's base language, name and gender. The
// app loads the page into a WebView, waits for fit.js and opens Android's print dialog. Made on the first request and
// kept until the course, the grammar book (its pages' cards), the word packs, the dictionary or the print's own files
// change.
import { bookletDoc, contentStamp, levelOfUnit, PRINT_BASES, sheetsOf, unitDoc, type Doc } from '../../prints/build'
import { overviewDoc } from '../../prints/overview'
import { contentDirs, levelsOf, loadContent, type ContentDirs, type Options } from '../../prints/sheet'

export type PrintGender = Options['gender']

/** What a print asks for: a unit's sheet, a level's booklet or an overview (by its grammar page: "skloni"), in [base], for [learner] of [gender]. */
export type PrintWhat = { kind: 'sheet'; unit: string } | { kind: 'booklet'; level: string } | { kind: 'overview'; id: string }

/** How many documents are kept (a sheet is ~0.35 MB, a level's booklet ~1 MB). */
const KEEP = 24

export const isPrintBase = (b: string): b is (typeof PRINT_BASES)[number] => (PRINT_BASES as readonly string[]).includes(b)

export class Prints {
  private readonly dirs: ContentDirs
  private kept = new Map<string, Doc>()
  private stamp = ''

  constructor(
    readonly language: string,
    dirs: Partial<ContentDirs> & { root: string },
    private log: (...a: unknown[]) => void = () => {},
  ) {
    this.dirs = contentDirs(dirs.root, language, dirs)
  }

  /**
   * The document, or null when the course has no such unit (a planned one, an unknown id) or level (or no units in it),
   * there is no such overview (or its grammar page is missing), or there is no course in this language. [date]: the
   * booklet's cover.
   */
  doc(what: PrintWhat, o: Options, date = new Date().toISOString().slice(0, 10)): Doc | null {
    const stamp = contentStamp(this.dirs, this.language)
    if (stamp !== this.stamp) {
      this.kept.clear()
      this.stamp = stamp
    }
    const key = JSON.stringify([what, o.base, o.learner, o.gender, what.kind === 'booklet' ? date : ''])
    const hit = this.kept.get(key)
    if (hit) {
      // the most recent last
      this.kept.delete(key)
      this.kept.set(key, hit)
      return hit
    }
    const made = this.make(what, o, date)
    if (!made) return null
    this.kept.set(key, made)
    while (this.kept.size > KEEP) this.kept.delete(this.kept.keys().next().value as string)
    return made
  }

  private make(what: PrintWhat, o: Options, date: string): Doc | null {
    let content
    try {
      content = loadContent('', this.language, this.dirs)
    } catch (e) {
      // no course in this language (no course.json), or a file that doesn't parse: the course's own checks say which
      this.log(`prints: no course to print in ${this.dirs.course}: ${(e as Error).message}`)
      return null
    }
    if (what.kind === 'overview') {
      const doc = overviewDoc(content, what.id, o)
      return doc ? { name: doc.name, title: doc.title, html: doc.html, sheets: [] } : null
    }
    if (what.kind === 'sheet') {
      const level = levelOfUnit(content, what.unit)
      if (!level) return null
      const [s] = sheetsOf(content, level, o, [what.unit])
      return s ? unitDoc(content, s, o.base) : null
    }
    if (!levelsOf(content).includes(what.level)) return null
    const sheets = sheetsOf(content, what.level, o)
    return sheets.length ? bookletDoc(content, what.level, sheets, o.base, date) : null
  }
}
