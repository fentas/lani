// Smoke checks: the app's own tutor as a client of the channel (features/tutor.ts, docs/plans/05-built/tutor-live.md).
// The session's tools are exactly Claude Code's (plus the app session's own, which Claude Code never lists); attached,
// a message's notification waits in the app's inbox and never reaches the Claude Code session (this smoke's MCP
// client); a long poll wakes for it; tool calls go through the channel's handlers (reply reaches the app's events once
// per client id, get_village reads); record_session writes the learner's databases through update-db.py, card through
// card.py, the notebook into the data, report_problem the wishlist; detached, the session gets the events again,
// with what the inbox still held.
import { existsSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { auth, base, channelEvents, check, client, dataDir, fam } from './harness'

const post = (path: string, body: unknown, headers: Record<string, string> = auth) => fetch(`${base}${path}`, { method: 'POST', headers, body: JSON.stringify(body) })
const get = (path: string, headers: Record<string, string> = auth) => fetch(`${base}${path}`, { headers })
const call = async (name: string, args: Record<string, unknown>, client_id?: string) => {
  const r = await post(`/tutor/tools/${name}`, { args, ...(client_id ? { client_id } : {}) })
  const body: any = await r.json()
  return { status: r.status, body, text: String(body?.content?.[0]?.text ?? ''), error: body?.isError === true }
}
const notifications = () => channelEvents.filter(n => n.method === 'notifications/claude/channel')

export default async function tutorLive() {
  // --- the session: the channel's tools exactly as Claude Code lists them, and the app session's own -----------------
  const session: any = await (await get('/tutor/session')).json()
  const listedForClaude = (await client.listTools()).tools
  check('GET /tutor/session: protocol 1, the channel instructions', session.protocol === 1 && typeof session.instructions === 'string' && session.instructions.length > 100, session.protocol)
  check(
    `GET /tutor/session: its tools are Claude Code's list exactly (${listedForClaude.length}: names, descriptions, inputSchemas)`,
    JSON.stringify(session.tools) === JSON.stringify(listedForClaude.map(t => ({ name: t.name, description: t.description, inputSchema: t.inputSchema }))),
    { session: session.tools?.length, claude: listedForClaude.length },
  )
  // (the file tools, str_replace_based_edit_tool and search, are the studio's: test/smoke/tutor-studio.ts)
  const own = ['learner_state', 'record_session', 'card', 'notebook_note', 'notebook_write', 'report_problem', 'str_replace_based_edit_tool', 'search']
  const appNames = (session.app_tools ?? []).map((t: any) => t.name)
  check(
    "the app session's own tools (the scripts Claude Code runs through its shell, the notebook, report_problem), with the app's specs, none of them in Claude Code's list",
    JSON.stringify(appNames) === JSON.stringify(own) && own.every(n => !listedForClaude.some(t => t.name === n)) &&
      session.app_tools.find((t: any) => t.name === 'card')?.inputSchema?.properties?.action?.enum?.length === 8,
    appNames,
  )
  check('not attached at first', session.attached === null, session.attached)
  check('/tutor/* is the app\'s: no token 401, the family token 403', (await get('/tutor/session', {})).status === 401 && (await get('/tutor/session', fam)).status === 403)

  // --- attached: the notifications go to the app's inbox, not to the session -------------------------------------
  const attach: any = await (await post('/tutor/attach', { who: 'app', device: 'smoke phone' })).json()
  check('POST /tutor/attach: the app has the channel', attach.ok && attach.attached?.who === 'app' && attach.attached?.device === 'smoke phone', attach)
  const heard = notifications().length
  const msg: any = await (await post('/message', { kind: 'chat', conversation_id: 'main', text: 'Živjo, učitelj v aplikaciji!' })).json()
  await Bun.sleep(200)
  const inbox: any = await (await get('/tutor/inbox?after=0')).json()
  const item = inbox.items?.find((i: any) => i.meta?.msg_id === msg.msg_id)
  check("a chat's notification is in the app's inbox (id, content, meta, at), not marked queued", !!item && Number.isSafeInteger(item.id) && item.content.includes('Živjo, učitelj v aplikaciji!') && item.meta.kind === 'chat' && item.meta.conversation_id === 'main' && typeof item.at === 'number' && !msg.queued, { msg, inbox })
  check("… and it never reached the Claude Code session", notifications().length === heard, notifications().slice(heard))
  check("… without Claude Code's reply hint (the app's tutor answers with its own reply tool)", !String(item?.content).includes('mcp__lani__reply'), item?.content)

  // a long poll wakes for the next one
  const t0 = Date.now()
  const polled = get(`/tutor/inbox?after=${item?.id ?? 0}&wait=10`).then(r => r.json())
  await Bun.sleep(300)
  await post('/message', { kind: 'answer', conversation_id: 'ex_smoke1', text: 'Jaz sem Jan.', data: { module_id: 'x', rubric: 'a greeting' } })
  const woke: any = await polled
  check('GET /tutor/inbox?wait= waits, and answers as soon as a notification comes', woke.items?.length === 1 && woke.items[0].meta.kind === 'answer' && Date.now() - t0 < 5_000, { woke, ms: Date.now() - t0 })
  const quiet = Date.now()
  const none: any = await (await get(`/tutor/inbox?after=${woke.items?.[0]?.id ?? 0}&wait=1`)).json()
  check('… and with nothing new it answers empty after its wait', Array.isArray(none.items) && none.items.length === 0 && Date.now() - quiet >= 900, none)
  check("the inbox is kept in the data (app/tutor-inbox.json), with who is attached", existsSync(join(dataDir, 'app/tutor-inbox.json')) && JSON.parse(readFileSync(join(dataDir, 'app/tutor-inbox.json'), 'utf8')).attached?.who === 'app')

  // --- tools through the channel's handlers -----------------------------------------------------------------------
  const since = (await (await get('/events/backlog?since=0')).json()).at(-1)?.id ?? 0
  const replied = await call('reply', { conversation_id: 'main', text: 'Živjo! (the app tutor)' }, 'toolu_smoke_reply1')
  const again = await call('reply', { conversation_id: 'main', text: 'Živjo! (the app tutor)' }, 'toolu_smoke_reply1')
  await Bun.sleep(100)
  const replies = ((await (await get(`/events/backlog?since=${since}`)).json()) as any[]).filter(e => e.event?.type === 'reply' && e.event.text === 'Živjo! (the app tutor)')
  check("POST /tutor/tools/reply: the reply reaches the app's events as Claude Code's do", replied.status === 200 && !replied.error && /delivered|queued/.test(replied.text) && replies.length >= 1, { replied, replies })
  check('… once per client_id (the tool call\'s id): a repeat gets the first answer', again.status === 200 && again.text === replied.text && replies.length === 1, replies.length)
  const village = await call('get_village', {})
  check('a read tool (get_village) answers through the same route', village.status === 200 && !village.error && village.text.length > 0, village.text.slice(0, 120))
  const bad = await call('reply', { conversation_id: 'not valid!', text: 'x' })
  check("a tool's own validation answers as it does Claude Code (isError, nothing sent)", bad.status === 200 && bad.error && bad.text.startsWith('not sent'), bad)
  check('an unknown tool is a 404 naming the tools', (await post('/tutor/tools/no_such_tool', { args: {} })).status === 404)

  // learner_state and record_session: read-db.py and update-db.py
  const state = await call('learner_state', {})
  const summary = JSON.parse(state.text || '{}')
  const sessionId = summary.computed?.next_session_id
  check('learner_state: read-db.py summed up (computed with next_session_id, top_mistakes, recent_sessions)', !state.error && /^session-\d+$/.test(sessionId ?? '') && 'top_mistakes' in summary && Array.isArray(summary.recent_sessions), state.text.slice(0, 300))
  const day = new Date().toISOString().slice(0, 10)
  const recorded = await call('record_session', {
    session_id: sessionId, date: day, duration_minutes: 6, command_used: 'app:session_end', skills_practiced: ['vocabulary'],
    skill_scores: { vocabulary: { exercises: 4, correct: 3 } },
    errors: [{ pattern_id: 'smoke_app_tutor', category: 'grammar', your_answer: 'Jaz je Jan.', correct_answer: 'Jaz sem Jan.', severity: 'moderate' }],
    new_vocabulary: [{ item_id: 'vocab_smoke_hisa', content: 'hiša', answer: 'house' }],
    session_notes: 'The app tutor recorded this (smoke).',
  })
  const log = JSON.parse(readFileSync(join(dataDir, 'session-log.json'), 'utf8'))
  const mistakes = JSON.parse(readFileSync(join(dataDir, 'mistakes-db.json'), 'utf8'))
  const sr = JSON.parse(readFileSync(join(dataDir, 'spaced-repetition.json'), 'utf8'))
  check(
    "record_session writes the session through update-db.py: the session log, the mistake, the new card",
    !recorded.error && (log.sessions ?? []).some((s: any) => s.session_id === sessionId) && !!mistakes.error_patterns?.smoke_app_tutor && !!sr.items?.vocab_smoke_hisa,
    recorded.text.slice(0, 400),
  )
  const refused = await call('record_session', { date: day })
  check('record_session without a session_id writes nothing (isError)', refused.error, refused.text)

  // card: card.py's commands
  const found = await call('card', { action: 'find', text: 'hiša' })
  check('card find: card.py finds the card', !found.error && found.text.includes('vocab_smoke_hisa'), found.text.slice(0, 300))
  const shown = await call('card', { action: 'show', card_id: 'vocab_smoke_hisa' })
  check('card show: how the app asks it', !shown.error && shown.text.includes('vocab_smoke_hisa'), shown.text.slice(0, 300))
  const dry = await call('card', { action: 'edit', card_id: 'vocab_smoke_hisa', back: 'house, home', reason: 'smoke: a second meaning', dry_run: true })
  const untouched = JSON.parse(readFileSync(join(dataDir, 'spaced-repetition.json'), 'utf8')).items.vocab_smoke_hisa
  check('card edit dry_run: shown, nothing written', !dry.error && untouched.answer === 'house', { dry: dry.text.slice(0, 300), untouched })
  const edited = await call('card', { action: 'edit', card_id: 'vocab_smoke_hisa', back: 'house, home', reason: 'smoke: a second meaning' })
  const now = JSON.parse(readFileSync(join(dataDir, 'spaced-repetition.json'), 'utf8')).items.vocab_smoke_hisa
  check('card edit: card.py writes the new back, the card kept', !edited.error && now.answer === 'house, home', { edited: edited.text.slice(0, 300), now })
  const wrong = await call('card', { action: 'regrade', card_id: 'vocab_smoke_hisa' })
  check('card regrade without its quality: refused before card.py runs', wrong.error && wrong.text.includes('quality'), wrong.text)

  // the notebook, in the learner's data
  await call('notebook_note', { text: 'Jan likes short drills.' })
  const noted: any = await (await get('/tutor/notebook')).json()
  check('notebook_note: a line in app/tutor/notebook.md, its meta beside it', noted.text.includes('- Jan likes short drills.') && noted.why === 'notebook_note' && existsSync(join(dataDir, 'app/tutor/notebook.json')), noted)
  await call('notebook_write', { text: '# Notebook\n- Jan: A1, practises daily.' })
  const written: any = await (await get('/tutor/notebook')).json()
  const versions = JSON.parse(readFileSync(join(dataDir, 'app/tutor/notebook-versions.json'), 'utf8'))
  check('notebook_write replaces it; the old text is kept', written.text.startsWith('# Notebook') && !written.text.includes('short drills') && versions.some((v: any) => v.text.includes('short drills')), { written, versions })
  const byHand: any = await (await fetch(`${base}/tutor/notebook`, { method: 'PUT', headers: auth, body: JSON.stringify({ text: '# Notebook\n- Jan: A1, practises daily.\n- Corrected by Jan.' }) })).json()
  check("PUT /tutor/notebook: the learner's correction, by hand", byHand.text.includes('Corrected by Jan.') && byHand.why === 'by hand', byHand)

  // report_problem: the wishlist, until the discussions have a GitHub token
  const reported = await call('report_problem', { kind: 'fix', title: 'Fix: pack smoke, word hiša', what_is_wrong: 'the plural is wrong', suggested_fix: 'hiše', item: { kind: 'pack', pack_id: 'smoke', word_id: 'hisa' } })
  const wishlist = readFileSync(join(dataDir, 'app/wishlist.md'), 'utf8')
  check("report_problem: an entry in the learner's wishlist (app/wishlist.md), saying the discussions need a GitHub sign-in", !reported.error && reported.text.includes('GitHub') && wishlist.includes('· Fix: pack smoke, word hiša') && wishlist.includes('**Fix:** hiše'), wishlist.slice(-400))

  // --- the lease (lease.ts): while another side holds the learner's data, the writes fail, the reads and the channel go on
  const leaseFile = join(dataDir, 'app/lease.json')
  const leaseBefore = existsSync(leaseFile) ? readFileSync(leaseFile, 'utf8') : null
  const stamp = (d: number) => new Date(Date.now() + d).toISOString().replace(/\.\d+Z$/, 'Z')
  writeFileSync(leaseFile, JSON.stringify({ schema: 'lani.lease/v1', holder: { side: 'phone', device: 'p_0123abcd', name: 'Pixel 8', mode: 'hosted' }, offer: null, since: stamp(0), renewed: stamp(0), expires: stamp(14 * 86_400_000) }))
  try {
    const blocked = await call('record_session', { session_id: 'session-999', date: day })
    const noted = await call('notebook_note', { text: 'not while the phone writes' })
    const editRefused = await call('card', { action: 'edit', card_id: 'vocab_smoke_hisa', back: 'x', reason: 'lease' })
    check("lease: the app session's writes fail while another side holds the data (record_session, the notebook, card's edit), saying why", [blocked, noted, editRefused].every(r => r.status === 200 && r.error && r.text.startsWith('Nothing written.') && r.text.includes('Pixel 8')), [blocked.text, noted.text, editRefused.text])
    const reads = [await call('learner_state', {}), await call('card', { action: 'find', text: 'hiša' }), await call('get_village', {})]
    check("lease: …its reads go on (learner_state, card find, get_village)", reads.every(r => !r.error), reads.map(r => r.text.slice(0, 80)))
    const published = await call('publish_gloss', { lemma: 'hiša', meaning: 'house' })
    check("lease: …and the channel's writing tools fail as Claude Code's do (guardTools)", published.error && published.text.startsWith('Nothing written.'), published.text)
    const put = await fetch(`${base}/tutor/notebook`, { method: 'PUT', headers: auth, body: JSON.stringify({ text: 'by hand' }) })
    const acking = await post('/tutor/inbox/ack', { ids: [] })
    check('lease: PUT /tutor/notebook is refused (423), the inbox and attach go on', put.status === 423 && acking.status === 200 && (await get('/tutor/session')).status === 200, [put.status, acking.status])
  } finally {
    if (leaseBefore === null) rmSync(leaseFile, { force: true })
    else writeFileSync(leaseFile, leaseBefore)
  }

  // --- acknowledged, then detached: the session hears the channel again --------------------------------------------
  const last: any = await (await get('/tutor/inbox?after=0')).json()
  const acked: any = await (await post('/tutor/inbox/ack', { upto: last.items.at(-1).id })).json()
  check('POST /tutor/inbox/ack: the items up to an id are done', acked.ok && acked.acked === last.items.length && acked.inbox.size === 0, acked)
  await post('/message', { kind: 'chat', conversation_id: 'main', text: 'Ta ostane brez odgovora.' })
  await Bun.sleep(100)
  const before = notifications().length
  const detached: any = await (await post('/tutor/detach', {})).json()
  await Bun.sleep(200)
  const given = notifications().slice(before)
  check('POST /tutor/detach: what the inbox still held goes to the Claude Code session, with its reply hint', detached.ok && detached.returned === 1 && given.length === 1 && String(given[0].params.content).includes('Ta ostane brez odgovora.') && String(given[0].params.content).includes('mcp__lani__reply') && !!given[0].params.meta.queued_at, { detached, given })
  await post('/message', { kind: 'chat', conversation_id: 'main', text: 'Spet Claude Code.' })
  await Bun.sleep(150)
  const after: any = await (await get('/tutor/inbox?after=0')).json()
  check("detached: a new message reaches the session again, the inbox stays empty", notifications().some(n => String(n.params.content).includes('Spet Claude Code.')) && after.items.length === 0 && after.attached === null, after)
}
