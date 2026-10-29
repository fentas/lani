// Smoke checks: voices on a visit (src/visit-voices.ts, src/features/visit-voices.ts), with the towns section's bridges:
// Jan's (the harness's: Slovene, primorska; its clips put into its store here: the voice section leaves its ElevenLabs
// failing), Mia's (Italian, friuli: her node's ElevenLabs is [visitEleven], her local worker [visitGepard], so a Slovene
// town's line can be voiced there) and Luka's (a child's, friuli: at most two clips an hour for a town,
// LANI_TOWN_VOICE_PER_HOUR=2). Called by towns.ts while the towns are linked.
// - the host's side: GET /town/voice hands over a clip it has, signed, for linked towns only, never one made for the
//   asking (the engines aren't asked), never a file but the store's own MP3s; GET /town/voice/cast its cast's voices;
// - the visitor's side: its app's /towns/:id/voice/say gets the host's clip (kept, byte for byte), else voices it on
//   the node in the host's cast voice (Mia's ElevenLabs in Babica's Zlata, in Slovene; then her local worker, a Slovene
//   town's), else 204; the learner's own lines in the town's voice for their gender; an Italian town's line (in-process)
//   in its cast's Italian voice, never with the local worker.
import { Database } from 'bun:sqlite'
import { readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { loadBridgeKey, type BridgeKey } from '../../src/pairing'
import { normalizeText } from '../../src/family'
import { H, responseMessage, signRequest, verifySigned } from '../../src/towns'
import { castFromPack, cleanHostClip, cleanVisitCast, VisitClips, visitVoices as voicesFor, voiceInTown } from '../../src/visit-voices'
import { ElevenLabs, Gepard } from '../../src/voice'
import { bridgeEleven, bridgeGepard, check, culturesDir, fakeEleven, fakeGepard, fam, mp3, tempDir } from './harness'

export type Town = { base: string; auth: Record<string, string>; key: BridgeKey; data: string }

/** Mia's node's ElevenLabs and local worker (towns.ts starts her bridge with them): a Slovene town's line voiced on her node. */
export const visitEleven = fakeEleven()
export const visitGepard = fakeGepard()

const speakersOf = (path: string) => JSON.parse(readFileSync(path, 'utf8')).speakers
const PRIMORSKA = speakersOf(join(culturesDir, '../voice-cast.json'))
const FRIULI = speakersOf(join(culturesDir, 'friuli/voice-cast.json'))

/** A clip put into [town]'s voice store by hand (its row and its file), as a store tampered with or from before might hold. */
function plant(town: Town, o: { text: string; voice: string; file: string; bytes?: Uint8Array }) {
  const dir = join(town.data, 'app/voice')
  if (o.bytes) writeFileSync(join(dir, 'files', o.file), o.bytes)
  const db = new Database(join(dir, 'voice.db'))
  db.exec('PRAGMA busy_timeout = 5000')
  const norm = normalizeText(o.text)
  db.query('INSERT OR REPLACE INTO clips (key, norm, text, voice, engine, file, chars, created_at, source) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)').run(`${o.voice}:${norm}`, norm, o.text, o.voice, 'elevenlabs', o.file, o.text.length, new Date().toISOString(), 'smoke')
  db.close()
}

export async function visitVoiceChecks(jan: Town, mia: Town, luka: Town) {
  const ask = async (to: Town, from: BridgeKey, q: Record<string, string | string[]>, path = '/town/voice') => {
    const u = new URLSearchParams()
    for (const [k, v] of Object.entries(q)) for (const x of [v].flat()) u.append(k, x)
    const p = u.size ? `${path}?${u}` : path
    const headers = signRequest(from, { method: 'GET', path: p, to: to.key.fingerprint })
    const res = await fetch(`${to.base}${p}`, { headers })
    const text = await res.text()
    let body: any = null
    try {
      body = JSON.parse(text)
    } catch {}
    return { res, text, body, nonce: headers[H.nonce], headers, path: p }
  }
  const say = (from: Town, to: Town, body: unknown, headers = from.auth) => fetch(`${from.base}/towns/${to.key.fingerprint}/voice/say`, { method: 'POST', headers, body: JSON.stringify(body) })
  const fileOf = async (town: Town, url: string) => new Uint8Array(await (await fetch(`${town.base}${url}`, { headers: town.auth })).arrayBuffer())

  // --- Jan's town has a line of Babica's, and one of the female narrator's (as his node made them) ---------------------
  const line = 'Dober dan, kako ste kaj danes?'
  const thanks = 'Hvala lepa za vse, res!'
  const janClip = mp3(`el:babica:${line}`)
  const narratorBytes = mp3(`el:nina:${thanks}`)
  plant(jan, { text: line, voice: 'grandma', file: `${'1'.repeat(40)}.mp3`, bytes: janClip })
  plant(jan, { text: thanks, voice: 'female.sl', file: `${'2'.repeat(40)}.mp3`, bytes: narratorBytes })
  const janIdx = await (await fetch(`${jan.base}/voice/index`, { headers: jan.auth })).json()
  check("visit voices: Jan's town has Babica's line and the narrator's (its own clips: the premise)", !!janIdx['dober dan kako ste kaj danes']?.grandma && !!janIdx['hvala lepa za vse res']?.female, { line: janIdx['dober dan kako ste kaj danes'], thanks: janIdx['hvala lepa za vse res'] })

  // --- the host's side ---------------------------------------------------------------------------------------------
  const ttsBefore = bridgeEleven.st.tts.length
  const gepardBefore = bridgeGepard.st.calls.length
  const got = await ask(jan, mia.key, { v: '1', text: line, voice: 'grandma' })
  const signedBack = verifySigned(jan.key.spki.toString('base64'), responseMessage(got.nonce, got.text), got.res.headers.get(H.signature) ?? '')
  const bytes = got.body?.audio ? new Uint8Array(Buffer.from(got.body.audio, 'base64')) : new Uint8Array()
  check("visit voices: GET /town/voice hands a linked town the host's clip of a line, signed back: byte for byte the host's own", got.res.status === 200 && signedBack && got.body.town === jan.key.fingerprint && got.body.voice === 'grandma' && got.body.engine === 'elevenlabs' && got.body.language === 'sl' && Buffer.from(bytes).equals(Buffer.from(janClip)), { status: got.res.status, body: { ...got.body, audio: got.body?.audio?.length } })
  const first = await ask(jan, mia.key, { v: '1', text: line, voice: ['@nobody', 'grandma'] })
  check('visit voices: … the first of the voices asked that has one (someone without a voice of their own: their archetype\'s)', first.res.status === 200 && first.body.voice === 'grandma', first.body?.voice)
  const missing = await ask(jan, mia.key, { v: '1', text: 'Tega stavka ni nihče izgovoril.', voice: ['grandma', 'female'] })
  const replay = await fetch(`${jan.base}${got.path}`, { headers: got.headers })
  check("visit voices: … a line it has no clip of: 404 no_clip, and nothing made for it (ElevenLabs and the local worker weren't asked)", missing.res.status === 404 && missing.body?.code === 'no_clip' && bridgeEleven.st.tts.length === ttsBefore && bridgeGepard.st.calls.length === gepardBefore, { status: missing.res.status, tts: bridgeEleven.st.tts.length - ttsBefore, gepard: bridgeGepard.st.calls.length - gepardBefore })
  const stranger = loadBridgeKey(tempDir('lani-smoke-voice-stranger-'))
  const refusals = {
    unlinked: (await ask(jan, stranger, { v: '1', text: line, voice: 'grandma' })).res.status,
    unsigned: (await fetch(`${jan.base}/town/voice?text=${encodeURIComponent(line)}&voice=grandma`)).status,
    appToken: (await fetch(`${jan.base}/town/voice?text=${encodeURIComponent(line)}&voice=grandma`, { headers: jan.auth })).status,
    replayed: replay.status,
    cast: (await ask(jan, stranger, {}, '/town/voice/cast')).res.status,
  }
  check('visit voices: an unlinked town, an unsigned request, the app token, a replayed request: 401 (the cast too)', Object.values(refusals).every(s => s === 401), refusals)
  const bad = {
    path: (await ask(jan, mia.key, { v: '1', text: line, voice: '../../voice.db' })).res.status,
    long: (await ask(jan, mia.key, { v: '1', text: 'a'.repeat(301), voice: 'grandma' })).res.status,
    many: (await ask(jan, mia.key, { v: '1', text: line, voice: ['grandma', 'female', 'male', 'boy', 'girl'] })).res.status,
    none: (await ask(jan, mia.key, { v: '1', text: line })).res.status,
    gender: (await ask(jan, mia.key, { v: '1', text: line, voice: 'player', gender: 'x' })).res.status,
    control: (await ask(jan, mia.key, { v: '1', text: 'Dober\u0000dan', voice: 'grandma' })).res.status,
  }
  check('visit voices: … a voice that is a path, a line over 300 characters, five voices, none, a wrong gender, a control character: 400', Object.values(bad).every(s => s === 400), bad)

  // a store tampered with: a row that names a file outside the store, one that isn't an MP3, one too large to hand over
  const notMp3 = `${'a'.repeat(40)}.mp3`
  const huge = `${'b'.repeat(40)}.mp3`
  plant(jan, { text: 'Kje je ključ od hiše?', voice: 'grandpa', file: '../../towns.json' })
  plant(jan, { text: 'Kje je ključ od skednja?', voice: 'grandpa', file: notMp3, bytes: new TextEncoder().encode('{"not":"audio"}') })
  const big = new Uint8Array(900 * 1024)
  big.set(mp3('big'))
  plant(jan, { text: 'Kje je ključ od kleti?', voice: 'grandpa', file: huge, bytes: big })
  const tampered = await Promise.all(['Kje je ključ od hiše?', 'Kje je ključ od skednja?', 'Kje je ključ od kleti?'].map(t => ask(jan, mia.key, { v: '1', text: t, voice: 'grandpa' })))
  check("visit voices: … only the store's own MP3s are handed over: a row naming a file outside the store, a file that isn't MP3, one over 768 KB: no_clip", tampered.every(t => t.res.status === 404 && t.body?.code === 'no_clip') && !tampered.some(t => t.text.includes('links')), tampered.map(t => t.res.status))

  const cast = await ask(jan, mia.key, {}, '/town/voice/cast')
  const castBack = verifySigned(jan.key.spki.toString('base64'), responseMessage(cast.nonce, cast.text), cast.res.headers.get(H.signature) ?? '')
  const sp = cast.body?.speakers ?? {}
  check("visit voices: GET /town/voice/cast: the host's speakers in its language (the narrators' Slovene voices, Nina and Luka; Babica's Zlata), their local voices, the learner's voice by gender",
    cast.res.status === 200 && castBack && cast.body.language === 'sl' && cast.body.culture === 'primorska' && sp.female?.elevenlabs === PRIMORSKA.female.languages.sl.elevenlabs && sp.male?.elevenlabs === PRIMORSKA.male.languages.sl.elevenlabs && !!sp.female.carrier === !!PRIMORSKA.female.languages.sl.carrier && sp.grandma?.elevenlabs === PRIMORSKA.grandma.elevenlabs && sp.grandma.gepard === 'nina' && sp.female.gepard_words === 'ana' && sp.girl?.tempo?.elevenlabs === 1.1 && cast.body.player?.male === 'male' && cast.body.player.female === 'female',
    { status: cast.res.status, female: sp.female, grandma: sp.grandma, player: cast.body?.player })
  check("visit voices: … nothing private in it: no key, no account, no designed voice's id", !/test-key|library_owner|alternatives/.test(cast.text) && Object.values(cast.body?.people ?? {}).every((p: any) => Object.keys(p).sort().join() === 'archetype,pitch,rate,speaker'), cast.text.slice(0, 300))

  // --- the visitor's side: Mia's app on a visit to Jan's town ----------------------------------------------------------
  const a = await say(mia, jan, { text: line, voice: 'grandma' })
  const aBody = await a.json()
  const aBytes = aBody.url ? await fileOf(mia, aBody.url) : new Uint8Array()
  check("visit voices: Mia's app asks her bridge for Babica's line: the host's own clip, kept on her node, byte for byte", a.status === 200 && aBody.from === 'host' && aBody.voice === 'grandma' && aBody.engine === 'elevenlabs' && /^\/towns\/voice\/file\/[0-9a-f]{40}\.mp3$/.test(aBody.url) && Buffer.from(aBytes).equals(Buffer.from(janClip)), aBody)
  const idx = await (await fetch(`${mia.base}/towns/${jan.key.fingerprint}/voice/index`, { headers: mia.auth })).json()
  const again = await (await say(mia, jan, { text: line, voice: 'grandma' })).json()
  check("visit voices: … the town's index has it (normalized text → voice → URL); asked again, the same clip", idx['dober dan kako ste kaj danes']?.grandma === aBody.url && again.url === aBody.url && again.from === 'host', { idx, again })
  const kept = JSON.parse(readFileSync(join(mia.data, 'app/voice/visits/index.json'), 'utf8'))
  check("visit voices: … kept apart from her village's clips (<voice dir>/visits/), with the town's language", kept.clips.some((c: any) => c.town === jan.key.fingerprint && c.language === 'sl' && c.voice === 'grandma' && c.from === 'host'), kept.clips.length)

  // a line the host has no clip of: Mia's node voices it in the host's cast voice: her ElevenLabs in Babica's Zlata, Slovene
  const made = 'Kako lepo, da si prišla k nam.'
  const tts0 = visitEleven.st.tts.length
  const m = await say(mia, jan, { text: made, voice: 'grandma' })
  const mBody = await m.json()
  const sentSl = visitEleven.st.tts.slice(tts0)
  check("visit voices: a line Jan's town has no clip of: Mia's node voices it in the town's cast voice (her ElevenLabs, Babica's Zlata, language_code sl), a live line",
    m.status === 200 && mBody.from === 'node' && mBody.engine === 'elevenlabs' && mBody.voice === 'grandma' && sentSl.length === 1 && sentSl[0].voice === PRIMORSKA.grandma.elevenlabs && sentSl[0].body.language_code === 'sl' && sentSl[0].body.text === made && sentSl[0].body.model_id === 'eleven_v4_turbo',
    { mBody, sent: sentSl.map(t => ({ voice: t.voice, lang: t.body.language_code, text: t.body.text })) })
  const mBytes = await fileOf(mia, mBody.url)
  check("visit voices: … the take is kept on Mia's node for the visit, never among her village's clips (/voice/index)", new TextDecoder().decode(mBytes).includes(`${PRIMORSKA.grandma.elevenlabs}:${made}`) && !Object.keys(await (await fetch(`${mia.base}/voice/index`, { headers: mia.auth })).json()).some(k => k.startsWith('kako lepo da si')), new TextDecoder().decode(mBytes).slice(0, 80))
  // her ElevenLabs down: her local worker, Babica's "nina" (a Slovene town: the worker speaks nothing else)
  visitEleven.st.fail = 500
  const fresh = 'Lepo vas je videti pri nas.'
  const b = await say(mia, jan, { text: fresh, voice: 'grandma' })
  const bBody = await b.json()
  const asked = visitGepard.st.calls.filter((c: any) => c.text === fresh)
  check("visit voices: … ElevenLabs down: her local worker in Babica's local voice (nina)", b.status === 200 && bBody.from === 'node' && bBody.engine === 'gepard' && bBody.voice === 'grandma' && asked.length === 1 && asked[0].speaker === 'nina', { bBody, asked })
  // a word no section voiced before (the host has none of it)
  const word = await (await say(mia, jan, { text: 'brumbrumček', voice: 'female' })).json()
  check("visit voices: … a lone word of the female narrator in her local voice for words (ana)", word.from === 'node' && visitGepard.st.calls.some((c: any) => c.text === 'brumbrumček' && c.speaker === 'ana'), { word, calls: visitGepard.st.calls.slice(-2) })

  // the learner's own line: in the town's voice for her (Mia's profile says no gender: the female one), the host's clip
  const prof = await (await fetch(`${mia.base}/towns/${jan.key.fingerprint}/voice/profiles`, { headers: mia.auth })).json()
  const own = await (await say(mia, jan, { text: thanks, voice: 'player' })).json()
  const ownBytes = own.url ? await fileOf(mia, own.url) : new Uint8Array()
  check("visit voices: the learner's own line: the town's voice for them (/voice/profiles says it: female), the host's clip of it", prof.player === 'female' && prof.language === 'sl' && own.from === 'host' && own.voice === 'female' && Buffer.from(ownBytes).equals(Buffer.from(narratorBytes)), { prof: { player: prof.player, language: prof.language }, own })
  visitGepard.st.up = false
  const ownNone = await say(mia, jan, { text: 'Tega pa res ne znam povedati.', voice: 'player' })
  visitGepard.st.up = true
  check("visit voices: … one no voice can have now: 204 (no other voice stands in for the learner's own), the phone speaks it", ownNone.status === 204, ownNone.status)

  // the visitor's choice ("🗣️ Moj glas · My voice": my-voice.ts): the town's voice of her kind at her place on its list.
  // Mia (no gender: the women's voices first, then the men's) picks her second woman's voice: Jan's town's second woman's
  // voice says her line (the host's clip in it)
  const mineMia = await (await fetch(`${mia.base}/learner/voice`, { headers: mia.auth })).json()
  const pick = mineMia.options?.find((o: any) => o.gender === 'female' && o.pick === 1)
  const hostSecond = cast.body?.players?.female?.adult?.[1]
  const picked = await (await fetch(`${mia.base}/learner/voice`, { method: 'POST', headers: mia.auth, body: JSON.stringify({ voice: pick?.id }) })).json()
  const chosenLine = 'Hvala, pridem spet.'
  plant(jan, { text: chosenLine, voice: hostSecond, file: `${'e'.repeat(40)}.mp3`, bytes: mp3(`second:${chosenLine}`) })
  const prof2 = await (await fetch(`${mia.base}/towns/${jan.key.fingerprint}/voice/profiles`, { headers: mia.auth })).json()
  const own3 = await (await say(mia, jan, { text: chosenLine, voice: 'player' })).json()
  check("visit voices: the visitor's choice of her voice (her second woman's): the town's voice of her kind at her place on its list (its second woman's; GET /town/voice/cast has its players), its profiles say it, the host's clip in it",
    !!pick && picked.voice === pick.id && cast.body.players?.male?.child?.length >= 2 && !!hostSecond && prof2.player === hostSecond && own3.from === 'host' && own3.voice === hostSecond,
    { pick, players: cast.body?.players, prof2: prof2.player, own3 })
  await fetch(`${mia.base}/learner/voice`, { method: 'POST', headers: mia.auth, body: JSON.stringify({ voice: 'default' }) })

  // the app's side of the routes
  const refusedApp = {
    unknown: (await fetch(`${mia.base}/towns/${'0'.repeat(32)}/voice/say`, { method: 'POST', headers: mia.auth, body: JSON.stringify({ text: line, voice: 'grandma' }) })).status,
    badVoice: (await say(mia, jan, { text: line, voice: '../x' })).status,
    long: (await say(mia, jan, { text: 'a'.repeat(301), voice: 'grandma' })).status,
    explanation: (await say(mia, jan, { text: 'Biti (to be) has a short form', voice: 'teacher', lang: 'en' })).status,
    family: (await say(jan, mia, { text: line, voice: 'grandma' }, { ...fam, 'content-type': 'application/json' })).status,
    noToken: (await fetch(`${mia.base}${aBody.url}`)).status,
    notKept: (await fetch(`${mia.base}/towns/voice/file/${'c'.repeat(40)}.mp3`, { headers: mia.auth })).status,
    traversal: (await fetch(`${mia.base}/towns/voice/file/..%2Findex.json`, { headers: mia.auth })).status,
  }
  check('visit voices: the app\'s routes: an unknown town 404, a bad voice or line 400, an explanation in another language 400, the family token 403, no token 401, a file not kept or a path 404', refusedApp.unknown === 404 && refusedApp.badVoice === 400 && refusedApp.long === 400 && refusedApp.explanation === 400 && refusedApp.family === 403 && refusedApp.noToken === 401 && refusedApp.notKept === 404 && refusedApp.traversal === 404, refusedApp)

  // --- the other way: Jan's town asks Mia's Italian town: it has no clip; an Italian town's line voiced on a node --------
  const none = await ask(mia, jan.key, { v: '1', text: 'Buongiorno! Come stai oggi?', voice: 'grandma' })
  check("visit voices: Mia's Italian town has no clips (no ElevenLabs before this section): no_clip", none.res.status === 404 && none.body?.code === 'no_clip', none.res.status)
  const itCast = await ask(mia, jan.key, {}, '/town/voice/cast')
  check("visit voices: … its cast for a visit: the Italian voices (Angelina), no local voice (the worker speaks only Slovene)", itCast.res.status === 200 && itCast.body.language === 'it' && itCast.body.speakers?.grandma?.elevenlabs === FRIULI.grandma.elevenlabs && !Object.values(itCast.body.speakers ?? {}).some((x: any) => x.gepard), itCast.body?.speakers?.grandma)
  // in-process, as Jan's node voices it (the harness bridge's ElevenLabs is left failing by the voice section): its
  // ElevenLabs in the town's voice and language, never the local worker for an Italian line
  const fe = fakeEleven()
  const fg = fakeGepard()
  const it = 'Buongiorno! Come stai oggi?'
  const made2 = await voiceInTown({
    text: it, language: 'it', spec: itCast.body.speakers.grandma,
    engines: { eleven: new ElevenLabs({ key: 'test-key', base: fe.url, language: 'sl' }), gepard: new Gepard({ url: fg.url }) }, live: () => true,
  })
  check("visit voices: an Italian town's line on a node: ElevenLabs in its cast's voice (Angelina) with language_code it, never the local worker",
    made2?.engine === 'elevenlabs' && fe.st.tts.length === 1 && fe.st.tts[0].voice === FRIULI.grandma.elevenlabs && fe.st.tts[0].body.language_code === 'it' && fe.st.tts[0].body.text === it && fg.st.calls.length === 0,
    { made2: made2 && { engine: made2.engine, voice: made2.engine_voice }, tts: fe.st.tts.map(t => ({ voice: t.voice, lang: t.body.language_code })), gepard: fg.st.calls.length })
  fe.st.fail = 500
  const noneIt = await voiceInTown({ text: 'Ciao, a domani!', language: 'it', spec: itCast.body.speakers.grandma, engines: { eleven: new ElevenLabs({ key: 'test-key', base: fe.url, language: 'sl' }), gepard: new Gepard({ url: fg.url }) }, live: () => true })
  const notLive = await voiceInTown({ text: 'Ciao!', language: 'it', spec: itCast.body.speakers.grandma, engines: { eleven: new ElevenLabs({ key: 'test-key', base: visitEleven.url, language: 'sl' }) }, live: () => false })
  check("visit voices: … ElevenLabs down: no Italian voice on the node (null: the phone speaks it); over the live lines' rate: nothing asked", noneIt === null && fg.st.calls.length === 0 && notLive === null, { noneIt, gepard: fg.st.calls.length })
  // (a node whose own village is Italian, voicing a Slovene town's lone word in its male narrator's English voice)
  const ce = fakeEleven()
  const carrier = await voiceInTown({ text: 'kosilo', language: 'sl', spec: { gender: 'male', elevenlabs: 'onwK4e9ZLuTAKqWW03F9', carrier: true }, engines: { eleven: new ElevenLabs({ key: 'test-key', base: ce.url, language: 'it' }) }, live: () => true })
  const said = ce.st.tts.at(-1)
  check("visit voices: … a lone word of a carrier voice in the town's language's carrier sentence (with ffmpeg), else plainly", !!carrier && (said?.body.text === 'Beseda je: kosilo.' || said?.body.text === 'kosilo') && said?.body.language_code === 'sl', said?.body)

  // --- a host's limit: Luka's town hands a town two clips an hour (LANI_TOWN_VOICE_PER_HOUR=2) -----------------------
  const limited = []
  for (let i = 0; i < 3; i++) limited.push(await ask(luka, jan.key, { v: '1', text: `Ciao numero ${i}`, voice: 'grandma' }))
  check('visit voices: a town asks at most so many clips an hour: then 429 with retry-after', limited[0].res.status === 404 && limited[1].res.status === 404 && limited[2].res.status === 429 && limited[2].body?.code === 'rate_limited' && Number(limited[2].res.headers.get('retry-after')) > 0, limited.map(l => l.res.status))

  // --- pure: what another town sends is checked ---------------------------------------------------------------------------
  const id = 'f'.repeat(32)
  const audio = Buffer.from(mp3('ok')).toString('base64')
  const cleaned = {
    ok: !!cleanHostClip({ town: id, voice: 'grandma', engine: 'elevenlabs', audio }, id, ['grandma']),
    otherTown: cleanHostClip({ town: 'e'.repeat(32), voice: 'grandma', audio }, id, ['grandma']),
    notAsked: cleanHostClip({ town: id, voice: 'female', audio }, id, ['grandma']),
    notMp3: cleanHostClip({ town: id, voice: 'grandma', audio: Buffer.from('<html>').toString('base64') }, id, ['grandma']),
    notBase64: cleanHostClip({ town: id, voice: 'grandma', audio: '%%%' }, id, ['grandma']),
    tooBig: cleanHostClip({ town: id, voice: 'grandma', audio: Buffer.from(new Uint8Array(800 * 1024).fill(0xff)).toString('base64') }, id, ['grandma']),
  }
  check("visit voices: a host's clip is taken only for that town and a voice asked, an MP3 within 768 KB in base64", cleaned.ok && Object.entries(cleaned).every(([k, v]) => k === 'ok' || v === undefined), cleaned)
  const evil = cleanVisitCast({ town: id, language: 'it', culture: 'friuli', speakers: { grandma: { gender: 'female', elevenlabs: 'MLpDWJvrjFIdb63xbJp8', gepard: 'nina', denoise: 'boom', tempo: { elevenlabs: 9 } }, 'Bad Name': { gender: 'female' }, ghost: { gender: 'x' } }, player: { female: 'nobody', male: 'male' }, people: { rosa: { speaker: '@rosa', archetype: 'grandma', pitch: 5, rate: 0.1 }, '../x': { speaker: 'grandma', archetype: 'grandma' } } }, id)
  check("visit voices: another town's cast is cleaned: a bad speaker or person left out, an Italian town's local voices dropped, a filter or pace out of range dropped, the pitch held in 0.8–1.2",
    !!evil && Object.keys(evil.speakers).join() === 'grandma' && !evil.speakers.grandma.gepard && !evil.speakers.grandma.denoise && !evil.speakers.grandma.tempo && evil.player.female === 'female' && Object.keys(evil.people).join() === 'rosa' && evil.people.rosa.pitch === 1.2 && evil.people.rosa.rate === 0.8 && cleanVisitCast({ town: 'x', language: 'it' }, id) === undefined,
    evil)
  const kaernten = castFromPack(culturesDir, 'kaernten', 'de')
  const primorska = castFromPack(culturesDir, 'primorska', 'sl')
  check("visit voices: a town older than this: its culture pack's cast as this node has it (Kärnten's carrier narrator; Primorska's default cast in Slovene: Nina, ana for words)",
    !!kaernten?.speakers.male?.carrier && !kaernten.speakers.male.gepard && primorska?.speakers.female?.elevenlabs === PRIMORSKA.female.languages.sl.elevenlabs && primorska?.speakers.female?.gepard_words === 'ana' && primorska?.player.male === 'male' && castFromPack(culturesDir, '../x', 'sl') === undefined,
    { kaernten: kaernten?.speakers.male, primorska: primorska?.speakers.female })
  const people = { speakers: { grandma: { gender: 'female' as const, elevenlabs: 'MLpDWJvrjFIdb63xbJp8' }, female: { gender: 'female' as const }, male: { gender: 'male' as const } }, player: { female: 'female', male: 'male' }, people: { micka: { speaker: '@micka', archetype: 'grandma', pitch: 1, rate: 1 } } }
  const own2 = voicesFor('@micka', people)
  const player = voicesFor('player', people, { gender: 'male' })
  check("visit voices: the voices tried: someone's own, then their archetype's (voiced in it), then the narrators; the learner's own in theirs alone",
    own2.wanted.join() === '@micka,grandma' && own2.speaker === 'grandma' && own2.fallback.join() === 'female,male' && player.wanted.join() === 'male' && !player.fallback.length,
    { own2, player })

  // the store's own bounds: the least recently given out go beyond its size; a town no longer linked, all of its clips
  const dir = tempDir('lani-smoke-visit-clips-')
  const store = new VisitClips(dir, { maxBytes: 250 })
  const put = (town: string, n: number) => store.put({ town, language: 'sl', voice: 'grandma', norm: `line ${n}`, text: `Line ${n}`, from: 'host', engine: 'elevenlabs', bytes: new Uint8Array(100).fill(n) })
  const p1 = put('a'.repeat(32), 1)
  await Bun.sleep(5)
  put('a'.repeat(32), 2)
  await Bun.sleep(5)
  store.touch(p1)
  await Bun.sleep(5)
  put('b'.repeat(32), 3)
  const after = new VisitClips(dir, { maxBytes: 250 })
  check('visit voices: the store keeps within its size, the least recently given out first, and its index across restarts', !!after.get('a'.repeat(32), 'grandma', 'line 1') && !after.get('a'.repeat(32), 'grandma', 'line 2') && !!after.get('b'.repeat(32), 'grandma', 'line 3') && after.stats().bytes === 200, after.stats())
  after.forget(t => t === 'b'.repeat(32))
  check("visit voices: … a town no longer linked: its clips go", !after.get('a'.repeat(32), 'grandma', 'line 1') && after.stats().clips === 1, after.stats())
}
