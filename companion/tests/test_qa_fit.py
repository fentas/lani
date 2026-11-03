"""Tests for companion/bin/qa's comparison of what fit.js chose in the app's WebView and in headless Chromium (same_fit).
Run: python3 -m unittest discover -s companion/tests

No emulator: the reports are as fit.js writes them, one dict a unit.
"""
from __future__ import annotations

import importlib.machinery
import importlib.util
import sys
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
_path = Path(__file__).resolve().parents[1] / "bin" / "qa"
_loader = importlib.machinery.SourceFileLoader("qa_fit", str(_path))
_spec = importlib.util.spec_from_loader("qa_fit", _loader)
qa = importlib.util.module_from_spec(_spec)
sys.modules["qa_fit"] = qa
_loader.exec_module(qa)


def report(**over) -> dict:
    r = {"unit": "comparatives", "pages": 2, "fits": True, "layout": "two", "density": 0, "side": False, "over": 13}
    r.update(over)
    return r


class SameFit(unittest.TestCase):
    def test_the_same_choice_with_a_line_wrapped_otherwise_is_the_same_fit(self):
        # the run that stopped here: two pages in both, the one-page try they gave up on 17 mm over in the WebView, 13 in Chromium
        self.assertTrue(qa.same_fit([report(over=17)], [report(over=13)]))
        self.assertTrue(qa.same_fit([report(over=13)], [report(over=13 + qa.SAME_CHOICE_OVER_MM)]))

    def test_a_report_without_over_is_none_over(self):
        one = {"unit": "how-much", "pages": 1, "fits": True, "layout": "one", "density": 3, "side": False}
        self.assertTrue(qa.same_fit([one], [dict(one)]))

    def test_far_more_left_over_than_a_line_or_two_still_fails(self):
        self.assertFalse(qa.same_fit([report(over=13 + qa.SAME_CHOICE_OVER_MM + 1)], [report(over=13)]))

    def test_another_layout_density_or_page_count_always_fails(self):
        for other in ({"layout": "one"}, {"density": 1}, {"pages": 1}, {"side": True}, {"unit": "superlatives"}):
            with self.subTest(other):
                self.assertFalse(qa.same_fit([report(**other)], [report()]))
                self.assertFalse(qa.same_fit([report(over=13, **other)], [report(over=13)]))

    def test_one_that_fits_against_one_that_doesnt_fails(self):
        self.assertFalse(qa.same_fit([report(fits=False, layout=None, density=None)], [report()]))

    def test_neither_fits_the_overrun_is_all_there_is_within_a_millimetre(self):
        none = {"unit": "adverbs", "pages": 3, "fits": False, "over": 40}
        self.assertTrue(qa.same_fit([none], [dict(none, over=41)]))
        self.assertFalse(qa.same_fit([none], [dict(none, over=45)]))

    def test_a_missing_report_or_another_number_of_units_fails(self):
        self.assertFalse(qa.same_fit(None, [report()]))
        self.assertFalse(qa.same_fit([report()], None))
        self.assertFalse(qa.same_fit([report(), report()], [report()]))


if __name__ == "__main__":
    unittest.main()
