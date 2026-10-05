# Troubleshooting

First: run `companion/bin/lani-setup` again. Step 1 checks the tools, and every step shows what is set up. Running it
changes nothing you don't change.

Where to look:

| What | Command |
|---|---|
| The tutor session | `tmux ls`, then `tmux attach -t lani` (detach: Ctrl-b d) |
| The bridge service | `companion/bin/lani-bridge status`, `companion/bin/lani-bridge logs -f` |
| The bridge under systemd | `systemctl --user status lani-bridge@default`, `journalctl --user -u lani-bridge@default -f` |
| The learner's data | `python3 .claude/hooks/read-db.py \| head`, `git -C ~/.local/share/lani/<id> log --oneline` |
| The settings | `~/.config/lani/lani.env` |
| Pushes of the data | `~/.local/state/lani/data-push.log` |

## Setup

**`lani-setup needs Bun`.** Install it from [bun.sh](https://bun.sh) and open a new terminal.

**"a program still has files open in …/data".** The old tutor or its bridge still runs on the data you import. Stop it
first: `tmux attach -t fluent` (or `lani`), then `/exit`; in the service mode also `companion/bin/fluent-bridge stop`
(`lani-bridge stop`). Then run the import again.

**"… holds a learner already".** The directory you import into has a learner. Import into an empty directory (`--data`),
or move that one away first. The wizard never merges two learners.

**"the data is a learner of Slovene".** The language a learner learns doesn't change: their databases are that
language's. Another language is another learner (`companion/bin/lani-profile add`), or a fresh start in another
directory.

**"… is not a private repository".** The wizard refuses to push the learner's data to a public repository. Make it
private on GitHub, or choose another remote.

## The app

**The app can't reach the tutor.**

1. Is the tutor running? `tmux ls` shows `lani`. If not: `companion/bin/lani-session`.
2. Does the bridge answer? `curl -s http://127.0.0.1:8790/health` says `{"ok":true}`. In the service mode:
   `companion/bin/lani-bridge status`.
3. Is it published? `tailscale serve status` names `http://127.0.0.1:8790`. If not: `tailscale serve --bg 8790`.
4. Is the phone on the tailnet? The Tailscale app on the phone is on and signed in to the same tailnet (or the
   machine is shared with the phone's account).
5. Is the address HTTPS? The app refuses plain HTTP. With your own proxy, `LANI_PUBLIC_URL` must be `https://…`.

**"Address already in use" or the app reaches a bridge without the tutor.** Only the tutor session's bridge serves the
app. Another Claude Code session opened in the checkout starts a bridge too, which keeps its tools but not the port. If
8790 is taken by something else, set `LANI_BRIDGE_PORT` in `lani.env` and `tailscale serve --bg <port>`.

**Pairing fails.** The code works once, for 10 minutes: make a new one (`companion/bin/lani-pair`). After 10 wrong or
expired codes in 10 minutes, the bridge waits before it takes another. A phone pairs with one learner: for another,
clear the app's data first. After a move to another machine the bridge has a new key: pair the phones again.

**The app doesn't update itself.** `companion/bin/release-app` writes the APK to `LANI_RELEASE_DIR`
(`~/.local/share/lani/releases`). A bridge started before that directory existed doesn't watch it yet: restart it
(`lani-bridge restart`, or the tutor in the classic mode).

## The tutor

**Claude Code asks about a development channel.** Confirm it: it is Lani's bridge, from this checkout (see
[setup.md, "Start the tutor"](setup.md#2-start-the-tutor)).

**The tutor works on the wrong data.** `python3 .claude/hooks/read-db.py | head` reads what the tutor reads. The data
is `LANI_DATA_DIR` from the environment, else from `lani.env`, else the checkout's `data/`. A `LANI_DATA_DIR` exported in
your shell wins over `lani.env`. Another learner's session (`lani-session --profile <id>`) never uses `lani.env`'s
learner settings. After a change, restart the tutor.

**The tutor writes session results into the checkout.** It should write them into `<data>/results/`. The bridge tells
it where (and the SessionStart hook prints it). Restart the tutor after setting up the data repository.

**The tutor runs out of usage.** The always-on session uses your Claude account's limits. A Console API key has no
limits but costs per token.

## The data repository

**No commits after sessions.** Check that the data directory is a repository's root: `ls <data>/.git`. Then
`LANI_DATA_AUTOCOMMIT` in `lani.env` (not `off`). update-db.py prints `[Lani] 📚 data repository: …` (on stderr) after
each commit, and why when one fails.

**Pushes fail.** See `~/.local/state/lani/data-push.log`.

- Pushes run in the background and never ask for a password. With SSH, load your key into an agent (`ssh-add`) or use
  a key without a passphrase for this remote; with HTTPS, a credential helper (`gh auth setup-git`).
- "rejected" or "fetch first": the remote has commits this machine doesn't have (another machine pushed). Lani never
  forces a push. Merge them yourself: `git -C <data> pull --rebase origin main`, then `git push`.
- "origin is …, lani.env says …": someone changed the remote. Run `lani-setup` to choose one.

**A secret in the repository.** The `.gitignore` keeps the tokens and the bridge's key out. If one got in anyway (a
`.gitignore` changed by hand), remove it from the history before you push, and make a new one: delete
`<data>/app/bridge-token` (or `family-token`) and restart the tutor; pair the phones again.

## Voices and speech

**No natural voices.** Is `ELEVENLABS_API_KEY` in `~/.config/lani/keys.env`? The tutor's `voice_status` shows the
engines and the quota left. Without a key or quota the phone speaks.

**The clips are gone after the move.** The bridge keeps them in `LANI_VOICE_CACHE` (`~/.cache/lani/voice`), or in
`<data>/app/voice` while only that one has them. If a cache cleaner removed `~/.cache/lani/voice`, restore it from a
`lani-backup` snapshot (`voice/`): making the clips again costs ElevenLabs credits.

**The local workers aren't found.** `curl -s http://127.0.0.1:8795/health` (voice), `:8796/health` (speech). Start them
with `systemctl --user start lani-voice lani-stt`; install them with `companion/voice-local/install.sh` and
`companion/stt-local/install.sh`.

## Running it

**The bridge stops when I log out (systemd).** User services stop with the last login session unless linger is on:
`loginctl enable-linger $USER`.

**`tailscale serve` says "access denied".** Allow your user once: `sudo tailscale set --operator=$USER`.

**After a reboot nothing runs.** Start the tutor: `companion/bin/lani-session` (it starts the bridge service in the
service mode). Under systemd or launchd the bridge starts by itself; the tutor still needs `lani-session`.
