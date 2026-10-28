// The course's content against what the app makes of it (companion/COURSE.md): used by the course section on every
// unit of the repository.
// - unitAnswers: a sheet's answers are the dictionary's forms. A table cell's or a transform line's named form (lemma +
//   form, which the learner's forms card then practises) is that slot's; a gap's, a cloze's or a choice's answer with
//   one word in brackets after its gap ("Zala pomaga ___ (babica).") is a form of that word. What the dictionary and the
//   forms book don't know (a name, an adverb made of the word: zjutraj from jutro, an adjective with too few forms in
//   the dictionary) isn't judged.
// - turnsOn: COURSE.md's "What a unit turns on" table lists every unit whose pages turn on a form of the forms cards,
//   with its pages as the unit names them.
import { mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import type { Unit } from '../../src/course'
import { adjectiveForms, FormsBook, formsOf, LineIndex, pageOf, SLOT_KEYS } from '../../src/forms'
import { Lexicon, normalizeForm } from '../../src/lexicon'

/** The personal pronouns: the dictionary has some as nouns ("jaz", the self), never their cases. */
const PRONOUNS = new Set(['jaz', 'ti', 'on', 'ona', 'ono', 'mi', 'vi', 'oni', 'one', 'midva', 'medve', 'vidva', 'vedve', 'onadva', 'onidve'])
/** An adjective whose forms the dictionary has fewer of than this (lesen: lesen, lesena, leseno, lesene) isn't judged. */
const ADJ_FORMS = 8

type Exercise = { type: string; [k: string]: any }

/** Every exercise of [u] with where it is: the intro's quick questions, the worksheets', the test's. */
function exercisesOf(u: Unit): [string, Exercise][] {
  const out: [string, Exercise][] = []
  u.intro.steps.forEach((s, i) => s.ask && out.push([`intro ${i + 1}`, s.ask as Exercise]))
  for (const w of u.worksheets) for (const e of w.exercises) out.push([w.id, e as Exercise])
  for (const e of u.test.exercises) out.push(['test', e as Exercise])
  return out
}

export function unitAnswers(units: Unit[], language: string, pages: Set<string>, lexiconDir: string): { checked: number; problems: string[] } {
  const dict = new Lexicon({ dir: lexiconDir, language, tutorFile: join(mkdtempSync(join(tmpdir(), 'lani-smoke-answers-')), 'lexicon.json') })
  const c = { dict, book: new FormsBook({ dir: lexiconDir, language }), pages, lines: new LineIndex([]), language }
  const norm = (s: string) => normalizeForm(s, language)
  const words = (s: string) => (s.match(/[\p{L}\p{M}]+/gu) ?? []).map(norm)
  const slotForms = (lemma: string, key: string) => {
    for (const e of formsOf(c, lemma)) {
      const s = e.slots.find(x => x.key === key)
      if (s) return new Set([...s.forms, ...(s.also ?? [])].map(norm))
    }
    return null
  }
  const known = new Map<string, Set<string>>()
  const formsOfWord = (w: string) => {
    const k = norm(w)
    const hit = known.get(k)
    if (hit) return hit
    const out = new Set<string>()
    if (!PRONOUNS.has(k)) {
      for (const e of formsOf(c, w)) {
        const own = new Set<string>([norm(e.lemma)])
        for (const s of e.slots) for (const f of [...s.forms, ...(s.also ?? [])]) own.add(norm(f))
        if (e.pos === 'adj') {
          for (const fs of adjectiveForms(c, e.lemma).values()) for (const f of fs) own.add(norm(f))
          if (own.size < ADJ_FORMS) continue
        }
        for (const f of own) out.add(f)
      }
    }
    known.set(k, out)
    return out
  }
  const isAdverb = (w: string) => dict.lemmasOf(w).some(l => l.pos === 'adv')
  let checked = 0
  const problems: string[] = []
  for (const u of units) {
    for (const [where, e] of exercisesOf(u)) {
      const at = `${u.id} ${where} ${e.type}`
      if (e.type === 'table' && e.lemma) {
        for (const r of e.rows) for (const cell of r.cells) {
          if (typeof cell === 'string' || !cell.form) continue
          const fs = slotForms(e.lemma, cell.form)
          if (!fs) continue
          checked++
          if (!cell.accept.some((a: string) => fs.has(words(a).at(-1) ?? ''))) problems.push(`${at}: ${e.lemma} ${cell.form} is ${[...fs].join('/')}, the cell accepts ${cell.accept.join(' | ')}`)
        }
      }
      if (e.type === 'transform') {
        for (const it of e.items) {
          if (!it.lemma || !it.form) continue
          const fs = slotForms(it.lemma, it.form)
          if (!fs) continue
          checked++
          if (!words(it.accept[0]).some(w => fs.has(w))) problems.push(`${at}: ${it.lemma} ${it.form} is ${[...fs].join('/')}, the line's answer is "${it.accept[0]}"`)
        }
      }
      // one word in brackets right after the gap: the answer is a form of it
      const cued = (after: string, answer: string, label: string) => {
        const m = after.match(/^_{3,}\s*\(([^()]+)\)/)
        const cue = m?.[1].trim()
        if (!cue || /\s/.test(cue)) return
        const fs = formsOfWord(cue)
        if (!fs.size) return
        checked++
        const last = words(answer).at(-1) ?? ''
        if (!fs.has(last) && !isAdverb(last)) problems.push(`${at} ${label}: "${answer}" isn't a form of "${cue}" (${[...fs].slice(0, 12).join(' ')} …)`)
      }
      if (e.type === 'gaptext') {
        const parts = e.text.split(/_{3,}/)
        e.gaps.forEach((g: { accept: string[] }, i: number) => cued(`___${parts[i + 1] ?? ''}`, g.accept[0], `gap ${i + 1}`))
      }
      const at3 = (s: string) => s.slice(s.search(/_{3,}/))
      if (e.type === 'cloze' && /_{3,}/.test(e.text)) cued(at3(e.text), e.accept[0], 'gap')
      if (e.type === 'choice' && /_{3,}/.test(e.prompt ?? '')) cued(at3(e.prompt), e.options[e.answer], 'answer')
    }
  }
  return { checked, problems }
}

/** The pages a form of the forms cards counts on (forms.ts pageOf): a unit naming one turns that form on. */
export const SLOT_PAGES = new Set(['biti', 'imeti', 'iti', 'delati', 'hiša'].flatMap(l => SLOT_KEYS.flatMap(k => {
  const p = pageOf(l, k)
  return p ? [p.page, ...p.needs] : []
})))

/** COURSE.md's "What a unit turns on" table: each row's unit ("`svoj`", or "`svoj` (B1)" past A2) and its pages. */
export function turnsOnTable(courseMd: string): Map<string, string[]> {
  const from = courseMd.indexOf('### What a unit turns on')
  const to = courseMd.indexOf('\n## ', from)
  const rows = new Map<string, string[]>()
  for (const m of courseMd.slice(from, to < 0 ? undefined : to).matchAll(/^\| `([a-z0-9-]+)`(?: \([A-C][12]\))? \| ([^|]+) \|/gm)) rows.set(m[1], m[2].split(',').map(p => p.trim()).filter(Boolean))
  return rows
}

/** What COURSE.md's table gets wrong about [units]: a row whose pages aren't the unit's, a unit turning on a form without its row. */
export function turnsOn(units: Unit[], courseMd: string): { rows: number; problems: string[] } {
  const table = turnsOnTable(courseMd)
  const byId = new Map(units.map(u => [u.id, u]))
  const problems: string[] = []
  for (const [id, pages] of table) {
    const u = byId.get(id)
    if (!u) problems.push(`the row of \`${id}\`: the course has no such unit written`)
    else if (u.grammar.join(', ') !== pages.join(', ')) problems.push(`the row of \`${id}\` lists ${pages.join(', ')}, the unit's grammar is ${u.grammar.join(', ')}`)
  }
  for (const u of units) {
    const slots = u.grammar.filter(g => SLOT_PAGES.has(g))
    if (slots.length && !table.has(u.id)) problems.push(`\`${u.id}\` turns on the forms of ${slots.join(', ')}: add its row (| \`${u.id}\` | ${u.grammar.join(', ')} | what the forms cards ask from then on |)`)
  }
  return { rows: table.size, problems }
}
