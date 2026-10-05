# lani.module/v0 — spec reference

Source of truth for validation: `companion/bridge/src/spec.ts`. Keep this file in sync with it.

## Module

| Field | Type | Notes |
|---|---|---|
| `schema` | `"lani.module/v0"` | required, literal |
| `id` | string | kebab-case, 2-63 chars. Stable across versions. |
| `title` | string | shown on the module card |
| `description` | string? | one or two sentences |
| `level` | `A1`…`C2` | CEFR level of the content |
| `targets` | string[] | `mistakes-db` pattern IDs this module drills |
| `tags` | string[] | free labels. `retired` hides the module. |
| `exercises` | Exercise[] | 1-50 items, played in order |
| `quest` | object? | makes the module a village sidequest in the game (see below) |

Don't set `version`. The bridge assigns it on publish.

## Exercise types

Every text field must be non-empty. `explain` (optional) appears after the answer.

**Layout fields** (optional, on `choice`, `cloze`, `reorder`, `translate`, `multi`, `dictation`, `speak`):
- `instruction`: what to do, shown small above the prompt, e.g. `"Kaj pomeni? · What does it mean?"`. With it, keep `prompt` to the bare subject (`"dober dan"`), which is shown large. Without it, `prompt` is a full question.
- `say` (not on `cloze`, `multi`, `dictation`; required on `speak`, see there): Slovene text behind a 🔊 button next to the prompt. Use it when the prompt is Slovene.
- `say_options` (`choice`, `multi`): a 🔊 on each option. Use it when the options are Slovene.
```json
{ "type": "choice", "instruction": "Kaj pomeni? · What does it mean?", "prompt": "Dober večer", "say": "Dober večer", "options": ["Good evening", "Good morning", "Goodbye"], "answer": 0 }
```

**Grammar page** (optional, on every type): `grammar`, the id of the grammar book's page of the rule the exercise
practises (`"kje-mestnik-orodnik"`; [grammar-spec.md](grammar-spec.md), `list_grammar`). Answering it unlocks the page in
the app, and its `explain` gets "📖 V knjigo · To the book". Tag every exercise of a grammar module.
```json
{ "type": "cloze", "grammar": "tozilnik", "text": "Pijem ___. (I drink coffee.)", "accept": ["kavo"], "explain": "kava → kavo: feminine -a → -o." }
```

### `flashcard` — see the front, recall the back, self-rate
```json
{ "type": "flashcard", "front": "dobro jutro", "back": "good morning", "note": "jutro is neuter → dobro", "speak": true }
```
`speak` (default `true`): the app reads `front` aloud with sl-SI TTS.

### `choice` — pick one
```json
{ "type": "choice", "prompt": "Your neighbour at 8:00 — you say…", "options": ["Dober jutro", "Dobro jutro", "Dobra jutro"], "answer": 1, "explain": "jutro is neuter → dobro" }
```
2-6 options. `answer` is the 0-based index. Make distractors plausible near-misses.
Optional `audio`: Slovene text the app plays with TTS (a listening exercise). Don't put it in the prompt too.
```json
{ "type": "choice", "prompt": "🎧 What did you hear?", "audio": "Na svidenje!", "options": ["Hello", "Goodbye", "Thanks"], "answer": 1 }
```

### `cloze` — fill the gap
```json
{ "type": "cloze", "text": "Moja partnerka ___ iz Gorice.", "accept": ["je"], "hint": "to be, 3rd person", "explain": "Slovene always needs the copula." }
```
`text` must contain `___`. `accept` lists every correct fill.

### `reorder` — tap tokens into order
```json
{ "type": "reorder", "prompt": "My name is {learner}.", "tokens": ["se", "{learner}.", "Imenujem"], "solutions": [["Imenujem", "se", "{learner}."]], "explain": "se sits in second position." }
```
2-12 tokens, plus up to 6 optional `distractors`: extra tiles that belong in no solution (like Duolingo's decoy words). Every solution must be a permutation of `tokens`, ignoring case: capitalisation follows position, so `["Tudi","jaz",…]` can have the solution `["Jaz","se","tudi",…]`. Write each solution with its correct capitalisation. The app compares orders case-insensitively and shows the solution's casing. List every valid order.

### `translate` — type the target sentence
```json
{ "type": "translate", "prompt": "I'm from Berlin.", "accept": ["Sem iz Berlina.", "Jaz sem iz Berlina."], "grade": "match" }
```
`grade`: `match` (default) means the app compares against `accept`. `claude` means the answer goes to the tutor session.

### `free` — open production, graded by the tutor
```json
{ "type": "free", "prompt": "Introduce yourself to your partner's neighbours (3 sentences).", "rubric": "Polite register (vi), imenujem se with correct clitic order, iz + genitive." }
```
Always graded by Claude via a `kind="answer"` channel event.

### `multi` — select all that apply
```json
{ "type": "multi", "prompt": "Which greetings are polite (vi)?", "options": ["Dober dan", "Živjo", "Dobro jutro", "Oj"], "answers": [0, 2], "explain": "živjo and oj are for friends." }
```
3-8 options, `answers` lists every correct index. All right and none wrong = correct. One miss or one extra = almost.

### `scenario` — a situation to respond to
```json
{ "type": "scenario", "scene": "🥖 Bakery in Nova Gorica, 8:00", "speaker": "Baker", "line": "Dober dan! Kaj bo?", "prompt": "Ask for bread, politely.", "accept": ["Kruh, prosim.", "En kruh, prosim."] }
```
`scene` sets the place (start with an emoji). `line` is what `speaker` says. The app shows it as a speech bubble and plays it with TTS. Then the learner answers `prompt` in one of two ways:
- typed: `accept` (match grading), or
- picked: `options` + `answer`.
Use exactly one of the two.

### `dictation` — hear it, write it
```json
{ "type": "dictation", "audio": "Moja partnerka je iz Gorice.", "accept": ["Moja partnerka je iz Gorice."], "explain": "c = ts, as in German z." }
```
The Slovene is only spoken (TTS), with normal and 🐢 slow replay. It's shown only if the phone has no Slovene voice. Trains listening and spelling. List only what's actually spoken in `accept`.

### `speak` — say it out loud
```json
{ "type": "speak", "say": "Dober dan, jaz sem Jan.", "prompt": "Greet the baker and introduce yourself." }
```
The learner reads `prompt`, can play `say` as a model (TTS, normal and 🐢 slow), then taps 🎤 and says it. The phone's speech recognizer (sl-SI) turns it into text. The app grades the best of the recognizer's alternatives against `say` and `accept` with the matching rules below, and highlights the words it heard differently. The learner gets 3 attempts, then the answer is shown. While `say` is hidden (`show: false`), 💡 reveals it step by step (its first letters, then its words) until it can be read aloud; each hint counts a little less (`hints_used` in the results), yet reading it aloud correctly still passes. After a miss, "Povej pravilno · Say it correctly" lets the learner say the model again, just for practice: the result stays.
- `accept` (optional): other valid ways to say it. `say` is always accepted.
- `show` (default `true`): `false` hides `say` (text and audio) until the answer is shown, so the learner produces it from the English `prompt` alone. Use `true` for "repeat after me", `false` for real production.
```json
{ "type": "speak", "instruction": "Povej po slovensko · Say it in Slovene", "prompt": "Ask for a loaf of bread, politely.", "say": "En kruh, prosim.", "accept": ["Kruh, prosim.", "Eno štruco kruha, prosim."], "show": false, "explain": "prosim = please; štruca = a loaf" }
```
Keep `say` short (one sentence, what a person says in one breath). Recognizers write numbers as digits and drop punctuation, so avoid numbers in `say`. If the phone has no Slovene recognizer, the learner says it anyway and rates it themselves, so the exercise never blocks. Pronunciation isn't scored: a recognized sentence counts as correct.

## Village sidequests (`quest`)

With a `quest` block, the module appears in the game *Moja vas* as a villager's request, and playing it pays the reward into the village.
```json
{ "giver": "Babica Micka", "emoji": "👵", "story": "Micka is expecting guests from Gorica and forgot how to greet them politely.", "skill": "food", "reward": { "food": 40, "wisdom": 10 } }
```
- `giver` and `emoji`: a villager. Reuse the village's characters (`companion/GAME.md`) or invent a fitting Slovene one.
- `story`: 1-2 sentences, in English with Slovene flavour. It says why the villager needs this, and should match the exercises. The giver and anyone it names should be in `get_village`'s `people.known` (in the village and met): the app keeps a request that names someone else until they are.
- `skill`: which resource the practice feeds: `food` (vocabulary), `wood` (listening), `stone` (grammar), `wisdom` (conversation and writing).
- `reward`: resource → amount, 0-500 each. Stay in the ranges in `companion/GAME.md`. Tutor quests may pay a bit more than generated ones, because they're tailored.
- `helps` (optional): the module id of an open task at the same giver that this module is a smaller drill for. The app keeps that task waiting behind the drill ("⏸️ Najprej · First: <drill>") and brings it back once the drill is passed. Kebab-case like a module id; never the module's own id (refused). `publish_module` notes it when the task isn't published or is another villager's. Tag such a drill `short`: an older bridge drops `helps`, and the app then finds the link by a grammar page both modules share.
```json
{ "giver": "Kovač Tone", "emoji": "⚒️", "story": "Tone gre na tržnico, v Gorico in zvečer v gostilno. Povej mu, kam gre. · Tone is going to the market, to Gorizia and to the inn. Tell him where he's going.", "skill": "stone", "reward": { "stone": 70, "wisdom": 10 }, "helps": "predlogi-kraja" }
```

A villager shows at most two open requests at once: their own first, then the tutor's newest; the rest wait in the app's Scroll under "Kasneje · Later". Spread quests over the cast rather than piling them on one villager.

## Word packs (`lani.pack/v0`)

New vocabulary, not drills. Validator: `companion/bridge/src/packs.ts`. Curated packs live in `companion/packs/<id>.json` (Slovene), `companion/packs/<language>/<id>.json` (another language: `packs/it/`) and a culture pack's `packs/` (its festivals' words); a bridge serves the ones of its village's language and culture. `publish_pack` writes tutor packs to `<data>/app/packs/` (same id = replace; curated ids are refused).

| Field | Type | Notes |
|---|---|---|
| `schema` | `"lani.pack/v0"` | required, literal |
| `id` | string | kebab-case, 2-63 chars |
| `language` | `sl` \| `it` \| `en` \| `de` | the language of the words (what is learned); `sl` when missing. Write the village's language (the bridge's instructions say it) |
| `title` | string \| text | "V kuhinji · In the kitchen" (target · English), or per language `{"it": "La famiglia", "sl": "Družina", "en": "Family"}` |
| `emoji` | string | pack icon (default 📚) |
| `level` | `A1`…`C2` | |
| `description` | string? \| text | one sentence, shown on the card and by the giver: English, or per language `{"sl", "en"}` |
| `review` | string? | who wrote which languages and who checked them: `"de, it: machine-written, not reviewed by a native speaker"` |
| `giver` | `{name, emoji}`? | the villager who teaches it |
| `festival` | string? | a calendar festival's id (`martinovo`, `novo_leto`; the app's `game/Calendar.kt`): its words. Only the curated festival packs `companion/packs/praznik-<festival>.json` (and a culture pack's `festa-*.json`) set it: the app bundles them, the festival's run asks their words and records the answers, the packs screen lists them under "🎉 Prazniki · Feasts". Don't set it in tutor packs |
| `words` | Word[] | 8-24 |

Word: `id` (kebab-case, unique in the pack), the word in the pack's language (`sl`, or `it` …: what is learned and typed; a word or short phrase, ≤ 60 chars), `en` (always), the learner's base language if it isn't English (`sl` for a learner of Italian from Slovene, `de` for one from German), and optional `emoji`, `gender` (`m`/`f`/`n`, nouns), `plural`, an example in each language (`example_sl`, `example_it`, `example_de`, `example_en`), `note` (English) and a note in each other base language (`note_sl`, `note_it`, `note_de`).

```json
{ "id": "kruh", "sl": "kruh", "en": "bread", "emoji": "🍞", "gender": "m", "plural": "kruhi",
  "example_sl": "Kruh je še topel.", "example_en": "The bread is still warm." }
{ "id": "mamma", "it": "la mamma", "sl": "mama", "en": "mum", "emoji": "👩", "gender": "f", "plural": "le mamme",
  "example_it": "La mamma è in cucina.", "example_sl": "Mama je v kuhinji.", "example_en": "Mum is in the kitchen." }
```

Rules: Slovene words in their dictionary form (nominative singular, infinitive), no articles; Italian nouns with their definite article (`la mamma`, `l'uovo`, `lo zaino`), and the plural with it (`le uova`); no slashes or brackets (it is typed in practice); one meaning per word; examples short and at the pack's level. Each learned word becomes SR item `vocab_<pack-id>_<word-id>` (content = the word in the pack's language, answer = its meaning in the learner's base, category = pack id).

## Role-play scenarios (`lani.scenario/v0`)

A scene for the app's "Pogovor · Talk": you play a character, Jan speaks (voice or typing). Validator: `companion/bridge/src/scenarios.ts`. Curated scenarios live in `companion/scenarios/<id>.json`; `publish_scenario` writes tutor ones to `<data>/app/scenarios/` (same id = replace; curated ids are refused).

| Field | Type | Notes |
|---|---|---|
| `schema` | `"lani.scenario/v0"` | required, literal |
| `id` | string | kebab-case, 2-63 chars |
| `title` | string | "Na pošti · At the post office" |
| `emoji` | string | scene icon (default 🗣️) |
| `level` | `A1`…`C2` | how you speak in character |
| `setting` | string | 1-2 sentences, English |
| `role` | string | who you play. **Starts with the character's name, then a comma** (the app shows the part before the comma): "Uslužbenka Mojca, the clerk at the counter. Patient, says vi." Say ti or vi. |
| `voice` | `female` \| `male` | the character's gender: how they sound in the app (the node's voice store) when no `speaker` is set; default `female`. Set `male` for a male character. |
| `speaker` | string? | a voice from the voice cast (`companion/voice-cast.json`: `grandma`, `grandpa`, `young-man`, `gruff`, `woman`, `teacher`, `young-woman`, `girl`, `boy`) for the character's lines; of the same gender as `voice`. Leave it out for the narrator's voice. |
| `goals` | string[] | 3-5 short things Jan should manage to say, in English ("Greet politely") |
| `opener_sl`, `opener_en` | string | the character's first line (shown and spoken by the app) and its translation |
| `vocabulary_hints` | `{sl, en}[]` | 0-12 phrases for the intro, each with 🔊 |

```json
{ "schema": "lani.scenario/v0", "id": "na-posti", "title": "Na pošti · At the post office", "emoji": "📮", "level": "A1",
  "setting": "The post office in Nova Gorica. You want to send a parcel to Germany.",
  "role": "Uslužbenka Mojca, the clerk at the counter. Patient and friendly, says vi.", "voice": "female",
  "goals": ["Greet her", "Say you want to send a parcel", "Say where it goes", "Ask how much it costs", "Say goodbye"],
  "opener_sl": "Dober dan! Izvolite?", "opener_en": "Hello! How can I help?",
  "vocabulary_hints": [{ "sl": "Rad bi poslal paket.", "en": "I'd like to send a parcel." }, { "sl": "v Nemčijo", "en": "to Germany" }, { "sl": "Koliko stane?", "en": "How much is it?" }] }
```

Rules: one real situation from the learner's life (Primorska, family, shops, offices); the opener is a question or invitation the learner can answer with the hints; goals in the order the scene naturally takes; every Slovene line simple and correct at `level`.

**Playing it** (kind `roleplay` on the channel, `conversation_id` `rp-…`): stay in character, 1-2 short sentences of simple Slovene per turn, and reply with the line as `text` and `data: {"sl", "en", "correction"?, "goals_done", "end"?}`. `heard` comes from a speech recognizer: forgive missing punctuation, capitals and a misheard word; correct only real mistakes (wrong ending, word order, wrong word), gently, as "Rekli bi: **…** · why in English". `goals_done` lists all goal indices (0-based) reached so far; set `end: true` when the scene closes naturally (after the goodbye, or once all goals are done). For `data.hint: true` reply `{"hint": true, "sl", "en"}`: one line the learner could say next, towards the next open goal. On `roleplay_end`, step out of the role: 3-5 lines of debrief (what went well, 2 things to practise, with the correct Slovene) with `data: {"debrief": true}`, then persist with lani-db-updater: `skills_practiced: ["speaking"]`, `skill_scores.speaking` = turns and turns without correction, one `errors[]` entry per real mistake, `command_used: "/lani-app-talk"`. The app has already paid the village; don't mention resources.

## Scenes (`lani.scene/v0`)

A close-up of one place in the village ("Moja vas"): the campfire, the forest, a kitchen, a room of a house (a living room, a workshop, a wine cellar, an attic), the square, the stream, the pond, the church, the bee house, the market, the view from the watchtower. Its objects teach pack words (tap → word, 🔊, gender, example; learning them counts like learning the pack), and its **happenings** put a villager there at some times, on some weekdays, by chance, with a short **dialog** of Slovene choices the learner answers. Validator: `companion/bridge/src/scenes.ts`; the full description and the art registry (arts, slots, person spots, sprites): `companion/SCENES.md`. Curated scenes live in `companion/scenes/<id>.json`, and the village's culture pack brings its own places in `companion/cultures/<culture>/scenes/` (`list_scenes` shows them with their `culture`; its landscape at the horizon opens from `spot:horizon`, a place a village project leads into from `project:<id>`, like Primorska's vineyard from its terraces); `publish_scene` writes tutor ones to `<data>/app/scenes/` (same id = replace; a tutor scene with a curated id replaces the curated one until `remove_scene`).

| Field | Type | Notes |
|---|---|---|
| `schema` | `"lani.scene/v0"` | required, literal |
| `id` | string | kebab-case, 2-63 chars |
| `language` | `sl` \| `it` \| `de` \| `en`? | the language the lines are in: the village's (default `sl`). In another language every line, choice, reply and memory has that language and `en` plus the learner's base: `{"who": "rosa", "it": "Hai fame?", "sl": "Si lačen?", "en": "Are you hungry?"}`; a `why` may be `{"sl": …, "en": …}` |
| `title` | string or per language | "Ob ognju · At the campfire", or `{"it": "Il mare", "sl": "Morje", "en": "The sea"}` (a happening's too) |
| `emoji`, `level` | string, `A1`…`C2` | scene icon (default 📍), level of the Slovene (default A1) |
| `review` | string? | who wrote which languages and who checked them: `"de, it: machine-written, not reviewed by a native speaker"` |
| `art` | `campfire` \| `kitchen` \| `livingroom` \| `workshop` \| `cellar` \| `attic` \| `forest` \| `square` \| `tent` \| `field` \| `smithy` \| `school` \| `hills` \| `sea` \| `alps` \| `stream` \| `pond` \| `church` \| `apiary` \| `market` \| `watchtower` | the painter; decides the slots and person spots (`hills`, `sea` and `alps` are the culture packs' landscapes: Primorska's vineyard and its mountains, Friuli's lagoon and sea; `livingroom`, `workshop`, `cellar` and `attic` are rooms of a house) |
| `from` | string[] | where the village opens it: `fire`, `forest`, a spot of the landscape (`spot:meadow`, `spot:pond`, `spot:highseat`, `spot:path`, `spot:woodpile`, `spot:rocks`, `spot:riverbank`, `spot:road`, `spot:kopa`: the charcoal pile deep in the woods, on a phone held upright only, `spot:horizon`: the land at the horizon, what the culture pack has there) or a building type in lower case (`tent`, `hut`, `house`, `lipa`, `smithy`, `school`, `church`, `beehive`, `well`, `market`, `watchtower`, …), or `project:<id>`: a village project's landmark, there once the project is finished (the id one of the culture's `projects.json`). 1-6 entries; the first that exists shows its bubbles |
| `needs` | age? | earliest age: `ogenj`, `tabor`, `zaselek`, `vas`, `trg`, `mesto` |
| `household` | string[]? | only a room of a house (`from: ["house"]`): who lives there, villager ids of the cast (`["marko"]`). A house opens one room, its own: the houses get the rooms of the households living in the village, in the order they moved in (SCENES.md, "Houses and their rooms"); a room without one is anyone's (the kitchen). A new room for a house: give it a household and its people |
| `pack` | string? | the pack the objects' words come from |
| `objects` | `{slot, word, pack?, needs?}[]` | at most 30; each `slot` one of the art's (each at most once), `word` a word id in `pack` (or the object's own `pack`). The bridge serves them resolved (`sl`, `en`, `emoji`, `gender`, `plural`, `example_sl`, `example_en`). `needs`: what the village must have built for the thing to be there, `{"building": "KOZOLEC"}` (a building type; `"level": 2`: one at least at that level, the one the scene is opened from when it is of that type), `{"level": 3}` alone (the place's own: the hut or house the scene is opened from, at least at that level; 1-5), `{"project": "mlin"}` (a village project finished; `"step": 2`: that far), `"before": true` for the other way round; the painters draw some things only when built (the kozolec and its hay, the tent at the fire, the well and the church on the square, France's mill, what a room gets with its level: the kitchen's stove and table from level 2, its clock from 3, its hearth and straw bed only at 1; the living room's lamp and clock from 2, its photo from 3; the cellar's bottles from 2, its pršut from 3), so give those slots the same `needs` (SCENES.md, "What the village has built") |
| `people` | `{id, name, emoji, art, slot, always?, villager?}[]` | at most 8; `art` a sprite (`grandma`, `grandpa`, `child1`-`child3`, `shepherd`, `smith`, `teacher`, `farmer`, `beekeeper`, `innkeeper`, `winemaker`, `aunt`, `woman`, `man`, `baby`, `hunter`), `slot` one of the art's person spots; `always` = there all day; `villager` = who this is in the cast (a villager id, see Villagers), so the friendship grows here and the lines are voiced in their voice |
| `happenings` | object[] | at most 12: `id`, `title` ("Slovene · English"), `who` (a person id), `when` (`morning` 5-11, `afternoon` 11-17, `evening` 17-22, `night` 22-5, `dawn`: the morning's first two or three hours round the month's sunrise, 5-7 in June, 6-9 in December, when the hunter watches from his high seat and France mows in the dew; empty = all day), `weekdays` (1 = Monday … 7), `chance` (0-1, per day), `dialog` (a dialog id), `reward` (`food`, `wood`, `stone`, `wisdom` → 0-300, paid once a day when the dialog is done), `marker` (emoji for the village bubble), `needs` (the earliest age, `"vas"`, or what the village must have built, as an object's: Luka brings in the hay only where a kozolec stands, `{"building": "KOZOLEC"}`; France talks of the mill he wants, `{"project": "mlin", "before": true}`), `memory` (`{sl, en}`: what the person remembers afterwards, in their words, fitting "Še vedno mislim na …": `{"sl": "juho, ki sva jo jedla ob ognju", "en": "the soup we ate by the fire"}`) |
| `dialogs` | object[] | at most 12: `id`, `talk` (a scenario id to go on with the tutor), `lines` (3-8). A line is someone's (`who`, `sl`, `en`) or the learner's turn (`choices`: 2-3, one `ok` or two where people would accept either, at least one wrong; a right one may have the other person's `reply {sl, en}`, a wrong one has its `why` and their reaction as its `reply`; a wrong one that differs from the right one in one word tests a form and may name the grammar page of its rule, `grammar: "dvojina"`, else the app guesses it from the why; the turn may have a `puzzled {sl, en}`: the reaction to a wrong answer without its own, the learner's own trap or a form typed wrong, else "Hm? Kako, prosim?"; companion/SCENES.md "Adaptive turns", "Own traps"). The first line is someone's. Every `sl`/`en`/`why` at most 200 chars. A line or a reply may change the weather when it plays: `sky {rain, snow, fog, wind, gloom: 0-1, lightning: bool}` (only what changes; it eases in over a few seconds), and the dialog's `sky_stays` (default true with a cue) keeps the sky it ends with over the scene and the village until midnight. Use it where the text says so ("Dež gre!" → gloom, then rain; "Megla je." → fog; "Poglej, sonce!" → rain 0, gloom 0). Likewise `fx {name: 0-1 or true/false}` sets the art's effects (campfire `flare`, `steam`, `bell` (a distant bell); kitchen `oven`, `steam`; living room `stove`, `dog`; workshop `dust`; cellar `tap`, `candle`; attic `lamp`, `cradle`; forest `birds`, `herd` (a stag and two hinds step out of the trees), `fox`, `hoot` (the owl), `bats`; square `bell`; tent `lantern`; field `crows`, `bell` (the church bell from the village), `bats`; smithy `forge`, `sparks`; hills `bell`, `tractor`, `starlings`; sea `waves`, `gulls`, `sail`; alps `cowbells`, `bell`, `fish`, `birds`, `glow` (the peaks pink at dawn); stream `trout`, `wheel`, `bats`, `bell`; pond `ducks`, `frog`, `fish`, `bats`; church `candles`, `bell`, `organ`; apiary `swarm`, `smoke`, `honey`; market `bustle`, `bell`, `pigeons`; watchtower `torch`, `horn`, `birds`, `wolves`; SCENES.md "Arts"): "Pečem potico" → `{"oven": 1}`, "zvoni!" → `{"bell": 1}`. They go with the dialog unless it says `"fx_stays": true`. In the living room and the tent, `act` moves people and a turn's choices may each `tap` a place in the picture (Stage directions and tap turns, below) |

```json
{ "schema": "lani.scene/v0", "id": "ob-ognju-jesen", "title": "Jesen ob ognju · Autumn at the campfire", "emoji": "🍂", "level": "A1",
  "art": "campfire", "from": ["fire"], "pack": "ob-ognju",
  "objects": [{ "slot": "fire", "word": "ogenj" }, { "slot": "logs", "word": "drva" }, { "slot": "kettle", "word": "kotlicek" }, { "slot": "stump", "word": "stor" }],
  "people": [{ "id": "luka", "name": "Pastir Luka", "emoji": "🐑", "art": "shepherd", "slot": "back", "villager": "luka" }],
  "happenings": [{ "id": "kostanj", "title": "Luka peče kostanj · Luka is roasting chestnuts", "who": "luka", "when": ["evening"], "weekdays": [5, 6, 7], "chance": 0.5, "dialog": "kostanj", "reward": { "food": 15 }, "marker": "🌰",
    "memory": { "sl": "kostanj, ki sva ga pekla ob ognju", "en": "the chestnuts we roasted by the fire" } }],
  "dialogs": [{ "id": "kostanj", "lines": [
    { "who": "luka", "sl": "{learner}! Pečem kostanj. Hočeš?", "en": "{learner}! I am roasting chestnuts. Want some?" },
    { "choices": [
      { "sl": "Ja, prosim. Dva, tri.", "en": "Yes, please. Two, three.", "ok": true, "reply": { "sl": "Izvoli. Vroči so!", "en": "Here you are. They are hot!" } },
      { "sl": "Ja, prosim. Dve, tri.", "en": "Yes, please. Two, three.", "why": "Kostanj is masculine: dva kostanja. Dve is for feminine and neuter nouns (dve jabolki).",
        "reply": { "sl": "Dve? Dve hruški? Ej, to so kostanji!", "en": "Dve? Two pears? Hey, these are chestnuts!" } } ] },
    { "who": "luka", "sl": "Jeseni je kostanj najboljši. Dober tek!", "en": "In autumn chestnuts are best. Enjoy!" },
    { "choices": [
      { "sl": "Hvala lepa!", "en": "Thank you very much!", "ok": true, "reply": { "sl": "Ni za kaj.", "en": "You're welcome." } },
      { "sl": "Hvala, stari!", "en": "Thanks, mate!", "ok": true, "reply": { "sl": "Ha ha, ni panike, stari!", "en": "Ha ha, no worries, mate!" } },
      { "sl": "Na zdravje!", "en": "Cheers!", "why": "Na zdravje is for a toast or a sneeze. For food you say hvala.",
        "reply": { "sl": "Na zdravje? Saj ne pijeva, jeva!", "en": "Cheers? We're not drinking, we're eating!" } } ] } ] }] }
```

A happening with `"stories": true` (and no `dialog`) is the storyteller's: its person (who needs a `villager`) tells tonight's story, the next one of theirs the learner hasn't heard (see Stories below). Stari Janez's evening at the campfire is one.

Rules: every object's word must exist in the named pack (`list_packs` shows the ids; a scene for new words needs its pack published first). The dialog's Slovene is correct and natural at the scene's level; the learner is `{learner}`, and their own lines and what is said to them agree with them as gender pairs (`Sem {m:lačen|f:lačna}.`, `Si {m:videl|f:videla} …?`; see "The learner in the content" below). Each choice turn teaches one thing (the greeting for the time of day, the case after a preposition, ti/vi with older people, the dual, an adverb vs an adjective…): the wrong choices are wrong for a reason the `why` explains in one sentence, never a trap on the learner's gender. Every wrong choice also gets a `reply`: the villager's reaction to what the learner actually said, taken at face value, in their voice, kind and a little funny, never mocking: "Ja, zelo sem žejen." where the learner meant hungry → Micka: "Žejen? Tu imaš vodo."; "Deset jajce, prosim." → "Samo eno jajce? Deset? Ne razumem …"; ti to an old man → "Tikaš me? No, prav …"; a slip with no funny reading → "Hm? Kako, prosim?". One short sentence at the scene's level, in every language the choice has. **Never the right form in it** (not "Deset jajc? Seveda.": the learner must still find it). It may carry an `fx` of the art (it passes when the learner picks again), never a `sky`. Where people would accept more than one answer (a greeting, a goodbye, thanks, a yes or a no, a question: "Adijo!" and "Čav!", "Hvala!" and "Hvala lepa!"), give the turn two right choices, each with its own reply (polite answered politely, cheeky cheekily) and the same cues, and keep a wrong one; a turn that tests a form (the choices differ in one ending) has one right answer. A handful per scene, where it is natural. Keep the villagers as their files draw them (`list_villagers`: Micka feeds, Janez tells, Mojca loves the dual), and name only people who are in the village and whom Jan has met (`get_village`: `people.known`; a happening whose dialog, title or memory names anyone else waits in the app until they are, and `publish_scene` says which), give every person their `villager` and every happening a `memory`, the tone warm and a little funny, Primorska where it fits (mark regional words: "čav: Primorska for bye"). Rewards: 15-30 of one resource, like a short village quest; the app pays them itself and reports learned words as `words_learned` (already persisted). `Happenings.active` in the app decides what is on: at most four at once and one per person, so give people in the same spot different times of day.

**Rules not yet.** Write the dialog natural at the scene's level; forms above the learner's level are fine. The app asks the learner only for the rules introduced to them: a turn whose form is of a page above their level that they haven't been introduced to loses that form's wrong choices (they choose about meaning, or hear the right line and repeat it: an echo, nothing graded), and each such turn passed meets the rule; enough meetings make it ripe for you to introduce, then open its page (grammar-spec.md, "Rules not yet"; companion/SCENES.md). So name every form choice's page (`grammar`): gating goes by it. A wrong choice about meaning (other words, or a word of no rule) stays for a learner at any level: give a form turn one where it is natural.

**Variants.** A happening may list several dialogs, `"dialogs": ["ovce-stejem", "ovce-jagnje", "ovce"]` (ids of the scene's `dialogs` or of its `variants`, a second list of dialogs the curated files use so an older bridge, which reads at most 12 dialogs, reads them as before): the same situation, different lines and another point of grammar. The app plays one not heard yet (the first in the list), then, once all are heard, the one heard longest ago if that was three days ago or more and it isn't the one heard last; else the happening rests that day. `dialog` stays what an older app plays (never a counting one). A variant may have its own `memory`. `publish_dialog_variant` adds one to a happening of any scene (curated too) without publishing the scene: `{scene, happening, dialog}`, validated with the scene; it is served with `"source": "tutor"`, and `remove_dialog_variant` takes it back. The bridge sends `dialog_variants_heard` when the learner has heard all of a happening's dialogs (at most three a day).

**Stage directions and tap turns.** In an art with a stage (the living room, the tent; the table below) a line or a reply can move the scene's people: `"act": {"zala": {"to": "behind-door", "pose": "hide"}}` (for each person of the scene it names, `to`: a spot of the stage, standing there unless it says a `pose`; `pose` alone: taken where they are). It plays like `sky` and `fx` (a line's when said, a right choice's reply's when it follows, a reaction's passes with the next pick: Zala peeks out at a wrong guess, `{"zala": {"pose": "peek"}}`); the app walks them there (1-2 s), has them dash into hiding (a puff, and gone: only a clue shows, and they can't be tapped) or pop out of it. When the dialog closes they go back to their spots, unless it says `"act_stays": true` (then until midnight, and they stay in the scene: Luka by the lantern while it rains). A learner's turn can be answered by tapping the picture: give each choice a `tap`, the place it stands for (a spot of the stage, an object slot of the art, or a person id of the scene; the thing that hides a spot counts as the spot, the door for `behind-door`); a tap there picks that choice, graded as a pick, and the choices wait behind "✋ Izberi raje". 3 places is good (4 at most; a bridge from before tap turns skips a scene with 4). The language point is where someone is (kje: za vrati, pod mizo, za pečjo): name `kje-mestnik-orodnik` on the right choice only (a wrong place is no slip on the rule), and give every wrong place a playful reaction ("Tu me ni! Hi hi!", "Mrzlo, mrzlo!"), a `peek` as a hint where someone hides. The bridge checks the people, spots and poses as the dialog goes. Full rules: `companion/SCENES.md`, "Stage directions" and "Tap turns".

| Art | Spots (poses) | Hiding places (what shows of someone there; the thing that hides it) |
|---|---|---|
| `livingroom` | `stove`, `table`, `door` (stand); `bench` (sit, on the zapeček); `under-table`, `behind-door` (stand, hide, peek) | under the table: their shoes under the cloth (the `table`); behind the door, which stands open: a braid or a sleeve past it, the shoes under it (the `door`) |
| `tent` | `left`, `right`, `door` (stand); `lantern` (sit, stand: on the bedroll behind the table, under the roof, out of the rain) | none |

```json
{ "id": "skrivalnice-smoke", "lines": [
  { "who": "zala", "sl": "{learner}! Se greva skrivalnice? Ti mižiš, jaz se skrijem!", "en": "{learner}! Shall we play hide-and-seek? You close your eyes, I'll hide!" },
  { "choices": [
    { "sl": "Prav! … Osem, devet, deset! Grem iskat!", "en": "OK! … Eight, nine, ten! Here I come!", "ok": true,
      "reply": { "sl": "Skrila sem se! Kje sem?", "en": "I've hidden! Where am I?", "act": { "zala": { "to": "behind-door", "pose": "hide" } } } },
    { "sl": "Prav! … Osem, devet, deset! Greš iskat!", "en": "OK! … Eight, nine, ten! You're coming!", "grammar": "glagoli-sedanjik", "why": "You are the one who looks: grem (I go).",
      "reply": { "sl": "Jaz? Ne, ne, ti iščeš!", "en": "Me? No, no, you're looking!" } } ] },
  { "choices": [
    { "sl": "Aha, za vrati si!", "en": "Aha, you're behind the door!", "ok": true, "tap": "behind-door", "grammar": "orodnik",
      "reply": { "sl": "Našel si me!", "en": "You found me!", "act": { "zala": { "to": "door" } } } },
    { "sl": "Aha, pod mizo si!", "en": "Aha, you're under the table!", "tap": "under-table", "why": "Look for a clue: something of hers shows.",
      "reply": { "sl": "Tu me ni! Hi hi!", "en": "I'm not here! Hee hee!", "act": { "zala": { "pose": "peek" } } } },
    { "sl": "Aha, za pečjo si!", "en": "Aha, you're behind the stove!", "tap": "stove", "why": "Look for a braid or a shoe.",
      "reply": { "sl": "Mrzlo, mrzlo!", "en": "Cold, cold!" } } ] },
  { "who": "zala", "sl": "Še enkrat!", "en": "Once more!" } ] }
```

**Numbers from the village.** A dialog that counts something says `"count": {"from": "dice", "min": 12, "max": 24, "gender": "f"}` (`from`: `dice`, the day's dice between min and max, or what the village has: `food`, `wood`, `stone`, `wisdom` with `per`, `villagers`, `building:<type>`; `gender` of the counted noun in the scene's language, `case: "acc"` for an object, `animate` for a Slovene masculine that lives). Its texts carry `{n}` (the number: written out where it is said, in figures in the translations) and the forms that agree: `{ovca|ovci|ovce|ovc}` for Slovene's 1, 2, 3–4, 5+ (by the last two figures: 101 like 1), `{Schaf|Schafe}` in two forms for one and more; verbs too (`{je|sta|so|je}`). The learner says the number phrase; a wrong choice with `"wrong_form": 1` (or 2) takes the wrong form learners put there (for 5+ the 3–4 form, then the singular; for 2 the 3–4 form, then 5+; for 3–4 the 5+ form, then the dual; for 1 the 3–4 form, then the dual), only its forms marked `!` (`{!ovca|ovci|ovce|ovc}`) when some are. Its `why` explains with forms (`"{One takes the singular|Two take the dual|Three and four take the plural|From five on, the genitive plural}: {n} {ovca|ovci|ovce|ovc}."`), its reaction has none. Give a Slovene counting turn `wrong_form` 1 and 2; in two forms, `wrong_form` 1 and a plain wrong choice (and no noun whose plural is its singular: sheep). Full rules: `companion/SCENES.md`, "Variants" and "Numbers".

## Stories (`lani.story/v0`)

The village's storyteller (the cast's Storyteller: Stari Janez at the campfire, Nonno Bepi in the Italian village) tells one story every evening: the next one the learner hasn't heard, at their level (A1 → the A1 telling, A2 → A2, higher → the highest the story has), a long legend in chapters, one an evening; once all are heard, he retells them a level up. A story is a scene dialog per level: his narration in short parts and the learner's turns between them (what happened, what they think of it). Validator: `companion/bridge/src/stories.ts`; the full description: `companion/SCENES.md`, "Stories". The curated ones are the culture pack's (`companion/cultures/<culture>/stories/<id>.json`, at A1 and A2 at least, some at B1); `publish_story` writes tutor ones to `<data>/app/stories/` (same id = replace; a curated id is replaced until `remove_story`). `list_stories` shows the collection in the rotation's order with what the learner has heard (`heard.told`, `heard.chapter`), their level and `all_heard`.

| Field | Type | Notes |
|---|---|---|
| `schema` | `"lani.story/v0"` | required, literal |
| `id` | string | kebab-case, 2-63 chars |
| `language` | `sl` \| `it` \| … | the village's language (default `sl`): every text in it and `en` (an Italian story also `sl`, the second learner's base) |
| `title` | string or per language | "Zlatorog · Goldhorn", or `{"sl": "Zlatorog", "en": "Goldhorn"}` |
| `teller` | villager id | the storyteller (`janez`; his `role` is the Storyteller) |
| `emoji`, `order`, `about` | string, int, `{en}` | its icon (default 📖), its place in the rotation (the curated ones; the tutor's come after them, oldest first), a note on where the legend comes from |
| `teaser` | `{sl, en}` | what he tells, a whole sentence for "Jutri: …" and "Nocoj ob ognju: …": `{"sl": "Janez pove zgodbo o Zlatorogu", "en": "Janez tells the story of Zlatorog"}` |
| `memory` | `{sl, en}` | what he remembers afterwards, fitting "Še vedno mislim na …" (accusative): `{"sl": "zgodbo o Zlatorogu, ki sem ti jo povedal", "en": "the story of Zlatorog I told you"}` |
| `pack`, `words` | string, `(id \| {word, pack})[]` | the words it teaches (at most 24): ids in `pack`, or `{"word": "gora", "pack": "v-gorah"}`; the story's end shows them with a button to learn them |
| `levels` | `{A1?, A2?, B1?, …: {lines, sky_stays?, fx_stays?}}` | told in one evening: a telling per level, each 3-16 dialog lines (as a scene dialog's: his lines without `who`, the learner's turns with `choices`) |
| `chapters` | `{title?, teaser, levels}[]` | instead of `levels`: 2-8 evenings, in order, each with its own `teaser` ("Janez pove, kako je Krpan srečal cesarja") and the same levels |
| `continues` | story id | optional: the story it goes on from (a new chapter or a sequel of one the learner liked, "Martin Krpan se vrne"): the same teller's, never in a circle; it is told after that one, and its notebook entry joins that one's |
| `pictures` | `{chapter?, after, bg, figures?, props?, night?, caption}[]` | its pictures, 2-4 (in chapters one or two an evening), sketched in the notebook: in evening `chapter` (from 1; default 1), `after` the telling's nth paragraph (the teller's lines between two of the learner's turns; 0: at the head), a background `bg`, up to 6 `figures` and 8 `props` from the vignette library (`list_stories`: `picture_library`), each `{id, x: 0-1 across, y?: 0-1 up from the ground, size?: 0.5-2, flip?: facing left, gold?: gilded}`, `night?` (the sky dark), and a `caption` `{sl, en}` (the story's language and English, and its title's other languages) |
| `notes` | `{chapter?, level, after?, text}[]` | what the learner would have written down of each telling, for their story notebook: evening `chapter` (from 1) told at `level`, `text` its paragraphs `{sl, en}` (the story's language and English, and the others its lines have): the story as prose in the past tense (A1 too: je živel, je prišel), no asides to the listener, no greetings or questions, none of the learner's turns, but every beat the turns and replies carry; short, clear sentences at the telling's level; `after` the paragraph each of the evening's pictures follows (0: at the head). One per telling; without them the notebook writes the telling down itself (his lines without the asides) |
| `review` | string | optional, for writers: who wrote what, "notes: machine-written (Claude) in sl and en; not reviewed by a native speaker" |

The pictures are drawn from the library (companion/SCENES.md, "The story notebook"; the notebook shows them as pencil sketches): backgrounds `mountains`, `lake`, `castle`, `village`, `forest`, `church`, `sea`, `cave`, `river`, `bridge` (things at y 0 stand on its deck), `meadow`, `town`; figures `king`, `girl`, `woman`, `boy`, `old-man`, `peasant`, `hunter`, `knight`, `soldier`, `giant`, `fairy`, `water-man`, `devil`, `goblin`, `dragon`, `chamois` (with `gold`: Zlatorog), `horse`, `ox`, `boar`, `dog`, `cat`, `bird`, `boat`, `sheep`; props `crown`, `sword`, `bell`, `tree`, `spruce`, `cloud`, `sun`, `moon`, `stars`, `flower`, `rock`, `barrel`, `chest`, `sack`, `club`, `table`, `fire`, `house`, `chapel` (a church on an islet: on the lake at y 0.3, Bled), `wall`. A picture shows the paragraph before it: who is there and where, two or three things, a caption of one short sentence at the story's level. Things in the sky (a cloud, the moon, a bird in flight) stand high (y 0.7-0.9); people in front stand at y 0; a figure up a mountain or on a crag at y 0.3-0.5.

His lines are his alone (leave `who` out), short parts in his register (Janez: "veš, fant", "nekoč", "poslušaj", a proverb now and then; ti to Jan, vi expected back); every telling has at least one turn for the learner, the first line is his. A comprehension turn: he asks in his line ("Povej, fant: kaj je naredil lovec?"), the choices answer; a reaction turn: "O, ne! Ubogi Zlatorog!". The wrong choices are wrong for a reason the `why` says (a case, an ending, agreement, a tense, what the story said), never the learner's gender; like a scene's, a wrong choice has his reaction as its `reply` (taken at face value, in his voice, never the right form in it: the curated stories all have theirs). By the fire the `fx` are the campfire's: `flare` at the dramatic moment, `steam`, `bell` (a distant bell); the `sky` sets the mood (fog, gloom, a storm): give such a telling `"sky_stays": false`, so the story's weather passes with it. A1: present tense, very short sentences, the commonest words; A2: the past, more detail; B1: subordinate clauses, the conditional, reported speech.

```json
{ "schema": "lani.story/v0", "id": "kovac-in-hudic", "language": "sl", "title": { "sl": "Kovač in hudič", "en": "The smith and the devil" }, "teller": "janez", "emoji": "⚒️",
  "teaser": { "sl": "Janez pove, kako je kovač ukanil hudiča", "en": "Janez tells how a smith tricked the devil" },
  "memory": { "sl": "zgodbo o kovaču in hudiču, ki sem ti jo povedal", "en": "the story of the smith and the devil I told you" },
  "pack": "zgodbe", "words": ["velikan", { "word": "sekira", "pack": "ob-ognju" }],
  "levels": { "A2": { "sky_stays": false, "lines": [
    { "sl": "Dober večer, {learner}. Veš, zakaj kovač nikoli ne da hudiču roke?", "en": "Good evening, {learner}. Do you know why a smith never gives the devil his hand?" },
    { "choices": [
      { "sl": "Ne vem. Povejte, prosim.", "en": "I don't know. Please tell me.", "ok": true, "reply": { "sl": "Poslušaj, {m:fant|f:dekle}.", "en": "Listen, {m:lad|f:lass}." } },
      { "sl": "Ne vem. Povej, prosim.", "en": "I don't know. Please tell me (ti).", "why": "Stari Janez is old and not family: use vi. The polite imperative is povejte.",
        "reply": { "sl": "Tikaš me? No, pa naj bo, {m:fant|f:dekle}.", "en": "Calling me ti? Well, so be it, {m:lad|f:lass}." } } ] },
    { "sl": "Nekoč je hudič prišel h kovaču. Hotel je, da mu kovač podkuje konja.", "en": "Once the devil came to a smith. He wanted the smith to shoe his horse.", "sky": { "gloom": 0.4 } },
    { "sl": "Kovač pa je hudiča prikoval na nakovalo in ga spustil šele, ko je obljubil, da ne pride nikoli več.", "en": "But the smith nailed the devil to the anvil and let him go only when he promised never to come back.", "fx": { "flare": 1 } },
    { "choices": [
      { "sl": "Ha! Pameten kovač!", "en": "Ha! A clever smith!", "ok": true, "reply": { "sl": "Ja. Lahko noč, {m:fant|f:dekle}.", "en": "Yes. Good night, {m:lad|f:lass}.", "sky": { "gloom": 0 } } },
      { "sl": "Ha! Pametna kovač!", "en": "Ha! A clever smith!", "why": "Kovač is masculine: pameten kovač. Pametna goes with feminine nouns.",
        "reply": { "sl": "Pametna? Kdo, kovačeva žena?", "en": "Pametna? Who, the smith's wife?" } } ] } ] } },
  "pictures": [
    { "after": 1, "bg": "village", "figures": [ { "id": "horse", "x": 0.3 }, { "id": "devil", "x": 0.65, "flip": true } ],
      "caption": { "sl": "Hudič pride h kovaču s konjem.", "en": "The devil comes to the smith with his horse." } },
    { "after": 2, "bg": "village", "night": true, "figures": [ { "id": "peasant", "x": 0.3 }, { "id": "devil", "x": 0.72, "flip": true } ],
      "props": [ { "id": "fire", "x": 0.5 } ], "caption": { "sl": "Hudič obljubi, da ne pride nikoli več.", "en": "The devil promises never to come back." } } ],
  "notes": [
    { "level": "A2", "after": [1, 2], "text": [
      { "sl": "Nekoč je hudič prišel h kovaču. Hotel je, da mu kovač podkuje konja.", "en": "Once the devil came to a smith. He wanted the smith to shoe his horse." },
      { "sl": "Kovač pa je hudiča prikoval na nakovalo. Spustil ga je šele, ko je hudič obljubil, da ne pride nikoli več.", "en": "But the smith nailed the devil to the anvil. He let him go only when the devil promised never to come back." } ] } ] }
```

Write one when a `stories_low` note comes (fewer than three stories the learner hasn't heard are left at their level: `list_stories` `left_at_level`; the note brings what was told, their level, their weak patterns, the region and the picture library), when `list_stories` says `all_heard` (a new story comes before the retellings), when the learner asks for a story, or to bring a week's words back: at the learner's current level (`learner_level`, or the level they hear at), with words they know and a few new ones listed in `words`, 8-14 lines, 2-4 turns, 2-4 `pictures`, and its `notes` (a telling each). A legend of the region the collection doesn't have, a local tale, a small story of the village itself (the winter the wolves came, how Micka's potica won a prize), or a continuation of one they liked (`continues`: a new chapter or a sequel, "Martin Krpan se vrne"; it comes after the new ones, and its notebook entry joins the first's). Bring a weak pattern or two into the learner's turns (the case the note names, the ending they miss). Check the Slovene twice (case endings, aspect, clitic order, diacritics). `publish_story` with a one-line `note`; it is told the next evening, and once heard it is written down in the learner's story notebook.

## Villagers (`lani.villager/v0`)

The people of Moja vas: one cast for the quest givers, the people in scenes, the companion on the training stage, and the characters you play when Jan talks to them ("Pogovori se · Talk" on a villager's page). They remember: the friendship (points, level 0-4, memories) lives in the app's village state, and the lines get warmer as it grows. Validator: `companion/bridge/src/villagers.ts`; the full description: `companion/VILLAGERS.md`. Curated villagers live in their culture pack, `companion/cultures/<culture>/villagers/<id>.json` (Jan's: `primorska`); `publish_villager` writes tutor ones to `<data>/app/villagers/` (same id = replace; a curated id is replaced until `remove_villager`).

| Field | Type | Notes |
|---|---|---|
| `schema` | `"lani.villager/v0"` | required, literal |
| `id` | string | kebab-case, 1-63 chars; scenes point to it (`people[].villager`), quests find them by `name` |
| `name` | string | "Pastir Luka": the name quests and scenes use (a quest giver's name must match) |
| `emoji`, `art`, `voice` | string, sprite, `female` \| `male` | the sprite is one of the scene sprites; `voice` is their gender (grammar, and the fallback voice) and defaults to the sprite's (child sprites: female, so set `male` for a boy) |
| `speaker` | string? | their voice in the voice cast (`companion/voice-cast.json`), of the same gender as `voice`: `grandma`, `grandpa`, `young-man`, `gruff`, `woman`, `teacher`, `young-woman`, `girl`, `boy`. Someone who moved in: `young-woman` or `young-man`, a child `girl` or `boy`. Without it, their lines are read by the narrator of their gender |
| `role` | string \| text | "Pastir · Shepherd", Slovene · English; or per language `{"it": "Pastore", "sl": "Pastir", "en": "Shepherd"}` (a village in another language) |
| `home` | string[] | where they are, best first: building types in lower case (there once it is built) and `spot:<id>` (always there: `meadow`, `pond`, `highseat`, `path`, `woodpile`, `rocks`, `riverbank`, `road`, `kopa`). At most 6, each once. A villager without a spot waits for their building |
| `register` | `ti` \| `vi` | what Jan says to them (and what they expect back): older people and strangers `vi`, children and friends `ti`. Say in `personality` what they say to Jan (Micka says ti to Jan, expects vi) |
| `personality`, `story` | string | English, for you and the villager page: manner, speech habits, what they care about (at most 600 and 500 chars) |
| `likes` | (string \| text)[] | at most 8, "Slovene · English" (`"ovce · his sheep"`), or per language (`{"it": "le pecore", "sl": "ovce", "en": "his sheep"}`): the village's language becomes a hint in a talk with them |
| `skill` | resource? | what their requests train: `food` (vocabulary), `wood` (listening), `stone` (grammar), `wisdom` (conversation) |
| `since`, `order` | age?, int | from which age they live in the village (`ogenj` … `mesto`; none = from the start), and who moves in first when there is room (lower first; the cast uses 1-13) |
| `routine` | object? | their day (companion/VILLAGERS.md, "A day in the village"): `up` and `bed` ("6:00", "22:15"; "0:30" is after midnight; without them their kind's: children 20:00, grown-ups about 22:00, never after the storyteller's 22:45), and `rituals` (at most 4), the only reason to be out at night: `{id, at, until, to, doing, title}`: from 19:00 until 6:00, 10 to 60 minutes, over before they get up, `to` a building type, `spot:<id>`, `fire` or `forest`, `doing` `stand` \| `tend` \| `carry` \| `watch`, the `title` in the village's language and the learner's base ("Ana zapre statve · Ana covers the loom"). The map shows them walking there with a lantern |
| `sleeps` | string? | where they sleep, when it isn't where they are by day (companion/VILLAGERS.md, "A day in the village"): a roof, `tent`, `hut`, `house`, `smithy`, `school`, `market` or `beehive` (Tone `smithy`: his cot by the forge), or a room of a house by its art, `house:<art>` (Anton `house:cellar`, Jože `house:livingroom`: they lodge in the house that has that room, in a bed of it; while no house has it, in a house). Without it, the first roof of their `home`. The app has a bed for them only where the room's painter draws one |
| `extra` | bool? | a smaller person of the culture (Lovec Jože, the hunter at the high seat), not one of the roles: fewer lines (2-3 of each kind), moves in as a newcomer does, taking turns with the names the village makes up, once the cast has come |
| `lines` | object | `greet`, `thanks`, `remember`, `idle`, `cheer`, `comfort`, `listen`, `bye`: 2-6 lines each (for any one time of day), `{sl, en, level?, when?}`, or in a village of another language `{it, sl, en, level?, when?}` (the village's language, the learner's base, English: English is always there). `level` (0-4) is the friendship level from which a line can be said, so they grow warmer; one `greet` line must be level 0. `when` (the parts of the day a line is said in: `morning`, `afternoon`, `evening`, `night`) tags a line that belongs to a time of day: "Dober dan" `["morning", "afternoon"]`, "Dobro jutro" `["morning"]`, "Dober večer" and "Lahko noč" `["evening", "night"]`, "Nocoj praznujemo" `["morning", "afternoon", "evening"]`; the app says it only then, by the phone's clock (a line without `when` fits any time, and "jutri" in a line doesn't make it a morning line). The level-0 greetings together cover the whole day (a "Dober dan" wants a "Dober večer" or one without `when` beside it), and a line with `when` counts toward the 6 only at its times. `remember` lines contain `{memory}` in every language (Slovene: a memory in the accusative after `na`; Italian: after a transitive verb, "Ricordo ancora {memory}", never after a preposition); no other line does. `cheer`, `comfort` and `listen` are for the training stage (a right answer, a wrong one, before a listening exercise). Optional `gift`: what they say when Jan gives them a good from the chest, `{liked, ordinary, rare}`, 1-6 lines each (the cast has 3, 2 and 1): a good they like (delight), any other (warm thanks), a rare one (special joy); said as they are (no `{…}`), fitting any good of the kind ("Kako si vedel, da imam to rada?"). Without them they thank in plain words |

```json
{ "schema": "lani.villager/v0", "id": "n-ana-furlan", "name": "Ana Furlan", "emoji": "👩", "art": "woman", "voice": "female", "speaker": "young-woman",
  "role": "Tkalka · Weaver", "home": ["house", "hut"], "register": "vi", "order": 40,
  "personality": "Calm and precise, from Solkan, says vi and expects vi. Talks about wool, her loom and the burja that dries it. Short, clear sentences.",
  "story": "Ana moved up from Solkan with her loom and weaves blankets for the whole plateau.",
  "likes": ["volna · wool", "burja · the burja", "kava · coffee"],
  "lines": {
    "greet": [{ "sl": "Dober dan.", "en": "Good day.", "when": ["morning", "afternoon"] }, { "sl": "Dober večer.", "en": "Good evening.", "when": ["evening", "night"] }, { "sl": "O, {learner}! Pridite noter.", "en": "Oh, {learner}! Come in.", "level": 2 }],
    "thanks": [{ "sl": "Hvala vam.", "en": "Thank you." }, { "sl": "Hvala, {learner}. Res ste mi pomagali.", "en": "Thanks, {learner}. You really helped me." }],
    "remember": [{ "sl": "Še vedno mislim na {memory}.", "en": "I still think of {memory}.", "level": 1 }, { "sl": "Ne pozabim na {memory}.", "en": "I do not forget {memory}.", "level": 2 }],
    "idle": [{ "sl": "Volna je mehka.", "en": "The wool is soft." }, { "sl": "Burja suši volno.", "en": "The burja dries the wool." }],
    "cheer": [{ "sl": "Odlično.", "en": "Excellent." }, { "sl": "Tako, brez napake.", "en": "There, without a mistake." }],
    "comfort": [{ "sl": "Nič hudega.", "en": "Never mind." }, { "sl": "Še enkrat, počasi.", "en": "Once more, slowly." }],
    "listen": [{ "sl": "Poslušajte.", "en": "Listen." }, { "sl": "Tiho. Poslušajte.", "en": "Quiet. Listen." }],
    "bye": [{ "sl": "Na svidenje.", "en": "Goodbye." }, { "sl": "Adijo, {learner}.", "en": "Bye, {learner}.", "level": 2 }],
    "gift": {
      "liked": [{ "sl": "O, {learner}! Kako ste vedeli, da imam to rada?", "en": "Oh, {learner}! How did you know I like this?" }],
      "ordinary": [{ "sl": "Hvala vam, zelo prijazno.", "en": "Thank you, very kind." }],
      "rare": [{ "sl": "Joj, kaj takega! Najlepša hvala.", "en": "Oh my, something like this! Thank you so much." }]
    }
  } }
```

Rules: every line simple and correct at A1-A2, in their voice (Janez slow and proverbial, the children quick and cheeky, Tone gruff and kind), Slovene said to the learner as `{learner}`, what agrees with them a gender pair ("Si {m:lačen|f:lačna}?", and the learner says "{m:Lačen|f:Lačna} sem" too; "The learner in the content" below), Primorska words marked in the English ("čav: Primorska for bye"). Tag every greeting, goodbye and line that names a time of day with `when` when you publish a villager (a "Lahko noč" without it would be said after a morning review), and give a goodnight a daytime goodbye beside it in their voice ("Lahko noč, {m:fant|f:dekle}. In jej!" and "Pa lep dan, {m:fant|f:dekle}. In jej!"); `publish_villager` refuses a `when` that isn't a part of the day and level-0 greetings that leave a part of the day without one. Keep `id`, `name`, `art` and `voice` as the app sent them (and give them the `speaker` for their age and gender) for someone who moved in (`villager_arrived`), so their friendship and their place in the village carry over. When you replace a curated villager, keep their `name`, `skill`, `home`, `since` and `order`: quests, scenes and the village's arrivals depend on them.

**Talking to a villager** (kind `roleplay`, `scenario_id` `villager:<id>`): the bridge builds the role-play from the villager for the current friendship (the opener is their warmest greeting Jan has earned, the goals are open: greet, ask how they are, talk about something they like, say goodbye) and adds `data.villager`: the persona, `friendship {level, name, points}` and their `memories` (`{on, sl, en}`, newest last). Play them in character and in their register (a `vi` villager keeps vi and expects it back; note it gently when Jan slips), simple Slovene at Jan's level, 1-2 short sentences, their manner and their topics; bring up one shared memory naturally when there is one ("Še vedno mislim na …"), never all of them; correct only real mistakes, as in other role-plays. On `roleplay_end`, the debrief (`data: {debrief: true}`) also carries `data.memory: {"sl", "en"}`: one thing from this talk worth keeping, in the villager's words, fitting "Še vedno mislim na …" (accusative: `"pogovor o ovcah na travniku"`, `"the talk about the sheep in the meadow"`). The app stores it and the friendship grows; a villager who is not in the cast (someone who moved in) is played from their name, role and family until you give them a spec.

## Arrivals (`lani.arrival/v0`)

When someone joins the village (one of the cast, a newcomer, a baby born there, a mover from a friend's town), an
introduction waits for Jan: a bubble at their home, and a short dialog at Jan's level with the right people (the newcomer,
or a relative who introduces them; a baby's parents). Nobody hosts a run, gives a request, leads a feast or stands in a
scene before Jan has met them in it. The culture pack has one for each of its cast and templates for the people the
village makes up (`companion/cultures/<culture>/arrivals.json`, `lani.arrivals/v0`, bundled in the app); a newcomer's or
a baby's plays the template until you write theirs. Validator: `companion/bridge/src/arrivals.ts`; the full description:
`companion/VILLAGERS.md`, "Arrivals". `publish_arrival` writes to `<data>/app/arrivals/<id>.json` (same id = replace; a cast
member's id replaces the pack's until `remove_arrival`); `get_arrival` shows what the app would play for someone now.

| Field | Type | Notes |
|---|---|---|
| `schema` | `"lani.arrival/v0"` | required, literal |
| `id` | string | the resident's id (`data.id` of `villager_arrived`: "n-ana-furlan"), someone who lives in the village |
| `language` | `sl` \| `it` \| … | the village's language (default `sl`): every text in it and `en` (and the learner's base, when it isn't English) |
| `by` | id? | a relative or friend who lives there and introduces them (their lines' `who`); leave it out when they introduce themself |
| `memory` | `{sl, en}` | what the newcomer keeps of the day, in their words, first person, fitting "Še vedno mislim na …" (accusative): `{"sl": "dan, ko sem prinesla statve v vas", "en": "the day I brought my loom to the village"}` |
| `levels` | `{A1?, A2?, B1?, …: {late?, lines}}` | the learner's level is enough; `lines`: 3-12 scene dialog lines (someone's line `{who, sl, en}`, the learner's turn `{choices}`), the first someone's, every line with its `who` (the newcomer's id; a baby's parents' ids: a baby can't talk; `by`); `late`: an optional line said instead of the first when Jan meets them days later ("Dober dan! Nisva se še spoznala, kajne?"). No `sky` or `fx` |

A1: 3-5 short lines (name, trade or age, where from, what they do); A2: also why they came and with whom; B1: a small
story. 1-2 turns for Jan with 2-3 choices each, where the grammar is natural: the greeting's register ("Dober dan" to
someone new and grown-up, "Živjo" to a child), the welcome agreeing with who it is ("Dobrodošel", "Dobrodošla",
"Dobrodošli" to someone Jan says vi to), "Čestitam!" for a baby, "Me veseli."; around one of Jan's weak patterns
(`data.arrival.weak_patterns`) where it fits. Every wrong choice has a `why` and a `reply`: their reaction to what Jan
actually said, kind and a little funny, never the right form. No placeholders (`{…}`): name people as they are.

```json
{ "schema": "lani.arrival/v0", "id": "n-ana-furlan", "language": "sl",
  "memory": { "sl": "dan, ko sem prinesla statve v vas", "en": "the day I brought my loom to the village" },
  "levels": { "A1": { "late": { "who": "n-ana-furlan", "sl": "Dober dan! Nisva se še spoznala, kajne?", "en": "Good day! We haven't met yet, have we?" },
    "lines": [
      { "who": "n-ana-furlan", "sl": "Dober dan! Jaz sem Ana, tkalka.", "en": "Good day! I'm Ana, a weaver." },
      { "choices": [
        { "sl": "Dober dan! Me veseli.", "en": "Good day! Nice to meet you.", "ok": true, "reply": { "sl": "Tudi mene.", "en": "Me too." } },
        { "sl": "Živjo, Ana! Kako si?", "en": "Hi, Ana! How are you? (ti)", "why": "You've just met a grown-up: say vi (dober dan, kako ste).",
          "reply": { "sl": "Kako si? Hm, saj se še ne poznava.", "en": "How are you? Hm, we don't know each other yet." } } ] },
      { "who": "n-ana-furlan", "sl": "Iz Solkana sem prišla, s statvami.", "en": "I came from Solkan, with my loom." } ] } } }
```

## The learner in the content

Every text you write (a module, a pack, a role-play, a scene, a story, a villager, an arrival, a reading, a grammar page)
speaks to whoever learns, so it never writes the learner's name or gender out (companion/SCENES.md, "The learner in the
content", has it all):

- **The name** is `{learner}`; its cases `{learner:gen}`, `{learner:dat}`, `{learner:acc}`, `{learner:loc}`,
  `{learner:ins}`, and `{learner:poss}` the possessive's stem with its ending after it (`{learner:poss}a hiša`: "Markova
  hiša", "Anina hiša"). The app and the bridge decline it (Ana → Ane, Ani, Ano; Marko → Marka, Marku, Markom).
- **What agrees with the learner** is a gender pair, `{m:man's form|f:woman's form}`, the smallest natural unit:
  `Si {m:lačen|f:lačna}?`, `{m:Rad|f:Rada} bi kavo.`, `Lahko noč, {m:fant|f:dekle}.`, `Sei {m:stanco|f:stanca}?`,
  `{m:mein Junge|f:mein Mädchen}`, `{m:lad|f:lass}`. A branch may hold `{learner}`, nothing else in braces, no `|`.
  Pair it in every language of the text, and in a `why`, an `accept` or a token that says it.
- **What stays**: what agrees with anyone else (the villager's own "Utrujena sem."), polite *vi* ("Ste lačni?"), the
  story's narration; a dual is feminine only when both are women (with Micka `sva {m:jedla|f:jedli}`).
- **A turn never tests the learner's gender**: the choices of a turn differ for a woman as for a man; `publish_*`
  refuses a turn whose choices read the same for one, and a malformed placeholder.

A text with a name written out still works, it just says that name to everyone.

## Extending the format

New types need two changes that ship together: the validator (`companion/bridge/src/spec.ts`) and a renderer in the app (`ExerciseView` in `companion/android/.../ui/PlayerScreen.kt`). An app that is older than the bridge shows unknown types as a "new exercise type, update the app" card and skips them. So publish a new type only after the app release that renders it.

## App matching rules (for `match`)
- case-insensitive; trims and collapses whitespace; ignores punctuation (`.,!?;:` and quotes)
- an answer that equals an accepted one except for diacritics (`c`↔`č`, `s`↔`š`, `z`↔`ž`) is **almost**: shown as a near-miss with the correct spelling
- in another target language, its own table decides (`app/src/main/resources/grading/<code>.json`): Italian's accents (`e`↔`è`, `perche`/`perchè`/`perche'`↔`perché`, `citta`↔`città`) and its apostrophes (`un amica`↔`un'amica`, `la acqua`/`l acqua`↔`l'acqua`) are **almost**, with an accent or apostrophe hint; German's `ae`/`oe`/`ue`/`ss` for `ä`/`ö`/`ü`/`ß` are **almost** (umlaut hint); English contractions are the same answer (`don't` = `do not`, `it's` = `it is`)
- **one typo in a word's stem** (a missing, extra, wrong or swapped letter; word of 4+ letters) is **almost**: `zivo` → `živjo`, `hvlaa` → `hvala`; a word of 9+ letters forgives two (`Burgermaister` → `Bürgermeister`)
- **anything in a word's ending stays wrong**, because endings carry the grammar: the last letter of words up to 5 letters, the last 2 of longer words. `moj` → `moja`, `Berlin` → `Berlina` and `imenujme` → `imenujem` are wrong, with a "check the ending" hint
- the app highlights the differing letters in the correct answer

So `accept` only needs genuinely different valid answers. Don't list misspellings.

## Complete example
```json
{
  "schema": "lani.module/v0",
  "id": "clitic-se-basics",
  "title": "Where does se go?",
  "description": "The little word se always sits in second position.",
  "level": "A1",
  "targets": ["clitic_placement_se"],
  "exercises": [
    { "type": "choice", "prompt": "Which one is correct?", "options": ["Se imenujem Jan.", "Imenujem se Jan.", "Imenujem Jan se."], "answer": 1 },
    { "type": "reorder", "prompt": "I apologize.", "tokens": ["se", "Opravičujem"], "solutions": [["Opravičujem", "se"]] },
    { "type": "translate", "prompt": "My name is Jan.", "accept": ["Imenujem se Jan.", "Jaz se imenujem Jan."] }
  ]
}
```
