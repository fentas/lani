// The tutor in another language (docs/plans/05-built/tutor-languages.md). The features' paragraphs and the skills were
// written with Jan and Slovene: here is what a village in another language reads instead (its paragraph, rendered for it)
// and what's correct in its language (companion/tutor/languages: the same files the in-app tutor's prefix ends with).
// Jan's paragraphs stay as they are: everything here leaves a Slovene village's text alone.
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { LANGUAGES } from './learners'

/** A paragraph that says Slovene only as what the village speaks, for a village of [target]. */
export const forVillage = (paragraph: string, target: string): string =>
  target === 'sl' ? paragraph : paragraph.replace(/\bSlovene\b/g, LANGUAGES[target] ?? target)

/**
 * Takes [from] out of [text] (or puts [to] in its place): a paragraph's part that holds only in a Slovene village. A
 * paragraph that no longer has it stays as it is: a learner's bridge never fails over a sentence (the smoke's
 * tutor-languages section checks that no Slovene part is left).
 */
export const without = (text: string, from: string, to = ''): string => text.replace(from, () => to)

/** A file's text without its comments, by its "## " sections. */
const sections = (text: string): string[] =>
  text
    .replace(/<!--[\s\S]*?-->[ \t]*(\r?\n)?/g, '')
    .split(/^(?=## )/m)
    .map(s => s.trim())
    .filter(Boolean)

/** What a Claude Code session reads a language with, Slovene's fran.si aside: Jan's choice (docs/plans/05-built/tutor-reach.md). */
const SITES: Record<string, string> = { it: 'treccani.it', de: 'duden.de', en: 'dictionary.cambridge.org' }

/**
 * What a learner's channel guide says is correct: what holds in every language, then their language's own part (as the
 * in-app tutor reads them: android tutor/Prompt.kt LanguageParts), its writing rules (this session writes the content
 * too) and a child's case. [projectDir]: where companion/tutor is; a place without it (the phone's bridge, whose tutor
 * has them in its prefix) gets none.
 */
export function languageParts(projectDir: string, language: string, child: boolean): string[] {
  const read = (f: string) => {
    try {
      return readFileSync(join(projectDir, 'companion/tutor/languages', f), 'utf8')
    } catch {
      return ''
    }
  }
  const general = sections(read('general.md'))
  if (!general.length) return []
  const own = sections(read(`${language}.md`))
  const kid = own.filter(s => s.startsWith('## A child'))
  const check =
    language === 'sl'
      ? []
      : [`Checking a form in this session: WebFetch reads ${SITES[language] ?? 'no dictionary of this language'}, one page at a time, as far as this session's settings allow (.claude/settings.json; a page refused isn't a source). You also have the word packs (list_packs, companion/packs/${language}/, the culture pack's packs/), the grammar book (list_grammar, get_grammar), companion/lexicon/${language}.extra.json and the learner's glosses (<data>/app/lexicon.json). Nothing else is a source: what they don't settle you don't know.`]
  return [...general, ...check, ...own.filter(s => !s.startsWith('## A child')), ...(child ? kid : [])]
}
