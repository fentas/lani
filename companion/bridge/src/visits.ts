// Visiting a linked town (companion/README.md, "Towns", "Visiting"; plan 2, §3.1): what one town lets a linked town
// see besides its public state (towns.ts), so the visitor's app can draw it and people it:
// - its people: the cast of its culture pack, as the host's own app gets them (their looks, voices, roles, homes and
//   lines), never the ones its tutor made (the tutor's newcomers, a changed villager: private to the host);
// - its places: the curated scenes of its language and of its culture pack (the sea at the horizon), resolved with the
//   curated word packs only, never the tutor's scenes or packs.
// The visitor's bridge checks and cleans every answer again (a town's content is untrusted), and keeps the last good
// one per town for a while ([PeerCache]): fresh for a minute, and when the host stops answering, stale with a note.
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { CULTURE_ID, type CultureTutor } from './cultures'
import { partNow, type Part } from './langs'
import type { Scenario } from './scenarios'
import { validateScene, type ResolvedScene } from './scenes'
import { TOWN_VERSION } from './towns'
import { learnerLevel, validateVillager, villagerBrief, villagerScenario, type LoadedVillager, type TalkPlace, type Villager } from './villagers'

const LANGUAGE = /^[a-z]{2,3}$/
/** At most this many people and places are passed on. */
export const MAX_VILLAGERS = 80
export const MAX_SCENES = 60

/** A text from another town as the app gets it: no control characters, no < or >, as long as its format allows. */
const cleanString = (s: string) => s.replace(/[\u0000-\u001f\u007f<>]/g, ' ')

/** [x] with every text in it cleaned ([cleanString]), keys and numbers as they are. */
export function cleanStrings<T>(x: T): T {
  if (typeof x === 'string') return cleanString(x) as T
  if (Array.isArray(x)) return x.map(cleanStrings) as T
  if (x && typeof x === 'object') return Object.fromEntries(Object.entries(x).map(([k, v]) => [k, cleanStrings(v)])) as T
  return x
}

/** Who a town is and what it speaks, on its visit answers. */
export type VisitHead = { v: number; town: string; culture: string; language: string }

/** A villager as a visitor gets them: the spec's fields, nothing of where it was stored. */
export type PublicVillager = Omit<LoadedVillager, 'published_at'>

/** A scene as a visitor gets it: resolved, curated, without when it was written. */
export type PublicScene = Omit<ResolvedScene, 'published_at'>

/** The host's side: its culture's cast (the curated villagers), as they are. */
export function publicVillagers(head: VisitHead, curated: LoadedVillager[]): VisitHead & { villagers: PublicVillager[] } {
  return { ...head, villagers: curated.filter(v => v.source === 'curated').map(({ published_at: _, ...v }) => ({ ...v, source: 'curated' as const })) }
}

/** The host's side: its curated scenes (its language's and its culture pack's), resolved with the curated packs. */
export function publicScenes(head: VisitHead, curated: ResolvedScene[]): VisitHead & { scenes: PublicScene[] } {
  return { ...head, scenes: curated.filter(s => s.source === 'curated').map(({ published_at: _, ...s }) => ({ ...s, source: 'curated' as const })) }
}

/** The head of another town's visit answer, checked: its id is the one asked ([id]); undefined otherwise. */
export function cleanHead(b: any, id: string): VisitHead | undefined {
  if (!b || typeof b !== 'object' || b.town !== id) return undefined
  return {
    v: Number.isInteger(b.v) ? b.v : TOWN_VERSION,
    town: id,
    culture: typeof b.culture === 'string' && CULTURE_ID.test(b.culture) ? b.culture : '',
    language: typeof b.language === 'string' && LANGUAGE.test(b.language) ? b.language : '',
  }
}

/**
 * Another town's people as this bridge passes them on: each checked as a villager (lani.villager/v0: its shape, its
 * lengths, its lines) and cleaned; one that isn't is left out. Undefined when the answer isn't for town [id].
 */
export function cleanVillagers(b: any, id: string): (VisitHead & { villagers: PublicVillager[] }) | undefined {
  const head = cleanHead(b, id)
  if (!head) return undefined
  const seen = new Set<string>()
  const villagers = (Array.isArray(b.villagers) ? b.villagers.slice(0, MAX_VILLAGERS) : []).flatMap((raw: unknown) => {
    const v = validateVillager(raw)
    if (!v.ok || seen.has(v.villager.id)) return []
    seen.add(v.villager.id)
    return [cleanStrings({ ...v.villager, source: 'curated' as const })]
  })
  return { ...head, villagers }
}

/** The words an object of a resolved scene carries (resolveScene), which the scene's format doesn't know. */
const RESOLVED = ['sl', 'en', 'it', 'de', 'emoji', 'gender', 'plural', 'example_sl', 'example_en', 'example_it', 'example_de'] as const

/**
 * Another town's scenes as this bridge passes them on: each checked as a scene (lani.scene/v0; the host's own packs
 * and cast aren't known here, so what they name isn't checked) and cleaned, its objects' resolved words kept as short
 * texts. A scene that isn't one is left out. Undefined when the answer isn't for town [id].
 */
export function cleanScenes(b: any, id: string): (VisitHead & { scenes: PublicScene[] }) | undefined {
  const head = cleanHead(b, id)
  if (!head) return undefined
  const seen = new Set<string>()
  const scenes = (Array.isArray(b.scenes) ? b.scenes.slice(0, MAX_SCENES) : []).flatMap((raw: any) => {
    const v = validateScene(raw)
    if (!v.ok || seen.has(v.scene.id)) return []
    seen.add(v.scene.id)
    const rawObjects: any[] = Array.isArray(raw?.objects) ? raw.objects : []
    const objects = v.scene.objects.map(o => {
      const r = rawObjects.find(x => x?.slot === o.slot) ?? {}
      const words: Record<string, string> = {}
      for (const k of RESOLVED) if (typeof r[k] === 'string' && r[k].length <= 300) words[k] = r[k]
      return { ...o, en: '', ...words }
    })
    const culture = typeof raw?.culture === 'string' && CULTURE_ID.test(raw.culture) ? { culture: raw.culture as string } : {}
    return [cleanStrings({ ...v.scene, objects, source: 'curated' as const, ...culture }) as PublicScene]
  })
  return { ...head, scenes }
}

/**
 * The last good answer of each linked town, per kind ("public", "villagers", "scenes"): [fresh] while younger than
 * [freshMs] (asked again after), and [stale] while younger than [staleMs], for when the town doesn't answer.
 */
export class PeerCache<T = unknown> {
  private entries = new Map<string, { at: number; body: T }>()

  constructor(
    readonly freshMs = 60_000,
    readonly staleMs = 24 * 60 * 60_000,
  ) {}

  put(id: string, kind: string, body: T, now = Date.now()) {
    this.entries.set(`${id}:${kind}`, { at: now, body })
  }

  fresh(id: string, kind: string, now = Date.now()) {
    const e = this.entries.get(`${id}:${kind}`)
    return e && now - e.at < this.freshMs ? e : undefined
  }

  stale(id: string, kind: string, now = Date.now()) {
    const e = this.entries.get(`${id}:${kind}`)
    return e && now - e.at < this.staleMs ? e : undefined
  }

  /** Forgets town [id] (it no longer knows this one, or was unlinked). */
  drop(id: string) {
    for (const k of [...this.entries.keys()]) if (k.startsWith(`${id}:`)) this.entries.delete(k)
  }
}

// --- talking on a visit ---------------------------------------------------------------------------------------------

/**
 * What the tutor gets on every turn of a role-play on a visit (chat.ts): the scenario (its brief) and [data]: who is
 * played (a host villager's persona) and the visit (the town, its language, the learner's level in it, the trade).
 */
export type VisitTalk = { scenario: Scenario; data: Record<string, unknown> }

/** The role-plays the app started on visits, by scenario id, kept for a few hours (a talk's turns come within minutes). */
export class VisitTalks {
  private talks = new Map<string, VisitTalk & { at: number }>()

  constructor(readonly keepMs = 6 * 60 * 60_000) {}

  put(t: VisitTalk, now = Date.now()) {
    for (const [k, v] of this.talks) if (now - v.at > this.keepMs) this.talks.delete(k)
    this.talks.set(t.scenario.id, { ...t, at: now })
  }

  get(id: string, now = Date.now()): VisitTalk | undefined {
    const t = this.talks.get(id)
    return t && now - t.at <= this.keepMs ? t : undefined
  }
}

const LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'] as const

/**
 * The learner's level in [language] (docs/DB_SCRIPTS.md, "Languages"): the home language's ([target], or the profile's
 * home_language) is their level; another language's is in its own profile (languages/<code>/learner-profile.json), else
 * its copy in the home profile's `languages`; a language never practised is A1: a visit in it starts easy.
 */
export function levelIn(dataDir: string, language: string, target: string): Scenario['level'] {
  const read = (path: string) => {
    try {
      return JSON.parse(readFileSync(path, 'utf8'))
    } catch {
      return undefined
    }
  }
  const home = read(join(dataDir, 'learner-profile.json'))
  if (language === (typeof home?.home_language === 'string' ? home.home_language : target)) return learnerLevel(dataDir)
  if (!/^[a-z]{2,3}$/.test(language)) return 'A1'
  const own = read(join(dataDir, 'languages', language, 'learner-profile.json'))?.learner?.current_level
  if (LEVELS.includes(own)) return own
  const copy = (Array.isArray(home?.languages) ? home.languages : []).find((l: any) => l?.code === language)?.level
  return LEVELS.includes(copy) ? copy : 'A1'
}

/** Where a talk on a visit happens: the host town, its culture's texts for the tutor, and the visitor. */
export type VisitPlace = {
  town: { id: string; name: string; learner: string; culture: string; language: string }
  tutor: CultureTutor
  visitor: { name: string; village: string; base: string; level: Scenario['level'] }
}

/** The visit, as every turn tells the tutor: the town, the language to play in, the visitor's level in it. */
const visitOf = (p: VisitPlace, kind: 'villager' | 'market') => ({
  kind,
  town: { id: p.town.id, name: p.town.name, learner: p.town.learner, culture: p.town.culture },
  language: p.town.language,
  level: p.visitor.level,
})

/** The host's culture texts with the setting told for a visit: the host's village, and who is visiting from where. */
function visitTutor(p: VisitPlace): CultureTutor {
  const setting = `${p.tutor.setting.replaceAll('{learner}', p.town.learner || 'the host')}. ${p.visitor.name} is visiting from ${p.visitor.village}, a linked town`
  return { ...p.tutor, setting, village: p.town.name || p.tutor.village }
}

/**
 * A talk with a person of the visited town: the host's villager, played by the visitor's own tutor in the host's
 * language at the visitor's level in it, a stranger to them (the friendship is the host's), greeting for the [part] of
 * the day. Its id is "town:<town id>:<villager id>".
 */
export function visitVillagerTalk(v: Villager, p: VisitPlace, part: Part = partNow()): VisitTalk {
  const stranger = { points: 0, memories: [] }
  const place: TalkPlace = { target: p.town.language, base: p.visitor.base, learner: p.visitor.name, tutor: visitTutor(p) }
  const s = villagerScenario(v, stranger, p.visitor.level, place, part)
  // `language`: whose data the talk's words and mistakes go to (update-db.py's report `language`, docs/DB_SCRIPTS.md)
  return { scenario: { ...s, id: `town:${p.town.id}:${v.id}` }, data: { villager: villagerBrief(v, stranger), visit: visitOf(p, 'villager'), language: p.town.language } }
}

/** One side of a trade as the tutor and the hints see it: how many of what, named per language. */
export type TradeLine = { n: number; emoji: string; name: Record<string, string> }

/** A market's phrases in each language: the seller's greeting (by day, and from the evening on), and what a buyer says. */
const MARKET: Record<string, { hello: string; evening: string; offer: string; how: string; deal: string; bye: string }> = {
  sl: { hello: 'Dober dan! Kaj ste prinesli za menjavo?', evening: 'Dober večer! Kaj ste prinesli za menjavo?', offer: 'Ponujam … za …', how: 'Koliko?', deal: 'Velja, dogovorjeno!', bye: 'Hvala, na svidenje!' },
  it: { hello: 'Buongiorno! Cosa ha portato da scambiare?', evening: 'Buonasera! Cosa ha portato da scambiare?', offer: 'Offro … per …', how: 'Quanto?', deal: 'Affare fatto!', bye: 'Grazie, arrivederci!' },
  de: { hello: 'Guten Tag! Was haben Sie zum Tauschen mitgebracht?', evening: 'Guten Abend! Was haben Sie zum Tauschen mitgebracht?', offer: 'Ich biete … für …', how: 'Wie viel?', deal: 'Abgemacht!', bye: 'Danke, auf Wiedersehen!' },
  en: { hello: 'Good day! What have you brought to trade?', evening: 'Good evening! What have you brought to trade?', offer: 'I offer … for …', how: 'How much?', deal: "It's a deal!", bye: 'Thanks, goodbye!' },
}

const cut = (s: string, max: number) => (s.length <= max ? s : s.slice(0, max - 1).trimEnd() + '…')
const nameIn = (l: TradeLine, lang: string) => l.name[lang] ?? l.name.en ?? Object.values(l.name)[0] ?? '?'
const lineText = (l: TradeLine, lang: string) => `${l.n} × ${nameIn(l, lang)}`

/**
 * A haggle at the visited town's market: its seller (the culture's market, played by the visitor's tutor in the host's
 * language), the trade the visitor wants ([give] for [get]). The tutor's debrief says whether they agreed and how well it
 * went (a score), which decides the trade. Its id is "market:<town id>:<n>". The seller greets for the [part] of the day
 * (this node's, by default).
 */
export function visitMarketTalk(o: { place: VisitPlace; seller: { emoji: string; name: Record<string, string> }; give: TradeLine[]; get: TradeLine[]; n: string; part?: Part }): VisitTalk {
  const p = o.place
  const lang = p.town.language
  const base = p.visitor.base
  const say = MARKET[lang] ?? MARKET.en
  const gloss = MARKET[base] ?? MARKET.en
  // the seller greets for the time of day: good day by day, good evening from the evening on
  const byDay = ['morning', 'afternoon'].includes(o.part ?? partNow())
  const tutor = visitTutor(p)
  const offer = o.give.map(l => lineText(l, 'en')).join(', ')
  const want = o.get.map(l => lineText(l, 'en')).join(', ')
  const goods = [...o.give, ...o.get].flatMap(l => (l.name[lang] ? [{ sl: cut(l.name[lang], 80), en: cut(l.name[base] ?? l.name.en ?? l.name[lang], 80) }] : []))
  const scenario: Scenario = {
    schema: 'lani.scenario/v0',
    id: `market:${p.town.id}:${o.n}`,
    title: cut(o.seller.name[lang] ?? o.seller.name.en ?? 'Market', 80),
    emoji: o.seller.emoji || '🏪',
    level: p.visitor.level,
    setting: cut(`${tutor.setting}. At the village market ${p.visitor.name} wants to trade ${offer} for ${want}.`, 400),
    role: cut(`The market seller of ${tutor.village}: friendly, likes a little haggling, speaks only the village's language, simply, at ${p.visitor.name}'s level. Agrees when asked politely and clearly; may ask for a little more first.`, 300),
    voice: 'male',
    goals: ['Greet the seller', cut(`Offer ${offer}`, 80), cut(`Ask for ${want}`, 80), 'Agree on the deal and say goodbye'],
    opener_sl: byDay ? say.hello : say.evening,
    opener_en: byDay ? gloss.hello : gloss.evening,
    vocabulary_hints: [
      ...goods,
      { sl: say.offer, en: gloss.offer },
      { sl: say.how, en: gloss.how },
      { sl: say.deal, en: gloss.deal },
      { sl: say.bye, en: gloss.bye },
    ].slice(0, 12),
  }
  const side = (l: TradeLine) => ({ n: l.n, name: nameIn(l, lang), en: nameIn(l, 'en') })
  return { scenario, data: { visit: { ...visitOf(p, 'market'), trade: { give: o.give.map(side), get: o.get.map(side) } }, language: p.town.language } }
}
