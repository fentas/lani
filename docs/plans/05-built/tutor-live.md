# Plan 5, built: the live in-app tutor (phase A)

*Built 2026-10-19 (the overnight round, phase A: "the tutor in the app answers for real"). Branch `tutor-live`. Code:
the bridge's `companion/bridge/src/features/tutor.ts`, `src/tutor-inbox.ts` and `src/channel.ts`; the app's
`companion/android/app/src/main/java/si/lanisce/lani/tutor/live/` (`LiveTutor`, `LiveBridge`, `LiveTools`,
`LiveEvents`, `LiveStore`, `Fran`, `TutorWork`), the shared core `tutor/Core.kt`, the streaming `tutor/Partial.kt`, the
settings `ui/tutor/TutorSettings.kt`.*

## What

- **The tutor in the app answers for real.** It runs the agent loop on the learner's own key and gives real replies in
  the chat. The data changes for real too. Claude Code on the node stays the default and works as before: the app's
  tutor is a setting ("🧑‍🏫 Učitelj · Tutor" → "In the app").
- **It is a client of the bridge's channel.** It gets the same notifications and calls the same tools as Claude Code,
  through new `/tutor/*` routes. Now it runs over Tailscale against a node. Later the same routes run in-process on the
  phone's own bridge (the phone runtime's work).
- **While it is attached, Claude Code hears nothing new.** The bridge gives the channel's events to the app's inbox.
  When the learner switches back, the bridge gives the channel back to Claude Code, with every event the app didn't
  answer.
- **The parts the shadow and the live tutor share** moved out of `ShadowTutor` into `tutor/Core.kt`: the day's thread,
  its restart as text, the notebook's edits, a call's record, the retry rules. The shadow and its matrix call them. The
  shadow's tests pass unchanged.

## The bridge: the app's tutor session

`features/tutor.ts` is the last of `build.ts`'s `FEATURES`, on a node and on the phone. It reads the session's tools
and instructions lazily (`Ctx.session`), since they exist only once every feature is built. All routes have the app's
role (the bridge token or a paired phone's), as the rest of the app's API does.

**The same contract on both channels.** `ChannelPort` (src/tool.ts) has the app tutor's half: `divert` (the events go
to an inbox), `undivert` (they go back to the session, with what the inbox held), `call` (a tool as the session's call
runs) and `draining`. The node's `Channel` (channel.ts) and the phone's `PhoneChannel` (src/phone/channel.ts) both
implement `divert` and `undivert`. On the phone, "the session" is the host: while the app's tutor is detached, the
events go to `PhoneBridge`'s notifications, as before. Only the node's channel counts calls in flight and drains.

| Route | What |
|---|---|
| `GET /tutor/session` | `{protocol: 1, instructions, tools, app_tools, attached, inbox}`. `instructions` are the channel's rules, as a Claude Code session gets them. `tools` is exactly Claude Code's list (name, description, inputSchema). `app_tools` are the session's own tools (below). |
| `POST /tutor/attach` `{who: "app", device?}` | The app's tutor takes the channel. What the channel's queue held for the next Claude Code session moves to the inbox. Idempotent. |
| `POST /tutor/detach` | The channel goes back to Claude Code. Every item the inbox still holds goes to the session (or to its queue, while none is attached), with `queued_at`. The answer is `{returned: n}`. |
| `GET /tutor/inbox?after=<id>&wait=<s>` | The notifications after an id: `{items: [{id, content, meta, at}], attached, inbox}`. With `wait` (at most 25 s), the request waits until an item comes (a long poll). When the service stops, the poll ends at once. |
| `POST /tutor/inbox/ack` `{ids}` or `{upto}` | The app is done with these items. |
| `POST /tutor/tools/:name` `{args, client_id?}` | A tool call. It goes through `Channel.call`, the handler Claude Code's calls use: it is counted in flight, and it is refused while the service drains (a 503: the app tries once more). A repeat with the same `client_id` (the tool call's id) gets the first answer (the outbox's `once`). An unknown tool is a 404. |
| `GET /tutor/notebook`, `PUT /tutor/notebook {text}` | The notebook and its meta. `PUT` is the learner's own correction ("by hand"). |

**The inbox** (`src/tutor-inbox.ts`) is in `app/tutor-inbox.json`, beside `channel-queue.json`. It holds who is
attached, the items, and the next id (ids grow across restarts and are seeded from the clock, as the events' ids are).
The bridge keeps the file out of the data repository (`/app/tutor-inbox.json` in the `.gitignore`, `keepOut`). It
keeps at most 500 items and drops items older than three days (the queue's limits). An attach that the app hasn't
polled for three days lets go by itself and logs it (`LANI_TUTOR_LEASE_MS`), so a phone that is gone can't keep the
channel.

**What Claude Code sees while the app is attached:**
- The bridge logs "the app's own tutor is attached: … the Claude Code session gets none until it detaches".
- A session that starts meanwhile reads the same at the end of its instructions (`APP_TUTOR_NOTE`). A session that
  already runs keeps the instructions it started with: the shim never changes them.
- The service's status (`lani-bridge status`'s file) shows `app_tutor`.
- The session's tools keep working.

**The app session's own tools.** These are the tools Claude Code runs as Python through its shell. They are never in
Claude Code's list. Their schemas are the app's own specs (`companion/tutor/app-tools/*.json`), so the bridge and the
app agree.

| Tool | What it runs |
|---|---|
| `learner_state {language?}` | `read-db.py`, summed up as the shadow's summary (`learnerSummary`: the computed fields with `next_session_id`, the languages, the learner, the streak, the overall stats, the mastery, the top eight mistakes with their last example, the last three sessions, the deck's size) |
| `record_session {…report}` | `update-db.py` with the report on stdin (`Learner.update`) |
| `card {action, …}` | `card.py` with its eight commands. Options go as `--name=value`, then `--`, then the positionals, so an id that starts with `-` stays an id. A missing argument is refused before the script runs. |
| `notebook_note {text}`, `notebook_write {text}` | The notebook in the data: `app/tutor/notebook.md`, its meta `app/tutor/notebook.json` (when and why it was written, the history's times), the old texts in `app/tutor/notebook-versions.json` (the last 20). The notebook moves with the data repository between modes. |
| `report_problem {kind, title, what_is_wrong, …}` | An entry in `app/wishlist.md`, in the `lani-fix` skill's shape, as the node does today. **What's missing for GitHub discussions:** a GitHub sign-in (the plan's GitHub App, or the learner's own account) that the app doesn't have yet. The tool's answer says so, and the tutor tells the learner that the report is kept for the developer. |

**The lease** (lease.ts, git sync): while another side holds the learner's data, the writes are refused and the reads
go on, as for the rest of the app's API.
- The app session's writing tools (`record_session`, `notebook_note`, `notebook_write`, `report_problem`, `card`'s
  edit, merge, split, regrade and forgive) fail with "Nothing written." and the reason. `learner_state` and `card`'s
  find, show and overlaps go on.
- The session's own writing tools are guarded by `guardTools`, as Claude Code's are.
- `PUT /tutor/notebook` is refused with 423. Attaching, detaching, the inbox and its acks are the channel's state, not
  the learner's data: lease.ts's `NOT_DATA` lets them through, and `/tutor/tools/*` too (guarded tool by tool).

The scripts run through the bridge's own spawn path (`config.ts` `spawn`). On a node that is Python. On the phone,
phone-runtime dispatches the same `python3 .claude/hooks/<script>.py …` spawns to the db port.

**The notebook: tools, not only routes.** The model writes with `notebook_note` and `notebook_write`. The app reads with
`GET /tutor/notebook` (for the first message of a day's thread, and the settings' "What my tutor knows") and writes
the learner's corrections with `PUT`.

## The app: the live tutor

`tutor/live/LiveTutor.kt`. One instance lives as long as the app's process (`TutorRuntime`), so the view model and the
WorkManager check use the same.

- **Attaching.** "In the app" first reads `GET /tutor/session` and checks protocol 1. It keeps the node's tool names,
  so a tool the node lacks says so ("needs an update"). Then it calls `POST /tutor/attach` with the phone's name. A
  node whose bridge has no tutor session (a 404) says it needs an update, and the setting stays off. "Claude Code on my
  node" calls `POST /tutor/detach`. The turns not yet run are dropped from the app's queue, and the bridge gives their
  events to Claude Code. A detach that didn't reach the node is sent again at the next foreground.
- **Events.** While the app is up, a long poll of the inbox runs (25 s, then again). While it isn't,
  `TutorWork` runs every ~15 minutes, and once right away when the app leaves with a turn waiting. It reads the inbox
  once and runs what waits for an answer, for four minutes at most. A reply then reaches the learner as a
  notification, through the app's own background check. An event is acknowledged only when its turn is done (or its
  digest found nothing to say), so the bridge can give back what the app didn't answer.
- **Event kinds** (`LiveEvents`):
  - **At once:** a chat, an answer, the rhythm (morning, evening, the week), a level-up, `gloss_gaps`, the family's
    and the towns' events (their untrusted text stays marked as the bridge marks it), a role-play's turns, and any
    kind not listed here.
  - **Into the day's digest:** `review_done` and `session_end` (at once with "react at once"), `words_added`,
    `words_learned`, `reading_done`, `dialog_words`. The digest runs when the app opens after two hours, in the evening,
    or when the chat opens. A digest is worth a call when a session ended, a level was reached, an answer was wrong
    (read from the review's "n/m recalled"), a review was all right and long (20 or more), or a pack's words were
    learned (phase 0's quality pass).
  - **Left for Claude Code:** what writes content (phase C): `villager_arrived`, `dialog_variants_heard`,
    `stories_low`. These stay unacknowledged in the inbox, and the bridge gives them to Claude Code when the learner
    switches back. Meanwhile the app's template plays, as it does when no tutor answers.
- **Turns.** Plan 5's loop (`TutorLoop`), with the provider and the model per task: the presets, and the per-task
  model and effort under "advanced". With OpenAI the presets pick GPT-6.1 Sol for chat, grading and routine, never
  GPT-6 Luna ([The presets](#the-presets-and-the-matrixs-fixes-2026-10-26)); role-play and content keep their tier's
  family (Sol, Astra).
  - A turn on the chat's model joins the day's thread (from 04:00, append-only, the notebook in its first message).
  - Another model's task is a stateless call, its line added to the day's thread (`pending`).
  - A role-play gets its own short thread per conversation (`rp/<conversation>.json`). Its end drops that thread, and
    the debrief becomes a line of the day.
  - The day's end and a thread past its limit run a closing call. The model rewrites the notebook through the
    bridge's `notebook_write`.
  - A thread made under another prefix (an app update changed a tool or the prompt) restarts as text. A new key does
    the same.
- **Tools** (`LiveTools`). Phase A's 17, in the shadow's order and prefix (`PhaseA`):
  - The bridge's tools run for real through `POST /tutor/tools/:name`, with the tool call's id as `client_id`.
  - `reply` goes through the bridge as Claude Code's does: the bridge emits the `reply` event, and the chat shows it.
  - `guide(topic)` is the app's own bundled guide.
  - `dictionary_lookup` is the app's dictionary on the node (`GET /lookup`) and, for Slovene, fran.si's search page,
    which the app fetches itself (`FranLookup`: only `https://fran.si`, the page's text). Both are returned as
    `<untrusted_dictionary_content>`.
  - `report_problem` keeps the shadow's check (a fix about a word needs a `dictionary_lookup` earlier in the turn).
  - Every input is checked against its schema before it goes anywhere.
- **Streaming into the chat sheet.** The transports now hand out a tool call's input as it streams:
  - `TutorTransport.streamTools` and `TutorProvider.callTools`, from the Messages API's `input_json_delta` (the tools
    have `eager_input_streaming`) and the Responses API's `function_call_arguments.delta`;
  - `TutorLoop.run(onToolInput)` passes them on.

  `PartialJson.string` reads `reply`'s `text` as far as it came. The chat's typing bubble shows it
  (`ChatController.streaming`), and the final message from the bridge's `reply` event replaces it.
- **The text as the reply.** A turn that ends with text and no `reply` (Haiku 5.5 at `low` did that in the shadow)
  sends that text through the bridge's `reply`.
- **Budget and spend.** Each call is priced (`Prices`, the shadow's table; $0 on the ChatGPT plan, its API-equivalent
  kept). The daily cap ($1 by default) is checked before each call. When it's spent the tutor stops, the turn waits,
  and the chat says so once a day. The settings show today's spend against the cap and this month's.
- **Errors told in the chat** (`tutorLive.note.*`, once a day each, in the learner's two languages):

  | What | What the app does |
  |---|---|
  | The daily cap is spent | Turns wait; "raise it under Connection → Tutor" |
  | The key refused (401), the ChatGPT sign-in ended | The tutor pauses; "enter a new one" |
  | The credit out, a spend limit, the ChatGPT plan's limit | A chat or an answer says it can't answer now, routine work waits; a new day or "Check again" tries again |
  | Offline (no usable network, or Android holding the background app's network) | The queue waits for the network (no call, no try counted); a chat or an answer says "your message waits and gets its answer once you're back online", and the section says "no connection, n waiting" |
  | The API busy (a chat's second failed try) | "your message waits and gets its answer once you're back online" |
  | A refusal, a failure the loop doesn't retry | A short line, no retry |

- **The AI disclosure** (the Usage Policy's rule for consumer chatbots, plan 5 "Practicalities"):
  - in the Tutor section, before anything is set: "Učitelj v aplikaciji je umetna inteligenca … The tutor in the app is
    an AI (a language model). Your messages go straight to the provider on your own key; you pay it, by use.";
  - under the chat's title while the app's tutor answers: "🤖 Odgovarja umetna inteligenca (claude-sonnet-5-5) · An
    AI answers here (…)", with "Using ChatGPT plan" (OpenAI's UI guidelines) when the plan pays.
- **The shadow is hidden** while the live tutor is on: the developer section says so instead of its switch, and the
  shadow gets no events, real replies or role-play turns meanwhile.
- **The keys.** The live tutor uses the shadow's vaults (`TutorRuntime.vault`, `.openai`): one Claude key, one OpenAI
  key, one ChatGPT sign-in, so its token refreshes one at a time. AES-GCM with a Keystore key, decrypted for a call
  only, only in `x-api-key` to the API (or the OpenAI bearer to api.openai.com). Never in a log line (`Redact`), the
  settings or a tool's input. The bridge token goes only to the bridge.

## The presets and the matrix's fixes (2026-10-26)

The matrix ran 19 lanes beside the primary and the real tutor (Opus 5.5 in Claude Code) on Jan's events of 19 to 22
October: 32 events (20 chats, 4 session ends, 8 review digests), each lane scored 1–5 on Slovene, grading, teaching,
context, tools, habits and tone (Σ of 35; the real tutor 35). Costs are at Jan's use (about 5 chats, 1 session end and
2 review digests a day). The presets follow its recommendations; learners who chose a preset get them with the next
turn, and a task set under "advanced" keeps its own model and effort.

**With an Anthropic key** (`tutor/Presets.kt`, `Task` and `Preset`):

| Task | Economy (≈ $0.03 a day) | Balanced ⭐ (≈ $0.30 a day) | Best (≈ $0.85 a day) |
|---|---|---|---|
| chat (and the notebook) | Haiku 5.5 · xhigh (Σ25, 17 s) | Sonnet 5.5 · xhigh (Σ30, $0.23 a day, 15 s) | Opus 5.5 · medium (Σ32.5, 9 s) |
| grading (answer, session end) | Haiku 5.5 · high | Haiku 5.5 · high (stateless, $0.005 a day, every verdict right) | Opus 5.5 · medium |
| routine (the digest) | Haiku 5.5 · high | Haiku 5.5 · xhigh (stateless, ≈ $0.009 a day, fewer miscounts) | Opus 5.5 · medium |
| role-play | Haiku 5.5 · low | Sonnet 5.5 · low | Opus 5.5 · medium |
| content | Sonnet 5.5 · xhigh | Opus 5.5 · medium | Opus 5.5 · high |

- **Before** (2026-10-16): Economy on Haiku at `medium` (content on Sonnet at `medium`), Balanced's chat on Sonnet at
  `medium` and routine on Haiku at `high`. Sonnet at `low` and `medium` was the weak spot: about 6 wrong claims in 20
  chats (the locative "the 6th case", the dual *šla*, a *pika/točka* table backwards).
- **The faster alternative to Balanced's chat:** Opus 5.5 at `low` (Σ29, $0.29 a day, 5 s), under "advanced".
- **Effort's knees:** Haiku's at `xhigh` (Σ25 against 19–20 at `medium` and `high`; `max` took a median 63 s a chat),
  Sonnet's at `xhigh` (`low` to `high` Σ21–22), Opus good from `low`.
- **Economy's content** is the matrix's inference (no content event ran): not Sonnet at `medium`, whose Slovene erred
  most in chat. Sonnet at `xhigh` is the cheaper of its two picks (Opus at `low` the other).

**With OpenAI** (an API key or the ChatGPT plan, $0 on the plan): GPT-6.1 Sol for chat, grading and routine, at `low` in
Economy (Σ25) and `medium` in Balanced and Best (Σ27). GPT-6 Astra scored no higher at about 4.5 times the API price;
no plan model reached Sonnet at `xhigh` or Opus, so for the best a Claude key. Role-play and content keep their tier's
family: Sol at `low` (Economy, Balanced) or Astra at `medium` (Best) for role-play, Sol at `medium` (Economy) or Astra at
`medium` (Balanced) and `high` (Best) for content.

**GPT-6 Luna is for the matrix only** (`Family.matrixOnly`): not acceptable on any task at any effort (Σ15–19: it refused
an action it has, retracted correct rules it had been shown, claimed a card fix it never made). No preset picks it, the
Tutor section's pickers don't offer it (a task set to it before keeps it), and OpenRouter's small tier is GPT-6.1 Sol
now (`OpenRouterModels.DEFAULTS`). The matrix's grid still has its lanes, with a note.

**The matrix's fixes** (its report's 15, section 7):

| # | Fix | What was done |
|---|---|---|
| 1 | Never end a turn silently | With one call left and no reply, the tools' results say so (`TutorLoop.LAST_CALL`). At the limit with still none, one more call with the tools off (`TutorProvider.toolsOff`: `tool_choice: none` on Anthropic, which thinking accepts; forced `tool`/`any` is a 400 on the 5.5 models), after a note (`TOOLS_OFF`); its text is the reply (`ReplyFallback`, now also at the limit). A required turn (a chat, an answer, a session's end, a role-play: `Owed.REQUIRED`) that ends with neither a reply nor text gets that call too, after a user line (`NO_REPLY`). A digest may still end silent. |
| 2 | `tool_use` with no tool call | Asked once more, the empty answer not kept; the second time its text is the reply, or the tools-off call gives one. |
| 3 | Dry runs visible to later reads | The shadow's and the lanes' `card` reads lay the day's dry-run card writes over the deck (`CardOverlay`: an edit's texts and `edits` entry, a merge, a split's new card, a regrade), within the turn and across the day's turns (from the day log's tool calls); never the model's own `dry_run: true` preview nor a refused write. The lanes' shared reads key on each lane's writes. |
| 4 | The card-fault rule | Core prompt rule 15: a wrong answer that is a valid translation of the card's prompt (*mrzel* for "cold", *luč* for "lamp, lantern"), or a confusing card, is the card's fault: `overlaps`, `edit`, `regrade`, tell the learner it counts. |
| 5 | "Correct Slovene" | Added where certain: the cases by number (*mestnik* the 5th, *orodnik* the 6th), the locative of a neuter noun in *-u* with *-em* only for adjectival words (*v Trnovem*, *v Novem mestu*), the dual l-participle (m *-la*, f/n *-li*). Left for the native check: *bojo*, *pa* as "and", *Me zebe*, *noht/nohet*, *Pazi se*. |
| 6 | Speaking items | A sound-spelling from speech-to-text (*u vasi*) is never counted; the standard form beside it (Correct Slovene). |
| 7 | "Not your words" | The shown replies' title says it outright: never correct, retract, apologise for or claim them (nor what they say was done), never mention them or another tutor (`TurnText.SHOWN`, `SHOWN_TODAY`; a restart's exchanges too). |
| 8 | `data.score` only on answers | The `reply` tool (the shadow's and the live one) drops `score` and `severity` unless the reply answers an event whose guide asks for a score (`ReplyData.SCORED`: an answer, a town's check, question and answer, a market's debrief, the family's challenges) and tells the model. |
| 9 | Card-level numbers in `review_done` | The shadow's review event (what the matrix's lanes read) carries `counts` (cards, cards_wrong, forms_asked, forms_wrong, steps, steps_wrong), and its line says the forms asked are within the cards, not cards of their own (`ShadowEvents.reviewCounts`). The live tutor reads the bridge's own text ("19/20 recalled …", the forms after it), which Claude Code's channel shares: unchanged. |
| 10 | A words-added digest's line-up | Not done: a feature (the line-up made by the app from the dictionary, as the counting card's forms are, without a call), not a fix. |
| 11 | `session_end` must persist | A turn with session ends to persist gets its `reply` refused until `record_session` ran (`ReplyData.persistFirst`, as `report_problem`'s lookup check); a message's `reply` runs after its other calls, so one with both passes. The digest's text says a `session_end` in it isn't persisted yet (it claimed every event was). |
| 12 | Follow-ups | Core prompt rule 16: a short follow-up goes on with the last topic, no "which card?". |
| 13 | A practice question every reply, correction messages | Rule 14: most replies end without a question. Rule 1: a correction of an earlier reply in one line of the next. Replacing the last reply in place needs the chat's support: not done. |
| 14 | Offline | The queues (the live tutor's, the shadow's, the lanes') pause while the phone has no usable network (`Online`, `AndroidOnline`: ConnectivityManager's default-network callback: a default network with internet, not blocked for the app; not "validated" too, so a VPN never pauses it wrongly); a network error then isn't retried and its try isn't counted (`Failures.offline`). The background check stops waiting when offline; its next run needs a network (WorkManager's constraint). |
| 15 | OpenAI threads re-fetching the guide | Checked: the Responses thread replays the whole history with `store: false`, the function results included. Luna's re-fetch was Luna's; Luna is out of the tutor. Nothing changed. |

**Gemini's free tier in the EEA** ([providers.md](providers.md), "The free tier and privacy"): hidden where the phone
is in the EEA, Switzerland or the UK (`Region`, `AndroidRegion`: the mobile network's country, else the SIM's, else
the locale's), with the line "Google allows only the paid API for apps used in the EEA (and in Switzerland and the UK):
no free tier here". A key marked free before is treated as paid there (priced and capped, Argon offered, not paced); its
privacy note stays.

**The core prompt changed** (rules 1, 14, 15, 16, the kinds, Correct Slovene), so a day's thread restarts as text at the
first turn after the update (the prefix's hash), as after any prompt change. Its HTML comments are for maintainers and
never sent (`Prefix.bundled`).

## Turning it on

1. Pair the phone with a node whose bridge has this round's code (an older one answers 404: "the node needs an update").
2. "🔗 Povezava · Connection" (the status dot on Home) → "🧑‍🏫 Učitelj · Tutor" → "📱 V aplikaciji · In the app".
3. Choose the provider and give the credential:
   - **Anthropic:** paste a Console key (a workspace of its own, "Lani", with a monthly spend limit, expiry "Never")
     and tap "Preveri in shrani · Check and keep". The check is `GET /v1/models`, not a model call.
   - **OpenAI:** tap "Nadaljuj s ChatGPT · Continue with ChatGPT" (the plan pays), or paste an OpenAI key.
4. Once the credential is kept the app attaches, and the state line says "The tutor in the app answers: ready".
5. Optional: the preset (Economy, Balanced ⭐, Best), "advanced" per task, the daily cap, "react at once".
6. To switch back: "🖥️ Claude Code na mojem vozlišču · Claude Code on my node".

## Tests

- **The bridge's smoke:** 2,429 checks, 0 failed (`test/smoke/tutor-live.ts` and the service section's app-tutor
  part). They cover:
  - the session's tools equal Claude Code's list, the app tools are none of Claude Code's, the app's role (401, 403);
  - attached, a message in the inbox and not at the session, without Claude Code's reply hint;
  - the long poll waking at once and timing out empty, the inbox file;
  - `reply` once per client id, a read (`get_village`), a tool's own validation, an unknown tool's 404;
  - `learner_state`, `record_session` written (the session log, the mistake, the new card), refused without its id;
  - `card` find, show, a dry run, an edit, a refused regrade;
  - the notebook's note, write (the old text kept) and `PUT`; the wishlist entry;
  - ack, and detach giving the session what was left, with its reply hint and `queued_at`;
  - in the service: the status, a new shim's instructions, a stop ending the long poll at once, a restart keeping the
    attach and the inbox, the tools on the service's handlers, detach.
- **The mock's self-check:** `test/tutor-mock-check.ts`, all passed, with `[mock:slow]`.
- **The app's JVM tests:** `LiveTutorTest` (12) runs over the real Messages API client against a scripted API and a
  played tutor session:
  - attach, a chat answered (its tools through the bridge, the guide local), the reply streamed in growing pieces, the
    event acknowledged;
  - the guards: the key only in `x-api-key` to the API, the bridge token only to the bridge, neither in a log line;
  - `session_end` at once with `record_session`; the digest skipped and acknowledged, and a wrong review made a turn;
  - the cap with its note; the key refused and a new key; the credit out with its note;
  - detach handing back; content kinds left unacknowledged; the role-play's own thread;
  - the text as the reply; an old node; a tool the node lacks.

  `PartialTest` (3) covers the partial parse and both streams' tool deltas. The tutor's other 143 tests pass
  unchanged.
- **QA on the emulator** (2026-10-19, the debug build, the dev bridge on a new learner's data made from the templates,
  `LANI_DEV_DATA`; `companion/bin/qa --no-town --steps tutor-live`): setup and tutor-live passed, no crash.
  - The Tutor section showed Claude Code on the node first. "In the app" with a fake key kept the key, and the dev
    bridge's channel became the app's (`GET /tutor/session`: attached `app`; the app listed the node's 53 tools).
  - A chat ("[mock:slow]"): its reply was seen mid-way in the typing bubble, then as the final message. The dev
    bridge's tutor (Claude Code's stand-in) never heard it. The chat said "🤖 Odgovarja umetna inteligenca
    (claude-sonnet-5-5) · An AI answers here".
  - Three calls on Sonnet at `medium`: guide and learner_state, then reply and notebook_note, then the end.
  - A session's end, posted as the app posts it, with "react at once" on: a stateless turn on Haiku at `high`, its
    reaction in the chat. Its `record_session` was in the dev data (the session log, the mistake `mock_pattern`).
  - "Claude Code on my node" again: detached (0 events back, all acknowledged). The next chat was answered by the dev
    bridge's tutor, and the AI line was gone.
  - The key was in no log line. The mock's requests had the phase A tools in order.
  - The run's folder has the screenshots, `tutor-live-mock-requests.json` and `tutor-live-logcat.txt`;
    `LANI_QA_TUTOR_SHOTS` copies the screenshots elsewhere too.
  - The setup step now founds the village when a new learner's data has none ("Tu bo moja vas").

## What's left for phase A

- **Reports as GitHub discussions:** built in phase C with git sync's GitHub sign-in ([studio.md](studio.md), "Reports as
  GitHub discussions"); without a sign-in `report_problem` keeps the wishlist.
- **The rhythm on the phone:** the node's timer still sends `daily_*`. On the phone it becomes one-time WorkManager work.
- **Export and import with a node** (plan 5's phase A exit): not in this round.
- **Curated content as a download, clips from glas, the phone's STT** for a learner without a node (the hosted mode
  proper).
- **"What my tutor knows"** shows the notebook's versions and restores one since phase C ([studio.md](studio.md)).
- **The learner-facing setup** of the first launch ("☁️ Lani v oblaku"): the hosted round's. The ChatGPT plan's
  welcome at the first sign-in (OpenAI's UI guidelines) and the GPT-6 thread's `configuration_update` for a turn at
  another effort are done in phase C ([studio.md](studio.md), "The caps, the notebook's versions, OpenAI").
- **The monthly cap** of the plan ("only chats the learner sends, after asking"): built in phase C
  ([studio.md](studio.md), "The caps").
- **Batching `gloss_gaps`:** each one is a stateless call on the routine model now.
- **Measuring:** the live tutor's calls are in its day logs (`files/tutor-live/days/`). There is no export or upload
  of them yet (the shadow's `DevUpload` doesn't include them).

## What waits for the phone runtime

- **The same routes in-process: built, not wired.** `PhoneLink` is an OkHttp interceptor that hands `LiveBridge`'s calls
  to `PhoneBridge.handle`, so the live tutor runs against the phone's own bridge with no other change. `LivePhoneTest`
  runs it against the real bundle in QuickJS: attached in-process, a chat answered with `learner_state` from the db
  port, the reply as the phone bridge's event, a session written by `record_session`, detached. What's left is the
  hosted mode's wiring: a learner without a node, whose app makes its `LiveBridge` with `PhoneLink.client` instead of
  the node's address.
- **The scripts:** `learner_state`, `record_session` and `card` spawn `python3 .claude/hooks/*.py` through the bridge's
  spawn path, which the phone runtime dispatches to the db port (`src/db/cli.ts`). The notebook, the inbox and the
  wishlist are plain files under the data directory (`<filesDir>/lani/data/app/`).
- **The long poll** uses only `setTimeout` and promises (no `Bun.sleep`), so it can run on the phone's event loop.
- **The node's own timers** (the rhythm, the gloss gaps) notify through the channel. On the phone they do the same,
  and the inbox delivers them to the in-process tutor.
