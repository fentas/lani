// Smoke checks: the voice store, companion/bin/voice-build, and the voice routes.
import { Database } from 'bun:sqlite'
import { createHash } from 'node:crypto'
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { validateScenario } from '../../src/scenarios'
import { validateModule } from '../../src/spec'
import { CAST_FILE, carrierVoice, cast, fallbacks, newcomerSpeaker, speakerFor } from '../../src/cast'
import { normalizeText } from '../../src/family'
import { charsPerSecond, corpus, denoisedFile, ElevenLabs, ffmpegCutter, ffmpegDenoiser, ffmpegLeveller, ffmpegLoudness, ffmpegPace, ffmpegRecordingLeveller, Gepard, gepardTempo, isMp3, isShortText, LEVEL, levelGain, mp3Duration, mp3Kbps, PACE, paceChain, processedFile, redoKept, VoiceStore, wordSpan, type Clip, type Cutter, type Denoiser, type Engine, type Leveller } from '../../src/voice'
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
    check('ElevenLabs request: eleven_v3, Slovenian, Matilda, mp3', call?.voice === 'XrExE9yKIg1WjnnlVkGX' && call.body.model_id === 'eleven_v3' && call.body.language_code === 'sl' && call.body.text === 'Dober dan!' && call.query === '?output_format=mp3_44100_128', call)
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
        thanks: [vl('Hvala, fant.', 'Thanks, lad.'), vl('Dobro delo.', 'Good work.')],
        remember: [vl('Še vedno mislim na {memory}.', 'I still think of {memory}.', 1), vl('Ne pozabim na {memory}.', 'I do not forget {memory}.', 2)],
        idle: [vl('Nekoč je bil sneg do strehe.', 'Once the snow reached the roof.'), vl('Muri, kje si?', 'Muri, where are you?')],
        cheer: [vl('Tako je.', 'That is right.'), vl('Bravo, fant.', 'Well done, lad.')],
        comfort: [vl('Počasi. Še enkrat.', 'Slowly. Once more.'), vl('Nič hudega, fant.', 'Never mind, lad.')],
        listen: [vl('Poslušaj, fant.', 'Listen, lad.'), vl('Tiho. Poslušaj.', 'Quiet. Listen.')],
        bye: [vl('Lahko noč, fant.', 'Good night, lad.'), vl('Pojdi, pojdi.', 'Go, go.')],
      },
    }))
    fe.st.used = fe.st.limit - 5_000 - 30 // 30 characters above the reserve
    // the village's culture pack as a manifest alone: its own packs and scenes aren't this corpus's
    const vbCultures = mkdtempSync(join(tmpdir(), 'lani-vb-cultures-'))
    mkdirSync(join(vbCultures, 'primorska'))
    cpSync(join(culturesDir, 'primorska/culture.json'), join(vbCultures, 'primorska/culture.json'))
    const vbEnv = { ...process.env, LANI_CULTURES_DIR: vbCultures, LANI_DATA_DIR: vbData, LANI_PACKS_DIR: vbPacks, LANI_MODULES_DIR: vbModules, LANI_SCENARIOS_DIR: vbScenarios, LANI_SCENES_DIR: mkdtempSync(join(tmpdir(), 'lani-vb-scenes-')), LANI_DRILLS_DIR: mkdtempSync(join(tmpdir(), 'lani-vb-drills-')), LANI_VILLAGERS_DIR: vbVillagers, LANI_KEYS_FILE: '/nonexistent', ELEVENLABS_API_KEY: 'test-key', ELEVENLABS_BASE_URL: fe.url, LANI_GEPARD_URL: fg.url } as Record<string, string>
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
    check('voice-build --run generates in priority order, the villager first in his speaker\'s voice', ran.exitCode === 0 && fe.st.tts.length - ttsBefore === plan.would.elevenlabs.texts && fg.st.calls.length - gpBefore === plan.would.gepard.texts && fe.st.tts[ttsBefore]?.body.text === 'Dober dan, mladi mož.' && fe.st.tts[ttsBefore]?.voice === cast().grandpa.elevenlabs && fg.st.calls[gpBefore]?.speaker === 'marko' && fg.st.calls.at(-1)?.text === 'Kruh, prosim.' && fg.st.calls.at(-1)?.speaker === 'ana', ranOut)
    check('voice-build never prints the key', !ranOut.includes('test-key'))
    const after = JSON.parse((await voiceBuild('--json')).stdout || '{}')
    check('after --run everything is cached', after.cached === 38 && after.missing === 0 && after.by_engine?.elevenlabs === plan.would.elevenlabs.texts, after)
    check('voice-build --revoice refuses a speaker not in the cast', (await voiceBuild('--revoice', 'soprano')).exitCode === 2)
    fe.st.used = fe.st.limit - 5_000 - 1_000
    const rev = JSON.parse((await voiceBuild('--json', '--revoice', 'grandpa')).stdout || '{}')
    check('voice-build --revoice plans every clip of that speaker again with ElevenLabs', rev.would?.elevenlabs.texts === 14 && rev.would.elevenlabs.replace === 14 && rev.would.gepard.texts === 0, rev.would)

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

    // A store from before the carrier sentence: the narrator words said plainly by ElevenLabs (no ffmpeg then).
    if (ffmpeg) {
      const plainly = await voiceBuildIn({ ...vbEnv, LANI_FFMPEG: 'off' }, '--run', '--upgrade')
      const words = ['kruh', 'mleko', 'sir', 'jajce', 'juha', 'vilice', 'nož', 'Dobro jutro', 'hvala', 'sem', 'si', 'Dober dan', 'Živjo', 'Oj']
      const wordChars = words.reduce((n, w) => n + `Beseda je: ${w}.`.length, 0)
      const tsBefore = fe.st.tts.length
      const oldPlan = JSON.parse((await voiceBuild('--json', '--revoice-words', '--max', '3')).stdout || '{}')
      check('voice-build: the plan counts the old-style narrator words (said plainly) and what re-voicing them all costs', plainly.exitCode === 0 && oldPlan.carrier?.old === words.length && oldPlan.carrier.old_chars === wordChars && oldPlan.carrier.carrier === 0 && oldPlan.carrier.daily === 40 && oldPlan.carrier.cut === true, [plainly.stderr.slice(-400), oldPlan.carrier])
      check('voice-build --revoice-words --max 3: the dry run plans three, the rest later, and voices nothing', oldPlan.revoice_words?.texts === 3 && oldPlan.revoice_words.later.texts === words.length - 3 && fe.st.tts.length === tsBefore, oldPlan.revoice_words)
      const shown = await voiceBuild('--revoice-words')
      check('voice-build prints the old-style words, what re-voicing them costs, and how to', shown.exitCode === 0 && shown.stdout.includes(`${words.length} old-style`) && shown.stdout.includes('re-voicing all old-style') && shown.stdout.includes('voice-build --revoice-words --run'), shown.stdout)
      check('voice-build --max needs --revoice-words', (await voiceBuild('--max', '3')).exitCode === 2)
      const rw = await voiceBuild('--revoice-words', '--max', '3', '--run')
      const after2 = JSON.parse((await voiceBuild('--json')).stdout || '{}')
      check('voice-build --revoice-words --max 3 --run: three words again in their carrier sentence, counted for the day', rw.exitCode === 0 && fe.st.tts.length - tsBefore === 3 && fe.st.tts.slice(tsBefore).every(c => c.timestamps && c.body.text.startsWith('Beseda je: ')) && after2.carrier?.old === words.length - 3 && after2.carrier.carrier === 3 && after2.carrier.today === 3, [rw.stdout + rw.stderr, after2.carrier])
    }
  }

  // --- a village in another language: ElevenLabs is told which (in-process against the fake) -------------------
  {
    const fe = fakeEleven()
    for (const language of ['it', 'de', 'en']) await new ElevenLabs({ key: 'test-key', base: fe.url, language }).synth('Ciao!', 'female')
    check('a village in Italian, German or English: each clip asks ElevenLabs for its language_code', JSON.stringify(fe.st.tts.map(c => c.body.language_code)) === '["it","de","en"]', fe.st.tts.map(c => c.body))
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
    check('a lone word in a narrator voice: its carrier sentence through /with-timestamps (eleven_v3, Slovenian, Matilda, mp3)', !!call?.timestamps && call.body.text === 'Beseda je: kosilo.' && call.body.model_id === 'eleven_v3' && call.body.language_code === 'sl' && call.query === '?output_format=mp3_44100_128' && call.voice === 'XrExE9yKIg1WjnnlVkGX' && fe.st.tts.length === 1, call)
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
    const rv = new VoiceStore({ appDir: dir, engines: [fresh], queueDelayMs: 0, revoiceDaily: 2, onUpdated: n => void updated.push(n) })
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
    const vs = new VoiceStore({ appDir: mkdtempSync(join(tmpdir(), 'lani-redo-')), engines: [el], queueDelayMs: 0, revoiceDaily: 4, onUpdated: n => void updated.push(n) })
    const d = new Date()
    const today = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    check('the lazy re-voice leaves a quarter of the day\'s cap (at most 10) for re-records on request', vs.lazyDaily === 3 && redoKept(40) === 10 && redoKept(100) === 10 && redoKept(2) === 0 && redoKept(0) === 0)
    const k0 = await vs.voice('kosilo', 'female', 'pack:test')
    const t0 = fe.st.tts.length
    const r1 = await vs.redo('Kosilo', 'female')
    const c1 = r1.status === 'done' ? r1.clip : undefined
    check('a re-record of a narrator word: made now in its carrier sentence, under a new name, the old file gone, the index on it, the app told, counted for the day',
      !!c1 && c1.method === 'carrier' && !!k0 && c1.file !== k0.file && !vs.path(k0.file) && !!vs.path(c1.file) && c1.source === 'pack:test' && vs.index().kosilo?.female === `/voice/file/${c1.file}` &&
        fe.st.tts.length === t0 + 1 && !!fe.st.tts.at(-1)?.timestamps && fe.st.tts.at(-1)?.body.text === 'Beseda je: kosilo.' && updated.join() === '1' && vs.revoicedToday() === 1 && vs.redosToday() === 1, [r1, updated])
    const r2 = await vs.redo('kosilo', 'female')
    check('once a clip a day: the second is "not_needed", with the take from before; nothing asked, not counted', r2.status === 'not_needed' && r2.clip.file === c1?.file && fe.st.tts.length === t0 + 1 && vs.revoicedToday() === 1, r2)
    const s0 = await vs.voice('Kosilo je na mizi.', 'female', 'test')
    const r3 = await vs.redo('Kosilo je na mizi.', 'female')
    check('a sentence: a plain re-take (no carrier sentence), under a new name', r3.status === 'done' && !r3.clip.method && r3.clip.file !== s0?.file && fe.st.tts.at(-1)?.body.text === 'Kosilo je na mizi.' && !fe.st.tts.at(-1)?.timestamps && vs.revoicedToday() === 2, r3)
    const g0 = await vs.voice('kosilo', 'grandma', 'test', { live: true })
    const r4 = await vs.redo('kosilo', 'grandma')
    check('a villager\'s voice: a plain re-take in it, under a new name', r4.status === 'done' && r4.clip.voice === 'grandma' && r4.clip.file !== g0?.file && fe.st.tts.at(-1)?.voice === cast().grandma.elevenlabs && !fe.st.tts.at(-1)?.timestamps && vs.revoicedToday() === 3, r4)
    // an old-style narrator word the app asks for: the lazy re-voice has used its share (3 of 4), the re-record still goes
    const oldFile = `${createHash('sha1').update('redo|old|potok').digest('hex')}.mp3`
    writeFileSync(join(vs.filesDir, oldFile), mp3('old:potok'))
    vs.database.query('INSERT INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, method, hits) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 0)').run('female:potok', 'potok', 'potok', 'female', 'elevenlabs', oldFile, 5, '2026-09-01T10:00:00.000Z', 'pack:old')
    const t1 = fe.st.tts.length
    vs.requested(oldFile)
    await Bun.sleep(100)
    const lazyWaits = fe.st.tts.length === t1 && vs.get('potok', 'female')?.file === oldFile
    const r5 = await vs.redo('potok', 'female')
    check('a re-record goes first: the lazy re-voice leaves the rest of the cap for it', lazyWaits && r5.status === 'done' && r5.clip.method === 'carrier' && !vs.path(oldFile) && vs.revoicedToday() === 4 && !vs.revoiceQueue().length, [lazyWaits, r5])
    const st0 = await vs.voice('Stol je lesen.', 'female', 'test')
    const t2 = fe.st.tts.length
    const r6 = await vs.redo('Stol je lesen.', 'female')
    check('the day\'s cap spent: "limit" (cap), nothing asked, the clip as it was', r6.status === 'limit' && r6.reason === 'cap' && r6.message.includes('4 of 4') && fe.st.tts.length === t2 && vs.get('stol je lesen', 'female')?.file === st0?.file, r6)
    vs.database.query('DELETE FROM revoices WHERE day = ?').run(today) // a new cap, as the next day
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
    const cs = vs.carrierStats()
    check('the carrier stats show today\'s re-records and the part of the cap kept for them', cs.redos === 4 && cs.kept === 1 && cs.daily === 4, cs)
    vs.close()
  }

  // --- a voice that hisses: the cast's `denoise` (in-process; voice-build --denoise on a copy) ------------------------
  {
    check(
      'the cast filters the voices that hiss (Stari Janez\'s and Micka\'s own, the girl, the young man, gruff, France\'s own), and nobody else\'s',
      ['@janez', '@micka', '@france', 'girl', 'young-man', 'gruff'].every(v => denoiseOf(v) === 'hiss') && !denoiseOf('grandpa') && !denoiseOf('female') && !denoiseOf('@luka'),
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

    // the real filter (ffmpeg): 1 s of hiss at −51 dBFS, then a tone in it, then hiss again
    const noisy = ffmpeg && Bun.spawnSync(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i', "aevalsrc='0.5*sin(2*PI*440*t)*between(t,1,1.5)+0.005*(2*random(0)-1)':s=44100:d=1.8", '-ac', '1', '-c:a', 'libmp3lame', '-b:a', '128k', '-f', 'mp3', 'pipe:1'])
    const pcm = (b: Uint8Array) => new Float32Array(Bun.spawnSync(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'mp3', '-i', 'pipe:0', '-ac', '1', '-ar', '44100', '-f', 'f32le', 'pipe:1'], { stdin: b }).stdout.buffer)
    const level = (x: Float32Array, from: number, to: number) => {
      let e = 0
      for (let k = Math.floor(from * 44100); k < Math.floor(to * 44100); k++) e += x[k] ** 2
      return 10 * Math.log10(e / ((to - from) * 44100) + 1e-12)
    }
    const onset = (x: Float32Array) => x.findIndex((v, i) => i > 22050 && Math.abs(v) > 0.25) / 44100
    let hissy = mp3('not audio')
    if (noisy && noisy.exitCode === 0) {
      hissy = new Uint8Array(noisy.stdout)
      const f = ffmpegDenoiser()!
      const out = await f(hissy, 'hiss')
      const [a, b] = [pcm(hissy), out ? pcm(out) : new Float32Array()]
      check('the filter (hiss): the hiss goes (over 20 dB), the tone stays (within 1 dB), nothing moves in time (the 25 ms of afftdn put back) and the length stays',
        !!out && isMp3(out) && level(a, 0.2, 0.9) - level(b, 0.2, 0.9) > 20 && Math.abs(level(a, 1.1, 1.4) - level(b, 1.1, 1.4)) < 1 && Math.abs(onset(a) - onset(b)) < 0.002 && Math.abs(a.length - b.length) < 0.01 * 44100,
        [level(a, 0.2, 0.9), level(b, 0.2, 0.9), level(a, 1.1, 1.4), level(b, 1.1, 1.4), onset(a), onset(b), a.length, b.length])
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
    ;[['Ha! In ti boš tudi, Jan.', '@janez', 'elevenlabs'], ['Lahko noč, fant.', '@janez', 'elevenlabs'], ['Pridi spet k lipi.', '@janez', 'elevenlabs'], ['Lahko noč, fant.', 'grandpa', 'elevenlabs'], ['Lahko noč, fant.', 'male', 'gepard']].forEach(([text, voice, engine], i) => {
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
        !dry.stdout.includes(oldFile['grandpa:Lahko noč, fant.']) && Object.values(oldFile).every(f => existsSync(join(vbData, 'app/voice/files', f))) && !cols().includes('denoise') && fe.st.tts.length === tts0, [dry.stdout, dry.stderr, dryJson])
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
      check('the filtered clip is audio, as long as before, with less hiss', isMp3(clip) && Math.abs(pcm(clip).length - pcm(hissy).length) < 0.01 * 44100 && level(pcm(hissy), 0.2, 0.9) - level(pcm(clip), 0.2, 0.9) > 20)
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
    const heard = (v: string) => gepardTempo({ LANI_VOICE_GEPARD_TEMPO: v }, (...a) => void said.push(a.join(' ')))
    check('the local voice\'s pace: LANI_VOICE_GEPARD_TEMPO a factor from 0.5 to 2, "off" or 0 for none, else 1.05 (a value out of range logged)',
      gepardTempo({}) === PACE.tempo && PACE.tempo === 1.05 && heard('1.2') === 1.2 && heard('1') === 1 && heard('off') === null && heard('0') === null && heard('3') === PACE.tempo && heard('fast') === PACE.tempo && said.length === 2 && !ffmpegPace('off'),
      said)
    check('…its chain: the silences shortened (before, the pauses, after), then atempo; at 1 none', paceChain(1.05).startsWith('silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.05:') && paceChain(1.05).endsWith(',areverse,atempo=1.05') && !paceChain(1).includes('atempo') && charsPerSecond(' Dober dan. ', 2) === 5, paceChain(1.05))
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
    check('a new clip is levelled before it is stored: its row records the level and the gain', zala?.level === LEVEL.target && (zala.gain ?? 0) < -5 && vs.get(normalizeText('Živjo, Jan!'), 'girl')?.level === LEVEL.target && near(zm), [zala, zm])
    check('…so it is not levelled twice: nothing waits, the batch does nothing', vs.levelQueue().length === 0 && (await vs.levelClips()).done === 0 && vs.levelStats().levelled === 1 && vs.levelStats().waiting === 0, vs.levelStats())
    const lvs = new VoiceStore({ appDir: tempDir('lani-level-gepard-'), engines: [stub('gepard', local)], queueDelayMs: 0 })
    const kruh = await lvs.voice('Kruh je še topel.', 'male', 'test')
    const kb = kruh ? new Uint8Array(readFileSync(join(lvs.filesDir, kruh.file))) : new Uint8Array()
    check('…a local worker\'s clip too, in its own encoding (and paced in the same encode)', kruh?.engine === 'gepard' && kruh.level === LEVEL.target && kruh.tempo === lvs.tempo && lvs.tempo !== null && near(await measure(kb)) && rateOf(kb) === 22050 && mp3Kbps(kb) === 64, kruh)
    lvs.close()

    // a voice that hisses: filtered and levelled in one encode (the level runs its filter first)
    const castDir = tempDir('lani-level-cast-')
    const castJson = JSON.parse(readFileSync(CAST_FILE, 'utf8'))
    castJson.denoise = { grandma: 'hiss' }
    writeFileSync(join(castDir, 'voice-cast.json'), JSON.stringify(castJson))
    process.env.LANI_VOICE_CAST = join(castDir, 'voice-cast.json')
    const hissy = gen("0.5*sin(2*PI*440*t)*between(t,1,1.5)+0.005*(2*random(0)-1)", 1.8)
    const calls: (string | undefined)[] = []
    const spy: Leveller = { target: lv.target, level: (b, pre) => (calls.push(pre), lv.level(b, pre)) }
    const hs = new VoiceStore({ appDir: tempDir('lani-level-hiss-'), engines: [stub('elevenlabs', hissy)], queueDelayMs: 0, level: spy })
    const gm = await hs.voice('Dober večer.', 'grandma', 'test')
    const [h0, h1] = [samples(hissy), gm ? samples(new Uint8Array(readFileSync(join(hs.filesDir, gm.file)))) : new Float32Array()]
    const rms = (x: Float32Array, from: number, to: number) => {
      let e = 0
      for (let k = Math.floor(from * 44100); k < Math.floor(to * 44100); k++) e += x[k] ** 2
      return 10 * Math.log10(e / ((to - from) * 44100) + 1e-12)
    }
    // the tone over the hiss, before and after: the hiss went down (the filter) more than the tone (the gain)
    const snr = (x: Float32Array) => rms(x, 1.1, 1.4) - rms(x, 0.2, 0.9)
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
      ['okno', 'female', 'elevenlabs', l!.bytes], // at the level already
      ['Bravo, fant.', '@janez', 'elevenlabs', hissy], // a voice that hisses: filtered in the same encode
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
      dry.exitCode === 0 && dj.target === LEVEL.target && dj.waiting === 5 && dj.measured === 5 && row('boy')?.gain < -5 && row('grandpa')?.gain > 5 && row('female')?.keep === true && row('@janez')?.denoise === 'hiss' && row('male')?.tempo === PACE.tempo && dj.voices?.['boy elevenlabs']?.lufs.median > -12 &&
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
    check('voice-build --level --run (--max 2, then the rest, then nothing): each clip levelled once, under a new name, the old file gone; the one at the level kept (its file, gain 0); Janez\'s filtered in the same encode, the local one paced',
      run1.exitCode === 0 && run1.stdout.includes('Levelled 2 clip(s)') && run2.exitCode === 0 && run2.stdout.includes('Levelled 3 clip(s)') && run3.stdout.includes('Levelled 0 clip(s)') &&
        Object.values(rows).every(c => c.level === LEVEL.target && c.hits === 2) &&
        ['boy', 'grandpa'].every(v => rows[v].file === processedFile(oldFile[v], { level: LEVEL.target }) && !existsSync(join(vbData, 'app/voice/files', oldFile[v]))) &&
        rows.male.tempo === PACE.tempo && rows.male.file === processedFile(oldFile.male, { level: LEVEL.target, tempo: PACE.tempo }) && !existsSync(join(vbData, 'app/voice/files', oldFile.male)) && rows.boy.tempo == null &&
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

    // --- the local voice's pace: its silences shortened, a slight speed-up, in the level's encode -------------------
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
    const factor = ps.tempo!
    const said = 'Dober dan. Kako ste?'
    const pc = await ps.voice(said, 'male', 'test')
    const pb = pc ? new Uint8Array(readFileSync(join(ps.filesDir, pc.file))) : new Uint8Array()
    const p1 = await pace(pb)
    check('a clip from the local worker is paced before it is stored: 0.05 s of silence before its sound, 0.1 s after it, the pause down to 0.4 s, sped up by the factor (1.05); recorded (tempo), levelled in the same encode, in its own encoding',
      pc?.engine === 'gepard' && factor === PACE.tempo && pc.tempo === factor && pc.level === LEVEL.target && !!p1 && p1.lead <= 0.08 && p1.tail <= 0.16 && Math.abs(p1.longest - PACE.pause / factor) < 0.06 && Math.abs(p1.sound - 1.6 / factor) < 0.1 &&
        Math.abs(p1.secs - (PACE.lead + 1.6 + PACE.pause + PACE.tail) / factor) < 0.12 && near(await measure(pb)) && rateOf(pb) === 22050 && mp3Kbps(pb) === 64,
      [pc, p0, p1])
    check('…so it is not paced twice: nothing waits, the batch does nothing', ps.tempoQueue().length === 0 && (await ps.tempoClips()).done === 0 && JSON.stringify(ps.tempoStats()) === JSON.stringify({ tempo: factor, paced: 1, waiting: 0, ffmpeg: true }), ps.tempoStats())
    const off = new VoiceStore({ appDir: tempDir('lani-pace-off-'), engines: [new Gepard({ url: fg.url })], queueDelayMs: 0, tempo: null })
    const oc = await off.voice(said, 'male', 'test')
    const po = oc ? await pace(new Uint8Array(readFileSync(join(off.filesDir, oc.file)))) : null
    check('…the pace off (LANI_VOICE_GEPARD_TEMPO=off): as the worker made it, only levelled, under another name; nothing waits for the pace',
      oc?.engine === 'gepard' && oc.tempo === null && oc.level === LEVEL.target && oc.file !== pc?.file && !!po && Math.abs(po.secs - 3.9) < 0.1 && off.tempoQueue().length === 0 && off.tempoStats().tempo === null, [oc, po])
    off.close()
    const pel = await new VoiceStore({ appDir: pdir, engines: [stub('elevenlabs', worker)], queueDelayMs: 0 }).voice('Dober večer.', 'female', 'test')
    check('…an ElevenLabs clip keeps its pace (only the local voice is paced)', pel?.engine === 'elevenlabs' && pel.tempo == null && pel.level === LEVEL.target && ps.tempoQueue().length === 0, pel)
    // a new factor: the clips paced at the old one are sped up by the difference, from their files as they are
    const faster = new VoiceStore({ appDir: pdir, engines: [], tempo: 1.2 })
    const waits = faster.tempoQueue().map(c => c.key)
    const fr = await faster.tempoClips()
    const fc = faster.get(normalizeText(said), 'male')
    const p2 = fc ? await pace(new Uint8Array(readFileSync(join(ps.filesDir, fc.file)))) : null
    check('a new factor (1.2): the clip paced at 1.05 waits, and is sped up by the difference from its file, under a new name, levelled again',
      waits.join() === `male:${normalizeText(said)}` && fr.done === 1 && fc?.tempo === 1.2 && fc.file === processedFile(pc!.file, { level: LEVEL.target, tempo: 1.2 }) && !existsSync(join(ps.filesDir, pc!.file)) &&
        !!p1 && !!p2 && Math.abs(p2.secs - (p1.secs * factor) / 1.2) < 0.1 && near(await measure(new Uint8Array(readFileSync(join(ps.filesDir, fc.file))))), [waits, fr, fc, p1, p2])
    faster.close()
    ps.close()

    // voice-build --tempo on a copy of the data: a store from before the pace (no tempo column), levelled, real MP3s
    const tData = tempDir('lani-pace-data-')
    mkdirSync(join(tData, 'app/voice/files'), { recursive: true })
    const tdb = new Database(join(tData, 'app/voice/voice.db'))
    tdb.exec('CREATE TABLE clips (key TEXT PRIMARY KEY, norm TEXT NOT NULL, text TEXT NOT NULL, voice TEXT NOT NULL, engine TEXT NOT NULL, file TEXT NOT NULL, chars INTEGER NOT NULL, created_at TEXT NOT NULL, source TEXT NOT NULL, method TEXT, hits INTEGER NOT NULL DEFAULT 0, denoise TEXT, level REAL, gain REAL)')
    const tFile: Record<string, string> = {}
    const levelled = (await lv.level(worker))!.bytes // as the store has them: at the level already
    const tclips: [string, string, string][] = [[said, 'male', 'gepard'], ['Hvala, dobro. In vi?', 'female', 'gepard'], ['Lepo vreme imamo danes.', 'female', 'elevenlabs']]
    tclips.forEach(([text, voice, engine], i) => {
      const file = `${createHash('sha1').update(`pace|${voice}|${text}`).digest('hex')}.mp3`
      tFile[`${voice}:${text}`] = file
      writeFileSync(join(tData, 'app/voice/files', file), levelled)
      tdb.query('INSERT INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, hits, level, gain) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)').run(`${voice}:${normalizeText(text)}`, normalizeText(text), text, voice, engine, file, text.length, `2026-10-0${i + 1}T10:00:00.000Z`, 'test', 3, LEVEL.target, 0)
    })
    tdb.close()
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
    const tdry = await vbt({}, '--tempo', '--data', tData)
    const tj = JSON.parse((await vbt({}, '--tempo', '--json', '--data', tData)).stdout || '{}')
    check('voice-build --tempo (dry run): the two local clips, each measured now and as it would be paced (shorter, more characters a second), ElevenLabs\' clips of the same voices beside them; nothing changed',
      tdry.exitCode === 0 && tj.tempo === PACE.tempo && tj.waiting === 2 && tj.chars === 40 && tj.measured === 2 && tj.clips?.map((c: any) => c.text).sort().join('|') === `${said}|Hvala, dobro. In vi?` &&
        tj.clips.every((c: any) => c.after.secs < c.now.secs - 1 && c.after.lead < 0.08) && tj.voices?.['male gepard']?.rate.after.median > tj.voices['male gepard'].rate.now.median * 1.4 && tj.elevenlabs?.n === 1 &&
        tdry.stdout.includes('male gepard') && tdry.stdout.includes('ElevenLabs, the same voices') && tdry.stdout.includes('Dry run') && tdry.stdout.includes('--tempo --run') && !tcols().includes('tempo') && Object.values(tFile).every(f => existsSync(join(tData, 'app/voice/files', f))),
      [tdry.stdout, tdry.stderr, tj])
    check('voice-build --tempo: no --max-chars (no characters are spent), not with another mode; with the pace off, nothing to do',
      (await vbt({}, '--tempo', '--max-chars', '10', '--data', tData)).exitCode === 2 && (await vbt({}, '--tempo', '--level', '--data', tData)).exitCode === 2 && (await vbt({ LANI_VOICE_GEPARD_TEMPO: 'off' }, '--tempo', '--run', '--data', tData)).stdout.includes('is off'))
    // the bridge, running on the same data: it tells the app when another process changed the clips
    const ttold: number[] = []
    const tbridge = new VoiceStore({ appDir: join(tData, 'app'), engines: [], queueDelayMs: 0, onUpdated: n => void ttold.push(n) })
    tbridge.checkExternal()
    const t1 = await vbt({}, '--tempo', '--run', '--max', '1', '--data', tData)
    const t2 = await vbt({}, '--tempo', '--run', '--data', tData)
    const t3 = await vbt({}, '--tempo', '--run', '--data', tData)
    const td = new Database(join(tData, 'app/voice/voice.db'), { readonly: true })
    const trows = Object.fromEntries((td.query('SELECT * FROM clips').all() as Clip[]).map(c => [`${c.voice}:${c.text}`, c]))
    td.close()
    const tlocal = [`male:${said}`, 'female:Hvala, dobro. In vi?']
    const tp = await Promise.all(tlocal.map(k => pace(new Uint8Array(readFileSync(join(tData, 'app/voice/files', trows[k].file))))))
    const elKey = 'female:Lepo vreme imamo danes.'
    check('voice-build --tempo --run (--max 1, then the rest, then nothing): each local clip paced once and levelled in the same encode, under a new name, the old file gone; the ElevenLabs clip as it was',
      t1.exitCode === 0 && t1.stdout.includes('Paced 1 clip(s)') && t2.exitCode === 0 && t2.stdout.includes('Paced 1 clip(s)') && t3.stdout.includes('Paced 0 clip(s)') && tcols().includes('tempo') &&
        tlocal.every(k => trows[k].tempo === PACE.tempo && trows[k].level === LEVEL.target && trows[k].hits === 3 && trows[k].file === processedFile(tFile[k], { level: LEVEL.target, tempo: PACE.tempo }) && !existsSync(join(tData, 'app/voice/files', tFile[k]))) &&
        tp.every(p => !!p && p.secs < 2.3 && p.lead <= 0.08 && p.longest < 0.45) && trows[elKey]?.tempo == null && trows[elKey].file === tFile[elKey] && existsSync(join(tData, 'app/voice/files', tFile[elKey])),
      [t1.stdout + t1.stderr, t2.stdout, t3.stdout, trows, tp])
    const tn = tbridge.checkExternal()
    check('the bridge sees the clips voice-build paced and tells the app once (voice_updated)', tn === 2 && ttold.join() === '2' && tbridge.checkExternal() === 0, [tn, ttold])
    tbridge.close()
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
    const said = await (await fetch(`${base}/voice/say`, { method: 'POST', headers: auth, body: JSON.stringify({ text: 'Pridi k mizi, fant.', voice: '@micka' }) })).json()
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
    const calls = bridgeEleven.st.tts.length
    const s1 = await say('Dobro jutro, Micka!')
    const s1Body = await s1.json()
    check('POST /voice/say synthesizes with ElevenLabs', s1.ok && s1Body.engine === 'elevenlabs' && /^\/voice\/file\/[a-f0-9]{40}\.mp3$/.test(s1Body.url) && bridgeEleven.st.tts.length === calls + 1, s1Body)
    const s2 = await (await say('dobro jutro micka')).json()
    check('POST /voice/say serves the cached clip', s2.url === s1Body.url && bridgeEleven.st.tts.length === calls + 1)
    const s3 = await (await say('Dobro jutro, Micka!', 'male')).json()
    check('male voice is its own clip (Daniel)', s3.url !== s1Body.url && s3.voice === 'male' && bridgeEleven.st.tts.at(-1)?.voice === 'onwK4e9ZLuTAKqWW03F9', s3)
    const file = await fetch(`${base}${s1Body.url}`, { headers: auth })
    const bytes = new TextDecoder().decode((await file.arrayBuffer()).slice(6))
    check('GET /voice/file serves audio/mpeg', file.ok && file.headers.get('content-type') === 'audio/mpeg' && bytes === 'el:XrExE9yKIg1WjnnlVkGX:Dobro jutro, Micka!', bytes)
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

    // A narrator word from before the carrier sentence in the bridge's store: the app downloads it, gets it as it is,
    // and soon it is voiced again in its carrier sentence under a new name, and the app is told.
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
      let url = `/voice/file/${oldFile}`
      for (let i = 0; i < 100 && url.endsWith(oldFile); i++) {
        await Bun.sleep(50)
        url = (await (await fetch(`${base}/voice/index`, { headers: auth })).json()).kolovrat?.female ?? url
      }
      const fresh = await fetch(`${base}${url}`, { headers: auth })
      const freshBytes = new Uint8Array(await fresh.arrayBuffer())
      check('…then voiced again in its carrier sentence, under a new name (the old one gone), and the app told (voice_updated)', !url.endsWith(oldFile) && fresh.ok && mp3Duration(freshBytes) > 0.3 && bridgeEleven.st.tts.some(c => c.timestamps && c.body.text === 'Beseda je: kolovrat.') && (await fetch(`${base}/voice/file/${oldFile}`, { headers: auth })).status === 404 && (await updates()) > u0, url)
    }

    // Jan's re-record (a long press on 🔊): POST /voice/redo makes the clip again now, under a new name.
    {
      const redo = (body: unknown, headers: Record<string, string> = auth) => fetch(`${base}/voice/redo`, { method: 'POST', headers, body: JSON.stringify(body) })
      const updates = async () => ((await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as any[]).filter(b => b.event.type === 'voice_updated').length
      const u0 = await updates()
      const n0 = bridgeEleven.st.tts.length
      const r = await redo({ text: 'Dobro jutro, Micka!', voice: 'female', client_id: 'redo-smoke-0001' })
      const rb = await r.json()
      const idx = await (await fetch(`${base}/voice/index`, { headers: auth })).json()
      check('POST /voice/redo: made again now (a plain re-take of a sentence), a new file, the old one gone, the index on it, the app told (voice_updated)',
        r.ok && rb.status === 'done' && rb.url !== s1Body.url && rb.key === 'female:dobro jutro micka' && rb.norm === 'dobro jutro micka' && rb.voice === 'female' && rb.method === null && rb.daily === 40 && rb.today >= 1 &&
          bridgeEleven.st.tts.length === n0 + 1 && bridgeEleven.st.tts.at(-1)?.body.text === 'Dobro jutro, Micka!' && (await fetch(`${base}${s1Body.url}`, { headers: auth })).status === 404 &&
          (await fetch(`${base}${rb.url}`, { headers: auth })).ok && idx['dobro jutro micka']?.female === rb.url && (await updates()) > u0, rb)
      const replay = await redo({ text: 'Dobro jutro, Micka!', voice: 'female', client_id: 'redo-smoke-0001' })
      check('POST /voice/redo: a retry with the same client_id gets the same answer, no second take', replay.headers.get('x-lani-replay') === '1' && (await replay.json()).url === rb.url && bridgeEleven.st.tts.length === n0 + 1)
      const again = await redo({ text: 'dobro jutro micka', voice: 'female' })
      const ab = await again.json()
      check('POST /voice/redo: once a clip a day, then "not_needed" with the take from before', again.ok && ab.status === 'not_needed' && ab.url === rb.url && !!ab.message && bridgeEleven.st.tts.length === n0 + 1, ab)
      const st = await (await fetch(`${base}/voice/status`, { headers: auth })).json()
      check('GET /voice/status counts the re-records of the day, and the part of the cap kept for them', st.carrier?.redos === 1 && st.carrier.kept === 10, st.carrier)
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
      const fresh = 'Prav! Druga je prava.'
      const body = { texts: [{ text: 'Dobro jutro, Micka!' }, { text: fresh, voice: 'male' }, { text: 'Ena.', voice: 'nobody' }] }
      const r = await prepare(body)
      const p = await r.json()
      check('POST /voice/prepare: a clip the store has as it is, a new one voiced (Daniel), a voice not in the cast none; the day\'s count and cap',
        r.ok && p.clips?.length === 3 && !!have && p.clips[0].url === have && p.clips[0].voice === 'female' &&
          p.clips[1].text === fresh && p.clips[1].voice === 'male' && /^\/voice\/file\/[a-f0-9]{40}\.mp3$/.test(p.clips[1].url) && p.clips[1].engine === 'elevenlabs' &&
          p.clips[2].voice === 'nobody' && p.clips[2].url === null && p.daily === 6_000 && p.today === fresh.length &&
          bridgeEleven.st.tts.length === n0 + 1 && bridgeEleven.st.tts.at(-1)?.body.text === fresh && bridgeEleven.st.tts.at(-1)?.voice === 'onwK4e9ZLuTAKqWW03F9', p)
      const again = await (await prepare(body)).json()
      check('POST /voice/prepare again: the same clips, nothing voiced, the day\'s count as it was', JSON.stringify(again.clips) === JSON.stringify(p.clips) && again.today === p.today && bridgeEleven.st.tts.length === n0 + 1, again)
    }

    bridgeEleven.st.fail = 500
    const s4 = await (await say('Kje je pošta?')).json()
    check('ElevenLabs down: the local worker answers', s4.engine === 'gepard', s4)
    if (ffmpeg) {
      bridgeGepard.st.audio = workerClip()
      const sp = await (await say('Kje je trgovina? Tam, za vogalom.')).json()
      const spBytes = new Uint8Array(await (await fetch(`${base}${sp.url}`, { headers: auth })).arrayBuffer())
      const stp = (await (await fetch(`${base}/voice/status`, { headers: auth })).json()).tempo
      check('…and the bridge paces its clip before it stores it (3.9 s as the worker made it, about 2 s stored); GET /voice/status counts it (tempo)',
        sp.engine === 'gepard' && mp3Duration(spBytes) < 2.3 && stp?.tempo === PACE.tempo && stp.paced >= 1 && typeof stp.waiting === 'number' && stp.ffmpeg === true, [sp, mp3Duration(spBytes), stp])
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
      { id: 'micka', name: 'Babica Micka', voice: 'female' as const, speaker: 'grandma', role: 'Babica · Grandmother', personality: 'Warm grandmother, always cooking. Forgets words, never people. Worries.', order: 1, lines: { greet: [{ sl: 'Dober dan. Si lačen?' }, { sl: 'O, pridi, pridi. Sedi.' }, { sl: 'Jan, fant moj! Pridi, juha je vroča.' }], idle: [{ sl: 'Kje si bil tako dolgo? Sedi, sedi, jej.' }, { sl: 'Rada mislim na {memory}.' }] } },
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
    const line = await vs.say('Jej, jej, fant.', '@micka')
    check('voice store: a line of hers is voiced in her own voice, under her own key', line?.voice === '@micka' && line.engine === 'elevenlabs' && fe.st.tts.at(-1)?.voice === 'own1', line)

    // slots: 4, one kept spare, Micka has one: Luka moves in and gets the second; then the cast waiting keeps the rest
    prof.upsert([{ id: 'micka', name: 'Babica Micka', gender: 'female', speaker: 'grandma', cast: true }, { id: 'luka', name: 'Pastir Luka', gender: 'male', speaker: 'young-man', cast: true }, { id: 'n-ana-furlan', name: 'Ana Furlan', gender: 'female', speaker: 'young-woman' }])
    const why = await prof.designNext()
    check('profiles: Luka gets his; then the one spare slot is kept for a redesign', prof.get('luka')?.status === 'own' && fe.st.created.length === 2 && fe.st.voiceSlotsUsed === 2 && typeof why === 'string' && why.includes('spare') === false && why.length > 0, [why, fe.st.voiceSlotsUsed, SPARE_SLOTS])
    check('profiles: whoever the app no longer lists is not present', prof.get('ancka')?.present === 0 && prof.get('micka')?.present === 1)

    // a redesign: a new voice, the old one and its clips go
    const re = await prof.redesign('micka', 'An old woman in her eighties with a very soft, trembling voice. A native Slovene speaker.')
    check('redesign: a new voice from the new description, the old one deleted, her old clips dropped', 'person' in re && re.elevenlabs === 'own3' && fe.st.deleted.includes('own1') && changed.includes('@micka') && !vs.get('jej jej fant', '@micka') && fe.st.designs.at(-1)?.voice_description.includes('eighties'), [re, fe.st.deleted, changed])
    check('redesign: an unknown person is an error', 'error' in (await prof.redesign('nobody')))
    const like = await prof.redesign('luka', undefined, 'grandpa')
    check('redesign like another voice: its sample recording lends the timbre (reference audio), the prompt the rest', 'person' in like && like.status === 'own' && !!fe.st.designs.at(-1)?.reference_audio_base64 && fe.st.designs.at(-1)?.prompt_strength === 0.4, fe.st.designs.at(-1) && { ...fe.st.designs.at(-1), reference_audio_base64: '…' })
    check('redesign like a voice that does not exist is an error', 'error' in (await prof.redesign('luka', undefined, 'nobody-here')))
    heardServer.stop(true)
  }

}
