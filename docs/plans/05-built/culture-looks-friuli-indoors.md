# Built: Friuli's indoor places in their own look

Jan, 2026-10-28: "yes, do also all the other scenes". Every art drew the Slovene (Primorska) picture in every culture's
village. Teo's Friuli village (Italian from German, the Collio near Gorizia) steps into four places indoors: the tent at
the camp (`nella-tenda`, art `tent`), Nonna Rosa's kitchen in the hut and the houses (`in-cucina`, `kitchen`), Fabbro
Bruno's smithy (`nella-fucina`, `smithy`) and Mugnaio Franco's mill (`nel-mulino`, `mill`). They now draw Friuli's own
picture through the culture look hook (companion/SCENES.md "Culture looks"; `game/scene/CultureLook.kt`). The school is
the friuli-school work's; the outdoor places are the looks-friuli-out work's.

## What

| Art | What Friuli's look draws | Instead of (Primorska) |
|---|---|---|
| `kitchen` | **The fogolâr** in the back corner at every level: a knee-high hearth of grey stone, the fire on its top between two andirons, the copper cauldron for the polenta (cjaldîr) on its chain over the fire, a wooden stick in it, polenta in it in the evening. Level 1: the chain from a pole across the sooty corner, logs stacked beside it, the round polenta board (taulîr) on the wall. From level 2: the white hood over it up to the walls' top, a shelf round its foot (copper pans, the taulîr, a plate, a lace edging), a second bench along the left wall; beside the hearth **the spolert**, the iron range in cream enamel (its oven, its fire door glowing, a blue coffee pot on its plate, its pipe up into the hood). Zia Nives's **radio** on a shelf in the front corner; from level 3 that corner (l'angolo) with a lace edging, a vase and the family's photo. A walnut dresser, an ochre dado, a frieze of vines and grapes, the Collio through the window (rows of vines, a cypress, a church tower); the oil lamp (lum) hanging from the hood's shelf, lit from dusk, instead of the petroleum lamp over the table, which hid the fogolâr | the open hearth with the pot on a trivet (level 1), the green tiled stove and its pipe (from 2), the holy corner (bohkov kot) with the crucifix and the candle, the blue painted dresser with tulips, the green dado and the stencilled flowers, a spruce through the window, the petroleum lamp over the table |
| `smithy` | Walls of pale **river cobbles** with bands of brick (the plain's and the Isonzo's houses), a wrought-iron **grille** in the window, Bruno's **black notebook** of orders on the sill, the **moka** warming on the forge | grey rubble walls, a plain window |
| `tent` | Friuli's **pennant** (the golden eagle on blue) on the front post, a pair of **scarpets** (the Friulian velvet slippers) by the camp bed, a **row of vines** along the meadow's back edge (grapes from July, gold in autumn, bare in winter) | the plain camp |
| `mill` | **Maize**: golden kernels in the hopper, the stream poured into it and the measure, the meal yellow in its sack, its stream and its spill; **cobs** hung up to dry from the gallery and on the wall; the Collio through the window | golden wheat, white flour, a spruce through the window |

Before and after (JVM renders, the Slovene picture on the left, Friuli's on the right, every level, by day and in the
evening; each level closer up, each culture alone; a winter night): `companion/android/app/build/scene-snapshots/looks/`
(`FriuliIndoorLooksTest`), copied for the review to the round's scratch folder `ux-looks-friuli-in/`.

## The words follow the picture

Checked each Friuli scene file, its I spy clues and its dialogs against its picture:

- **The hearth is the fogolâr, at every level.** The Slovene kitchen's open hearth goes when the tiled stove comes
  (level 2); the fogolâr is the Friulian kitchen's heart and stays. The look says so (`fixtures = mapOf("hearth" to
  null)`, a fourth kind of a look's entry: `CultureLook.fixes`/`fixture`), `SceneFixtures.of/there/showing` read the
  world's culture, and `in-cucina`'s hearth has no `needs` any more: "il focolare" is a word of the kitchen at every
  level (the words panel, I spy, a building's upgrade and its sticker go by it). "Il fornello" is the spolert beside it
  from level 2 (a cooker, as the word says, not the hearth).
- **The pot is the cauldron on its chain**: its clues were "È blu", "È sul focolare", "È sul fornello", "Ci cuciniamo la
  minestra"; now "È di rame", "Pende da una catena", "È sopra il focolare", "Ci cuciniamo la polenta" (Rosa's polenta,
  stirred in it, in the evening).
- **"La metto sul tagliere"** (Rosa's polenta): the polenta board was nowhere; it hangs on the wall (level 1) and leans
  on the hood's shelf (from 2).
- **Zia Nives's radio** ("C'è il meteo alla radio!", the Udinese match) was nowhere; it stands on its shelf in the front
  corner at every level. The corner's clues said "C'è appesa una croce", "C'è una candela accesa"; now "C'è la foto della
  famiglia", "C'è una vecchia radio".
- **The stove's clues** said "È verde", "È grande": now "È bianco e nero", "È di ferro". **The dresser** "È blu": now "È
  marrone". **The hearth** "Dentro brucia il fuoco": now "Sopra brucia il fuoco" (a raised hearth), "Nad tem visi
  lonec" for its pot.
- **The mill's flour** "È bianca", "Con questa facciamo il pane e la polenta": Franco grinds maize for polenta, so the
  meal is yellow and the clues say "È gialla", "Con questa facciamo la polenta". The grain's "È giallo" and the hopper's
  "Ci versiamo dentro il mais" were right already; Lorenzo's "Che nuvola bianca!" holds (the dust cloud stays white).
- **Bruno's black notebook** ("Hai il quaderno? Sì, ecco il quaderno nero") lies on the sill; his coffee ("Bruno beve
  il caffè") is the moka on the forge. The window's new clue: "Ha una grata di ferro".
- The tent's content describes its picture as it was (the map on the wall above the bed, the hat with its feather, the
  blue coat); the new things are scenery.

All clue texts are machine-written in it, sl, en and de, flagged so in their files (`machine_written`, `review`), not
reviewed by a native speaker.

## How

- `CultureLooks.FRIULI_KITCHEN`, `FRIULI_SMITHY`, `FRIULI_TENT`, `FRIULI_MILL`: variants (`hearth`/`stove`/`pot`/`corner`/
  `cupboard`/`frieze`/`view`; `walls`/`window`/`notebook`/`moka`; `pennant`/`slippers`/`vines`; `grain`/`view`), colours
  (the dado, the walnut, the maize and its meal) and the kitchen's fixture.
- The painters read the look once a frame (`RoomPainter.readLook`, `MillPainter.readGrain`, the flags at the top of
  `paint`) and branch where a thing is drawn; the default culture's values are the painters' own, so Primorska is pixel
  for pixel what it was (`CultureLooksTest`'s digests).
- The people keep their spots (the stage is the same; `FriuliIndoorLooksTest` checks each person's anchor).

## Tests

- `FriuliIndoorLooksTest`: the sheets; each art and level draws Friuli's own picture; in Friuli every thing a level has is
  drawn and tappable once at every detail, the people where they were; the fogolâr a word at every level, the spolert from
  2, the hearth's sticker from the full kitchen; every culture's scene says the needs its look's fixtures have; a frame's
  time.
- `CultureLooksTest`: Primorska unchanged (every art's digests); the arts Friuli's look doesn't touch draw as Primorska's.
- The whole Android unit suite: 3268 tests, green (the l10n check of the keys the code names found two in the hook's docs,
  "school.plaster.l" and "school.map": reworded). `ISpyContentTest`, `WordStickersTest`, `BuildingWordsTest`,
  `RoomLevelsRenderTest`, `ScenePaintersTest` unchanged and green. The Friuli pack validates (`validateCulture`).

## On the emulator

One run (`companion/bin/qa --steps scene-building --no-town`, the debug build, a synthetic Friuli learner of
`test/fixtures/make-learner.ts --target it --base de` on the dev bridge with `LANI_CULTURE=friuli`, the phone in sl · en as
QA reads it, `LANI_QA_PAIR=sl-en`; 4.5 minutes): the hut's kitchen at level 2 from the village with its building in the
top bar and its card ("Koča, stopnja 2"), its words 23 here now and 3 still hidden for level 3 (the dresser, the clock,
the corner; the fogolâr among the 23); then by QA's hooks ("hour:<h>", "scene:<id>") the kitchen by day and at 20:00
(the lum and the fire lighting it), the smithy (level 1: the cobble walls, the moka), the tent (October: the vines gold,
their grapes, the pennant) and the mill (the cobs, the yellow meal). No crash. The screenshots are in the round's
`ux-looks-friuli-in/` (`emu-*.png`); before this work the Friuli scenes drew the Slovene pictures, the JVM renders'
left halves.

## Frame times

A frame at night with people and a highlight, 360 × 240 on the JVM (`FriuliIndoorLooksTest`: the best of three rounds of
20 frames, the two cultures by turns, on the shared machine); the budget is 20 ms:

| Art | Primorska | Friuli |
|---|---|---|
| `kitchen` (level 3) | 6.78 ms | 7.88 ms |
| `smithy` (level 3) | 5.34 ms | 6.78 ms |
| `tent` | 3.33 ms | 4.97 ms |
| `mill` | 4.48 ms | 4.84 ms |

What costs more is drawn per pixel as the painters' other faces are (the hood's whitewash, the cobbles, the vines' leaves),
nothing per frame that a still picture would save: no cache was needed.

## Taps

None changed: the places are entered as before (the hut, a house, the smithy, the tent: tap it, "Vstopi · Go in"; the mill
from the map or from the stream), and the things are tapped where they are drawn. A word added to a place: "il focolare" in
the kitchen from level 2 (it was a word of the first level only).

## What's left

- A native check of the clue texts (it, sl, en, de).
- The stream's mill outside (the looks-friuli-out work) and the mill inside should agree if that work builds it of stone.
