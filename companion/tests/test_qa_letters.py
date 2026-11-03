"""Tests for companion/bin/qa's typing of a course's letters: adb types ASCII, the rest is the letter's chip, as a learner enters
it, and what the field holds is checked. Run: python3 -m unittest discover -s companion/tests

The bug they keep out: QA folded ä to ae and è to e, which grade almost, so a unit's test with such answers couldn't be passed.
No emulator: adb is recorded, a screen is a list of (label, centre) as QA reads it from uiautomator.
"""
from __future__ import annotations

import importlib.machinery
import importlib.util
import sys
import unittest
from pathlib import Path
from unittest import mock

sys.dont_write_bytecode = True
_path = Path(__file__).resolve().parents[1] / "bin" / "qa"
_loader = importlib.machinery.SourceFileLoader("qa_letters", str(_path))
_spec = importlib.util.spec_from_loader("qa_letters", _loader)
qa = importlib.util.module_from_spec(_spec)
sys.modules["qa_letters"] = qa
_loader.exec_module(qa)

CHIPS = {"de": "äöüßÄÖÜ", "it": "àèéìòù", "sl": "čšžČŠŽ"}


class TypeLetters(unittest.TestCase):
    def typed(self, text: str, lang: str | None, holds=None, ime=None) -> tuple[list, list]:
        """The keys and the chip taps of typing [text]; the field holds [holds] in turn (else what was asked), the keyboard's top is [ime]."""
        keys: list = []
        taps: list = []

        def adb(*args, check=True):
            if args[:3] == ("shell", "input", "text"):
                keys.append(args[3])
            elif args[:3] == ("shell", "input", "tap"):
                taps.append((int(args[3]), int(args[4])))
            return ""

        chips = [(ch, (100 + 50 * i, 1500)) for i, ch in enumerate(CHIPS.get(lang or "", ""))]
        held = iter(holds or [])
        with mock.patch.object(qa, "adb", adb), mock.patch.object(qa, "nodes", lambda: iter(chips)), \
                mock.patch.object(qa, "ime_top", lambda: ime), mock.patch.object(qa.time, "sleep"), \
                mock.patch.object(qa, "focused_field_text", lambda: next(held, None)):
            qa.type_letters(text, lang)
        return keys, taps

    def test_each_letter_is_its_chip_the_rest_is_typed(self):
        keys, taps = self.typed("Grüßen", "de", ime=1500 + 300)
        self.assertEqual(keys, ["Gr", "en"])
        self.assertEqual(taps, [(100 + 50 * CHIPS["de"].index("ü"), 1500), (100 + 50 * CHIPS["de"].index("ß"), 1500)])

    def test_a_space_and_an_apostrophe_as_the_device_shell_wants_them(self):
        keys, _ = self.typed("l'acqua è qui", "it", ime=1800)
        self.assertEqual(keys, ["l\\'acqua%s", "%squi"])

    def test_a_capital_with_only_a_small_chip_is_typed_small_and_a_letter_with_none_folded(self):
        _, taps = self.typed("È", "it", ime=1800)
        self.assertEqual(taps, [(100 + 50 * CHIPS["it"].index("è"), 1500)])
        keys, taps = self.typed("ćevap", "sl", ime=1800)
        self.assertEqual((keys, taps), (["cevap"], []))

    def test_with_the_keyboard_down_one_key_at_a_time(self):
        keys, _ = self.typed("Bürger", "de", ime=None)
        self.assertEqual(keys, ["B", "r", "g", "e", "r"])

    def test_english_has_no_chips(self):
        keys, taps = self.typed("She sings well", "en", ime=1800)
        self.assertEqual((keys, taps), (["She%ssings%swell"], []))

    def test_what_the_field_holds_is_checked_and_typed_again(self):
        keys, taps = self.typed("Grüßen", "de", holds=["Grüßne", "Grüßen"], ime=1800)
        self.assertEqual(keys, ["Gr", "en", "G", "r", "e", "n"])
        self.assertEqual(len(taps), 4)

    def test_a_field_that_never_holds_it_fails_saying_so(self):
        with self.assertRaisesRegex(AssertionError, "the field holds «Grüßne»"):
            self.typed("Grüßen", "de", holds=["Grüßne"] * 3, ime=1800)

    def test_a_chip_that_isnt_there_fails_naming_it(self):
        with self.assertRaisesRegex(AssertionError, "the letter chip «ü»"):
            with mock.patch.object(qa, "letter_chip", lambda _: None):
                self.typed("für", "de", ime=1800)


class LetterChip(unittest.TestCase):
    def test_under_the_keyboard_it_takes_the_keyboard_down(self):
        tops = iter([1500, None])
        sent = []
        screens = iter([[("ü", (300, 1700))], [("ü", (300, 1700))]])
        with mock.patch.object(qa, "ime_top", lambda: next(tops)), mock.patch.object(qa, "nodes", lambda: iter(next(screens))), \
                mock.patch.object(qa, "adb", lambda *a, **k: sent.append(a)), mock.patch.object(qa.time, "sleep"):
            self.assertEqual(qa.letter_chip("ü"), (300, 1700))
        self.assertEqual(sent, [("shell", "input", "keyevent", "KEYCODE_BACK")])


if __name__ == "__main__":
    unittest.main()
