"""Tests for companion/lexicon/tools/common_words.py: the words a language's content uses most that no pack teaches, on a
small tree here and against the repository's own (the commonest words are in a pack since common-words, 2026-11-03).
Run: python3 -m unittest discover -s companion/tests
"""
from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
_path = Path(__file__).resolve().parents[1] / "lexicon" / "tools" / "common_words.py"
_spec = importlib.util.spec_from_file_location("common_words", _path)
cw = importlib.util.module_from_spec(_spec)
sys.modules["common_words"] = cw
_spec.loader.exec_module(cw)


class MeasureTest(unittest.TestCase):
    def setUp(self):
        self.root = cw.ROOT

    def tearDown(self):
        cw.ROOT = self.root

    def tree(self, root: Path):
        def put(rel: str, o):
            f = root / rel
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_text(json.dumps(o, ensure_ascii=False))

        put("cultures/x/culture.json", {"language": "it"})
        put("lexicon/it.extra.json", {"entries": [
            {"lemma": "essere", "pos": "verb", "gloss": ["to be"], "forms": {"sono": "1sg", "è": "3sg"}},
            {"lemma": "casa", "pos": "noun", "gloss": ["house"], "forms": {"casa": "sg"}},
            {"lemma": "stare", "pos": "verb", "gloss": ["to stay"], "forms": {"stai": "2sg"}},
            {"lemma": "come", "pos": "adv", "gloss": ["how"], "forms": {"come": None}},
            {"lemma": "piccolo", "pos": "adj", "gloss": ["small"], "forms": {"piccola": "f sg"}},
        ]})
        put("packs/it/p.json", {"schema": "lani.pack/v0", "language": "it", "id": "p", "words": [
            {"id": "casa", "it": "la casa", "plural": "le case"}, {"id": "come-stai", "it": "Come stai?"}]})
        # the course: an accept list counts by its first (standard) answer only, spoken forms not at all
        put("course/it/units/u.json", {"items": [{"from": "Sono a casa.", "accept": ["Sono qui.", "Io sono qui."], "spoken": ["So' qui."]}]})
        put("cultures/x/scenes/s.json", {"lines": [{"it": "Come stai? È piccola, la casa.", "en": "How are you? It is small, the house."}]})

    def test_counts_what_no_pack_has(self):
        with tempfile.TemporaryDirectory() as d:
            self.tree(Path(d))
            cw.ROOT = d
            m = cw.measure("it")
        got = {lem: n for n, lem, _ in m.rows()}
        # sono ×2 (from, the first accept) and è: essere; piccola: piccolo
        self.assertEqual(got, {"essere": 3, "piccolo": 1})
        # "come stai" is a pack's expression where it stands: its words are learned with it; casa is a pack word
        self.assertNotIn("come", got)
        self.assertNotIn("stare", got)
        # the pack (its examples are content too), the culture file, its scene, the unit
        self.assertEqual((m.files, m.packs), (4, 1))

    def test_helpers(self):
        self.assertEqual(cw.pack_key("L'acqua!", "it"), "acqua")
        self.assertEqual(cw.pack_key("der Hund", "de"), "hund")
        self.assertEqual(cw.pack_key("{m:rad|f:rada} bi", "sl"), "rad bi")
        self.assertEqual(cw.norm("casìna", "it"), "casina")
        self.assertEqual(cw.norm("città", "it"), "città")
        self.assertEqual(cw.counted("{m:Sei pronto|f:Sei pronta} (ready)?").split(), ["Sei", "pronto", "Sei", "pronta", "?"])

    def test_the_repository_packs_its_commonest_words(self):
        # the four packs of common-words: the words each language's content uses most are no longer missing
        commonest = {"sl": ["biti", "iti", "imeti"], "it": ["essere", "avere", "fare"], "de": ["haben", "sein", "kommen"], "en": ["be", "have", "do"]}
        for lang, words in commonest.items():
            got = {lem for _, lem, _ in cw.measure(lang).rows()}
            for w in words:
                self.assertNotIn(w, got, f"{lang}: {w}")


if __name__ == "__main__":
    unittest.main()
