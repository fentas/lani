// The hint chip's shared turns (test/fixtures/hint-lens.json): learner's turns of the dialogs (the right sentence, a wrong
// one, the grammar page it names, the word at the gap) with what the case lens says of the gap's word in the right
// sentence, as GET /lookup's `case` carries it. The app's chip (📖 Namig: game/TurnHint.kt) reads the same trigger table
// as the lens (companion/grammar/sl/data/case-triggers.json) but alone, at a turn and offline; its test
// (TurnHintLensTest) checks that it names the same trigger, case and question as the lens on each of these turns. Here,
// and in the smoke (test/smoke/case-lens.ts), that the lens still answers each turn as the fixture records: a change of
// the lens or the table that answers otherwise shows, and the fixture is made again with --write.
// `bun test/hint-lens.ts` checks it; `bun test/hint-lens.ts --write` makes it from the turns in it.
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { repoLens, VILLAGE_NAMES } from './case-lens-eval'

export const HINT_LENS_FIXTURE = resolve(import.meta.dir, 'fixtures/hint-lens.json')

export type HintTurn = { sl: string; wrong: string; page: string; word: string; nth?: number; note?: string; lens?: unknown }

/** The fixture's turns. */
export function hintTurns(): HintTurn[] {
  return (JSON.parse(readFileSync(HINT_LENS_FIXTURE, 'utf8')) as { turns: HintTurn[] }).turns
}

/** What the lens says of each turn's gap word, as GET /lookup's `case` (null: nothing). */
export function lensAnswers(turns: HintTurn[]): unknown[] {
  const lens = repoLens()
  return turns.map(t => lens.at(t.sl, t.word, t.nth ?? 0, VILLAGE_NAMES))
}

/** The turns the lens answers otherwise than the fixture records: each with what it says now. */
export function hintLensDrift(): { sl: string; word: string; recorded: unknown; now: unknown }[] {
  const turns = hintTurns()
  const now = lensAnswers(turns)
  return turns.flatMap((t, k) => (JSON.stringify(t.lens ?? null) === JSON.stringify(now[k]) ? [] : [{ sl: t.sl, word: t.word, recorded: t.lens ?? null, now: now[k] }]))
}

if (import.meta.main) {
  if (process.argv.includes('--write')) {
    const fx = JSON.parse(readFileSync(HINT_LENS_FIXTURE, 'utf8'))
    const now = lensAnswers(fx.turns)
    fx.turns = fx.turns.map((t: HintTurn, k: number) => ({ ...t, lens: now[k] }))
    writeFileSync(HINT_LENS_FIXTURE, `${JSON.stringify(fx, null, 2)}\n`)
    console.log(`hint-lens: ${fx.turns.length} turns written`)
  } else {
    const drift = hintLensDrift()
    for (const d of drift) console.log(`${d.sl} (${d.word}):\n  recorded ${JSON.stringify(d.recorded)}\n  now      ${JSON.stringify(d.now)}`)
    console.log(drift.length ? `hint-lens: ${drift.length} turns answered otherwise (bun test/hint-lens.ts --write)` : `hint-lens: ${hintTurns().length} turns as recorded`)
    process.exit(drift.length ? 1 : 0)
  }
}
