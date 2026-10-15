// Chat with the tutor: app messages into the session, the reply tool back out, and the permission
// relay (tool approvals answered from the app). A session_end whose results were answered with the cheat
// sheet open (looked_up) gets a line naming the topics looked up.
import { z } from 'zod'
import { lookedUpNote, resultsLookedUp } from '../answers'
import { fail, ok } from '../channel'
import type { FeatureFactory } from '../feature'
import { json, readJson } from '../http'
import { scenarioBrief } from '../scenarios'
import { learnerLevel, villagerTalk } from '../villagers'
import { talkPlace } from './villagers'

const instructions = `
You are Jan's Lani language tutor. This channel connects you to the Lani Android app.

Inbound: <channel source="lani" kind="..." conversation_id="..." msg_id="...">. The sender is
Jan, authenticated by the app token; only the family events (see Family), a guest's gift (town_gift,
see Visits) and the questions between towns (town_*, see Questions between towns) carry text from someone
else. kind is one of:
- chat: free conversation or a request ("make me a drill about clitics"). Reply in the tutor voice.
  If the JSON block has about_exercise, Jan asked about that exercise: "Ask" on its feedback, or 💬 in
  the top bar of the exercise on screen (Jan can take it off before sending, so a plain chat may come
  from an exercise too). It holds the prompt, the learner's answer, the expected answer, the verdict,
  the grading hint (typo / ending / diacritics) and its source, the item it came from (kind review:
  card_id and variant; module: module_id, module_version, exercise_index; pack: pack_id, word_id,
  card_id; challenge: its card_id, module_id or pack word; visit_request). Answer about that exercise
  specifically (e.g. "Why?" = why the answer was wrong). answered: false (💬 before answering): no
  answer or verdict yet; help with the question (a word, the rule, a hint) without giving the expected
  answer away unless Jan asks for it. type word: a word met before its practice (word, meaning,
  example; source pack, no variant); type reading: a text to read before its questions (text,
  questions; source challenge, no exercise_index). When Jan says a question, card, word, dialog choice
  or translation is wrong or confusing, or a right answer was marked wrong, fix the item with the
  lani-fix skill.
  A chat with data.about_roleplay continues that role-play's debrief (it holds scenario_id, title, the
  role-play's conversation_id and your debrief): answer out of character about that scene, on the
  chat's own conversation_id.
  A chat with data.reply_to answers an earlier message of the chat that Jan quoted, maybe days old:
  its text, from (learner or tutor), at, and what it was about (about, and attached: the data it was
  sent with, e.g. about_exercise). Pick up from that message.
- answer: an answer to an exercise the app could not grade by itself (grade="claude"). The JSON block
  carries module_id, exercise_index, prompt, rubric and the learner answer. Grade it with the
  lani-feedback-formatter template and reply. Put {"score": 0-10, "severity": "..."} in data.
- session_end: the app finished a practice run. The JSON block carries the results. Persist them with
  the lani-db-updater skill (update-db.py), then reply with a short summary. A block with language
  (a code, e.g. "it": practice in another language than the village's, on a visit) goes to that
  language's data: put "language" in the report, and take next_session_id from
  read-db.py --language <code>.
- review_done: a flashcard review in the app, ALREADY persisted by the bridge. Don't persist it again.
  Reply only if you have something useful to say (e.g. a tip for items Jan struggled with, or a new
  module for them via lani-studio). It names the answers typed wrong; recent_answers has every answer
  of the last days (reviews, forms, dialog words), for when Jan reports a card later without Ask. Its
  counting cards' wrong steps are ALREADY mistakes in the mistakes database (see Counting cards).
- roleplay, roleplay_end: a role-play turn and its end (see Role-play); villager_arrived: someone
  moved into the village (see Villagers and Arrivals).

looked_up on an answer (a session_end's results, a review's; the note's "📋 Looked up on the cheat
sheet: …" line counts them): Jan opened the cheat sheet (📋 Plonk listek) during that exercise (cards:
the cards opened; pages: their grammar pages; both empty: no card opened; a worksheet item's looked_up:
true, answered after it opened). A right answer then stays right and earns in full, but counts as right
with effort: quality at most 4 (the app caps a review's itself; when you persist a session_end, give
such a right answer at most 4; with a 💡 hint too, the hint's rule wins). A signal, never a fault: a
topic looked up often (here, get_village's or get_course's looked_up, recent_answers with looked_up)
is work potential: suggest its school unit (get_course), homework on it (publish_worksheet), a module
(lani-studio), or explain it in chat. Never scold Jan for looking something up.

Always answer with the reply tool and pass the conversation_id from the tag; the app does not see
terminal output. Keep replies phone-sized. Markdown is rendered (no images), links too: [text](https://…)
or a bare https:// URL. A link to fran.si opens at once; any other asks Jan first, showing its domain;
other schemes stay text.
`.trim()

const inbound = z.object({
  kind: z.enum(['chat', 'answer', 'session_end', 'roleplay', 'roleplay_end', 'villager_arrived']).default('chat'),
  // fam_<id> conversations are the family challenges': the bridge assigns them, the app can't.
  conversation_id: z
    .string()
    .regex(/^[\w-]{1,64}$/)
    .refine(id => !id.startsWith('fam_'), 'fam_ conversations belong to family challenges')
    .default('main'),
  text: z.string().max(8000).default(''),
  data: z.record(z.string(), z.unknown()).optional(),
})

const replyIn = z.object({
  conversation_id: z.string().regex(/^[\w-]{1,64}$/, 'the conversation_id from the <channel> tag'),
  text: z.string().max(32_000),
  data: z.record(z.string(), z.unknown()).nullish(),
})

const verdict = z.object({ request_id: z.string().regex(/^[a-km-z]{5}$/), behavior: z.enum(['allow', 'deny']) })

export const chat: FeatureFactory = ctx => {
  const { cfg, channel, events, family, outbox, scenarios, villagers, game, profile, visitTalks, learner, log } = ctx
  let msgSeq = 0

  /**
   * What an arrival is written around (villager_arrived): the learner's level, their weakest patterns now (mistakes-db,
   * with an example each), and for a baby the parents (their ids and names: they are the ones who speak).
   */
  function arrivalBrief(data: Record<string, unknown>) {
    let weak: { id: string; example?: { incorrect?: string; correct?: string } }[] = []
    try {
      const patterns = (learner.state().databases as any).mistakes_db?.error_patterns ?? {}
      weak = Object.entries(patterns)
        .filter(([, p]: any) => (p?.consecutive_incorrect ?? 0) > 0)
        .sort(([, a]: any, [, b]: any) => (b.frequency ?? 0) - (a.frequency ?? 0))
        .slice(0, 3)
        .map(([id, p]: any) => {
          const e = Array.isArray(p?.examples) ? p.examples[p.examples.length - 1] : undefined
          return { id, ...(e ? { example: { incorrect: e.incorrect, correct: e.correct } } : {}) }
        })
    } catch (e) {
      log(`villager_arrived: no mistakes to build on (${(e as Error).message})`)
    }
    const residents: any[] = (game.read()?.state as any)?.residents ?? []
    const parents = (Array.isArray(data.parents) ? data.parents : [])
      .flatMap(p => residents.filter(r => r?.id === p).map(r => ({ id: r.id, name: r.name, voice: r.voice })))
    return { level: learnerLevel(cfg.dataDir), weak_patterns: weak, ...(parents.length ? { parents } : {}), tool: 'publish_arrival' }
  }

  async function handleMessage(req: Request): Promise<Response> {
    const r = inbound.safeParse(await readJson(req))
    if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
    const m = r.data
    const msg_id = `m${Date.now().toString(36)}${(msgSeq++).toString(36)}`
    const scenarioId = m.kind.startsWith('roleplay') && typeof m.data?.scenario_id === 'string' ? m.data.scenario_id : undefined
    if (scenarioId?.startsWith('villager:')) {
      // A talk with a villager: the role-play is built for the friendship, and the persona rides along.
      const t = villagerTalk(villagers, game.read(), scenarioId.slice('villager:'.length), learnerLevel(cfg.dataDir), talkPlace(ctx))
      if (t) m.data = { ...m.data, scenario: scenarioBrief(t.scenario), villager: t.brief }
    } else if (scenarioId && /^(town|market):/.test(scenarioId)) {
      // A talk on a visit (features/towns.ts, POST /towns/:id/talk): a host's villager or its market, in the host's
      // language; the persona and the visit ride along.
      const t = visitTalks.get(scenarioId)
      if (t) m.data = { ...m.data, scenario: scenarioBrief(t.scenario), ...t.data }
    } else if (scenarioId) {
      const scenario = scenarios.get(scenarioId)
      if (scenario) m.data = { ...m.data, scenario: scenarioBrief(scenario) }
    } else if (m.kind === 'villager_arrived' && typeof m.data?.id === 'string') {
      // someone joined the village: what the tutor needs to write their introduction (publish_arrival, features/arrivals.ts)
      m.data = { ...m.data, arrival: arrivalBrief(m.data) }
    }
    // a run's exercises answered with the cheat sheet open (answers.ts lookedUpNote): the topics looked up, in a line
    const looked = m.kind === 'session_end' ? lookedUpNote(resultsLookedUp(m.data?.results), 'exercise') : ''
    const said = looked ? (m.text ? `${m.text}${looked}` : looked.trim()) : m.text
    const content = m.data ? `${said}\n\n\`\`\`json\n${JSON.stringify(m.data, null, 2)}\n\`\`\`` : said
    // queued: the bridge service has no tutor session attached now; the next one gets it (docs/plans/04-bridge-service.md)
    const queued = (await channel.notify(content, { kind: m.kind, conversation_id: m.conversation_id, msg_id })) === 'queued'
    return json({ msg_id, ...(queued ? { queued: true } : {}) })
  }

  return {
    instructions,
    routes: [
      { method: 'POST', path: '/message', handle: ({ req, url }) => outbox.once(req, url.pathname, handleMessage) },
      {
        method: 'POST',
        path: '/permission',
        handle: async ({ req }) => {
          // A child's app gets no approval prompts, and can't answer one either.
          if (profile.child) return json({ error: 'tool approvals are answered in the terminal for this learner' }, 403)
          const r = verdict.safeParse(await readJson(req))
          if (!r.success) return json({ error: z.prettifyError(r.error) }, 400)
          await channel.permission(r.data)
          return json({ ok: true })
        },
      },
    ],
    tools: [
      {
        name: 'reply',
        description: 'Send a message to the Lani app. Markdown text plus optional structured data.',
        inputSchema: {
          type: 'object',
          properties: {
            conversation_id: { type: 'string', description: 'conversation_id from the inbound <channel> tag' },
            text: { type: 'string', description: 'Markdown message shown to the learner' },
            data: { type: 'object', description: 'Optional structured payload, e.g. {"score": 8}' },
          },
          required: ['conversation_id', 'text'],
        },
        handle: args => {
          const r = replyIn.safeParse(args)
          if (!r.success) return fail(`not sent:\n${z.prettifyError(r.error)}`)
          const { conversation_id, text, data } = r.data
          family.onReply(conversation_id, text, data ?? undefined) // fam_<id>: a partner challenge's feedback
          events.emit({ type: 'reply', conversation_id, text, data })
          return ok(events.connected ? 'delivered' : 'queued (app not connected; it will receive this on reconnect)')
        },
      },
    ],
  }
}
