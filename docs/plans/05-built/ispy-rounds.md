# Built: I spy only where a round can start (ispy-rounds, 2026-10-31)

Jan, 2026-10-31: "ok do all" (the third batch of follow-ups, its part 4) and, for the round: "for choices - make it the
best - prod ready". place-life's emulator run saw «Vidim, vidim» offered in Na mostu, Na čebeljem travniku, L'orologio and
La cuccagna with no round starting, and QA's tap on a spied thing landing on something else (the stream under Il ponte's
deck, Aldo's sign in Il prato delle api's meadow). This note says what was found, what was built, how to see it, how it
is tested, and what is left. The rules as built are in companion/SCENES.md ("I spy").

## What was found

- **Every scene gives a beginner a round already.** Played as the app plays it (the scene's things in its language, its
  clue file read as `ISpyController` reads it, the child's lines, the clues a learner at A1 with nothing introduced gets:
  the pages above A1 are not yet, A1's always are), each of the 70 scenes (33 Slovene, 33 Friuli, 2 Kärnten, 2 Lakeland)
  gives a whole game of three rounds in every world it is seen in, at every hour and in every season. place-life's guess
  ("`ISpy.rounds` found no thing with two clues") was wrong: every thing of every scene has two A1 clues of its own
  (`ISpyContentTest`), and a Slovene word of one piece has two picture-free ones besides.
- **The four scenes' "no round started" was QA's.** With nobody "Here today" (Na mostu, Na čebeljem travniku, L'orologio,
  La cuccagna at the hour QA played them) the scene's panel is short and scrolls no further than to put the I spy card's
  "🔍 Igrajva · Let's play" at y 1966 px of 2400. QA's `scroll_to` only takes what lies above 1850 (clear of Home's tutor
  bar), so it took the card's title for the button, tapped it, and tapped nothing on its second try. The other scenes had
  people listed under the card, so the button scrolled higher. The before shots, `before/pl-*-ispy-not-started.png`, show
  the button on screen and the game not started.
- **An offer that can't start was still possible.** The offer asked for a child, games left and something in the picture,
  never for a round: a tutor's scene without a clue file in Italian, German or English got "Igrajva" and then "Zdaj tu ni
  ničesar za iskanje" (its words come with their article, "il ponte", so the picture-free clues gave one letter and no
  count: one clue, not two). The same for any picture whose things have too few clues for the learner.
- **A box's middle is often not the thing.** In the JVM renders of every art in every culture's look, by day and at night,
  a tap at the middle of a thing's hit area misses 847 of 4,377 things (another thing's pixel, or the scenery's).

## What was built

### Offered only where a round can start

- `ISpy.spyable`: the things a round can spy now, each as its round would be (in the picture, two clues to give: of the
  pages introduced and naming a thing in the picture, else the picture-free ones). `ISpy.rounds` picks from it and
  `ISpy.canStart` asks it.
- `ISpyController.offer` asks `canStart` with the same grammar gate the game's start uses (one `notYet`, each page asked
  once), after the cheap checks (a child, the games left, no wolves, no lesson). So neither the card on the words panel nor
  the 🔍 over the child shows where "Igrajva" would find nothing. QA's hook ("ispy:<scene>/<slot>") still offers it whatever
  the day, but never where no round can start. "Nothing to spy here right now" stays only for a picture that changed
  between the offer and the tap (a wild animal gone).
- I spy is offered nowhere else: not on Home, not in a villager's happening (a child with their own marker in the scene
  keeps it, the card on the panel offers the game), not in the guidance's tips. All of them go through the scene's offer.

### Picture-free clues a beginner can play in every language

- `ISpy.fallback` goes by the noun after its article (`ISpy.noun`: Italian il, lo, la, i, gli, le, l', un, uno, una, un';
  German der, die, das, ein, eine; English the, a, an): "il ponte" → "Inizia con la lettera P." and "Ha cinque lettere.",
  "l'uovo" → U and four letters, "der Brunnen" → B and seven. A noun of two words gives its own first letter ("kačji pastir"
  starts with K; before, the last word's: P).
- So a tutor's scene without a clue file is playable in every language, and even a learner with no page of the book at all
  gets a round in every curated scene (the test below).

### QA taps the thing's own pixels

- `TapPoints.of`: a pixel the thing painted itself (the painter's owners, to the pixel), the one with the most of its
  neighbours within 2 px its own too (inside it, away from its edges), of those the nearest to the middle of its area. A
  thing with no pixel of its own (the stars, a few sparks) gets a pixel nothing painted where its area is the front-most, as
  a tap falls back to the areas there.
- `SceneAnchors.point`: that pixel's middle on the view; the scene view reads it from the picture's last frame under the
  pictures' lock. The debug build's logs give it: I spy's things (tag `ISpy`) and a tap turn's places (tag `TapTurn`). A
  release build computes none of it.
- `companion/bin/qa`: `ispy_play` presses the card's button wherever it is on the screen (`scroll_to` takes a `bottom`
  now), else the 🔍 over the child, and tells "no offer" from "not started". `place-life` uses it and now fails when a
  place's thing isn't offered, no round starts or the tap isn't taken (before, it only printed them).

## How to see it

- In the app: a scene where no round can start (a tutor's scene with two-word things only, a picture changed by a dialog)
  shows no "🔍 Vidim, vidim" card and no 🔍 over the child. Everywhere else the game is as it was.
- QA: `LANI_DEV_DATA=<a synthetic learner> LANI_QA_PLACE_LIFE_ONLY=na-mostu,na-cebeljem-travniku companion/bin/qa-slot
  --no-town --steps place-life`, and the Friuli village's with `LANI_CULTURE=friuli` and an Italian learner
  (`make-learner.ts --target it --base de --name Teo`), `LANI_QA_PLACE_LIFE_ONLY=l-orologio,la-cuccagna,il-ponte,il-prato-delle-api`.

## Taps

| Journey | Before | After |
|---|---|---|
| A scene where no round can start: the I spy card | shown; "🔍 Igrajva" (1 tap) → "Nothing to spy here right now", a dead end | not shown: 0 taps, no dead end |
| A scene where a round starts: the game | "🔍 Igrajva" (1 tap), or the 🔍 over the child | the same |
| QA spying Il ponte's cart bridge, Il prato delle api's meadow | the tap at the area's middle took the stream, Aldo's sign ("Il ruscello? ✗") | the tap on its own pixels finds it |
| QA starting the game in Na mostu, Na čebeljem travniku, L'orologio, La cuccagna | the card's title tapped, no game | the button tapped, the round on |

## How it is tested

- `ISpyRoundsTest` (new): every scene file of companion/scenes and of each culture pack (read from the files, so the new
  Kärnten and Lakeland scenes are held to it as they come), as the app plays it for a learner at A1 with nothing introduced,
  in each world it is seen in (everything built, nothing built, the building it is in at levels 1 to 3), at dawn, morning,
  midday, afternoon, dusk and night, in January, April, July and October, on two villages' dice, wherever the picture shows
  something: a round can start, with a thing whose two clues are its own; with everything built, a whole game where three
  things show; and a round by the nouns' letters even with no page of the book. A scene's language must have the child's
  lines.
- `ISpyTest`: the picture-free clues by the noun (Italian, German, English, a two-word noun), `canStart` with and without a
  round, a tutor's Italian scene without a clue file playable, the rounds giving what `canStart` found.
- `TapPointsTest` (new): the deck with the stream through its middle tapped on its own pixels, inside them; the areas'
  fallback; in every art's picture in each culture's look, by day and at night, every thing's tap point is where a tap gets
  it (its own pixel where it has any); the cart bridge on its deck.
- `ISpyContentTest` as before (three clues a thing, two at A1).

## The emulator

QA's step `place-life` through `companion/bin/qa-slot` on this round's debug build, on synthetic learners made by
`make-learner.ts` (shots in the round's scratchpad, `ux-ispy-rounds/`; `before/` holds place-life's from its own run):

- **The Slovene village** (Ana, A1, Slovene from English; `LANI_QA_PLACE_LIFE_ONLY=na-mostu,na-cebeljem-travniku`, slot 2):
  in Na mostu Tine's "🔍 Igrajva" pressed low on the short panel, the first round the cart bridge ("Je širok."), found by one
  tap on its deck ("Most? · Ja, to je most! Bravo!"), then its word outside the game; Na čebeljem travniku the same with the
  meadow ("Poleti je pisan." · "Ja, to je travnik! Bravo!"). Before: `before/pl-sl-na-mostu-ispy-not-started.png`, the card
  on screen and no game.
- **The Friuli village** (Teo, Italian from German, the app in it · de; `LANI_CULTURE=friuli`, `LANI_QA_SETUP_PAIR=it-de`,
  `LANI_QA_PLACE_LIFE_ONLY=l-orologio,la-cuccagna,il-ponte,il-prato-delle-api`, slot 1): L'orologio's clock and La
  cuccagna's greasy pole spied and found (before: no game), Il ponte's cart bridge found by one tap ("È largo." · "Il ponte?
  · Sì, è il ponte! Bravo!"; before: "Il ruscello? ✗", `before/pl-fr-il-ponte-ispy-tapped.png`) and Il prato delle api's
  meadow ("È verde e pieno di fiori." · "Il prato? · Sì, è il prato! Bravo!"; before: Aldo's sign); then the clock's
  strokes and the climb as place-life has them.
- `place-life` passed in both runs ("spied: na-mostu/cartbridge, na-cebeljem-travniku/meadow" and "spied:
  l-orologio/clock, la-cuccagna/maypole, il-ponte/cartbridge, il-prato-delle-api/meadow"), and it now fails on any place
  not offered, not started or not found. No crash in either run.

## The checks

On the branch at main b3ff5280 (main hadn't moved when the round ended, so the rebase was a no-op):

- The Android unit suite through lani-slot.sh: 3,528 tests in 447 classes, 2 skipped, none failed (`ISpyRoundsTest` 3,
  `TapPointsTest` 4, `ISpyTest` 17, `ISpyContentTest` 5 among them).
- The bridge (untouched by this round): `tsc` clean; the smoke on ports 9270–9275 and 9288 (Drama 3's worker): 3,154 checks,
  all passed; `tutor-export --check`: 88 files match.
- The Python tests (QA's script changed): `companion/tests` 135 (11 skipped), `tests` 251, all pass.
- Emulator QA: `place-life` in both villages, above.

## What is left

- No new words or lines: the fallback uses the child's existing `letter` and `letters` lines, nothing for the native check.
- `DayAudioGather` still fetches the clues' voices of a scene where no round can start (it asks the games left and a child,
  not `canStart`); such a scene is rare (a tutor's without a clue file, whose letters are voiced when played anyway).
- The scene's `SceneAt` log (QA's people and things for other steps) still gives the areas, which those steps want.
