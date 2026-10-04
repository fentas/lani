// The car's audio drills (lani.drill/v0, drills.ts): the curated ones of the village's language, for the app to get
// the road ready with (companion/README.md, "Im Auto · In the car"). The app bundles them too: an older bridge without
// this route answers 404 and the app plays its own copy.
import type { FeatureFactory } from '../feature'
import { json } from '../http'

export const drills: FeatureFactory = ({ drills: store }) => ({
  routes: [
    { method: 'GET', path: '/drills', handle: () => json(store.all()) },
    {
      method: 'GET',
      path: /^\/drills\/([a-z0-9-]+)$/,
      handle: ({ params: [id] }) => {
        const d = store.get(id)
        return d ? json(d) : json({ error: 'not found' }, 404)
      },
    },
  ],
})
