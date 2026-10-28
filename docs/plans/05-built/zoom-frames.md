# Built: the village map smooth closer up, panned and pinched

Jan, 2026-10-27, about Teo's tablet (Samsung Galaxy Tab S6: Snapdragon 855, Android 12, 2560 × 1600): "performance on
the tablet improved overall, but zoomed in it's still quite laggy, surprising, as I'd think it doesn't have to render as
much".

This note says why it was so, what changed, how it is measured and tested, and what is left. The rules as built are in
companion/GAME.md ("The land": "The map is drawn beside the screen"). The village's numbers before this round are in
docs/plans/05-built/coast-horizon.md ("The frame's cost").

## Why closer up was slower

Three things, all in `game/render/TownScene.kt` as it was:

- **The map was drawn on the main thread.** Its pictures were rendered in the draw phase of the screen's frame, about 12
  times a second, by the processor (the graphics chip only scales the finished bitmap). On Teo's tablet a picture took
  hundreds of milliseconds (about 400 ms before the ground was kept, 2026-10-26), and while it did, nothing else on the
  screen moved: the fingers' pan and pinch waited, then jumped.
- **Panning closer up drew the ground whole every frame.** From 1.5 × the fitted zoom the map shows a second picture at
  twice the detail (three times from 2.5 ×) of just what the camera sees: its lens moves with the camera by whole pixels,
  and the ground's kept picture (`GroundCache`) was the canvas's, so it was drawn whole again for every frame of a pan, the
  biggest part of a frame (the doc's 115 to 136 ms a frame on the emulator, panned at detail 3, against 22 at rest).
- **The closer picture was a canvas of the fitted view's size**, which covers the screen only at 2 × and 3 ×: at 1.5 × to
  2 × and 2.5 × to 3 × the screen's edges showed the coarser picture under it. And its noise (the grass's speckle, the
  trees' leaves) was in the canvas's pixels, not the land's, so it stayed on the screen while the land moved under it.

So a closer look didn't draw less: it drew as many pixels, with finer detail per pixel (roof tiles, faces, tufts), and a
gesture made every frame start again.

## Before: what a frame cost

Measured before anything changed (the branch's first commit, 2a0511d2: only QA's hooks added).

**The renderer's passes on the JVM** (`TownFrameProfileTest`, the tablet's 320 × 200 canvas, at night; the middle of 40
frames; a machine shared with other builds, so the numbers move by ±30 %):

| Village | At rest, detail 1 | 1.5 × (detail 2) | 2 × | 2.5 × (detail 3) | 3 × | Panned at 2 | Panned at 3 |
|---|---|---|---|---|---|---|---|
| Teo's camp (coast) | 1.8 ms | 2.1 | 2.2 | 2.1 | 1.8 | 7.5 (ground 5.2) | 6.3 (ground 4.6) |
| Teo's coast at Vas | 2.5 | 2.5 | 2.4 | 2.1 | 2.3 | 6.6 (ground 4.7) | 7.4 (ground 5.6) |
| The classic land at Vas | 2.9 | 2.6 | 2.6 | 1.7 | 1.6 | 5.3 (ground 3.3) | 5.0 (ground 3.2) |

At rest the ground is kept and a frame is the objects (trees, buildings, people: 0.5 to 1.3 ms), the backdrop, the
outline and the light; a pan pays the whole ground every frame, two thirds of it. Where a frame's time goes at rest
closer up (the sampler, Teo's coast at Vas, detail 3, by day): the linden's and the beeches' crowns 27 %, the outline
pass 18 %, the polygons of the buildings 8 %, the ground's detail 5 %; at night the light pass 24 %.

**On the emulator** (QA's `zoom-frames`, the qa build, Teo-like coast village at night, 6 s a look): at 2560 × 1600 with
the emulator on two host cores, a frame took 300 to 350 ms in the middle whatever the app did (the emulator's software
GPU putting 4 million pixels on the screen: gfxinfo's GPU part 200 to 300 ms), so these runs say nothing of the app's
own time. At 1280 × 800 / 160 dpi (the same 320 × 200 canvas and the same layout in dp, a quarter of the pixels to put on
the screen) on four host cores, the main thread's own work a frame (input to handing the frame over, gfxinfo framestats):

| Look | Map render (ms) | Main p50 | Main p90 | Main max | Main frames over 16 ms | Frames in 6 s |
|---|---|---|---|---|---|---|
| At home (detail 1) | 12.5 | 2.2 ms | 16.5 | 45.6 | 20 | 205 |
| 1.5 ×, at rest | 18.4 | 9.6 | 30.1 | 52.2 | 44 | 113 |
| 1.5 ×, panned | 18.9 | 9.1 | 31.3 | 92.3 | 72 | 193 |
| 2 ×, at rest | 9.8 | 1.7 | 13.6 | 33.6 | 17 | 228 |
| 2 ×, panned | 21.7 | 6.5 | 29.1 | 48.6 | 78 | 257 |
| Pinched (1 × to 3 ×) | 6.7 | 4.1 | 11.0 | 24.8 | 6 | 275 |

The map's render ran inside those frames, once a tick (12 a second): the frames that drew a picture took it all on the
main thread. On Teo's tablet the processor is about 3 to 4 times slower than this emulator's (the 2026-10-26 numbers: about
400 ms there against 96 here, drawn the same way), and every tick's frame waited the whole render: the lag Jan saw.

## What

- **A thread of its own** (`game/render/RenderThread.kt`, `TownPictures.kt`). The draw phase only asks for what the
  screen needs (the nominal picture every tick while it shows, else when what it shows changed; the closer look every tick,
  and at once when the camera moved past what was asked for) and lays the newest pictures through the camera with
  `drawImage` (nearest-neighbour, on the GPU). The render thread draws them into canvases of its own and hands each over
  in one of three bitmaps a layer (`PictureSlots`): never the one the screen drew last nor the one it draws next, so a
  frame never shows a picture half drawn over; its texture is made ahead (`prepareToDraw`). Asks that come while it renders
  wait as one (the newest frame, every layer asked for), so it never falls behind. The thread runs at the screen's own
  priority (a background one could keep it to the little cores). The first picture is drawn at once, so the village never
  opens empty. A failed picture is logged; a debug or qa build crashes then, as before, which QA looks for.
- **The renderer read from two threads** (`VillageRenderer`). A frame is drawn holding its lock, and what reads the last
  frame's pixels takes it too (a tap's `hitTest`, the sky's taps and TalkBack's list, `hitRects`): a tap waits for the
  picture being drawn, at most one. What the main thread reads every frame (the people's anchors for the bubbles and
  TalkBack's nodes, the wild animals, the lens, the sky's colour, the sign) is a copy made when a frame is done, read
  without waiting.
- **The closer look is a window of the whole picture** (`TownFrames.kt`). Its lens has no offset: the whole village at
  2 or 3 canvas pixels a nominal one, of which the canvas holds a window (`PixelCanvas.originX`, as the scenes' closer
  looks do) covering the screen, `k + 2` px more round it so its edges are drawn as the whole picture has them (the
  outline's pixel, a tuft or a stone from beyond), and `8 × k` more while the camera moves (the frames until the next
  picture). Its size is a multiple of 32 px; a canvas kept up to 64 px bigger serves, so a pinch doesn't make a canvas each
  frame. Everything the renderer draws pixel by pixel keeps to the canvas's window (the sky, the hills, the sea, the
  treeline, the ground and its detail, a damaged building's char, the light, the trees', the animals' and the palisade's
  culling); what falls over the screen rather than the land (the rain, the falling leaves, the fireworks, a bolt of
  lightning) falls over the screen's part of it (`Frame.screen`). Every window's pixels are the whole picture's there.
- **The ground in cells of the picture** (`GroundCache.kt`). The ground's kept picture is in cells of 64 × 64 canvas
  pixels of each lens's picture: a frame copies the cells it shows and draws their moving water anew; a cell not kept yet is
  drawn (the ground pass draws any rectangle: each pixel by its own place alone) and kept. A pan draws only the cells
  newly in view, a zoom within a detail level none. The cells are kept for the land, the age, the paths, the season, the
  weather and the sky's colour at the horizon where the ground shows it (the key, as before); a change draws them anew as
  they are shown. They keep under 24 MB (a phone's closest look holds every cell of its picture in about 13), the ones
  shown longest ago going first.
- **The trees' stamps** (`Stamps.kt`, `TreePainter`). A tree, a bush or a linden's crown is the same pixels every frame
  (each written, never read or blended): its first painting goes into a scratch canvas and what it wrote, row by row, is
  kept; the frames after write those pixels (with the frame's object id, clipped as each write would be). They are kept by
  what they are, where, at which detail and in which season, month and snow, under 1.6 million pixels (about 8 MB).
- **The Home header's village and the scenes** render on threads of their own the same way (`VillageScene`'s
  `StripPictures`; `ui/scene/ScenePictures.kt`: a scene's painters keep their last frame to hit-test, read holding the
  pictures' lock). A scene's closer look was drawn on the main thread as often as its cost allowed (at most a third of it);
  it is drawn every tick now, on its own thread.
- **A release build can be measured** (`<profileable android:shell="true">` in the manifest: Perfetto, simpleperf and the
  profiler from the shell, on Android 10 and later), and it carries a **Baseline Profile** (`app/src/main/baseline-prof.txt`:
  the village's and the scenes' rendering, the sky, the villagers' day, the village's screens), which R8 rewrites for its
  names: a phone compiles those ahead of time when the app is installed from a store, or once ProfileInstaller's profile is
  compiled in the background (a sideloaded APK).
- **QA measures it** (`companion/bin/qa --steps zoom-frames`; QA's hook "cam:zoom:<f>" and "cam:pinch:<s>",
  `ui/game/QaCamera.kt`): the display as Teo's tablet, at night (and by day), at home, at 1.5, 2.5 and 3 × the fitted zoom,
  each at rest and panned, then pinched; every frame's time and the main thread's part (gfxinfo framestats), the map's own
  render time (tag TownScene). `TownFrameProfileTest` (LANI_PROFILE=1) times the renderer's passes on the JVM, and samples
  where its time goes.

## After

**The renderer on the JVM**, the same scenes (the closer look as it is drawn now: a window of the picture covering the
screen and its apron, the trees stamped, the ground in cells):

| Village | At rest, detail 1 | 1.5 × | 2 × | 2.5 × | 3 × | Panned at 2 | Panned at 3 | Pinched at 3 |
|---|---|---|---|---|---|---|---|---|
| Teo's camp (coast) | 1.1 ms | 2.8 | 1.3 | 1.1 | 0.9 | 1.7 (ground 0.3) | 1.3 (ground 0.2) | 1.4 |
| Teo's coast at Vas | 1.6 | 2.5 | 1.4 | 1.4 | 1.1 | 1.9 (ground 0.3) | 1.5 (ground 0.2) | 1.6 |
| The classic land at Vas | 1.6 | 3.4 | 1.7 | 1.5 | 1.6 | 2.6 (ground 0.4) | 1.8 (ground 0.2) | 1.9 |

A pan costs what a frame at rest does (`TownWindowTest`: 1.3 to 1.7 ms panning against 1.0 to 1.3 at rest; it was up to
five times as much). At 1.5 × a frame draws more than before, by design: the window covers the whole screen (427 × 267
canvas pixels and its apron), where the old canvas (320 × 200) left a quarter of the screen to the coarser picture. The
trees' stamps take the objects from 0.5 to 1.3 ms to 0.1 to 0.9.

**On the emulator**, the same runs (1280 × 800, four host cores):

| Look | Map render (ms, its own thread) | Main p50 | Main p90 | Main max | Main frames over 16 ms | Frames in 6 s |
|---|---|---|---|---|---|---|
| At home (detail 1) | 5.6 | 1.2 ms | 2.7 | 14.2 | 0 (20) | 269 (205) |
| 1.5 ×, at rest | 14.4 | 1.2 | 3.0 | 10.2 | 0 (44) | 232 (113) |
| 1.5 ×, panned | 16.7 | 5.8 | 10.7 | 34.7 | 2 (72) | 201 (193) |
| 2 ×, at rest | 13.6 | 1.3 | 4.1 | 12.3 | 0 (17) | 217 (228) |
| 2 ×, panned | 11.0 | 5.4 | 10.9 | 28.6 | 7 (78) | 254 (257) |
| Pinched (1 × to 3 ×) | 8.5 | 4.3 | 9.1 | 28.4 | 4 (6) | 224 (275) |

(In brackets: before.) The main thread's work a frame is under 16 ms at the 90th percentile in every look now (it was
over it at every zoom but the pinch), and over 33 ms in at most two frames of 1,400 (a pan's at 1.5 ×: 34.7 ms; the pinch none). What is
left on it while the camera moves is Compose's own (the bubbles, TalkBack's nodes following the people, the HUD), 5 to
11 ms. The whole frame on this emulator stays at 67 to 88 ms in the middle, bound by its software GPU (gfxinfo's GPU part
46 to 65 ms at a quarter of the tablet's pixels), which the tablet doesn't have (4 to 11 ms there, measured before the
ground was kept). So the round's targets, honestly: the main thread's part of a frame is within 20 ms (p90 2.7 to 10.9
ms) at rest and panning at every zoom on the emulator, and a pinch has no frame over 33 ms of the main thread's; ≥ 50 fps
of whole frames can't be seen on this emulator, and the tablet hasn't been measured yet (see "What's left").

**What it looks like**: the same pictures at rest (`ClassicLandTest`'s digests, through lenses at details 2 and 3 too,
are unchanged; every window is the whole picture's pixels); at 1.5 × the screen's left and right quarters were the coarser
picture before (a seam where the finer one began, the palisade a different, chunkier shape), and are the finer one now
(screenshots: ux-perf-zoom/crop-edges.png, before on the left). The ground's and the trees' grain stays on the land as the
camera moves.

**Memory**: the ground's cells under 24 MB (a phone's closest look, every cell of its picture: 10.8 MB in 408 cells; the
tablet's picture at detail 3 has 150 cells, about 4 MB); the stamps under 1.6 million pixels (about 8 MB); three bitmaps a layer (the tablet's nominal
picture 0.8 MB, its closer look's up to about 2.4 MB) and up to three canvases of the closer look with their drawing
state (about 18 bytes a pixel: 8 MB on the tablet at most).

## How to measure

- **On the emulator, as Teo's tablet** (the steps' numbers below): a synthetic learner of the Friuli culture
  (`bun companion/bridge/test/fixtures/make-learner.ts <dir> --name Teo --target it --base de`, its village, a Vas of 11
  buildings, moved onto Teo's coast: `"land": {"kind": "coast", "seed": 551323397}`), the emulator pinned to four host
  cores (`taskset -a -p -c 2-5,14-17 <qemu pid>`; with two, its software GPU took every frame's time), its display as the
  tablet on its side at a quarter of the pixels (the same 320 × 200 canvas, the same layout in dp):

  ```bash
  LANI_DEV_DATA=<the learner's dir> LANI_CULTURE=friuli LANI_QA_PAIR=sl-en LANI_QA_ZOOM_SHOTS=<dir> \
    LANI_QA_FRAMES_S=6 LANI_QA_FRAMES_HOURS=22 LANI_QA_FRAMES_ZOOMS=1.5,2.5 LANI_QA_FRAMES_DISPLAY=1280x800/160 \
    companion/bin/qa --minified --no-town --steps zoom-frames
  ```

  About six minutes on the emulator. "2.5" settles at 2 × at this display's fitted scale of 4 (the crisp scales are
  multiples of the detail); the tablet's own (8) settles at 2.6 ×, detail 3.

  `--minified` is the qa build: the release build's R8 with QA's hooks, not debuggable (a debuggable build's code runs
  slower, and the tablet runs a release build).
- **On the tablet itself**: a release build since this round is profileable (companion/README.md, "Measure the map on a
  phone or a tablet": gfxinfo's framestats, a Perfetto trace with the threads TownRender, RenderThread and the main thread).
- **On the JVM**: `LANI_PROFILE=1 ./gradlew :app:testDebugUnitTest --tests '*TownFrameProfileTest'`: each pass's time on
  the tablet's canvas at rest, closer, closest, panned and pinched, the closer look as it was (a canvas through a moving
  lens) and as it is (a window of the picture), by day and at night; and where a frame's time goes, by method (a
  sampler of the test's own thread).

## Tests

- `TownWindowTest`: 100 windows of the closer picture (corners, the middle, small and big, at odd places, on the tablet's
  and a phone's canvases, at details 2 and 3: the coast's camp at noon, its village at night, the storm's rain, the
  classic land at an autumn dusk, a lake in the snow) the same pixels as the whole picture drawn at once, inside their
  apron; the window always covers the screen and its apron, inside the picture, its size on the grid, a few pixels more
  than the screen; 336 frames of a pan and a pinch at details 2 and 3 on four lands, the day turning, a building more,
  a new month, the cells the same as the ground drawn whole; a pan draws only the cells newly in view, coming back none,
  a building placed or a new month all of them, a building's upgrade none; a 2 MB cap kept, the same pixels; a phone's
  closest look's every cell in 10.8 MB of the 24; a pan costs about what a frame at rest does; 270 frames the same with
  the trees stamped and painted (every season, every detail, panning); the anchors, the animals and taps read while
  another thread draws.
- `TownPicturesTest` (Robolectric, native graphics): the first picture there at once, the nominal canvas's pixels; the
  closer look from the render thread covering the screen and its apron, its pixels a render of its window; the picture
  on the screen never drawn into while 38 asks come in a row, merged while it renders.
- Unchanged and green: `ClassicLandTest`'s digests (the classic land's pictures pixel for pixel, at details 2 and 3
  through a lens too), `GroundCacheTest` (1,302 frames the same kept and whole, now through the cells), `LodTest`,
  `LandmarksTest`, `CoastHorizonTest`, `SceneDetailTest` (every scene's window the whole picture's, the frame budget) and
  the other render and scene tests; `QaHooksTest`.

## What's left

- **Teo's tablet itself.** The emulator can't show whole frames (its GPU is software); the main thread's part is what
  this round changed, and it is small now. With wireless debugging on, a release build can be traced as it runs (README,
  "Measure the map on a phone or a tablet"): the frames' time, and whether the render thread keeps up at 12 pictures a
  second on the Snapdragon 855 (on the emulator a picture takes 5 to 17 ms; 3 to 4 times that is 20 to 70 ms there, under
  the 83 ms of a tick, so the village should still move 12 times a second closer up).
- **The main thread while the camera moves**: 5 to 11 ms a frame of Compose's own (the bubbles and TalkBack's nodes
  following the people, read through the camera each frame), in ui/game (the village screens' area). Worth a look on the
  tablet once it is measured.
- **The rest of a picture**: the outline and the light passes and the backdrop are per pixel every frame (a third of a
  frame closer up at night); the buildings are painted every frame (they flicker, smoke and flutter: not stampable as
  they are). A coast at dusk draws its ground anew every frame (the sea takes the sky's colour at the horizon, which moves
  by the minute), as before.
- **A fling faster than the margin** (about 2,400 screen px a second at 3 × on the tablet) shows the coarser picture at
  the screen's edge for a frame or two, until the next picture. No popping beyond that: a detail switch fades as before.
- **The scenes** render on their own thread now; their painters draw the whole picture clipped to the window each frame
  (no cells or stamps of their own: a vista's ground is the painter's own code).
