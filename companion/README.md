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
| `bridge/` | Claude Code channel server (Bun/TS). Forwards app messages into the session, exposes `reply`, `publish_module`, `publish_pack`, `publish_grammar`, `publish_scenario`, `publish_scene`, `publish_reading` and `publish_gloss` tools, relays permission prompts, serves the app API. |
| `lexicon/` | Word lookup's dictionaries (see [Word lookup](#word-lookup)): `sl.json`, built from Wiktionary, with its meanings in Italian and German, `sl.extra.json`, a hand-written supplement, and `sl.forms.json`, the forms book: hand-checked tables of the irregular verbs and the verb pairs (see [Word forms](#word-forms)). The larger ones (Italian, German, English) are downloads (`LANI_LEXICON_URL`). |
| `grammar/` | The grammar book's curated pages (`lani.grammar/v0`, one rule each, see [Grammar book](#grammar-book)): `grammar/<language>/`, 44 in `grammar/sl/`, 16 each in `grammar/it/`, `grammar/de/` and `grammar/en/`. |
| `drills/` | The car's audio drills (`lani.drill/v0`, see [The drills](#the-drills)): `drills/<language>/`, four in `drills/sl/` (transformations, rapid fire, sentence building, riddles). |
| `modules/`, `packs/`, `scenarios/`, `scenes/` | Curated grammar modules (`lani.module/v0`, with villager quests), word packs (`lani.pack/v0`: Slovene in `packs/` itself, another target language in `packs/<language>/`, e.g. `packs/it/`, `packs/de/`, `packs/en/`), role-play scenarios (`lani.scenario/v0`) and village scenes (`lani.scene/v0`, see [SCENES.md](SCENES.md)). |
| `cultures/` | Culture packs (`lani.culture/v0`, see [GAME.md](GAME.md#culture-packs)): the village's people, stories, feasts, goods and names, in its language. `primorska/` is the default village, with the cast in `primorska/villagers/` (`lani.villager/v0`, see [VILLAGERS.md](VILLAGERS.md)); `friuli/` is the second learner's Italian town in the Collio near Gorizia, with its cast, its festivals' word packs (`friuli/packs/`) and its voice cast (`friuli/voice-cast.json`, Italian voices); `kaernten/` is a German village in Carinthia (the Gailtal near Villach) for learners of German, the same parts in German with Slovene, English and Italian; `lakeland/` is the English village in a dale of the Lake District, for learners of English from Slovene, Italian or German (its starter packs in `packs/en/`), each with its voice cast. `villagers` is a link to `cultures/primorska/villagers`, the cast's old place, for a bridge started before the move; remove it once every bridge has restarted. |
| `voice-local/` | Local Slovene text-to-speech (Gepard) on the node's GPU, the fallback voice (see [Voice](#voice)). |
| `bin/lani-setup` | Sets up this machine step by step, or changes it: the learner, their data as a git repository of its own, a private remote, voices, the network, running, pairing ([docs/setup.md](../docs/setup.md)). `bin/lani <command>` runs `bin/lani-<command>` (`lani setup`, `lani pair`, …). |
| `bin/lani-session` | Starts or re-attaches the always-on session in tmux, with the channel and Remote Control. Loads `~/.config/lani/keys.env`. `--profile <id>` starts another learner's session, `--list` shows the learners (see [Learners](#learners)). `--service` starts the bridge service first and the session with the shim, `--classic` the bridge inside the session (see [The bridge as a service](#the-bridge-as-a-service)). |
| `bin/lani-bridge` | Runs each learner's bridge service in tmux: `start`, `restart` (deploys new bridge code without a tutor restart), `status`, `stop`, `logs` (see [The bridge as a service](#the-bridge-as-a-service)). `systemd/` has a user unit template for it, not installed. |
| `bin/lani-profile` | Adds a learner (their own data, bridge port, tmux session and tokens) and shows how to publish, start and pair them. |
| `bin/lani-pair` | Pairs a phone with a learner's tutor: prints a one-time QR code; lists and unpairs phones (see [Pair a phone](#pair-a-phone)). |
| `bin/lani-town` | Links learners' towns: invitations (a one-time QR code), accepting one, linking two towns of this node, unlinking (see [Towns](#towns)). |
| `bin/voice-build` | Counts and prebuilds the voice clips (see [Voice](#voice)). |
| `bin/lexicon-build` | Builds a word lookup dictionary from a kaikki.org extract of Wiktionary, gives its lemmas their meanings in the other languages (from the English, German and Italian Wiktionaries), and shows how much of the content it knows (see [Word lookup](#word-lookup)). |
| `bin/release-app` | Builds a release APK and publishes it to the app's self-update. |
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
| Bridge | `127.0.0.1:8790` | a free port from 8792 up (never 8790, 8791 and 8797 for QA, 8795/8796 for the voice and speech workers, 8799 for the smoke tests) |
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
invitation; `link`: the node linked two of its towns). The file holds no secrets.

### Link two towns

- **In the app:** the village's scroll (or book), its "👥 Prebivalci · Villagers" page → "🤝 Prijatelji · Friends".
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
from before the rename is read too). The code
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
grammar book, drills, the family's words, a town's villagers, scenes, requests and market) are said to the learner of
the profile: `{learner}` and `{m:…|f:…}` rendered for them (`Route.addressed`, `bridge/src/addressee.ts`; the tutor's
tools keep the placeholders, [SCENES.md](SCENES.md#the-learner-in-the-content)).

All endpoints except `/health`, `POST /pair`, the family page and the routes between towns (`/town/…`, signed by the asking town, see [Towns](#towns)) need `Authorization: Bearer <token>`: the app token, or a paired phone's device token. The towns' routes for the app (`/towns…`) are in [Towns](#towns). The family token reaches only the `/family` and `/audio` routes, and can read voice clips (see [Family](#family)). JSON bodies are limited to 256 KB (`413` beyond; `PUT /game` 512 KB, audio 2 MB); errors never carry stack traces. `PUT /game` keeps the village's `land` when a state leaves it out (an app from before generated land: GAME.md, "The land").

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/pair` | `{code, device}` (no token) → `{v, token, device: {id, name}, profile, learner: {name, target, base, child}, fingerprint, public_key, signature}`; `403` wrong or spent code, `410` expired, `429` too many wrong codes (see [Pair a phone](#pair-a-phone)) |
| `POST` | `/message` | `{kind: chat\|answer\|session_end\|roleplay\|roleplay_end\|villager_arrived, conversation_id, text, data?}` → into the session (`fam_…` conversation ids are the bridge's); a chat's `data.reply_to` quotes an earlier message of the chat (see [Tutor chat](#tutor-chat)) |
| `GET` | `/events` | SSE stream: `reply`, `module_published`, `pack_published`, `grammar_published` (`{id, title, emoji, extended, note}`), `scenario_published`, `scene_published`, `scene_removed`, `variant_published` and `variant_removed` (a happening's dialog the tutor wrote or took back, `{scene, happening, id, title, emoji, note}`: the app reloads the scene, news only with a note), `villager_published`, `villager_removed`, `permission_request`, `app_update`, `partner_challenge`, `voice_updated`, `words_added`, `lexicon_updated`, `reading_published` (`{id, title, level, note}`) and `reading_removed` (`{id}`), `town_guest` (a guest from a linked town brought help, a gift, a trade or help against trouble, or a move changed: `{key, kind, from, learner}`, kind `help`, `gift`, `trade`, `aid`, `move`), `town_question` and `town_answer` (a guest's question to the learner, the answer to the learner's: `{key, from, learner, text}`). Resume with `Last-Event-ID`. |
| `POST` | `/permission` | `{request_id, behavior: allow\|deny}` → answers a tool approval prompt |
| `POST` | `/reviews` | `{results: [{item_id, quality 0-5}], duration_minutes, language?, forms?}` → persisted via `update-db.py` (no LLM), Claude gets a `review_done` summary. `language`: another language's deck (a code, e.g. `it`: practised on visits), the home language's by default. `forms`: the cards that asked a form of their word, `[{item_id, key, page, right, answer?, expected?}]` (see [Word forms](#word-forms)): the summary names the wrong ones, nothing else is written; an older bridge drops it |
| `POST` | `/level` | `{from, to, stations: [{id, skill, right, total, minutes?}], duration_minutes?, language?, client_id}`: the treasure found (the level test at the end of `from` passed, [GAME.md](GAME.md#zemljevid-zaklada--the-treasure-map)) → the new level `to` (the one after `from`, else `400`) persisted via `update-db.py` (the report's `level` sets `current_level` and `level_since`; a session `/lani-app-treasure`, the stations' answers by skill), Claude gets a `level_up` summary with the new level's grammar pages; `{already: true, level}` and nothing written when the level is there already. An older bridge answers 404: the app sends it as a `session_end` with the report for the tutor to persist |
| `GET` | `/readings`, `/readings/:id` | the reading corner's readings (see [GAME.md](GAME.md#the-reading-corner)): the village's culture pack's (`companion/cultures/<culture>/readings/`, read per request) with `source: curated` and `culture`, then the tutor's (`<data>/app/readings/`, `publish_reading`) with `source: tutor`, `machine_written` and `published_at` |
| `POST` | `/readings/done` | `{kind: reading\|story\|aloud, id, title?, level?, exercises, correct, duration_minutes, questions?: [{type?, right}], looks?, aloud?: {words, right, misread, skipped, wpm?, recognizer: node\|phone, misses?: [{word, heard?}]}, language?, client_id}` → reading practice persisted via `update-db.py` (no LLM): `skill_scores.reading` (a reading's questions, a story's turns, a reading aloud's sentences; a story's "🎯 Preveri se" in the story notebook reports as a reading, `id` `book:<story>`, its questions the telling's turns), and `speaking` too for a reading aloud (`command_used` `/lani-app-reading`, `/lani-app-story`, `/lani-app-read-aloud`); Claude gets a `reading_done` summary, with `looks` (how often the text, hidden while its questions were asked, was shown again: [GAME.md](GAME.md#read-first-then-answer)) as "looked back at the text 3 times while answering" |
| `GET` | `/state[?language=it]` | all 6 learner databases + computed fields (no LLM involved), the home language's or, with `language`, that language's; always with `language` (whose they are) and `languages: [{code, name, level, words, due, source: home\|visits}]`, the home one first (see [docs/DB_SCRIPTS.md](../docs/DB_SCRIPTS.md#languages)) |
| `GET` | `/modules`, `/modules/:id[?version=N]` | published modules |
| `GET` | `/packs` | word packs (curated: the village's language's, `companion/packs/` for Slovene or `companion/packs/<language>/`, then the culture pack's `packs/`; + tutor `<data>/app/packs/`) with `source`, `total`, `learned`, `language` (and `festival` for a festival's pack, `praznik-*`, `festa-*`) |
| `GET` | `/packs/:id` | a pack with its words and the ids of words already learned |
| `GET` | `/grammar` | the grammar book of the village's language: the curated pages (`companion/grammar/<language>/`, `LANI_GRAMMAR_DIR` for another root) with what the tutor added to them (`extended`), then the tutor's own pages (`<data>/app/grammar/`), each with `source` (`curated`, `tutor`) and `published_at` for the tutor's (see [Grammar book](#grammar-book)) |
| `GET` | `/grammar/:id` | one page |
| `GET` | `/drills` | the car's audio drills of the village's language (`companion/drills/<language>/`, `LANI_DRILLS_DIR` for another root), each with `source: "curated"` (see [The drills](#the-drills)); an older bridge answers 404 and the app plays its own copy |
| `GET` | `/drills/:id` | one drill |
| `POST` | `/packs/:id/learn` | `{results: [{word_id, quality 0-5}], duration_minutes, language?}` → new vocabulary SR items via `update-db.py` (known words skipped), in the home language's data or `language`'s, Claude gets a `words_learned` summary. A pack session and a festival's run in the village both post here |
| `GET` | `/lookup?w=…&line=…&en=…&base=…&language=…` | what a word tapped in a dialog means, in the learner's base language (or `base`); `language`: another town's, on a visit: `{word, entries: [{lemma, pos, gloss, gloss_lang, gloss_via?, gloss_en?, fallback?, grammar, gender, source, item_id, known, example, emoji, aspect?, partner?, here?}], attribution}` (see [Word lookup](#word-lookup), and [Word forms](#word-forms) for a verb's partner) |
| `GET` | `/forms?w=…&w=…` | a word's forms (1-40 words): `{language, words: [{word, entries: [{lemma, pos, gloss, gender, aspect?, partner?, table: wiktionary\|checked, slots: [{key, forms, also?, grammar, page, needs?, lines?}]}]}], attribution}`; an older bridge answers 404 (see [Word forms](#word-forms)) |
| `POST` | `/words` | `{sl, en, pos?, item_id?, example_sl?, example_en?, from?, language?, client_id}` → `{item_id, added}`: a vocabulary SR item, without a session, the home language's or `language`'s (see [Word lookup](#word-lookup)) |
| `GET` | `/words` | `{from: [scene, roleplay, villager, chat, reading]}`: the origins `POST /words` takes (an older bridge: 404) |
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
| `GET` | `/voice/status` | clips per voice and engine, ElevenLabs quota, engines up or down, the voice cast, corpus coverage, the voices filtered (`denoise`), the level (`level`: target, clips at it and waiting) |
| `GET` | `/audio/index` | family recordings: normalized Slovene text → `[{file, speaker, recorded_at, text}]` |
| `POST` | `/audio?text=…&speaker=…` | a recording (raw body or multipart `file`; m4a, ogg or webm, max 2 MB), stored at the voice clips' loudness |
| `GET`, `DELETE` | `/audio/file/:name` | one recording |
| `GET`, `PUT` | `/family/api/settings` | the partner's name, emoji and Slovene "from" form (`Maje`) |
| `GET` | `/family/api/words` | words and phrases to record (pack words, examples, vocabulary cards) |
| `GET`, `POST` | `/family/api/challenges` | partner challenges `{type: question\|translate\|say, text, hint?}` with answers and feedback |
| `POST` | `/family/api/challenges/:id/answer` | `{text, spoken}`: Jan's answer (app token only) → `partner_answer` to Claude |

### Bridge code

`bridge/src/bridge.ts` only wires the parts. Each feature in `bridge/src/features/` (chat, modules, grammar,
rhythm, game, packs, lexicon, scenarios, scenes, stories, readings, villagers, culture, family, voice, stt, reviews, level, events, releases,
pairing, towns) exports
`(ctx: Ctx) => Feature`: its HTTP routes (method, path, and who may call it: `public`, `any` token
or `app` only, the default), its MCP tools and its paragraph of the channel guide. To add a
feature, write the module and add it to the `features` list in `bridge.ts`. `bridge/src/index.ts` is what `.mcp.json`
starts: the bridge (classic), or the shim (`shim.ts`) when `LANI_BRIDGE_SHIM=1`. `service.ts` starts the bridge as a
service (`bridge-service.ts`: the app's API, the control socket, the status, the stop), and `control.ts` is what the
service and the shim share: the socket's place, MCP over it, the hellos. The shim, `control.ts` and `index.ts` reach a
running tutor only when it restarts: keep them small. Claude Code keeps only the
first 2048 characters of a server's instructions, so the instructions are just the rules that must
never be lost (answer with the `reply` tool; a child's rules), and the whole guide comes from the
`channel_guide` tool, which the tutor calls before its first reply and again after compaction. The shared parts are
`http.ts` (route table, tokens), `channel.ts` (MCP server, tool registry), `events.ts` (SSE),
`outbox.ts` (client_id dedupe), `learner.ts` (`read-db.py`, `update-db.py`), `learners.ts` (profiles and
the learner's channel instructions), `cultures.ts` (the culture packs' schema and which one a bridge plays),
`pairing.ts` (the bridge's key, pairing codes, device tokens) and `towns.ts` (links, invitations,
signatures between bridges, a town's public state), both shared with the CLIs in `bridge/src/cli/`, `visits.ts`
(what a town gives a visiting one, cleaning it, the cache of other towns' answers, the talks on a visit),
`guests.ts` (the things to do on a visit: a host's requests and market, the guest book, the visitor's log, the limits),
`friendship.ts` (friendship between towns: the counts, the level both agree on, the rates, who moves) and
`questions.ts` (questions between towns: both sides' book, the limits, the count of answered questions; its routes
and the tutor's notes are `features/questions.ts`, which keeps the tutor's replies to its `tq_…` conversations
through `Events.tap`).
`bun test/smoke.ts` runs the smoke sections in `bridge/test/smoke/` against one bridge; the pairing, friuli, kaernten,
lakeland, towns and service sections start more bridges of their own (kaernten: `LANI_SMOKE_KAERNTEN_PORT`, by default
`LANI_SMOKE_PORT` + 5; lakeland: `LANI_SMOKE_LAKELAND_PORT`, by default `LANI_SMOKE_PORT` + 6; towns:
`LANI_SMOKE_TOWN_PORT` and the next port, by default `LANI_SMOKE_PORT` + 3 and + 4; service:
`LANI_SMOKE_SERVICE_PORT` and the next two, by default `LANI_SMOKE_PORT` + 8 to + 10). The service section runs
bridge services (`test/fixtures/service-probe.ts`, with probe tools) and their shims under a scripted Claude Code: the
channel both ways, a restart mid-way with the reconnect and `list_changed`, a call during an outage, the queue, a child's
service beside it, and `lani-bridge`'s loop and CLI (without tmux).

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

**Bookmarks.** A long press on a message, the tutor's or the learner's, opens its menu (TalkBack offers the same as
actions):

- "🔖 Zaznamek · Bookmark", or "Odstrani zaznamek · Remove bookmark": a bookmarked message shows a 🔖 beside it.
- "✏️ Opomba · Note": why it was kept ("Zakaj? · Why?", up to 200 characters). A note bookmarks the message too.
- "↩️ Odgovori · Reply" (see below).

"🔖" at the top of the chat (with their number) lists the bookmarks, newest first, each with its day, who wrote it and
its note. A tap goes to the message: the archive opens to its week and day, and the message is framed for two seconds.
↩️ replies to it, ✕ takes the bookmark away.

**Replies.** "↩️ Odgovori · Reply" quotes the message above the input, as messengers do (✕ lets it go). The next message
goes to the tutor with `data.reply_to`, next to what is attached now (`about_exercise`, …): `from` (`learner` or
`tutor`), `at` (ISO with the offset; none for a message from before the archive), `text` (up to 4,000 characters),
`about` (the label of the exercise or page it was about) and `attached` (the data that message was sent with, e.g.
`about_exercise` or `about_grammar`). The bridge gives the tutor a message's data as a JSON block, as it always has,
so an older bridge passes `reply_to` on unchanged; the chat feature's guide says what it is. In the chat, the reply shows
its quote, and a tap on the quote goes to the message it answers while the phone keeps it.

**Where it is kept.** Only on the phone, in the app's private files. `files/chat/<yyyy-MM>.json` holds a month (by
the messages' time in UTC): each message with its time, its bookmark and note, the message it quotes and the data it was
sent with. A change rewrites only its message's month. `files/chat.json` is the conversation kept before the archive
(its last 100 messages, without times): read as it is, and written again only for a bookmark on one of its messages. The
chat keeps a year: when it loads, older months are deleted, except their bookmarked messages. The node keeps no chat
history (the event stream replays only its last 200 events), so neither the history nor the bookmarks sync: they
survive app updates, but not clearing the app's data or a reinstall (`android:allowBackup="false"`).

## Grammar book

The village book (the scroll before Vas) has a chapter "📖 Slovnica · Grammar": one page per rule of the language
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

## Voice

The app speaks Slovene with natural voices. The bridge makes each clip once, keeps it in the voice cache (SQLite
`voice.db` + `files/<sha1>.mp3`: `LANI_VOICE_CACHE`, `~/.cache/lani/voice` after lani-setup; else `<data>/app/voice/`,
which is also used while only it has the clips) and the app caches the clips it plays
(LRU, 50 MB). Everywhere the app speaks, it plays, in this order: a family recording, a node clip,
a clip the node makes on request (`POST /voice/say`; a new line waits up to 8 s for it, since ElevenLabs
takes a few seconds, and a node that can't make it answers at once), then Android TTS, which is only
the fallback. 🐢 plays clips at 0.75×. The village's background sounds duck under every voice, and stop while the microphone
records ([GAME.md](GAME.md#background-sounds)).

**The voice cast.** Who speaks is in `voice-cast.json`: each speaker has an ElevenLabs voice, a
Gepard voice for when ElevenLabs can't, and a gender. The narrators `female` (default) and `male`
read every generic text (words, examples, exercises, role-play characters by their scenario's
`voice`). Each villager speaks with their own `speaker` (`cultures/<culture>/villagers/*.json`; `voice` stays their
gender, which the app needs for Slovene agreement), in their lines, their scene dialogs and their
role-play. A scenario may set a `speaker` too. The character voices come from the ElevenLabs voice
library, picked by audition (Slovene test sentences, word error rate with the local Whisper):

| Speaker | Voice | Who |
|---|---|---|
| `female` | Matilda (premade) | narrator |
| `male` | Daniel (premade) | narrator, male role-play characters |
| `grandma` | Zlata (Croatian) | Babica Micka, Teta Ančka |
| `grandpa` | Tomasz Z (Polish) | Stari Janez, Mlinar France, Čebelar Anton |
| `young-man` | Bartłomiej (Polish) | Pastir Luka, Vinar Marko, men who move in |
| `gruff` | Nikola (Serbian) | Kovač Tone |
| `woman` | Mila (Croatian) | Gostilničarka Vida |
| `teacher` | Milena (Serbian) | Učiteljica Mojca |
| `young-woman` | Ana (Croatian) | women who move in |
| `girl` | Torri Miles (Ukrainian) | Zala, girls born in the village |
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
2. Put its id in the speaker's `elevenlabs` line in `voice-cast.json`. The bridge reads the file
   again when it changes; no restart.
3. Make that speaker's clips again: `companion/bin/voice-build --run --revoice grandma` (dry run
   first without `--run`). The new clips get new file names, so the app downloads them.

**A voice that hisses: the denoise filter.** Stari Janez's own designed voice (`@janez`) has a steady hiss:
its pauses are at about −35 dBFS, where the other voices are at −45 to −80 dBFS. It also has a rumble below
30 Hz. Jan likes the voice, and a new one would cost credits, so the bridge filters his clips instead:

- **Which voices:** `denoise` in `voice-cast.json` maps a voice to a preset. The voice is a speaker of the
  cast (`grandpa`) or someone's own voice (`@janez`); `true` means `hiss`. Only their ElevenLabs clips are
  filtered: the hiss is in the ElevenLabs voice, and the local worker's clips are clean. A wrong entry is
  logged and left out; the rest of the cast stays. A culture pack's cast has its own `denoise`. Since
  2026-09-28 the list also has the girl (Torri Miles, in every village's cast), the young man, gruff, and
  France's and Micka's own voices. Their pauses measured −38 to −55 dB (Jan heard the girl as loud and
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
  the same encode (`--denoise --run` levels in the same encode too). A clip that the bridge made again meanwhile
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

**Engines**, tried in this order for a text without a clip:

| Engine | What | Setup |
|---|---|---|
| ElevenLabs | `eleven_v3` (the only model with Slovenian), `language_code: sl`, the speaker's voice from the cast. | `ELEVENLABS_API_KEY=…` in `~/.config/lani/keys.env` (mode 600). `bin/lani-session` exports it into the session, so the bridge inherits it. |
| Gepard (local) | Slovenian TTS model as a worker on `127.0.0.1:8795` (`GET /health`, `POST /synth`), the speaker's `gepard` voice (`ana`, `marko`, `nina`). | Runs separately. When it is down, it is skipped. |

If neither can make a clip, the app uses phone TTS; an engine that is down never fails a request.
A villager line whose narrator clip exists waits for ElevenLabs (no local clip in the character's
key): the app plays the narrator clip until then.

**Lone words: a carrier sentence.** The narrators are English library voices, and `eleven_v3` takes
`language_code: sl` only as a hint: a lone Slovene word (*kosilo*) comes out with English sounds, a
sentence doesn't (`previous_text`/`next_text` are not supported for `eleven_v3`). So a speaker marked
`"carrier": true` in the cast (the narrators; villagers' voices are Slovene enough) says a short text inside
a carrier sentence:

- **Short text:** one to three words without sentence punctuation (`kosilo`, `dober dan`; not `Kako si?`).
- **The request:** `Beseda je: kosilo.` to `POST /v1/text-to-speech/{voice}/with-timestamps` (`eleven_v3`,
  `sl`, MP3). The carrier sentences are per language (`CARRIERS` in `bridge/src/voice.ts`: `Beseda je: …`, `La parola
  è: …`, `Das Wort ist: …`, `The word is: …`), in the village's language.
- **The cut:** the word's span from the characters' timestamps, from 0.06 s before to 0.12 s after, with
  ffmpeg (`atrim`, fade in 0.02 s, fade out 0.05 s). The clip is the cut; its `method` is `carrier`.
- **Costs:** the guard counts the whole carrier sentence (`Beseda je: kosilo.` is 18 characters, about 9 on
  the subscription).
- **Fallback, logged:** without ffmpeg (`LANI_FFMPEG` names another, `off` turns it off) a short text is
  asked for plainly, as before. If the word isn't in the timestamps, it is asked for plainly too (both takes
  are paid for), and the clip is marked `plain`, so it isn't tried again.

**The words from before** (about 500 narrator words said plainly) are voiced again lazily. When the app
asks for one (it downloads the file, or `/voice/say` hands it over), it gets the old clip at once. The
bridge counts the request and soon re-voices the word in its carrier sentence:

- most asked for first;
- at most 30 a day: the day's cap is 40 (`LANI_VOICE_REVOICE_DAILY`; 0 turns off re-voicing and
  re-records), about 360 subscription characters, and the lazy re-voice leaves a quarter of it (at most 10)
  for Jan's re-records (below);
- never into the reserve for live lines.

The new clip gets a new file name, and the bridge sends `voice_updated`. The app then reloads the
index and downloads the new file the next time it plays the word. Words that wait for the next day are
taken up at the next request, or when the bridge starts.

The phone keeps the clips it has (50 MB) and doesn't ask for them again. So the lazy re-voice reaches
mostly words the phone doesn't have yet. For the rest: Jan's long press on 🔊 (below), or
`voice-build --revoice-words`: its plan shows how many words are old-style and what re-voicing them all
costs. `voice_status` shows the same (`carrier`).

**Re-record: a long press on 🔊.** When Jan hears a word or a line wrong (English sounds, a bad take), a
long press on its 🔊 has the node record it again at once (`POST /voice/redo {text, voice, client_id}`),
ahead of the lazy re-voice:

- **What is made:** the clip in the voice a tap plays (the first voice of the speaker's chain that the phone
  has a clip in). A short text in a carrier voice is said in its carrier sentence and cut, as above. Anything
  else (a sentence, a villager's voice, someone's own voice) is a plain re-take: `eleven_v3` never says a text
  the same way twice. A clip that the local worker made is made again with ElevenLabs. A text without a clip is
  voiced as `/voice/say` voices a live line, and is not counted.
- **Limits:** once a clip a day. Each re-record counts in the day's cap (40, shared with the lazy re-voice,
  which leaves the last 10 for re-records); a sentence counts as one, like a word. A re-record never goes into
  the reserve for live lines. A `voice-build --revoice-words` run counts in the same cap, so after a big run
  there are no re-records that day.
- **The answer:** `200 {status: "done", url, file, key, norm, voice, method, today, daily}`: the new take
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

`voice_status` shows today's re-records (`carrier.redos`) and the part of the cap kept for them
(`carrier.kept`).

**Costs.** The Starter plan has 30,935 characters a month, reset on the 24th. The quota guard reads
the subscription at most every 10 minutes. Prebuilds and background jobs stop when fewer than 5,000
characters would be left (`LANI_VOICE_RESERVE`); that reserve is for live lines (the tutor's
role-play lines, voiced as they come). The content is about 42,500 characters (the scene dialogs are
half of it). The subscription counted about half a character per character of `eleven_v3` text
(September 2026: 34,100 characters voiced for about 17,000), so one month voiced all of it; the guard
counts every character between subscription reads, so it stops early rather than late. New content
and a month that runs short go in the order below, and the next month goes on where one stopped.

**Prebuild**, in priority order: villager lines in their speakers' voices, review cards and pack
words, examples, modules, scenarios, the storyteller's stories told at A1 (`stories A1`), the car's drills at A1
(`drills A1`), scene dialogs and the keepers' talks (the culture's spots: the charcoal burner's meeting and his story,
every telling at every level, in his speaker's voice), then the stories at A2 (`stories A2`), the drills above A1
(`drills A2+`) and the stories at B1 and up (`stories B1+`). A story's teller speaks their lines and replies in their own designed voice once the voice
profiles have it ready (`@janez`), else in their speaker; the right choices are in the default voice. A riddle is in
its teller's voice the same way; a drill's other texts (a transformation's sentence and answer, a rapid answer and the
numbers generated, a build's steps) are in the female narrator's. A voice of one's own is ElevenLabs only: the local
worker never makes it. The car mode plays a story or a drill's item only when its clips exist (the app voices a scene's
line when it is played), so the A1 tellings and drills (Jan's level) come before the scenes.

```bash
companion/bin/voice-build                  # dry run: texts, characters, cached, missing, quota left
companion/bin/voice-build --run            # generate the missing clips
companion/bin/voice-build --run --upgrade  # also replace local clips while the quota lasts
companion/bin/voice-build --run --revoice grandma   # a swapped voice: its clips again
companion/bin/voice-build --run --max-chars 2000
companion/bin/voice-build --revoice-words --max 100        # dry run: old-style narrator words, the cost
companion/bin/voice-build --revoice-words --max 100 --run  # 100 of them again, most asked for first
companion/bin/voice-build --denoise        # dry run: the clips of a voice that hisses, not filtered yet
companion/bin/voice-build --denoise --run  # filter them (ffmpeg only, no characters)
companion/bin/voice-build --denoise --data <dir>   # another data directory (a copy)
companion/bin/voice-build --level          # dry run: every clip not at the level, each voice's loudness and gain
companion/bin/voice-build --level --run    # level them (ffmpeg only, no characters)
```

`--revoice-words` makes only the old-style narrator words again, in their carrier sentence, within the
reserve. The daily cap doesn't hold it back, but it counts for the day, so the bridge doesn't add
another 40 on top (and has no re-records left that day).

**Another learner's village** (a culture pack in another language): `LANI_CULTURE=friuli companion/bin/voice-build
--data <their data dir>` counts that pack's content in its language, with its voice cast; `--run` voices it. The
local worker speaks only Slovene, so the plan says it is "not for this village": what the ElevenLabs budget doesn't
cover waits for later, and a cast without voices plans nothing.

When the tutor publishes a pack, module, scenario or scene, the bridge voices its new texts in the
background (one at a time) and sends the app a `voice_updated` event. `voice_status` (MCP tool) and
`GET /voice/status` show coverage, quota and engines.

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
  3. Pack words, then the tutor's glosses, then the others.
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

## Im Auto · In the car

"🚗 Za pot · For the road" is a learning session for the car that uses sound only. Nothing is read, typed or looked
at. The app plays it as a media app: in **Android Auto**, and over Bluetooth with the car's and the steering wheel's
play, pause, next and previous. It plays only what is on the phone, so the drive works without the node.

**Get ready (before the drive, with the node reachable).** On Home, tap the "🚗 Za pot · For the road" tile, then
"🚗 Pripravi za pot · Get ready for the road". The app:

1. gathers (a few seconds, with the app open) the review cards due in the next 3 days (GET /state), the words of the packs under way and then of the next
   packs (at most 600, the easiest level first), the scenes' dialogs, the stories at Jan's level, the dialogs'
   short phrases to shadow (of the scenes at Jan's level or below, at most 600) and the drills (GET /drills, else the
   app's own copy; see [The drills](#the-drills));
2. keeps only the items whose Slovene clips are in the voice store's index (`GET /voice/index`), in the voice each
   person speaks in (their own voice, their archetype, their gender's narrator, the female narrator). A dialog that
   misses more than a quarter of its lines is left out; a single missing line is skipped. It never asks the node to
   voice anything (`/voice/say`, `/voice/redo`), so the car costs no ElevenLabs characters;
3. copies the clips from the phone's clip cache, or downloads them with `?count=0`: the bridge then doesn't count the
   download, so an old-style narrator word is not queued for its carrier re-voice (`features/voice.ts`; a bridge
   started before this change counts the download like the app's other downloads);
4. renders the prompts in Jan's base language (English) with the phone's own text-to-speech, to WAV files (the
   meanings, "You say: …", the stories' setups and recaps, and the drills' instructions, questions and meanings);
5. writes the library. Everything is in the app's `files/road/` (`road.json`, `clips/`, `prompts/`), not in the
   50 MB clip cache, so nothing is dropped. Getting ready again takes only what is new.

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

**The browse tree** in Android Auto is one tab, "🚗 Za pot · For the road", with eleven sessions:

| Session | What plays |
|---|---|
| 🚗 Za pot · For the road | Blocks of about 30 minutes, one after the other: two due cards, two new words, a dialog, again; each new word comes back three items later; an evening of a story about every 10 minutes; a short burst of a drill about every 7 minutes, the first after 4 (four transformations of one set, five rapid answers, three sentences built or three riddles: the kinds in turn, the one heard least recently first). The least recently heard first; when everything was heard, it goes round again. |
| 🌙 Mirno · Easy listening | For heavy traffic or when Jan is tired: only the stories and the dialogs, with no "say it" prompts or pauses. An evening of a story, then about 10 minutes of dialogs, again. A dialog plays as an audio play: the other person's lines and the learner's right answers in turn. A story keeps its English setup and recaps. |
| 🗣️ Odmev · Shadowing | Short Slovene phrases from the dialogs (the villagers' lines, the learner's right answers and the replies to them; 2 to 8 words, from the scenes at Jan's level or below): the phrase, a pause to say it along in the same rhythm, the phrase again. No English, nothing graded. |
| 🔁 Ponavljanje · Reviews (N) | Today's due cards as audio flashcards. |
| 🆕 Besede · New words | The new words, each heard again three items later. |
| 💬 Pogovori · Dialogs | The scenes' dialogs as short audio plays. |
| 📖 Zgodbe · Stories | The storyteller's stories, a chapter at a time, in order. |
| 🔄 Preobrat · Transformations | A sentence and what to do with it; say the new sentence. Six of a set in a row, then six of the next set; the set heard least recently first. |
| ⚡ Hitri odziv · Rapid fire | Numbers, the clock, days, months and dates at a fast pace. Six of a set in a row, then the next set; the ones heard alike in another order each day. |
| 🧱 Gradnja stavkov · Sentence building | Sentences built in three or four steps, the least recently heard first. |
| 🕵️ Uganke · Riddles | Stari Janez's riddles, the least recently heard first. |

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
| ⏮ Previous | Plays the item again from its start (pressed twice quickly, the item before). |
| Next or ⏩ held (the car sends fast-forward) | On a card or a word: 👍 "Znal sem · I knew it". Elsewhere: nothing. |
| Previous or ⏪ held (the car sends rewind) | On a card or a word: 👎 "Nisem · I didn't". Elsewhere: nothing. |
| 🌙 Mirno (Android Auto, the notification) | Switches the session playing to "🌙 Mirno" at once, for sudden heavy traffic. In Mirno the button is "🚗 Za pot" and goes back. |

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

**Without Android Auto** (Bluetooth only): the sheet has a simple player: the eleven sessions, ⏮ ⏯ ⏭ and, on a card or
a word, ✓ and ✗. Start a session before the drive; then the steering wheel's buttons and the media notification control
it.

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
ratings' writes), `RoadGather` (the items from the node's content), `RoadPrep` (getting ready as the sheet shows it,
and the gathering into a plan), `RoadWork` (the plan, and the order the files are got in), `RoadPrepWork` (getting the
files, writing the library as it grows), `RoadPrepService` (the foreground service that runs it, its notification),
`RoadPrompts` (the phone's voice), `RoadStore` (the files), `RoadService` (the Media3 `MediaLibraryService`, the browse
tree, the buttons), `RoadControls` (what next and a held button do, and `RoadPlayer`, the plain `ForwardingPlayer` the
session gets: a `ForwardingSimpleBasePlayer` rebuilds the timeline and crashed on an item's pieces not prepared yet),
`RoadSources` (an item as one media source: its prompts, clips and pauses), `RoadRemote` (the phone's player) and
`RoadCarCheck` (QA's car, a debug build only). The Android Auto declaration is `res/xml/automotive_app_desc.xml` and
the manifest's `com.google.android.gms.car.application`.

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

```bash
cd companion/android
JAVA_HOME=$(mise where java@temurin-21) ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first launch, scan the QR code from `companion/bin/lani-pair` ("📷 Skeniraj QR · Scan the QR code"),
or enter the bridge URL (`tailscale serve status`) and the token. For Slovene speech, install the
Slovenian voice in Android's text-to-speech settings (Google Speech Services → sl-SI).

The story notebook's handwriting is Kalam (Indian Type Foundry, SIL Open Font License 1.1), bundled in
`app/src/main/res/font/`; its licence and credits are in `app/src/main/assets/fonts/`. The ambient sounds' are in
`app/src/main/assets/ambient/CREDITS.md`.

## Test

```bash
cd companion/bridge && bun test/smoke.ts
cd companion/android && ./gradlew :app:testDebugUnitTest
python3 -m unittest discover companion/tests     # backups, the Whisper worker (models faked)
python3 tests/test_update_db.py                  # update-db.py
python3 -m unittest discover -s tests            # also lani.env (test_config), the data repository, lani-setup end to end
companion/bin/qa                                 # end to end, on the lani-qa emulator
companion/bin/qa --steps visit                   # only the visit of the second town (and setup)
companion/bin/qa --steps road                    # only the car's "🚗 Za pot" (and setup), a few minutes
companion/bin/qa --dry                           # the plan: bridges, ports, the fixture town, the steps
companion/bin/qa --bridges-only                  # no app: both dev bridges, linked, and what a visit asks
```

**The road's player in the unit tests.** `road/RoadPlayerTest` runs under Robolectric (a test dependency only, with
media3's test utilities: a fake clock and fake renderers). It builds `RoadPlayer` over a real ExoPlayer with road
items as `RoadSources` makes them (WAV prompts and clips, pauses of silence: one window of pieces, a period each) and a
media session. The session and the test read the player's whole state at every change, also while pieces are still
being prepared and have no duration. It checks the commands a car can send, and next in the pause to say it and after
it. With 0.1.543's `ForwardingSimpleBasePlayer` both tests fail with the crash from the car, "Periods other than last
need a duration".

**QA of the car** (`--steps road`, the last step). On a fresh install, it opens the 🚗 sheet and asks the debug build
for a small library (`road:mini`): two items of each kind from the dev bridge (cards, words, dialogs, stories,
phrases, drills), their clips downloaded and their prompts rendered in the background. It waits until the sheet says
"✅ Končano" and reports the notifications ("🚗 Pripravljam za pot … %" if it is quick enough to catch it, "Končano" at
the end). Then it:

1. starts "🚗 Za pot" from the sheet's player, and checks with `adb shell dumpsys media_session` that the app's session
   is PLAYING with a current item (what a car sees);
2. follows the item with the road service's dump (`adb shell dumpsys activity service si.lanisce.lani/.road.RoadService`,
   a debug build's: the session, the item, its piece and what it is, such as `pause:SAY`, and each session's items on
   the phone). When a pause to say the Slovene begins, it sends next (`cmd media_session dispatch next`, as a steering
   wheel does). The same item must go on past the pause (the answer at once). Next again must go to the next item. The
   app must still run (`pidof`) and play after each;
3. pauses (`dispatch pause`) and checks PAUSED;
4. plays the car's part (`road:car`, `road/RoadCarCheck`). The platform's media browser and controller, the protocol
   Android Auto speaks, connect to the service, browse the root and the tab to its eleven sessions, and play each one
   from its media id (the service's `onSetMediaItems`), until its first item plays. The app logs a line each (tag
   `RoadCar`). Every session that has items on the phone must play, none may fail ("Failed to load selection" in a car),
   and the app must still run at the end, paused.

The step takes about 70 seconds (with setup and without the build, about 2 minutes). With 0.1.543's player it fails
at step 1: the app closes as the session starts ("Periods other than last need a duration" in the crash check).

**QA with two towns.** `companion/bin/qa` runs the dev bridge (`127.0.0.1:8791`, on a copy of `data/` without its
town links, so QA never asks a learner's town) and a second dev town: `companion/bridge/test/dev-town.ts` on
`127.0.0.1:8797` (`LANI_DEV_TOWN_PORT`), the fixture of `test/fixtures/dev-town.json` ("Borgo di Prova": the Friuli
pack, Italian from Slovene, not a child's; a village at Vas with six people, today's visitor, two open requests and
goods to spare) in a temp data dir, linked with the dev bridge's town as two apps link (an invitation, accepted).
After the village steps, the visit steps go from the scroll's "🤝 Prijatelji · Friends" into the town ("🧳 In visita ·
Visiting": the labels in Italian), to a villager's card, the sea at the horizon and its scene, the requests and the
market (opened, not played: no tutor needed), the gift's message field with the keyboard up (it and "🎁 Invia · Send"
above the keyboard; nothing sent), and back with "⌂ Casa · Home". Both bridges stop with QA, and on their own
when QA is gone; a program other than a second town on 8797 is left alone (the visit steps skip). `--no-town` leaves
the second town out.

**QA's hooks in a debug build** (`app/QaHooks.kt`; a release build ignores them). QA asks them through the launch
intent's `si.lanisce.lani.qa` extra: `happening:<scene>/<happening>` counts that happening as on, so
`dialog-words` opens its dialog through its deep link (`scene:<scene>@<scene>/<happening>`) whatever the hour, the
day's dice or who has been met, then lets it go (`happening:`); `zoom` zooms the camera on screen in, as its "Približaj ·
Zoom in" action does, where two double taps came too far apart on a busy machine (`scene`, `zoom`); `road:mini` gets
a small library for the car ready, and `road:car` browses and plays the car's sessions as Android Auto does (`road`);
`turns:type` asks the dialogs opened from then on to type every turn that tests a form, as if each rule were secure
(`turns:` lets the learner's masteries decide again). With it, `typed-turn` opens dialogs until one's first turn is
typed, focuses its field and checks with the keyboard up that the field, the letter chips, "Preveri · Check" and the
sentence with the gap lie above the keyboard's top (its frame from `adb shell dumpsys window`, `type=ime`), that the
panel rose over the picture (its ✕ higher, the top bar gone), and that both come back once the keyboard is down.
An app without the hooks gets the steps as before (the fire's card, the scenes' links; the double taps).

Two more are for recordings (the site's clips), not used by QA's steps: `bubbles:<n>` shows only the n bubbles nearest
the fire in the village (`bubbles:` all again), and `clock:<f>` runs the towns' and scenes' time at f of the wall's
(0.05 to 1; `clock:` undoes it). A slow emulator then renders every frame of a recording that is sped up again
afterwards: with `clock:0.2`, set the animator duration scale to 5 too (`adb shell settings put global
animator_duration_scale 5`, back to 1 afterwards) for the camera's flights and the bubbles' bob, record (e.g. `adb emu
screenrecord start --fps 30`), and speed the clip up five times (`ffmpeg -vf setpts=PTS/5,fps=30`).
