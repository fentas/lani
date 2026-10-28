# Built: Carinthia's and the Lake District's own look in their scenes

Jan, 2026-10-28: "yes, do also all the other scenes". Until this round a Carinthian (Kärnten) or a Lake District
(Lakeland) village drew its horizon's scene (Am See, On the fell: art `alps`) as Primorska's picture: Triglav with the
Aljaž turret over Bohinj's lake, a white church with a red roof and a spire, the cika cows, chamois and an ibex, alpine
flowers. The campfire (Am Feuer, By the fire: art `campfire`) already had its village in the culture's way (`FireStyle`),
but the camp's tent flew Slovenia's flag in every culture.

The rules are in companion/SCENES.md "Culture looks" (its table says what each look draws) and in the class comment of
`AlpsPainter`. This note says what was built, how, how to see it, and what is left.

## What each look draws

**Carinthia, Am See** (the Faaker See under the Mittagskogel; `CultureLooks.KAERNTEN_ALPS`):

- the Karawanken behind the lake, the Mittagskogel's clean pyramid (the `peak`) with a summit cross;
- the lake turquoise, paler over the shallows along its shores;
- the church's tower under an onion dome of green copper with a gilt ball and the cross, the nave under dark shingles;
- on the far shore, which runs level along a meadow there, a farm: the farmhouse white below and timbered above with its
  balcony (geraniums from May to September, its windows lit at night, smoke morning and evening), a Harpfe beside it (hay
  on it from June to October), a boathouse on its piles in the water, a jetty with a rowing boat; all of it mirrored in
  the lake;
- the Almhütte of logs on a low stone footing, its gable boarded, stones holding its shingles down, a trough of a hollowed
  log with its running spout by the door (frozen in winter);
- Fleckvieh cows: white heads, gold-red patches over white.

The stone bridge with its two arches, the dry-stone wall, the chamois, the ibex and the marmot stay the art's own:
Carinthia has them too, and the scene's I spy clues describe them so.

**The Lake District, On the fell** (`CultureLooks.LAKELAND_ALPS`):

- rounded fells for the Julian Alps: their walled fields low down (a patchwork of greens, a dry-stone wall round each),
  the open fell above (grass; bracken green in summer, rusting in October, dead russet through the winter; heather in
  flower in August and September), grey crags with scree under them, the summit's cairn, oakwoods along their feet,
  snow only on the tops in winter and lying in patches below; the far fells blue-grey and round;
- the lake dark as slate, a wooded island in it;
- the little chapel whitewashed under slate, its square tower battlemented; the packhorse bridge humped over its two
  arches, its parapet low;
- the shepherd's hut of slate stone to the ridge under a slate roof, a round chimney, a white window frame;
- Daisy, the fell's one cow (an Ayrshire: mahogany patches, lyre horns), among six Herdwick sheep (grey fleece, white faces)
  and Sooty the black lamb ("And Sooty, the little black lamb"); when a dialog counts the cows ("Daisy's not alone today …
  her friends from the farm below!"), all of them come up over the brow, none of the art's own three standing there before;
- Meg the sheepdog lying by the wall ("Come on, Meg! That'll do!");
- the wall of slate stone;
- ravens off the crag for the alpine choughs ("The ravens live there"), a feral goat and her kid for the chamois, a Herdwick
  ram on the crag for the ibex, a rabbit by its burrow for the marmot (its poke silent: no whistle);
- buttercups, clover, harebells and primroses for the gentians and alpine roses, bracken and foxgloves close by;
- oaks and birches for the spruces at the pasture's sides (the framing spruce, the scene's "fir-tree", stays).

**The campfire** (`KAERNTEN_CAMPFIRE`, `LAKELAND_CAMPFIRE`): the camp tent's pennant in Austria's red, white and red, and a
St George's cross. The rest was checked against the content and matches: Carinthia's village round the fire has its white
walls with timbered upper storeys and balconies under dark shingles and the onion dome; the Lake District's its grey stone
cottages under slate, the battlemented tower and the oak.

Every thing of each scene stays where it was and is there to tap exactly when Primorska's picture has it (the seasons
round the clock, the weather, every effect a dialog cues, the wild animals); the stage's spots (path, wall, stream) are
the same. Primorska's picture is pixel for pixel what it was (`CultureLooksTest`'s digests).

## Mismatches fixed (the words follow the picture)

- On the fell's I spy clues (`companion/ispy/scenes/on-the-fell.json`, en and sl) were written from Primorska's picture:
  the fell "grey and white" is now "green and brown", the summit "white" and "pointed" now "grey" with "a pile of stones
  on top" (its cairn), the hut "made of wood" now "made of stone", the goat's "small horns" and "white face with black
  stripes" (a chamois's) now "long horns" and "a beard", the ram "grey and brown" now "grey and white".
- Am Feuer's woodpile "at the edge of the woods" (de, sl, en) is now "in a pile at the back": from the village on the woods
  are gone and a wall stands behind it. By the fire's stones "keep the fire off the grass" (en, sl) now "keep the fire in
  its place": from the market town on the square is cobbled.
- SCENES.md said Am See's bridge was wooden; its clues and the picture have it of stone with two arches: the doc says so
  now. `AlpsPainter`'s comment said three arches; it draws two.
- The marmot's poke whistled wherever it was drawn: `Sfx.poke` reads the look (`Ambience.poke(…, look)` from the scene and
  the visit), and the Lake District's rabbit makes no sound.

The changed clues are machine-written (Claude), not reviewed by a native speaker: please check the German ("Es liegt
hinten auf einem Haufen.") and the Slovene ("Je zelene in rjave barve.", "Zgoraj ima kup kamnov.", "Je iz kamna.",
"Ima dolge roge.", "Ima brado.", "Je sive in bele barve.", "Leži zadaj na kupu.", "Drži ogenj na mestu.").

## How

- `CultureLooks` (game/scene/CultureLook.kt) has the four tables: `alps` variants (`range`, `lake`, `church`, `bridge`,
  `hut`, `cow`, `herd`, `dog`, `birds`, `chamois`, `ibex`, `marmot`, `flowers`, `trees`, `shore`) and the wall's material
  (`slate`); `campfire.flag`.
- `AlpsPainter.readLook()` reads them once a frame into fields; every branch keeps the art's own arithmetic for the
  default, so Primorska draws as before. The range swaps its peaks (`PEAKS_KARAWANKEN`: a pyramid, flanks straight;
  `PEAKS_FELLS`: round backs, 1 − q²) and, for the fells, its colouring (`fell`, `fellFar`, `fellMid`, `fellFlank` over
  `fellside`). The far shore (`farShore`: the farmhouse, the Harpfe, the boathouse, the jetty; the island) is drawn on the
  water and mirrored in it as the church and the bridge are.
- `Vista.dryWall` takes a culture's stone tones (`stones`).
- `CampfirePainter.pennant` draws the culture's flag.

## How to see it

`./gradlew :app:testDebugUnitTest --tests '*AlpsLooksTest*' --tests '*CampfireLooksTest*'`, then
`app/build/scene-snapshots/looks/alps/`: `00-before-after.png` (Primorska's picture, which both cultures drew before,
beside Carinthia's and the Lake District's: summer, autumn, night), `00-<culture>.png` (summer, autumn, winter, spring,
dawn's glow with the pail, night, the cows counted, a storm with the birds and the ibex), each view on its own, closer up
(`-d2`, `-d3`) and on the tablet's canvases (`-tablet-landscape`, `-tablet-portrait`); `looks/campfire/` the flags.
FireAgesTest's `fire-ages/00-kaernten-ages.png` and `00-lakeland-ages.png` show the fire's village at every age.

In the app: a learner of German in Carinthia or of English in the Lake District taps the hills behind the village, "Vstopi
· Go in"; a visitor to such a town sees the host's look. On the emulator: `LANI_DEV_DATA=<a synthetic learner: bun
test/fixtures/make-learner.ts <dir>> LANI_DEV_TOWN_FIXTURE=dev-town-kaernten.json companion/bin/qa --steps visit` visits
"Probedorf", a Carinthian village in German (bridge/test/fixtures/dev-town-kaernten.json), and goes into its horizon:
`visit-scene.png` is Am See in Carinthia's look on the phone (run on 2026-10-28: setup, friends, the visit, the villager,
the scene, the requests, the market, the gift and the sky passed; `visit-home` found Friends where it waits for the
village: "⌂ Home" from a visit now leads back to Friends, where the visit began, which the step doesn't know yet).

## Tap counts

None changed: the look changes the picture, not the way in (the horizon: 2 taps, the hills and "Vstopi · Go in", before
and after; the fire: as before).

## Where a Carinthian or a Lake District village goes in

Its language has no curated scenes yet (no companion/scenes/de or /en), so its places are its pack's own two: the fire
(`campfire`) and the horizon (`alps`), both in its look now. `AlpsLooksTest` checks that no building of either village opens
a scene (`Homes.scenesOf` is empty for every `BuildingType`), so no card shows "Vstopi · Go in" but the fire's and the
hills'. Without a scene (none made in this round), in both cultures: the tent, the field, the well, the hut, the hayrack
(Harpfe; the Lake District's hay barn), the bee house, the palisade, the watchtower, the smithy, the linden (the Lake
District's oak), the house, the church, the school, the market; and the places of the map that open a scene in Primorska
and Friuli: the forest, the stream bank, the pond, and the mill once its project grinds. A scene the tutor publishes
(`publish_scene`) for such a village in another art would draw that art's Primorska picture until the art has the
culture's look.

## What's left

- The other arts have no Carinthian or Lake District look (no scene uses them there yet).
- The village map (game/render/) still flies Slovenia's flag on the tent and the feasts' garlands in every culture.
- A native check of the changed clues (above), and of the picture by someone who knows the Faaker See and the Lakes.
- Frame time (`AlpsLooksTest`, night, 360 × 240 on the desktop, two runs on the shared machine): Primorska 7.62 and
  11.95 ms, Carinthia 7.00 and 11.69 ms, the Lake District 7.37 and 12.47 ms (the budget is 20 ms): the looks cost no
  more than Primorska's picture.
- The Friends card flags a town by its language: a Carinthian town shows 🇩🇪 (data/Towns.kt `flag`).
