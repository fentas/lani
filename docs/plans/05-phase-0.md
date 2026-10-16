# Plan 5, phase 0: the checks

*Started 2026-10-14 (Jan: "phase 0: your validations, phase *: implement all"). [Plan 5](05-in-app-tutor.md)'s
phase 0: prototype and measure, and check what the plan marks "to verify". The results land here, and the plan
changes where they say so.*

**Exit:** numbers per event kind; the quality compared (Haiku against Sonnet, the in-app tutor against today's);
the estimate confirmed or replaced; the validators' engine chosen; the SDK or the plain client chosen.

## What Jan provides

- **A Console API key** in a workspace of its own ("Lani") with a monthly spend limit (about $60 covers phase 0).
  - On the machine: `ANTHROPIC_API_KEY` in `~/.config/lani/keys.env`, for the checks run there. Never in a chat,
    a log or a commit.
  - On the phone: entered in the app's developer setting for the shadow tutor (the Keystore, as the plan's "The API
    key").
  - On a Max plan, a key in the organization linked to the plan draws from its monthly credits.
- **Two weeks of using the app as usual** with the shadow tutor on.
- **The native check:** a sample of the shadow tutor's Slovene feedback, and the filler-name voice sample.

## The checks

| # | Check | Needs | Who | Status |
|---|---|---|---|---|
| 1 | **The API, from the docs:** the web fetch and web search tools (`allowed_domains`, which models, the price); compaction on demand; preserved thinking on a restarted thread; the tier-1 rate limits; the low-credit error; the Console's workspace limits; the model ids and prices the plan uses | — | Claude | done |
| 2 | **Android, from the docs:** Jetpack's JavaScriptEngine (requirements, WebView versions); `EncryptedSharedPreferences`; foreground-service limits on Android 16; the Java SDK on Android (Jackson, R8, size) | — | Claude | done |
| 3 | **ElevenLabs:** `/with-timestamps` and request stitching (`previous_text`, `next_text`) on v4 Turbo | a few characters | Claude | done: both work |
| 4 | **The validators in a JS engine:** the bridge's validators bundled; QuickJS and JavaScriptEngine in the app; the size, the start, a run per file; the same verdicts and messages as the bridge on the conformance corpus (every curated file and broken variants) | the emulator | Claude | done: QuickJS |
| 5 | **The filler-name voice sample:** lines with the name (calls, closings, thanks, mid-phrase) in three voices, voiced with a filler and the name put in, next to today's whole takes | ~2,000 ElevenLabs characters | Claude makes it; Jan and the native check listen | on Jan's phone: to listen |
| 6 | **The prototype:** the loop in the app behind a developer setting, in shadow mode on Jan's real events (reads through the paired bridge, writes dry-run, replies in a debug panel next to the real tutor's); usage, cost and latency per call, exported | — to build; the key to run | Claude | built: waits for the key |
| 7 | **Two weeks of shadow mode:** Balanced, and Haiku against Sonnet on the same events | 6, the key | Jan uses the app | waits |
| 8 | **Quality:** grading agreement with the app's verdicts and Jan's corrections; the Slovene feedback on a sample | 7 | Claude measures; the native check reads | waits |
| 9 | **Memory:** the notebook and the day's thread against today's long session, the same days side by side | 7 | Claude; Jan judges | waits |
| 10 | **The Java SDK against the plain client:** R8, the APK's size, streaming on Android 8+ | R8 merged | Claude | waits |
| 11 | **The request to Anthropic** for signing in with the subscription | 1 (where to ask) | Claude drafts; Jan sends | drafted: Jan's call |

## Results

### Checks 1 and 2: the docs (2026-10-14)

From platform.claude.com, code.claude.com, anthropic.com and developer.android.com; the plan is updated where they
differ ([plan 5](05-in-app-tutor.md)). In short:

- **Confirmed:** every model id and price; compaction on demand (`compact-2026-09-04`) and context editing;
  `eager_input_streaming`; workspace spend limits; a restarted thread with re-sent text passes the thinking check.
- **Changed in the plan:**
  - the rate-limit tiers (Evaluation, then Start: 1,000 RPM, 2M ITPM, 400k OTPM);
  - the error table (the low-credit message, the workspace cap's 400, the tier's spend cap as a 429 without
    `retry-after`, an expired key's 401);
  - setup: a "Lani" workspace (the Default can't have a limit), key expiry "Never";
  - Haiku 4.5's cache minimum (4,096) and its weakness as a fallback (legacy, manual thinking);
  - the thread's rules: never edit earlier messages, keep empty thinking blocks, a thread never changes its model,
    restart on a tools change or a new key, the effort fixed per thread;
  - `dictionary_lookup` stays ours, app-side: the API's web fetch can't fetch URLs the model builds, and opens an
    exfiltration path;
  - no memory tool for the notebook (it forces a call on every task);
  - Android 16's job quota; the JavaScriptEngine's limits (one sandbox, a 1 MB input limit without
    `provideNamedData`, Guava ~2.9 MB before R8, WebView 104+);
  - the Java SDK's core jar is 39 MB (Jackson, kotlin-reflect): the plain client is favoured;
  - the Usage Policy's AI disclosure for consumer chatbots.

### Check 4: the validators in a JS engine (2026-10-14)

**The bridge's validators run unchanged in both engines and give the bridge's verdicts and messages on all 1,702
cases** of the conformance corpus (every curated file, the docs' examples, and broken variants per kind: fields
dropped, wrong types, unknown references, bad `{learner}` and `{m:…|f:…}` placeholders, invalid JSON). The only
difference is the JSON parser's own wording for invalid JSON, compared on the verdict only.

- **The bundle:** 315 KB minified (95 KB gzipped), 329 KB with the woven-words dictionary check. Nothing in the
  validators changed: the build swaps in an in-memory `node:fs` the app fills, a small `node:path`, and zod's
  English messages only. The dictionary (`sl.json`, 1.8 MB) is handed in at run time.
- **The numbers** (the x86_64 emulator on a desktop CPU; an arm64 phone is likely 2–4× slower for QuickJS, an
  estimate):

| | QuickJS (quickjs-kt 1.0.15) | JavaScriptEngine 1.1.1 |
|---|---|---|
| Matches the bridge | 1,702 / 1,702 | 1,702 / 1,702 |
| Start; loading the bundle | 6 ms; 73–88 ms | 76 ms per app, 3–16 ms per isolate; 93–105 ms |
| Per file (416 curated) | median 1.9 ms, p95 13.6, max 39 | median 2.1 ms, p95 8.4, max 21.5 |
| The biggest scene (100 KB) | 19.9 ms | 7.6 ms |
| A whole culture pack | median 333 ms | median 68 ms |
| Memory, one village | 7 MB, 42 MB with the dictionary | a second process: 42 MB, 84 MB after a pack check |
| A release APK with R8, arm64 | +448 KB | +53 KB |

- **Chosen: QuickJS through quickjs-kt.** In the app's process, no WebView (so a build without Google's WebView
  works), the same engine in the JVM unit tests (the corpus runs there too), big texts handed over by a function
  call, memory limits and interrupts, ~450 KB. Fast enough: the slowest file takes tens of milliseconds against a
  tool call's seconds. JavaScriptEngine is faster on heavy files and smaller, but depends on the WebView's version,
  costs a second process of 40–80 MB, allows one sandbox per app, is asynchronous and can't run in JVM tests.
  Fallback binding: Cash App's Zipline (2.4 MB per processor type).
- **How the app calls it:** one runtime on its own thread, made on first use; the file tools call `check(kind,
  text)` on each write of a known kind and turn a failure into `is_error`; the village's references set once and
  again when content changes; the culture files and the dictionary handed over only when a check needs them.
- **A catch:** the culture pack check reads whole directories, and the dictionary and the curated scenes from outside
  the pack, skipping them quietly when they're missing. The app must hand it all three, or its verdicts differ
  without an error.
- **On main:** `companion/bridge/src/validate-entry.ts` (and `-lexicon`), `companion/bridge/validators/` (the build,
  the shims), `companion/bridge/test/conformance/` (the corpus: `build-corpus.ts`, `run-bundle.ts`; the output is
  generated), and the standalone spike `companion/android/spike-validators/` with its raw results. The corpus found
  one real mistake: SCENES.md's example scene named a word its pack doesn't have (fixed).

### Check 6: the prototype, built (2026-10-14)

**Built, in shadow mode: it watches, it never acts.** A developer setting, off by default, in the release app too:
the connection sheet's version line tapped seven times shows "🛠️ Razvijalec · Developer" with "🕵️ Učitelj v senci ·
Shadow tutor". How it works and how to use it: [companion/README.md, "The in-app tutor in shadow mode"](../../companion/README.md#the-in-app-tutor-in-shadow-mode-developer-setting).

- **What it sees:** every write the outbox delivered to the node (chat, answer, session_end, review_done, words_added,
  words_learned, reading_done, dialog_words, level_up; role-play recorded only) and the real tutor's replies, matched
  to the event of their conversation.
- **What it does with them:** plan 5's loop in the app (`companion/android/.../tutor/`): a chat and an answer at once,
  the rest in the day's digest (after 2 h away, the evening, the chat opened; no call when nothing is worth saying), a
  persisted queue, the manual tool loop (each step saved, at most 8 calls, parallel calls, `tool_choice: auto`,
  adaptive thinking with the effort set and fixed per thread, thinking blocks back unchanged), the Balanced preset per
  task with each model changeable, the prefix (phase A's 17 tools in a fixed order and the core prompt, a 1-hour
  breakpoint, automatic caching after it), the guide on demand, the notebook and the day's thread with our compaction.
- **Where nothing goes:** reads only through the paired bridge's GET routes (its client refuses anything else); writes
  as dry runs (validated against their schema, recorded with their input, never sent); `reply` only into the panel; no
  chat message, no notification, nothing in the learner's data (the shadow's own files are in `files/shadow-tutor`).
- **The client: plain** (OkHttp, okhttp-sse, kotlinx.serialization; check 2: the Java SDK's 39 MB core jar), the
  errors by the plan's table and check 1's (the low credit balance, the workspace's and the tier's spend limits, 429
  after `retry-after`, 529 and 5xx with backoff, `max_tokens` once more, `refusal` never); the beta
  `thinking-binding-controls-2026-08-01` sent and `input_transformations` logged per call (an append-only thread should
  have none).
- **The key:** checked with `GET /v1/models`, sealed with an AES-GCM key in the Android Keystore, only ever in
  `x-api-key` to the Messages API; never in a log, the export, a prompt or a tool's result.
- **Measured per call and per day,** as the plan's "What the prototype measures": the tokens by kind with both cache
  lifetimes, latency to the first token and in all, the price, cold starts over 5 and 60 minutes, the thread's size at
  each restart, the notebook's size, spend by model and event kind, the guide topics fetched; a daily budget ($3)
  pauses it.
- **Compared:** the panel shows each event with the shadow's reply and tool calls next to the real tutor's; "Also run
  on…" runs the same event on another model (its own stateless call, metered), so Haiku and Sonnet meet the same
  events.
- **Exported:** one JSON file (`lani.shadow-export/v0`) through the share sheet: the events, turns, replies, tool
  calls, calls and numbers of every day, the notebook and its versions, the thread; no key, no token.
- **One source:** the bridge's 47 tool definitions and its channel guide (28 topics) exported by
  `companion/bridge/src/cli/tutor-export.ts` into `companion/tutor/`, `--check` in the smoke test; the app's seven new
  tools hand-written beside them.
- **Tests** (JVM): the stream's parsing (text, thinking and its signature, tool input in pieces, usage), the loop with a
  fake transport (tool calls, the call limit, errors and retries, `max_tokens`, refusal, the budget, resuming), the
  dry runs, the guards (the bridge client refuses anything but GET; the key only in `x-api-key` to the API, never in a
  log line or the export), the queue's resume after the app is killed mid-turn, the thread's restart and the day's
  end, an app update's restart, pricing and the presets, the events' mapping; end to end over the real client against
  a scripted API.

**Run it without a key:** `bun companion/bridge/test/tutor-mock.ts` (a canned Messages API on 127.0.0.1:8771); a debug
or qa build's panel takes its address ("The mock (10.0.2.2:8771)"); `companion/bin/qa --steps shadow-tutor` does all of
it on the emulator. **QA, 2026-10-14, on the qa build (R8, `--minified`), Jan's data copied by the dev bridge:** the
developer section by seven taps, a fake key checked and kept, shadow mode on; a chat answered by the real (dev) tutor in
the chat and by the shadow only in the panel (Sonnet, 3 calls: the prefix written for an hour, then read); a review of
five form cards (all right: its digest made no call); "Also run on" Haiku for it (3 calls); the numbers ($0.048 at the
mock's token counts), the export through the share sheet (44 KB, no key), the notebook and the thread; the mock saw the
key only in `x-api-key`, the 1-hour breakpoint and phase A's tool order; no log line with the key; no crash.

**When the key arrives (check 7):** paste it in the panel (a "Lani" workspace with a spend limit, expiry "Never"),
leave Balanced, use the app as usual; export after a few days and after two weeks. What to look at first: the cold
starts and the thread's size at restarts (the estimate's levers), whether `input_transformations` ever shows, the
first token's latency for a chat, and "Also run on" Haiku for the chats and answers Sonnet had.

### Checks 7–9: the first days of shadow mode (2026-10-15 to 16)

- **It runs:** 54 calls by the morning of the 16th, no errors or refusals; $0.34 on the 15th (Sonnet $0.31 for chats, Haiku
  $0.02 for digests), the day's closing (the notebook) $0.08; first token in ~2 s, a whole reply in ~2.9 s (medians).
- **Since 0.2.220 the app sends its data to the node itself** (`app/dev/`, companion/README.md "Developer data on the
  node"), with the raw `usage` of sampled calls.
- **The 1-hour cache:** every cache write so far is filed as 5-minute although the system block asks `ttl: "1h"`;
  whether the API reports the split is to be read from the first raw `usage` (open).
- **First finding: the digest is too quiet.** On the 16th Opus answered 9 new words from a scene with the nouns lined up
  (1 · 2 · 3 · 5: en les, dva lesa, tri lesi, pet lesov, as the dictionary has them) and a 20/20 review with praise; the
  shadow's digest found "nothing worth saying" (no wrong answer, no milestone) both times. Jan noticed and valued Opus's
  messages. So:
  - **for the comparison,** "React at once" on, so the shadow reacts to the same events as Opus;
  - **for the in-app tutor,** "worth saying" includes new nouns (a line-up: better made by the app from the dictionary,
    as the counting card's forms are, exact and without a call) and a notable review (all right, 20 or more).

### Check 11: signing in with the subscription

- The Agent SDK overview: *"Unless previously approved, Anthropic does not allow third party developers to offer
  claude.ai login…"*. The legal and compliance page (2026-10-05) has no exception: *"Anthropic does not permit
  third-party developers to offer Claude.ai login into their own applications, or to route requests through Free,
  Pro, or Max plan credentials on behalf of their users."* The Usage Policy from 2026-11-12 bans *"services that route
  requests through consumer subscriptions"*.
- **No approval route is published.** The closest is the legal page's "contact sales" (claude.com/contact-sales);
  Console support as a fallback. Expect a no.
- **The draft,** for Jan to send if Jan wants to (placeholders for the name and contact):

> **Subject:** Request for approval: "Sign in with Claude" in Lani, an open-source Slovene-learning app
>
> Hello,
>
> I develop Lani, a free, open-source Android app for learning Slovene (public repository: github.com/fentas/lani).
> Its tutor is Claude: it reacts to the learner's reviews, chats with them, grades their typed answers and plays
> role-plays in Slovene.
>
> Today the tutor runs self-hosted: Claude Code on the learner's own machine, on their own Claude subscription,
> connected to the app. We are adding a mode where the tutor runs in the app. The agent loop runs on the learner's
> phone and calls the Messages API. The model gets only typed tools over the learner's own data on the phone (later
> also file tools limited to that data folder): no shell and no web access. Untrusted content, such as messages from
> family members or other learners' towns, goes in as marked data.
>
> In that mode the learner brings their own Console API key. We would like learners on Pro or Max to be able to use
> their subscription instead. Our estimate for an active learner is about 50 calls a day with a context of about
> 35,000 tokens; a quieter learner makes about 15 calls.
>
> Your Agent SDK overview says third-party developers may not offer claude.ai login "unless previously approved",
> and the Claude Code legal page says it is not permitted. We have not built it, and we will not imitate Claude
> Code's sign-in. We ask:
>
> 1. May Lani offer "Sign in with Claude", completed through Anthropic's own flow, so that learners on Pro or Max use
>    their subscription for the tutor?
> 2. If a program covers this, which one should we apply to?
>
> We will follow any conditions you set.
>
> Thank you,
> [name and contact]

### Check 3: ElevenLabs, the docs

v4 Turbo on `/with-timestamps` and request stitching on v4 or v4 Turbo: not stated in the docs (stitching is
documented as unavailable on v3). The alignment format: `characters`, `character_start_times_seconds`,
`character_end_times_seconds`. The test call runs with check 5.

### Checks 3 and 5: the test calls and the name sample (2026-10-14)

- **v4 Turbo:** `/with-timestamps` and request stitching both answer HTTP 200; the stitching context isn't billed;
  about 0.2 credits a character (not 0.5).
- **The sample is on Jan's phone:** 13 lines, A (today's whole take) and B (stitched) side by side, C (a whole v4
  Turbo take, so B against C is the seam alone), plus pause ladders (D), the name voiced alone with context (E) and a
  pitch match (F). `README.txt` lists every file.
- **For the native check:** "Muri, poglej, naš Jan je spet tu." (Janez to his dog), "Danes bova z Janom spekla
  potico." (the dual), and the carriers "Jan! Pridi sem.", "Dober dan, Jan! Kako si?", "Dobro, Jan. Zdaj vem.",
  "Danes je Jan doma.", "Grem z Janom domov."
- **What it changed:** [plan 3](03-voice-service.md#proposed-lines-with-the-learners-name-a-filler-name-cut-out-2026-10-14):
  the cut needs Whisper's word times, not the timestamps alone; a name's carrier never ends on "Jan." (read as
  "januar"); the pitch match matters mid-phrase and for long names.
- **Spent:** 207 credits; 24,270 characters left until the reset on 2026-10-24.
