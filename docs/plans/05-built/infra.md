# Tests and QA that don't get in the way (October 2026)

The follow-ups round's `infra` part. Agents queued for one emulator, the unit suite failed for reasons that had
nothing to do with the change under test, and a few QA steps were wrong. On the same day the tmux server died with
the terminal it ran in, and both learners' bridges and tutors stayed down for hours. This page is what changed, why,
how to use it, and what is left.

## The command for agents

```bash
companion/bin/qa-slot [qa's options …]          # e.g. companion/bin/qa-slot --steps visit; LANI_DEV_DATA=… companion/bin/qa-slot --no-town --steps coast
companion/bin/qa-slot --status                   # who holds each slot, who waits, the memory
```

It replaces `flock /tmp/lani-emulator.lock companion/bin/qa …`, which still works (it waits for slot 1, as before).
Don't wrap qa-slot in `lani-slot.sh`: qa-slot gates its own build, and the outer heavy slot would stay taken while it
waits for an emulator.

## A second emulator slot

`companion/bin/qa-slot` runs `companion/bin/qa` on the first free of two slots. A slot is an emulator with its own
lock and ports, so two QA runs go at once:

| Slot | Lock | Emulator | Dev bridge | Second town | Tutor mock | Setup's unreachable port |
|---|---|---|---|---|---|---|
| 1 | `/tmp/lani-emulator.lock` | `emulator-5554`: the QA AVD, left running between runs | 8791 | 8797 | 8771 | 19601 |
| 2 | `/tmp/lani-emulator-2.lock` | `emulator-5556`: a second, read-only instance of the same AVD, started for the run and stopped after it | 8781 | 8787 | 8761 | 19611 |

**How a run goes.**

1. **The build first**, before any emulator is waited for. Twelve runs were queued on 2026-10-27, some holding the
   emulator for 20 to 30 minutes, part of it their Gradle build. Unless `--no-build` or `--apk` is given, qa-slot runs
   `qa --build-only` once 14 GB are available (`LANI_QA_MIN_GB`), in one of the machine's heavy slots
   (`/tmp/lani-heavy-slot1.lock` to 3: lani-slot.sh's, `LANI_HEAVY_LOCKS`), without a Gradle daemon
   (`LANI_QA_GRADLE_NO_DAEMON=1`). Inside a heavy slot already, it builds in that one. Then qa runs with `--no-build`.
2. **Turns.** Each waiting run has a ticket in `/tmp/lani-qa-queue` (when it came, which slots it can use). A run takes
   a slot only when no run that came earlier waits for that slot; a dead run's ticket is removed. No lock is held while
   waiting. Slot 1 is tried first (its emulator runs already), then slot 2, only while 14 GB are available. A run
   inside slot 1's lock already (`flock /tmp/lani-emulator.lock companion/bin/qa-slot …`: an ancestor holds the lock's
   file open) takes slot 1 without waiting for itself.
3. **The run.** qa gets the slot's `ANDROID_SERIAL`, `LANI_DEV_PORT`, `LANI_DEV_TOWN_PORT`, `LANI_TUTOR_MOCK_PORT`,
   `LANI_QA_NOBODY_PORT` and `LANI_QA_SLOT` (slot 2 also `LANI_QA_READ_ONLY=1`). Its screenshots go to
   `companion/qa-shots/<time>/` (slot 2: `<time>-slot2/`).
4. **After it**, also after Ctrl-C, a timeout's SIGTERM (passed on to qa as a Ctrl-C, so qa stops its bridges) or a
   failure: slot 2's emulator is stopped (`adb emu kill`, then its processes), the slot's dev bridges that qa left
   listening are stopped (only processes with the slot's `LANI_QA_SLOT` in their environment), and the lock is freed.
   The exit code is qa's; 75 when no slot came free within `LANI_QA_WAIT_S` (6 hours).

**Slot 2's emulator.** qa starts an emulator that isn't running on its serial's console port (`-port 5556`), read-only
(`-read-only`: its changes go to temporary files and are thrown away) when another instance of the AVD runs or
`LANI_QA_READ_ONLY=1`, its output in the run's `emulator.log`, and waits up to 420 s for its boot
(`LANI_QA_BOOT_S`). Before a run, an emulator on 5556 that a dead run left (its marker, `/tmp/lani-emulator-2.lock.run.json`,
names a process that is gone) is stopped; one that no qa-slot started is left alone, and slot 2 isn't used then.

**What changed in qa for it.**

- `ANDROID_SERIAL` defaults to `emulator-5554`. With two emulators running, adb without `-s` refuses ("more than one
  device/emulator"), which would have broken every `flock … companion/bin/qa` the moment slot 2 ran.
- `--build-only`, `LANI_QA_GRADLE_NO_DAEMON`, `LANI_QA_BOOT_S`, `LANI_QA_READ_ONLY`, `LANI_QA_SLOT`.
- `shadow-tutor` taps the panel's "The mock (10.0.2.2:8771)" as before, then types the slot's mock over it (the app's
  button names slot 1's port). The other mock steps set it through QA's hooks already.
- qa and qa-slot refuse the adb server on 5038 (a real tablet's).
- The bridge reserves 8781 and 8787 (`src/learners.ts` `RESERVED_PORTS`), and `test/dev-town.ts` serves on either
  slot's town port (`QA_TOWN_PORTS`).

**Tests.** `companion/tests/test_qa_slot.py` (12): the slots' ports (none shared, none a learner's or a worker's, slot
2's reserved in the bridge), the build rule, the locks (one another process holds isn't taken, one an ancestor holds
is ours), the queue's order and a dead run's ticket, slot 1 first with its ports, slot 2 while slot 1 is held (its
emulator stopped after, its marker gone, both locks free again), no slot within the wait (75, nothing run, the
ticket gone), an emulator nobody here started keeping slot 2 closed, the build before the run (`--build-only
--minified`, no daemon, then `--no-build`), never 5038.

## The flaky unit tests

### MyWordsSearchTest

**Cause.** Timing, not shared state. The first test typed "mačka" with a debounce of 30 ms, then waited 150 ms with
`delay` under `runBlocking`, and expected the lookup to have run. The debounce's coroutine is only queued by `type()`;
it calls its own `delay(30)` when the event loop first runs it, after the test's `delay(150)` was scheduled. If the
thread stalls for more than 120 ms in between (a garbage collection or a page fault on a machine at a load of 65 with
its swap full, which happens in the full suite and not in a fresh JVM), the debounce is due after the test's wait,
and the test finds nothing asked. A scratch test that stalls the thread once for 200 ms before the debounce starts
fails the same way, every time (`expected:<[mačka]> but was:<[]>`).

The suspect named in the brief, Compose's global snapshot after the Robolectric tests, doesn't reach this test:
Robolectric loads its own copy of the Compose runtime in its sandbox's class loader, and this plain JUnit test reads
and writes its states on one thread in the global snapshot.

**Fix.** The test runs on `runTest`'s virtual time (`kotlinx-coroutines-test`, now declared as a test dependency: it
came only through Compose's test library before). The debounce and the test's waits are on one clock that only the
test moves, so no stall can reorder them. The first test now also checks the debounce exactly: nothing asked after
29 ms, the lookup at 31.

### Fixed ports

**Cause.** `PhoneBridgeNodeTest` started its node bridge on 127.0.0.1:19110. With two suites running at once on the
machine, the second one's bridge couldn't bind, and its `/health` (public, no token) was answered by the first
suite's bridge, so its tests talked to a bridge with another token and data. The same was true of seven more servers
in the suite: `ChatGPTAuthTest`'s loopback (19401 to 19408), `ShadowTutorTest`'s sign-in loopback (19420),
`GeminiTest` (19440), `OpenRouterTest` (19450), `DiscussionsTest` (19620, 19621, 19625) and `BatchesTest` (19622,
19623, 19626).

**Fix.** Every one of them takes a port the system picks:

- `PhoneBridgeNodeTest`: a free port (`ServerSocket(0)`), then the bridge on it; up means alive and answering an app
  route with this test's token (`/culture`), not just `/health`. If something takes the port first and the bridge
  exits, it tries another one, three times.
- The MockWebServers start on port 0 (`server.port` after); the "nothing listens there" ports are a free port closed
  again; the sign-in loopbacks begin with port 0, as the app does, and the tests read the port from the redirect.

### The frame budgets

**Cause.** The renderers' budget tests measured the wall clock. At a load of 33 to 46 on this machine's 24 hardware
threads, a thread waits for a CPU for part of every frame: `SceneDetailTest`'s kitchen at detail 3 took 25.8 ms
against its 25 ms budget, the Alps at detail 3 25.3 ms, while the same frames take 18 to 20 ms (the run's other arts).
A measurement at a load of 40 (scratchpad `infra/Calib.java`, a pixel loop): the wall clock 4.1 to 7.4 ms a round, the
thread's CPU time 3.8 to 4.7 ms.

**Decision.** The budgets measure the CPU time of the rendering thread (`ThreadMXBean.getCurrentThreadCpuTime`, ns on
Linux), the best of three rounds of the same frames (`FrameBudget.ms`, `FrameBudget.best`, `FrameBudget.cpuMs`). A
calibration render in the same run was the other option. It needs a reference time per machine, and its own noise
adds to the measurement's. CPU time drops what doesn't belong to the renderer: the time spent waiting for a CPU, and
garbage collection pauses spent at a safepoint. The best round drops a round that a page fault or the JIT slowed. A
renderer that got slower raises every round's CPU time, so the budgets still catch it, and at rest the numbers are
the same as the wall clock's: no budget was raised. Where a test measured 3n frames, it now measures n frames three
times, the frames spread over the same stretch of time, so the suite takes as long as before. `LANI_PERF_SCALE` stays
for slow CI runners.

Every budget test uses it: SceneDetailTest and the 20 others that call `FrameBudget.scaled` (the list:
`grep -rl FrameBudget companion/android/app/src/test`), and two that measured the wall clock against a fixed number:
WildlifeTest's cost of the night's animals (1.5 ms a frame, now scaled too) and StickersTest's sticker (120 ms).

**Close to its budget for real.** With the noise gone, SceneDetailTest's kitchen at detail 3 takes 23.2 ms of CPU time
(24.2 ms in all the weather) against its 25 ms: that is the kitchen's own cost, not the machine's. The other arts at
detail 2 and 3 take 9 to 20 ms.

## QA steps

- **Home, found in the pair the phone shows** (`is_home`, `wait_home`). Home was any label holding "Danes · Today".
  A villager's page has "📅 Danes · Today", Napredek has "Danes · Today: 5 kartic", the village HUD's days and the tutor
  chat's day divider (new with the chat's archive) say it too. `is_home` is the Today card's heading exactly, in the
  pair the phone shows (`pair_now`: `LANI_QA_PAIR`, else the setup's pair), with neither the village nor the tutor
  chat over it. `go_home`, `plain_village`, the waits after a restart, the hosted steps and `on_home` use it.
  `go_home` and `village()` find Home's "Moja vas" and the HUD's scroll in that pair too: a non-Slovene course's
  ("Oggi · Heute", "Il mio villaggio") works.
- **The coast step's "open village"** failed after Nonna Rosa's talk. With the pair back in sl · en, plain_village
  saw her page's "📅 Danes · Today", took it for Home and pressed "Moja vas" there. With `is_home` it goes back to the
  village.
- **visit-home**: "⌂ Home" on a visit goes back to where the visit began (the back stack, navigation.md): Friends,
  with the visited town listed and no banner. The step checks that, then goes on to the own village.
- **The dev bridge's role-play tutor** answered every talk in Slovene. It now answers in the course's language (the
  culture pack's: Italian for Friuli), with the translation in the learner's base (German for Teo), and a visit's talk
  in the visited town's language. Slovene from English stays word for word. Checked on a dev bridge of its own for a
  synthetic Italian learner from German.

## voice-build on a profile's data

`voice-build --data <a profile's data>` took the culture from `LANI_CULTURE` only, so Teo's data played primorska in
Slovene, and the tutor's Italian grammar pages were skipped ("a page of "it", this book is "sl""). `cultureOf`
(`src/learners.ts`) resolves it as the learner's bridge gets it: `LANI_CULTURE` when set; else the registry entry
whose data directory it is; else the pack of the language the course teaches (as the phone picks one); else the
default. The plan names the village and its language. Smoke: course-speech's "voice-build --data of another learner".

## The watchdog

**What happened.** On 2026-10-30 the tmux server died when the terminal it was started from closed: it lived in that
terminal's systemd scope (`app-…-xdg-terminal-exec-….scope`). Both learners' bridge services (`lani-bridge`,
`lani-bridge-teo`) and tutor sessions (`lani`, `lani-teo`) went with it, for hours, and nothing noticed.

**What changed.**

- `companion/bin/lani-watchdog`, a round every 3 minutes from a systemd user timer (`--install`; the units in
  `companion/systemd/`). The table of what it does is in companion/README.md, "The watchdog": a bridge service that
  is down is started, a tutor session that is gone (or stale: there over 10 minutes, the tutor detached for 6) is
  started again with `lani-session --restart --profile <id> --now`. Nothing starts below 4 GB available, for 120
  minutes after a stop on purpose, or while paused. Its log: `~/.local/state/lani/watchdog.log`.
- Stops on purpose: `lani-session --stop [--profile <id> | --all]` (new) and `lani-bridge stop` leave a hold mark in
  `~/.local/state/lani/held/`; a start by hand removes it.
- The root cause: a tmux server that doesn't run yet starts in a systemd scope of its own (`systemd-run --user
  --scope`), from `lani-session` and `lani-bridge start`, so it outlives the terminal. A server the watchdog's round
  starts outlives the round the same way (the round is a oneshot service whose leftover processes systemd stops).

**Linger.** It is off on this machine (`loginctl show-user fentas -p Linger`: no). User timers run only while Jan is
logged in, so the watchdog stops at a logout and comes back at the next login. `loginctl enable-linger` would keep it
running, and the tmux server and services with it. That is Jan's decision; nothing here turns it on.

**Not installed here.** The lead installs it after merging: `companion/bin/lani-watchdog --install`, then
`companion/bin/lani-watchdog --status`.

**Tests** (`companion/tests/test_watchdog.py`, 16, never the real services): the decisions as a pure function (all up:
nothing; down: the bridge first, then the tutor; stopped on purpose: nothing until the hold ends; the bridge stopped on
purpose: neither; low memory: waits, says what; paused; a detached tutor's grace: its young session, then 6 minutes,
then a restart; the classic mode); rounds of the real script against fake `lani-profile`, `lani-bridge`,
`lani-session` and `tmux` (the server died: everything back, then a round that starts and logs nothing; a hold said
once; low memory then enough; a failed start; pause and resume; a dry run; `--install` twice, the second rewriting
nothing); `lani-session --stop` against a fake tmux.

## The numbers

(filled in at the end of the round)

## What's left

(filled in at the end of the round)
