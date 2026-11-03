// The level-up ("🗺️ Zemljevid zaklada · The treasure map"): the app's treasure hunt tests the end of the learner's level
// and, passed, posts the next level (POST /level). Persisted like a flashcard review: update-db.py sets the language's
// current_level and level_since, no LLM; the tutor hears it and plans the new level.
import { without } from '../tutor-languages'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import type { FeatureFactory } from '../feature'
import { titleEn } from '../grammar'
import { json, readJson } from '../http'
import { languageParam } from '../learner'
import { aimOf, LANGUAGES } from '../learners'
import { pendingOf, setTargetIn, TARGET_LEVELS, type TargetLevel } from '../aim'
import { placementIn, placementNotice, placementPayload, raises } from '../placement'
import { localDate } from '../rhythm'
import { fail, ok } from '../tool'

const instructions = `
Level-ups: the app offers "🗺️ Zemljevid zaklada · The treasure map" once the learner is ready (most of the level's
grammar pages secure in the app, the reviews of the level's cards going well over two weeks, two weeks of practice at
the level). Stari Janez gives it; Napredek's "🗺️ Zemljevid zaklada ›" (the path to the next level: the signs and what's missing, then "Sem pripravljen") starts it too. Its 5-6 stations
test the end of the level; passed, the app posts the new level. level_up: ALREADY persisted by the bridge (update-db.py
set current_level and level_since): don't persist it again. Don't raise the level yourself for a learner who can take
the map: a report's "level" is for a real placement (/lani-setup) or a correction. When they ask "am I ready for A2?",
point them to the map. An older app (no treasure map) keeps the old way: you judge, and a report's "level" sets it.
The target level (learner.target_level; B2 for a new learner, or the level above theirs from B2 on) is where the village's
ages lead: each age needs a share of its words (get_village's aim: the next age and its words). A raised target starts
with the village's next age: the step the village is on keeps the words it asked, so a learner close to an age isn't
handed hundreds more at once (learner.target_level_next {level, from_age}: the app shows "From the next age: B1" on the
age card and the progress screen; the bridge applies it when the village reaches that age). A lower target applies at
once (it never makes a step harder); the current target again takes a waiting raise back. Change it only when the
learner or their parent asks, with set_target_level, never by editing the profile: for a raise it sets the pending
target, not the target. Say what it answers: what the step now and the next one need. The app's setup and its settings
("🎯 Cilj · Aim") set it by the same rule.
`.trim()

export const LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'] as const
export type Level = (typeof LEVELS)[number]
/** What a station tests: a skill of the session report (skill_scores). */
export const STATION_SKILLS = ['reading', 'speaking', 'writing', 'listening', 'vocabulary'] as const

/** [l] as a CEFR level ("a2" → "A2"); undefined for anything else (a template's placeholder). */
export const levelOf = (l: unknown): Level | undefined => {
  const u = typeof l === 'string' ? l.trim().toUpperCase() : ''
  return (LEVELS as readonly string[]).includes(u) ? (u as Level) : undefined
}

const cefr = z
  .string()
  .trim()
  .toUpperCase()
  .pipe(z.enum(LEVELS, { error: 'a CEFR level: A1, A2, B1, B2, C1 or C2' }))

const station = z
  .object({
    id: z.string().regex(/^[a-z_-]{1,24}$/, 'a station id: 1-24 of a-z, _ and -'),
    skill: z.enum(STATION_SKILLS),
    right: z.number().int().min(0).max(100),
    total: z.number().int().min(1).max(100),
    minutes: z.number().int().min(0).max(240).optional(),
  })
  .refine(s => s.right <= s.total, { message: 'right: at most the total', path: ['right'] })

/** POST /level: the level test passed at the end of [from]; [to] is the level right after it. */
export const levelUp = z
  .object({
    from: cefr,
    to: cefr,
    language: languageParam,
    stations: z.array(station).min(1).max(8),
    duration_minutes: z.number().int().min(1).max(240).optional(),
    client_id: z.string().optional(),
  })
  .superRefine((d, ctx) => {
    const next = LEVELS[LEVELS.indexOf(d.from) + 1]
    if (d.to !== next) ctx.addIssue({ code: 'custom', path: ['to'], message: next ? `the level after ${d.from} is ${next}, not ${d.to}` : `${d.from} is the last level: there is none after it` })
  })
export type LevelUp = z.infer<typeof levelUp>

/** "letter 4/5, dialog 5/6": each station's result. */
export const stationsSummary = (stations: LevelUp['stations']) => stations.map(s => `${s.id} ${s.right}/${s.total}`).join(', ')

/** The session report of a passed level test: update-db.py sets [to] as the language's level (and level_since). */
export function levelPayload(d: LevelUp, o: { session_id: string; date: string; language?: string; name: string }) {
  const sum = (xs: number[]) => xs.reduce((a, b) => a + b, 0)
  const skills = [...new Set(d.stations.map(s => s.skill))]
  const skill_scores = Object.fromEntries(
    skills.map(k => {
      const of = d.stations.filter(s => s.skill === k)
      return [k, { exercises: sum(of.map(s => s.total)), correct: sum(of.map(s => s.right)), time_minutes: sum(of.map(s => s.minutes ?? 0)) }]
    }),
  )
  return {
    session_id: o.session_id,
    date: o.date,
    ...(o.language ? { language: o.language } : {}),
    level: d.to,
    duration_minutes: d.duration_minutes ?? Math.max(1, sum(d.stations.map(s => s.minutes ?? 0))),
    command_used: '/lani-app-treasure',
    skills_practiced: skills,
    skill_scores,
    milestones: [`Reached ${d.to} in ${o.name}: the treasure hunt`],
    session_notes: `The treasure hunt in the app: the level test at the end of ${d.from} passed (${stationsSummary(d.stations)}).`,
  }
}

/** POST /learner/target: the aim ("🎯 Cilj · Aim"), a CEFR level. */
const targetIn = z.object({ level: cefr })

export const level: FeatureFactory = ({ cfg, log, learner, channel, outbox, grammar, culture }) => {
  /**
   * The home language's level and aim, as the profile says them: [next], a raise waiting for the village's next age
   * (learner.target_level_next: its level and the age it starts with; aim.ts), null when none waits.
   */
  function aimNow() {
    const l = learner.state().databases?.learner_profile?.learner
    const current = levelOf(l?.current_level) ?? 'A1'
    const n = pendingOf(l)
    return { level: current, target: levelOf(l?.target_level) ?? null, next: n ? { level: n.level, from_age: n.from_age } : null }
  }

  /**
   * POST /learner/target {level}: the aim the app chose ("🎯 Cilj · Aim": the setup, docs/plans/05-built/setup-refresh.md,
   * and the settings), the level the village's ages lead to (the words each age needs). A level at or below the learner's
   * is refused (C2 at C2 is fine). Kept by aim.ts's rule (companion/GAME.md "A target raised later"): a raise waits for the
   * village's next age once there is a village (app/game.json), at once before (the setup); a lower aim, or the current one
   * again (which takes a waiting raise back), at once. Answers the level, the target, the raise waiting and what changed
   * (change: now, next_age or unchanged, already for the last). It writes the learner's data: the lease guards it.
   */
  function handleTarget(body: unknown): Response {
    const r = targetIn.safeParse(body)
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    const now = aimNow()
    const aim = aimOf(now.level, r.data.level) as TargetLevel | undefined
    if (!aim) return json({ error: `an aim above the learner's level (${now.level})`, code: 'not_above' }, 400)
    let change: string
    try {
      const village = existsSync(join(cfg.dataDir, 'app', 'game.json'))
      change = setTargetIn(cfg.dataDir, aim, { fromNextAge: village, today: localDate(new Date()) }).change.mode
    } catch (e) {
      return json({ error: `the aim couldn't be kept: ${(e as Error).message}`, code: 'not_kept' }, 409)
    }
    const after = aimNow()
    if (change === 'unchanged') return json({ ...after, change, already: true })
    log(`aim: ${now.target ?? '-'}${now.next ? ` (${now.next.level} from ${now.next.from_age})` : ''} → ${after.target ?? '-'}${after.next ? ` (${after.next.level} from ${after.next.from_age})` : ''}`)
    return json({ ...after, change })
  }



  async function handleLevel(req: Request): Promise<Response> {
    const r = levelUp.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    const d = r.data
    const other = learner.other(d.language)
    const code = other ?? learner.home()
    const name = LANGUAGES[code] ?? code
    const state = learner.state(other)
    const current = levelOf(state.databases?.learner_profile?.learner?.current_level) ?? 'A1'
    // Sent twice, or the level already raised another way: nothing to do.
    if (LEVELS.indexOf(current) >= LEVELS.indexOf(d.to)) return json({ already: true, level: current, ...(other ? { language: other } : {}) })
    const payload = levelPayload(d, { session_id: state.computed.next_session_id, date: state.computed.today, language: other, name })
    const u = learner.update(payload)
    if (!u.ok) return json({ error: u.output }, 500)
    const was = current !== d.from ? ` (their profile said ${current})` : ''
    const where = other ? `${name} (language "${other}")` : name
    // The grammar book is the village's language: its pages of the new level, for the plan.
    const pages = code === grammar.language ? grammar.all().filter(p => p.level === d.to) : undefined
    const plan = pages
      ? ` then plan ${d.to}: its rules come in as the dialogs meet them and as you introduce them (a module whose exercises name a page, or the page extended with publish_grammar). ${pages.length ? `The grammar book's ${d.to} pages: ${pages.map(p => `${p.id} (${titleEn(p)})`).join(', ')}.` : `The grammar book has no ${d.to} pages yet.`}`
      : ` then plan ${d.to} in ${name}.`
    await channel.notify(
      `The learner found the treasure in the app: they passed the level test at the end of ${d.from}${was} (${stationsSummary(d.stations)}), so their level in ${where} is now ${d.to}. ALREADY persisted by the bridge as ${payload.session_id} (update-db.py set current_level and level_since): don't persist it again. Congratulate them in one short reply (reply tool, conversation main),${plan}`,
      { kind: 'level_up', conversation_id: 'main', msg_id: payload.session_id },
    )
    return json({ session_id: payload.session_id, level: d.to, output: u.output, ...(other ? { language: other } : {}) })
  }

  /**
   * POST /placement: the placement test's result (src/placement.ts; companion/COURSE.md "The placement test"), the
   * learner having taken it or kept their place: persisted via update-db.py as a session `/lani-app-placement`, its
   * `level` the one suggested when the learner took a result above their level (current_level and level_since; never
   * lowered), and the tutor told (`placement`). Once per client_id (the outbox).
   */
  async function handlePlacement(req: Request): Promise<Response> {
    const r = placementIn.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    const d = r.data
    const other = learner.other(d.language)
    const code = other ?? learner.home()
    const name = LANGUAGES[code] ?? code
    const state = learner.state(other)
    const current = levelOf(state.databases?.learner_profile?.learner?.current_level) ?? 'A1'
    const raise = raises(d, current)
    const payload = placementPayload(d, { session_id: state.computed.next_session_id, date: state.computed.today, language: other, name, raise })
    const u = learner.update(payload)
    if (!u.ok) return json({ error: u.output }, 500)
    const where = other ? `${name} (language "${other}")` : name
    await channel.notify(placementNotice(d, { session_id: payload.session_id, where, current, raise }), { kind: 'placement', conversation_id: 'main', msg_id: payload.session_id })
    return json({ session_id: payload.session_id, level: raise ? d.level : current, raised: raise, output: u.output, ...(other ? { language: other } : {}) })
  }

  return {
    instructions: (culture.manifest?.language ?? 'sl') === 'sl' ? instructions : without(instructions, `Stari Janez gives it; Napredek's "🗺️ Zemljevid zaklada ›" (the path to the next level: the signs and what's missing, then "Sem pripravljen") starts it too.`, `A villager gives it; the progress screen's treasure map (the path to the next level: the signs and what's missing, then "I'm ready") starts it too.`),
    routes: [
      { method: 'POST', path: '/level', handle: ({ req, url }) => outbox.once(req, url.pathname, handleLevel) },
      // the placement test's result (the school's "🧭 Uvrstitveni test"): a session, the level raised when taken above it
      { method: 'POST', path: '/placement', handle: ({ req, url }) => outbox.once(req, url.pathname, handlePlacement) },
      // the aim ("🎯 Cilj · Aim"): the learner's level and target_level; the app's setup sets it
      { method: 'GET', path: '/learner/target', handle: () => json(aimNow()) },
      { method: 'POST', path: '/learner/target', handle: async ({ req }) => handleTarget(await readJson(req)) },
    ],
    tools: [
      {
        name: 'set_target_level',
        description:
          "Set the learner's target level (learner.target_level: where the village's ages lead, each age a share of its words; companion/GAME.md \"Ages\"). A raise starts with the village's next age (learner.target_level_next): the step the village is on keeps its words, the new target counts from the next age on, and the bridge applies it when the village gets there. A lower target, or the current one again (which takes a waiting raise back), applies at once. Only when the learner or their parent asks. Answers what changed and what the step now and the next one need.",
        inputSchema: {
          type: 'object',
          properties: { level: { type: 'string', enum: [...TARGET_LEVELS], description: 'The CEFR level to aim at: A1, A2, B1, B2, C1 or C2' } },
          required: ['level'],
        },
        handle: args => {
          const level = TARGET_LEVELS.find(l => l === String(args.level ?? '').trim().toUpperCase())
          if (!level) return fail(`level: one of ${TARGET_LEVELS.join(', ')}`)
          try {
            // the home language's profile: the village's ages follow it
            return ok(setTargetIn(learner.dataDir, level, { fromNextAge: true, today: localDate(new Date()) }).text)
          } catch (e) {
            return fail(`the learner profile can't be changed: ${(e as Error).message}`)
          }
        },
      },
    ],
  }
}
