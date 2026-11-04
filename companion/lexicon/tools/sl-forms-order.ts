#!/usr/bin/env bun
// companion/lexicon/sl.forms-order.json, from Sloleks 3.0 (CC BY-SA 4.0, http://hdl.handle.net/11356/1745): what the
// dictionary's tables show first, and the nouns they ask no dual or plural of (docs/plans/05-built/cleanup-code.md,
// "Forms in their order"). Wiktionary's tables list old and regional forms beside the standard ones, and a slot with more
// than one was shown by the alphabet (aktualiziraste before aktualizirate, mescu before mesecu).
//
//   python3 -I companion/lexicon/tools/sloleks3.py index <Sloleks.3.0.zip> .cache/sloleks-3.0/index.json
//   bun companion/lexicon/tools/sl-forms-order.ts .cache/sloleks-3.0/index.json
//
// For each dictionary table (never a hand-checked one) and each slot with more than one form, against the alphabet's pick:
// a form Sloleks lacks gives way to the commonest it has; one it has gives way only to one at least twice as common in
// Gigafida (and at least 5 times there), so a near tie stays as it was. Nothing is taken away: every form stays right.
// A noun goes in `singular` when Sloleks's own entry has no dual and no plural form and the table has them.
import { existsSync, mkdtempSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { adjectiveForms, FormsBook, formsOf, LineIndex } from '../../bridge/src/forms'
import { Lexicon } from '../../bridge/src/lexicon'
import { validateGrammar } from '../../bridge/src/grammar'

type Row = { category: string; forms: [string, string, number][] }
const indexPath = process.argv[2]
if (!indexPath) throw new Error('usage: sl-forms-order.ts <index.json>')
const companion = resolve(import.meta.dir, '../..')
const lexicon = join(companion, 'lexicon')
const index = JSON.parse(readFileSync(indexPath, 'utf8')).lemmas as Record<string, Row[]>
const pages = new Set<string>()
const bookDir = join(companion, 'grammar/sl')
for (const f of existsSync(bookDir) ? readdirSync(bookDir).filter(x => x.endsWith('.json')) : []) {
  const v = validateGrammar(JSON.parse(readFileSync(join(bookDir, f), 'utf8')))
  if (v.ok) pages.add(v.page.id)
}
// the alphabet's order to compare with: a book that reads no order file, so a second run gives the same file
const bare = mkdtempSync(join(tmpdir(), 'lani-forms-order-'))
writeFileSync(join(bare, 'sl.forms.json'), readFileSync(join(lexicon, 'sl.forms.json')))
const dict = new Lexicon({ dir: lexicon, language: 'sl', tutorFile: join(bare, 'lexicon.json') })
const c = { dict, book: new FormsBook({ dir: bare, language: 'sl' }), pages, lines: new LineIndex([]), language: 'sl' }

const CATEGORY: Record<string, string> = { noun: 'noun', verb: 'verb', adj: 'adjective' }
/** Sloleks's forms of [lemma] of [pos], lowercased, each with its commonest count. */
function counts(lemma: string, pos: string): Map<string, number> | null {
  const hits = (index[lemma.toLowerCase()] ?? []).filter(e => e.category === CATEGORY[pos])
  if (!hits.length) return null
  const out = new Map<string, number>()
  for (const e of hits) for (const [form, , n] of e.forms) out.set(form.toLowerCase(), Math.max(out.get(form.toLowerCase()) ?? 0, n))
  return out
}

/** The form [forms] should show instead of its first, by [known]; none when the first stands. */
function better(forms: string[], known: Map<string, number>): string | undefined {
  const [shown] = forms
  const listed = forms.filter(f => known.has(f)).sort((a, b) => known.get(b)! - known.get(a)!)
  if (!listed.length) return undefined
  const best = listed[0]
  if (best === shown) return undefined
  if (!known.has(shown)) return best
  return known.get(best)! >= 2 * known.get(shown)! && known.get(best)! >= 5 ? best : undefined
}

const first: Record<string, Record<string, string>> = {}
const singular = new Set<string>()
const lemmas = new Set<string>()
for (const l of JSON.parse(readFileSync(join(lexicon, 'sl.json'), 'utf8')).lemmas as { lemma: string }[]) lemmas.add(l.lemma)
for (const e of JSON.parse(readFileSync(join(lexicon, 'sl.extra.json'), 'utf8')).entries as { lemma: string }[]) lemmas.add(e.lemma)
for (const lemma of [...lemmas].sort((a, b) => a.localeCompare(b, 'sl'))) {
  for (const e of formsOf(c, lemma)) {
    if (e.table !== 'wiktionary' || !CATEGORY[e.pos]) continue
    const known = counts(e.lemma, e.pos)
    if (!known) continue
    const key = `${e.pos}:${e.lemma.toLowerCase()}`
    const pick = (slot: string, forms: string[]) => {
      if (forms.length < 2) return
      const b = better(forms, known)
      if (b) (first[key] ??= {})[slot] = b
    }
    for (const s of e.slots) pick(s.key, s.forms)
    if (e.pos === 'adj') for (const [slot, forms] of adjectiveForms(c, e.lemma)) pick(slot, forms)
    if (e.pos === 'noun' && e.slots.some(s => /\.(du|pl)$/.test(s.key))) {
      const rows = (index[e.lemma.toLowerCase()] ?? []).filter(x => x.category === 'noun')
      const many = rows.some(r => r.forms.some(([, msd]) => msd[0] === 'S' && msd[3] !== 'e'))
      if (rows.length && !many) singular.add(e.lemma.toLowerCase())
    }
  }
}
const out = {
  meta: {
    language: 'sl',
    source: 'Sloleks 3.0 (Čibej et al., Centre for Language Resources and Technologies, University of Ljubljana, 2022)',
    licence: 'CC BY-SA 4.0',
    licence_url: 'https://creativecommons.org/licenses/by-sa/4.0/',
    url: 'http://hdl.handle.net/11356/1745',
    changed: 'adapted: only which of a dictionary table\'s forms to show first, by its Gigafida 2.0 counts, and the nouns it has no dual or plural of',
    made_by: 'companion/lexicon/tools/sl-forms-order.ts',
  },
  first,
  singular: [...singular].sort((a, b) => a.localeCompare(b, 'sl')),
}
writeFileSync(join(lexicon, 'sl.forms-order.json'), `${JSON.stringify(out, null, 0).replace(/,"(noun|verb|adj):/g, ',\n"$1:')}\n`)
console.log(`${Object.keys(first).length} tables, ${Object.values(first).reduce((n, s) => n + Object.keys(s).length, 0)} slots; singular only: ${out.singular.length}`)
