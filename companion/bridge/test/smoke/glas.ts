// Smoke checks: the hosted voice service as the voice store's first engine (src/glas.ts): off unless set, asked first and kept in
// the store, and every way it can fail going on to the node's own engines, then the phone. Against a fake glas (test/fakes/glas.ts):
// glas's own suite pins the real answers.
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { elevenlabsOf } from '../../src/cast'
import { normalizeText } from '../../src/family'
import { Glas, glasFromEnv } from '../../src/glas'
import { ElevenLabs, ENGINE_RANK, engineIn, Gepard, VoiceStore, type Engine } from '../../src/voice'
import { fakeGlas } from '../fakes/glas'
import { check, fakeEleven, fakeGepard, tempDir, tone } from './harness'

const LINE = 'Dober dan, kako si danes?'

export default async function glas() {
  // --- off unless set ---
  {
    const none = glasFromEnv({})
    check('glas is off unless both its settings are there', !none.configured && !glasFromEnv({ GLAS_URL: 'http://x' }).configured && !glasFromEnv({ GLAS_KEY: 'k' }).configured && glasFromEnv({ GLAS_URL: 'http://x/', GLAS_KEY: 'k' }).configured)
    check('…off, it is never ready and voices nothing', !(await none.ready(1, true)) && (await none.synth(LINE, 'grandma')) === null)
    check('its rank is the ElevenLabs clips\', and it stands where ElevenLabs is asked for', ENGINE_RANK.glas === ENGINE_RANK.elevenlabs && engineIn('glas', ['elevenlabs']) && !engineIn('glas', ['gepard']) && !engineIn('elevenlabs', ['glas']))
  }

  const fg = fakeGlas({ bytes: () => tone(1) })
  const fe = fakeEleven()
  const lg = fakeGepard()
  const engine = (backoffMs = 60_000) => new Glas({ url: fg.url, key: 'glas_test', backoffMs })
  const store = (e: Engine, o: Partial<ConstructorParameters<typeof VoiceStore>[0]> = {}) =>
    new VoiceStore({ appDir: tempDir('lani-glas-'), engines: [e, new ElevenLabs({ key: 'test-key', base: fe.url }), new Gepard({ url: lg.url })], queueDelayMs: 0, level: null, ...o })
  const asked = () => fg.st.calls.filter(c => c.path.startsWith('/v1/clips')).length

  // --- asked first, kept in the store ---
  {
    const vs = store(engine())
    const tts = fe.st.tts.length
    const c = await vs.voice(LINE, 'grandma', 'live', { live: true })
    check('a live line: asked of glas first, with say, the node\'s ElevenLabs and worker not asked',
      c?.engine === 'glas' && fe.st.tts.length === tts && fg.st.calls.at(-2)?.path === '/v1/clips/say' && fg.st.calls.at(-2)?.body?.voice === 'grandma', c)
    check('…kept in the store as any clip: its file, its voice, its model, no characters of the node\'s',
      !!c && existsSync(join(vs.filesDir, c.file)) && c.engine_voice === elevenlabsOf('grandma') && c.model === 'eleven_v4_turbo' && c.chars === 0 && vs.index()[normalizeText(LINE)]?.grandma?.endsWith(c.file), c)
    const n = asked()
    const again = await vs.say(LINE, 'grandma')
    check('…the next ask is the store\'s: glas isn\'t asked again', again?.file === c?.file && asked() === n)
    check('…nothing about it wants making again: not stale, not an old-style clip, no upgrade', !!c && !vs.stale(c) && !vs.oldStyle(c) && !vs.wantsUpgrade(c) && !vs.wantsElevenLabsVoice(c))
    const made = await vs.voice('Ta vrstica ni živa, pripravi jo.', 'grandma', 'road')
    check('a line not live: resolve, never say', made?.engine === 'glas' && fg.st.calls.filter(x => x.path === '/v1/clips/resolve').length === 1)
  }

  // --- what glas doesn't have ---
  {
    fg.st.generate = false
    const vs = store(engine())
    const tts = fe.st.tts.length
    const c = await vs.voice('Tega v knjižnici ni nikjer.', 'grandma', 'live', { live: true })
    check('a key that may only read, a line the library lacks: the node\'s own engine says it', c?.engine === 'elevenlabs' && fe.st.tts.length === tts + 1, c)
    fg.st.generate = true
  }

  // --- someone's own voice, another voice, a lone word ---
  {
    const e = engine()
    const n = asked()
    check('someone\'s own voice is the node\'s: glas isn\'t asked', (await e.synth(LINE, '@micka')) === null && asked() === n)
    fg.st.voices.grandma = 'SomeoneElse000000000'
    const other = engine()
    check('a speaker glas has in another voice than the cast here: not used, nobody else says the line', (await other.synth(LINE, 'grandma')) === null && asked() === n)
    delete fg.st.voices.grandma
    const word = engine()
    await word.synth('kosilo', 'grandma', { carrier: true })
    check('a lone word in its carrier sentence: only one glas has, nothing is made for it', fg.st.calls.at(-1)?.items?.[0]?.make === false || fg.st.calls.at(-2)?.items?.[0]?.make === false, fg.st.calls.slice(-2))
  }

  // --- the Slovene narrators, an explanation ---
  {
    const vs = store(engine(), { narrators: 'elevenlabs', language: 'sl' })
    const c = await vs.voice('To je stavek za pripovedovalko.', 'female', 'live', { live: true })
    check('a Slovene narrator: glas\'s "female", kept as her Slovene voice', c?.engine === 'glas' && c.voice === 'female.sl' && fg.st.calls.some(x => x.body?.voice === 'female'), c)
    const x = await vs.voice('This explains the dative.', 'teacher', 'explanation', { lang: 'en' })
    check('an explanation: in its own language, under its own key', x?.engine === 'glas' && x.norm.startsWith('en|') && fg.st.calls.some(c => c.items?.[0]?.lang === 'en'), x)
  }

  // --- filter and pace: a clip as it came from ElevenLabs, a clip from a node's store ---
  {
    const filtered: string[] = []
    const denoise = async (b: Uint8Array, preset: string) => {
      filtered.push(preset)
      return b
    }
    const vs = store(engine(), { denoise })
    await vs.voice('Mlad mož to reče prvič.', 'young-man', 'live', { live: true })
    check('a voice that hisses: a clip as ElevenLabs made it goes through its filter', filtered.length === 1, filtered)
    fg.st.imported.add(`${normalizeText('Mlad mož to reče drugič.')}|young-man`)
    await vs.voice('Mlad mož to reče drugič.', 'young-man', 'live', { live: true })
    check('…one imported from a node has been through it', filtered.length === 1, filtered)
  }

  // --- a local clip, made in the voice when glas can ---
  {
    const vs = store(engine())
    const before = await vs.voice('Lokalni posnetek babice.', 'grandma', 'pack:x', { engines: ['gepard'] })
    check('a character\'s local clip wants its voice through glas too', before?.engine === 'gepard' && vs.wantsElevenLabsVoice(before), before)
    const up = before ? await vs.upgradeLocal(before) : undefined
    check('…and is replaced by glas\'s', up?.engine === 'glas', up)
  }

  // --- what goes wrong ---
  {
    const e = engine()
    const vs = store(e)
    fg.st.fail = 502
    const tts = fe.st.tts.length
    const c = await vs.voice('Glas je zdaj nedosegljiv.', 'grandma', 'live', { live: true })
    check('glas down: the node\'s own engine says the line', c?.engine === 'elevenlabs' && fe.st.tts.length === tts + 1, c)
    const n = fg.st.calls.length
    check('…and glas isn\'t asked for a while: no line waits on it', !(await e.ready(1, true)) && (await vs.voice('Še vedno je nedosegljiv.', 'grandma', 'live', { live: true }))?.engine === 'elevenlabs' && fg.st.calls.length === n)
    fg.st.fail = undefined
    const soon = engine(30)
    fg.st.fail = 500
    await soon.synth(LINE, 'grandma')
    fg.st.fail = undefined
    await Bun.sleep(40)
    check('…and is asked again after it', await soon.ready(1, true))

    const q = engine()
    fg.st.refuse = { code: 'quota_exceeded' }
    check('a quota used up: nothing from glas', (await q.synth('Dnevna kvota je porabljena.', 'grandma')) === null)
    fg.st.refuse = undefined
    const paused = (await q.status()) as { paused_until?: string; why?: string }
    const midnight = new Date(paused.paused_until ?? 0)
    check('…until the next UTC day', !(await q.ready(1, true)) && midnight.getUTCHours() === 0 && midnight.getUTCMinutes() === 0 && /daily quota/.test(paused.why ?? ''), paused)
    const m = engine()
    fg.st.refuse = { code: 'monthly_quota_exceeded' }
    await m.synth('Mesečna kvota je porabljena.', 'grandma')
    fg.st.refuse = undefined
    const pm = (await m.status()) as { paused_until?: string }
    check('a monthly cap used up: until the next month', new Date(pm.paused_until ?? 0).getUTCDate() === 1, pm)

    const wrong = new Glas({ url: fg.url, key: 'glas_wrong' })
    check('a wrong key: nothing, and it says so once', (await wrong.synth(LINE, 'grandma')) === null && !(await wrong.ready(1, true)))

    const nobody = store(new Glas({ url: 'http://127.0.0.1:9', key: 'glas_test' }), { engines: [new Glas({ url: 'http://127.0.0.1:9', key: 'glas_test' })] })
    check('nothing but glas, and glas gone: no clip, no error, the phone speaks', (await nobody.say('Nikogar ni.', 'grandma')) === null)
  }
  fg.stop()
}
