# Built: Back after a real change stays cheap (changes-areas, 2026-10-31)

The follow-ups' third batch, part 6 (Jan, 2026-10-31: "ok do all"; scratchpad followups-3.md, section 6). nav-perf
(docs/plans/05-built/nav-perf.md) made a Back that changed nothing one small request, kept Home composed under the
screens opened from it and left four costs: after a real change the next Back still read everything; the first Back
after setup read everything once; Back from the village had a longest frame of 19 to 24 ms (10 before); and Home at rest
allocated about 3 MB a second for its header village. This round takes them out. Measured first, changed second: the
numbers before and after are below.

## What

### A version per area (the bridge)

`GET /changes` (`companion/bridge/src/changes.ts`) answers `{version, day, areas}`: besides the version of everything (an
app before the areas goes by it as before), the version of each area of what the bridge serves the app:

| Area | Its files | Moved by |
|---|---|---|
| `learner` | the data directory outside `app/` (the six databases, another language's in `languages/`, the sessions' results), and the bridge's other records of the learner in `app/`: every file in none of the areas below (the answers' log, the word requests, the tutor's notebook …) | a run's result, a review, a session's `update-db.py`, `card.py`'s fixes |
| `village` | `app/game.json` | every move in the village |
| `towns` | `app/towns.json`, `app/town-*` | a guest, a question, a friendship, a link |
| `family` | `app/family/`, `app/audio/` | a family challenge, a recording |
| `content` | the tutor's stores in `app/` (modules, packs, grammar, cards, cases, course, scenes, variants, stories, readings, villagers, arrivals, scenarios, overrides, `lexicon.json`, the phone's `voice-profiles.json`), on a node the project's content under `companion/` and any content directory set apart, and who the learner is: the profile's `learner`, `preferences` and `home_language` by value | the tutor's publishing, a merge on main, a new name, level or base language |

Each is a digest of this run of the bridge, the day and its files by inode, size and time of change (the walk of
nav-perf, each file into its area's digest), `content` also of the learner's identity as a value: a session writes the
profile's counts (the streak, the sessions), which move `learner` only. The bridge's own bookkeeping moves nothing, now
also the paired phones' last visits (`app/devices-seen.json`) and the notifications waiting for the tutor
(`app/channel-queue.json`, written with a session's note while no tutor listens: it moved the version after every
review on a node whose tutor was away).

**What each read depends on** (`Route.areas`, declared where the route is): `/state` `learner`; `/packs` `learner` and
`content` (each pack's words learned); the scenes, the course, the grammar book, the cheat sheet, the modules, the
readings, the case bank, the people, the arrivals, the culture, the role-plays, the stories, the drills and the clips'
index and voices `content`; the towns' list, guests, children and questions `towns` and `content`, their friendships also
`village` and `learner` (the feast the village shares, the learner's level for a move); the family's challenges,
settings and recordings `family` and `content`. Each such GET's answer says the versions of its areas as they were before
it was made (`lani-areas: learner=…, content=…`, on a 200, a 304 and a 404; never on another error, which the app asks
again). A walk is taken again for the answers in the next 2 s while no write came through the bridge: walked before the
answer, those versions are never newer than what it says (a change meanwhile makes the app read it once more, never
less). A route without areas says none, and the app reads it again whenever anything moved.

**Checked, not assumed.** A probe on the QA learner (scratch, `probe/deps.ts`) swapped the six databases for another
learner's, then only the spaced repetition, then the profile's name and gender, its level, its streak and sessions, then
the village, and listed the answers that changed: the spaced repetition changed `/state` and `/packs` only, the name and
gender every read said to the learner (the scenes, the course, the grammar book, the readings, the people, the
role-plays, the stories, the town's name, the family's settings), the level, the streak and the sessions `/state` only,
the village `/game` only. Which is what the areas say. The smoke checks it on every run (below).

### Home reads only what moved (the app)

- **Each answer's areas are kept by its URL** (`data/Areas.kt`): an interceptor of the app's one HTTP client (before the
  hosted transport, so the phone's own bridge too), so the family's, the clips' and the towns' reads count as the
  bridge's own. An answer without the header (an older bridge, a route without areas, an error besides a 404) forgets
  its URL's: never fresh. A request that got no answer (offline) keeps what its URL had: the app still has what it read
  then.
- **The reading** (`AppViewModel.reload`): nothing when the version didn't move (as before); when it moved, while the
  day, the time of day, the pair, the learner and the culture are those of the last reading and the last whole reading
  is under ten minutes old (`Fresh.within`), only the reads whose areas moved since they were made (`Bridge.fresh`): the
  culture, the family, the towns' questions, the towns (each of its reads on its own: `TownsController.load(fresh)`), the
  clips' index, the dashboard and the other languages' decks, the modules (`ContentController.reload(fresh)`), the
  grammar book, the cheat sheet, the course, the readings, the case bank, the packs, the scenes, the people and their
  arrivals. The deck's forms and the day's counting and forms cards come with the deck (what the learner did). A new
  culture from the bridge reads the rest whole; anything else (another day, a new connection, new data in the hosted
  mode, ten minutes) everything, as before.
- **The app's own writes and the events** read only what moved too: the refresh after the outbox delivered a review
  (`onSaved`) reads the state, the packs and the deck's cards, not the scenes.

### The first Back after setup

It read everything once (35 to 43 requests), for two reasons:
- **The reading was for a learner it didn't know yet.** A new phone learns the learner and their pair from the first
  dashboard (`relearn`, `showLanguages`), part way through the first reading, which recorded the ones it began with: the
  next reading was for "another learner" and read everything. Now the reading records the learner, the pair and the
  culture it settled on (the reads before the dashboard, the culture's, the family's, the towns' and the clips', don't
  depend on them). The first after build still had it (its first Back from the village: 43 requests); the second hasn't.
- **The reading wrote itself.** The day's counting and forms cards go into the spaced repetition during the first reading,
  and the app's start writes the village: the one version taken at the reading's start was older than the files when it
  ended. Now each read says the versions it was made at, and a write through the bridge makes the next answers walk
  anew: the dashboard read again after the cards were made says the spaced repetition with them, the packs after it too;
  the village's moves move only the village, which only the towns' friendships depend on.

### Back from the village

- **Home covered lies out of the way, placed** (`ui/Covered.kt`): measured and placed left of the window
  (`OUT_OF_THE_WAY`, a million pixels: nothing there to touch) and not drawn (`drawWithContent` draws nothing while it
  isn't shown), its semantics cleared, its Back handlers off, `LocalCovered` as before. nav-perf's covered Home was
  neither measured nor placed: shown again it was placed anew and its pictures (display lists) recorded anew in the
  Back's frame. Placed, its layers keep what they drew, and what changed in it meanwhile (the reading's results) is laid
  out while it is covered, not in the Back's frame. Behind the back gesture Home is placed in the window and drawn, as
  before.
- **The screen left ends after the Back's frame** (`ScreenStack`): the screen Back leaves for Home stays composed for
  two frames more, covered (out of the way, not drawn, no semantics, its Back handlers off), at the same place in the
  composition, so it isn't composed anew; then its composition ends. A method trace of the before build's first Back
  from the village (am profile) had the village's composition ending (`CompositionImpl.dispose`, the layout nodes'
  release) as about half of the Back's 35 ms frame. Covered gives a screen on top the real Back dispatchers now (the
  idle ones only while covered), so wrapping the top screen in it changes nothing for its own Back.
- **Less to do in the Back's frame**: the reading after it reads nothing when nothing moved and only what moved
  otherwise (above), so the Back's frames no longer apply 30-odd answers to Home's state; the header village's clock
  starts again without composing anything (below).

### Home at rest

What Home made for the collector with nothing touched (16 to 19 MB in 6 s) was the header village, in three parts:

- **The main thread drew the village twice a picture** (`VillageScene`): its canvas read the clock in the draw phase, so
  each tick drew it (and built the picture's frame there, to ask the render thread), and each picture the render thread
  handed over drew it again: 24 draws a second for 12 pictures. Now the clock's ticks ask for the pictures
  (`askPicture`: the frame, the construction's and the celebration's marks), and the draw only shows the newest picture:
  12 draws a second. A new state of the village is asked for at once, not at the next tick.
- **The village was drawn in Home's own pictures**: its canvas had no layer of its own, so each of its pictures recorded
  all of Home's drawing that has none again. It draws in a layer now (`graphicsLayer()`), and a new picture records only
  that layer. Between the ticks the main thread sleeps (`delay` to the next tick; before it woke every frame).
- **The render thread made 167 KB a picture** (the desktop JVM, the QA learner's village as Home's header): 64 KB for the
  footpaths' grid made anew each picture (`PathGrid`, now one the renderer keeps and clears), the frame's date parsed
  from nothing several times a picture with its exception's trace filled in (`Frame.date`: read once, no parsing of an
  empty day), the festivals' dates for two years each picture (`CentreLook.festivalDay`: once a day), each depth boxed
  to sort the painter's order (`sortWith`), the palisade's gates and torches by boxed index (arrays by stake). The
  pixels are the same (the render tests' digests and pictures hold); what is left is mostly a closure for each thing
  drawn, the painters' way.
- **The streak's pulse** (the flame when the streak waits for today's practice) composed the pill again every frame:
  its size is read in its layer now (`graphicsLayer { scaleX = … }`), nothing composed.

## How it is measured

```
LANI_DEV_DATA=<a synthetic learner's data> LANI_QA_HOME_SHOTS=<dir> LANI_QA_HOME_TRIES=3 \
  companion/bin/qa-slot --no-town --steps home-return --apk <a qa build>
```

QA's home-return as nav-perf left it (the main thread's messages in the 3 s after the key or the tap, over 6 s the
requests, what the runtime allocated and its collections, the frames and the longest frame's phases), and a row more:
**Back from Settings after a real change** ("settings, a change"): Settings opened, a session's results written beside
the bridge meanwhile (the learner's databases in the dev bridge's copy, as `update-db.py` would), then Back.

The learner: nav-perf's synthetic one (make-learner.ts grown to 1,812 made-up cards with review histories, 60 error
patterns and 120 sessions; nothing of a real learner), a copy for each run. The builds: the qa build (R8 as in a release,
QA's hooks) of main `b3ff5280` ("before") and of this branch ("after"), installed with `--apk`, measured by the same QA
script. The emulator draws in software (its frames are 200 to 400 ms whatever the app does): the main thread's part of
the frames and the messages besides them say the app's cost.

The header village's allocation on the desktop JVM: a scratch test (not committed) rendering the QA learner's village as
Home's header (360 × 140 at a scale of 3) 120 to 240 times, the render thread's bytes allocated a picture
(`ThreadMXBean.getThreadAllocatedBytes`), by variant (without the field, without the palisade, without buildings) and by
the renderer's passes (its profile marks reading the thread's allocated bytes instead of the clock, for the measurement
only), and a JFR recording of 600 pictures (`jdk.ObjectAllocationSample` with stacks) for where (its weights lean to
what allocates right after a buffer is refilled: the passes and the variants are the measure, the stacks the hint).
`HeaderVillageAllocTest` keeps what came of it.

## Before and after

On the emulator, the qa builds, QA's home-return, min–max of the tries (the median in brackets). The machine was shared
with seven other agents' builds and QA runs all day: the frames' times are its load as much as the app's (before2 ran
on QA's second emulator while the first ran another agent's QA; its idle frames took twice the morning's). The requests
and the allocations hold whatever the load.

**Before** (main `b3ff5280`, the morning, emulator 1, 3 tries; before2 the afternoon, emulator 2, 3 tries, with the row
after a change; both against this branch's dev bridge, which the older app reads as one version):

| From | | requests in 6 s | allocated MB in 6 s | collections | main thread besides frames, ms (3 s) | longest frame's main thread, ms | all frames' main thread, ms (6 s) |
|---|---|---|---|---|---|---|---|
| idle on Home | before | 0–1 (0) | 16.7–20.9 (19.5) | 0–1 | 12.1–15.6 (14.4) | 12.9–52.4 (19.7) | 132.3–201.2 (179.5) |
| | before2 | 0 | 12.6–13.9 (13.3) | 0–1 | 8.9–13.9 (13.3) | 18.7–47.5 (43.0) | 304.4–332.0 (318.7) |
| Back from the village | before | 1–42 (34) | 28.7–107.3 (36.6) | 2–3 | 30.7–126.8 (59.5) | 4.0–23.8 (11.1) | 89.0–146.7 (126.1) |
| | before2 | 1–32 (1) | 21.4–47.9 (24.5) | 1–2 | 48.2–80.5 (54.4) | 24.6–41.5 (29.0) | 204.9–272.1 (265.7) |
| Back from Settings | before | 1 | 26.4–28.5 (27.9) | 1 | 26.7–28.8 (27.0) | 16.1–42.5 (17.0) | 108.7–228.0 (121.6) |
| | before2 | 1–37 (1) | 20.7–31.8 (21.5) | 1 | 27.2–57.8 (54.1) | 42.2–62.9 (45.8) | 271.9–381.2 (302.5) |
| Back from Settings after a change | before2 | 24–40 (28) | 43.6–97.6 (43.9) | 1–4 | 55.2–95.1 (84.4) | 18.4–62.5 (19.0) | 179.3–418.4 (238.3) |
| Opening the village | before | 3 | 47.2–59.9 (49.3) | 2 | 20.7–25.4 (21.2) | 7.1–61.6 (14.3) | 193.6–511.1 (278.8) |

The first Back from the village read everything (42 and 32 requests), the second of the morning again (34: the village
had moved the one version); the Back from Settings after a change read everything (24 to 40), and once a Back from
Settings too (37: the ten minutes).

**After** (this branch; after2: `b2383ac1`, the learner settled on, emulator 1, 4 tries; the first after build, after1,
before that fix, read everything at its first Back too: 43 requests):

| From | | requests in 6 s | allocated MB in 6 s | collections | main thread besides frames, ms (3 s) | longest frame's main thread, ms | all frames' main thread, ms (6 s) |
|---|---|---|---|---|---|---|---|
| idle on Home | after2 | 0–1 (0) | 6.8–7.0 (7.0) | 0–1 | 15.0–25.5 (23.4) | 1.6–3.0 (2.1) | 35.4–49.7 (41.2) |
| Back from the village | after2 | 1–29 (2) | 15.6–84.2 (15.8) | 0–2 | 34.4–127.1 (89.8) | 9.0–23.8 (17.9) | 63.3–106.2 (90.8) |
| Back from Settings | after2 | 1 | 14.8–15.4 (14.9) | 1 | 36.5–73.0 (47.4) | 6.8–20.7 (8.2) | 47.7–71.5 (54.1) |
| Back from Settings after a change | after2 | 15–36 (18) | 43.9–90.9 (65.1) | 1–2 | 51.2–131.5 (67.0) | 6.0–12.9 (7.9) | 40.0–70.4 (48.1) |
| Opening the village | after2 | 4 | 22.2–24.4 (23.0) | 1 | 22.2–42.3 (25.8) | 8.6–48.9 (18.6) | 273.7–493.5 (304.9) |

- **Home at rest** makes 7 MB in 6 s, 1.2 MB a second (before 13 to 21 MB, 2.2 to 3.5 a second), with a collection
  every few seconds less; its frames take 41 ms of the main thread in 6 s against 180 and 319, the longest 2 ms against
  20 and 43. The work besides the frames grew by about 10 ms in 3 s: the clock's ticks ask for the pictures there now (in
  a frame before).
- **A Back after a real change** (a session's results written beside the bridge while Settings was open): 15 to 21
  requests, against 24 to 40: `/changes`, `/state` (the learner's state, 1.2 MB, which did change), `/towns/friendship`
  (it depends on the learner), the day's forms cards and the deck's forms (10 to 18 small `/forms` of the 1,812-card
  deck still being filled: they come with the deck's reading); not the scenes, the course, the grammar book, the
  modules, the cards, the readings, the case bank, the packs, the people, the culture, the family, the clips. The fourth
  try is the ten-minute whole reading (36). Its frames: 48 ms in 6 s against 238, the longest 8 against 19.
- **The first Back after setup**: 29 requests in after2, all of them the content's (none of the state, the forms, the
  towns' list): the content's version had moved between the first reading and the Back, by QA's own screenshots and logs
  under `companion/qa-shots`, which the walk of the project took for content (`e9a873f3` leaves them out). The final run
  below has it without.
- **Back from the village** allocates 15.8 MB in 6 s against 24.5 and 36.6, its frames 91 ms against 126 and 266.
  Its longest frame (9 to 24 ms, 18 in the median) is in the range of the before runs (4 to 24 and 25 to 42 ms): the
  Back's own frame, composing Home's covered parts again and ending the village's composition, which the final change
  splits (below).
- **Back from Settings** without a change: one request (as before), 14.9 MB against 21.5 and 27.9, its longest frame 8
  against 17 and 46.

## Tests

- The bridge's smoke, section `changes` (16 checks more): `/changes` has the version of each area; Home's reads move no
  area; each read the app makes again on a Back says the areas it depends on at their versions now, a 304 too, a read
  without areas none; each area moves with its own files only (a database written beside the bridge, the answers' log,
  the village written and gone again, a town's file, the family's, a curated module under the bridge, the profile's
  counts `learner` only, its learner's name `learner` and `content`, the bridge's bookkeeping nothing); the content's
  reads say the same after a card, a mistake and a streak (only `/state` and `/packs` differ); after a write through the
  bridge the next answer says the versions walked anew.
- `AreasTest`: the header read as each area's version; a read fresh while its areas are at their versions, not when one
  moved or `/changes` lacks one; an answer without the header forgets them, a 404 says them, a 500 not; a read that got
  no answer keeps what it had, another node is another URL; a write's answer says nothing.
- `BridgeAnswersTest`: `/changes` with its areas, and without (a bridge before them). `FreshTest`: another version of the
  same day, time, pair, learner and culture reads only what moved, ten minutes or any of those everything.
- `ScreenStackTest`: a covered Home lies out of the window, nothing of it touched (a tap where the screen on top has
  nothing), what changed shows once on top, where it was, touched again.
- `HeaderVillageAllocTest`: a picture of Home's header village (a village of some weeks, by day and at night) makes
  under 110 KB for the collector on its render thread, asking for a picture under 4 KB on the main thread; the night sky
  the same within the second, worked out again after it.
- The render tests' pixels (`GroundCacheTest`, `PalisadeTest`, `VillageRendererTest`, `MapLooksTest`,
  `HearthsRenderTest`), unchanged: the footpaths, the order, the date, the festival day and the palisade's lookups draw
  the same.

## How to use it

Nothing to do: the app asks a bridge that has the areas (this branch's, the phone's own bridge with the APK built from
it), and an older bridge is read as before (no areas: everything when its version moved). To see what a Back costs, QA's
home-return (above). To see what a read depends on: its answer's `lani-areas` header (`curl -sD- -o /dev/null -H "Authorization: Bearer $TOKEN" "$BRIDGE/scenes"`), and
`GET /changes` for the versions now. A new GET the app
reads again on a Back declares its `areas` where the route is (`companion/bridge/src/http.ts`, `Route.areas`), and the
smoke's changes section lists it with them.

## What's left

- **The towns' friendships** depend on the village (the feast it shares) and the learner (their level for a move), so a
  move in the village or a session reads `/towns/friendship` again, which asks each linked town for its counts (3 s at
  most, in the background). Their feast and level as values (as the learner's identity is for the content) would leave
  them out.
- **The ten-minute reading** still reads everything (with the ETags most come back as 304s, but the bridge makes each
  answer to hash it). The bridge could answer a 304 without making the answer when the If-None-Match is the ETag it
  served at the same versions of the read's areas; only for the reads whose areas say everything they depend on (not
  the towns', whose answers are the linked towns' too, nor the clips', the voice store's).
- **The deck's forms** (`/forms`, 400 lemmas a reading) come with the deck's reading: on a big deck still being filled
  (the QA learner's 1,812 cards) a session's results bring 10 to 20 small requests of forms after them.
- **The header village's render thread** still makes about 85 KB a picture (a closure and an object for each thing
  drawn, the painters' way: a tree, a stake, a building); a renderer that kept what doesn't move between pictures (the
  buildings, the trees, the palisade) would make less and draw less.
- **Measured on the emulator and the desktop JVM**, not on Jan's phone; the emulator's frames are its software
  rendering's and the shared machine's load (other agents' builds), so the frames' numbers are noisy (see the tables).
