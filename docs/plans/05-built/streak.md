# Built: the streak, one honest number with freezes (streak, 2026-11-10)

Jan (2026-11-10): "this morning i had still 46; agent is talking about keeping the streak - then I did vocab and reset to
1; I guess I really missed a day; but I would say wrong behaviour here. 1. can we fix it 2. maybe can we have gotchas to
keep a streak (ala freeze day - or similar)".

## What was wrong

The streak was a stored counter, `learner_profile.current_streak_days`, changed only at a session's end (update-db.py and
its TypeScript port: +1 after yesterday, else 1). Everything that showed it read the stored number: Home's pill and
"Danes · Today", the widget, the evening's notification, the tutor's context and the rhythm's snapshot, the session hooks.
So after a missed day every one of them still said the old number, the evening nudge said how to "keep the streak" that
had already ended, and the next session dropped it to 1. Meanwhile the progress screen (Stats.kt) and a linked town's
progress (progress.ts) each counted their own streak from the practice days, so two screens could disagree.

## What was built

**One function, three implementations, one fixture.** The streak is a pure function of the session log's days and
today (the learner's day: read-db's `computed.today`):

- `companion/android/app/src/main/java/si/lanisce/lani/data/Streak.kt` (`Streak.of`, `Streak.ofDates`, `Streaks`),
- `companion/bridge/src/streak.ts` (`streakOf`, `dayOf`, `computedStreak`, `keptToday`, `frozenDays`; the database port's
  side in `src/db/streak.ts`),
- `.claude/hooks/lani_streak.py` (`streak`, `day_of`, `log_dates`, `of_log`, `computed`, `line`),

kept equal by `companion/bridge/test/fixtures/streak-cases.json` (21 histories with their answers, written by hand: the
generator only did the date arithmetic). StreakParityTest (app), the smoke's `streak` section (bridge) and
tests/test_streak.py (scripts) read it. A session's day is read alike on all three sides: its date as Python's
`strptime("%Y-%m-%d")` reads one ("2026-4-24" too), else the date a timestamp starts with ("2026-11-08T21:30:00");
anything else (a date that doesn't exist, year 0, not a string) is no day.

**The rule as implemented.**
- A run counts its practised days. Today is open, never a miss.
- Freezes: 1 to start; every 7th practised day of a run earns 1 more (7th, 14th, 21st …), at most 2 held.
- A day with no practice before today is covered by a held freeze, spent by itself. A covered day keeps the run and adds
  nothing to its count.
- A gap longer than the freezes held ends the run and spends none (also the gap still open before today: while it is
  covered the freezes show as spent; once it is too long the run is over and they are back).
- Nothing is stored: the rule holds for every past gap, for every learner alike. `best` is the longest run under it.
- The answer: `current`, `best`, `practised_today`, `freezes` (held now), `covered` (every day a freeze covered),
  `since` (the current run's first day), `today_covered` (a run going on, today open, a freeze held: a miss today would
  be covered), `last_run` (the run that ended last), `to_next_freeze` (practised days to the next freeze, 0 while 2 are
  held). read-db's `computed.streak` keeps `covered` to this run's days.

**How much the longest streak can gain.** Applied to every past gap, a run joins the next one across a gap of one or two
days whenever the freezes held then cover it, so `best` can only grow. There is no fixed bound in days: a learner who
missed one day after every 7th practised day never broke a run, and their best is every practised day (the old rule:
7). But a freeze must be earned: only the starter freeze is there without a week of practice, so a learner who practises
every other day joins just their first two days (case 20). Fixture case 19: runs of 5 and 8 a day apart are 13 (8
before). Jan's own number is the lead's to check after the release: the rule treats the 11-08/11-09 gap as anyone's.

**The fixture's cases** (today 2026-11-10):

| # | Days up to today (x practised, . not; the last one today) | current | best | freezes | covered | today_covered | last_run |
|---|---|---|---|---|---|---|---|
| 1 | `(none)` — no practice yet: no streak, the starter freeze held | 0 | 0 | 1 | – | no | 0 |
| 2 | `x` — only today | 1 | 1 | 1 | – | no | 0 |
| 3 | `x.` — yesterday practised, today open: today is never a miss | 1 | 1 | 1 | – | yes | 0 |
| 4 | `xxx` — three days in a row ending today | 3 | 3 | 1 | – | no | 0 |
| 5 | `xx.x` — one missed day is covered by the starter freeze: it keeps the run and adds nothing | 3 | 3 | 0 | 11-09 | no | 0 |
| 6 | `xx..` — the morning after a covered day: the run kept, no freeze left for today | 2 | 2 | 0 | 11-09 | no | 0 |
| 7 | `xxx...` — two missed days with one freeze end the run, and the freeze is kept | 0 | 3 | 1 | – | no | 3 |
| 8 | `xxx..x` — a new start after a run that ended | 1 | 3 | 1 | – | no | 3 |
| 9 | `xxxxxxx.` — the 7th practised day of a run earns a second freeze | 7 | 7 | 2 | – | yes | 0 |
| 10 | `xxxxxxx` — a freeze earned today counts today | 7 | 7 | 2 | – | no | 0 |
| 11 | `xxxxxxxxxxxxxxxxxxxxx` — at most two freezes are held | 21 | 21 | 2 | – | no | 0 |
| 12 | `x×46..x` — 46 days, two missed, today practised: two freezes cover the gap | 47 | 47 | 0 | 11-08, 11-09 | no | 0 |
| 13 | `x×46...` — 46 days, two missed, today open: the run kept by two freezes | 46 | 46 | 0 | 11-08, 11-09 | no | 0 |
| 14 | `x×46....` — 46 days, three missed: the gap is longer than the freezes, the run ends and both are kept | 0 | 46 | 2 | – | no | 46 |
| 15 | `xxxxxxx...xx` — a gap longer than the freezes held ends the run in the middle too, spending none | 2 | 7 | 2 | – | no | 7 |
| 16 | `xxxxxxx..` — one freeze left after a covered day: a miss today would be covered | 7 | 7 | 1 | 11-09 | yes | 0 |
| 17 | `xxxxxxx.xxxxxxx..x.` — spent and earned again: the 14th practised day earns one back | 15 | 15 | 0 | 10-30, 11-07, 11-08 | no | 0 |
| 18 | `xxx..xx.x` — a freeze kept from a run that ended covers a gap in the next one | 3 | 3 | 0 | 11-09 | no | 3 |
| 19 | `xxxxx.xxxxxxxx.....` — old runs join across a covered day: the longest streak counts under the same rule | 0 | 13 | 1 | 10-28 | no | 13 |
| 20 | `x.x.x.x.x.x.x` — every other day: the starter freeze joins the first two, then nothing is left to cover | 1 | 2 | 0 | 10-30 | no | 1 |
| 21 | `the session log as it is: "2026-11-10", "2026-11-09", "2026-11-10", "2026-11-11", "2026-11-08T21:30:00", "2026-11-7", "garbage", "2026-02-30", "0000-01-01", "", null, 42` — the session log as it is: duplicates, any order, a time after the date, an unpadded date; a later day, a date that doesn't exist and what isn't a date are left out | 4 | 4 | 1 | – | no | 0 |

## Every reader, changed

| Reader | Where | Now |
|---|---|---|
| Home's 🔥 pill | `ui/home/HomeHero.kt` (Pill) | `Dashboard.streak` = the derived current; TalkBack adds the freezes; pulses only while today is open and no freeze would cover it |
| "Danes · Today" | `ui/home/HomeLogic.kt` `streakLine`, `ui/home/TodayCard.kt` | the line by state (below) |
| The dashboard | `data/Models.kt` `Dashboard.parse` | `streak`, `streaks` (the whole answer) and `today` from the session log; never `current_streak_days` |
| Progress (Napredek) | `data/Stats.kt` `parse`, `ui/progress/ProgressScreen.kt` `StreakCard`, `ui/progress/Charts.kt` `Heatmap` | the derived current and best, the freezes' line, "a freeze would cover today", a covered day as ice |
| The widget | `app/ContentController.kt`, `data/BackgroundCheck.kt` `widget` | `d.streak` (derived) |
| The evening notification | `data/NotifyChannels.kt` `Notices.reminder`/`streakNudge`, `data/BackgroundCheck.kt` `eveningReminder` | says only what is true |
| "Practised once" (first-run hints) | `AppViewModel.kt` `practisedOnce` | the session log's days, not the streak (a streak that ended is 0) |
| A linked town's week dots | `data/TownProgress.kt` `ProgressWeek.frozen`, `ui/towns/TownProgressScreen.kt` `SummaryCard` | a covered day as ❄️ |
| Town progress (bridge) | `bridge/src/progress.ts` `progressOf`, `cleanProgress`, `weeklyFacts` | the derived streak, `week.frozen` |
| The rhythm's snapshot | `bridge/src/features/rhythm.ts` `snapshot` | `streak` (computed.streak) instead of the stored `streak_days` |
| The evening ask | `bridge/src/rhythm.ts` `routineAsk.evening` | only what the snapshot's streak says |
| The tutor's context | `bridge/src/features/tutor.ts` `learnerSummary`, `tutor/Reads.kt` `Summaries.learnerState` | `computed.streak`; read-db's `current_streak_days` is today's derived one |
| read-db | `.claude/hooks/read-db.py` `computed`, `main`; `bridge/src/db/read-db.ts` | `computed.streak`, `streak_active` = current > 0, the profile's `current_streak_days` in the output = today's derived |
| update-db | `.claude/hooks/update-db.py` `update_learner_profile`; `bridge/src/db/update-db.ts` | writes `current_streak_days` and the session's `streak_day` as the derived streak with this session's day |
| The hooks | `.claude/hooks/session-start.py`, `session-end.py` | "🔥 Streak: 46 days · ❄️ 1 freeze held (a miss today would be covered)" |
| The channel guide, skills | `features/rhythm.ts` instructions; `.claude/skills/lani-progress`, `lani-learn`, `lani-db-updater`; `companion/tutor/app-tools/learner_state.json`; `companion/tutor/` regenerated | the derived streak, never the profile file's counter |

## What the learner sees

- **Home's pill**: the derived number (🔥 10 the morning after a covered day, 🔥 0 after a run that ended).
- **"Danes · Today"**, one line:
  - practised today: "🔥 47 dni zapored · 47-day streak ✓" (with "· ❄️ 1" while a freeze is held);
  - the morning after a covered day (the gap still open, covered; shown that day only, gone once today is practised):
    "❄️ Zamrznitev je ohranila niz 10 dni — še 1 · A freeze kept your 10-day streak — 1 left";
  - a streak going on: "🔥 Vadi danes za 47. dan · Practise today for day 47" (with "· ❄️ 1");
  - after a run that ended: "🔥 Začni nov niz — prejšnji: 15 dni · Start a new streak — the last one: 15 days", never
    "keep your streak"; with no run before, "🔥 Začni nov niz · Start a new streak".
- **The evening notification** (an adult's, cards due, nothing practised today): "a freeze would cover today — 5 minutes
  make it day 47", "5 minutes today keeps your streak" (no freeze left), or "5 minutes today start a new streak". A
  child's says no streak, as before.
- **Napredek's streak card**: the current and the best; "⏳ … zamrznitev bi pokrila današnji dan · … a freeze would cover
  today" or "… 5 minut ohrani niz · … 5 minutes keeps the streak"; one line on the freezes, "❄️ 1 od 2 zamrznitev — nova
  vsakih 7 dni zapored, še 4 · 1 of 2 freezes — one more every 7 days in a row, 4 to go" (or "❄️ 2 od 2 zamrznitev —
  vsaka sama pokrije dan brez vaje · 2 of 2 freezes — each covers a day without practice by itself"); the calendar draws a
  covered day in ice blue with a white flake (its day: "❄️ pokrila zamrznitev · covered by a freeze"), the legend an ice
  square.
- **A linked town's progress**: the week's dots show a covered day as ❄️ on ice blue, not as practice ✓; TalkBack says
  how many days a freeze covered.
- Taps: none added. The streak is on Home (0 taps) and Napredek (1 tap), as before.

Screenshots: see "Checks" below.

## Older apps (Teo's tablet, 0.2.698)

An older app reads `learner_profile.current_streak_days` from GET /state for Home's pill, "Danes · Today" and the widget.
read-db (both implementations) now gives that field as today's derived streak, so as soon as the node runs this version
an older app shows the honest number too, in its own words: "🔥 Vadi danes za 11. dan" the morning after a covered day,
"🔥 Začni nov niz" after a run that ended (it shows no ❄️ line, no freezes). Its progress screen keeps its own old count
(the session log's days in a row, no freezes), so there a covered day still reads as a break until the app is updated. It
ignores `computed.streak` and a linked town's `week.frozen` (unknown fields). update-db keeps writing
`current_streak_days` (the derived value), so the stored file stays meaningful for anything that reads it directly. An app
on the phone runtime (hosted, no node) runs its own bundled scripts, so it changes with its update.

## Native check

Machine-written by Claude in sl, en, de and it (`_note_streak` in each table). The word for a freeze above all:
"zamrznitev" (sl), "Serienschutz" (de, shown as "1 × Serienschutz" to keep one form), "congelamento" (it). Nothing is
voiced: no voice clips, 0 characters.

| Key | sl | en | de | it |
|---|---|---|---|---|
| `todayCard.freezeKept` | {n, plural, one {Zamrznitev je ohranila} two {Dve zamrznitvi sta ohranili} few {# zamrznitve so ohranile} other {# zamrznitev je ohranilo}} niz {streak} {streak, plural, one {dneva} other {dni}} — {left, plural, =0 {nobene več} other {še #}} | {n, plural, one {A freeze kept} other {# freezes kept}} your {streak}-day streak — {left, plural, =0 {none left} other {# left}} | {n, plural, one {Ein Serienschutz hat} other {# × Serienschutz haben}} deine Serie von {streak} {streak, plural, one {Tag} other {Tagen}} gehalten — {left, plural, =0 {keiner mehr} other {noch #}} | {n, plural, one {Un congelamento ha salvato} other {# congelamenti hanno salvato}} la tua serie di {streak, plural, one {# giorno} other {# giorni}} — {left, plural, =0 {non ne restano} one {ne resta #} other {ne restano #}} |
| `todayCard.freezes` | {n, plural, one {# zamrznitev} two {# zamrznitvi} few {# zamrznitve} other {# zamrznitev}} | {n, plural, one {# freeze} other {# freezes}} | {n} × Serienschutz | {n, plural, one {# congelamento} other {# congelamenti}} |
| `todayCard.startNewStreakLast` | Začni nov niz — prejšnji: {n} {n, plural, one {dan} two {dneva} few {dnevi} other {dni}} | Start a new streak — the last one: {n, plural, one {# day} other {# days}} | Starte eine neue Serie — die letzte: {n} {n, plural, one {Tag} other {Tage}} | Inizia una nuova serie — l'ultima: {n, plural, one {# giorno} other {# giorni}} |
| `progressScreen.freezesEarning` | {n} od {max} zamrznitev — nova vsakih 7 dni zapored, še {left} | {n} of {max} freezes — one more every 7 days in a row, {left} to go | {n} von {max} × Serienschutz — einer mehr alle 7 Tage in Folge, noch {left} | {n} congelamenti su {max} — uno in più ogni 7 giorni di fila, {left, plural, one {manca #} other {mancano #}} |
| `progressScreen.freezesFull` | {max} od {max} zamrznitev — vsaka sama pokrije dan brez vaje | {max} of {max} freezes — each covers a day without practice by itself | {max} von {max} × Serienschutz — jeder deckt von selbst einen Tag ohne Übung | {max} congelamenti su {max} — ognuno copre da solo un giorno senza esercizio |
| `progressScreen.notPractisedTodayCovered` | Danes še nisi {learnerGender, select, f {vadila} other {vadil}} — zamrznitev bi pokrila današnji dan | Not practised today yet — a freeze would cover today | Heute noch nicht geübt — ein Serienschutz würde den Tag abdecken | Oggi niente esercizio ancora: un congelamento coprirebbe la giornata |
| `progressScreen.coveredByFreeze` | pokrila zamrznitev | covered by a freeze | vom Serienschutz gedeckt | coperto da un congelamento |
| `backgroundCheck.freezeWouldCover` | Zamrznitev bi pokrila današnji dan — s 5 minutami bo {n}. dan | A freeze would cover today — 5 minutes make it day {n} | Ein Serienschutz würde heute abdecken — mit 5 Minuten wird es Tag {n} | Un congelamento coprirebbe oggi — con 5 minuti arrivi al {n}° giorno |
| `backgroundCheck.fiveMinutesStartStreak` | 5 minut danes začne nov niz | 5 minutes today start a new streak | 5 Minuten heute starten eine neue Serie | 5 minuti oggi iniziano una nuova serie |
| `townProgress.weekFrozen` | zamrznitev je pokrila {n} {n, plural, one {dan} two {dneva} few {dneve} other {dni}} | {n, plural, one {# day} other {# days}} covered by a freeze | {n} {n, plural, one {Tag} other {Tage}} vom Serienschutz gedeckt | {n, plural, one {# giorno coperto} other {# giorni coperti}} da un congelamento |

## Checks

- **The parity fixture on all three sides**: 21 cases. StreakParityTest (4 tests: the rule, every case, the morning
  after, a session's day), the smoke's `streak` section (10 checks), tests/test_streak.py (6 tests: the cases, the
  computed fields, a log that isn't one, the hooks' line, read-db's derived streak and the profile's overlay, update-db's
  written streak).
- **Bridge**: `bunx tsc --noEmit -p .` clean; the smoke (ports 11380-11398): all checks passed, 4622 ✅;
  `bun build --no-bundle` on the changed test files; `tutor-export --check`: 140 files match.
- **Python**: tests/ 259 tests OK (the TypeScript port against the Python byte for byte: read-db's computed streak and
  overlay, update-db's written streak); companion/tests 188 OK (11 skipped). On this host `python3` is a version
  manager's shim that writes into the tests' throwaway HOME: run them with the system python first on PATH, or 22
  port tests fail the same on main (environment, not code).
- **Android**: the full unit suite, 4561 tests, 0 failures, 0 errors (StatsTest's streaks under the new rule,
  DashboardTodayTest, ParsingTest, HomeLogicTest, NotifyChannelsTest, TownProgressTest, the l10n tables).
- **Emulator** (`companion/bin/qa-slot --slot 2 --no-town --steps streak`, LANI_DEV_DATA a synthetic learner from
  make-learner.ts): two made-up histories written into the dev bridge's copy of the session log. Ten days, yesterday
  without practice: Home "❄️ Zamrznitev je ohranila niz 10 dni — še 1 · A freeze kept your 10-day streak — 1 left", the
  pill 🔥 10, Napredek 10 / 10, "⏳ … zamrznitev bi pokrila današnji dan", "❄️ 1 od 2 zamrznitev — … še 4", yesterday as
  ice with a flake. Fifteen days with a covered day, then five without: Home 🔥 0, "🔥 Začni nov niz — prejšnji: 15 dni ·
  Start a new streak — the last one: 15 days", no "keep your streak"; Napredek 0 / 15, "❄️ 2 od 2 zamrznitev — vsaka
  sama pokrije dan brez vaje", the covered day as ice. Each on the phone (light, dark) and the tablet (2560 × 1600 at 320
  dpi, on its side and upright): 16 screenshots, ls-streak-*.png.
- A linked town's ❄️ dots: checked by the smoke (progress.ts) and TownProgressTest, not on the emulator (the second
  town's QA step needs a covered day in its fixture).

## What's left

- The legend's ice square has no flake (the cells have one): "❄️" beside it says it.
- A linked town's ❄️ dots on the emulator (above).
- An older app's own progress screen counts the old way until it is updated (above).
