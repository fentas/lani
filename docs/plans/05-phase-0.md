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
| 1 | **The API, from the docs:** the web fetch and web search tools (`allowed_domains`, which models, the price); compaction on demand; preserved thinking on a restarted thread; the tier-1 rate limits; the low-credit error; the Console's workspace limits; the model ids and prices the plan uses | — | Claude | running |
| 2 | **Android, from the docs:** Jetpack's JavaScriptEngine (requirements, WebView versions); `EncryptedSharedPreferences`; foreground-service limits on Android 16; the Java SDK on Android (Jackson, R8, size) | — | Claude | running |
| 3 | **ElevenLabs:** `/with-timestamps` and request stitching (`previous_text`, `next_text`) on v4 Turbo | a few characters | Claude | running |
| 4 | **The validators in a JS engine:** the bridge's validators bundled; QuickJS and JavaScriptEngine in the app; the size, the start, a run per file; the same verdicts and messages as the bridge on the conformance corpus (every curated file and broken variants) | the emulator | Claude | running |
| 5 | **The filler-name voice sample:** lines with the name (calls, closings, thanks, mid-phrase) in three voices, voiced with a filler and the name put in, next to today's whole takes | ~2,000 ElevenLabs characters | Claude makes it; Jan and the native check listen | running |
| 6 | **The prototype:** the loop in the app behind a developer setting, in shadow mode on Jan's real events (reads through the paired bridge, writes dry-run, replies in a debug panel next to the real tutor's); usage, cost and latency per call, exported | — to build; the key to run | Claude | running |
| 7 | **Two weeks of shadow mode:** Balanced, and Haiku against Sonnet on the same events | 6, the key | Jan uses the app | waits |
| 8 | **Quality:** grading agreement with the app's verdicts and Jan's corrections; the Slovene feedback on a sample | 7 | Claude measures; the native check reads | waits |
| 9 | **Memory:** the notebook and the day's thread against today's long session, the same days side by side | 7 | Claude; Jan judges | waits |
| 10 | **The Java SDK against the plain client:** R8, the APK's size, streaming on Android 8+ | R8 merged | Claude | waits |
| 11 | **The request to Anthropic** for signing in with the subscription | 1 (where to ask) | Claude drafts; Jan sends | running |

## Results

*(Filled in as the checks finish.)*
