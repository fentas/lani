// Smoke checks: each language's curated practice as data (docs/plans/05-built/practice-it.md): its modules
// (companion/modules/<language>, Slovene's in companion/modules) and its car drills (companion/drills/<language>). A language
// with files is held to this; German's and English's come in by adding their files, nothing else.
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { drillProblems, drillTexts, numbersOf, validateDrill, type Drill } from '../../src/drills'
import { GrammarStore } from '../../src/grammar'
import { PackStore, packsDirs } from '../../src/packs'
import { validateCuratedModule } from '../../src/store'
import { VillagerStore } from '../../src/villagers'
import { check, culturesDir, dataDir } from './harness'

const repo = resolve(import.meta.dir, '../../..')
/**
 * The languages whose practice is written per base: the culture that plays them, the bases a module's texts are in, the
 * bases every drill text is in ([drillBases]), the levels its modules reach, and a number its rapid fire says as the corpus
 * has it ([counted]).
 */
const LANGUAGES = [
  { language: 'it', culture: 'friuli', bases: ['de', 'sl', 'en'], drillBases: ['en', 'de'], levels: ['A1', 'A2', 'B1'], counted: 'ventitré' },
  { language: 'de', culture: 'kaernten', bases: ['sl', 'en', 'it'], drillBases: ['en', 'sl', 'it'], levels: ['A1', 'A2', 'B1', 'B2'], counted: 'dreiundzwanzig' },
]
const LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']
const json = (dir: string, f: string) => JSON.parse(readFileSync(join(dir, f), 'utf8'))
const files = (dir: string) => (existsSync(dir) ? readdirSync(dir).filter(f => f.endsWith('.json')).sort() : [])

export default async function practice() {
  for (const { language, culture, bases, drillBases, levels, counted } of LANGUAGES) {
    const pages = new GrammarStore(join(repo, 'grammar', language), language, join(dataDir, `unused-grammar-practice-${language}`)).all()
    const pageIds = new Set(pages.map(p => p.id))
    const cast = new VillagerStore(join(culturesDir, culture, 'villagers'), join(dataDir, `unused-villagers-practice-${language}`)).all()
    const packs = new PackStore(packsDirs(join(repo, 'packs'), culturesDir, culture, language), join(dataDir, `unused-packs-practice-${language}`)).all()

    // --- the modules ------------------------------------------------------------------------------------------------
    const mDir = join(repo, 'modules', language)
    const raw = files(mDir).map(f => ({ f, input: json(mDir, f) }))
    const each = raw.map(({ f, input }) => ({ f, input, v: bases.map(b => validateCuratedModule(input, b)) }))
    const bad = each.filter(m => m.v.some(v => !v.ok))
    check(`modules/${language}: every module validates in each base (${raw.length}) and is in the file of its id`, raw.length > 0 && bad.length === 0 && each.every(m => `${m.input.id}.json` === m.f), bad.map(m => [m.f, m.v.find(v => !v.ok)]))
    const mods = each.flatMap(m => (m.v[0].ok ? [m.v[0].spec] : []))
    check(`modules/${language}: every exercise names a page of the grammar book`, mods.every(m => m.exercises.every(e => !!e.grammar && pageIds.has(e.grammar))), mods.flatMap(m => m.exercises.filter(e => !e.grammar || !pageIds.has(e.grammar)).map(e => [m.id, e.grammar])))
    const untexted = (v: unknown, at: string, out: string[]) => {
      if (Array.isArray(v)) v.forEach((x, i) => untexted(x, `${at}.${i}`, out))
      else if (v && typeof v === 'object') for (const [k, x] of Object.entries(v)) {
        if (['explain', 'instruction', 'description', 'story', 'title'].includes(k) && (typeof x === 'string' || bases.some(b => !(x as any)?.[b]))) out.push(`${at}.${k}`)
        else untexted(x, `${at}.${k}`, out)
      }
      return out
    }
    const notPerBase = raw.flatMap(({ f, input }) => untexted(input, f, []))
    check(`modules/${language}: what the learner reads is in each base (${bases.join(', ')})`, notPerBase.length === 0, notPerBase.slice(0, 12))
    check(`modules/${language}: each has what Slovene's have: a run's kinds, a translation to say and to type`, mods.every(m => ['speak', 'translate'].every(t => m.exercises.some(e => e.type === t)) && m.exercises.some(e => ['cloze', 'reorder', 'multi'].includes(e.type))), mods.map(m => [m.id, m.exercises.map(e => e.type)]))
    for (const level of levels) {
      const at = mods.filter(m => m.level === level)
      const named = new Set(at.flatMap(m => m.exercises.flatMap(e => (e.grammar ? [e.grammar] : []))))
      check(`modules/${language}: ${level} has modules (${at.length}), naming pages of its level or below`, at.length >= 3 && [...named].every(id => LEVELS.indexOf(pages.find(p => p.id === id)!.level) <= LEVELS.indexOf(level) + 1), at.map(m => m.id))
    }
    check(`modules/${language}: a quest's giver is someone of the cast`, mods.every(m => !m.quest || cast.some(v => v.name === m.quest!.giver)), mods.filter(m => m.quest && !cast.some(v => v.name === m.quest!.giver)).map(m => [m.id, m.quest!.giver]))
    check(`modules/${language}: machine-written and saying so`, raw.every(({ input }) => /machine-written/.test(input.review ?? '')))
    const orders = levels.map(l => raw.filter(({ input }) => input.level === l).map(({ input }) => input.order).sort((a: number, b: number) => a - b))
    check(`modules/${language}: each has its place in its level (order 1, 2, 3 …: the pace, ModuleStore.offered)`, orders.every(o => o.every((n: number, i: number) => n === i + 1)), orders)
    const tokens = mods.flatMap(m => m.exercises.flatMap(e => (e.type === 'reorder' ? [...e.tokens, ...(e.distractors ?? [])] : [])))
    check(`modules/${language}: no placeholder in a sentence's chips`, tokens.every(t => !t.includes('{')), tokens.filter(t => t.includes('{')))

    // --- the drills -------------------------------------------------------------------------------------------------
    const dDir = join(repo, 'drills', language)
    const results = files(dDir).map(f => ({ f, v: validateDrill(json(dDir, f)) }))
    check(`drills/${language}: every drill validates, is of its language and in the file of its id`, results.length > 0 && results.every(r => r.v.ok && r.v.drill.language === language && `${r.v.drill.id}.json` === r.f), results.filter(r => !r.v.ok).map(r => [r.f, (r.v as any).errors]))
    const drills = results.flatMap(r => (r.v.ok ? [r.v.drill] : []))
    const of = <K extends Drill['kind']>(k: K) => drills.filter((d): d is Extract<Drill, { kind: K }> => d.kind === k)
    check(`drills/${language}: the four kinds, one drill each`, JSON.stringify(drills.map(d => d.kind).sort()) === JSON.stringify(['build', 'rapid', 'riddle', 'transform']), drills.map(d => d.kind))
    const refs = { grammar: pageIds, packs, villagers: cast }
    check(`drills/${language}: each names only pages, pack words and villagers there are`, drills.every(d => drillProblems(d, refs).length === 0), drills.map(d => [d.id, drillProblems(d, refs)]))
    check(`drills/${language}: machine-written and saying so`, drills.every(d => d.machine_written && d.review?.includes('not reviewed by a native speaker')))
    const texts: Record<string, string>[] = []
    const cues: Record<string, string>[] = [...drills.map(d => d.title)]
    for (const d of drills) {
      if (d.kind === 'transform') for (const st of d.sets) (cues.push(st.do), st.items.forEach(i => (texts.push(i.from, i.to), i.do && cues.push(i.do))))
      else if (d.kind === 'rapid') for (const st of d.sets) (cues.push(st.title), texts.push(...st.items))
      else if (d.kind === 'build') for (const b of d.builds) b.steps.forEach(x => (texts.push(x as Record<string, string>), x.add && cues.push(x.add)))
      else for (const x of d.riddles) texts.push(...x.clues, x.ask, x.answer)
    }
    const lacks = [...texts.filter(t => !t[language] || drillBases.some(b => !t[b])), ...cues.filter(t => drillBases.some(b => !t[b]))]
    check(`drills/${language}: every text in ${drillBases.join(', ')}, every instruction and cue too (${texts.length} texts)`, lacks.length === 0, lacks.slice(0, 10))
    check(`drills/${language}: nothing voiced has a phrase separator (/ →) or a placeholder`, texts.every(t => !/[/→{]/.test(t[language])), texts.filter(t => /[/→{]/.test(t[language])).map(t => t[language]))
    const [turn] = of('transform')
    const ordered = turn.sets.map(s => LEVELS.indexOf(s.level))
    const size = turn.sets.flatMap(s => s.items).length
    check(`drills/${language}: transformations the size of Slovene's (${size}), the easiest sets first, one a level at least`, size >= 200 && ordered.every((l, i) => i === 0 || l >= ordered[i - 1]) && levels.every(l => turn.sets.some(s => s.level === l)), turn.sets.map(s => [s.id, s.level, s.items.length]))
    const [fast] = of('rapid')
    const numbers = fast.sets.flatMap(s => s.numbers.flatMap(numbersOf))
    check(`drills/${language}: rapid fire's numbers 1 to 100 first, then some up to 999; the clock, days, months, dates`, JSON.stringify(numbers.slice(0, 100)) === JSON.stringify(Array.from({ length: 100 }, (_, i) => i + 1)) && numbers.some(n => n > 100) && fast.sets.filter(s => s.items.length > 0).length >= 5, fast.sets.map(s => [s.id, s.items.length]))
    const [grow] = of('build')
    check(`drills/${language}: sentence building the size of Slovene's (${grow.builds.length}), of 3 or 4 steps`, grow.builds.length >= 60 && grow.builds.every(b => b.steps.length >= 3 && b.steps.length <= 4), grow.builds.filter(b => b.steps.length < 3 || b.steps.length > 4).map(b => b.id))
    const [guess] = of('riddle')
    check(`drills/${language}: riddles (${guess.riddles.length}), told by someone of the cast, each a pack word or a villager`, guess.riddles.length >= 40 && cast.some(v => v.id === guess.teller) && guess.riddles.every(x => !!x.word || !!x.villager))
    const said = drillTexts(drills, language)
    check(`drills/${language}: in the voice corpus, the numbers in their words`, said.length > 500 && said.some(t => t.text === counted) && said.every(t => t.source.startsWith('drill:')), said.length)
  }
}
