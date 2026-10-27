# Built: the fire's scene grows with the village

Jan, 2026-10-27, at Vas: "the fireplace scene — I think it should change over time". Until then the scene Ob ognju (art
`campfire`, `CampfirePainter`; Al fuoco, Am Feuer and By the fire in the other cultures) followed the village's age only
where it was cheap: its name, plank benches from Vas, cobbles round the fire from Trg. Everything else stayed a campfire in
a clearing under spruces, and SCENES.md said "A scene of the square with its houses round the fire is left for later".

The rules as built are in companion/SCENES.md ("Ob ognju by the age"). This note says what was built, how, how to see it,
what it costs and what is left.

## What each age looks like

| Age | The place | Its fire | Round it |
|---|---|---|---|
| Ogenj, Tabor | the camp in its clearing among the spruces, as before; the tent while it stands | the campfire in its ring of field stones, the tripod and the kettle | two logs to sit on (longer from Tabor, for the crowd's seats) |
| Zaselek | the ground trodden bare round the fire and out to the huts' doors; the first huts (and a house) on the lots at the back where the spruces made way; the young linden, the church once built | the campfire | log benches: a split log on two blocks |
| Vas | packed gravel with grass along the edges; the linden over the ring; the house fronts round the place, gables to the square; the church with its bell tower; the well at the square's front right; a lantern; a dry-stone wall behind the woodpile | the fire ring of big flat stones | plank benches |
| Trg | cobbles over the square (earth round the linden's foot); the market's stalls; the well of stone, or the fountain as far as its project has come; a second lantern | the fire basket on three legs inside a kerb of dressed stones, the kettle hung higher; the kres on a feast evening | plank benches |
| Mesto | paving of cut stone with a ring of wedges round the basket; four lanterns; the clock in the church's tower as far as its project has come | the fire basket | benches on iron legs |

Each culture in its own way (`FireStyle`): Primorska's white and ochre plaster, green shutters, red tiles, wooden
balconies with geraniums, a spire; Friuli's ochre, terracotta and rosy plaster under coppi and a brick campanile with its low
cap (on Teo's coast sand, umbrella pines round the camp, the lagoon's thatched casoni for huts, blue shutters); Carinthia's
white walls with a timbered upper storey under dark shingles and an onion dome; the Lake District's grey stone cottages
under slate, a square tower with battlements, an oak for the linden.

The homes are the village's own (`SceneWorld.homes`: its huts and houses by their plots, the nearest the centre first), up
to five on the lots round the square (three while the tent stands by the fire), each a hut or a house as built, at its
level, its chimney smoking and its windows lit in the evening unless its hearth is out. The linden grows with the days
since it was planted (`FireLayout.growth`).

Screenshots: build/scene-snapshots/fire-ages after `./gradlew :app:testDebugUnitTest --tests '*FireAgesTest*'`
(`00-<culture>-ages.png`: every age by day, in the evening with the village round its fire, on a winter's night, in an
autumn morning; `00-tablet-ages.png`; every age at every detail; the feasts and the fire by the hour); QA's `fire-ages` on
the emulator (below).

## Over the day and the year

`FireBurn` (pure, game/render/scene/FireVillage.kt) decides how the fire burns before a dialog's cues: the campfire (to
the hamlet) all day, low in the morning, let die down in a summer's noon unless someone sits by it, built up for the
evening, burning down at night; from the village for the evening's gathering (17:30 to 23:30, as the map's `CentreLook`),
the lunch by the fire and whenever someone sits by it, its coals by day, out in a summer's noon and when the village had
no wood for it (`SceneWorld.Centre.out`, from `Hearths`); the kres on a feast evening from the market town. A dialog's
`flare` and `embers` work on top as before (a `flare` lights a cold fire). The scene's sound follows it
(`Place.Scene.fire`: the coals a murmur, out silent, the kres louder). Snow on the roofs, the benches, the stones, the
square swept round the fire; the linden by the season, blossom in June and July.

The feasts as the map has them (`AgeUps.decorOn`): Tabor's torches, the likof's spruce on the newest roof, garlands of
flags round the square with little lanterns in them after dusk (also on every feast day from the village on), the town's
banners.

## Who is there

`Whereabouts.inScene` puts the village's others whom their day has at the fire now at the crowd's spots (`FireCrowd`: 2 at
the camp, 3 at the hamlet, 5 at the village, 6 at the market town and the town; one or two fewer while the tent stands by
the square's fire), the cast first, each as themselves; the scene's own people keep the stage's spots. The unnamed people of a
bigger village (`SceneWorld.Centre.unnamed`) take the spots nobody took on an evening by the fire and at a feast, as the
map has them, as figures not to tap. The crowd's spots are no part of the stage (`DioramaPainter.tapSpot`): no `act` can
send anyone there and no tap turn names one, so every dialog keeps working at every age.

## The frame

What stands still (the room round the diorama, the Milky Way, the ground, the woods and the houses at the back, the
linden, the stalls) is drawn once and kept while the world, the season and the light stay the same (`ArtPainter.still`,
the pattern of the map's `GroundCache`): the next frames copy it back with its objects and draw only what moves. The sky is
drawn behind it (`PixelCanvas.behind`), its moon's object taken first so the outlines come out as before; the moon keeps
above the roofs, the tower and the linden while the sky has room (`FireVillagePainter.skyline`). `FireAgesTest` checks the
kept picture against a frame drawn whole, pixel for pixel and hit for hit, at every age and detail.

Measured (`FireAgesTest`, the JVM, 24 frames after 8, a 360 × 240 canvas at night with the village round the fire, the
machine shared with other builds; the old painter's in a throwaway test before the change):

| Age | Detail 1: kept / drawn whole | Detail 2 | Detail 3 |
|---|---|---|---|
| Ogenj | 1.2 / 5.5 ms | 1.9 / 13.0 ms | 2.4 / 20.1 ms |
| Tabor | 1.0 / 5.6 ms | 2.4 / 14.2 ms | 3.3 / 18.8 ms |
| Zaselek | 1.0 / 8.3 ms | 2.4 / 20.3 ms | 2.4 / 21.8 ms |
| Vas | 1.0 / 6.8 ms | 2.0 / 17.4 ms | 2.9 / 19.5 ms |
| Trg | 1.0 / 7.5 ms | 2.1 / 17.3 ms | 2.6 / 17.7 ms |
| Mesto | 1.0 / 5.7 ms | 2.3 / 18.1 ms | 2.5 / 17.4 ms |
| the camp's painter before | 7.0 ms | 14.0 ms | 16.7 ms |

A frame with the still picture kept costs 1 to 3.5 ms at every age and detail: a fifth or less of the camp's frame before
(drawn whole every frame), though the town's square has far more in it. Drawn whole (the first frame, a new window while
panning closer up, a change of the light's colours at dusk) the village's frame costs about what the camp's did. The
light pass and the outlines (over the whole canvas each frame) are most of what is left.

## Tests

- `FireAgesTest`: renders for review; each age differs from the one before; every thing to tap at every age, culture,
  canvas and hour; the stage's spots and the crowd's (someone there drawn, person-sized, nothing over their head; behind the
  stump by design); the village round the fire leaves the fire to tap and everyone in view; the crowd's count; the kept
  still picture is the picture; the moon in the sky while it is up; the fire by the hour; every culture its own; the
  linden's growth; a frame's time.
- `FireCrowdTest`: who of the village stands round the fire on its evenings, at the age's spots; none round the first
  campfire, none in another scene.
- `SceneWorldTest`: the centre of a game state (its homes by their plots, the linden's day, a feast, the unnamed).
- `SoundscapeTest`: the fire's crackle as it burns.

## How to see it

- On the phone: open the fire's scene (the fire's card, "Vstopi · Go in") at any age; it shows the village as built.
- QA: `companion/bin/qa --no-town --steps fire-ages` (the hooks `hearths:<age>:pond`, `hour:<h>`, `hearths:feast`, `zoom`).

## Tap counts

Nothing to tap changed: the scene opens as before (the fire's card, 1 tap to "Vstopi · Go in"); its things and people are
tapped as before; the village's others at the fire are one tap each to their card, as anyone in a scene.

## What's left

- The strings are unchanged (no new labels); nothing to check by a native speaker.
- A tablet performance round on the device (Teo's Galaxy Tab S6) is planned separately; the JVM numbers above are the
  basis.
- The stalls and the second home on the right make way for the tent while it stands by the square's fire.
- Not drawn: the school, the smithy and the market's building themselves (the scene shows the homes, the church, the
  linden, the well or fountain and the stalls); the clock tower's project and the fountain only as far as their steps.
