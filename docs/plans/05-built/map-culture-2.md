# Built: the village map's landmarks and hayracks as their scenes build them (map-culture-2)

*Built 2026-11-02, branch `map-culture-2` (wave C, part 3: "the village map in each culture's look"). From map-culture.md's
"What's left", the places' notes (places-sl, places-it-*, places-kaernten, kaernten-projects-a/b, places-lakeland,
lakeland-projects-a/b) and followups.md "Scenes' looks per culture". The map was drawn first; the places' scenes came after
it with looks of their own, so the same thing looked one way on the map and another way when the learner stepped into it.
For Jan (Primorska), Teo (Friuli, a tablet) and the Carinthian and Lake District villages.*

## Already done before this round

Checked on main 21aa4e9b, in the code and on the contact sheets (`MapLooksTest`, `MapBridgeLooksTest`), before changing
anything:

- **The tent's flag and the feasts' garlands** are each culture's since map-culture (fe0674cf): `MapLook.pennant` and
  `MapLook.festive` (Friuli's eagle and gold and blue, Austria's red and white, St George's cross and the Union's bunting).
- **The hayrack in Friuli** is its fienile, growing with its level as Nel campo's (7cca2258); in Carinthia a Harpfe and in
  the Lake District a stone field barn since map-culture (redrawn here to match their fields, below).
- **The bridge over the stream** is each culture's since a6f0be83: Friuli's cobble piers, Carinthia's larch, the Lake
  District's slate packhorse bridge as its project and its scene (On the packhorse bridge) build it.
- **The Lake District's toplar** was already its bank barn on the map (lakeland-projects-b); its ramp and doors now follow
  the scene (below).
- **The Friends card's flag** is the culture's (map-culture).

## What changed

Every landmark of a finished project and the hayrack now draw what the culture's scene of the same thing shows outside, as
far as the map's pixels allow. The values come from the culture's `map` table in `CultureLooks`, named as the scenes'
variants (`MapLandmarkLooksTest` checks each against the scene's).

| Thing | Primorska | Friuli | Carinthia | The Lake District |
|---|---|---|---|---|
| Hayrack (the building) | **the kozolec as Na njivi builds it**: a single rack of oak on stone feet, six rails, knee braces, growing with its level (one bay, two, three; boards, then shingles from level 4; full of hay at 5). Before: a double toplar under red tiles | the fienile (as before) | **the Harpfe as Auf dem Feld**: the same rack in larch weathered silver-grey, taller, nine thin rails, steeper roof, growing with its level. Before: brown posts, six laths, end struts | **the field barn as Mr Tyson's field**: the cart door a dark arch of slate voussoirs, the byre's arch from level 2, steps up the gable from 3, quoins from 4. Before: a brown plank door |
| Toplar | **oak, three bays of four posts on stone feet, six rails, a roof of boards** (Pri toplarju). Before: five bays under red tiles | the same, with **maize drying from its tie beam** August to March (Il grande fienile) | **the Doppelharpfe**: grey larch, nine rails, dark shingles (Bei der Doppelharpfe). Before: the Slovene toplar | the bank barn, **its ramp along the front held by a dry-stone wall to the cart door at its left end, its byre doors green** (At the bank barn) |
| Fire station | **the hose tower plastered on its stone footing, the belfry's arches with the bell, a stone cornice, shingles, an iron cross** (V gasilskem domu). Before: a wooden belfry box under red tiles, a spike | the same tower in cobbles under coppi | the same under shingles | **the rescue hut's tower of slate stone under slate with its bell** (At the rescue hut). Before: a radio mast |
| Wayside chapel | **under shingles, its little open turret with the bell on the ridge** (Pri kapelici). Before: red tiles, a cross on the gable | the ancona, **its bell on a bracket** under the gable | **the turret under an onion cap** (Bei der Wegkapelle) | **the stone bellcote** on its gable |
| Lookout tower | **shingles, Slovenia's flag** (Na razglednem stolpu). Before: red tiles, no flag | as before (the eagle) | as before (Austria's flag) | the viewing station **flies St George's cross, its panes green, yellow, orange and blue** as its scene's |
| Town clock | **the church's white tower under its spire of slate, a gold ball and cross** (Pod uro). Before: an ochre tower under a green onion dome | the campanile (as before) | **white under the onion dome**, as the square's church tower. Before: the ochre tower | **its faces of dark slate with gold** |
| Fountain | **eight-sided, of white limestone, the knob on its pillar, the iron pipes and their arcs** (Pri vodnjaku). Before: a square grey basin, a bowl, a gold finial | **the pine cone of stone and the moulded rim** (La fontana) | **St Florian with his bucket on the pillar** (Am Dorfbrunnen) | **green Honister slate, the lion's head spouting** (At the fountain) |
| Maypole | four ribbons (red, white, blue, gold), as the square's | **the greasy pole keeps its spruce crown, wreath and ribbons, the wheel of salami, cheese and sweets under the wreath, no flag** | **red and white ribbons, the Kraxler's Würstel and Speck** under the wreath | **eight ribbons, red, blue, yellow and green, to a child's height** |
| Playground | **the wooden slide, the swing's two plank seats, a blue bucket and a red spade, the red ball** (Na igrišču). Before: a seesaw, one red seat | the same and **the fontanella** | the same | the same |
| Others | **the bocce balls of steel and the wooden jack** (Na balinišču) | the same balls; **sunflowers over the bee meadow** July to October | **the Stockbahn's Daube black** | **the mill of slate stone under slate** (By the beck) |

### Primorska's buildings

The map's own sprites, which Jan's village and every culture without a look draw, against Primorska's scenes of the same
buildings (the lead, 2026-11-01: "Changing Primorska's golden pictures is fine when it corrects a mistake"):

| Building | Before | Now, as its scene |
|---|---|---|
| Kozolec | a double toplar under red tiles, the same at every level (the toplar landmark beside it looked the same) | the single kozolec of Na njivi, growing with its level (in the table above) |
| Houses | all whitewashed, the balcony at every level | whitewashed, ochre or pink by their plot, the balcony from level 2 (Ob ognju's village, FireStyle) |
| Well | a roof of red tiles over the windlass at every level | its roof by level as Na vasi's: boards until 4, shingles at 4, tiles at 5 |
| Linden | the old linden at every level | a slim young tree on its stake at 1 and 2, its crown fuller at 3 and 4, the old linden at 5 (Na vasi) |
| Bee house | on two posts, shingles at every level | on posts at 1, on its stone footing from 2, boards until its shingles at 4 (Pri čebelnjaku) |
| School | ochre | whitewashed over its stone plinth (Na igrišču's side of it); Carinthia's under its dark shingles (Am Spielplatz) |
| Market | striped awnings at every level | plain canvas until level 5, then the red, blue and green stalls (Na tržnici) |
| Tent | two red dots for a pennant, a coloured hem | Slovenia's pennant, plain canvas (Ob ognju's pup tent) |

The watchtower, the church, the hut, the smithy and the field already matched.

## How

- `MapLook` (game/render/MapLook.kt) reads new entries from the culture's `map` table: `toplar` (enum `Toplar`: the
  scenes' `field.toplar` names "pannocchie", "harpfe", "bank"), `chapel` (`Chapel`: "ancona", "onion", "slate"),
  `fountain` (`Fountain`: "pigna", "florian", "slate"), `maypole` gained "maibaum", `clock` gained "onion", and the flags
  `slateClock` ("clockface"), `slateMill` ("mill"), `fontanella`, `sunflowers` ("meadow"). The tables in
  `CultureLook.kt` (`FRIULI_MAP`, `KAERNTEN_MAP`, `LAKELAND_MAP`) set them. Data, no culture ids in the painters.
- The painters: `CultureBuildings.rack` (BuildingLooks.kt) draws the kozolec and the Harpfe from one routine (wood colours,
  rails, height and roof per kind), `hayBarn` by its level; `LandmarkPainter` (Landmarks.kt): `toplar`, `maize`,
  `bankBarn`, `gasilskiDom`, `kapelica` and `turret`, `stolp` and `roofFlag`, `station`, `ura` and `spire`, `vodnjak`
  with `octagon`, `mlaj` with `prizes` and `kraxel`, `ribbonPole`, `igrisce`, `balinisce`, `mlin`, `travnik`.
- Shared: `Pal.HARPFE_*` (the field and the map read the same larch), `BuildingPainter.planks` (a roof of boards, its seams
  down the slope closer up). The map's own onion dome on the town clock (`dome`) is gone with the ochre tower.
- Every landmark keeps its footprint, its tap, its stages (the site, half built, scaffolded, finished) and its lights.

## How to see it

- The contact sheets: `./gradlew :app:testDebugUnitTest --tests '*MapLandmarkLooksTest*' --tests '*MapLooksTest*'`, then
  `app/build/map-looks/`: `05-landmarks-<june|september|winter|night>.png` (a row a culture, a column a landmark, closer
  up), `06-hayrack.png` (the hayrack at levels 1 to 5, in June and in winter), and map-culture's sheets. Before and after in
  the round's scratchpad, `ux-map-culture-2/before/` and `after/`, and one sheet a culture with both,
  `ux-map-culture-2/sheets/<culture>-before-after.png` (the "before" is main 21aa4e9b rendered by this test).
- In the app: a village of any culture with its projects finished. QA: `companion/bin/qa-slot --steps map-landmarks` (only
  when named) on a synthetic learner of the culture (`LANI_DEV_DATA` from `make-learner.ts --target sl|it|de|en`,
  `LANI_CULTURE`, `LANI_QA_PAIR=sl-en`): the village a town, the projects finished by QA's hook, the maypole standing, each
  landmark shown closer up (`landmark-<culture>-<id>.png` in `$LANI_QA_MAP_SHOTS`).

## Tests

- `MapLandmarkLooksTest` (new, app/src/test/.../game/render):
  - the map's entries are the scenes' wherever a scene has the thing, in all four cultures: the hayrack and the toplar
    (`field.kozolec`, `field.toplar`), the bridge and the mill (`stream.cartbridge`, `stream.mill`), the maypole, the fountain and
    the clock (`square.*`), the chapel (`chapel.chapel` or `.turret`), the fire station's walls (`firestation.walls`), the
    fontanella (`playground.fontanella`), the sunflowers (`apiary.meadow`);
  - Primorska's toplar, chapel and lookout add no tile red to the picture round them;
  - digests of every culture's finished landmarks (48: 12 landmarks, 4 cultures, closer up in June), taken after a look at
    the sheets; the sheets.
- `ClassicLandTest`: its digests retaken after a look at the pictures, for the buildings and the mlaj (each view of both the
  village like Jan's and the town, but the jan-like night view, where only the dark differs); it now takes every village's
  digests before it asserts, so one failure prints them all.
- `MapLooksTest` and `MapBridgeLooksTest` unchanged: every building of every culture is drawn and found by a tap at every age;
  a frame of a culture's map costs no more than the default's.
- `VillageRendererTest`, `LandsTest` (a tall building never hides most of a small one) and `WildlifeTest` (every building's tap
  at the middle of its body) caught the first versions of the well and the linden; both were changed to meet them, the tests
  weren't touched.
- `TownWindowTest`: "stamps kept" now counts the most stamps kept at once. It asked for more than 50 at the end of a land's
  frames, but the cache empties itself when full, so that size depended on when it last did; the linden's smaller crowns moved
  that moment.
- The whole Android unit suite on the branch's tip: 483 classes, 3762 tests, none failed (2 skipped). The Python tests (251; 142,
  11 skipped), the bridge's typecheck, its smoke (all checks passed; ports 9690, 9693, 9696, DRAMA3 9708) and `tutor-export
  --check` (89 files) pass; nothing of the bridge changed.

## On the emulator

`companion/bin/qa-slot --steps map-landmarks --no-town` on a synthetic learner of each culture (`make-learner.ts --target
sl|it|de|en`, `LANI_CULTURE`, the phone in sl · en), slot 1, the debug build, 2026-11-02 under a machine's load near 25:
setup, map-landmarks and the crash check passed in all four. Screenshots in the round's scratchpad,
`ux-map-culture-2/emulator/` (`landmark-<culture>-<id>.png`, `-town.png`, `-town-closer.png`): nine landmarks finished by QA's
hook and shown on the map, in today's November light (so no hay on the racks: the sheets have June). The map's frame on the emulator's software rendering at detail 2: 100.9 to 107.6 ms in
Primorska, 73.1 to 78.7 in Friuli, 89.6 to 104.9 in Carinthia, 89.6 to 95.8 in the Lake District, within the range of
map-culture.md's and map-people.md's runs (71 to 227 ms); `MapLooksTest`'s frame budget holds. Primorska again after the well's
and the linden's fix (`emulator-final/`): setup, map-landmarks and the crash check passed, 84.8 to 87.6 ms a frame.

The step's closer look is 2.5 × the fitted zoom, so each picture shows the landmark among its neighbours; the closest looks
are the contact sheets'.

## Tap counts

No journey changes: the pictures do, not the places or their taps (a landmark's card: one tap, as before).

## Credits

No voice: 0 characters.

## For a look by someone who knows

No language here (no text, no voice), so no native check. The forms are the scenes', which were settled against their
sources in their rounds. What wants a look by someone who knows the places: whether Primorska's kozolec reads right as a
single rack beside the toplar (the map drew a double hayrack for both before).

## What's left

- **The well below level 4**: Na vasi's has no roof yet (at levels 1 and 2 a sweep's forked pole, at 3 the posts and the
  windlass). A map well without its roof is mostly hidden behind a church in front of it (`VillageRendererTest`, `LandsTest`:
  52 to 64 % of its pixels, the limit is 50), so the map keeps its height under a roof of boards.
- **The linden's sapling**: Na vasi's level 1 is a sapling; the map's body anchor (`TownAnchors.bodies`, half of
  `spriteHeight`) is the same at every level, so the map's youngest linden is a slim tree with a small crown at that height.
- **The mill's wheel**: the map's is an undershot wheel in its race (mill-mechanics kept it so and set its turn right); both
  mill scenes have an overshot wheel fed by a wooden flume on posts. A flume to the wheel's top on the map is its own small
  piece of work (the race, the wheel's direction, `mill-mechanics`'s checks).
- **Carinthia's mill walls**: In der Mühle builds them of larch logs, its beck's scene (Am Bach) of the art's planks; the
  map keeps the planks until the two scenes agree.
- **Primorska's vineyard**: the map's terraces with the klopotec; Na gričih draws rows on a slope behind one dry-stone wall
  and no klopotec. Which is right for the Brda is a call for someone who knows them (the klopotec is Styria's more than the
  Brda's), so neither changed.
- **Smaller things the map leaves out**: the playground's climbing frame and little house, the Lake District's dry-stone
  wall round it, Carinthia's Schragenzaun round its orchard, the hives the map puts in the bee meadow (its scene has none in
  it), the fire station's sign (PGD, FF, VVF, MRT: a pale plate on the map), Friuli's lookout under coppi on the map where
  its scene's eave is shingles (the scene's side).
- **Primorska's tent's hem**: gone (its scene's pup tent has none); the other cultures' tents keep theirs.
