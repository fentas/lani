// The content's forms the case lens knows nothing of: no reading as a word with a case, as a verb or as anything else
// (the dictionary, the supplement, the forms book, the trigger table, the word packs), and no preposition or fused form
// of the table. The measure case-lens-2 took (docs/plans/05-built/case-lens-2.md, "Accuracy": 1,269 → 1,021 forms), kept
// here since its script lived in a scratchpad; lens-unknown-forms added the kinds.
//
//   bun test/case-lens-gaps.ts [--all] [--units] [--kind name|sound|foreign|word] [form,form …]
//
// The lines are the smoke's (scenes, stories, readings, grammar pages, packs of the Primorska village; test/smoke/
// case-lens.ts); --units adds the course units' lines. A capitalized form inside a line is counted as written (Krpan),
// any other in small letters. Prints the lines, the words with a case, the unknown forms and their tokens, each kind's
// count, and the forms by frequency with a line each is in (the 150 most frequent, or --all).
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { addressee, renderDeep } from '../src/addressee'
import { contentLines } from '../src/forms'
import { repoLens, VILLAGE_NAMES } from './case-lens-eval'

const companion = resolve(import.meta.dir, '../..')
const js = (d: string) => (existsSync(d) ? readdirSync(d).filter(f => f.endsWith('.json')).sort().map(f => JSON.parse(readFileSync(join(d, f), 'utf8'))) : [])

/** The content's Slovene lines, as the smoke reads them; with the course units' when [units]. */
export function lensLines(units = false): { text: string; from: string }[] {
  const culture = join(companion, 'cultures/primorska')
  const lines = contentLines(renderDeep({
    language: 'sl',
    scenes: [...js(join(companion, 'scenes')), ...js(join(culture, 'scenes'))],
    stories: js(join(culture, 'stories')),
    readings: js(join(culture, 'readings')),
    grammar: js(join(companion, 'grammar/sl')),
    packs: [...js(join(companion, 'packs')), ...js(join(culture, 'packs'))],
  }, addressee({ name: 'Jan', gender: 'male' })))
  if (!units) return lines
  // the course units' Slovene lines: every string under a "sl" key (examples, dialog lines, exercises' sentences)
  const seen = new Set(lines.map(l => l.text))
  const more: { text: string; from: string }[] = []
  const walk = (v: unknown, from: string) => {
    if (Array.isArray(v)) v.forEach(x => walk(x, from))
    else if (v && typeof v === 'object') for (const [k, x] of Object.entries(v)) k === 'sl' && typeof x === 'string' ? add(x, from) : walk(x, from)
  }
  const add = (text: string, from: string) => {
    const t = text.trim()
    if (!t || /[{}]|__/.test(t) || seen.has(t)) return
    seen.add(t)
    more.push({ text: t, from })
  }
  const dir = join(companion, 'course/sl/units')
  for (const f of existsSync(dir) ? readdirSync(dir).filter(f => f.endsWith('.json')).sort() : []) walk(JSON.parse(readFileSync(join(dir, f), 'utf8')), `unit:${f.slice(0, -5)}`)
  return [...lines, ...more]
}

export type Gap = { form: string; tokens: number; line: string; from: string; kind: GapKind }
/**
 * What kind of form it is, by its letters and where it stands: a name (never written small, capitalized inside a line),
 * a sound or an interjection (no vowel, or a letter three times: hm, pst, mmm, brrr), a word of another language the
 * lines say in it (allora, bitte: listed in FOREIGN), or a word of the language.
 */
export type GapKind = 'name' | 'sound' | 'foreign' | 'word'

/** Words of Italian, German, Friulian … that Slovene lines say as they are (a guest's allora): no Slovene entry. */
export const FOREIGN = new Set(['allora', 'bitte', 'grazie', 'prego', 'ciao', 'danke', 'servus', 'mandi', 'ja', 'nein', 'si', 'bene', 'basta', 'ecco', 'dai', 'buongiorno', 'buonasera'])

export function gaps(units = false): { lines: number; words: number; decided: number; gaps: Gap[] } {
  const lens = repoLens()
  const lines = lensLines(units)
  const unknown = new Map<string, { tokens: number; line: string; from: string }>()
  const small = new Set<string>()
  const inside = new Set<string>()
  let words = 0
  let decided = 0
  for (const l of lines) {
    const r = lens.analyze(l.text, VILLAGE_NAMES)
    words += r.words.length
    decided += r.words.filter(w => w.sure !== 'ambiguous').length
    for (const m of l.text.matchAll(/[\p{L}\p{M}]+/gu)) {
      const raw = m[0]
      const form = raw.toLowerCase()
      if (/^\p{Ll}/u.test(raw)) small.add(form)
      // (capitalized after a word, not where a sentence starts: »Ha!« after a question is no name)
      else if (/[\p{L}\p{M}\d,;]\s*$/u.test(l.text.slice(0, m.index))) inside.add(form)
      const lex = (lens as any).lexical(form)
      if (lex.nominal.length || lex.verbs.length || lex.other.size) continue
      if ((lens as any).preps.has(form) || (lens as any).fused.has(form)) continue
      const key = /^\p{Lu}/u.test(raw) && m.index! > 0 ? raw : form
      const had = unknown.get(key)
      if (had) had.tokens++
      else unknown.set(key, { tokens: 1, line: l.text, from: l.from })
    }
  }
  const kindOf = (key: string): GapKind => {
    const form = key.toLowerCase()
    if (FOREIGN.has(form)) return 'foreign'
    if (!/[aeiouà-ü]/u.test(form) || /(\p{L})\1\1/u.test(form)) return 'sound'
    if (!small.has(form) && inside.has(form)) return 'name'
    return 'word'
  }
  const out = [...unknown].map(([form, g]) => ({ form, ...g, kind: kindOf(form) })).sort((a, b) => b.tokens - a.tokens || a.form.localeCompare(b.form, 'sl'))
  return { lines: lines.length, words, decided, gaps: out }
}

if (import.meta.main) {
  const args = process.argv.slice(2)
  const kindAt = args.indexOf('--kind')
  const kind = kindAt >= 0 ? args[kindAt + 1] : undefined
  const want = args.filter((a, k) => !a.startsWith('--') && k !== kindAt + 1).flatMap(a => a.split(',')).filter(Boolean)
  const r = gaps(args.includes('--units'))
  const tokens = (gs: Gap[]) => gs.reduce((a, g) => a + g.tokens, 0)
  console.log(`${r.lines} lines${args.includes('--units') ? ' (with the course units\')' : ''}, ${r.words} words with a case, ${((r.decided / r.words) * 100).toFixed(1)}% decided or likely`)
  console.log(`${r.gaps.length} unknown forms, ${tokens(r.gaps)} tokens`)
  for (const k of ['name', 'sound', 'foreign', 'word'] as const) {
    const gs = r.gaps.filter(g => g.kind === k)
    console.log(`  ${k}: ${gs.length} forms, ${tokens(gs)} tokens`)
  }
  for (const w of want) {
    const g = r.gaps.find(x => x.form === w)
    console.log('WANT', w, g ? `${g.tokens} | ${g.line.slice(0, 90)}` : 'known')
  }
  const shown = r.gaps.filter(g => !kind || g.kind === kind)
  for (const g of args.includes('--all') ? shown : shown.slice(0, 150)) console.log(`${g.tokens}\t${g.kind}\t${g.form}\t| ${g.line.slice(0, 100)}\t(${g.from})`)
}
