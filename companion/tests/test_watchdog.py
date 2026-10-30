"""Tests for companion/bin/lani-watchdog and lani-session --stop. Run: python3 -m unittest discover -s companion/tests

Never the real services: the decisions are a pure function; the rounds run against fake lani-profile, lani-bridge,
lani-session and tmux (a "world" file says what is up, and each fake writes down how it was called), with the state
directory, the memory and the systemd units in a temp dir.
"""
from __future__ import annotations

import importlib.machinery
import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
_path = ROOT / "companion/bin/lani-watchdog"
_loader = importlib.machinery.SourceFileLoader("lani_watchdog", str(_path))
_spec = importlib.util.spec_from_loader("lani_watchdog", _loader)
wd = importlib.util.module_from_spec(_spec)
sys.modules["lani_watchdog"] = wd  # dataclasses look their module up
_loader.exec_module(wd)

CFG = wd.Config()  # 4 GB, held 120 min, a session's grace 10 min, detached 6 min
NOW = 1_800_000_000.0


def seen(**kw) -> "wd.Seen":
    base = dict(learner="teo", mode="service", bridge_up=True, attached=True, session=True, session_age=3600.0,
                held_bridge=None, held_tutor=None)
    return wd.Seen(**{**base, **kw})


class DecideTest(unittest.TestCase):
    def kinds(self, s, prev=None, gb=20.0, paused=None):
        actions, note, state = wd.decide(s, prev or {}, NOW, CFG, gb, paused)
        return [a.kind for a in actions], note, state

    def test_all_up_nothing(self):
        self.assertEqual(([], "", {}), self.kinds(seen()))

    def test_down_is_started_the_bridge_first(self):
        kinds, note, _ = self.kinds(seen(bridge_up=False, attached=False, session=False, session_age=0))
        self.assertEqual(["bridge", "tutor"], kinds)
        self.assertEqual("", note)
        # the bridge alone: its tutor's shim reconnects once it is up
        self.assertEqual(["bridge"], self.kinds(seen(bridge_up=False, attached=False))[0])
        # the tutor's session alone
        self.assertEqual(["tutor"], self.kinds(seen(session=False, attached=False, session_age=0))[0])

    def test_stopped_on_purpose_is_left_down_until_the_hold_ends(self):
        kinds, note, _ = self.kinds(seen(session=False, attached=False, held_tutor=5 * 60))
        self.assertEqual([], kinds)
        self.assertIn("stopped on purpose", note)
        # two hours later: started again
        self.assertEqual(["tutor"], self.kinds(seen(session=False, attached=False, held_tutor=121 * 60))[0])
        # the bridge stopped on purpose: neither it nor its tutor
        kinds, note, _ = self.kinds(seen(bridge_up=False, attached=False, session=False, held_bridge=60))
        self.assertEqual([], kinds)
        self.assertIn("bridge service was stopped on purpose", note)

    def test_low_memory_waits_and_says_so(self):
        kinds, note, _ = self.kinds(seen(bridge_up=False, attached=False, session=False), gb=3.2)
        self.assertEqual([], kinds)
        self.assertIn("only 3.2 GB available (needs 4)", note)
        self.assertIn("the bridge service is down", note)

    def test_paused_starts_nothing(self):
        kinds, note, _ = self.kinds(seen(session=False, attached=False), paused="until lani-watchdog --resume")
        self.assertEqual([], kinds)
        self.assertIn("paused", note)

    def test_a_session_without_its_tutor_gets_time_then_a_restart(self):
        # a young session: still starting
        kinds, note, state = self.kinds(seen(attached=False, session_age=120))
        self.assertEqual([], kinds)
        self.assertIn("isn't attached yet", note)
        # an older one, first seen detached: its time noted, waited for
        kinds, note, state = self.kinds(seen(attached=False))
        self.assertEqual(([], NOW), (kinds, state["detached_since"]))
        self.assertIn("restarted if it still isn't", note)
        # 4 minutes later, still waiting; 7 minutes later, restarted
        self.assertEqual([], wd.decide(seen(attached=False), {"detached_since": NOW - 240}, NOW, CFG, 20)[0])
        self.assertEqual(["tutor"], [a.kind for a in wd.decide(seen(attached=False), {"detached_since": NOW - 420}, NOW, CFG, 20)[0]])
        # attached again: forgotten
        self.assertEqual({}, self.kinds(seen(), prev={"detached_since": NOW - 420})[2])

    def test_classic_mode_only_the_session(self):
        self.assertEqual(([], "", {}), self.kinds(seen(mode="classic", bridge_up=False, attached=False)))
        self.assertEqual(["tutor"], self.kinds(seen(mode="classic", bridge_up=False, attached=False, session=False))[0])


FAKE = r'''#!/usr/bin/env python3
# a fake of companion/bin/%(name)s: the world file says what is up; every call is written down
import json, os, sys, time
name, args = "%(name)s", sys.argv[1:]
world_path = os.environ["FAKE_WORLD"]
world = json.load(open(world_path))
with open(os.environ["FAKE_CALLS"], "a") as f:
    f.write(json.dumps([name, *args]) + "\n")
def learner():
    return args[args.index("--profile") + 1] if "--profile" in args else "default"
def save():
    json.dump(world, open(world_path, "w"))
if name == "lani-profile":
    print("\n".join(world["learners"]))
elif name == "tmux":
    if args[:1] == ["list-sessions"]:
        if not world["sessions"]:
            print("no server running", file=sys.stderr); sys.exit(1)
        for s, created in world["sessions"].items():
            print(f"{s} {created}")
elif name == "lani-bridge":
    me = world["learner"][learner()]
    if args[0] == "status":
        if "--quiet" in args:
            sys.exit(0 if me["bridge"] else 1)
        print("running, the tutor is attached since 12:00" if me["bridge"] and me["attached"] else "no tutor session is attached")
    elif args[0] == "start":
        me["bridge"] = True; save(); print("started")
elif name == "lani-session":
    if "--print" in args:
        print("companion/bin/lani-bridge start" if world["mode"] == "service" else "tmux new-session -A -s lani x")
    elif "--restart" in args:
        sys.exit(int(os.environ.get("FAKE_SESSION_EXIT", "0")))
'''


class RoundTest(unittest.TestCase):
    """A round of the real script against the fakes."""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="lani-watchdog-test-"))
        self.bin = self.tmp / "bin"
        self.bin.mkdir()
        for name in ("lani-profile", "lani-bridge", "lani-session", "tmux"):
            p = self.bin / name
            p.write_text(FAKE % {"name": name})
            p.chmod(0o755)
        self.world = self.tmp / "world.json"
        self.calls = self.tmp / "calls.jsonl"
        self.state = self.tmp / "state"
        self.mem(20)

    def mem(self, gb: float):
        (self.tmp / "meminfo").write_text(f"MemTotal: 130000000 kB\nMemAvailable: {int(gb * 1048576)} kB\n")

    def set_world(self, **learners):
        now = int(time.time())
        self.world.write_text(json.dumps({
            "learners": list(learners), "mode": "service",
            "learner": {k: {"bridge": v[0], "attached": v[1]} for k, v in learners.items()},
            "sessions": {wd.tmux_name(k): now - 3600 for k, v in learners.items() if v[2]},
        }))

    def round_(self, *args: str, **env) -> subprocess.CompletedProcess:
        self.calls.unlink(missing_ok=True)
        return subprocess.run([sys.executable, str(_path), *args], capture_output=True, text=True, timeout=60, env={
            **os.environ, "PATH": f"{self.bin}:{os.environ['PATH']}", "LANI_WATCHDOG_BIN": str(self.bin),
            "XDG_STATE_HOME": str(self.state), "LANI_WATCHDOG_MEMINFO": str(self.tmp / "meminfo"),
            "FAKE_WORLD": str(self.world), "FAKE_CALLS": str(self.calls), **env})

    def starts(self) -> list:
        calls = [json.loads(l) for l in self.calls.read_text().splitlines()] if self.calls.exists() else []
        return [c for c in calls if c[0] == "lani-bridge" and c[1] == "start" or c[0] == "lani-session" and "--restart" in c]

    def log(self) -> str:
        p = self.state / "lani/watchdog.log"
        return p.read_text() if p.exists() else ""

    def test_the_tmux_server_died_everything_comes_back_then_nothing(self):
        # bridge, attached, session — as on 2026-10-30: the server gone, both learners down
        self.set_world(default=(False, False, False), teo=(False, False, False))
        r = self.round_()
        self.assertEqual(0, r.returncode, r.stdout + r.stderr)
        self.assertEqual([["lani-bridge", "start"], ["lani-session", "--restart", "--profile", "default", "--now"],
                          ["lani-bridge", "start", "--profile", "teo"], ["lani-session", "--restart", "--profile", "teo", "--now"]],
                         self.starts())
        log = self.log()
        self.assertIn("teo: its tmux session is gone: lani-session --restart --profile teo --now: ok", log)
        self.assertIn("default: the bridge service is down: lani-bridge start: ok", log)
        # all up: the next round starts nothing and writes nothing
        self.set_world(default=(True, True, True), teo=(True, True, True))
        before = self.log()
        self.round_()
        self.assertEqual([], self.starts())
        self.assertEqual(before, self.log())

    def test_attached_nothing(self):
        self.set_world(default=(True, True, True))
        self.round_()
        self.assertEqual([], self.starts())
        self.assertEqual("", self.log())

    def test_stopped_on_purpose_nothing_and_said_once(self):
        self.set_world(teo=(True, False, False))
        hold = self.state / "lani/held/tutor-teo"
        hold.parent.mkdir(parents=True)
        hold.write_text("2026-10-30T12:00:00+01:00\n")
        for _ in range(2):
            self.round_()
            self.assertEqual([], self.starts())
        self.assertEqual(1, self.log().count("stopped on purpose"), self.log())
        # the hold is old: started
        old = time.time() - 3 * 3600
        os.utime(hold, (old, old))
        self.round_()
        self.assertEqual([["lani-session", "--restart", "--profile", "teo", "--now"]], self.starts())

    def test_low_memory_waits_and_logs_it(self):
        self.set_world(teo=(False, False, False))
        self.mem(2.5)
        self.round_()
        self.assertEqual([], self.starts())
        self.assertIn("teo: only 2.5 GB available (needs 4): not started", self.log())
        self.mem(16)
        self.round_()
        self.assertEqual(2, len(self.starts()))

    def test_a_failed_start_is_logged_and_the_round_fails(self):
        self.set_world(teo=(True, False, False))
        r = self.round_(FAKE_SESSION_EXIT="1")
        self.assertEqual(1, r.returncode)
        self.assertIn("lani-session --restart --profile teo --now: failed (1)", self.log())

    def test_paused_then_resumed(self):
        self.set_world(teo=(True, False, False))
        self.round_("--pause")
        self.round_()
        self.assertEqual([], self.starts())
        self.assertIn("teo: paused (until lani-watchdog --resume)", self.log())
        self.round_("--resume")
        self.round_()
        self.assertEqual(1, len(self.starts()))

    def test_dry_run_starts_and_writes_nothing(self):
        self.set_world(teo=(False, False, False))
        r = self.round_("--dry-run")
        self.assertEqual([], self.starts())
        self.assertIn("would start: the bridge service is down", r.stdout)
        self.assertEqual("", self.log())

    def test_install_is_idempotent(self):
        units = self.tmp / "units"
        ctl = self.bin / "systemctl"
        ctl.write_text("#!/bin/sh\necho \"$@\" >> \"$FAKE_CALLS.ctl\"\n[ \"$2\" = is-active ] && echo active\nexit 0\n")
        ctl.chmod(0o755)
        login = self.bin / "loginctl"
        login.write_text("#!/bin/sh\necho no\n")
        login.chmod(0o755)
        env = {"LANI_WATCHDOG_UNIT_DIR": str(units), "LANI_WATCHDOG_SYSTEMCTL": str(ctl), "LANI_WATCHDOG_LOGINCTL": str(login)}
        self.set_world(teo=(True, True, True))
        r = self.round_("--install", **env)
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn("linger is off", r.stdout)
        service = (units / "lani-watchdog.service").read_text()
        self.assertNotIn("@", service.replace("user@", ""))
        self.assertIn(f"ExecStart=/usr/bin/env python3 {ROOT}/companion/bin/lani-watchdog", service)
        self.assertIn("OnUnitInactiveSec=3min", (units / "lani-watchdog.timer").read_text())
        ctl_calls = Path(str(self.calls) + ".ctl").read_text().splitlines()
        self.assertEqual(["--user daemon-reload", "--user enable --now lani-watchdog.timer"], ctl_calls)
        # again: nothing rewritten, no reload
        Path(str(self.calls) + ".ctl").unlink()
        r = self.round_("--install", **env)
        self.assertIn("up to date", r.stdout)
        self.assertEqual(["--user enable --now lani-watchdog.timer"], Path(str(self.calls) + ".ctl").read_text().splitlines())


class SessionStopTest(unittest.TestCase):
    """lani-session --stop against a fake tmux: the tutor quits, and its hold is left for the watchdog."""

    def test_stop_quits_the_tutor_and_holds_it_down(self):
        tmp = Path(tempfile.mkdtemp(prefix="lani-session-stop-"))
        fake_bin = tmp / "bin"
        fake_bin.mkdir()
        sessions = tmp / "sessions"
        sessions.write_text("lani-teo $1\n")
        tmux = fake_bin / "tmux"
        # list-sessions from the file; "/exit" sent to the session closes it
        tmux.write_text(f"""#!/usr/bin/env bash
echo "$*" >> {tmp}/tmux.calls
case "$1" in
  list-sessions) [ -s {sessions} ] || exit 1; cat {sessions} ;;
  list-panes) echo %1 ;;
  send-keys) [ "$4" = /exit ] && : > {sessions} ;;
esac
exit 0
""")
        tmux.chmod(0o755)
        env = {**os.environ, "PATH": f"{fake_bin}:{os.environ['PATH']}", "XDG_STATE_HOME": str(tmp / "state"), "LANI_ENV_FILE": "/dev/null"}
        r = subprocess.run([str(ROOT / "companion/bin/lani-session"), "--stop", "--profile", "teo"], capture_output=True, text=True, timeout=60, env=env)
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn("lani-teo stopped; the watchdog leaves it down for 120 minutes", r.stderr)
        self.assertTrue((tmp / "state/lani/held/tutor-teo").exists())
        calls = (tmp / "tmux.calls").read_text()
        self.assertIn("send-keys -t %1 /exit Enter", calls)
        self.assertNotIn("kill-session", calls)
        # not running: held down all the same
        r = subprocess.run([str(ROOT / "companion/bin/lani-session"), "--stop"], capture_output=True, text=True, timeout=60, env=env)
        self.assertIn("lani isn't running", r.stderr)
        self.assertTrue((tmp / "state/lani/held/tutor-default").exists())


if __name__ == "__main__":
    unittest.main()
