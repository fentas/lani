# Moving an install from Fluent to Lani

Until October 2026 the project was called Fluent (after [m98/fluent](https://github.com/m98/fluent), the kit it grew
from). It is now Lani, in its own repository, [fentas/lani](https://github.com/fentas/lani). This page is for a node
that runs the Fluent checkout today: what changed, and how to move to a fresh Lani checkout without losing anything.

Below, `$OLD` is the Fluent checkout and `$NEW` the Lani one.

## What changed

| What | Fluent | Lani | Old name still works? |
|---|---|---|---|
| Android app | `si.lanisce.fluent`, "Fluent", 0.1.N (code N, up to 629) | `si.lanisce.lani`, "Lani", 0.2.N (code 1000 + N) | No: a new app, installed beside the old one |
| Scripts | `companion/bin/fluent-{session,bridge,pair,profile,town,backup}` | `companion/bin/lani-*` | No (they are in the old checkout) |
| tmux sessions | `fluent`, `fluent-<id>`, `fluent-bridge(-<id>)` | `lani`, `lani-<id>`, `lani-bridge(-<id>)` | A profile registered before keeps its stored names (see step 4) |
| Remote Control | `fluent-tutor`, `fluent-tutor-<id>` | `lani-tutor`, `lani-tutor-<id>` | As above |
| MCP server | `fluent` (`mcp__fluent__*`, `<channel source="fluent">`) | `lani` (`mcp__lani__*`, `<channel source="lani">`) | No: restart the tutor from the new checkout |
| Skills | `/fluent-learn`, `/fluent-review`, … `/fluent-fix`, `fluent-studio` | `/lani-learn`, `/lani-review`, … `/lani-fix`, `lani-studio` | No |
| Plugin | `fluent@lani` | `lani@lani` | No |
| Environment | `FLUENT_*` | `LANI_*` | Yes: `FLUENT_X` is read when `LANI_X` isn't set |
| Config | `~/.config/fluent/` (`keys.env`, `bridge-mode`) | `~/.config/lani/` | Yes: each file is read from the old directory while the new one doesn't have it |
| State, cache, data | `~/.local/state/fluent`, `~/.cache/fluent`, `~/.local/share/fluent` (backups, built dictionaries) | the same with `lani` | Yes: the old directory is used while the new one doesn't exist |
| Plugin data | `~/.claude/fluent-data/` | `~/.claude/lani-data/` | Yes, as above |
| Voice and speech workers | `fluent-voice.service`, `fluent-stt.service`, `~/.local/share/fluent-{voice,stt}`, `~/.config/fluent-{voice,stt}.env` | `lani-voice.service`, `lani-stt.service`, … | Yes: the installers reuse the old directory and read the old `.env` |
| Backup timer | `fluent-backup(.timer)` | `lani-backup(.timer)` | Snapshots stay readable (see step 7) |
| Content schema ids | `fluent.pack/v0`, `fluent.scene/v0`, … | `lani.pack/v0`, … | Yes: every reader takes both; what a tutor published into a learner's data still loads |
| Pairing codes, invitations | `fluent://pair?…`, `fluent://town?…`, signed `fluent-pair/1` | `lani://…`, `lani-pair/1` | Yes: the new app reads both and verifies both |
| Town requests | `x-fluent-town-*`, `fluent-town/1` | `x-lani-town-*`, `lani-town/1` | Yes: a bridge reads both and signs under both, so old and new towns talk |
| Session result files | `results/fluent-{skill}-session-NNN.md` | `results/lani-{skill}-session-NNN.md` | Yes: the analyzer reads both |
| QA emulator | AVD `fluent-qa` | AVD `lani-qa` (`LANI_QA_AVD`) | Yes: `fluent-qa` is used while there is no `lani-qa` |

The ports stay the same (8790 for the default learner's bridge, 8792 and up for other learners, 8795 voice, 8796
speech), so `tailscale serve` and the phone's address don't change.

## Steps

### 1. Take a snapshot

```bash
$OLD/companion/bin/fluent-backup run
$OLD/companion/bin/fluent-backup --profile <id> run    # each other learner
```

### 2. Clone Lani

```bash
git clone https://github.com/fentas/lani.git $NEW
cd $NEW/companion/bridge && bun install
cp $OLD/companion/android/local.properties $NEW/companion/android/    # the Android SDK path, to build the app
cp $OLD/.claude/settings.local.json $NEW/.claude/                     # optional: your local Claude Code permissions
sed -i 's/mcp__fluent__/mcp__lani__/g; s/"fluent"/"lani"/g' $NEW/.claude/settings.local.json   # the MCP server's new name
```

### 3. Stop the old tutor and bridges

Choose a quiet moment (no role-play running).

- Classic mode (the bridge inside the tutor session): `tmux attach -t fluent`, then `/exit`. Its bridge exits and
  frees 8790.
- Service mode: also `$OLD/companion/bin/fluent-bridge stop` (and `--profile <id>` for each other learner).
- Each other learner: `tmux attach -t fluent-<id>`, then `/exit`.

### 4. Copy the learner data

`data/`, `results/` and `profiles/` are not in git; copy them as they are (the voice store, the app's state and the
tokens live in `data/app/`):

```bash
cp -a $OLD/data/.     $NEW/data/
cp -a $OLD/results/.  $NEW/results/
cp -a $OLD/profiles   $NEW/            # if you have other learners
git -C $NEW checkout -- data/README.md results/README.md   # the copies brought the old READMEs along
```

Nothing in the data needs converting. In `profiles/profiles.json` each learner keeps the tmux session and
Remote Control name stored when it was added (`fluent-<id>`, `fluent-tutor-<id>`) and an `env` with
`FLUENT_VOICE_DESIGN` (read as `LANI_VOICE_DESIGN`). That works as it is; for the new names, change them to
`lani-<id>`, `lani-tutor-<id>` and `LANI_VOICE_DESIGN` while no session runs.

### 5. Keys, configuration and directories

Nothing has to move: `~/.config/fluent/keys.env` and `~/.config/fluent/bridge-mode` are read while
`~/.config/lani/` doesn't have them, and `FLUENT_*` lines in `keys.env` (`FLUENT_LEXICON_URL`, …) are read as
`LANI_*`. The state, cache and backup directories under `fluent` are used while their `lani` ones don't exist.

To use the new names (optional; with nothing running):

```bash
mkdir -p ~/.config/lani
mv ~/.config/fluent/keys.env ~/.config/fluent/bridge-mode ~/.config/lani/ 2>/dev/null
sed -i 's/^FLUENT_/LANI_/' ~/.config/lani/keys.env
mv ~/.local/share/fluent ~/.local/share/lani          # backups and built dictionaries
mv ~/.cache/fluent ~/.cache/lani
mv ~/.local/state/fluent ~/.local/state/lani
```

A `LANI_LEXICON_URL` (or any other variable) that points into `~/.local/share/fluent/…` must then point to the new
place. Variables you export in your shell profile (`FLUENT_*`) keep working, but new docs only name `LANI_*`.

### 6. Start the tutor from the new checkout

```bash
cd $NEW
companion/bin/lani-session                      # tmux "lani", Remote Control "lani-tutor"
companion/bin/lani-session --profile <id>       # each other learner
```

On the first start, confirm the development channel and approve the `lani` MCP server (`.mcp.json`). The tutor's
tools are now `mcp__lani__*` and the skills `/lani-*`: a session still running from the old checkout has the old
names, so restart every tutor from `$NEW` rather than resuming an old one. In the service mode `lani-session` starts
`lani-bridge` itself (`companion/bin/lani-bridge status` shows it).

Claude Code keeps a project's memory and history under a directory named after its path
(`~/.claude/projects/<the path with / as ->/`): the new checkout is a new project. To keep the tutor's notes, copy
the old project's `memory/` directory into the new project's directory (start Claude Code in `$NEW` once to create
it).

A plugin install (`claude plugin install fluent@lani`) moves with
`claude plugin uninstall fluent@lani && claude plugin install lani@lani`; its data stays in
`~/.claude/fluent-data/` until `~/.claude/lani-data/` exists.

### 7. Backups, voice and speech

- Backups: `$OLD/companion/bin/fluent-backup uninstall` (and `--profile <id> uninstall`), then
  `$NEW/companion/bin/lani-backup install` (and `--profile <id> install`). The snapshots in
  `~/.local/share/fluent/backups` stay where they are and are listed and restored as before, while
  `~/.local/share/lani/backups` doesn't exist.
- The voice and speech workers keep running as `fluent-voice.service` and `fluent-stt.service`: same ports, same
  servers, nothing to do. The next time you update them, `$NEW/companion/voice-local/install.sh` (and
  `stt-local/install.sh`) stops and removes the old unit, installs `lani-voice.service` (`lani-stt.service`) with the
  existing `~/.local/share/fluent-voice` (no download again), and keeps reading `~/.config/fluent-voice.env`.
- A bridge under systemd (`companion/systemd/`): the template is now `lani-bridge@.service`.

### 8. The app

The renamed app is a new package: install it once by hand, pair it, then remove the old one.

1. Before you switch, open the old app once while it is connected, so that its outbox (answers not yet sent) is
   empty.
2. Build and publish it: `cd $NEW && companion/bin/release-app "Lani"` (writes `data/app/release/lani-<code>.apk`,
   versionCode 1000 + the commit count). Copy that APK to the phone (adb, Taildrop) and open it to install. The old
   app may offer it as an update; that would install it as a second app too.
3. Pair it: `companion/bin/lani-pair` (`--profile <id>` for another learner) and scan the code in the new app.
   The village, the reviews, the stats and the grammar book come from the node, as before.
4. Phone-only data doesn't carry over: the chat archive (older tutor messages), the app's settings (language pair,
   sounds, the dialog and speech settings, the family page settings on the phone), the home-screen widget (add it
   again), what was cached for offline play and the car (fetched again). In Android Auto, allow the new app as you
   did for the old one (Android Auto's developer settings, "Unknown sources").
5. Uninstall the old app (`si.lanisce.fluent`) when the new one works.

### 9. Check, then retire the old checkout

- `companion/bin/lani-bridge status` (service mode), `companion/bin/lani-session --list`, a chat from the app.
- `companion/bin/lani-backup status` shows the timer and the newest snapshot.
- Keep `$OLD` until everything works, then archive it.
