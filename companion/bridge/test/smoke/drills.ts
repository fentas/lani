// Smoke checks: the car's audio drills (lani.drill/v0, drills.ts). The format and what it refuses, the curated
// Slovene drills (companion/drills/sl: every text in English and German, what they name there), the numbers generated
// as the app counts them, serving them (GET /drills), and their texts in the voice corpus (voice-build's "drills" rows).
import { readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { drillProblems, drillTexts, numberWord, numbersOf, validateDrill, type Drill } from '../../src/drills'
import { GrammarStore } from '../../src/grammar'
import { PackStore, packsDirs } from '../../src/packs'
import { VillagerStore } from '../../src/villagers'
import { clipKey, corpus, PRIORITY_LABELS } from '../../src/voice'
import { normalizeText } from '../../src/family'
import { auth, base, check, culturesDir, dataDir } from './harness'
import { JAN } from '../learner-content'

const repo = resolve(import.meta.dir, '../../..')

export default async function drills() {
  // --- the format --------------------------------------------------------------------------------------------------
  const s = (sl: string, en: string) => ({ sl, en })
  const transform = {
    schema: 'lani.drill/v0', id: 'smoke-preobrat', kind: 'transform', emoji: '🔄', title: { sl: 'Preobrat', en: 'Transformations' },
    sets: [{ id: 'past', rule: 'pretekli-cas', level: 'A1', do: { sl: 'V preteklik', en: 'Into the past.' }, items: [
      { from: s('Micka kuha.', 'Micka cooks.'), to: s('Micka je kuhala.', 'Micka cooked.') },
      { from: s('Luka pase ovce.', 'Luka herds the sheep.'), to: s('Luka je pasel ovce.', 'Luka herded the sheep.') },
      { from: s('Dežuje.', "It's raining."), to: s('Deževalo je.', 'It rained.') },
    ] }],
  }
  const ok = validateDrill(transform)
  check('a transformation drill validates: a set of a rule, its instruction, sentences and what they become', ok.ok && ok.drill.kind === 'transform' && ok.drill.language === 'sl' && ok.drill.machine_written === true, ok)
  const refuse = (what: string, d: unknown, why: RegExp) => {
    const v = validateDrill(d)
    check(`refused: ${what}`, !v.ok && why.test(v.errors), v)
  }
  const set0 = transform.sets[0]
  refuse('a kind there is none of', { ...transform, kind: 'quiz' }, /kind/)
  refuse('a key the format lacks', { ...transform, colour: 'red' }, /colour/)
  refuse("a sentence without the drill's language", { ...transform, sets: [{ ...set0, items: [...set0.items.slice(1), { from: { en: 'Micka cooks.' }, to: s('Micka je kuhala.', 'Micka cooked.') }] }] }, /"sl"/)
  refuse('a text without its English', { ...transform, sets: [{ ...set0, items: [...set0.items.slice(1), { from: { sl: 'Micka kuha.' }, to: s('Micka je kuhala.', 'Micka cooked.') }] }] }, /English/)
  refuse('a sentence that doesn\'t change', { ...transform, sets: [{ ...set0, items: [...set0.items.slice(1), { from: s('Micka kuha.', 'x'), to: s('micka kuha', 'x') }] }] }, /doesn't change/)
  refuse('the same answer twice in a set', { ...transform, sets: [{ ...set0, items: [...set0.items, set0.items[0]] }] }, /same answer twice/)
  refuse('a set without a rule of the grammar book', { ...transform, sets: [{ ...set0, rule: undefined }] }, /rule/)
  const rapid = { schema: 'lani.drill/v0', id: 'smoke-hitro', kind: 'rapid', emoji: '⚡', title: { sl: 'Hitro', en: 'Rapid' }, sets: [
    { id: 'ura', level: 'A1', rule: 'kdaj-cas', title: { sl: 'Ura', en: 'Time' }, items: [s('Ura je pol štirih.', "It's half past three.")] },
    { id: 'stevila', level: 'A1', title: { sl: 'Števila', en: 'Numbers' }, numbers: [{ from: 1, to: 3 }, { list: [21, 245] }] },
  ] }
  const r = validateDrill(rapid)
  check('a rapid-fire drill validates: listed answers asked in the bases, numbers generated', r.ok && r.drill.kind === 'rapid' && JSON.stringify(r.drill.sets[1].numbers.flatMap(numbersOf)) === '[1,2,3,21,245]', r)
  refuse('a number twice in a set', { ...rapid, sets: [rapid.sets[0], { ...rapid.sets[1], numbers: [{ from: 1, to: 3 }, { list: [2] }] }] }, /same number twice/)
  refuse('numbers generated in a language other than Slovene', { ...rapid, language: 'it', title: { it: 'Veloce', en: 'Rapid' }, sets: [{ ...rapid.sets[1], title: { it: 'Numeri', en: 'Numbers' } }] }, /Slovene only/)
  refuse('a set with neither items nor numbers', { ...rapid, sets: [{ id: 'x', level: 'A1', title: { sl: 'X', en: 'X' } }] }, /items or numbers/)
  const build = { schema: 'lani.drill/v0', id: 'smoke-gradnja', kind: 'build', emoji: '🧱', title: { sl: 'Gradnja', en: 'Building' }, builds: [
    { id: 'grem', level: 'A1', rules: ['vezniki'], steps: [s('Grem.', "I'm going."), { add: { en: 'to the shop', de: 'in den Laden' }, ...s('Grem v trgovino.', "I'm going to the shop.") }] },
  ] }
  check('a sentence-building drill validates: the first step said whole, each one after what it adds', validateDrill(build).ok, validateDrill(build))
  const b0 = build.builds[0]
  refuse('a step that adds nothing said', { ...build, builds: [{ ...b0, steps: [b0.steps[0], { ...b0.steps[1], add: undefined }] }] }, /"add"/)
  refuse('a first step that adds something', { ...build, builds: [{ ...b0, steps: [{ ...b0.steps[0], add: { en: 'x' } }, b0.steps[1]] }] }, /said whole/)
  refuse('a step that doesn\'t grow the sentence', { ...build, builds: [{ ...b0, steps: [b0.steps[1], { add: { en: 'x' }, ...s('Grem.', "I'm going.") }] }] }, /longer than the one before/)
  refuse('a step with a key that is no language', { ...build, builds: [{ ...b0, steps: [b0.steps[0], { ...b0.steps[1], hint: 'x' }] }] }, /isn't a language code/)
  const riddle = { schema: 'lani.drill/v0', id: 'smoke-uganke', kind: 'riddle', emoji: '🕵️', title: { sl: 'Uganke', en: 'Riddles' }, teller: 'janez', riddles: [
    { id: 'miza', level: 'A1', word: 'v-kuhinji/miza', clues: [s('Iz lesa sem.', "I'm made of wood."), s('Imam štiri noge.', 'I have four legs.')], ask: s('Kaj sem?', 'What am I?'), answer: s('Miza!', 'A table!') },
  ] }
  check("a riddle drill validates: the teller's clues, the question, the answer and the word it is", validateDrill(riddle).ok, validateDrill(riddle))
  refuse('a riddle of a word and a villager at once', { ...riddle, riddles: [{ ...riddle.riddles[0], villager: 'micka' }] }, /not both/)
  refuse('a riddle with one clue', { ...riddle, riddles: [{ ...riddle.riddles[0], clues: [riddle.riddles[0].clues[0]] }] }, /clues/)

  // what a drill names that isn't there is said, not refused
  const refs = {
    grammar: new Set(new GrammarStore(join(repo, 'grammar/sl'), 'sl', join(dataDir, 'unused-grammar-drills')).all().map(p => p.id)),
    packs: new PackStore(packsDirs(join(repo, 'packs'), culturesDir, 'primorska', 'sl'), join(dataDir, 'unused-packs-drills')).all(),
    villagers: new VillagerStore(join(culturesDir, 'primorska/villagers'), join(dataDir, 'unused-villagers-drills')).all(),
  }
  const odd = validateDrill({ ...riddle, teller: 'nobody', riddles: [{ ...riddle.riddles[0], word: 'v-kuhinji/stol' }, { ...riddle.riddles[0], id: 'x', word: 'no-pack/x' }] })
  check('the problems of a drill: a teller not in the cast, a word not in its pack, a pack there is none of', odd.ok && JSON.stringify(drillProblems(odd.drill, refs)) === JSON.stringify(['teller: no villager "nobody"', 'riddle miza: no word "stol" in pack "v-kuhinji"', 'riddle x: no pack "no-pack"']), odd.ok && drillProblems(odd.drill, refs))

  // --- the numbers: as the app counts them (data/SpokenNumbers.kt, its counting form) ---------------------------------
  const counted: Record<number, string> = { 0: 'nič', 1: 'ena', 2: 'dva', 4: 'štiri', 11: 'enajst', 20: 'dvajset', 21: 'enaindvajset', 38: 'osemintrideset', 99: 'devetindevetdeset', 100: 'sto', 101: 'sto ena', 200: 'dvesto', 245: 'dvesto petinštirideset', 999: 'devetsto devetindevetdeset' }
  check('numbers are said as the app counts them (SpokenNumbers): enaindvajset, dvesto petinštirideset …; none out of range or in another language', Object.entries(counted).every(([n, w]) => numberWord(Number(n), 'sl') === w) && numberWord(1000, 'sl') === undefined && numberWord(5, 'it') === undefined, Object.keys(counted).map(n => [n, numberWord(Number(n), 'sl')]))

  // --- the curated drills ------------------------------------------------------------------------------------------
  const dir = join(repo, 'drills/sl')
  const files = readdirSync(dir).filter(f => f.endsWith('.json')).sort()
  const results = files.map(f => ({ f, v: validateDrill(JSON.parse(readFileSync(join(dir, f), 'utf8'))) }))
  check('every curated drill validates, is Slovene and in the file of its id', results.every(r => r.v.ok && r.v.drill.language === 'sl' && `${r.v.drill.id}.json` === r.f), results.filter(r => !r.v.ok).map(r => [r.f, (r.v as { errors: string }).errors]))
  const curated = results.flatMap(r => (r.v.ok ? [r.v.drill] : []))
  const of = <K extends Drill['kind']>(k: K) => curated.filter((d): d is Extract<Drill, { kind: K }> => d.kind === k)
  check('the four kinds, one drill each: transformations, rapid fire, sentence building, riddles', JSON.stringify(curated.map(d => d.kind).sort()) === JSON.stringify(['build', 'rapid', 'riddle', 'transform']), curated.map(d => d.kind))
  check('each is machine-written and says who checked it', curated.every(d => d.machine_written && d.review?.includes('not reviewed by a native speaker')))
  check('each names only pages, pack words and villagers there are', curated.every(d => drillProblems(d, refs).length === 0), curated.map(d => [d.id, drillProblems(d, refs)]))
  // every text Slovene, English and German; what the learner hears in their base (instructions, cues) English and German
  const texts: Record<string, string>[] = []
  const cues: Record<string, string>[] = [...curated.map(d => d.title)]
  for (const d of curated) {
    if (d.kind === 'transform') for (const st of d.sets) (cues.push(st.do), st.items.forEach(i => (texts.push(i.from, i.to), i.do && cues.push(i.do))))
    else if (d.kind === 'rapid') for (const st of d.sets) (cues.push(st.title), texts.push(...st.items))
    else if (d.kind === 'build') for (const b of d.builds) b.steps.forEach(x => (texts.push(x as Record<string, string>), x.add && cues.push(x.add)))
    else for (const x of d.riddles) texts.push(...x.clues, x.ask, x.answer)
  }
  const lacks = [...texts.filter(t => !t.sl || !t.en || !t.de), ...cues.filter(t => !t.en || !t.de)]
  check(`every Slovene text in English and German, every instruction and cue in both (${texts.length} texts)`, lacks.length === 0, lacks.slice(0, 10))
  // the car speaks the texts: no phrase separators (voice.ts phrases splits "/" and "→") in what is voiced
  check('nothing voiced has a phrase separator (/ →) or a placeholder', texts.every(t => !/[/→{]/.test(t.sl)), texts.filter(t => /[/→{]/.test(t.sl)).map(t => t.sl))

  const [pre] = of('transform')
  const a1 = pre.sets.filter(x => x.level === 'A1')
  const firstA2 = pre.sets.findIndex(x => x.level !== 'A1')
  check(
    'transformations: the A1 sets first, 150 items or more at A1, the rules asked for (the past, the future, the dual, numbers with nouns, the no with the genitive, the accusative, ti → vi)',
    a1.flatMap(x => x.items).length >= 150 && (firstA2 < 0 || pre.sets.slice(firstA2).every(x => x.level !== 'A1')) &&
      ['pretekli-cas', 'prihodnjik', 'dvojina', 'stevila-samostalniki', 'rodilnik-nikalnica', 'tozilnik', 'ti-vi'].every(rule => a1.some(x => x.rule === rule)),
    pre.sets.map(x => [x.id, x.rule, x.level, x.items.length]),
  )
  const stej = pre.sets.find(x => x.rule === 'stevila-samostalniki')!
  check('… counting: ena krava → dve kravi → tri krave → pet krav, each with the number to say', ['Dve kravi.', 'Tri krave.', 'Pet krav.'].every(t => stej.items.some(i => i.to.sl === t && !!i.do?.en)), stej.items.slice(0, 3))
  const [hitro] = of('rapid')
  const numbers = hitro.sets.flatMap(x => x.numbers.flatMap(numbersOf))
  check('rapid fire: the numbers 1 to 100 first, then some up to 999; the clock, days, months, dates', JSON.stringify(numbers.slice(0, 100)) === JSON.stringify(Array.from({ length: 100 }, (_, i) => i + 1)) && numbers.slice(100).every(n => n > 100 && n <= 999) && ['Ura je pol štirih.', 'ob petih', 'v ponedeljek', 'januarja', 'prvega maja'].every(t => hitro.sets.some(x => x.items.some(i => i.sl === t))), numbers.length)
  const [gradnja] = of('build')
  check('sentence building: 60 builds or more at A1, of 3 or 4 steps', gradnja.builds.filter(b => b.level === 'A1').length >= 60 && gradnja.builds.every(b => b.steps.length >= 3 && b.steps.length <= 4), gradnja.builds.length)
  const [uganke] = of('riddle')
  check("riddles: 40 or more, told by Stari Janez, each a pack word or a villager", uganke.teller === 'janez' && uganke.riddles.length >= 40 && uganke.riddles.every(x => !!x.word || !!x.villager), uganke.riddles.length)

  // --- served -------------------------------------------------------------------------------------------------------
  const served = await (await fetch(`${base}/drills`, { headers: auth })).json()
  check('GET /drills serves the curated drills of the village\'s language', Array.isArray(served) && served.length === curated.length && served.every((d: any) => d.source === 'curated' && d.language === 'sl'), served?.map?.((d: any) => d.id))
  const one = await fetch(`${base}/drills/uganke`, { headers: auth })
  const none = await fetch(`${base}/drills/no-such-drill`, { headers: auth })
  check('GET /drills/:id serves one; 404 for none; not without the token', one.status === 200 && (await one.json()).riddles?.length === uganke.riddles.length && none.status === 404 && (await fetch(`${base}/drills`)).status === 401)

  // --- the voice corpus --------------------------------------------------------------------------------------------
  const all = drillTexts(curated, 'sl')
  check('drillTexts: every sentence and answer, the numbers generated, a riddle\'s texts its teller\'s', all.some(t => t.text === 'enaindvajset') && all.some(t => t.text === 'Micka je kuhala kosilo.' && !t.teller) && all.every(t => (t.source === 'drill:uganke') === (t.teller === 'janez')) && drillTexts(curated, 'it').length === 0, all.length)
  const scene = { id: 'smoke-drill-scene', language: 'sl', objects: [], people: [], dialogs: [{ id: 'd', lines: [{ choices: [{ sl: 'Ura je pol štirih.', en: 'x', ok: true }] }] }] } as any
  const items = corpus({ learner: JAN, packs: [], modules: [], scenarios: [], villagers: refs.villagers, scenes: [scene], drills: curated, ownVoice: id => id === 'janez' })
  const at = (t: string, voice = 'female') => items.find(i => i.text === t && i.voice === voice)
  const [pA1, pScenes, pA2] = ['drills A1', 'scenes', 'drills A2+'].map(l => PRIORITY_LABELS.indexOf(l))
  const clue = uganke.riddles[0].clues[0].sl
  const a2 = pre.sets.find(x => x.level !== 'A1')?.items[0].to.sl
  check(
    "corpus: the A1 drills before the scenes (a text of both is the drill's), the others after the A2 tellings; the narrator's voice, a riddle in Stari Janez's own",
    pA1 >= 0 && pA1 < pScenes && pA2 > PRIORITY_LABELS.indexOf('stories A2') && pA2 < PRIORITY_LABELS.indexOf('stories B1+') &&
      at('Ura je pol štirih.')?.priority === pA1 && at('Ura je pol štirih.')?.source === 'drill:hitri-odziv' && at('enaindvajset')?.priority === pA1 &&
      at(clue, '@janez')?.source === 'drill:uganke' && !at(clue) && (!a2 || at(a2)?.priority === pA2) &&
      clipKey(normalizeText('Kaj sem?'), '@janez') === '@janez:kaj sem',
    { a1: at('Ura je pol štirih.'), clue: items.find(i => i.text === clue), a2: a2 && at(a2) },
  )
  const shared = corpus({ learner: JAN, packs: [], modules: [], scenarios: [], villagers: refs.villagers, drills: curated })
  check('… without a voice of his own, the riddles in his speaker (grandpa)', shared.find(i => i.text === clue)?.voice === 'grandpa')
}
