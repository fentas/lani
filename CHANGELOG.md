# Changelog

All notable changes to Lani's Claude Code tutor (the `lani` plugin) are documented in this file. Versions up to 0.3.0
are from the kit it grew from, [m98/fluent](https://github.com/m98/fluent), and keep its names.

## [0.4.0] — 2026-10-03

### Breaking changes

The plugin, its skills and the app's MCP server are named after Lani (see `docs/migrate-from-fluent.md`):

| Old | New |
|-----|-----|
| plugin `fluent@lani` | `lani@lani` (`claude plugin install lani@lani`) |
| `/fluent-setup`, `/fluent-learn`, `/fluent-review`, … `/fluent-fix` | `/lani-setup`, `/lani-learn`, `/lani-review`, … `/lani-fix` |
| `fluent-studio`, `fluent-db-updater`, … (helper skills) | `lani-studio`, `lani-db-updater`, … |
| MCP server `fluent` (tools `mcp__fluent__*`, `<channel source="fluent">`) | `lani` (`mcp__lani__*`, `<channel source="lani">`) |
| `FLUENT_DATA_DIR` and the other `FLUENT_*` variables | `LANI_DATA_DIR`, `LANI_*` (a `FLUENT_*` variable is still read when its `LANI_*` name isn't set) |
| `~/.claude/fluent-data/` (plugin data) | `~/.claude/lani-data/` (the old directory is used while the new one doesn't exist) |

New session result files are `/results/lani-{skill}-session-{NNN}.md`; the older `fluent-{skill}-…` and
`{skill}-…` files are still read.

## [0.3.0] — 2026-06-15

### Added

- Milestones support in the `update-db.py` session payload. The new
  `milestones[]` field accepts either a bare string or an object
  `{ "milestone": <required non-empty string>, "date": <optional YYYY-MM-DD,
  defaults to the session date> }`. Each milestone is recorded in both
  `session-log.milestones[]` and `learner-profile.achievements[]`. Validation
  rejects malformed entries (exit `1`, no files written); an unparseable
  `date` falls back to the session date.

## [0.2.1] — 2026-06-11

### Fixed

- Hooks no longer fail on Windows with `No such file or directory` (#5).
  Plugin hook commands in `hooks.json` used the bash default-value syntax
  `${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}`, which Claude Code's own
  variable substitution does not understand on Windows — it replaced the
  variable names but left the `:-` separators literal, producing a single
  garbage path. Hook commands now use plain `${CLAUDE_PLUGIN_ROOT}` (always
  set for plugin hooks) and invoke scripts via an explicit `python3`/`bash`
  interpreter so they don't depend on shebang handling under Git Bash.

## [0.2.0] — 2026-05-14

### Breaking changes

All 12 skills renamed with a `fluent-` prefix to prevent collisions with other
plugins and Claude Code built-ins. Update any muscle memory or external
references.

| Old | New |
|-----|-----|
| `/setup` | `/fluent-setup` |
| `/learn` | `/fluent-learn` |
| `/review` | `/fluent-review` |
| `/vocab` | `/fluent-vocab` |
| `/writing` | `/fluent-writing` |
| `/speaking` | `/fluent-speaking` |
| `/reading` | `/fluent-reading` |
| `/progress` | `/fluent-progress` |
| `sm2-calculator` | `fluent-sm2-calculator` |
| `db-updater` | `fluent-db-updater` |
| `feedback-formatter` | `fluent-feedback-formatter` |
| `session-analyzer` | `fluent-session-analyzer` |

New session result files use `/results/fluent-{skill}-session-{NNN}.md`.
Existing files using the older `{skill}-session-{NNN}.md` naming are still
read by `fluent-session-analyzer` — no migration required.

### Fixed

- Plugin install no longer fails on first DB read. Skills now invoke helper
  scripts via `${CLAUDE_PLUGIN_ROOT:-${CLAUDE_PROJECT_DIR:-.}}/.claude/hooks/...`
  so the path resolves regardless of CWD.
- Added missing `.claude/hooks/ensure_data_dir.py` referenced by
  `fluent-setup`.

### Migration

```bash
claude plugin update fluent@m98
```

Then use the new slash commands. Your data (`~/.claude/fluent-data/` or
`./data/`) is unchanged.

## [0.1.0] — 2026-03-15

Initial release.
