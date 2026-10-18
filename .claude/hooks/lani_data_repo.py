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
    lani_data_repo.py lease [DIR] [--json]      who writes the data now (the lease, lani_lease.py)
    lani_data_repo.py handover [DIR] [--device ID] [--name N] [--json]
                                                hand the data over to the phone: commit, offer the lease, push
    lani_data_repo.py take [DIR] [--force] [--json]
                                                take the data back: pull (fast-forward only), take the lease, push
    lani_data_repo.py push [DIR]                the push after a commit (what autocommit starts in the background)

The lease (lani_lease.py, docs/plans/05-built/git-sync.md): while another side holds it (the phone, after `lani
handover`), nothing is committed here; update-db.py and card.py refuse to write.

DIR defaults to the learner's data directory (lani_paths.data_dir). Settings (lani.env, the default learner's; the
environment wins): LANI_DATA_AUTOCOMMIT=off turns the commits after sessions off; LANI_DATA_REMOTE is the remote;
LANI_DATA_PUSH=off commits without pushing.
"""
from __future__ import annotations

import argparse
import contextlib
import fcntl
import json
import os
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lani_lease  # noqa: E402
import lani_paths  # noqa: E402

# The .gitignore lani-setup writes: one file, data-repo.gitignore next to this one, which the app reads too (it never
# pushes what the file keeps out: companion/android/.../sync/DataFilter.kt). Edit the file, not a copy.
GITIGNORE = (Path(__file__).resolve().parent / "data-repo.gitignore").read_text(encoding="utf-8")

# What never goes into a data repository, whatever its .gitignore says (the learner's own, or one written before a line
# was added to GITIGNORE): what the learner typed or said lately (the bridge's answers.json, companion/README.md "Recent
# answers"; docs/privacy.md says it stays on the machine), with its temporary files; the developer's data from the app
# (app/dev/: companion/README.md "Developer data on the node"); the tutor chat's photos (app/chat-images/: companion/
# README.md "Tutor chat"). Each line, with paths it must keep out (to check with git).
PRIVATE = (
    ("/app/answers.json*", ("app/answers.json", "app/answers.json.tmp-0-000000")),
    ("/app/dev/", ("app/dev/shadow-2026-01-01.json", "app/dev/app-log-2026-01-01.txt")),
    ("/app/chat-images/", ("app/chat-images/2026-01/0123456789abcdef0123456789abcdef.jpg",)),
)

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


def ignored(data: Path, rel: str) -> bool:
    """Whether git ignores [rel] in the repository [data] (its .gitignore, .git/info/exclude, the user's own excludes),
    tracked or not."""
    return git(data, "check-ignore", "-q", "--no-index", "--", rel, check=False).returncode == 0


def keep_private_out(data: Path) -> list:
    """Makes sure no PRIVATE file goes into the repository [data], idempotently: each line whose paths git doesn't ignore
    yet is added to the .gitignore (kept otherwise as the learner has it), and such a file committed before is no longer
    tracked (it stays on disk). Returns the lines added."""
    data = Path(data)
    missing = [line for line, paths in PRIVATE if not all(ignored(data, rel) for rel in paths)]
    if missing:
        p = data / ".gitignore"
        old = p.read_text(encoding="utf-8") if p.exists() else ""
        sep = "\n" if old and not old.endswith("\n") else ""
        p.write_text(old + sep + "\n# Private: stays on this machine (added by Lani, lani_data_repo.py PRIVATE).\n"
                     + "".join(f"{line}\n" for line in missing), encoding="utf-8")
        log(f"data repository: .gitignore now keeps out {', '.join(missing)}")
    for line, _ in PRIVATE:
        git(data, "rm", "-r", "-q", "--cached", "--ignore-unmatch", "--", line.lstrip("/"), check=False)
    return missing


def commit(data: Path, message: str, lease: bool = True) -> str | None:
    """Commits everything that changed in [data] (the .gitignore decides what counts; never a PRIVATE file). Returns the
    new commit's short hash, or None when nothing changed, the repository is busy, or (with [lease]) another side holds
    the lease (lani_lease.py: then nothing is committed here). A lease this node holds is renewed with the commit, once
    a day. [lease] False: the lease's own commits (handover, take)."""
    data = Path(data)
    if lease:
        refusal = lani_lease.node_refusal(data)
        if refusal:
            log(f"data repository: not committed ({message.splitlines()[0]}). {refusal}")
            return None
    with locked(data) as got:
        if not got:
            log(f"data repository busy: not committed now ({message}); the next commit takes it along")
            return None
        keep_private_out(data)
        git(data, "add", "-A")
        if git(data, "diff", "--cached", "--quiet", check=False).returncode == 0:
            return None
        if lease:
            again = lani_lease.renewed(lani_lease.read(data), lani_lease.this_node())
            if again:
                lani_lease.write(data, again)
                git(data, "add", "--", lani_lease.FILE)
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
        subprocess.Popen([sys.executable, str(Path(__file__).resolve()), "push", str(data)], env=git_env(),
                         stdin=subprocess.DEVNULL, stdout=f, stderr=f, start_new_session=True)
    return True


class LeaseError(RuntimeError):
    """A handover or a takeover that can't be done now: the message says why and what to do."""


def current_branch(data: Path) -> str:
    return git(data, "rev-parse", "--abbrev-ref", "HEAD").stdout.strip() or "main"


def origin_url(data: Path) -> str | None:
    p = git(data, "remote", "get-url", "origin", check=False)
    return (p.stdout.strip() or None) if p.returncode == 0 else None


def fetch(data: Path, timeout: int = 60) -> subprocess.CompletedProcess:
    """`git fetch origin`, tried again while a push of this repository's (in the background) holds the remote's ref."""
    for _ in range(20):
        p = git(data, "fetch", "-q", "origin", check=False, timeout=timeout)
        if p.returncode == 0 or "cannot lock ref" not in p.stderr:
            return p
        time.sleep(0.3)
    return p


def remote_lease(data: Path) -> dict | None:
    """The repository's lease (origin's branch, fetched now); None when it can't be fetched or has none."""
    if fetch(data).returncode != 0:
        return None
    p = git(data, "show", f"origin/{current_branch(data)}:{lani_lease.FILE}", check=False)
    return lani_lease.parse(p.stdout) if p.returncode == 0 else None


def push(data: Path, timeout: int = 120) -> bool:
    """`git push origin HEAD`, never forced (what autocommit starts in the background). A push the repository refuses
    (it went on without this node: the phone took the data over) fetches the repository's lease: when it names another
    side, it is kept in .git/lani-lease-remote.json, and this node stops writing (lani_lease.effective) until `lani
    handover --take`. Returns whether it pushed."""
    data = Path(data)
    note = data / ".git" / lani_lease.REMOTE_NOTE
    p = git(data, "push", "-q", "origin", "HEAD", check=False, timeout=timeout)
    if p.returncode == 0:
        note.unlink(missing_ok=True)
        return True
    sys.stderr.write(p.stderr)
    seen = remote_lease(data)
    if seen is not None and lani_lease.state(seen, lani_lease.this_node()) != "mine":
        note.write_text(lani_lease.dump(seen), encoding="utf-8")
        log(f"data repository: the push was refused: {lani_lease.describe(seen)}. This node stops writing "
            f"(lani handover --take takes the data back once it is handed over).")
    return False


def _sync_push(data: Path, what: str) -> None:
    p = git(data, "push", "-q", "origin", "HEAD", check=False, timeout=120)
    if p.returncode != 0:
        seen = remote_lease(data)
        why = f" The repository says: {lani_lease.describe(seen)}." if seen is not None else ""
        raise LeaseError(f"{what} is committed here, but the push failed: {p.stderr.strip() or 'no answer'}.{why} Run "
                         f"the command again once the repository can be reached.")


def _committed(data: Path, rel: str) -> bool:
    return not git(data, "status", "--porcelain", "--", rel).stdout.strip()


def handover(data: Path, device: str | None = None, name: str | None = None) -> dict:
    """Hands the data over to the phone (`lani handover`): commits what changed, writes the lease offered to the phone
    ([device], [name]: that phone only), commits it and pushes, synchronously. From then on this node only reads the
    data. Raises LeaseError when it can't (no remote, the lease isn't this node's, the push failed)."""
    data = Path(data)
    if not is_repo(data):
        raise LeaseError(f"{data} is not a data repository (companion/bin/lani-setup makes it one)")
    url = origin_url(data)
    if not url:
        remote = settings_of(data, "LANI_DATA_REMOTE")
        url = remote if remote and push_target(data, remote) else None
    if not url:
        raise LeaseError("the data repository has no remote (LANI_DATA_REMOTE, companion/bin/lani-setup): the phone "
                         "takes the data from a repository on GitHub")
    me = lani_lease.this_node()
    lease = lani_lease.effective(data)
    st = lani_lease.state(lease, me)
    if st not in ("mine", "offered-away"):
        raise LeaseError(f"this node doesn't hold the data: {lani_lease.describe(lease)}")
    if st == "mine":
        commit(data, "handover: the node's last state before the phone takes over")
    to = {"side": "phone", "device": (device or "").strip(), "name": (name or "").strip(), "mode": "hosted"}
    offer = lani_lease.offered(to, me)
    lani_lease.write(data, offer)
    commit(data, f"lease: handed over to {lani_lease.who(lani_lease.party(to))}", lease=False)
    if not _committed(data, lani_lease.FILE):
        raise LeaseError("the data repository is busy (a session is being saved): run the command again in a moment")
    _sync_push(data, "the handover")
    return {"lease": offer, "commit": git(data, "rev-parse", "--short", "HEAD").stdout.strip(), "remote": url,
            "branch": current_branch(data)}


def take(data: Path, force: bool = False) -> dict:
    """Takes the data back on this node (`lani handover --take`): fetches the repository and fast-forwards to it (never a
    merge: when the histories went apart, this node's commits are kept on a branch lani/node-<time> and nothing else
    changes), then takes the lease when it is this node's to take (handed over to it, free, run out, or its own offer),
    commits and pushes. [force]: also when another side holds it (the phone is lost; it stops writing at its next sync).
    Raises LeaseError when it can't."""
    data = Path(data)
    if not is_repo(data):
        raise LeaseError(f"{data} is not a data repository (companion/bin/lani-setup makes it one)")
    me = lani_lease.this_node()
    has_remote = origin_url(data) is not None
    if has_remote:
        f = fetch(data, timeout=120)
        if f.returncode != 0:
            raise LeaseError(f"can't reach the data repository: {f.stderr.strip() or 'no answer'}")
        theirs = f"origin/{current_branch(data)}"
        if git(data, "rev-parse", "-q", "--verify", theirs, check=False).returncode == 0:
            dirty = [line[3:] for line in git(data, "status", "--porcelain", "--untracked-files=no").stdout.splitlines()
                     if line.strip()]
            if dirty:
                raise LeaseError("these files changed here while the data wasn't this node's, and aren't committed: "
                                 f"{', '.join(dirty[:8])}{' …' if len(dirty) > 8 else ''}. Look at them (git -C {data} "
                                 f"diff), commit or restore them, then run the command again.")
            behind = git(data, "merge-base", "--is-ancestor", "HEAD", theirs, check=False).returncode == 0
            ahead = git(data, "merge-base", "--is-ancestor", theirs, "HEAD", check=False).returncode == 0
            if behind:
                m = git(data, "merge", "--ff-only", "-q", theirs, check=False)
                if m.returncode != 0:
                    raise LeaseError(f"the repository's state can't be checked out here: {m.stderr.strip()}")
            elif not ahead:
                side = f"lani/node-{datetime.now().strftime('%Y%m%d-%H%M%S')}"
                git(data, "branch", side)
                raise LeaseError(f"this node's history and the repository's went apart (both wrote the data). This "
                                 f"node's commits are kept on the branch {side}; nothing else changed. Compare them "
                                 f"(git -C {data} log --oneline {theirs}...{side}) and decide which to keep.")
    lease = lani_lease.read(data)
    st = lani_lease.state(lease, me)
    if st not in lani_lease.TAKEABLE and not force:
        raise LeaseError(f"{lani_lease.describe(lease)}. In the app, hand the data over first (Podatki · Data → Hand over "
                         f"to the node); if the phone is lost, take the data anyway: lani handover --take --force")
    if st == "mine" and not (data / ".git" / lani_lease.REMOTE_NOTE).exists() and lease is not None:
        return {"lease": lease, "commit": None, "already": True, "pushed": False}
    new = lani_lease.held(me)
    lani_lease.write(data, new)
    (data / ".git" / lani_lease.REMOTE_NOTE).unlink(missing_ok=True)
    how = "" if st in lani_lease.TAKEABLE else " (taken over without a handover)"
    commit(data, f"lease: {lani_lease.who(me)} takes the data{how}", lease=False)
    if not _committed(data, lani_lease.FILE):
        raise LeaseError("the data repository is busy (a session is being saved): run the command again in a moment")
    if has_remote:
        _sync_push(data, "the takeover")
    return {"lease": new, "commit": git(data, "rev-parse", "--short", "HEAD").stdout.strip(), "pushed": has_remote}


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
    head = f"session {report.get('date', lani_paths.now().strftime('%Y-%m-%d'))}" + (f": {', '.join(parts)}" if parts else "")
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
    for name in ("init", "commit", "autocommit", "files", "lease", "handover", "take", "push"):
        p = sub.add_parser(name)
        p.add_argument("dir", nargs="?")
        if name in ("init", "commit", "autocommit"):
            p.add_argument("-m", "--message")
        if name in ("lease", "handover", "take"):
            p.add_argument("--json", action="store_true", help="print the result as JSON")
        if name == "handover":
            p.add_argument("--device", help="hand over to this phone only (its id in the app)")
            p.add_argument("--name", help="the phone's name, as the lease shows it")
        if name == "take":
            p.add_argument("--force", action="store_true", help="also when another side holds the lease (it is lost)")
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
    if a.cmd == "push":
        return 0 if push(data) else 1
    if a.cmd == "lease":
        lease = lani_lease.effective(data)
        st = lani_lease.state(lease, lani_lease.this_node())
        if a.json:
            print(json.dumps({"lease": lease, "state": st, "node": lani_lease.this_node(),
                              "describe": lani_lease.describe(lease)}, ensure_ascii=False))
        else:
            print(lani_lease.describe(lease))
        return 0
    if a.cmd in ("handover", "take"):
        try:
            r = handover(data, a.device, a.name) if a.cmd == "handover" else take(data, a.force)
        except LeaseError as e:
            if a.json:
                print(json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False))
            else:
                print(f"[Lani] {e}", file=sys.stderr)
            return 1
        if a.json:
            print(json.dumps({"ok": True, **r}, ensure_ascii=False))
        else:
            print(lani_lease.describe(r["lease"]))
        return 0
    if not a.message:
        print("-m: a commit message", file=sys.stderr)
        return 2
    sha = commit(data, a.message) if a.cmd == "commit" else autocommit(data, a.message)
    print(sha or "")
    return 0


if __name__ == "__main__":
    sys.exit(main())
