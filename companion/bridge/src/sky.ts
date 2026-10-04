// lani.sky/v0: a culture pack's night sky (companion/cultures/<id>/sky.json, optional; companion/GAME.md "The night
// sky"). Where the village lies under the sky, and what its people call what is in it, in the pack's language with
// translations: the moon's phases and lore, the constellations, the named stars, the planets (the morning and the
// evening star), the Milky Way, the shooting stars and the year's meteor showers. The app bundles the file with the
// pack and draws the real sky itself (game/sky: the star catalog, the moon, the planets); this checks the file with the
// pack (validateCulture), so a broken one is caught before it ships. An older bridge ignores the file.
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { schemaField } from './schema'

export const SKY_SCHEMA = 'lani.sky/v0'

/** The moon's phases as sky.json keys them (the app's Moon.Phase, snake_case). */
export const MOON_PHASES = ['new', 'waxing_crescent', 'first_quarter', 'waxing_gibbous', 'full', 'waning_gibbous', 'last_quarter', 'waning_crescent'] as const

/** Mirrored from the app's sky catalog (resources/sky/figures.tsv): the constellations drawn, and the Pleiades (m45). */
export const SKY_FIGURES = ['uma', 'umi', 'cas', 'ori', 'tau', 'gem', 'leo', 'cyg', 'lyr', 'aql', 'sco', 'cma', 'cmi', 'boo', 'vir', 'aur', 'peg', 'and', 'per', 'sgr', 'm45'] as const

/** The planets the app draws (game/sky/Planets.kt). */
export const SKY_PLANETS = ['venus', 'mars', 'jupiter', 'saturn'] as const

/** Mirrored from the app's sky catalog (resources/sky/stars.tsv): the named stars, as sky.json ids them. */
export const SKY_STARS = [
  'alpheratz', 'caph', 'algenib', 'schedar', 'navi', 'mirach', 'polaris', 'almach', 'algol', 'mirfak', 'alcyone', 'aldebaran',
  'capella', 'rigel', 'bellatrix', 'elnath', 'mintaka', 'meissa', 'alnilam', 'alnitak', 'saiph', 'betelgeuse', 'menkalinan',
  'mirzam', 'alhena', 'sirius', 'adhara', 'wezen', 'gomeisa', 'castor', 'procyon', 'pollux', 'alphard', 'regulus', 'algieba',
  'merak', 'dubhe', 'denebola', 'phecda', 'megrez', 'alioth', 'mizar', 'spica', 'alkaid', 'arcturus', 'izar', 'kochab',
  'alphecca', 'antares', 'shaula', 'rasalhague', 'eltanin', 'kaus-australis', 'vega', 'nunki', 'albireo', 'tarazed', 'altair',
  'sadr', 'deneb', 'enif', 'fomalhaut', 'scheat', 'markab',
] as const

/** Every id a thing of sky.json may have. */
export const SKY_THINGS: readonly string[] = [...SKY_FIGURES, ...SKY_PLANETS, 'milky-way', ...SKY_STARS]

/** The year's meteor showers the app knows (game/sky/Meteors.kt SHOWERS). */
export const SKY_SHOWERS = ['quadrantids', 'lyrids', 'perseids', 'orionids', 'leonids', 'geminids'] as const

/** The levels lore is told at (the app's Readings.LEVELS). */
export const SKY_LEVELS = ['A1', 'A2', 'B1', 'B2'] as const

const LANG = /^[a-z]{2,3}$/
const str = z.string().trim().min(1)
/** {"sl": "…", "en": "…"}; no message arguments (the app shows these as they are). */
const text = z.record(z.string().regex(LANG, 'a language code (sl, en, it …)'), str.refine(s => !/[{}]/.test(s), 'no braces: a sky text has no arguments'))
  .refine(t => Object.keys(t).length > 0, 'a text in at least one language')
const obj = <T extends z.ZodRawShape>(shape: T) => z.strictObject(shape)
const words = z.array(str).refine(w => new Set(w).size === w.length, 'a word twice').default([])
const lore = obj({ level: z.enum(SKY_LEVELS).default('A1'), text, phase: z.enum(MOON_PHASES).optional() })

export const skySpec = obj({
  schema: schemaField(SKY_SCHEMA),
  language: z.string().regex(LANG),
  review: str.optional(),
  where: obj({ lat: z.number().min(-90).max(90), lon: z.number().min(-180).max(180), name: text.optional() }),
  /** The word pack of the sky's words (the pack's packs/<id>.json): the card's "Learn them". */
  pack: z.string().regex(/^[a-z0-9][a-z0-9-]{1,62}$/).optional(),
  moon: obj({
    name: text,
    phases: obj(Object.fromEntries(MOON_PHASES.map(p => [p, text])) as Record<(typeof MOON_PHASES)[number], typeof text>),
    waxing: text,
    waning: text,
    lore: z.array(lore).default([]),
    reading: z.string().regex(/^[a-z0-9][a-z0-9_-]{0,62}$/).optional(),
    words,
  }),
  things: z.array(obj({
    id: z.string().refine(i => SKY_THINGS.includes(i), 'a thing of the sky the app draws: a constellation (ori), the Pleiades (m45), a planet, milky-way or a named star (polaris)'),
    name: text,
    folk: text.optional(),
    morning: text.optional(),
    evening: text.optional(),
    lore: z.array(lore.refine(l => !l.phase, 'only the moon\'s lore has phases')).default([]),
    words,
  })).refine(t => new Set(t.map(x => x.id)).size === t.length, 'a thing twice').default([]),
  meteors: obj({
    name: text,
    wish: text,
    lore: z.array(lore.refine(l => !l.phase, 'only the moon\'s lore has phases')).default([]),
    words,
    showers: z.array(obj({ id: z.enum(SKY_SHOWERS), name: text, folk: text.optional(), tonight: text }))
      .refine(s => new Set(s.map(x => x.id)).size === s.length, 'a shower twice').default([]),
  }).optional(),
})
export type SkyFile = z.infer<typeof skySpec>

/** Every text of [v] (an object of language codes → strings), with its path. */
function texts(v: unknown, path: string, out: [string, Record<string, string>][] = []) {
  if (Array.isArray(v)) v.forEach((x, i) => texts(x, `${path}[${i}]`, out))
  else if (v && typeof v === 'object') {
    const entries = Object.entries(v)
    if (entries.length && entries.every(([k, x]) => LANG.test(k) && typeof x === 'string')) out.push([path, v as Record<string, string>])
    else for (const [k, x] of entries) texts(x, `${path}.${k}`, out)
  }
  return out
}

/**
 * The problems of pack [cultureId]'s sky.json (none when it has none): its shape, its language the pack's [lang] and in
 * every text, the reading the moon's card opens one of the pack's [readings], and its word pack one of the pack's
 * [packs] (its packs/ directory), when given.
 */
export function cultureSky(culturesDir: string, cultureId: string, lang: string, readings?: Set<string>, packs?: Set<string>): { sky?: SkyFile; errors: string[] } {
  const p = join(culturesDir, cultureId, 'sky.json')
  if (!existsSync(p)) return { errors: [] }
  const where = `${cultureId}/sky.json`
  try {
    const r = skySpec.safeParse(JSON.parse(readFileSync(p, 'utf8')))
    if (!r.success) return { errors: [`${where}:\n${z.prettifyError(r.error)}`] }
    const s = r.data
    const errors: string[] = []
    if (s.language !== lang) errors.push(`${where}: in "${s.language}", not the pack's language "${lang}"`)
    for (const [path, t] of texts(s, 'sky')) if (!(lang in t)) errors.push(`${where}: ${path}: no "${lang}" text (the pack's language)`)
    if (s.moon.reading && readings && !readings.has(s.moon.reading)) errors.push(`${where}: moon.reading: no reading "${s.moon.reading}" in ${cultureId}/readings`)
    if (s.pack && packs && !packs.has(s.pack)) errors.push(`${where}: pack: no word pack "${s.pack}" in ${cultureId}/packs`)
    return { sky: s, errors }
  } catch (e) {
    return { errors: [`${where}: ${(e as Error).message}`] }
  }
}
