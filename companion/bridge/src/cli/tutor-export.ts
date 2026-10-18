#!/usr/bin/env bun
// The in-app tutor's copy of the bridge's MCP tools and channel guide (docs/plans/05-in-app-tutor.md, "One source of
// truth"), made from the bridge itself so the two never drift:
//
//   bun src/cli/tutor-export.ts            write companion/tutor/tools and companion/tutor/guide
//   bun src/cli/tutor-export.ts --check    compare them with what the bridge makes now: exit 1 with the files that differ
//   bun src/cli/tutor-export.ts --out DIR  write into DIR/tools and DIR/guide instead
//
// - tools/<name>.json: every MCP tool the bridge registers ({name, description, input_schema}: the bridge's inputSchema
//   under the Messages API's name), tools/index.json their names in the bridge's order, and tools/guide.json the app's
//   guide(topic) tool, which replaces channel_guide.
// - guide/<id>.md: the channel guide split into topics: one per feature paragraph, exactly as channel_guide gives them
//   to the default learner (Jan), and the skills the app's tutor reads as topics (each with a short "> In the app:"
//   line first); guide/index.json the topics in reading order.
//
// The bridge is built (buildBridge: no server, timer or job started) in an environment of its own, never this
// machine's: no lani.env, a throwaway data directory from data-examples/ (learner Jan, male), the repository's curated
// content as the bridge reads it by default. companion/tutor/app-tools/ (the app's own tools) is hand-written.
import { cpSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, unlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, relative, resolve } from 'node:path'
import { parseArgs } from 'node:util'
import type { BuiltBridge } from '../bridge'

const repo = resolve(import.meta.dir, '../../../..')
const SOURCE = 'companion/bridge (npm run/bun: src/cli/tutor-export.ts)'

/** The guide's topic per feature that has a paragraph: its id, a short title and when to fetch it (≤ 140 characters). */
const FEATURE_TOPICS: Record<string, { id: string; title: string; summary: string }> = {
  chat: { id: 'events', title: 'Inbound events', summary: 'Every event kind (chat, answer, session_end, review_done, …), its JSON and how to answer it. Fetch first in every thread.' },
  modules: { id: 'studio-modules', title: 'Practice modules', summary: 'Before creating, changing or retiring a practice module (publish_module, list_modules, get_module).' },
  grammar: { id: 'grammar-book', title: 'Grammar book', summary: 'When explaining a rule, a chat with about_grammar or grammar_drill, or introducing a rule that is ripe.' },
  cards: { id: 'cheat-sheet', title: 'Cheat sheet', summary: 'When a table or list in chat is worth a card (publish_card), or looked_up answers point to a topic.' },
  course: { id: 'school', title: 'The school', summary: "For a session_end with data.course (a school sheet or test), a unit done, or homework (publish_worksheet)." },
  counting: { id: 'counting', title: 'Counting cards', summary: 'For a review_done with counting cards, or mistakes counting_*: their four steps, the mistake kinds, what to suggest.' },
  'forms-cards': { id: 'forms-cards', title: 'Forms cards', summary: "For a review_done with forms cards, or mistakes forms_*: what a card shows and tests, the overview, the kinds, what to suggest." },
  rhythm: { id: 'rhythm', title: 'Daily rhythm', summary: 'For daily_morning, daily_evening and daily_weekly, or when the learner changes the times (set_rhythm).' },
  game: { id: 'village', title: 'The village game', summary: "When the village (get_village) can motivate, or for sidequests and a task the learner keeps failing." },
  packs: { id: 'packs', title: 'Word packs', summary: 'When the learner asks for words on a topic (publish_pack, list_packs), or for words_learned.' },
  lexicon: { id: 'word-lookup', title: 'Word lookup', summary: 'For a chat with data.lookup, gloss_gaps or words_added (publish_gloss).' },
  sentences: { id: 'sentence-grammar', title: 'Sentence grammar', summary: "For a chat with data.about_sentence: explain a line's grammar and keep it (publish_sentence_note)." },
  scenarios: { id: 'role-play', title: 'Role-play', summary: 'For roleplay and roleplay_end (in character, hints, the debrief), or to write a scenario.' },
  scenes: { id: 'scenes', title: 'Scenes and dialogs', summary: "For dialog_variants_heard and words_to_weave, writing scenes or dialog variants, and the learner placeholders." },
  stories: { id: 'stories', title: 'Evening stories', summary: 'For stories_low, or when the learner asks for a story (publish_story, list_stories).' },
  readings: { id: 'readings', title: 'Readings', summary: 'For reading_done, or to write a reading for the reading corner (publish_reading, list_readings).' },
  villagers: { id: 'villagers', title: 'Villagers', summary: 'For a villager talk (scenario_id villager:…), villager_arrived, or writing a villager (publish_villager).' },
  arrivals: { id: 'arrivals', title: 'Arrivals', summary: 'For villager_arrived: how the learner meets someone new (publish_arrival, get_arrival).' },
  family: { id: 'family', title: 'Family challenges', summary: 'For partner_challenge and partner_answer from the family page, and its untrusted content.' },
  voice: { id: 'voices', title: 'Voices', summary: "When a voice or a clip sounds wrong, or the learner asks about audio (voice_profiles, voice_redesign)." },
  level: { id: 'level-up', title: 'Level-ups', summary: 'For level_up, or when the learner asks whether they are ready for the next level (the treasure map).' },
  towns: { id: 'visits', title: 'Visits', summary: 'For talks and sessions on a visit to a linked town (town:, market:) and town_gift.' },
  friendship: { id: 'friendship', title: 'Friendship between towns', summary: "When a friend town's neighbour moves in (villager_arrived, kind moved)." },
  questions: { id: 'town-questions', title: 'Questions between towns', summary: 'For town_check, town_asked, town_answer, town_question and town_answered.' },
}

/** The skills the app's tutor reads as guide topics (the plan's "The Python scripts and the skills"). */
const SKILL_TOPICS: { id: string; title: string; summary: string; skills: string[]; app: string }[] = [
  {
    id: 'feedback',
    title: 'Feedback format',
    summary: "The feedback template for every graded answer (an answer, a partner_answer, a town check): corrections, severity, score.",
    skills: ['lani-feedback-formatter'],
    app: "> In the app: the feedback is the reply tool's text, its score and severity in reply's data; mistakes are recorded with record_session.",
  },
  {
    id: 'session-end',
    title: 'Recording a session',
    summary: 'For session_end and every session to persist: the report record_session takes, and the SM-2 qualities.',
    skills: ['lani-db-updater', 'lani-sm2-calculator'],
    app: '> In the app: update-db.py is the record_session tool (the report is its input), read-db.py is learner_state (next_session_id); the app runs SM-2 itself; there is no shell.',
  },
  {
    id: 'fix',
    title: 'Fixing a wrong item',
    summary: 'When the learner says a question, card, word, dialog choice or translation is wrong, or a right answer was marked wrong.',
    skills: ['lani-fix'],
    app: '> In the app: card.py is the card tool (its command is the action), a curated item goes to report_problem instead of wishlist.md; there is no shell, grep or lani-backup.',
  },
  {
    id: 'progress',
    title: 'Progress report',
    summary: 'When the learner asks how they are doing: stats, streak, mastery, words, achievements.',
    skills: ['lani-progress'],
    app: '> In the app: read-db.py is the learner_state tool (language for another language); there are no files to read and no shell.',
  },
  {
    id: 'analysis',
    title: 'Planning from past sessions',
    summary: 'When planning what to practise next or answering "what should I work on?" from recent sessions and mistakes.',
    skills: ['lani-session-analyzer'],
    app: "> In the app: there are no results/*.md files; learner_state's sessions and mistakes, recent_answers and the notebook are the record.",
  },
  {
    id: 'studio',
    title: 'Studio: writing content',
    summary: 'Before writing or fixing app content (modules, packs, scenes, stories, readings, villagers, grammar pages, homework).',
    skills: ['lani-studio'],
    app: '> In the app: read-db.py is learner_state, wishlist.md is report_problem (kind wish), content writing comes with phase C; there is no shell and no file to read under companion/.',
  },
]

/** The guide's reading order: the topics an event needs most first, the studio's last. */
const ORDER = [
  'events', 'feedback', 'session-end', 'fix', 'progress', 'analysis', 'rhythm', 'village', 'grammar-book', 'cheat-sheet',
  'school', 'counting', 'forms-cards', 'packs', 'word-lookup', 'sentence-grammar', 'role-play', 'villagers', 'scenes',
  'stories', 'readings', 'arrivals', 'level-up', 'voices', 'family', 'visits', 'friendship', 'town-questions',
  'studio-modules', 'studio',
]

type Topic = { id: string; title: string; summary: string; source: string; text: string }

const json = (v: unknown) => `${JSON.stringify(v, null, 2)}\n`
class ExportError extends Error {}
const fail = (msg: string): never => {
  throw new ExportError(msg)
}

/** A SKILL.md without its YAML frontmatter. */
function skillBody(name: string): string {
  const text = readFileSync(join(repo, '.claude/skills', name, 'SKILL.md'), 'utf8')
  const m = /^---\r?\n[\s\S]*?\r?\n---\r?\n/.exec(text) ?? fail(`.claude/skills/${name}/SKILL.md has no frontmatter`)
  return text.slice(m[0].length).trim()
}

/** Builds the bridge in a throwaway environment and returns what goes into tools/ and guide/, by path. */
async function generate(): Promise<Map<string, string>> {
  const tmp = mkdtempSync(join(tmpdir(), 'lani-tutor-export-'))
  // gone however the script ends
  process.on('exit', () => rmSync(tmp, { recursive: true, force: true }))
  try {
    // Nothing of this machine: no lani.env, no learner's data, settings, keys or caches.
    for (const k of Object.keys(process.env)) if (/^(LANI_|FLUENT_|ELEVENLABS_)/.test(k) || k === 'CLAUDE_PROJECT_DIR' || k === 'CLAUDE_PLUGIN_ROOT') delete process.env[k]
    const data = join(tmp, 'data')
    mkdirSync(data)
    // The learner databases from the empty templates, the learner Jan, a man (as the smoke tests: test/smoke/harness.ts).
    const templates = join(repo, 'data-examples')
    for (const f of readdirSync(templates).filter(f => f.endsWith('-template.json')).sort()) cpSync(join(templates, f), join(data, f.replace('-template', '')))
    const profile = join(data, 'learner-profile.json')
    const p = JSON.parse(readFileSync(profile, 'utf8'))
    writeFileSync(profile, JSON.stringify({ ...p, learner: { ...p.learner, name: 'Jan', gender: 'male' } }, null, 2))
    Object.assign(process.env, {
      LANI_ENV_FILE: '/dev/null',
      LANI_DATA_DIR: data, // ensure_data_dir.py (lani_paths.data_dir) takes it first
      CLAUDE_PROJECT_DIR: repo, // this checkout's curated content (companion/), whichever session runs the script
      LANI_CONFIG_DIR: join(tmp, 'config'),
      XDG_CACHE_HOME: join(tmp, 'cache'),
      XDG_STATE_HOME: join(tmp, 'state'),
      XDG_DATA_HOME: join(tmp, 'share'),
      LANI_VOICE_DESIGN: 'off',
    })
    await import('../env')
    const { buildBridge } = await import('../bridge')
    // The bridge's log goes to stderr: kept out of the way unless something fails.
    const said: unknown[][] = []
    const error = console.error
    console.error = (...a: unknown[]) => void said.push(a)
    let b: BuiltBridge
    try {
      b = buildBridge({ mode: 'stdio' })
    } catch (e) {
      console.error = error
      for (const a of said) console.error(...a)
      throw e
    } finally {
      console.error = error
    }
    return render(b)
  } finally {
    rmSync(tmp, { recursive: true, force: true })
  }
}

function render(b: BuiltBridge): Map<string, string> {
  const out = new Map<string, string>()

  // --- tools ---------------------------------------------------------------------------------------------------------
  const names = b.tools.map(t => t.name)
  const twice = names.filter((n, i) => names.indexOf(n) !== i)
  if (twice.length) fail(`tools registered twice: ${twice.join(', ')}`)
  for (const t of b.tools) out.set(`tools/${t.name}.json`, json({ name: t.name, description: t.description, input_schema: t.inputSchema }))
  out.set('tools/index.json', json({ source: SOURCE, tools: names }))

  // --- the guide -----------------------------------------------------------------------------------------------------
  const guide = b.guide()
  const paragraphs = b.named.filter(f => f.feature.instructions)
  // The topics are the guide, split: joined in the bridge's order they must be channel_guide's text exactly.
  const joined = paragraphs.map(f => f.feature.instructions!).join('\n\n')
  if (joined !== guide) fail("channel_guide's text isn't the features' paragraphs joined (a paragraph for this learner the topics don't have)")
  const topics: Topic[] = paragraphs.map(({ name, feature }) => {
    const t = FEATURE_TOPICS[name] ?? fail(`the feature ${name} has a guide paragraph but no topic: add it to FEATURE_TOPICS`)
    return { ...t, source: `bridge feature ${name}`, text: feature.instructions! }
  })
  for (const s of SKILL_TOPICS) {
    const text = [s.app, ...s.skills.map(skillBody)].join('\n\n')
    topics.push({ id: s.id, title: s.title, summary: s.summary, source: `${s.skills.length > 1 ? 'skills' : 'skill'} ${s.skills.join(' + ')}`, text })
  }
  const ids = topics.map(t => t.id)
  const unordered = ids.filter(id => !ORDER.includes(id))
  const gone = ORDER.filter(id => !ids.includes(id))
  if (unordered.length || gone.length) fail(`ORDER doesn't match the topics: ${[...unordered.map(i => `+${i}`), ...gone.map(i => `-${i}`)].join(' ')}`)
  for (const t of topics) {
    if (t.summary.length > 140) fail(`the summary of ${t.id} is ${t.summary.length} characters (at most 140)`)
  }
  const ordered = ORDER.map(id => topics.find(t => t.id === id)!)
  for (const t of ordered) out.set(`guide/${t.id}.md`, `${t.text}\n`)
  out.set('guide/index.json', json({ topics: ordered.map(t => ({ id: t.id, title: t.title, summary: t.summary, chars: `${t.text}\n`.length, source: t.source })) }))

  // The app's guide(topic): one topic at a time, instead of channel_guide's whole text.
  out.set('tools/guide.json', json({
    name: 'guide',
    description: [
      "One topic of the tutor's guide: what to do for a kind of event or task, and which tools to use. Fetch a topic the first time a thread needs it (its text stays in the thread); \"events\" first in every thread. Topics:",
      ...ordered.map(t => `- ${t.id}: ${t.summary}`),
    ].join('\n'),
    input_schema: { type: 'object', properties: { topic: { type: 'string', enum: ordered.map(t => t.id), description: 'The topic id' } }, required: ['topic'], additionalProperties: false },
  }))
  return out
}

/** The files of [dir]/tools and [dir]/guide, by path relative to [dir]. */
function present(dir: string): Map<string, string> {
  const out = new Map<string, string>()
  for (const sub of ['tools', 'guide']) {
    const d = join(dir, sub)
    if (!existsSync(d)) continue
    for (const f of readdirSync(d, { withFileTypes: true })) if (f.isFile()) out.set(`${sub}/${f.name}`, readFileSync(join(d, f.name), 'utf8'))
  }
  return out
}

const { values } = parseArgs({ args: process.argv.slice(2), options: { check: { type: 'boolean' }, out: { type: 'string' } } })
const dir = resolve(values.out ?? join(repo, 'companion/tutor'))
/** [dir] for the messages: from the repository's root when inside it. */
const shown = relative(repo, dir).startsWith('..') ? dir : relative(repo, dir) || '.'
let made: Map<string, string>
try {
  made = await generate()
} catch (e) {
  console.error(`tutor-export: ${e instanceof ExportError ? e.message : ((e as Error).stack ?? e)}`)
  process.exit(1)
}
const there = present(dir)

if (values.check) {
  const paths = [...new Set([...made.keys(), ...there.keys()])].sort()
  const differ = paths.flatMap(p => (!there.has(p) ? [`missing: ${p}`] : !made.has(p) ? [`not made by the bridge: ${p}`] : there.get(p) !== made.get(p) ? [`differs: ${p}`] : []))
  if (differ.length) {
    console.error(`tutor-export --check: ${shown} is not what the bridge makes now (run: bun companion/bridge/src/cli/tutor-export.ts):`)
    for (const d of differ) console.error(`  ${d}`)
    process.exit(1)
  }
  console.log(`tutor-export --check: ${made.size} files match the bridge (${[...made.keys()].filter(p => p.startsWith('tools/') && p !== 'tools/index.json' && p !== 'tools/guide.json').length} tools, ${[...made.keys()].filter(p => p.endsWith('.md')).length} guide topics)`)
} else {
  for (const sub of ['tools', 'guide']) mkdirSync(join(dir, sub), { recursive: true })
  // What the bridge no longer makes goes (a tool removed, a topic renamed); only files of tools/ and guide/.
  for (const p of there.keys()) if (!made.has(p)) unlinkSync(join(dir, p))
  let changed = 0
  for (const [p, text] of [...made].sort(([a], [b]) => a.localeCompare(b))) {
    if (there.get(p) === text) continue
    writeFileSync(join(dir, p), text)
    changed++
  }
  console.log(`tutor-export: ${made.size} files in ${shown} (${changed} written, ${[...there.keys()].filter(p => !made.has(p)).length} removed)`)
}
