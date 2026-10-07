#!/usr/bin/env python3
"""
A learner's data as a git repository of its own (docs/setup.md, "Your data repository").

lani-setup makes the data directory a repository (`init`): a .gitignore that keeps out the secrets, the caches and the
runtime files, and a first commit. From then on, each session is committed on its own (`autocommit`): update-db.py
after it wrote a session (a report from the tutor, a review from the app), and the SessionEnd hook when the tutor
session ends. With a remote (LANI_DATA_REMOTE in lani.env, the repository's `origin`), each commit is pushed in the
background; never forced, and a push that fails is tried again with the next commit.

Only a data directory that is a repository's root counts (<data>/.git): the checkout's own data/ is never committed into
the app's repository.

    lani_data_repo.py init DIR [--message M]   make DIR a data repository (idempotent): .gitignore, git init, a commit
    lani_data_repo.py commit [DIR] -m M         commit what changed (no push)
    lani_data_repo.py autocommit [DIR] -m M     the same as after a session: commit, then push when a remote is set
    lani_data_repo.py files [DIR]               what the repository holds (what a push sends), with sizes
    lani_data_repo.py gitignore                 the .gitignore lani-setup writes

DIR defaults to the learner's data directory (lani_paths.data_dir). Settings (lani.env, the default learner's; the
environment wins): LANI_DATA_AUTOCOMMIT=off turns the commits after sessions off; LANI_DATA_REMOTE is the remote;
LANI_DATA_PUSH=off commits without pushing.
"""
from __future__ import annotations

import argparse
import contextlib
import fcntl
import os
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lani_paths  # noqa: E402

GITIGNORE = """\
# What stays out of this learner's data repository (written by lani-setup; edit as you like).
# See docs/setup.md, "Your data repository", and docs/privacy.md.

# Secrets: they prove this node and the phones to each other. They stay on this machine.
/app/bridge-token
/app/family-token
/app/bridge-key.pem
*.pem
*.key
keys.env
/app/pairing/
/app/town-invites/

# Caches: made or fetched again when they're missing. The voice clips live in ~/.cache/lani/voice and the APKs in
# ~/.local/share/lani/releases; a data directory that isn't moved yet keeps them here.
/app/voice/
/app/release/
*.apk

# Runtime state: rewritten all the time, nothing worth keeping in history.
/app/client-ids.json
/app/devices-seen.json
/app/events.json
/app/channel-queue.json

# Backups (snapshots already) and temporary files
/.backups/
*.backup-*
*.tmp
*.restore-tmp
*-wal
*-shm
*-journal
.DS_Store
__pycache__/
"""

GIT_TIMEOUT = 30
LOCK_WAIT = 10.0


def log(msg: str) -> None:
    print(f"[Lani] {msg}", file=sys.stderr)


def is_repo(data: Path) -> bool:
    """Whether [data] is a data repository: the root of a git repository of its own."""
    return (Path(data) / ".git").exists()


def git_env() -> dict:
    """The environment for git: no inherited GIT_DIR and the like (a hook may run inside another repository's git), and
    never a prompt for a password (a push in the background has no terminal)."""
    env = {k: v for k, v in os.environ.items()
           if k not in ("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE", "GIT_OBJECT_DIRECTORY", "GIT_COMMON_DIR",
                        "GIT_PREFIX", "GIT_NAMESPACE", "GIT_CEILING_DIRECTORIES")}
    env["GIT_TERMINAL_PROMPT"] = "0"
    env.setdefault("GIT_SSH_COMMAND", "ssh -o BatchMode=yes")
    return env


def git(data: Path, *args: str, check: bool = True, timeout: int = GIT_TIMEOUT) -> subprocess.CompletedProcess:
    p = subprocess.run(["git", "-C", str(data), *args], env=git_env(), capture_output=True, text=True, timeout=timeout)
    if check and p.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)}: {(p.stderr or p.stdout).strip()}")
    return p


def identity(data: Path) -> list:
    """`-c user.name=… -c user.email=…` when git has no identity of its own for [data] (a fresh machine), else nothing."""
    p = git(data, "var", "GIT_COMMITTER_IDENT", check=False)
    if p.returncode == 0:
        return []
    return ["-c", "user.name=Lani", "-c", f"user.email=lani@{os.uname().nodename or 'localhost'}"]


@contextlib.contextmanager
def locked(data: Path):
    """One commit at a time per repository (the bridge's review and the tutor's report can come at once). Yields
    whether the lock was had within LOCK_WAIT seconds."""
    path = Path(data) / ".git" / "lani-commit.lock"
    with open(path, "a") as f:
        end = time.monotonic() + LOCK_WAIT
        while True:
            try:
                fcntl.flock(f, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= end:
                    yield False
                    return
                time.sleep(0.1)
        try:
            yield True
        finally:
            fcntl.flock(f, fcntl.LOCK_UN)


def write_gitignore(data: Path) -> bool:
    """Writes the .gitignore when there is none; a learner's own is kept. Returns whether it wrote one."""
    p = Path(data) / ".gitignore"
    if p.exists():
        return False
    p.write_text(GITIGNORE, encoding="utf-8")
    return True


def commit(data: Path, message: str) -> str | None:
    """Commits everything that changed in [data] (the .gitignore decides what counts). Returns the new commit's short
    hash, or None when nothing changed or the repository is busy."""
    data = Path(data)
    with locked(data) as got:
        if not got:
            log(f"data repository busy: not committed now ({message}); the next commit takes it along")
            return None
        git(data, "add", "-A")
        if git(data, "diff", "--cached", "--quiet", check=False).returncode == 0:
            return None
        # No signing (it could wait for a passphrase) and no hooks: these commits are the machine's.
        git(data, *identity(data), "-c", "commit.gpgsign=false", "commit", "--no-verify", "-q", "-m", message)
        return git(data, "rev-parse", "--short", "HEAD").stdout.strip()


def init(data: Path, message: str = "lani-setup: the learner's data") -> str | None:
    """Makes [data] a data repository (idempotent): the .gitignore (unless it has one), `git init -b main`, a commit of
    what is there. Returns the commit's short hash (None: nothing new to commit)."""
    data = Path(data)
    data.mkdir(parents=True, exist_ok=True)
    write_gitignore(data)
    if not is_repo(data):
        p = git(data, "init", "-q", "-b", "main", check=False)
        if p.returncode != 0:  # a git older than 2.28 has no -b
            git(data, "init", "-q")
            git(data, "symbolic-ref", "HEAD", "refs/heads/main")
    return commit(data, message)


def push_target(data: Path, remote: str) -> str | None:
    """The remote to push [data] to: `origin`, added from [remote] (LANI_DATA_REMOTE) when the repository has none.
    None (logged) when origin points somewhere else: the learner changed it, and lani-setup is the place to change it."""
    p = git(data, "remote", "get-url", "origin", check=False)
    if p.returncode != 0:
        git(data, "remote", "add", "origin", remote)
        return "origin"
    if p.stdout.strip() != remote:
        log(f"data repository: origin is {p.stdout.strip()}, lani.env says LANI_DATA_REMOTE={remote}: not pushed "
            f"(run companion/bin/lani-setup to change the remote)")
        return None
    return "origin"


def push_log() -> Path:
    state = lani_paths.lani_dir(Path(os.environ.get("XDG_STATE_HOME") or Path.home() / ".local" / "state"))
    return state / "data-push.log"


def settings_of(data: Path, key: str):
    """[key] for the learner whose data is [data]: the environment's, else lani.env's when [data] is the default
    learner's (another repository never gets the default learner's remote)."""
    return lani_paths.setting(key, {**os.environ, "LANI_DATA_DIR": str(data)})


OFF = ("off", "0", "no", "false")


def push_in_background(data: Path) -> bool:
    """`git push origin HEAD` (never forced) in a process of its own, so a session never waits for the network. Its
    output goes to ~/.local/state/lani/data-push.log. Returns whether a push was started."""
    remote = settings_of(data, "LANI_DATA_REMOTE")
    if not remote or (settings_of(data, "LANI_DATA_PUSH") or "on").lower() in OFF:
        return False
    if not push_target(data, remote):
        return False
    out = push_log()
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "a") as f:
        f.write(f"--- {datetime.now().isoformat(timespec='seconds')} push {data}\n")
        f.flush()
        subprocess.Popen(["git", "-C", str(data), "push", "-q", "origin", "HEAD"], env=git_env(), stdin=subprocess.DEVNULL,
                         stdout=f, stderr=f, start_new_session=True)
    return True


def autocommit(data: Path, message: str) -> str | None:
    """After a session: commit [data] when it is a data repository and LANI_DATA_AUTOCOMMIT isn't off, then push in the
    background when a remote is set. Never raises: the session is saved either way. Returns the commit's short hash."""
    data = Path(data)
    if not is_repo(data):
        return None
    if (settings_of(data, "LANI_DATA_AUTOCOMMIT") or "on").lower() in OFF:
        return None
    try:
        sha = commit(data, message)
        if sha:
            log(f"📚 data repository: {sha} {message.splitlines()[0]}")
            push_in_background(data)
        return sha
    except Exception as e:  # noqa: BLE001 — a commit that fails never fails the session
        log(f"data repository: not committed ({e})")
        return None


def _count(n: int, one: str, many: str) -> str:
    return f"{n} {one if n == 1 else many}"


def session_message(report: dict) -> str:
    """'session 2026-10-05: 12 reviews, 2 new words, 1 mistake', and a line with the session's id, its command and
    language, from an update-db.py report."""
    parts = []
    # a gentle lowering (a word got wrong in a dialog: update-db.py, lower_gently) is no review
    results = report.get("review_results") or []
    reviews = [r for r in results if not (isinstance(r, dict) and r.get("gentle") is True)]
    lowered = len(results) - len(reviews)
    words = report.get("new_vocabulary") or []
    errors = report.get("errors") or []
    if reviews:
        parts.append(_count(len(reviews), "review", "reviews"))
    if lowered:
        parts.append(_count(lowered, "card lowered", "cards lowered"))
    if words:
        parts.append(_count(len(words), "new word", "new words"))
    if errors:
        parts.append(_count(len(errors), "mistake", "mistakes"))
    if not parts:
        exercises = sum(int(s.get("exercises", 0) or 0) for s in (report.get("skill_scores") or {}).values()
                        if isinstance(s, dict))
        if exercises:
            parts.append(_count(exercises, "exercise", "exercises"))
        elif report.get("duration_minutes"):
            parts.append(f"{report['duration_minutes']} min")
    head = f"session {report.get('date', datetime.now().strftime('%Y-%m-%d'))}" + (f": {', '.join(parts)}" if parts else "")
    details = [str(x) for x in (report.get("session_id"), report.get("command_used"), report.get("language")) if x]
    return head + ("\n\n" + " · ".join(details) if details else "")


def files(data: Path) -> list:
    """(path, size) of every file the repository holds now (what a push sends), largest first."""
    data = Path(data)
    out = []
    for rel in git(data, "ls-files", "-z").stdout.split("\0"):
        if rel:
            with contextlib.suppress(OSError):
                out.append((rel, (data / rel).stat().st_size))
    return sorted(out, key=lambda x: -x[1])


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="The learner's data as a git repository of its own.")
    sub = ap.add_subparsers(dest="cmd", required=True)
    for name in ("init", "commit", "autocommit", "files"):
        p = sub.add_parser(name)
        p.add_argument("dir", nargs="?")
        if name != "files":
            p.add_argument("-m", "--message")
    sub.add_parser("gitignore")
    a = ap.parse_args(argv)
    if a.cmd == "gitignore":
        sys.stdout.write(GITIGNORE)
        return 0
    data = Path(a.dir).expanduser().resolve() if a.dir else lani_paths.data_dir()
    if a.cmd == "init":
        sha = init(data, a.message or "lani-setup: the learner's data")
        print(sha or "")
        return 0
    if not is_repo(data):
        print(f"{data} is not a data repository (companion/bin/lani-setup makes it one)", file=sys.stderr)
        return 2
    if a.cmd == "files":
        for rel, size in files(data):
            print(f"{size}\t{rel}")
        return 0
    if not a.message:
        print("-m: a commit message", file=sys.stderr)
        return 2
    sha = commit(data, a.message) if a.cmd == "commit" else autocommit(data, a.message)
    print(sha or "")
    return 0


if __name__ == "__main__":
    sys.exit(main())
