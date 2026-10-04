---
name: lani-update
description: Compare this Lani clone against upstream (fentas/lani) and optionally pull the latest skills, hooks, and docs — without ever touching learner data. Triggered only when the learner types /lani-update. Shows what changed upstream (new commits, changed files, CHANGELOG entries) and local modifications that could conflict, then asks before updating.
allowed-tools: Read, Bash, AskUserQuestion
disable-model-invocation: true
---

# Lani Update (clone mode)

## Overview

Clone-mode installs don't get `claude plugin update` — the system files (`.claude/skills/`, `.claude/hooks/`, `LEARNING_SYSTEM.md`, docs) only update via git. This skill fetches upstream, reports the gap between the local clone and `origin/main`, surfaces local modifications that could conflict, and — only after explicit confirmation — pulls the update.

Learner data is safe by construction: `data/*.json`, `results/*.md`, and `.backups/` are gitignored, so no git operation in this skill can modify or delete them. Never claim otherwise, and never run commands that would change that (no `git clean`, no `git checkout -- .`, no `git reset --hard`).

## When to Use

Trigger only when the learner types `/lani-update`. Gated with `disable-model-invocation: true` — the update step rewrites system files mid-session.

Skip this skill for plugin-mode installs (no `.git` in the Lani root); those update with `claude plugin update lani@lani` instead.

## Instructions

### 1. Fetch and compare

```bash
git fetch origin
BEHIND=$(git rev-list --count HEAD..origin/main)
AHEAD=$(git rev-list --count origin/main..HEAD)
echo "behind=$BEHIND ahead=$AHEAD"
```

If `behind=0`: report "✅ Up to date with upstream" (mention `ahead` count if non-zero — local commits the learner made), show current version from `CHANGELOG.md` heading, and stop.

### 2. Report what's new upstream

```bash
git log --oneline --no-merges HEAD..origin/main
git diff --stat HEAD...origin/main
git diff HEAD...origin/main -- CHANGELOG.md | grep '^+' | head -40
```

Present a compact summary:

```markdown
## 🔄 Lani Update Check

**Local:** {current version / HEAD short sha}
**Upstream:** {origin/main short sha} — **{N} commits ahead of you**

### What changed
- {grouped summary: skills touched, hooks touched, docs, version bumps}

### Changed files
{diff --stat, grouped: .claude/skills/, .claude/hooks/, docs, other}
```

Call out anything that changes behavior the learner relies on (hook scripts, `update-db.py` schema, skill flows).

### 3. Check local drift

```bash
git status --porcelain
git stash list
```

Classify each dirty tracked file:

- `.claude/settings.local.json` — expected: personal permissions live here even though upstream tracks the file. Flag as "will be preserved via stash".
- Anything else tracked and modified — show the learner a short diff and ask whether to keep it.
- Untracked files — irrelevant, git pull ignores them. Don't list learner data noise.

### 4. Confirm before updating

Use `AskUserQuestion`: **Update now** / **Just show me the diff** / **Skip**. Never pull without the learner choosing to.

For "show me the diff": `git diff HEAD...origin/main -- .claude/ LEARNING_SYSTEM.md PRACTICE.md CLAUDE.md` (system files only) and stop.

### 5. Update

Clean tree:

```bash
git pull --ff-only origin main
```

Dirty tracked files (the normal case — `settings.local.json`):

```bash
git stash push -m "lani-update $(date +%Y%m%d-%H%M%S)" -- {dirty tracked files}
git pull --ff-only origin main
git stash pop
```

- If `--ff-only` fails (local commits diverge), stop and explain; offer `git rebase origin/main` only if the learner made those commits knowingly.
- If `stash pop` conflicts: resolve by keeping the learner's values for `settings.local.json` (their permissions) while adopting upstream's new keys; show the result. If unsure, keep both versions visible and ask.

### 6. Wrap up

```markdown
## ✅ Updated to {new version}

**Highlights:** {2-3 bullet summary from CHANGELOG/commits}
**Preserved:** your learner data (untouched by design) + local settings
**Next:** restart Claude Code so updated skills and hooks load — they're read at session start.
```

If hook scripts changed, explicitly note the restart is required, not optional.

## Examples

### Example 1 — up to date

> ## 🔄 Lani Update Check
>
> ✅ You're up to date with upstream (v0.3.0, `86fb80f`).
> Nothing to do — see you at `/lani-review`!

### Example 2 — update available

> ## 🔄 Lani Update Check
>
> **Local:** v0.3.0 · **Upstream:** 4 commits ahead
>
> ### What changed
> - 📚 `/lani-vocab`: new image-association exercise type
> - 🔧 `update-db.py`: fixes weekly summary rollover bug
> - 📄 CHANGELOG: v0.4.0
>
> ### Local changes that need care
> - `.claude/settings.local.json` — your permission rules (will be stashed and restored)
>
> Update now? (I'll stash your settings, fast-forward, and restore them.)

## Critical Rules

- **Never touch learner data.** No `git clean`, `git reset --hard`, or `git checkout -- <path>` — ever. `data/`, `results/`, `.backups/` are gitignored and must stay untouched.
- **Fetch and report freely; pull only after explicit confirmation.**
- **Fast-forward only.** Diverging histories are a conversation, not an auto-rebase.
- **Preserve local settings.** `settings.local.json` conflicts resolve in the learner's favor.
- **Recommend a restart after updating** — skills and hooks load at session start.
