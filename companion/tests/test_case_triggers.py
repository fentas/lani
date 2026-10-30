"""Tests for the case lens's trigger table generator (companion/grammar/sl/data/case-triggers.py): the JSON in the
repository is exactly what the script makes, so the table is never edited by hand, and --check says when it isn't.
Run: python3 -m unittest discover -s companion/tests
"""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
DATA = Path(__file__).resolve().parents[1] / "grammar" / "sl" / "data"
SCRIPT = DATA / "case-triggers.py"
TABLE = DATA / "case-triggers.json"


def run(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run([sys.executable, "-I", str(SCRIPT), *args], capture_output=True, text=True, timeout=60)


class CaseTriggersTest(unittest.TestCase):
    def test_the_table_is_what_the_script_makes(self):
        r = run("--check")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn("matches", r.stdout)

    def test_a_table_edited_by_hand_fails_the_check(self):
        with tempfile.TemporaryDirectory() as d:
            out = Path(d) / "case-triggers.json"
            self.assertEqual(run("--out", str(out)).returncode, 0)
            self.assertEqual(out.read_text(encoding="utf-8"), TABLE.read_text(encoding="utf-8"))
            table = json.loads(out.read_text(encoding="utf-8"))
            table["negation"]["words"].append("nikakor")
            out.write_text(json.dumps(table, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
            r = run("--check", "--out", str(out))
            self.assertEqual(r.returncode, 1)
            self.assertIn("is not what case-triggers.py makes", r.stderr)

    def test_the_table_says_it_is_machine_written(self):
        table = json.loads(TABLE.read_text(encoding="utf-8"))
        self.assertEqual(table["schema"], "lani.case-triggers/v0")
        self.assertTrue(table["machine_written"])
        self.assertIn("not reviewed by a native speaker", table["review"])
        self.assertIn("case-triggers.py", table["about"])


if __name__ == "__main__":
    unittest.main()
