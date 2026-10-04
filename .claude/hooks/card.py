#!/usr/bin/env python3
"""
Lani card helper: find a review card, see how the app asks and grades it, and fix it without losing
its review history or scheduling. For the lani-fix skill (a card or question the learner reported
as wrong or confusing).

Usage:
    python3 .claude/hooks/card.py find <text>                  cards whose id, front or back match
    python3 .claude/hooks/card.py show <id>                    one card, how the app uses it, its problems
    python3 .claude/hooks/card.py overlaps [<id>]              cards that ask the same thing or take the same answer
    python3 .claude/hooks/card.py edit <id> [--front T] [--back T] [--note T] [--category C]
    python3 .claude/hooks/card.py regrade <id> --quality Q [--date YYYY-MM-DD]
    python3 .claude/hooks/card.py merge <keep-id> <drop-id> [--front T] [--back T] [--note T]
    python3 .claude/hooks/card.py split <id> --new-id ID --new-front T --new-back T [--front T] [--back T]
    python3 .claude/hooks/card.py forgive <pattern-id> [--date YYYY-MM-DD] [--answer TEXT]

Every command takes --data DIR (default: the learner's data directory, lani_paths.data_dir()) and
--language CODE (another language's cards: DIR/languages/<code>/). Commands that write also take
--reason TEXT (kept with the change) and --dry-run (print the result, write nothing). They back up
the files they change to DIR/.backups/pre-card-<command>-<time>/ first and write atomically.

A card's front is its `content` (what the learner answers, in the target language), its back is its
`answer` (the meaning, in the learner's base language). See docs/DB_SCRIPTS.md, "Review cards".

Prints JSON. Exit codes: 0 ok, 1 invalid input or refused (nothing written), 2 I/O error.
"""
import argparse
import copy
import json
import math
import os
import re
import shutil
import sys
import unicodedata
from datetime import datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from lani_paths import data_dir, force_utf8_io  # noqa: E402
import lani_languages as langs  # noqa: E402

force_utf8_io()

SR_FILE = langs.FILES["sr"]
MISTAKES_FILE = langs.FILES["mistakes"]
ID = re.compile(r"^[A-Za-z0-9_][A-Za-z0-9_-]{0,127}$")
DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
MAX_TEXT = 200


class Refused(Exception):
    """Invalid input or a change the card can't take: exit 1, nothing written."""


# --- How the app reads a card (companion/android/.../data/ReviewPlanner.kt; keep in step) -------------------------

PAREN = re.compile(r"\s*\(([^)]*)\)")
SPACES = re.compile(r"\s+")
# Grading.normalize: lowercase, no punctuation, one kind of apostrophe, single spaces.
PUNCTUATION = re.compile(r"[.,!?;:¡¿\"„“”«»]")
APOSTROPHE = re.compile(r"[’ʼ`´]")
MAX_ANSWER = 40  # ReviewPlanner.accepted drops longer alternatives


def is_rule(front: str) -> bool:
    """Rules and placeholders ("dober/dobra/dobro → …", "Imenujem se ...") aren't phrases you can type."""
    return any(c in front for c in "→♂♀…") or "..." in front


def accepted(front: str) -> list:
    """ReviewPlanner.accepted: the answers the app takes for a front. " / " separates alternatives; "(…)" is optional
    ("hvala (lepa)" takes "hvala" and "hvala lepa"). Empty for a rule or placeholder."""
    if is_rule(front):
        return []
    out = []
    for alt in front.split(" / "):
        bare = PAREN.sub("", alt).strip()
        full = SPACES.sub(" ", PAREN.sub(lambda m: " " + m.group(1), alt)).strip()
        out += [bare, full]
    return list(dict.fromkeys(a for a in out if a.strip() and len(a) <= MAX_ANSWER))


def meaning(back: str) -> str:
    """ReviewPlanner.meaning: the back without its notes, what the app asks with ("good day (polite hello)" → "good day")."""
    return PAREN.sub("", back).strip() or back


def notes(back: str) -> str:
    """What the back has in parentheses: shown on the flashcard only, never in a prompt."""
    return "; ".join(m.group(1).strip() for m in PAREN.finditer(back) if m.group(1).strip())


def with_note(back: str, note: str) -> str:
    """The back's meaning with [note] as its only note ("" removes the notes)."""
    base = meaning(back) if notes(back) or PAREN.search(back) else back.strip()
    return f"{base} ({note.strip()})" if note.strip() else base


def normalize(s: str) -> str:
    return SPACES.sub(" ", PUNCTUATION.sub("", APOSTROPHE.sub("'", s.lower()))).strip()


def fold(s: str) -> str:
    """normalize()d, without accents (č → c): for searching, not grading."""
    return "".join(c for c in unicodedata.normalize("NFD", normalize(s)) if not unicodedata.combining(c))


def shown(item: dict) -> bool:
    """Dashboard.parse: an error pattern's content is the learner's wrong answer, never shown as a card."""
    return item.get("type") != "error_pattern" and isinstance(item.get("content"), str) and isinstance(item.get("answer"), str)


def variants(item: dict) -> list:
    """The exercises ReviewPlanner.plan can make of a card (which one comes up depends on how well it is known), by the
    names the app sends in about_exercise.source.variant (ReviewPlanner.Variant, lowercase)."""
    if not shown(item):
        return []
    acc = accepted(item["content"])
    if item.get("type", "vocabulary") != "vocabulary" or not acc:
        return ["flip"]
    out = ["flip", "recognize", "listen", "pick_slovene", "type", "dictation", "speak"]
    if len(acc[0].split(" ")) >= 2:
        out.insert(4, "tiles")
    return out


def app_view(item: dict) -> dict:
    """How the app shows and grades [item]."""
    if not shown(item):
        return {"shown": False, "why": "an error pattern: its content is the learner's wrong answer; the app never shows it as a card (modules drill it)"}
    front, back = item["content"], item["answer"]
    acc = accepted(front)
    out = {
        "shown": True,
        "front": front,
        "back": back,
        "prompt": meaning(back),
        "note": notes(back) or None,
        "accepted": acc,
        "first": acc[0] if acc else None,
        "variants": variants(item),
        "rusty": langs.rusty(item),
        "learned": langs.learned(item),
    }
    return out


# --- Problems a card can have -------------------------------------------------------------------------------------

def prompt_parts(back: str) -> set:
    """The meanings a back asks for, compared loosely ("the house" and "house" alike)."""
    parts = re.split(r" / |[,;]", meaning(back))
    out = set()
    for p in parts:
        p = re.sub(r"^(a|an|the|to) ", "", normalize(p))
        if p:
            out.add(p)
    return out


def quizzed(item: dict) -> bool:
    """The app asks for its front (types, tiles, speaks, picks it), not only flips it."""
    return len(variants(item)) > 1


def overlap(a: dict, b: dict) -> dict | None:
    """What two cards share: the same prompt (a meaning the app asks for, when one of them is asked for) or the same
    answer. Two flashcards with one meaning ("Ime mi je ...", "Imenujem se ...") are self-rated: no wrong answer."""
    if not (shown(a) and shown(b)):
        return None
    same_prompt = sorted(prompt_parts(a["answer"]) & prompt_parts(b["answer"])) if quizzed(a) or quizzed(b) else []
    same_answer = sorted({normalize(x) for x in accepted(a["content"])} & {normalize(x) for x in accepted(b["content"])})
    if not same_prompt and not same_answer:
        return None
    out = {}
    if same_prompt:
        out["same_prompt"] = same_prompt
    if same_answer:
        out["same_answer"] = same_answer
    return out


def overlaps_of(cid: str, items: dict) -> list:
    item = items[cid]
    out = []
    for oid, other in items.items():
        if oid == cid or not isinstance(other, dict):
            continue
        o = overlap(item, other)
        if o:
            out.append({"id": oid, "front": other.get("content"), "back": other.get("answer"), **o})
    return out


def problems(item: dict) -> list:
    """What makes a card confusing or ungradeable, in the app's terms."""
    if item.get("type") == "error_pattern":
        return ["an error pattern: the app never shows it as a card (its content is the learner's wrong answer); fix the practice that drills it instead"]
    front, back = item.get("content") or "", item.get("answer") or ""
    out = []
    if not front.strip() or not back.strip():
        out.append("an empty front or back: the app skips the card")
        return out
    for side, text in (("front", front), ("back", back)):
        if text.count("(") != text.count(")"):
            out.append(f"the {side} has unbalanced parentheses")
    if re.search(r"\S/|/\S", front) and not is_rule(front):
        out.append("the front has '/' without spaces: it is not split into alternatives (write ' / ')")
    alts = front.split(" / ")
    if is_rule(front):
        out.append("a rule or placeholder (→ ♂ ♀ … or ...): the app shows it as a flashcard only, nothing to type")
    elif not accepted(front):
        out.append(f"no alternative of the front is {MAX_ANSWER} letters or shorter: nothing to type, flashcard only")
    else:
        long = [a for a in alts if len(PAREN.sub("", a).strip()) > MAX_ANSWER]
        if long:
            out.append(f"alternatives over {MAX_ANSWER} letters are not accepted: {long}")
    if item.get("type", "vocabulary") != "vocabulary":
        out.append(f"type {item.get('type')!r}: only vocabulary cards get quiz variants; this one is a flashcard only")
    if " / " in meaning(back) and len(alts) >= 2:
        out.append(f"the prompt asks for several meanings at once ({meaning(back)!r}) but every answer is one alternative of the front: "
                   "split it into one card per meaning")
    if PAREN.sub("", back).strip() == "":
        out.append("the back is only a note: the app asks with the note itself")
    return out


# --- SM-2 (update-db.py's update_spaced_repetition, for replaying a card's history; keep in step) -----------------

SM2_FIELDS = ("easiness_factor", "interval_days", "repetitions", "due_date", "last_reviewed", "last_quality",
              "total_reviews", "consecutive_correct", "consecutive_incorrect", "mastery_level")


def sm2(ef: float, interval: int, reps: int, quality: int):
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
    ef = max(1.3, ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)))
    return round(ef, 2), interval, reps


def replay(item: dict, history: list) -> dict:
    """The scheduling a card has after [history], from the state update-db.py gives a new item."""
    st = {"easiness_factor": 2.5, "interval_days": 1, "repetitions": 0, "total_reviews": 0, "consecutive_correct": 0,
          "consecutive_incorrect": 1 if item.get("type") == "error_pattern" else 0, "mastery_level": 0}
    for e in history:
        q = int(e["quality"])
        st["easiness_factor"], st["interval_days"], st["repetitions"] = sm2(st["easiness_factor"], st["interval_days"], st["repetitions"], q)
        st["due_date"] = plus_days(e["date"], st["interval_days"])
        st["last_reviewed"] = e["date"]
        st["last_quality"] = q
        st["total_reviews"] += 1
        if q >= 3:
            st["consecutive_correct"] += 1
            st["consecutive_incorrect"] = 0
        else:
            st["consecutive_incorrect"] += 1
            st["consecutive_correct"] = 0
        current = st["mastery_level"]
        if st["repetitions"] >= 5 and st["consecutive_correct"] >= 3:
            st["mastery_level"] = min(5, max(current, 3))
        elif st["repetitions"] >= 2 and st["consecutive_correct"] >= 1 and q >= 4:
            st["mastery_level"] = min(5, current + 1)
    return st


def schedule(item: dict) -> dict:
    return {k: item.get(k) for k in SM2_FIELDS}


# --- Files --------------------------------------------------------------------------------------------------------

def plus_days(day: str, n: int) -> str:
    return (datetime.strptime(day, "%Y-%m-%d") + timedelta(days=n)).strftime("%Y-%m-%d")


def load(path: Path) -> dict:
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except FileNotFoundError:
        raise OSError(f"{path} does not exist")
    except json.JSONDecodeError as e:
        raise OSError(f"{path} is not valid JSON: {e}")
    if not isinstance(data, dict):
        raise OSError(f"{path} is not a JSON object")
    return data


def save(path: Path, data: dict):
    tmp = path.with_suffix(".json.tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
        f.flush()
        os.fsync(f.fileno())
    os.replace(str(tmp), str(path))


class Store:
    """The learner's data directory (and the language whose cards these are)."""

    def __init__(self, root: Path, language):
        self.root = root
        self.dir = root
        self.language = None
        if language:
            code = langs.language_code(language)
            if code is None:
                raise Refused(f"--language: a language code (it) or name (Italian), not {language!r}")
            home = None
            profile = root / langs.FILES["profile"]
            if profile.exists():
                home = langs.home_language(load(profile))
            if code != home:
                self.language = code
                self.dir = langs.language_dir(root, code)

    def path(self, name: str) -> Path:
        return self.dir / name

    def sr(self) -> dict:
        data = load(self.path(SR_FILE))
        if not isinstance(data.get("items"), dict):
            raise OSError(f"{self.path(SR_FILE)} has no items")
        return data

    def backup(self, command: str, names: list) -> str:
        """Copies of [names] (as they are now) in .backups/pre-card-<command>-<time>/, under their path in the data
        directory (languages/<code>/… for another language)."""
        tag = f"pre-card-{command}-{datetime.now().strftime('%Y%m%d-%H%M%S')}"
        dest = self.root / ".backups" / tag
        n = 1
        while dest.exists():
            n += 1
            dest = self.root / ".backups" / f"{tag}-{n}"
        for name in names:
            src = self.path(name)
            if src.exists():
                target = dest / src.relative_to(self.root)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, target)
        return str(dest)


def rebuild_queue(sr: dict, today: str):
    """update-db.py's review_queue, from the due dates (nothing reads it but people; keep it true)."""
    queue = {"today": [], "tomorrow": [], "this_week": [], "later": []}
    tom, week_end = plus_days(today, 1), plus_days(today, 7)
    for iid, item in sr["items"].items():
        due = item.get("due_date", today) if isinstance(item, dict) else today
        key = "today" if due <= today else "tomorrow" if due == tom else "this_week" if due <= week_end else "later"
        queue[key].append(iid)
    sr["review_queue"] = queue
    meta = sr.setdefault("metadata", {})
    meta["last_updated"] = today
    meta["total_items_tracked"] = len(sr["items"])


# --- Commands -----------------------------------------------------------------------------------------------------

def card_of(items: dict, cid: str) -> dict:
    item = items.get(cid)
    if not isinstance(item, dict):
        close = [i for i in items if cid.lower() in i.lower()][:5]
        raise Refused(f"no card {cid!r}" + (f" (did you mean {', '.join(close)}?)" if close else " (card.py find <text>)"))
    return item


def summary(cid: str, item: dict) -> dict:
    out = {"id": cid, "type": item.get("type"), "front": item.get("content"), "back": item.get("answer")}
    if shown(item):
        out["accepted"] = accepted(item["content"])
        out["prompt"] = meaning(item["answer"])
    for k in ("due_date", "interval_days", "repetitions", "last_quality", "total_reviews"):
        out[k] = item.get(k)
    out["rusty"] = langs.rusty(item)
    return out


def cmd_find(store: Store, args) -> dict:
    query = fold(args.text)
    if not query:
        raise Refused("find: give some text (a word of the front or the back, or part of an id)")
    items = store.sr()["items"]
    hits = []
    for cid, item in items.items():
        if not isinstance(item, dict):
            continue
        front, back = item.get("content") or "", item.get("answer") or ""
        answers = [fold(a) for a in accepted(front)] + [fold(p) for p in prompt_parts(back)] if shown(item) else []
        words = set(re.findall(r"[\w'-]+", f"{fold(front)} {fold(back)}"))
        if query in answers:
            score, how = 3, "answer"
        elif query in words or query == fold(cid):
            score, how = 2, "word"
        elif len(query) >= 3 and (query in fold(cid) or query in fold(front) or query in fold(back)):
            score, how = 1, "text"  # part of a word: only for 3 letters or more ("ja" is in half the deck)
        else:
            continue
        hits.append((score, cid, how, item))
    # Best matches first; cards the app shows before error patterns.
    hits.sort(key=lambda h: (-h[0], not shown(h[3]), h[1]))
    cards = [{**summary(cid, item), "match": how} for _, cid, how, item in hits[: args.limit]]
    return {"query": args.text, "language": store.language, "count": len(hits), "cards": cards}


def cmd_show(store: Store, args) -> dict:
    items = store.sr()["items"]
    item = card_of(items, args.id)
    return {
        "id": args.id,
        "language": store.language,
        "file": str(store.path(SR_FILE)),
        "card": item,
        "app": app_view(item),
        "problems": problems(item),
        "overlaps": overlaps_of(args.id, items) if shown(item) else [],
    }


def cmd_overlaps(store: Store, args) -> dict:
    items = {k: v for k, v in store.sr()["items"].items() if isinstance(v, dict)}
    if args.id:
        card_of(items, args.id)
        return {"id": args.id, "overlaps": overlaps_of(args.id, items)}
    ids = sorted(items)
    pairs = []
    for i, a in enumerate(ids):
        for b in ids[i + 1:]:
            o = overlap(items[a], items[b])
            if o:
                pairs.append({"ids": [a, b], "fronts": [items[a]["content"], items[b]["content"]],
                              "backs": [items[a]["answer"], items[b]["answer"]], **o})
    return {"count": len(pairs), "pairs": pairs}


def checked_text(value, what: str) -> str:
    if value is None:
        return None
    text = SPACES.sub(" ", value).strip() if "\n" not in value else None
    if text is None:
        raise Refused(f"{what}: one line, no line breaks")
    if not text:
        raise Refused(f"{what}: empty")
    if len(text) > MAX_TEXT:
        raise Refused(f"{what}: {len(text)} letters; at most {MAX_TEXT}")
    if text.count("(") != text.count(")"):
        raise Refused(f"{what}: unbalanced parentheses in {text!r}")
    return text


def retext(item: dict, args, today: str, action: str, prefix: str = "") -> dict | None:
    """Applies --front/--back/--note/--category (or --new-… with [prefix]) to [item]; returns the old texts, or None
    when nothing changes. The old texts go into the card's `edits`."""
    front = checked_text(getattr(args, f"{prefix}front", None), f"--{prefix}front")
    back = checked_text(getattr(args, f"{prefix}back", None), f"--{prefix}back")
    note = getattr(args, f"{prefix}note", None)
    category = getattr(args, "category", None) if not prefix else None
    if note is not None:
        if "\n" in note or "(" in note or ")" in note:
            raise Refused(f"--{prefix}note: one line, without parentheses (they are added)")
        back = with_note(back if back is not None else item.get("answer") or "", note)
    before = {"content": item.get("content"), "answer": item.get("answer"), "category": item.get("category")}
    after = dict(before)
    if front is not None:
        after["content"] = front
    if back is not None:
        after["answer"] = back
    if category is not None:
        after["category"] = category.strip()
    if after == before:
        return None
    item.update({k: v for k, v in after.items() if v is not None})
    entry = {"date": today, "action": action, **{k: v for k, v in before.items() if v is not None and before[k] != after[k]}}
    if args.reason:
        entry["reason"] = args.reason.strip()
    item.setdefault("edits", []).append(entry)
    return before


def text_warnings(cid: str, item: dict, items: dict) -> list:
    out = problems(item)
    for o in overlaps_of(cid, items) if shown(item) else []:
        what = " and ".join(f"{k.replace('_', ' ')} {v}" for k, v in o.items() if k.startswith("same_"))
        out.append(f"overlaps {o['id']} ({o['front']!r} = {o['back']!r}): {what}")
    return out


def finish(store: Store, args, command: str, names: list, data: dict, result: dict) -> dict:
    """Backs up and writes [data] (file name → content), unless --dry-run."""
    result["dry_run"] = bool(args.dry_run)
    if args.dry_run:
        return result
    result["backup"] = store.backup(command, names)
    for name in names:
        save(store.path(name), data[name])
    result["written"] = [str(store.path(n)) for n in names]
    return result


def cmd_edit(store: Store, args) -> dict:
    sr = store.sr()
    items = sr["items"]
    item = card_of(items, args.id)
    before = retext(item, args, args.today, "edit")
    if before is None:
        raise Refused("edit: nothing to change (give --front, --back, --note or --category with a new text)")
    if shown(item) and not accepted(item["content"]) and item.get("type", "vocabulary") == "vocabulary" and not args.force:
        raise Refused(f"edit: the new front {item['content']!r} has no answer the app can take (a rule or placeholder, or too long); "
                      "add --force if it should be a flashcard only")
    result = {"id": args.id, "before": before, "after": {k: item.get(k) for k in before}, "app": app_view(item),
              "warnings": text_warnings(args.id, item, items), "kept": schedule(item)}
    return finish(store, args, "edit", [SR_FILE], {SR_FILE: sr}, result)


def regrade_first(store: Store, args, sr: dict, item: dict) -> dict:
    """A card never reviewed: its only grade is the first one (a pack's practice: update-db.py's initial_quality, kept as
    last_quality). A right answer (3 or more) makes the word learned; the scheduling stays a new card's."""
    if args.date:
        raise Refused(f"regrade: {args.id} has no reviews; --date is for a review")
    old = item.get("last_quality")
    if old == args.quality:
        raise Refused(f"regrade: {args.id}'s first grade is {old} already")
    before = {"last_quality": old, "learned": langs.learned(item)}
    item["last_quality"] = args.quality
    entry = {"date": args.today, "action": "regrade", "last_quality": old}
    if args.reason:
        entry["reason"] = args.reason.strip()
    item.setdefault("edits", []).append(entry)
    result = {"id": args.id, "first_grade": {"from": old, "to": args.quality}, "before": before,
              "after": {"last_quality": args.quality, "learned": langs.learned(item)}}
    return finish(store, args, "regrade", [SR_FILE], {SR_FILE: sr}, result)


def cmd_regrade(store: Store, args) -> dict:
    sr = store.sr()
    item = card_of(sr["items"], args.id)
    history = item.get("review_history")
    if (not isinstance(history, list) or not history) and not item.get("total_reviews"):
        return regrade_first(store, args, sr, item)
    if not isinstance(history, list) or not history:
        raise Refused(f"regrade: {args.id} was reviewed ({item.get('total_reviews')}) but has no review history to replay")
    if not all(isinstance(e, dict) and DATE.match(str(e.get("date", ""))) and isinstance(e.get("quality"), int) for e in history):
        raise Refused(f"regrade: {args.id} has a review history entry without a date or quality")
    if args.date:
        at = [i for i, e in enumerate(history) if e["date"] == args.date]
        if not at:
            raise Refused(f"regrade: {args.id} has no review on {args.date} (reviews: {', '.join(e['date'] for e in history)})")
        index = at[-1]
    else:
        index = len(history) - 1
    old = history[index]["quality"]
    if old == args.quality:
        raise Refused(f"regrade: the review on {history[index]['date']} already has quality {old}")
    replayed = replay(item, history)
    stored = {k: item.get(k) for k in replayed}
    if replayed != stored and not args.force:
        differ = {k: {"stored": stored[k], "from_history": replayed[k]} for k in replayed if stored[k] != replayed[k]}
        raise Refused("regrade: the card's scheduling doesn't follow from its review history (changed by hand?), so a replay "
                      f"would lose that: {json.dumps(differ, ensure_ascii=False)}. Add --force to replay anyway.")
    before = schedule(item)
    was_rusty = langs.rusty(item)
    old_incorrect = item.get("consecutive_incorrect", 0)
    entry = history[index]
    entry["quality"] = args.quality
    entry["regraded"] = {"from": old, "date": args.today, **({"reason": args.reason.strip()} if args.reason else {})}
    item.update(replay(item, history))
    if item["consecutive_incorrect"] >= 2:
        item["priority"] = "high"
    elif item["mastery_level"] >= 3:
        item["priority"] = "low"
    elif old_incorrect >= 2 and item.get("priority") == "high":
        item["priority"] = "medium"
    rebuild_queue(sr, args.today)
    result = {"id": args.id, "review": {"date": entry["date"], "quality": {"from": old, "to": args.quality}},
              "before": before, "after": schedule(item), "rusty": {"before": was_rusty, "after": langs.rusty(item)},
              "note": "the review's session keeps its score in progress-db and the session log; only the card changes"}
    return finish(store, args, "regrade", [SR_FILE], {SR_FILE: sr}, result)


def cmd_merge(store: Store, args) -> dict:
    sr = store.sr()
    items = sr["items"]
    if args.keep == args.drop:
        raise Refused("merge: two different cards")
    keep, drop = card_of(items, args.keep), card_of(items, args.drop)
    for cid, it in ((args.keep, keep), (args.drop, drop)):
        if not shown(it) and not args.force:
            raise Refused(f"merge: {cid} is an error pattern, not a card (add --force to merge it anyway)")
    before = retext(keep, args, args.today, "merge") or {"content": keep.get("content"), "answer": keep.get("answer"), "category": keep.get("category")}
    if shown(keep) and shown(drop) and not accepted(keep["content"]) and keep.get("type", "vocabulary") == "vocabulary" and not args.force:
        raise Refused(f"merge: the kept front {keep['content']!r} has no answer the app can take; add --force for a flashcard only")
    record = {"date": args.today, "id": args.drop, "item": copy.deepcopy(drop)}
    if args.reason:
        record["reason"] = args.reason.strip()
    keep.setdefault("merged", []).append(record)
    del items[args.drop]
    rebuild_queue(sr, args.today)
    warnings = text_warnings(args.keep, keep, items)
    lost = [a for a in accepted(drop.get("content") or "") if normalize(a) not in {normalize(x) for x in accepted(keep.get("content") or "")}]
    if lost:
        warnings.append(f"no card takes {lost} any more: a word pack with it will teach it again")
    warnings.append(f"a review of {args.drop} still in the app (not yet sent) is ignored when it arrives")
    result = {"kept": args.keep, "dropped": args.drop, "before": before,
              "after": {k: keep.get(k) for k in ("content", "answer", "category")}, "schedule": schedule(keep),
              "app": app_view(keep), "warnings": warnings}
    return finish(store, args, "merge", [SR_FILE], {SR_FILE: sr}, result)


def cmd_split(store: Store, args) -> dict:
    sr = store.sr()
    items = sr["items"]
    item = card_of(items, args.id)
    if not shown(item):
        raise Refused(f"split: {args.id} is an error pattern, not a card")
    if not ID.match(args.new_id):
        raise Refused(f"split: --new-id {args.new_id!r}: letters, digits, _ and - (e.g. vocab_ne)")
    if args.new_id in items:
        raise Refused(f"split: {args.new_id} exists already (merge into it, or pick another id)")
    before = retext(item, args, args.today, "split") or {"content": item.get("content"), "answer": item.get("answer"), "category": item.get("category")}
    new = {"content": "", "answer": ""}
    retext(new, args, args.today, "split", prefix="new_")
    if not new.get("content") or not new.get("answer"):
        raise Refused("split: the new card needs --new-front and --new-back")
    new.pop("edits", None)
    items[args.new_id] = {
        "id": args.new_id,
        "type": item.get("type", "vocabulary"),
        "content": new["content"],
        "answer": new["answer"],
        "category": item.get("category", ""),
        "difficulty": item.get("difficulty", ""),
        "created_date": args.today,
        "due_date": plus_days(args.today, 1),
        "interval_days": 1,
        "repetitions": 0,
        "easiness_factor": 2.5,
        "consecutive_correct": 0,
        "consecutive_incorrect": 0,
        "last_reviewed": args.today,
        "last_quality": 3,
        "mastery_level": 0,
        "total_reviews": 0,
        "priority": "medium",
        "split_from": args.id,
    }
    for cid, it in ((args.id, item), (args.new_id, items[args.new_id])):
        if it.get("type", "vocabulary") == "vocabulary" and not accepted(it["content"]) and not args.force:
            raise Refused(f"split: the front {it['content']!r} of {cid} has no answer the app can take; add --force for a flashcard only")
    rebuild_queue(sr, args.today)
    result = {"id": args.id, "before": before, "after": {k: item.get(k) for k in ("content", "answer", "category")},
              "kept": schedule(item), "new": items[args.new_id],
              "warnings": text_warnings(args.id, item, items) + text_warnings(args.new_id, items[args.new_id], items)}
    return finish(store, args, "split", [SR_FILE], {SR_FILE: sr}, result)


def cmd_forgive(store: Store, args) -> dict:
    mistakes = load(store.path(MISTAKES_FILE))
    patterns = mistakes.get("error_patterns")
    if not isinstance(patterns, dict) or not isinstance(patterns.get(args.pattern), dict):
        known = sorted(patterns) if isinstance(patterns, dict) else []
        raise Refused(f"forgive: no error pattern {args.pattern!r} in {MISTAKES_FILE}" + (f" (patterns: {', '.join(known)})" if known else ""))
    pat = patterns[args.pattern]
    examples = [e for e in pat.get("examples", []) if isinstance(e, dict)]
    day = args.date or max((e.get("date", "") for e in examples), default="")
    matches = [i for i, e in enumerate(pat.get("examples", []))
               if isinstance(e, dict) and e.get("date") == day and (args.answer is None or normalize(e.get("incorrect", "")) == normalize(args.answer))]
    if not matches:
        shown_ex = [{"date": e.get("date"), "incorrect": e.get("incorrect")} for e in examples]
        raise Refused(f"forgive: {args.pattern} has no example on {day or '?'}{' with that answer' if args.answer else ''}: {json.dumps(shown_ex, ensure_ascii=False)}")
    before = {k: pat.get(k) for k in ("frequency", "consecutive_incorrect", "consecutive_correct", "last_seen")}
    removed = pat["examples"].pop(matches[-1])
    pat["frequency"] = max(0, int(pat.get("frequency", 1)) - 1)
    pat["consecutive_incorrect"] = max(0, int(pat.get("consecutive_incorrect", 1)) - 1)
    left = [e.get("date") for e in pat["examples"] if isinstance(e, dict) and e.get("date")]
    if left:
        pat["last_seen"] = pat["last_occurred"] = max(left)
    names, data = [MISTAKES_FILE], {MISTAKES_FILE: mistakes}
    result = {"pattern": args.pattern, "removed": removed, "before": before}
    if pat["frequency"] == 0:
        del patterns[args.pattern]
        result["deleted"] = True
        # The review item update-db.py made of the mistake, when nothing else has touched it.
        if store.path(SR_FILE).exists():
            sr = store.sr()
            it = sr["items"].get(args.pattern)
            if isinstance(it, dict) and it.get("type") == "error_pattern" and not it.get("total_reviews"):
                del sr["items"][args.pattern]
                rebuild_queue(sr, args.today)
                names.append(SR_FILE)
                data[SR_FILE] = sr
                result["deleted_review_item"] = args.pattern
    else:
        entry = {"date": args.today, "example": removed}
        if args.reason:
            entry["reason"] = args.reason.strip()
        pat.setdefault("forgiven", []).append(entry)
        result["after"] = {k: pat.get(k) for k in before}
    meta = mistakes.setdefault("metadata", {})
    meta["last_updated"] = args.today
    meta["total_patterns_tracked"] = len(patterns)
    return finish(store, args, "forgive", names, data, result)


COMMANDS = {"find": cmd_find, "show": cmd_show, "overlaps": cmd_overlaps, "edit": cmd_edit, "regrade": cmd_regrade,
            "merge": cmd_merge, "split": cmd_split, "forgive": cmd_forgive}


def common() -> argparse.ArgumentParser:
    """The options every command takes, before or after its name: a new parser each time (argparse shares a parent's
    actions, and one parser's default would overwrite what another parsed)."""
    p = argparse.ArgumentParser(add_help=False)
    p.add_argument("--data", default=argparse.SUPPRESS, help="the data directory (default: the learner's)")
    p.add_argument("--language", default=argparse.SUPPRESS, help="another language's cards: a code (it) or name")
    p.add_argument("--today", default=argparse.SUPPRESS, help="the date to record (YYYY-MM-DD; default: today)")
    return p


def parser() -> argparse.ArgumentParser:
    writes = argparse.ArgumentParser(add_help=False)
    writes.add_argument("--reason", help="why (kept with the change)")
    writes.add_argument("--dry-run", action="store_true", help="print the result, write nothing")
    writes.add_argument("--force", action="store_true", help="do it despite a refusal that says --force")
    texts = argparse.ArgumentParser(add_help=False)
    texts.add_argument("--front", help="the new front (content): the answer, in the target language")
    texts.add_argument("--back", help="the new back (answer): the meaning; (…) is a note, shown on the flashcard only")
    texts.add_argument("--note", help="the back's note, replacing its (…) ('' removes it)")

    ap = argparse.ArgumentParser(description="Find, show and fix Lani review cards (JSON out).", parents=[common()])
    sub = ap.add_subparsers(dest="command", required=True)
    p = sub.add_parser("find", parents=[common()], help="cards whose id, front or back match")
    p.add_argument("text")
    p.add_argument("--limit", type=int, default=20)
    p = sub.add_parser("show", parents=[common()], help="one card, how the app asks and grades it, its problems")
    p.add_argument("id")
    p = sub.add_parser("overlaps", parents=[common()], help="cards asking the same thing or taking the same answer")
    p.add_argument("id", nargs="?")
    p = sub.add_parser("edit", parents=[common(), writes, texts], help="new texts; history and scheduling kept")
    p.add_argument("id")
    p.add_argument("--category")
    p = sub.add_parser("regrade", parents=[common(), writes], help="a review graded wrong by the card's fault: its quality, and SM-2 replayed")
    p.add_argument("id")
    p.add_argument("--quality", type=int, required=True, choices=range(6), metavar="0-5")
    p.add_argument("--date", help="the review's date (default: the latest review)")
    p = sub.add_parser("merge", parents=[common(), writes, texts], help="one card instead of two: keep-id keeps its scheduling")
    p.add_argument("keep")
    p.add_argument("drop")
    p = sub.add_parser("split", parents=[common(), writes, texts], help="a new card from part of one")
    p.add_argument("id")
    p.add_argument("--new-id", required=True)
    p.add_argument("--new-front", required=True)
    p.add_argument("--new-back", required=True)
    p.add_argument("--new-note")
    p = sub.add_parser("forgive", parents=[common(), writes], help="take back a mistake (mistakes-db) that was the item's fault")
    p.add_argument("pattern")
    p.add_argument("--date", help="the mistake's date (default: its latest example's)")
    p.add_argument("--answer", help="the learner's answer, when the day has several examples")
    return ap


def main(argv=None) -> int:
    args = parser().parse_args(argv)
    for k in ("data", "language", "today"):
        if not hasattr(args, k):
            setattr(args, k, None)
    try:
        if args.today is None:
            args.today = datetime.now().strftime("%Y-%m-%d")
        elif not DATE.match(args.today):
            raise Refused("--today: YYYY-MM-DD")
        if getattr(args, "date", None) and not DATE.match(args.date):
            raise Refused("--date: YYYY-MM-DD")
        root = Path(args.data).expanduser().resolve() if args.data else data_dir()
        store = Store(root, args.language)
        result = COMMANDS[args.command](store, args)
    except Refused as e:
        print(f"[Lani] Error: {e}", file=sys.stderr)
        return 1
    except OSError as e:
        print(f"[Lani] Error: {e}", file=sys.stderr)
        return 2
    json.dump(result, sys.stdout, indent=2, ensure_ascii=False)
    print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
