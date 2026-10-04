// Who a text names (companion/VILLAGERS.md, "Who a text may name"), as the app finds it (game/villagers/Mentions.kt; the
// smoke checks both on the same sentences): a villager of the cast by their own name as the village's language says it
// in a sentence ("Anton", "Antona", "z Antonom", "Antonov med" in Slovene; "Resis" in German), with or without their
// title. Not the learner ("Jan"), not a word that is a name only with its capital ("luka", a harbour), not someone else
// with the same first name and a surname or a title of their own ("France Prešeren", "King Arthur", "Lepa Vida"). The app
// holds back what names someone who isn't in the village or whom the learner hasn't met; the bridge tells the tutor
// (publish_scene) and shows who they are (get_village: people.known).
import type { Scene } from './scenes'

/** Someone a text may name: a villager of the cast (their name "Čebelar Anton"), or someone who moved in ("Ana Furlan"). */
export type Named = { id: string; name: string; source?: string }

/** Someone born here or who moved in goes by their first name; the cast by the last word of theirs (after the title). */
export const isResident = (v: Named) => v.source === 'village' || v.id.startsWith('n-')

export function personal(v: Named): string {
  const name = v.name.trim()
  return isResident(v) ? name.split(' ')[0] : name.slice(name.lastIndexOf(' ') + 1)
}

/** A possessive adjective's endings: Antonov, Antonova, Antonovo, Antonovi, Antonove, Antonovega … */
const POSSESSIVE = ['', 'a', 'o', 'i', 'e', 'ega', 'emu', 'em', 'im', 'ih', 'ima', 'imi']

/** A Slovene name, declined (singular) and as a possessive adjective (Mentions.slovene). */
export function slovene(name: string): string[] {
  const out = new Set([name])
  const cases = (stem: string, ...endings: string[]) => endings.forEach(e => out.add(stem + e))
  const possessive = (stem: string, suffix: string) => POSSESSIVE.forEach(e => out.add(stem + suffix + e))
  const soft = (stem: string) => (stem.endsWith('c') ? stem.slice(0, -1) + 'č' : stem)
  const last = name.slice(-1).toLowerCase()
  if (last === 'a') {
    const s = name.slice(0, -1)
    cases(s, 'a', 'e', 'i', 'o', 'ama', 'ami', 'ah')
    possessive(soft(s), 'in')
    possessive(s, 'ov')
  } else if (last === 'o') {
    const s = name.slice(0, -1)
    cases(s, 'a', 'u', 'om')
    possessive(s, 'ov')
  } else if (last === 'e') {
    cases(name + 't', 'a', 'u', 'om')
    possessive(name + 't', 'ov')
  } else if ('cčšžj'.includes(last)) {
    cases(name, 'a', 'u', 'em')
    possessive(soft(name), 'ev')
  } else if ('iuy'.includes(last)) {
    cases(name + 'j', 'a', 'u', 'em')
    possessive(name + 'j', 'ev')
  } else {
    cases(name, 'a', 'u', 'om')
    possessive(name, 'ov')
  }
  return [...out]
}

/** The forms a name takes in a sentence of [lang]: Slovene declines it, German adds a genitive -s. */
export function forms(name: string, lang: string): string[] {
  if (lang === 'sl') return slovene(name)
  if (lang === 'de') return [name, name + 's']
  return [name]
}

const WORD = /\p{L}+/gu
/** What a sentence (or a quote, a line) starts after: a capital there is no title. */
const SENTENCE = '.!?…:;«»"„“”\'(—–-\n'
const isUpper = (c: string) => c !== c.toLowerCase() && c === c.toUpperCase()
const isLower = (c: string) => c !== c.toUpperCase() && c === c.toLowerCase()
/** A word with a capital and a small letter: a name, a title ("Prešeren", "King"); not "OK". */
const capital = (w: string) => w.length >= 2 && isUpper(w[0]) && isLower(w[1])

/** The people of [cast] a text in [language] names (Names in Mentions.kt). */
export class Names {
  private forms = new Map<string, Set<string>>()
  private families = new Map<string, string>()
  private titles = new Map<string, string[]>()
  /** Their names by id ("Čebelar Anton"). */
  readonly names = new Map<string, string>()
  /** German capitalises every noun: a capital next to a name there is no one else's surname or title. */
  private capitals: boolean

  constructor(cast: Named[], readonly language: string) {
    this.capitals = language !== 'de'
    for (const v of cast) {
      const own = personal(v)
      if (own.length < 2 || !isUpper(own[0])) continue
      this.names.set(v.id, v.name)
      for (const f of forms(own, language)) {
        const set = this.forms.get(f) ?? new Set<string>()
        set.add(v.id)
        this.forms.set(f, set)
      }
      const words = v.name.trim().split(' ').filter(w => w)
      if (isResident(v) && words.length > 1) this.families.set(v.id, words[words.length - 1])
      this.titles.set(v.id, words.filter(w => w !== own).map(w => (w.length > 3 ? w.slice(0, -1) : w)))
    }
  }

  /** The ids [text] names. */
  of(text: string | undefined): Set<string> {
    const out = new Set<string>()
    if (!text || this.forms.size === 0) return out
    for (const m of text.matchAll(WORD)) {
      const who = this.forms.get(m[0])
      if (!who) continue
      const start = m.index ?? 0
      const end = start + m[0].length
      if (this.capitals && (this.surnamed(text, end, who) || this.titled(text, start, who))) continue
      for (const id of who) out.add(id)
    }
    return out
  }

  /** A surname that isn't theirs right after the name ("France Prešeren"). */
  private surnamed(text: string, end: number, who: Set<string>): boolean {
    if (text[end] !== ' ') return false
    const next = /^\p{L}+/u.exec(text.slice(end + 1))?.[0]
    if (!next || !capital(next) || this.forms.has(next)) return false
    return ![...who].some(id => this.families.get(id) === next)
  }

  /** A title that isn't theirs right before the name, inside a sentence ("King Arthur", "Lepa Vida"). */
  private titled(text: string, start: number, who: Set<string>): boolean {
    if (start < 2 || text[start - 1] !== ' ') return false
    let j = start - 2
    while (j >= 0 && /\p{L}/u.test(text[j])) j--
    const prev = text.slice(j + 1, start - 1)
    if (!capital(prev) || this.forms.has(prev)) return false
    let k = j
    while (k >= 0 && text[k] === ' ') k--
    if (k < 0 || SENTENCE.includes(text[k])) return false
    return ![...who].some(id => (this.titles.get(id) ?? []).some(t => prev.startsWith(t)))
  }
}

type Texts = Record<string, unknown> | undefined

/** A text of a scene in its language: {"sl": …} or "target · English" (a title). */
function said(t: unknown, lang: string): string | undefined {
  if (typeof t === 'string') return t.split(' · ')[0]
  const s = (t as Texts)?.[lang]
  return typeof s === 'string' ? s : undefined
}

/**
 * Who each happening of [scene] names besides its own person (its dialogs' lines, the choices and the replies, and what
 * its person remembers, all its variants; its title and its memory, in the scene's language), as the app keeps it back
 * (Happenings.on: the happening while its title or memory names someone, a variant while its dialog does).
 */
export function sceneMentions(scene: Pick<Scene, 'language' | 'happenings' | 'people' | 'dialogs'> & { variants?: Scene['variants'] }, names: Names): { happening: string; who: string; named: string[] }[] {
  const lang = scene.language
  const all = [...scene.dialogs, ...(scene.variants ?? [])]
  return scene.happenings.flatMap(h => {
    const own = scene.people.find(p => p.id === h.who)?.villager
    const ids = h.dialogs ?? (h.dialog ? [h.dialog] : [])
    const texts = [said(h.title, lang), said(h.memory, lang)]
    for (const dialog of all.filter(d => ids.includes(d.id))) {
      texts.push(said(dialog.memory, lang))
      for (const l of dialog.lines) {
        texts.push(said(l, lang))
        for (const c of l.choices) texts.push(said(c, lang), said(c.reply, lang))
      }
    }
    const named = new Set(texts.flatMap(t => [...names.of(t)]))
    if (own) named.delete(own)
    return named.size ? [{ happening: h.id, who: h.who, named: [...named] }] : []
  })
}

type Village = { residents?: { id?: unknown; name?: unknown }[]; bonds?: Record<string, { met?: unknown }>; migrated?: unknown; visitor?: { id?: unknown; on?: unknown } }

/** The day on this node (ISO), for today's visitor. */
const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * Who is in the village and met, from the app's state (Residents.present): the residents and today's visitor, those the
 * learner has met once the village introduces its people (arrivals, `migrated`); undefined while nobody lives there.
 */
export function knownIds(state: unknown): Set<string> | undefined {
  const s = state as Village | undefined
  const residents = Array.isArray(s?.residents) ? s.residents.flatMap(r => (typeof r?.id === 'string' ? [r.id] : [])) : []
  if (!residents.length) return undefined
  const v = s?.visitor
  const living = [...residents, ...(typeof v?.id === 'string' && v.id && v.on === today() ? [v.id] : [])]
  const introduces = Array.isArray(s?.migrated) && s.migrated.includes('arrivals')
  return new Set(introduces ? living.filter(id => s?.bonds?.[id]?.met != null) : living)
}

/** The people who moved in or were born in the village (their names), as texts may name them. */
export function residentsNamed(state: unknown): Named[] {
  const rs = (state as Village | undefined)?.residents
  return Array.isArray(rs) ? rs.flatMap(r => (typeof r?.id === 'string' && typeof r.name === 'string' ? [{ id: r.id, name: r.name, source: 'village' }] : [])) : []
}
