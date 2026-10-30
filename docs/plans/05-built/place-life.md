# Built: the places come alive (place-life, 2026-10-30)

Jan, 2026-10-30: "sure do follow ups" (the second batch, its part 3) and, for the round: "for choices - make it the best -
prod ready". The places rounds (docs/plans/05-built/places-sl.md, places-it-projects.md) gave every village project a
scene, but the projects themselves were scenery in the pictures they stand in: the square's maypole, fountain and clock,
the stream's cart bridge and the field's double hayrack were drawn and couldn't be tapped, the bee meadow wasn't drawn at
all, the grown-ups who stand by the square's projects stood at the children's play spots and joined their ball game, the
greasy pole had no prizes, the clock struck nothing, nobody's day went to the new places, and the map's bridge was the
Slovene timber one in every culture. This note says what was built, how, how to see it, how it is tested, and what is
left. The rules as built are in companion/SCENES.md ("Arts", "What the village has built", "Culture looks", "Numbers",
"Scenes of a culture pack"), companion/VILLAGERS.md ("A day in the village") and companion/GAME.md ("The map in a
culture's look").

## What was built

### The finished projects are things of their pictures

| Art | Slot | There once | Drawn (the default culture) | Friuli's look |
|---|---|---|---|---|
| `square` | `maypole` | `mlaj` finished and standing (not down for the season) | the peeled pole on its ring of stones, the young spruce's top, the wreath and four ribbons (red, white, blue, gold) | the greasy pole: its soaped trunk shining down its lit side, a glint sliding down it; under the wreath the wooden wheel of prizes (a salami, a wheel of cheese, two bags of sweets tied with blue and gold); the wheel lies ready on the cobbles from the fourth step ("Hang the prizes") and goes up with the pole |
| `square` | `fountain` | `vodnjak_na_trgu` finished | the round trough of white stone, the pillar with its four iron pipes, the water | a pine cone of white stone on the pillar (the Venetian fountains of the plain), a moulding under the rim |
| `square` | `clock` | the church and `mestna_ura` finished | the face in the tower, the hands at the phone's hour, lit at night | the same face in the brick campanile |
| `stream` | `cartbridge` | `most` finished | the two stone piers, the logs, the plank deck, the railings, the ramps | piers and ramp walls of river cobbles in courses with a band of brick every fifth |
| `field` | `toplar` | `toplar` finished | eight posts on stone feet, the rails hung with hay, the plank roof | il grande fienile: bunches of golden-orange maize cobs hung from its front beam, August to March (the village's stores) |
| `apiary` | `meadow` | `cebelji_travnik` finished | new: a strip of tall meadow along the garden's front and round its front corner, by the month (dandelions and daisies; clover, phacelia, buckwheat; red buckwheat and asters; dry stalks and snow), every other bee out to it | the children's clover with a row of sunflowers along its back from July, bowed and brown in October |

Before its project is finished each stays scenery as far as its steps have come (SceneFixtures says when a slot is there,
`SceneSights` lists it "still hidden" with the project to finish). A thing that was a `prop` is now a `thing` drawing
exactly the same pixels; the clock is an object of its own without an outline inside the tower's object, so the tower is
drawn round it as before (`SquarePainter.clockThing`). The square's maypole is the thing also while a dialog raises it
again, so an effect never takes it away.

### The square's standing spots

`maypole` (behind the flower bed, left of the pole's foot), `clock` (in the middle of the square, behind the bed's right
end, under the tower) and `fountain` (in front of the fountain's right side, clear of the well), at 13–17 m, near enough
for a person sprite to stand for a person, the projects themselves just over their heads. They are there whatever is
built, as the well's spot. The play spots are the children's: a grown-up left at one stands still and watches the game
(`SquarePainter.CHILDREN`). `PeopleClearTest` holds them clear (the I spy child too), `PlaceLifeRenderTest` keeps them
apart from each other.

### The greasy pole's climb and the clock's strike

- `climb` (square): a lad shinning up the maypole, seen from behind, hugging it, arms and legs taking turns, scaled to
  its distance; 0 at its foot, 1 at the top reaching for the prize (Friuli's on its wheel); a cue lower slides him down.
- `strike` (square): the clock strikes the hour, once it goes: one stroke every 1.8 s from half a second after the cue,
  the bell swinging to one side and back and a ring going out, as many strokes as the dialog counts, else the hour the
  clock shows. It plays out in time, not as the other effects ease into a state: its fade is linear over 24 s (`Fade`'s
  `span` and `linear`, `SLOW` in SceneFx.kt, `Strike`) and a cue back down, or the dialog closing, stops it at once (no
  strokes backwards). Each stroke rings the bell's sound as the painter swings it (`Sfx.fx` with the count the scene's
  place now carries).
- A count of the hour: `"count": {"from": "hour", "min": 1, "max": 12}` is the hour the clock has struck last, by the
  scene's hour (QA's "hour:" too), so a counting turn matches the strokes (`Counts.HOUR`).

### The villagers go to the places

Routine options that need a project's landmark (`project:<id>`, there once finished and while standing), by kind (all
cultures alike): the storyteller's and the grandfathers' afternoons at the bocce court (Stari Janez raking its sand in the
morning), the children at the playground most of all (the fountain on hot days, the trout under the cart bridge, the bee
meadow, the maypole), the grandmother's and the aunt's flowers at the wayside chapel and its candles at dusk, the shepherd's
flock over the cart bridge and the fire brigade's drill, the farmer's hay into the toplar (June to September, the field's
hay an errand there), the smith at the fire station, the fountain and the clock, the beekeeper in the bee meadow (April to
September), the winemaker at the bocce court's table, the innkeeper's guests up the lookout, the teacher on the
playground's bench. The map's people walk there; each place's scene shows whoever its day has there, at their spot.

The day's caches were keyed on the buildings alone: a project finished today changed nobody's day until tomorrow (the
mill's miller too). They are keyed on the standing projects as well (`Routine.placesOf`).

### The map's bridge in each culture's look

`MapLook.bridge` from the map's tables: Friuli's piers of river cobbles under a course of brick at the deck's corners (as
Al ruscello's), Carinthia's deck and railings of larch, the Lake District's packhorse bridge of slate as its project builds
it (the walls on both banks; the timber frame, its ribs on props; the arch humped over the beck, the water in its shade
under the round opening; then its low parapets). Primorska's and an unknown culture's are the map's own, pixel for pixel.

### Older apps

The new slots, spots, effects and the count are `SceneArt.VERSION` 5 (`SCENE_ARTS_VERSION`, `ADDED[5]`): for an app
that asks with `arts=4` or less the bridge leaves the things of the new slots out, stands the people of the new spots at the
play spots it had (`maypole` → play1, `clock` → play2, `fountain` → play3), and drops every variant counting the hour
(and its id from the happenings' lists); an older app ignores an effect it doesn't know.

## The content

Written by a helper agent of this round on a branch of its own and folded in after the host's reboot (its work was left
uncommitted; checked and fixed here). All machine-written, flagged in each file's `review` for the native check.

- **Every scene of the four arts gives the new slots a word** (the smoke wants every slot of an art worded in a Slovene
  scene, `CultureScenesTest` every slot once in In piazza, Dalle api and Il prato delle api), with the fixtures' exact
  `needs` and I spy clues from the picture as drawn (three or more, two at A1; the "where" clues held against the tap
  boxes):

  | Art | Slovene scenes | Word (pack) | Friuli scenes | Word (pack) |
  |---|---|---|---|---|
  | square | Na vasi, Pod mlajem, Pod uro, Pri vodnjaku | mlaj (pod-mlajem), vodnjak (pri-vodnjaku), ura (pod-uro) | In piazza, La cuccagna, La fontana, L'orologio | l'albero della cuccagna (la-cuccagna), la fontana (in-paese), l'orologio del campanile (l-orologio, new) |
  | stream | Ob potoku, Na mostu | most (na-mostu) | Al ruscello, Il ponte | il ponte (in-paese) |
  | field | Na njivi, Pri toplarju | toplar (pri-toplarju) | Nel campo, Il grande fienile | il grande fienile (il-grande-fienile, new) |
  | apiary | Pri čebelnjaku, Na čebeljem travniku | travnik (na-cebeljem-travniku) | Dalle api, Il prato delle api | il prato (festa-pasquetta) |

- **Look lines**: the project scenes look at their thing ("Poglej mlaj! Na vencu ima pisane trakove.", "Guarda il ponte
  nuovo", "Guarda i premi in cima all'albero!").
- **The standing spots**: Pod uro's Mojca under the clock and Ančka by the fountain; Pri vodnjaku's Tone at the fountain
  and France under the clock (never on at once with each other's alternate); Pod mlajem's Marko by the maypole; La
  cuccagna's Davide by the pole, La fontana's Bruno at the fountain, L'orologio's Elena and Bruno under the clock.
- **The clock strikes the hour**: Pod uro's Ančka, who hears badly, asks the learner to count the strokes ("Bil je en
  udarec / Bila sta dva udarca / Bili so trije udarci / Bilo je pet udarcev", the wrong forms 1 and 2 with their whys);
  L'orologio's Bruno has Teo count them ("Sono tre colpi!"). Each a variant of its happening, counting the hour, the
  strike on its first line.
- **The greasy pole's climb**: in La cuccagna a lad shins up and slides down while Davide and Lorenzo talk ("Guarda, quel
  ragazzo ci prova", "Oh, è scivolato giù!"), and Lorenzo looks at the prizes. Nejc's own climb in Pod mlajem has no climb
  cue (the picture's climber is a lad, not Nejc, who stands at his play spot).
- Fixed here: a reaction that said the right choice's word ("signor"), a "where" clue the picture didn't hold (the toplar
  "behind the wheat", far beyond it: "far off in the field" now).

The words for the native check: udarec in the counting forms, "Bilo je pet udarcev"; "Kakšno vedenje!"; travnik for the bee
meadow; il grande fienile, l'orologio del campanile, "Sono … colpi"; "Ma che maniere!"; the I spy clues' adjectives.

## How to see it, and the taps

| Journey | Before | After |
|---|---|---|
| The maypole, the fountain, the clock, the cart bridge, the toplar, the bee meadow in their scene: its word | not possible (scenery) | 1 tap on the thing (from the village: the landmark, "🚪 Vstopi", the thing: 3) |
| One of them as I spy's thing | never asked | as any thing of the scene |
| The clock striking the hour in Pod uro / L'orologio | not possible | the dialog (its happening's variant) |
| The map's bridge in Friuli, Carinthia, the Lake District | the Slovene timber bridge | its own, no tap |

Contact sheets (JVM renders, build/scene-snapshots/place-life/ and build/map-looks/04-bridge.png; copied to the round's
scratchpad, ux-place-life/): each new thing at every step of its project in both looks, finished by day, in the evening,
at night and in winter at every detail; the square with someone at every spot, a grown-up at a play spot, the climb from 0
to 1 and the strike stroke by stroke closer up, the greasy pole's steps and its raising; the bee meadow through the year
and at each of the bee garden's levels; Friuli's maize by the month; the map's bridge per culture at every step, at night
and in winter.

**On the emulator** (QA's step `place-life` through `companion/bin/qa-slot`, the dev bridge on synthetic learners: Ana,
Slovene from English, and Teo, Italian from German in the Friuli village; the village built up as a Trg and the six
projects finished through QA's hooks; shots in the round's scratchpad, `ux-place-life/qa-sl`, `qa-sl2`, `qa-fr`):

- Pod uro: «Vidim, vidim» with Zala at the clock spot, its first round the clock ("Je bela."), found by a tap ("Ja, to je
  ura! Bravo!"), then the clock tapped outside the game: "ura · clock". Ančka's strokes to count at three in the afternoon:
  "Pst, Ana! Ura na zvoniku bije. Preštej udarce …", the choices "Bili so trije udarca / udarcev / udarci" (the hour from the
  scene's clock), Ančka at the fountain spot.
- Pod mlajem: the maypole spied and found, its word "mlaj"; Pri vodnjaku: the fountain, "vodnjak"; Pri toplarju: the double
  hayrack, "toplar · double hayrack".
- La fontana: the fountain with its pine cone, found, "la fontana · Brunnen"; Il grande fienile: the big barn with its maize
  (October), found by Tommaso's round ("Sì, è il grande fienile! Bravo!"); Il ponte and Il prato delle api: their rounds
  spied the cart bridge and the meadow, and QA's tap at the middle of their boxes landed on the stream under the deck and on
  Aldo's sign standing in the meadow (a learner taps the deck or the flowers; the JVM test taps every thing at its pixels).
  L'orologio: Bruno's strokes, "Sono tre colpi!"; La cuccagna: Davide by the pole, the lad at its foot and then up it.
- Na mostu, Na čebeljem travniku, L'orologio and La cuccagna offered «Vidim, vidim» but no round started for these synthetic
  learners (`ISpy.rounds` found no thing there with two clues their grammar has reached: the offer shows before that is
  known, as before this round); the things themselves showed in the pictures as drawn.

No crash in either run.

## How it is tested

- `PlaceLifeRenderTest`: the renders above, and each new thing there to tap exactly when its fixture is (every step, both
  looks, every detail), the things drawn before draw the same pixels as a thing as they did as scenery, Friuli's look
  changes each, the climb at three heights, the strokes one by one (`Strike`), a grown-up at a play spot stands still, the
  standing spots clear of each other.
- `CultureLooksTest`: the default culture's digests hold, with the square's people at the spots the digests were taken
  with and the apiary before its meadow; `FriuliOutdoorLooksTest`'s digests take the cobble piers and the maize (looked at
  first). `SceneEffectsTest` looks at the strike while a stroke rings; `SceneWeatherTest` (the ribbons' sway is bounded by
  the crown, so the wind never moves the maypole's box), `SceneSightsRenderTest`, `PeopleClearTest`, `SceneDetailTest`,
  `ScenePaintersTest`, `StickersTest`, `BuildingLevelsTest`, `SceneWorldRenderTest` as for every art.
- `RoutineTest`: once the places are built their people go there (shares over two summer weeks), the work stays first,
  nobody goes to a place not built, and the playground's scene shows the children there at their spots.
- `MapBridgeLooksTest`: each culture's bridge its own from its pillars on, the default's the map's (with MapLooksTest's
  digests and BridgesTest unchanged).
- The bridge: its registry mirror (the smoke compares it with SceneArt.kt), and a smoke check of `forApp` for an app before
  arts 5.

## The checks

On the branch rebased onto main 18c3bd71 (map-culture, native-check-sl and places-it-arts merged; SCENES.md's culture-pack
paragraph and qa's step list kept both sides), the content folded in:

- The Android unit suite through lani-slot.sh: 3,497 tests in 441 classes, 2 skipped, none failed.
- The bridge: `tsc` clean; the smoke on ports 9150–9158 and 9168 (Drama 3's worker): 3,138 checks, all passed (with the content; the run before it
  failed on exactly the scenes that didn't word the new slots yet, and once on a timing check of the words' note under a
  load of 56); `tutor-export --check`: 88 files match.
- The Python tests: `companion/tests` 131 (11 skipped), `tests` 251, all pass.
- The contact sheets: the round's scratchpad, `ux-place-life/sheets/` (and every shot on its own in `ux-place-life/shots/`);
  the JVM renders are made again by `PlaceLifeRenderTest` and `MapBridgeLooksTest` into build/scene-snapshots/place-life and
  build/map-looks.

## What is left

- QA's `place-life` taps a spied thing at the middle of its box, which for the cart bridge and the bee meadow is something
  else's pixel (the stream under the deck, Aldo's sign): a tap point from the thing's own pixels would make it sure.
- «Vidim, vidim» is offered where no round can start for a beginner (before this round too): the offer could ask
  `ISpy.rounds` first.

- The native check of the new content (see the content's `review` notes).
- The Kärnten and Lake District villages have no square, stream, field or apiary scenes yet (a later wave): their looks of
  these arts come with them.
- places-it-arts' requests for the new arts (an `inn` slot at the bocce court, the Friuli playground's `fontanella`, an
  altar candle of the chapel's own, the cramâr's crassigne for the palisade's pedlar, NOI | VOI on Friuli's scoreboard)
  belong to those arts' owners, not to this round's four.
- Teo's new places (Al campo da bocce, Al parco giochi, Dai pompieri, …) now get their people by their days too (the routines
  are by kind, every culture alike); no QA run has looked at them there yet.
- The bee meadow is drawn once finished; its steps (mowed, sown) aren't drawn as they go.
