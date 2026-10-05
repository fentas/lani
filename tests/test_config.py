"""lani.env, the node's settings (docs/setup.md, "lani.env"): parsed alike by the hooks (lani_paths), the bridge (env.ts)
and the scripts (lani-env.sh); the environment wins; the default learner's settings (their data, voice cache, remote)
never reach another learner's process; the voice cache and the session results are found where lani-setup puts them,
and in the old places while those are all there is. Every run is in a temporary HOME.
Run: python3 -m unittest discover -s tests"""
import json
import os
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

# This process never reads the machine's own lani.env (it imports lani_paths below); every child gets its own HOME.
os.environ["LANI_ENV_FILE"] = os.devnull

REPO = Path(__file__).resolve().parents[1]
HOOKS = REPO / ".claude" / "hooks"
BRIDGE = REPO / "companion" / "bridge"

SAMPLE = """\
# the default learner
LANI_DATA_DIR=~/.local/share/lani/ana
LANI_VOICE_CACHE="$HOME/.cache/lani/voice"   # quoted, then a comment
LANI_DATA_REMOTE=git@example.org:ana/lani-ana.git
# this machine
export LANI_RELEASE_DIR=${HOME}/.local/share/lani/releases
LANI_STT_URL=http://127.0.0.1:9 # a comment
LANI_GEPARD_URL='http://h/#not-a-comment'
LANI_EMPTY=
  LANI_SPACED  =  $HOME/a b
ELEVENLABS_API_KEY=never-read-here
FLUENT_OLD=not-read-either
"""


def real_tool(name: str) -> str:
    """[name] on PATH, past version-manager shims (mise's need the real HOME, and the tests run in another)."""
    for d in os.environ.get("PATH", "").split(os.pathsep):
        p = Path(d) / name
        if "shims" not in Path(d).parts and p.is_file() and os.access(p, os.X_OK):
            return str(p)
    return name


def sandbox_env(home: Path, **more) -> dict:
    """A clean environment in [home]: no LANI_*/FLUENT_*/XDG_* of the machine running the tests, and the real bun and
    python3 first on PATH."""
    env = {k: v for k, v in os.environ.items() if not k.startswith(("LANI_", "FLUENT_", "XDG_", "CLAUDE_", "TMUX"))}
    tools = [str(Path(real_tool("bun")).parent), str(Path(sys.executable).parent)]
    env.update(HOME=str(home), XDG_CONFIG_HOME=str(home / ".config"), XDG_CACHE_HOME=str(home / ".cache"),
               XDG_DATA_HOME=str(home / ".local/share"), XDG_STATE_HOME=str(home / ".local/state"),
               PATH=os.pathsep.join(tools + [env.get("PATH", "")]))
    env.update({k: str(v) for k, v in more.items()})
    return env


class ConfigTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.home = Path(self.tmp.name).resolve() / "home"
        (self.home / ".config" / "lani").mkdir(parents=True)
        self.envfile = self.home / ".config" / "lani" / "lani.env"
        self.envfile.write_text(SAMPLE)
        self.data = self.home / ".local/share/lani/ana"

    def tearDown(self):
        self.tmp.cleanup()

    def py(self, code: str, **env) -> str:
        out = subprocess.run([sys.executable, "-c", f"import sys, os; sys.path.insert(0, {str(HOOKS)!r}); import lani_paths; {code}"],
                             cwd=self.home, env=sandbox_env(self.home, **env), capture_output=True, text=True)
        self.assertEqual(0, out.returncode, out.stderr)
        return out.stdout.strip()

    def bash(self, code: str, **env) -> str:
        out = subprocess.run(["bash", "-c", f". {REPO / 'companion/bin/lani-env.sh'}; {code}"], cwd=self.home,
                             env=sandbox_env(self.home, **env), capture_output=True, text=True)
        self.assertEqual(0, out.returncode, out.stderr)
        return out.stdout.strip()

    def ts(self, code: str, **env) -> str:
        out = subprocess.run(["bun", "-e", f"const e = await import({str(BRIDGE / 'src/env.ts')!r}); {code}"], cwd=BRIDGE,
                             env=sandbox_env(self.home, **env), capture_output=True, text=True)
        self.assertEqual(0, out.returncode, out.stderr)
        return out.stdout.strip()

    def test_the_three_readers_parse_it_alike(self):
        py = json.loads(self.py("import json, lani_paths as p; print(json.dumps(p.read_env_file()))"))
        ts = json.loads(self.ts("console.log(JSON.stringify(e.readEnvFile()))"))
        self.assertEqual(py, ts)
        h = str(self.home)
        self.assertEqual({
            "LANI_DATA_DIR": f"{h}/.local/share/lani/ana", "LANI_VOICE_CACHE": f"{h}/.cache/lani/voice",
            "LANI_DATA_REMOTE": "git@example.org:ana/lani-ana.git", "LANI_RELEASE_DIR": f"{h}/.local/share/lani/releases",
            "LANI_STT_URL": "http://127.0.0.1:9", "LANI_GEPARD_URL": "http://h/#not-a-comment", "LANI_EMPTY": "",
            "LANI_SPACED": f"{h}/a b",
        }, py)
        # bash exports this machine's settings only, as the others copy them into their environment
        exported = dict(line.split("=", 1) for line in self.bash("env | grep '^LANI_' | sort").splitlines())
        self.assertEqual({k: v for k, v in py.items() if k not in ("LANI_DATA_DIR", "LANI_VOICE_CACHE", "LANI_DATA_REMOTE")}, exported)

    def test_the_environment_wins_and_a_fluent_name_counts_as_set(self):
        self.assertEqual("http://mine", self.py("print(os.environ['LANI_STT_URL'])", LANI_STT_URL="http://mine"))
        self.assertEqual("http://old", self.py("print(os.environ['LANI_STT_URL'])", FLUENT_STT_URL="http://old"))
        self.assertEqual("http://mine", self.bash('printf %s "$LANI_STT_URL"', LANI_STT_URL="http://mine"))
        self.assertEqual("http://old", self.bash('printf %s "$LANI_STT_URL"', FLUENT_STT_URL="http://old"))
        self.assertEqual("http://mine", self.ts("console.log(process.env.LANI_STT_URL)", LANI_STT_URL="http://mine"))

    def test_the_learners_settings_stay_out_of_the_environment(self):
        for out in (self.py("print(os.environ.get('LANI_DATA_DIR', '-'), os.environ.get('LANI_VOICE_CACHE', '-'))"),
                    self.bash('printf "%s %s" "${LANI_DATA_DIR--}" "${LANI_VOICE_CACHE--}"'),
                    self.ts("console.log(process.env.LANI_DATA_DIR ?? '-', process.env.LANI_VOICE_CACHE ?? '-')")):
            self.assertEqual("- -", out)

    def test_the_data_directory_comes_from_it_for_the_default_learner_only(self):
        data = "import lani_paths as p; print(p.data_dir())"
        self.assertEqual(str(self.data), self.py(data))
        self.assertEqual(str(self.home / "mine"), self.py(data, LANI_DATA_DIR=self.home / "mine"))
        # another learner's process (lani-session --profile) never gets the default learner's data
        self.assertNotEqual(str(self.data), self.py(data, LANI_PROFILE="luka"))
        voice = "import lani_paths as p; print(p.learner_setting('LANI_VOICE_CACHE'))"
        self.assertEqual(str(self.home / ".cache/lani/voice"), self.py(voice))
        self.assertEqual(str(self.home / ".cache/lani/voice"), self.py(voice, LANI_DATA_DIR=self.data))
        self.assertEqual("None", self.py(voice, LANI_DATA_DIR=self.home / "other"))
        self.assertEqual("None", self.py(voice, LANI_PROFILE="luka"))
        self.assertEqual("undefined", self.ts("console.log(e.learnerSetting('LANI_VOICE_CACHE'))", LANI_PROFILE="luka"))
        self.assertEqual(str(self.home / ".cache/lani/voice"), self.ts("console.log(e.learnerSetting('LANI_VOICE_CACHE'))"))

    def test_lani_env_file_and_config_dir_name_another_file(self):
        other = self.home / "other.env"
        other.write_text("LANI_STT_URL=http://other\n")
        self.assertEqual("http://other", self.py("print(os.environ['LANI_STT_URL'])", LANI_ENV_FILE=other))
        self.assertEqual("-", self.py("print(os.environ.get('LANI_STT_URL', '-'))", LANI_ENV_FILE="/dev/null"))
        (self.home / "cfg").mkdir()
        (self.home / "cfg" / "lani.env").write_text("LANI_STT_URL=http://cfg\n")
        self.assertEqual("http://cfg", self.bash('printf %s "$LANI_STT_URL"', LANI_CONFIG_DIR=self.home / "cfg"))

    def test_results_and_voice_are_found_in_the_new_places_and_the_old(self):
        import importlib
        sys.path.insert(0, str(HOOKS))
        p = importlib.import_module("lani_paths")
        env = sandbox_env(self.home, LANI_ENV_FILE="/dev/null")
        plain = self.home / "checkout" / "data"
        self.assertEqual((self.home / "checkout" / "results").resolve(), p.results_dir_of(plain, env))
        (self.data / ".git").mkdir(parents=True)
        self.assertEqual(self.data / "results", p.results_dir_of(self.data, env))
        # the voice store: the cache when set, the old place while only it has the clips
        old = plain / "app" / "voice"
        cache = self.home / ".cache" / "lani" / "voice"
        self.assertEqual(old, p.voice_dir_of(plain, env))
        self.assertEqual(cache, p.voice_dir_of(plain, {**env, "LANI_VOICE_CACHE": str(cache)}))
        old.mkdir(parents=True)
        (old / "voice.db").write_bytes(b"")
        self.assertEqual(old, p.voice_dir_of(plain, {**env, "LANI_VOICE_CACHE": str(cache)}))
        cache.mkdir(parents=True)
        (cache / "voice.db").write_bytes(b"")
        self.assertEqual(cache, p.voice_dir_of(plain, {**env, "LANI_VOICE_CACHE": str(cache)}))


class BackupWithLaniEnvTest(unittest.TestCase):
    """lani-backup finds the data, the results inside a data repository and the voice cache as the bridge does."""

    def test_a_snapshot_of_a_data_repository_and_its_voice_cache(self):
        with tempfile.TemporaryDirectory() as t:
            home = Path(t).resolve()
            data, cache = home / "share" / "jan", home / "cache" / "voice"
            (data / ".git").mkdir(parents=True)
            (data / ".git" / "HEAD").write_text("ref: refs/heads/main\n")
            (data / "results").mkdir()
            (data / "results" / "lani-learn-session-001.md").write_text("# session\n")
            (data / "learner-profile.json").write_text('{"learner": {"name": "Jan"}}')
            (data / "app").mkdir()
            (data / "app" / "game.json").write_text("{}")
            (cache / "files").mkdir(parents=True)
            (cache / "files" / "a.mp3").write_bytes(b"\xff" * 100)
            with sqlite3.connect(cache / "voice.db") as db:
                db.execute("CREATE TABLE clips (key TEXT)")
            (home / ".config" / "lani").mkdir(parents=True)
            (home / ".config" / "lani" / "lani.env").write_text(f"LANI_DATA_DIR={data}\nLANI_VOICE_CACHE={cache}\n")
            env = sandbox_env(home, LANI_BACKUP_DIR=home / "backups")
            out = subprocess.run([sys.executable, str(REPO / "companion/bin/lani-backup"), "run"], env=env,
                                 capture_output=True, text=True)
            self.assertEqual(0, out.returncode, out.stdout + out.stderr)
            snap = next(p for p in (home / "backups").iterdir() if (p / "manifest.json").exists())
            files = json.loads((snap / "manifest.json").read_text())["files"]
            self.assertIn("data/learner-profile.json", files)
            self.assertIn("data/app/game.json", files)
            self.assertIn("results/lani-learn-session-001.md", files)
            self.assertIn("voice/voice.db", files)
            self.assertIn("voice/files/a.mp3", files)
            self.assertFalse([k for k in files if k.startswith("data/.git/") or k.startswith("data/results/")], files)


if __name__ == "__main__":
    unittest.main()
