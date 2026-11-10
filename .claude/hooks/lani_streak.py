"""
The streak: one number, derived from the practice days, with freezes (companion/GAME.md, "The streak").

The streak is never a stored counter that only a session's end changes: it is a pure function of the days the session
log has a session on and of today (the learner's day), so every reader says the same, the morning after a missed day as
well. The app (data/Streak.kt), the bridge (companion/bridge/src/streak.ts) and these scripts implement the same rule;
companion/bridge/test/fixtures/streak-cases.json keeps the three equal (tests/test_streak.py here).

The rule:
- A run counts its practised days. Today is open, never a miss: until the day ends, the run is yesterday's.
- Freezes: each learner starts with 1; every 7th practised day of a run earns 1 more, at most 2 held.
- A day with no practice, before today, is covered by a held freeze, spent by itself. A covered day keeps the run but
  adds nothing to its count.
- A gap longer than the freezes held ends the run and spends none: they are kept for the next run.
- Nothing new is stored, so the rule holds for every past gap too; the longest streak is the longest run under it.

read-db.py puts the answer in its computed fields ("streak"); update-db.py writes it as current_streak_days at a
session's end, for older apps that read that.
"""
from __future__ import annotations

import sys
from datetime import date, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lani_languages as langs  # noqa: E402

START_FREEZES = 1
EARN_EVERY = 7
MAX_FREEZES = 2


def day_of(v) -> str | None:
    """A session's day as the streak counts it: its date (YYYY-MM-DD, date_of's forms: "2026-4-24" too), or the date
    a timestamp starts with ("2026-11-08T21:30:00"); None for anything else."""
    if not isinstance(v, str):
        return None
    return langs.date_of(v) or langs.date_of(v[:10])


def streak(dates, today: str) -> dict:
    """The streak of the practice days [dates] (the session log's dates, as they are) on [today] (YYYY-MM-DD).

    current: the practised days of the run going on (0 when none is); best: the longest run (current included);
    practised_today; freezes: held now; covered: every day a freeze covered, oldest first; since: the current run's
    first practised day (None when none is going on); today_covered: a miss today would be covered (a run going on,
    today open, a freeze held); last_run: the run that ended last (0 when none did); to_next_freeze: the practised days
    still to go for the next freeze (0 while the most are held).
    """
    end = date.fromisoformat(today)
    days = sorted({d for d in (day_of(v) for v in dates) if d is not None and d <= today})
    freezes = START_FREEZES
    run = best = last_run = 0
    covered: list[str] = []
    since: str | None = None
    prev: date | None = None

    def gap(start: date, missed: int):
        """[missed] days without practice after [start]: covered while freezes last, else the run ends."""
        nonlocal freezes, run, last_run, since
        if missed <= 0:
            return
        if missed <= freezes:
            freezes -= missed
            covered.extend((start + timedelta(days=k)).isoformat() for k in range(1, missed + 1))
        else:
            last_run, run, since = run, 0, None

    for d in days:
        day = date.fromisoformat(d)
        if prev is not None:
            gap(prev, (day - prev).days - 1)
        run += 1
        if since is None:
            since = d
        if run % EARN_EVERY == 0 and freezes < MAX_FREEZES:
            freezes += 1
        best = max(best, run)
        prev = day
    practised_today = prev == end
    if prev is not None and not practised_today:
        gap(prev, (end - prev).days - 1)  # the gap still open: the days before today
    return {
        "current": run,
        "best": best,
        "practised_today": practised_today,
        "freezes": freezes,
        "covered": covered,
        "since": since,
        "today_covered": run > 0 and not practised_today and freezes >= 1,
        "last_run": last_run,
        "to_next_freeze": 0 if freezes >= MAX_FREEZES else EARN_EVERY - run % EARN_EVERY,
    }


def log_dates(log) -> list:
    """The dates of a session log's sessions (session-log.json's object; anything else has none), as they are."""
    sessions = log.get("sessions") if isinstance(log, dict) else None
    return [s.get("date") for s in sessions if isinstance(s, dict)] if isinstance(sessions, list) else []


def of_log(log, today: str) -> dict:
    """The streak of a session log on [today]."""
    return streak(log_dates(log), today)


def computed(s: dict) -> dict:
    """The streak as read-db.py's computed fields give it: covered is this run's days only (since on)."""
    out = dict(s)
    out["covered"] = [d for d in s["covered"] if s["since"] is not None and d >= s["since"]]
    return out


def line(s: dict) -> str:
    """The streak for a hook's line: "46 days · ❄️ 1 freeze held (a miss today would be covered)"."""
    days = f"{s['current']} day{'' if s['current'] == 1 else 's'}"
    held = f"❄️ {s['freezes']} freeze{'' if s['freezes'] == 1 else 's'} held"
    if s["current"] == 0:
        before = f" (the last one: {s['last_run']} days)" if s["last_run"] > 1 else ""
        return f"{days}: a new one starts with the next practice{before} · {held}"
    if s["practised_today"]:
        return f"{days}, today counts · {held}"
    today = "a miss today would be covered" if s["today_covered"] else "today keeps it"
    return f"{days} · {held} ({today})"
