#!/usr/bin/env python3
"""
Lani DB Update Script
Updates all 6 learning databases from a single JSON session report via stdin.

Usage:
    python3 .claude/hooks/update-db.py <<'EOF'
    { "session_id": "session-005", "date": "2026-04-24", ... }
    EOF

See docs/DB_SCRIPTS.md for the full input schema. A report with "record_session": false
only adds new_vocabulary review items, without a session (no minutes, no streak day).

A report's "language" (a code or name; default: the home language, the learner's town's) says
whose data it goes to: another language's six databases are in data/languages/<code>/, created
the first time it is practised (docs/DB_SCRIPTS.md, "Languages"). Data from before language
profiles is migrated first (lani_languages.migrate: backed up, then a no-op once done).

Exit codes: 0=success, 1=validation error, 2=blocking/data error
"""
import copy
import json
import os
import re
import shutil
import sys
from datetime import datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from lani_paths import ensure_data_dir, ensure_backups_dir, force_utf8_io  # noqa: E402
import lani_languages as langs  # noqa: E402

force_utf8_io()
DATA_DIR = ensure_data_dir()
BACKUP_DIR = ensure_backups_dir()

# --- Utility functions ---

def load_json(path: Path) -> dict:
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)


def save_json(path: Path, data: dict):
    tmp_path = path.with_suffix('.json.tmp')
    with open(tmp_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write('\n')
        f.flush()
        os.fsync(f.fileno())
    os.replace(str(tmp_path), str(path))  # atomic + overwrites (os.rename fails on Windows if dest exists)


def parse_date(s: str) -> datetime:
    return datetime.strptime(s, "%Y-%m-%d")


def date_str(d: datetime) -> str:
    return d.strftime("%Y-%m-%d")


def tomorrow(today_str: str) -> str:
    return date_str(parse_date(today_str) + timedelta(days=1))


def yesterday(today_str: str) -> str:
    return date_str(parse_date(today_str) - timedelta(days=1))


def date_plus_days(today_str: str, days: int) -> str:
    return date_str(parse_date(today_str) + timedelta(days=days))


def get_week_start(today_str: str) -> str:
    d = parse_date(today_str)
    monday = d - timedelta(days=d.weekday())
    return date_str(monday)


def normalize_milestones(session: dict) -> list:
    """Validate + canonicalize session['milestones'] in place.

    Accepts each entry as either a bare string or an object
    {"milestone": <required non-empty str>, "date": <optional YYYY-MM-DD>}.
    Returns a list of canonical dicts and rewrites session['milestones'] to it.

    Decisions: a milestone's own date is honored (falling back to the session
    date if missing/blank/unparseable); the authoritative top-level session_id
    always wins (any nested session_id is ignored). Malformed entries exit 1
    (validation error) before any DB is touched.

    Each canonical dict carries a private '_achievement_id' key used by
    update_learner_profile; it is harmless because `session` is never persisted
    (only the 6 DBs are written).
    """
    raw = session.get("milestones", [])
    if not raw:
        session["milestones"] = []
        return []
    if not isinstance(raw, list):
        print(f"[Lani] Error: 'milestones' must be a list, got {type(raw).__name__}", file=sys.stderr)
        sys.exit(1)

    outer_date = session["date"]
    outer_sid = session["session_id"]
    normalized = []

    for i, ms in enumerate(raw):
        if isinstance(ms, str):
            text = ms.strip()
            if not text:
                print(f"[Lani] Error: milestone at index {i} is an empty string", file=sys.stderr)
                sys.exit(1)
            when = outer_date
        elif isinstance(ms, dict):
            text = ms.get("milestone")
            if not isinstance(text, str) or not text.strip():
                print(f"[Lani] Error: milestone at index {i} must have a non-empty string 'milestone' field", file=sys.stderr)
                sys.exit(1)
            text = text.strip()
            when = outer_date
            d = ms.get("date")
            if isinstance(d, str) and d.strip():
                try:
                    parse_date(d.strip())
                    when = d.strip()
                except (ValueError, TypeError):
                    when = outer_date  # malformed date falls back to session date
        else:
            print(f"[Lani] Error: milestone at index {i} must be a string or object, got {type(ms).__name__}", file=sys.stderr)
            sys.exit(1)

        slug = re.sub(r'[^a-z0-9]+', '_', text[:30].lower()).strip('_') or "milestone"
        normalized.append({
            "date": when,
            "milestone": text,
            "session_id": outer_sid,
            "_achievement_id": f"session_{outer_sid}_{i}_{slug}",
        })

    session["milestones"] = normalized
    return normalized


def backup_all(tag: str):
    backup_path = BACKUP_DIR / tag
    backup_path.mkdir(parents=True, exist_ok=True)
    for f in DATA_DIR.glob("*.json"):
        shutil.copy2(f, backup_path / f.name)


# --- Languages: the home language's data, or another's (lani_languages.py) ---

HOME_PROFILE = DATA_DIR / langs.FILES["profile"]


def load_home_profile() -> dict:
    """The home learner profile; {} when there is none (yet)."""
    if not HOME_PROFILE.exists():
        return {}
    p = load_json(HOME_PROFILE)
    return p if isinstance(p, dict) else {}


def report_language(report: dict):
    """The code of the language a report is in, or None for the home language (also when it names the home
    language). Exits 1 when "language" isn't a language."""
    raw = report.get("language")
    if raw is None or (isinstance(raw, str) and not raw.strip()):
        return None
    code = langs.language_code(raw)
    if code is None:
        print(f"[Lani] Error: 'language' must be a language code (it) or name (Italian), got {raw!r}", file=sys.stderr)
        sys.exit(1)
    try:
        home = langs.home_language(load_home_profile())
    except Exception as e:
        print(f"[Lani] Error loading {HOME_PROFILE.name}: {e}", file=sys.stderr)
        sys.exit(2)
    return None if code == home else code


def report_level(report: dict):
    """The report's "level" (A1..C2), which becomes its language's current level (a new one also sets level_since to
    the report's date); None when not given. Exits 1 when it isn't a CEFR level."""
    raw = report.get("level")
    if raw is None:
        return None
    level = raw.strip().upper() if isinstance(raw, str) else None
    if level not in langs.CEFR:
        print(f"[Lani] Error: 'level' must be a CEFR level (A1, A2, B1, B2, C1, C2), got {raw!r}", file=sys.stderr)
        sys.exit(1)
    return level


def paths_of(code) -> dict:
    """The six files of the home language (code None) or of another language."""
    d = DATA_DIR if code is None else langs.language_dir(DATA_DIR, code)
    return {k: d / name for k, name in langs.FILES.items()}


def load_databases(code, date: str) -> dict:
    """The six databases by key. The home language's must all exist; another language's that don't yet (it was
    never practised) are fresh ones, written with the report."""
    paths = paths_of(code)
    if code is None:
        return {k: load_json(p) for k, p in paths.items()}
    fresh = langs.fresh_databases(code, date, learner_name=(load_home_profile().get("learner") or {}).get("name", ""))
    return {k: (load_json(p) if p.exists() else fresh[k]) for k, p in paths.items()}


def backup_language(code: str, tag: str):
    """Before another language's write: its files (languages/<code>/) and the home profile (its list of languages)."""
    backup_path = BACKUP_DIR / tag
    backup_path.mkdir(parents=True, exist_ok=True)
    if HOME_PROFILE.exists():
        shutil.copy2(HOME_PROFILE, backup_path / HOME_PROFILE.name)
    src = langs.language_dir(DATA_DIR, code)
    if src.is_dir():
        dst = backup_path / "languages" / code
        dst.mkdir(parents=True, exist_ok=True)
        for f in src.glob("*.json"):
            shutil.copy2(f, dst / f.name)


def listed_in_home(code: str, lang_profile: dict, date: str):
    """The home profile with [code] in its languages (its level refreshed); None when there is no home profile."""
    home = load_home_profile()
    if not home:
        return None
    home = copy.deepcopy(home)
    langs.list_entry(home, code, lang_profile, date)
    return home


# --- SM-2 Algorithm ---

def calculate_sm2(item: dict, quality: int) -> dict:
    """Classic SM-2. Uses ceil() for interval growth (standard)."""
    import math
    ef = item.get("easiness_factor", 2.5)
    interval = item.get("interval_days", 1)
    reps = item.get("repetitions", 0)

    if quality >= 3:
        if reps == 0:
            interval = 1
        elif reps == 1:
            interval = 6
        else:
            interval = int(math.ceil(interval * ef))
        reps += 1
    else:
        reps = 0
        interval = 1

    ef = ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
    ef = max(1.3, ef)

    return {
        "easiness_factor": round(ef, 2),
        "interval_days": interval,
        "repetitions": reps,
    }


# --- Updater functions ---
# Each mutates in place. Confidence in learner-profile is 0-100 int.
# Session-log preserves the existing rich schema (skills_practiced array,
# score_breakdown, topics_covered, breakthroughs, focus_next_session,
# achievements_earned). Spaced-repetition preserves consecutive_*,
# mastery_level, total_reviews, priority, content, answer, category,
# difficulty fields on existing items.

def update_learner_profile(profile: dict, session: dict):
    today = session["date"]
    last = profile.get("last_updated", "")

    if last == today:
        pass
    elif last == yesterday(today):
        profile["current_streak_days"] = profile.get("current_streak_days", 0) + 1
    else:
        profile["current_streak_days"] = 1

    profile["last_updated"] = today
    profile["total_sessions"] = profile.get("total_sessions", 0) + 1
    profile["total_study_minutes"] = profile.get("total_study_minutes", 0) + session.get("duration_minutes", 0)

    for skill, scores in session.get("skill_scores", {}).items():
        skills = profile.setdefault("skills", {})
        s = skills.setdefault(skill, {
            "current_level": 0, "confidence": 0,
            "last_practiced": None, "total_practice_time": 0,
        })
        s["last_practiced"] = today
        s["total_practice_time"] = s.get("total_practice_time", 0) + scores.get("time_minutes", 0)
        if scores.get("exercises", 0) > 0:
            # confidence is 0-100 int; EWMA against session accuracy (0-100)
            new_acc_pct = (scores["correct"] / scores["exercises"]) * 100
            old_conf = s.get("confidence", 0)
            s["confidence"] = round(old_conf * 0.7 + new_acc_pct * 0.3)
        s["current_level"] = max(s.get("current_level", 0), 1)

    if session.get("focus_areas"):
        profile["focus_areas"] = session["focus_areas"]

    for m in session.get("milestones", []):
        profile.setdefault("achievements", []).append({
            "id": m["_achievement_id"],
            "name": m["milestone"],
            "earned_date": m["date"],
            "description": m["milestone"],
        })


def update_progress_db(progress: dict, session: dict):
    today = session["date"]
    skill_scores = session.get("skill_scores", {})
    total_ex = sum(s.get("exercises", 0) for s in skill_scores.values())
    total_cor = sum(s.get("correct", 0) for s in skill_scores.values())
    accuracy = round(total_cor / total_ex, 3) if total_ex > 0 else 0.0

    stats = progress.setdefault("overall_stats", {
        "total_sessions": 0, "total_exercises": 0, "total_correct": 0,
        "total_incorrect": 0, "accuracy_rate": 0.0,
        "total_study_minutes": 0, "average_session_duration": 0,
    })
    stats["total_sessions"] = stats.get("total_sessions", 0) + 1
    stats["total_exercises"] = stats.get("total_exercises", 0) + total_ex
    stats["total_correct"] = stats.get("total_correct", 0) + total_cor
    stats["total_incorrect"] = stats.get("total_incorrect", 0) + (total_ex - total_cor)
    stats["accuracy_rate"] = round(stats["total_correct"] / stats["total_exercises"], 3) if stats["total_exercises"] > 0 else 0.0
    stats["total_study_minutes"] = stats.get("total_study_minutes", 0) + session.get("duration_minutes", 0)
    stats["average_session_duration"] = round(stats["total_study_minutes"] / stats["total_sessions"])

    trend = progress.setdefault("accuracy_trend", [])
    # Dedup same-day entries: replace if present, else append
    existing = next((t for t in trend if t.get("date") == today), None)
    if existing is not None:
        existing["accuracy"] = accuracy
        existing["exercises"] = existing.get("exercises", 0) + total_ex
    else:
        trend.append({"date": today, "accuracy": accuracy, "exercises": total_ex})

    for skill, scores in skill_scores.items():
        sp = progress.setdefault("skill_progress", {}).setdefault(skill, {
            "sessions": 0, "accuracy": 0.0, "last_practiced": None,
            "exercises_completed": 0, "correct_count": 0, "incorrect_count": 0,
        })
        old_sessions = sp.get("sessions", 0)
        sp["sessions"] = old_sessions + 1
        new_acc = scores["correct"] / scores["exercises"] if scores.get("exercises", 0) > 0 else 0.0
        sp["accuracy"] = round(
            (sp.get("accuracy", 0.0) * old_sessions + new_acc) / sp["sessions"], 3
        )
        sp["last_practiced"] = today
        sp["exercises_completed"] = sp.get("exercises_completed", 0) + scores.get("exercises", 0)
        sp["correct_count"] = sp.get("correct_count", 0) + scores.get("correct", 0)
        sp["incorrect_count"] = sp.get("incorrect_count", 0) + (scores.get("exercises", 0) - scores.get("correct", 0))

    week_start = get_week_start(today)
    weekly = progress.setdefault("weekly_summary", [])
    week_entry = next((w for w in weekly if w.get("week_start") == week_start), None)
    if week_entry is None:
        week_entry = {"week_start": week_start, "sessions": 0, "total_minutes": 0, "accuracy": 0.0}
        weekly.append(week_entry)

    old_s = week_entry.get("sessions", 0)
    week_entry["sessions"] = old_s + 1
    week_entry["total_minutes"] = week_entry.get("total_minutes", 0) + session.get("duration_minutes", 0)
    week_entry["accuracy"] = round(
        (week_entry.get("accuracy", 0.0) * old_s + accuracy) / week_entry["sessions"], 3
    )

    progress.setdefault("metadata", {})["last_updated"] = today


def update_mistakes_db(mistakes: dict, session: dict):
    today = session["date"]
    patterns = mistakes.setdefault("error_patterns", {})

    for error in session.get("errors", []):
        pid = error["pattern_id"]

        if pid in patterns:
            pat = patterns[pid]
            pat["frequency"] = pat.get("frequency", 0) + 1
            pat["last_seen"] = today
            pat["last_occurred"] = today  # legacy alias kept
            pat["next_review"] = tomorrow(today)
            pat["consecutive_incorrect"] = pat.get("consecutive_incorrect", 0) + 1
            pat["consecutive_correct"] = 0
            pat.setdefault("examples", []).append({
                "incorrect": error.get("your_answer", ""),
                "correct": error.get("correct_answer", ""),
                "context": error.get("context", ""),
                "date": today,
            })
            pat["examples"] = pat["examples"][-5:]
            if error.get("notes"):
                pat["notes"] = error["notes"]
        else:
            patterns[pid] = {
                "category": error.get("category", "other"),
                "subcategory": error.get("subcategory", ""),
                "description": error.get("description", ""),
                "severity": error.get("severity", "minor"),
                "frequency": 1,
                "mastery_level": 0,
                "difficulty_score": error.get("difficulty_score", 0.5),
                "last_seen": today,
                "last_occurred": today,
                "next_review": tomorrow(today),
                "consecutive_correct": 0,
                "consecutive_incorrect": 1,
                "examples": [{
                    "incorrect": error.get("your_answer", ""),
                    "correct": error.get("correct_answer", ""),
                    "context": error.get("context", ""),
                    "date": today,
                }],
                "notes": error.get("notes", ""),
            }

    mistakes.setdefault("metadata", {})["last_updated"] = today
    mistakes["metadata"]["total_patterns_tracked"] = len(patterns)


def update_mastery_db(mastery: dict, session: dict, progress: dict):
    today = session["date"]

    for skill, scores in session.get("skill_scores", {}).items():
        s = mastery.setdefault("skills", {}).setdefault(skill, {
            "mastery_level": 0, "confidence_score": 0.0,
            "total_practice_time": 0, "last_practiced": None,
            "practice_count": 0, "avg_accuracy": 0.0,
        })
        s["last_practiced"] = today
        s["total_practice_time"] = s.get("total_practice_time", 0) + scores.get("time_minutes", 0)
        s["practice_count"] = s.get("practice_count", 0) + scores.get("exercises", 0)

        sp = progress.get("skill_progress", {}).get(skill, {})
        acc = sp.get("accuracy", 0)
        sessions = sp.get("sessions", 0)
        s["confidence_score"] = round(acc, 3)
        s["avg_accuracy"] = round(acc, 3)

        if sessions == 0:
            s["mastery_level"] = 0
        elif sessions < 3 or acc < 0.5:
            s["mastery_level"] = max(s.get("mastery_level", 0), 1)
        elif sessions < 5 or acc < 0.65:
            s["mastery_level"] = max(s.get("mastery_level", 0), 2)
        elif sessions < 10 or acc < 0.8:
            s["mastery_level"] = max(s.get("mastery_level", 0), 3)
        elif sessions < 20 or acc < 0.9:
            s["mastery_level"] = max(s.get("mastery_level", 0), 4)
        else:
            s["mastery_level"] = 5

    mastery.setdefault("metadata", {})["last_updated"] = today


def update_spaced_repetition(sr: dict, session: dict):
    today = session["date"]
    items = sr.setdefault("items", {})

    for review in session.get("review_results", []):
        item_id = review["item_id"]
        quality = review["quality"]
        if item_id in items:
            item = items[item_id]
            result = calculate_sm2(item, quality)
            item["easiness_factor"] = result["easiness_factor"]
            item["interval_days"] = result["interval_days"]
            item["repetitions"] = result["repetitions"]
            item["due_date"] = date_plus_days(today, result["interval_days"])
            item["last_reviewed"] = today
            item["last_quality"] = quality
            item["total_reviews"] = item.get("total_reviews", 0) + 1
            if quality >= 3:
                item["consecutive_correct"] = item.get("consecutive_correct", 0) + 1
                item["consecutive_incorrect"] = 0
            else:
                item["consecutive_incorrect"] = item.get("consecutive_incorrect", 0) + 1
                item["consecutive_correct"] = 0
            # Mastery: rough map from repetitions and quality (clamped 0..5)
            current = item.get("mastery_level", 0)
            if item["repetitions"] >= 5 and item["consecutive_correct"] >= 3:
                item["mastery_level"] = min(5, max(current, 3))
            elif item["repetitions"] >= 2 and item["consecutive_correct"] >= 1 and quality >= 4:
                item["mastery_level"] = min(5, current + 1)
            # priority heuristic
            if item.get("consecutive_incorrect", 0) >= 2:
                item["priority"] = "high"
            elif item.get("mastery_level", 0) >= 3:
                item["priority"] = "low"
            else:
                item["priority"] = item.get("priority", "medium")
            item.setdefault("review_history", []).append({
                "date": today,
                "quality": quality,
                "score": review.get("score", 0),
            })

    for vocab in session.get("new_vocabulary", []):
        item_id = vocab["item_id"]
        if item_id not in items:
            items[item_id] = {
                "id": item_id,
                "type": vocab.get("item_type", "vocabulary"),
                "content": vocab.get("content", ""),
                "answer": vocab.get("answer", ""),
                "category": vocab.get("category", ""),
                "difficulty": vocab.get("difficulty", ""),
                "created_date": today,
                "due_date": tomorrow(today),
                "interval_days": 1,
                "repetitions": 0,
                "easiness_factor": 2.5,
                "consecutive_correct": 0,
                "consecutive_incorrect": 0,
                "last_reviewed": today,
                "last_quality": vocab.get("initial_quality", 3),
                "mastery_level": 0,
                "total_reviews": 0,
                "priority": vocab.get("priority", "medium"),
            }
            # Where the word was met (a sentence and its translation), and where the item came from
            # ("lookup": added from the app's word card, never answered), when the caller knows it.
            for k in ("example", "example_translation", "source"):
                if isinstance(vocab.get(k), str) and vocab[k].strip():
                    items[item_id][k] = vocab[k].strip()

    for error in session.get("errors", []):
        item_id = error["pattern_id"]
        if item_id not in items:
            items[item_id] = {
                "id": item_id,
                "type": "error_pattern",
                "content": error.get("your_answer", ""),
                "answer": error.get("correct_answer", ""),
                "category": error.get("category", ""),
                "difficulty": "",
                "created_date": today,
                "due_date": tomorrow(today),
                "interval_days": 1,
                "repetitions": 0,
                "easiness_factor": 2.5,
                "consecutive_correct": 0,
                "consecutive_incorrect": 1,
                "last_reviewed": today,
                "last_quality": 2,
                "mastery_level": 0,
                "total_reviews": 0,
                "priority": "high",
            }

    # Rebuild review queue
    sr["review_queue"] = {"today": [], "tomorrow": [], "this_week": [], "later": []}
    tom = tomorrow(today)
    week_end = date_plus_days(today, 7)
    for item_id, item in items.items():
        due = item.get("due_date", today)
        if due <= today:
            sr["review_queue"]["today"].append(item_id)
        elif due == tom:
            sr["review_queue"]["tomorrow"].append(item_id)
        elif due <= week_end:
            sr["review_queue"]["this_week"].append(item_id)
        else:
            sr["review_queue"]["later"].append(item_id)

    sr.setdefault("metadata", {})["last_updated"] = today
    sr["metadata"]["total_items_tracked"] = len(items)


def update_session_log(log: dict, session: dict, streak: int):
    """Matches existing schema: skills_practiced (array), score_breakdown,
    topics_covered, breakthroughs, focus_next_session, achievements_earned."""
    today = session["date"]
    skill_scores = session.get("skill_scores", {})
    total_ex = sum(s.get("exercises", 0) for s in skill_scores.values())
    total_cor = sum(s.get("correct", 0) for s in skill_scores.values())

    score_breakdown = {
        skill: round(s["correct"] / s["exercises"], 3) if s.get("exercises", 0) > 0 else 0.0
        for skill, s in skill_scores.items()
    }

    entry = {
        "session_id": session["session_id"],
        "date": today,
        "duration_minutes": session.get("duration_minutes", 0),
        "skills_practiced": session.get("skills_practiced", list(skill_scores.keys())),
        "command_used": session.get("command_used", "/lani-learn"),
        "exercises_completed": total_ex,
        "accuracy": round(total_cor / total_ex, 3) if total_ex > 0 else 0.0,
        "score_breakdown": score_breakdown,
        "topics_covered": session.get("topics_covered", []),
        "breakthroughs": session.get("breakthroughs", []),
        "focus_next_session": session.get("focus_next_session", session.get("focus_areas", [])),
        "notes": session.get("session_notes", ""),
        "achievements_earned": session.get("achievements_earned", []),
        "streak_day": streak,
    }
    if session.get("exam_focus"):
        entry["exam_focus"] = session["exam_focus"]
    if session.get("critical_errors_identified"):
        entry["critical_errors_identified"] = session["critical_errors_identified"]

    log.setdefault("sessions", []).append(entry)

    for m in session.get("milestones", []):
        log.setdefault("milestones", []).append({
            "date": m["date"],
            "milestone": m["milestone"],
            "session_id": m["session_id"],
        })

    log.setdefault("metadata", {})["total_sessions"] = len(log["sessions"])


# --- Review items without a session ---

# What describes practice: a report that records no session must not carry any of it.
SESSION_FIELDS = ("duration_minutes", "skill_scores", "review_results", "errors", "milestones",
                  "skills_practiced", "topics_covered", "breakthroughs", "achievements_earned", "level")


def add_items_only(report: dict):
    """record_session: false — add the new_vocabulary review items and nothing else.

    A word met outside practice (looked up in a dialog of the app) becomes a review item
    without a session: no session-log entry, no minutes, no exercises, no streak day. Only
    spaced-repetition.json is written, after a backup (one per day: the state before the
    day's latest write). Items that exist already are left as they are. In another language
    ("language"), its spaced-repetition.json; the first word of a language never practised
    creates its six databases and lists it in the home profile.
    """
    try:
        parse_date(str(report.get("date", "")))
    except ValueError:
        print("[Lani] Error: 'date' must be YYYY-MM-DD", file=sys.stderr)
        sys.exit(1)
    practice = [k for k in SESSION_FIELDS if report.get(k)]
    if practice:
        print(f"[Lani] Error: a report with record_session false adds review items only; remove {', '.join(practice)}", file=sys.stderr)
        sys.exit(1)
    vocab = report.get("new_vocabulary")
    if not isinstance(vocab, list) or not vocab or not all(
            isinstance(v, dict) and isinstance(v.get("item_id"), str) and v["item_id"].strip() for v in vocab):
        print("[Lani] Error: 'new_vocabulary' must be a non-empty list of items with an item_id", file=sys.stderr)
        sys.exit(1)
    code = report_language(report)

    paths = paths_of(code)
    path = paths["sr"]
    new_language = code is not None and not paths["profile"].exists()
    try:
        databases = load_databases(code, report["date"]) if new_language else {"sr": load_json(path)}
    except Exception as e:
        print(f"[Lani] Error loading {path.name}: {e}", file=sys.stderr)
        sys.exit(2)
    original = databases["sr"]
    sr = copy.deepcopy(original)
    try:
        update_spaced_repetition(sr, {"date": report["date"], "new_vocabulary": vocab})
        home = listed_in_home(code, databases["profile"], report["date"]) if new_language else None
    except Exception as e:
        import traceback
        print(f"[Lani] Error updating {path.name}: {e}", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(2)
    added = [i for i in dict.fromkeys(v["item_id"] for v in vocab) if i not in original.get("items", {})]
    if added:
        if code is None:
            backup = BACKUP_DIR / f"pre-words-{report['date']}"
            backup.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, backup / path.name)
        else:
            backup_language(code, f"pre-words-{code}-{report['date']}")
        try:
            if new_language:
                path.parent.mkdir(parents=True, exist_ok=True)
                for k, p in paths.items():
                    save_json(p, sr if k == "sr" else databases[k])
                if home is not None:
                    save_json(HOME_PROFILE, home)
            else:
                save_json(path, sr)
        except Exception as e:
            print(f"[Lani] Error saving {path.name}: {e}", file=sys.stderr)
            sys.exit(2)
    where = f" in {langs.language_name(code)}" if code else ""
    print(f"[Lani] ✅ Added {len(added)} review item(s){where} ({', '.join(added) or 'all known already'}); no session recorded")
    print(f"[Lani] 🧠 SR: {sr['metadata']['total_items_tracked']} items tracked")
    sys.exit(0)


# --- Main ---

def main():
    try:
        session = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        print(f"[Lani] Error: Invalid JSON input: {e}", file=sys.stderr)
        sys.exit(1)
    if not isinstance(session, dict):
        print("[Lani] Error: the report must be a JSON object", file=sys.stderr)
        sys.exit(1)

    # Data from before language profiles: backed up and migrated once, then this does nothing.
    langs.migrate_quietly(DATA_DIR, BACKUP_DIR)

    if session.get("record_session") is False:
        add_items_only(session)

    for field in ("session_id", "date"):
        if field not in session:
            print(f"[Lani] Error: Missing required field '{field}'", file=sys.stderr)
            sys.exit(1)

    # Validate + canonicalize milestones before touching any DB (exits 1 on
    # malformed input, so disk stays untouched on a validation failure).
    normalize_milestones(session)
    level = report_level(session)
    # The home language's six databases (data/), or another language's (data/languages/<code>/).
    code = report_language(session)

    session.setdefault("duration_minutes", 0)

    files = paths_of(code)

    try:
        originals = load_databases(code, session["date"])
    except Exception as e:
        print(f"[Lani] Error loading databases: {e}", file=sys.stderr)
        sys.exit(2)

    # Work on deep copies so a mid-run exception leaves disk untouched.
    data = {k: copy.deepcopy(v) for k, v in originals.items()}

    try:
        if level:
            learner = data["profile"].setdefault("learner", {})
            # A new level is reached on the report's day; the same level again keeps its day.
            if learner.get("current_level") != level:
                learner["level_since"] = session["date"]
            learner["current_level"] = level
        update_learner_profile(data["profile"], session)
        update_progress_db(data["progress"], session)
        update_mistakes_db(data["mistakes"], session)
        update_mastery_db(data["mastery"], session, data["progress"])
        update_spaced_repetition(data["sr"], session)
        streak = data["profile"].get("current_streak_days", 0)
        update_session_log(data["log"], session, streak)
        # Another language is listed in the home profile, with its level.
        home = listed_in_home(code, data["profile"], session["date"]) if code else None
    except Exception as e:
        import traceback
        print(f"[Lani] Error updating databases: {e}", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(2)

    # Backup originals BEFORE writing new state.
    if code is None:
        backup_all(f"pre-update-{session['session_id']}")
    else:
        backup_language(code, f"pre-update-{code}-{session['session_id']}")

    try:
        files["profile"].parent.mkdir(parents=True, exist_ok=True)
        for k, p in files.items():
            save_json(p, data[k])
        if home is not None:
            save_json(HOME_PROFILE, home)
    except Exception as e:
        print(f"[Lani] Error saving databases: {e}", file=sys.stderr)
        sys.exit(2)

    # Summary
    stats = data["progress"]["overall_stats"]
    sr_tomorrow = len(data["sr"]["review_queue"].get("tomorrow", []))
    skill_scores = session.get("skill_scores", {})
    total_ex = sum(s.get("exercises", 0) for s in skill_scores.values())
    total_cor = sum(s.get("correct", 0) for s in skill_scores.values())

    where = f" ({langs.language_name(code)}: {files['profile'].parent})" if code else ""
    print(f"[Lani] ✅ Updated 6 databases for session {session['session_id']}{where}")
    print(f"[Lani] 🔥 Streak: {streak} days | Sessions: {stats['total_sessions']} | Minutes: {stats['total_study_minutes']}")
    if total_ex > 0:
        print(f"[Lani] 📊 This session: {total_cor}/{total_ex} correct ({round(total_cor/total_ex*100)}%)")
    else:
        print("[Lani] 📊 No exercises recorded")
    print(f"[Lani] 📈 Overall accuracy: {stats['accuracy_rate']*100:.0f}% ({stats['total_exercises']} exercises)")
    print(f"[Lani] 🧠 SR: {data['sr']['metadata']['total_items_tracked']} items tracked, {sr_tomorrow} due tomorrow")
    print(f"[Lani] 📝 Errors tracked: {data['mistakes']['metadata']['total_patterns_tracked']} patterns")

    sys.exit(0)


if __name__ == "__main__":
    main()
