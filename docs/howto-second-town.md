# How to: a second learner's town (an Italian village)

Everything for multiplayer is built (plan 2, M1–M8). What's left is setting up the second learner on this node.
Jan decided to do it later; this is the checklist. Details are in [companion/README.md](../companion/README.md)
("Learners", "Pair a phone", "Towns", "Word lookup").

## 1. Make their profile

```bash
companion/bin/lani-profile add luka --name Luka --target it --base sl --gender male --child --culture friuli
```

- Pick the id and name you want (`luka` is only an example). `--child` gives the child's rules (invite only,
  no approvals on their phone, age-appropriate tutor). `--culture friuli` is their village in the Collio near
  Gorizia. `--level A1` unless you say otherwise.
- `--gender male|female`: what the village says to them agrees with it, and their own lines are said in a voice of
  it. Without it the CLI warns (the village then speaks in the masculine, their own lines in the female voice).
- It prints their bridge port (8792 up), their tailnet HTTPS port (8443 up) and the next steps, and warns when the
  village's language has no dictionary on this node (step 2).

## 2. Switch on the Italian dictionary and speech

- **Dictionary:** the Italian dictionary is a download, not in the repository (38 MB; [Word
  lookup](../companion/README.md#word-lookup)). Build it once (a few minutes; the commands are in the README), and put
  `it.json.gz` and `it.manifest.json` in `~/.local/share/lani/lexicon/`. Then add the variable to their profile's
  `env` in `profiles/profiles.json`:

  ```json
  "env": { "LANI_LEXICON_URL": "~/.local/share/lani/lexicon" }
  ```

  (A `~` there is the node's home: `lani-session` and `lani-bridge` expand it. Before 2026-10-26 they passed it on as
  it was, and the bridge found no dictionary.) For Jan's own visits to Italian towns, and for every learner at once,
  add the same line to `~/.config/lani/keys.env` (`LANI_LEXICON_URL=~/.local/share/lani/lexicon`). Restart the
  learner's bridge. Its log then says `lexicon it: <version> (download), … lemmas`, and `GET /lexicon/status` says
  `"download": "ready"`; without one, the log says `lexicon it: no dictionary (…)` and what to do.
- **Speech (node):** put the new speech worker in place once (it restarts the Whisper service):
  `companion/stt-local/install.sh`. The phone's own recognizer already listens in it-IT.

## 3. Publish their bridge to the tailnet (once)

```bash
tailscale serve --bg --https=8443 http://127.0.0.1:<their port>
```

(`lani-profile show luka` prints the exact command and the URL.)

## 4. Start their tutor

```bash
companion/bin/lani-session --profile luka
```

On the first start confirm the development channel and approve the `lani` MCP server, as for Jan's.

## 5. Pair their phone

```bash
companion/bin/lani-pair --profile luka
```

Their phone needs Tailscale (signed in to the tailnet, or the node shared with it) and the Lani app; scan the QR
code in the app.

## 6. Link the two towns

As the parent, on the node (a child's app can't invite or accept):

```bash
companion/bin/lani-town link default luka
companion/bin/lani-town name "Vas pod Sabotinom"                   # optional: the Primorska village's name
companion/bin/lani-town name "Borgo sul Collio" --profile luka  # optional: their
```

Then each app shows the other town under the village scroll's "👥 Prebivalci" → "🤝 Prijatelji · Friends",
with "Obišči · Visit". When the second learner is a child, `link` makes your town their guardian: you see their
progress in detail ("📈 Napredek · Progress" on their card; companion/README.md, "Progress between towns"), and their
app says so. For a link made before, or by an invitation: `companion/bin/lani-town guardian default --profile luka`.

## 7. Check

- `companion/bin/lani-session --list` shows both learners, their villages and whether their bridges run.
- `companion/bin/lani-town list` and `lani-town list --profile luka` show the link on both sides, and what each town
  shares of its learning with the other (`SHARES`, `THEY SHARE`).
- On a visit from Jan's phone the labels switch to Italian ("🧳 In visita").
