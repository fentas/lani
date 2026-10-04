# Database Helper Scripts

Three Python scripts under `.claude/hooks/` manage the six learner databases:

| Script | Purpose |
|--------|---------|
| `read-db.py` | Load all 6 databases (+ computed fields) in one call |
| `update-db.py` | Apply a session report to all 6 databases atomically |
| `card.py` | Find a review card, see how the app asks and grades it, fix it with its history kept (see [Review cards](#review-cards-cardpy)) |

The scripts resolve the data directory internally (see `lani_paths.data_dir()`)
and produce identical output regardless of CWD. Always invoke them with the
`${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}` prefix so the script path
itself resolves whether Lani is installed as a plugin (CWD is the data
directory) or cloned (CWD is the repo root).

## Reading

```bash
python3 "${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/hooks/read-db.py"
```

Outputs a single JSON object:

```json
{
  "databases": {
    "learner_profile": { ... },
    "progress_db": { ... },
    "mistakes_db": { ... },
    "mastery_db": { ... },
    "spaced_repetition": { ... },
    "session_log": { ... }
  },
  "computed": {
    "today": "2026-04-24",
    "due_reviews_count": 3,
    "due_review_items": ["vocab_dag", ...],
    "next_session_id": "session-005",
    "streak_active": true,
    "days_since_last_session": 1
  },
  "language": "sl",
  "languages": [
    { "code": "sl", "name": "Slovene", "level": "A2", "words": 120, "due": 3, "source": "home" },
    { "code": "it", "name": "Italian", "level": "A1", "words": 85, "due": 2, "source": "visits", "since": "2026-10-02" }
  ]
}
```

`databases` and `computed` are the home language's, as they always were. `language` is
the code of the language they belong to; `languages` sums up every language the learner
has, the home one first (see [Languages](#languages)).

```bash
python3 "${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/hooks/read-db.py" --language it
```

prints the same shape for another language (a code or a name: `it`, `Italian`): its six
databases, its `computed` (its due reviews, its `next_session_id`, e.g. `session-it-004`),
`"language": "it"`. Its `learner_profile.learner` has the learner's name, base language
and preferences from the home profile, and the language, level and target level of its
own. A language never practised reads as fresh, empty databases (exit `0`; nothing is
written). The home language's code gives the default output.

Exit codes: `0` OK, `1` one or more files missing (partial result with
`_warnings`), `2` critical error (also: `--language` isn't a language).

## Writing

```bash
python3 "${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/hooks/update-db.py" <<'EOF'
{
  "session_id": "session-005",
  "date": "2026-04-24",
  "duration_minutes": 20,
  "command_used": "/lani-learn",
  "skills_practiced": ["vocabulary", "writing"],
  "skill_scores": {
    "vocabulary": { "exercises": 5, "correct": 4, "time_minutes": 10 },
    "writing":    { "exercises": 3, "correct": 3, "time_minutes": 10 }
  },
  "errors": [
    {
      "pattern_id": "verb_conjugation_3rd_person",
      "category": "grammar",
      "subcategory": "verb_conjugation",
      "your_answer": "Hij spreek",
      "correct_answer": "Hij spreekt",
      "context": "3rd person singular",
      "difficulty_score": 0.7,
      "severity": "critical",
      "notes": "optional free text"
    }
  ],
  "new_vocabulary": [
    {
      "item_id": "het_huis",
      "item_type": "vocabulary",
      "content": "het huis",
      "answer": "the house",
      "category": "essential_nouns",
      "difficulty": "A1",
      "initial_quality": 4,
      "priority": "medium"
    }
  ],
  "review_results": [
    { "item_id": "vocab_dag", "quality": 4 }
  ],
  "topics_covered": ["articles", "house_vocabulary"],
  "breakthroughs": ["First correct use of 'het' vs 'de'"],
  "focus_next_session": ["Drill de/het article gender"],
  "session_notes": "Strong session. Article gender still tricky.",
  "achievements_earned": [],
  "milestones": [
    { "milestone": "Reached 100 words learned", "date": "2026-04-24" },
    "Completed first full conversation"
  ]
}
EOF
```

### Required fields
- `session_id` (string, conventionally `session-NNN`)
- `date` (YYYY-MM-DD)

Everything else is optional; omitted fields do not update.

### Side effects
- Backs up `data/*.json` to `.backups/pre-update-<session_id>/` *before*
  writing (another language's report: see
  [Another language](#another-language-language-and-level)).
- Writes each JSON file via a `.tmp` + `fsync` + `rename` pattern so a crash
  mid-write cannot leave a half-written file.
- Rebuilds `spaced-repetition.review_queue` from scratch each run — any manual
  edits there will be overwritten.

### Exit codes
- `0` success
- `1` validation error (bad/missing JSON, missing required field)
- `2` I/O or logic error (full traceback on stderr; no files were modified)

### Review items without a session

A word met outside practice (the learner looked it up in a dialog of the Lani app and
added it to their words) becomes a review item without a session:

```json
{
  "record_session": false,
  "date": "2026-04-24",
  "new_vocabulary": [
    {
      "item_id": "vocab_word_gozd",
      "item_type": "vocabulary",
      "content": "gozd",
      "answer": "forest",
      "category": "dialog",
      "example": "V gozdu je tiho.",
      "example_translation": "It's quiet in the forest.",
      "source": "lookup"
    }
  ]
}
```

- Only `spaced-repetition.json` changes: no session-log entry, no minutes, no exercises, no
  streak day. `session_id` is not needed.
- `date` and a non-empty `new_vocabulary` are required. A report that also carries practice
  (`duration_minutes`, `skill_scores`, `review_results`, `errors`, `milestones`, …) is a
  validation error (exit `1`).
- An item that exists already is left as it is; when nothing is new, nothing is written.
- The backup is `.backups/pre-words-<date>/spaced-repetition.json`: the state before the
  day's latest write.

`example` and `example_translation` (optional, in any `new_vocabulary` item) keep the
sentence the word was met in.

`source` (optional string, in any `new_vocabulary` item) says where a new item came from and is
kept only when the item is created. The Lani app's word card sends `"lookup"`: the learner
added the word and did not answer it, so its `last_quality` (`initial_quality`) does not mean
it was learned.

### Another language: `language` and `level`

- `language` (optional, a code such as `"it"` or a name such as `"Italian"`): the language
  the report is in. Leave it out for the home language (the learner's town's); the home
  language's code does the same. Another language's report writes that language's six
  databases in `data/languages/<code>/` (see [Languages](#languages)): its new vocabulary,
  review results, mistakes, skills, session and streak. The home language's data does not
  change, except the home profile's list of languages.
  - The first report in a language creates its six databases (level `A1`, source `visits`)
    and adds the language to the home profile's `languages`.
  - The backup is `.backups/pre-update-<code>-<session_id>/` (or
    `pre-words-<code>-<date>/`): the home `learner-profile.json` and the language's files
    (`languages/<code>/*.json`) as they were.
  - Use that language's `next_session_id` (`read-db.py --language <code>`): `session-it-NNN`.
    Then its result files and backups never meet the home language's.
  - With `record_session: false`, only its `spaced-repetition.json` changes (the first word
    of a new language also creates the language).
- `level` (optional, `A1`–`C2`): the report's language's new current level
  (`learner.current_level` of its `learner-profile.json`; for another language also the copy
  in the home profile's list). Not allowed with `record_session: false`.
  - A `level` that changes the current level also sets `learner.level_since` to the report's
    `date`: the day the learner reached it (in the same `learner-profile.json`). The same level
    again leaves `level_since` as it is.

A `language` that is not a language, or a `level` that is not a CEFR level, is a validation
error (exit `1`, nothing written).

## Review cards: `card.py`

For the `lani-fix` skill: a card or question the learner reports as wrong or confusing. A card
is a `spaced-repetition.json` item: its **front** is `content` (the answer, in the target
language), its **back** is `answer` (the meaning, the prompt). `card.py` reads a card the way the
app does (`ReviewPlanner.kt`), and changes it without losing its review history or scheduling.

```bash
card="${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/hooks/card.py"
python3 "$card" find ja                  # cards whose id, front or back match; answers first
python3 "$card" show vocab_da            # the card, how the app asks and grades it, its problems and overlaps
python3 "$card" overlaps                 # pairs of cards that ask the same thing or take the same answer
python3 "$card" edit vocab_da --front "da / ja" --note "da = written/standard, ja = spoken" --reason "ja was marked wrong"
python3 "$card" regrade vocab_da --quality 5 --date 2026-09-27
python3 "$card" merge vocab_da vocab_ja  # keep vocab_da; vocab_ja goes, its item kept inside vocab_da's `merged`
python3 "$card" split vocab_ne --front ne --back no --new-id vocab_ja --new-front ja --new-back yes
python3 "$card" forgive iz_genitive --date 2026-09-26 --answer "iz Ljubljana"
```

`show` gives the card as stored and `app`: `prompt` (the back without its notes), `note` (the
back's `(…)`, shown on the flashcard only), `accepted` (the front's alternatives, split at ` / `,
with and without its optional `(…)` words; empty for a rule or placeholder), `first` (what
dictation plays and tiles build), `variants` (the app's names: `flip`, `recognize`, `listen`,
`pick_slovene`, `tiles`, `type`, `dictation`, `speak`), `rusty` and `learned`. `problems` says
what makes it ungradeable or confusing (a back asking for several meanings while each answer is
one alternative, `/` without spaces, an alternative over 40 letters); `overlaps` lists the cards
with the same prompt (when one of them is asked for) or the same answer.

| Command | Changes |
|---|---|
| `edit <id>` | `--front`, `--back`, `--note` (replaces the back's `(…)`; `""` removes it), `--category`. Every other field stays. The old texts go into the card's `edits` list. A front with nothing to type is refused unless `--force`. |
| `regrade <id> --quality Q` | A review graded wrong through the card's fault: the review of `--date` (default: the latest) gets quality `Q`, marked `regraded` with the old one, and SM-2 is replayed over the history from a new card's state, exactly as update-db.py would have left it. Refused when the card's state doesn't follow from its history (changed by hand) unless `--force`. A card never reviewed (a pack word's first practice): its `last_quality` becomes `Q`. |
| `merge <keep> <drop>` | `<drop>` is deleted; its whole item is kept in `<keep>`'s `merged` list. `<keep>` keeps its scheduling (and takes `--front`/`--back`/`--note`). |
| `split <id> …` | `<id>` keeps its id, history and scheduling (with `--front`/`--back`/`--note`); `--new-id` is a new card (due tomorrow, as `new_vocabulary` makes one, `split_from` set). |
| `forgive <pattern>` | `mistakes-db.json`: one example of the pattern (of `--date`, default its latest; `--answer` picks one of several) is removed, `frequency` and `consecutive_incorrect` go down by one and the removal is kept in `forgiven`. The last example removes the pattern, and the review item update-db.py made of it when it was never reviewed. |

- Every command takes `--data DIR` (default: the learner's data directory) and `--language CODE`
  (another language's cards, `data/languages/<code>/`). The writing ones take `--reason` (kept
  with the change), `--dry-run` (print, write nothing) and `--today YYYY-MM-DD`.
- Before writing, the files it changes are copied to `.backups/pre-card-<command>-<time>/`; each
  is written with `.tmp` + `fsync` + rename. A regrade, merge or split rebuilds `review_queue`.
- The app reads the cards through `GET /state` (read-db.py) each time it goes Home: no restart.
  Keep ids: a review the app sends later is saved by id.
- Prints JSON. Exit codes: `0` OK, `1` invalid input or refused (nothing written), `2` I/O error.

## Languages

A learner has a **language profile per language**: the home language (the target of their
own town) and every language they practise elsewhere, e.g. Italian on visits to a town that
speaks it (plan 2, §3.4). Each has its own spaced-repetition items, mistakes, skills and
mastery, level, progress stats, session log and streak.

```
data/
  learner-profile.json      the learner, and the home language's profile:
                            "home_language": "sl",
                            "languages": [{"code": "it", "name": "Italian", "level": "A1",
                                           "source": "visits", "since": "2026-10-02"}]
  progress-db.json …        the home language's other five databases, as always
  languages/
    it/                     the same six files, in the same shapes, for Italian
      learner-profile.json  only what is Italian's: learner.target_language(_code),
                            current_level, target_level; source, streak, sessions,
                            minutes, skills, achievements
      progress-db.json  mistakes-db.json  mastery-db.json
      spaced-repetition.json  session-log.json
```

- **The level** of another language is in its own `learner-profile.json`
  (`learner.current_level`). The home profile's `languages[].level` is a copy for a
  glance, refreshed by every `update-db.py` write in that language.
- **`source`**: `home` for the town's language; `visits` for a language practised on
  visits (today the only other way).
- **The streak** is per language: practice in Italian doesn't make or break the home
  language's streak, and the Lani app's stats, words learned, rusty words and the
  village's ages count the home language only.

**Why a directory per language, and not a `language` field on every item.** Everything
that reads the databases reads the home files directly: the tutor's skills
(`lani-progress`, `lani-review` and others open `data/*.json`), the bridge
(`spaced-repetition.json` for packs, word lookups, the family page and voice cards), the
app (`GET /state` for the dashboard, stats, the review deck and the village's ages), and
the hooks. With a field per item, each of them would have to filter by language, or the
Italian cards would mix into the Slovene review, word counts and village ages. With a
directory per language, the home files keep exactly what they had, so every reader stays
right without a change. Only code that wants another language asks for it (`--language`,
a report's `language`). That language's files have the same names and shapes, so the same
code and skills read them from another directory.

### Migration

Data from before language profiles (a `learner-profile.json` without `home_language`) is
migrated automatically the next time `read-db.py` or `update-db.py` runs:

1. Every database in `data/` is copied to `.backups/pre-migrate-languages-<time>/`.
2. The profile gets `home_language` (the code of its `learner.target_language_code`, or of
   its `target_language`: `"Slovene"` → `"sl"`) and `languages: []`. Nothing else changes,
   and no other file is written.

Once migrated, the check is a read of the profile and nothing else: no backup, no write.
A profile whose target language isn't known yet (a template's `{placeholders}`) is left as
it is. A failed migration is a warning on stderr; reading and writing go on.

## Data model notes

- `learner-profile.json` stores `confidence` per skill as an integer 0–100.
- `progress-db.json` stores `accuracy` values as floats 0.0–1.0.
- `session-log.json` sessions use `skills_practiced` (array), `score_breakdown`
  (per-skill float accuracy), `topics_covered`, `breakthroughs`,
  `focus_next_session`, `achievements_earned`. Session IDs are
  `session-NNN`.
- `spaced-repetition.json` items preserve `consecutive_correct/incorrect`,
  `mastery_level`, `total_reviews`, `priority`, `content`, `answer`,
  `category`, `difficulty` — supply these in `new_vocabulary` payloads so new
  items are fully populated.
- `milestones[]` accepts **either** a bare string **or** an object
  `{ "milestone": <required non-empty string>, "date": <optional YYYY-MM-DD,
  defaults to the session date> }`. A nested `session_id` is ignored — the
  script always stamps each milestone with the authoritative top-level
  `session_id`. An entry that is neither a string nor an object, or an object
  whose `milestone` is missing/empty/non-string, is a validation error (exit
  `1`, no files written). A present-but-unparseable `date` silently falls back
  to the session date.
