"""Tests for companion/bin/qa's check that Home's learning tiles come right under Today (tiles_under_today), however
long a learner's Today is. Run: python3 -m unittest discover -s companion/tests

No emulator: the screens are lists of (label, y) as QA reads them from uiautomator, at Home's top and after each swipe.
"""
from __future__ import annotations

import importlib.machinery
import importlib.util
import sys
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
_path = Path(__file__).resolve().parents[1] / "bin" / "qa"
_loader = importlib.machinery.SourceFileLoader("qa_nav", str(_path))
_spec = importlib.util.spec_from_loader("qa_nav", _loader)
qa = importlib.util.module_from_spec(_spec)
sys.modules["qa_nav"] = qa
_loader.exec_module(qa)

HEADING = "Učenje · Learning"
ROW = "Moje besede"
AFTER = ["Vaščani te čakajo · Villagers need you"]


def today(*lines: str) -> list:
    return [(t, 400 + 200 * i) for i, t in enumerate(lines)]


class TilesUnderToday(unittest.TestCase):
    def test_a_new_learner_s_short_today_the_tiles_at_once(self):
        top = today("Danes · Today", "Ponovi 5 kartic") + [(HEADING, 1300), ("Moje besede\n12 besed", 1500)]
        self.assertEqual((0, 0), qa.tiles_under_today([top], HEADING, ROW, AFTER))

    def test_a_grown_learner_s_long_today_takes_more_swipes_and_passes(self):
        # nav-perf.md: two new modules, a tip, two arrivals to meet, "Še 7 v vasi": five screens of Today
        screens = [today("Danes · Today", "Nov izziv: Dvojina"), today("Nov izziv: Mlin", "💡 Namig"), today("Spoznaj: Nonna Rosa"),
                   today("Spoznaj: Pastir Luka", "Še 7 v vasi"), today("Še 7 v vasi") + [(HEADING, 1700)],
                   [(HEADING, 1100), ("Moje besede\n12 besed", 1400)]]
        self.assertEqual((4, 5), qa.tiles_under_today(screens, HEADING, ROW, AFTER))

    def test_something_between_today_and_the_tiles_fails(self):
        screens = [today("Danes · Today"), [(AFTER[0], 600), (HEADING, 1500), ("Moje besede", 1700)]]
        self.assertIn("came between Today and the tiles", qa.tiles_under_today(screens, HEADING, ROW, AFTER))
        # the villagers' strip under the tiles, on the same screen: in its place
        screens = [today("Danes · Today"), [(HEADING, 600), ("Moje besede", 800), (AFTER[0], 1600)]]
        self.assertEqual((1, 1), qa.tiles_under_today(screens, HEADING, ROW, AFTER))

    def test_the_heading_without_its_row_or_never_in_view_fails(self):
        screens = [[(HEADING, 1800)], [("Slovnica", 900)], [("Pogovor", 900)]]
        self.assertIn("first row", qa.tiles_under_today(screens, HEADING, ROW, AFTER))
        never = [today("Danes · Today")] * (qa.TILES_SWIPES + 1)
        self.assertIn(f"not within {qa.TILES_SWIPES}", qa.tiles_under_today(never, HEADING, ROW, AFTER))


if __name__ == "__main__":
    unittest.main()
