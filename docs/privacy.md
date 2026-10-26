# Privacy: what leaves your machine

Lani runs on your machine. The learner's data (their profile, mistakes, progress, reviews, the village, what the tutor
made for them, the family's recordings, the session results) stays in `~/.local/share/lani/<id>` on that machine. This
page lists everything that leaves it, where it goes, and how to turn it off. There is no telemetry and no analytics:
Lani itself sends nothing to us.

## Always: Anthropic, through Claude Code

The tutor is a Claude Code session on your machine, signed in with your Claude account (a Pro or Max plan) or your
Console API key. Claude Code sends the session's conversation to Anthropic to get the tutor's answers. That includes:

- what comes from the app: chat messages, answers to exercises, finished sessions and reviews (a summary, with the
  answers typed wrong), role-play turns, words the learner adds, a question the learner asks about an exercise, and the
  recent answers the tutor looks up (`recent_answers`) when the learner reports a card;
- photos sent in the chat (📷): the tutor opens the node's copy with its Read tool, so the picture goes to Anthropic as
  chat text does (see "Photos in the chat" below);
- what the tutor reads to answer: the learner's databases (through `read-db.py`), the session results, the curated
  content, the files of this checkout it opens;
- texts from other people, when you use those features: the partner's challenges on the family page, and questions,
  answers and gifts' messages from linked towns. The tutor gets them marked as someone else's content.

Remote Control is on for the tutor session (`--remote-control lani-tutor`), so you can also reach the session from
claude.ai; that also goes through Anthropic.

How Anthropic handles it is set by your account's terms: the consumer terms and privacy settings for a Pro or Max plan,
the commercial terms for a Console API key. See [anthropic.com/legal](https://www.anthropic.com/legal). To keep a
conversation out of it, don't send it to the tutor.

## If you set an ElevenLabs key: ElevenLabs

With `ELEVENLABS_API_KEY` in `keys.env`, the bridge asks ElevenLabs to speak texts:

- the lines of the curated content (villagers, dialogs, stories, words), made once and kept in the voice cache;
- lines the tutor writes for the learner (role-play, new content), when the app first plays them;
- for a villager's own voice (Voice Design): a description of the character and a sample sentence.

The texts only: not the learner's name, data, recordings or answers (unless the tutor wrote them into a line). The
bridge also reads the account's remaining characters. Without a key nothing goes to ElevenLabs, and the phone speaks.

## If you use Tailscale: Tailscale

The phone and the machine talk over your tailnet, end to end encrypted (WireGuard). Tailscale's coordination server
knows your devices, their names and addresses, not the traffic. `tailscale serve` gets an HTTPS certificate for
`<machine>.<tailnet>.ts.net` from Let's Encrypt, and certificates are public: that name appears in the Certificate
Transparency logs. Choose machine names you're happy to have seen. Never use Funnel: it would put the bridge on the open
internet.

## If you set a remote for the data repository: that remote

With `LANI_DATA_REMOTE` (or a GitHub repository the wizard made), every commit of the learner's data is pushed there:
the profile and databases, the village, what the tutor made, the family's recordings (`app/audio/`), the partner's
challenges, questions between towns, and the session results. Not the tokens, the bridge's key, the voice clips or the
APKs (`.gitignore`). Use a **private** repository; the wizard refuses a GitHub repository that isn't private. The
family's recordings are their voices: ask them before they go to a remote. `LANI_DATA_PUSH=off` keeps the commits on
this machine.

**From the phone too, when git is connected in the app** (💾 Podatki · Data): the phone syncs the same repository through
GitHub's API, with the learner's own GitHub token (kept in the phone's Keystore). It pushes what the node's `.gitignore`
lets through, never the tokens, the town's key, the recent answers, the developer's data or the chat's photos. The app
warns before it uses a public repository.

**The encrypted backup (the app):** the phone's data directory, encrypted on the phone with a key from the learner's
recovery code or passphrase, goes to Lani's relay; the relay holds only ciphertext and can't open it. The town's key and
the family token are inside, encrypted with the rest, so a restore brings the town back. Without the recovery code or
the passphrase nobody can open a backup, not even us. See [plans/05-built/git-sync.md](plans/05-built/git-sync.md).

## Only when you use them

- **Linked towns** (by invitation only): a linked town's bridge gets this town's public state (the village's name, its
  buildings and people, the learner's first name, today's visitor) and what you do there (help, gifts, trades,
  questions). Never the learner's words, mistakes, progress or chat. See [companion/README.md,
  "Towns"](../companion/README.md#towns).
- **The family page:** the partner's browser reaches the bridge over your tailnet (a node share) and sees the family
  page only: the word list, recordings, challenges.
- **The relay** (only with `LANI_RELAY_URL` set; plan 5's phase D, [docs/plans/05-built/relay.md](plans/05-built/relay.md)):
  the relay gets this town's public state (as linked towns get it) and passes on what towns and the family page send
  each other, so it can read it (its TLS ends there). It keeps the town's key and id, the ids of its linked towns, the
  family token's SHA-256 (never the token), what waits for a town that is away (up to 14 days) and its answers (a day).
  The towns sign everything between them, so the relay can't change it. Backups there are encrypted before they leave.
- **The phone's speech recognizer:** speaking exercises use Android's recognizer unless the node's Whisper does it.
  Whether audio leaves the phone depends on the recognizer (Google's may use its servers). The node's Whisper
  (`companion/stt-local`) keeps it on your machine.
- **Dictionaries:** with `LANI_LEXICON_URL` set, the bridge downloads the word-lookup dictionaries from there. The
  request carries nothing of the learner.
- **The phone's offline voice:** when the learner asks for it (after pairing, or in the app's settings), the bridge
  downloads that language's Piper voice from Hugging Face once per machine: the model from `rhasspy/piper-voices` and
  the espeak-ng data from `csukuangfj/vits-piper-sl_SI-artur-medium`, at pinned revisions, each file checked against
  its pinned SHA-256. Hugging Face sees the machine's address and which voice; the request carries nothing of the
  learner. `LANI_PIPER_URL` names another source (a mirror, or a local directory). The voice then runs on the phone:
  what it says doesn't leave the phone.
- **The app:** it talks to your bridge only. It updates itself from your bridge, and you download it once (GitHub
  Releases, or your own build).

## Photos in the chat

A photo the learner takes or picks in the chat (📷) is made ready on the phone before it goes anywhere: turned
upright, scaled to 1,568 px on its long side, and encoded afresh **without any metadata**: no place (GPS), no time, no
camera or phone model. It goes to the learner's own node only (the app's token; the family page and linked towns can't
reach it), which strips any metadata left once more and keeps it in `app/chat-images/<YYYY-MM>/` (readable by this user
only). From there it goes to Anthropic through the tutor, as chat text does, when the tutor opens it to answer. The
tutor's guide says never to copy, send or publish it anywhere else: a word pack or a card it makes gets the photo's
words, never the photo.

- **Never in git:** the data repository's `.gitignore` keeps `/app/chat-images/` out; each commit adds the line back if
  it is missing, and the bridge adds it at its start to a `.gitignore` without it. So it is never pushed to a remote.
- **Never to towns or the family page:** neither can read the photos, and nothing about them is in what towns see.
- **How long:** the phone keeps a photo while its message is kept (the chat keeps a year, and bookmarked messages); the
  node keeps a year of photos, by the month they came. `lani-backup` snapshots of the data (on this machine) include
  them.
- **Deleting one:** a long press on the photo, "🗑️ Izbriši fotografijo · Delete photo", deletes the phone's copy and the
  node's (also later, through the outbox, when the phone is offline). A backup snapshot made before keeps its copy until
  the snapshot goes, and what the tutor already saw of it is in the tutor's conversation.
- **The shadow tutor** (a developer setting, with the learner's own API key) sends a chat's photo to the Messages API
  too, as an image block; its export and the copy it leaves on the node have a note in the photo's place, never the
  photo.

## Lani on the phone (the hosted mode)

With "📱 Samo ta telefon / tablica · Just this device" (the app's "☁️ Lani na tej napravi") there is no machine: the learner's data lives on the phone, in the
app's own files (`<filesDir>/lani/data`), which only the app can read, and Android's backup leaves them out
(`allowBackup` is off). The bridge runs inside the app, so nothing of it goes over a network. What leaves the phone:

- **The tutor's provider**, once the learner sets one up (the in-app tutor, on the learner's own key): what the tutor
  needs for a turn.
- **The relay**, only when one is set (the build's, or the connection sheet's "🛰️ Posrednik (relay)"): the town's
  public state, its mail with other towns and the family page, and the encrypted backup. Without one, nothing.
- **GitHub**, only when git is connected in 💾 Podatki · Data (above).
- **An export** ("📤 Izvozi moje podatke · Export my data"): a zip of the data's files, without the secrets, the recent
  answers, the developer's data or the chat's photos. It goes where the learner sends it from the share sheet; it holds
  the learner's mistakes, sessions and family names, so it is private data.

## What stays on the machine

The learner's data, the tokens and the bridge's key, the voice clips (and the offline voice, `~/.cache/lani/piper`), the backups (`~/.local/share/lani/backups`), the
logs (`~/.local/state/lani`) and `keys.env`. `lani-backup` snapshots stay on the machine unless you copy them. The
learner's recent answers (`app/answers.json`: what they typed or said in the last 7 days, at most 500 answers) stay
too: a data repository never commits them, so they are never pushed (each commit keeps them out, also in a repository
set up before they were kept). So does the developer's data from the app (`app/dev/`, only while the app's developer
setting is on: the shadow tutor's exports and the app's log, cleaned of tokens and keys; kept 30 days), and the
chat's photos (`app/chat-images/`, a year), but for the tutor reading them (above).
