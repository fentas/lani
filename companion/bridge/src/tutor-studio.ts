// The app tutor's studio on the bridge (docs/plans/05-built/studio.md; plan 5, phase C), beside its session
// (features/tutor.ts):
//
// - the file tools, over the learner's data folder only: str_replace_based_edit_tool (Anthropic's text editor,
//   text_editor_20250728: view, create, str_replace, insert) and search. Paths are relative to the data folder; no "..",
//   no links, nothing private (keys, tokens, the lease, the inbox, the jobs …). Writes only in the tutor's content areas,
//   each checked by its kind's validator before anything is written: a refusal is the validator's message, and the model
//   fixes and tries again, as with the publish tools. The six databases are read-only (record_session and card write them).
// - POST /tutor/check {kind?, path?, text}: the same check, for a draft.
// - the studio's jobs (app/tutor/jobs/<id>.json: the app's long content jobs, saved step by step so a killed app
//   resumes), the notebook's versions, the reports (app/reports.json) and the local overrides of curated content
//   (overrides.ts).
//
// It runs on a node and in the phone's bundle alike: only node:fs, node:path and node:crypto (the phone's shims).
import { existsSync, lstatSync, mkdirSync, readdirSync, readFileSync, realpathSync, statSync, unlinkSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { z } from 'zod'
import { residentIds, validateArrival } from './arrivals'
import { validateCard } from './cards'
import { validateWorksheet } from './course'
import type { Ctx } from './feature'
import { validateGrammar } from './grammar'
import { HttpError, json, readJson, type Route } from './http'
import { labelShown } from './langs'
import { learnerLangs, LANGUAGES } from './learners'
import { OVERRIDE_KINDS, type OverrideEntry, type OverrideKind, type Overrides } from './overrides'
import { validatePack } from './packs'
import { writeAtomic } from './pairing'
import { checkReading, readingSpec } from './readings'
import { validateScenario } from './scenarios'
import { cultureProjects } from './cultures'
import { validateScene } from './scenes'
import { validateModule } from './spec'
import { validateStory } from './stories'
import { fail, ok, type Tool } from './tool'
import { speakerErrors, validateVillager } from './villagers'

// --- paths ------------------------------------------------------------------------------------------------------------

/** What the file tools never read or write: secrets, the channel's and the app's runtime state, caches, backups. */
const PRIVATE: RegExp[] = [
  /^\.git(\/|$)/, /(^|\/)[^/]*\.pem$/, /(^|\/)[^/]*\.key$/, /(^|\/)keys\.env$/, /(^|\/)lani\.env$/, /(^|\/)\.env[^/]*$/,
  /^app\/bridge-token$/, /^app\/family-token$/, /^app\/pairing(\/|$)/, /^app\/town-invites(\/|$)/, /^app\/voice(\/|$)/,
  /^app\/release(\/|$)/, /\.apk$/, /^app\/client-ids\.json$/, /^app\/devices\.json$/, /^app\/devices-seen\.json$/,
  /^app\/events\.json$/, /^app\/channel-queue\.json$/, /^app\/answers\.json/, /^app\/dev(\/|$)/, /^app\/chat-images(\/|$)/,
  /^app\/audio(\/|$)/, /^app\/family(\/|$)/, /^app\/lease\.json$/, /^app\/tutor-inbox\.json$/, /^app\/tutor\/jobs(\/|$)/,
  /^\.backups(\/|$)/, /\.tmp$/,
]
export const isPrivate = (rel: string) => PRIVATE.some(p => p.test(rel))

/** The six databases at the data folder's top: the file tools only read them. */
const DATABASES = new Set(['learner-profile.json', 'progress-db.json', 'mistakes-db.json', 'mastery-db.json', 'spaced-repetition.json', 'session-log.json'])

/** A path the model gave, inside the data folder: [rel] from its root ("" the root itself), [abs] on disk. */
export type DataPath = { rel: string; abs: string }

/**
 * [raw] (the model's path: relative to the learner's data folder, a leading "/" or the folder's own absolute path
 * stripped) as a path inside [dataDir], or why not: NUL, "..", "~", "%", a backslash, or a link anywhere on the way.
 */
export function dataPath(dataDir: string, raw: unknown): DataPath | { error: string } {
  if (typeof raw !== 'string' || !raw.trim()) return { error: 'path: required (relative to the learner\'s data folder, e.g. "app/stories/my-story.json")' }
  let p = raw.trim()
  if (/[\0\\%]/.test(p)) return { error: `path "${raw}": no NUL, backslashes or "%" (paths are plain, relative to the data folder)` }
  let root: string
  try {
    root = realpathSync(dataDir)
  } catch {
    return { error: 'the data folder is not there' }
  }
  for (const prefix of [root, resolve(dataDir)]) if (p === prefix || p.startsWith(`${prefix}/`)) p = p.slice(prefix.length)
  p = p.replace(/^\/+/, '')
  const parts = p.split('/').filter(s => s !== '' && s !== '.')
  if (parts.some(s => s === '..')) return { error: `path "${raw}": no ".." (the file tools stay in the data folder)` }
  if (parts.some(s => s.startsWith('~'))) return { error: `path "${raw}": no "~" (paths are relative to the data folder)` }
  const rel = parts.join('/')
  const abs = rel ? join(root, ...parts) : root
  // no links: none of the path's parts that exist may be one, and the deepest that exists is where it says it is
  let deepest = root
  for (let i = 1; i <= parts.length; i++) {
    const at = join(root, ...parts.slice(0, i))
    let st: ReturnType<typeof lstatSync> | undefined
    try {
      st = lstatSync(at, { throwIfNoEntry: false })
    } catch {
      st = undefined
    }
    if (!st) break
    if (st.isSymbolicLink()) return { error: `path "${raw}": ${parts.slice(0, i).join('/')} is a link (the file tools follow none)` }
    deepest = at
  }
  try {
    if (realpathSync(deepest) !== deepest) return { error: `path "${raw}": it leads out of the data folder (a link)` }
  } catch {
    return { error: `path "${raw}": it doesn't resolve` }
  }
  return { rel, abs }
}

// --- the content areas and their checks --------------------------------------------------------------------------

/** What a check says: the kind, the item's id, and the validator's errors (none: it may be written) or notes. */
export type Checked = { ok: boolean; kind: string; id?: string; errors?: string; notes?: string[] }

const STEM = '([a-z0-9][a-z0-9_-]{0,62})'
const MODULE_ID = '([a-z0-9][a-z0-9-]{1,62})'
/** The tutor's content areas: where the file tools may write, and what each file there is. */
const AREAS: { kind: string; re: RegExp }[] = [
  { kind: 'module', re: new RegExp(`^app/modules/${MODULE_ID}/v([1-9]\\d{0,5})\\.json$`) },
  { kind: 'module-current', re: new RegExp(`^app/modules/${MODULE_ID}/current\\.json$`) },
  { kind: 'pack', re: new RegExp(`^app/packs/${STEM}\\.json$`) },
  { kind: 'scene', re: new RegExp(`^app/scenes/${STEM}\\.json$`) },
  { kind: 'story', re: new RegExp(`^app/stories/${STEM}\\.json$`) },
  { kind: 'reading', re: new RegExp(`^app/readings/${STEM}\\.json$`) },
  { kind: 'villager', re: new RegExp(`^app/villagers/${STEM}\\.json$`) },
  { kind: 'arrival', re: new RegExp(`^app/arrivals/${STEM}\\.json$`) },
  { kind: 'grammar', re: new RegExp(`^app/grammar/${STEM}\\.json$`) },
  { kind: 'card', re: new RegExp(`^app/cards/${STEM}\\.json$`) },
  { kind: 'worksheet', re: new RegExp(`^app/course/${STEM}/${STEM}\\.json$`) },
  { kind: 'scenario', re: new RegExp(`^app/scenarios/${STEM}\\.json$`) },
]
export const CONTENT_KINDS = [...new Set(AREAS.map(a => a.kind))]
const AREAS_SAID =
  'app/modules/<id>/v<N>.json and current.json, app/packs/, app/scenes/, app/stories/, app/readings/, app/villagers/, app/arrivals/, app/grammar/, app/cards/, app/course/<unit>/, app/scenarios/ (<id>.json each)'

/** The kind of file [rel] is, with its path's parts, or why the file tools may not write it. */
export function areaOf(rel: string): { kind: string; m: RegExpExecArray } | { refused: string } {
  if (isPrivate(rel)) return { refused: `${rel} is private: the file tools don't touch it` }
  if (DATABASES.has(rel)) return { refused: `${rel} is one of the learner's six databases: read-only here (record_session and card write them; SM-2 is the app's arithmetic)` }
  for (const a of AREAS) {
    const m = a.re.exec(rel)
    if (m) return { kind: a.kind, m }
  }
  return { refused: `${rel || '/'} is not a file the tutor writes. The content areas: ${AREAS_SAID}. Glosses, sentence notes, dialog variants and the notebook have their own tools` }
}

const parsed = (text: string): { value?: unknown; error?: string } => {
  try {
    return { value: JSON.parse(text) }
  } catch (e) {
    return { error: `not JSON: ${(e as Error).message}` }
  }
}
const obj = (v: unknown): Record<string, any> | undefined => (v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, any>) : undefined)
const errs = (list: string[]) => list.map(e => (e.startsWith('✖') ? e : `✖ ${e}`)).join('\n')
const belongs = (kind: string, id: string, rel: string, want: string) => (id === want ? [] : [`${kind} "${id}" belongs in ${rel.replace(/[^/]+\.json$/, `${id}.json`)} (the file is named for its id)`])

/**
 * Checks [text] as the file [rel] of kind [kind] (from the path when not given), as the publish tools and the stores
 * check that kind: the schema, the references (packs, the cast, scenarios, the village's projects, its residents), the
 * village's language, a curated id where the kind may not take one, the file named for its id.
 */
export function checkContent(ctx: Ctx, rel: string | undefined, text: string, kind?: string): Checked {
  const area = rel !== undefined ? areaOf(rel) : undefined
  if (area && 'refused' in area && !kind) return { ok: false, kind: 'none', errors: area.refused }
  const k = kind ?? (area && 'kind' in area ? area.kind : undefined)
  if (!k || !CONTENT_KINDS.includes(k)) return { ok: false, kind: k ?? 'none', errors: `kind: one of ${CONTENT_KINDS.join(', ')} (or a path in a content area)` }
  const m = area && 'm' in area && area.kind === k ? area.m : undefined
  const p = parsed(text)
  if (p.error) return { ok: false, kind: k, errors: `${rel ?? k}: ${p.error}` }
  const input = p.value
  const language = ctx.culture.manifest?.language ?? 'sl'
  const lang = () => learnerLangs(ctx.cfg.dataDir, language)
  const out = (id: string | undefined, problems: string[], notes: string[] = []): Checked =>
    problems.length ? { ok: false, kind: k, id, errors: errs(problems) } : { ok: true, kind: k, id, ...(notes.length ? { notes } : {}) }
  const rejected = (errors: string) => ({ ok: false, kind: k, errors } as Checked)
  switch (k) {
    case 'module': {
      const v = validateModule(input)
      if (!v.ok) return rejected(v.errors)
      const problems: string[] = []
      if (m && v.spec.id !== m[1]) problems.push(`id "${v.spec.id}": this file is module ${m[1]}'s (app/modules/${v.spec.id}/… is its own)`)
      const version = obj(input)?.version
      if (m && version !== Number(m[2])) problems.push(`version: the file v${m[2]}.json holds "version": ${m[2]} (it has ${JSON.stringify(version ?? null)}); publish_module numbers the versions itself`)
      return out(v.spec.id, problems)
    }
    case 'module-current': {
      const v = obj(input)?.version
      if (!Number.isInteger(v) || v < 1) return out(m?.[1], ['current.json is {"version": N}: the version the app plays'])
      if (m && !existsSync(join(ctx.cfg.appDir, 'modules', m[1], `v${v}.json`))) return out(m[1], [`version ${v}: app/modules/${m[1]}/v${v}.json isn't there`])
      return out(m?.[1], [])
    }
    case 'pack': {
      const v = validatePack(input)
      if (!v.ok) return rejected(v.errors)
      return out(v.pack.id, [...(m ? belongs('pack', v.pack.id, rel!, m[1]) : []), ...(ctx.packs.isCurated(v.pack.id) ? [`"${v.pack.id}" is a curated pack; pick another id (a wrong curated word: report_problem, and card for the learner's card of it)`] : [])])
    }
    case 'scene': {
      const refs = { packs: ctx.packs.all(), scenarios: ctx.scenarios.all(), villagers: ctx.villagers.all(), projects: cultureProjects(ctx.culture.dir, ctx.culture.id) }
      const v = validateScene(input, refs)
      if (!v.ok) return rejected(v.errors)
      return out(v.scene.id, m ? belongs('scene', v.scene.id, rel!, m[1]) : [], ctx.scenes.isCurated(v.scene.id) ? [`it replaces the curated scene "${v.scene.id}" (a local override: it retires when the curated one changes)`] : [])
    }
    case 'story': {
      const v = validateStory(input, { packs: ctx.packs.all(), villagers: ctx.villagers.all() })
      if (!v.ok) return rejected(v.errors)
      const problems = [...(m ? belongs('story', v.story.id, rel!, m[1]) : [])]
      if (v.story.language !== language) problems.push(`language: the village speaks "${language}", the story is in "${v.story.language}"`)
      return out(v.story.id, problems, ctx.stories.isCurated(v.story.id) ? [`it replaces the curated story "${v.story.id}" (a local override: it retires when the curated one changes)`] : [])
    }
    case 'reading': {
      const v = readingSpec.safeParse(input)
      if (!v.success) return rejected(`reading invalid:\n${z.prettifyError(v.error)}`)
      const r = v.data
      const { base } = lang()
      const problems = [...(m ? belongs('reading', r.id, rel!, m[1]) : []), ...checkReading(r, language, 'reading', ctx.villagers.all()).map(p => p.replace(/^reading: /, ''))]
      if (!(language in r.title)) problems.push(`title: no "${language}" text (the village's language)`)
      if (!(base in r.title)) problems.push(`title: no "${base}" text (the learner reads the translations in ${LANGUAGES[base] ?? base}); every text needs it`)
      if (ctx.readings.isCurated(r.id)) problems.push(`id: "${r.id}" is the culture pack's reading; pick another id`)
      return out(r.id, problems)
    }
    case 'villager': {
      const v = validateVillager(input)
      if (!v.ok) return rejected(v.errors)
      return out(v.villager.id, [...(m ? belongs('villager', v.villager.id, rel!, m[1]) : []), ...speakerErrors(v.villager)], ctx.villagers.isCurated(v.villager.id) ? [`it replaces the curated villager "${v.villager.id}" (a local override: it retires when the curated one changes)`] : [])
    }
    case 'arrival': {
      const r = residentIds(ctx.game.read())
      const who = r && [...new Set([...r, ...ctx.villagers.all().map(v => v.id)])]
      const v = validateArrival(input, { language, people: who })
      if (!v.ok) return rejected(v.errors)
      const problems = [...(m ? belongs('arrival', v.arrival.id, rel!, m[1]) : [])]
      if (who && !who.includes(v.arrival.id)) problems.push(`id: "${v.arrival.id}" doesn't live in the village (nor is of the cast)`)
      return out(v.arrival.id, problems)
    }
    case 'grammar': {
      const v = validateGrammar(input)
      if (!v.ok) return rejected(v.errors)
      const problems = [...(m ? belongs('grammar page', v.page.id, rel!, m[1]) : [])]
      if (v.page.language !== ctx.grammar.language) problems.push(`language: this village's grammar book is in "${ctx.grammar.language}", the page is "${v.page.language}"`)
      return out(v.page.id, problems, ctx.grammar.curated().some(p => p.id === v.page.id) ? [`it extends the curated page "${v.page.id}"`] : [])
    }
    case 'card': {
      const v = validateCard(input)
      if (!v.ok) return rejected(v.errors)
      const problems = [...(m ? belongs('card', v.card.id, rel!, m[1]) : [])]
      if (v.card.language !== ctx.cards.language) problems.push(`language: this cheat sheet is in "${ctx.cards.language}", the card is "${v.card.language}"`)
      if (ctx.cards.curated().some(c => c.id === v.card.id)) problems.push(`"${v.card.id}" is a curated card's id: give yours another`)
      return out(v.card.id, problems)
    }
    case 'worksheet': {
      const v = validateWorksheet(input)
      if (!v.ok) return rejected(v.errors)
      const ws = v.worksheet
      const problems: string[] = []
      if (m && (ws.unit !== m[1] || ws.id !== m[2])) problems.push(`homework "${ws.unit}/${ws.id}" belongs in app/course/${ws.unit}/${ws.id}.json`)
      if (ws.language !== ctx.course.language) problems.push(`language: this course is "${ctx.course.language}", the homework is "${ws.language}"`)
      return out(ws.id, problems)
    }
    case 'scenario': {
      const v = validateScenario(input)
      if (!v.ok) return rejected(v.errors)
      return out(v.scenario.id, [...(m ? belongs('scenario', v.scenario.id, rel!, m[1]) : []), ...(ctx.scenarios.isCurated(v.scenario.id) ? [`"${v.scenario.id}" is a curated scenario; pick another id`] : [])])
    }
  }
  return { ok: false, kind: k, errors: `no check for ${k}` }
}

/** The app event a write of [kind] tells the app with (as its publish tool does), so the app reloads it. */
function publishedEvent(ctx: Ctx, kind: string, id: string | undefined, value: Record<string, any> | undefined, rel: string): Record<string, unknown> | undefined {
  if (!id || !value) return undefined
  const language = ctx.culture.manifest?.language ?? 'sl'
  const { target, base } = learnerLangs(ctx.cfg.dataDir, language)
  const title = (l: unknown) => labelShown(l as any, target, base) || id
  switch (kind) {
    case 'module':
      return { type: 'module_published', id, version: value.version, title: value.title }
    case 'module-current':
      return { type: 'module_published', id, version: value.version }
    case 'pack':
      return { type: 'pack_published', id, title: title(value.title), emoji: value.emoji }
    case 'scene':
      return { type: 'scene_published', id, title: title(value.title), emoji: value.emoji }
    case 'story':
      return { type: 'story_published', id, title: title(value.title), emoji: value.emoji }
    case 'reading':
      return { type: 'reading_published', id, title: title(value.title), level: value.level }
    case 'villager':
      return { type: 'villager_published', id, name: value.name, emoji: value.emoji }
    case 'arrival':
      return { type: 'arrival_published', id }
    case 'grammar':
      return { type: 'grammar_published', id, title: title(value.title), emoji: value.emoji }
    case 'card':
      return { type: 'card_published', id, title: title(value.title), emoji: value.emoji }
    case 'worksheet':
      return { type: 'worksheet_published', unit: value.unit, id, title: title(value.title) }
    case 'scenario':
      return { type: 'scenario_published', id, title: value.title, emoji: value.emoji }
  }
  return { type: `${kind}_published`, id, file: rel }
}

/** The override kind of a content kind (the four whose tutor item replaces a curated one), or undefined. */
const overrideKind = (kind: string): OverrideKind | undefined => (kind === 'module-current' ? 'module' : (OVERRIDE_KINDS as readonly string[]).includes(kind) ? (kind as OverrideKind) : undefined)

// --- the file tools ------------------------------------------------------------------------------------------------

/** A view's result is cut here; a file over [MAX_FILE] isn't read whole. */
export const VIEW_MAX = 16_000
const MAX_FILE = 2 * 1024 * 1024
/** Old texts kept per file under app/tutor/file-history/. */
const HISTORY_KEPT = 5

const isBinary = (b: Uint8Array) => b.subarray(0, 8000).includes(0)

/** What the file tools tell the caller after a write (features/tutor.ts checks the overrides, logs). */
export type Written = { rel: string; kind: string; id?: string }

/**
 * The file tools (the app session's: never in Claude Code's list): str_replace_based_edit_tool, the text editor's
 * commands, and search. [after]: a write went through.
 */
export function fileTools(ctx: Ctx, specOf: (name: string, fallback: { description: string; inputSchema: Record<string, unknown> }) => { description: string; inputSchema: Record<string, unknown> }, after: (w: Written) => void): Tool[] {
  const { cfg, log } = ctx
  const where = (raw: unknown) => dataPath(cfg.dataDir, raw)

  const readText = (abs: string, rel: string): string | { error: string } => {
    let st
    try {
      st = statSync(abs)
    } catch {
      return { error: `no such file or directory: ${rel || '/'}` }
    }
    if (st.isDirectory()) return { error: `${rel || '/'} is a directory` }
    if (st.size > MAX_FILE) return { error: `${rel} is ${Math.round(st.size / 1024)} KB: too big to read whole (view a range, or search it)` }
    const bytes = readFileSync(abs)
    if (isBinary(bytes)) return { error: `${rel} is not a text file` }
    return bytes.toString('utf8')
  }

  const history = (rel: string, abs: string) => {
    if (!existsSync(abs)) return
    const dir = join(cfg.appDir, 'tutor', 'file-history')
    const base = rel.replace(/\//g, '__')
    mkdirSync(dir, { recursive: true })
    writeFileSync(join(dir, `${base}.${Date.now()}`), readFileSync(abs))
    const old = readdirSync(dir).filter(f => f.startsWith(`${base}.`)).sort()
    for (const f of old.slice(0, Math.max(0, old.length - HISTORY_KEPT))) unlinkSync(join(dir, f))
  }

  /** Checks [text] as [rel] and writes it; the tool's answer. */
  const write = (rel: string, abs: string, text: string, what: string) => {
    const area = areaOf(rel)
    if ('refused' in area) return fail(`Nothing written. ${area.refused}`)
    const c = checkContent(ctx, rel, text)
    if (!c.ok) return fail(`Nothing written: ${rel} doesn't validate as a ${c.kind}:\n${c.errors}`)
    history(rel, abs)
    mkdirSync(dirname(abs), { recursive: true })
    writeAtomic(abs, text)
    log(`tutor: ${what} ${rel} (${c.kind}${c.id ? ` ${c.id}` : ''}), checked`)
    try {
      after({ rel, kind: c.kind, id: c.id })
    } catch (e) {
      log(`tutor: after ${rel}: ${(e as Error).message}`)
    }
    const ev = publishedEvent(ctx, c.kind, c.id, obj(JSON.parse(text)), rel)
    if (ev && ctx.events) ctx.events.emit(ev as any)
    return ok(`${what} ${rel}: a ${c.kind}${c.id ? ` (${c.id})` : ''}, checked by its validator; the app has it${c.notes?.length ? `\nNote: ${c.notes.join('; ')}` : ''}`)
  }

  const view = (rel: string, abs: string, range: unknown) => {
    if (isPrivate(rel)) return fail(`${rel} is private: the file tools don't read it`)
    let st
    try {
      st = statSync(abs)
    } catch {
      return fail(`no such file or directory: ${rel || '/'}`)
    }
    if (st.isDirectory()) {
      const lines: string[] = []
      const walk = (dir: string, prefix: string, depth: number) => {
        for (const name of readdirSync(dir).sort()) {
          const r = prefix ? `${prefix}/${name}` : name
          if (isPrivate(r)) continue
          let isDir = false
          try {
            const ls = lstatSync(join(dir, name))
            if (ls.isSymbolicLink()) continue
            isDir = ls.isDirectory()
          } catch {
            continue
          }
          lines.push(`${'  '.repeat(depth)}${name}${isDir ? '/' : ''}`)
          if (isDir && depth < 1) walk(join(dir, name), r, depth + 1)
        }
      }
      walk(abs, rel, 0)
      const text = `${rel || '/'} (the learner's data folder${rel ? '' : ': the six databases at the top, the app\'s files under app/'}):\n${lines.join('\n') || '(empty)'}`
      return ok(text.length > VIEW_MAX ? `${text.slice(0, VIEW_MAX)}\n… (cut at ${VIEW_MAX} characters: view a deeper directory)` : text)
    }
    const t = readText(abs, rel)
    if (typeof t !== 'string') return fail(t.error)
    const all = t.split('\n')
    let from = 1
    let to = all.length
    if (range !== undefined) {
      if (!Array.isArray(range) || range.length !== 2 || !range.every(n => Number.isInteger(n))) return fail('view_range: [start, end], 1-based lines (end -1: to the end)')
      from = Math.max(1, range[0])
      to = range[1] === -1 ? all.length : Math.min(all.length, range[1])
      if (from > all.length) return fail(`view_range: ${rel} has ${all.length} lines`)
      if (to < from) return fail('view_range: the end comes before the start')
    }
    const numbered = all.slice(from - 1, to).map((l, i) => `${String(from + i).padStart(6)}\t${l}`).join('\n')
    return ok(numbered.length > VIEW_MAX ? `${numbered.slice(0, VIEW_MAX)}\n… (cut at ${VIEW_MAX} characters: view_range [${from}, …] for the rest, or search)` : numbered)
  }

  const editor: Tool = {
    name: 'str_replace_based_edit_tool',
    ...specOf('str_replace_based_edit_tool', {
      description: "View, create and edit the learner's files (the data folder): view, create, str_replace, insert. Writes only in the tutor's content areas, each checked by its kind's validator first.",
      inputSchema: { type: 'object', properties: { command: { type: 'string', enum: ['view', 'create', 'str_replace', 'insert'] }, path: { type: 'string' } }, required: ['command', 'path'] },
    }),
    handle: a => {
      const command = a.command
      const p = where(a.path)
      if ('error' in p) return fail(p.error)
      const { rel, abs } = p
      switch (command) {
        case 'view':
          return view(rel, abs, a.view_range)
        case 'create': {
          if (typeof a.file_text !== 'string') return fail('create: file_text (the whole file) is needed; nothing written')
          if (existsSync(abs) && statSync(abs).isDirectory()) return fail(`Nothing written: ${rel} is a directory`)
          return write(rel, abs, a.file_text, existsSync(abs) ? 'rewrote' : 'created')
        }
        case 'str_replace': {
          if (typeof a.old_str !== 'string' || !a.old_str) return fail('str_replace: old_str (the text to replace, exactly as it is in the file) is needed; nothing written')
          if (isPrivate(rel)) return fail(`${rel} is private: the file tools don't touch it`)
          const t = readText(abs, rel)
          if (typeof t !== 'string') return fail(`Nothing written: ${t.error}`)
          const n = t.split(a.old_str).length - 1
          if (n !== 1) return fail(`Nothing written: old_str is in ${rel} ${n === 0 ? 'nowhere' : `${n} times`} (it must be there exactly once: give more of the text around it)`)
          return write(rel, abs, t.replace(a.old_str, () => (typeof a.new_str === 'string' ? a.new_str : '')), 'edited')
        }
        case 'insert': {
          const text = typeof a.insert_text === 'string' ? a.insert_text : typeof a.new_str === 'string' ? a.new_str : undefined
          if (text === undefined) return fail('insert: insert_text (the text to insert) is needed; nothing written')
          if (!Number.isInteger(a.insert_line) || a.insert_line < 0) return fail('insert: insert_line (the line to insert after, 0 for the start) is needed; nothing written')
          if (isPrivate(rel)) return fail(`${rel} is private: the file tools don't touch it`)
          const t = readText(abs, rel)
          if (typeof t !== 'string') return fail(`Nothing written: ${t.error}`)
          const lines = t.split('\n')
          if (a.insert_line > lines.length) return fail(`Nothing written: insert_line ${a.insert_line}, but ${rel} has ${lines.length} lines`)
          lines.splice(a.insert_line, 0, ...text.split('\n'))
          return write(rel, abs, lines.join('\n'), 'edited')
        }
        default:
          return fail('command: one of view, create, str_replace, insert')
      }
    },
  }

  const search: Tool = {
    name: 'search',
    ...specOf('search', {
      description: "Search the learner's files (the data folder) for a text: path:line: the line.",
      inputSchema: { type: 'object', properties: { pattern: { type: 'string' }, path: { type: 'string' }, regex: { type: 'boolean' }, max_results: { type: 'integer' } }, required: ['pattern'] },
    }),
    handle: a => {
      if (typeof a.pattern !== 'string' || !a.pattern) return fail('search: pattern (the text to find) is needed')
      if (a.pattern.length > 500) return fail('search: the pattern is at most 500 characters')
      const max = Number.isInteger(a.max_results) ? Math.min(Math.max(a.max_results, 1), 200) : 50
      let re: RegExp | undefined
      if (a.regex === true) {
        try {
          re = new RegExp(a.pattern, 'i')
        } catch (e) {
          return fail(`search: not a regular expression: ${(e as Error).message}`)
        }
      }
      const needle = a.pattern.toLowerCase()
      const p = where(typeof a.path === 'string' && a.path.trim() ? a.path : '/')
      if ('error' in p) return fail(p.error)
      if (isPrivate(p.rel)) return fail(`${p.rel} is private: the file tools don't read it`)
      if (!existsSync(p.abs)) return fail(`no such file or directory: ${p.rel || '/'}`)
      const hits: string[] = []
      let files = 0
      let more = false
      const scan = (abs: string, rel: string) => {
        if (more) return
        let st
        try {
          st = lstatSync(abs)
        } catch {
          return
        }
        if (st.isSymbolicLink() || isPrivate(rel)) return
        if (st.isDirectory()) {
          for (const name of readdirSync(abs).sort()) scan(join(abs, name), rel ? `${rel}/${name}` : name)
          return
        }
        if (st.size > MAX_FILE) return
        const bytes = readFileSync(abs)
        if (isBinary(bytes)) return
        files++
        bytes.toString('utf8').split('\n').forEach((line, i) => {
          if (more) return
          const probe = line.length > 4000 ? line.slice(0, 4000) : line
          if (re ? re.test(probe) : probe.toLowerCase().includes(needle)) {
            if (hits.length >= max) {
              more = true
              return
            }
            hits.push(`${rel}:${i + 1}: ${line.length > 200 ? `${line.slice(0, 200)}…` : line}`)
          }
        })
      }
      scan(p.abs, p.rel)
      if (!hits.length) return ok(`no match for ${JSON.stringify(a.pattern)} in ${p.rel || 'the data folder'} (${files} files)`)
      return ok(`${hits.join('\n')}${more ? `\n… more than ${max} matches: narrow the path or the pattern` : ''}`)
    },
  }
  return [editor, search]
}

/** The file tools that write (lease-guarded as the app session's writing tools are). */
export const writesFiles = (name: string, args: Record<string, any>) => name === 'str_replace_based_edit_tool' && args.command !== 'view'
/** The file tools' names. */
export const FILE_TOOLS = new Set(['str_replace_based_edit_tool', 'search'])

// --- jobs ----------------------------------------------------------------------------------------------------------

export const JOB_SCHEMA = 'lani.studio-job/v1'
const JOB_ID = /^j_[a-z0-9]{6,24}$/
const JOB_MAX = 4 * 1024 * 1024
const ENDED = new Set(['done', 'failed', 'cancelled'])
/** An ended job is kept this long (then GET /tutor/jobs prunes it). */
const JOBS_KEPT_MS = () => Number(process.env.LANI_STUDIO_JOBS_KEPT_MS ?? 7 * 86_400_000)
/** The big parts of a job, left out of the list. */
const JOB_BIG = new Set(['messages', 'subtasks', 'drafts', 'steps'])

/** The studio's jobs (the app's long content jobs), one file each in app/tutor/jobs/. */
export class Jobs {
  readonly dir: string
  constructor(appDir: string) {
    this.dir = join(appDir, 'tutor', 'jobs')
  }

  private file(id: string) {
    return join(this.dir, `${id}.json`)
  }

  get(id: string): Record<string, unknown> | undefined {
    if (!JOB_ID.test(id)) return undefined
    try {
      return JSON.parse(readFileSync(this.file(id), 'utf8'))
    } catch {
      return undefined
    }
  }

  /** The jobs' summaries (the big parts left out), newest first; ended ones past their time are removed first. */
  list(now = Date.now()): Record<string, unknown>[] {
    if (!existsSync(this.dir)) return []
    const out: Record<string, unknown>[] = []
    for (const f of readdirSync(this.dir).filter(f => f.endsWith('.json'))) {
      const id = f.slice(0, -5)
      const j = this.get(id)
      if (!j) continue
      const updated = Number(j.updated_at ?? 0)
      if (ENDED.has(String(j.status)) && now - updated > JOBS_KEPT_MS()) {
        unlinkSync(this.file(id))
        continue
      }
      out.push(Object.fromEntries(Object.entries(j).filter(([k]) => !JOB_BIG.has(k))))
    }
    return out.sort((a, b) => Number(b.updated_at ?? 0) - Number(a.updated_at ?? 0))
  }

  /** Saves [job] under [id]; an error text when it isn't a job. */
  put(id: string, job: unknown): string | number {
    if (!JOB_ID.test(id)) return 'id: j_ and 6-24 lowercase letters or digits'
    const j = obj(job)
    if (!j) return 'the body is the job (a JSON object)'
    if (j.schema !== JOB_SCHEMA) return `schema: "${JOB_SCHEMA}"`
    if (j.id !== id) return `id: the job's id is "${String(j.id)}", the path's "${id}"`
    if (typeof j.status !== 'string') return 'status: queued, running, batch, done, failed or cancelled'
    const updated = Number.isFinite(Number(j.updated_at)) && Number(j.updated_at) > 0 ? Number(j.updated_at) : Date.now()
    mkdirSync(this.dir, { recursive: true })
    writeAtomic(this.file(id), JSON.stringify({ ...j, updated_at: updated }))
    return updated
  }

  delete(id: string): boolean {
    if (!JOB_ID.test(id) || !existsSync(this.file(id))) return false
    unlinkSync(this.file(id))
    return true
  }
}

// --- reports ---------------------------------------------------------------------------------------------------------

export type Report = {
  title: string
  kind: 'fix' | 'wish'
  item?: Record<string, unknown>
  url?: string
  number?: number
  state: 'posted' | 'upvoted' | 'kept' | 'declined' | 'resolved'
  via: 'discussion' | 'wishlist'
  at: string
  resolved_at?: string
  resolved_by?: string
}
const reportIn = z.object({
  title: z.string().trim().min(1).max(200),
  kind: z.enum(['fix', 'wish']),
  item: z.record(z.string(), z.unknown()).optional(),
  url: z.string().url().max(500).optional(),
  number: z.number().int().positive().optional(),
  state: z.enum(['posted', 'upvoted', 'kept', 'declined']),
  via: z.enum(['discussion', 'wishlist']),
  at: z.string().max(40).optional(),
})

/** The learner's reports (app/reports.json): what went to Lani's discussions or the wishlist, and what came of it. */
export class Reports {
  private readonly file: string
  constructor(appDir: string) {
    this.file = join(appDir, 'reports.json')
  }

  all(): Report[] {
    try {
      const r = JSON.parse(readFileSync(this.file, 'utf8'))
      return r?.schema === 'lani.reports/v1' && Array.isArray(r.reports) ? r.reports : []
    } catch {
      return []
    }
  }

  private save(reports: Report[]) {
    mkdirSync(dirname(this.file), { recursive: true })
    writeAtomic(this.file, JSON.stringify({ schema: 'lani.reports/v1', reports }, null, 2) + '\n')
  }

  /** Adds [input] (or updates the report with its url); the report, or the error's text. */
  add(input: unknown): Report | string {
    const r = reportIn.safeParse(input)
    if (!r.success) return z.prettifyError(r.error)
    const reports = this.all()
    const report: Report = { ...r.data, at: r.data.at ?? new Date().toISOString() }
    const i = report.url ? reports.findIndex(x => x.url === report.url) : -1
    if (i >= 0) reports[i] = { ...reports[i], ...report }
    else reports.push(report)
    this.save(reports.slice(-500))
    return report
  }

  /** The reports whose item names [kind]'s [id] (its ids by any key), marked resolved by [why]; those resolved. */
  resolve(kind: string, id: string, why: string): Report[] {
    const reports = this.all()
    const named = (r: Report) => {
      const it = r.item ?? {}
      const ids = Object.entries(it).filter(([k]) => k !== 'kind').map(([, v]) => v)
      const kinds = [it.kind, ...Object.keys(it).map(k => k.replace(/_id$/, ''))]
      return r.state !== 'resolved' && ids.includes(id) && (kinds.includes(kind) || kinds.includes(`${kind}s`) || (kind === 'module' && kinds.includes('challenge')))
    }
    const hit = reports.filter(named)
    if (!hit.length) return []
    const at = new Date().toISOString()
    for (const r of hit) Object.assign(r, { state: 'resolved', resolved_at: at, resolved_by: why })
    this.save(reports)
    return hit
  }
}

// --- the routes --------------------------------------------------------------------------------------------------

const checkIn = z.object({ kind: z.string().max(40).optional(), path: z.string().max(500).optional(), text: z.string().max(2 * 1024 * 1024) })
const restoreIn = z.object({ at: z.string().min(1).max(40) })

/** What the routes use of the notebook (features/tutor.ts Notebook). */
export type NotebookPort = {
  versionsList: () => { at: string; why: string; text: string }[]
  restore: (at: string) => { text: string; meta: Record<string, unknown> } | undefined
}

/** The studio's routes: the check, the jobs, the notebook's versions, the reports, the overrides. */
export function studioRoutes(ctx: Ctx, o: { jobs: Jobs; reports: Reports; notebook: NotebookPort; overrides: Overrides }): Route[] {
  const { jobs, reports, notebook, overrides } = o
  return [
    {
      method: 'POST',
      path: '/tutor/check',
      handle: async ({ req }) => {
        const r = checkIn.safeParse(await readJson(req, 2 * 1024 * 1024 + 4096))
        if (!r.success) return json({ error: `{kind?, path?, text}: ${z.prettifyError(r.error)}` }, 400)
        let rel: string | undefined
        if (r.data.path !== undefined) {
          const p = dataPath(ctx.cfg.dataDir, r.data.path)
          if ('error' in p) return json({ ok: false, kind: 'none', errors: p.error })
          rel = p.rel
        }
        if (!rel && !r.data.kind) return json({ error: 'a kind or a path, to know what the text is' }, 400)
        const c = checkContent(ctx, rel, r.data.text, r.data.kind)
        return json(c)
      },
    },
    { method: 'GET', path: '/tutor/jobs', handle: () => json({ jobs: jobs.list() }) },
    {
      method: 'GET',
      path: /^\/tutor\/jobs\/([\w-]{1,40})$/,
      handle: ({ params }) => {
        const j = jobs.get(params[0])
        return j ? json(j) : json({ error: `no job ${params[0]}` }, 404)
      },
    },
    {
      method: 'PUT',
      path: /^\/tutor\/jobs\/([\w-]{1,40})$/,
      handle: async ({ req, params }) => {
        const body = await readJson(req, JOB_MAX)
        const r = jobs.put(params[0], body)
        return typeof r === 'string' ? json({ error: r }, 400) : json({ ok: true, updated_at: r })
      },
    },
    {
      method: 'DELETE',
      path: /^\/tutor\/jobs\/([\w-]{1,40})$/,
      handle: ({ params }) => json({ ok: true, deleted: jobs.delete(params[0]) }),
    },
    {
      method: 'GET',
      path: '/tutor/notebook/versions',
      handle: () => json({ versions: notebook.versionsList().map(v => ({ at: v.at, why: v.why, chars: v.text.length, text: v.text })).reverse() }),
    },
    {
      method: 'POST',
      path: '/tutor/notebook/restore',
      handle: async ({ req }) => {
        const r = restoreIn.safeParse(await readJson(req))
        if (!r.success) throw new HttpError(400, 'at: the version\'s time (GET /tutor/notebook/versions)')
        const done = notebook.restore(r.data.at)
        if (!done) return json({ error: `no version of ${r.data.at}` }, 404)
        ctx.log(`tutor: the notebook restored to its version of ${r.data.at}`)
        return json({ text: done.text, ...done.meta })
      },
    },
    { method: 'GET', path: '/tutor/reports', handle: () => json({ reports: reports.all() }) },
    {
      method: 'POST',
      path: '/tutor/reports',
      handle: async ({ req }) => {
        const r = reports.add(await readJson(req))
        if (typeof r === 'string') return json({ error: r }, 400)
        ctx.log(`tutor: report ${r.state} (${r.via}): ${r.title.slice(0, 80)}`)
        return json({ ok: true, report: r })
      },
    },
    {
      method: 'GET',
      path: '/tutor/overrides',
      handle: () => {
        overrides.check()
        return json(overrides.read())
      },
    },
  ]
}

/** The text the channel gets when an override retired ([e]), with the report it resolved. */
export function retiredText(e: OverrideEntry, resolved: Report[]): string {
  const report = resolved.find(r => r.url)
  return (
    `The curated ${e.kind} "${e.id}" changed upstream (a fix shipped): the learner's local fix of it (an override) retired, ` +
    `and the curated one plays again. The tutor's version is kept in app/${e.moved_to}.` +
    (resolved.length ? ` It resolves the report "${resolved[0].title}"${report ? ` (${report.url})` : ''}: tell the learner the fix they reported is in.` : '')
  )
}

