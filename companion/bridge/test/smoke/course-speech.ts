// Smoke checks: the school's course in the teacher's voice (src/course-speech.ts, companion/COURSE.md "Read aloud"), and
// the characters' local clips made in their ElevenLabs voice as the app asks for them (README "Voice"):
// - the port reads the shared cases of test/fixtures/intro-speech.json as the app does (IntroSpeechParityTest reads them too);
// - who teaches (the school's person: Učiteljica Mojca, her own voice once it is ready), what the corpus voices of the
//   course and of the cast's arrivals, in her voice;
// - companion/bin/voice-build --only, --explanations and --base on a store of its own with the fake engines: the plan's
//   course row, the explanations counted apart, ElevenLabs within the budget and never into the reserve;
// - a character's Gepard clip asked for (/voice/say, /voice/prepare, a download): made in the speaker's ElevenLabs voice,
//   once, within the day's cap and above the reserve, the app told (voice_updated).
import { Database } from 'bun:sqlite'
import { createHash } from 'node:crypto'
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { addressee, renderDeep } from '../../src/addressee'
import { cast, elevenlabsOf } from '../../src/cast'
import { CourseStore, courseDir } from '../../src/course'
import * as S from '../../src/course-speech'
import { normalizeText } from '../../src/family'
import { corpus, ElevenLabs, EXPLANATIONS, Gepard, PRIORITY_LABELS, VoiceStore } from '../../src/voice'
import { VillagerStore } from '../../src/villagers'
import { auth, base, bridgeEleven, check, courseRoot, culturesDir, dataDir, fakeEleven, fakeGepard, mp3, tempDir } from './harness'

const repo = resolve(import.meta.dir, '../../../..')
const mark = (m: S.Mark) => (m.kind === 'part' ? `part:${m.line}:${m.segments[0]}-${m.segments[1]}` : m.kind === 'example' ? `example:${m.index}` : m.kind)
const scriptOut = (s: S.Script) => ({
  explanation: s.explanation ? { plain: s.explanation.plain, lang: s.explanation.lang, key: S.key(s.explanation.lang, s.explanation.plain), runs: s.explanation.runs } : null,
  lines: s.lines.map(l => ({ text: l.text, slovene: l.slovene, mark: mark(l.mark) })),
})
const until = async (f: () => boolean | Promise<boolean>, ms = 4_000) => {
  const end = Date.now() + ms
  while (!(await f()) && Date.now() < end) await Bun.sleep(25)
  return f()
}

export default async function courseSpeech() {
  // --- the port reads what the app reads (the shared fixture) ---------------------------------------------------------
  {
    const fx = JSON.parse(readFileSync(resolve(import.meta.dir, '../fixtures/intro-speech.json'), 'utf8'))
    const looks = fx.looks.filter((c: any) => S.spokenLooks(c.text, c.target, c.base) !== c.looks)
    check(`intro speech: Spoken.looks as the app reads ${fx.looks.length} texts (the shared fixture)`, fx.looks.length >= 10 && looks.length === 0, looks)
    for (const c of fx.intros) {
      const index: S.ClipIndex = {}
      c.clips.forEach(([t, v]: [string, string], i: number) => ((index[normalizeText(t)] ??= {})[v] = `/voice/file/${i}.mp3`))
      const isSlovene = S.looksLearned(c.language, c.base, t => S.indexHas(index, t))
      const say = c.summary ? S.summarySay(c.summary.rule, c.summary.goal, c.base) : undefined
      // the unit's words (its `words`), which the intro's reading counts as the language learned too
      const words: string[] = c.words ?? []
      const got = {
        vocabulary: [...S.vocabulary(c.intro, c.language, c.title, isSlovene, words)].sort(),
        steps: c.intro.steps.map((st: S.IntroStep, i: number) => ({ ...scriptOut(S.introStep(`x/${i}`, c.intro, i, c.language, c.base, c.title, isSlovene, words)), answer: S.answer(st.ask) ?? null })),
        summary: say === undefined ? null : { say, ...scriptOut(S.summary('x/summary', say, c.base, c.intro, c.language, c.title, isSlovene, words)) },
      }
      const diff = got.steps.flatMap((s: any, i: number) => (JSON.stringify(s) === JSON.stringify(c.expected.steps[i]) ? [] : [{ step: i, got: s, want: c.expected.steps[i] }]))
      check(`intro speech, as the app reads it: ${c.name}`, JSON.stringify(got) === JSON.stringify(c.expected), diff.length ? diff : [got.vocabulary, got.summary])
    }
    // the app's Clips.has and Voice.has: a clip of the text in any voice, or of each phrase in a narrator's
    const idx: S.ClipIndex = { micka: { grandma: '/a' }, 'kako ste': { female: '/b' }, 'kako si': { female: '/c' }, 'dober dan': { boy: '/d' }, 'dobro jutro': { female: '/e' } }
    check('intro speech: a clip in any voice makes a text the language learned, phrases only in a narrator\'s (the app\'s Clips.has); a family recording the same (Voice.has)',
      S.indexHas(idx, 'Micka!') && S.indexHas(idx, 'Kako ste? / Kako si?') && !S.indexHas(idx, 'Dober dan / Dobro jutro') && S.indexHas(idx, 'Dober dan') && !S.indexHas(idx, 'Mario') &&
        S.familyHas({ 'kako ste': [{}], 'kako si': [{}] }, 'Kako ste? / Kako si?') && !S.familyHas({ 'kako ste': [] }, 'Kako ste?'))
  }

  // --- who teaches, and what the corpus voices of the course in her voice ----------------------------------------------
  const villagers = new VillagerStore(join(culturesDir, 'primorska/villagers'), tempDir('lani-cs-villagers-')).all()
  const jan = addressee({ name: 'Jan', gender: 'male' })
  const units = renderDeep(new CourseStore(courseDir(join(repo, 'companion/course'), 'sl'), 'sl', tempDir('lani-cs-course-')).served()!.units, jan)
  {
    const t = S.schoolTeacher(villagers)
    const extra = [...villagers, { id: 'nova', voice: 'female' as const, speaker: 'young-woman', home: ['school' as const], since: 'zaselek' as const, order: 50, lines: villagers[0].lines }]
    const first = [{ ...extra.at(-1)!, order: 1 }, ...villagers]
    check('the school\'s person (the app\'s Residents.brings(SCHOOL)): Učiteljica Mojca in Primorska; her speaker the teacher, her own voice once it is ready ("@mojca"); the first by order of two at the school; the female narrator without one',
      t?.id === 'mojca' && S.teacherVoice(villagers, v => v.speaker ?? v.voice) === 'teacher' && S.teacherVoice(villagers, v => v.speaker ?? v.voice, id => id === 'mojca') === '@mojca' &&
        S.schoolTeacher(extra)?.id === 'mojca' && S.schoolTeacher(first)?.id === 'nova' && S.teacherVoice(villagers.filter(v => v.id !== 'mojca'), v => v.speaker ?? v.voice) === 'female' &&
        S.schoolTeacher([{ ...t!, since: undefined }]) === undefined && S.schoolTeacher([{ ...t!, extra: true }]) === undefined)
    const ct = S.courseTexts({ units, bases: ['en'] }, 'sl')
    const has = (x: string) => ct.lines.some(l => l.text === x)
    const steps = units.reduce((n, u) => n + u.intro.steps.length, 0)
    check('the course as the app reads it in her voice: every unit\'s examples, the quick questions (a gap a pause; a sentence with a gap, a word named in »quotes« and a label\'s first half in her voice), the answers, the Slovene of the explanations; never their English',
      units.length >= 10 && has('Jaz sem Jan.') && has('Luka … na pašniku.') && has('Luka je na pašniku.') && has('Micka in France … na njivi.') && has('Kakšnega spola?') && has('»smreka«') && has('jaz sem') &&
        !has('Which gender?') && !ct.lines.some(l => l.text.includes('{') || /\bthe\b/.test(l.text)) && ct.lines.every(l => l.source.startsWith('course:') && !l.lang),
      ct.lines.slice(0, 12))
    check('…and the explanations apart: one a step and one a summary, in each base asked (en; en and de twice as many), keyed as the app keys them',
      ct.explanations.length > steps && ct.explanations.every(e => e.lang === 'en') && S.courseTexts({ units, bases: ['en', 'de'] }, 'sl').explanations.length === 2 * ct.explanations.length &&
        S.key('en', ct.explanations[0].text).startsWith('en|biti to be'), ct.explanations.length)
    const items = corpus({ packs: [], modules: [], scenarios: [], villagers, course: { units, bases: ['en'] }, learner: jan, language: 'sl' })
    const course = items.filter(i => i.source.startsWith('course:'))
    const lines = course.filter(i => !i.lang)
    const explained = course.filter(i => i.lang)
    check('corpus: the course in the teacher\'s voice after the scenarios ("course"), its explanations last ("explanations", with their language), each text once',
      lines.length > 300 && lines.every(i => i.voice === 'teacher' && i.priority === PRIORITY_LABELS.indexOf('course')) && PRIORITY_LABELS.indexOf('course') === PRIORITY_LABELS.indexOf('scenarios') + 1 &&
        explained.length === ct.explanations.length && explained.every(i => i.voice === 'teacher' && i.lang === 'en' && i.priority === EXPLANATIONS) && EXPLANATIONS === PRIORITY_LABELS.length - 1 &&
        new Set(course.map(i => `${i.voice}|${i.lang ?? ''}|${normalizeText(i.text)}`)).size === course.length && items.at(-1)?.lang === 'en',
      [lines.length, explained.length])
    const own = corpus({ packs: [], modules: [], scenarios: [], villagers, course: { units: units.slice(0, 1), bases: ['en'] }, ownVoice: id => id === 'mojca', learner: jan, language: 'sl' })
    check('…in her own voice once it is ready ("@mojca"), as the app asks for it', own.filter(i => i.source.startsWith('course:')).every(i => i.voice === '@mojca'))
    const arrivals = JSON.parse(readFileSync(join(culturesDir, 'primorska/arrivals.json'), 'utf8')).cast
    const met = corpus({ packs: [], modules: [], scenarios: [], villagers, arrivals: Object.entries(arrivals).map(([id, a]: [string, any]) => ({ id, levels: a.levels })), learner: jan, language: 'sl' })
    const hers = met.filter(i => i.source === 'arrival:mojca')
    check('corpus: the cast\'s arrivals as a scene\'s dialog: Mojca\'s lines in the teacher\'s voice, the learner\'s choices in the default one, rendered for the learner, with the scenes',
      hers.some(i => i.voice === 'teacher') && hers.some(i => i.voice === 'female') && met.filter(i => i.source.startsWith('arrival:')).every(i => i.priority === PRIORITY_LABELS.indexOf('scenes') && !i.text.includes('{')) &&
        met.some(i => i.source === 'arrival:micka' && i.voice === 'grandma'), hers.slice(0, 4))
  }

  // --- companion/bin/voice-build --only, --explanations, --base on a store of its own ---------------------------------
  {
    const fe = fakeEleven()
    const fg = fakeGepard()
    // the village's culture pack with Mojca and Micka only, and the pack's arrivals
    const cultures = tempDir('lani-cs-cultures-')
    mkdirSync(join(cultures, 'primorska/villagers'), { recursive: true })
    for (const f of ['culture.json', 'arrivals.json']) cpSync(join(culturesDir, 'primorska', f), join(cultures, 'primorska', f))
    for (const v of ['mojca', 'micka']) cpSync(join(culturesDir, `primorska/villagers/${v}.json`), join(cultures, `primorska/villagers/${v}.json`))
    const data = tempDir('lani-cs-data-')
    cpSync(join(dataDir, 'learner-profile.json'), join(data, 'learner-profile.json'))
    const empty = (n: string) => tempDir(`lani-cs-${n}-`)
    const env = {
      ...process.env, LANI_CULTURES_DIR: cultures, LANI_DATA_DIR: data, LANI_PACKS_DIR: empty('packs'), LANI_MODULES_DIR: empty('modules'), LANI_SCENARIOS_DIR: empty('scenarios'),
      LANI_SCENES_DIR: empty('scenes'), LANI_DRILLS_DIR: empty('drills'), LANI_GRAMMAR_DIR: empty('grammar'), LANI_COURSE_DIR: courseRoot, LANI_KEYS_FILE: '/nonexistent',
      ELEVENLABS_API_KEY: 'test-key', ELEVENLABS_BASE_URL: fe.url, LANI_GEPARD_URL: fg.url,
    } as Record<string, string>
    delete env.LANI_VILLAGERS_DIR
    const voiceBuild = async (...args: string[]) => {
      const p = Bun.spawn(['bun', resolve(import.meta.dir, '../../../bin/voice-build'), ...args], { env, stdout: 'pipe', stderr: 'pipe' })
      const [stdout, stderr, exitCode] = await Promise.all([new Response(p.stdout).text(), new Response(p.stderr).text(), p.exited])
      return { stdout, stderr, exitCode }
    }
    fe.st.used = fe.st.limit - 5_000 - 100_000 // plenty above the reserve
    const all = JSON.parse((await voiceBuild('--json')).stdout || '{}')
    const dry = await voiceBuild('--json', '--only', 'teacher')
    const plan = JSON.parse(dry.stdout || '{}')
    const row = (p: any, l: string) => p.rows?.find((r: any) => r.label === l)
    check('voice-build --only teacher (dry run): only her texts (her villager lines, her arrival, the course), the course a row of the plan, the explanations counted apart (2 steps and the summary, in the learner\'s English), what they would cost; nothing sent, nothing written',
      dry.exitCode === 0 && JSON.stringify(plan.only) === '["teacher"]' && Object.keys(plan.by_voice).join() === 'teacher' && row(plan, 'course')?.texts > 0 && row(plan, 'villagers')?.texts > 0 && row(plan, 'scenes')?.texts > 0 &&
        plan.texts < all.texts && plan.explanations?.included === false && plan.explanations.texts === 3 && plan.explanations.bases.join() === 'en' && plan.explanations.missing_chars > 0 && plan.explanations.credits === Math.round(plan.explanations.missing_chars / 2) &&
        row(plan, 'explanations')?.texts === 0 && plan.would.elevenlabs.texts === plan.missing && fe.st.tts.length === 0 && !existsSync(join(data, 'app/voice/voice.db')),
      [dry.stderr.slice(-300), plan.rows?.filter((r: any) => r.texts), plan.explanations])
    const human = await voiceBuild('--only', 'teacher')
    check('…its table has the course row, not the explanations\' one; the explanations on a line of their own; how to run it, with --only',
      human.exitCode === 0 && /\n {2}course +\d/.test(human.stdout) && !/\n {2}explanations /.test(human.stdout) && /Course explanations \(en\), not in this plan: 3 /.test(human.stdout) &&
        human.stdout.includes('--explanations makes them now') && human.stdout.includes('Generate with: companion/bin/voice-build --run --only teacher'), human.stdout)
    const withX = JSON.parse((await voiceBuild('--json', '--only', 'teacher', '--explanations')).stdout || '{}')
    const de = JSON.parse((await voiceBuild('--json', '--only', 'teacher', '--explanations', '--base', 'en,de')).stdout || '{}')
    check('voice-build --explanations: the explanations in the plan (their row, ElevenLabs only); --base en,de: in both languages; the lines the same',
      withX.explanations?.included === true && row(withX, 'explanations')?.texts === 3 && withX.texts === plan.texts + 3 && de.explanations.texts === 6 && de.explanations.bases.join() === 'en,de' && row(de, 'course')?.texts >= row(plan, 'course')?.texts,
      [withX.explanations, de.explanations])
    const bad = await Promise.all([['--only', 'nobody'], ['--only'], ['--only', 'teacher', '--narrators'], ['--explanations', '--level'], ['--base', 'sl'], ['--base', 'english'], ['--only', '@mojca']].map(a => voiceBuild(...a)))
    check('voice-build refuses --only of a voice not in the cast or none, --only/--explanations with another mode, --base of the village\'s language or not a code, --only someone without a voice of their own',
      bad.every(b => b.exitCode === 2) && bad[0].stderr.includes('not in the voice cast: nobody') && bad[6].stderr.includes('no voice of their own'), bad.map(b => b.stderr.split('\n')[0]))
    // the budget: 60 characters above the reserve; the run makes what fits in her ElevenLabs voice and stops at the reserve
    fe.st.used = fe.st.limit - 5_000 - 60
    const tight = JSON.parse((await voiceBuild('--json', '--only', 'teacher')).stdout || '{}')
    const n0 = fe.st.tts.length
    const ran = await voiceBuild('--run', '--only', 'teacher')
    const sent = fe.st.tts.slice(n0)
    const chars = sent.reduce((n, t) => n + t.body.text.length, 0)
    const after = JSON.parse((await voiceBuild('--json', '--only', 'teacher')).stdout || '{}')
    check('voice-build --only teacher --run within a budget of 60 characters: her texts only, in her ElevenLabs voice, at most the budget (never into the reserve); the rest by the local worker in her voice where no narrator clip says it, else later',
      ran.exitCode === 0 && tight.would.elevenlabs.chars <= 60 && sent.length === tight.would.elevenlabs.texts && sent.length > 0 && chars <= 60 && sent.every(t => t.voice === elevenlabsOf('teacher')) &&
        fe.st.used <= fe.st.limit - 5_000 && fg.st.calls.every(c => c.speaker === cast().teacher.gepard) && after.cached === tight.would.elevenlabs.texts + tight.would.gepard.texts && !(ran.stdout + ran.stderr).includes('test-key'),
      [tight.would, chars, fg.st.calls.length, after.cached])
  }

  // --- a character's local clip, in the speaker's ElevenLabs voice as the app asks for it -----------------------------
  {
    const fe = fakeEleven()
    const fg = fakeGepard()
    const el = new ElevenLabs({ key: 'test-key', base: fe.url })
    const updated: number[] = []
    const dir = tempDir('lani-cs-local-')
    const vs = new VoiceStore({ appDir: dir, engines: [el, new Gepard({ url: fg.url })], queueDelayMs: 0, level: null, denoise: null, revoiceDaily: 2, onUpdated: n => void updated.push(n) })
    const local = async (text: string, voice = 'teacher') => (await vs.voice(text, voice, 'scene:sola', { engines: ['gepard'] }))!
    const a = await local('Dober dan, otroci.')
    const b = await local('Danes beremo.')
    const c = await local('Kdo ve odgovor?')
    const word = await local('kruh', 'female')
    const t0 = fe.st.tts.length
    check('a character\'s Gepard clip (made while ElevenLabs couldn\'t) waits for the speaker\'s ElevenLabs voice: queued once the app asks for it; never a narrator\'s (their local clip is the fallback by design), nor Drama 3\'s',
      a.engine === 'gepard' && vs.wantsElevenLabsVoice(a) && !vs.wantsElevenLabsVoice(word) && vs.localUpgradeQueue().length === 0 && vs.localUpgradeQueue(false).length === 3 &&
        !vs.wantsElevenLabsVoice({ ...a, engine: 'drama3' }) && vs.localStats().clips === 3 && vs.localStats().asked === 0 && vs.localStats().credits === Math.round(vs.localStats().chars / 2))
    const said = await vs.say('Dober dan, otroci.', 'teacher')
    check('…asked for (/voice/say): made in her ElevenLabs voice now, served instead of the local clip; under a new name, the local file gone, its hits kept, counted for the day, the app told',
      said?.engine === 'elevenlabs' && said.voice === 'teacher' && fe.st.tts.length === t0 + 1 && fe.st.tts.at(-1)?.voice === elevenlabsOf('teacher') && fe.st.tts.at(-1)?.body.text === 'Dober dan, otroci.' &&
        said.file !== a.file && !existsSync(join(vs.filesDir, a.file)) && (vs.get(a.norm, 'teacher')?.hits ?? 0) >= 1 && vs.lazyToday() === 1 && updated.join() === '1', [said, updated])
    check('…asked again: that clip, nothing made again', (await vs.say('Dober dan, otroci.', 'teacher'))?.file === said?.file && fe.st.tts.length === t0 + 1)
    // a download of the local file: served as it is, made soon (the day's cap: 2)
    vs.requested(b.file)
    await until(() => vs.get(b.norm, 'teacher')?.engine === 'elevenlabs')
    check('…a download of its file (the day\'s clips, a day ahead): the local clip served as it is, the ElevenLabs one made soon, the app told; the day\'s cap reached',
      vs.get(b.norm, 'teacher')?.engine === 'elevenlabs' && fe.st.tts.length === t0 + 2 && vs.lazyToday() === 2 && updated.length === 2, [vs.get(b.norm, 'teacher'), updated])
    const capped = await vs.say('Kdo ve odgovor?', 'teacher')
    vs.requested(c.file)
    await Bun.sleep(150)
    check('…over the day\'s cap: the local clip served, nothing sent, it waits for tomorrow (asked)',
      capped?.file === c.file && capped.engine === 'gepard' && fe.st.tts.length === t0 + 2 && vs.localUpgradeQueue().map(x => x.key).join() === c.key && vs.localStats().asked === 1)
    vs.database.query('DELETE FROM revoices').run() // tomorrow
    fe.st.used = fe.st.limit - 5_000 // at the reserve
    const reserve = new VoiceStore({ appDir: dir, engines: [new ElevenLabs({ key: 'test-key', base: fe.url }), new Gepard({ url: fg.url })], queueDelayMs: 0, level: null, denoise: null, revoiceDaily: 2 })
    const kept = await reserve.say('Kdo ve odgovor?', 'teacher')
    await Bun.sleep(150)
    check('…with the quota at the reserve: the local clip served, nothing sent, not counted (the reserve is for live lines)', kept?.engine === 'gepard' && fe.st.tts.length === t0 + 2 && reserve.lazyToday() === 0)
    reserve.close()
    vs.close()
  }
  check('course speech: the shared fixture is in place for the app\'s test', readdirSync(resolve(import.meta.dir, '../fixtures')).includes('intro-speech.json'))
}

/**
 * The bridge's side of a villager's local clips: /voice/prepare (the day's clips got ready ahead) and a download. Run by
 * the voice section while the bridge's ElevenLabs answers (before it takes it down).
 */
export async function bridgeLocalClips() {
  {
    const voiceDir = join(dataDir, 'app/voice')
    const db = new Database(join(voiceDir, 'voice.db'))
    const row = (text: string) => {
      const norm = normalizeText(text)
      const file = `${createHash('sha1').update(`smoke|gepard|${norm}`).digest('hex')}.mp3`
      writeFileSync(join(voiceDir, 'files', file), mp3(`gp:${text}`))
      db.query('INSERT OR REPLACE INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, hits) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)').run(`teacher:${norm}`, norm, text, 'teacher', 'gepard', file, text.length, '2026-10-20T10:00:00.000Z', 'scene:sola')
      return { norm, file }
    }
    const p = row('Učiteljica Mojca bere zgodbo.')
    const d = row('Otroci, tiho, prosim.')
    db.close()
    const updates = async () => ((await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()) as any[]).filter(b => b.event.type === 'voice_updated').length
    const u0 = await updates()
    const n0 = bridgeEleven.st.tts.length
    const r = await fetch(`${base}/voice/prepare`, { method: 'POST', headers: auth, body: JSON.stringify({ texts: [{ text: 'Učiteljica Mojca bere zgodbo.', voice: 'teacher' }] }) })
    const rb = await r.json()
    check('POST /voice/prepare of a line whose clip is the teacher\'s Gepard one: made in her ElevenLabs voice now and handed over instead, the local file gone, the app told (voice_updated)',
      r.ok && rb.clips?.[0]?.engine === 'elevenlabs' && rb.clips[0].url !== `/voice/file/${p.file}` && bridgeEleven.st.tts.length === n0 + 1 && bridgeEleven.st.tts.at(-1)?.voice === elevenlabsOf('teacher') &&
        (await fetch(`${base}/voice/file/${p.file}`, { headers: auth })).status === 404 && (await until(async () => (await updates()) > u0)), rb)
    const got = await fetch(`${base}/voice/file/${d.file}`, { headers: auth })
    const served = new TextDecoder().decode((await got.arrayBuffer()).slice(6))
    const url = async () => (await (await fetch(`${base}/voice/index`, { headers: auth })).json())[d.norm]?.teacher as string | undefined
    const moved = await until(async () => (await url()) !== `/voice/file/${d.file}`)
    check('GET /voice/file of a teacher\'s Gepard clip: served as it is, then made in her ElevenLabs voice soon, the index on the new file',
      got.ok && served === 'gp:Otroci, tiho, prosim.' && moved && bridgeEleven.st.tts.some(t => t.body.text === 'Otroci, tiho, prosim.' && t.voice === elevenlabsOf('teacher')), await url())
  }
}
