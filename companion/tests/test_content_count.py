"""Tests for companion/bin/content-count: it counts the files, on a small tree here and against the repository's own.
Run: python3 -m unittest discover -s companion/tests
"""
from __future__ import annotations

import importlib.machinery
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
_path = Path(__file__).resolve().parents[1] / "bin" / "content-count"
_loader = importlib.machinery.SourceFileLoader("content_count", str(_path))
_spec = importlib.util.spec_from_loader("content_count", _loader)
cc = importlib.util.module_from_spec(_spec)
sys.modules["content_count"] = cc
_loader.exec_module(cc)


class CountTest(unittest.TestCase):
    def tree(self, root: Path):
        def put(rel: str, o):
            f = root / rel
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_text(json.dumps(o))

        put("course/de/course.json", {"levels": [{"level": "A1", "units": [{"id": "a"}, {"id": "b"}]}, {"level": "A2", "units": [{"id": "c"}]}]})
        put("course/de/units/a.json", {"id": "a", "level": "A1"})
        put("course/de/units/b.json", {"id": "b", "level": "A1"})
        put("grammar/de/p.json", {"level": "A2"})
        put("modules/de/m.json", {"level": "A1", "exercises": [{}, {}]})
        put("drills/de/d.json", {"kind": "transform", "sets": [{"level": "A2", "items": [{}, {}, {}]}]})
        put("drills/de/r.json", {"kind": "riddle", "riddles": [{"level": "A1"}]})
        put("packs/de/p1.json", {"id": "p1", "level": "A1", "words": [{}, {}]})
        put("scenes/de/s1.json", {"id": "s1", "level": "A1", "happenings": [{}], "dialogs": [{}, {}]})
        c = "cultures/x"
        put(f"{c}/culture.json", {"language": "de", "status": "complete"})
        put(f"{c}/packs/p1.json", {"id": "p1", "level": "B1", "words": [{}]})  # the language's first, as the bridge reads them
        put(f"{c}/packs/p2.json", {"id": "p2", "words": []})
        put(f"{c}/stories/one.json", {"levels": {"A1": {}, "B1": {}}})
        put(f"{c}/stories/two.json", {"chapters": [{"levels": {"A1": {}}}, {"levels": {"A1": {}, "A2": {}}}]})
        put(f"{c}/readings/r.json", {"level": "B1"})
        put(f"{c}/arrivals.json", {"cast": {"a": {"levels": {"A1": {}, "A2": {}}}, "b": {"levels": {"A1": {}}}}, "templates": {"t": {}}})
        put(f"{c}/project-steps/m.json", {"steps": [{"levels": {"A1": {}, "A2": {}}}, {"levels": {"A1": {}}}]})
        put(f"{c}/celebrations/tabor.json", {"levels": {"A1": {}, "B2": {}}})
        put(f"{c}/villagers/v.json", {})
        put(f"{c}/world.json", {"spots": {
            "kopa": {"keeper": {"levels": {"A1": {}}, "tales": [{"levels": {"A1": {}, "A2": {}}}]}, "cycle": {"stages": [{"talks": [{"levels": {"A1": {}}}]}], "breakout": {"levels": {"A2": {}}}}},
            "seat": {"name": "no keeper", "levels": {"A1": {}}},
        }})

    def test_a_small_tree(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.tree(root)
            n = cc.count(root)
        de = n["languages"]["de"]
        self.assertEqual(de["course"]["units"], {"A1": 2})
        self.assertEqual(de["course"]["units_planned"], {"A2": 1})
        self.assertEqual(de["course"]["pages"], {"A2": 1})
        self.assertEqual(de["modules"], {"total": 1, "by_level": {"A1": 1}, "exercises": 2})
        self.assertEqual(de["drills"]["transform"], {"items": 3, "by_level": {"A2": 3}})
        self.assertEqual(de["drills"]["riddle"], {"items": 1, "by_level": {"A1": 1}})
        x = n["cultures"]["x"]
        self.assertEqual(x["packs"]["by_level"], {"A1": 1, "?": 1}, "the first with an id wins; a pack with no level shows")
        self.assertEqual(x["packs"]["words"], 2)
        self.assertEqual((x["scenes"]["total"], x["scenes"]["happenings"], x["scenes"]["dialogs"]), (1, 1, 2))
        self.assertEqual(x["stories"], {"total": 2, "in_chapters": 1, "tellings": {"A1": 2, "A2": 1, "B1": 1}})
        self.assertEqual(x["readings"]["by_level"], {"B1": 1})
        self.assertEqual(x["arrivals"], {"cast": 2, "by_level": {"A1": 2, "A2": 1}, "templates": 1})
        self.assertEqual(x["project_steps"], {"projects": 1, "steps": 2, "by_level": {"A1": 2, "A2": 1}})
        self.assertEqual(x["parties"], {"total": 1, "tellings": {"A1": 1, "B2": 1}})
        self.assertEqual(x["talks"], {"keepers": 1, "total": 4, "by_level": {"A1": 3, "A2": 2}}, "a spot without a keeper isn't a talk")
        self.assertEqual(x["villagers"], 1)
        self.assertIn("| units written | 2 |", cc.markdown(n))

    def test_the_repository(self):
        root = Path(__file__).resolve().parents[1]
        n = cc.count(root)
        for lang, l in n["languages"].items():
            self.assertEqual(l["course"]["units_total"], len(list((root / "course" / lang / "units").glob("*.json"))), lang)
            self.assertEqual(sum(l["course"]["units"].values()), l["course"]["units_total"], f"{lang}: every unit has a level")
            self.assertEqual(l["course"]["pages_total"], len(list((root / "grammar" / lang).glob("*.json"))), lang)
        self.assertEqual(sorted(n["languages"]), ["de", "en", "it", "sl"])
        self.assertEqual(sorted(n["cultures"]), ["friuli", "kaernten", "lakeland", "primorska"])
        self.assertTrue(cc.markdown(n).startswith("### Each language"))


if __name__ == "__main__":
    unittest.main()
