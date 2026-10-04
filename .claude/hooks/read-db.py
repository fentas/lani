#!/usr/bin/env python3
"""
Lani DB Reader Script
Loads all 6 learning databases and outputs a single JSON object to stdout.

Usage:
    python3 .claude/hooks/read-db.py                  # the home language (the learner's town's), as always
    python3 .claude/hooks/read-db.py --language it    # another language the learner practises (e.g. on visits)

Both print the same shape: {databases, computed, language, languages}. `languages` sums up every language the
learner has (the home one first); see docs/DB_SCRIPTS.md, "Languages". Data from before language profiles is
migrated first (lani_languages.migrate: backed up, then a no-op once done).

Exit codes: 0=success, 1=partial (some files missing), 2=critical error
"""
import argparse
import json
import re
import sys
from datetime import datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from lani_paths import backups_dir, data_dir, force_utf8_io  # noqa: E402
import lani_languages as langs  # noqa: E402

force_utf8_io()
DATA_DIR = data_dir()

# read-db.py's database keys -> update-db.py's (lani_languages.FILES): the same six files everywhere.
KEYS = {
    "learner_profile": "profile",
    "progress_db": "progress",
    "mistakes_db": "mistakes",
    "mastery_db": "mastery",
    "spaced_repetition": "sr",
    "session_log": "log",
}


def load_json(path: Path):
    if not path.exists():
        return None
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)


def next_session_id(sessions: list, code: str = None) -> str:
    """Produce 'session-NNN' matching existing id convention; another language's are 'session-<code>-NNN', so its
    backups and result files never meet the home language's.
    Falls back to 'session-001' on empty log or unparseable last id."""
    prefix = f"session-{code}-" if code else "session-"
    if not sessions:
        return f"{prefix}001"
    last_id = sessions[-1].get("session_id", "")
    m = re.search(r'(\d+)', last_id)
    if m:
        return f"{prefix}{int(m.group(1)) + 1:03d}"
    return f"{prefix}{len(sessions) + 1:03d}"


def load_dir(d: Path):
    """The six databases in [d] by read-db key, and the paths of those missing."""
    databases, missing = {}, []
    for key, k in KEYS.items():
        path = d / langs.FILES[k]
        data = load_json(path)
        if data is None:
            missing.append(str(path))
            databases[key] = {}
        else:
            databases[key] = data
    return databases, missing


def load_language(code: str, home_profile: dict, today: str) -> dict:
    """Another language's six databases; a language not practised yet reads as fresh, empty ones (nothing written).
    Its learner profile gets the learner's name, base language and preferences from the home profile."""
    d = langs.language_dir(DATA_DIR, code)
    fresh = langs.fresh_databases(code, today, learner_name=(home_profile.get("learner") or {}).get("name", ""))
    databases = {}
    for key, k in KEYS.items():
        data = load_json(d / langs.FILES[k])
        databases[key] = data if isinstance(data, dict) else fresh[k]
    own = databases["learner_profile"]
    merged = dict(own)
    merged["learner"] = {**(home_profile.get("learner") or {}), **(own.get("learner") or {})}
    if "preferences" not in own and "preferences" in home_profile:
        merged["preferences"] = home_profile["preferences"]
    databases["learner_profile"] = merged
    return databases


def computed(databases: dict, now: datetime, code: str = None) -> dict:
    today = now.strftime("%Y-%m-%d")
    yesterday = (now - timedelta(days=1)).strftime("%Y-%m-%d")

    sr = databases.get("spaced_repetition", {})
    items = sr.get("items", {})
    due_items = [iid for iid, item in items.items() if item.get("due_date", "") <= today]

    log = databases.get("session_log", {})
    sessions = log.get("sessions", [])

    profile = databases.get("learner_profile", {})
    last_updated = profile.get("last_updated", "")
    streak_active = last_updated in (today, yesterday)
    try:
        days_since = (now - datetime.strptime(last_updated, "%Y-%m-%d")).days if last_updated else None
    except ValueError:
        days_since = None

    return {
        "today": today,
        "due_reviews_count": len(due_items),
        "due_review_items": due_items,
        "next_session_id": next_session_id(sessions, code),
        "streak_active": streak_active,
        "days_since_last_session": days_since,
    }


def summaries(home_dbs: dict, home: str, today: str) -> list:
    """Every language the learner has: the home one first, then the others (level, words learned, due cards)."""
    profile = home_dbs.get("learner_profile", {})
    since = {e["code"]: e.get("since") for e in langs.listed(profile)}
    out = [langs.summary(home_dbs, home, "home", today)]
    for code in langs.other_codes(DATA_DIR, profile):
        dbs = load_language(code, profile, today)
        source = dbs["learner_profile"].get("source") or "visits"
        out.append(langs.summary(dbs, code, source, today, since.get(code) or dbs["learner_profile"].get("profile_created")))
    return out


def main():
    ap = argparse.ArgumentParser(description="Print the learner databases as one JSON object.")
    ap.add_argument("--language", help="a language code (it) or name (Italian); default: the home language")
    args = ap.parse_args()

    langs.migrate_quietly(DATA_DIR, backups_dir())

    databases, missing = load_dir(DATA_DIR)
    home_profile = databases.get("learner_profile", {})
    home = langs.home_language(home_profile)

    code = None
    if args.language is not None:
        code = langs.language_code(args.language)
        if code is None:
            print(f"[Lani] Error: --language: a language code (it) or name (Italian), not {args.language!r}", file=sys.stderr)
            sys.exit(2)
        if code == home:
            code = None

    now = datetime.now()
    today = now.strftime("%Y-%m-%d")
    home_dbs = databases
    if code is not None:
        # Another language's data: fresh when it has none yet, so nothing is "missing".
        databases, missing = load_language(code, home_profile, today), []

    result = {
        "databases": databases,
        "computed": computed(databases, now, code),
        "language": code or home,
        "languages": summaries(home_dbs, home, today),
    }

    if missing:
        result["_warnings"] = [f"Missing file: {m}" for m in missing]

    json.dump(result, sys.stdout, indent=2, ensure_ascii=False)
    print()

    sys.exit(1 if missing else 0)


if __name__ == "__main__":
    main()
