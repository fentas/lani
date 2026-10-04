// Texts in several languages, for the formats whose texts were "Slovene · English" before a learner could learn
// another language (plan 2, step 3b): a villager's lines, role and likes (villagers.ts), a word pack's words, title
// and description (packs.ts). The old forms stay valid: a line {"sl": "Dober dan!", "en": "Good day!"}, a role
// "Babica · Grandmother". The new ones name every language: {"it": "Buongiorno!", "sl": "Dober dan!", "en": "Good
// day!"}, the target language (what the village speaks) and translations into the bases it serves.
import { z } from 'zod'

/** A language code: sl, en, it, de … */
export const LANG = /^[a-z]{2,3}$/

const str = z.string().trim().min(1)

/** {"it": "Nonna", "sl": "Babica", "en": "Grandmother"}: at least one language. */
export const langText = (max = 400) =>
  z.record(z.string().regex(LANG, 'a language code (sl, en, it …)'), str.max(max)).refine(t => Object.keys(t).length > 0, 'a text in at least one language')

export type LangText = Record<string, string>

/** A label: one string ("Babica · Grandmother", "target · English", as the formats always had it), or a text per language. */
export const label = (max = 80) => z.union([str.max(max), langText(max)])
export type Label = string | LangText

/** "ovce · his sheep" → {sl, en}; plain English → {en} only. */
export function parseLike(like: string): { sl?: string; en: string } {
  const at = like.indexOf(' · ')
  return at < 0 ? { en: like.trim() } : { sl: like.slice(0, at).trim(), en: like.slice(at + 3).trim() }
}

/** A text in [lang], else in English, else in its first language. */
export function textIn(t: LangText, lang: string): string {
  return t[lang] ?? t.en ?? Object.values(t)[0] ?? ''
}

/** A label's English: the part after " · " of a string ("Grandmother" of "Babica · Grandmother"), or its "en". */
export function labelEn(l: Label | undefined): string {
  if (!l) return ''
  return typeof l === 'string' ? parseLike(l).en : textIn(l, 'en')
}

/** A label in [target] (the first half of "Babica · Grandmother", or the text in that language); undefined when it has none. */
export function labelTarget(l: Label | undefined, target: string): string | undefined {
  if (!l) return undefined
  if (typeof l === 'string') return parseLike(l).sl
  return l[target]
}

/** A label as "target · base" for a learner: a string as it is, a text in their two languages ("Nonna · Babica"). */
export function labelShown(l: Label | undefined, target: string, base: string): string {
  if (!l) return ''
  if (typeof l === 'string') return l
  const t = l[target] ?? textIn(l, target)
  const b = base !== target ? l[base] ?? l.en : undefined
  return b && b !== t ? `${t} · ${b}` : t
}

/**
 * The parts of the day a spoken line can be bound to (its `when`), as the app's TimeOfDay has them (SceneSpec.kt): dawn
 * is the morning's first hours, not a part of its own.
 */
export const PARTS = ['morning', 'afternoon', 'evening', 'night'] as const
export type Part = (typeof PARTS)[number]

/** The part of the day at [hour] (0–23), as TimeOfDay.of in the app: morning 5–11, afternoon 11–17, evening 17–22, night. */
export function partOf(hour: number): Part {
  return hour >= 5 && hour <= 10 ? 'morning' : hour >= 11 && hour <= 16 ? 'afternoon' : hour >= 17 && hour <= 21 ? 'evening' : 'night'
}

/** The part of the day [s] names ("evening", from the app's `?time=`), else the one it is now on this node. */
export function partNow(s?: string | null, now: Date = new Date()): Part {
  return PARTS.includes(s as Part) ? (s as Part) : partOf(now.getHours())
}

/** Whether line [l] can be said at [part]: a line without a `when` fits any time ("Lahko noč" fits the evening and the night). */
export const fits = (l: { when?: readonly string[] }, part: Part): boolean => !l.when?.length || l.when.includes(part)

/**
 * A spoken line: its text per language ({"sl": "Dober dan!", "en": "Good day!"}, or {"it": "Buongiorno!", "sl": "Dober
 * dan!", "en": "Good day!"}), the friendship level from which it can be said, and the parts of the day it is said in when
 * it's bound to one (`"when": ["evening", "night"]`: "Lahko noč, fant."; without it, any time). English is always there
 * (the tutor reads it, and it's the translation of last resort); at least one other language is what is said.
 */
export const spokenLine = (max = 200): z.ZodType<SpokenLine, SpokenLineIn> =>
  z
    .looseObject({ level: z.number().int().min(0).max(4).default(0) })
    .superRefine((l, ctx) => {
      const langs = Object.keys(l).filter(k => k !== 'level' && k !== 'when')
      for (const k of langs) {
        if (!LANG.test(k)) ctx.addIssue({ code: 'custom', path: [k], message: `"${k}" isn't a language code (sl, en, it …), "level" or "when"` })
        const v = l[k]
        if (typeof v !== 'string' || !v.trim()) ctx.addIssue({ code: 'custom', path: [k], message: 'a non-empty text' })
        else if (v.length > max) ctx.addIssue({ code: 'custom', path: [k], message: `at most ${max} characters` })
      }
      if ('when' in l) {
        const w = l.when
        if (!Array.isArray(w) || !w.length) ctx.addIssue({ code: 'custom', path: ['when'], message: 'the parts of the day it is said in, ["evening", "night"] (leave "when" out for a line of any time)' })
        else {
          w.forEach((p, i) => {
            if (!PARTS.includes(p)) ctx.addIssue({ code: 'custom', path: ['when', i], message: `${JSON.stringify(p)} isn't a part of the day: ${PARTS.join(', ')} (dawn is the morning)` })
          })
          if (new Set(w).size !== w.length) ctx.addIssue({ code: 'custom', path: ['when'], message: 'a part of the day twice' })
        }
      }
      if (typeof l.en !== 'string') ctx.addIssue({ code: 'custom', path: ['en'], message: 'the English translation ("en") is missing' })
      if (!langs.some(k => k !== 'en')) ctx.addIssue({ code: 'custom', message: 'a line in the village\'s language besides English: {"sl": …, "en": …} or {"it": …, "sl": …, "en": …}' })
    }) as unknown as z.ZodType<SpokenLine, SpokenLineIn>

/** A line as the files have it: its languages, the level (0 when missing), and the parts of the day it is bound to. */
export type SpokenLine = { level: number; when?: Part[]; en: string; sl?: string; it?: string; de?: string; [lang: string]: string | number | Part[] | undefined }
export type SpokenLineIn = Omit<SpokenLine, 'level'> & { level?: number }

/** The languages a line is in (not "level" or "when"). */
export const lineLangs = (l: SpokenLine): string[] => Object.keys(l).filter(k => k !== 'level' && k !== 'when' && typeof l[k] === 'string')

/**
 * What is said: the line in [target] (the village's language), else in Slovene (a line in the old format), else in its
 * first language that isn't English.
 */
export function said(l: SpokenLine, target: string): string {
  const langs = lineLangs(l)
  const k = langs.includes(target) ? target : langs.includes('sl') ? 'sl' : langs.find(x => x !== 'en') ?? 'en'
  return l[k] as string
}

/** The language [said] picks. */
export function saidLang(l: SpokenLine, target: string): string {
  const langs = lineLangs(l)
  return langs.includes(target) ? target : langs.includes('sl') ? 'sl' : langs.find(x => x !== 'en') ?? 'en'
}

/** Its translation for a learner explaining in [base]: in [base] when the line has it (and isn't said in it), else English. */
export function meant(l: SpokenLine, target: string, base: string): string {
  const s = saidLang(l, target)
  const b = base !== s && typeof l[base] === 'string' ? base : 'en'
  return b === s ? '' : ((l[b] as string | undefined) ?? '')
}

/** A line with its text in every language changed by [f]. */
export function mapLine<T extends SpokenLine>(l: T, f: (s: string) => string): T {
  return Object.fromEntries(Object.entries(l).map(([k, v]) => [k, typeof v === 'string' ? f(v) : v])) as T
}
