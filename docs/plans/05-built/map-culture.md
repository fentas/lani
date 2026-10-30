# Built: the village map in each culture's look (2026-10-30)

Jan, from Teo's Friuli map: its watchtower flew Slovenia's tricolour, its field's hayrack was a Slovene kozolec, the
tent's flag and the feasts' garlands were Slovenia's, and the Friends card showed 🇩🇪 for a Carinthian town. The scenes
had their culture looks (docs/plans/05-built/culture-looks-friuli-indoors.md, looks-friuli-out.md, looks-alps-lakes.md);
the village map (game/render/) drew Slovene buildings in every culture. This note says what the map draws in each
culture now, how it is built, how to see it, how it is tested, and what is left. The rules as built are in
companion/GAME.md ("The map in a culture's look", under "Culture packs").

## What each culture's map draws

The table in GAME.md ("The map in a culture's look") lists every building and decoration per culture. In short:

- **Friuli** (Teo's): ochre, terracotta and cream houses under coppi with green shutters (blue by the sea), windows in
  pale stone frames, the vine's pergola over the portal, the fogolâr's chimney under its little roof; the church's gabled
  front with its rose window and the free-standing brick campanile; stone huts, by the sea the casoni of the lagoon under
  their reed thatch; the fienile of Nel campo for the kozolec; la vera da pozzo under its iron arch for the well (as In
  piazza); plain painted hives, in a shed of cobbles from level 2 (as Dalle api); the smithy of river cobbles with bands
  of brick (as Nella fucina); the school with the tricolore over its door; Friuli's golden eagle on blue on the
  watchtower (as Sulla torre), its pillars of cobbles and brick from level 3, and on the tent (as Nella tenda); the
  feasts in Friuli's gold, blue and white (as Dalle api's bunting), the market's awnings as Al mercato's; the greasy pole
  (l'albero della cuccagna) with its prizes; the Collio's vines without the klopotec; the town clock in the campanile; the
  wayside chapel an ancona in ochre, the fire station of cobbles, Friuli's eagle on the lookout tower (as their places'
  scenes).
- **Carinthia**: white houses with a timbered upper storey, the balcony with geraniums, dark shingles and the half-hipped
  gable (Krüppelwalm); the onion-domed church; the Harpfe (as on Am See's far shore); the log trough with its running
  spout; Austria's red, white and red on the watchtower, the tent (as Am Feuer) and the feasts; the Karawanken with the
  Mittagskogel at the horizon (as Am See); the orchard of old apple and pear trees; the Stockschießbahn.
- **The Lake District**: grey stone and whitewashed cottages under slate, white window frames, no shutters, a slate porch,
  the round chimneys at both gable ends; the battlemented church tower; stone huts; a stone field barn for the hay barn;
  the village pump on its trough; the oak on the green; white WBC hives; the school of stone with its bellcote; St
  George's cross on the watchtower and the tent (as By the fire), the bunting red, white and blue; the fells at the
  horizon (as On the fell); the maypole with its ribbons; damsons on the terraces; a stone bank barn; the bowling green;
  the church clock on its battlemented tower; the viewing station with its coloured windows.
- **Primorska**: as before, pixel for pixel.

The Friends card (and the town's progress page, the visit's header) flies the town's culture's flag, the one its
watchtower flies: 🇦🇹 for a Carinthian village (🇩🇪 before), England's for the Lake District's (🇬🇧 before); 🇸🇮 and 🇮🇹
as before. A culture this app has no pack of shows its language's flag.

## How it is built

- **The hook** (`game/render/MapLook.kt`): `MapLook.of(culture, landscape)`, read once per culture and land, gives the
  renderer typed fields. Two sources, so the map and the scenes agree:
  - the village as the fire's scene builds it, `FireStyle` (game/render/scene/FireVillage.kt): the houses' walls, roofs,
    shutters, timber, balcony, stone; the church's tower; the huts' stone or thatch; the oak. The same style paints the
    village round the fire in Am Feuer, By the fire and Al fuoco;
  - the `map` art of the culture's `CultureLooks` table (game/scene/CultureLook.kt: `FRIULI_MAP`, `KAERNTEN_MAP`,
    `LAKELAND_MAP`), beside the scenes' tables: `hayrack`, `well`, `hives`, `smithy`, `school`, `flag`, `pennant`,
    `festive`, `range`, `maypole`, `vineyard`, `toplar`, `court`, `clock`. Data, no culture ids in the painters.
- **The frame** says whose village it is (`Frame.culture`): the learner's (`Cultures.current`, on a visit the host's),
  the founding's and the setup's preview the culture chosen there, the widget the learner's own. The renderer sets
  `SceneCtx.look` once a frame.
- **The sprites**: `BuildingPainter.draw` asks `CultureBuildings` (game/render/BuildingLooks.kt) first; it draws a
  culture's own version where the look has one and says no otherwise, so the map's own sprites are untouched. The flags
  (`Flag`: its colour at each cell of a flag of any size), the feasts' colours (`Festive`: bunting, ribbons, banner,
  stalls, awnings), the mountains (`Backdrop.mountains` with the Karawanken's peaks, `fells`), the landmarks
  (`LandmarkPainter`'s variants) and the oak's crown (`TreePainter.oak`) read the same look. Shared: the culture's roof
  (`BuildingPainter.villageRoof`: coppi with their channels down the slope at every detail, shingles, slate, thatch),
  stone and plaster walls with their texture closer up, windows with the culture's shutters and frames, the onion dome
  and the battlemented tower (`TowerTops`, for the church and the town clock).
- Every building keeps its plot, its tap, its height (within `spriteHeight`), its lit windows and dark ones while
  everyone sleeps, its hearth's smoke and the forge's glow.

## How to see it

- The contact sheets: `./gradlew :app:testDebugUnitTest --tests '*MapLooksTest*'`, then `app/build/map-looks/`:
  `00-<culture>-ages.png` (each culture's map at every age, by day, at dusk in October, at night, in winter),
  `00-before-after.png` (the default culture's map, which every culture drew before, beside each culture's: a hamlet, a
  village, a town), `01-buildings-<day|night|winter|autumn>.png` (each building closer up, a row per culture),
  `02-feasts.png` (a celebration's decorations by day and at night, the feast's garlands), `03-landmarks.png` (the town's
  landmarks closer up in May and in September). Copied for the review to the round's scratchpad, `ux-map-culture/`.
- In the app: any village of Friuli, Carinthia or the Lake District; a visit to such a town (the host's look). QA:
  `companion/bin/qa --steps map-looks` (only when named) on a synthetic learner of the culture: the village as a hamlet
  and as a town, by day and at night, at rest and closer; `--steps friends,visit` with
  `LANI_DEV_TOWN_FIXTURE=dev-town-kaernten.json` for the Carinthian town's card and its map on a visit.

## On the emulator

Two runs of the debug build on the shared machine (2026-10-30, the first emulator, `ANDROID_SERIAL=emulator-5554`, inside
the emulator's lock), screenshots in the round's scratchpad `ux-map-culture/emulator/`:

- `--steps map-looks --no-town` on a synthetic Italian learner of the Friuli culture founded at Teo's land
  (`LANI_CULTURE=friuli LANI_LANDSCAPE=coast LANI_QA_FOUND_SEED=551323397 LANI_QA_PAIR=sl-en`): the hamlet and the town by
  day and at night, at rest and closer (`map-friuli-*.png`): the plastered houses under coppi, the campanile, the vera,
  the watchtower on its pillars with Friuli's flag, the tent's pennant, the tricolore over the school. setup, map-looks and
  the crash check passed; the map's render 82 to 122 ms a frame at details 1 to 3 (the emulator's software rendering under
  a load average near 40).
- `--steps visit` with `LANI_DEV_TOWN_FIXTURE=dev-town-kaernten.json` on a synthetic Slovene learner: the Friends card
  of Probedorf with 🇦🇹 (`kaernten-friends-card.png`; 🇩🇪 before), the visit's header with 🇦🇹 and the Carinthian village
  under the Karawanken and the Mittagskogel (`kaernten-visit-map.png`), Am See (`19-`, `21-visit-scene.png`). Every
  visit step passed but `visit-home`, which still waits for the village where "⌂ Home" now leads back to Friends (the
  infra part of this round fixes the step).

The "before" of every culture is the default culture's map, which every culture drew until now: the left column of
`00-before-after.png`.

## Tests

`MapLooksTest` (app/src/test/.../game/render):

- the default culture's map, named or not, and an unknown culture's are pixel for pixel the map as it always was (and
  `ClassicLandTest`'s digests, taken before generated land, still hold);
- each culture's map draws its own village;
- the map shows what the scenes show: each `map` entry that a scene has too is the scene's (`field.kozolec`,
  `tent.pennant`, `smithy.walls`, `school.flag`, `watchtower.flag` and `.tower`, `square.well`, `apiary.house` and
  `.bunting`, `firestation.walls`, `chapel.chapel`, `alps.range`, `campfire.flag`), the houses' style is the fire's village's, and the Friends card's flag is
  the watchtower's;
- every building of every culture's map is drawn and found by a tap at every age, at the nominal detail by day and
  closer up at night;
- a frame of a culture's map costs no more than the default culture's (a town at night on a phone upright, each
  culture's best of five rounds taken in turn: 8.7 ms in Friuli, 9.0 in Carinthia, 8.9 in the Lake District, 10.1 in
  Primorska, in the full suite's run on the shared machine; the windows of the upper floors glow without a light of their
  own, so a culture's many windows don't fill the frame's lights);
- the contact sheets.

The whole Android unit suite after the rebase onto main: 3441 tests, none failed (2 skipped). The Python tests (251 and
102, 11 skipped), the bridge's typecheck, its smoke (ports 9060, 9063, 9066) and `tutor-export --check` (87 files) pass;
nothing of the bridge changed.

## Tap counts

No journey changes: the map's pictures do, not its places or its taps (a building's card: one tap, as before).

## What's left

- The bridge over the stream is the map's timber bridge in every culture; the Lake District's project says a packhorse
  bridge of slate with one round arch (its steps, the walks over it and `BridgesTest` would change with it).
- The people's clothes (the kerchiefs, the hats) are the map's own in every culture.
- The machine's choices of what each culture builds want a look by someone who knows the Collio, the Gailtal and the
  Lakes (the forms are from the scenes' looks, which were checked against their content).
