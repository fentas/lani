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
areas with `/changes` by name, so an app already installed gets this with the bridge. A friend's own news (their trouble,
their feast, their counts) comes as `/towns`'s always has: with a write in the towns' files (a guest, a question, help
sent), an event, the ten minutes below, or a new day. `GameController.sync` still reads the friendships each time the
village opens, as before.

### Ten minutes on, a Back reads what no version says, not everything (the app)

The per-area versions only helped within ten minutes of the last whole reading: after them **any** Back read everything
(about 30 requests, 75 MB for the phone's collector, 1.4 s of the bridge's work). A run of exercises often takes longer than
ten minutes, so the usual Back after a session didn't use the areas at all: the brief's "next Back builds every answer"
was still true where it matters most.

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
- **`/state` is a Python process per read** (`read-db.py`: 336 ms of the whole reading's 1.4 s here). The biggest single cost
  of a Back after a session; caching its answer by the learner's version would take it out.
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
emulator draws in software and the machine was shared with six other agents' builds and QA runs all day, so frame times are
their load as much as the app's: the requests and the allocations hold whatever the load.

The bridge's side, on the same learner with the second town linked (a scratch script, not committed): each of Home's reads
asked as the app asks it, with its ETag, one after another, five rounds: the median of the rounds.

## Before and after

RESULTS

## Tests

CHECKS

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
- The four items under "What I found and left".
- From changes-areas, still open: a big deck's forms come with each deck reading (10 to 20 small requests while it fills), the
  header village's 85 KB a picture, and Back from the village's longest frame (a Perfetto trace on a quiet machine or Jan's phone).
