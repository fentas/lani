#!/usr/bin/env python3
"""
Language profiles (docs/DB_SCRIPTS.md, "Languages"): the migration of single-language data, writing
to another language, and read-db.py's summaries and --language.

Every run works on copies in a temp dir (LANI_DATA_DIR): data-examples/, and the fixtures below;
never the repository's data/.

Usage:
    python3 tests/test_languages.py
"""
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_update_db import REPO_ROOT, SESSION_PAYLOAD, make_fixtures, script_env  # noqa: E402

HOOKS = REPO_ROOT / ".claude" / "hooks"
EXAMPLES = REPO_ROOT / "data-examples"


def jan_like(data_dir: Path):
    """A realistic learner from before language profiles: Slovene, words learned, a rusty one, a looked-up one, an
    error pattern, sessions (the shapes of the real data, none of its content)."""
    item = lambda **kw: {"type": "vocabulary", "category": "greetings", "difficulty": "A1", "created_date": "2026-09-01",
                         "due_date": "2099-01-01", "interval_days": 6, "repetitions": 2, "easiness_factor": 2.5,
                         "consecutive_correct": 2, "consecutive_incorrect": 0, "last_reviewed": "2026-09-20",
                         "last_quality": 4, "mastery_level": 1, "total_reviews": 2, "priority": "medium", **kw}
    files = {
        "learner-profile.json": {
            "learner": {"name": "Jan", "native_language": "English and German", "other_languages": ["German"],
                        "target_language": "Slovene", "current_level": "A1", "target_level": "B2", "daily_goal_minutes": 60},
            "profile_created": "2026-08-25", "last_updated": "2026-09-25", "current_streak_days": 4,
            "total_sessions": 26, "total_study_minutes": 900, "skills": {}, "focus_areas": [], "achievements": [],
            "preferences": {"use_emojis": True},
        },
        "spaced-repetition.json": {
            "metadata": {"algorithm": "SM-2", "last_updated": "2026-09-25", "total_items_tracked": 4, "language": "Slovene"},
            "review_queue": {"today": [], "tomorrow": [], "this_week": [], "later": []},
            "daily_limits": {"new": 10},
            "items": {
                "vocab_hvala": item(id="vocab_hvala", content="hvala", answer="thank you", due_date="2026-01-01"),
                "vocab_miza": item(id="vocab_miza", content="miza", answer="table", repetitions=0, last_quality=1, total_reviews=3),
                "vocab_word_gozd": item(id="vocab_word_gozd", content="gozd", answer="forest", repetitions=0, mastery_level=0,
                                        total_reviews=0, last_quality=3, source="lookup"),
                "sklon_dajalnik": item(id="sklon_dajalnik", type="error_pattern", content="z mami", answer="z mamo", due_date="2026-01-01"),
            },
        },
        "mistakes-db.json": {"metadata": {"last_updated": "2026-09-25", "total_patterns_tracked": 1, "language": "Slovene"},
                             "error_patterns": {"sklon_dajalnik": {"category": "grammar", "frequency": 3, "examples": []}}},
        "mastery-db.json": {"metadata": {"last_updated": "2026-09-25", "language": "Slovene"}, "skills": {}, "patterns": {},
                            "mastery_scale": {"0": "Not started"}},
        "progress-db.json": {"metadata": {"last_updated": "2026-09-25", "language": "Slovene", "tracking_started": "2026-08-25"},
                             "overall_stats": {"total_sessions": 26, "total_exercises": 300, "total_correct": 240,
                                               "total_incorrect": 60, "accuracy_rate": 0.8, "total_study_minutes": 900,
                                               "average_session_duration": 35},
                             "accuracy_trend": [], "skill_progress": {}, "weekly_summary": []},
        "session-log.json": {"metadata": {"language": "Slovene", "learner_name": "Jan", "total_sessions": 26},
                             "sessions": [{"session_id": "session-026", "date": "2026-09-25", "duration_minutes": 30}],
                             "milestones": []},
    }
    for name, data in files.items():
        (data_dir / name).write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")


def from_examples(data_dir: Path, fill: dict = None):
    """data-examples/ copied as they are (placeholders and all), or filled like the bridge's initLearnerData."""
    for f in EXAMPLES.glob("*-template.json"):
        text = f.read_text()
        data = json.loads(text)
        if fill and f.name == "learner-profile-template.json":
            data["learner"].update(fill)
            data.pop("home_language", None)
            data.pop("languages", None)
        (data_dir / f.name.replace("-template", "")).write_text(json.dumps(data, indent=2) if fill else text)


ITALIAN_SESSION = {
    "session_id": "session-it-001",
    "date": "2026-04-24",
    "language": "it",
    "duration_minutes": 10,
    "command_used": "/lani-visit",
    "skills_practiced": ["speaking"],
    "skill_scores": {"speaking": {"exercises": 4, "correct": 3, "time_minutes": 10}},
    "errors": [{"pattern_id": "articolo_lo", "category": "grammar", "your_answer": "il zaino", "correct_answer": "lo zaino",
                "severity": "moderate"}],
    "new_vocabulary": [
        {"item_id": "vocab_word_buongiorno", "item_type": "vocabulary", "content": "buongiorno", "answer": "good morning",
         "category": "visit", "difficulty": "A1", "initial_quality": 4},
        {"item_id": "vocab_word_grazie", "item_type": "vocabulary", "content": "grazie", "answer": "thank you",
         "category": "visit", "difficulty": "A1", "initial_quality": 2},
    ],
}


class LanguagesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="lani-languages-"))
        self.data = self.tmp / "data"
        self.data.mkdir()

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    # --- helpers ---

    def run_script(self, name, args=(), payload=None):
        return subprocess.run(
            ["python3", str(HOOKS / name), *args],
            input=json.dumps(payload).encode() if payload is not None else b"",
            cwd=str(self.tmp), env=script_env(self.data), capture_output=True,
        )

    def read(self, *args):
        p = self.run_script("read-db.py", args)
        self.assertIn(p.returncode, (0, 1), msg=p.stderr)
        return json.loads(p.stdout)

    def update(self, payload, code=0):
        p = self.run_script("update-db.py", payload=payload)
        self.assertEqual(p.returncode, code, msg=f"stdout={p.stdout!r} stderr={p.stderr!r}")
        return p

    def load(self, rel):
        return json.loads((self.data / rel).read_text())

    def raw(self):
        """Every file under data/ except the backups, by relative path."""
        return {str(f.relative_to(self.data)): f.read_bytes() for f in self.data.rglob("*.json") if ".backups" not in f.parts}

    def backups(self, prefix):
        b = self.data / ".backups"
        return sorted(d.name for d in b.iterdir() if d.name.startswith(prefix)) if b.is_dir() else []

    # --- migration ---

    def test_migration_adds_the_home_language_after_a_backup(self):
        jan_like(self.data)
        before = self.raw()
        self.read()
        profile = self.load("learner-profile.json")
        self.assertEqual(profile["home_language"], "sl")
        self.assertEqual(profile["languages"], [])
        # everything else as it was: the profile's other keys, and the other five files byte for byte
        old = json.loads(before["learner-profile.json"])
        self.assertEqual({k: v for k, v in profile.items() if k not in ("home_language", "languages")}, old)
        after = self.raw()
        for name, data in before.items():
            if name != "learner-profile.json":
                self.assertEqual(after[name], data, f"{name} changed")
        # the backup holds every database as it was before
        [backup] = self.backups("pre-migrate-languages-")
        for name, data in before.items():
            self.assertEqual((self.data / ".backups" / backup / name).read_bytes(), data, f"backup of {name}")

    def test_migration_is_idempotent(self):
        jan_like(self.data)
        self.read()
        once = self.raw()
        self.read()
        self.read("--language", "it")
        self.update({"record_session": False, "date": "2026-09-26",
                     "new_vocabulary": [{"item_id": "vocab_hvala", "content": "hvala", "answer": "thanks"}]})
        self.assertEqual(self.raw(), once)
        self.assertEqual(len(self.backups("pre-migrate-languages-")), 1)

    def test_migrated_data_is_left_alone(self):
        make_fixtures(self.data)  # migrated already: home_language nl, languages []
        before = self.raw()
        self.read()
        self.assertEqual(self.raw(), before)
        self.assertEqual(self.backups("pre-migrate-languages-"), [])

    def test_update_db_migrates_before_it_writes(self):
        make_fixtures(self.data, migrated=False)
        self.update(SESSION_PAYLOAD)
        profile = self.load("learner-profile.json")
        self.assertEqual((profile["home_language"], profile["languages"]), ("nl", []))
        self.assertEqual(profile["total_sessions"], 2)  # the session went home, as always
        self.assertEqual(len(self.backups("pre-migrate-languages-")), 1)
        # the migration's backup is the data before it; the session's is the migrated data
        pre = json.loads((self.data / ".backups" / self.backups("pre-migrate-languages-")[0] / "learner-profile.json").read_text())
        self.assertNotIn("home_language", pre)
        mid = json.loads((self.data / ".backups" / "pre-update-session-002" / "learner-profile.json").read_text())
        self.assertEqual(mid["home_language"], "nl")

    def test_the_templates_as_they_are_are_not_migrated(self):
        from_examples(self.data)  # placeholders: the target language isn't known
        before = self.raw()
        state = self.read()
        self.assertEqual(self.raw(), before)
        self.assertEqual(self.backups("pre-migrate-languages-"), [])
        self.assertIsNone(state["language"])
        self.assertEqual([l["source"] for l in state["languages"]], ["home"])

    def test_templates_filled_for_a_new_learner(self):
        from_examples(self.data, {"name": "Luka", "target_language": "Italian", "target_language_code": "it",
                                  "current_level": "A1", "target_level": "A2"})
        state = self.read()
        self.assertEqual(self.load("learner-profile.json")["home_language"], "it")
        self.assertEqual(state["language"], "it")
        self.assertEqual(state["languages"][0]["code"], "it")

    # --- writing to another language ---

    def test_a_session_in_another_language_goes_to_its_own_databases(self):
        jan_like(self.data)
        self.read()  # migrated
        home_before = self.raw()
        p = self.update(ITALIAN_SESSION)
        self.assertIn("Italian", p.stdout.decode())
        it = self.data / "languages" / "it"
        for name in ("learner-profile.json", "progress-db.json", "mistakes-db.json", "mastery-db.json",
                     "spaced-repetition.json", "session-log.json"):
            self.assertTrue((it / name).exists(), f"languages/it/{name}")
        sr = self.load("languages/it/spaced-repetition.json")
        self.assertEqual(set(sr["items"]), {"vocab_word_buongiorno", "vocab_word_grazie", "articolo_lo"})
        self.assertEqual(sr["metadata"]["language"], "Italian")
        self.assertIn("articolo_lo", self.load("languages/it/mistakes-db.json")["error_patterns"])
        self.assertEqual(self.load("languages/it/session-log.json")["sessions"][-1]["session_id"], "session-it-001")
        lp = self.load("languages/it/learner-profile.json")
        self.assertEqual((lp["learner"]["current_level"], lp["current_streak_days"], lp["total_sessions"], lp["source"]), ("A1", 1, 1, "visits"))
        # the home language's data: untouched but for the list of languages
        after = self.raw()
        for name, data in home_before.items():
            if name != "learner-profile.json":
                self.assertEqual(after[name], data, f"{name} changed")
        home = self.load("learner-profile.json")
        self.assertEqual(home["languages"], [{"code": "it", "name": "Italian", "source": "visits", "since": "2026-04-24", "level": "A1"}])
        self.assertEqual((home["current_streak_days"], home["total_sessions"]), (4, 26))
        # backed up first: the home profile as it was, and no files of a language that didn't exist
        backup = self.data / ".backups" / "pre-update-it-session-it-001"
        self.assertEqual(json.loads((backup / "learner-profile.json").read_text())["languages"], [])

    def test_a_second_session_counts_on_in_that_language(self):
        make_fixtures(self.data)
        self.update(ITALIAN_SESSION)
        again = dict(ITALIAN_SESSION, session_id="session-it-002", date="2026-04-25", new_vocabulary=[],
                     errors=[], review_results=[{"item_id": "vocab_word_grazie", "quality": 4}])
        self.update(again)
        lp = self.load("languages/it/learner-profile.json")
        self.assertEqual((lp["current_streak_days"], lp["total_sessions"]), (2, 2))
        grazie = self.load("languages/it/spaced-repetition.json")["items"]["vocab_word_grazie"]
        self.assertEqual((grazie["total_reviews"], grazie["last_quality"]), (1, 4))
        self.assertTrue((self.data / ".backups" / "pre-update-it-session-it-002" / "languages" / "it" / "spaced-repetition.json").exists())
        self.assertEqual(self.load("learner-profile.json")["current_streak_days"], 2)  # the home streak: as it was

    def test_a_language_by_its_name_and_the_home_language_by_its_code(self):
        make_fixtures(self.data)
        self.update(dict(ITALIAN_SESSION, language="Italian"))
        self.assertTrue((self.data / "languages" / "it" / "spaced-repetition.json").exists())
        self.update(dict(SESSION_PAYLOAD, language="nl"))  # the home language: data/, as without a language
        self.assertIn("het_huis", self.load("spaced-repetition.json")["items"])
        self.assertFalse((self.data / "languages" / "nl").exists())

    def test_a_level_sets_the_language_level_and_its_copy_in_the_list(self):
        make_fixtures(self.data)
        self.update(dict(ITALIAN_SESSION, level="a2"))
        self.assertEqual(self.load("languages/it/learner-profile.json")["learner"]["current_level"], "A2")
        self.assertEqual(self.load("learner-profile.json")["languages"][0]["level"], "A2")

    def test_bad_language_or_level_writes_nothing(self):
        make_fixtures(self.data)
        before = self.raw()
        for bad in (dict(ITALIAN_SESSION, language="Klingon!"), dict(ITALIAN_SESSION, language=7), dict(ITALIAN_SESSION, level="A7"),
                    {"record_session": False, "date": "2026-04-24", "language": "??",
                     "new_vocabulary": ITALIAN_SESSION["new_vocabulary"]}):
            with self.subTest(case=bad):
                self.update(bad, code=1)
                self.assertEqual(self.raw(), before)
        self.assertFalse((self.data / "languages").exists())

    def test_a_word_added_in_another_language(self):
        make_fixtures(self.data)
        before = self.raw()
        word = {"item_id": "vocab_word_ciao", "item_type": "vocabulary", "content": "ciao", "answer": "hi", "source": "lookup"}
        self.update({"record_session": False, "date": "2026-04-24", "language": "it", "new_vocabulary": [word]})
        self.assertEqual(set(self.load("languages/it/spaced-repetition.json")["items"]), {"vocab_word_ciao"})
        self.assertEqual(self.load("languages/it/session-log.json")["sessions"], [])  # no session
        after = self.raw()
        for name in ("spaced-repetition.json", "session-log.json", "progress-db.json"):
            self.assertEqual(after[name], before[name])
        self.assertEqual([e["code"] for e in self.load("learner-profile.json")["languages"]], ["it"])
        self.assertTrue((self.data / ".backups" / "pre-words-it-2026-04-24" / "learner-profile.json").exists())
        # the next word only touches that language's review items
        mid = self.raw()
        self.update({"record_session": False, "date": "2026-04-25", "language": "it",
                     "new_vocabulary": [dict(word, item_id="vocab_word_casa", content="casa", answer="house")]})
        changed = {k for k, v in self.raw().items() if mid.get(k) != v}
        self.assertEqual(changed, {"languages/it/spaced-repetition.json"})

    # --- read-db.py ---

    def test_read_db_sums_up_every_language(self):
        jan_like(self.data)
        self.update(ITALIAN_SESSION)
        state = self.read()
        # the home language's data, as always
        self.assertIn("vocab_hvala", state["databases"]["spaced_repetition"]["items"])
        self.assertEqual(state["computed"]["next_session_id"], "session-027")
        self.assertEqual(state["language"], "sl")
        home, it = state["languages"]
        # hvala learned; miza rusty; gozd looked up; the error pattern isn't a review card
        self.assertEqual({k: home[k] for k in ("code", "level", "words", "source")}, {"code": "sl", "level": "A1", "words": 1, "source": "home"})
        self.assertEqual(home["due"], 1)
        # buongiorno answered right while learned; grazie not; both due tomorrow of 2026-04-24, so due now
        self.assertEqual({k: it[k] for k in ("code", "name", "level", "words", "source", "since")},
                         {"code": "it", "name": "Italian", "level": "A1", "words": 1, "source": "visits", "since": "2026-04-24"})
        self.assertEqual(it["due"], 2)

    def test_read_db_one_language_in_full(self):
        jan_like(self.data)
        self.update(ITALIAN_SESSION)
        state = self.read("--language", "it")
        self.assertEqual(state["language"], "it")
        db = state["databases"]
        self.assertEqual(set(db["spaced_repetition"]["items"]), {"vocab_word_buongiorno", "vocab_word_grazie", "articolo_lo"})
        learner = db["learner_profile"]["learner"]
        # the learner is the home profile's, the language and level the language's
        self.assertEqual((learner["name"], learner["target_language"], learner["target_language_code"], learner["current_level"]),
                         ("Jan", "Italian", "it", "A1"))
        self.assertEqual(learner["daily_goal_minutes"], 60)
        self.assertEqual(db["learner_profile"]["preferences"], {"use_emojis": True})
        self.assertEqual(state["computed"]["next_session_id"], "session-it-002")
        self.assertEqual(state["computed"]["due_reviews_count"], 3)
        self.assertEqual([l["code"] for l in state["languages"]], ["sl", "it"])
        # by its name too; the home language by its code is the default output
        self.assertEqual(self.read("--language", "Italian")["databases"], db)
        self.assertEqual(self.read("--language", "sl")["databases"], self.read()["databases"])

    def test_read_db_a_language_never_practised_reads_empty_and_writes_nothing(self):
        make_fixtures(self.data)
        before = self.raw()
        p = self.run_script("read-db.py", ("--language", "de"))
        self.assertEqual(p.returncode, 0, msg=p.stderr)
        state = json.loads(p.stdout)
        self.assertEqual(state["language"], "de")
        self.assertEqual(state["databases"]["spaced_repetition"]["items"], {})
        self.assertEqual(state["databases"]["learner_profile"]["learner"]["target_language"], "German")
        self.assertEqual(state["computed"]["next_session_id"], "session-de-001")
        self.assertNotIn("_warnings", state)
        self.assertEqual(self.raw(), before)
        self.assertEqual([l["code"] for l in state["languages"]], ["nl"])

    def test_read_db_refuses_what_isnt_a_language(self):
        make_fixtures(self.data)
        p = self.run_script("read-db.py", ("--language", "not a language"))
        self.assertEqual(p.returncode, 2)
        self.assertEqual(p.stdout, b"")


if __name__ == "__main__":
    unittest.main()
