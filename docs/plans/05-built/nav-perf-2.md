# Built: Back and Home, the leftovers (nav-perf-2, 2026-11-02)

Wave C, part 2 (Jan, 2026-11-02: "sure continue"; the kit's followups-8.md, section 2). The brief named four costs of Back
and Home, from the Home freeze notes, nav-perf.md and changes-areas.md. Three were already gone; one was open; and
measuring found two more that kept the third alive in real use. Measured first, changed second: the numbers are below.

## The brief's items

| Item | State on main (`21aa4e9b`) | Checked by |
|---|---|---|
| Home composed anew after every Back | **Already done** (nav-perf): `ui/Covered.kt` `ScreenStack` in `MainActivity` | the code; `ScreenStackTest`; a Back's longest frame below |
| ~16 requests per Back; `Clips.refresh` re-fetches the audio index (40 to 110 MB) | **Already done** (nav-perf): a Back that changed nothing is `GET /changes` (1 request, 14 to 15 MB in 6 s, against Home's 7 alone); the index is asked with its ETag | QA's home-return, below |
| After a real change the next Back builds every answer (~1.1 s) | **Done by changes-areas within ten minutes of the last whole reading; still true after them.** Changed here | below |
| `/towns/friendship` reread after every village move or session | **Open.** Changed here | below |

## What changed

### The towns' friendships say what they take by value (the bridge)

`GET /towns/friendship` depended on the areas `village` and `learner`, so every move in the village and every session made
the next Back read it, and the bridge asked each linked town for its counts (3 s at most). Of the village the answer takes
only its last feast (`feastOf`), of the learner only their level in each linked town's language (`skillIn`). A new area,
`friends`, says those two by value (`changes.ts`: `Changes.value`, registered by `features/friendship.ts`):

- worked out again only when an area it comes from (`village`, `learner`, `towns`) moved, so a walk costs nothing more when
  they didn't, and reading the village's file happens only after a move;
- it moves only when the value does: a move in the village that keeps the feast moves `village` alone, a session `learner`
  alone, and no read depends on those alone any more for the friendships;
- empty without a linked town.

The friendships' read now depends on `towns`, `friends` and `content`. The app needs no change: it compares each answer's
areas with `/changes` by name, so an app already installed gets this with the bridge.

**The one timing that differs:** a friend's own news (their trouble, their feast, their counts) used to come along when a move
or a session happened to read the friendships. It now comes as `/towns`'s always has: with a write in the towns' files (a
guest, a question, help sent), an event, the ten minutes below, a new day, or opening the village (`GameController.sync` reads
the friendships each time, as before). So Friends' badge on Home can show a friend's trouble up to ten minutes later than it
might have; before, that lateness depended on whether the learner had moved anything.

### Ten minutes on, a Back reads what no version says, not everything (the app)

The per-area versions only helped within ten minutes of the last whole reading: after them **any** Back read everything
(24 to 31 requests, 1.4 s of the bridge's work, 20 MB for the phone's collector, 46 with a session's results, each 55 more when
the day's audio is planned). A run of exercises often takes longer than ten minutes, so the usual Back after a session didn't
use the areas at all: the brief's "next Back builds every answer" was still true where it matters most.

The ten minutes were there for "what no version says: a linked town's news and a clock the bridge doesn't count by"
(nav-perf.md). So I audited what each of Home's reads depends on:

- **Clocks count by the day at most.** `/state` is `read-db.py` (dates: `%Y-%m-%d`); `/cards`, `/course`, `/cases`, `/readings`,
  `/packs` take `today`; `/villagers` has its time of day in its URL (and another time of day reads everything anyway);
  `/modules`, `/grammar`, `/scenes`, `/arrivals`, `/culture`, `/scenarios`, `/stories`, `/drills` have no clock in their
  answers' making. The whole readings on the emulator agree: `/state`, `/modules`, `/grammar`, `/cards`, `/course`,
  `/readings`, `/cases`, `/packs`, `/scenes` and `/villagers` came back 304 each time.
- **What no version says** is then the towns' reads (`/towns`, `/towns/children`, `/towns/guests`, `/towns/friendship`,
  `/towns/questions`: they ask the linked towns when they're made, and a town's news moves no file here) and the voice
  store's (`/voice/index`, `/voice/profiles`: not walked; each clip made says so, `voice_updated`, an event the app can miss).

So (`app/Freshness.kt`, `AppViewModel.reload`, `NO_VERSION`):

- **Every ten minutes** (`Fresh.due`, counted from when they were last read: `Fresh.asked`) those reads are read again, moved
  or not, with whatever moved, and with what a whole reading also did: the deck's forms, the day's counting and forms cards,
  the people's voices, the day's audio plan.
- **Everything** is still read for another day, time of day, pair, learner or culture, after a new connection or new data,
  and now **an hour** after the last whole reading (`Fresh.WHOLE_AGE_MS`). The hour is my call, not the audit's: should a read
  have a clock the audit missed, it is at most an hour stale instead of ten minutes.
- A bridge without areas reads everything at ten minutes, as before.

The learner sees nothing different: the same things come as fresh.

### Smaller

- **A merge of the project's docs alone moves no version.** The walk took `companion/README.md`, `GAME.md`, `SCENES.md`,
  `VILLAGERS.md` and `COURSE.md` for content, and a node's bridge reads merged content at once, so a docs-only merge made
  every phone's next Back read the content again. Nothing the app reads comes from them. Of the last 100 commits under
  `companion/`, 58 touch what the walk sees, and 9 of those touch only these docs.
- **The day's audio plan reads each pack once.** It works out today's words and tomorrow's from the same packs and read each
  for both: three packs were six requests in every reading that planned (`DayAudioGather.packsRead`).
- **The app says what each reading read** (log tag `LaniRead`): what moved, what moved and what no version says, or everything
  and why (no reading recorded, another day, time of day, pair, learner or culture, an hour on, no areas from the node), a
  reading beside another, one that stopped part way (`Fresh.unlike`). No learner data in it.

## What I found and left

- **The first Back after setup reads everything once** (38 requests, 105 MB on the QA learner). Not a bug in the reading: its
  log says "what moved", and the setup's last step keeps the learner's own voice (`MyVoices.keepAtSetup`:
  `preferences.my_voice`), which moves `learner` and `content` after the first reading. Once per phone, so not Jan's
  recurring cost. changes-areas' first Back had 1 to 7 requests because that step came later (setup-refresh).
- **A reading beside another reads everything.** `reload` clears its record at its start, and `refresh()` doesn't wait for a
  running reading, so a `words_added` during a reading (the day's forms or counting cards being made in it) starts a whole one
  beside it. Seen in the code, not in any run: none of the runs logged "begins beside another". The log line now tells if it
  happens on a phone. Keeping the record until the end would fix it, and failed readings stay safe through each URL's own
  areas, but I'd want it seen first: `reload` has no test of its own.
- **The day's audio plan makes about 55 MB for the collector each time it runs** on this learner (rows with its pack requests
  against rows without: 70.3 against 15.4 to 16.0, 71.1 against 15.3, 97.2 against 42.6). It is the largest allocation left in a
  Back, off the main thread but worth a collection; it runs at most once a minute, whenever a reading read something. I left
  when it runs alone (planning less often would delay the words the background job fetches); what `DayAudioGather.plan`
  allocates is worth a profile.
- **`/state` is a Python process per read** (`read-db.py`: 336 ms of the whole reading's 1.4 s here). Caching its answer by the
  learner's version would take the bridge's part out. On the phone a Back with a session's results makes 30 MB more than one
  without (46 against 16): its 1.2 MB answer and what follows from it, not profiled.
- **Opening the village** still reads `/game`, `/towns/guests` and `/towns/friendship` (`GameController.sync`): 3 requests,
  unchanged.

## How it is measured

```
LANI_DEV_DATA=<a synthetic learner's data> LANI_QA_HOME_SHOTS=<dir> LANI_QA_HOME_TRIES=3 \
  companion/bin/qa-slot --slot 2 --steps home-return --apk <a qa build>
```

QA's home-return as changes-areas left it, with:

- **the second town linked** (`TOWN_STEPS`; unless `--no-town`), so the friendships' read has a friend to ask;
- **a row after a move in the village** ("village, a move": the village's state written beside the dev bridge, as a move writes
  it), and **two rows ten minutes on** ("settings, ten minutes on", "settings, a change, ten minutes on": QA's new hook
  `read:older:<minutes>` sets the last reading back eleven minutes);
- **what moved and what was read**: with each row's requests, the areas that moved before the key and in the window (QA's own
  `GET /changes`), the data copy's files that moved them, and the app's `LaniRead` lines.

The learner: changes-areas' synthetic one (1,812 made-up cards with review histories, 63 error patterns, 124 sessions; nothing
of a real learner), a copy for each run. QA's slot 2 (dev bridge 8781, second town 8787), never the service ports. The
emulator draws in software and the machine was shared with other agents' builds and QA runs all day, so frame times are
their load as much as the app's: the requests and the allocations hold whatever the load.

The bridge's side, on the same learner with the second town linked (a scratch script, not committed): each of Home's reads
asked as the app asks it, with its ETag, one after another, five rounds: the median of the rounds.

## Before and after

Three runs, three tries of each row, min–max (the median in brackets), the second town linked:

- **main**: main's app (`21aa4e9b`) and bridge: the friendships' before;
- **before2**: an app with main's readings and QA's hook (`6779a10a`), the bridge with the friendships' change: the ten minutes' before;
- **after**: the branch merged with main (`3895650a`).

| From | | requests in 6 s | allocated MB in 6 s | collections | main thread besides frames, ms (3 s) | longest frame's main thread, ms | all frames' main thread, ms (6 s) |
|---|---|---|---|---|---|---|---|
| idle on Home | main | 0–39 (0) | 6.5–13.8 (6.7) | 0–1 (1) | 19.3–41.3 (25.5) | 2.1–12.7 (2.9) | 40.4–57.0 (45.4) |
|  | before2 | 0–1 (0) | 5.4–5.6 (5.4) | 0 | 24.3–42.6 (37.9) | 3.3–16.1 (6.6) | 50.5–70.9 (51.7) |
|  | after | 0 | 6.4–6.6 (6.5) | 0 | 24.0–37.3 (24.4) | 1.7–12.2 (3.9) | 42.3–60.9 (46.4) |
| Back from the village | main | 1–38 (1) | 15.0–104.7 (15.5) | 1–2 (1) | 31.9–96.8 (95.4) | 13.2–25.5 (20.5) | 70.3–106.8 (88.6) |
|  | before2 | 1–39 (1) | 13.8–102.7 (14.2) | 1–2 (1) | 64.9–104.5 (71.2) | 36.5–75.4 (45.2) | 144.7–239.5 (151.0) |
|  | after | 1–35 (1) | 15.1–104.1 (15.2) | 1–2 (1) | 65.9–95.5 (85.8) | 23.7–26.5 (24.6) | 106.6–128.8 (122.7) |
| Opening the village | main | 3 | 22.9–29.3 (27.5) | 1 | 17.8–21.5 (19.7) | 12.8–24.8 (14.4) | 249.0–290.7 (285.0) |
|  | before2 | 3 | 21.4–23.0 (21.7) | 1 | 17.4–20.4 (18.2) | 12.4–47.3 (12.7) | 399.7–452.4 (425.7) |
|  | after | 3 | 21.1–22.2 (21.7) | 0–1 (1) | 25.0–56.7 (28.5) | 30.2–54.8 (43.9) | 444.1–524.7 (479.0) |
| Back from Settings | main | 1 | 14.2–14.9 (14.3) | 0–1 (1) | 26.1–44.6 (35.0) | 8.2–11.5 (8.8) | 36.6–54.6 (42.4) |
|  | before2 | 1–2 (1) | 13.3–13.4 (13.3) | 1 | 65.5–73.0 (69.5) | 12.1–18.0 (13.9) | 67.1–77.4 (75.5) |
|  | after | 1 | 14.0–14.9 (14.2) | 0–1 (0) | 47.9–60.7 (52.5) | 7.2–9.1 (9.0) | 57.1–63.5 (59.6) |
| Settings, a session's results | main | 11–21 (15) | 43.6–99.5 (44.3) | 1–3 (1) | 44.1–81.0 (53.8) | 5.5–12.0 (9.3) | 44.7–58.7 (54.8) |
|  | before2 | 10–20 (15) | 42.7–97.4 (42.8) | 1–3 (1) | 60.2–119.0 (80.2) | 5.7–22.7 (15.5) | 54.9–77.5 (76.3) |
|  | after | 10–17 (14) | 43.8–98.6 (44.1) | 1–2 (1) | 63.4–75.4 (64.3) | 8.1–17.6 (13.8) | 61.2–78.5 (67.9) |
| The village, a move | main | 2–30 (2) | 15.2–75.2 (16.2) | 1–2 (1) | 43.9–82.2 (79.9) | 15.3–40.4 (19.8) | 93.2–141.9 (103.0) |
|  | before2 | 1–30 (1) | 12.9–75.1 (13.4) | 0–3 (0) | 65.2–114.1 (93.0) | 18.2–27.0 (22.3) | 88.0–108.9 (107.1) |
|  | after | 1–12 (1) | 15.2–71.1 (15.3) | 0–2 (1) | 41.0–75.8 (47.6) | 8.9–23.7 (21.2) | 67.3–120.6 (102.7) |
| Settings, ten minutes on | before2 | 24–31 (24) | 19.5–75.5 (20.1) | 1–2 (1) | 78.0–100.8 (87.9) | 7.2–31.7 (7.3) | 54.5–91.5 (62.5) |
|  | after | 9–12 (9) | 15.4–70.3 (16.0) | 0–1 (1) | 43.7–61.6 (47.6) | 5.0–16.8 (12.4) | 48.1–67.8 (56.6) |
| Settings, a session's results, ten minutes on | before2 | 24–30 (24) | 46.1–101.3 (46.5) | 1–3 (1) | 100.4–138.6 (109.6) | 6.0–14.1 (9.4) | 57.2–71.0 (68.2) |
|  | after | 11–24 (14) | 42.6–97.2 (46.1) | 1–2 (2) | 67.5–78.3 (68.4) | 7.3–24.6 (14.4) | 53.4–70.4 (55.7) |

**What each says** (each row's requests and its reading's log line, `home-return-requests.txt`):

- **A Back that changed nothing** is one request, on main already: `GET /changes`, 14 to 15 MB in 6 s against Home's 7 alone.
- **A Back after a move in the village:** 2 requests on main (`/changes`, `/towns/friendship`), **1** now. After's 12 is that try's
  ten minutes coming due, as its log says, not the move; main's 30 is the same moment's whole reading (main has no log: a build
  with only the log named the ten minutes in the same row).
- **A Back after a session:** `/towns/friendship` is gone, but the row barely moves (15 to 14 in the median): most of it is the QA
  learner's forms (6 to 18 small `/forms` of a deck still filling), besides `/changes` and `/state`.
- **A Back ten minutes on, nothing changed:** 24 to 31 requests, **9 to 12 now**: `/changes`, the five towns' reads, the two
  voice reads, the day's forms cards, and the plan's three packs when it plans. 20 MB, 16 now.
- **A Back ten minutes on, after a session** (the usual Back after a long run): 24 to 30, **11 and 14 now**, the same reads and
  `/state`, `/packs`. The second try's 24 is the hour's whole reading: QA's hook adds up over the rows, past the hour, which shows
  that way works too. The allocation holds (46 MB, 97 with the plan): `/state`'s answer did change.
- **The plan's reads:** three packs are three requests now, six on main.
- **The first Back after setup** reads everything once in every run (35 to 39 requests, 103 to 105 MB; see above).

**The bridge's work** (the median of five rounds; each read asked with its ETag, one after another, all 304):

| Reading | The bridge's work |
|---|---|
| A whole reading, 23 reads | 1,386 ms (1,159 to 1,448): `/scenes` 435, `/state` 336, `/packs` 245, `/course` 99, `/towns` 44, `/grammar` 34, `/readings` 29, the rest under 16 each |
| What no version says, with `/changes`, 8 reads | 79 ms (54 to 86) |
| The same with a session's results (`/state` and `/packs` too) | about 660 ms, adding their times above |

**Frame times didn't measurably change, and these runs can't say more.** They follow the machine's load: opening the village,
which nothing here touches, took 285, 426 and 479 ms of the main thread across the three runs, and main and before2 run the same
code there. A Back without a change has its longest frame at 9 ms in the median on main and after (14 in before2), a Back from the
village 20, 45 and 25. Nothing here changes what a frame does. The main thread's messages besides the frames did come down in the
ten-minute rows (88 to 48 ms and 110 to 68 ms in the 3 s), against 70 to 53 ms for the plain Back from Settings in the same two runs.

## Tests

- The bridge's smoke, section `changes`: a version of each area, `friends` among them; `/towns/friendship` says `towns`, `friends`
  and `content`; on a tree of its own, the project's docs move nothing and the content beside them does; `Changes.value` is worked
  out only when the areas it comes from moved, its area moves only with the value, one that fails counts as none, an area said by
  value is no source of another.
- The smoke's towns section, with linked towns: the read's header at the versions now, without the village and the learner; a move
  in the village that keeps its feast moves `village` alone, a session `learner` alone; a feast held moves `friends` too, and so
  does the learner's level in the friend's language.
- `FreshTest`: ten minutes on is due, whatever the whole reading's age, and still within; an hour on, or another day, time, pair,
  learner or culture, reads everything; a reading of what no version says starts its ten minutes anew; why everything is named.
- `DayAudioVisitTest`: a plan reads each pack under way once, today's words and tomorrow's still different.
- QA's home-return (above), with its new rows, what moved and the reading's log.

**The checks on the branch merged with main `bde83009`** (`3895650a`): the bridge's `tsc` clean; the smoke **3,484 checks, all
passing** (`LANI_SMOKE_PORT=9670`, `LANI_SMOKE_DRAMA3_PORT=9688`); `tutor-export --check` 89 files match; Python `companion/tests`
142 (11 skipped) and `tests` 251, all passing; the Android unit suite (through lani-slot): **3,763 tests, 2 skipped, 0 failures**; QA on slot 2 (setup, the second
town, home-return, the crash check) all passing on that merged build, screenshots in the scratchpad's `ux-nav-perf-2/shots/`.

## Tap counts, strings, the hosted mode

- **Tap counts:** none changed (no screen, no way through the app).
- **Strings:** none new (nothing for the native check).
- **The hosted mode** (the phone's own bridge): the same `appApi`, so the same area and the same reading.

## How to use it

Nothing to do: `/changes` says `friends` with this bridge, and an app without it reads by name all the same. To see why a Back
read what it read: `adb logcat -s LaniRead:I`, or QA's home-return (above). A read that depends on a value rather than on every
change of an area: `changes.value(area, from, of)` (`changes.ts`), and name the area in the route's `areas`.

## What's left

- **Measured on the emulator**, not on Jan's phone (installs on real devices are Jan's call). `adb logcat -s LaniRead:I` on the
  phone would say which readings Jan's Backs really are.
- The five items under "What I found and left".
- From changes-areas, still open: a big deck's forms come with each deck reading (10 to 20 small requests while it fills), the
  header village's 85 KB a picture, and Back from the village's longest frame (a Perfetto trace on a quiet machine or Jan's phone).
