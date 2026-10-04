"""An install from before the project was renamed from Fluent to Lani keeps working (docs/migrate-from-fluent.md):
FLUENT_* variables are read as LANI_*, and the plugin's data stays in ~/.claude/fluent-data while ~/.claude/lani-data
doesn't exist. Run: python3 -m unittest discover -s tests"""
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HOOKS = Path(__file__).resolve().parents[1] / ".claude" / "hooks"
sys.path.insert(0, str(HOOKS))
import lani_paths  # noqa: E402


def data_dir_in(home: Path, **env) -> str:
    """lani_paths.data_dir() in a fresh process with HOME=[home], run in [home] (no ./data), plus [env]."""
    clean = {k: v for k, v in os.environ.items() if not k.startswith(("LANI_", "FLUENT_", "CLAUDE_"))}
    out = subprocess.run([sys.executable, "-c", f"import sys; sys.path.insert(0, {str(HOOKS)!r}); from lani_paths import data_dir; print(data_dir())"],
                         cwd=home, env={**clean, "HOME": str(home), **env}, capture_output=True, text=True, check=True)
    return out.stdout.strip()


class LegacyNamesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.home = Path(self.tmp.name).resolve()

    def tearDown(self):
        self.tmp.cleanup()

    def test_a_fluent_variable_is_read_as_its_lani_name_and_lani_wins(self):
        env = lani_paths.adopt_legacy_env({"FLUENT_DATA_DIR": "/old", "FLUENT_BRIDGE_PORT": "1", "LANI_BRIDGE_PORT": "2"})
        self.assertEqual("/old", env["LANI_DATA_DIR"])
        self.assertEqual("2", env["LANI_BRIDGE_PORT"])

    def test_fluent_data_dir_still_points_at_the_data(self):
        self.assertEqual(str(self.home / "old"), data_dir_in(self.home, FLUENT_DATA_DIR=str(self.home / "old")))
        self.assertEqual(str(self.home / "new"), data_dir_in(self.home, FLUENT_DATA_DIR=str(self.home / "old"), LANI_DATA_DIR=str(self.home / "new")))

    def test_the_plugin_data_stays_in_fluent_data_until_lani_data_exists(self):
        self.assertEqual(str(self.home / ".claude/lani-data"), data_dir_in(self.home))
        (self.home / ".claude/fluent-data").mkdir(parents=True)
        self.assertEqual(str(self.home / ".claude/fluent-data"), data_dir_in(self.home))
        (self.home / ".claude/lani-data").mkdir(parents=True)
        self.assertEqual(str(self.home / ".claude/lani-data"), data_dir_in(self.home))


if __name__ == "__main__":
    unittest.main()
