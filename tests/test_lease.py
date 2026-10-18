"""Who writes a learner's data repository: the lease (.claude/hooks/lani_lease.py, app/lease.json) and the node's side of a
handover (lani_data_repo.py handover / take, docs/plans/05-built/git-sync.md). The phone is played by a second clone of
the same bare remote, which writes the lease as the app does (sync/Lease.kt). Every run is in a temporary HOME.
Run: python3 -m unittest discover -s tests"""
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

os.environ["LANI_ENV_FILE"] = os.devnull  # this process never reads the machine's own lani.env

REPO = Path(__file__).resolve().parents[1]
HOOKS = REPO / ".claude" / "hooks"
sys.path.insert(0, str(HOOKS))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import lani_lease as L  # noqa: E402
from test_config import sandbox_env  # noqa: E402

TEMPLATES = REPO / "data-examples"
NODE = {"side": "node", "device": "test-node", "name": "test-node", "mode": "self-hosted"}
PHONE = {"side": "phone", "device": "p_0123abcd", "name": "Pixel 8", "mode": "hosted"}
OTHER_PHONE = {"side": "phone", "device": "p_ffff0000", "name": "Tablet", "mode": "hosted"}
T0 = datetime(2026, 10, 18, 21, 0, tzinfo=timezone.utc)


def git(cwd: Path, *args: str, check=True) -> str:
    p = subprocess.run(["git", "-C", str(cwd), "-c", "user.name=t", "-c", "user.email=t@t", *args], capture_output=True,
                       text=True)
    if check and p.returncode != 0:
        raise AssertionError(f"git {args}: {p.stderr}")
    return p.stdout.strip()


class LeaseRulesTest(unittest.TestCase):
    """The rules alone: what each state lets a side do."""

    def test_no_lease_is_the_nodes_and_a_phone_takes_it_only_when_empty(self):
        self.assertEqual("mine", L.state(None, NODE))
        self.assertTrue(L.may_write(None, NODE))
        self.assertEqual("unclaimed", L.state(None, PHONE))
        self.assertFalse(L.may_take(None, PHONE))
        self.assertEqual("free", L.state(None, PHONE, has_data=False))
        self.assertTrue(L.may_take(None, PHONE, has_data=False))
        self.assertFalse(L.may_write(None, PHONE))

    def test_a_held_lease_lets_only_its_holder_write(self):
        lease = L.held(PHONE, T0)
        self.assertEqual("mine", L.state(lease, PHONE, T0))
        self.assertEqual("other", L.state(lease, NODE, T0))
        self.assertEqual("other", L.state(lease, OTHER_PHONE, T0))
        self.assertFalse(L.may_take(lease, NODE, T0))
        # the node is the node on any machine; a phone is one device
        self.assertEqual("mine", L.state(L.held({**NODE, "device": "new-machine"}, T0), NODE, T0))

    def test_an_offer_waits_for_the_side_it_names_and_its_giver_may_take_it_back(self):
        lease = L.offered(PHONE, NODE, T0)
        self.assertIsNone(lease["holder"])
        self.assertEqual("offered", L.state(lease, PHONE, T0))
        self.assertEqual("other", L.state(lease, OTHER_PHONE, T0))  # offered to that phone only
        self.assertEqual("offered-away", L.state(lease, NODE, T0))
        self.assertFalse(L.may_write(lease, NODE))
        self.assertFalse(L.may_write(lease, PHONE))
        self.assertTrue(L.may_take(lease, PHONE, T0))
        self.assertTrue(L.may_take(lease, NODE, T0))
        # an offer to any phone
        anyone = L.offered({"side": "phone"}, NODE, T0)
        self.assertEqual("offered", L.state(anyone, OTHER_PHONE, T0))

    def test_a_lease_that_ran_out_may_be_taken_but_never_lets_anyone_write(self):
        lease = L.held(PHONE, T0)
        later = T0 + L.TERM + timedelta(minutes=1)
        self.assertEqual("expired", L.state(lease, NODE, later))
        self.assertTrue(L.may_take(lease, NODE, later))
        self.assertFalse(L.may_write(lease, NODE))
        self.assertEqual("mine", L.state(lease, PHONE, later))  # its holder still holds it

    def test_renewal_is_once_a_day_and_only_by_the_holder(self):
        lease = L.held(NODE, T0)
        self.assertIsNone(L.renewed(lease, NODE, T0 + timedelta(hours=23)))
        again = L.renewed(lease, NODE, T0 + timedelta(days=1, minutes=1))
        self.assertEqual(L.iso(T0 + timedelta(days=1, minutes=1) + L.TERM), again["expires"])
        self.assertEqual(lease["since"], again["since"])
        self.assertIsNone(L.renewed(lease, PHONE, T0 + timedelta(days=3)))

    def test_a_lease_that_cant_be_read_lets_nobody_write(self):
        bad = L.parse("{not json")
        self.assertEqual("other", L.state(bad, NODE))
        self.assertFalse(L.may_take(bad, NODE))
        self.assertEqual({"invalid": True}, L.parse('{"schema": "something/else"}'))

    def test_the_file_round_trips_and_the_app_s_shape_reads(self):
        lease = L.offered(PHONE, NODE, T0)
        self.assertEqual(lease, L.parse(L.dump(lease)))
        # as the app writes it (sync/Lease.kt): the same keys, any order, nulls explicit
        app = '{"schema":"lani.lease/v1","holder":{"side":"phone","device":"p_0123abcd","name":"Pixel 8","mode":"hosted"},' \
              '"offer":null,"since":"2026-10-18T21:00:00Z","renewed":"2026-10-18T21:00:00Z","expires":"2026-11-01T21:00:00Z"}'
        self.assertEqual("mine", L.state(L.parse(app), PHONE, T0))
        self.assertEqual("other", L.state(L.parse(app), NODE, T0))


class HandoverTest(unittest.TestCase):
    """The node's side: lani_data_repo.py handover and take against a bare remote, and what refuses to write meanwhile."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name).resolve()
        self.home = root / "home"
        self.data = self.home / ".local/share/lani/ana"
        self.data.mkdir(parents=True)
        for t in TEMPLATES.glob("*-template.json"):
            (self.data / t.name.replace("-template", "")).write_text(t.read_text())
        (self.data / "app").mkdir()
        (self.data / "app" / "game.json").write_text("{}")
        (self.data / "app" / "bridge-key.pem").write_text("secret")
        self.remote = root / "remote.git"
        subprocess.run(["git", "init", "-q", "--bare", "-b", "main", str(self.remote)], check=True)
        (self.home / ".config/lani").mkdir(parents=True)
        (self.home / ".config/lani/lani.env").write_text(f"LANI_DATA_DIR={self.data}\nLANI_DATA_REMOTE={self.remote}\n")
        self.phone = root / "phone"
        out = self.repo("init", str(self.data), "-m", "lani-setup: import")
        self.assertEqual(0, out.returncode, out.stderr)
        git(self.data, "remote", "add", "origin", str(self.remote))
        git(self.data, "push", "-q", "origin", "HEAD")

    def tearDown(self):
        self.tmp.cleanup()

    def env(self, **more):
        return sandbox_env(self.home, LANI_LEASE_DEVICE="test-node", **more)

    def repo(self, *args: str, **env) -> subprocess.CompletedProcess:
        return subprocess.run([sys.executable, str(HOOKS / "lani_data_repo.py"), *args], cwd=self.home, capture_output=True,
                              text=True, env=self.env(**env))

    def hook(self, script: str, *args: str, stdin: str = "") -> subprocess.CompletedProcess:
        return subprocess.run([sys.executable, str(HOOKS / script), *args], input=stdin, cwd=self.home, capture_output=True,
                              text=True, env=self.env())

    def report(self, sid="session-001") -> str:
        return json.dumps({"session_id": sid, "date": "2026-10-18", "duration_minutes": 5,
                           "review_results": [{"item_id": "w1", "quality": 4}]})

    def remote_lease(self) -> dict | None:
        p = subprocess.run(["git", "-C", str(self.remote), "show", f"main:{L.FILE}"], capture_output=True, text=True)
        return L.parse(p.stdout) if p.returncode == 0 else None

    def phone_clone(self) -> Path:
        if self.phone.exists():
            git(self.phone, "pull", "-q", "--ff-only")
        else:
            subprocess.run(["git", "clone", "-q", str(self.remote), str(self.phone)], check=True)
        return self.phone

    def phone_writes(self, lease: dict | None = None, file: str | None = None, message="phone: sync"):
        """The app's sync: the phone writes the lease (and a file) and pushes, fast-forward."""
        p = self.phone_clone()
        if lease is not None:
            (p / L.FILE).parent.mkdir(parents=True, exist_ok=True)
            (p / L.FILE).write_text(L.dump(lease))
        if file:
            (p / file).write_text(json.dumps({"written": "on the phone"}))
        git(p, "add", "-A")
        git(p, "commit", "-qm", message)
        git(p, "push", "-q", "origin", "HEAD")

    def handover(self, *more) -> dict:
        out = self.repo("handover", str(self.data), "--json", *more)
        r = json.loads(out.stdout or "{}")
        self.assertEqual(0, out.returncode, out.stdout + out.stderr)
        return r

    def take(self, *more) -> subprocess.CompletedProcess:
        return self.repo("take", str(self.data), "--json", *more)

    def test_handover_pushes_the_offer_and_the_node_stops_writing(self):
        (self.data / "progress-db.json").write_text((self.data / "progress-db.json").read_text().replace("{", "{ ", 1))
        r = self.handover("--device", PHONE["device"], "--name", PHONE["name"])
        self.assertTrue(r["ok"])
        lease = self.remote_lease()
        self.assertEqual("offered", L.state(lease, PHONE))
        self.assertEqual("offered-away", L.state(lease, NODE))
        # the node's last state went with it, and nothing private
        files = subprocess.run(["git", "-C", str(self.remote), "ls-tree", "-r", "--name-only", "main"], capture_output=True,
                               text=True).stdout.split()
        self.assertIn("progress-db.json", files)
        self.assertNotIn("app/bridge-key.pem", files)
        self.assertIn("lease: handed over to the phone (Pixel 8)", git(self.data, "log", "-1", "--format=%s"))
        # update-db.py refuses, and says why
        out = self.hook("update-db.py", stdin=self.report())
        self.assertEqual(2, out.returncode, out.stderr)
        self.assertIn("isn't this node's to write", out.stderr)
        self.assertIn("lani handover --take", out.stderr)
        # card.py's writes too (its reads still work)
        out = self.hook("card.py", "edit", "example_item_id", "--front", "x", "--data", str(self.data))
        self.assertEqual(1, out.returncode, out.stderr)
        self.assertIn("not written", out.stderr)
        self.assertEqual(0, self.hook("card.py", "find", "x", "--data", str(self.data)).returncode)
        # and nothing is committed here meanwhile
        head = git(self.data, "rev-parse", "HEAD")
        (self.data / "app" / "game.json").write_text('{"changed": true}')
        self.assertEqual("", self.repo("autocommit", str(self.data), "-m", "session").stdout.strip())
        self.assertEqual(head, git(self.data, "rev-parse", "HEAD"))
        (self.data / "app" / "game.json").write_text("{}")

    def test_handover_needs_a_remote_and_the_lease(self):
        git(self.data, "remote", "remove", "origin")
        (self.home / ".config/lani/lani.env").write_text(f"LANI_DATA_DIR={self.data}\n")
        out = self.repo("handover", str(self.data), "--json")
        self.assertEqual(1, out.returncode)
        self.assertIn("no remote", json.loads(out.stdout)["error"])
        git(self.data, "remote", "add", "origin", str(self.remote))
        L.write(self.data, L.held(PHONE))
        out = self.repo("handover", str(self.data), "--json")
        self.assertEqual(1, out.returncode)
        self.assertIn("doesn't hold the data", json.loads(out.stdout)["error"])

    def test_the_phone_takes_over_and_hands_back_and_the_node_takes_it_again(self):
        self.handover("--device", PHONE["device"])
        # the phone takes the lease and writes (the app: Lease.take, then its syncs)
        self.phone_writes(L.held(PHONE), "app/notebook.json", "lease: the phone takes the data")
        self.phone_writes(None, "progress-db.json", "phone: a session")
        # the node can't take it while the phone holds it
        out = self.take()
        self.assertEqual(1, out.returncode)
        self.assertIn("the phone (Pixel 8) writes the data", json.loads(out.stdout)["error"])
        # the phone hands it back (the app: "Hand over to the node")
        self.phone_writes(L.offered({"side": "node"}, PHONE), None, "lease: handed over to the node")
        out = self.take()
        self.assertEqual(0, out.returncode, out.stdout + out.stderr)
        # the node has the phone's changes, holds the lease, and the repository knows
        self.assertTrue((self.data / "app" / "notebook.json").exists())
        self.assertIn("on the phone", (self.data / "progress-db.json").read_text())
        self.assertEqual("mine", L.state(L.read(self.data), NODE))
        self.assertEqual("mine", L.state(self.remote_lease(), NODE))
        self.assertEqual("other", L.state(self.remote_lease(), PHONE))
        out = self.hook("update-db.py", stdin=self.report("session-002"))
        self.assertEqual(0, out.returncode, out.stderr)
        # taking it again changes nothing
        out = self.take()
        self.assertEqual(0, out.returncode, out.stdout + out.stderr)
        self.assertTrue(json.loads(out.stdout).get("already"))

    def test_the_node_takes_back_its_own_offer(self):
        self.handover()
        out = self.take()
        self.assertEqual(0, out.returncode, out.stdout + out.stderr)
        self.assertEqual("mine", L.state(self.remote_lease(), NODE))

    def test_a_takeover_without_handover_stops_the_node_at_its_next_push(self):
        # the node writes as usual, then the phone takes the data without a handover (the learner confirmed it)
        self.phone_writes(L.held(PHONE), None, "lease: the phone takes the data (without a handover)")
        self.assertIsNone(L.node_refusal(self.data))  # the node doesn't know yet
        out = self.hook("update-db.py", stdin=self.report())
        self.assertEqual(0, out.returncode, out.stderr)
        # its background push is refused, and it fetched the repository's lease
        note = self.data / ".git" / L.REMOTE_NOTE
        deadline = time.monotonic() + 20
        while not note.exists() and time.monotonic() < deadline:
            time.sleep(0.1)
        self.assertTrue(note.exists(), "the refused push keeps the repository's lease")
        refusal = L.node_refusal(self.data)
        self.assertIn("the phone (Pixel 8) writes the data", refusal)
        out = self.hook("update-db.py", stdin=self.report("session-002"))
        self.assertEqual(2, out.returncode)
        # taking it back now: the histories went apart (the node's session isn't in the repository): kept on a branch
        self.phone_writes(L.offered({"side": "node"}, PHONE), None, "lease: handed over to the node")
        out = self.take()
        self.assertEqual(1, out.returncode)
        error = json.loads(out.stdout)["error"]
        self.assertIn("went apart", error)
        branches = git(self.data, "branch", "--list", "lani/node-*")
        self.assertTrue(branches.strip(), "the node's commits are kept on a branch")

    def test_take_force_takes_it_from_a_lost_phone(self):
        self.handover()
        self.phone_writes(L.held(PHONE), "app/notebook.json")
        self.assertEqual(1, self.take().returncode)
        out = self.take("--force")
        self.assertEqual(0, out.returncode, out.stdout + out.stderr)
        self.assertEqual("mine", L.state(self.remote_lease(), NODE))
        self.assertIn("without a handover", git(self.data, "log", "-1", "--format=%s"))

    def test_take_refuses_to_lose_uncommitted_changes(self):
        self.handover()
        self.phone_writes(L.offered({"side": "node"}, PHONE), "app/notebook.json")
        (self.data / "progress-db.json").write_text("{}")
        out = self.take()
        self.assertEqual(1, out.returncode)
        self.assertIn("progress-db.json", json.loads(out.stdout)["error"])
        self.assertEqual("{}", (self.data / "progress-db.json").read_text())

    def test_the_node_renews_its_lease_with_a_commit_once_a_day(self):
        old = L.held(NODE, datetime.now(timezone.utc) - timedelta(days=2))
        L.write(self.data, old)
        git(self.data, "add", "-A")
        git(self.data, "commit", "-qm", "an old lease")
        (self.data / "app" / "game.json").write_text('{"day": 2}')
        sha = self.repo("commit", str(self.data), "-m", "session").stdout.strip()
        self.assertTrue(sha)
        now = L.read(self.data)
        self.assertEqual(old["since"], now["since"])
        self.assertGreater(now["renewed"], old["renewed"])
        self.assertIn(L.FILE, git(self.data, "show", "--name-only", "--format=", "HEAD").splitlines())
        # nothing else changed: no commit, no renewal
        self.assertEqual("", self.repo("commit", str(self.data), "-m", "nothing").stdout.strip())

    def test_lease_shows_who_writes(self):
        out = self.repo("lease", str(self.data), "--json")
        self.assertEqual(0, out.returncode)
        self.assertEqual("mine", json.loads(out.stdout)["state"])
        self.handover()
        self.assertIn("handed over to the phone", self.repo("lease", str(self.data)).stdout)

    def test_lani_handover_shows_the_town_s_secrets_and_takes_them_back(self):
        """companion/bin/lani-handover end to end: the handover pushed, then the QR code's text (--no-qr: as text), which
        the bridge's own decoder opens: the town key's scalar, the family token, the profile; --status and --take."""
        (self.data / "app" / "bridge-key.pem").unlink()  # the CLI makes a real one, as a bridge does on first use
        (self.data / "app" / "family-token").write_text("fam-secret-123\n")
        cli = [str(REPO / "companion/bin/lani-handover")]
        run = lambda *a: subprocess.run(cli + list(a), cwd=self.home, capture_output=True, text=True, env=self.env(), timeout=120)
        out = run("--yes", "--no-qr", "--device", PHONE["device"])
        self.assertEqual(0, out.returncode, out.stdout + out.stderr)
        uri = next(line for line in out.stdout.splitlines() if line.startswith("lani://handover?"))
        self.assertIn("&k=", uri)
        self.assertEqual("offered", L.state(self.remote_lease(), PHONE))
        script = ("import { decodeHandover } from './src/handover'; import { loadTownKey, fingerprintOf } from './src/pairing';"
                  f"const r = decodeHandover({json.dumps(uri)}); const k = loadTownKey({json.dumps(str(self.data / 'app'))});"
                  "console.log(JSON.stringify({ b: r.bundle, f: r.fingerprint, mine: k.fingerprint, d: String(k.privateKey.export({ format: 'jwk' }).d) }))")
        dec = subprocess.run(["bun", "-e", script], cwd=REPO / "companion/bridge", capture_output=True, text=True, env=self.env(), timeout=60)
        self.assertEqual(0, dec.returncode, dec.stderr)
        got = json.loads(dec.stdout)
        self.assertEqual(got["mine"], got["f"])
        self.assertEqual(got["d"], got["b"]["k"])
        self.assertEqual("fam-secret-123", got["b"]["t"])
        self.assertEqual("default", got["b"]["p"])
        self.assertNotIn("fam-secret-123", out.stdout)  # never in clear text on the screen
        self.assertIn("handed over to the phone", run("--status").stdout)
        took = run("--take")
        self.assertEqual(0, took.returncode, took.stdout + took.stderr)
        self.assertEqual("mine", L.state(self.remote_lease(), NODE))
        # with --code the key isn't in the code: a salt, and the code printed apart
        out = run("--yes", "--no-qr", "--code")
        self.assertEqual(0, out.returncode, out.stdout + out.stderr)
        uri = next(line for line in out.stdout.splitlines() if line.startswith("lani://handover?"))
        self.assertNotIn("&k=", uri)
        self.assertIn("&s=", uri)
        self.assertRegex(out.stdout, r"The code to type on the phone: [A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}")

    def test_ignore_lets_the_node_write_in_an_emergency(self):
        self.handover()
        out = subprocess.run([sys.executable, str(HOOKS / "update-db.py")], input=self.report(), cwd=self.home,
                             capture_output=True, text=True, env=self.env(LANI_LEASE="ignore"))
        self.assertEqual(0, out.returncode, out.stderr)


if __name__ == "__main__":
    unittest.main()
