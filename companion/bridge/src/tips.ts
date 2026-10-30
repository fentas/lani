// The app's tips ("💡 Namigi · Tips", companion/GAME.md "Guidance"): the moments the game notices and the one tap each
// offers, as the tutor sees them (get_village's tips) and points at them (show_tip). The ids are the app's catalogue
// (android game/Guidance.kt MOMENTS; GuidanceTest checks the two lists agree).

/** A moment of the app's catalogue: its id, where it shows, what it says and what its one tap opens (for the tutor). */
export type TipMoment = { id: string; where: 'map' | 'home' | 'scene'; about: string }

export const TIP_MOMENTS: TipMoment[] = [
  { id: 'full-wisdom', where: 'map', about: 'the wisdom store is full: trade some (the trade sheet, wisdom chosen to give) or spend it' },
  { id: 'full-stone', where: 'map', about: 'the stone store is full: trade some, or spend it' },
  { id: 'full-wood', where: 'map', about: 'the wood store is full: trade some, or spend it' },
  { id: 'full-food', where: 'map', about: 'the food store is full: trade some, or build or upgrade something' },
  { id: 'trade', where: 'map', about: 'trading one resource for another (the fullest store chosen to give); only when you point at it' },
  { id: 'repair', where: 'map', about: 'the first damaged building: it gives nothing until repaired, the neighbours help (a moba)' },
  { id: 'festival', where: 'map', about: "the first festival on today: celebrate it" },
  { id: 'back', where: 'map', about: 'a request tried below the pass mark comes back tomorrow; its drill ("Practise and try again") brings it back today' },
  { id: 'arrival', where: 'map', about: 'someone moved into the village: meet them' },
  { id: 'school', where: 'map', about: 'the school stands: the lesson suggested next for the learner\'s level' },
  { id: 'class', where: 'map', about: 'the class has begun (from the hamlet, before the school): the lesson suggested next' },
  { id: 'request', where: 'map', about: "the village's first request: help (the request's run)" },
  { id: 'upgrade', where: 'map', about: 'the first upgrade within reach: the words it asks for first, then the upgrade' },
  { id: 'market', where: 'map', about: 'the market stands: better trades, goods to buy and sell' },
  { id: 'smithy', where: 'map', about: "the smithy stands: the smith forges the villagers' tools stronger (the chest)" },
  { id: 'visitor', where: 'map', about: 'a visitor on the road: say hello' },
  { id: 'scene', where: 'map', about: 'the first place to go into (a scene): tap things and people inside' },
  { id: 'project', where: 'map', about: 'a village project: one step a day, with the whole village' },
  { id: 'lipa', where: 'map', about: 'the linden stands: the village meets under it (feasts, talks, class)' },
  { id: 'well', where: 'map', about: "the well stands: the village square's life" },
  { id: 'watchtower', where: 'map', about: 'the watchtower stands: it warns of wolves and storms sooner (12 hours more)' },
  { id: 'church', where: 'map', about: 'the church stands: go in' },
  { id: 'beehive', where: 'map', about: 'the bee house stands: go in to the bees' },
  { id: 'home', where: 'map', about: 'the first hut or house: its card shows who lives there' },
  { id: 'palisade', where: 'map', about: 'the palisade stands: it guards against wolves and the bear' },
  { id: 'chest', where: 'map', about: "the villagers' gifts in the chest: give, sell or buy goods" },
  { id: 'surprise', where: 'map', about: "the day's surprise on the road" },
  { id: 'age-tabor', where: 'map', about: 'a new age, the camp: new buildings to build' },
  { id: 'age-zaselek', where: 'map', about: 'a new age, the hamlet: new buildings to build' },
  { id: 'age-vas', where: 'map', about: 'a new age, the village: new buildings to build' },
  { id: 'age-trg', where: 'map', about: 'a new age, the market town: new buildings to build' },
  { id: 'age-mesto', where: 'map', about: 'a new age, the town: new buildings to build' },
  { id: 'reviews', where: 'home', about: 'the first reviews are due' },
  { id: 'pack-done', where: 'home', about: 'a word pack all learned: pick the next' },
  { id: 'cases', where: 'home', about: 'the case trainer is open' },
  { id: 'grammar', where: 'home', about: 'the grammar book has pages: open it' },
  { id: 'chat', where: 'home', about: 'the learner can ask you anything in the chat' },
  { id: 'talk', where: 'home', about: 'a talk (role-play) with a villager' },
  { id: 'friends', where: 'home', about: "a friend's town to visit" },
  { id: 'inside', where: 'scene', about: 'the first time inside a scene: tap things to hear their words, people to talk' },
]

const IDS = new Set(TIP_MOMENTS.map(m => m.id))

export const isTipMoment = (id: string) => IDS.has(id)

const day = (v: unknown) => (typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v) ? v : undefined)

/**
 * The village's tips as the tutor sees them (the village state's tips, GAME.md "Guidance"): whether they are off, the
 * moments first shown in the last two weeks (newest first, each with whether it was dealt with, put off till a day, or is
 * still open), your pointer (show_tip) while it waits, and how many were dealt with in all. Undefined before the tips began.
 */
export function tipsOf(t: any, today: string) {
  if (!t || typeof t !== 'object') return undefined
  const shown = t.shown && typeof t.shown === 'object' ? (t.shown as Record<string, unknown>) : {}
  const seen = t.seen && typeof t.seen === 'object' ? (t.seen as Record<string, unknown>) : {}
  const later = t.later && typeof t.later === 'object' ? (t.later as Record<string, unknown>) : {}
  const from = new Date(`${today}T00:00:00Z`)
  from.setUTCDate(from.getUTCDate() - 14)
  const since = from.toISOString().slice(0, 10)
  const recent = Object.entries(shown)
    .flatMap(([id, d]) => (day(d) && (d as string) >= since ? [{ id, d: d as string }] : []))
    .sort((a, b) => b.d.localeCompare(a.d))
    .map(({ id, d }) => {
      const s = day(seen[id])
      const l = day(later[id])
      return { moment: id, shown: d, ...(s && s >= d ? { dealt: s } : l && l > today ? { later: l } : { open: true }) }
    })
  const asked = t.asked && typeof t.asked?.moment === 'string' ? { moment: t.asked.moment, note: typeof t.asked.note === 'string' ? t.asked.note : undefined } : undefined
  if (!day(t.since) && !recent.length && !asked && t.off !== true) return undefined
  return {
    off: t.off === true || undefined,
    since: day(t.since),
    recent: recent.length ? recent : undefined,
    asked,
    dealt: Object.keys(seen).length || undefined,
  }
}
