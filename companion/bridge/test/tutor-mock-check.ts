#!/usr/bin/env bun
// A quick self-check of the Messages API mock (test/tutor-mock.ts): starts it on a free port as QA runs it, then plays
// the app's client with plain fetch: the models, a turn's first call (streamed), its tool results, the call after the
// reply, a 429 and its retry, a refusal, the credit error, a stream error, a non-streamed call, the effort (top-level, per
// message with its beta, the thinking it weighs, Haiku 5.5 at low and [mock:no-reply] without a reply), the caching usage and
// the request log (the key never in it).
//
//   bun companion/bridge/test/tutor-mock-check.ts
import { resolve } from 'node:path'

let failures = 0
const check = (name: string, cond: unknown, detail?: unknown) => {
  console.log(`${cond ? '✅' : '❌'} ${name}`, cond ? '' : (detail === undefined ? '' : typeof detail === 'string' ? detail : JSON.stringify(detail, null, 1)))
  if (!cond) failures++
}

const KEY = 'sk-mock-check-1234567890'
const mock = Bun.spawn(['bun', resolve(import.meta.dir, 'tutor-mock.ts')], {
  env: { ...process.env, LANI_TUTOR_MOCK_PORT: '0', LANI_TUTOR_MOCK_DELAY_MS: '1' },
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
