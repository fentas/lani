# Moja vas: rules reference

"Moja vas" ("My village") is the village game in the companion app. Practice earns resources, and resources grow the village from a campfire to a town. Helping the villagers earns 🤝 help, their friendship, their tools and their goods. Every day brings something new: a surprise at the road, a step of a village project, and on the real Slovene holidays a festival. The rules engine is in `android/app/src/main/java/si/lanisce/lani/game/` (help and the moba in `Help.kt`, the chest and the stalls in `Chest.kt`, the village projects in `Projects.kt`, the festivals in `Calendar.kt`, the day's surprise in `Surprises.kt`, "Jutri · Tomorrow" in `Tomorrow.kt`, the words an upgrade asks for in `BuildingWords.kt`). The balance numbers are in `Catalog.kt`. Keep this file in sync with that file. What the village says and is (its people, requests, festivals, surprises, goods, projects' words, names and chronicle) is its culture pack's: Jan's is `companion/cultures/primorska` (see "Culture packs").

## Resources

| Resource | Earned by | Exercises |
|---|---|---|
| 🌾 Hrana · Food | vocabulary | flashcard, plain choice, translate (`grade: match`), unsupported |
| 🪵 Les · Wood | listening | choice with `audio`, dictation, and by ear a cloze or a reorder with `audio` (the app's own, against the clock) |
| 🪨 Kamen · Stone | grammar | cloze, reorder (without `audio`), multi |
| 📜 Modrost · Wisdom | conversation, writing | scenario, free, translate (`grade: claude`) |

- Each answer pays: correct = 6, almost = 3, wrong = 1. A right answer after a hint pays like an almost. The learner always gets something for the effort.
- **Fresh mind.** The first 40 answers of a game day pay in full. Later answers pay half. The count starts again at the first tick of a new day (`answersToday`). Forty answers are about one review session and one word pack. The rule makes daily practice build the village faster than one long session. It also keeps a 60-minute day from buying a whole age at once.
- The pay is multiplied by the production of the resource: 1.0, plus building bonuses, plus tools (see "The chest"), plus morale ((morale − 50) / 500, so ±10 %), plus 2 % per villager after the first (at most +30 %).
- Each resource has a storage cap. The base cap by age is 100 / 200 / 500 / 800 / 1200 / 1800. Buildings and Marko's barrel can raise it. Nothing costs more than the base cap of the age that unlocks it, so every price can be paid without extra storage.
- Event and quest rewards come in addition to the pay for each answer. The fresh-mind rule does not halve them.

### Where resources come from

Every source pays through `GameEngine.earn`, one entry per answer. Typical amounts at full pay, production 1.0 and 75 % correct:

| Source | Answers | Pays about |
|---|---|---|
| Review session (ReviewPlanner) | 12–20 | 5 per answer: two thirds 🌾, one third 🪵, a little 🪨 from word tiles, 📜 from spoken answers |
| Word pack session (6 new words, 2 rounds) | 12 | 60: 40🌾 20🪵 |
| Curated grammar module | 12 | 55: 18🌾 31🪨 4📜, plus its tutor quest |
| Talk scene (role-play) | 1 per line, 8–10 lines | 40–50📜. A corrected line pays like an almost. |
| Family challenge answer | 3 | 18📜 |
| Gathering run in the village | 7 | 35 of the chosen resource |
| Local quest | 5–6 | the answers, plus 30 × (1 + 0.3 × age index) of its skill and 10 × (…) of the village's scarcest other resource |
| Village project step | 5 | the answers; the step itself costs (see "Village projects") |
| Festival | 6 | the answers, plus 20 × (…) 🌾 and 10 × (…) of the scarcest other resource |
| The day's surprise | 2–3 | the answers, plus 12 × (…) of the scarcest resource |
| «Vidim, vidim» with a child in a scene (SCENES.md, "I spy") | 1–3 | the answers: 🌾, a quick find a right one, a later one an almost, a thing shown a wrong one |

Most practice is vocabulary, so 🌾 and 🪵 are the bulk resources. Grammar modules and gathering bring 🪨. Talk is by far the biggest 📜 source; a learner who never talks still gets 📜 from quests, the linden (gathering) and the modules.

## Buildings

A damaged building has no effect and does not count for the next age. A repair costs half the build cost, rounded up, at any level, and brings back the whole upgraded effect. Food above a store that shrank (a damaged hayrack) is kept, but no more comes in until the repair. Building, upgrading and repairing can all call a moba: the neighbours bring part of the 🪵 and 🪨 (see "Helping people"). Building new costs the resources alone; an upgrade also asks for the building's words, the words of what is in it ("Znanje gradi · Knowledge builds", under "Upgrades").

| Building | Cost | Min age | Max | Effect |
|---|---|---|---|---|
| Šotor · Tent | 25🌾 15🪵 | Ogenj | 4 | +2 population cap |
| Njiva · Field | 30🌾 20🪵 | Ogenj | 4 | +15 % 🌾 |
| Vodnjak · Well | 20🌾 15🪵 20🪨 | Tabor | 2 | +5 morale, +50 🌾 cap |
| Koča · Hut | 30🌾 40🪵 10🪨 | Tabor | – | +3 population cap |
| Kozolec · Hayrack | 30🌾 50🪵 | Tabor | – | +150 🌾 cap |
| Palisada · Palisade | 30🌾 40🪵 30🪨 | Tabor | 1 | +15 defence |
| Čebelnjak · Bee house | 40🌾 30🪵 15📜 | Zaselek | 3 | +10 % 🌾, +10 % 📜 |
| Stražni stolp · Watchtower | 40🌾 70🪵 50🪨 | Zaselek | 1 | +10 defence, events give 12 h more time |
| Kovačnica · Smithy | 40🌾 50🪵 70🪨 | Zaselek | 1 | +20 % 🪨, +5 defence |
| Lipa · Linden tree | 120🌾 40📜 | Zaselek | 1 | +10 morale |
| Hiša · House | 60🌾 70🪵 50🪨 | Zaselek | – | +4 population cap |
| Cerkev · Church | 140🌾 120🪵 90🪨 70📜 | Zaselek | 1 | +20 % 📜, +5 morale |
| Šola · School | 100🌾 120🪵 100🪨 100📜 | Vas | 1 | +25 % 📜 |
| Tržnica · Market | 160🌾 150🪵 100🪨 80📜 | Vas | 1 | +200 cap on all resources, better merchant trades (2.5×) |

The base population cap is 2.

A workplace brings its person (the bee house its beekeeper, the smithy its smith, the market its winemaker, the school its
teacher: VILLAGERS.md, "A building brings its person"), who needs a bed: while every bed is taken it can't be built, and its
card offers the way on, a dwelling to build or upgrade in one tap (or what to gather for it). Where no room can be made
now it isn't held back, and its person moves in once a bed is free.

The palisade takes no plot (nor does the tent once it has moved to the pond: see "The tent moves to the pond"): it goes up round the clearing, a ring of stakes just inside the forest's edge that grows with the clearing from age to age. It opens in a gate where the road leaves the clearing (to the left too from Zaselek, over the bridge) and where the path goes down into the woods, a lintel over the posts where they stand level. The stream runs along the clearing's upper left rim, and where the ring runs along it, it keeps to the stream's far bank all the way, the stream inside it (the bridge's gate stands on that bank): it never switches banks between two gates, and it crosses the water only where the stream comes in and where it goes out, in a water gate each time, closed by a row of shorter stakes standing in the water with gaps for the current. Every gap in the ring is a gate. A footpath of stepping stones goes down to a landing at the water on the village's bank, inside the ring (the way to Ob potoku, see [SCENES.md](SCENES.md)); a ring that runs by the stream on the village's side instead (the campfire's small clearing, with the stream outside it) opens there in a small river gate, a postern, for the footpath. From level 2 torches burn at the road and path gates. A tap anywhere on it opens it. A village from before gives the palisade's plot back on its next tick.

**The layout.** The plots stand in rows along the road, 5.5 cells apart in a row and 7 from row to row, every other row shifted along, each plot a little off the line; plot 0 is the nearest the fire, and an age's plots are the first ones (`VillageLayout`). The rows lie that far apart so that a building in front doesn't hide the one behind it: wherever two plots stand one in front of the other, a house, a hayrack, a smithy, a school, a hut or the church on the front one hides less than half of a beehive, a tent, a well, a market stall or a hut behind it. No plot stands in the stream, on its banks or where people sit by the water. The clearing (and the palisade round it) is the outermost plot of the age plus a margin. The village screen shows the whole village of the current age at first, and zooming in brings its details closer: on a 1080 px wide phone the canvas is 360 px across at 3 screen pixels a pixel (`SceneFit.town`, 320 px at least), zooming in to 15 (`CameraRig.town`); the Home header's strip shows the whole village too. A saved village keeps its buildings' plots (a lone kozolec aside, see below); only where each plot lies on the map changed.

**Beside what it belongs with** (`Placement`: the plot the game suggests, the ★ of the plot chooser below). A field, a hut, a house and a bee house go beside one of their own kind first (`Placement.KIN`: the fields make one big field, the huts and the houses a street, the bee houses a row). Buildings that a scene shows together stand together on the map: the kozolec by a field and a field by a kozolec (the field's scene has the hayrack in the field; a field goes beside another field before it goes beside a kozolec), and the linden, the well, the church and the market by each other (the village square). The suggestion is the free plot of the age nearest such a building, if that plot is its neighbour (plot centres at most 8.5 cells apart: the next plot in a row, the nearest ones in the rows in front, behind and across the road). Otherwise, and for every other building, it is the next free plot, as before (so the first of the square's buildings goes nearest the fire). An older village moves its kozolec once: a kozolec farther than a neighbour from every field goes to the free plot beside a field nearest it, on the next tick, or when the next age brings a free plot there. Only its plot changes, and no other building moves (`GameState.migrated`). Once the learner has chosen a plot themselves (built where they chose, or moved a building), that move counts as done: the game never moves a building on its own after that.

**The learner chooses the plot** (`PlotChoice`, `ui/game/PlotChooser.kt`). The rows of plots stay (their spacing keeps the buildings from hiding each other); the choice is which free plot of the age.

- **Building.** A building card of "🔨 Gradi · Build" (or the goal's "🔨 Zgradi zdaj · Build now", or a dwelling to make room for someone a workplace brings) doesn't build at once: the map comes back with the chooser over it. Every free plot of the age is lit up and framed at its corners, a gold ★ floats over the plot the game suggests (beside what it belongs with, above), and the building stands on the chosen plot as it would, outlined in gold, a pointer over it; the choice starts at the ★. The camera frames the plots to choose from (closer when they're few), above the chooser's bar. A tap on a free plot (on it, or the nearest within a finger's reach) chooses it. The bar says what goes where ("📍 Parcela 5 · Plot 5", ★ when it's the suggestion) and the price, which is paid only then: "✓ Tukaj · Here" builds on the chosen plot, "★ Predlog · Suggested" on the ★ one, "← Nazaj · Back" (or the phone's back) goes back to the sheet it came from with nothing spent. A dwelling built to make room goes back to the build sheet either way, so the workplace can go up next. The palisade takes no plot: it goes up at once, as before. TalkBack finds every plot to choose ("Parcela 5 · Plot 5, ★ Predlog · Suggested").
- **Moving.** A building's card has "↔ Premakni · Move" (not the palisade's, nor the tent's at the pond: they stand on no plot). It opens the same chooser: the free plots, and the other buildings' plots, where a tap on the building swaps the two ("⇄ 🛖 Koča · Hut" on the bar, both shown where they'd stand). The ★ is the free plot beside what it belongs with, when it doesn't stand there already; a building that belongs with nothing has no ★, and nothing is chosen until the learner taps. **A move is free**: it's a matter of arranging, and a price there would only be a wall. Only the plots change: the level, damage, the date it was built, who lives or works there, its scenes and its effect all stay (a field's scene shows its hayrack wherever the field stands). The chronicle says "↔️ Premaknjeno: Njiva · Moved: Field" or "↔️ Zamenjani mesti: Koča ↔ Njiva · Swapped places: Hut ↔ Field", and the building goes up again at its new place.
- **More of the same, side by side.** The maxima stay (fields 4, tents 4, bee houses 3 …); the ★ puts a new field beside the fields, and the chooser any other free plot.
- **Saves and visits.** A save keeps every building on its plot: nothing moves until the learner moves it, and a village that moves nothing renders pixel for pixel as before (`ClassicLandTest`). The plot is what the state has always had (`Building.plot`), so a town's public state carries it as before (`buildings`: type, plot, level, damage) and a visit, in this app or an older one, draws each building on the plot its learner chose.

## The land

Every village stands on a land of its own (plan 2, "Generated worlds"): its landscape and the seed its map is generated
from (`GameState.land`, `Land.kt`), laid out by `game/render/Terrain.kt`. The land is chosen when the village is founded
and never changes after.

**Villages from before keep their look.** A village founded before generated land has no land in its save. It stands on
the classic valley, the fixed layout every village had: the stream down the clearing's left side, the straight road, the
clearing's edge, the plots and their order, the trees, the rocks and the woodpile, the woods below all where they always
were. Its next tick marks it (`"land": {"kind": "classic"}`), and that mark is the only change: it renders pixel for pixel
as before (`ClassicLandTest`: digests of the fixed layout's renders of a Primorska village and a town, on every canvas
and two zoomed-in lenses, and a real Primorska village save compared before and after). An app from before generated land doesn't know
a village's land and would leave it out of the state it saves: the node keeps it (`PUT /game`), so a generated village
isn't marked classic by mistake.

**A new village's land is generated from its seed** (`GameEngine.newGame`: a valley with a stream by default). The same
seed always gives the same land; another seed another. What the seed decides:

- **The stream:** its course down the clearing's left side (how far out, how it winds), and so where the road's bridge
  crosses it and where the stream's bank is, where people fish (the clearing reaches out to take that bank in from the
  camp on).
- **The road:** straight through the plaza and past the first plots, then bending gently away on each side; the plots'
  rows bend with it, and the buildings' paths, the merchant's cart, the walkers' lanes, the palisade's gates and the road's
  spot follow it.
- **The clearing:** the ragged rim's noise and a broad bulge to one side (still 30 px past the age's farthest plot).
- **The plots:** the rows shifted along the road, their own jitter and order (the nearest the fire first, as always).
- **The woods:** how many trees the forest's first rows leave out, where glades open deep in the woods, where spruce
  takes over from beech, the trees' and bushes' own noise.
- **The spots:** where the rocks are at the clearing's upper right and the small woodpile at its lower right (each on
  every canvas of the town at every age).
- **The woods below** (a tall canvas): the path's winding, the pond, the meadow, the woodcutter's stack, the high seat and
  the charcoal pile, each where it is from the path.
- **The landscape's own**: the lake's place and size, the sea's shore, the rises, the vineyards' sectors (below).

**Landscapes** (`Landscape`, each generated from the seed as above, with its own shapes and horizon):

| Landscape | The land | The horizon |
|---|---|---|
| Dolina s potokom · A valley with a stream (`valley`) | The classic composition: the stream down the left, the woods all round, spruce and beech | The Alps and their foothills, as the classic |
| Griči · Hills (`hills`) | Gentle rises (the grass lighter on the slopes facing the light), vineyards' terraces where the woods would be behind the village and on its right (green, gold and red with the seasons, bare stocks in winter), open beech woods, a brook | Rolling hills in front, the Alps low and far |
| Jezero · A lake (`lake`) | A lake at the clearing's right, a little above the road, a path along its shore on the village's side, open shores | The Alps a little higher |
| Gore · The mountains (`mountains`) | A torrent, narrower and breaking white; spruce woods with pastures, boulders in the clearing and the glades; steeper rises | The Alps close and high |
| Obala · The coast (`coast`) | The sea along the left, surf on a sandy beach, the stream's mouth; the sea comes in lower down, but never over the woods' pond | The sea along the horizon, wider; low hills |

The coast is only for a region that has the sea (its culture's backdrop shows it: Friuli), see "The choice at setup"
below. A lake or the sea is a wall of the palisade: the ring ends on its shore either side (a `SHORE` opening, two
posts), and the plots, the landmarks, the trees, the animals and the forest's spot keep off the water.

What a generated land keeps is what the game needs (`LandsTest`, over 18 seeds of every landscape): the fire in the
middle; the 28 plots on open ground, off the stream, its bank's spot and the road, on every canvas of the town, the first
by the fire; the stream on the left (the palisade keeps to its far bank, the mill and the footbridge are on it), the road
through the clearing and over the bridge; the palisade's gates on the road and the path, no stake in the water or on a
plot, a way down to the stream; every named spot on the canvas, in the woods or the clearing as it should be, and a tap
on it opens it; every project's landmark placed; the wild animals outside the palisade; no tall building hiding most of a
small one behind it; a lake or the sea leaving room for all 28 plots with the town's clearing on its canvases (a smaller
lake, a shore further out where it wouldn't); a town of every landscape within the frame budget.

**The choice at setup** (`Founding`, `ui/game/FoundingScreen.kt`). A village is founded at the app's first start, once
the node has no village and the phone none either (offline too: it goes to the node when it answers): "🏡 Kje bo tvoja
vas? · Where will your village be?" shows the region (the culture packs of the village's language; the node plays one of
them, `lani-profile add --culture`), the landscapes the region offers (the coast only where its horizon shows the sea;
first the node's `--landscape`, else the region's usual one: Friuli's hills, the lakes of Kärnten and the Lake District,
Primorska's valley), and the village drawn where it would stand, at its first age, the whole land round it.
"🎲 Nov kraj · Another place" rolls the next seed of a fixed sequence (SplitMix64: the same seed always rolls the same
next one, and the same seed is always the same land); "✓ Tu bo moja vas · My village will be here" founds it there, its
first day ticked, for good. A village that exists is never asked about, and a village that arrives from the node or
another phone meanwhile stays.

**A visit shows the host's land.** A town's public state carries its land (`land`: the landscape and the number its map
is made from, not the village's seed; README, "What a town shows"), and the visitor's app draws the host's village on
it; a town whose bridge is older shows the classic valley.

## Upgrades

Every building can be upgraded one level at a time. Each age raises the top level by one, up to level 5. Upgrades are the steady use for resources between the word milestones, when every plot is full. Each also asks for the building's words (below).

| To level | Cost | Needs age | Effect | Words |
|---|---|---|---|---|
| 2 | 2 × build cost | Tabor | +60 % of the base effect | 8 of the building's words known |
| 3 | 3 × build cost | Zaselek | +120 % of the base effect | 14, none of them rusty |
| 4 | 5 × build cost | Vas | +180 % of the base effect | 20, none rusty |
| 5 | 7 × build cost | Trg | +240 % of the base effect | 26, none rusty |

Damaged buildings must be repaired before an upgrade. Warning hours (the watchtower) don't scale.

### Znanje gradi · Knowledge builds

An upgrade asks for the building's words besides the resources (`game/BuildingWords.kt`): the words of what is in it. Jan
found that a building could go up and be upgraded right away; now it grows with what the learner knows of it. Building new
asks for the resources alone (a first build is quick, a tent about seven answers' worth, but it isn't gated).

**The building's words**, in order: the things of its close-up scene as the learner sees it now (the scene seen in this
building, at its level: [SCENES.md](SCENES.md), "What the village has built"; a house its own room, "Houses and their
rooms"), one per word; where those are fewer than the level asks for, then the words of its scene's pack and of its type's
pack in its culture (the table below), in the pack's order. Upgraded, a room shows more things (a kitchen's tiled stove and
the table laid at level 2, the clock and the holy corner at 3), and the next level asks for those: a hut's kitchen has 11
words at level 1, 22 at level 2 and 24 at level 3, so its level 2 asks for 8 of the 11, level 3 for 14 of the 22, level 4
for 20 of the 24, and level 5 for the 24 and 2 words of V kuhinji's pack. A building with fewer words asks for all it has.

**Known** is what the word count means (`Stats.learned`): answered right at least once and not failed since. A word
counts as soon as it's answered right, in a word pack, a scene's words or the run below; the next day's review stays the
judge. A word the learner has under another card counts (the same word, as the node compares them: "voda" from a lesson is
the kitchen's water). A rusty word (its last review failed, see "Rusty words") doesn't count, and from level 3 on a rusty
word among the building's words holds the upgrade back until it's polished, however many others are known.

**The numbers** (`BuildingWords.NEED`: 8, 14, 20, 26) make an upgrade a few minutes of real practice, not an hour: each
level asks for the words before it and 6 to 8 more, one run. A new word takes about half a minute (met, then asked twice),
a rusty or half-learned one about ten seconds; eight new words are about four minutes.

**On the building's card**, under the upgrade's price and its moba (`UpgradeWordsCard`): "🧱 Za stopnjo 2: besede · For
level 2: words 5 / 8" with a bar, where the words are from ("🍳 V kuhinji · In the kitchen", or the pack), the missing
words as chips (🔩 on a rusty one; eight shown, then "+n"), a line when rust holds it back ("🔩 Od stopnje 3 naprej nobena
ne sme biti zarjavela · From level 3 on, none of them may be rusty"), and "🎯 Vadi jih zdaj · Practise them now". Known:
"✅ … 8 / 8". The upgrade's buttons wait for the words, the moba's too: the neighbours bring timber and stone, never the
words. The resources part is as before, with its own ways on.

**"🎯 Vadi jih zdaj"** (`UpgradeWordsScreen`) is a short run of exactly the missing words: the rusty ones first (every one
of them from level 3), then those met but never answered right, then new ones, in the building's order, as many as are
missing; at most 8 new and 12 to review in one run. The new ones are met one by one (their card, as in a pack), then every
word is asked: the new ones twice (recognition, then recall), the others once, never the same word twice in a row. What was
answered is saved like a pack's words (by pack) and a review, and counts on the phone at once, offline too; a word not
answered (the run left early) isn't saved. The answers pay the village like any practice. Its end says how the words stand
and offers "⬆️ Nadgradi zdaj · Upgrade now" when the upgrade can be done right then (the village shows it going up), else
the run again for what is still missing; "Naprej · Continue" goes back to the village with the building's card open.

**A way to make room**: a workplace whose person has nowhere to live offers to build or upgrade a dwelling (VILLAGERS.md,
"A building brings its person"). When that dwelling's upgrade waits for its words, the way is "🎯 Vadi besede za kočo ·
Practise the words for the hut ›": the run, then its card.

**Each building type's pack** (`BuildingWords.PACKS`: one table for every culture pack; a test checks that every culture
has one for every type, in its language, of its own packs and not a festival's, with at least 8 words). Primorska's
buildings mostly have scenes, and its packs are theirs (the palisade, which has none, the forest's, whose trees are its
stakes; a house whose room nobody lives in yet the home's). The other cultures' villages have no rooms to step into yet
(their scenes are the campfire and the horizon), so each building asks for the pack of what it is for: the campfire's for
the tent, food for the fields, the hayrack, the bee house and the market, the house and kitchen for the hut, the village
for the well and the palisade, the greetings for the linden (people meet under it), the family for the house, the
culture's landscape for the watchtower (the view from it), the charcoal pile for the smithy (its fire), the night sky for
the church (over its steeple), the numbers and the time for the school.

| Building | Primorska | Friuli | Kärnten | Lakeland |
|---|---|---|---|---|
| Tent | v-sotoru | al-fuoco | am-feuer | by-the-fire |
| Field | na-njivi | cibo | essen | food |
| Well | na-vasi | in-paese | im-dorf | in-the-village |
| Hut | v-kuhinji | casa-e-cucina | haus-und-kueche | home-and-kitchen |
| Hayrack | na-njivi | cibo | essen | food |
| Bee house | pri-cebelnjaku | cibo | essen | food |
| Palisade | v-gozdu | in-paese | im-dorf | in-the-village |
| Watchtower | na-stolpu | il-mare | am-see | on-the-fell |
| Smithy | v-kovacnici | alla-carbonaia | am-meiler | at-the-pit |
| Linden | na-vasi | saluti | gruesse | greetings |
| House | dom-in-hisa | famiglia | familie | family |
| Church | v-cerkvi | il-cielo | der-himmel | the-night-sky |
| School | v-soli | numeri-e-ore | zahlen-und-uhrzeit | numbers-and-time |
| Market | na-trznici | cibo | essen | food |

**What stays**: a level reached stays (no building loses one; only the next upgrade asks). Where the phone doesn't know
the learner's words yet (the dashboard not loaded: offline at the start) or has no word of a building to teach (no scene,
its pack not on the phone yet: the card fetches it), the upgrade asks for none rather than wait. On a visit to a linked
town nothing of the learner's village is upgraded. `SimulationTest` plays the upgrades without the words (it models the
resources): the run of a few minutes comes on top of its sessions.

## Ages

The ages are milestones on the way to the learner's target level (`Dashboard.targetLevel`, B2 unless the learner profile says otherwise): Vas at A2, Trg at B1, Mesto at the target. The words a level needs come from `data/Stats.kt` (`wordsToReach`: A2 300, B1 1000, B2 2000, C1 3500, C2 5000; A1 counts as A2). Each age needs a share of the target's words, at least its minimum, and always more than the age before (`Catalog.ageWords`).

| Age | Plots | Buildings needed | Cost | Words learned | Friends |
|---|---|---|---|---|---|
| 🔥 Ogenj · Campfire | 2 | (start) | – | – | – |
| ⛺ Tabor · Camp | 5 | a tent | 20🌾 10🪵 | 10 | – |
| 🛖 Zaselek · Hamlet | 9 | palisade, field | 170🌾 80🪵 60🪨 | 3 % of the target, at least 40 | 1 |
| 🏘️ Vas · Village | 14 | church, linden, 2 huts or houses | 500🌾 320🪵 130🪨 110📜 | 15 % | 3 |
| 🏛️ Trg · Market town | 20 | market, school, well | 800🌾 750🪵 700🪨 650📜 | 50 % | 5 |
| 🏰 Mesto · Town | 28 | watchtower, smithy, 5 houses, 2 hayracks | 1200🌾 1100🪵 800🪨 700📜 | 100 % | 8 |

Words learned per age for each target:

| Target | Tabor | Zaselek | Vas | Trg | Mesto |
|---|---|---|---|---|---|
| A2 (or A1) | 10 | 40 | 45 | 150 | 300 |
| B1 | 10 | 40 | 150 | 500 | 1000 |
| **B2** (Jan) | 10 | 60 | 300 | 1000 | 2000 |
| C1 | 10 | 105 | 525 | 1750 | 3500 |
| C2 | 10 | 150 | 750 | 2500 | 5000 |

The last free plots of an age are kept for the buildings the next age needs: with no more free plots than
required buildings still missing, only those can be built, so a village can't block its own way on. The palisade
needs no plot, so none is kept for it, and it can go up when every plot is built.

"Words learned" counts the vocabulary items in spaced repetition that were answered right at least once and not failed since: `repetitions` ≥ 1 or `mastery_level` ≥ 1, or, before the first review, answered right while being learned (a word pack's session stores that answer as `last_quality` ≥ 3), and not rusty (below). So a word counts as soon as the pack is done, and a failed review takes it out, whatever its mastery, until a review gets it right again. A card added without an answer (looked up in a dialog, `source: "lookup"`) doesn't count. It links the village to real progress: the word milestone is the real gate. The app says "you know N words" with the same count (the word packs, the home road, the progress screen); "tracked" is kept only where it means the cards under review.

**Rusty words** (🔩 "zarjavele besede", `Stats.rusty`) are the vocabulary items whose last review failed: reviewed at least once (`total_reviews` ≥ 1), `repetitions` 0 and `last_quality` below 3 (update-db.py's SM-2 resets a failed card's repetitions and keeps its low quality). A word learned in a pack and never reviewed can't be rusty (answered wrong there, it is just not learned yet), and neither can a looked-up card. A rusty word isn't learned, and while there is one, the next age waits for it, however many words are learned: the step "🔩 3 zarjavele besede · 3 rusty words" joins the age's steps (only while there is rust), with the line "3 zarjavele besede: očisti jih (1 min) · 3 rusty words: polish them (1 min)" (about ten seconds a word). It is a path, not a wall: the step's "🔩 Očisti · Polish" opens a short review of just the rusty words (at most 20), due or not, right away, and a right or almost-right answer polishes a word at once, so the age can come the minute after. The answers go through the usual `POST /reviews` and SM-2 (update-db.py takes a review of any item, due or not), so a polished word comes back in the next day's review, which stays the honest judge: failed there, it rusts again. The app counts a polished word at once, before the node has the review (offline too). Where rust shows:

- The review's end screen says how many words its answers left rusty, "🔩 3 besede so zarjavele: očisti jih zdaj ali jutri · 3 words got rusty: polish them now or tomorrow", with a "🔩 Očisti jih zdaj · Polish them now" button (a polishing run shows the same for a word that fails again).
- The village's goal is "🔩 Očisti zarjavele besede · Polish the rusty words" when only rusty words, words and friends are missing (polishing counts the words again, in a minute); a village still growing (buildings or resources missing) offers the same button under its checklist.
- Every step that holds an age back leads to something Jan can do now (`stepAction`): build, gather, learn, polish, help; only a damaged building is repaired from its own sheet.

An age once reached is never taken back by rust.

"Friends" are villagers who live here at friendship level 2 ("Prijatelj · Friend", 30 points) or more (`Bonds.friends`; a friend who moved away doesn't count). They are a step of the age like the words, with a "💬 Pomagaj · Help" button to the register. Zaselek needs 1, Vas 3, Trg 5 and Mesto 8: the ages are long (they follow the target level), and the people carry them. The cast gives enough people for each age: Micka, Luka, Janez, France and the children from the start, Ančka and Anton from Tabor, Tone, Marko, Vida and Nejc from Zaselek, Mojca from Vas (13 in all), and the people who move in. Requests, talks, gifts, the village projects, the festivals and the day's surprise all grow the friendships; `SimulationTest` checks that the friends never hold an age back for B2 and that eight are within reach long before Mesto's words (see "Pacing").

An age once reached is never taken back: a village already past a milestone that the target raised keeps its age, and only the next age waits for the new number.

## Pacing

`SimulationTest` plays 60 days of three learner profiles against the engine, for Jan's target, B2. Jan starts with 23 learned words and their first tent. A word-pack session adds 6 learned words. A review answered wrong (15 % of them) leaves its word rusty: it doesn't count, the rusty words come back first in the next day's review (right or almost right 85 % of the time), and when they are all that holds the next age back, the sim player polishes them at once. There are at most 4 to 8 rusty words at a time; polishing comes up in 2 to 4 sessions of a run, and no age comes later for it.

The people are modelled on the app: the cast moves in by its order once its age and home are there (Tone with the smithy, Mojca with the school), only who lives here asks for help (a tutor's quest waits for its giver), leads a project or is in a surprise, and friendship grows as in VILLAGERS.md. The sim player talks with the villager closest to becoming a friend, prefers that villager's request while the next age lacks friends, gives goods to who likes them, calls a moba whenever it helps, has the smith forge tools after the upgrades, does the day's project step with what the stores can spare, holds the weekly feast, plays the day's surprise and the festival that is on, and with full stores buys goods for the people who'd like them (and the pedlar's rare one).

| Profile | Practice | Words a day | Tabor | Zaselek | Vas | Trg |
|---|---|---|---|---|---|---|
| Casual | 10 min: reviews daily, a word pack every other day, one village run on the other days | 3 | day 1 | day 13 (words: 13, a friend: 6) | about day 93 (3 friends: day 18) | about day 327 (5 friends: day 38) |
| Committed | 30–40 min: reviews, a word pack, a grammar module or a talk scene, family every third day, three village runs | 6 | day 1 | day 7 (words: 7, a friend: 2) | day 47 (words: 47, 3 friends: 8) | day 163 (words: 163, 5 friends: 14) |
| Binge and gap | 5 days of an hour each, then 10 days away, again | 12 on active days | day 1 | day 4 (words: 4) | about day 64 (3 friends: 16) | – |

Friends come well before the words for every profile, also at 1 / 3 / 5 / 8 friends: Mesto's eight by day 17 for the committed learner and day 33 for the binge learner; the casual learner has eight by day 58. For a low target (A2: Vas at 45 words) the friends are the slower gate. In 60 days the committed learner earns about 310 🤝 (about 45 mobas) and has tools from 12 villagers; the casual learner about 90 🤝 and 6–9 tools.

**The long ages.** For B2, Vas runs from 300 to 1000 words: for the committed learner from day 47 to day 163. In the 170-day run its six projects (35 steps, a step every day or two) and the three of Zaselek are all done in Vas, and the village has something to build until day 145. Before the projects it was complete by about day 106 and waited for Trg with full stores, with the weekly feast as the only use for them. In the last weeks of Vas the feast, the pedlar's goods and the festivals are the uses for the stores, and every session still has the day's surprise; the stores are never all full for more than 2 sessions in a row. The run has 141 surprises and 7 festivals (the grape harvest to pust). Zaselek (60 to 300 words) has its three projects (the mill came in addition to the maypole and the bridge) and is complete from about day 40 of 47.

The test asserts these properties for all three profiles and for six other seeds, plus a committed learner who never talks, and runs the committed learner 170 days, to Trg:

- An age comes at most 3 active days after its word milestone.
- While the age still has something to buy (a plot, an upgrade, a forging, a project step), every session buys something (build, upgrade, repair, forge, project step, feast, goods, next age) or visibly gets closer to what the learner saves for. At most one session in a row does neither; two for the casual learner, whose one village run every other day an event may take, so an upgrade waiting on 📜 can wait two sessions.
- Once the age is complete, the learner waits for words: at most a week of sessions in a row without spending (the feast comes once a week).
- Every session from Tabor on has something new: the day's surprise (played, or the pedlar to see), a festival or a project step.
- The stores are never all full (95 % or more) for more than a week of sessions in a row.
- No resource sits at its cap for more than 5 days while another is below a quarter of its cap.
- After each 10-day gap, the village is repaired, fed and warm again within 2 active days.
- Talk brings at least twice as much 📜 as any other source. Without talk, 📜 from quests, the linden and the modules still reaches Vas on time.
- The 170-day run: the Vas village has something to build until day 130 or later, every project of Zaselek and Vas is done in Vas, and eight friends come within the 170 days.

What changed in the properties, and why: "complete" now counts the project steps (they are something to buy), and so does "buys something"; the two new properties (something new every session, never all stores full for a week) and the 170-day ones check what the projects, the calendar and the day's surprise are for. The sim now also tells the engine who lives here (`GameState.residents`), so project leaders, surprises and the friends an age counts follow the cast, and a feast or a festival brings everyone who lives here closer (before, the sim's village had no named residents). Since the palisade stands round the clearing on no plot, the sim player builds a hayrack on the plot it gave back in Tabor. Since the tent moves to the pond in Zaselek (see "The tent moves to the pond"), the sim player plays Luka's request the day he asks, besides the day's runs and on its own dice (like polishing), and builds the next building of its plan on the plot the tent gives back. With rusty words the sim's reviews fail, the player polishes when rust alone holds an age back, and it plans its saving with the rusty words counted (they are a minute away). Its project step is now the one that spends most from the fullest stores, not the first it can afford: in the 170-day run, late Vas spends 🌾 on the food-heavy steps while 🪨 sits at its cap, and with the first step that streak ran 3 to 14 days depending on the dice (this seed had 4 by luck, and rust's few extra answers made it 9 to 14); with the fuller-store step it is 4 to 9 across eight seeds, and 3 here. The 60-day runs keep it at 5 or less for every seed.

## Upkeep (daily tick)

- The engine applies upkeep once for each calendar day since `lastTick`, for at most 14 days. Days are dates, so month ends, leap days and summer time do not matter. A second tick on the same day changes nothing. The first tick only records the day (and `foundedOn`).
- If the date goes back by one day (a time zone change), the tick waits for the date to catch up. If it goes back by more (the phone clock was set far ahead at the last tick), the village starts again from today: no upkeep for the jump, and dates and deadlines set in the future come back to today. An unreadable date counts as a first tick.
- Villagers eat ⌈1.5 × villagers⌉ 🌾 each day, less with Micka's recipe (see "The chest"). The fire burns 2 + age index 🪵 each day.
- If there is enough wood, fire +10. If not, fire −15. The fire never goes below 10.
- If there is enough food, morale moves 5 per day toward its target (50 + linden, well and church bonuses, + Anton's candles and the children's keepsakes, at most 100). Morale above the target (after a festival or a feast) goes down by 2 per day. If there is not enough food, morale −10. If the fire is below 25, morale −5.
- If morale stays below 25 for 3 days, a villager leaves. The village always keeps at least 1 villager. If there is enough food and morale is 50 or more, a villager joins, up to the population cap.
- From the 4th missed day, the rules are gentler: consumption is halved, penalties are smaller, and morale does not go below 30. Upkeep never destroys buildings.

## Events

- There is at most one active event. The learner has 36 h to play it, or 48 h with a watchtower (and more with Ančka's radio and Nejc's horn). When the time runs out, the next tick resolves the event as lost ("too late").
- There are no events at Ogenj or in the first 2 days. After that, the daily chance is 20 % + 3 % per age (Tabor 23 %, Mesto 35 %).
- Strength is 1 to (age index + 1), capped at 5. A bear gets +1.
- An event has 5 + strength questions, against the clock (`RunTime.limit`, pure). A run that listens gets 60 % of what
  its questions take by the intro's estimates (`RunTime.seconds`: a sound whose meaning is picked 20 s, a gap by ear
  25 s, word chips by ear 30 s …), when that is more than the old flat time of 50 s and 4 s a question; a run that
  doesn't listen keeps the flat time. Either way up to the next half minute, so no event's clock is harsher than before.
  A storm of 6, 8 and 10 questions (a third of them written by ear) gets 1½, 2 and 2½ minutes (it had 74, 82 and 90 s).
- **Said before it starts.** The intro says it plainly above what the run practises: "⏱ Izziv na čas: 8 vprašanj, 2
  minuti · A timed challenge: 8 questions, 2 minutes", and "Ura začne teči po 3, 2, 1 in počaka, ko se zvok nalaga ali
  predvaja · The clock starts after 3, 2, 1, and waits while a sound loads or plays". After ⚔️ Start: its sounds are
  got onto the phone first ("🔊 Pripravljam zvoke … · Getting the sounds ready …" when that takes a moment: they are
  fetched from the node while the intro is read already, three at a time, `Speaker.preload`; at most 20 s, then it
  starts anyway), then "Pripravi se! · Get ready!" 3, 2, 1, then the first question and the clock. The clock stops while
  a sound loads or plays (`Speaker.busy`; ⏸ beside the bar), so waiting for a line to be voiced never costs time.
- Only fully correct answers count towards a pass mark (events and quests). An almost-right answer still pays its resources.
- Leaving an event run before the first answer changes nothing. Leaving after one or more answers resolves the event with the answers so far, measured against all its questions.

| Event | Skill | Pass mark | Win | Lose |
|---|---|---|---|---|
| 🐺 Volkovi · Wolves | 🪨 grammar | 75 % − defence/2 %, 40–80 % | 8×str 🪨, 6×str 🌾 | lose (6 + 3×str) % of 🌾 and 🪵, morale −5, chance to damage a building |
| 🐻 Medved · Bear (Zaselek or later) | 🪨 grammar | 80 % − defence/2 %, 45–85 % | 1.5× the wolf loot | 1.5× the wolf losses, higher chance of damage |
| ⛈️ Nevihta · Storm (only if the phone can speak) | 🪵 listening | 60 % | 12×str 🪵 | lose (10 + 3×str) % of 🪵, fire −20, chance of damage |
| 🧳 Trgovec · Merchant | 📜 wisdom | 60 % | trade up to 15×str of the largest stock for 2× (market: 2.5×; Vida's scales: +25 %) of the scarcest resource, never more than its store can take. While he is in the village he also buys goods (see "The chest"). | nothing is lost |
| 🎉 Praznik · Festival (morale 60 or more) | 🌾 vocabulary | 50 % | morale +15, 10×str 🌾, 5×str 📜 | morale +5, 5×str 🌾 (expired: morale +3) |

Buildings are never damaged at Tabor or earlier.

**Help from friends' towns** (companion/README.md, "Friendship between towns"): from level 1 of a friendship between
towns, a linked town can send help against wolves, a bear or a storm (it costs that town 10 🌾 and 10 🪵). Each helper
(3 at most) makes a lost event take less: its losses and its chance to damage a building are divided by 1 + helpers. Help
that comes after the event is over brings its 10 🌾 and 10 🪵 instead. The event's intro names the helpers.

## Challenges

- Gathering: 7 untimed exercises, mostly of the chosen resource.
  - Vocabulary exercises come from `ReviewPlanner`.
  - Listening (`ListeningMix`, pure): mostly heard (a phrase heard, its meaning picked), at most every third question
    written (the third, the sixth, …): untimed, a dictation of a short phrase (4 words at most; a longer one is heard and
    its meaning picked, and a module's long dictation is left out). Against the clock (an event) nothing is typed whole:
    **word chips by ear** (the phrase heard, its words tapped in order from a bank with one or two extra words; a phrase
    of 3 words and more) and **a gap by ear** (the phrase heard, shown with one word missing, that word typed), in turn;
    a module's dictation is written by ear too. Full hear-and-type dictation stays in untimed practice.
  - Grammar and wisdom exercises come first from published modules.
- If modules do not supply enough exercises, cards fill the gap: tiles or typing for grammar, translation for wisdom. If the phone cannot speak, listening becomes reading. The intro tells the learner when this happens.
- The `skills` and `cardIds` lists match `exercises` one to one.

## Quests

- **Local quests.** The engine keeps up to 3 open local quests, each from a different villager (see "Villagers" below). The culture pack's `quests.json` holds the quest templates (Primorska: 26); a villager with an open quest does not get a second one.
  - Each quest uses 5–6 cards and expires after 3 days. New quests come on the next new day.
  - A template names card categories (topics) that its story is about, for example `greetings`, `politeness`, `food` or a word-pack id. A card fits when its spaced-repetition `category` is one of the topics or contains one as a word (`grammar_cases` fits `grammar`).
  - With at least 4 fitting cards, the quest uses them (weakest first), fills up from the other cards, and records the category. Villagers with a fitting story come first. With fewer fitting cards, the quest takes the weakest cards of all.
  - Reward: 30 of the quest skill + 10 of the village's scarcest other resource (lowest share of its cap when the quest opens), multiplied by (1 + 0.3 × age index). The side reward is a way out when one resource is stuck.
- **Tutor quests.** Each module with a `quest` block becomes a tutor quest. See the next section.
- **Pass mark.** To pass a quest, at least 60 % of the answers must be correct (almost-right answers do not count). A pass gives the full reward and marks the quest as done. A fail gives one fifth of the reward, and the quest stays open.
- **Helping.** A pass also earns 3 🤝 and the giver's thank-you good (see "Helping people" and "The chest"), and +10 ♥ with the giver (VILLAGERS.md). Mojca's chalk makes every quest reward bigger.
- **The tent moves to the pond.** A special request, below.

### At most two at once

A villager can have more requests than Jan can take in: their own, and the tutor's tasks that pile up at the same giver
(the tutor adds a drill for a task Jan keeps failing, and publishes new modules). What shows is decided in one place,
`game/QuestQueue.kt`, and the map, the villager's card, the Scroll and Home all read it.

- **One bubble per person.** All the requests a villager has up now are one bubble over them (the first request's look),
  with a count badge when there are more ("⚒️ 2"). A tap opens their card, which lists them (also for someone away from
  the clearing). Requests of someone the cast doesn't know are one bubble per place; a tap on one with more opens the
  Scroll's requests. Zoomed out, a place's cluster counts every request.
- **Two up at most.** A villager shows at most two open requests (`QuestQueue.UP`): their own (the local one) first, then a
  task of the tutor's that comes back from its drill, then the tutor's newest. The rest wait in the Scroll under
  "📜 Kasneje · Later", after the requests that are up, each villager's together and in the order they come up, with why
  ("🕰️ Čaka na vrsto · Waits its turn"). They come up as those are done. Any request opens from "Later" and from the
  villager's card, where they are listed dim under the ones up.
- **A drill before its task.** When the tutor adds a smaller drill for a task Jan keeps failing (same villager: the drill's
  quest block names the task's module, `helps`), the task waits in "Later" behind the drill: "⏸️ Najprej · First: Kam? Na
  tržnico!". Once the drill is passed, the task comes back, first among the tutor's. Without `helps` (an older bridge drops
  it) the app finds the link itself: a module tagged `short` (the tutor's drills) at the same giver, published after an
  open task of the tutor's, and a grammar page that lists both modules (the book's pages and the pages their exercises
  name). A full module on a rule another one touches (the dual after "imeti in iti") is no drill. A link found is kept
  (`Quest.helps`); two requests that name each other wait for neither.
- **How close.** Every run of a request counts as a try (`Quest.tries`), and the best try is kept (`best` of `bestOf`,
  the best share of right answers). Its row on the card and in the Scroll, its intro and its result say it: "🎯 Najboljše
  7/12 · potrebuješ 8 · Best 7/12, you need 8  🔁 3 poskusi · 3 tries". A request not tried yet says nothing; its intro
  says the pass mark (a tutor's request too: "🏁 8 / 12 pravilnih").
- **Practise what's hard.** After 3 tries below the mark (`QuestQueue.HARD_TRIES`) and with no drill for it, a request
  offers "🎯 Vadi, kar ti ne gre · Practise what's hard" on its card row, its intro and its result, and stays open: the
  grammar book's "🎯 Vadi" run of the rule it missed most (the pages its wrong answers named, `Quest.missed`; else the
  request's own page, else its module's). With no page known, the tutor is asked for a shorter drill for it
  (`data.quest_drill`: the request, its module, tries, best and pass mark), which it writes with `helps`.
- **Old requests of the villagers.** A villager's own request that Jan never started waits in "Later" two days after it
  was asked (`QuestQueue.STALE_DAYS`): "💤 Vpraša te kdaj drugič · Asks you another day". The map shows it on the day it
  is asked and the next; it still expires three days after it was asked, and the villager asks something new on a later
  day. Started once, it stays up. The tent's move never waits by age, nor does a request asked before the app counted
  tries (it may have been started).
- **For the tutor**, `get_village` lists each open request's module, tries, best try, pass mark, the pages missed and the
  task a drill helps.

### The tent moves to the pond

"Šotor na novo mesto · A new place for the tent" (`TentMove.kt`; its words are the culture pack's, `quests.json` `tent_move`). Once people sleep under a proper roof, the tent doesn't belong among the houses any more, and Pastir Luka, who shelters in it, asks to take it out of the village to the pond in the woods below.

- **When.** On a new day, once the village is a hamlet (Zaselek) or more, a hut or a house stands, a tent stands on a plot (none at the pond yet), and Luka (the pack's giver) lives here; the chronicle says so like any request ("🐑 Pastir Luka te prosi za pomoč"). He asks once: a village from before gets the request on its next new day, and keeps its tent on its plot until the request is done.
- **The request** (`tent-move` among the local ones) never expires, waits while Luka is away (like a tutor's), and isn't one of the three (Luka asks nothing else meanwhile). It can be put off like any request ("Pozneje · Later"), and its bubble follows Luka. It pays like a request: 30 × (1 + 0.3 × age index) 🪵 and 10 × (…) of the scarcest other resource, 3 🤝, Luka's tolminc, +10 ♥ and the memory "dan, ko sva šotor prestavila k ribniku · the day we moved the tent to the pond".
- **The run is his description** (A1–A2, the prepositions of place and their cases): "Šotor bo stal ob ribniku, pod veliko smreko, blizu pomola. Pred šotorom bo ognjišče, drva pa bodo za šotorom. · The tent will stand by the pond, under the big spruce, near the jetty. The fire ring will be in front of the tent, and the firewood behind it." Jan hears it in Luka's voice (where the phone can't speak, reads it: "Luka: »…«") and answers four choices about it, in its order, 3 of 4 to pass:

  | Question | Options (the answer first) | Explains |
  |---|---|---|
  | Kje bo stal šotor? · Where will the tent stand? | 🪷 ob ribniku, 🏞️ ob potoku, 🌲 v gozdu, 🌼 na travniku | ob + mestnik (locative) |
  | Pod čim bo stal? · What will it stand under? | 🌲 pod veliko smreko, 🌳 pod staro lipo, 🌉 pod mostom, 🪨 pod skalo | pod + orodnik (instrumental) for where something is |
  | Kaj bo blizu šotora? · What will be near the tent? | pomol, most, mlin, kozolec | blizu + rodilnik (genitive): pomol → blizu pomola |
  | Kje bo ognjišče? · Where will the fire ring be? | pred šotorom, za šotorom, v šotoru, med drevesi | pred + orodnik; za šotorom is where the firewood goes |

  The options show their place with a picture where one shows it (all of a question's options or none, so a picture never gives the answer away); a 🔊 says only the words. Each question names the grammar book's page of its rule (`grammar` in `quests.json`: `kje-mestnik-orodnik` for ob ribniku, `orodnik` for pod veliko smreko and pred šotorom, `rodilnik-predlogi` for blizu pomola), so the first answer unlocks it, and the intro and every "why" lead there (see "The grammar book").
- **Passed**, the tent (the first on a plot, the one the places and V šotoru call the tent) leaves its plot for the pond: its plot is `NO_PLOT`, like the palisade's, and free for a building. The chronicle: "⛺ Luka je šotor prestavil k ribniku, pod veliko smreko. · Luka moved the tent to the pond, under the big spruce." It stays a tent for every rule: Tabor's "a tent", the population cap, the limit of four, upgrades, repairs and damage, the tent's place and its scene. A village with more tents keeps the others where they are. The palisade's migration (a building of a type that takes no plot gives its plot back) leaves it at the pond.
- **On the map** (a phone held upright: the woods below the village, see [SCENES.md](SCENES.md)), it stands as Luka described it, a small campsite on the pond's right bank (`render/Campsite.kt`): the tent under a big spruce, a plank jetty into the pond, a ring of stones in front of its door with a small fire (higher at night, lighting the tent), a stump to sit on, the firewood stacked behind it; the trees there make way. Every part is depth-sorted with the woods, and a tap on any of it is a tap on the tent: its card, "Vstopi · Go in" to V šotoru. The tent's bubbles point at it and who waits at the tent waits by its fire. A canvas without the woods (the Home header's strip) doesn't draw it; there the tent's place is the forest's.
- **In the friuli village** Pastore Davide asks, in Italian (machine-written like the rest of the pack): "Mettiamo la tenda vicino allo stagno, sotto il grande abete, accanto al pontile. Davanti alla tenda facciamo il fuoco, e dietro la tenda mettiamo la legna." Its questions practise the articulated prepositions (vicino allo stagno, accanto al pontile, davanti alla tenda; sotto il, with no a), and name their pages: `dove-preposizioni`, and `preposizioni-articolate` for accanto al pontile.
- **In the kaernten village** Hirte Florian asks, in German: "Das Zelt steht dann neben dem Teich, unter der großen Fichte, beim Steg. Vor dem Zelt machen wir das Feuer, und hinter dem Zelt liegt das Holz." Its questions practise the dative after the two-way prepositions when they say where (Wo? neben dem Teich, unter der Fichte, vor dem Zelt; bei dem: beim Steg), and name their pages: `wechselpraepositionen`, and `dativ` for beim Steg.
- **In the lakeland village** Shepherd Jack asks, in English: "Let's put the tent by the pond, under the big fir tree, next to the jetty. We'll make the fire in front of the tent and put the wood behind it." Its questions practise the prepositions of place (by, under, next to, in front of, behind: the noun never changes after them) and name `prepositions-place`.

## The grammar book

The village scroll, and from Vas the book, has a chapter "📖 Slovnica · Grammar" (`ScrollSection.GRAMMAR`, before the chronicle): one page per rule of the language learned, filled in as Jan meets the rules. A "why" after an answer is short and gone once read; the page keeps the rule. The pages are the grammar book's (`lani.grammar/v0`, the format and each language's pages, 44 of Slovene and 16 of the others, in [README.md](README.md#grammar-book)); the village state keeps which Jan has met (`GameState.grammar`, by "sl/<page id>": the day it unlocked, the answers on its rule, the latest three sentences said right and three slips; `game/GrammarBook.kt`).

- **Unlocking.** A page unlocks the first time Jan answers an exercise that names it (a module's exercise with `"grammar"`, a challenge's question such as the tent's, a letter's question or a way to show the pilgrim), when they play a module the page lists among its `modules` or a request that names it (`quests.json` `requests[].grammar`; the quest keeps it, `Quest.grammar`, and its challenge's intro lists it), when they pick a scene's wrong choice whose why names it (or, first time right, a turn that names it; see [SCENES.md](SCENES.md#a-why-that-is-a-rule-of-the-grammar-book)), when they review a grammar card its tags name, when a challenge's intro or a "why" opens it, and when the tutor writes the page or adds to it (Jan met the rule in the chat). The answer's feedback then says "📖 Nova stran v knjigi · New page in the book: Kje? Predlogi kraja"; a timed run's quick answers have no feedback box, so a notice says it when the run ends, and a scene's pick has none either: a notice says it at once. Every later answer on the rule counts on the page.
- **Another language.** The book is of the learner's own village's language (the pair's target, `L10n.ownPair`; "it/<page id>" in the village state), with that language's curated pages and the tutor's: the second learner's friuli village fills "📖 Grammatica · Slovnica" as they meet the articles and the articulated prepositions (the tent, the pilgrim's ways, Fabbro Bruno's orders, Maestra Elena's il or la), each page explained in their Slovene. A visit's scenes are another language's: their pages aren't in the village's book, so their whys have no link.
- **The chapter** lists the pages met, in the order met (then the book's), each with its title in both languages, its level, 🧑‍🏫 for the tutor's, and its stars once practised, and greyed "🔒 Še 12 strani · 12 more pages" for the rest. The book turns to a page like to a section ("‹" and back turn back to the chapter); the scroll shows it on a sheet of paper over itself.
- **A page** shows its level and who wrote it, the rule in Jan's base language (English; German or Italian for a learner from those), the tutor's addition ("🧑‍🏫 Učitelj dodaja · Your tutor adds"), the table (its words of Slovene looked up with a tap), the examples (each with 🔊 and word lookup, its translation, the tutor's marked 🧑‍🏫), "🙋 Tvoji stavki · Your sentences" (what they said right on the rule, their grammar cards on it), "🩹 Tvoje napake · Your mistakes" (the mistakes-db patterns its tags name, with their examples, and their slips here), "📈 Kako dobro · How well" in stars, the related pages they have met ("Glej tudi · See also"), and whether a machine wrote it.
- **How well** (0 to 5 stars, `GrammarBook.meter`): the answers on the rule (right of all, weighing up to 8) and their grammar cards on it (how far their reviews are: 5 in a row is 5, a failed last review 1), weighed together; each open mistake on it (its last answer wrong, not fixed since by a review or the run: see "Mastery and adaptive turns") takes a star off, two at most. Nothing practised yet says so.
- **"🎯 Vadi · Practise"** (not during a run): a short run of at most 8 (`Grammar.practice`): the exercises that name the page (the modules', the tent challenge's questions, and the letters' questions and pilgrim's ways that name it: `Surprises.onRules`), then the other exercises of the modules it links; with none, their grammar cards on it, reviewed like a polishing run (their answers go to SM-2); with none of those either, the tutor is asked for a drill ("Pripravi mi kratko vajo: … · Make me a short drill: …", `data.grammar_drill`) and the chat opens. The answers feed the village like practice and count on the page; the module exercises' results go to the tutor as a session to keep. The finish shows the stars and leads back to the page.
- **"💬 Vprašaj · Ask the tutor"** opens the chat with the page attached (`data.about_grammar`: its id, title, the rule as they read it, their answers, slips and mistakes on it).
- **From a "why" and an intro.** Beside the explanation of an answer whose rule has a page, and under a scene's why that names one, "📖 V knjigo · To the book: Kje? Predlogi kraja"; a challenge's and a module's intro list their rules' pages under "📖 V knjigi · In the book" (a request's own page first). Both open the page on a sheet over the run, without leaving it.
- **From a dialog, before answering and after.** A turn whose rule has a page offers "📖 Namig · Hint" beside the 🎤: the rule without the answer, and "📖 V knjigo" to its pages; a long press on a line said opens "🔍 Slovnica stavka · The sentence's grammar", each word's form and why with a 📖 to its page, and the tutor's explanation of the line once asked ([SCENES.md](SCENES.md#the-sentences-grammar-a-long-press-on-a-line)). Opening a page from them meets it, as the why's link does.

### Mastery and adaptive turns

How far Jan has a rule (`game/Mastery.kt`, `Masteries.of`) decides how a turn that tests it is asked, in the scenes' dialogs and in the choice exercises (`game/Adaptive.kt`). The rule is a page of the book. A turn tests a form when a wrong choice differs from the one right choice in a single word (`Forms.turn`: "Deset jajc, prosim." / "Deset jajce, prosim."); its rule is the page that wrong choice names (`grammar`, else guessed from its why: [SCENES.md](SCENES.md#adaptive-turns)). A choice exercise's is the page it names (`grammar`, or the run's), when its prompt has a gap ("Nimam ___. (I don't have time.)").

| Level | When | The turn |
|---|---|---|
| 🌱 not yet | the page's level is above the learner's, and the rule wasn't introduced: page not unlocked, no answers, cards or mistakes (see "Rules not yet" below) | not asked for: its form's wrong choices go; chosen about meaning, or heard and repeated (an echo) |
| 🆕 new | nothing practised on the rule: no answers, no grammar cards, no mistakes | chosen, as always |
| 🌱 learning | practised, not secure yet; or a mistake on it still open | chosen |
| ✍️ secure | 4 right in a row on it in the app (`SECURE_RUN`), at least 3 stars (the page's "How well"), no open mistake | the missing word typed into the sentence ("Deset ____, prosim."), with what Jan wants to say above it ("Hočeš reči · You want to say: Ten eggs, please.") and the letter chips (č, š, ž); graded like a cloze (`Grading`: č/š/ž and a slip in the stem forgiven, never the ending; the whole sentence typed counts too) |
| 🎤 mastered | 8 right in a row (`MASTERED_RUN`), begun on an earlier day than the latest answer, at least 4 stars, no open mistake | the whole sentence said: the dialog's 🎤 (or a speak exercise's screen), graded by `SpeechGrading` with a number the recognizer writes as digits read as the word the answer has ("2 ovci" → "dve ovci"); typed where nothing can listen |

- **The run** (`RuleRecord.run`, `since`, `last`): the right answers on the rule in a row, counted in the book like every answer: an exercise's, and a dialog turn's first try on the page it names (which it unlocks, as a pick does; a page the app only guessed counts nothing). **A slip steps it back a level** (`Masteries.afterSlip`): from mastered to secure (the run back to 4), from secure to learning (0). A record from before runs were kept reads its answers as the run when none was wrong, from the day it unlocked.
- **After the hint.** A dialog turn's right answer given after its "📖 Namig · Hint" ([SCENES.md](SCENES.md#the-hint-the-rule-never-the-answer): the rule, the question and its trigger, a model) counts right on the rule (the stars, "Your sentences") but leaves the run as it was, neither longer nor broken, nor its days (`GrammarBook.answered`, `hinted`; a record from before runs were kept gets its run written out). Hints alone never make a rule secure or mastered; a slip after a hint steps back as always.
- **An open mistake** keeps the rule learning: a mistakes-db pattern the page's tags name, whose last answer was wrong (`consecutive_incorrect`), whose card (the spaced-repetition `error_pattern` item of the same id) no review got right on a later day than it last happened (`last_seen`), and which happened on or after the day the run began. The page's stars leave out a mistake fixed that way too (`GrammarBook.stillOpen`).
- **A turn of several rules** goes by the weakest. A turn is chosen when one of its form's wrong choices names no rule the book has (a word's meaning: žejen for lačen), when it has two right choices, in a story (reading practice), and when it is a tap turn, answered by tapping the scene's picture ([SCENES.md](SCENES.md#tap-turns-answer-by-tapping-the-scene): "Kje sem?", and Jan taps the door). An exercise against the clock and a listening one are chosen.
- **Never a wall.** "✋ Izberi raje · Let me choose" under a typed or said turn brings back its choices for that turn, and so does a wrong answer: a form typed that is one of the wrong choices is that choice picked (its reaction, its why); another is said as typed and the person is puzzled (the turn's `puzzled`, else "Hm? Kako, prosim?"); a wrong choice said is that choice; what the recognizer heard unclearly is said again. Each counts as a mistake, as a wrong pick does. An exercise's "✋ Izberi raje" brings back its options.
- **The page** says under its stars how its turns are asked: "🌱 Se učiš: izbiraš med odgovori", "✍️ Utrjeno: napišeš manjkajočo besedo", "🎤 Obvladano: poveš cel stavek"; "💬 Vprašaj" sends it to the tutor as `mastery` (`new`, `learning`, `secure`, `mastered`).
- **Jan's own traps.** Where a turn or a choice exercise tests a form, the wrong choice shown is one Jan mixed up before, when there is one (their mistakes' examples; [SCENES.md](SCENES.md#own-traps)), else the file's; never in a turn of a rule not yet.
- **A new learner** has no mistakes, so no traps, and every rule of their level new, so those turns are chosen; the rules above their level are not yet (below).

### Rules not yet

What the others say in a dialog stays natural; what the learner must choose or produce grows with the grammar introduced to them (`game/Introduction.kt`; [SCENES.md](SCENES.md#rules-not-yet-what-the-learner-is-asked-grows-with-the-grammar)). A learner at the start of A1 can't tell "z mamo" from "z mama": the instrumental isn't theirs yet, so a turn doesn't ask for it.

- **The levels** are the pages' `level`s (companion/grammar/sl, from docs/grammar-syllabus.md): A1 (28 pages) the present, biti, imeti and iti, gender and agreement, the plural and the dual, the nominative, the accusative and the locative (Kam? and Kje? with v, na, pri), the genitive after a no, numbers with nouns, dates and the time, the past, the future, the imperative, the modal verbs, se, the clitics, ti or vi, question words, adverbs, joining words, the double no; A2 (11) the genitive (whose, amounts, after iz, od, do), the dative, the instrumental (with, and where with pod, za …), the personal pronouns, the comparative, the conditional, aspect, the supine, svoj; B1 (5) reported speech, ki and kateri, the declension tables. The learner's level is the village language's (`Dashboard.levelIn`: the learner profile's `current_level` for the home language), the one their readings and stories are at; it rises when the learner finds the treasure (see "The treasure map").
- **Not yet** (`Mastery.NOT_YET`, below new): the page's level is above the learner's and the rule wasn't **introduced**. A rule is introduced when its page is unlocked (a module that names it played, an exercise or a challenge question that names it answered, the tutor's page or its extension published, the page opened from the book or a why) or the learner has answers, grammar cards or mistakes on it (its tags). A rule at or below the learner's level is never not yet.
- **Its turns** keep what is about meaning, or become echoes: the right line heard and repeated, "✓ Naprej · Next", nothing graded. Nothing counts on the rule there; no trap of Jan's, no typing or saying. A tap turn is tapped as ever. Exercises aren't gated: a module, a challenge or a letter that names the rule is how it is introduced (playing or answering it unlocks the page).
- **The car's drills** ([README.md](README.md#the-drills)): a transformation set of a rule not yet ("🔄 Preobrat": the comparative, "with …"), and a sentence built that asks for one, isn't played on the road; once the rule is introduced, it is.
- **Meetings** (`GameState.meetings`, "sl/<page id>": times, days, the first and the last day): each such turn passed meets its rules once. The chapter lists them greyed under the pages met: "🌱 Dajalnik · A2 · Srečano v pogovorih: 4× · Met in the dialogs: 4×"; a page opened later says it too.
- **Ripe to introduce:** a rule of the next level up (A2 for an A1 learner) met 3 times (`Introduction.RIPE`). The tutor hears of it: a session's report carries it (`data.grammar_ripe`: the learner's level and each rule's id, title, level, meetings, days, first and last day; an older bridge passes it on as it is), and so do the tutor's `list_grammar` (`ripe`, and each page's `met_in_dialogs`) and the morning snapshot (`grammar_ripe`). The tutor introduces it when the learner is ready: a module whose exercises name it, or the page extended for them (`publish_grammar` under its id); either unlocks the page.
- **The book opens it itself** after 6 meetings on 2 days or more (`Introduction.UNLOCK`, `UNLOCK_DAYS`), with "📖 Nova stran v knjigi · New page in the book: …", so no rule waits for a tutor. From then on its turns ask for it (new: chosen, then as its mastery grows). A rule two levels up (B1 at A1) waits for the learner's level however often it is met.
- **On the page:** a rule not yet (were its page open) says "🌱 Še ne: v pogovorih ga zaenkrat samo srečuješ · Not yet: for now you only meet it in the dialogs"; "💬 Vprašaj" sends `mastery: "not_yet"` and `met_in_dialogs`.

### A word's forms

A word Jan added (a word card's "➕ Dodaj med moje besede", a pack's word) is one review card, asked for its meaning. Once Jan knows the meaning, the same card now and then asks one of its forms instead, and which forms grows with the book: a sentence with a gap and the word given, „Jaz ____ na klop. (sesti)" → **sedem** (`game/WordForms.kt`; the review's `Variant.FORM`, chosen, and `FORM_TYPE`, typed).

- **The forms** are the bridge's (`GET /forms`, [README.md](README.md#word-forms)): each form of the lemma ("pres.1sg", "past.f.sg", "loc.pl" …) with the page of its rule, the pages it needs too, and the lines of the content where it stands as that word. The phone keeps them for the learner's one-word cards (`files/forms.json`, asked again after two days; `app/FormsController.kt`). An older bridge has none: every card asks its meaning, as before. A card whose word isn't a lemma the dictionary knows ("iščem", "sedem dni") has no forms.
- **Which forms** (`WordForms.locks`): those whose page and the pages they need aren't not yet ([Rules not yet](#rules-not-yet): the gating the dialogs use). The present by person (`glagoli-sedanjik`; biti's on `biti`, imeti's and iti's on `imeti-iti`), the past (`pretekli-cas`), the imperative (`velelnik`), biti's future (`prihodnjik`) and the negative forms (`zanikanje`), a noun's six cases (`imenovalnik`, `rodilnik`, `dajalnik`, `tozilnik`, `mestnik`, `orodnik`; the plural needs `mnozina` too), an adjective's genders (`pridevniki-ujemanje`) and its comparative (`primernik`). The dual (`dvojina`) comes late: its page must have been met as well. At A1 the genitive, the dative and the instrumental (A2 pages) wait until they're introduced.
- **When** (`WordForms.due`): a familiar card (three good reviews in a row, `ReviewPlanner.familiarity` 2) asks one at most every third good review, the first one spread over the deck by the card; at most a third of a review's cards ask one, so the meanings stay practised.
- **Which** (`WordForms.pick`): the form got wrong the last time it was asked first, then one not asked yet (the present and the singular first; the plural and the past later, the dual last), then the one asked longest ago. The phone keeps what each card asked (`files/forms-asked.json`, "sl/<card id>": the last 12 forms asked, right or not, the day, and the card's repetitions then), not the village state: it would grow with every word.
- **The sentence:** a real line first, one of the scenes' dialogs, the stories, the readings, the book's examples or a pack's examples where that exact form stands as that word (the bridge checks by the line's translation that it means this word: "Kralj sedi za mizo" is sedeti, not sesti), its translation shown. Else a frame: a verb by person ("Jaz ____.", "Ti ____.", "Oni ____."), its past by person and gender ("Jaz (♂) sem ____.", "Ona je ____."), its imperative ("(ti) ____!", "(vi) ____!"); a noun by the school's six questions, the case named with its question ("5. sklon · locative: pri kom? pri čem?") and a preposition in front (brez, k/h, Vidim, pri, s/z; it follows the word, s kruhom, z mlekom, h kmetu, so the noun is what's asked), the plural marked "mn.", the dual with a number ("pri dveh ____"); an adjective after a pronoun ("Ona je ____."). A frame needs a sure answer: one standard form in the dictionary's table, or a hand-checked table.
- **Choosing, then typing:** chosen among the word's own other forms (the endings are the test; the same tense or number first) until that form was once right, typed after (graded by `Grading`: č/š/ž and a slip in the stem forgiven, never the ending; the noun alone, with its preposition or the whole sentence); typed too when the word has too few forms to choose from. As any choice on a rule, it is typed once the rule is secure and said once mastered ([Mastery and adaptive turns](#mastery-and-adaptive-turns)).
- **What an answer counts:** a right form gives the word its usual quality (4 chosen, 5 typed). A wrong one leaves the word as known (quality 3: its meaning wasn't asked, the interval isn't reset) and counts on the form's rule instead: a slip on that page with both sentences ("Jaz sedi na klop." for "Jaz sedem na klop."), as a dialog's turn records one, so the page's "🩹 Tvoje napake" shows it and the rule's run steps back. The review's report (`POST /reviews`, `forms`) tells the tutor which forms were asked and which were wrong, to record in mistakes-db if it wants.
- **On the word's card:** a word Jan has shows "📚 Oblike · Forms": its present, negative and future by person (ednina, množina, dvojina), its past by gender, its imperative; a noun's six cases by number; an adjective's genders and comparative. The forms reached are filled in, the rest 🔒, with the page that opens them: "🔒 6. sklon · instrumental — Orodnik, A2" (a tap opens the page, which introduces it). A hand-checked table says it's machine-written.
- **Verb partners:** a tapped form two verbs share ("sedite": sesti and sedeti) shows the one the line means ("📍 tukaj · here", by its translation: "Sit down, sit down" → sesti) and the other under it as its partner, not as a second meaning: "↔ par · partner: sedeti — to sit" and how the two differ ("sesti: a movement, once · sedeti: a state that lasts"; an aspect pair: "kupiti: once, to the end (perfective) · kupovati: ongoing or repeated (imperfective)"), with its own ➕. A verb of the bridge's pairs says its aspect ("dovršni · perfective").
- **QA:** `companion/bin/qa --steps word-forms`: the dev bridge's forms of the deck's words, then the debug build's "forms" hook starts a review of the words with a form reached, each asking one.

### Words met in play

A word the learner has a card of, met in play and answered right, counts as a review of the card (`game/PlayReviews.kt`):
«Vidim, vidim»'s quick find now (SCENES.md, "I spy": the thing tapped within two clues), a dialog's word some day. Not
every time, and never worse than nothing:

- **Once a card a day**: not when the deck reviewed it today (the card's `last_reviewed`) nor when play counted it today
  already (`GameState.playReviews`, the day's cards; it syncs, so another phone doesn't count it again).
- **Due or nearly due**: its `due_date` today or before, or tomorrow (`PlayReviews.EARLY_DAYS`): a review the deck would ask
  soon anyway. A card the phone made from today's pack run, which the node hasn't sent back yet, has no due date: it doesn't
  count.
- **A good answer only**, quality 4 (`PlayReviews.QUALITY`): SM-2 leaves the card's ease as it is and lengthens its
  interval. A wrong tap, a find after more clues or a word shown counts nothing: no failed review, no quality that lowers
  the ease.
- It goes to the node as a review does (POST /reviews, persisted by update-db.py; the tutor hears it as an app review),
  when the game ends or is closed, with the minutes on screen.

## Zemljevid zaklada · The treasure map

The learner's level in the village's language (`current_level` in their learner profile) decides what is theirs: the
rules not yet (above), the stories told at it, the order of the readings, what a look back at a text costs. It never
rises by itself. The treasure map is how it rises: an opt-in test at the end of the level, with a big reward
(`game/Readiness.kt`, `game/Treasure.kt`, `app/TreasureController.kt`, `ui/game/TreasureScreen.kt`).

**Readiness** (`LevelReadiness`, pure). Three signs from the evidence, all needed:

| Sign | Ready when |
|---|---|
| 📖 The grammar | 70 % (rounded up) of the level's grammar pages that the curated content tests are secure or mastered (see "Mastery and adaptive turns"). A page is tested when a dialog's turn, an exercise, a challenge's question or a drill names it; where the content names none of the level's pages, all of them count. Slovene A1: the content names 26 of the 28 pages (not `skloni` and `vezniki`), so 19 must be secure. |
| 🔁 The reviews | Of the reviews of the last 14 days, of the cards of the level and below (the card's `difficulty`; a card above the level is a stretch, a card without one counts): 80 % right, and 30 reviews at least. |
| 📅 The time | 10 days of practice at the level (days with a session in the log), over 14 days or more since the level began (the profile's `learner.level_since`, which update-db.py sets when a report changes the level; else the day the profile was made). |

**The offer.** Once all three are there, the storyteller (the teller of the evening stories: Stari Janez; else the
riddles' teller) gives the map: the chronicle says "🗺️ Stari Janez je našel star zemljevid", a 🗺️ bubble waits by the
fire, Home has a row "🗺️ Stari Janez ima zate star zemljevid · Stari Janez has an old map for you", and the chest keeps it
("🗝️ Iz zakladov · From the treasures"). The map (an old sheet of paper) opens with his line, voiced: "Našel sem star
zemljevid … Si pripravljen poiskati zaklad? · I found an old map … Ready to find the treasure?", what the path is, the
signs, "🗺️ Vzamem zemljevid · I'll take the map" and "Pozneje · Later". Never forced: it waits until it is taken, and a
dip in the signs doesn't take it back.

**Before the offer.** The grammar book's chapter has "🗺️ Sem pripravljen · I'm ready" on top ("Preskus za raven A2 ·
The test for A2"): the map shows the signs, the level's pages still to secure (a tap opens the page), "Stari Janez misli,
da še nisi čisto pripravljen, a poskusiš lahko že zdaj: nič ne izgubiš", and the same button to start.

**The hunt.** Six stations at the village's landscape (its spots, there in every culture's village), in the path's order,
each a short run on the stage with the storyteller, built from content the app has; where the content has too little, the
learner's cards fill the station (a gathering run's way):

| Station | Where | What | Questions |
|---|---|---|---|
| ✉️ Pismo · The letter | na cesti · on the road | A reading of the level (a letter and one not read yet first; the level below where there is none; else two of the culture's letters by the road), read first, then its questions with the text hidden ("Read first, then answer") | its 3 to 5 |
| 💬 Pogovor · The conversation | na travniku · in the meadow | Dialog turns of the scenes whose choices test a form of the level's rules (a wrong choice differs from the right one in a word: `Forms.turn`), with the line before and what the learner wants to say, the form's choices only, one rule after another; then the modules' choice exercises with a gap on the level's pages | 6 |
| 🪨 Oblike · The forms | pri skalah · by the rocks | The exercises that name the level's pages (the modules', the challenges' questions), one per page first, and about as many of the car's transformations of the level ("🔄 Preobrat": »Micka kuha kosilo.« into the past, its words as chips with the old form among them) | 6 |
| 👂 Poslušanje · Listening | ob potoku · by the stream | The grammar book's example sentences of the level and below heard, their meaning picked; two short ones written as heard | 6 |
| 🕵️ Uganke · The riddles | ob ognju · by the fire | Stari Janez's riddles of the level and below (the car's riddle drill), else the culture's riddles by the road | 4 |
| 🎤 Na glas · Out loud | na preži · on the high seat | Short sentences said from what they mean (the car's sentences built, else the book's examples; 2 to 6 words), heard by the node's Whisper or the phone | 3 |

- A station passes at 70 % of its questions fully right, rounded: 4 of 6, 4 of 5, 3 of 4, 2 of 3. An almost-right or hinted
  answer pays but doesn't count, as in the requests. Its answers pay the village like any run.
- Where nothing can listen, the speaking station is left out ("🔇 Tu te nihče ne sliši: postaja odpade"), and where
  nothing can speak, the listening one; the hunt needs the others.
- Each station can be done on a day of its own, in any order. The map draws the path and the stations: passed green ✓,
  to try gold, blocked red, left out grey, and the X; a tap on a station to try starts it.

**Not passing.** Nothing is lost: the stores stay as they were, and the other stations can be tried the same day. The
station keeps the pages its wrong answers named most (at most two), and the map says where the path is blocked: "⛔ Pri
skalah: Dajalnik · By the rocks: The dative", with "🎯 Vadi · Practise" and 📖 for each page. A station without pages
offers its skill's practice: the reading corner, a listening run in the forest, new words, the quarry, a talk. It can be
tried again from the next day ("⏳ Spet jutri · Again tomorrow"), with other questions.

**Passing.** With every station passed, "⛏️ Izkoplji zaklad · Dig up the treasure": the coin, the new level ("Zdaj si na
ravni A2"), Stari Janez's "Čestitam! Vedel sem, da ga boš našel.", 40 % of each store's size (as much as the stores hold),
10 🤝, and the level's keepsake in the chest for good (🧭 Stari kompas for A2, 🏺 Rimska amfora B1, 🪔 Stara oljenka B2, 📜
Stara listina C1, 👑 Zlata krona C2; each +5 % of everything: `Treasure.effect`). The chronicle: "🪙 Zaklad je najden!
Zdaj si na ravni A2, v skrinji pa je 🧭 Stari kompas." State: `GameState.treasure` (the hunt: `from`, `to`, `offered`,
`taken`, each station's tries, best, the day it passed and its blocked pages, `found`, the `report` sent, `confirmed`) and
`GameState.treasures` (the level each treasure brought → the day: the keepsakes).

**The new level on the node.** The app goes by the new level at once (`ContentController.levelIn`, `Treasure.level`):
the new level's rules are the learner's (their turns are asked, new, instead of only met), the next level's are the ones
not yet, and the stories, readings and looks follow; until GET /state shows the level (`confirmed`). It queues `POST
/level` (`{from, to, stations: [{id, skill, right, total, minutes}]}`; README, "App API"), offline too: the bridge
persists it with update-db.py (the report's `level` sets `current_level` and `level_since`; a session
`/lani-app-treasure` with the stations' answers by skill, and a milestone) and tells the tutor (`level_up`: congratulate
the learner, plan the new level's rules). A node from before the treasure answers 404: the outbox sends the same as a
`session_end` whose report (its `level` too) the tutor persists with update-db.py, in the same place in the queue, and
says so ("📨 Nova raven: učiteljev strežnik je starejši, zato je šlo učitelju, da to zapiše sam"). While the node doesn't
show the level, the map and Home say "⏳ Učitelj nove ravni še ni zapisal · Your tutor hasn't recorded the new level yet"
with "📨 Pošlji znova · Send again".

**After.** The new level's rules come in through the gating and the meetings (see "Rules not yet"); its own treasure map
comes when its signs are there (the days at it count from the day the treasure was found).

## Helping people

🤝 Pomoč · Help is earned by helping the villagers, never by drills or gathering:

| Jan … | 🤝 |
|---|---|
| passes a villager's request (a local or a tutor quest) | 3 |
| talks with a villager (a role-play with the tutor playing them) | 2 |
| plays a scene's happening to its end (once a day each, like its pay) | 1 |
| answers a family challenge | 1 |
| helps with the day's surprise: reads someone their letter, finds Luka's lamb, shows a pilgrim the way, guesses a child's riddle | 1 |
| plays «Vidim, vidim» with a child to its end (the first game of the day with them; SCENES.md, "I spy") | 1 |
| (the host) a visitor from a linked town does one of the village's requests (companion/README.md, "Things to do on a visit") | 3 |

**Moba.** A moba is the Slovene tradition of neighbours coming to help build a house or bring in the harvest. When Jan builds, upgrades or repairs, they can call one: the neighbours bring part of the 🪵 and 🪨, and they pay for it in 🤝.

- Each neighbour covers 10 % of the 🪵 and 🪨, at most half of it. Neighbours are the people who live here, babies aside (a village whose people have no names yet counts its population).
- One 🤝 covers 5 🪵/🪨 at the campfire, growing with the age like quest rewards: 7, 8, 10, 11, 13 (`Catalog.mobaPerHelp`). With too few 🤝, the neighbours bring what the 🤝 pay for, in the same proportion.
- 🌾 and 📜 are always paid in full. A moba never makes possible what the age, a limit or the plots forbid, and a damaged building is repaired before it is upgraded.
- The build sheet has a "🤝 Moba" switch; the building sheet a "🤝 … z mobo · with a moba" button for the repair and the upgrade. Each shows what the neighbours bring and the 🤝 it costs: "−35 🪵 −25 🪨 za 8 🤝 · for 8 🤝". The chronicle says so ("Zgrajeno: Hiša · House · 🤝 moba: −35 🪵 −25 🪨").

The HUD shows 🤝 on the chest's chip (🧰); the stats count `helpEarned` and `mobas`.

## The chest

"🧰 Skrinja · The chest" (the HUD's 🧰 chip) holds what the villagers give Jan: tools, kept for good, and goods, to give or to sell.

**Between towns** (companion/README.md, "Things to do on a visit"): on a visit to a linked town, a request of its people done brings its giver's thank-you good, a good can be left there as a gift, and goods and resources traded at its market for the goods it spares; a guest's gift or trade comes into this chest the same way. A good from another culture's town becomes this village's own that looks the same (Friuli's 🍯 miele is Primorska's 🍯 med), else one of the same price and kind (`Guests.nativeGood`).

**What comes in is a present.** A good or a tool that comes into the chest (a request's thank-you good, a festival's good, a friend's tool or small good at a new friendship level) is shown on the result card of the run, the module, the scene's dialog or the talk as a wrapped present, after the resource chips (`ui/game/GiftReveal.kt`, what to show in `GiftLogic`). A tap, or a moment, unwraps it: the thing big, its name to tap for its word card (it's a word to learn) and to hear, the name in the base language, who gave it ("👵 Babica Micka: V zahvalo · As thanks", "Za praznik · For the holiday: 🍷 Martinovo · St Martin's Day", "🐑 Pastir Luka: Za prijateljstvo · For your friendship") and what it's for (who likes it, a rare good pleases everyone, a tool's effect). Then it flies into the chest, and the HUD's 🧰 chip has a red dot until the chest is opened next; the chest marks what came in "✨ Novo · New" (the unseen things are kept on the phone, `ChestNews`). TalkBack hears "Darilo: Potica · A gift: Potica" as it opens; with the system's animations off, it shows open at once. Tools, goods and who likes what are the culture pack's (`chest.json`, by villager id, not in the villager files); their numbers are the game's: a good names a price class (`Catalog.GOOD_PRICES`: small 10, plain 15, good 20, fine 25, rich 30, precious 35) and a tool an effect (`Catalog.TOOL_EFFECTS`). Someone the pack doesn't know (who moved in or was born here, or a tutor's villager) gives small goods and likes potica, bread and apples. Only who lives here (or visits today) gives or gets anything.

**Tools.** At friendship level 2 ("Prijatelj · Friend") a villager gives a tool of their trade; at level 4 ("Kot družina · Like family") the better one, twice as strong, in its place. A friend who jumps both levels gives the better one at once. Friends who were friends before the chest existed give theirs the next time they're in the village. The gift shows on the result card of the quest or talk, in the chronicle and as a notice: "Luka ti podari sekiro 🪓 · Luka gives you an axe: +10 % 🪵". It is a surprise (Jan): nothing announces it before it comes. The chest lists no gifts waiting, a villager's card and page show the friendship's ♥ and next level but not what it brings, and "Jutri · Tomorrow" never says a gift is one talk away.

| Villager | Level 2 | Level 4 | Effect (level 2; twice at level 4) | Forge |
|---|---|---|---|---|
| 🐑 Luka | 🪓 Sekira · An axe | 🪓 Ostra sekira · A sharp axe | +10 % 🪵 | ✓ |
| 🧑‍🌾 France | 🌾 Srp · A sickle | 🌾 Kosa · A scythe | +10 % 🌾 | ✓ |
| ⚒️ Tone | ⛏️ Kramp · A pickaxe | ⛏️ Jeklen kramp · A steel pickaxe | +10 % 🪨 | ✓ |
| 👴 Janez | 📖 Stara knjiga · An old book | 📖 Knjiga pregovorov · A book of proverbs | +10 % 📜 | |
| 👵 Micka | 🗒️ Recept · Her recipe | 🥮 Recept za potico · The potica recipe | villagers eat 10 % less | |
| 📻 Ančka | 📻 Radio · A radio | 📻 Nov radio · A new radio | events give 6 h more | |
| 🐝 Anton | 🕯️ Voščene sveče · Beeswax candles | ❤️ Lectovo srce · A licitar heart | +3 morale | |
| 👩‍🏫 Mojca | 🖍️ Kreda · Chalk | 📚 Učbenik · A textbook | quests pay 10 % more | |
| 🍷 Vida | ⚖️ Tehtnica · Scales | ⚖️ Medeninasta tehtnica · Brass scales | trades +25 % | ✓ |
| 🍇 Marko | 🛢️ Sodček · A small barrel | 🛢️ Velik sod · A big barrel | +40 to every store | ✓ |
| 👧 Zala | 🌼 Venček · A daisy chain | 🎨 Risba · A drawing | +2 morale | |
| 🧒 Nejc | 📯 Lesen rog · A wooden horn | 🔭 Daljnogled · A spyglass | events give 3 h more | |
| 👦 Tine | 🐴 Lesen konjiček · A wooden horse | 🧸 Medvedek · A teddy bear | +2 morale | |
| anyone else | a small good | a small good | – | |

**Readings.** Some things in the chest hold something to read: Micka's recipe (frtalja with spring herbs, A2) and her better one (walnut potica: the dough, the filling, the rising, rolling and baking, B1), Janez's old book (a page of an old pratika, the farmers' almanac: St Matthias's ice, the birds marrying on St Gregory's Day, A2) and his book of proverbs (12 Slovene proverbs, each with what it means, A2), Mojca's textbook (its page on the dual, A2) and Prešeren's Poems (the seventh stanza of Zdravljica, the anthem, B1). Their row in the chest has "📖 Preberi · Read"; a better tool keeps the first one's reading too, under its title. The reading (`ui/game/ReadingSheet.kt`, the logic in `ReadingLogic`) shows the title, a recipe's servings, its ingredients (in parts: "Testo · The dough") and numbered steps, or the page's lines (a proverb with its meaning); every word opens its word card (with the line), each line has 🔊 in the giver's voice, and its translation shows under it: "👁 Prevod · Translation" hides them all to read first, and 👁 on a line opens that one. TalkBack reads each line with its translation while it shows. It is read first, as long as Jan likes; "▶️ Začni · Start" hides it, and a question or two close it, one at a time (3 options each, the answer and why once one is picked; the first pick counts), "👁 Pokaži besedilo · Show the text" bringing the text back over the question (see "Read first, then answer"). The first time they're all answered they pay 📜 as answers do (`GameEngine.earn`: the day's first 40 in full, later ones half: two right answers are about 12 📜; less a little for looking back), and the reading is marked read (`GameState.read`, "primorska/potica"; `Readings.finish`); read again, it pays nothing ("Že prebrano · Read before"). Every time, the questions answered count as reading practice (see "The reading corner"). "🎤 Beri na glas · Read aloud" reads it aloud (see "Reading aloud"), and "📖 Branje" at the chest's tools opens the reading corner. What a thing holds is the pack's (`read` in `chest.json`, the reading in `readings/`, see "Culture packs").

**The story notebook.** Under the tools, its own section: "📓 Moj zvezek zgodb · My story notebook", every story heard by the fire written down by hand, a page each in the order they were heard, each evening under its day, the pictures as pencil sketches, "🎯 Preveri se · Test yourself" at a story's end (companion/SCENES.md, "The story notebook"). It opens over the chest and closes back to it; the reading corner lists it as one row, and the storyteller's card and a story's end open it too.

### The reading corner

"📖 Branje · Reading" (`ui/reading/ReadingsScreen.kt`, `app/ReadingsController.kt`) is on Home next to the other practice tiles, in the village book's grammar chapter and at the chest's tools. On top, one row for the story notebook ("📓 Moj zvezek zgodb · My story notebook", its stories and sketches): the stories heard by the fire are written down there, not shelved among the readings (companion/SCENES.md, "The story notebook"). Then it lists every reading of the village's culture pack (bundled: `readings/`, listed in `readings/index.json` by `app/build.gradle.kts`, `Cultures.library`) and the tutor's (`publish_reading`, served with the pack's by `GET /readings` and cached for offline; the node's copy of a pack's reading is the newer one), each checked as the pack's are (`Cultures.check`). They come in the learner's level's order (`Readings.corner`): at their level, then a step up (i+1), then the easier ones, then the harder; in each, the unread first, then the shorter. A row shows the kind, the level, the words to read and about how long they take (60 words a minute at A1, 20 more a level up), ✓ when read, and 🧑‍🏫 for the tutor's (machine-written). "💬 Prosi učitelja za novo branje" asks the tutor for one (`data.reading_request`). The Home tile says how many at the learner's level and a step up are still to read, the tutor's new ones in red.

Primorska's corner has, besides what the chest holds, Babica Micka's morning ("Jutro v vasi · Morning in the village",
A1: she gets up at six, bakes bread, Luka comes by with the sheep), Vinar Marko's letter ("Pismo od Marka · A letter
from Marko", A1: an invitation to the grape harvest on Saturday) and Stari Janez on the bora ("Burja · The bora", A2:
the stones on the roofs, the clear sky after it, the pršut it dries); each with five questions, one of every type, and
five or six new words. The chest's readings have their questions' types, a true/false statement (the potica: a word in
context) and their new words too. What was added is machine-written, not reviewed by a native speaker yet (the new
readings say so in their `review`).

A reading opens in the chest's reading view: its lines to read first, then, once started, its questions one at a time with the text hidden ("Read first, then answer"), each with its type ("Glavna misel · The main idea", "Podrobnost · A detail", "Beseda v besedilu · A word in context", "Drži ali ne drži? · True or false?", "Med vrsticami · Between the lines"): a true/false statement offers "Drži" and "Ne drži". Once they're all answered, the score and its new words, each with what it means and "➕ Dodaj" (a review item, `POST /words`, the line it stands in as the example); every other word opens its word card, with its own ➕.

### Read first, then answer

A reading exercise tests reading, not finding a sentence again: the day's letter (`ChallengeScreen`, `Challenge.text`: `Surprises.text`), a reading of the chest, the reading corner and the night sky's cards (`ReadingSheet`), and a story's "🎯 Preveri se · Test yourself" in the story notebook (`BookQuizSheet`: its text was read on the notebook's page, so its questions start at once) all go the same way (`ui/game/ReadFirst.kt`, the state `ReadRun` in `ReadingLogic.kt`):

1. **Read first.** The text shows with its voice (the letter's "🔊 Poslušaj · Listen" for all of it and 🔊 on each sentence; a reading's and a book's 🔊 on each line in the reader's voice), every word to tap for its card, the translation shown or hidden as before, "🎤 Beri na glas" where there was one. Under it: "💡 Beri v miru, kolikor dolgo želiš. Ko začneš, se besedilo skrije in vprašanja pridejo eno za drugim. · Read it in your own time, as long as you like. Once you start, the text hides and the questions come one at a time." and how looking back counts at Jan's level ("Prva dva pogleda sta brezplačna, vsak naslednji malo zniža plačilo · The first two looks are free; each one after that takes a little off the pay"; a book and a reading read before just say the text can be looked at again). Then "▶️ Začni · Start".
2. **Then the questions, one at a time,** with the text hidden, as the letter's always were ("Vprašanje 2 od 5 · Question 2 of 5", "Naprej · Next" after each in a reading and a book).
3. **"👁 Pokaži besedilo · Show the text"** above the question brings the text back over it (the question and what was typed stay under it; TalkBack skips them while covered) as often as needed; "🙈 Skrij besedilo · Hide the text", back, or the next question hides it again. A look counts only while the question is still open: after its answer, and once all are answered, the text is there to check, free.
4. **It counts, gently, by level** (`Peeks`, pure): looking back takes a little of what the run pays, never an answer, so a pass stays a pass; less at the lower levels. The level is the learner's in the village's language (`levelIn`, the level the reading corner and the storyteller go by, "Tvoja raven: A1"): a text's questions test the whole text, not one rule of the grammar book, so a rule's mastery (`Mastery.kt`, which decides how a turn is asked) doesn't fit; no level yet counts as A1.

   | Level | Free looks | Then each look costs | Looks 2, 3, 4, 5 cost together |
   |---|---|---|---|
   | A1 | 2 | 5 % each | 0, 5, 10, 15 % |
   | A2 | 1 | 5 %, then 5 % more each time (5, 10, 15 …) | 5, 15, 30, 30 % |
   | B1 | 1 | 10 %, then 5 % more each time (10, 15, 20 …) | 10, 25, 30, 30 % |
   | B2, C1, C2 | 1 | 10 %, then 10 % more each time (10, 20 …) | 10, 30, 30, 30 % |

   Never more than 30 % in all (`Peeks.MAX_COST`). The cost comes off everything the run pays: a reading's 📜 (`Readings.finish`, through `GameEngine.earn`), the letter's answers and its reward (`Surprises.finish`, a third of it too when it wasn't passed), each resource rounded and at least 1 of what it had. 🤝 and ♥ stay whole. A book and a reading read before pay nothing, so looking costs nothing there.
5. **The result says it kindly**, under the score: "👁 Dva pogleda v besedilo, brez odbitka · You looked at the text twice, at no cost", "👁 3 pogledi v besedilo: −5 % plačila · You looked at the text 3 times: −5 % of the pay", or, where nothing was paid, "👁 Dva pogleda v besedilo · You looked at the text twice" (`looksText`). The letter's result card shows it under its tally; the rest of the result screens are as they were. The Slovene is a noun phrase ("pogled v besedilo") so it needs no gender: the app has no way yet to address the learner as a woman or a man, and its older lines use the masculine ("Boj si zapustil").
6. **What the answers feed** is unchanged: grammar pages, cards, the village, reading practice. The reading report to the node carries `looks` (`POST /readings/done`; a node from before ignores it), and the tutor's `reading_done` note says "looked back at the text 3 times while answering".

The grammar book's "🎯 Vadi · Practise" still asks a letter's questions with the letter above them: it practises the rule, not the reading.

**What counts.** Every reading practice reaches the learner's reading skill (`skills.reading` in `mastery-db.json`, the progress screen's "👀 Branje"), persisted by the bridge like a flashcard review (`POST /readings/done`, update-db.py, no LLM; the tutor hears it as `reading_done`):

| Practice | Exercises | Right | Minutes |
|---|---|---|---|
| a reading's questions (the chest's or the corner's), answered to the last | the questions | right at the first pick | on screen, from opening it to the last answer |
| the storyteller's story heard to its end (`SceneController`) | the learner's turns (1 for a telling without) | right at the first pick (`DialogRun.missedTurns`) | on screen, from its first line to its end |
| a story's "🎯 Preveri se" in the notebook, its questions answered to the last (a reading, `id` `book:<story>`) | its questions: the telling's turns, at the level read | right at the first pick | on screen, from opening it to the last answer |
| a reading aloud (also the learner's speaking) | its sentences read | read well: 4 of 5 words right | on screen, from opening it to finishing |

The minutes are ScreenClock's (on screen only), rounded up, at least 1. Offline, the report waits in the outbox; a node from before the reading corner (no `GET /readings`) gets it as a `session_end` with the report for the tutor to persist.

### Reading aloud

"🎤 Beri na glas · Read aloud" (`ui/reading/ReadAloudScreen.kt`, the grading in `data/ReadAloud.kt`) reads a reading (its intro, steps and lines, not the ingredients) aloud part by part: whole sentences, up to 24 words a part (the node takes 30 s). The learner taps 🎤, reads the part shown and taps again (or pauses 3 s); the chips on top jump to any part, and a part can be read again (its last take counts). The node's Whisper hears the take twice: with the text as its prompt and without (companion/stt-local/README.md, "Reading aloud", has why and the evidence). Each word of the text then shows how it was read:

- **right** (green): the prompted transcript has it, and the plain one doesn't contradict it. Case, punctuation and č/š/ž don't count; a number read out is the number; words the recognizer ran together or split elsewhere ("jebel" for "je bel", "speto živi" for "spet oživi") are read; so is a one-letter preposition (v, z, s, k) run into its word. A word Whisper was unsure of (under 0.3) is right, marked gold: "Izgovori razločneje · Say these more clearly".
- **misread** (red, "slišano »gorah« · heard »gorah«"): the prompted transcript has another word there; or it has the text's word but the plain one heard it with another ending ("zlatim" read "zlatimi": "končnica · the ending", which the prompt can mend); or Whisper was all but sure it didn't hear it (under 0.05) and the plain transcript doesn't have it either ("ni bilo razločno slišati · not heard clearly").
- **skipped** (struck through): nothing was heard for it.

Under the part: the words right of all and the percentage, the words a minute (the words read over the time from the first word to the last, Whisper's timings), each word read wrong with what was heard, and 🔊 on each sentence with a mistake, in the reader's voice. At the end, the whole reading: the words right, the pace, the sentences read well, and those to hear again. When the node's Whisper can't be reached, the phone's recognizer listens (one pass, no prompt, graded alone; the pace from its speech's start to the result); with neither, the screen says so.

**Forging.** At the smithy, Kovač Tone makes an iron or brass tool (✓ above) better, twice at most: each time it gains half its first strength (a sharp axe forged twice: +30 % 🪵). He needs the smithy standing, to live here, a good from the chest as his pay (the one Jan has most of), and the price: 200🌾 300🪵 200🪨 the first time (from Zaselek), 400🌾 600🪵 400🪨 the second (from Vas).

**Goods.** A villager thanks with a good after their request, one each time. Which one (`Chest.thanksFor`): their `thanks` good, or, when the pack gives them a rotation (`thanks_rotation`: 2 to 4 of their everyday goods, `thanks` first), two requests in three that usual one and the third one of the others (an even turn sped the friendships up enough for the pacing simulation to feel it), picked by the village's seed and the request's id, so the same request always brings the same good (a try that fails and is played again, the result opened again) and the next request maybe another. In a festival's season, from a week before its day to its last grace day (`Catalog.THANKS_SEASON_DAYS`, `Calendar.GRACE_DAYS`), a villager with a seasonal good for it thanks with that one instead (of two seasons at once, the festival nearer its day). A good the chest's catalogue lacks never comes (the app's culture check and the bridge's refuse such a pack); someone without a rotation gives their `thanks` good, someone the pack doesn't know its `thanks_default`. A visitor who does a request in another town gets the thanks its bridge picks the same way (`guests.ts` `thanksFor`, by the giver and the request). The goods and who gives them:

| Good | Value | Given by | Liked by |
|---|---|---|---|
| 🥮 Potica | 20 | Micka | Anton, Ančka, Janez, Zala, Nejc, Tine, newcomers |
| 🍞 Hlebec kruha · A loaf of bread | 15 | France | Anton, Luka, Tone, Marko, newcomers |
| 🍯 Kozarec medu · A jar of honey | 20 | Anton | Micka, Mojca, Vida, Zala, Nejc |
| 🧀 Tolminc (cheese from the Soča valley) | 15 | Luka | France, Marko, Vida |
| 🍾 Steklenica rebule · A bottle of rebula | 25 | Marko | France, Tone, Janez |
| ⚒️ Podkev · A horseshoe | 15 | Tone | Tine |
| 🧵 Vezenina · Embroidery | 25 | Ančka | Micka, Mojca, Vida |
| 🍵 Lipov čaj · Linden tea | 15 | Janez | Micka, Anton, Ančka, Mojca |
| 📓 Zvezek · A notebook | 15 | Mojca | Janez, Zala, Tine |
| 🍲 Lonec jote · A pot of jota | 20 | Vida | France, Luka, Tone, Marko |
| 🍎 Jabolka · Apples | 10 | anyone else | Luka, Nejc, Tine, newcomers |
| 🥚 Jajca · Eggs | 10 | newcomers' gifts | Micka, Vida |
| 🫐 Borovnice · Blueberries | 10 | newcomers' gifts | Zala, Nejc |
| 🧦 Volnene nogavice · Wool socks | 15 | newcomers' gifts | Luka, Ančka, Janez |

Who thanks with what now and then (`thanks_rotation`: the usual one first, two requests in three), and in a festival's season (a week before it to two days after):

| Villager | Usually | Now and then | In season |
|---|---|---|---|
| 👵 Micka | 🥮 potica | 🍞 her bread, 🧦 wool socks she knitted | 🍩 krofi (pust), 🥚 pisanice (Easter), 🍪 medenjaki (St Nicholas), 🍞 poprtnik (Christmas) |
| 🧑‍🌾 France | 🍞 bread | 🥚 eggs, 🍎 apples | 🍇 grapes (the grape harvest) |
| 🐝 Anton | 🍯 honey | 🫐 blueberries | 🍪 medenjaki (St Nicholas) |
| 🐑 Luka | 🧀 Tolminc | 🫐 blueberries | 🍩 krofi (pust) |
| 🍇 Marko | 🍾 rebula | 🍎 apples | 🍇 grapes (the grape harvest), 🍷 young wine (St Martin's) |
| ⚒️ Tone | ⚒️ a horseshoe | 🍎 apples | – |
| 📻 Ančka | 🧵 embroidery | 🥚 eggs, 🍎 apples | 🥚 pisanice (Easter) |
| 👴 Janez | 🍵 linden tea | 🫐 blueberries, 🍎 apples | 🌼 St John's wort (Midsummer night) |
| 👩‍🏫 Mojca | 📓 a notebook | 🍎 apples, 🫐 blueberries | 📕 Prešeren's Poems (Prešeren Day) |
| 🍷 Vida | 🍲 jota | 🍞 bread | 🍷 young wine (St Martin's), 🥂 sparkling wine (New Year) |

Nobody's is a good they like themselves (`CulturePackTest`). The other packs are alike: Nonna Rosa's gubana, wool socks and apples, with carbone dolce for the Befana, crostoli at Carnival, pinza at Easter and panettone at Christmas; Oma Resi's Reindling, socks and apples, with Krapfen at Fasching, a Palmbuschen on Palm Sunday and Kletzenbrot at Christmas; Granny Maggie's sticky toffee pudding, socks and apples, with pancakes on Pancake Day, simnel cake at Easter and Christmas cake. Every villager who makes requests has one; the others' seasons follow what they lead or do (the winemakers' grapes at the harvest, the beekeepers' honey sweets at Christmas or St Nicholas, the innkeepers' treat at New Year: sparkling wine, Kathi's marzipan lucky pig).

- **Give.** A good given to someone who lives here: +5 ♥ one they like, +10 a rare one, +2 anything else (`Catalog.PLAIN_GIFT_POINTS`: warm thanks), once a day each, no wine (`adult`) for the children; they remember it ("potico od tebe"). From the chest (beside who likes it) or the villager's page (what they like, then anything else). Giving is a small scene: Jan says it in their register, and they answer in their words (VILLAGERS.md, "Giving a good").
- **Sell.** The merchant buys goods while he is in the village (his event is open), at their value; the market (Tržnica) from Trg, any day, at 1.5×. The value grows with the age like quest rewards (×1.6 at Zaselek, ×2.2 at Trg), Vida's scales add 25 % (the lookout tower 10 %), and it's paid in the resource the village is shortest of, as much as its store can take. Nobody pays more for a good than they ask for it: a sale brings at most 2× (Vida's scales forged twice would otherwise make 2.6×).
- **Buy.** "🛒 Kupi · Buy" in the chest: goods cost twice their value (growing with the age like the sale price), paid 60 % in 🌾 and 40 % in 🪵, the plentiful resources (potica at Vas: 46 🌾 30 🪵). Who sells, a few of each a day (the day's dice pick what, the same all day):

  | Seller | When | Offers | Of each |
  |---|---|---|---|
  | 🎒 Krošnjar · The pedlar | on his day (the day's surprise) | his rare good and two others | 1 of the rare one, 2 of the others |
  | 🧳 Trgovec · The merchant | while his event is open | three goods | 2 |
  | 🏪 Tržnica · The market | from Trg, while it stands | five goods | 3 |

  It is no endless friendship pump: each villager still takes one good a day. Rare goods come only from the pedlar and the festivals (a festival's own, and a seasonal thank-you around it), never as everyday thanks, from the small gifts or at the market.
- **Feast.** "🎪 Veselica · A village feast" under the linden, from Zaselek, once a week: 300🌾 150🪵 at Zaselek, 450🌾 250🪵 at Vas, 700🌾 350🪵 at Trg, 1000🌾 500🪵 at Mesto (what everyday practice piles up; never the scarce 🪨 and 📜). Morale +15, and everyone who lives here +1 ♥, +1 more for each treat served: up to 2 food goods from the chest.

**Rare goods** (`GoodSpec.rare`): everyone likes them (+10 ♥), but no wine for the children (nor for someone the catalog doesn't know: they may be a child born here). The smith takes a rare good as his pay only when there's nothing else.

| Good | Value | From |
|---|---|---|
| 🥂 Penina · Sparkling wine (not for children) | 30 | Novo leto |
| 📕 Prešernove Poezije · Prešeren's Poems | 30 | Prešernov dan |
| 🍩 Krofi · Doughnuts | 25 | Pust |
| 🥚 Pisanice · Painted Easter eggs | 30 | Velika noč |
| 🌭 Kranjska klobasa · A Carniolan sausage | 25 | Prvi maj |
| 🌼 Šentjanževka · St John's wort | 20 | Kresna noč |
| 🍇 Grozdje · Grapes | 20 | Trgatev |
| 🍷 Mlado vino · Young wine (not for children) | 30 | Martinovo |
| 🍪 Medenjaki · Honey biscuits | 25 | Miklavž |
| 🍞 Poprtnik · Christmas bread | 30 | Božič |
| 🧂 Piranska sol · Salt from Piran | 25 | the pedlar |
| 🫒 Oljčno olje · Olive oil | 30 | the pedlar |
| 🥓 Kraški pršut · Karst ham | 35 | the pedlar |
| ☕ Kava iz Trsta · Coffee from Trieste | 25 | the pedlar |

The feast, forging, the project steps and buying goods are the uses for full stores while an age waits on words (see "Pacing").

## Village projects

"🏗️ Skupni projekti · Village projects" (`Projects.kt`; the frames, with the ages, steps, landmarks and bonuses, in `Catalog.projectFrames`; the words, the leaders and the helpers in the culture pack's `projects.json`; together `Catalog.projects`): community builds, one or more per age from Zaselek, each led by a villager of the cast. The village does one step a day, across all its projects, so there is always a next step to look forward to. They are the big use for full stores in the long ages.

- **The leader must be here.** A project waits for its leader to live in the village or visit today ("Vodja še ni v vasi · The leader isn't in the village"); a village without named residents yet (an older bridge) counts everyone as here.
- **A step** is a short practice with the leader on the stage: 5 exercises of the leader's skill, on the project's topics (cards chosen as for a request), with a request's pass mark (3 of 5). A pass pays the step's price, tells its line in the chronicle ("Luka in fantje so v gozdu izbrali visoko smreko. · Luka and the young men chose a tall spruce in the forest.") and brings the leader +5 ♥. A fail costs nothing, and the step can be tried again the same day.
- **The price** follows the project's age (its store must hold it) and what the step needs: about a day or two of a committed learner's surplus, mostly the plentiful 🌾 and 🪵. A step "with the neighbours" also costs 🤝 (4 at Zaselek, 6, 8, 10 at Mesto), and the last step, the feast, a treat from the chest (the food good Jan has most of, a common one first).

  | Step | Zaselek | Vas | Trg | Mesto |
  |---|---|---|---|---|
  | Work (hands, food for the workers) | 240🌾 100🪵 | 455🌾 190🪵 | 625🌾 260🪵 | 840🌾 350🪵 |
  | Timber | 100🌾 240🪵 | 190🌾 455🪵 | 260🌾 625🪵 | 350🌾 840🪵 |
  | Stone | 100🌾 60🪵 180🪨 | 190🌾 115🪵 340🪨 | 260🌾 155🪵 470🪨 | 350🌾 210🪵 630🪨 |
  | Craft (plans, a craftsman) | 100🌾 80🪵 120📜 | 190🌾 150🪵 230📜 | 260🌾 210🪵 310📜 | 350🌾 280🪵 420📜 |
  | Feast (the last step) | 300🌾 120🪵 60📜 + a treat | 570🌾 230🪵 115📜 + a treat | 780🌾 310🪵 155📜 + a treat | 1050🌾 420🪵 210📜 + a treat |

- **Finished**: its bonus counts for good, like a building's (`Projects.effect`), the chronicle says so ("Mlaj: Mlaj stoji! Vas je bolj vesela. · The maypole stands! The village is merrier."), and every helper who lives here, and the leader once more, +5 ♥ with the memory "naš mlaj · our maypole".

| Age | Project | Landmark | Leads | Steps | Practice | When finished, for good | Helpers | Site |
|---|---|---|---|---|---|---|---|---|
| Zaselek | 🌲 Mlaj · The maypole | `mlaj` | 🐑 Luka | 5 | 🪵 | +3 😊 | Marko, Nejc, Tine | lipa, fire |
| Zaselek | 🌉 Most čez potok · A bridge over the stream | `most` | 🧑‍🌾 France | 6 | 🌾 | +5 % 🪵 | Luka, Tone, Anton | spot:riverbank |
| Zaselek | ⚙️ Mlin na potoku · The mill on the stream | `mlin` | 🧑‍🌾 France | 6 | 🌾 | −10 % 🍲 (the villagers eat less: bread from their own flour) | Tone, Micka, Tine | spot:riverbank |
| Vas | 🎳 Balinišče · A bocce court | `balinisce` | 👴 Janez | 5 | 📜 | +3 😊 | France, Anton, Marko | lipa, fire |
| Vas | 🛐 Kapelica · A wayside chapel | `kapelica` | 📻 Ančka | 6 | 🪵 | +2 😊 | Micka, Tone, Zala | spot:road |
| Vas | 🍇 Terase v vinogradu · Vineyard terraces | `vinograd` | 🍇 Marko | 7 | 📜 | +5 % 📜 | France, Luka, Vida | field, spot:meadow |
| Vas | 🌼 Čebelji travnik · A bee meadow | `cebelji_travnik` | 🐝 Anton | 6 | 🌾 | +5 % 🌾 | Micka, Zala, Nejc | spot:meadow |
| Vas | 🚒 Gasilski dom · A fire station | `gasilski_dom` | 🐑 Luka | 6 | 🪵 | events +6 h | Tone, France, Marko | fire |
| Vas | 🎠 Otroško igrišče · A playground | `igrisce` | 👩‍🏫 Mojca | 5 | 🪨 | requests pay +5 % | Zala, Nejc, Tine | school, lipa |
| Trg | ⛲ Vodnjak na trgu · A fountain on the square | `vodnjak_na_trgu` | ⚒️ Tone | 6 | 🪨 | +3 😊 | France, Marko, Mojca | market, fire |
| Trg | 🪜 Toplar · A double hayrack | `toplar` | 🧑‍🌾 France | 6 | 🌾 | +60 to every store | Luka, Tone, Marko | kozolec, field |
| Trg | ⛰️ Razgledni stolp · A lookout tower | `razgledni_stolp` | 🍷 Vida | 7 | 📜 | trades +10 % | Nejc, Luka, Janez | spot:rocks, spot:highseat |
| Mesto | 🕰️ Mestna ura · The town clock | `mestna_ura` | 👩‍🏫 Mojca | 7 | 🪨 | +5 % of everything | Tone, Janez, Ančka | church, fire |

Each step's task and story line, in Slovene and English, are in the culture pack (`projects.json`); the step lines name only the leader and groups ("fantje", "sosedje", "otroci", "vsa vas"), so they fit whoever lives here.

**For the map.** The state the landmarks are drawn from:

- `GameState.projects: Map<String, Int>`: project id → steps done (missing: 0).
- `Catalog.projects`: each `ProjectSpec` with its `id`, `landmark` (the id to draw), `age`, `site` and `steps`.
- `Projects.landmarks(state)`: one `Landmark(id, project, site, stage, stages)` per project with a step done. Stage 1 … stages − 1: under construction (one stage per step); `stage == stages` (`finished`): the finished landmark. Nothing is drawn before the first step.
- `site`: place keys as scenes use them (a building type in lower case, `fire`, `spot:<id>`); draw at the first that exists (`TownMarkers.projectPlace`). The bridge crosses the stream (`LandmarkLayout`'s `bridge`), the mill stands on the village's bank, its wheel in a race dug off the stream (`bank`).

**In the scenes.** The close-ups show the projects too, step by step, and the buildings only once they stand ([SCENES.md](SCENES.md), "What the village has built"): France's mill and the cart bridge by the stream, the double hayrack in the field, the maypole and the tower clock on the square; the kozolec in the field, the tent at the campfire, the well and the church on the square. France talks of the mill he wants at the stream every morning until it grinds, and mills there once it does. The
finished vineyard terraces are a place of their own: their landmark opens a place card that leads into the vineyard (Na
gričih) as well as to the project. The church leads inside (V cerkvi: Ančka's flowers for Sunday, Tine ringing noon,
Micka's candle) and the beehive to Anton's bee house (Pri čebelnjaku: the painted hive fronts, a swarm on the linden,
Zala's spoon of honey); the well opens the square as the linden does.

**In the app.** A 🏗️ chip in the HUD (finished / open), bubbles at the sites (at most two, the started ones first), rows in the scroll's today list, and the projects sheet: each project's leader, its steps (the story of the done ones, today's with its price, the ones to come), what it brings, and "🔨 Današnji korak · Today's step".

## The calendar

"Koledar · The calendar" (`Calendar.kt`; the festivals, their dates, leaders, words and texts are the culture pack's, `festivals.json`): real Slovene days in Primorska, by the phone's date, from Tabor. Every date is a pure function of the year: Easter by the Gregorian computus, pust 47 days before it, the grape harvest on the last Saturday of September.

- **Before**: from 5 days ahead, a countdown ("Čez 3 dni: Martinovo · St Martin's Day in 3 days", "Čez 2 dneva …", "Jutri: …") in the village's "Jutri" line, the scroll and Home.
- **On the day**: the chronicle tells it in the morning, and its leader (the first of its people who is here; if none is, the whole village) asks Jan to celebrate: a run of 6 of the festival's words (A1–A2), as choices, half from Slovene and half into it, the pack's other words as the wrong options; after each answer, the word and its example sentence. Half right celebrates it.
- **Celebrated**: morale +10, the festival's good for the chest, 20 🌾 and 10 of the scarcest other resource (growing with the age like a request's), the leader +5 ♥ and everyone who lives here +1 ♥, and the chronicle's line ("Mošt je postal vino, na mizi je bila gos z mlinci."). Once a year.
- **Kind to a learner who missed it**: two more days to celebrate ("Martinovo še traja · St Martin's Day isn't over yet"), with morale +5 instead of +10. A try that doesn't pass lifts the spirits (+3) and pays a fifth; it can be tried again.
- **Its words are kept.** Each festival's words are a curated word pack, `companion/packs/praznik-<festival>.json` (`lani.pack/v0` with `"festival": "<id>"`; `praznik-novo-leto` for `novo_leto`; another culture's are in its pack's `packs/`, friuli's `festa-*.json`), 8 words each with gender, plural and an A1–A2 example. The app bundles these files (`app/build.gradle.kts`, `festivalPacks`) and `Calendar.kt` reads each festival's words from its pack (`Festival.words`), so the festival and its pack always agree; the bridge serves the same files as curated packs. Every run of a festival, on its day or a grace day, passed or not, records its answers for the words it asked like a pack session: `POST /packs/<pack>/learn` (through the outbox) → `update-db.py`, one quality per word (right 4, almost 3, wrong 2; a word asked twice keeps its weakest), words Jan already knows skipped by the bridge (also a word they know from another pack: kres, košara). So the words come back in the reviews from the next day, like a pack's words.
- **Learn them early.** The packs screen lists the festival packs in their own section, "🎉 Prazniki · Feasts and holidays", the next festival first, each with a "🎉 praznik · feast" tag and its day ("11. novembra · 11 November", "Čez 3 dni · In 3 days", "Danes · Today"). The suggested pack (Home, the village) is the festival's while it's on or in its countdown; otherwise the festival packs wait there and don't push the other packs aside, even half-learned after a celebration.

| Festival | Day | Leads (the first here) | Words (`praznik-…` pack) | Good |
|---|---|---|---|---|
| 🎆 Novo leto · New Year | 1 January | Vida, Janez, Micka | novo leto, silvestrovo, polnoč, ognjemet, želja, zdravica, nazdraviti, Srečno! | 🥂 penina |
| 📜 Prešernov dan · Prešeren Day | 8 February | Janez, Mojca, Micka | pesnik, pesem, knjiga, brati, kultura, himna, pisatelj, gledališče | 📕 Prešernove Poezije |
| 🎭 Pust · Carnival | Shrove Tuesday (Easter − 47) | Nejc, Tine, Zala, Luka | maska, kurent, krof, zvonec, ples, zima, povorka, našemiti se | 🍩 krofi |
| 🐣 Velika noč · Easter | Easter Sunday | Micka, Ančka | pisanica, jajce, košara, šunka, hren, pomlad, zajček, barvati | 🥚 pisanice |
| 🌿 Prvi maj · May Day | 1 May | Luka, Marko, France | mlaj, kres, godba, delo, praznik, smreka, budnica, izlet | 🌭 kranjska klobasa |
| 🐝 Svetovni dan čebel · World Bee Day | 20 May | Anton, Micka | čebela, med, panj, cvet, čebelar, satje, čebelnjak, pičiti | 🍯 med ×2 |
| 🔥 Kresna noč · Midsummer night | 23 June | Janez, Zala, Luka | kres, noč, kresnica, poletje, zvezda, ogenj, hrib, venček | 🌼 šentjanževka |
| 🍇 Trgatev · The grape harvest | the last Saturday of September | Marko, Vida, France | grozdje, trta, vinograd, škarje, košara, mošt, trgati, zrel | 🍇 grozdje |
| 🍷 Martinovo · St Martin's Day | 11 November | Marko, Vida, Janez | mlado vino, gos, mlinci, sod, klet, Na zdravje!, krst, vinar | 🍷 mlado vino |
| 🎅 Miklavž · St Nicholas | 6 December | Janez, Micka | Miklavž, angel, parkelj, darilo, okno, priden, poreden, šiba | 🍪 medenjaki |
| 🎄 Božič · Christmas | 25 December | Micka, Ančka, Janez | božič, jaslice, sveča, sneg, smrečica, Vesel božič!, poprtnik, okrasek | 🍞 poprtnik |

Each pack's giver is the festival's first leader. State: `GameState.festivals: Map<String, String>` (festival id → the ISO date of the festival day celebrated); the run carries its pack and the word under each exercise (`Challenge.pack`, `packWords`). In the app: a gold "Danes: …" pill under the goal (a tap celebrates), a bubble under the linden (else by the fire), a card at the top of the scroll's today list, a line on Home.

## The day's surprise

"🎁 Dnevno presenečenje · The day's surprise" (`Surprises.kt`; the letters, the ways, the lamb's places, the riddles, the strangers and every line are the culture pack's, `surprises.json`): one every day from Tabor, at the road. It is rolled at the day's first tick from the village's seed and the date (`GameState.surprise`), so it's the same all day; the first comes the day the village reaches Tabor. Short and optional; the people in it live here (or visit today).

| Surprise | Weight | Who | The run | Trains |
|---|---|---|---|---|
| 🎒 Krošnjar · The pedlar | 2 | the pedlar from Ribnica | none: for the day he sells his rare good and two common goods (see "The chest") | – |
| ✉️ Pismo iz Gorice · A letter from Gorizia | 2 | who it's for: Marko, Micka, Tone or Vida (if they live here), or Jan | the letter, read first, then two questions about it with the letter hidden ("Read first, then answer") | 📜 reading |
| 🐑 Izgubljeno jagnje · The lost lamb | 1, while Luka lives here | Luka | three times where Luka hears the lamb ("Jagnje je za kozolcem."), heard, or read without a voice | 🪵 listening |
| 🥾 Romar · A pilgrim | 2 | the pilgrim, on the stage himself | three ways to tell him the way to Sveta Gora (straight on, over the bridge, past the church: "mimo" with the genitive, "dva kilometra": the dual) | 📜 directions |
| ❓ Uganka · A riddle | 2, while a child lives here | a child: Zala, Nejc, Tine, or one born here | two riddles | 🌾 |

**The letter** is read first, on the stage after its intro: "✉️ Preberi pismo · Read the letter", the letter on paper with "🔊 Poslušaj · Listen" for all of it and 🔊 on each sentence, every word to tap for its card, for as long as Jan likes; "▶️ Začni · Start" hides it and its two questions come one at a time (their prompt is the question alone, under the letter's title), "👁 Pokaži besedilo · Show the text" bringing it back over the question (see "Read first, then answer": a look costs a little of what it pays, never the pass). A question about it that goes to the tutor carries the letter (`about_exercise.source.text`).

Half right pays 12 of what the village is shortest of (growing with the age like a request's), 1 🤝, and ♥ with who it was about (+5; a letter's addressee +3). Less pays a third, kindly ("Ha, nisi uganil! Jutri bo nova. · Ha, you didn't guess it! A new one tomorrow."). Either way it's done for the day. In the app: a bubble at the road like a visitor's, with its emoji (the pedlar's opens the chest to buy), a row in the scroll's today list, a line on Home.

**The strangers.** The pedlar and the pilgrim come from the road, so they have sprites of their own (`SceneArt.people`: `pedlar`, `pilgrim`; `Surprises.strangers`), but never live here or become friends:

- 🎒 **Krošnjar** (`pedlar`): the pedlar from Ribnica, in a white shirt with a red neckerchief, a black felt hat and a walking stick, under his krošnja, the wooden frame on his back loaded higher than his hat with suha roba: an upright sieve, wooden spoons, a ladle.
- 🥾 **Romar** (`pilgrim`): a bearded pilgrim on his way to Sveta Gora, in a brown cape, a felt hat with a scallop shell, a tall staff with a gourd, a shoulder bag and dusty boots.

While their surprise is on (the pedlar all his day, the pilgrim until he's been shown the way, `Surprises.stranger`), the stranger stands and walks at the road on the map, day and night, at every detail (the 7 px figure, the 14 px one, the scene sprite), and their bubble follows them; a tap on him does what his bubble does. The pilgrim is on the stage himself for his run (intro, reactions, the finish: "Iščem pot na Sveto Goro. Mi lahko pomagate? · I'm looking for the way to Sveta Gora. Can you help me?"), a stranger who says vi, with his own lines and no friendship to grow. The pedlar's portrait heads his stall in the chest ("Dober dan! Suha roba iz Ribnice!").

## Jutri · Tomorrow

`Tomorrow.teasers` (pure): what's coming, so there's always something to come back for, the most telling first: a festival's countdown (5 days ahead), tomorrow's project step (after today's), the storyteller's story by the fire (tonight's until it's heard, "Nocoj ob ognju: Janez pove zgodbo o Zlatorogu · Tonight by the fire: …", then tomorrow's, "Jutri: Janez pove, kako je Krpan srečal cesarja · Tomorrow: …"; SCENES.md, "Stories"), the pedlar's next day within a week ("Krošnjar pride v soboto · The pedlar comes on Saturday"), the next feast within a week (never a friend's gift: that comes as a surprise, see "The chest"), and, from Tabor, always tomorrow's surprise at the road. The village shows the first under the goal (the festival that is on takes its place), the scroll's today list the first three, and Home's "Danes · Today" the first, under what's on in the village today.

## Culture packs

A culture pack is everything that makes a village a place, in its language: its people, their requests, the feasts,
the surprises at the road, the goods and tools, the projects' words, the event flavour, the names of the newcomers and
of the village's resources, ages and buildings, and how the chronicle says things (plan 2, step 3a). The rules and
the numbers are the game's (`Catalog.kt`): a pack says what there is and how it is said, never what it costs or what
it does. The Primorska village is in `primorska` (Trnovo, Nova Gorica, Brda). The second learner's is in `friuli` (Italian, with Slovene
and English: the Collio near Gorizia, facing Brda; see "The friuli pack" below). A learner of German's is in
`kaernten` (German, with Slovene, English and Italian: the Gailtal near Villach in Carinthia; see "The kaernten pack"),
a learner of English's in `lakeland` (English, with Slovene, Italian and German: a dale in the Lake District; see "The
lakeland pack").

**Where a pack is.** `companion/cultures/<id>/`, one JSON file per part (`lani.culture/v0`):

| File | What |
|---|---|
| `culture.json` | The manifest: `schema`, `id`, `language` (the pack's language: the target of the learners whose town it is), `status` (`complete`, or `stub`: only the manifest, not played), `region` and `name` (texts), `about`, `review` (who wrote the language and who checked it, and which translations a machine added: friuli's is "machine-written, not reviewed by a native speaker", primorska's names its German and Italian), `tutor` (in English, for the bridge: the village's name, the role-play `setting` with `{learner}` (any text of the pack may say the learner so, and agree with them `{m:…|f:…}`: [SCENES.md](SCENES.md#the-learner-in-the-content)), the `style` newcomers are written in, the `voice` a designed voice speaks with; Primorska's are what the bridge always said) |
| `world.json` | What the culture calls each resource (`name`, and `partitive`: after "more", "več lesa · more wood"; `leader`: who goes gathering it with the learner), age, building and event, by the game's ids (`food`, `zaselek`, `kozolec`, `wolves`); the `host`, who welcomes the learner on the training stage before they've met anyone; `places`: the places its texts name, with what they are (for writers and the tutor; `GET /culture` serves them); `backdrop`: what lies at the village's horizon, a place to go (`emoji`, `name`, `where`, `about` for its place card; `sea`: the village's backdrop shows the sea along the horizon), see "Its own places" below; `spots`: its own texts for spots of the landscape, by spot (`kopa`, the charcoal pile in the woods): `name` and `where` (they win over the app's), `about` (the card's line), `story` by level (a few sentences of its history, A1 and A2 at least), the word `pack` of its words (`words`: which, else all), `review`, and a `keeper` who is there now and then (VILLAGERS.md, "At the charcoal pile"), with his meeting (`levels`) and the story he tells across visits (`talks`: a talk a day, each told again on later rounds as its `again`; "His story, visit by visit") |
| `quests.json` | `requests`: the local quest templates (`giver`: the villager's name, `emoji`, `skill`, `title`, `story`, `topics`, and `memory`: how the giver remembers the help, fitting their remember lines; without it the app makes one); `lines`: asking, expiring, thanks and try-again; `tent_move` (optional): the shepherd's request to move the tent to the pond (its `giver`, `emoji`, `skill`, `title`, `story`, `memory`, what he `said`, the run's `listen`, `read` and `heard` ({said}), the `questions` about it, each with its `ask`, `options`, the answer first, and `explain`, and the chronicle's line once it `moved`; see "The tent moves to the pond") |
| `festivals.json` | `festivals`: `id`, `emoji`, `date` (`{"month", "day"}`, `{"easter": days}` or `{"month", "last": weekday}`), `leaders`, `name`, `ask`, `about`, `line`, `good` and `goods`, `words` (the word pack's id); `lines`: the run's prompts |
| `surprises.json` | Each surprise's emoji, title, intro, ask and results, with its content: the pedlar's rare `goods`, the `letters` and their questions, the lamb's `shepherd` and `places`, the pilgrim's `ways`, the `riddles` and the `child` who asks; the `strangers` (the pedlar and the pilgrim, as villagers) |
| `chest.json` | `goods` (`name`, `acc`, `the`, a `price` class, `food`, `rare`, `adult`, `read`), `tools` per villager (`first`, `better`, each with its `read`, an `effect`, `forge`), `thanks` (one good each: an app from before the rotation gives it), `thanks_rotation` (per villager who has a `thanks` good: `goods`, 2 to 4 of the chest's, that one first, none rare; `seasonal`, a festival id of `festivals.json` → the good they thank with in its season, a rare one too; see "Goods"), `likes`, `children`, the `smith`, the `sellers`, and the chest's chronicle `lines` (`gift_memory`, `{item}` the good's `the` form: how a villager remembers a gift, "la gubana che mi hai regalato"; without it, Primorska's "potico od tebe"). `read`: the id of a reading in `readings/` the thing holds (see "The chest", "Readings") |
| `readings/` | What the chest's things hold to read, and the reading corner's others, one file each (`<id>.json`): `id`, `kind` (`recipe`, `proverbs`, `letter`, `page`, `story`, `article`), `level` (A1, A2, B1, B2), `title`, `by` (who reads it aloud, a villager of the cast; a tool's reading: its giver), `intro`; a recipe's `servings`, `ingredients` (`amount` as written, `unit`, `item` as it reads after them, "moke", "di farina", "flour"; a `part` starts a group) and `steps` (imperatives), the others' `lines` (`text`, a proverb's `means`); and 1 to 5 `questions` (`ask`, 3 or 4 `options` in the pack's language, the answer first, `explain`; a `type`: `main_idea`, `detail`, `word` (with its `word` as it stands in the text), `true_false` (`ask` a statement, `answer` true or false, no `options`), `inference`; none: a plain question, as the first readings have them). Optional: up to 12 new `words` it offers for the reviews (`word` as a dictionary has it, its `form` as it stands in the text, `pos`, what it `means` in the title's other languages), its `topics` (English tags, for the tutor), `machine_written` and `review`. Every text is in the pack's language and in every language its title has: a translation of every line. The bridge's validator is `bridge/src/readings.ts`; a bridge from before the question types and words refuses those fields in a file (its check at start only logs it) |
| `projects.json` | Per project of the game (`Catalog.projectFrames`): its `leader` and `helpers`, `name` (`in_text`: inside a sentence), `about`, each step's `task` and `line` (as many as the frame has), `done`, `memory`; `where`: where its landmark is, when a scene opens from it ("v vinogradu · in the vineyard") |
| `events.json` | Each event's `leader` (who stands with the learner on the stage), `title` (the wolves' `title_walled`, at the palisade), `intro`, `won`, `lost` (the merchant's `gift`, the feast's `quiet`), `too_late` |
| `people.json` | The newcomers: `first_names`, `surnames`, `trades` (a trade may `need` a building), the children's `stages`, `newborn`, `story`; their plain `lines` (the villager format's, in the pack's language); the `news` of arrivals, births and departures |
| `chronicle.json` | The engine's lines: founded, built, upgraded, repaired, a new age, gathering, the days away |
| `villagers/` | The cast (`lani.villager/v0`, see [VILLAGERS.md](VILLAGERS.md)): the bridge serves it |
| `packs/` | The pack's own word packs (`lani.pack/v0`): its festivals' words and its places' (`na-gricih`, `il-mare`). The bridge serves them with the packs of the pack's language (`companion/packs/<language>/`; Slovene's are `companion/packs/` itself, Primorska's festival packs among them), and the app bundles them for the calendar |
| `scenes/` | The pack's own places (`lani.scene/v0`, in the pack's language, with its cast): the bridge serves them for a village of the pack after the curated scenes of its language, never for another's ([SCENES.md](SCENES.md), "Scenes of a culture pack") |
| `stories/` | The storyteller's evening stories (`lani.story/v0`, in the pack's language, told by its cast's Storyteller, at A1 and A2 at least, a long legend in chapters): every evening by the fire the next one the learner hasn't heard, at their level ([SCENES.md](SCENES.md), "Stories"); their words in a pack of `packs/` (Primorska's `zgodbe`, Friuli's `storie`, Kärnten's `geschichten`) |
| `voice-cast.json` | The pack's voice cast, when its language isn't Slovene (README, "Voice"): the same archetypes, its own voices |
| `sky.json` | Optional (`lani.sky/v0`): where the village lies under the sky (`where`: `lat`, `lon`), and what its people call what is in it: the moon's `name`, its eight `phases`, `waxing` and `waning`, its `lore` (by level, some by phase), the `reading` its card opens, its `words`; the `things` with a card (the constellations by their IAU abbreviation, `m45` the Pleiades, a named star by its name, the planets, `milky-way`), each with its `name`, `folk` name ("Kosci", "Gostosevci"), a planet's `morning` and `evening` names (Danica, Večernica), `lore` and `words`; the shooting stars (`meteors`: `name`, `wish`, `lore`, `words`, the year's `showers` with their `folk` name and the card's `tonight` line); the word `pack` of the sky's words. See "The night sky" |

**Texts.** Every text is an object of language codes: `{"sl": "Mickina kuhinja", "en": "Micka's kitchen"}`, the
pack's language first, then translations into the base languages it serves. The game shows it "target · base" in the
learner's pair ("Mickina kuhinja · Micka's kitchen"). A language a text doesn't have shows in English, then in the
pack's language. In an English pack English is what is said, so its texts carry every base they serve, and what the
learner has to say or guess in English (the pilgrim's `say`, a riddle's `answer` and `meaning`) has no `"en"` at all:
the fallback would give it away. A text that takes arguments is a message in the string tables' syntax (ICU,
`l10n/Message.kt`):
`{giver} te prosi za pomoč`, `{g, select, f {se je preselila} other {se je preselil}}`, plurals; the comments in
`game/culture/Pack.kt` say what each gets. An argument that is a name of the pack (a building, an age, a good's
`acc` form) reads in each message's language: "Zgrajeno: {building}" and "{building}" give "Zgrajeno: Šotor · Tent".
A form the pack needs in a sentence is its own field (a good's `acc`, "ti podari sekiro · gives you an axe"; the
`the` form, "improved the axe"; a project's `in_text`, "the maypole"). A form a language lacks is the one before it.

**What stays in the app.** The rules and numbers: prices (`Catalog.GOOD_PRICES`), tool effects
(`Catalog.TOOL_EFFECTS`), project frames (ages, step kinds, costs, landmarks and bonuses), the odds of events and
surprises, the rewards. The reasons why something can't be done now ("Manjka · Need", "Potrebuje kovačnico · Needs
the smithy") and the dates and countdowns are the app's too, in the string tables (`chest.*`, `projects.*`,
`calendar.*`). Dates are written by language (`l10n/Dates.kt`): java.time's month names by locale ("11 November",
"11 novembre"), except in Slovene, which takes the month in the genitive ("11. novembra"), so Slovene keeps its own
formatter.

**Checked twice.** The app reads a pack with `game/culture/Cultures.kt`: the shape of each file (a field the format
doesn't have is an error too), the pack's language in every text and in every line people say (the newcomers', the
strangers'), every message's arguments, the goods its lists name, the game's projects and their steps, the dates, and the readings its chest names (each there, of a kind and level the game knows, every line translated). The bridge checks the same with a zod schema
(`bridge/src/cultures.ts`) and, besides, that everyone the pack names (quest givers, leaders, helpers, the smith, the
shepherd, a letter's addressee) is in its cast, and that its own scenes are valid scenes in its language with everyone
in them of its cast (their words in its packs, when the bridge starts). Every problem says the file and the place in it:
`primorska/quests.json requests[0].title: no "sl" text (the pack's language)`. The smoke test validates every pack
in the repository.

**Which pack a village is in.** The learner's profile names it (`culture` in `profiles.json`, `lani-profile add
--culture`; `primorska` by default and for the default profile). `lani-session` passes it to the bridge as
`LANI_CULTURE`. The bridge serves that pack's cast (`GET /villagers`) and word packs (`GET /packs`), speaks of the
place in its role-plays and instructions as the pack's `tutor` says, voices it with the pack's voice cast, and says
which pack it is (`GET /culture`); a stub or an unknown pack isn't played: the bridge plays `primorska` and says why. The app bundles every pack
(`app/build.gradle.kts`, `culturePacks`: the JSON files and the readings, not the cast), asks `GET /culture` when it connects, keeps
the id (`CultureSetting`, like the language pair) and plays it, so the village reads the same offline; a pack the app
can't use leaves `primorska` too.

**Its own places.** A pack's `scenes/` hold the places only its village has, reached from the village itself: its
landscape at the horizon opens from `spot:horizon`. Tapping the hills and mountains behind the village (what the
buildings, people, spots and the forest there don't take) opens a place card named for the pack's `backdrop` with
"Vstopi · Go in" into the scene, the way the fire's card leads into its scene; a village whose culture has nothing there
answers the tap as before, with nothing. Primorska's is V gorah (a mountain pasture above the lake under Triglav, the
little church and its stone bridge on the shore, the shepherd's hut, cows with bells; Janez, Luka and Nejc); Friuli's is Il
mare (the lagoon at Grado with its casoni, the beach and the open sea; Zia Nives, Tommaso and Nonno Bepi), and its
village's backdrop shows the sea along the horizon (`sea`). A place can also open from a village project's landmark once
the project is finished (`project:<id>`): Primorska's vineyard terraces lead into Na gričih (the vineyard's rows running
to a white farmstead, the hills of Brda behind; Marko, Janez and Tine). Someone whose happening is at one of these far
places isn't on the village map meanwhile: a small bubble with their face sits there (SCENES.md, "Bubbles in the
village"). Visits (plan 2) will let a guest walk into the host's places; one dialog in each is written for a guest already.

A pack also tells about the landscape's spots in its own words (`world.json` `spots`): the charcoal pile deep in the woods
below the village is a place to tap, and its card has the culture's name for it, a few sentences of its history at the
learner's level with every word to look up, and its words from a pack of `packs/` to learn; some evenings its charcoal
burner sits by it, a person of the culture who doesn't live in the village, with a short talk, and the next time the
next talk of the story he tells visit by visit (the spot's `keeper`, VILLAGERS.md "At the charcoal pile"). Both validators check them (`Cultures.kt`, `cultures.ts` `spotErrors`: every
language, a story at A1 and A2, the words in their pack, the keeper's talk by an introduction's rules). An older bridge
refuses a `world.json` with `spots` when it starts (its check is strict, and only logs); an older app bundles its own
packs, so it never sees them.

**Parity.** `CultureParityTest` holds what the game said for sl · en before its words moved into the pack (1775
texts: every quest, festival, surprise, good, tool, project line, event text, name and chronicle line, and what the
engine builds from them, in `src/test/resources/culture/parity.json`) and wants the same today; content added since
brings its own probes and changes none (the mill's 19, `project/mlin/…`). `CultureSwitchTest`
plays a tiny made-up Italian pack (test resources, `cultures-test/tinyland`) and checks that everything switches;
`FriuliCultureTest` plays the friuli pack for a learner of Italian from Slovene; `KaerntenCultureTest` the kaernten pack
for a learner of German from Slovene, then from English and from Italian; `LakelandCultureTest` the lakeland pack for a
learner of English from Slovene and from German.

**A new pack**: copy `primorska`'s files and write them anew for the region, in its language with translations for
its learners' bases (for the Italian town: Italian, with Slovene and English), and its own cast in `villagers/`
(lines `{"it", "sl", "en"}`, VILLAGERS.md). Keep the ids the game knows (resources, ages, buildings, events,
projects, the price classes and tool effects), write a request for each villager who gives them, festivals of the
region with a word pack each (the pack's `packs/`, `"festival": "<id>"`), starter word packs of its language
(`companion/packs/<language>/`, if there are none yet), a `voice-cast.json` for its language, the `tutor` texts and a
`review` line, and set `"status": "complete"` when the validators pass. Its landscape at the horizon is a `backdrop` in
`world.json` and a scene in `scenes/` from `spot:horizon` (an art of the landscape: `alps`, `hills`, `sea`), with its words
in a pack of `packs/`.

### The friuli pack

The second learner's village: a paese in the Collio hills near Gorizia, facing Brda across the border. Italian (A1, kind,
for a child), with Slovene (the second learner's base) and English; machine-written, not yet checked by a native speaker (`review`).
Places: Gorizia and Nova Gorica, Cormòns, San Floriano del Collio, the Isonzo, Monte Sabotino, Udine, Trieste,
Cividale, Castelmonte (the pilgrim's sanctuary), Grado, San Daniele, the Carnia (the cramaro, the pedlar, comes from
there). The cast has Primorska's roles (VILLAGERS.md, "The friuli cast"); the ids of resources, buildings and projects
are the game's, named in Italian (the kozolec is "Il fienile", the lipa "Il tiglio", the maypole "L'albero della
cuccagna", the town clock "L'orologio del campanile").

| Festival | Day | Leads (the first here) | Good |
|---|---|---|---|
| 🎆 Capodanno | 1 January | Marisa, Bepi, Rosa | 🥂 spumante (not for children) |
| 🧹 Epifania (the Befana, the pignarûl) | 6 January | Rosa, Giulia, Bepi | 🍬 carbone dolce |
| 🎭 Carnevale | Martedì grasso (Easter − 47) | Tommaso, Lorenzo, Giulia, Davide | 🥨 crostoli |
| 🦅 Festa della Patria del Friuli | 3 April | Bepi, Elena, Rosa | 🦅 the Friulian flag |
| 🐣 Pasqua | Easter Sunday | Rosa, Nives | 🍞 pinza |
| 🧺 Pasquetta | Easter Monday | Davide, Matteo, Tommaso | 🍳 frittata alle erbe |
| 🌷 Festa della Liberazione | 25 April | Bepi, Elena | – |
| 🇮🇹 Festa della Repubblica | 2 June | Elena, Bepi, Marisa | 🇮🇹 a little tricolore |
| ☀️ Ferragosto | 15 August | Marisa, Matteo, Tommaso | 🍉 anguria |
| 🍇 Vendemmia | the last Saturday of September | Matteo, Marisa, Franco | 🍇 uva |
| 🌰 San Martino | 11 November | Matteo, Marisa, Bepi | 🌰 castagne |
| 🎅 San Nicolò | 6 December | Bepi, Rosa | 🍊 mandarini |
| 🎄 Natale | 25 December | Rosa, Nives, Bepi | 🎂 panettone |
| ⭐ Santo Stefano | 26 December | Marisa, Nives, Tommaso | 🍫 torrone |

Each has its word pack in `cultures/friuli/packs/festa-<…>.json` (8 words). Its own place is Il mare (`scenes/il-mare.json`,
in Italian with Slovene and English, machine-written like the rest), its words in `packs/il-mare.json`; its campfire is Al fuoco
(`scenes/al-fuoco.json`, words in `packs/al-fuoco.json`), where Nonno Bepi tells his five legends in the evening (`stories/`:
the agane, the Devil's Bridge of Cividale, the castle hill of Udine, the sbilfs, the Orcolat; words in `packs/storie.json`). The starter packs of Italian are in
`companion/packs/it/`: `saluti`, `famiglia`, `casa-e-cucina`, `cibo`, `numeri-e-ore`, `in-paese` (A1, 21–24 words
each, with articles, plurals, examples in all three languages, and notes in English and Slovene). The thanks goods are
the region's (gubana, cornmeal for polenta, acacia honey, Montasio, ribolla gialla, Gorizia lace, frico …), the
cramaro sells prosciutto di San Daniele, spices, olive oil and coffee from Trieste. Nonna Rosa's recipes are frico with
potatoes (A1) and the gubana of the Natisone valleys (A2), Nonno Bepi's old book a page about the fogolâr (A1) and his
book of proverbs 11 proverbs said in Friuli, each with its meaning (and the Slovene one, where there is one: "Rana ura,
zlata ura"), all in Italian with Slovene and English (`readings/`).

### The kaernten pack

A village for learners of German: in the lower Gailtal below the Dobratsch, near Villach, where Austria, Slovenia and
Italy meet (the Dreiländereck above Arnoldstein), so the three packs make the three-countries corner. Standard Austrian
German with a light Carinthian colour (Grüß Gott, Servus, Jänner, Paradeiser, Pfiat di as a flavour word with its
meaning), A1–A2, kind and clear, with Slovene, English and Italian (the bases a learner of German may have); machine-written,
not yet checked by a native speaker (`review`). Everyone says du to the learner; the learner says Sie to four of them
(Müller Franz, Schmied Toni, Wirtin Kathi, Lehrerin Eva). Carinthian Slovenes live here: bilingual place names in the
texts (Villach/Beljak, the Rosental/Rož, the Faaker See/Baško jezero), some Slovene family names among the newcomers'
(Wutti, Ogris, Sadjak), Imker Stanko from the Rosental, and a Slovene word now and then (Oma Resi counts eggs in
Slovene, Lena learns it at the bilingual school); a light touch, never politics. Places: Villach, Klagenfurt (the
Lindwurm), the Gailtal and the Rosental, the Drau, the Dobratsch, the Faaker See under the Mittagskogel, the Wörthersee,
the Karawanken, the Petzen, the Kanaltal around Tarvis (the Kraxenträger, the pedlar, comes from there), the Luschariberg
(Monte Lussari, Svete Višarje: the pilgrim's goal), the Großglockner. The cast has Primorska's roles (VILLAGERS.md, "The
kaernten cast"); the game's ids are named in German (the kozolec is "die Harpfe", the lipa "die Linde", the maypole
"der Maibaum", the vineyard "der Obstgarten" with old apple and pear trees for the Most, the toplar "die Doppelharpfe",
the bocce court "die Stockschießbahn", the fire station "das Feuerwehrhaus", the town clock "die Kirchturmuhr").

| Festival | Day | Leads (the first here) | Good |
|---|---|---|---|
| 🎆 Neujahr | 1 January | Kathi, Sepp, Resi | 🐷 a marzipan lucky pig |
| ⭐ Heilige Drei Könige (the Sternsinger) | 6 January | Eva, Lena, Maxi | 🍞 Kletzenbrot |
| 🎭 Fasching (the Villacher Fasching) | Faschingsdienstag (Easter − 47) | Maxi, Jakob, Lena, Florian | 🍩 Krapfen |
| 🌿 Palmsonntag (the Palmbuschen) | Easter − 7 | Resi, Jakob, Sepp | 🌿 a Palmbuschen |
| 🐣 Ostern | Easter Sunday | Resi, Mitzi | 🐣 painted eggs |
| 🌳 Maibaumaufstellen | 1 May | Florian, Stefan, Maxi | 🌭 Würstel |
| 🐎 Kufenstechen (and the Lindentanz) | Whit Monday (Easter + 50) | Florian, Toni, Lena | – |
| 🔥 Sonnwendfeuer | 21 June | Sepp, Florian, Stanko | 🌼 elderflower syrup |
| ⛪ Kirchtag | the last Sunday of August | Kathi, Resi, Sepp | 🍲 Kirchtagssuppe |
| 🐄 Almabtrieb | the last Saturday of September | Florian, Franz, Resi | 🧈 Almbutter |
| 🇦🇹 Nationalfeiertag | 26 October | Eva, Sepp, Kathi | 🇦🇹 a little flag |
| 🏮 Martinsfest (lanterns) | 11 November | Stefan, Kathi, Lena | 🌰 Maroni |
| 🎅 Nikolo (the Krampus the evening before) | 6 December | Sepp, Resi, Maxi | 🍊 mandarins |
| 🎄 Weihnachten (Christmas Eve) | 24 December | Resi, Mitzi, Sepp | 🍪 Christmas biscuits |

Each has its word pack in `cultures/kaernten/packs/fest-<…>.json` (8 words). Its own place is Am See (`scenes/am-see.json`,
the `alps` art: the Faaker See under the Mittagskogel, the Alm with its cows, chamois and ibex), its words in
`packs/am-see.json`; its campfire is Am Feuer (`scenes/am-feuer.json`, words in `packs/am-feuer.json`), where Opa Sepp
tells his five legends in the evening (`stories/`: the Lindwurm of Klagenfurt, the Dobratsch in 1348, how the Wörthersee
came to be, King Matthias asleep under the Petzen, the giants of the Karawanken; words in `packs/geschichten.json`). The
starter packs of German are in `companion/packs/de/`: `gruesse`, `familie`, `haus-und-kueche`, `essen`,
`zahlen-und-uhrzeit`, `im-dorf` (A1, 22–24 words each, with articles, plurals, examples in all four languages, notes in
English, Slovene and Italian; the Austrian word first, the German-German one in the note: der Paradeiser, die Tomate).
The thanks goods are the region's (Reindling, Kasnudeln, buckwheat flour for Heidensterz, honey, Gailtaler Almkäse,
Most, a linen cloth from the Gailtal …), the Kraxenträger sells saffron for the Kirchtagssuppe, spices, olive oil and
coffee from Tarvis. Oma Resi's recipes are Kärntner Kasnudeln (A1) and Reindling (A2), Opa Sepp's old book a page about
the Harpfe (A1) and his book of proverbs 11 proverbs, each with its meaning (and the Slovene one, where there is one),
all in German with Slovene, English and Italian (`readings/`). The voice cast (`voice-cast.json`) has the same
archetypes, in German: an Austrian narrator (Fiona) and old man (Helmut), the others voices of the account (README,
"Voice").

### The lakeland pack

The village of a learner of English: a dale among the fells of the Lake District in Cumbria, between Keswick and
Grasmere. British English (A1–A2, simple and kind), with Slovene, Italian and German, the bases a learner of English
may have; machine-written, not yet checked by a native speaker (`review`). Places: Keswick and Derwentwater, Borrowdale
and the Bowder Stone, Grasmere, Langdale, Ullswater and Aira Force, Helvellyn, Scafell Pike, Skiddaw, Kendal (the
packman and the merchant come from there), Penrith, Cockermouth, Hawkshead (Beatrix Potter), Castlerigg, Ashness Bridge,
Honister, Whitehaven (the old port: tea and spices), Carlisle, Cartmel, Bassenthwaite, and St Herbert's Island (the
pilgrim's goal). The cast has Primorska's roles (VILLAGERS.md, "The lakeland cast"). The ids are the game's, named in
English: the kozolec is the "Hay barn", the lipa the village's "Oak tree", the maypole a maypole, the bocce court "A
bowling green", the vineyard terraces "The damson orchard", the fire station "A mountain rescue hut", the lookout tower
"A viewing station" (as the first tourists had them), the double hayrack "A bank barn", the town clock "The church
clock". Cumbrian words come sparingly and explained (fell, beck, tarn, force, "Now then!").

**Politeness.** English has one "you", so the pack's register is in the names: a villager with `register: "ti"` is
called by the first name (Maggie, Jack), one with `"vi"` by title and surname (Mr Tyson, Mr Dixon, Mrs Birkett, Mrs
Hartley: the same four as friuli's Lei). What the learner says to them is translated with the polite form (sl vi, it
Lei, de Sie); what the villagers say to the learner, with the familiar one. The gift scene has no wrong register in
English: its three ways to say it are all right (`GiftTalk.phrases`).

| Festival | Day | Leads (the first here) | Good |
|---|---|---|---|
| 🎆 New Year (first-footing) | 1 January | Mrs Birkett, Wilf, Maggie | 🥂 sparkling wine (not for children) |
| 🥞 Pancake Day | Shrove Tuesday (Easter − 47) | Harry, Alfie, Poppy, Jack | 🥞 pancakes |
| 💐 Mothering Sunday | Easter − 21 | Maggie, Edna, Poppy | 🌼 daffodils |
| 🐉 St George's Day | 23 April | Wilf, Mrs Hartley, Maggie | 🌹 a red rose |
| 🐣 Easter | Easter Sunday | Maggie, Edna | 🍰 simnel cake |
| 🥚 Egg rolling (pace eggs) | Easter Monday | Jack, Sam, Harry | 🥚 pace eggs |
| 🌸 May Day | 1 May | Jack, Poppy, Mr Tyson | 🌸 a May garland |
| 🌾 Rushbearing (Grasmere) | 5 August, St Oswald's Day | Edna, Maggie, Poppy | 🍪 Grasmere gingerbread |
| 🏅 The show (and sports) | the last Monday of August | Jack, Mr Tyson, Mr Dixon | 🌭 Cumberland sausage |
| 🌾 Harvest festival | the last Sunday of September | Mr Tyson, Maggie, Mrs Hartley | 🍞 the harvest loaf |
| 🎇 Bonfire Night | 5 November | Harry, Jack, Alfie | 🍭 treacle toffee |
| 🐑 The shepherds' meet | the last Saturday of November | Jack, Wilf, Mrs Birkett | 🪝 a shepherd's crook |
| 🎄 Christmas | 25 December | Maggie, Edna, Wilf | 🎂 Christmas cake |
| 🎁 Boxing Day | 26 December | Mrs Birkett, Edna, Harry | 🥧 mince pies |

Grasmere's rushbearing is on the Saturday nearest St Oswald's Day; the calendar keeps the saint's day itself. Burns Night
is left out: it is Scottish, not Cumbrian. Each feast has its word pack in `cultures/lakeland/packs/feast-<…>.json` (8
words). Its own place is On the fell (`scenes/on-the-fell.json`, the `alps` art at the horizon: the fell above a lake,
the little chapel and its packhorse bridge, a drystone wall, a force; the chamois and the ibex the painter draws are a
wild goat and a Herdwick ram here), its words in `packs/on-the-fell.json`; its campfire is By the fire
(`scenes/by-the-fire.json`, words in `packs/by-the-fire.json`), where Old Wilf tells his five legends in the evening
(`stories/`: the Borrowdale cuckoo, the Bowder Stone, the Lady of the Lake, the Giant's Grave at Penrith, the hob;
words in `packs/tales.json`). The starter packs of English are in `companion/packs/en/`: `greetings`, `family`,
`home-and-kitchen`, `food`, `numbers-and-time`, `in-the-village` (A1, 21–24 words each, without articles, with their
plurals, examples in all four languages, and notes on false friends and British words). The thanks goods are the
region's (sticky toffee pudding, porridge oats, heather honey, Herdwick wool, Lakeland ale, a knitted tea cosy, Kendal
mint cake, tatie pot …); the packman sells Cumberland ham, Morecambe Bay shrimps, tea and spices from Whitehaven. Granny
Maggie's recipes are tatie pot (A1) and sticky toffee pudding (A2), Old Wilf's old book a page about counting sheep the
old way (yan, tyan, tethera, A1) and his book of sayings (A2), Mrs Hartley's schoolbook a page on the irregular plurals
(A2), all in English with Slovene, Italian and German (`readings/`). The voice cast (`voice-cast.json`) has the same
archetypes, in English: British voices for the narrators and the grandparents, no northern one yet (README, "Voice").

## The night sky

The sky over the village is the real one, over the village's own place (its culture pack's `sky.json`, `where`;
Primorska's Trnovo plateau when a pack says none) at the phone's time, worked out in the app without the network
(`game/sky`): the sun, the moon and its phase (Meeus's low-precision algorithms, the moon's periodic terms complete),
the planets Venus, Mars, Jupiter and Saturn (Standish's Keplerian elements, JPL), and some 760 stars of the Yale Bright
Star Catalogue (magnitude 4.5 and brighter, public domain; `resources/sky/stars.tsv`, made by
`android/tools/sky_catalog.py`) with the stick figures of 20 constellations and the Pleiades (`resources/sky/figures.tsv`):
the Plough and the Little Dipper with the Pole Star, Cassiopeia, Orion, Taurus, Gemini, Leo, Cygnus, Lyra, Aquila,
Scorpius, the Great and the Little Dog, Boötes, Virgo, Auriga, Pegasus, Andromeda, Perseus, Sagittarius. `SkyNow` holds a
minute's sky (made once a minute and shared by every picture); only the twinkle and the shooting stars change per frame.
The tests hold it to published values: the moon's phases of 2026 within two minutes of the U.S. Naval Observatory's,
its rise and set over the village within three (half a minute, measured), stars within 0.05° of the Observatory's
altitudes and azimuths, the moon and Jupiter within a few hundredths of a degree, Saturn within a tenth.

**What is drawn** (`game/render/NightSky.kt`). As the painted sky darkens, the stars come out, the brightest first, in
their colours, the brightest with a little cross of light, twinkling (more near the horizon); the planets shine
steadily, and earlier in the dusk; the Milky Way lies where it is, brightest toward Sagittarius, split by the Great Rift;
the figures' lines are faint dots, brighter for the one whose card is open. The moon's light washes out the faint stars
and the Milky Way; grey clouds, rain, snow and fog (a dialog's sky, `Sky`) hide them, and a storm hides them all; hills,
clouds and buildings stand in front. Each picture sees the sky through a lens that keeps the constellations' shapes
(`SkyView`: stereographic, the horizon straight): the village looks south (the sun rises on its left), the zenith at
the top of a tall sky; an outdoor scene looks its own way (the Alps north-north-west to the Plough and the Pole Star,
the watchtower north-north-east, the vineyard west, the stream east, the pond north-east, the rest south), and while
the moon is up it turns to the moon, so the moon stands in its column over its water, among the stars around it, as
high as it is. The village's moon stands where the real one is. A frame without a `SkyNow` (tests, previews, the
stickers) is drawn exactly as before, with the painted stars.

**Shooting stars** (`game/sky/Meteors.kt`). Now and then one streaks across a clear dark sky, a short trail for half a
second: a few an hour of looking, and many more in the nights of the year's showers (the Quadrantids, the Lyrids, the
Perseids, the Orionids, the Leonids, the Geminids, by the sun's place, the same date every year), those streaking
away from their radiant while it is up. They are the same for everyone at the same place and minute: each ten seconds
of the clock has at most one, seeded by the slot.

**A tap on the sky** (the village and every outdoor scene, where nothing else is: `SkyTargets`) finds the moon, a
planet, a star (its constellation's card, or its own: the Pole Star, Sirius), a constellation's line, the Milky Way, or
a shooting star (generously, and for almost two seconds after it faded), and opens its card (`game/sky/SkyCards.kt`,
`ui/sky/SkySheet.kt`), the thing marked in the sky meanwhile:

- **The moon**: the phase in the village's language and the learner's base ("Prvi krajec · First quarter"), between the
  quarters what it does ("Luna narašča. · The moon is waxing."), how much is lit, its age in days, when it rises and sets
  today at the village (on the phone's clock), the next full and new moon, tonight's meteor shower on its nights, one
  line of lore at the learner's level (of this phase where the pack has one, another each day), the words to tap, and
  the reading it opens (Primorska's old almanac, `stara-pratika`; Friuli's and Kärnten's old book, Lakeland's sayings).
- **A constellation**: its name, the people's name for it (Kosci, Gostosevci, Veliki voz …), where it stands now ("Zdaj
  visoko na jugu · Now high in the south", or below the horizon), its bright stars, lore, words.
- **A planet**: its name, Venus as the morning or the evening star as it is (Danica, Večernica), where it stands, that
  it doesn't twinkle, lore, words. **The Milky Way**, and **a shooting star**: the wish ("Utrinek! Zaželi si nekaj. · A
  shooting star! Make a wish."), its shower's name and folk name (Lovrenčeve solze), lore, words.

**For TalkBack** the sky, drawn in pixels, has two custom actions on the village and on an outdoor scene (once its
picture has the real sky, `ScenePainter.showsSky`): "🌙 Luna · The Moon" opens the moon's card, and "🌌 Nebo nocoj ·
Tonight's sky" a list of what the picture shows now (`SkyTargets.up`: the moon, the planets, the constellations whose
stars show, the named stars, the Milky Way, each once), each row opening its card (`SkyTonightSheet`); by day or under
clouds it says there's nothing to see.

Every line in the village's language can be tapped word by word for the word's card (as in the dialogs), the card's
words are chips to tap, and "📚 Learn the sky's words" learns the pack's sky word pack (`nebo`, `il-cielo`,
`der-himmel`, `the-night-sky`). Without a `sky.json` the cards use the app's names (the string tables, `sky.*`; the
constellations' Latin ones). The app checks `sky.json` when it reads it (`Cultures.sky`: its language in every text, the
phases, the things the app draws, the showers, the levels, the reading and the word pack it names; a file with problems
is left out and the problems are logged); the bridge checks it with the pack (`bridge/src/sky.ts`, `validateCulture`),
and an older bridge ignores the file.

## Background sounds

Quiet sounds of the place play under the village and its scenes: birds by day, crickets and the owl at night, the rain
when it rains, the stream at the water, the fire at the campfire. They follow what is on screen, the hour, the month and
the weather. When one of these changes, the sounds fade in and out over a second or two. A visited town sounds the same
way. Where the picture shows a sound, a short one plays once: thunder after each flash of lightning, a bell as it swings,
the anvil, the cow's bell, the owl, the marmot, the pot's lid, the cat and a fire catching.

**What plays where** (`game/ambient/Soundscape.kt`: the place, the hour, the month and the weather give a level for each
loop; pure, unit-tested in `SoundscapeTest`):

| Place | By day | At night | Day and night |
|---|---|---|---|
| The village | birds | crickets, the owl | the sea, faint, by the coast (friuli) |
| `forest` | birds, the most | the owl, a few crickets | the brook, faint; a breath of wind |
| `field`, `hills`, `apiary` | birds; grasshoppers on summer days | crickets, the owl | a breeze on the field and the hills |
| `campfire` | birds | crickets, the owl | the fire, higher when it flares |
| `tent`, `square`, `market` | birds | crickets, the owl (not at the market) | |
| `stream` | birds | crickets, frogs, the owl | the stream |
| `pond` | birds, frogs (spring and summer) | frogs, crickets, the owl | the brook that feeds it, faint |
| `alps` | a few birds (the choughs) | | the pasture's brook, the wind |
| `sea` | | a few crickets | the waves, a breeze |
| `watchtower` | birds, far off | crickets, the owl | the wind |
| the rooms: `kitchen`, `livingroom`, `workshop`, `school`, `smithy`, `attic` | birds, faint through the window | crickets and the owl, faint | the stove in the kitchen, the forge in the smithy |
| `cellar`, `church` | | | only the weather, faint |

- **The hour**: day and night follow the scenes' sun (`Env`: the month's sunrise and sunset). The birds begin 45 minutes
  before sunrise, a quarter louder in the dawn chorus (to an hour and a half after sunrise), and stop 45 minutes after
  sunset. The crickets, the frogs and the owl come in as the birds go.
- **The month**: fewer birds in winter (a third of May's); crickets from May to October, most in July and August; frogs
  from March to September; the owl all year, most in autumn and winter.
- **The weather**: a scene's sky (its dialog's cues, [SCENES.md](SCENES.md#sky-cues-the-weather-in-a-dialog)); over the
  village, the storm event or the sky a dialog left today (`Soundscape.villageSky`). The rain is heard at the square root of
  its level: fully in the open and under the tent's canvas, 0.7 in the attic under the roof, 0.4 in a room, a little in
  the cellar. A light shower (0.2) halves the birds; from 0.4 the birds, the crickets and the owl are quiet, and the frogs
  only halve. The sky's wind is heard as wind. Fog and snow muffle the birds; snow quiets the crickets and the frogs. When
  the sky has lightning, thunder follows each flash (below).
- **A dialog's effects** join in: the campfire's `flare`, the kitchen's `oven`, the living room's `stove` and the smithy's
  `forge` (the fire), the forest's `hoot` (the owl, by day too), the sea's `waves`.

**Thunder and short sounds** (`game/ambient/Thunder.kt`, `game/ambient/Sfx.kt`: pure, unit-tested in `ThunderTest` and
`SfxTest`). A short sound plays once where the picture shows one. It is timed on the picture's own clock: the screen gives
its clock to `AmbientSound` (`TownClock.origin`, the clock's second 0 in `System.nanoTime`), so the sound and the picture
agree.

- **Thunder.** The flashes come from one schedule, `game/scene/Lightning.kt`, which the painters draw from too. A scene
  flashes every 7.3 s, up to 2.5 s late: a flicker of 0.08 s and an echo (`Weather.flash`). The village flashes every
  5.3 s in the storm event and every 8.1 s under a dialog's sky kept for the day (`VillageRenderer`). As it plays, the
  player looks at the picture's clock and takes the flashes since it last looked. Each flash's thunder comes 2.5 to 6 s
  after it (the storm is 1 to 2 km off): the farther, the later, the softer (down to 0.55) and the deeper (played at 0.9
  instead of 1.06). Three rumbles take turns; each rises softly over a quarter second or more. All of it is heard in the
  open and under the tent's canvas, about half in a room, a fifth in the cellar (the place's share of the rain to the
  power 0.75: a low rumble comes through walls better than rain). A flash while the microphone records or the app is away
  brings no thunder later. Leaving the place drops thunder still to come. A newer art's placeholder picture draws no
  weather, so it has no thunder.
- **What a tap sets off** ([SCENES.md](SCENES.md#pokes-small-gotchas)), timed as the painter draws it:

  | Art | Tapped | Sound |
  |---|---|---|
  | `square` | `tower` | the bell, a strike at each end of its swing (the painter's sin(5.5 t): every 0.57 s) while it swings, dying down over 3 s |
  | `church` | `rope` | the bell in the gable, the same way (sin(4.2 t): every 0.75 s), longer the second time |
  | `smithy` | `anvil` | a ring; the second time two, 0.65 s apart |
  | `alps` | `cow` | the second tap: her bell clanks at each shake of her head (every 0.22 s), dying down; her lowing has no sound |
  | `alps` | the marmot | its whistle at 0.3 s, and its echo off the slopes |
  | `forest` | `owl` | the third tap: "hu … hu-hu-hooo" as its calls go out (at 0.6 s) |
  | `kitchen` | `pot` | the lid rattles on its rim (a clink every 0.125 s) with a puff of steam; boiling over, after its hop |
  | `kitchen` | the cat | the second tap: it purrs |
  | `campfire` | `fire` | a whoosh as it leaps up, louder the second time |

- **A dialog's effect coming on** ([SCENES.md](SCENES.md#effects-the-place-joins-in)): a rise of 0.25 or more in the same
  scene. A `bell` rings a peal of 5 s, its last half fading. Its strikes fall at the ends of the painter's swing (the
  square, the church; the market's small hand bell higher and quicker), or, for a bell far off, as its rings go out (the
  hills, the lake in the mountains, the field, the stream, beyond the woods at the campfire). The picture may go on
  swinging; the sound stops. The mountains' `cowbells`: the herd's bells clank for 2.5 s. The kitchen's `oven`, the
  living room's `stove` and the smithy's `forge` catch with a whoosh, and so does the campfire when it `flare`s up by
  0.3 or more. The smithy's `sparks`: the smith's blows on the anvil for 3 s (every 0.62 s, as the sparks fly).
- **Left out**: the cow's lowing, the dog's bark and the other animals' calls (the hen, the crow, the sparrow), the
  watchtower's horn and the church's organ. Made from simple tones, they would not sound right. The evening bell at
  sunset by itself (the ave over the field and the stream, with no cue) has no sound yet.

**Quiet, in the background** (`game/ambient/AmbientBus.kt`). The loops are made at −26 LUFS, and the voices are at −18
LUFS (companion/README.md, "Voice"). The volume setting (0 to 1, 0.375 by default) is squared, so the default plays the
whole at 0.14 (−17 dB; the middle of the slider is a quarter, −12 dB): a loop at its full level is at about −43 LUFS, 25 dB
under the voices, and at the top of the slider still 8 dB under them. The phone keeps only a level the learner has set, so
someone who moved the slider keeps their level when the default changes. Each loop has a trim besides (`Layer.trim`): the crickets' high chirps sound louder than the
rain at the same measure, and a phone's speaker loses most of the fire's roar and the wind (their energy is low). A short
sound is made with its loudest moment (EBU R128 momentary loudness, 400 ms) at −28 LUFS, 2 dB under a loop at its full
level, and a thunder at −26 LUFS, as loud as one. Each plays at a gain of 1 or less, so none is louder than the loudest
loop. A far bell's peal and the cow's shaking bell stay under 0.9 (their strikes overlap). Short sounds play under the
level of the whole like the loops. Then:

- **Under a voice** (a clip, a family recording, the phone's TTS: `Speaker.talking`) they duck to 0.3 (−10 dB) in 0.3 s,
  and come back in 1.2 s.
- **While the microphone records** they stop at once, and a short sound still to come is dropped. Every recorder holds
  `MicOpen` while it records (`SpeechRecorder` for the node's Whisper, the family recorder), and so does the phone's
  recognizer while it listens (`MicHold`: the speaking exercise, the dialog's 🎤, reading aloud, a talk). The phone's voice
  dialog comes over the app, and the app pauses: that stops them too.
- **When the app pauses** (the background, or another app's dialog over it) they stop at once, and come back with a 2 s
  fade.
- They play as a game's sound (`USAGE_GAME`), so the phone's media volume sets how loud they are.

**The setting**: Home's status dot, "🔗 Povezava · Connection", then "🐦 Zvoki v ozadju · Background sounds": a switch (on
by default) and the volume. Moving the slider plays the stream for three seconds, so the level can be heard. The phone
keeps both (`ambience` preferences).

**The sounds** (`android/app/src/main/assets/ambient/`; the sources and licence of each in `CREDITS.md` there): nine loops,
all synthesized by `android/tools/ambient_sounds.py` from filtered noise and simple tones, no recordings, CC0: `birds`
(36 s), `crickets` (20 s), `owl` (40 s), `frogs` (24 s), `rain` (16 s), `stream` (18 s), `fire` (20 s), `wind` (30 s),
`sea` (32 s). Ogg Vorbis (quality 1), mono, 24 kHz, about 700 KB together. Each file holds its loop with a quarter second of the loop's end
before it and of its start after it, so the codec's edges never fall on the loop point. The player (`data/AmbientPlayer.kt`)
decodes a loop once, the first time it is wanted, and cuts one period out of the middle (`LoopCut`). It mixes the loops in
one `AudioTrack` on a thread of its own (`AmbientMixer`, pure). With nothing to play, the track pauses and the thread
sleeps.

Twelve short sounds, made the same way: `thunder-1`, `thunder-2`, `thunder-3` (4 to 6.5 s), `bell`, `bell-far`, `anvil`,
`cowbell`, `hoot`, `whistle`, `lid`, `purr` and `whoosh` (0.6 to 3 s), 122 KB together, no pads (each starts after 8 ms
of silence). The player decodes a short sound once, when a place that can play it is shown, so a tap is heard at once
(`Sfx.sounds`). It mixes each into the same track from the sample where its time falls, sped up or slowed down for its
rate (`AmbientMixer.play`; at most 16 at a time).

To make them again: `uv run companion/android/tools/ambient_sounds.py` (`--shots` for the short sounds only;
`--preview-shots DIR` for 60-second MP3s of the thunder in a storm and of the short sounds in their scenes). A new loop:
add it to the script and to `Layer` (its file, its period in seconds, its trim), and give it its places in
`Soundscape.mix`. A new short sound: add it to the script and to `Sound`, and give it its cue in `Sfx` (a poke, an
effect) or in `Thunder`.

## Villagers

The cast of "Moja vas" lives in its culture pack, `companion/cultures/primorska/villagers/*.json` (`lani.villager/v0`; the format, the friendship and the talks are described in [VILLAGERS.md](VILLAGERS.md)): each villager's personality, story, likes, register, home, skill and lines, in one place for the quests, the scenes, the training stage and the tutor who plays them. Every villager has one voice and one skill; keep them consistent so the learner gets to know them. Names take the usual Slovene possessives in titles: Mickina, Francetov, Antonov, Lukovi, Ančkin, Tonetova, Mojčina, Janezove, Markovo, Vidina.

| Villager | Emoji | Skill | In a sentence | Typical quest topics |
|---|---|---|---|---|
| Babica Micka | 👵 | 🌾 vocabulary | Warm grandmother, always cooking, forgets words, never people. Zala's grandmother. | Kitchen and food words, potica, Sunday lunch, greeting guests, family members, numbers at the table |
| Mlinar France | 🧑‍🌾 | 🌾 vocabulary | Practical miller and farmer, counts everything. Tine's grandfather. | Farm and tool words, the harvest (žetev), the hayrack (kozolec), weather, seasons, shopping lists |
| Čebelar Anton | 🐝 | 🌾 vocabulary | Quiet beekeeper, proud of the Carniolan bee (kranjska sivka) and World Bee Day. | Nature and colour words, honey and sweets, listening to short quiet phrases |
| Pastir Luka | 🐑 | 🪵 listening | Young shepherd on the Trnovo plateau, shouts across valleys, says ti at once. | Animal words, calls and commands, directions, fog and bells, weather in the mountains |
| Teta Ančka | 📻 | 🪵 listening | Village gossip with a crackling radio (Radio Koper), hard of hearing. Micka's sister. | News, times and dates, numbers, names of places, dictation |
| Kovač Tone | ⚒️ | 🪨 grammar | Gruff and kind smith, few words, no tolerance for a loose ending. Takes orders from Gorizia. | Word order, cases after prepositions, exact sentences, tools and materials |
| Učiteljica Mojca | 👩‍🏫 | 🪨 grammar | Kind but precise teacher, loves the dual and explains why a rule exists. Comes with the school. | Endings and agreement, the dual (dvojina), verb forms, fixing sentences with mistakes |
| Stari Janez | 👴 | 📜 conversation, writing | Old storyteller under the linden, slow and proverbial; five times up Triglav. Every evening a story by the fire (SCENES.md, "Stories"). | Finishing stories, talking about the past, Triglav and the mountains, the linden as the village meeting place |
| Vinar Marko | 🍇 | 📜 conversation, writing | Easygoing winemaker from Brda, sells rebula across the border; Gorizia and Nova Gorica are one town to him. | Small talk over a glass of wine, the grape harvest (trgatev), short letters and messages, Primorska places |
| Gostilničarka Vida | 🍷 | 📜 conversation, writing | Innkeeper in Trnovo, busy and warm, German guests every summer. Nejc's mother. | Ordering food and drink, translating for guests, polite phrases, prices |
| Nejc, Zala, Tine | 🧒 👧 👦 | — | The village children: the ringleader, the one who asks why, the smallest. Quick, cheeky, ti. | No quests; they appear in scenes and the talks |

Flavour notes for stories and events:

- Places: Trnovo pri Gorici and the Trnovo plateau (Trnovski gozd), Nova Gorica and Gorizia (Gorica), the Vipava valley, Brda, the Soča, Triglav.
- Things: potica, kozolec, čebelnjak and the kranjska sivka, the village linden (lipa), rebula, the village feast (praznik, veselica).
- Events: wolves at the palisade, a brown bear in the orchard (Slovenia has around a thousand), a storm from the Vipava valley, a merchant from Gorizia, a feast day.
- The chronicle (Kronika) reads like a village diary: short past-tense lines, one thing per line.

**Someone from a friend's town** (companion/README.md, "Friendship between towns"): at a close friendship between two
towns, and when the learner reaches A2 in the friend's town's language, someone of the friend's culture may move in,
once both learners said yes. They are a resident like a newcomer (one villager more), with their culture, language and
town (`Resident.culture`, `language`, `from`), and they leave last when the village shrinks. Their lines are the
village's plain lines and their culture's, in their language with the learner's base translation, so they speak their
language now and then. **A shared feast**: when the village and a friend's town (level 2 or more) held feasts three days
apart or less, morale +10 and +2 🤝, once.

## Writing tutor quests

Add a top-level `quest` block to a module (lani.module/v0):

```json
"quest": {
  "giver": "Gostilničarka Vida",
  "emoji": "🍷",
  "story": "V gostilni v Trnovem čakajo gostje iz Nemčije. Pomagaj Vidi sprejeti naročila. · Guests from Germany are waiting at the inn in Trnovo. Help Vida take their orders.",
  "skill": "wisdom",
  "reward": { "wisdom": 60, "food": 20 }
}
```

- `giver`: a villager with a Slovene name. Reuse the cast above (keep their voice and skill) or add new people from Primorska.
- `emoji`: the villager's emoji. The default is 🧑.
- `story`: one or two short sentences, in the form "Slovene · English". The Slovene half must be correct standard Slovene at A1–A2 level (the learner reads and learns from it), and the English half must say the same thing. Tie the story to what the module practises and, where it fits, to a small cultural fact.
- `skill`: one of `food` (vocabulary), `wood` (listening), `stone` (grammar) or `wisdom` (conversation or writing). If you leave it out, the largest reward sets the skill.
- `reward`: resource name → amount. Unknown names are ignored. Each amount is capped at 300. If the reward is empty, the quest pays 40 × (1 + 0.3 × age index) of its skill.
- Reward ranges: about 50–80 in total for a short drill (6–8 exercises), 80–150 for a long or hard module, and up to 200 for a milestone module. Pay mostly in the quest skill: 🪨 for grammar, 📜 for conversation and writing. Add 10–20 of a second resource at most. For scale: the module's own answers pay about 55, one gathering run about 35, and an upgrade at Zaselek costs 100–400. A tutor quest should be worth one or two gathering runs. The reward is not halved after the day's first 40 answers, so keep it in these ranges.
- The quest id is `tutor-<moduleId>-v<version>`. Publish a new version to offer the quest again. When a module is removed, its quest is removed too. A done tutor quest stays done.
- A passed tutor quest helps like a local one: 3 🤝, +10 ♥ and the giver's thank-you good (a giver the catalog doesn't know gives apples). It waits for its giver to live in the village (or visit): pick a giver who is there, or who comes soon.
- A villager shows at most two open requests at once, their own first, then your newest (see "At most two at once"): the rest wait in the Scroll's "Later". Spread your quests over the cast; don't pile modules on one villager.
- `helps` (optional): for a task Jan keeps failing (`get_village`: its `tries`, `best` and `missed`), write a short drill (tagged `short`) at the same giver whose quest block names that task's module: `"helps": "predlogi-kraja"`. The task waits behind the drill ("⏸️ Najprej · First: …") and comes back once the drill is passed. The bridge refuses a module that names itself and says when the task isn't published or is another villager's.
