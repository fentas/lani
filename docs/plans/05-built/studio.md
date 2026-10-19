# Plan 5, built: the studio, the in-app tutor writes content (phase C)

*Built 2026-10-19 (the overnight round, phase C: "the in-app tutor writes content"), branch `studio`. Code: the app's
`companion/android/app/src/main/java/si/lanisce/lani/tutor/live/` (`Studio`, `StudioWork`, `Batches`, `Discussions`,
and the live tutor's `LiveTutor`, `LiveTools`, `LiveBridge`), the UI `ui/tutor/StudioUi.kt` and `TutorSettings.kt`; the
bridge's `companion/bridge/src/tutor-studio.ts`, `src/overrides.ts` and `src/features/tutor.ts`; the bundle's
`companion/tutor/studio-prompt.md` and `app-tools/` (`request_content`, `studio_subtask`, `str_replace_based_edit_tool`,
`search`); the mock's studio (`companion/bridge/test/tutor-mock.ts`); QA's `studio` step.*

## What

- **The studio task.** Content is a job of its own: the studio. It writes on the content model (Opus 5.5 at the
  preset's effort, plan 5 "Model per task") with the bridge's own publishing tools, and they validate on write. A
  refusal comes back as the tool's error with the validator's message; the model fixes it and publishes again.
- **What asks for content:**
  - the chat: `request_content` hands the studio a brief ("make me exercises for X"), and the chat's tutor says it's on
    its way;
  - the events that Claude Code alone answered until now: `villager_arrived` (soon), `dialog_variants_heard` and
    `stories_low` (overnight), and `gloss_gaps` overnight through the batch API (the routine model).
- **Long jobs.** Each step is saved on the bridge (`app/tutor/jobs/<id>.json`): the brief, the thread, the sub-tasks
  with their own threads, the steps, the drafts (each publishing call's input and what came of it) and what was
  published. A killed app resumes from there. The job runs while the app is open, else as WorkManager work.
- **Batches.** What can wait goes through Anthropic's Message Batches API (50 % off), each step a batch of one; the
  results go through the same tools. On OpenAI it waits until the phone charges on Wi-Fi.
- **The file tools** over the learner's data folder: Anthropic's text editor (`text_editor_20250728`, the tool
  `str_replace_based_edit_tool`) and `search`, carried out by the bridge. Every write of a file the app reads runs that
  kind's validator first. On OpenAI the same tools are function tools.
- **Local overrides** of curated content: a fixed module, scene, story or villager applies at once under its curated
  id, and retires when the curated file changes upstream (its hash).
- **Phase A's leftovers:**
  - reports as GitHub discussions (looked up first, a vote or a new one, the learner asked first);
  - a monthly cap next to the daily one;
  - the notebook's versions in "What my tutor knows";
  - the per-turn effort on a GPT-6 thread (`configuration_update`: built in phase A, now tested in the live tutor);
  - the ChatGPT plan's welcome at the first sign-in (OpenAI's UI guidelines).

## The studio in the app

`tutor/live/Studio.kt`. The live tutor owns one (`LiveTutor.studio`); it runs on the live tutor's thread.

**A job** (`StudioJob`, schema `lani.studio-job/v1`):

| Field | What |
|---|---|
| `id`, `title`, `brief` | `j_<12 hex>`; the strip's name; what to write (a request's brief, or the event as the channel had it) |
| `origin`, `event_kind`, `event_ids` | `request` or `event`; the inbox events it answers, acknowledged when it ends |
| `urgency` | `now` (the learner waits), `soon` (an arrival), `whenever` (the night's) |
| `status` | `queued`, `running`, `batch` (a batch waits), `done`, `failed`, `cancelled` |
| `task`, `model`, `effort`, `batched` | CONTENT (ROUTINE for the dictionary's gaps), the preset's model and effort; calls through the Message Batches API |
| `prefix` | the studio prefix its thread was made under: an app update that changes it restarts the thread as text, with what was done |
| `messages`, `subtasks` | the thread in the provider's format; each sub-task with its own |
| `steps`, `drafts`, `published` | the last 120 steps; the drafts by `<tool>:<item>`; what was published |
| `calls`, `cost`, `attempts`, `not_before` | the main thread's calls (a sub-task counts its own), the spend, the runs, a retry's time |

**A run** is plan 5's loop (`TutorLoop`) on the studio's prefix: its own prompt (`studio-prompt.md`) and tools.
- Every step is saved through `PUT /tutor/jobs/<id>`, after the model's answer and after the tools' results.
- A resumed run starts with the tool calls still open (a publish that already went through is answered by the
  bridge's once-per-client-id, so nothing is published twice).
- At most 24 calls on the main thread and 12 on a sub-task.
- The spend's caps are checked before each call.
- The first message carries the notebook, the local time and the job (`<studio_job …>`).
- Jobs run one at a time, the learner's own first. At most five are open at once: a sixth request is refused, and the
  chat's tutor says so.

**How a run ends:**
- `done`: the model ended, or used its calls;
- `failed`: a refusal, a cut-off twice, an error the loop doesn't retry. A request's failure is told in the chat;
- `queued` again: the cap spent, a retry's time (429, 5xx, network), the key refused or the credit out (the live
  tutor's pause and notes, as for a turn);
- `batch`: a call sent as a batch;
- `cancelled`: the learner's ✕, or the channel handed back to Claude Code (a job for an event: its events go back to
  Claude Code; a request waits instead).

**Sub-tasks.** `studio_subtask {title, brief}` runs a part of the job on a thread of its own: its first message is
only the brief. It reads and publishes with the same tools, and its last text goes back as the tool's result. A
resumed job finds a sub-task by its call's id and goes on. At most six a job; a sub-task can't start another.

**The tools** (`StudioSpecs.STUDIO`, in this order):
- `reply`, `guide`;
- the reads: `learner_state`, `get_village`, `dictionary_lookup`, `recent_answers`, `list_*`, `get_*`, `get_course`;
- the publishing tools and their `remove_*`, `rollback_module`, `report_problem`;
- `notebook_note`;
- `str_replace_based_edit_tool`, `search`, `studio_subtask`.

The bridge's tools run through `POST /tutor/tools/:name` with the call's id as `client_id`, as the chat's do.
`studio_subtask` is the app's own. On the Messages API the text editor is declared by its type (`{"type":
"text_editor_20250728", "name": "str_replace_based_edit_tool", "max_characters": 16000}`, no schema); on OpenAI it is
a function tool from its spec (`Prefix.alternates`).

**The chat's tools** are phase A's 17 and `request_content` (`StudioSpecs.CHAT`). The core prompt's rule 12 says new
content is the studio's: a brief with `request_content`, and a line that it's on its way.

**When it runs:**

| Job | In the app | In the background (`StudioWork`) |
|---|---|---|
| A request (urgent) | at once | one-time work with the network, at once |
| An arrival | at once | the same |
| The night's, on Anthropic | its steps sent as batches; the open app asks them every 30 s | a poll in 5 minutes with the network, again while a batch waits |
| The night's, on OpenAI (or batches off) | while the phone charges on Wi-Fi | one-time work with `requiresCharging` and an unmetered network |

- A background run takes at most eight minutes; what's saved goes on at the next.
- No expedited work and no foreground service: nothing here is a call the learner sits and waits for (the open app runs
  those itself).

**The spend.** Each call is a `CallRecord` in the day's log (thread `studio`, or `studio-batch` at half the price),
so today's and this month's spend count it, and the caps stop it. A job's end leaves a turn in the day's log and a line
in the day's thread ("16:40 studio (claude-opus-5-5): «Vaje: rodilnik» done: published module mock-studio-drill"), so
the chat's tutor knows what the studio made.

## Batches

`tutor/live/Batches.kt` (`AnthropicBatches`): `POST /v1/messages/batches`, `GET …/{id}`, the results' JSONL from its
`results_url`, `…/cancel`.
- The same key guard as the transport: the key goes only to the API (or a debug build's mock), the results URL too.
- The params are the Messages API body as the studio builds it, without `stream` and the tools'
  `eager_input_streaming`.
- The betas go with the batch, and the effort's when a request has an effort message.

A batched job's provider (`Studio.Batching`):
- **A call is sent as a batch of one** (custom id `<job>-<slot>-<n>`), and the run stops: status `batch`.
- **The poll** asks the batch; once it ended, the result's message is kept in the job and the job runs again. The
  same call gets it as its step, and its tool calls run.
- **An expired or errored request:** a server's error is sent again (the loop's retry); a request the API refused is
  the loop's error.
- **The cost:** the record is priced at half (`studio-batch`).

Prompt caching applies to batches too (best effort).

## The bridge: the studio's side

`companion/bridge/src/tutor-studio.ts`, wired into `features/tutor.ts`. It runs on a node and in the phone's bundle
alike: only `node:fs`, `node:path` and `node:crypto` (the phone's shims).

**The file tools** (app-session tools: listed in `GET /tutor/session`'s `app_tools`, never in Claude Code's list;
lease-guarded: `view` and `search` read, the rest write):
- **Paths** are relative to the learner's data folder; `/` is its root; the folder's own absolute path is stripped.
  Refused: NUL, backslashes, `%`, `..`, `~`, a link anywhere on the path, a path that resolves outside.
- **Private** (never read or written): `.git/`, `*.pem`, `*.key`, `keys.env`, `lani.env`, `.env*`, the bridge and
  family tokens, pairing, invitations, voices, releases, APKs, the runtime files (`client-ids.json`, `devices*.json`,
  `events.json`, `channel-queue.json`), `answers.json*`, `app/dev/`, `app/chat-images/`, `app/audio/`, `app/family/`,
  `app/lease.json`, `app/tutor-inbox.json`, `app/tutor/jobs/`, `.backups/`, `*.tmp`.
- **`view`:** a file as numbered lines (`view_range` `[a, b]`, `b = -1` to the end); a folder two levels deep; at most
  16,000 characters.
- **Writes** go only to the tutor's content areas, each file checked as its publish tool checks it (the references, the
  village's language, the curated-id rules, the file named for its id):
  - `app/modules/<id>/v<N>.json` and `current.json`;
  - `app/packs/`, `scenes/`, `stories/`, `readings/`, `villagers/`, `arrivals/`, `grammar/`, `cards/`, `scenarios/`
    (`<id>.json`);
  - `app/course/<unit>/<id>.json` (homework).
- **Refusals** (`is_error`, nothing written):
  - "Nothing written: <path> doesn't validate as a <kind>: <the validator's errors>";
  - the six databases: "read-only here (record_session and card write them)";
  - anywhere else: "not a file the tutor writes", with the content areas named.
- **After a write:** the old text is kept in `app/tutor/file-history/` (the last five), the publish tool's event goes to
  the app (it reloads), and the overrides are checked.
- **`search {pattern, path?, regex?, max_results?}`:** `path:line: text` over the readable text files.

**The routes** (the app's token):

| Route | What |
|---|---|
| `POST /tutor/check {kind?, path?, text}` | `{ok, kind, id?, errors?, notes?}`: a write's check, nothing written (it passes the lease) |
| `GET /tutor/jobs` | the jobs without their threads, newest first; finished ones pruned after 7 days |
| `GET`, `PUT`, `DELETE /tutor/jobs/:id` | a job as saved (at most 4 MB, schema and id checked); kept out of git |
| `GET /tutor/notebook/versions`, `POST /tutor/notebook/restore {at}` | the notebook's last 20 texts; one written back (the one before kept) |
| `GET`, `POST /tutor/reports` | `app/reports.json`: each report's title, kind, item, URL, number, state (`posted`, `upvoted`, `kept`, `declined`, `resolved`), via (`discussion`, `wishlist`) |
| `GET /tutor/overrides` | the overrides' registry |

## Local overrides of curated content

`companion/bridge/src/overrides.ts`. The stores already prefer the tutor's item under a curated id: a module's versions
over the curated v1, a scene, story or villager over the curated one. The fix applies at once. What's new is the way
back:
- **The registry,** `app/overrides.json`, keeps each such item's curated file and its sha256 when the override was
  first seen.
- **The check** runs at the bridge's start, every 15 minutes, after a publish, remove or rollback through the app
  tutor's tool route, and after a file-tool write. A Claude Code publish is seen by the next check.
- **When the curated file changes** (a git pull and a restart on a node, an app update on the phone), the override
  retires:
  - the tutor's file(s) move to `app/overrides/retired/<kind>/<id>-<stamp>`;
  - the curated item plays again (the app is told);
  - the report that named the item is marked resolved;
  - the channel hears `override_retired`, with the report's link: the tutor tells the learner "the fix is in".
- **A record whose item is gone** (`remove_scene`) is dropped.

Curated packs, readings and scenarios can't be overridden (their stores refuse curated ids). The fix there is a card
fix (`card`) and `report_problem`, as before. The studio prompt and the `fix` guide topic say so.

## Reports as GitHub discussions

`tutor/live/Discussions.kt`: GitHub's GraphQL as the learner, with git sync's GitHub sign-in (`si.lanisce.lani.sync`,
its `GIT_TOKEN`).
- `find(title)`: a `search(type: DISCUSSION)` in `fentas/lani`, the title matched exactly;
- `upvote(d)`: `addUpvote`;
- `create(draft)`: `createDiscussion` in "Content fixes" (a fix) or "Ideas" (a wish). The body is the category's form:
  the item's source, what is wrong, the suggested fix, "native check" for machine-written Slovene, and "Reported from
  the Lani app's tutor." Nothing personal.
- The title, the body and the search go in `variables`. The token goes only to `api.github.com` and follows no
  redirect. GitHub's rate limits are kept as git sync keeps them.

**The flow** (`LiveTutor.report`, for the chat's `report_problem` and the studio's):
1. A sign-in is kept: the discussions are looked up. The report waits as a `PendingReport`, and the tool's answer says
   what will happen ("an existing discussion has it: their vote goes there", or "a new one in Content fixes").
2. The chat shows "📣 Pošljem v Lanijeve razprave? · Send to Lani's discussions?" with the title, what's wrong, the
   fix, the discussion found, and "the discussions are public: only what you see here is sent".
3. "Send": the vote or the new discussion. The link goes to `app/reports.json` (`POST /tutor/reports`), and a note in
   the chat says it was sent.
4. "No, keep it here", or a failure (no scope for the repository, offline): the bridge's `report_problem` (the
   wishlist), noted in the chat.

Without a sign-in, `report_problem` keeps the wishlist, as before. The check that a word's fix comes after a
`dictionary_lookup` stays.

## The caps, the notebook's versions, OpenAI

- **The monthly cap** (`LiveSettings.monthlyCap`, $20 by default; chips $5–100), next to the daily one:
  - at the cap only the learner's own chats and answers run, and only once they said so: "Vseeno odgovarjaj na moja
    sporočila ta mesec · Answer my messages anyway this month" in the chat or the Tutor section
    (`monthAllowed`: the month);
  - a role-play's line goes on (plan 5: "never in the middle of a role-play");
  - routine turns and studio jobs wait, and the state is `PAUSED_MONTH`;
  - the chat is told once a day (`tutorLive.note.month`).
- **The notebook's versions:** "📓 Kaj ve učitelj" → "🕘 Starejše različice · Older versions" lists the bridge's last
  20 texts by when and why. A tap shows one; "↩️ Obnovi to različico · Restore this version" writes it back (`restored
  (<at>)`), the text before kept as a version.
- **GPT-6's per-turn effort:** OpenAI's reasoning guide ("Change reasoning mid-conversation", checked 2026-10-19):
  `{"type": "configuration_update", "reasoning": {"effort": …}}` before the next user message, "supported by the GPT-6
  model family in standard, single-agent mode"; the request's own reasoning setting stays, so the cache holds. The
  provider layer built it (`OpenAIFormat.update`) and the live tutor's day thread already passed the turn's level
  (`DayThreads.onThread`); a test now checks it end to end (a GPT-6 Astra thread at medium, a grading turn at high: the
  update before its user message, `reasoning.effort` medium at the top).
- **ChatGPT's welcome** (OpenAI's UI guidelines, checked 2026-10-19): "Show a welcome modal only the first time a user
  signs in to your app with ChatGPT plan usage enabled". So once, after the first sign-in with the plan: "Uporabljaš
  svoj paket ChatGPT · You're using your ChatGPT plan", the requests use the plan and usage is managed in ChatGPT's
  settings, "Razumem · Got it" and "Manage usage" (`chatgptWelcomed`). The guidelines' "Manage usage" near the composer
  while the plan pays is now beside the chat's AI line.

## Turning it on

The studio comes with the tutor in the app (tutor-live.md, "Turning it on") on a bridge of this round. An older bridge
has no file tools in its session: content events stay for Claude Code there, and `request_content` says the bridge
needs an update.

In "🧑‍🏫 Učitelj · Tutor":
- the monthly cap;
- "🌙 Kar lahko počaka, ponoči za pol cene · What can wait, overnight at half price" (on by default);
- "✍️ Studio": the jobs, the open ones and the last five, each with its line and a ✕.

In the chat, each open job is a card: "✍️ Pišem … · Writing …", its title, its step and what it published (or "in a
batch at half price", "waits for the night", "waits: the spending cap"), and "✕ Prekliči · Cancel".

## Tests

- **The app's JVM tests** (the tutor's 192, all passing):
  - `StudioTest` (10), over the real Messages API client against a scripted API and a played bridge with the studio's
    routes. The cases:
    - a chat's request_content becomes a job on Opus at medium, with the text editor by its type and the studio
      prompt; the publish is refused with the validator's message, fixed and published; the reply; the job saved on
      the bridge at each step; the calls priced in the day's log;
    - the file tool refused, then accepted;
    - a killed process's job resumed by a new one from the bridge's copy, nothing published twice;
    - a sub-task on its own thread;
    - a job for the night through the Message Batches API: every step a batch, no stream, no
      `eager_input_streaming`, half the price;
    - a ✕;
    - an arrival's job, acknowledged when it ends;
    - the monthly cap and "answer anyway";
    - the notebook's versions and a restore;
    - a report: looked up, nothing posted before the learner's yes, then posted and kept.
  - `BatchesTest` (8) and `DiscussionsTest` (14) against fakes on MockWebServer (ports 19620–19626).
  - `LiveTutorTest` +1: the GPT-6 thread's per-turn effort.
- **The bridge's smoke:** the `tutor-studio` section (49 checks):
  - the routes, both tools, path escapes (`..`, an absolute path outside, a link, `%2e`), private reads, a database
    write refused;
  - a module refused by the validator, then written; `str_replace`, `insert`, view ranges, search; `/tutor/check`;
  - jobs and their pruning; the notebook's versions and restore; reports;
  - an override recorded, then retired after its curated file changed;
  - the lease refusing a write.

  Also the phone section (the bundle builds, 997.7 KB minified) and `tutor-export --check`.
- **The mock's self-check** (`test/tutor-mock-check.ts`, 63): the studio's markers and the batches.

## QA on the emulator

`companion/bin/qa --no-town --steps studio` (inside `flock /tmp/lani-emulator.lock`), with the dev bridge and the mock.
The mock's studio calls wait `LANI_TUTOR_MOCK_STUDIO_MS` (2,500 ms) so the job's card can be seen.
1. A chat "Vaje za rodilnik prosim [mock:request]": the chat's tutor says it's on its way, the card "✍️ Pišem …"
   appears, the job publishes a module the validator refuses, then a valid one. Then its reply, the banner, the module
   on the dev bridge (`GET /modules`), the job done there on `claude-opus-5-5`, the text editor declared by its type.
2. "Zgodbo prosim [mock:request:file]": the story through the text editor, refused by the story validator, then
   written (`GET /stories`).
3. "Vaje znova prosim [mock:request:slow]": once the job saved two calls on the dev bridge, the app is killed (`am
   force-stop`) and started again. The job goes on from the bridge's copy and finishes: two attempts, its reply in the
   chat.
4. The Tutor section's studio list; then Claude Code on the node again.

The screenshots are `studio-*.png` in the run's folder (and in `LANI_QA_STUDIO_SHOTS`), with
`studio-mock-requests.json` and `studio-logcat.txt` (the key in no line).

## What's left

- **The real APIs.** Nothing here called a real model or GitHub: the batch API's real timing and its caching in a
  batch, GitHub's search for discussions (its index lags a little behind a new discussion) and the token's scopes (an
  OAuth app token with `repo`; a fine-grained token only for the data repository can't post to `fentas/lani`: the
  report then goes to the wishlist, told in the chat). Discussions must be turned on in `fentas/lani` with the
  categories "Content fixes" and "Ideas" (plan 5, "Can start now").
- **OpenAI's batch API** isn't used: it needs a JSONL file uploaded through the Files API first, and the ChatGPT plan's
  route has none. On OpenAI the night's jobs wait for charging on Wi-Fi at the full price.
- **Overrides of curated packs, readings and scenarios:** their stores refuse curated ids. A pack word's fix is a card
  fix and a report.
- **The QA step plays the studio on Anthropic only.** The OpenAI path (the text editor as a function tool, the night's
  job while charging) is covered by the JVM tests' format checks, not by a mock of the Responses API's studio.
- **Claude Code's publishes** are seen by the overrides' next check (within 15 minutes, or at the next start), not at
  once: the MCP tool path isn't hooked.
- **A sub-task in a batched job** waits for its own batches; two side by side would send two (the prompt says not to).
- **The hosted mode's own QA** (the studio on the phone's bridge in-process) is the hosted round's: the same routes
  are in the bundle, and the JVM tests run the studio against the played routes.
