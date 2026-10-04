"""
Lani path resolution — supports dual-mode (clone vs plugin install).

Data directory resolution precedence:
  1. $LANI_DATA_DIR if set (absolutized)
  2. $CLAUDE_PROJECT_DIR/data if that dir holds learner-profile.json (clone mode, non-repo cwd)
  3. ./data if ./data/learner-profile.json exists (clone mode, in-repo cwd)
  4. ~/.claude/lani-data (plugin-mode fallback; ~/.claude/fluent-data while only that one exists)

Before the project was renamed from Fluent to Lani, the variables were named FLUENT_* and the plugin's data lived in
~/.claude/fluent-data. Importing this module copies each FLUENT_X into LANI_X when LANI_X isn't set
(adopt_legacy_env), so an install from then keeps working; the hooks read LANI_* only.

Plugin-root resolution precedence:
  1. $CLAUDE_PLUGIN_ROOT if set
  2. $CLAUDE_PROJECT_DIR if set
  3. parent of this file's .claude/ dir (dev-run fallback)

Pure resolvers (data_dir / plugin_root / backups_dir) do not create directories.
Call ensure_data_dir() before writing.
"""
from __future__ import annotations

import os
import sys
from functools import lru_cache
from pathlib import Path

LEGACY_PREFIX = "FLUENT_"


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

    project = os.environ.get("CLAUDE_PROJECT_DIR")
    if project:
        candidate = (Path(project) / "data").resolve()
        if (candidate / "learner-profile.json").exists():
            return candidate

    cwd_data = (Path.cwd() / "data").resolve()
    if (cwd_data / "learner-profile.json").exists():
        return cwd_data

    return lani_dir(Path.home() / ".claude", "lani-data").resolve()


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
