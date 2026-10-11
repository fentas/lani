# Plan 5: the tutor in the app

*Status: draft, 2026-10-11. Jan's decisions of 2026-10-11 (below) change the hosted variant of
[plan 1](01-public-availability.md): no Claude runs on our servers. The self-hosted mode stays as it is. Builds on
[plan 3](03-voice-service.md) (glas, the voice service) and [plan 4](04-bridge-service.md) (the bridge as a service).
The cost figures are estimates from Jan's tutor logs; a prototype measures them first (see [Phases](#phases)).*

Today the tutor is Claude Code in a terminal on Jan's machine. The app reaches it over Tailscale through the bridge (an
MCP server and channel). That needs an always-on machine, a Claude subscription, Tailscale and a setup most learners
can't do. This plan adds a second mode where the app itself is the tutor's home:

- **Self-hosted**, as today: Claude Code on the learner's machine, on their Claude subscription.
- **Hosted**: the app runs the tutor: an agent loop on the phone against the Claude API, with typed tools over the
  learner's data, which lives on the phone. Voices and the few things that need a server come from our SaaS (plan 3's
  glas and the services beside it). No Claude instance runs on our side; there is no Tailscale and no node. Two ways to
  pay:
  - **BYOK** (bring your own key): the learner's own Claude API key from the Anthropic Console. They pay Anthropic.
  - **Covered**: the learner pays us, and the app's API calls go through our SaaS, which holds our key. Whether we offer
    it, and at what price, is decided by the measured cost.

**The core of the design is a slim tutor.** Claude Code's tutor carries a 107k-token context on average (Claude Code's
system prompt, about 60 tools, CLAUDE.md, the long session) and rewrites most of it after every pause. The in-app tutor
keeps:

- a small stable prefix (~10–15k tokens: the core prompt and 16–25 tools), cached;
- the guide, fetched on demand;
- a notebook of what it knows about the learner;
- one short thread per day.

It picks the model per task: Haiku for routine reactions, Sonnet for chat and role-play, Opus only for writing
content on request.

**The estimate:** about $0.85 a day for a learner as active as Jan (~$25 a month) and about $0.30 for a quieter one.
Today's tutor costs $4.39 a day on Opus 5.5.

**The bigger job isn't the agent loop.** In the hosted mode the app loses its backend: the bridge serves the app 99 HTTP
routes besides the tutor's 41 tools, about 25,000 lines of TypeScript. Most of that moves into the app (Kotlin) or the
SaaS (see [The app's backend](#the-apps-backend-the-bridges-http-routes)).

## Jan's decisions (2026-10-11)

- **Two modes, both kept:**
  1. **Self-hosted** (as today): Claude Code on the learner's own machine with their standard Claude subscription, the
     bridge as MCP server and channel, the phone over Tailscale. Voices are self-hosted (an ElevenLabs key, Gepard) or
     come from our voice SaaS.
  2. **Hosted**: the tutor runs **in the app**, on the learner's **own Claude API key** (Anthropic Console), with **our
     voice and audio SaaS** (plan 3). No Claude instances hosted by us, no Tailscale, no node.
- **Covered, a variant of the hosted mode** (Jan: "depending on the price maybe we could also just cover the API key
  (à la full hosting)"). The learner pays us a subscription and we pay Anthropic, through the SaaS as a proxy. The
  measured cost decides. See [The covered variant](#the-covered-variant).
- **Prompt injection is contained by design.**
  - With the in-app tutor, the model's tool calls run only in the app, against typed tools: no shell, no free file
    writes, no web.
  - Untrusted inputs (family messages, linked towns, visitors) go in as marked data.
  - Worst case: bad content in that learner's own village.
  - The voice SaaS has no LLM.
- **"We manage our own session/compaction."** See [Context and compaction](#context-and-compaction).

## Why not another way (checked 2026-10-11)

| Way | Why not |
|---|---|
| Sign in with the learner's Claude subscription | The [Agent SDK overview](https://code.claude.com/docs/en/agent-sdk/overview): *"Unless previously approved, Anthropic does not allow third party developers to offer claude.ai login or rate limits for their products…"*. Parked, as plan 1's option 2c. |
| Bundle Claude Code with the app | It's proprietary, and a Node CLI: it doesn't run on Android. The Agent SDK is Claude Code as a library, so the same applies. |
| A remote MCP connector in the Claude apps | It works on every plan ([support article](https://support.claude.com/en/articles/11175166)). But the tutor can't be pinged: no reaction to a review or a session's end, no chat from inside Lani. **Rejected by Jan:** reactions and chat are a major feature. |
| Claude Code routines with an API trigger ([docs](https://code.claude.com/docs/en/routines.md)) | Pro/Max, `POST …/routines/<id>/fire`, a fresh cloud session per fire, 30 fires an hour per routine, a research preview, *"not part of the Claude Platform API surface"*. It would keep the pings on the subscription, but it's a workaround around the API key. **Rejected.** |
| Managed Agents (Anthropic runs the loop and a sandbox per session) | We need no sandbox: the tools are the app's, and the loop is small. It would add a beta dependency and a server-side session per learner for what the app can do itself. *Not checked in detail.* |

**The Max and Team plans' API credits** (checked 2026-10-11 in [Monthly API credits for Max and Team
plans](https://support.claude.com/en/articles/17154008-monthly-api-credits-for-max-and-team-plans), announced in
[15036540](https://support.claude.com/en/articles/15036540) on 2026-10-07):

- **Amounts:** Max 5x $100 a month, Max 20x $200; Team $20 per Standard seat and $100 per Premium seat, pooled, at
  most $500. Pro has none.
- **What they cover:** the Claude API (the Messages and Message Batches APIs), the Console's Playground, Managed Agents
  and the Agent SDK. Not Claude Code, Bedrock, Vertex AI or Foundry.
- **How they reach a key:** the learner links a Console organization to their plan. *"Everyone with an API key in the
  linked organization draws from the same balance."*
- **They expire** at the end of each billing cycle. When they run out and the organization has no other credits, *"API
  requests stop until your next monthly credits arrive"* (or the learner buys credits or turns on auto-reload).

So a Max learner's key, made in the linked organization, is in effect paid by the plan. $100 a month covers even
the most expensive slim setup below many times over. *Still open: whether the article's terms say anything about
using the credits through a third-party app (it says nothing either way; the key is the learner's own).*

## The modes side by side

| | Self-hosted | Hosted, BYOK | Hosted, covered |
|---|---|---|---|
| **Claude access** | Claude Code with the learner's subscription (Pro/Max), or a Console key | The learner's own Console API key, kept on the phone | Our key, held by the SaaS proxy; never in the app |
| **Where the tutor runs** | A Claude Code session on the learner's machine (tmux), the bridge as MCP server and channel | In the app: our agent loop, the tools over local data | In the app; the proxy adds the prompt and the tools and meters |
| **Model** | Whatever the session runs (Jan: Opus 5.5) | Per task, chosen by the app (the learner can change it) | Per task and plan, chosen by the proxy |
| **Voice** | Own ElevenLabs key, local Gepard, or glas with a node key | glas: shared clips, live lines with a quota; the phone's TTS as the fallback | The same |
| **Speech recognition** | Local Whisper or the phone | The phone's recognizer; Whisper in the SaaS later | The same |
| **The learner's data** | The node's data repository (git), the source of truth | The phone, the source of truth; an encrypted backup in the SaaS | The same |
| **Network** | Phone ↔ node over Tailscale | Phone → Anthropic, phone → SaaS (HTTPS) | Phone → SaaS only |
| **Who pays** | The learner: their subscription (or API use), optionally ElevenLabs. Nothing to us | The learner pays Anthropic per token (Max and Team: from the plan's credits). Voices: free tier or a small fee (open) | The learner pays us a subscription; we pay Anthropic and the voices |
| **Setup** | `lani setup` on an always-on machine, Tailscale, the pairing QR code | Install the app, paste a key, sign in to the SaaS (voices, backup) | Install the app, sign in, subscribe |
| **The tutor's reach** | Everything Claude Code can do: content authoring, edits to the repository, the shell (with permission prompts) | The app's typed tools only | The same |
| **Content writing** | Full (`lani-studio`) | Later (phase C), narrower | Later, by plan |
| **Towns and family** | Bridge to bridge over Tailscale | Through the SaaS relay (phase D) | The same |

**What's shared:**
- **The content:** the culture packs, modules, word packs, scenes, villagers, grammar book and drills. The same files
  and schemas.
- **The tools' meaning:** the same names, inputs and effects. `publish_gloss` does the same in the bridge and in the
  app. One spec for both (see [One source of truth](#one-source-of-truth)).
- **The learner's data formats:** the six databases as JSON, the answers log, the tutor's content files. So data moves
  between modes by export and import.
- **The game:** it's the same app. Only where the tutor and the data live differs.
- **The guide:** the channel guide's paragraphs and the skills, as topics from one set of files.

**What differs:**
- **The harness.**
  - Self-hosted: Claude Code, with its system prompt, its own tools (shell, files, web), skills, permission prompts and
    compaction.
  - Hosted: our loop, with typed tools only and our own compaction.
- **Memory:** the long session (self-hosted) against the notebook and the day's threads (hosted).
- **The tutor's edits to the repository** (CLAUDE.md, fixes to curated content, code) exist only when self-hosted.
  In the hosted mode, a wrong curated item becomes a report to us.
- **Where the data lives**, and how family and towns connect.

---

## Where we are

```
Self-hosted (today):
Android app ─HTTPS (Tailscale)─► bridge service (Bun) ◄─socket─ shim ◄─stdio─ Claude Code (tmux, Opus 5.5)
                                  ├─ learner data: the data repo (6 JSON DBs, the     ├─ Python: read-db, update-db, card.py
                                  │  answers log, the tutor's content)                ├─ skills, CLAUDE.md, LEARNING_SYSTEM.md
                                  ├─ voice store ── ElevenLabs, Gepard, glas          └─ its own tools: shell, files, web
                                  ├─ towns ◄──► other bridges; the family page
                                  └─ 99 HTTP routes for the app, 41 MCP tools for the tutor

Hosted (this plan):
Android app: the tutor's loop, typed tools, the learner's data (the source of truth)
  ├─HTTPS─► Claude API (BYOK: the learner's key)
  │   or ─► SaaS proxy ─► Claude API (covered: our key)
  └─HTTPS─► SaaS: glas (clips, live lines), backup, relay (towns, family), reports
```

### What the tutor costs today (measured)

From Jan's tutor session logs, 2026-09-23 to 2026-10-11, 16 active days. API prices are from the
[pricing page](https://platform.claude.com/docs/en/about-claude/pricing), checked 2026-10-11.

| | Opus 5.5 | The same tokens on Sonnet 5.5 | On Haiku 4.5 |
|---|---|---|---|
| Average day | $4.39 | $2.20 | $1.37 |
| Quiet days (10–20 calls) | $1.70–3.00 | | |
| Heavy day, 2026-10-11 (126 calls: chat, fixes, publishing) | $12.46 | | |

- **The cost split on Opus:** cache writes 62 %, cache reads 25 %, output 14 %, uncached input about 0 %.
- **Context per call:** 107k tokens on average (Claude Code's system prompt, about 60 MCP tools, 41 of them the
  bridge's, CLAUDE.md, the session).
- **A typical day's events:** 3–8 `review_done`, 1–14 `chat`, 1–5 `session_end`, `words_learned` and `words_added`,
  `daily_morning` and `daily_evening`, a role-play, an `answer`, a `reading_done`.

**What the numbers say** (derived here, worth checking against the logs):

- **About 51 calls on an average day:** $1.10 of cache reads at $0.20 a million is 5.5M tokens, about 51 reads of
  107k.
- **3–5 full rewrites a day:** $2.72 of cache writes is 340k tokens at the 1-hour price, or 544k at the 5-minute price.
  Each pause longer than the cache's lifetime makes the next event write the whole context again. That is where the
  62 % goes.
- **About 30k output tokens a day.**

**The bridge's own share of the 107k is small:**
- the 41 tool definitions, about 24k characters (~6k tokens, estimated from the source);
- the channel guide, about 28–36k characters (~7–9k tokens), once `channel_guide` is called.

The rest is Claude Code itself and, above all, the session.

So the slim tutor saves in three places, in this order:
1. a short thread, so a rewrite after a pause is small;
2. a small prefix;
3. the model per task.

---

## The tool inventory

### The bridge's MCP tools (41)

Counted from the features' `tools` arrays in `companion/bridge/src` (40) plus `channel_guide` (`bridge.ts`). For the
hosted mode each tool is one of:
- **port**: to the app, in Kotlin, over local data, in phase A or B;
- **later**: ported with content writing (phase C);
- **SaaS**: its work is glas's;
- **drop**: a tool for developers or the operator, or content that is ours to make, not the tutor's.

| Feature | Tool | Hosted | Phase | Note |
|---|---|---|---|---|
| chat | `reply` | port | A | Still the only way the tutor speaks, in both modes. Its text streams into the chat sheet. |
| (bridge) | `channel_guide` | port | A | Becomes `guide(topic)`: one topic at a time, not the whole guide. |
| game | `get_village` | port | A | The game state is on the phone already. |
| reviews | `recent_answers` | port | A | The answers log (`answers.ts`) moves to the app. |
| lexicon | `publish_gloss` | port | A | Lookups the dictionary missed, and `gloss_gaps`. |
| sentences | `publish_sentence_note` | port | A | |
| rhythm | `set_rhythm` | port | A | The schedule runs on the phone. |
| packs | `list_packs` | port | A | The learner's progress per pack. |
| | `publish_pack` | later | C | "Teach me words for the doctor." |
| grammar | `list_grammar`, `get_grammar` | port | A | Feedback names the book's pages. |
| | `publish_grammar` | port | B | The book the tutor keeps: small, and part of explaining. |
| modules | `publish_module`, `list_modules`, `get_module` | later | C | |
| | `rollback_module` | drop | | The app keeps a module's versions. A fix is a new version. |
| scenarios | `publish_scenario` | later | C | Playing role-plays needs no tool: the brief rides with the event. |
| scenes | `list_scenes`, `get_scene` | later | C | |
| | `publish_dialog_variant`, `remove_dialog_variant` | later | C | `dialog_variants_heard`, the morning's `words_to_weave`. |
| | `publish_scene`, `remove_scene` | drop | | Scenes are culture-pack content with their art (plan 2, layer 1). |
| stories | `list_stories`, `get_story`, `publish_story`, `remove_story` | later | C | `stories_low`: through Batches, at half price. |
| readings | `list_readings`, `get_reading`, `publish_reading`, `remove_reading` | later | C | |
| villagers | `list_villagers`, `get_villager` | later | C | For content that names people. A villager talk gets the persona with the event. |
| | `publish_villager`, `remove_villager` | drop | | The cast comes from the culture pack (plan 2). |
| arrivals | `get_arrival`, `publish_arrival`, `remove_arrival` | later | C | Until then the culture pack's template plays, as it does when no tutor answers. |
| voice | `voice_profiles` | SaaS | A | glas's characters (`GET /v1/characters`): who speaks with which voice. |
| | `voice_redesign` | SaaS | B | A request to glas. Voices are shared, so a redesign is ours, made for everyone. |
| | `voice_status` | drop | | An operator's view: glas's `/v1/usage` and metrics. |

**Counts: port 11** (10 in phase A, 1 in B), **later 22** (phase C), **SaaS 2**, **drop 6**.

### New tools the hosted tutor needs

Claude Code does these today with Python, files and the shell:

| Tool | From | Phase |
|---|---|---|
| `learner_state` | `read-db.py`: the six databases summed up, the due reviews, the streak, the languages | A |
| `record_session` | `update-db.py`'s report ([docs/DB_SCRIPTS.md](../DB_SCRIPTS.md)), today written through the `lani-db-updater` skill | A |
| `card` (find, show, overlaps, edit, merge, split, regrade, forgive) | `card.py` (`lani-fix`) | A |
| `notebook_note`, `notebook_write` | New: the notebook (below) | A |
| `report_problem` | New: a curated item that's wrong, sent to us through the SaaS. Today it's "a note for the developer" in the repository. | A |
| `request_content` | New: hands a brief to the studio task (content writing) | C |

So phase A's tutor has 16 tools: 10 ported, 6 new (with `card` as one tool).

### The channel's events

The kinds the tutor gets today: from the app through `POST /message` (`chat`, `answer`, `session_end`, `roleplay`,
`roleplay_end`, `villager_arrived`), and from the bridge's features (`channel.notify`). How each becomes work in the
hosted mode:

| Kind | Today | Hosted | Model | Phase |
|---|---|---|---|---|
| `chat` (also with `about_exercise`, `about_sentence`, `lookup`, `reply_to`) | A turn | A turn at once, streamed. A fix calls `card`. | Sonnet | A |
| `chat` with `about_roleplay` | A turn | A turn | Sonnet | B |
| `answer` (`grade="claude"`) | Grade, reply with the score | A turn at once: the learner waits for it | Haiku, Sonnet if measured worse | A |
| `session_end` | Persist with `update-db.py`, a short summary | A turn: `record_session`, a short summary | Haiku (Sonnet for a writing session) | A |
| `review_done` | Persisted by the bridge; reply only if useful | Persisted by the app. **Batched** into the day's digest. A reaction at most every few hours or at the next app open, and only when there is something to say (answers typed wrong). | Haiku | A |
| `words_added`, `words_learned`, `reading_done`, `dialog_words` | Persisted; mostly no reply | Into the digest; no call of their own | | A |
| `gloss_gaps` | `publish_gloss` for each, no reply | In the background, batched and stateless (no thread) | Haiku | A |
| `daily_morning`, `daily_evening`, `daily_weekly` | The bridge's timer | Scheduled on the phone. The evening closes the day (the notebook). | Sonnet | A |
| `level_up` | Congratulate, plan the next level | A turn | Sonnet | B |
| `roleplay`, `roleplay_end` | In character; a debrief, persisted | Their own short thread; the debrief goes into the day's thread | Sonnet (Haiku to test for turns) | B |
| `villager_arrived` | `publish_arrival` | The template plays; from phase C, the studio task | Sonnet | C |
| `dialog_variants_heard`, `stories_low` | Write content | The studio task, through Batches: nobody waits for it | Sonnet or Opus | C |
| `partner_challenge`, `partner_answer` | Feedback on the family's challenge | Through the SaaS relay | Haiku or Sonnet | D |
| `town_gift`, `town_question`, `town_answered`, `town_check`, `town_asked`, `town_answer` | Per kind | Through the SaaS relay | Haiku or Sonnet | D |
| (the permission relay) | The app approves Claude Code's tool use | None: typed tools need no approval | | |

### The Python scripts and the skills

| Script or skill | Hosted |
|---|---|
| `read-db.py` | Port. The app reads its own databases; `learner_state` gives the tutor the summary. |
| `update-db.py` (836 lines, with SM-2: `calculate_sm2`, `lower_gently`) | Port to Kotlin. The app persists reviews, packs, readings and dialog words itself; today the bridge does it through this script. `record_session` is the tutor's way in. The app already maps answers to SM-2 qualities (`ReviewPlanner.kt`); the interval and easiness update moves next to it. |
| `card.py` (798 lines) | Port: the `card` tool. |
| `lani_languages.py` (a profile per language, plan 2's M4) | Port. |
| `validate-data.py` | Port: the checks on import. |
| `lani_data_repo.py`, `lani_paths.py`, `ensure_data_dir.py` | Drop: there is no data repository on the phone. The backup replaces git. |
| `session-start.py`, `session-end.py`, `precompact-backup.sh` (Claude Code's hooks) | Drop: the loop does their work. It loads at the day's start and saves at its end. |
| `lani-feedback-formatter`, `lani-fix`, `lani-session-analyzer`, `lani-progress` | Guide topics. |
| `lani-db-updater`, `lani-sm2-calculator` | Go into `record_session`'s description and the app's code. |
| `lani-studio` | The studio task's guide (phase C). |
| `lani-learn`, `-vocab`, `-writing`, `-speaking`, `-reading`, `-review` | Drop at first: they are terminal sessions, and the app has its own exercises. Writing practice in the chat comes later. |
| `lani-setup`, `lani-update` | Drop: the app's onboarding and app updates do their work. |

### The app's backend: the bridge's HTTP routes

The tools are the smaller part. The bridge serves the app 99 routes (counted per feature). Each goes to the app, to the
SaaS, or nowhere:

| Area | Routes today | Hosted |
|---|---|---|
| The learner's data and reviews | `/state`, `/reviews`, `/readings/done`, `/packs/:id/learn`, `/level`, `/words` | The app (the Kotlin port of `update-db.py`) |
| Content | `/modules`, `/packs`, `/grammar`, `/drills`, `/scenarios`, `/scenes`, `/villagers`, `/arrivals`, `/stories`, `/readings`, `/culture`, `/cultures` | The app. Curated content becomes a versioned download (plan 1, Phase 0, item 6: about 10 MB of JSON today). The tutor's content stays on the phone. |
| Words | `/lookup`, `/forms`, `/sentence`, `/lexicon/status` | The app: the dictionary download (Slovene, 1.4 MB built) and the lookup code (`lexicon.ts`, `forms.ts`, `lexicon-meanings.ts`: about 1,900 lines) |
| The game | `/game` | The app, which holds the state already. The sync goes away; the backup takes its place. |
| Chat and events | `/message`, `/permission`, `/events`, `/events/backlog` | The app's loop |
| Voice and STT | `/voice/*` (8), `/voice/offline/*` (4), `/stt` (3) | The SaaS (glas) and the phone |
| Family | `/audio*`, `/family/api/*` (11) | The SaaS relay (phase D) |
| Towns, friendship, questions | `/town/*`, `/towns/*` (32) | The SaaS relay (phase D) |
| Pairing, releases | `/pair`, `/app/latest`, `/app/apk` | Drop: sign-in instead of pairing; Play or GitHub releases |

The bridge is about 25,000 lines of TypeScript, 3,000 of them the voice store (`voice.ts`), which goes to glas. A good
part of the rest must exist in Kotlin for the hosted mode. The app already parses and renders every content type, so
the content's validation maps onto its models.

**Two ways to avoid two implementations drifting:**
1. Port feature by feature, with a shared conformance corpus (below).
2. Run the bridge's TypeScript logic (the validators, the lookups) in a small JavaScript engine inside the app, such as
   QuickJS (1–2 MB).

Decide in the prototype, for the validators first.

### One source of truth

- **Content schemas.**
  - The bridge uses zod 4, which can emit JSON Schema (`z.toJSONSchema`). Some validators are written by hand
    (`validateScene`, `validateStory`, `validateModule`, …) and would move to zod first.
  - Generate JSON Schema files into the repository (e.g. `companion/schemas/`) in CI. The bridge validates with zod;
    the app's Kotlin models are tested against the same schemas.
  - **Source:** zod, not the reverse. Content is written where zod already runs.
  - **The conformance corpus:** every curated file plus a set of bad examples, run by the bridge's tests and the app's.
  - **What isn't schema** goes through the same corpus: a dialog's `words` must be tested elements, ids must exist,
    forms must agree. Those checks are ported or shared (see above).
- **Tool specs:** one JSON file per tool (name, description, input schema). The bridge registers its MCP tools from
  them; the app bundles them as the Messages API's `tools`; the covered proxy serves them per app version.
- **The guide:** Markdown topics (e.g. `companion/tutor/guide/*.md`), made from today's feature paragraphs and skills.
  The bridge's `channel_guide` joins them; the app's `guide(topic)` returns one.
  - Today's text names Python scripts and skills. The topics say *what* to do; the mode-specific *how* goes in short,
    marked lines.

---

## The agent loop in the app

### Events to turns

- **A turn** is one event (or a digest of several) as a user message, plus the tool loop until the model ends its
  turn.
- **The user message** carries the event's JSON in today's shapes (the channel's), plus the local time and where the
  learner is. The first turn of a day also carries the notebook.
- **Order:** one turn at a time per thread, from a persisted queue. A chat goes ahead of digests.
- **Batching:** routine events (reviews, words, readings) go into the day's digest, persisted in the app. A digest turn
  runs when:
  - the app opens after two hours or more;
  - the evening comes;
  - the learner opens the chat.

  A digest with nothing worth saying (no answers typed wrong, no milestone) ends without a call.
- **At once:** a chat, an `answer` (the learner waits for the grade) and a role-play turn.

### The tool loop

- **A manual loop**, not the SDK's tool runner. Each step is saved, so a turn resumes after Android kills the app.
- **Parallel tool calls** run together, and their results go back in one message.
- **A failed or invalid call** returns `is_error` with the validator's message, as the bridge's `fail()` does today.
- **Limits:** at most 8 calls per turn, a `max_tokens` per task, and the budget checked before every call.
- **Stop reasons** are handled: `end_turn`, `tool_use`, `max_tokens` (continue once), `refusal` (a neutral message, no
  retry).
- **`tool_choice: auto` only.** Forced tool use returns a 400 on Opus 5.5 (and may on others), so the prompt names
  the tool instead. `strict: true` where a tool's schema allows it.
- **Thinking:** adaptive, with effort set per task (low for reactions, medium for chat). The thread passes thinking
  blocks back unchanged.

### The client: the Java SDK or plain HTTP (to verify)

- **The official Java SDK** is written in Kotlin and runs on OkHttp; its README says *"Java 8+"* and nothing about
  Android. A spike checks:
  - that it builds with R8 (Jackson's reflection may need keep rules);
  - what it adds to the APK;
  - that streaming works on Android 8+ (the app's `minSdk` is 26).

  It brings typed errors, retries (2 by default, for 408, 409, 429 and 5xx) and the stream helpers.
- **The fallback:** OkHttp, `okhttp-sse` and kotlinx.serialization, all three in the app already, against
  `POST /v1/messages`. That is a small client for the part we use: messages, the stream's events, tool blocks, usage,
  errors, `retry-after`.
- **One interface** (`TutorTransport`):
  - BYOK calls `api.anthropic.com` with `x-api-key`;
  - covered calls the SaaS proxy with the account's token.

  The requests are the same.

### Streaming into the chat sheet

- Requests stream (`stream: true`).
- The tutor speaks only through `reply`, as in the self-hosted mode, so the guide is the same. With
  `eager_input_streaming: true` on `reply`, its `text` arrives while it's written. The chat sheet shows it as it comes
  (a partial parse of one string field); the final, validated input replaces it.
- Text outside `reply` isn't shown (it's logged for debugging).
- *To verify:* how it feels. The alternative: the turn's final text is the reply to its conversation, and `reply` stays
  for structured data and other conversations.
- A reply that comes while the chat is closed arrives as a notification, as now.

### Background work on Android

- **Turns run in the app's process,** in a scope that outlives the screens, as the controllers do now. A turn takes
  seconds.
- **The learner leaves mid-turn:** the turn finishes as expedited WorkManager work. If Android stops it, the saved step
  resumes at the next start.
- **The rhythm** (morning, evening, weekly) is one-time WorkManager work with a delay. The app already runs a 15-minute
  periodic `BackgroundCheck`. The timing isn't exact, which is fine. It needs the network. The reply comes as a
  notification.
- **Reactions wait for the app to be open.** Digests run when the app opens, not in the background; only the rhythm
  does.
- **No long foreground services.** Android 14 and later limit them (types; time limits for `dataSync`). *To verify on
  Android 16.*

### The API key

- **Entered at setup:** pasted from the Console, and checked with `GET /v1/models`, which uses no tokens.
- **Stored encrypted:** an AES-GCM key in the Android Keystore (not exportable) encrypts it, and the ciphertext goes in
  DataStore.
  - It's decrypted in memory for a call only.
  - It's never in logs, prompts, tool results or the export.
  - The app's `allowBackup` is already `false`.
  - Jetpack Security's `EncryptedSharedPreferences` is deprecated *(to verify)*, so the plan uses the Keystore directly.
    Today's bridge token sits in DataStore unencrypted: it should move to the same scheme.
- **Setup suggests a Console workspace for Lani,** with its own key and a monthly spend limit, so Anthropic enforces a
  cap as well as the app. *To verify: the Console's workspace limits.*
- **Settings can replace or remove the key.** On a 401, the app asks for a new one.

### Errors, rate limits, offline

| What | The app |
|---|---|
| 401 authentication | Asks for a new key. Nothing is lost: the events stay queued. |
| 400, the credit balance too low *(check the exact error)* | Says so (Console billing; a Max plan's credits come back next month). Routine work waits; a chat says why it can't answer. |
| 429 rate limit | Waits for `retry-after`, then tries again. Routine work backs off longer. |
| 529 overloaded, 5xx, timeouts | Retries with backoff. A routine reaction may fall back to the other model. |
| `refusal` | A neutral message, no automatic retry. |
| No network | The event stays queued. |

- **Rate limits:** a new Console organization starts at the lowest usage tier, with low per-minute limits. That is
  enough for one learner: cache reads don't count toward the input-token rate limit on most models. *To verify: the
  tier-1 limits for Sonnet 5.5 and Haiku 5.5.*
- **Offline:**
  - Events queue in the app, persisted, like the outbox. The tutor answers later, and the turn carries `queued_at`, as
    plan 4's queue does.
  - Queued routine events merge into one digest.
  - A role-play needs the network, and the app says so.
  - A chat sent offline is answered when the phone is back online.

### Prompt injection

- **The tools are typed, local and validated.** No shell, file paths, URLs or web. No tool sends anything to another
  person or town: sending is the learner's own action in the UI.
- **Untrusted text is marked data:** family messages, a town's gifts and questions, a visitor's text. It comes inside a
  marked block (`untrusted_family_content`, `untrusted_town_content`, as the bridge's `untrustedJson` does). The core
  prompt says it's content, never instructions.
- **No way out for data:**
  - The only outbound traffic is the request to Anthropic and the reply to the learner.
  - The chat's Markdown loads no remote images (the app's renderer has no image loader: keep it so).
  - A link opens only on a tap.
- **No tool can reach the key or the settings.**
- **The worst case:** bad content in the learner's own village (a gloss, a grammar page, a dialog variant, a notebook
  line). It can be undone: the tutor's content can be removed, and the notebook is visible and editable.
- **Covered:** the proxy adds server-side limits (below). glas runs no model.

---

## Context and compaction

**What a call carries**, in order (the cache is a prefix match: what changes least comes first):

```
[tools + core prompt]                  stable; 1-hour cache breakpoint                     ~10–15k
[notebook + yesterday in brief]        first user message of the day's thread                ~2–3k
[the day's turns: events, tool calls and results, replies]   grows; automatic cache, 5 min  up to ~30k
[the new event]
```

### The stable prefix

- **What's in it:**
  - the tools, in a fixed order per app version;
  - the core prompt: who the tutor is, the rules (one question at a time, the feedback format, phone-sized replies,
    untrusted content), the event kinds in brief, and the guide's list of topics.
- **What isn't:** anything about the learner, and anything time-dependent. That goes in the messages.
- **Size:** about 10–15k tokens. One breakpoint sits at its end, with a 1-hour lifetime.
- **Caching per mode:**
  - BYOK caches it per learner (caches are per workspace).
  - Covered: one cache entry per model and app version serves every learner (see
    [The covered variant](#the-covered-variant)).
- **The minimum cacheable length** is 512 tokens on the 5.5 models (1,024 on Haiku 4.5): not a concern.

### The guide on demand

- **`guide(topic)`** returns one topic: feedback, fix, session end, review digest, role-play, grammar book, words,
  rhythm, progress, analysis, village; studio (phase C); family and towns (phase D).
- **Its result stays in the day's thread** (cached) until the thread restarts.
- **Topics fetched every day move into the prefix.** The prototype counts them.

### The notebook

What Claude Code's long session holds today, written down:

- **The learner:** goals, level, what they told about their life, the family's names.
- **How they like to learn.**
- **Recurring mistakes,** in the tutor's words: the pattern ids from the mistakes database, and what helped.
- **Plans:** today, this week.
- **Open promises:** "a drill on clitics tomorrow".
- **The last days,** in a few lines.

How it's kept:

- At most about 2–3k tokens.
- It's the first user message of each day's thread, after the prefix, so rewriting it never touches the prefix.
- `notebook_note(text)` adds a line during the day (cheap). `notebook_write` rewrites it at the day's end and when the
  thread restarts. The old version is kept.
- **The learner can read it** in the app ("What my tutor knows") and correct or delete lines. It's part of their data:
  export, backup.
- **The self-hosted mode could adopt it too:** a file in the data repository, read at the session's start and
  rewritten at compaction (Claude Code's PreCompact hook). Then both modes have one memory, and a move between modes
  keeps it.

### The day's thread

- **One thread per local day** (from 04:00), only ever appended to: the notebook and yesterday in brief, then each
  turn's event, tool calls, results and reply.
- **A role-play gets its own short thread:** the scenario and its turns. Its debrief goes into the day's thread.
- **Routine reactions on Haiku don't use the thread.** They're a stateless call: the prefix, the notebook, the event
  and today's digest. Their reply is added to the day's thread as a line, so the main tutor knows what was said.
  - Why: caches are per model. A Haiku call on the Sonnet thread would write the whole thread again for Haiku.

### Compaction: our own

- **At the day's end** (the evening event, or the first event of a new day): a closing call on the same model and
  prefix. The model reads the thread and calls `notebook_write`. The model's thread is then dropped; the learner's
  chat history is the chat archive, as now.
- **During the day,** when the thread passes a limit (~25–30k tokens; after each call: input + cache reads + cache
  writes):
  1. `notebook_write`, as at the day's end;
  2. a new thread: the notebook, a few lines on the day so far, and the last 2–3 exchanges re-sent as plain text in
     its first user message (not as assistant turns).

  Old tool results go with the restart.
- **Why this way:** the 5.5 models keep their thinking ([preserved
  thinking](https://platform.claude.com/docs/en/build-with-claude/preserved-thinking)).
  - Editing earlier turns on the client invalidates the thinking blocks of every later turn.
  - For accounts made on or after 2026-08-31 (every new learner's key), such a request is refused, unless it opts into
    dropping those blocks.
  - A thread that is only appended to, and restarted fresh, never edits history.
- **The API's alternatives,** to try in the prototype:
  - **Compaction on demand** (beta `compact-2026-09-04`; Opus 5.5, Sonnet 5.5 and Haiku 5.5, not Haiku 4.5). A
    signed summary block replaces the summarized messages. Custom `instructions` up to 16,384 characters. It needs the
    same system prompt and tools.
  - **Context editing** (beta `context-management-2025-06-27`): `clear_tool_uses_20250919` and
    `clear_thinking_20251015`. Clearing tool results invalidates the cache from that point. It can't be combined
    with compaction on one request.
- **Ours is the default.** It works the same in every mode and on every model, the notebook shows what's kept, and it
  needs no beta. Compaction on demand could replace the mid-day restart if it keeps more of the day.

### Caching: lifetimes and breakpoints

- **Breakpoint 1:** the end of the prefix, 1 hour.
- **Breakpoint 2:** automatic caching on the last block, 5 minutes. The API wants longer lifetimes first, and this
  order meets that.
- **What a pause costs:**
  - 5 to 60 minutes: a rewrite of the thread, not the prefix;
  - over an hour: both.

  So a short thread matters more than anything else: it's Claude Code's 62 %.
- **The 1-hour lifetime pays only when the learner comes back within the hour** (its write costs twice the input
  price, the 5-minute one 1.25 times). The prototype measures the gaps between events and chooses, or uses 5 minutes
  everywhere.
- **Within a turn's tool loop and a conversation, the thread stays cached.**

### Budgets and the spend shown

- **BYOK:**
  - The learner sets a daily budget and a monthly cap (defaults such as $1 a day and $20 a month).
  - The app prices each response's `usage` with a price table (shipped with the app, updated from the SaaS).
  - It shows the spend in the settings and on the tutor's card: "Ta mesec · This month: $4.20 of $20".
- **Near the daily budget:** the tutor goes brief: Haiku for everything, digests once a day.
- **At the monthly cap:** only chats the learner sends, after asking. Never in the middle of a role-play.
- **Covered:** the SaaS enforces the plan's budget (below).

---

## Model per task

| Task | Model | Effort | Why |
|---|---|---|---|
| The review digest, words, readings, `gloss_gaps` | Haiku 5.5 | low | Routine and stateless: a fraction of a cent a call |
| Grading `answer`, `session_end` | Haiku 5.5; Sonnet 5.5 if measured worse | low–medium | Grading Slovene is what to measure |
| Chat, "Why?" on an exercise, card fixes, morning and evening, the notebook, `level_up` | Sonnet 5.5 | medium | Judgement, tone, the Slovene |
| Role-play turns | Sonnet 5.5 (Haiku 5.5 to test: the fastest) | low | Latency; the line is voiced |
| Writing content, on request | Sonnet 5.5; Opus 5.5 for large pieces | medium–high | Quality. Through Batches when nobody waits (half price). |

**Prices** per million tokens (the [pricing page](https://platform.claude.com/docs/en/about-claude/pricing) and the
[models overview](https://platform.claude.com/docs/en/about-claude/models/overview), 2026-10-11):

| Model | API id | Input | 5-min write | 1-hour write | Cache read | Output |
|---|---|---|---|---|---|---|
| Opus 5.5 | `claude-opus-5-5` | $4 | $5 | $8 | $0.20 | $20 |
| Sonnet 5.5 | `claude-sonnet-5-5` | $2 | $2.50 | $4 | $0.10 | $10 |
| Haiku 5.5 (prompts up to 100k) | `claude-haiku-5-5` | $0.10 | $0.125 | $0.20 | $0.01 | $0.50 |
| Haiku 4.5 | `claude-haiku-4-5` | $1 | $1.25 | $2 | $0.10 | $5 |

- **Batches** are half price.
- **Haiku 5.5 over 100k-token prompts** costs five times as much ($0.50 / $2.50). The slim tutor's calls stay far
  below that.
- **Haiku 5.5 is new:** its retirement is "not sooner than October 7, 2027", so it launched around 2026-10-07. It has
  1M context and adaptive thinking, at a tenth of Haiku 4.5's price. It makes routine reactions almost free.
  - Whether its Slovene is good enough for grading, and maybe for chat, is the question that matters most for the
    price.
  - Haiku 4.5 is the fallback.
- **Caches are per model,** so each model in use writes its own prefix. That's small next to the thread.

### The estimate

**Assumptions,** from the measured average day (above):
- 51 calls and 4 cold starts a day;
- 30k output tokens a day.

The slim tutor's shape:
- a prefix of 12k tokens;
- a notebook of 3k;
- a thread of 20k on average (restarted at ~30k);
- each call adds about 2k new tokens.

| Setup | A day | A month |
|---|---|---|
| Today: Claude Code, Opus 5.5 (measured) | $4.39 | ~$130 |
| Today's tokens on Sonnet 5.5 (measured, re-priced) | $2.20 | ~$66 |
| Slim, all Sonnet 5.5 | ~$1.20 | ~$36 |
| **Slim: Sonnet 5.5 for chat, fixes and the day's turns (26 calls), Haiku 5.5 for routine (25 calls)** | **~$0.85** | **~$25** |
| The same, covered (the prefix shared by all learners) | ~$0.65 | ~$20 |
| Slim, all Haiku 5.5 (if its quality holds) | ~$0.07 | ~$2 |
| A quieter learner (15 calls, 2 cold starts, a thread of ~10k), Sonnet and Haiku | ~$0.30 | ~$9 |

**The all-Sonnet line, worked out:**

| Item | Cost |
|---|---|
| 4 cold starts × (12k at $4 + 20k at $2.50) | $0.39 |
| 51 calls × 2k new tokens at $2.50 | $0.26 |
| 51 calls × 30k read at $0.10 | $0.15 |
| 30k output at $10 | $0.31 |
| Two notebook rewrites | $0.07 |
| **Total** | **$1.18** |

The Haiku 5.5 half of the mixed line costs about $0.03 a day.

**Beyond the average day:**
- A heavy day like 2026-10-11 (126 calls) is about 2.5 times an average day.
- Writing content on Opus, on request, comes on top.
- Opus 5.5 for chat doubles the Sonnet part.

**So Jan's estimate holds** (4–8 times less, $0.30–1.00 a day, $10–30 a month on Sonnet 5.5): about 5 times less for
Jan's average day with Sonnet and Haiku 5.5. Jan sits in the top half of the range, a quieter learner in the bottom
half.

**The levers, in order:**
1. the rewrites after pauses (the thread's size times the pauses);
2. the model for chat;
3. the output (the thinking effort).

A Max learner's $100 of monthly credits covers even the all-Sonnet case.

*This is an estimate, to verify with the prototype.*

**What the prototype measures:**
- **Per call:** input, cache writes (5-minute and 1-hour, from `usage.cache_creation`), cache reads and output
  (thinking included); the model; the event kind; latency (first token and total).
- **Per day:**
  - calls and turns;
  - cold starts (the gaps between calls over 5 and over 60 minutes);
  - the thread's size at restart, and the notebook's size;
  - the spend by model and by event kind;
  - the guide topics fetched.
- **Quality, Haiku 5.5 against Sonnet 5.5 on the same events:**
  - grading agreement with the app's verdicts and with Jan's corrections in the answers log;
  - feedback in Slovene, checked by Jan's wife on a sample (spoken forms are labelled, never counted wrong);
  - for role-play, the turn's latency and staying in character.
- **The phone:** battery and data a day, and what the client adds to the APK.

---

## The covered variant

### How it works

- **The learner subscribes** (see Practicalities). The app's calls go to the SaaS proxy (e.g. `tutor.lani.page`) with
  the account's token, not to Anthropic. **Our key is never in the app:** an app's secrets can be extracted.
- **The app sends turns, never a raw prompt:** the messages (the learner's text, events, tool results, the earlier
  assistant turns) and the task (chat, reaction, role-play, …).
- **The proxy adds the rest:**
  - the system prompt and tool definitions for the app's version (one bundle per supported version, from the same files
    as BYOK's bundle);
  - the model and `max_tokens` per task and plan;
  - the cache breakpoints.

  It forwards the request to the Messages API and streams the answer back. Whatever the app sends as `system`, `tools`
  or `model` is replaced.
- **One code path in the app:** the same loop and the same requests. BYOK calls Anthropic directly; covered calls the
  proxy (another base URL and auth).
- **Metering:** each response's `usage`, per learner, per day and month, priced. The plan's monthly budget (fair use)
  is checked before each call (an estimate) and after (the actual).
- **At the budget:** a soft fall-back to a cheaper model (Haiku 5.5) instead of a hard stop. A hard stop only at a
  ceiling, against abuse.
- **Caching gets cheaper:** one workspace and the same prefix for everyone. The prefix is written once per model and
  version while traffic keeps it warm, not once per learner (about 25 % off the estimate above).
- **The proxy runs no tools and keeps no learner data.** The loop and the tools stay in the app.

### Abuse

- **Using the tutor as a free, general-purpose Claude.** Bounded by:
  - the per-learner budget;
  - the fixed server-side prompt and tools (no web search, no code execution);
  - `max_tokens` and rate limits per task;
  - light checks on the request's shape (a turn is a known event, or a chat of bounded length).
- **Prompt injection stays contained:** the tools still run only in the app.

### Pricing (the prototype's numbers decide)

| Per learner a month | Casual | Full |
|---|---|---|
| What it includes | Haiku 5.5 for reactions and most chat, a few Sonnet turns a day, no live voiced role-play | Sonnet chat, role-play, content on request, a fair-use cap |
| Claude | ~$1–4 | ~$8–15 typical, up to ~$25 at Jan's level (the cap) |
| Voice | Shared clips: about 0 | Plus live lines, 3–10k characters (plan 1): ~$1–2 |
| SaaS (proxy, glas, backup, relay), at 50–100 learners | ~$1–3 | ~$1–3 |
| Store fee and VAT | Play's commission on subscriptions; VAT 22 % in Slovenia, OSS across the EU | |
| A starting price | ~€5–6 | ~€15–20 |

A plan's cap falls back softly (to a cheaper model, digests once a day) instead of stopping.

### Practicalities

- **Payments.**
  - Through Play, digital subscriptions must use Play Billing. In the EEA, alternative billing is possible under
    Google's programme, with fees.
  - The GitHub build (self-update) can sell through Stripe or a merchant of record that handles EU VAT (Paddle and
    similar).
  - The SaaS checks the entitlement on the server.
- **VAT and EU consumer law:** EU VAT OSS, the right of withdrawal for subscriptions (plan 1).
- **Anthropic's [Commercial Terms](https://www.anthropic.com/legal/commercial-terms) and Usage Policy.**
  - In covered mode we are the API customer offering an end-user product: the Usage Policy covers what our users do.
  - Branding: "Lani, powered by Claude".
  - **Minors:** products that serve minors have extra requirements *(to verify in Anthropic's current guidance)*. A
    child learner (plan 2) on covered falls under them.
  - A child can't have a Console account of their own (the Console is for adults), so a child's hosted tutor runs on a
    parent's key, or covered.
- **Data handling:**
  - The proxy logs metadata only: the learner's id, the model, tokens, cost, latency, errors. No content. Content
    passes through memory and isn't stored.
  - Billing metadata is kept as long as accounting requires.
  - The privacy note states Anthropic's own retention of API data *(verify the current default)*, and Anthropic is a
    sub-processor (a DPA).
- **The SaaS's auth** (an open question): accounts, device tokens, the plan's entitlement.

---

## Data

- **The source of truth:** the node's data repository today; in the hosted mode, the phone.
- **The same formats in both modes:**
  - the six databases as JSON, exactly as `read-db.py` and `update-db.py` know them ([docs/DB_SCRIPTS.md](../DB_SCRIPTS.md)),
    with their schema versions and the per-language profiles;
  - the answers log;
  - the tutor's content, as the bridge's files under `data/app/`: packs, modules, grammar pages, `sentences.json`,
    `lexicon.json`, stories, readings, dialog variants, arrivals.

  New in the hosted mode: the game state (the app's already), the chat archive, the notebook, the spend ledger and the
  settings.

  Not data: voice clips (a cache from glas), curated content (a download), the API key.
- **Export and import between modes:**
  - **Export:** one file (a zip) with the data repository's layout and a manifest (the schema versions, the app's
    version, the date).
  - **On a node:** `lani import <file>` writes it into the data repository and commits.
  - **In the app:** it's unpacked and checked (the port of `validate-data.py`), and replaces the data after a local
    backup.
- **Moving from self-hosted to hosted:** `lani export` on the node, then import in the app. The node has no notebook
  (Claude Code's memory is the session), so the first hosted day writes one from `learner_state` and the mistakes
  database, in one turn.
- **One mode at a time:** switching modes moves the data. There is no live two-way sync between a node and a phone.
- **Backup through the SaaS:**
  - encrypted on the phone: a key in the Keystore and a recovery code the learner keeps (or a passphrase);
  - daily, and after a session ends; several versions kept;
  - restored on a new phone with the recovery code. We hold only ciphertext.
- **Several devices** (a phone and a tablet) need a real sync with conflicts: later. At first, one active device per
  learner.
- **Privacy:**
  - **BYOK:** the learner's text goes from the phone to Anthropic under the learner's own Console account. They are
    Anthropic's customer; we see none of it.
  - **Covered:** it passes through our proxy without being stored.
  - **Voice:** the shared namespace gets curated texts only; personal lines go to the learner's private namespace, with
    GDPR export and deletion (plan 3).
  - `docs/privacy.md` gets a hosted section.

---

## What the hosted mode leaves out at first

- **The heavy lifting of content authoring:** new scenes, villagers, culture-pack work. That's ours, in the culture
  packs (plan 2). The tutor's own content (modules, packs, stories, readings, dialog variants, arrivals) comes in
  phase C.
- **The tutor's edits to the repository:** CLAUDE.md, fixes to curated files, code. A wrong curated item becomes
  `report_problem`, and we fix it for everyone.
- **The terminal sessions** (`/lani-learn` and the others): the app has its own exercises.
- **What needs a server:**

| Feature | Needs the SaaS | What it does there | Phase |
|---|---|---|---|
| Curated clips | Yes (glas) | `resolve`: signed URLs; the offline packs | A |
| Live lines (role-play, the tutor's new lines) | Yes (glas `say`, the private namespace) | A quota per account. Plan 3's open question on ElevenLabs' OEM terms applies. | B |
| Speech checks (STT) | Optional | The phone's recognizer first; Whisper in the SaaS later | A / later |
| Backup and restore | Yes | Encrypted blobs | B |
| Reports of wrong items | Yes | An inbox for us | A |
| Towns: visits, friendship, trades, questions | Yes | A relay: each town's last public state (published by the phone), mailboxes, requests signed with the town's key, which the phone holds | D |
| The family page, partner challenges | Yes | The page on lani.team and a mailbox; recordings in glas's private namespace | D |
| Curated content, dictionaries | A download (GitHub releases or lani.page) | Versioned files | A |
| App updates | No | Play, or the GitHub build's self-update | A |
| Covered: the LLM proxy | Yes | A separate service | after A |

---

## Phases

| Phase | What | Exit criterion |
|---|---|---|
| 0 | **Prototype and measure.** The loop in a debug build on Jan's own key, in **shadow mode** on Jan's real events: the self-hosted tutor still answers; the in-app tutor's answers go to a debug panel. Both models side by side. The Java SDK spike. 2 weeks. | Numbers per event kind (see [The estimate](#the-estimate)). Quality compared. The estimate confirmed or replaced. |
| A | **Hosted BYOK alpha.** Key setup and caps; chat (streamed); reactions to session ends and reviews; grading answers; "Why?" on exercises; card fixes; words (glosses, sentence notes); the rhythm; the notebook and the day's threads; the spend shown; export and import with a node. Curated content as a download, clips from glas, the phone's STT. | Jan (a second profile) and 2–3 testers use it daily for 2 weeks. The cost per learner-day is measured. Export and import lose nothing. |
| B | **Role-play** (scenarios, villager talks, live lines from glas), the grammar book (`publish_grammar`), `level_up`, the encrypted backup. | A role-play turn answers in under ~3 s. A restore on a second phone works. |
| C | **Content writing:** the studio task (modules, packs, stories, readings, dialog variants, arrivals), Batches for what can wait, fixes to content. The validators ported or shared. | The conformance corpus is green in the bridge and the app. A tester's tutor-made module plays. |
| D | **Towns and family through the SaaS relay.** | Two hosted towns visit each other and trade. The family page works without a node. |
| Covered | **After phase A's numbers:** the proxy, the plans, billing. | A closed beta with the measured cost per learner under the plan's price. |

---

## What changes in plans 1 and 3

**Plan 1:**
- **Variant 2, "we host it",** is replaced. Its main work, "replacing the always-on terminal session with a multi-user
  agent service" (the tutor as a service, one agent run per message, per-tenant storage, the job queue for the
  rhythm), is no longer needed: the tutor runs in the app, and the rhythm runs on the phone. "We host" now means the
  SaaS: voices, backup, the relay, and in the covered variant the proxy.
- **Its Claude access options:**
  - 2a (our key) becomes the **covered** variant, still with the loop in the app.
  - 2b (the learner's key, encrypted on our servers) becomes **BYOK**, but the key stays on the phone: no key custody
    for us.
  - 2c (signing in with the subscription) stays parked.
- **Its cost table** (Sonnet-4-class prices) is replaced by this plan's.
- **Variant 1.5** (we host the heavy parts, the learner runs Claude Code at home) is dropped.
  - Its point, no GPU and no ElevenLabs account, is met for self-hosters by glas with a node key (plan 3).
  - Its limit, an always-on machine, is what the hosted mode removes.
- **Jan's decision of 2026-09-25,** "Hosted: we host everything, and the learner needs no token or key. This comes
  last", becomes the covered variant. BYOK comes first.
- **The first-launch choice:** "🔗 Poveži svojega učitelja · Connect my own tutor" (the QR code) stays.
  "☁️ Lani v oblaku · Lani cloud" becomes the tutor on the phone, with "my own key" or "a subscription".
- **Kept as it is:** Phase 0, Variant 1, the constraints, the Play build items.

**Plan 3:**
- **glas gains the hosted mode's clients: the app itself, with no node.**
  - Per-account app tokens: shared read, the private namespace, `say` with a character quota.
  - The app fetches signed URLs directly. That was a "later" in plan 3; now it's needed.
  - The production certificate: phones won't trust the dev cluster's mkcert root.
- **The private namespace is per account,** not per node.
- **`say` for the in-app tutor's lines** (role-play) keeps plan 3's open question about ElevenLabs' OEM terms. Under
  BYOK it has a new edge: the tutor runs on the learner's key, but the product and the voices are ours.
- **STT for hosted clients** (Whisper, which glas already uses for QA) is a new item: maybe `/v1/stt` in glas.
- **Auth:** Zitadel accounts for app users (plan 3 already names Zitadel for the hosted service), or simpler. Open.
- **The SaaS grows** beside glas: backup, the relay, the covered proxy. Separate services on the same platform. glas
  stays free of LLM calls.

---

## Open questions

1. **The Max credits and API keys.** Mostly answered (above). Still open: whether the credits' terms allow use through
   a third-party app such as Lani (the article says nothing either way), and how setup shows a learner which
   organization their key belongs to.
2. **The Java SDK on Android** (R8, APK size, streaming), or our own small client.
3. **The cost per learner in practice:** the prototype. Above all, the cold starts a day and the thread's size.
4. **Haiku 5.5's Slovene:** grading, feedback, role-play, against Sonnet 5.5. Jan's wife checks a sample.
5. **The SaaS's auth:** e-mail magic links, Google sign-in, Zitadel. Does BYOK need an account at all (voices,
   backup)? Do BYOK learners get the voices free, with a quota, or for a small fee?
6. **Play policies:**
   - an app where the user brings their own API key (they buy nothing from us);
   - the [AI-Generated Content policy](https://support.google.com/googleplay/android-developer/answer/14094294). It
     names text chatbots and makes the developer responsible for offensive output. *Check its in-app reporting
     requirement.*
   - the Data safety form (the key; chats sent to Anthropic);
   - Play Billing for covered.
7. **EU rules for the SaaS:**
   - GDPR for the accounts, the encrypted backups and the family's recordings;
   - a child's data (the age of consent in Slovenia is 15);
   - AI Act Article 50 (telling learners they talk to an AI; labelling synthetic audio);
   - Anthropic as a sub-processor in covered mode;
   - data residency: the pricing page lists `inference_geo` with `"us"` (×1.1) and global; check whether there is an
     EU option.
8. **Minors:** Anthropic's requirements for products that serve minors (covered), and a parent's key for a child
   (BYOK).
9. **Preserved thinking:** check that restarting the thread with re-sent text passes the history check on a new
   account.
10. **Shared validators:** a Kotlin port with a conformance corpus, or the bridge's TypeScript in a JS engine in the app.
11. **The notebook in the self-hosted mode too?**
12. **Can a hosted town and a self-hosted town visit each other** (the relay and the bridges federating)?
13. **The covered plans' prices,** from the prototype's numbers.
