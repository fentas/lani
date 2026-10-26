// Smoke checks: the voice store, companion/bin/voice-build, and the voice routes.
import { Database } from 'bun:sqlite'
import { createHash } from 'node:crypto'
import { cpSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, utimesSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { validateScenario } from '../../src/scenarios'
import { validateModule } from '../../src/spec'
import { baseVoice, CAST_FILE, carrierVoice, cast, fallbacks, genderOf, isVoice, languageVoice, newcomerSpeaker, PLAYER, playerVoice, playerVoices, speakerFor, tempoOf, tempoVoices, voiceFor } from '../../src/cast'
import { addressee, voicedGender } from '../../src/addressee'
import { normalizeText } from '../../src/family'
import { charsPerSecond, clipPace, corpus, creditsFor, DEFAULT_ELEVENLABS_CONCURRENCY, DEFAULT_ELEVENLABS_MODEL, modelOf, denoisedFile, EDGES, edgesOf, ElevenLabs, enginesFromEnv, explanationLang, ffmpegCutter, ffmpegDenoiser, ffmpegEdges, ffmpegLeveller, ffmpegLoudness, ffmpegPace, ffmpegRecordingLeveller, Gepard, gepardPace, isMp3, isOneWord, isShortText, LEVEL, levelGain, sloveneNarrators, mp3Duration, mp3Kbps, paceChain, processedFile, DEFAULT_REDO_DAILY, Gate, redoDaily, revoiceDaily, trimChain, trimmable, VoiceStore, wordSpan, type Clip, type Cutter, type Denoiser, type Engine, type Leveller } from '../../src/voice'
import { describe, Profiles, sampleOf, SPARE_SLOTS, variationOf, wordMatch } from '../../src/profiles'
import { denoiseOf, denoiseVoices, elevenlabsOf, personVoice } from '../../src/cast'
import { postOffice } from './fixtures'
import { auth, base, bridgeEleven, bridgeGepard, check, client, culturesDir, dataDir, fakeEleven, fakeGepard, fam, guideOf, mp3, tempDir } from './harness'
import { JAN } from '../learner-content'

/** The node's ffmpeg: the carrier cut. Without it the carrier checks that need a real cut are skipped. */
const ffmpeg = ffmpegCutter()
if (!ffmpeg) console.log('⚠️  no ffmpeg here: the carrier checks with a real cut are skipped')

/**
 * A clip as the local worker makes them (with ffmpeg): 22.05 kHz, 64 kbit/s; 0.6 s of silence, 0.8 s of sound, a pause
 * of 1 s, 0.8 s of sound, 0.7 s of silence (3.9 s); the worker's floor of noise, at about −75 dBFS.
 */
const workerClip = () => {
  const part = (from: number, to: number) => `between(t,${from},${to})*0.12*sin(2*PI*180*t)*(0.55+0.45*sin(2*PI*4*t))`
  return new Uint8Array(Bun.spawnSync(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i', `aevalsrc='${part(0.6, 1.4)}+${part(2.4, 3.2)}+0.0003*(2*random(0)-1)':s=22050:d=3.9`, '-ac', '1', '-c:a', 'libmp3lame', '-b:a', '64k', '-f', 'mp3', 'pipe:1']).stdout)
}

export default async function voice() {
  // --- the gate before ElevenLabs: someone waiting goes ahead of the background work ---------------
  {
    const gate = new Gate(1)
    const order: string[] = []
    let release!: () => void
    const first = gate.run(() => new Promise<void>(r => (release = r)))
    const job = (name: string, urgent = false) => gate.run(async () => void order.push(name), urgent)
    const all = [job('re-voice a'), job('re-voice b'), job('live c', true), job('redo d', true), job('re-voice e')]
    check('the gate queues an urgent request (a live line, a re-record) ahead of the background work waiting, urgent ones in turn', gate.running === 1 && gate.queued === 5, { running: gate.running, queued: gate.queued })
    release()
    await Promise.all([first, ...all])
    check('… so they run: live c, redo d, then re-voice a, b, e; the gate empty after', order.join() === 'live c,redo d,re-voice a,re-voice b,re-voice e' && gate.running === 0 && gate.queued === 0, { order, running: gate.running, queued: gate.queued })
  }

  // --- voice store: engines, quota guard, order (in-process against the fakes) -------------------
  {
    const fe = fakeEleven()
    const fg = fakeGepard()
    fe.st.limit = 10_000
    fe.st.used = 4_900 // 5,100 left; 5,000 are kept for live lines
    const el = new ElevenLabs({ key: 'test-key', base: fe.url })
    check('quota guard: 5,000 characters kept for live lines by default', el.reserve === 5_000)
    check('quota guard: a prebuild within the reserve margin may go', await el.ready(50, false))
    check('quota guard: a prebuild that would dip into the reserve may not', !(await el.ready(150, false)))
    check('quota guard: a live line may use the reserve', await el.ready(150, true))
    check('quota guard: nothing beyond the quota', !(await el.ready(5_200, true)))
    check('subscription read once and cached', fe.st.subscriptionCalls === 1, fe.st.subscriptionCalls)

    const vs = new VoiceStore({ appDir: mkdtempSync(join(tmpdir(), 'lani-voice-')), engines: [el, new Gepard({ url: fg.url })], queueDelayMs: 0 })
    const a = await vs.voice('Dober dan!', 'female', 'test')
    const call = fe.st.tts[0]
    check('ElevenLabs first while the quota allows', a?.engine === 'elevenlabs' && fg.st.calls.length === 0, a)
    check('ElevenLabs request: eleven_v4_turbo (the default model), Slovenian, Matilda, mp3; the clip records its model', call?.voice === 'XrExE9yKIg1WjnnlVkGX' && call.body.model_id === 'eleven_v4_turbo' && a?.model === 'eleven_v4_turbo' && el.model === DEFAULT_ELEVENLABS_MODEL && call.body.language_code === 'sl' && call.body.text === 'Dober dan!' && call.query === '?output_format=mp3_44100_128', call)
    check('clip stored as <sha1>.mp3 with its row', !!a && /^[a-f0-9]{40}\.mp3$/.test(a.file) && !!vs.path(a.file) && a.chars === 10 && a.source === 'test' && a.key === 'female:dober dan', a)
    const again = await vs.voice('  dober DAN ', 'female', 'test')
    check('an existing clip is reused (no engine call)', again?.file === a?.file && fe.st.tts.length === 1)
    const long = 'Danes je lep dan, zato gremo na sprehod ob morju, potem pa na kosilo k babici Micki, ki kuha odlično juho.'
    const lg = await vs.voice(long, 'female', 'test')
    check('over the prebuild budget: the local worker', lg?.engine === 'gepard' && fe.st.tts.length === 1 && fg.st.calls.length === 1, lg)
    const live = await vs.say(`${long} Pa še sladica.`, 'male')
    check('a live line may use the reserve (male: Daniel)', live?.engine === 'elevenlabs' && fe.st.tts.at(-1)?.voice === 'onwK4e9ZLuTAKqWW03F9', live)
    fg.st.busy = true
    check('over budget and the worker busy: no clip, no throw', (await vs.voice(`${long} Tretjič.`, 'female', 'test')) === null)
    fg.st.busy = false
    fe.st.fail = 500
    const fb = await vs.voice('Kruh je topel.', 'female', 'test')
    check('ElevenLabs failing: falls back to the worker', fb?.engine === 'gepard', fb)
    const logged: string[] = []
    const leaky = new ElevenLabs({ key: 'test-key', base: fe.url, log: (...a) => void logged.push(a.join(' ')) })
    await leaky.status() // reads the quota first; an error then stays in the status
    fe.st.fail = 400
    fe.st.echoKey = true // an error body that repeats the key
    await leaky.synth('Živjo.', 'female')
    const leakStatus = JSON.stringify(await leaky.status())
    check('ElevenLabs errors are logged and shown without the key', leakStatus.includes('[redacted]') && !leakStatus.includes('test-key') && logged.some(l => l.includes('[redacted]')) && !logged.some(l => l.includes('test-key')), [leakStatus, logged])
    fe.st.echoKey = false
    fe.st.fail = 0
    const dead = new VoiceStore({ appDir: mkdtempSync(join(tmpdir(), 'lani-voice-')), engines: [new ElevenLabs({}), new Gepard({ url: 'http://127.0.0.1:9' })] })
    check('no key and no worker: null, never throws', (await dead.voice('Hvala.', 'female', 'test')) === null)
    const idx = vs.index()
    check('index: normalized text → voice → URL', idx['dober dan']?.female === `/voice/file/${a?.file}` && !idx['dober dan'].male, idx)
    check('status lists clips per engine, and the cast', JSON.stringify((await vs.status()).clips).includes('gepard') && (await vs.status()).cast.some((c: any) => c.id === 'grandma' && c.gender === 'female'))

    // --- speakers: the voice cast (companion/voice-cast.json) --------------------------------------
    const c = cast()
    check('cast: the narrators are Matilda and Daniel, each character has a gender, an ElevenLabs voice and a Gepard one', c.female?.elevenlabs === 'XrExE9yKIg1WjnnlVkGX' && c.male?.elevenlabs === 'onwK4e9ZLuTAKqWW03F9' && Object.values(c).every(s => ['female', 'male'].includes(s.gender) && !!s.elevenlabs && !!s.gepard), Object.keys(c))
    check('cast: fallbacks are the gender, then the other narrator', JSON.stringify(fallbacks('grandma')) === '["female","male"]' && JSON.stringify(fallbacks('boy')) === '["male","female"]' && JSON.stringify(fallbacks('male')) === '["female"]' && JSON.stringify(fallbacks('female')) === '["male"]')
    check('cast: a villager\'s speaker, else their gender; newcomers by age and gender', speakerFor({ voice: 'female', speaker: 'grandma' }) === 'grandma' && speakerFor({ voice: 'male', speaker: 'nobody' }) === 'male' && speakerFor({ voice: 'male' }) === 'male' && newcomerSpeaker('female', false) === 'young-woman' && newcomerSpeaker('male', false) === 'young-man' && newcomerSpeaker('female', true) === 'girl' && newcomerSpeaker('male', true) === 'boy')
    const tts0 = fe.st.tts.length
    const gm = await vs.voice('Dober dan!', 'grandma', 'test', { live: true }) // the prebuild budget is spent: as a live line
    check('a character speaker is its own clip, with the cast\'s ElevenLabs voice (grandma: Zlata)', gm?.engine === 'elevenlabs' && gm.key === 'grandma:dober dan' && gm.file !== a?.file && fe.st.tts[tts0]?.voice === c.grandma.elevenlabs, gm)
    check('index: the speaker next to the narrator', vs.index()['dober dan']?.grandma === `/voice/file/${gm?.file}` && vs.index()['dober dan']?.female === `/voice/file/${a?.file}`, vs.index()['dober dan'])
    check('a voice not in the cast: no clip, no engine call', (await vs.voice('Dober dan!', 'soprano', 'test')) === null && fe.st.tts.length === tts0 + 1)
    // Over the prebuild budget: a character whose narrator clip exists waits for ElevenLabs (no local clip); a new text gets the cast's Gepard voice.
    const gp0 = fg.st.calls.length
    check('over budget, the narrator clip there: the character waits for ElevenLabs', (await vs.voice(long, 'grandma', 'test')) === null && fg.st.calls.length === gp0 && vs.waitsForElevenLabs(normalizeText(long), 'grandma') && !vs.waitsForElevenLabs(normalizeText(long), 'grandpa'), fg.st.calls.slice(gp0))
    const girl = await vs.voice(`${long} Juhu!`, 'girl', 'test')
    check('over budget, a new text: the local worker with the cast\'s voice for the speaker (girl: nina)', girl?.engine === 'gepard' && fg.st.calls.at(-1)?.speaker === 'nina', [girl, fg.st.calls.at(-1)])
    const quiet = new VoiceStore({ appDir: dirname(vs.dir), engines: [] }) // the same clips, no engine
    const toMale = await quiet.say(`${long} Pa še sladica.`, 'grandpa')
    const toFemale = await quiet.say(long, 'grandpa')
    check('a live character line nobody can make: the gender narrator\'s clip, then the other one', toMale?.voice === 'male' && toFemale?.voice === 'female' && toFemale.file === lg?.file, [toMale, toFemale])
    // the learner's own line (the voice "player", said in the male narrator's voice for Jan): only in their voice; nobody
    // else's clip stands in (the app speaks it otherwise)
    const alone = await quiet.say(long, 'male', undefined, { alone: true })
    check("the learner's own line nobody can make in their voice: no other voice's clip (alone), where any other line gets the other narrator's", alone === null && (await quiet.say(long, 'male'))?.voice === 'female', alone)
    quiet.close()
    // Swapping a voice is one line in the cast; --revoice makes the speaker's clips again, under new names.
    const swapped = join(mkdtempSync(join(tmpdir(), 'lani-cast-')), 'voice-cast.json')
    const castJson = JSON.parse(readFileSync(CAST_FILE, 'utf8'))
    castJson.speakers.grandma.elevenlabs = 'nEu1CRXDjHpQT7G4E6sZ'
    writeFileSync(swapped, JSON.stringify(castJson))
    process.env.LANI_VOICE_CAST = swapped
    const re = await vs.voice('Dober dan!', 'grandma', 'test', { engines: ['elevenlabs'], replace: true, force: true, live: true })
    delete process.env.LANI_VOICE_CAST
    check('a swapped voice: --revoice replaces the clip with the new voice, under a new name', re?.engine === 'elevenlabs' && re.file !== gm?.file && fe.st.tts.at(-1)?.voice === 'nEu1CRXDjHpQT7G4E6sZ' && !vs.path(gm!.file) && !!vs.path(re.file), [re, fe.st.tts.at(-1)])
    check('the cast file is read again when it changes', cast().grandma.elevenlabs === c.grandma.elevenlabs)

    // --- the player: who says the learner's own lines (the cast's `player`, by the learner's gender) ------------------
    check("the cast's player: Luka's and Nina's voices (the narrators' Slovene ones), as the voices the store speaks in (male, female); the female one when the profile says no gender",
      JSON.stringify(playerVoices()) === JSON.stringify({ female: 'female.sl', male: 'male.sl' }) && playerVoice('male') === 'male' && playerVoice('female') === 'female' && playerVoice() === 'female' &&
        voiceFor(PLAYER, 'male') === 'male' && voiceFor(PLAYER) === 'female' && voiceFor('grandma', 'male') === 'grandma' && languageVoice(playerVoice('male'), 'sl') === 'male.sl' && !isVoice(PLAYER),
      [playerVoices(), playerVoice('male'), playerVoice()])
    check("the learner's gender for their voice: the profile's, none when it says none (spoken to as a man all the same)",
      voicedGender(JAN) === 'male' && voicedGender(addressee({ name: 'Ana', gender: 'F' })) === 'female' && voicedGender(addressee({ name: 'Kim' })) === undefined && addressee({ name: 'Kim' }).gender === 'male' && addressee({ name: 'Kim', gender: '{GENDER}' }).unsaid === true && JAN.unsaid === undefined)
    // a voice of the learner's own: one line in the cast's player (a speaker of the cast); a wrong one keeps the narrator
    const ownPlayer = join(mkdtempSync(join(tmpdir(), 'lani-cast-')), 'voice-cast.json')
    writeFileSync(ownPlayer, JSON.stringify({ ...castJson, player: { _comment: 'x', male: 'grandpa', female: 'nobody', other: 'female' } }))
    const castErrors: string[] = []
    const consoleError = console.error
    console.error = (...a: unknown[]) => void castErrors.push(a.join(' '))
    process.env.LANI_VOICE_CAST = ownPlayer
    const swappedPlayer = [playerVoice('male'), playerVoice('female'), playerVoice()]
    delete process.env.LANI_VOICE_CAST
    console.error = consoleError
    check("the player swapped in the cast is one line (male: grandpa); a voice not in the cast, or no gender, is logged and that gender keeps its narrator",
      JSON.stringify(swappedPlayer) === JSON.stringify(['grandpa', 'female', 'female']) && castErrors.some(e => e.includes('player female') && e.includes('nobody')) && castErrors.some(e => e.includes('player "other"')) && playerVoice('male') === 'male',
      [swappedPlayer, castErrors])

    // The corpus and companion/bin/voice-build on a fixture (fresh data dir, own content dirs).
    const vbPacks = mkdtempSync(join(tmpdir(), 'lani-vb-packs-'))
    const vbModules = mkdtempSync(join(tmpdir(), 'lani-vb-modules-'))
    const vbScenarios = mkdtempSync(join(tmpdir(), 'lani-vb-scenarios-'))
    const vbData = mkdtempSync(join(tmpdir(), 'lani-vb-data-'))
    const vbWords = ['Kako ste? / Kako si?', 'kruh', 'mleko', 'sir', 'jajce', 'juha', 'vilice', 'nož'].map((sl, i) => ({ id: `w${i}`, sl, en: 'x' }))
    Object.assign(vbWords[1], { example_sl: 'Kruh je še topel.' })
    Object.assign(vbWords[2], { example_sl: 'Mleko je hladno.' })
    Object.assign(vbWords[3], { example_sl: 'Kruh je še topel.' }) // a duplicate: voiced once
    const vbPack = { schema: 'lani.pack/v0', id: 'vb-pack', title: 'VB', level: 'A1', words: vbWords }
    const vbModule = {
      schema: 'lani.module/v0', id: 'vb-mod', title: 'VB', level: 'A1',
      exercises: [
        { type: 'flashcard', front: 'Dobro jutro', back: 'Good morning' },
        { type: 'flashcard', front: 'Lahko noč', back: 'Good night', speak: false },
        { type: 'choice', prompt: 'hvala', say: 'hvala', options: ['thanks', 'please'], answer: 0 },
        { type: 'choice', prompt: 'What did you hear?', audio: 'Dober večer.', options: ['sem', 'si'], answer: 0, say_options: true },
        { type: 'multi', prompt: 'Greetings?', options: ['Dober dan', 'Živjo', 'Oj'], answers: [0], say_options: true },
        { type: 'reorder', prompt: 'My name is Jan.', say: 'Imenujem se Jan.', tokens: ['Imenujem', 'se', 'Jan.'], solutions: [['Imenujem', 'se', 'Jan.']] },
        { type: 'translate', prompt: 'bread', say: 'kruh', accept: ['kruh'] },
        { type: 'scenario', scene: 'Bakery', line: 'Dober dan! Kaj bo?', prompt: 'Order', accept: ['Kruh, prosim.'] },
        { type: 'dictation', audio: 'Dober večer.', accept: ['Dober večer.'] },
        { type: 'speak', say: 'Rad bi kavo.', prompt: 'Order a coffee.' },
        { type: 'cloze', text: 'Jaz ___ Jan.', accept: ['sem'] },
      ],
    }
    const vbScenario = { ...postOffice, id: 'vb-scene', voice: 'male', vocabulary_hints: [{ sl: 'Kruh, prosim.', en: 'x' }, { sl: 'kruh', en: 'bread' }] }
    const vbm = validateModule(vbModule)
    const vbs = validateScenario(vbScenario)
    check('fixture validates', vbm.ok && vbs.ok)
    writeFileSync(join(vbPacks, 'vb-pack.json'), JSON.stringify(vbPack))
    writeFileSync(join(vbModules, 'vb-mod.json'), JSON.stringify(vbModule))
    writeFileSync(join(vbScenarios, 'vb-scene.json'), JSON.stringify(vbScenario))
    const items = corpus({ learner: JAN, packs: [vbPack as any], modules: vbm.ok ? [vbm.spec] : [], scenarios: vbs.ok ? [vbs.scenario] : [] })
    const texts = (p: number) => items.filter(i => i.priority === p).map(i => i.text)
    check('corpus: pack words, split phrases', JSON.stringify(texts(1)) === JSON.stringify(['Kako ste?', 'Kako si?', 'kruh', 'mleko', 'sir', 'jajce', 'juha', 'vilice', 'nož']), texts(1))
    check('corpus: examples, deduplicated', JSON.stringify(texts(2)) === JSON.stringify(['Kruh je še topel.', 'Mleko je hladno.']), texts(2))
    check('corpus: module say/audio/line/front/options, once each', JSON.stringify(texts(3)) === JSON.stringify(['Dobro jutro', 'hvala', 'Dober večer.', 'sem', 'si', 'Dober dan', 'Živjo', 'Oj', 'Imenujem se Jan.', 'Dober dan! Kaj bo?', 'Rad bi kavo.']), texts(3))
    check('corpus: scenario opener in the character voice, hints female', JSON.stringify(items.filter(i => i.priority === 4).map(i => [i.text, i.voice])) === JSON.stringify([['Dober dan! Izvolite?', 'male'], ['Kruh, prosim.', 'female']]), items.filter(i => i.priority === 4))

    // A villager: 16 lines, the 2 remember lines carry {memory} and are not prebuilt, so 14 texts in his voice.
    const vbVillagers = mkdtempSync(join(tmpdir(), 'lani-vb-villagers-'))
    const vl = (sl: string, en: string, level = 0) => ({ sl, en, level })
    writeFileSync(join(vbVillagers, 'vb-dedek.json'), JSON.stringify({
      schema: 'lani.villager/v0', id: 'vb-dedek', name: 'Dedek Vinko', art: 'grandpa', speaker: 'grandpa', role: 'Dedek · Grandfather', home: ['spot:highseat'], register: 'vi',
      lines: {
        greet: [vl('Dober dan, mladi mož.', 'Good day, young man.'), vl('O, Jan, sedi.', 'Oh, Jan, sit.', 2)],
        thanks: [vl('Hvala, Jan.', 'Thanks, Jan.'), vl('Dobro delo.', 'Good work.')],
        remember: [vl('Še vedno mislim na {memory}.', 'I still think of {memory}.', 1), vl('Ne pozabim na {memory}.', 'I do not forget {memory}.', 2)],
        idle: [vl('Nekoč je bil sneg do strehe.', 'Once the snow reached the roof.'), vl('Muri, kje si?', 'Muri, where are you?')],
        cheer: [vl('Tako je.', 'That is right.'), vl('Bravo, Jan.', 'Well done, Jan.')],
        comfort: [vl('Počasi. Še enkrat.', 'Slowly. Once more.'), vl('Nič hudega, Jan.', 'Never mind, Jan.')],
        listen: [vl('Poslušaj, Jan.', 'Listen, Jan.'), vl('Tiho. Poslušaj.', 'Quiet. Listen.')],
        bye: [vl('Lahko noč, Jan.', 'Good night, Jan.'), vl('Pojdi, pojdi.', 'Go, go.')],
      },
    }))
    fe.st.used = fe.st.limit - 5_000 - 30 // 30 characters above the reserve
    // the village's culture pack as a manifest alone: its own packs and scenes aren't this corpus's
    const vbCultures = mkdtempSync(join(tmpdir(), 'lani-vb-cultures-'))
    mkdirSync(join(vbCultures, 'primorska'))
    cpSync(join(culturesDir, 'primorska/culture.json'), join(vbCultures, 'primorska/culture.json'))
    // the narrators speak Slovene with the local voice first here (LANI_VOICE_NARRATOR_SL=gepard, as from 10 October 2026);
    // with their Slovene ElevenLabs voices first (the default) further below
    const vbEnv = { ...process.env, LANI_CULTURES_DIR: vbCultures, LANI_DATA_DIR: vbData, LANI_PACKS_DIR: vbPacks, LANI_MODULES_DIR: vbModules, LANI_SCENARIOS_DIR: vbScenarios, LANI_SCENES_DIR: mkdtempSync(join(tmpdir(), 'lani-vb-scenes-')), LANI_DRILLS_DIR: mkdtempSync(join(tmpdir(), 'lani-vb-drills-')), LANI_GRAMMAR_DIR: mkdtempSync(join(tmpdir(), 'lani-vb-grammar-')), LANI_VILLAGERS_DIR: vbVillagers, LANI_KEYS_FILE: '/nonexistent', ELEVENLABS_API_KEY: 'test-key', ELEVENLABS_BASE_URL: fe.url, LANI_GEPARD_URL: fg.url, LANI_VOICE_NARRATOR_SL: 'gepard' } as Record<string, string>
    // Async: the fake engines run in this process and must keep answering.
    const voiceBuildIn = async (env: Record<string, string>, ...args: string[]) => {
      const p = Bun.spawn(['bun', resolve(import.meta.dir, '../../../bin/voice-build'), ...args], { env, stdout: 'pipe', stderr: 'pipe' })
      const [stdout, stderr, exitCode] = await Promise.all([new Response(p.stdout).text(), new Response(p.stderr).text(), p.exited])
      return { stdout, stderr, exitCode }
    }
    const voiceBuild = (...args: string[]) => voiceBuildIn(vbEnv, ...args)
    const ttsBefore = fe.st.tts.length
    const gpBefore = fg.st.calls.length
    const dry = await voiceBuild('--json')
    const plan = JSON.parse(dry.stdout || '{}')
    check('voice-build dry run counts the corpus, the villager first, in his speaker', dry.exitCode === 0 && plan.texts === 38 && plan.missing === 38 && plan.cached === 0 && JSON.stringify(plan.rows?.map((r: any) => r.texts)) === '[14,9,2,11,2,0,0,0,0,0,0,0]' && plan.rows[0].label === 'villagers' && plan.by_voice?.grandpa?.texts === 14, plan)
    check('voice-build dry run reads the quota and the worker', plan.quota?.remaining === 5_030 && plan.quota.reserve === 5_000 && plan.gepard_up === true, plan.quota)
    check('voice-build plans ElevenLabs within the budget, the worker for the rest', plan.would?.elevenlabs.chars > 0 && plan.would.elevenlabs.chars <= 30 && plan.would.elevenlabs.texts + plan.would.gepard.texts === 38 && plan.would.later.texts === 0, plan.would)
    check('voice-build dry run synthesizes nothing and writes no DB', fe.st.tts.length === ttsBefore && fg.st.calls.length === gpBefore && !existsSync(join(vbData, 'app/voice/voice.db')))
    const human = await voiceBuild()
    check('voice-build dry run prints a table, the stories and the drills by level: A1 before the scenes, the others after them', human.exitCode === 0 && human.stdout.includes('pack words') && /scenarios[\s\S]*stories A1[\s\S]*drills A1[\s\S]*scenes[\s\S]*stories A2[\s\S]*drills A2\+[\s\S]*stories B1\+/.test(human.stdout) && human.stdout.includes('Dry run'), human.stdout)
    const ran = await voiceBuild('--run')
    const ranOut = ran.stdout + ran.stderr
    check('voice-build --run generates in priority order, the villager first in his speaker\'s voice', ran.exitCode === 0 && fe.st.tts.length - ttsBefore === plan.would.elevenlabs.texts && fg.st.calls.length - gpBefore === plan.would.gepard.texts && fe.st.tts[ttsBefore]?.body.text === 'Dober dan, mladi mož.' && fe.st.tts[ttsBefore]?.voice === cast().grandpa.elevenlabs && fg.st.calls[gpBefore]?.speaker === 'marko' && fg.st.calls.at(-1)?.text === 'Kruh, prosim.' && fg.st.calls.at(-1)?.speaker === 'nina', ranOut)
    check('voice-build never prints the key', !ranOut.includes('test-key'))
    const after = JSON.parse((await voiceBuild('--json')).stdout || '{}')
    check('after --run everything is cached', after.cached === 38 && after.missing === 0 && after.by_engine?.elevenlabs === plan.would.elevenlabs.texts, after)
    check('voice-build --revoice refuses a speaker not in the cast', (await voiceBuild('--revoice', 'soprano')).exitCode === 2)
    fe.st.used = fe.st.limit - 5_000 - 1_000
    const rev = JSON.parse((await voiceBuild('--json', '--revoice', 'grandpa')).stdout || '{}')
    check('voice-build --revoice plans every clip of that speaker again with ElevenLabs', rev.would?.elevenlabs.texts === 14 && rev.would.elevenlabs.replace === 14 && rev.would.gepard.texts === 0, rev.would)
    check('voice-build\'s plan: the model new clips are made with, and the clips by model', after.models?.now === 'eleven_v4_turbo' && after.models.by?.eleven_v4_turbo?.clips === plan.would.elevenlabs.texts && Object.keys(after.models.by).length === 1, after.models)
    // the node set to eleven_v4: the dry run of --upgrade-model lists them, nothing sent; --run makes the most played again
    const v4Env = { ...vbEnv, LANI_ELEVENLABS_MODEL: 'eleven_v4' }
    const n0 = fe.st.tts.length
    const upDry = await voiceBuildIn(v4Env, '--upgrade-model', '--json')
    const up = JSON.parse(upDry.stdout || '{}')
    const upHuman = await voiceBuildIn(v4Env, '--upgrade-model')
    check('voice-build --upgrade-model (dry run): the clips of an older model, by model, what --run would make within the budget and its credits; nothing sent, nothing written',
      upDry.exitCode === 0 && up.model === 'eleven_v4' && up.older === plan.would.elevenlabs.texts && up.from?.eleven_v4_turbo?.clips === up.older && up.would?.clips > 0 && up.would.chars <= 1_000 && up.would.credits === up.would.chars &&
        upHuman.stdout.includes('Dry run') && upHuman.stdout.includes('--upgrade-model --run') && fe.st.tts.length === n0, [up, upHuman.stdout.slice(0, 600)])
    const upRun = await voiceBuildIn(v4Env, '--upgrade-model', '--run', '--max', '1')
    const upAfter = JSON.parse((await voiceBuildIn(v4Env, '--json')).stdout || '{}')
    check('voice-build --upgrade-model --run --max 1: one made again by eleven_v4 (in the request and the store), the rest wait; the key never printed',
      upRun.exitCode === 0 && fe.st.tts.length === n0 + 1 && fe.st.tts.at(-1)?.body.model_id === 'eleven_v4' && upAfter.models?.by?.eleven_v4?.clips === 1 && upAfter.models.by.eleven_v4_turbo?.clips === plan.would.elevenlabs.texts - 1 && !(upRun.stdout + upRun.stderr).includes('test-key'),
      [upRun.stdout.slice(-400), upRun.stderr.slice(-400), upAfter.models])
    const staleHuman = await voiceBuildIn(v4Env)
    check('voice-build\'s plan: the stale clips (a swapped voice: none; an older model: the rest, none asked for yet) and what making them all again costs (eleven_v4: a credit a character)',
      upAfter.stale?.voice.clips === 0 && upAfter.stale.model.clips === plan.would.elevenlabs.texts - 1 && upAfter.stale.model.asked === 0 && upAfter.stale.model.lazy === true &&
        upAfter.stale.all.chars > 0 && upAfter.stale.all.credits === upAfter.stale.all.chars && /Stale clips[^\n]*a swapped voice 0 \/ asked 0; an older model \d+ \/ asked 0/.test(staleHuman.stdout),
      [upAfter.stale, staleHuman.stdout.split('\n').filter(l => l.startsWith('Stale'))])

    // A village in another language (a copy of friuli): the local worker speaks only Slovene, so what the ElevenLabs
    // budget (1,000 here) doesn't cover waits for later; a voice cast without voices plans nothing (the phone speaks).
    const itCultures = mkdtempSync(join(tmpdir(), 'lani-vb-it-'))
    cpSync(join(culturesDir, 'friuli'), join(itCultures, 'friuli'), { recursive: true })
    const itCast = JSON.parse(readFileSync(join(culturesDir, 'friuli/voice-cast.json'), 'utf8'))
    const itCastWith = (id: string) =>
      writeFileSync(join(itCultures, 'friuli/voice-cast.json'), JSON.stringify({ ...itCast, speakers: Object.fromEntries(Object.entries(itCast.speakers).map(([k, s]) => [k, { ...(s as object), elevenlabs: id }])) }))
    // the pack's own content, as a friuli learner's voice-build has it: none of the Slovene fixtures above
    const { LANI_VILLAGERS_DIR: _v, LANI_PACKS_DIR: _p, LANI_MODULES_DIR: _m, LANI_SCENARIOS_DIR: _s, LANI_SCENES_DIR: _c, LANI_VOICE_CAST: _x, ...vbRest } = vbEnv
    const itEnv = { ...vbRest, LANI_CULTURE: 'friuli', LANI_CULTURES_DIR: itCultures, LANI_DATA_DIR: mkdtempSync(join(tmpdir(), 'lani-vb-it-data-')) }
    itCastWith('ItalianVoice00000001')
    const itDry = await voiceBuildIn(itEnv, '--json')
    const itPlan = JSON.parse(itDry.stdout || '{}')
    check(
      'voice-build for an Italian village: ElevenLabs within the budget, the rest later, never the local worker (Slovene only)',
      itDry.exitCode === 0 && itPlan.missing > 0 && itPlan.local === false && itPlan.gepard_up === false && itPlan.would.gepard.texts === 0 && itPlan.would.elevenlabs.chars > 0 && itPlan.would.elevenlabs.chars <= 1_000 && itPlan.would.elevenlabs.texts + itPlan.would.later.texts === itPlan.missing,
      [itDry.stderr.slice(-300), itPlan.would],
    )
    const itHuman = await voiceBuildIn(itEnv)
    check('voice-build for an Italian village says the local worker is not for it', itHuman.stdout.includes('Local Gepard worker: not for this village'), itHuman.stdout)
    itCastWith('')
    const itNone = JSON.parse((await voiceBuildIn(itEnv, '--json')).stdout || '{}')
    check('voice-build for a village whose voice cast has no voices plans nothing: the phone speaks', itNone.would?.elevenlabs.texts === 0 && itNone.would.gepard.texts === 0 && itNone.would.later.texts === itNone.missing, itNone.would)

    // A store from before the carrier sentence, in a village whose narrators speak with their English voices (a German
    // village here, in the same voice store: the cast's Matilda, the carrier sentence "Das Wort ist: …"; in a Slovene
    // village no narrator speaks with an English voice): the narrator words said plainly by ElevenLabs (no ffmpeg then).
    const deCultures = mkdtempSync(join(tmpdir(), 'lani-vb-de-'))
    mkdirSync(join(deCultures, 'primorska'))
    writeFileSync(join(deCultures, 'primorska/culture.json'), JSON.stringify({ ...JSON.parse(readFileSync(join(culturesDir, 'primorska/culture.json'), 'utf8')), language: 'de' }))
    for (const [dir, f] of [[vbPacks, 'vb-pack.json'], [vbModules, 'vb-mod.json'], [vbScenarios, 'vb-scene.json']] as const) {
      mkdirSync(join(dir, 'de'), { recursive: true })
      cpSync(join(dir, f), join(dir, 'de', f))
    }
    const deEnv = { ...vbEnv, LANI_CULTURES_DIR: deCultures }
    const deBuild = (...args: string[]) => voiceBuildIn(deEnv, ...args)
    if (ffmpeg) {
      const plainly = await voiceBuildIn({ ...deEnv, LANI_FFMPEG: 'off' }, '--run', '--upgrade')
      const words = ['kruh', 'mleko', 'sir', 'jajce', 'juha', 'vilice', 'nož', 'Dobro jutro', 'hvala', 'sem', 'si', 'Dober dan', 'Živjo', 'Oj']
      const wordChars = words.reduce((n, w) => n + `Das Wort ist: ${w}.`.length, 0)
      const tsBefore = fe.st.tts.length
      const oldPlan = JSON.parse((await deBuild('--json', '--revoice-words', '--max', '3')).stdout || '{}')
      check('voice-build: the plan counts the old-style narrator words (said plainly) and what re-voicing them all costs (a German village: its carrier sentence)', plainly.exitCode === 0 && oldPlan.carrier?.old === words.length && oldPlan.carrier.old_chars === wordChars && oldPlan.carrier.carrier === 0 && oldPlan.carrier.daily === 40 && oldPlan.carrier.cut === true, [plainly.stderr.slice(-400), oldPlan.carrier])
      check('voice-build --revoice-words --max 3: the dry run plans three, the rest later, and voices nothing', oldPlan.revoice_words?.texts === 3 && oldPlan.revoice_words.later.texts === words.length - 3 && fe.st.tts.length === tsBefore, oldPlan.revoice_words)
      const shown = await deBuild('--revoice-words')
      check('voice-build prints the old-style words, what re-voicing them costs, and how to', shown.exitCode === 0 && shown.stdout.includes(`${words.length} old-style`) && shown.stdout.includes('re-voicing all old-style') && shown.stdout.includes('voice-build --revoice-words --run'), shown.stdout)
      check('voice-build --max needs --revoice-words', (await voiceBuild('--max', '3')).exitCode === 2)
      const rw = await deBuild('--revoice-words', '--max', '3', '--run')
      const after2 = JSON.parse((await deBuild('--json')).stdout || '{}')
      check('voice-build --revoice-words --max 3 --run: three words again in their carrier sentence, counted for the day', rw.exitCode === 0 && fe.st.tts.length - tsBefore === 3 && fe.st.tts.slice(tsBefore).every(c => c.timestamps && c.body.text.startsWith('Das Wort ist: ') && c.body.language_code === 'de') && after2.carrier?.old === words.length - 3 && after2.carrier.carrier === 3 && after2.carrier.today === 3, [rw.stdout + rw.stderr, after2.carrier])
    }

    // The narrators' local clips in their local voice (female: ana for a lone word, nina for a sentence; male: marko):
    // voice-build --narrators on this store, whose narrator clips are ElevenLabs' English ones now (said plainly or in
    // their carrier sentence, above), with one female lone word in nina (made before Jan wanted ana for words)
    {
      const vbLocal = vbEnv
      const nb = (...args: string[]) => voiceBuildIn(vbLocal, ...args)
      const vdb = () => new Database(join(vbData, 'app/voice/voice.db'))
      // one more: a narrator clip from another source than this village's corpus (another village's pack): left as it is
      const foreign = `${createHash('sha1').update('smoke|foreign').digest('hex')}.mp3`
      writeFileSync(join(vbData, 'app/voice/files', foreign), mp3('el:foreign'))
      const ninaWord = `${createHash('sha1').update('smoke|nina|word').digest('hex')}.mp3`
      writeFileSync(join(vbData, 'app/voice/files', ninaWord), mp3('gp:nina:žlica'))
      const ninaLine = `${createHash('sha1').update('smoke|nina|line').digest('hex')}.mp3`
      writeFileSync(join(vbData, 'app/voice/files', ninaLine), mp3('gp:nina:Juha je vroča.'))
      const d0 = vdb()
      const insert = d0.query('INSERT OR REPLACE INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, engine_voice, hits) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)')
      insert.run('female:buongiorno', 'buongiorno', 'Buongiorno', 'female', 'elevenlabs', foreign, 10, '2026-09-01T10:00:00.000Z', 'pack:it-saluti', null, 0)
      insert.run('female:žlica', 'žlica', 'žlica', 'female', 'gepard', ninaWord, 5, '2026-10-10T10:00:00.000Z', 'pack:vb-pack', 'nina', 2)
      insert.run('female:juha je vroča', 'juha je vroča', 'Juha je vroča.', 'female', 'gepard', ninaLine, 14, '2026-10-10T10:00:00.000Z', 'pack:vb-pack', 'nina', 0)
      const narratorRows = () => (vdb().query("SELECT * FROM clips WHERE voice IN ('female', 'male')").all() as Clip[])
      const before = narratorRows()
      d0.close()
      // the local voice a narrator's clip is made in: female ana for a lone word, nina for a sentence; male marko
      const want = (c: Clip) => (c.voice === 'female' ? (isShortText(c.text) ? 'ana' : 'nina') : 'marko')
      const english = before.filter(c => c.engine === 'elevenlabs' && c.key !== 'female:buongiorno')
      const local = before.filter(c => c.engine === 'gepard' && (c.engine_voice ?? (c.voice === 'female' ? 'ana' : 'marko')) !== want(c))
      const ndry = JSON.parse((await nb('--narrators', '--json')).stdout || '{}')
      const nhuman = await nb('--narrators')
      check('voice-build --narrators (dry run): the narrators\' clips not in their local voice (ElevenLabs\' English narrators; a female lone word in nina, for ana), the Slovene ones to make again, the one from another village\'s pack left out and listed; nothing changed',
        english.length > 5 && local.map(c => c.key).join() === 'female:žlica' && ndry.voices?.female === 'nina' && ndry.words?.female === 'ana' && ndry.voices.male === 'marko' && ndry.words.male === 'marko' && ndry.todo === english.length + local.length && ndry.skipped?.map((c: any) => c.key).join() === 'female:buongiorno' && ndry.gepard_up === true &&
          nhuman.stdout.includes('(a lone word: ana)') && nhuman.stdout.includes('female gepard (nina) → ana') && nhuman.stdout.includes('Left as they are, not known to be Slovene') && nhuman.stdout.includes('pack:it-saluti') && nhuman.stdout.includes('--narrators --run') && JSON.stringify(narratorRows()) === JSON.stringify(before),
        [ndry, nhuman.stdout])
      const fgBefore = fg.st.calls.length
      const ttsBefore2 = fe.st.tts.length
      const n1 = await nb('--narrators', '--run', '--max', '2')
      const left1 = JSON.parse((await nb('--narrators', '--json')).stdout || '{}').todo
      const n2 = await nb('--narrators', '--run')
      const nafter = narratorRows()
      const nby = Object.fromEntries(nafter.map(c => [c.key, c]))
      check('voice-build --narrators --run (--max 2, then the rest): each Slovene narrator clip made again by the local worker (a female lone word ana, a sentence nina; marko), never ElevenLabs, under a new name, the old file gone; the foreign one and the sentence in nina as they were',
        n1.exitCode === 0 && n1.stdout.includes('Made 2 again') && left1 === ndry.todo - 2 && n2.exitCode === 0 && n2.stdout.includes(`Made ${ndry.todo - 2} again`) && fe.st.tts.length === ttsBefore2 && fg.st.calls.length - fgBefore === ndry.todo &&
          fg.st.calls.slice(fgBefore).every((c: any) => ['ana', 'nina', 'marko'].includes(c.speaker)) && fg.st.calls.slice(fgBefore).some((c: any) => c.speaker === 'ana') &&
          [...english, ...local].every(c => nby[c.key]?.engine === 'gepard' && nby[c.key].engine_voice === want(c) && nby[c.key].file !== c.file && nby[c.key].hits === c.hits && nby[c.key].source === c.source && !existsSync(join(vbData, 'app/voice/files', c.file))) &&
          nby['female:žlica']?.engine_voice === 'ana' && nby['female:juha je vroča']?.file === ninaLine &&
          nby['female:buongiorno']?.engine === 'elevenlabs' && nby['female:buongiorno'].file === foreign && JSON.parse((await nb('--narrators', '--json')).stdout || '{}').todo === 0,
        [n1.stdout + n1.stderr, n2.stdout.slice(-400), nafter.filter(c => c.engine !== 'gepard')])
      fg.st.up = false
      const ndown = await nb('--narrators', '--run')
      fg.st.up = true
      check('voice-build --narrators --run with the worker down: stops, says so (the next run goes on)', ndown.exitCode === 1 && ndown.stderr.includes('worker is down'), ndown.stderr)
      const plain = JSON.parse((await nb('--json')).stdout || '{}')
      check('voice-build\'s plan in a Slovene village with the local voice first (gepard): the narrators\' texts go to the local worker, never the ElevenLabs budget', plain.would?.elevenlabs.texts === 0 && plain.missing === 0, plain.would)
      check('voice-build --narrators in a village in another language: its narrators speak as its cast has them, nothing to do', (await deBuild('--narrators')).stdout.includes('nothing to do'))

      // Their Slovene ElevenLabs voices first (the default: no LANI_VOICE_NARRATOR_SL): each narrator line is to do again in
      // Nina's or Luka's voice, next to its local clip, which stays (the app plays the local one until then)
      const { LANI_VOICE_NARRATOR_SL: _n, ...vbDefault } = vbEnv
      const eb = (...args: string[]) => voiceBuildIn(vbDefault, ...args)
      const NINA = cast().female.languages!.sl.elevenlabs
      const LUKA = cast().male.languages!.sl.elevenlabs
      const slRows = () => (vdb().query("SELECT * FROM clips WHERE voice IN ('female.sl', 'male.sl')").all() as Clip[])
      fe.st.used = fe.st.limit - 5_000 - 60 // 60 characters above the reserve
      const localBefore = narratorRows()
      const eplan = JSON.parse((await eb('--json')).stdout || '{}')
      const ehuman = await eb()
      const narratorTexts = (eplan.by_voice?.female?.texts ?? 0) + (eplan.by_voice?.male?.texts ?? 0)
      check('voice-build in a Slovene village (the default): the narrators\' lines to do in their Slovene ElevenLabs voices (Nina, Luka) within the budget, each next to its local clip; the rest later (they have a clip); nothing missing, the worker not needed',
        eplan.missing === 0 && eplan.narrator_voices?.female === 'Nina' && eplan.narrator_voices.male === 'Luka' && eplan.would?.elevenlabs.narrators.texts > 0 && eplan.would.elevenlabs.narrators.chars <= 60 &&
          eplan.would.elevenlabs.over_local === eplan.would.elevenlabs.narrators.texts && eplan.would.elevenlabs.texts === eplan.would.elevenlabs.narrators.texts && eplan.would.gepard.texts === 0 &&
          eplan.would.elevenlabs.texts + eplan.would.later.texts === narratorTexts && eplan.by_voice.female.elevenlabs === 0 && eplan.by_voice.female.local === eplan.by_voice.female.texts &&
          ehuman.stdout.includes('Narrators in their Slovene ElevenLabs voices') && ehuman.stdout.includes('female (Nina) 0/') && ehuman.stdout.includes('next to a local clip, which stays'),
        [eplan.would, eplan.by_voice, eplan.narrator_voices, ehuman.stdout.slice(-900)])
      const t0 = fe.st.tts.length
      const g0 = fg.st.calls.length
      const erun = await eb('--run')
      const made = slRows()
      const madeWords = made.filter(c => isShortText(c.text))
      const sentNow = fe.st.tts.slice(t0)
      check('voice-build --run (the default): the narrators\' lines in their Slovene ElevenLabs voices in the priority order, as many as the budget allows (a lone word costs its carrier sentence), never into the reserve; a sentence plainly, a lone word inside its carrier sentence (since 2026-10-26; without ffmpeg plainly); their local clips stay as they were; the worker not asked',
        erun.exitCode === 0 && fe.st.tts.length - t0 === eplan.would.elevenlabs.texts && made.length === eplan.would.elevenlabs.texts && fe.st.tts[t0]?.body.text === 'Kako ste?' && fe.st.tts[t0]?.voice === NINA &&
          sentNow.every(c => (c.voice === NINA || c.voice === LUKA) && c.body.language_code === 'sl' && !!c.timestamps === c.body.text.startsWith('Beseda je: ')) && madeWords.length > 0 &&
          sentNow.filter(c => c.timestamps).length === (ffmpeg ? madeWords.length : 0) && made.every(c => c.engine === 'elevenlabs' && c.engine_voice === (c.voice === 'female.sl' ? NINA : LUKA) && (c.method ?? null) === (ffmpeg && isShortText(c.text) ? 'carrier' : null)) &&
          fe.st.used <= fe.st.limit - 5_000 && fg.st.calls.length === g0 && JSON.stringify(narratorRows()) === JSON.stringify(localBefore),
        [erun.stdout.slice(-600), erun.stderr.slice(-300), made.map(c => [c.key, c.method]), sentNow.map(c => [c.body.text, !!c.timestamps])])
      // what the app gets: the ElevenLabs clip where there is one, else the local one; with the local voice first, the local ones
      const seen = (narrators: 'elevenlabs' | 'gepard') => {
        const vs2 = new VoiceStore({ appDir: join(vbData, 'app'), engines: [], readonly: true, narrators, language: 'sl' })
        const idx = vs2.index()
        vs2.close()
        return idx
      }
      const elIdx = seen('elevenlabs')
      const gpIdx = seen('gepard')
      const madeKeys = new Set(made.map(c => `${c.voice === 'female.sl' ? 'female' : 'male'}:${c.norm}`))
      check('the index: a narrator line\'s ElevenLabs clip where it is made, else its local clip (the default); with the local voice first, every local clip, kept; never the voice "female.sl" itself',
        localBefore.every(c => elIdx[c.norm]?.[c.voice] === (madeKeys.has(c.key) ? `/voice/file/${made.find(m => m.norm === c.norm && m.voice === `${c.voice}.sl`)!.file}` : `/voice/file/${c.file}`)) &&
          localBefore.every(c => gpIdx[c.norm]?.[c.voice] === `/voice/file/${c.file}`) && !Object.values(elIdx).some(v => 'female.sl' in v || 'male.sl' in v),
        [...madeKeys])
      check('voice-build --narrators (the default): the clips in the Slovene ElevenLabs voices are left alone', JSON.parse((await eb('--narrators', '--json')).stdout || '{}').todo === 0)
    }
  }

  // --- a village in another language: ElevenLabs is told which (in-process against the fake) -------------------
  {
    const fe = fakeEleven()
    for (const language of ['it', 'de', 'en']) await new ElevenLabs({ key: 'test-key', base: fe.url, language }).synth('Ciao!', 'female')
    check('a village in Italian, German or English: each clip asks ElevenLabs for its language_code', JSON.stringify(fe.st.tts.map(c => c.body.language_code)) === '["it","de","en"]', fe.st.tts.map(c => c.body))
  }

  // --- an explanation in the learner's own language: a school intro's step as one clip (in-process against the fakes) ---
  {
    const fe = fakeEleven()
    const fg = fakeGepard()
    const el = new ElevenLabs({ key: 'test-key', base: fe.url })
    const vs = new VoiceStore({ appDir: tempDir('lani-explain-'), engines: [el, new Gepard({ url: fg.url })], queueDelayMs: 0, level: null })
    const text = 'Biti (to be) has a short form for each person, and Slovene never leaves it out. One person first: jaz sem (I am), ti si (you are), on, ona, ono je (he, she, it is).'
    const norm = `en|${normalizeText(text)}`
    const ex = await vs.say(text, 'teacher', 'en')
    const call = fe.st.tts.at(-1)
    check('an explanation in English (a school intro\'s step): ElevenLabs in the teacher\'s voice (Milena), plainly, no language_code, under its own key ("en|…"); the worker not asked',
      ex?.engine === 'elevenlabs' && ex.norm === norm && ex.key === `teacher:${norm}` && ex.text === text && ex.chars === text.length && call?.voice === cast().teacher.elevenlabs && call.body.text === text && call.body.model_id === 'eleven_v4_turbo' && ex.model === 'eleven_v4_turbo' &&
        !call.timestamps && !('language_code' in call.body) && fg.st.calls.length === 0 && vs.index()[norm]?.teacher === `/voice/file/${ex.file}` && vs.cost(text, 'teacher', 'en') === text.length, [ex, call])
    const short = await vs.say('Two', 'female', 'en')
    const shortCall = fe.st.tts.at(-1)
    check('…a short one in a carrier voice (the narrator): said plainly too, never in a carrier sentence, nor ever old-style',
      short?.norm === 'en|two' && short.key === 'female:en|two' && shortCall?.body.text === 'Two' && !shortCall.timestamps && !('language_code' in shortCall.body) && !vs.oldStyle(short) && vs.cost('Two', 'female', 'en') === 3, [short, shortCall])
    const line = await vs.say('jaz sem', 'female')
    const n0 = fe.st.tts.length
    const again = await vs.say(text, 'teacher', 'en')
    check('…a Slovene line with the same words is a clip of its own (asked for in Slovene); the explanation asked for again: the clip there, nothing sent',
      line?.key === 'female:jaz sem' && line.file !== short?.file && fe.st.tts.at(-1)?.body.language_code === 'sl' && again?.file === ex?.file && fe.st.tts.length === n0 && fg.st.calls.length === 0, [line, fe.st.tts.length])
    const sl = await vs.say('Dober dan.', 'female', 'sl')
    check('…lang the village\'s own: a line as ever', sl?.key === 'female:dober dan' && fe.st.tts.at(-1)?.body.language_code === 'sl', sl)
    check('…never re-voiced as Slovene: not in the carrier re-voice\'s queue, however often the app asked for it', (await vs.say('Two', 'female', 'en'))?.file === short?.file && !vs.revoiceQueue(false).some(c => explanationLang(c.norm)) && explanationLang(short!.norm) === 'en' && explanationLang('dober dan') === undefined)
    const st = await vs.status()
    check('status: the explanations counted apart (clips, characters, by language), not among the lines',
      st.explanations.clips === 2 && st.explanations.chars === text.length + 3 && st.explanations.by_lang.en === 2 && st.clips.total === 2 && !st.clips.by.some(b => b.voice === 'teacher'), [st.explanations, st.clips])
    // the quota down to the reserve: an explanation isn't a live line and waits; a live line still may
    fe.st.used = fe.st.limit - 4_000
    await el.subscription(true)
    const t0 = fe.st.tts.length
    const kept = await vs.say('Three or more: mi smo (we are), vi ste (you are), oni so (they are).', 'teacher', 'en')
    const live = await vs.say('Dober večer, Jan.', 'teacher')
    check('…the quota at the reserve: no explanation (the app reads it with the phone\'s voice and the Slovene clips), nobody else\'s voice for it, the worker not asked; a live line still voiced',
      kept === null && live?.engine === 'elevenlabs' && fe.st.tts.length === t0 + 1 && fe.st.tts.at(-1)?.body.text === 'Dober večer, Jan.' && fg.st.calls.length === 0, [kept, live])
    fe.st.used = 0
    await el.subscription(true)
    fe.st.fail = 500
    check('…ElevenLabs failing: no explanation, the worker never asked (it speaks only Slovene)', (await vs.say('Three or more: mi smo.', 'teacher', 'en')) === null && fg.st.calls.length === 0)
    fe.st.fail = 0
    vs.close()

    // a Slovene village's narrator: the explanation in the narrator's Slovene ElevenLabs voice (Nina), as their lines
    const fe2 = fakeEleven()
    const el2 = new ElevenLabs({ key: 'test-key', base: fe2.url })
    const ns = new VoiceStore({ appDir: tempDir('lani-explain-sl-'), engines: [el2, new Gepard({ url: fg.url })], queueDelayMs: 0, level: null, narrators: 'gepard', language: 'sl' })
    const nx = await ns.say(text, 'female', 'de')
    check('a Slovene village\'s narrator (the local voice first): an explanation in German by her Slovene ElevenLabs voice (Nina), the voice "female.sl", the app gets it as the narrator\'s; the worker not asked',
      nx?.voice === 'female.sl' && nx.norm === `de|${normalizeText(text)}` && fe2.st.tts.at(-1)?.voice === cast().female.languages!.sl.elevenlabs && !('language_code' in (fe2.st.tts.at(-1)?.body ?? {})) && fg.st.calls.length === 0 &&
        ns.index()[`de|${normalizeText(text)}`]?.female === `/voice/file/${nx.file}` && !ns.wantsUpgrade(nx) && !ns.upgradeQueue(false).some(c => explanationLang(c.norm)) && !ns.narratorQueue(() => true).todo.some(c => explanationLang(c.norm)), [nx, fe2.st.tts.at(-1)])
    ns.close()
  }

  // --- a school intro step read aloud asks for its explanation and its Slovene at once: within ElevenLabs' concurrency
  // (in-process against the fakes) ------------------------------------------------------------------------------------
  {
    const fe = fakeEleven()
    // a plan of three at once, each take a while (as the real one: a Creator plan allows five, voice-build may use some)
    Object.assign(fe.st, { concurrency: 3, delayMs: 120 })
    const fg = fakeGepard()
    const el = new ElevenLabs({ key: 'test-key', base: fe.url })
    const vs = new VoiceStore({ appDir: tempDir('lani-burst-'), engines: [el, new Gepard({ url: fg.url })], queueDelayMs: 0, level: null })
    // biti's step 4 (the one whose explanation Jan's node never made, 2026-10-14): its explanation as the app sends it,
    // the Slovene forms read in its runs, its examples and question
    const explanation = 'To say no, ne and the form melt into one word: nisem, nisi, ni; nismo, niste, niso; for two nisva, nista. Never ne sem.'
    const lines = ['ne', 'nisem, nisi, ni', 'nismo, niste, niso', 'nisva, nista', 'ne sem', 'Nisem utrujen.', 'Kruh ni svež.', 'Babica ni doma, je na vrtu.']
    const [ex, ...said] = await Promise.all([vs.say(explanation, 'teacher', 'en'), ...lines.map(l => vs.say(l, 'teacher'))])
    check('a school intro step read aloud (its explanation and its Slovene, nine asked at once) against a plan of three at once: never more at ElevenLabs than that (none refused), every one made by ElevenLabs in the teacher\'s voice, the explanation too; the worker not asked',
      fe.st.refused === 0 && fe.st.maxInFlight === 3 && ex?.engine === 'elevenlabs' && ex.norm === 'en|to say no ne and the form melt into one word nisem nisi ni nismo niste niso for two nisva nista never ne sem' &&
        said.every(c => c?.engine === 'elevenlabs' && c.voice === 'teacher') && fg.st.calls.length === 0, [fe.st.refused, fe.st.maxInFlight, ex, said.map(c => c?.engine)])
    vs.close()
    // more at once than the plan allows (here: a bridge set to eight; on the node: voice-build's requests besides): what
    // ElevenLabs refuses as busy is asked again a moment later, and ElevenLabs isn't paused for it
    const fe2 = fakeEleven()
    Object.assign(fe2.st, { concurrency: 3, delayMs: 30 })
    const el2 = new ElevenLabs({ key: 'test-key', base: fe2.url, concurrency: 8, busyWaitMs: 60 })
    const vs2 = new VoiceStore({ appDir: tempDir('lani-busy-'), engines: [el2, new Gepard({ url: fg.url })], queueDelayMs: 0, level: null })
    const many = await Promise.all(lines.map(l => vs2.say(l, 'teacher')))
    const after = await vs2.say(explanation, 'teacher', 'en')
    const est = await el2.status()
    check('…more asked at once than the plan allows: what ElevenLabs refuses as too many at once is asked again a moment later and made (not by the worker), ElevenLabs not paused (an explanation right after made at once); the status counts them',
      fe2.st.refused > 0 && many.every(c => c?.engine === 'elevenlabs') && after?.engine === 'elevenlabs' && fg.st.calls.length === 0 && est.up === true &&
        est.concurrency.limit === 8 && est.concurrency.busy_again === fe2.st.refused && est.concurrency.running === 0 && est.concurrency.waiting === 0, [fe2.st.refused, many.map(c => c?.engine), after, est.concurrency])
    vs2.close()
    // another 429 (a rate limit, not the concurrency): the engine pauses as before, the worker takes the line
    const fe3 = fakeEleven()
    fe3.st.fail = 429
    const el3 = new ElevenLabs({ key: 'test-key', base: fe3.url, busyWaitMs: 10 })
    check('…a 429 that isn\'t about the concurrency (a rate limit): not asked again, ElevenLabs paused a while as before', (await el3.synth('Dober dan.', 'teacher')) === null && fe3.st.tts.length === 1 && !(await el3.ready(10, true)))
    check('LANI_ELEVENLABS_CONCURRENCY sets the requests at once (a whole number above 0; else the default, three)',
      (await enginesFromEnv({ LANI_ELEVENLABS_CONCURRENCY: '5' }).eleven.status()).concurrency.limit === 5 &&
        (await enginesFromEnv({ LANI_ELEVENLABS_CONCURRENCY: '0' }).eleven.status()).concurrency.limit === DEFAULT_ELEVENLABS_CONCURRENCY &&
        (await enginesFromEnv({ LANI_ELEVENLABS_CONCURRENCY: 'many' }).eleven.status()).concurrency.limit === 3)
  }

  // --- the ElevenLabs model: a setting, recorded with every clip; an older model's clips made again in it (voice-build
  // --upgrade-model; in-process against the fakes) ---------------------------------------------------------------------
  {
    check('LANI_ELEVENLABS_MODEL picks the model (eleven_v4_turbo by default: v4 as good as v3 to Jan\'s ear, Turbo half the credits)',
      enginesFromEnv({}).eleven.model === 'eleven_v4_turbo' && DEFAULT_ELEVENLABS_MODEL === 'eleven_v4_turbo' && enginesFromEnv({ LANI_ELEVENLABS_MODEL: ' eleven_v4 ' }).eleven.model === 'eleven_v4' &&
        (await enginesFromEnv({ LANI_ELEVENLABS_MODEL: 'eleven_v3' }).eleven.status()).model === 'eleven_v3' && creditsFor(1_000, 'eleven_v4_turbo') === 500 && creditsFor(1_000, 'eleven_v4') === 1_000 && creditsFor(10, 'something-else') === 10)
    // a store from before the model was recorded: its clips table without the column, a row of eleven_v3's in it
    const dir = tempDir('lani-model-')
    mkdirSync(join(dir, 'voice', 'files'), { recursive: true })
    const old = new Database(join(dir, 'voice', 'voice.db'))
    old.exec('CREATE TABLE clips (key TEXT PRIMARY KEY, norm TEXT NOT NULL, text TEXT NOT NULL, voice TEXT NOT NULL, engine TEXT NOT NULL, file TEXT NOT NULL, chars INTEGER NOT NULL, created_at TEXT NOT NULL, source TEXT NOT NULL, method TEXT, hits INTEGER NOT NULL DEFAULT 0, denoise TEXT, level REAL, gain REAL, tempo REAL, edges INTEGER, engine_voice TEXT)')
    const oldFile = `${'a'.repeat(40)}.mp3`
    writeFileSync(join(dir, 'voice', 'files', oldFile), mp3('el:old'))
    old.query("INSERT INTO clips VALUES ('young-man:kje je pošta', 'kje je pošta', 'Kje je pošta?', 'young-man', 'elevenlabs', ?, 13, '2026-09-01T10:00:00.000Z', 'scene:x', NULL, 5, 'hiss', -18, -2, NULL, 1, ?)").run(oldFile, cast()['young-man'].elevenlabs)
    old.close()
    const fe = fakeEleven()
    const fg = fakeGepard()
    // the processing as the node's ffmpeg does it, by stand-ins that mark the bytes: the voice's filter, the level, the edges
    const MARK = (b: Uint8Array, tag: string) => new Uint8Array([...b, ...new TextEncoder().encode(tag)])
    const denoise: Denoiser = async b => MARK(b, '|denoised')
    const level: Leveller = { target: -18, level: async b => ({ bytes: MARK(b, '|levelled'), gain: -3, limited: false, before: { lufs: -15 } as any, edges: { lead: 0.05, tail: 0.1 } as any, trimmed: true }) }
    const v3 = new ElevenLabs({ key: 'test-key', base: fe.url, model: 'eleven_v3' })
    const vs3 = new VoiceStore({ appDir: dir, engines: [v3, new Gepard({ url: fg.url })], queueDelayMs: 0, denoise, level })
    const was = vs3.get('kje je pošta', 'young-man')
    check('a store from before the model: the column added, its ElevenLabs clips counted as eleven_v3\'s, the setting\'s model in the status',
      was?.model === null && modelOf(was!) === 'eleven_v3' && JSON.stringify(vs3.modelStats()) === JSON.stringify({ eleven_v3: { clips: 1, chars: 13 } }) && (await vs3.status()).models.now === 'eleven_v3', [was, vs3.modelStats()])
    // clips by eleven_v3 (the setting), a local one, an explanation; how often each was played
    const a = await vs3.voice('Dober dan, Micka.', 'grandma', 'test')
    const x = await vs3.say('Three or more: mi smo (we are).', 'teacher', 'en')
    const w = await vs3.voice('kruh', 'girl', 'test')
    const g = await vs3.voice('Lahko noč.', 'grandpa', 'test', { engines: ['gepard'] })
    check('every new ElevenLabs clip records its model (the setting\'s, in the request too), a local one none',
      a?.model === 'eleven_v3' && x?.model === 'eleven_v3' && w?.model === 'eleven_v3' && fe.st.tts.every(t => t.body.model_id === 'eleven_v3') && g?.engine === 'gepard' && g.model === null && modelOf(g) === undefined, [a, x, g])
    vs3.database.query("UPDATE clips SET hits = 3 WHERE key = ?").run(x!.key)
    vs3.database.query("UPDATE clips SET hits = 9 WHERE key = ?").run(g!.key)
    vs3.close()
    // the node set to eleven_v4_turbo: the older clips wait for it, most played first, never the local one
    const v4 = new ElevenLabs({ key: 'test-key', base: fe.url })
    const updated: number[] = []
    const vs = new VoiceStore({ appDir: dir, engines: [v4, new Gepard({ url: fg.url })], queueDelayMs: 0, denoise, level, onUpdated: n => void updated.push(n) })
    const queue = vs.modelQueue()
    check('the upgrade\'s queue: the ElevenLabs clips of an older model (one from before it was recorded too), most played first, then the oldest; never a local clip',
      JSON.stringify(queue.map(c => c.key)) === JSON.stringify(['young-man:kje je pošta', `teacher:${x!.norm}`, 'grandma:dober dan micka', 'girl:kruh']) && !queue.some(c => c.engine === 'gepard'), queue.map(c => [c.key, c.hits]))
    const n0 = fe.st.tts.length
    const r = await vs.upgradeModel({ max: 2, out: () => {} })
    const up = vs.get('kje je pošta', 'young-man')
    const ux = vs.get(x!.norm, 'teacher')
    const bytes = (c: Clip | undefined) => (c ? new TextDecoder().decode(readFileSync(join(vs.filesDir, c.file))) : '')
    const sent = fe.st.tts.slice(n0)
    check('voice-build --upgrade-model --max 2: the two most played made again by eleven_v4_turbo in their voices (the explanation plainly, no language_code), their filter, level and edges as every new clip, under new names, their hits kept, the old files gone',
      r.made === 2 && r.left === 2 && sent.length === 2 && sent.every(t => t.body.model_id === 'eleven_v4_turbo') && sent[0].voice === cast()['young-man'].elevenlabs && sent[1].body.text === x!.text && !('language_code' in sent[1].body) &&
        up?.model === 'eleven_v4_turbo' && up.denoise === 'hiss' && up.level === -18 && up.gain === -3 && up.edges === 1 && up.hits === 5 && up.file !== oldFile && !existsSync(join(vs.filesDir, oldFile)) && bytes(up).endsWith('|denoised|levelled') &&
        ux?.model === 'eleven_v4_turbo' && ux.file !== x!.file && ux.hits === 3 && !ux.denoise && ux.level === -18 && ux.edges === 1 && !existsSync(join(vs.filesDir, x!.file)) && vs.get(g!.norm, 'grandpa')?.file === g!.file,
      [r, sent.map(t => [t.voice, t.body]), up, ux])
    check('…the rest wait; the status counts the clips by model', vs.modelQueue().length === 2 && JSON.stringify(vs.modelStats()) === JSON.stringify({ eleven_v3: { clips: 2, chars: a!.chars + w!.chars }, eleven_v4_turbo: { clips: 2, chars: 13 + x!.chars } }), vs.modelStats())
    // the quota down to the reserve: it stops, nothing more sent
    fe.st.used = fe.st.limit - 5_000
    await v4.subscription(true)
    const n1 = fe.st.tts.length
    const r2 = await vs.upgradeModel({ out: () => {} })
    check('…the quota at the reserve: it stops, nothing sent, the clips as they were', r2.made === 0 && !!r2.stopped && fe.st.tts.length === n1 && vs.modelQueue().length === 2, r2)
    fe.st.used = 0
    await v4.subscription(true)
    const r3 = await vs.upgradeModel({ maxChars: 5, out: () => {} })
    check('…--max-chars: only what fits (the short word), the longer one left', r3.made === 1 && vs.get('kruh', 'girl')?.model === 'eleven_v4_turbo' && vs.get('dober dan micka', 'grandma')?.model === 'eleven_v3', r3)
    // voice-build --elevenlabs: a speaker's local clips in their ElevenLabs voice (Mojca's lines the local voice said)
    const local = vs.localQueue(['grandpa', 'female'])
    check('--elevenlabs\'s queue: the speaker\'s local (Gepard) clips, never a narrator\'s (theirs stay as the fallback)',
      local.length === 1 && local[0].key === g!.key && vs.localQueue(['female', 'male']).length === 0, local.map(c => c.key))
    const r4 = await vs.elevenlabsFor(['grandpa'], { out: () => {} })
    const now4 = vs.get(g!.norm, 'grandpa')
    check('…made in their ElevenLabs voice and the model now, the hits kept, a new file, the local file gone; the queue empty after',
      r4.made === 1 && now4?.engine === 'elevenlabs' && now4.model === 'eleven_v4_turbo' && now4.hits === 9 && now4.file !== g!.file && !existsSync(join(dir, 'voice', 'files', g!.file)) && vs.localQueue(['grandpa']).length === 0, [r4, now4])
    vs.close()
  }

  // --- stale clips made again as the app asks for them: a swapped voice, an older model (in-process against the fakes) ---
  {
    // two casts: the girl in voice A (filtered: she hissed), then in voice B since today (no longer on the denoise list)
    const castDir = tempDir('lani-stale-cast-')
    const base = JSON.parse(readFileSync(CAST_FILE, 'utf8'))
    const A = 'GirlVoiceAaaaaaaaaaa'
    const B = 'GirlVoiceBbbbbbbbbbb'
    const today = new Date().toISOString().slice(0, 10)
    const castA = join(castDir, 'a.json')
    const castB = join(castDir, 'b.json')
    writeFileSync(castA, JSON.stringify({ ...base, denoise: { girl: 'hiss' }, speakers: { ...base.speakers, girl: { ...base.speakers.girl, elevenlabs: A } } }))
    writeFileSync(castB, JSON.stringify({ ...base, denoise: {}, speakers: { ...base.speakers, girl: { ...base.speakers.girl, elevenlabs: B, since: today } } }))
    const before = process.env.LANI_VOICE_CAST
    process.env.LANI_VOICE_CAST = castA
    const fe = fakeEleven()
    const fg = fakeGepard()
    const denoise: Denoiser = async b => new Uint8Array([...b, ...new TextEncoder().encode('|denoised')])
    const el = new ElevenLabs({ key: 'test-key', base: fe.url })
    const updated: number[] = []
    const vs = new VoiceStore({ appDir: tempDir('lani-stale-'), engines: [el, new Gepard({ url: fg.url })], queueDelayMs: 0, denoise, level: null, revoiceDaily: 10, onUpdated: n => void updated.push(n) })
    const read = (c: Clip | null | undefined) => (c ? new TextDecoder().decode(readFileSync(join(vs.filesDir, c.file))) : '')
    const old = await vs.voice('Kje je mama?', 'girl', 'scene:x')
    const kept = await vs.voice('Mama je doma.', 'grandma', 'scene:x')
    check('stale: a clip in its voice now is not stale (its voice recorded, the model now)', old?.engine_voice === A && !vs.staleVoice(old) && !vs.staleModel(old) && read(old).endsWith('|denoised'), old)
    // the girl's voice swapped (the cast's edit): her clip is stale, the grandmother's not
    process.env.LANI_VOICE_CAST = castB
    check('…the girl\'s voice swapped in the cast: her clip is stale (its recorded voice another), the grandmother\'s not; nothing made yet',
      vs.staleVoice(vs.get('kje je mama', 'girl')!) && !vs.staleVoice(kept!) && vs.staleQueue('voice').length === 0)
    const n0 = fe.st.tts.length
    const served = await vs.say('Kje je mama?', 'girl')
    check('…the app asks for it: the old clip at once (nothing waited for), the re-voice queued', served?.file === old?.file && fe.st.tts.length === n0)
    const until = async (f: () => boolean, ms = 3_000) => {
      const end = Date.now() + ms
      while (!f() && Date.now() < end) await Bun.sleep(20)
      return f()
    }
    await until(() => vs.get('kje je mama', 'girl')?.file !== old?.file)
    const now = vs.get('kje je mama', 'girl')
    check('…then made again in the new voice (B), not filtered (the girl left the denoise list), under a new name, its hits kept, the old file gone, the app told',
      now?.engine_voice === B && fe.st.tts.at(-1)?.voice === B && !now.denoise && !read(now).endsWith('|denoised') && now.file !== old?.file && !existsSync(join(vs.filesDir, old!.file)) && (now.hits ?? 0) >= 1 && updated.length === 1 && !vs.staleVoice(now),
      [now, fe.st.tts.at(-1)?.voice, updated])
    check('…and asked again: the new clip', (await vs.say('Kje je mama?', 'girl'))?.file === now?.file && vs.staleQueue('voice').length === 0)
    // a clip from before the voice was recorded: stale when made before the voice's `since`, not after; a person's own voice by its id
    const unrecorded = await vs.voice('Dober dan.', 'girl', 'scene:x')
    vs.database.query("UPDATE clips SET engine_voice = NULL, created_at = '2026-09-30T10:00:00.000Z' WHERE key = ?").run(unrecorded!.key)
    const wasBefore = vs.staleVoice(vs.get('dober dan', 'girl')!)
    vs.database.query('UPDATE clips SET created_at = ? WHERE key = ?').run(new Date().toISOString(), unrecorded!.key)
    check('…a clip whose voice isn\'t recorded (from before October 11): stale when made before the voice\'s `since`, not on or after it', wasBefore && !vs.staleVoice(vs.get('dober dan', 'girl')!))
    // the model: an older model's clips after the swapped voices', in the same day's cap; with the setting off, not lazily
    vs.database.query("UPDATE clips SET model = 'eleven_v3' WHERE key = ?").run(kept!.key)
    vs.database.query('UPDATE clips SET hits = 50 WHERE key = ?').run(kept!.key)
    const swapped = await vs.voice('Kruh, prosim.', 'girl', 'scene:x')
    vs.database.query('UPDATE clips SET engine_voice = ?, hits = 2 WHERE key = ?').run(A, swapped!.key)
    const off = new VoiceStore({ appDir: vs.dir.replace(/\/voice$/, ''), engines: [el], queueDelayMs: 0, level: null, revoiceDaily: 1, lazyModel: false })
    const mama = vs.get('mama je doma', 'grandma')!
    const kruh = vs.get('kruh prosim', 'girl')!
    check('…an older model\'s clip is stale by its model (LANI_VOICE_UPGRADE_MODEL_LAZY off: not lazily), a swapped voice\'s either way',
      vs.staleModel(mama) && vs.stale(mama) === 'model' && off.stale(mama) === undefined && vs.stale(kruh) === 'voice' && off.stale(kruh) === 'voice')
    off.close()
    vs.close()
    // a store held to one re-voice a day: both asked, the swapped voice's goes first (though asked less), the model's waits
    const one: number[] = []
    const capped = new VoiceStore({ appDir: vs.dir.replace(/\/voice$/, ''), engines: [el, new Gepard({ url: fg.url })], queueDelayMs: 0, denoise, level: null, revoiceDaily: 1, onUpdated: n => void one.push(n) })
    capped.database.query('DELETE FROM revoices').run() // today's re-voice above not counted here
    const first = capped.lazyQueue().map(c => c.key)
    await capped.say('Mama je doma.', 'grandma')
    await capped.say('Kruh, prosim.', 'girl')
    const order = capped.lazyQueue().map(c => c.key)
    await until(() => capped.get('kruh prosim', 'girl')?.engine_voice === B)
    await Bun.sleep(200)
    check('the day\'s cap of one: a swapped voice\'s clip played before is queued without a new ask (the phone keeps its clips), an older model\'s only once asked for; the swapped voice\'s first though the model\'s is played more (50 to 3), made in voice B; the older model\'s waits for tomorrow (still eleven_v3)',
      first.join() === 'girl:kruh prosim' && JSON.stringify(order) === JSON.stringify(['girl:kruh prosim', 'grandma:mama je doma']) && capped.get('kruh prosim', 'girl')?.engine_voice === B &&
        capped.get('mama je doma', 'grandma')?.model === 'eleven_v3' && capped.lazyToday() === 1 && capped.lazyQueue().map(c => c.key).join() === 'grandma:mama je doma', [order, capped.lazyQueue().map(c => c.key)])
    // the status: the stale clips, asked for, and what making them all again costs at Turbo's half price
    const st = capped.staleStats()
    check('the status counts the stale clips (a swapped voice, an older model: all and asked for) and what making them all again costs (Turbo: half a credit a character)',
      st.voice.clips === 0 && st.model.clips === 1 && st.model.asked === 1 && st.model.lazy && st.all.chars === 'Mama je doma.'.length && st.all.credits === Math.round(st.all.chars / 2), st)
    // tomorrow (the day's count reset): the model's clip in eleven_v4_turbo, its filter as the cast has the grandmother (none)
    capped.database.query('DELETE FROM revoices').run()
    capped.resumeRevoice()
    await until(() => capped.get('mama je doma', 'grandma')?.model === 'eleven_v4_turbo')
    check('…the next day it is made again with eleven_v4_turbo, in the grandmother\'s voice', capped.get('mama je doma', 'grandma')?.model === 'eleven_v4_turbo' && fe.st.tts.at(-1)?.body.model_id === 'eleven_v4_turbo' && fe.st.tts.at(-1)?.voice === cast().grandma.elevenlabs)
    capped.close()
    if (before === undefined) delete process.env.LANI_VOICE_CAST
    else process.env.LANI_VOICE_CAST = before
  }

  // --- the narrators of a Slovene village: their Slovene ElevenLabs voices, the local voice as their fallback, never the
  // English library voices (in-process) ---------------------------------------------------------------------------------
  {
    const fe = fakeEleven()
    const fg = fakeGepard()
    const NINA = '0IwLFuWc6siAnpd77hRG'
    const LUKA = 'SmCUS1ekZnVjC5lqnRQG'
    const english = [cast().female.elevenlabs, cast().male.elevenlabs]
    const el = new ElevenLabs({ key: 'test-key', base: fe.url })
    const gp = new Gepard({ url: fg.url })
    const updated: number[] = []
    const dir = tempDir('lani-narrators-')
    // a day's cap of 4: 3 for the lazy re-voice, 1 kept for a re-record
    const ns = new VoiceStore({ appDir: dir, engines: [el, gp], queueDelayMs: 0, level: null, narrators: 'elevenlabs', language: 'sl', revoiceDaily: 3, redoDaily: 1, onUpdated: n => void updated.push(n) })
    const w = await ns.voice('kruh', 'female', 'pack:hrana')
    const call = fe.st.tts.at(-1)
    check('a Slovene narrator\'s line (the default): its Slovene ElevenLabs voice first (female: Nina), a clip of the voice "female.sl"; a new lone word inside its carrier sentence (since 2026-10-26: "Beseda je: kruh.", /with-timestamps, Slovenian), cut out and costing the sentence (without ffmpeg: plainly, the word); the app gets it as the narrator\'s',
      w?.engine === 'elevenlabs' && w.voice === 'female.sl' && w.key === 'female.sl:kruh' && w.engine_voice === NINA && call?.voice === NINA && call.body.language_code === 'sl' && fg.st.calls.length === 0 &&
        (ffmpeg
          ? !!call.timestamps && call.body.text === 'Beseda je: kruh.' && w.method === 'carrier' && w.chars === 16 && ns.cost('kruh', 'female') === 16
          : !call.timestamps && call.body.text === 'kruh' && !w.method && ns.cost('kruh', 'female') === 4) &&
        ns.index().kruh?.female === `/voice/file/${w.file}` && !ns.index().kruh?.['female.sl'] && ns.best('kruh', 'female')?.file === w.file && !ns.get('kruh', 'female') && ns.sloveneNarrator('female') && !ns.sloveneNarrator('grandma'), [w, call])
    const m = await ns.say('Dober dan, Jan.', 'male')
    check('…the male narrator Luka, a live line too; a villager keeps their own voice', m?.voice === 'male.sl' && fe.st.tts.at(-1)?.voice === LUKA && (await ns.voice('Kje je mlin?', 'grandpa', 'villager:janez'))?.engine === 'elevenlabs' && fe.st.tts.at(-1)?.voice === cast().grandpa.elevenlabs, m)
    // the quota down to the reserve: a line voiced ahead comes from the local voice
    fe.st.used = fe.st.limit - 5_000
    await el.subscription(true)
    const lw = await ns.voice('mleko', 'female', 'pack:hrana')
    const ls = await ns.voice('Mleko je hladno.', 'female', 'pack:hrana')
    const lm = await ns.voice('mleko', 'male', 'pack:hrana')
    check('…ElevenLabs can\'t (the quota at the reserve): the local voice in the narrator\'s key, female ana for a lone word and nina for a sentence, male marko; the index on them',
      lw?.engine === 'gepard' && lw.key === 'female:mleko' && lw.engine_voice === 'ana' && ls?.engine_voice === 'nina' && lm?.engine_voice === 'marko' && fg.st.calls.map(c => c.speaker).join() === 'ana,nina,marko' &&
        ns.index().mleko?.female === `/voice/file/${lw.file}` && ns.index().mleko?.male === `/voice/file/${lm.file}`, [lw, ls, lm, fg.st.calls])
    fg.st.busy = true
    const none = await ns.voice('Lahko noč.', 'female', 'test')
    check('…neither can: no clip (the phone speaks), never an English narrator', none === null && fe.st.tts.every(c => !english.includes(c.voice)), [none, fe.st.tts.map(c => c.voice)])
    fg.st.busy = false
    // the app asks for a local clip whose ElevenLabs one is wanted: it gets it at once; the ElevenLabs one is made soon,
    // within the day's cap and never into the reserve, and the local clip stays as its fallback
    check('the local clips whose ElevenLabs one is wanted: three, none asked for yet; the status counts them',
      ns.upgradeQueue().length === 0 && ns.upgradeQueue(false).length === 3 && ns.wantsUpgrade(lw!) && !ns.wantsUpgrade(w!) && ns.narratorStats().waiting === 3 && ns.narratorStats().asked === 0 && ns.narratorStats().elevenlabs === 2 &&
        ns.narratorStats().mode === 'elevenlabs' && ns.narratorStats().elevenlabs_voices.female === 'Nina' && ns.narratorStats().elevenlabs_voices.male === 'Luka' && ns.narratorStats().word_voices.female === 'ana' && ns.narratorStats().voices.female === 'nina', ns.narratorStats())
    const t0 = fe.st.tts.length
    ns.requested(lw!.file)
    await Bun.sleep(60)
    check('…asked for while the quota is at the reserve: the local clip plays, nothing is sent, nothing counted', !ns.get('mleko', 'female.sl') && fe.st.tts.length === t0 && ns.revoicedToday() === 0 && ns.upgradeQueue().length === 1)
    fe.st.used = 0
    await el.subscription(true)
    ns.requested(ls!.file)
    ns.requested(lm!.file)
    // (the lone words' carrier takes are cut with ffmpeg: a moment more)
    for (let i = 0; i < 80 && ns.upgradeQueue().length; i++) await Bun.sleep(25)
    const up = ns.get('mleko', 'female.sl')
    const upSent = fe.st.tts.slice(t0)
    check('…the quota back: the ElevenLabs ones made soon, most asked for first (Nina, Luka; a lone word inside its carrier sentence, without ffmpeg plainly; the sentence plainly), each as its voice\'s clip; the local clips kept, their files there; the index on the ElevenLabs ones; the app told; counted for the day',
      !!up && up.engine_voice === NINA && !!ns.get('mleko je hladno', 'female.sl') && ns.get('mleko', 'male.sl')?.engine_voice === LUKA && fe.st.tts.length - t0 === 3 &&
        upSent.every(c => !!c.timestamps === c.body.text.startsWith('Beseda je: ')) && upSent.filter(c => c.timestamps).length === (ffmpeg ? 2 : 0) && fe.st.tts[t0]?.body.text === (ffmpeg ? 'Beseda je: mleko.' : 'mleko') &&
        (up.method ?? null) === (ffmpeg ? 'carrier' : null) && (ns.get('mleko', 'male.sl')?.method ?? null) === (ffmpeg ? 'carrier' : null) && !ns.get('mleko je hladno', 'female.sl')?.method &&
        ns.get('mleko', 'female')?.file === lw!.file && existsSync(join(ns.filesDir, lw!.file)) && existsSync(join(ns.filesDir, ls!.file)) && ns.index().mleko?.female === `/voice/file/${up!.file}` && ns.index().mleko?.male === `/voice/file/${ns.get('mleko', 'male.sl')!.file}` &&
        ns.revoicedToday() === 3 && updated.length > 0 && ns.upgradeQueue(false).length === 0, [up, fe.st.tts.slice(t0).map(c => c.body.text), ns.revoicedToday()])
    // the day's cap: the lazy re-voice has used its 3; one more asked for waits, a long press still goes (in Nina's voice): its own count
    fe.st.used = fe.st.limit - 5_000
    await el.subscription(true)
    const lj = await ns.voice('juha', 'female', 'pack:hrana')
    fe.st.used = 0
    await el.subscription(true)
    ns.requested(lj!.file)
    await Bun.sleep(60)
    const capped = !ns.get('juha', 'female.sl')
    const rj = await ns.redo('juha', 'female')
    const rjCall = fe.st.tts.at(-1)
    check('…a re-record of a word alone: inside its carrier sentence in Nina\'s native voice too (/with-timestamps, "Beseda je: juha.", Slovenian), cut out; a lone word said plainly came out in another language\'s sound ("učiteljica", 2026-10-19)',
      rj.status === 'done' && rjCall?.voice === NINA && !!rjCall.timestamps && rjCall.body.text === 'Beseda je: juha.' && rjCall.body.language_code === 'sl' && (rj as any).clip.method === 'carrier', [rjCall?.body, (rj as any).clip?.method])
    check('…the day\'s cap: a fourth asked for waits (3 of 3 used); a long press makes it in Nina\'s voice now, a re-record of its own (1 of 1; the lazy re-voice\'s still 3), the local clip kept',
      lj?.engine_voice === 'ana' && capped && rj.status === 'done' && rj.clip.voice === 'female.sl' && rj.clip.engine_voice === NINA && ns.redosToday() === 1 && ns.lazyToday() === 3 && ns.get('juha', 'female')?.file === lj!.file && existsSync(join(ns.filesDir, lj!.file)), [capped, rj])
    const rj2 = await ns.redo('juha', 'female')
    const rk = await ns.redo('kruh', 'female')
    check('…again: once a clip a day (not_needed, that take); another one: the day\'s re-records are spent', rj2.status === 'not_needed' && rj2.clip.file === (rj as any).clip.file && rk.status === 'limit' && (rk as any).reason === 'cap', [rj2, rk])
    // an English narrator's clip from before in the narrator's key: not re-voiced in its carrier sentence, made in Nina's voice instead
    const old = `${createHash('sha1').update('smoke|narrator|old').digest('hex')}.mp3`
    writeFileSync(join(ns.filesDir, old), mp3('el:old:sir'))
    ns.database.query('INSERT OR REPLACE INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, hits) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)').run('female:sir', 'sir', 'sir', 'female', 'elevenlabs', old, 3, '2026-09-01T10:00:00.000Z', 'pack:hrana', 4)
    const oldRow = ns.get('sir', 'female')!
    check('an English narrator\'s word from before: not old-style (no carrier re-voice), not in the local voice, its Slovene ElevenLabs clip wanted', !ns.oldStyle(oldRow) && ns.revoiceQueue(false).length === 0 && !ns.inLocalVoice(oldRow) && ns.wantsUpgrade(oldRow) && ns.lazyQueue()[0]?.key === 'female:sir', oldRow)
    ns.close()

    // Nina's and Luka's lone words from before 2026-10-26, said plainly (no method), in their voice and the model now:
    // kept as they are, never re-voiced in their carrier sentence (the lazy re-voice, voice-build --revoice-words, the
    // status); a new lone word beside them is said in its carrier sentence
    {
      const ks = new VoiceStore({ appDir: tempDir('lani-narrators-before-'), engines: [el], queueDelayMs: 0, level: null, narrators: 'elevenlabs', language: 'sl' })
      const put = (text: string, voice: string, engine: string, ev: string | null, model: string | null, hits: number) => {
        const key = `${voice}:${normalizeText(text)}`
        const f = `${createHash('sha1').update(`smoke|before|${key}`).digest('hex')}.mp3`
        writeFileSync(join(ks.filesDir, f), mp3(`before:${key}`))
        ks.database.query('INSERT OR REPLACE INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, hits, engine_voice, model) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)')
          .run(key, normalizeText(text), text, voice, engine, f, text.length, '2026-10-20T10:00:00.000Z', 'pack:hrana', hits, ev, model)
        return ks.get(normalizeText(text), voice)!
      }
      const ninaOld = put('sol', 'female.sl', 'elevenlabs', NINA, el.model, 3)
      const lukaOld = put('Dober dan', 'male.sl', 'elevenlabs', LUKA, el.model, 2)
      const local = put('kis', 'female', 'gepard', 'ana', null, 0)
      const old = [ninaOld, lukaOld]
      const cs0 = ks.carrierStats()
      check('the narrators\' Slovene voices say a lone word in its carrier sentence now ("female.sl", "male.sl" carrier voices), yet their plain clips from before are not old-style: not in the carrier re-voice\'s queue nor the lazy re-voice\'s (asked for or not), not counted as old-style, not stale, no upgrade wanted',
        carrierVoice('female.sl') && carrierVoice('male.sl') && el.carrierFor('sol', 'female.sl') === 'Beseda je: sol.' && old.every(c => !ks.oldStyle(c) && !ks.stale(c) && !ks.wantsUpgrade(c)) &&
          ks.revoiceQueue(false).length === 0 && ks.lazyQueue(false).map(c => c.key).join() === 'female:kis' && ks.lazyQueue().length === 0 && cs0.old === 0 && cs0.old_chars === 0 && cs0.asked === 0,
        [old.map(c => [c.key, ks.oldStyle(c), ks.stale(c)]), ks.lazyQueue(false).map(c => c.key), cs0])
      const t1 = fe.st.tts.length
      ks.requested(ninaOld.file)
      ks.requested(lukaOld.file)
      const saidF = await ks.say('sol', 'female')
      const saidM = await ks.say('dober dan', 'male')
      // the local clip asked for: the lazy re-voice runs, and makes its clip in Nina's voice (a new lone word)
      ks.requested(local.file)
      for (let i = 0; i < 80 && !ks.get('kis', 'female.sl'); i++) await Bun.sleep(25)
      await Bun.sleep(60)
      const kis = ks.get('kis', 'female.sl')
      const sent = fe.st.tts.slice(t1)
      const words = await ks.revoiceWords()
      check('…asked for (a download, /voice/say): the clips from before served as they are, their asks counted, nothing sent for them; the lazy re-voice that ran meanwhile, and voice-build --revoice-words, leave them as they were (file, no method, the old file there, the index on them)',
        saidF?.file === ninaOld.file && saidM?.file === lukaOld.file && sent.every(c => !c.body.text.includes('sol') && !/dober dan/i.test(c.body.text)) && words.made === 0 && words.plain === 0 && words.left === 0 && fe.st.tts.length === t1 + sent.length &&
          old.every(c => { const now = ks.get(c.norm, c.voice); return now?.file === c.file && !now.method && now.hits === (c.hits ?? 0) + 2 && existsSync(join(ks.filesDir, c.file)) }) &&
          ks.index().sol?.female === `/voice/file/${ninaOld.file}` && ks.index()['dober dan']?.male === `/voice/file/${lukaOld.file}` && ks.carrierStats().old === 0,
        [saidF, saidM, sent.map(c => c.body.text), words, old.map(c => ks.get(c.norm, c.voice))])
      check('…the new lone word beside them, made by the lazy re-voice in Nina\'s voice: inside its carrier sentence ("Beseda je: kis.", /with-timestamps, Slovenian), cut out, costing the sentence (without ffmpeg: plainly); the local clip kept',
        !!kis && kis.engine_voice === NINA && sent.length === 1 && sent[0].voice === NINA && sent[0].body.language_code === 'sl' &&
          (ffmpeg ? !!sent[0].timestamps && sent[0].body.text === 'Beseda je: kis.' && kis.method === 'carrier' && kis.chars === 15 : !sent[0].timestamps && sent[0].body.text === 'kis' && !kis.method) &&
          ks.get('kis', 'female')?.file === local.file && ks.index().kis?.female === `/voice/file/${kis.file}`,
        [kis, sent.map(c => [c.body.text, !!c.timestamps])])
      ks.close()
    }

    // the local voice first (LANI_VOICE_NARRATOR_SL=gepard), on the same clips: the local ones again, each kept
    const gs = new VoiceStore({ appDir: dir, engines: [el, gp], queueDelayMs: 0, level: null, narrators: 'gepard', language: 'sl' })
    const gi = gs.index()
    check('the local voice first, on the same store: the app gets the local clips again (kept), an ElevenLabs one where there is no local one; nothing waits for ElevenLabs',
      gi.mleko?.female === `/voice/file/${lw!.file}` && gi.mleko?.male === `/voice/file/${lm!.file}` && gi.kruh?.female === `/voice/file/${w!.file}` && gs.upgradeQueue(false).length === 0 && gs.narratorStats().waiting === 0 && gs.narratorStats().mode === 'gepard', gi.mleko)
    const g1 = await gs.voice('Dobro jutro.', 'female', 'test')
    fg.st.busy = true
    const g2 = await gs.voice('Dober večer.', 'female', 'test')
    fg.st.busy = false
    check('…a new line: the local voice (nina); the worker can\'t: the narrator\'s Slovene ElevenLabs voice (Nina), never Matilda',
      g1?.engine === 'gepard' && g1.engine_voice === 'nina' && g2?.engine === 'elevenlabs' && g2.voice === 'female.sl' && fe.st.tts.at(-1)?.voice === NINA && fe.st.tts.every(c => !english.includes(c.voice)), [g1, g2])
    const tts1 = fe.st.tts.length
    const r1 = await gs.redo('sir', 'female')
    const r2 = await gs.redo('sir', 'female')
    check('…a re-record (long press) of the English clip from before: made in the local voice now (ana: a lone word), not counted, the old file gone; again: not_needed (the local voice says it the same way)',
      r1.status === 'done' && r1.clip.engine === 'gepard' && r1.clip.engine_voice === 'ana' && r1.clip.hits === 4 && !existsSync(join(gs.filesDir, old)) && fe.st.tts.length === tts1 && gs.revoicedToday() === 4 &&
        r2.status === 'not_needed' && r2.clip.file === r1.clip.file, [r1, r2])
    // the female narrator's local clips: a lone word wants ana, a sentence nina (one from before without its voice was ana's)
    const put = (text: string, voice: string, ev: string | null) => {
      const key = `${voice}:${normalizeText(text)}`
      const f = `${createHash('sha1').update(`smoke|narrator|${key}`).digest('hex')}.mp3`
      writeFileSync(join(gs.filesDir, f), mp3(`gp:${key}`))
      gs.database.query('INSERT OR REPLACE INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, engine_voice) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)').run(key, normalizeText(text), text, voice, 'gepard', f, text.length, '2026-10-02T10:00:00.000Z', 'pack:hrana', ev)
      return f
    }
    const fZlica = put('žlica', 'female', 'nina')
    put('vilice', 'female', null)
    const fLine = put('Sir je mehak.', 'female', null)
    put('Juha je vroča.', 'female', 'nina')
    put('jajce', 'male', null)
    const q = gs.narratorQueue(c => c.source.startsWith('pack:'))
    check('the narrator queue: a female lone word in nina waits (for ana), a sentence in ana waits (for nina); the lone word in ana, the sentence in nina, the male\'s marko don\'t; the clips in Nina\'s voice are no part of it',
      q.todo.map(c => c.key).sort().join() === 'female:sir je mehak,female:žlica' && !q.todo.some(c => c.voice.endsWith('.sl')) && !q.skipped.length, q.todo.map(c => c.key))
    const g0 = fg.st.calls.length
    const rn = await gs.revoiceNarrators(c => c.source.startsWith('pack:'), { waitMs: 0 })
    check('…made again by the worker: the lone word in ana, the sentence in nina, the old files gone; nothing waits then; the clips in Nina\'s voice as they were',
      rn.made === 2 && rn.left === 0 && gs.get('žlica', 'female')?.engine_voice === 'ana' && gs.get('sir je mehak', 'female')?.engine_voice === 'nina' && fg.st.calls.slice(g0).map(c => c.speaker).sort().join() === 'ana,nina' &&
        !existsSync(join(gs.filesDir, fZlica)) && !existsSync(join(gs.filesDir, fLine)) && gs.get('mleko', 'female.sl')?.file === up!.file, [rn, fg.st.calls.slice(g0)])
    gs.close()

    // a cast whose narrators have no Slovene voice: the local voice, else none, never the English ones
    const castDir = tempDir('lani-narrators-cast-')
    const bare = JSON.parse(readFileSync(CAST_FILE, 'utf8'))
    for (const n of ['female', 'male']) delete bare.speakers[n].languages
    writeFileSync(join(castDir, 'voice-cast.json'), JSON.stringify(bare))
    process.env.LANI_VOICE_CAST = join(castDir, 'voice-cast.json')
    const bs = new VoiceStore({ appDir: tempDir('lani-narrators-bare-'), engines: [el, gp], queueDelayMs: 0, level: null, narrators: 'elevenlabs', language: 'sl' })
    const tts2 = fe.st.tts.length
    const b1 = await bs.voice('Lahko noč.', 'female', 'test')
    fg.st.busy = true
    const b2 = await bs.voice('Dobro jutro, Jan.', 'male', 'test')
    fg.st.busy = false
    delete process.env.LANI_VOICE_CAST
    check('…a cast whose narrators have no Slovene voice: the local voice; the worker can\'t: none (the phone speaks); never Matilda or Daniel', b1?.engine === 'gepard' && b1.voice === 'female' && b2 === null && fe.st.tts.length === tts2, [b1, b2])
    bs.close()
    // a village in another language (no Slovene narrators): the narrators speak as the cast has them, Matilda first
    const en = new VoiceStore({ appDir: tempDir('lani-narrators-en-'), engines: [el, gp], queueDelayMs: 0, level: null })
    check('…a store whose narrators speak as the cast has them (another village): Matilda first, as before', (await en.voice('Dober večer, Jan.', 'female', 'test'))?.engine === 'elevenlabs' && fe.st.tts.at(-1)?.voice === cast().female.elevenlabs && en.narratorQueue(() => true).todo.length === 0 && !en.narrators)
    en.close()
  }

  // --- a lone word in a carrier sentence, cut out (in-process against the fake) ---------------------------
  {
    const fe = fakeEleven()
    const cuts: [number, number][] = []
    // the node's ffmpeg; without one, a stand-in that keeps the take's first half (the store's logic is the same)
    const cut: Cutter = async (take, from, to) => {
      cuts.push([from, to])
      return ffmpeg ? ffmpeg(take, from, to) : take.slice(0, Math.floor(take.length / 2))
    }
    const logged: string[] = []
    const log = (...a: unknown[]) => void logged.push(a.join(' '))
    const el = new ElevenLabs({ key: 'test-key', base: fe.url, cut, log })
    const vs = new VoiceStore({ appDir: mkdtempSync(join(tmpdir(), 'lani-carrier-')), engines: [el], queueDelayMs: 0 })
    check('short texts: a word, up to three, without sentence punctuation', isShortText('kosilo') && isShortText(' dober dan ') && isShortText('hvala lepa, Micka') && !isShortText('Kako si?') && !isShortText('Dober dan!') && !isShortText('kosilo je na mizi') && !isShortText('Grem.') && !isShortText('  '))
    check('the carrier: the narrators, not the villagers\' voices, in a language with a carrier sentence', carrierVoice('female') && carrierVoice('male') && !carrierVoice('grandma') && !carrierVoice('@micka') && el.carrierFor('kosilo', 'female') === 'Beseda je: kosilo.' && !el.carrierFor('kosilo', 'grandma') && !new ElevenLabs({ key: 'test-key', language: 'hr', cut }).carrierFor('ručak', 'female'))
    const carrierIn = (language: string, w: string) => new ElevenLabs({ key: 'test-key', language, cut }).carrierFor(w, 'female')
    check('… every village language has its carrier sentence: Italian, German, English', carrierIn('it', 'pranzo') === 'La parola è: pranzo.' && carrierIn('de', 'Mittagessen') === 'Das Wort ist: Mittagessen.' && carrierIn('en', 'lunch') === 'The word is: lunch.')
    await el.subscription()
    const left0 = el.remaining()!
    const k = await vs.voice('kosilo', 'female', 'test')
    const call = fe.st.tts.at(-1)
    check('a lone word in a narrator voice: its carrier sentence through /with-timestamps (eleven_v4_turbo, Slovenian, Matilda, mp3)', !!call?.timestamps && call.body.text === 'Beseda je: kosilo.' && call.body.model_id === 'eleven_v4_turbo' && call.body.language_code === 'sl' && call.query === '?output_format=mp3_44100_128' && call.voice === 'XrExE9yKIg1WjnnlVkGX' && fe.st.tts.length === 1, call)
    // "kosilo" is characters 11–16 of its carrier sentence: from 0.2 + 11 × 0.07 s to 0.2 + 17 × 0.07 s
    check('the cut: the word\'s timestamps, from 0.06 s before to 0.12 s after', cuts.length === 1 && Math.abs(cuts[0][0] - 0.91) < 1e-6 && Math.abs(cuts[0][1] - 1.51) < 1e-6, cuts)
    const kept = k && vs.path(k.file) ? new Uint8Array(readFileSync(vs.path(k.file)!)) : new Uint8Array()
    const take = fe.st.takes.at(-1) ?? new Uint8Array()
    check('the clip is the cut: other bytes than the full take, and shorter (the word, about 0.6 s of 1.9 s)', k?.method === 'carrier' && k.engine === 'elevenlabs' && Buffer.compare(kept, take) !== 0 && mp3Duration(kept) > 0.3 && mp3Duration(kept) < mp3Duration(take) - 0.8, [k, mp3Duration(kept), mp3Duration(take)])
    check('the quota counts the carrier sentence, and the clip what it cost', el.remaining() === left0 - 18 && fe.st.used === 18 && k?.chars === 18, [el.remaining(), k?.chars])
    const sentence = await vs.voice('Kosilo je na mizi, Jan.', 'female', 'test')
    check('a sentence: asked for as it is', !fe.st.tts.at(-1)?.timestamps && fe.st.tts.at(-1)?.body.text === 'Kosilo je na mizi, Jan.' && !!sentence && !sentence.method, sentence)
    const villager = await vs.voice('kosilo', 'grandma', 'test')
    check('a villager\'s voice: asked for as it is', !fe.st.tts.at(-1)?.timestamps && fe.st.tts.at(-1)?.body.text === 'kosilo' && fe.st.tts.at(-1)?.voice === cast().grandma.elevenlabs && !!villager && !villager.method, villager)
    // the word not in the timestamps: a plain request, logged; the carrier take was paid for too
    fe.st.align = 'other'
    const n0 = fe.st.tts.length
    const u0 = fe.st.used
    const r0 = el.remaining()!
    const miss = await vs.voice('žlica', 'female', 'test')
    fe.st.align = 'ok'
    const both = 'Beseda je: žlica.'.length + 'žlica'.length
    check('the word not in the timestamps: asked for plainly and logged, kept as "plain", both takes counted', fe.st.tts.length === n0 + 2 && !!fe.st.tts[n0]?.timestamps && !fe.st.tts[n0 + 1]?.timestamps && fe.st.tts[n0 + 1]?.body.text === 'žlica' && miss?.method === 'plain' && miss.chars === both && fe.st.used - u0 === both && r0 - el.remaining()! === both && logged.some(l => l.includes('without its carrier sentence')), [miss, logged])
    const bare = new ElevenLabs({ key: 'test-key', base: fe.url, cut: null, log })
    const b = await bare.synth('gozd', 'female')
    check('no ffmpeg: a short word is asked for plainly, and that is logged', !!b && !b.method && !fe.st.tts.at(-1)?.timestamps && fe.st.tts.at(-1)?.body.text === 'gozd' && bare.cost('gozd', 'female') === 4 && el.cost('gozd', 'female') === 16 && logged.some(l => l.includes('no ffmpeg')) && !ffmpegCutter('/nonexistent/ffmpeg') && !ffmpegCutter('off'))
    const broken = new ElevenLabs({ key: 'test-key', base: fe.url, cut: async () => null, log })
    const n1 = fe.st.tts.length
    const br = await broken.synth('lipa', 'female')
    const br2 = await broken.synth('breza', 'female')
    check('ffmpeg failing: asked for plainly (still old-style: re-voiced later), and no carrier from then on (no takes paid for nothing)', !!br && !br.method && !!br2 && fe.st.tts.length === n1 + 3 && !!fe.st.tts[n1]?.timestamps && !fe.st.tts[n1 + 1]?.timestamps && !fe.st.tts[n1 + 2]?.timestamps && logged.some(l => l.includes('could not cut')), fe.st.tts.slice(n1).map(c => c.body.text))
    const al = (s: string) => {
      const c = [...s]
      return { characters: c, character_start_times_seconds: c.map((_, i) => i / 10), character_end_times_seconds: c.map((_, i) => (i + 1) / 10) }
    }
    const je = wordSpan(al('Beseda je: je.'), 'je')
    check('the word\'s timestamps: its own place at the end, not the carrier\'s "je"', !!je && Math.abs(je.start - 1.1) < 1e-9 && Math.abs(je.end - 1.3) < 1e-9 && !wordSpan(al('Beseda je: kosilo.'), 'je') && !!wordSpan(al('Beseda je: Dober dan.'), 'dober dan'), je)
    vs.close()

    // --- the clips from before: re-voiced lazily, as the app asks for them ------------------------------------------------
    // a store as it was: no method, no hits; narrator words said plainly, a sentence, a villager's word, a local clip
    const dir = mkdtempSync(join(tmpdir(), 'lani-revoice-'))
    mkdirSync(join(dir, 'voice/files'), { recursive: true })
    const old = new Database(join(dir, 'voice/voice.db'))
    old.exec('CREATE TABLE clips (key TEXT PRIMARY KEY, norm TEXT NOT NULL, text TEXT NOT NULL, voice TEXT NOT NULL, engine TEXT NOT NULL, file TEXT NOT NULL, chars INTEGER NOT NULL, created_at TEXT NOT NULL, source TEXT NOT NULL)')
    const oldFile: Record<string, string> = {}
    ;[['potok', 'female', 'elevenlabs'], ['gozd', 'female', 'elevenlabs'], ['hvala', 'female', 'elevenlabs'], ['miza', 'male', 'elevenlabs'], ['Gozd je temen.', 'female', 'elevenlabs'], ['gozd', 'grandma', 'elevenlabs'], ['sir', 'female', 'gepard']].forEach(([text, voice, engine], i) => {
      const file = `${createHash('sha1').update(`old|${voice}|${text}`).digest('hex')}.mp3`
      oldFile[`${voice}:${text}`] = file
      writeFileSync(join(dir, 'voice/files', file), mp3(`old:${voice}:${text}`))
      old.query('INSERT INTO clips VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)').run(`${voice}:${normalizeText(text)}`, normalizeText(text), text, voice, engine, file, text.length, `2026-09-0${i + 1}T10:00:00.000Z`, 'pack:old')
    })
    old.close()
    const updated: number[] = []
    const fresh = new ElevenLabs({ key: 'test-key', base: fe.url, cut, refreshMs: 0 }) // reads the subscription every time
    // the carrier re-voice alone: the store's rows are from before the model too, whose lazy upgrade is checked below
    const rv = new VoiceStore({ appDir: dir, engines: [fresh], queueDelayMs: 0, revoiceDaily: 2, lazyModel: false, onUpdated: n => void updated.push(n) })
    const s0 = rv.carrierStats()
    check('an old store: its narrator words are old-style (not a sentence, a villager\'s word, a local clip); re-voicing them costs their carrier sentences', s0.old === 4 && s0.old_chars === ['potok', 'gozd', 'hvala', 'miza'].reduce((n, w) => n + `Beseda je: ${w}.`.length, 0) && s0.asked === 0 && s0.carrier === 0 && s0.daily === 2, s0)
    fe.st.used = 0
    const t0 = fe.st.tts.length
    // the app asks: hvala three times, gozd twice (once through /voice/say), potok once
    for (let i = 0; i < 3; i++) rv.requested(oldFile['female:hvala'])
    const said = await rv.say('gozd', 'female')
    rv.requested(oldFile['female:gozd'])
    rv.requested(oldFile['female:potok'])
    check('the app gets the old clip at once', said?.file === oldFile['female:gozd'] && fe.st.tts.length === t0, said)
    for (let i = 0; i < 100 && !updated.length; i++) await Bun.sleep(50)
    const redone = fe.st.tts.slice(t0)
    check('re-voiced soon, most asked for first, up to the daily cap (2 here): hvala, then gozd; the app is told', redone.length === 2 && redone.every(c => c.timestamps) && redone[0]?.body.text === 'Beseda je: hvala.' && redone[1]?.body.text === 'Beseda je: gozd.' && rv.revoicedToday() === 2 && updated[0] === 2, redone.map(c => c.body.text))
    const gozd = rv.get('gozd', 'female')
    check('a re-voiced clip: marked, under a new file name (the phone downloads it), the old file gone, the index on the new one', gozd?.method === 'carrier' && gozd.file !== oldFile['female:gozd'] && !rv.path(oldFile['female:gozd']) && !!rv.path(gozd.file) && rv.index().gozd?.female === `/voice/file/${gozd.file}` && gozd.hits === 2 && rv.index().gozd?.grandma === `/voice/file/${oldFile['grandma:gozd']}`, gozd)
    check('potok waits for tomorrow (the cap); miza, never asked for, is not re-voiced', rv.revoiceQueue().map(c => c.text).join() === 'potok' && rv.carrierStats().old === 2 && rv.carrierStats().asked === 1)
    rv.requested(gozd!.file)
    rv.requested(oldFile['female:Gozd je temen.'])
    rv.requested(oldFile['grandma:gozd'])
    await Bun.sleep(100)
    check('a re-voiced clip, a sentence, a villager\'s word: not re-voiced', fe.st.tts.length === t0 + 2)
    rv.database.exec('DELETE FROM revoices') // the next day
    fe.st.used = fe.st.limit - 5_000 - 10 // 10 characters above the reserve: less than a carrier sentence
    rv.requested(oldFile['female:potok'])
    await Bun.sleep(150)
    check('the reserve stays for live lines: no re-voice that would dip into it', fe.st.tts.length === t0 + 2 && rv.get('potok', 'female')?.file === oldFile['female:potok'] && rv.revoicedToday() === 0)
    const live = await rv.say('jabolko', 'female')
    check('…while a live word may use it (in its carrier sentence)', live?.method === 'carrier' && fe.st.tts.at(-1)?.body.text === 'Beseda je: jabolko.', live)
    // the word not in its take: the clip stays as it is, marked, and isn't tried again; the take counts for the day
    fe.st.used = 0
    fe.st.align = 'other'
    rv.requested(oldFile['female:potok'])
    for (let i = 0; i < 100 && !rv.get('potok', 'female')?.method; i++) await Bun.sleep(50)
    fe.st.align = 'ok'
    const potok = rv.get('potok', 'female')
    const t2 = fe.st.tts.length
    rv.requested(oldFile['female:potok'])
    await Bun.sleep(100)
    check('re-voicing a word its take lacks: it keeps its clip, marked "plain", not tried again; counted for the day', potok?.method === 'plain' && potok.file === oldFile['female:potok'] && !!rv.path(potok.file) && rv.revoicedToday() === 1 && fe.st.tts.length === t2 && !rv.revoiceQueue().length, potok)
    rv.close()
  }

  // --- a re-record on request: the app's long press on 🔊 (in-process against the fake) ----------------------------
  {
    const fe = fakeEleven()
    const cut: Cutter = async (take, from, to) => (ffmpeg ? ffmpeg(take, from, to) : take.slice(0, Math.floor(take.length / 2)))
    const updated: number[] = []
    const el = new ElevenLabs({ key: 'test-key', base: fe.url, cut, refreshMs: 0 }) // reads the subscription every time
    const vs = new VoiceStore({ appDir: mkdtempSync(join(tmpdir(), 'lani-redo-')), engines: [el], queueDelayMs: 0, revoiceDaily: 1, redoDaily: 4, onUpdated: n => void updated.push(n) })
    const d = new Date()
    const today = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    check('re-records on request have a day\'s cap of their own (LANI_VOICE_REDO_DAILY, 30 by default), apart from the lazy re-voice\'s',
      vs.redoDaily === 4 && vs.revoiceDaily === 1 && new VoiceStore({ appDir: mkdtempSync(join(tmpdir(), 'lani-redo-')), engines: [] }).redoDaily === DEFAULT_REDO_DAILY && DEFAULT_REDO_DAILY === 30 &&
        redoDaily({ LANI_VOICE_REDO_DAILY: '90' }) === 90 && redoDaily({ LANI_VOICE_REDO_DAILY: '0' }) === 0 && redoDaily({}) === undefined && redoDaily({ LANI_VOICE_REDO_DAILY: 'all' }) === undefined)
    const k0 = await vs.voice('kosilo', 'female', 'pack:test')
    const t0 = fe.st.tts.length
    const r1 = await vs.redo('Kosilo', 'female')
    const c1 = r1.status === 'done' ? r1.clip : undefined
    check('a re-record of a narrator word: made now in its carrier sentence, under a new name, the old file gone, the index on it, the app told, counted for the day',
      !!c1 && c1.method === 'carrier' && !!k0 && c1.file !== k0.file && !vs.path(k0.file) && !!vs.path(c1.file) && c1.source === 'pack:test' && vs.index().kosilo?.female === `/voice/file/${c1.file}` &&
        fe.st.tts.length === t0 + 1 && !!fe.st.tts.at(-1)?.timestamps && fe.st.tts.at(-1)?.body.text === 'Beseda je: kosilo.' && updated.join() === '1' && vs.redosToday() === 1 && vs.lazyToday() === 0, [r1, updated])
    const r2 = await vs.redo('kosilo', 'female')
    check('once a clip a day: the second is "not_needed", with the take from before; nothing asked, not counted', r2.status === 'not_needed' && r2.clip.file === c1?.file && fe.st.tts.length === t0 + 1 && vs.redosToday() === 1, r2)
    const s0 = await vs.voice('Kosilo je na mizi.', 'female', 'test')
    const r3 = await vs.redo('Kosilo je na mizi.', 'female')
    check('a sentence: a plain re-take (no carrier sentence), under a new name', r3.status === 'done' && !r3.clip.method && r3.clip.file !== s0?.file && fe.st.tts.at(-1)?.body.text === 'Kosilo je na mizi.' && !fe.st.tts.at(-1)?.timestamps && vs.redosToday() === 2, r3)
    const g0 = await vs.voice('kosilo', 'grandma', 'test', { live: true })
    const r4 = await vs.redo('kosilo', 'grandma')
    check('a villager\'s voice: a word alone re-taken in it inside its carrier sentence (since 2026-10-19), under a new name', r4.status === 'done' && r4.clip.voice === 'grandma' && r4.clip.file !== g0?.file && r4.clip.method === 'carrier' && fe.st.tts.at(-1)?.voice === cast().grandma.elevenlabs && !!fe.st.tts.at(-1)?.timestamps && fe.st.tts.at(-1)?.body.text === 'Beseda je: kosilo.' && vs.redosToday() === 3 && vs.lazyToday() === 0, r4)
    // old-style narrator words the app asks for: the lazy re-voice takes one (its cap of 1), the re-records left untouched;
    // the next waits for tomorrow, and a re-record of it still goes
    const oldOf = (w: string) => {
      const f = `${createHash('sha1').update(`redo|old|${w}`).digest('hex')}.mp3`
      writeFileSync(join(vs.filesDir, f), mp3(`old:${w}`))
      vs.database.query('INSERT INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, method, hits) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 0)').run(`female:${w}`, w, w, 'female', 'elevenlabs', f, w.length, '2026-09-01T10:00:00.000Z', 'pack:old')
      return f
    }
    const oldPotok = oldOf('potok')
    const oldMiza = oldOf('miza')
    vs.requested(oldPotok)
    for (let i = 0; i < 100 && !vs.get('potok', 'female')?.method; i++) await Bun.sleep(25)
    check('the lazy re-voice spends its own cap (1 of 1), not the re-records\' (still 3 of 4)', vs.get('potok', 'female')?.method === 'carrier' && vs.lazyToday() === 1 && vs.redosToday() === 3, [vs.get('potok', 'female'), vs.lazyToday(), vs.redosToday()])
    const t1 = fe.st.tts.length
    vs.requested(oldMiza)
    await Bun.sleep(100)
    const lazyWaits = fe.st.tts.length === t1 && vs.get('miza', 'female')?.file === oldMiza
    const r5 = await vs.redo('miza', 'female')
    check('…its cap spent, the next asked for waits; a re-record of it still goes (4 of 4)', lazyWaits && r5.status === 'done' && r5.clip.method === 'carrier' && !vs.path(oldMiza) && vs.redosToday() === 4 && vs.lazyToday() === 1 && !vs.revoiceQueue().length, [lazyWaits, r5])
    const st0 = await vs.voice('Stol je lesen.', 'female', 'test')
    const t2 = fe.st.tts.length
    const r6 = await vs.redo('Stol je lesen.', 'female')
    check('the day\'s cap spent: "limit" (cap), nothing asked, the clip as it was', r6.status === 'limit' && r6.reason === 'cap' && r6.message.includes('4 of 4') && fe.st.tts.length === t2 && vs.get('stol je lesen', 'female')?.file === st0?.file, r6)
    const cs = vs.carrierStats()
    check('the carrier stats show today\'s re-voices of the lazy re-voice\'s cap and the re-records of theirs', cs.today === 1 && cs.daily === 1 && cs.redos === 4 && cs.redo_daily === 4, cs)
    // a new day: both counts start again
    vs.database.query('DELETE FROM revoices WHERE day = ?').run(today)
    vs.database.query('DELETE FROM redos WHERE day = ?').run(today)
    fe.st.used = fe.st.limit - 5_000 - 10 // 10 characters above the reserve: less than the sentence
    const r7 = await vs.redo('Stol je lesen.', 'female')
    check('the reserve stays for live lines: "limit" (reserve), nothing asked', r7.status === 'limit' && r7.reason === 'reserve' && fe.st.tts.length === t2 && vs.revoicedToday() === 0, r7)
    fe.st.used = 0
    const r8 = await vs.redo('jabolko', 'female')
    check('a text without a clip: voiced now as a live line is (in its carrier sentence), not counted', r8.status === 'done' && r8.clip.method === 'carrier' && fe.st.tts.at(-1)?.body.text === 'Beseda je: jabolko.' && vs.revoicedToday() === 0 && !vs.redoneToday('female:jabolko'), r8)
    check('a voice not in the cast: failed, nothing asked', (await vs.redo('stol', 'soprano')).status === 'failed' && fe.st.tts.length === t2 + 1)
    fe.st.fail = 500
    const r9 = await vs.redo('Stol je lesen.', 'female')
    fe.st.fail = 0
    check('ElevenLabs failing: "failed", the clip as it was, not counted, not marked as re-recorded', r9.status === 'failed' && vs.get('stol je lesen', 'female')?.file === st0?.file && vs.revoicedToday() === 0 && !vs.redoneToday('female:stol je lesen'), r9)
    vs.close()
  }

  // --- a voice that hisses: the cast's `denoise` (in-process; voice-build --denoise on a copy) ------------------------
  {
    check(
      'the cast filters the voices that hiss (Stari Janez\'s and Micka\'s own, the young man, gruff, France\'s own), and nobody else\'s (the girl\'s Piku is clean)',
      ['@janez', '@micka', '@france', 'young-man', 'gruff'].every(v => denoiseOf(v) === 'hiss') && !denoiseOf('girl') && !denoiseOf('grandpa') && !denoiseOf('female') && !denoiseOf('@luka'),
      denoiseVoices(),
    )
    // a cast of its own: grandma and the female narrator filtered (true: the first preset), wrong entries left out
    const castDir = mkdtempSync(join(tmpdir(), 'lani-denoise-cast-'))
    const castJson = JSON.parse(readFileSync(CAST_FILE, 'utf8'))
    castJson.denoise = { _comment: 'test', grandma: true, female: 'hiss', '@janez': 'hiss-strong', grandpaa: 'hiss', male: 'loud', boy: false }
    writeFileSync(join(castDir, 'voice-cast.json'), JSON.stringify(castJson))
    process.env.LANI_VOICE_CAST = join(castDir, 'voice-cast.json')
    check('denoise in the cast: a speaker or someone\'s own voice → a preset (true: hiss); a typo, an unknown preset, false: left out, the rest of the cast stays',
      JSON.stringify(denoiseVoices()) === JSON.stringify({ grandma: 'hiss', female: 'hiss', '@janez': 'hiss-strong' }) && cast().male?.elevenlabs === castJson.speakers.male.elevenlabs && Object.keys(cast()).length === Object.keys(castJson.speakers).length, denoiseVoices())

    // the real filter (ffmpeg): hiss at −51 dBFS all through, a tone in it at the start and at the end (so there is no
    // silence at the edges to trim: EDGES), a pause of hiss alone between them (measured from 0.8 s: past the gate's release)
    const noisy = ffmpeg && Bun.spawnSync(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i', "aevalsrc='0.5*sin(2*PI*440*t)*(between(t,0,0.5)+between(t,1.3,1.8))+0.005*(2*random(0)-1)':s=44100:d=1.8", '-ac', '1', '-c:a', 'libmp3lame', '-b:a', '128k', '-f', 'mp3', 'pipe:1'])
    const pcm = (b: Uint8Array) => new Float32Array(Bun.spawnSync(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'mp3', '-i', 'pipe:0', '-ac', '1', '-ar', '44100', '-f', 'f32le', 'pipe:1'], { stdin: b }).stdout.buffer)
    const level = (x: Float32Array, from: number, to: number) => {
      let e = 0
      for (let k = Math.floor(from * 44100); k < Math.floor(to * 44100); k++) e += x[k] ** 2
      return 10 * Math.log10(e / ((to - from) * 44100) + 1e-12)
    }
    const onset = (x: Float32Array) => x.findIndex((v, i) => i > 0.9 * 44100 && Math.abs(v) > 0.25) / 44100 // the second tone's
    let hissy = mp3('not audio')
    if (noisy && noisy.exitCode === 0) {
      hissy = new Uint8Array(noisy.stdout)
      const f = ffmpegDenoiser()!
      const out = await f(hissy, 'hiss')
      const [a, b] = [pcm(hissy), out ? pcm(out) : new Float32Array()]
      check('the filter (hiss): the hiss goes (over 20 dB), the tone stays (within 1 dB), nothing moves in time (the 25 ms of afftdn put back) and the length stays',
        !!out && isMp3(out) && level(a, 0.8, 1.2) - level(b, 0.8, 1.2) > 20 && Math.abs(level(a, 1.4, 1.7) - level(b, 1.4, 1.7)) < 1 && Math.abs(onset(a) - onset(b)) < 0.002 && Math.abs(a.length - b.length) < 0.01 * 44100,
        [level(a, 0.8, 1.2), level(b, 0.8, 1.2), level(a, 1.4, 1.7), level(b, 1.4, 1.7), onset(a), onset(b), a.length, b.length])
      check('the filter: hiss-strong is a preset too; no ffmpeg, no filter', !!(await f(hissy, 'hiss-strong')) && !ffmpegDenoiser('off') && !ffmpegDenoiser('/nonexistent/ffmpeg') && (await f(mp3('not audio'), 'hiss')) === null)
    }

    // the store: a flagged voice's new clips go through the filter (a stand-in marks them), once, and get names of their own
    const fe = fakeEleven()
    const fg = fakeGepard()
    const MARK = new TextEncoder().encode('|denoised')
    const filtered: string[] = []
    const stand: Denoiser = async (b, preset) => {
      filtered.push(preset)
      return new Uint8Array([...b, ...MARK])
    }
    const marked = (vs: VoiceStore, c: Clip | null | undefined) => !!c && new TextDecoder().decode(readFileSync(join(vs.filesDir, c.file))).endsWith('|denoised')
    const cut: Cutter = async (take, from, to) => (ffmpeg ? ffmpeg(take, from, to) : take.slice(0, Math.floor(take.length / 2)))
    const logged: string[] = []
    const el = new ElevenLabs({ key: 'test-key', base: fe.url, cut, refreshMs: 0 })
    const updated: number[] = []
    const dir = mkdtempSync(join(tmpdir(), 'lani-denoise-'))
    // the stand-in filter alone (no level, which would encode the mark away; the level's checks are below)
    const vs = new VoiceStore({ appDir: dir, engines: [el, new Gepard({ url: fg.url })], queueDelayMs: 0, denoise: stand, level: null, log: (...a) => void logged.push(a.join(' ')), onUpdated: n => void updated.push(n) })
    const g = await vs.voice('Dober dan, Jan!', 'grandma', 'test')
    check('a flagged voice\'s new clip goes through its filter before it is stored, and is marked', g?.denoise === 'hiss' && marked(vs, g) && filtered.join() === 'hiss' && vs.get('dober dan jan', 'grandma')?.denoise === 'hiss', [g, filtered])
    const m = await vs.voice('Dober dan, Jan!', 'male', 'test')
    check('another voice\'s clip is stored as it is', !!m && !m.denoise && !marked(vs, m) && filtered.length === 1, m)
    const k = await vs.voice('kosilo', 'female', 'test')
    check('a carrier cut of a flagged narrator: cut, then filtered', k?.method === 'carrier' && k.denoise === 'hiss' && marked(vs, k) && filtered.length === 2, k)
    const r = await vs.redo('Dober dan, Jan!', 'grandma')
    check('a re-record of a flagged voice: the new take filtered once (not the old one again), under a new name', r.status === 'done' && r.clip.denoise === 'hiss' && marked(vs, r.clip) && r.clip.file !== g?.file && !vs.path(g!.file) && filtered.length === 3 && !new TextDecoder().decode(readFileSync(join(vs.filesDir, r.clip.file))).endsWith('|denoised|denoised'), r)
    fe.st.used = fe.st.limit // ElevenLabs can't: the local worker's clip, which doesn't hiss
    const lg = await vs.voice('Kruh je še topel.', 'grandma', 'test')
    fe.st.used = 0
    check('a local clip of a flagged voice is stored as it is (the hiss is in the ElevenLabs voice)', lg?.engine === 'gepard' && !lg.denoise && filtered.length === 3, lg)
    check('the status shows the voices filtered, their clips filtered and waiting', (() => {
      const s = vs.denoiseStats()
      return s.voices.grandma?.filtered === 1 && s.voices.female?.filtered === 1 && s.voices['@janez']?.preset === 'hiss-strong' && s.voices.grandma.waiting === 0
    })(), vs.denoiseStats())
    // without ffmpeg (or when it fails): stored as it is, logged once, and the batch takes it up later
    const bare = new VoiceStore({ appDir: dir, engines: [el], queueDelayMs: 0, denoise: null, level: null, log: (...a) => void logged.push(a.join(' ')) })
    const u = await bare.voice('Lahko noč.', 'grandma', 'test')
    await bare.voice('Na zdravje.', 'grandma', 'test')
    const failing = new VoiceStore({ appDir: dir, engines: [el], queueDelayMs: 0, denoise: async () => null, level: null, log: (...a) => void logged.push(a.join(' ')) })
    const u2 = await failing.voice('Dobro jutro.', 'grandma', 'test')
    const unfiltered = logged.filter(l => l.includes('without its filter'))
    check('no ffmpeg, or ffmpeg failing: stored as it is, logged (no ffmpeg: once), waiting for voice-build --denoise',
      !!u && !u.denoise && !marked(bare, u) && !!u2 && !u2.denoise && unfiltered.filter(l => l.includes('no ffmpeg')).length === 1 && unfiltered.some(l => l.includes('ffmpeg failed')) && vs.denoiseQueue().map(c => c.text).join() === 'Lahko noč.,Na zdravje.,Dobro jutro.', [logged, vs.denoiseQueue().map(c => c.text)])
    bare.close()
    failing.close()

    // the batch in-process: each waiting clip once, under a new name, the old file gone only once the new one is recorded
    const waiting = vs.denoiseQueue()
    const before = Object.fromEntries(waiting.map(c => [c.key, c.file]))
    const f0 = filtered.length
    const b1 = await vs.denoiseClips({ max: 1 })
    const one = vs.get(normalizeText('Lahko noč.'), 'grandma')
    check('denoiseClips --max 1: the oldest waiting clip filtered, under a new name (the index on it), the old file gone, the app told',
      b1.done === 1 && b1.left === 2 && one?.denoise === 'hiss' && one.file !== before[one.key] && !!vs.path(one.file) && !vs.path(before[one.key]) && marked(vs, one) && vs.index()[one.norm]?.grandma === `/voice/file/${one.file}` && updated.at(-1) === 1 && filtered.length === f0 + 1, [b1, one])
    // a clip made again meanwhile (the bridge re-recorded it while the batch filtered it): the new take stays
    const racing = new VoiceStore({ appDir: dir, engines: [], level: null, denoise: async (b, preset) => {
      vs.database.query('UPDATE clips SET file = ? WHERE key = ?').run('0'.repeat(40) + '.mp3', 'grandma:na zdravje')
      return stand(b, preset)
    } })
    const b2 = await racing.denoiseClips({ max: 1 })
    check('a clip made again while it was filtered: the new take stays, the filtered file is removed', b2.done === 0 && b2.skipped === 1 && vs.get('na zdravje', 'grandma')?.file === '0'.repeat(40) + '.mp3' && !vs.path(denoisedFile(before['grandma:na zdravje'], 'hiss')), b2)
    racing.close()
    const b3 = await vs.denoiseClips()
    const b4 = await vs.denoiseClips()
    check('the rest filtered; then nothing is filtered twice', b3.done === 1 && b3.skipped === 1 && b4.done === 0 && b4.left === 1 && vs.get('dobro jutro', 'grandma')?.denoise === 'hiss' && filtered.length === f0 + 3, [b3, b4])
    vs.close()
    delete process.env.LANI_VOICE_CAST

    // voice-build --denoise on a copy of the data: a store from before the filter (no denoise column), real MP3s
    const vbData = mkdtempSync(join(tmpdir(), 'lani-denoise-data-'))
    mkdirSync(join(vbData, 'app/voice/files'), { recursive: true })
    const old = new Database(join(vbData, 'app/voice/voice.db'))
    old.exec('CREATE TABLE clips (key TEXT PRIMARY KEY, norm TEXT NOT NULL, text TEXT NOT NULL, voice TEXT NOT NULL, engine TEXT NOT NULL, file TEXT NOT NULL, chars INTEGER NOT NULL, created_at TEXT NOT NULL, source TEXT NOT NULL, method TEXT, hits INTEGER NOT NULL DEFAULT 0)')
    const oldFile: Record<string, string> = {}
    ;[['Ha! In ti boš tudi, Jan.', '@janez', 'elevenlabs'], ['Lahko noč, Jan.', '@janez', 'elevenlabs'], ['Pridi spet k lipi.', '@janez', 'elevenlabs'], ['Lahko noč, Jan.', 'grandpa', 'elevenlabs'], ['Lahko noč, Jan.', 'male', 'gepard']].forEach(([text, voice, engine], i) => {
      const file = `${createHash('sha1').update(`vb|${voice}|${text}`).digest('hex')}.mp3`
      oldFile[`${voice}:${text}`] = file
      writeFileSync(join(vbData, 'app/voice/files', file), hissy)
      old.query('INSERT INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, hits) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)').run(`${voice}:${normalizeText(text)}`, normalizeText(text), text, voice, engine, file, text.length, `2026-09-2${i}T10:00:00.000Z`, 'villager:janez', 3)
    })
    old.close()
    const env = { ...process.env, LANI_KEYS_FILE: '/nonexistent', ELEVENLABS_API_KEY: '', LANI_CULTURES_DIR: culturesDir } as Record<string, string>
    delete env.LANI_VOICE_CAST
    delete env.LANI_DATA_DIR
    delete env.LANI_VOICE_GEPARD_TEMPO
    const vb = async (...args: string[]) => {
      const p = Bun.spawn(['bun', resolve(import.meta.dir, '../../../bin/voice-build'), ...args], { env, stdout: 'pipe', stderr: 'pipe' })
      const [stdout, stderr, exitCode] = await Promise.all([new Response(p.stdout).text(), new Response(p.stderr).text(), p.exited])
      return { stdout, stderr, exitCode }
    }
    const tts0 = fe.st.tts.length
    const dry = await vb('--denoise', '--data', vbData)
    const dryJson = JSON.parse((await vb('--denoise', '--json', '--data', vbData)).stdout || '{}')
    const cols = () => {
      const d = new Database(join(vbData, 'app/voice/voice.db'), { readonly: true })
      const c = (d.query('PRAGMA table_info(clips)').all() as { name: string }[]).map(c => c.name)
      d.close()
      return c
    }
    check('voice-build --denoise (dry run): lists Janez\'s three ElevenLabs clips (not the archetype\'s, not a local clip), changes nothing, calls nobody',
      dry.exitCode === 0 && dryJson.waiting === 3 && dryJson.voices?.['@janez'] === 'hiss' && dryJson.clips?.every((c: any) => c.voice === '@janez') && dry.stdout.includes('Pridi spet k lipi.') && dry.stdout.includes('Dry run') && dry.stdout.includes('--denoise --run') &&
        !dry.stdout.includes(oldFile['grandpa:Lahko noč, Jan.']) && Object.values(oldFile).every(f => existsSync(join(vbData, 'app/voice/files', f))) && !cols().includes('denoise') && fe.st.tts.length === tts0, [dry.stdout, dry.stderr, dryJson])
    // the bridge, running on the same data: it tells the app when another process changed the clips
    const told: number[] = []
    const bridge = new VoiceStore({ appDir: join(vbData, 'app'), engines: [el], queueDelayMs: 0, onUpdated: n => void told.push(n) })
    bridge.checkExternal() // what counts as changed starts here
    check('voice-build --denoise: no --max-chars (no characters are spent), --data must be a directory', (await vb('--denoise', '--max-chars', '10', '--data', vbData)).exitCode === 2 && (await vb('--denoise', '--data', join(vbData, 'nope'))).exitCode === 2 && (await vb('--denoise', '--revoice-words')).exitCode === 2)
    if (ffmpeg) {
      const run1 = await vb('--denoise', '--run', '--max', '1', '--data', vbData)
      const run2 = await vb('--denoise', '--run', '--data', vbData)
      const run3 = await vb('--denoise', '--run', '--json', '--data', vbData)
      const d = new Database(join(vbData, 'app/voice/voice.db'), { readonly: true })
      const rows = d.query('SELECT * FROM clips').all() as Clip[]
      d.close()
      const janez = rows.filter(c => c.voice === '@janez')
      const others = rows.filter(c => c.voice !== '@janez')
      check('voice-build --denoise --run (--max 1, then the rest): each of Janez\'s clips filtered once and levelled in the same encode, under a new name, the old file gone, the rest as it was',
        run1.exitCode === 0 && run1.stdout.includes('Filtered 1 clip(s)') && run2.exitCode === 0 && run2.stdout.includes('Filtered 2 clip(s)') && run3.stdout.includes('Filtered 0 clip(s)') && cols().includes('denoise') && cols().includes('level') &&
          janez.length === 3 && janez.every(c => c.denoise === 'hiss' && c.level === LEVEL.target && c.file !== oldFile[`@janez:${c.text}`] && c.file === processedFile(oldFile[`@janez:${c.text}`], { denoise: 'hiss', level: LEVEL.target }) && existsSync(join(vbData, 'app/voice/files', c.file)) && !existsSync(join(vbData, 'app/voice/files', oldFile[`@janez:${c.text}`])) && c.hits === 3) &&
          others.every(c => !c.denoise && c.level == null && c.file === oldFile[`${c.voice}:${c.text}`] && existsSync(join(vbData, 'app/voice/files', c.file))) && fe.st.tts.length === tts0,
        [run1.stdout + run1.stderr, run2.stdout, run3.stdout, rows])
      const clip = new Uint8Array(readFileSync(join(vbData, 'app/voice/files', janez[0].file)))
      check('the filtered clip is audio, as long as before (no silence at its edges), with less hiss', isMp3(clip) && Math.abs(pcm(clip).length - pcm(hissy).length) < 0.01 * 44100 && level(pcm(hissy), 0.8, 1.2) - level(pcm(clip), 0.8, 1.2) > 20)
      const n = bridge.checkExternal()
      check('the bridge sees clips another process changed (voice-build) and tells the app once (voice_updated)', n === 3 && told.join() === '3' && bridge.checkExternal() === 0 && told.length === 1, [n, told])
      await bridge.voice('Dober večer.', 'female', 'test')
      const own = bridge.checkExternal()
      const other = new Database(join(vbData, 'app/voice/voice.db'))
      other.query('DELETE FROM clips WHERE key = ?').run(janez[0].key)
      other.close()
      check('…not for its own clips (it tells the app about those itself), not even with the next change from outside (a clip gone counts)', own === 0 && bridge.checkExternal() === 1 && told.join() === '3,1', [own, told])
    }
    bridge.close()
  }

  // --- every clip at one loudness: the level (in-process with ffmpeg; voice-build --level on a copy) -----------------
  {
    check('the level: the gain to the target, the limiter only where the peak would pass, a clip near the target kept, silence left alone, at most +15 dB',
      JSON.stringify(levelGain({ lufs: -10, peak: -6 })) === JSON.stringify({ gain: -8, limit: false, keep: false }) && levelGain({ lufs: -22, peak: -3 }).limit && !levelGain({ lufs: -22, peak: -7 }).limit &&
        levelGain({ lufs: -18.4, peak: -4 }).keep && !levelGain({ lufs: -18.4, peak: -1 }).keep && levelGain({ lufs: -70, peak: -Infinity }).keep && levelGain({ lufs: -50, peak: -40 }).gain === 15 && LEVEL.target === -18 && LEVEL.peak === -1.5,
      [levelGain({ lufs: -10, peak: -6 }), levelGain({ lufs: -50, peak: -40 })])
    check('no ffmpeg, no level', !ffmpegLeveller('off') && !ffmpegLeveller('/nonexistent/ffmpeg') && !ffmpegLoudness('off') && !ffmpegRecordingLeveller('off'))
    const said: string[] = []
    const heard = (e: Record<string, string>) => JSON.stringify(gepardPace(e, (...a) => void said.push(a.join(' '))))
    check('the local voice\'s pace: none by default (its own); LANI_VOICE_GEPARD_TEMPO a factor from 0.5 to 2, LANI_VOICE_GEPARD_PAUSES the longest pause (0.1 to 2 s), "off" or 0 for none (a wrong value logged, left out)',
      gepardPace({}) === null && heard({ LANI_VOICE_GEPARD_TEMPO: '1' }) === 'null' && heard({ LANI_VOICE_GEPARD_TEMPO: 'off' }) === 'null' && heard({ LANI_VOICE_GEPARD_TEMPO: '1.2' }) === '{"tempo":1.2,"pause":null}' &&
        heard({ LANI_VOICE_GEPARD_PAUSES: '0.4' }) === '{"tempo":1,"pause":0.4}' && heard({ LANI_VOICE_GEPARD_TEMPO: '3' }) === 'null' && heard({ LANI_VOICE_GEPARD_PAUSES: 'long' }) === 'null' && said.length === 2 && !ffmpegPace('off'),
      said)
    check('…its chain: the pauses shortened, then atempo; neither for its own pace', paceChain({ tempo: 1.05, pause: 0.4 }) === 'silenceremove=stop_periods=-1:stop_threshold=-50dB:stop_duration=0.2:stop_silence=0.2,atempo=1.05' && paceChain({ tempo: 1, pause: null }) === '' && charsPerSecond(' Dober dan. ', 2) === 5, paceChain({ tempo: 1.05, pause: 0.4 }))
    // the edges: [parts] of a 300 Hz tone, each [secs] long at [db] dBFS RMS (−120: silence), at 32 kHz
    const tone32 = (parts: [number, number][]) => {
      const x = new Float32Array(Math.round(parts.reduce((n, [d]) => n + d, 0) * 32_000))
      let i = 0
      for (const [d, db] of parts) for (let k = 0; k < Math.round(d * 32_000); k++, i++) x[i] = Math.SQRT2 * 10 ** (db / 20) * Math.sin((2 * Math.PI * 300 * i) / 32_000)
      return x
    }
    const soft = edgesOf(tone32([[0.5, -120], [0.08, -52], [0.6, -20], [0.06, -55], [0.3, -120]]), 32_000)
    const hard = edgesOf(tone32([[0.5, -120], [0.08, -66], [0.6, -20], [0.3, -120]]), 32_000)
    check('the edges: the sound from the first 20 ms over −45 dBFS, back and on while over −60 (a soft h rising, a vowel fading, kept); under −60 silence',
      Math.abs(soft.lead - 0.5) < 0.021 && Math.abs(soft.tail - 0.3) < 0.021 && Math.abs(hard.lead - 0.58) < 0.021 && Math.abs(hard.tail - 0.3) < 0.021 && EDGES.threshold === -45 && EDGES.floor === -60 &&
        // measured as loud as the gain will make it: −52 + 10 dB is over −45
        Math.abs(edgesOf(tone32([[0.5, -120], [0.2, -64], [0.6, -20], [0.3, -120]]), 32_000, 10).lead - 0.5) < 0.021 && edgesOf(tone32([[1, -120]]), 32_000).lead === 1,
      [soft, hard])
    // a single word from ElevenLabs with a breath 0.4 s after it (Nina's "kosilo" in October 2026), and one with a stop's closure
    const breath: [number, number][] = [[0.05, -120], [0.6, -20], [0.06, -50], [0.4, -75], [0.15, -30], [0.3, -120]]
    const asIs = edgesOf(tone32(breath), 32_000)
    const asWord = edgesOf(tone32(breath), 32_000, 0, EDGES, true)
    const closure = edgesOf(tone32([[0.05, -120], [0.3, -20], [0.12, -75], [0.3, -20], [0.3, -120]]), 32_000, 0, EDGES, true)
    check('…a single word ends at its first pause of 0.25 s or more: the breath after it is cut off (its fading vowel kept); a stop\'s closure inside it (0.12 s) is no such pause; anything else keeps what follows',
      Math.abs(asIs.tail - 0.3) < 0.021 && Math.abs(asWord.tail - (0.4 + 0.15 + 0.3)) < 0.021 && Math.abs(asWord.lead - 0.05) < 0.021 && Math.abs(closure.tail - 0.3) < 0.021 && EDGES.wordGap === 0.25 &&
        isOneWord('kosilo') && isOneWord(' Kosilo ') && !isOneWord('dober dan') && !isOneWord('Kosilo.') && !isOneWord(''),
      [asIs, asWord, closure])
    check('…the trim: 0.05 s kept before the sound, 0.1 s after it, with a short fade where it cuts; a clip encoded anyway trimmed to that, else only over 0.1 s before or 0.2 s after',
      trimChain({ secs: 1.44, lead: 0.5, tail: 0.3 }) === 'atrim=start=0.450:end=1.240,asetpts=PTS-STARTPTS,afade=t=in:d=0.005,afade=t=out:st=0.780:d=0.01' &&
        trimChain({ secs: 1, lead: 0.02, tail: 0.3 }) === 'atrim=end=0.800,asetpts=PTS-STARTPTS,afade=t=out:st=0.790:d=0.01' && trimChain({ secs: 1, lead: 0.04, tail: 0.05 }) === '' && trimChain({ secs: 1, lead: 1, tail: 0 }) === '' &&
        trimmable({ secs: 1, lead: 0.08, tail: 0 }) && !trimmable({ secs: 1, lead: 0.08, tail: 0 }, true) && trimmable({ secs: 1, lead: 0.12, tail: 0 }, true) && !trimmable({ secs: 1, lead: 0.02, tail: 0.18 }, true) && trimmable({ secs: 1, lead: 0.02, tail: 0.25 }, true) && !trimmable({ secs: 1, lead: 1, tail: 0 }) && !ffmpegEdges('off'),
      [trimChain({ secs: 1.44, lead: 0.5, tail: 0.3 }), trimChain({ secs: 1, lead: 0.02, tail: 0.3 })])
    check('the day\'s re-voice cap as the node set it (LANI_VOICE_REVOICE_DAILY; the bridge and voice-build read it alike): a whole number, 0 for none; nothing or a wrong value: the default',
      revoiceDaily({ LANI_VOICE_REVOICE_DAILY: '100' }) === 100 && revoiceDaily({ LANI_VOICE_REVOICE_DAILY: ' 0 ' }) === 0 && revoiceDaily({}) === undefined &&
        revoiceDaily({ LANI_VOICE_REVOICE_DAILY: '' }) === undefined && revoiceDaily({ LANI_VOICE_REVOICE_DAILY: 'lots' }) === undefined && revoiceDaily({ LANI_VOICE_REVOICE_DAILY: '-3' }) === undefined && revoiceDaily({ LANI_VOICE_REVOICE_DAILY: '2.5' }) === undefined)
    const narratorLog: string[] = []
    check('the narrators of a Slovene village: their Slovene ElevenLabs voices first (LANI_VOICE_NARRATOR_SL=elevenlabs, the default), or the local voice first (gepard); a wrong value is logged and the default taken; another village\'s as its cast has them',
      sloveneNarrators('sl', {}) === 'elevenlabs' && sloveneNarrators('sl', { LANI_VOICE_NARRATOR_SL: 'gepard' }) === 'gepard' && sloveneNarrators('sl', { LANI_VOICE_NARRATOR_SL: 'elevenlabs' }) === 'elevenlabs' &&
        sloveneNarrators('sl', { LANI_VOICE_NARRATOR_SL: 'matilda' }, (...a) => void narratorLog.push(a.join(' '))) === 'elevenlabs' && narratorLog.some(l => l.includes('matilda')) &&
        !sloveneNarrators('en', {}) && !sloveneNarrators('it', { LANI_VOICE_NARRATOR_SL: 'gepard' }), narratorLog)
    check('the cast: the narrators\' Slovene voices (Nina, Luka: "female.sl", "male.sl"; a lone word inside its carrier sentence since 2026-10-26), their local voices (female nina, a lone word ana; male marko); the English voices kept for elsewhere',
      cast().female.gepard === 'nina' && cast().female.gepard_words === 'ana' && cast().male.gepard === 'marko' && !cast().male.gepard_words &&
        cast().female.languages?.sl?.elevenlabs === '0IwLFuWc6siAnpd77hRG' && cast().male.languages?.sl?.elevenlabs === 'SmCUS1ekZnVjC5lqnRQG' && cast().female.elevenlabs === 'XrExE9yKIg1WjnnlVkGX' &&
        languageVoice('female', 'sl') === 'female.sl' && languageVoice('male', 'sl') === 'male.sl' && !languageVoice('grandma', 'sl') && !languageVoice('female', 'it') &&
        elevenlabsOf('female.sl') === '0IwLFuWc6siAnpd77hRG' && elevenlabsOf('male.sl') === 'SmCUS1ekZnVjC5lqnRQG' && carrierVoice('female.sl') && carrierVoice('male.sl') && carrierVoice('female') && !carrierVoice('grandma.sl') && baseVoice('female.sl') === 'female' && baseVoice('grandma') === 'grandma' &&
        genderOf('male.sl') === 'male' && !isVoice('female.sl') &&
        new Gepard().speakerOf('female', 'kosilo') === 'ana' && new Gepard().speakerOf('female', 'dober dan') === 'ana' && new Gepard().speakerOf('female', 'Kosilo je na mizi.') === 'nina' && new Gepard().speakerOf('male', 'kosilo') === 'marko' &&
        new Gepard().speakerOf('grandma', 'kosilo') === 'nina' && new Gepard({ speakers: { female: 'ana' } }).speakerOf('female', 'Kosilo je na mizi.') === 'ana')
  }
  if (ffmpeg) {
    // speech-like test clips: a vowel-like tone (180 Hz, its level moving 4 times a second) after [lead] s of silence
    const gen = (expr: string, secs: number, o: { rate?: number; kbps?: number } = {}) =>
      new Uint8Array(Bun.spawnSync(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i', `aevalsrc='${expr}':s=${o.rate ?? 44100}:d=${secs}`, '-ac', '1', '-c:a', 'libmp3lame', '-b:a', `${o.kbps ?? 128}k`, '-f', 'mp3', 'pipe:1']).stdout)
    const vowel = (a: number, lead = 0.4) => `between(t,${lead},9)*${a}*sin(2*PI*180*t)*(0.55+0.45*sin(2*PI*4*t))`
    const loud = gen(vowel(0.9), 1.6)
    const quiet = gen(vowel(0.1), 1.6)
    const peaky = gen(`${vowel(0.15)}+0.9*between(t,0.8,0.8003)`, 1.6) // quiet, with a click near full scale
    const short = gen(vowel(0.9, 0.02), 0.3) // a short word: no 400 ms block
    const local = gen(vowel(0.12), 1.6, { rate: 22050, kbps: 64 }) // as the local Gepard worker's: 22.05 kHz, 64 kbit/s
    const measure = ffmpegLoudness()!
    const lv = ffmpegLeveller()!
    const samples = (b: Uint8Array) => new Float32Array(Bun.spawnSync(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'mp3', '-i', 'pipe:0', '-ac', '1', '-f', 'f32le', 'pipe:1'], { stdin: b }).stdout.buffer)
    const onset = (x: Float32Array) => {
      const top = x.reduce((m, v) => Math.max(m, Math.abs(v)), 0)
      return x.findIndex(v => Math.abs(v) > top * 0.25)
    }
    const rateOf = (b: Uint8Array) => Number(Bun.spawnSync(['ffprobe', '-v', 'error', '-show_entries', 'stream=sample_rate', '-of', 'csv=p=0', '-f', 'mp3', '-i', 'pipe:0'], { stdin: b }).stdout.toString().trim())
    const near = (m: { lufs: number } | null, target = LEVEL.target, by = 0.5) => !!m && Math.abs(m.lufs - target) <= by
    const [l, q, p, s, g] = [await lv.level(loud), await lv.level(quiet), await lv.level(peaky), await lv.level(short), await lv.level(local)]
    const [ml, mq, mp, ms, mg] = await Promise.all([l, q, p, s, g].map(x => (x ? measure(x.bytes) : null)))
    check('the level: a loud and a quiet clip both come out at −18 LUFS (±0.5), their true peak at most −1.5 dBTP, by a plain gain',
      !!l && !!q && l.gain < -5 && q.gain > 5 && !l.limited && !q.limited && near(ml) && near(mq) && ml!.peak <= -1.5 && mq!.peak <= -1.5 && isMp3(l.bytes),
      [l && { ...l, bytes: l.bytes.length }, ml, q && { ...q, bytes: q.bytes.length }, mq])
    check('…a clip whose click would pass the ceiling: the limiter holds them (true peak at most −1.5 dBTP), its loudness within 1 dB',
      !!p && p.limited && near(mp, LEVEL.target, 1) && mp!.peak <= -1.5, [p && { ...p, bytes: p.bytes.length }, mp])
    check('…a word under 0.4 s (no 400 ms block for ebur128): measured looped, levelled too',
      !!s && s.before.lufs > -70 && near(ms), [s && { ...s, bytes: s.bytes.length }, ms])
    check('…a local clip keeps its encoding (22.05 kHz, 64 kbit/s); ElevenLabs\' stays 44.1 kHz, 128 kbit/s',
      !!g && near(mg) && rateOf(g.bytes) === 22050 && mp3Kbps(g.bytes) === 64 && rateOf(l!.bytes) === 44100 && mp3Kbps(l!.bytes) === 128, [rateOf(g?.bytes ?? new Uint8Array()), mp3Kbps(g?.bytes ?? new Uint8Array()), mg])
    const [a, b] = [samples(loud), samples(l!.bytes)]
    check('…its length and timing stay (within 2 ms)', Math.abs(a.length - b.length) <= 0.002 * 44100 && Math.abs(onset(a) - onset(b)) <= 0.002 * 44100, [a.length, b.length, onset(a), onset(b)])
    const again = await lv.level(l!.bytes)
    check('…and a clip at the level already is kept as it is (the same bytes, no encode)', !!again && again.bytes === l!.bytes && again.gain === 0, again && { ...again, bytes: again.bytes.length })

    // the store: every new clip levelled as it is stored (ElevenLabs and the local worker), recorded, never twice
    const stub = (name: 'elevenlabs' | 'gepard', bytes: Uint8Array): Engine => ({ name, ready: async () => true, synth: async () => ({ bytes }), status: async () => ({}) })
    const dir = tempDir('lani-level-')
    const updated: number[] = []
    const vs = new VoiceStore({ appDir: dir, engines: [stub('elevenlabs', loud)], queueDelayMs: 0, onUpdated: n => void updated.push(n) })
    const zala = await vs.voice('Živjo, Jan!', 'girl', 'test')
    const zm = zala ? await measure(new Uint8Array(readFileSync(join(vs.filesDir, zala.file)))) : null
    const ze = zala ? await ffmpegEdges()!(new Uint8Array(readFileSync(join(vs.filesDir, zala.file)))) : null
    check('a new clip is levelled before it is stored: its row records the level and the gain', zala?.level === LEVEL.target && (zala.gain ?? 0) < -5 && vs.get(normalizeText('Živjo, Jan!'), 'girl')?.level === LEVEL.target && near(zm), [zala, zm])
    // (at the girl's own pace: the cast's tempo for her ElevenLabs voice, ×1.1)
    const zt = tempoOf('girl', 'elevenlabs')
    check('…and its silence at the edges trimmed in the same encode (0.4 s before the sound down to 0.05 s), recorded (edges); at her voice\'s tempo', zala?.edges === 1 && !!ze && Math.abs(ze.lead - EDGES.lead) <= 0.03 && Math.abs(ze.secs - (EDGES.lead + 1.2 / zt)) < 0.05 && zala.tempo === (zt === 1 ? null : zt), [zala, ze, zt])
    check('…so it is not levelled or trimmed twice: nothing waits, the batch does nothing', vs.levelQueue().length === 0 && vs.edgesQueue().length === 0 && (await vs.levelClips()).done === 0 && (await vs.edgesClips()).done === 0 && vs.levelStats().levelled === 1 && vs.levelStats().waiting === 0 && vs.edgesStats().done === 1, vs.levelStats())
    const lvs = new VoiceStore({ appDir: tempDir('lani-level-gepard-'), engines: [stub('gepard', local)], queueDelayMs: 0 })
    const kruh = await lvs.voice('Kruh je še topel.', 'male', 'test')
    const kb = kruh ? new Uint8Array(readFileSync(join(lvs.filesDir, kruh.file))) : new Uint8Array()
    check('…a local worker\'s clip too, in its own encoding, at its own pace (its edges trimmed)', kruh?.engine === 'gepard' && kruh.level === LEVEL.target && kruh.tempo === null && kruh.edges === 1 && lvs.pace === null && near(await measure(kb)) && rateOf(kb) === 22050 && mp3Kbps(kb) === 64, kruh)
    lvs.close()

    // a voice that hisses: filtered and levelled in one encode (the level runs its filter first)
    const castDir = tempDir('lani-level-cast-')
    const castJson = JSON.parse(readFileSync(CAST_FILE, 'utf8'))
    castJson.denoise = { grandma: 'hiss' }
    writeFileSync(join(castDir, 'voice-cast.json'), JSON.stringify(castJson))
    process.env.LANI_VOICE_CAST = join(castDir, 'voice-cast.json')
    const hissy = gen("0.5*sin(2*PI*440*t)*(between(t,0,0.5)+between(t,1.3,1.8))+0.005*(2*random(0)-1)", 1.8) // no silence at its edges
    const calls: (string | undefined)[] = []
    const spy: Leveller = { target: lv.target, level: (b, o) => (calls.push(o?.denoise), lv.level(b, o)) }
    const hs = new VoiceStore({ appDir: tempDir('lani-level-hiss-'), engines: [stub('elevenlabs', hissy)], queueDelayMs: 0, level: spy })
    const gm = await hs.voice('Dober večer.', 'grandma', 'test')
    const [h0, h1] = [samples(hissy), gm ? samples(new Uint8Array(readFileSync(join(hs.filesDir, gm.file)))) : new Float32Array()]
    const rms = (x: Float32Array, from: number, to: number) => {
      let e = 0
      for (let k = Math.floor(from * 44100); k < Math.floor(to * 44100); k++) e += x[k] ** 2
      return 10 * Math.log10(e / ((to - from) * 44100) + 1e-12)
    }
    // the tone over the hiss, before and after: the hiss went down (the filter) more than the tone (the gain)
    const snr = (x: Float32Array) => rms(x, 1.4, 1.7) - rms(x, 0.8, 1.2)
    check('a flagged voice\'s new clip: its filter and the level in one encode (the level called once, with the preset), both recorded',
      gm?.denoise === 'hiss' && gm.level === LEVEL.target && JSON.stringify(calls) === '["hiss"]' && snr(h1) - snr(h0) > 15 && near(await measure(new Uint8Array(readFileSync(join(hs.filesDir, gm.file))))), [gm, calls, snr(h0), snr(h1)])
    hs.close()
    delete process.env.LANI_VOICE_CAST

    // a new target: the clips at the old one are levelled again, from their files as they are
    const to20 = new VoiceStore({ appDir: dir, engines: [], level: ffmpegLeveller(undefined, { ...LEVEL, target: -20 }) })
    const waiting = to20.levelQueue().map(c => c.key)
    const re = await to20.levelClips()
    const z2 = to20.get(normalizeText('Živjo, Jan!'), 'girl')
    check('a new target (−20): the clip levelled to −18 waits, and is levelled again from its file, under a new name, the gain added up',
      waiting.join() === `girl:${normalizeText('Živjo, Jan!')}` && re.done === 1 && z2?.level === -20 && z2.file === processedFile(zala!.file, { level: -20 }) && !existsSync(join(vs.filesDir, zala!.file)) &&
        Math.abs((z2.gain ?? 0) - ((zala!.gain ?? 0) - 2)) < 0.6 && near(await measure(new Uint8Array(readFileSync(join(vs.filesDir, z2.file)))), -20), [waiting, re, z2])
    to20.close()
    vs.close()

    // voice-build --level on a copy of the data: a store from before the level (no level column), real MP3s
    const vbData = tempDir('lani-level-data-')
    mkdirSync(join(vbData, 'app/voice/files'), { recursive: true })
    const old = new Database(join(vbData, 'app/voice/voice.db'))
    old.exec('CREATE TABLE clips (key TEXT PRIMARY KEY, norm TEXT NOT NULL, text TEXT NOT NULL, voice TEXT NOT NULL, engine TEXT NOT NULL, file TEXT NOT NULL, chars INTEGER NOT NULL, created_at TEXT NOT NULL, source TEXT NOT NULL, method TEXT, hits INTEGER NOT NULL DEFAULT 0, denoise TEXT)')
    const oldFile: Record<string, string> = {}
    const clips: [string, string, string, Uint8Array][] = [
      ['Živjo! Zakaj si tukaj?', 'boy', 'elevenlabs', loud], // loud, and no hiss (the girl's voice is filtered too),
      ['Tiho. Poslušaj.', 'grandpa', 'elevenlabs', quiet],
      ['Adijo! Grem nazaj v Brda.', 'male', 'gepard', local],
      ['okno', 'female', 'elevenlabs', s!.bytes], // at the level already, no silence at its edges
      ['Bravo, Jan.', '@janez', 'elevenlabs', hissy], // a voice that hisses: filtered in the same encode
    ]
    clips.forEach(([text, voice, engine, bytes], i) => {
      const file = `${createHash('sha1').update(`level|${voice}|${text}`).digest('hex')}.mp3`
      oldFile[voice] = file
      writeFileSync(join(vbData, 'app/voice/files', file), bytes)
      old.query('INSERT INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, hits) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)').run(`${voice}:${normalizeText(text)}`, normalizeText(text), text, voice, engine, file, text.length, `2026-09-2${i}T10:00:00.000Z`, 'test', 2)
    })
    old.close()
    const env = { ...process.env, LANI_KEYS_FILE: '/nonexistent', ELEVENLABS_API_KEY: '', LANI_CULTURES_DIR: culturesDir } as Record<string, string>
    delete env.LANI_VOICE_CAST
    delete env.LANI_DATA_DIR
    delete env.LANI_VOICE_GEPARD_TEMPO
    const vb = async (...args: string[]) => {
      const p = Bun.spawn(['bun', resolve(import.meta.dir, '../../../bin/voice-build'), ...args], { env, stdout: 'pipe', stderr: 'pipe' })
      const [stdout, stderr, exitCode] = await Promise.all([new Response(p.stdout).text(), new Response(p.stderr).text(), p.exited])
      return { stdout, stderr, exitCode }
    }
    const cols = () => {
      const d = new Database(join(vbData, 'app/voice/voice.db'), { readonly: true })
      const c = (d.query('PRAGMA table_info(clips)').all() as { name: string }[]).map(c => c.name)
      d.close()
      return c
    }
    const dry = await vb('--level', '--data', vbData)
    const dj = JSON.parse((await vb('--level', '--json', '--data', vbData)).stdout || '{}')
    const row = (voice: string) => dj.clips?.find((c: any) => c.voice === voice)
    check('voice-build --level (dry run): every clip measured, each voice\'s loudness now and the gain (the loud one down, the quiet one up, the one at the level kept, the one that hisses filtered too); nothing changed',
      dry.exitCode === 0 && dj.target === LEVEL.target && dj.waiting === 5 && dj.measured === 5 && row('boy')?.gain < -5 && row('grandpa')?.gain > 5 && row('female')?.keep === true && row('@janez')?.denoise === 'hiss' && row('male')?.tempo === undefined && dj.voices?.['boy elevenlabs']?.lufs.median > -12 &&
        dry.stdout.includes('boy elevenlabs') && dry.stdout.includes('Dry run') && dry.stdout.includes('--level --run') && !cols().includes('level') && Object.values(oldFile).every(f => existsSync(join(vbData, 'app/voice/files', f))),
      [dry.stdout, dry.stderr, dj])
    check('voice-build --level: no --max-chars (no characters are spent), not with another mode', (await vb('--level', '--max-chars', '10', '--data', vbData)).exitCode === 2 && (await vb('--level', '--denoise', '--data', vbData)).exitCode === 2)
    // the bridge, running on the same data: it tells the app when another process changed the clips
    const told: number[] = []
    const bridge = new VoiceStore({ appDir: join(vbData, 'app'), engines: [], queueDelayMs: 0, onUpdated: n => void told.push(n) })
    bridge.checkExternal()
    const run1 = await vb('--level', '--run', '--max', '2', '--data', vbData)
    const run2 = await vb('--level', '--run', '--data', vbData)
    const run3 = await vb('--level', '--run', '--json', '--data', vbData)
    const d = new Database(join(vbData, 'app/voice/voice.db'), { readonly: true })
    const rows = Object.fromEntries((d.query('SELECT * FROM clips').all() as Clip[]).map(c => [c.voice, c]))
    d.close()
    const at = (voice: string) => new Uint8Array(readFileSync(join(vbData, 'app/voice/files', rows[voice].file)))
    const levels = await Promise.all(Object.keys(rows).map(v => measure(at(v))))
    check('voice-build --level --run (--max 2, then the rest, then nothing): each clip levelled once, its edges trimmed in the same encode, under a new name, the old file gone; the one at the level kept (its file, gain 0); Janez\'s filtered in the same encode, the local one at its own pace',
      run1.exitCode === 0 && run1.stdout.includes('Levelled 2 clip(s)') && run2.exitCode === 0 && run2.stdout.includes('Levelled 3 clip(s)') && run3.stdout.includes('Levelled 0 clip(s)') &&
        Object.values(rows).every(c => c.level === LEVEL.target && c.hits === 2 && c.edges === 1) &&
        ['boy', 'grandpa', 'male'].every(v => rows[v].file === processedFile(oldFile[v], { level: LEVEL.target, edges: true }) && !existsSync(join(vbData, 'app/voice/files', oldFile[v]))) &&
        rows.male.tempo == null && rows.boy.tempo == null &&
        rows.female.file === oldFile.female && rows.female.gain === 0 && rows['@janez'].denoise === 'hiss' && rows['@janez'].file === processedFile(oldFile['@janez'], { denoise: 'hiss', level: LEVEL.target }) &&
        levels.every(m => near(m)) && rateOf(at('male')) === 22050 && mp3Kbps(at('male')) === 64,
      [run1.stdout + run1.stderr, run2.stdout, run3.stdout, rows, levels])
    const n = bridge.checkExternal()
    check('the bridge sees the clips voice-build levelled (the four with new files) and tells the app once (voice_updated)', n === 4 && told.join() === '4' && bridge.checkExternal() === 0, [n, told])
    bridge.close()

    // family recordings: levelled as they are uploaded, in their own container
    const rec = (ext: 'm4a' | 'ogg' | 'webm') => {
      const args = { m4a: ['-c:a', 'aac', '-b:a', '96k', '-f', 'mp4'], ogg: ['-c:a', 'libopus', '-b:a', '64k', '-f', 'ogg'], webm: ['-c:a', 'libopus', '-b:a', '64k', '-f', 'webm'] }[ext]
      const out = join(tempDir('lani-level-rec-'), `take.${ext}`)
      Bun.spawnSync(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i', `aevalsrc='${vowel(0.9)}':s=44100:d=1.6`, '-ac', '1', ...args, out])
      return new Uint8Array(readFileSync(out))
    }
    const loudnessOf = (b: Uint8Array, ext: string) => {
      const f = join(tempDir('lani-level-rec-'), `take.${ext}`)
      writeFileSync(f, b)
      const s = Bun.spawnSync(['ffmpeg', '-hide_banner', '-nostats', '-i', f, '-af', 'ebur128=peak=true:framelog=quiet', '-f', 'null', '-']).stderr.toString()
      return Number(/I:\s*(-?[\d.]+) LUFS/.exec(s.slice(s.lastIndexOf('Summary:')))?.[1])
    }
    const rl = ffmpegRecordingLeveller()!
    const recs = await Promise.all((['m4a', 'ogg', 'webm'] as const).map(async ext => [ext, await rl(rec(ext), ext)] as const))
    check('a family recording (m4a, ogg, webm) is levelled in its own container', recs.every(([ext, r]) => !!r && r.gain < -5 && Math.abs(loudnessOf(r.bytes, ext) - LEVEL.target) <= 0.5), recs.map(([ext, r]) => [ext, r?.gain, r && loudnessOf(r.bytes, ext)]))
    const up = await fetch(`${base}/audio?${new URLSearchParams({ text: 'Dober večer!', speaker: '👵 Micka' })}`, { method: 'POST', headers: { authorization: auth.authorization }, body: rec('m4a') })
    const upBody = await up.json()
    const stored = await fetch(`${base}/audio/file/${upBody.file}`, { headers: auth })
    const sb = new Uint8Array(await stored.arrayBuffer())
    check('POST /audio: a loud recording is stored at the level, as an m4a still', up.ok && stored.ok && stored.headers.get('content-type') === 'audio/mp4' && Math.abs(loudnessOf(sb, 'm4a') - LEVEL.target) <= 0.5, [upBody, loudnessOf(sb, 'm4a')])

    // --- the local voice: its own pace, its edges trimmed; a pace only when one is set ------------------------------
    const worker = workerClip()
    const pace = ffmpegPace()!
    const p0 = await pace(worker)
    // (the clip, written to a pipe, has no gapless header: it starts about 0.05 s late)
    check('the pace measure: the silence before and after the sound, the pauses, the time with sound, the length',
      !!p0 && Math.abs(p0.lead - 0.6) < 0.08 && Math.abs(p0.tail - 0.7) < 0.08 && p0.pauses === 1 && Math.abs(p0.longest - 1) < 0.06 && Math.abs(p0.paused - 1) < 0.06 && Math.abs(p0.sound - 1.6) < 0.08 && Math.abs(p0.secs - 3.9) < 0.1, p0)
    const fg = fakeGepard()
    fg.st.audio = worker
    const pdir = tempDir('lani-pace-')
    const ps = new VoiceStore({ appDir: pdir, engines: [new Gepard({ url: fg.url })], queueDelayMs: 0 })
    const said = 'Dober dan. Kako ste?'
    const pc = await ps.voice(said, 'male', 'test')
    const pb = pc ? new Uint8Array(readFileSync(join(ps.filesDir, pc.file))) : new Uint8Array()
    const p1 = await pace(pb)
    check('a clip from the local worker keeps its own pace: only its edges trimmed (0.05 s before its sound, 0.1 s after it), its pause and its speed as made; recorded (edges, no tempo), levelled in the same encode, in its own encoding',
      pc?.engine === 'gepard' && ps.pace === null && pc.tempo === null && pc.edges === 1 && pc.level === LEVEL.target && !!p1 && p1.lead <= 0.08 && p1.tail <= 0.16 && Math.abs(p1.longest - 1) < 0.06 && Math.abs(p1.sound - 1.6) < 0.1 &&
        Math.abs(p1.secs - (EDGES.lead + 1.6 + 1 + EDGES.tail)) < 0.12 && near(await measure(pb)) && rateOf(pb) === 22050 && mp3Kbps(pb) === 64,
      [pc, p0, p1])
    check('…its name carries the worker\'s voice (marko), recorded too; nothing waits for a pace; it is not trimmed twice',
      pc?.engine_voice === 'marko' && fg.st.calls.at(-1)?.speaker === 'marko' && ps.tempoQueue().length === 0 && (await ps.tempoClips()).done === 0 && ps.edgesQueue().length === 0 &&
        JSON.stringify(ps.tempoStats()) === JSON.stringify({ tempo: null, pause: null, paced: 0, voices: tempoVoices(), voiced: 0, waiting: 0, ffmpeg: true }), [pc, ps.tempoStats()])
    // a pace set (LANI_VOICE_GEPARD_TEMPO=1.05, LANI_VOICE_GEPARD_PAUSES=0.4): the pause shortened, sped up, then the edges
    const set = new VoiceStore({ appDir: tempDir('lani-pace-set-'), engines: [new Gepard({ url: fg.url })], queueDelayMs: 0, pace: { tempo: 1.05, pause: 0.4 } })
    const sc = await set.voice(said, 'male', 'test')
    const s1 = sc ? await pace(new Uint8Array(readFileSync(join(set.filesDir, sc.file)))) : null
    check('…with a pace set: the pause down to 0.4 s and sped up ×1.05 in the same encode, the edges trimmed after; recorded (tempo)',
      sc?.tempo === 1.05 && sc.edges === 1 && !!s1 && s1.lead <= 0.08 && s1.tail <= 0.16 && Math.abs(s1.longest - 0.4 / 1.05) < 0.06 && Math.abs(s1.secs - (EDGES.lead + (1.6 + 0.4) / 1.05 + EDGES.tail)) < 0.12 && set.tempoQueue().length === 0, [sc, s1])
    const pel = await new VoiceStore({ appDir: pdir, engines: [stub('elevenlabs', worker)], queueDelayMs: 0, pace: { tempo: 1.05, pause: 0.4 } }).voice('Dober večer.', 'female', 'test')
    const pe = pel ? await pace(new Uint8Array(readFileSync(join(ps.filesDir, pel.file)))) : null
    check('…an ElevenLabs clip keeps its pace (only the local voice is paced), its edges trimmed as every clip\'s', pel?.engine === 'elevenlabs' && pel.tempo == null && pel.edges === 1 && pel.level === LEVEL.target && !!pe && Math.abs(pe.longest - 1) < 0.06 && pe.lead <= 0.08, [pel, pe])
    // a new factor: the clips paced at the old one are sped up by the difference, from their files as they are
    const faster = new VoiceStore({ appDir: set.dir.replace(/\/voice$/, ''), engines: [], pace: { tempo: 1.2, pause: 0.4 } })
    const waits = faster.tempoQueue().map(c => c.key)
    const fr = await faster.tempoClips()
    const fc = faster.get(normalizeText(said), 'male')
    const p2 = fc ? await pace(new Uint8Array(readFileSync(join(set.filesDir, fc.file)))) : null
    check('a new factor (1.2): the clip paced at 1.05 waits, and is sped up by the difference from its file, under a new name, levelled again',
      waits.join() === `male:${normalizeText(said)}` && fr.done === 1 && fc?.tempo === 1.2 && fc.file === processedFile(sc!.file, { level: LEVEL.target, tempo: 1.2 }) && !existsSync(join(set.filesDir, sc!.file)) &&
        !!s1 && !!p2 && Math.abs(p2.secs - (s1.secs * 1.05) / 1.2) < 0.1 && near(await measure(new Uint8Array(readFileSync(join(set.filesDir, fc.file))))), [waits, fr, fc, s1, p2])
    faster.close()
    set.close()
    ps.close()

    // --- a voice's own pace (the cast's tempo; Jan, 2026-10-19: "Zala talks a bit slow"): sped up as its clips are stored,
    // by engine, after a carrier sentence's cut ---------------------------------------------------------------------
    {
      const tc = tempDir('lani-tempo-cast-')
      const baseCast = JSON.parse(readFileSync(CAST_FILE, 'utf8'))
      const writeCast = (name: string, edit: (c: any) => void) => {
        const c = structuredClone(baseCast)
        edit(c)
        const f = join(tc, `${name}.json`)
        writeFileSync(f, JSON.stringify(c))
        return f
      }
      const castBefore = process.env.LANI_VOICE_CAST
      const castT = writeCast('tempo', c => {
        c.speakers.girl.tempo = { elevenlabs: 1.1 }
        c.speakers.boy.tempo = 1.2
        c.speakers.female.tempo = { elevenlabs: 1.25 }
        c.speakers.female.languages.sl.tempo = 0.9
      })
      process.env.LANI_VOICE_CAST = castT
      const sorted = (o: object) => JSON.stringify(Object.fromEntries(Object.entries(o).sort()))
      check('the cast\'s tempo: a number for every engine, or by engine (an engine left out keeps its pace), a voice in a language its own; none for someone\'s own voice or a speaker without it',
        tempoOf('girl', 'elevenlabs') === 1.1 && tempoOf('girl', 'gepard') === 1 && tempoOf('boy', 'elevenlabs') === 1.2 && tempoOf('boy', 'gepard') === 1.2 && tempoOf('female', 'elevenlabs') === 1.25 &&
          tempoOf('female', 'gepard') === 1 && tempoOf('female.sl', 'elevenlabs') === 0.9 && tempoOf('@micka', 'elevenlabs') === 1 && tempoOf('grandma', 'elevenlabs') === 1 &&
          sorted(tempoVoices()) === sorted({ girl: { elevenlabs: 1.1 }, boy: 1.2, female: { elevenlabs: 1.25 }, 'female.sl': 0.9 }), tempoVoices())
      check('…the pace a clip is stored at (clipPace): its voice\'s tempo by its engine, a local clip\'s times the local voice\'s pace (with its pauses); none at 1',
        JSON.stringify(clipPace('girl', 'elevenlabs', null)) === '{"tempo":1.1,"pause":null}' && clipPace('girl', 'gepard', null) === null && JSON.stringify(clipPace('girl', 'elevenlabs', { tempo: 1.05, pause: 0.4 })) === '{"tempo":1.1,"pause":null}' &&
          JSON.stringify(clipPace('boy', 'gepard', { tempo: 1.05, pause: 0.4 })) === '{"tempo":1.26,"pause":0.4}' && JSON.stringify(clipPace('grandma', 'gepard', { tempo: 1, pause: 0.4 })) === '{"tempo":1,"pause":0.4}' && clipPace('grandma', 'elevenlabs', null) === null)
      // a tempo out of range: the cast is refused as for any wrong field (the narrators alone), and logged
      process.env.LANI_VOICE_CAST = writeCast('bad', c => void (c.speakers.girl.tempo = 3))
      const errorBefore = console.error
      const castErrors: string[] = []
      console.error = (...a: unknown[]) => void castErrors.push(a.join(' '))
      const badCast = Object.keys(cast())
      console.error = errorBefore
      check('…a tempo out of 0.5 to 2: the cast is refused (the narrators alone), logged', badCast.join() === 'female,male' && castErrors.some(l => l.includes('tempo')), [badCast, castErrors])
      process.env.LANI_VOICE_CAST = castT

      // the girl's new line from ElevenLabs: ×1.1; the grandmother's, without a tempo, as made
      const ts = new VoiceStore({ appDir: tempDir('lani-tempo-'), engines: [stub('elevenlabs', worker)], queueDelayMs: 0 })
      const line = 'Vidim, vidim nekaj, česar ti ne vidiš …'
      const zg = await ts.voice(line, 'girl', 'test')
      const gm = await ts.voice(line, 'grandma', 'test')
      const [zp, gp] = await Promise.all([zg, gm].map(c => (c ? pace(new Uint8Array(readFileSync(join(ts.filesDir, c.file)))) : null)))
      check('a new clip of a voice with a tempo (the girl, ElevenLabs ×1.1): sped up in the level\'s encode (its sound and its pause ÷1.1), the edges trimmed after, levelled; recorded (tempo); a voice without one as made',
        zg?.tempo === 1.1 && zg.engine === 'elevenlabs' && zg.edges === 1 && zg.level === LEVEL.target && gm?.tempo == null && gm.edges === 1 && !!zp && !!gp &&
          Math.abs(zp.longest - 1 / 1.1) < 0.05 && Math.abs(zp.sound - 1.6 / 1.1) < 0.08 && zp.lead <= 0.08 && zp.tail <= 0.16 && Math.abs(gp.longest - 1) < 0.06 && Math.abs(zp.span * 1.1 - gp.span) < 0.12 &&
          near(await measure(new Uint8Array(readFileSync(join(ts.filesDir, zg.file))))), [zg, zp, gp])
      check('…nothing waits for its pace, and a pass does nothing (each clip records its factor); the status lists the voices and their clips at it',
        ts.tempoQueue().length === 0 && (await ts.tempoClips()).done === 0 && ts.tempoStats().voiced === 1 && sorted(ts.tempoStats().voices) === sorted(tempoVoices()), ts.tempoStats())
      ts.close()
      // the local worker: the girl's tempo is ElevenLabs' only (her local voice is fast already); the boy's every engine's,
      // times the local voice's pace when one is set
      const tg = new VoiceStore({ appDir: tempDir('lani-tempo-gepard-'), engines: [stub('gepard', worker)], queueDelayMs: 0 })
      const tgp = new VoiceStore({ appDir: tempDir('lani-tempo-gepard-pace-'), engines: [stub('gepard', worker)], queueDelayMs: 0, pace: { tempo: 1.05, pause: 0.4 } })
      const [zl, bl, bp] = [await tg.voice('Toplo, toplo!', 'girl', 'test'), await tg.voice('Toplo, toplo!', 'boy', 'test'), await tgp.voice('Toplo, toplo!', 'boy', 'test')]
      const [zlp, blp] = await Promise.all([zl, bl].map(c => (c ? pace(new Uint8Array(readFileSync(join(tg.filesDir, c.file)))) : null)))
      const bpp = bp ? await pace(new Uint8Array(readFileSync(join(tgp.filesDir, bp.file)))) : null
      check('…a local clip: the girl\'s at its own pace (her tempo is ElevenLabs\'), the boy\'s ×1.2 (every engine), ×1.26 with the local voice\'s pace (its pause down to 0.4 s, then sped up)',
        zl?.engine === 'gepard' && zl.tempo == null && bl?.tempo === 1.2 && bp?.tempo === 1.26 && !!zlp && !!blp && !!bpp && Math.abs(zlp.longest - 1) < 0.06 && Math.abs(blp.longest - 1 / 1.2) < 0.05 &&
          Math.abs(blp.sound - 1.6 / 1.2) < 0.08 && Math.abs(bpp.longest - 0.4 / 1.26) < 0.05 && Math.abs(bpp.sound - 1.6 / 1.26) < 0.08, [zl, bl, bp, zlp, blp, bpp])
      tg.close()
      tgp.close()
      // a lone word of a carrier voice (the female narrator, Matilda: ×1.25 in this cast): cut out of its carrier sentence
      // by the take's own timestamps, as without a tempo, and then paced
      const fe = fakeEleven({ tones: true })
      const cutter = ffmpegCutter()!
      const cuts: [number, number][] = []
      const el = new ElevenLabs({ key: 'test-key', base: fe.url, cut: async (take, from, to) => (cuts.push([from, to]), cutter(take, from, to)) })
      const cs = new VoiceStore({ appDir: tempDir('lani-tempo-carrier-'), engines: [el], queueDelayMs: 0 })
      const word = await cs.voice('kosilo', 'female', 'test')
      const wb = word ? new Uint8Array(readFileSync(join(cs.filesDir, word.file))) : new Uint8Array()
      process.env.LANI_VOICE_CAST = writeCast('plain', () => {})
      const plainStore = new VoiceStore({ appDir: tempDir('lani-tempo-carrier-plain-'), engines: [el], queueDelayMs: 0 })
      const plain = await plainStore.voice('kosilo', 'female', 'test')
      const pb = plain ? new Uint8Array(readFileSync(join(plainStore.filesDir, plain.file))) : new Uint8Array()
      check('…a carrier cut: the word cut at the take\'s timestamps (0.91 to 1.51 s, as without a tempo), then paced (0.6 s ÷1.25), edges and level after; recorded (carrier, tempo)',
        word?.method === 'carrier' && word.tempo === 1.25 && word.level === LEVEL.target && plain?.method === 'carrier' && plain.tempo == null && cuts.length === 2 &&
          cuts.every(c => Math.abs(c[0] - 0.91) < 1e-6 && Math.abs(c[1] - 1.51) < 1e-6) && Math.abs(mp3Duration(wb) * 1.25 - mp3Duration(pb)) < 0.08 && mp3Duration(wb) < mp3Duration(pb) - 0.06,
        [word, plain, cuts, mp3Duration(wb), mp3Duration(pb)])
      cs.close()
      plainStore.close()
      fe.stop()
      if (castBefore === undefined) delete process.env.LANI_VOICE_CAST
      else process.env.LANI_VOICE_CAST = castBefore
    }

    // voice-build --tempo, --edges and --restore-gepard on a copy of the data: a store from before the pace and the edges
    // (no tempo, no edges column), levelled, real MP3s: two local clips as the worker made them, an ElevenLabs one
    const tData = tempDir('lani-pace-data-')
    mkdirSync(join(tData, 'app/voice/files'), { recursive: true })
    const tdb = new Database(join(tData, 'app/voice/voice.db'))
    tdb.exec('CREATE TABLE clips (key TEXT PRIMARY KEY, norm TEXT NOT NULL, text TEXT NOT NULL, voice TEXT NOT NULL, engine TEXT NOT NULL, file TEXT NOT NULL, chars INTEGER NOT NULL, created_at TEXT NOT NULL, source TEXT NOT NULL, method TEXT, hits INTEGER NOT NULL DEFAULT 0, denoise TEXT, level REAL, gain REAL)')
    const tFile: Record<string, string> = {}
    const levelled = (await lv.level(worker))!.bytes // as the store has them: at the level already
    const tight = (await lv.level(gen(vowel(0.12, 0.03), 1.5)))!.bytes // at the level, its sound from 0.03 s to the end: nothing to trim
    const tclips: [string, string, string, Uint8Array][] = [[said, 'male', 'gepard', levelled], ['Hvala, dobro. In vi?', 'female', 'gepard', levelled], ['Lepo vreme imamo danes.', 'female', 'elevenlabs', levelled], ['Kje je pošta?', 'grandma', 'elevenlabs', tight]]
    tclips.forEach(([text, voice, engine, bytes], i) => {
      const file = `${createHash('sha1').update(`pace|${voice}|${text}`).digest('hex')}.mp3`
      tFile[`${voice}:${text}`] = file
      writeFileSync(join(tData, 'app/voice/files', file), bytes)
      tdb.query('INSERT INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, hits, level, gain) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)').run(`${voice}:${normalizeText(text)}`, normalizeText(text), text, voice, engine, file, text.length, `2026-10-0${i + 1}T10:00:00.000Z`, 'test', 3, LEVEL.target, 0)
    })
    tdb.close()
    // the store as it was before (a lani-backup snapshot's voice/: the clips as the worker made them)
    const snap = tempDir('lani-pace-snapshot-')
    cpSync(join(tData, 'app/voice'), join(snap, 'voice'), { recursive: true })
    const vbt = async (extra: Record<string, string>, ...args: string[]) => {
      const p = Bun.spawn(['bun', resolve(import.meta.dir, '../../../bin/voice-build'), ...args], { env: { ...env, ...extra }, stdout: 'pipe', stderr: 'pipe' })
      const [stdout, stderr, exitCode] = await Promise.all([new Response(p.stdout).text(), new Response(p.stderr).text(), p.exited])
      return { stdout, stderr, exitCode }
    }
    const tcols = () => {
      const d = new Database(join(tData, 'app/voice/voice.db'), { readonly: true })
      const c = (d.query('PRAGMA table_info(clips)').all() as { name: string }[]).map(c => c.name)
      d.close()
      return c
    }
    const trowsNow = () => {
      const d = new Database(join(tData, 'app/voice/voice.db'), { readonly: true })
      const r = Object.fromEntries((d.query('SELECT * FROM clips').all() as Clip[]).map(c => [`${c.voice}:${c.text}`, c]))
      d.close()
      return r
    }
    const paceSet = { LANI_VOICE_GEPARD_TEMPO: '1.05', LANI_VOICE_GEPARD_PAUSES: '0.4' }
    const own = await vbt({}, '--tempo', '--data', tData)
    check('voice-build --tempo without a pace set: the local voice keeps its own pace, nothing to do (and how to trim the edges)', own.exitCode === 0 && own.stdout.includes('keeps its own pace') && own.stdout.includes('--edges') && !tcols().includes('tempo'), own.stdout)
    const tdry = await vbt(paceSet, '--tempo', '--data', tData)
    const tj = JSON.parse((await vbt(paceSet, '--tempo', '--json', '--data', tData)).stdout || '{}')
    check('voice-build --tempo (dry run) with a pace set: the two local clips, each measured now and as it would be paced (shorter, more characters a second), ElevenLabs\' clips of the same voices beside them; nothing changed',
      tdry.exitCode === 0 && tj.tempo === 1.05 && tj.pace?.pause === 0.4 && tj.waiting === 2 && tj.chars === 40 && tj.measured === 2 && tj.clips?.map((c: any) => c.text).sort().join('|') === `${said}|Hvala, dobro. In vi?` &&
        tj.clips.every((c: any) => c.after.secs < c.now.secs - 0.5) && tj.voices?.['male gepard']?.rate.after.median > tj.voices['male gepard'].rate.now.median * 1.15 && tj.elevenlabs?.n === 1 &&
        tdry.stdout.includes('male gepard') && tdry.stdout.includes('ElevenLabs, the same voices') && tdry.stdout.includes('Dry run') && tdry.stdout.includes('--tempo --run') && !tcols().includes('tempo') && Object.values(tFile).every(f => existsSync(join(tData, 'app/voice/files', f))),
      [tdry.stdout, tdry.stderr, tj])
    check('voice-build --tempo, --edges, --restore-gepard: no --max-chars (no characters are spent), not with another mode; --restore-gepard needs a snapshot',
      (await vbt({}, '--tempo', '--max-chars', '10', '--data', tData)).exitCode === 2 && (await vbt({}, '--tempo', '--level', '--data', tData)).exitCode === 2 && (await vbt({}, '--edges', '--max-chars', '10', '--data', tData)).exitCode === 2 &&
        (await vbt({}, '--edges', '--tempo', '--data', tData)).exitCode === 2 && (await vbt({}, '--restore-gepard', tempDir('lani-no-snapshot-'), '--data', tData)).exitCode === 2 && (await vbt({}, '--restore-gepard', '--data', tData)).exitCode === 2)
    // the bridge, running on the same data: it tells the app when another process changed the clips
    const ttold: number[] = []
    const tbridge = new VoiceStore({ appDir: join(tData, 'app'), engines: [], queueDelayMs: 0, onUpdated: n => void ttold.push(n) })
    tbridge.checkExternal()
    const t1 = await vbt(paceSet, '--tempo', '--run', '--max', '1', '--data', tData)
    const t2 = await vbt(paceSet, '--tempo', '--run', '--data', tData)
    const t3 = await vbt(paceSet, '--tempo', '--run', '--data', tData)
    const trows = trowsNow()
    const tlocal = [`male:${said}`, 'female:Hvala, dobro. In vi?']
    const tp = await Promise.all(tlocal.map(k => pace(new Uint8Array(readFileSync(join(tData, 'app/voice/files', trows[k].file))))))
    const elKey = 'female:Lepo vreme imamo danes.'
    check('voice-build --tempo --run (--max 1, then the rest, then nothing): each local clip paced once, its edges trimmed and levelled in the same encode, under a new name, the old file gone; the ElevenLabs clips as they were',
      t1.exitCode === 0 && t1.stdout.includes('Paced 1 clip(s)') && t2.exitCode === 0 && t2.stdout.includes('Paced 1 clip(s)') && t3.stdout.includes('Paced 0 clip(s)') && tcols().includes('tempo') && tcols().includes('edges') &&
        tlocal.every(k => trows[k].tempo === 1.05 && trows[k].edges === 1 && trows[k].level === LEVEL.target && trows[k].hits === 3 && trows[k].file === processedFile(tFile[k], { level: LEVEL.target, tempo: 1.05, edges: true }) && !existsSync(join(tData, 'app/voice/files', tFile[k]))) &&
        tp.every(p => !!p && p.secs < 2.3 && p.lead <= 0.08 && p.longest < 0.45) && trows[elKey]?.tempo == null && trows[elKey].edges == null && trows[elKey].file === tFile[elKey] && existsSync(join(tData, 'app/voice/files', tFile[elKey])),
      [t1.stdout + t1.stderr, t2.stdout, t3.stdout, trows, tp])
    const tn = tbridge.checkExternal()
    check('the bridge sees the clips voice-build paced and tells the app once (voice_updated)', tn === 2 && ttold.join() === '2' && tbridge.checkExternal() === 0, [tn, ttold])

    // --restore-gepard: the paced local clips back to their files and rows in the snapshot from before
    const snapFiles = () => JSON.stringify(Object.fromEntries(readdirSync(join(snap, 'voice/files')).sort().map(f => [f, createHash('sha1').update(readFileSync(join(snap, 'voice/files', f))).digest('hex')])))
    const snapBefore = snapFiles()
    const rdry = await vbt({}, '--restore-gepard', snap, '--data', tData)
    const rj = JSON.parse((await vbt({}, '--restore-gepard', join(snap, 'voice'), '--json', '--data', tData)).stdout || '{}')
    check('voice-build --restore-gepard (dry run; the snapshot or its voice/): the two paced local clips to restore, none missing; nothing changed, the snapshot not written to',
      rdry.exitCode === 0 && rdry.stdout.includes('To restore: 2') && rdry.stdout.includes('--restore-gepard') && rj.paced === 2 && rj.restored === 2 && rj.missing?.length === 0 && rj.left === 2 &&
        JSON.stringify(trowsNow()) === JSON.stringify(trows) && snapFiles() === snapBefore && readdirSync(join(snap, 'voice')).sort().join() === 'files,voice.db', [rdry.stdout, rdry.stderr, rj])
    const r1 = await vbt({}, '--restore-gepard', snap, '--run', '--max', '1', '--data', tData)
    const r2 = await vbt({}, '--restore-gepard', snap, '--run', '--data', tData)
    const r3 = await vbt({}, '--restore-gepard', snap, '--run', '--data', tData)
    const rrows = trowsNow()
    const snapRows = Object.fromEntries((new Database(join(snap, 'voice/voice.db'), { readonly: true }).query('SELECT * FROM clips').all() as Clip[]).map(c => [`${c.voice}:${c.text}`, c]))
    const back = await Promise.all(tlocal.map(k => pace(new Uint8Array(readFileSync(join(tData, 'app/voice/files', rrows[k].file))))))
    check('voice-build --restore-gepard --run (--max 1, then the rest, then nothing): each paced local clip back to its file (byte for byte the snapshot\'s) and its row (level, gain; tempo and edges cleared), the paced file gone; the other clips as they were',
      r1.exitCode === 0 && r1.stdout.includes('Restored: 1 (1 byte for byte') && r2.stdout.includes('Restored: 1 (1 byte for byte') && r2.stdout.includes('still paced: 0') && r3.stdout.includes('Paced local clips: 0') &&
        tlocal.every(k => rrows[k].file === tFile[k] && rrows[k].tempo == null && rrows[k].edges == null && rrows[k].level === snapRows[k].level && rrows[k].gain === snapRows[k].gain && rrows[k].hits === 3 &&
          Buffer.from(readFileSync(join(tData, 'app/voice/files', tFile[k]))).equals(readFileSync(join(snap, 'voice/files', tFile[k]))) && !existsSync(join(tData, 'app/voice/files', trows[k].file))) &&
        back.every(p => !!p && Math.abs(p.secs - 3.9) < 0.1) && JSON.stringify(rrows[elKey]) === JSON.stringify(trows[elKey]) && snapFiles() === snapBefore,
      [r1.stdout + r1.stderr, r2.stdout, r3.stdout, rrows])
    const rn = tbridge.checkExternal()
    check('…the bridge sees the restored clips and tells the app (voice_updated)', rn === 2 && ttold.join() === '2,2', [rn, ttold])

    // --edges: every clip's silence at its edges, the local ones as restored, the ElevenLabs ones as they were
    const edry = await vbt({}, '--edges', '--data', tData)
    const ej = JSON.parse((await vbt({}, '--edges', '--json', '--data', tData)).stdout || '{}')
    check('voice-build --edges (dry run): every clip measured, per engine how many to trim (the three with 0.6 s before their sound, not the one without), the silence now and after; nothing changed',
      edry.exitCode === 0 && ej.waiting === 4 && ej.measured === 4 && ej.trim === 3 && ej.groups?.gepard?.trim === 2 && ej.groups?.elevenlabs?.trim === 1 && ej.groups.gepard.lead.now.median > 0.5 && ej.groups.gepard.lead.after.median === EDGES.lead &&
        ej.clips?.find((c: any) => c.voice === 'grandma')?.trim === false && edry.stdout.includes('to trim') && edry.stdout.includes('--edges --run') && JSON.stringify(trowsNow()) === JSON.stringify(rrows), [edry.stdout, edry.stderr, ej])
    const e1 = await vbt({}, '--edges', '--run', '--max', '2', '--data', tData)
    const e2 = await vbt({}, '--edges', '--run', '--data', tData)
    const e3 = await vbt({}, '--edges', '--run', '--data', tData)
    const erows = trowsNow()
    const ep = await Promise.all([...tlocal, elKey].map(k => pace(new Uint8Array(readFileSync(join(tData, 'app/voice/files', erows[k].file))))))
    check('voice-build --edges --run (--max 2, then the rest, then nothing): the three trimmed once and levelled again, under a new name, the old file gone; the one without silence only recorded (its file kept); at their own pace',
      e1.exitCode === 0 && e1.stdout.includes('2 clip(s) done') && e2.stdout.includes('2 clip(s) done') && e3.stdout.includes('0 clip(s) done') &&
        [...tlocal, elKey].every(k => erows[k].edges === 1 && erows[k].file === processedFile(rrows[k].file, { level: LEVEL.target, edges: true }) && !existsSync(join(tData, 'app/voice/files', rrows[k].file)) && erows[k].tempo == null) &&
        erows['grandma:Kje je pošta?'].edges === 1 && erows['grandma:Kje je pošta?'].file === tFile['grandma:Kje je pošta?'] &&
        ep.every(p => !!p && p.lead <= 0.08 && p.tail <= 0.16 && Math.abs(p.longest - 1) < 0.06 && Math.abs(p.secs - (EDGES.lead + 2.6 + EDGES.tail)) < 0.12) &&
        (await Promise.all([...tlocal, elKey].map(k => measure(new Uint8Array(readFileSync(join(tData, 'app/voice/files', erows[k].file))))))).every(m => near(m)),
      [e1.stdout + e1.stderr, e2.stdout, e3.stdout, erows, ep])
    const en = tbridge.checkExternal()
    check('…the bridge sees the trimmed clips and tells the app (voice_updated); the one only recorded is no change', en === 3 && ttold.join() === '2,2,3', [en, ttold])
    tbridge.close()

    // voice-build --pace on a copy of the data: the girl's two ElevenLabs clips from before her tempo, her local clip, the
    // grandmother's (no tempo), and a local clip the local voice's pace sped up before (×1.05; none set now)
    {
      const pData = tempDir('lani-tempo-data-')
      mkdirSync(join(pData, 'app/voice/files'), { recursive: true })
      const pdb = new Database(join(pData, 'app/voice/voice.db'))
      pdb.exec('CREATE TABLE clips (key TEXT PRIMARY KEY, norm TEXT NOT NULL, text TEXT NOT NULL, voice TEXT NOT NULL, engine TEXT NOT NULL, file TEXT NOT NULL, chars INTEGER NOT NULL, created_at TEXT NOT NULL, source TEXT NOT NULL, method TEXT, hits INTEGER NOT NULL DEFAULT 0, denoise TEXT, level REAL, gain REAL, tempo REAL, edges INTEGER, engine_voice TEXT, model TEXT)')
      const pclips: [string, string, string, number | null][] = [
        ['Vidim, vidim nekaj, česar ti ne vidiš …', 'girl', 'elevenlabs', null], ['Je oster.', 'girl', 'elevenlabs', null], ['Toplo, toplo!', 'girl', 'gepard', null],
        ['Kje je pošta?', 'grandma', 'elevenlabs', null], ['Dober dan.', 'male', 'gepard', 1.05],
      ]
      pclips.forEach(([text, voice, engine, t], i) => {
        const file = `${createHash('sha1').update(`tempo|${voice}|${engine}|${text}`).digest('hex')}.mp3`
        writeFileSync(join(pData, 'app/voice/files', file), levelled)
        pdb.query('INSERT INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, hits, level, gain, tempo, edges) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)')
          .run(`${voice}:${normalizeText(text)}`, normalizeText(text), text, voice, engine, file, text.length, `2026-10-1${i}T10:00:00.000Z`, 'ispy:test', 2, LEVEL.target, 0, t, 1)
      })
      pdb.close()
      const pc = tempDir('lani-tempo-data-cast-')
      const pcBase = JSON.parse(readFileSync(CAST_FILE, 'utf8'))
      const castOf = (girl: unknown) => {
        const c = structuredClone(pcBase)
        for (const sp of Object.values(c.speakers) as any[]) delete sp.tempo
        if (girl !== undefined) c.speakers.girl.tempo = girl
        const f = join(pc, `cast-${JSON.stringify(girl ?? 'none').replace(/\W/g, '')}.json`)
        writeFileSync(f, JSON.stringify(c))
        return { LANI_VOICE_CAST: f }
      }
      const prows = () => {
        const d = new Database(join(pData, 'app/voice/voice.db'), { readonly: true })
        const r = Object.fromEntries((d.query('SELECT * FROM clips').all() as Clip[]).map(c => [`${c.voice}:${c.text}`, c]))
        d.close()
        return r
      }
      const secsOf = (c: Clip) => mp3Duration(new Uint8Array(readFileSync(join(pData, 'app/voice/files', c.file))))
      const girlEl = ['girl:Vidim, vidim nekaj, česar ti ne vidiš …', 'girl:Je oster.']
      const others = ['girl:Toplo, toplo!', 'grandma:Kje je pošta?', 'male:Dober dan.']
      const before = prows()
      // the clips' length as stored: 3.9 s, at the level
      const made = mp3Duration(levelled)
      const at11 = castOf({ elevenlabs: 1.1 })
      const pdry = await vbt(at11, '--pace', '--data', pData)
      const pj = JSON.parse((await vbt(at11, '--pace', '--json', '--data', pData)).stdout || '{}')
      check('voice-build --pace (dry run): the girl\'s two ElevenLabs clips wait (×1.1, the cast\'s tempo), each measured now and as it would be paced (more characters a second); her local clip, the grandmother\'s and the one the local pace sped up (left for --restore-gepard) don\'t; nothing changed',
        pdry.exitCode === 0 && pj.waiting === 2 && pj.measured === 2 && pj.clips?.every((c: any) => c.voice === 'girl' && c.engine === 'elevenlabs' && c.by === 1.1 && c.after.secs < c.now.secs - 0.25) &&
          pj.voices?.['girl elevenlabs']?.rate.after.median > pj.voices['girl elevenlabs'].rate.now.median * 1.07 && JSON.stringify(pj.cast) === '{"girl":{"elevenlabs":1.1}}' &&
          pdry.stdout.includes('girl elevenlabs') && pdry.stdout.includes('girl (elevenlabs ×1.1)') && pdry.stdout.includes('×1.1') && pdry.stdout.includes('Dry run') && pdry.stdout.includes('--pace --run') &&
          pdry.stdout.includes('1 local clip(s) were paced before') && JSON.stringify(prows()) === JSON.stringify(before),
        [pdry.stdout, pdry.stderr, pj])
      const q1 = await vbt(at11, '--pace', '--run', '--max', '1', '--data', pData)
      const q2 = await vbt(at11, '--pace', '--run', '--data', pData)
      const q3 = await vbt(at11, '--pace', '--run', '--data', pData)
      const r11 = prows()
      check('voice-build --pace --run (--max 1, then the rest, then nothing): each of the girl\'s ElevenLabs clips paced once (×1.1: 3.9 s → 3.55 s), levelled again in the same encode, under a new name, the old file gone; recorded (tempo); the other clips as they were',
        q1.exitCode === 0 && q1.stdout.includes('Paced 1 clip(s)') && q2.stdout.includes('Paced 1 clip(s)') && q3.stdout.includes('Paced 0 clip(s)') &&
          girlEl.every(k => r11[k].tempo === 1.1 && r11[k].level === LEVEL.target && r11[k].edges === 1 && r11[k].hits === 2 && r11[k].file === processedFile(before[k].file, { level: LEVEL.target, tempo: 1.1 }) &&
            !existsSync(join(pData, 'app/voice/files', before[k].file)) && Math.abs(secsOf(r11[k]) - made / 1.1) < 0.08) &&
          others.every(k => JSON.stringify(r11[k]) === JSON.stringify(before[k]) && existsSync(join(pData, 'app/voice/files', before[k].file))),
        [q1.stdout + q1.stderr, q2.stdout, q3.stdout, r11])
      const at12 = castOf({ elevenlabs: 1.2 })
      const q4 = await vbt(at12, '--pace', '--run', '--data', pData)
      const r12 = prows()
      check('…a tempo changed (1.2): her clips paced by the difference from their files as they are (×1.0909), under a new name; recorded 1.2',
        q4.exitCode === 0 && q4.stdout.includes('Paced 2 clip(s)') && girlEl.every(k => r12[k].tempo === 1.2 && r12[k].file === processedFile(r11[k].file, { level: LEVEL.target, tempo: 1.2 }) && Math.abs(secsOf(r12[k]) - made / 1.2) < 0.1),
        [q4.stdout + q4.stderr, r12])
      const none = castOf(undefined)
      const q5 = await vbt(none, '--pace', '--run', '--data', pData)
      const q6 = await vbt(none, '--pace', '--data', pData)
      const r1 = prows()
      check('…the tempo taken away: her clips slowed down to their own pace again (÷1.2), recorded at their own (no tempo); then nothing to pace',
        q5.exitCode === 0 && q5.stdout.includes('Paced 2 clip(s)') && girlEl.every(k => r1[k].tempo == null && Math.abs(secsOf(r1[k]) - made) < 0.12) &&
          q6.exitCode === 0 && q6.stdout.includes('nothing to pace') && q6.stdout.includes('keeps its own pace') && JSON.stringify(prows()) === JSON.stringify(r1),
        [q5.stdout + q5.stderr, q6.stdout, r1])
    }
  }

  // --- voice profiles through the bridge ---------------------------------------------------------------
  {
    bridgeEleven.st.fail = 0
    bridgeGepard.st.busy = false
    const post = (people: unknown) => fetch(`${base}/voice/profiles`, { method: 'POST', headers: auth, body: JSON.stringify({ people }) })
    const g0 = await (await fetch(`${base}/voice/profiles`, { headers: auth })).json()
    check('GET /voice/profiles: the cast, each with their speaker, pitch and pace', g0.people?.micka?.speaker === 'grandma' && typeof g0.people.micka.pitch === 'number' && Object.keys(g0.people).length >= 13, Object.keys(g0.people ?? {}))
    check('POST /voice/profiles rejects junk', (await post([{ id: '../x', name: '', gender: 'x' }])).status === 400)
    const p1 = await (await post([{ id: 'micka', name: 'Babica Micka', gender: 'female', speaker: 'grandma', cast: true }, { id: 'n-rok-humar', name: 'Rok Humar', gender: 'male', speaker: 'young-man', stage: 'adult' }])).json()
    check('POST /voice/profiles: residents get a profile too, with their own pitch and pace', p1.people?.['n-rok-humar']?.speaker === 'young-man' && (p1.people['n-rok-humar'].pitch !== 1 || p1.people['n-rok-humar'].rate !== 1), p1.people?.['n-rok-humar'])
    let own: any
    for (let i = 0; i < 50 && own?.speaker !== '@micka'; i++) {
      own = (await (await fetch(`${base}/voice/profiles`, { headers: auth })).json()).people?.micka
      if (own?.speaker !== '@micka') await Bun.sleep(100)
    }
    check('the bridge designs a voice for Micka by itself (she lives here, the account has room)', own?.speaker === '@micka' && own.status === 'own' && bridgeEleven.st.created.length >= 1, own)
    const said = await (await fetch(`${base}/voice/say`, { method: 'POST', headers: auth, body: JSON.stringify({ text: 'Pridi k mizi, Jan.', voice: '@micka' }) })).json()
    check('POST /voice/say in her own voice', said.voice === '@micka' && !!bridgeEleven.st.tts.at(-1)?.voice?.startsWith('own'), [said, bridgeEleven.st.tts.at(-1)])
    check('POST /voice/say for someone without a voice of their own is a 400 (the app uses the archetype)', (await fetch(`${base}/voice/say`, { method: 'POST', headers: auth, body: JSON.stringify({ text: 'Živjo.', voice: '@n-rok-humar' }) })).status === 400)
    const tp = await client.callTool({ name: 'voice_profiles', arguments: {} })
    const rows = JSON.parse((tp.content as { text: string }[])[0].text)
    check('voice_profiles tool lists who has a voice of their own', !tp.isError && rows.some((r: any) => r.person === 'micka' && r.status === 'own' && r.present), rows.filter((r: any) => r.person === 'micka'))
    const tr = await client.callTool({ name: 'voice_redesign', arguments: { person: 'nobody' } })
    check('voice_redesign: an unknown person is an error', !!tr.isError)
  }

  // --- voice store through the bridge ------------------------------------------------------------------
  {
    for (let i = 0; i < 30; i++) {
      const s = await (await fetch(`${base}/voice/status`, { headers: auth })).json()
      if (s.queued === 0) break
      await Bun.sleep(100)
    }
    const vIdx = await (await fetch(`${base}/voice/index`, { headers: auth })).json()
    check('published content is voiced in the background', !!vIdx.kruh?.female && !!vIdx['dober dan kaj bo']?.female && !!vIdx['dober dan izvolite']?.female, Object.keys(vIdx))
    const vEvents = await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()
    check('voice_updated event for the app', vEvents.some((b: any) => b.event.type === 'voice_updated'))

    const say = (text: string, voice?: string, headers: Record<string, string> = auth) =>
      fetch(`${base}/voice/say`, { method: 'POST', headers, body: JSON.stringify(voice ? { text, voice } : { text }) })
    // the Slovene village's narrators speak with their Slovene ElevenLabs voices (LANI_VOICE_NARRATOR_SL: elevenlabs by default)
    const NINA = cast().female.languages!.sl.elevenlabs
    const LUKA = cast().male.languages!.sl.elevenlabs
    const calls = bridgeEleven.st.tts.length
    const gcalls = bridgeGepard.st.calls.length
    const s1 = await say('Dobro jutro, Micka!')
    const s1Body = await s1.json()
    check('POST /voice/say: a narrator\'s line in the Slovene village in its Slovene ElevenLabs voice (female: Nina), the voice the app knows (female); never Matilda, the worker not asked',
      s1.ok && s1Body.engine === 'elevenlabs' && s1Body.voice === 'female' && /^\/voice\/file\/[a-f0-9]{40}\.mp3$/.test(s1Body.url) && bridgeEleven.st.tts.length === calls + 1 && bridgeEleven.st.tts.at(-1)?.voice === NINA && bridgeEleven.st.tts.at(-1)?.body.language_code === 'sl' && bridgeGepard.st.calls.length === gcalls, s1Body)
    const s2 = await (await say('dobro jutro micka')).json()
    check('POST /voice/say serves the cached clip', s2.url === s1Body.url && s2.voice === 'female' && bridgeGepard.st.calls.length === gcalls && bridgeEleven.st.tts.length === calls + 1)
    const s3 = await (await say('Dobro jutro, Micka!', 'male')).json()
    check('male voice is its own clip (Luka)', s3.url !== s1Body.url && s3.voice === 'male' && s3.engine === 'elevenlabs' && bridgeEleven.st.tts.at(-1)?.voice === LUKA && bridgeGepard.st.calls.length === gcalls, s3)
    const file = await fetch(`${base}${s1Body.url}`, { headers: auth })
    const bytes = new TextDecoder().decode((await file.arrayBuffer()).slice(6))
    check('GET /voice/file serves audio/mpeg', file.ok && file.headers.get('content-type') === 'audio/mpeg' && bytes === `el:${NINA}:Dobro jutro, Micka!`, bytes)
    check('GET /voice/file rejects odd names', (await fetch(`${base}/voice/file/..%2Fvoice.db`, { headers: auth })).status === 404 && (await fetch(`${base}/voice/file/${'0'.repeat(40)}.mp3`, { headers: auth })).status === 404)
    const idx2 = await (await fetch(`${base}/voice/index`, { headers: auth })).json()
    check('GET /voice/index has both voices', idx2['dobro jutro micka']?.female === s1Body.url && idx2['dobro jutro micka']?.male === s3.url, idx2['dobro jutro micka'])
    check('POST /voice/say without text is a 400', (await say('  ')).status === 400)
    const s7 = await (await say('Dobro jutro, Micka!', 'grandma')).json()
    check('POST /voice/say in a character voice (grandma: the cast\'s ElevenLabs voice)', s7.voice === 'grandma' && s7.url !== s1Body.url && bridgeEleven.st.tts.at(-1)?.voice === 'V5O4WAcdw7w3Ccfvma8Z', s7)
    const unknownVoice = await say('Dobro jutro!', 'soprano')
    check('POST /voice/say with a voice not in the cast is a 400 (the app tries its fallback)', unknownVoice.status === 400 && (await unknownVoice.text()).includes('grandma'))
    const idx3 = await (await fetch(`${base}/voice/index`, { headers: auth })).json()
    check('GET /voice/index carries the character voices next to the narrators', idx3['dobro jutro micka']?.grandma === s7.url && !!idx3['dobro jutro micka']?.female, idx3['dobro jutro micka'])

    // The learner's own lines (the voice "player"): said in the cast's player for the profile's gender, on the normal path,
    // their clip the voice's (Jan, a man: the male narrator's, Luka in the Slovene village)
    {
      const p0 = bridgeEleven.st.tts.length
      const mine = await say('Ja, zelo rad.', 'player')
      const mineBody = await mine.json()
      check("POST /voice/say for the learner's own line (player): Jan is a man, so the male narrator's Slovene voice (Luka) on the normal path, the clip the voice the app knows it by (male), in the index under it",
        mine.ok && mineBody.voice === 'male' && mineBody.engine === 'elevenlabs' && bridgeEleven.st.tts.length === p0 + 1 && bridgeEleven.st.tts.at(-1)?.voice === LUKA && bridgeEleven.st.tts.at(-1)?.body.text === 'Ja, zelo rad.' &&
          (await (await fetch(`${base}/voice/index`, { headers: auth })).json())['ja zelo rad']?.male === mineBody.url, [mineBody, bridgeEleven.st.tts.at(-1)])
      const again = await (await say('ja, zelo rad', 'player')).json()
      const asMale = await (await say('Ja, zelo rad.', 'male')).json()
      check("… asked again: the cached clip, which is the male narrator's too (one clip of a text)", again.url === mineBody.url && asMale.url === mineBody.url && bridgeEleven.st.tts.length === p0 + 1, [again, asMale])
      const profiles = await (await fetch(`${base}/voice/profiles`, { headers: auth })).json()
      check('GET /voice/profiles says the voice the learner\'s lines are in (male), for the app to look their clips up by', profiles.player === 'male', profiles.player)
      if (ffmpeg) {
        const word = await (await say('Hvala lepa', 'player')).json()
        const take = bridgeEleven.st.tts.at(-1)
        check("the learner's own line of a word to three, made new: said inside its carrier sentence, as a re-record says it", word.voice === 'male' && take?.timestamps === true && take.voice === LUKA && take.body.text === 'Beseda je: Hvala lepa.', [word, take])
        const lepa = await (await say('Lepa hvala', 'male')).json()
        const lepaTake = bridgeEleven.st.tts.at(-1)
        check("… and the male narrator's own new line of a word to three too (Luka, since 2026-10-26)", lepa.voice === 'male' && lepaTake?.timestamps === true && lepaTake.voice === LUKA && lepaTake.body.text === 'Beseda je: Lepa hvala.', [lepa, lepaTake])
      }
      const prep = await (await fetch(`${base}/voice/prepare`, { method: 'POST', headers: auth, body: JSON.stringify({ texts: [{ text: 'Ja, zelo rad.', voice: 'player' }] }) })).json()
      check('POST /voice/prepare in the learner\'s voice: their clip, under the voice asked for', prep.clips?.[0]?.url === mineBody.url && prep.clips[0].voice === 'player', prep)
      // the profile says a woman, then no gender: the female narrator's voice (Nina) both times
      const profile = join(dataDir, 'learner-profile.json')
      const was = readFileSync(profile, 'utf8')
      const as = (gender: string | undefined, t: number) => {
        const p = JSON.parse(was)
        writeFileSync(profile, JSON.stringify({ ...p, learner: { ...p.learner, gender } }, null, 2))
        utimesSync(profile, t, t) // its own mtime: the bridge reads it again
      }
      try {
        as('female', Date.now() / 1000 + 5)
        const her = await (await say('Ja, zelo rada.', 'player')).json()
        const herProfiles = await (await fetch(`${base}/voice/profiles`, { headers: auth })).json()
        check("a learner who is a woman: her lines in the female narrator's Slovene voice (Nina)", her.voice === 'female' && bridgeEleven.st.tts.at(-1)?.voice === NINA && herProfiles.player === 'female', [her, herProfiles.player])
        as(undefined, Date.now() / 1000 + 10)
        const none = await (await fetch(`${base}/voice/profiles`, { headers: auth })).json()
        const noneSaid = await (await say('Ja, zelo rada.', 'player')).json()
        check('a profile without a gender: the female voice (as an unknown name counts)', none.player === 'female' && noneSaid.url === her.url, [none.player, noneSaid])
      } finally {
        writeFileSync(profile, was)
        utimesSync(profile, Date.now() / 1000 + 15, Date.now() / 1000 + 15)
      }
      check('… and Jan a man again', (await (await fetch(`${base}/voice/profiles`, { headers: auth })).json()).player === 'male')
    }

    check('family token reads the voice index and files', (await fetch(`${base}/voice/index`, { headers: fam })).ok && (await fetch(`${base}${s1Body.url}`, { headers: fam })).ok)
    check('family token cannot synthesize or read status', (await say('Živjo', undefined, { ...fam, 'content-type': 'application/json' })).status === 403 && (await fetch(`${base}/voice/status`, { headers: fam })).status === 403)
    check('voice routes need a token', (await fetch(`${base}/voice/index`)).status === 401 && (await fetch(`${base}${s1Body.url}`)).status === 401)

    const st = await (await fetch(`${base}/voice/status`, { headers: auth })).json()
    check('GET /voice/status: clips, quota, engines, corpus', st.clips?.total >= 3 && st.engines?.elevenlabs?.up === true && st.engines.elevenlabs.quota?.limit === 100_000 && st.engines.gepard?.up === true && st.corpus?.texts > 0, st)
    check('GET /voice/status never shows the key', !JSON.stringify(st).includes('test-key'))
    const vt = await client.callTool({ name: 'voice_status', arguments: {} })
    check('voice_status tool', !vt.isError && JSON.stringify(vt).includes('elevenlabs') && JSON.stringify(vt).includes('gepard'))
    check('instructions say audio is automatic', JSON.stringify(await guideOf()).includes('Audio is automatic'))
    check('GET /voice/status: the short narrator words (carrier sentence, old-style, the daily cap)', st.carrier?.daily === 40 && typeof st.carrier.old === 'number' && st.carrier.cut === !!ffmpeg, st.carrier)
    check('GET /voice/status: the voices the cast filters (denoise: Stari Janez\'s own voice), and whether the node can (ffmpeg)', st.denoise?.voices?.['@janez']?.preset === 'hiss' && st.denoise.ffmpeg === !!ffmpeg, st.denoise)
    check('GET /voice/status: the level (its target and peak, the clips at it and waiting, ffmpeg)', st.level?.target === LEVEL.target && st.level.peak === LEVEL.peak && typeof st.level.levelled === 'number' && typeof st.level.waiting === 'number' && st.level.ffmpeg === !!ffmpeg, st.level)
    check('GET /voice/status: the edges (what is kept, the clips done and waiting), the narrators (their Slovene ElevenLabs voices first: Nina, Luka; the local ones nina and ana for a lone word, marko), no pace set',
      st.edges?.lead === EDGES.lead && st.edges.tail === EDGES.tail && typeof st.edges.done === 'number' && typeof st.edges.waiting === 'number' && st.narrators?.local === true && st.narrators.mode === 'elevenlabs' &&
        st.narrators.elevenlabs_voices?.female === 'Nina' && st.narrators.elevenlabs_voices.male === 'Luka' && st.narrators.voices?.female === 'nina' && st.narrators.word_voices?.female === 'ana' && st.narrators.voices.male === 'marko' && st.narrators.elevenlabs >= 2 &&
        st.cast?.find((c: any) => c.id === 'female')?.languages?.sl === 'Nina' && st.tempo?.tempo === null && st.tempo.pause === null, [st.edges, st.narrators, st.tempo])

    // A narrator word from before the carrier sentence in the bridge's store (an English narrator's): the app downloads it
    // and gets it as it is. In the Slovene village it is never re-voiced in the English voice: the narrator's Slovene
    // ElevenLabs voice makes it soon (Nina, inside its carrier sentence since 2026-10-26), the clip from before kept in the
    // narrator's key.
    if (ffmpeg) {
      const voiceDir = join(dataDir, 'app/voice')
      const oldFile = `${createHash('sha1').update('smoke|old|kolovrat').digest('hex')}.mp3`
      writeFileSync(join(voiceDir, 'files', oldFile), mp3('el:old:kolovrat'))
      const db = new Database(join(voiceDir, 'voice.db'))
      db.query('INSERT OR REPLACE INTO clips (key, norm, text, voice, engine, file, chars, created_at, source) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)').run('female:kolovrat', 'kolovrat', 'kolovrat', 'female', 'elevenlabs', oldFile, 8, '2026-09-01T10:00:00.000Z', 'pack:old')
      db.close()
      const updates = async () => ((await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as any[]).filter(b => b.event.type === 'voice_updated').length
      const u0 = await updates()
      const got = await fetch(`${base}/voice/file/${oldFile}`, { headers: auth })
      check('GET /voice/file of an old-style narrator word: the old clip, at once', got.ok && new TextDecoder().decode((await got.arrayBuffer()).slice(6)) === 'el:old:kolovrat')
      // its carrier take is cut, then levelled (ffmpeg twice): up to 3 s
      const koloUrl = async () => (await (await fetch(`${base}/voice/index`, { headers: auth })).json()).kolovrat?.female as string | undefined
      await Bun.sleep(300)
      for (let i = 0; i < 27 && (await koloUrl()) === `/voice/file/${oldFile}`; i++) await Bun.sleep(100)
      const url = await koloUrl()
      const kolo = bridgeEleven.st.tts.filter(c => c.body.text.includes('kolovrat'))
      check('…no re-voice in the English voice: the narrator\'s Slovene ElevenLabs voice makes it soon (Nina, a new lone word inside its carrier sentence: "Beseda je: kolovrat."), the index on it, the app told, counted for the day; the clip from before kept',
        url !== `/voice/file/${oldFile}` && /^\/voice\/file\/[a-f0-9]{40}\.mp3$/.test(url ?? '') && kolo.length === 1 && kolo[0].voice === NINA && kolo[0].body.text === 'Beseda je: kolovrat.' && !!kolo[0].timestamps &&
          (await updates()) > u0 && (await fetch(`${base}/voice/file/${oldFile}`, { headers: auth })).ok, [url, kolo])
      const rk = await fetch(`${base}/voice/redo`, { method: 'POST', headers: auth, body: JSON.stringify({ text: 'kolovrat', voice: 'female', client_id: 'redo-smoke-kolovrat' }) })
      const rkb = await rk.json()
      check('…a long press: a new take in Nina\'s voice inside its carrier sentence, under a new name, the take before gone, the answer in the voice the app knows (female); counted in the day\'s re-records (30)',
        rk.ok && rkb.status === 'done' && rkb.voice === 'female' && rkb.url !== url && bridgeEleven.st.tts.at(-1)?.body.text === 'Beseda je: kolovrat.' && !!bridgeEleven.st.tts.at(-1)?.timestamps && bridgeEleven.st.tts.at(-1)?.voice === NINA &&
          (await fetch(`${base}${url}`, { headers: auth })).status === 404 && (await fetch(`${base}/voice/file/${oldFile}`, { headers: auth })).ok && rkb.today >= 1 && rkb.daily === 30, rkb)
    }

    // Jan's re-record (a long press on 🔊): POST /voice/redo makes the clip again now, under a new name.
    {
      const redo = (body: unknown, headers: Record<string, string> = auth) => fetch(`${base}/voice/redo`, { method: 'POST', headers, body: JSON.stringify(body) })
      const updates = async () => ((await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as any[]).filter(b => b.event.type === 'voice_updated').length
      const u0 = await updates()
      const n0 = bridgeEleven.st.tts.length
      const r = await redo({ text: 'Dobro jutro, Micka!', voice: 'grandma', client_id: 'redo-smoke-0001' })
      const rb = await r.json()
      const idx = await (await fetch(`${base}/voice/index`, { headers: auth })).json()
      check('POST /voice/redo: made again now (a plain re-take of a sentence), a new file, the old one gone, the index on it, the app told (voice_updated)',
        r.ok && rb.status === 'done' && rb.url !== s7.url && rb.key === 'grandma:dobro jutro micka' && rb.norm === 'dobro jutro micka' && rb.voice === 'grandma' && rb.method === null && rb.daily === 30 && rb.today >= 1 &&
          bridgeEleven.st.tts.length === n0 + 1 && bridgeEleven.st.tts.at(-1)?.body.text === 'Dobro jutro, Micka!' && (await fetch(`${base}${s7.url}`, { headers: auth })).status === 404 &&
          (await fetch(`${base}${rb.url}`, { headers: auth })).ok && idx['dobro jutro micka']?.grandma === rb.url && (await updates()) > u0, rb)
      const replay = await redo({ text: 'Dobro jutro, Micka!', voice: 'grandma', client_id: 'redo-smoke-0001' })
      check('POST /voice/redo: a retry with the same client_id gets the same answer, no second take', replay.headers.get('x-lani-replay') === '1' && (await replay.json()).url === rb.url && bridgeEleven.st.tts.length === n0 + 1)
      const again = await redo({ text: 'dobro jutro micka', voice: 'grandma' })
      const ab = await again.json()
      check('POST /voice/redo: once a clip a day, then "not_needed" with the take from before', again.ok && ab.status === 'not_needed' && ab.url === rb.url && !!ab.message && bridgeEleven.st.tts.length === n0 + 1, ab)
      const nr = await (await redo({ text: 'Dobro jutro, Micka!', voice: 'female' })).json()
      const nr2 = await (await redo({ text: 'Dobro jutro, Micka!', voice: 'female' })).json()
      check('POST /voice/redo of a narrator\'s clip: a re-take in its Slovene ElevenLabs voice (Nina), the answer in the voice the app knows (female); again: once a clip a day',
        nr.status === 'done' && nr.voice === 'female' && nr.url !== s1Body.url && bridgeEleven.st.tts.length === n0 + 2 && bridgeEleven.st.tts.at(-1)?.voice === NINA && nr2.status === 'not_needed' && nr2.url === nr.url, [nr, nr2])
      const st = await (await fetch(`${base}/voice/status`, { headers: auth })).json()
      check('GET /voice/status counts the re-records of the day and their cap, apart from the lazy re-voice\'s', st.carrier?.redos === (ffmpeg ? 3 : 2) && st.carrier.redo_daily === 30 && st.carrier.daily === 40, st.carrier)
      check('POST /voice/redo: a voice not in the cast is a 400, no text a 400', (await redo({ text: 'Živjo', voice: 'soprano' })).status === 400 && (await redo({ text: ' ', voice: 'female' })).status === 400)
      check('POST /voice/redo: the family token may not, nor no token', (await redo({ text: 'Živjo', voice: 'female' }, { ...fam, 'content-type': 'application/json' })).status === 403 && (await redo({ text: 'Živjo', voice: 'female' }, { 'content-type': 'application/json' })).status === 401)
    }

    // The car's quiz getting ready: POST /voice/prepare voices the options the store lacks, not as a live line.
    {
      const prepare = (body: unknown, headers: Record<string, string> = auth) => fetch(`${base}/voice/prepare`, { method: 'POST', headers, body: JSON.stringify(body) })
      check('POST /voice/prepare: no texts, an empty text, more than 20 are a 400',
        (await prepare({ texts: [] })).status === 400 && (await prepare({ texts: [{ text: '  ' }] })).status === 400 && (await prepare({})).status === 400 &&
          (await prepare({ texts: Array.from({ length: 21 }, (_, i) => ({ text: `Ena ${i}.` })) })).status === 400)
      check('POST /voice/prepare: the family token may not', (await prepare({ texts: [{ text: 'Ena.' }] }, { ...fam, 'content-type': 'application/json' })).status === 403)
      const have = (await (await fetch(`${base}/voice/index`, { headers: auth })).json())['dobro jutro micka']?.female
      const n0 = bridgeEleven.st.tts.length
      const g0 = bridgeGepard.st.calls.length
      const fresh = 'Prav! Druga je prava.'
      const body = { texts: [{ text: 'Dobro jutro, Micka!' }, { text: fresh, voice: 'male' }, { text: 'Ena.', voice: 'nobody' }] }
      const r = await prepare(body)
      const p = await r.json()
      check('POST /voice/prepare: a clip the store has as it is, a new one voiced (the male narrator: Luka), a voice not in the cast none; the day\'s count and cap',
        r.ok && p.clips?.length === 3 && !!have && p.clips[0].url === have && p.clips[0].voice === 'female' &&
          p.clips[1].text === fresh && p.clips[1].voice === 'male' && /^\/voice\/file\/[a-f0-9]{40}\.mp3$/.test(p.clips[1].url) && p.clips[1].engine === 'elevenlabs' &&
          p.clips[2].voice === 'nobody' && p.clips[2].url === null && p.daily === 6_000 && p.today === fresh.length &&
          bridgeEleven.st.tts.length === n0 + 1 && bridgeEleven.st.tts.at(-1)?.body.text === fresh && bridgeEleven.st.tts.at(-1)?.voice === LUKA && bridgeGepard.st.calls.length === g0, p)
      const again = await (await prepare(body)).json()
      check('POST /voice/prepare again: the same clips, nothing voiced, the day\'s count as it was', JSON.stringify(again.clips) === JSON.stringify(p.clips) && again.today === p.today && bridgeEleven.st.tts.length === n0 + 1 && bridgeGepard.st.calls.length === g0, again)
    }

    // A school intro's step read out as one clip: its explanation in the learner's own language (lang), the Slovene in it.
    {
      const features = (await (await fetch(`${base}/state`, { headers: auth })).json()).features
      check('GET /state lists "explanations" in its features: the app asks this bridge for an explanation\'s clip', Array.isArray(features) && features.includes('explanations'), features)
      const ask = (body: unknown) => fetch(`${base}/voice/say`, { method: 'POST', headers: auth, body: JSON.stringify(body) })
      const prepare = async (texts: unknown[]) => (await fetch(`${base}/voice/prepare`, { method: 'POST', headers: auth, body: JSON.stringify({ texts }) })).json()
      const n0 = bridgeEleven.st.tts.length
      const g0 = bridgeGepard.st.calls.length
      const text = 'Three or more: mi smo (we are), vi ste (you are: several of you, or one person you say vi to), oni so (they are).'
      const r = await ask({ text, voice: 'teacher', lang: 'en' })
      const rb = await r.json()
      const idx = await (await fetch(`${base}/voice/index`, { headers: auth })).json()
      const call = bridgeEleven.st.tts.at(-1)
      check('POST /voice/say with lang: an explanation in English in the teacher\'s voice, plainly, no language_code, the worker not asked; the index has it under "en|…", not as a line',
        r.ok && rb.voice === 'teacher' && rb.engine === 'elevenlabs' && /^\/voice\/file\/[a-f0-9]{40}\.mp3$/.test(rb.url) && bridgeEleven.st.tts.length === n0 + 1 && call?.voice === cast().teacher.elevenlabs && call.body.text === text &&
          !call.timestamps && !('language_code' in call.body) && bridgeGepard.st.calls.length === g0 && idx[`en|${normalizeText(text)}`]?.teacher === rb.url && !idx[normalizeText(text)], [rb, call])
      const nr = await (await ask({ text: 'Two', voice: 'female', lang: 'en' })).json()
      const ncall = bridgeEleven.st.tts.at(-1)
      check('…a narrator\'s in the Slovene village, a short one: by Nina, plainly (no carrier sentence), the answer in the voice the app knows (female)',
        nr.voice === 'female' && ncall?.voice === NINA && ncall.body.text === 'Two' && !ncall.timestamps && !('language_code' in ncall.body) && bridgeGepard.st.calls.length === g0, [nr, ncall])
      const long = `${text} `.repeat(4).trim()
      check('…over 300 characters: an explanation (with lang) may, a line may not (without lang, or in the village\'s), nor anything over 1,500; a lang is two small letters',
        long.length > 300 && (await ask({ text: long, voice: 'teacher', lang: 'de' })).ok && (await ask({ text: long, voice: 'teacher' })).status === 400 && (await ask({ text: long, voice: 'teacher', lang: 'sl' })).status === 400 &&
          (await ask({ text: 'x'.repeat(1_501), voice: 'teacher', lang: 'en' })).status === 400 && (await ask({ text: 'Two', voice: 'teacher', lang: 'EN' })).status === 400 && (await ask({ text: 'Two', voice: 'soprano', lang: 'en' })).status === 400)
      const st = await (await fetch(`${base}/voice/status`, { headers: auth })).json()
      check('GET /voice/status: the explanations counted apart (by language), not among the lines',
        st.explanations?.clips === 3 && st.explanations.by_lang?.en === 2 && st.explanations.by_lang.de === 1 && st.explanations.chars === text.length + 3 + long.length && !st.clips.by.some((b: any) => b.voice === 'teacher'), [st.explanations, st.clips.by])
      const before = (await prepare([{ text, voice: 'teacher', lang: 'en' }])).today
      const next = 'Slovene has a form for exactly two, the dual (dvojina): midva sva (we two are).'
      const n1 = bridgeEleven.st.tts.length
      const pr = await prepare([{ text: next, voice: 'teacher', lang: 'en' }, { text, voice: 'teacher', lang: 'en' }])
      const idx2 = await (await fetch(`${base}/voice/index`, { headers: auth })).json()
      const pcall = bridgeEleven.st.tts.at(-1)
      check('POST /voice/prepare with lang: the next unit\'s explanation made ahead (plainly, no language_code), one there as it is, counted in the day\'s characters; the index has it',
        /^\/voice\/file\/[a-f0-9]{40}\.mp3$/.test(pr.clips?.[0]?.url) && pr.clips[0].engine === 'elevenlabs' && pr.clips[1].url === rb.url && pr.today === before + next.length && bridgeEleven.st.tts.length === n1 + 1 &&
          pcall?.body.text === next && !('language_code' in pcall.body) && idx2[`en|${normalizeText(next)}`]?.teacher === pr.clips[0].url && bridgeGepard.st.calls.length === g0, [pr, pcall])
      // the app's own requests as a step is read (data/IntroVoice.kt through ClipsApi.say): the explanation first, then the
      // step's Slovene, all at once, against a plan of three at once that takes its time; then the key the app looks the
      // clip up by (IntroSpeech.key; the app's IntroSpeechTest has the same) and the file it downloads
      Object.assign(bridgeEleven.st, { concurrency: 3, delayMs: 80, refused: 0, maxInFlight: 0 })
      const said = 'To say no, ne and the form melt into one word: nisem, nisi, ni; nismo, niste, niso; for two nisva, nista. Never ne sem.'
      const key = 'en|to say no ne and the form melt into one word nisem nisi ni nismo niste niso for two nisva nista never ne sem'
      const forms = ['ne', 'nisem, nisi, ni', 'nismo, niste, niso', 'nisva, nista', 'ne sem']
      const g1 = bridgeGepard.st.calls.length
      const [xr, ...fr] = await Promise.all([ask({ text: said, voice: 'teacher', lang: 'en' }), ...forms.map(text => ask({ text, voice: 'teacher' }))])
      const xb = xr.status === 200 ? await xr.json() : null
      const fb = await Promise.all(fr.map(r => (r.status === 200 ? r.json() : null)))
      const idx3 = await (await fetch(`${base}/voice/index`, { headers: auth })).json()
      const file = xb?.url ? await fetch(`${base}${xb.url}`, { headers: auth }) : null
      check('the app\'s requests as a step is read (POST /voice/say: its explanation with lang "en" in the teacher\'s voice and its Slovene forms, six at once) against a plan of three at once: none refused, all by ElevenLabs, the worker not asked; the index has the explanation under the key the app looks it up by ("en|to say no ne and …"), its file downloads',
        bridgeEleven.st.refused === 0 && bridgeEleven.st.maxInFlight <= 3 && xb?.engine === 'elevenlabs' && xb.voice === 'teacher' && fb.every(b => b?.engine === 'elevenlabs') && bridgeGepard.st.calls.length === g1 &&
          key === `en|${normalizeText(said)}` && idx3[key]?.teacher === xb.url && file?.status === 200 && file.headers.get('content-type') === 'audio/mpeg', [xb, fb, bridgeEleven.st.refused, bridgeEleven.st.maxInFlight])
      const n2 = bridgeEleven.st.tts.length
      const second = await (await ask({ text: said, voice: 'teacher', lang: 'en' })).json()
      check('…the step read again: the same clip at once, nothing voiced', second.url === xb?.url && bridgeEleven.st.tts.length === n2, second)
      Object.assign(bridgeEleven.st, { concurrency: 0, delayMs: 0 })
      const bad = await fetch(`${base}/voice/prepare`, { method: 'POST', headers: auth, body: JSON.stringify({ texts: [{ text: long, voice: 'teacher' }] }) })
      check('POST /voice/prepare: a line over 300 characters is a 400 as ever', bad.status === 400)
    }

    bridgeEleven.st.fail = 500
    const s4 = await (await say('Kje je pošta?')).json()
    const s4g = await (await say('Kje je pošta?', 'grandpa')).json()
    check('ElevenLabs down: the local worker answers (a narrator\'s line anyway, a villager\'s too)', s4.engine === 'gepard' && s4g.engine === 'gepard' && s4g.voice === 'grandpa', [s4, s4g])
    if (ffmpeg) {
      bridgeGepard.st.audio = workerClip()
      const sp = await (await say('Kje je trgovina? Tam, za vogalom.')).json()
      const spBytes = new Uint8Array(await (await fetch(`${base}${sp.url}`, { headers: auth })).arrayBuffer())
      const stp = (await (await fetch(`${base}/voice/status`, { headers: auth })).json()).tempo
      check('…and the bridge keeps its clip at the worker\'s pace, only the edges trimmed (3.9 s as the worker made it, about 2.75 s stored); nothing paced',
        sp.engine === 'gepard' && Math.abs(mp3Duration(spBytes) - (EDGES.lead + 2.6 + EDGES.tail)) < 0.15 && stp?.tempo === null && stp.paced === 0 && stp.ffmpeg === true && JSON.stringify(stp.voices) === JSON.stringify(tempoVoices()), [sp, mp3Duration(spBytes), stp])
      bridgeGepard.st.audio = undefined
    }
    bridgeGepard.st.busy = true
    const s5 = await say('Kje je banka?')
    check('no engine available: 204 (the app uses phone TTS)', s5.status === 204)
    const s6 = await (await say('Kje je pošta?', 'male')).json()
    check('no engine for the male voice: the female clip rather than nothing', s6.voice === 'female' && s6.url === s4.url, s6)
    const s8 = await (await say('Kje je pošta?', 'boy')).json()
    check('no engine for a character: their gender narrator, then the other one', s8.voice === 'female' && s8.url === s4.url, s8)
  }

  // --- voice profiles: one voice per person (in-process against the fakes) -----------------------------
  {
    const fe = fakeEleven()
    fe.st.voiceLimit = 4
    const el = new ElevenLabs({ key: 'test-key', base: fe.url })
    // a Whisper that understands only the second take
    const heardServer = Bun.serve({
      hostname: '127.0.0.1', port: 0,
      async fetch(req) {
        const audio = new TextDecoder().decode(new Uint8Array(await req.arrayBuffer()).slice(6))
        return Response.json({ text: audio.startsWith('take:1:') ? audio.slice('take:1:'.length) : 'mm hm' })
      },
    })
    const vs = new VoiceStore({ appDir: mkdtempSync(join(tmpdir(), 'lani-prof-')), engines: [el], queueDelayMs: 0 })
    const castList = [
      { id: 'micka', name: 'Babica Micka', voice: 'female' as const, speaker: 'grandma', role: 'Babica · Grandmother', personality: 'Warm grandmother, always cooking. Forgets words, never people. Worries.', order: 1, lines: { greet: [{ sl: 'Dober dan. Si lačen?' }, { sl: 'O, pridi, pridi. Sedi.' }, { sl: 'Jan! Pridi, juha je vroča.' }], idle: [{ sl: 'Kje si bil tako dolgo? Sedi, sedi, jej.' }, { sl: 'Rada mislim na {memory}.' }] } },
      { id: 'ancka', name: 'Teta Ančka', voice: 'female' as const, speaker: 'grandma', order: 5 },
      { id: 'luka', name: 'Pastir Luka', voice: 'male' as const, speaker: 'young-man', order: 2 },
    ]
    const changed: string[] = []
    const prof = new Profiles(vs.database, { eleven: el, sttUrl: `http://127.0.0.1:${heardServer.port}`, cast: () => castList, auto: false, onVoiceChanged: v => { changed.push(v); vs.dropVoice(v) } })
    prof.syncCast()
    const v0 = prof.views()
    check('profiles: the cast has one each, speaking with their archetype', v0.micka?.speaker === 'grandma' && v0.ancka?.speaker === 'grandma' && v0.luka?.speaker === 'young-man' && v0.micka.status === 'shared', v0)
    check('profiles: the first of an archetype keeps its voice as it is, the others have their own pitch and pace', v0.micka.pitch === 1 && v0.micka.rate === 1 && (v0.ancka.pitch !== 1 || v0.ancka.rate !== 1), [v0.micka, v0.ancka])
    const va = variationOf('n-ana-furlan')
    check('profiles: pitch and pace stay in a gentle range and are the same every time', va.pitch >= 0.93 && va.pitch <= 1.07 && va.rate >= 0.96 && va.rate <= 1.04 && JSON.stringify(variationOf('n-ana-furlan')) === JSON.stringify(va), va)
    const desc = describe({ name: 'Babica Micka', archetype: 'grandma', gender: 'female', role: 'Babica · Grandmother', personality: castList[0].personality })
    check('profiles: Voice Design is asked for who they are, a native Slovene speaker from Primorska', desc.includes('grandmother') && desc.includes('seventies') && desc.includes('native Slovene speaker') && desc.includes('Primorska') && desc.includes('Warm grandmother'), desc)
    const sample = sampleOf(castList[0].lines)
    check('profiles: the sample is their own Slovene, no placeholders, long enough for Voice Design', sample.length >= 110 && sample.startsWith('Dober dan. Si lačen?') && !sample.includes('{'), sample)
    check('word match: the same words 1, a garbled half less', wordMatch('Dober dan, Jan!', 'dober dan jan') === 1 && wordMatch('O, pridi, pridi. Sedi.', 'o pridi sedi') === 0.75)

    check('profiles: no one designed before they live in the village', (await prof.designNext()) === 'no one waiting for a voice' && fe.st.designs.length === 0, fe.st.designs)
    prof.upsert([{ id: 'micka', name: 'Babica Micka', gender: 'female', speaker: 'grandma', cast: true }, { id: 'n-ana-furlan', name: 'Ana Furlan', gender: 'female', speaker: 'young-woman', stage: 'adult' }])
    await prof.designNext()
    const mk = prof.get('micka')!
    check('profiles: Micka, of the cast and living here, gets a voice of her own (Voice Design v3, her Slovene sample)', mk.status === 'own' && mk.elevenlabs === 'own1' && fe.st.designs[0]?.model_id === 'eleven_ttv_v3' && fe.st.designs[0]?.text === mk.sample && fe.st.designs[0]?.voice_description === mk.description, [mk.status, fe.st.designs[0]])
    check('profiles: the take Whisper understands best is the one saved', fe.st.created[0]?.generated_voice_id === 'gen1x1' && fe.st.created[0]?.played_not_selected_voice_ids?.length === 2 && fe.st.created[0]?.voice_name === 'Lani @micka (Babica Micka)', fe.st.created[0])
    check('profiles: the resident waits: the slots left are for the cast still to come', prof.get('n-ana-furlan')?.status === 'shared' && fe.st.created.length === 1)
    const vm = prof.views()
    check('profiles: Micka now speaks as "@micka", at her own voice\'s pitch; Ana with the young woman at hers', vm.micka.speaker === '@micka' && vm.micka.pitch === 1 && vm['n-ana-furlan'].speaker === 'young-woman' && vm['n-ana-furlan'].pitch === va.pitch, vm)
    check('cast: "@micka" is a voice, her own, in for the grandmother', personVoice('@micka')?.elevenlabs === 'own1' && elevenlabsOf('@micka') === 'own1' && fallbacks('@micka')[0] === 'grandma' && !personVoice('@ancka'), fallbacks('@micka'))
    const line = await vs.say('Jej, jej, Jan.', '@micka')
    check('voice store: a line of hers is voiced in her own voice, under her own key', line?.voice === '@micka' && line.engine === 'elevenlabs' && fe.st.tts.at(-1)?.voice === 'own1', line)

    // slots: 4, one kept spare, Micka has one: Luka moves in and gets the second; then the cast waiting keeps the rest
    prof.upsert([{ id: 'micka', name: 'Babica Micka', gender: 'female', speaker: 'grandma', cast: true }, { id: 'luka', name: 'Pastir Luka', gender: 'male', speaker: 'young-man', cast: true }, { id: 'n-ana-furlan', name: 'Ana Furlan', gender: 'female', speaker: 'young-woman' }])
    const why = await prof.designNext()
    check('profiles: Luka gets his; then the one spare slot is kept for a redesign', prof.get('luka')?.status === 'own' && fe.st.created.length === 2 && fe.st.voiceSlotsUsed === 2 && typeof why === 'string' && why.includes('spare') === false && why.length > 0, [why, fe.st.voiceSlotsUsed, SPARE_SLOTS])
    check('profiles: whoever the app no longer lists is not present', prof.get('ancka')?.present === 0 && prof.get('micka')?.present === 1)

    // a redesign: a new voice, the old one and its clips go
    const re = await prof.redesign('micka', 'An old woman in her eighties with a very soft, trembling voice. A native Slovene speaker.')
    check('redesign: a new voice from the new description, the old one deleted, her old clips dropped', 'person' in re && re.elevenlabs === 'own3' && fe.st.deleted.includes('own1') && changed.includes('@micka') && !vs.get('jej jej jan', '@micka') && fe.st.designs.at(-1)?.voice_description.includes('eighties'), [re, fe.st.deleted, changed])
    check('redesign: an unknown person is an error', 'error' in (await prof.redesign('nobody')))
    const like = await prof.redesign('luka', undefined, 'grandpa')
    check('redesign like another voice: its sample recording lends the timbre (reference audio), the prompt the rest', 'person' in like && like.status === 'own' && !!fe.st.designs.at(-1)?.reference_audio_base64 && fe.st.designs.at(-1)?.prompt_strength === 0.4, fe.st.designs.at(-1) && { ...fe.st.designs.at(-1), reference_audio_base64: '…' })
    check('redesign like a voice that does not exist is an error', 'error' in (await prof.redesign('luka', undefined, 'nobody-here')))
    heardServer.stop(true)
  }

}
