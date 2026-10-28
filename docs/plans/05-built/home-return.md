# Built: Home at once after Back

Jan, 2026-10-28, on their phone (Android 16, a recent build): "going back to the main screen freezes the app for a short
moment, then it works; consistently", and from the village map "it takes roughly 1–2 s until it reacts". Opening the
village was about instant; only coming back to Home was slow.

This note says why it was so, measured, what changed, how it is measured and tested, and what is left. The rule as built
is in companion/GAME.md ("Nothing is drawn on the main thread, the first picture neither"); the map's own drawing is
docs/plans/05-built/zoom-frames.md.

## Why Home froze after Back

Home and the village are composed anew each time the learner comes back to them: the back stack keeps their place, not
their composition. Measured on the emulator (the qa build, R8 as in a release; a synthetic learner grown to some weeks of
daily use: 1,800 cards with their review histories, 60 error patterns, 120 sessions, 2.5 MB of data), by a method trace
of Back from the village and by QA's watch on the main thread (below), these were on the main thread after the Back:

- **Home's header village drew its first picture on the main thread, with nothing kept.** `StripPictures` (then inside
  `VillageScene.kt`) made a new `VillageRenderer` in the composable, so its ground's cells, its trees' stamps and its land
  were gone, and `ask()` drew the first picture at once in the draw phase when there was none (55.6 ms of a debug build's
  Home frame; the whole first frame of Home 242.5 ms in the qa build). The village's map did the same when it opened
  (`TownPictures.ask()`, the same `if (base == null) draw(ask)`), and so did a scene's picture.
- **Every Back to Home read everything from the bridge again, and parsed it on the main thread.** `AppViewModel.shown`
  refreshes Home each time it is on view again: the culture, the family, the guests' questions, the linked towns, the
  clips' index, the learner's state, the modules, the grammar book, the cards, the course, the forms, the counting and
  the forms cards, the readings, the packs, the scenes and the people. Bridge's calls fetched on the IO thread but read
  their answers on the caller's, the main thread: the learner's state (`Dashboard.parse`) 269 ms, the scenes 266 ms, the
  course 101 ms, each a message of its own after Home's first frame (a debug build: the scenes 312, the state 285, the
  course 165, the people 87, the grammar book 52 ms; rendering the texts for the learner, `Learner.renderJson`, 442 ms in
  all). This was the freeze: Home showed, then didn't answer for most of a second.
- **Home's first frame sorted all the learner's words.** The learning tiles count them (`MyWords.of`), and the sort made
  each word's key anew in every comparison, a regular expression compiled for each letter: with 1,800 cards a debug build
  showed "Lani isn't responding" (the ANR's main thread in `MyWords.fold` under `Collections.sort`).
- **The garbage.** With the parsing moved off the main thread, every Back still made 95 to 150 MB for the collector (four
  or five concurrent collections of 20 to 40 MB on the emulator) and took the processor while Home was drawn; under a
  method trace each collection became a wait (159 ms in `WaitForGcToComplete`).
- **The same values compared field by field.** A new value equal to the last went into the screen's state through its
  equality check, on the main thread: the scenes' lists 67.6 ms in a debug build, the grammar book's 8.6 ms.
- **The reading corner checked each of the bridge's readings again** (`Cultures.check`: the culture pack's manifest read
  from the APK and parsed for each reading, a regular expression compiled for each text; 132 ms in a debug build), and
  **the forms cards' and counting cards' candidates** went over the whole deck (45 ms).

Not found: a join or a wait when the village is left (`RenderThread.close()` only marks the thread done; the renderer's
cells went to the collector), a `runBlocking` or a synchronous call to the bridge (the linked towns' `towns.load()` is
one of the reads above, its answer small), or a cost in restoring the saved state.

## What changed

- **No picture is drawn on the main thread, the first neither** (`game/render/RenderThread.kt`, `TownPictures.kt`,
  `StripPictures.kt`, `ui/scene/ScenePictures.kt`). `ask()` only posts to the view's render thread; the thread starts with
  an ask and ends after 2 s without one, so a view kept off the screen holds no thread. Until the first picture comes the
  canvas is the hour's sky (`Env.skyTop`), and the picture fades in over it in 220 ms (`Arrival` for the map and a scene,
  an `Animatable` for Home's header).
- **The pictures are kept for coming back** (`game/render/Retained.kt`): Home's header village by canvas size (two sizes:
  a phone turned), the learner's own village's renderer (`Retained.townRenderer()`, the village screen's: its map, its
  taps, its people's anchors) and its map's pictures by size, a visited town's map until another comes. A view on the
  screen is counted (`use`); under memory pressure (`onTrimMemory`, from `TRIM_MEMORY_RUNNING_LOW`, or the app in the
  background) the views not on the screen go whole and those on it let their renderers' kept ground cells, trees' stamps
  and canvases' contexts go (`VillageRenderer.trim()`). Coming back shows the last picture at once.
- **The bridge's answers are read off the main thread** (`Bridge.off`: every call but the download, on
  `Dispatchers.Default`), **and an answer the same as the last one isn't read again** (`Bridge.remembered`: the answer's
  bytes, their SHA-256; with the same learner, language pair and day the value read then comes back, the same instance:
  nothing parsed, nothing for the screen's state to compare). The state (`/state`, a language's too), the modules, the
  packs, the grammar book, the cards, the course, the readings, the scenes and the people.
- **Home's words**: each word's sort key made once (`MyWords.ordered`, the regular expressions compiled once), and the
  list kept for the same deck (`MyWords.of`).
- **The reading corner** does nothing for an answer that didn't change (`ReadingsController.reload`), the culture pack's
  manifest is read once (`Cultures.manifest`, until the learner changes), and the language code's expression compiled once.
- **The deck-wide candidates** (`FormsCard.candidates`, `Counting.candidates`, the words whose forms to ask) are made on
  `Dispatchers.Default` (the forms' cache is a snapshot state's immutable map).

## Before and after

**A method trace of Back from the village** (`am profile`, sampled every 0.2 ms; the qa build; the emulator's main
thread, its own work, not the wait for the screen's render thread):

| | Before (main, 5f92988e) | After |
|---|---|---|
| Home's first frame | 242.5 ms (with the header's first picture) | 62 ms (layout 33, drawing 35), in the first frame after the key |
| Then, in messages of their own | the state parsed 269.0 ms, the course 100.6, the scenes 265.8, and more | none over 30 ms |
| The main thread's work after the Back | about 1.1 s in the 3.5 s traced | 164 ms in 17 messages in the 3 s after the key |

The trace's sampling makes every garbage collection a wait (a setter "took" 295 ms in one run), so its sums are more
than the app's own; QA's watch below has no such waits.

**QA's watch on the main thread** (`home-return`: for the 3 s from the key or the tap, the main thread's messages
besides the screen's frames, which on the emulator also wait for its software graphics; two tries each; the "after" of
the village's first Back was traced, its second is shown, the traced one 61 ms with the longest 14):

| From | | Their work in 3 s | The longest | Over a frame (16.7 ms) | Over 100 ms | Collections (MB freed) |
|---|---|---|---|---|---|---|
| Back from the village | before | 338, 476 ms | 185, 118 ms | 4, 8 | 1, 2 | 4 (148), 4 (95) |
| | after | 31 ms | 8 ms | 0 | 0 | 3 (63) |
| Back from Settings | before | 396, 456 ms | 136, 136 ms | 5, 6 | 2, 2 | 4 (119), 5 (146) |
| | after | 111, 47 ms | 57, 22 ms | 2, 1 | 0, 0 | 2 (39), 4 (109) |
| Opening the village | before | 20, 20 ms | 3, 4 ms | 0 | 0 | 3 (75), 3 (70) |
| | after | 20, 17 ms | 4, 2 ms | 0 | 0 | 3 (65), 3 (64) |

Opening the village was quick before too (its first picture was drawn in its first frame, 11 to 22 ms there on the
emulator, now on its thread). The "before" is main as it was with only QA's watch added (a worktree of 5f92988e with
`MainWatch.kt` and its hook).

## How it is measured

```
LANI_DEV_DATA=<a synthetic learner's data> LANI_QA_HOME_SHOTS=<dir> LANI_QA_HOME_TRIES=2 [LANI_QA_TRACE=<dir>] \
  companion/bin/qa --steps home-return
```

`home-return` (named only) opens the village and Settings from Home and goes back, and opens the village again, each
LANI_QA_HOME_TRIES times (3). For 3 s from the key or the tap it asks nothing of the screen (uiautomator's dumps are the
main thread's work too), then reads QA's hook "main:report" (app/MainWatch.kt: the Looper's own logging times each
message of the main thread; "main:watch" turns it on), dumpsys gfxinfo framestats, Choreographer's skipped frames and the
runtime's collections from logcat. A table, also home-return.tsv. With LANI_QA_TRACE the first Back from the village is
also traced by method into back-village.trace (the profiler is started 1.5 s before the key: `am` returns before it is
on). The emulator's frames are 250 to 300 ms whatever the app does (its software graphics), so the frames' own times and
the watch's messages besides the frames are what say the app's cost.

## Tests

- `TownPicturesTest` (Robolectric, native graphics): nothing is drawn on the thread that asks, the first picture neither,
  for the map, Home's header village and a scene (`drewOn`, the pixels the same as drawn at once); the render thread ends
  when idle and starts again with an ask; `Retained` keeps the views and lets them go under memory pressure (a view on
  the screen keeps its picture, its renderer's cells go; one off the screen goes whole; `TRIM_MEMORY_UI_HIDDEN` keeps
  everything); what the screen drew is never drawn into.
- `BridgeAnswersTest`: the same answer gives back the value read then; a changed one, another learner, a 404 of an older
  bridge and a failure as before.
- `MyWordsTest`: two thousand words listed in a few milliseconds (7.4 ms on the JVM), in Slovene's order; the same deck
  gives back the same list, another its own.

## What is left

- **Home is still composed anew after every Back**: its first frame is 62 ms of the main thread on the emulator (a third
  of that on a phone, about two frames). Keeping Home's composition while another screen is over it would remove it, a
  change to the navigation itself.
- **Every Back still asks the bridge for everything again** (about 16 requests). Those answers aren't read again while
  they stay the same, but the clips' index (`Clips.refresh`: fetched, parsed and written on the IO thread each time,
  about 270 ms there on the emulator) and the days' audio plan (`OfflineAudioController.plan`, on `Dispatchers.Default`,
  at most once a minute) still make 40 to 110 MB for the collector per Back on the emulator; no wait for it showed on
  the main thread outside the profiler.
- `SkyCards.now()` is made in the draw phase once a minute (the 766 stars' places): a few milliseconds a minute, which
  could go to the render thread with the rest of the picture.
- Measured on the emulator only, not on Jan's phone.
