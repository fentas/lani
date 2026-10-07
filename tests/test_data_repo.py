"""The learner's data as a git repository of its own (.claude/hooks/lani_data_repo.py, docs/setup.md "Your data
repository"): what lani-setup's init keeps out (secrets, caches, runtime files), the commit after each session
(update-db.py) and when the tutor session ends, the push to a private remote in the background (never forced), and
that nothing else is ever committed or pushed. Every run is in a temporary HOME; remotes are local bare repositories.
Run: python3 -m unittest discover -s tests"""
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

os.environ["LANI_ENV_FILE"] = os.devnull  # this process never reads the machine's own lani.env

REPO = Path(__file__).resolve().parents[1]
HOOKS = REPO / ".claude" / "hooks"
sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_config import sandbox_env  # noqa: E402

TEMPLATES = REPO / "data-examples"


def git(cwd: Path, *args: str) -> str:
    return subprocess.run(["git", "-C", str(cwd), *args], capture_output=True, text=True, check=True).stdout.strip()


class DataRepoTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name).resolve()
        self.home = root / "home"
        self.data = self.home / ".local/share/lani/ana"
        self.data.mkdir(parents=True)
        for t in TEMPLATES.glob("*-template.json"):
            (self.data / t.name.replace("-template", "")).write_text(t.read_text())
        app = self.data / "app"
        for d in ("voice/files", "release", "pairing", "town-invites", "audio", "modules"):
            (app / d).mkdir(parents=True)
        for f in ("bridge-token", "family-token", "bridge-key.pem", "client-ids.json", "devices-seen.json", "events.json",
                  "channel-queue.json", "voice/voice.db", "voice/files/a.mp3", "release/lani-1001.apk",
                  "pairing/abc", "town-invites/def"):
            (app / f).write_text("x")
        (app / "game.json").write_text("{}")
        (app / "devices.json").write_text('{"devices": []}')
        (app / "audio" / "index.json").write_text("{}")
        (self.data / "learner-profile.json.backup-20261005-101010").write_text("{}")
        (self.data / ".backups").mkdir()
        (self.data / ".backups" / "old.json").write_text("{}")
        (self.data / "results").mkdir()
        (self.data / "results" / "lani-learn-session-001.md").write_text("# one\n")
        self.remote = root / "remote.git"
        subprocess.run(["git", "init", "-q", "--bare", "-b", "main", str(self.remote)], check=True)
        (self.home / ".config/lani").mkdir(parents=True)
        self.lani_env = self.home / ".config/lani/lani.env"
        self.lani_env.write_text(f"LANI_DATA_DIR={self.data}\n")

    def tearDown(self):
        self.tmp.cleanup()

    def run_hook(self, script: str, stdin: str = "", **env) -> subprocess.CompletedProcess:
        return subprocess.run([sys.executable, str(HOOKS / script)], input=stdin, cwd=self.home, capture_output=True,
                              text=True, env=sandbox_env(self.home, **env))

    def repo(self, *args: str, **env) -> subprocess.CompletedProcess:
        return subprocess.run([sys.executable, str(HOOKS / "lani_data_repo.py"), *args], cwd=self.home, capture_output=True,
                              text=True, env=sandbox_env(self.home, **env))

    def init(self):
        out = self.repo("init", str(self.data), "-m", "lani-setup: import")
        self.assertEqual(0, out.returncode, out.stderr)
        return out.stdout.strip()

    def report(self, session_id="session-001", **more) -> str:
        r = {"session_id": session_id, "date": "2026-10-05", "duration_minutes": 12, "command_used": "/lani-app-review",
             "review_results": [{"item_id": f"w{i}", "quality": 4} for i in range(12)],
             "new_vocabulary": [{"item_id": "miza", "word": "miza", "translation": "table"},
                                {"item_id": "stol", "word": "stol", "translation": "chair"}]}
        r.update(more)
        return json.dumps(r)

    def wait_for(self, cond, secs=10.0):
        end = time.monotonic() + secs
        while time.monotonic() < end:
            if cond():
                return True
            time.sleep(0.1)
        return cond()

    def test_init_keeps_secrets_caches_and_runtime_files_out(self):
        sha = self.init()
        self.assertTrue(sha)
        files = set(git(self.data, "ls-files").splitlines())
        for kept in (".gitignore", "learner-profile.json", "spaced-repetition.json", "app/game.json", "app/devices.json",
                     "app/audio/index.json", "results/lani-learn-session-001.md"):
            self.assertIn(kept, files)
        for secret_or_cache in ("app/bridge-token", "app/family-token", "app/bridge-key.pem", "app/voice/voice.db",
                                "app/voice/files/a.mp3", "app/release/lani-1001.apk", "app/pairing/abc",
                                "app/town-invites/def", "app/client-ids.json", "app/devices-seen.json", "app/events.json",
                                "app/channel-queue.json", ".backups/old.json", "learner-profile.json.backup-20261005-101010"):
            self.assertNotIn(secret_or_cache, files)
        self.assertEqual("main", git(self.data, "branch", "--show-current"))
        self.assertEqual("lani-setup: import", git(self.data, "log", "-1", "--format=%s"))

    def test_init_again_changes_nothing_and_keeps_the_learners_own_gitignore(self):
        self.init()
        mine = (self.data / ".gitignore").read_text() + "# mine\n/app/wishlist.md\n"
        (self.data / ".gitignore").write_text(mine)
        git(self.data, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qam", "my own ignores")
        head = git(self.data, "rev-parse", "HEAD")
        self.assertEqual("", self.init())
        self.assertEqual(head, git(self.data, "rev-parse", "HEAD"))
        self.assertEqual(mine, (self.data / ".gitignore").read_text())

    def test_a_session_is_committed_with_a_short_message(self):
        self.init()
        out = self.run_hook("update-db.py", self.report())
        self.assertEqual(0, out.returncode, out.stderr)
        self.assertEqual("session 2026-10-05: 12 reviews, 2 new words", git(self.data, "log", "-1", "--format=%s"))
        self.assertIn("session-001 · /lani-app-review", git(self.data, "log", "-1", "--format=%b"))
        self.assertIn("data repository:", out.stderr)
        self.assertEqual("", git(self.data, "status", "--porcelain"))  # the backups update-db.py made are ignored

    def test_the_session_end_hook_commits_what_changed_since(self):
        self.init()
        (self.data / "app" / "game.json").write_text('{"age": "OGENJ"}')
        out = self.run_hook("session-end.py", "{}")
        self.assertEqual(0, out.returncode, out.stderr)
        self.assertTrue(git(self.data, "log", "-1", "--format=%s").startswith("tutor session ended "))
        before = git(self.data, "rev-parse", "HEAD")
        self.run_hook("session-end.py", "{}")
        self.assertEqual(before, git(self.data, "rev-parse", "HEAD"))  # nothing changed: no empty commit

    def test_each_commit_is_pushed_to_the_remote_in_the_background(self):
        self.init()
        self.lani_env.write_text(f"LANI_DATA_DIR={self.data}\nLANI_DATA_REMOTE={self.remote}\n")
        out = self.run_hook("update-db.py", self.report())
        self.assertEqual(0, out.returncode, out.stderr)
        head = git(self.data, "rev-parse", "HEAD")
        self.assertTrue(self.wait_for(lambda: subprocess.run(["git", "-C", str(self.remote), "rev-parse", "main"],
                                                              capture_output=True, text=True).stdout.strip() == head))
        self.assertEqual(str(self.remote), git(self.data, "remote", "get-url", "origin"))
        log = self.home / ".local/state/lani/data-push.log"
        self.assertTrue(log.exists())
        files = set(git(self.remote, "ls-tree", "-r", "--name-only", "main").splitlines())
        self.assertNotIn("app/bridge-token", files)
        self.assertNotIn("app/voice/voice.db", files)

    def test_a_push_is_never_forced(self):
        self.init()
        # the remote has a commit the learner's repository doesn't: a push is refused, nothing is overwritten
        other = Path(self.tmp.name) / "other"
        subprocess.run(["git", "clone", "-q", str(self.remote), str(other)], check=True, capture_output=True)
        (other / "elsewhere.txt").write_text("from another machine\n")
        git(other, "add", "-A")
        git(other, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "elsewhere")
        git(other, "push", "-q", "origin", "HEAD:main")
        theirs = git(self.remote, "rev-parse", "main")
        self.lani_env.write_text(f"LANI_DATA_DIR={self.data}\nLANI_DATA_REMOTE={self.remote}\n")
        out = self.run_hook("update-db.py", self.report())
        self.assertEqual(0, out.returncode, out.stderr)
        log = self.home / ".local/state/lani/data-push.log"
        self.assertTrue(self.wait_for(lambda: log.exists() and ("rejected" in log.read_text() or "failed" in log.read_text())))
        self.assertEqual(theirs, git(self.remote, "rev-parse", "main"))
        self.assertTrue(git(self.data, "log", "-1", "--format=%s").startswith("session 2026-10-05"))

    def test_off_means_no_commit_and_push_off_means_no_push(self):
        self.init()
        head = git(self.data, "rev-parse", "HEAD")
        self.lani_env.write_text(f"LANI_DATA_DIR={self.data}\nLANI_DATA_AUTOCOMMIT=off\n")
        self.run_hook("update-db.py", self.report())
        self.assertEqual(head, git(self.data, "rev-parse", "HEAD"))
        self.lani_env.write_text(f"LANI_DATA_DIR={self.data}\nLANI_DATA_REMOTE={self.remote}\nLANI_DATA_PUSH=off\n")
        self.run_hook("update-db.py", self.report("session-002"))
        self.assertNotEqual(head, git(self.data, "rev-parse", "HEAD"))
        self.assertNotEqual(0, subprocess.run(["git", "-C", str(self.remote), "rev-parse", "main"], capture_output=True).returncode)

    def test_a_data_directory_that_isnt_a_repository_is_left_alone(self):
        # e.g. the checkout's own data/, inside the app's repository: never committed into it
        outer = Path(self.tmp.name) / "checkout"
        (outer / "data").mkdir(parents=True)
        for t in TEMPLATES.glob("*-template.json"):
            (outer / "data" / t.name.replace("-template", "")).write_text(t.read_text())
        subprocess.run(["git", "init", "-q", str(outer)], check=True)
        out = self.run_hook("update-db.py", self.report(), LANI_DATA_DIR=outer / "data")
        self.assertEqual(0, out.returncode, out.stderr)
        self.assertNotEqual(0, subprocess.run(["git", "-C", str(outer), "rev-parse", "HEAD"], capture_output=True).returncode)
        self.assertNotIn("data repository", out.stderr)

    def test_another_learners_repository_never_gets_the_default_learners_remote(self):
        self.init()
        self.lani_env.write_text(f"LANI_DATA_DIR={self.data}\nLANI_DATA_REMOTE={self.remote}\n")
        luka = Path(self.tmp.name) / "luka"
        luka.mkdir()
        for t in TEMPLATES.glob("*-template.json"):
            (luka / t.name.replace("-template", "")).write_text(t.read_text())
        self.assertEqual(0, self.repo("init", str(luka)).returncode)
        out = self.run_hook("update-db.py", self.report(), LANI_DATA_DIR=luka, LANI_PROFILE="luka")
        self.assertEqual(0, out.returncode, out.stderr)
        self.assertTrue(git(luka, "log", "-1", "--format=%s").startswith("session 2026-10-05"))
        self.assertNotEqual(0, subprocess.run(["git", "-C", str(luka), "remote", "get-url", "origin"], capture_output=True).returncode)

    def test_files_lists_what_a_push_sends(self):
        self.init()
        out = self.repo("files", str(self.data))
        listed = [line.split("\t", 1)[1] for line in out.stdout.splitlines()]
        self.assertIn("learner-profile.json", listed)
        self.assertNotIn("app/bridge-token", listed)

    def test_messages(self):
        sys.path.insert(0, str(HOOKS))
        import lani_data_repo as r
        self.assertEqual("session 2026-10-05: 1 review, 1 mistake", r.session_message(
            {"date": "2026-10-05", "review_results": [{}], "errors": [{}]}).splitlines()[0])
        # a word got wrong in a dialog lowers its card gently: no review
        self.assertEqual("session 2026-10-05: 1 review, 1 card lowered", r.session_message(
            {"date": "2026-10-05", "review_results": [{"item_id": "a", "quality": 4}, {"item_id": "b", "gentle": True}]}).splitlines()[0])
        self.assertEqual("session 2026-10-05: 7 exercises", r.session_message(
            {"date": "2026-10-05", "skill_scores": {"writing": {"exercises": 7}}}).splitlines()[0])
        self.assertEqual("session 2026-10-05: 20 min", r.session_message({"date": "2026-10-05", "duration_minutes": 20}))
        self.assertIn("session-it-003 · /lani-learn · it", r.session_message(
            {"date": "2026-10-05", "session_id": "session-it-003", "command_used": "/lani-learn", "language": "it"}))


if __name__ == "__main__":
    unittest.main()
