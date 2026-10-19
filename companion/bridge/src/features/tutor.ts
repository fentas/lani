// The app's tutor session (docs/plans/05-built/tutor-live.md): the in-app tutor (the app's own agent loop, on the
// learner's own API key) as a client of the channel, beside Claude Code. Routes for the paired phone, with the app's
// role:
//
//   GET  /tutor/session          the channel's instructions, its tools exactly as Claude Code lists them, the app
//                                session's own tools, the protocol's version, who is attached
//   POST /tutor/attach {who}     the app's tutor takes the channel: every notification goes to its inbox, not to the
//                                Claude Code session (it hears nothing new; the log and a new session's instructions
//                                say so), and what the queue held moves there
//   POST /tutor/detach           the session gets them again, with what the inbox still held
//   GET  /tutor/inbox?after=&wait=   the notifications after an id (a long poll of at most 25 s)
//   POST /tutor/inbox/ack {ids|upto}  done with them
//   POST /tutor/tools/:name {args}   a tool call, through the very handlers Claude Code's calls use (Channel.call: counted
//                                in flight, refused while the service stops), once per client_id (the tool call's id)
//   GET  /tutor/notebook         the tutor's notebook (app/tutor/notebook.md) and its meta; PUT {text}: the learner's own
//                                correction ("What my tutor knows"); GET /tutor/notebook/versions, POST …/restore {at}
//   the studio's (tutor-studio.ts, docs/plans/05-built/studio.md): POST /tutor/check {kind?, path?, text}, the jobs
//                                (GET /tutor/jobs, GET/PUT/DELETE /tutor/jobs/:id), GET/POST /tutor/reports, GET
//                                /tutor/overrides (the local overrides of curated content: overrides.ts)
//
// The tools Claude Code runs as Python through its shell are the app session's own tools (never in Claude Code's list):
// learner_state (read-db.py), record_session (update-db.py's report), card (card.py's commands), the notebook's
// notebook_note and notebook_write, and report_problem (the wishlist until the discussions have a GitHub token). They
// run the scripts through the bridge's own spawn path (config.ts), so on the phone the db port answers them. And the file
// tools (tutor-studio.ts): str_replace_based_edit_tool and search over the learner's data folder, checked on write.
import { existsSync, mkdirSync, readFileSync, appendFileSync } from 'node:fs'
import { join } from 'node:path'
import { z } from 'zod'
import { fail, listed, ok, runTool, type Tool, type ToolResult } from '../tool'
import { spawn } from '../config'
import type { Ctx, FeatureFactory } from '../feature'
import { HttpError, json, readJson, type Route } from '../http'
import { keepOut } from '../ignore'
import { writeAtomic } from '../pairing'
import { TutorInbox } from '../tutor-inbox'
import { Overrides, type OverrideKind } from '../overrides'
import { FILE_TOOLS, fileTools, Jobs, Reports, retiredText, studioRoutes, writesFiles } from '../tutor-studio'

/** The version of this contract; the app checks it before it attaches. */
export const TUTOR_PROTOCOL = 1
/** The longest a GET /tutor/inbox waits, in seconds (the app's read timeout is longer). */
export const INBOX_WAIT_MAX = 25
/** An attach the app hasn't polled for this long lets go (logged), so a phone gone for good doesn't keep the channel. */
const leaseMs = () => Number(process.env.LANI_TUTOR_LEASE_MS ?? 3 * 86_400_000)
/** The notebook's size limit (notebook_write's schema: 12,000 characters) and how many old versions are kept. */
const NOTEBOOK_MAX = 12_000
const VERSIONS_KEPT = 20

const attachIn = z.object({ who: z.literal('app').default('app'), device: z.string().max(80).optional() })
const ackIn = z.object({ ids: z.array(z.number().int()).max(1000).optional(), upto: z.number().int().optional() })
const toolIn = z.object({ args: z.record(z.string(), z.unknown()).default({}), client_id: z.string().max(80).optional() })
/** A language as the app tools take it: a code (it) or a name (Italian). */
const languageIn = z.string().min(2).max(40).regex(/^[\p{L} -]+$/u, 'a language code ("it") or name ("Italian")').optional()
const notebookIn = z.object({ text: z.string().max(NOTEBOOK_MAX) })

type NotebookMeta = { schema: 'lani.notebook/v0'; updated_at: string | null; why: string | null; chars: number; history: { at: string; why: string; chars: number }[] }
type NotebookVersion = { at: string; why: string; text: string }

/**
 * The tutor's notebook (plan 5, "The notebook") in the learner's data, so it moves with the data repository between
 * modes: app/tutor/notebook.md the text, app/tutor/notebook.json its meta (when and why it was last written, the
 * history's times), app/tutor/notebook-versions.json the old texts (the last [VERSIONS_KEPT]).
 */
export class Notebook {
  private readonly dir: string
  constructor(appDir: string) {
    this.dir = join(appDir, 'tutor')
  }

  private file(name: string) {
    return join(this.dir, name)
  }

  text(): string {
    try {
      return readFileSync(this.file('notebook.md'), 'utf8')
    } catch {
      return ''
    }
  }

  meta(): NotebookMeta {
    try {
      const m = JSON.parse(readFileSync(this.file('notebook.json'), 'utf8'))
      if (m?.schema === 'lani.notebook/v0') return m
    } catch {} // none yet
    return { schema: 'lani.notebook/v0', updated_at: null, why: null, chars: this.text().length, history: [] }
  }

  /** The old texts, oldest first (the last [VERSIONS_KEPT]). */
  versionsList(): NotebookVersion[] {
    return this.versions()
  }

  /** Writes the version of [at] back (the text now kept as a version, as [write] does); undefined: no such version. */
  restore(at: string): { text: string; meta: NotebookMeta } | undefined {
    const v = this.versions().find(x => x.at === at)
    if (!v) return undefined
    const meta = this.write(v.text, `restored (${at})`)
    return { text: this.text(), meta }
  }

  private versions(): NotebookVersion[] {
    try {
      const v = JSON.parse(readFileSync(this.file('notebook-versions.json'), 'utf8'))
      return Array.isArray(v) ? v : []
    } catch {
      return []
    }
  }

  /** Replaces the text ([why]: notebook_write, by hand); the old one is kept among the versions. */
  write(text: string, why: string): NotebookMeta {
    mkdirSync(this.dir, { recursive: true })
    const old = this.text()
    const before = this.meta()
    const now = new Date().toISOString()
    if (old.trim()) writeAtomic(this.file('notebook-versions.json'), JSON.stringify([...this.versions(), { at: before.updated_at ?? now, why: before.why ?? 'before', text: old }].slice(-VERSIONS_KEPT), null, 2) + '\n')
    const clean = text.replace(/\s+$/, '') + '\n'
    writeAtomic(this.file('notebook.md'), clean)
    const meta: NotebookMeta = { schema: 'lani.notebook/v0', updated_at: now, why, chars: clean.length, history: [...before.history, { at: now, why, chars: clean.length }].slice(-50) }
    writeAtomic(this.file('notebook.json'), JSON.stringify(meta, null, 2) + '\n')
    return meta
  }

  /** Adds one line ("- <text>", notebook_note). */
  note(line: string): NotebookMeta {
    mkdirSync(this.dir, { recursive: true })
    const old = this.text().replace(/\s+$/, '')
    const next = `${old ? `${old}\n` : ''}- ${line.replace(/\s+/g, ' ').trim()}\n`
    writeAtomic(this.file('notebook.md'), next)
    const before = this.meta()
    const meta: NotebookMeta = { ...before, updated_at: new Date().toISOString(), why: 'notebook_note', chars: next.length }
    writeAtomic(this.file('notebook.json'), JSON.stringify(meta, null, 2) + '\n')
    return meta
  }
}

/** The app tools' specs as the app bundles them (companion/tutor/app-tools/<name>.json): one source for both. */
function appToolSpec(projectDir: string, name: string, fallback: { description: string; inputSchema: Record<string, unknown> }) {
  try {
    const o = JSON.parse(readFileSync(join(projectDir, 'companion/tutor/app-tools', `${name}.json`), 'utf8'))
    if (o?.name === name && typeof o.description === 'string' && o.input_schema) return { description: o.description as string, inputSchema: o.input_schema as Record<string, unknown> }
  } catch {} // not in this project (a phone's unpacked content without it): the short one
  return fallback
}

const str = (v: unknown) => (typeof v === 'string' ? v : undefined)

/**
 * read-db.py's output summed up for the model (the same fields as the shadow's summary, tutor/Reads.kt Summaries): the
 * computed fields (the due reviews' first ids only), the languages, the profile's learner and streak, the overall
 * stats, the mastery per skill, the top mistakes with their latest example, the last three sessions, the deck's size.
 */
export function learnerSummary(raw: string): string {
  const o = JSON.parse(raw)
  const dbs = o?.databases ?? {}
  const profile = dbs.learner_profile ?? {}
  const patterns: Record<string, any> = dbs.mistakes_db?.error_patterns ?? {}
  const top = Object.entries(patterns)
    .sort(([, a], [, b]) => (Number(b?.frequency) || 0) - (Number(a?.frequency) || 0))
    .slice(0, 8)
  const pick = (from: any, keys: string[]) => Object.fromEntries(keys.filter(k => from?.[k] !== undefined).map(k => [k, from[k]]))
  const computed = o?.computed ? { ...o.computed, ...(Array.isArray(o.computed.due_review_items) ? { due_review_items: o.computed.due_review_items.slice(0, 20) } : {}) } : undefined
  const out = {
    ...(computed ? { computed } : {}),
    ...(o?.language !== undefined ? { language: o.language } : {}),
    ...(o?.languages !== undefined ? { languages: o.languages } : {}),
    ...(profile.learner ? { learner: profile.learner } : {}),
    ...pick(profile, ['current_streak_days', 'total_sessions', 'total_study_minutes']),
    ...(dbs.progress_db?.overall_stats ? { overall_stats: dbs.progress_db.overall_stats } : {}),
    ...(dbs.mastery_db?.skills ? { mastery: dbs.mastery_db.skills } : {}),
    top_mistakes: Object.fromEntries(
      top.map(([id, p]) => [
        id,
        {
          ...pick(p, ['category', 'subcategory', 'severity', 'frequency', 'mastery_level', 'last_occurred', 'consecutive_incorrect']),
          ...(Array.isArray(p?.examples) && p.examples.length ? { last_example: p.examples[p.examples.length - 1] } : {}),
        },
      ]),
    ),
    recent_sessions: (Array.isArray(dbs.session_log?.sessions) ? dbs.session_log.sessions : []).slice(-3),
    ...(dbs.spaced_repetition?.items ? { review_items: Object.keys(dbs.spaced_repetition.items).length } : {}),
  }
  return JSON.stringify(out, null, 2)
}

/** card's command line (card.py's argparse: options as --name=value, then `--`, then the positionals). */
export function cardArgv(a: Record<string, unknown>, dataDir: string): string[] | string {
  const action = str(a.action)
  const opt = (name: string, v: unknown) => (v === undefined || v === null ? [] : [`--${name}=${String(v)}`])
  const flag = (name: string, v: unknown) => (v === true ? [`--${name}`] : [])
  const need = (k: string) => (str(a[k])?.trim() ? str(a[k])! : undefined)
  const head = ['--data=' + dataDir, ...opt('language', str(a.language))]
  const write = [...opt('reason', str(a.reason)), ...flag('dry-run', a.dry_run), ...flag('force', a.force)]
  const texts = [...opt('front', str(a.front)), ...opt('back', str(a.back)), ...opt('note', str(a.note))]
  switch (action) {
    case 'find':
      if (!need('text')) return 'find needs text'
      return [...head, 'find', ...opt('limit', typeof a.limit === 'number' ? a.limit : undefined), '--', a.text as string]
    case 'show':
      if (!need('card_id')) return 'show needs card_id'
      return [...head, 'show', '--', a.card_id as string]
    case 'overlaps':
      return [...head, 'overlaps', ...(need('card_id') ? ['--', a.card_id as string] : [])]
    case 'edit':
      if (!need('card_id')) return 'edit needs card_id'
      return [...head, 'edit', ...write, ...texts, ...opt('category', str(a.category)), '--', a.card_id as string]
    case 'regrade':
      if (!need('card_id')) return 'regrade needs card_id'
      if (typeof a.quality !== 'number') return 'regrade needs quality (0-5)'
      return [...head, 'regrade', ...write, `--quality=${a.quality}`, ...opt('date', str(a.date)), '--', a.card_id as string]
    case 'merge':
      if (!need('card_id') || !need('drop_id')) return 'merge needs card_id (kept) and drop_id'
      return [...head, 'merge', ...write, ...texts, '--', a.card_id as string, a.drop_id as string]
    case 'split':
      if (!need('card_id') || !need('new_id') || !need('new_front') || !need('new_back')) return 'split needs card_id, new_id, new_front and new_back'
      return [...head, 'split', ...write, ...texts, `--new-id=${a.new_id}`, `--new-front=${a.new_front}`, `--new-back=${a.new_back}`, ...opt('new-note', str(a.new_note)), '--', a.card_id as string]
    case 'forgive':
      if (!need('pattern_id')) return 'forgive needs pattern_id'
      return [...head, 'forgive', ...write, ...opt('date', str(a.date)), ...opt('answer', str(a.answer)), '--', a.pattern_id as string]
    default:
      return 'action: one of find, show, overlaps, edit, merge, split, regrade, forgive'
  }
}

/** What a script printed, as a tool's result: its output, an error when it failed. */
function scriptResult(p: { exitCode: number | null; stdout: string; stderr: string }, what: string): ToolResult {
  const out = (p.stdout + (p.stderr.trim() ? `\n${p.stderr}` : '')).trim()
  return p.exitCode === 0 ? ok(out || `${what}: done`) : fail(`${what} failed (exit ${p.exitCode}): ${out.slice(0, 4000) || 'no output'}`)
}

/** A wishlist entry for report_problem (the lani-fix skill's shape), dated today. */
export function wishlistEntry(a: Record<string, unknown>, day = new Date().toISOString().slice(0, 10)): string {
  const line = (s: unknown) => String(s ?? '').replace(/\s+/g, ' ').trim()
  const kind = a.kind === 'wish' ? 'wish' : 'fix'
  const title = line(a.title) || (kind === 'wish' ? 'Idea' : 'Fix')
  return [
    `## ${day} · ${title}`,
    `- **What:** ${line(a.what_is_wrong)}`,
    ...(a.suggested_fix ? [`- **Fix:** ${line(a.suggested_fix)}`] : []),
    ...(a.item && typeof a.item === 'object' ? [`- **Item:** \`${JSON.stringify(a.item).replace(/`/g, "'")}\``] : []),
    ...(a.native_check === true ? ['- **Native check:** a native speaker should check this Slovene'] : []),
    `- **From:** the app's tutor (report_problem, kind ${kind}); not in Lani's GitHub discussions yet: the app has no GitHub sign-in for them`,
    '',
  ].join('\n')
}

/** card's commands that only read (find, show, overlaps): allowed while another side holds the learner's data. */
const CARD_READS = new Set(['find', 'show', 'overlaps'])

/**
 * [tools] with the lease's guard (lease.ts, as guardTools does for the session's): a write (record_session, the
 * notebook, report_problem, card's writing commands) fails with the reason while another side holds the learner's data;
 * learner_state and card's reads go on.
 */
function leaseGuarded(tools: Tool[], refusal: () => string | undefined): Tool[] {
  const writes = (name: string, args: Record<string, any>) =>
    FILE_TOOLS.has(name) ? writesFiles(name, args) : name !== 'learner_state' && !(name === 'card' && CARD_READS.has(String(args.action)))
  return tools.map(t => ({
    ...t,
    handle: (args: Record<string, any>) => {
      const why = writes(t.name, args) ? refusal() : undefined
      return why ? fail(`Nothing written. ${why}`) : t.handle(args)
    },
  }))
}

/**
 * The app session's own tools (the ones Claude Code runs as Python, and the file tools Claude Code has as its own Read,
 * Edit and Write): never in Claude Code's list. [written]: a file tool wrote a content file (the overrides' check).
 */
export function appSessionTools(ctx: Ctx, notebook: Notebook, written: (w: { rel: string; kind: string; id?: string }) => void = () => {}): Tool[] {
  const spec = (name: string, fallback: { description: string; inputSchema: Record<string, unknown> }) => appToolSpec(ctx.cfg.projectDir, name, fallback)
  return leaseGuarded([...appTools(ctx, notebook), ...fileTools(ctx, spec, written)], () => ctx.lease?.refusal())
}

function appTools(ctx: Ctx, notebook: Notebook): Tool[] {
  const { cfg, learner, log } = ctx
  const spec = (name: string, description: string, inputSchema: Record<string, unknown>) => appToolSpec(cfg.projectDir, name, { description, inputSchema })
  const lang = (a: Record<string, any>): string | undefined | ToolResult => {
    const l = languageIn.safeParse(a.language ?? undefined)
    return l.success ? l.data : fail(`language: ${z.prettifyError(l.error)}`)
  }
  return [
    {
      name: 'learner_state',
      ...spec('learner_state', "The learner's state from the six databases, summed up (read-db.py).", { type: 'object', properties: { language: { type: 'string' } }, additionalProperties: false }),
      handle: a => {
        const l = lang(a)
        if (typeof l === 'object') return l
        const p = spawn(cfg.projectDir, ['python3', '.claude/hooks/read-db.py', ...(l && l !== learner.home() ? ['--language', l] : [])])
        if (p.exitCode !== 0) return fail(`learner_state failed: ${(p.stderr || p.stdout).trim().slice(0, 2000)}`)
        try {
          return ok(learnerSummary(p.stdout))
        } catch (e) {
          return fail(`learner_state: read-db.py's output isn't JSON (${(e as Error).message})`)
        }
      },
    },
    {
      name: 'record_session',
      ...spec('record_session', "Persist one practice session in the learner's six databases (update-db.py's report).", { type: 'object', properties: { session_id: { type: 'string' }, date: { type: 'string' } }, required: ['session_id', 'date'] }),
      handle: a => {
        const l = lang(a)
        if (typeof l === 'object') return l
        if (a.record_session !== false && (typeof a.session_id !== 'string' || typeof a.date !== 'string')) return fail('record_session: session_id (learner_state\'s next_session_id) and date (YYYY-MM-DD) are needed; nothing written')
        const r = learner.update(a)
        if (r.ok) log(`tutor: record_session ${a.session_id ?? '(vocabulary only)'}${l ? ` (${l})` : ''} written`)
        return r.ok ? ok(r.output || 'recorded') : fail(`record_session: nothing written. ${r.output.slice(0, 4000)}`)
      },
    },
    {
      name: 'card',
      ...spec('card', "Find, inspect and fix the learner's review cards and mistakes (card.py).", { type: 'object', properties: { action: { type: 'string' } }, required: ['action'] }),
      handle: a => {
        const l = lang(a)
        if (typeof l === 'object') return l
        const argv = cardArgv({ ...a, language: l }, cfg.dataDir)
        if (typeof argv === 'string') return fail(`card: ${argv}; nothing done`)
        const p = spawn(cfg.projectDir, ['python3', '.claude/hooks/card.py', ...argv])
        if (p.exitCode === 0 && !['find', 'show', 'overlaps'].includes(String(a.action)) && a.dry_run !== true) log(`tutor: card ${a.action} ${a.card_id ?? a.pattern_id ?? ''}`)
        return scriptResult(p, `card ${a.action}`)
      },
    },
    {
      name: 'notebook_note',
      ...spec('notebook_note', 'Add one line to the notebook.', { type: 'object', properties: { text: { type: 'string', maxLength: 300 } }, required: ['text'] }),
      handle: a => {
        const t = str(a.text)?.trim()
        if (!t || t.length > 300) return fail('notebook_note: text, 1-300 characters; nothing noted')
        const m = notebook.note(t)
        return ok(`noted (the notebook has ${m.chars} characters)`)
      },
    },
    {
      name: 'notebook_write',
      ...spec('notebook_write', 'Rewrite the whole notebook.', { type: 'object', properties: { text: { type: 'string', maxLength: NOTEBOOK_MAX } }, required: ['text'] }),
      handle: a => {
        const t = str(a.text)?.trim()
        if (!t || t.length > NOTEBOOK_MAX) return fail(`notebook_write: text, 1-${NOTEBOOK_MAX} characters; nothing written`)
        const m = notebook.write(t, 'notebook_write')
        return ok(`written: the notebook has ${m.chars} characters; the old version is kept`)
      },
    },
    {
      name: 'report_problem',
      ...spec('report_problem', "Report a wrong curated item or a wish for the app.", { type: 'object', properties: { kind: { type: 'string', enum: ['fix', 'wish'] }, title: { type: 'string' }, what_is_wrong: { type: 'string' } }, required: ['kind', 'title', 'what_is_wrong'] }),
      handle: a => {
        if (a.kind !== 'fix' && a.kind !== 'wish') return fail('report_problem: kind is fix or wish; nothing kept')
        if (!str(a.title)?.trim() || !str(a.what_is_wrong)?.trim()) return fail('report_problem: title and what_is_wrong are needed; nothing kept')
        const file = join(cfg.appDir, 'wishlist.md')
        const head = existsSync(file) ? '' : '# Wishlist\n\nWhat the tutor noted for the developer: wrong curated items and wishes for the app.\n\n'
        appendFileSync(file, head + wishlistEntry(a) + '\n')
        log(`tutor: report_problem (${a.kind}) kept in the wishlist: ${String(a.title).slice(0, 80)}`)
        return ok(
          "Kept in the learner's wishlist for the developer (app/wishlist.md), as the node does today. Not posted to Lani's GitHub discussions: that needs a GitHub sign-in the app doesn't have yet. Tell the learner it's noted for the developer.",
        )
      },
    },
  ]
}

/**
 * The app's tutor session's routes over the session's tools (ctx.session: exactly Claude Code's list, read once every
 * feature is built) and its instructions, on a node's channel and the phone's alike (ChannelPort's divert, undivert,
 * call).
 */
export const tutorSession: FeatureFactory = ctx => {
  const { cfg, channel, log } = ctx
  const notebook = new Notebook(cfg.appDir)
  const jobs = new Jobs(cfg.appDir)
  const reports = new Reports(cfg.appDir)
  // the local overrides of curated content (overrides.ts): a tutor item under a curated id retires when the curated file changes
  const overrides = new Overrides({
    appDir: cfg.appDir,
    projectDir: cfg.projectDir,
    log,
    sources: {
      module: { dirs: () => ctx.modules.dirs(), layout: 'dir' },
      scene: { dirs: () => ctx.scenes.dirs(), layout: 'file' },
      story: { dirs: () => ctx.stories.dirs(), layout: 'file' },
      villager: { dirs: () => ctx.villagers.dirs(), layout: 'file' },
    },
    onRetired: e => {
      const resolved = reports.resolve(e.kind, e.id, `the curated ${e.kind} changed upstream (${e.retired_at})`)
      // the app reloads it: the curated one is back (a villager comes with the next reload)
      const back = e.kind === 'module' ? { type: 'module_published', id: e.id, version: 1 } : e.kind === 'scene' ? { type: 'scene_removed', id: e.id } : e.kind === 'story' ? { type: 'story_removed', id: e.id } : undefined
      if (back) ctx.events.emit(back)
      void channel
        .notify(retiredText(e, resolved), { kind: 'override_retired', conversation_id: 'main', msg_id: `override-${e.kind}-${e.id}-${Date.now().toString(36)}` })
        .catch(err => log(`tutor: override_retired not sent: ${(err as Error).message}`))
    },
  })
  const checkOverrides = (only?: { kind?: OverrideKind; id?: string }) => {
    try {
      overrides.check(only)
    } catch (e) {
      log(`tutor: the overrides' check: ${(e as Error).message}`)
    }
  }
  const overrideKind = (k: string): OverrideKind | undefined => (k === 'module-current' ? 'module' : ['module', 'scene', 'story', 'villager'].includes(k) ? (k as OverrideKind) : undefined)
  const appTools = appSessionTools(ctx, notebook, w => {
    const k = overrideKind(w.kind)
    if (k) checkOverrides({ kind: k, id: w.id })
  })
  /** Every tool the app's tutor may call: the session's, then the app session's own. */
  let byNameMemo: Map<string, Tool> | undefined
  const byName = () => {
    if (byNameMemo) return byNameMemo
    const session = ctx.session.tools()
    const names = new Set(session.map(t => t.name))
    const clash = appTools.filter(t => names.has(t.name)).map(t => t.name)
    if (clash.length) log(`tutor: the app session's tools ${clash.join(', ')} shadow the channel's of the same name`)
    byNameMemo = new Map([...session, ...appTools].map(t => [t.name, t]))
    return byNameMemo
  }
  const draining = () => channel.draining === true
  const inbox = new TutorInbox(join(cfg.appDir, 'tutor-inbox.json'), log)
  // attached before a restart: the app's tutor still has the channel
  if (inbox.attached) {
    if (channel.divert) channel.divert(inbox, log)
    else inbox.setAttached(undefined)
  }
  channel.whenDraining?.(() => inbox.wake())

  /** The attach holds while the app polls; a phone silent for the lease's time lets go (logged). */
  const leaseHolds = async () => {
    const a = inbox.attached
    if (!a) return false
    if (Date.now() - a.seen <= leaseMs()) return true
    log(`tutor: the app's tutor hasn't read its inbox since ${new Date(a.seen).toISOString()}: detached (lease)`)
    inbox.setAttached(undefined)
    await channel.undivert?.(log)
    return false
  }

  const status = () => ({ attached: inbox.attached ? { who: inbox.attached.who, since: inbox.attached.since, ...(inbox.attached.device ? { device: inbox.attached.device } : {}) } : null, inbox: { size: inbox.size, last_id: inbox.lastId } })

  const routes: Route[] = [
    {
      method: 'GET',
      path: '/tutor/session',
      handle: async () => {
        await leaseHolds()
        return json({ protocol: TUTOR_PROTOCOL, instructions: ctx.session.rules(), tools: listed(ctx.session.tools()), app_tools: listed(appTools), ...status() })
      },
    },
    {
      method: 'POST',
      path: '/tutor/attach',
      handle: async ({ req }) => {
        const r = attachIn.safeParse((await readJson(req)) ?? {})
        if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
        if (!channel.divert) return json({ error: "this bridge's channel can't give its events to the app's tutor" }, 501)
        if (!inbox.attached) {
          inbox.setAttached({ who: r.data.who, since: new Date().toISOString(), ...(r.data.device ? { device: r.data.device } : {}), seen: Date.now() })
          const moved = channel.divert(inbox, log)
          return json({ ok: true, moved, ...status() })
        }
        inbox.seen()
        return json({ ok: true, moved: 0, ...status() })
      },
    },
    {
      method: 'POST',
      path: '/tutor/detach',
      handle: async () => {
        if (!inbox.attached) return json({ ok: true, returned: 0, ...status() })
        inbox.setAttached(undefined)
        const returned = (await channel.undivert?.(log)) ?? 0
        return json({ ok: true, returned, ...status() })
      },
    },
    {
      method: 'GET',
      path: '/tutor/inbox',
      handle: async ({ url }) => {
        const after = Number(url.searchParams.get('after') ?? 0)
        const wait = Math.min(Math.max(Number(url.searchParams.get('wait') ?? 0), 0), INBOX_WAIT_MAX)
        if (!Number.isFinite(after) || !Number.isFinite(wait)) return json({ error: 'after: an id; wait: seconds (at most 25)' }, 400)
        if (!(await leaseHolds())) return json({ items: [], ...status() })
        inbox.seen()
        const items = await inbox.wait(after, wait * 1000, draining)
        return json({ items, ...status() })
      },
    },
    {
      method: 'POST',
      path: '/tutor/inbox/ack',
      handle: async ({ req }) => {
        const r = ackIn.safeParse((await readJson(req)) ?? {})
        if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
        const acked = inbox.ack(r.data.ids, r.data.upto)
        return json({ ok: true, acked, ...status() })
      },
    },
    {
      method: 'POST',
      path: /^\/tutor\/tools\/([\w-]{1,64})$/,
      handle: ({ req, url, params }) =>
        ctx.outbox.once(req, url.pathname, async r => {
          const body = toolIn.safeParse((await readJson(r)) ?? {})
          if (!body.success) return json({ error: z.prettifyError(body.error) }, 400)
          const name = params[0]
          const tools = byName()
          if (!tools.has(name)) return json({ error: `no tool ${name} on this bridge`, tools: [...tools.keys()] }, 404)
          const args = body.data.args as Record<string, any>
          try {
            const result = await (channel.call ? channel.call(tools, name, args) : runTool(tools, name, args))
            // a curated item replaced or given back: the overrides' registry follows
            if (!result.isError && /^(publish|remove|rollback)_/.test(name)) checkOverrides()
            return json(result)
          } catch (e) {
            if (draining()) return json({ error: 'the bridge service is restarting: try again', restarting: true }, 503)
            // what Claude Code shows of a tool that threw (the MCP server's error answer): its message
            return json(fail(String((e as Error)?.message ?? e)))
          }
        }),
    },
    {
      method: 'GET',
      path: '/tutor/notebook',
      handle: () => json({ text: notebook.text(), ...notebook.meta() }),
    },
    {
      method: 'PUT',
      path: '/tutor/notebook',
      handle: async ({ req }) => {
        const r = notebookIn.safeParse(await readJson(req))
        if (!r.success) throw new HttpError(400, `text: the notebook, at most ${NOTEBOOK_MAX} characters`)
        const meta = notebook.write(r.data.text, 'by hand')
        log(`tutor: the notebook corrected by hand (${meta.chars} characters)`)
        return json({ text: notebook.text(), ...meta })
      },
    },
  ]

  routes.push(...studioRoutes(ctx, { jobs, reports, notebook, overrides }))

  return {
    routes,
    start: () => {
      // the overrides: at start (a curated file may have changed with an update), then every 15 minutes
      checkOverrides()
      const every = setInterval(() => checkOverrides(), Number(process.env.LANI_OVERRIDES_CHECK_MS ?? 15 * 60_000))
      ;(every as { unref?: () => void }).unref?.()
      // the inbox (what the learner said lately) stays on this machine, as the channel's queue does
      try {
        keepOut(cfg.dataDir, { line: '/app/tutor-inbox.json', samples: ['app/tutor-inbox.json'], comment: "The app tutor's inbox (features/tutor.ts): stays on this machine (added by the bridge)." }, m => log(`tutor: ${m}`))
        keepOut(cfg.dataDir, { line: '/app/tutor/jobs/', samples: ['app/tutor/jobs/j_000000.json'], comment: "The app tutor's studio jobs (tutor-studio.ts): its threads, stay on this machine (added by the bridge)." }, m => log(`tutor: ${m}`))
        keepOut(cfg.dataDir, { line: '/app/tutor/file-history/', samples: ['app/tutor/file-history/x.1'], comment: "The file tools' old texts (tutor-studio.ts): stay on this machine (added by the bridge)." }, m => log(`tutor: ${m}`))
      } catch (e) {
        log(`tutor: ${(e as Error).message}`)
      }
    },
  }
}

