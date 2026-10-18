# The learner-data scripts in TypeScript (plan 5, the hosted mode)

The phone's bridge runs in QuickJS, without Python. The learner-data scripts the bridge calls (`read-db.py`,
`update-db.py`, `card.py`, `validate-data.py`, `ensure_data_dir.py`) now have a TypeScript twin in
`companion/bridge/src/db/`. It behaves exactly as the Python does: the same stdout and stderr, the same exit codes, the
same files, byte for byte. The node keeps running the Python, as before. A differential test runs both on the same
fixtures and keeps them the same.

## What

| File | Port of |
|---|---|
| `cli.ts` | the entry: `run(script, argv, stdin, env, options?)`, and `bun companion/bridge/src/db/cli.ts <script> [args]` |
| `read-db.ts` | `read-db.py` |
| `update-db.ts` | `update-db.py` (SM-2, the gentle lowering, items-only reports, resurfaced forms cards, other languages) |
| `card.ts` | `card.py` (find, show, overlaps, edit, regrade, merge, split, forgive) |
| `validate-data.ts` | `validate-data.py` (Claude Code's hook after an edit: the checks on import) |
| `ensure-data-dir.ts` | `ensure_data_dir.py` (the bridge's config asks it on start) |
| `paths.ts` | `lani_paths.py`: the data directory from the environment, lani.env, the `FLUENT_*` names |
| `languages.ts` | `lani_languages.py`: the language profiles, the summaries, the migration |
| `data-repo.ts` | `lani_data_repo.session_message`. The commit itself is the host's (see "The hook" below) |
| `py.ts` | Python's semantics: dicts in order, ints and floats apart, the operators, `round()`, `str()`/`repr()`, the errors, `strptime`, code-point strings |
| `pyjson.ts` | `json.loads` (as CPython's C scanner reads, with its error messages) and `json.dump(indent=2, ensure_ascii=False)` |
| `pyargparse.ts`, `argtexts.ts` | argparse, as far as `read-db.py` and `card.py` use it, with their usage and help texts |
| `sys.ts` | the process: the environment, the working directory, stdout and stderr, the clock, the files (node:fs with Python's errors), pathlib's paths |

## How

Each script is a line-by-line translation of the Python onto `py.ts`. These are the details that keep the files the
same:

- **Numbers:** an int is a JS number (a bigint past 2^53), a float is a `PyFloat`. So a `2.0` read from a file is
  written back as `2.0`. `add`, `mul`, `truediv` and the rest give Python's result types: `int + float` is a float,
  `round(x)` an int, `round(x, 3)` a float, and `max(1.3, ef)` gives back the first one on a tie. The rounding is
  CPython's: on the float's exact binary value, ties to even (`round(0.125, 2)` is `0.12`).
- **Floats as text:** `repr` (`1e-05`, `1e+16`, `0.30000000000000004`) for `json.dump` and for `str()`, and `'%.0f'`
  for the summary line.
- **Dicts:** a `PyDict` keeps the insertion order (a JS object puts `"0"`…`"9"` keys first). A key that isn't a str
  (an int `item_id` in a report) is kept the way Python keeps it.
- **Strings:** lengths and slices go by code point (`len(text) <= 40`, `text[:30]`), comparisons too (`"9999" <=
  today`). `strip()` and `\s` use Python's whitespace. `\d` is Unicode's (`２０２６-04-05` passes `strptime`, as in
  Python). `$` in `re.match` also takes a trailing newline, as Python's does.
- **Errors:** Python's types and messages: `TypeError: unsupported operand type(s) for +: 'int' and 'str'`, `KeyError:
  'correct'`, `[Errno 2] No such file or directory: '/x'`, `Expecting ',' delimiter: line 3 column 5 (char 41)`. They
  reach stderr where the Python's do. An exception nothing catches prints a traceback whose last line is Python's
  (the frames can't be the same) and exits 1.
- **Files:** `.json.tmp`, then fsync, then `os.replace`. The backups have the same names (`pre-update-<id>`,
  `pre-words-<date>`, `pre-card-<command>-<time>[-n]`, `pre-migrate-languages-<time>`, `<file>.backup-<time>`) and
  the same contents (`shutil.copy2`, with the times where the host can set them). A file is read as strict UTF-8 with
  universal newlines, as `open()` reads it. stdin is read without newline translation, as `sys.stdin` reads it.
- **The clock:** `datetime.now()` is the local time. `LANI_NOW` (`YYYY-MM-DD[THH:MM[:SS]]`) freezes it for both
  implementations: `lani_paths.now()` reads it in the Python, `Ctx.now()` in the port. Without `LANI_NOW`, the
  Python does what it always did.
- **The environment:** what `lani_paths` does on import (the `FLUENT_*` names, lani.env's settings for the machine)
  is done first, on a copy of the `env` given to `run()`.

Only node:fs (sync calls) is used, plus Date. There is no Bun API, no `child_process`, no `os` (HOME comes from the
environment), no `import.meta`. The command-line part runs only where there is a `process`. The phone's fs shim needs
these calls:

- **Required:** `statSync` (`isFile`, `isDirectory`, `atime`, `mtime`), `readFileSync` (bytes; a string is taken
  as it is), `writeFileSync`, `renameSync`, `mkdirSync` (not recursive), `copyFileSync`, `readdirSync`, `unlinkSync`.
- **Optional** (looked up by name, used when they are there): `openSync`, `writeSync`, `fsyncSync` and `closeSync`
  (the fsync before the rename), `lstatSync` and `readlinkSync` (symlinks in `Path.resolve()`), `utimesSync` (the
  backups' times).

An error with a `code` (`ENOENT`, `EACCES`, `EISDIR` …) becomes Python's OSError of that errno.

## How to use

```ts
import { run, scriptOf } from './db/cli'

// what `python3 .claude/hooks/update-db.py < report.json` does, in [env]
const r = run('update-db', [], JSON.stringify(report), env, { cwd: projectDir, hooks })
r.status // 0, 1 or 2, as the Python's exit code
r.stdout // the same text
r.stderr

scriptOf('.claude/hooks/card.py') // 'card': for a dispatcher of `python3 .claude/hooks/<script>.py` spawns
dataDirFor(env, { cwd })          // lani_paths.data_dir(): learners.ts's `python3 -c "… print(data_dir())"`
```

- **The phone runtime** turns the bridge's `python3 .claude/hooks/<script>.py …` spawns (learner.ts, config.ts) into
  `run()` calls. It passes the spawn's environment (`CLAUDE_PROJECT_DIR` …), its working directory, and stdin.
  `LANI_DATA_DIR=<filesDir>/lani/data` and `HOME` should be in the environment: the data directory is then never
  searched for. Everything a script writes is inside the data directory: the databases, `.backups/`, and a
  `<file>.backup-*` next to the file `validate-data` checks.
- **The hook:** `options.hooks.afterWrite({script, dataDir, paths, commit?})` is called after a run that wrote
  files, with every path written (the databases and their backups). `commit` is set where the Python commits a data
  repository: `update-db` after a session, `lani_data_repo.autocommit(data, session_message(report))`. A host with git
  may commit then. What the hook returns goes to stderr (the Python's `📚 data repository: …` line). On the phone,
  nothing commits for now.
- **On a node:** nothing changes. `learner.ts` and `config.ts` keep spawning Python. `bun companion/bridge/src/db/cli.ts
  read-db` runs the twin by hand, with the same arguments and stdin as the Python.

## The differential test

`tests/test_db_port.py` (found by `python3 -m unittest discover -s tests`; skipped without bun) and the `*DiffTest`
classes added to `tests/test_update_db.py`, `tests/test_card.py` and `tests/test_languages.py` run every case through
both implementations (`tests/db_impl.py`, `run("diff", …)`). The two run on identical copies of the test's directory,
at the same path, one after the other, with the same environment and the clock frozen. The exit codes, stdout,
stderr and every file under the directory must be equal, byte for byte. Of a Python traceback, the text before it
and its last line must be equal. A difference fails the test with a diff.

- **The existing cases**, all through both: test_update_db (30), test_card (21), test_languages (16).
- **The building blocks** (`companion/bridge/test/db-port-probe.ts`, about 12,000 cases):
  - `json.loads` and `json.dump`: about 530 documents (the templates, odd documents, 480 mutated documents with their
    errors);
  - floats: `repr`, `round(x)`, `round(x, 0/2/3)` and `'%.0f'` of about 1,600 values, the ties among them;
  - `strptime` and date arithmetic;
  - `int()`, `float()`, `strip()`, `lower()`, `repr()`, `len()`.
- **Scenarios:**
  - six weeks of a learner in Slovene, Italian and German: reviews, gentle lowerings, new words, counting and forms
    cards, mistakes, milestones, levels, items-only reports, resurfaced forms cards; read-db between them; then every
    card.py command on what they made;
  - the reports and commands of docs/DB_SCRIPTS.md;
  - the migration, with unlisted and broken language directories;
  - bad reports, odd values and broken files: 1, 2 and the tracebacks;
  - the command lines' errors and help texts;
  - where the data directory is found: `LANI_DATA_DIR` (relative, `~`, a symlink), lani.env, `LANI_PROFILE`,
    `FLUENT_DATA_DIR`, `$CLAUDE_PROJECT_DIR/data`, `./data`, `~/.claude/lani-data` and `fluent-data`;
  - validate-data.py and its rotation;
  - empty and odd data directories, and the corners of card.py.
- **In process:** nine `run()` calls in one process, as the phone makes them, against the Python, and the hook's
  calls (the commit message equals the Python's `session_message`). Also `read-db` without `LANI_NOW` in four time
  zones.

In all: 88 test cases and 623 differential runs of the five scripts (read-db 87, update-db 264, card 244,
ensure_data_dir 15, validate-data 13), plus the 17 in-process runs. They cover every card.py command, the exit codes
0, 1 and 2 of each script (ensure_data_dir: 0 and 1), and 48 Python tracebacks. All are the same.

## What isn't the same (known, and left)

- **Traceback frames:** only the last line is Python's.
- **Unicode tables:** the JS engine's version, not Python 3.14's Unicode 16.0. This touches `lower()`, NFD, `\w`,
  `\d` and `str.isprintable` (for `repr`) in scripts added since: a handful of new letters. The canonical combining
  classes (`card.py find`'s accent folding) are Python's own, copied.
- **argparse's texts** are Python 3.14's at 80 columns. A `COLUMNS` that wraps them otherwise, or `FORCE_COLOR`, isn't
  followed.
- **`~user` paths** stay as they are (no password database). `Path.resolve()` is lexical where the host has no
  `lstatSync`/`readlinkSync`.
- **A lone surrogate** in a database fails to be written in both. Python leaves a partial `.tmp` file; the port writes
  nothing, and the position in its message is the whole text's.
- **Git:** the commit after a session is the host's (the hook). The port never runs git.
- **A glob metacharacter** in a file name that `validate-data` checks is taken literally.
- **Not ported:** `session-start.py`, `session-end.py`, `precompact-backup.sh` (Claude Code's own hooks; plan 5 drops
  them in the hosted mode) and the rest of `lani_data_repo.py` (init, push).

## Python oddities found on the way (not fixed)

1. **read-db.py fails for every language** when one other language's `learner-profile.json` isn't valid JSON, or
   its `learner` isn't an object. The exit is 1, with a traceback (`TypeError: 'str' object is not a mapping`). The
   summaries load every language, so the home language's `GET /state` fails too.
2. **A date with a one-digit month or day** (`"2026-4-24"`) passes the checks: `strptime` takes it, and a full
   session's `date` isn't checked at all. The scripts then compare dates as strings, and `"2026-4-24"` sorts after
   `"2026-04-30"`. This affects the due dates, the resurface window, the review queue and the streak (`last_updated`
   is never `yesterday()`'s padded form).
3. **`re.match(...$)` takes a trailing newline.** `card.py --today $'2026-09-27\n'` passes the `--today` check, then
   fails in `strptime` with a traceback (exit 1) instead of `--today: YYYY-MM-DD`. A `home_language` of `"sl\n"` is
   valid for lani_languages, and a report language is then compared with it as another language.
4. **A `session_id` with `/` or `..`** makes the backup's path: `.backups/pre-update-a/b`, and enough `../` reaches
   outside `.backups/` and the data directory.
5. **A failing backup ends in a traceback** (exit 1), not exit 2 as documented: `backup_all` runs outside the `try`.
   A directory named `*.json` in data/ does it.
6. **An int `item_id`** in `new_vocabulary` is kept: the item's key is `"5"` in the file, and its `id` is `5`.
7. **`card.py find` and `show`** fail with a traceback when a card's `content` or `answer` isn't a string (a number).
8. **validate-data.py opens the file without an encoding** (the locale's). The other scripts read UTF-8.

## What's left

- **The phone runtime's dispatch** of the bridge's spawns to `run()`: another agent, on the shared contract.
- **A run in QuickJS:** not done here, as no `qjs` is on this machine. A `bun build --target browser --format iife`
  bundle of `cli.ts` (with node:fs left out) builds at about 170 KB, without `import.meta`. A node-targeted build
  gives the Python's output under plain Node.
- **Speed in QuickJS:** not measured. Under bun, on a deck of 4,000 cards (2.8 MB), the outputs are the same and the
  times close to the Python's: read-db 0.23 s (Python 0.16 s, most of it bun's start), `card.py find` 0.21 s (0.20 s),
  `card.py overlaps` 59 s (105 s: it compares every pair). An interpreter is slower: `overlaps` of a big deck isn't
  for the phone.
- **Regenerating `argtexts.ts`** when card.py's or read-db.py's options change. The differential test's help and
  usage cases fail until it is done. Rebuild it from `parser().format_usage()` and `format_help()` of each parser.
