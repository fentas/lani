# The tutor's wishlist, October 2026

The tutor keeps a wishlist of what the app gets wrong or makes hard: content to fix in the repository, and small app
changes. Jan approved every entry up to 2026-10-25. This file says what was done for each and what is left.

## Content (curated, existing keys only)

| Entry | What changed | Why |
|---|---|---|
| `praznik-trgatev` · `skarje` | `en` "scissors" (de "Schere", it "forbici"), `example_en` "Take the scissors!"; the pruning shears in the note | "secateurs" is a rare word: Jan didn't know the English and picked "boots" |
| `ob-ognju` · `kurim` | `sl` "kurim (ogenj)" (the reviews take "kurim" and "kurim ogenj", as `hvala (lepa)`); `en` "I keep the fire going"; "I make a fire" in the note | a two-clause `en` read as a sentence to translate |
| `v-hisi` · `pec` | `en` "heating stove (the big tiled one that heats the farmhouse)", de and it likewise | the prompt "stove" was babicina-kuhinja's `stedilnik`'s too |
| `vreme-in-dan` · `vroce`, `toplo` | `sl` "vroče (je)", "toplo (je)"; `en` "it's hot (the weather)", "it's warm (the weather)"; the adjectives in the notes | the prompt "hot" was v-kovacnici's `vroc`'s too; "warm" would be a later `topel`'s |
| `dom-in-hisa` · `luc`, `v-sotoru` · `svetilka` | `luc` "light" (any lamp in the note), `svetilka` "lantern" (a lamp you carry or hang up), de and it likewise | both asked "lamp" |
| scene `v-sotoru`, dialog `svetilka-komu` | Luka's reaction "Kako? Ej, {m:zmešal\|f:zmešala} si me, {learner}!"; the German why "Du bist es, {m:der\|f:die} leiht" (en and it are neutral already) | a form that agrees with the learner without its pair |
| unit `biti`, worksheet `biti-2`, exercise 1 | the last gap takes `sem` and `smo`; its explain says both (vi to one person: sem; vi to several: smo) | vi is polite singular or plural |

Checked with card.py's overlap rule over every curated Slovene pack (each word as the card the app makes of it, in en, de
and it): the three collisions are gone and none is new. The packs, the scene and the unit pass the bridge's validators,
and the content audit (`bun test/learner-content.ts`) is clean. The packs ship in the APK too: an app release brings them
to the hosted mode.

## App and bridge

- **A select-all looks like one** (`MultiEx`, ui/PlayerScreen.kt): each option had a checkbox already. The Check button
  now counts the options ticked: "0 izbranih · 0 selected" while none is ticked (the button disabled), then
  "Preveri · Check (2)". The verdict is `Grading.multi` (unchanged: all right and none else is right, one off is almost).
- **Almost for picked answers.** `choice` and a picked `scenario` take an optional `almost: [indices]`: options that are
  almost right, a real answer that doesn't fit there. Picking one is almost (`Grading.picked`), paid as almost, with the
  exercise's `explain`; its card turns orange. The bridge's validator (src/spec.ts) refuses an index out of range, the
  answer itself, one twice, `almost` on a typed scenario, and an `almost` that leaves no option wrong. The school's quick
  questions (a step's `ask`, the same schema) grade it too. A learner's own trap that takes a near miss's place makes it
  wrong, as the trap is (game/Adaptive). The tutor learns it from publish_module's description, the lani-studio skill
  and module-spec.md (the studio guide), and the studio prompt (tutor-export regenerated, `--check` clean).
- **A village challenge asks a card as the reviews do** (game/Content, data/ReviewPlanner): one rule,
  `ReviewPlanner.quizzes`, for both: a word with a front to type, not a list. The reviews flip everything else (a
  grammar card, a rule, a placeholder, now a list: a front or a meaning of parts between " · "). A run built from cards
  (gathering, requests, project steps, events, trades) never types or builds a grammar card or a list: it is a
  flashcard. By ear a grammar card is heard and its meaning picked, as before, a list never (a flashcard only where the
  run would be short without it); a rule or a placeholder stays out of a written run, as before. No list's words are a
  distractor's chips or options.
- **" · " is a separator like a comma** in `Grading.normalize`: "midva sva, vidva sta" and "midva sva · vidva sta" are
  the same answer; the dots never glue two words together. card.py and its TypeScript twin (src/db/card.ts) follow both
  rules (`normalize`, `variants`), so `card.py show` and `overlaps` say what the app does.

## How to see it

- Unit tests: `GradingTest` (the separator, picked and multi verdicts), `ParsingTest` (`almost` read), `MultiCheckTest`
  (the count), `ChallengeCardsTest` (the picker), `QaHooksTest` (the request hook), tests/test_card.py (lists flipped,
  the dot in overlaps), the smoke's modules section (almost accepted, served and refused).
- Emulator: `companion/bin/qa --steps wishlist` (with `LANI_DEV_DATA`, a synthetic learner): a module written into the
  dev bridge's copy (a select-all, a choice and a scenario with a near miss) and QA's hook `request:<ids>` (Mojca's request
  of the named cards, its challenge started). Screenshots also as wish-*.png in `$LANI_QA_WISH_SHOTS`.

## What's left

- The module from the wishlist, `dober-dobra-dobro` #6 (Živjo! as almost), is the learner's own (a tutor module in their
  data, not in the repository): the tutor republishes it with `almost` once the bridge runs this validator and the phone
  has an app that plays it (an older bridge drops the key).
- The festival line of trgatev ("Take the secateurs and a basket!", cultures/primorska and friuli festivals.json) still
  says secateurs: a translation, never a prompt, left as it is.
- A pack word with an optional part ("kurim (ogenj)", "vroče (je)") is a new clip text ("kurim ogenj", "vroče je") for
  the voices: until voice-build makes it, the phone's voice says it.
