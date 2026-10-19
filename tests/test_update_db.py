#!/usr/bin/env python3
"""
Smoke test for .claude/hooks/update-db.py.

Runs the script against a fresh fixture DB in a temp dir, feeds it a sample
session report, and asserts schema invariants on the output files.

UpdateDbDiffTest runs the same cases through both implementations, the Python and its TypeScript twin
(companion/bridge/src/db/), and requires the same output and files byte for byte (tests/db_impl.py).

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

sys.path.insert(0, str(Path(__file__).resolve().parent))
import db_impl  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parent.parent
SCRIPT = REPO_ROOT / ".claude" / "hooks" / "update-db.py"


def script_env(data_dir: Path) -> dict:
    """The scripts' environment, pinned to [data_dir]: never the repository's data/."""
    env = {k: v for k, v in os.environ.items() if k not in ("CLAUDE_PROJECT_DIR", "CLAUDE_PLUGIN_ROOT")}
    env["LANI_DATA_DIR"] = str(data_dir)
    env.setdefault("LANI_ENV_FILE", os.devnull)  # never the learner's lani.env
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
    impl = "py"  # db_impl: "py", or "diff" (both implementations, compared)

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="lani-test-"))
        (self.tmp / "data").mkdir()
        make_fixtures(self.tmp / "data")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _run(self, payload: dict):
        return db_impl.run(self.impl, "update-db", stdin=json.dumps(payload).encode(), cwd=self.tmp,
                           env=script_env(self.tmp / "data"), root=self.tmp)

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

    # --- A word got wrong in a dialog: its card lowered gently (review_results' "gentle") ---

    def _card(self, **fields):
        """vocab_dag, well on its way (interval 6, due in a week, reviewed four days ago), with [fields] over it."""
        path = self.tmp / "data" / "spaced-repetition.json"
        sr = json.loads(path.read_text())
        sr["items"]["vocab_dag"].update({
            "interval_days": 6, "repetitions": 2, "easiness_factor": 2.5, "due_date": "2026-04-30",
            "consecutive_correct": 2, "last_reviewed": "2026-04-20", "last_quality": 4, "mastery_level": 1,
            "total_reviews": 2, "review_history": [{"date": "2026-04-20", "quality": 4, "score": 0}], **fields,
        })
        path.write_text(json.dumps(sr))

    def _dialog(self, session_id, results, date="2026-04-24"):
        return {
            "session_id": session_id, "date": date, "duration_minutes": 2, "command_used": "/lani-app-dialog-words",
            "skills_practiced": ["vocabulary"], "skill_scores": {"vocabulary": {"exercises": 1, "correct": 0, "time_minutes": 2}},
            "review_results": results, "session_notes": "Words in a dialog in the Lani app.",
        }

    def test_gentle_lowers_a_card_without_a_review(self):
        self._card()
        proc = self._run(self._dialog("session-200", [{"item_id": "vocab_dag", "gentle": True}]))
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        dag = self._load("spaced-repetition.json")["items"]["vocab_dag"]
        # a little harder, back tomorrow, its interval halved; its repetitions kept
        self.assertEqual((dag["easiness_factor"], dag["interval_days"], dag["repetitions"], dag["due_date"]), (2.36, 3, 2, "2026-04-25"))
        self.assertEqual(dag["consecutive_correct"], 0)
        self.assertEqual((dag["lapses"], dag["last_lapse"]), (1, "2026-04-24"))
        # no review: what readiness and "rusty" read stays as it was
        self.assertEqual((dag["last_quality"], dag["last_reviewed"], dag["mastery_level"], dag["total_reviews"]), (4, "2026-04-20", 1, 2))
        self.assertEqual(len(dag["review_history"]), 1)
        self.assertEqual(dag["consecutive_incorrect"], 0)
        self.assertIn("vocab_dag", self._load("spaced-repetition.json")["review_queue"]["tomorrow"])

    def test_gentle_keeps_an_earlier_due_date_and_the_floors(self):
        self._card(due_date="2026-04-22", interval_days=1, easiness_factor=1.35)
        self.assertEqual(self._run(self._dialog("session-201", [{"item_id": "vocab_dag", "gentle": True, "quality": 0}])).returncode, 0)
        dag = self._load("spaced-repetition.json")["items"]["vocab_dag"]
        # overdue already: it stays due; the interval and the easiness stop at their floors; the quality is ignored
        self.assertEqual((dag["due_date"], dag["interval_days"], dag["easiness_factor"], dag["repetitions"]), ("2026-04-22", 1, 1.3, 2))
        self.assertEqual(dag["last_quality"], 4)

    def test_gentle_once_a_day(self):
        self._card()
        self.assertEqual(self._run(self._dialog("session-202", [{"item_id": "vocab_dag", "gentle": True}])).returncode, 0)
        once = self._load("spaced-repetition.json")["items"]["vocab_dag"]
        self.assertEqual(self._run(self._dialog("session-203", [{"item_id": "vocab_dag", "gentle": True}])).returncode, 0)
        twice = self._load("spaced-repetition.json")["items"]["vocab_dag"]
        self.assertEqual(twice, once)  # a second slip the same day changes nothing
        # the next day it may lower again
        self.assertEqual(self._run(self._dialog("session-204", [{"item_id": "vocab_dag", "gentle": True}], date="2026-04-25")).returncode, 0)
        later = self._load("spaced-repetition.json")["items"]["vocab_dag"]
        self.assertEqual((later["lapses"], later["last_lapse"], later["easiness_factor"], later["interval_days"]), (2, "2026-04-25", 2.22, 1))

    def test_a_review_after_a_gentle_lowering_is_a_review(self):
        self._card()
        self.assertEqual(self._run(self._dialog("session-205", [{"item_id": "vocab_dag", "gentle": True}])).returncode, 0)
        proc = self._run(self._dialog("session-206", [{"item_id": "vocab_dag", "quality": 5}], date="2026-04-25"))
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        dag = self._load("spaced-repetition.json")["items"]["vocab_dag"]
        # SM-2 from where the lowering left it: interval 3 x 2.36 → 8, the easiness up by 0.1
        self.assertEqual((dag["repetitions"], dag["interval_days"], dag["easiness_factor"], dag["due_date"]), (3, 8, 2.46, "2026-05-03"))
        self.assertEqual((dag["last_quality"], dag["last_reviewed"], dag["total_reviews"], dag["consecutive_correct"]), (5, "2026-04-25", 3, 1))
        self.assertEqual((dag["lapses"], len(dag["review_history"])), (1, 2))

    def test_gentle_for_a_card_that_isnt_there_is_nothing(self):
        proc = self._run(self._dialog("session-207", [{"item_id": "vocab_nope", "gentle": True}]))
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        self.assertNotIn("vocab_nope", self._load("spaced-repetition.json")["items"])

    # --- Review items without a session (record_session: false) ---

    WORD = {"item_id": "vocab_word_gozd", "item_type": "vocabulary", "content": "gozd",
            "answer": "forest", "category": "dialog", "initial_quality": 3,
            "example": "V gozdu je tiho.", "example_translation": "It's quiet in the forest."}

    def _raw(self):
        return {f.name: f.read_bytes() for f in (self.tmp / "data").glob("*.json") if f.is_file()}

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

    def test_a_counting_card_keeps_its_forms_and_is_due_the_day_it_is_made(self):
        forms = {"v": 1, "lemma": "kava", "gender": "f", "lines": ["ena kava", "dve kavi", "tri kave", "pet kav"],
                 "forms": {"nom.sg": ["kava"], "nom.du": ["kavi"], "nom.pl": ["kave"], "gen.pl": ["kav"]}, "others": ["kavo"], "table": "wiktionary"}
        card = {"item_id": "counting_kava", "item_type": "counting", "content": "kava", "answer": "ena kava · dve kavi · tri kave · pet kav",
                "category": "counting", "difficulty": "A1", "source": "counting", "due_date": "2026-04-24", "counting": forms}
        late = dict(card, item_id="counting_late", due_date="2026-05-30")  # never later than tomorrow
        early = dict(card, item_id="counting_early", due_date="2026-04-01")  # nor before the report's day
        odd = dict(card, item_id="counting_odd", due_date="soon", counting="not an object")
        proc = self._run({"record_session": False, "date": "2026-04-24", "new_vocabulary": [card, late, early, odd]})
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        sr = self._load("spaced-repetition.json")
        kava = sr["items"]["counting_kava"]
        self.assertEqual((kava["type"], kava["due_date"], kava["source"]), ("counting", "2026-04-24", "counting"))
        self.assertEqual(kava["counting"], forms)
        self.assertIn("counting_kava", sr["review_queue"]["today"])
        self.assertEqual([sr["items"][i]["due_date"] for i in ("counting_late", "counting_early", "counting_odd")], ["2026-04-25"] * 3)
        self.assertNotIn("counting", sr["items"]["counting_odd"])

    FORMS = {"v": 1, "lemma": "hiša", "pos": "noun", "gender": "f", "gloss": ["house"], "table": "wiktionary",
             "slots": [{"key": "nom.sg", "forms": ["hiša"], "page": "imenovalnik"}, {"key": "acc.sg", "forms": ["hišo"], "page": "tozilnik"},
                       {"key": "loc.sg", "forms": ["hiši"], "page": "mestnik"}, {"key": "dat.sg", "forms": ["hiši"], "page": "dajalnik"}],
             "tested": ["acc.sg", "loc.sg"]}

    def _forms_card(self, **extra):
        return dict({"item_id": "forms_hisa", "item_type": "forms", "content": "hiša", "answer": "hišo · hiši · hiši",
                     "category": "forms", "difficulty": "A1", "source": "forms", "due_date": "2026-04-24", "forms": self.FORMS}, **extra)

    def test_a_forms_card_keeps_its_table_and_is_due_the_day_it_is_made(self):
        odd = self._forms_card(item_id="forms_odd", forms="not an object")
        proc = self._run({"record_session": False, "date": "2026-04-24", "new_vocabulary": [self._forms_card(), odd]})
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        sr = self._load("spaced-repetition.json")
        hisa = sr["items"]["forms_hisa"]
        self.assertEqual((hisa["type"], hisa["due_date"], hisa["source"]), ("forms", "2026-04-24", "forms"))
        self.assertEqual(hisa["forms"], self.FORMS)
        self.assertIn("forms_hisa", sr["review_queue"]["today"])
        self.assertNotIn("forms", sr["items"]["forms_odd"])

    def _reviewed_forms_card(self):
        """forms_hisa made on 04-24 and reviewed well three times: due 05-12, 2 repetitions in a row and more."""
        self.assertEqual(self._run({"record_session": False, "date": "2026-04-24", "new_vocabulary": [self._forms_card()]}).returncode, 0)
        path = self.tmp / "data" / "spaced-repetition.json"
        sr = json.loads(path.read_text())
        sr["items"]["forms_hisa"].update({
            "due_date": "2026-05-12", "interval_days": 15, "repetitions": 3, "easiness_factor": 2.6, "total_reviews": 3,
            "mastery_level": 1, "consecutive_correct": 3, "last_quality": 4,
            "review_history": [{"date": "2026-04-24", "quality": 4, "score": 0}, {"date": "2026-04-25", "quality": 4, "score": 0},
                               {"date": "2026-05-01", "quality": 5, "score": 0}],
        })
        path.write_text(json.dumps(sr))
        return sr["items"]["forms_hisa"]

    def test_resurface_brings_a_forms_card_back_soon_as_new_learning(self):
        before = self._reviewed_forms_card()
        grown = dict(self.FORMS, tested=["acc.sg", "loc.sg", "dat.sg"], fresh=["dat.sg"], unlocked="2026-05-02", resurface="2026-05-03")
        proc = self._run({"record_session": False, "date": "2026-05-02",
                          "resurface": [{"item_id": "forms_hisa", "due_date": "2026-05-03", "forms": grown},
                                        {"item_id": "vocab_dag", "due_date": "2026-05-03"},  # not a forms card: left out
                                        {"item_id": "forms_nope", "due_date": "2026-05-03"}]})
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        self.assertIn("Forms cards back soon: 1 (forms_hisa); 2 entr(ies) left out", proc.stdout.decode())
        sr = self._load("spaced-repetition.json")
        hisa = sr["items"]["forms_hisa"]
        self.assertEqual((hisa["due_date"], hisa["interval_days"], hisa["repetitions"]), ("2026-05-03", 1, 0))
        # its history kept: easiness, the reviews, mastery, the run of right answers
        for k in ("easiness_factor", "total_reviews", "review_history", "mastery_level", "consecutive_correct", "last_quality", "created_date"):
            self.assertEqual(hisa[k], before[k], k)
        self.assertEqual(hisa["forms"], grown)
        self.assertEqual(hisa["resurfaced"], [{"date": "2026-05-02", "due": "2026-05-03"}])
        self.assertIn("forms_hisa", sr["review_queue"]["tomorrow"])
        self.assertNotIn("forms", sr["items"]["vocab_dag"])
        self.assertTrue((self.tmp / "data" / ".backups" / "pre-words-2026-05-02" / "spaced-repetition.json").exists())

    def test_resurface_never_moves_a_card_later_nor_takes_a_bad_date(self):
        self._reviewed_forms_card()
        path = self.tmp / "data" / "spaced-repetition.json"
        sr = json.loads(path.read_text())
        sr["items"]["forms_hisa"]["due_date"] = "2026-05-02"  # due today already
        path.write_text(json.dumps(sr))
        proc = self._run({"record_session": False, "date": "2026-05-02", "resurface": [{"item_id": "forms_hisa", "due_date": "2026-05-05"}]})
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        hisa = self._load("spaced-repetition.json")["items"]["forms_hisa"]
        self.assertEqual((hisa["due_date"], hisa["repetitions"], hisa["interval_days"]), ("2026-05-02", 0, 1))
        self.assertEqual(hisa["forms"], self.FORMS)  # no forms given: kept
        before = self._raw()
        for bad in ("2026-05-01", "2026-06-02", "soon"):  # before the report's day, more than 30 days after it, no date
            with self.subTest(due=bad):
                proc = self._run({"record_session": False, "date": "2026-05-02", "resurface": [{"item_id": "forms_hisa", "due_date": bad}]})
                self.assertEqual(proc.returncode, 0, msg=proc.stderr)
                self.assertEqual(self._raw(), before)  # nothing to do: nothing written
        for bad in ({"resurface": "x"}, {"resurface": []}):
            with self.subTest(case=bad):
                proc = self._run(dict({"record_session": False, "date": "2026-05-02"}, **bad))
                self.assertEqual(proc.returncode, 1, msg=proc.stderr)

    def test_resurface_and_new_cards_in_one_report(self):
        self._reviewed_forms_card()
        oko = self._forms_card(item_id="forms_oko", content="oko", due_date="2026-05-02", forms=dict(self.FORMS, lemma="oko", gender="n"))
        proc = self._run({"record_session": False, "date": "2026-05-02", "new_vocabulary": [oko],
                          "resurface": [{"item_id": "forms_hisa", "due_date": "2026-05-04"}]})
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        items = self._load("spaced-repetition.json")["items"]
        self.assertEqual((items["forms_oko"]["due_date"], items["forms_hisa"]["due_date"]), ("2026-05-02", "2026-05-04"))

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


    # --- A backup that fails ---

    def test_a_backup_that_fails_is_exit_2_and_writes_nothing(self):
        """A directory named *.json in data/ (the backup copies data/*.json) made the backup fail outside the error
        handling: a traceback and exit 1. Now exit 2 (an I/O error, as documented), and no database written."""
        (self.tmp / "data" / "odd.json").mkdir()
        before = self._raw()
        proc = self._run(SESSION_PAYLOAD)
        self.assertEqual(proc.returncode, 2, msg=proc.stderr)
        err = proc.stderr.decode()
        self.assertIn("[Lani] Error backing up the databases (nothing written): [Errno 21] Is a directory", err)
        self.assertNotIn("Traceback", err)
        self.assertEqual(self._raw(), before)
        # a review item without a session: its backup is the spaced-repetition file's
        (self.tmp / "data" / ".backups").mkdir(exist_ok=True)
        (self.tmp / "data" / ".backups" / "pre-words-2026-04-24").write_text("a file where the backup's directory goes")
        proc = self._run({"record_session": False, "date": "2026-04-24", "new_vocabulary": [self.WORD]})
        self.assertEqual(proc.returncode, 2, msg=proc.stderr)
        self.assertIn("[Lani] Error backing up spaced-repetition.json (nothing written): ", proc.stderr.decode())
        self.assertEqual(self._raw(), before)

    # --- The session's id: it names the backup's directory ---

    def test_a_session_id_that_isnt_one_name_is_refused(self):
        """A session_id with '/' or '..' put the backup elsewhere than .backups/pre-update-<id>/, enough '../' outside
        the data directory: refused now (exit 1), nothing written anywhere."""
        outside = self.tmp / "outside"
        before = self._raw()
        for bad in ("../../../outside/x", "a/b", "../x", "..", "a\\b", "session 005", "x" * 101, "", "session-005\n", 5, None, ["s"]):
            with self.subTest(session_id=bad):
                proc = self._run(dict(SESSION_PAYLOAD, session_id=bad))
                self.assertEqual(proc.returncode, 1, msg=proc.stderr)
                self.assertIn("'session_id' must be letters, digits", proc.stderr.decode())
                self.assertEqual(self._raw(), before)
        self.assertFalse(outside.exists())
        self.assertEqual(sorted(p.name for p in (self.tmp / "data" / ".backups").iterdir()), [])
        for good in ("session-003", "session-it-004", "s.1_x-Y"):
            with self.subTest(session_id=good):
                self.assertEqual(self._run(dict(SESSION_PAYLOAD, session_id=good)).returncode, 0)
                self.assertTrue((self.tmp / "data" / ".backups" / f"pre-update-{good}").is_dir())

    # --- Dates: compared as dates, written zero-padded ---

    def _write(self, name, change):
        path = self.tmp / "data" / name
        data = json.loads(path.read_text())
        change(data)
        path.write_text(json.dumps(data))

    def _unpadded(self):
        """The fixtures as an update-db.py from before dates were zero-padded left them, after reports dated "2026-4-23"
        and "2026-4-24": the scripts compared such dates as text ("2026-4-9" after "2026-04-24")."""
        self._write("learner-profile.json", lambda p: p.update(last_updated="2026-4-23"))
        self._write("progress-db.json", lambda p: p.update(accuracy_trend=[{"date": "2026-4-24", "accuracy": 0.5, "exercises": 2}]))

        def items(sr):
            dag = sr["items"]["vocab_dag"]
            dag.update(created_date="2026-4-23", last_reviewed="2026-4-23", due_date="2026-04-30", interval_days=6, repetitions=2)
            sr["items"]["vocab_old"] = dict(dag, id="vocab_old", content="oud", answer="old", due_date="2026-4-9")
        self._write("spaced-repetition.json", items)

    def test_a_date_without_zeros_is_written_with_them(self):
        report = dict(SESSION_PAYLOAD, date="2026-4-24", milestones=[{"milestone": "First 'het'", "date": "2026-4-2"}, "Second"])
        proc = self._run(report)
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        profile = self._load("learner-profile.json")
        self.assertEqual((profile["last_updated"], profile["current_streak_days"]), ("2026-04-24", 3))  # 2026-04-23: yesterday
        self.assertEqual([a["earned_date"] for a in profile["achievements"]], ["2026-04-02", "2026-04-24"])
        log = self._load("session-log.json")
        self.assertEqual(log["sessions"][-1]["date"], "2026-04-24")
        self.assertEqual([m["date"] for m in log["milestones"]], ["2026-04-02", "2026-04-24"])
        sr = self._load("spaced-repetition.json")
        huis = sr["items"]["het_huis"]
        self.assertEqual((huis["created_date"], huis["last_reviewed"], huis["due_date"]), ("2026-04-24", "2026-04-24", "2026-04-25"))
        self.assertEqual(sr["items"]["vocab_dag"]["review_history"][-1]["date"], "2026-04-24")
        self.assertEqual(self._load("mistakes-db.json")["error_patterns"]["verb_spreek"]["last_seen"], "2026-04-24")
        self.assertEqual(self._load("progress-db.json")["accuracy_trend"][-1]["date"], "2026-04-24")
        # with ASCII digits too
        proc = self._run(dict(SESSION_PAYLOAD, session_id="session-003", date="２０２６-04-25", review_results=[]))
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        self.assertEqual(self._load("learner-profile.json")["last_updated"], "2026-04-25")

    def test_a_date_that_isnt_one_is_refused(self):
        before = self._raw()
        for bad in ("yesterday", "2026-02-30", "24.4.2026", "2026-04-24\n", 20260424, None):
            with self.subTest(date=bad):
                proc = self._run(dict(SESSION_PAYLOAD, date=bad))
                self.assertEqual(proc.returncode, 1, msg=proc.stderr)
                self.assertIn("'date' must be YYYY-MM-DD", proc.stderr.decode())
                self.assertEqual(self._raw(), before)

    def test_dates_written_without_zeros_compare_as_dates(self):
        self._unpadded()
        proc = self._run(dict(SESSION_PAYLOAD, review_results=[]))  # 2026-04-24
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        # "2026-4-23" was yesterday: the streak goes on
        self.assertEqual(self._load("learner-profile.json")["current_streak_days"], 3)
        # the day's accuracy entry is the one of "2026-4-24", its date kept as it was written
        self.assertEqual(self._load("progress-db.json")["accuracy_trend"], [{"date": "2026-4-24", "accuracy": 0.8, "exercises": 7}])
        sr = self._load("spaced-repetition.json")
        # vocab_old ("2026-4-9") is overdue, vocab_dag (2026-04-30) due this week
        self.assertIn("vocab_old", sr["review_queue"]["today"])
        self.assertIn("vocab_dag", sr["review_queue"]["this_week"])
        # an entry is never rewritten only to add the zeros
        old = sr["items"]["vocab_old"]
        self.assertEqual((old["due_date"], old["created_date"], old["last_reviewed"]), ("2026-4-9", "2026-4-23", "2026-4-23"))

    def test_a_gentle_lowering_once_a_day_with_a_date_without_zeros(self):
        self._card(last_lapse="2026-4-24", lapses=1)
        proc = self._run(self._dialog("session-210", [{"item_id": "vocab_dag", "gentle": True}]))
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        dag = self._load("spaced-repetition.json")["items"]["vocab_dag"]
        self.assertEqual((dag["lapses"], dag["last_lapse"], dag["easiness_factor"], dag["interval_days"]), (1, "2026-4-24", 2.5, 6))

    def test_resurface_never_moves_a_card_later_with_a_date_without_zeros(self):
        self._reviewed_forms_card()
        self._write("spaced-repetition.json", lambda sr: sr["items"]["forms_hisa"].update(due_date="2026-5-2"))
        proc = self._run({"record_session": False, "date": "2026-5-2", "resurface": [{"item_id": "forms_hisa", "due_date": "2026-05-04"}]})
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        hisa = self._load("spaced-repetition.json")["items"]["forms_hisa"]
        self.assertEqual((hisa["due_date"], hisa["repetitions"]), ("2026-5-2", 0))  # due already (2026-05-02): kept
        self.assertEqual(hisa["resurfaced"], [{"date": "2026-05-02", "due": "2026-5-2"}])
        self.assertTrue((self.tmp / "data" / ".backups" / "pre-words-2026-05-02" / "spaced-repetition.json").exists())
        proc = self._run({"record_session": False, "date": "2026-05-02", "new_vocabulary": [dict(self.WORD, due_date="２０２６-05-02")]})
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        self.assertEqual(self._load("spaced-repetition.json")["items"]["vocab_word_gozd"]["due_date"], "2026-05-02")


@db_impl.needs_bun
class UpdateDbDiffTest(UpdateDbSmokeTest):
    """Every case above, by the Python and the TypeScript port at once: the same output and files."""
    impl = "diff"


if __name__ == "__main__":
    unittest.main()
