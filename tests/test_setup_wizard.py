"""companion/bin/lani-setup without a terminal (--yes), end to end in a temporary HOME: a fresh learner, an import of an
earlier install (the learner files copied, the tokens kept and out of git, the voice clips and APKs moved to their
caches), the data repository and its remote, keys.env, the network and the service files, a second run that changes
only what it is told, and a session committed and pushed afterwards. claude, tailscale, gh, systemctl, loginctl and
launchctl are fakes that log what they were asked; remotes are local bare repositories; no port of a real bridge is
asked (LANI_BRIDGE_PORT is a free one, the workers' URLs dead ones).
Run: python3 -m unittest discover -s tests"""
import json
import os
import socket
import sqlite3
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

os.environ["LANI_ENV_FILE"] = os.devnull  # this process never reads the machine's own lani.env

REPO = Path(__file__).resolve().parents[1]
SETUP = REPO / "companion" / "bin" / "lani-setup"
sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_config import sandbox_env  # noqa: E402

FAKES = {
    "claude": 'echo "2.1.99 (Claude Code)"',
    "tailscale": """case "$1 $2" in
  "status --json") echo '{"BackendState":"Running","Self":{"DNSName":"node.tail.ts.net."}}' ;;
  "serve status") echo '{}' ;;
  *) echo "ok" ;;
esac""",
    "gh": """case "$1 $2" in
  "auth status") exit 0 ;;
  "api user") echo tester ;;
  "repo view") if [ -n "$FAKE_GH_VISIBILITY" ]; then echo "$FAKE_GH_VISIBILITY"; else exit 1; fi ;;
  "repo create") echo "https://github.com/$3" ;;
  "config get") echo https ;;
  *) echo "gh 2.80.0" ;;
esac""",
    "systemctl": "exit 0",
    "loginctl": '[ "$1" = show-user ] && echo no; exit 0',
    "launchctl": "exit 0",
}


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class SetupWizardTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name).resolve()
        self.home = self.root / "home"
        self.home.mkdir()
        self.fakes = self.root / "fakebin"
        self.fakes.mkdir()
        self.calls = self.root / "calls.log"
        for name, body in FAKES.items():
            f = self.fakes / name
            f.write_text(f'#!/bin/sh\necho "{name} $*" >> "{self.calls}"\n{body}\n')
            f.chmod(0o755)
        self.port = free_port()
        self.lani_env = self.home / ".config" / "lani" / "lani.env"

    def tearDown(self):
        self.tmp.cleanup()

    def setup_(self, *args: str, stdin: str = "", **env) -> subprocess.CompletedProcess:
        e = sandbox_env(self.home, LANI_BRIDGE_PORT=self.port, LANI_GEPARD_URL="http://127.0.0.1:9", LANI_STT_URL="http://127.0.0.1:9", **env)
        e["PATH"] = f"{self.fakes}{os.pathsep}{e['PATH']}"
        e["TMUX_TMPDIR"] = str(self.root)
        return subprocess.run([str(SETUP), "--yes", *args], input=stdin, cwd=self.home, env=e, capture_output=True, text=True, timeout=180)

    def ok(self, *args: str, **env) -> str:
        out = self.setup_(*args, **env)
        self.assertEqual(0, out.returncode, out.stdout + out.stderr)
        return out.stdout

    def settings(self) -> dict:
        sys.path.insert(0, str(REPO / ".claude" / "hooks"))
        import lani_paths
        return lani_paths.parse_env_file(self.lani_env.read_text(), self.home)

    def git(self, cwd: Path, *args: str) -> str:
        return subprocess.run(["git", "-C", str(cwd), *args], capture_output=True, text=True, check=True).stdout.strip()

    def old_install(self) -> Path:
        """An earlier install: a Fluent checkout's data/ (tokens, a voice store, APKs) with results/ beside it."""
        old = self.root / "ai-slo"
        data = old / "data"
        (data / "app" / "voice" / "files").mkdir(parents=True)
        (data / "app" / "release").mkdir(parents=True)
        (old / "results").mkdir()
        for t in (REPO / "data-examples").glob("*-template.json"):
            (data / t.name.replace("-template", "")).write_text(t.read_text())
        profile = json.loads((data / "learner-profile.json").read_text())
        profile["learner"].update(name="Jan", target_language="Slovene", target_language_code="sl", base_language="English",
                                  base_language_code="en", native_language="German", current_level="A2", daily_goal_minutes=30)
        (data / "learner-profile.json").write_text(json.dumps(profile, indent=2))
        for name in ("bridge-token", "family-token", "bridge-key.pem"):
            (data / "app" / name).write_text(f"secret {name}\n")
            (data / "app" / name).chmod(0o600)
        (data / "app" / "game.json").write_text('{"village": "Moja vas"}')
        (data / "app" / "devices.json").write_text('{"devices": []}')
        db = sqlite3.connect(data / "app" / "voice" / "voice.db")
        db.execute("CREATE TABLE clips (key TEXT, file TEXT)")
        db.commit()
        db.close()  # (a `with` would keep it open: the import would find this test holding the old voice store)
        (data / "app" / "voice" / "files" / "abc.mp3").write_bytes(b"\xff" * 5000)
        (data / "app" / "release" / "fluent-629.apk").write_text("apk")
        (data / "app" / "release" / "lani-1042.apk").write_text("apk")
        (data / "app" / "release" / "latest.json").write_text('{"versionCode": 1042, "file": "lani-1042.apk"}')
        (data / "README.md").write_text("the checkout's README\n")
        (data / ".gitkeep").write_text("")
        (old / "results" / "README.md").write_text("the checkout's README\n")
        (old / "results" / "fluent-learn-session-001.md").write_text("# one\n")
        return data

    # --- a fresh learner -----------------------------------------------------------------------------------------------------

    def test_a_fresh_learner(self):
        out = self.ok("--name", "Ana", "--gender", "female", "--target", "sl", "--base", "de", "--level", "A2", "--goal", "20",
                      "--network", "skip", "--run", "tmux")
        data = self.home / ".local/share/lani/ana"
        profile = json.loads((data / "learner-profile.json").read_text())["learner"]
        self.assertEqual(("Ana", "female", "Slovene", "sl", "German", "de", "A2", "B1", 20),
                         tuple(profile[k] for k in ("name", "gender", "target_language", "target_language_code", "base_language",
                                                    "base_language_code", "current_level", "target_level", "daily_goal_minutes")))
        s = self.settings()
        self.assertEqual(str(data), s["LANI_DATA_DIR"])
        self.assertEqual(str(self.home / ".cache/lani/voice"), s["LANI_VOICE_CACHE"])
        self.assertEqual(str(self.home / ".local/share/lani/releases"), s["LANI_RELEASE_DIR"])
        self.assertEqual("classic", s["LANI_BRIDGE_MODE"])
        self.assertNotIn("LANI_CULTURE", s)  # Slovene: the default village
        text = self.lani_env.read_text()
        self.assertIn("LANI_DATA_DIR=~/.local/share/lani/ana", text)
        self.assertIn("# LANI_DATA_REMOTE=", text)  # the schema, documented, unset
        self.assertIn("# The learner's data: a git repository of its own", text)
        self.assertEqual("lani-setup: Ana's data", self.git(data, "log", "-1", "--format=%s"))
        self.assertTrue((data / ".gitignore").exists() and (data / "results").is_dir())
        self.assertIn("Ana (female) learns Slovene from German, A2, 20 min a day.", out)
        self.assertNotIn("\x1b[", out)  # no colour codes without a terminal

    def test_a_learner_of_italian_lives_in_friuli(self):
        self.ok("--name", "Luka", "--gender", "male", "--target", "it", "--base", "sl", "--network", "skip", "--run", "skip")
        self.assertEqual("friuli", self.settings()["LANI_CULTURE"])

    def test_wrong_answers_stop_it_and_say_why(self):
        out = self.setup_("--name", "Ana", "--target", "sl", "--base", "sl")
        self.assertEqual(2, out.returncode)
        self.assertIn("two different languages", out.stdout)
        out = self.setup_("--name", "Ana", "--gender", "x")
        self.assertEqual(2, out.returncode)
        self.assertIn("--gender: one of male, female", out.stdout)

    # --- importing an earlier install ---------------------------------------------------------------------------------------

    def test_import_copies_the_learner_and_moves_the_caches(self):
        old = self.old_install()
        remote = self.root / "lani-jan.git"
        subprocess.run(["git", "init", "-q", "--bare", "-b", "main", str(remote)], check=True)
        out = self.ok("--import", str(old), "--gender", "male", "--remote", str(remote), "--push", "--network", "tailscale", "--serve",
                      "--run", "systemd")
        data = self.home / ".local/share/lani/jan"
        # the learner, as they were (the questions' answers from their profile)
        learner = json.loads((data / "learner-profile.json").read_text())["learner"]
        self.assertEqual(("Jan", "male", "A2", "German"), (learner["name"], learner["gender"], learner["current_level"], learner["native_language"]))
        # the tokens and the key came along (phones stay paired), mode 600, and out of git
        for name in ("bridge-token", "family-token", "bridge-key.pem"):
            self.assertEqual(f"secret {name}\n", (data / "app" / name).read_text())
            self.assertEqual(0o600, stat.S_IMODE((data / "app" / name).stat().st_mode))
        files = set(self.git(data, "ls-files").splitlines())
        self.assertTrue({"learner-profile.json", "app/game.json", "app/devices.json", "results/fluent-learn-session-001.md"} <= files, files)
        self.assertFalse({"app/bridge-token", "app/family-token", "app/bridge-key.pem", "README.md", "results/README.md"} & files, files)
        # the caches: moved to their own places, gone from the old install and from the data
        self.assertTrue((self.home / ".cache/lani/voice/voice.db").exists() and (self.home / ".cache/lani/voice/files/abc.mp3").exists())
        self.assertEqual({"fluent-629.apk", "lani-1042.apk", "latest.json"}, {f.name for f in (self.home / ".local/share/lani/releases").iterdir()})
        self.assertFalse((old / "app" / "voice").exists() or (old / "app" / "release").exists())
        self.assertFalse((data / "app" / "voice").exists() or (data / "app" / "release").exists())
        self.assertTrue((old / "learner-profile.json").exists())  # the old install's learner files stay
        # the remote has the first commit; nothing secret
        self.assertEqual(self.git(data, "rev-parse", "HEAD"), self.git(remote, "rev-parse", "main"))
        self.assertNotIn("app/bridge-token", self.git(remote, "ls-tree", "-r", "--name-only", "main"))
        s = self.settings()
        self.assertEqual(str(remote), s["LANI_DATA_REMOTE"])
        self.assertEqual(("tailscale", "service"), (s["LANI_NETWORK"], s["LANI_BRIDGE_MODE"]))
        # tailscale serve, the systemd unit; linger never without asking
        calls = self.calls.read_text()
        self.assertIn(f"tailscale serve --bg {self.port}", calls)
        self.assertNotIn("funnel", calls)
        self.assertIn("systemctl --user enable --now lani-bridge@default", calls)
        self.assertNotIn("enable-linger", calls)
        unit = (self.home / ".config/systemd/user/lani-bridge@.service").read_text()
        self.assertIn(f"WorkingDirectory={REPO}", unit)
        self.assertNotIn("@REPO@", unit)
        self.assertIn("Voice clips: moved to ~/.cache/lani/voice.", out)

    def test_import_can_copy_the_caches_instead(self):
        old = self.old_install()
        self.ok("--import", str(old), "--caches", "copy", "--network", "skip", "--run", "skip")
        self.assertTrue((old / "app" / "voice" / "voice.db").exists() and (self.home / ".cache/lani/voice/voice.db").exists())
        self.assertTrue((old / "app" / "release" / "latest.json").exists())

    def test_import_waits_for_the_old_bridge_to_stop(self):
        old = self.old_install()
        holder = subprocess.Popen([sys.executable, "-c", f"f = open({str(old / 'app/voice/voice.db')!r}, 'rb'); import time; time.sleep(60)"])
        try:
            import time
            for _ in range(50):  # until it has the file open
                if any(os.path.realpath(os.path.join(f"/proc/{holder.pid}/fd", fd)).endswith("voice.db") for fd in os.listdir(f"/proc/{holder.pid}/fd")):
                    break
                time.sleep(0.1)
            out = self.setup_("--import", str(old), "--network", "skip", "--run", "skip")
        finally:
            holder.kill()
            holder.wait()
        self.assertEqual(2, out.returncode)
        self.assertIn("still has files open", out.stdout)
        self.assertTrue((old / "app" / "voice" / "voice.db").exists())
        self.assertFalse((self.home / ".local/share/lani/jan/learner-profile.json").exists())

    # --- running it again -------------------------------------------------------------------------------------------------

    def test_a_second_run_changes_only_what_it_is_told(self):
        old = self.old_install()
        remote = self.root / "r.git"
        subprocess.run(["git", "init", "-q", "--bare", "-b", "main", str(remote)], check=True)
        self.ok("--import", str(old), "--gender", "male", "--remote", str(remote), "--network", "skip", "--run", "tmux")
        self.lani_env.write_text(self.lani_env.read_text() + "LANI_VOICE_RESERVE=4000\n")  # a setting of the learner's own
        data = self.home / ".local/share/lani/jan"
        self.ok()
        self.assertIn("# --- Other settings (kept from before) ---\nLANI_VOICE_RESERVE=4000\n", self.lani_env.read_text())
        head, text = self.git(data, "rev-parse", "HEAD"), self.lani_env.read_text()
        self.ok()  # nothing asked, nothing told: nothing changes
        self.assertEqual(head, self.git(data, "rev-parse", "HEAD"))
        self.assertEqual(text, self.lani_env.read_text())
        self.assertIn("imported before", self.ok("--import", str(old)))  # the same command again: done already
        self.assertEqual(head, self.git(data, "rev-parse", "HEAD"))
        self.ok("--level", "B1", "--goal", "45")
        learner = json.loads((data / "learner-profile.json").read_text())["learner"]
        self.assertEqual(("B1", "B2", 45, "male"), (learner["current_level"], learner["target_level"], learner["daily_goal_minutes"], learner["gender"]))
        self.assertIn("level_since", learner)
        self.assertNotEqual(head, self.git(data, "rev-parse", "HEAD"))
        s = self.settings()
        self.assertEqual(("4000", str(remote)), (s["LANI_VOICE_RESERVE"], s["LANI_DATA_REMOTE"]))  # another setting kept; the remote too
        out = self.setup_("--target", "it")
        self.assertEqual(2, out.returncode)
        self.assertIn("is a learner of Slovene", out.stdout)

    def test_a_session_after_setup_is_committed_and_pushed(self):
        remote = self.root / "r.git"
        subprocess.run(["git", "init", "-q", "--bare", "-b", "main", str(remote)], check=True)
        self.ok("--name", "Ana", "--gender", "female", "--remote", str(remote), "--push", "--network", "skip", "--run", "skip")
        data = self.home / ".local/share/lani/ana"
        report = {"session_id": "session-001", "date": "2026-10-05", "duration_minutes": 10,
                  "review_results": [{"item_id": "a", "quality": 4}], "new_vocabulary": [{"item_id": "miza", "word": "miza", "translation": "table"}]}
        e = sandbox_env(self.home)
        out = subprocess.run([sys.executable, str(REPO / ".claude/hooks/update-db.py")], input=json.dumps(report), cwd=self.home,
                             env=e, capture_output=True, text=True)
        self.assertEqual(0, out.returncode, out.stderr)
        self.assertEqual("session 2026-10-05: 1 review, 1 new word", self.git(data, "log", "-1", "--format=%s"))
        head = self.git(data, "rev-parse", "HEAD")
        import time
        for _ in range(100):
            if subprocess.run(["git", "-C", str(remote), "rev-parse", "main"], capture_output=True, text=True).stdout.strip() == head:
                break
            time.sleep(0.1)
        self.assertEqual(head, self.git(remote, "rev-parse", "main"))

    # --- the remote, the key, the network ---------------------------------------------------------------------------------

    def test_a_private_github_repository_with_gh(self):
        self.ok("--name", "Ana", "--gender", "female", "--remote", "github", "--network", "skip", "--run", "skip")
        calls = self.calls.read_text()
        self.assertIn("gh repo create tester/lani-ana --private", calls)
        data = self.home / ".local/share/lani/ana"
        self.assertEqual("https://github.com/tester/lani-ana.git", self.git(data, "remote", "get-url", "origin"))
        self.assertEqual("https://github.com/tester/lani-ana.git", self.settings()["LANI_DATA_REMOTE"])
        self.assertNotIn("git push", calls)

    def test_a_public_repository_is_refused(self):
        out = self.setup_("--name", "Ana", "--remote", "https://github.com/tester/open.git", FAKE_GH_VISIBILITY="PUBLIC")
        self.assertEqual(2, out.returncode)
        self.assertIn("not a private repository", out.stdout)
        self.assertNotIn("LANI_DATA_REMOTE=https", self.lani_env.read_text())

    def test_the_elevenlabs_key_goes_to_keys_env_mode_600_and_is_never_shown(self):
        keys = self.home / ".config/lani/keys.env"
        keys.parent.mkdir(parents=True)
        keys.write_text("OTHER_KEY=kept\nELEVENLABS_API_KEY=old\n")
        key = "sk_test_0123456789abcdef"
        out = self.setup_("--name", "Ana", "--elevenlabs-key-file", "-", "--network", "skip", "--run", "skip", stdin=key + "\n")
        self.assertEqual(0, out.returncode, out.stdout + out.stderr)
        self.assertEqual(f"OTHER_KEY=kept\nELEVENLABS_API_KEY={key}\n", keys.read_text())
        self.assertEqual(0o600, stat.S_IMODE(keys.stat().st_mode))
        self.assertNotIn(key, out.stdout + out.stderr)
        self.assertNotIn(key, self.lani_env.read_text())

    def test_a_lan_address_must_be_https(self):
        out = self.setup_("--name", "Ana", "--network", "lan", "--url", "http://192.168.1.5:8790")
        self.assertEqual(2, out.returncode)
        self.assertIn("https://", out.stdout)
        self.ok("--name", "Ana", "--network", "lan", "--url", "https://lani.home.example/", "--run", "skip")
        self.assertEqual(("lan", "https://lani.home.example"), (self.settings()["LANI_NETWORK"], self.settings()["LANI_PUBLIC_URL"]))


if __name__ == "__main__":
    unittest.main()
