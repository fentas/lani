// The level-up ("🗺️ Zemljevid zaklada · The treasure map"): the app's treasure hunt tests the end of the learner's level
// and, passed, posts the next level (POST /level). Persisted like a flashcard review: update-db.py sets the language's
// current_level and level_since, no LLM; the tutor hears it and plans the new level.
import { readFileSync, renameSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import type { FeatureFactory } from '../feature'
import { titleEn } from '../grammar'
import { json, readJson } from '../http'
import { languageParam } from '../learner'
import { aimOf, LANGUAGES } from '../learners'

const instructions = `
Level-ups: the app offers "🗺️ Zemljevid zaklada · The treasure map" once the learner is ready (most of the level's
grammar pages secure in the app, the reviews of the level's cards going well over two weeks, two weeks of practice at
the level). Stari Janez gives it; Napredek's "🗺️ Zemljevid zaklada ›" (the path to the next level: the signs and what's missing, then "Sem pripravljen") starts it too. Its 5-6 stations
test the end of the level; passed, the app posts the new level. level_up: ALREADY persisted by the bridge (update-db.py
set current_level and level_since): don't persist it again. Don't raise the level yourself for a learner who can take
the map: a report's "level" is for a real placement (/lani-setup) or a correction. When they ask "am I ready for A2?",
point them to the map. An older app (no treasure map) keeps the old way: you judge, and a report's "level" sets it.
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

/**
 * The aim in [dataDir]'s learner profile: `learner.target_level` = [aim], everything else as it is (read just before,
 * written beside it and renamed over it). Throws when there's no profile.
 */
export function writeTarget(dataDir: string, aim: string) {
  const path = join(dataDir, 'learner-profile.json')
  const p = JSON.parse(readFileSync(path, 'utf8'))
  if (!p?.learner || typeof p.learner !== 'object') throw new Error("learner-profile.json has no learner")
  p.learner.target_level = aim
  const tmp = `${path}.target.tmp`
  writeFileSync(tmp, JSON.stringify(p, null, 2) + '\n')
  renameSync(tmp, path)
}

export const level: FeatureFactory = ({ cfg, log, learner, channel, outbox, grammar }) => {
  /** The home language's level and aim, as the profile says them. */
  function aimNow() {
    const l = learner.state().databases?.learner_profile?.learner
    const current = levelOf(l?.current_level) ?? 'A1'
    return { level: current, target: levelOf(l?.target_level) ?? null }
  }

  /**
   * POST /learner/target {level}: the aim the app's setup chose ("🎯 Cilj · Aim", docs/plans/05-built/setup-refresh.md),
   * kept as the profile's target_level at once: the village's ages follow it (the words each age needs). A level at or
   * below the learner's is refused (C2 at C2 is fine). It writes the learner's data: the lease guards it.
   */
  function handleTarget(body: unknown): Response {
    const r = targetIn.safeParse(body)
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    const now = aimNow()
    const aim = aimOf(now.level, r.data.level)
    if (!aim) return json({ error: `an aim above the learner's level (${now.level})`, code: 'not_above' }, 400)
    if (aim === now.target) return json({ ...now, already: true })
    try {
      writeTarget(cfg.dataDir, aim)
    } catch (e) {
      return json({ error: `the aim couldn't be kept: ${(e as Error).message}`, code: 'not_kept' }, 409)
    }
    log(`aim: ${now.target ?? '-'} → ${aim}`)
    return json({ level: now.level, target: aim })
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

  return {
    instructions,
    routes: [
      { method: 'POST', path: '/level', handle: ({ req, url }) => outbox.once(req, url.pathname, handleLevel) },
      // the aim ("🎯 Cilj · Aim"): the learner's level and target_level; the app's setup sets it
      { method: 'GET', path: '/learner/target', handle: () => json(aimNow()) },
      { method: 'POST', path: '/learner/target', handle: async ({ req }) => handleTarget(await readJson(req)) },
    ],
  }
}
