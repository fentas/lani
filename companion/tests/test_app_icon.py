"""The launcher icon's generated files stay in sync with their generator (companion/android/tools/app_icon.py build).

The app's 176 activity aliases, their drawables, the shared backgrounds and monochrome layers, the application's icon,
ic_notify, the welcome's wordmark and the site's icons are all drawn by app_icon.py. A change to the drawing without a
`python3 companion/android/tools/app_icon.py build` (or a hand edit of a generated file) fails here. The Kotlin side
(AppIconsTest) keeps the alias names and the manifest.
"""
from __future__ import annotations

import contextlib
import io
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TOOLS = ROOT / "companion" / "android" / "tools"
sys.path.insert(0, str(TOOLS))
import app_icon  # noqa: E402


class AppIconInSync(unittest.TestCase):
    def test_generated_files_match_the_generator(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            problems = app_icon.build(ROOT / "companion" / "android" / "app", check=True)
        self.assertEqual(problems, 0, out.getvalue())

    def test_aliases_are_named_by_the_rule_and_unique(self):
        names = [app_icon.alias_name(*look) for look in app_icon.icon_looks()]
        self.assertEqual(len(names), 176)
        self.assertEqual(len(set(names)), 176)
        self.assertEqual(names[0], ".icon.Ogenj")
        pinned = (ROOT / "companion/android/app/src/test/resources/app-icon-aliases.txt").read_text().split()
        self.assertTrue(set(pinned) <= set(names), set(pinned) - set(names))

    def test_shared_layers(self):
        files = app_icon.build_files()
        self.assertEqual(sorted(k for k in files if k.startswith("drawable/ic_icon_bg_")),
                         ["drawable/ic_icon_bg_green.xml", "drawable/ic_icon_bg_snow.xml"])
        self.assertLess(len([k for k in files if k.startswith("drawable/ic_icon_mono_")]), 30)


if __name__ == "__main__":
    unittest.main()
