// Smoke checks: the SSE stream replays what the tutor sent.
import { Events } from '../../src/events'
import { auth, base, check, client } from './harness'

export default async function events() {
  await client.callTool({ name: 'reply', arguments: { conversation_id: 'c1', text: '✅ 10/10', data: { score: 10 } } })

  const res = await fetch(`${base}/events`, { headers: auth })
  const reader = res.body!.getReader()
  let buf = ''
  const deadline = Date.now() + 2000
  while (!buf.includes('"reply"') && Date.now() < deadline) buf += new TextDecoder().decode((await reader.read()).value)
  reader.cancel()
  check('SSE replays module_published', buf.includes('"module_published"'))
  check('SSE replays reply', buf.includes('"reply"') && buf.includes('"score":10'))

  const reply = (args: object) => client.callTool({ name: 'reply', arguments: args })
  check('reply refuses a missing or odd conversation id', (await reply({ conversation_id: '../x', text: 'x' })).isError && (await reply({ text: 'x' })).isError)
  check('reply refuses data that is not an object, and text that is not a string', (await reply({ conversation_id: 'c1', text: 'x', data: 'score 9' })).isError && (await reply({ conversation_id: 'c1', text: { t: 1 } })).isError)
  // A client that drops its stream is forgotten, and one that stops reading is dropped. In-process,
  // because Bun's fetch keeps the socket of a cancelled body open, so the bridge still sees the
  // earlier stream as connected.
  {
    const ev = new Events()
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, idleTimeout: 0, fetch: req => ev.stream(req) })
    const ctl = new AbortController()
    const res = await fetch(`http://127.0.0.1:${server.port}/events`, { signal: ctl.signal })
    await res.body!.getReader().read()
    const wasConnected = ev.connected
    ctl.abort()
    await Bun.sleep(100)
    ev.emit({ type: 'after' })
    check('a dropped SSE stream is cleaned up', wasConnected && !ev.connected)
    const stalled = await fetch(`http://127.0.0.1:${server.port}/events`)
    await stalled.body!.getReader().read() // then never reads again
    for (let i = 0; i < 20; i++) ev.emit({ type: 'big', text: 'x'.repeat(1_000_000) })
    check('a client that stops reading is dropped once 16 MB wait for it', !ev.connected)
    server.stop(true)
  }
  const after = await reply({ conversation_id: 'c1', text: 'after the stream checks' })
  check('the bridge keeps delivering after a client dropped its stream', !after.isError && (await fetch(`${base}/health`)).ok, after)
}
