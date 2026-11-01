# Built: the map and the scenes take the culture from one source, and Friuli's art requests (culture-source, 2026-10-31)

Jan, 2026-10-31 ("ok do all"), the third batch of follow-ups. Two parts:

1. places-it-arts' QA (a Slovene course with `LANI_CULTURE=friuli`) showed the map's houses in a Primorska-like look
   while the scene inside was Friuli's. The map's `Frame.culture` and the scenes' culture must come from one source, the
   village's culture as the bridge serves it, for any course and culture pair and every way in, with a test that fails if
   they can disagree.
2. The art requests places-it-arts left (docs/plans/05-built/places-it-arts.md, "Requests for the art owners"): NOI | VOI
   on Friuli's bocce scoreboard, an `inn` slot for the osteria and its frasca, a Friuli `fontanella` slot at the
   playground, an altar-candle slot at the chapel, the cramâr's crassigne for the palisade's pedlar, and Friuli's chapel
   step that said shingles while its ancona is drawn under coppi. Each new slot worded in the Friuli scenes, with I spy
   clues. Primorska stays pixel-identical.

## Where the culture came from, before

Every picture of a village asked for its culture on its own:

| Way in | Before | Now (`game/culture/VillageCulture.kt`) |
|---|---|---|
| The village map (`TownScene`) and Home's header (`VillageScene`) | `Cultures.current.id`, its sea from `Cultures.current.world` | `VillageCulture.shown` and `VillageCulture.sea(…)` |
| The scenes (`SceneWorld.of`) | `Cultures.current.manifest.id`; the land the raw `state.land.kind` | `VillageCulture.shown.id`; the land as the map draws it (`VillageCulture.landscape`: an unknown land is the classic valley there and inside) |
| A scene opened before the village is read (`AppViewModel.sceneWorld`) | `SceneWorld.ALL`: always Primorska's look | `SceneWorld.shown`: the village's culture |
| A visit's map | `Cultures.visiting` (the host's pack, or the learner's own when this app can't play it) | the same, set through `VillageCulture.visiting` |
| A visit's places (`VisitSceneScreen`) | the town's raw `culture` string copied into the world: a culture this app doesn't bundle drew the default's look while the town's map outside drew the learner's own | `VisitLogic.world`: the culture of the town's map, whatever it is |
| The founding's preview | `vm.culture` (a copy of the id), its sea read again from the pack | `VillageCulture.home`, its sea as the map has it (`Founding.seaIn` through `VillageCulture.playable`) |
| The setup's preview | the first region of the language chosen, always | the same until a bridge has said a culture (`Cultures.said`), then the village's: setting up again (to pair anew) shows the village the learner has |
| The widget | `Cultures.home` | `VillageCulture.home` (during a visit too) |
| The charcoal pile's picture, the words' stickers | `Cultures.current` | `VillageCulture.shown` |

What could make them disagree, found on the way:

- **A visit to a town of a culture this app doesn't bundle** (a newer pack): the town's map showed the learner's own
  look, its places the default's. Both are the learner's own look now.
- **A scene opened before the village was read**: drawn in Primorska's look whatever the village. Now in the village's.
- **The land**: a land this app doesn't know drew the classic valley on the map and was passed raw to the scenes' look
  (the coast's touches decide by it). One answer now.
- **The setup again** after a bridge said Friuli for a Slovene course: the preview showed Primorska, the founding and the
  map Friuli. Now the village's.
- **`CultureSetting.profileSays`** didn't keep a Primorska the bridge said (it was the default already), so nothing knew a
  bridge had said it; it keeps it now, and `Cultures.said` says whether one has.

On the learner's own village the map and the scenes read the same global (`Cultures.current`) before too, for every
course: the course never enters into it (the bridge plays the culture's language whatever the course, the app the pack
the bridge says). The Primorska-like houses in places-it-arts' QA I could not make happen again in code or in the JVM:
Friuli's map houses are ochre, terracotta and cream plaster under coppi with green shutters, Primorska's white, ochre and
pink plaster under red tiles with their balconies; at Home's header size the two are close. Its likely
causes, a run disturbed by a dev bridge started on 8791 outside the slot lock that evening (followups.md), or the
cached Friuli scenes of a phone whose bridge then said Primorska, both end in the same rule now: whatever the phone
plays, map and scene play together. The QA step `culture-source` below shows a Slovene course in Friuli's village, map
and scenes alike.

## How it is held

`CultureSourceTest` (app/src/test/…/game/culture/):

- **The learner's own village**: for six courses (sl · en, it · de, de · sl, en · sl, it · sl, sl · it) and every culture a
  bridge can say (the four bundled and one this app doesn't have), on every land (and one it doesn't know), the map's frame
  (the culture and land `MapLook.of` is given, as `TownScene`, `VillageScene` and the widget build it) and every scene's
  world (`SceneWorld.of`, `Homes.world` of the kitchen and of the forest) have the very same `CultureLook` (the same
  instance: culture and land); the culture is the one the bridge said when the app can play it, else the default; a scene
  before the village is read is in it too; the widget's is the own village's.
- **A visit**: for every course, every own culture and every host culture (bundled, unknown, none), the town's map and its
  places (`VisitLogic.world`, the town read or not) agree; the widget stays the learner's own; home again, they agree again.
- **The previews**: the founding's land is in the village's culture and the village it founds is in it, map and scenes,
  its sea the map's; the setup shows the region of the language chosen until a bridge said a culture, the village's after.
- **The sources**: every `Frame(…)` in app/src/main takes its `culture =` from `VillageCulture` (or a `culture` parameter
  its callers fill from it: `LandPreview`), no scene world is built or copied with a culture of its own (`SceneWorld`
  itself included: its `of`, `shown` and the stickers' `showing` take it from `VillageCulture` or a parameter),
  `Cultures.visiting` is set only through `VillageCulture.visiting`, and the ten files that build a picture ask
  `VillageCulture`. A new picture that takes its culture from somewhere else fails it (tried: `SceneWorld.of` or
  `VillageScene` back on `Cultures.current`, a visit's places on the town's raw culture).

## Friuli's art requests

| Request | What was done | Files |
|---|---|---|
| The bocce scoreboard reads NOI \| VOI in Friuli | `FRIULI_BOCCE`'s `scoreboard` "noi": the slate 0.35 m wider on each side, the line under the headings lower, NOI and VOI each centred on its half in chalk pixels on the slate's plane (a stroke of one canvas pixel, two at detail 3; the N four strokes, the O three, the I one; on the scene's canvas the N three and the letters close), so they read closer up; the tallies spread over the wider slate. Primorska's MI \| VI were nominal blocks a fraction of a pixel apart, a white smudge at every detail; they stay as they were (its digests) | `BoccePainter`, `CultureLook.kt` |
| An `inn` slot for the osteria and its frasca | `bocce`'s new slot `inn`, a thing of Friuli's look only (`SceneFixtures.A_LOOK_ONLY`, `FRIULI_BOCCE`'s fixture): the osteria's walls, roof and chimney with the frasca over its door, one thing in two parts so the outlines are what they were; the barrel and crates stay scenery. In Primorska the gostilna stays scenery as before | `BoccePainter`, `SceneArt.kt`, `SceneWorld.kt`, `CultureLook.kt`, bridge `scenes.ts` |
| A Friuli `fontanella` slot at the playground | `playground`'s new slot `fontanella`, drawn only in Friuli's look | `PlaygroundPainter`, `SceneWorld.kt`, `CultureLook.kt` |
| An altar-candle slot at the chapel | `chapel`'s new slot `altarcandle`, a thing of Friuli's look only (`FRIULI_CHAPEL`'s fixture): the white candle on the altar apart from `candle` (the two red lumini on the step), lit with the third of `candles` as before, on a brass candlestick and a pixel of the picture wide (the Slovene stick is one canvas pixel at every detail, too thin for a child to find). In Primorska it stays one thing with the lumini, `sveča`, as before | `ChapelPainter`, `SceneArt.kt`, `SceneWorld.kt`, `CultureLook.kt` |
| The cramâr's crassigne for the palisade's pedlar | `FRIULI_PALISADE`'s `pedlar` "cramar": a walnut case from his hips up past his hat, its drawers in rows lighter along their tops with brass knobs once there are pixels for them, the lid's ledge, his jacket brown; also below Friuli's watchtower (`FRIULI_WATCHTOWER`), where Tommaso says «Ha una cassetta di legno sulla schiena!» | `PalisadePainter`, `WatchtowerPainter`, `CultureLook.kt` |
| The chapel step says shingles, the ancona has coppi | the content agrees with the picture (the Collio's anconas are under coppi): the step «Il tetto di coppi», «Sul tetto hanno messo i coppi, le tegole curve di terracotta» (Slovene korci, German Hohlziegel); a village that did the step keeps its chronicle line as it was said | `cultures/friuli/projects.json` |

**Things only a culture's look makes words.** The three new slots are things of Friuli's look alone: `SceneFixtures`
gives each `A_LOOK_ONLY` (a level no place reaches) and Friuli's tables `fixes` it (always there). So Primorska's pictures,
its scenes and Jan's words are what they were (the gostilna scenery, the altar's candle one with the lumini as `sveča`), and
a culture that comes later gets them only when its look says so. The bridge keeps a copy (`LOOK_ONLY` in scenes.ts; the
smoke compares it with SceneWorld.kt and CultureLook.kt and asks the curated Slovene scenes for every other slot);
`CultureScenesTest` asks a culture's scene for the slots its look draws, `PlacesRenderTest` Friuli's picture for those on
top of Primorska's. They are `SceneArt.VERSION` 6 (bridge `SCENE_ARTS_VERSION` 6, `ADDED[6]`): an app of version 5 gets
the three scenes without those things (`forApp`), an older app nothing new.

**Worded in the Friuli scenes, with I spy clues** (all machine-written, flagged in each file's `review`):

| Scene | Slot | Word | Clues |
|---|---|---|---|
| `al-campo-da-bocce` | `inn` | l'osteria (`in-paese`'s, one card with the starter pack's) | È gialla. · È grande. · Sopra la porta ha un ramo. · È dietro il pergolato. · Lì la signora Marisa cucina per tutti. |
| `al-parco-giochi` | `fontanella` | la fontanella, new in `al-parco-giochi` (sl pitnik, en drinking fountain, de Trinkbrunnen; 13 words) | È verde. · È alta e stretta. · È di ferro. · È vicino allo steccato. · Lì beviamo quando abbiamo sete. |
| `alla-cappelletta` | `altarcandle` | la candela (`festa-natale`'s, one card) | È bianca. · È piccola e sottile. · È di cera. · È sull'altare. · È dietro la grata. |

Every clue is at A1 (aggettivi-accordo, essere-avere, dove-preposizioni, preposizioni-articolate, presente-regolare), so
a beginner gets a round on each. The German clues of the fontanella say «Das ist …» (der Trinkbrunnen against the Italian
feminine, which the I spy test's pronoun rule would refuse as «Er»). The chapel's candle line («Adesso accendi la
candela, per favore. Quella bianca, sull'altare.») now names a thing of the scene; its right reply lights all three.

**Deploying.** The content (the scenes naming `inn`, `fontanella` and `altarcandle`, commit "content(friuli): the inn,
the fontanella and the altar candle worded …") needs the bridge of arts version 6 first: an older bridge doesn't know the
slots and would skip those three Friuli scenes until restarted. The code commits go out first (bridge restarted, the app
released), then the content. An app of version 5 or older gets the three scenes without the new things (`forApp`).

## Contact sheets

JVM renders, `FriuliRequestsLooksTest`, in build/scene-snapshots/culture-source/ and copied to the round's scratchpad
`ux-culture-source/`: `bocce.png` (Primorska and Friuli by day, in the evening, in winter, the inn highlighted, the
scoreboard at each detail), `playground.png` (the fontanella by day, in winter, highlighted, at each detail),
`chapel.png` (the candles out, one, all, the altar candle highlighted at each detail), `pedlar.png` (the krošnjar and the
cramâr on the road to the gate and below the watchtower, closer at detail 2 and 3).

## Tap counts

| Journey (Friuli's village) | Before | After |
|---|---|---|
| The village → the osteria's word (l'osteria) | none: the inn was scenery | 3: the bocce court's landmark, "🚪 Vstopi", the osteria |
| The village → the fontanella's word | none: drawn, not a thing | 3: the playground's landmark, "🚪 Vstopi", the fontanella |
| The village → the altar candle's word (la candela) | none: one thing with the lumini, «il lumino» | 3: the chapel's landmark, "🚪 Vstopi", the candle on the altar |
| «Vedo, vedo» on one of them | none | 3 to the offer, as at any thing |
| The village map and a scene, in any course | 2 (unchanged) | 2: a building or landmark, "🚪 Vstopi" |

Primorska's journeys are as they were (no new thing there).

## How it was checked

- **The culture source**: `CultureSourceTest` (4 tests). Its source scan was tried against three breaks put back
  after: `VillageScene` on `Cultures.current.id`, `SceneWorld.of` on `Cultures.current.manifest.id`, a visit's places on
  the town's raw culture; it named all three.
- **The arts**: `CultureLooksTest` (Primorska's pictures pixel for pixel: every art's digests), `FriuliRequestsLooksTest`
  (5), `PlacesRenderTest`, `ScenePaintersTest`, `CultureScenesTest`, `ScenesContentTest`, `ISpyContentTest`,
  `WordStickersTest`, `SceneWorldTest`: green.
- **The Android unit suite** (lani-slot.sh): 3,528 tests in 447 classes, 0 failures, 2 skipped.
- **The bridge**: `tsc` clean; the smoke on ports 9290–9308 (`LANI_SMOKE_DRAMA3_PORT=9308`): 3,156 checks, all passed; `tutor-export
  --check`: 88 files match.
- **Python** (bin/qa changed): 251 tests and 135 (11 skipped), all passing.
- **On the emulator** (qa-slot, the new step `culture-source`: a synthetic Slovene learner, `make-learner.ts`, with
  `LANI_CULTURE=friuli`, the phone in sl · en; screenshots in the round's scratchpad `ux-culture-source/qa-run1/` and
  `qa-run2/`, a sheet of the first in `qa-run1-sheet.png`): Home after the setup and the village map in Friuli's look (its
  stone huts, the houses' plaster under coppi, the market's blue and gold awnings, the vera), the bocce court's landmark,
  its card's way in and inside in the same look («Na balinišču · At the bocce court», 16 things; NOI \| VOI legible on the
  slate once the camera comes closer), the osteria tapped (its card: «l'osteria · inn, tavern», a new find), the
  playground's fontanella tapped («la fontanella · drinking fountain»), the candle on the altar tapped («la candela ·
  candle»), the cramâr coming up the road to the gate in Tommaso's dialog. The step passes (`qa-run3/`, the whole run;
  `qa-run1/` the first, whose tap on the candle missed: QA looked for a clear grid point in an area some 30 px wide, found
  none, zoomed and swiped the chapel out of view; a small thing is tapped on its area's middle now). The cramâr is small
  on a phone's screen at that distance; `pedlar.png` shows his crassigne closer. The run's APK is the branch's of 10:03,
  before the osteria and the altar candle became things of Friuli's look only, which changes nothing in Friuli.
- **Before**: the places-it-arts QA shots of the Friuli places are gone with /tmp; Primorska's pictures in the contact
  sheets are the arts as they were (Friuli's looked the same but for the ochre osteria, the fontanella, the ancona and
  the land), and an app of arts version 5 gets the scenes as before (`forApp`, the smoke).

## For the native speaker

- **Italian**: «Il tetto di coppi», «i coppi, le tegole curve di terracotta»; the clues above, «È gialla» for an ochre
  osteria, «Sopra la porta ha un ramo» for the frasca, «È alta e stretta» for the fontanella, «È piccola e sottile» for
  the candle.
- **Slovene**: «Streha iz korcev», «korce, upognjene opečnate strešnike» (korec, the Primorska word for the coppo:
  check against fran.si), pitnik for la fontanella.
- **German**: «Ein Dach aus Hohlziegeln», «die gebogenen Hohlziegel aus Ton», Trinkbrunnen.

## What is left

- **The pedlar sprite** (`People.kt`'s `pedlar`, the krošnjar of the day's surprise at the road) still carries the
  krošnja in every culture; a Friuli cramâr would need the people sprites to know the culture.
- **The mill's roof step** in Friuli's projects says scandole too («I ragazzi hanno coperto il tetto con le scandole»);
  the stream's mill is drawn with shingles in every culture, so it agrees with its picture today.
- **A tutor's scene** that names the osteria, the fontanella or the altar candle in a culture other than Friuli gets a
  thing that is never drawn: the app leaves it out of the scene (`inWorld`); the words panel would list it as waiting
  for a level no place reaches. ~~The bridge doesn't refuse it yet~~ Done (polish-app, 2026-11-01): the bridge refuses it
  by the village's culture (`lookOnlyErrors` over `LOOK_ONLY`, in `publish_scene` and `publish_dialog_variant`).
- **The Slovene scenes** could name their gostilna and the candle on the altar too (`v-vasi`'s gostilna is a word
  already): Primorska's look would fix `inn` and `altarcandle`, its painter drawing them as things there, and
  `na-baliniscu` and `pri-kapelici` would word them with Slovene I spy clues (a word apart from the lumini's `sveča`).
