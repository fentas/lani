// Counting dialogs (companion/SCENES.md, "Numbers: counting what the village has"): a dialog's `count` says where its
// number comes from, and its texts carry {n} and the forms that agree with it ({ovca|ovci|ovce|ovc}). The app renders
// them (game/scene/Numbers.kt, Counts.render); the bridge checks them, with the same categories (a parity test compares).
import { z } from 'zod'
import { withoutLearner } from './addressee'

/** Where a count's number comes from: the day's dice, a store of the village, its people, its buildings of a type. */
export const COUNT_SOURCES = ['dice', 'food', 'wood', 'stone', 'wisdom', 'villagers'] as const

export const countSpec = z.strictObject({
  from: z.union([z.enum(COUNT_SOURCES), z.string().regex(/^building:[a-z]+$/, 'building:<type>, a building type in lower case')]),
  min: z.number().int().min(1).max(999).optional(),
  max: z.number().int().min(1).max(999).optional(),
  /** A store counted per this many (wood 73 per 10: 7 logs). */
  per: z.number().int().min(1).max(1000).optional(),
  /** The counted thing's gender in the scene's language, for the number word. */
  gender: z.enum(['m', 'f', 'n']).optional(),
  case: z.enum(['nom', 'acc']).optional(),
  /** A Slovene masculine that is alive: enega konja. */
  animate: z.boolean().optional(),
})
export type CountSpec = z.infer<typeof countSpec>

/** Slovene's four categories by the last two figures: 1 (101), 2 (102), 3–4 (103), 5 and more (0, 11–19, 21 …). */
export function slovene(n: number): number {
  const m = ((n % 100) + 100) % 100
  return m === 1 ? 0 : m === 2 ? 1 : m === 3 || m === 4 ? 2 : 3
}

/** The category of [n] among [forms] forms: Slovene's four, or one and more. */
export const category = (n: number, forms: number) => (forms === 4 ? slovene(n) : n === 1 ? 0 : 1)

/** For each Slovene category, the wrong ones learners put there, the likeliest first (Numbers.WRONG_SL). */
export const WRONG_SL: Record<number, [number, number]> = { 0: [2, 1], 1: [2, 3], 2: [3, 1], 3: [2, 0] }

/** The wrong category learners put for [n] among [forms] forms: [k] 1 the likeliest, 2 the next (Numbers.wrong). */
export const wrongCategory = (n: number, forms: number, k: number) => (forms === 4 ? WRONG_SL[slovene(n)][k === 2 ? 1 : 0] : 1 - category(n, 2))

/** A count's placeholder ({n}, {a|b}, {!a|b}); not the learner's ({learner}, {m:…|f:…}: addressee.ts). */
const PLACEHOLDER = /\{(?!learner\b|[mf]:)(!?)([^{}]*)\}/g

/** The placeholders of [t]: {n}, and the selects {a|b} / {a|b|c|d} (marked ! for a wrong choice's wrong forms). */
export function placeholders(t: string): { n: boolean; selects: { marked: boolean; forms: string[] }[]; other: string[] } {
  const out = { n: false, selects: [] as { marked: boolean; forms: string[] }[], other: [] as string[] }
  for (const m of t.matchAll(PLACEHOLDER)) {
    if (m[2] === 'n' && !m[1]) out.n = true
    else if (m[2].includes('|')) out.selects.push({ marked: m[1] === '!', forms: m[2].split('|') })
    else out.other.push(m[0])
  }
  return out
}

/**
 * [t] with its forms chosen for [n] ({n} as figures): what the app says, as far as the bridge needs it (whether a wrong
 * form comes out as the right one). [wrong]: a wrong choice's `wrong_form`, in a scene whose language has [arity] forms.
 */
export function renderForms(t: string, n: number, arity: number, wrong?: number): string {
  const marked = [...t.matchAll(PLACEHOLDER)].some(m => m[1] === '!' && m[2].includes('|'))
  return t.replace(PLACEHOLDER, (all, bang: string, body: string) => {
    if (body === 'n') return String(n)
    if (!body.includes('|')) return all
    const forms = body.split('|')
    if (forms.length !== 2 && forms.length !== 4) return all
    if (!wrong || !(bang || !marked)) return forms[category(n, forms.length)]
    const w = wrongCategory(n, arity, wrong)
    const k = forms.length === arity ? w : forms.length === 2 ? (w === 0 ? 0 : 1) : w === 0 ? 0 : slovene(n) !== 0 ? slovene(n) : 2
    return forms[k]
  })
}

type Text = Partial<Record<string, unknown>> | undefined
type CountDialog = {
  count?: CountSpec
  lines: { choices: { ok?: boolean; wrong_form?: number; why?: unknown; reply?: Text; [k: string]: unknown }[]; [k: string]: unknown }[]
}

/** The numbers a check renders a counting dialog at: every one from its min to 30, and round the hundreds, within it. */
export function samples(c: CountSpec): number[] {
  const lo = c.min ?? 1
  const hi = Math.max(lo, c.max ?? 999)
  const ns = new Set<number>([lo, hi])
  for (let n = lo; n <= Math.min(hi, 30); n++) ns.add(n)
  for (const n of [99, 100, 101, 102, 103, 104, 105, 111, 121, 122, 201, 202]) if (n >= lo && n <= hi) ns.add(n)
  return [...ns].sort((a, b) => a - b)
}

/**
 * What is wrong with a dialog's placeholders, in a scene of [lang] (its texts in [langs]): {n} and the forms only with a
 * count, two or four forms, a `wrong_form` only on a wrong choice with forms in what it says, a reaction without them, the
 * count's gender for a language whose number words have one, and a wrong choice left at every number.
 */
export function countErrors(d: CountDialog, lang: string, at: string, langs: readonly string[]): string[] {
  const errs: string[] = []
  const texts: [string, string][] = []
  const push = (t: unknown, where: string) => {
    if (typeof t === 'string') texts.push([t, where])
    else if (t && typeof t === 'object') for (const [k, v] of Object.entries(t)) if (typeof v === 'string' && (langs as string[]).includes(k)) texts.push([v, `${where}.${k}`])
  }
  d.lines.forEach((l, li) => {
    push(l, `${at}.lines[${li}]`)
    l.choices.forEach((c, ci) => {
      const here = `${at}.lines[${li}].choices[${ci}]`
      push(c, here)
      push(c.why, `${here}.why`)
      push(c.reply, `${here}.reply`)
      if (c.wrong_form !== undefined) {
        if (c.ok) errs.push(`${here}: wrong_form is for a wrong choice`)
        else if (!d.count) errs.push(`${here}: wrong_form without a count`)
        else if (typeof c[lang] === 'string' && !placeholders(c[lang] as string).selects.length) errs.push(`${here}: wrong_form, but what it says has no forms ({a|b})`)
      }
      if (!c.ok && c.reply && Object.values(c.reply).some(v => typeof v === 'string' && /[{}]/.test(withoutLearner(v))))
        errs.push(`${here}.reply: a reaction has no {…}: it never says the right form`)
    })
  })
  for (const [t, where] of texts) {
    const p = placeholders(t)
    if (!d.count && (p.n || p.selects.length)) errs.push(`${where}: {n} and forms need the dialog's count`)
    for (const s of p.selects) if (s.forms.length !== 2 && s.forms.length !== 4) errs.push(`${where}: forms come in two ({one|more}) or four ({1|2|3–4|5+}), not ${s.forms.length}`)
    if (p.other.length) errs.push(`${where}: unknown placeholder ${p.other.join(', ')} (only {n} and forms)`)
  }
  const c = d.count
  if (!c) return errs
  if (c.from === 'dice' && (c.min === undefined || c.max === undefined)) errs.push(`${at}.count: the dice need a min and a max`)
  if (c.min !== undefined && c.max !== undefined && c.min > c.max) errs.push(`${at}.count: min ${c.min} is more than max ${c.max}`)
  if (c.per !== undefined && !['food', 'wood', 'stone', 'wisdom'].includes(c.from)) errs.push(`${at}.count: per goes with a store (food, wood, stone, wisdom)`)
  if (['sl', 'de', 'it'].includes(lang) && !c.gender) errs.push(`${at}.count: the gender of what it counts (m, f, n), for the number word in "${lang}"`)
  // at every number, each turn keeps a wrong choice, and none comes out as the right one
  const arity = lang === 'sl' ? 4 : 2
  for (const n of samples(c)) {
    d.lines.forEach((l, li) => {
      if (!l.choices.length) return
      const said = (ch: (typeof l.choices)[number]) => (typeof ch[lang] === 'string' ? renderForms(ch[lang] as string, n, arity, ch.ok ? undefined : ch.wrong_form) : '')
      const rights = new Set(l.choices.filter(ch => ch.ok).map(said))
      if (!l.choices.some(ch => !ch.ok && !rights.has(said(ch)))) errs.push(`${at}.lines[${li}]: at ${n}, every wrong form comes out right: no wrong choice is left (add another)`)
    })
    if (errs.length > 20) break
  }
  return [...new Set(errs)]
}
