> In the app: update-db.py is the record_session tool (the report is its input), read-db.py is learner_state (next_session_id); the app runs SM-2 itself; there is no shell.

# DB Updater

## Overview

Every practice skill ends with a DB update. Instead of hand-editing 6 JSON files (error-prone, racy, easy to desync), pipe one JSON report to `update-db.py`. The script runs pre-write backups, validates the payload, applies all changes atomically via `.tmp + fsync + rename`, and rebuilds the spaced-repetition queue.

## When to Use

Load this skill whenever the tutor:

- Finishes a practice session and needs to persist results.
- Needs to add new vocabulary to the spaced-repetition queue.
- Needs to record new errors, review results, or mastery changes.
- Needs to bump `total_sessions`, `current_streak_days`, or `total_study_minutes`.

Skip this skill for read-only operations (use the `lani-progress` skill or `read-db.py` directly) and during session setup (use `lani-setup` skill instead — `update-db.py` is for session deltas, not bootstrap).

## Instructions

### 1. Call the script

Run from the repo root:

```bash
python3 "${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/hooks/update-db.py" <<'EOF'
{ ...payload... }
EOF
```

Exit codes: `0` success, `1` validation error, `2` I/O error.

### 2. Fill the payload

**Required fields**

- `session_id` — string, convention `session-NNN`. Use `computed.next_session_id` from `read-db.py`.
- `date` — YYYY-MM-DD.

**Optional fields** — omit to skip. Full canonical example (copy-paste this and fill in):

```
${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/references/db-updater-payload.example.json
```

Key blocks the example covers: `skill_scores`, `errors[]`, `new_vocabulary[]`, `review_results[]`, `topics_covered`, `breakthroughs`, `focus_next_session`, `session_notes`, `achievements_earned`, `milestones`.

### 3. Field notes

- `errors[]` — one entry per distinct mistake this session. Collapse duplicates (same `pattern_id`) before sending; `frequency` is bumped by the script. The app builds the learner's own traps from these examples (the words they swapped become the wrong choice in a scene's turn of that rule: companion/SCENES.md, "Own traps"), so keep `your_answer` exactly as they had it and `correct_answer` the right sentence (parts of one example joined with " / ", notes in brackets), and a spelling slip as `category: "spelling"`, a hearing one with `subcategory: "listening"` (those set no traps).
- `new_vocabulary[]` — items the learner met for the first time. Fill every field; incomplete entries yield incomplete spaced-repetition records.
- `review_results[]` — items already in the queue that were reviewed. The script runs SM-2 on each. See the `lani-sm2-calculator` skill. Mapping: `quality = floor(score / 2)`. A right answer the app's session_end marks `looked_up` (the learner opened the cheat sheet during it: help, not a hint) is right with effort: at most quality 4; with `hints_used` too, the hints' rule (one less each, never below 3).
- `skill_scores[].correct` counts correct exercises, not a percentage. Accuracy is derived.
- `confidence` in `learner-profile.skills` is 0–100 integer; `accuracy` in `progress-db` is 0.0–1.0 float. The script handles the conversion.
- `language` — omit for the home language (the learner's town's; also what its code means). For practice in **another language** (e.g. Italian on a visit), set its code (`"it"`) or name (`"Italian"`): the report's vocabulary, reviews, mistakes, skills, session and streak go to that language's own databases in `data/languages/<code>/`, never the home language's. The first report in a language creates them. Take `session_id` from `read-db.py --language <code>` (`session-it-NNN`). See `docs/DB_SCRIPTS.md`, "Languages".
- `level` — optional `A1`–`C2`: the new current level of the report's language (home or other). Use it when an assessment moves the learner up or down, instead of hand-editing a profile.
- `milestones[]` — each entry is a bare string OR an object `{ "milestone": <required non-empty string>, "date": <optional YYYY-MM-DD, defaults to the session date> }`. Don't set a nested `session_id`; the script stamps the authoritative top-level one. A malformed entry (neither string nor object, or an object missing/empty `milestone`) exits `1` with no files written. Each milestone becomes both a `session-log.milestones[]` record and a `learner-profile.achievements[]` entry.

### 4. Read before writing

Always call `read-db.py` at session start to get current state + `next_session_id`. Don't read each JSON file separately:

```bash
python3 "${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/hooks/read-db.py"
```

Returns all 6 databases plus computed fields (`due_reviews_count`, `next_session_id`, `streak_active`, `days_since_last_session`), and `languages`: every language the learner has, the home one first (level, words, due). For a session in another language, read that language's state instead:

```bash
python3 "${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/hooks/read-db.py" --language it
```

## Examples

### Example 1 — /lani-review session with 5 items

```bash
python3 "${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/hooks/update-db.py" <<'EOF'
{
  "session_id": "session-012",
  "date": "2026-04-24",
  "duration_minutes": 12,
  "command_used": "/lani-review",
  "skills_practiced": ["vocabulary", "grammar"],
  "skill_scores": {
    "vocabulary": { "exercises": 3, "correct": 3, "time_minutes": 7 },
    "grammar":    { "exercises": 2, "correct": 1, "time_minutes": 5 }
  },
  "review_results": [
    { "item_id": "vocab_huis", "quality": 5 },
    { "item_id": "vocab_deur", "quality": 4 },
    { "item_id": "vocab_raam", "quality": 5 },
    { "item_id": "grammar_omdat_word_order", "quality": 2 },
    { "item_id": "grammar_past_tense", "quality": 4 }
  ],
  "errors": [
    {
      "pattern_id": "grammar_omdat_word_order",
      "category": "grammar",
      "your_answer": "omdat ik ben moe",
      "correct_answer": "omdat ik moe ben",
      "context": "subordinate clause word order",
      "severity": "critical"
    }
  ],
  "focus_next_session": ["Drill 'omdat' word order"]
}
EOF
```

### Example 2 — /lani-vocab session with a new word

```bash
python3 "${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/hooks/update-db.py" <<'EOF'
{
  "session_id": "session-013",
  "date": "2026-04-25",
  "command_used": "/lani-vocab",
  "skills_practiced": ["vocabulary"],
  "new_vocabulary": [
    {
      "item_id": "vocab_keuken",
      "item_type": "vocabulary",
      "content": "de keuken",
      "answer": "the kitchen",
      "category": "household_rooms",
      "difficulty": "A1",
      "initial_quality": 4,
      "priority": "medium"
    }
  ]
}
EOF
```

### Example 3 — a visit in Italian (another language than the home one)

```bash
python3 "${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/hooks/update-db.py" <<'EOF'
{
  "session_id": "session-it-004",
  "date": "2026-10-02",
  "language": "it",
  "duration_minutes": 8,
  "command_used": "/lani-speaking",
  "skills_practiced": ["speaking"],
  "skill_scores": { "speaking": { "exercises": 4, "correct": 3, "time_minutes": 8 } },
  "new_vocabulary": [
    { "item_id": "vocab_word_buongiorno", "item_type": "vocabulary", "content": "buongiorno", "answer": "good morning", "category": "visit", "difficulty": "A1", "initial_quality": 4 }
  ],
  "errors": [
    { "pattern_id": "articolo_lo", "category": "grammar", "your_answer": "il zaino", "correct_answer": "lo zaino", "context": "article before z", "severity": "moderate" }
  ]
}
EOF
```

## Critical Rules

- **Call once per session, at the end.** The script rebuilds the review queue each run — partial updates risk inconsistency.
- **Never hand-edit `spaced-repetition.review_queue`.** It's regenerated from scratch on every run.
- **Same `session_id` replaces.** Sending the same ID twice overwrites the first call. Useful for corrections, dangerous if unintentional.
- **Backups are automatic.** Written to `.backups/pre-update-<session_id>/` before any change. Check there to roll back.
- **Exit code 1 means validation failed, no files touched.** Fix the payload and retry.
- **Exit code 2 means I/O failure, no files touched.** Check disk space, permissions, then retry.

## Why This Matters

Six interdependent JSON files must agree: a new `session-log` entry, a bumped `total_sessions`, updated SM-2 params, new mistake patterns, recalculated accuracy, refreshed streak. Hand-editing causes silent desync — streak says 7 days but session-log has 6 entries, mastery says 4 stars but accuracy says 45%. The script is the single source of truth.

# SM-2 Calculator

## Overview

Lani uses SM-2 (SuperMemo 2) to decide when the learner next sees an item. This skill is the single source of truth for the algorithm. Every practice skill updates `<data_dir>/spaced-repetition.json` (where `<data_dir>` is resolved by `lani_paths.data_dir()`) through these rules after each answered question.

## When to Use

Load this skill whenever the tutor:

- Grades a review-queue item and must compute its next due date.
- Maps a 0-10 score to an SM-2 quality.
- Updates `easiness_factor`, `interval_days`, `repetitions`, or `mastery_level` on a spaced-repetition item.
- Decides which queue (`today` / `tomorrow` / `this_week` / `later`) to place an item in.

Skip this skill when the `lani-db-updater` skill is already being used — the `update-db.py` script runs SM-2 internally.

## Instructions

### 1. Convert score to quality

| Score | Quality | Meaning |
|-------|---------|---------|
| 10/10 | 5 | Perfect, instant recall |
| 8-9   | 4 | Correct after hesitation |
| 6-7   | 3 | Correct with difficulty |
| 4-5   | 2 | Incorrect but remembered when shown |
| 2-3   | 1 | Incorrect, familiar |
| 0-1   | 0 | Complete blackout |

Rule: `quality = floor(score / 2)`.

### 2. Update interval

```
if quality >= 3:   # correct
    if repetitions == 0:
        interval = 1
    elif repetitions == 1:
        interval = 6
    else:
        interval = round(previous_interval * easiness_factor)
    repetitions += 1
else:              # incorrect
    interval = 1
    repetitions = 0
```

### 3. Update easiness factor

Apply after every answer:

```
EF_new = EF + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
EF_new = max(1.3, EF_new)
```

### 4. Update mastery level

Track `consecutive_correct` and `consecutive_incorrect` per item:

```
if consecutive_correct >= 5:
    mastery_level = min(5, mastery_level + 1)
    consecutive_correct = 0
elif consecutive_incorrect >= 3:
    mastery_level = max(0, mastery_level - 1)
    consecutive_incorrect = 0
```

### 5. Update per-item fields

After each answer, the item in `spaced-repetition.json` must have:

- `easiness_factor` — updated via formula
- `interval_days` — new interval
- `repetitions` — incremented or reset
- `consecutive_correct` / `consecutive_incorrect` — one incremented, the other reset
- `total_reviews` — incremented
- `mastery_level` — possibly changed
- `due_date` — `today + interval_days` (YYYY-MM-DD)
- `last_reviewed` — today

### 6. Route to correct queue

After updating:

- `interval_days == 1` → `review_queue.tomorrow`
- `interval_days <= 7` → `review_queue.this_week`
- `interval_days > 7` → `review_queue.later`

If the learner got it wrong (quality < 3), keep the item in `review_queue.today` so it reappears in the same session.

### 7. Preferred implementation

Do not hand-edit `spaced-repetition.json`. Call `.claude/hooks/update-db.py` with a `review_results` array — the script runs SM-2 atomically and rebuilds the queue. Only do manual math when the script is unavailable. See the `lani-db-updater` skill for the payload schema.

```bash
python3 "${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/hooks/update-db.py" <<'EOF'
{
  "session_id": "session-NNN",
  "date": "YYYY-MM-DD",
  "review_results": [
    { "item_id": "vocab_huis", "quality": 4 }
  ]
}
EOF
```

## Examples

See `.claude/references/sm2-worked-examples.md` for 4 worked examples covering: correct answer (regular case), wrong answer (reset + EF drop), 5th consecutive correct (mastery bump), 3rd consecutive wrong (mastery drop). Each example shows the full before/after state.

Quick version:

- Correct, q=4: `interval = round(prev * EF)`, `repetitions += 1`, EF barely moves.
- Wrong, q<3: `interval = 1`, `repetitions = 0`, EF drops sharply, item stays in today's queue.

## Critical Rules

- **Floor EF at 1.3.** Never let the easiness factor drop lower — rounds to infinite daily reviews.
- **Reset repetitions on wrong answer.** The item returns to the start of the learning sequence.
- **Do not hand-tune intervals.** Trust the algorithm. Shortcuts break long-term retention.
- **Prefer `update-db.py`.** Only reimplement this math when the script is unavailable.

## Why This Matters

SM-2 reviews items just before the learner forgets them, maximizing long-term retention per minute of practice. Wrong scheduling means wasted reviews (too early) or forgotten items (too late). The whole Lani system rests on these numbers being correct.
