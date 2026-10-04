// Smoke checks: speech recognition. /stt proxies a recording to the (fake) Whisper worker.
import { readdirSync } from 'node:fs'
import { join } from 'node:path'
import { Stt } from '../../src/features/stt'
import { auth, base, bridgeWhisper, check, dataDir, fam, sendChunked } from './harness'

export default async function stt() {
  const take = new Uint8Array([0, 0, 0, 0x18, ...new TextEncoder().encode('ftypM4A '), 0, 0, 0, 0, 7, 7, 7, 7])
  const post = (body: BodyInit, query = '?language=sl&prompt=Na%20vrtu%20je%20%C4%8Debelnjak.', headers: Record<string, string> = { authorization: auth.authorization }) =>
    fetch(`${base}/stt${query}`, { method: 'POST', headers: { 'content-type': 'audio/mp4', ...headers }, body })
  const filesIn = (d: string): string[] => readdirSync(d, { withFileTypes: true }).flatMap(e => (e.isDirectory() ? filesIn(join(d, e.name)) : [join(d, e.name)]))
  const before = filesIn(dataDir).length
  const whisper = bridgeWhisper.st

  const r1 = await post(take)
  const b1 = await r1.json()
  const call = whisper.calls.at(-1)
  check('POST /stt returns the transcript with word probabilities', r1.ok && b1.text === 'Na vrtu je čebelniak.' && b1.segments[0].words.length === 4 && b1.segments[0].words[3].prob === 0.21 && b1.model === 'large-v3-turbo', b1)
  check('POST /stt passes language, prompt and the audio unchanged', call?.query.language === 'sl' && call.query.prompt === 'Na vrtu je čebelnjak.' && call.bytes.length === take.length && call.bytes.every((x, i) => x === take[i]) && call.type === 'application/octet-stream', call?.query)
  check('POST /stt keeps no audio on disk', filesIn(dataDir).length === before)

  const n = whisper.calls.length
  check('POST /stt needs a token', (await post(take, undefined, {})).status === 401)
  check('family token cannot use /stt', (await post(take, undefined, fam)).status === 403 && (await fetch(`${base}/stt/status`, { headers: fam })).status === 403)
  check('POST /stt rejects what is not audio (415)', (await post(new TextEncoder().encode('hello, not audio'))).status === 415)
  check('POST /stt rejects an empty body', (await post(new Uint8Array())).status === 400)
  const big = new Uint8Array(2 * 1024 * 1024 + 1)
  big.set(take)
  check('POST /stt rejects more than 2 MB (413)', (await post(big)).status === 413)
  const chunked = await sendChunked(`${base}/stt?language=sl`, 'POST', { authorization: auth.authorization }, 3 * 1024 * 1024, take)
  check('POST /stt rejects a chunked body over 2 MB', chunked.status === 413 || chunked.status === 0, chunked)
  check('POST /stt rejects an odd language', (await post(take, '?language=../x')).status === 400)
  check('refused requests never reach the worker', whisper.calls.length === n, whisper.calls.length - n)
  const noPrompt = await post(take, '')
  check('POST /stt without a prompt: sl, no prompt', noPrompt.ok && whisper.calls.at(-1)?.query.language === 'sl' && !('prompt' in whisper.calls.at(-1)!.query))

  whisper.mode = 'busy'
  const busy = await post(take)
  check('worker busy: 503 with a reason', busy.status === 503 && (await busy.json()).error === 'speech recognition is busy' && busy.headers.get('retry-after') === '5')
  whisper.mode = 'slow'
  const t0 = Date.now()
  const slow = await post(take)
  check('worker too slow: 504 after the timeout', slow.status === 504 && Date.now() - t0 < 1_400, Date.now() - t0)
  whisper.mode = 'error'
  check('worker error: 502', (await post(take)).status === 502)
  whisper.mode = 'junk'
  const junk = await (await post(take)).json()
  check(
    'odd worker fields are cleaned',
    JSON.stringify(junk) === JSON.stringify({ text: 'Hvala.', segments: [{ start: 0, end: 1, text: 'Hvala.', words: [{ word: 'Hvala.', start: null, end: null, prob: null }] }], language: 'sl', duration: null }),
    junk,
  )
  whisper.mode = 'ok'

  const status = await (await fetch(`${base}/stt/status`, { headers: auth })).json()
  check('GET /stt/status: up, model, device', status.up === true && status.model === 'large-v3-turbo' && status.device === 'fake', status)

  // --- other languages: the app asks in its target language; the worker picks the model -------------------------
  const it = await post(take, '?language=it&prompt=Dov%27%C3%A8%20l%27acqua%3F')
  const itBody = await it.json()
  check("POST /stt?language=it: Italian and its prompt reach the worker", it.ok && whisper.calls.at(-1)?.query.language === 'it' && whisper.calls.at(-1)?.query.prompt === "Dov'è l'acqua?", { itBody, q: whisper.calls.at(-1)?.query })
  const statusIt = await (await fetch(`${base}/stt/status?language=it`, { headers: auth })).json()
  check('GET /stt/status?language=it: up, the language passed to the worker (which warms its model)', statusIt.up === true && statusIt.language === 'it' && statusIt.language_model === 'large-v3-turbo' && whisper.health.at(-1)?.language === 'it', statusIt)
  const statusDe = await (await fetch(`${base}/stt/status?language=de`, { headers: auth })).json()
  check('GET /stt/status?language=de: German is up too (the general model)', statusDe.up === true && statusDe.language === 'de' && statusDe.language_model === 'large-v3-turbo', statusDe)
  const statusHr = await (await fetch(`${base}/stt/status?language=hr`, { headers: auth })).json()
  check('GET /stt/status?language=hr: a language without a model on the node is down, with the reason', statusHr.up === false && statusHr.language === 'hr' && /no model for hr/.test(statusHr.error), statusHr)
  const hr = await post(take, '?language=hr')
  const hrBody = await hr.json()
  check('POST /stt?language=hr without a model: 503, "no model for hr" (the app keeps the phone)', hr.status === 503 && hrBody.up === false && /no model for hr/.test(hrBody.error), hrBody)
  check('GET /stt/status?language=../x: an odd language is left out', (await (await fetch(`${base}/stt/status?language=../x`, { headers: auth })).json()).language === undefined && whisper.health.at(-1)?.language === undefined)
  whisper.legacy = true
  const legacy = await (await fetch(`${base}/stt/status?language=it`, { headers: auth })).json()
  check('GET /stt/status?language=it from a worker before languages: up (its one model takes every language)', legacy.up === true && legacy.language === 'it', legacy)
  whisper.legacy = false

  // Worker not running (nothing listens on port 9): a clear 503, and the status says so.
  const down = new Stt({ url: 'http://127.0.0.1:9' })
  const dr = await down.transcribe(new Request('http://x/stt?language=sl', { method: 'POST', body: take }), new URL('http://x/stt?language=sl'))
  check('worker down: 503 "not running"', dr.status === 503 && (await dr.json()).error === 'speech recognition is not running on the node')
  check('worker down: status up false', (await down.status()).up === false)
}
