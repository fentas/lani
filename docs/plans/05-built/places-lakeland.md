# Built: the Lake District village gets its places (places-lakeland, wave 2)

Jan, 2026-10-31: "ok do all" (the third batch of follow-ups, 7–8: "the Carinthian and Lake District villages get their
places"). The Lake District village (culture `lakeland`: English, for an adult learning English from Slovene, German or
Italian) had two scenes, the fire and the fell at the horizon, against Friuli's 33. Since map-culture its map had its own
look (stone cottages under slate, the battlemented tower, the field barn, the pump, the oak, WBC hives), but none of its
buildings had a scene, so a building's card had no way in, and the arts its buildings would open drew Slovenia.

This branch gives every building the village can build a scene of its own, in English with Slovene, Italian and German,
and draws every art those buildings open in the Lake District's look. The projects are the next wave (below). Two helpers
wrote half the scenes each, on branches merged into this one (`places-lakeland-homes`: the house's rooms, the tent, the
smithy and the church; `places-lakeland-village`: the field, the green, the bee garden, the school, the market, the
watchtower and the gate); the pictures, the integration and this note are the lead's. Everything is machine-written and
waits for a native check.

## What

### The way in, before and after

| A learner of the Lake District taps … | Before | Now |
|---|---|---|
| the tent | its card, no way in | its card's "🚪 Go in: In the tent" (2 taps) |
| the hut | its card, no way in | "🚪 Go in: In the kitchen" (2 taps) |
| a house | its card, no way in | the room of whoever lives there: In the living room (Old Wilf), In the cellar (Brewer Sam, Arthur lodging), In the attic (Mrs Birkett and Harry), In the workshop (Mr Tyson and Alfie), else the kitchen (2 taps) |
| the field, the hay barn | its card, no way in | "In the field" (2 taps) |
| the oak, the well (the pump) | its card, no way in | "On the village green" (2 taps) |
| the beehives, the palisade, the watchtower, the smithy | their cards, no way in | "At the bees", "At the gate", "On the watchtower", "In the smithy" (2 taps each) |
| the church, the school, the market | their cards, no way in | "In the church", "At school", "At the market" (2 taps each) |

The cards read the culture's scenes (`from`), so the doors are content: a Lakeland village's bridge serves them as soon as
its checkout has them. The looks and the I spy clues come with the next app build.

### The scenes

Fifteen scenes, one for each building the village can build, in `companion/cultures/lakeland/scenes/`, each with its
word pack of the same id in `packs/` and its I spy clues in `companion/ispy/scenes/`. They follow Friuli's
(places-it-buildings): every slot the content names is a thing of the scene's pack or of the culture's starter packs
(`home-and-kitchen`, `food`), the people are the culture's cast (`villager`, voiced by its voice-cast), the happenings
spread over the day and the year (the months where it matters: the fire lit October to April, the hay June to September,
the show in summer, the shepherds' meet in autumn, the carols in December), and every dialog has variants, faces, its
learner's turns asked by the rules the learner has (adaptive turns naming the English book's pages) and a why for every
wrong choice in Slovene, Italian, German and English. Two are A2 (the cellar and the watchtower), the rest A1.

| Scene (art) | From | People | Happenings | Dialogs | Turns | Wrong | Two right | Tap | Counted | Pack words | I spy things, clues |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `in-the-kitchen`, In the kitchen (`kitchen`, A1) | the hut, a house | Granny Maggie, Auntie Edna | Granny Maggie makes breakfast (morning); Granny Maggie is baking (morning/afternoon); Auntie Edna is listening to the radio (afternoon); Granny Maggie is making tatie pot (evening) | 4 + 4 | 19 | 21 | 6 | 0 | 1 | 14 | 27, 118 |
| `in-the-living-room`, In the living room (`livingroom`, A1) | a house (Old Wilf) | Old Wilf, Poppy | Old Wilf is lighting the fire (dawn/morning, Oct–Apr); Poppy is playing with Bess (afternoon); Poppy wants to play hide-and-seek (morning/evening); Old Wilf is looking at an old photo (evening); Old Wilf's cup of tea (afternoon) | 5 + 6 | 25 | 29 | 2 | 4 | 1 | 14 | 16, 70 |
| `in-the-cellar`, In the cellar (`cellar`, A2) | a house (Brewer Sam) | Brewer Sam, Mrs Birkett, Beekeeper Arthur | Mrs Birkett fetches ale for the inn (morning/afternoon); Brewer Sam lights the candle (evening); Brewer Sam's damsons (afternoon, Sep); Brewer Sam gets ready for the show (morning, Jul–Aug); Brewer Sam shows the ham (afternoon); Beekeeper Arthur brings his honey (afternoon) | 6 + 6 | 25 | 29 | 3 | 1 | 2 | 14 | 15, 64 |
| `in-the-attic`, In the attic (`attic`, A1) | a house (Mrs Birkett, Harry) | Harry, Mrs Birkett | Harry won't get up (dawn/morning); Mrs Birkett and the old cradle (afternoon); A storm at night (night, Oct–Mar); Harry wants to play ball (afternoon/evening); Mrs Birkett opens the old chest (morning/afternoon) | 5 + 5 | 21 | 22 | 3 | 1 | 0 | 12 | 14, 59 |
| `in-the-workshop`, In the workshop (`workshop`, A1) | a house (Mr Tyson, Alfie) | Mr Tyson, Alfie | Mr Tyson is hammering nails (morning); Mr Tyson is sawing boards (afternoon); Alfie's wooden horse (evening); Mr Tyson carves a crook (afternoon, Sep–Nov); Alfie sweeps the workshop (morning) | 5 + 5 | 20 | 23 | 2 | 1 | 1 | 14 | 15, 62 |
| `in-the-tent`, In the tent (`tent`, A1) | the tent | Shepherd Jack, Alfie, Poppy | Shepherd Jack shelters from the rain (morning/afternoon); Alfie wants a bedtime story (evening/night); Poppy is looking for her treasure map (afternoon); A summer evening at the tent (evening, May–Sep) | 4 + 4 | 18 | 19 | 3 | 1 | 0 | 12 | 12, 48 |
| `in-the-smithy`, In the smithy (`smithy`, A1) | the smithy | Mr Dixon, Harry | Mr Dixon is making a horseshoe (morning); Harry is working the bellows (afternoon); Brewer Sam's cart has a broken wheel (afternoon/evening); Mr Dixon sharpens the shears (morning/afternoon, Jun–Jul) | 4 + 4 | 18 | 19 | 2 | 0 | 1 | 12 | 14, 58 |
| `in-church`, In the church (`church`, A1) | the church | Auntie Edna, Granny Maggie, Alfie | Auntie Edna brings flowers for Sunday (morning); Alfie rings the noon bell (afternoon); Auntie Edna and the organ (afternoon); Granny Maggie lights a candle (evening); Alfie and the painting (morning) | 5 + 7 | 24 | 26 | 4 | 1 | 1 | 11 | 12, 50 |
| `in-the-field`, In the field (`field`, A1) | the field, the hay barn | Mr Tyson, Alfie | Mr Tyson makes hay (afternoon, Jun–Sep); Alfie and the scarecrow (morning); Where's Bonny? (afternoon); Evening in the field (evening) | 4 + 5 | 20 | 26 | 5 | 1 | 0 | 16 | 21, 88 |
| `on-the-green`, On the village green (`square`, A1) | the oak, the pump | Old Wilf, Harry, Mrs Hartley | Old Wilf on his bench (morning/afternoon); Where's Bess? (evening); Harry's football (afternoon); The cat up the oak (morning/evening); Mrs Hartley at the pump (morning, Sep–Jun); The summer fête (evening, Jun–Aug) | 6 + 7 | 30 | 33 | 10 | 1 | 1 | 12 | 15, 65 |
| `at-the-bees`, At the bees (`apiary`, A1) | the beehives | Beekeeper Arthur, Poppy, Old Wilf | Arthur opens the hives (morning, Mar–Oct); A swarm! (afternoon, May–Jun); Poppy's bread and honey (afternoon); Arthur listens to his bees (evening); The lime tree in flower (morning, Jun–Jul) | 5 + 7 | 28 | 30 | 7 | 1 | 1 | 14 | 13, 57 |
| `at-school`, At school (`school`, A1) | the school | Mrs Hartley, Poppy, Alfie, Harry | The morning register (morning, Sep–Jun); The mistakes on the board (afternoon, Sep–Jun); The map of the Lakes (morning, Sep–Jun); Poppy at the globe (afternoon); Harry's homework (afternoon, Sep–Jun); Harry at the door (morning/afternoon) | 6 + 8 | 29 | 44 | 10 | 0 | 1 | 15 | 14, 59 |
| `at-the-market`, At the market (`market`, A1) | the market | Granny Maggie, Mrs Birkett, Brewer Sam | Granny Maggie's stall (morning); The hen runs off! (morning/afternoon); Mrs Birkett shops for the inn (morning); Brewer Sam's cart (afternoon/evening); Show day (afternoon, Aug–Nov) | 5 + 9 | 32 | 47 | 12 | 2 | 2 | 14 | 16, 70 |
| `on-the-watchtower`, On the watchtower (`watchtower`, A2) | the watchtower | Shepherd Jack, Harry, Mr Tyson | Jack on watch (evening/night); The packman is coming (morning/afternoon); A storm over the fells (afternoon, Apr–Oct); The village from up high (morning); Harry and the crow (evening) | 5 + 7 | 30 | 36 | 4 | 2 | 2 | 16 | 16, 67 |
| `at-the-gate`, At the gate (`palisade`, A1) | the palisade | Shepherd Jack, Mr Dixon, Harry, Poppy | The sheep go out (dawn, Mar–Nov); The sheep come home (evening, Mar–Nov); Mr Dixon shuts the gate (evening); The torches at the gate (evening); The packman comes up the road (morning/afternoon); Poppy and the bell (afternoon) | 6 + 6 | 27 | 34 | 7 | 2 | 1 | 13 | 15, 59 |
| **15 scenes** | | | 75 | 165 | 366 | 438 | 80 | 18 | 15 | 203 | 235, 994 |

In all: 75 happenings, 165 dialogs (75 the happenings' first, 90 variants), 366 turns, 438 wrong
choices, 306 of them a rule of the English book (270 at A1, 36 at A2: the past simple, the future, the present
perfect, some and any) and 132 a word's meaning or a greeting's hour; 80 turns with two right answers, 18 tap
turns, 15 dialogs counting something of the village, 203 pack words, 235 things to spy with 994 clues.
The English book has no B1 page yet, so the few B1 sentences are lines to read, never a rule asked.

### The Lake District's look

Each art's table is in `game/scene/LakelandLooks.kt`, added to the culture's `CultureLooks` entry beside its fells, its
campfire and its map (a file of its own, so the Carinthian round's tables sit beside it without a conflict). The shared
materials are in `game/render/scene/LakelandArt.kt` (`Lakeland`: the dale's slate stone in thin courses, dry or pointed with
lime, slate flags, dry-stone walls with their cope stones, the oak of the furniture, the willow pattern's blue); the far
views' houses, church and fells in `Vista.kt` (`farSlateHouse`, `farTowerChurch`, `fellSlope`). Every slot, person spot,
stage spot and hiding place is where the default picture has it; only how a thing looks changes. SCENES.md "Culture looks"
has a row per art, and "Every building at its level" the levels; in short:

| Art | The Lake District's look |
|---|---|
| the rooms | slate flags from level 2, a duck-egg dado, an oak plate rail with blue-and-white plates at 4 (for the stencilled border), the fells through the window |
| `kitchen` | the black cast-iron range (oven door, fire behind bars, boiler and tap, brass rail), the oak dresser with willow-pattern plates, a sampler and a brass candlestick for the holy corner, ladder-back chairs, limewash thin on slate stone at level 1 |
| `livingroom` | the fireplace: a slate hearth, the fire in an iron basket, the smoke hood with its oak mantel, brass candlesticks and a china dog; oak settles and chest |
| `cellar` | slate-stone walls, damsons in the crate, brown ale bottles |
| `attic` | slates between the battens at level 1, the wardrobe and the chest of oak |
| `workshop` | slate-stone walls, shepherd's crooks on the wall, a basket of bobbins |
| `tent` | St George's pennant, a dry-stone wall with bracken and foxgloves, green wellingtons |
| `smithy` | slate-stone walls, the yard flagged with slate |
| `church` | slate flags, clear leaded quarries, the open timber roof, the Good Shepherd in an oak frame and a gilt text board, a slate plinth, memorial tablets, a slate bellcote, an oak loft, a brass eagle lectern, a yew |
| `school` | green and cream, an alphabet frieze, the map of the Lakes (LAKES), St George's cross, "Hello! / All well? / Yes!", writing slates |
| `field` | the stone field barn by its level (cart door, byre door, pointed walls, through-stones and steps, quoins, slits and an owl hole, the loft full), dry-stone walls, the fells, the village round its battlemented tower |
| `square` | the green (grass, daisies, a gravel path, flags by the houses), the oak, the village pump by its level, the battlemented church, slate cottages with white window frames and no shutters, a dry-stone wall, daffodils, the fells |
| `market` | slate houses with white frames, the battlemented clock tower, green and cream awnings, heather honey, onions, rhubarb and leeks, ale on the cart, the fells |
| `apiary` | Friuli's build of a shed with tiers, of slate stone under slate with two bee boles, white WBC hives, a green HONEY sign, heather honey |
| `watchtower` | a slate village round its battlemented tower below, walled fields, the fell; St George's cross; the tower under slate, on slate-stone pillars, a battlemented parapet |
| `palisade` | the fells beyond the gate, their walls and bracken, a cairn, a hamlet round a battlemented tower, oak and birch woods |

The map agrees (`MapLooksTest`): the field's barn is the map's hay barn, the green's pump its well, the bee garden's WBC
hives its hives, the watchtower's St George's cross its flag and the tent's its pennant, the school's flag goes with the
map's school of stone; the map's watchtower now stands on pillars of slate stone from level 3, as its view builds it
(`MapLook.peleTower`, `BuildingLooks.towerPillars(slate = true)`). The school's chalk hand learnt the letters of the English
greeting (`ChalkFont` and `ChalkHand`: H, l, A, w and Y).

## How it was checked

CHECKS

## How to use

Nothing to switch on. A Lake District village's bridge serves the scenes and packs at once; the app draws the looks and
bundles the I spy clues from its next build. An older app draws the buildings in the default picture and plays the scenes
all the same: no new art id, no new slot, no new effect (`SceneArt.VERSION` unchanged).

`bun test/check-culture.ts lakeland` (companion/bridge) validates a culture pack as the bridge does at start, with its cast,
the packs and scenarios its village is served and its language's grammar pages (new on this branch, for writing a
culture's scenes).

QA, with a synthetic learner of English (never a real learner's data):

```
bun companion/bridge/test/fixtures/make-learner.ts <dir> --name Jan --target en --base sl
LANI_DEV_DATA=<dir> LANI_ENV_FILE=/dev/null LANI_CULTURE=lakeland LANI_QA_SETUP_PAIR=en-sl LANI_QA_LAKELAND_SHOTS=<shots> \
companion/bin/qa-slot --no-town --steps lakeland-places
```

The step (`lakeland-places`, only when named) makes the village a Trg (QA's hook `hearths:trg`), taps the church on the map,
goes in by its card's "🚪 Go in", opens every building's scene by its link (a few closer up), plays a round of «I spy» on the
green and opens Granny Maggie's breakfast in the kitchen.

## Tap counts

Into a building's scene: before, none (the card had no way in); now 2 (the building, "🚪 Go in"), as in every other
culture. Nothing else changed.

## What is left

- **A native check** of all the English (and its Slovene, Italian and German): every scene, pack and clue file says
  "machine-written" in its `review`.
- **The projects, the third wave.** The Lake District's thirteen projects have no place of their own yet, and the arts that
  draw them on this branch draw the default build: the maypole with its long ribbons (`mlaj`, the green; red, white and blue
  there), the packhorse bridge of slate with one arch (`most`, the beck: the stream's cart bridge is the default timber one),
  the mill on the beck (`mlin`), the bowling green (`balinisce`: the bocce art), the little stone chapel by the road
  (`kapelica`), the damson orchard on terraces (`vinograd`: the hills art), the wildflower meadow for the bees
  (`cebelji_travnik`, the bee garden), the mountain rescue hut (`gasilski_dom`: the fire station art), the playground
  (`igrisce`), the drinking fountain on the square (`vodnjak_na_trgu`: the green draws the default fountain), the bank barn
  (`toplar`: the field draws the default double hayrack, so the field's `toplar` word is "hay barn" until it draws the bank
  barn the map has), the viewing station with its coloured windows (`razgledni_stolp`: the lookout art) and the church clock
  (`mestna_ura`, the green's tower: its face is drawn). Each wants its scene, its pack and clues, its project steps
  (`project-steps/`), and its art's look.
- **The landscape's spots**: the wood, the beck and the tarn (`forest`, `spot:riverbank`, `spot:pond`) have no Lakeland scene
  yet (Friuli's have theirs); a tap there opens nothing. They aren't buildings, so not this round's.
- **Small closer up only**: the sampler's stitches, the plate rail's willow pattern, the china dog and the text board read at
  detail 2 and 3; at detail 1 they're a few pixels.
