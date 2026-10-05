# Plan 1: making Lani available to others

*Status: draft, 2026-09-25. [Plan 2](02-worlds-languages-multiplayer.md) builds on this one (own worlds, other
languages, towns that visit each other); a later plan settles the rest: see [What a follow-up plan should settle](#what-a-follow-up-plan-should-settle).*

Today Lani is one learner's setup: Jan's machine, Jan's keys, Jan's data. This plan covers
how other people could use it, in two variants:

1. **Bring your own keys: host it yourself.** A learner runs the tutor on their own machine with their own
   Claude account and, optionally, their own ElevenLabs key.
2. **We host it.** We run the servers and provide the voices and the Claude access. The learner installs the
   app and signs in.

There is also a middle way: we host the heavy parts and the learner brings their own Claude (see
[Variant 1.5](#variant-15-we-host-the-heavy-parts-the-learner-brings-claude)).

**Recommendation.** Do Variant 1 first. It forces the work every variant needs: taking out what's personal,
configuration, pairing, packaging and docs. It carries no legal or running costs for us, and it finds the
rough edges with a few friendly testers. Variant 2 then reuses all of it. Its new, big part is replacing the
always-on terminal session with a multi-user agent service.

**One hard constraint up front.** A hosted Lani cannot let learners sign in with their Claude
subscription unless Anthropic approves it. It must use API keys: ours, or the learner's own. See
[Constraints](#constraints-checked-2026-09-25).

## Jan's decisions (2026-09-25)

- **Voices: we host them.**
  - A voice service holds our ElevenLabs account and builds up, over time, a shared database of
    characters, languages, words and phrases. Every clip is made once, for everyone.
  - Self-hosted nodes use it with a node key, and need no ElevenLabs key of their own.
  - The proof of concept runs on a local Kubernetes cluster, in a new repository: see
    [plan 3](03-voice-service.md).
- **Claude, two variants:**
  - **Self-hosted,** as now: Claude Code on the learner's machine, reached over Tailscale.
  - **Hosted:** we host everything, and the learner needs no token or key. This comes **last**.
- **One app for both.** On first launch the learner chooses:
  - "🔗 Poveži svojega učitelja · Connect my own tutor", which scans a QR code;
  - or "☁️ Lani v oblaku · Lani cloud", which signs in.

  Settings can switch between them later. The data model and screens are the same; only the connection
  differs.
- **Self-hosted bootstrap: Tailscale + Claude Code, and a QR code to pair the app.** See
  [Pairing by QR code](#pairing-by-qr-code).
- **The licence: later.**
- **The name: Lani** (proposed by Jan, recommended).
  - **The domains** are Jan's: lani.page, lani.help, lani.team (and `lanisce.si` behind the app's package).
  - **It's short and easy in any language.** In Slovene and Croatian "lani" means "last year", hence the
    tagline *"Lani nisem znal, letos znam · Last year I didn't know it, this year I do."*
  - **The domains' roles:**
    - **lani.page:** the app and its site, later the hosted service; now the dev setup (`glas.lani.page`).
    - **lani.help:** docs, FAQ, support, the privacy policy.
    - **lani.team:** family and friends: invites, visiting each other's towns, the family page.
  - **A name to watch:** a small "Lani AI" chat and horoscope app on Google Play (another category). Check
    the trademark before launch; a store name like "Lani – moja vas" sets it apart.
  - *Earlier ideas: Kres (the midsummer bonfire), Vasica ("a little village"), Sosed ("the neighbour"). Lipa
    is taken in the category (Lipa Learning).*
- **The core dump** is out of the tree (a3dd17b) and ignored from now on. It leaves the history with the move
  to the new repository.

## Jan's decisions (2026-09-26)

- **A new public repository for the app** (the bridge, the app, skills, curated content, culture packs,
  the dictionary builder), started with a fresh history. Before it goes public, what is Jan's comes out:
  CLAUDE.md written for Jan, content that greets "Jan!" (the learner's name instead).
- **Dictionaries are downloads, not bundled.** The Wiktionary extracts are large (German 1 GB, English
  3 GB, Italian 0.7 GB; Slovene 27 MB, built down to 1.4 MB). Built dictionaries are versioned release
  files; a bridge downloads the languages its learners use, checks and caches them. Their licence (CC
  BY-SA) stays apart from the code's. Meanings in a base language other than English come through
  Wiktionary's translation tables, with Claude filling the gaps (flagged machine-written).
- **Dictionaries keep growing, like the voice store:** the base, the supplement, and what each tutor adds
  with `publish_gloss`; later, reviewed additions flow back into the shared dictionaries.
- **Each learner's data is its own private git repository** (Jan's, the second learner's): auto-commits after a
  session, an optional push to a private remote, on top of the snapshots. Never in git: the voice clips
  (a cache; they belong in glas).
- **Secrets, reasoned through:** each side keeps the secret that proves its own identity and only a
  fingerprint or hash of the other's. The phone holds its device token and the node's key fingerprint;
  the node needs what it checks requests against. So:
  - the shared `bridge-token` (from before QR pairing) retires: every phone uses its own device token;
    the family page moves to per-person invite tokens, stored as hashes;
  - `devices.json` (hashes only) goes into the learner's repo;
  - the bridge's private key goes into the repo encrypted with sops + age, so a learner's
    repo is a complete, portable copy of their tutor and a move to another machine keeps every pairing;
    only the age key travels separately.

### Pairing by QR code

1. `lani setup` (or `lani pair`, to add a phone later) prints a QR code in the terminal and shows it on
   a local page. The QR holds:
   - the bridge's URL (the `tailscale serve` HTTPS name);
   - a one-time pairing code, valid for 10 minutes;
   - the learner profile it pairs with;
   - a fingerprint of the bridge's key, so the app can tell it's the right node.
2. The app's "Connect my own tutor" opens the camera. It uses CameraX with an on-device barcode scanner:
   ML Kit's bundled model, or ZXing for an F-Droid build without Google libraries. It reads the QR and calls
   `POST /pair {code, device}`.
3. The bridge checks the code (once, unexpired) and returns a long-lived device token, which the app keeps
   in its encrypted storage. The code is spent.
4. Each paired device is listed (`lani devices`), and any of them can be revoked. A second learner on the
   same machine (plan 2) has their own profile, and so their own QR code.
5. The family page keeps its link. A QR code for it (the family token in the fragment, as now) makes giving
   it to the partner easier.

The phone must be on the tailnet: the Tailscale app, signed in to the learner's account, or a node share.
`lani setup` says so and checks it (`tailscale status`).

---

## Where we are

```
Android app ──HTTPS (Tailscale)──► bridge (Bun) ──channel (stdio MCP)──► Claude Code session (tmux, always on)
                                     │  ├─ learner DBs (data/*.json, read-db.py / update-db.py)
                                     │  ├─ app content (modules, packs, scenarios, scenes, villagers)
                                     │  ├─ voice store (voice.db + mp3s) ── ElevenLabs (eleven_v3, Voice Design v3)
                                     │  │                               └─ Gepard TTS worker (GPU, :8795)
                                     │  ├─ STT ── Whisper large-v3-turbo worker (GPU, :8796)
                                     │  ├─ family page + partner challenges (family token)
                                     │  └─ app releases (self-update, latest.json)
                                     └─ Remote Control (claude.ai) into the same session
```

What assumes one learner today:

| Area | Today | What sharing needs |
|---|---|---|
| Tutor | One interactive `claude` session in tmux (`companion/bin/lani-session`), started with `--dangerously-load-development-channels`, using Jan's Claude login. The session's context is the tutor's short-term memory. | Self-host: the same, installed by script. Hosted: an agent service, one run per message, with no terminal. |
| Data | One `data/` directory with 6 JSON databases plus `data/app/` (tokens, voice store, releases, content). | Self-host: the same. Hosted: per-tenant storage with isolation. |
| Access | Two bearer tokens in files. The app URL is entered by hand. Tailscale Serve (Funnel off). | Pairing by QR code, network options, and accounts for hosted. |
| Voices | One ElevenLabs Starter account (10 voice slots, ~31k chars a month), a `voice-cast.json` of library voices, designed per-villager voices. | A clip pack for curated content; a shared cache and a bigger plan for hosted. |
| Speech | Gepard and Whisper on Jan's AMD GPU (ROCm). Each takes about 2.5–3 GB of VRAM. The venvs are ~15–17 GB. | Optional and CPU-aware for self-host; a GPU box or a hosted STT for us. |
| App | Sideloaded APK that updates itself from the bridge (`bin/release-app`). | GitHub Releases for self-host; a Play Store build (no self-update) for hosted. |
| Content | Slovene only: 6 modules, 20 packs, 8 scenes, 13 villagers, role-play scenarios. It's tuned to one learner (the region, a partner's name, the family page). | Profile-driven: who the partner is, the region and the names come from setup, not from the files. |
| Repo | A fork of `m98/fluent` (MIT, © Fluent Contributors). `origin` points to upstream, and nothing is pushed. | Our own public repo, with a clean history. |

---

## Constraints (checked 2026-09-25)

**Anthropic.**
- **No claude.ai login in third-party products.** The [Agent SDK overview](https://code.claude.com/docs/en/agent-sdk/overview)
  says: *"Unless previously approved, Anthropic does not allow third party developers to offer claude.ai login
  or rate limits for their products, including agents built on the Claude Agent SDK. Use the API key
  authentication methods … instead."*
  - A hosted Lani therefore runs on API keys.
  - A learner running Claude Code on their own machine with their own subscription is ordinary personal use.
- **Commercial terms.** The Agent SDK falls under the [Commercial Terms](https://www.anthropic.com/legal/commercial-terms),
  including when it powers a product for end users.
- **Branding.**
  - Allowed: "Claude", or "Lani, powered by Claude".
  - Not allowed: anything that looks like Claude Code.
- **[Channels](https://code.claude.com/docs/en/channels) are a research preview.**
  - A channel that isn't on Anthropic's allowlist (ours isn't) only loads with `--dangerously-load-development-channels`.
  - The flags and protocol may change.
  - Channels work with a Pro/Max login or a Console API key. Team and Enterprise organisations must enable them.
  - A self-host release depends on this preview feature. Getting the bridge onto the allowlist, or keeping
    the dev flag, is a decision to make.

**ElevenLabs.**
- **Commercial use.** It needs a paid plan; Starter and up include a commercial licence for the output. You may
  put generated audio in your product, but you may not resell the ElevenLabs service itself
  ([summary](https://terms.law/ai-output-rights/elevenlabs/)). *To verify in the actual terms before
  shipping a clip pack or hosting.*
- **Slovenian and voices.** Slovenian only works with `eleven_v3`. The library has one Slovenian voice
  (Uros, 30-day notice period). The cast uses library voices of other languages, which may be withdrawn after
  a notice period. The designed voices belong to the account that made them.
- **Plans.** Voice slots and characters grow with the plan (Starter 10 slots; Creator 30). *Check current
  plans and prices.*

**Speech models.**
- **Licences:**
  - Whisper (MIT).
  - The Gepard Slovenian TTS LoRA (Apache-2.0).
  - NVIDIA NanoCodec (NVIDIA Open Model License). *Check what that licence allows when it's hosted for others.*
- **Hardware:** both need a GPU to be quick. Gepard on a CPU takes 3–15 s per sentence.

**EU law.** Relevant to Variant 2, and partly to shipping anything at all.
- **GDPR:**
  - The learner data, and above all the family's voice recordings, are personal data.
  - The digital age of consent in Slovenia is 15.
- **AI Act transparency obligations (Art. 50)** apply from 2 August 2026. People must be told they are
  talking to an AI, and synthetic audio must be marked. *Check what that means for tutor chat and
  ElevenLabs voices.*
- **EU VAT (OSS)** on digital services.

---

## Phase 0: make it shareable (needed by every variant)

1. **Clean the repository history.**
   - `companion/android/core.197872` is a **10 GB core dump committed in afc79b2**. It makes `.git` 1.7 GB, and
     it may contain memory contents (environment variables, tokens). Remove it from the whole history
     (`git filter-repo`) before anything is published, and add `core.*` to `.gitignore`.
   - Scan the history for secrets (`gitleaks`/`trufflehog`), tokens, the tailnet name and e-mail addresses.
   - Keep the stock reference images in the repo root (`1000_F_…jpg`, `360_F_…jpg`,
     `pixel-farm-life-stockcake.jpg`, `tent.webp`, `original-…png`) out of the repo: they are someone else's
     art. They are untracked today.
   - Never publish: `data/`, `results/`, `qa-shots/`, releases, voice stores, `.claude/settings.local.json`.
2. **Our own repository and name.**
   - "Fluent" is upstream's name and a common word. Pick a name for the companion, and keep the MIT notice
     for the upstream parts. (Done: the project is Lani, fentas/lani; docs/migrate-from-fluent.md.)
   - Decide the licence for our parts. Options:
     - MIT, like upstream: the simplest.
     - AGPL-3.0 for the bridge: anyone who hosts a modified version must share it.
     - Source-available: keeps a hosted service to us.

     This choice shapes Variant 2.
3. **Take out what's personal.**
   - Names (the learner's, the partner's and its "from" form), the region (in voice descriptions), and masculine
     forms addressed to the learner.
   - These come from the learner profile at setup: the learner's name and grammatical gender (Slovene
     agrees with it), the partner's name and their "from" form, the region and accent.
   - Docs and skills speak of "the learner".
4. **Configuration instead of constants.** Every port, path, key and limit goes in one `lani.env` (or TOML).
   Keep the current defaults, and give the file a documented schema. (Done: `~/.config/lani/lani.env`, read by the
   bridge, the hooks and the scripts, the environment winning; its schema in [docs/setup.md](../setup.md#lanienv).)
5. **Pairing.**
   - On first start, the bridge shows a QR code in the terminal and on a local page. It holds the URL and a
     one-time pairing code, which the app trades for its token.
   - Tokens can be revoked. A second phone can be added.
6. **Content packaging.** Curated content (modules, packs, scenarios, scenes, villagers, voice cast) gets a
   version number, so an update can ship new content without touching the learner's own.
7. **Docs.**
   - A README for learners: what it is, what it costs, a 15-minute setup.
   - A troubleshooting page.
   - A privacy note: what leaves the machine and where it goes.

   (Done for self-hosting: [docs/setup.md](../setup.md), [docs/troubleshooting.md](../troubleshooting.md),
   [docs/privacy.md](../privacy.md).)

Rough effort: 1–2 weeks. Most of it is mechanical, but taking out what's personal touches the content and
the tutor skills.

---

## Variant 1: bring your own keys, host it yourself

**For:** technical learners of Slovene with an always-on machine (Linux, macOS, maybe WSL), a Claude
Pro/Max subscription or a Console API key, and an Android phone.

**Tiers:**

| Tier | Needs | Gets |
|---|---|---|
| Minimal | Bun, Claude Code (subscription or API key), Python 3 | Everything, with the phone's TTS and recognizer for speech. |
| Standard | + ElevenLabs key (Starter is enough: about one month of credits voices all curated content) | Natural voices, a voice for each villager, role-play lines voiced live. |
| Full | + a GPU with ~6 GB VRAM (CUDA or ROCm) | Local Whisper for accurate speaking checks, and Gepard as the offline voice. |

**Work items:**

1. **Installer** (`lani setup`, idempotent). It:
   - checks for Bun, Claude Code and Python;
   - creates the data directory and `keys.env` (mode 600);
   - runs the learner-profile onboarding;
   - installs the session as a systemd user service (Linux) or a launchd agent (macOS) instead of hand-run
     tmux;
   - prints the pairing QR code.

   Linger and background running are explained and asked about, never switched on silently.

   (Done: `companion/bin/lani-setup`, [docs/setup.md](../setup.md). It also makes the learner's data a git repository
   of its own, imports an install from before, and keeps the caches out of the data.)
2. **Optional workers as containers**, with a `docker compose` profile each:
   - `stt` in cuda, rocm and cpu builds;
   - `tts-local`.

   The bridge keeps working when they are missing, as it does today.
3. **Networking options.**
   - Tailscale Serve: recommended, as today.
   - LAN only: the phone and the machine on the same Wi-Fi.
   - Cloudflare Tunnel, or a reverse proxy with TLS: for advanced users, with a warning.

   Never plain HTTP, and never Funnel without a warning. The pairing QR carries whichever URL is chosen.
4. **Claude, either way:**
   - **Subscription (Pro/Max).** `claude` logged in with the learner's own account. This is personal use of
     Claude Code. The always-on session consumes that account's usage limits: document typical usage.
   - **Console API key.** Pay per token, with no usage-limit surprises. Show an estimate (see
     [Costs](#costs)).
   - The `--dangerously-load-development-channels` flag needs a plain explanation: what it means, and why
     it's there during the preview.
   - Longer term, ask Anthropic to put the bridge on the channel allowlist, or ship it through a plugin
     marketplace an organisation can allowlist.
   - Permission prompts reach the phone through the permission relay (already built). No
     `--dangerously-skip-permissions`.
5. **A voice clip pack.**
   - Publish the curated content's ElevenLabs clips (and Gepard clips) as a versioned download, with an
     index file the bridge can import.
   - A self-hoster without an ElevenLabs key then still hears natural voices for everything curated. Only
     new, personal lines need a key, Gepard or the phone.
   - *Check the ElevenLabs terms: redistributing output made with library voices.* The designed per-villager
     voices stay in our account; only their clips ship.
6. **App distribution.**
   - A signed APK on GitHub Releases, with our release key. The app then updates itself from the learner's
     own bridge, as today (`bin/release-app` becomes a CI job that the bridge fetches from).
   - Later: F-Droid (it needs reproducible builds and no proprietary dependencies; check Google Speech
     Services usage).
   - The Play Store isn't needed for Variant 1.
7. **Updates and migrations.** `lani update` pulls a release, runs data migrations (the databases get a
   schema version) and restarts the service. Backups (`bin/lani-backup`) run before every update.
8. **Family features** stay optional: the family page still needs the partner to reach the machine (a
   Tailscale node share, or the LAN).
9. **Support.** GitHub Discussions for questions, and a "doctor" command that checks keys, ports, the
   session, workers and the app connection.

**Risks:**
- The channel preview changes or ends.
- Claude Code updates break the session.
- The support load from home-server setups.
- A subscription's usage limits running out mid-month for heavy learners.

**Effort:** 3–5 weeks after Phase 0, including a beta with 3–5 testers.

---

## Variant 1.5: we host the heavy parts, the learner brings Claude

Some learners have Claude, but no GPU, no ElevenLabs account and no wish to run a server. We could host
the bridge, the voice store (with the shared clip cache), STT and content updates. The learner runs only
Claude Code at home, with a small local channel plugin that connects to our bridge over HTTPS.

- **Allowed:** we never see or use their Claude credentials, and they use Claude Code themselves. This is like
  any third-party MCP server. *Still worth confirming with Anthropic before promoting it.*
- **Limitation:** the tutor only answers while their Claude Code session runs, so they still need an
  always-on machine. That takes away much of the appeal.
- **What it offers:** a stepping stone that tests multi-tenant hosting (accounts, storage, voice costs)
  without our Claude costs. Build it only if Variant 2 slips.

---

## Variant 2: we host it

**For:** learners who just install the app. We run everything: accounts, the tutor, voices, speech and
backups.

### Claude access options

| Option | How | Allowed | Notes |
|---|---|---|---|
| **2a. Our API key** (recommended) | Agent SDK or Managed Agents with our Console key; the cost is in the price. | Yes (Commercial Terms) | Per-learner token budgets, a model per task, prompt caching. |
| **2b. The learner's own API key** | The learner pastes a Console key. We encrypt it at rest and use it only for their runs. | Yes, but key custody is ours | A cheaper plan for power users. We hold a secret that bills someone else: KMS, audit log, revocation. |
| **2c. The learner's Claude subscription (login)** | OAuth into their claude.ai account. | **No, unless Anthropic approves** | Park it. If there's traction, ask Anthropic about a partnership. |

### Architecture changes: the main work

1. **Accounts and tenancy.**
   - Sign-in by e-mail magic link, with Google and Apple sign-in on the phone.
   - A tenant id on every route and every stored object. The tokens become per-device sessions.
   - Family access gets a per-tenant invite link.
2. **Storage.**
   - Per-tenant learner databases: SQLite per tenant, or Postgres with row ownership.
   - Object storage (S3-compatible, in the EU) for clips and recordings.
   - Shared, read-only curated content, plus each tenant's own published content.
3. **The tutor as a service.** This replaces the tmux session.
   - Each app message or schedule tick becomes one agent run with the Agent SDK (TypeScript, inside the
     bridge process or a worker pool), resuming the tenant's session. Anthropic's
     [Managed Agents](https://platform.claude.com/docs/en/managed-agents/overview) is the hosted alternative.
   - The tools are the bridge's MCP tools, in process: `reply`, publishing, reviews, voice. There are no
     shell, file or web tools, or at most a per-tenant sandbox. The skills (`lani-*`, `lani-studio`) load
     as today.
   - A fixed allowlist of tools replaces the permission prompts. Family and partner text stays marked as
     untrusted content (already done).
   - The daily rhythm (morning, evening, weekly) becomes a job queue with per-tenant time zones.
   - **Memory** is no longer the long-lived session context. It becomes the learner databases, plus a
     compact running summary per tenant: cheaper and more predictable.
   - **Models per task:** Haiku for grading and short replies, Sonnet for tutoring and role-play, the bigger
     model rarely (new modules or scenes). Cache the static system context (CLAUDE.md,
     LEARNING_SYSTEM.md, skills).
   - **Budgets:** a token budget per tenant per day; above it, the tutor becomes brief, and the app says so.
4. **Voices.**
   - One ElevenLabs account, a plan sized by usage.
   - Curated content is voiced once for everyone: the shared cache is keyed by text + voice, as today.
   - The cast's designed voices are made once and shared.
   - Residents' own voices only in a higher tier, or never (archetype voice + pitch/pace, as today).
   - Live lines, such as role-play, count against the tenant's quota.
5. **Speech recognition.** Two options:
   - One GPU server running Whisper large-v3-turbo. Under 1 s per clip and one at a time, so it needs a
     queue: fine for a beta.
   - A hosted STT API with Slovenian. *Compare its WER on our test clips first (`stt-local/bench.py`).*
6. **The Android app.**
   - **A Play Store build:** no self-update (Play forbids it; keep that for the sideload flavour),
     sign-in, the Data safety form, AI disclosure, an age rating.
   - **Subscriptions** inside a Play app must use Google Play Billing. In the EEA, alternative billing is
     possible under Google's DMA programme, with fees. Selling on the web is an option too, with Play's
     rules on linking out.
7. **Operations.**
   - EU hosting (e.g. Hetzner, Scaleway, OVH): one CPU node for the bridge and workers, one GPU node for
     STT and Gepard (or none).
   - Backups to a second provider, monitoring and alerts.
   - Per-tenant cost dashboards (tokens, characters, storage), abuse limits (the rate limits on challenges
     and messages already exist), and an incident runbook.
8. **Legal and business.**
   - A legal entity (in Slovenia, an s.p. or d.o.o.).
   - Terms of service, a privacy policy, and data processing agreements with Anthropic, ElevenLabs and the
     host. A list of sub-processors.
   - Data export and deletion, consent for family recordings (their voices; possibly children).
   - AI Act transparency: "you are talking to an AI tutor", with synthetic voices labelled.
   - EU VAT OSS, consumer withdrawal rights for subscriptions.

### Costs

These are orders of magnitude to test against real measurements, not quotes. **First step: measure Jan's
real usage.** That means Claude Code's usage for the tutor session, and the voice store's character log
(`voice_status`), over two weeks.

Per active learner per month, with example prices. Check current prices; these are Sonnet-4-class list
prices ($3 / $15 per million input/output tokens, $0.30 per million for cache reads) and Haiku-4.5-class
($1 / $5):

| Item | Assumption | ~Monthly |
|---|---|---|
| Tutor turns | 20 a day, each ~22k cached + 3k new input tokens and 400 output (Sonnet) | ~$13 heavy; ~$3 at 5 a day |
| Same, grading and short replies on Haiku | about half the turns | saves 30–40% |
| Content generation | 1 module or scene a week (~30k in, 6k out) | < $1 |
| ElevenLabs live lines | 3–10k characters (role-play, new personal content) | $1–3 at plan rates |
| Curated voices | voiced once for all learners (~17k billed characters today) | ~0 per learner |
| STT and hosting | a shared GPU box and a CPU node, over 50–100 learners | $2–5 |

So a hosted subscription likely needs €9–15 a month, or a Claude budget with a cheaper tier. Option 2b
(the learner's own API key) could sell for about €4–6 a month.

### Effort

About 2–3 months for one person to reach a closed beta:

- 3–4 weeks: tenancy, storage and accounts.
- 3–4 weeks: the tutor service with budgets and jobs.
- 2 weeks: the app's sign-in and a Play build.
- 2 weeks: operations, backups and cost dashboards.

The public launch then waits on the legal work and billing (more weeks, partly in parallel).

---

## Suggested order

| Phase | What | Exit criterion |
|---|---|---|
| 0 | Make it shareable (above) | A fresh clone contains nothing personal. `lani setup` works on a second machine. |
| 1 | Self-host alpha: Variant 1, Minimal and Standard tiers | 3–5 testers learn daily for 2 weeks. Setup takes under 30 minutes. |
| 1b | Clip pack and Full tier (containers) | A tester without ElevenLabs hears the curated content. STT works in the cuda container. |
| 2 | Measure: tokens and characters per learner-day, from Jan and the testers | A cost model from data, not guesses. |
| 3 | Hosted closed beta: Variant 2a, invite-only, free, EU | 10–20 learners. Costs per learner as measured. No data leaks between tenants (tested). |
| 4 | Public hosted | Legal work, billing, the Play Store, support. |

Variant 1.5 only if Phase 3 slips. Option 2c only with Anthropic's approval.

---

## Decisions needed from Jan

1. **The name** for the public project, and the **licence** for our parts (MIT, AGPL or source-available).
2. **Who it's for:** learners of Slovene in general (expats, partners of Slovenes, the diaspora), or also
   other languages? Upstream Fluent supports any language, but the companion's content, voices and scenes
   are Slovene.
3. **Hosting at all?** Is Variant 2 a goal (a business, a hobby service for friends), or is Variant 1
   enough for now?
4. **Claude for the hosted tier:** our key (2a), the learner's key (2b), or both.
5. **Budget** for a beta: API, voices, GPU.
6. **The repository clean-up** (history rewrite to drop the core dump) and where the new repo lives.
7. **iOS or web?** Android only for now. A web client for the chat and the family page would reach iPhone users
   cheaply; a native iOS app is a separate project.

---

## What a follow-up plan should settle

- The multi-tenant design in detail: data model, storage, isolation tests, the agent service's interfaces,
  the job queue.
- The cost model, from the measurements in Phase 2, and prices and tiers.
- Shipping the channel for self-hosters: the allowlist or marketplace route, and what happens when the
  preview ends.
- Learner onboarding in the app (the profile, the partner/family and region questions), and removing
  Jan-specific content from the curated sets.
- Legal work: the entity, terms, privacy, DPAs, AI Act labels, and the timeline.
- Go-to-market: who the first 20 learners are and where they come from.
