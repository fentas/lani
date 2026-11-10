#!/usr/bin/env python3
"""
The streak's rule (.claude/hooks/lani_streak.py) against the shared cases of
companion/bridge/test/fixtures/streak-cases.json, which the app (StreakParityTest) and the bridge (the smoke's streak
section) read too: so the three can't drift. Then what the scripts do with it: read-db.py's computed streak and the
profile's current_streak_days of today, update-db.py's current_streak_days and streak_day at a session's end.

Usage:
    python3 -m unittest tests.test_streak
"""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
HOOKS = REPO_ROOT / ".claude" / "hooks"
FIXTURE = REPO_ROOT / "companion" / "bridge" / "test" / "fixtures" / "streak-cases.json"
sys.path.insert(0, str(HOOKS))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import lani_streak  # noqa: E402
from test_update_db import make_fixtures, script_env  # noqa: E402


class StreakCasesTest(unittest.TestCase):
    def test_the_shared_cases(self):
        doc = json.loads(FIXTURE.read_text(encoding="utf-8"))
        self.assertEqual(doc["rule"], {"start_freezes": lani_streak.START_FREEZES, "earn_every": lani_streak.EARN_EVERY,
                                       "max_freezes": lani_streak.MAX_FREEZES})
        self.assertGreaterEqual(len(doc["cases"]), 20)
        for c in doc["cases"]:
            with self.subTest(c["name"]):
                self.assertEqual(lani_streak.streak(c["days"], c["today"]), c["expect"])

    def test_the_computed_fields_keep_this_runs_covered_days(self):
        s = lani_streak.streak(["2026-10-01", "2026-10-03", "2026-10-06", "2026-10-07", "2026-10-09"], "2026-10-10")
        # 10-02 covered by the starter freeze, 10-04 and 10-05 end that run; the next one has none left to cover 10-08
        self.assertEqual((s["covered"], s["since"], s["current"], s["last_run"]), (["2026-10-02"], "2026-10-09", 1, 2))
        self.assertEqual(lani_streak.computed(s)["covered"], [])

    def test_a_log_that_isnt_one_has_no_sessions(self):
        for log in (None, [], {"sessions": "x"}, {"sessions": [1, "2026-10-01", {"date": 3}]}):
            self.assertEqual(lani_streak.of_log(log, "2026-10-10")["current"], 0)

    def test_the_hooks_line(self):
        s = lani_streak.streak(["2026-11-07", "2026-11-08"], "2026-11-10")
        self.assertEqual(lani_streak.line(s), "2 days · ❄️ 0 freezes held (today keeps it)")
        s = lani_streak.streak(["2026-11-09"], "2026-11-10")
        self.assertEqual(lani_streak.line(s), "1 day · ❄️ 1 freeze held (a miss today would be covered)")
        s = lani_streak.streak(["2026-11-04", "2026-11-05", "2026-11-06"], "2026-11-10")
        self.assertEqual(lani_streak.line(s), "0 days: a new one starts with the next practice (the last one: 3 days) · ❄️ 1 freeze held")


class StreakScriptsTest(unittest.TestCase):
    """read-db.py and update-db.py on a learner whose log has sessions on 04-20, 04-21 and 04-23."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.data = Path(self.tmp.name)
        make_fixtures(self.data)
        log = json.loads((self.data / "session-log.json").read_text())
        first = log["sessions"][0]
        log["sessions"] = [{**first, "session_id": f"session-00{i + 1}", "date": d}
                           for i, d in enumerate(["2026-04-20", "2026-04-21", "2026-04-23"])]
        (self.data / "session-log.json").write_text(json.dumps(log))
        profile = json.loads((self.data / "learner-profile.json").read_text())
        profile["current_streak_days"] = 99  # a stored counter that says something else: never read
        (self.data / "learner-profile.json").write_text(json.dumps(profile))

    def tearDown(self):
        self.tmp.cleanup()

    def run_script(self, name, now, stdin=None):
        env = {**script_env(self.data), "LANI_NOW": now}
        return subprocess.run([sys.executable, str(HOOKS / name)], input=stdin, capture_output=True, text=True, env=env)

    def test_read_db_says_the_derived_streak_of_today(self):
        # 04-22 covered by the starter freeze: a run of 3 on the 24th, today open, no freeze left
        r = self.run_script("read-db.py", "2026-04-24T08:00:00")
        self.assertEqual(r.returncode, 0, r.stderr)
        out = json.loads(r.stdout)
        s = out["computed"]["streak"]
        self.assertEqual((s["current"], s["freezes"], s["covered"], s["today_covered"]), (3, 0, ["2026-04-22"], False))
        self.assertTrue(out["computed"]["streak_active"])
        self.assertEqual(out["databases"]["learner_profile"]["current_streak_days"], 3)
        # two days later the gap (04-24, 04-25) is longer than the freezes held: the run is over, and an older app
        # reading the profile says so too
        r = self.run_script("read-db.py", "2026-04-26T08:00:00")
        out = json.loads(r.stdout)
        self.assertEqual((out["computed"]["streak"]["current"], out["computed"]["streak"]["last_run"]), (0, 3))
        self.assertFalse(out["computed"]["streak_active"])
        self.assertEqual(out["databases"]["learner_profile"]["current_streak_days"], 0)
        # the file keeps what the last session's end wrote
        self.assertEqual(json.loads((self.data / "learner-profile.json").read_text())["current_streak_days"], 99)

    def test_update_db_writes_the_derived_streak(self):
        report = {"session_id": "session-004", "date": "2026-04-25", "duration_minutes": 5,
                  "skill_scores": {"vocabulary": {"exercises": 2, "correct": 2, "time_minutes": 5}}}
        r = self.run_script("update-db.py", "2026-04-25T20:00:00", json.dumps(report))
        self.assertEqual(r.returncode, 0, r.stderr)
        # 04-22 covered, 04-24 not (the starter freeze was spent): a new run starts on the 25th
        profile = json.loads((self.data / "learner-profile.json").read_text())
        log = json.loads((self.data / "session-log.json").read_text())
        self.assertEqual((profile["current_streak_days"], log["sessions"][-1]["streak_day"]), (1, 1))
        self.assertIn("Streak: 1 days", r.stdout)


if __name__ == "__main__":
    unittest.main()
