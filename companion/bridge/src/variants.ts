// A happening's dialogs, all heard (companion/SCENES.md, "Variants: not the same dialog every time"): the app records
// what the learner heard to its end (GameState.dialogsHeard, synced with the village); when every variant of a
// happening is heard, the bridge asks the tutor for a fresh one (dialog_variants_heard), a few a day, each happening
// once until a variant is added, and the tutor writes it with publish_dialog_variant.
import { existsSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { forGuests, variantsOf, type ResolvedScene, type Scene } from './scenes'

/** At most this many happenings a day are asked for (the tutor writes each variant with care). */
export const MAX_ASKS_A_DAY = 3

type Heard = Record<string, { heard?: Record<string, unknown>; last?: unknown }>

/** A happening all of whose dialogs the learner has heard: its scene, its variants and when each was heard. */
export type AllHeard = { scene: ResolvedScene; happening: Scene['happenings'][number]; key: string; variants: string[]; heard: Record<string, string> }

/**
 * The happenings of [scenes] whose every dialog the learner has heard, from the village's [state] (its dialogsHeard, by
 * "scene/happening"); not a storyteller's, nor one meant for guests (the learner never meets those at home).
 */
export function allHeard(scenes: ResolvedScene[], state: unknown): AllHeard[] {
  const dh = ((state as { dialogsHeard?: Heard } | undefined)?.dialogsHeard ?? {}) as Heard
  return scenes.flatMap(s =>
    s.happenings.flatMap(h => {
      if (h.stories || forGuests(s, h)) return []
      const key = `${s.id}/${h.id}`
      const variants = variantsOf(h).filter(id => s.dialogs.some(d => d.id === id))
      const heard = dh[key]?.heard ?? {}
      if (!variants.length || !variants.every(id => typeof heard[id] === 'string')) return []
      return [{ scene: s, happening: h, key, variants, heard: Object.fromEntries(variants.map(id => [id, String(heard[id])])) }]
    }),
  )
}

type Asks = Record<string, { on: string; variants: string[] }>

/** Which happenings the tutor was asked about, and when (<data>/app/variant-asks.json). */
export class VariantAsks {
  constructor(private readonly path: string) {}

  read(): Asks {
    try {
      return existsSync(this.path) ? (JSON.parse(readFileSync(this.path, 'utf8')) as Asks) : {}
    } catch {
      return {}
    }
  }

  /**
   * Of [all], those to ask about [today]: not asked yet with these same variants (a variant added since: asked again once
   * those are heard too), at most [max] a day with the ones asked earlier today.
   */
  due(all: AllHeard[], today: string, max = MAX_ASKS_A_DAY): AllHeard[] {
    const asks = this.read()
    const left = max - Object.values(asks).filter(a => a.on === today).length
    if (left <= 0) return []
    const same = (a: string[] | undefined, b: string[]) => !!a && a.length === b.length && b.every(x => a.includes(x))
    return all.filter(a => !same(asks[a.key]?.variants, a.variants)).slice(0, left)
  }

  mark(a: AllHeard, today: string) {
    const asks = { ...this.read(), [a.key]: { on: today, variants: a.variants } }
    writeFileSync(`${this.path}.tmp`, JSON.stringify(asks, null, 2))
    renameSync(`${this.path}.tmp`, this.path)
  }
}

/** A weak spot of the learner's, from mistakes-db (error_patterns). */
export type WeakSpot = { pattern: string; category?: string; description?: string; example?: { incorrect?: string; correct?: string } }

/** The learner's weak spots: the patterns still going wrong, the most frequent first ([n] of them). */
export function weakSpots(mistakesDb: unknown, n = 3): WeakSpot[] {
  const ps = ((mistakesDb as { error_patterns?: Record<string, any> } | undefined)?.error_patterns ?? {}) as Record<string, any>
  return Object.entries(ps)
    .filter(([, p]) => (p?.consecutive_incorrect ?? 0) > 0)
    .sort(([, a], [, b]) => (b?.frequency ?? 0) - (a?.frequency ?? 0))
    .slice(0, n)
    .map(([pattern, p]) => {
      const e = Array.isArray(p?.examples) ? p.examples[p.examples.length - 1] : undefined
      return {
        pattern,
        ...(p?.category ? { category: String(p.category) } : {}),
        ...(p?.description ? { description: String(p.description) } : {}),
        ...(e ? { example: { incorrect: e.incorrect, correct: e.correct } } : {}),
      }
    })
}

/** A text of a scene in its language ({"sl": …}), or a title ("target · English"). */
const said = (t: unknown, lang: string): string | undefined =>
  typeof t === 'string' ? t.split(' · ')[0] : typeof (t as Record<string, unknown>)?.[lang] === 'string' ? ((t as Record<string, string>)[lang]) : undefined

/**
 * The note to the tutor for [a]: which happening, its person (named with [nameOf]), the dialogs heard (their first lines
 * and when), the learner's [level] and [weak] spots; how to write the fresh one.
 */
export function variantNote(a: AllHeard, level: string, weak: WeakSpot[], nameOf: (id: string) => string | undefined = () => undefined): string {
  const s = a.scene
  const lang = s.language
  const person = s.people.find(p => p.id === a.happening.who)
  const name = (person?.villager && nameOf(person.villager)) ?? person?.name ?? a.happening.who
  const info = {
    scene: s.id,
    scene_title: said(s.title, lang),
    language: lang,
    happening: a.happening.id,
    title: a.happening.title,
    when: a.happening.when,
    person: { id: a.happening.who, name, ...(person?.villager ? { villager: person.villager } : {}) },
    memory: a.happening.memory,
    learner_level: level,
    heard: a.variants.map(id => {
      const d = s.dialogs.find(x => x.id === id)
      return { dialog: id, on: a.heard[id], first_line: said(d?.lines[0], lang), ...(d?.count ? { counts: true } : {}), ...(d?.source === 'tutor' ? { tutor: true } : {}) }
    }),
    weak_spots: weak,
  }
  return (
    `App: the learner has heard every dialog of a happening, ${name} in ${said(s.title, lang) ?? s.id}, "${said(a.happening.title, lang) ?? a.happening.id}" ` +
    `(${a.variants.length} ${a.variants.length === 1 ? 'dialog' : 'variants'}). Write a fresh variant with publish_dialog_variant (scene "${s.id}", ` +
    `happening "${a.happening.id}"): the same situation (its title, its time, its person) in other words, with another point of ` +
    `grammar, at the learner's level (${level}), around one of their weak spots below where it fits naturally. The rules of a ` +
    `scene's dialogs hold (load lani-studio, "Scenes"): a reaction to every wrong choice that never says the right form, two ` +
    `right answers where people would accept either, the learner's lines in the masculine, a text naming only the person and ` +
    `whom the happening names; a number with "count" ({n}) where something is counted. It plays the next time the happening ` +
    `comes. No reply needed (the tool's note is optional).\n\n\`\`\`json\n${JSON.stringify(info, null, 2)}\n\`\`\``
  )
}
