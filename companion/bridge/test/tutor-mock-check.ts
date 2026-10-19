#!/usr/bin/env bun
// A quick self-check of the Messages API mock (test/tutor-mock.ts): starts it on a free port as QA runs it, then plays
// the app's client with plain fetch: the models, a turn's first call (streamed), its tool results, the call after the
// reply, a 429 and its retry, a refusal, the credit error, a stream error, a non-streamed call, the effort (top-level, per
// message with its beta, the thinking it weighs, Haiku 5.5 at low and [mock:no-reply] without a reply), the caching usage,
// the studio (a chat's request_content, a content job: publish_module refused by the real validator then taken, the text
// editor's story file, a sub-task, a slow call, no marker), the Message Batches (results, an errored request, a cancel) and
// the request log (the key never in it).
//
//   bun companion/bridge/test/tutor-mock-check.ts
import { resolve } from 'node:path'
import { validateModule } from '../src/spec'
import { validateStory } from '../src/stories'

let failures = 0
const check = (name: string, cond: unknown, detail?: unknown) => {
  console.log(`${cond ? '✅' : '❌'} ${name}`, cond ? '' : (detail === undefined ? '' : typeof detail === 'string' ? detail : JSON.stringify(detail, null, 1)))
  if (!cond) failures++
}

const KEY = 'sk-mock-check-1234567890'
const mock = Bun.spawn(['bun', resolve(import.meta.dir, 'tutor-mock.ts')], {
  env: { ...process.env, LANI_TUTOR_MOCK_PORT: '0', LANI_TUTOR_MOCK_DELAY_MS: '1', LANI_TUTOR_MOCK_SLOW_MS: '20', LANI_TUTOR_MOCK_STUDIO_MS: '300', LANI_TUTOR_MOCK_BATCH_MS: '200' },
  stdout: 'pipe',
  stderr: 'inherit',
})
process.on('exit', () => mock.kill())
const reader = mock.stdout.getReader()
let out = ''
let port = 0
while (!port) {
  const { value, done } = await reader.read()
  if (done) break
  out += new TextDecoder().decode(value)
  port = Number(/127\.0\.0\.1:(\d+)/.exec(out)?.[1] ?? 0)
}
if (!port) {
  console.error(`the mock didn't start:\n${out}`)
  process.exit(1)
}
// the rest of its log, for the check that the key never shows
void (async () => {
  for (;;) {
    const { value, done } = await reader.read()
    if (done) return
    out += new TextDecoder().decode(value)
  }
})()
console.log(`mock on port ${port}`)
const base = `http://127.0.0.1:${port}`
const H = { 'x-api-key': KEY, 'anthropic-version': '2023-06-01', 'content-type': 'application/json' }

type Event = { event: string; data: any }
async function sse(r: Response): Promise<Event[]> {
  const text = await r.text()
  return text
    .split('\n\n')
    .filter(Boolean)
    .map(chunk => {
      const lines = chunk.split('\n')
      return { event: lines.find(l => l.startsWith('event: '))?.slice(7) ?? '', data: JSON.parse(lines.find(l => l.startsWith('data: '))?.slice(6) ?? 'null') }
    })
}

/** The message a stream adds up to, as the app's client builds it: blocks by index, tool inputs from their partial JSON. */
function assemble(events: Event[]) {
  const start = events.find(e => e.event === 'message_start')?.data.message
  const blocks: any[] = []
  const partial: string[] = []
  const pieces: number[] = []
  for (const { event, data } of events) {
    if (event === 'content_block_start') blocks[data.index] = structuredClone(data.content_block)
    if (event === 'content_block_delta') {
      const b = blocks[data.index]
      const d = data.delta
      if (d.type === 'text_delta') b.text += d.text
      if (d.type === 'thinking_delta') b.thinking += d.thinking
      if (d.type === 'signature_delta') b.signature = d.signature
      if (d.type === 'input_json_delta') {
        partial[data.index] = (partial[data.index] ?? '') + d.partial_json
        pieces.push(d.partial_json.length)
      }
    }
    if (event === 'content_block_stop' && blocks[data.index]?.type === 'tool_use') blocks[data.index].input = JSON.parse(partial[data.index] || '{}')
  }
  const delta = events.find(e => e.event === 'message_delta')?.data
  return { start, content: blocks, stop: delta?.delta?.stop_reason, details: delta?.delta?.stop_details, output: delta?.usage?.output_tokens, pieces }
}

const post = (body: unknown, headers: Record<string, string> = H) => fetch(`${base}/v1/messages`, { method: 'POST', headers, body: JSON.stringify(body) })

// --- auth and the models -------------------------------------------------------------------------------------------
const noKey = await fetch(`${base}/v1/models`, { headers: { 'anthropic-version': '2023-06-01' } })
check('no x-api-key: 401 authentication_error', noKey.status === 401 && (await noKey.json()).error?.type === 'authentication_error')
const bad = await fetch(`${base}/v1/models`, { headers: { ...H, 'x-api-key': 'sk-bad-key' } })
const badBody = await bad.json()
check('a key with "bad": 401 {"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}', bad.status === 401 && badBody.type === 'error' && badBody.error.type === 'authentication_error' && badBody.error.message === 'invalid x-api-key', badBody)
const noVersion = await fetch(`${base}/v1/models`, { headers: { 'x-api-key': KEY } })
check('no anthropic-version: 400 invalid_request_error', noVersion.status === 400 && (await noVersion.json()).error?.type === 'invalid_request_error')
const models = await (await fetch(`${base}/v1/models`, { headers: H })).json()
const ids = models.data?.map((m: any) => m.id)
check(
  'GET /v1/models: the four models newest first, each {type, id, display_name, created_at, max_input_tokens, max_tokens}, has_more false, first_id and last_id',
  JSON.stringify(ids) === JSON.stringify(['claude-opus-5-5', 'claude-sonnet-5-5', 'claude-haiku-5-5', 'claude-haiku-4-5']) &&
    models.has_more === false && models.first_id === ids[0] && models.last_id === ids[3] &&
    models.data.every((m: any, i: number) => m.type === 'model' && m.display_name && m.created_at && m.max_input_tokens > 0 && m.max_tokens > 0 && (i === 0 || models.data[i - 1].created_at >= m.created_at)),
  models,
)

// --- a turn: the first call, the tool results, after the reply -------------------------------------------------------
const tools = [
  { name: 'guide', description: 'One topic.', input_schema: { type: 'object', properties: { topic: { type: 'string', enum: ['events', 'feedback'] } }, required: ['topic'] } },
  { name: 'learner_state', description: 'State.', input_schema: { type: 'object', properties: {} } },
  { name: 'reply', description: 'Reply.', input_schema: { type: 'object', properties: { conversation_id: { type: 'string' }, text: { type: 'string' } }, required: ['conversation_id', 'text'] } },
  { name: 'notebook_note', description: 'Note.', input_schema: { type: 'object', properties: { text: { type: 'string' } }, required: ['text'] } },
]
const system = [{ type: 'text', text: 'You are the Lani tutor (mock check). '.repeat(80), cache_control: { type: 'ephemeral', ttl: '1h' } }]
const prefixTokens = Math.ceil((JSON.stringify(tools) + JSON.stringify(system)).length / 4)
const event = { role: 'user', content: [{ type: 'text', text: '<event kind="answer" conversation_id="c-42">The answer: Jaz sem Jan.</event>' }] }
const request = (messages: unknown[], extra: Record<string, unknown> = {}) => ({ model: 'claude-sonnet-5-5', max_tokens: 4096, stream: true, cache_control: { type: 'ephemeral' }, tool_choice: { type: 'auto' }, tools, system, messages, ...extra })

const first = await post(request([event]))
const firstEvents = await sse(first)
const one = assemble(firstEvents)
const order = firstEvents.map(e => e.event)
check('first call: text/event-stream, message_start then ping first, message_delta then message_stop last', first.headers.get('content-type')?.startsWith('text/event-stream') && order[0] === 'message_start' && order[1] === 'ping' && order.at(-2) === 'message_delta' && order.at(-1) === 'message_stop', order)
const msg = one.start
check(
  'message_start: {id msg_mock_…, type message, role assistant, model, content [], stop_reason null, stop_sequence null, usage with cache_creation}',
  /^msg_mock_\d+$/.test(msg?.id) && msg.type === 'message' && msg.role === 'assistant' && msg.model === 'claude-sonnet-5-5' && Array.isArray(msg.content) && !msg.content.length && msg.stop_reason === null && msg.stop_sequence === null &&
    ['input_tokens', 'cache_creation_input_tokens', 'cache_read_input_tokens', 'output_tokens'].every(k => typeof msg.usage?.[k] === 'number') && typeof msg.usage.cache_creation?.ephemeral_5m_input_tokens === 'number' && typeof msg.usage.cache_creation?.ephemeral_1h_input_tokens === 'number',
  msg,
)
const [thinking, text, guide, state] = one.content
check('first call: a thinking block (thinking_delta, then one signature_delta), a text, then guide {topic: "feedback"} and learner_state {}; stop tool_use',
  thinking?.type === 'thinking' && thinking.thinking.length > 0 && thinking.signature?.length > 0 && firstEvents.filter(e => e.data?.delta?.type === 'signature_delta').length === 1 &&
    text?.type === 'text' && text.text.startsWith('Pogledam') &&
    guide?.type === 'tool_use' && guide.name === 'guide' && guide.input.topic === 'feedback' && /^toolu_mock_/.test(guide.id) &&
    state?.type === 'tool_use' && state.name === 'learner_state' && JSON.stringify(state.input) === '{}' && one.content.length === 4 && one.stop === 'tool_use',
  one.content)
check('tool inputs stream as input_json_delta in pieces of at most 10 characters', one.pieces.length >= 3 && one.pieces.every(n => n <= 10), one.pieces)
check('first call: the prefix written for an hour (cache_control ttl 1h), nothing read', msg.usage.cache_creation.ephemeral_1h_input_tokens === prefixTokens && msg.usage.cache_read_input_tokens === 0, { usage: msg.usage, prefixTokens })

const assistant1 = { role: 'assistant', content: one.content }
const results1 = { role: 'user', content: [{ type: 'tool_result', tool_use_id: guide.id, content: 'Feedback: …' }, { type: 'tool_result', tool_use_id: state.id, content: '{"computed": {"next_session_id": "session-007"}}' }] }
const second = assemble(await sse(await post(request([event, assistant1, results1]))))
const reply = second.content.find((b: any) => b.name === 'reply')
const note = second.content.find((b: any) => b.name === 'notebook_note')
check('tool results: reply {conversation_id "c-42", text "**Mock tutor** · …", data {score 8, severity}} and notebook_note beside it; stop tool_use',
  reply?.input.conversation_id === 'c-42' && reply.input.text.startsWith('**Mock tutor** · ') && reply.input.data?.score === 8 && reply.input.data?.severity && note?.input.text.startsWith('mock: ') && second.stop === 'tool_use', second.content)
const u2 = second.start.usage
check('tool results: the prefix read from the cache, the first message too (the previous request had it), the assistant turn written for 5 minutes',
  u2.cache_read_input_tokens === prefixTokens + Math.ceil(JSON.stringify(event).length / 4) && u2.cache_creation.ephemeral_5m_input_tokens === Math.ceil(JSON.stringify(assistant1).length / 4) && u2.cache_creation.ephemeral_1h_input_tokens === 0 &&
    u2.cache_creation_input_tokens === u2.cache_creation.ephemeral_5m_input_tokens && u2.input_tokens === Math.ceil(JSON.stringify(results1).length / 4),
  u2)

const assistant2 = { role: 'assistant', content: second.content }
const results2 = { role: 'user', content: second.content.map((b: any) => ({ type: 'tool_result', tool_use_id: b.id, content: 'delivered' })) }
const third = assemble(await sse(await post(request([event, assistant1, results1, assistant2, results2]))))
check('after the reply: a text "Opravljeno.", stop end_turn', third.stop === 'end_turn' && third.content.length === 1 && third.content[0].text === 'Opravljeno.', third)

// session_end: record_session beside the reply, with the session id from learner_state's result
const ended = { role: 'user', content: 'kind: session_end, conversation_id: run-3, 2026-10-14 {"results": []}' }
const sTools = [...tools, { name: 'record_session', description: 'Record.', input_schema: { type: 'object', properties: {} } }]
const sFirst = assemble(await sse(await post(request([ended], { tools: sTools }))))
const sSecond = assemble(await sse(await post(request([ended, { role: 'assistant', content: sFirst.content }, { role: 'user', content: sFirst.content.filter((b: any) => b.type === 'tool_use').map((b: any) => ({ type: 'tool_result', tool_use_id: b.id, content: '{"next_session_id": "session-012"}' })) }], { tools: sTools }))))
const rec = sSecond.content.find((b: any) => b.name === 'record_session')
check('session_end: record_session beside the reply (session-012 from the tool result, the turn\'s date)', rec?.input.session_id === 'session-012' && rec.input.date === '2026-10-14' && sSecond.content.some((b: any) => b.name === 'reply' && b.input.conversation_id === 'run-3'), sSecond.content)

// the closing call
const closing = { role: 'user', content: 'The day is over. [notebook_write]' }
const nTools = [...tools, { name: 'notebook_write', description: 'Write.', input_schema: { type: 'object', properties: { text: { type: 'string' } } } }]
const c1 = assemble(await sse(await post(request([closing], { tools: nTools }))))
const c2 = assemble(await sse(await post(request([closing, { role: 'assistant', content: c1.content }, { role: 'user', content: [{ type: 'tool_result', tool_use_id: c1.content[0]?.id, content: 'saved' }] }], { tools: nTools }))))
check('[notebook_write]: notebook_write {text "# Notebook (mock)…"} (tool_use), then end_turn', c1.content[0]?.name === 'notebook_write' && c1.content[0].input.text.startsWith('# Notebook (mock)') && c1.stop === 'tool_use' && c2.stop === 'end_turn', { c1, c2 })

// [mock:slow]: a long reply whose input streams slowly (the live tutor's typing bubble, QA's screenshot mid-way)
const slowTurn = { role: 'user', content: '<event kind="chat" conversation_id="main">Živjo [mock:slow]</event>' }
const slow1 = assemble(await sse(await post(request([slowTurn]))))
const slowAt = Date.now()
const slow2 = assemble(await sse(await post(request([slowTurn, { role: 'assistant', content: slow1.content }, { role: 'user', content: slow1.content.filter((b: any) => b.type === 'tool_use').map((b: any) => ({ type: 'tool_result', tool_use_id: b.id, content: 'ok' })) }]))))
const slowMs = Date.now() - slowAt
const slowReply = slow2.content.find((b: any) => b.name === 'reply')
check('[mock:slow]: a long reply ("Kako si danes?"), its input in many pieces with LANI_TUTOR_MOCK_SLOW_MS between them', slowReply?.input.text.includes('Kako si danes?') && slow2.pieces.length > 30 && slowMs >= 20 * 30, { ms: slowMs, pieces: slow2.pieces.length })

// --- errors and markers -------------------------------------------------------------------------------------------
const limited = { role: 'user', content: 'kind="chat" conversation_id="main" Zdravo! [mock:429]' }
const r429 = await post(request([limited]))
const b429 = await r429.json()
check('[mock:429]: HTTP 429, retry-after: 1, a rate_limit_error body', r429.status === 429 && r429.headers.get('retry-after') === '1' && b429.type === 'error' && b429.error.type === 'rate_limit_error' && typeof b429.request_id === 'string', { status: r429.status, b429 })
const retried = await post(request([limited]))
check('… the retry of the same message goes through', retried.status === 200 && assemble(await sse(retried)).stop === 'tool_use')

const refused = assemble(await sse(await post(request([{ role: 'user', content: 'Kaj je to? [mock:refusal]' }]))))
check('[mock:refusal]: stop_reason refusal with stop_details {type refusal, category cyber, explanation}, no tool use', refused.stop === 'refusal' && refused.details?.type === 'refusal' && refused.details.category === 'cyber' && typeof refused.details.explanation === 'string' && !refused.content.some((b: any) => b?.type === 'tool_use'), refused)

const credit = { role: 'user', content: 'Hello [mock:credit]' }
const cr1 = await post(request([credit]))
const cr2 = await post(request([credit]))
const crBody = await cr2.json()
check('[mock:credit]: HTTP 400 "Your credit balance is too low…", every time', cr1.status === 400 && cr2.status === 400 && crBody.error.type === 'invalid_request_error' && crBody.error.message.startsWith('Your credit balance is too low to access the Anthropic API.'), crBody)

const broken = await sse(await post(request([{ role: 'user', content: 'Hello [mock:stream-error]' }])))
check('[mock:stream-error]: message_start, then an error event (overloaded_error), and the stream ends', broken.length === 2 && broken[0].event === 'message_start' && broken[1].event === 'error' && broken[1].data.error.type === 'overloaded_error', broken)

const cut = assemble(await sse(await post(request([{ role: 'user', content: 'Povej več [mock:max_tokens]' }]))))
const uncut = assemble(await sse(await post(request([{ role: 'user', content: 'Povej več [mock:max_tokens]' }], { max_tokens: 8192 }))))
check('[mock:max_tokens]: a text cut off, stop max_tokens; the retry works', cut.stop === 'max_tokens' && cut.content[0]?.type === 'text' && uncut.stop === 'tool_use', { cut, uncut })

const plain = await (await post({ ...request([event]), stream: false })).json()
check('a non-streamed call: one message JSON (content blocks with full inputs, stop_reason, stop_details null, usage)', plain.type === 'message' && plain.stop_reason === 'tool_use' && plain.stop_details === null && plain.content.some((b: any) => b.type === 'tool_use' && b.name === 'guide' && b.input.topic === 'feedback') && plain.usage?.output_tokens > 0, plain)

const forced = await post({ ...request([event]), model: 'claude-opus-5-5', tool_choice: { type: 'any' } })
check('claude-opus-5-5 refuses a forced tool_choice (400)', forced.status === 400)
const unknown = await post({ ...request([event]), model: 'claude-nope-1' })
check('an unknown model: 404 not_found_error', unknown.status === 404 && (await unknown.json()).error.type === 'not_found_error')

// --- effort: top-level, per message (beta), the thinking it weighs, Haiku at low ----------------------------------
const lastLogged = async () => (await (await fetch(`${base}/mock/requests`)).json()).at(-1)
const BETA = 'mid-conversation-output-config-2026-07-01'
const withBeta = { ...H, 'anthropic-beta': `thinking-binding-controls-2026-08-01,${BETA}` }
const effortMsg = (effort: string) => ({ role: 'system', content: [], output_config: { effort } })
const chatTurn = { role: 'user', content: [{ type: 'text', text: '<event kind="chat" conversation_id="main">Živjo!</event>' }] }
const answered = { role: 'assistant', content: [{ type: 'text', text: 'Živjo, Jan!' }] }
const gradeTurn = { role: 'user', content: [{ type: 'text', text: '<event kind="answer" conversation_id="ex-7">Jaz sem Jan.</event>' }] }
const top = (effort: string) => ({ output_config: { effort } })

const noBeta = await post(request([chatTurn, answered, effortMsg('high'), gradeTurn], top('medium')))
const noBetaBody = await noBeta.json()
check('an effort message without the beta: 400 "messages.2.output_config: Extra inputs are not permitted"', noBeta.status === 400 && noBetaBody.error?.message === 'messages.2.output_config: Extra inputs are not permitted', noBetaBody)
await (await post(request([chatTurn], top('medium')))).text() // the chat's call: what the next one reads from the cache
const graded = await post(request([chatTurn, answered, effortMsg('high'), gradeTurn], top('medium')), withBeta)
const gradedOne = assemble(await sse(graded))
const gradedLog = await lastLogged()
check(
  'with the beta: the effort message before the user turn sets its reply\'s level (high), the top-level stays medium, the history before it read from the cache',
  graded.status === 200 && gradedLog.effort === 'high' && gradedLog.effort_top === 'medium' && gradedLog.effort_messages === 1 && gradedOne.stop === 'tool_use' &&
    gradedOne.start.usage.cache_read_input_tokens === prefixTokens + Math.ceil(JSON.stringify(chatTurn).length / 4),
  { gradedLog, usage: gradedOne.start.usage },
)
await (await post(request([chatTurn, answered, gradeTurn, effortMsg('low')], top('medium')), withBeta)).text()
check('an effort message directly after the user turn sets that reply\'s level; the messages may end with it', (await lastLogged()).effort === 'low')
await (await post(request([chatTurn, effortMsg('high'), answered, gradeTurn], top('medium')), withBeta)).text()
check('an effort message earlier on holds for the later turns', (await lastLogged()).effort === 'high')
const toolTurn = { role: 'assistant', content: [{ type: 'tool_use', id: 'toolu_x', name: 'learner_state', input: {} }] }
const toolDone = { role: 'user', content: [{ type: 'tool_result', tool_use_id: 'toolu_x', content: '{}' }] }
await (await post(request([gradeTurn, toolTurn, toolDone, effortMsg('max')], top('medium')), withBeta)).text()
check('one after a user turn of only tool_results waits for the next turn with new input (the turn\'s level holds)', (await lastLogged()).effort === 'medium')
const legacy = await post({ ...request([chatTurn, effortMsg('high'), gradeTurn]), model: 'claude-haiku-4-5' }, withBeta)
const legacyBody = await legacy.json()
check('the beta on a model without per-message effort: 400 "output_config.effort requires a model that supports per-turn effort…"', legacy.status === 400 && legacyBody.error.message.startsWith('output_config.effort requires a model that supports per-turn effort'), legacyBody)
check('claude-haiku-4-5 takes no effort (400)', (await post({ ...request([chatTurn], top('high')), model: 'claude-haiku-4-5' })).status === 400)
check('an unknown level (400)', (await post(request([chatTurn], top('adaptive')))).status === 400)
check('a system message with text: the mock takes none (400)', (await post(request([chatTurn, answered, { role: 'system', content: 'Be brief.' }, gradeTurn]), withBeta)).status === 400)

const atLow = assemble(await sse(await post(request([gradeTurn], top('low')))))
const atHigh = assemble(await sse(await post(request([gradeTurn], top('high')))))
check('a thinking block weighs by the level: high 260 output tokens more than low', atHigh.output - atLow.output === 260, { low: atLow.output, high: atHigh.output })
const haikuLow = assemble(await sse(await post({ ...request([gradeTurn], top('low')), model: 'claude-haiku-5-5' })))
check('Haiku 5.5 at low: its answer as text, end_turn, no reply call (as in Jan\'s shadow)', haikuLow.stop === 'end_turn' && !haikuLow.content.some((b: any) => b.type === 'tool_use') && haikuLow.content.some((b: any) => b.type === 'text' && b.text.includes('as text, no reply tool')), haikuLow.content)
const haikuHigh = assemble(await sse(await post({ ...request([gradeTurn], top('high')), model: 'claude-haiku-5-5' })))
check('Haiku 5.5 at high: the usual first call (guide, learner_state)', haikuHigh.stop === 'tool_use' && haikuHigh.content.some((b: any) => b.name === 'guide'), haikuHigh.content)
const silentTurn = { role: 'user', content: '<event kind="chat" conversation_id="main">Zdravo [mock:no-reply]</event>' }
const silent1 = assemble(await sse(await post(request([silentTurn]))))
const silent2 = assemble(await sse(await post(request([silentTurn]))))
check('[mock:no-reply]: once the answer as text (end_turn, no tool), then as usual', silent1.stop === 'end_turn' && !silent1.content.some((b: any) => b.type === 'tool_use') && silent2.stop === 'tool_use', { silent1, silent2 })

// --- the studio: a chat hands a brief over, a content job plays ------------------------------------------------------
const askTools = [...tools, { name: 'request_content', description: 'Brief the studio.', input_schema: { type: 'object', properties: { title: { type: 'string' }, brief: { type: 'string' } }, required: ['title', 'brief'] } }]
const askTurn = { role: 'user', content: '<event kind="chat" conversation_id="main">Naredi mi vaje za pozdrave [mock:request]</event>' }
const ask1 = assemble(await sse(await post(request([askTurn], { tools: askTools }))))
const asked = ask1.content.find((b: any) => b.name === 'request_content')
check("[mock:request]: request_content {title, brief with the chat's text and [mock:studio-module], kinds, urgent} beside guide and learner_state",
  asked?.input.title.includes('machine-written') && asked.input.brief.includes('Naredi mi vaje za pozdrave') && asked.input.brief.includes('[mock:studio-module]') && !asked.input.brief.includes('[mock:request') && asked.input.urgent === true && ask1.content.some((b: any) => b.name === 'guide'), ask1.content)
const ask2 = assemble(await sse(await post(request([askTurn, { role: 'assistant', content: ask1.content }, { role: 'user', content: ask1.content.filter((b: any) => b.type === 'tool_use').map((b: any) => ({ type: 'tool_result', tool_use_id: b.id, content: 'queued: job j_abc123' })) }], { tools: askTools }))))
check("…then the reply: it's on its way", ask2.content.find((b: any) => b.name === 'reply')?.input.text.includes('on its way'), ask2.content)
const askBatch = assemble(await sse(await post(request([{ role: 'user', content: 'kind="chat" conversation_id="main" Zgodbe za teden [mock:request:batch]' }], { tools: askTools }))))
check('[mock:request:batch]: urgent false', askBatch.content.find((b: any) => b.name === 'request_content')?.input.urgent === false, askBatch.content)

const studioSystem = [{ type: 'text', text: "# You are the studio of Lani's tutor (mock check)", cache_control: { type: 'ephemeral', ttl: '1h' } }]
const studioTools = [
  { name: 'reply', description: 'Reply.', input_schema: { type: 'object', properties: { conversation_id: { type: 'string' }, text: { type: 'string' } }, required: ['conversation_id', 'text'] } },
  { name: 'guide', description: 'One topic.', input_schema: { type: 'object', properties: { topic: { type: 'string', enum: ['events', 'studio', 'studio-modules'] } }, required: ['topic'] } },
  { name: 'learner_state', description: 'State.', input_schema: { type: 'object', properties: {} } },
  { name: 'publish_module', description: 'Publish.', input_schema: { type: 'object', properties: { spec: { type: 'object' } }, required: ['spec'] } },
  { name: 'studio_subtask', description: 'A sub-task.', input_schema: { type: 'object', properties: { title: { type: 'string' }, brief: { type: 'string' } } } },
  { type: 'text_editor_20250728', name: 'str_replace_based_edit_tool' },
]
const studioReq = (messages: unknown[]) => ({ model: 'claude-opus-5-5', max_tokens: 32000, stream: true, cache_control: { type: 'ephemeral' }, tool_choice: { type: 'auto' }, tools: studioTools, system: studioSystem, messages })
/** Plays a studio job to its end: each tool_use answered by [answer] (its result, its error), at most [max] calls. */
async function playStudio(first: string, answer: (b: any) => { content: string; error?: boolean }, max = 8) {
  const thread: any[] = [{ role: 'user', content: [{ type: 'text', text: first }] }]
  const calls: any[] = []
  for (let i = 0; i < max; i++) {
    const one = assemble(await sse(await post(studioReq(thread))))
    calls.push(one)
    thread.push({ role: 'assistant', content: one.content })
    const uses = one.content.filter((b: any) => b.type === 'tool_use')
    if (one.stop !== 'tool_use' || !uses.length) break
    thread.push({ role: 'user', content: uses.map((b: any) => { const a = answer(b); return { type: 'tool_result', tool_use_id: b.id, content: a.content, ...(a.error ? { is_error: true } : {}) } }) })
  }
  return calls
}
const asBridge = (b: any) => {
  if (b.name === 'publish_module') {
    const v = validateModule(b.input.spec)
    return v.ok ? { content: `published module ${v.spec.id} v2` } : { content: `spec invalid, nothing published:\n${v.errors}`, error: true }
  }
  if (b.name === 'str_replace_based_edit_tool') {
    const v = validateStory(JSON.parse(b.input.file_text))
    return v.ok ? { content: `created ${b.input.path}` } : { content: `Nothing written: ${b.input.path} doesn't validate as a story:\n${v.errors}`, error: true }
  }
  return { content: 'ok' }
}
const studioJob = await playStudio('Studio job (conversation_id: main). Brief: a drill on greetings [mock:studio-module]', asBridge)
const named = studioJob.map(c => c.content.filter((b: any) => b.type === 'tool_use').map((b: any) => b.name).join('+') || c.stop)
check('[mock:studio-module]: guide (studio-modules) + learner_state, publish_module refused, publish_module taken, reply, end_turn',
  JSON.stringify(named) === JSON.stringify(['guide+learner_state', 'publish_module', 'publish_module', 'reply', 'end_turn']) && studioJob[0].content[0].type === 'thinking' && studioJob[0].content.find((b: any) => b.name === 'guide').input.topic === 'studio-modules', named)
const pubs = studioJob.flatMap(c => c.content).filter((b: any) => b.name === 'publish_module')
const firstVerdict = validateModule(pubs[0]?.input.spec)
check("…the first spec is one the bridge's validateModule refuses (answer index out of range), the second one it takes (id mock-studio-drill, 5 exercises, marked machine-written)",
  !firstVerdict.ok && /answer index out of range/.test((firstVerdict as any).errors) && validateModule(pubs[1]?.input.spec).ok && pubs[1].input.spec.id === 'mock-studio-drill' && pubs[1].input.spec.exercises.length >= 5 && pubs[1].input.spec.title.includes('machine-written'), pubs.map(p => p.input.spec.title))
check("…the reply to the job's conversation (\"main\")", studioJob[3]?.content[0]?.input.conversation_id === 'main' && studioJob[3].content[0].input.text.includes('the drill is published'), studioJob[3]?.content)
check("…a studio call's tool inputs stream in big pieces", studioJob[1].pieces.length >= 1 && Math.max(...studioJob[1].pieces) > 100, studioJob[1].pieces)
const resumed = assemble(await sse(await post(studioReq([{ role: 'user', content: [{ type: 'text', text: 'Brief [mock:studio-module]' }] }, { role: 'assistant', content: studioJob[0].content }, { role: 'user', content: studioJob[0].content.filter((b: any) => b.type === 'tool_use').map((b: any) => ({ type: 'tool_result', tool_use_id: b.id, content: 'ok' })) }, { role: 'assistant', content: studioJob[1].content }, { role: 'user', content: [{ type: 'tool_result', tool_use_id: studioJob[1].content[0].id, content: 'refused', is_error: true }] }]))))
check('…a resumed job goes on where its thread stopped (the fixed publish next)', resumed.content[0]?.name === 'publish_module' && validateModule(resumed.content[0].input.spec).ok, resumed.content)

const fileJob = await playStudio('Studio job (conversation_id: c-9): a story [mock:studio-file]', asBridge)
const fileNamed = fileJob.map(c => c.content.filter((b: any) => b.type === 'tool_use').map((b: any) => `${b.name}${b.input?.command ? `:${b.input.command}` : ''}`).join('+') || c.stop)
const creates = fileJob.flatMap(c => c.content).filter((b: any) => b.name === 'str_replace_based_edit_tool')
check('[mock:studio-file]: the text editor (declared by type, no input_schema) creates app/stories/mock-studio-story.json refused by the story validator, then valid, reply to c-9, end_turn',
  JSON.stringify(fileNamed) === JSON.stringify(['str_replace_based_edit_tool:create', 'str_replace_based_edit_tool:create', 'reply', 'end_turn']) && creates.every((b: any) => b.input.path === 'app/stories/mock-studio-story.json') &&
    !validateStory(JSON.parse(creates[0].input.file_text)).ok && validateStory(JSON.parse(creates[1].input.file_text)).ok && JSON.parse(creates[1].input.file_text).title.en.includes('machine-written') && fileJob[2]?.content[0]?.input.conversation_id === 'c-9', fileNamed)

const sub = await playStudio('Studio job: a unit [mock:subtask]', b => ({ content: b.name === 'studio_subtask' ? 'the sub-task published mock-studio-drill' : 'ok' }))
const subNamed = sub.map(c => c.content.filter((b: any) => b.type === 'tool_use').map((b: any) => b.name).join('+') || c.stop)
check('[mock:subtask]: studio_subtask {brief [mock:studio-module]}, then the reply, end_turn', JSON.stringify(subNamed) === JSON.stringify(['studio_subtask', 'reply', 'end_turn']) && sub[0].content.find((b: any) => b.name === 'studio_subtask').input.brief === '[mock:studio-module]', subNamed)
const inner = assemble(await sse(await post(studioReq([{ role: 'user', content: [{ type: 'text', text: 'Sub-task of a job [mock:subtask]: [mock:studio-module]' }] }]))))
check("…the sub-task's own thread plays studio-module (its brief's marker first)", inner.content.some((b: any) => b.name === 'guide'), inner.content)
const slowAt2 = Date.now()
await sse(await post(studioReq([{ role: 'user', content: [{ type: 'text', text: 'Brief [mock:studio-slow]' }] }])))
check('[mock:studio-slow]: a studio call waits LANI_TUTOR_MOCK_STUDIO_MS (300 here)', Date.now() - slowAt2 >= 300, Date.now() - slowAt2)
const nothing = await playStudio('Studio job: dialog_variants_heard (conversation_id: main)', () => ({ content: 'ok' }))
check('no marker: the reply "nothing to write", then end_turn', nothing.length === 2 && nothing[0].content[0]?.input?.text.includes('nothing to write') && nothing[1].stop === 'end_turn', nothing.map(c => c.content))
const notStudio = assemble(await sse(await post({ ...studioReq([{ role: 'user', content: [{ type: 'text', text: 'kind="chat" [mock:studio-module]' }] }]), system: [{ type: 'text', text: 'The tutor.' }] })))
check("a request without the studio's prompt is the tutor's, markers or not", notStudio.content.some((b: any) => b.name === 'guide') && !notStudio.content.some((b: any) => b.name === 'publish_module'), notStudio.content)

// --- Message Batches -------------------------------------------------------------------------------------------------
const batchPost = (body: unknown, headers: Record<string, string> = H) => fetch(`${base}/v1/messages/batches`, { method: 'POST', headers, body: JSON.stringify(body) })
const nonStream = (r: any) => {
  const { stream: _s, ...rest } = r
  return rest
}
check('batches: no key 401', (await batchPost({ requests: [] }, { 'anthropic-version': '2023-06-01' })).status === 401)
check('batches: a bad custom_id, a duplicate, a streaming params: 400',
  (await batchPost({ requests: [{ custom_id: 'a b', params: {} }] })).status === 400 &&
    (await batchPost({ requests: [{ custom_id: 'a', params: nonStream(studioReq([])) }, { custom_id: 'a', params: nonStream(studioReq([])) }] })).status === 400 &&
    (await batchPost({ requests: [{ custom_id: 'a', params: studioReq([{ role: 'user', content: 'x' }]) }] })).status === 400)
const batchMade: any = await (await batchPost({
  requests: [
    { custom_id: 'j_batch01-1', params: nonStream(studioReq([{ role: 'user', content: [{ type: 'text', text: 'Night job (conversation_id: main) [mock:studio-module]' }] }])) },
    { custom_id: 'j_batch01-2', params: { model: 'claude-opus-5-5', messages: [{ role: 'user', content: 'x' }] } },
  ],
})).json()
check('POST /v1/messages/batches: a message_batch in progress, its counts, no results_url yet', /^msgbatch_mock_/.test(batchMade.id) && batchMade.type === 'message_batch' && batchMade.processing_status === 'in_progress' && batchMade.request_counts.processing === 2 && batchMade.results_url === null && batchMade.ended_at === null && typeof batchMade.expires_at === 'string', batchMade)
const batchEarly = await fetch(`${base}/v1/messages/batches/${batchMade.id}/results`, { headers: H })
check('…its results before it batchEnded: 400', batchEarly.status === 400, batchEarly.status)
await Bun.sleep(260)
const batchEnded: any = await (await fetch(`${base}/v1/messages/batches/${batchMade.id}`, { headers: H })).json()
check('GET /v1/messages/batches/:id after LANI_TUTOR_MOCK_BATCH_MS: batchEnded, 1 succeeded and 1 errored, results_url', batchEnded.processing_status === 'ended' && batchEnded.request_counts.succeeded === 1 && batchEnded.request_counts.errored === 1 && batchEnded.results_url === `${base}/v1/messages/batches/${batchMade.id}/results` && !!batchEnded.ended_at, batchEnded)
const batchLines = (await (await fetch(batchEnded.results_url, { headers: H })).text()).trim().split('\n').map(l => JSON.parse(l))
const batchOk = batchLines.find((l: any) => l.custom_id === 'j_batch01-1')
const batchErr = batchLines.find((l: any) => l.custom_id === 'j_batch01-2')
check("…its results (JSONL): the succeeded one a full message (the studio's first call: guide, learner_state; stop tool_use; usage), the errored one an error",
  batchLines.length === 2 && batchOk.result.type === 'succeeded' && batchOk.result.message.type === 'message' && batchOk.result.message.stop_reason === 'tool_use' && batchOk.result.message.content.some((b: any) => b.name === 'guide') && batchOk.result.message.usage.output_tokens > 0 &&
    batchErr.result.type === 'errored' && batchErr.result.error.type === 'error' && batchErr.result.error.error.type === 'invalid_request_error' && /max_tokens/.test(batchErr.result.error.error.message), batchLines)
const batchToCancel: any = await (await batchPost({ requests: [{ custom_id: 'c1', params: nonStream(studioReq([{ role: 'user', content: [{ type: 'text', text: 'x' }] }])) }] })).json()
const batchCanceling: any = await (await fetch(`${base}/v1/messages/batches/${batchToCancel.id}/cancel`, { method: 'POST', headers: H })).json()
await Bun.sleep(400)
const batchCanceled: any = await (await fetch(`${base}/v1/messages/batches/${batchToCancel.id}`, { headers: H })).json()
const batchCLines = (await (await fetch(batchCanceled.results_url, { headers: H })).text()).trim().split('\n').map(l => JSON.parse(l))
check('POST …/cancel: batchCanceling, then batchEnded with the requests canceled', batchCanceling.processing_status === 'canceling' && !!batchCanceling.cancel_initiated_at && batchCanceled.processing_status === 'ended' && batchCanceled.request_counts.canceled === 1 && batchCLines[0].result.type === 'canceled', { batchCanceling, batchCanceled, batchCLines })
check('an unknown batch: 404', (await fetch(`${base}/v1/messages/batches/msgbatch_nope`, { headers: H })).status === 404)
const batchLog = (await (await fetch(`${base}/mock/requests`)).json()).filter((r: any) => String(r.call ?? '').startsWith('batch_'))
check("the batches in /mock/requests (create with its requests' calls, get, results, cancel)", ['batch_create', 'batch_get', 'batch_results', 'batch_cancel'].every(c => batchLog.some((r: any) => r.call === c)) && batchLog.find((r: any) => r.call === 'batch_create')?.requests?.[0]?.studio === true, batchLog.map((r: any) => r.call))

// --- the log --------------------------------------------------------------------------------------------------------
const log = await (await fetch(`${base}/mock/requests`)).json()
const logged = JSON.stringify(log)
check('GET /mock/requests: at most the last 50, each with method, path, has_key, and never the key', Array.isArray(log) && log.length <= 50 && log.length > 10 && log.every((r: any) => r.method && r.path && typeof r.has_key === 'boolean') && !logged.includes(KEY) && log.some((r: any) => r.has_key === false), log.length)
check('the key is not in the mock\'s own log', !out.includes(KEY) && out.includes('key: present'))
await fetch(`${base}/mock/reset`, { method: 'POST' })
const again = assemble(await sse(await post(request([event]))))
check('POST /mock/reset: the log is empty and the prefix is written again', (await (await fetch(`${base}/mock/requests`)).json()).length === 1 && again.start.usage.cache_creation.ephemeral_1h_input_tokens === prefixTokens)

mock.kill()
console.log(failures ? `\n${failures} check(s) failed` : '\nall checks passed')
process.exit(failures ? 1 : 0)
