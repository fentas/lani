---
name: lani-fix
description: Fix a question, review card, exercise, word, dialog choice or translation in the Lani app that the learner reports as wrong, confusing or badly graded ("this question is confusing", "my answer was right but marked wrong", "that's not what X means", "both answers were wrong"). Finds the item from what the app sent (about_exercise.source), explains how the app grades it, fixes it safely (review cards with card.py, keeping their history and scheduling; content with the bridge's publish tools; curated content by an override plus a note for the developer), takes back what the item's fault cost the learner, and tells them. Use whenever a chat reports something wrong in the app, or on /lani-fix.
allowed-tools: Read, Edit, Bash, mcp__lani__reply, mcp__lani__list_modules, mcp__lani__get_module, mcp__lani__publish_module, mcp__lani__rollback_module, mcp__lani__list_packs, mcp__lani__publish_pack, mcp__lani__publish_gloss, mcp__lani__list_scenes, mcp__lani__get_scene, mcp__lani__publish_scene, mcp__lani__remove_scene, mcp__lani__list_stories, mcp__lani__get_story, mcp__lani__publish_story, mcp__lani__remove_story, mcp__lani__list_readings, mcp__lani__get_reading, mcp__lani__publish_reading, mcp__lani__remove_reading, mcp__lani__list_villagers, mcp__lani__get_villager, mcp__lani__publish_villager, mcp__lani__remove_villager
---

# Lani Fix — a wrong or confusing item

## Overview

Jan says something in the app is wrong: a right answer marked wrong, a prompt that can mean two things, a wrong translation, a dialog whose "right" choice is wrong. Find the item, see how the app grades it, fix it so the grader agrees with you, take back what it cost Jan, and tell them. Work with the grader, not against it: most "wrong" gradings are an answer the item doesn't list.

Skip this skill when Jan's answer really was wrong (explain it: `lani-feedback-formatter`) or when they want new practice (`lani-studio`).

`card.py` below is `python3 "${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/hooks/card.py"` (docs/DB_SCRIPTS.md, "Review cards"). It prints JSON and uses the learner's data directory (`LANI_DATA_DIR` for another learner).

## 1. Find the item

"Ask" on an exercise's feedback sends `about_exercise` with the prompt, Jan's answer, `expected`, `verdict`, `hint`, and `source`, the item it came from:

| `source.kind` | Ids | Look it up |
|---|---|---|
| `review` | `card_id`, `variant`, `language` (absent: home) | `card.py show <card_id>` (`--language it` for another deck) |
| `module` | `module_id`, `module_version`, `exercise_index` | `get_module {id, version}` → `exercises[exercise_index]` |
| `pack` | `pack_id`, `word_id`, `card_id` | `list_packs`; the card as for `review` |
| `challenge` | `origin`, `title`, `exercise_index`, and `card_id` (a card), `module_id` (a module's exercise: find it by its prompt) or `pack_id` + `word_id` (a festival's word); `text`: the letter a surprise's question asks about (read first, hidden while it's asked) | as above; none of them: a surprise or the tent move, see §2 |
| `visit_request` | `town`, `request_id` | the host town's curated pack words |

Other contexts: `about_exercise.error_pattern` (a mistake on the Progress screen: a mistakes-db pattern id); `lookup {word, line, en}` (a word card: the dictionary). Without a source (an older app; scene dialogs, stories, villager lines, readings and surprises have no Ask button), search the text Jan quotes: `card.py find <word>`, `list_scenes`/`get_scene`, `get_villager`, or `grep -rl "<text>" companion/ <data dir>/app/`.

## 2. Where each kind lives

Curated content is in the repo (`companion/`); the tutor's in `<data dir>/app/`. Never edit repo files from the tutor session: publish an override where a tool allows it, and note the problem for the developer (§6).

| Item | Where | Fix | The app sees it |
|---|---|---|---|
| Review card | `<data>/spaced-repetition.json` `items[id]` (the learner's) | `card.py` | on its next trip Home (GET /state reads the file each time) |
| Module exercise | `companion/modules/<id>.json`; tutor's `<data>/app/modules/<id>/v<N>.json` | `publish_module` with the same id: a new version, replaces a curated module (`rollback_module` can't reach its v1: publish the curated spec again) | at once (`module_published`) |
| Word pack | `companion/packs/<id>.json` (`packs/<lang>/` for other languages), `companion/cultures/<c>/packs/`; tutor's `<data>/app/packs/` | tutor pack: `publish_pack`, same id. A curated id is refused: note it, and fix the cards made of it (`vocab_<pack>_<word>`) with `card.py` | at once. Festival packs (`packs/praznik-*`) and culture packs' packs are also in the APK: a release |
| Scene dialog | `companion/scenes/<id>.json`, `companion/cultures/<c>/scenes/`; tutor's `<data>/app/scenes/` | `get_scene`, change, `publish_scene` with the same id (replaces the curated one; `remove_scene` brings it back) | at once (`scene_published`) |
| Story turn | `companion/cultures/<c>/stories/<id>.json`; tutor's `<data>/app/stories/` | `get_story`, `publish_story` same id; `remove_story` | at once |
| Reading question (the reading corner's) | `companion/cultures/<c>/readings/<id>.json`; tutor's `<data>/app/readings/` | tutor's: `get_reading`, `publish_reading` same id; a culture pack's id is refused: note it | at once (`reading_published`); a developer's fix of a culture pack's reading: the corner reads the node's copy on its next reload, the chest's own view with an app release |
| Villager line | `companion/cultures/<c>/villagers/<id>.json`; tutor's `<data>/app/villagers/` | `get_villager`, `publish_villager` same id (keep name, art, voice, home) | on the next reload (Home), no banner |
| Role-play scenario | `companion/scenarios/<id>.json`; tutor's `<data>/app/scenarios/` | curated ids are refused: note it | next time Talk opens |
| Word lookup | `companion/lexicon/sl.json` (Wiktionary), `sl.extra.json` (hand-written; `override: true` replaces a lemma's gloss) | `publish_gloss` (the word card lists it with the dictionary's, before it unless the dictionary's matches the line; pack words first); a word Jan added (`vocab_word_<lemma>`) also `card.py edit` | at once (`lexicon_updated`) |
| Surprises, readings, tent-move quiz, festivals, people's plain lines | `companion/cultures/<c>/*.json`, `readings/` | bundled in the APK: note it | an app release |
| Gift-talk phrases, UI labels, grading tables | app code, `resources/l10n/`, `resources/grading/` | note it | an app release |

A developer's edit of curated files: packs, scenes, stories, villagers and scenarios are read per request; modules, `sl.json`/`sl.extra.json` and visit quests need a bridge (tutor session) restart.

## 3. How the app grades

**Typed answers** (`companion/android/app/src/main/java/si/lanisce/lani/data/Grading.kt`, tables in `app/src/main/resources/grading/<lang>.json`):

- Case, punctuation (`. , ! ? ; : ¡ ¿ " „ “ ” « »`) and extra spaces never matter. `/` is not punctuation.
- The language's `same` rules are the same answer (English: don't = do not). Otherwise nothing is guessed: no synonyms, no articles, no word order. Every right answer must be listed.
- Letters a keyboard may lack (`fold`: Slovene č/ć/š/ž/đ, Italian accents, German umlauts): almost. Italian apostrophes and elided articles (`loose`): almost.
- One word differs: a slip in its stem is almost (a typo: none under 4 letters, 1 from 4, 2 from 9); a change in its ending (the last letter up to 5 letters, the last 2 in longer words) is wrong, hint "ending". Anything more is wrong.
- `expected` is the listed answer closest to Jan's.

**Review cards** (`data/ReviewPlanner.kt`; `card.py show` says all of this per card):

- The front (`content`) is the answer; the back (`answer`) is the meaning, the prompt.
- Front: ` / ` (with spaces) separates alternatives, each accepted alone. `(…)` is optional: `hvala (lepa)` takes `hvala` and `hvala lepa`. `→ ♂ ♀ …` or `...` make a rule or placeholder: a flashcard only, self-rated. Alternatives over 40 letters are dropped.
- Back: `(…)` is a note. It shows on the flashcard only, never in a prompt or option (reviews and village challenges ask with the meaning alone).
- Variants (`source.variant`): `flip` (self-rated), `recognize` (front → pick the meaning), `listen`, `pick_slovene` (meaning → pick the front, shown as written), `tiles` (build the first alternative), `type` (any alternative), `dictation` (only the first alternative: it is what was spoken), `speak`.
- Each answer is one alternative. A back `yes / no` with a front `ja / ne` asks for both meanings and takes one word: nothing is right. Two cards with one meaning (`yes` on two cards) mark the other card's word wrong. `card.py overlaps` finds both.
- Quality of a right answer: 3 (flip, recognize), 4 (listen, pick_slovene, tiles), 5 (type, dictation, speak); almost 3; wrong 1; a hint lowers it. Village challenges: right 4, almost 3, wrong 1. A pack word's first practice: right 4, almost 3, wrong 2.
- `error_pattern` items are never shown as cards (their `content` is Jan's old wrong answer).

**Modules** (`ui/PlayerScreen.kt`, spec in `.claude/skills/lani-studio/reference/module-spec.md`): `choice` and `scenario` with options: exactly the `answer` index (0-based). `cloze`, `translate` (`grade: "match"`), `dictation`, `scenario` without options: typed against `accept`. `reorder`: one of `solutions`, in order, never almost. `multi`: all `answers` and no other = right; one missed or one extra = almost. `speak`: the recognizer's best take against `say` + `accept`, 3 tries. `free` and `translate` with `grade: "claude"`: you grade them.

**Dialog choices** (scenes and stories, `companion/SCENES.md`): a choice has the scene's language (`sl`) and `en`; `ok: true` marks a right one, and a turn may have two where people would accept either (each with its own `reply`). A wrong choice needs a `why`, and its `reply` is the person's reaction to what was said, taken at face value, never containing the right form. By voice, `ui/scene/ChoiceMatch.kt` picks the clearly closest choice, ignoring diacritics: two choices one letter apart can't be told apart by voice. Each wrong pick takes a quarter off the reward.

## 4. Fix it

1. **Snapshot:** `companion/bin/lani-backup run` (another learner: `lani-backup --profile <id> run`). `card.py` also copies each file it changes to `<data>/.backups/pre-card-<command>-<time>/`.
2. **Learner data:** only through `card.py` (or update-db.py). It keeps ids, review history and scheduling. Keep ids: a review still in the app is saved by id. Try a change with `--dry-run` first; give `--reason`.
3. **Content:** the publish tool for its kind (§2), same id. Validator errors are real: fix the spec.
4. **Check:** `card.py show <id>` has no `problems` and no `overlaps`; solve a changed exercise yourself from its prompt alone.

## 5. Take back what it cost

The item's fault must not count against Jan:

- **A review graded wrong:** `card.py regrade <id> --quality <q> [--date YYYY-MM-DD]`, with the quality a right answer gets on that variant (§3). It replays SM-2 over the card's history, so the card is where a right answer would have left it (no longer rusty). It refuses when the card's state doesn't follow from its history.
- **A pack word graded wrong while learned:** `card.py regrade <id> --quality 4` sets its first grade (it counts as learned).
- **A mistake you recorded** from a `session_end` (update-db.py `errors[]`): `card.py forgive <pattern_id> [--date D] [--answer TEXT]` removes that example; the last one removes the pattern and the review item it made.
- The session's score in progress-db and the session log stays. Never send the session to update-db.py again (the same session_id replaces the session). A village reward isn't given back.

## 6. Tell Jan, and note it for the developer

- **Reply** (the `reply` tool, the `conversation_id` of the chat): what was wrong and that it was the item's fault, not Jan's; what you changed; what you took back ("da is no longer rusty"); when they see it ("from your next review").
- **Curated content** (anything under `companion/`): append a dated entry to `<data dir>/app/wishlist.md`, as the other entries are:

  ```
  ## 2026-09-27 · Fix: <kind> <id>
  - **What:** <file and field>, what is wrong, the right text
  - **Why:** Jan's report, and how the app graded it
  - **Where:** companion/…; meanwhile <the override you published | not overridable: repo change (and app release)>
  ```

## Common fixes

- **Accept another answer.** Card: `card.py edit <id> --front "da / ja"` (an alternative) or `"hvala (lepa)"` (an optional word). Module: add it to `accept` (`solutions` for reorder, `answers` for multi), `publish_module`. Dialog: mark the choice `ok: true` with its own `reply` (a second right answer), `publish_scene`.
- **Split or merge overlapping cards.** `card.py overlaps`. One card per meaning: `card.py split <id> --front ne --back no --new-id vocab_ja --new-front ja --new-back yes`. One card for two: `card.py merge <keep> <drop>` (the kept card keeps its scheduling, the dropped one's history is kept inside it; keep the better-known one). Merge only when the fronts are one word's forms or synonyms.
- **Reword a confusing prompt.** Card: the back is the prompt, `card.py edit <id> --back "…"`; explain written vs spoken in a note, `--note "…"` (flashcard only). Module: `prompt`, `instruction`, `explain`. Never put the answer in a prompt.
- **Fix a wrong translation.** Card: `--back`. Pack word: a tutor pack `publish_pack`; a curated one: note it and `card.py edit vocab_<pack>_<word>`. Dictionary: `publish_gloss` (and `card.py edit vocab_word_<lemma>` if Jan added it). Villager or dialog line: `get_…`, fix the `en`, `publish_…`.
- **Fix a wrong "right" choice.** Module `choice`: the `answer` index or the options. Dialog: the `ok` flags; the wrong one gets a `why` and a reaction `reply`. Then `forgive` what the old key recorded.

## Example: da and ja

Jan, from a review: "I typed ja for yes and it was wrong, and ja / ne was wrong too." `about_exercise.source` is `{"kind": "review", "card_id": "vocab_da", "variant": "type"}`.

1. `card.py show vocab_da`: front `da`, accepted `["da"]`, overlaps `vocab_ne` (same prompt `yes`). `card.py show vocab_ne`: front `ja / ne`, back `yes / no`, problem: "asks for several meanings at once". Every answer is wrong on one of the two.
2. `companion/bin/lani-backup run`
3. `card.py edit vocab_da --front "da / ja" --note "da = written/standard, ja = spoken" --reason "ja was marked wrong"` and `card.py edit vocab_ne --front ne --back no --reason "yes / no asked for two words"`. `card.py overlaps`: none.
4. `card.py regrade vocab_da --quality 5 --reason "ja is right"` (a right `type` answer is 5): no longer rusty.
5. Reply: "You were right: ja is yes too (spoken; da is the written form). The card takes both now, and 'ne' has its own card. Your review counts as right."

Both cards are Jan's own data: no note for the developer.

## Critical Rules

- **Never hand-edit the learner's databases.** `card.py` for cards and mistakes, update-db.py for sessions.
- **Keep ids.** Reviews, packs and scenes refer to them.
- **Curated content: override and note, never edit it from the session.**
- **A mistake that was the item's fault never counts against Jan.**
- **Linguistic correctness first.** Accept only what is right; if unsure an answer is standard Slovene, say so and don't accept it.
