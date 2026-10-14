// The school's course (course.ts, companion/COURSE.md): the course for the app, the tutor's homework for its units, and
// where the learner is in it (the village state's course).
import { badNote, fail, noteIn, ok } from '../channel'
import { grammarNamed, LEVELS, validateWorksheet, type Homework, type ServedCourse, type Unit } from '../course'
import type { FeatureFactory } from '../feature'
import { titleFor, type GrammarExample, type GrammarPage } from '../grammar'
import { HttpError, json, type Route } from '../http'
import { textIn } from '../langs'
import { learnerLangs } from '../learners'
import { isPrintBase, type PrintGender, type PrintWhat } from '../prints'
import type { ModuleSpec } from '../spec'
import { corpus } from '../voice'

const instructions = `
The school (companion/COURSE.md; GAME.md "The school"): the class begins with Zaselek (right after the camp), nothing
to build or pay: Mojca comes over from the next village to teach, by the fire and under the linden once it stands,
until the school (built from Zaselek on) brings her to live here. The course goes level by level: A1, then A2 once A1's units are done (the school opens it), and each upgrade of the school
needs the course level before it done. A unit is a guided intro (a quick question after each step), 2–4 worksheets
(the module types and the school's own gaptext, table, transform and sort) and a test. get_course shows where Jan is.
A session_end whose data has course is a sheet of the school ({language, unit, level, sheet: a worksheet id, "test" or a
homework id, kind: worksheet | test | homework, right, almost, of, passed?, unit_done?, tested_out?}): persist it as any
session_end (lani-db-updater). When course.unit_done is true, congratulate in one short line and consider homework on
what Jan got wrong (the results): publish_worksheet into that unit. Load lani-studio first; never write course files
directly.
`.trim()

/** A sheet's best try in the village state (GameState.course: units[…].sheets[id], units[…].test). */
type Try = { right?: number; almost?: number; of?: number; day?: string; tries?: number }
/** A unit's place in the village state, by "<language>/<unit id>" (companion/COURSE.md, "The learner's progress"). */
type UnitProgress = { started?: string; intro?: number; introDone?: boolean; sheets?: Record<string, Try>; test?: Try; done?: string | null; testedOut?: boolean }
type CourseProgress = { classroom?: string | null; opened?: Record<string, string>; units?: Record<string, UnitProgress> }

const count = (v: unknown) => (Number.isInteger(v) && (v as number) > 0 ? (v as number) : 0)
/** A try as "right/of" ("6/8, 1 almost"). */
const score = (t: Try | undefined) => (t && count(t.of) ? `${count(t.right)}/${count(t.of)}${count(t.almost) ? `, ${count(t.almost)} almost` : ''}` : undefined)
const obj = <T>(v: unknown): T | undefined => (v && typeof v === 'object' && !Array.isArray(v) ? (v as T) : undefined)

/** The module types of a sheet's exercises (and its intro's questions), as a module for the voice: what the app plays. */
function voiced(ws: Homework): { modules: ModuleSpec[]; grammar: Pick<GrammarPage, 'id' | 'language' | 'examples'>[] } {
  const own = new Set(['gaptext', 'table', 'transform', 'sort'])
  const asks = (ws.intro?.steps ?? []).flatMap(s => (s.ask ? [s.ask] : []))
  const exercises = [...asks, ...ws.exercises.filter(e => !own.has(e.type))] as ModuleSpec['exercises']
  const id = `course-${ws.unit}-${ws.id}`
  const examples = (ws.intro?.steps ?? []).flatMap(s => s.examples) as GrammarExample[]
  return {
    modules: exercises.length ? [{ schema: 'lani.module/v0', id, title: id, level: 'A1', targets: [], tags: [], exercises }] : [],
    grammar: examples.length ? [{ id, language: ws.language, examples }] : [],
  }
}

export const course: FeatureFactory = ({ course: store, prints, grammar, game, events, voice, cfg, culture, addressee }) => {
  const langs = () => learnerLangs(cfg.dataDir, culture.manifest?.language ?? 'sl')
  const own = store.language

  /**
   * A printed sheet or booklet (companion/COURSE.md, "Printed sheets") for this bridge's learner: in their base language
   * (English where the sheets have none), with their name and their gender's forms; ?base=en|de|it, ?learner=<name> and
   * ?gender=m|f|both say otherwise (both: "utrujen/utrujena", as the generator's default).
   */
  function printed(what: PrintWhat, url: URL): Response {
    const q = url.searchParams
    const who = addressee()
    const asked = q.get('base')
    if (asked !== null && !isPrintBase(asked)) throw new HttpError(400, 'base: en, de or it')
    const theirs = langs().base
    const base = asked ?? (isPrintBase(theirs) ? theirs : 'en')
    const gender = (q.get('gender') ?? (who.gender === 'female' ? 'f' : 'm')) as PrintGender
    if (!['m', 'f', 'both'].includes(gender)) throw new HttpError(400, 'gender: m, f or both')
    const learner = (q.get('learner') ?? who.name).trim().slice(0, 40) || who.name
    const doc = prints.doc(what, { base, learner, gender })
    if (!doc) return json({ error: what.kind === 'sheet' ? `no unit "${what.unit}" of the course to print` : `no units of level ${what.level} to print` }, 404)
    return new Response(doc.html, {
      headers: {
        'content-type': 'text/html; charset=utf-8',
        'cache-control': 'no-store',
        // what the app names the print job after, and how many units it holds
        'x-lani-print-name': doc.name,
        'x-lani-print-units': String(doc.sheets.length),
      },
    })
  }
  const printRoutes: Route[] = [
    { method: 'GET', path: /^\/course\/sheet\/([a-z0-9][a-z0-9-]{0,63})$/, handle: ({ url, params }) => printed({ kind: 'sheet', unit: params[0] }, url) },
    { method: 'GET', path: /^\/course\/booklet\/([A-C][12])$/, handle: ({ url, params }) => printed({ kind: 'booklet', level: params[0] }, url) },
  ]

  /** Where the learner is in the course: the served course and the village state's course. */
  function whereNow(c: ServedCourse | undefined) {
    const state = obj<Record<string, unknown>>(game.read()?.state) ?? {}
    const p = obj<CourseProgress>(state.course) ?? {}
    const opened = obj<Record<string, string>>(p.opened) ?? {}
    const units = obj<Record<string, UnitProgress>>(p.units) ?? {}
    const school = (Array.isArray(state.buildings) ? state.buildings : []).find((b: any) => b?.type === 'SCHOOL') as { level?: number } | undefined
    const head = { language: own, classroom: typeof p.classroom === 'string' ? p.classroom : null, school: school ? count(school.level) || 1 : null }
    if (!c) return { ...head, course: null, note: `no course in this village's language yet (companion/course/${own}/course.json)` }
    const files = new Map<string, Unit & { homework: Homework[] }>(c.units.map(u => [u.id, u]))
    let next: { level: string; id: string; title: string } | null = null
    const levels = c.levels.map(l => {
      const openOn = opened[`${own}/${l.level}`]
      const rows = l.units.map(o => {
        const u = files.get(o.id)
        if (!u) return { id: o.id, title: textIn(o.title, 'en'), planned: true }
        const at = obj<UnitProgress>(units[`${own}/${u.id}`]) ?? {}
        const sheets = obj<Record<string, Try>>(at.sheets) ?? {}
        const done = typeof at.done === 'string' ? at.done : null
        const test = obj<Try>(at.test)
        if (!next && openOn && !done) next = { level: l.level, id: u.id, title: textIn(u.title, 'en') }
        const best = Object.fromEntries(Object.entries(sheets).flatMap(([id, t]) => (score(obj<Try>(t)) ? [[id, score(obj<Try>(t))]] : [])))
        return {
          id: u.id,
          title: textIn(u.title, 'en'),
          done,
          ...(at.testedOut ? { tested_out: true } : {}),
          ...(at.started ? { started: at.started } : {}),
          intro: `${Math.min(count(at.intro), u.intro.steps.length)}/${u.intro.steps.length}`,
          intro_done: !!at.introDone,
          sheets: `${u.worksheets.filter(w => sheets[w.id]).length}/${u.worksheets.length}`,
          ...(Object.keys(best).length ? { best } : {}),
          test: { pass: u.test.pass, ...(test ? { best: score(test), tries: count(test.tries) } : { tries: 0 }) },
          homework: u.homework.map(h => h.id),
        }
      })
      const written = rows.filter(r => !('planned' in r))
      return { level: l.level, open: !!openOn, ...(openOn ? { opened: openOn } : {}), units_done: `${written.filter(r => 'done' in r && r.done).length}/${written.length}`, units: rows }
    })
    return { ...head, levels, next }
  }

  return {
    instructions,
    routes: [
      {
        method: 'GET',
        path: '/course',
        addressed: true,
        handle: () => {
          const c = store.served()
          return c ? json(c) : json({ error: 'no course' }, 404)
        },
      },
      ...printRoutes,
    ],
    tools: [
      {
        name: 'publish_worksheet',
        description:
          "Validate a lani.worksheet/v0 sheet of homework for a unit of the school's course and publish it to the app: it shows in the unit under \"📝 Domača naloga · Homework\", the newest first (companion/COURSE.md). unit: a unit of the course that isn't planned (get_course); id: kebab-case, not one of the unit's own worksheets (publishing the same id again replaces it); exercises: 1–8, the module types and the school's gaptext, table, transform, sort. Returns validation errors instead of publishing if invalid. Load the lani-studio skill before using. Say the learner {learner} and what agrees with them {m:…|f:…} (companion/SCENES.md, the learner in the content).",
        inputSchema: {
          type: 'object',
          properties: {
            worksheet: { type: 'object', description: 'The homework (lani.worksheet/v0)' },
            note: { type: 'string', description: 'One-line announcement shown in the app' },
          },
          required: ['worksheet'],
        },
        handle: args => {
          const input = args.worksheet && typeof args.worksheet === 'object' && !Array.isArray(args.worksheet) ? { language: own, ...(args.worksheet as object) } : args.worksheet
          const v = validateWorksheet(input)
          if (!v.ok) return fail(`worksheet invalid, nothing published:\n${v.errors}`)
          const note = noteIn.safeParse(args.note)
          if (!note.success) return badNote()
          const ws = v.worksheet
          const r = store.publish(ws)
          if ('error' in r) return fail(`nothing published: ${r.error}`)
          const { target, base } = langs()
          events.emit({ type: 'worksheet_published', unit: ws.unit, id: ws.id, title: titleFor(ws, target, base), note: note.data ?? undefined })
          // what the app plays aloud: the intro's examples and the module types' lines
          voice.enqueue(corpus({ packs: [], scenarios: [], ...voiced(ws), language: own, learner: addressee() }))
          // a page the book doesn't have: said back, not refused (the page may come next)
          const pages = new Set(grammar.all().map(p => p.id))
          const missing = grammarNamed(ws).filter(g => !pages.has(g))
          const warn = missing.length ? `\nNote: no grammar page ${missing.map(id => `"${id}"`).join(', ')} yet (list_grammar; publish_grammar)` : ''
          return ok(`${r.replaced ? 'replaced' : 'published'} homework ${ws.unit}/${ws.id} (${ws.exercises.length} exercises)${warn}`)
        },
      },
      {
        name: 'remove_worksheet',
        description: "Remove homework you published from a unit of the school's course.",
        inputSchema: {
          type: 'object',
          properties: { unit: { type: 'string', description: 'The unit id' }, id: { type: 'string', description: 'The homework id' } },
          required: ['unit', 'id'],
        },
        handle: args => {
          const unit = typeof args.unit === 'string' ? args.unit : ''
          const id = typeof args.id === 'string' ? args.id : ''
          if (!store.remove(unit, id)) return fail(`no homework of yours "${unit}/${id}" to remove (get_course lists each unit's homework)`)
          events.emit({ type: 'worksheet_removed', unit, id })
          return ok(`removed homework ${unit}/${id}`)
        },
      },
      {
        name: 'get_course',
        description:
          `The school's course in this village's language and where Jan is in it: the classroom (the day the class began, with Zaselek; null: not yet, or a village whose school stood first), the school's level (null: not built), and per level (${LEVELS.slice(0, 4).join(', ')} …) whether it is open and its units done; per unit its intro (steps done/of, intro_done), its worksheets done/of and the best try of each sheet (right/of, almost), the test (pass mark, best try, tries), the day it was done (tested_out: passed without the worksheets) and your homework ids; next: the first unit to do. Planned units are listed, not playable yet.`,
        inputSchema: { type: 'object', properties: {} },
        handle: () => ok(JSON.stringify(whereNow(store.served()), null, 2)),
      },
    ],
  }
}
