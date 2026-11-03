"""Tests for companion/bin/qa reading a screen's dump: a text with a double quote in it (the serializer writes that attribute
between single quotes), and fill's field low on a screen with nothing left to scroll. Run: python3 -m unittest discover -s companion/tests

The bugs they keep out: course-unit never found a quick question with direct speech in it, and stopped at a test's fourth transform line.
No emulator: a screen is uiautomator's XML, or a list of (label, centre).
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
_loader = importlib.machinery.SourceFileLoader("qa_nodes", str(_path))
_spec = importlib.util.spec_from_loader("qa_nodes", _loader)
qa = importlib.util.module_from_spec(_spec)
sys.modules["qa_nodes"] = qa
_loader.exec_module(qa)

XML = """<?xml version='1.0' encoding='UTF-8'?><hierarchy rotation="0">
<node index="0" text='"Wait here." → She told me ___ there.' resource-id="" class="android.widget.TextView" content-desc="" bounds="[80,1490][1000,1640]" />
<node index="1" text="to wait" resource-id="" class="android.widget.TextView" content-desc="" bounds="[80,1700][1000,1760]" />
<node index="2" text="I&apos;ll &amp; you" resource-id="" class="android.widget.TextView" content-desc="" bounds="[0,0][10,10]" />
<node index="3" text='It&apos;s "her" dog' resource-id="" class="android.view.View" content-desc="" bounds="[0,20][10,30]" />
<node index="4" text="" resource-id="" class="android.view.View" content-desc='The "Book"' bounds="[0,40][10,60]" />
</hierarchy>"""


class NodesTest(unittest.TestCase):
    def test_a_text_with_a_double_quote_is_read(self):
        with mock.patch.object(qa, "screen_xml", return_value=XML):
            read = dict(qa.nodes())
        self.assertEqual((540, 1565), read['"Wait here." → She told me ___ there.'])
        self.assertEqual((540, 1730), read["to wait"])
        self.assertIn("I'll & you", read)
        self.assertIn('It\'s "her" dog', read)
        self.assertEqual((5, 50), read['The "Book"'])

    def test_boxes_read_it_too(self):
        with mock.patch.object(qa, "screen_xml", return_value=XML):
            read = dict(qa.boxes())
        self.assertEqual((80, 1490, 1000, 1640), read['"Wait here." → She told me ___ there.'])

    def test_a_missing_attribute_is_empty(self):
        self.assertEqual("", qa.node_attr('<node text="x" />', "content-desc"))


class FillTest(unittest.TestCase):
    def filled(self, y: int) -> bool:
        with mock.patch.object(qa, "find", return_value=(540, y)), mock.patch.object(qa, "adb"), mock.patch.object(qa.time, "sleep"), \
                mock.patch.object(qa, "tap_at") as tap, mock.patch.object(qa, "type_text") as typed:
            ok = qa.fill("Vrstica 4 · Line 4", "If I were you")
        self.assertEqual(ok, tap.called)
        self.assertEqual(ok, typed.called)
        return ok

    def test_a_field_scrolled_as_high_as_it_goes_is_taken_above_the_bottom_bar(self):
        self.assertTrue(self.filled(1878))

    def test_a_field_higher_up_is_taken(self):
        self.assertTrue(self.filled(900))

    def test_a_field_behind_the_bottom_bar_isn_t(self):
        self.assertFalse(self.filled(2250))


if __name__ == "__main__":
    unittest.main()
