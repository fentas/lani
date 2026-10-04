// Smoke checks: the daily rhythm schedule and set_rhythm.
import { mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { RhythmStore } from '../../src/rhythm'
import { check, client } from './harness'

export default async function rhythm() {
  // daily rhythm: pure scheduling rules, and the settings tool
  {
    const { dueRoutines, rhythmSettings } = await import('../../src/rhythm')
    const st = (last = {}, patch = {}) => ({ settings: rhythmSettings.parse(patch), last })
    const d = (h: number, m = 0, day = 3) => new Date(2026, 8, 20 + day, h, m) // 2026-09-23 is a Wednesday
    check('morning fires at 07:30', JSON.stringify(dueRoutines(d(7, 31), st())) === '["morning"]')
    check('not before its time', dueRoutines(d(7, 0), st()).length === 0)
    check('only once a day', dueRoutines(d(8, 0), st({ morning: '2026-09-23' })).length === 0)
    check('a restart catches up within the window', dueRoutines(d(11, 0), st()).includes('morning'))
    check('too late is skipped', !dueRoutines(d(12, 0), st()).includes('morning'))
    check('weekly only on its day', dueRoutines(d(19, 5, 0), st()).includes('weekly') && !dueRoutines(d(19, 5), st()).includes('weekly'))
    check('off means off', dueRoutines(d(7, 31), st({}, { enabled: false })).length === 0)
    check('a routine can be switched off', dueRoutines(d(20, 45), st({}, { evening: null })).length === 0)
  }
  const rs = await client.callTool({ name: 'set_rhythm', arguments: { morning: '08:00', evening: null } })
  check('set_rhythm updates settings', JSON.stringify(rs).includes('08:00') && JSON.stringify(rs).includes('null'), rs)
  check('set_rhythm rejects bad times', (await client.callTool({ name: 'set_rhythm', arguments: { morning: '25:99' } })).isError)
  const corrupt = join(mkdtempSync(join(tmpdir(), 'lani-rhythm-')), 'rhythm.json')
  writeFileSync(corrupt, '{"settings": {"morning": 7')
  check('a corrupt rhythm.json reads as the defaults (the scheduler must not throw)', new RhythmStore(corrupt).read().settings.morning === '07:30')
}
