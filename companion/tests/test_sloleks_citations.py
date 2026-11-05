"""The Slovene word forms cite the Sloleks 3.0 release (CC BY-SA 4.0), never the 2.0 site (CC BY-NC-SA 4.0): the supplement, the forms
book and the grammar pages (docs/plans/05-built/leftovers-content.md, "Sloleks"). What the forms themselves hold against the release is
`sloleks3.py check`'s, which needs the release.
Run: python3 -m unittest discover -s companion/tests
"""
from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

COMPANION = Path(__file__).resolve().parents[1]
FILES = [COMPANION / "lexicon/sl.extra.json", COMPANION / "lexicon/sl.forms.json", *sorted((COMPANION / "grammar/sl").glob("*.json"))]


class SloleksCitations(unittest.TestCase):
    def test_nothing_cites_the_2_0_site_or_release(self):
        for path in FILES:
            text = path.read_text(encoding="utf8")
            self.assertNotIn("viri.cjvt.si", text, path.name)
            self.assertNotIn("Sloleks 2.0", text, path.name)

    def test_a_citation_of_3_0_names_its_entry(self):
        entries = json.loads((COMPANION / "lexicon/sl.extra.json").read_text(encoding="utf8"))["entries"]
        cited = [e for e in entries if "11356/1745" in (e.get("source") or "")]
        self.assertGreater(len(cited), 2000)
        for e in cited:
            self.assertRegex(e["source"], r"11356/1745 \([A-Z]_[^)]+\)", e["lemma"])

    def test_the_supplement_says_whose_its_forms_are(self):
        meta = json.loads((COMPANION / "lexicon/sl.extra.json").read_text(encoding="utf8"))["meta"]["sloleks"]
        self.assertEqual("CC BY-SA 4.0", meta["licence"])
        self.assertIn("11356/1745", meta["url"])


if __name__ == "__main__":
    unittest.main()
