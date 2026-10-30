"""Tests for lani export and lani import (.claude/hooks/lani_export.py, companion/bin/lani-export, lani-import): the
format lani.export/v1, what an export holds and leaves out, the round trip, the secrets, a data repository's two
commits, and the refusals (nothing changes). Synthetic data only, in a temporary HOME.
Run: python3 -m unittest discover -s companion/tests"""
from __future__ import annotations

import contextlib
import hashlib
import io
import json
import os
import re
import socket
import stat
import subprocess
import sys
import tempfile
import types
import unittest
import unittest.mock
import zipfile
from pathlib import Path

sys.dont_write_bytecode = True
os.environ["LANI_ENV_FILE"] = os.devnull  # this process never reads the machine's own lani.env

REPO = Path(__file__).resolve().parents[2]
HOOKS = REPO / ".claude" / "hooks"
BIN = REPO / "companion" / "bin"
TEMPLATES = REPO / "data-examples"
sys.path.insert(0, str(HOOKS))
import lani_export as lx  # noqa: E402

MANIFEST = "lani-export.json"


def sha(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


def tree(d: Path) -> dict:
    """Every file under [d] but .git/, with its content's hash and mode."""
    out = {}
    for p in sorted(Path(d).rglob("*")):
        rel = p.relative_to(d).as_posix()
        if p.is_file() and ".git" not in rel.split("/"):
            out[rel] = (sha(p.read_bytes()), stat.S_IMODE(p.stat().st_mode))
    return out


def make_data(d: Path, name: str = "Ana", pem: str = "KEY", token: str = "tok") -> Path:
    """A learner's data directory: the six databases from data-examples, the app's files, the files an export leaves
    out (a token, a key, the recent answers, a voice clip), a session report and another language."""
    d.mkdir(parents=True, exist_ok=True)
    for t in TEMPLATES.glob("*-template.json"):
        (d / t.name.replace("-template", "")).write_text(t.read_text(encoding="utf-8"), encoding="utf-8")
    profile = json.loads((d / "learner-profile.json").read_text(encoding="utf-8"))
    profile["learner"].update(name=name, target_language="Slovene", target_language_code="sl", base_language="English",
                              base_language_code="en")
    profile["home_language"] = "sl"
    profile["languages"] = [{"code": "it", "name": "Italian", "level": "A1", "source": "visits", "since": "2026-10-01"}]
    (d / "learner-profile.json").write_text(json.dumps(profile, indent=2), encoding="utf-8")
    for sub in ("app/voice", "results", "languages/it"):
        (d / sub).mkdir(parents=True, exist_ok=True)
    (d / "app/game.json").write_text(json.dumps({"rev": 3, "state": {"schema": 1}}))
    (d / "app/towns.json").write_text(json.dumps({"schema": "lani.towns/v1", "towns": []}))
    (d / "app/bridge-token").write_text(token)
    (d / "app/bridge-key.pem").write_text(pem)
    (d / "app/answers.json").write_text("[]")
    (d / "app/voice/x.mp3").write_bytes(b"\xff" * 300)
    (d / "app/game.json.tmp").write_text("{")
    (d / "results/session-1.md").write_text("# one\n")
    (d / "languages/it/learner-profile.json").write_text(
        json.dumps({"learner": {"target_language": "Italian", "target_language_code": "it"}}))
    return d


class ExportTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name).resolve()
        self.home = self.root / "home"
        self.home.mkdir()
        isolated = {"HOME": str(self.home), "XDG_CONFIG_HOME": str(self.home / ".config"),
                    "XDG_CACHE_HOME": str(self.home / ".cache"), "XDG_DATA_HOME": str(self.home / ".local/share"),
                    "XDG_STATE_HOME": str(self.home / ".local/state"), "LANI_ENV_FILE": os.devnull,
                    # the learners on this machine: none (never the checkout's own profiles/), unless a test lists some
                    "LANI_PROFILES_DIR": str(self.root / "no-profiles")}
        self.env = {k: v for k, v in os.environ.items() if not k.startswith(("LANI_", "FLUENT_", "XDG_", "CLAUDE_", "GIT_"))}
        self.env.update(isolated, LANI_NOW="2026-10-19T10:15:00", PYTHONDONTWRITEBYTECODE="1",
                        PATH=os.pathsep.join([str(Path(sys.executable).parent), os.environ.get("PATH", "")]))
        self.saved = {k: os.environ.get(k) for k in [*isolated, "LANI_DATA_DIR"]}
        os.environ.update(isolated)  # the in-process calls too (read-db.py runs with this environment)
        os.environ.pop("LANI_DATA_DIR", None)
        self.a = make_data(self.root / "a")
        self.zip = self.root / "a.zip"

    def tearDown(self):
        for k, v in self.saved.items():
            if v is None:
                os.environ.pop(k, None)
            else:
                os.environ[k] = v
        self.tmp.cleanup()

    # --- helpers ---

    def run_cmd(self, *args, cmd=None, stdin=subprocess.DEVNULL, **env) -> subprocess.CompletedProcess:
        argv = cmd or [sys.executable, str(HOOKS / "lani_export.py")]
        return subprocess.run([*argv, *map(str, args)], cwd=self.root, env={**self.env, **env}, stdin=stdin,
                              capture_output=True, text=True, timeout=120)

    def export(self, data=None, out=None, *more) -> subprocess.CompletedProcess:
        r = self.run_cmd("export", "--data", data or self.a, "--out", out or self.zip, *more)
        self.assertEqual(0, r.returncode, r.stderr)
        return r

    def manifest(self, z=None) -> dict:
        with zipfile.ZipFile(z or self.zip) as zf:
            return json.loads(zf.read(MANIFEST))

    def names(self, z=None) -> list:
        with zipfile.ZipFile(z or self.zip) as zf:
            return zf.namelist()

    def git(self, d: Path, *args) -> str:
        env = {**self.env, "GIT_CONFIG_NOSYSTEM": "1"}
        return subprocess.run(["git", "-C", str(d), *args], env=env, capture_output=True, text=True, check=True).stdout.strip()

    def init_repo(self, d: Path) -> None:
        r = subprocess.run([sys.executable, str(HOOKS / "lani_data_repo.py"), "init", str(d)], cwd=self.root,
                           env=self.env, capture_output=True, text=True)
        self.assertEqual(0, r.returncode, r.stderr)

    def rezip(self, dst: Path, put=None, drop=(), change=None, relist=True) -> Path:
        """A copy of the good export with entries put (bytes, or (bytes, mode) for the zip's mode bits) or dropped, the
        manifest's files listed again from the entries ([relist]) and then changed by [change]."""
        with zipfile.ZipFile(self.zip) as zf:
            entries = {n: zf.read(n) for n in zf.namelist()}
        m = json.loads(entries.pop(MANIFEST))
        for n in drop:
            entries.pop(n, None)
        modes = {}
        for n, v in (put or {}).items():
            entries[n], modes[n] = (v if isinstance(v, tuple) else (v, None))
        if relist:
            m["files"] = [{"path": n, "size": len(b), "sha256": sha(b)} for n, b in sorted(entries.items())]
        if change:
            change(m)
        with zipfile.ZipFile(dst, "w") as zf:
            for n, b in entries.items():
                zi = zipfile.ZipInfo(n)
                if modes.get(n):
                    zi.external_attr = modes[n] << 16
                zf.writestr(zi, b)
            zf.writestr(MANIFEST, json.dumps(m))
        return dst

    # --- what an export holds ---

    def test_the_manifest(self):
        self.export()
        m = self.manifest()
        self.assertEqual("lani.export/v1", m["format"])
        self.assertRegex(m["created"], r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")
        self.assertEqual({"side": "node", "mode": "self-hosted", "name": socket.gethostname(), "profile": "default"},
                         {k: m["from"][k] for k in ("side", "mode", "name", "profile")})
        self.assertIn("version", m["from"])
        self.assertEqual({"name": "Ana", "target": "sl", "base": "en"}, m["learner"])
        self.assertEqual({"app/towns.json": "lani.towns/v1", "app/game.json": 1}, m["schemas"])
        self.assertEqual([], m["secrets"])
        paths = [f["path"] for f in m["files"]]
        self.assertEqual(sorted(paths), paths)
        self.assertEqual(sorted(paths + [MANIFEST]), sorted(self.names()))
        for f in m["files"]:
            raw = (self.a / f["path"]).read_bytes()
            self.assertEqual((len(raw), sha(raw)), (f["size"], f["sha256"]), f["path"])
        self.assertEqual(0o600, stat.S_IMODE(self.zip.stat().st_mode))

    def test_what_stays_out(self):
        (self.a / "app/family-token").write_text("fam")
        (self.a / "app/dev").mkdir()
        (self.a / "app/dev/app-log.txt").write_text("log")
        (self.a / MANIFEST).write_text("{}")
        self.export()
        names = self.names()
        for kept in ("learner-profile.json", "session-log.json", "app/game.json", "app/towns.json",
                     "results/session-1.md", "languages/it/learner-profile.json"):
            self.assertIn(kept, names)
        for out in ("app/bridge-token", "app/bridge-key.pem", "app/family-token", "app/answers.json", "app/voice/x.mp3",
                    "app/game.json.tmp", "app/dev/app-log.txt"):
            self.assertNotIn(out, names)
        self.assertEqual(1, names.count(MANIFEST))  # the data directory's own lani-export.json is left out

    def test_a_gitignore_of_the_learners_own_never_lets_secrets_or_private_files_in(self):
        (self.a / ".gitignore").write_text("# mine\n/app/voice/\n")
        self.export()
        names = self.names()
        self.assertIn(".gitignore", names)
        self.assertIn("app/game.json.tmp", names)  # their rules, not lani-setup's
        for out in ("app/bridge-token", "app/bridge-key.pem", "app/answers.json", "app/voice/x.mp3"):
            self.assertNotIn(out, names)

    def test_the_gitignore_rules(self):
        rules = lx.parse_rules(lx.lani_data_repo.GITIGNORE)
        for out in ("app/bridge-token", "app/voice/files/a.mp3", "app/answers.json.tmp-0-000000", "x/y.pem",
                    "learner-profile.json.backup-20261005-101010", "app/__pycache__/m.pyc", ".backups/old.json"):
            self.assertTrue(lx.ignored(rules, out), out)
        for kept in ("app/game.json", "sub/app/bridge-token", "results/session-1.md", "app/voices.json"):
            self.assertFalse(lx.ignored(rules, kept), kept)
        rules = lx.parse_rules("*.json\n!keep.json\nd/\n!d/x\n**/deep\na/**\n")
        self.assertFalse(lx.ignored(rules, "keep.json"))
        self.assertTrue(lx.ignored(rules, "sub/other.json"))
        self.assertTrue(lx.ignored(rules, "d/x"))  # a file in a folder that is out can't be taken back in
        self.assertTrue(lx.ignored(rules, "q/r/deep"))
        self.assertTrue(lx.ignored(rules, "a/b/c"))
        self.assertFalse(lx.ignored(rules, "b/a"))

    def test_the_default_name_and_no_overwrite(self):
        r = self.run_cmd("export", "--data", self.a)
        self.assertEqual(0, r.returncode, r.stderr)
        out = self.root / "lani-export-ana-2026-10-19.zip"
        self.assertTrue(out.is_file())
        self.assertIn("lani-export-ana-2026-10-19.zip", r.stdout)
        self.assertRegex(r.stdout, r"\d+ files")
        r = self.run_cmd("export", "--data", self.a)
        self.assertEqual(1, r.returncode)
        self.assertIn("exists already", r.stderr)
        self.assertEqual(0, self.run_cmd("export", "--data", self.a, "--force").returncode)
        self.assertFalse(list(self.root.glob(".lani-export-*")))

    def test_another_learner_by_profile(self):
        reg = self.root / "profiles"
        make_data(reg / "luka/data", name="Luka")
        (reg / "profiles.json").write_text(json.dumps({"schema": "lani.profiles/v1", "profiles": [
            {"id": "luka", "name": "Luka", "data_dir": "luka/data"}]}))
        r = self.run_cmd("export", "--profile", "luka", "--out", self.zip, LANI_PROFILES_DIR=reg)
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertEqual(("Luka", "luka"), (self.manifest()["learner"]["name"], self.manifest()["from"]["profile"]))
        r = self.run_cmd("export", "--profile", "nobody", "--out", self.root / "n.zip", LANI_PROFILES_DIR=reg)
        self.assertEqual(1, r.returncode)
        self.assertIn("no learner \"nobody\"", r.stderr)

    def test_a_childs_export_says_so_and_makes_the_profile_a_childs(self):
        """A child's profile (lani profile add --child) exports with "child": true in its learner, as the hosted phone's
        "for a child" does; importing it makes the profile it goes into a child's, never the other way round."""
        reg = self.root / "profiles"
        make_data(reg / "teo/data", name="Teo")
        make_data(reg / "luka/data", name="Luka")
        (reg / "profiles.json").write_text(json.dumps({"schema": "lani.profiles/v1", "profiles": [
            {"id": "teo", "name": "Teo", "child": True, "data_dir": "teo/data"},
            {"id": "luka", "name": "Luka", "child": False, "data_dir": "luka/data"}]}, indent=2))
        teo, luka = self.root / "teo.zip", self.root / "luka.zip"
        for pid, z in (("teo", teo), ("luka", luka)):
            r = self.run_cmd("export", "--profile", pid, "--out", z, LANI_PROFILES_DIR=reg)
            self.assertEqual(0, r.returncode, r.stderr)
        self.assertIs(True, self.manifest(teo)["learner"]["child"])
        self.assertNotIn("child", self.manifest(luka)["learner"])
        # by its data directory too (--data, as lani-backup finds a learner)
        r = self.run_cmd("export", "--data", reg / "teo/data", "--out", self.root / "t2.zip", LANI_PROFILES_DIR=reg)
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIs(True, self.manifest(self.root / "t2.zip")["learner"]["child"])
        r = self.run_cmd("verify", teo, LANI_PROFILES_DIR=reg)
        self.assertIn("Teo (sl, from en), a child", r.stdout)
        # the dry run says what it would do, and does nothing
        r = self.run_cmd("import", teo, "--profile", "luka", "--dry-run", LANI_PROFILES_DIR=reg)
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn("the profile luka becomes a child's", r.stdout)
        self.assertIs(False, json.loads((reg / "profiles.json").read_text())["profiles"][1]["child"])
        r = self.run_cmd("import", teo, "--profile", "luka", "--yes", LANI_PROFILES_DIR=reg)
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn("luka is a child's profile now", r.stdout)
        doc = json.loads((reg / "profiles.json").read_text())
        self.assertEqual([True, True], [e["child"] for e in doc["profiles"]])
        self.assertEqual(["teo", "luka"], [e["id"] for e in doc["profiles"]])
        self.assertFalse(list(reg.glob("*.tmp")))
        # an adult's export into a child's profile: still a child's
        r = self.run_cmd("import", luka, "--profile", "teo", "--yes", LANI_PROFILES_DIR=reg)
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertNotIn("child", r.stdout)
        self.assertIs(True, json.loads((reg / "profiles.json").read_text())["profiles"][0]["child"])
        # into data no profile has: said, nothing marked
        r = self.run_cmd("import", teo, "--data", self.root / "loose", "--yes", LANI_PROFILES_DIR=reg)
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn("no learner here has this data directory", r.stdout)
        # the default learner's data: LANI_CHILD in lani.env is theirs
        b = make_data(self.root / "b", name="Bine")
        r = self.run_cmd("import", teo, "--yes", LANI_PROFILES_DIR=reg, LANI_DATA_DIR=b)
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn("set LANI_CHILD=1 in lani.env", r.stdout)
        r = self.run_cmd("export", "--out", self.root / "b.zip", LANI_PROFILES_DIR=reg, LANI_DATA_DIR=b, LANI_CHILD="1")
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIs(True, self.manifest(self.root / "b.zip")["learner"]["child"])

    def test_data_that_would_not_import_is_not_exported(self):
        (self.a / "mistakes-db.json").write_text("{broken")
        r = self.run_cmd("export", "--data", self.a, "--out", self.zip)
        self.assertEqual(1, r.returncode)
        self.assertIn("mistakes-db.json isn't valid JSON", r.stderr)
        self.assertFalse(self.zip.exists())
        self.assertFalse(list(self.root.glob(".lani-export-*")))

    # --- import ---

    def test_round_trip(self):
        self.export()
        b = make_data(self.root / "b", name="Bine", pem="KEY-B", token="tok-b")
        (b / "app/voice/y.mp3").write_bytes(b"b" * 10)
        (b / "app/packs/old").mkdir(parents=True)
        (b / "app/packs/old/p.json").write_text('{"old": true}')
        (b / "progress-db.json").write_text('{"b": 1}')
        r = self.run_cmd("import", self.zip, "--data", b, "--yes")
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn("Imported", r.stdout)
        self.assertIn("lani bridge restart", r.stdout)
        m = self.manifest()
        for f in m["files"]:
            self.assertEqual(f["sha256"], sha((b / f["path"]).read_bytes()), f["path"])
            self.assertEqual((self.a / f["path"]).read_bytes(), (b / f["path"]).read_bytes())
        # what an export never holds stays as it was here
        self.assertEqual("tok-b", (b / "app/bridge-token").read_text())
        self.assertEqual("KEY-B", (b / "app/bridge-key.pem").read_text())
        self.assertEqual(b"b" * 10, (b / "app/voice/y.mp3").read_bytes())
        # removed: what an export of b held that this one doesn't, and the folders it leaves empty
        self.assertFalse((b / "app/packs").exists())
        backups = list(self.root.glob("b.before-import-*"))
        self.assertEqual(1, len(backups))
        self.assertEqual('{"old": true}', (backups[0] / "app/packs/old/p.json").read_text())
        self.assertEqual('{"b": 1}', (backups[0] / "progress-db.json").read_text())
        self.assertEqual({"a.zip", "a", "b", backups[0].name, "home"}, {p.name for p in self.root.iterdir()})
        self.assertFalse(list(self.root.glob(".b.lani-import-*")))
        rd = subprocess.run([sys.executable, str(HOOKS / "read-db.py")], env={**self.env, "LANI_DATA_DIR": str(b)},
                            capture_output=True, text=True)
        self.assertEqual(0, rd.returncode, rd.stderr)
        self.assertEqual("Ana", json.loads(rd.stdout)["databases"]["learner_profile"]["learner"]["name"])

    def test_into_a_new_directory(self):
        self.export()
        c = self.root / "new/c"
        r = self.run_cmd("import", self.zip, "--data", c, "--yes")
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertEqual(sorted(f["path"] for f in self.manifest()["files"]), sorted(tree(c)))

    def test_the_lease_and_the_gitignore_stay_this_sides_own(self):
        (self.a / "app/lease.json").write_text(json.dumps({"schema": "lani.lease/v1", "holder": {"side": "phone"}}))
        (self.a / ".gitignore").write_text(lx.lani_data_repo.GITIGNORE.replace("*.pem\n", ""))
        self.export()
        self.assertIn("app/lease.json", self.names())
        b = make_data(self.root / "b")
        (b / ".gitignore").write_text(lx.lani_data_repo.GITIGNORE)
        r = self.run_cmd("import", self.zip, "--data", b, "--yes")
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn("Kept as they are here", r.stdout)
        self.assertFalse((b / "app/lease.json").exists())
        self.assertEqual(lx.lani_data_repo.GITIGNORE, (b / ".gitignore").read_text())

    def test_dry_run_changes_nothing(self):
        self.export()
        b = make_data(self.root / "b", name="Bine")
        (b / "app/old.json").write_text("{}")
        before = tree(b)
        r = self.run_cmd("import", self.zip, "--data", b, "--dry-run")
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn("1 removed", r.stdout)
        self.assertIn("- app/old.json", r.stdout)
        self.assertIn("Nothing written", r.stdout)
        self.assertEqual(before, tree(b))
        self.assertEqual({"a", "a.zip", "b", "home"}, {p.name for p in self.root.iterdir()})

    def test_it_asks_and_never_goes_on_without_an_answer(self):
        self.export()
        b = make_data(self.root / "b", name="Bine")
        before = tree(b)
        r = self.run_cmd("import", self.zip, "--data", b)  # no terminal, no --yes
        self.assertEqual(1, r.returncode)
        self.assertIn("--yes", r.stderr)
        self.assertEqual(before, tree(b))
        asked = []
        with unittest.mock.patch.object(sys, "stdin", types.SimpleNamespace(isatty=lambda: True)), \
                contextlib.redirect_stdout(io.StringIO()):
            code = lx.import_file(self.zip, b, ask=lambda q: asked.append(q) or "n")
        self.assertEqual(1, code)
        self.assertEqual(["Replace Bine's data with this export? [y/N] "], asked)
        self.assertEqual(before, tree(b))

    def test_with_secrets(self):
        (self.a / "app/family-token").write_text("fam")
        r = self.export(None, None, "--with-secrets")
        self.assertIn("private", r.stderr)
        m = self.manifest()
        self.assertEqual(["app/family-token", "app/town-key.pem"], sorted(m["secrets"]))
        names = self.names()
        self.assertNotIn("app/bridge-key.pem", names)
        with zipfile.ZipFile(self.zip) as zf:
            self.assertEqual(b"KEY", zf.read("app/town-key.pem"))  # the bridge key is the town's identity
        b = make_data(self.root / "b", pem="KEY-B")
        r = self.run_cmd("import", self.zip, "--data", b, "--yes")
        self.assertEqual(0, r.returncode, r.stderr)
        for p, content in (("app/town-key.pem", "KEY"), ("app/family-token", "fam")):
            self.assertEqual(content, (b / p).read_text())
            self.assertEqual(0o600, stat.S_IMODE((b / p).stat().st_mode), p)
        self.assertEqual("KEY-B", (b / "app/bridge-key.pem").read_text())  # the node's own identity stays
        (self.a / "app/town-key.pem").write_text("TOWN")
        self.export(None, self.root / "t.zip", "--with-secrets")
        with zipfile.ZipFile(self.root / "t.zip") as zf:
            self.assertEqual(b"TOWN", zf.read("app/town-key.pem"))

    def test_a_data_repository(self):
        self.init_repo(self.a)
        with open(self.a / ".gitignore", "a") as f:
            f.write("/notes/\n")
        (self.a / "notes").mkdir()
        (self.a / "notes/x.txt").write_text("mine")
        (self.a / "app/new.json").write_text("{}")  # not committed yet: what the next commit takes
        self.export()
        names = self.names()
        self.assertIn("app/new.json", names)
        self.assertIn(".gitignore", names)
        self.assertNotIn("notes/x.txt", names)  # git decides
        self.assertFalse([n for n in names if n.startswith(".git/")])
        b = make_data(self.root / "b", name="Bine")
        self.init_repo(b)
        (b / "progress-db.json").write_text('{"before": "the import"}')
        r = self.run_cmd("import", self.zip, "--data", b, "--yes")
        self.assertEqual(0, r.returncode, r.stderr)
        log = self.git(b, "log", "--format=%s").splitlines()
        self.assertEqual("lani import: the data before the import", log[1])
        self.assertRegex(log[0], rf"^lani import: a\.zip \(node {re.escape(socket.gethostname())}, \d{{4}}-\d\d-\d\d \d\d:\d\d:\d\d UTC\)$")
        self.assertEqual('{"before": "the import"}', self.git(b, "show", "HEAD~1:progress-db.json"))
        self.assertEqual("", self.git(b, "status", "--porcelain"))
        self.assertEqual((self.a / "progress-db.json").read_text(), (b / "progress-db.json").read_text())
        self.assertNotIn("app/bridge-token", self.git(b, "ls-files").splitlines())
        self.assertFalse(list(self.root.glob("b.before-import-*")))  # the history keeps it

    def test_the_lease_refuses(self):
        self.export()
        b = make_data(self.root / "b", name="Bine")
        lease = lx.lani_lease
        lease.write(b, lease.held({"side": "phone", "device": "pixel-8", "name": "Pixel 8", "mode": "hosted"}))
        before = tree(b)
        r = self.run_cmd("import", self.zip, "--data", b, "--yes")
        self.assertEqual(1, r.returncode, r.stdout)
        self.assertIn("isn't this node's to write now", r.stderr)
        self.assertIn("the phone (Pixel 8)", r.stderr)
        self.assertEqual(before, tree(b))
        r = self.run_cmd("import", self.zip, "--data", b, "--yes", LANI_LEASE="ignore")  # for an emergency
        self.assertEqual(0, r.returncode, r.stderr)

    # --- refusals ---

    def test_refusals(self):
        self.export()
        b = make_data(self.root / "b", name="Bine")
        before = tree(b)
        first = lambda m: m["files"][0]  # noqa: E731
        cases = {
            "not a zip": (lambda p: (p.write_bytes(b"hello"), p)[1], "not a zip file"),
            "no manifest": (lambda p: _without_manifest(self.rezip(p)), "no lani-export.json"),
            "v2": (lambda p: self.rezip(p, change=lambda m: m.update(format="lani.export/v2")), "newer Lani"),
            "another format": (lambda p: self.rezip(p, change=lambda m: m.update(format="zip/v1")), "not a Lani export"),
            "../x": (lambda p: self.rezip(p, put={"../x": b"x"}), "not a path inside the data"),
            "/abs": (lambda p: self.rezip(p, put={"/etc/x": b"x"}), "not a path inside the data"),
            ".git": (lambda p: self.rezip(p, put={".git/config": b"x"}), "not a path inside the data"),
            "symlink": (lambda p: self.rezip(p, put={"app/link": (b"/etc/passwd", stat.S_IFLNK | 0o777)}), "symbolic link"),
            "tampered sha": (lambda p: self.rezip(p, change=lambda m: first(m).update(sha256="0" * 64)), "SHA-256"),
            "wrong size": (lambda p: self.rezip(p, change=lambda m: first(m).update(size=first(m)["size"] + 1)), "size"),
            "unlisted entry": (lambda p: self.rezip(p, put={"app/extra.json": b"{}"}, relist=False), "not in its manifest"),
            "listed, not there": (lambda p: self.rezip(p, change=lambda m: m["files"].append(
                {"path": "zz.json", "size": 2, "sha256": sha(b"{}")})), "not in the file"),
            "broken database": (lambda p: self.rezip(p, put={"progress-db.json": b"{broken"}), "progress-db.json isn't valid JSON"),
            "private file": (lambda p: self.rezip(p, put={"app/answers.json": b"[]"}), "never in an export"),
            "secret": (lambda p: self.rezip(p, put={"app/bridge-token": b"tok"}), "never in an export"),
            "unnamed secret": (lambda p: self.rezip(p, put={"app/town-key.pem": b"K"}), "never in an export"),
            "no profile": (lambda p: self.rezip(p, drop=["learner-profile.json"]), "no learner-profile.json"),
            "read-db": (lambda p: self.rezip(p, drop=["session-log.json"]), "read-db.py"),
        }
        for i, (name, (make, why)) in enumerate(cases.items()):
            with self.subTest(name):
                bad = make(self.root / f"bad-{i}.zip")
                v = self.run_cmd("verify", bad)
                self.assertEqual(1, v.returncode, v.stdout)
                self.assertIn(why, v.stderr)
                self.assertEqual(1, len(v.stderr.strip().splitlines()), v.stderr)
                r = self.run_cmd("import", bad, "--data", b, "--yes")
                self.assertEqual(1, r.returncode, r.stdout)
                self.assertIn(why, r.stderr)
                self.assertEqual(before, tree(b))
                self.assertFalse(list(self.root.glob(".b.lani-import-*")))
                self.assertFalse(list(self.root.glob("b.before-import-*")))

    def test_verify_says_what_it_holds(self):
        self.export()
        r = self.run_cmd("--verify", self.zip, cmd=[sys.executable, str(BIN / "lani-import")])
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn("Ana (sl, from en)", r.stdout)
        self.assertIn(f"the node {socket.gethostname()} (self-hosted)", r.stdout)
        self.assertIn("app/towns.json lani.towns/v1", r.stdout)
        self.assertIn(f"files:    {len(self.manifest()['files'])} ", r.stdout)

    # --- the command line ---

    def test_the_command_line(self):
        for script, flag in (("lani-export", "--with-secrets"), ("lani-import", "--dry-run")):
            r = self.run_cmd("--help", cmd=[str(BIN / script)])
            self.assertEqual(0, r.returncode, r.stderr)
            self.assertIn(flag, r.stdout)
        r = self.run_cmd("export", "--data", self.a, "--out", self.zip, cmd=[str(BIN / "lani")])
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertTrue(self.zip.is_file())
        r = self.run_cmd("import", "--verify", self.zip, cmd=[str(BIN / "lani")])
        self.assertEqual(0, r.returncode, r.stderr)
        r = self.run_cmd("help", cmd=[str(BIN / "lani")])
        self.assertIn("lani export", r.stdout)
        self.assertIn("lani import", r.stdout)
        self.assertIn("A link to this script", r.stdout)
        self.assertEqual(2, self.run_cmd("import", cmd=[str(BIN / "lani")]).returncode)  # no FILE
        self.assertEqual(2, self.run_cmd("export", "--data", self.a, "--profile", "x", cmd=[str(BIN / "lani")]).returncode)


def _without_manifest(p: Path) -> Path:
    with zipfile.ZipFile(p) as zf:
        entries = {n: zf.read(n) for n in zf.namelist() if n != MANIFEST}
    with zipfile.ZipFile(p, "w") as zf:
        for n, b in entries.items():
            zf.writestr(n, b)
    return p


if __name__ == "__main__":
    unittest.main()
