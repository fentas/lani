// Speaking and free writing in a unit (companion/COURSE.md, "Speaking and writing"): the school's types `speaking` (say
// it or answer aloud, graded on the phone through its recognizer) and `writing` (a short text to a prompt, checked at once
// by rules, then read by the tutor), the rules of what an answer must use and must not, and how an app tells the bridge it
// plays them. The app's half is data/Production.kt; test/fixtures/production-rules.json holds cases both check.
import { z } from 'zod'
import { addressee, render } from './addressee'
import { grammarRef } from './grammar'

/**
 * The school's item types an app plays, as it says on GET /course?types=<n>: 1 (an app that doesn't say) the module
 * types and gaptext, table, transform and sort; 2 also speaking and writing. An app below 2 gets units without them
 * (course.ts heldBack), so 0.2.698 on Teo's tablet opens every unit as it did.
 */
export const COURSE_TYPES = 2
/** The types an app below [COURSE_TYPES] 2 doesn't play. */
export const PRODUCTION_TYPES: ReadonlySet<string> = new Set(['speaking', 'writing'])

// --- rules: what an answer must use, what it must not ---------------------------------------------------------------
//
// A rule is a few words, matched against the answer's words with case, accents and punctuation ignored:
//   če            the word itself
//   pogovor*      a word that begins so and goes on (pogovora, pogovorih)
//   *la           a word that ends so (bila, šla, brala), longer than the ending
//   bi … *l       words in a row, … standing for none to three words between ("bi šel", "bi rad šel")
// An answer meets a must when its rules match at least n times in all (non-overlapping, left to right).

/** At most this many words stand for a … in a rule. */
export const GAP_WORDS = 3
/** A rule's longest run of words (… counted). */
const RULE_TOKENS = 6

type Tok = { gap: true } | { gap?: false; core: string; head: boolean; tail: boolean }

/**
 * [s] without case and accents (č → c, è → e, ü → u, ß → ss), for matching. The app folds by the language's grading table
 * (Grading.fold: German ü → ue, English's contractions written out); both read a rule and an answer alike, so a rule
 * matches the same answers in both.
 */
export function fold(s: string): string {
  return s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/ß/g, 'ss')
}

/** The words of [s] as rules see them: runs of letters and digits ("l'ho" → l, ho; "wie's" → wie, s), folded. */
export const wordsOf = (s: string): string[] => fold(s).match(/[\p{L}\p{N}]+/gu) ?? []

/** [rule] read as tokens, or why it can't be. */
export function parseRule(rule: string): Tok[] | string {
  const parts = rule.trim().split(/\s+/).filter(Boolean)
  if (!parts.length) return 'an empty rule'
  const toks: Tok[] = []
  for (const p of parts) {
    if (p === '…' || p === '...') {
      if (!toks.length) return `"${rule}": a rule doesn't begin with …`
      if (toks[toks.length - 1].gap) return `"${rule}": two … in a row`
      toks.push({ gap: true })
      continue
    }
    if (/[/|()[\]{}<>]/.test(p)) return `"${rule}": one rule for each way to say it (no / | brackets or placeholders)`
    const head = p.startsWith('*')
    const tail = p.endsWith('*') && p.length > 1
    const core = p.slice(head ? 1 : 0, tail ? -1 : undefined)
    if (core.includes('*')) return `"${rule}": * only at a word's start or end`
    const words = wordsOf(core)
    if (!words.length) return `"${rule}": "${p}" has no letters`
    if (words.length > 1 && (head || tail)) return `"${rule}": "${p}" is more than one word, so it can't have a *`
    for (const w of words) toks.push({ core: w, head, tail })
  }
  if (toks[toks.length - 1].gap) return `"${rule}": a rule doesn't end with …`
  if (toks.length > RULE_TOKENS) return `"${rule}": at most ${RULE_TOKENS} words and … in a rule`
  return toks
}

const wordFits = (w: string, t: Exclude<Tok, { gap: true }>) =>
  t.head && t.tail ? w.includes(t.core) && w !== t.core : t.head ? w.endsWith(t.core) && w.length > t.core.length : t.tail ? w.startsWith(t.core) && w.length > t.core.length : w === t.core

/** Where a match of [toks] from word [i] of [words] ends (the index after it), shortest first; -1 for none. */
function endOf(words: string[], i: number, toks: Tok[], t = 0): number {
  if (t === toks.length) return i
  const tok = toks[t]
  if (tok.gap) {
    for (let k = 0; k <= GAP_WORDS && i + k <= words.length; k++) {
      const r = endOf(words, i + k, toks, t + 1)
      if (r >= 0) return r
    }
    return -1
  }
  return i < words.length && wordFits(words[i], tok) ? endOf(words, i + 1, toks, t + 1) : -1
}

/** The matches of [rules] in [text]: non-overlapping, left to right, each the words it matched. */
export function matches(text: string, rules: string[]): string[] {
  const alts = rules.map(parseRule).filter((r): r is Tok[] => typeof r !== 'string')
  const words = wordsOf(text)
  const out: string[] = []
  let i = 0
  while (i < words.length) {
    let end = -1
    for (const a of alts) {
      const r = endOf(words, i, a)
      if (r > i && (end < 0 || r < end)) end = r
    }
    if (end > i) {
      out.push(words.slice(i, end).join(' '))
      i = end
    } else i++
  }
  return out
}

/** A text's words, as the length counts them. */
export const wordCount = (text: string) => wordsOf(text).length

// --- the items ------------------------------------------------------------------------------------------------------

const text = z.string().trim().min(1)
const opt = text.optional()
const grammar = grammarRef.optional()
const rule = z.string().trim().min(1).max(80).superRefine((r, ctx) => {
  const p = parseRule(r)
  if (typeof p === 'string') ctx.addIssue({ code: 'custom', message: p })
})
const rules = z.array(rule).min(1).max(12)

/** What the answer must use: [label] says it to the learner, [any] matches it [n] times or more. */
const must = z.strictObject({ label: text.max(120), any: rules, n: z.number().int().min(1).max(6).default(1), grammar })
/** A mistake the unit is about, caught at once ([explain]: why, and what is right). Never a form people say. */
const avoid = z.strictObject({ any: rules, explain: text.max(300), grammar })
/** How long a text is, in words: [min] at least (shorter is a check not met), [max] what the prompt asks at most (said, not graded). */
const length = z
  .strictObject({ min: z.number().int().min(1).max(250), max: z.number().int().min(1).max(300).optional() })
  .refine(l => l.max === undefined || l.max >= l.min, 'max under min')
/** What a native speaker says beside the model (as a gap's `spoken`): right, labelled, never a mistake. */
const spoken = z.array(z.string().trim().min(1).max(200)).min(1).max(8).optional()

export type Must = z.infer<typeof must>
export type Avoid = z.infer<typeof avoid>

/** The child's version's keys of a speaking item: what is shorter and simpler for a child (each replaces the item's). */
const speakingChild = z.strictObject({
  prompt: text.max(300).optional(),
  ask: text.max(200).optional(),
  say: text.max(200).optional(),
  accept: z.array(text.max(200)).max(12).optional(),
  spoken,
  must: z.array(must).max(3).optional(),
  min_words: z.number().int().min(1).max(20).optional(),
  show: z.boolean().optional(),
})

/** The child's version's keys of a writing item: a child says it first (COURSE.md), then may write a line. */
const writingChild = z.strictObject({
  prompt: text.max(300).optional(),
  ask: text.max(200).optional(),
  model: text.max(200).optional(),
  must: z.array(must).max(3).optional(),
  avoid: z.array(avoid).max(3).optional(),
  length: length.optional(),
})

/** A child's version's longest model, in words: a sentence a child of seven says in one breath. */
export const CHILD_WORDS = 12
/** A child's writing asks at most this many words. */
export const CHILD_WRITE_MAX = 25

const both = [addressee({ name: 'Ana', gender: 'female' }), addressee({ name: 'Jan', gender: 'male' })]

type Checkable = { must?: Must[]; avoid?: Avoid[] }

/** What is wrong with [t] (a model or an accepted answer) against [item]'s rules, for a learner of either gender. */
function selfCheck(t: string, item: Checkable, what: string): string[] {
  const errs: string[] = []
  for (const a of both) {
    const s = render(t, a)
    for (const m of item.must ?? []) {
      const n = matches(s, m.any).length
      if (n < m.n) errs.push(`${what} "${s}" doesn't meet its own must "${m.label}" (${n} of ${m.n})`)
    }
    for (const v of item.avoid ?? []) {
      const hit = matches(s, v.any)
      if (hit.length) errs.push(`${what} "${s}" has what it avoids: "${hit.join('", "')}"`)
    }
  }
  return [...new Set(errs)]
}

const issue = (ctx: z.RefinementCtx, path: (string | number)[], message: string) => ctx.addIssue({ code: 'custom', path, message })

type SpeakingLike = { say: string; accept?: string[]; spoken?: string[]; must?: Must[]; avoid?: Avoid[] }

function speakingIssues(e: SpeakingLike, ctx: z.RefinementCtx, at: (string | number)[]) {
  selfCheck(e.say, e, 'say').forEach(m => issue(ctx, [...at, 'say'], m))
  ;(e.accept ?? []).forEach((a, i) => selfCheck(a, e, 'an accepted answer').forEach(m => issue(ctx, [...at, 'accept', i], m)))
  const right = new Set([e.say, ...(e.accept ?? [])].map(s => wordsOf(s).join(' ')))
  ;(e.spoken ?? []).forEach((s, i) => {
    if (right.has(wordsOf(s).join(' '))) issue(ctx, [...at, 'spoken', i], `"${s}" is the model or an accepted answer: spoken lists what people say beside them`)
    for (const v of e.avoid ?? []) if (matches(s, v.any).length) issue(ctx, [...at, 'spoken', i], `"${s}" is said by people, so no avoid may match it`)
  })
}

export const speaking = z
  .strictObject({
    type: z.literal('speaking'),
    grammar,
    instruction: opt,
    /** What to say or answer, in the learner's own language. */
    prompt: text.max(300),
    /** What the teacher asks (the language learned): heard and shown; the learner answers it. */
    ask: text.max(200).optional(),
    /** The model answer (the language learned), one sentence: what is right, shown and heard once answered. */
    say: text.max(200),
    /** Other answers that are right. */
    accept: z.array(text.max(200)).max(12).default([]),
    spoken,
    /** An open answer: what it must use. Any answer that meets them is right; the model and accept are right anyway. */
    must: z.array(must).max(4).default([]),
    avoid: z.array(avoid).max(4).default([]),
    /** An open answer's fewest words. */
    min_words: z.number().int().min(1).max(40).optional(),
    /** true: the model shows (and can be heard) before, "repeat after me"; false (the default): produced, then shown. */
    show: z.boolean().default(false),
    hint: opt,
    explain: opt,
    child: speakingChild.optional(),
  })
  .superRefine((e, ctx) => {
    speakingIssues(e, ctx, [])
    if (e.child) {
      const c = { ...e, ...e.child }
      speakingIssues(c, ctx, ['child'])
      if (wordCount(c.say) > CHILD_WORDS) issue(ctx, ['child', 'say'], `a child's sentence of ${wordCount(c.say)} words: at most ${CHILD_WORDS}`)
      if (e.child.must && !e.child.say && !e.child.accept) issue(ctx, ['child', 'must'], "a child's must without the child's own say")
    }
  })

type WritingLike = { model: string; must?: Must[]; avoid?: Avoid[]; length?: { min: number; max?: number } }

function writingIssues(e: WritingLike, ctx: z.RefinementCtx, at: (string | number)[]) {
  if (!(e.must ?? []).length && !e.length) issue(ctx, at, 'nothing to check: give it a must or a length')
  selfCheck(e.model, e, 'the model').forEach(m => issue(ctx, [...at, 'model'], m))
  const n = Math.min(...both.map(a => wordCount(render(e.model, a))))
  const most = Math.max(...both.map(a => wordCount(render(e.model, a))))
  if (e.length && n < e.length.min) issue(ctx, [...at, 'model'], `the model has ${n} words, the length asks ${e.length.min} at least`)
  if (e.length?.max && most > e.length.max) issue(ctx, [...at, 'model'], `the model has ${most} words, the length asks ${e.length.max} at most`)
}

export const writing = z
  .strictObject({
    type: z.literal('writing'),
    grammar,
    instruction: opt,
    /** The task, in the learner's own language: what to write, to whom, how long. */
    prompt: text.max(400),
    /** A line the text answers (the language learned): a letter's, a question; shown and heard. */
    ask: text.max(300).optional(),
    /** A model text (the language learned) that meets every must: shown once checked, printed with the answers. */
    model: text.max(1200),
    must: z.array(must).max(6).default([]),
    avoid: z.array(avoid).max(6).default([]),
    length: length.optional(),
    hint: opt,
    explain: opt,
    child: writingChild.optional(),
  })
  .superRefine((e, ctx) => {
    writingIssues(e, ctx, [])
    if (e.child) {
      const c = { ...e, ...e.child }
      writingIssues(c, ctx, ['child'])
      if (wordCount(c.model) > CHILD_WORDS * 2) issue(ctx, ['child', 'model'], `a child's model of ${wordCount(c.model)} words: at most ${CHILD_WORDS * 2}`)
      if (c.length?.max && c.length.max > CHILD_WRITE_MAX) issue(ctx, ['child', 'length'], `a child writes at most ${CHILD_WRITE_MAX} words`)
      if (e.child.must && !e.child.model) issue(ctx, ['child', 'must'], "a child's must without the child's own model")
    }
  })

export type Speaking = z.infer<typeof speaking>
export type Writing = z.infer<typeof writing>

/** [e] as a child plays it: its child's version's keys over its own (COURSE.md "A child's version"). */
export function forChild<T extends { child?: object }>(e: T): Omit<T, 'child'> {
  const { child, ...rest } = e
  return child ? ({ ...rest, ...child } as Omit<T, 'child'>) : rest
}
