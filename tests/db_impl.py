"""
The learner-data scripts by either implementation: the Python (.claude/hooks/<script>.py, what a node runs) or its
TypeScript twin (companion/bridge/src/db/cli.ts, what the phone's bridge runs, here through bun), or both at once.

run(impl, script, args, stdin, env, cwd, root) runs one script:
  - "py": the Python;
  - "ts": the TypeScript;
  - "diff": both, on identical copies of [root] at the same path, one after the other. Their exit codes, stdout,
    stderr and every file under [root] afterwards must be the same, byte for byte (a Python traceback's frames can't
    be: there, the text before it and its last line); a difference fails the test with a diff. The Python's result is
    returned, and [root] is left as the Python left it.

Both get the same environment, with the clock frozen (LANI_NOW, unless the caller sets it) and lani.env out of the way
(LANI_ENV_FILE=/dev/null, unless set): the scripts read the time for backup names and "today".
"""
import difflib
import os
import shutil
import subprocess
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
HOOKS = REPO_ROOT / ".claude" / "hooks"
CLI = REPO_ROOT / "companion" / "bridge" / "src" / "db" / "cli.ts"
BUN = shutil.which("bun")
FROZEN_NOW = "2026-10-18T09:30:15"

needs_bun = unittest.skipUnless(BUN, "bun is not installed: the TypeScript port isn't run")


def command(impl: str, script: str, args=()) -> list:
    if impl == "py":
        return ["python3", str(HOOKS / f"{script}.py"), *map(str, args)]
    return [BUN, str(CLI), script, *map(str, args)]


def _run(impl, script, args, stdin, env, cwd):
    return subprocess.run(command(impl, script, args), input=stdin, env=env, cwd=str(cwd) if cwd else None,
                          capture_output=True)


def tree(root: Path) -> dict:
    """Every entry under [root]: a directory, a file's bytes, a link's target."""
    out = {}
    if not root.exists():
        return out
    for dirpath, dirnames, filenames in os.walk(root):
        for name in dirnames + filenames:
            p = Path(dirpath) / name
            rel = str(p.relative_to(root))
            if p.is_symlink():
                out[rel] = ("link", os.readlink(p))
            elif p.is_dir():
                out[rel] = ("dir",)
            else:
                out[rel] = ("file", p.read_bytes())
    return out


def _tree_diff(a: dict, b: dict) -> str:
    lines = []
    for rel in sorted(set(a) | set(b)):
        x, y = a.get(rel), b.get(rel)
        if x == y:
            continue
        if x is None or y is None:
            lines.append(f"  {rel}: only {'TypeScript' if x is None else 'Python'}")
        elif x[0] == "file" and y[0] == "file":
            d = difflib.unified_diff(x[1].decode(errors="replace").splitlines(), y[1].decode(errors="replace").splitlines(),
                                     "python/" + rel, "typescript/" + rel, lineterm="", n=2)
            lines.append("\n".join(list(d)[:60]))
        else:
            lines.append(f"  {rel}: {x[0]} (Python) vs {y[0]} (TypeScript)")
    return "\n".join(lines)


TRACEBACK = "Traceback (most recent call last):"


def _stderr_same(py: str, ts: str) -> bool:
    if TRACEBACK not in py:
        return py == ts
    if TRACEBACK not in ts:
        return False
    last = lambda s: [l for l in s.rstrip("\n").split("\n") if l][-1]  # noqa: E731
    return py.split(TRACEBACK)[0] == ts.split(TRACEBACK)[0] and last(py) == last(ts)


class Mismatch(AssertionError):
    pass


def compare(py, ts, py_tree, ts_tree, what: str):
    problems = []
    if py.returncode != ts.returncode:
        problems.append(f"exit code: Python {py.returncode}, TypeScript {ts.returncode}")
    if py.stdout != ts.stdout:
        d = difflib.unified_diff(py.stdout.decode(errors="replace").splitlines(), ts.stdout.decode(errors="replace").splitlines(),
                                 "python stdout", "typescript stdout", lineterm="", n=2)
        problems.append("stdout:\n" + "\n".join(list(d)[:80]))
    if not _stderr_same(py.stderr.decode(errors="replace"), ts.stderr.decode(errors="replace")):
        problems.append(f"stderr:\n--- python\n{py.stderr.decode(errors='replace')}\n--- typescript\n{ts.stderr.decode(errors='replace')}")
    if py_tree != ts_tree:
        problems.append("files:\n" + _tree_diff(py_tree, ts_tree))
    if problems:
        raise Mismatch(f"{what}: the Python and the TypeScript differ\n" + "\n".join(problems))


def frozen(env: dict) -> dict:
    env = dict(env)
    env.setdefault("LANI_NOW", FROZEN_NOW)
    env.setdefault("LANI_ENV_FILE", os.devnull)
    return env


def run(impl: str, script: str, args=(), stdin: bytes = b"", env: dict = None, cwd=None, root: Path = None):
    """One script by [impl] ("py", "ts", "diff"); [root] (for "diff"): the directory whose files it may change."""
    env = dict(os.environ if env is None else env)
    if impl != "diff":
        return _run(impl, script, args, stdin, env, cwd)
    env = frozen(env)
    root = Path(root)
    side = root.parent / f".{root.name}.db-port"
    shutil.rmtree(side, ignore_errors=True)
    side.mkdir()
    try:
        before = side / "before"
        had = root.exists()
        if had:
            shutil.copytree(root, before, symlinks=True)
        py = _run("py", script, args, stdin, env, cwd)
        py_tree = tree(root)
        if root.exists():
            os.rename(root, side / "python")
        if had:
            shutil.copytree(before, root, symlinks=True)
        ts = _run("ts", script, args, stdin, env, cwd)
        ts_tree = tree(root)
        shutil.rmtree(root, ignore_errors=True)
        if (side / "python").exists():
            os.rename(side / "python", root)
        compare(py, ts, py_tree, ts_tree, f"{script} {' '.join(map(str, args))}")
        return py
    finally:
        shutil.rmtree(side, ignore_errors=True)
