# Built: every place of the Slovene village to step into (places-sl, 2026-10-30)

Jan, 2026-10-30: "teo upgraded the tower - now he can't enter it > can you build all scenes for all upgrades including slo
culture?" and, for the round: "for choices - make it the best - prod ready". This branch is the Slovene half of the
places round: a scene for each of the village's projects that had none, and one for the palisade, with the six new
pictures they needed (each with its Friuli look from the start). The rules as built are in companion/SCENES.md ("The
village's places: every project a place to go in", "Arts", "Culture looks", "What the village has built", "Seasons",
"Pokes") and companion/GAME.md ("Landmarks to step into"). This note says what was built, how to use it, how it is tested,
what I would change in pictures this branch doesn't own, and what is left.

## What was missing, and what was built

Before, in the Slovene village a finished project's landmark on the map opened a card with nothing to go into, except the
mill (`v-mlinu`) and the vineyard (`na-gricih`); the palisade had no scene at all. Eleven projects and one building had no
door.

| Place | Scene (`companion/scenes/`) | Picture (art) | New art? |
|---|---|---|---|
| the maypole (`mlaj`) | `pod-mlajem` Pod mlajem · Under the maypole | `square` | no: the square draws the maypole once raised |
| the fountain on the square (`vodnjak_na_trgu`) | `pri-vodnjaku` Pri vodnjaku · At the fountain | `square` | no: it draws the fountain |
| the town clock (`mestna_ura`) | `pod-uro` Pod uro · Under the clock | `square` | no: it draws the clock |
| the cart bridge (`most`) | `na-mostu` Na mostu · On the bridge | `stream` | no: the stream draws the bridge |
| the double hayrack (`toplar`) | `pri-toplarju` Pri toplarju · By the double hayrack | `field` | no: the field draws the toplar |
| the bee meadow (`cebelji_travnik`) | `na-cebeljem-travniku` Na čebeljem travniku · In the bee meadow | `apiary` | no (the meadow isn't drawn: see below) |
| the bocce court (`balinisce`) | `na-baliniscu` Na balinišču · At the bocce court | `bocce` | yes, `BoccePainter` |
| the playground (`igrisce`) | `na-igriscu` Na igrišču · At the playground | `playground` | yes, `PlaygroundPainter` |
| the wayside chapel (`kapelica`) | `pri-kapelici` Pri kapelici · At the wayside chapel | `chapel` | yes, `ChapelPainter` |
| the fire station (`gasilski_dom`) | `v-gasilskem-domu` V gasilskem domu · At the fire station | `firestation` | yes, `FirestationPainter` |
| the lookout tower (`razgledni_stolp`) | `na-razglednem-stolpu` Na razglednem stolpu · On the lookout tower | `lookout` | yes, `LookoutPainter` |
| the palisade (a building, levels 1–5) | `pri-vratih` Pri vratih · At the gate | `palisade` | yes, `PalisadePainter`, by level |

Each scene: its pack (`companion/packs/<id>.json`, 13–16 words), its I spy clues (`companion/ispy/scenes/<id>.json`, 50–84
clues), 12–20 things, 5–6 happenings over the day and the year, 10–14 dialogs with their variants (20–29 turns, 34–51 wrong
choices, each with its why and the person's reply in sl, en, de and it), faces, puzzled reactions, a counting variant, every
slot of its art as a word and, in the new arts, every effect cued. Who is there and what happens is in SCENES.md's table.
The levels: A1 for eight of them, A2 for the bocce court, the hayrack, the fire station and the lookout.

The new arts (game/render/scene/, each a full class comment of all it draws, its effects, pokes, seasons and Friuli look):

| Art | Painter | Kind | Lines | Things | Spots | Effects | Poke |
|---|---|---|---|---|---|---|---|
| `firestation` | `FirestationPainter` | diorama | 1,054 | 16 | pump, hoses, door | bell, pump, doors, lantern, horn | the bell, a helmet |
| `bocce` | `BoccePainter` | diorama | 680 | 15 | throw, jack, table | throw, hit, measure, pour, score, lantern | a ball by the jack |
| `playground` | `PlaygroundPainter` | diorama | 614 | 15 | swing, sandpit, bench | swing, ball, kite, bubbles, castle | the swing |
| `chapel` | `ChapelPainter` | vista | 727 | 15 | chapel, bench, road | bell, candles, flowers, traveller | the bell |
| `palisade` | `PalisadePainter` | vista, by level | 678 | 15 | gate, bench, road | gate, torch, wolves, flock, pedlar, bell | the bell |
| `lookout` | `LookoutPainter` | vista | 560 | 16 | rail, board, stairs | clear, glow, hawk, bell | the telescope |

The palisade grows with its level, each level something a child sees (300 pixels of the 240 × 160 picture at least, by
day, as the buildings' levels are judged): the plank wings and the torches (2), the gate's roof, its pennant and the
stakes' charred points (3), the walkway and its ladder (4), the stone footing, the bell and the carved sun (5).

Their Friuli looks (`CultureLook.kt`, `FRIULI_PLACES`): the fire station in Friulian stone under coppi, the VVF's sign,
chrome helmets, St Barbara's picture and the tricolour; the bocce court by an osteria in ochre with brown shutters and the
frasca over its door; the playground by a Friulian school with a fontanella; the chapel an ancona among the maize and the
poplars; the palisade's land and the lookout's view Friuli's (the Collio's vines, the campanile, the plain to the sea).

## The square at every age, and the hours as people say them

- **Na vasi** needed the age Vas, while its well stands from Tabor and its linden from Zaselek: a hamlet with its linden
  had a square nobody could step into. It now opens wherever one of them stands. Stari Janez's old linden (two hundred
  years, "starejša je od cerkve") needs LIPA at its fifth level, the picture of the old linden; before that he sits by
  the young one the village planted (two new dialogs: it grows slowly and lives long, a bucket of water; on hot days the
  dual, "jo zalijeva"); Mojca's dual at the well keeps Vas (the school's teacher, the church's bell).
- **"Ob dvanajst", "ob deset", "ob sedem", "ob štiri"** were graded as a wrong case in Pod uro, Pri kapelici and Na vasi,
  while the village's own people say them so (Na vasi's "ob štiri", V cerkvi's "ob deset"): they are right now, the
  reply saying the written form back.

## How to use it, and the taps it takes

Counted on the emulator from the plain village map with a synthetic Slovene learner whose village has the projects
finished and a palisade (QA's hooks):

| Journey | Before | After |
|---|---|---|
| Into a finished project's place (the bocce court, the fire station, …) | not possible (the card had no door) | 2 (the landmark, "🚪 Vstopi") |
| Into the palisade's gate | not possible | 2 (the palisade, "🚪 Vstopi") |
| A happening there from the village | not possible | 1 (its bubble over the landmark, as every place's) |
| I spy there | not possible | 3 (the landmark, "🚪 Vstopi", the child's "Vidim, vidim") |

**The emulator** (QA's step `places`, 8 ½ minutes inside the emulator's lock, a synthetic Slovene learner Ana of a
Vas village with a palisade, the dev bridge's fake voices; the shots in the round's scratchpad,
`ux-places/places-sl/qa/places-*.png`): the bocce court finished by QA's hook, its landmark on the map, its card
"Balinišče · A bocce court" with "🚪 Vstopi · Go in: 🎳 Na balinišču · At the bocce court ›" over "Skupni projekti",
inside (15 things, "Danes tukaj"), Stari Janez's game opened at its happening ("Dober dan, Ana! Igrava? Poglej, bulin je
že na koncu balinišča.", its turn "Igramo! / Igrava! Kdo vrže prvi?"; on a fresh install the first answer brings the
offer of the learner's own voice first, as everywhere), «Vidim, vidim» offered by Zala who came by, the clue "Je svetlo
rjav.", the jack tapped, "Ja, to je bulin! Bravo!"; then the playground, the chapel at dusk, the fire station, the lookout
and the palisade's gate by day and at night (the palisade at its first level: its five things to come listed under "Še
skrito · Still hidden" with the level each needs). No crash.

## How it is tested

Run on the rebased branch: the bridge's `tsc` clean, its smoke 3,098 checks passed (ports 9010–9018), `tutor-export
--check` 87 files match; the Android unit suite 3,393 tests through lani-slot.sh, all passing but one timing check of an
existing art on the loaded machine (`SceneDetailTest` "a closer frame renders fast": the kitchen, the Alps, the stream at
26–30 ms against 25; none of the new arts), and the scene battery again after the last changes (254 tests, the same one).

- **The pictures.** Every art goes through the generic art contract: `ScenePaintersTest` (a hit for every thing and
  spot, sane sizes, taps to the pixel), `SceneDetailTest` (every detail, a window is that part of the whole, the hits stay
  put), `SceneEffectsTest` (every effect shows at every detail), `SceneWeatherTest` (the weather never moves a hit),
  `ScenePokesTest`, `SceneSightsRenderTest`, `PeopleClearTest` (nothing over a head at any spot, by day and night, built
  and not), `SceneWorldRenderTest` (the palisade differs at every level, its top level the default), `StickersTest`,
  `SoundscapeTest`, `CultureLooksTest` (the default culture's digests of the six added; the other cultures as the
  default unless their look touches the art). `PlacesRenderTest` renders each new art for review and checks that every
  effect changes the picture from 0 to 1, that the Friuli look changes each place and keeps every thing tappable, and the
  palisade's levels.
- **The scenes.** `ScenesContentTest` (31 curated scenes now, the registry's arts, slots and spots, the dialogs that keep
  their effects or their weather), `DialogReactionsTest`, `ISpyContentTest` (the clues' Slovene and their "where" against
  the hit boxes), `SceneCoherenceTest`, `VariantsContentTest`, `TurnHintTest`, `HintSpeechTest`, `DialogWordsContentTest`,
  `IntroductionContentTest`, `WordStickersTest`, `BuildingWordsTest` (the palisade's upgrade now asks for the things at its
  gate at its level); the bridge's smoke validates every scene and pack and compares its art registry with the app's.
- **Older apps.** The six arts are `SceneArt.VERSION` 4 (`SCENE_ARTS_VERSION`, `ADDED[4]` in bridge/src/scenes.ts): an app
  that asks with `arts=3` or less never gets a scene of an art it can't draw; the six scenes on existing arts reach it
  as they are (no new slot in an old art).

## For the native speaker

All the Slovene is machine-written (each file's `review` says so and lists its words to check). Never grade a spoken form
wrong. The words to look at first: bulin, brajda, zbiti, bližja/bližje, "Rana ura, zlata ura" (the bocce court); mreža for
the chapel's grille, avemarija, zvoniti proti toči; brizgalna, cev, prapor, Florjanovo; ristanc, plezalo, hiška, lopatka,
gugati se, porini me; kol, zapah, tečaj, škripati, krošnjar, suha roba, "pojdiva vprašat"; kanja, žig, vpisati se, zarja;
stražiti mlaj, godba, "Živijo!" as a toast; štirna for the square's draw well (so that vodnjak is the fountain), curek;
četrt na enajst, pol ure čez eno, navijati uro, zamuja/prehiteva, "zdaj bo bila"; narasel/upade; toplar, lata, kopica, vile
as a plural; facelija, točiti med, "v kepi"; "Mir je največ, kar imamo".

## What I would change in pictures this branch doesn't own

- **The square** (`SquarePainter`): its people stand on the bench, at the well and at three play spots, and everyone at a
  play spot strolls to and fro and kicks the ball with the others there. The three square scenes have grown-ups at the play
  spots (Tone and France at the fountain, Mojca and Ančka under the clock), so they join the children's game. The square
  should get standing spots by its projects (by the maypole, at the fountain, under the clock) where a grown-up stands
  still, and only children should play; then the content moves them there. The maypole, the fountain and the clock are
  drawn (`SceneArt.projects`) but aren't things to tap: slots `maypole`, `fountain`, `clock` would let the words, I spy and
  "poglej" point at them (the lines say "Vidiš …" for now, never "poglej").
- **The stream** and **the field**: the cart bridge and the toplar are drawn by the project's steps but aren't things to
  tap either (`cartbridge`, `toplar` slots).
- **The apiary**: the bee meadow (`cebelji_travnik`) isn't drawn at all; `na-cebeljem-travniku` stands by the bee house.
  A meadow of phacelia, buckwheat and clover in front of the hives once the project is finished would make it the place.

## What is left

- **Friuli scenes on the new arts**: the bocce court, the playground, the fire station, the chapel (an ancona), the
  palisade and the lookout for Teo's village (the places-it-projects round's follow-up once this branch is merged; the
  arts' Friuli looks are ready).
- **The square's standing spots and the three things to tap** above, then moving the square scenes' grown-ups there.
- **The native check** of the twelve scenes, their packs and I spy clues.
- **Voices**: the new lines get their clips as for any scene (`voice-build`).
- **The villagers' days**: nobody's routine (`Routine.options`) goes to the new places yet, so their people are there with
  their happenings only ("Zdaj je tukaj mirno" between them). Stari Janez's afternoons at the bocce court, the children's
  at the playground, Teta Ančka's evenings at the chapel and Tone at the fire station would fill them, as France tends
  the mill (`needs = "project:mlin"`).
- **The my-voice offer** comes over the first answer of the first dialog on a fresh install (QA's places step answers
  through it in a later run once it dismisses it, as `my-voice` does).
