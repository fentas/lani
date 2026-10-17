// Makes spot-cycle.json (bun test/fixtures/spot-cycle-make.ts): a cycle and the days the bridge gives it, which the app must give too (SpotCyclesTest).
import { writeFileSync } from 'node:fs'
import { on, rounds, hash, isoOf, dayOf, type CycleSpec } from '../../src/spot-cycles'
const cycle: CycleSpec & Record<string, unknown> = {
  season: { from: '05-01', until: '10-10' },
  start: [0, 9],
  pause: [5, 12],
  stages: [
    { id: 'fell', days: [2, 3], art: 'logs' },
    { id: 'stack', days: [1, 2], art: 'stack' },
    { id: 'cover', days: [1, 1], art: 'covered' },
    { id: 'light', days: [1, 1], art: 'lit', smoke: 'white' } as any,
    { id: 'white', days: [3, 5], art: 'smoke', smoke: 'white' } as any,
    { id: 'yellow', days: [3, 5], art: 'smoke', smoke: 'yellow' } as any,
    { id: 'blue', days: [2, 4], art: 'smoke', smoke: 'blue' } as any,
    { id: 'cool', days: [2, 3], art: 'cooling' },
    { id: 'open', days: [1, 2], art: 'opened' },
    { id: 'sacks', days: [1, 1], art: 'sacks' },
    { id: 'deliver', days: [1, 1], art: 'empty', delivers: true },
  ],
  rest: { id: 'rest', art: 'empty' },
  winter: { id: 'winter', art: 'empty' },
  lost: { id: 'lost', art: 'ash' },
  breakout: { stages: ['white', 'yellow'], chance: 0.6, title: { en: 'Fire!' }, levels: {}, saved: {}, lost: {} },
  delivery: { to: 'tone', days: 14, line: { en: 'The charcoal came.' } },
}
const spot = 'kopa'
const marks = { lost: ['2026-4'] }
const days: unknown[] = []
for (let n = dayOf('2026-01-03'); n <= dayOf('2027-02-01'); n += 3) {
  const d = on(cycle, spot, isoOf(n))
  days.push({ date: d.date, kind: d.kind, id: d.id, day: d.day, days: d.days, round: d.round ?? null })
}
const lost: unknown[] = []
const r1 = rounds(cycle, spot, 2026)[4]
for (let n = r1.spans[0].from; n <= r1.spans[r1.spans.length - 1].until + 2; n++) {
  const d = on(cycle, spot, isoOf(n), marks)
  lost.push({ date: d.date, kind: d.kind, id: d.id, day: d.day, days: d.days, round: d.round ?? null })
}
const out = {
  _note: 'A spot cycle and the days it gives (spot-cycles.ts on, the app\'s SpotCycles.on): the bridge\'s smoke test and the app\'s SpotCyclesTest both want these. Made with the bridge; never edit by hand.',
  spot,
  cycle,
  hashes: [['kopa', 2026, 'start'], ['kopa', 2026, 0, 'fell'], ['oglar', '2026-07-14', 'veter'], ['ščž']].map(p => ({ parts: p, hash: hash(...(p as (string | number)[])) })),
  rounds: [2026, 2027].map(y => rounds(cycle, spot, y).map(r => ({ key: r.key, from: isoOf(r.spans[0].from), until: isoOf(r.spans[r.spans.length - 1].until), breakout: r.breakout !== undefined ? isoOf(r.breakout) : null, delivery: r.delivery !== undefined ? isoOf(r.delivery) : null }))),
  days,
  marks,
  lost,
}
writeFileSync(new URL('./spot-cycle.json', import.meta.url).pathname, JSON.stringify(out, null, 1) + '\n')
console.log(JSON.stringify(out.rounds, null, 1))
