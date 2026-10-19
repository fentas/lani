# Lani

An Android app connected to an always-on Claude tutor session. The session is your personal
teacher. You can reach it from the app, from the terminal, or through claude.ai Remote Control.
It also builds the app's practice content as you progress.

```
Android app ──HTTP/SSE (Tailscale)──► lani-bridge ──channel (stdio MCP)──► claude session (tmux)
                                         │                                  └─ skills: lani-*, lani-studio
                                         └─ <data>/app/modules/<id>/vN.json   (published by Claude)
```

In the service mode ([The bridge as a service](#the-bridge-as-a-service)), the bridge runs on its own and the
session holds only a small shim:

```
Android app ──HTTP/SSE──► bridge service ◄──unix socket (MCP)──► shim ◄──stdio (MCP)──► claude session (tmux)
                          tmux "lani-bridge"
```

## Parts

| Path | What |
|---|---|
| `bridge/` | Claude Code channel server (Bun/TS). Forwards app messages into the session, exposes `reply`, `publish_module`, `publish_pack`, `publish_grammar`, `publish_worksheet`, `publish_scenario`, `publish_scene`, `publish_reading` and `publish_gloss` tools, relays permission prompts, serves the app API. |
| `lexicon/` | Word lookup's dictionaries (see [Word lookup](#word-lookup)): `sl.json`, built from Wiktionary, with its meanings in Italian and German, `sl.extra.json`, a hand-written supplement, and `sl.forms.json`, the forms book: hand-checked tables of the irregular verbs and the verb pairs (see [Word forms](#word-forms)). The larger ones (Italian, German, English) are downloads (`LANI_LEXICON_URL`). |
| `grammar/` | The grammar book's curated pages (`lani.grammar/v0`, one rule each, see [Grammar book](#grammar-book)): `grammar/<language>/`, 44 in `grammar/sl/`, 16 each in `grammar/it/`, `grammar/de/` and `grammar/en/`. |
| `cards/` | The cheat sheet's curated cards no grammar page fits (`lani.card/v0`, see [Cheat sheet](#cheat-sheet)): `cards/<language>/<id>.json`. |
| `course/` | The school's course (see [The school's course](#the-schools-course) and [COURSE.md](COURSE.md)): `course/<language>/course.json`, the outline of its levels and units (`lani.course/v0`), and `course/<language>/units/<id>.json`, a unit each (`lani.unit/v0`). |
| `drills/` | The car's audio drills (`lani.drill/v0`, see [The drills](#the-drills)): `drills/<language>/`, four in `drills/sl/` (transformations, rapid fire, sentence building, riddles). |
| `modules/`, `packs/`, `scenarios/`, `scenes/` | Curated grammar modules (`lani.module/v0`, with villager quests), word packs (`lani.pack/v0`: Slovene in `packs/` itself, another target language in `packs/<language>/`, e.g. `packs/it/`, `packs/de/`, `packs/en/`), role-play scenarios (`lani.scenario/v0`) and village scenes (`lani.scene/v0`, see [SCENES.md](SCENES.md)). |
| `cultures/` | Culture packs (`lani.culture/v0`, see [GAME.md](GAME.md#culture-packs)): the village's people, stories, feasts, goods and names, in its language. `primorska/` is the default village, with the cast in `primorska/villagers/` (`lani.villager/v0`, see [VILLAGERS.md](VILLAGERS.md)); `friuli/` is the second learner's Italian town in the Collio near Gorizia, with its cast, its festivals' word packs (`friuli/packs/`) and its voice cast (`friuli/voice-cast.json`, Italian voices); `kaernten/` is a German village in Carinthia (the Gailtal near Villach) for learners of German, the same parts in German with Slovene, English and Italian; `lakeland/` is the English village in a dale of the Lake District, for learners of English from Slovene, Italian or German (its starter packs in `packs/en/`), each with its voice cast. `villagers` is a link to `cultures/primorska/villagers`, the cast's old place, for a bridge started before the move; remove it once every bridge has restarted. |
| `voice-local/` | Local Slovene text-to-speech (Gepard) on the node's GPU, the fallback voice (see [Voice](#voice)). |
| `bin/lani-setup` | Sets up this machine step by step, or changes it: the learner, their data as a git repository of its own, a private remote, voices, the network, running, pairing ([docs/setup.md](../docs/setup.md)). `bin/lani <command>` runs `bin/lani-<command>` (`lani setup`, `lani pair`, …). |
| `bin/lani-session` | Starts or re-attaches the always-on session in tmux, with the channel and Remote Control. Loads `~/.config/lani/keys.env`. `--profile <id>` starts another learner's session, `--list` shows the learners (see [Learners](#learners)). `--service` starts the bridge service first and the session with the shim, `--classic` the bridge inside the session (see [The bridge as a service](#the-bridge-as-a-service)). |
| `bin/lani-bridge` | Runs each learner's bridge service in tmux: `start`, `restart` (deploys new bridge code without a tutor restart), `status`, `stop`, `logs` (see [The bridge as a service](#the-bridge-as-a-service)). `systemd/` has a user unit template for it, not installed. |
| `bin/lani-profile` | Adds a learner (their own data, bridge port, tmux session and tokens) and shows how to publish, start and pair them. |
| `bin/lani-pair` | Pairs a phone with a learner's tutor: prints a one-time QR code; lists and unpairs phones (see [Pair a phone](#pair-a-phone)). |
| `bin/lani-send-app` | Sends the app (the newest APK of the release directory) to a phone over Tailscale (Taildrop), no cable (see [Android app](#android-app)). |
| `bin/lani-town` | Links learners' towns: invitations (a one-time QR code), accepting one, linking two towns of this node, unlinking (see [Towns](#towns)). |
| `bin/lani-handover` | Moves a learner's data between this node and their phone through the data repository: hands it over (the lease, then the town's key as a QR code), takes it back (`--take`), says who writes (`--status`) (see [Data: git, the handover, the backup](#data-git-the-handover-the-backup)). |
| `bin/lani-export`, `bin/lani-import` | A learner's data as one file and back (`lani.export/v1`), to move them between this node and the app's hosted mode (see [Lani on this phone](#lani-on-this-phone-no-node)). |
| `bin/voice-build` | Counts and prebuilds the voice clips (see [Voice](#voice)). |
| `bin/lexicon-build` | Builds a word lookup dictionary from a kaikki.org extract of Wiktionary, gives its lemmas their meanings in the other languages (from the English, German and Italian Wiktionaries), and shows how much of the content it knows (see [Word lookup](#word-lookup)). |
| `bin/release-app` | Builds a release APK (`--docker`: in Docker) and publishes it to the app's self-update; `--github [<tag>]` publishes a GitHub release's APK instead (see [Android app](#android-app)). |
| `bin/lani-build-app` | Builds the app in Docker or Podman, without a JDK or an Android SDK: the debug or release APK, the unit tests, any Gradle task (see [Build in Docker](#build-in-docker)). |
| `bin/lani-keystore` | Creates the app's release key outside the repository (`~/.config/lani`) and shows how to give it to the release workflow (see [docs/releasing.md](../docs/releasing.md)). |
| `bin/qa` | End-to-end check on an emulator against a dev bridge on a copy of the data, and a second dev town to visit. |
| `bin/lani-backup` | Snapshots of the learner data, with restore (see [Backups](#backups)). |
| `.claude/skills/lani-studio/` | How Claude designs, publishes, fixes and retires app modules. |
| `.claude/skills/lani-fix/` | How Claude fixes a question, card, word or dialog choice the learner reports as wrong or confusing, with `.claude/hooks/card.py` for review cards. |
| `android/` | Jetpack Compose app: gamified home, flashcard reviews with sl-SI speech, module player, tutor chat, permission relay. |

## Run

`companion/bin/lani-setup` sets it all up ([docs/setup.md](../docs/setup.md)): the learner's data in
`~/.local/share/lani/<id>` (a git repository of its own, committed after each session), the settings in
`~/.config/lani/lani.env`, the voice clips in `~/.cache/lani/voice` and the APKs in `~/.local/share/lani/releases`.
Without it, the data stays in the checkout's `data/` and the caches in `data/app/`, as before. Then:

```bash
cd companion/bridge && bun install     # once (lani-setup does it)
companion/bin/lani-session             # start the tutor; detach with Ctrl-b d
```

On first start, Claude Code asks two things: confirm the development channel (channels are a research
preview), and approve the `lani` MCP server from `.mcp.json`.

The app token is generated at `<data>/app/bridge-token`. The API listens on `127.0.0.1:8790`
(override with `LANI_BRIDGE_HOST` / `LANI_BRIDGE_PORT`). `tailscale serve --bg 8790` publishes it
to your tailnet as `https://<node>.<tailnet>.ts.net`, which is the URL the app uses. Only the tutor
session's bridge serves the API (`lani-session` sets `LANI_TUTOR=1`): another Claude Code session
opened in the repo starts a bridge from `.mcp.json` too, but it keeps just the MCP tools and never takes
the port. To connect a phone,
run `companion/bin/lani-pair` and scan its QR code in the app (see [Pair a phone](#pair-a-phone)), or
type the URL and the token.

Coming from a Fluent install (the project's name until October 2026)? Its `FLUENT_*` variables are still read when
the `LANI_*` ones aren't set, and its `~/.config/fluent`, `~/.local/state/fluent`, `~/.cache/fluent` and
`~/.local/share/fluent` directories while the `lani` ones don't exist: see
[docs/migrate-from-fluent.md](../docs/migrate-from-fluent.md).

## The bridge as a service

In the classic mode above, Claude Code spawns the bridge inside the tutor session. So new bridge code takes effect
only when the tutor restarts, and the tutor's context is lost each time. In the **service mode**, the bridge runs on
its own and can be restarted in about a second. The tutor session keeps running. The plan and its reasons are in
[plan 4](../docs/plans/04-bridge-service.md).

- **The service** (`bridge/src/service.ts`) is the whole bridge: every feature, the app's API on the learner's port,
  the background jobs. It runs in its own tmux session, `lani-bridge` (`lani-bridge-<id>` for another learner),
  under a small loop that starts it again when it exits.
- **The shim** (`bridge/src/shim.ts`) is what Claude Code spawns from `.mcp.json` when `LANI_BRIDGE_SHIM=1`: it
  forwards the tools, the channel's events and the permission relay to the service over a unix socket
  (`~/.local/state/lani/run/bridge-<hash>.sock`). When the service restarts, the shim reconnects and tells Claude
  Code about new tools (`list_changed`). A tool call during a restart waits up to 10 s, then fails with a clear message.
  The shim itself never exits until the session ends.
- **While no tutor session is attached**, the service keeps the app's messages for it (at most 200, for 3 days) in
  `<data>/app/channel-queue.json`, and delivers them, marked `queued_at`, when a session attaches. The app's event
  buffer is kept across restarts in `<data>/app/events.json`.
- **One service per learner**: its own tmux session, socket, port, log and data.

```bash
companion/bin/lani-session --service             # the service (if not running), then the tutor with the shim
companion/bin/lani-bridge restart                # deploy new bridge code: about a second of downtime for the app
companion/bin/lani-bridge restart --all          # every learner's running service
companion/bin/lani-bridge status [--all]         # running since, the commit, the tutor attached, events waiting
companion/bin/lani-bridge logs -f                # ~/.local/state/lani/bridge.log (bridge-<id>.log)
companion/bin/lani-bridge stop
```

`restart` first checks that the code builds (`bun build`); if it doesn't, the running service is left alone. Add
`--profile <id>` for another learner. `lani-session` picks the mode from `--service` or `--classic`, else
`$LANI_BRIDGE_MODE`, else the word in `~/.config/lani/bridge-mode`, else `classic`.

**What still needs a tutor restart:** a change to the shim (`bridge/src/shim.ts`, `bridge/src/control.ts`,
`bridge/src/index.ts`) or to the MCP SDK it loads; the channel's short instructions (the `channel_guide` tool is
live); `.mcp.json` and Claude Code's own flags.

**tmux or systemd.** The loop in tmux gives the service the tutor's lifetime: it survives a logout and doesn't start
at boot. A systemd user unit (`systemd/lani-bridge@.service`, a template) would stop at logout unless linger is on
(`loginctl enable-linger`), which is Jan's decision (plan 4, "How it runs").

### Switching to the service mode (once)

1. On `main`: `cd companion/bridge && bun install && bun test/smoke.ts`.
2. **Rehearse** on a throwaway learner (a temporary registry, port 9120, no API keys; nothing of Jan's is touched):
   ```bash
   export LANI_PROFILES_DIR=$(mktemp -d) LANI_KEYS_FILE=/dev/null
   companion/bin/lani-profile add rehearsal --name Test --target sl --base en --port 9120 --https-port 19443
   companion/bin/lani-session --profile rehearsal --service        # confirm the development channel; Ctrl-b d
   companion/bin/lani-bridge status --profile rehearsal            # up on 9120, the tutor attached
   T=$(cat $LANI_PROFILES_DIR/rehearsal/data/app/bridge-token)
   curl -N -H "Authorization: Bearer $T" 127.0.0.1:9120/events &
   curl -H "Authorization: Bearer $T" -d '{"text":"Živjo! Say hi."}' 127.0.0.1:9120/message   # the reply comes
   companion/bin/lani-bridge restart --profile rehearsal           # "the tutor reconnected"; chat again
   companion/bin/lani-bridge stop --profile rehearsal; tmux kill-session -t lani-rehearsal
   unset LANI_PROFILES_DIR LANI_KEYS_FILE                          # else the real service would start without keys
   ```
3. At a quiet moment (no role-play running): `echo service > ~/.config/lani/bridge-mode`.
4. Quit the tutor: `tmux attach -t lani`, then `/exit`. Its bridge exits and frees 8790.
5. `companion/bin/lani-session`: it starts the service in tmux `lani-bridge`, waits for it on 8790, then starts
   the tutor with the shim. Confirm the development channel as usual.
6. `companion/bin/lani-bridge status` shows the service on 8790 with the tutor attached. Send a chat from the
   phone. `tailscale serve` needs nothing: it still proxies to 8790.
7. `companion/bin/lani-bridge restart`: the phone is back within a second or two. Chat again.
8. Each other learner: quit their tutor (`tmux attach -t lani-<id>`, `/exit`), then
   `companion/bin/lani-session --profile <id>`.

**Rollback:** `echo classic > ~/.config/lani/bridge-mode`, quit the tutor (`/exit`), `companion/bin/lani-bridge
stop` (it frees 8790 and says how many events still wait for the tutor: roll back when it's 0), then
`companion/bin/lani-session`. The data stays readable both ways: the service only adds files the classic bridge
ignores.

## Learners

One machine can hold several learners, each with a tutor of their own: Jan, and a second learner, who learns
Italian from Slovene in a town of their own. A learner's **profile** has an id and its own:

| | The default profile (Jan) | Another profile, e.g. `luka` |
|---|---|---|
| Data directory | `LANI_DATA_DIR` from `lani.env` (lani-setup: `~/.local/share/lani/<id>`, results in its `results/`), else `data/` | `profiles/luka/data/` (results in `profiles/luka/results/`) |
| Bridge | `127.0.0.1:8790` | a free port from 8792 up (never 8790, 8791 and 8797 for QA, 8795/8796 for the voice and speech workers, 8799 for the smoke tests of an older checkout; today's smoke runs on 18800 and up) |
| Tailnet URL | `https://<node>.<tailnet>.ts.net` (443) | `https://<node>.<tailnet>.ts.net:8443` (then 9443, 10443, …) |
| tmux session | `lani` | `lani-luka` |
| Remote Control name | `lani-tutor` | `lani-tutor-luka` |
| Tokens, paired phones | `data/app/bridge-token`, `devices.json`, … | the same files in its own `app/` |

The default profile is today's setup and is not in any file. The others are in `profiles/profiles.json`
(git-ignored with their data; `LANI_PROFILES_DIR` moves it), each with the `culture` its village is in
(a culture pack, `companion/cultures/<id>`; `primorska` when not set, and for the default profile). The
learner's name, level, target and base language are in their `learner-profile.json` (`target_language`,
`base_language` and their `_code` fields), which the tutor's `/lani-setup` can refine. The content speaks to the
learner by that name and gender (`learner.name`, `learner.gender`: `male` or `female`, male when missing;
`learner.name_forms` for a name whose cases the rules miss): it is written with `{learner}` and `{m:…|f:…}`, and the
app and the bridge say it to the learner of the profile ([SCENES.md](SCENES.md#the-learner-in-the-content)).

**Add a learner:**

```bash
companion/bin/lani-profile add luka --name Luka --target it --base sl --child --culture friuli --landscape hills
```

It makes the six learner databases from `data-examples/` (level A1 unless `--level`), picks the ports,
registers the profile with its village's culture pack and prints the next steps. `--target` and `--base` are two
different app languages (`sl`, `en`, `it`, `de`, or their English names). The village is the culture pack of the
target language (`primorska` for Slovene, `friuli` for Italian, `kaernten` for German); `--culture` picks another pack (the village then
speaks that pack's language, and the CLI says so). Without a pack in the target language, the CLI stops and says
what to do; a pack that isn't there is refused, and a stub plays `primorska` until its content is written.
`--landscape` (`valley`, `hills`, `lake`, `mountains`, or `coast` for a region by the sea: Friuli) is the land the
learner's village is offered first when it's founded (see "The village's place" below); without it the region's usual
one comes first (Friuli's hills, the lakes of Kärnten and the Lake District, Primorska's valley). The coast for a region
without the sea, or a landscape the app hasn't, is refused.

1. Publish the bridge to the tailnet, once. The command and the resulting URL are printed, e.g.
   `tailscale serve --bg --https=8443 http://127.0.0.1:8792` → `https://<node>.<tailnet>.ts.net:8443`.
   Each learner gets their own HTTPS port rather than a path, because the app and the family page use
   absolute paths.
2. Start the tutor: `companion/bin/lani-session --profile luka`. On the first start, confirm the
   development channel and approve the `lani` MCP server, as for the default session.
3. Pair the learner's phone: `companion/bin/lani-pair --profile luka`.

`companion/bin/lani-session --list` (or `lani-profile list`) shows every learner, their village's
culture, their port, tmux session and whether their bridge and session run; `lani-profile show <id>`
prints the steps again (and the landscape, when given).

**The village's place.** The app founds the village at its first start, once the node has none (and the phone none
either): "🏡 Kje bo tvoja vas? · Where will your village be?" shows the region (the culture packs of the village's
language; the node plays one, `--culture`: another is chosen there), the landscapes the region offers (valley, hills,
lake, mountains, the coast where it has the sea; the node's `--landscape` or the region's usual one first), and the
village drawn there at its first age, the whole land round it. "🎲 Nov kraj · Another place" rolls another seed (another
stream, road, clearing, plots and woods; the same seed always the same land), and "✓ Tu bo moja vas · My village will be
here" founds it there for good (GAME.md, "The land"). A village the node already has is never asked about: Jan's stands
on the classic valley, as it always did.

**How a session finds its learner.** `lani-session --profile luka` exports `LANI_PROFILE`,
`LANI_DATA_DIR`, `LANI_BRIDGE_PORT`, `LANI_CULTURE` (and `LANI_CHILD`, `LANI_LANDSCAPE`) in the tmux session. Claude Code passes its
environment to the bridge it starts from `.mcp.json`, and the helper scripts (`read-db.py`, `update-db.py`)
follow `LANI_DATA_DIR`, so the session, its bridge and its tools all work on that learner's data. The
bridge's channel instructions name the learner instead of Jan, say where their data is, and tell the
tutor to leave the repository's `data/` and `results/` alone. The bridge serves the cast of the learner's
culture pack and tells the app which pack it is (`GET /culture`, with the `landscape` a new village is offered first
when the profile has one); the app bundles every pack, keeps the id and plays it, offline too. Without `--profile`, `lani-session` runs
exactly the command it ran before profiles existed. In the service mode, `lani-bridge` runs the learner's bridge with the
same environment in tmux `lani-bridge-<id>`, and the session gets `LANI_BRIDGE_SHIM=1` and the service's socket instead
of the port (see [The bridge as a service](#the-bridge-as-a-service)).

**A village in another language** (the second learner's: `--target it --base sl --culture friuli`; a learner of English:
`--target en --base de --culture lakeland`). The bridge follows the culture pack's language:

- It serves the word packs of that language (`companion/packs/it/`) and the pack's own
  (`companion/cultures/friuli/packs/`, its festivals' words), not Jan's Slovene ones. A learned word becomes an
  Italian card with its meaning in the learner's base language.
- Jan's curated Slovene modules, role-plays and scenes aren't served either: another language's go in
  `companion/modules/it/`, `companion/scenarios/it/` and `companion/scenes/it/` (none yet; the tutor writes them for
  the learner with `publish_module`, `publish_scenario`, `publish_scene`).
- The villager talks open with the villager's Italian line, translated into the learner's base. The setting,
  the style newcomers are written in and a designed voice's accent come from the pack (`culture.json` `tutor`).
- A learner of German plays the same way in `kaernten` (`--target de --culture kaernten`), with the German packs of
  `companion/packs/de/` and the pack's own (`companion/cultures/kaernten/packs/`).
- The tutor's instructions say where the village is and in which language its people speak; a child's profile
  keeps the child paragraph.
- The pack's voice cast (`voice-cast.json` in the pack) replaces `companion/voice-cast.json`: ElevenLabs voices for
  the pack's language, asked for in it (`language_code: it`). A clip not made yet is the phone's text-to-speech in the
  target language (Italian is on every phone). See "Voice".

**Language pairs.** A learner learns a *target* language and reads explanations in a *base* language: any two of
Slovene, English, Italian and German, so 12 pairs (Jan: Slovene ← English; the second learner: Italian ← Slovene). What the
pair switches:

- **Labels**: the app's string tables (`android/app/src/main/resources/l10n/<code>.json`, the same keys in each),
  shown as "target · base" ("Danes · Heute"). The German and Italian tables are machine-written and not reviewed yet
  (their `_status`). A text that names the language learned ("Write it in German") selects on the target; names from
  the culture pack (ages, buildings, skills) and dates read in each half's own language. The connection sheet (🌐
  Jeziki · Languages) switches the pair on the phone, for testing.
- **Grading** (`resources/grading/<code>.json`): a missing č/š/ž, Italian accent or German umlaut (also ae/oe/ue/ss
  for ä/ö/ü/ß) is almost right, with its hint; English contractions are the same answer. Answers are lowercased in the
  target's locale; a word's stem forgives one slip from 4 letters, two from 9.
- **Speech**: the phone's recognizer and text-to-speech take the target's locale (sl-SI, it-IT, de-DE, en-GB); the
  node's Whisper has Slovene's model and serves Italian, German and English with the general one
  ([stt-local](stt-local/README.md#languages)). ElevenLabs gets the village's `language_code`, and a narrator says a
  lone word in its language's carrier sentence ("Beseda je: …", "La parola è: …", "Das Wort ist: …", "The word is:
  …"); Gepard, the local voice, speaks Slovene only.
- **Grammar**: plurals by each language's ICU rules (the Slovene dual); ti/vi, tu/Lei, du/Sie in the villagers'
  built-in lines, hints and role-plays (English has one "you"); the keyboard chips offer the target's letters.
- **The tutor**: the channel instructions name the pair; Jan's name the base when it isn't English.
- **The curated content**: Slovene's word packs, cast, culture pack (primorska) and village scenes explain in English,
  German and Italian; Italian's (friuli, `packs/it`) in Slovene, English and German. The app shows the base's text
  under what is said, English where a text has none. The German and Italian translations are machine-written and not
  reviewed yet (each file's `review`).

Not every pair has everything yet: see [plan 2, §1](../docs/plans/02-worlds-languages-multiplayer.md#1-language-pairs)
for what's left per pair (an English culture pack, reviews by native speakers, voices, meanings in the base).

**Shared between learners:** the Claude account (two always-on sessions share its usage limits), the
keys in `~/.config/lani/keys.env`, this machine's settings in `~/.config/lani/lani.env` (not the default learner's:
their data, voice cache, village, port and remote are theirs alone), the curated content, and the app's releases (every
bridge serves `LANI_RELEASE_DIR`, else the default profile's `data/app/release/`, so every phone updates itself). A new profile starts with
`LANI_VOICE_DESIGN=off` (in its `env` in `profiles.json`): voices of one's own would take ElevenLabs voice
slots from the default profile's village.

**A child's profile** (`--child`):

- The tutor's instructions get a paragraph: the learner is a child, so it stays age-appropriate,
  encouraging and simple, never asks for personal data, and sends no links.
- Tool approvals are not relayed to the child's phone (the bridge doesn't offer the channel's permission
  relay, and `POST /permission` answers `403`). A parent answers them in the terminal or through Remote
  Control.

## Pair a phone

```bash
companion/bin/lani-pair                       # the default learner
companion/bin/lani-pair --profile luka        # another learner
```

It prints a QR code and its text:

```
lani://pair?v=1&u=<bridge URL>&c=<code>&p=<profile>&f=<bridge key fingerprint>
```

(The app also reads `fluent://pair?…`, as bridges printed it before the project was renamed.)

In the app, tap "📷 Skeniraj QR · Scan the QR code" on the first screen (the URL and token fields stay
for typing), or, to pair again, the tutor's status dot on Home. The app asks for the camera only then,
reads the code with CameraX and ZXing (no Google Play services), calls `POST /pair` and shows which
learner it paired with. The phone must be on the tailnet: the Tailscale app, signed in to your tailnet,
or with this node shared with it.

- **The code** works once and for 10 minutes. `lani-pair` writes it as a file under
  `<data>/app/pairing/`, named by the code's SHA-256, so making one needs this machine's files and the
  code itself is never stored. `--url` sets the bridge URL when `tailscale status` can't tell it.
- **Wrong codes:** after 10 wrong or expired codes in 10 minutes, `POST /pair` answers `429` until the
  oldest leaves the window (the bridge is behind `tailscale serve`, so the limit is for the bridge, not
  per address).
- **The bridge's key.** Each bridge has an ECDSA P-256 key (`<data>/app/bridge-key.pem`, made on first
  use). The QR code carries its fingerprint (the first 16 bytes of the SHA-256 of the public key), and
  the bridge signs its answer (`lani-pair/1`, the profile, the code and the new token; the app also verifies
  `fluent-pair/1`, what a bridge from before the rename signs). The app pairs
  only when the key matches the fingerprint and the signature verifies, so it knows it paired with the
  learner the QR code names.
- **Device tokens** are long-lived, one per phone, and act like the app token. The bridge keeps only
  their SHA-256 in `<data>/app/devices.json`, with the phone's name, when it paired and (in
  `devices-seen.json`) when it was last seen. The typed `bridge-token` keeps working, so a phone set up
  by hand needs nothing new.
- **Unpair a phone:** `lani-pair --devices` lists them; `lani-pair --revoke "Pixel 8"` (a name or an
  id) removes one, and its token stops working at the next request. The app then shows "🔒 Ni več
  seznanjen · Not paired any more" and offers to scan a new code.
- **One phone, one learner.** A phone keeps its learner's village, chat and queued answers, so it
  pairs again only with the same learner (the same bridge key and profile, or, for a typed token, the
  same address). For another learner, clear the app's data first.

## Towns

Each learner's village is a **town** that can know other towns: Jan's and the second learner's, for a start (plan 2, §3–§5).
Towns are linked only by invitation. There is no directory, and a town sees only the public state of the towns it is
linked with. A linked town can be visited ([Visiting](#visiting)), in its language: its people talked with, its
requests done, a gift or a message left, its market traded at ([Things to do on a visit](#things-to-do-on-a-visit)),
its learner asked a question, which they answer in that language ([Questions between towns](#questions-between-towns)).
Linked towns grow a friendship that both agree on ([Friendship between towns](#friendship-between-towns)).

On one node (the family node), every learner's bridge is a town of its own, on its own port. The towns talk to each
other over HTTP as if they were on different machines, so the same protocol works between nodes later.

**A town's id** is its bridge key's fingerprint (see [Pair a phone](#pair-a-phone)). A town shows itself as its
village's name (the one set with `lani-town name`, else the culture pack's, e.g. "Moja vas"), the learner's
first name, the culture pack and its language.

**Links** are in `<data>/app/towns.json`: for each linked town, its id, its public key, the URL its bridge
answers on (on one node `http://127.0.0.1:<port>`; `LANI_TOWN_URL` sets a bridge's own), when it was linked,
by whom (`app` or `cli`) and how (`invite`: it accepted this town's invitation; `accept`: this town accepted its
invitation; `link`: the node linked two of its towns), and its address on the relay when it has one (`relay`, see
[Through the relay](#through-the-relay)). The file holds no secrets.

### Link two towns

- **In the app:** the village's scroll, its "👥 Prebivalci · Villagers" → "🤝 Prijatelji · Friends".
  "✉️ Povabi · Invite" shows an invitation as a QR code, its code and a share button. On the other phone,
  "🔗 Sprejmi · Accept" scans the QR code, or takes the invitation's text pasted.
- **On the node:**

  ```bash
  companion/bin/lani-town list [--profile luka]            # this town and its links
  companion/bin/lani-town invite [--profile luka]          # a one-time invitation, as a QR code
  companion/bin/lani-town accept '<invitation>' [--profile mia]
  companion/bin/lani-town link default luka                # two towns of this node, directly
  companion/bin/lani-town unlink luka [--profile mia]      # a profile, a town id or a name; both ways
  companion/bin/lani-town name "Vas pod Sabotinom"         # the village's name other towns see (--clear)
  ```

  `invite`, `accept` and `name` take `--profile` like `lani-pair`; `invite` and `accept` also take `--url`, where
  this town's bridge answers other towns.

**The invitation** is `lani://town?v=1&u=<the inviting bridge's URL>&c=<code>&f=<its fingerprint>` (`fluent://town?…`
from before the rename is read too), with `&r=<its relay address>` when the town is on a relay. The code
works once, for 30 minutes. The bridge keeps only its SHA-256 (in `<data>/app/town-invites/`), as it does for
pairing codes. After 10 wrong or expired codes in 10 minutes, the bridge answers `429` until the oldest leaves
the window. To accept, the other bridge:

1. asks `GET /town/hello` for the inviting town's id and public key, and stops unless the key's fingerprint is the
   invitation's `f`;
2. sends `POST /town/join` with the code and its own id, public key, URL and display, signed with its own key;
3. checks the signed answer (the inviting town's id, key and display), and links it.

The inviting bridge links the other town when the code is right. So both towns know each other's key.

**A child's town** (a profile made with `--child`): the child's app can't invite or accept (`403`), and the
Friends screen hides those buttons. A parent links the child's town on the node with `lani-town` (`link`, or
`invite`/`accept` with `--profile`).

**Signed requests.** Every request between two bridges carries `x-lani-town-from` (the sender's id),
`x-lani-town-to` (the recipient's id), `x-lani-town-time` (milliseconds), `x-lani-town-nonce` and
`x-lani-town-signature`: an ECDSA P-256 signature (SHA-256, DER, base64) by the sender's bridge key of

```
lani-town/1
<METHOD>
<path and query>
<SHA-256 of the body, hex>
<time>
<nonce>
<sender id>
<recipient id>
```

The recipient checks it with the key of its link to the sender. It answers `401` (with a `code`) when the request
is unsigned (`unsigned`), for another town (`misaddressed`), more than 5 minutes off its clock (`stale`), from a
town it isn't linked with (`unlinked`), badly signed (`bad_signature`), or a nonce it has seen (`replayed`). A
successful answer is signed too (`x-lani-town-signature` over `lani-town-response/1`, the request's nonce and
the SHA-256 of the body), and the asking bridge checks it with the linked town's key.

Before the project was renamed from Fluent, these were `x-fluent-town-*`, `fluent-town/1` and
`fluent-town-response/1`. A bridge reads and checks those too, and signs every request and answer under both names
(the `x-fluent-town-*` headers alongside), so a town that still runs the old code and this one understand each
other.

### Through the relay

Towns that can't reach each other (a town in the app with no node, a node in another tailnet) talk through the relay,
[companion/relay](relay) (plan 5, phase D; [docs/plans/05-built/relay.md](../docs/plans/05-built/relay.md)). It is off
unless `LANI_RELAY_URL` names one (in `lani.env`, for the whole machine):

- The town's bridge publishes its public state there for the towns it is linked with, polls its mailbox there (outgoing
  HTTPS only) and answers what comes in through its own routes: other towns' signed requests (`/town/…`) and the family
  page's. Never the app's routes.
- Its address there is `<LANI_RELAY_URL>/v1/towns/<its id>`. Its invitations carry it (`r=`), and `GET /towns` shows it
  (`self.relay`); `lani-town list` too.
- A linked town is asked at its URL first, then at its relay address when the URL doesn't answer (and there first for 10
  minutes after that). Neighbours on one tailnet keep the direct way. A town that accepted an invitation through the
  relay, or joined through it, is reached through it.
- Requests through the relay are signed as before inside (the towns check each other's keys, so the relay can't make up
  or change a request or an answer) and with the town's key for the relay outside.
- A town that is away shows the state it last published on the relay, checked with its key.
- `LANI_TOWN_URL=relay` (a learner's setting): a town with no address of its own, only its relay address (the phone's
  bridge). Its family page goes through the relay too ([Family](#without-a-node)).

### What a town shows

`GET /town/public` (signed, linked towns only) is the town's public state, built field by field from the
village's state (`game.json`):

| Field | What |
|---|---|
| `town` | `id`, `name`, `learner`, `culture`, `language` |
| `date` | the town's today (the node's local date) |
| `age`, `founded_on`, `villagers` | the village's age (`OGENJ` … `MESTO`, `null` without a village), when it was founded, how many live there |
| `buildings` | `type`, `plot`, `level`, `damaged` of each building (`plot`: where it stands, the plot the learner chose or moved it to; every app draws it there) |
| `projects` | the village projects' steps done (their landmarks are on the map) |
| `residents` | `id`, `name`, `stage` (`baby`, `child`, `youth`, `adult`); for people born there or who moved in, also `emoji` and `art` |
| `visitor` | today's visitor (`id`, `name`), or `null` |
| `sky` | the sky a scene left today (`scene`, `rain`, `snow`, `fog`, `wind`, `gloom`, `lightning`), or `null` |
| `festival` | the culture's festival that is on (`id`, `emoji`, `name`, `day`, `late` days after it, `done`), or `null` |
| `land` | the land the village stands on (GAME.md, "The land"): `kind` (its landscape: `classic`, `valley`, …) and `map` (the number its map is generated from, the land's own, not the village's seed), or `null` (a village from before generated land, drawn as the classic valley; an older bridge doesn't send it) |

Nothing else leaves the town: not the resources, chest, friendships, chronicle or statistics, and nothing of the
learner's chat, mistakes, progress, words, tokens, devices or family. A visit gets two things more, each for what it
is for: the open requests of the town's people (without the learner's cards behind them) and the goods its market can
spare ([Things to do on a visit](#things-to-do-on-a-visit)). The texts one town gets from another (names) are cleaned
(one line, no `<`/`>`, at most 60 characters) and are shown as plain text only.

### The app's routes

The app talks only to its own bridge, which asks the other towns:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/towns` | `{self: {id, name, learner, culture, language, child, can_invite}, towns: [{id, name, learner, culture, language, linked_at, by, via, status, age?, villagers?, buildings?, date?}]}`: every linked town is asked for its public state now (3 s). `status` is `ok`, `unreachable`, `refused` (it no longer knows this town) or `bad_answer` |
| `GET` | `/towns/:id/public` | one linked town's public state, checked and cleaned, with `fetched_at`; when the town doesn't answer, the last state had (up to a day old) with `stale: true`; `404` for a town that isn't linked, `502` with a `code` when it can't be had |
| `GET` | `/towns/:id/villagers` | a visit: the town's people, `{town, culture, language, villagers, fetched_at, stale?}` (see [Visiting](#visiting)); `502` with a `code`: `unreachable`, `refused`, `unsupported` (its bridge is older than visits), `bad_answer` |
| `GET` | `/towns/:id/scenes` | a visit: the town's places, `{town, culture, language, scenes, fetched_at, stale?}` (their happenings for guests too); the same answers |
| `GET` | `/towns/:id/requests` | a visit: the town's open requests, `{town, culture, language, requests: [{id, giver, emoji, skill, title, story, words: [{id, word, meaning, emoji?}]}], fetched_at, stale?}` (see [Things to do on a visit](#things-to-do-on-a-visit)); the same answers |
| `GET` | `/towns/:id/market` | a visit: `{town, culture, language, wares: {<good>: n}, seller?: {emoji, name}, fetched_at, stale?}`; the same answers |
| `POST` | `/towns/:id/help` | `{id, request}`: a request of the town's done → `{ok, key, kind, amount, giver, thanks?: {culture, good}, duplicate?}` |
| `POST` | `/towns/:id/gift` | `{id, good?, message?}` (a good of the learner's chest, a message of at most 200 characters) → `{ok, key, kind, duplicate?}` |
| `POST` | `/towns/:id/trade` | `{id, give: {goods, res}, get: {culture, goods}, score}`: a trade agreed in a haggle → `{ok, key, kind, duplicate?}` |
| `POST` | `/towns/:id/talk` | `{villager}` or `{market: {give, get}}` → a role-play (`lani.scenario/v0`, id `town:<town>:<villager>` or `market:<town>:<n>`, `source: "visit"`) |
| `GET` | `/towns/guests` | what guests brought this town, newest first: `{entries: [{key, id, kind, from: {id, name, learner, culture, language}, at, help?, gift?, trade?}]}` |
| `GET` | `/towns/questions` | questions between towns ([Questions between towns](#questions-between-towns)), newest first: `{asked: [{key, id, town, at, language, text, conversation, feedback?, answer?}], received: [{key, id, from, at, language, text, answer?: {text, at, sent, conversation, feedback?}}], answered: {<town id>: n}}` |
| `POST` | `/towns/:id/questions` | `{id, text, checked?}`: a question to the town, in its language → `{ok, key, conversation, duplicate?}` |
| `POST` | `/towns/questions/answer` | `{key, text}`: the answer to a guest's question, in this town's language → `{ok, key, sent, conversation, code?}` (`sent: false` with a `code`: kept, sent again later) |
| `POST` | `/towns/questions/check` | `{text, town}` (a question) or `{text, key}` (an answer): the learner's tutor checks it first → `{conversation_id}` (`tq_…`; the reply comes on it) |
| `POST` | `/towns/invite` | → `{code, link, url, fingerprint, expires_at}`; `403` (`child`) for a child |
| `POST` | `/towns/accept` | `{invite}` (the `lani://town?…` text), or `{code, url, fingerprint}` → `{town: {id, name, learner, culture, language}}`; `400` not an invitation, `403` wrong or spent code (or a child), `409` the town isn't the one the invitation names, `410` expired, `429` too many wrong codes, `502` the town doesn't answer |

Between bridges (no token; see above): `GET /town/hello` → `{v, id, public_key}`, `POST /town/join`,
`GET /town/public`, `GET /town/villagers`, `GET /town/scenes`, `GET /town/requests` and `GET /town/market` (a visit),
`POST /town/help`, `POST /town/gifts` and `POST /town/trade` (a visit's writes), `POST /town/questions` and
`POST /town/answers` (a guest's question and the host's answer), and `POST /town/unlink`, with which a town lets go of
the asking one (`lani-town unlink` sends it to a town on another node). A write's refusals are `400` (`bad_request`),
`409` with a `code` (`not_open`, `no_deal`, `unfair`, `not_for_sale`, `too_many_open`, `not_for_a_child`, `answered`,
`unknown_question`) and `429` (`rate_limited`); the visitor's bridge passes them on to its app as they are.

### Visiting

In "🤝 Prijatelji · Friends", "🚶 Obišči · Visit" on a linked town opens its village, read-only, also while the town
isn't answering (then its last state shows). The app still talks only to its own bridge, which asks the other town:

- **What a town gives a linked town for a visit** (signed, linked towns only, `401` otherwise): `GET /town/villagers`,
  the cast of its culture pack as its own app gets them (looks, voices, roles, homes, lines, personality), and
  `GET /town/scenes`, its curated scenes (its language's and its culture pack's, like Friuli's sea at the horizon),
  resolved with the curated word packs. Each answer says the town's id, culture pack and language. Never what its
  tutor made: the tutor's villagers (a newcomer given lines, a changed cast member), scenes and packs stay home, like
  the chat, mistakes, words and everything else private.
- **What the visitor's bridge passes on** (`/towns/:id/public`, `/villagers`, `/scenes`): every villager and scene
  checked against the formats again and its texts cleaned (no control characters, no `<`/`>`), one that isn't valid
  left out. People and places are kept for a minute. When a town doesn't answer, the last good answer (up to a day old)
  comes with `stale: true`; what was never had is a `502` with `code: unreachable`.
- **The app** draws the town from its public state: a read-only village state (buildings on their plots, the age,
  the projects' landmarks, who lives there, today's visitor, the sky, the festival celebrated) that is never saved or
  sent anywhere, in the host culture's look and with its people walking about. A banner says "🧳 Na obisku ·
  Visiting: <village> (<learner>)", with "⌂ Domov · Home"; a town that isn't answering says so, with a retry, or shows
  its last state with when it was had.
- **The town's language.** During a visit every label, villager line and scene word is in the host town's language,
  explained in the visitor's base: Jan (Slovene from English) visiting an Italian town reads "Italian · English"; the
  second learner (Italian from Slovene) visiting Jan's town reads "Slovene · Italian". The phone's voice speaks the town's
  language (the node's voices speak the learner's own). The own pair and culture come back on leaving.
- **A guest.** Nothing of the host changes but through the things to do on a visit (below), and the visitor's own
  village waits (no days pass on the phone during a visit). A place's card shows its name and the ways into its scenes,
  and "🧳 Tukaj si gost · You're a guest here" instead of building, gathering, feeding the fire, the chest or projects.
  A villager's card shows who they are, their greeting to a stranger (a word tapped opens the word card; not known to
  the visitor's dictionary, "ask the tutor") and "💬 Pogovori se · Talk". A scene can be walked into, its things tapped
  for their words, and its people talked with: its dialogs, the ones meant for guests among them. A word of the visit
  added to the learner's words goes to their words in the town's language (`POST /words` with its `language`).

### Things to do on a visit

The bar under a visited town opens them: "📜 Prošnje · Requests", "🎁 Darilo · Gift", "⚖️ Tržnica · Market"; a villager's
card and a scene's people open a talk. Everything is in the town's language, explained in the visitor's base, and
everything the visitor learns there goes to their profile for that language (docs/DB_SCRIPTS.md, "Languages"), never
their home language's.

- **Talking with the town's people.** "💬 Pogovori se · Talk" on a villager's card (the town's cast): `POST
  /towns/:id/talk {villager}` has the visitor's bridge build a role-play from the host's villager as the host's bridge
  gave them (`GET /town/villagers`): a stranger's greeting, the host's culture's setting ("Jan is visiting from Moja
  vas"), the town's language, the visitor's level in it (`levelIn`: the home language's level, another language's own
  profile, else A1). The visitor's own tutor plays them; every turn carries `data.villager` and `data.visit` (the town,
  its language, the level) and `data.language`. The friendship stays the host's: the visitor's village keeps "Spoznal
  si · You met Nonna Rosa (Il mio villaggio)" (`GameState.towns`), and the talk pays wisdom as any talk.
- **A request of the town's people.** `GET /town/requests`: the open requests of the host's people (their quests in its
  village state: not done, not expired, not the tutor's, not the move of the tent), with their title and story in every
  language the culture pack has them (`quests.json`, matched by giver and title) and six words of the host's curated
  packs (the giver's, else the request's topics', else any). Never the learner's cards or categories behind them. The
  visitor plays a run of those words (two thirds right to pass); a pass sends `POST /town/help {id, request}`: the host's
  guest book gets +3 🤝, and the answer names the giver's thank-you good (`chest.json` `thanks`), which comes into the
  visitor's chest. The run goes to the visitor's tutor as a `session_end` with `data.language`. A town does a request once.
- **A gift and a message.** A good of the visitor's chest and/or a message of at most 200 characters (one line, plain
  text; the app asks for it in the town's language): `POST /town/gifts`. The host's app puts the good into its chest and
  shows the message as it was written ("Darilo od Jan: … · A gift from Jan: …", and under "📬 Od gostov · From your
  guests" in Friends, where a child's parent sees it too). The host's tutor gets it as a `town_gift` event with the text
  under `untrusted_town_content` (escaped like the family's), never as instructions; it doesn't reply.
- **The market.** `GET /town/market`: the goods the host's chest can spare (of each good it has more than one of, all
  but one, at most 3, less what trades it hasn't applied yet promised) and its seller. The visitor picks what they want
  (up to 3 kinds) and what they offer of their own (goods and resources, worth at least half), and "🗣️ Barantaj ·
  Haggle": `POST /towns/:id/talk {market: {give, get}}` makes a role-play with the seller in the town's language. The
  tutor's debrief says `{"deal": true|false, "score": 0-10}`; a deal with 6 or more sends `POST /town/trade {id, give,
  get, score}` (the role-play's conversation id is the trade's id). The host checks it under its guest book's lock (the
  goods are still there to spare, the score, the value) and keeps it; what the visitor gave comes into the host's
  village, what they got leaves it, and the other way round in the visitor's.
- **The guest book.** Every write is kept once by the sending town and its id (`<town id>:<id>`) in the host's
  `<data>/app/town-guests.json`; sent again (a retry), it is the same entry, answered `duplicate: true`. The visitor's
  bridge logs its writes and their answers (`<data>/app/town-visits.json`), so its app's retry is answered the same
  without asking again. The host's app reads `GET /towns/guests` on every sync (and at once on a `town_guest` event)
  and applies each entry once: its key goes into `GameState.guestbook`, which the host's bridge reads too (a trade not
  applied yet still holds its goods). The visitor's app applies its side once likewise, by the answer's key.
- **Goods between cultures.** Each culture has its own goods (Primorska's `med`, Friuli's `miele`). A good that comes
  from another culture's town becomes the village's own that looks the same (🍯 is 🍯), else one of the same price and
  kind, else its thank-you default.
- **Limits.** A linked town may write at most 20 helps, 10 gifts and 10 trades a day and 30 writes an hour
  (`LANI_TOWN_HELP_PER_DAY`, `LANI_TOWN_GIFTS_PER_DAY`, `LANI_TOWN_TRADES_PER_DAY`, `LANI_TOWN_WRITES_PER_HOUR`);
  beyond, `429`. An unlinked town, or a request without a town's signature, gets `401` from every one of these routes.
- **Dialogs for guests.** A scene's happening (or its dialog) can say `"guests": true` ([SCENES.md](SCENES.md#dialogs-for-guests)):
  the village's own app never gets it (`GET /scenes` leaves it out), a visitor meets it (`GET /town/scenes` keeps it).
  The culture packs' first-visit dialogs are for guests: "Si prvič v gorah?", "Si prvič na gričih?", "Sei di fuori?".

### Questions between towns

The host takes part too (plan 2, §3.2), as in a family partner challenge: on a visit the guest asks the host a question
in the host town's language, and the host answers in it. Jan, visiting the second learner's Italian town, asks "Come si
chiama il tuo cane?"; the second learner answers in Italian. Both practise Italian, each with their own tutor.

- **Asking.** The bar under a visited town has "❓ Vprašaj · Ask". The panel ("❓ Chiedi a Luka · Ask Luka") takes one
  line of plain text, at most 200 characters, in the town's language. "✓ Preveri · Check" first is optional: `POST
  /towns/questions/check {text, town}` sends it to the learner's own tutor (`town_check`), whose reply comes on the
  conversation it answers (`tq_…`): short feedback, `data: {score, corrected}`, and "✏️ Use the correction". "Send":
  `POST /towns/:id/questions {id, text}`; the learner's bridge sends it on (a signed `POST /town/questions`) and keeps
  it (`<data>/app/town-questions.json`, `asked`). The learner's tutor gets it as `town_asked` with `data.language` the
  town's and the learner's level in it: it replies with brief feedback (kept as the question's `feedback`) and records
  it as a writing exercise in that language's profile (update-db.py's `language`, docs/DB_SCRIPTS.md, "Languages").
- **The host's side.** The host's bridge keeps the question once (`received`, by the asking town and its id: a retry is
  the same question) and tells its app (a `town_question` event: a notice and a notification, "❓ Vprašanje od Jana · A
  question from Jan") and its tutor: `town_question`, the text under `untrusted_town_content` (escaped like the
  family's), never as instructions, and never to be answered for the learner. In Friends, "❓ Vprašanja · Questions"
  lists guests' questions (the open ones first) and the learner's own; a question opens its screen.
- **Answering.** In the learner's own town's language, at most 300 characters, checked by the tutor first if they like
  (`{text, key}`), then `POST /towns/questions/answer {key, text}`. The host's tutor gets it as `town_answer` (the
  question as untrusted content), replies with feedback (kept as the answer's `feedback`, shown under it) and records it
  as practice in the town's language, their home one. Answering pays wisdom like a partner challenge's answer. The
  answer goes back with a signed `POST /town/answers`; when the guest's town isn't answering it is kept (`sent: false`)
  and sent again whenever the app lists the questions.
- **The guest's side.** Their bridge keeps the answer once (a different second answer is a `409 answered`, an answer to
  a question it didn't ask a `409 unknown_question`), tells its app (`town_answer`: "💬 Odgovor od Luke · An answer
  from Luka", in Friends and on the question's screen) and its tutor (`town_answered`, the answer as untrusted content:
  it doesn't reply, it helps if the learner asks).
- **Friendship.** Every question answered between two towns counts for their friendship ([Friendship between
  towns](#friendship-between-towns)): an act of kind `answer` in both towns' ledgers, which the host counts as given
  once the guest's town took the answer, and the guest as got when it came (so both towns count it once). The app also
  keeps it in the ties with the town, `GameState.towns[<town>].questions` (once per question: `q:<key>` in
  `GameState.guestbook`, with a line in the chronicle), and `GET /towns/questions` says how many were answered with each
  town, both ways (`answered`; `answeredWith()` in `bridge/src/questions.ts`).
- **Limits.** A linked town may send 10 questions a day (`LANI_TOWN_QUESTIONS_PER_DAY`), have 5 waiting for an answer
  at once (`LANI_TOWN_OPEN_QUESTIONS`, then `409 too_many_open`) and 30 writes an hour (`LANI_TOWN_WRITES_PER_HOUR`,
  counted apart from the visit's); beyond, `429`. An unlinked town, or a request without a town's signature, gets `401`.
- **A child's town.** Its links are a parent's (see [Link two towns](#link-two-towns)), so only those towns can ask. It
  takes no question with a link or an address (`409 not_for_a_child`). The child's tutor keeps the exchange
  age-appropriate (the child's rules): a question that isn't, it asks the child to show a parent, and doesn't help
  answer it. The parent sees every question and answer in the child's Friends screen, and on the node in
  `town-questions.json`.

### Friendship between towns

Linked towns grow a friendship (plan 2, §3.3), like the villagers' bonds: points and five levels, named for what two
towns are to each other. Each town's card in "🤝 Prijatelji · Friends" shows the level, a bar to the next one and what it
brings, and what can be done now, each with its button.

| Level | From | Name | What it brings |
|---|---|---|---|
| 0 | 0 | Znanstvo · Acquaintance | visits, the market at its first rates |
| 1 | 8 | Dobro sosedstvo · Good neighbours | help when trouble hits the friend; better rates at its market |
| 2 | 24 | Prijateljstvo · Friendship | a shared feast now and then; better rates |
| 3 | 48 | Tesno prijateljstvo · Close friendship | people may move between the towns; better rates |
| 4 | 90 | Pobratenje · Twin towns | the best rates |

**What counts**, in points per act: a day with a visit 2 (a visit opens with the town's people), a request done 3, a gift
2, a trade 3, help sent against trouble 4, a question answered 2 (a host's answer to a guest's question, kind `answer`: [Questions between towns](#questions-between-towns)).

**How both towns agree on the level:**
- Each town counts, per linked town, what it did there and what the other did here, as it happens, in
  `<data>/app/town-friends.json`: a count per kind of act, and the days of visits both ways. The first time, it counts
  what the guest book and the visit log already hold.
- `GET /town/friendship` (signed both ways, linked towns only) gives a linked town these counts, with this town's
  trouble now, its last feast and whether it is a child's. Nothing else: no resources, words or chat.
- Each direction's acts are the lower of the doer's count and the receiver's: an act counts once both towns have it. One
  town's records, even made up, can't raise the level: they can only lower that town's own report.
- The level's points are twice the smaller direction's points, plus how far the larger runs ahead, at most 30. A
  friendship that one learner keeps up alone stops at Friendship (level 2): people move only when both do their part.
- Both towns compute the level from the same four counts, so both show the same level. Each town grants what the level
  brings from its own computation: its market's rates, the help it takes, a move it says yes to. A town keeps the other's
  report for a minute (`LANI_TOWN_FRIEND_CACHE_MS`). While the other town doesn't answer, its last report (up to a day
  old) counts; without one, the level is 0.

**The market's rates** are the host's rates for the visiting town. `/town/market` gives them (`rates`), and the visitor's
market uses them:

| Level | 0 | 1 | 2 | 3 | 4 |
|---|---|---|---|---|---|
| The least share of the value taken that a trade gives | 0.5 | 0.45 | 0.4 | 0.35 | 0.3 |
| The haggle's least score | 6 | 6 | 5 | 5 | 5 |
| Goods spared, each | 3 | 3 | 4 | 4 | 5 |

**Help when trouble hits a friend.** From Good neighbours on, a friend's wolves, bear or storm (its village's event,
until its deadline) show on its card with "🤝 Pošlji pomoč · Send help". Help costs 10 🌾 and 10 🪵.
- `POST /towns/:id/aid {id, event}` sends `POST /town/aid` to the friend. The friend's guest book keeps it as kind `aid`:
  once per event and town, and a retry with the same id gets the same answer. A town may send at most 3 a day to one
  town (`LANI_TOWN_AID_PER_DAY`).
- The friend's app applies it once: the event has one helper more (up to 3). A lost event then takes a half, a third or
  a quarter of what it would, and damages a building less often. Help that comes after the event is over brings its
  supplies instead.

**The shared feast ("skupna veselica").** From Friendship on, when both towns held a feast three days apart or less, in
the last two weeks, each town's app applies it once: +10 morale, +2 🤝, a line in the chronicle and a notice. A feast is
the feast under the linden or a calendar festival. When a friend held a feast lately, its card gives the last day to hold
one to share it, with "🎪 Priredi veselico · Hold a feast" (or why the village can't yet).

**People moving between towns.** From Close friendship on:
- One town offers a move and the other says yes: nobody moves on their own. "In": someone of the friend's town moves
  here. "Out": someone of here moves there. The app offers with `POST /towns/:id/moves {id, dir}`. The other town's app
  says yes with `POST /towns/:id/moves/:move/accept`, or no with `…/decline`; the town that offered can withdraw with
  `…/decline`. Between the bridges: `POST /town/moves {v, id, action: offer|accept|decline}`.
- Someone moves in only when the learner's level in the friend's town's language is A2 or more (the language's profile,
  step M4; the learner's base language counts as theirs). The receiving town's bridge checks it, when it offers and when
  it says yes. The other town hears only "not yet" (`skill`).
- The one who moves is a newcomer of the sending town's culture, named from its culture pack (a first name, a family of
  its village, a trade), chosen by the sending town's bridge. Nobody leaves a village: the receiving village gets a
  resident of that culture (one villager more), and the sending one a line in the chronicle.
- The newcomer is bilingual: their lines are the village's plain lines and their culture's lines, in their language
  with the learner's base translation. So they speak their language with the learner now and then. The tutor hears of
  them (`villager_arrived` with `kind: moved`, their culture and language) and can give them lines of their own.
- Between two towns, one move is open at a time, one a fortnight each way, three each way at most.
- **A child's town.** A child's town offers moves itself, and its app shows what may happen. It never says yes to one
  (`403 child`), and it refuses offers from other towns (`409 child`). So a move with a child's town happens only when
  the grown-up on the other side says yes; in the family, that is the parent's own town. The parent also decides which
  towns the child's town is linked with (only on the node, see [Link two towns](#link-two-towns)) and can let a town
  go (`lani-town unlink <town> --profile <child>`). Two children's towns can't move anyone.

**The app's routes** (the app token): `GET /towns/friendship` gives `{self, levels, points, towns: [{id, name, learner,
culture, language, status, level, points, next, mine, theirs, mine_points, theirs_points, rates, child, event, feast:
{mine, theirs, shared, open_until}, moves: {skill, in, out, list}}]}`; each linked town is asked for its report now (3
s). Then `POST /towns/:id/aid`, `POST /towns/:id/moves` and `POST /towns/:id/moves/:move/accept` or `/decline`. The
refusals are `409` with a `code` (`too_early`, `no_event`, `already_helped`, `level`, `skill`, `child`, `open`, `soon`,
`enough`, `not_yours`, `declined`), `403` (`child`) and `429` (`rate_limited`). The app reads the list on every sync and
on the Friends screen: what it brings is applied once each (`GameState.guestbook`), and a friend's trouble, a move
offered or a feast to share shows once as a notice. Help and moves also come as `town_guest` events (kind `aid`,
`move`).

## App API

The answers that carry content (scenes, stories, villagers, arrivals, readings, modules, packs, role-plays, the
grammar book, the course, drills, the family's words, a town's villagers, scenes, requests and market) are said to the learner of
the profile: `{learner}` and `{m:…|f:…}` rendered for them (`Route.addressed`, `bridge/src/addressee.ts`; the tutor's
tools keep the placeholders, [SCENES.md](SCENES.md#the-learner-in-the-content)).

All endpoints except `/health`, `POST /pair`, the family page and the routes between towns (`/town/…`, signed by the asking town, see [Towns](#towns)) need `Authorization: Bearer <token>`: the app token, or a paired phone's device token. The towns' routes for the app (`/towns…`) are in [Towns](#towns). The family token reaches only the `/family` and `/audio` routes, and can read voice clips (see [Family](#family)). JSON bodies are limited to 256 KB (`413` beyond; `PUT /game` 512 KB, audio 2 MB, a chat photo 8 MB); errors never carry stack traces. `PUT /game` keeps the village's `land` when a state leaves it out (an app from before generated land: GAME.md, "The land"), and the learner's `course` the same way (an app from before the school: [COURSE.md](COURSE.md#the-learners-progress)).

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/pair` | `{code, device}` (no token) → `{v, token, device: {id, name}, profile, learner: {name, target, base, child}, fingerprint, public_key, signature}`; `403` wrong or spent code, `410` expired, `429` too many wrong codes (see [Pair a phone](#pair-a-phone)) |
| `POST` | `/message` | `{kind: chat\|answer\|session_end\|roleplay\|roleplay_end\|villager_arrived, conversation_id, text, data?, image?}` → into the session (`fam_…` conversation ids are the bridge's); a chat's `data.reply_to` quotes an earlier message of the chat; a chat's `image: {id}` names a photo sent before it, and the tutor gets its path as `data.image` (`400` for an id the node hasn't, or with another kind; see [Tutor chat](#tutor-chat), "Photos") |
| `PUT` | `/chat/images/:id` | a chat photo's bytes (JPEG, PNG or WebP by its content, at most 8 MB; `413` beyond, `415` for anything else), its id the message's client id → `{id, width, height, bytes, type, file}`; kept as `<data>/app/chat-images/<YYYY-MM>/<id>.<ext>` without its metadata; the same id again → its first answer and `existing: true` (see [Tutor chat](#tutor-chat), "Photos") |
| `GET` | `/chat/images/:id` | the photo's bytes (its type, `cache-control: private, immutable`); `404` when the node hasn't it |
| `DELETE` | `/chat/images/:id` | deletes it → `{id, deleted}` (`false` when there was none) |
| `GET` | `/events` | SSE stream: `reply`, `module_published`, `pack_published`, `grammar_published` (`{id, title, emoji, extended, note}`), `scenario_published`, `scene_published`, `scene_removed`, `variant_published` and `variant_removed` (a happening's dialog the tutor wrote or took back, `{scene, happening, id, title, emoji, note}`: the app reloads the scene, news only with a note), `villager_published`, `villager_removed`, `permission_request`, `app_update`, `partner_challenge`, `voice_updated`, `words_added`, `lexicon_updated`, `reading_published` (`{id, title, level, note}`) and `reading_removed` (`{id}`), `worksheet_published` (the tutor's homework for a unit of the course, `{unit, id, title, note}`, the title as "target · base") and `worksheet_removed` (`{unit, id}`), `town_guest` (a guest from a linked town brought help, a gift, a trade or help against trouble, or a move changed: `{key, kind, from, learner}`, kind `help`, `gift`, `trade`, `aid`, `move`), `town_question` and `town_answer` (a guest's question to the learner, the answer to the learner's: `{key, from, learner, text}`). Resume with `Last-Event-ID`. |
| `POST` | `/permission` | `{request_id, behavior: allow\|deny}` → answers a tool approval prompt |
| `POST` | `/reviews` | `{results: [{item_id, quality 0-5}], duration_minutes, language?, forms?, dialog?, answers?, from?, answered_at?, counting?, forms_cards?}` → persisted via `update-db.py` (no LLM), Claude gets a `review_done` summary. `counting`: how its counting cards went, step by step: each wrong step a mistake of its kind (see [Counting cards](#counting-cards)). `forms_cards`: how its forms cards went, gap by gap: each wrong gap a mistake of its kind (see [Forms cards](#forms-cards)). `answers`, `from`, `answered_at`: what each card was answered with, where in the app and when, for the log of [recent answers](#tutor-chat) (read leniently: never a reason to refuse a review; an older bridge drops them). `language`: another language's deck (a code, e.g. `it`: practised on visits), the home language's by default. `forms`: the cards that asked a form of their word, `[{item_id, key, page, right, answer?, expected?}]` (see [Word forms](#word-forms)): the summary names the wrong ones, nothing else is written; an older bridge drops it. `dialog`: the learner's words a dialog's turns tested ([GAME.md](GAME.md#your-words-in-the-dialogs)), `{scene?, happening?, dialog?, person?, title?, words: [{item_id, word, lemma?, right, how: meaning\|form, review?, said?, expected?}]}`, its reviews in `results` (quality 4 chosen or tapped, 5 typed or said; `results` may be empty then): the bridge keeps a review only of a card within its window and not reviewed today (`skipped` says the others), lowers gently each card got wrong about its meaning, once a day (`lowered`), leaves a form slip alone, and the tutor gets a `dialog_words` line instead; an older bridge drops `dialog` and takes `results` as a review's |
| `POST` | `/level` | `{from, to, stations: [{id, skill, right, total, minutes?}], duration_minutes?, language?, client_id}`: the treasure found (the level test at the end of `from` passed, [GAME.md](GAME.md#zemljevid-zaklada--the-treasure-map)) → the new level `to` (the one after `from`, else `400`) persisted via `update-db.py` (the report's `level` sets `current_level` and `level_since`; a session `/lani-app-treasure`, the stations' answers by skill), Claude gets a `level_up` summary with the new level's grammar pages; `{already: true, level}` and nothing written when the level is there already. An older bridge answers 404: the app sends it as a `session_end` with the report for the tutor to persist |
| `GET` | `/readings`, `/readings/:id` | the reading corner's readings (see [GAME.md](GAME.md#the-reading-corner)): the village's culture pack's (`companion/cultures/<culture>/readings/`, read per request) with `source: curated` and `culture`, then the tutor's (`<data>/app/readings/`, `publish_reading`) with `source: tutor`, `machine_written` and `published_at` |
| `POST` | `/readings/done` | `{kind: reading\|story\|aloud, id, title?, level?, exercises, correct, duration_minutes, questions?: [{type?, right}], looks?, aloud?: {words, right, misread, skipped, wpm?, recognizer: node\|phone, misses?: [{word, heard?}]}, language?, client_id}` → reading practice persisted via `update-db.py` (no LLM): `skill_scores.reading` (a reading's questions, a story's turns, a reading aloud's sentences; a story's "🎯 Preveri se" in the story notebook reports as a reading, `id` `book:<story>`, its questions the telling's turns), and `speaking` too for a reading aloud (`command_used` `/lani-app-reading`, `/lani-app-story`, `/lani-app-read-aloud`); Claude gets a `reading_done` summary, with `looks` (how often the text, hidden while its questions were asked, was shown again: [GAME.md](GAME.md#read-first-then-answer)) as "looked back at the text 3 times while answering" |
| `GET` | `/state[?language=it]` | all 6 learner databases + computed fields (no LLM involved), the home language's or, with `language`, that language's; always with `language` (whose they are) and `languages: [{code, name, level, words, due, source: home\|visits}]`, the home one first (see [docs/DB_SCRIPTS.md](../docs/DB_SCRIPTS.md#languages)), and `features`: what this bridge takes that an older one doesn't (`"dialog-words"`: POST /reviews' `dialog`); an older bridge has none, and the app sends it only what it takes |
| `GET` | `/modules`, `/modules/:id[?version=N]` | published modules |
| `GET` | `/packs` | word packs (curated: the village's language's, `companion/packs/` for Slovene or `companion/packs/<language>/`, then the culture pack's `packs/`; + tutor `<data>/app/packs/`) with `source`, `total`, `learned`, `language` (and `festival` for a festival's pack, `praznik-*`, `festa-*`; `project` for a village project's, `projekt-*`) |
| `GET` | `/packs/:id` | a pack with its words and the ids of words already learned |
| `GET` | `/grammar` | the grammar book of the village's language: the curated pages (`companion/grammar/<language>/`, `LANI_GRAMMAR_DIR` for another root) with what the tutor added to them (`extended`), then the tutor's own pages (`<data>/app/grammar/`), each with `source` (`curated`, `tutor`) and `published_at` for the tutor's (see [Grammar book](#grammar-book)) |
| `GET` | `/cards` | the cheat sheet's cards no grammar page fits: the curated ones (`companion/cards/<language>/`, `LANI_CARDS_DIR` for another root), then the tutor's (`<data>/app/cards/`, `publish_card`), each with `source` (`curated`, `tutor`) and `published_at` for the tutor's; `GET /cards/<id>` one (see [Cheat sheet](#cheat-sheet)) |
| `GET` | `/grammar/:id` | one page |
| `GET` | `/course` | the school's course of the village's language (see [The school's course](#the-schools-course)): `{schema: "lani.course/v0", language, levels: [{level, units: [{id, title, planned, grammar}]}], units: [<lani.unit/v0> + homework: [<lani.worksheet/v0> + source: "tutor", published_at]], machine_written, review?}`; `404` `{error: "no course"}` when the language has none |
| `GET` | `/course/sheet/:unit` | a unit's printed sheet, a page to print ([COURSE.md](COURSE.md#in-the-app)): `text/html`, the generator's own (`companion/prints/build.ts`) with its CSS, fonts and `fit.js` inlined, for the learner's base language, name and gender (`?base=en\|de\|it`, `?learner=`, `?gender=m\|f\|both` say otherwise; `400` for another); headers `x-lani-print-name` (`01-biti`) and `x-lani-print-units`; `404` for a planned or unknown unit. Kept until the course, the grammar book, the packs, the dictionary or the print's own files change |
| `GET` | `/course/booklet/:level` | the level's booklet the same way: the cover, the contents with page numbers, every unit (`lani-sl-A1-en`); `404` for a level without units |
| `GET` | `/drills` | the car's audio drills of the village's language (`companion/drills/<language>/`, `LANI_DRILLS_DIR` for another root), each with `source: "curated"` (see [The drills](#the-drills)); an older bridge answers 404 and the app plays its own copy |
| `GET` | `/drills/:id` | one drill |
| `POST` | `/packs/:id/learn` | `{results: [{word_id, quality 0-5}], duration_minutes, language?}` → new vocabulary SR items via `update-db.py` (known words skipped), in the home language's data or `language`'s, Claude gets a `words_learned` summary. A pack session and a festival's run in the village both post here |
| `GET` | `/lookup?w=…&line=…&en=…&base=…&language=…` | what a word tapped in a dialog means, in the learner's base language (or `base`); `language`: another town's, on a visit: `{word, entries: [{lemma, pos, gloss, gloss_lang, gloss_via?, gloss_en?, fallback?, grammar, gender, source, item_id, known, example, emoji, aspect?, partner?, here?}], attribution}` (see [Word lookup](#word-lookup), and [Word forms](#word-forms) for a verb's partner) |
| `GET` | `/forms?w=…&w=…` | a word's forms (1-40 words): `{language, words: [{word, entries: [{lemma, pos, gloss, gender, aspect?, partner?, table: wiktionary\|checked, slots: [{key, forms, also?, grammar, page, needs?, lines?}]}]}], attribution}`; an older bridge answers 404 (see [Word forms](#word-forms)) |
| `POST` | `/words` | `{sl, en, pos?, item_id?, example_sl?, example_en?, from?, language?, client_id}` → `{item_id, added}`: a vocabulary SR item, without a session, the home language's or `language`'s (see [Word lookup](#word-lookup)) |
| `POST` | `/counting` | `{words: [{word, item_id?}], client_id?}` (1–40) → `{added, skipped, made_today, per_day}`: counting cards of the learner's learned nouns, from the dictionary's forms, at most `LANI_COUNTING_PER_DAY` (3) a day (see [Counting cards](#counting-cards)); `400` without words or in a village that isn't Slovene |
| `POST` | `/forms-cards` | `{words: [{word, item_id?}], introduced: [page ids], asked?, client_id?}` (0–40 words) → `{added, skipped, resurfaced, made_today, per_day, resurface_per_day}`: forms cards of the learner's learned words from the dictionary's forms, at most `LANI_FORMS_PER_DAY` (2) a day, one at once with `asked`; the cards the `introduced` pages ask more of come back within days, at most `LANI_FORMS_RESURFACE_PER_DAY` (3) a day (see [Forms cards](#forms-cards)); `400` in a village that isn't Slovene |
| `GET` | `/forms-cards?w=…&w=…` | each word's tables as its forms card would have them, to read (1–10 words): `{language, words: [{word, tables: [{lemma, pos, gender?, gloss, emoji?, table, slots, tested: []}]}], attribution}`; nothing is written, no card made: the app's overview, "📋 Vse oblike" (see [Forms cards](#forms-cards)); an older bridge answers 404 |
| `GET` | `/words` | `{from: [scene, roleplay, villager, chat, reading, search]}`: the origins `POST /words` takes (an older bridge: 404) |
| `GET` | `/scenarios` | role-play scenarios (curated `companion/scenarios/` + tutor `<data>/app/scenarios/`) with `source` |
| `GET` | `/scenarios/:id` | one scenario |
| `GET` | `/scenes` | village scenes (curated `companion/scenes/` + tutor `<data>/app/scenes/`, a tutor scene replacing a curated one with its id) with `source`, every object resolved from its pack word (`sl`, `en`, `emoji`, `gender`, `plural`, `example_sl`, `example_en`) |
| `GET` | `/scenes/:id` | one scene, resolved |
| `GET` | `/villagers` | the people of the village (curated: the cast of the learner's culture pack, `companion/cultures/<culture>/villagers/`, or `LANI_VILLAGERS_DIR`; + tutor `<data>/app/villagers/`, a tutor villager replacing a curated one with its id) with `source`, in arrival `order` (see [VILLAGERS.md](VILLAGERS.md)) |
| `GET` | `/villagers/:id` | one villager |
| `GET` | `/culture` | the culture pack the village is in (`id`, `language`, `status`, `region`, `name`, its `places`); when the profile asked for one that can't be played (a stub), also `wanted` and `why` (see [GAME.md](GAME.md#culture-packs)); `landscape` when the profile has one for a new village (`lani-profile add --landscape`) |
| `GET` | `/cultures` | the culture packs there are, with their language and status |
| `GET` | `/villagers/:id/scenario` | a role-play (`lani.scenario/v0`, id `villager:<id>`) with that villager, built for the current friendship (`bonds` in the village state); a resident who is not in the cast gets one from their name and role |

### Role-play ("Pogovor · Talk")

The app plays a scenario as a conversation with the tutor in character. The opener comes from the
scenario; every line of Jan's is a `/message` with `kind: roleplay`, `conversation_id: rp-<uuid>` and
`data: {scenario_id, turn, heard, alternatives?}` (`heard` is the recognizer's text or what the learner typed;
`{scenario_id, turn, hint: true}` asks for a suggestion). The bridge adds the scenario brief (role,
setting, goals) so the tutor stays in character. The tutor's `reply` carries
`data: {sl, en, correction?, goals_done: [goal indices], end?}`, or `{hint: true, sl, en}` for a hint.
`kind: roleplay_end` sends the transcript; the tutor replies with a debrief (`data: {debrief: true}`) and persists it as a
speaking session (lani-db-updater). The app credits the village: one wisdom entry per line,
correct when it needed no correction, almost when it did.

Jan says a line with the talk's 🎤 (tap to start and stop, or hold it), as many sentences as they like: a pause of
3 s ends the take, shorter ones don't. The node's Whisper listens when it is up: one recording per stretch, sent at
once when they tap "Naslednji stavek · Next sentence" (a full stop, and it records on) and cut at a pause before the
node's 30 s limit; the transcripts land in the order said, the words Whisper was unsure of (probability under 0.3, at
most 3 a take) underlined, to say again or type. When the node can't be reached the phone's recognizer listens
instead, again after each sentence. The text waits in the input field until Jan sends it (`heard`, spoken unless they
typed it all). Under the debrief, "Vprašaj učitelja · Ask your tutor" sends `kind: chat` on
`conversation_id: rp-<uuid>-debrief` with `data.about_roleplay: {scenario_id, title, conversation_id, turns,
goals_done, goals_total, debrief}`; the tutor's answers show there as a thread, which can move on to the chat.

A talk with a villager uses the same flow with `scenario_id: villager:<id>`: the bridge builds the
scenario for the friendship and adds `data.villager` (persona, friendship level, memories) to every
turn, so the tutor plays them in character and remembers; the debrief may carry `data.memory: {sl, en}`,
which the app keeps as a memory. `kind: villager_arrived` (`data: {id, name, role, family, kind:
newcomer|birth}`) tells the tutor someone moved in, so they can give them lines with `publish_villager`.

| `GET` | `/voice/index` | node voice clips: normalized Slovene text → `{<speaker>: url}` (`female`, `male`, `grandma`, …; either token) |
| `GET` | `/voice/file/:name` | one clip, `audio/mpeg` (either token) |
| `POST` | `/voice/say` | `{text, voice?: <speaker>}` (default `female`) → voices it now if an engine is available, caches it, `{url, voice, engine}` (another voice's clip when this one can't be made); `204` when none is; `400` for a voice not in the cast |
| `POST` | `/voice/redo` | `{text, voice?, client_id?}` → the clip made again now (a long press on 🔊): `{status: done\|not_needed, url, key, voice, …}`, `429 {status: limit, reason: cap\|reserve}`, `503 {status: failed}`; once a clip a day, within the day's cap (see [Voice](#voice)) |
| `POST` | `/voice/prepare` | `{texts: [{text, voice?}]}` (1 to 20; the car's quiz getting ready) → `{clips: [{text, voice, url, engine?}], today, daily}`, in order: the clip there, else voiced now but not as a live line (ElevenLabs only above the reserve, then the local worker); `url: null` when it can't be, or for a voice not in the cast (no other voice's clip); at most `LANI_VOICE_ROAD_DAILY` characters (6,000) voiced new a day |
| `GET` | `/voice/status` | clips per voice and engine, ElevenLabs quota, engines up or down, the voice cast, corpus coverage, the voices filtered (`denoise`), the level (`level`: target, clips at it and waiting) |
| `GET` | `/voice/offline` | the phone's offline voices, one per language (see [Voice](#offline-on-the-phone)): `{voices: [{language, id, name, quality, bytes, status: missing\|fetching\|ready\|failed, progress?, error?, licence, attribution, source, files?, inference?, sample_rate?, espeak?}]}`; `files: [{path, bytes, sha256}]` once ready |
| `GET`, `POST` | `/voice/offline/:language` | one voice's entry; `POST` fetches it from Hugging Face into the node's cache unless it's there (`200`) or on its way (`202`), and after a failure tries again; `404` for a language without one |
| `GET` | `/voice/offline/:language/<path>` | a file of a ready voice (only the paths its `files` list; Range requests answered), `application/octet-stream` |
| `GET` | `/audio/index` | family recordings: normalized Slovene text → `[{file, speaker, recorded_at, text}]` |
| `POST` | `/audio?text=…&speaker=…` | a recording (raw body or multipart `file`; m4a, ogg or webm, max 2 MB), stored at the voice clips' loudness |
| `GET`, `DELETE` | `/audio/file/:name` | one recording |
| `GET`, `PUT` | `/family/api/settings` | the partner's name, emoji and Slovene "from" form (`Maje`) |
| `GET` | `/family/api/words` | words and phrases to record (pack words, examples, vocabulary cards) |
| `GET`, `POST` | `/family/api/challenges` | partner challenges `{type: question\|translate\|say, text, hint?}` with answers and feedback |
| `POST` | `/family/api/challenges/:id/answer` | `{text, spoken}`: Jan's answer (app token only) → `partner_answer` to Claude |

### Bridge code

`bridge/src/build.ts` builds the bridge (the shared stores, the features, the guide, the tools) on a platform: a
node's (`bridge.ts`: its config, its MCP channel, its voices) or the phone's (`src/phone/entry.ts`: the bridge in the
app, below); `bridge/src/bridge.ts` only wires the node's parts. Each feature in `bridge/src/features/` (chat, modules, grammar,
rhythm, game, packs, lexicon, scenarios, scenes, stories, readings, villagers, culture, family, voice, stt, reviews, level, events, releases,
pairing, towns) exports
`(ctx: Ctx) => Feature`: its HTTP routes (method, path, and who may call it: `public`, `any` token
or `app` only, the default), its MCP tools and its paragraph of the channel guide. To add a
feature, write the module and add it to the `FEATURES` list in `build.ts`. `bridge/src/index.ts` is what `.mcp.json`
starts: the bridge (classic), or the shim (`shim.ts`) when `LANI_BRIDGE_SHIM=1`. `service.ts` starts the bridge as a
service (`bridge-service.ts`: the app's API, the control socket, the status, the stop), and `control.ts` is what the
service and the shim share: the socket's place, MCP over it, the hellos. The shim, `control.ts` and `index.ts` reach a
running tutor only when it restarts: keep them small. Claude Code keeps only the
first 2048 characters of a server's instructions, so the instructions are just the rules that must
never be lost (answer with the `reply` tool; a child's rules), and the whole guide comes from the
`channel_guide` tool, which the tutor calls before its first reply and again after compaction. The shared parts are
`http.ts` (route table, tokens), `channel.ts` (MCP server), `tool.ts` (the tools' shapes, `ok`/`fail`, what an event
into the session looks like: the phone's channel shares it), `events.ts` (SSE),
`outbox.ts` (client_id dedupe), `learner.ts` (`read-db.py`, `update-db.py`), `db/` (the TypeScript twin of the
learner-data scripts, for a bridge without Python: `run(script, argv, stdin, env)`,
[docs/plans/05-built/db-port.md](../docs/plans/05-built/db-port.md)), `learners.ts` (profiles and
the learner's channel instructions), `cultures.ts` (the culture packs' schema and which one a bridge plays),
`pairing.ts` (the bridge's key, pairing codes, device tokens) and `towns.ts` (links, invitations,
signatures between bridges, a town's public state), both shared with the CLIs in `bridge/src/cli/`, `visits.ts`
(what a town gives a visiting one, cleaning it, the cache of other towns' answers, the talks on a visit),
`guests.ts` (the things to do on a visit: a host's requests and market, the guest book, the visitor's log, the limits),
`friendship.ts` (friendship between towns: the counts, the level both agree on, the rates, who moves),
`dialog-words.ts` (the learner's words in the dialogs: which turns test a word, the check of a variant's `words`, the
words to weave and where they fit, the window a dialog's review counts in; [SCENES.md](SCENES.md#your-words-in-the-dialogs))
and `questions.ts` (questions between towns: both sides' book, the limits, the count of answered questions; its routes
and the tutor's notes are `features/questions.ts`, which keeps the tutor's replies to its `tq_…` conversations
through `Events.tap`).
`bun test/smoke.ts` runs the smoke sections in `bridge/test/smoke/` against one bridge, on `LANI_SMOKE_PORT` (18800 by
default: out of the learners' range, 8792 to 8899, and away from QA's and the workers' ports); the pairing, friuli,
kaernten, lakeland, towns and service sections start more bridges of their own (pairing: `LANI_SMOKE_PORT` + 1; friuli,
then pairs: `LANI_SMOKE_FRIULI_PORT`, by default `LANI_SMOKE_PORT` + 2; kaernten: `LANI_SMOKE_KAERNTEN_PORT`, by default
`LANI_SMOKE_PORT` + 5; lakeland: `LANI_SMOKE_LAKELAND_PORT`, by default `LANI_SMOKE_PORT` + 6; towns:
`LANI_SMOKE_TOWN_PORT` and the next port, by default `LANI_SMOKE_PORT` + 3 and + 4; setup: `LANI_SMOKE_SETUP_PORT`, by
default `LANI_SMOKE_PORT` + 7; service: `LANI_SMOKE_SERVICE_PORT` and the next two, by default `LANI_SMOKE_PORT` + 8
to + 10). Where pairing lets `lani-profile add` pick a port, it looks from `LANI_SMOKE_PORT` + 11 up
(`LANI_PROFILE_PORT_BASE`, for the tests only), so a smoke run never binds or probes a learner's port. The service
section runs bridge services (`test/fixtures/service-probe.ts`, with probe tools) and their shims under a scripted
Claude Code: the channel both ways, a restart mid-way with the reconnect and `list_changed`, a call during an outage,
the queue, a child's service beside it, and `lani-bridge`'s loop and CLI (without tmux).

**The bridge in the app** ([docs/plans/05-built/phone-runtime.md](../docs/plans/05-built/phone-runtime.md)): the same
bridge in QuickJS inside the app, for the hosted mode. `bridge/src/phone/` is its entry, its channel (the in-app
tutor) and its voices (none); `bridge/phone/build.ts` bundles it (`bun phone/build.ts`: `phone/dist/bridge-phone.js`),
node's built-ins replaced by the shims in `bridge/phone/shims/` over the app's host functions. A module the phone
shares must not import what it hasn't (`node:net`, the MCP SDK, a `bun:` module other than sqlite): the build fails then, and the
smoke's `phone` section with it. The app builds the bundle itself (Gradle's `phoneBridgeBundle`), so it needs bun
(below).

Try it without the app:

```bash
TOKEN=$(cat data/app/bridge-token)
curl -N -H "Authorization: Bearer $TOKEN" localhost:8790/events &
curl -H "Authorization: Bearer $TOKEN" -d '{"text":"Dober dan! Make me a drill for se."}' localhost:8790/message
```

## Tutor chat

The chat (the 💬 sheet over every screen) opens on today's conversation: the messages since midnight, by the phone's
clock and time zone (the app's day, as the village's). Everything before is folded into "📦 Arhiv · Archive", one row
above "Danes · Today" and today's messages; scrolling up from today reaches it.

What Home's tutor bar shows, the tutor's latest message, is never folded away: when it came before today (a late
answer last night, a reply while the learner was away), the conversation up front begins on its day, under "Včeraj ·
Yesterday" (or the day's name), with the days after it and "Danes · Today" below; the next day the tutor answers, it
goes to the archive as the rest.

- The archive opens into weeks, Monday first: "Ta teden · This week", "Prejšnji teden · Last week", then "14.–20.
  sept." (in the target language, with the year when it isn't this year's).
- A week opens into its days, "Ponedeljek, 28. septembra", each with one line of what was talked about: the learner's
  first question that day ("📎" and the exercise or grammar page it was about, else its text), else how the tutor began.
- A day opens into its messages, oldest first, as a chat reads. "🕰️ Starejša sporočila · Older messages" holds the
  messages kept before the archive, which have no times.

Every fold is closed when the chat opens. The list is lazy: a closed week or day is one row, and only the rows on screen
are drawn. At midnight, with the chat open, the day's conversation moves into the archive within half a minute. A
message dated after today (the phone's clock was set back) counts as today's.

**Bookmarks, copy, share.** A long press on a message, the tutor's or the learner's, opens its menu (TalkBack offers
the same as actions):

- "🔖 Zaznamek · Bookmark", or "Odstrani zaznamek · Remove bookmark": a bookmarked message shows a 🔖 beside it.
- "✏️ Opomba · Note": why it was kept ("Zakaj? · Why?", up to 200 characters). A note bookmarks the message too.
- "↩️ Odgovori · Reply" (see below).
- "📄 Kopiraj · Copy": the message on the clipboard as plain text. A tutor message is copied as it reads: bold, italics
  and code marks out, a list item a line ("• …", "1. …"), a table a line a row with its cells between " · " (its rule
  and empty cells left out), headings and quotes without their marks, a link as its text and its address ("SSKJ
  (https://fran.si/…)"). The learner's own message is copied as typed.
  Android 13 and later confirm the copy themselves; an older Android shows "📄 Kopirano · Copied".
- "↗️ Deli · Share": Android's share sheet with the same text (copy, messengers, notes …), the text's preview titled
  "Deli sporočilo · Share message".

"🔖" at the top of the chat (with their number) lists the bookmarks, newest first, each with its day, who wrote it and
its note. A tap goes to the message: the archive opens to its week and day, and the message is framed for two seconds.
↩️ replies to it, ✕ takes the bookmark away.

**Swipes.** As in messengers, a sideways swipe on a message is a shortcut for two of these: to the right "↩️" replies
to it, to the left "🔖" bookmarks it (a bookmarked one: takes the bookmark away, its note too). The bubble follows the
finger with resistance (half as far at 160 dp); the action's icon fades in on the side the bubble uncovers and grows
toward the threshold, 64 dp past the touch slop, with a light tick there. Let go past it and the action runs, and the
bubble springs back:

- A reply shows the reply card above the input, as from the menu.
- A bookmark shows its 🔖 beside the bubble and "🔖 Shranjeno · Saved", or "🔖 Zaznamek odstranjen · Bookmark removed".
  The snackbar's "Razveljavi · Undo" puts the bookmark back as it was, with its time and note. The snackbar shows for
  four seconds over the input at the bottom of the sheet, not over the messages: the one just swiped is often the
  newest, at the bottom of the list.

Let go before the threshold and the bubble only springs back. With the system's "Remove animations" on, the action
still runs and the bubble is back at once.

- Only a clearly sideways drag is a swipe: past the touch slop, at most 30° off the horizontal. A drag up or down, or a
  steeper one, is the list's scroll or the sheet's drag down, as before. A long press is the menu's, and a drag after it
  is no swipe.
- A swipe that starts on a word, a link or a reply's quote in the bubble is a swipe all the same, and taps nothing. A
  table wider than the bubble keeps its own sideways scroll; one that fits doesn't scroll, so a swipe on it is the
  bubble's.
- The typing bubble ("…"), the reply card and the context card above the input, and the bookmarks' list don't swipe.
  TalkBack has the same through the message's actions (the long press's menu).
- Once, under the tutor's latest message: "Povleci sporočilo: ← zaznamek · odgovor →" (✕ closes it). It also goes with
  the first swipe, or when the chat closes after it showed. The phone keeps that it was shown (`ChatPrefs`).

**Replies.** "↩️ Odgovori · Reply" quotes the message above the input, as messengers do (✕ lets it go). The next message
goes to the tutor with `data.reply_to`, next to what is attached now (`about_exercise`, …): `from` (`learner` or
`tutor`), `at` (ISO with the offset; none for a message from before the archive), `text` (up to 4,000 characters),
`about` (the label of the exercise or page it was about) and `attached` (the data that message was sent with, e.g.
`about_exercise` or `about_grammar`). The bridge gives the tutor a message's data as a JSON block, as it always has,
so an older bridge passes `reply_to` on unchanged; the chat feature's guide says what it is. In the chat, the reply shows
its quote, and a tap on the quote goes to the message it answers while the phone keeps it.

**Links.** A link in what the tutor writes is a link: in a chat message, an exercise's explanation, a card pinned on
the cheat sheet (`ui/Markdown.kt`, found by `data/Links.kt`). Markdown's `[text](https://…)` shows its text, a bare
`https://…` itself; a URL keeps the parentheses it opens itself (`…/Kranj_(mesto)`), and leaves out what ends the
sentence after it (`.`, `,`, `)`, `*` …); `<https://…>` shows without its brackets. Only `https://` and `http://` are
links: `javascript:`, `intent:`, `file:`, `content:`, an app's own scheme stay text as written, and so does a URL with a
user name before its host (`https://fran.si@other.example`), a backslash, an invisible character or an IPv6 address. A
link inside `code` stays code. The words around a link keep their taps (a Slovene word to hear, 🔈; a cheat sheet's
word to look up), a link's own words are the link's; the text stays copyable as before. Nothing is ever loaded to show
a message: no images, no previews.

A tap goes through the app's link gate (`ui/Links.kt`, the app's `UriHandler`, so every link in the app does; a sheet's
or a dialog's window gets the platform's own handler, which opens any link at once, so `LaniSheet` and `Markdown` put
the gate back there, `GateLinks`):

- **Opened at once:** https links to `fran.si` and its subdomains (the Slovene dictionaries: SSKJ, Pravopis, the
  etymological dictionary …, the tutor's links for a word), and to Lani's own repository, `github.com/fentas/lani/…`
  (its docs, issues and discussions, where the tutor points to a report or a wish; not the rest of GitHub, not
  `../` out of it). The one list is `Links.ALLOWED`; the tutor's guide (`bridge/src/features/chat.ts`) says the same.
- **Asked first:** any other https link: "🔗 Odpreti example.com? · Open example.com?", the address in full under it,
  "Odpri · Open" or "Prekliči · Cancel". The domain is the one the browser reaches: what follows `https://` up to the
  first `/`, `?` or `#`, an international name in its ASCII form (`xn--…`), so a look-alike shows as what it is, and a
  Markdown link whose text names fran.si but goes elsewhere asks for where it goes. A reply could carry a link put
  there by a prompt injection: the learner always sees where a link goes before leaving the app.
- **http:** asks as well, also fran.si's, with "⚠️ Povezava ni varna (http) · Not a secure link (http)".

It opens in the phone's browser (a browsable view: an app verified for the site may take it); without a browser, "🔗 Ni
brskalnika … · No browser to open the link.", and nothing else happens.

**The exercise on screen.** 💬 in the top bar of an exercise, a pack's word, a building's new word or a text read first
opens the chat with what is on screen attached, as "Ask" on an answer's feedback does: the chip "📎 …" above the input,
which ✕ takes away, so a general question still works. Nothing is sent until the learner writes (or taps one of the
chip's quick questions). The next message carries it as `about_exercise` (`ui/PlayerScreen.kt` `chatContext`,
`app/ChatContext.kt` `ChatAbout`), with its `source` where it has one (the item the tutor's lani-fix skill finds):

- an exercise before its answer: its type, prompt and options, `answered: false` and `expected` (for the tutor, which
  helps without giving it away); the chip's label gives nothing away either (a sound as 🎧, a card's front only). Once
  answered, the exercise with the answer, the verdict and the hint, as "Ask" sends it. A timed run attaches nothing
  while it counts down.
- a word met before its practice (a pack's, a building's): `type: word`, the word, its meaning, gender, plural,
  example and note; `source` the pack and the word's card, no `variant`.
- a challenge's text read before its questions (the day's letter): `type: reading`, its title, text and how many
  questions follow; `source` the challenge, no `exercise_index`.

Closed without a message, the chat lets the screen's attachment go, so the chat opened from Home later has none
(`ChatController.openFrom`); "Ask"'s stays as before. A sheet's "💬 Vprašaj učitelja" at a school sheet's end attaches
the unit with that sheet (`about_course`).

**Photos.** "📷" by the input ("Dodaj fotografijo · Add a photo") opens a menu: "📸 Fotografiraj · Take a photo" or
"🖼️ Iz galerije · From the gallery". For a sign, a menu, a letter or a textbook page ("what does it say, explain the
grammar"), a handwritten worksheet ("check my answers"), a thing at home ("what's this in Slovene?").

- **Taking one:** the back camera full screen (CameraX, as the QR scanner), asked for only now, as the scanner does
  (refused: why, and "Dovoli kamero" or the settings); the shutter "📸". **Picking one:** Android's photo picker, which
  needs no storage permission: the app sees only the photo picked.
- **Before it goes** (`data/ChatPhotos.kt` `PhotoPrep`, on the phone): turned upright by its EXIF orientation, scaled to
  a long side of 1,568 px (a smaller one keeps its size), JPEG at quality 80, and **without any metadata**: no place,
  no time, no camera, no device. The bitmap is encoded afresh (Android writes none of the old metadata), and
  `data/Jpeg.kt` takes out any APP1–APP15 segment and comment left (a colour profile stays). A transparent picture goes
  on white. A file over 48 MB, or one Android can't decode, says "📷 Te fotografije ne morem odpreti".
- **Above the input:** its thumbnail and "📷 Gre s sporočilom · Goes with your message", ✕ ("Odstrani fotografijo")
  lets it go (off the phone). It goes with the text, if any; a photo alone is a message too. The photo being made ready
  ("Pripravljam fotografijo …") holds the message back.
- **In the chat:** the bubble shows the thumbnail at its proportions (at most 240 dp wide), the text under it. A tap
  opens it full screen, on black: two fingers zoom (up to 6×) and move it, a double tap zooms in or back, ✕ or back
  closes it. The long press's menu is the other bubbles' (bookmark, note, reply, copy and share the text when there is
  one) and "🗑️ Izbriši fotografijo · Delete photo": asked first ("Izgine s telefona in z vozlišča. Učitelj jo je morda
  že videl."), the photo leaves the phone and the node (`DELETE /chat/images/<id>` through the outbox), and the bubble
  says "📷 Fotografija izbrisana"; the message and its text stay. TalkBack reads the thumbnail as "Fotografija · Photo"
  and offers to open it; the button, the shutter and the ✕ have their labels.
- **Sent:** one outbox entry (`Writes.chatPhoto`): the photo first, `PUT /chat/images/<id>` with the message's client id,
  then the message with `image: {id}`. Offline it waits in the outbox with its photo (the entry names the phone's file)
  and goes when the node is back; a retry sends both again, and the node keeps the photo once. A node from before photos
  (404; `GET /state`'s `features` without `"chat-images"`, which also hides 📷) and a photo deleted before it went get
  the text alone, with a line for the tutor that a photo didn't come.
- **On the node** (`bridge/src/chat-images.ts`): `<data>/app/chat-images/<YYYY-MM>/<id>.jpg` (the node's month at the
  upload; the folders 0700, the file 0600, written whole), only a JPEG, PNG or WebP by its bytes, at most 8 MB, its
  metadata stripped once more (a second net). The tutor's chat event carries `data.image: {path, width, height, bytes,
  type}` (the absolute path; an `image` the app puts in `data` itself is dropped), and a reply to a photo message its
  photo the same way (`reply_to.photo`, or `{id, gone: true}` once deleted). The guide (`bridge/src/features/chat.ts`)
  tells the tutor to open the path with its Read tool and answer about it: transcribe the Slovene, explain words and
  grammar at the learner's level, check handwritten answers kindly, name a thing; to offer a word pack (`publish_pack`)
  or a card of its words; and never to copy, send or publish the photo anywhere. The node keeps a year of them, as the
  phone keeps its chat: an older month's folder goes at the bridge's start and with each new photo. Never committed:
  the data repository's `.gitignore` keeps `/app/chat-images/` out (`lani_data_repo.py` writes the line and each commit
  adds it back, `PRIVATE`; the bridge appends it at its start to a `.gitignore` without it).
- **On the phone:** `files/chat-photos/<id>.jpg`, the app's private files (never the share sheet's), the message keeping
  its reference (`ChatMessage.photo`: id, size, deleted). A photo no message keeps any more (a draft left when the app
  closed, a month the chat let go after its year) leaves the phone when the chat loads, once it is a day old; a draft's
  ✕ deletes it at once. A message whose photo the phone hasn't (its files
  cleared) shows the node's copy (`GET /chat/images/<id>`), kept from then on.
- **The shadow tutor** sends a chat's photo to the Messages API as an image block (base64 JPEG) before the event's text,
  from the phone's copy, metered as any call; a call's record counts the images it carried (`images`), and its export
  (the share sheet's file and the node's copy) has a note of the size in the photo's place, never the photo.

**Where it is kept.** Only on the phone, in the app's private files. `files/chat/<yyyy-MM>.json` holds a month (by
the messages' time in UTC): each message with its time, its bookmark and note, the message it quotes and the data it was
sent with, and its photo's id and size (the photo itself in `files/chat-photos/`, see "Photos"). A change rewrites only
its message's month. `files/chat.json` is the conversation kept before the archive
(its last 100 messages, without times): read as it is, and written again only for a bookmark on one of its messages. The
chat keeps a year: when it loads, older months are deleted, except their bookmarked messages. The node keeps no chat
history (the event stream replays only its last 200 events), so neither the history nor the bookmarks sync: they
survive app updates, but not clearing the app's data or a reinstall (`android:allowBackup="false"`). Only the photos
are on the node too (a year), for a message whose photo the phone lost.

**Recent answers.** "Ask" on an exercise's feedback sends the tutor the exercise with Jan's answer (`about_exercise`).
A card reported later in a plain message ("kurim was marked wrong") came without it, and the app kept no answers, so the
tutor could only guess why. Now the node keeps what Jan typed or said lately:

- **What:** every card a review counts, one entry each, wherever in the app it was answered (`from`: the review screen,
  the grammar book's card practice, a building's words run, a village challenge, I spy, the car's ✓/✗): the card, how it
  was asked (`variant`: type, tiles, listen, …), the exercise and what it asked, what was typed or heard, what was
  expected, the verdict, the grading hint (typo, ending, diacritics, …), the hints used and what was looked up on the
  cheat sheet during it (`looked_up`: the cards opened, their grammar pages; see [Cheat sheet](#cheat-sheet)). The app
  sends them with the review (`POST /reviews` `answers`, `ui/PlayerScreen.kt` `reviewAnswer`). Where nothing is typed the entry says so: a
  self-rated flashcard, the car's ratings (knew or didn't), I spy's taps (the clues heard and the thing found). A form a
  review asked joins its card's answer (its slot and grammar page); a dialog's word turns go in too, a form the car's
  quiz asked once, with its word's turn.
- **When:** as answered: the app sends the review's time (`answered_at`; the car each rating's own), so a review the
  outbox sends a day later keeps its time; without one (an app from before), when it reached the node.
- **Where:** `<data>/app/answers.json` (`bridge/src/answers.ts`, readable by this user only), written once the review
  is persisted, with the quality it gave each card (a dialog's only for the cards the bridge counted) and the session. A
  rolling log: the last 7 days, at most 500 answers, the oldest dropped as new ones come; a review's first 100 answers
  (never a reason to refuse the review). Nothing else reads it, the learner's databases don't change with it, and a data
  repository never commits it: each commit makes sure its `.gitignore` keeps `/app/answers.json*` out, also in a
  repository set up before the line (`.claude/hooks/lani_data_repo.py` `PRIVATE`).
- **The tutor:** the `review_done` note names the answers typed wrong ("Answered wrong: vocab_hvala "hvla" for
  "hvala""), a form slip only with the forms, and what was looked up on the cheat sheet ("📋 Looked up on the cheat
  sheet: biti ×2, osebni-zaimki (during 3 answers).": each answer once for each page its cards name, a card without a
  page by its key; `answers.ts` `lookedUpNote`, a `session_end`'s too, there "during N exercises" and the worksheet
  items answered after it opened). The tool `recent_answers` lists them, newest first, for a card
  (`card_id`), a word in what was asked, typed or expected (`text`, any case, diacritics optional: "zivjo" finds
  "živjo"), only the wrong ones (`wrong_only`: by the verdict, so a right answer whose hints lowered its quality isn't
  one), only those given with the cheat sheet open (`looked_up`; each line says "📋 looked up biti, …") or a deck
  (`language`). With none it says what the log holds since when. The lani-fix skill asks it when Jan
  reports a card without "Ask", so the card can be fixed and its review taken back in one go.
- **An older app or bridge:** an app from before sends only the qualities: each card is logged with its quality and "no
  answer sent (an app from before)", and the tutor asks Jan; an older bridge ignores the answers, and its tutor has no
  `recent_answers` until the bridge restarts. An app from before the cheat sheet's lookups sends no `looked_up` (no 📋
  line); a bridge from before drops it from the answers (the schema isn't strict) and a `session_end`'s `looked_up`
  reaches its tutor only in the JSON block.

## The tutor in the app ("🧑‍🏫 Učitelj · Tutor")

The tutor can answer from the app itself, on the learner's own API key (plan 5, phase A;
[docs/plans/05-built/tutor-live.md](../docs/plans/05-built/tutor-live.md)). **Claude Code on the node stays the default
and works as before.** The app's tutor is a client of the bridge's channel: it gets the notifications Claude Code would
get and calls the same tools, through the bridge's `/tutor/*` routes.

**Turning it on.** "🔗 Povezava · Connection" (the status dot on Home) → "🧑‍🏫 Učitelj · Tutor":
- "Kdo odgovarja · Who answers": "🖥️ Claude Code na mojem vozlišču · Claude Code on my node" (the default), or
  "📱 V aplikaciji · In the app";
- the provider: Anthropic with a Claude API key, or OpenAI with "Nadaljuj s ChatGPT · Continue with ChatGPT" (the
  ChatGPT plan pays) or an OpenAI API key. A key is checked with `GET /v1/models` (not a model call) and kept encrypted
  with a key in the Android Keystore, in the same vault as the shadow tutor's: never shown again, logged or exported;
- the preset (Varčno · Economy, Uravnoteženo · Balanced ⭐, Najboljše · Best), "advanced" for the model and effort per
  task, the daily cap ($1 by default) and the monthly cap ($20 by default), today's and this month's spend, "Odzovi se
  takoj · React at once", "🌙 Kar lahko počaka, ponoči za pol cene · What can wait, overnight at half price";
- "✍️ Studio": the content jobs, open and the last few, each with a ✕;
- "📓 Kaj ve učitelj · What my tutor knows": the notebook on the node (`app/tutor/notebook.md`), to read and correct,
  and "🕘 Starejše različice · Older versions" (the last 20) to see and restore.

Once the credential is kept, the app attaches to the paired bridge. From then on:
- **The channel's events go to the app.** The Claude Code session hears nothing new: the bridge's log says so, a
  session that starts meanwhile reads it in its instructions, and the service's status shows `app_tutor`.
- **Replies stream into the chat.** The typing bubble fills as the reply is written, and the final message replaces it.
  The reply goes through the bridge's `reply` tool, so it reaches the chat as Claude Code's do: on Home's tutor bar,
  and as a notification while the app is closed.
- **The chat says an AI answers:** "🤖 Odgovarja umetna inteligenca (…) · An AI answers here (…)", with "Using ChatGPT
  plan" when the plan pays.
- **The data changes for real:** `record_session` (update-db.py), `card` (card.py), the notebook, glosses and sentence
  notes, through the bridge.
- **Reports go to Lani's GitHub discussions** when git sync is signed in to GitHub ("💾 Podatki · Data"): the tutor's
  `report_problem` looks the report up first, and the chat asks "📣 Pošljem v Lanijeve razprave? · Send to Lani's
  discussions?" with what would be posted. "Send" votes on the discussion that has it, or starts one ("Content fixes",
  "Ideas"); the link is kept in `app/reports.json`. Without a sign-in, or with "No, keep it here", it goes to
  `app/wishlist.md` as before.
- **Content is the studio's** (below): "make me exercises for X" becomes a job, and so do an arrival, dialog variants,
  stories running low and the dictionary's gaps.
- **What goes wrong is said in the chat, briefly:** the cap spent (today's or the month's: "Vseeno odgovarjaj na moja
  sporočila ta mesec · Answer my messages anyway this month"), the key refused, the credit out, no connection.
- **The shadow tutor is off** meanwhile (it would shadow itself).

While the app is up it long-polls the bridge's inbox. While it's closed, a WorkManager check runs about every 15
minutes and answers what waits (a chat, the rhythm).

**Turning it off.** "Claude Code on my node": the app detaches. The bridge gives the session every event the app
hadn't answered, and Claude Code answers as before. The studio's jobs for events end (their events go back to Claude
Code); the learner's own requests wait until the app's tutor is on again.

### The studio: content written in the app

[docs/plans/05-built/studio.md](../docs/plans/05-built/studio.md). The chat's tutor hands a brief to the studio with
`request_content` ("make me exercises for the genitive", "words for the doctor", "a story about the vintage"), and says
it's on its way. The studio is a job of its own, on the content model (Opus 5.5 at the preset's effort):
- it reads the guide's topics and the learner's state, then publishes with the bridge's own tools (`publish_module`,
  `publish_pack`, `publish_story`, `publish_reading`, `publish_scene`, `publish_dialog_variant`, `publish_villager`,
  `publish_arrival`, `publish_grammar`, …). They validate on write: a refusal is the validator's message, and the model
  fixes it and publishes again;
- it has the file tools over the learner's data folder (`str_replace_based_edit_tool`, Anthropic's text editor, and
  `search`), checked on write by the same validators; the six databases are read-only to them;
- a large job gives its parts to sub-tasks, each on a thread of its own;
- each step is saved on the bridge (`app/tutor/jobs/<id>.json`), so a killed app goes on where it stopped;
- the chat shows "✍️ Pišem … · Writing …" with the job's step and what it published, and a ✕; the studio tells the
  learner itself when it's done.

When it runs: a request the learner waits for at once (in the background too, with the network); an arrival soon; new
dialog variants, a story for the fire and the dictionary's gaps overnight, through Anthropic's Message Batches API at
half price (OpenAI: while the phone charges on Wi-Fi). Everything is metered in the spend.

**Local overrides of curated content.** A module, scene, story or villager the studio publishes under a curated id
replaces it for this learner at once. The bridge records the curated file's hash (`app/overrides.json`); when the
curated file changes upstream (the fix arrived), the override retires by itself, its report is marked resolved, and the
tutor hears `override_retired`.

**The bridge's routes** (`companion/bridge/src/features/tutor.ts` and `src/tutor-studio.ts`, the app's token only):
- `GET /tutor/session`: the channel's instructions and tools as Claude Code gets them, the app session's own tools
  (`learner_state`, `record_session`, `card`, `notebook_note`, `notebook_write`, `report_problem`,
  `str_replace_based_edit_tool`, `search`), protocol 1;
- `POST /tutor/attach`, `POST /tutor/detach`;
- `GET /tutor/inbox?after=&wait=` (a long poll of at most 25 s) and `POST /tutor/inbox/ack`, kept in
  `app/tutor-inbox.json` (out of the data repository);
- `POST /tutor/tools/:name {args, client_id}`: through the same handlers as Claude Code's tool calls, once per
  `client_id`;
- `GET` and `PUT /tutor/notebook`, `GET /tutor/notebook/versions`, `POST /tutor/notebook/restore {at}`;
- the studio's: `POST /tutor/check {kind?, path?, text}` (a validator's verdict, nothing written), `GET /tutor/jobs`,
  `GET`/`PUT`/`DELETE /tutor/jobs/:id`, `GET`/`POST /tutor/reports`, `GET /tutor/overrides`.

**QA.** `companion/bin/qa --steps tutor-live` runs on the emulator with the dev bridge and QA's mock of the Messages
API (`test/tutor-mock.ts`, its `[mock:slow]` reply). The step:
1. turns the app's tutor on with a fake key, and checks the dev bridge's channel is the app's;
2. sends a chat, and takes the streamed reply mid-way and in full; the dev tutor never hears the chat;
3. posts a session's end, and checks its reaction and the `record_session` in the dev data;
4. turns the app's tutor off, and checks the dev tutor answers again.

The QA hook `tutorlive:<url>` sets the mock's address in a debug or qa build.

`companion/bin/qa --steps studio` plays the studio against the mock (its studio calls slowed by
`LANI_TUTOR_MOCK_STUDIO_MS`): a chat's `[mock:request]`, the module refused by the validator then published (on the dev
bridge, the banner); a story through the text editor, refused then written; a slow job, the app killed mid-way and
started again, the job resumed from the dev bridge's copy.

## The in-app tutor in shadow mode (developer setting)

The prototype of plan 5's in-app tutor ([docs/plans/05-in-app-tutor.md](../docs/plans/05-in-app-tutor.md), phase 0,
check 6 in [05-phase-0.md](../docs/plans/05-phase-0.md)). It runs in the app on the learner's own Claude API key, on
the learner's real events, next to the real tutor. **It watches; it never acts:** the real tutor still answers
everything, and the shadow's answers show only in its debug panel.

**The developer setting.** Off by default, in the release app too. Open "🔗 Povezava · Connection" (the tutor's status
dot on Home) and tap the version line at the bottom ("Lani 0.2.…") seven times: "🛠️ Razvijalec · Developer" appears
with "🕵️ Učitelj v senci · Shadow tutor", its switch, its status and "🔍 Odpri pregled · Open the panel". Turning it on
without a key opens the panel, which asks for one.

**The key.** Pasted in the panel (or in "🧑‍🏫 Učitelj · Tutor": one vault for both, and one ChatGPT sign-in) and checked
with `GET /v1/models` (not a model call). Make it in the Console in a
workspace of its own ("Lani") with a monthly spend limit (the Default workspace can't have one), expiry "Never". The
app keeps it encrypted: an AES-GCM key in the Android Keystore (never exportable) seals it, the ciphertext is in a
DataStore of its own (`tutor_secrets`); it is decrypted in memory for a call only, and goes only to
`https://api.anthropic.com` in its `x-api-key` header (a debug or qa build, `BuildConfig.QA`: also to the mock below). Never in a log line
(`tutor/KeyVault` `Redact`), the export, a prompt or a tool's result. "Remove" deletes it and its Keystore key; a 401
pauses the shadow until a new key or "Check again" (the events stay queued).

**What it sees.** Every write the outbox delivered to the node (`SyncController.onDelivered`): `POST /message` (chat,
answer, session_end, villager_arrived), `/reviews` (review_done, or dialog_words), `/words` (words_added),
`/packs/<id>/learn` (words_learned), `/readings/done` (reading_done), `/level` (level_up); the role-play's turns
(recorded, not run: phase B); and the real tutor's replies (the SSE `reply`), matched to the event of their
conversation (an answer's `ex…`; for "main" the latest chat or session end).

**Events to turns** (plan 5, "Events to turns"):
- at once: a chat, an answer; with "React at once" also reviews and session ends;
- into the day's digest: reviews, words, readings, session ends, level-ups. The digest runs when the app comes back
  after two hours or more, in the evening (from 19:00, once), when the learner opens the chat, or by "Run the digest
  now". A digest with nothing worth saying (no answer wrong, no session end or level-up) makes no call: it's logged as
  skipped;
- recorded only: role-play (phase B), an arrival (phase C).

The turns wait in a persisted queue (a chat ahead of the rest), one at a time.

**The loop** (`tutor/Loop.kt`): a manual tool loop over a provider (`tutor/Provider.kt` `TutorProvider`: Anthropic's
`AnthropicProvider` over `AnthropicTransport`, OkHttp, okhttp-sse and kotlinx.serialization against `POST /v1/messages`,
streamed, no SDK; OpenAI's below). A thread keeps its provider's own messages; the loop gets back one normalized step
(text, tool calls, usage, the stop reason) and the same error classes from every provider
([docs/plans/05-built/providers.md](../docs/plans/05-built/providers.md)). Each step is saved (the assistant's message,
then the tools' results), so a turn Android stopped resumes where it was. At most 8 calls a turn; parallel tool calls
answer in one message; `tool_choice: auto`; adaptive thinking with the effort set explicitly (below); thinking blocks go
back unchanged, empty ones too; the beta `thinking-binding-controls-2026-08-01` is sent and a response's
`input_transformations` logged per call. Errors by the plan's table: a 401 pauses (nothing lost); the low credit balance
and the spend limits (the workspace's 400, the tier's 429 `enforced_spend_limit_reached`) pause routine work and fail a
chat with the reason; a 429 waits for its `retry-after` (a long one sends the turn back to the queue), 529/5xx/timeouts
back off; `max_tokens` is retried once with twice the room; a `refusal` is noted, never retried.

**A turn without `reply`** (`tutor/Loop.kt` `ReplyFallback`): a turn that ends (`end_turn`) with text and never called
`reply` (Haiku 5.5 at `low` did that in Jan's shadow on 2026-10-16) takes that last text as its reply, to the event's
conversation (a digest's: "main"). The turn is marked `replyFromText`, the panel shows "✍️ as text, no reply tool" by the
reply, and the day counts them (`repliesFromText`, and by model × effort), so the comparison sees how often a model at a
level skips `reply`. A closing (it writes the notebook) never does.

**The model and the effort per task** (Jan, 2026-10-16: "we recommend, the user sets"): the preset (Economy, Balanced,
Best) sets both, and each task's model and effort can be set in the panel's Settings ("chat: claude-sonnet-5-5 ⭐",
"chat effort: medium ⭐"; ✎ when set there). The effort's picker offers only the levels the model takes (all five on the
5.5 models; none on Haiku 4.5), `xhigh` and `max` behind "more… (xhigh, max)", our recommendation marked "⭐ priporočeno ·
recommended". Kept with the settings (`ShadowSettings.models`, `.efforts`). Effort is how much the model thinks and
writes: every output token, thinking included, is billed as output.

| Task | Recommended (Balanced) | Economy | Best |
|---|---|---|---|
| routine (the digest) | Haiku 5.5 · high | Haiku 5.5 · medium | Opus 5.5 · medium |
| grading (answer, session end) | Haiku 5.5 · high (to measure against Sonnet · medium) | Haiku 5.5 · medium | Opus 5.5 · medium |
| chat (and the notebook) | Sonnet 5.5 · medium | Haiku 5.5 · medium | Opus 5.5 · medium |
| role-play (phase B) | Sonnet 5.5 · low | Haiku 5.5 · low | Opus 5.5 · medium |
| content (phase C) | Opus 5.5 · medium | Sonnet 5.5 · medium | Opus 5.5 · high |

Why: Haiku 5.5 at `low` skipped `reply` in the shadow, and Anthropic recommends `high` on Haiku 5.5 for strict
instruction following (~$0.001–0.002 more a reaction); content is what the learner drills again and again, so its
Slovene matters most. Routine and grading get 16,000 tokens of room (thinking counts toward `max_tokens`), content
32,000; `xhigh` at least 32,000, `max` 64,000. The ids come from the key's `GET /v1/models`: the plan's when listed, else
the family's newest, else the family above's; never a legacy model (Haiku 4.5) by default.

**The effort within the day's thread** (`tutor/Effort.kt`): the thread's top-level effort is fixed for its life (the
chat's when it began; a restart keeps it): changing it between requests would clear its cache. A turn at another level
on the thread (a grading question on the chat's model, the chat's effort changed in the panel) sets its own with an
effort-only system message, which keeps the cache (the effort docs, "Change effort mid-conversation"; the 5.5 models and
Opus 5): `{"role": "system", "content": [], "output_config": {"effort": "high"}}`, placed after the last answer and
before the turn's user message (it applies from that turn on and holds until the next one changes it; after the user
message when that holds tool_results, never between a tool_use and its results). The request then carries the beta
`mid-conversation-output-config-2026-07-01` beside the thinking-binding one; a request without such a message sends no
new beta. Only the unanswered end of the thread ever changes. On a model without per-message effort (Sonnet 5, the Sonnet
family's fallback) such a turn runs at the thread's level. A stateless call ("Also run on", another model's turn) has
its level as its top-level effort.

**The prefix** (`tutor/Prompt.kt`): the tools in phase A's fixed order, then the core prompt
(`companion/tutor/core-prompt.md`: who the tutor is, the rules, the event kinds in brief), its end a 1-hour cache
breakpoint; automatic caching (5 minutes) on the last block. Nothing about the learner, nothing time-dependent.

**The tools, from one spec.** `companion/bridge/src/cli/tutor-export.ts` builds the bridge (`buildBridge`: nothing
started) in an environment of its own and writes `companion/tutor/tools/<name>.json` (every MCP tool: name,
description, `input_schema`) and the guide, split into topics (`companion/tutor/guide/<id>.md` and `index.json`: the
channel guide's paragraphs per feature, exactly as `channel_guide` gives them, and the skills the plan names: feedback,
fix, progress, analysis, session-end, studio), with `tools/guide.json` (`guide(topic)`). `--check` compares them with
the bridge (the smoke section `tutor` runs it): regenerate with `cd companion/bridge && bun src/cli/tutor-export.ts`
after changing a tool, a feature's paragraph or the curated content the guide names. The app's new tools are
hand-written in `companion/tutor/app-tools/`. The Gradle task `tutorFiles` bundles `companion/tutor` as resources
under `tutor/`. Phase A's 17 tools in shadow mode (`tutor/ShadowTools.kt`):
- `reply`: into the panel, never the chat, never a notification;
- reads, through the paired bridge's GET routes only (`tutor/Reads.kt` `ShadowBridge`, the app's bearer token, its
  client refusing anything else, `GetOnly`): `learner_state` (`/state`, summed up), `get_village` (`/game`),
  `list_packs` (`/packs`), `list_grammar` and `get_grammar` (`/grammar`), `dictionary_lookup` (`/lookup`, the node's
  dictionary as marked untrusted data; fran.si comes later), `card` find, show and overlaps (`/state`'s deck);
  `recent_answers` from the reviews the shadow saw;
- writes, dry runs (validated against their schema, recorded with their input, never sent): `publish_gloss`,
  `publish_sentence_note`, `set_rhythm`, `record_session`, `report_problem`, `card` edit, merge, split, regrade and
  forgive. The model reads what the live tool answers on success, made from the input (`tutor/DryRuns.kt`): the
  bridge's "kept: …" of a gloss or a sentence note, its rhythm settings, update-db.py's summary, card.py's JSON, the
  report flow's "posted: …". Nothing it reads says shadow or dry run (nor do the reads), so it acts as it would live;
  the record keeps `dryRun`, which the panel and the export show. A refusal the live tool would give where the result
  depends on it (a card write without its card, a report without a session that adds no words) comes back as an error;
- a `report_problem` fix about a word, a form or an answer key (its source has an answer or a word, or its text says
  so) fails until a `dictionary_lookup` ran earlier in the turn: "look it up first";
- its own: `guide`, `notebook_note`, `notebook_write`.

**The notebook and the day's thread** (`files/shadow-tutor`): one thread per local day from 04:00, on the chat's model,
only ever appended to; its first user message the notebook and yesterday in brief. A turn on another model (Balanced:
the routine Haiku turns) is a stateless call (the prefix, the notebook, today's lines, the event): its reply becomes a
line in the thread's next message. Past the thread's limit (a setting; the preset's: 20k, 30k, 60k tokens) a closing
call has the tutor rewrite the notebook (`notebook_write`), and a fresh thread starts with the notebook, the day so far
and the last exchanges as plain text. The first event of a new day closes yesterday's thread the same way. An app update
that changes the tools or the prompt restarts the thread as text (no closing call: its history can't be sent under
another prefix). The notebook's older versions are kept; the panel can edit it.

**What the learner was shown** (`tutor/Events.kt` `TurnText.shown`): the learner's next message answers the real tutor's
reply, which the shadow never gave, so each real reply goes into the thread's next user message, before the event:
"What the learner was shown in reply (another tutor answered this time). Their next messages answer this: take it as
what they saw, and don't mention it or the other tutor to them.", each reply in a `<shown>` block, cut at 1,500
characters, once (`ThreadState.shownUntil`). A stateless turn gets the day's last three beside "Today so far". A turn
never gets the real answer to its own events, nor a role-play's lines.

**The matrix** (`tutor/Matrix.kt`, the panel's Matrix tab; [05-phase-0.md, "The matrix"](../docs/plans/05-phase-0.md#the-matrix-2026-10-18)):
off by default. Lanes of one model at one effort (nine preselected: Haiku 5.5 at medium, high, xhigh and max; Sonnet 5.5
at low, medium and high; Opus 5.5 at low and medium) each handle every event the primary gets, on a day thread of their
own at their effort for every task, with their own queue and worker beside the primary (which runs as before) and a
client of their own. They read the primary's notebook and never write it (their notebook calls are dry runs, as their
writes), restart as text past their family's limit, close their day without a call, and share their reads (one GET for
all lanes). No budget of their own and never the primary's: the switch, a lane's toggle, a model the key doesn't list
(skipped, and said so), or the key refused or out of credit stop them. Their turns and calls carry the lane's id
(`lane`: "claude-sonnet-5-5@low"), in a day log per lane (`files/shadow-tutor/matrix/<lane>/`) and in the export (each
day's `lanes`, the top-level `matrix`; tool calls in brief). Compared afterwards with `companion/bin/shadow-compare`
(the uploads on the node by default; `--out DIR` writes `summary.md` and a file per task; `--lanes openai` the OpenAI
lanes). **OpenAI's lanes** (GPT-6 Luna at medium to max, GPT-6.1 Sol at low to high, GPT-6 Astra at low and medium: the
same places in the grid) join once, when an OpenAI credential is first set (below); their ids carry the provider
(`openai:gpt-6.1-sol@medium`; Anthropic's keep theirs), and an OpenAI credential refused or out pauses only them.

**OpenAI: an API key, or "Sign in with ChatGPT"** (the panel's Settings, "🤖 OpenAI"; `tutor/OpenAI.kt`,
`tutor/ChatGPT.kt`): for the matrix's OpenAI lanes and "Also run on" a GPT-6 model; the primary stays on Claude. The
Responses API (`POST https://api.openai.com/v1/responses`, `stream: true`, `store: false`): the core prompt as
`instructions`, the same tools as function tools (`strict: false`; in one `lani` namespace with the ChatGPT plan), the
effort as `reasoning.effort`, the reasoning items passed back with their encrypted content, a chat's photo as an
`input_image`. **"Continue with ChatGPT"** opens the browser on OpenAI's sign-in (OAuth with PKCE; the app registers
itself at the first sign-in, no client secret), which comes back to a listener on 127.0.0.1 and then to the app
(`lani://tutor/signed-in`); the learner's ChatGPT plan pays (Plus or Pro; its allowance is shared with Codex: "Manage
usage" opens ChatGPT's settings, where a limit for Lani can be set). Its calls cost $0; the panel, the records and
shadow-compare show their API-equivalent price beside it. "Sign out" revokes the sign-in. **An API key** (`sk-…`) is
checked with `GET /v1/models` and kept as the Claude key is (its own Keystore key and DataStore entry); with both, a
chip picks which one the calls use. The key and the sign-in's tokens go only to api.openai.com and auth.openai.com,
never into a log line, the export, a prompt or a tool's result.

**Metering** (`tutor/ShadowStore.kt` `DayStats`): per call the model, its effort (and whether an effort message set it),
the event kind, input, the cache writes (5-minute and 1-hour, from `usage.cache_creation`), cache reads, output (thinking
included: the API doesn't report it apart; `thinkingEstimate` is the output less the response's visible characters at
about four a token, an estimate), the time to the first token and in all, the price (`companion/tutor/prices.json`);
per day the calls, turns, cold starts (gaps over 5 and 60 minutes on a model), the thread's size at each restart, the
notebook's size, the spend by model and by event kind, the guide's topics fetched, the replies taken from text
(`repliesFromText`), and by model × effort (`byModelEffort`, "claude-haiku-5-5 · high": calls, turns, spend, output,
≈ thinking, replies by tool, as text, none). The day's budget (default $3) pauses the shadow until the next day.

**The panel** ("🕵️ Shadow tutor"): Events (each event with the shadow's turns: model, effort, status, a line of numbers
per run (cost, output with ≈ thinking, the first token and the whole time, "reply ✓ tool", "reply ✍️ as text" or none),
reply, tool calls with their inputs and results, dry runs marked; the real tutor's reply below; "Also run on…" a model
at an effort ("claude-haiku-5-5 · low", xhigh and max behind "more…"): its own stateless call, metered, so models and
levels can be compared on the same events; "Queue again" for a failed turn); Numbers (the day's, "By model × effort",
each call's with its effort, "⬆️ Export everything"); Matrix (its switch, the lanes' grid: each family's model × the
efforts it takes, a toggle each; today's spend per lane and in all; each event in Events has "▸ 🧪 Matrix: N lane(s)"
with each lane's run); Notebook (the notebook, the thread, the queue); Settings (on, the key, a debug or qa build's base
URL, the preset and the model and effort per task, the budget, the thread's limit, "React at once"). Older days are a
tap away.

**The export:** one JSON file (`lani.shadow-export/v0`: the settings, the notebook and its versions, the thread, the
queue, and each day's events, turns, replies, tool calls, calls and numbers) through the share sheet (a FileProvider
on the cache's `shared/`). No key and no token: neither is kept with them, and every text is cleaned of the keys, the
ChatGPT sign-in's tokens, the node's token and anything shaped like a secret (`Redact.json`: `sk-ant-…`, OpenAI's
`sk-…`, JSON Web Tokens); a photo and a reasoning item's encrypted content are notes of their size.

**Without a key: the mock.** `bun companion/bridge/test/tutor-mock.ts` serves a canned Messages API on
127.0.0.1:8771 (the emulator: `http://10.0.2.2:8771`): `GET /v1/models` and streamed `POST /v1/messages` in the real
wire format (thinking, text, parallel tool calls with their input in pieces, usage that looks like caching), markers in
a chat for the errors (`[mock:429]`, `[mock:529]`, `[mock:500]`, `[mock:refusal]`, `[mock:max_tokens]`,
`[mock:stream-error]`, `[mock:credit]`) and `[mock:no-reply]` (once, the answer as text without `reply`); the effort as
the API takes it (the levels; effort-only system messages only with the per-message beta, else the API's 400
`messages.N.output_config: Extra inputs are not permitted`; none on Haiku 4.5), the level in effect logged per request,
a thinking block weighing by it, and Haiku 5.5 at `low` answering as text without `reply`, as Jan's shadow saw;
`GET /mock/requests` (the last 50, never the key) and `POST /mock/reset`;
`bun companion/bridge/test/tutor-mock-check.ts` checks it. A debug or qa build's panel takes its base URL ("The mock
(10.0.2.2:8771)"); QA's hook `shadow:http://10.0.2.2:8771` sets it and opens the panel (`app/QaHooks.kt`); a release
build always calls the API.

**Developer data on the node.** While the developer setting is on and the app is paired, the app sends its developer
data to the paired node by itself (`app/DevUpload.kt`), and the developer reads it there as files: nothing to export or
share. Off, and for every other learner, nothing is read and nothing is sent; the developer section says what goes
where while it's on. The tutor knows nothing of it (no tool, no paragraph of the channel guide).
- **What:** the shadow's export (the share sheet's `lani.shadow-export/v0`; past 5 MB the oldest days are left out,
  named in `days_left_out`) and the app's own log: this process's logcat lines (an app reads its own without a
  permission, on Android 16 too), its warnings and errors and the info lines of its own tags (`AppLog.TAGS`: the
  shadow's calls, turns and transport errors, Clips' "no clip of a line", the speaker's route lines in QA builds …),
  at most the last 2,000 of the last 24 hours, after those sent before. Both cleaned of secrets (`tutor/KeyVault.kt`
  `Redact.all`, `Redact.json`): the API key and the device token by their values, `sk-ant-…`, `Bearer …`, the value
  after a secret's name (`token=…`, `x-api-key: …`, `Authorization: …`, `"api_key": "…"`), a URL's `key`, `sig` or
  `code`; counts of tokens stay.
- **When:** after the shadow's day closed (its notebook rewritten), when the app goes to the background (at most every
  30 minutes) and by "📤 Pošlji vozlišču · Send to the node" in the panel's Numbers, which shows the last upload.
- **How:** over the app's own client with its device token (`Bridge.devUpload`; not the shadow's GET-only client) to
  `POST /dev/shadow` (JSON) and `POST /dev/logs` (text): the app's token only, 5 MB at most (413 beyond).
- **Where:** `<data>/app/dev/` (`bridge/src/features/dev.ts`): `shadow-<YYYY-MM-DD>.json` (that day's latest export)
  and `app-log-<YYYY-MM-DD>.txt` (that day's uploads appended, each after a `=== …` line: when, the app, the phone),
  mode 0600 in a 0700 directory, kept 30 days (the node's day). The bridge cleans them once more (the request's own
  token, the same patterns).
- **Never committed:** the data repository's `.gitignore` keeps `/app/dev/` out (`lani_data_repo.py`: lani-setup writes
  the line, each commit adds it back when it's missing and untracks anything there, staged before or not), and the bridge
  adds the line at its start to a `.gitignore` without it.
- **The raw usage, for the cache question:** for its thread's first call (the day's, a restarted one, a stateless
  turn's) and every 20th call of the day, the shadow's calls log keeps `usage` as the API sent it in `message_start` and
  in `message_delta` (`usageRaw`); every call says how its cache writes came (`cacheReport`: `breakdown` by lifetime,
  or only the `total`, which `tutor/Wire.kt` files as 5-minute writes), and the day's numbers count both
  (`cacheBreakdown`, `cacheTotalOnly`; the panel's Numbers too).

## Grammar book

The village's scroll has a collection "📖 Slovnica · Grammar": one page per rule of the language
learned, filled in as the learner meets the rules. A "why" after an answer is short and gone once read; the page keeps
the rule, with a table, examples, the learner's own examples and mistakes, practice and the tutor a tap away. What the
app does with it is in [GAME.md](GAME.md#the-grammar-book).

**The format** (`lani.grammar/v0`, `bridge/src/grammar.ts`; for the tutor
`.claude/skills/lani-studio/reference/grammar-spec.md`): an `id` (`kje-mestnik-orodnik`), the `language` learned, a
`level` and an `emoji`, the `title` in that language and the bases, the `rule` in each base language (a few short
paragraphs, Markdown, the learned language's terms in brackets: "the locative (*mestnik*)"), an optional `table` (cells in
the learned language as they are, or a text per base), `examples` (the sentence and its translation into each base, an
optional note), the `modules` that practise it, `tags` (matched against mistakes-db pattern ids and review cards'
categories and ids, a tag's words in a row: `accusative` matches `accusative_feminine_a_to_o`, `iz` the card
`vocab_iz_genitive_fem`), related pages (`see`), `machine_written` and `review`. English is always there: it is every
base's fallback. The tutor's `more` is a clearer or longer explanation, shown as the tutor's.

**Curated pages** are `companion/grammar/<language>/<id>.json`. Slovene has 44, each explained in English, German and
Italian (machine-written, not reviewed by a native speaker): `kje-mestnik-orodnik` (Kje? v, na, ob, pri + locative, v or
na; pod, za … and blizu point on to their A2 pages), `kam-tozilnik` (Kam? the accusative, k + dative, domov),
`rodilnik-predlogi` (iz, z/s, od, do, blizu, brez), `rodilnik-nikalnica` (nimam časa; ne berem knjigo, ampak revijo),
`tozilnik` (the object), `dvojina` (the dual), `stevila-samostalniki` (ena ovca, dve ovci, tri ovce, pet ovc), `biti`,
`imeti-iti`, `glagoli-sedanjik` (the present's endings), `ti-vi`, `pridevniki-ujemanje` (dober dan, dobra kava, dobro
jutro), `naslonke` (the clitics in second place, bom last with je: "Jutri ti ga bom dal"; a clitic may start a sentence:
"Se vidimo!"), `pretekli-cas` (biti + the l-form), `vprasalnice` (kje, kam, od kod …, čigav; a or ali for yes or no) and
`spol-samostalnikov` (noun gender).

The cases: `skloni` (the six cases, each with its questions, a job, prepositions and when it is learnt; "ask the
question" finds the case; the three numbers; no vocative), a page per case, `imenovalnik` (the subject, calling
someone), `rodilnik` (of, none, how much, from: it points to the genitive's slices), `dajalnik` (komu? dati, pomagati,
mi je mrzlo, všeč mi je, k/h), `mestnik` (o, po, pri; v Trnovem) and `orodnik` (z/s and when each; pod, nad, pred, za,
med for Kje?, A2), with the accusative's `tozilnik`, and the declension tables `sklanjatev-moski` (stol, brat),
`sklanjatev-zenski` (knjiga, vas) and `sklanjatev-srednji` (okno, jajce), six cases × singular, dual and plural (the
format's one table a page). The rules the dialogs test: `rodilnik-kolicina` (malo sena, kos kruha), `primernik` (boljši,
večji, slajši; kot; vedno hitreje), `prihodnjik` (bom + the l-form), `pogojnik` (rad bi, kupil bi), `modalni-glagoli`
(moram + the infinitive, lahko + the present, ne morem), `velelnik` (pazi, dajte, pridita), `povratni-glagoli` (bati
se + genitive, se zbudim), `osebni-zaimki` (mi or meni, ga or njega; pomagam vam, vidim vas; jih for the plural), `svoj`
(Strašilu dam svoj klobuk), `prislovi` (dobro, glasno; noter or notri), `kdaj-cas` (ob petih, v sredo, pozimi, čez eno
uro) and `zanikanje` (ne vidim nič, nikoli nisem). And what docs/grammar-syllabus.md found missing: `datumi` (prvi,
drugi; petega maja, katerega smo danes), `mnozina` (konji, ovce, okna; ljudje, otroci), `vezniki` (in, ampak, ker, ko,
če; kadar, čeprav, zato), `namenilnik` (grem spat, pridi jest), `glagolski-vid` (delati or narediti, pisati or
napisati), and at B1 `oziralni-ki` (človek, ki ga poznam; vas, v kateri živim) and `odvisni-govor` (rekel je, da pride;
naj pridem). Their levels follow when each is learnt (the cases' in `skloni`; the rest as docs/grammar-syllabus.md has
them).

Italian, German and English have 16 each, A1 and A2, explained in the bases their learners have (Italian in Slovene,
English and German; German in Slovene, English and Italian; English in Slovene, Italian and German, and English), with
examples from their village (machine-written, not reviewed by a native speaker):

- **Italian** (`grammar/it/`, friuli): `articoli-genere` (il, lo, l', la; un, uno, una, un'), `plurale`, `essere-avere`
  (ho freddo, ho nove anni), `presente-regolare` (-are, -ere, -ire), `verbi-irregolari` (andare, fare, stare, venire),
  `passato-prossimo` (avere or essere), `tu-lei`, `preposizioni-articolate` (al, nel, dal, sui …), `dove-preposizioni`
  (a, in, da; vicino allo stagno, davanti alla tenda: the tent's page), `partitivo`, `possessivi` (il mio, mia madre),
  `ce-ci-sono`, `piacere`, `aggettivi-accordo`, `numeri` and `domande`.
- **German** (`grammar/de/`, kaernten): `artikel-genus` (der, die, das), `faelle` (the four cases), `akkusativ` (and
  durch, für …), `dativ` (and mit, von …; mir ist kalt), `wechselpraepositionen` (Wo? + dative, Wohin? + accusative:
  the tent's page), `sein-haben`, `praesens`, `wortstellung` (the verb in second place), `nebensatz` (the verb at the
  end), `du-sie`, `trennbare-verben`, `perfekt`, `plural`, `kein-nicht`, `modalverben` and `fragen`.
- **English** (`grammar/en/`, lakeland): `articles`, `plurals` (one sheep, two sheep), `to-be` (I'm ten), `to-have`,
  `present-simple`, `present-continuous`, `questions-do`, `past-simple`, `present-perfect`, `future` (going to, will),
  `can`, `there-is-are`, `some-any`, `prepositions-place` (by the pond, next to the jetty: the tent's page),
  `possessive-s` and `word-order`.

**What names a page:** an exercise's `"grammar": "<id>"` (any type; every exercise of the six curated modules has one),
a culture pack's challenge question (`quests.json` `tent_move.questions[].grammar`: Luka's tent challenge names
`kje-mestnik-orodnik` for ob ribniku, `orodnik` for pod veliko smreko and pred šotorom, and `rodilnik-predlogi` for
blizu pomola; and `surprises.json`: a letter's question, `letter.letters[].questions[].grammar`, and a way to show the
pilgrim, `pilgrim.ways[].grammar`), a request (`quests.json` `requests[].grammar`: its intro lists the page, and playing
it meets the page), a scene's wrong choice or learner's turn (`dialogs[].lines[].choices[].grammar`,
`dialogs[].lines[].grammar`: its why leads to the page, see
[SCENES.md](SCENES.md#a-why-that-is-a-rule-of-the-grammar-book)), a module in a page's `modules`, and a grammar review
card (not vocabulary) that a page's tags match. Each names a page of its own language: the bridge's culture check
(`validateCulture`, given the book's ids) and the smoke check it, and `publish_scene` and `publish_module` name a page
the book lacks. The page is the unit of the learner's mastery, which decides whether a turn on its rule is chosen, typed
or said ([GAME.md](GAME.md#mastery-and-adaptive-turns)). The app bundles the curated pages (`app/build.gradle.kts`,
resources `grammar/<language>/`) and takes the bridge's list over them; the book it shows is of the learner's own
village's language (the pair's target).

**The tutor's pages.** `publish_grammar {page, note?}` validates a page and publishes it (`<data>/app/grammar/<id>.json`,
the `language` taken from the village when missing, a page of another language refused), `grammar_published` tells the
app, and the examples are voiced like a module's lines. Under a curated page's id it extends that page: the curated
title, rule, table, level and review stay, the tutor's examples that aren't the curated page's come after them marked
`"by": "tutor"`, the tutor's `more` is kept, and `modules`, `tags` and `see` add to the curated ones; publishing again
replaces the addition. Any other id is the tutor's own page (`source: "tutor"`). The answer names links that lead
nowhere (a `see` page or a module there is not), and `publish_module` names exercises whose page the book lacks.
`list_grammar` shows the pages and what the learner met of them (the village state's `grammar`, "sl/<id>": the day a
page unlocked, the answers on it); `get_grammar` one page, to extend. The channel guide says when: whenever the tutor
explains a rule in chat, and for the page's "Vprašaj" (`data.about_grammar`) and "Vadi" (`data.grammar_drill`) chats.

**Other target languages.** A page is of one language and a bridge serves its village's (`companion/grammar/<language>/`,
then the tutor's): the friuli bridge the Italian book, kaernten the German one, lakeland the English one, and the app
shows the book of the learner's own village's language (the second learner's "📖 Grammatica · Slovnica", explained in Slovene).
What names their pages, as Primorska's: the tent's questions (friuli: `dove-preposizioni`, and
`preposizioni-articolate` for accanto al pontile; kaernten: `wechselpraepositionen`, and `dativ` for beim Steg;
lakeland: `prepositions-place`), the pilgrim's ways (Vada or Vai: `tu-lei`; Gehen Sie or Geh: `du-sie`; il ponte, die
Brücke: the articles; two kilometres, two miles: the plural), a letter's question (Lily's "I'm ten": `to-be`), the
requests about a rule (the smiths' orders, the teachers' lessons, the storytellers' past, the innkeepers' guests), and
the scenes' wrong choices whose why is a rule (ho freddo, mir ist kalt, I'm cold; la luna, der Mond; there are a lot of
stars). The app's labels for the chapter are in all four string tables.

## Cheat sheet

"📋 Plonk listek · Cheat sheet" (*plonk listek* is the school slang for the slip of paper with the basics on it):
compact reference cards of the basics, in the style of a good chat answer: a title, one bold rule line, a small table,
two or three examples, a 💡 tip at most; one phone screen. It opens from Home (the tile "📋 Plonk listek", "✨ 3 nove ·
3 new" while some are new), from the chat's header (📋) and from any exercise (📋 in its top bar, by 💬). Opening it is
help, not a hint: during an exercise a right answer stays right and pays in full, but it is right with help (its review
quality at most 4), and the tutor hears what was looked up (see "Looked up during an exercise" below). The logic is `game/CheatSheet.kt`, the
controller `app/CheatSheetController.kt`, the sheet `ui/CheatSheetSheet.kt`; the rules in
[GAME.md](GAME.md#the-cheat-sheet).

**Where the cards come from.**
- **The grammar book.** Every page gives a card: its `card` when it has one (`lani.grammar/v0`, a hand-written
  `{title?, rule, table?, examples?, tip?, start?}`: the rule line per base, a compact table, two or three examples, the
  page's title, table and first two examples where it leaves them out), else the page's table, its rule's first sentence
  (cut at a word past 240 characters, never inside bold) and its first two examples. The curated deck of the basics is
  such cards (machine-written, not reviewed by a native speaker): biti, imeti and iti, the present's endings, the past,
  the future, the six cases at a glance, noun gender by ending, the plural, adjective agreement, the question words and
  ti or vi.
- **Curated cards no page fits** (`companion/cards/<language>/<id>.json`, `lani.card/v0`, `bridge/src/cards.ts`): the
  same face, a title per language, and the pages they belong to (`grammar`): the pronouns 1 · 2 · 3+ (jaz, midva, mi;
  me, ve, one; vi polite), kje or kam (v, na + locative or accusative), the numbers 1–20 and the tens, the possessives
  (moj … njihov, svoj). The app bundles them (`app/build.gradle.kts`, resources `cards/<language>/`) and takes the
  bridge's list (`GET /cards`) over them.
- **The tutor.** `publish_card {title, markdown, grammar?, level?, emoji?, id?, note?}` puts a card of the tutor's on
  the sheet (`<data>/app/cards/<id>.json`, the id from the title when missing, a curated card's id refused, the same id
  replaced): its Markdown as the tutor writes in chat, the grammar pages it is about (pages the book lacks refused), the
  learner's level by default; `card_published` tells the app (a notice, the card new on the sheet). `list_cards` lists
  the pages' cards (hand-written ones marked), the curated and the tutor's, what the learner pinned and the cards they
  open most; `remove_card` takes one of the tutor's back (`card_removed`). The channel guide says when: a basic the tutor
  just explained with a table or a list worth keeping.
- **Pinned.** A long press on a tutor message with a table or a list (two rows or two items), beside "🔖 Zaznamek ·
  Bookmark", offers "📌 Na plonk listek · Pin to cheat sheet", which keeps it as a card: its title its heading, else its first line when short, else its first bold words (✏️
  renamed on the card), its pages guessed from the book (the forms it stresses in bold against each page's tables: the
  best page with three or more, and a second nearly as good), the learner's level. Pinning counts as opening it once
  (the learner chose it: not new, among the most opened for a while). Pinned, the long press offers "📋 Na plonk listku · On
  the cheat sheet" and opens the sheet at that card, first and open. At most 50; ✕ takes one off.

The card's Markdown is the app's renderer (`ui/Markdown.kt`); on a card a tap on a Slovene word in bold or italics looks
it up (the word's card, `ui/words/WordSheet.kt`), as do the words of a table's cells and the examples (🔊 beside each).
TalkBack reads a card's head as a heading that expands and collapses, and a table row by row (the head, then each row:
its first cell and each other with its column's name, "ti, imeti: imaš"; the chat's Markdown tables read row by row too).

**Unlocked as you go.** A page's card unlocks the day the learner meets its page (the grammar book's "met",
`GameState.grammar`: an exercise or a module naming it, a page shown from a why, a school unit's introduction), a curated
card with the first of its pages, an A1 basic marked `start` from the start (the pronouns, biti, the question words, the
numbers), a pinned or the tutor's at once. What unlocks a page is one call: `vm.cheat.met(pageIds)` (the same as
`vm.grammar.meet(pageIds, announce = true)`: the book's pages and their cards; the school's units call it with their
pages). Locked cards aren't listed (no spoilers); the sheet ends with "🔒 Še 23 kartic · 23 more cards as you go". A card
never opened is "✨ Novo · New" (a pinned one isn't: pinning it opened it).

**In order of relevance** when it opens: (1) from an exercise, the cards of its pages (the exercise's own `grammar`, or
its review card's page, first; then the run's other exercises' and the module's pages: those that list it), the page's
own card before a curated one, a pinned one, the tutor's, the first of them open at once; (2) the most opened lately (an
open counts one, halving every 14 days: `GameState.sheet.opens`), most first; (3) the new ones; (4) the rest in the
book's order (a curated card beside its first page). A card whose pages the learner all has secure or mastered (the
grammar book's masteries) drifts to the bottom, unless it's the exercise's. The order stays while the sheet is open
(opening a card doesn't move it). A filter by level (Vse · All, A1, A2 …) when there are several.

**An open exercise.** A card's table may hold the very form the exercise asks for (the exercise on imeti, the card with
imam, imaš …): that's fine, it is the rule's table as a school's cheat sheet would have it, the same the grammar book
shows from the why. The sheet shows rules, never the exercise's answer: not its sentence, not its gap filled; finding the
form in the table and putting it in the gap is the practice. It doesn't count as a full hint (a hint fills in letters of
the answer itself): the learner looks it up themselves.

**Looked up during an exercise.** The sheet opened between an exercise appearing (in a timed run, once the clock runs)
and its answer counts on that exercise: in the exercise player, the reviews, a challenge, a building's words run, the
grammar book's practice, a pack, a visit's request and the school's sheets (`ExerciseRun`, which watches each exercise:
`CheatSheetController.watch`, pure in `game/CheatSheet.kt` `SheetLookups`, `SheetWatch`), by its 📋 or the chat's over
it. Opened before an exercise appears (from Home, the chat) or over its feedback, it counts for nothing.
- **The grade.** A right answer stays right: it pays in full and the feedback says "Pravilno". Its review quality is
  capped at 4, "right with effort" instead of 5, so SM-2 brings the card back a little sooner (`Hints.quality`,
  `Hints.LOOKED_UP_QUALITY`: the reviews, the grammar book's card practice, a building's words run; a challenge's and a
  pack's right answers are 4 already). A 💡 hint as well: the hint's rule (one less each, never below 3; it pays as
  almost). A wrong or almost answer is graded as ever. A rule's run in the grammar book counts as ever (the player's
  hints don't touch it either).
- **A worksheet** (many items, checked at once): the items answered after the sheet first opened over it count, until it
  is checked: each item's answer at the check against what it held when the sheet opened (one filled before and left
  as it was doesn't; `ItemMark.lookedUp`).
- **What's recorded.** Each answered exercise keeps the cards opened during it (their keys, in the order opened; none
  when the sheet was opened and no card) and their grammar pages (`Outcome.lookedUp`, `LookUp`). It goes with the answer:
  `looked_up: {cards, pages}` in `POST /reviews` `answers` (an older bridge drops it), in a `session_end`'s `results`
  (a module, the grammar book's practice, the school's sheets: there per item `looked_up: true` for the items answered
  after it) and in "Ask" (`about_exercise`). The bridge says it in one line on the tutor's `review_done` ("📋 Looked up
  on the cheat sheet: biti ×3, tozilnik ×2 (during 4 answers).") and `session_end` ("… (during 1 exercise, 6 worksheet
  items answered after it opened).": `bridge/src/answers.ts` `lookedUpNote`), the recent-answers log keeps it
  (`recent_answers {looked_up: true}`), and the channel guide says what to do with it: give a right answer looked up at
  most quality 4 when persisting a `session_end`; a topic looked up often is work potential (its school unit, homework
  with `publish_worksheet`, a module, an explanation in chat), never a reason to scold.
- **Counted by page.** Each answer adds one lookup to each of its pages for the day (`GameState.sheet.lookups`,
  "<language>/<page id>" → day → count, the last 14 days kept: `CheatSheet.lookedUp`); the tutor's `get_village`,
  `get_course` (with the units that teach each) and `list_cards` show the most looked up of the last 14 days
  (`looked_up: [{page, title, n, last}]`, `bridge/src/cards.ts` `sheetLookups`).

**Kept** in the village state (`GameState.sheet`, synced with the node like the rest of it; an older save has none, an
older bridge keeps it as it is): the pinned cards (`pinned`: id, language, title, Markdown, pages, level, the day, the
chat message), the open counts (`opens`, "<language>/<card key>": the count and its day, at most 300) and the lookups
during exercises (`lookups`, "<language>/<page id>": each day's count, the last 14 days; an older app drops them when it
writes the village). The card keys: `page:<page id>`, `deck:<card id>`, `tutor:<card id>`, `pin:<id>`.

Tests: `CheatSheetTest` (a page's card, its own card, the rule line; unlocking; the order; the decay; pinning, the title
and the pages guessed; saved and loaded, an old save; the events; what counts as looked up during an exercise, a
worksheet's items after the sheet opened, the lookups by page and day), `HintsTest` (the quality capped at 4, a hint's
rule with both), `AskAboutTest` (`looked_up` with the answer, the pay and the quality), `BuildingWordsTest` (a building's
words run looked up), `CardsTest` (the formats, the bundled deck in every base, the merge); the smoke's `cards` section
(the curated cards and the pages' own in every base, the formats, `GET /cards`, `publish_card`, `list_cards`,
`remove_card`, the guide, `list_cards`' lookups), `reviews` (`looked_up` with a review's answers, logged, a malformed
one left out, the note's 📋 line, `recent_answers {looked_up}`, an app from before), `chat` (a `session_end`'s 📋 line,
a worksheet's items, none without, the guide) and `course` (the last 14 days' lookups, `get_village` and `get_course`
showing the most looked up, none without).

## The school's course

The school teaches the language level by level (A1, A2, B1, B2): each level an ordered list of units, each unit a guided
introduction with a quick question after each step, two to four worksheets and a test. The format, the school's own
exercise types (`gaptext`, `table`, `transform`, `sort`) and how to write a unit are in [COURSE.md](COURSE.md); how it
fits the game (the class from the hamlet on, the school, the gates) in [GAME.md](GAME.md#the-school).

**Served** (`bridge/src/course.ts`, `GET /course`): the outline `companion/course/<language>/course.json` and its units
`companion/course/<language>/units/<id>.json` (`LANI_COURSE_DIR` for another root), read per request like the grammar
book: a file that doesn't validate is skipped and logged once, a unit file of another level than the outline's too. A
unit is served `planned` when it has no file; a unit file the outline lacks comes after its level's units. Each unit
carries the tutor's homework for it (`<data>/app/course/<unit>/<id>.json`), the newest first. The app bundles the course
too, for offline.

**The tutor's tools.** `publish_worksheet {worksheet, note?}` validates homework (`lani.worksheet/v0`, the `language`
taken from the village when missing) and publishes it into its unit: refused for a unit the course lacks or that is
planned, an id of the unit's own worksheets, or another language; publishing an id again replaces it.
`worksheet_published` tells the app, the intro's examples and the module types' lines are voiced, and the answer names a
`grammar` page the book lacks. `remove_worksheet {unit, id}` takes it back (`worksheet_removed`). `get_course` shows the
course and where the learner is (the village state's `course`, [COURSE.md](COURSE.md#the-learners-progress)): the
day the class began, the school's level, each level open or not and its units done, each unit's intro, sheets (the best try of
each), test (best try, tries) and homework, and the next unit; `get_village` has a short `course` block (the day the class began,
the units done and in progress). A sheet played reaches the tutor as a `session_end` whose `data.course` is
`{language, unit, level, sheet: <worksheet id> | "test" | <homework id>, kind: worksheet | test | homework, right, almost,
of, passed?, unit_done?, tested_out?}`: persisted as any session end; a unit done is congratulated, and homework on what
went wrong may follow. `test/smoke/course.ts` validates every course file of the repository (its outline, its units,
the grammar pages they name).

**Printed.** Each unit's sheet and each level's booklet print from the app: "🖨️ Natisni list · Print sheet" on the unit
and its row, "🖨️ Vsi listi · All sheets" on the level. The bridge serves the page `companion/bin/school-prints` makes
(`GET /course/sheet/<unit>`, `GET /course/booklet/<level>`: one source, `companion/prints/build.ts`), the app fits it in
a WebView off screen and opens Android's print dialog (a printer, "Save as PDF", sharing). See
[COURSE.md](COURSE.md#printed-sheets), "Printed sheets" and "In the app".

## Napredek · Progress

"📊 Napredek · Progress" (`ui/progress/ProgressScreen.kt`; its numbers in `data/Stats.kt`, from GET /state, and
`ui/progress/ProgressLogic.kt`, from the village and the readiness) opens from Home's last line and its pills (the streak,
XP, achievements) and from the village HUD's "Naselje · Settlement" ("📊 Napredek ›"). From top to bottom:

- **The words.** The words learned (the count the village's ages wait for, [GAME.md](GAME.md#ages)), their trend over
  eight weeks, and a bar to the words of the next level (`Stats.wordsGoal`: 300 for A2, 1000 for B1, 2000, 3500, 5000).
  The words don't raise the level, the treasure map does, so once the level's own goal is passed the bar moves on to the
  next one, "312 / 1000 besed za B1 · words for B1", with the goal passed marked under it ("✅ 300 besed za A2 · 300
  words for A2"); past C2's 5000 (at C2 its own 8000) it stays full. The forecast follows the bar's goal: at the recent
  pace, and with a word pack a day ("1000 besed okoli …" once past the level's own).
- **"🧭 Do A2 · To A2".** How the level rises: "Raven zraste z zemljevidom zaklada, ki ti ga Stari Janez da, ko so
  izpolnjeni trije znaki" (the storyteller: `TreasureController.tellerName`; [GAME.md](GAME.md#zemljevid-zaklada--the-treasure-map),
  `LevelReadiness`). A line each, ✅ or ⏳ with its numbers: 📚 the words (the level's goal: a milestone, not one of the
  signs; a tap opens "📚 Moje besede · My words", [below](#moje-besede--my-words)), 📖 the rules of the level secure (of those needed: 70 % of the level's tested pages; the first eight still to
  secure as chips below it, each its title up to the colon ("✌️ Dvojina"), "+13" for the rest, a tap opens the page), 🔁 the reviews of the last 14 days (right, of at least 30; 80 % wanted), 📅 the
  days of practice at the level (10, over at least 14 days at it). Once the signs are met or the map is offered, "🗺️
  Zemljevid zaklada te čaka · The treasure map is waiting" with the storyteller's offer is a row to the map; the hunt on,
  its stations passed; before, "🗺️ Zemljevid zaklada ›" opens the map's signs with "🗺️ Sem pripravljen · I'm ready" (the
  only way to them before the offer). A treasure just found says so, and whether the tutor has the new level yet.
  TalkBack reads each line as one, in the base language ("Rules of A1 secure: 5 / 19. Needed: 19 of the level's 26 pages
  (70 %). not yet").
- **"📝 Slovnica · Grammar".** The school at the learner's level: its units done of all ("1 / 18", a bar; the row opens
  the school), those begun, and the one suggested next (★ "Naslednja · Next", or ▶ "V teku · In progress" when begun; a
  tap opens its page); before the class begins, that it begins with the hamlet. The grammar book's pages of the level: those met of all, one bar of the secure and the met, and the
  three weakest met pages with their stars, those not practised yet last (a tap opens the page over the screen). The
  counting cards while counting is on ([Counting cards](#counting-cards)): how many, how many secure, how many due today.
  The bars have their TalkBack labels ("A1 units done: 1 of 18", "A1 pages in the book: 28; met: 12; secure: 5").
- Then the streak calendar, the accuracy, the exercises per week, the skills, the recurring mistakes (a tap asks the
  tutor), the reviews due in the next 7 days, and the other languages' words and decks.

The treasure map, the school (and a unit opened from here), a grammar page's "🎯 Vadi" and "📚 Moje besede" lead back
to the progress screen. QA's step `progress` goes through it, in the dark theme and with the base in German too
([Test](#test)).

### Moje besede · My words

"📚 Moje besede · My words" (`ui/words/MyWordsScreen.kt`; the list and the search in `data/MyWords.kt`, the
dictionary's part in `app/MyWordsSearch.kt`): the learner's words in one list, to look one up quickly. It opens from
Napredek's words line ("📚 Besede · Words 248 / 300 ›") and from "📚 Moje besede ›" under the head of "Nove besede · New
words" (the word packs); back leads where it came from.

- **Which words.** The home language's vocabulary cards (GET /state), one row a word (the same word twice, a pack's and
  one added from a dialog, is one: its learned card): the learned ones, as Napredek counts them (`Stats.learned`:
  answered right, not rusty since), and those still being learned with 🌱 (rusty, or added from a word's card and not
  answered yet). The head says both: "248 znanih · 248 known · 12 v učenju · 12 still being learned". Counting and forms
  cards are no words here.
- **The order.** The newest on top, "🆕 Nedavno · Recent" (added in the last 14 days, the newest first, at most 8; only
  when the list has more than 8), then all of them by their first letter in the Slovene alphabet: c, č, d …, s, š …, z,
  ž, each its own letter (Č after C), another accent under its letter (ć under C), a digit or a sign under "#", last.
  Why: the alphabet is how a word is looked up, and the newest are the ones most often looked for; by kind (noun, verb)
  would need the dictionary for every card and split a short list.
- **The search** at the top, kept above the keyboard (the screen's `imePadding` after `navigationBarsPadding`, as the
  app's other fields: the app draws edge to edge and the window doesn't shrink for the keyboard). The list filters as it
  is typed: č, š, ž typed or not ("zlica" finds žlica), the word's beginning first, then a word of it ("dan": "dober
  dan"), from two letters on within the word, then its meaning ("house": hiša). A search of letters (2–40) that isn't
  one of the learner's words as it is goes to the dictionary too, 350 ms after the typing stops (GET /lookup, as a tapped
  word; a newer search drops the one under way): "📖 V slovarju · In the dictionary" lists its entries, the learner's
  own marked ✓ (a form of theirs, "hiše", names its word, hiša). Nothing found, offline or a node older than lookups is
  said only when none of theirs matched.
- **A word** opens its card (the word sheet, [Word lookup](#word-lookup)): its meanings, its forms, "📋 Vse oblike · All
  forms" ([Forms cards](#forms-cards)), ➕ for a dictionary word not theirs yet (POST /words `from: "search"`, to a
  bridge whose GET /words lists it; "villager" to an older one).

QA's step `forms-view` searches it and opens a word from it ([Test](#test)).

## Voice

The app speaks Slovene with natural voices. The bridge makes each clip once, keeps it in the voice cache (SQLite
`voice.db` + `files/<sha1>.mp3`: `LANI_VOICE_CACHE`, `~/.cache/lani/voice` after lani-setup; else `<data>/app/voice/`,
which is also used while only it has the clips) and the app keeps the clips it plays, and the day's clips it gets
ready on Wi-Fi, within the learner's cap ([Offline on the phone](#offline-on-the-phone)). Everywhere the app speaks, it
plays, in this order: a family recording, a node clip on the phone, a clip the node makes on request (`POST /voice/say`;
a new line waits up to 8 s for it, since ElevenLabs takes a few seconds, and a node that can't make it answers at once),
a clip on the phone in another of the speaker's voices, the phone's offline voice (Piper, when it's on the phone, in
its own language only), then Android TTS, which is only the last fallback. 🐢 plays clips at 0.75×. The village's background sounds duck under every voice, and stop while the microphone
records ([GAME.md](GAME.md#background-sounds)).

**The voice cast.** Who speaks is in `voice-cast.json`: each speaker has an ElevenLabs voice, a
Gepard voice for when ElevenLabs can't, and a gender. The narrators `female` (default) and `male`
read every generic text (words, examples, exercises, role-play characters by their scenario's
`voice`). In a Slovene village they speak with native Slovene library voices, Nina and Luka, and the local Gepard voice
is their fallback, never with their English library voices (below, "The narrators in Slovene"). Each villager speaks with their own `speaker` (`cultures/<culture>/villagers/*.json`; `voice` stays their
gender, which the app needs for Slovene agreement), in their lines, their scene dialogs and their
role-play. A scenario may set a `speaker` too. The character voices come from the ElevenLabs voice
library, picked by audition (Slovene test sentences, word error rate with the local Whisper):

| Speaker | Voice | Who |
|---|---|---|
| `female` | Matilda (premade, English); in Slovene Nina (Slovene), then Gepard ana (a lone word) or nina | narrator |
| `male` | Daniel (premade, English); in Slovene Luka (Slovene), then Gepard marko | narrator, male role-play characters |
| `grandma` | Zlata (Croatian) | Babica Micka, Teta Ančka |
| `grandpa` | Tomasz Z (Polish) | Stari Janez, Mlinar France, Čebelar Anton |
| `young-man` | Bartłomiej (Polish) | Pastir Luka, Vinar Marko, men who move in |
| `gruff` | Nikola (Serbian) | Kovač Tone |
| `woman` | Mila (Croatian) | Gostilničarka Vida |
| `teacher` | Milena (Serbian) | Učiteljica Mojca |
| `young-woman` | Ana (Croatian) | women who move in |
| `girl` | Piku (since 2026-10-14; Torri Miles before) | Zala, girls born in the village |
| `boy` | Kavya | Nejc, Tine, boys born in the village |

The app plays a line in the speaker's voice; without that clip, in the villager's gender voice,
then `female`, then it asks the node (an older bridge refuses a character voice, and the app then
asks in the next voice), then Android TTS. The bridge's `POST /voice/say` falls back the same way.

**A culture pack's voices.** A village in another language has its own voice cast, `voice-cast.json` in its
culture pack (`cultures/friuli/voice-cast.json`), with the same archetypes (`grandma`, `grandpa`, `young-man`, …)
so the cast's `speaker` fields carry over. The bridge uses it instead of `companion/voice-cast.json`
(`LANI_VOICE_CAST` still overrides both), asks ElevenLabs in the pack's language (`language_code: it`), and
never the local Gepard worker, which speaks only Slovene. A cast whose speakers have no ElevenLabs ids (`""`) makes no
clips at all, and the app speaks with the phone's TTS. Voice Design stays off for new profiles.

The friuli, kaernten and lakeland casts were chosen by audition (September 2026): two or three candidates per speaker
said the same villager lines, and the local Whisper checked them (every word right), with their pitch and noise
measured. The narrator reads about half of each pack's text, the grandparents a fifth. So the Italian village's
narrators, grandparents and young man are native Italian library voices, and the German village's narrator and old man
Austrian ones. The rest are voices the account had, most of them verified by ElevenLabs in that language:

| Speaker | friuli (Italian) | kaernten (German) | lakeland (English) |
|---|---|---|---|
| `female` | Violetta (Italian) | Fiona (Austrian) | Alice (British, premade) |
| `male` | Chris Basetta (Italian) | Daniel (`carrier`) | Daniel (British, premade) |
| `grandma` | Angelina (Italian) | Mila | Lily (British, premade) |
| `grandpa` | Nonno Ben (Italian) | Helmut (Austrian) | George (British, premade) |
| `young-man` | Chris Basetta (Italian) | Chris Basetta | Will |
| `gruff` | Tomasz Z | Brian | Brian |
| `woman` | Mila | Matilda | Sarah |
| `teacher` | Alice | Lily | Matilda |
| `young-woman` | Ana | Jessica | Jessica |
| `girl` | Torri Miles | Torri Miles | Torri Miles |
| `boy` | Kavya | Kavya | Kavya |

- The library has no Italian or German child's voice (Oceano, 14, has a teenager's), so the children keep Primorska's.
- Lakeland has no northern English voice yet. The library's (Wilf, Steve, Beatrice, Tony) are in its cast's
  `alternatives`.
- Voicing a village's content: `LANI_CULTURE=friuli companion/bin/voice-build --data <the learner's data dir>`, a dry
  run, then `--run`. Each pack's content is about 27,000–32,000 characters (friuli 27,373, kaernten 31,188, lakeland
  31,982), about half of that on the subscription.

**One voice per person.** Everyone who speaks has a voice profile (`profiles` in `voice.db`,
`bridge/src/profiles.ts`): the speaker above they stand in for (their archetype), and a pitch and pace
of their own that the app plays that voice with (0.93–1.07, 0.96–1.04; the first of each archetype by
order keeps it unchanged), so Stari Janez, Mlinar France and Čebelar Anton never sound alike, and
neither do the people who move in. The app registers who lives in the village (`POST
/voice/profiles`) and gets everyone's speaker, pitch and pace back.

While the ElevenLabs account has room, the cast who live in the village also get **a voice of their
own**, made on demand: ElevenLabs Voice Design v3 (`eleven_ttv_v3`) is asked for who they are (age,
manner, a native Slovene speaker from near Nova Gorica) and hears them speak a Slovene sample of their
own lines; of its three takes, the one the local Whisper understands best is saved in the account.
Their lines are then voiced in it the first time they are heard (speaker `@micka`); until then, and if
it fails, their archetype's clip plays. There is only one Slovenian voice in the ElevenLabs library, so
designing from Slovene is how the voices get a native sound (a test: 0.87–0.91 of the words understood,
against 0.79 for the library grandmother). Room: a voice of one's own takes a voice slot (Starter: 10);
the cast first by order, residents only while slots are left beyond the cast still to come; one slot is
kept for a redesign, 10 monthly voice edits spare, at most 4 designs a day, and a design costs about the
sample's length in characters (~150). `LANI_VOICE_DESIGN=off` turns it off. The tutor's
`voice_profiles` shows who has which voice; `voice_redesign` gives someone a new one (optionally from a
new description), deleting the old voice and its clips.

**Swap a voice** (Jan heard a sample and prefers another one):

1. Find the voice. The audition runners-up are in `voice-cast.json` under `alternatives`, a library
   voice with its library owner id for the API. A library voice takes no voice slot (only designed
   voices do, 10 on Starter), and the first request with its id adds it to the account (checked
   2026-09-27): "Add to my voices" is not needed.
2. Put its id in the speaker's `elevenlabs` line in `voice-cast.json`, and the day in its `since`
   (`"since": "2026-10-14"`). The bridge reads the file again when it changes; no restart.
3. Its clips are made again as the app asks for them (below, "Stale clips"), or all at once:
   `companion/bin/voice-build --run --revoice grandma` (dry run first without `--run`). The new clips get new file
   names, so the app downloads them.

A narrator's Slovene voice is its `languages` `sl` entry (below, "The narrators in Slovene"): put the new id in its
`elevenlabs`, then `companion/bin/voice-build --run --revoice female`. The local clips stay as they are.

**A voice that hisses: the denoise filter.** Stari Janez's own designed voice (`@janez`) has a steady hiss:
its pauses are at about −35 dBFS, where the other voices are at −45 to −80 dBFS. It also has a rumble below
30 Hz. Jan likes the voice, and a new one would cost credits, so the bridge filters his clips instead:

- **Which voices:** `denoise` in `voice-cast.json` maps a voice to a preset. The voice is a speaker of the
  cast (`grandpa`) or someone's own voice (`@janez`); `true` means `hiss`. Only their ElevenLabs clips are
  filtered: the hiss is in the ElevenLabs voice, and the local worker's clips are clean. A wrong entry is
  logged and left out; the rest of the cast stays. A culture pack's cast has its own `denoise`. Since
  2026-09-28 the list also has the girl (Torri Miles, in the other villages' casts; Primorska's girl is Piku since
  2026-10-14, a clean voice: no filter), the young man, gruff, and France's and Micka's own voices. Their pauses measured −38 to −55 dB (Jan heard the girl as loud and
  noisy); `hiss` takes them to −74 to −96 dB, and their loudness changes by under 1 dB.
- **The filter** (ffmpeg, `DENOISE` in `bridge/src/voice.ts`):
  - a high-pass at 60 Hz, for the rumble;
  - `afftdn`, spectral noise reduction: 12 dB off below a fixed noise floor of −40 dBFS. It delays the sound by
    25 ms, which the chain puts back;
  - a gentle gate: below −37 dBFS the level falls 3:1, at most 24 dB, with a 5 ms attack (word starts stay)
    and a 120 ms release (tails fade naturally).

  `hiss-strong` takes 20 dB off below −35 dBFS, for a voice that `hiss` doesn't clean enough.
- **Measured** on all 39 of Janez's clips and on single words cut from them (`hiss`):
  - the hiss in the pauses is 12 dB lower (`hiss-strong`: 19 dB), and astats' noise floor goes from −33 to
    −62 dBFS;
  - speech level, fricatives, word starts and tails change by less than 1 dB on average; length and timing
    stay the same;
  - the local Whisper's word match goes from 0.82 to 0.79: a few words are heard differently, some better,
    some worse.
- **New clips:** the bridge filters a clip of such a voice before it stores it: a new clip, a carrier cut, a
  lazy re-voice or a re-record. The clip's row records the preset (`denoise` in `voice.db`), and its file
  name includes it. Without ffmpeg (or when ffmpeg fails), the clip is stored as it is. This is logged, and
  the clip waits for the batch below.
- **The clips from before:** `companion/bin/voice-build --denoise` is a dry run that lists the clips that
  wait; `--denoise --run` filters them (`--max N` for a few first, `--data DIR` for another data directory).
  Each clip is filtered once and written under a new file name. The old file is removed only after the new
  one is recorded. A clip that the bridge made again meanwhile keeps the new take. It uses no ElevenLabs
  characters.
- **The phone** caches clips by file name, so it downloads the new files. The bridge looks every minute for
  clips that another process changed (SQLite's `data_version`) and sends `voice_updated`. The app then reloads
  its index. A phone that is away gets the event when it comes back (the bridge keeps the last 200 events
  until it restarts), and every app start reloads the index anyway.

`voice_status` shows the voices filtered, their clips filtered and waiting, and whether the node has ffmpeg
(`denoise`).

**Every clip at one loudness: the level.** The voices came out of ElevenLabs up to 10 dB apart. Jan asked
why Zala was so much louder than the rest. Measured on all 2,826 clips (integrated loudness, EBU R128; the median
of each voice): the girl voice (Zala) at −10.0 LUFS with peaks at 0 dBFS, grandma −12.8, `@janez` −13.4, the
female narrator −17.4, grandpa −19.4, the local Gepard clips −17.1 to −19.0. So the bridge brings every clip to
one loudness before it stores it:

- **The target:** −18 LUFS integrated, the true peak at most −1.5 dBTP (`LEVEL` in `bridge/src/voice.ts`).
  Speech peaks about 15 dB over its loudness (p90 18 dB). A louder target would need the limiter on most clips:
  at −16 LUFS, 58% of them, by 3.4 dB (p90 6.5), and the most limited words stayed up to 4 dB short of it. At
  −18, about a third, mostly by 1–2 dB. The female narrator, half of all clips, was at −17.4 already.
- **The measure:** ffmpeg's `ebur128`, the integrated loudness and the true peak. It gates in 400 ms blocks, so a
  clip under 0.4 s has none; it is then measured looped (its mean loudness). From 0.4 s on, single words measure
  like the same voice's sentences (the narrator's 0.6–0.9 s words −17.7, her 1.5–3 s lines −17.3), so carrier
  cuts use the integrated value too.
- **The gain:** a plain gain (`volume`), which keeps the sound. `loudnorm` isn't used: its dynamic mode changes
  short clips. Where the gain would take the true peak over the ceiling, a limiter holds the peaks (`alimiter`,
  5 ms lookahead, its delay put back). A clip within 0.5 dB of the target, its peak under the ceiling, is kept
  as it is (no encode). At most +15 dB, so a clip of near silence isn't pumped up.
- **One encode:** the MP3 encode itself takes about 0.4 dB off (LAME at a constant bitrate, even a plain tone).
  So the result is measured and, when it misses by more than a quarter dB, encoded again from the clip as it was,
  not from the encode. A voice's denoise filter runs in the same encode, before the measure. A carrier cut is the
  one clip that is encoded twice: the cut, then the level.
- **The clip stays as it was made** otherwise: its encoding (ElevenLabs 44.1 kHz 128 kbit/s, Gepard 22.05 kHz
  64 kbit/s, mono), its length and its timing. A clip without a gapless header (Gepard's, a carrier cut) ends
  47 samples (1–2 ms) later.
- **New clips:** every clip the bridge stores (a new clip, a carrier cut, a lazy re-voice, a re-record, the
  local worker's, the tutor's live lines) is levelled first. Its row records the target (`level` in `voice.db`)
  and the gain (`gain`, dB, in all, from the clip as it was made), and its file name includes the level. Without
  ffmpeg (or when ffmpeg fails), the clip is stored as it is. This is logged once, and the clip waits for the
  batch below.
- **The clips from before:** `companion/bin/voice-build --level` is a dry run. It measures every clip that
  waits and shows each voice's loudness now (median, p10–p90), the gain, how many are kept as they are and how
  many the limiter holds, and by how much. `--level --run` levels them (`--max N` for a few first, `--data DIR`
  for another data directory; about 2 minutes for all 2,826). Each clip is levelled once, under a new file name;
  the old file is removed only after the new one is recorded. A clip whose voice's filter is due is filtered in
  the same encode, and a clip whose edges wait is trimmed in it (`--denoise --run`, `--edges --run` and `--tempo --run`
  level in the same encode too). A clip that the bridge made again meanwhile
  keeps the new take. It uses no ElevenLabs characters. The bridge sees the changed clips within a minute and
  sends `voice_updated`, as for the denoise filter.
- **A new target:** change `LEVEL.target`, then run `voice-build --level --run`. It levels the clips at the old
  target again, from their files as they are (one more encode; a clip within 0.5 dB of the new target keeps its
  file), and adds the gain to the row's `gain`.
- **Measured on a copy** of all 2,826 clips after `--level --run`: every voice's median at −18.0 to −18.2 LUFS,
  the whole store from −18.3 to −18.0 (p10–p90; the most limited word −20.4), the true peak at most −1.5 dBTP;
  434 clips kept as they were, 920 limited.
- **Family recordings** are levelled as they are uploaded, in their own container (m4a: AAC 96 kbit/s; ogg and
  webm: Opus 64 kbit/s), without the second encode. The ones from before stay as they are.
- **The phone's TTS**, the last fallback, can't be levelled by the node.

`voice_status` shows the target and the clips at it and waiting (`level`).

**The silence at a clip's edges.** In the listening exercise the word came late. A narrator's lone word, cut out of its
carrier sentence, often had 0.2 to 0.7 s of silence before it. Measured on a copy of Jan's store (October 2026, 9,615
clips; the silence before / after the sound, median and p90):

| Clips | Before the sound | After it |
|---|---|---|
| narrator words cut out of their carrier sentence (1,163) | 0.24 s (0.46) | 0.02 s (0.03) |
| the local Gepard worker's, as it makes them (549) | 0.52 s (0.68) | 0.64 s (0.87) |
| ElevenLabs' other clips (7,903) | 0.02 s (0.08) | 0.00 s (0.26) |

So the bridge trims every clip's silence at its edges before it stores it, whatever made it (`EDGES` in
`bridge/src/voice.ts`):

- **What is kept:** 0.05 s of silence before the sound, 0.1 s after it, with a short fade where it cuts (5 ms in,
  10 ms out).
- **Where the sound starts:** at the first 20 ms window over −45 dBFS (RMS), as loud as the clip will be after the
  level (27 dB under its −18 LUFS). From there it reaches back as long as the level stays over −60 dBFS, and the same at
  the end. The voices' silence is at −61 to −84 dBFS.
  - A soft h, f or v (hrbet, februar, včeraj) rises from −60 for up to 0.08 s before it passes −45. A trim at −45 alone
    cut into it on 14 of the 1,163 carrier words; with the −60 floor, on none.
  - A lower threshold alone takes the end of the carrier sentence's "je:" at the start of the cut for sound, and trims
    nothing there.
- **One encode:** it runs in the level's encode, after a voice's filter. The clip keeps its encoding.
- **When:** a clip that is encoded anyway is trimmed to 0.05 s and 0.1 s. A clip that the level would keep as it is
  is trimmed only when it has more than twice that (0.1 s before the sound, 0.2 s after it), so a clip is not encoded
  again for a few milliseconds.
- **Recorded:** the clip's row has `edges` (1: trimmed, or found short enough; null: not yet), and a trimmed clip's file
  name includes it. Without ffmpeg (or when ffmpeg fails), the clip is stored as it is. This is logged, and the clip
  waits for the batch below.
- **The clips from before:** `companion/bin/voice-build --edges` is a dry run. It measures every clip that waits and
  shows, per engine, how many it would trim and the silence before and after the sound, now and after. `--edges --run`
  trims them (`--max N` for a few first, `--data DIR` for another data directory). Each clip that it trims is brought
  to the level again in the same encode, under a new file name, and the old file is removed only after the new one is
  recorded. A clip at the level keeps its loudness (one that the limiter held under the target stays so). The other
  clips are only recorded. No engine is asked, and no characters are spent. The bridge sees the changed clips within a
  minute and sends `voice_updated`.
- **Measured on a copy** of Jan's store, the paced Gepard clips restored first (below): 2,584 of the 9,615 clips
  trimmed (1,026 carrier words, 549 Gepard clips, 1,009 other ElevenLabs clips), 20 minutes of silence in all, in about
  6 minutes. Measured again after it (in 20 ms windows): the carrier words' silence before the sound 0.24 → 0.06 s
  (median), the Gepard clips' 0.52 → 0.06 s before and 0.64 → 0.11 s after. Each trimmed clip is as long as before less
  the silence taken (within 2 ms), at −18.0 LUFS (median).

- **A single word** asked for as it is (one word, no sentence punctuation; not a carrier cut, which is the word already)
  ends at its first pause of 0.25 s or more (`EDGES.wordGap`): what follows is cut off with the silence. ElevenLabs
  sometimes adds a breath or the start of another sound after one word, louder than −45 dBFS, so the trim kept it. In the
  check of October 2026 (Nina and Luka, 8 words, 24 plain takes) 5 takes had one, 0.38 to 1 s after the word (clips of
  1.2 to 1.75 s). The guard cut those five to the word (0.46 to 0.64 s of sound) and left the other 19 as they were. No
  word has such a pause inside: a stop's closure is 0.05 to 0.15 s. A phrase of two or three words keeps its pauses.

`voice_status` shows the clips trimmed (or found short enough) and waiting (`edges`).

**The local voice at its own pace.** Gepard's clips were once paced to the ElevenLabs narrators' characters a second
(October 2026: the pauses at most 0.4 s, ×1.05). Jan heard them as too fast. So the local voice keeps its own pace: only
its silence at the edges is trimmed, as every clip's.

- **A pace stays settable**, off by default (`gepardPace` in `bridge/src/voice.ts`; in `lani.env`, read when the bridge
  starts):
  - `LANI_VOICE_GEPARD_TEMPO`: a factor from 0.5 to 2 (ffmpeg `atempo`, the pitch kept). The default is `1`.
  - `LANI_VOICE_GEPARD_PAUSES`: the longest pause kept inside a clip, 0.1 to 2 s (silence: under −50 dBFS). The
    default is `off`.
- With a pace set, the bridge paces a new Gepard clip in the level's encode, before its edges are trimmed. The row
  records the factor (`tempo`), and the file name includes it. `companion/bin/voice-build --tempo` then measures the
  local clips not at that pace (a dry run), and `--tempo --run` paces them. Without a pace set, `--tempo` says so and
  does nothing.
- **The 549 clips paced before:** `companion/bin/voice-build --restore-gepard SNAPSHOT` puts them back as the worker
  made them, from a `lani-backup` snapshot taken before they were paced (the snapshot directory, or its `voice/`):
  - The dry run lists each paced clip (its row has `tempo`) and what it would restore, and the clips it can't restore,
    with the reason (not in the snapshot, another text, not by the worker, its file missing).
  - `--run` copies each clip's file from the snapshot into `files/` (under a temporary name, then renamed), checks it
    byte for byte, and points the row at it with the snapshot's level, gain and filter. Its `tempo`, `edges` and engine
    voice are cleared. Only then is the paced file removed. A clip that the bridge made again meanwhile keeps the new take.
  - The snapshot is only read. Its database is read from a copy, so not even SQLite's side files are written next to
    it.
  - Then `--edges --run` trims the restored clips' edges.
  - On a copy of Jan's store: 549 restored, all byte for byte the snapshot's files, no other row changed, no file left
    without a row.

**The narrators in Slovene: native voices, the local voice as the fallback.** The narrators `female` and `male` are
English library voices (Matilda, Daniel). In Slovene a lone word comes out with English sounds, even cut out of a carrier
sentence. Jan compared a word side by side: "1. and 2. sound the same and slow (and wrong), 3. is in another league" (1
the ElevenLabs narrator, 2 the same trimmed, 3 Gepard's marko). So since 10 October 2026 the narrators of a Slovene village
never speak with Matilda or Daniel. On 11 October Jan auditioned the native Slovene voices of the ElevenLabs library and
picked one for each narrator. They come first now, and the local Gepard voice is their fallback:

| | `female` | `male` |
|---|---|---|
| 1. ElevenLabs | Nina ("Nina - Warm, Proud Ad", `0IwLFuWc6siAnpd77hRG`) | Luka ("Luka - Warm, Amused Creator", a Štajerska colour, `SmCUS1ekZnVjC5lqnRQG`) |
| 2. Gepard, the local voice | ana for a lone word, nina for a sentence | marko |
| 3. The phone | its offline voice (Piper), then Android TTS | the same |

- **Who:** the narrators' `languages` `sl` in `voice-cast.json` (a library voice, with its `library_owner`; it takes no
  voice slot, and the first request with its id adds it to the account). Their clips are the voices `female.sl` and
  `male.sl` in `voice.db`. The local voice is the narrator's `gepard`, and for a lone word (one to three words, no
  sentence punctuation) its `gepard_words` when it has one: Jan preferred ana for single words, nina for sentences. The
  bridge sends the worker the voice by name, so the worker's own aliases don't matter.
- **What:** every text in a narrator's voice: words, examples, cards, exercises, the car's drills and quiz, a role-play
  character by its scenario's `voice`, live lines.
- **When ElevenLabs can't:** the quota is down to the reserve (5,000 characters, `LANI_VOICE_RESERVE`; a live line may
  use the reserve), there is no key, it fails or is down. Then the local voice makes the clip. When it can't either (down,
  busy), there is no clip, and the phone speaks. A cast whose narrators have no `sl` voice has the local voice only.
- **At once:** the bridge has at most `LANI_ELEVENLABS_CONCURRENCY` text-to-speech requests at ElevenLabs at once (3 by
  default; the plans allow 3 on Starter, 5 on Creator), the rest wait their turn: a request someone in the app waits for
  (a live line, a long press's re-record) ahead of the background work (the lazy re-voice, the road's and the prebuilds'
  clips), which otherwise kept a new line past the app's 8 s wait on a morning of re-voices, and the phone said it in its
  own voice (`Gate`, 15 October 2026). Over the plan's limit ElevenLabs
  refuses a request (HTTP 429 `too_many_concurrent_requests`), and the bridge used to pause ElevenLabs for a minute then:
  a school intro's step, which asks for its explanation and its Slovene at once, had a form said by the local voice and
  its explanation not made (Jan's node, 14 October 2026). One still refused as too many at once or busy (voice-build's
  requests beside the bridge's) is asked again after 1 s, then 2 s, without the pause; any other 429 pauses it as
  before. `voice_status` shows `elevenlabs.concurrency` (`limit`, `running`, `waiting`, `busy_again`).
- **Not for:** the villagers (their speakers and their voices of their own): ElevenLabs first, then Gepard, as before.
  Nor a village in another language: its cast's narrators speak (the Lakeland village's Alice and Daniel speak English,
  the car's English prompts come from the phone).
- **The setting:** `LANI_VOICE_NARRATOR_SL` (in `lani.env`, read when the bridge starts): `elevenlabs` (the default:
  Nina and Luka first) or `gepard` (the local voice first, Nina and Luka when it can't; as from 10 October 2026).
- **Lone words:** Nina and Luka say a lone word plainly, not in a carrier sentence. A check with Jan's key (8 words: Nina
  *kosilo*, *žlica*, *hrbet*, *včeraj*; Luka *kosilo*, *miza*, *jajce*, *stol*; 268 characters, into a scratch store):

  | Way | The takes | After the store's trim |
  |---|---|---|
  | in the carrier sentence, cut out | 0.50–0.97 s, 0.04–0.28 s of silence before the word | 0.44–0.81 s; 7 of 8 takes ended inside the word's last sound (the last 20 ms at −7 to −34 dBFS), and Luka's *stol* lost its end (0.26 s of sound, plainly 0.52 s) |
  | plainly, as it is | 0.71–2.06 s, up to 1.24 s of silence after the word | 0.71–1.33 s; 2 of 8 with a breath after the word (below) |
  | plainly, with a period | 0.78–1.75 s | 2 of 8 with a breath or another sound after the word |
  | Gepard (for comparison) | | nina 0.99–1.07 s, marko 0.73–0.91 s (0.46–0.64 s of sound) |

  So plainly, and every single word's sound ends at its first pause of 0.25 s or more ("The silence at a clip's edges",
  above): with it, the plain takes are 0.71–0.99 s with 0.48–0.68 s of sound, like Gepard's. Plainly also costs the
  word alone: *kosilo* is 6 characters, `Beseda je: kosilo.` 18.
- **Two clips:** a narrator's line has a row for each voice: the ElevenLabs clip (`female.sl:<text>`) and the local clip
  (`female:<text>`). The app knows only `female`. `/voice/index`, `/voice/say` and `/voice/prepare` give it the first
  that is there, in the setting's order: with `elevenlabs`, Nina's clip, else the local one. So while Nina's clip is
  missing, the app plays the local clip: no silence, no TTS. When Nina's clip is made, the local clip stays: it is the
  fallback, and with `gepard` the app gets the local clips again at once, nothing made again. The phone caches by file
  name, so it downloads Nina's clip the next time it plays the line (or with the day's clips on Wi-Fi); until then it
  plays the local file it has.
- **From the local clips to Nina and Luka:**
  - **As the app asks:** when the app downloads a narrator's local clip (it plays the line, or gets the day's clips on
    Wi-Fi) or `/voice/say` hands one over, and Nina's or Luka's clip of it is missing, the bridge makes that clip soon:
    most asked for first, within the day's re-voice cap (`LANI_VOICE_REVOICE_DAILY`, shared with the carrier re-voice;
    re-records have their own), never into the reserve. Then it sends `voice_updated`. Clips
    that wait for the next day are taken up at the next request, or when the bridge starts (the app's requests are
    counted in `hits`).
  - **Ahead:** `companion/bin/voice-build --run` makes them in the priority order (pack words, examples, modules, …)
    within the budget: the quota less the reserve, `--max-chars N` for less. Its dry run shows "Narrators in their Slovene
    ElevenLabs voices: female (Nina) x/y (local z)" and how many `--run` would make now, "next to a local clip, which
    stays". Every run goes on where the last one stopped.
  - **A long press** on 🔊 makes the clip in Nina's or Luka's voice at once: a new take, or the first one where only the
    local clip is there. It counts as any re-record (once a clip a day, in the day's re-records).
- **The local voice's lone words in ana:** `companion/bin/voice-build --narrators` (a dry run) counts the narrators' local
  clips not in their local voice for their text: the female narrator's lone words in nina (for ana), sentences in ana
  (for nina), and ElevenLabs' English clips from before (with the carrier words). It shows each group as it is and the
  voice it is made in ("female gepard (nina) → ana"). It makes again only the Slovene ones: a clip of this village's
  corpus, of a source the corpus has (a pack, a scene, a story), or a line the app spoke here (`live`, `road`). It lists
  the others, which stay as they are. `--narrators --run` makes them again with the worker, most asked for first
  (`--max N` for a few first). Each new clip has its edges trimmed and is levelled, under a new file name, and the old
  file is removed once the new one is recorded. The worker makes one at a time, about a second each. A busy worker is
  waited for, and a worker that is down stops the run. The next run goes on where it stopped. The clips in Nina's and
  Luka's voices are left as they are. No ElevenLabs characters.
- **Recorded:** a clip's row has the engine's own voice (`engine_voice`: Nina's voice id, `ana`), and its file name
  includes it, so the app downloads a clip whose voice changed. A Gepard clip from before has none: a female narrator's
  was ana's.
- **With the local voice first** (`gepard`), a re-record of a narrator's clip from before is made in the local voice, and
  isn't counted in the day's cap; a clip that is in it already is the one (`not_needed`: the local voice says a text the
  same way every time). The lazy carrier re-voice leaves the Slovene narrators' words alone either way.
- **On a copy of Jan's store** (11 October 2026): 5,042 narrator clips, all from the local voice (4,545 female in nina,
  497 male in marko). 1,045 of the female ones are lone words in nina: `--narrators --run` makes them again in ana (7,284 characters of
  text, about 18 minutes of the worker). The corpus has 5,621 narrator texts (116,083 characters), 4,223 of them with a
  local clip. With 18,766 characters left until 24 October and 1,500 kept, `voice-build --run` would make 1,481 of them in
  Nina's voice now (17,266 characters, pack words and examples first; 1,388 next to a local clip); the rest waits for the
  next periods, and the app asks for more every day.

`voice_status` shows the narrators (`narrators`): the setting (`mode`), their ElevenLabs voices and local voices (for a
sentence and for a lone word), the clips in their ElevenLabs voices, their local clips in the local voice and not, and the
local clips whose ElevenLabs one is still missing (`waiting`; `asked`: the app asked for them). It shows a pace when one
is set (`tempo`).

**Engines**, tried in this order for a text without a clip (a Slovene narrator's: its Slovene voice, then Gepard; see above):

| Engine | What | Setup |
|---|---|---|
| ElevenLabs | The model `LANI_ELEVENLABS_MODEL` (`eleven_v4_turbo` by default; v3 and v4 speak Slovenian), `language_code: sl`, the speaker's voice from the cast (a Slovene narrator's: its `languages` `sl`). Each clip records its model (`model` in `voice.db`; null: `eleven_v3`, every clip until October 2026). | `ELEVENLABS_API_KEY=…` in `~/.config/lani/keys.env` (mode 600). `bin/lani-session` exports it into the session, so the bridge inherits it. |
| Gepard (local) | Slovenian TTS model as a worker on `127.0.0.1:8795` (`GET /health`, `POST /synth`), the speaker's `gepard` voice (`nina`, `marko`, `ana`), sent by name. | Runs separately. When it is down, it is skipped. |

If neither can make a clip, the app uses phone TTS; an engine that is down never fails a request.
A villager line whose narrator clip exists waits for ElevenLabs (no local clip in the character's
key): the app plays the narrator clip until then.

**Lone words: a carrier sentence.** Where an English library voice narrates with ElevenLabs (a village in another
language whose cast marks a narrator `carrier`: the Kärnten village's Daniel; in a Slovene village the narrators speak
with native voices since October 2026, which say a lone word plainly, above): `eleven_v3` (and v4) take
`language_code: sl` only as a hint: a lone Slovene word (*kosilo*) comes out with English sounds, a
sentence doesn't (`previous_text`/`next_text` are not supported for `eleven_v3`). So a speaker marked
`"carrier": true` in the cast (the narrators; villagers' voices are Slovene enough) says a short text inside
a carrier sentence:

- **Short text:** one to three words without sentence punctuation (`kosilo`, `dober dan`; not `Kako si?`).
- **The request:** `Beseda je: kosilo.` to `POST /v1/text-to-speech/{voice}/with-timestamps` (the model,
  `sl`, MP3; both v4 models give the timestamps). The carrier sentences are per language (`CARRIERS` in `bridge/src/voice.ts`: `Beseda je: …`, `La parola
  è: …`, `Das Wort ist: …`, `The word is: …`), in the village's language.
- **The cut:** the word's span from the characters' timestamps, from 0.06 s before to 0.12 s after, with
  ffmpeg (`atrim`, fade in 0.02 s, fade out 0.05 s). The clip is the cut; its `method` is `carrier`.
- **Costs:** the guard counts the whole carrier sentence (`Beseda je: kosilo.` is 18 characters, 9 credits with
  `eleven_v4_turbo`).
- **Fallback, logged:** without ffmpeg (`LANI_FFMPEG` names another, `off` turns it off) a short text is
  asked for plainly, as before. If the word isn't in the timestamps, it is asked for plainly too (both takes
  are paid for), and the clip is marked `plain`, so it isn't tried again.

**The words from before** (about 500 narrator words said plainly) are voiced again lazily, where an English narrator
speaks (in a Slovene village the narrators' clips go to Nina and Luka instead, the same way, and `voice-build
--narrators` makes the local clips from before again in the local voice: above). When the app
asks for one (it downloads the file, or `/voice/say` hands it over), it gets the old clip at once. The
bridge counts the request and soon re-voices the word in its carrier sentence:

- most asked for first;
- at most 40 a day (`LANI_VOICE_REVOICE_DAILY`; 0 turns off the lazy re-voice), about 360 subscription
  characters; Jan's re-records (below) have a cap of their own, which the lazy re-voice never spends;
- never into the reserve for live lines.

The new clip gets a new file name, and the bridge sends `voice_updated`. The app then reloads the
index and downloads the new file the next time it plays the word. Words that wait for the next day are
taken up at the next request, or when the bridge starts.

The phone keeps the clips it has (within the learner's cap) and doesn't ask for them again. So the lazy re-voice reaches
mostly words the phone doesn't have yet. For the rest: Jan's long press on 🔊 (below), or
`voice-build --revoice-words`: its plan shows how many words are old-style and what re-voicing them all
costs. `voice_status` shows the same (`carrier`).

**Stale clips, made again as the app asks for them** (Jan, 2026-10-14: "we can regenerate on demand"). A clip is
stale when it isn't in its speaker's ElevenLabs voice now, or (`LANI_VOICE_UPGRADE_MODEL_LAZY`, on by default; `off`
turns it off) when an older model than `LANI_ELEVENLABS_MODEL` made it:

- **A swapped voice:** an ElevenLabs clip whose recorded voice (`engine_voice` in `voice.db`) isn't the speaker's
  ElevenLabs voice in the cast now (one's voice in a language, `female.sl`, and someone's own voice, `@micka`, too). A
  clip from before the voice was recorded (October 11, 2026) is stale when it was made before the day the speaker's
  voice took effect, the cast's `since` (Zala's girl: Piku since 2026-10-14). A new voice of one's own drops the old
  one's clips anyway.
- **An older model:** an ElevenLabs clip of another model than new clips are made with (one from before the model
  was recorded: `eleven_v3`).
- **When:** the app asks for it (a download, `/voice/say`): it gets the old clip at once, and the bridge records the
  ask and makes it again soon, in the lazy re-voice above: one at a time, most asked for first, within the day's cap
  (`LANI_VOICE_REVOICE_DAILY`, shared with the carrier words and the narrators' Slovene voices), never into the reserve.
  A swapped voice's clips come with the words and the narrators', also one played before without a new ask (the phone
  keeps the clips it has and doesn't ask again: the line would stay in two voices); an older model's after them, only
  once the app asks for it again (a better take, not a wrong one).
- **How:** in the speaker's voice and the model now (a short word in its carrier sentence, an explanation plainly in
  its language), stored as every new clip: the cast's filter for that voice now (the girl, off the denoise list with
  Piku, isn't filtered), its edges, the level, under a new file name, its hits kept, the old file removed; the app is
  told (`voice_updated`). Never a local (Gepard) clip, a Slovene narrator's English-voice clip from before, or a family
  recording.
- **What it costs:** `voice_status` (`stale`) and voice-build's plan show the stale clips (a swapped voice: n clips /
  asked m; an older model: n / asked m) and what making them all again costs: the characters sent and the credits with
  the model now (`eleven_v4_turbo` half a credit a character). `voice-build --upgrade-model` makes an older model's all
  at once.

**Re-record: a long press on 🔊.** When Jan hears a word or a line wrong (English sounds, a bad take), a
long press on its 🔊 has the node record it again at once (`POST /voice/redo {text, voice, client_id}`),
ahead of the lazy re-voice:

- **What is made:** the clip in the voice a tap plays (the first voice of the speaker's chain that the phone
  has a clip in). A short text in a carrier voice is said in its carrier sentence and cut, as above. Anything
  else (a sentence, a villager's voice, someone's own voice) is a plain re-take: ElevenLabs never says a text
  the same way twice. A clip that the local worker made is made again with ElevenLabs. A text without a clip is
  voiced as `/voice/say` voices a live line, and is not counted. A Slovene narrator's clip is made in its Slovene
  voice (Nina, Luka): a new take, or the first one where only the local clip is there, which stays. With the local voice
  first (`LANI_VOICE_NARRATOR_SL=gepard`), a clip from before is made in the local voice now (not counted), and one in it
  already is the one (`not_needed`: the local voice says a text the same way every time).
- **Limits:** once a clip a day, and 30 re-records a day (`LANI_VOICE_REDO_DAILY`; 0 turns them off), on their
  own count: the lazy re-voice and a `voice-build --revoice-words` run have the re-voice cap and never spend them. A
  sentence counts as one, like a word. A re-record never goes into the reserve for live lines.
- **The answer:** `200 {status: "done", url, file, key, norm, voice, method, today, daily}` (`today` of `daily`: the
  day's re-records): the new take
  under a new file name; the old file is gone, and the bridge sends `voice_updated`.
  `200 {status: "not_needed", …, message}`: the clip was re-recorded on request today already, and this is
  that take. `429 {status: "limit", reason: "cap" | "reserve", message}`. `503 {status: "failed", message}`:
  ElevenLabs is not set up, is down, or failed. A retry with the same `client_id` gets the first answer again
  (the outbox's dedupe), not a second take.
- **The app:** a spinner in the 🔊 and "🔄 Nov posnetek · Re-recording…" above it. Then the app drops its
  cached clip, puts the new one in its index, downloads it and plays it. Otherwise a short note says why:
  "Danes ni več posnetkov · No re-recordings left today", the reserve, or offline (nothing is queued).
  TalkBack has it as the action "Posnemi znova · Re-record". After a week of use, the first tap on a 🔊 shows
  once where the long press is (not when Jan has found it already).

`voice_status` shows today's re-records and their cap (`carrier.redos`, `carrier.redo_daily`), and the lazy re-voice's
(`carrier.today`, `carrier.daily`).

**The school's introductions, read aloud** ([COURSE.md](COURSE.md#read-aloud)). Each step of a unit's guided
introduction is read as it appears, in the teacher's voice (`data/IntroVoice.kt`). Its explanation, in the learner's
own language with the Slovene forms left inside, is one clip, asked for with `lang` on `POST /voice/say` (an
explanation of up to 1,500 characters; a line stays at 300) or on `/voice/prepare`. ElevenLabs makes it in one go
(the model, with no `language_code`, so it says both languages as they are; no carrier sentence, even for one word),
never the local worker. It is no live line: it never uses the reserve, and counts in the live lines' rate window. Its
clips have their own key space (`en|<normalized text>` in `/voice/index`; a Slovene line of the same words stays
another clip), are shared by every learner of that language, are counted apart in `voice_status` (`explanations:
{clips, chars, by_lang}`; not in `clips` nor in the coverage), and are left alone by the re-voice and narrator passes.
As the intro opens the app asks for its steps' explanations ahead, one after the other (`IntroVoice.ahead`: the first
while Mojca walks in), so each step finds its clip as it is read; a step whose clip the node is making waits for it up
to 6 s (a clip on the node but not on the phone: 3 s, a download), and one that doesn't come in time is there next time
(a step is mostly read once: a clip made only "for next time" was never heard). Meanwhile, and offline, without
ElevenLabs or with the quota at its reserve, the app reads the step in its runs: the base-language parts by the
phone's own text-to-speech in that language (rendered to files as the car's prompts are), the Slovene parts in the
teacher's voice as any line (asked for while the clip is waited for), the run read marked in her bubble. An explanation
the node couldn't make just now (a 204) is asked again after a minute, not ten as a line. The app asks only a bridge
whose `GET /state` lists `explanations` in its `features` (an older one would voice the English as a Slovene line): the
bridge must be restarted after updating for it.

**Costs.** The quota guard reads the subscription at most every 10 minutes. Prebuilds and background jobs stop
when fewer than 5,000 characters would be left (`LANI_VOICE_RESERVE`); that reserve is for live lines (the tutor's
role-play lines, voiced as they come). Every line of a new learner's village, voiced once (October 2026): Primorska
252,000 characters in 8,129 lines, Friuli 58,000, Kärnten 66,000, Lakeland 69,000 (`voice-build`'s dry run on an
empty voice cache; docs/setup.md, "Your own ElevenLabs"). A character costs what its model's
`character_cost_multiplier` says (the account's `/v1/models`, October 2026): `eleven_v4_turbo` half a credit,
`eleven_v4` and `eleven_v3` one (`MODEL_CREDITS` in `bridge/src/voice.ts`; in September 2026 `eleven_v3` counted about
half, 34,100 characters for about 17,000, its price while it was new). The model is `LANI_ELEVENLABS_MODEL`
(`eleven_v4_turbo` by default: Jan, comparing samples in October 2026, heard v4 better than v3 and no difference between
v4 and v4 Turbo, which costs half); Voice Design keeps `eleven_ttv_v3`, and the denoise filter still applies (Janez's and
Micka's designed voices hiss in v4 too). Each ElevenLabs clip records its model (`model` in `voice.db`, a take of
another model gets a new file name; null: `eleven_v3`); `voice_status` shows them (`models`: `now`, and `by` model with
their clips and characters), as does voice-build's plan. `voice-build --upgrade-model` makes the clips of an older model
again in the current one, most played first (`hits`), within the quota above the reserve (`--max-chars`, `--max`): each
in its voice (a short word in its carrier sentence, an explanation plainly), filtered, trimmed and levelled as every new
clip, its hits kept, under a new file name, the old file removed; a running bridge tells the app (`voice_updated`).
Never a local clip, nor the family's recordings. Not run on its own: Jan decides when (the quota resets on the 24th). The guard counts every character sent between subscription reads, so it stops
early rather than late. New content and a month that runs short go
in the order below, and the next month goes on where one stopped.

**Prebuild**, in priority order: villager lines in their speakers' voices, review cards and pack
words, examples, modules, scenarios, the storyteller's stories told at A1 (`stories A1`), the car's drills at A1
(`drills A1`), scene dialogs and the keepers' talks (the culture's spots: the charcoal burner's meeting and his story,
every telling at every level, and with his pile's cycle every stage's talks, the night watch's race, what he tells of the
days the learner missed and his own story's parts, in his speaker's voice), what a child says playing I spy in the scenes (`ispy`: the lines,
the clues and each thing's find and reveal of companion/ispy, in the voice of each child who may play in the scene;
companion/SCENES.md, "I spy"), then the stories at A2 (`stories A2`), the drills above A1
(`drills A2+`) and the stories at B1 and up (`stories B1+`). A story's teller speaks their lines and replies in their own designed voice once the voice
profiles have it ready (`@janez`), else in their speaker; the right choices are in the default voice. A riddle is in
its teller's voice the same way; a drill's other texts (a transformation's sentence and answer, a rapid answer and the
numbers generated, a build's steps) are in the female narrator's. A voice of one's own is ElevenLabs only: the local
worker never makes it. The car mode plays a story or a drill's item only when its clips exist (the app voices a scene's
line when it is played), so the A1 tellings and drills (Jan's level) come before the scenes.

```bash
companion/bin/voice-build                  # dry run: texts, characters, cached, missing, quota left
companion/bin/voice-build --run            # generate the missing clips (and the Slovene narrators' clips in Nina's and Luka's voices)
companion/bin/voice-build --run --upgrade  # also replace local clips while the quota lasts
companion/bin/voice-build --upgrade-model  # dry run: the ElevenLabs clips by model, those of an older one, what --run would make and its credits
companion/bin/voice-build --upgrade-model --run --max-chars 20000   # the most played of them again in the current model
companion/bin/voice-build --run --revoice grandma   # a swapped voice: its clips again
companion/bin/voice-build --elevenlabs teacher       # dry run: a speaker's local (Gepard) clips, made while ElevenLabs couldn't; --run makes them in its ElevenLabs voice (never the narrators')
companion/bin/voice-build --run --max-chars 2000
companion/bin/voice-build --revoice-words --max 100        # dry run: old-style narrator words, the cost
companion/bin/voice-build --revoice-words --max 100 --run  # 100 of them again, most asked for first
companion/bin/voice-build --denoise        # dry run: the clips of a voice that hisses, not filtered yet
companion/bin/voice-build --denoise --run  # filter them (ffmpeg only, no characters)
companion/bin/voice-build --denoise --data <dir>   # another data directory (a copy)
companion/bin/voice-build --level          # dry run: every clip not at the level, each voice's loudness and gain
companion/bin/voice-build --level --run    # level them (ffmpeg only, no characters)
companion/bin/voice-build --edges          # dry run: every clip's silence before and after its sound, how many to trim
companion/bin/voice-build --edges --run    # trim them (ffmpeg only, no characters)
companion/bin/voice-build --restore-gepard ~/.local/share/lani/backups/<snapshot>        # dry run: the paced local clips
companion/bin/voice-build --restore-gepard ~/.local/share/lani/backups/<snapshot> --run  # back as the worker made them
companion/bin/voice-build --narrators      # dry run: the narrators' local clips not in their local voice yet (lone words: ana)
companion/bin/voice-build --narrators --run   # make them again with the local worker (resumable, no characters)
companion/bin/voice-build --tempo          # only with LANI_VOICE_GEPARD_TEMPO or _PAUSES set: the local clips not at that pace
```

`--revoice-words` makes only the old-style narrator words again, in their carrier sentence, within the
reserve. The daily cap doesn't hold it back, but it counts for the day, so the bridge doesn't add
another 40 on top (Jan's re-records have their own cap: they still go).

**Another learner's village** (a culture pack in another language): `LANI_CULTURE=friuli companion/bin/voice-build
--data <their data dir>` counts that pack's content in its language, with its voice cast; `--run` voices it. The
local worker speaks only Slovene, so the plan says it is "not for this village": what the ElevenLabs budget doesn't
cover waits for later, and a cast without voices plans nothing.

When the tutor publishes a pack, module, scenario or scene, the bridge voices its new texts in the
background (one at a time) and sends the app a `voice_updated` event. `voice_status` (MCP tool) and
`GET /voice/status` show coverage, quota and engines.

### Offline on the phone

The phone's audio comes in layers, so a day sounds the same without the node:

| Layer | What | Where |
|---|---|---|
| 1. The day's clips, got ready ahead | What the learner will likely hear today (and tomorrow, while the cap has room), downloaded on Wi-Fi | `files/voice-clips/` |
| 2. The clips played | Every clip a line played, kept until the cap needs the room (the least recently played go first) | `files/voice-clips/` |
| 3. The phone's offline voice | Piper, run on the phone by sherpa-onnx, for a line without a clip when the node can't be reached | `files/piper/<language>/` |
| 4. Android's text-to-speech | The last fallback, and the only one for a language without an offline voice | the system |

**The day's clips** (`audio/DayAudio.kt`, `app/DayAudioGather.kt`). When the app opens, once the village is loaded, and
again whenever the lines change (a pack learned, the village's day turned; at most once a minute), it writes what the
learner will likely hear today and tomorrow (`files/audio/day.json`), each line in the voices the app
plays it in (a villager's own voice first, then their archetype's, their gender's narrator, the female narrator):

1. the review cards due (today also the overdue ones), as the review asks them (the front and each form it accepts), in
   the day's companion's voice;
2. the next words of the packs under way (at most three packs; tomorrow the session after) and their examples, in the
   voice of whoever teaches them;
3. the dialogs of the day's happenings (the variant each plays that day; the storyteller's chapter tonight among them),
   each person's lines in their voices, the right answers and the replies;
4. the lines of everyone in the village that day (meeting the learner, small talk, cheering, goodbye, the gifts'; not a
   memory, which is said live) and the greetings of the day;
5. I spy's clues (those a round would give, of the rules introduced) in the scenes with a happening that day or a child
   of their own, not played out yet, in the voice of the child who plays there (the scene's own, else the one the day's
   dice sends by);
6. the grammar book's examples of the pages the learner practises (met, not secure yet);
7. the school's next units' introductions (the unit under way or next and the one after it in its level, while their
   intro isn't through): each step's explanation as its one clip in the learner's base (`lang` on `/voice/prepare`,
   [The school's introductions, read aloud](#voice)), its Slovene forms and its examples, in the teacher's voice.

A line with a placeholder (`{name}`) is said live, not got ahead. Each text is in the list once per voice, at most
3,000 a day.

**The job** (`audio/Prefetch.kt`, `audio/PrefetchRun.kt`): WorkManager, every 6 hours and when the app opens or the plan
changes (unless it ran in the last hour for the same plan; a new plan's run comes after one under way), only on an unmetered network (Wi-Fi), with the storage and the battery not low,
and only while charging when the learner says so. It needs no open app: it asks the node for the due cards again (`GET
/state`; the tutor adds cards in a session) and for its index (`GET /voice/index`), then:

1. asks the node to voice what its store lacks in a line's wanted voice (`POST /voice/prepare`, 20 at a time, today's
   first, within the node's own limits: not as a live line, `LANI_VOICE_ROAD_DAILY` characters a day); once the node voices
   none of a batch, it isn't asked again that run. So the prefetch also fills the store gradually;
2. takes each line's clips in the first of its voices that the store has whole, as the app finds them offline;
3. marks those files as the day's (`files/audio/keep.txt`): the cache never drops them to make room;
4. downloads them, four at a time, today's first, then tomorrow's while the cap has room. A clip the car's library has is
   linked, not downloaded (one file with two names, counted once), and a clip the car's getting ready is downloading is
   waited for (`audio/AudioFiles.kt`, `ClipFiles`): the two never fetch one twice. These downloads count on the node, as a
   line played does, so an old-style narrator word is queued for its carrier re-voice, and a Slovene narrator's local clip
   for its clip in Nina's or Luka's voice; the next run gets the new take.

What it did is in `files/audio/status.json` and the settings: when, the day's lines on the phone (in their wanted voice,
in another of theirs, missing), the files and megabytes downloaded, the lines the node voiced, whether the cap stopped it.
An older bridge without `/voice/prepare` (404): only what the store has.

**A re-voiced line keeps its old clip until the new one is here** (`audio/PreviousClips.kt`). A new take has a new file
name, so when the index changes (the app's refresh after `voice_updated`, the prefetch) the phone notes, for each line whose
URL changed, the earlier clip it still has (`files/voice-clips/previous.json`, the newest of a chain), and plays it
offline instead of the phone's voice; online the new clip downloads in the background for the next play, and a line
waiting on the node gets the old clip, not TTS, when the new one doesn't come in time. Once the new file is on the phone (a
play, the prefetch, the car's library) the old one is deleted, unless another line still plays it or it played in the last
minute; the day keeps a line's old clip while its new one is missing and counts the line as on the phone with it, each line
once; a re-record on request drops it on purpose, and an old clip the cap dropped is forgotten.

**The cap** (`audio/AudioBudget.kt`), in the settings: Off, 100 MB, 250 MB (the default), 500 MB or Unlimited. One number
for all of Lani's audio on the phone: the day's clips, the clips played and the car's library ("🚗 Za pot"), each file
once. The clips played go first, the least recently played first; the day's clips never; the car's library is never
dropped either (it was got ready on purpose), so getting the road ready takes what the cap leaves after the day's clips,
and what doesn't fit is left out like what can't be had (the sheet says so). The clips played keep at least 20 MB whatever
the car's library and the day's clips take, so a clip just downloaded to play is never dropped before it plays (until
0.2.208 the day's clips counted in those 20 MB: with the cap full and the day's over 20 MB, every new line fell to the
phone's voice). **Off**: no prefetch; the clips stream as before, the last 50 MB kept; the car's library
as big as it gets; the offline voice still speaks when it's on the phone. **Unlimited**: nothing is dropped. The clips are
in the app's files, not its cache, so Android doesn't clear them behind the learner's back (an app from before moves its
cache's clips there once).

**The offline voice** (`audio/Piper.kt`, `audio/OfflineVoices.kt`). The runtime is in the APK: sherpa-onnx's Android library
(Apache-2.0; onnxruntime linked in), for 64-bit ARM phones only (the debug build also for the x86_64 emulator). The voice is
not in the APK: one per language, through the node. The node fetches it from Hugging Face once per machine
(`bridge/src/offline-voice.ts`, `~/.cache/lani/piper/`, `LANI_PIPER_CACHE`), checks every file against its pinned SHA-256,
adds the metadata sherpa-onnx reads to the model (byte for byte what sherpa-onnx's own `vits-piper.py` writes), writes
`tokens.txt` from the voice's config and takes the espeak-ng data of its language only. The phone downloads the files and
checks each one's size and SHA-256 again, into `files/piper/<language>.part/`, and moves the folder into place only once all
are there; a download stopped halfway goes on where it stopped.

| Language | Voice (rhasspy/piper-voices) | Size on the phone | Licence of the voice |
|---|---|---|---|
| Slovene | `sl_SI-artur-medium` (Artur) | about 61 MB | CC BY 4.0 (the ARTUR studio TTS dataset, trained by ppisljar) |
| Italian | `it_IT-paola-medium` (Paola) | about 61 MB | CC0 1.0 (paolapersico1/Voice-Dataset-Italian) |
| German | `de_DE-thorsten-medium` (Thorsten) | about 61 MB | CC0 (Thorsten-Voice) |
| English | `en_GB-northern_english_male-medium` (a northern English man, for Lakeland) | about 61 MB | CC BY-SA 4.0 (OpenSLR 83) |

The espeak-ng data (GPL-3.0-or-later, like espeak-ng, which sherpa-onnx links in) comes from sherpa-onnx's own converted
Slovene voice (`csukuangfj/vits-piper-sl_SI-artur-medium`). The app offers the voice once after pairing ("🗣️ Prenesi glas
brez povezave (61 MB)? · Download the offline voice (61 MB)?", Wi-Fi recommended); the settings have it too (download, its
size, remove, its credit), and with it wanted the prefetch gets it again on Wi-Fi should it go.

It speaks when no clip of the line is on the phone and the node can't make one now (offline, an error, a clip that won't
play), before Android's TTS, and only in its own language: on a visit to a town in another language Android's TTS speaks
as before. Normal speed, and 🐢 at 0.75 through the voice's length scale (the speech slower, not the playback), then
played at the person's pitch and pace like a clip. Its speech comes out about 3.5 dB under the clips (−21.8 LUFS), so
it is made 1.5 times louder, its peak held under −1 dBFS (the sample below: −17.6 LUFS, the clips' level being −18).
The model loads on the first line and is let go after 5 minutes without one; what it said is kept as WAV (the newest
20 MB of the cache) and plays at once again. The car's quiz uses it too for a Slovene text the node can't voice, before
Android's voice.

Measured on the x86_64 emulator (two threads; a phone's CPU is slower), with QA's `offline-voice` step: the model loads
in 0.9 s; "Ko povezave ni, govori glas na telefonu." (40 characters) takes 0.17 s for 2.5 s of speech, at 🐢 0.21 s for
3.0 s. The voice came to the emulator in 5 s through the dev bridge (its cache filled from a local mirror).

**A day of Jan's**, measured on a copy of Jan's data (the dev bridge, October 2026; its voice worker pointed nowhere, so
nothing new was voiced): 1,128 lines for today (76 of the dialogs, 389 of the villagers, 415 of I spy, 36 of the packs,
212 of the grammar book; the cards were done that day) and 1,276 for tomorrow (45 cards among them). 582 of today's had
clips in the store (279 in their wanted voice, 303 in another of theirs); the rest (I spy's clues and grammar examples
not voiced yet, villagers' own voices) is for the node to voice as its limits let it. Today's and tomorrow's clips were
750 files, 31.3 MB, downloaded in the first run when the app opened; the clip cache came to 36.5 MB.

## Speech recognition

Speaking exercises use the phone's recognizer when it can take the learner's target language: the
pair's target, in its locale (`Lang.locale`: sl-SI for Jan, it-IT for a learner of Italian, de-DE,
en-GB). The phone remembers per language whether its recognizer takes it. When it can't, or
when Jan picks "🎯 Natančneje · More accurate", the app records the answer (AAC, 16 kHz, stops
after a pause) and sends it to `POST /stt?language=<target>` with the expected sentence; the bridge
passes it to a local Whisper worker (`stt-local/`, `127.0.0.1:8796`) and nothing is stored. The
worker has a model per language: the primary one for Slovene (`large-v3-turbo`, or the Slovene
fine-tune) and a general multilingual one for the others (`large-v3-turbo`); a language without a
model on the node answers `503` and the app keeps the phone. A request without a language is in the
village's language. The transcript is graded like the phone's, and words Whisper was unsure of show as
a pronunciation hint. `GET /stt/status?language=it` says whether the worker is up for that language
(and has it load the model, if it isn't loaded); without it, the app keeps the phone path and
self-rating. Setup, the models per language, measurements and the model comparison:
`stt-local/README.md`.

**Reading aloud** ("🎤 Beri na glas", [GAME.md](GAME.md#reading-aloud)) sends each take to `POST /stt` twice, with the
text as the prompt and without it, and grades word by word: on the prompted transcript, with the plain one as a second
opinion where the prompt may have mended a slip (`data/ReadAloud.kt`; why, and the recordings it was decided on:
[stt-local/README.md](stt-local/README.md#reading-aloud)). A take is up to 28 s (the worker takes 30), and a pause of
3 s ends it. Without the node, the phone's recognizer hears it once.

**Grading in the target language.** What counts as the same answer, a slip of the keyboard or almost
right is a table per language (`android/app/src/main/resources/grading/<code>.json`, `data/Spelling.kt`), so
another language is a new table, not new code: Slovene's č/š/ž; Italian's accents (è for e, perché
typed perche, perchè or perche') and apostrophes (un amica for un'amica, la acqua for l'acqua), each
almost right with its hint; German's ae/oe/ue/ss for ä/ö/ü/ß (almost, the umlaut hint); English's
contractions (don't = do not). Choosing a dialog's answer by voice folds the same letters. Speech
labels name the target language (`{targetLang, select, …}` in the string tables).

## Word lookup

In the app's dialogs, Jan taps a word and sees its base form, its meaning and the grammar of the form,
and can add it to their words (a review card). The meaning is in the learner's base language: English for Jan,
Slovene for the second learner (Italian from Slovene), German for someone learning Slovene from German. The bridge looks the word
up; no LLM is involved, except to fill a gap (below).

**`GET /lookup?w=gozdu&line=V gozdu je tiho.&en=It's quiet in the forest.`** (`line` and `en` are
optional) answers:

```json
{ "word": "gozdu",
  "entries": [{ "lemma": "gozd", "pos": "noun", "gloss": ["forest"], "gloss_lang": "en", "grammar": "locative singular",
                "gender": "m", "source": "wiktionary", "item_id": "vocab_word_gozd", "known": false, "example": null,
                "emoji": null }],
  "attribution": "Wiktionary (CC BY-SA 4.0) via kaikki.org" }
```

With `&base=de`, the same entry has `"gloss": ["Wald", "Forst"], "gloss_lang": "de", "gloss_via": "pivot",
"gloss_en": ["forest"]`.

- At most 5 entries, one per lemma and part of speech; an expression is one entry, whatever its part of speech. No
  entries: not found (still `200`). `400` when `w` is missing, empty or longer than 40 characters, or `language` or
  `base` isn't a language code (or `base` is the word's own language).
- `base` (optional): the language the meanings are wanted in; the app sends its pair's base. Without it, the
  learner's base (their profile's). `language` (optional): the word's language when it isn't the village's: on a
  visit, the host town's (see below).
- `source`, where an entry comes from: `pack` (a curated or tutor word pack: its gloss written for the learner, in
  their base language, its emoji, example and review item), `tutor` (the tutor's glosses, `publish_gloss`),
  `village` (a villager's name, also declined: "Tonetu" → "Tone — the blacksmith"; and the learner's), `extra`
  (the supplement) and `wiktionary`. A tutor gloss may carry a `note`.
- The order:
  1. An expression of the line that the word is part of (2-4 words, from the packs, the supplement, the tutor's
     glosses or Wiktionary): "prav" in "Ravno prav." is "ravno prav — just right; just in time"; a pack phrase
     matches without its punctuation ("Lahko noč!").
  2. The entries whose glosses the translation `en` has ("Vidim vas." / "I see you": the pronoun before the village).
  3. Pack words, then the tutor's glosses, then the others. A pack word the tapped word is only a form of (not the
     word, its plural or a phrase of the line) comes after the pack words the word is ("debel": thick, then deblo),
     and, without a translation, after the word itself as the supplement or the tutor wrote it ("ravno": the adverb,
     just, then raven, flat).
  4. Among the others: the village's people; the word itself (its own lemma) and the supplement's entries; then
     Wiktionary's readings of it as a form of another word ("prav" alone: the adverb before a genitive plural of
     pravo); Wiktionary's names last ("lepa": nice, not a cat's name), but first when the word is capitalized inside
     the line ("Pozdravi Samo.").

  The word before a noun narrows its case ("v gozdu": locative, not dative).
- `item_id`: a pack word's own item (`vocab_<pack>_<word>`, as `POST /packs/:id/learn` makes it), else
  `vocab_word_<lemma>` (lowercase, č š ž as c s z, other characters as `-`); none for a name. `known`: that item, or
  a vocabulary item with the same word, exists.
- `gloss_lang`: the language of an entry's gloss: the base, when the pack word, the dictionary or the tutor has a
  meaning in it; else English, with `fallback: true`. `gloss_via` says how a meaning in the base was found: `direct`
  (the base language's own Wiktionary explains the word), `translations` (a translation table between the two
  languages), `pivot` (through English) or `claude` (the tutor wrote it); `gloss_en` is the English it stands for. A
  Slovene pack word has only Slovene and English: for another base it takes the dictionary's meaning of its lemma. The
  word card says "po angleško: water" to a learner who reads Slovene and gets the English, adds "✍️ Učitelj bo napisal
  pomen po slovensko" while the tutor writes one, and flags a Claude-written meaning "🤖 strojno napisano ·
  machine-written".
- A village in another language uses `companion/lexicon/<language>.json` when there is one, else the dictionary
  downloaded for it (below); without either, the answer has the pack words, the villagers and the tutor's glosses only.
- **On a visit** the words are the host town's language, explained in the visit's base: the learner's base, or their
  own target when the town speaks their base (the second learner, Italian from Slovene, reads a Slovene town in Italian). The
  app sends both (`language`, `base`); the bridge loads that language's dictionary at its first lookup (bundled, or
  downloaded from `LANI_LEXICON_URL`; that first lookup waits for it up to `LANI_LEXICON_WAIT_MS`, 20 s) and keeps
  it in memory. It looks in that language's curated packs and its culture packs' packs, and in the tutor's glosses of
  its words (`<data>/app/lexicon.<language>.json`); the host's villagers aren't among the entries.
- Italian (and French) elide a word before an apostrophe: a tapped "l'acqua", "dell'acqua" or "un'amica" is looked up as
  the word after it, unless the whole is an entry of its own ("dov'è").
- `GET /lexicon/status`: the village's language, where its dictionary came from (`bundled`, `download`, `cache` or
  none), its version, lemmas and forms, the base languages it has meanings in (`bases`), the learner's base and how
  many lemmas have a meaning in it (`base_meanings`), the download's state, and the same for each language looked up on
  a visit (`visits`).

**`POST /words`** `{sl, en, pos?, item_id?, example_sl?, example_en?, from?: scene|roleplay|villager|chat|reading,
client_id}` → `{item_id, added: true}`, or `{item_id, added: false}` with the item that has the word already.
`GET /words` → `{from: […]}` lists the origins it takes: the app sends `reading` (a word of the reading corner, a book
from the chest) only to a bridge that lists it, once asked per bridge (`WordsController.sourceFor`); an older bridge
answers 404 and would refuse `reading` with a 400, so it gets `villager`, as before. The
word becomes a vocabulary review item due tomorrow, through `update-db.py` with `record_session: false`: no
session, no minutes, no streak day (see `docs/DB_SCRIPTS.md`); the sentence it was met in is kept on the item, and
`source: "lookup"`: the word was added, not answered, so the app does not count it as learned. A
pack word's `item_id` gives the same card as learning it from its pack. The app gets `words_added {item_ids}`; the
tutor hears the words added in one line, gathered (5 minutes after the first; `LANI_WORDS_NOTE_MS`), marked as
already persisted.

**Ask the tutor.** For a word not found, the app sends a chat message with `data.lookup {word, line, en,
language?}` (`language` on a visit). The tutor explains it with `reply` and keeps the explanation with
`publish_gloss {form, lemma, pos?, gloss (1-3), grammar?, note?, gloss_lang?, language?}` in `<data>/app/lexicon.json`
(another language's word: `lexicon.<language>.json`); the app gets `lexicon_updated {word, language?}`, and the next
lookup finds it. `gloss_lang` is the language of the glosses (default: the learner's base for that word's language);
a gloss kept before glosses had one is in the learner's base.

**The tutor fills the gaps.** When a lookup's first entries have only the English (for a learner whose base isn't
English), the bridge tells the tutor: `gloss_gaps` lists the words, their lemma, part of speech and English, and what
to call (gathered for `LANI_GLOSS_GAP_MS`, 3 s; each lemma once a run, at most 30 an hour; `LANI_GLOSS_GAPS=off`
turns it off). The tutor's `publish_gloss` with `gloss_lang` becomes the meaning of the dictionary's entry of that
lemma in that base (`gloss_via: "claude"`, with the dictionary's grammar and gender), not an entry of its own; an open
card looks again at `lexicon_updated`. A tutor gloss shows only in its own language, or as the English fallback.

**The dictionary.** `lexicon/sl.json` is built from English Wiktionary's Slovene entries, as kaikki.org extracts
them (the JSONL of https://kaikki.org/dictionary/Slovene/):

```bash
companion/bin/lexicon-build --from kaikki.org-dictionary-Slovene.jsonl --lang sl
companion/bin/lexicon-build --coverage     # how much of the curated content the lexicon knows, what it misses,
                                           # and the words it knows only as a form of another word
```

It keeps each lemma with up to 3 short glosses and a noun's gender, and every inflected form with its lemma and
grammar. The grammar is written to be read: readings that differ only in their cases or only in their person,
gender or number are one ("dative/locative singular", "genitive dual/plural", "second/third-person dual present"),
all three genders are left out, and "any case" stands for all of them. The builder fixes what Wiktionary's tables tag
wrongly: a finite verb form has no gender ("second-person singular imperative"), an l-participle is not also a
converb, the forms of biti, imeti and hoteti are negative only when they begin with n- (so "bo" is "third-person
singular future", not "negative present"), and a form that is the same definite and indefinite says neither. Rows
tagged `table-tags`, `inflection-template` or `error-…` are not forms. The build is deterministic: the same extract
on the same day gives the same file. Forms are compared without Wiktionary's tonal accents (potọ́ka → potoka),
with the caron (č, š, ž). September 2026: 5,774 lemmas, 43,097 forms, 322 grammar strings; 1.75 MB with its meanings
in Italian and German (1.40 MB without; see below). The builder reads an extract line by line, gzipped or not, or
from standard input (`--from -`), so a large one builds the same way.

**Italian, German and English are downloads.** They are too large for the repository (plan 1, "Jan's decisions
(2026-09-26)"), so a bridge downloads the ones its learners use. They are built from kaikki.org's extracts, gzipped
(1.05 GB in all, about a minute to download; the node keeps them in `~/.cache/lani/kaikki/`, the built files in
`~/.local/share/lani/lexicon/`):

```bash
k=~/.cache/lani/kaikki
curl -o $k/en-English.jsonl.gz https://kaikki.org/dictionary/English/kaikki.org-dictionary-English.jsonl.gz  # Italian, German, Slovene alike
curl -o $k/dewikt-raw.jsonl.gz https://kaikki.org/dewiktionary/raw-wiktextract-data.jsonl.gz
curl -o $k/itwikt-raw.jsonl.gz https://kaikki.org/itwiktionary/raw-wiktextract-data.jsonl.gz
# once per extract, what it gives the meanings (~/.cache/lani/lexicon-work/, 73 MB; all six in 30 s)
companion/bin/lexicon-build --index $k/en-English.jsonl.gz --edition en --name English --url https://kaikki.org/dictionary/English/
companion/bin/lexicon-build --index $k/en-Italian.jsonl.gz --edition en --name Italian --url https://kaikki.org/dictionary/Italian/
companion/bin/lexicon-build --index $k/dewikt-raw.jsonl.gz --edition de --name raw    # and German, Slovene, itwikt-raw (--edition it)
# a dictionary with its meanings, its manifest and the gzipped file; the bundled Slovene one gets its meanings
companion/bin/lexicon-build --from $k/en-Italian.jsonl.gz --lang it --out <dir>/it.json --meanings --manifest --gzip
companion/bin/lexicon-build --meanings --lang sl
companion/bin/lexicon-build --coverage --lang it --dict <dir>/it.json
LANI_LEXICON_URL=<dir or https://…/lexicon/>        # in the learner's profile env, or ~/.config/lani/keys.env
```

Nothing large has to be kept: `curl -s … | lexicon-build --index - …` streams (a `.gz` source: `gunzip` first).

| | lemmas | forms | JSON | gzipped | heap in the bridge | built in |
|---|---:|---:|---:|---:|---:|---:|
| `sl` (bundled) | 5,774 | 43,097 | 1.75 MB | | small | 2 s (meanings) |
| `it` | 201,097 | 684,211 | 38.0 MB | 6.5 MB | 166 MB | 57 s |
| `de` | 102,531 | 468,234 | 24.7 MB | 4.7 MB | 112 MB | 59 s |
| `en` | 123,300 | 279,076 | 29.9 MB | 7.0 MB | 138 MB | 79 s |

- `--manifest` writes `<language>.manifest.json` next to the file: `schema` `lani.lexicon/v0`, `language`,
  `version` (the build date and the start of the sha256), `file`, `sha256`, `size`, the licence, the attribution and
  counts, the bases the lemmas have meanings in (`bases`: how many, their share, how found, the content words'
  share) and the Wiktionaries they come from (`sources`). `--gzip` makes the file downloaded `<language>.json.gz`.
- `LANI_LEXICON_URL` (unset: nothing is downloaded) is an `https://` base URL or a local directory holding the
  manifest and the file. A bridge that serves the app and has no `companion/lexicon/<language>.json` fetches the
  manifest at start (another town's language: at the first lookup of a visit there), then the file when the cache
  doesn't have that version, checks its size and sha256, and keeps both in `${XDG_CACHE_HOME:-~/.cache}/lani/lexicon/`
  (`LANI_LEXICON_CACHE`). A file that doesn't match its manifest is refused; when the source can't be reached, the
  cached copy serves. Lookups work without Wiktionary until the dictionary is there (`GET /lexicon/status`).
- Italian: the forms drop the stress marks Wiktionary writes inside a word (casìna → casina) and keep the accent
  Italian writes (città, perché); the grammar leaves out the indicative ("first-person plural present", "… present
  subjunctive"). Of the Italian content's words it knows 98.5 %; what it lacks is mostly the region's names (Collio,
  Gorica, Carnia, Cormons, Castelmonte) and foods (frico).
- English Wiktionary has 1.4 million English entries, most of them rare words and names: the English dictionary keeps
  the words it translates into any language (and their forms), no names (830,000 lemmas and 120 MB otherwise). It knows
  93 % of the lakeland pack's words; its glosses are English definitions.
- German: 91 % of the kaernten pack's words (the rest are mostly Carinthian words and names).

**Meanings in the learner's base language.** Wiktionary's glosses are English; the second learner reads Slovene. Each lemma
gets its meaning in the other three languages (`"meanings": {"de": {"gloss": ["Haus"], "via": "direct"}}` at the end
of its row: an older bridge reads the file as before), from three Wiktionaries (`bridge/src/lexicon-meanings.ts`),
the best evidence first:

1. **direct**: the base language's own Wiktionary explains the word, in a few words (German Wiktionary's Italian
   "casa": Haus, Zuhause). German Wiktionary often writes "definition; translation" ("größeres fließendes Gewässer;
   Fluss"): the translation is taken. A longer gloss is a definition and comes last.
2. **pivot, confirmed**: the lemma's English gloss ("house"), and the translation table of that English word that
   lists the lemma itself ("human abode": Italian casa, Slovene hiša): the sense shown, and a table that says so.
3. **translations**: a table between the two languages: the target's own Wiktionary lists the word's translations
   (German Wiktionary's "Haus": Slovene hiša), or the base's lists it first as a translation of one of its own words
   (Italian Wiktionary's "casa" lists German Haus). Italian Wiktionary copies a page's tables to each part of speech
   ("grazie" the interjection and the noun): a copied table stays with the one of the most senses.
4. **pivot, together**: a third language's table lists the lemma and base words (English "forest": Italian bosco,
   Slovene gozd).
5. **pivot, by sense**: the English gloss's table its other words single out ("bank (of a river)": "edge of river").
   Never a guess between two senses ("bank" alone: none), never an English word's only table ("present" has a table
   for the time, none for the gift).

Dialect words (tagged "Alemannic German", …), obsolete ones, German Wiktionary's gender marks ("Engel m") and
articles ("das Haus") are left out; Slovene loses Wiktionary's tonal accents (dóber → dober).

| target ← base | lemmas | content words | pack words | how found (lemmas) |
|---|---:|---:|---:|---|
| sl ← de | 81.6 % | 96.5 % of 2,396 | 97.6 % | pivot 2,911, direct 329, translations 212 |
| sl ← it | 79.8 % | 95.8 % | 98.1 % | pivot 3,182, direct 181, translations 13 |
| it ← sl | 7.7 % | 87.0 % of 1,712 | 94.2 % | pivot 14,152, translations 121 |
| it ← de | 22.1 % | 95.7 % | 98.9 % | pivot 25,155, direct 9,494, translations 6,126 |
| de ← sl | 18.1 % | 87.0 % of 1,874 | 88.9 % | pivot 13,305, translations 3,655 |
| de ← it | 43.4 % | 97.0 % | 97.3 % | pivot 25,642, direct 6,959, translations 8,057 |
| en ← sl | 11.7 % | 88.7 % of 1,751 | 92.3 % | translations 10,242, pivot 4,220 |
| en ← it | 48.0 % | 98.9 % | 98.2 % | translations 37,590, direct 17,582, pivot 4,014 |
| en ← de | 55.3 % | 99.3 % | 98.5 % | translations 59,708, direct 8,124, pivot 371 |

Every lemma has its English (an English dictionary's: its definition). "Content words": the different words of the
curated content (scenes, packs, culture packs; `lexicon-build --coverage`) the dictionary knows, with a meaning in the
base in one of their readings; "pack words": the word packs' words (the most frequent). The share of all lemmas is low
for the large dictionaries: most of their lemmas are rare words. How good the pivot is: where a table between the two
languages has a meaning too, the confirmed pivot shares a word with it for 96 % (sl ← it), 92 % (sl ← de), 88 %
(de ← sl), 79 % (de ← it) and 77-78 % (it ← de, it ← sl) of the lemmas; the rest are mostly synonyms (of 25 it ← de
read by hand, 23 were right). "Together" agrees for 51-93 %, "by sense" for 36-41 % (few lemmas). `--disagree <file>`
lists the disagreements.

Weak spots: Slovene is thin in all three Wiktionaries (it, de and en ← sl reach 87-89 % of the content's words; what
they lack is mostly function words without a Slovene counterpart, like the articles il, della, the, and everyday words
like lovely, carefully, gern); Italian Wiktionary's Slovene translations are unreliable (libertà: Croatian sloboda),
so the pivot ranks before them; German Wiktionary glosses some English words with lists (went: "gehen, sich
fortbewegen, fahren, fliegen…"); homographs share a row (biti: sein; schlagen); a German separable verb's form ("fängt
… an") isn't found under its verb.

**The tutor fills the rest** (above): a lookup that has only the English tells the tutor, whose meaning is kept with
the learner's glosses and flagged machine-written. Reviewed additions flowing back into the shared dictionaries is the
next step.

**Licence.** Wiktionary's text is licensed CC BY-SA 4.0: English, German and Italian Wiktionary, via kaikki.org. A
dictionary's `meta` (and its manifest) names the licence, the attribution and the Wiktionaries its meanings come from
(`sources`), and every lookup that uses it carries the `attribution`, which the app shows. The dictionaries are data
apart from the code: the bundled `sl.json`, the downloads outside the repository.

**The supplement.** `lexicon/sl.extra.json` is written by hand:

- the words the curated content uses that Wiktionary lacks, each with the forms met and their grammar ("prinesel" →
  prinesti, masculine singular l-participle; "svidenje" → na svidenje);
- the usual reading of a few frequent forms (je, sem, si, vas, hvala), which comes first;
- the words Wiktionary knows only as a form of another word, where they are words of their own: prav (Wiktionary:
  only a genitive plural of pravo), ravno, samo, več, bi, da, to, pod, sredi, lepo, odlično, ljudje, …;
- the content's expressions, which a lookup finds around the tapped word: ravno prav, dober dan, lahko noč, ni za
  kaj, hvala lepa, kaj pa ti, na zdravje, ne vem, rad bi, v redu, …;
- with `"override": true`, a better gloss for a lemma whose Wiktionary gloss starts with a rare sense (babica:
  grandmother, not midwife; mesto: town; za, do, po). It replaces Wiktionary's gloss for every form of that lemma
  and part of speech; the grammar stays Wiktionary's, and the answer keeps the attribution.

An entry's form with `null` grammar takes Wiktionary's grammar of that form when Wiktionary has it. Names and pure
interjections are left out. It is machine-written and not reviewed by a native speaker (`"reviewed": false`). Of the
11,977 words in the content's texts, Wiktionary knows 81.9 %, with the supplement 93.7 %, with the pack words and the
villagers' names 98.4 %. When new content comes, `--coverage` lists the words none of them knows, and the words
known only as a form of another word, to check for the ones that are words of their own.

### Words in an exercise

Every exercise (a review, a module, a challenge, a pack's run, the grammar book's practice, a visit's run: all
`ExerciseRun`) lets Jan look up its Slovene words as the dialogs do (`ui/ExerciseLook.kt`, the rule in
`ui/words/ExerciseWords.kt`): a tap on a word of the prompt or of the sentence with the gap opens its card; in the
answers (a choice's options, a reorder's tiles, a multi's options) a long press, since a tap chooses. Only the words in
the language learned: the English half of "Kam gre Luka? · Where is Luka going?", a translation in brackets and an
English meaning have none. Looking up is free: no hint, no lower grade. A word added from the card ("➕") becomes a
review card as from a dialog (`from: scene`), its sentence with the gap filled as its example.

**Never the answer.** While the exercise is open, nothing it tests can be looked up (`ExerciseTaps`, per type, tested in
`ExerciseWordsTest`):

| Exercise | Can be looked up before the answer | Can't |
|---|---|---|
| flashcard | its word once turned over | its word before |
| choice, multi: a sentence with a gap | the sentence's words | the lemma or hint in brackets ("(sesti)"), the options (the forms that fill it) |
| choice, multi: options in English (meanings) | nothing | the Slovene asked about: the prompt is what's tested |
| choice, multi: options in Slovene | the prompt's Slovene ("Kam gre Luka?", "Kako rečeš …?"); the options by a long press when the prompt asks in Slovene, every option has two words or more and nothing is quoted: a reply, a place | what the prompt quotes ("«good day»": the meaning asked for), single-word options, the words the options differ in (`ChoiceDiff`: "en / eno / ena čaj") |
| cloze | the sentence round the gap | what brackets add; the gap has nothing |
| reorder | the tiles, by a long press, for their meaning (the card shows no line, so not their order); the prompt's Slovene | – |
| translate | the source's Slovene words while the answer is Slovene | a Slovene source to say in English (or an answer that reads as either: "pes" → "dog") |
| speak | the prompt's Slovene (a sentence with a gap: round it), the model sentence once shown | – |
| scenario | the line and the scene while the answer is a reply (options in Slovene, or typed) | the line while the options are its meanings |
| dictation | nothing is shown | – |
| the school's gap text, table, transform list ([COURSE.md](COURSE.md)) | the text round the gaps (in its sentence), a table's row labels and given cells, a list's lines given | what brackets add (a lemma, a cue); the gaps, the cells and the lines to write have nothing |
| the school's sort | its prompt | the words to sort: their card says their gender (once checked, by a tap) |

A long press on a word of an answer that can't be looked up does nothing (a soft buzz): no card, and the option isn't
chosen. While the exercise is open, a word the learner has shows no table of its forms on its card (`WordQuery.forms`):
one of them may be the answer. After the answer everything can be looked up, the tested word too, by a tap also in the
options; the feedback's right answer, its whole sentence and its explanation too, and the card shows the forms again.

Which text is in which language: by its parts (apart by " · " and line breaks; quotes, `*emphasis*` and brackets apart),
each read as Slovene by its letters (č, š, ž) or frequent words (`Spoken`), by a clip of it in the voice store, or by
all its words being the learner's own; a part that reads as either goes by the exercise (a sentence with a gap is
Slovene, a meaning to translate isn't). Where it can't tell, nothing is looked up.

**New words dotted.** A word the learner hasn't met has a faint dotted underline in the text's own colour (both themes;
`WordText`'s `unknown`); the words they have look plain. Met: a word of any of their cards (the deck's `pool`: a
phrase's words each, a pack's words once learned, a word added from a dialog), and every form of the lemmas among them
that the phone has from `GET /forms` ("mizo" is "miza"'s; `KnownWords`, tested in `KnownWordsTest`). A name inside a
sentence (capitalized after a word) isn't marked. Another language's deck (a review of it at home, a visit's run) goes by
its own cards, and its words are looked up in its language (`WordQuery.language`).

**The tip.** The first time an exercise has words to look up, "👆 Tapni besedo in poglej, kaj pomeni (pikice: nova
zate); v odgovorih jo pridrži · Tap a word to see what it means (dots: new to you); in the answers, press and hold it"
shows under its prompt, until closed, a word looked up or the next exercise; once (`DialogPrefs.exerciseWordHintSeen`).
TalkBack offers each word that can be looked up as an action ("Poišči «gozdu» · Look up «gozdu»"), as in a dialog.

**Explanations in shape.** An exercise's `explain` (and the grading hint) shows as markdown in the feedback, a long one
scrolling in the card while its buttons stay. One written in markdown shows as written; a plain one is put in shape
without rewording (`ui/ExplainFormat.kt`, tested in `ExplainFormatTest`): one sentence a line, a sentence of
"X = Y" pairs ("I work = delam, you work = delaš") a small table of two columns, a line's leading "word:" in bold,
endings written with their dash ("-m", "(-a)") in bold. Its Slovene words can be looked up word by word (a part with an
English word in it isn't Slovene as a whole: "dela: no -m and …"; the endings never). How to write them:
`.claude/skills/lani-studio/reference/module-spec.md`, "Explanations" (and `publish_module`'s description).

### Word forms

A word Jan has (a review card whose front is a word: "sesti", "hiša") has its forms, and the app's review asks them once
the meaning is known: „Jaz ___ na klop." (sesti) → sedem. Each form belongs to a page of the grammar book, and is asked
only once that page's rule is introduced (the app decides, from the pages' mastery). The bridge says what the forms
are, which page each is on, and where the content says them (`bridge/src/forms.ts`).

**`GET /forms?w=sesti&w=hiša`** answers, for each word, an entry per dictionary lemma written so (a noun, a verb, an
adjective; `lep` is an adjective, nice, and a noun, glue):

```json
{ "language": "sl",
  "words": [{ "word": "sesti", "entries": [{
    "lemma": "sesti", "pos": "verb", "gloss": ["to sit down"], "gender": null, "aspect": "pf",
    "partner": { "lemma": "sedeti", "aspect": "impf", "kind": "position", "gloss": ["to be sitting, to sit"] },
    "table": "wiktionary",
    "slots": [
      { "key": "pres.1sg", "forms": ["sedem"], "grammar": "first-person singular present", "page": "glagoli-sedanjik",
        "lines": [{ "sl": "Lahko sedem na zapeček?", "en": "May I sit down on the stove bench?", "form": "sedem", "from": "scene:v-hisi" }] },
      { "key": "pres.1du", "forms": ["sedeva"], "grammar": "first-person dual present", "page": "dvojina", "needs": ["glagoli-sedanjik"] },
      … ] }] }],
  "attribution": "Wiktionary (CC BY-SA 4.0) via kaikki.org" }
```

- **Slots**, in this order: a verb's present (`pres.1sg` … `pres.3pl`, then the dual `pres.1du` …), negative present
  (`neg.*`: nisem, nimam, nočem), future (`fut.*`: biti's bom …), l-form (`past.m.sg` sedel, `past.f.sg` sedla,
  `past.n.sg`, `past.m.pl`, `past.f.pl`, `past.m.du`, `past.f.du`: the feminine and neuter dual) and imperative
  (`imp.2sg`, `imp.2pl`, `imp.1pl`, `imp.1du`, `imp.2du`); a noun's six cases (`nom`, `gen`, `dat`, `acc`, `loc`,
  `ins`) in the singular, the plural and the dual (`loc.sg`, `ins.pl`, `gen.du`); an adjective's nominative
  (`adj.m.sg`, `adj.f.sg`, `adj.n.sg`, `adj.m.pl`, `adj.f.pl`) and comparative (`cmp.m.sg`). Wiktionary's grammar
  strings are read back into them ("dative/locative singular" → `dat.sg`, `loc.sg`); an infinitive, a supine, a
  participle, a vocative, a definite form are no slot. `forms`: the slot's standard forms (the first is shown as the
  answer; Wiktionary's alternatives, gozdi and gozdovi, are both right); `also`: accepted too, never shown (bojo).
- **Pages**: the present's endings (`glagoli-sedanjik`; biti's own page `biti`, imeti's and iti's `imeti-iti`), the
  negative's `zanikanje`, the future's `prihodnjik`, the l-form's `pretekli-cas`, the imperative's `velelnik`, each
  case's (`imenovalnik`, `rodilnik`, `dajalnik`, `tozilnik`, `mestnik`, `orodnik`), the plural's `mnozina` (the
  nominative plural; another case's plural needs it), the dual's `dvojina` (needing the tense's or the case's), the
  adjectives' `pridevniki-ujemanje` and the comparative's `primernik`. A slot of a page the book lacks isn't served.
  `page` is where a wrong form counts; `needs`, the pages that must be introduced too.
- **Lines**: up to 3 sentences of the content per slot, the shortest first, where that form stands as that word: the
  scenes' dialogs (what the people say, the learner's right choices and the replies to them; never a wrong choice),
  the stories, the readings, the grammar pages' examples, the packs' examples; none with a placeholder ({n}), longer
  than 120 characters or 14 words, or without an English translation. The form is in the line once, with no other
  form of the same word. A form of another word too ("sedi": sesti's imperative, sedeti's present) must be this
  word's by the line's translation, by its glosses' words and then by how much of a gloss it has ("The king sits at
  the table": sedeti; "Sit down, please!": sesti); a form of biti too (je: jesti's, and the auxiliary of "je
  pojedel") is biti's. Within the word, the form must be of one page there: a preposition before it narrows the cases
  ("pri hiši": the locative), dva, dve, oba, obe make it dual, biti's form beside an l-form its number ("sta sedla":
  the dual), the translation tells a present from an imperative ("sits", "is sitting"; "Then sit down", "Let's go"),
  and an l-form with bom or bi (the future, the conditional) is no past line. The lines are read once and kept 10
  minutes, or until the tutor publishes something with lines in it.
- No entries: a word the dictionary doesn't know, or whose Wiktionary entry has no table (krožnik, šotor). `400`
  without `w`, with more than 40, one longer than 80 characters, or `language` other than the village's.

**The forms book.** `lexicon/sl.forms.json` is written by hand (machine-written, not reviewed by a native speaker:
`"reviewed": false`, `"machine_written": true`):

- `tables`: the everyday irregular verbs, slot by slot, each replacing Wiktionary's forms of that word (`"table":
  "checked"`): biti (Wiktionary's merges biti, to beat: bijem, bije), imeti, iti, priti, jesti, vedeti, dati, hoteti
  (its Wiktionary table has odd rows), reči, moči, spati, vzeti and piti, with their negative present where they have
  one (nisem, nimam, nočem) and biti's future. Standard Slovene in `forms`, the spoken forms a native speaker says too
  in `also` (bojo, grejo, jejo, vejo, reko, mam; gremo and greva for let's go): accepted, never marked wrong, never
  shown as the answer.
- `partners`: verbs that pair, `pf` the one done once, `impf` the one going on; `kind` `position` for a movement and
  the state after it (sesti, sit down: sedeti, be sitting; leči: ležati; vstati: stati), `aspect` for the same act
  once or going on (kupiti: kupovati, reči: govoriti, …), and a gloss where the dictionary has none or says more.
- `leave_out`: words whose forms aren't asked (stati: to cost, stanem, and to stand, stojim, in one entry).

Each entry is checked on its own (a wrong one is logged and left out); the file is read again when it changes. A
bridge from before it ignores it.

**A verb's partner in the lookup.** A verb of the pairs gets `aspect` and `partner` (`{lemma, aspect, kind,
gloss}`) in `GET /lookup` too. When both partners are found for the tapped form ("sedite": sesti's imperative,
sedeti's present and imperative), the earlier is `"here": true` when the line's translation fits it better ("Sedite,
sedite." / "Sit down, sit down": sesti), and the app shows the other as its partner, with what tells them apart, not
as a second entry of its own.

### Counting cards

"🔢 Štetje · Counting": once Jan has done the intro of the A1 unit `stevila` ("Ena ovca, dve ovci, pet ovc"; finished
or skipped as known), the review holds a card per noun they have learned, which counts it the four ways Slovene does:

| | m | f | n |
|---|---|---|---|
| 1 | en sin | ena kava | eno jajce |
| 2 | dva sinova | dve kavi | dve jajci |
| 3–4 | trije sinovi | tri kave | tri jajca |
| 5+ | pet sinov | pet kav | pet jajc |

**The card** (`app/.../data/Counting.kt`, `ui/CountingCard.kt`): the noun at the top (its pack's emoji, its base form,
what it means), then four steps one at a time: **1** → en / ena / eno; **2**, **3** and **5** → the whole phrase (the
numerals agree too: dva or dve, trije or tri). Each step offers three choices while the card is new: the right phrase,
the same numeral with another number's ending, the other numeral (dve kavi · dve kave · dva kava), never one the
dictionary accepts. Once the card is secure (two good reviews in a row, the last without a slip: quality 4 or more),
the steps are typed, graded as a cloze is (č, š, ž forgotten is almost, a wrong ending is wrong), with
"✋ Izberi raje · Let me choose" back to the choices. Each step is marked at once with the right line; step 1 says why
from the noun's end, as the unit `spol` teaches it, by the noun's real gender in the dictionary (kava ends in -a:
feminine → ena kava; oče ends in -e, but it is masculine: an exception → en oče; vas ends in a consonant, but it is
feminine). A right step goes on by itself, a wrong one at "Naprej · Next"; the step on screen is kept in view as the
ones above it grow (a typed one with its letters and "Preveri · Check" above the keyboard, whose ✓ checks too). At the
end all four lines together, said by the narrator in one clip ("ena kava, dve kavi, tri kave, pet kav", made on demand like any line: nothing is voiced
ahead, the car and the day's audio leave counting cards out). "📋 Štetje in spol · Counting and gender" under the noun
opens the cheat sheet at the pages `stevila-samostalniki` and `spol-samostalnikov` (as the top bar's 📋 does), the 💬 attaches
the card with its steps so far (`about_exercise.counting`: the noun, its gender, each step's line, options and answer;
`source.variant` `counting` or `counting_type`).

**Its quality and its rules.** All four right is quality 4 (typed 5), a č, š or ž forgotten 3 (typed 4), one step wrong
and the rest right 3 (almost), two wrong (or one and a č forgotten) 2, more 1; the cheat sheet opened during it keeps it
at 4 at most, as any answer's. The companion's reaction stays silent after the card (the narrator says the lines). The steps count on the grammar book: the counting
rule all four, the gender step 1 (and a wrong numeral alone in 2 or 3: dva kavi), the dual step 2 and the plural step 3
by their forms. POST /reviews' `counting` says how each card went (`[{item_id, lemma, typed, steps: [{n, right, kind?,
answer, expected}]}]`, read leniently: never a reason to refuse the review); the bridge writes each wrong step as a
mistake of its kind (`counting_noun_gender`, `counting_dual`, `counting_plural`, `counting_from_five`: the genitive
plural, an id without "genitive" so it lands on no A2 page) and names them in `review_done`.

**Which nouns, how many.** After each reload, while counting is on and the bridge says `counting` in GET /state's
`features`, the app names the nouns to the bridge (**`POST /counting`** `{words: [{word, item_id?}]}`, at most 12): its
learned one-word vocabulary cards the dictionary has as a noun with a gender and the four forms (the forms the phone
has, GET /forms), the best known first, none that has a card or the bridge refused today. The bridge
(`bridge/src/counting.ts`, `features/counting.ts`) makes at most `LANI_COUNTING_PER_DAY` (3) a day: a learned word of
the learner's, a noun of the dictionary (`lexicon.ts` its gender, `forms.ts` its nominative singular, dual and plural
and its genitive plural), each a review item of its own, `counting_<noun>`, due the day it is made, its forms kept in
the item's `counting` (`{lemma, gender, forms, lines, others, emoji?, meaning?}`) so the app asks it offline. Every form
of a slot is right; the line shows one that isn't the slot it is told from (dve sestri, not sestre; pet sester, not
sestra), then the one the content says (pet ovc, not Wiktionary's ovac). The irregular ones come out as the dictionary
has them (oče → dva očeta, dan → dva dneva, pesem → dve pesmi, človek → trije ljudje); a noun without a gender or one of
the four forms gets no card (mleko, vrata). It answers `{added: [{item_id, lemma, lines}], skipped: [{word, why}],
made_today, per_day}` and `words_added`. In the review a few due counting cards (at most 4) are spread among the
others; the rest wait for the next. `card.py show` tells how the app asks one.

### Numbers

"🔢 Števila · Numbers" (Jan: "Do we learn numbers in school at some point? Would be nice: a separate test module."): a
practice module of the numbers themselves, beside the counting cards (which count a noun). Its rules are
`app/.../data/NumbersPractice.kt` (pure), its words `data/SpokenNumbers.kt`, its screens `ui/numbers/` (the module's
screen, the run, the number pad: `NumeralEx.kt`), the rest `app/NumbersController.kt`.

**Where it opens.** The intro of the A1 unit `stevila` opens it (finished, or skipped as known: as for counting cards),
with its first two ranges. It opens from the unit's page ("🔢 Števila · Numbers", under the intro: its ranges, or the
numbers due again), and from Home's "Danes · Today": an idea under "Še · Also" when it wasn't played today, right after the
tutor's modules while numbers missed are due again, else after a word pack (only where there is room). Closed, its screen
says what opens it with the way there one tap away: "📖 Odpri uvod · Open the intro" (the unit's intro), or before the
class begins (the hamlet) "🏫 V šolo · To the school", which says what begins it. The intro's end says once "🔢 Števila so
odprta: vadi jih pri enoti in na domači strani. · The numbers are open: practise them at the unit and on Home."

**A run** is 10 tasks, the ways of asking mixed, never the same twice in a row while another is on, no number twice:

| way | asks | answered | on |
|---|---|---|---|
| 🎧 hear | a number said (the app's voice: the node's clip, made on demand, else the phone's) | its figures on the number pad | with a voice |
| 👀 read | its word ("sedemindvajset") | its figures on the pad | always |
| ✍️ write | its figures ("27") | the word typed (č, š, ž chips, 💡) | always |
| 🎤 say | its figures | said (the speak exercise; a recognizer's "27" is read as the word) | from a range the learner has, with something to listen |
| 📅 date | "Danes je ___ maj. (15.)" | the ordinal typed: petnajsti | the page `datumi` introduced (the unit stevila) |
| ⏰ time by ear | "ob pol šestih" said | 5:30 on the pad (17:30 too) | the page `kdaj-cas` introduced (the unit Kdaj?), with a voice |
| 🕰️ time in words | "⏰ 5:30" | "ob pol šestih" typed | the page `kdaj-cas` introduced |

The number pad: what is typed so far, large (TalkBack reads it as it changes), the keys 1–9, 0, ⌫ (and ":" for a clock
time), each at least 60 dp high, then "Preveri · Check". Each task is marked at once, as any exercise: the right answer,
the word said by the 🔊, and how it is built, in the learner's base ("**27** → **sedem** + **in** + **dvajset**: the ones
first (7 + 20), joined by in, in one word"; -najst for 11 to 19, -deset for the tens, the hundreds and thousands apart; a
date's ordinal; ob and -ih, half towards the next hour). The cheat sheet (📋) opens at the curated card "🔢 Števila" and
the page `datumi`; 💬 attaches the task (`about_exercise.source` kind `numbers`, its mode and number, `machine_written`).
The run pays the village as practice (heard: 🪵, read and written: 🌾, said: 📜, a date or a clock time: 🪨); its end
says what opened and what comes back, with "▶️ Še enkrat · Once more".

**Grading.** Figures are right or wrong ("027" is 27; a clock time on either half of the day). A word as the app grades a
typed answer (č, š, ž forgotten is almost), with what a number's word adds: the same letters apart or together ("sedem in
dvajset", "dvatisoč šestindvajset") is almost; a one-letter slip anywhere in it is almost (a number has no ending to get
wrong), but never when it is another number's word ("deset" for devet), and "dvasto" for dvesto is wrong; 1 to 4 bare
in a gender's form (en, eno, dve, trije) are right, the counting form is shown. A date's day and a clock's hour have their
endings: "petnajst" for petnajsti, "sedemi" for sedmi, "ob pet" for ob petih are wrong.

**The ranges grow one at a time:** 0–10 and 11–20 (with the intro), 30–90 (the tens), 21–99 (the "-in-" numbers:
sedem-in-dvajset), 100–999 (dvesto, sto enaindvajset), 1000–9999 (half of them years: dva tisoč šestindvajset). **The
next opens when 8 of the last 10 answers in the newest range are right** (an almost counts; dates and clock times count
on no range). The screen shows each range: ✅ open, ▶️ the newest with "6 od zadnjih 10 prav · 8 od 10 odpre 21–99" and a
bar, 🔒 the later ones ("odpre se po 11–20"). Of a run's new numbers 60 % are from the newest range, the rest from those
before; a number to say only from a range the learner has (the next one open after it).

**Numbers missed come back in the module's own schedule**, not as review cards: a number is no word the learner owns,
the module makes its tasks from rules, and a card for every number missed would fill the review with "27" and "72". A
wrong one comes back in the next run (the same day), then a day, three days and a week after each right answer, then it
is gone; wrong again, it starts over; an almost comes back the next day. At most 4 a run, the oldest first, spread through
it, asked the same way (heard ones read where there is no voice). The village state keeps it all (`GameState.numbers`:
the day each range opened, each range's last 10 answers, the numbers missed with their day and box, the runs), so another
phone has it; the bridge keeps it from an app that leaves it out (PUT /game), and `get_village` shows it (`numbers`: the
ranges open, the newest one's last answers, the numbers missed as asked, the runs).

**The tutor** hears each run as a `session_end` whose `data.numbers` has the ranges open (`opened_now`: one this run
opened), the newest range's score, each task (mode, what it showed, the answer, what was right, the verdict, the kind of a
wrong one) and `errors`: update-db.py's errors as they stand, which the tutor persists (lani-db-updater), so each wrong
answer is a mistake of its own kind in mistakes-db: `numbers_listening`, `numbers_reading`, `numbers_spelling`,
`numbers_order` (the tens first: "dvajset sedem" for sedemindvajset, 72 typed for 27; a said one by what was heard),
`numbers_speaking`, `numbers_ordinal_date` (on the grammar book's `datumi` by its tag "ordinal"),
`numbers_clock_time`, `numbers_clock_time_half` (half past counted from the hour before; on `kdaj-cas` by "clock_time").
The plain numbers' ids name no page. The channel guide's counting paragraph says the same (the in-app tutor's topic
"counting", tutor-export).

**The words** are rule-built, never a list: `SpokenNumbers.slovene` (0 … 999 999: one word up to 99, the ones first;
dvesto; the hundreds and thousands apart: sto enaindvajset, tisoč, dva tisoč), `SpokenNumbers.ordinal` (1 … 99: prvi,
drugi, tretji, četrti, sedmi, osmi; else the number + -i: enaindvajseti) and the clock's hours after ob (enih, dveh, treh,
štirih … sedmih, osmih … dvanajstih; half: ob pol šestih). Speech keeps to 0 … 999 (a heard "1991" must still match a
text's figures), so a year said is right as its figures too. Machine-written, not checked by a native speaker yet: the
module's screen says so ("🤖"), the tutor's data has `machine_written`, and a word that looks wrong is fixed in the app's
code. `NumbersPracticeTest` holds a hand-checked table of the tricky ones (1 and 2's forms, 11–19, 21, 22, 99, 100, 101,
200, 1000, 2000, 2026) and reads every number 0 … 9999 back. QA's step `numbers` goes through it ([Test](#test)).

### Forms cards

"📋 Oblike · Forms": one of Jan's words with its table in a review card of its own, so all the forms of a word are
learned in one go. Only the forms the school has introduced are asked, a part at a time, and a review shows only the
tables of the part asked (biti's present, not its negative, future, past and imperative: Jan, after the first test,
"for tests I think it makes sense only to show the table which is tested"). In a table shown, a form not introduced yet
is greyed with its form, a preview to read ahead, and becomes a gap once its page is introduced, so in the end every
form is asked. The whole table is the word's overview, to read, never a test ("📋 Vse oblike · All forms", [below](#the-overview-of-a-words-forms)).
The counting card stays separate.

**What's introduced** (`FormsCard.introduced`, `tested`): a grammar page is introduced when it was met in the grammar
book (`GameState.grammar`: a unit's intro gone through, a page the book opened, a rule answered) or a course unit
whose `grammar` lists it had its intro done (finished or skipped as known: `School.progress(…).introDone`). A form of
the table is asked once its page and every page it `needs` are introduced (the forms book's pages, as
[Word forms](#word-forms) has them: a dual case needs `dvojina` and the case's page, a plural case `mnozina` too). The
word's own form (a noun's nominative singular, an adjective's masculine) is given, never asked. What a unit turns on:
[COURSE.md](COURSE.md#what-a-unit-turns-on).

**The table** (`app/.../data/FormsCard.kt` `plans`, `ui/FormsCardView.kt`):

| part of speech | sections (rows × columns) |
|---|---|
| noun | the six cases (1. kdo? kaj? … 6. s kom? s čim?) × ednina, dvojina, množina |
| verb | sedanjik (persons 1–3 × numbers: jaz delam, midva delava, mi delamo), nikalna oblika where the forms book has one (nisem, nimam), prihodnjik (bom delal, bova delala, bomo delali), preteklik by gender (on je delal, onadva sta delala, oni so delali; ona je …; ono je delalo), velelnik ((ti) delaj!, (vidva) delajta!, (vi) delajte!; (midva) delajva!, (mi) delajmo!) |
| adjective | the nominative by gender × number (dober, dobra, dobro …), then the cases 2–6 by gender in the singular, and in the plural |

The forms are the dictionary's (forms.ts: Wiktionary's, or the forms book's hand-checked tables for the irregular
verbs), never written by a rule: človek → ljudje, iti → grem, šel, biti → sem, nisem, bom. Every form the dictionary
gives for a slot is right (brati and bratje); the spoken forms the forms book accepts too (bojo, grejo) are right and
never shown. A verb's future (every verb but biti, whose own future the forms book has) is joined of biti's future and
the verb's l-form, both the dictionary's ("bom delal"; persons 1 and 2 also with the feminine l-form, "bom delala", the
learner's own first for a woman; the third person masculine, as its pronoun). An adjective's cases are read from the
dictionary's table the same way as its nominative (an indefinite reading only; no gender named means all three); the
accusative masculine singular, which Wiktionary gives as "genitive" or the nominative by animacy, is left out, as are
the oblique duals and the comparative. A word whose dictionary entry has fewer than four forms besides its own gets no
card; one missing a row or a cell gets the parts it has.

**Playing.** The word at the top (its pack's emoji, its base form and gender, what it is and means), "📖 Pravila v knjigi
· The rules in the book" (the cheat sheet at the pages of the gaps asked, as the top bar's 📋), then the sections of the
table with a gap asked now (`FormsCard.exercise`; a section without one is hidden, `Exercise.FormsTable.hidden`): the
gaps asked as `____` (the one asked now framed), the other introduced forms shown, the rest greyed (TalkBack: "…, še ni
predstavljeno · not introduced yet"), "🔒 Sivo: še ni predstavljeno …" under it. What is asked and graded is the part's
alone: a hidden section changes neither the gaps, their order and options (drawn from the word's whole table as before),
nor the rows said, the quality or what the bridge hears. Under the table the gap asked now
("📋 miza · 2/6 · Skloni · Cases — 4. koga? kaj? — množina · plural": the word named, the card's head may be scrolled
away by then; the words around the gap: "on je ____"), answered:

- **chosen** among three: the right form and two of the word's other forms, the closest first (the same row or column
  of the same section: hišo · hiše · hiši for the accusative singular; a future's are futures), the introduced ones
  before the greyed, never one that is right here;
- **typed** once the card is secure (two good reviews in a row, the last of quality 4 or more), with č, š, ž to tap and
  "Preveri · Check" above the keyboard, graded as a cloze (č, š, ž forgotten is almost, a wrong ending wrong; the
  pronoun typed too is fine), "✋ Izberi raje · Let me choose" back to the choices. A part newly introduced is chosen
  even then.

Each gap is marked at once, in the table (green, the wrong answer under it in red) and under it with its 🔊; a right one
goes on by itself, a wrong one at "Naprej · Next". At the end "🧾 Vsa tabela · The whole table": the rows asked said
together by the narrator in one clip, made on demand like any line (nothing is voiced ahead; the car and the day's
audio leave forms cards out), each row of the table its own 🔊 (one clip per row, on demand), and "📋 Vse oblike · All
forms" under the score: the overview of the word over the run. The 💬 attaches the card (`about_exercise.forms_card`:
the word, each gap's slot, where it stands, its options and answer so far, the greyed slots on screen (`preview`), the
titles of the sections hidden in this review (`hidden`); `source.variant` `forms_card` or `forms_card_type`).

**A part a review.** A long table is played a part at a time, so a card stays short: whole rows, at most 6 gaps (a
noun's two cases, a verb's two persons; a row is never split). The parts come in turn, by the card's reviews
(`total_reviews`); a failed review (quality under 3) asks the same part again. A part newly introduced comes first
(below).

**Its quality and its rules.** All gaps right is quality 4 (typed 5), a č, š or ž forgotten 3 (typed 4), 70 % of its
score 3, half 2, less 1; the cheat sheet opened during it keeps it at 4 at most. Each gap counts on its slot's page in
the grammar book (`ruleAnswers`). POST /reviews' `forms_cards` says how each card went (`[{item_id, lemma, typed,
fresh, cells: [{key, right, kind?, answer, expected}]}]`, read leniently); the bridge writes each wrong gap as a mistake
of its kind, `forms_<kind>`, and names them in `review_done`:

| kind | when |
|---|---|
| `forms_case_<nominative…instrumental>` | a noun's case (or an adjective's) |
| `forms_number_plural`, `forms_number_dual` | a plural or dual cell answered with the same case's other number; any dual cell of a verb |
| `forms_verb_person_<1sg…3pl>` | the present's ending |
| `forms_tense_past`, `forms_tense_future`, `forms_tense_imperative`, `forms_tense_negative` | the l-form, the future, the imperative, the negative present |
| `forms_adjective_gender` | an adjective answered with another gender's form |

The ids' words match the grammar pages' tags (grammar.ts `tagMatches`: "accusative", "dual", "verb_person", "past",
"future", "imperative", "adjective"); a kind is only ever written for a form that was asked, so its page was introduced:
an A2 page's name (genitive, dative, instrumental) reaches an A1 learner's patterns only once that page was.

**Which words, how many** (`app/.../FormsCardController.kt`; the bridge's `bridge/src/forms-cards.ts`,
`features/forms-cards.ts`). **`POST /forms-cards`** `{words: [{word, item_id?}] (0–40), introduced: [page ids], asked?}`
(the bridge says `forms-cards` in GET /state's `features`; Slovene only):

- **Of their own accord**: after each reload the app names its learned one-word vocabulary cards whose forms the phone
  has (GET /forms) and that have no forms card, the core verbs first (biti, imeti, iti, delati, hoteti, moči, vedeti …),
  at most 20. The bridge makes at most `LANI_FORMS_PER_DAY` (2) a day: a word the learner has learned, a noun, verb or
  adjective of the dictionary with at least 2 of its forms introduced, the core verbs first, then the words the content
  says most (its scenes, stories, readings, grammar examples and packs), then the app's order. A word refused today
  (not learned, too few forms) isn't named again until tomorrow.
- **On demand**: "📋 Vse oblike · All forms" on a word's card (a noun, a verb or an adjective; the home language's, not
  a word of an exercise still open) opens its overview at once (below); there "🃏 Vadi oblike · Practise the forms"
  makes its card (`asked: true`: whatever the day's count, learned or not, at least one of its forms introduced), then
  "▶ Vadi zdaj · Practise now" reviews it right away; a word that has one says so ("📋 Vse oblike te besede so v tvojih
  ponovitvah.") and offers it. Nothing introduced of it yet, or too few forms in the dictionary: the overview says which.

<a id="the-overview-of-a-words-forms"></a>**The overview of a word's forms** (`ui/FormsOverviewSheet.kt`,
`FormsCard.overview`): "📋 Vse oblike · All forms" on a word's card, and under a forms card's score, opens the word's
whole table in a sheet, to read, never graded: every section (a verb's present, negative, future, past and imperative;
a noun's cases by number; an adjective's genders, then its cases), the word's own form bold, the forms the learner
practises quietly marked (on a light green: their page and the pages they need introduced, as a card asks them;
TalkBack "…, vadiš · you practise it"), the others plain and a little grey, each row's 🔊, "✓ Vadiš 18 od 39 oblik · You
practise 18 of the 39 forms" above, "Na zeleni podlagi: oblike, ki jih vadiš. Sive pridejo z enotami v šoli." under it.
Its table is the learner's forms card's when they have one (offline too), else the bridge's: **`GET
/forms-cards?w=delati`** (1–10 words) answers each word's tables as its card would have them (`{language, words:
[{word, tables: [{lemma, pos, gender?, gloss, emoji?, table, slots, tested: []}]}], attribution}`: the future joined,
the adjective's cases), one per part of speech (lep: the adjective and the noun; the app takes the word card's), and
writes nothing; an older bridge (404): the dictionary's forms of GET /forms, as they are (no future joined, no
adjective's cases). Opened in a run, it has no "🃏 Vadi oblike" (the card is being practised).

Each card is a review item of its own (`forms_<word>`, type `forms`, due the day it is made), its table in the item's
`forms` (`{v, lemma, pos, gender?, gloss, meaning?, emoji?, table, slots: [{key, forms, also?, page, needs?,
composed?}], tested, fresh?, unlocked?, resurface?, asked?}`) so the app asks it offline; scheduled by SM-2 as any card.
In the review at most 3 due forms cards are spread among the others; the rest wait for the next. It answers
`{added: [{item_id, lemma, pos, tested, existing?}], skipped: [{word, why}], resurfaced: [{item_id, lemma, due_date,
fresh}], made_today, per_day, resurface_per_day}` and `words_added`.

**Back soon when a page asks more** (Jan: "it will reintroduce already learned words more often for repeated
learning"). Every request says the pages introduced. A card whose table asks more now than when it was last scheduled
(`forms.tested`: a unit's intro done, a page met) comes back within days: the most useful first (as above), at most
`LANI_FORMS_RESURFACE_PER_DAY` (3) a day (the next day with room; never later than it was due), written with
update-db.py's `resurface` ([DB_SCRIPTS.md](../docs/DB_SCRIPTS.md)): its repetitions back to 0 and its interval 1 (the
new part is new learning: 1 day, 6 days, then times its easiness), its easiness, history, mastery and total reviews
kept; `forms.fresh` the slots newly asked, `unlocked` the day. The app plays the fresh rows first (up to 6 gaps), chosen,
under "🆕 Novo v tabeli · New in the table", while the card has fewer than 2 good reviews since (within 30 days); then
the parts in turn again. A unit's intro done tells the bridge at once (the app asks right away, which also makes the
day's new cards of words that had none yet) and says "📋 Nove oblike v tabelah: delati, hiša. · New forms in your
tables: …" after the pages' news. A request whose pages ask nothing new writes nothing. `card.py show` tells how the app
asks a card (its slots, what was tested, the fresh part, the joined future); a form that looks wrong is the
dictionary's: fixed there, then the card deleted so a new one is made.

## Family

The learner's family can take part in two ways:

- **Family voices.** Family members record words and phrases, on Jan's phone ("Družina posname ·
  Family records" on the home screen) or on the family page. Everywhere the app speaks Slovene, it
  plays a family recording when there is one (with a "👵 Micka" badge) and falls back to TTS. The bridge
  brings each new recording to the loudness of the voice clips as it stores it (see [Voice](#voice), "Every
  clip at one loudness").
- **Partner challenges.** The partner writes a question, a sentence to translate, or something to
  say. Jan sees it as "💌 Od Maje · From Maja" on the home screen and as a notification, answers by
  typing or speaking, and earns wisdom in the village. The tutor gives feedback to Jan and a short
  note that the partner sees on the family page.

The family page is served by the bridge at `/family`. It uses its own token,
`<data>/app/family-token` (generated on first start, like the app token).

### Give the partner access

1. Tailscale admin console → **Machines** → the tutor's node → **Share…**. Send the partner the invite link.
   Node sharing is free and gives the partner's devices access to this one node only, not to the rest of
   your tailnet.
2. The partner installs Tailscale on their phone, signs in with their own account and accepts the invite.
3. Send the partner the link (it contains the family token):

   ```bash
   echo "https://$(tailscale status --json | jq -r '.Self.DNSName | rtrimstr(".")')/family#t=$(cat data/app/family-token)"
   ```

4. The partner opens it, enters their name, and can add the page to the home screen. The page keeps the token
   in the browser and removes it from the address bar. The token is in the fragment (`#t=`), so it
   never reaches the server or its logs; older `?t=` links still work.

### What the family token can do

- It reaches only `/family/api/*`, the `/audio` routes and the voice clips (`GET /voice/index`,
  `/voice/file/*`; it cannot make new clips). Every other route (`/state`, `/message`,
  `/events`, `/packs`, `/game`, …) answers `403`. It cannot post Jan's answers.
- The page itself (`/family`, `app.js`, `style.css`) holds no data and needs no token. It is sent
  with a strict Content-Security-Policy.
- Uploads must be real m4a, ogg or webm (checked by their first bytes), at most 2 MB, stored under
  random names and served with a fixed audio type and `nosniff`. All recordings together: at most
  5,000 files and 500 MB (`507` beyond; delete some first).
- At most 30 challenges an hour (`429` beyond): each one wakes the tutor session.
- What the partner writes reaches Claude as channel content, inside the JSON block under
  `untrusted_family_content` (with `<`, `>`, `&` and backticks escaped). The channel instructions
  tell Claude to treat it as content, never as instructions.
- To revoke access: delete `<data>/app/family-token` and restart the session (a new token is
  generated), or remove the node share in the admin console.

### Without a node

A town on the relay with no address of its own (`LANI_TOWN_URL=relay`; or any town with `LANI_RELAY_FAMILY=on`) has its
family page on the relay ([Through the relay](#through-the-relay)): the relay serves the page, and its API goes to the
town's bridge through the town's mailbox. The relay keeps only the family token's SHA-256; the town checks the token
again, and the token can do there exactly what it does on a node. Send the partner:

```bash
echo "$LANI_RELAY_URL/family/<town id>/#t=$(cat data/app/family-token)"   # the town id: lani-town list
```

While the town is away (the phone asleep), the page can't load what is new; a challenge or a recording the partner
sends waits on the relay (up to 14 days) and reaches the town when it is back. A node's family page stays on its
tailnet unless `LANI_RELAY_FAMILY=on`.

## Backups

`bin/lani-backup` snapshots the learner data (as the bridge finds it: `LANI_DATA_DIR`, lani.env's, else `data/`; except
release APKs and a data repository's `.git`), the session results, and the voice cache when it is outside the data
(as `voice/`) into `~/.local/share/lani/backups` (set `LANI_BACKUP_DIR` to change it). Each snapshot is a plain
directory you can browse; files that didn't change are hard links to the previous snapshot, so they
cost no space. The voice database is copied with SQLite's online backup, and JSON files are parsed
after copying.

```bash
companion/bin/lani-backup install          # systemd user timer: every 6 hours
companion/bin/lani-backup status           # last run, newest snapshot, disk use
companion/bin/lani-backup list
companion/bin/lani-backup verify           # newest snapshot against its manifest
companion/bin/lani-backup restore 2026-09-23T2231                    # preview only
companion/bin/lani-backup restore 2026-09-23T2231 --only data/spaced-repetition.json --apply
companion/bin/lani-backup --profile luka install     # another learner (see "Learners"): own snapshots and timer
```

Each learner of `lani-profile` has their own snapshots (`~/.local/share/lani/backups/<id>`) and timer
(`lani-backup-<id>`); `--profile` works with every command. The default profile keeps the paths above.

A restore first snapshots the current state (`…-pre-restore`). Files newer than the snapshot are
left alone. The learner databases (`data/*.json`) can be restored any time; files under `data/app/`
only while the bridge is stopped, since it would write its state over them: quit the tutor session first. It keeps the newest 7 snapshots, then
the last one of each day for 14 days, of each week for 8 weeks and of each month for a year.

User timers only run while you're logged in, unless lingering is on (`loginctl enable-linger $USER`);
missed runs catch up at the next login. The snapshots stay on this machine. To keep a copy
elsewhere too, sync the backup directory to another tailnet device, e.g. with `rsync -aH`.

## Data: git, the handover, the backup

Opt-in, for the app's hosted mode (plan 5): the learner's data can live on the phone and move between the node and the
phone through the data repository on GitHub. Built in [docs/plans/05-built/git-sync.md](../docs/plans/05-built/git-sync.md),
with what to set up and a switch step by step.

```bash
companion/bin/lani-handover                 # hand the data over to the phone: commit, the lease offered, push; then a QR code
companion/bin/lani-handover --code          # the QR code needs a code typed on the phone too
companion/bin/lani-handover --status        # who writes the data now
companion/bin/lani-handover --take          # take it back once the phone handed it over (--force: the phone is lost)
```

- **The lease** (`app/lease.json`, `.claude/hooks/lani_lease.py`) says who writes. While the phone holds it, this node
  only reads: `update-db.py` and `card.py` refuse, nothing is committed, and the bridge answers the app's writes with
  423 and fails the tutor's writing tools, each saying who writes and how to take it back. `LANI_LEASE=ignore` overrides
  it (an emergency only).
- **The town's own key:** `app/town-key.pem` when there is one, else the bridge key as before; a town keeps its id
  among its friends when it moves.
- **In the app:** 🔗 Povezava · Connection → 💾 Podatki · Data: connect git (the OAuth device flow with
  `lani.githubClientId`, or a pasted fine-grained token), the repository and the last sync, who writes and the
  handover's QR code, the encrypted backup (a recovery code or a passphrase; the relay's backup API, `lani.relayUrl`).

## Lani on this phone (no node)

The app's hosted mode (plan 5): "☁️ Lani na tem telefonu · Lani on this phone" on the first screen, next to "🔗 Poveži
svojega učitelja · Connect my own tutor". The bridge runs in the app (QuickJS), the learner's data lives on the phone
(`<filesDir>/lani/data`, a data repository's layout), and there is no node, Tailscale or Claude Code. Built in
[docs/plans/05-built/hosted-mode.md](../docs/plans/05-built/hosted-mode.md).

- **The first launch:** a fresh start asks what `lani-setup` asks (the name, how the village speaks to them, the language
  learned, the explanations' language, the level, the minutes a day) and makes the six databases from `data-examples/`;
  or "📥 Uvozi izvoz · Import an export" takes a file `lani export` made. Then the tutor's step, then Home, where the
  village is founded.
- **Moving a learner here from a node:**

  ```bash
  companion/bin/lani export                     # → ./lani-export-<name>-<date>.zip (no secrets)
  companion/bin/lani export --with-secrets      # with the town's key and the family token: keep it private
  companion/bin/lani export --profile luka      # another learner on this machine
  ```

  Put the file on the phone, and import it in the app (the first launch, or 🔗 Povezava · Connection → "☁️ Preklopi na
  ta telefon · Switch to this phone"). With git, `lani handover` does it through the data repository instead
  ([Data](#data-git-the-handover-the-backup)).
- **Back to the node:** in the app 🔗 Povezava · Connection → "📤 Izvozi moje podatke · Export my data", then

  ```bash
  companion/bin/lani import lani-export-ana-2026-10-19.zip            # checks, asks, keeps what was there, commits
  companion/bin/lani import --dry-run lani-export-ana-2026-10-19.zip  # only what would change
  companion/bin/lani import --verify lani-export-ana-2026-10-19.zip   # only the checks
  ```

  and "🖥️ Na svoj računalnik · To my own computer" in the app, then pair as usual. An import refuses while the phone
  holds the lease, and it keeps this node's own files (its secrets, voice clips, runtime files, `.gitignore` and lease).
- **Towns and the family page** go through the relay in this mode: the build's `lani.relayUrl` (or `LANI_RELAY_URL`),
  or the connection sheet's "🛰️ Posrednik (relay)". Without one, towns aren't available on the phone, and Friends
  says so.
- **What isn't there yet:** the tutor's settings (the tutor-live work), voices other than the phone's own
  text-to-speech, the car's clips.

## Im Auto · In the car

"🚗 Za pot · For the road" is a learning session for the car that uses sound only. Nothing is read, typed or looked
at. The app plays it as a media app: in **Android Auto**, and over Bluetooth with the car's and the steering wheel's
play, pause, next and previous. It plays only what is on the phone, so the drive works without the node. Besides
listening and saying things aloud, "❓ Kviz · Quiz" asks questions that the learner answers with the steering wheel's
buttons (see [The quiz](#the-quiz-answered-with-the-steering-wheel)).

**Get ready (before the drive, with the node reachable).** On Home, tap the "🚗 Za pot · For the road" tile, then
"🚗 Pripravi za pot · Get ready for the road". The app:

1. gathers (a few seconds, with the app open) the review cards due in the next 3 days (GET /state), the words of the packs under way and then of the next
   packs (at most 600, the easiest level first), the scenes' dialogs, the stories at Jan's level, the dialogs'
   short phrases to shadow (of the scenes at Jan's level or below, at most 600) and the drills (GET /drills, else the
   app's own copy; see [The drills](#the-drills));
2. keeps only the items whose Slovene clips are in the voice store's index (`GET /voice/index`), in the voice each
   person speaks in (their own voice, their archetype, their gender's narrator, the female narrator). A dialog that
   misses more than a quarter of its lines is left out; a single missing line is skipped. The listening sessions never
   ask the node to voice anything (`/voice/say`, `/voice/redo`). Only the quiz does, for the options the voice store
   lacks (a sentence with a wrong form, "Prav!"): see [The quiz's audio](#the-quizs-audio);
3. links the clips the phone's clip cache has (one file with two names, nothing copied: the day's prefetch got many of
   them already), or downloads them with `?count=0`: the bridge then doesn't count the download, so an old-style
   narrator word is not queued for its carrier re-voice (`features/voice.ts`; a bridge started before this change
   counts the download like the app's other downloads). A clip the day's prefetch is downloading meanwhile is waited
   for, not downloaded twice ([Offline on the phone](#offline-on-the-phone));
4. renders the prompts in Jan's base language (English) with the phone's own text-to-speech, to WAV files (the
   meanings, "You say: …", the stories' setups and recaps, the drills' instructions, questions and meanings, and the
   quiz's questions and its meanings to choose from);
5. writes the library. Everything is in the app's `files/road/` (`road.json`, `clips/`, `prompts/`, `spoken/`), not
   in the clip cache, so nothing is dropped. It counts in the learner's cap for audio (the settings' "🔊 Zvok brez
   povezave · Offline audio"; 250 MB by default): getting ready takes what the cap leaves after the day's clips, and what
   doesn't fit is left out, as what can't be had ("Prostor za zvok je poln · The storage cap for audio was reached").
   With the cap off or unlimited, it takes what it gets, as before. Getting ready again takes only what is new.

**In the background, playable early.** Steps 3 to 5 run in a foreground service (`RoadPrepService`, a data sync with
a partial wake lock), so they go on with the screen off or the app in the background. Its notification shows how far
it is: "🚗 Pripravljam za pot … 40 %". The work goes in the order the items play: first the first block of
"🚗 Za pot" (about 30 minutes), then each session of the browse tree a little at a time (the one with the fewest
minutes ready first, so all of them grow alike), then the cards due only in the next days. The clips (four at a time)
and the prompts (one at a time) go down this order side by side. When the first block is on the phone, the app writes
the library with what is ready, and the sessions can start: in the car, in Android Auto and in the sheet's player.
The sheet and the notification say "Že lahko poslušaš (35 min), ostalo še prihaja. · You can listen already (35 min);
the rest is still coming in." The library is written again about every 15 seconds while the rest comes in, and "🚗 Za pot" reads it again for
each next block. A library from an earlier getting ready stays playable until then.

**Resumable.** What is still to do is in `files/road/plan.json` (the gathered library and the clips' URLs) until the
end. If the run stops (the app is force-stopped, the phone restarts, Android stops the service), the app goes on with
the plan the next time it opens. The clips and prompts already on the phone are kept, and only the rest is fetched and
rendered. The node must be reachable again for the clips that are still missing.

The sheet shows the progress ("🚗 Pripravljam za pot · Getting ready for the road … 40 %", "Posnetki · Clips 120/800",
"Navodila · Prompts 30/400"). At the end it says "✅ Končano · Done: everything for the road is on the phone (5 h 10 min
to listen to)", and the notification says the same (a tap opens the app). Items whose files could not be had are left
out. When no clip could be downloaded, the sheet says that the node must be reachable. When the phone has no voice for
the prompts, it says to install one.

The percentage counts a prompt as ten clips, because rendering one takes about that much longer than downloading a
clip. Measured on the emulator with a copy of Jan's data (the dev bridge, September 2026): 4,276 clips and 2,612
prompts (14 h 49 min to listen to) took about 2.5 minutes with the screen off, and the sessions could start after
about 15 seconds. Before this change, the prompts alone took about 5 minutes, with the app open. A phone's voice is
slower than the emulator's, and its downloads go over the network, so expect longer on the phone.

The pauses, the stories' recaps, the phrases to shadow and the drills' items are made while getting ready. After an
update of the app, get ready again once (with the node reachable): a library from before keeps the old pauses and
stories, and has no phrases for "🗣️ Odmev" and no drills.

**How much there is.** Measured on a copy of the voice store at the end of September 2026, before the pauses followed
the answer's length (the durations are the code's estimates, and more than half of the time was the pauses; with the
shorter pauses for short answers it is somewhat less): about 5 hours, and the sessions go round again after that.

| Kind | Items with clips | Hours | Left out for missing clips |
|---|---|---|---|
| Review cards (due in 3 days) | 73 | 0.2 | 12 |
| New words, with their examples | 545 | 3.1 | 0 |
| Dialogs | 84 | 1.75 | 144 (a quarter of their lines or more have no clip) |
| Stories at A1 (evenings) | 2 | 0.1 | 20 (no clips at A1 or A2 yet: `voice-build` voices them now, the A1 ones before the scenes) |

That is about 2,000 clip files (about 70 MB) and about 1,400 prompts (WAV, about 100 MB) in `files/road/`.

The drills have no clips yet (`voice-build` voices them: 1,039 texts, 18,329 characters, see [The drills](#the-drills)).
Once voiced they add about 3.4 hours: transformations about 55 minutes at A1 (and 9 at A2), sentence building about 75
minutes, riddles about 35, rapid fire about 25 (it goes round again in another order each day), and about 800 more
prompts (about 60 MB).

**The menu** in Android Auto has two tabs, so nothing needs scrolling while driving (`road/RoadMenu.kt`):

| Tab | Items |
|---|---|
| 🚗 Za pot · For the road | Four big items in a grid: **▶ Nadaljuj · Continue** ("🚗 Za pot", where it left off), **❓ Kviz · Quiz**, **🌙 Mirno · Easy listening**, **📖 Zgodba · Story** (the story under way, from its next evening) |
| Več · More | Each kind on its own, a list: 🔁 Ponavljanje, 🆕 Besede, 💬 Pogovori, 📖 Zgodbe, 🗣️ Odmev, 🔄 Preobrat, ⚡ Hitri odziv, 🧱 Gradnja stavkov, 🕵️ Uganke |

Every session id from before still plays (a car remembers the one it played last), and the first tab keeps its id.
The sessions:

| Session | What plays |
|---|---|
| ▶ Nadaljuj · Continue (🚗 Za pot · For the road) | Blocks of about 30 minutes, one after the other: two due cards, two new words, a dialog, again; each new word comes back three items later; an evening of a story about every 10 minutes; a short burst of a drill about every 7 minutes, the first after 4 (four transformations of one set, five rapid answers, three sentences built or three riddles: the kinds in turn, the one heard least recently first). The least recently heard first; when everything was heard, it goes round again. |
| ❓ Kviz · Quiz | Questions answered with the steering wheel: see [The quiz](#the-quiz-answered-with-the-steering-wheel). |
| 🌙 Mirno · Easy listening | For heavy traffic or when Jan is tired: only the stories and the dialogs, with no "say it" prompts or pauses. An evening of a story, then about 10 minutes of dialogs, again. A dialog plays as an audio play: the other person's lines and the learner's right answers in turn. A story keeps its English setup and recaps. |
| 📖 Zgodba · Story | The story under way (the one whose evening was heard last) from its next evening, then the other stories as "📖 Zgodbe" has them; with none under way, as "📖 Zgodbe". |
| 🔁 Ponavljanje · Reviews (N) | Today's due cards as audio flashcards. |
| 🆕 Besede · New words | The new words, each heard again three items later. |
| 💬 Pogovori · Dialogs | The scenes' dialogs as short audio plays. |
| 📖 Zgodbe · Stories | The storyteller's stories, a chapter at a time, in order. |
| 🗣️ Odmev · Shadowing | Short Slovene phrases from the dialogs (the villagers' lines, the learner's right answers and the replies to them; 2 to 8 words, from the scenes at Jan's level or below): the phrase, a pause to say it along in the same rhythm, the phrase again. No English, nothing graded. |
| 🔄 Preobrat · Transformations | A sentence and what to do with it; say the new sentence. Six of a set in a row, then six of the next set; the set heard least recently first. |
| ⚡ Hitri odziv · Rapid fire | Numbers, the clock, days, months and dates at a fast pace. Six of a set in a row, then the next set; the ones heard alike in another order each day. |
| 🧱 Gradnja stavkov · Sentence building | Sentences built in three or four steps, the least recently heard first. |
| 🕵️ Uganke · Riddles | Stari Janez's riddles, the least recently heard first. |

**Starting by itself.** When the car connects, or when Jan presses play on the steering wheel and the app isn't
running (after a restart), the session starts "▶ Nadaljuj" (Media3's playback resumption, `onPlaybackResumption`; the
manifest's `MediaButtonReceiver` lets the button start the service). The phone's media controls offer it to resume too.
Jan presses play and nothing else.

**How an item sounds** (say it aloud before you hear it):

- **A card or a word:** its meaning in English (the phone's voice), a pause to say it in Slovene, the Slovene clip, a
  pause to repeat it, the Slovene again. A word with an example then has the example's meaning, a pause, and the
  example.
- **A dialog:** the other person's lines in their voice. Before each of the learner's turns: "You say: Of course I'll
  help you!", a pause to say it in Slovene, the right answer, a pause to repeat it, and the other person's reply.
- **A story's evening:** a short setup in English (the evening's teaser, "Janez tells the story of Zlatorog", else what
  its first paragraph tells). Then Janez tells a whole paragraph in Slovene only (a paragraph is his lines between two
  of the learner's turns), and a short recap in English follows (about 140 characters, one or two sentences): at A1
  after every paragraph, at A2 after every second one, from B1 only at the end. The recap is from the story's notes
  (the notebook's written-down paragraphs, each with the paragraph it shares the most words with), else from the
  paragraph's English lines without Janez's asides to the listener ("Sit by the fire", questions), shortened: the first
  sentence of each note or line first, then what still fits. The learner's turns are as in a dialog.
- **A phrase to shadow** (🗣️ Odmev): the phrase, a pause to say it along, the phrase again.
- **A transformation** (🔄): the Slovene sentence ("Micka kuha kosilo."), what to do with it in English ("Into the
  past.", or the item's own: "Three.", "With milk."), a pause to say the new sentence, the new sentence ("Micka je
  kuhala kosilo."), a pause to repeat it, the new sentence again.
- **A rapid answer** (⚡): a number, a time or a day in English ("twenty-one", "It's half past three.", "On Monday."),
  about 2 seconds to say it, the Slovene once ("enaindvajset", "Ura je pol štirih.", "v ponedeljek"), and on at once.
- **A sentence built** (🧱): "Say: I'm going.", a pause, "Grem.", a pause to repeat it; "Now add: to the shop", a pause,
  "Grem v trgovino.", a pause; and so on to the whole sentence ("Jutri grem v trgovino, ker potrebujem kruh."), heard
  once more at the end.
- **A riddle** (🕵️): Stari Janez's three or four clues in his own voice ("Iz lesa sem. Imam štiri noge. …"), "Kaj
  sem?", 5 seconds to guess aloud, the answer ("Miza!") and its English ("A table!"), then each clue again with its
  English.

**The pauses follow the answer's length**: 1.5 s a word plus 1 s, at least 2.5 s and at most 8 s, to say it (cards,
words, examples, the learner's turns in dialogs and stories, the phrases to shadow, the transformations and each step
of a sentence built); the pause to repeat it after hearing it is a fifth shorter. For example: "Hvala." 2.5 s (repeat
2 s), "Seveda vam pomagam!" 5.5 s (4.4 s), "Ker je hotel zlato za dekle." 8 s (6.4 s). Rapid fire has its own shorter
pause: 2 s, half a second more for each word from the second, at most 3.5 s ("enaindvajset" 2 s, "ob petih" 2.5 s). A
riddle gives 5 s to guess. Next during any of these pauses plays the answer at once.

**Controls.**

| Button | What it does |
|---|---|
| ⏭ Next (steering wheel, Android Auto, the notification) | During the pause to say the Slovene: plays the answer at once (Jan said it already). At any other moment: skips to the next item. |
| ⏮ Previous | Plays the item again from its start (pressed twice quickly, the item before). In the quiz: see [its buttons](#the-quiz-answered-with-the-steering-wheel). |
| Next or ⏩ held (the car sends fast-forward) | On a card or a word: 👍 "Znal sem · I knew it". Elsewhere: nothing. |
| Previous or ⏪ held (the car sends rewind) | On a card or a word: 👎 "Nisem · I didn't". Elsewhere: nothing. |
| 🌙 Mirno (Android Auto, the notification) | Switches the session playing to "🌙 Mirno" at once, for sudden heavy traffic. In Mirno the button is "🚗 Za pot" and goes back. |
| ❓ Kviz (Android Auto, the notification) | Switches to "❓ Kviz" at once. In the quiz the button is "🚗 Za pot" and goes back. |

Whether holding a steering-wheel button sends fast-forward or rewind depends on the car: many do; others send nothing
or a repeated next, and then nothing changes. The session offers fast-forward and rewind at all times, so a car that
sends them reaches the app. A navigation prompt or a phone call pauses the session, and it goes on after (it is
speech, so it pauses rather than ducks). Bluetooth disconnecting pauses it.

**Rating (optional).** On a card or a word, Android Auto and the notification show two buttons: 👍 "✓ Znal sem · I knew
it" and 👎 "✗ Nisem · I didn't" (holding a button that sends fast-forward or rewind does the same). A press records the
item's review as a spoken answer does in the app's review (quality 5 or 1, `ReviewPlanner.Variant.SPEAK`); the button
fills to show it. A card's answer goes to `POST /reviews`, a new word's to `POST /packs/<pack>/learn` (it becomes one of
the learner's words). If nothing is pressed, nothing is graded. The ratings are kept in `files/road/ratings.json` and
go to the app's outbox as one session when the session is paused or ends (the car switched off, a button), when the
service stops, and when the app opens. The outbox sends them when the node can be reached.

**Without Android Auto** (Bluetooth only): the sheet has a simple player with the same menu (four big buttons, and
"▸ Več · More" folded with the other sessions), ⏮ ⏯ ⏭ and, on a card or a word, ✓ and ✗; in the quiz a line says what
⏭ and ⏮ do. Start a session before the drive; then the steering wheel's buttons and the media notification control it.

**Enable Lani in Android Auto.** Android Auto hides apps that were not installed from the Play Store until its
developer settings allow them:

1. On the phone, open the Android Auto settings (Settings → Connected devices → Connection preferences → Android Auto,
   or the Android Auto app).
2. Tap "Version" (at the bottom) repeatedly, about 10 times, until it asks to allow developer settings. Confirm.
3. Open ⋮ (top right) → "Developer settings", and turn on "Unknown sources".
4. Connect to the car. Lani is in the list of media apps (the car's app launcher; on some cars, customise the
   launcher first to show it).

**Safety.** Nothing in this mode needs the screen or typing: the phone shows only the player. Start the session before
driving, or from Android Auto's media screen.

**Code.** `app/src/main/java/si/lanisce/lani/road/`: `RoadPlay` (how an item sounds, only from clips that exist; the
pauses; Mirno's straight play; the phrases to shadow), `RoadStory` (a story's setup and recaps), `RoadDrills` (the
drills' items, which are offered, their sessions' order and the bursts), `RoadMix` (the sessions and their order, the
ratings' writes), `RoadMenu` (the two tabs, the custom actions), `RoadQuiz` (the quiz's questions, its session, a
question composed as it plays, the feedback, `QuizControls`: what each button does when), `RoadQuizCount` (what its
answers count), `RoadGather` (the items from the node's content), `RoadPrep` (getting ready as the sheet shows it,
and the gathering into a plan), `RoadWork` (the plan, and the order the files are got in), `RoadPrepWork` (getting the
files, writing the library as it grows), `RoadPrepService` (the foreground service that runs it, its notification),
`RoadPrompts` (the phone's voice), `RoadStore` (the files), `RoadService` (the Media3 `MediaLibraryService`, the browse
tree, the buttons), `RoadControls` (what next and a held button do, and `RoadPlayer`, the plain `ForwardingPlayer` the
session gets, with the quiz's picks: a `ForwardingSimpleBasePlayer` rebuilds the timeline and crashed on an item's pieces not prepared yet),
`RoadSources` (an item as one media source: its prompts, clips and pauses), `RoadRemote` (the phone's player) and
`RoadCarCheck` (QA's car, a debug build only). The Android Auto declaration is `res/xml/automotive_app_desc.xml` and
the manifest's `com.google.android.gms.car.application`.

### The quiz: answered with the steering wheel

A question is read, then its options one at a time, numbered: "Ena: …", "Dve: …", "Tri: …", each followed by a short
gap. Jan presses ⏭ while the right one is read, or just after it. With nothing pressed, the options come once more,
then the next question (skipped: it counts nothing). After a pick: "Prav!" and the right answer whole, or "Ne, prav je:"
and the right answer; then the next question. After every ten answers a short summary: "Sedem od desetih." The volume
keys can't be used: in Android Auto and over Bluetooth the car keeps them, they never reach a media app.

| Button | During the question | While an option is read, or in its gap | In the feedback ("Prav!") |
|---|---|---|---|
| ⏭ Next | On to the options at once | Picks it: the option being read, or the one just read (its gap of 1.2 s, the pause between the rounds and at the end, and the first half second of the next one's number) | On to the next question |
| ⏮ Previous | The question again from its start | The question and its options again from the start (never the question before) | The feedback again |
| ⏯ Play/pause (where the car sends it) | Pauses | Picks, as ⏭ does | Pauses |
| Held (fast-forward, rewind) | Nothing | Nothing | Nothing |

On Android Auto's screen and in the media notification, the pause button also picks while the options are read: to
pause, press it during the question or the feedback. A navigation prompt or a call pauses the quiz as any session.
`road/RoadQuiz.kt` (`QuizControls`) has the timing, `road/RoadControls.kt` (`RoadPlayer`) the buttons.

**How a question sounds** (the options in another order each day, the right one anywhere among them):

| Kind | The question | The options |
|---|---|---|
| A dialog's turn | Its context line in the speaker's voice ("Kaj boš?"), then "You want to say: Ten eggs, please." when the turn tests a form (or has no context), else "What do you answer?" | The right answer and up to three wrong ones of the turn, in the learner's voice ("Deset jajc, prosim." / "Deset jajce, prosim.") |
| A card's meaning | "What does it mean?" and the Slovene ("kruh") | Its meaning and two other cards', in English |
| A card's word | "How do you say: bread?" | Its Slovene and two other cards' |
| A word's form | "Which is right?", the word ("sesti"), the sentence's meaning when it is a line of the content | The sentence with each form: "Jaz sedem na klop.", "Jaz sedim na klop.", "Jaz sede na klop." |
| A riddle | Stari Janez's clues and "Kaj sem?" in his voice | "Miza!" and two other riddles' answers (a word's or a villager's) |
| The grammar book's practice | The sentence's meaning ("I don't have time."), else "Which is right?" | The sentence with each choice: "Nimam čas.", "Nimam časa.", "Nimam času." |

**Which questions.** What Jan has, at Jan's level: the dialogs' turns of the scenes at Jan's level or below (a turn
whose rule isn't introduced yet is trimmed as in the app: [GAME.md](GAME.md#rules-not-yet); an echo turn and a tap turn
are no questions), the vocabulary cards whose Slovene the voice store has (one of a card's two questions a day), the
forms of the familiar one-word cards (the form a review would ask next, always chosen: [GAME.md](GAME.md#a-words-forms)),
the riddles offered at Jan's level, and the grammar book's choice exercises with a sentence to fill, of the pages met
(the modules', the tent's, the letters'). At most 80 dialog turns, 60 cards of each kind, 40 forms, 40 riddles and 40
exercises. The session: the questions on the cards due (or nearly due) first, then the rest; each part the kinds in
turn, each kind the least recently heard first.

**What an answer counts**, as the app's own play does ([GAME.md](GAME.md#words-met-in-play)): a right answer on one of
Jan's words (a card's question, a dialog's turn that tests it, a form, a riddle of a pack word) is a word met in play, a
review of quality 4 when the card is due or nearly and wasn't reviewed today, once a day; a wrong one about its meaning
lowers it gently; a wrong form leaves it as it is. A dialog's turn, a form and an exercise count on their grammar page,
as a dialog's pick counts. The answers go to the outbox when the session is paused or ends (one `POST /reviews` with the
quiz's words, as a dialog's: the bridge checks again what may count), offline until the phone reaches the node. The
rules, the forms asked and the cards changed today are counted by the app when it next opens (`files/road/quiz-app.json`).

#### The quiz's audio

Everything is on the phone before the drive, got ready with the rest. The question's and its options' Slovene come from
the voice store first. What it lacks (a wrong form in a sentence, a grammar exercise's wrong choice, the fixed phrases)
is asked of the node while getting ready (`POST /voice/prepare`, up to 20 texts at a time): voiced now if it can, but not
as a live line (ElevenLabs only above the reserve kept for live lines, then the local Gepard worker), at most 6,000
characters a day (`LANI_VOICE_ROAD_DAILY`); else by the phone's own voice in Slovene, if it has one. A question whose
options can't all be had isn't played. The fixed phrases ("Ena.", "Dve.", "Tri.", "Štiri.", "Prav!", "Ne, prav je:",
"Sedem od desetih.", machine-written) fall back to the base language's prompt ("One.", "Right!", "7 of ten."). The
questions in English ("What does it mean?", "You want to say: …") and the meanings are the phone's voice, as every
prompt. A bridge from before `/voice/prepare` (404): only the phone's voice.

### The drills

Four audio drills for the car, in the Pimsleur way (a prompt, a pause to say it aloud, the answer). Their content is
curated in `companion/drills/<language>/<id>.json`, one file a drill (Slovene: `companion/drills/sl/`), machine-written
(Claude) in Slovene, English and German and not reviewed by a native speaker (each file's `review`). The bridge
validates and serves them (`GET /drills`); the app bundles its own copy (for a bridge from before, which answers 404);
`voice-build` voices them (`drills A1`, `drills A2+`).

| Drill | File | What is in it |
|---|---|---|
| 🔄 Preobrat · Transformations | `preobrat.json` | 11 sets, 215 items, one set a rule of the grammar book. At A1: into the past (`pretekli-cas`: "Micka kuha kosilo." → "Micka je kuhala kosilo."), into the future (`prihodnjik`), jaz → midva and make it two (`dvojina`: "Imam brata." → "Imam dva brata."), counting on (`stevila-samostalniki`: "Ena krava." → "Dve kravi." → "Tri krave." → "Pet krav."), say no (`rodilnik-nikalnica`: "Imam čas." → "Nimam časa."; "To je krava." → "To ni krava."), never, nothing, nobody (`zanikanje`), "Vidim …" (`tozilnik`: "To je Micka." → "Vidim Micko."), ti → vi (`ti-vi`). At A2, for when they are introduced: the comparative (`primernik`) and "with" (`orodnik`). |
| ⚡ Hitri odziv · Rapid fire | `hitri-odziv.json` | The numbers 1 to 100 and 24 up to 999, generated as the app counts them (`SpokenNumbers`; the bridge's `numberWord` voices the same words); the clock ("Ura je pol štirih.", "ob petih", "ob pol šestih"), days ("v ponedeljek", "ob ponedeljkih"), months ("januarja"), time words ("pojutrišnjem", "čez eno uro") and dates ("prvega maja"): 130 listed. |
| 🧱 Gradnja stavkov · Sentence building | `gradnja.json` | 73 sentences of three or four steps (67 at A1), of the village's people and places: "Grem." → "Grem v trgovino." → "Jutri grem v trgovino." → "Jutri grem v trgovino, ker potrebujem kruh." Each names the rules it asks for beyond the obvious (`rules`: `vezniki`, `prihodnjik` …; the A2 ones `orodnik`, `dajalnik` …). |
| 🕵️ Uganke · Riddles | `uganke.json` | 54 riddles told by Stari Janez (`teller`): 44 of words of the A1 packs (`word`: "v-kuhinji/miza"), each clue in the first person ("Iz lesa sem. Imam štiri noge. …" "Kaj sem?" "Miza!"), and 10 of the village's people (`villager`: "Kdo je to?" "Kovač Tone!"). |

**Only what the learner was introduced to.** A transformation set is of one rule (`rule`, a page of the grammar book),
a build names its rules (`rules`): while one of them is not yet introduced to the learner (the page above their level
and not met: [GAME.md](GAME.md#rules-not-yet), `Mastery.NOT_YET`), the set or build isn't played. Anything two levels
above the learner's waits too. Each drill's A1 items come first. The learner's level is the village language's, as for
the stories.

**The format** (`lani.drill/v0`, `bridge/src/drills.ts`; the app's `data/Drills.kt`): `schema`, `id`, `kind`
(`transform`, `rapid`, `build` or `riddle`), `language`, `emoji`, `title` and `about` (per language), `machine_written`
and `review`. Every text is a text per language: the drill's language is what is said, the others are its meaning
(`{"sl": "Micka kuha kosilo.", "en": "Micka is cooking lunch.", "de": "Micka kocht das Mittagessen."}`); English is
always there.

- `transform`: `sets`, each with an `id`, a `rule`, a `level`, `do` (the instruction: `{"sl": "V preteklik", "en":
  "Into the past."}`, the English spoken) and `items` (`from`, `to`, and an item's own `do`).
- `rapid`: `sets`, each with an `id`, a `level`, a `rule` if it has one, a `title`, `items` (a rapid answer is a text
  whose drill-language part is the answer and whose English is the question) and `numbers` (`{"from": 1, "to": 100}`
  or `{"list": [101, 250]}`, Slovene only).
- `build`: `builds`, each with an `id`, a `level`, `rules` and 2–5 `steps`: the first said whole, each after it with
  `add` (what it adds, in the bases: `{"en": "tomorrow", "de": "morgen"}`) and longer than the one before.
- `riddle`: a `teller` (a villager: the riddles are in their voice) and `riddles`, each with an `id`, a `level`, a
  `word` (`<pack>/<word id>`) or a `villager`, 2–5 `clues`, `ask` and `answer`.

The bridge refuses a drill that doesn't validate (and logs it); what a valid one names that isn't there (a grammar page,
a pack word, a villager) is logged, not refused. No `/` or `→` in what is voiced: the voice store splits phrases there.

## Android app

The APK comes from one of three places:

- **The GitHub releases** ([fentas/lani/releases](https://github.com/fentas/lani/releases)): `lani-<version>.apk`,
  signed with the project's release key, with its SHA-256. A bridge offers it to its app's self-update with
  `companion/bin/release-app --github` (the latest release, or `--github v0.2.40`).
- **A Docker build**, without a JDK or an Android SDK: `companion/bin/lani-build-app` (below).
- **A local Gradle build**, with JDK 21 and the Android SDK (platform 36, build tools 36.0.0):

```bash
cd companion/android
JAVA_HOME=$(mise where java@temurin-21) ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A Gradle build also needs [bun](https://bun.sh) 1.3.13 (the `bun` on the PATH, else `LANI_BUN` or `-Plani.bun`): the
bridge in the app (`app/build.gradle.kts` `phoneBridgeBundle`) is the bridge's TypeScript bundled at build time, not
kept in the repository; the task installs the bridge's packages first when they aren't there. The build image has it.
With it go the curated content the bridge reads (`phoneProject`: the culture packs, modules, packs, dictionary …, 4.1
MB compressed) and QuickJS's library (0.45 MB a processor type).

**The processors.** The release APK runs on 64-bit ARM phones (`arm64-v8a`) only: the offline voice's runtime
(sherpa-onnx, about 24 MB a processor type) would make it several times larger for all four. The debug build adds
`x86_64`, for the emulator. The native libraries are compressed in the APK (`useLegacyPackaging`), so an update
downloads less; the phone unpacks them once at install. The build gets sherpa-onnx's AAR from its GitHub releases (no
Maven repository has it) and checks its SHA-256 (`gradle/verification-metadata.xml`; a new version needs its checksum
there). Measured in October 2026: the release APK went from 22.2 to 31.0 MB with it (the library is 24.2 MB, 9.0 MB
compressed), the debug APK from 30.5 to 51.3 MB (both processor types).

**R8.** The release build is shrunk by R8 (`isMinifyEnabled`, `isShrinkResources`, `proguard-android-optimize.txt`):
the code no path reaches and the resources no code uses are left out, the rest is optimized and its names shortened.
Measured in October 2026, in MiB: the release APK went from 30.7 to 17.5, its dex from 18.3 to 5.7 and its Android
resources from 1.2 to 0.6 (517 files to 359); the native libraries (8.6) and the bundled content (the Java resources
and assets, 2.5) stay as they were. The libraries bring their own rules (kotlinx.serialization's serializers, WorkManager's
workers, Media3, OkHttp, CameraX, DataStore, Compose), and AAPT's keep the manifest's activity, services and receivers.
`app/proguard-rules.pro` keeps what only the app finds by name, each rule with what reads it: sherpa-onnx's package
(its native library finds its classes and fields by name), the exercise classes' names (an exercise's type in the
results is its class's simple name) and the source lines for [crash reports](#crash-reports). The workers keep their
names, so work that an app from before R8 scheduled finds its worker after the update; an R8 build installs over one
from before as any update does (the same key, a higher versionCode). A crash or a screen that loads nothing in a
release build only is a rule missing: `companion/bin/qa --minified` runs QA on the qa build, the release build's R8
with QA's hooks ([Test](#test)).

To the phone without a cable: `companion/bin/lani-send-app` sends the newest APK of the release directory (where
`release-app` and `release-app --github` put it; another with `--file`) over Tailscale (Taildrop), to the only device
Taildrop can reach or to the one named (`lani-send-app <device>`). On the phone it waits in Files → Downloads.

### Build in Docker

```bash
companion/bin/lani-build-app                          # the debug APK, app/build/outputs/apk/debug/app-debug.apk
companion/bin/lani-build-app --release --out ~/apk/   # the release APK, as ~/apk/lani-<versionName>.apk (and its mapping)
companion/bin/lani-build-app --test                   # the unit tests first, then the debug APK
companion/bin/lani-build-app -- :app:testDebugUnitTest --tests '*RoadPlayerTest'   # any Gradle arguments
companion/bin/release-app --docker "notes"            # build in Docker and publish to the app's self-update
```

The image (`companion/android/docker/Dockerfile`, about 1 GB) has Temurin JDK 21.0.11, the Android SDK command-line
tools 20.0, platform `android-36`, build tools 36.0.0, the platform tools and bun 1.3.13 (the phone bridge's bundle);
the base image is pinned by its digest, the command-line tools and bun by their SHA-256. `lani-build-app` builds it on first use and again when the Dockerfile changes,
as `lani-android-build:local`. The build runs as you, so its files are yours, with:

- the repository mounted at the same path, and a worktree's git directory with it (the version is the commit count);
- `~/.cache/lani/android-build` as the build's home (`LANI_BUILD_CACHE`): the Gradle cache (about 1.5 GB after the
  first build, which takes a few minutes; later ones are quicker) and Robolectric's jars;
- the debug key a local Gradle build signs with, read-only: `debug.keystore` in the Android user home the Android
  Gradle plugin uses (`ANDROID_USER_HOME`; else `$XDG_CONFIG_HOME/.android` when `XDG_CONFIG_HOME` is set, as some
  desktops do; else `~/.android`), so a debug build is signed as a local one is and the phone takes either as an
  update. The build's own Android user home goes with the container: without a debug key there, it signs with a
  throwaway key (as on CI) and says so, and it never creates or overwrites a keystore;
- a memory limit of 8 GB (`LANI_BUILD_MEMORY`, empty for none); a build with the unit tests peaks at about 5 GB.

Docker comes first; Podman (`LANI_CONTAINER=podman`, or when Docker isn't installed) runs rootless with
`--userns=keep-id`, and is less tried. `--exec <command>` runs a command in the build container, in
`companion/android`, such as `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`; `--image`
only builds the image and prints its name.

The same commit and the same key give the same APK, byte for byte, in Docker and with a local Gradle: the release
build leaves out the dependency report that AGP puts in the signing block for Google Play, which differs on every
build, and R8 gives the same code and the same mapping for the same input and rules.

### Signing

A debug build is signed with the debug key (`debug.keystore` in the Android user home: `ANDROID_USER_HOME`, else
`$XDG_CONFIG_HOME/.android` when `XDG_CONFIG_HOME` is set, else `~/.android`; a Docker build takes the same one). A
release build is signed with the release key when one is set, else with the debug key, as the sideloaded app always was:

- `LANI_KEYSTORE` (the keystore's path), `LANI_KEYSTORE_PASSWORD`, `LANI_KEY_ALIAS` (`lani` if unset) and
  `LANI_KEY_PASSWORD` (the keystore's if unset), as the release workflow sets them;
- else `~/.config/lani/signing.properties` (`LANI_CONFIG_DIR`): `storeFile` (relative to the file), `storePassword`,
  `keyAlias`, `keyPassword`.

`companion/bin/lani-keystore` creates both files there and shows how to give GitHub the same key. A phone takes an
update only with the key its app was installed with: changing keys means installing the app again once. See
[docs/releasing.md](../docs/releasing.md) for the key, the GitHub secrets and the tags.

### Crash reports

A release build's stack trace (`adb logcat`, Android's crash dialog, a bug report) names R8's short classes and
methods and no file, with R8's numbers for the lines (`at gx.p(Unknown Source:129)`). The mapping of that very build
turns it back into the source's classes, methods, files and lines (`at
androidx.camera.core.impl.CameraValidatorImpl.validateOnFirstInit(CameraValidatorImpl.java:136)`). It is never in the
APK, and each build has its own:

- `release-app` keeps it next to each APK in the release directory, as `lani-<versionCode>-mapping.txt.gz` (about
  10 MB, 150 MB unpacked), and drops it with its APK;
- `release-app --github` gets it from the GitHub release (`lani-<versionName>-mapping.txt.gz`, its SHA-256 in
  `lani-<versionName>.json`); the workflow attaches it to every release, public as the source it maps to;
- `lani-build-app --release --out <dir>` puts it beside the APK, and a Gradle build leaves it in
  `app/build/outputs/mapping/release/mapping.txt`.

```bash
gunzip -k ~/.local/share/lani/releases/lani-1180-mapping.txt.gz
~/Android/Sdk/cmdline-tools/latest/bin/retrace ~/.local/share/lani/releases/lani-1180-mapping.txt crash.txt
```

`retrace` comes with the SDK's command-line tools (in the build image too: `lani-build-app --exec retrace …`).

### Install and pair

On first launch, scan the QR code from `companion/bin/lani-pair` ("📷 Skeniraj QR · Scan the QR code"),
or enter the bridge URL (`tailscale serve status`) and the token. Without a node, choose "☁️ Lani na tem telefonu · Lani
on this phone" instead ([Lani on this phone](#lani-on-this-phone-no-node)). For Slovene speech, install the
Slovenian voice in Android's text-to-speech settings (Google Speech Services → sl-SI).

The story notebook's handwriting is Kalam (Indian Type Foundry, SIL Open Font License 1.1), bundled in
`app/src/main/res/font/`; its licence and credits are in `app/src/main/assets/fonts/`. The ambient sounds' are in
`app/src/main/assets/ambient/CREDITS.md`.

### Answer fields and the keyboard

What the learner types as an answer (an exercise, a review card, a dialog's typed turn, what they say in a talk) gets
no suggestion strip and no auto-correct: both would give the words away (Gboard's Slovene makes "hisa" "hiša"). The
keyboard opens in the answer's language, and the learner can still switch it as in any other field.
`ui/AnswerKeyboard.kt` (`NoSuggestions`, `AnswerEditor`) asks for this on each answer field's `EditorInfo`:

- plain text with `TYPE_TEXT_FLAG_NO_SUGGESTIONS`, without `TYPE_TEXT_FLAG_AUTO_CORRECT`;
- `privateImeOptions` `com.google.android.inputmethod.latin.noDecoding`: Gboard shows its toolbar instead of the
  strip and does not decode (no suggestions, no auto-correct, no glide typing). The globe key, a long press on the
  space bar and the long-press letters (č on c) work as in any field. Gboard does not document this option. If a
  Gboard update drops it, the strip comes back (still without auto-correct), and `typed-turn` in `companion/bin/qa`
  fails;
- `hintLocales` with the answer's language code (the learner's target; on a visit, the town's language): `sl`.
  Gboard opens in the learner's Slovene layout. A language that is not installed (`it`) is used for that field only.
  The next field without a hint (the tutor chat) gets the learner's own language again.

The tutor chat keeps the keyboard's suggestions and its language. The letter chips under an answer field (č, š, ž …)
stay. Until October 2026, answer fields were visible passwords (`TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`). That removed
the strip, but in a password field Gboard drops the globe key when the emoji key is shown, and on Jan's phone it kept
to English.

What Gboard 15.1.08 (the QA emulator, Android 16; English, Slovene QWERTZ and German installed) does with an answer
field, after "teh" or "hisa" is typed:

| EditorInfo | Strip | Auto-correct | Language switch | Layout with hint `sl` |
|---|---|---|---|---|
| plain text (auto-correct flag off) | suggestions | on: "teh" → "the", "hisa" → "hiša" | globe, long press on space | Slovene |
| + `TYPE_TEXT_FLAG_NO_SUGGESTIONS` | suggestions: Gboard honours the flag only in its own list of apps | off | yes | Slovene |
| + `IME_FLAG_NO_PERSONALIZED_LEARNING` | suggestions, incognito icon | off | yes | – |
| `URI`, `FILTER`, `PERSON_NAME`, `WEB_EDIT_TEXT`, `PHONETIC`, `POSTAL_ADDRESS`; `TYPE_TEXT_FLAG_AUTO_COMPLETE` (each with no suggestions) | suggestions | off | yes | – |
| `EMAIL_ADDRESS`, `WEB_EMAIL_ADDRESS` (with no suggestions) | the typed word only | off | yes, "@" instead of the comma | – |
| `VISIBLE_PASSWORD`, `PASSWORD`, `WEB_PASSWORD` (until now) | none, a number row | off | globe key only when it replaces the emoji key, else long press on space; none on Jan's phone | Slovene |
| `privateImeOptions` `noDecoding` (without the package) | suggestions | off | yes | – |
| `privateImeOptions` `com.google.android.inputmethod.latin.noDecoding` (now) | none, the toolbar | off | globe or emoji key as in any field, long press on space | Slovene (no name on the space bar) |

## Test

```bash
cd companion/bridge && bun test/smoke.ts
cd companion/bridge && bun test/relay.ts         # the relay end to end: three towns' bridges through it (LANI_RELAY_SMOKE_PORT, default 18870 and the next three)
cd companion/relay && bun test                   # the relay's unit tests
cd companion/android && ./gradlew :app:testDebugUnitTest
cd companion/android && ./gradlew :app:testDebugUnitTest --tests 'si.lanisce.lani.phone.*'   # the bridge in QuickJS, and against a node's (port 19110)
adb shell am broadcast -n si.lanisce.lani/.phone.PhoneBridgeProbe   # the bridge in the app timed on a device (debug, qa); adb logcat -s PhoneBridgeProbe
companion/bin/lani-build-app -- :app:testDebugUnitTest   # the same without a JDK, in Docker
python3 -m unittest discover companion/tests     # backups, the Whisper worker (models faked)
python3 tests/test_update_db.py                  # update-db.py
python3 -m unittest discover -s tests            # update-db.py, card.py, the languages, the old names, lani.env (test_config), the data repository, lani-setup end to end
companion/bin/qa                                 # end to end, on the lani-qa emulator
companion/bin/qa --steps visit                   # only the visit of the second town (and setup)
companion/bin/qa --steps road                    # only the car's "🚗 Za pot" (and setup), a few minutes
companion/bin/qa --steps trade                   # only a trade of food for wood (and setup), about 3 minutes
companion/bin/qa --steps sheets-settle           # flings into the ends of Home and two sheets: each comes to rest
companion/bin/qa --steps cheat-sheet             # the cheat sheet from Home, from an exercise, a tutor message pinned from its long press
companion/bin/qa --steps chat-swipe              # a tutor message swiped: the reply card, bookmarked, taken away and undone; the list still scrolls; copy, share
companion/bin/qa --steps chat-links              # a tutor message's links: example.com and http ask first, fran.si opens the browser at once
companion/bin/qa --steps chat-attach             # 💬 in an exercise's top bar: the exercise attached, ✕, the answer with it; chat-attach-pack: a pack's word
companion/bin/qa --steps chat-photo              # a photo (GPS EXIF) from the gallery and one from the camera: the node keeps them upright, 1568 px, without EXIF; full screen, deleted
companion/bin/qa --steps village-hud             # the village's HUD: its bar and cards, the ring, each card's sheet, German, the plot chooser's ★
companion/bin/qa --steps scroll                  # the village's scroll: its count, the grid, each collection, a grammar page, the count cleared, German
companion/bin/qa --steps scene-people            # I spy in the church, the kitchen, the smithy: who stands where (SCENES.md "Where people stand"); the church's top bar in German, its pills apart
companion/bin/qa --steps bubbles-follow          # the village's bubbles with their people: dragged, zoomed in, the edge chip tapped
companion/bin/qa --steps village-life            # the village's day on the map at 8, 11, 15, 19 o'clock, walkers by the field
companion/bin/qa --steps age-up                  # Zaselek to Vas and Vas to Trg: each moment beat by beat, its card, the veselica at A1 to its end, the chronicle's Watch again (recorded)
companion/bin/qa --steps exercise-words          # a review's words looked up: the sentence's, not the forms to choose
companion/bin/qa --steps exercise-explain        # the module kdo-govori's explanation in shape, «delam» in it looked up
companion/bin/qa --steps counting                # counting cards: the unit stevila's intro skipped and its note, a card's four steps (one wrong), the lines said, the cheat sheet, 💬, typed
companion/bin/qa --steps counting-dialogs        # the happenings with a counting dialog (Luka's cows): each opens, its number in words
companion/bin/qa --steps numbers                 # 🔢 Števila: closed with "📖 Odpri uvod", the intro skipped; a number heard on the pad, a word with the tens first, a clock time, a date, a whole run opening 100–999
LANI_DEV_FAKE_ELEVENLABS=1 companion/bin/qa --no-town --steps forms-card  # forms cards: Vse oblike on a word's card (its overview, then Vadi oblike), a noun's table (greyed rows, a mistake kept by kind, the rows said), a verb's (its imperative hidden), the day's in a review, typed, a unit done bringing a card back
LANI_DEV_FAKE_ELEVENLABS=1 companion/bin/qa --no-town --steps forms-view  # biti's card in a test: its present alone; the overview after it and from the word's card; Moje besede from Napredek, a search with its dictionary part, a word opened from it
companion/bin/qa --steps school                  # the school: from Home's tile, the unit biti's intro (read aloud), sheets and test
companion/bin/qa --steps school-build            # the school built in the hamlet: the Build sheet's tenth plot, the line under it, the bed, indoors
companion/bin/qa --steps school-scene            # the teacher in the school's scene: her requests as on the map, the lesson by her, the blackboard
companion/bin/qa --steps school-seats            # the school's scene at 10: the pupils at their desks and the blackboard's lesson, zoomed in
companion/bin/qa --steps school-print            # the unit biti's sheet printed: fit.js in the WebView as in Chromium, the print dialog, the PDF saved
companion/bin/qa --steps shadow-tutor            # the shadow tutor on QA's mock: the developer section, a fake key, a chat and a review, the panel, the export, the effort per task and within the thread
companion/bin/qa --steps shadow-matrix           # the shadow's matrix on QA's mock: the Matrix tab on (nine lanes), a chat answered by every lane at its model and effort, the event's lane runs, today's spend per lane
companion/bin/qa --steps dev-upload              # the developer's data on the node: a chat, the app to the background, app/dev/ in the dev bridge's copy (no key, no token), the panel's button, nothing with the setting off
LANI_DEV_FAKE_ELEVENLABS=1 companion/bin/qa --minified --steps voice-node-clip  # a new line a villager says plays the node's clip (not the phone's voice), also with the cap full
companion/bin/qa --minified                      # the same on the qa build: the release build's R8 and rules, with QA's hooks
companion/bin/qa --apk <file>                    # an APK built before (a debug or qa build), nothing built
companion/bin/qa --dry                           # the plan: bridges, ports, the fixture town, the steps
companion/bin/qa --bridges-only                  # no app: both dev bridges, linked, and what a visit asks
```

**QA on the release build's R8** (`--minified`). The qa build type (`app/build.gradle.kts`) is the release build as
R8 makes it, with the same rules and optimizations, plus QA's hooks, fixtures and logs (`BuildConfig.QA`), the debug
manifest's plain HTTP to the dev bridge, `x86_64` and the debug key. It isn't debuggable (a debuggable build gets R8's
debug mode, which optimizes less), so QA reads the app's files with the emulator's `su` where a debug build has
`run-as` (a Google APIs image has it, a Play Store image doesn't). A crash, or a screen that loads nothing, there and
not in the debug build is a keep rule missing (`app/proguard-rules.pro`). In October 2026 every step but the visit's
ran there as on the debug build, with no crash: pairing, Home, the village and its HUD, scenes and dialogs with tap
and typed turns, I spy, reviews, packs and runs of every exercise type, the school (its intro read aloud, its four
sheet types, the test, a sheet printed through fit.js), the cheat sheet, the chat, trade, the car, the offline audio
and Piper's voice (sherpa-onnx through JNI), and the QR scanner (CameraX) by hand. `cheat-sheet` and `chat-swipe`
failed on both builds alike, at QA's checks of the pin under the tutor's list (a choice of the message's long press
now) and of the swipe's tip.

**CI.** `.github/workflows/ci.yml` runs on every push and pull request: the bridge's typecheck
(`bunx tsc --noEmit -p .`) and the whole smoke, which needs no local workers, network or tmux (it starts its bridges
on 18800 and the ports after it, and fakes the voice and speech servers; ffmpeg is installed for the voice checks);
both Python suites; and the app's unit tests with the debug APK, built with `companion/bin/lani-build-app` in the
build image, the APK kept for a week as an artifact, then the release APK with R8 (`lani-build-app --release`), so an
R8 error shows on the push and not first on a release tag; without the release key it is signed with a throwaway
debug key and not kept. The QA on an emulator stays local. The release APKs come from
`.github/workflows/android-release.yml` (see [docs/releasing.md](../docs/releasing.md)).

**The road's player in the unit tests.** `road/RoadPlayerTest` runs under Robolectric (a test dependency only, with
media3's test utilities: a fake clock and fake renderers). It builds `RoadPlayer` over a real ExoPlayer with road
items as `RoadSources` makes them (WAV prompts and clips, pauses of silence: one window of pieces, a period each) and a
media session. The session and the test read the player's whole state at every change, also while pieces are still
being prepared and have no duration. It checks the commands a car can send, and next in the pause to say it and after
it. With 0.1.543's `ForwardingSimpleBasePlayer` both tests fail with the crash from the car, "Periods other than last
need a duration". A third plays a quiz's question (`RoadQuiz.compose`, three options, twice): next during the question
goes on to the options, next while an option is read, in its gap or just into the next one's number picks it,
previous asks the question again, play/pause picks during the options and pauses during the question; a card plays on
as ever. It seeks to exact positions with the player paused: `untilPosition` overshoots with these many short pieces.
The quiz's pure parts have their own tests: `RoadQuizTest` (a question's sounds and parts, each button at each moment,
every kind of question, the session's order, the fixed phrases), `RoadQuizCountTest` (what the answers count),
`RoadMenuTest` (the tabs, the old ids, the custom actions, "📖 Zgodba") and `RoadWorkTest` (the spoken texts got ready).

**Sheets that settle.** Every sheet of the app is a `LaniSheet` (`ui/LaniSheet.kt`): Material's `ModalBottomSheet`,
always fully open, whose room ends at the status bar, its content padded only at the bottom. Material (1.4) pads a
sheet's content by the part of the status bar it covers, so a sheet in reach of the status bar changed its height as it
moved; its anchor moved with it, and the anchored-drag animation starts again with the fling's first speed whenever the
anchors change. A fling into the end of a sheet's list hands its leftover speed to the sheet, which then bounced until
the next touch (Jan saw it in 0.2.107, in the tutor chat). `ui/LaniSheetTest` runs a sheet under Robolectric (the
app's and the libraries' resources included: `unitTests.isIncludeAndroidResources`) in a phone's window with a status
bar, hands it a fast fling's leftover speed as a list at its end does, and draws every 16 ms frame: Material's own sheet
still bounces three seconds later, a `LaniSheet` springs once and rests where it was. It also keeps
`ModalBottomSheet` out of the app's code elsewhere. On the emulator, `companion/bin/qa --steps sheets-settle` flings
Home, the tutor chat's sheet and the challenges' sheet into their ends (a monkey script's moves 8 ms apart: `input
swipe` leaves a busy emulator's app no speed) and checks three screenshots half a second apart are the same.

**The village's HUD** (GAME.md, "The village's HUD"). `ui/game/VillageHudLogicTest` checks what it shows and when (the
cards shown or hidden, by the learner and by the plot chooser until its ring is tapped; the ring's age, steps,
festival and quiet day; "Danes in kmalu"'s lines in their order, today's first, each without its day, today's and
tonight's story as news and the pedlar's day not; the days' words, "Nocoj · Tonight", "Torek · Tuesday", "Čez 9 dni ·
In 9 days"; each step's action; the dots, gone once their sheet showed what they marked; what TalkBack reads).
`ui/game/VillageHudTest` puts the HUD on a phone's screen under Robolectric with Compose's test rule (`ui-test-junit4`,
test only; `ui-test-manifest`'s empty activity in debug builds): the bar and the three cards (the settlement's swiped
to), the goal's card with its next step and its button, the bar and the cards under 140 dp; the ring hiding and showing
the cards (a tap, a long press), kept in `HudPrefs` for the next village screen; each card's section in the sheet, what
leads on from it closing it (a step's button, a tile, the ages, progress, a festival's line), its ✕; the timeline
without a doubled day; the dots in the ring's label; the plot chooser hiding the cards and giving them back, the
learner's choice untouched; and a tap outside the open sheet closing it without reaching the map, a drag closing it and
panning on. On the emulator, `companion/bin/qa --steps village-hud` (also after `ages` in a whole run; with
`--minified` on the qa build too) checks the bar and the cards (their bottom under 600 px, where the old HUD's panel
reached about 1550 on the 1080 × 2400 emulator), hides them by the ring, goes home and into the village again (still
hidden), shows them, opens each card's section (a tap on the map above the goal's closes it and opens nothing), the
settlement's in sl · de (QA's hook `pair:sl-de`), then opens the plot chooser through QA's hook `build:field` and checks
the cards are hidden, the ★ lies between the bar and the chooser's bar, and the cards are back once the chooser is
left. Screenshots: `hud-cards`, `hud-bar`, `hud-goal`, `hud-soon`, `hud-settlement`, `hud-settlement-de`,
`hud-plot-chooser`.

**The village's scroll** (GAME.md, "The scroll"). `ui/game/VillageScrollTest` checks what's new in it (who joined and the
grammar pages met in the last 7 days, not seen in it yet: the bottom bar's count; the chronicle's lines since it was
opened marked, not counted), that it's gone once the scroll showed it (`ScrollPrefs`, kept on the phone), and on a
phone's screen under Robolectric the grid of four collections with their TalkBack labels ("Prebivalci · Villagers: 3,
Novo · New: 1"), the chronicle's newest line, a collection's list and what its rows lead to, "‹" back to the grid, ✕.
On the emulator, `companion/bin/qa --steps scroll` (after `village` in a whole run; with `--minified` on the qa
build too) reads the count on the bottom bar, opens the grid (no "Danes v vasi": today is the HUD's), each collection
and back, a grammar page over the grammar list (its examples) and back to it, checks the count is gone once the scroll
was open, and the grid in sl · de (QA's hook `pair:sl-de`). Screenshots: `scroll-badge`, `scroll-grid`, `scroll-places`,
`scroll-villagers`, `scroll-grammar`, `scroll-grammar-page`, `scroll-grammar-examples`, `scroll-chronicle`, `scroll-badge-cleared`,
`scroll-grid-de`.

**The village's bubbles** (SCENES.md, "Bubbles in the village"; VILLAGERS.md, "Round the fire"). `ui/game/TownLogicTest`
checks the layout: a bubble whose place is off the screen's left, right or bottom is off it too, over its place; one at
the edge is nudged in by 22 dp at most (whole while its place is on the screen) and slides off with it, never jumping; a
huddle of eight is pushed a bubble and a half at most, below none of their starts, stacked so two fifths of each shows,
and where there's room the reach changes nothing; three places panned off the left keep their bubbles' offsets within a
hair from step to step; the crowd round the fire (nine bubbles over six people) keeps off the fire and below the HUD,
stacked as cards, and held from frame to frame it jumps about half as often as without and hardly flips back (362
jumps and 11 flips against 675 and 145, zooming from 4.5 to 12, where the app shows the people's bubbles one by one);
the edge chips count the bubbles off each side, the event's first,
else the nearest. `game/render/FireRingTest` checks the same over the renderer's fire ring, `ui/scene/BubblePlaceTest`
a scene's marker over someone at the view's edge. On the emulator, `companion/bin/qa --steps bubbles-follow` (also
after `camera` in a whole run, QA's hook `hour:11`: the people out) shoots the village at home, dragged both ways,
zoomed in and dragged, prints the bubbles on screen in each, then taps the edge chip and checks its first bubble is on
screen then. Screenshots: `bubbles-home`, `bubbles-dragged-right`, `bubbles-dragged-left`, `bubbles-zoomed`,
`bubbles-zoomed-dragged`, `bubbles-chip-tapped`.

**Swipes in the chat** ("Tutor chat", "Swipes"). `ui/ChatSwipeTest` checks the swipe's decisions (sideways past the
touch slop and within 30°, the list's past it any other way, too short to tell; the resistance; the threshold both
ways) and `ChatController`'s toggle and undo (the bookmark back with its note). It puts bubbles on a phone's screen
under Robolectric with Compose's test rule: a swipe to the right past the threshold replies, the bubble following the
finger and back at rest after; one short of it does nothing; one to the left bookmarks (its 🔖), a second takes the
bookmark away; a long press still opens the menu, TalkBack keeps its actions, and a drag after a long press is no
swipe; in a list of 30 bubbles a drag up and a steep one scroll the list, a sideways one with a wobble is the bubble's
and the list stays where it was; a table that fits is the bubble's to swipe (before, its sideways scroll took the drag
though it had nowhere to go), a wider one scrolls; with "Remove animations" the bubble is at rest a frame after it was
let go, with animations still on its way back; the typing bubble doesn't move. On the emulator, `companion/bin/qa
--steps chat-swipe` puts two tutor messages into the chat through QA's hook `chat:<text>` (a long one, and a short one
starting with a bold Slovene greeting, a link to hear it) and swipes with `input swipe`: the tip under the latest (it
runs before the other chat steps, whose chat would show the tip first; after one of them, it checks the tip is gone); the
reply card after a swipe to the right from the greeting (the tip gone); the 🔖 and "🔖 Shranjeno · Saved" after one to
the left; after a second the 🔖 gone, and "Razveljavi · Undo" bringing it back (tapped right after one look at the
screen: the snackbar stays four seconds, a uiautomator dump takes one or two); "Zaznamek odstranjen" after a third; a
slow drag down on the long message scrolling the list (the short one 300 px lower or out of sight) with the sheet still
open; and a long press on it: the menu with "📄 Kopiraj · Copy" (Android's own confirmation) and "↗️ Deli · Share"
(the share sheet, its preview titled "Deli sporočilo · Share message"; Back returns to the chat). Screenshots:
`chat-swipe-tip`, `chat-swipe-reply`, `chat-swipe-bookmarked`, `chat-swipe-undo`, `chat-swipe-removed`,
`chat-swipe-scrolled`, `chat-menu`, `chat-copied`, `chat-share`. `ui/MarkdownPlainTest` checks what Copy and Share
give: the tutor's imeti list and the 1 · 2 · 3+ table of pronouns as plain text, headings, quotes, numbers and rules as
they read, the learner's own message as typed.

**Links in the chat** ("Tutor chat", "Links"). `data/LinksTest` checks what is a link and where it ends: a Markdown
link's text and URL (with a title, in `<…>`, with parentheses inside), a bare URL without the full stop, comma, `)`,
`*` or quote after it, an autolink's brackets; fran.si, its subdomains and Lani's repository opening at once, and
nothing else (not `fran.si.example.com`, not `github.com/fentas/lani-other` nor `/fentas/lani/../../`, not
`gist.github.com`, not http); `javascript:`, `intent:`, `file:`, `content:`, `mailto:`, an app's scheme and a scheme-less
`www.` staying text; a user name before the host, a backslash, an invisible character, an IPv6 address, an encoded
host refused; a look-alike name as `xn--…`. `ui/MarkdownLinksTest` checks the drawing: the link's annotation over its
text, a `*` in a bare URL no italics, a link's own bold, a link in `code` code, the 🔈 and the words to look up beside a
link but never around it, a link inside the segment a step's reading marks, the copy's "text (URL)", an explanation's
endings never bold inside a URL. `ui/LinkGateTest` taps them under Robolectric with Compose's test rule: fran.si opens
the browser at once (a browsable view, no dialog); example.com asks first ("🔗 Odpreti example.com? · Open
example.com?"), opens nothing on Cancel and the browser on Open, also in a sheet's window (`LaniSheet`, and the
Markdown in another sheet: the platform's handler there opened it at once on the emulator before `GateLinks`); a link
whose text names fran.si asks for where it goes; http asks with "⚠️ Povezava ni varna"; without a browser, the toast. On the emulator, `companion/bin/qa --steps
chat-links` puts a tutor message with three links into the chat (QA's hook `chat:<text>`) and taps them: example.com's
dialog and its Cancel, the http one's warning, fran.si's opening the browser at once (the browser in front, then the app
again and the browser stopped). Screenshots: `chat-links`, `chat-link-asks`, `chat-link-http`, `chat-link-fran`.

**The exercise on screen in the chat** ("Tutor chat", "The exercise on screen"). `ui/AskAboutTest` checks
`chatContext` before an answer (`answered: false`, `expected`, the options and the source, no answer, verdict or hint;
a label with a sound as 🎧, a card's front, no arrow) and after it (the answer, "→" in the label); `app/ChatContextTest`
`ChatAbout`'s word (the pack, the word and its card, no `variant`) and reading (the text, the questions, the challenge
without `exercise_index`); `app/ChatAttachTest` `ChatController.openFrom`: the chat open with it attached and nothing
sent, the next message carrying it, ✕ sending a plain question, the chat closed unsent letting it go (and opened again
with none), "Ask"'s kept. On the emulator, `companion/bin/qa --steps chat-attach` opens imeti-in-iti by its link and
taps 💬 in its top bar: the chip "📎 …" above the input before the answer (no "→", nothing sent), its ✕, the exercise
again; answered, 💬 again: the chip with "→" and the answer. `--steps chat-attach-pack` opens an unfinished pack's
first word and checks "📎 <word> = <meaning>". Screenshots: `chat-attach-open`, `chat-attach-removed`,
`chat-attach-answered`, `chat-attach-pack`.

**Photos in the chat** ("Tutor chat", "Photos"). `data/JpegTest` checks the JPEG's segments: a camera's EXIF (GPS,
make, model, orientation 6) and a comment named and taken out, the rest byte for byte, a colour profile kept, a cut-off
or other file left alone; `data/PhotoPrepTest` (Robolectric's native graphics: Android's real decoder and encoder)
the size (1,568 px on the long side, a smaller one as it is), a camera's 2000×1000 JPEG with GPS EXIF and orientation 6
coming out 784×1568, upright (what was left is on top) and without EXIF, place, make, model or comment (also as
`ExifInterface` reads it), a transparent PNG on white, the 48 MB cap while reading; `data/OutboxPhotoTest` the outbox:
one entry under the photo's id, the photo PUT before the message (its bytes as on the phone), offline through a
restart and sent once the node is back, a retry sending both again under the same id, an older node (404) and a photo
deleted before it went getting the text alone with the tutor's line, 413/415 refusing the write, 5xx retrying, the
DELETE; `app/ChatPhotoTest` the controller: a photo made ready (upright, no EXIF) and above the input, ✕, a photo alone
a message waiting in the outbox with it, with text and quoted by a reply (`reply_to.photo`), held back while being made
ready, what isn't a photo, deleted (marked, off the phone, the DELETE queued), the sweep, the thumbnail; the shadow's
`tutor/ShadowTutorTest` the image block before the event (its base64 the phone's file), the call's `images`, the export
without it, and a photo no longer on the phone. The bridge's smoke (`test/smoke/chat-images.ts`) checks the routes: the
tokens (none 401, the family's 403), 8 MB (413), a non-image (415), a JPEG with GPS EXIF kept without it, its size,
0600 and 0700 under `app/chat-images/<YYYY-MM>/`, the repeat (`existing`), GET and DELETE, `/message` with `image` (the
tutor's `data.image`), an unknown id and another kind (400), a quoted photo, an `image` the app put in `data` dropped,
PNG and WebP metadata, the `.gitignore` line at the start (once) and in lani-setup's template, an old month pruned;
`tests/test_data_repo.py` that no commit takes a photo, also in a repository from before the line. On the emulator,
`companion/bin/qa --steps chat-photo` makes a shop's sign as a phone takes it (its pixels on their side, EXIF
orientation 6, a make, a model and a place), puts it in the emulator's gallery (`content call … scan_file`), picks it
in the system's photo picker, sends it with a line of text and checks the thumbnail (upright), the dev tutor's answer,
the dev bridge's copy (`app/chat-images/<YYYY-MM>/`, 0700 and 0600, 1568 px landscape, no EXIF, make or model), the
chat event's `image` in `bridge.log` (the dev tutor logs it), the photo full screen and zoomed by a double tap, its
deletion from the long press (gone on the node); then the camera, allowed when asked, the virtual scene taken and sent
alone; the gallery's photo is taken away again. Screenshots: `chat-photo-menu`, `chat-photo-draft`, `chat-photo-sent`,
`chat-photo-full`, `chat-photo-zoomed`, `chat-photo-menu-long`, `chat-photo-delete-ask`, `chat-photo-deleted`,
`chat-photo-camera`, `chat-photo-taken`, `chat-photo-camera-sent`.

**QA of the trade** (`--steps trade`, after the gathering runs; GAME.md, "Trading"). From the village (on its own, it
goes in from Home), food's sheet leads to "⇄ Zamenjaj · Trade"; the trade's sheet gets wood (its lower chip: the
upper one is wood to give), its most is given, and the step reads what goes out from the preview ("−194 🌾 → +97 … 155
🪵"). The run is answered to its end whatever the exercises are; the result must show what went out and came in and
the rate's line ("3/6 pravilno · 3/6 right: 0,65"). Back in the village the HUD's chips (their TalkBack labels, "Hrana
· Food: <stock> / <cap>") must say less food and more wood, and the dev bridge's village (`GET /game`) must keep today's
trade, food given. On Jan's data (October 2026) it gives 194 🌾 of 389 for 97 to 155 🪵. A village that can't trade
food for wood now (the wood store full, food traded away today) skips the step and says why.

**QA of the car** (`--steps road`, the last step). On a fresh install, it opens the 🚗 sheet and asks the debug build
for a small library (`road:mini`): two items of each kind from the dev bridge (cards, words, dialogs, stories,
phrases, drills) and eight quiz questions (the kinds in turn) with the quiz's fixed phrases, their clips downloaded,
their prompts rendered and the quiz's texts the voice store lacks voiced (the dev bridge has no engine: the
emulator's own voice) in the background. It waits until the sheet says
"✅ Končano" and reports the notifications ("🚗 Pripravljam za pot … %" if it is quick enough to catch it, "Končano" at
the end). Then it:

1. starts "▶ Nadaljuj · Continue" from the sheet's menu, and checks with `adb shell dumpsys media_session` that the
   app's session is PLAYING with a current item (what a car sees);
2. follows the item with the road service's dump (`adb shell dumpsys activity service si.lanisce.lani/.road.RoadService`,
   a debug build's: the session, the item, its piece and what it is, such as `pause:SAY`, and each session's items on
   the phone). When a pause to say the Slovene begins, it sends next (`cmd media_session dispatch next`, as a steering
   wheel does). The same item must go on past the pause (the answer at once). Next again must go to the next item. The
   app must still run (`pidof`) and play after each;
3. pauses (`dispatch pause`) and checks PAUSED;
4. starts "❓ Kviz · Quiz" from the sheet. The dump says which part of a question plays (`quiz=TEXT:1`: the second
   option, being read). Then it sends next: the question's feedback must play ("✓ …" or "✗ …" in `dumpsys
   media_session`), with the pick in the dump (`last=<question>:1:right`). During the next question's options it sends
   previous: the same question again from its start (`ASK`). Then it pauses;
5. plays the car's part (`road:car`, `road/RoadCarCheck`). The platform's media browser and controller, the protocol
   Android Auto speaks, connect to the service and browse the root: two tabs, "🚗 Za pot" a grid of four (`road:mix
   road:quiz road:easy road:story`) and "Več" a list of nine. Then they play each session from its media id (the
   service's `onSetMediaItems`), until its first item plays. The app logs a line each (tag `RoadCar`). Every session
   that has items on the phone must play, the quiz too, none may fail ("Failed to load selection" in a car), and the app
   must still run at the end, paused.

Android Auto's own screen (the grid's look, its pictures) isn't checked: the Desktop Head Unit isn't installed here.
The step takes about 2 minutes (with setup and without the build, about 3 minutes). With 0.1.543's player it fails
at step 1: the app closes as the session starts ("Periods other than last need a duration" in the crash check).

**QA of an exercise's words** (`--steps exercise-words,exercise-explain`; [Words in an exercise](#words-in-an-exercise)).
A debug build logs each text of the exercise on screen (tag `ExWords`): what it is (`prompt`, `option`, `tile`,
`correct`, `sentence`, `explain` …), how its words are looked up (`tap`, `hold`, `none`) and where each word is on the
screen, "!" after one that can't be looked up and "*" after one dotted as new. `exercise-words` starts a review of form
questions (QA's hook `forms`): the tip shows under the sentence with the gap; a tap on its first word opens the word's
card (Back closes it, the review stays); every option (a form: what's tested) logs `none`, and a long press on one's word
opens nothing and picks nothing; answered, the options log `tap`, and a tap on the form in the feedback opens its card,
with the table of its forms. `exercise-explain` opens Jan's module `kdo-govori` (in the dev bridge's copy; skipped
without it) by its link, answers its first three choices and checks the third's explanation in shape: "dela: no -m and
no -š, so he or she." a line of its own, "I work", "delam" … the cells of a table; a tap on «delam» opens its card.

**QA with two towns.** `companion/bin/qa` runs the dev bridge (`127.0.0.1:8791`, on a copy of `data/` without its
town links, so QA never asks a learner's town) and a second dev town: `companion/bridge/test/dev-town.ts` on
`127.0.0.1:8797` (`LANI_DEV_TOWN_PORT`), the fixture of `test/fixtures/dev-town.json` ("Borgo di Prova": the Friuli
pack, Italian from Slovene, not a child's; a village at Vas with six people, today's visitor, two open requests and
goods to spare) in a temp data dir, linked with the dev bridge's town as two apps link (an invitation, accepted).
After the village steps, the visit steps go from the scroll's villagers' "🤝 Prijatelji · Friends" into the town ("🧳 In visita ·
Visiting": the labels in Italian), to a villager's card, the sea at the horizon and its scene, the requests and the
market (opened, not played: no tutor needed), the gift's message field with the keyboard up (it and "🎁 Invia · Send"
above the keyboard; nothing sent), and back with "⌂ Casa · Home". Both bridges stop with QA, and on their own
when QA is gone; a program other than a second town on 8797 is left alone (the visit steps skip). `--no-town` leaves
the second town out. `LANI_DEV_FAKE_ELEVENLABS=1` gives the dev bridge a fake ElevenLabs (`test/fakes/eleven.ts`, the
smoke's: tones for clips, a take's time, a plan's concurrency, no voice slots; `LANI_DEV_FAKE_ELEVENLABS_MS`,
`…_MS_PER_CHAR`, `…_CONCURRENCY`), so a clip made on demand runs end to end without the real one: with it, `--steps
school` plays biti's first explanation as its clip (`intro-voice.txt`: `explanation:en`), and the dev bridge's log lists
what the fake was asked, refused and the most at once.

**QA's hooks in a debug build** (`app/QaHooks.kt`, `BuildConfig.QA`; a release build ignores them, the qa build of
`--minified` has them). QA asks them through the launch
intent's `si.lanisce.lani.qa` extra: `happening:<scene>/<happening>` counts that happening as on, so
`dialog-words` opens its dialog through its deep link (`scene:<scene>@<scene>/<happening>`) whatever the hour, the
day's dice or who has been met, then lets it go (`happening:`), and `happening:<scene>/<happening>/<dialog>` with that
variant of its dialogs whatever was heard lately (`fire-peek`: the fire's hide-and-seek); `zoom` zooms the camera on screen in, as its "Približaj ·
Zoom in" action does, where two double taps came too far apart on a busy machine (`scene`, `zoom`); `road:mini` gets
a small library for the car ready, and `road:car` browses and plays the car's sessions as Android Auto does (`road`);
`turns:type` asks the dialogs opened from then on to type every turn that tests a form, as if each rule were secure
(`turns:` lets the learner's masteries decide again). With it, `typed-turn` opens dialogs until one's first turn is
typed, focuses its field and checks with the keyboard up that the field, the letter chips, "Preveri · Check" and the
sentence with the gap lie above the keyboard's top (its frame from `adb shell dumpsys window`, `type=ime`), that the
panel rose over the picture (its ✕ higher, the top bar gone), and that both come back once the keyboard is down.
It also types "hisa" into the field and reads `adb shell dumpsys input_method` to check what the field asked for
([Answer fields and the keyboard](#answer-fields-and-the-keyboard)): plain text with no suggestions, Gboard's
`noDecoding`, and `hintLocales` `[sl]`. With Gboard it also checks that the current subtype is Slovene and that its
candidate strip (`LatinFixedCountCandidatesHolderView`) is not visible.
`ispy:<scene>/<slot>` offers «Vidim, vidim» in that scene whatever the day (a child comes by if none is there), its first
round spying that thing, and the debug build logs where each round's things are on the screen (tag `ISpy`, with the
thing's word); `ispy` (companion/SCENES.md, "I spy") plays a game by the fire: the stump spied, a wrong thing (the
woodpile) tapped in the picture, the child's reaction and the next clue, the stump tapped and cheered, the other rounds'
things through "✋ Izberi raje · Let me choose", the end card. An app without the hooks gets the steps as before (the
fire's card, the scenes' links; the double taps).

Two more are for recordings (the site's clips), not used by QA's steps: `bubbles:<n>` shows only the n bubbles nearest
the fire in the village (`bubbles:` all again), and `clock:<f>` runs the towns' and scenes' time at f of the wall's
(0.05 to 1; `clock:` undoes it). A slow emulator then renders every frame of a recording that is sped up again
afterwards: with `clock:0.2`, set the animator duration scale to 5 too (`adb shell settings put global
animator_duration_scale 5`, back to 1 afterwards) for the camera's flights and the bubbles' bob, record (e.g. `adb emu
screenrecord start --fps 30`), and speed the clip up five times (`ffmpeg -vf setpts=PTS/5,fps=30`).

`season:<day>` shows the landmarks with a season (the maypole, GAME.md "Village projects") as on that day, on the map
and in the scenes, without a line in the chronicle: `season:2027-05-10`, or `season:05-10` for the next 10 May from
today; the rest of the game keeps today, and `season:` goes by today again (screenshots of the maypole up in May and
down in autumn).

`hour:<h>` shows a scene at that hour of today (`hour:7.5`; 0 to 24, to the second): its light, sky and wild animals,
who is there and who sleeps, without the real night sky; the village's map too, its clock standing at that moment (its
light, who is where and doing what, what's on); `hour:` goes by the clock again. With it, `village-life` (VILLAGERS.md,
"A day in the village") looks at the village's day zoomed in at 8, 11, 15 and 19 o'clock without the bubbles
(`bubbles:0`), and at the field beside the fire at the moments someone walks by it (`LANI_QA_FIELD_WALK`, "h:mm[:ss],…");
and `field-cues` (companion/SCENES.md, "Effects") sees each scene at its hour whatever the hour of the run, and plays a
dialog of each to its cue through the happening hook: France's hare in the field at dawn (a burst of shots from the
dialog's opening: the grass stirs, the hare leaps out and is off within four seconds), the pedlar on the road below the
tower in the morning and walking in at the gate, Micka's hen flying onto Marko's cart and the tap turn that finds her
there, Muri barking by the jetty and asleep. A scene the village hasn't opened yet (no market, no tower) is skipped and
said.

`build:<type>` gets the dev village's copy ready to build one (`build:field`): its stores full and a free plot (the next
age's, when the age's own are all taken: Jan's Zaselek has none left), and opens the plot chooser for it over the map,
with its ★ (`village-hud` looks at the HUD over it, its cards hidden, then goes back: nothing is built). `build:` lets it go.
`pair:<target>-<base>` shows the app in that language pair (`pair:sl-de`), as the connection sheet's languages choose
it; `pair:` follows the learner profile again (`village-hud`'s German).

**The school** (GAME.md, "The school"; [COURSE.md](COURSE.md)). `data/SheetsTest` checks the four worksheet types'
items (a gap in its own sentence, a table's cells with their forms, a transform's lines, a sorted word), their grading
(a missing č is almost, a wrong ending wrong, an empty gap wrong), partial credit (an almost counts half; a sheet's
verdict; the test's pass mark), a hint's pay, and what a sheet counts on the grammar book's rules (one answer a rule, its
first slip, a sentence said right). `data/CourseTest` reads a served course (`data/CourseFixture`: the outline in order,
a planned unit, a unit the outline lacks, the intro's steps and growing table, homework, a type of a newer app skipped).
`game/SchoolTest` checks the class (begun with the hamlet: no words, no price, no linden; where it meets, by the fire, under the linden, in the school; its day kept once, the teacher named), the school building the hamlet's (built from Zaselek on the tenth plot a full hamlet has for it, priced within its store; it brings the teacher, a bed first; built there, the class indoors, A2 after A1, its level 2 waiting for A1 before A2's units, also with A2 empty: a level not reached isn't done), the Build sheet's line on it while none stands (`School.buildNote`: by the fire, under the linden, A1 done; nothing without a course or once it stands), the levels (A2 waiting for the
school and A1's units; B1 and B2 for the school's levels, whose upgrades wait for the course: `GameEngine.upgradeOption`
with the gate; a level once open staying open), the unit suggested next and the free order, a unit's steps, the best
try kept, the test passed (the unit done once, its bonus) and tested out, and the progress through a save (and a save
from before). `SimulationTest` plays the school (GAME.md, "Pacing" and "The school"). The intro read aloud
(COURSE.md, "Read aloud"): `data/IntroSpeechTest` splits a step's `say` into its runs (biti's first step: "Biti", "(to
be) has …", "jaz sem", "(I am),", … in order, each with its language and where it is shown; quotes and italics, English in
bold, a sentence without Slovene, a list, a table and emoji left out), the explanation's text and key as the node voices
it (apart from a line's), the quick question's gap read as a pause and a choice's options not read, a step's script and
the intro's own words that make "**Biti**" Slovene; `IntroMarkTest` the run read marked in the line's text;
`IntroVoiceSettingTest` the setting (on by default, kept); `data/IntroVoiceRequestTest` the explanation as the app asks
for it (`POST /voice/say` with its text, the teacher's voice, `lang` and the app token, to a node of its own; a 204 no
clip) and looks it up (biti's steps keyed as Jan's node and the smoke keep them, found in her voice in the index, as the
day's audio finds it); `ui/school/IntroPointerTest` what Mojca points at as biti's
first step is read (its forms at their rows, a translation where its form was, the rest at its words, the examples, the
question), the forms alone at their rows, her hand at the right end of what is read (the words' at the explanation's,
the question level with its prompt; none off screen), its hop, no hand with the animations off or the keyboard up (the
rows still marked), and her turn toward what she explains (shifted on her walk-in); `audio/DayAudioIntroTest` and `PrefetchRunTest` an
explanation in the day's audio (asked for with its language, kept under its own key, never cut into phrases). The
bridge's side is `test/smoke/course.ts` and `test/smoke/voice.ts` (an explanation's clip; a step's burst against a plan
of three at once, nothing refused; the app's own requests over HTTP, the key and the file). On the emulator, `companion/bin/qa --steps school` asks the debug build for QA's hook `school`
(the dev village's copy a hamlet with its linden: the class begins with it), checks that there's nothing to set up,
goes back Home and opens the school from its tile, opens the unit biti, plays its intro step by step (a tap, a gap, a tap, a gap; the summary
card; the lesson begins with Mojca walking in, the screen recorded through it (`school-walk-in-<n>`, frames from
`school-walk-in.mp4`); its first step read aloud as it appears: a shot as each piece begins, what is read marked, Mojca
talking, her small hand beside what is read (`school-hand-row` at the row jaz · sem, `school-hand-example` at the first
example), its pieces
in order from the log (tag `IntroVoice`: the explanation's clip, else its English by the phone's voice and its Slovene in
Mojca's, then the examples and the question) kept in `intro-voice.txt`, read again by 🔊 and stopped by a tap on the
text), fills its tables with the cheat sheet (📋) opened over the first (biti's card in it), checks it, answers its gap text with one mistake
and looks a word of it up (its card), checks the corrections, then takes the test straight away ("⏩ Znam že") and
passes it (the third line typed without its č: almost, half an item): the unit done, and the dev bridge's village
(`GET /game`) keeps it. Screenshots: `school-levels`, `school-home`, `school-unit`, `school-intro-reading-<n>` (as piece n begins), `school-intro-replay`, `school-intro-1`, `school-intro-4`, `school-intro-summary`, `school-table`,
`school-help`, `school-table-checked`, `school-sheet-done`, `school-word`, `school-gaptext`, `school-gaptext-checked`,
`school-test-cheer-<n>` (Mojca cheering, frames of `school-test-cheer.mp4`), `school-test-passed`. `companion/bin/qa --steps school-build` takes the dev village's copy as it is (Jan's hamlet, its nine plots built): the
Build sheet has the hamlet's tenth plot ("Parcele · Plots: 9 / 10") and the Šola on it, nothing of Vas on its card, and
the line under its row ("🏫 Pouk že poteka pod lipo …"); every bed taken, its card's way on upgrades a dwelling for
Mojca's bed; what the stores then lack the moba brings; built on the plot chooser, the dev bridge's village keeps it,
and the school's screen has the class indoors ("Šola · School", Mojca teaching in it). Screenshots: `school-build-sheet`,
`school-build-card`, `school-build-bed`, `school-build-moba`, `school-build-plot`, `school-built`, `school-indoors`.
The lesson where the class meets (GAME.md "The school", SCENES.md "What someone has here"): `game/scene/SceneOffersTest`
checks the map's bubble over Mojca and her requests in the school agree (Jan's: her two up, the same, as many), the
order of what someone has (the class's lesson, a dialog, the requests), the marker and a tap straight in or the choice,
where the class meets (by the fire, under the linden, in the school; nowhere before the hamlet or without a course), the
blackboard, and the lesson (`School.lesson`, `School.lessonStep`: a unit begun goes on at its intro, its next worksheet,
its test; else the next one begins). `companion/bin/qa --steps school-scene` takes the dev village's copy as it is (Jan's
hamlet, its school standing) at 10 o'clock (QA's hook `hour:10`): Mojca's bubble on the map and her requests in the
school's scene, as many and the same; her marker (📚, her requests) and "📚 Pouk · Lesson" under "Danes tukaj"; a tap on
her in the picture (where, from the log: tag `SceneAt`) brings up the choice, the lesson first, which goes into the unit,
and back through the school ("← V šoli") to the scene; the blackboard tapped in the picture shows its word with "🏫 Vse
enote", which opens the school's screen, back to the scene. Screenshots: `school-scene-map`, `school-scene`,
`school-scene-choice`, `school-scene-lesson`, `school-scene-back`, `school-scene-blackboard`, `school-scene-units`.
In the school's scene (SCENES.md, "Arts" and "The school's blackboard") `SchoolSeatsRenderTest` checks the pupils sitting
on their chairs behind their desks (what shows of them the seated sprite but its shins and shoes, the desk under them,
the head over it, the hands on the lid; each child, a grown-up, every pose, every detail) and `SchoolBoardTest` the
blackboard's lesson (the unit worked on last, its forms, every letter of the course written and every lesson fitting,
the chalk at every detail); `companion/bin/qa --steps school-seats` opens the dev village's copy's school at 10 o'clock
and zooms in on the desks, again with the class's happening on, and on the blackboard. Screenshots: `school-seats`,
`school-seats-closer`, `school-seats-class`, `school-seats-class-closer`, `school-board`.

**The printed sheets** ([COURSE.md](COURSE.md#printed-sheets), "In the app"). `data/PrintsTest` checks what the app asks
for: a unit's sheet numbered among its level's written units as the generator numbers its files (a planned one left
out, none for it), a level's booklet only with a written unit, the bridge's path (`?base=` only for a base the sheets
explain in), and the job's name ("Lani – A1 – 01 biti", "Lani – A1 – vsi listi"). The bridge's side is
`test/smoke/prints.ts`: both routes as HTML (the unit's title, its table, its words said to the learner, the answers;
the CSS, the fonts with their latin-ext faces and fit.js inlined, nothing fetched), byte for byte the generator's page of
the same content, `?learner`, `?gender` and `?base` saying otherwise, the 404s (a planned unit, an unknown one, a level
without units) and 400s (a base or a gender it doesn't know), the token, and a unit changed printing as it is now.
`companion/bin/school-prints` still writes the PDFs and checks their pages. On the emulator, `companion/bin/qa --steps
school-print` opens the unit biti (QA's hook `school`), taps "🖨️ Natisni list", waits for Android's print dialog,
compares what fit.js chose in the app's WebView (the log, tag `LaniPrint`) with what headless Chromium chooses for the
same page from the dev bridge, saves the PDF through the dialog ("Save to PDF", the picker's Save: `school-print-biti.pdf`,
A4, its pages as fit.js chose), then does the same for A1's "🖨️ Vsi listi" up to its dialog; both choices side by side in
`school-print-fit.txt`. Screenshots: `school-print-button`, `school-print-preview`, `school-print-save`,
`school-print-pdf` (the PDF's first page), `school-print-all`, `school-print-booklet`.
