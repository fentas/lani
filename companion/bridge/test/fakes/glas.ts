// A fake voice service for the smoke: enough of its API for the bridge's backend (src/glas.ts), and the failures the real one can't
// be asked for: resolve, say, the library, /v1/usage.
import { cast, elevenlabsOf } from '../../src/cast'
import { normalizeText } from '../../src/family'

export type FakeGlasState = {
  /** normalized text|speaker → the clip's bytes; asked for and absent: made when the key may (see [generate]). */
  clips: Map<string, Uint8Array>
  /** How a clip in the fake came to be: "import" from a node's store, else ElevenLabs. */
  imported: Set<string>
  generate: boolean
  /** What every request answers with instead, when set: 500, 502; 'hang' never answers. */
  fail?: number | 'hang'
  /** Answered to the next items instead of a clip. */
  refuse?: { code: string }
  /** speaker → another voice id than the cast's. */
  voices: Record<string, string>
  /** The names the fake's library has. */
  names: Set<string>
  calls: { path: string; body?: Record<string, unknown>; items?: Record<string, unknown>[] }[]
  downloads: number
}

export function fakeGlas(o: { bytes: (text: string) => Uint8Array }) {
  const st: FakeGlasState = { clips: new Map(), imported: new Set(), generate: true, voices: {}, names: new Set(), calls: [], downloads: 0 }
  const of = (i: Record<string, unknown>) => `${normalizeText(String(i.text))}|${i.voice}`
  const result = (url: string, i: Record<string, unknown>) => {
    if (st.refuse) return { status: 'rejected', error: { code: st.refuse.code, message: 'refused' } }
    const k = of(i)
    if (!st.clips.has(k)) {
      if (!st.generate || i.make === false) return { status: 'missing' }
      st.clips.set(k, o.bytes(String(i.text)))
    }
    return { status: 'ready', url: `${url}/file/${encodeURIComponent(k)}`, clip: { hash: 'h', namespace: 'shared', voice: String(i.voice), engine: st.imported.has(k) ? 'import' : 'elevenlabs', model: 'eleven_v4_turbo' } }
  }
  const server = Bun.serve({
    hostname: '127.0.0.1',
    port: 0,
    async fetch(req) {
      const u = new URL(req.url)
      const url = `http://127.0.0.1:${server.port}`
      const body = req.method === 'POST' ? ((await req.json()) as Record<string, unknown>) : undefined
      st.calls.push({ path: u.pathname, body, items: body?.items as Record<string, unknown>[] | undefined })
      if (st.fail === 'hang') await Bun.sleep(30_000)
      if (typeof st.fail === 'number') return Response.json({ error: { code: 'x', message: 'x' } }, { status: st.fail })
      if (u.pathname.startsWith('/file/')) {
        st.downloads++
        const b = st.clips.get(decodeURIComponent(u.pathname.slice('/file/'.length)))
        return b ? new Response(b as unknown as BodyInit) : new Response(null, { status: 404 })
      }
      if (req.headers.get('authorization') !== 'Bearer glas_test') return Response.json({ error: { code: 'unauthorized', message: 'no' } }, { status: 401 })
      if (u.pathname === '/v1/voices') {
        const ids = Object.keys(cast()).flatMap(s => [{ id: s, provider_id: st.voices[s] ?? elevenlabsOf(`${s}.sl`) ?? elevenlabsOf(s) ?? '' }])
        return Response.json({ voices: ids })
      }
      if (u.pathname === '/v1/characters') return Response.json({ characters: Object.keys(cast()).map(s => ({ archetype: s, voice: s })) })
      if (u.pathname === '/v1/usage') return Response.json({ key: { scopes: ['read-shared', ...(st.generate ? ['generate'] : [])] } })
      if (u.pathname === '/v1/clips/resolve') return Response.json({ items: (body!.items as Record<string, unknown>[]).map(i => result(url, i)) })
      if (u.pathname === '/v1/clips/say') return Response.json(result(url, body!))
      if (u.pathname === '/v1/names/resolve') return Response.json({ items: (body!.items as { name: string }[]).map((i, index) => ({ index, status: st.names.has(i.name) ? 'ready' : 'missing' })) })
      if (u.pathname === '/v1/names') {
        st.names.add(String(body!.name))
        return Response.json({ status: 'added' })
      }
      return new Response(null, { status: 404 })
    },
  })
  return { st, url: `http://127.0.0.1:${server.port}`, stop: () => server.stop(true) }
}
