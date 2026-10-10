# Set up Lani

This guide sets up Lani on your own machine in about 15 minutes: your tutor (a Claude Code session), the bridge the app
talks to, and the learner's data. A wizard does most of it. The manual steps are at the end, for when you want to know
what it does or do it differently.

You need:

- **A machine that stays on:** Linux or macOS. The tutor answers only while it runs.
- **Claude Code**, signed in with a Claude Pro or Max plan, or a Console API key. The tutor uses that account's usage.
- **An Android phone.**
- Optional: **Tailscale** (the phone reaches the tutor from anywhere, over HTTPS), an **ElevenLabs** key (natural voices),
  a GPU (local voice and speech recognition).

Coming from Fluent, the project's name until October 2026? The wizard moves your install: see
[migrate-from-fluent.md](migrate-from-fluent.md).

## 1. Get Lani and run the wizard

```bash
git clone https://github.com/fentas/lani.git && cd lani
companion/bin/lani-setup
```

The wizard installs the bridge's dependencies (`bun install`) the first time. It asks one thing at a time and starts
each step from what is set up already. Run it again any time to change something. Ctrl-C stops it; what it did so far
stays.

The eight steps:

| Step | What it asks | What it does |
|---|---|---|
| 1. The tools | Nothing | Checks for bun, claude, python3, git and tmux (needed), and tailscale, adb, docker and gh (optional). Says what each is for and where to get it. |
| 2. The learner | Name, grammatical gender, the language they learn and the language for explanations, level, minutes a day | The gender matters: Slovene agrees with it (*si lačen?* to a man, *si lačna?* to a woman). Any two of Slovene, English, Italian and German make a pair; Slovene has the most content. |
| 3. Their data | Where it lives (default `~/.local/share/lani/<id>`); a new learner, or an import of an earlier install | Writes the learner's databases, makes the directory a git repository and writes `~/.config/lani/lani.env`. An import copies the learner files and moves the caches out (see [Where things live](#where-things-live)). |
| 4. A remote | None, a git URL you have, or a new private GitHub repository (with `gh`) | Shows what the repository holds before it creates anything. Refuses a repository that isn't private. Pushes if you say so. |
| 5. Voices and speech | Your ElevenLabs key (optional) | Saves it in `~/.config/lani/keys.env` (mode 600). It is never shown. Finds the local voice and speech workers, or says how to get them. |
| 6. The network | Tailscale, or your own HTTPS reverse proxy on the LAN | Offers `tailscale serve --bg 8790`. Never Funnel: the bridge is never on the open internet. |
| 7. Running | tmux, or the bridge as a systemd user service (Linux) or a launchd agent (macOS) | Writes the service file and asks before it enables it. Explains linger and asks before it turns it on. Offers to start the tutor. |
| 8. The phone | Nothing | Shows the pairing QR code when the bridge runs, and where the app comes from. |

`companion/bin/lani setup` is the same (`lani <command>` runs `companion/bin/lani-<command>`; link it into your `PATH`
to type just `lani`).

### Without questions

`--yes` (or no terminal) asks nothing: the flags and what is set up already decide. Nothing that reaches outside the
machine happens without its flag: a GitHub repository (`--remote github`), a push (`--push`), `tailscale serve`
(`--serve`) or a service (`--run systemd`). Linger is never turned on this way.

```bash
companion/bin/lani-setup --yes --name Ana --gender female --target sl --base en --level A1 --goal 20 \
  --remote git@github.com:ana/lani-ana.git --push --network tailscale --serve --run tmux
companion/bin/lani-setup --help        # every flag
```

The ElevenLabs key comes from a file or stdin, never from the command line: `--elevenlabs-key-file ~/key.txt`, or
`--elevenlabs-key-file -`.

## 2. Start the tutor

```bash
companion/bin/lani-session
```

The tutor opens in tmux. On the first start, Claude Code asks two things:

1. **Confirm the development channel.** The app reaches the tutor through a Claude Code *channel*, which is a research
   preview. Lani's channel isn't on Anthropic's list yet, so it loads with `--dangerously-load-development-channels`.
   It is the bridge in this checkout, nothing from elsewhere.
2. **Approve the `lani` MCP server** (`.mcp.json`): the bridge's tools.

Then detach with Ctrl-b d. The tutor keeps running. `tmux attach -t lani` brings it back.

## 3. Get the app

The simplest way: the latest release from GitHub, sent to the phone over Tailscale (no cable):

```bash
companion/bin/release-app --github     # the latest release (its SHA-256 checked), where your bridge offers it to the app
companion/bin/lani-send-app            # sends it to your phone over Tailscale (Taildrop)
```

On the phone, open `lani-<version>.apk` from **Files → Downloads** (or from Tailscale's notification) and allow
installing from that app once. `lani-send-app` sends to the only device Taildrop can reach, or asks which one
(`companion/bin/lani-send-app <device>`, as `tailscale file cp --targets` lists them).

Other ways to the phone: `adb install -r <apk>` over USB, or download the APK from
[GitHub Releases](https://github.com/fentas/lani/releases) on the phone itself.

Or build it yourself:

- **In Docker**, without a JDK or an Android SDK: `companion/bin/lani-build-app --release --out ~/apk/`, then
  `companion/bin/lani-send-app --file ~/apk/lani-<version>.apk`.
- **With the Android SDK** (JDK 21; the SDK in `ANDROID_HOME`, or `sdk.dir` in `companion/android/local.properties`):
  `companion/bin/release-app "notes"` builds the APK and puts it where your bridge offers it; then `lani-send-app`.

A phone takes an update only with the key its app was installed with: an app from the GitHub releases updates from
them (`release-app --github`), one you built with your own key (`companion/bin/lani-keystore`) from your own builds.
Once the app runs, it updates itself from your bridge whenever you publish a new APK with `release-app`.

## 4. Pair the phone

```bash
companion/bin/lani-pair
```

It prints a QR code that works once, for 10 minutes. In the app: "📷 Skeniraj QR · Scan the QR code". With Tailscale,
the phone needs the Tailscale app, signed in to the same tailnet. More: [companion/README.md, "Pair a
phone"](../companion/README.md#pair-a-phone).

That's it. Something off? [troubleshooting.md](troubleshooting.md).

---

## Where things live

| What | Where | In git |
|---|---|---|
| Lani itself | the checkout | the public repository |
| The learner's data: the six databases, the village, what the tutor made, family recordings, session results | `~/.local/share/lani/<id>` (`LANI_DATA_DIR`) | **its own repository** (below) |
| The settings | `~/.config/lani/lani.env` | no |
| Secrets: the ElevenLabs key | `~/.config/lani/keys.env`, mode 600 | no |
| Secrets: the app token, the family token, the bridge's key | `<data>/app/bridge-token`, `family-token`, `bridge-key.pem` | no (ignored) |
| Voice clips (a cache) | `~/.cache/lani/voice` (`LANI_VOICE_CACHE`) | no |
| The app's APKs | `~/.local/share/lani/releases` (`LANI_RELEASE_DIR`) | no |
| Backups (`lani-backup`) | `~/.local/share/lani/backups` | no |
| Logs, sockets | `~/.local/state/lani` | no |

The old places keep working. A setup the wizard hasn't moved yet keeps its data in the checkout's `data/`, its clips
in `<data>/app/voice` and its APKs in `<data>/app/release`. While only the old place has the clips (or a release), the
bridge, `voice-build` and the app's update use it.

The voice store's database (`voice.db`) also remembers each villager's own designed voice. It is a cache, but making
it again costs ElevenLabs credits, so `lani-backup` keeps a copy (in `voice/` of each snapshot).

## Your data repository

The learner's data is a git repository of its own. It is a history of the learning and a copy you can move to another
machine.

- **After each session it commits.** `update-db.py` commits when it has written a session, for a report from the tutor
  or a review in the app: `session 2026-10-05: 12 reviews, 2 new words`. When the tutor session ends, it commits what
  else changed (the village, what the tutor published). A commit that fails never fails the session.
- **With a remote, it pushes.** Each commit is pushed to `origin` in the background, never forced. A push that fails (no
  network, the remote moved on) is tried again with the next commit. `~/.local/state/lani/data-push.log` says what
  happened.
- **What stays out** (`.gitignore`, written once; edit it as you like): the tokens and the bridge's key, pairing and
  invitation codes, the voice clips and APKs, files the bridge rewrites all the time (`client-ids.json`,
  `devices-seen.json`, `events.json`, `channel-queue.json`), backups and temporary files. `devices.json` is in: it holds
  only hashes of the phones' tokens.
- **Only a repository's root counts.** The checkout's `data/` is never committed into Lani's repository.

| Setting (lani.env) | Default | |
|---|---|---|
| `LANI_DATA_AUTOCOMMIT` | `on` | `off`: no commits after sessions. |
| `LANI_DATA_REMOTE` | empty | The remote (`origin`). Empty: nothing is pushed. |
| `LANI_DATA_PUSH` | `on` | `off`: commit, but push yourself. |

The remote must be private: it holds the learner's mistakes, progress, chat-related notes and family recordings. See
[privacy.md](privacy.md). On another machine: `git clone` the repository to `~/.local/share/lani/<id>`, run
`lani-setup` and keep that directory. The phones pair again (the bridge's key isn't in git).

## lani.env

`~/.config/lani/lani.env` holds the settings for this machine. The wizard writes it with each setting documented;
you can edit it. `KEY=value` lines, `#` comments; `~` and `$HOME` are expanded, nothing else. The environment wins over
the file, and a `FLUENT_*` variable counts as its `LANI_*` name. `LANI_ENV_FILE` names another file
(`/dev/null`: none), and `LANI_CONFIG_DIR` another directory.

**The learner's settings** are for the default learner (the one the wizard sets up). They are not passed on to other
programs' environments, so a process of another learner (`lani-profile`) never gets them:

| Setting | Default | |
|---|---|---|
| `LANI_DATA_DIR` | the checkout's `data/` | The learner's data repository. |
| `LANI_VOICE_CACHE` | `<data>/app/voice` | The voice clips (`voice.db`, `files/`). The wizard sets `~/.cache/lani/voice`. |
| `LANI_CULTURE` | `primorska` | The village's culture pack: `primorska` (Slovene), `friuli` (Italian), `kaernten` (German), `lakeland` (English). |
| `LANI_BRIDGE_PORT` | `8790` | The bridge's port on 127.0.0.1. |
| `LANI_PUBLIC_URL` | Tailscale's name | The HTTPS address the app uses, when it isn't Tailscale Serve's. The QR code carries it. |
| `LANI_DATA_REMOTE`, `LANI_DATA_PUSH`, `LANI_DATA_AUTOCOMMIT` | | See [Your data repository](#your-data-repository). |

**This machine's settings** apply to every learner on it:

| Setting | Default | |
|---|---|---|
| `LANI_NETWORK` | | `tailscale` or `lan`: what the wizard set up. |
| `LANI_BRIDGE_MODE` | `classic` | `classic`: the bridge runs inside the tutor session. `service`: on its own. (The older `~/.config/lani/bridge-mode` file still counts.) |
| `LANI_RELEASE_DIR` | `<data>/app/release` | The app's APKs. The wizard sets `~/.local/share/lani/releases`. |
| `LANI_GEPARD_URL` | `http://127.0.0.1:8795` | The local voice worker. |
| `LANI_STT_URL` | `http://127.0.0.1:8796` | The local speech recognition worker. |
| `LANI_BACKUP_DIR` | `~/.local/share/lani/backups` | `lani-backup`'s snapshots. |
| `LANI_BRIDGE_HOST` | `127.0.0.1` | Keep it: Tailscale Serve or your proxy is in front. |

Other `LANI_*` settings (from [companion/README.md](../companion/README.md)) can go in the file too; the wizard keeps
them. Secrets never go in `lani.env`: the ElevenLabs key belongs in `keys.env`.

## Running it

| Way | What runs where | After a reboot |
|---|---|---|
| tmux (`classic`) | The tutor in tmux `lani`, the bridge inside it. The simplest. | `companion/bin/lani-session` |
| tmux (`service`) | The bridge in tmux `lani-bridge`, the tutor in tmux `lani`. A new bridge version needs only `lani-bridge restart`, not a tutor restart. | `companion/bin/lani-session` (it starts both) |
| systemd (Linux) | The bridge as the user unit `lani-bridge@default`; the tutor in tmux. | The bridge starts at login (or at boot, with linger); then `lani-session`. |
| launchd (macOS) | The bridge as the agent `page.lani.bridge`; the tutor in tmux. | The bridge starts at login; then `lani-session`. |

**Linger (systemd).** systemd stops your user services when your last login session ends. With linger
(`loginctl enable-linger $USER`), they start at boot and keep running without a login. The wizard explains it and asks;
it never turns it on by itself. The tutor itself is a terminal program in tmux, so after a reboot you start it with
`lani-session`.

More on the service mode: [companion/README.md, "The bridge as a service"](../companion/README.md#the-bridge-as-a-service).

## The network

- **Tailscale (recommended).** `tailscale serve --bg 8790` publishes the bridge on your tailnet only, over HTTPS, at
  `https://<machine>.<tailnet>.ts.net`. The phone needs the Tailscale app, signed in to your tailnet (or the machine
  shared with the phone's account). Tailscale Serve may need `sudo tailscale set --operator=$USER` once.
- **Your own HTTPS proxy on the LAN.** The app refuses plain HTTP. Put a reverse proxy with a certificate (Caddy,
  nginx) in front of `127.0.0.1:8790`, on a name your phone resolves, and give its address to the wizard
  (`LANI_PUBLIC_URL`). The phone must be on the same network.
- Never Tailscale Funnel or a port open to the internet: the bridge holds the learner's data.

## Voices and speech

| Tier | Needs | Gets |
|---|---|---|
| Minimal | nothing more | The phone's text-to-speech and speech recognizer. |
| Standard | an ElevenLabs key ([your own ElevenLabs](#your-own-elevenlabs)) | Natural voices, a voice for each villager. |
| Full | a GPU with about 6 GB (CUDA or ROCm) | Local Whisper for exact speaking checks, Gepard as the offline Slovene voice. |

The local workers install as user services: `companion/voice-local/install.sh` and `companion/stt-local/install.sh`
(each about 15 GB). See [companion/README.md, "Voice"](../companion/README.md#voice) and
[stt-local/README.md](../companion/stt-local/README.md). The bridge shortens the long silences in Gepard's clips and
speeds them up a little, to the ElevenLabs voices' pace (with ffmpeg; `LANI_VOICE_GEPARD_TEMPO`, 1.05, `off` for none).

### Your own ElevenLabs

Lani speaks with ElevenLabs on your own account: you pay ElevenLabs for what is voiced, and nothing goes through
anyone else.

1. **An account and an API key.** Sign up at [elevenlabs.io](https://elevenlabs.io) and create an API key. If you
   restrict it, it needs: text to speech, reading voices (`/v1/voices`), reading the user's subscription (the quota
   guard), and voice generation only if villagers get voices of their own (below).
2. **The key into Lani:** the wizard asks for it (step 5), or
   `companion/bin/lani-setup --elevenlabs-key-file -` reads it from standard input. It goes to
   `~/.config/lani/keys.env` (mode 600) as `ELEVENLABS_API_KEY`, never onto a command line; `lani-session` hands it
   to the tutor's bridge.
3. **What it costs.** Every line of a new learner's village, voiced once (measured in October 2026):

   | Village | Lines | Characters | ElevenLabs credits (≈) |
   |---|---|---|---|
   | Primorska (Slovene) | 8,129 | 252,000 | 126,000 |
   | Friuli (Italian) | 1,742 | 58,000 | 29,000 |
   | Kärnten (German) | 1,765 | 66,000 | 33,000 |
   | Lakeland (English) | 1,824 | 69,000 | 34,000 |

   The model is `eleven_v3` (the only one with Slovene), which counted about half a credit per character. Plans and
   their monthly credits are on [ElevenLabs' pricing page](https://elevenlabs.io/pricing).
4. **All at once, or as you go.** You needn't voice everything up front. The bridge voices what you meet, in order of
   need (villagers' lines, your review words, examples, modules, stories at your level first), and keeps the last
   5,000 characters of your quota for the tutor's live role-play lines (`LANI_VOICE_RESERVE`). A line without a clip yet is spoken
   by the local Gepard voice (Slovene, if installed) or by the phone. So a small plan fills in over a few months; a
   larger month voices it all at once:

   ```bash
   companion/bin/voice-build                      # dry run: lines, characters, what's missing, the quota left
   companion/bin/voice-build --run --max-chars 20000   # voice up to 20,000 characters now
   LANI_CULTURE=friuli companion/bin/voice-build --run # another village's lines
   ```

5. **The voices.** The cast (`companion/voice-cast.json`) uses voices from ElevenLabs' voice library by their ids;
   the first request with one adds it to your account, and it takes no voice slot. A villager's voice of their own
   (designed by the tutor, `LANI_VOICE_DESIGN`, on by default) takes a voice slot each and costs a few credits to
   design; `LANI_VOICE_DESIGN=off` keeps to the cast. To swap a voice: companion/README.md, "Voice".
6. **Privacy.** Every line voiced goes to ElevenLabs as text ([privacy.md](privacy.md)). Without a key, nothing does.

## Manual setup

What the wizard does, by hand:

```bash
cd companion/bridge && bun install && cd -
mkdir -p ~/.config/lani ~/.local/share/lani && chmod 700 ~/.config/lani
# the learner: copy the templates and fill them in (or let the tutor's /lani-setup do it)
mkdir -p ~/.local/share/lani/ana && for f in data-examples/*-template.json; do
  cp "$f" ~/.local/share/lani/ana/"$(basename "$f" | sed 's/-template//')"; done
python3 .claude/hooks/lani_data_repo.py init ~/.local/share/lani/ana     # .gitignore, git init, the first commit
cat > ~/.config/lani/lani.env <<'EOF'
LANI_DATA_DIR=~/.local/share/lani/ana
LANI_VOICE_CACHE=~/.cache/lani/voice
LANI_RELEASE_DIR=~/.local/share/lani/releases
EOF
printf 'ELEVENLABS_API_KEY=%s\n' "<your key>" > ~/.config/lani/keys.env && chmod 600 ~/.config/lani/keys.env   # optional
tailscale serve --bg 8790
companion/bin/lani-session
companion/bin/lani-pair
```

A remote: `git -C ~/.local/share/lani/ana remote add origin <url>`, then `LANI_DATA_REMOTE=<url>` in `lani.env`.

## More learners

The wizard sets up one learner, the machine's default one. Another learner on the same machine has their own data,
port and tutor: `companion/bin/lani-profile add` ([companion/README.md, "Learners"](../companion/README.md#learners)).
