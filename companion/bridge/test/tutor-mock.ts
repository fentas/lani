#!/usr/bin/env bun
// A stand-in for the Messages API (api.anthropic.com) for the in-app tutor's QA: no key exists yet, and the app's client
// must be tested against the real wire format (docs/plans/05-in-app-tutor.md, "The agent loop in the app"). Purely
// local, canned and deterministic: it never calls out.
//
//   bun companion/bridge/test/tutor-mock.ts      on http://127.0.0.1:8771 (the emulator: http://10.0.2.2:8771)
//
// LANI_TUTOR_MOCK_PORT (default 8771; 0 picks a free one, printed), LANI_TUTOR_MOCK_DELAY_MS between stream events
// (default 15), LANI_TUTOR_MOCK_MODELS: the model ids GET /v1/models lists (comma list; default claude-opus-5-5,
// claude-sonnet-5-5, claude-haiku-5-5, claude-haiku-4-5), so a test can check the app's fallback when one is missing.
//
// Every /v1 request needs x-api-key (any value; one containing "bad" is refused with a 401) and anthropic-version:
// 2023-06-01. The key is never logged, kept or echoed.
//
// POST /v1/messages, by the request (the app runs a manual tool loop over client tools, tool_choice auto):
// 1. the turn's first call (the last user message has no tool_result): a thinking block, a short text and two parallel
//    tool_use blocks, guide {topic: "feedback", else the enum's first} and learner_state {}, of the tools the request
//    has (stop_reason tool_use);
// 2. tool results and no reply yet this turn: reply {conversation_id (from the turn's first message, else "main"),
//    text, data {score, severity} for an answer}, with notebook_note beside it when the request has it, and
//    record_session when the turn mentions session_end (tool_use);
// 3. after the reply: a short text (end_turn);
// 4. a closing call (the last user message says [notebook_write]): notebook_write (tool_use), then end_turn.
// Markers in the turn's first user message act once per distinct message, so the app's retry then succeeds:
// [mock:429] (retry-after: 1), [mock:529], [mock:500], [mock:refusal] (stop_reason refusal, stop_details),
// [mock:max_tokens] (a text cut off), [mock:stream-error] (message_start, then an error event), [mock:no-reply] (the
// answer as text, end_turn, no reply call); [mock:credit] (a 400: the credit balance is too low) acts every time;
// [mock:slow] (every time) makes the reply long and streams its input slowly (LANI_TUTOR_MOCK_SLOW_MS a piece of ten
// characters, default 150), so the app's live tutor shows it while it is written (QA's screenshot mid-way).
//
// Effort (output_config.effort; docs: build-with-claude/effort): the top-level level must be one of low, medium, high,
// xhigh, max, and claude-haiku-4-5 takes none (400). An effort-only system message in messages ({"role": "system",
// "content": [], "output_config": {"effort": …}}) needs the beta mid-conversation-output-config-2026-07-01 in
// anthropic-beta (else the API's 400 "messages.N.output_config: Extra inputs are not permitted") and a model with
// per-message effort (else "output_config.effort requires a model that supports per-turn effort; this model does not";
// here every model but claude-haiku-4-5); the mock takes no other system message in messages (the app sends none). The
// level in effect for the reply is the docs': an effort message directly after the turn's user message, else the last
// one before it, else the top-level, else the model's default (medium on Opus 5.5 and Haiku 5.5, high else). It sets
// how much a thinking block weighs in output_tokens (low 40, medium 120, high 300, xhigh 600, max 1000), and Haiku 5.5
// at low answers a turn's first call as Jan's shadow saw it (2026-10-16): its answer as text, end_turn, no reply.
//
// Usage looks like caching, tokens estimated as characters / 4: the prefix (tools + system) is written once per model
// (1 hour when the last system block's cache_control says ttl "1h", else 5 minutes) and read while it lives; of the
// messages, what the previous request of the model had too (within 5 minutes) is read, the rest but the last message
// written for 5 minutes (with a top-level cache_control), the last message is input.
//
// GET /mock/requests: the last 50 requests (method, path, model, call, status, stop_reason, usage, body, has_key; never
// the key). POST /mock/reset: forgets the cache, the markers' once and the log.
import { createHash } from 'node:crypto'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'

export type MockOptions = { port?: number; host?: string; delayMs?: number; slowMs?: number; studioMs?: number; batchMs?: number; models?: string[]; log?: (line: string) => void }

type Block = Record<string, any>
type Msg = { role: string; content: string | Block[] }
type Usage = {
  input_tokens: number
  cache_creation_input_tokens: number
  cache_read_input_tokens: number
  cache_creation: { ephemeral_5m_input_tokens: number; ephemeral_1h_input_tokens: number }
  output_tokens: number
}
type Logged = Record<string, unknown>

const VERSION = '2023-06-01'
const HOUR = 3_600_000
const FIVE_MIN = 300_000

/** The models the mock knows (newest first by created_at); an id outside it gets a plausible entry. */
const CATALOG: Record<string, { display_name: string; created_at: string; max_input_tokens: number; max_tokens: number }> = {
  'claude-opus-5-5': { display_name: 'Claude Opus 5.5', created_at: '2026-10-07T00:00:00Z', max_input_tokens: 1_000_000, max_tokens: 128_000 },
  'claude-sonnet-5-5': { display_name: 'Claude Sonnet 5.5', created_at: '2026-10-06T00:00:00Z', max_input_tokens: 1_000_000, max_tokens: 128_000 },
  'claude-haiku-5-5': { display_name: 'Claude Haiku 5.5', created_at: '2026-10-05T00:00:00Z', max_input_tokens: 400_000, max_tokens: 64_000 },
  'claude-haiku-4-5': { display_name: 'Claude Haiku 4.5', created_at: '2025-10-15T00:00:00Z', max_input_tokens: 200_000, max_tokens: 64_000 },
}
const DEFAULT_MODELS = Object.keys(CATALOG)

const MARKERS_ONCE = ['429', '529', '500', 'stream-error', 'refusal', 'max_tokens', 'no-reply'] as const
type Marker = (typeof MARKERS_ONCE)[number] | 'credit'

const KINDS = ['session_end', 'roleplay_end', 'roleplay', 'review_done', 'answer', 'daily_morning', 'daily_evening', 'daily_weekly', 'villager_arrived', 'level_up', 'chat']

const LEVELS = ['low', 'medium', 'high', 'xhigh', 'max']
const EFFORT_BETA = 'mid-conversation-output-config-2026-07-01'
/** The output a thinking block weighs at each level (tokens). */
const THINKING: Record<string, number> = { low: 40, medium: 120, high: 300, xhigh: 600, max: 1000 }
const takesEffort = (model: string) => model !== 'claude-haiku-4-5'
const perMessageEffort = (model: string) => takesEffort(model)
const defaultEffort = (model: string) => (['claude-opus-5-5', 'claude-haiku-5-5'].includes(model) ? 'medium' : 'high')
/** An effort-only system message's level, else undefined. */
const effortOf = (m: Msg | undefined): string | undefined => (m?.role === 'system' ? (m as any).output_config?.effort : undefined)

const sha = (s: string) => createHash('sha256').update(s).digest('hex')
const tokens = (s: string) => Math.ceil(s.length / 4)
const today = () => new Date().toISOString().slice(0, 10)

/** A string cut into pieces of about [n] characters (whole code points). */
function chunks(s: string, n: number): string[] {
  const cps = [...s]
  const out: string[] = []
  for (let i = 0; i < cps.length; i += n) out.push(cps.slice(i, i + n).join(''))
  return out.length ? out : ['']
}

const blocksOf = (m: Msg | undefined): Block[] => (!m ? [] : typeof m.content === 'string' ? [{ type: 'text', text: m.content }] : Array.isArray(m.content) ? m.content : [])
const textOf = (m: Msg | undefined) => blocksOf(m).filter(b => b?.type === 'text' && typeof b.text === 'string').map(b => b.text).join('\n')
const hasToolResult = (m: Msg | undefined) => blocksOf(m).some(b => b?.type === 'tool_result')

/**
 * The level in effect for the reply to [messages] (the docs' rules): an effort message directly after the last user turn
 * with new input (not only tool_results) sets it for that reply; one anywhere before that turn holds from the next user
 * turn with new input; one after it, later than directly, waits. Undefined: none in the messages.
 */
function effortInEffect(messages: Msg[]): string | undefined {
  let u = messages.length - 1
  const newInput = (m: Msg) => m.role === 'user' && blocksOf(m).some(b => b?.type !== 'tool_result')
  while (u >= 0 && !newInput(messages[u])) u--
  if (u < 0) return undefined
  const direct = effortOf(messages[u + 1])
  if (direct) return direct
  for (let i = u - 1; i >= 0; i--) if (effortOf(messages[i])) return effortOf(messages[i])
  return undefined
}

const errorBody = (type: string, message: string) => ({ type: 'error', error: { type, message }, request_id: `req_mock_${sha(`${type}${message}${Date.now()}${Math.random()}`).slice(0, 24)}` })

/** A Message Batch as the mock keeps it: its requests' answers made at creation, shown once [endsAt] passed. */
type Batch = {
  id: string
  created: number
  endsAt: number
  cancelInitiated?: number
  cancelAt?: number
  results: { custom_id: string; result: Record<string, any> }[]
}

/** The request's system prompt as text. */
const systemText = (body: any) => (typeof body?.system === 'string' ? body.system : Array.isArray(body?.system) ? body.system.map((b: any) => b?.text ?? '').join('\n') : '')
/** A studio call (the app's studio task, docs/plans/05-built/studio.md): the publish tools, and the studio's prompt. */
const isStudio = (body: any) => Array.isArray(body?.tools) && body.tools.some((t: any) => t?.name === 'publish_module') && /studio/i.test(systemText(body))

/**
 * The studio's module ([mock:studio-module]): one the bridge's validateModule refuses (an answer index out of range),
 * then one it takes. Its Slovene is machine-written (marked in the title and the note).
 */
const STUDIO_MODULE_ID = 'mock-studio-drill'
function studioModule(valid: boolean) {
  return {
    schema: 'lani.module/v0',
    id: STUDIO_MODULE_ID,
    title: 'Pozdravi in vljudnost (strojno · machine-written)',
    level: 'A1',
    targets: ['mock_pattern'],
    tags: ['mock', 'machine-written'],
    exercises: [
      { type: 'choice', prompt: 'Kateri pozdrav je vljuden? · Which greeting is polite?', options: ['Živjo', 'Dober dan'], answer: valid ? 1 : 4, explain: '**Dober dan** is polite; *živjo* is for friends.' },
      { type: 'cloze', text: 'Ona ___ doma. · She is at home.', accept: ['je'], explain: '**je** · she/he is' },
      { type: 'reorder', prompt: 'Good day!', tokens: ['Dober', 'dan'], solutions: [['Dober', 'dan']] },
      { type: 'translate', prompt: 'Thank you very much.', accept: ['Hvala lepa.', 'Najlepša hvala.'] },
      { type: 'flashcard', front: 'hvala', back: 'thank you' },
    ],
  }
}

/**
 * The studio's story file ([mock:studio-file]): one the story validator refuses (no levels), then a copy of the shortest
 * curated story of primorska under the mock's id, its title machine-written and marked so.
 */
const STUDIO_STORY_ID = 'mock-studio-story'
const STUDIO_STORY_PATH = `app/stories/${STUDIO_STORY_ID}.json`
function studioStory(valid: boolean): string {
  if (!valid) return JSON.stringify({ schema: 'lani.story/v0', id: STUDIO_STORY_ID, language: 'sl', title: 'Poskusna zgodba (strojno · machine-written)', teller: 'janez' }, null, 2)
  const dir = resolve(import.meta.dir, '../../cultures/primorska/stories')
  const shortest = readdirSync(dir).filter(f => f.endsWith('.json')).map(f => join(dir, f)).sort((a, b) => statSync(a).size - statSync(b).size)[0]
  const story = JSON.parse(readFileSync(shortest, 'utf8'))
  return JSON.stringify({ ...story, id: STUDIO_STORY_ID, title: { sl: 'Poskusna zgodba (strojno)', en: 'A mock story (machine-written)' }, review: `the mock's copy of the curated "${story.id}" (test/tutor-mock.ts); its title machine-written`, order: undefined }, null, 2)
}

export function startTutorMock(o: MockOptions = {}) {
  const host = o.host ?? '127.0.0.1'
  const delayMs = o.delayMs ?? 15
  const slowMs = o.slowMs ?? 150
  const studioMs = o.studioMs ?? 4000
  const batchMs = o.batchMs ?? 1500
  const say = o.log ?? ((line: string) => console.log(line))
  const modelIds = o.models?.length ? o.models : DEFAULT_MODELS

  // --- memory (POST /mock/reset forgets it) -----------------------------------------------------------------------
  /** Per model: the prefixes (tools + system) cached, by hash: when written or last read, and how long they live. */
  let prefixes = new Map<string, Map<string, { at: number; ttl: number }>>()
  /** Per model: the previous request's messages (each serialized) and when. */
  let previous = new Map<string, { messages: string[]; at: number }>()
  /** The once-markers that acted, by marker and the message's hash. */
  let spent = new Set<string>()
  let requests: Logged[] = []
  /** The Message Batches (POST /v1/messages/batches): their requests' answers made at once, shown once they "ended". */
  let batches = new Map<string, Batch>()
  /** Never reset: message and tool ids stay unique while the mock runs. */
  let seq = 0

  function models() {
    const list = modelIds.map((id, i) => {
      const known = CATALOG[id]
      const created = known?.created_at ?? new Date(Date.UTC(2026, 8, 30 - i)).toISOString().replace('.000Z', 'Z')
      return { type: 'model', id, display_name: known?.display_name ?? id, created_at: created, max_input_tokens: known?.max_input_tokens ?? 200_000, max_tokens: known?.max_tokens ?? 64_000 }
    })
    return list.sort((a, b) => b.created_at.localeCompare(a.created_at))
  }

  function remember(entry: Logged) {
    requests.push(entry)
    if (requests.length > 50) requests = requests.slice(-50)
  }

  // --- usage ------------------------------------------------------------------------------------------------------
  function usageOf(model: string, body: any, record: boolean): Omit<Usage, 'output_tokens'> {
    const now = Date.now()
    const system = body.system
    const lastSystem = Array.isArray(system) ? system[system.length - 1] : undefined
    const auto = !!body.cache_control
    const cacheable = !!lastSystem?.cache_control || auto
    const ttl = lastSystem?.cache_control?.ttl === '1h' ? HOUR : FIVE_MIN
    const prefix = JSON.stringify(body.tools ?? []) + JSON.stringify(system ?? '')
    const prefixTokens = tokens(prefix)
    const msgs: string[] = (body.messages as Msg[]).map(m => JSON.stringify(m))
    let input = 0
    let read = 0
    let w5 = 0
    let w1h = 0

    if (!cacheable) input += prefixTokens
    else {
      const seen = prefixes.get(model) ?? new Map()
      const hit = seen.get(sha(prefix))
      if (hit && now - hit.at <= hit.ttl) read += prefixTokens
      else if (ttl === HOUR) w1h += prefixTokens
      else w5 += prefixTokens
      if (record) {
        seen.set(sha(prefix), { at: now, ttl })
        prefixes.set(model, seen)
      }
    }

    const last = msgs.length - 1
    let common = 0
    const before = previous.get(model)
    if (auto && before && now - before.at <= FIVE_MIN) {
      while (common < last && common < before.messages.length && before.messages[common] === msgs[common]) common++
    }
    for (let i = 0; i < msgs.length; i++) {
      const t = tokens(msgs[i])
      if (i === last || !auto) input += t
      else if (i < common) read += t
      else w5 += t
    }
    if (record) previous.set(model, { messages: msgs, at: now })
    return { input_tokens: input, cache_creation_input_tokens: w5 + w1h, cache_read_input_tokens: read, cache_creation: { ephemeral_5m_input_tokens: w5, ephemeral_1h_input_tokens: w1h } }
  }

  // --- the canned answer ------------------------------------------------------------------------------------------
  /** What the request asks for, from its messages: the call's kind and the content blocks to answer with. */
  function answer(body: any, id: string, effort: string, noReply: boolean) {
    // the effort-only system messages set the level (done by the caller); the answer reads the turns around them
    const messages = (body.messages as Msg[]).filter(m => m.role !== 'system')
    const tools = new Map<string, any>((Array.isArray(body.tools) ? body.tools : []).filter((t: any) => typeof t?.name === 'string').map((t: any) => [t.name, t]))
    const lastMsg = messages[messages.length - 1]
    let start = messages.length - 1
    while (start >= 0 && !(messages[start].role === 'user' && !hasToolResult(messages[start]))) start--
    const turn = textOf(messages[Math.max(start, 0)])
    const replied = messages.slice(start + 1).some(m => m.role === 'assistant' && blocksOf(m).some(b => b?.type === 'tool_use' && b.name === 'reply'))
    const results = hasToolResult(lastMsg)
    const kind = (/\bkind[="':\s]+([a-z_]+)/i.exec(turn)?.[1] ?? KINDS.find(k => turn.includes(k)) ?? 'chat').toLowerCase()
    const conversation = /conversation_id[^\w]+([\w-]+)/.exec(turn)?.[1] ?? 'main'
    let n = 0
    const toolUse = (name: string, input: unknown) => ({ type: 'tool_use', id: `toolu_mock_${id.slice('msg_mock_'.length)}_${++n}`, name, input })
    const done = { call: 'after_reply', stop: 'end_turn', content: [{ type: 'text', text: 'Opravljeno.' }] as Block[] }

    // 4. the closing call: the notebook rewritten, then done
    if (turn.includes('[notebook_write]') || textOf(lastMsg).includes('[notebook_write]')) {
      if (results || !tools.has('notebook_write')) return { ...done, call: 'closing_done' }
      const notebook = `# Notebook (mock)\n- The learner practises a little every day.\n- Recurring: mock_pattern (a mock mistake).\n- Plan: a short review tomorrow.\n- ${today()}: a mock day, ${messages.length} messages in the thread.`
      return { call: 'closing', stop: 'tool_use', content: [toolUse('notebook_write', { text: notebook })] }
    }

    // the studio's calls (the app's content jobs): by the markers in the job's (or the sub-task's) first message
    if (isStudio(body)) return studio(messages, start, turn, tools, toolUse, conversation, done, id)

    // Haiku 5.5 at low (as Jan's shadow saw it) or [mock:no-reply]: the answer as text at once, end_turn, no reply call
    if (!results && (noReply || (body.model === 'claude-haiku-5-5' && effort === 'low'))) {
      const said = kind === 'answer' ? 'ocenil sem odgovor · I graded the answer' : `videl sem dogodek ${kind} · I saw a ${kind} event`
      return {
        call: 'no_reply',
        stop: 'end_turn',
        content: [
          { type: 'thinking', thinking: `Mock: a ${kind} event; I'll just answer.`, signature: Buffer.from(sha(`sig${id}`), 'hex').toString('base64') },
          { type: 'text', text: `**Mock tutor** · ${said} (as text, no reply tool, at ${effort}).` },
        ] as Block[],
      }
    }

    // 1. the turn's first call: think, say a word, look things up
    if (!results) {
      const guide = tools.get('guide')
      const topics: unknown[] = guide?.input_schema?.properties?.topic?.enum ?? []
      const looks: Block[] = []
      if (guide) looks.push(toolUse('guide', { topic: topics.includes('feedback') ? 'feedback' : (topics[0] ?? 'events') }))
      if (tools.has('learner_state')) looks.push(toolUse('learner_state', {}))
      // [mock:request] (or [mock:request:file|slow|subtask|batch]): a chat that hands a brief to the studio
      const asked = /\[mock:request(?::(\w+))?\]/.exec(turn)
      if (asked && tools.has('request_content')) {
        const variant = asked[1] ?? 'module'
        const marker = variant === 'file' ? '[mock:studio-file]' : variant === 'slow' ? '[mock:studio-slow]' : variant === 'subtask' ? '[mock:subtask]' : '[mock:studio-module]'
        const said = turn.replace(/<[^>]+>/g, ' ').replace(/\[mock:[\w:-]+\]/g, '').replace(/\s+/g, ' ').trim().slice(0, 300)
        looks.push(toolUse('request_content', { title: 'Mock: vaje (strojno) · machine-written drill', brief: `${said || 'A drill'} (mock brief) ${marker}`, kinds: ['module'], urgent: variant !== 'batch' }))
      }
      if (looks.length) {
        return {
          call: 'first',
          stop: 'tool_use',
          content: [
            { type: 'thinking', thinking: `Mock: a ${kind} event on ${conversation}. The guide's topic first, then the learner's state.`, signature: Buffer.from(sha(`sig${id}`), 'hex').toString('base64') },
            { type: 'text', text: 'Pogledam … · Let me look.' },
            ...looks,
          ] as Block[],
        }
      }
    }

    // 2. the reply, once a turn
    if (!replied && tools.has('reply')) {
      const said = /\[mock:request/.test(turn) && tools.has('request_content') ? 'naročeno, vaje so na poti · on its way' : kind === 'answer' ? 'ocenil sem odgovor · I graded the answer' : `videl sem dogodek ${kind} · I saw a ${kind} event`
      const long = turn.includes('[mock:slow]')
        ? '\n\nTo je daljši odgovor, ki se piše počasi, da se vidi, kako nastaja: najprej pozdrav, potem razlaga in na koncu vprašanje. · A longer reply, written slowly so you can watch it come: a greeting first, then the explanation, and a question at the end.\n\n**Kako si danes?** · How are you today?'
        : ''
      const reply: Block[] = [toolUse('reply', { conversation_id: conversation, text: `**Mock tutor** · ${said}.${long}`, ...(kind === 'answer' ? { data: { score: 8, severity: '🟢' } } : {}) })]
      if (tools.has('notebook_note')) reply.push(toolUse('notebook_note', { text: `mock: saw a ${kind} event on ${conversation}` }))
      if (tools.has('record_session') && /session_end/.test(turn)) {
        const all = messages.map(m => JSON.stringify(m.content)).join('\n')
        const session = /next_session_id[\\"':\s]+(session-[\w-]+)/.exec(all)?.[1] ?? 'session-001'
        reply.push(toolUse('record_session', {
          session_id: session,
          date: /\b(\d{4}-\d{2}-\d{2})\b/.exec(turn)?.[1] ?? today(),
          duration_minutes: 5,
          command_used: 'app:session_end',
          skills_practiced: ['vocabulary'],
          skill_scores: { vocabulary: { exercises: 3, correct: 2, time_minutes: 5 } },
          errors: [{ pattern_id: 'mock_pattern', category: 'grammar', your_answer: 'Jaz je Jan.', correct_answer: 'Jaz sem Jan.', context: 'mock', severity: 'moderate' }],
          session_notes: 'A mock session.',
        }))
      }
      return { call: results ? 'tool_results' : 'first', stop: 'tool_use', content: reply }
    }

    // 3. done
    return done
  }

  /**
   * A studio call's answer: [mock:studio-module] (and -slow) guide and learner_state, publish_module refused by the
   * validator, then a valid one, the reply, done; [mock:studio-file] the text editor's create refused, then a valid story,
   * the reply, done; [mock:subtask] studio_subtask (its own thread plays studio-module), then the reply; no marker: the
   * reply alone. Each step is read from what the thread already holds, so a resumed job goes on where it stopped.
   */
  function studio(messages: Msg[], start: number, turn: string, tools: Map<string, any>, toolUse: (name: string, input: unknown) => Block, conversation: string, done: { call: string; stop: string; content: Block[] }, id: string) {
    const after = messages.slice(Math.max(start, 0) + 1)
    const uses = after.filter(m => m.role === 'assistant').flatMap(blocksOf).filter(b => b?.type === 'tool_use')
    const results = new Map(after.filter(m => m.role === 'user').flatMap(blocksOf).filter(b => b?.type === 'tool_result').map(b => [b.tool_use_id, b]))
    const failedLast = (name: string, f: (u: Block) => boolean = () => true) => {
      const last = uses.filter(u => u.name === name && f(u)).at(-1)
      return last ? results.get(last.id)?.is_error === true : false
    }
    const replied = uses.some(u => u.name === 'reply')
    const reply = (text: string) => ({ call: 'studio_reply', stop: 'tool_use', content: [toolUse('reply', { conversation_id: conversation, text })] as Block[] })
    const variant = turn.includes('[mock:studio-module]') || turn.includes('[mock:studio-slow]') ? 'module' : turn.includes('[mock:studio-file]') ? 'file' : turn.includes('[mock:subtask]') ? 'subtask' : 'none'
    const thinking = { type: 'thinking', thinking: `Mock studio: a ${variant} job. The guide first, then the learner's state.`, signature: Buffer.from(sha(`sig${id}`), 'hex').toString('base64') }
    if (variant === 'module') {
      const pubs = uses.filter(u => u.name === 'publish_module')
      if (!uses.length && (tools.has('guide') || tools.has('learner_state'))) {
        const topics: unknown[] = tools.get('guide')?.input_schema?.properties?.topic?.enum ?? []
        const looks: Block[] = []
        if (tools.has('guide')) looks.push(toolUse('guide', { topic: topics.includes('studio-modules') ? 'studio-modules' : (topics[0] ?? 'studio') }))
        if (tools.has('learner_state')) looks.push(toolUse('learner_state', {}))
        return { call: 'studio_look', stop: 'tool_use', content: [thinking, { type: 'text', text: 'Pogledam … · Let me look.' }, ...looks] as Block[] }
      }
      if (!pubs.length) return { call: 'studio_publish_refused', stop: 'tool_use', content: [toolUse('publish_module', { spec: studioModule(false), note: 'machine-written (mock)' })] as Block[] }
      if (failedLast('publish_module') && pubs.length < 3) return { call: 'studio_publish', stop: 'tool_use', content: [toolUse('publish_module', { spec: studioModule(true), note: 'machine-written (mock): a drill on greetings' })] as Block[] }
      if (!replied) return reply('**Mock studio** · vaje so objavljene · the drill is published')
      return { ...done, call: 'studio_done' }
    }
    if (variant === 'file') {
      const create = (u: Block) => u.input?.command === 'create'
      const creates = uses.filter(u => u.name === 'str_replace_based_edit_tool' && create(u))
      if (!creates.length) return { call: 'studio_file_refused', stop: 'tool_use', content: [thinking, toolUse('str_replace_based_edit_tool', { command: 'create', path: STUDIO_STORY_PATH, file_text: studioStory(false) })] as Block[] }
      if (failedLast('str_replace_based_edit_tool', create) && creates.length < 3) return { call: 'studio_file', stop: 'tool_use', content: [toolUse('str_replace_based_edit_tool', { command: 'create', path: STUDIO_STORY_PATH, file_text: studioStory(true) })] as Block[] }
      if (!replied) return reply('**Mock studio** · zgodba je zapisana · the story is written')
      return { ...done, call: 'studio_done' }
    }
    if (variant === 'subtask') {
      if (!uses.some(u => u.name === 'studio_subtask') && tools.has('studio_subtask')) return { call: 'studio_subtask', stop: 'tool_use', content: [thinking, toolUse('studio_subtask', { title: 'Mock subtask', brief: '[mock:studio-module]' })] as Block[] }
      if (!replied) return reply('**Mock studio** · podnaloga je opravljena · the sub-task is done')
      return { ...done, call: 'studio_done' }
    }
    if (!replied && tools.has('reply')) return reply('**Mock studio** · nothing to write')
    return { ...done, call: 'studio_done' }
  }

  /** The text of the turn's first user message (what the markers are read from). */
  function turnText(body: any): string {
    const messages = (body.messages as Msg[]).filter(m => m.role !== 'system')
    let start = messages.length - 1
    while (start >= 0 && !(messages[start].role === 'user' && !hasToolResult(messages[start]))) start--
    return textOf(messages[Math.max(start, 0)])
  }

  /** The marker that acts on this request, if any (a once-marker is spent here). */
  function markerOf(body: any): Marker | undefined {
    const messages = (body.messages as Msg[]).filter(m => m.role !== 'system')
    let start = messages.length - 1
    while (start >= 0 && !(messages[start].role === 'user' && !hasToolResult(messages[start]))) start--
    const turn = textOf(messages[Math.max(start, 0)])
    if (turn.includes('[mock:credit]')) return 'credit'
    for (const m of MARKERS_ONCE) {
      const key = `${m}:${sha(turn)}`
      if (turn.includes(`[mock:${m}]`) && !spent.has(key)) {
        spent.add(key)
        return m
      }
    }
    return undefined
  }

  // --- responses --------------------------------------------------------------------------------------------------
  const headers = (extra: Record<string, string> = {}) => ({ 'request-id': `req_mock_${sha(`${seq}${Date.now()}`).slice(0, 24)}`, ...extra })
  const failed = (status: number, type: string, message: string, extra: Record<string, string> = {}) => Response.json(errorBody(type, message), { status, headers: headers(extra) })

  const outputTokens = (content: Block[], effort: string | undefined) =>
    tokens(content.map(b => (b.type === 'text' ? b.text : b.type === 'thinking' ? b.thinking : b.type === 'tool_use' ? JSON.stringify(b.input) : '')).join('')) +
    (content.some(b => b.type === 'thinking') ? THINKING[effort ?? 'low'] ?? 40 : 0)

  function stream(id: string, model: string, content: Block[], stop: string, usage: Omit<Usage, 'output_tokens'>, o: { stopDetails?: unknown; error?: boolean; output: number; slow?: boolean; chunk?: number }) {
    const enc = new TextEncoder()
    const event = (type: string, data: unknown) => enc.encode(`event: ${type}\ndata: ${JSON.stringify(data)}\n\n`)
    const events: Uint8Array[] = []
    /** The events after which the stream waits longer ([mock:slow]: the reply's input pieces). */
    const slowAfter = new Set<number>()
    events.push(event('message_start', { type: 'message_start', message: { id, type: 'message', role: 'assistant', model, content: [], stop_reason: null, stop_sequence: null, stop_details: null, usage: { ...usage, output_tokens: 1 } } }))
    if (o.error) {
      events.push(event('error', { type: 'error', error: { type: 'overloaded_error', message: 'Overloaded' } }))
    } else {
      events.push(event('ping', { type: 'ping' }))
      content.forEach((b, index) => {
        if (b.type === 'thinking') {
          events.push(event('content_block_start', { type: 'content_block_start', index, content_block: { type: 'thinking', thinking: '' } }))
          for (const piece of chunks(b.thinking, 24)) events.push(event('content_block_delta', { type: 'content_block_delta', index, delta: { type: 'thinking_delta', thinking: piece } }))
          events.push(event('content_block_delta', { type: 'content_block_delta', index, delta: { type: 'signature_delta', signature: b.signature } }))
        } else if (b.type === 'text') {
          events.push(event('content_block_start', { type: 'content_block_start', index, content_block: { type: 'text', text: '' } }))
          for (const piece of chunks(b.text, 12)) events.push(event('content_block_delta', { type: 'content_block_delta', index, delta: { type: 'text_delta', text: piece } }))
        } else if (b.type === 'tool_use') {
          events.push(event('content_block_start', { type: 'content_block_start', index, content_block: { type: 'tool_use', id: b.id, name: b.name, input: {} } }))
          for (const piece of chunks(JSON.stringify(b.input), o.chunk ?? 10)) {
            if (o.slow && b.name === 'reply') slowAfter.add(events.length)
            events.push(event('content_block_delta', { type: 'content_block_delta', index, delta: { type: 'input_json_delta', partial_json: piece } }))
          }
        }
        events.push(event('content_block_stop', { type: 'content_block_stop', index }))
      })
      events.push(event('message_delta', { type: 'message_delta', delta: { stop_reason: stop, stop_sequence: null, ...(o.stopDetails ? { stop_details: o.stopDetails } : {}) }, usage: { output_tokens: o.output } }))
      events.push(event('message_stop', { type: 'message_stop' }))
    }
    let i = 0
    const body = new ReadableStream<Uint8Array>({
      async pull(c) {
        if (i >= events.length) return c.close()
        const wait = slowAfter.has(i - 1) ? slowMs : delayMs
        if (i > 0 && wait > 0) await Bun.sleep(wait)
        c.enqueue(events[i++])
      },
    })
    return new Response(body, { headers: headers({ 'content-type': 'text/event-stream; charset=utf-8', 'cache-control': 'no-cache' }) })
  }

  /** A planned answer to a /v1/messages body: what it streams or returns, or the error the API gives. */
  type Planned = { id: string; model: string; content: Block[]; stop: string; stopDetails?: unknown; usage: Omit<Usage, 'output_tokens'>; output: number; marker?: Marker; slow: boolean; studio: boolean; wait: number }

  /** The answer to [body] (headers' [betas]): its validation as the API's, the markers, the canned content, the usage. */
  async function plan(body: any, betas: string[], entry: Logged): Promise<Planned | { error: Response }> {
    const fail = (status: number, type: string, message: string, extra: Record<string, string> = {}) => ({ error: failed(status, type, message, extra) })
    entry.body = body
    entry.model = body?.model
    entry.stream = !!body?.stream
    if (typeof body?.model !== 'string') return fail(400, 'invalid_request_error', 'model: Field required')
    if (typeof body.max_tokens !== 'number' || body.max_tokens < 1) return fail(400, 'invalid_request_error', 'max_tokens: Field required')
    if (!Array.isArray(body.messages) || !body.messages.length) return fail(400, 'invalid_request_error', 'messages: at least one message is required')
    if (!modelIds.includes(body.model)) return fail(404, 'not_found_error', `model: ${body.model}`)
    const model: string = body.model
    // what the 5.5 models refuse (the app's client must never send it)
    if (model === 'claude-opus-5-5' && ['any', 'tool'].includes(body.tool_choice?.type)) return fail(400, 'invalid_request_error', 'tool_choice: type "tool" and "any" are not supported for this model.')
    if (model === 'claude-opus-5-5' && body.thinking && body.thinking.type !== 'adaptive') return fail(400, 'invalid_request_error', `thinking: type "${body.thinking.type}" is not supported for this model; use "adaptive" or omit it.`)
    // effort: the top-level level, and the effort-only system messages (each needs the beta and a model that takes it)
    const top = body.output_config?.effort
    if (top !== undefined && !takesEffort(model)) return fail(400, 'invalid_request_error', `output_config.effort: ${model} does not support effort`)
    if (top !== undefined && !LEVELS.includes(top)) return fail(400, 'invalid_request_error', `output_config.effort: Input should be 'low', 'medium', 'high', 'xhigh' or 'max'`)
    const messagesIn = body.messages as Msg[]
    for (let i = 0; i < messagesIn.length; i++) {
      const m: any = messagesIn[i]
      if (m?.role !== 'system') continue
      if (!m.output_config) return fail(400, 'invalid_request_error', `messages.${i}: the mock takes only effort-only system messages (role system, content [], output_config)`)
      if (!betas.includes(EFFORT_BETA)) return fail(400, 'invalid_request_error', `messages.${i}.output_config: Extra inputs are not permitted`)
      if (!perMessageEffort(model)) return fail(400, 'invalid_request_error', 'output_config.effort requires a model that supports per-turn effort; this model does not')
      if (!Array.isArray(m.content) || m.content.length) return fail(400, 'invalid_request_error', `messages.${i}.content: an effort-only system message has empty content`)
      if (!LEVELS.includes(m.output_config.effort)) return fail(400, 'invalid_request_error', `messages.${i}.output_config.effort: Input should be 'low', 'medium', 'high', 'xhigh' or 'max'`)
    }
    const effortMessages = messagesIn.filter(m => effortOf(m)).length
    const effort: string | undefined = takesEffort(model) ? (effortInEffect(messagesIn) ?? top ?? defaultEffort(model)) : undefined
    Object.assign(entry, { effort_top: top ?? null, effort: effort ?? null, effort_messages: effortMessages })
    // a prefill: the last turn (effort messages aside) must be the user's
    if (model !== 'claude-haiku-4-5' && messagesIn.filter(m => m.role !== 'system').at(-1)?.role !== 'user') return fail(400, 'invalid_request_error', 'This model does not support assistant message prefill. The conversation must end with a user message.')

    const marker = markerOf(body)
    if (marker) entry.marker = marker
    if (marker === 'credit') return fail(400, 'invalid_request_error', 'Your credit balance is too low to access the Anthropic API. Please go to Plans & Billing to upgrade or purchase credits.')
    if (marker === '429') return fail(429, 'rate_limit_error', 'This request would exceed the rate limit for your organization of 50 requests per minute (mock). Please wait and try again.', { 'retry-after': '1' })
    if (marker === '529') return fail(529, 'overloaded_error', 'Overloaded')
    if (marker === '500') return fail(500, 'api_error', 'Internal server error')
    if (marker === 'stream-error' && !body.stream) return fail(529, 'overloaded_error', 'Overloaded')

    const id = `msg_mock_${++seq}`
    let call: string
    let stop: string
    let content: Block[]
    let stopDetails: unknown
    if (marker === 'refusal') {
      call = 'refusal'
      stop = 'refusal'
      content = []
      stopDetails = { type: 'refusal', category: 'cyber', explanation: 'The mock declined this request ([mock:refusal]).' }
    } else if (marker === 'max_tokens') {
      call = 'max_tokens'
      stop = 'max_tokens'
      content = [{ type: 'text', text: '**Mock tutor** · To je dolg odgovor, ki se tu pret' }]
    } else if (marker === 'stream-error') {
      call = 'stream_error'
      stop = 'error'
      content = []
    } else {
      ;({ call, stop, content } = answer(body, id, effort ?? 'none', marker === 'no-reply'))
    }
    // a refusal before any output isn't billed and caches nothing
    const usage = marker === 'refusal'
      ? { input_tokens: 0, cache_creation_input_tokens: 0, cache_read_input_tokens: 0, cache_creation: { ephemeral_5m_input_tokens: 0, ephemeral_1h_input_tokens: 0 } }
      : usageOf(model, body, true)
    const output = outputTokens(content, effort)
    const studio = isStudio(body)
    Object.assign(entry, { call, stop_reason: stop, usage: { ...usage, output_tokens: output }, ...(studio ? { studio: true } : {}), ...(content.length ? { tools_used: content.filter(b => b.type === 'tool_use').map(b => b.name) } : {}) })
    const turn = turnText(body)
    return { id, model, content, stop, stopDetails, usage, output, marker, slow: turn.includes('[mock:slow]'), studio, wait: studio && turn.includes('[mock:studio-slow]') ? studioMs : 0 }
  }

  /** A planned answer as the API's message JSON (a non-streamed call, a batch's result). */
  const messageOf = (p: Planned) => ({ id: p.id, type: 'message', role: 'assistant', model: p.model, content: p.content, stop_reason: p.stop, stop_sequence: null, stop_details: p.stopDetails ?? null, usage: { ...p.usage, output_tokens: p.output } })

  async function messages(req: Request, entry: Logged): Promise<Response> {
    let body: any
    try {
      body = await req.json()
    } catch {
      return failed(400, 'invalid_request_error', 'The request body is not valid JSON.')
    }
    const betas = (req.headers.get('anthropic-beta') ?? '').split(',').map(b => b.trim())
    const p = await plan(body, betas, entry)
    if ('error' in p) return p.error
    // [mock:studio-slow]: a studio call takes its time (QA kills the app meanwhile)
    if (p.wait) await Bun.sleep(p.wait)
    if (body.stream) return stream(p.id, p.model, p.content, p.stop, p.usage, { stopDetails: p.stopDetails, error: p.marker === 'stream-error', output: p.output, slow: p.slow, chunk: p.studio ? 2000 : 10 })
    return Response.json(messageOf(p), { headers: headers() })
  }

  // --- Message Batches (POST /v1/messages/batches; docs: build-with-claude/batch-processing) ------------------------
  const BATCH_ID = /^[a-zA-Z0-9_-]{1,64}$/
  /** The batch as the API shows it, at [now]: in progress until its time, then ended with its results' URL. */
  function batchView(b: Batch, base: string, now = Date.now()) {
    const ended = b.cancelAt !== undefined ? now >= b.cancelAt : now >= b.endsAt
    const status = ended ? 'ended' : b.cancelAt !== undefined ? 'canceling' : 'in_progress'
    const counts = { processing: 0, succeeded: 0, errored: 0, canceled: 0, expired: 0 }
    for (const r of b.results) {
      if (!ended) counts.processing++
      else if (b.cancelAt !== undefined) counts.canceled++
      else if (r.result.type === 'succeeded') counts.succeeded++
      else counts.errored++
    }
    const at = (ms: number) => new Date(ms).toISOString()
    return {
      id: b.id, type: 'message_batch', processing_status: status, request_counts: counts,
      ended_at: ended ? at(b.cancelAt ?? b.endsAt) : null, created_at: at(b.created), expires_at: at(b.created + 86_400_000),
      archived_at: null, cancel_initiated_at: b.cancelInitiated !== undefined ? at(b.cancelInitiated) : null,
      results_url: ended ? `${base}/v1/messages/batches/${b.id}/results` : null,
    }
  }

  async function batchCreate(req: Request, url: URL, entry: Logged): Promise<Response> {
    let body: any
    try {
      body = await req.json()
    } catch {
      return failed(400, 'invalid_request_error', 'The request body is not valid JSON.')
    }
    const list = body?.requests
    if (!Array.isArray(list) || !list.length) return failed(400, 'invalid_request_error', 'requests: at least one request is required')
    if (list.length > 100_000) return failed(400, 'invalid_request_error', 'requests: at most 100000')
    const ids = new Set<string>()
    for (const [i, r] of list.entries()) {
      if (typeof r?.custom_id !== 'string' || !BATCH_ID.test(r.custom_id)) return failed(400, 'invalid_request_error', `requests.${i}.custom_id: String should match pattern '^[a-zA-Z0-9_-]{1,64}$'`)
      if (ids.has(r.custom_id)) return failed(400, 'invalid_request_error', `requests.${i}.custom_id: duplicate custom_id ${r.custom_id}`)
      ids.add(r.custom_id)
      if (!r.params || typeof r.params !== 'object') return failed(400, 'invalid_request_error', `requests.${i}.params: Field required`)
      if (r.params.stream) return failed(400, 'invalid_request_error', `requests.${i}.params.stream: streaming is not supported in a batch`)
    }
    const betas = (req.headers.get('anthropic-beta') ?? '').split(',').map(b => b.trim())
    const results: Batch['results'] = []
    const answered: Logged[] = []
    for (const r of list) {
      const e: Logged = { custom_id: r.custom_id }
      const p = await plan(r.params, betas, e)
      if ('error' in p) {
        const err: any = await p.error.json()
        results.push({ custom_id: r.custom_id, result: { type: 'errored', error: { type: 'error', error: { type: err?.error?.type ?? 'invalid_request_error', message: err?.error?.message ?? 'invalid request' } } } })
      } else results.push({ custom_id: r.custom_id, result: { type: 'succeeded', message: messageOf(p) } })
      answered.push({ custom_id: r.custom_id, model: e.model, call: e.call, stop_reason: e.stop_reason, studio: e.studio ?? false, tools_used: e.tools_used, body: r.params })
    }
    const now = Date.now()
    const b: Batch = { id: `msgbatch_mock_${sha(`${now}${Math.random()}`).slice(0, 20)}`, created: now, endsAt: now + batchMs, results }
    batches.set(b.id, b)
    Object.assign(entry, { call: 'batch_create', batch: b.id, requests: answered })
    return Response.json(batchView(b, `${url.protocol}//${url.host}`), { headers: headers() })
  }

  function batchRoute(req: Request, url: URL, entry: Logged): Response | undefined {
    const m = /^\/v1\/messages\/batches\/([\w-]+)(\/results|\/cancel)?$/.exec(url.pathname)
    if (!m) return undefined
    const b = batches.get(m[1])
    if (!b) return failed(404, 'not_found_error', `batch ${m[1]} not found`)
    const base = `${url.protocol}//${url.host}`
    entry.batch = b.id
    if (!m[2] && req.method === 'GET') {
      entry.call = 'batch_get'
      const view = batchView(b, base)
      entry.processing_status = view.processing_status
      return Response.json(view, { headers: headers() })
    }
    if (m[2] === '/cancel' && req.method === 'POST') {
      entry.call = 'batch_cancel'
      const now = Date.now()
      if (now < b.endsAt && b.cancelAt === undefined) {
        b.cancelInitiated = now
        b.cancelAt = now + Math.min(batchMs, 300)
      }
      return Response.json(batchView(b, base), { headers: headers() })
    }
    if (m[2] === '/results' && req.method === 'GET') {
      entry.call = 'batch_results'
      const view = batchView(b, base)
      if (view.processing_status !== 'ended') return failed(400, 'invalid_request_error', `batch ${b.id} is still ${view.processing_status}: its results come once it has ended`)
      const lines = b.results.map(r => JSON.stringify(b.cancelAt !== undefined ? { custom_id: r.custom_id, result: { type: 'canceled' } } : r))
      return new Response(lines.join('\n') + '\n', { headers: headers({ 'content-type': 'application/binary' }) })
    }
    return failed(404, 'not_found_error', `Not found: ${req.method} ${url.pathname}`)
  }

  async function route(req: Request, url: URL, entry: Logged): Promise<Response> {
    if (url.pathname === '/mock/requests' && req.method === 'GET') return Response.json(requests)
    if (url.pathname === '/mock/reset' && req.method === 'POST') {
      prefixes = new Map()
      previous = new Map()
      spent = new Set()
      requests = []
      batches = new Map()
      return Response.json({ ok: true })
    }
    if (!url.pathname.startsWith('/v1/')) return failed(404, 'not_found_error', `Not found: ${url.pathname}`)
    const key = req.headers.get('x-api-key')
    if (!key) return failed(401, 'authentication_error', 'x-api-key header is required')
    if (key.includes('bad')) return failed(401, 'authentication_error', 'invalid x-api-key')
    const version = req.headers.get('anthropic-version')
    if (!version) return failed(400, 'invalid_request_error', 'anthropic-version: header is required')
    if (version !== VERSION) return failed(400, 'invalid_request_error', `anthropic-version: "${version}" is not a valid version`)
    if (url.pathname === '/v1/models' && req.method === 'GET') {
      const data = models()
      entry.call = 'models'
      return Response.json({ data, has_more: false, first_id: data[0]?.id ?? null, last_id: data[data.length - 1]?.id ?? null }, { headers: headers() })
    }
    const one = /^\/v1\/models\/([\w.-]+)$/.exec(url.pathname)
    if (one && req.method === 'GET') {
      entry.call = 'model'
      const m = models().find(x => x.id === one[1])
      return m ? Response.json(m, { headers: headers() }) : failed(404, 'not_found_error', `model: ${one[1]}`)
    }
    if (url.pathname === '/v1/messages' && req.method === 'POST') return messages(req, entry)
    if (url.pathname === '/v1/messages/batches' && req.method === 'POST') return batchCreate(req, url, entry)
    const batch = batchRoute(req, url, entry)
    if (batch) return batch
    return failed(404, 'not_found_error', `Not found: ${req.method} ${url.pathname}`)
  }

  const server = Bun.serve({
    hostname: host,
    port: o.port ?? 8771,
    idleTimeout: 0,
    async fetch(req) {
      const url = new URL(req.url)
      const mock = url.pathname.startsWith('/mock/')
      const entry: Logged = { at: new Date().toISOString(), method: req.method, path: url.pathname, has_key: !!req.headers.get('x-api-key') }
      const version = req.headers.get('anthropic-version')
      const beta = req.headers.get('anthropic-beta')
      if (version) entry.anthropic_version = version
      if (beta) entry.anthropic_beta = beta
      const res = await route(req, url, entry)
      if (mock) return res
      entry.status = res.status
      remember(entry)
      const u = entry.usage as Usage | undefined
      say(
        `[tutor-mock] ${req.method} ${url.pathname} ${res.status}${entry.model ? ` model=${entry.model}` : ''}${entry.effort ? ` effort=${entry.effort}${entry.effort_messages ? ` (${entry.effort_messages} effort message(s))` : ''}` : ''}${entry.call ? ` call=${entry.call}` : ''}${entry.marker ? ` marker=${entry.marker}` : ''}${entry.stream ? ' stream' : ''}` +
          `${entry.stop_reason ? ` stop=${entry.stop_reason}` : ''}${u ? ` usage in=${u.input_tokens} write5m=${u.cache_creation.ephemeral_5m_input_tokens} write1h=${u.cache_creation.ephemeral_1h_input_tokens} read=${u.cache_read_input_tokens} out=${u.output_tokens}` : ''}` +
          ` key: ${entry.has_key ? 'present' : 'absent'}`,
      )
      return res
    },
  })
  return { port: server.port as number, url: `http://${host}:${server.port}`, stop: () => server.stop(true) }
}

if (import.meta.main) {
  const env = process.env
  const mock = startTutorMock({
    port: Number(env.LANI_TUTOR_MOCK_PORT ?? 8771),
    delayMs: env.LANI_TUTOR_MOCK_DELAY_MS !== undefined ? Number(env.LANI_TUTOR_MOCK_DELAY_MS) : 15,
    slowMs: env.LANI_TUTOR_MOCK_SLOW_MS !== undefined ? Number(env.LANI_TUTOR_MOCK_SLOW_MS) : 150,
    studioMs: env.LANI_TUTOR_MOCK_STUDIO_MS !== undefined ? Number(env.LANI_TUTOR_MOCK_STUDIO_MS) : 4000,
    batchMs: env.LANI_TUTOR_MOCK_BATCH_MS !== undefined ? Number(env.LANI_TUTOR_MOCK_BATCH_MS) : 1500,
    models: env.LANI_TUTOR_MOCK_MODELS?.split(',').map(s => s.trim()).filter(Boolean),
  })
  console.log(`tutor mock on ${mock.url} (the emulator: http://10.0.2.2:${mock.port}); GET /mock/requests, POST /mock/reset`)
}
