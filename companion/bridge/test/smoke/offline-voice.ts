// Smoke checks: the phone's offline voice (offline-voice.ts, features/offline-voice.ts). A fake Hugging Face (a local
// directory of its layout, LANI_PIPER_URL) has a Slovene voice whose pins are right and an Italian one whose model pin is
// wrong (LANI_PIPER_CATALOG). POST /voice/offline/sl fetches the Slovene one into the cache, converted as sherpa-onnx
// needs it (the model with its metadata, tokens.txt, the espeak-ng files), and its files are served as listed; the
// Italian one fails, serves nothing, and the next ask tries again without fetching what it had checked.
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { sha256 } from '../../src/lexicon-download'
import { appendOnnxMetadata, catalogFrom, OfflineVoices, onnxMetadata, piperConfig, tokensTxt, TOKENS_ESTIMATE, type PiperCatalog } from '../../src/offline-voice'
import { auth, base, check, fam, piper, tempDir } from './harness'

const enc = new TextEncoder()
const REV = 'c0ffee'.padEnd(40, '0')
const ESPEAK_REV = 'e5'.padEnd(40, '0')
const ESPEAK_REPO = 'csukuangfj/vits-piper-smoke'
/** Some bytes that look like nothing in particular. */
const bytesOf = (n: number, seed: number) => Uint8Array.from({ length: n }, (_, i) => (i * 31 + seed * 7 + (i >> 8)) & 0xff)
const asBytes = (d: string | Uint8Array) => (typeof d === 'string' ? enc.encode(d) : d)

/** [data] at <source>/<repo>/resolve/<revision>/<path>, as Hugging Face serves it; its pin. */
function put(repo: string, revision: string, path: string, data: string | Uint8Array) {
  const b = asBytes(data)
  const p = join(piper.source, repo, 'resolve', revision, path)
  mkdirSync(dirname(p), { recursive: true })
  writeFileSync(p, b)
  return { bytes: b.length, sha256: sha256(b) }
}

/** A Piper config as rhasspy/piper-voices has them (what the conversion reads, and some it doesn't). */
const configOf = (lang: string, name: string, lengthScale: number) => ({
  audio: { sample_rate: 22050, quality: 'low' },
  espeak: { voice: lang },
  inference: { noise_scale: 0.667, length_scale: lengthScale, noise_w: 0.8 },
  phoneme_type: 'espeak',
  phoneme_map: {},
  phoneme_id_map: { _: [0], '^': [1], $: [2], ' ': [3], '!': [4], a: [14], ʃ: [96], ɛ: [61, 62] },
  num_symbols: 256,
  num_speakers: 1,
  speaker_id_map: {},
  language: { code: `${lang}_XX`, family: 'smoke', region: 'XX', name_native: name, name_english: name, country_english: 'Smokeland' },
  dataset: 'smoke',
})

function varint(b: Uint8Array, at: number): [number, number] {
  let n = 0
  let shift = 0
  for (;;) {
    const x = b[at++]
    n += (x & 0x7f) * 2 ** shift
    if (!(x & 0x80)) return [n, at]
    shift += 7
  }
}
/** ONNX metadata_props entries read back (field 14, each {key: 1, value: 2}): written here apart from the encoder. */
function decodeMetadata(b: Uint8Array): [string, string][] | string {
  const dec = new TextDecoder()
  const out: [string, string][] = []
  let at = 0
  while (at < b.length) {
    if (b[at++] !== 0x72) return `not field 14 at ${at - 1}`
    const [len, from] = varint(b, at)
    const inner = b.subarray(from, from + len)
    at = from + len
    const fields: Record<number, string> = {}
    for (let i = 0; i < inner.length; ) {
      const tag = inner[i++]
      const [l, s] = varint(inner, i)
      fields[tag >> 3] = dec.decode(inner.subarray(s, s + l))
      i = s + l
    }
    out.push([fields[1], fields[2]])
  }
  return out
}

const filesIn = (d: string): string[] => (existsSync(d) ? readdirSync(d, { withFileTypes: true }).flatMap(e => (e.isDirectory() ? filesIn(join(d, e.name)) : [join(d, e.name)])) : [])
const get = (path: string, headers: Record<string, string> = auth) => fetch(`${base}${path}`, { headers })
const post = (path: string, headers: Record<string, string> = auth) => fetch(`${base}${path}`, { method: 'POST', headers })
const entry = async (lang: string) => (await (await get(`/voice/offline/${lang}`)).json()) as any
/** The voice's entry once its status is one of [want] (5 s at most). */
async function until(lang: string, want: string[]) {
  let e: any
  for (let i = 0; i < 100; i++) {
    e = await entry(lang)
    if (want.includes(e.status)) return e
    await Bun.sleep(50)
  }
  return e
}

export default async function offlineVoice() {
  // --- the conversion, pure ----------------------------------------------------------------------------------------
  const one = onnxMetadata([['a', 'bc']])
  check('offline voice: a metadata entry is ONNX field 14 (0x72), its length, the key (0x0a) and the value (0x12)', Buffer.from(one).equals(Buffer.from([0x72, 7, 0x0a, 1, 0x61, 0x12, 2, 0x62, 0x63])), [...one])
  const long = appendOnnxMetadata(Uint8Array.of(8, 1), [['k', 'x'.repeat(200)], ['ž', '1']])
  check(
    'offline voice: appended after the model\'s bytes, a long value with a two-byte length, UTF-8 keys; read back in order',
    long[0] === 8 && long[1] === 1 && long[2] === 0x72 && long[3] === 0xce && long[4] === 0x01 && JSON.stringify(decodeMetadata(long.subarray(2))) === JSON.stringify([['k', 'x'.repeat(200)], ['ž', '1']]),
    [...long.subarray(0, 12)],
  )
  const slConfig = configOf('sl', 'Slovenian', 1.1)
  const tokens = '_ 0\n^ 1\n$ 2\n  3\n! 4\na 14\nʃ 96\nɛ 61\n'
  check('offline voice: tokens.txt is a line "<symbol> <first id>" per phoneme, the space\'s "  3"', tokensTxt(piperConfig.parse(slConfig)) === tokens, tokensTxt(piperConfig.parse(slConfig)))

  // --- a fake Hugging Face and the catalog ------------------------------------------------------------------------
  const shared = (['phontab', 'phonindex', 'phondata', 'intonations'] as const).map((path, i) => ({ path, ...put(ESPEAK_REPO, ESPEAK_REV, `espeak-ng-data/${path}`, bytesOf(200 + i * 300, 40 + i)) }))
  const slEspeak = [
    { path: 'sl_dict', ...put(ESPEAK_REPO, ESPEAK_REV, 'espeak-ng-data/sl_dict', bytesOf(700, 50)) },
    { path: 'lang/zls/sl', ...put(ESPEAK_REPO, ESPEAK_REV, 'espeak-ng-data/lang/zls/sl', 'name slovenian\nlanguage sl\n') },
  ]
  const itEspeak = [
    { path: 'it_dict', ...put(ESPEAK_REPO, ESPEAK_REV, 'espeak-ng-data/it_dict', bytesOf(600, 60)) },
    { path: 'lang/roa/it', ...put(ESPEAK_REPO, ESPEAK_REV, 'espeak-ng-data/lang/roa/it', 'name italian\nlanguage it\n') },
  ]
  const slDir = 'sl/sl_SI/smoke/low'
  const slId = 'sl_SI-smoke-low'
  const slModel = bytesOf(6000, 1)
  const slCard = '# Model card for sl_SI-smoke-low\n\n* Language: sl_SI\n* License: CC BY 4.0\n'
  const slMeta: [string, string][] = [['model_type', 'vits'], ['comment', 'piper'], ['language', 'Slovenian'], ['voice', 'sl'], ['has_espeak', '1'], ['n_speakers', '1'], ['sample_rate', '22050']]
  const converted = appendOnnxMetadata(slModel, slMeta)
  const itDir = 'it/it_IT/smoke/low'
  const itId = 'it_IT-smoke-low'
  const itModel = put('rhasspy/piper-voices', REV, `${itDir}/${itId}.onnx`, bytesOf(5000, 2))
  const catalog: PiperCatalog = {
    espeak: { repo: ESPEAK_REPO, revision: ESPEAK_REV, dir: 'espeak-ng-data', shared },
    voices: [
      {
        language: 'sl', id: slId, name: 'Smoke', quality: 'low', repo: 'rhasspy/piper-voices', revision: REV, dir: slDir,
        model: put('rhasspy/piper-voices', REV, `${slDir}/${slId}.onnx`, slModel),
        config: put('rhasspy/piper-voices', REV, `${slDir}/${slId}.onnx.json`, JSON.stringify(slConfig, null, 2)),
        card: put('rhasspy/piper-voices', REV, `${slDir}/MODEL_CARD`, slCard),
        converted: { bytes: converted.length, sha256: sha256(converted) },
        espeak: slEspeak, licence: 'CC BY 4.0', attribution: 'Smoke voice, trained on nothing',
      },
      {
        // the model's pin is someone else's: its size is right, its sha256 isn't
        language: 'it', id: itId, name: 'Fumo', quality: 'low', repo: 'rhasspy/piper-voices', revision: REV, dir: itDir,
        model: { bytes: itModel.bytes, sha256: '0'.repeat(64) },
        config: put('rhasspy/piper-voices', REV, `${itDir}/${itId}.onnx.json`, JSON.stringify(configOf('it', 'Italian', 1))),
        card: put('rhasspy/piper-voices', REV, `${itDir}/MODEL_CARD`, '# Model card for it_IT-smoke-low\n'),
        espeak: itEspeak, licence: 'CC0 1.0', attribution: 'Smoke voice, Italian',
      },
    ],
  }
  writeFileSync(piper.catalog, JSON.stringify(catalog, null, 2))
  const [sl, it] = catalog.voices
  const espeakBytes = (v: typeof sl) => [...shared, ...v.espeak].reduce((n, f) => n + f.bytes, 0)

  // --- the list, who may ask ----------------------------------------------------------------------------------------
  const listed = (await (await get('/voice/offline')).json()) as any
  const ls = listed.voices?.find((v: any) => v.language === 'sl')
  const li = listed.voices?.find((v: any) => v.language === 'it')
  check(
    'GET /voice/offline: the catalog\'s voices, missing, their size estimated from the pins (the converted model, tokens, card, espeak-ng files), licence, attribution and page',
    listed.voices?.length === 2 && !listed.error && ls?.status === 'missing' && ls.id === slId && ls.name === 'Smoke' && ls.quality === 'low' &&
      ls.bytes === converted.length + TOKENS_ESTIMATE + sl.card.bytes + espeakBytes(sl) && li?.status === 'missing' && li.bytes > it.model.bytes + espeakBytes(it) &&
      ls.licence === 'CC BY 4.0' && ls.attribution === 'Smoke voice, trained on nothing' && ls.source === `https://huggingface.co/rhasspy/piper-voices/tree/${REV}/${slDir}` &&
      !('files' in ls) && !('progress' in ls) && !('inference' in ls),
    listed,
  )
  check(
    'offline voice: the family token is refused (403), no token is 401',
    (await get('/voice/offline', fam)).status === 403 && (await post('/voice/offline/sl', fam)).status === 403 && (await get('/voice/offline/sl', fam)).status === 403 && (await get('/voice/offline')).status === 200 && (await fetch(`${base}/voice/offline`)).status === 401,
  )
  check('POST /voice/offline/<language>: a language without a voice is a 404', (await post('/voice/offline/xx')).status === 404 && (await get('/voice/offline/xx')).status === 404 && (await post('/voice/offline/slovene')).status === 404)
  check('offline voice: nothing in the cache before it is asked for', filesIn(piper.cache).length === 0, filesIn(piper.cache))

  // --- the Slovene voice: fetched, converted, served ------------------------------------------------------------------
  const p1 = await post('/voice/offline/sl')
  const e1 = (await p1.json()) as any
  const p2 = await post('/voice/offline/sl')
  const total = sl.model.bytes + sl.config.bytes + sl.card.bytes + espeakBytes(sl)
  check('POST /voice/offline/sl: 202, fetching, its progress of all it downloads; asked again, 202 (it joins) or 200', p1.status === 202 && e1.status === 'fetching' && e1.progress?.total === total && e1.progress.done >= 0 && [200, 202].includes(p2.status), { status: p1.status, e1, again: p2.status })
  const ready = await until('sl', ['ready', 'failed'])
  const want = ['model.onnx', 'tokens.txt', 'MODEL_CARD', ...[...shared, ...slEspeak].map(f => `espeak-ng-data/${f.path}`)]
  const paths = (ready.files ?? []).map((f: any) => f.path)
  check(
    'offline voice sl: ready, its files the model, tokens.txt, the card and the espeak-ng files; sherpa-onnx\'s settings from the config; the size exact',
    ready.status === 'ready' && JSON.stringify(paths) === JSON.stringify(want) && ready.bytes === ready.files.reduce((n: number, f: any) => n + f.bytes, 0) &&
      JSON.stringify(ready.inference) === JSON.stringify({ noise_scale: 0.667, length_scale: 1.1, noise_w: 0.8 }) && ready.sample_rate === 22050 && ready.espeak === 'sl' && !('progress' in ready) && !('error' in ready),
    ready,
  )
  check('POST /voice/offline/sl once it is ready: 200, ready', (await post('/voice/offline/sl')).status === 200)

  const bodies = new Map<string, Uint8Array>()
  const served: string[] = []
  for (const f of ready.files ?? []) {
    const r = await get(`/voice/offline/sl/${f.path}`)
    const b = new Uint8Array(await r.arrayBuffer())
    bodies.set(f.path, b)
    const ok = r.status === 200 && r.headers.get('content-type') === 'application/octet-stream' && r.headers.get('content-length') === String(f.bytes) && r.headers.get('cache-control') === 'private, max-age=31536000, immutable' && b.length === f.bytes && sha256(b) === f.sha256
    if (!ok) served.push(`${f.path}: ${r.status} ${r.headers.get('content-type')} ${r.headers.get('content-length')} ${r.headers.get('cache-control')} ${b.length} ${sha256(b) === f.sha256}`)
  }
  check('GET /voice/offline/sl/<path>: every listed file, octet-stream, its content-length, cached for good, its sha256 the list\'s', ready.files?.length === want.length && served.length === 0, served)
  const model = bodies.get('model.onnx') ?? new Uint8Array()
  check(
    'offline voice sl: model.onnx is the model\'s bytes, then sherpa-onnx\'s metadata (model_type, comment, language, voice, has_espeak, n_speakers, sample_rate), the converted pin\'s',
    Buffer.from(model.subarray(0, slModel.length)).equals(Buffer.from(slModel)) && JSON.stringify(decodeMetadata(model.subarray(slModel.length))) === JSON.stringify(slMeta) && sha256(model) === sl.converted!.sha256,
    decodeMetadata(model.subarray(slModel.length)),
  )
  const tokensServed = new TextDecoder().decode(bodies.get('tokens.txt'))
  check('offline voice sl: tokens.txt of its phoneme map, the space\'s line "  3" among them', tokensServed === tokens && tokensServed.split('\n').includes('  3'), tokensServed)
  check(
    'offline voice sl: the card and the espeak-ng files as the source has them',
    new TextDecoder().decode(bodies.get('MODEL_CARD')) === slCard && new TextDecoder().decode(bodies.get('espeak-ng-data/lang/zls/sl')) === 'name slovenian\nlanguage sl\n' && sha256(bodies.get('espeak-ng-data/phondata')!) === shared[2].sha256,
  )
  const part = await get('/voice/offline/sl/model.onnx', { ...auth, range: 'bytes=100-199' })
  const partBody = new Uint8Array(await part.arrayBuffer())
  check('GET …/model.onnx with a Range: 206 and those bytes (the app can resume a download)', part.status === 206 && partBody.length === 100 && Buffer.from(partBody).equals(Buffer.from(model.subarray(100, 200))), { status: part.status, length: partBody.length })
  const outside = ['voice.json', '..%2Fvoice.json', '..%2F..%2Fcatalog.json', `${slId}.onnx.json`, 'model.onnx.part', `model.onnx.${process.pid}.part`, 'espeak-ng-data', 'espeak-ng-data/', '%E0%A4%A', '../sl_SI-smoke-low/voice.json']
  const leaks: string[] = []
  for (const p of outside) {
    const r = await get(`/voice/offline/sl/${p}`)
    if (r.status !== 404) leaks.push(`${p}: ${r.status}`)
  }
  check('GET /voice/offline/sl/<path>: a path not in the list is a 404 (voice.json, the config, a .part, ../, a directory)', leaks.length === 0, leaks)
  check('offline voice: the family token gets no file (403)', (await get('/voice/offline/sl/model.onnx', fam)).status === 403)
  const slCache = join(piper.cache, slId)
  const onDisk = existsSync(join(slCache, 'voice.json')) ? JSON.parse(readFileSync(join(slCache, 'voice.json'), 'utf8')) : null
  check(
    'offline voice sl: <cache>/<id>/voice.json lists the files, sherpa-onnx\'s settings, the licence and when; no .part left anywhere',
    JSON.stringify(onDisk?.files) === JSON.stringify(ready.files) && onDisk.espeak === 'sl' && onDisk.sample_rate === 22050 && onDisk.licence === 'CC BY 4.0' && !Number.isNaN(Date.parse(onDisk.fetched_at)) && !filesIn(piper.cache).some(f => f.endsWith('.part')),
    { onDisk, files: filesIn(piper.cache) },
  )

  // --- the Italian voice: a wrong pin -----------------------------------------------------------------------------------
  const p3 = await post('/voice/offline/it')
  const failed = await until('it', ['ready', 'failed'])
  const itCache = join(piper.cache, itId)
  check(
    'offline voice it: a model whose sha256 isn\'t its pin fails, the error names the checksum; no model in the cache, no .part',
    p3.status === 202 && failed.status === 'failed' && /checksum mismatch/.test(failed.error ?? '') && failed.error.includes(`${itId}.onnx`) && !('files' in failed) && !existsSync(join(itCache, 'model.onnx')) && !existsSync(join(itCache, 'voice.json')) && !filesIn(itCache).some(f => f.endsWith('.part')),
    { status: p3.status, failed, files: filesIn(itCache) },
  )
  check('offline voice it: nothing of it is served', (await get('/voice/offline/it/MODEL_CARD')).status === 404 && (await get('/voice/offline/it/model.onnx')).status === 404 && (await get('/voice/offline/it/tokens.txt')).status === 404)
  check('offline voice it: the failure is kept with its error until asked again', (await entry('it')).status === 'failed' && (await entry('it')).error === failed.error)
  // The source's config changes: a retry that fetched it again would fail on it; one that kept it fails on the model again.
  put('rhasspy/piper-voices', REV, `${itDir}/${itId}.onnx.json`, '{"not": "the pinned config"}')
  const p4 = await post('/voice/offline/it')
  const again = await until('it', ['ready', 'failed'])
  check(
    'offline voice it: a second POST tries again (202), keeps the files it had checked (the config isn\'t fetched again) and fails on the model again',
    p4.status === 202 && again.status === 'failed' && again.error.includes(`${itId}.onnx:`) && !again.error.includes('.onnx.json') && existsSync(join(itCache, `${itId}.onnx.json`)) && existsSync(join(itCache, 'MODEL_CARD')),
    { status: p4.status, again },
  )

  // --- another start: what is in the cache counts -------------------------------------------------------------------
  const logs: string[] = []
  const log = (...a: unknown[]) => void logs.push(a.join(' '))
  const nowhere = tempDir('lani-smoke-piper-nowhere-')
  const fresh = new OfflineVoices({ cacheDir: piper.cache, source: nowhere, catalog: catalogFrom(piper.catalog, log), log })
  const after = fresh.list().voices
  check(
    'offline voice: another bridge on the same cache finds the Slovene voice ready (voice.json, the files\' sizes), the Italian one missing',
    after.find(v => v.language === 'sl')?.status === 'ready' && JSON.stringify(after.find(v => v.language === 'sl')?.files) === JSON.stringify(ready.files) && after.find(v => v.language === 'it')?.status === 'missing',
    after,
  )
  const copy = tempDir('lani-smoke-piper-copy-')
  cpSync(slCache, join(copy, slId), { recursive: true })
  writeFileSync(join(copy, slId, 'tokens.txt'), 'cut')
  const damaged = new OfflineVoices({ cacheDir: copy, source: nowhere, catalog: catalogFrom(piper.catalog, log), log })
  const before = damaged.entry('sl')?.status
  damaged.start('sl')
  let repaired = damaged.entry('sl')
  for (let i = 0; i < 100 && repaired?.status === 'fetching'; i++) {
    await Bun.sleep(20)
    repaired = damaged.entry('sl')
  }
  check(
    'offline voice: a file not its size in the cache leaves the voice missing; fetched again, every checked file is kept (an empty source) and tokens.txt made again',
    before === 'missing' && repaired?.status === 'ready' && JSON.stringify(repaired.files) === JSON.stringify(ready.files),
    { before, repaired, logs },
  )

  // --- over HTTP, as from Hugging Face: …/resolve/… answers with a redirect to its CDN ------------------------------
  let stall = false
  const cdn = Bun.serve({
    hostname: '127.0.0.1',
    port: 0,
    fetch(req) {
      const u = new URL(req.url)
      if (!u.pathname.startsWith('/cdn/')) return Response.redirect(`http://127.0.0.1:${cdn.port}/cdn${u.pathname}`, 302)
      const p = join(piper.source, decodeURIComponent(u.pathname.slice('/cdn/'.length)))
      // a download that stops halfway: the first bytes, then nothing, the connection open
      if (stall && p.endsWith('.onnx')) return new Response(new ReadableStream({ start: c => c.enqueue(slModel.subarray(0, 100)) }))
      return existsSync(p) ? new Response(Bun.file(p)) : new Response('not found', { status: 404 })
    },
  })
  const overHttp = async (idleMs: number) => {
    const dir = tempDir('lani-smoke-piper-http-')
    const v = new OfflineVoices({ cacheDir: dir, source: `http://127.0.0.1:${cdn.port}/`, catalog: catalogFrom(piper.catalog, log), log, idleMs })
    v.start('sl')
    let e = v.entry('sl')
    for (let i = 0; i < 200 && e?.status === 'fetching'; i++) {
      await Bun.sleep(20)
      e = v.entry('sl')
    }
    return { e, dir }
  }
  try {
    const { e: viaHttp } = await overHttp(5_000)
    check('offline voice over HTTP: the redirect followed, the same files as from the directory', viaHttp?.status === 'ready' && JSON.stringify(viaHttp.files) === JSON.stringify(ready.files), viaHttp)
    stall = true
    const { e: stalled, dir } = await overHttp(300)
    check(
      'offline voice over HTTP: a download that gets no bytes for a while is given up, said so, its .part removed',
      stalled?.status === 'failed' && /\.onnx: no bytes for 300 ms/.test(stalled.error ?? '') && !filesIn(dir).some(f => f.endsWith('.part')) && !existsSync(join(dir, slId, 'model.onnx')),
      { stalled, files: filesIn(dir) },
    )
  } finally {
    cdn.stop(true)
  }

  const broken = join(tempDir('lani-smoke-piper-broken-'), 'catalog.json')
  writeFileSync(broken, JSON.stringify({ ...catalog, voices: [{ ...catalog.voices[0], dir: '../outside' }] }))
  const none = new OfflineVoices({ cacheDir: copy, source: nowhere, catalog: catalogFrom(broken, log), log }).list()
  check('LANI_PIPER_CATALOG that isn\'t a catalog (a path out of its directory): no voices, the error said', none.voices.length === 0 && /relative path/.test(none.error ?? '') && logs.some(l => l.includes('LANI_PIPER_CATALOG')), none)
}
