// The culture pack the learner's village is in (companion/cultures/<id>, cultures.ts). The app bundles every pack and
// plays the one this says (GET /culture), caching the id so the village reads the same offline; the bridge serves
// that pack's cast (GET /villagers). GET /cultures lists the packs there are. GET /culture also says which landscape a
// new village is offered first, when the profile has one (LANI_LANDSCAPE).
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import type { FeatureFactory } from '../feature'
import { json } from '../http'
import { cultureDir, cultureIds, readManifest } from '../cultures'

export const culture: FeatureFactory = ({ culture, log, profile }) => {
  /** The places the pack's texts name (world.json), for the tutor and whoever writes for it. */
  function places(id: string): unknown[] {
    const p = join(cultureDir(culture.dir, id), 'world.json')
    if (!existsSync(p)) return []
    try {
      return JSON.parse(readFileSync(p, 'utf8')).places ?? []
    } catch (e) {
      log(`culture ${id}: world.json: ${(e as Error).message}`)
      return []
    }
  }

  return {
    routes: [
      {
        method: 'GET',
        path: '/culture',
        handle: () => json({
          id: culture.id,
          ...(culture.wanted !== culture.id ? { wanted: culture.wanted, why: culture.why } : {}),
          ...(culture.manifest ? { language: culture.manifest.language, status: culture.manifest.status, region: culture.manifest.region, name: culture.manifest.name } : {}),
          // the landscape a new village is offered first (lani-profile add --landscape): the app founds it where the learner chooses
          ...(profile.landscape ? { landscape: profile.landscape } : {}),
          places: places(culture.id),
        }),
      },
      {
        method: 'GET',
        path: '/cultures',
        handle: () => json(cultureIds(culture.dir).flatMap(id => {
          const m = readManifest(culture.dir, id)
          return m.ok ? [{ id, language: m.manifest.language, status: m.manifest.status, region: m.manifest.region, name: m.manifest.name }] : []
        })),
      },
    ],
  }
}
