// Shared harness for the smoke checks (test/smoke.ts runs the sections in order): a throwaway data
// dir and content dirs, fake voice engines, the bridge started as Claude Code would (MCP client over
// stdio), and check().
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { cpSync, existsSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { sha256 } from '../../src/lexicon-download'
import { CARRIERS, ffmpegCutter } from '../../src/voice'
import { writeCourse } from './course-fixture'
import { fakeEleven, mp3, tone } from '../fakes/eleven'

// Every temp dir goes away when the run ends, pass or fail.
const temps: string[] = []
export const tempDir = (prefix: string) => {
  const d = mkdtempSync(join(tmpdir(), prefix))
  temps.push(d)
  return d
}

/** [name].json (or .json.gz) and its manifest in [dir], as companion/bin/lexicon-build --manifest writes them. */
export function publishLexicon(dir: string, name: string, body: unknown, gzip = false, version?: string) {
  const raw = new TextEncoder().encode(typeof body === 'string' ? body : JSON.stringify(body))
  const bytes = gzip ? Bun.gzipSync(raw) : raw
  const file = `${name}.json${gzip ? '.gz' : ''}`
  writeFileSync(join(dir, file), bytes)
  const [language, baseLang] = name.split('.')
  const digest = sha256(bytes)
  const manifest = { schema: 'lani.lexicon/v0', language, ...(baseLang ? { base: baseLang } : {}), version: version ?? `smoke-${digest.slice(0, 8)}`, file, sha256: digest, size: bytes.length }
  writeFileSync(join(dir, `${name}.manifest.json`), JSON.stringify(manifest))
  return manifest
}
process.on('exit', () => {
  for (const d of temps) rmSync(d, { recursive: true, force: true })
})

export const dataDir = tempDir('lani-smoke-')
// The learner databases start from the empty templates (so /reviews can run update-db.py): the checks
// never depend on, or copy, the real learner data.
const templates = resolve(import.meta.dir, '../../../../data-examples')
for (const f of readdirSync(templates).filter(f => f.endsWith('-template.json'))) cpSync(join(templates, f), join(dataDir, f.replace('-template', '')))
// The smoke's learner is Jan, a man, whom the curated content was written to: what the bridge serves reads as it did
// (test/smoke/learner.ts says it to a woman too).
{
  const profile = join(dataDir, 'learner-profile.json')
  const p = JSON.parse(readFileSync(profile, 'utf8'))
  writeFileSync(profile, JSON.stringify({ ...p, learner: { ...p.learner, name: 'Jan', gender: 'male' } }, null, 2))
}
// A .gitignore of the learner's own, without /app/dev/: the bridge adds the line at its start (test/smoke/dev.ts).
writeFileSync(join(dataDir, '.gitignore'), '# the smoke learner\n/app/bridge-token\n')
// Curated packs come from a temp dir too, so the repo's companion/packs don't affect the checks.
export const curatedDir = tempDir('lani-smoke-packs-')
// Curated scenes too (test/smoke/scenes.ts writes them; the bridge reads them per request).
export const scenesDir = tempDir('lani-smoke-scenes-')
// Curated modules must exist before the bridge starts (they are loaded once).
export const modulesDir = tempDir('lani-smoke-modules-')
writeFileSync(join(modulesDir, 'curated-demo.json'), JSON.stringify({
  schema: 'lani.module/v0', id: 'curated-demo', title: 'Curated demo', level: 'A1',
  exercises: [
    { type: 'choice', prompt: 'Which is polite?', options: ['Živjo', 'Dober dan'], answer: 1 },
    { type: 'cloze', text: 'Jaz ___ Jan.', accept: ['sem'] },
  ],
}))
// The school's course: a small Slovene one (course-fixture.ts) in a temp root, so the repo's companion/course doesn't
// affect the checks (test/smoke/course.ts validates the repo's files on their own).
export const courseRoot = tempDir('lani-smoke-course-')
writeCourse(courseRoot)
// The culture packs are the repo's (companion/cultures): the bridge serves the default one's cast (primorska/villagers).
export const culturesDir = resolve(import.meta.dir, '../../../cultures')
// The phone's offline voice (offline-voice.ts): a fake Hugging Face as a local directory, the cache and the catalog,
// which test/smoke/offline-voice.ts writes (the bridge reads the catalog when it is asked). Never Hugging Face itself.
const piperDir = tempDir('lani-smoke-piper-')
export const piper = { source: join(piperDir, 'source'), cache: join(piperDir, 'cache'), catalog: join(piperDir, 'catalog.json') }
export const port = Number(process.env.LANI_SMOKE_PORT ?? 8799)
export const token = 'smoke-token'
export const familyToken = 'family-smoke-token'
export const base = `http://127.0.0.1:${port}`
export const auth = { authorization: `Bearer ${token}`, 'content-type': 'application/json' }

// Fake voice engines: ElevenLabs (subscription + text-to-speech: test/fakes/eleven.ts, which the dev bridge uses too) and
// the local Gepard worker. The real API is never called; the key is a dummy.
export { fakeEleven, mp3, tone }
export function fakeGepard() {
  // [audio]: what it says (a real MP3, the worker's silences and all); else a tag of the text, which ffmpeg can't read
  const st = { up: true, busy: false, calls: [] as any[], audio: undefined as Uint8Array<ArrayBuffer> | undefined }
  const server = Bun.serve({
    hostname: '127.0.0.1',
    port: 0,
    async fetch(req) {
      const u = new URL(req.url)
      if (u.pathname === '/health') return st.up ? Response.json({ ok: true, engine: 'gepard' }) : new Response('down', { status: 500 })
      if (u.pathname === '/synth' && req.method === 'POST') {
        const body = await req.json()
        st.calls.push(body)
        if (st.busy || !st.up) return new Response('busy', { status: 503 })
        return new Response(st.audio ?? mp3(`gp:${body.text}`), { headers: { 'content-type': 'audio/mpeg' } })
      }
      return new Response('not found', { status: 404 })
    },
  })
  return { st, url: `http://127.0.0.1:${server.port}` }
}
// Fake Whisper worker (companion/stt-local): the real model never runs in tests.
export function fakeWhisper() {
  const st = {
    mode: 'ok' as 'ok' | 'busy' | 'slow' | 'error' | 'junk',
    calls: [] as { query: Record<string, string>; bytes: Uint8Array; type: string | null }[],
    // the languages it has a model for (as the node: Slovene, and the general model's Italian, German and English);
    // legacy: a worker from before languages (one model, no "language" in /health)
    languages: ['sl', 'it', 'de', 'en'],
    legacy: false,
    health: [] as Record<string, string>[],
  }
  const server = Bun.serve({
    hostname: '127.0.0.1',
    port: 0,
    async fetch(req) {
      const u = new URL(req.url)
      const language = u.searchParams.get('language')
      if (u.pathname === '/health') {
        st.health.push(Object.fromEntries(u.searchParams))
        const own = language && !st.legacy ? { language: { code: language, model: 'large-v3-turbo', available: st.languages.includes(language), loaded: language === 'sl' } } : {}
        return Response.json({ ok: true, engine: 'whisper', model: 'large-v3-turbo', device: 'fake', busy: false, ...own })
      }
      if (u.pathname === '/transcribe' && req.method === 'POST') {
        st.calls.push({ query: Object.fromEntries(u.searchParams), bytes: new Uint8Array(await req.arrayBuffer()), type: req.headers.get('content-type') })
        if (!st.legacy && language && !st.languages.includes(language)) return Response.json({ ok: false, error: 'not available', language, model: 'large-v3-turbo' }, { status: 503 })
        if (st.mode === 'busy') return Response.json({ ok: false, error: 'busy' }, { status: 503, headers: { 'retry-after': '5' } })
        if (st.mode === 'slow') await Bun.sleep(1_500)
        if (st.mode === 'error') return Response.json({ ok: false, error: 'boom' }, { status: 500 })
        if (st.mode === 'junk') return Response.json({ text: 'Hvala.', segments: [{ start: 0, end: 1, text: 'Hvala.', secret: 'x', words: [{ word: ' Hvala.', prob: 'high' }, { word: ' ' }] }], language: 'sl', debug: '/tmp/x' })
        return Response.json({
          text: 'Na vrtu je čebelniak.',
          segments: [{ start: 0, end: 2.1, text: 'Na vrtu je čebelniak.', avg_logprob: -0.3, no_speech_prob: 0.01, words: [
            { word: 'Na', start: 0, end: 0.2, prob: 0.99 }, { word: 'vrtu', start: 0.2, end: 0.6, prob: 0.97 },
            { word: 'je', start: 0.6, end: 0.8, prob: 0.98 }, { word: 'čebelniak.', start: 0.8, end: 2.1, prob: 0.21 }] }],
          language: 'sl', duration: 2.4, took: 0.25, model: 'large-v3-turbo',
        })
      }
      return new Response('not found', { status: 404 })
    },
  })
  return { st, url: `http://127.0.0.1:${server.port}` }
}
export const bridgeEleven = fakeEleven()
export const bridgeGepard = fakeGepard()
export const bridgeWhisper = fakeWhisper()

let failures = 0
export const check = (name: string, cond: unknown, detail?: unknown) => {
  console.log(`${cond ? '✅' : '❌'} ${name}`, cond ? '' : (detail ?? ''))
  if (!cond) failures++
}

/**
 * A culture village's voices, as its bridge speaks them (the pack's voice-cast.json, the fake ElevenLabs): a line in
 * the grandmother's voice goes to the ElevenLabs model (eleven_v4_turbo) in the pack's voice with the village's language_code, and a narrator's lone
 * word inside the language's carrier sentence when the cast marks that narrator `carrier` (with ffmpeg for the cut),
 * else plainly. A cast without voices yet sends nothing and makes no clip (204): the phone's text-to-speech speaks.
 * [since]: the fake's requests before the village's bridge started; [role]: whom the grandmother voices.
 */
export async function checkCultureVoices(o: { culture: string; base: string; headers: Record<string, string>; line: string; word: string; role: RegExp; since: number }) {
  const speakers = JSON.parse(readFileSync(join(culturesDir, o.culture, 'voice-cast.json'), 'utf8')).speakers
  const language = JSON.parse(readFileSync(join(culturesDir, o.culture, 'culture.json'), 'utf8')).language
  const say = (text: string, voice: string) => fetch(`${o.base}/voice/say`, { method: 'POST', headers: o.headers, body: JSON.stringify({ text, voice }) })
  const sent = (text: string) => bridgeEleven.st.tts.slice(o.since).filter(t => t.body.text === text)
  const line = await say(o.line, 'grandma')
  const st = await (await fetch(`${o.base}/voice/status`, { headers: o.headers })).json()
  const theirs = st.cast.some((s: any) => s.id === 'grandma' && o.role.test(s.role ?? ''))
  if (!speakers.grandma.elevenlabs) {
    check(`${o.culture}: the ${o.culture} voice cast has no voices yet: no clip (204), nothing sent to ElevenLabs, the cast is the pack's`, line.status === 204 && bridgeEleven.st.tts.length === o.since && theirs, { status: line.status, cast: st.cast })
    return
  }
  const asked = sent(o.line)
  check(`${o.culture}: the grandmother speaks in the pack's voice: a clip from eleven_v4_turbo with language_code ${language}`, line.ok && asked.length === 1 && asked[0].voice === speakers.grandma.elevenlabs && asked[0].body.model_id === 'eleven_v4_turbo' && asked[0].body.language_code === language && theirs, { status: line.status, asked })
  const narrator = ['female', 'male'].find(n => speakers[n].carrier) ?? 'female'
  const carrier = !!speakers[narrator].carrier && !!ffmpegCutter()
  const word = await say(o.word, narrator)
  const inCarrier = sent(CARRIERS[language].replace('{w}', o.word))
  const plain = sent(o.word)
  check(
    `${o.culture}: a lone word of the narrator ${narrator} ${carrier ? `inside "${CARRIERS[language]}"` : 'plainly'}, in the pack's voice with language_code ${language}`,
    word.ok && (carrier ? inCarrier.length === 1 && !!inCarrier[0].timestamps && !plain.length : plain.length === 1 && !inCarrier.length) && [...inCarrier, ...plain].every(t => t.voice === speakers[narrator].elevenlabs && t.body.language_code === language),
    { status: word.status, inCarrier, plain },
  )
}

export const channelEvents: any[] = []
export const client = new Client({ name: 'smoke', version: '0' })
client.fallbackNotificationHandler = async n => void channelEvents.push(n)
await client.connect(
  new StdioClientTransport({
    command: 'bun',
    args: [resolve(import.meta.dir, '../../src/index.ts')],
    env: { ...process.env, LANI_DATA_DIR: dataDir, LANI_BRIDGE_PORT: String(port), LANI_BRIDGE_TOKEN: token, LANI_PACKS_DIR: curatedDir, LANI_MODULES_DIR: modulesDir, LANI_RHYTHM: 'off', LANI_SCENARIOS_DIR: resolve(import.meta.dir, '../../../scenarios'), LANI_GRAMMAR_DIR: resolve(import.meta.dir, '../../../grammar'), LANI_COURSE_DIR: courseRoot, LANI_DRILLS_DIR: resolve(import.meta.dir, '../../../drills'), LANI_SCENES_DIR: scenesDir, LANI_CULTURES_DIR: culturesDir, LANI_FAMILY_TOKEN: familyToken, ELEVENLABS_API_KEY: 'test-key', ELEVENLABS_BASE_URL: bridgeEleven.url, LANI_GEPARD_URL: bridgeGepard.url, LANI_VOICE_QUEUE_DELAY_MS: '0', LANI_STT_URL: bridgeWhisper.url, LANI_STT_TIMEOUT_MS: '800', LANI_WORDS_NOTE_MS: '1000', LANI_GLOSS_GAP_MS: '300', LANI_TOWN_FRIEND_CACHE_MS: '0', LANI_PIPER_URL: piper.source, LANI_PIPER_CACHE: piper.cache, LANI_PIPER_CATALOG: piper.catalog } as Record<string, string>,
    stderr: 'inherit',
  }),
)
await Bun.sleep(300)
/** The channel's whole guide, the channel_guide tool: Claude Code shows only the first 2048 characters of the instructions. */
export async function guideOf(c: Client = client): Promise<string> {
  const r: any = await c.callTool({ name: 'channel_guide', arguments: {} })
  return r?.content?.[0]?.text ?? ''
}
export const fam = { authorization: `Bearer ${familyToken}` }

/**
 * A village in another language: its bridge serves that language's grammar book (companion/grammar/<lang>, every page
 * explained in the learner's [learnerBase]), and the guide says whose book it is and how many curated pages it has.
 */
export async function languageBook(village: string, url: string, headers: Record<string, string>, lang: string, name: string, chapter: string, learnerBase: string, guide: string) {
  const dir = resolve(import.meta.dir, '../../../grammar', lang)
  const curated = existsSync(dir) ? readdirSync(dir).filter(f => f.endsWith('.json')).length : 0
  const book = (await (await fetch(`${url}/grammar`, { headers })).json()) as any[]
  check(
    `${village}: GET /grammar serves the ${name} book, its ${curated} curated pages, each explained in ${learnerBase}`,
    Array.isArray(book) && book.length === curated && book.every(p => p.language === lang && p.source === 'curated' && p.title?.[lang] && p.rule?.[learnerBase] && p.examples.every((e: any) => e[lang] && e[learnerBase])),
    book.map?.(p => [p.id, p.language, Object.keys(p.rule ?? {})]),
  )
  check(
    `${village}: the guide says the grammar book is ${name} ("📖 ${chapter}") and has ${curated} curated pages`,
    guide.includes(`grammar book is in ${name}`) && guide.includes(`📖 ${chapter}`) && guide.includes(curated ? `${curated} curated pages` : 'no curated pages yet'),
    guide.slice(guide.indexOf("This village's grammar book"), guide.indexOf("This village's grammar book") + 300),
  )
}

/** A request whose body is streamed without a content-length (chunked): its size is only known by reading it. */
export async function sendChunked(url: string, method: string, headers: Record<string, string>, size: number, first = new Uint8Array()) {
  const chunk = 64 * 1024
  let sent = 0
  const body = new ReadableStream<Uint8Array>({
    pull(c) {
      if (sent >= size) return c.close()
      const n = Math.min(chunk, size - sent)
      const b = new Uint8Array(n).fill(0x20)
      if (sent === 0) b.set(first.subarray(0, n))
      sent += n
      c.enqueue(b)
    },
  })
  try {
    // Its own connection: the server answers before reading the rest, and the unread rest must not
    // end up in front of the next request on a pooled connection.
    const r = await fetch(url, { method, headers, body, duplex: 'half', keepalive: false } as RequestInit)
    return { status: r.status, text: await r.text() }
  } catch (e) {
    return { status: 0, text: (e as Error).message } // the server hung up while we were still sending
  }
}

/** Closes the bridge and exits with the result. */
export async function done() {
  // The SDK ends stdin, waits 2 s, then sends SIGTERM: a bridge that exits on its own is well under that.
  const closing = Date.now()
  await client.close()
  check('the bridge exits when Claude Code closes the channel', Date.now() - closing < 1500, Date.now() - closing)
  console.log(failures ? `\n${failures} check(s) failed` : '\nall checks passed')
  process.exit(failures ? 1 : 0)
}
