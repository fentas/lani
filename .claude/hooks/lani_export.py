#!/usr/bin/env python3
"""
A learner's data as one file, to move them between a node and the app (docs/plans/05-in-app-tutor.md, "Export and
import between modes").

An export (the format lani.export/v1) is a zip of the data directory's files, at their paths in it, and a manifest at
its root, lani-export.json: what made it and when, the learner (with "child": true for a child's: a child's profile, a
hosted phone set up "for a child"), the schema versions, and each file's size and SHA-256.
It holds what a commit of the data repository takes: everything but .git/ and what the .gitignore keeps out
(lani-setup's, lani_data_repo.GITIGNORE, when the directory has none); never the PRIVATE files or the secrets, whatever
the .gitignore says. With --with-secrets it holds the town's key and the family token too. The app reads and writes the
same format.

    lani_export.py export [--profile ID | --data DIR] [--out FILE] [--with-secrets] [--force]
    lani_export.py import FILE [--profile ID | --data DIR] [--yes] [--dry-run]
    lani_export.py verify FILE

companion/bin/lani-export and lani-import run these (`lani export`, `lani import`). An import checks the whole file
first (the paths, the sizes and hashes, the JSON, read-db.py on the databases) and changes nothing when it refuses.
Then it keeps what was there (a commit in a data repository, else a copy beside the data directory), replaces the
files and commits; a child's export makes the learner's registered profile a child's (never the other way round). What an export never holds stays as it is: the node's own secrets, the voice clips, the runtime
files.

The data is found as lani-backup finds it: --data, else --profile's (the registry), else LANI_DATA_DIR, else lani.env's
for the default learner, else the checkout's data/.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import socket
import stat
import subprocess
import sys
import tempfile
import time
import unicodedata
import zipfile
import zlib
from datetime import timezone
from pathlib import Path

HOOKS = Path(__file__).resolve().parent
REPO = HOOKS.parents[1]
sys.path.insert(0, str(HOOKS))
import lani_paths  # noqa: E402  (lani.env's settings; the default learner's data)
import lani_data_repo  # noqa: E402  (what a data repository keeps out; its commits)
import lani_languages as langs  # noqa: E402
import lani_lease  # noqa: E402  (which side writes the data now)

FORMAT = "lani.export/v1"
MANIFEST = "lani-export.json"
MAX_BYTES = 1 << 30
MAX_FILES = 50_000

# The secrets (lani-setup's .gitignore, "Secrets"): they prove this node, its town and the phones to each other. Never in
# an export, whatever a .gitignore says, but for these two with --with-secrets: the town's key (a node without one has
# the town's identity in its bridge key) and the family token.
SECRETS = ("/app/bridge-token", "/app/family-token", "/app/bridge-key.pem", "/app/town-key.pem", "*.pem", "*.key",
           "keys.env", "/app/pairing/", "/app/town-invites/")
TOWN_KEY, FAMILY_TOKEN, BRIDGE_KEY = "app/town-key.pem", "app/family-token", "app/bridge-key.pem"
WITH_SECRETS = (TOWN_KEY, FAMILY_TOKEN)

# This side's own files: an import keeps the data directory's as they are, and takes the export's never. The lease says
# which side writes this copy (lani_lease.py), the .gitignore what this side commits (another's could let the node's
# secrets into its history).
OWN = (".gitignore", "app/lease.json")


class Refused(Exception):
    """Why an export can't be made, read or imported: one line for the learner."""


# --- what an export holds ---------------------------------------------------------------------------------------------


def _regex(pat: str) -> str:
    """A .gitignore pattern as a regular expression: * and ? within a path part, ** across parts, \\x the x itself."""
    out, i = [], 0
    while i < len(pat):
        if pat.startswith("**/", i):
            out.append("(?:.*/)?")
            i += 3
        elif pat.startswith("**", i):
            out.append(".*")
            i += 2
        elif pat[i] == "*":
            out.append("[^/]*")
            i += 1
        elif pat[i] == "?":
            out.append("[^/]")
            i += 1
        elif pat[i] == "\\" and i + 1 < len(pat):
            out.append(re.escape(pat[i + 1]))
            i += 2
        else:
            out.append(re.escape(pat[i]))
            i += 1
    return "".join(out)


def parse_rules(text: str) -> list:
    """A .gitignore's patterns, in order, as (negated, directories only, anchored, regex): comments and blank lines
    skipped, ! re-includes, a trailing / takes directories only, a / before the end anchors the pattern at the root
    (else it matches a name at any depth). What lani-setup's .gitignore uses; no [] ranges."""
    out = []
    for line in text.splitlines():
        line = line.rstrip()
        if not line or line.startswith("#"):
            continue
        neg = line.startswith("!")
        line = line[1:] if neg else line
        dir_only = line.endswith("/")
        line = line.rstrip("/")
        if not line:
            continue
        anchored = "/" in line
        out.append((neg, dir_only, anchored, re.compile(_regex(line.lstrip("/")))))
    return out


def verdict(rules: list, rel: str, is_dir: bool):
    """True when [rules] keep the path [rel] out, False when a ! line takes it back in, None when no line matches."""
    name = rel.rsplit("/", 1)[-1]
    out = None
    for neg, dir_only, anchored, rx in rules:
        if (is_dir or not dir_only) and rx.fullmatch(rel if anchored else name):
            out = not neg
    return out


def ignored(rules: list, rel: str) -> bool:
    """Whether [rules] keep the file [rel] out: it, or a directory it is in (a file in a directory that is out can't be
    taken back in, as in git)."""
    parts = rel.split("/")
    return any(verdict(rules, "/".join(parts[:i]), True) for i in range(1, len(parts))) or bool(verdict(rules, rel, False))


ALWAYS_OUT = parse_rules("\n".join([line for line, _ in lani_data_repo.PRIVATE] + list(SECRETS)))


def kept_out(rel: str) -> bool:
    """Whether [rel] is never in an export: the manifest's own name, a .git, a PRIVATE file, a secret."""
    return rel == MANIFEST or ".git" in rel.split("/") or ignored(ALWAYS_OUT, rel)


def walk(data: Path, rules: list):
    """The files under [data] that [rules] don't keep out, but .git/ (a data directory that isn't a repository)."""
    for dirpath, dirnames, filenames in os.walk(data):
        base = Path(dirpath).relative_to(data).as_posix()
        pre = "" if base == "." else base + "/"
        dirnames[:] = sorted(d for d in dirnames if d != ".git" and not verdict(rules, pre + d, True))
        for f in sorted(filenames):
            if not verdict(rules, pre + f, False):
                yield pre + f


def files(data: Path) -> list:
    """What an export of [data] holds (but the secrets), sorted: the files a commit of the data repository takes. In a
    repository git decides (tracked or not ignored, still on disk); else the .gitignore's rules (lani-setup's when it has
    none). Never a PRIVATE file, a secret, a symbolic link."""
    data = Path(data)
    if not data.is_dir():
        return []
    if lani_data_repo.is_repo(data):
        try:
            p = lani_data_repo.git(data, "ls-files", "-z", "--cached", "--others", "--exclude-standard")
        except (RuntimeError, OSError, subprocess.SubprocessError) as e:
            raise Refused(f"git can't list the data repository's files: {e}") from None
        names = p.stdout.split("\0")
    else:
        own = data / ".gitignore"
        names = walk(data, parse_rules(own.read_text(encoding="utf-8") if own.is_file() else lani_data_repo.GITIGNORE))
    return sorted({rel for rel in names if rel and not kept_out(rel) and plain(data / rel)})


def plain(p: Path) -> bool:
    return p.is_file() and not p.is_symlink()


# --- the data, the learner ------------------------------------------------------------------------------------------


def registry() -> Path:
    """Where the learners on this machine are listed (profiles/profiles.json, or $LANI_PROFILES_DIR's)."""
    return Path(os.environ.get("LANI_PROFILES_DIR") or REPO / "profiles").expanduser().resolve()


def profile_data(pid: str) -> Path:
    """The data of another learner on this machine (companion/README.md, "Learners"), from the registry."""
    reg = registry()
    try:
        doc = json.loads((reg / "profiles.json").read_text(encoding="utf-8"))
        entry = next(e for e in doc.get("profiles", []) if e.get("id") == pid)
        return (reg / entry["data_dir"]).resolve()
    except (OSError, ValueError, StopIteration, AttributeError, KeyError, TypeError):
        raise Refused(f"no learner \"{pid}\" in {reg / 'profiles.json'} (lani profile list)") from None


def registry_entry(data: Path, profile: str | None = None) -> dict | None:
    """The registry's entry of the learner of [data]: [profile]'s, else the one whose data directory it is; None for the
    default learner (lani.env's) or data no entry has."""
    reg = registry()
    try:
        doc = json.loads((reg / "profiles.json").read_text(encoding="utf-8"))
        entries = [e for e in doc.get("profiles", []) if isinstance(e, dict)]
    except (OSError, ValueError, AttributeError):
        return None
    want = Path(data).expanduser().resolve()
    for e in entries:
        if profile and profile != "default" and e.get("id") == profile:
            return e
    for e in entries:
        try:
            if isinstance(e.get("data_dir"), str) and (reg / e["data_dir"]).resolve() == want:
                return e
        except (OSError, ValueError):
            continue
    return None


def child_of(data: Path, profile: str | None = None) -> bool:
    """Whether the learner of [data] is a child, as their bridge has it: their registry entry's child flag (lani profile
    add --child); the default learner's LANI_CHILD (the environment's, else lani.env's)."""
    entry = registry_entry(data, profile)
    if entry is not None:
        return entry.get("child") is True
    return (lani_paths.setting("LANI_CHILD") or "").strip().lower() in ("1", "true", "yes")


def is_child(m: dict) -> bool:
    """Whether the export [m] is a child's: its learner's "child" (a hosted phone's "for a child", a node's child profile)."""
    lr = m.get("learner") if isinstance(m.get("learner"), dict) else {}
    return lr.get("child") is True


def child_note(m: dict, data: Path, profile: str | None = None) -> str | None:
    """What an import of the child's export [m] into [data] does about it (None: not a child's, or this side knows)."""
    if not is_child(m):
        return None
    entry = registry_entry(data, profile)
    if entry is not None:
        if entry.get("child") is True:
            return None
        return f"The export is a child's: the profile {entry.get('id')} becomes a child's (lani profile show {entry.get('id')})."
    if Path(data).expanduser().resolve() != data_dir():
        return "⚠️  The export is a child's, and no learner here has this data directory: give it a child's profile (lani profile add … --child)."
    if child_of(data, profile):
        return None
    return "⚠️  The export is a child's: set LANI_CHILD=1 in lani.env (the default learner's), so the tutor and the bridge treat them as one."


def mark_child(data: Path, profile: str | None = None) -> str | None:
    """The registry entry of [data]'s learner marked a child's (an import of a child's export); what was done, or None."""
    entry = registry_entry(data, profile)
    if entry is None or entry.get("child") is True:
        return None
    path = registry() / "profiles.json"
    doc = json.loads(path.read_text(encoding="utf-8"))
    for e in doc.get("profiles", []):
        if isinstance(e, dict) and e.get("id") == entry.get("id"):
            e["child"] = True
    tmp = path.with_name(path.name + ".tmp")
    tmp.write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    os.replace(tmp, path)
    return f"{entry.get('id')} is a child's profile now, as the export says: restart its bridge and tutor session."


def data_dir(data: str | None = None, profile: str | None = None) -> Path:
    """--data, else --profile's, else LANI_DATA_DIR, else lani.env's for the default learner, else the checkout's data/."""
    if data:
        return Path(data).expanduser().resolve()
    if profile and profile != "default":
        return profile_data(profile)
    return Path(os.environ.get("LANI_DATA_DIR") or lani_paths.learner_setting("LANI_DATA_DIR") or REPO / "data").expanduser().resolve()


def read_json(path: Path):
    try:
        return json.loads(Path(path).read_bytes().decode("utf-8"))
    except (OSError, ValueError):
        return None


def _known(v):
    """[v] when it is a text that is set: not blank, not a template's placeholder such as "{YOUR_NAME}"."""
    return v.strip() if isinstance(v, str) and v.strip() and "{" not in v else None


def learner_of(profile) -> dict:
    """The manifest's learner: the name and the languages' codes of learner-profile.json (as the bridge reads them)."""
    lr = profile.get("learner") if isinstance(profile, dict) and isinstance(profile.get("learner"), dict) else {}
    return {
        "name": _known(lr.get("name")),
        "target": langs.language_code(lr.get("target_language_code")) or langs.language_code(lr.get("target_language")),
        "base": langs.language_code(lr.get("base_language_code"))
        or langs.language_code(lr.get("base_language") or lr.get("native_language")),
    }


def schema_of(rel: str, raw: bytes):
    """A JSON file's schema: its "schema" text (e.g. lani.towns/v1); app/game.json's is the number of its state."""
    if not rel.endswith(".json"):
        return None
    try:
        doc = json.loads(raw.decode("utf-8"))
    except ValueError:
        return None
    if not isinstance(doc, dict):
        return None
    if rel == "app/game.json" and isinstance(doc.get("state"), dict):
        s = doc["state"].get("schema")
        if isinstance(s, (int, float)) and not isinstance(s, bool):
            return s
    return doc["schema"] if isinstance(doc.get("schema"), str) else None


def version() -> str | None:
    """This checkout's version (git describe --always), or None."""
    try:
        p = lani_data_repo.git(REPO, "describe", "--always", check=False)
    except (OSError, subprocess.SubprocessError):
        return None
    return (p.stdout.strip() or None) if p.returncode == 0 else None


def slug(s) -> str:
    """"Ana Novák" -> "ana-novak": lowercase ASCII, for a file name."""
    t = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "-", t).strip("-")


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def size(n: int) -> str:
    return f"{n / 1e9:.1f} GB" if n >= 1e9 else f"{n / 1e6:.1f} MB" if n >= 1e6 else f"{n / 1e3:.1f} kB" if n >= 1e3 else f"{n} B"


# --- export -----------------------------------------------------------------------------------------------------------


def _info(name: str, mtime: float, mode: int) -> zipfile.ZipInfo:
    t = time.localtime(mtime)
    zi = zipfile.ZipInfo(name, date_time=(max(t.tm_year, 1980), *t[1:6]))
    zi.compress_type = zipfile.ZIP_DEFLATED
    zi.external_attr = (stat.S_IFREG | mode) << 16
    return zi


def default_out(data: Path, profile: str | None) -> Path:
    name = slug(learner_of(read_json(data / "learner-profile.json")).get("name")) or slug(profile) or "learner"
    return Path(f"lani-export-{name}-{lani_paths.now().strftime('%Y-%m-%d')}.zip")


def export(data: Path, out: Path, with_secrets: bool = False, profile: str | None = None, force: bool = False) -> dict:
    """Writes the export of [data] to [out] (mode 0600), checked as an import checks it. Returns its manifest."""
    data, out = Path(data), Path(out)
    if not (data / "learner-profile.json").is_file():
        raise Refused(f"no learner's data in {data} (no learner-profile.json)")
    if out.exists() and not force:
        raise Refused(f"{out} exists already (--force replaces it)")
    entries = [(rel, data / rel) for rel in files(data)]
    secrets = []
    if with_secrets:
        town = data / TOWN_KEY if plain(data / TOWN_KEY) else data / BRIDGE_KEY
        for rel, src in ((TOWN_KEY, town), (FAMILY_TOKEN, data / FAMILY_TOKEN)):
            if plain(src):
                entries.append((rel, src))
                secrets.append(rel)
    entries.sort()
    out.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix=".lani-export-", suffix=".tmp", dir=out.parent)  # 0600
    os.close(fd)
    try:
        listed, schemas = [], {}
        with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zf:
            for rel, src in entries:
                raw = src.read_bytes()  # read once: the hash is of what goes in
                listed.append({"path": rel, "size": len(raw), "sha256": hashlib.sha256(raw).hexdigest()})
                schema = schema_of(rel, raw)
                if schema is not None:
                    schemas[rel] = schema
                zf.writestr(_info(rel, src.stat().st_mtime, 0o600 if rel in secrets else 0o644), raw)
            now = lani_paths.now()
            manifest = {
                "format": FORMAT,
                "created": now.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                "from": {"side": "node", "mode": "self-hosted", "name": socket.gethostname(),
                         "profile": profile or "default", "version": version()},
                "learner": {**learner_of(read_json(data / "learner-profile.json")),
                            **({"child": True} if child_of(data, profile) else {})},
                "schemas": schemas,
                "files": listed,
                "secrets": secrets,
            }
            text = json.dumps(manifest, indent=2, ensure_ascii=False) + "\n"
            zf.writestr(_info(MANIFEST, now.timestamp(), 0o644), text.encode("utf-8"))
        with tempfile.TemporaryDirectory(prefix="lani-export-check-") as t:
            try:
                check(Path(tmp), Path(t) / "data")  # an export that wouldn't import is no use
            except Refused as e:
                raise Refused(f"the data in {data} wouldn't import as it is: {e}") from None
        os.chmod(tmp, 0o600)
        os.replace(tmp, out)
    except BaseException:
        Path(tmp).unlink(missing_ok=True)
        raise
    return manifest


# --- reading and checking an export -----------------------------------------------------------------------------------


def unsafe(name: str) -> bool:
    """Whether [name] isn't a plain path inside the data: absolute, .., a backslash, a NUL, a .git, an empty part."""
    return (not name or name.startswith("/") or "\\" in name or "\0" in name
            or any(p in ("", ".", "..", ".git") for p in name.split("/")))


def _listed(m: dict) -> dict:
    """The manifest's files by path; Refused when they aren't a list of {path, size, sha256}."""
    out = {}
    items = m.get("files")
    if not isinstance(items, list):
        raise Refused("its manifest has no list of files")
    for f in items:
        ok = (isinstance(f, dict) and isinstance(f.get("path"), str) and isinstance(f.get("size"), int)
              and not isinstance(f.get("size"), bool) and f["size"] >= 0 and isinstance(f.get("sha256"), str)
              and re.fullmatch(r"[0-9a-fA-F]{64}", f["sha256"]))
        if not ok:
            raise Refused(f"its manifest lists a file that isn't {{path, size, sha256}}: {json.dumps(f)[:80]}")
        if f["path"] in out:
            raise Refused(f"{f['path']}: listed twice in its manifest")
        out[f["path"]] = f
    return out


def manifest_of(zf: zipfile.ZipFile) -> dict:
    try:
        m = json.loads(zf.read(MANIFEST).decode("utf-8"))
    except (ValueError, zipfile.BadZipFile, zlib.error, EOFError, RuntimeError, NotImplementedError):
        raise Refused(f"its {MANIFEST} isn't JSON") from None
    if not isinstance(m, dict):
        raise Refused(f"its {MANIFEST} isn't a Lani export's manifest")
    fmt = m.get("format")
    if fmt != FORMAT:
        v = re.fullmatch(r"lani\.export/v([0-9]+)", fmt) if isinstance(fmt, str) else None
        if v and int(v.group(1)) > 1:
            raise Refused(f"made by a newer Lani ({fmt}): update this one first")
        raise Refused(f"not a Lani export (its format is {json.dumps(fmt)})")
    return m


def check(path: Path, into: Path) -> dict:
    """Reads the export [path] and unpacks it into [into] (a directory that doesn't exist yet), checking everything:
    the manifest, every entry's path, size and SHA-256, the limits, no private file or secret it doesn't name, the JSON,
    and read-db.py on the databases. Returns the manifest; raises Refused with the reason."""
    path, into = Path(path), Path(into)
    if not path.is_file():
        raise Refused(f"no file {path}")
    try:
        zf = zipfile.ZipFile(path)
    except (zipfile.BadZipFile, OSError):
        raise Refused("not a zip file, so not a Lani export") from None
    with zf:
        infos = [i for i in zf.infolist() if not i.is_dir()]
        if len(infos) > MAX_FILES + 1:
            raise Refused(f"over {MAX_FILES:,} files")
        names = [i.filename for i in infos]
        if MANIFEST not in names:
            raise Refused(f"no {MANIFEST} in it: not a Lani export")
        if len(set(names)) != len(names):
            raise Refused("an entry is in it twice")
        if zf.getinfo(MANIFEST).file_size > 64 << 20:
            raise Refused(f"its {MANIFEST} is too big for a manifest")
        m = manifest_of(zf)
        listed = _listed(m)
        if len(listed) > MAX_FILES:
            raise Refused(f"over {MAX_FILES:,} files")
        if sum(f["size"] for f in listed.values()) > MAX_BYTES:
            raise Refused("over 1 GiB of data")
        secrets = m.get("secrets", [])
        if not isinstance(secrets, list) or not all(isinstance(s, str) for s in secrets):
            raise Refused("its manifest's secrets aren't a list of paths")
        for s in secrets:
            if s not in WITH_SECRETS:
                raise Refused(f"{s}: not a secret an export may hold")
            if s not in listed:
                raise Refused(f"{s}: named a secret, but not in it")
        entries = {}
        for i in infos:
            n = i.filename
            if n == MANIFEST:
                continue
            if unsafe(n) or i.orig_filename != n:
                raise Refused(f"{json.dumps(i.orig_filename)}: not a path inside the data")
            if stat.S_ISLNK(i.external_attr >> 16):
                raise Refused(f"{n}: a symbolic link")
            if n not in listed:
                raise Refused(f"{n}: in the file but not in its manifest")
            entries[n] = i
        for p, f in listed.items():
            if unsafe(p):
                raise Refused(f"{json.dumps(p)}: not a path inside the data")
            if p not in entries:
                raise Refused(f"{p}: in its manifest but not in the file")
            if kept_out(p) and p not in secrets:
                raise Refused(f"{p}: never in an export (a private file, or a secret)")
            if entries[p].file_size != f["size"]:
                raise Refused(f"{p}: its size isn't the manifest's")
        into.mkdir(parents=True)
        for p in sorted(entries):
            unpack(zf, entries[p], listed[p], into / p)
    if "learner-profile.json" not in listed:
        raise Refused("no learner-profile.json in it: no learner's data")
    for p in sorted(listed):
        if p.endswith(".json") and read_json(into / p) is None:
            raise Refused(f"{p} isn't valid JSON")
    read_db(into, listed)
    return m


def unpack(zf: zipfile.ZipFile, info: zipfile.ZipInfo, f: dict, dest: Path) -> None:
    """One entry to [dest], its size and SHA-256 checked as it goes."""
    h, n = hashlib.sha256(), 0
    try:
        dest.parent.mkdir(parents=True, exist_ok=True)
        with zf.open(info) as src, open(dest, "xb") as out:
            while chunk := src.read(1 << 20):
                n += len(chunk)
                if n > f["size"]:
                    raise Refused(f"{f['path']}: its size isn't the manifest's")
                h.update(chunk)
                out.write(chunk)
    except (zipfile.BadZipFile, zlib.error, EOFError, RuntimeError, NotImplementedError) as e:
        raise Refused(f"{f['path']}: can't be read from the file ({e})") from None
    except OSError as e:
        raise Refused(f"{f['path']}: can't be unpacked ({e.strerror or e})") from None
    if n != f["size"] or h.hexdigest() != f["sha256"].lower():
        raise Refused(f"{f['path']}: its SHA-256 isn't the manifest's")


def read_db(into: Path, listed: dict) -> None:
    """read-db.py on a copy of the databases in [into] (its migration may write: never into what is imported)."""
    dbs = into.parent / "read-db"
    for p in listed:
        if (p.endswith(".json") and "/" not in p) or re.fullmatch(r"languages/[^/]+/[^/]+\.json", p):
            (dbs / p).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(into / p, dbs / p)
    try:
        r = subprocess.run([sys.executable, str(HOOKS / "read-db.py")], env={**os.environ, "LANI_DATA_DIR": str(dbs)},
                           capture_output=True, encoding="utf-8", errors="replace", timeout=120)
    except (OSError, subprocess.SubprocessError) as e:
        raise Refused(f"read-db.py didn't run: {e}") from None
    try:
        doc = json.loads(r.stdout)
    except ValueError:
        doc = None
    if r.returncode != 0 or not isinstance(doc, dict):
        why = (doc or {}).get("_warnings") if isinstance(doc, dict) else None
        why = "; ".join(why) if why else ((r.stderr or "").strip().splitlines() or [f"exit {r.returncode}"])[-1]
        raise Refused(f"its databases don't read (read-db.py): {why.replace(str(dbs) + os.sep, '')}")


def who(m: dict) -> str:
    """"the node jan-desktop (self-hosted)", "the phone Pixel 8 (hosted)"."""
    f = m.get("from") if isinstance(m.get("from"), dict) else {}
    name, mode = _known(f.get("name")), _known(f.get("mode"))
    return f"the {_known(f.get('side')) or 'unknown side'}" + (f" {name}" if name else "") + (f" ({mode})" if mode else "")


def learner_line(m: dict) -> str:
    lr = m.get("learner") if isinstance(m.get("learner"), dict) else {}
    target, base = _known(lr.get("target")), _known(lr.get("base"))
    codes = ", ".join(x for x in (target, f"from {base}" if base else None) if x)
    return ((_known(lr.get("name")) or "a learner without a name") + (f" ({codes})" if codes else "")
            + (", a child" if is_child(m) else ""))


def when(m: dict) -> str:
    c = m.get("created")
    return c.replace("T", " ").replace("Z", " UTC") if isinstance(c, str) else "an unknown time"


def summary(path: Path, m: dict) -> str:
    listed = _listed(m)
    schemas = m.get("schemas") if isinstance(m.get("schemas"), dict) else {}
    lines = [f"✓ {path.name}: a Lani export ({FORMAT})",
             f"  from:     {who(m)}, {when(m)}",
             f"  learner:  {learner_line(m)}",
             f"  files:    {len(listed)} ({size(sum(f['size'] for f in listed.values()))})",
             f"  schemas:  {', '.join(f'{k} {v}' for k, v in sorted(schemas.items())) or 'none'}"]
    if m.get("secrets"):
        lines.append(f"  secrets:  {', '.join(m['secrets'])}")
    return "\n".join(lines)


# --- import -----------------------------------------------------------------------------------------------------------


def plan(m: dict, unpacked: Path, data: Path) -> dict:
    """What an import of [m] (unpacked in [unpacked]) changes in [data]: new, changed, the same, removed (files an export
    of [data] holds now that this one doesn't), and the export's files of this side's own, kept as they are here."""
    listed = _listed(m)
    incoming = sorted(p for p in listed if p not in OWN)
    gone = sorted(set(files(data)) - set(incoming) - set(OWN))
    out = {"new": [], "changed": [], "same": [], "removed": gone, "kept": sorted(p for p in listed if p in OWN),
           "secrets": list(m.get("secrets") or [])}
    for p in incoming:
        dest = data / p
        for parent in list(Path(p).parents)[:-1]:
            here = data / parent
            if (here.exists() or here.is_symlink()) and not here.is_dir() and parent.as_posix() not in gone:
                raise Refused(f"{p}: {here} is a file here, not a folder")
        if dest.is_dir() and not dest.is_symlink():
            raise Refused(f"{p}: {dest} is a folder here")
        if not dest.is_file():
            out["new"].append(p)
        elif sha256_of(dest) != listed[p]["sha256"].lower():
            out["changed"].append(p)
        else:
            out["same"].append(p)
    return out


def _some(paths: list, mark: str, n: int = 10) -> list:
    return [f"  {mark} {p}" for p in paths[:n]] + ([f"    … and {len(paths) - n} more"] if len(paths) > n else [])


def describe_plan(path: Path, m: dict, data: Path, pl: dict, profile: str | None = None) -> str:
    kind = "a data repository" if lani_data_repo.is_repo(data) else "a data directory"
    lines = [f"{path.name}: {learner_line(m)}, from {who(m)}, made {when(m)}.",
             f"Into {data} ({kind}): {len(pl['new'])} new, {len(pl['changed'])} changed, {len(pl['removed'])} removed, "
             f"{len(pl['same'])} the same."]
    lines += _some(pl["new"], "+") + _some(pl["changed"], "~") + _some(pl["removed"], "-")
    if pl["secrets"]:
        lines.append(f"  Secrets, replacing this node's: {', '.join(pl['secrets'])}")
    if pl["kept"]:
        lines.append(f"  Kept as they are here (this side's own): {', '.join(pl['kept'])}")
    note = child_note(m, data, profile)
    if note:
        lines.append(f"  {note}")
    return "\n".join(lines)


def keep_before(data: Path) -> str | None:
    """Keeps the data as it is before an import: committed in a data repository, else copied (without .git) to
    <data>.before-import-<time> beside it. Returns where, for the learner; None when there was nothing to keep."""
    if lani_data_repo.is_repo(data):
        try:
            sha = lani_data_repo.commit(data, "lani import: the data before the import")
            dirty = lani_data_repo.git(data, "status", "--porcelain").stdout.strip()
            head = sha or lani_data_repo.git(data, "rev-parse", "--short", "HEAD", check=False).stdout.strip()
        except (RuntimeError, OSError, subprocess.SubprocessError) as e:
            raise Refused(f"the data before the import can't be committed: {e}") from None
        if dirty:
            raise Refused("the data repository is busy (another commit is running): try again in a moment")
        return f"The data before the import is in the repository's history ({head or 'no commit yet'})."
    if not data.is_dir() or not any(data.iterdir()):
        return None
    stamp = lani_paths.now().strftime("%Y%m%d-%H%M%S")
    dest, n = data.with_name(f"{data.name}.before-import-{stamp}"), 1
    while dest.exists():
        n += 1
        dest = data.with_name(f"{data.name}.before-import-{stamp}-{n}")
    shutil.copytree(data, dest, symlinks=True, ignore=shutil.ignore_patterns(".git"))
    os.chmod(dest, 0o700)  # tokens and keys are in it
    return f"The data before the import is in {dest}."


def put(src: Path, dest: Path, mode: int) -> None:
    """[src] as [dest], atomically: a temporary file in the same folder (*.tmp: never committed), renamed."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_name(f".{dest.name}.lani-import.tmp")
    tmp.unlink(missing_ok=True)
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(fd, "wb") as out, open(src, "rb") as f:
            shutil.copyfileobj(f, out, 1 << 20)
        os.chmod(tmp, mode)
        os.replace(tmp, dest)
    except BaseException:
        tmp.unlink(missing_ok=True)
        raise


def apply(pl: dict, unpacked: Path, data: Path) -> None:
    """Removes the files the export doesn't hold (and the folders they leave empty), then writes its files."""
    for p in pl["removed"]:
        (data / p).unlink(missing_ok=True)
        d = (data / p).parent
        while d != data and data in d.parents:
            try:
                d.rmdir()
            except OSError:
                break
            d = d.parent
    mask = os.umask(0)
    os.umask(mask)
    for p in pl["new"] + pl["changed"]:
        dest = data / p
        mode = 0o600 if p in pl["secrets"] else (stat.S_IMODE(dest.stat().st_mode) if dest.is_file() else 0o666 & ~mask)
        put(unpacked / p, dest, mode)
    for p in pl["secrets"]:
        os.chmod(data / p, 0o600)


def current_name(data: Path) -> str | None:
    return learner_of(read_json(data / "learner-profile.json")).get("name") if (data / "learner-profile.json").is_file() else None


def import_file(path: Path, data: Path, yes: bool = False, dry_run: bool = False, ask=input, profile: str | None = None) -> int:
    """Imports the export [path] into [data] (see the module's docstring): refused while the lease names another side
    (lani_lease.py). A child's export makes [data]'s registered profile ([profile]'s) a child's ([mark_child]). Returns the
    exit code; raises Refused before anything is changed."""
    path, data = Path(path), Path(data)
    try:
        tmp = Path(tempfile.mkdtemp(prefix=f".{data.name}.lani-import-", dir=data.parent))  # the same disk as the data
    except OSError:
        tmp = Path(tempfile.mkdtemp(prefix="lani-import-"))
    try:
        unpacked = tmp / "data"
        m = check(path, unpacked)
        refusal = lani_lease.node_refusal(data)  # the lease: another side writes the data now
        if refusal:
            raise Refused(refusal)
        pl = plan(m, unpacked, data)
        print(describe_plan(path, m, data, pl, profile))
        if dry_run:
            print("Nothing written (--dry-run).")
            return 0
        if not yes:
            if not sys.stdin.isatty():
                raise Refused("no terminal to ask on: run it in one, or pass --yes")
            name = current_name(data)
            q = (f"Replace {name}'s data with this export? [y/N] " if name
                 else f"Replace the data in {data} with this export? [y/N] " if (data / "learner-profile.json").is_file()
                 else f"Write this export into {data}? [y/N] ")
            try:
                answer = ask(q)
            except (EOFError, KeyboardInterrupt):
                answer = ""
            if answer.strip().lower() not in ("y", "yes"):
                print("Nothing changed.")
                return 1
        kept = keep_before(data)
        if kept:
            print(kept)
        data.mkdir(parents=True, exist_ok=True)
        apply(pl, unpacked, data)
        n = len(pl["new"]) + len(pl["changed"]) + len(pl["same"])
        if lani_data_repo.is_repo(data):
            f = m.get("from") if isinstance(m.get("from"), dict) else {}
            msg = f"lani import: {path.name} ({_known(f.get('side')) or '?'} {_known(f.get('name')) or '?'}, {when(m)})"
            try:
                sha = lani_data_repo.commit(data, msg)
            except (RuntimeError, OSError, subprocess.SubprocessError) as e:
                print(f"Imported {n} files, but not committed: {e}", file=sys.stderr)
                return 1
            if sha:
                print(f"Committed {sha}: {msg}")
        if is_child(m):
            try:
                marked = mark_child(data, profile)
            except (OSError, ValueError) as e:
                marked = f"⚠️  The export is a child's, but the profile couldn't be marked one: {e}"
            if marked:
                print(marked)
        print(f"Imported {n} files. Restart the tutor session or the bridge (lani bridge restart) so it reads them.")
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


# --- the command line -------------------------------------------------------------------------------------------------


def _who_args(ap: argparse.ArgumentParser) -> None:
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--profile", metavar="ID", help="another learner on this machine (lani profile list)")
    g.add_argument("--data", metavar="DIR", help="the data directory (default: the learner's, as the bridge finds it)")


def export_main(argv: list | None = None, prog: str = "lani export") -> int:
    ap = argparse.ArgumentParser(prog=prog, description=(
        "The learner's data as one file (a zip with a manifest), to move them to the app or to another node: "
        "what a commit of their data repository takes, without the secrets, the voice clips or what they typed lately."),
        epilog="Import it in the app, or on a node with `lani import FILE`. The format: .claude/hooks/lani_export.py.")
    _who_args(ap)
    ap.add_argument("--out", metavar="FILE", help="where to write it (default: ./lani-export-<name>-<date>.zip)")
    ap.add_argument("--with-secrets", action="store_true",
                    help="the town's key and the family token too, to move the town itself: keep the file private")
    ap.add_argument("--force", action="store_true", help="replace FILE when it exists")
    a = ap.parse_args(argv)
    lani_paths.force_utf8_io()
    try:
        data = data_dir(a.data, a.profile)
        out = Path(a.out).expanduser() if a.out else default_out(data, a.profile)
        m = export(data, out, a.with_secrets, a.profile, a.force)
    except Refused as e:
        print(f"{prog}: {e}", file=sys.stderr)
        return 1
    print(f"✓ Wrote {out}: {len(m['files'])} files ({size(sum(f['size'] for f in m['files']))}), "
          f"{size(out.stat().st_size)} packed.")
    if a.with_secrets:
        if m["secrets"]:
            print(f"⚠️  It holds the town's private key and the family token ({', '.join(m['secrets'])}): whoever has it "
                  "can be this town. Keep it private, and delete it once it is imported.", file=sys.stderr)
        else:
            print(f"⚠️  {data} has no town key and no family token: none in it.", file=sys.stderr)
    return 0


def import_main(argv: list | None = None, prog: str = "lani import") -> int:
    ap = argparse.ArgumentParser(prog=prog, description=(
        "A learner's data from an export (lani export, or the app's): checked first, then it replaces the data here. "
        "What was there is kept: a commit in a data repository, else a copy beside the data directory."),
        epilog="Exit codes: 0 done, 1 refused or failed (nothing changed when it refuses), 2 usage.")
    ap.add_argument("file", metavar="FILE", help="the export (a .zip)")
    _who_args(ap)
    ap.add_argument("--yes", "-y", action="store_true", help="don't ask")
    ap.add_argument("--dry-run", action="store_true", help="say what would change, and write nothing")
    ap.add_argument("--verify", action="store_true", help="only check FILE and say what it holds")
    a = ap.parse_args(argv)
    lani_paths.force_utf8_io()
    path = Path(a.file).expanduser()
    try:
        if a.verify:
            with tempfile.TemporaryDirectory(prefix="lani-import-check-") as t:
                print(summary(path, check(path, Path(t) / "data")))
            return 0
        return import_file(path, data_dir(a.data, a.profile), a.yes, a.dry_run, profile=a.profile)
    except Refused as e:
        print(f"{prog}: {e}", file=sys.stderr)
        return 1
    except OSError as e:
        print(f"{prog}: failed: {e}", file=sys.stderr)
        return 1


def main(argv: list | None = None) -> int:
    argv = sys.argv[1:] if argv is None else list(argv)
    cmd, rest = (argv[0], argv[1:]) if argv else ("", [])
    if cmd == "export":
        return export_main(rest, "lani_export.py export")
    if cmd == "import":
        return import_main(rest, "lani_export.py import")
    if cmd == "verify":
        return import_main(["--verify", *rest], "lani_export.py verify")
    print(__doc__.split("\n\n")[2], file=sys.stderr if cmd not in ("-h", "--help", "help") else sys.stdout)
    return 0 if cmd in ("-h", "--help", "help") else 2


if __name__ == "__main__":
    sys.exit(main())
