// The placement test ("🧭 Uvrstitveni test · Placement test"; companion/COURSE.md "The placement test", the app's
// game/LevelPlacement.kt, docs/plans/05-built/placement.md): the app's short adaptive test from the course's own test
// items, offered at setup and in the school. Its result, once the learner took it or kept their place, comes as
// POST /placement (features/level.ts): persisted as a session via update-db.py, the level raised when the learner took a
// result above their level (never lowered), and the tutor told. Pure: the body, the report and the tutor's line.
import { z } from 'zod'
import { languageParam } from './learner'

/** The CEFR levels, as features/level.ts has them (not imported: level.ts imports this file). */
export const PLACEMENT_LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'] as const
export type PlacementLevel = (typeof PLACEMENT_LEVELS)[number]

const cefr = z
  .string()
  .trim()
  .toUpperCase()
  .pipe(z.enum(PLACEMENT_LEVELS, { error: 'a CEFR level: A1, A2, B1, B2, C1 or C2' }))

const part = z.number().int().min(1).max(9)

/** A probe of a part ("A2 2/3": [level], [part] of [parts]): its items right, almost and of, passed or not. */
const probe = z
  .object({
    level: cefr,
    part,
    parts: part,
    right: z.number().int().min(0).max(100),
    almost: z.number().int().min(0).max(100).default(0),
    of: z.number().int().min(1).max(100),
    passed: z.boolean(),
  })
  .refine(p => p.right + p.almost <= p.of, { message: 'right and almost: at most of', path: ['right'] })
  .refine(p => p.part <= p.parts, { message: 'part: at most parts', path: ['part'] })

/**
 * POST /placement: the test's result. [from]: the learner's level as it began; [level]: the level it suggested;
 * [applied]: the learner took it ("✓ Začnem tukaj · Start here"), else kept their place; [start]: where the school goes on
 * (the first part not known; null past the course's last); [units]: the units it marked done; [probes]: each part asked.
 */
export const placementIn = z
  .object({
    language: languageParam,
    from: cefr,
    level: cefr,
    applied: z.boolean(),
    start: z.object({ level: cefr, part, parts: part }).nullable().optional(),
    units: z.number().int().min(0).max(500).default(0),
    probes: z.array(probe).min(1).max(8),
    child: z.boolean().optional(),
    duration_minutes: z.number().int().min(1).max(240).optional(),
    client_id: z.string().optional(),
  })
  .refine(d => d.applied || d.units === 0, { message: 'units: none marked when the result wasn\'t taken', path: ['units'] })
export type PlacementIn = z.infer<typeof placementIn>

const rank = (l: string) => PLACEMENT_LEVELS.indexOf(l as PlacementLevel)

/** The level rises: the learner took a result above [current]. */
export const raises = (d: PlacementIn, current: string) => d.applied && rank(d.level) > rank(current)

const label = (p: { level: string; part: number; parts: number }) => (p.parts > 1 ? `${p.level} ${p.part}/${p.parts}` : p.level)
const score = (p: { right: number; almost: number }) => p.right + p.almost / 2

/** "A1 2/3 passed 8/8, A2 3/3 not 3/8": each part asked, as asked. */
export const probesSummary = (probes: PlacementIn['probes']) =>
  probes.map(p => `${label(p)} ${p.passed ? 'passed' : 'not'} ${score(p)}/${p.of}`).join(', ')

/** Where the school goes on: "A2 3/3", or past the course's last part. */
const startText = (d: PlacementIn) => (d.start ? label(d.start) : 'after the course\'s last part (every unit done)')

/** The session report of the test: update-db.py persists it; its `level` (only when it [raise]s) sets current_level. */
export function placementPayload(d: PlacementIn, o: { session_id: string; date: string; language?: string; name: string; raise: boolean }) {
  const items = d.probes.reduce((n, p) => n + p.of, 0)
  const right = d.probes.reduce((n, p) => n + p.right, 0)
  const minutes = d.duration_minutes ?? Math.max(1, Math.round(items / 3))
  const kept = d.applied
    ? `they took its result: ${d.units} units marked done in the school, they start at ${startText(d)}`
    : 'they kept their place: nothing changed in the school'
  return {
    session_id: o.session_id,
    date: o.date,
    ...(o.language ? { language: o.language } : {}),
    ...(o.raise ? { level: d.level } : {}),
    duration_minutes: minutes,
    command_used: '/lani-app-placement',
    skills_practiced: ['grammar'],
    skill_scores: { grammar: { exercises: items, correct: right, time_minutes: minutes } },
    milestones: o.raise ? [`Placed at ${d.level} in ${o.name}: the placement test`] : [],
    session_notes: `The placement test in the app${d.child ? ' (a child\'s version)' : ''}, from ${d.from}: ${probesSummary(d.probes)}; it suggested ${d.level}, and ${kept}.`,
  }
}

/** What the tutor hears ([session_id]: the session the bridge persisted already; [current]: the level before). */
export function placementNotice(d: PlacementIn, o: { session_id: string; where: string; current: string; raise: boolean }) {
  const child = d.child ? ' (the child\'s version: tap questions only, A1 to B1)' : ''
  const was = d.from !== o.current ? ` (their profile said ${o.current})` : ''
  const asked = `The learner took the placement test in the app${child} in ${o.where}, from ${d.from}${was}: ${probesSummary(d.probes)} (an almost counts half; a part passes at 75 %). It suggested ${d.level}.`
  const done = `ALREADY persisted by the bridge as ${o.session_id}${o.raise ? ' (update-db.py set current_level and level_since)' : ''}: don't persist it again.`
  if (!d.applied)
    return `${asked} They kept their place: nothing changed in the school, their level stays ${o.current}. ${done} Don't press it; if they ask, the school's "🧭" row offers the test again.`
  const level = o.raise ? `their level is now ${d.level}` : `their level stays ${o.current}`
  return (
    `${asked} They took its result: the school marked ${d.units} units done (tested out, still open to do again) and they start at ${startText(d)}; ${level}. ${done} ` +
    `Welcome them at their level in one short reply (reply tool, conversation main), and plan from there: get_course shows the unit they start with.`
  )
}
