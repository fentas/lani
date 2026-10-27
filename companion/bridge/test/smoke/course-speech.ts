// Smoke checks: the school's course in the teacher's voice (src/course-speech.ts, companion/COURSE.md "Read aloud"):
// - the port reads the shared cases of test/fixtures/intro-speech.json as the app does (IntroSpeechParityTest reads them too);
// - who teaches (the school's person: Učiteljica Mojca, her own voice once it is ready), what the corpus voices of the
//   course and of the cast's arrivals, in her voice;
// - companion/bin/voice-build --only, --explanations and --base on a store of its own with the fake engines: the plan's
//   course row, the explanations counted apart, ElevenLabs within the budget and never into the reserve.
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
      const got = {
        vocabulary: [...S.vocabulary(c.intro, c.language, c.title, isSlovene)].sort(),
        steps: c.intro.steps.map((st: S.IntroStep, i: number) => ({ ...scriptOut(S.introStep(`x/${i}`, c.intro, i, c.language, c.base, c.title, isSlovene)), answer: S.answer(st.ask) ?? null })),
        summary: say === undefined ? null : { say, ...scriptOut(S.summary('x/summary', say, c.base, c.intro, c.language, c.title, isSlovene)) },
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

  check('course speech: the shared fixture is in place for the app\'s test', readdirSync(resolve(import.meta.dir, '../fixtures')).includes('intro-speech.json'))
}

