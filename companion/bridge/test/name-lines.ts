// Name lines (docs/plans/05-built/audio-names.md): the flags over all the content, the name check, the cut, the store and the
// routes, against the fake ElevenLabs. The smoke's section runs this. On its own: `bun test/name-lines.ts`.
//
//   bun test/name-lines.ts --table      what the content's lines come to, per culture: the types and templates
//   bun test/name-lines.ts --cost       what building them would cost, in characters and credits: nothing is voiced
import './isolate'
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { addressee } from '../src/addressee'
import { useCastFile, personVoice } from '../src/cast'
import { cultureArrivals } from '../src/arrivals'
import { cultureCelebrationFiles, partyTellings } from '../src/celebrations'
import { CourseStore, courseDir } from '../src/course'
import { cultureDir, cultureKeepers, keeperTellings, resolveCulture } from '../src/cultures'
import { DrillStore, drillsDir } from '../src/drills'
import { grammarDir, GrammarStore } from '../src/grammar'
import { readISpy } from '../src/ispy'
import { clickDb, cutLine, rmsDb, SR, tracks } from '../src/name-cut'
import { flagLine, LINE_TYPES, markName, NAME_CARRIERS, nameOk, NameCatalogue, PHRASE_TAILS, PROBE_NAME, saysName, TEMPLATES, FILLERS, spokenName, normalizeName } from '../src/name-lines'
import { lineId, nameSettings, NameStore, type NameSettings } from '../src/name-store'
import { PackStore, packsDirs } from '../src/packs'
import { cultureStepFiles, stepTellings } from '../src/project-steps'
import { ScenarioStore } from '../src/scenarios'
import { SceneStore, scenesDirs } from '../src/scenes'
import { ModuleStore } from '../src/store'
import { StoryStore, storiesDirs } from '../src/stories'
import { VillagerStore } from '../src/villagers'
import { corpus, ElevenLabs, ffmpegPcm, Gate, Gepard, TURN, VoiceStore } from '../src/voice'
import { fakeEleven, speechTake } from './fakes/eleven'

type Check = (name: string, cond: unknown, detail?: unknown) => void

const companion = resolve(import.meta.dir, '../..')
const cultures = join(companion, 'cultures')
const temp = (p: string) => mkdtempSync(join(tmpdir(), p))
const CULTURES: [id: string, language: string][] = [['primorska', 'sl'], ['friuli', 'it'], ['kaernten', 'de'], ['lakeland', 'en']]

/** Which of a village's content: all of it, its A1 (the scenes at A1, the villagers, who greet at any level), or its first scenes. */
type Scope = 'all' | 'A1' | 'first'
/** The fire and the tent: what a new village has, each culture's. */
const FIRST: Record<string, string[]> = { primorska: ['ob-ognju', 'v-sotoru'], friuli: ['al-fuoco', 'nella-tenda'], kaernten: ['am-feuer', 'im-zelt'], lakeland: ['by-the-fire', 'in-the-tent'] }

/** The texts [id]'s village says to a man or a woman called [PROBE_NAME], as voice-build reads its content, each in its voice. */
export function voicedItems(id: string, language: string, gender: 'male' | 'female', scope: Scope = 'all') {
  const own = join(cultureDir(cultures, id), 'voice-cast.json')
  useCastFile(existsSync(own) ? own : undefined)
  const app = temp('lani-names-')
  const log = () => {}
  const ofLanguage = (d: string) => (language === 'sl' ? d : join(d, language))
  const packs = new PackStore(packsDirs(join(companion, 'packs'), cultures, id, language), join(app, 'packs'))
  const modules = new ModuleStore(join(app, 'modules'), ofLanguage(join(companion, 'modules')), log)
  const scenarios = new ScenarioStore(ofLanguage(join(companion, 'scenarios')), join(app, 'scenarios'), log, language)
  const villagers = new VillagerStore(join(cultureDir(cultures, id), 'villagers'), join(app, 'villagers'), log)
  const scenes = new SceneStore(scenesDirs(join(companion, 'scenes'), cultures, id, language), join(app, 'scenes'), () => ({ packs: packs.all(), scenarios: scenarios.all(), villagers: villagers.all() }), log)
  const stories = new StoryStore(storiesDirs(cultures, id), join(app, 'stories'), () => ({ packs: packs.all(), villagers: villagers.all() }), log)
  const drills = new DrillStore(drillsDir(join(companion, 'drills'), language), language, () => ({ packs: packs.all(), villagers: villagers.all() }), log)
  const grammar = new GrammarStore(grammarDir(join(companion, 'grammar'), language), language, join(app, 'grammar'), log)
  const course = new CourseStore(courseDir(join(companion, 'course'), language), language, join(app, 'course'), log, () => 'en')
  const arrivals = Object.entries(cultureArrivals(cultures, id, language).file?.cast ?? {}).map(([k, a]) => ({ id: k, levels: (a as { levels: never }).levels }))
  const learner = addressee({ name: PROBE_NAME, gender }, language)
  if (scope !== 'all') {
    // a dialog with no level is everyone's; one above A1 isn't said to a beginner
    const a1 = (d: { level?: string }) => !d.level || d.level === 'A1'
    const picked = scenes.resolved()
      .filter(s => (scope === 'first' ? FIRST[id].includes(s.id) : (s as { level?: string }).level === 'A1'))
      .map(s => ({ ...s, dialogs: s.dialogs.filter(a1), variants: (s as { variants?: { level?: string }[] }).variants?.filter(a1) }))
    return corpus({ packs: [], modules: [], scenarios: [], scenes: picked as never, villagers: villagers.all(), language, learner, ownVoice: i => !!personVoice(`@${i}`) }).filter(i => !i.lang && (scope === 'A1' || i.source.startsWith('scene:')))
  }
  return corpus({
    packs: packs.all(), modules: modules.list().filter(m => !m.tags.includes('retired')).flatMap(m => modules.get(m.id) ?? []), scenarios: scenarios.all(), scenes: scenes.resolved(),
    keepers: cultureKeepers(cultures, id).map(k => ({ ...k, tellings: keeperTellings(k) })),
    steps: [...cultureStepFiles(cultures, id).map(f => ({ project: f.project, tellings: stepTellings(f) })), ...cultureCelebrationFiles(cultures, id).map(f => ({ project: `party:${f.age}`, tellings: partyTellings(f) }))],
    villagers: villagers.all(), stories: stories.all(), drills: drills.all(), grammar: grammar.all(), course: { units: course.served()?.units ?? [], pages: grammar.all(), bases: ['en'], has: () => false },
    arrivals, ispy: readISpy(join(companion, 'ispy')), ownVoice: i => !!personVoice(`@${i}`), language, learner,
  }).filter(i => !i.lang)
}

const voiced = (id: string, language: string, gender: 'male' | 'female') => voicedItems(id, language, gender).map(i => i.text)

/** Every name line of a village, flagged. */
export const catalogueOf = (id: string, language: string) => new NameCatalogue(language, voiced(id, language, 'male'), voiced(id, language, 'female'))

const count = <T>(l: T[], f: (x: T) => string) => l.reduce<Record<string, number>>((o, x) => ((o[f(x)] = (o[f(x)] ?? 0) + 1), o), {})

function flags(check: Check) {
  const flag = (said: string, language = 'sl') => flagLine(said, language)
  const cases: [string, string, string, string, string?][] = [
    // the test's own lines: [line, language, type, template, what is voiced per name]
    ['{n}! Pridi, juha je vroča.', 'sl', 'first', 'arch'],
    ['O, {n}! Ravno kuham. Si lačen?', 'sl', 'phrase', 'arch', 'O, {n}!'],
    ['Hvala, {n}. Ti si zlat.', 'sl', 'phrase', 'ending', 'Hvala, {n}.'],
    ['{n}? Še buden? Prinesla sem odejo. In vroč čaj!', 'sl', 'first', 'rise'],
    ['Lovski blagor, {n}!', 'sl', 'phrase', 'ending', 'Lovski blagor, {n}!'],
    ['Dober dan, {n}! Si prišel pomagat?', 'sl', 'phrase', 'arch', 'Dober dan, {n}!'],
    ['Lahko noč, {n}.', 'sl', 'phrase', 'ending', 'Lahko noč, {n}.'],
    ['{n}, imaš čas za eno zgodbo?', 'sl', 'first', 'arch'],
    ['Glasen? Kdo je glasen, {n}?', 'sl', 'phrase', 'rise', 'Kdo je glasen, {n}?'],
    ['Oho? Še enkrat, {n}, in na glas.', 'sl', 'splice', 'ending'],
    // the other languages
    ['Grazie, {n}! Sei bravissimo.', 'it', 'phrase', 'ending', 'Grazie, {n}!'],
    ['{n}, vieni a vedere!', 'it', 'first', 'arch'],
    ['Ciao, {n}! Come va?', 'it', 'phrase', 'arch', 'Ciao, {n}!'],
    ['Danke, {n}. Das war gut.', 'de', 'phrase', 'ending', 'Danke, {n}.'],
    ['Wo warst du denn so lange, {n}?', 'de', 'splice', 'rise'],
    ['Thank you, {n}! That was kind.', 'en', 'phrase', 'ending', 'Thank you, {n}!'],
    ['{n}! Come and look.', 'en', 'first', 'arch'],
  ]
  for (const [said, language, type, template, phrase] of cases) {
    const l = flag(said, language)
    check(`flags: "${said}": ${type}, ${template}`, l.type === type && l.template === template && (!phrase || l.phrase === phrase), l)
  }
  check('flags: a line where a word follows the name is voiced whole', flag('Danes je {n} doma.').type === 'whole', flag('Danes je {n} doma.'))
  check('flags: the name twice is voiced whole', flag('{n}, {n}! Pridi.').type === 'whole')
  check('flags: another form of the name beside it is voiced whole', flagLine('Hvala, {n}.', 'sl', true).type === 'whole')
  check('flags: a language without carrier sentences is voiced whole', flag('{n}! Vem.', 'hr').type === 'whole')
  check('flags: a line that names someone called as the filler is voiced whole: a cut would find them', flag('Dobro jutro, {n}! Marko je v vinogradu.').why === 'the filler is in the line', flag('Dobro jutro, {n}! Marko je v vinogradu.'))
  const p = flag('Hvala, {n}. Ti si zlat.')
  check('flags: a phrase that leads on is voiced with the words after it', p.carrier === 'Hvala, {n}. Zdaj vem.' && p.kind.startsWith('phrase:') && p.tail === 'Ti si zlat.', p)
  check('flags: a phrase that ends the line is voiced as it is, and is another clip', flag('Hvala, {n}.').carrier === 'Hvala, {n}.' && flag('Hvala, {n}.').kind !== p.kind)
  check('flags: a sentence before the name is a shared head', flag('Oho! {n}, kaj nosiš?').head === 'Oho!' && flag('Oho! {n}, kaj nosiš?').type === 'first')
  check('marks: whole words only: "Jana" is no Jan, "Jan" in any case is', markName('Jana in JAN.', 'Jan').said === 'Jana in {n}.' && markName('Jana in JAN.', 'Jan').count === 1)

  // every language has what it needs, and no carrier ends a name's sentence on the name
  for (const [, language] of CULTURES) {
    const c = NAME_CARRIERS[language]
    check(`flags: ${language} has fillers, carriers for each template, tails`, !!FILLERS[language] && TEMPLATES.every(t => c?.[t]?.includes('{n}') && !c[t].trimEnd().endsWith('{n}.')) && !!PHRASE_TAILS[language])
  }

  // the check over all the content: no line of any village goes unflagged, and few are voiced whole
  for (const [id, language] of CULTURES) {
    const lines = catalogueOf(id, language).all()
    const bad = lines.filter(l => !LINE_TYPES.includes(l.type) || !TEMPLATES.includes(l.template) || (l.type !== 'whole' && (!l.kind || !l.carrier.includes('{n}') || !l.phrase.includes('{n}'))) || (l.type === 'whole' && !l.why))
    const wholes = lines.filter(l => l.type === 'whole').length
    check(`flags: every name line of ${id} has its type and template (${lines.length}: ${JSON.stringify(count(lines, l => l.type))})`, lines.length > 100 && !bad.length, bad.slice(0, 3))
    check(`flags: at most 1 in 10 of ${id}'s name lines is voiced whole (${wholes} of ${lines.length})`, wholes * 10 <= lines.length, lines.filter(l => l.type === 'whole').slice(0, 5).map(l => `${l.said}: ${l.why}`))
  }
}

function names(check: Check) {
  const ok = ['Jan', 'Ana', 'Teo', 'Matej', 'Aleksandra', 'Ana Marija', 'Jean-Luc', "D'Arcy", 'Žiga', 'José', 'Maximilian', 'Lea', ' jan ', 'Björn', 'Gergő', 'Søren', 'Zoë']
  const no = ['', 'J', 'Give me all your money', 'a'.repeat(25), 'Jan2', 'J@n', 'http://x.y', 'Ana Marija Novak', 'Aaaaaa', 'Юлия', 'Prrrt', 'Admin', 'fuck', 'Hvala. Pridi', 'Jan!', '{learner}', 'x'.repeat(17)]
  for (const n of ok) check(`names: "${n}" may be voiced`, !!nameOk(n), nameOk(n))
  for (const n of no) check(`names: "${n.slice(0, 26)}" may not`, !nameOk(n), nameOk(n))
  check('names: keyed in one form however it is written', normalizeName(' ANA   Marija ') === 'ana marija' && nameOk('JAN') === 'jan')
  check('names: said with their capitals', spokenName('ana marija') === 'Ana Marija' && spokenName('jean-luc') === 'Jean-Luc')
  const a = addressee({ name: 'Admin' })
  check('names: a text says a name in any form: the name, a case, the possessive with its ending', saysName('Dober dan, Admin!', a.name, a.forms) && saysName('Sveža voda za admina.', a.name, a.forms) && saysName('To je Adminova hiša.', a.name, a.forms))
  check('names: and not inside another word, nor where a short name is only letters', !saysName('Administracija je zaprta.', a.name, a.forms) && !saysName('Jaz grem.', 'J', {}))
}

async function cut(check: Check) {
  const pcm = ffmpegPcm()!
  const speak = async (text: string, o = {}) => {
    const t = speechTake(text, o)
    const x = (await pcm(t.audio))!
    return { x, a: t.alignment, tr: tracks(x) }
  }
  const line = flagLine('Hvala, {n}. Ti si zlat.', 'sl')
  const a = await speak('Hvala, Marko. Ti si zlat.')
  const c = cutLine(a.x, a.a, line, 'Marko', a.tr, true)
  check('cut: a phrase line has a tail after a real pause', !('why' in c) && !!c.tail && c.gapRight > 0.1 && c.tail[0] < c.tail[1], c)
  const s = flagLine('Oho? Še enkrat, {n}, in na glas.', 'sl')
  const b = await speak('Oho? Še enkrat, Marko, in na glas.')
  const d = cutLine(b.x, b.a, s, 'Marko', b.tr, true)
  check('cut: a splice has a head and a tail, a real pause on both sides', !('why' in d) && !!d.head && !!d.tail && d.gapLeft > 0.05 && d.gapRight > 0.05, d)
  const run = await speak('Oho? Še enkrat, Marko, in na glas.', { runThrough: true })
  const e = cutLine(run.x, run.a, s, 'Marko', run.tr, true)
  check("cut: a voice that runs through the comma can't be trusted: voiced whole", 'why' in e, e)
  const w = await speak('Nekaj drugega.')
  check("cut: a take without the filler can't be trusted", 'why' in cutLine(w.x, w.a, line, 'Marko', w.tr, true))
  // a low tone, cut at its peak against silence, and the same with a fade
  const tone = Float32Array.from({ length: SR }, (_, i) => (i < SR / 2 ? Math.sin((2 * Math.PI * 110 * (i - SR / 2 + SR / 440)) / SR) * 0.5 : 0))
  const faded = Float32Array.from(tone, (v, i) => v * Math.min(1, Math.max(0, (SR / 2 - i) / 441)))
  check('cut: the click measure sees a hard step and no faded join', clickDb(tone, [SR / 2]) > 20 && clickDb(faded, [SR / 2]) < 0, { hard: clickDb(tone, [SR / 2]), faded: clickDb(faded, [SR / 2]) })
}

const settings = (o: Partial<NameSettings> = {}): NameSettings => ({ on: true, daily: 3_000, names: 3, takes: 2, ...o })

/** A store with the fake ElevenLabs, its catalogue of [lines] (the name as PROBE_NAME). */
function storeOf(lines: string[], o: Partial<NameSettings> = {}, voice: { monthly?: number; liveLimit?: { max: number; windowMs: number } } = {}) {
  const fe = fakeEleven()
  fe.st.speech = true
  const vs = new VoiceStore({ appDir: temp('lani-names-store-'), engines: [new ElevenLabs({ key: 'test-key', base: fe.url, reserve: 0 })], queueDelayMs: 0, language: 'sl', ...voice })
  const cat = new NameCatalogue('sl', lines, lines)
  const logged: string[] = []
  const ns = new NameStore(vs, { settings: settings(o), catalogue: () => cat, log: (...a) => logged.push(a.join(' ')) })
  return { fe, vs, ns, logged }
}

async function store(check: Check) {
  const jan = addressee({ name: 'Jan' })
  const ana = addressee({ name: 'Ana', gender: 'female' })
  const say = (name: string, rest: string) => rest.replace('{n}', name)
  const lines = ['Hvala, {n}. Ti si zlat.', '{n}! Pridi, juha je vroča.', 'Lahko noč, {n}.', 'Oho? Še enkrat, {n}, in na glas.', 'Danes je {n} doma.'].map(l => say(PROBE_NAME, l))
  const { fe, vs, ns, logged } = storeOf(lines)
  const sent = () => fe.st.tts.map(t => t.body.text as string)

  // the first learner reaches a phrase line: one take of the line, two of the phrase
  const a = await ns.line('Hvala, Jan. Ti si zlat.', 'grandma', jan)
  check('store: a phrase line: one take with the filler, two of the phrase in its carrier', a.say === 'joined' && sent().join('|') === 'Hvala, Marko. Ti si zlat.|Hvala, Jan. Zdaj vem.|Hvala, Jan. Zdaj vem.', { a, sent: sent() })
  const id = a.say === 'joined' ? a.url.match(/([a-f0-9]{40})/)![1] : ''
  const m = ns.phone(id)
  check("store: the phone's manifest: the name, the line's own pause, the piece", m?.v === 1 && m.text === 'Hvala, Jan. Ti si zlat.' && m.parts.map(p => p.kind).join() === 'name,gap,piece' && m.parts[0].kind === 'name' && m.parts[0].url.startsWith('/voice/file/'), m)
  check('store: asked again: the same line, nothing spent', await ns.line('Hvala, Jan. Ti si zlat.', 'grandma', jan).then(r => r.say === 'joined' && r.url === (a as { url: string }).url && sent().length === 3))
  const row = vs.database.query('SELECT * FROM name_clips').all() as Record<string, unknown>[]
  check("store: a name clip's row: language, voice, name, kind and how it was made, nobody's", row.length === 1 && row[0].name === 'jan' && row[0].voice === 'grandma' && !Object.keys(row[0]).some(k => /learner|node|device|account|user|profile/.test(k)), row)

  // the next learner: the line is there, only their name is new; the same name later costs nothing
  const before = sent().length
  await ns.line('Hvala, Ana. Ti si zlat.', 'grandma', ana)
  check('store: the next learner pays for their name only: the shared piece stays', sent().length === before + 2 && sent().slice(-1)[0] === 'Hvala, Ana. Zdaj vem.', sent().slice(before))
  const again = sent().length
  const other = await ns.line('Hvala, Jan. Ti si zlat.', 'grandma', addressee({ name: 'jan', gender: 'male' }))
  check('store: another learner of the same name: the library has it, nothing spent', other.say === 'joined' && sent().length === again)

  // a name-first line: only the name's template clip; a line that is only its phrase: no take of it
  const first = await ns.line('Jan! Pridi, juha je vroča.', 'grandma', jan)
  const fm = first.say === 'joined' ? ns.phone(first.url.match(/([a-f0-9]{40})/)![1]) : undefined
  check('store: a name-first line: the name in its template, one gain, then the shared piece', fm?.parts.map(p => p.kind).join() === 'name,gap,piece' && sent().includes('Jan! Pridi sem.') && sent().includes('Marko! Pridi, juha je vroča.'), { first, fm })
  const nameRow = vs.database.query("SELECT kind FROM name_clips WHERE kind = 'arch'").all()
  check('store: the name clip is the template, for every line of it', nameRow.length === 1)
  const n0 = sent().length
  const whole = await ns.line('Lahko noč, Jan.', 'grandma', jan)
  check('store: a line that is only its phrase: no take of the line, the phrase ends it', whole.say === 'joined' && sent().slice(n0).every(t => t === 'Lahko noč, Jan.') && sent().length === n0 + 2, sent().slice(n0))
  const splice = await ns.line('Oho? Še enkrat, Jan, in na glas.', 'grandma', jan)
  const sm = splice.say === 'joined' ? ns.phone(splice.url.match(/([a-f0-9]{40})/)![1]) : undefined
  check('store: a splice: the head, its pause, the name, its pause, the tail', sm?.parts.map(p => p.kind).join() === 'piece,gap,name,gap,piece', sm)

  // what stays as before
  const b0 = sent().length
  check('store: a line flagged whole goes on as before', (await ns.line('Danes je Jan doma.', 'grandma', jan)).say === 'before' && sent().length === b0)
  check("store: a text that's no line of the content (the tutor's) goes on as before: nothing of it is shared", (await ns.line('Hvala, Jan, za današnji pogovor.', 'grandma', jan)).say === 'before' && sent().length === b0)
  await vs.voice('Lahko noč, Matej.', 'grandma', 'scene:test')
  const b1 = sent().length
  check('store: a whole clip of a line stays its clip', (await ns.line('Lahko noč, Matej.', 'grandma', addressee({ name: 'Matej' }))).say === 'before' && sent().length === b1)
  check("store: a learner's name that fails the check: the phone's voice, nothing sent", (await ns.line('Hvala, Xx1. Ti si zlat.', 'grandma', addressee({ name: 'Xx1' }))).say === 'phone' && sent().length === b1)
  const bad = storeOf([say(PROBE_NAME, 'Lahko noč, {n}.')])
  check("store: a name that is no name, in a line of the content: the phone's voice, silently", (await bad.ns.line('Lahko noč, Admin.', 'grandma', addressee({ name: 'Admin' }))).say === 'phone' && bad.fe.st.tts.length === 0)

  const badOut = storeOf([say(PROBE_NAME, 'Lahko noč, {n}.')])
  const admin = addressee({ name: 'Admin' })
  check("store: a name that is no name, in a text the catalogue doesn't know, or a declined form: the phone's voice", (await badOut.ns.line('Kako si, Admin, danes?', 'grandma', admin)).say === 'phone' && (await badOut.ns.line('Sveža voda za admina.', 'grandma', admin)).say === 'phone' && badOut.fe.st.tts.length === 0)
  check('store: a text without the name goes on as before, whatever the name', (await badOut.ns.line('Dober dan vsem.', 'grandma', admin)).say === 'before')
  const badWhole = storeOf([say(PROBE_NAME, 'Danes je {n} doma.')])
  check("store: a name that is no name, in a line voiced whole: the phone's voice too: a whole take would say it", (await badWhole.ns.line('Danes je Admin doma.', 'grandma', addressee({ name: 'Admin' }))).say === 'phone' && badWhole.fe.st.tts.length === 0)

  // no learner in the log, not even a name
  check('store: no name in any log line', !logged.some(l => /\bjan\b|\bana\b/i.test(l)), logged)

  // the finished file for an older app, joined on the node
  const p = await ns.finished(id)
  const joined = p ? (await ffmpegPcm()!(new Uint8Array(readFileSync(p))))! : new Float32Array()
  const want = m!.parts.reduce((n, x) => n + x.ms, 0) / 1000
  check("store: an older app's file: one MP3, the parts' length to the millisecond's worth", !!p && Math.abs(joined.length / SR - want) < 0.06, { got: joined.length / SR, want })
  // where the name ends and the piece starts stands the line's own pause and nothing longer
  const db = rmsDb(joined)
  let run = 0
  let longest = 0
  for (const v of db) {
    run = v < -55 ? run + 1 : 0
    longest = Math.max(longest, run)
  }
  const gap = m!.parts.find(x => x.kind === 'gap')!.ms
  check(`store: no gap in the joined file longer than the line's own pause (${gap} ms, and the take's own)`, (longest * 5) <= Math.max(gap, 300) + 40, { longest: longest * 5, gap })
  check('store: the finished file is kept, not joined again', (await ns.finished(id)) === p)
  const idx = ns.withLines({ 'hvala jan ti si zlat': { other: '/voice/file/x.mp3' } })
  check('store: the index gives the joined line beside what there is', idx['hvala jan ti si zlat'].grandma === `/voice/line/${id}.mp3` && idx['hvala jan ti si zlat'].other === '/voice/file/x.mp3', idx)
  const kept = ns.withLines({ 'hvala jan ti si zlat': { grandma: '/voice/file/whole.mp3' } })
  check('store: … and a whole clip wins over it', kept['hvala jan ti si zlat'].grandma === '/voice/file/whole.mp3')

  // a swapped voice: its pieces and names are made again, nothing else
  const stale = vs.database.query('SELECT COUNT(*) AS n FROM name_lines WHERE engine_voice = ?').get('old') as { n: number }
  check('store: nothing is stale yet', stale.n === 0)
  vs.database.query("UPDATE name_lines SET engine_voice = 'old'").run()
  vs.database.query("UPDATE name_clips SET engine_voice = 'old'").run()
  vs.database.query("UPDATE name_joined SET engine_voice = 'old'").run()
  check('store: a voice swapped under a line takes it out of the index, or the app would never ask for it again', !ns.withLines({})['hvala jan ti si zlat'], ns.withLines({})['hvala jan ti si zlat'])
  const s0 = sent().length
  await ns.line('Hvala, Jan. Ti si zlat.', 'grandma', jan)
  check('store: a voice swapped under a line: made again in the voice now', sent().length === s0 + 3, sent().slice(s0))

  // the cut can't be trusted: one take paid, then voiced whole for each learner, the take not paid for again
  {
    const t = storeOf(lines)
    t.fe.st.runThrough = true
    const r = await t.ns.line('Oho? Še enkrat, Jan, in na glas.', 'grandma', jan)
    const paid = t.fe.st.tts.length
    const row = t.vs.database.query('SELECT type, why FROM name_lines WHERE line = ?').get(lineId('sl', 'grandma', 'Oho? Še enkrat, {n}, in na glas.')) as { type: string; why: string } | null
    check("store: a line whose cut can't be trusted is voiced whole, and says why", r.say === 'before' && row?.type === 'whole' && !!row.why && paid === 1, { r, row })
    check('store: … and the take is not paid for again', (await t.ns.line('Oho? Še enkrat, Ana, in na glas.', 'grandma', ana)).say === 'before' && t.fe.st.tts.length === paid)
  }

  // ffmpeg failing for a moment is no verdict on a name: it is tried again, and then it cuts
  {
    const t = storeOf(lines)
    await t.ns.line('Oho? Še enkrat, Jan, in na glas.', 'grandma', jan)
    const down = new NameStore(t.vs, { settings: settings(), catalogue: () => new NameCatalogue('sl', lines, lines), pcm: async () => null })
    const r = await down.line('Lahko noč, Ana.', 'grandma', ana)
    const rows = (t.vs.database.query("SELECT COUNT(*) AS n FROM name_clips WHERE name = 'ana'").get() as { n: number }).n
    check('store: a name take ffmpeg failed on leaves no verdict', r.say === 'before' && rows === 0, { r, rows })
    check('store: … and it is made once ffmpeg is back', (await t.ns.line('Lahko noč, Ana.', 'grandma', ana)).say === 'joined')
  }

  // the live lines' rate limit is on what is voiced
  {
    const t = storeOf(lines, {}, { liveLimit: { max: 1, windowMs: 600_000 } })
    await t.ns.line('Lahko noč, Jan.', 'grandma', jan)
    t.vs.database.query('DELETE FROM name_joined').run()
    while (t.vs.liveSlot()) {}
    check('store: over the live limit, a line whose parts are all here is still joined', (await t.ns.line('Lahko noč, Jan.', 'grandma', jan)).say === 'joined')
    check('store: … and one that would be voiced waits for the phone', (await t.ns.line('Hvala, Jan. Ti si zlat.', 'grandma', jan)).say === 'phone')
  }

  // a name's two takes: the lower is kept
  {
    const t = storeOf(lines, { takes: 2 })
    t.fe.st.pitches = [160, 220, 150]
    const r = await t.ns.line('Hvala, Jan. Ti si zlat.', 'grandma', jan)
    const c = t.vs.database.query('SELECT file FROM name_clips').get() as { file: string }
    const y = (await ffmpegPcm()!(new Uint8Array(readFileSync(t.vs.path(c.file)!))))!
    const { medianPitch } = await import('../src/name-cut')
    const f = medianPitch(y)
    check('store: of two takes of a name the lower is kept', r.say === 'joined' && f > 130 && f < 175, f)
  }

  // the bounds
  {
    const t = storeOf(lines, { daily: 40 })
    const r1 = await t.ns.line('Hvala, Jan. Ti si zlat.', 'grandma', jan)
    const paid = t.fe.st.tts.length
    check("store: over the day's characters: the phone's voice, and nothing spent", r1.say === 'phone' && paid <= 1, { r1, paid })
    check('store: told once in the log, without the name', t.logged.filter(l => l.includes('spent')).length === 1 && !t.logged.some(l => /\bjan\b/i.test(l)), t.logged)
    const n = storeOf(lines, { names: 2 })
    await n.ns.line('Lahko noč, Jan.', 'grandma', jan)
    await n.ns.line('Lahko noč, Ana.', 'grandma', ana)
    const third = await n.ns.line('Lahko noč, Mojca.', 'grandma', addressee({ name: 'Mojca', gender: 'female' }))
    const known = await n.ns.line('Hvala, Jan. Ti si zlat.', 'grandma', jan)
    check("store: a new name past the month's bound: the phone's voice; a name counted goes on", third.say === 'phone' && known.say === 'joined', { third, known })
    const off = storeOf(lines, { on: false })
    check('store: with the setting off nothing runs: every line as before', (await off.ns.line('Hvala, Jan. Ti si zlat.', 'grandma', jan)).say === 'before' && off.fe.st.tts.length === 0)
    const none = nameSettings({})
    check('store: off unless the node sets it', !none.on && nameSettings({ LANI_VOICE_NAMES: 'on' }).on && nameSettings({ LANI_VOICE_NAME_TAKES: '9' }).takes === 3)
  }

  // an engine that can't now is no cap: what else can say the line says it, as ever
  {
    const t = storeOf(lines)
    t.fe.st.used = t.fe.st.limit
    await t.vs.eleven!.subscription(true)
    check('store: ElevenLabs out of characters: the line goes on as before, to the local worker or the phone', (await t.ns.line('Hvala, Jan. Ti si zlat.', 'grandma', jan)).say === 'before' && t.fe.st.tts.length === 0, t.fe.st.tts.length)
  }

  // the month's cap, on anything ElevenLabs is sent
  {
    const t = storeOf(lines, {}, { monthly: 50 })
    const c1 = await t.vs.voice('Dober dan, vsi skupaj in še kdo.', 'grandma', 'live', { live: true })
    const c2 = await t.vs.voice('Lep pozdrav vsem skupaj, res lep pozdrav.', 'grandma', 'live', { live: true })
    check("caps: the month's cap holds even for a live line: no clip, the phone speaks", !!c1 && !c2 && t.vs.spentThisMonth() === 'Dober dan, vsi skupaj in še kdo.'.length, { c1, c2, spent: t.vs.spentThisMonth() })
    const u = storeOf(lines)
    await u.vs.voice('Dober dan.', 'grandma', 'live', { live: true })
    check('caps: with no cap set, nothing changes but the count', u.vs.monthly === undefined && u.vs.spentThisMonth() === 'Dober dan.'.length)
  }
}

function gate(check: Check) {
  return (async () => {
    const g = new Gate(1)
    const order: string[] = []
    let open = () => {}
    const first = g.run(() => new Promise<void>(r => (open = r)))
    const job = (n: string, turn: Parameters<Gate['run']>[1]) => g.run(async () => void order.push(n), turn)
    const all = [job('background', false), job('preload', TURN.preload), job('live', true), job('preload 2', TURN.preload), job('background 2', TURN.background)]
    open()
    await Promise.all([first, ...all])
    check('turns: a live line first, the preload next, the background last, each in its order', order.join() === 'live,preload,preload 2,background,background 2', order)
  })()
}

async function revoice(check: Check) {
  const fe = fakeEleven()
  const vs = new VoiceStore({ appDir: temp('lani-names-revoice-'), engines: [new ElevenLabs({ key: 'test-key', base: fe.url, reserve: 0, model: 'eleven_v4_turbo' }), new Gepard({ url: 'http://127.0.0.1:9' })], queueDelayMs: 0, level: null, denoise: null, revoiceDaily: 10, revoiceKeep: 4, language: 'sl' })
  const { normalizeText } = await import('../src/family')
  const db = vs.database
  const put = (text: string, voice: string, engine: string, model: string | null, n: number) => {
    const file = `${String(n).padStart(40, '0')}.mp3`
    writeFileSync(join(vs.filesDir, file), new Uint8Array([0x49, 0x44, 0x33, 4]))
    const norm = normalizeText(text)
    db.query('INSERT INTO clips (key, norm, text, voice, engine, file, chars, created_at, source, hits, engine_voice, model) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)')
      .run(`${voice}:${norm}`, norm, text, voice, engine, file, text.length, '2026-10-01T00:00:00.000Z', 'scene:test', 3, null, model)
    db.query('INSERT OR IGNORE INTO stale_asks (key, n, first) VALUES (?, 1, ?)').run(`${voice}:${norm}`, '2026-10-02T00:00:00.000Z')
  }
  for (let i = 0; i < 20; i++) put(`Stara vrstica številka ${i}.`, 'grandma', 'elevenlabs', 'eleven_v3', i)
  vs.resumeRevoice()
  for (let i = 0; i < 100 && (vs.lazyToday() < 6 || vs.lazyQueue().length); i++) await Bun.sleep(20)
  check('caps: an older model takes the cap less what is kept for the wrong voices', vs.modelsToday() === 6 && vs.lazyToday() === 6, { models: vs.modelsToday(), today: vs.lazyToday() })
  // a character's local clip, asked for now: the kept part of the day is there for it
  put('Lokalna vrstica.', 'grandma', 'gepard', null, 99)
  check('caps: what is kept is there for a clip in the wrong voice', vs.lazyQueue().some(c => c.engine === 'gepard') && !vs.lazyQueue().some(c => c.model === 'eleven_v3'), vs.lazyQueue().map(c => c.key))
  vs.resumeRevoice()
  for (let i = 0; i < 100 && vs.lazyQueue().length; i++) await Bun.sleep(20)
  check('caps: … and it runs', vs.get(normalizeText('Lokalna vrstica.'), 'grandma')?.engine === 'elevenlabs' && vs.lazyToday() === 7, vs.get(normalizeText('Lokalna vrstica.'), 'grandma'))
  const { revoiceKeep } = await import('../src/voice')
  check('caps: by default about a third of the cap is kept (30 of 100), a setting says otherwise', revoiceKeep(100, {}) === 30 && revoiceKeep(40, {}) === 12 && revoiceKeep(100, { LANI_VOICE_REVOICE_KEEP: '50' }) === 50 && revoiceKeep(10, { LANI_VOICE_REVOICE_KEEP: '50' }) === 10)
}

async function preloading(check: Check) {
  const { Preload, preloadSettings } = await import('../src/voice-preload')
  const { normalizeText } = await import('../src/family')
  const fe = fakeEleven()
  const vs = new VoiceStore({ appDir: temp('lani-names-preload-'), engines: [new ElevenLabs({ key: 'test-key', base: fe.url, reserve: 0 })], queueDelayMs: 0, level: null, denoise: null, language: 'sl' })
  fe.st.delayMs = 30
  const made: string[] = []
  const pre = (daily: number) => new Preload(vs, {
    settings: { on: true, daily },
    done: l => !!vs.best(normalizeText(l.text), l.voice),
    make: async l => {
      made.push(l.text)
      await vs.voice(l.text, l.voice, 'preload', { turn: TURN.preload })
    },
  })
  const p = pre(4_000)
  const lines = ['Prva vrstica.', 'Druga vrstica.', 'Tretja vrstica.', 'Četrta vrstica.'].map(text => ({ text, voice: 'grandma' }))
  await vs.voice('Prva vrstica.', 'grandma', 'scene:test')
  const queued = p.start('a', [...lines, lines[1]])
  check('preload: only what is missing, each once', queued === 3, queued)
  for (let i = 0; i < 100 && made.length < 1; i++) await Bun.sleep(5)
  p.cancel('b')
  check("preload: someone else's cancel leaves it", made.length >= 1)
  p.cancel('a')
  const stopped = made.length
  await Bun.sleep(150)
  check('preload: leaving stops it: what is left is not made', made.length === stopped && made.length < 3 && made[0] === 'Druga vrstica.', made)
  const q = pre(4_000)
  made.length = 0
  q.start('c', [{ text: 'Peta vrstica.', voice: 'grandma' }, { text: 'Šesta vrstica.', voice: 'grandma' }])
  q.start('d', [{ text: 'Sedma vrstica.', voice: 'grandma' }])
  for (let i = 0; i < 100 && (q as unknown as { working: boolean }).working; i++) await Bun.sleep(10)
  check('preload: a new start replaces the last', made.join() === 'Peta vrstica.,Sedma vrstica.', made)
  check('preload: what it cost is counted', q.spentToday() > 0, q.spentToday())
  const small = pre(q.spentToday() + 5)
  made.length = 0
  small.start('e', [{ text: 'Osma vrstica je dolga.', voice: 'grandma' }])
  await Bun.sleep(30)
  check("preload: over the day's characters nothing more is made", made.length === 0, made)
  const off = new Preload(vs, { settings: { on: false, daily: 4_000 }, done: () => false, make: async () => void made.push('x') })
  check('preload: off unless the node sets it', off.start('f', lines) === 0 && !preloadSettings({}).on && preloadSettings({ LANI_VOICE_PRELOAD: 'on' }).on && made.length === 0)
  fe.stop()
}

/** The routes on a real bridge, the fake ElevenLabs behind it, with the setting on and an older app's asks. */
async function routes(check: Check, port: number) {
  const fe = fakeEleven()
  fe.st.speech = true
  const data = temp('lani-names-bridge-')
  mkdirSync(join(data, 'app'), { recursive: true })
  writeFileSync(join(data, 'learner-profile.json'), JSON.stringify({ learner: { name: 'Teo', gender: 'male' } }))
  const env: Record<string, string> = {
    ...(process.env as Record<string, string>), LANI_DATA_DIR: data, LANI_BRIDGE_PORT: String(port), LANI_BRIDGE_TOKEN: 'names-token', LANI_RHYTHM: 'off', LANI_CULTURES_DIR: cultures, LANI_VOICE_DESIGN: 'off',
    ELEVENLABS_API_KEY: 'test-key', ELEVENLABS_BASE_URL: fe.url, LANI_GEPARD_URL: 'http://127.0.0.1:9', LANI_VOICE_NAMES: 'on', LANI_VOICE_RESERVE: '0', LANI_ENV_FILE: '/dev/null',
  }
  const client = new Client({ name: 'smoke-names', version: '0' })
  const transport = new StdioClientTransport({ command: 'bun', args: [resolve(import.meta.dir, '../src/index.ts')], env, stderr: 'pipe' })
  let logged = ''
  transport.stderr?.on('data', (b: Buffer) => void (logged += b.toString()))
  await client.connect(transport)
  const base = `http://127.0.0.1:${port}`
  const auth = { authorization: 'Bearer names-token', 'content-type': 'application/json' }
  for (let i = 0; i < 80; i++) {
    if (await fetch(`${base}/health`).then(r => r.ok, () => false)) break
    await Bun.sleep(100)
  }
  try {
    // a line of the content: the first with a name in it and with something after it
    const cat = catalogueOf('primorska', 'sl')
    const line = cat.all().find(l => l.type === 'phrase' && l.tail && !l.woman && !l.head)!
    const text = line.said.replace('{n}', 'Teo')
    const say = (t: string, voice = 'grandma') => fetch(`${base}/voice/say`, { method: 'POST', headers: auth, body: JSON.stringify({ text: t, voice }) })
    const r = await say(text)
    const body = (await r.json().catch(() => ({}))) as { url?: string; voice?: string }
    check(`routes: POST /voice/say gives a line of the content its line's URL ("${line.said}")`, r.status === 200 && /^\/voice\/line\/[a-f0-9]{40}\.mp3$/.test(body.url ?? '') && body.voice === 'grandma', { status: r.status, body, log: logged.slice(-300) })
    const url = body.url ?? ''
    const m = (await (await fetch(`${base}${url.replace('.mp3', '.json')}`, { headers: auth })).json()) as { parts?: { url?: string }[] }
    const part = m.parts?.find(p => p.url)?.url ?? ''
    check('routes: its manifest, and its parts are clips', m.parts?.length === 3 && (await fetch(`${base}${part}`, { headers: auth })).ok, m)
    const old = await fetch(`${base}${url}`, { headers: auth })
    const bytes = new Uint8Array(await old.arrayBuffer())
    const heard = bytes.length ? await ffmpegPcm()!(bytes) : null
    check('routes: an older app downloads one finished MP3 at that URL', old.ok && old.headers.get('content-type') === 'audio/mpeg' && (heard?.length ?? 0) > SR / 2, { status: old.status, n: bytes.length })
    const index = (await (await fetch(`${base}/voice/index`, { headers: auth })).json()) as Record<string, Record<string, string>>
    const { normalizeText } = await import('../src/family')
    check('routes: /voice/index lists it, as a clip', index[normalizeText(text)]?.grandma === url, index[normalizeText(text)])
    const prep = (await (await fetch(`${base}/voice/prepare`, { method: 'POST', headers: auth, body: JSON.stringify({ texts: [{ text, voice: 'grandma' }] }) })).json()) as { clips: { url: string | null }[] }
    check('routes: /voice/prepare gives the same line', prep.clips[0].url === url, prep)
    const free = await say('Kaj pa zdaj, Teo, kam greva?')
    check("routes: a text that's no line of the content is voiced whole, as ever", free.status === 200 && ((await free.json()) as { url: string }).url.startsWith('/voice/file/'))
    check('routes: an unknown line is a 404', (await fetch(`${base}/voice/line/${'0'.repeat(40)}.json`, { headers: auth })).status === 404 && (await fetch(`${base}/voice/line/${'0'.repeat(40)}.mp3`, { headers: auth })).status === 404)
    const st = (await (await fetch(`${base}/voice/status`, { headers: auth })).json()) as { names: { on: boolean; lines: number; name_clips: number } }
    check('routes: voice_status says what there is', st.names?.on === true && st.names.lines >= 1 && st.names.name_clips >= 1, st.names)
    const q = await fetch(`${base}/voice/queue`, { method: 'POST', headers: auth, body: JSON.stringify({ id: 'x', lines: [{ text, voice: 'grandma' }] }) })
    check('routes: POST /voice/queue answers, with nothing queued while the setting is off', q.status === 200 && ((await q.json()) as { queued: number }).queued === 0)
    // a name changed to something that isn't one: the phone's voice, silently, whichever way the text comes
    const bad = 'Buy Coins Now Please'
    writeFileSync(join(data, 'learner-profile.json'), JSON.stringify({ learner: { name: bad, gender: 'male' } }))
    const paid = fe.st.tts.length
    const said = await say(line.said.replace('{n}', bad))
    check("routes: a name that fails the check: 204, the phone's voice, no error", said.status === 204, said.status)
    const own = await say(`Ime mi je ${bad}.`, 'player')
    check("routes: … in the learner's own line too", own.status === 204, own.status)
    const free2 = await say(`Kaj pa zdaj, ${bad}?`)
    check("routes: … in a text the content doesn't have", free2.status === 204, free2.status)
    const redo = await fetch(`${base}/voice/redo`, { method: 'POST', headers: auth, body: JSON.stringify({ text: `Ime mi je ${bad}.`, voice: 'grandma', client_id: 'x' }) })
    check('routes: … in a re-record', redo.status === 503, redo.status)
    const prep2 = (await (await fetch(`${base}/voice/prepare`, { method: 'POST', headers: auth, body: JSON.stringify({ texts: [{ text: `Ime mi je ${bad}.`, voice: 'grandma' }] }) })).json()) as { clips: { url: string | null }[] }
    check('routes: … and in the day\'s audio', prep2.clips[0].url === null, prep2)
    check('routes: nothing of it reached ElevenLabs', fe.st.tts.length === paid, fe.st.tts.slice(paid).map(t => t.body.text))
  } finally {
    await client.close()
    fe.stop()
  }
}

export default async function nameLines(check: Check, port: number) {
  flags(check)
  names(check)
  await cut(check)
  await store(check)
  await gate(check)
  await revoice(check)
  await preloading(check)
  await routes(check, port)
}

if (import.meta.main) {
  if (process.argv.includes('--table')) {
    for (const [id, language] of CULTURES) {
      const lines = catalogueOf(id, language).all()
      const chars = (t: string) => lines.filter(l => l.type === t).reduce((n, l) => n + l.said.length, 0)
      console.log(`${id}: ${lines.length} lines; ${JSON.stringify(count(lines, l => l.type))}; ${JSON.stringify(count(lines, l => l.template))}; characters ${JSON.stringify(Object.fromEntries(LINE_TYPES.map(t => [t, chars(t)])))}`)
    }
    process.exit(0)
  }
  if (process.argv.includes('--cost')) {
    // what eleven_v4_turbo billed in the test: 0.2 credits a character (the name-splice ledger); takes as the default setting
    const CREDITS = 0.2
    const TAKES = nameSettings({}).takes
    const n = (v: number) => Math.round(v).toLocaleString('en-US')
    const NAME = 'Matej'
    for (const scope of ['first', 'A1', 'all'] as Scope[]) {
      console.log(`\n${scope === 'first' ? 'the first scenes (the fire, the tent)' : scope === 'A1' ? 'A1 (scenes at A1, the villagers)' : 'all the content'}:`)
      for (const [id, language] of CULTURES) {
        const men = voicedItems(id, language, 'male', scope)
        const women = voicedItems(id, language, 'female', scope)
        const cat = new NameCatalogue(language, men.map(i => i.text), women.map(i => i.text))
        const named = [...men, ...women].filter(i => i.text.includes(PROBE_NAME))
        const seen = new Set<string>()
        let lines = 0, pieces = 0, wholeLines = 0, wholeChars = 0, asToday = 0
        const clips = new Map<string, number>()
        for (const i of named) {
          const key = `${i.voice}|${i.text}`
          if (seen.has(key)) continue
          seen.add(key)
          const l = cat.find(i.text, PROBE_NAME)
          if (!l) continue
          const said = (t: string) => t.split('{n}').join(NAME).length
          asToday += said(l.said)
          lines++
          if (l.type === 'whole') {
            wholeLines++
            wholeChars += said(l.said)
            continue
          }
          if (l.head || l.tail) pieces += l.said.length - 3 + FILLERS[language].m.length
          clips.set(`${i.voice}|${l.kind}`, said(l.carrier) * TAKES)
        }
        const names = [...clips.values()].reduce((a, b) => a + b, 0)
        const voices = new Set(named.map(i => i.voice)).size
        const row = (c: number) => `${n(c)} chars, ${n(c * CREDITS)} credits`
        console.log(`  ${id}: ${lines} lines in ${voices} voices; once for everyone: ${row(pieces)}; a new name: ${clips.size} clips, ${row(names + wholeChars)} (${wholeLines} lines voiced whole in it); as today per learner: ${row(asToday)}`)
      }
    }
    process.exit(0)
  }
  let failed = 0
  const check: Check = (name, cond, detail) => {
    if (!cond) {
      failed++
      console.log('❌', name, detail ?? '')
    } else if (process.env.VERBOSE) console.log('✅', name)
  }
  await nameLines(check, Number(process.env.LANI_SMOKE_NAMES_PORT ?? 11249))
  console.log(failed ? `${failed} failed` : 'all passed')
  process.exit(failed ? 1 : 0)
}
