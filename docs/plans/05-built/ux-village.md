# Built: the village screens, production-ready (the UX round of 2026-10-27)

Jan, while Teo (a child learning Italian from German, the Friuli culture, on a 2560 × 1600 tablet) played, and the
navigation audit's section 4 ("the tent overview is a bit overloaded"). Jan: "for choices - make it the best - prod ready".
The rules as built are in companion/GAME.md ("The village's HUD", "Buildings": "The build sheet", "When every plot is
built", "The building's card", "Upgrades", "Ages and their celebrations", "The tent moves to the pond") and SCENES.md
("What the village has built"). This note says what changed, why, how to use it, how it is tested, and what is left.

## What Jan asked, and what was built

| Ask | Built |
|---|---|
| The building card: one card, "Vstopi" first, who is here and who lives there, the upgrade as ONE button for the next step, the words and the moba folded, Move and Repair at the bottom, no duplicate "what it gives", no build date, no count that jumps | `ui/game/BuildingCard.kt`: one card over the map (the old place card and the building sheet merged). The next step is a state (`UpgradeStep`): the age, the words, the course, the price, then "⬆️ Nadgradi". "🔤 …" until the words' packs are on the phone. |
| The build sheet: only what can be built now, one quiet line for what comes later | `BuildSheetLogic` + `BuildSheet`: only open kinds, each with its way on (gather, a bed, its way through the plots); "🔒 Pozneje (Zaselek): čebelnjak, stražni stolp, kovačnica …" as one line; the plots kept for the next age as one line. |
| No dead end from filling plots: tents grow up, a building not needed can be replaced, proven by a simulation | `game/Rebuild.kt`: growing up (tent → hut → house, its level kept, for the difference), replacing, taking down; `BuildOption.plotWay`; `BuildOrdersTest`: 2,000 random build orders, 0 dead ends (760 with the old rules). |
| The age-up: let it breathe, keep it skippable; a short "what's new" | `AgeUps`: beats 3–5.4 s (27–35 s in all, was 15–22 s), the camera flies in under half a beat and holds; one caption after the other; `AgeUps.digest`: three things, the rest folded. |
| The age ring opens the stage sheet directly | `VillageHud`: a tap on the ring opens the ages' sheet (its count as the ring's, one action for the next step, every step, the cards' switch); a long press shows or hides the cards. |
| (added) Teo's three huts: rebuild as houses, take down, never homeless | `Rebuild.grow` for a hut ("🏠 Prezidaj v: Hiša"), `Rebuild.takeDown` with a confirmation, `Rebuild.bedsShort` for every replacement or taking down. Named case in `BuildOrdersTest`. |
| (added) The first tent at the pond stays a tent | It never grows, is never replaced or taken down; `BuildOrdersTest` checks it survives every order. |
| (added) "Why do I get suggested to build a tent?" | `nextGoal`/`usefulBuild`: a building only when the next age needs it, a home for whom a needed workplace brings, or a field while food runs short; never a tent from the camp on, never a hut once houses can go up. |
| (added) The bubbles: "a bit overloading + if I do one it is right back" | A window of 3 requests on a phone (4 on a tablet), one per villager, the best first, the rest behind "📜 +N"; a run clears its "!" and the giver rests. See "The bubbles" below. |

## How to use it, and the taps it takes

Counted on the emulator from the plain village map, the base commit's debug build ("before", b5a0b187) against this
branch's ("after"), the same synthetic learners (Ana, Primorska: a Vas village; a copy at Zaselek with three plots free;
Teo-like: Friuli, Italian from German, a hamlet of a tent, three huts and a field). A swipe counts as a tap.

| Journey | Before | After |
|---|---|---|
| What the next age needs (every step) | 5 (the ring, two swipes of the cards, the settlement's card, its age row); 4 with the cards shown | 1 (the ring) |
| A tent's card with its upgrade's next step | 2 (the tent, "🔨 Stavba · Building ›") | 1 (the tent) |
| Upgrading a tent whose words are missing: to its words' run | 3 (the tent, "Stavba", "🎯 Vadi jih zdaj") | 2 (the tent, "🎯 Nauči se 8 besed") |
| Upgrading a tent whose words are known | 3 (the tent, "Stavba", "⬆️ Nadgradi") | 2 (the tent, "⬆️ Nadgradi") |
| Building something new | 3 ("Gradi", its card, "✓ Tukaj") | 3 (the same; the sheet holds only what can be built, the card is on its first screen) |
| A hut rebuilt as a house (Teo's tablet, it · de) | not possible | 2 (the hut, "🏠 Ricostruisci"), no scroll |
| Replacing a building | not possible | 5 (the building, "▸ Nadomesti ali podri", "🔁 Nadomesti", its card, "✓ Nadomesti") |
| A building held back by full plots, from the build sheet | a wall ("Ni prostora") | 2 (its way, "✓ Nadomesti"), or 1 for a dwelling that grows |
| The age-up | 20–22 s, then a card of 4–6 screens | 27–35 s (Zaselek → Vas measured: 29 s), then a card of one screen, "Še 4 novosti" folded |

Measured on the emulator (a 1080 × 2400 phone, and the same as Teo's tablet on its side, 2560 × 1600 at 320 dpi), the
base commit's debug build against this branch's, the screenshots in the round's scratchpad (`ux-village/before/b-*.png`,
`ux-village/after/a-*.png`):

- **The ring.** Before: a tap hid or showed the cards; the ages' sheet was the settlement card's age row at the end of
  the row (`b-01` … `b-07`). After: one tap opens it, "4/9" as the ring says, "🌾 Nabiraj · Gather food" at its top,
  every step, the cards' switch at its foot (`a-j1-01`, `a-j1-02`; on the tablet `a-t-07`).
- **A building's card.** Before: the place card, then "🔨 Stavba · Building ›", a sheet of eleven blocks whose house card
  was cut at its foot (`b-10` … `b-15`; on the tablet the place card ran 2560 px wide and the sheet cut "Premakni" to a
  sliver, `b-72` … `b-74`). After: one card over the map, the building in view above it (`a-j2-Šotor-card`,
  `a-j2-Hiša-card`, `a-j7-01-field-card`); on the tablet 600 dp wide, scrolling within itself (`a-t-01`, `a-t-05`).
- **The tent's words.** Before: 3 taps to the run. After: 2 (`a-j3-01`, `a-j3-02`: the run "1/8", the card's button
  said "Nauči se 8 besed", the chip "0 / 14": no more "0/14" against a run of 8 unexplained); back from the run the card is
  open again (`a-j3-03`).
- **Building.** Before: a sheet of 4 screens, locked cards among them (`b-30`, `b-31-*`). After: the cards that can be
  built (`a-j4-01`, `a-j4-02`), 3 across on the tablet (`a-t-06`); built: the chronicle's line and a burst of confetti
  (`a-j4-04`).
- **Every plot built** (Ana's Vas village, 16 buildings on 14 plots): the orange line and only cards with a way through
  the plots (`a-j4b-01`; that first run offered "Namesto: Hiša" on the tent's card, fixed: nothing comes down for what the
  next age doesn't need).
- **Replacing** (a field by a house: `a-j7-01` … `a-j7-06`): the fold, the replace sheet ("Kaj zgradiš na tem mestu? …"),
  the map with the house drawn on the field's plot and "✓ Nadomesti · Replace", then built.
- **Taking down** (the hayrack, kept): the confirmation says what is lost and what comes back (`a-j8-01`).
- **Teo's hut** (Friuli, Italian from German, the tablet): the card in it·de, "🏠 Ricostruisci come: Casa · Umbauen zu:
  Haus", one tap, the chronicle "Ricostruito: Capanna → Casa · Umgebaut: Hütte → Haus" (`a-t-01` … `a-t-04`).
- **The second run** (the fixes of the first, 6 minutes on the emulator): Teo's hut card on the tablet with "🏠
  Ricostruisci come: Casa" right under its one button, no scroll (`a-s2-t-hut-card`); the ages' sheet scrolled on the
  tablet (`a-s2-t-ages-sheet-scrolled`); Ana's full build sheet offering only "⬆️ Prezidaj: Koča → Hiša" on the house's
  card, nothing to come down for a tent (`a-s2-full-build-sheet`).
- **The age-up** (`ageup:vas`): frames every half second, the card at 29 s after the moment began (`montage-ageup.png`,
  `a-j5-ageup-06s`, `-14s`, `-26s`, `a-j5-ageup-card`, `a-j5-ageup-card-more`); the captions one after the other.

## The bubbles: a window, not a pile

Jan: "all the bubbles is a bit overloading + if I do one it is right back" (his village: 13 open requests, 12 of them the
tutor's; up to two a villager on the map, and the next one up the moment one was done). Built by the round's helper; the
rules are in GAME.md "Quests" ("A window of requests") and SCENES.md "Bubbles in the village".

- **A window.** The map shows 3 requests on a phone, 4 on a tablet, one per villager, the best first: what the next age
  needs (a step the request completes), then weak or due items (a drill, the dashboard's due or rusty cards, a module on a
  weak pattern), then one already started and almost passed, then the tutor's newest, then the oldest of the villagers'
  own; ties to the giver nearer the view's centre. The event, arrivals and an age's party always show; happenings, project
  steps, the visitor and the like are quiet icons away from the view's centre. Everything else waits behind one chip "📜
  +N" that opens the Scroll's villagers. Clearing what's shown in a roll grows the window by one (three more at most); 4
  hours untouched and it is back at its start. What's up is kept for the day (`GameState.pace`, `RequestPace`), so a new
  task never pushes a bubble off the map.
- **A run clears its "!".** Passed: done. Below the pass mark: a fifth of the reward, the try counted, and it comes back the
  next morning ("🌙 Jutri še enkrat · Again tomorrow"). The giver thanks over their head ("Hvala! Jutri še enkrat?") and
  rests at least 3 hours and into the next part of the day ("☕ Počiva do 14:30"), so the freed place goes to another
  villager.
- **The tutor**, soft only: `get_village` shows `on_map`, `back` and `resting`; `publish_module` adds notes (a resting giver,
  open tasks there, four or more in the village) and still publishes; `session_end`'s `data.quest` says what was missed,
  for the reviews; the guide, the core and studio prompts and the studio skill say to keep tasks few and extend rather
  than add.
- **Tests:** `RequestWindowTest` (14: the busy Jan-like village shows 3 on a phone and 4 on a tablet, one each, 10 behind
  the chip, nothing lost; the order and its ties; a pass, a run below the mark, the rest times, the roll, the day's pins,
  the wolves), `RequestWindowSimulationTest` (a week of three sessions a day, the tutor publishing after every run, phone
  and tablet: never more than the window, one per villager, nobody asking again right after their run, nothing lost); the
  bridge's smoke (all checks passed, with the new ones).
- **Taps:** a waiting request 3 → 2 (the chip, the row); the best one 3 → 3; at a glance 8 request bubbles and 5 full-size
  happenings → 3 bubbles and one chip (`bubbles/b-00-village-map.png` → `a-00-village-map.png`; the run below the mark,
  the thanks, "Počiva do 05:00" and "Jutri še enkrat": `a-03` … `a-11`).
- **Left:** the chip opens the villagers' list at its top (the requests are further down); the thanks shows only while the
  giver is drawn on the map; an older app drops `pace` (the window starts afresh); before the dashboard loads the weak/due
  tier is empty; QA's `school-scene` now expects one of Mojca's on the map (not run on the emulator).

## Tests

- `game/BuildOrdersTest` (the proof that the learner can't block their own progress): 250 random build orders per culture
  (Primorska, Friuli, Kärnten, Lakeland) for each of two players, one building at random among what it can (stopping
  now and then, moving and upgrading too), one filling every plot with what the next age doesn't need first (tents in
  every slot); every bed taken, the stores full; from the campfire to the town, each age's buildings reached only the
  ways the app offers. Before (nothing grows, nothing is replaced): 760 of 2,000 orders dead-ended, all in the market
  town on the way to Mesto (71–123 per culture and player). After: 0 of 2,000 (about 2,700 dwellings grown and 2,800
  buildings replaced on the way, some taken down; the tent at the pond, in about 1,930 of the orders, still a tent there at
  the end of each). Teo's camp (Friuli: the first tent, three huts and a field in five plots) played on 200 times: 0
  dead ends; a hut rebuilt as a house for 30 🌾 30 🪵 40 🪨. About 80 s on the JVM.
- `game/RebuildTest`: growing (the price of the difference at each level, the level kept, a new id and day, the first
  tent and the tent at the pond never, before the age never), replacing (which may be, the people staying, the beds),
  taking down (half the materials, within the stores, the beds), the ways through the plots, Mesto's nine buildings
  reached from a walled-in market town.
- `ui/game/BuildingCardTest` (Robolectric, a 411 dp phone): the card's order, the one button's steps, "…" while the
  words come, the folds, growing, moving, replacing and taking down (confirmed first), the repair.
- `ui/game/BuildSheetLogicTest`: only what can be built now, the later and the kept lines, full plots, the replace
  sheet, the card's step order.
- `ui/game/AgeSheetTest`, `ui/game/VillageHudTest`: the ring's tap and long press, TalkBack's labels, the sheet's count
  and its one action, the cards' switch.
- `game/AgeUpsTest`: the moment's length (26–36 s, every beat at least 3 s), the digest (three first, nothing lost).
- `ui/game/VillageLogicTest`: the goal never a tent for a Jan-like Vas village, and in every culture and age only a
  building the next age needs.
- The string tables (`l10n/*`): every new key in all four, none unused.

The whole Android unit suite on the branch rebased onto main (157b9b29): 3,116 tests, 1 failure, `SceneDetailTest` "a
closer frame renders fast" (a scene's render-time budget, 28.7 ms over its limit while the machine's load average stood at
34; the branch changes no render code; alone it failed again at 25.4 ms against its 25 ms budget, and another round's
branch logged the same failure under the same load: a timing flake of a busy machine, not this change). The bridge's `tsc --noEmit` is green; the Python tests 251 and
98 (10 skipped) pass (QA's `bin/qa` changed). The bridge's smoke: see "The bubbles".

## Strings

Every new label is Slovene first with the learner's base second through `bi()`, in sl, en, de and it. The Slovene, German
and Italian are machine-written (Claude, 2026-10-27): for a native speaker's check (Jan's wife for the Slovene). The keys:
`buildingCard.*`, `buildSheet.*`, `rebuild.*`, `replace.*`, `takeDown.*`, `plotChooser.hintReplace`, `engine.grew`,
`engine.replaced`, `engine.tookDown`, `villageHud.openAge`, `villageSheets.cardsUnderBar`, `ageUp.more`,
`ageUp.new.buildings`, `ageUp.new.people`, `ageUp.new.projects`. Removed (no longer shown): `villageSheets.goodShape`,
`villageSheets.allPlotsTaken`, `villageSheets.levelTo`, `villageSheets.ageOf`, `villageSheets.move`, `placeCard.building`.
The bubbles' keys (`quests.again`, `quests.resting`, `townBubbles.waiting`) and the four cultures' new `try_again` line
("Hvala! Jutri še enkrat?") are machine-written too.

## What's left, and what to watch

- The new labels' Slovene, German and Italian are machine-written: a native check (above).
- Growing up keeps the level without asking the bigger building's words for it; its next upgrade asks for all of them
  (they include the levels before). A tent at level 3 grown into a hut is a level-3 hut whose kitchen words the learner
  may not know yet. Chosen so nothing is lost; if it should ask, `Rebuild.growOption` can take the words as an upgrade does.
- Replacing and taking down give nothing back for the old building's upgrades (taking down: half its build materials).
  A child can't do it by accident: both are folded and taking down asks first.
- The village's first tent (before the shepherd moves it to the pond) can't grow or be replaced, and the bee house of a
  beekeeper who lives here is kept while it is the last one. Both are said by their cards not offering it, not by a line.
- The building card replaces the place card for the village's own buildings; a visited town's buildings keep the place
  card (nothing of the host's changes).
- The age-up's length is set in `AgeUps` (`OPEN_S` … `CHEER_S`); QA's `MOMENT_S` mirrors it.
- The navigation round (ux-nav) changes the HUD's Friends entry and the back stack; this branch changes the ring's tap
  (VillageHud.kt) and the village's sheets (VillageScreen.kt): a rebase onto main keeps both.
