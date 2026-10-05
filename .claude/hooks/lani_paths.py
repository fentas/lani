"""
Lani path resolution — supports dual-mode (clone vs plugin install).

Data directory resolution precedence:
  1. $LANI_DATA_DIR if set (absolutized)
  2. LANI_DATA_DIR in lani.env (the default learner's data, e.g. a data repository that lani-setup made), unless the
     environment is another learner's (LANI_PROFILE)
  3. $CLAUDE_PROJECT_DIR/data if that dir holds learner-profile.json (clone mode, non-repo cwd)
  4. ./data if ./data/learner-profile.json exists (clone mode, in-repo cwd)
  5. ~/.claude/lani-data (plugin-mode fallback; ~/.claude/fluent-data while only that one exists)

lani.env (~/.config/lani/lani.env, $LANI_ENV_FILE; docs/setup.md, "lani.env") holds the node's settings as KEY=value
lines; the environment wins over it. Importing this module copies its settings for this machine into os.environ where
they aren't set (adopt_env_file). The default learner's (LEARNER_KEYS: their data, voice cache, remote, …) are not
copied, so a process of another learner never inherits them: learner_setting() reads them, for the default learner
only. The same rules are in companion/bridge/src/env.ts and companion/bin/lani-env.sh.

Before the project was renamed from Fluent to Lani, the variables were named FLUENT_* and the plugin's data lived in
~/.claude/fluent-data. Importing this module copies each FLUENT_X into LANI_X when LANI_X isn't set
(adopt_legacy_env), so an install from then keeps working; the hooks read LANI_* only.

Plugin-root resolution precedence:
  1. $CLAUDE_PLUGIN_ROOT if set
  2. $CLAUDE_PROJECT_DIR if set
  3. parent of this file's .claude/ dir (dev-run fallback)

Pure resolvers (data_dir / results_dir / plugin_root / backups_dir) do not create directories.
Call ensure_data_dir() before writing.
"""
from __future__ import annotations

import os
import re
import sys
from functools import lru_cache
from pathlib import Path

LEGACY_PREFIX = "FLUENT_"

# The default learner's settings in lani.env: never copied into the environment (see the module's docstring).
LEARNER_KEYS = frozenset({
    "LANI_DATA_DIR", "LANI_RESULTS_DIR", "LANI_VOICE_CACHE", "LANI_CULTURE", "LANI_LANDSCAPE", "LANI_BRIDGE_PORT",
    "LANI_PUBLIC_URL", "LANI_TOWN_URL", "LANI_DATA_AUTOCOMMIT", "LANI_DATA_REMOTE", "LANI_DATA_PUSH", "LANI_PROFILE",
    "LANI_CHILD",
})


def adopt_legacy_env(env=None):
    """Copy each FLUENT_X of [env] (default os.environ) into LANI_X when LANI_X isn't set. Returns env."""
    env = os.environ if env is None else env
    for k, v in list(env.items()):
        if k.startswith(LEGACY_PREFIX):
            env.setdefault("LANI_" + k[len(LEGACY_PREFIX):], v)
    return env


adopt_legacy_env()


def lani_dir(base: Path, name: str = "lani") -> Path:
    """[base]/lani (or [name]), or the same named fluent while only that one exists (an install from before the rename)."""
    new = Path(base) / name
    old = Path(base) / name.replace("lani", "fluent")
    return old if not new.exists() and old.exists() else new


def _home(env) -> Path:
    return Path(env.get("HOME") or Path.home())


def config_file(name: str, env=None) -> Path:
    """A file of the config directory: $LANI_CONFIG_DIR/NAME, else ~/.config/lani/NAME (~/.config/fluent/NAME while only
    the old one has it)."""
    env = os.environ if env is None else env
    if env.get("LANI_CONFIG_DIR"):
        return Path(env["LANI_CONFIG_DIR"]) / name
    new = _home(env) / ".config" / "lani" / name
    old = _home(env) / ".config" / "fluent" / name
    return old if not new.exists() and old.exists() else new


def env_file(env=None) -> Path:
    """The node's settings: $LANI_ENV_FILE (/dev/null: none; the process's own when [env] doesn't name one, so a test
    that isolates itself stays isolated), else lani.env in the config directory."""
    env = os.environ if env is None else env
    named = env.get("LANI_ENV_FILE") or os.environ.get("LANI_ENV_FILE")
    return Path(named) if named else config_file("lani.env", env)


def expand_home(v: str, home) -> str:
    """"~", "~/x", "$HOME/x" and "${HOME}/x" under [home]; anything else as it is."""
    home = str(home)
    if v == "~" or v.startswith("~/"):
        return home + v[1:]
    return re.sub(r"^(\$\{HOME\}|\$HOME)(?=/|$)", lambda _m: home, v)


_LINE = re.compile(r"^\s*(?:export\s+)?(LANI_[A-Z0-9_]*)\s*=(.*)$")


def parse_env_file(text: str, home) -> dict:
    """lani.env's KEY=value lines (as env.ts parseEnvFile): blank lines, # comments and `export ` skipped; a quoted value
    is what is between the quotes, else it ends at a ` #` comment; a leading ~ or $HOME expanded. Only LANI_* keys."""
    out = {}
    for line in text.splitlines():
        m = _LINE.match(line)
        if not m:
            continue
        v = m.group(2).strip()
        if v[:1] in ("'", '"'):
            end = v.find(v[0], 1)
            v = v[1:end] if end > 0 else v[1:]
        else:
            v = re.sub(r"(^|\s)#.*$", "", v).strip()
        out[m.group(1)] = expand_home(v, home)
    return out


def read_env_file(env=None) -> dict:
    """All of lani.env's settings; {} without the file."""
    env = os.environ if env is None else env
    path = env_file(env)
    try:
        if not path.is_file():
            return {}
        return parse_env_file(path.read_text(encoding="utf-8"), _home(env))
    except OSError:
        return {}


def adopt_env_file(env=None):
    """lani.env's settings for this machine (not LEARNER_KEYS) into [env] (default os.environ) where it doesn't set
    them. Returns env."""
    env = os.environ if env is None else env
    for k, v in read_env_file(env).items():
        if k not in LEARNER_KEYS and k not in env:
            env[k] = v
    return env


def _norm(p: str, home) -> str:
    return os.path.realpath(os.path.abspath(expand_home(p, home)))


def learner_setting(key: str, env=None):
    """lani.env's [key] (one of LEARNER_KEYS) for the default learner; None when it isn't set there, or when [env] is
    another learner's process: LANI_PROFILE names another profile, or LANI_DATA_DIR another directory than lani.env's.
    The environment's own value comes first: see setting()."""
    env = os.environ if env is None else env
    profile = (env.get("LANI_PROFILE") or "").strip()
    if profile and profile != "default":
        return None
    values = read_env_file(env)
    v = values.get(key)
    if not v:
        return None
    if key != "LANI_DATA_DIR" and env.get("LANI_DATA_DIR"):
        mine = values.get("LANI_DATA_DIR")
        if not mine or _norm(env["LANI_DATA_DIR"], _home(env)) != _norm(mine, _home(env)):
            return None
    return v


def setting(key: str, env=None):
    """[key] from the environment, else (a learner's setting) lani.env's for the default learner."""
    env = os.environ if env is None else env
    v = env.get(key)
    return v if v is not None else learner_setting(key, env)


adopt_env_file()


def force_utf8_io() -> None:
    """Make stdout/stderr UTF-8 so emoji/CJK output doesn't crash on Windows.

    Windows consoles default to a legacy code page (cp1252/gbk); printing the
    emoji in the hook summaries raises UnicodeEncodeError there. No-op on
    platforms whose streams are already UTF-8 or predate ``reconfigure``.
    Call once at the top of any hook that prints.
    """
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except (AttributeError, ValueError):
            pass


@lru_cache(maxsize=1)
def data_dir() -> Path:
    """Resolve the runtime data directory (pure — does not create it)."""
    env = os.environ.get("LANI_DATA_DIR")
    if env:
        return Path(env).expanduser().resolve()

    configured = learner_setting("LANI_DATA_DIR")
    if configured:
        return Path(configured).expanduser().resolve()

    project = os.environ.get("CLAUDE_PROJECT_DIR")
    if project:
        candidate = (Path(project) / "data").resolve()
        if (candidate / "learner-profile.json").exists():
            return candidate

    cwd_data = (Path.cwd() / "data").resolve()
    if (cwd_data / "learner-profile.json").exists():
        return cwd_data

    return lani_dir(Path.home() / ".claude", "lani-data").resolve()


def results_dir_of(data: Path, env=None) -> Path:
    """Where the tutor's session result files (results/*.md) of the learner whose data is [data] go: $LANI_RESULTS_DIR
    (lani.env's for the default learner); inside a data repository (lani-setup) its results/; else next to the data
    (the checkout's results/, a profile's profiles/<id>/results/)."""
    configured = setting("LANI_RESULTS_DIR", env)
    if configured:
        return Path(configured).expanduser().resolve()
    data = Path(data)
    if (data / ".git").exists() or (data / "results").is_dir():
        return data / "results"
    return (data.parent / "results").resolve()


def results_dir() -> Path:
    """The tutor's session result files of this learner (pure — does not create it)."""
    return results_dir_of(data_dir())


def voice_dir_of(data: Path, env=None) -> Path:
    """The voice store (voice.db, files/) of the learner whose data is [data], as the bridge finds it (env.ts voiceDir):
    $LANI_VOICE_CACHE (lani.env's for the default learner; ~/.cache/lani/voice after lani-setup), but the old place
    <data>/app/voice while only that one has the clips; without the setting, <data>/app/voice."""
    old = Path(data) / "app" / "voice"
    configured = setting("LANI_VOICE_CACHE", env)
    if not configured:
        return old
    new = Path(configured).expanduser()
    return old if not (new / "voice.db").exists() and (old / "voice.db").exists() else new


def ensure_data_dir() -> Path:
    """Resolve the data directory and create it if missing. Call before writing."""
    d = data_dir()
    d.mkdir(parents=True, exist_ok=True)
    return d


@lru_cache(maxsize=1)
def plugin_root() -> Path:
    """Resolve the plugin/repo root directory."""
    env = os.environ.get("CLAUDE_PLUGIN_ROOT")
    if env:
        return Path(env).resolve()
    env = os.environ.get("CLAUDE_PROJECT_DIR")
    if env:
        return Path(env).resolve()
    return Path(__file__).resolve().parents[2]


@lru_cache(maxsize=1)
def backups_dir() -> Path:
    """Resolve the backups directory. Always nested inside data_dir to avoid collisions
    when the fallback ~/.claude/lani-data is used (the parent ~/.claude/ is shared
    across plugins)."""
    return data_dir() / ".backups"


def ensure_backups_dir() -> Path:
    """Resolve the backups directory and create it if missing."""
    b = backups_dir()
    b.mkdir(parents=True, exist_ok=True)
    return b
