# Privacy: what leaves your machine

Lani runs on your machine. The learner's data (their profile, mistakes, progress, reviews, the village, what the tutor
made for them, the family's recordings, the session results) stays in `~/.local/share/lani/<id>` on that machine. This
page lists everything that leaves it, where it goes, and how to turn it off. There is no telemetry and no analytics:
Lani itself sends nothing to us.

## Always: Anthropic, through Claude Code

The tutor is a Claude Code session on your machine, signed in with your Claude account (a Pro or Max plan) or your
Console API key. Claude Code sends the session's conversation to Anthropic to get the tutor's answers. That includes:

- what comes from the app: chat messages, answers to exercises, finished sessions and reviews (a summary), role-play
  turns, words the learner adds, a question the learner asks about an exercise;
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

## Only when you use them

- **Linked towns** (by invitation only): a linked town's bridge gets this town's public state (the village's name, its
  buildings and people, the learner's first name, today's visitor) and what you do there (help, gifts, trades,
  questions). Never the learner's words, mistakes, progress or chat. See [companion/README.md,
  "Towns"](../companion/README.md#towns).
- **The family page:** the partner's browser reaches the bridge over your tailnet (a node share) and sees the family
  page only: the word list, recordings, challenges.
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

## What stays on the machine

The learner's data, the tokens and the bridge's key, the voice clips (and the offline voice, `~/.cache/lani/piper`), the backups (`~/.local/share/lani/backups`), the
logs (`~/.local/state/lani`) and `keys.env`. `lani-backup` snapshots stay on the machine unless you copy them.
