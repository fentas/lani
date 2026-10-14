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
// [mock:max_tokens] (a text cut off), [mock:stream-error] (message_start, then an error event); [mock:credit] (a 400:
// the credit balance is too low) acts every time.
//
// Usage looks like caching, tokens estimated as characters / 4: the prefix (tools + system) is written once per model
// (1 hour when the last system block's cache_control says ttl "1h", else 5 minutes) and read while it lives; of the
// messages, what the previous request of the model had too (within 5 minutes) is read, the rest but the last message
// written for 5 minutes (with a top-level cache_control), the last message is input.
//
// GET /mock/requests: the last 50 requests (method, path, model, call, status, stop_reason, usage, body, has_key; never
// the key). POST /mock/reset: forgets the cache, the markers' once and the log.
import { createHash } from 'node:crypto'

export type MockOptions = { port?: number; host?: string; delayMs?: number; models?: string[]; log?: (line: string) => void }

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

const MARKERS_ONCE = ['429', '529', '500', 'stream-error', 'refusal', 'max_tokens'] as const
type Marker = (typeof MARKERS_ONCE)[number] | 'credit'

const KINDS = ['session_end', 'roleplay_end', 'roleplay', 'review_done', 'answer', 'daily_morning', 'daily_evening', 'daily_weekly', 'villager_arrived', 'level_up', 'chat']

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

const errorBody = (type: string, message: string) => ({ type: 'error', error: { type, message }, request_id: `req_mock_${sha(`${type}${message}${Date.now()}${Math.random()}`).slice(0, 24)}` })

export function startTutorMock(o: MockOptions = {}) {
  const host = o.host ?? '127.0.0.1'
  const delayMs = o.delayMs ?? 15
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
  function answer(body: any, id: string) {
    const messages = body.messages as Msg[]
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

    // 1. the turn's first call: think, say a word, look things up
    if (!results) {
      const guide = tools.get('guide')
      const topics: unknown[] = guide?.input_schema?.properties?.topic?.enum ?? []
      const looks: Block[] = []
      if (guide) looks.push(toolUse('guide', { topic: topics.includes('feedback') ? 'feedback' : (topics[0] ?? 'events') }))
      if (tools.has('learner_state')) looks.push(toolUse('learner_state', {}))
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
      const said = kind === 'answer' ? 'ocenil sem odgovor · I graded the answer' : `videl sem dogodek ${kind} · I saw a ${kind} event`
      const reply: Block[] = [toolUse('reply', { conversation_id: conversation, text: `**Mock tutor** · ${said}.`, ...(kind === 'answer' ? { data: { score: 8, severity: '🟢' } } : {}) })]
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

  /** The marker that acts on this request, if any (a once-marker is spent here). */
  function markerOf(body: any): Marker | undefined {
    const messages = body.messages as Msg[]
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
  const fail = (status: number, type: string, message: string, extra: Record<string, string> = {}) => Response.json(errorBody(type, message), { status, headers: headers(extra) })

  const outputTokens = (content: Block[]) =>
    tokens(content.map(b => (b.type === 'text' ? b.text : b.type === 'thinking' ? b.thinking : b.type === 'tool_use' ? JSON.stringify(b.input) : '')).join('')) +
    (content.some(b => b.type === 'thinking') ? 40 : 0)

  function stream(id: string, model: string, content: Block[], stop: string, usage: Omit<Usage, 'output_tokens'>, o: { stopDetails?: unknown; error?: boolean; output: number }) {
    const enc = new TextEncoder()
    const event = (type: string, data: unknown) => enc.encode(`event: ${type}\ndata: ${JSON.stringify(data)}\n\n`)
    const events: Uint8Array[] = []
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
          for (const piece of chunks(JSON.stringify(b.input), 10)) events.push(event('content_block_delta', { type: 'content_block_delta', index, delta: { type: 'input_json_delta', partial_json: piece } }))
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
        if (i > 0 && delayMs > 0) await Bun.sleep(delayMs)
        c.enqueue(events[i++])
      },
    })
    return new Response(body, { headers: headers({ 'content-type': 'text/event-stream; charset=utf-8', 'cache-control': 'no-cache' }) })
  }

  async function messages(req: Request, entry: Logged): Promise<Response> {
    let body: any
    try {
      body = await req.json()
    } catch {
      return fail(400, 'invalid_request_error', 'The request body is not valid JSON.')
    }
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
    if (model !== 'claude-haiku-4-5' && body.messages[body.messages.length - 1]?.role !== 'user') return fail(400, 'invalid_request_error', 'This model does not support assistant message prefill. The conversation must end with a user message.')

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
      ;({ call, stop, content } = answer(body, id))
    }
    // a refusal before any output isn't billed and caches nothing
    const usage = marker === 'refusal'
      ? { input_tokens: 0, cache_creation_input_tokens: 0, cache_read_input_tokens: 0, cache_creation: { ephemeral_5m_input_tokens: 0, ephemeral_1h_input_tokens: 0 } }
      : usageOf(model, body, true)
    const output = outputTokens(content)
    Object.assign(entry, { call, stop_reason: stop, usage: { ...usage, output_tokens: output }, ...(content.length ? { tools_used: content.filter(b => b.type === 'tool_use').map(b => b.name) } : {}) })
    if (body.stream) return stream(id, model, content, stop, usage, { stopDetails, error: marker === 'stream-error', output })
    return Response.json(
      { id, type: 'message', role: 'assistant', model, content, stop_reason: stop, stop_sequence: null, stop_details: stopDetails ?? null, usage: { ...usage, output_tokens: output } },
      { headers: headers() },
    )
  }

  async function route(req: Request, url: URL, entry: Logged): Promise<Response> {
    if (url.pathname === '/mock/requests' && req.method === 'GET') return Response.json(requests)
    if (url.pathname === '/mock/reset' && req.method === 'POST') {
      prefixes = new Map()
      previous = new Map()
      spent = new Set()
      requests = []
      return Response.json({ ok: true })
    }
    if (!url.pathname.startsWith('/v1/')) return fail(404, 'not_found_error', `Not found: ${url.pathname}`)
    const key = req.headers.get('x-api-key')
    if (!key) return fail(401, 'authentication_error', 'x-api-key header is required')
    if (key.includes('bad')) return fail(401, 'authentication_error', 'invalid x-api-key')
    const version = req.headers.get('anthropic-version')
    if (!version) return fail(400, 'invalid_request_error', 'anthropic-version: header is required')
    if (version !== VERSION) return fail(400, 'invalid_request_error', `anthropic-version: "${version}" is not a valid version`)
    if (url.pathname === '/v1/models' && req.method === 'GET') {
      const data = models()
      entry.call = 'models'
      return Response.json({ data, has_more: false, first_id: data[0]?.id ?? null, last_id: data[data.length - 1]?.id ?? null }, { headers: headers() })
    }
    const one = /^\/v1\/models\/([\w.-]+)$/.exec(url.pathname)
    if (one && req.method === 'GET') {
      entry.call = 'model'
      const m = models().find(x => x.id === one[1])
      return m ? Response.json(m, { headers: headers() }) : fail(404, 'not_found_error', `model: ${one[1]}`)
    }
    if (url.pathname === '/v1/messages' && req.method === 'POST') return messages(req, entry)
    return fail(404, 'not_found_error', `Not found: ${req.method} ${url.pathname}`)
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
        `[tutor-mock] ${req.method} ${url.pathname} ${res.status}${entry.model ? ` model=${entry.model}` : ''}${entry.call ? ` call=${entry.call}` : ''}${entry.marker ? ` marker=${entry.marker}` : ''}${entry.stream ? ' stream' : ''}` +
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
    models: env.LANI_TUTOR_MOCK_MODELS?.split(',').map(s => s.trim()).filter(Boolean),
  })
  console.log(`tutor mock on ${mock.url} (the emulator: http://10.0.2.2:${mock.port}); GET /mock/requests, POST /mock/reset`)
}
