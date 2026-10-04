"""Tests for companion/bin/lani-backup. Run: python3 -m unittest discover companion/tests"""
from __future__ import annotations

import contextlib
import importlib.machinery
import importlib.util
import io
import json
import os
import socket
import sqlite3
import sys
import tempfile
import unittest
from datetime import datetime, timedelta
from pathlib import Path

sys.dont_write_bytecode = True
_path = Path(__file__).resolve().parents[1] / "bin" / "lani-backup"
_loader = importlib.machinery.SourceFileLoader("lani_backup", str(_path))
_spec = importlib.util.spec_from_loader("lani_backup", _loader)
fb = importlib.util.module_from_spec(_spec)
sys.modules["lani_backup"] = fb  # dataclasses look their module up
_loader.exec_module(fb)


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class BackupTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.data, self.results, self.backups = root / "data", root / "results", root / "backups"
        (self.data / "app/release").mkdir(parents=True)
        (self.data / "app/voice/files").mkdir(parents=True)
        self.results.mkdir()
        self.write("data/spaced-repetition.json", {"items": [1, 2, 3]})
        self.write("data/app/game.json", {"rev": 1})
        (self.data / "app/voice/files/a.mp3").write_bytes(b"\xff" * 5000)
        (self.data / "app/release/lani-1.apk").write_bytes(b"apk")
        (self.data / "app/game.json.tmp").write_text("{")
        (self.results / "session-001.md").write_text("# one\n")
        with contextlib.closing(sqlite3.connect(self.data / "app/voice/voice.db")) as db:
            db.execute("PRAGMA journal_mode=WAL")
            db.execute("CREATE TABLE clips (text TEXT)")
            db.execute("INSERT INTO clips VALUES ('dober dan')")
            db.commit()
        self.env = {
            "LANI_DATA_DIR": str(self.data),
            "LANI_RESULTS_DIR": str(self.results),
            "LANI_BACKUP_DIR": str(self.backups),
            "LANI_BRIDGE_PORT": str(free_port()),
        }
        self.saved = {k: os.environ.get(k) for k in self.env}
        os.environ.update(self.env)

    def tearDown(self):
        for k, v in self.saved.items():
            if v is None:
                os.environ.pop(k, None)
            else:
                os.environ[k] = v
        self.tmp.cleanup()

    def write(self, rel: str, doc) -> None:
        top, rest = rel.split("/", 1)
        p = (self.data if top == "data" else self.results) / rest
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(doc))

    def take(self, **kw):
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return fb.take(**kw)

    def test_snapshot_holds_data_and_results_but_not_apks_or_temp_files(self):
        snap = self.take()
        files = snap.manifest()["files"]
        self.assertIn("data/spaced-repetition.json", files)
        self.assertIn("results/session-001.md", files)
        self.assertIn("data/app/voice/voice.db", files)
        self.assertNotIn("data/app/release/lani-1.apk", files)
        self.assertNotIn("data/app/game.json.tmp", files)
        self.assertFalse(any(k.endswith(("-wal", "-shm")) for k in files))
        self.assertEqual(oct(self.backups.stat().st_mode & 0o777), "0o700")

    def test_sqlite_copy_is_readable(self):
        snap = self.take()
        with contextlib.closing(sqlite3.connect(snap.path / "data/app/voice/voice.db")) as db:
            self.assertEqual(db.execute("SELECT text FROM clips").fetchall(), [("dober dan",)])

    def test_unchanged_data_is_skipped_and_unchanged_files_are_shared(self):
        first = self.take()
        self.assertIsNone(self.take())
        self.write("data/spaced-repetition.json", {"items": [1, 2, 3, 4]})
        second = self.take()
        self.assertNotEqual(first.name, second.name)
        mp3 = "data/app/voice/files/a.mp3"
        self.assertTrue((first.path / mp3).samefile(second.path / mp3))
        srs = "data/spaced-repetition.json"
        self.assertFalse((first.path / srs).samefile(second.path / srs))
        self.assertEqual(json.loads((first.path / srs).read_text())["items"], [1, 2, 3])

    def test_always_takes_one_even_when_unchanged(self):
        first = self.take()
        again = self.take(always=True)
        self.assertIsNotNone(again)
        self.assertGreater(again.at, first.at)

    def test_invalid_json_is_kept_with_a_warning(self):
        (self.data / "mistakes-db.json").write_text("{broken")
        snap = self.take()
        self.assertIn("data/mistakes-db.json", snap.manifest()["files"])
        self.assertTrue(any("not valid JSON" in w for w in snap.manifest()["warnings"]))

    def test_verify_notices_damage(self):
        snap = self.take()
        self.assertEqual(fb.verify(snap), [])
        (snap.path / "data/app/game.json").write_text('{"rev": 2}')
        self.assertTrue(any("game.json" in p for p in fb.verify(snap)))

    def test_retention(self):
        now = datetime(2026, 9, 23, 12)
        hourly = [now - timedelta(hours=6 * i) for i in range(4 * 400)]  # every 6 h for 400 days
        kept = fb.keep(hourly, now)
        self.assertTrue(set(hourly[:7]) <= kept)
        days = {d.date() for d in kept if now - d <= timedelta(days=14)}
        self.assertEqual(len(days), 15)  # today and the 14 before it
        self.assertLessEqual(len(kept), 7 + 15 + 9 + 13)
        self.assertFalse(any(now - d > timedelta(days=366) for d in kept))
        self.assertEqual(fb.keep([], now), set())

    def test_prune_keeps_shared_files_alive(self):
        first = self.take()
        self.write("data/app/game.json", {"rev": 2})
        second = self.take()
        with contextlib.redirect_stdout(io.StringIO()):
            gone = fb.prune(self.backups, now=datetime.now() + timedelta(days=400))
        self.assertEqual(gone, [])  # the newest 7 always stay
        for i in range(8):
            self.write("data/app/game.json", {"rev": 3 + i})
            self.take()  # prunes as it goes: beyond the newest 7, one per day
        names = [s.name for s in fb.snapshots()]
        self.assertEqual(len(names), 7)
        self.assertNotIn(first.name, names)
        self.assertNotIn(second.name, names)
        newest = fb.snapshots()[0]  # its mp3 is a link to the pruned first copy
        self.assertEqual((newest.path / "data/app/voice/files/a.mp3").read_bytes(), b"\xff" * 5000)
        self.assertEqual(fb.verify(newest), [])

    def test_restore_dry_run_writes_nothing(self):
        snap = self.take()
        self.write("data/spaced-repetition.json", {"items": []})
        with contextlib.redirect_stdout(io.StringIO()) as out:
            self.assertEqual(fb.restore(snap, [], apply=False, force=False), 0)
        self.assertIn("spaced-repetition.json", out.getvalue())
        self.assertEqual(json.loads((self.data / "spaced-repetition.json").read_text()), {"items": []})
        self.assertEqual(len(fb.snapshots()), 1)

    def test_restore_apply(self):
        snap = self.take()
        self.write("data/spaced-repetition.json", {"items": []})
        (self.data / "app/game.json").unlink()
        self.write("data/app/new.json", {"new": True})
        with contextlib.closing(sqlite3.connect(self.data / "app/voice/voice.db")) as db:
            db.execute("INSERT INTO clips VALUES ('nasvidenje')")
            db.commit()
        Path(f"{self.data}/app/voice/voice.db-wal").write_bytes(b"stale")
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(fb.restore(snap, [], apply=True, force=False), 0)
        self.assertEqual(json.loads((self.data / "spaced-repetition.json").read_text()), {"items": [1, 2, 3]})
        self.assertEqual(json.loads((self.data / "app/game.json").read_text()), {"rev": 1})
        self.assertTrue((self.data / "app/new.json").exists())  # newer files are left alone
        self.assertFalse(Path(f"{self.data}/app/voice/voice.db-wal").exists())
        with contextlib.closing(sqlite3.connect(self.data / "app/voice/voice.db")) as db:
            self.assertEqual(db.execute("SELECT text FROM clips").fetchall(), [("dober dan",)])
        names = [s.name for s in fb.snapshots()]
        self.assertTrue(any(n.endswith("-pre-restore") for n in names))
        pre = next(s for s in fb.snapshots() if s.name.endswith("-pre-restore"))
        self.assertEqual(json.loads((pre.path / "data/spaced-repetition.json").read_text()), {"items": []})

    def test_restore_only_some_paths(self):
        snap = self.take()
        self.write("data/spaced-repetition.json", {"items": []})
        self.write("data/app/game.json", {"rev": 9})
        with contextlib.redirect_stdout(io.StringIO()):
            fb.restore(snap, ["data/app"], apply=True, force=False)
        self.assertEqual(json.loads((self.data / "app/game.json").read_text()), {"rev": 1})
        self.assertEqual(json.loads((self.data / "spaced-repetition.json").read_text()), {"items": []})

    def test_restore_refuses_while_the_bridge_runs(self):
        snap = self.take()
        self.write("data/app/game.json", {"rev": 9})
        with socket.socket() as s:
            s.bind(("127.0.0.1", 0))
            s.listen()
            os.environ["LANI_BRIDGE_PORT"] = str(s.getsockname()[1])
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(fb.restore(snap, [], apply=True, force=False), 2)
        self.assertEqual(json.loads((self.data / "app/game.json").read_text()), {"rev": 9})

    def test_learner_files_restore_while_the_bridge_runs(self):
        snap = self.take()
        self.write("data/spaced-repetition.json", {"items": []})
        with socket.socket() as s:
            s.bind(("127.0.0.1", 0))
            s.listen()
            os.environ["LANI_BRIDGE_PORT"] = str(s.getsockname()[1])
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(fb.restore(snap, ["data/spaced-repetition.json"], apply=True, force=False), 0)
        self.assertEqual(json.loads((self.data / "spaced-repetition.json").read_text()), {"items": [1, 2, 3]})


if __name__ == "__main__":
    unittest.main()


class ProfileTest(unittest.TestCase):
    """Another learner on this machine: their data from the registry, their own backup directory and timer."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / "profiles/luka/data").mkdir(parents=True)
        (self.root / "profiles/profiles.json").write_text(json.dumps({
            "schema": "lani.profiles/v1",
            "profiles": [{"id": "luka", "name": "Luka", "data_dir": "luka/data"}],
        }))
        keys = ("LANI_PROFILES_DIR", "LANI_DATA_DIR", "LANI_RESULTS_DIR", "LANI_BACKUP_DIR", "HOME")
        self.saved = {k: os.environ.get(k) for k in keys}
        for k in keys:
            os.environ.pop(k, None)
        os.environ["LANI_PROFILES_DIR"] = str(self.root / "profiles")
        os.environ["HOME"] = str(self.root / "home")
        self.unit = fb.UNIT

    def tearDown(self):
        for k, v in self.saved.items():
            if v is None:
                os.environ.pop(k, None)
            else:
                os.environ[k] = v
        fb.UNIT = self.unit
        self.tmp.cleanup()

    def test_a_learner_has_their_own_data_backups_and_timer(self):
        fb.use_profile("luka")
        data = (self.root / "profiles/luka/data").resolve()
        self.assertEqual(data, fb.data_dir())
        self.assertEqual(data.parent / "results", fb.sources()["results"])
        self.assertEqual(self.root / "home/.local/share/lani/backups/luka", fb.backup_dir())
        self.assertEqual("lani-backup-luka", fb.UNIT)

    def test_the_default_profile_is_unchanged(self):
        fb.use_profile("default")
        fb.use_profile("")
        self.assertEqual("lani-backup", fb.UNIT)
        self.assertNotIn("LANI_DATA_DIR", os.environ)

    def test_snapshots_made_before_the_rename_stay_where_they_are(self):
        old = self.root / "home/.local/share/fluent/backups"
        old.mkdir(parents=True)
        self.assertEqual(old, fb.backup_dir())
        fb.use_profile("luka")
        self.assertEqual(old / "luka", fb.backup_dir())

    def test_the_variables_from_before_the_rename_are_read(self):
        os.environ["FLUENT_DATA_DIR"] = str(self.root / "old")
        try:
            fb.adopt_legacy_env()
            self.assertEqual((self.root / "old").resolve(), fb.data_dir())
        finally:
            os.environ.pop("FLUENT_DATA_DIR", None)

    def test_an_unknown_learner_is_an_error(self):
        with self.assertRaises(SystemExit):
            fb.use_profile("nobody")
