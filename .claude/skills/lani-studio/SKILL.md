---
name: lani-studio
description: Create, adjust, fix, or retire practice modules and word packs in the Lani Android app — new drills, challenges, and exercise sets built from the learner's mistake patterns — and publish them live through the lani channel. Use when the learner asks for a new drill/challenge/module ("make me exercises for X", "this exercise is broken", "harder clitic practice") or for new words on a topic ("teach me words for the doctor"), when a session_end or mistakes-db shows a recurring weak pattern without a matching module, or when an app exercise needs correcting.
allowed-tools: Read, Bash, mcp__lani__publish_module, mcp__lani__list_modules, mcp__lani__get_module, mcp__lani__rollback_module, mcp__lani__publish_pack, mcp__lani__list_packs, mcp__lani__publish_grammar, mcp__lani__list_grammar, mcp__lani__get_grammar, mcp__lani__publish_scenario, mcp__lani__list_scenes, mcp__lani__get_scene, mcp__lani__publish_scene, mcp__lani__remove_scene, mcp__lani__publish_dialog_variant, mcp__lani__remove_dialog_variant, mcp__lani__list_stories, mcp__lani__get_story, mcp__lani__publish_story, mcp__lani__remove_story, mcp__lani__list_readings, mcp__lani__get_reading, mcp__lani__publish_reading, mcp__lani__remove_reading, mcp__lani__list_villagers, mcp__lani__get_villager, mcp__lani__publish_villager, mcp__lani__remove_villager, mcp__lani__reply
---

# Lani Studio — building the app from the inside

## Overview

The Android app renders **modules**: versioned JSON specs (`lani.module/v0`) made of exercise primitives. You author them; the bridge validates and publishes them; the app picks them up live through its event stream. No app release is needed to add or change practice content.

The full spec with every exercise type is in [reference/module-spec.md](reference/module-spec.md). Read it before writing a spec.

## When to Use

- The learner asks for new practice, a challenge, or a change to an existing exercise.
- The learner reports an exercise is wrong, confusing, or too easy/hard.
- A `session_end` event or `mistakes-db.json` shows a pattern with `frequency >= 2` or `consecutive_incorrect >= 2` and `list_modules` has no module targeting it.

Skip this skill for grading a single answer (use `lani-feedback-formatter`) and for persisting session results (use `lani-db-updater`). A review card, question or dialog choice the learner reports as wrong or badly graded: `lani-fix` finds the item and fixes it (and comes back here to rewrite a module).

## Instructions

### 1. Load context

```bash
python3 "${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/hooks/read-db.py"
```

Then call `list_modules`. If a module already targets the pattern, **update it** (same `id`, new version) instead of creating a near-duplicate.

### 2. Design

Pick exercise types that attack the error, not just test it:

| Error kind | Best primitives |
|---|---|
| Word order / clitics | `reorder`, then `translate` |
| Endings, agreement, cases | `cloze`, `choice` with near-miss options |
| Spelling / diacritics | `cloze`, `translate` (match grading) |
| Register (ti/vi) | `scenario`, `multi` ("which are polite?") |
| New vocabulary | `flashcard`, `choice` with `audio`, then `translate` |
| Listening | `choice` with `audio`, `scenario` with a spoken `line` |
| Real-life use | `scenario` (bakery, bank, the in-laws' kitchen) |
| Pronunciation / speaking | `speak` (`show: true` to repeat a model, `false` to produce it from English) |
| Open production | `free` (graded by you via the channel) |

Rules:

- **5-12 exercises per module.** Go from recognition (`choice`) to controlled production (`cloze`, `reorder`) to free production (`translate`, `free`).
- **Interleave 2-3 patterns** when the learner has several weak spots. Aim for 60-70 % success.
- **i+1:** use known vocabulary from `spaced-repetition.json` and add at most 3 new words per module, each explained in `explain` or `note`.
- **Never leak the answer in the prompt.**
- **List all accepted variants** in `accept` and `solutions` (with/without `jaz`, other valid word orders). The app normalises case, punctuation and whitespace. Missing diacritics count as "almost", not correct.
- **Standard Slovene** in answers. If a regional variant the learner uses is valid (e.g. Primorska *nasvidanje*), mention it in `explain`; don't reject it silently.
- Put the real pattern IDs from `mistakes-db.json` in `targets`, so progress links back.

### 3. Self-check before publishing

Solve every exercise yourself from the prompt alone. Check that:

- every `reorder` solution uses exactly the given tokens,
- every `choice` has exactly one defensible answer,
- each `cloze` gap has a single unambiguous slot.

### 4. Publish

Call `publish_module` with the spec and a one-line `note` (shown in the app, e.g. "3 new reorder drills for *se*"). If it returns validation errors, fix the spec and try again. Don't work around the validator.

### 5. Tell the learner

If the request came from the app, use `reply` with the `conversation_id` from the channel tag. Say what you built and why (which mistake it targets) in 2-3 lines.

### Village sidequests

The app's game *Moja vas* turns practice into a village. A module with a `quest` block appears there as a villager's request, and players often choose quests over plain modules. So prefer writing targeted practice as a quest:

1. Call `get_village` to see the age, resources, open quests and recent events. Aim the quest at what the village lacks. For example, if stone is short and grammar is weak, make a grammar quest that pays stone.
2. Read `companion/GAME.md` for the characters and reward ranges.
3. Add the `quest` block (see the reference) and publish as usual. Keep the story, the exercises and the skill consistent.
4. **At most two at once.** A villager shows at most two open requests: their own first, then your newest. The rest wait in the app's Scroll under "Kasneje · Later". Spread quests over the cast; check `open_quests` for the giver before you add one.
5. **A drill for a task Jan keeps failing.** `open_quests` shows each request's `module`, `tries`, `best` ("7/12"), `pass_mark` and the grammar pages it `missed`. For a task tried a few times below the mark, write a short drill (5-8 exercises, tagged `short`, its exercises naming the pages missed) at the same giver, with `"helps": "<the task's module id>"` in its quest block. The task then waits behind the drill ("⏸️ Najprej · First: …") and comes back once the drill is passed. A chat with `data.quest_drill` (the request's "🎯 Vadi, kar ti ne gre" found no grammar page to practise) asks for exactly this: its `module`, `title`, `giver`, `tries`, `best` of `of` and `pass_mark` are there.

### Word packs (new words)

Modules drill; **packs teach new words**. A pack (`lani.pack/v0`) is 8-24 themed words. The app introduces 5-7 at a time on picture cards (Slovene spoken aloud, gender, plural, example), drills them at once, and saves each as a vocabulary item in `spaced-repetition.json` (`vocab_<pack>_<word>`). From then on the regular reviews bring them back, and they count towards the village's word milestones.

Write a pack when the learner asks for words on a topic ("teach me words for the doctor", "what do I need at the market?") or when `list_packs` shows the learner has finished what fits their next real situation.

1. Call `list_packs`. Don't repeat words the learner has; the app skips words that match an existing vocabulary item anyway.
2. Write the pack (format in the reference, "Word packs"): high-frequency words for one real situation, each with `emoji` if one fits, `gender` and `plural` for nouns, and a short `example_sl`/`example_en` that uses only known words plus the new one. The emoji must show the word itself; never a different object — when no emoji shows it, use the pack's topic emoji. A villager from `companion/GAME.md` as `giver` makes it part of the village.
3. `publish_pack` with a one-line `note`. The app shows the pack with a NOVO badge.
4. The app reports finished words as a `words_learned` event. It is already persisted: don't call update-db.py for it.

### Grammar pages (the village book's "📖 Slovnica · Grammar")

The rules you explain in passing (a "why", a chat) stay in the app's grammar book: one page per rule (`lani.grammar/v0`,
[reference/grammar-spec.md](reference/grammar-spec.md)), with the rule in the learner's base language, a table, examples
with 🔊, the modules that practise it, and the learner's own mistakes on it. 44 curated Slovene pages ship in
`companion/grammar/sl/` (the six cases, each case's page and the declension tables among them); a page unlocks when the
learner meets its rule in an exercise that names it (`"grammar": "<id>"`).

1. When you explain a rule in chat, `list_grammar`: extend its page with your example or a clearer explanation (`more`),
   or write a page for a rule the book lacks. `publish_grammar` with a one-line `note`.
2. Tag the exercises of every grammar module you write with `"grammar": "<page id>"`, and a new module in the page's `modules`.
3. A chat with `data.grammar_drill` asks for a short drill on that page: write the module (tagged), then extend the page's
   `modules` with it.

**How the app adapts** (companion/GAME.md, "Mastery and adaptive turns"). Each page has a mastery the app computes from
the learner's answers on its rule (the run of right answers in a row), their grammar cards and their mistakes-db patterns
on it: new, learning, secure (4 right in a row, 3 stars, no open mistake), mastered (8 in a row over days, 4 stars). A
turn that tests the rule (a scene's choice that differs from the right one in one word; a module's `choice` with a `___`
gap) is chosen while they learn it, typed into its gap once it is secure, and said whole once it is mastered; "✋ Izberi
raje · Let me choose" or a wrong answer brings back the choices, and a slip steps the mastery back a level. A mistake
stays open (and the rule learning) until its card's review is right on a later day, or a run of right answers begins
after it. `list_grammar` shows each page's `run`; "💬 Vprašaj" sends its `mastery`. Where a turn tests a form, the app
shows the learner's **own confusion** as the wrong choice: the words they swapped in mistakes-db's examples ("nimam
vžigalico" for "Nimam vžigalic.", and "Lahko ena kava?" makes "Pijem voda." in an accusative turn). So record every
`errors[]` entry with `your_answer` exactly as they had it and `correct_answer` the right sentence (parts of one example
joined with " / ", notes in brackets), a spelling slip as `category: "spelling"` and a hearing one with `subcategory:
"listening"` (those set no traps).

**Rules not yet, and introducing one** (reference/grammar-spec.md, "Rules not yet"). Below new, a rule is *not yet* when
its page's level is above the learner's and it wasn't introduced (page not unlocked, no answers, cards or mistakes on it).
A dialog turn of such a rule doesn't ask for its form: the learner chooses about meaning or repeats the line (an echo),
and meets the rule. Write dialogs natural at their level anyway, forms above the learner's level included, and name the
pages. When `list_grammar`'s `ripe`, the morning snapshot's or a session_end's `grammar_ripe` lists a rule (met 3 times,
the next level up), introduce it when the learner is ready: a short module whose exercises name it, or its page extended
for them (`publish_grammar` under its id). The app opens the page itself after 6 meetings on 2 days.

### Role-play scenarios (conversations)

The app's "Pogovor · Talk" screen plays **scenarios** (`lani.scenario/v0`): you act a character (the mother-in-law at Sunday lunch, the baker, the doctor), Jan answers by voice. Six curated ones ship in `companion/scenarios/`. Write a new one when the learner asks to practise a situation ("let's practise at the post office", "I have to call the plumber") or when their real life brings one up.

1. Write the scenario (format and protocol in the reference, "Role-play scenarios"): a real situation, a named character (`role` starts with the name and a comma), 3-5 goals, an opener the learner can answer with the vocabulary hints. Keep every Slovene line simple and correct.
2. `publish_scenario` with a one-line `note`. The result lists all scenario ids, so you can avoid duplicates. Reply to Jan that it's in "🗣️ Pogovor · Talk".
3. When the learner plays it, `roleplay` and `roleplay_end` messages arrive on the channel; the channel instructions and the reference say how to answer and persist them.

### Scenes (stepping into the village)

The village has **scenes** (`lani.scene/v0`): close-ups of the campfire, the forest, the kitchen and the square, and the interiors of the tent, the field, the smithy and the school. Tapping an object teaches its pack word; on some days a villager is there (a **happening**: a time of day, weekdays, a chance) with a short **dialog** of Slovene choices, and a small reward; when it is done, the villager keeps a **memory** of it. Eight curated scenes ship in `companion/scenes/`. The format, the art registry and the rules are in the reference ("Scenes") and in `companion/SCENES.md`.

Write a happening (or a whole scene) when:

- **A weak point keeps coming back.** `mistakes-db` or a `session_end` shows a pattern (ti/vi, the case after a preposition, the dual, an adverb vs an adjective) and the drills are not enough: stage it in a dialog where the learner has to choose it, with the wrong choices wrong for exactly that reason. E.g. Učiteljica Mojca at the well counting in the dual, Kovač Tone at the smithy asking for the accusative.
- **The learner's real life comes up.** A visit to the in-laws on Sunday, a doctor's appointment, the first burja of the year: put it in the kitchen or on the square that week, and give the dialog a `talk` scenario so it can go on with you.
- **A festival or a season.** Advent and potica in December, chestnuts (kostanj) and jurčki in October, kresovanje (bonfires) on 30 April, World Bee Day on 20 May with Čebelar Anton: a happening with `weekdays` and a `chance`, and objects that teach the words of the season. Remove it again afterwards.

How:

1. `list_scenes` shows what exists and what is on when; `get_scene` gives a scene's spec. To add a happening to a curated scene, take its spec, add the person (if new), the happening and the dialog, and `publish_scene` it with the same id: the tutor version replaces the curated one. `remove_scene` brings the curated one back, so a seasonal happening is easy to undo.
2. Every object's word must exist in a pack (`list_packs`); publish the pack first if the words are new. A scene may take words from several packs (each object's own `pack`).
3. Follow the dialog rules in the reference: 3-8 lines, A1 Slovene checked twice (case endings, aspect, clitic order, dual, diacritics), one learning point per choice turn, `why` and the villager's reaction (`reply`, never the right form) on every wrong choice (a wrong choice that differs in one word tests a form: name its grammar page, `grammar`, when the why doesn't say the case or form in plain words; the app types and says such turns as the learner masters the rule), two right choices where people would accept either (greetings, thanks, goodbyes), the learner's lines in the masculine, the villagers in their voices (`list_villagers`, `get_villager`), every person with their `villager` and every happening with a `memory`, rewards 15-30 like a short quest. People in the same spot need different times of day (the app shows one happening per person, at most four at once).
4. `publish_scene` with a one-line `note`. The app reloads its scenes; the village bubble shows the happening at its place. Tell the learner where to look ("Micka is in the kitchen this evening").

**A fresh variant of a happening.** A happening may have several dialogs (`dialogs`, its variants): the app plays one the learner hasn't heard, then, once all are heard, the one heard longest ago after a few days, never the same twice in a row. When `dialog_variants_heard` comes (the learner has heard all of a happening's dialogs; the note names the happening, its person, the dialogs heard with their first lines, the learner's level and weak spots from `mistakes-db`), write one more with `publish_dialog_variant` (the scene, the happening, the dialog): the same situation (the title, the time, the person), other words and another point of grammar than the ones heard, at the learner's level, around a weak spot where it fits, with every rule above (a reaction on each wrong choice, never the right form in it; the learner's lines masculine; only the person and whom the happening names). Where something is counted (sheep, eggs, logs, coins), let the number come from the village with a `count` (`{n}` and the forms `{ovca|ovci|ovce|ovc}`, the wrong choices `wrong_form` 1 and 2: the reference, "Scenes"). It plays the next time the happening comes, marked as yours; no message to the learner is needed (the `note` is optional). `remove_dialog_variant` takes one back.

### Stories (the evening story by the fire)

Every evening Stari Janez tells a story at the campfire (`lani.story/v0`): the next one the learner hasn't heard, at their level, a long legend (Martin Krpan) in chapters, one an evening; once all are heard he retells them a level up. Nine curated legends ship in `companion/cultures/primorska/stories/` (A1 and A2, three at B1). The format and the rules are in the reference ("Stories") and in `companion/SCENES.md`.

Write one when a `stories_low` note comes (fewer than three stories the learner hasn't heard are left at their level: write one that day, so an evening never runs out), when `list_stories` says `all_heard` (a new story is told before the retellings), when the learner asks for a story, or to bring a week's words back in one. Write it for Janez at the learner's current level (`learner_level`), with words they know and a few new ones (in `words`), 8-14 lines, 2-4 turns (a weak pattern from the note in one of them), the Slovene checked twice, 2-4 `pictures` from the vignette library (`picture_library`), and its `notes` (what the learner would have written down of each telling: the story as prose in the past tense, no asides, at its level): a legend of the region the collection doesn't have, or a continuation of one they liked (`continues`: its id; "Martin Krpan se vrne"). `publish_story` with a one-line `note`, and tell the learner Janez has a new story tonight. Every story heard is written down in the learner's story notebook ("📓 Moj zvezek zgodb"): its notes, its pictures as pencil sketches. `get_story` gives a story to change; `remove_story` takes back a tutor's.

### Readings (the reading corner)

The app's "📖 Branje · Reading" lists short texts by level: the culture pack's (`companion/cultures/<culture>/readings/`, what the chest's things hold and more) and yours (`<data>/app/readings/`). Each is read line by line with its translation, then its questions, its new words (➕ to the reviews), and "🎤 Beri na glas", read aloud and graded word by word. The format is the culture packs' readings (`companion/GAME.md`, "Readings"; the validator `companion/bridge/src/readings.ts`). A reading done, a story heard to its end and a reading aloud count as the learner's reading practice (`reading_done`, already persisted).

Write one when `list_readings` says `unread_at_level: 0`, when the learner asks for something to read (a chat with `data.reading_request`), or to bring their topics and weak spots (`mistakes-db`) into a text: `kind` page, letter, story or article; at their `learner_level` (A1 40-80 words, A2 80-150, B1 150-250), short sentences, words they know and a few new ones; every line in Slovene with its translation in their base (and the other languages its `title` has), read by a villager of the cast (`by`); 3-5 `questions` in Slovene (`main_idea`, `detail`, `word` with the `word` as it stands in the text, `true_false` with `answer` and no options, `inference`; the others 3-4 options, the answer first; `explain` quoting the text), the `lani-reading` skill's method; up to 8 new `words` (`word` as a dictionary has it, `form` as it stands in the text, `means` in the other languages). Check the Slovene twice (case endings, aspect, clitic order, the dual). `publish_reading` with a one-line `note`; `get_reading` gives one to change, `remove_reading` takes back yours. A culture pack's id is refused.

### Villagers (the people who remember)

The village has a **cast** (`lani.villager/v0`): Babica Micka, Stari Janez, Pastir Luka, Kovač Tone, Mlinar France, Čebelar Anton, Učiteljica Mojca, Gostilničarka Vida, Vinar Marko, Teta Ančka and the children Nejc, Zala and Tine. Each has a personality, a story, likes, a register, a home and lines for every occasion (greeting, thanks, remembering, idling, and cheering, comforting and "listen" on the training stage), warmer at higher friendship levels. Thirteen curated villagers ship in `companion/cultures/primorska/villagers/` (the cast of Jan's culture pack, see `companion/GAME.md`, "Culture packs"). The format and the rules are in the reference ("Villagers") and in `companion/VILLAGERS.md`. The friendship itself (points, memories) is the app's: you read it in `data.villager` on a villager role-play and add to it with `data.memory` in the debrief.

Write or change a villager when:

- **Someone moved in.** A `villager_arrived` message (kind `newcomer`) names a resident the app generated (a name, a trade, a Primorska family name). Give them a personality, a story, likes and lines with `publish_villager` **under the same id, name, art and voice**, with the `speaker` for their age and gender (`young-woman`, `young-man`), in Primorska style, so they become cast: their friendship and their home carry over. For a birth, a warm line to Jan is enough; the child gets their own lines when they are old enough to talk to.
- **How Jan meets them.** Nobody hosts, asks or stands in a scene before Jan has met them in their introduction (an arrival: a bubble at their home, a short dialog). The culture pack has one for each of the cast and templates for everyone else; write the newcomer's or the baby's own with `publish_arrival` (reference, "Arrivals"): at `data.arrival.level`, around one of `data.arrival.weak_patterns` where it fits naturally, the newcomer speaking (a baby's parents, `data.arrival.parents`, for a baby), every wrong choice with its reaction. `get_arrival` shows what plays now.
- **A villager should say more.** Jan keeps talking to Luka and the lines repeat: `get_villager`, add lines (or a like, or a story detail Jan learned in a talk), `publish_villager` with the same id. Keep their `name`, `skill`, `home`, `since` and `order`; `remove_villager` brings the curated version back.
- **A new person for a new place.** A scene or a quest needs someone the cast lacks (a priest for the church, a stallholder for the market): a new villager with a home at that building and a `since` that fits its age.

How: every line simple and correct at A1-A2, in their voice, the register right (older people vi, children and friends ti), `remember` lines with `{memory}`, likes as "Slovene · English". `publish_villager` with a one-line `note`; the app reloads its cast and voices the lines.

## Fixing and retiring

- **Fix:** `get_module`, correct the spec, `publish_module` again. This creates a new version, and the app switches to it.
- **Undo:** `rollback_module` to the last good version.
- **Learner data by hand:** a wrong review card is fixed with `.claude/hooks/card.py` (the `lani-fix` skill), which keeps its history. Before repairing a database file directly (a broken JSON file), run `companion/bin/lani-backup` so there is a snapshot to go back to. `companion/bin/lani-backup restore <name>` previews a restore; add `--only data/<file>.json --apply` to restore one database.
- **Retire:** publish a new version with `"tags": ["retired"]`. The app hides retired modules. History stays.

Spaced-repetition reviews don't need modules. The app turns due cards into recognize, listen, pick, tile and type variants by itself, harder for better-known cards.

## Beyond modules: changing the app itself

Modules change **content**. For a new mechanic, screen or widget, first append a dated entry to `<data dir>/app/wishlist.md`: what, why, which primitive or screen it needs. Tell the learner it's on the build list.

When the learner asks you to build a wishlist item:

1. Change the app in `companion/android/` (and the bridge and spec if it's a new exercise type; see "Extending the format" in the reference).
2. Run the tests: `cd companion/android && ./gradlew :app:testDebugUnitTest`, and `bun test/smoke.ts` in `companion/bridge` if you touched the bridge.
3. Commit on `main`.
4. Run `companion/bin/release-app "<one-line notes>"`. The app installs the update by itself (once the learner has allowed it) and shows the notes.
5. If you changed the bridge, tell the learner to restart the tutor session so it loads the new bridge code.

Publish modules that use a new type only after that release is out.

## Critical Rules

- **Only change the app through the bridge tools.** Never write files under `<data>/app/modules/`, `<data>/app/packs/`, `<data>/app/scenarios/` or `<data>/app/scenes/` directly.
- **Validator errors are real errors.** Fix the spec, never loosen it.
- **Linguistic correctness beats creativity.** If you're unsure a sentence is natural Slovene, use a simpler one.
- **One module per purpose.** Update existing modules instead of spawning variants.
