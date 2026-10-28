# Built: Friuli's outdoor scenes in Friuli's look

Jan, 2026-10-28: "yes, do also all the other scenes". Until then Teo's Friuli village drew its field, its wood, its stream
and its pond as the Slovene (Primorska) pictures: a kozolec in the field, a spired church on the hill. Only the fire's
village round it (`FireStyle`) and the sea at Grado were Friuli's own. This note says what Friuli's look draws outdoors,
how it is built, how to see it, what it costs and what is left. The rules for every culture's look are in companion/SCENES.md
("Culture looks"); the indoor scenes (the kitchen, the smithy, the mill, the tent) are another part of the round.

## What changed, by scene

| Scene | Art | Before (Primorska's picture) | Friuli's look |
|---|---|---|---|
| Nel campo | `field` | the kozolec, the wooden hayrack with its hay on the rails; the village on the hill with a spired church; fields and hedges on the near hills; apple trees | the fienile of the Friulian plain in its place: walls of river cobbles in courses with brick bands and quoins, two round brick arches (the hay heaped under the left one), the hay loft above with its pole and the hay bulging out, brick lattices (gelosie) either side and in the gable, a low coppi roof; the village as the fire's village is built (ochre, terracotta and cream houses under coppi, the church's gable, the free-standing brick campanile with its belfry and pyramid cap, the evening bell ringing from it); farmsteads with brick barns; vine rows on the Collio's slopes; the piantata (pollarded mulberries behind the corn with the vines strung between them) and Lombardy poplars along a far ditch |
| Nel bosco | `forest` | the clearing as before | the same clearing with an ancona (the maina, the wayside shrine of the Friulian roads) right of the path: a plastered pillar, the Madonna in her blue niche under a little coppi roof, flowers in a jar, from dusk a red lumino and its light; Aldo's basket of mushrooms by him while his dialog counts them |
| Al ruscello | `stream` | spruces and beeches along the banks and in the woods' edge; wooded hills | Lombardy poplars among the alders and in the woods' edge (gold in October, a bare broom in winter); vine rows on the hill over the valley and a hamlet with its campanile. The mill stays as it is: plank walls on a stone footing under shingles, as the project "Il mulino sul ruscello" builds it and as the mill inside (Nel mulino, the indoor part of the round) has its plank walls |
| Allo stagno | `pond` | spruces and beeches round the pond | the lowland wood of the coast's plain: Lombardy poplars and oaks round the pond, mirrored in it; Nonno Bepi's tin of worms by Moro when they fish |
| Il mare | `sea` | Grado's lagoon (Friuli's own art) | the batela's halyard from the masthead to its cleat, the rest coiled by the stern |

Before and after: the "primorska" column of each sheet is what Friuli drew before (every culture drew it then), the
"friuli" and "friuli-coast" columns what it draws now. Screenshots in the scratchpad's ux-looks-friuli-out/ (the sheets per
art: `<art>-sheet.png`, `<art>-tall.png`, `<art>-wide.png`; every view closer up), and after
`./gradlew :app:testDebugUnitTest --tests '*FriuliOutdoorLooksTest*'` in app/build/scene-snapshots/looks/friuli-out.

## The words and the picture

Each Friuli scene's lines were read against its picture ("every look line shows its thing", companion/SCENES.md "Culture
looks"). What did not match, and how it was made to:

| Scene | The words | The picture before | Now |
|---|---|---|---|
| Nel campo | the slot `kozolec` is "il fienile", its clues "È di legno", "Ci asciughiamo il fieno" | a wooden hayrack | the fienile; its clues "È di sassi e mattoni", "Ha due archi", "Ci teniamo il fieno" (companion/ispy/scenes/nel-campo.json, machine-written); the word's Slovene gloss "senik" (it was "kozolec, senik"; companion/cultures/friuli/packs/nel-campo.json, machine-written) |
| Nel bosco | Aldo: "guarda nel mio cesto. Quanti funghi ci sono?" (counted, 3 to 12) | no basket: nothing to count | his basket by him at the clearing while the dialog counts, with that many mushrooms |
| Allo stagno | Nonno Bepi: "mi passi la scatola dei vermi? È lì, vicino a Moro." | no tin | the tin of worms on the grass right of Moro while he is there or someone fishes |
| Il mare | Nonno Bepi: "Adesso alziamo la vela. Vedi la corda lì?" | no rope | the halyard from the masthead to its cleat |

The rest was checked and holds: the crows, the scarecrow's hat and scarf, Baio and the cart, the wheat golden in its
months; Bianca's bell, the fly agaric and the porcino, the stag and the hinds, the owl on the fir; the laundry, the sheep
and the lamb at the water, the stones; the heron on one leg, the frog on the lily, Moro, the float, the tadpoles; the gulls
on the posts, the waves, the sail and the casoni. The project "Il grande fienile" (`toplar`) says eight posts, beams and a
plank roof, as the field's big barn draws it, so it stays as it is.

## How it is built

- `CultureLooks` (game/scene/CultureLook.kt): Friuli's tables `FRIULI_FIELD`, `FRIULI_FOREST`, `FRIULI_STREAM`, `FRIULI_POND`,
  `FRIULI_SEA`, variants only (`field.kozolec` = `fienile`, `field.village`, `field.hills`, `field.trees`, `forest.shrine`,
  `forest.basket`, `stream.trees`, `stream.hills`, `pond.trees`, `pond.bait`, `sea.halyard`).
- Each painter reads its variants once a frame at the top of `paint()` and branches where the thing is drawn; every branch
  of the default culture is as it was (`CultureLooksTest`'s digests).
- The shared pieces are `VistaPainter`'s (game/render/scene/Vista.kt): `poplar`, `mulberry`, `vineGarland`, `collioSlope`
  (a hill's vine rows as a ridge's texture), `farCoppiHouse` and `farCampanile` (the village far off in a `FireStyle`'s
  colours, so the field's and the stream's hills show the same village as the fire's square).
- The fienile (`FieldPainter.fienile`, `fienileHay`) is laid out in metres in the field's plan where the kozolec stands:
  its front, its gable end toward the track, the roof's front slope in perspective; closer up the same barn shows its
  cobbles, the mortar and the brick joints finer. It keeps the kozolec's slots, its `needs` (the KOZOLEC building), its
  person spot in front of it and the rake against its corner.
- The basket reads `SceneFrame.count` (the dialog's number); the tin shows with the pond's `dog` or `bite`; neither is a new
  effect, so the bridge and the content need nothing new.

## Tests and the frame

`FriuliOutdoorLooksTest` (app/src/test/.../game/render/scene): the review renders; Friuli's look changes each of its five
arts, inland and on the coast; every thing and person of Primorska's picture is there to tap in Friuli's at every detail and
size; the fienile stands where the kozolec stood with the hay in it and the rake at its corner; the basket holds the
count; the shrine, the tin and the halyard; every effect still plays; nothing drawn over a head at a person spot; the
frame's time; the digests of Friuli's pictures (every art, inland and on the coast, at every detail, with what the village
has built and without). `CultureLooksTest`: Primorska pixel for pixel as before.

Frame times (a night frame, 360×240, the JVM on the shared machine, in the same run of the whole suite after the rebase
onto main; the budget is 20 ms, scaled):

| Art | Primorska (`ScenePaintersTest`) | Friuli on the coast (`FriuliOutdoorLooksTest`) |
|---|---|---|
| `field` | 4.4 ms | 5.0 ms |
| `forest` | 3.8 ms | 3.3 ms |
| `stream` | 6.0 ms | 6.0 ms |
| `pond` | 4.7 ms | 4.4 ms |
| `sea` | 5.6 ms | 5.6 ms |

The machine is shared, so a few tenths either way are noise; the look draws no more than the picture it replaces (the
fienile's front is a few thousand pixels, as the kozolec's rails and hay were).

## On the emulator

`companion/bin/qa --steps friuli-looks` (only when named; on a synthetic Italian learner, `bun
test/fixtures/make-learner.ts <dir> --target it`, `LANI_DEV_DATA=<dir> LANI_CULTURE=friuli LANI_QA_PAIR=sl-en`, `--no-town`):
the village built up as a Vas (QA's hook `hearths:vas`, so the field has its kozolec's place for the fienile), then each
scene by its link at 11 o'clock, closer up, and the field and the wood at night; no crash, the scenes' frame times in the
log. The run of 2026-10-28 (the debug build, on the shared machine while its load average was over 100) logged 61 to 200
ms a frame at details 1 and 2: the emulator's software rendering under that load, not the look (the JVM's times above
are the comparison). Its screenshots: the scratchpad's ux-looks-friuli-out/emulator (`friuli-<scene>.png`,
`-closer`, `-night`).

## Tap counts

No journey changes: a scene opens as before (from its place on the map, one tap and "Vstopi · Go in"), every thing is
where it was to tap. Before and after: the same taps.

## What's left

- The village map draws the kozolec building as the Slovene double hayrack in every culture; the field's fienile is
  Friuli's only in the scene.
- Primorska's own wood (V gozdu) counts Anton's mushrooms in his basket ("Preštej gobe v moji košari") without a basket in
  the picture, as Friuli's did; the same `basket` variant could serve it (a Primorska look, or the painter's own once the
  golden digests are taken again).
- The clues' new lines need their voices built (voice-build) before Teo hears them.
- The machine-written Italian, Slovene, English and German lines (the clues, the gloss) want a native speaker's check.
