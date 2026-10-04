"""
Lani language profiles: learning data per language (docs/DB_SCRIPTS.md, "Languages").

The home language, the target of the learner's own town, keeps its six databases in data/ as they
always were. Every other language the learner practises (e.g. on a visit to a town that speaks it)
has the same six files, in the same shapes, in data/languages/<code>/. learner-profile.json in data/
names the home language (`home_language`) and lists the others (`languages`: code, name, level,
source, since).

Data from before language profiles has no `home_language`: migrate() adds it (from the profile's
target language) and an empty `languages` list, after a backup. It runs whenever read-db.py or
update-db.py runs, and does nothing once the profile is migrated.
"""
from __future__ import annotations

import json
import os
import re
import shutil
import sys
from datetime import datetime
from pathlib import Path

# ISO 639-1 code -> the language's English name (the bridge's LANGUAGES, companion/bridge/src/learners.ts).
LANGUAGES = {
    "sl": "Slovene", "it": "Italian", "en": "English", "de": "German", "hr": "Croatian", "sr": "Serbian",
    "bs": "Bosnian", "fr": "French", "es": "Spanish", "pt": "Portuguese", "nl": "Dutch", "pl": "Polish",
    "cs": "Czech", "sk": "Slovak", "hu": "Hungarian", "uk": "Ukrainian", "ru": "Russian",
}
# Other names a profile may give: in the language itself, in Slovene, the other English spelling.
OTHER_NAMES = {
    "slovenian": "sl", "slovenščina": "sl", "slovensko": "sl", "italiano": "it", "italijanščina": "it",
    "deutsch": "de", "nemščina": "de", "angleščina": "en", "français": "fr", "español": "es",
    "hrvatski": "hr", "nederlands": "nl",
}
CODE = re.compile(r"^[a-z]{2,3}$")
CEFR = ("A1", "A2", "B1", "B2", "C1", "C2")

# The six databases, by the key update-db.py uses, and their file names (the same in every language's directory).
FILES = {
    "profile": "learner-profile.json",
    "progress": "progress-db.json",
    "mistakes": "mistakes-db.json",
    "mastery": "mastery-db.json",
    "sr": "spaced-repetition.json",
    "log": "session-log.json",
}


def language_code(s) -> str | None:
    """'it', 'IT', 'Italian' or 'italiano' -> 'it'; any other two- or three-letter code as it is; None otherwise
    (blank, a template placeholder such as '{LANGUAGE_YOU_WANT_TO_LEARN}', a name we don't know)."""
    if not isinstance(s, str):
        return None
    t = s.strip().lower()
    if not t or "{" in t:
        return None
    if CODE.match(t):
        return t
    for code, name in LANGUAGES.items():
        if name.lower() == t:
            return code
    return OTHER_NAMES.get(t)


def language_name(code: str) -> str:
    return LANGUAGES.get(code, code)


def home_language(profile: dict) -> str | None:
    """The home language's code: the profile's home_language, else its learner's target language."""
    if not isinstance(profile, dict):
        return None
    h = profile.get("home_language")
    if isinstance(h, str) and CODE.match(h):
        return h
    learner = profile.get("learner") if isinstance(profile.get("learner"), dict) else {}
    return language_code(learner.get("target_language_code")) or language_code(learner.get("target_language"))


def is_migrated(profile: dict) -> bool:
    """Whether the profile names its home language and lists the others (an empty list is fine)."""
    h = profile.get("home_language") if isinstance(profile, dict) else None
    return isinstance(h, str) and bool(CODE.match(h)) and isinstance(profile.get("languages"), list)


def language_dir(data_dir: Path, code: str) -> Path:
    return data_dir / "languages" / code


def listed(profile: dict) -> list:
    """The profile's other languages, as listed: entries with a valid code."""
    out = profile.get("languages") if isinstance(profile, dict) else None
    return [e for e in out if isinstance(e, dict) and isinstance(e.get("code"), str) and CODE.match(e["code"])] if isinstance(out, list) else []


def other_codes(data_dir: Path, profile: dict) -> list:
    """The other languages' codes: the profile's list, then any directory in data/languages/ it doesn't list."""
    codes = [e["code"] for e in listed(profile)]
    root = data_dir / "languages"
    if root.is_dir():
        for d in sorted(root.iterdir()):
            if d.is_dir() and CODE.match(d.name) and d.name not in codes and (d / FILES["profile"]).exists():
                codes.append(d.name)
    home = home_language(profile)
    return [c for c in dict.fromkeys(codes) if c != home]


def next_level(level: str) -> str:
    i = CEFR.index(level) if level in CEFR else 0
    return CEFR[min(i + 1, len(CEFR) - 1)]


def fresh_databases(code: str, date: str, level: str = "A1", learner_name: str = "", source: str = "visits") -> dict:
    """The six databases of a language practised for the first time, by update-db.py key; the shapes of data/'s.

    The profile holds only what is this language's (its level, streak, sessions, skills, achievements); read-db.py
    fills in the learner (name, base language, preferences) from the home profile.
    """
    name = language_name(code)
    return {
        "profile": {
            "learner": {"target_language": name, "target_language_code": code,
                        "current_level": level, "target_level": next_level(level)},
            "language": code,
            "source": source,
            "profile_created": date,
            "last_updated": "",  # no session yet: the first one starts the streak at 1
            "current_streak_days": 0,
            "total_sessions": 0,
            "total_study_minutes": 0,
            "skills": {},
            "focus_areas": [],
            "achievements": [],
        },
        "progress": {
            "metadata": {"last_updated": date, "language": name, "tracking_started": date},
            "overall_stats": {"total_sessions": 0, "total_exercises": 0, "total_correct": 0, "total_incorrect": 0,
                              "accuracy_rate": 0.0, "total_study_minutes": 0, "average_session_duration": 0},
            "accuracy_trend": [],
            "skill_progress": {},
            "weekly_summary": [],
        },
        "mistakes": {"metadata": {"last_updated": date, "total_patterns_tracked": 0, "language": name}, "error_patterns": {}},
        "mastery": {"metadata": {"last_updated": date, "language": name}, "skills": {}, "patterns": {}},
        "sr": {
            "metadata": {"algorithm": "SM-2", "last_updated": date, "total_items_tracked": 0, "language": name},
            "review_queue": {"today": [], "tomorrow": [], "this_week": [], "later": []},
            "items": {},
        },
        "log": {"metadata": {"language": name, "learner_name": learner_name, "total_sessions": 0}, "sessions": [], "milestones": []},
    }


def list_entry(profile: dict, code: str, lang_profile: dict, date: str) -> dict:
    """Adds [code] to the home profile's languages, or refreshes its entry: the level is a copy of the language's own
    learner-profile.json (current_level), which is where it is kept."""
    langs = profile.get("languages")
    if not isinstance(langs, list):
        langs = profile["languages"] = []
    entry = next((e for e in langs if isinstance(e, dict) and e.get("code") == code), None)
    if entry is None:
        entry = {"code": code, "name": language_name(code), "source": lang_profile.get("source", "visits"), "since": date}
        langs.append(entry)
    level = (lang_profile.get("learner") or {}).get("current_level")
    if isinstance(level, str) and level:
        entry["level"] = level
    return entry


# --- summaries (read-db.py) --------------------------------------------------------------------------------------

def _int(v) -> int:
    if isinstance(v, bool):
        return int(v)
    if isinstance(v, (int, float)):
        return int(round(v))
    try:
        return int(round(float(v)))
    except (TypeError, ValueError):
        return 0


def rusty(item: dict) -> bool:
    """Its last review failed (the app's Stats.rusty): not learned until a review gets it right."""
    return (_int(item.get("total_reviews")) >= 1 and _int(item.get("repetitions")) == 0 and "last_quality" in item
            and _int(item.get("last_quality")) < 3 and item.get("source") != "lookup")


def learned(item: dict) -> bool:
    """Answered right at least once and not failed since (the app's Stats.learned): what 'N words' counts."""
    return not rusty(item) and (
        _int(item.get("repetitions")) >= 1 or _int(item.get("mastery_level")) >= 1
        or (_int(item.get("total_reviews")) == 0 and _int(item.get("last_quality")) >= 3 and item.get("source") != "lookup"))


def summary(dbs: dict, code: str | None, source: str, today: str, since: str | None = None) -> dict:
    """One language at a glance: its level, words learned, cards due in its review deck (error patterns are drilled
    in modules, not reviewed), and where it comes from ('home': the town's; 'visits')."""
    profile = dbs.get("learner_profile") or {}
    items = ((dbs.get("spaced_repetition") or {}).get("items") or {})
    items = [i for i in items.values() if isinstance(i, dict)] if isinstance(items, dict) else []
    level = ((profile.get("learner") or {}).get("current_level")) or "A1"
    out = {
        "code": code,
        "name": language_name(code) if code else ((profile.get("learner") or {}).get("target_language") or ""),
        "level": level,
        "words": sum(1 for i in items if i.get("type") == "vocabulary" and learned(i)),
        "due": sum(1 for i in items if i.get("type") != "error_pattern" and (i.get("due_date") or i.get("next_review") or "9999") <= today),
        "source": source,
    }
    if since:
        out["since"] = since
    return out


# --- migration ---------------------------------------------------------------------------------------------------

def _save_json(path: Path, data: dict):
    tmp = path.with_suffix(".json.tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
        f.flush()
        os.fsync(f.fileno())
    os.replace(str(tmp), str(path))


def _backup_dir(backups: Path, tag: str) -> Path:
    """backups/<tag>, or <tag>-2, -3 … when that exists: a backup never overwrites an older one."""
    d, n = backups / tag, 1
    while d.exists():
        n += 1
        d = backups / f"{tag}-{n}"
    return d


def migrate(data_dir: Path, backups: Path) -> str:
    """Brings single-language data to language profiles: the profile gets `home_language` (its target language's
    code) and `languages` (the others: none yet). Backs up every database first (backups/pre-migrate-languages-<time>/).

    Returns 'migrated', 'already' (nothing to do: nothing read but the profile, nothing written), or 'skipped' (no
    profile, or its target language isn't known yet, e.g. a template's placeholder: nothing written).
    """
    path = data_dir / FILES["profile"]
    if not path.exists():
        return "skipped"
    with open(path, "r", encoding="utf-8") as f:
        profile = json.load(f)
    if not isinstance(profile, dict):
        return "skipped"
    if is_migrated(profile):
        return "already"
    home = home_language(profile)
    if not home:
        return "skipped"

    backup = _backup_dir(backups, "pre-migrate-languages-" + datetime.now().strftime("%Y%m%d-%H%M%S"))
    backup.mkdir(parents=True, exist_ok=True)
    for f in data_dir.glob("*.json"):
        shutil.copy2(f, backup / f.name)

    profile["home_language"] = home
    langs = listed(profile)
    for code in other_codes(data_dir, dict(profile, languages=langs)):
        if not any(e["code"] == code for e in langs):
            try:
                with open(language_dir(data_dir, code) / FILES["profile"], "r", encoding="utf-8") as f:
                    lp = json.load(f)
            except (OSError, ValueError):
                lp = {}
            lp = lp if isinstance(lp, dict) else {}
            entry = {"code": code, "name": language_name(code), "source": lp.get("source", "visits"),
                     "since": lp.get("profile_created") or datetime.now().strftime("%Y-%m-%d")}
            level = (lp.get("learner") or {}).get("current_level") if isinstance(lp.get("learner"), dict) else None
            if level:
                entry["level"] = level
            langs.append(entry)
    profile["languages"] = langs
    _save_json(path, profile)
    return "migrated"


def migrate_quietly(data_dir: Path, backups: Path) -> str:
    """migrate(), telling stderr what it did; a failure is a warning, never the caller's end (stdout stays theirs)."""
    try:
        r = migrate(data_dir, backups)
    except Exception as e:  # noqa: BLE001 - reading and writing must go on without it
        print(f"[Lani] Warning: language profiles migration failed, data left as it was: {e}", file=sys.stderr)
        return "failed"
    if r == "migrated":
        print("[Lani] Migrated the learner data to language profiles (home_language, languages); "
              "backup in .backups/pre-migrate-languages-*", file=sys.stderr)
    return r
