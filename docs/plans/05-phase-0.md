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
| 3 | **ElevenLabs:** `/with-timestamps` and request stitching (`previous_text`, `next_text`) on v4 Turbo | a few characters | Claude | docs done (not stated); the test call runs with 5 |
| 4 | **The validators in a JS engine:** the bridge's validators bundled; QuickJS and JavaScriptEngine in the app; the size, the start, a run per file; the same verdicts and messages as the bridge on the conformance corpus (every curated file and broken variants) | the emulator | Claude | running |
| 5 | **The filler-name voice sample:** lines with the name (calls, closings, thanks, mid-phrase) in three voices, voiced with a filler and the name put in, next to today's whole takes | ~2,000 ElevenLabs characters | Claude makes it; Jan and the native check listen | running |
| 6 | **The prototype:** the loop in the app behind a developer setting, in shadow mode on Jan's real events (reads through the paired bridge, writes dry-run, replies in a debug panel next to the real tutor's); usage, cost and latency per call, exported | — to build; the key to run | Claude | running |
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
