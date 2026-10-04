// The app's inbox: the SSE stream, and the buffered events for background checks.
import type { FeatureFactory } from '../feature'
import { json } from '../http'

export const events: FeatureFactory = ({ events }) => ({
  routes: [
    { method: 'GET', path: '/events', handle: ({ req }) => events.stream(req) },
    // For background checks: the buffered events after `since`, without holding a stream open.
    { method: 'GET', path: '/events/backlog', handle: ({ url }) => json(events.backlog(Number(url.searchParams.get('since') ?? 0))) },
  ],
})
