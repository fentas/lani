# CI reliably green (October 2026)

CI on main (`.github/workflows/ci.yml`) was red in 15 of its last 39 runs. This page gives each failure, its cause and
its fix, and the numbers of the reproductions before and after. Two of the causes were real bugs that the tests found
only on a slow runner: a content job queued twice, and `voice-build` crashing on a locked database. One was GitHub's
mirror. The rest were fixed on main before this work. No test is retried, and no test was made weaker.

## What failed

| Run (main) | Job, step | What | Cause | Fix |
|---|---|---|---|---|
| 40486785393, 2026-10-27 | Android, unit tests | `StudioTest` › "the background's run and the open app's worker never run a job at once": `IllegalArgumentException` at `node.jobs.values.single()` | **A real bug**: a chat turn resumed after a kill queued its `request_content` job a second time | [The studio's second job](#the-studios-second-job) |
| 40486785393 | Bridge, ffmpeg | cancelled after the job's 20 minutes, the typecheck and the smoke not run | The runner's Ubuntu mirror sent ffmpeg's 87 packages (63 MB) at about 50 kB/s | [ffmpeg on CI](#ffmpeg-on-ci) |
| 38240440289, 2026-10-12 | Bridge, smoke | `voice-build --narrators --run`: `SQLiteError: database is locked` | **A real bug**: the voice store set its busy timeout last | [The voice store's locked database](#the-voice-stores-locked-database) |
| 8 runs, 2026-10-19 to 10-27 | Python, `tests` | `test_db_port`: a bad UTF-8 byte, a date out of range | Deterministic: the runner's locale and Python's version | Already on main: 3a2d54af (strict UTF-8), Python 3.14 pinned |
| 3 runs, 2026-10-07 and 10-08 | Android, unit tests | the renderers' frame budgets (`SceneDetailTest`, `LodTest`, …) | A shared runner is slower than a desktop | Already on main: 1c362d7c (`LANI_PERF_SCALE=4`); none failed in the 40 runs since |
| 2 runs, 2026-10-05 | Bridge, Python | a villagers check; the Python tests without `bun` | Deterministic, the same in both runs | Already on main then |

## The studio's second job

**What happened.** A chat turn runs the tool calls of a step (`Loop.kt`), then saves their results with the thread.
The chat's `request_content` queues a studio job and saves it on the bridge right away. When the process is killed
between those two saves, the next process resumes the turn from its saved step: the calls aren't answered, so it runs
them again. Before this fix, `request_content` then queued a second job of the same brief, with a new random id, and
the studio ran both. That is the Opus calls paid twice, and a second version of the module published (the job's
`intents` keep one job from publishing twice, not two jobs).

The test's process A is cancelled at the studio's first call. On a slow runner that was often before A's chat turn
had saved its step. The new process B then queued the second job, and `single()` found two.

**The fix** (`Studio.request`, `Studio.requestId`). The job's id is made from the call: the turn's id, the tool
call's id and its input, hashed (`j_` and 12 hex digits, as the bridge's job ids are). The same call again finds its job,
in the process or on the bridge (`GET /tutor/jobs/<id>`), and is answered from it ("not queued twice" in the log). When
the bridge can't say whether the job is there, nothing is queued: a job saved again under its id would start over what
already ran.

**The tests** (`StudioTest`, 14):

- New: "a chat turn resumed after the app was killed doesn't queue its job a second time". The kill comes at the job's
  first save on the bridge (`afterPut`), so the turn's step is never saved: deterministic.
- The background/worker test, without its timing. The killed process is joined (`gone`: every coroutine it ran has
  ended) instead of slept on. The start-up's session read is held by a latch until the background's run is in the job
  (it was a 400 ms sleep), so the open app's worker always starts during that run. The job's calls in flight are counted:
  never two at once.
- The other kill and event tests wait for their condition for up to 30 s (`awaitUntil`) and join the killed process,
  instead of fixed polls of 2 to 5 s and sleeps.

**The numbers.** One test method run N times in one JVM (a scratch JUnit test calling
`JUnitCore().run(Request.method(StudioTest::class.java, name))` in a loop, not committed). It runs under
`./gradlew --no-daemon :app:testDebugUnitTest --rerun --tests <it>`, with Gradle pinned by `taskset -c` to two cores
(CI's runner has four), or to one:

| | Not pinned | 2 cores | 1 core |
|---|---|---|---|
| Before: the test and the code as on main | 0 of 40 failed | **20 of 60 failed**, each with two `request` jobs, the second queued by B | |
| The new test, without the fix | **20 of 20 failed** | | |
| After: the new test | 0 of 20 | 0 of 100 | |
| After: the background/worker test | 0 of 20 | 0 of 100 | |
| After: each of the 14 `StudioTest` methods | | | 0 of 30 each |
| The background/worker test with the run lock taken out (`Studio` before 20434a01) | **20 of 20 failed** | **20 of 20 failed** | |

The last row shows the deterministic test still proves what it was written for, more strongly than before: before, it
saw eight calls without the lock and five with it.

The app's whole unit test suite passes with the fix (3,069 tests, none failed).

The same kind of resume may be left elsewhere: `report_problem` with a GitHub sign-in keeps a pending report under a
random id, so a turn resumed there would ask the learner twice. That costs no model call; it is left for later.

## The voice store's locked database

**What happened.** `VoiceStore` opens `voice.db`, sets `journal_mode = WAL`, makes and migrates its tables, and only
then set `busy_timeout`. Another connection that holds the database alone for a moment (it makes the file, turns it to
WAL, or recovers its WAL as it opens) made the opener fail at once with `SQLITE_BUSY` instead of waiting. On CI it was
`voice-build` during the smoke. On a node it is `voice-build` started while the bridge starts, or two of them at once.

**The fix.** `busy_timeout` (5 s) is the first statement after the open.

**The numbers.** A second bun process holds a new database (`BEGIN EXCLUSIVE`) for 1.5 s while the store opens it:
before, 3 of 3 opens failed within 1 ms (`SQLITE_BUSY`); after, 3 of 3 waited about 1,530 ms and opened. A store already
in WAL with a writer opened at once both times. The smoke's voice section has this as a check now: it failed before the
fix (`SQLITE_BUSY` after 11 ms) and passes after, with the whole smoke (2,722 checks).

## ffmpeg on CI

**What happened.** The step was `sudo apt-get update && sudo apt-get install ffmpeg`. On 2026-10-27 the update took a
second, and then the runner's mirror (`azure.archive.ubuntu.com`) sent the packages at about 50 kB/s: libcodec2's 9 MB
took three minutes. After 20 minutes apt was still unpacking, and the job's time limit cancelled the run. apt wasn't
hung, so its own retries and connection timeouts wouldn't have caught it.

**Now** (`.github/scripts/ffmpeg.sh`, the Bridge job in `ci.yml`):

- Ubuntu's packages are kept between runs in `~/.cache/lani/apt-ffmpeg` (`actions/cache` restore and save, apt's
  `Dir::Cache::Archives`). A run with the cache downloads nothing. apt checks each cached package against the signed
  lists, so a stale one is downloaded again.
- What the cache lacks is downloaded in bounded tries: `apt-get update` 2 × 45 s, the packages 2 × 90 s, with
  `Acquire::Retries=3`. A try that is cut off keeps what it fetched, and the next one goes on from there.
- A run saves the cache under its own key (the runner image and the run id) whenever it fetched something, so a run
  that got only part of the packages leaves them for the next. The restore takes the newest cache of this runner
  image, else of an older one.
- The packages are installed from that directory with `--no-download`: dpkg never runs with the network, and no
  timeout stops it half-way.
- If ffmpeg still isn't there, the job goes on. The smoke skips its voice checks that need ffmpeg (about 30). The step
  writes a warning, and a "⚠️ ffmpeg not installed" section in the job's summary that says why. An ffmpeg that is there
  but doesn't run is removed, so the smoke doesn't run the checks with it. The step's own limit (7 minutes) is only a
  backstop; then the next step (`ffmpeg.sh --cut-off`) says so in the summary.

A slow mirror now costs a run at most about five minutes and the voice checks, never the run. Tried in `ubuntu:24.04`
containers:

| Case | Result |
|---|---|
| No cache | installed: 198 packages there (87 on the runner), 133 MB |
| From the cache | installed, "Need to get 0 B/133 MB of archives" |
| The mirror at 50 kB/s (apt's `Dl-Limit`), tries of 15 s and 30 s | not installed, exit 0, the warning and the summary; 6 then 12 packages kept, the second try going on from the first |
| `--cut-off` with an ffmpeg that doesn't run | removed, the summary written |

shellcheck and actionlint report nothing.

## A time limit on each step that downloads

A service that hangs now fails its step in minutes, with the step's name, and doesn't take the job's whole time. Each
limit is well above the step's time in the October runs, and under the job's limit.

| Job | Step | October | Limit |
|---|---|---|---|
| all | checkout, setup-bun, setup-python, `bun install` | 0–4 s | 5 min |
| Bridge | ffmpeg (above) | 17–28 s | 7 min, and goes on |
| Bridge | typecheck; smoke | 0–4 s; 103–157 s | 5 min; 10 min |
| Android | the Gradle cache's restore | 16–74 s | 8 min, `SEGMENT_DOWNLOAD_TIMEOUT_MINS=5`, and goes on (a slower build, not a failed one) |
| Android | unit tests and the debug APK (with the build image) | 13–23 min | 32 min |
| Android | the release APK with R8 | 6–11 min | 20 min |
| Android | the artifacts' uploads | 0–4 s | 5 min |

`android-release.yml` has the same for its checkout, its cache, its build (25 min) and its artifacts.

**Not changed:** the build image's apt (4 small packages from `archive.ubuntu.com`, 8 s) and its pinned downloads (the
SDK, bun). The step's limit bounds them, and a retry written into the Dockerfile would change the Gradle cache's key.
