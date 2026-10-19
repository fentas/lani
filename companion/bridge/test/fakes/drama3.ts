// A fake Drama 3 worker (companion/voice-drama3's HTTP contract) for the smoke: voice-build --drama3 starts it as it
// would start the real one (LANI_DRAMA3_CMD="bun test/fakes/drama3.ts"), on LANI_DRAMA3_HOST:LANI_DRAMA3_PORT, and stops
// it with SIGTERM. The real model never runs in tests.
//
//   FAKE_DRAMA3_LOG    a file it appends one JSON line to for each event: {start: {lock, port}}, {synth: {text, speaker}},
//                      {stopped: true}
//   FAKE_DRAMA3_FAIL   texts containing this get 422 "quality" with the checks' reasons of four takes
//   FAKE_DRAMA3_WAIT_MS its /health says it waits for the heavy lock this long first (503 loading)
//   FAKE_DRAMA3_LOW_AFTER  after this many takes it writes 1 GB available into LANI_DRAMA3_MEMINFO (the memory pause),
//                      once (a worker started again after the pause doesn't)
import { appendFileSync, existsSync, writeFileSync } from 'node:fs'
import { tone } from './eleven'

const port = Number(process.env.LANI_DRAMA3_PORT ?? 8785)
const logFile = process.env.FAKE_DRAMA3_LOG
const fail = process.env.FAKE_DRAMA3_FAIL
const readyAt = Date.now() + Number(process.env.FAKE_DRAMA3_WAIT_MS ?? 0)
const lowAfter = Number(process.env.FAKE_DRAMA3_LOW_AFTER ?? 0)
let takes = 0
const note = (o: unknown) => logFile && appendFileSync(logFile, `${JSON.stringify(o)}\n`)
const VOICES = ['female', 'male', 'grandma', 'grandpa', 'young-man', 'gruff', 'woman', 'teacher', 'young-woman', 'girl', 'boy']

note({ start: { lock: process.env.LANI_DRAMA3_LOCK ?? null, port } })
const server = Bun.serve({
  hostname: process.env.LANI_DRAMA3_HOST ?? '127.0.0.1',
  port,
  async fetch(req) {
    const u = new URL(req.url)
    if (u.pathname === '/health') {
      if (Date.now() < readyAt) return Response.json({ ok: false, engine: 'drama3', loading: true, waiting: `the heavy lock (${process.env.LANI_DRAMA3_LOCK})` }, { status: 503 })
      return Response.json({ ok: true, engine: 'drama3', device: 'fake', busy: false, voices: VOICES, built_with: 'Built with Fish Audio' })
    }
    if (u.pathname === '/synth' && req.method === 'POST') {
      if (Date.now() < readyAt) return Response.json({ ok: false, error: 'loading' }, { status: 503 })
      const body = (await req.json()) as { text: string; speaker?: string }
      note({ synth: body })
      if (!body.speaker || !VOICES.includes(body.speaker)) return Response.json({ ok: false, error: `unknown speaker ${body.speaker}` }, { status: 400 })
      if (fail && body.text.includes(fail)) {
        const reasons = ['gender: 104 Hz is not a female voice', 'noise: a hiss bed at -54 dB under the speech', 'text: sva → smo', 'text: sva → smo']
        return Response.json({ ok: false, error: 'quality', voice: body.speaker, text: body.text, attempts: reasons.map((r, i) => ({ seed: 1000 + i, ok: false, reasons: [r], warnings: [], metrics: {} })) }, { status: 422 })
      }
      await Bun.sleep(30)
      const mi = process.env.LANI_DRAMA3_MEMINFO
      if (lowAfter && ++takes === lowAfter && mi && !existsSync(`${mi}.low`)) {
        writeFileSync(`${mi}.low`, '')
        writeFileSync(mi, 'MemTotal: 134217728 kB\nMemAvailable: 1048576 kB\n')
      }
      return new Response(tone(Math.min(3, 0.5 + body.text.length / 30)), {
        headers: { 'content-type': 'audio/mpeg', 'x-voice': `${body.speaker}/${body.speaker}-2`, 'x-audio-seconds': '1.00', 'x-synth-seconds': '0.03', 'x-attempts': '1', 'x-seed': '42', 'x-checks': '{"f0": 210.0, "warnings": []}' },
      })
    }
    return new Response('not found', { status: 404 })
  },
})

const stop = () => {
  note({ stopped: true })
  server.stop(true)
  process.exit(0)
}
process.on('SIGTERM', stop)
process.on('SIGINT', stop)
