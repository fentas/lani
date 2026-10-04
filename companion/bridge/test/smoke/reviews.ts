// Smoke checks: flashcard reviews persisted via update-db.py.
import { auth, base, channelEvents, check } from './harness'

export default async function reviews() {
  const rv = await fetch(`${base}/reviews`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ results: [{ item_id: 'vocab_hvala', quality: 5 }, { item_id: 'vocab_zivjo', quality: 2 }], duration_minutes: 3 }),
  })
  const rvBody = await rv.json()
  check('POST /reviews persists via update-db.py', rv.ok && rvBody.correct === 1 && rvBody.total === 2, rvBody)
  await Bun.sleep(100)
  check('review_done reaches Claude', channelEvents.some(n => n.params?.meta?.kind === 'review_done'))
}
