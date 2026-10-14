// The bridge's content validators behind one call, for a JavaScript engine in the app (plan 5, "One source of truth" and
// open question 10; docs/plans/05-phase-0.md, check 4: a spike). validators/build.ts bundles this file for an engine
// without Node: node:fs is an in-memory tree there (validators/fs.ts), which the app fills with lani.mount before it
// checks what reads files (a culture pack, the dictionary). Run by bun from source (test/conformance), node:fs is the
// real one and the same calls give the bridge's own verdicts.
//
// Everything crosses as JSON text, as an engine passes strings: validate(kind, text, refs) parses [text] as the bridge
// parses a file (JSON.parse) and answers {ok, errors} as the kind's validator does, with a digest of what it read when
// asked (the values the defaults filled in).
import { learnerErrorsIn } from './addressee'
import { validateArrival } from './arrivals'
import { validateCard } from './cards'
import { validateCourse, validateUnit, validateWorksheet } from './course'
import { validateCulture } from './cultures'
import { drillProblems, validateDrill } from './drills'
import { validateFormsBook } from './forms'
import { validateGrammar } from './grammar'
import { validateExtra } from './lexicon'
import { validatePack } from './packs'
import { validateScenario } from './scenarios'
import { validateScene, type SceneRefs } from './scenes'
import { validateModule } from './spec'
import { validateStory } from './stories'
import { validateVillager } from './villagers'
import { copy, mount, mounted, unmount, unmountAll } from '../validators/fs'

/** What a check answers: its verdict, the validator's errors as it gives them (a text, or a list), and more on request. */
export type Verdict = {
  ok: boolean
  errors?: string | string[]
  /** What the bridge says besides (a drill's names that aren't there, the words a dialog weaves): not a refusal. */
  notes?: string[]
  /** FNV-1a of what the validator read (JSON), when asked: the defaults and transforms, compared across engines. */
  digest?: string
  /** The input wasn't JSON (the engine's own message: they differ between engines). */
  json?: false
  /** The validator threw (a bug): its message. */
  thrown?: string
}

export type Refs = Record<string, any>
export type Kind = (input: unknown, refs: Refs) => { verdict: Verdict; value?: unknown }

/** A validator's {ok, errors} or {ok, <what it read>}. */
const result = (r: { ok: boolean; errors?: string } & Record<string, unknown>): { verdict: Verdict; value?: unknown } => {
  const { ok, errors, ...rest } = r
  return ok ? { verdict: { ok }, value: Object.values(rest)[0] } : { verdict: { ok, errors } }
}
/** A validator that reports per entry and leaves the wrong ones out (the forms book, the supplement): ok when none is. */
const listed = (r: { errors: string[] } & Record<string, unknown>): { verdict: Verdict; value?: unknown } => {
  const { errors, ...rest } = r
  return { verdict: errors.length ? { ok: false, errors } : { ok: true }, value: rest }
}
const set = (xs: unknown) => (Array.isArray(xs) ? new Set(xs as string[]) : undefined)

/**
 * The kinds, each with the references the bridge checks it against (at load and at publish):
 * - scene: packs, scenarios, villagers, projects (SceneRefs); story: packs, villagers; arrival: language, people;
 * - drill: the bridge loads it on validateDrill alone and logs drillProblems against pages (the grammar book's ids),
 *   packs, villagers;
 * - culture: culturesDir and cultureId (the pack's files mounted under it), villagers (the cast), packs, scenarios and
 *   pages (the grammar book's ids), as the bridge checks its village's pack at start;
 * - learner: the placeholders ({learner}, {m:…|f:…}) of any JSON (addressee.ts).
 */
export const kinds: Record<string, Kind> = {
  module: i => result(validateModule(i)),
  pack: i => result(validatePack(i)),
  scene: (i, r) => result(validateScene(i, r.packs ? (r as SceneRefs) : undefined)),
  story: (i, r) => result(validateStory(i, r.packs || r.villagers ? { packs: r.packs, villagers: r.villagers } : undefined)),
  villager: i => result(validateVillager(i)),
  arrival: (i, r) => result(validateArrival(i, { language: r.language, people: r.people })),
  grammar: i => result(validateGrammar(i)),
  drill: (i, r) => {
    const v = validateDrill(i)
    const out = result(v)
    if (v.ok && (r.pages || r.packs || r.villagers)) {
      const notes = drillProblems(v.drill, { grammar: set(r.pages), packs: r.packs, villagers: r.villagers })
      if (notes.length) out.verdict.notes = notes
    }
    return out
  },
  course: i => result(validateCourse(i)),
  unit: i => result(validateUnit(i)),
  worksheet: i => result(validateWorksheet(i)),
  card: i => result(validateCard(i)),
  scenario: i => result(validateScenario(i)),
  forms: i => listed(validateFormsBook(i)),
  extra: i => listed(validateExtra(i)),
  culture: (_i, r) => result(validateCulture(r.culturesDir, r.cultureId, r.villagers, r.packs ? { packs: r.packs, scenarios: r.scenarios ?? [] } : undefined, set(r.pages))),
  learner: i => {
    const errors = learnerErrorsIn(i)
    return { verdict: errors.length ? { ok: false, errors } : { ok: true } }
  },
}

/** Named references kept between calls (setRefs): a village's packs, cast and scenarios, sent once, not with every file. */
const stored = new Map<string, Refs>()

/** Keeps [json] (an object) as the references named [name]; the number of its keys. */
export function setRefs(name: string, json: string): number {
  const r = JSON.parse(json)
  stored.set(name, r)
  return Object.keys(r).length
}

/** The references of a call: [json] (an object), over the stored ones it names with "use". */
function refsOf(json: string | null | undefined): Refs {
  if (!json) return {}
  const r = JSON.parse(json) as Refs
  if (typeof r.use !== 'string') return r
  const base = stored.get(r.use)
  if (!base) throw new Error(`no references "${r.use}" (setRefs first)`)
  const { use: _use, ...own } = r
  return { ...base, ...own }
}

/** FNV-1a (32 bits) of [s], as 8 hex digits. */
export function fnv1a(s: string): string {
  let h = 0x811c9dc5
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 0x01000193) >>> 0
  }
  return h.toString(16).padStart(8, '0')
}

/** Checks [text] (a file's JSON) as a [kind]; [refs]: its references, as JSON; [opts]: {"digest": true}. */
export function check(kind: string, text: string, refs?: string | null, opts?: string | null): Verdict {
  const k = kinds[kind]
  if (!k) return { ok: false, thrown: `no kind "${kind}" (${Object.keys(kinds).join(', ')})` }
  const o = opts ? JSON.parse(opts) : {}
  let input: unknown
  try {
    input = JSON.parse(text)
  } catch (e) {
    return { ok: false, json: false, errors: (e as Error).message }
  }
  try {
    const { verdict, value } = k(input, refsOf(refs))
    if (o.digest && verdict.ok) verdict.digest = fnv1a(JSON.stringify(value) ?? 'undefined')
    return verdict
  } catch (e) {
    return { ok: false, thrown: (e as Error).message }
  }
}

/** [check], its answer as JSON text (what an engine hands back). */
export const validate = (kind: string, text: string, refs?: string | null, opts?: string | null): string => JSON.stringify(check(kind, text, refs, opts))

/** What the app's engine calls (globalThis.lani). */
export const api = {
  validate,
  setRefs,
  /** Puts a file at [path] for the validators that read files; the number of files mounted. */
  mount: (path: string, text: string) => (mount(path, text), mounted()),
  /** Copies mounted files [rels] (a JSON list) from under [from] to under [to] (a broken copy of a pack, in the tests). */
  copy: (from: string, to: string, rels: string) => copy(from, to, JSON.parse(rels)),
  unmount: (path: string) => unmount(path),
  unmountAll: () => (unmountAll(), 0),
  clearRefs: () => (stored.clear(), 0),
  kinds: () => Object.keys(kinds).join(','),
}
