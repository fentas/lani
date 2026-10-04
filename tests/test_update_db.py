#!/usr/bin/env python3
"""
Smoke test for .claude/hooks/update-db.py.

Runs the script against a fresh fixture DB in a temp dir, feeds it a sample
session report, and asserts schema invariants on the output files.

Usage:
    python3 tests/test_update_db.py
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SCRIPT = REPO_ROOT / ".claude" / "hooks" / "update-db.py"


def script_env(data_dir: Path) -> dict:
    """The scripts' environment, pinned to [data_dir]: never the repository's data/."""
    env = {k: v for k, v in os.environ.items() if k not in ("CLAUDE_PROJECT_DIR", "CLAUDE_PLUGIN_ROOT")}
    env["LANI_DATA_DIR"] = str(data_dir)
    return env


def make_fixtures(data_dir: Path, migrated: bool = True):
    """Six databases of a Dutch learner; [migrated] False gives data from before language profiles (no
    home_language, no languages), which the scripts migrate when they first run."""
    profile_languages = {"home_language": "nl", "languages": []} if migrated else {}
    (data_dir / "learner-profile.json").write_text(json.dumps({
        "learner": {"name": "Test", "target_language": "Dutch",
                    "current_level": "A1", "target_level": "A2"},
        "profile_created": "2026-04-20",
        "last_updated": "2026-04-23",
        "current_streak_days": 2,
        "total_sessions": 1,
        "total_study_minutes": 10,
        "skills": {
            "vocabulary": {"current_level": 1, "confidence": 60,
                           "last_practiced": "2026-04-23",
                           "total_practice_time": 10}
        },
        "focus_areas": [],
        "achievements": [],
        "preferences": {},
        **profile_languages,
    }))
    (data_dir / "progress-db.json").write_text(json.dumps({
        "metadata": {"last_updated": "2026-04-23", "language": "Dutch",
                     "tracking_started": "2026-04-20"},
        "overall_stats": {"total_sessions": 1, "total_exercises": 4,
                          "total_correct": 3, "total_incorrect": 1,
                          "accuracy_rate": 0.75,
                          "total_study_minutes": 10,
                          "average_session_duration": 10},
        "accuracy_trend": [{"date": "2026-04-23", "accuracy": 0.75,
                            "exercises": 4}],
        "skill_progress": {
            "vocabulary": {"sessions": 1, "accuracy": 0.75,
                           "last_practiced": "2026-04-23",
                           "exercises_completed": 4, "correct_count": 3,
                           "incorrect_count": 1}
        },
        "weekly_summary": []
    }))
    (data_dir / "mistakes-db.json").write_text(json.dumps({
        "metadata": {"last_updated": "2026-04-23",
                     "total_patterns_tracked": 0, "language": "Dutch"},
        "error_patterns": {}
    }))
    (data_dir / "mastery-db.json").write_text(json.dumps({
        "metadata": {"last_updated": "2026-04-23", "language": "Dutch"},
        "skills": {
            "vocabulary": {"mastery_level": 1, "confidence_score": 0.75,
                           "total_practice_time": 10,
                           "last_practiced": "2026-04-23",
                           "practice_count": 4, "avg_accuracy": 0.75}
        },
        "patterns": {}
    }))
    (data_dir / "spaced-repetition.json").write_text(json.dumps({
        "metadata": {"algorithm": "SM-2", "last_updated": "2026-04-23",
                     "total_items_tracked": 1, "language": "Dutch"},
        "review_queue": {"today": [], "tomorrow": ["vocab_dag"],
                         "this_week": [], "later": []},
        "items": {
            "vocab_dag": {
                "id": "vocab_dag", "type": "vocabulary", "content": "dag",
                "answer": "day / hi-bye", "category": "greetings",
                "difficulty": "A1", "created_date": "2026-04-23",
                "due_date": "2026-04-24", "interval_days": 1,
                "repetitions": 1, "easiness_factor": 2.5,
                "consecutive_correct": 1, "consecutive_incorrect": 0,
                "last_reviewed": "2026-04-23", "last_quality": 4,
                "mastery_level": 1, "total_reviews": 1, "priority": "medium"
            }
        }
    }))
    (data_dir / "session-log.json").write_text(json.dumps({
        "metadata": {"language": "Dutch", "learner_name": "Test",
                     "total_sessions": 1},
        "sessions": [{
            "session_id": "session-001", "date": "2026-04-23",
            "duration_minutes": 10,
            "skills_practiced": ["vocabulary"],
            "exercises_completed": 4, "accuracy": 0.75,
            "score_breakdown": {"vocabulary": 0.75},
            "topics_covered": [], "breakthroughs": [],
            "focus_next_session": [], "notes": "",
            "achievements_earned": []
        }],
        "milestones": []
    }))


SESSION_PAYLOAD = {
    "session_id": "session-002",
    "date": "2026-04-24",
    "duration_minutes": 15,
    "command_used": "/lani-learn",
    "skills_practiced": ["vocabulary"],
    "skill_scores": {
        "vocabulary": {"exercises": 5, "correct": 4, "time_minutes": 15}
    },
    "errors": [{
        "pattern_id": "verb_spreek",
        "category": "grammar",
        "subcategory": "verb_conjugation",
        "your_answer": "Hij spreek",
        "correct_answer": "Hij spreekt",
        "context": "3rd person",
        "severity": "critical",
        "difficulty_score": 0.7
    }],
    "new_vocabulary": [{
        "item_id": "het_huis",
        "item_type": "vocabulary",
        "content": "het huis",
        "answer": "the house",
        "category": "nouns",
        "difficulty": "A1",
        "initial_quality": 4
    }],
    "review_results": [{"item_id": "vocab_dag", "quality": 5}],
    "topics_covered": ["house_vocab"],
    "breakthroughs": ["Got 'het huis' on first try"],
    "focus_next_session": ["de/het drill"],
    "session_notes": "Good session.",
    "milestones": []
}


class UpdateDbSmokeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="lani-test-"))
        (self.tmp / "data").mkdir()
        make_fixtures(self.tmp / "data")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _run(self, payload: dict):
        proc = subprocess.run(
            ["python3", str(SCRIPT)],
            input=json.dumps(payload).encode(),
            cwd=str(self.tmp),
            env=script_env(self.tmp / "data"),
            capture_output=True,
        )
        return proc

    def test_happy_path(self):
        proc = self._run(SESSION_PAYLOAD)
        self.assertEqual(proc.returncode, 0,
                         msg=f"stdout={proc.stdout!r} stderr={proc.stderr!r}")

        with open(self.tmp / "data" / "session-log.json") as f:
            log = json.load(f)
        latest = log["sessions"][-1]
        self.assertEqual(latest["session_id"], "session-002")
        self.assertIn("skills_practiced", latest)
        self.assertIsInstance(latest["skills_practiced"], list)
        self.assertIn("score_breakdown", latest)
        self.assertIn("topics_covered", latest)
        self.assertIn("breakthroughs", latest)
        self.assertIn("focus_next_session", latest)
        self.assertIn("achievements_earned", latest)
        self.assertEqual(latest["streak_day"], 3)  # was 2, yesterday -> +1

        with open(self.tmp / "data" / "learner-profile.json") as f:
            profile = json.load(f)
        self.assertEqual(profile["current_streak_days"], 3)
        conf = profile["skills"]["vocabulary"]["confidence"]
        self.assertIsInstance(conf, int)
        self.assertGreaterEqual(conf, 0)
        self.assertLessEqual(conf, 100)

        with open(self.tmp / "data" / "spaced-repetition.json") as f:
            sr = json.load(f)
        dag = sr["items"]["vocab_dag"]
        # Schema preserved
        for k in ("consecutive_correct", "consecutive_incorrect",
                  "mastery_level", "total_reviews", "priority",
                  "content", "answer", "category", "difficulty"):
            self.assertIn(k, dag, f"lost field {k} on vocab_dag")
        self.assertEqual(dag["total_reviews"], 2)  # was 1, +1 review
        self.assertEqual(dag["last_quality"], 5)

        # New vocabulary item fully populated
        huis = sr["items"]["het_huis"]
        for k in ("id", "type", "content", "answer", "category",
                  "difficulty", "due_date", "interval_days", "repetitions",
                  "easiness_factor", "consecutive_correct",
                  "consecutive_incorrect", "mastery_level",
                  "total_reviews", "priority"):
            self.assertIn(k, huis, f"new item missing {k}")

        with open(self.tmp / "data" / "mistakes-db.json") as f:
            mistakes = json.load(f)
        self.assertIn("verb_spreek", mistakes["error_patterns"])
        pat = mistakes["error_patterns"]["verb_spreek"]
        self.assertEqual(pat["consecutive_incorrect"], 1)
        self.assertEqual(pat["examples"][-1]["incorrect"], "Hij spreek")
        self.assertEqual(pat["examples"][-1]["correct"], "Hij spreekt")

        # Backup directory exists (nested inside data/ to avoid collisions
        # with other plugins when the global fallback ~/.claude/lani-data is used).
        backup = self.tmp / "data" / ".backups" / "pre-update-session-002"
        self.assertTrue(backup.exists(), "pre-update backup missing")

    def test_missing_required_field_exits_1(self):
        proc = self._run({"date": "2026-04-24"})  # no session_id
        self.assertEqual(proc.returncode, 1)

    def test_same_day_does_not_bump_streak(self):
        # Profile last_updated = 2026-04-23; send a session on 2026-04-23.
        payload = dict(SESSION_PAYLOAD)
        payload["session_id"] = "session-003"
        payload["date"] = "2026-04-23"
        proc = self._run(payload)
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        with open(self.tmp / "data" / "learner-profile.json") as f:
            profile = json.load(f)
        self.assertEqual(profile["current_streak_days"], 2)

    # --- Milestones (issue #8) ---

    def _payload_with(self, session_id, milestones, date="2026-04-24"):
        payload = dict(SESSION_PAYLOAD)
        payload["session_id"] = session_id
        payload["date"] = date
        payload["milestones"] = milestones
        return payload

    def _load(self, name):
        with open(self.tmp / "data" / name) as f:
            return json.load(f)

    def test_milestone_string_form(self):
        text = "Reached A2 vocabulary milestone"
        proc = self._run(self._payload_with("session-100", [text]))
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)

        log = self._load("session-log.json")
        m = log["milestones"][-1]
        self.assertEqual(m["milestone"], text)
        self.assertEqual(m["date"], "2026-04-24")
        self.assertEqual(m["session_id"], "session-100")

        profile = self._load("learner-profile.json")
        ach = profile["achievements"][-1]
        self.assertEqual(ach["name"], text)
        self.assertEqual(ach["description"], text)
        self.assertEqual(ach["earned_date"], "2026-04-24")
        self.assertTrue(ach["id"].startswith("session_session-100_"))

    def test_milestone_object_form(self):
        ms = {"milestone": "Wrote first paragraph", "date": "2026-04-24",
              "session_id": "session-101"}
        proc = self._run(self._payload_with("session-101", [ms]))
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)

        log = self._load("session-log.json")
        m = log["milestones"][-1]
        self.assertEqual(m["milestone"], "Wrote first paragraph")  # flat string
        self.assertEqual(m["date"], "2026-04-24")
        self.assertEqual(m["session_id"], "session-101")

        profile = self._load("learner-profile.json")
        self.assertEqual(profile["achievements"][-1]["name"], "Wrote first paragraph")

    def test_milestone_object_preserves_own_date(self):
        ms = {"milestone": "Backdated win", "date": "2026-04-20"}
        proc = self._run(self._payload_with("session-102", [ms], date="2026-04-24"))
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)

        log = self._load("session-log.json")
        self.assertEqual(log["milestones"][-1]["date"], "2026-04-20")
        profile = self._load("learner-profile.json")
        self.assertEqual(profile["achievements"][-1]["earned_date"], "2026-04-20")

    def test_milestone_bad_date_falls_back_to_session_date(self):
        ms = {"milestone": "Typo date", "date": "not-a-date"}
        proc = self._run(self._payload_with("session-103", [ms], date="2026-04-24"))
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        log = self._load("session-log.json")
        self.assertEqual(log["milestones"][-1]["date"], "2026-04-24")

    def test_milestone_malformed_exits_1_no_mutation(self):
        bad_cases = [
            {"date": "2026-04-24"},          # missing milestone
            {"milestone": None},
            {"milestone": ""},
            {"milestone": "   "},
            {"milestone": 5},
            42,                              # neither str nor dict
            "",                              # empty string form
        ]
        for n, bad in enumerate(bad_cases):
            with self.subTest(case=bad):
                proc = self._run(self._payload_with(f"session-2{n:02d}", [bad]))
                self.assertEqual(proc.returncode, 1,
                                 msg=f"case={bad!r} stderr={proc.stderr!r}")
                self.assertTrue(proc.stderr, "expected an error message on stderr")
                # No DB mutation: session-log still has its single original session.
                log = self._load("session-log.json")
                self.assertEqual(len(log["sessions"]), 1)
                self.assertEqual(log["milestones"], [])

    def test_milestone_nested_session_id_overridden(self):
        ms = {"milestone": "X", "session_id": "WRONG-999"}
        proc = self._run(self._payload_with("session-104", [ms]))
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        log = self._load("session-log.json")
        self.assertEqual(log["milestones"][-1]["session_id"], "session-104")

    def test_milestone_distinct_achievement_ids(self):
        # Two strings sharing the first 30 chars would slugify identically;
        # the index prefix must keep their IDs distinct.
        prefix = "Mastered the perfect tense fo"  # 29 chars
        ms = [prefix + "r regular verbs", prefix + "r irregular verbs"]
        proc = self._run(self._payload_with("session-105", ms))
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        profile = self._load("learner-profile.json")
        ids = [a["id"] for a in profile["achievements"][-2:]]
        self.assertEqual(len(set(ids)), 2, msg=f"colliding ids: {ids}")

    def test_milestone_non_latin_distinct_nonempty_ids(self):
        # All-non-Latin text slugifies to empty; fallback + index keep IDs valid.
        ms = ["مرحلة أولى", "مرحلة ثانية"]
        proc = self._run(self._payload_with("session-106", ms))
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        profile = self._load("learner-profile.json")
        ids = [a["id"] for a in profile["achievements"][-2:]]
        self.assertEqual(len(set(ids)), 2, msg=f"colliding ids: {ids}")
        for i in ids:
            self.assertFalse(i.endswith("_"), f"bare trailing underscore: {i}")

    def test_milestones_empty_and_omitted_are_noops(self):
        for n, payload in enumerate([
            self._payload_with("session-107", []),
            {k: v for k, v in self._payload_with("session-108", []).items()
             if k != "milestones"},
        ]):
            with self.subTest(n=n):
                before = len(self._load("learner-profile.json").get("achievements", []))
                proc = self._run(payload)
                self.assertEqual(proc.returncode, 0, msg=proc.stderr)
                after = len(self._load("learner-profile.json").get("achievements", []))
                self.assertEqual(after, before)


    # --- A new level: level_since ---

    def test_a_new_level_sets_level_since(self):
        payload = dict(self._payload_with("session-110", []), level="a2")
        proc = self._run(payload)
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        learner = self._load("learner-profile.json")["learner"]
        self.assertEqual((learner["current_level"], learner["level_since"]), ("A2", "2026-04-24"))

    def test_the_same_level_again_keeps_level_since(self):
        self.assertEqual(self._run(dict(self._payload_with("session-111", []), level="A2")).returncode, 0)
        again = dict(self._payload_with("session-112", [], date="2026-04-26"), level="A2")
        proc = self._run(again)
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        learner = self._load("learner-profile.json")["learner"]
        self.assertEqual((learner["current_level"], learner["level_since"]), ("A2", "2026-04-24"))
        # a report without a level leaves both alone too
        self.assertEqual(self._run(self._payload_with("session-113", [], date="2026-04-27")).returncode, 0)
        learner = self._load("learner-profile.json")["learner"]
        self.assertEqual((learner["current_level"], learner["level_since"]), ("A2", "2026-04-24"))

    def test_the_level_the_profile_has_sets_no_level_since(self):
        proc = self._run(dict(self._payload_with("session-114", []), level="A1"))
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        self.assertNotIn("level_since", self._load("learner-profile.json")["learner"])

    def test_a_new_level_in_another_language_sets_its_level_since(self):
        italian = dict(self._payload_with("session-it-001", []), language="it", level="A2",
                       errors=[], new_vocabulary=[], review_results=[])
        proc = self._run(italian)
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        it = self._load("languages/it/learner-profile.json")["learner"]
        self.assertEqual((it["current_level"], it["level_since"]), ("A2", "2026-04-24"))
        home = self._load("learner-profile.json")
        self.assertNotIn("level_since", home["learner"])  # the home language's level didn't change
        self.assertEqual(home["learner"]["current_level"], "A1")
        self.assertEqual(home["languages"][0]["level"], "A2")
        # the same level again, a day later: its day stays
        again = dict(italian, session_id="session-it-002", date="2026-04-25")
        self.assertEqual(self._run(again).returncode, 0)
        self.assertEqual(self._load("languages/it/learner-profile.json")["learner"]["level_since"], "2026-04-24")

    # --- Review items without a session (record_session: false) ---

    WORD = {"item_id": "vocab_word_gozd", "item_type": "vocabulary", "content": "gozd",
            "answer": "forest", "category": "dialog", "initial_quality": 3,
            "example": "V gozdu je tiho.", "example_translation": "It's quiet in the forest."}

    def _raw(self):
        return {f.name: f.read_bytes() for f in (self.tmp / "data").glob("*.json")}

    def test_items_only_adds_an_item_and_nothing_else(self):
        before = self._raw()
        proc = self._run({"record_session": False, "date": "2026-04-24", "new_vocabulary": [self.WORD]})
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        after = self._raw()
        # the other five databases are untouched, byte for byte: no session, minutes or streak day
        for name, raw in before.items():
            if name != "spaced-repetition.json":
                self.assertEqual(after[name], raw, f"{name} changed")
        sr = self._load("spaced-repetition.json")
        gozd = sr["items"]["vocab_word_gozd"]
        self.assertEqual((gozd["content"], gozd["answer"], gozd["due_date"]), ("gozd", "forest", "2026-04-25"))
        self.assertEqual((gozd["example"], gozd["example_translation"]), ("V gozdu je tiho.", "It's quiet in the forest."))
        self.assertIn("vocab_word_gozd", sr["review_queue"]["tomorrow"])
        self.assertEqual(sr["metadata"]["total_items_tracked"], 2)
        self.assertTrue((self.tmp / "data" / ".backups" / "pre-words-2026-04-24" / "spaced-repetition.json").exists())

    def test_new_item_keeps_its_source(self):
        looked_up = dict(self.WORD, source="lookup")
        plain = dict(self.WORD, item_id="vocab_word_hisa", content="hiša", answer="house")
        known = dict(self.WORD, item_id="vocab_dag", source="lookup")
        proc = self._run({"record_session": False, "date": "2026-04-24", "new_vocabulary": [looked_up, plain, known]})
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        items = self._load("spaced-repetition.json")["items"]
        self.assertEqual(items["vocab_word_gozd"]["source"], "lookup")
        self.assertNotIn("source", items["vocab_word_hisa"])  # only when given
        self.assertNotIn("source", items["vocab_dag"])  # an item that exists is left as it is
        self.assertEqual(items["vocab_word_gozd"]["last_quality"], 3)  # initial_quality as it was

    def test_items_only_keeps_a_known_item(self):
        word = dict(self.WORD, item_id="vocab_dag", content="dag?", answer="changed")
        before = self._raw()
        proc = self._run({"record_session": False, "date": "2026-04-24", "new_vocabulary": [word]})
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        self.assertEqual(self._raw(), before)  # nothing new: nothing written
        self.assertIn("all known already", proc.stdout.decode())

    def test_items_only_refuses_practice_and_bad_input(self):
        base = {"record_session": False, "date": "2026-04-24", "new_vocabulary": [self.WORD]}
        bad_cases = [
            dict(base, duration_minutes=5),
            dict(base, skill_scores={"vocabulary": {"exercises": 1, "correct": 1, "time_minutes": 1}}),
            dict(base, review_results=[{"item_id": "vocab_dag", "quality": 4}]),
            dict(base, milestones=["x"]),
            dict(base, new_vocabulary=[]),
            dict(base, new_vocabulary=[{"content": "gozd"}]),
            dict(base, date="yesterday"),
        ]
        before = self._raw()
        for bad in bad_cases:
            with self.subTest(case=bad):
                proc = self._run(bad)
                self.assertEqual(proc.returncode, 1, msg=proc.stderr)
                self.assertEqual(self._raw(), before)


if __name__ == "__main__":
    unittest.main()
