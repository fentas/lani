// The learner as the content speaks to them and of them (companion/SCENES.md, "The learner in the content"): their name
// in each case Slovene declines it, and their gender for the forms that agree with them. Curated content and the tutor's
// write placeholders instead of a name and a gender of their own:
//
//   {learner}                                   the name, as it is: "Dober večer, {learner}!"
//   {learner:gen|dat|acc|loc|ins}               Slovene cases: Jana, Janu, Jana, (pri) Janu, (z) Janom; Ane, Ani, Ano …
//   {learner:poss}                              the possessive's stem, its ending written after it: {learner:poss}a hiša
//   {m:prišel|f:prišla}                         what agrees with the learner: Si {m:lačen|f:lačna}? (either order)
//
// A branch may hold the name ({m:Dragi {learner}|f:Draga {learner}}), nothing else in braces. The bridge renders them for
// its learner (learner-profile.json: learner.name, learner.gender "male" | "female", male when missing, and
// learner.name_forms for the cases the rules get wrong) wherever it serves the app a text, speaks one or hands one to the
// tutor; the app renders what it bundles the same way (l10n/Learner.kt). Rendered for a male learner whose name is the
// one a text had before, every text reads as it did, so its voice clip is the same.
import { readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

export type Gender = 'male' | 'female'

/** The forms of a name besides its own: the Slovene cases (the vocative is the nominative) and the possessive's stem. */
export const NAME_CASES = ['gen', 'dat', 'acc', 'loc', 'ins', 'poss'] as const
export type NameCase = (typeof NAME_CASES)[number]
export type NameForms = Record<NameCase, string>

/**
 * Who the content speaks to: the [name] and its [forms], the [gender]; [generic] when the profile has no name and the
 * village's word for a friend stands in for it ("prijatelj"), which is written small inside a sentence.
 */
export type Addressee = { name: string; gender: Gender; forms: NameForms; generic?: boolean }

const CASE = `(?::(${NAME_CASES.join('|')}))?`
const NAME_SRC = `\\{learner${CASE}\\}`
const BRANCH = `((?:[^{}|]|\\{learner(?::(?:${NAME_CASES.join('|')}))?\\})*)`
/** A name placeholder, its case the first group. */
const NAME_RE = new RegExp(NAME_SRC, 'g')
/** A gender pair: the first label and its text, the second label and its text. */
const PAIR_RE = new RegExp(`\\{([mf]):${BRANCH}\\|([mf]):${BRANCH}\\}`, 'g')
/** Anything that starts like a learner placeholder (a malformed one too). */
const LOOKS_LIKE = /\{(?:learner\b|[mf]:)/

/** Whether [text] holds a placeholder of the learner, well formed or not. */
export const mentionsLearner = (text: string) => LOOKS_LIKE.test(text)

// --- a Slovene first name, declined -----------------------------------------------------------------------------------

const soft = (stem: string) => (stem.endsWith('c') ? stem.slice(0, -1) + 'č' : stem)

/**
 * The Slovene forms of first name [name] for someone of [gender] (the singular; the accusative of a man's name is its
 * genitive, the locative its dative): a name in -a declines as Ana, Ane, Ani, Ano, Ani, Ano (a man's too: Luka, Luke …,
 * Lukov); another woman's name stays as it is (Nives, Beti; Nivesin, Betin); a man's in -o Marko, Marka, Marku, Markom;
 * in -e Tone, Toneta, Tonetu, Tonetom; in -i, -u, -y Toni, Tonija, Tonijem; in a consonant Jan, Jana, Janu, Janom, Janov,
 * a soft one taking -em and -ev (Nejc, Nejcem, Nejčev; Matej, Matejem), with the fleeting e of Pavel, Karel, Peter,
 * Aleksander and the diminutives in -ek (Pavla, Petra, Tončka) and a j after another r (Igor, Igorja). What the rules get
 * wrong the profile's name_forms says.
 */
export function sloveneName(name: string, gender: Gender): NameForms {
  const n = name.trim()
  if (!n) return { gen: n, dat: n, acc: n, loc: n, ins: n, poss: n }
  const last = n.slice(-1).toLowerCase()
  if (last === 'a') {
    const s = n.slice(0, -1)
    return { gen: s + 'e', dat: s + 'i', acc: s + 'o', loc: s + 'i', ins: s + 'o', poss: gender === 'female' ? soft(s) + 'in' : s + 'ov' }
  }
  if (gender === 'female') return { gen: n, dat: n, acc: n, loc: n, ins: n, poss: (last === 'i' ? n.slice(0, -1) : n) + 'in' }
  const man = (stem: string, softStem = false): NameForms => ({
    gen: stem + 'a',
    dat: stem + 'u',
    acc: stem + 'a',
    loc: stem + 'u',
    ins: stem + (softStem ? 'em' : 'om'),
    poss: softStem ? soft(stem) + 'ev' : stem + 'ov',
  })
  if (last === 'o') return man(n.slice(0, -1))
  if (last === 'e') return man(n + 't')
  if ('iuy'.includes(last)) return man(n + 'j', true)
  if (!/[a-zčšžćđ]$/i.test(n)) return { gen: n, dat: n, acc: n, loc: n, ins: n, poss: n }
  // the fleeting e: Pavel, Karel; Peter, Silvester, Aleksander; Tonček, Janček
  const fleeting = /(?:[vr]el|[^aeiou]ek|(?:[aeiou]|s)ter|nder)$/i.test(n) && !/[aeiou]{2}[a-z]$/i.test(n)
  const stem = fleeting ? n.slice(0, -2) + n.slice(-1) : n
  if (stem.endsWith('r') && !fleeting) return man(stem + 'j', true)
  return man(stem, /[cčšžj]$/i.test(stem))
}

/** The village's word for a friend, the name for a learner whose profile has none: man's and woman's. */
const FRIEND: Record<string, [string, string]> = { sl: ['prijatelj', 'prijateljica'], it: ['amico', 'amica'], de: ['Freund', 'Freundin'], en: ['friend', 'friend'] }

/** A profile's text that is a template's placeholder ("{YOUR_NAME}") rather than a value. */
const TEMPLATE = /\{[^{}]*\}/

/**
 * The learner of a profile's [name], [gender] ("female", else male) and [forms] (learner.name_forms: any of the cases,
 * each overriding the rules'). Without a name, the village's ([language]) word for a friend.
 */
export function addressee(p: { name?: unknown; gender?: unknown; forms?: unknown } = {}, language = 'sl'): Addressee {
  const gender: Gender = typeof p.gender === 'string' && /^(f|female)$/i.test(p.gender.trim()) ? 'female' : 'male'
  const given = typeof p.name === 'string' && !TEMPLATE.test(p.name) ? p.name.trim() : ''
  const friend = FRIEND[language] ?? FRIEND.sl
  const name = given || friend[gender === 'female' ? 1 : 0]
  const forms = sloveneName(name, gender)
  if (given && p.forms && typeof p.forms === 'object')
    for (const k of NAME_CASES) {
      const v = (p.forms as Record<string, unknown>)[k]
      if (typeof v === 'string' && v.trim() && !TEMPLATE.test(v)) forms[k] = v.trim()
    }
  return { name, gender, forms, ...(given ? {} : { generic: true }) }
}

const profiles = new Map<string, { mtime: number; a: Addressee }>()

/** The learner of [dataDir]'s learner-profile.json (read again when it changes); [language]: the village's. */
export function addresseeOf(dataDir: string, language = 'sl'): Addressee {
  const path = join(dataDir, 'learner-profile.json')
  let mtime = -1
  try {
    mtime = statSync(path).mtimeMs
  } catch {
    // no profile yet: nobody named
  }
  const key = `${path}|${language}`
  const hit = profiles.get(key)
  if (hit && hit.mtime === mtime) return hit.a
  let l: Record<string, unknown> = {}
  try {
    l = JSON.parse(readFileSync(path, 'utf8'))?.learner ?? {}
  } catch {
    // unreadable: as if missing
  }
  const a = addressee({ name: l.name, gender: l.gender, forms: l.name_forms }, language)
  profiles.set(key, { mtime, a })
  return a
}

// --- rendering ----------------------------------------------------------------------------------------------------------

/** What a sentence starts after: the end of one, a line break or an opening quote. */
const STARTS = /(?:[.!?…]\s+|\n\s*|[«„"“(]\s*)$/

/** Whether a sentence starts at [at] of [text]: at its start (but for spaces), or after the end of one ([STARTS]). */
export const startsSentence = (text: string, at: number) =>
  (at <= 8 && !text.slice(0, at).trim()) || STARTS.test(text.slice(Math.max(0, at - 8), at))

/** [text] said to [a]: each gender pair as their gender has it, then the name in its case. */
export function render(text: string, a: Addressee): string {
  if (!text.includes('{')) return text
  const g = a.gender === 'female' ? 'f' : 'm'
  const paired = text.replace(PAIR_RE, (all, l1: string, t1: string, l2: string, t2: string) => (l1 === l2 ? all : l1 === g ? t1 : t2))
  return paired.replace(NAME_RE, (_all, c: NameCase | undefined, at: number, whole: string) => {
    const form = c ? a.forms[c] : a.name
    return a.generic && startsSentence(whole, at) ? form.charAt(0).toUpperCase() + form.slice(1) : form
  })
}

/** [v] with every string in it rendered for [a] (plain objects and arrays; anything else as it is). */
export function renderDeep<T>(v: T, a: Addressee): T {
  if (typeof v === 'string') return render(v, a) as T
  if (Array.isArray(v)) return v.map(x => renderDeep(x, a)) as T
  if (v && typeof v === 'object' && Object.getPrototypeOf(v) === Object.prototype)
    return Object.fromEntries(Object.entries(v as Record<string, unknown>).map(([k, x]) => [k, renderDeep(x, a)])) as T
  return v
}

/** Learners the checks render with: what is left of a text's braces after it are the text's own. */
const SAMPLE: Addressee = addressee({ name: 'Learner' })
const SAMPLES: Addressee[] = [SAMPLE, addressee({ name: 'Learner', gender: 'female' })]

/**
 * The gender for whom two of a turn's choices [texts] read the same (their gender pairs aside, they differ): the learner's
 * gender is never what a turn tests, so each choice says something else for anyone. Undefined when none do.
 */
export function sameFor(texts: (string | undefined)[]): Gender | undefined {
  const own = texts.filter((t): t is string => typeof t === 'string')
  if (new Set(own).size < own.length || !own.some(mentionsLearner)) return undefined
  for (const a of SAMPLES) if (new Set(own.map(t => render(t, a))).size < own.length) return a.gender
  return undefined
}

/** [text] with its learner's placeholders gone (rendered for a sample learner): for the checks of the other braces. */
export const withoutLearner = (text: string) => (mentionsLearner(text) ? render(text, SAMPLE) : text)

/**
 * What is wrong with the learner's placeholders in [text]: a name form that isn't one, a gender pair without both forms,
 * with a label twice, or with braces of another kind inside.
 */
export function learnerErrors(text: string): string[] {
  if (!mentionsLearner(text)) return []
  const errs: string[] = []
  const pairs = text.replace(PAIR_RE, (all, l1: string, _t1: string, l2: string) => {
    if (l1 === l2) errs.push(`${all}: a gender pair has one form for each, {m:…|f:…}`)
    return ''
  })
  const names = pairs.replace(NAME_RE, '')
  for (const m of names.matchAll(/\{learner[^{}]*\}?/g)) errs.push(`${m[0]}: the learner's name is {learner}, or a case of it: ${NAME_CASES.map(c => `{learner:${c}}`).join(', ')}`)
  for (const m of names.matchAll(/\{[mf]:[^}]*\}?/g)) errs.push(`${m[0]}: a gender pair is {m:…|f:…}, both forms (each without braces but {learner})`)
  return errs
}

/** The learner's placeholder problems of every string in [v], each with where it is ("dialogs[0].lines[1].sl: …"). */
export function learnerErrorsIn(v: unknown, at = ''): string[] {
  if (typeof v === 'string') return learnerErrors(v).map(e => (at ? `${at}: ${e}` : e))
  if (Array.isArray(v)) return v.flatMap((x, i) => learnerErrorsIn(x, `${at}[${i}]`))
  if (v && typeof v === 'object') return Object.entries(v as Record<string, unknown>).flatMap(([k, x]) => learnerErrorsIn(x, at ? `${at}.${k}` : k))
  return []
}
