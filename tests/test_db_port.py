#!/usr/bin/env python3
"""
The TypeScript port of the learner-data scripts (companion/bridge/src/db/, the phone's) against the Python
(.claude/hooks/, the node's): the same stdout, stderr, exit codes and files, byte for byte (tests/db_impl.py).

- Building blocks: Python's json (values, errors, floats as Python writes them), round(), float formats, strptime,
  int(), float(), str methods, on generated and mutated inputs (companion/bridge/test/db-port-probe.ts).
- Scenarios: a learner over weeks of sessions in three languages (reviews, gentle lowerings, new words, counting and
  forms cards, mistakes, milestones, levels, items-only reports, resurfaced forms cards), read-db.py between them,
  then every card.py command; the examples of docs/DB_SCRIPTS.md; the migration; bad reports and broken files; the
  command lines' errors and help; where the data directory is found; validate-data.py.

The existing cases of tests/test_update_db.py, tests/test_card.py and tests/test_languages.py run through both
implementations too (their *DiffTest classes). Skipped when bun isn't installed.

Usage:
    python3 tests/test_db_port.py
"""
import json
import math
import os
import random
import shutil
import subprocess
import sys
import tempfile
import unicodedata
import unittest
from datetime import datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import db_impl  # noqa: E402

REPO_ROOT = db_impl.REPO_ROOT
PROBE = REPO_ROOT / "companion" / "bridge" / "test" / "db-port-probe.ts"
EXAMPLES = REPO_ROOT / "data-examples"


# --- the building blocks: what Python does, case by case ---------------------------------------------------------

def py_case(c):
    kind = c[0]
    try:
        if kind == "json":
            v = json.loads(c[1])
            return [json.dumps(v, indent=2, ensure_ascii=False), json.dumps(v, ensure_ascii=False)]
        if kind == "round":
            return repr(round(float(c[1]), c[2]) if c[2] is not None else round(float(c[1])))
        if kind == "fixed":
            return f"{float(c[1]):.{c[2]}f}"
        if kind == "repr":
            return repr(float(c[1]))
        if kind == "date":
            return (datetime.strptime(c[1], "%Y-%m-%d") + timedelta(days=c[2])).strftime("%Y-%m-%d")
        if kind == "int":
            return str(int(c[1]))
        if kind == "float":
            return repr(float(c[1]))
        if kind == "str":
            s = c[2]
            return {"strip": s.strip, "lower": s.lower, "repr": lambda: repr(s), "len": lambda: len(s)}[c[1]]()
    except Exception as e:  # noqa: BLE001 - the error is the result
        return f"{type(e).__name__}: {e}"
    return f"unknown case {kind}"


def mutations(doc: str, rng: random.Random, n: int) -> list:
    """[doc] with a character dropped, doubled, swapped or replaced: mostly invalid JSON, each with its error."""
    out = []
    alphabet = '{}[]",:\\ \n\t0123456789.eE+-nulltruefalsex\u00e9\U0001f525\x01'
    for _ in range(n):
        s = list(doc)
        for _ in range(rng.randint(1, 3)):
            if not s:
                break
            i = rng.randrange(len(s))
            op = rng.randrange(4)
            if op == 0:
                del s[i]
            elif op == 1:
                s.insert(i, s[i])
            elif op == 2:
                s[i] = rng.choice(alphabet)
            else:
                s.insert(i, rng.choice(alphabet))
        out.append("".join(s))
    return out


def float_corpus(rng: random.Random) -> list:
    """Floats as reprs: every magnitude, the edges of repr's notation, ties of round(), the scripts' own sums."""
    xs = ["0.0", "-0.0", "1.0", "2.0", "0.1", "0.2", "0.30000000000000004", "1e16", "1e-05", "0.0001", "1234567890123456.0",
          "12345678901234567.0", "5e-324", "1.7976931348623157e+308", "inf", "-inf", "nan", "2.675", "0.125", "0.375",
          "2.5", "3.5", "-2.5", "0.5", "1.5", "12.5", "0.0625", "1e+22", "123456.789", "-1e-07", "9.995", "1.005"]
    for _ in range(400):
        e = rng.randint(-12, 22)
        xs.append(repr(rng.uniform(-1, 1) * 10 ** e))
    for _ in range(200):
        a, b = rng.randint(0, 50), rng.randint(1, 50)
        xs.append(repr(a / b))
        xs.append(repr(a / b * 100))
        xs.append(repr(2.5 + (0.1 - (5 - rng.randint(0, 5)) * (0.08 + (5 - rng.randint(0, 5)) * 0.02))))
    for k in range(0, 2000, 7):
        xs.append(repr(k / 1000 + 0.0005))
        xs.append(repr(k / 8))
    return xs


@db_impl.needs_bun
class BuildingBlocksTest(unittest.TestCase):
    """Python's json, round(), formats, dates, int(), float() and str methods: the port's py.ts and pyjson.ts."""

    def compare(self, cases):
        p = subprocess.run([db_impl.BUN, str(PROBE)], input=json.dumps(cases).encode(), capture_output=True)
        self.assertEqual(p.returncode, 0, p.stderr.decode())
        got = [json.loads(line) for line in p.stdout.decode().split("\n") if line]
        self.assertEqual(len(got), len(cases))
        bad = [(c, py_case(c), g) for c, g in zip(cases, got) if py_case(c) != g]
        self.assertEqual(bad[:5], [], f"{len(bad)} of {len(cases)} cases differ")
        return len(cases)

    def test_json_values_and_errors(self):
        rng = random.Random(5)
        docs = [f.read_text(encoding="utf-8") for f in sorted(EXAMPLES.glob("*.json"))]
        docs += ['{"a": 1, "b": 2.0, "c": [1.5, -0.0, 1e16, 1e-05], "d": {"0": "x", "10": "y", "2": "z", "__proto__": 1}}',
                 '{"k": "a\\u00e9\\ud83d\\ude00\\ud800 \\"q\\" \\\\ \\/ \\b\\f\\n\\r\\t \\u0001\\u001f\\u007f \u2028"}',
                 '[12345678901234567890, -0, -0.0, 1E5, 1e+5, 0.000001, NaN, Infinity, -Infinity, true, false, null]',
                 '{"a": 1, "a": 2, "b": {"x": 1}, "b": [3]}', ' \n\t{} \r\n', '[]', '""', '"\U0001f525\U0001f525"',
                 '{"🔥": [1, {"x": "🔥"}], "y": "ünïcöd\u00e9"}', '\ufeff{}']
        cases = [["json", d] for d in docs]
        for d in docs[:8]:
            cases += [["json", m] for m in mutations(d, rng, 60)]
        bad = ['', ' ', '{', '{"a"', '{"a":', '{"a":1', '{"a":1,', '{"a":1,}', '[1,]', '[1 2]', '"abc', '"a\x01"', '"\\x"',
               '"\\u12"', '"\\u12zz"', '{} x', '-', '01', '1.', '[-]', 'nul', '[1,\n2,,]', '{"a" 1}', '{1:2}', '[', '{"a":1 "b"',
               '[\n\n  "🔥🔥", x]', '{"🔥":1, x}', '"\\ud83d\\uzzzz"', '"abc\\', '{"a":[}', '[,1]', '{,}', '{"a":-}', '[1e]', '[.5]']
        cases += [["json", b] for b in bad]
        self.compare(cases)

    def test_floats_rounding_and_formats(self):
        rng = random.Random(7)
        xs = float_corpus(rng)
        cases = [["repr", x] for x in xs]
        cases += [["round", x, nd] for x in xs for nd in (None, 0, 2, 3)]
        cases += [["fixed", x, 0] for x in xs]
        cases += [["json", f"[{x}]"] for x in xs if x not in ("inf", "-inf", "nan")]
        self.compare(cases)

    def test_dates_and_conversions(self):
        dates = ["2026-04-24", "2026-4-5", "2026-02-29", "2024-02-29", "2026-13-01", "0000-01-01", "0001-01-01", "9999-12-31",
                 "２０２６-04-05", "2026-04-05\n", " 2026-04-05", "2026-04-05 ", "yesterday", "", "2026-04-31", "2026-1-1",
                 "2026-12-3 1", "1900-02-29", "2000-02-29", "2026-04- 5"]
        cases = [["date", d, n] for d in dates for n in (0, 1, -1, 7, 30, 6.0, 2.5, -0.5, 10 ** 7, 3652059)]
        for s in ["4", " 4 ", "+4", "-4", "0_4", "4_", "٤", "4.0", "", "x", "1_000", "１２", "--4", "4 4", "99999999999999999999999"]:
            cases += [["int", s], ["float", s]]
        for s in ["1e5", "inf", "-Infinity", "nan", ".5", "5.", "1_0.5", "1e", "0x10", " 2.5 ", "1e400"]:
            cases.append(["float", s])
        texts = ["  a b  ", "\x1c\x1dx\x1e\x1f", "\ufeffx\ufeff", "\u00a0\u2003y\u3000", "ÄÖÜ İ ΣΑΣ ß", "🔥x🔥", "a'b\"c",
                 "tab\there", "nl\nx", "\x7f\x00\u200b", "\U0001f525" * 3, "\u0301e", "'", '"', "\\"]
        for t in texts:
            cases += [["str", op, t] for op in ("strip", "lower", "repr", "len")]
        self.compare(cases)


# --- the scripts: scenarios run by both, step by step -------------------------------------------------------------

def write(path: Path, data, raw: str = None):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(raw if raw is not None else json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def from_examples(data: Path, **learner):
    """data-examples/ filled for a learner (as the bridge's initLearnerData does), migrated to language profiles."""
    for f in EXAMPLES.glob("*-template.json"):
        d = json.loads(f.read_text(encoding="utf-8"))
        if f.name.startswith("learner-profile"):
            d["learner"].update(learner)
            d.pop("home_language", None)
            d.pop("languages", None)
        write(data / f.name.replace("-template", ""), d)


class Scenario(unittest.TestCase):
    """A temp root (data/, home/), and each script run by both implementations there."""

    def setUp(self):
        self.root = Path(tempfile.mkdtemp(prefix="lani-db-port-"))
        self.data = self.root / "data"
        self.home = self.root / "home"
        self.home.mkdir()
        self.runs = 0
        self.now = db_impl.FROZEN_NOW

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)
        shutil.rmtree(self.root.parent / f".{self.root.name}.db-port", ignore_errors=True)

    def env(self, **extra):
        env = {k: v for k, v in os.environ.items() if not k.startswith(("LANI_", "FLUENT_", "CLAUDE_"))}
        env.update(HOME=str(self.home), LANI_DATA_DIR=str(self.data), LANI_ENV_FILE=os.devnull, LANI_NOW=self.now)
        for k, v in extra.items():
            if v is None:
                env.pop(k, None)
            else:
                env[k] = v
        return env

    def run_both(self, script, args=(), stdin=None, env=None, cwd=None, code=None):
        """[script] by both implementations (they must agree); the Python's result. [code]: the exit code expected."""
        payload = stdin if isinstance(stdin, (bytes, type(None))) else json.dumps(stdin, ensure_ascii=False).encode()
        p = db_impl.run("diff", script, args, payload or b"", env or self.env(), cwd or self.root, self.root)
        self.runs += 1
        if code is not None:
            self.assertEqual(p.returncode, code, f"{script} {args}: stdout={p.stdout.decode()} stderr={p.stderr.decode()}")
        return p

    def update(self, report, code=None, **kw):
        return self.run_both("update-db", stdin=report, code=code, **kw)

    def read(self, *args, code=None, **kw):
        return self.run_both("read-db", args, code=code, **kw)

    def card(self, *args, code=None, **kw):
        return self.run_both("card", ["--data", str(self.data), *args], code=code, **kw)


SL_WORDS = [("hvala", "thank you"), ("prosim", "please"), ("dober dan", "good day (polite hello)"), ("hiša", "house"),
            ("miza", "table"), ("kava", "coffee"), ("voda", "water"), ("kruh", "bread"), ("da", "yes"), ("ne", "no"),
            ("ja / ne", "yes / no"), ("hvala (lepa)", "thank you (very much)"), ("Imenujem se ...", "My name is ..."),
            ("na svidenje", "goodbye"), ("oče", "father"), ("mama", "mother"), ("knjiga", "book"), ("pes", "dog"),
            ("mačka", "cat"), ("šola", "school"), ("dober/dobra/dobro → good", "good (by gender)"), ("Kako si?", "How are you?"),
            ("zelo dobro, hvala", "very well, thanks"), ("delati", "to work"), ("gozd", "forest"), ("čaj", "tea"),
            ("ena dva tri štiri pet šest sedem osem devet deset", "one to ten"), ("jaz", "I"), ("ti", "you (informal)")]
IT_WORDS = [("ciao", "hi / bye"), ("grazie", "thank you"), ("buongiorno", "good morning"), ("casa", "house"), ("acqua", "water")]
DE_WORDS = [("danke", "thank you"), ("Haus", "house"), ("Wasser", "water")]
PATTERNS = [("iz_genitive", "grammar", "iz Ljubljana", "iz Ljubljane"), ("na_accusative", "grammar", "na Triglavo", "na Triglav"),
            ("clitic_order", "word_order", "Se imenujem", "Imenujem se"), ("dual_verbs", "grammar", "midva gremo", "midva greva"),
            ("counting_from_five", "counting", "pet kava", "pet kav"), ("forms_case_locative", "forms", "v hiša", "v hiši")]


def counting(lemma, gender, lines, forms):
    return {"v": 1, "lemma": lemma, "gender": gender, "lines": lines, "others": [], "table": "wiktionary",
            "forms": dict(zip(("nom.sg", "nom.du", "nom.pl", "gen.pl"), forms)), "emoji": "☕", "meaning": "coffee"}


FORMS_HISA = {"v": 1, "lemma": "hiša", "pos": "noun", "gender": "f", "gloss": ["house"], "table": "wiktionary",
              "slots": [{"key": "nom.sg", "forms": ["hiša"], "page": "imenovalnik"},
                        {"key": "acc.sg", "forms": ["hišo"], "page": "tozilnik"},
                        {"key": "loc.sg", "forms": ["hiši"], "page": "mestnik", "needs": ["tozilnik"]},
                        {"key": "dat.sg", "forms": ["hiši"], "also": ["hiše"], "page": "dajalnik"}],
              "tested": ["acc.sg", "loc.sg"], "fresh": [], "unlocked": "2026-09-01"}
FORMS_DELATI = {"v": 1, "lemma": "delati", "pos": "verb", "gloss": ["to work"], "table": "wiktionary",
                "slots": [{"key": "pres.1sg", "forms": ["delam"], "page": "glagoli-sedanjik"},
                          {"key": "fut.3pl", "forms": ["bodo delali"], "also": ["bojo delali"], "page": "prihodnjik", "composed": True},
                          {"key": "imp.2sg", "forms": ["delaj"], "page": "velelnik"}],
                "tested": ["pres.1sg", "imp.2sg"], "fresh": ["imp.2sg"], "unlocked": "2026-09-25", "asked": True}


@db_impl.needs_bun
class LearnerOverWeeksTest(Scenario):
    """Weeks of sessions in three languages, read-db.py between them, then every card.py command."""

    def word(self, rng, i, words, prefix="vocab_"):
        front, back = words[i % len(words)]
        item = {"item_id": f"{prefix}{i}_{front.split()[0].lower()}", "item_type": "vocabulary", "content": front,
                "answer": back, "category": rng.choice(["basics", "food", "dialog", "family"]), "difficulty": "A1",
                "initial_quality": rng.choice([2, 3, 4, 5, 4.0]), "priority": rng.choice(["medium", "high", "low"])}
        if rng.random() < 0.3:
            item.update(example=f"  {front} je tu.  ", example_translation=f"{back} is here.")
        if rng.random() < 0.2:
            item["source"] = rng.choice(["lookup", "pack", "  "])
        return item

    def session(self, rng, day: str, n: int, known: list, language=None, prefix="session-"):
        skills = {}
        for s in rng.sample(["vocabulary", "writing", "speaking", "reading", "listening"], rng.randint(1, 3)):
            ex = rng.randint(0, 12)
            skills[s] = {"exercises": ex, "correct": rng.randint(0, ex), "time_minutes": rng.choice([5, 10, 2.5, 7, 0])}
        reviews = []
        for iid in rng.sample(known, min(len(known), rng.randint(0, 12))):
            r = {"item_id": iid, "quality": rng.choice([0, 1, 2, 3, 4, 5, 5, 4, 3.0])}
            if rng.random() < 0.15:
                r = {"item_id": iid, "gentle": True, **({"quality": 1} if rng.random() < 0.5 else {})}
            if rng.random() < 0.3:
                r["score"] = rng.choice([0, 1, 0.5, 10])
            reviews.append(r)
        if rng.random() < 0.1:
            reviews.append({"item_id": "vocab_not_there", "quality": 4})
        errors = []
        for pid, cat, wrong, right in rng.sample(PATTERNS, rng.randint(0, 3)):
            e = {"pattern_id": pid, "category": cat, "your_answer": wrong, "correct_answer": right, "context": "a dialog"}
            if rng.random() < 0.5:
                e.update(severity=rng.choice(["critical", "moderate", "minor"]), difficulty_score=rng.choice([0.7, 1, 0.25]),
                         subcategory="cases", description="iz + genitive")
            if rng.random() < 0.3:
                e["notes"] = rng.choice(["", "again", "the dual"])
            errors.append(e)
        report = {"session_id": f"{prefix}{n:03d}", "date": day, "duration_minutes": rng.choice([5, 15, 20, 2.5, 0]),
                  "command_used": rng.choice(["/lani-learn", "/lani-app-review", "/lani-visit"]),
                  "skill_scores": skills, "review_results": reviews, "errors": errors,
                  "topics_covered": ["greetings"], "breakthroughs": [], "session_notes": "notes ✨"}
        if rng.random() < 0.3:
            report["focus_areas"] = ["cases", "dual"]
        if rng.random() < 0.2:
            report["focus_next_session"] = ["genitive"]
        if rng.random() < 0.2:
            report["milestones"] = rng.choice([["Reached 50 words 🎉"], [{"milestone": "First dialog", "date": "2026-09-03"}],
                                               [{"milestone": "Bad date", "date": "soon"}, "  Second  "],
                                               ["Mastered the perfect tense for regular verbs"]])
        if rng.random() < 0.1:
            report["level"] = rng.choice(["a2", "A2", " b1 "])
        if rng.random() < 0.1:
            report["exam_focus"] = "A2 oral"
            report["critical_errors_identified"] = ["iz_genitive"]
        if language:
            report["language"] = language
        return report

    def test_weeks_of_practice_then_the_card_commands(self):
        rng = random.Random(2026)
        from_examples(self.data, name="Jan", target_language="Slovene", target_language_code="sl", current_level="A1",
                      target_level="B2")
        self.read(code=0)  # the migration: home_language sl
        known = {"sl": [], "it": [], "de": []}
        sessions = {"sl": 0, "it": 0, "de": 0}
        day0 = datetime(2026, 9, 1)
        word_no = 0
        for d in range(42):
            day = (day0 + timedelta(days=d)).strftime("%Y-%m-%d")
            self.now = f"{day}T{rng.randint(6, 22):02d}:{rng.randint(0, 59):02d}:00"
            if rng.random() < 0.2:
                continue  # a day off: the streak breaks
            for _ in range(rng.choice([1, 1, 2])):
                lang = rng.choice(["sl", "sl", "sl", "it", "de"])
                sessions[lang] += 1
                code = None if lang == "sl" else lang
                report = self.session(rng, day, sessions[lang], known[lang], code, "session-" if lang == "sl" else f"session-{lang}-")
                words = {"sl": SL_WORDS, "it": IT_WORDS, "de": DE_WORDS}[lang]
                report["new_vocabulary"] = []
                for _ in range(rng.randint(0, 4)):
                    word_no += 1
                    w = self.word(rng, word_no, words, f"vocab_{lang}_" if lang != "sl" else "vocab_")
                    report["new_vocabulary"].append(w)
                    known[lang].append(w["item_id"])
                if lang == "sl" and d % 9 == 3:
                    cid = f"counting_kava_{d}"
                    report["new_vocabulary"].append({"item_id": cid, "item_type": "counting", "content": "kava",
                                                     "answer": "ena kava · dve kavi · tri kave · pet kav", "category": "counting",
                                                     "source": "counting", "due_date": day,
                                                     "counting": counting("kava", "f", ["ena kava", "dve kavi", "tri kave", "pet kav"],
                                                                          [["kava"], ["kavi"], ["kave"], ["kav"]])})
                    known[lang].append(cid)
                for e in report["errors"]:
                    if e["pattern_id"] not in known[lang]:
                        known[lang].append(e["pattern_id"])
                self.update(report, code=0)
            if d % 5 == 2:
                self.read(code=0)
                self.read("--language", rng.choice(["it", "Italian", "de", "German", "sl", "fr"]), code=0)
            if d % 7 == 4:
                word_no += 1
                lang = rng.choice(["sl", "it"])
                w = self.word(rng, word_no, SL_WORDS if lang == "sl" else IT_WORDS, f"vocab_word_{lang}_")
                w["source"] = "lookup"
                rep = {"record_session": False, "date": day, "new_vocabulary": [w, dict(w)]}
                if lang != "sl":
                    rep["language"] = lang
                self.update(rep, code=0)
                known[lang].append(w["item_id"])
            if d == 10:
                forms = {"item_id": "forms_hisa", "item_type": "forms", "content": "hiša", "answer": "hišo · hiši · hiši",
                         "category": "forms", "difficulty": "A1", "source": "forms", "due_date": day, "forms": FORMS_HISA}
                delati = dict(forms, item_id="forms_delati", content="delati", answer="delam · delaj", forms=FORMS_DELATI)
                self.update({"record_session": False, "date": day, "new_vocabulary": [forms, delati]}, code=0)
                known["sl"] += ["forms_hisa", "forms_delati"]
            if d in (20, 33):
                due = (day0 + timedelta(days=d + 1)).strftime("%Y-%m-%d")
                grown = dict(FORMS_HISA, tested=["acc.sg", "loc.sg", "dat.sg"], fresh=["dat.sg"], unlocked=day, resurface=due)
                self.update({"record_session": False, "date": day,
                             "resurface": [{"item_id": "forms_hisa", "due_date": due, "forms": grown},
                                           {"item_id": "forms_delati", "due_date": due},
                                           {"item_id": known["sl"][0], "due_date": due},
                                           {"item_id": "forms_nope", "due_date": due},
                                           {"item_id": "forms_hisa", "due_date": "2027-01-01"}]}, code=0)
        self.read(code=0)
        self.read("--language", "it", code=0)

        # every card.py command, on the cards these weeks made
        self.now = "2026-10-15T20:00:00"
        for q in ("hvala", "YES", "lepa", "kav", "zzz", "ja", "iz", "ciao", "house", "a", "Hvála!"):
            self.card("find", q, code=0)
        self.card("find", "house", "--language", "it", code=0)
        self.card("find", "hvala", "--limit", "2", code=0)
        self.card("find", "a", "--limit", "-1", code=0)
        for cid in known["sl"][:12] + ["forms_hisa", "forms_delati", "iz_genitive", "vocab_nope"]:
            self.card("show", cid)
        self.card("show", known["it"][0], "--language", "Italian")
        self.card("overlaps", code=0)
        self.card("overlaps", known["sl"][3])
        sl = known["sl"]
        self.card("edit", sl[0], "--front", "hvala / hvala lepa", "--note", "lepa: very much", "--reason", "both are right",
                  "--today", "2026-10-15")
        self.card("edit", sl[1], "--note", "", "--dry-run")
        self.card("edit", sl[1], "--category", " phrases ")
        self.card("edit", sl[2], "--front", "Dober dan, ...")
        self.card("edit", sl[2], "--front", "Dober dan, ...", "--force")
        self.card("edit", sl[3], "--back", "house (a building)")
        self.card("edit", sl[3], "--back", "house (a building")
        self.card("edit", sl[4], "--front", "x" * 201)
        self.card("edit", sl[4])
        for cid in sl[5:14]:
            self.card("regrade", cid, "--quality", str(rng.randint(0, 5)), "--reason", "the card's fault")
        self.card("regrade", sl[5], "--quality", "5", "--force")
        self.card("regrade", sl[6], "--quality", "4", "--date", "2026-09-10")
        self.card("regrade", sl[6], "--quality", "4", "--date", "yesterday")
        self.card("merge", sl[14], sl[15], "--front", "a / b", "--reason", "one card")
        self.card("merge", sl[16], sl[16])
        self.card("merge", sl[17], "iz_genitive")
        self.card("split", sl[18], "--front", "ena", "--back", "one", "--new-id", "vocab_dva_new", "--new-front", "dva",
                  "--new-back", "two", "--new-note", "the dual")
        self.card("split", sl[19], "--new-id", "bad id", "--new-front", "x", "--new-back", "y")
        self.card("split", sl[19], "--new-id", sl[20], "--new-front", "x", "--new-back", "y")
        for pid, *_ in PATTERNS:
            self.card("forgive", pid, "--reason", "the exercise accepted one form only")
        self.card("forgive", "clitic_order", "--date", "2026-01-01")
        self.card("forgive", "nope")
        self.card("forgive", "dual_verbs", "--answer", "Midva gremo!")
        self.read(code=0)
        self.assertGreater(self.runs, 130)


@db_impl.needs_bun
class DocsExamplesTest(Scenario):
    """The reports and commands of docs/DB_SCRIPTS.md, on a learner that has what they name."""

    def setUp(self):
        super().setUp()
        sys.path.insert(0, str(REPO_ROOT / "tests"))
        import test_card
        self.data.mkdir()
        test_card.make_fixtures(self.data)

    def test_the_documented_reports_and_commands(self):
        doc = (REPO_ROOT / "docs" / "DB_SCRIPTS.md").read_text(encoding="utf-8")
        # the full report: the first ```bash block with a heredoc
        report = json.loads(doc.split("update-db.py\" <<'EOF'\n")[1].split("\nEOF")[0])
        self.update(dict(report, date="2026-09-26"), code=0)
        items_only = json.loads(doc.split("A word met outside practice")[1].split("```json\n")[1].split("\n```")[0])
        self.update(items_only, code=0)
        self.update({"session_id": "session-010", "date": "2026-09-27", "duration_minutes": 2,
                     "review_results": [{"item_id": "vocab_word_gozd", "quality": 2, "gentle": True}]}, code=0)
        resurface = json.loads(doc.split("bring forms cards back soon")[1].split("```json\n")[1].split("\n```")[0])
        self.update(resurface, code=0)
        self.update(dict(report, session_id="session-it-001", language="it", level="A2", date="2026-09-28"), code=0)
        self.read(code=0)
        self.read("--language", "it", code=0)
        for line in doc.split('card="${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/hooks/card.py"\n')[1].split("```")[0].splitlines():
            if line.startswith('python3 "$card" '):
                import shlex
                args = shlex.split(line.split('python3 "$card" ')[1].split("  #")[0])
                self.card(*args, "--today", "2026-09-29")


@db_impl.needs_bun
class MigrationTest(Scenario):
    def test_single_language_data_with_unlisted_language_directories(self):
        sys.path.insert(0, str(REPO_ROOT / "tests"))
        from test_languages import jan_like
        self.data.mkdir()
        jan_like(self.data)
        write(self.data / "languages" / "it" / "learner-profile.json",
              {"learner": {"current_level": "A2"}, "source": "visits", "profile_created": "2026-09-02"})
        write(self.data / "languages" / "de" / "learner-profile.json", {"learner": "odd", "profile_created": ""})
        write(self.data / "languages" / "xx" / "spaced-repetition.json", {"items": {}})
        (self.data / "languages" / "fr").mkdir(parents=True)
        write(self.data / "languages" / "fr" / "learner-profile.json", None, raw="{ broken")
        # migrated; a language whose learner isn't an object, and one whose profile isn't JSON, left out of the summaries
        self.read(code=0)
        self.read("--language", "de", code=2)
        self.read("--language", "fr", code=2)
        write(self.data / "languages" / "de" / "learner-profile.json", {"learner": {"current_level": "B1"}, "profile_created": ""})
        self.read(code=0)  # only French left out
        (self.data / "languages" / "it" / "progress-db.json").mkdir()  # a file that can't be opened
        (self.data / "languages" / "it" / "session-log.json").write_bytes(b'{"x": "\xff"}')  # nor read as UTF-8
        self.read(code=0)
        self.read("--language", "it", code=2)
        (self.data / "languages" / "it" / "progress-db.json").rmdir()
        self.read(code=0)
        (self.data / "languages" / "it" / "session-log.json").unlink()
        write(self.data / "languages" / "fr" / "learner-profile.json", {"source": "visits"})
        self.read(code=0)
        self.read(code=0)  # already migrated: nothing written
        self.read("--language", "de", code=0)

    def test_update_db_migrates_first_and_a_broken_profile_is_a_warning(self):
        sys.path.insert(0, str(REPO_ROOT / "tests"))
        from test_update_db import SESSION_PAYLOAD, make_fixtures
        self.data.mkdir()
        make_fixtures(self.data, migrated=False)
        self.update(SESSION_PAYLOAD, code=0)
        write(self.data / "learner-profile.json", None, raw='{"learner": [}')
        self.update(dict(SESSION_PAYLOAD, session_id="session-003"))
        self.read()

    def test_templates_as_they_are(self):
        self.data.mkdir()
        for f in EXAMPLES.glob("*-template.json"):
            shutil.copy(f, self.data / f.name.replace("-template", ""))
        self.read(code=0)
        self.update({"session_id": "session-001", "date": "2026-10-18", "skill_scores": {"vocabulary": {"exercises": 3, "correct": 2}}})
        self.update({"record_session": False, "date": "2026-10-18", "new_vocabulary": [{"item_id": "vocab_x", "content": "x", "answer": "y"}]})


@db_impl.needs_bun
class BadInputTest(Scenario):
    """Bad reports, broken files and odd values: the same refusals, errors and exit codes."""

    def setUp(self):
        super().setUp()
        sys.path.insert(0, str(REPO_ROOT / "tests"))
        from test_update_db import make_fixtures, SESSION_PAYLOAD
        self.data.mkdir()
        make_fixtures(self.data)
        self.payload = SESSION_PAYLOAD

    def test_reports_that_are_refused(self):
        for raw in [b"", b"   ", b"{", b"[1, 2]", b'"x"', b"null", b'{"a": 1,}', b"{\"a\": \"\x01\"}", b'\xef\xbb\xbf{}',
                    b'{"session_id": "s", "date": "2026-04-24"} x', '{"🔥": 1, x}'.encode(), b"\xff\xfe", b'{"a": "\xc3"}']:
            self.update(raw)
        base = dict(self.payload, session_id="session-050")
        for bad in [{"date": "2026-04-24"}, {"session_id": "s"}, dict(base, milestones="x"), dict(base, milestones=[42]),
                    dict(base, milestones=[{"milestone": " "}]), dict(base, milestones=[""]), dict(base, milestones={"a": 1}),
                    dict(base, language="Klingon!"), dict(base, language=7), dict(base, language=["it"]), dict(base, language="  "),
                    dict(base, level="A7"), dict(base, level=2), dict(base, level=None), dict(base, language="NL"),
                    {"record_session": False, "date": "2026-04-24"}, {"record_session": False, "date": "24.4.2026", "new_vocabulary": [{"item_id": "a"}]},
                    {"record_session": False, "date": 20260424, "new_vocabulary": [{"item_id": "a"}]},
                    {"record_session": False, "date": "2026-04-24", "new_vocabulary": [{"item_id": " "}]},
                    {"record_session": False, "date": "2026-04-24", "new_vocabulary": [{"item_id": "a"}], "errors": [{"pattern_id": "x"}]},
                    {"record_session": False, "date": "2026-04-24", "resurface": {"item_id": "x"}},
                    {"record_session": False, "date": "2026-04-24", "resurface": [{"item_id": "x", "due_date": "2026-04-25"}]},
                    {"record_session": False, "date": "2026-4-24", "new_vocabulary": [{"item_id": "vocab_q", "due_date": "2026-04-24"}]},
                    {"record_session": 0, "session_id": "session-051", "date": "2026-04-24"}]:
            self.update(bad)

    def test_values_python_would_choke_on(self):
        base = {"session_id": "session-060", "date": "2026-04-24"}
        for odd in [dict(base, date="yesterday"), dict(base, date=20260424), dict(base, date="2026-02-30"),
                    dict(base, duration_minutes="10"), dict(base, duration_minutes=2.5),
                    dict(base, skill_scores={"vocabulary": {"exercises": "5", "correct": 1}}),
                    dict(base, skill_scores={"vocabulary": {"exercises": 5}}), dict(base, skill_scores=["vocabulary"]),
                    dict(base, skill_scores={"vocabulary": {"exercises": 4, "correct": 3.0, "time_minutes": 1.5}}),
                    dict(base, review_results=[{"item_id": "vocab_dag"}]), dict(base, review_results=[{"item_id": "vocab_dag", "quality": "4"}]),
                    dict(base, review_results=[{"item_id": "vocab_dag", "quality": None}]), dict(base, review_results=[{"quality": 4}]),
                    dict(base, review_results=[{"item_id": ["x"], "quality": 4}]), dict(base, review_results="abc"),
                    dict(base, errors=[{"pattern_id": "p", "your_answer": 5}]), dict(base, errors=[{}]),
                    dict(base, new_vocabulary=[{"item_id": 5, "content": "x"}]), dict(base, new_vocabulary=[{"content": "x"}]),
                    dict(base, session_id=5), dict(base, session_id="a/b"), dict(base, focus_areas=[], topics_covered=None)]:
            self.update(odd)
            base["session_id"] = base["session_id"] + "1" if isinstance(base["session_id"], str) else "session-061"

    def test_broken_database_files(self):
        sr = self.data / "spaced-repetition.json"
        good = sr.read_text(encoding="utf-8")
        for raw in ['{"items": [1, 2]}', '[]', '{"items": {"vocab_dag": "x"}}', '{ "items": {}, }', '\ufeff{"items": {}}',
                    '{"items": {"a": {"due_date": 5}}}', '{"items": {"a": {"due_date": null}}}', None]:
            if raw is None:
                sr.unlink()
            else:
                sr.write_text(raw, encoding="utf-8")
            self.read()
            self.update(dict(self.payload, session_id="session-070"))
            self.update({"record_session": False, "date": "2026-04-24", "new_vocabulary": [{"item_id": "vocab_new"}]})
            self.card("show", "vocab_dag")
            self.card("find", "dag")
            sr.write_text(good, encoding="utf-8")
        (self.data / "session-log.json").write_bytes(b'{"sessions": [{"session_id": "\xff"}]}')
        self.read()
        self.card("find", "dag")
        (self.data / "session-log.json").write_text('{"sessions": [{"session_id": 7}]}')
        self.read()
        (self.data / "session-log.json").write_text('{"sessions": {"a": 1}}')
        self.read()
        (self.data / "learner-profile.json").write_text('{"last_updated": 20260424}')
        self.read()
        (self.data / "learner-profile.json").write_text('{"last_updated": "2026-13-01", "learner": {"name": 5}}')
        self.read()
        self.read("--language", "it")
        (self.data / "mistakes-db.json").unlink()
        self.read()
        self.update(dict(self.payload, session_id="session-071"))
        self.card("forgive", "x")

    def test_numbers_as_python_keeps_them(self):
        path = self.data / "progress-db.json"
        d = json.loads(path.read_text())
        d["overall_stats"].update(total_sessions=1.0, total_study_minutes=10.0, accuracy_rate=1)
        d["skill_progress"]["vocabulary"]["accuracy"] = 1
        d["weekly_summary"] = [{"week_start": "2026-04-20", "sessions": 2.0, "total_minutes": 5, "accuracy": 0}]
        d["huge"] = 123456789012345678901234567890
        path.write_text(json.dumps(d))
        sr = self.data / "spaced-repetition.json"
        s = json.loads(sr.read_text())
        s["items"]["vocab_dag"].update(easiness_factor=2, interval_days=6.0, repetitions=2.0, total_reviews=True, lapses=1e3)
        s["items"]["vocab_two"] = dict(s["items"]["vocab_dag"], id="vocab_two", easiness_factor=1.3000000000000003, interval_days=2.5)
        sr.write_text(json.dumps(s))
        self.update(dict(self.payload, session_id="session-080", duration_minutes=2.5,
                         review_results=[{"item_id": "vocab_dag", "quality": 5.0}, {"item_id": "vocab_two", "quality": 3, "score": 0.1}],
                         skill_scores={"vocabulary": {"exercises": 7, "correct": 5, "time_minutes": 0.1}}), code=0)
        self.update(dict(self.payload, session_id="session-081", date="2026-04-25",
                         review_results=[{"item_id": "vocab_two", "gentle": True}, {"item_id": "vocab_dag", "gentle": True}]), code=0)
        self.read(code=0)


@db_impl.needs_bun
class CommandLineTest(Scenario):
    """argparse's parsing, errors and help texts, for card.py and read-db.py."""

    def setUp(self):
        super().setUp()
        sys.path.insert(0, str(REPO_ROOT / "tests"))
        import test_card
        self.data.mkdir()
        test_card.make_fixtures(self.data)

    def test_card_command_lines(self):
        d = str(self.data)
        for args in [[], ["-h"], ["--help"], ["bogus"], ["show"], ["show", "a", "b"], ["--d", d, "show", "vocab_da"],
                     ["--data=" + d, "show", "vocab_da"], ["show", "vocab_da", "--data", d], ["show", "--", "-x", "--data", d],
                     ["--new", "x"], ["edit", "x", "--new"], ["edit", "vocab_da", "--data", d, "--fr", "da"], ["regrade", "x", "--quality", "9"],
                     ["regrade", "x", "--quality", "q"], ["regrade", "x", "--quality", "٤", "--data", d], ["regrade", "x", "--quality"],
                     ["split", "x"], ["split", "vocab_ne", "--data", d, "--new-id", "vocab_j", "--new-front", "ja", "--new-back", "yes",
                                      "--new-front", "jaa"], ["find", "x", "--limit"], ["find", "x", "--limit", "x"],
                     ["edit", "x", "--dry-run=1"], ["edit", "x", "--front", "-y"], ["edit", "x", "--front=-y", "--data", d],
                     ["find", "-1", "--data", d], ["find", "a b", "--data", d], ["-hx"], ["show", "-hx"], ["find", "-x"],
                     ["overlaps", "--data", d], ["overlaps", "", "--data", d], ["merge", "a"], ["forgive"],
                     ["find", "da", "--data", d, "--today", "2026-1-1"], ["find", "da", "--data", d, "--language", "{x}"],
                     ["find", "da", "--data", d, "--language", "it\n"], ["show", "vocab_da", "--data", d, "--lang", "sl"]] \
                + [[c, "-h"] for c in ("find", "show", "overlaps", "edit", "regrade", "merge", "split", "forgive")]:
            self.run_both("card", args)

    def test_read_db_command_lines(self):
        for args in [["-h"], ["--lang"], ["x"], ["--language=it", "--language"], ["--language", "Italian"], ["--l", "it"],
                     ["--language", "not a language"], ["--language", ""], ["--language=sl"], ["--", "x"], ["-x"]]:
            self.run_both("read-db", args)

    def test_unknown_script_names_are_not_run(self):
        p = subprocess.run([db_impl.BUN, str(db_impl.CLI), "nope"], capture_output=True)
        self.assertEqual(p.returncode, 2)


@db_impl.needs_bun
class DataDirTest(Scenario):
    """Where the data directory is found (lani_paths.data_dir), as each script finds it."""

    def check(self, env, cwd=None):
        cwd = cwd or self.root
        # learners.ts's data_dir() (it creates nothing), then the scripts'
        py = subprocess.run(["python3", "-c", "import sys; sys.path.insert(0, sys.argv[1]); from lani_paths import data_dir; print(data_dir())",
                             str(REPO_ROOT / ".claude" / "hooks")], env=env, cwd=str(cwd), capture_output=True)
        ts = subprocess.run([db_impl.BUN, str(PROBE)], input=json.dumps([["datadir", env, str(cwd)]]).encode(), capture_output=True)
        self.assertEqual(json.loads(ts.stdout), py.stdout.decode().rstrip("\n"), env)
        self.run_both("ensure_data_dir", env=env, cwd=cwd)
        self.run_both("read-db", env=env, cwd=cwd)

    def test_the_places_and_their_order(self):
        sys.path.insert(0, str(REPO_ROOT / "tests"))
        from test_update_db import make_fixtures
        project = self.root / "project"
        (project / "data").mkdir(parents=True)
        make_fixtures(project / "data")
        cwd_data = self.root / "cwd"
        (cwd_data / "data").mkdir(parents=True)
        make_fixtures(cwd_data / "data")
        env_file = self.root / "lani.env"
        env_file.write_text(f'# the node\nexport LANI_DATA_DIR="{self.root}/from-env-file"  # a comment\nLANI_CULTURE=x\n'
                            f"LANI_RESULTS_DIR=~/results\nLANI_BRIDGE_HOST='0.0.0.0'\n")
        (self.root / "from-env-file").mkdir()
        make_fixtures(self.root / "from-env-file")
        self.check(self.env())
        self.check(self.env(LANI_DATA_DIR="data"), cwd=cwd_data)
        self.check(self.env(LANI_DATA_DIR="~/somewhere"))
        self.check(self.env(LANI_DATA_DIR=None, FLUENT_DATA_DIR=str(project / "data")))
        self.check(self.env(LANI_DATA_DIR=None, LANI_ENV_FILE=str(env_file)))
        self.check(self.env(LANI_DATA_DIR=None, LANI_ENV_FILE=str(env_file), LANI_PROFILE="kid"), cwd=cwd_data)
        self.check(self.env(LANI_DATA_DIR=None, CLAUDE_PROJECT_DIR=str(project)), cwd=cwd_data)
        self.check(self.env(LANI_DATA_DIR=None, CLAUDE_PROJECT_DIR=str(self.root / "nowhere")), cwd=cwd_data)
        self.check(self.env(LANI_DATA_DIR=None), cwd=self.root)  # ~/.claude/lani-data, made
        (self.home / ".claude" / "fluent-data").mkdir(parents=True)
        self.check(self.env(LANI_DATA_DIR=None), cwd=self.home)
        os.symlink(project / "data", self.root / "link")
        self.check(self.env(LANI_DATA_DIR=str(self.root / "link")))
        self.check(self.env(LANI_DATA_DIR=str(self.root / "link" / ".." / "cwd" / "data")))
        self.run_both("card", ["find", "dag"], env=self.env(LANI_DATA_DIR=str(self.root / "link")))
        self.run_both("card", ["--data", "~/x", "find", "dag"])


@db_impl.needs_bun
class ValidateDataTest(Scenario):
    """validate-data.py, Claude Code's hook after an edit of a database."""

    def hook(self, file_path, **kw):
        return self.run_both("validate-data", stdin={"tool_name": "Edit", "tool_input": {"file_path": file_path}}, **kw)

    def test_the_hook(self):
        self.data.mkdir()
        good = self.data / "progress-db.json"
        good.write_text('{"a": 1}')
        for i in range(12):
            (self.data / f"progress-db.json.backup-202609{i + 10:02d}-000000").write_text("{}")
        self.hook(str(good))
        self.hook("data/progress-db.json", cwd=self.root)
        (self.data / "bad.json").write_text('{"a": }')
        self.hook(str(self.data / "bad.json"))
        (self.data / "latin1.json").write_bytes(b'{"a": "\xe9"}')
        self.hook(str(self.data / "latin1.json"))
        self.hook(str(self.data / "missing.json"))
        self.hook(str(self.root / "elsewhere.json"))
        self.hook(str(self.data / "notes.md"))
        self.hook("")
        self.run_both("validate-data", stdin=b"not json")
        self.run_both("validate-data", stdin={"tool_input": {}})
        self.run_both("validate-data", stdin={"tool_input": {"file_path": 5}})
        self.run_both("validate-data", stdin=[1])
        self.hook(str(self.data / "x\0.json"))


@db_impl.needs_bun
class EdgeCasesTest(Scenario):
    """Empty and odd data directories, odd ids and dates, and the corners of card.py."""

    def test_an_empty_or_odd_data_directory(self):
        self.read()  # nothing there: every file missing
        self.data.mkdir()
        self.read()
        self.card("find", "x")
        self.card("show", "x", "--language", "it")
        self.update({"session_id": "session-001", "date": "2026-10-01"})
        self.update({"session_id": "session-it-001", "date": "2026-10-01", "language": "it",
                     "new_vocabulary": [{"item_id": "vocab_ciao", "content": "ciao", "answer": "hi"}]})
        self.update({"record_session": False, "date": "2026-10-02", "language": "de",
                     "new_vocabulary": [{"item_id": "vocab_danke", "content": "danke", "answer": "thanks"}]})
        self.read()
        self.read("--language", "it")
        (self.data / "odd.json").mkdir()  # a directory the backups' *.json takes
        write(self.data / "learner-profile.json", {"learner": {"target_language": "Slovene"}, "home_language": "sl", "languages": []})
        self.update({"session_id": "session-002", "date": "2026-10-02"})
        file_dir = self.root / "a-file"
        file_dir.write_text("x")
        self.run_both("ensure_data_dir", env=self.env(LANI_DATA_DIR=str(file_dir)))
        self.run_both("ensure_data_dir", env=self.env(LANI_DATA_DIR=str(file_dir / "below")))
        spaced = self.root / "dätä mit Leerzeichen"
        self.run_both("ensure_data_dir", env=self.env(LANI_DATA_DIR=str(spaced)))
        self.run_both("update-db", stdin={"session_id": "a/b/../c", "date": "2026-10-03"}, env=self.env(LANI_DATA_DIR=str(spaced)))

    def test_card_corners(self):
        sys.path.insert(0, str(REPO_ROOT / "tests"))
        import test_card
        self.data.mkdir()
        test_card.make_fixtures(self.data)
        sr = json.loads((self.data / "spaced-repetition.json").read_text())
        items = sr["items"]
        items["counting_oce"] = test_card.card("counting_oce", "oče", "en oče · dva očeta", type="counting",
                                               counting=counting("oče", "m", ["en oče", "dva očeta", "trije očeti", "pet očetov"],
                                                                 [["oče"], ["očeta"], ["očeti", "očetje"], ["očetov"]]))
        items["counting_odd"] = test_card.card("counting_odd", "pes", "en pes", type="counting",
                                               counting={"gender": "x", "lemma": "pes", "lines": [], "forms": {}})
        items["forms_delati"] = test_card.card("forms_delati", "delati", "delam", type="forms", forms=FORMS_DELATI)
        items["forms_tested_str"] = test_card.card("forms_tested_str", "hiša", "hišo", type="forms", forms=dict(FORMS_HISA, tested="acc"))
        items["vocab_long"] = test_card.card("vocab_long", "a" * 41 + " / kratko", "short / long")
        items["vocab_slash"] = test_card.card("vocab_slash", "ja/ne", "yes/no")
        items["vocab_parens"] = test_card.card("vocab_parens", "dober (dan", "(only a note)")
        items["vocab_empty"] = test_card.card("vocab_empty", " ", "x")
        items["phrase_type"] = test_card.card("phrase_type", "Kako si?", "How are you?", type="phrase")
        items["vocab_tiles"] = test_card.card("vocab_tiles", "dober dan / zdravo", "hello")
        items["vocab_hist_bad"] = test_card.card("vocab_hist_bad", "kruh", "bread", total_reviews=2,
                                                 review_history=[{"date": "2026-09-20", "quality": 4}, "x"])
        items["vocab_hist_none"] = test_card.card("vocab_hist_none", "voda", "water", total_reviews=1)
        items["vocab_hist_bool"] = test_card.card("vocab_hist_bool", "čaj", "tea", total_reviews=1,
                                                  review_history=[{"date": "2026-09-20", "quality": True}])
        items["not_a_dict"] = ["x"]
        (self.data / "spaced-repetition.json").write_text(json.dumps(sr, ensure_ascii=False))
        for cid in ("counting_oce", "counting_odd", "forms_delati", "forms_tested_str", "vocab_long", "vocab_slash",
                    "vocab_parens", "vocab_empty", "phrase_type", "vocab_tiles", "not_a_dict", "vocab_imenujem_se"):
            self.card("show", cid)
        self.card("overlaps")
        self.card("overlaps", "not_a_dict")
        self.card("find", "da", "--limit", "0")
        self.card("find", "  ")
        self.card("find", "🔥")
        self.card("find", "oce")
        self.card("regrade", "vocab_hist_bad", "--quality", "2")
        self.card("regrade", "vocab_hist_none", "--quality", "2")
        self.card("regrade", "vocab_hist_bool", "--quality", "4")
        self.card("regrade", "iz_genitive", "--quality", "3")
        self.card("regrade", "vocab_fresh", "--quality", "0", "--date", "2026-09-01")
        self.card("merge", "vocab_da", "iz_genitive", "--force", "--today", "2026-09-30")
        self.card("merge", "vocab_ne", "vocab_hvala", "--front", "Ja ...", "--dry-run")
        self.card("merge", "vocab_ne", "vocab_hvala", "--note", "x(y)")
        self.card("split", "iz_genitive", "--new-id", "x1", "--new-front", "x", "--new-back", "y")
        self.card("split", "vocab_ne", "--new-id", "vocab_x\n", "--new-front", "x", "--new-back", "y")
        self.card("split", "vocab_ne", "--new-id", "vocab_rule", "--new-front", "a → b", "--new-back", "rule")
        self.card("split", "vocab_ne", "--new-id", "vocab_rule", "--new-front", "a → b", "--new-back", "rule", "--force")
        self.card("edit", "vocab_da", "--front", "  da   /   ja  ", "--today", "2026-09-27\n")
        self.card("edit", "vocab_da", "--front", "da", "--today", "２０２６-09-27")
        self.card("edit", "vocab_da", "--back", "yes", "--note", "  spoken  ")
        self.card("edit", "vocab_da", "--front", "a" * 200)
        self.card("edit", "vocab_da", "--front", "\u00e9" * 201)
        self.card("edit", "not_a_dict", "--front", "x")
        self.card("forgive", "iz_genitive", "--date", "2026-09-20", "--dry-run")
        self.card("forgive", "na_accusative", "--reason", "  fine  ")
        mistakes = json.loads((self.data / "mistakes-db.json").read_text())
        mistakes["error_patterns"]["odd_dates"] = {"frequency": "2", "examples": [{"date": 5}, {"date": "2026-09-01", "incorrect": "x"}]}
        mistakes["error_patterns"]["no_examples"] = {"frequency": 1.5, "examples": "none"}
        (self.data / "mistakes-db.json").write_text(json.dumps(mistakes))
        self.card("forgive", "odd_dates")
        self.card("forgive", "odd_dates", "--date", "2026-09-01")
        self.card("forgive", "no_examples")


@db_impl.needs_bun
class InProcessRunTest(Scenario):
    """cli.ts run() as the phone's bridge calls it (many runs in one process, the environment and the working
    directory passed in), and its afterWrite hook: the Python's results and files, the paths each run wrote, and the
    commit message where the Python commits a data repository (lani_data_repo.session_message)."""

    def test_runs_in_one_process_and_the_hook(self):
        sys.path.insert(0, str(REPO_ROOT / "tests"))
        sys.path.insert(0, str(REPO_ROOT / ".claude" / "hooks"))
        import lani_data_repo
        import test_card
        self.data.mkdir()
        test_card.make_fixtures(self.data)
        d = str(self.data)
        report = {"session_id": "session-002", "date": "2026-09-27", "duration_minutes": 4, "command_used": "/lani-app-review",
                  "skill_scores": {"vocabulary": {"exercises": 3, "correct": 2, "time_minutes": 4}},
                  "review_results": [{"item_id": "vocab_da", "quality": 4}, {"item_id": "vocab_ne", "gentle": True}],
                  "new_vocabulary": [{"item_id": "vocab_miza", "content": "miza", "answer": "table"}],
                  "errors": [{"pattern_id": "iz_genitive", "your_answer": "iz Kranj", "correct_answer": "iz Kranja"}],
                  "language": "sl"}
        steps = [("update-db", [], json.dumps(report)),
                 ("update-db", [], json.dumps({"record_session": False, "date": "2026-09-27",
                                                "new_vocabulary": [{"item_id": "vocab_word_gozd2", "content": "gozd", "answer": "forest"}]})),
                 ("card", ["--data", d, "edit", "vocab_da", "--front", "da / ja", "--today", "2026-09-28"], None),
                 ("read-db", [], None),
                 ("card", ["--data", d, "show", "vocab_da"], None),
                 ("ensure_data_dir", [], None),
                 ("update-db", [], "{ not json"),
                 ("update-db", [], json.dumps(dict(report, session_id="session-it-001", language="it"))),
                 ("validate-data", [], json.dumps({"tool_input": {"file_path": str(self.data / "progress-db.json")}}))]
        env = self.env()
        before = self.root.parent / f"{self.root.name}.before"
        shutil.copytree(self.root, before, symlinks=True)
        try:
            p = subprocess.run([db_impl.BUN, str(PROBE)], capture_output=True,
                               input=json.dumps([["run", sc, args, stdin, env, str(self.root)] for sc, args, stdin in steps]).encode())
            self.assertEqual(p.returncode, 0, p.stderr.decode())
            ts = [json.loads(line) for line in p.stdout.decode().split("\n") if line]
            ts_tree = db_impl.tree(self.root)
            shutil.rmtree(self.root)
            shutil.copytree(before, self.root, symlinks=True)
            py = [subprocess.run(db_impl.command("py", sc, args), input=(stdin or "").encode(), env=dict(os.environ, **env),
                                 cwd=str(self.root), capture_output=True) for sc, args, stdin in steps]
            self.assertEqual(db_impl.tree(self.root), ts_tree)
        finally:
            shutil.rmtree(before, ignore_errors=True)
        for (sc, args, _), t, q in zip(steps, ts, py):
            hook_lines = [c for c in t["calls"] if c.get("commit")]
            stderr = t["stderr"].replace("".join(f"[Lani] data repository: committed {c['commit'].splitlines()[0]}\n" for c in hook_lines), "")
            self.assertEqual((t["status"], t["stdout"], stderr), (q.returncode, q.stdout.decode(), q.stderr.decode()), f"{sc} {args}")
        # the hook: a session commits (with the Python's message); other writes only say what they wrote
        commit = ts[0]["calls"]
        self.assertEqual([c.get("commit") for c in commit], [lani_data_repo.session_message(report)])
        self.assertEqual(commit[0]["dataDir"], d)
        self.assertIn(str(self.data / "spaced-repetition.json"), commit[0]["paths"])
        self.assertIn(str(self.data / ".backups" / "pre-update-session-002" / "session-log.json"), commit[0]["paths"])
        self.assertEqual([c.get("commit") for c in ts[1]["calls"]], [None])
        self.assertEqual(ts[1]["calls"][0]["paths"], [str(self.data / ".backups" / "pre-words-2026-09-27" / "spaced-repetition.json"),
                                                      str(self.data / "spaced-repetition.json")])
        self.assertEqual(len(ts[2]["calls"]), 1)
        for i in (3, 4, 5, 6):
            self.assertEqual(ts[i]["calls"], [], steps[i][0])
        self.assertEqual([c.get("commit") for c in ts[7]["calls"]], [lani_data_repo.session_message(dict(report, session_id="session-it-001", language="it"))])
        self.assertEqual(ts[8]["calls"][0]["script"], "validate-data")

    def test_the_local_clock_without_lani_now(self):
        sys.path.insert(0, str(REPO_ROOT / "tests"))
        from test_update_db import make_fixtures
        self.data.mkdir()
        make_fixtures(self.data)
        for tz in ("UTC", "Pacific/Kiritimati", "Pacific/Pago_Pago", "Europe/Ljubljana"):
            env = self.env(LANI_NOW=None, TZ=tz)
            py = subprocess.run(db_impl.command("py", "read-db"), env=env, capture_output=True)
            ts = subprocess.run(db_impl.command("ts", "read-db"), env=env, capture_output=True)
            self.assertEqual(json.loads(py.stdout)["computed"], json.loads(ts.stdout)["computed"], tz)


if __name__ == "__main__":
    unittest.main()
