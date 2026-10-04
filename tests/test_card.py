#!/usr/bin/env python3
"""
Tests for .claude/hooks/card.py (find, show and fix review cards) on fixtures in a temp dir: never the
repository's data/.

Usage:
    python3 tests/test_card.py
"""
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CARD = REPO_ROOT / ".claude" / "hooks" / "card.py"
UPDATE = REPO_ROOT / ".claude" / "hooks" / "update-db.py"
SM2 = ("easiness_factor", "interval_days", "repetitions", "due_date", "last_reviewed", "last_quality",
       "total_reviews", "consecutive_correct", "consecutive_incorrect", "mastery_level", "priority")


def env_for(data_dir: Path) -> dict:
    """The scripts' environment, pinned to [data_dir]: never the repository's data/."""
    env = {k: v for k, v in os.environ.items() if k not in ("CLAUDE_PROJECT_DIR", "CLAUDE_PLUGIN_ROOT")}
    env["LANI_DATA_DIR"] = str(data_dir)
    return env


def card(cid, front, back, history=(), **extra):
    """A card as update-db.py leaves it after [history] (date, quality) reviews: its fields are the replay's."""
    item = {"id": cid, "type": "vocabulary", "content": front, "answer": back, "category": "basics", "difficulty": "A1",
            "created_date": "2026-08-26", "due_date": "2026-08-27", "interval_days": 1, "repetitions": 0,
            "easiness_factor": 2.5, "consecutive_correct": 0, "consecutive_incorrect": 0, "last_reviewed": "2026-08-26",
            "last_quality": 4, "mastery_level": 0, "total_reviews": 0, "priority": "medium"}
    item.update(extra)
    return item


def make_fixtures(d: Path):
    """A Slovene learner with this morning's two overlapping cards: "da" (yes) and "ja / ne" (yes / no)."""
    (d / "learner-profile.json").write_text(json.dumps({
        "learner": {"name": "Test", "target_language": "Slovene", "current_level": "A1", "target_level": "A2"},
        "home_language": "sl", "languages": [], "last_updated": "2026-09-25", "current_streak_days": 1,
        "total_sessions": 1, "total_study_minutes": 5, "skills": {}, "achievements": [], "preferences": {},
    }))
    for name, body in (("progress-db.json", {"overall_stats": {}, "skill_progress": {}}),
                       ("mastery-db.json", {"skills": {}}),
                       ("session-log.json", {"sessions": [{"session_id": "session-001", "date": "2026-09-25"}], "milestones": []})):
        (d / name).write_text(json.dumps(body))
    items = {
        "vocab_da": card("vocab_da", "da", "yes"),
        "vocab_ne": card("vocab_ne", "ja / ne", "yes / no"),
        "vocab_hvala": card("vocab_hvala", "hvala (lepa)", "thank you (very much)"),
        "vocab_ime_mi_je": card("vocab_ime_mi_je", "Ime mi je ...", "My name is ... (literally: name to me is)"),
        "vocab_imenujem_se": card("vocab_imenujem_se", "Imenujem se ...", "My name is ... (se in second position!)"),
        "vocab_fresh": card("vocab_fresh", "gozd", "forest"),
        # a pack word answered wrong while it was learned: its first grade only (update-db.py's initial_quality)
        "vocab_hrana_kruh": card("vocab_hrana_kruh", "kruh", "bread", last_quality=2, category="hrana"),
        "iz_genitive": {**card("iz_genitive", "iz Berlin", "iz Berlina"), "type": "error_pattern",
                        "consecutive_incorrect": 1, "last_quality": 2, "priority": "high"},
        "na_accusative": {**card("na_accusative", "na Triglavo", "na Triglav"), "type": "error_pattern",
                          "consecutive_incorrect": 1, "last_quality": 2, "priority": "high"},
    }
    (d / "spaced-repetition.json").write_text(json.dumps({
        "metadata": {"algorithm": "SM-2", "last_updated": "2026-09-25", "total_items_tracked": len(items), "language": "Slovene"},
        "review_queue": {"today": [], "tomorrow": [], "this_week": [], "later": list(items)},
        "items": items,
    }))
    (d / "mistakes-db.json").write_text(json.dumps({
        "metadata": {"last_updated": "2026-09-26", "total_patterns_tracked": 2, "language": "Slovene"},
        "error_patterns": {
            "iz_genitive": {"category": "grammar", "severity": "moderate", "frequency": 2, "mastery_level": 0,
                            "last_seen": "2026-09-26", "last_occurred": "2026-09-26", "next_review": "2026-09-27",
                            "consecutive_correct": 0, "consecutive_incorrect": 2, "notes": "",
                            "examples": [{"incorrect": "iz Berlin", "correct": "iz Berlina", "context": "", "date": "2026-09-20"},
                                         {"incorrect": "iz Ljubljana", "correct": "iz Ljubljane", "context": "", "date": "2026-09-26"}]},
            "na_accusative": {"category": "grammar", "severity": "moderate", "frequency": 1, "mastery_level": 0,
                              "last_seen": "2026-09-26", "last_occurred": "2026-09-26", "next_review": "2026-09-27",
                              "consecutive_correct": 0, "consecutive_incorrect": 1, "notes": "",
                              "examples": [{"incorrect": "na Triglavo", "correct": "na Triglav", "context": "", "date": "2026-09-26"}]},
        },
    }))


class CardTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="lani-card-test-"))
        self.data = self.tmp / "data"
        self.data.mkdir()
        make_fixtures(self.data)
        # Two reviews each, through update-db.py itself: the cards' scheduling is what the app's reviews leave.
        self.review("session-002", "2026-09-24", [("vocab_da", 3), ("vocab_ne", 5)])
        self.review("session-003", "2026-09-25", [("vocab_da", 4), ("vocab_ne", 4)])

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    # --- helpers ---

    def review(self, sid, date, results, data=None):
        payload = {"session_id": sid, "date": date, "duration_minutes": 1, "command_used": "/lani-app-review",
                   "skill_scores": {"vocabulary": {"exercises": len(results), "correct": sum(q >= 3 for _, q in results), "time_minutes": 1}},
                   "review_results": [{"item_id": i, "quality": q} for i, q in results]}
        d = data or self.data
        p = subprocess.run(["python3", str(UPDATE)], input=json.dumps(payload).encode(), env=env_for(d), cwd=str(self.tmp), capture_output=True)
        self.assertEqual(p.returncode, 0, p.stderr.decode())

    def run_card(self, *args, data=None):
        d = data or self.data
        return subprocess.run(["python3", str(CARD), "--data", str(d), *args], env=env_for(d), cwd=str(self.tmp), capture_output=True)

    def ok(self, *args, data=None) -> dict:
        p = self.run_card(*args, data=data)
        self.assertEqual(p.returncode, 0, f"stdout={p.stdout.decode()} stderr={p.stderr.decode()}")
        return json.loads(p.stdout)

    def refused(self, *args):
        before = self.raw()
        p = self.run_card(*args)
        self.assertEqual(p.returncode, 1, f"{args}: stdout={p.stdout.decode()} stderr={p.stderr.decode()}")
        self.assertIn("Error", p.stderr.decode())
        self.assertEqual(self.raw(), before, f"{args} wrote something")
        return p.stderr.decode()

    def raw(self):
        return {f.name: f.read_bytes() for f in self.data.glob("*.json")}

    def items(self, data=None):
        return json.loads(((data or self.data) / "spaced-repetition.json").read_text())["items"]

    def mistakes(self):
        return json.loads((self.data / "mistakes-db.json").read_text())

    # --- reading ---

    def test_show_says_how_the_app_asks_and_grades(self):
        hvala = self.ok("show", "vocab_hvala")["app"]
        self.assertEqual(hvala["accepted"], ["hvala", "hvala lepa"])  # "(…)" in the front: optional words
        self.assertEqual(hvala["prompt"], "thank you")  # "(…)" in the back: a note, not in the prompt
        self.assertEqual(hvala["note"], "very much")
        self.assertEqual(hvala["first"], "hvala")  # what dictation plays and tiles build
        self.assertNotIn("tiles", hvala["variants"])  # tiles need two words in the first answer
        placeholder = self.ok("show", "vocab_imenujem_se")
        self.assertEqual((placeholder["app"]["accepted"], placeholder["app"]["variants"]), ([], ["flip"]))
        self.assertFalse(self.ok("show", "iz_genitive")["app"]["shown"])  # an error pattern is never a card

    def test_show_finds_this_mornings_problem(self):
        ne = self.ok("show", "vocab_ne")
        self.assertEqual(ne["app"]["accepted"], ["ja", "ne"])
        self.assertTrue(any("several meanings at once" in p for p in ne["problems"]), ne["problems"])
        self.assertEqual([(o["id"], o["same_prompt"]) for o in ne["overlaps"]], [("vocab_da", ["yes"])])
        self.assertEqual(ne["card"]["review_history"][-1], {"date": "2026-09-25", "quality": 4, "score": 0})

    def test_find_ranks_answers_first(self):
        found = self.ok("find", "ja")
        self.assertEqual([c["id"] for c in found["cards"]], ["vocab_ne"])
        self.assertEqual(found["cards"][0]["match"], "answer")
        yes = self.ok("find", "YES")  # case-insensitive, and prompts count as answers
        self.assertEqual({c["id"] for c in yes["cards"]}, {"vocab_da", "vocab_ne"})
        lepa = self.ok("find", "lepa")["cards"][0]  # an optional word
        self.assertEqual((lepa["id"], lepa["match"]), ("vocab_hvala", "word"))
        self.assertEqual(self.ok("find", "berli")["cards"][0]["id"], "iz_genitive")  # part of a word, 3 letters or more
        self.assertEqual(self.ok("find", "zzz")["count"], 0)

    def test_overlaps_only_where_an_answer_can_be_wrong(self):
        pairs = self.ok("overlaps")["pairs"]
        self.assertEqual([p["ids"] for p in pairs], [["vocab_da", "vocab_ne"]])  # the placeholders are flashcards only
        self.assertEqual(self.ok("overlaps", "vocab_da")["overlaps"][0]["id"], "vocab_ne")

    def test_language_option_reads_that_languages_cards(self):
        it = self.data / "languages" / "it"
        it.mkdir(parents=True)
        (it / "spaced-repetition.json").write_text(json.dumps({"items": {"vocab_si": card("vocab_si", "sì", "yes")}}))
        self.assertEqual([c["id"] for c in self.ok("find", "yes", "--language", "Italian")["cards"]], ["vocab_si"])
        self.assertEqual(self.ok("find", "si", "--language", "sl")["count"], 0)  # the home language: data/ itself

    def test_options_before_or_after_the_command(self):
        p = subprocess.run(["python3", str(CARD), "show", "vocab_da", "--data", str(self.data)], env=env_for(self.tmp / "nowhere"), capture_output=True)
        self.assertEqual(p.returncode, 0, p.stderr.decode())
        p = subprocess.run(["python3", str(CARD), "--data", str(self.data), "show", "vocab_da"], env=env_for(self.tmp / "nowhere"), capture_output=True)
        self.assertEqual(p.returncode, 0, p.stderr.decode())

    # --- edit: this morning's fix ---

    def test_edit_keeps_history_and_scheduling(self):
        before = self.items()
        others = {k: v for k, v in self.raw().items() if k != "spaced-repetition.json"}
        yes = self.ok("edit", "vocab_da", "--front", "da / ja", "--note", "da = written/standard, ja = spoken",
                      "--reason", "ja was wrong on the da card", "--today", "2026-09-27")
        no = self.ok("edit", "vocab_ne", "--front", "ne", "--back", "no", "--today", "2026-09-27")
        after = self.items()
        for cid in ("vocab_da", "vocab_ne"):
            for k in SM2 + ("review_history", "created_date", "category", "id", "type"):
                self.assertEqual(after[cid].get(k), before[cid].get(k), f"{cid}.{k} changed")
        self.assertEqual((after["vocab_da"]["content"], after["vocab_da"]["answer"]), ("da / ja", "yes (da = written/standard, ja = spoken)"))
        self.assertEqual(after["vocab_da"]["edits"], [{"date": "2026-09-27", "action": "edit", "content": "da", "answer": "yes",
                                                      "reason": "ja was wrong on the da card"}])
        self.assertEqual(yes["app"]["accepted"], ["da", "ja"])
        self.assertEqual(yes["app"]["prompt"], "yes")
        self.assertEqual((after["vocab_ne"]["content"], after["vocab_ne"]["answer"]), ("ne", "no"))
        self.assertEqual(no["warnings"], [])  # no overlap left
        self.assertEqual(self.ok("overlaps")["count"], 0)
        # a backup of the file as it was, and nothing else written
        backup = Path(yes["backup"]) / "spaced-repetition.json"
        self.assertEqual(json.loads(backup.read_text())["items"]["vocab_da"]["content"], "da")
        self.assertTrue(str(backup).startswith(str(self.data / ".backups" / "pre-card-edit-")))
        self.assertEqual({k: v for k, v in self.raw().items() if k != "spaced-repetition.json"}, others)
        # the next review goes on from the kept scheduling
        self.review("session-004", "2026-10-01", [("vocab_da", 5)])
        self.assertEqual(self.items()["vocab_da"]["total_reviews"], 3)

    def test_edit_note_replaces_the_notes(self):
        self.ok("edit", "vocab_hvala", "--note", "lepa: very much, optional")
        self.assertEqual(self.items()["vocab_hvala"]["answer"], "thank you (lepa: very much, optional)")
        self.ok("edit", "vocab_hvala", "--note", "")
        self.assertEqual(self.items()["vocab_hvala"]["answer"], "thank you")

    def test_edit_dry_run_writes_nothing(self):
        before = self.raw()
        out = self.ok("edit", "vocab_da", "--front", "da / ja", "--dry-run")
        self.assertTrue(out["dry_run"])
        self.assertEqual(out["after"]["content"], "da / ja")
        self.assertEqual(self.raw(), before)
        self.assertEqual(list((self.data / ".backups").glob("pre-card-*")), [])

    def test_edit_refuses_bad_input(self):
        self.assertIn("did you mean vocab_da", self.refused("edit", "vocab_d", "--front", "x"))
        self.refused("edit", "vocab_da")  # nothing to change
        self.refused("edit", "vocab_da", "--front", "da")  # the same text
        self.refused("edit", "vocab_da", "--front", "   ")
        self.refused("edit", "vocab_da", "--front", "da\nja")
        self.refused("edit", "vocab_da", "--back", "yes (spoken")
        self.refused("edit", "vocab_da", "--note", "(x)")
        self.refused("edit", "vocab_da", "--front", "Da, ...")  # a placeholder: nothing to type
        self.ok("edit", "vocab_da", "--front", "Da, ...", "--force")  # … unless it should be a flashcard

    # --- regrade ---

    def test_regrade_equals_a_review_graded_right(self):
        """A review graded wrong through the card's fault, regraded, leaves the card exactly as update-db.py would have
        had the review been graded right."""
        right = self.tmp / "right"
        shutil.copytree(self.data, right)
        self.review("session-004", "2026-09-27", [("vocab_da", 1)])
        self.review("session-004", "2026-09-27", [("vocab_da", 4)], data=right)
        self.assertTrue(self.ok("show", "vocab_da")["app"]["rusty"])
        out = self.ok("regrade", "vocab_da", "--quality", "4", "--reason", "every answer was wrong on one of the cards", "--today", "2026-09-27")
        fixed, expected = self.items()["vocab_da"], self.items(right)["vocab_da"]
        self.assertEqual({k: fixed.get(k) for k in SM2}, {k: expected.get(k) for k in SM2})
        self.assertEqual(out["rusty"], {"before": True, "after": False})
        self.assertEqual(out["review"], {"date": "2026-09-27", "quality": {"from": 1, "to": 4}})
        self.assertEqual(fixed["review_history"][-1]["regraded"], {"from": 1, "date": "2026-09-27", "reason": "every answer was wrong on one of the cards"})
        self.assertIn("vocab_da", json.loads((self.data / "spaced-repetition.json").read_text())["review_queue"]["later"])
        # a second regrade (back to the truth) replays again
        self.ok("regrade", "vocab_da", "--quality", "1")
        self.assertTrue(self.ok("show", "vocab_da")["app"]["rusty"])

    def test_regrade_a_review_by_its_date(self):
        self.ok("regrade", "vocab_ne", "--quality", "2", "--date", "2026-09-24")
        ne = self.items()["vocab_ne"]
        self.assertEqual([e["quality"] for e in ne["review_history"]], [2, 4])
        self.assertEqual((ne["repetitions"], ne["interval_days"], ne["due_date"]), (1, 1, "2026-09-26"))

    def test_regrade_a_first_grade(self):
        """A pack word graded wrong while it was learned has no reviews: its first grade is what changes."""
        self.assertFalse(self.ok("show", "vocab_hrana_kruh")["app"]["learned"])
        out = self.ok("regrade", "vocab_hrana_kruh", "--quality", "4", "--reason", "kruh was right", "--today", "2026-09-27")
        self.assertEqual(out["first_grade"], {"from": 2, "to": 4})
        self.assertEqual(out["after"]["learned"], True)
        kruh = self.items()["vocab_hrana_kruh"]
        self.assertEqual((kruh["last_quality"], kruh["repetitions"], kruh["due_date"], kruh["total_reviews"]), (4, 0, "2026-08-27", 0))
        self.assertEqual(kruh["edits"][-1], {"date": "2026-09-27", "action": "regrade", "last_quality": 2, "reason": "kruh was right"})
        self.refused("regrade", "vocab_hrana_kruh", "--quality", "3", "--date", "2026-09-27")  # no review that day to change

    def test_regrade_refuses(self):
        self.refused("regrade", "vocab_fresh", "--quality", "4")  # its first grade is 4 already
        self.refused("regrade", "vocab_da", "--quality", "4")  # already 4
        self.refused("regrade", "vocab_da", "--quality", "4", "--date", "2026-01-01")  # no review that day
        self.refused("regrade", "vocab_da", "--quality", "5", "--date", "yesterday")
        # scheduling changed by hand: a replay would lose it
        sr = json.loads((self.data / "spaced-repetition.json").read_text())
        sr["items"]["vocab_da"]["interval_days"] = 30
        (self.data / "spaced-repetition.json").write_text(json.dumps(sr))
        self.assertIn("doesn't follow from its review history", self.refused("regrade", "vocab_da", "--quality", "5"))
        self.ok("regrade", "vocab_da", "--quality", "5", "--force")

    # --- merge and split ---

    def test_merge_keeps_one_card_and_everything_of_the_other(self):
        out = self.ok("merge", "vocab_da", "vocab_ne", "--front", "da / ja", "--today", "2026-09-27", "--reason", "one card for yes")
        items = self.items()
        self.assertNotIn("vocab_ne", items)
        da = items["vocab_da"]
        self.assertEqual(da["content"], "da / ja")
        self.assertEqual(da["merged"][0]["id"], "vocab_ne")
        self.assertEqual(da["merged"][0]["item"]["review_history"][0]["quality"], 5)  # nothing of it is lost
        self.assertEqual(da["total_reviews"], 2)  # the kept card's own scheduling
        self.assertTrue(any("['ne']" in w for w in out["warnings"]), out["warnings"])  # no card takes "ne" any more
        sr = json.loads((self.data / "spaced-repetition.json").read_text())
        self.assertEqual(sr["metadata"]["total_items_tracked"], len(items))
        self.assertNotIn("vocab_ne", sum(sr["review_queue"].values(), []))
        self.refused("merge", "vocab_da", "vocab_da")
        self.refused("merge", "vocab_da", "iz_genitive")

    def test_split_makes_a_new_card(self):
        out = self.ok("split", "vocab_ne", "--front", "ne", "--back", "no", "--new-id", "vocab_ja", "--new-front", "ja",
                      "--new-back", "yes", "--new-note", "spoken; written: da", "--today", "2026-09-27")
        items = self.items()
        self.assertEqual((items["vocab_ne"]["content"], items["vocab_ne"]["total_reviews"]), ("ne", 2))
        ja = items["vocab_ja"]
        self.assertEqual((ja["content"], ja["answer"], ja["split_from"]), ("ja", "yes (spoken; written: da)", "vocab_ne"))
        self.assertEqual((ja["due_date"], ja["repetitions"], ja["total_reviews"], ja["easiness_factor"]), ("2026-09-28", 0, 0, 2.5))
        self.assertTrue(any("vocab_da" in w for w in out["warnings"]), out["warnings"])  # "yes" is also vocab_da's
        self.refused("split", "vocab_ne", "--new-id", "vocab_da", "--new-front", "x", "--new-back", "y")
        self.refused("split", "vocab_ne", "--new-id", "bad id", "--new-front", "x", "--new-back", "y")

    # --- forgive ---

    def test_forgive_takes_back_one_mistake(self):
        out = self.ok("forgive", "iz_genitive", "--reason", "the exercise accepted only one form", "--today", "2026-09-27")
        pat = self.mistakes()["error_patterns"]["iz_genitive"]
        self.assertEqual(out["removed"]["incorrect"], "iz Ljubljana")  # the latest example
        self.assertEqual((pat["frequency"], pat["consecutive_incorrect"], pat["last_seen"]), (1, 1, "2026-09-20"))
        self.assertEqual(pat["forgiven"][0]["reason"], "the exercise accepted only one form")
        self.assertEqual(len(pat["examples"]), 1)
        self.assertIn("iz_genitive", self.items())  # its review item stays: the pattern does

    def test_forgive_the_only_mistake_removes_the_pattern(self):
        out = self.ok("forgive", "na_accusative", "--date", "2026-09-26", "--answer", "Na Triglavo!")
        self.assertTrue(out["deleted"])
        m = self.mistakes()
        self.assertNotIn("na_accusative", m["error_patterns"])
        self.assertEqual(m["metadata"]["total_patterns_tracked"], 1)
        self.assertNotIn("na_accusative", self.items())  # the review item the mistake made, never reviewed
        self.assertTrue((Path(out["backup"]) / "mistakes-db.json").exists())
        self.assertTrue((Path(out["backup"]) / "spaced-repetition.json").exists())

    def test_forgive_refuses(self):
        self.refused("forgive", "no_such_pattern")
        self.refused("forgive", "iz_genitive", "--date", "2026-01-01")
        self.refused("forgive", "iz_genitive", "--date", "2026-09-26", "--answer", "something else")


if __name__ == "__main__":
    unittest.main()
