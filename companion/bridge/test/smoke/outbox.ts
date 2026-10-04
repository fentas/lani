// Smoke checks: repeated writes (client_id) are applied once.
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { auth, base, channelEvents, check, dataDir } from './harness'

export default async function outbox() {
  // offline outbox: a write repeated with its client_id is applied once and answered with the first result
  const sessions = () => JSON.parse(readFileSync(join(dataDir, 'session-log.json'), 'utf8')).sessions.length
  const reviewOnce = () =>
    fetch(`${base}/reviews`, {
      method: 'POST',
      headers: auth,
      body: JSON.stringify({ client_id: 'outbox-review-0001', results: [{ item_id: 'vocab_hvala', quality: 4 }], duration_minutes: 2 }),
    })
  const before = sessions()
  const [d1, d2] = await Promise.all([reviewOnce(), reviewOnce()]) // a retry racing the original
  const d1Body = await d1.json()
  const d2Body = await d2.json()
  const d3 = await reviewOnce()
  const d3Body = await d3.json()
  check('repeated client_id on /reviews is applied once', sessions() === before + 1, { before, after: sessions() })
  check('repeats get the original result', d1.ok && d2.ok && d3.ok && d1Body.session_id === d2Body.session_id && d3Body.session_id === d1Body.session_id, [d1Body, d2Body, d3Body])
  check('a later repeat is marked as a replay', d3.headers.get('x-lani-replay') === '1')
  const other = await fetch(`${base}/reviews`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ client_id: 'outbox-review-0002', results: [{ item_id: 'vocab_hvala', quality: 4 }], duration_minutes: 2 }),
  })
  check('a new client_id is a new write', other.ok && (await other.json()).session_id !== d1Body.session_id && sessions() === before + 2)
  const badOnce = await fetch(`${base}/reviews`, { method: 'POST', headers: auth, body: JSON.stringify({ client_id: 'outbox-review-bad1', results: [] }) })
  check('a rejected write is not remembered', badOnce.status === 400 && (await fetch(`${base}/reviews`, { method: 'POST', headers: auth, body: JSON.stringify({ client_id: 'outbox-review-bad1', results: [] }) })).status === 400)
  const chatOnce = () => fetch(`${base}/message`, { method: 'POST', headers: auth, body: JSON.stringify({ client_id: 'outbox-chat-0001', kind: 'chat', text: 'enkrat' }) })
  await chatOnce()
  await chatOnce()
  await Bun.sleep(100)
  check('repeated chat message reaches Claude once', channelEvents.filter(n => String(n.params?.content).split('\n\n↩')[0] === 'enkrat').length === 1)
}
