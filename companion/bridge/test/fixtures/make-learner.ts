// A synthetic learner's data for emulator QA (companion/bin/qa's LANI_DEV_DATA): the six databases as `lani-profile add`
// makes them, with a learner's progress (test/fixtures/learning.ts: cards, mistakes, sessions) and a village (the QA's
// second town's, test/fixtures/dev-town.json, with the people of the learner's culture pack). Nothing of a real learner.
// The "📈 Napredek · Progress" steps run on it: the parent's town of the QA's child town.
// Run: bun test/fixtures/make-learner.ts <dir> [--name Ana] [--target sl] [--base en]   (the dir must not hold a learner)
import { readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { parseArgs } from 'node:util'
import { initLearnerData } from '../../src/learners'
import { localDate } from '../../src/towns'
import { writeLearning } from './learning'

const { values, positionals } = parseArgs({
  args: process.argv.slice(2),
  allowPositionals: true,
  options: { name: { type: 'string', default: 'Ana' }, target: { type: 'string', default: 'sl' }, base: { type: 'string', default: 'en' } },
})
const dir = positionals[0]
if (!dir || !['sl', 'it'].includes(values.target!)) {
  console.error('usage: bun test/fixtures/make-learner.ts <dir> [--name Ana] [--target sl|it] [--base en|de|sl]')
  process.exit(1)
}
const data = resolve(dir)
initLearnerData(resolve(import.meta.dir, '../../../..'), data, { name: values.name!, target: values.target!, base: values.base!, culture: values.target === 'it' ? 'friuli' : 'primorska', child: false })
writeLearning(data, { language: values.target as 'sl' | 'it', base: values.base! })

// a village of some weeks (the second town's buildings and days), its people the culture pack's own ("@day": today)
const culture = values.target === 'it' ? 'friuli' : 'primorska'
const projectDir = resolve(import.meta.dir, '../../../..')
const cast = readdirSync(join(projectDir, 'companion/cultures', culture, 'villagers')).filter(f => f.endsWith('.json')).map(f => f.replace(/\.json$/, '')).sort().slice(0, 6)
const day = (n: number) => localDate(Date.now() + n * 86_400_000)
const days = (v: unknown): unknown =>
  typeof v === 'string' && /^@day(-\d+)?$/.test(v) ? day(-Number(v.slice(5) || 0)) : Array.isArray(v) ? v.map(days) : v && typeof v === 'object' ? Object.fromEntries(Object.entries(v).map(([k, x]) => [k, days(x)])) : v
const fixture = JSON.parse(readFileSync(resolve(import.meta.dir, 'dev-town.json'), 'utf8'))
const state = { ...(days(fixture.state) as Record<string, unknown>), quests: [], chest: { goods: {}, tools: {} }, visitor: null, residents: cast.map((id, i) => ({ id, since: day(-40 + 5 * i) })), villagers: cast.length }
writeFileSync(join(data, 'app/game.json'), JSON.stringify({ rev: 1, state }))
console.log(data)
