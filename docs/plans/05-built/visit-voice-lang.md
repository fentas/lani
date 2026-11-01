# Built: no text voiced in the wrong language (visit-voice-lang)

*Built 2026-11-01, branch `visit-voice-lang` (from `polish-app`), after the lead's follow-up to the visit voices' QA.*

## What happened

In the visit voices' QA run (`companion/qa-shots/20261101-130941/bridge.log` of the voices helper's worktree), the dev
bridge of an Italian learner (Leo: Italian from German, culture friuli), linked to the dev town "Vas za Preizkus" (Maja,
Slovene), asked the fake ElevenLabs for 204 texts with `language_code it`. Most of them were Slovene: "Moj zaklad!
Pridi, juha je vroča.", "Mandi! Jutri je kosilo ob enih. (mandi: furlanski pozdrav)", "Dobro jutro", "Dober dan",
"Dober večer", "Še o tebi".

**Whose lines.** Not the host's: the visit voices voiced the host's lines in Slovene, with `language_code sl` ("Danes
pečem kruh. Vonjaš?", "voiced here"), as designed. They were the learner's **own village's** villagers (Nonna Rosa,
Zia Nives …) in their **Slovene translations**. The Friuli villager files have `it`, `sl`, `en` and `de` for each line
(the `sl` for a Slovene base). There were also the app's built-in greetings in Slovene, and one label of the setup
("Še o tebi", `setup.aboutTitle`). It is not a fixture artefact: Teo, visiting Jan's village, would have had the same.
The lead checked Teo's store (3,388 clips, none in Slovene), so nothing was spent wrongly yet.

**Why the node voiced them, with its own language.**

- **The app asked for them.** After a reading, the app plans the day's audio for the background job
  (`AppViewModel.dayGather`, `app/DayAudioGather`), which asks its own node for the clips (`POST /voice/prepare`). The
  villagers' lines were taken with `VillagerLine.target()`, whose default is `L10n.pair`, and the greetings with
  `greeting()`, which reads `L10n.pair` too. During a visit `L10n.pair` is the visited town's (Slovene): the plan took
  Rosa's `sl` translations and "Dobro jutro". The setup read its title in a pair that wasn't yet the learner's.
- **The node trusted every text.** A text asked without `lang` was taken as the village's language and voiced with its
  `language_code` (Italian). `lang` only marks an explanation in the learner's own language.

**Credits.** Only the visit voices spend the visitor's credits on a host's line: a line the host has no clip of, said
on the visit, is voiced on the visitor's node, in the host's language (visit-voices.md, "voiced here"). That is
intended: the visit's lines wait for the host up to 4 s, then for the node. The 204 texts above were none of that: they
were the visitor's own content, asked in the wrong language.

## The fix

- **The app plans the day in the learner's own pair** (`DayAudioGather(pair = L10n.ownPair)`: the villagers' lines by
  `target(pair)`; `greeting(hour, lang)`, the day's greetings in `L10n.ownPair.target`). A visit no longer changes what
  the own village's day asks for.
- **The node never voices a text whose language it knows is another's** (`src/voice-lang.ts`, `/voice/say` and
  `/voice/prepare`):
  - A text asked with `lang` (an explanation, in the learner's own language or another the app shows) is voiced as
    before: plainly, without a `language_code`, so it can't be read in the wrong language.
  - A text asked without `lang` is not voiced when the node knows it only in other languages: its villagers' lines and
    its scenes' dialogs (lines, choices, replies) in each of their languages, a line's `{m:…|f:…}` in both genders, and
    the app's label tables when the app's sources are there (a node, not a phone).
  - Nothing is voiced then: `/voice/prepare` answers `url: null` for that text, and `/voice/say` answers 204, so the app
    says it otherwise (the phone's voice of that language). The node logs it once per text ("voice: not voiced: …:
    known in sl, not the village's it").
  - A text the node doesn't know (a tutor's live line, a card) is voiced as before.

## Tests

- `DayAudioVisitTest` (2, Robolectric): Teo's day planned while the labels show a Slovene town is Rosa's Italian lines
  and the Italian greetings, none of the Slovene ones; a greeting in the language asked.
- Smoke (Friuli section): Rosa's Slovene "Pridi, pridi. Sedi." and "Živjo! Si lačen?" (her gendered line as said to a
  boy) and the Slovene label "Še o tebi" get no clip, and nothing is asked of ElevenLabs. Her Italian line, a text the
  node doesn't know and an explanation in the learner's Slovene stay voiced. The voice section's explanations in English
  and German still pass.

## What's left

- A text with a placeholder only the app fills ({learner}, {memory}, a count) isn't known to the node, so it is voiced as
  asked. The app's own pair fix covers the day plan, where those lines aren't asked anyway (DayAudio leaves them out).
- The setup still reads its title in the pair it has before the learner's is known. The node now refuses the Slovene
  title on an Italian node, and the phone reads it instead.
