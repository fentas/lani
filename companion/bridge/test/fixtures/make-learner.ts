// A synthetic learner's data for emulator QA (companion/bin/qa's LANI_DEV_DATA): the six databases as `lani-profile add`
// makes them, with a learner's progress (test/fixtures/learning.ts: cards, mistakes, sessions) and a village (the QA's
// second town's, test/fixtures/dev-town.json, with the people of the learner's culture pack). Nothing of a real learner.
// The "📈 Napredek · Progress" steps run on it: the parent's town of the QA's child town.
// Run: bun test/fixtures/make-learner.ts <dir> [--name Ana] [--target sl] [--base en] [--level B2]   (the dir must not hold a learner)
// The village is the target language's culture pack's: sl Primorska, it Friuli, de Kärnten, en the Lake District.
// --level: the learner's level (A1 by default); above A1 the language's curated modules of the levels below count as played
// (a done request each, a month ago), so the pace (ModuleStore.offered) has the level's own first to offer.
import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { parseArgs } from 'node:util'
import { initLearnerData } from '../../src/learners'
import { localDate } from '../../src/towns'
import { type Language, writeLearning } from './learning'

const { values, positionals } = parseArgs({
  args: process.argv.slice(2),
  allowPositionals: true,
  options: { name: { type: 'string', default: 'Ana' }, target: { type: 'string', default: 'sl' }, base: { type: 'string', default: 'en' }, level: { type: 'string', default: 'A1' } },
})
const dir = positionals[0]
const CULTURES: Record<Language, string> = { sl: 'primorska', it: 'friuli', de: 'kaernten', en: 'lakeland' }
const LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']
if (!dir || !(values.target! in CULTURES) || !LEVELS.includes(values.level!)) {
  console.error('usage: bun test/fixtures/make-learner.ts <dir> [--name Ana] [--target sl|it|de|en] [--base en|de|sl|it] [--level A1|A2|B1|B2|C1|C2]')
  process.exit(1)
}
const data = resolve(dir)
initLearnerData(resolve(import.meta.dir, '../../../..'), data, { name: values.name!, target: values.target!, base: values.base!, culture: CULTURES[values.target as Language], child: false, level: values.level! })
writeLearning(data, { language: values.target as Language, base: values.base! })
if (values.level !== 'A1') {
  // the learning fixture writes an A1 learner aiming at A2: this one is at --level, aiming one higher
  const path = join(data, 'learner-profile.json')
  const profile = JSON.parse(readFileSync(path, 'utf8'))
  const at = LEVELS.indexOf(values.level!)
  profile.learner = { ...profile.learner, current_level: values.level, target_level: LEVELS[Math.min(at + 1, LEVELS.length - 1)] }
  writeFileSync(path, JSON.stringify(profile, null, 2) + '\n')
}

// a village of some weeks (the second town's buildings and days), its people the culture pack's own ("@day": today)
const culture = CULTURES[values.target as Language]
const projectDir = resolve(import.meta.dir, '../../../..')
const cast = readdirSync(join(projectDir, 'companion/cultures', culture, 'villagers')).filter(f => f.endsWith('.json')).map(f => f.replace(/\.json$/, '')).sort().slice(0, 6)
const day = (n: number) => localDate(Date.now() + n * 86_400_000)
const days = (v: unknown): unknown =>
  typeof v === 'string' && /^@day(-\d+)?$/.test(v) ? day(-Number(v.slice(5) || 0)) : Array.isArray(v) ? v.map(days) : v && typeof v === 'object' ? Object.fromEntries(Object.entries(v).map(([k, x]) => [k, days(x)])) : v
const fixture = JSON.parse(readFileSync(resolve(import.meta.dir, 'dev-town.json'), 'utf8'))
// the curated modules below the learner's level, played a month ago (Slovene's in companion/modules, the others' in modules/<language>;
// a language without curated modules has none)
const modulesDir = join(projectDir, 'companion/modules', values.target === 'sl' ? '' : values.target!)
const below = (existsSync(modulesDir) ? readdirSync(modulesDir) : []).filter(f => f.endsWith('.json')).map(f => JSON.parse(readFileSync(join(modulesDir, f), 'utf8')))
  .filter(m => m.quest && LEVELS.indexOf(m.level) < LEVELS.indexOf(values.level!))
const played = below.map(m => ({ id: `tutor-${m.id}-v1`, moduleId: m.id, giver: m.quest.giver, source: 'TUTOR', since: day(-30), done: true }))
const state = { ...(days(fixture.state) as Record<string, unknown>), quests: played, chest: { goods: {}, tools: {} }, visitor: null, residents: cast.map((id, i) => ({ id, since: day(-40 + 5 * i) })), villagers: cast.length }
writeFileSync(join(data, 'app/game.json'), JSON.stringify({ rev: 1, state }))
console.log(data)
