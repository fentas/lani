// A fake ElevenLabs (subscription, text-to-speech, /with-timestamps, Voice Design, voices): the smoke checks
// (test/smoke/harness.ts) and the dev bridge (test/dev-bridge.ts, LANI_DEV_FAKE_ELEVENLABS=1) talk to it, never to the
// real API; its key is "test-key". Like the real one it can take its time ([st.delayMs], [st.msPerChar]) and refuse what
// goes over its plan's concurrency ([st.concurrency]: HTTP 429 "too_many_concurrent_requests", counted in [st.refused]).
import { existsSync } from 'node:fs'

/** A tagged "MP3" (an ID3 header, then [tag]): enough for the bridge's check, not for a player. */
export const mp3 = (tag: string) => new Uint8Array([0x49, 0x44, 0x33, 4, 0, 0, ...new TextEncoder().encode(tag)])

const ffmpegBin = Bun.which('ffmpeg') ?? (existsSync('/usr/bin/ffmpeg') ? '/usr/bin/ffmpeg' : undefined)
const tones = new Map<number, Uint8Array>()

/** A real MP3 of [secs] seconds that ffmpeg can cut: a tone (with ffmpeg), else silent MPEG frames (128 kbit/s, 26 ms each). */
export function tone(secs: number): Uint8Array {
  const s = Math.round(secs * 100) / 100
  let b = tones.get(s)
  if (b) return b
  const p = ffmpegBin && Bun.spawnSync([ffmpegBin, '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i', `sine=frequency=440:duration=${s}`, '-ac', '1', '-c:a', 'libmp3lame', '-b:a', '64k', '-f', 'mp3', 'pipe:1'])
  if (p && p.exitCode === 0) b = new Uint8Array(p.stdout)
  else {
    const frames = Math.ceil(s / (1152 / 44100))
    b = new Uint8Array(frames * 417)
    for (let f = 0; f < frames; f++) b.set([0xff, 0xfb, 0x90, 0x64], f * 417)
  }
  tones.set(s, b)
  return b
}

/**
 * The fake. [o.tones]: a plain take is a real MP3 (a tone, longer for a longer text, at most 6 s), so a phone plays it
 * (the dev bridge); else a tagged one ([mp3]: "el:<voice id>:<text>"), which the smoke checks read back. [o.port]: its
 * port (0: any free one).
 */
export function fakeEleven(o: { tones?: boolean; port?: number } = {}) {
  const st = {
    used: 0, limit: 100_000, subscriptionCalls: 0, fail: 0, echoKey: false, tts: [] as { voice: string; query: string; body: any; timestamps?: boolean }[],
    // /with-timestamps: the takes it gave, and its alignment ('other': the timestamps of something else said)
    takes: [] as Uint8Array[], align: 'ok' as 'ok' | 'other',
    // voices of one's own: slots, monthly edits, Voice Design calls, saved and deleted voices
    voiceLimit: 10, voiceSlotsUsed: 0, edits: 0, editsMax: 65, designs: [] as any[], created: [] as any[], deleted: [] as string[],
    // as the real service: how long a take takes (a request, and per character of its text), the plan's concurrency (0: no
    // limit) and what went over it (refused with a 429), the requests at work now and the most at once
    delayMs: 0, msPerChar: 0, concurrency: 0, refused: 0, inFlight: 0, maxInFlight: 0,
    // the next [busy] text-to-speech requests refused as over the concurrency whatever is at work (another process's)
    busy: 0,
  }
  /** A text-to-speech request at work for its time, within the plan's concurrency: null when it may go on, else the 429. */
  const admit = (): Response | null => {
    if (st.busy > 0 || (st.concurrency > 0 && st.inFlight >= st.concurrency)) {
      if (st.busy > 0) st.busy--
      st.refused++
      return Response.json({ detail: { status: 'too_many_concurrent_requests', message: 'Too many concurrent requests. Your plan allows a few at a time.' } }, { status: 429 })
    }
    st.inFlight++
    st.maxInFlight = Math.max(st.maxInFlight, st.inFlight)
    return null
  }
  const work = async (text: string) => {
    const ms = st.delayMs + st.msPerChar * text.length
    if (ms > 0) await Bun.sleep(ms)
  }
  const server = Bun.serve({
    hostname: '127.0.0.1',
    port: o.port ?? 0,
    async fetch(req) {
      const u = new URL(req.url)
      // voice samples come from a public CDN, without the key
      if (u.pathname.startsWith('/preview/')) return new Response(new Uint8Array(4_000).fill(7), { headers: { 'content-type': 'audio/mpeg' } })
      if (req.headers.get('xi-api-key') !== 'test-key') return new Response('invalid key', { status: 401 })
      if (u.pathname === '/v1/user/subscription') {
        st.subscriptionCalls++
        return Response.json({
          tier: 'free', character_count: st.used, character_limit: st.limit, next_character_count_reset_unix: 1_790_000_000,
          voice_limit: st.voiceLimit, voice_slots_used: st.voiceSlotsUsed, voice_add_edit_counter: st.edits, max_voice_add_edits: st.editsMax,
        })
      }
      if (u.pathname === '/v1/text-to-voice/design' && req.method === 'POST') {
        const body = await req.json()
        st.designs.push(body)
        st.used += body.text.length
        const n = st.designs.length
        return Response.json({ text: body.text, previews: [0, 1, 2].map(i => ({ generated_voice_id: `gen${n}x${i}`, audio_base_64: Buffer.from(mp3(`take:${i}:${body.text}`)).toString('base64'), duration_secs: 3, language: 'sl', media_type: 'audio/mpeg' })) })
      }
      if (u.pathname === '/v1/text-to-voice' && req.method === 'POST') {
        const body = await req.json()
        if (st.voiceSlotsUsed >= st.voiceLimit) return Response.json({ detail: 'voice limit reached' }, { status: 400 })
        st.created.push(body)
        st.voiceSlotsUsed++
        st.edits++
        return Response.json({ voice_id: `own${st.created.length}`, name: body.voice_name })
      }
      const voice = /^\/v1\/voices\/(\w+)$/.exec(u.pathname)
      if (voice && req.method === 'GET') return Response.json({ voice_id: voice[1], preview_url: `http://127.0.0.1:${server.port}/preview/${voice[1]}.mp3` })
      const del = /^\/v1\/voices\/(\w+)$/.exec(u.pathname)
      if (del && req.method === 'DELETE') {
        st.deleted.push(del[1])
        st.voiceSlotsUsed = Math.max(0, st.voiceSlotsUsed - 1)
        return Response.json({ status: 'ok' })
      }
      const ts = /^\/v1\/text-to-speech\/(\w+)\/with-timestamps$/.exec(u.pathname)
      if (ts && req.method === 'POST') {
        const body = await req.json()
        st.tts.push({ voice: ts[1], query: u.search, body, timestamps: true })
        if (st.fail) return new Response('{"detail":"nope"}', { status: st.fail })
        const over = admit()
        if (over) return over
        try {
          await work(body.text)
        } finally {
          st.inFlight--
        }
        st.used += body.text.length
        // 0.2 s of breath, then 70 ms a character, then 0.4 s
        const chars = [...(st.align === 'other' ? 'Nekaj drugega.' : body.text)] as string[]
        const starts = chars.map((_, i) => Math.round((0.2 + i * 0.07) * 1000) / 1000)
        const alignment = { characters: chars, character_start_times_seconds: starts, character_end_times_seconds: starts.map(s => Math.round((s + 0.07) * 1000) / 1000) }
        const audio = tone(0.2 + chars.length * 0.07 + 0.4)
        st.takes.push(audio)
        return Response.json({ audio_base64: Buffer.from(audio).toString('base64'), alignment, normalized_alignment: alignment })
      }
      const m = /^\/v1\/text-to-speech\/(\w+)$/.exec(u.pathname)
      if (m && req.method === 'POST') {
        const body = await req.json()
        st.tts.push({ voice: m[1], query: u.search, body })
        if (st.fail) return new Response(st.echoKey ? `{"detail":"bad key ${req.headers.get('xi-api-key')}"}` : '{"detail":"nope"}', { status: st.fail })
        const over = admit()
        if (over) return over
        try {
          await work(body.text)
        } finally {
          st.inFlight--
        }
        st.used += body.text.length
        // a tone of about a second for a line, at most six for an explanation (shorter than said: QA waits for what's next)
        const audio = o.tones ? tone(Math.min(6, 0.4 + body.text.length / 30)) : mp3(`el:${m[1]}:${body.text}`)
        return new Response(audio, { headers: { 'content-type': 'audio/mpeg' } })
      }
      return new Response('not found', { status: 404 })
    },
  })
  return { st, url: `http://127.0.0.1:${server.port}`, stop: () => server.stop(true) }
}
