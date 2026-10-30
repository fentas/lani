"""Tests for companion/bin/qa-slot (QA on the first free of two emulator slots). Run: python3 -m unittest discover -s companion/tests

No emulator, adb or Gradle: the slots' locks, the queue and the memory are files in a temp dir, qa is a fake script that
writes down how it was run, and slot 2's emulator calls are stubbed.
"""
from __future__ import annotations

import fcntl
import importlib.machinery
import importlib.util
import json
import os
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock

sys.dont_write_bytecode = True
_path = Path(__file__).resolve().parents[1] / "bin" / "qa-slot"
_loader = importlib.machinery.SourceFileLoader("qa_slot", str(_path))
_spec = importlib.util.spec_from_loader("qa_slot", _loader)
qs = importlib.util.module_from_spec(_spec)
sys.modules["qa_slot"] = qs  # dataclasses look their module up
_loader.exec_module(qs)

FAKE_QA = """#!/usr/bin/env python3
import json, os, sys
keys = ("ANDROID_SERIAL", "LANI_DEV_PORT", "LANI_DEV_TOWN_PORT", "LANI_TUTOR_MOCK_PORT", "LANI_QA_NOBODY_PORT", "LANI_QA_SLOT",
        "LANI_QA_GRADLE_NO_DAEMON")
with open(os.environ["FAKE_QA_LOG"], "a") as f:
    f.write(json.dumps({"args": sys.argv[1:], "env": {k: os.environ.get(k) for k in keys}}) + "\\n")
sys.exit(int(os.environ.get("FAKE_QA_EXIT", "0")))
"""


def hold(path: str) -> subprocess.Popen:
    """Another process holding [path]'s lock: flock(1) and its command, in a process group of their own."""
    p = subprocess.Popen(["flock", path, "sleep", "30"], start_new_session=True)
    for _ in range(50):
        if qs.holder(path):
            break
        time.sleep(0.1)
    return p


def release(p: subprocess.Popen):
    """[hold]'s process group ended: flock's command keeps the lock's file open (and the lock) after flock is gone."""
    try:
        os.killpg(p.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    p.wait()


# qa-slot loaded in a child process, taking the lock its parent (flock) holds
INSIDE = """import importlib.machinery, importlib.util, sys
loader = importlib.machinery.SourceFileLoader("q", sys.argv[1])
q = importlib.util.module_from_spec(importlib.util.spec_from_loader("q", loader))
sys.modules["q"] = q
loader.exec_module(q)
lk = q.Lock(sys.argv[2])
print(lk.take(), lk.inherited)
"""


class SlotsTest(unittest.TestCase):
    def test_the_two_slots_keep_apart(self):
        one, two = qs.slots({})
        self.assertEqual(("/tmp/lani-emulator.lock", "emulator-5554", 5554), (one.lock, one.serial, one.console))
        self.assertEqual(("/tmp/lani-emulator-2.lock", "emulator-5556", 5556), (two.lock, two.serial, two.console))
        # no port shared, none a learner's (8790, 8792–8799 but QA's town 8797) or a worker's (8785 Drama 3, 8795, 8796)
        ports = [*one.ports, one.nobody, *two.ports, two.nobody]
        self.assertEqual(len(ports), len(set(ports)))
        for p in two.ports:
            self.assertFalse(8790 <= p <= 8799 or p in (8785, 8795, 8796), p)
        self.assertEqual((8791, 8797, 8771), one.ports)
        # what qa reads: slot 2 read-only, slot 1 as qa always ran
        self.assertEqual({"ANDROID_SERIAL": "emulator-5556", "LANI_DEV_PORT": "8781", "LANI_DEV_TOWN_PORT": "8787",
                          "LANI_TUTOR_MOCK_PORT": "8761", "LANI_QA_NOBODY_PORT": "19611", "LANI_QA_SLOT": "2"}, two.env())
        self.assertTrue(two.ephemeral and not one.ephemeral)

    def test_the_slot_ports_are_the_bridges_reserved_ones(self):
        learners = (Path(__file__).resolve().parents[1] / "bridge/src/learners.ts").read_text()
        for p in qs.slots({})[1].ports[:2]:
            self.assertIn(str(p), learners, "slot 2's dev bridge and town reserved in src/learners.ts RESERVED_PORTS")

    def test_what_qa_builds(self):
        self.assertTrue(qs.builds([]))
        self.assertTrue(qs.builds(["--steps", "visit", "--minified"]))
        for args in (["--no-build"], ["--apk", "x.apk"], ["--apk=x.apk"], ["--dry"], ["--bridges-only"], ["--build-only"], ["-h"]):
            self.assertFalse(qs.builds(args), args)


class MachineTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="qa-slot-test-"))

    def meminfo(self, gb: float) -> str:
        p = self.tmp / "meminfo"
        p.write_text(f"MemTotal: 130000000 kB\nMemFree: 1000 kB\nMemAvailable: {int(gb * 1048576)} kB\n")
        return str(p)

    def test_memory_and_slot_2(self):
        two = qs.slots({})[1]
        with mock.patch.dict(os.environ, {"LANI_QA_MEMINFO": self.meminfo(20.5)}), mock.patch.object(qs, "emulators", lambda: []):
            self.assertAlmostEqual(20.5, qs.available_gb(), places=3)
            with mock.patch.object(qs, "SDK", self.tmp):
                (self.tmp / "emulator").mkdir()
                (self.tmp / "emulator/emulator").write_text("")
                self.assertIsNone(qs.usable(two))
                with mock.patch.dict(os.environ, {"LANI_QA_SLOT2": "0"}):
                    self.assertIn("off", qs.usable(two))
        with mock.patch.dict(os.environ, {"LANI_QA_MEMINFO": self.meminfo(9.0)}):
            self.assertIn("14 GB", qs.usable(two))
        with mock.patch.dict(os.environ, {"LANI_QA_MEMINFO": self.meminfo(9.0), "LANI_QA_MIN_GB": "8"}):
            self.assertIsNone(qs.usable(qs.slots({})[0]))  # slot 1 doesn't wait for memory

    def test_a_lock_another_process_holds_is_not_taken_one_an_ancestor_holds_is_ours(self):
        path = str(self.tmp / "slot.lock")
        holder = hold(path)
        try:
            self.assertIn(f"pid {holder.pid}", qs.holder(path))
            self.assertFalse(qs.Lock(path).take(), "held by a process that isn't ours")
        finally:
            release(holder)
        # a qa-slot started inside the lock (flock FILE qa-slot …) has it already: its own, not one to wait for
        out = subprocess.run(["flock", path, sys.executable, "-c", INSIDE, str(_path), path], capture_output=True, text=True, timeout=60)
        self.assertEqual("True True", out.stdout.strip(), out.stderr)
        # free again: taken, and given back
        lk = qs.Lock(path)
        self.assertTrue(lk.take())
        self.assertFalse(lk.inherited)
        fd = os.open(path, os.O_RDWR)
        with self.assertRaises(OSError):
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        lk.free()
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        os.close(fd)


class QueueTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="qa-slot-queue-"))
        self.env = mock.patch.dict(os.environ, {"LANI_QA_QUEUE_DIR": str(self.tmp)})
        self.env.start()
        self.addCleanup(self.env.stop)

    def ticket_of(self, pid: int, wants: list, at: int) -> Path:
        p = self.tmp / f"{at:020d}-{pid}.json"
        p.write_text(json.dumps({"pid": pid, "slots": wants}))
        return p

    def test_runs_take_turns_in_the_order_they_came(self):
        sleeper = subprocess.Popen(["sleep", "30"])
        try:
            self.ticket_of(sleeper.pid, [2], at=1)  # an earlier run that waits for slot 2 only (talk-mic)
            mine = qs.Ticket([1, 2])
            self.assertTrue(mine.may_take(1), "slot 1: nobody before wants it")
            self.assertFalse(mine.may_take(2), "slot 2: the earlier run's turn")
            self.assertEqual(2, len(qs.queued()))
        finally:
            sleeper.kill()
            sleeper.wait()
        # its run is gone: its ticket doesn't count, and is cleared away
        self.assertTrue(mine.may_take(2))
        self.assertEqual([mine.path], sorted(self.tmp.glob("*.json")))
        mine.done()
        self.assertEqual([], list(self.tmp.glob("*.json")))


class RunTest(unittest.TestCase):
    """take_and_run end to end with a fake qa."""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="qa-slot-run-"))
        self.log = self.tmp / "qa.log"
        fake = self.tmp / "qa"
        fake.write_text(FAKE_QA)
        fake.chmod(0o755)
        mem = self.tmp / "meminfo"
        mem.write_text("MemAvailable: 33554432 kB\n")  # 32 GB
        self.env = mock.patch.dict(os.environ, {
            "LANI_QA_EMULATOR_LOCK": str(self.tmp / "e1.lock"), "LANI_QA_EMULATOR_LOCK_2": str(self.tmp / "e2.lock"),
            "LANI_QA_QUEUE_DIR": str(self.tmp / "queue"), "LANI_QA_MEMINFO": str(mem), "LANI_HEAVY_LOCKS": "",
            "FAKE_QA_LOG": str(self.log),
        })
        self.env.start()
        self.addCleanup(self.env.stop)
        for patch in (mock.patch.object(qs, "QA", fake), mock.patch.object(qs, "SDK", self.tmp),
                      mock.patch.object(qs, "emulators", lambda: []), mock.patch.object(qs, "adb", lambda *a, **k: ""),
                      mock.patch.object(qs, "cleanup_ports", lambda slot, keep_bridge: None)):
            patch.start()
            self.addCleanup(patch.stop)
        (self.tmp / "emulator").mkdir()
        (self.tmp / "emulator/emulator").write_text("")

    def runs(self) -> list:
        return [json.loads(l) for l in self.log.read_text().splitlines()] if self.log.exists() else []

    def test_slot_1_first_with_its_ports(self):
        self.assertEqual(0, qs.take_and_run([1, 2], ["--steps", "home", "--no-build"]))
        (r,) = self.runs()
        self.assertEqual(["--steps", "home", "--no-build"], r["args"])
        self.assertEqual("emulator-5554", r["env"]["ANDROID_SERIAL"])
        self.assertEqual("8791", r["env"]["LANI_DEV_PORT"])

    def test_slot_2_while_slot_1_is_held_its_emulator_stopped_after(self):
        holder = hold(os.environ["LANI_QA_EMULATOR_LOCK"])
        stopped = []
        try:
            with mock.patch.object(qs, "stop_emulator", lambda slot, why: stopped.append((slot.n, why))):
                with mock.patch.dict(os.environ, {"FAKE_QA_EXIT": "3"}):
                    self.assertEqual(3, qs.take_and_run([1, 2], ["--no-build"]), "qa's exit code")
        finally:
            release(holder)
        (r,) = self.runs()
        self.assertEqual(("emulator-5556", "8781", "8787", "8761", "2"),
                         tuple(r["env"][k] for k in ("ANDROID_SERIAL", "LANI_DEV_PORT", "LANI_DEV_TOWN_PORT", "LANI_TUTOR_MOCK_PORT", "LANI_QA_SLOT")))
        self.assertEqual([(2, "the run is over")], stopped)
        self.assertFalse(Path(os.environ["LANI_QA_EMULATOR_LOCK_2"] + ".run.json").exists(), "the run's marker gone")
        # both locks free again (the holder's gone): taken without waiting
        for k in ("LANI_QA_EMULATOR_LOCK", "LANI_QA_EMULATOR_LOCK_2"):
            lk = qs.Lock(os.environ[k])
            self.assertTrue(lk.take())
            lk.free()

    def test_no_slot_in_time_is_75_and_nothing_runs(self):
        holder = hold(os.environ["LANI_QA_EMULATOR_LOCK"])
        try:
            with mock.patch.dict(os.environ, {"LANI_QA_SLOT2": "0", "LANI_QA_WAIT_S": "1"}):
                self.assertEqual(75, qs.take_and_run([1, 2], ["--no-build"]))
        finally:
            release(holder)
        self.assertEqual([], self.runs())
        self.assertEqual([], list((self.tmp / "queue").glob("*.json")), "its ticket gone")

    def test_an_emulator_nobody_here_started_keeps_slot_2_closed(self):
        holder = hold(os.environ["LANI_QA_EMULATOR_LOCK"])
        try:
            with mock.patch.object(qs, "emulators", lambda: [(4242, 5556, True, "fluent-qa")]), \
                    mock.patch.dict(os.environ, {"LANI_QA_WAIT_S": "1"}):
                self.assertEqual(75, qs.take_and_run([2], ["--no-build"]))
        finally:
            release(holder)
        self.assertEqual([], self.runs())

    def test_a_build_first_then_qa_without_one(self):
        with mock.patch.object(sys, "argv", ["qa-slot", "--minified", "--steps", "home"]):
            self.assertEqual(0, qs.main(["--minified", "--steps", "home"]))
        build, run = self.runs()
        self.assertEqual(["--build-only", "--minified"], build["args"])
        self.assertEqual("1", build["env"]["LANI_QA_GRADLE_NO_DAEMON"])
        self.assertEqual(["--minified", "--steps", "home", "--no-build"], run["args"])

    def test_a_writable_emulator_from_before_keeps_slot_2_closed_and_says_how_to_open_it(self):
        two = qs.slots({})[1]
        with mock.patch.object(qs, "emulators", lambda: [(111, 5554, False, qs.avd())]):
            why = qs.usable(two)
        self.assertIn("emulator-5554 runs writable", why)
        self.assertIn("adb -s emulator-5554 emu kill", why)
        with mock.patch.object(qs, "emulators", lambda: [(111, 5554, True, qs.avd())]):
            self.assertIsNone(qs.usable(two), "beside a read-only one: open")
        with mock.patch.object(qs, "emulators", lambda: [(111, 5554, False, "another-avd")]):
            self.assertIsNone(qs.usable(two), "another AVD's writable emulator is no instance of this one")
        holder = hold(os.environ["LANI_QA_EMULATOR_LOCK"])
        try:
            with mock.patch.object(qs, "emulators", lambda: [(111, 5554, False, qs.avd())]), mock.patch.dict(os.environ, {"LANI_QA_WAIT_S": "1"}):
                self.assertEqual(75, qs.take_and_run([2], ["--no-build"]))
        finally:
            release(holder)
        self.assertEqual([], self.runs())

    def test_never_the_tablets_adb_server(self):
        with mock.patch.dict(os.environ, {"ANDROID_ADB_SERVER_PORT": "5038"}):
            self.assertEqual(2, qs.main(["--steps", "home"]))
        self.assertEqual([], self.runs())


if __name__ == "__main__":
    unittest.main()
