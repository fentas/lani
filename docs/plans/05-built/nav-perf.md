# Built: Back is instant and cheap (nav-perf, 2026-10-30)

The follow-ups' first part (Jan, 2026-10-30: "sure do follow ups"; scratchpad followups-2.md, section 1). The
home-return round (docs/plans/05-built/home-return.md) took the freeze out of Back to Home, and left two costs: Home was
composed anew after every Back, and every Back read everything from the node again. This round removes both, checks
the back stack of a scene gone into from another, brings the kitchen's closer frame under the shared budget again and
takes the last two wall-clock checks off the wall clock. Measured first, changed second: the numbers before and after
are below.

## What

### Home kept under the screens opened from it

- **Home stays composed** while another screen is on top of it (`ui/Covered.kt`: `ScreenStack`, `Covered`; used by
  `MainActivity.App`). Back to Home shows it as it was, nothing composed again. Every other screen below the top is its
  remembered state only, as before (the village, a scene, Napredek: composed again when Back returns to them).
- **Covered, it is out of the way:** neither measured nor placed (nothing drawn, nothing to touch), no semantics
  (TalkBack and QA's uiautomator dumps don't see it), and its Back handlers are off: a disabled child of the navigation
  events' dispatcher (`rememberNavigationEventDispatcherOwner(enabled = false)`, which BackHandler uses since
  activity 1.12) and an idle OnBackPressedDispatcher for the older path. A covered Home's BackHandler never takes Back
  from the screen on top (`ScreenStackTest`).
- **What would run by itself in it waits** (`LocalCovered`): the header village's frame clock (`VillageScene`: it asked
  for every frame; on again from where it stopped, so the construction's and the celebration's marks hold, and a
  celebration while covered counts as seen), the tickers (`rememberNow`), the streak's pulse (an infinite animation), a
  tip's being worked out and counted as shown (`rememberTip`: what Home showed stays, `tipShown` and `tipsBegin` only on
  view), and Home's sheets (a sheet is a window of its own: it would stay over the screen on top).
- **Shown again, as it was first** (`rememberAway`: covered, and for 300 ms of frames after it shows again): the
  tickers and the tip's facts wait that moment, so the Back's frame only shows Home and their work comes in a frame of
  its own (the first after-run: up to 57 ms of composing in the Back's frame, the minute's clocks and the tip worked out
  the moment Home was uncovered).
- **What Home works out by the clock** (the festival soon, the burner at his pile now, what's on in the village, the
  greeting's hour, a threat's time left) goes by when Home showed (`rememberShownAt`: the first composition's time, then
  a moment after each time it shows again from under another screen, steady while it stays on view): as fresh after a
  Back as when Home was composed anew, and no work while it stays on view. A clock of the minute did it in between, and
  recomposed all of Home once a minute (round 2 below).
- **Behind the back gesture**, from a screen right over Home, Home itself shows under the shrinking screen (placed and
  drawn, still covered for the rest), dimmed by the scrim, the pill naming it as before.
- Kept as they were: the stack's saved state and its restoring after the process's death (Home's remembered state is
  provided while it is composed, `SaveableStateHolder`), the links and notifications (`nav.reset` keeps Home's entry),
  the guidance's actions (a tip's button on Home opens its screen over Home), the tutor bar and the chat over Home.

### Back reads only what changed

- **The bridge's version of what it serves** (`GET /changes`, `companion/bridge/src/changes.ts`): a digest of this run
  of the bridge (a restart is new code or settings), the day, and every file the app's reads come from, by inode, size
  and time of change: the learner's data directory (the six databases, `app/`'s stores, the village, the tutor's
  content there; not the data repository's history, the backups, the clips, the releases, the chat's photos, nor the
  bridge's own bookkeeping: the events kept for the app, the outbox's client ids, the phones' last visits, the
  developer's uploads), and on a node the project's content under `companion/` (not its code: android, bridge, bin,
  relay, the workers) and any content directory set apart from it (`LANI_SCENES_DIR` …). So everything that writes what
  the app reads moves it: the app's writes through the bridge, the tutor's tools, a session's `update-db.py`,
  `card.py`'s fixes, a merge on main under the running bridge. Not the events: the app handles each as it comes, and
  what one says changed is written to a file the walk sees. Nor the voice store: each clip made says so
  (`voice_updated`), and the app reads its clips' index again then; with it in the version, a node voicing in the
  background (QA's dev bridge, as the first after-run showed) moved the version before every Back. A walk is a stat of
  each file: about 900 on a node, 3 to 6 ms (the answers it saves: about 1.1 s of the bridge's work). On the phone's
  own bridge (the hosted mode) the content came with the APK and isn't walked (`Platform.contentChanges = false`).
- **The app reads everything again only when that moved** (`AppViewModel.reload(onlyIfChanged)`, `app/Freshness.kt`
  `Fresh`): Home and the packs coming back on view ask `/changes` first; the same version, day, time of day (the
  people's whereabouts are asked by it), language pair, learner and culture as the last whole reading, no more than ten
  minutes later, is nothing more to read. Otherwise the whole reading runs as before and remembers what it read for. A
  reading that failed part way remembers nothing (the next Back reads again); a bridge without the route (`404`) or none
  reachable: everything read as before. The ten minutes are for what no version says: a linked town's news (`/towns`
  asks every linked town now) and a clock the bridge doesn't count by. The events (a module, a pack, a scene, a reading
  published, words added, a guest) and the app's own writes (`onSaved`) read again at once, as before.
- **Conditional reads** (`changes.ts` `conditional`, `Bridge.answer`): every GET answered with JSON carries its ETag
  (the SHA-1 of its bytes, after the rendering for the learner); the app asks again with it (`If-None-Match`) while the
  learner, pair and day are the same, and the same answer is a `304` without a body: nothing sent, nothing read or
  digested, the value read then. So a reading after a change (a run's result, the tutor's page) brings only what
  changed: the scenes' 2.9 MB, the course's 0.8 MB and the grammar book's 0.4 MB stay on the node when they didn't.
- **The clips' index** (`Clips.refresh`) the same: asked with its ETag, a `304` costs nothing (before: fetched, parsed,
  written to the disk and swapped with the earlier clips every Back). The scenes and the people aren't written to the
  disk again when the answer is the very one the phone has.

### The mill gone into from the stream

The back stack already holds the stream under the mill gone into from it: mill-scene built it (`003d23b4`, 2026-10-27:
`Screen.Scene.from`, its own destination in `Nav.kt`, kept in its route across the process's death, `openScene(from =
…)` from the stream's mill's "🚪 Vstopi"), and the follow-up note came from a run on a build before it. Checked again:

- **Every way from one scene into another** is the stream's mill's door (`SceneDoors`: `stream/mill` is the mill
  project's landmark, the only thing of a picture that is a place with scenes of its own), and it is in four scenes:
  Ob potoku and Na mostu (the stream with the cart bridge built) in the Slovene village, Al ruscello and Il ponte in
  Friuli's. All four go in with `from` (`SceneScreen`'s `DoorEntries`), so Back is the scene gone in from, then the
  village. `SceneDoorsTest` and `NavStackTest` now have Na mostu and Il ponte too. No other way leads from a scene into
  another: a scene's tips have no scene to open (its only moment, "inside", has none; a tutor's `show_tip` asks only a
  moment of the place it is shown at), its home sheet opens the village's sheets and the school, and a link (a
  notification, the chat's `lani://scene/…`) opens its scene over the village, Home under it, as navigation.md has it.
  A villager's page opened from a scene that opens their happening in another scene puts that scene where the first
  was (scenes lie side by side), by design.
- **On the emulator** (QA's mill step, which now also leaves with the system's Back, the key as the gesture ends): it
  passed on the after build (2026-10-30, slot 2): into the mill from the stream's mill's bubble, "‹ Ob potoku · By the
  stream" back to the stream, then the village; into the mill again, the system's Back to the stream; inside by day and
  at night, a dialog with the stones grinding. The tap counts are as they were: one tap in from the bubble, one Back to
  the stream.

### The kitchen's closer frame

**Where the time went.** A JFR profile of the kitchen at detail 3 (night, a 360 × 300 window, 25.6 ms a frame on the
JVM at a load of 45): 40 % in `KitchenPainter.paint` itself, which by line (Kotlin inlines `plot`, `quadFill` and
`polyScan` into it) was the plot's top: the grass shaded pixel by pixel (`Diorama.plot`, 26 % on its two lines, with
the scan's loop 17 %), then `RoomPainter.ground` 16 % with `Math.hypot` 15.5 % (HotSpot's StrictMath, `Double.isFinite`
in it another 6 %) for the hen's patch of bare earth, `PixelCanvas.set` 11 %. Closer up the window is almost all the
room, and the floor is drawn over the grass under it: most of that work was painted over.

**What changed** (the pixels the same):

- **The grass under the floor isn't shaded** (`Diorama.under`, `flatSpans`): the kitchen hands the plot the rows its
  floor covers, scanned as `RoomPainter.onFlat` fills the floor (the same four corners, the same scan), and the plot
  leaves them; the floor sets every one of them after, opaque (every colour it gives has its alpha), and nothing in
  between reads them (the tufts and the house's shade touch the grass only, and the floor paints over them there). Only
  the kitchen does it so far, where the identity is tested; the other rooms' plots are as they were.
- **The hen's patch** (`RoomPainter.ground`, every room's): beyond 0.65 of her squared (0.806, past the patch's 0.8) the
  grass as it is, without the hypot and the noise; within, the same computation as before.

**The numbers** (SceneDetailTest, the CPU time of the rendering thread, the best of three rounds of ten frames):
the kitchen at detail 3 18.5 ms, 19.6 in all the weather (before: 23.5 and 25.2 on the desktop, infra.md; 25.6 in this
round's profile at a load of 45); at detail 2 13.9 and 15.9. Its budget is the shared 25 ms again; the dearest closer
frame is now the Alps' (21.4 ms in all the weather).

**The same pixels.** 1,000 pictures of the five rooms on the plot (the kitchen, the living room, the workshop, the
cellar, the attic), in both looks (Primorska, Friuli), at every level, by day, at dusk, at night in all the weather and
in the mist, at every detail, a window in the middle and one at a corner: their pixels, ids, glow and hits digested
before the change and after, none different (a scratch test, not committed). CultureLooksTest's Primorska digests hold.
Committed: SceneDetailTest's "the kitchen's grass under its floor is left unshaded, every pixel the same" renders the
kitchen with and without (`shadeUnder`) at every level, in both looks, at every detail, by day, at dusk and at night in
all the weather, and compares the pixels, ids, glow and hits.

### The last wall-clock checks

- `LoopTest` "parallel tool calls run together": on the test scheduler's virtual time (`runTest`): the two calls' 200 ms
  take exactly 200 ms of its clock together, 400 one after the other, whatever the machine's load (before: under 390 ms
  of the wall clock).
- `BackupTest` "the push waits for the writes to stop": the push scheduler's waits, its clock (`now = { currentTime }`)
  and the test's on one virtual clock (`backgroundScope`): the push of writes that never stop comes 700 to 800 ms after
  the first, exactly (before: 600 to 1,900 ms of the wall clock).

## How it is measured

```
LANI_DEV_DATA=<a synthetic learner's data> LANI_QA_HOME_SHOTS=<dir> LANI_QA_HOME_TRIES=3 \
  companion/bin/qa-slot --no-town --steps home-return [--apk <a qa build>]
```

home-return (QA's step, named only; home-return.md) now measures, besides the main thread's messages in the 3 s after
the key or the tap (QA's watch, `app/MainWatch.kt`), over 6 s:

- **the requests** the app made (`data/NetWatch.kt`: an interceptor of the app's one HTTP client, before the hosted
  transport, off until QA's `main:watch`): how many, each one's method, path, status and length, into
  `home-return-requests.txt`;
- **what the runtime allocated** on every thread and how many collections it ran: ART's own counts
  (`art.gc.bytes-allocated`, `art.gc.gc-count`), sampled every 50 ms by the watch's thread;
- **the frames**: the frame whose main thread worked longest, split into composing (from `AnimationStart`: Compose's
  recomposition and the animations), layout (from `PerformTraversalsStart`) and drawing (from `DrawStart` to
  `SyncQueued`), and all the frames' main parts summed (dumpsys gfxinfo framestats);
- first **Home left alone** for as long, the baseline (its header village draws at 12 frames a second).

The learner: home-freeze's synthetic one grown to some weeks of use (1,812 cards, 2.6 MB of data; nothing of a real
learner), a copy for each run. The builds: the qa build (R8 as in a release, QA's hooks) of this branch with only the
instrument (`e7fa5e19`, "before") and of the branch's end ("after"), installed with `--apk`, so the same QA script
measures both. The emulator draws in software (its frames are 200 to 400 ms whatever the app does), so the main
thread's part of the frames and the messages besides the frames say the app's cost.

The kitchen's frame: SceneDetailTest's "a closer frame renders fast" (the CPU time of the rendering thread, the best of
three rounds of ten frames, `FrameBudget`), and a JFR profile of 400 frames at detail 3 (a scratch test, not committed: execution samples every millisecond, by
method and by line through the class's SMAP for Kotlin's inlined code).

## Before and after

On the emulator, the qa builds, QA's home-return, three tries of each row (min–max, the median in brackets). The host
was rebooted twice during the round (/tmp is RAM: the builds, the learner and the tables went each time); the
numbers below are the ones measured, the runs as they were.

**Round 1** (home-freeze's synthetic learner: 1,812 cards, its clips' index; main as it was at `2437193f`): the before
build, and a first after build whose version still counted the events and the voice store.

| From | | requests in 6 s | allocated MB in 6 s | main thread besides frames, ms (3 s) |
|---|---|---|---|---|
| idle on Home | before | 0 | 16.0–19.4 | 12.6–18.3 |
| Back from the village | before | 33–40 | 45.2–127.9 | 51.2–94.6 |
| | after (first version) | 32–40 | 19.9–77.5 | 48.6–93.3 |
| Back from Settings | before | 23–29 | 38.5–84.9 | 55.2–123.0 |
| | after (first version) | 1–24 (1, 1 after the first) | 18.6–23.9 | 29.9–65.8 |

A Back from Settings was one request; a Back from the village still read everything: the dev bridge voiced the
village's lines in the background (its clips' index grew from 3.3 to 6.9 KB between the Backs), and each clip moved the
version. So the version no longer counts the events nor the voice store (above).

**Round 2** (a synthetic learner grown the same way from make-learner.ts's: 1,812 made-up cards, 60 error patterns, 120
sessions, no clips; both builds on main `e5c5d810`: before = the instrument alone, `d820c9ac`; after = the branch at
`184e77e7`, before Home's clock went by when it shows):

| From | | requests in 6 s | allocated MB in 6 s | collections | main thread besides frames, ms (3 s) | longest frame's main thread, ms | all frames' main thread, ms (6 s) |
|---|---|---|---|---|---|---|---|
| idle on Home | before | 0 | 19.5–20.5 (20.3) | 0–1 | 10.7–14.2 (12.9) | 2.8–33.7 (3.8) | 81.5–165.9 (84.5) |
| | after | 0–1 (0) | 16.5–17.1 (16.6) | 0–1 | 11.0–17.7 (11.6) | 20.2–54.6 (45.7) | 158.5–261.8 (190.8) |
| Back from the village | before | 31 | 27.8–30.2 (29.5) | 1 | 31.4–45.3 (39.5) | 9.6–21.3 (10.0) | 160.8–223.3 (167.5) |
| | after | 1–40 (1) | 24.3–102.2 (28.0) | 1–2 | 30.8–47.1 (44.7) | 13.0–20.9 (18.7) | 133.9–149.1 (134.6) |
| Back from Settings | before | 23–29 (24) | 37.8–79.4 (41.3) | 2–3 | 30.1–84.1 (47.6) | 28.4–61.9 (59.7) | 195.8–232.5 (212.4) |
| | after | 1 | 22.4–27.5 (25.4) | 1 | 25.5–38.3 (28.3) | 2.9–13.0 (11.3) | 79.6–160.6 (122.2) |
| Opening the village | before | 3 | 42.4–45.1 (43.4) | 2 | 18.8–32.2 (28.1) | 23.6–58.1 (47.8) | 335.3–380.7 (372.0) |
| | after | 3 | 49.6–53.9 (49.7) | 2 | 23.5–40.7 (27.1) | 12.3–30.7 (22.4) | 220.9–257.8 (253.0) |

- **Requests:** a Back is one request now (`GET /changes`, 55 bytes) instead of 23 to 31; the after's 40 is the first
  Back after the setup, which read everything once (the app's own writes at its start had moved the version; the forms
  of the deck's words still being fetched, 680 at a reading).
- **Allocation:** within a few MB of Home left alone (16.6 MB in 6 s, its header village drawing): 22 to 28 MB against 28
  to 79 before; on round 1's learner, whose answers are bigger and whose clips' index was read and written each time,
  before was 38 to 128 MB.
- **The main thread:** Back from Settings 28 ms besides the frames against 48, its frames 122 ms against 212 in 6 s,
  the longest frame 11 ms against 60. From the village the frames' sum fell (135 against 168 ms) and the messages
  besides them stayed (39 to 45 ms: the village's own leaving), its longest frame 19 ms against 10: Home's display lists
  drawn again on showing, where a Home composed anew recorded them fresh.
- **Home left alone got worse** in this build (its longest frame 46 against 4 ms, its frames 191 against 85 ms in 6
  s): the clock of the minute added for the kept Home recomposed all of Home once a minute. Fixed after this round
  (`rememberShownAt`: what goes by the clock is worked out when Home shows, not every minute); round 3 below.

## Tests

- `ScreenStackTest` (Robolectric, Compose): Home composed once across the village, a scene and Back (a remembered
  observer begun once, never forgotten), its remembered state as it was; the village below a scene is its state only
  (composed again); a covered Home is neither measured nor placed while what it shows changes, and shows it once on
  top; a covered Home's BackHandler takes no Back from the screen on top, and takes it once Home is on top; a covered
  screen's clock (`rememberNow`) stops and says the time at once when shown; behind the back gesture Home is placed,
  still covered (no semantics); a stack whose root isn't Home keeps nothing.
- `FreshTest`: the same version a moment later is fresh; another version, day, time of day, pair, learner or culture,
  ten minutes on or a clock gone back read again.
- `BridgeAnswersTest`: asked with its ETag the same answer is a 304 and the value read then (the first time no ETag is
  sent; another learner asks without the other's); `changes()` is the version, `null` for an older bridge's 404, a
  failure names its status.
- The bridge's smoke, section `changes` (13 checks): `/changes` needs the app token, has a version and its day, stays the
  same over all of Home's reads (none of them writes what it walks), moves when a database is written beside the bridge
  (update-db.py, card.py), with each event and when the content changes under the bridge; a GET's JSON answer has its
  ETag, the same answer asked with it is a 304 without a body, a list of ETags with the weak form matches, another ETag
  gets the whole answer, `/state`'s ETag is its body's, the event stream and an error have none.
- `SceneDetailTest`: every closer frame under the shared 25 ms again, the kitchen's among them; "the kitchen's grass
  under its floor is left unshaded, every pixel the same" (above).
- `SceneDoorsTest`, `NavStackTest`: the mill from Na mostu and from Il ponte, as from the stream (below).
- `LoopTest`, `BackupTest`: on virtual time (above).
- QA: home-return measures the requests, the allocations and the frames' phases; the mill step leaves the mill gone into
  from the stream with the system's Back too.

## How to use it

Nothing to do: the app asks a bridge that has `/changes` and ETags (this branch's, the phone's own bridge with the APK
built from it), and an older bridge is read as before. To see what a Back costs, QA's home-return (above). To look at a
renderer's time by line: JFR from a unit test (`jdk.jfr.Recording` with the "profile" settings and
`jdk.ExecutionSample` every millisecond around a few hundred renders, read back with `RecordingFile`), then the class's
SMAP (`javap -v`, SourceDebugExtension) to map the lines Kotlin inlined into a painter's `paint` back to their source.
To check a renderer's change kept every pixel: digest the pictures (pixels, ids, glow, hits) of every art, look, level,
view, detail and window before the change and compare after; for the kitchen's floor SceneDetailTest does it.

## What's left

- **Home at rest allocates about 3 MB a second** (the baseline row: 16 to 19 MB in 6 s with nothing touched): its header
  village renders 12 pictures a second on its render thread. Not this round's (it is Home on view, not Back), and it
  makes a collection every few seconds while Home is open: worth a look at what `VillageRenderer.render` allocates a
  frame.
- **The other screens below the top** (the village under a scene, Napredek under a unit) are still composed anew when
  Back returns to them: only Home is kept. The village keeps its renderer and pictures (`Retained`), so its Back is
  cheap already; keeping more screens composed would keep their memory too.
- **The back gesture's Home behind the screen** isn't emulator-checked (QA's emulator navigates with buttons, and adb
  can't hold a predictive back gesture); ScreenStackTest checks it is placed.
- **A reading after a change still asks for everything** (with the ETags most come back as 304s, but the bridge
  still makes each answer to hash it: about 1.1 s of its work on a node). Per-area versions (the learner's state, the
  content, the towns, the voices) would let a run's result read only the state; it needs each route's inputs known,
  which the one version doesn't.
- **The other rooms' plots** (the living room, the workshop, the cellar, the attic) could leave the grass under their
  floors unshaded the same way; they are under their budget (12 to 14 ms at detail 3), and each needs its floor checked
  to cover what it says.
- Measured on the emulator and the desktop JVM, not on Jan's phone.
