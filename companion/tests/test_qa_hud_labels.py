"""Tests for companion/bin/qa's labels of the village's HUD: they follow the pair the phone shows, so that plain_village() finds
the goal's card and the ring on a phone that isn't Slovene first. Run: python3 -m unittest discover -s companion/tests

The bug they keep out: on en-sl, a label fixed in sl · en found no goal's card, and "Danes" (the base's half of the card's
"Today", its own node) passed for the ring: plain_village() long-pressed it, which opened the card's sheet, eight times over.
No emulator: a screen is a list of (label, centre) as QA reads it from uiautomator.
"""
from __future__ import annotations

import ast
import importlib.machinery
import importlib.util
import os
import sys
import unittest
from pathlib import Path
from unittest import mock

sys.dont_write_bytecode = True
_path = Path(__file__).resolve().parents[1] / "bin" / "qa"
_loader = importlib.machinery.SourceFileLoader("qa_hud_labels", str(_path))
_spec = importlib.util.spec_from_loader("qa_hud_labels", _loader)
qa = importlib.util.module_from_spec(_spec)
sys.modules["qa_hud_labels"] = qa
_loader.exec_module(qa)


def on_pair(pair: str):
    return mock.patch.dict(os.environ, {"LANI_QA_PAIR": pair})


class HudLabels(unittest.TestCase):
    def test_slovene_first_is_as_it_always_was(self):
        with on_pair("sl-en"):
            self.assertEqual(qa.hud_goal(), "Naslednji cilj · Next goal: ")
            self.assertEqual(qa.hud_soon(), "Danes in kmalu · Today and coming up")
            self.assertEqual(qa.hud_settlement(), "Naselje · Settlement: ")
            self.assertEqual(qa.hud_ring_starts(), ("Doba · ", "Danes"))
            self.assertEqual(qa.over_village(), ("Zapri · Close", "Close sheet"))
            self.assertEqual(qa.move_on(), ("Naprej · Continue", "V vas · To the village"))

    def test_they_follow_the_pair(self):
        with on_pair("en-sl"):
            self.assertEqual(qa.hud_goal(), "Next goal · Naslednji cilj: ")
            self.assertEqual(qa.hud_ring_starts(), ("Age · ", "Today"))
            self.assertEqual(qa.over_village(), ("Close · Zapri", "Close sheet"))
        with on_pair("de-sl"):
            self.assertEqual(qa.hud_soon(), "Heute und demnächst · Danes in kmalu")
        with on_pair("it-de"):
            self.assertEqual(qa.hud_settlement(), "Insediamento · Siedlung: ")


    def test_a_resource_is_named_as_the_villages_culture_names_it(self):
        with on_pair("it-de"), mock.patch.object(qa, "api", return_value={"id": "friuli"}):
            self.assertEqual(qa.hud_resource("food"), "Cibo · Nahrung: ")
        with on_pair("sl-en"), mock.patch.object(qa, "api", return_value={"id": "primorska"}):
            self.assertEqual(qa.hud_resource("wisdom"), "Modrost · Wisdom: ")


class Steps(unittest.TestCase):
    def test_no_function_of_qa_is_defined_twice(self):
        """A later `def village_hud() -> tuple` replaced the step of that name (60908e38): it reported ok for days, checking nothing."""
        tree = ast.parse(_path.read_text(encoding="utf-8"))
        names = [n.name for n in tree.body if isinstance(n, ast.FunctionDef)]
        self.assertEqual([], sorted({n for n in names if names.count(n) > 1}))

    def test_the_hud_step_is_the_step(self):
        self.assertIn("The village's HUD", qa.village_hud.__doc__)
        self.assertTrue(callable(qa.village_bar))


# The village on en-sl as the emulator showed it (2026-11-02): the ring in the bar, then the cards, each its own node.
EN_SL = [
    ("Food · Hrana: 140 / 1200", (100, 100)),
    ("Age · Doba: Village · Vas. To the market town: 8", (980, 200)),
    ("Next goal · Naslednji cilj: Market town: 8 steps to go", (400, 340)),
    ("Today and coming up · Danes in kmalu: Tonight", (980, 340)),
    ("Today", (970, 320)),
    ("Danes", (970, 360)),
    ("Scroll · Zvitek", (280, 2260)),
]


class PlainVillage(unittest.TestCase):
    def plain(self, screen: list) -> mock.Mock:
        press = mock.Mock()
        with mock.patch.object(qa, "nodes", lambda: screen), mock.patch.object(qa, "long_press", press), \
                mock.patch.object(qa, "hud_cards", lambda *_a, **_k: True), mock.patch.object(qa, "adb"), mock.patch.object(qa.time, "sleep"):
            qa.plain_village()
        return press

    def test_on_en_sl_the_plain_village_is_left_as_it_is(self):
        with on_pair("en-sl"):
            self.assertEqual(self.plain(EN_SL).call_count, 0)

    def test_on_a_feast_day_the_ring_says_the_feast_and_is_still_not_todays_card(self):
        feast = [
            ("Hrana · Food: 140 / 1200", (100, 200)),
            ("🕯️ Dan spomina na mrtve · Remembrance Day", (985, 205)),
            ("Naslednji cilj · Next goal: Trg", (400, 340)),
            ("Danes in kmalu · Today and coming up: Dan spomina", (990, 340)),
            ("Danes", (1000, 380)),  # the card's word, as the emulator showed it on 2026-11-03
        ]
        with on_pair("sl-en"):
            self.assertEqual(qa.hud_ring(feast), (985, 205))

    def test_the_ring_is_never_a_cards_word_of_the_base(self):
        with on_pair("en-sl"):
            self.assertEqual(qa.hud_ring(EN_SL), (980, 200))
        # the ring first in the screen's order, as it is on every pair
        with on_pair("sl-en"):
            self.assertEqual(qa.hud_ring([("Doba · Age: Vas", (980, 200)), ("Danes", (970, 320))]), (980, 200))

    def test_cards_hidden_it_is_the_ring_that_shows_them(self):
        hidden = [x for x in EN_SL if not x[0].startswith(("Next goal", "Today", "Danes"))]
        calls = []
        def press(c):
            calls.append(c)
            screen[:] = EN_SL
        screen = list(hidden)
        with on_pair("en-sl"), mock.patch.object(qa, "nodes", lambda: screen), mock.patch.object(qa, "long_press", press), \
                mock.patch.object(qa, "hud_cards", lambda *_a, **_k: True), mock.patch.object(qa, "adb"), mock.patch.object(qa.time, "sleep"):
            qa.plain_village()
        self.assertEqual(calls, [(980, 200)])

    def test_when_it_gives_up_it_says_what_is_on_the_screen(self):
        with on_pair("en-sl"), mock.patch.object(qa, "nodes", lambda: [("A page of its own", (1, 1))]), mock.patch.object(qa, "in_front", lambda: True), \
                mock.patch.object(qa, "is_home", lambda _l: False), mock.patch.object(qa, "adb"), mock.patch.object(qa.time, "sleep"):
            with self.assertRaisesRegex(AssertionError, "A page of its own"):
                qa.plain_village()


if __name__ == "__main__":
    unittest.main()
