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

SCENES_TABLE

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
| `school` | green and cream, an alphabet frieze, the map of the Lakes (LAKES), St George's cross, "Hello! / How are you? / Fine, thanks.", writing slates |
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
greeting (`ChalkHand`: H, l, w, y, u, F, t, h and the comma).

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
