# Plan 3: a hosted voice service (a new repo)

*Status: draft, 2026-09-25. Jan's decision for [plan 1](01-public-availability.md): we host ElevenLabs
ourselves and build up, over time, a database of characters, languages, words and phrases. The proof of
concept runs on a local Kubernetes cluster, in a new repository. Working name: **glas** (Slovene for "voice").*

## What it is for

Today every Lani node voices its own texts. The bridge's `VoiceStore` (`companion/bridge/src/voice.ts`,
`profiles.ts`, SQLite `voice.db` plus mp3 files) calls ElevenLabs with the node's own key, and so pays again
for texts that other nodes have already voiced. **glas** takes that over:

- **One ElevenLabs account (ours), one place that calls it.** Self-hosted nodes and the hosted service ask
  glas for clips with a node key. For the shared, pre-generated library they need no ElevenLabs key of
  their own (see [What ElevenLabs allows](#what-elevenlabs-allows-checked-2026-09-25) for lines outside it).
- **Every clip is made once, for everyone.** "Dober dan!" in the grandma voice is made once and served to
  every node. The more learners, the cheaper each one gets.
- **The shared library grows over time:**
  - languages;
  - voices: library voices, designed voices, archetypes like "grandma" per language;
  - characters: a persona with a voice per language;
  - the corpus of words and phrases that have been voiced, per language, with how often they're asked for.
- **Quality control.** Whisper checks what was made (word match, as the bridge's voice design does today). Bad
  takes are redone; a withdrawn library voice is flagged, and its clips are remade.

## What ElevenLabs allows (checked 2026-09-25)

From the [Terms of Service](https://elevenlabs.io/terms-of-use):
- **§5(b):** the licence to use the *Services* is "non-sublicensable". Building the Services into a product
  that others use falls under ElevenLabs' separate OEM terms.
- **§4(c)(ii):** we "retain all rights in and to" the *Output*.
- **§1(c):** a paid plan may use it commercially. Starter is enough.

So the rule for glas:
- **Pre-generated clips are fine.** We generate words and phrases, keep them, and serve them to any node or
  user. That's distributing our Output.
- **It must not become a wrapper:** no endpoint that lets someone else get *arbitrary text* voiced by
  ElevenLabs on demand.
  - Self-hosters' nodes get the shared, pre-generated library.
  - A line that isn't in it (a personal role-play line) is voiced with their own ElevenLabs key, a local
    engine (Gepard or Piper), or the phone.
  - `say` (live generation) is only for our own product's content: the hosted tutor's lines and the curated
    content. Before the hosted variant voices tutor lines live for many users, check the OEM terms or ask
    ElevenLabs.

## Voices and slots

Per the account's subscription data (2026-09-25):
- Only **designed voices** take voice slots: 2 used of Starter's 10 (Creator 30).
- **Library voices** (the cast's Zlata, Tomasz, Bartłomiej…: 10 on the account) and the **21 premade**
  voices take **no slots**.
- Designs and additions have their own monthly counter (2 of 65 used).
- A library voice needn't be added first (checked 2026-09-27): the first text-to-speech request with its id adds it
  to the account's voices. That takes no slot and doesn't count as an addition. So an audition adds every
  candidate it hears.

**Adding and removing voices** (ElevenLabs help pages, 2026-09-25):
- **Adding** takes effect at once: a designed or saved voice can be used right away. There's no downtime.
- **Adding and editing count against a monthly budget:** Starter 65, Creator 95, Pro 290, Scale 1040. It
  resets with the credits.
- **Slots:** Free 3, Starter 10, Creator 30, Pro 160, Scale 660. Library and default voices take none.
- **Deleting** a voice frees its slot and can't be undone.
  - A voice saved from the library can never be saved again.
  - A designed voice can't be made again exactly (designs are random).
  - The clips already made stay ours; a voice is only needed for new lines.
- **Concurrency:** Free 2, Starter 3, Creator 5, Pro 10, Scale 15 requests at once.
  - Beyond that: `429 too_many_concurrent_requests`.
  - `429 system_busy` means ElevenLabs is overloaded; retry with backoff.
  - glas's queue runs at most the plan's number of requests at once.
- **The policy** (as the bridge's `profiles.ts` does now): design on demand, at most a few a day, keep 10 edits
  and a slot spare, and delete only voices of characters that are gone.
- **What a plan costs** (list prices, check before buying):
  - **Per credit, the small plans are about the same:** Starter about $0.19 per 1,000, Creator about
    $0.22 per 1,000 (100k credits, about 220k characters of v3).
  - **What Creator adds:** overage billing instead of a hard stop, 30 slots, 95 operations, and 5 requests
    at once.
  - **Per slot, Starter is the cheapest,** but several accounts don't pay off:
    - The terms forbid sharing one account's credentials (§3), and ElevenLabs may bar users at its discretion
      (§6(f)).
    - A designed voice can't move between accounts, so glas would route each character to its account, with
      separate credits and limits for each.
  - **Slots won't be the tight limit** (library voices are free; only Slovene needs designs). One account,
    Creator when the credits run short.

**How many voices a language needs.** Jan's idea: a language needs two voices, and the rest are variants
we generate. That holds for adults, but not for a whole cast:
- **Pitch and pace** (what the app's voice profiles do: ±7 % pitch, ±4 % pace, no cost, no slots) makes
  individuals of one voice. Farther than that, a voice sounds processed. A child or a grandmother made from
  an adult voice doesn't sound like one.
- **Delivery** can vary without new voices too: `eleven_v3` audio tags (`[whispers]`, `[excited]`,
  `[sighs]`) change how a line is said, in the same voice.
- **Age and gender** need their own voices: a new library voice (no slot), or a design from a base voice.
  Voice Design v3 with the base as reference (`prompt_strength`, the "like" option), or a remix of a
  designed voice, gives an older or younger voice that still fits the family. Each design takes a slot.
- **So a language needs about 6 core voices:** a girl, a boy, a woman, a man, a grandmother, a grandfather.
  Everyone else is one of them at their own pitch, pace and delivery.
  - For a language with native library voices: 0 slots.
  - Designed (Slovene): 6 slots, so Creator's 30 hold about five designed languages.
- **Role-play lines** are generated once, in the character's voice, when first heard, and kept. That fits
  the terms: it's our product's own tutor content. But a role-play line is rarely asked for again word for
  word, so those lines save little on reuse. The savings come from the words, the phrases and the curated
  lines that every learner hears.

So glas plans voices like this:
- **Archetypes per language come from the library where it has native speakers.** Italian and German have
  many, so they cost no slots.
- **Voices are designed only where the library has none** (Slovene: the one Slovenian library voice can't be
  remixed), and for a few signature characters.
- **Everyone else is an archetype at their own pitch and pace**, as the app's voice profiles already do.
  Two languages' designed archetypes fit Creator's 30 slots.
- **Credits are the real limit.**
  - Starter: 30,935 characters a month, of which about 21k are used now.
  - Creator: about 100k a month, and `eleven_v3` has been billed at about half a character per character of
    text.
  - Upgrading to Creator for a year pays off once glas pre-generates a second language's starter content
    (and Slovene is re-voiced in designed voices). For Jan alone, Starter still covers the month. The
    account is on annual billing already, so *check how a mid-term upgrade is prorated*.

### Decided: lone Slovene words in a carrier sentence (2026-09-26)

**The problem.** Jan hears single words on the vocabulary cards (e.g. *kosilo*) pronounced the English
way. Sentences are fine.

- Every request already sends `language_code: 'sl'`, but with `eleven_v3` that is only a hint: for a lone
  word the voice's own accent wins.
- The narrators are English library voices (Matilda, Daniel). The library has no native Slovene voice to
  switch to (searched: one voice described as Slovenian, tagged Croatian).
- 2,058 narrator clips are ElevenLabs, 513 of them single words.

Jan prefers ElevenLabs over the local Gepard, so words stay on ElevenLabs. The options, cheapest first:

1. **Unspoken Slovene context:** `previous_text` / `next_text` around the word. Tested: `eleven_v3`
   rejects them (HTTP 400, "not yet supported").
2. **A carrier sentence, cut:** the word inside a Slovene sentence through `/with-timestamps`, keep only
   the word. Tested on five words; this is the choice (Jan: "sounds good — let's try it").
3. **Designed Slovene narrators** (two voices, a few hundred credits in previews, two slots).
   - It fixes the problem at the root and makes sentences sound native too.
   - But for consistency, all narrator clips must be voiced again: about 45k characters, ~22k credits.
     That fits after a credit reset, spread over a month, or at once on Creator.
   - This is also where the language round leads for Slovene anyway (see above). It stays the long-term
     way, with glas or a Creator upgrade.

**How it works** (the bridge; `companion/README.md`, "Voice"):

- **What gets a carrier sentence:** a short text (one to three words, no sentence punctuation) in a voice
  marked `carrier` in the voice cast (the two narrators).
- **The request:** `Beseda je: kosilo.` (a table per language: `sl`, `it`, `de` and `en`).
- **The cut:** ffmpeg cuts the word's span from its characters' timestamps, from 0.06 s before to 0.12 s
  after, with short fades.
  - The cut uses `atrim` first and then the fades. The test script's output `-ss` put the fades on the
    take's time, and its cuts came out almost silent.
- **Costs:** the quota guard counts the whole carrier sentence.
- **Fallback:** without ffmpeg, or when the word isn't in the timestamps, the word is asked for plainly,
  and this is logged.
- **Tracking:** a clip records how it was made (`method`: `carrier`, or `plain` when the word couldn't be
  cut out and isn't tried again), and how often the app asked for it (`hits`).

**The rollout** of the words from before:

- **Lazy:** when the app asks for an old-style word (a download, `/voice/say`), it gets the old clip at
  once, and the bridge re-voices the word soon.
  - The most asked for go first.
  - At most 40 a day (`LANI_VOICE_REVOICE_DAILY`), about 360 subscription credits; lazy uses 30 of
    them and leaves 10 for re-records on request.
  - Never into the 5k reserve for live lines.
  - The new clip has a new file name, and `voice_updated` makes the app reload its index, so the phone
    downloads the new file the next time it plays the word.
- **The limit of lazy:** the phone doesn't ask again for a clip it already caches, so lazy mostly reaches
  words the phone doesn't have yet.
- **On request (decided):** a long press on 🔊 re-records that clip at once (`POST /voice/redo`): the carrier
  sentence for a short narrator word, a plain re-take for anything else; once a clip a day, in the same daily
  cap, never into the reserve (companion/README.md, "Voice").
- **Manual, for the rest:** `voice-build --revoice-words [--max N]` (a dry run by default).
  - It shows how many narrator words are old-style and what re-voicing them all costs.
  - About 513 words × ~18 characters ≈ 9.5k characters, about 4.7k credits. The plan counts short
    phrases of up to three words too, so its number is higher.
  - Run it after a credit reset, or in batches.

Credits on 2026-09-26: 23,099 of 30,935 used since the reset on the 24th (the pre-building), 5k kept for
live tutor lines.

### Decided: a voice that hisses is filtered, not replaced (2026-09-26)

**The problem.** Stari Janez's designed voice (`@janez`) has a steady hiss. His pauses are at about
−35 dBFS; the other voices are at −45 to −80 dBFS (the `grandpa` library voice at −79). Jan: "his voice is
fine. If we can filter white noise would be enough." So there is no new design and no credits.

**How it works** (the bridge; `companion/README.md`, "Voice"):

- **Per voice:** `denoise` in the voice cast maps a voice to a preset (`"@janez": "hiss"`). Only the
  ElevenLabs clips of those voices are filtered.
- **The filter:** an ffmpeg chain: a 60 Hz high-pass (a rumble below 30 Hz), `afftdn` (12 dB off below a
  fixed −40 dBFS floor), and a gentle gate.
  - `afftdn` delays the sound by 25 ms and keeps the length, so the chain pads and trims to put it back.
  - The lead's first test (`nr=12:nf=-30:tn=1` and a gate at −34 dBFS) looked better than it was: its
    "−64 dB noise floor" was partly the 25 ms of silence at the start.
  - Noise tracking (`tn=1`) missed the hiss in some clips. A fixed floor is the same for every clip.
  - Whisper's word match fell least with `nf=-40` (0.82 → 0.79; with tracking 0.78, with `nf=-35` 0.77).
- **Tracking:** a clip records its preset (`denoise`), so nothing is filtered twice. A filtered clip gets a
  new file name, so the phone downloads it.
- **The rollout:** new clips are filtered as they are stored. The clips from before are filtered by
  `voice-build --denoise --run`, which the lead runs once after a backup. The bridge sees the changed clips
  (SQLite's `data_version`) and sends `voice_updated`.

**For glas:** a clip's settings include its post-filter (the `clips` key in the data model). QA could measure
the noise floor of Voice Design's takes and flag a hissing voice before it is saved.

### Decided: every clip at one loudness (2026-09-27)

**The problem.** Jan: "can it be that the voices of people (e.g. Zala) are much louder than the rest?" Yes.
Integrated loudness (EBU R128), the median of each voice over all 2,826 clips:

- the girl voice (Zala) −10.0 LUFS, its peaks at 0 dBFS;
- grandma −12.8, `@janez` −13.4, the boy and the young man −13.5 to −13.9, `@luka`, `@micka` and the woman
  −14.1 to −14.5, the teacher −15.2;
- the female narrator −17.4 (half of all clips);
- the local Gepard clips −17.1 to −19.0; the male narrator −19.8, grandpa −19.4, gruff −19.2.

Nothing levelled the clips: ElevenLabs' voices differ by 10 dB.

**How it works** (the bridge; `companion/README.md`, "Voice"):

- **The target:** −18 LUFS, the true peak at most −1.5 dBTP. Speech peaks about 15 dB over its loudness
  (p90 18 dB); in a voiced vowel the glottal pulses sit 11–12 dB over its RMS. So the higher the target, the more
  clips the limiter must hold, and a limiter that holds a vowel's pulses compresses the whole vowel.
  - At −16 LUFS (the first idea), 58% of the clips needed the limiter, by 3.4 dB (p90 6.5), and the most limited
    words stayed up to 4 dB short of the target.
  - At −18, a third of the clips, by 1–2 dB (each voice's median). The female narrator hardly changes.
- **The method:** measure with ffmpeg's `ebur128`, then a plain gain (`volume`), and a limiter (`alimiter`) only
  where the true peak would pass the ceiling. `loudnorm` isn't used: its dynamic mode changes short clips.
  - Short words: `ebur128` gates in 400 ms blocks. From 0.4 s on, single words measure like the same voice's
    sentences (the narrator's 0.6–0.9 s words −17.7 LUFS, her 1.5–3 s lines −17.3). A clip under 0.4 s is
    measured looped (none in the store so far).
  - The MP3 encode takes about 0.4 dB off (LAME at a constant bitrate, even on a plain tone; VBR doesn't, but
    the clips stay `mp3_44100_128`). So the result is measured and, when it misses by more than a quarter dB,
    encoded again from the clip as it was. A clip within 0.5 dB of the target is kept as it is.
  - A voice's denoise filter runs in the same encode. Only a carrier cut is encoded twice (the cut, then the level).
  - Kept: the encoding (ElevenLabs 44.1 kHz 128 kbit/s, Gepard 22.05 kHz 64 kbit/s), the length and the timing
    (a clip without a gapless header ends 1–2 ms later).
- **Tracking:** a clip records the target it is at (`level`) and the gain (`gain`), so nothing is levelled
  twice. A new target levels the clips again from their files as they are (one more encode).
- **The rollout:** new clips are levelled as they are stored, family recordings as they are uploaded. The clips
  from before: `voice-build --level --run`, which the lead runs once after a backup (about 2 minutes). On a copy
  of all 2,826 clips, every voice's median came out at −18.0 to −18.2 LUFS (the whole store p10–p90 −18.3 to
  −18.0), the true peak at most −1.5 dBTP.
- **Open:** the phone's TTS, the last fallback, has its own loudness.

**For glas:** a clip's settings include its loudness target (the `clips` key in the data model), and QA could
check a new voice's loudness and peak-to-loudness ratio before it is saved.

## Where the proof of concept runs

- `lani.page` resolves to a LAN address, the local dev cluster, until the move to the cloud. glas runs at
  **`glas.lani.page`**.
- **TLS in dev is self-signed.**
  - cert-manager's `platform-issuer` is a CA issuer that signs from the **mkcert root CA** (the
    `mkcert-root-ca` secret, patched in the dev overlay).
  - The Envoy Gateway serves `glas.lani.page` with a certificate from it.
  - Whatever calls glas must trust that root. The bridge is Bun, so `NODE_EXTRA_CA_CERTS=<mkcert rootCA.pem>`
    in `keys.env`.
  - The app talks only to its bridge (over Tailscale Serve, a real certificate), so it needs nothing. If it
    ever fetches clips from glas directly, a debug build would need the root in its network security config.
- **In the cloud:** the production issuer (ACME with Hetzner DNS-01).
- The bridge host must reach a LAN address: the LAN, or a Tailscale subnet route.

## Shared and private

| Kind of text | Where it goes |
|---|---|
| Curated and published content: packs, modules, scenes, villager lines, festival words | The **shared** library: made once, cached for everyone, and part of the corpus. |
| Personal lines: the tutor's role-play replies, lines with names, anything from a chat | A **private** namespace per node or tenant: cached for that node, never in the shared corpus, and deleted with the tenant. |

The node marks each request as `shared` or `private`; glas defaults to private. GDPR applies to the private
namespace (export and deletion).

## Tech stack

Chosen to match what the hosting platform already runs, and what the bridge is written in:

| Layer | Choice | Why |
|---|---|---|
| Runtime and language | **TypeScript on Bun** | Same as the bridge. The voice code (`voice.ts`, `profiles.ts`, `spec.ts`, the fake ElevenLabs in the smoke harness) moves into a shared package instead of being rewritten. |
| HTTP | **Hono** with `@hono/zod-openapi` | Small and fast on Bun. zod is already the bridge's validation. It generates an OpenAPI spec, which gives typed clients for the bridge (TS) and, if needed, the app (Kotlin). |
| Database | **Postgres on CloudNativePG** (a `Database` CR) with **Drizzle** | The same operator, backups (Barman plugin to the object store) and ORM as the platform's other services. `pg_trgm` for fuzzy phrase lookup; `pgvector` later, for "similar phrases" if wanted. |
| Audio files | **S3**: the in-cluster Ceph RGW (`ha-objectstore`) in production, **Garage** (or MinIO) on the local kind cluster | Content-addressed keys, `sha256(lang, normalized text, voice, model, settings)`. Served by pre-signed URLs, so nodes and apps download directly and glas never streams audio. |
| Jobs | **pg-boss** (a job queue in Postgres) | No new infrastructure. Jobs: generate, design a voice, QA-check, remake, and so on. Retries, priorities, deduplication by clip key, and a rate limit that respects ElevenLabs' concurrency. |
| Workers | The same image with a `worker` role, as a separate Deployment | **Generation:** ElevenLabs TTS `eleven_v3` and Voice Design v3, with a quota guard and a character ledger. **QA:** calls a Whisper endpoint over HTTP. |
| Speech models | **Outside the cluster at first** | The existing `stt-local` (Whisper, AMD GPU) and `voice-local` (Gepard) workers on Jan's machine, over Tailscale. GPU scheduling on kind isn't worth it for a proof of concept; a GPU node pool later (CUDA, e.g. faster-whisper, Piper for more languages). |
| Auth | **Node API keys** (hashed in Postgres; scoped to shared read and write, and a private namespace) for self-hosted bridges. **Zitadel** (the platform's sole IdP) service accounts and OIDC for the hosted service and an admin UI. | Self-hosted nodes need something simple. The hosted service plugs into the existing identity. |
| Limits | A characters-per-day quota per key, a rate limit per key (Redis, as `auth-redis`, or Postgres), and the global reserve (like `LANI_VOICE_RESERVE`) | ElevenLabs is the scarce resource. |
| Ingress | **Envoy Gateway HTTPRoute** under the platform's wildcard cert (e.g. `glas.<domain>`) | Same as the platform's other services. |
| Secrets | **SOPS** (as `.sops.yaml` in the cluster repo) for the ElevenLabs key and the S3 credentials | Never in the image or the repo. |
| Packaging | An OCI image (Bun compiled binary, distroless), pushed to **Harbor**; manifests as a **Kustomize** base and overlays in the style of `clusters/.targets/`, one per domain | Fits `lo up` and the existing targets. |
| Local dev | A new local cluster project (kind, e.g. `glas.dev`) and a **Tiltfile** with hot reload | Fast loop; the same manifests as production. |
| Observability | Prometheus `/metrics`: cache hit rate, characters used against quota, generation latency, the QA scores, job queue depth. A Grafana dashboard. Structured JSON logs. | Cost is the main thing to watch. |
| Tests | `bun test`, with a fake ElevenLabs (moved from the bridge's smoke harness) and a fake Whisper; an integration test against kind (Tilt CI); k6 for load; and one opt-in check against the real ElevenLabs with a tiny character budget | The fakes already exist. The fake is a small local HTTP server that answers like ElevenLabs (TTS, voice design, subscription, errors). Tests then cost no credits, need no key in CI, run offline and give the same result every time, and can make ElevenLabs fail on purpose (quota gone, 429, a timeout, a withdrawn voice), which the real service can't be asked to do. The real service runs in production and in the one opt-in check. |
| CI | GitHub Actions: test, build, push to Harbor, Renovate | |

## Data model (first cut)

- `languages`: code, name, ElevenLabs `language_code`, whether local TTS has it.
- `voices`: provider (elevenlabs / gepard / piper), provider id, kind (premade, library, designed), owner and
  notice period for library voices, language(s), gender and age, archetype (grandma, boy, narrator…), design
  description and sample, QA score, status (active, withdrawn, retired).
- `characters`: a persona (name, culture pack, description) with a voice per language. Archetype characters
  are shared; a world's own villagers (plan 2) belong to a tenant.
- `texts`: language, normalized text, hash, first seen, request count, source kinds (pack, module, scene,
  villager…), CEFR level if known. **The corpus.**
- `clips`: text × voice × model × settings → object key, duration, characters billed, engine, QA score,
  created at, and a namespace (shared or private: node or tenant).
- `jobs` (pg-boss), `usage` (a ledger per key per day), `keys` (hashed, scopes, quotas).

## API (first cut)

| Method | Path | Does |
|---|---|---|
| `POST` | `/v1/clips/resolve` | A batch of `{lang, text, voice, namespace}`. For each: a signed URL if the clip exists, else `pending` with a job id. Idempotent. |
| `POST` | `/v1/clips/say` | One line, now: wait up to N seconds for it (the app's 8 s), else `pending`. Only for keys with the `generate` scope (our own product's content), never for self-hosters' arbitrary text. |
| `GET` | `/v1/voices`, `/v1/characters`, `/v1/languages` | The library. |
| `POST` | `/v1/characters/{id}/design` | Design a voice for a character in a language (Voice Design v3 from a sample; the best of three takes by QA). |
| `GET` | `/v1/corpus?lang=sl&level=A1` | Words and phrases, with clip availability. |
| `GET` | `/v1/usage` | The key's usage and quota. |
| `GET` | `/healthz`, `/metrics` | |

## How the bridge changes

- `VoiceStore` gets a glas backend. It asks glas first and keeps its local cache (SQLite plus files) as it
  does now. With glas unreachable, it falls back to local engines (Gepard), then the phone, as today.
- `voice-cast.json` and the profiles move to glas characters. A node keeps only the mapping of its people to
  glas characters.
- The prebuild (`bin/voice-build`) becomes `POST /v1/clips/resolve` for the content's texts. glas decides
  what's new.
- The app doesn't change at first: it keeps talking to its bridge. Later it could fetch signed URLs directly.

## Phases

1. **Proof of concept on the local cluster.**
   - The repo, the local cluster project, Tilt, Postgres (CNPG), Garage.
   - The generation worker with our ElevenLabs key; `resolve` and `say`.
   - Jan's bridge switched to glas behind a flag.
   - Import the existing `voice.db` clips as the first shared library.
2. **Characters and design:** the archetypes per language (Slovene now, Italian for the second learner's town), the
   designed voices, QA by Whisper over Tailscale.
3. **The corpus and remakes:** request counts, levels, the withdrawn-voice flag and remakes, the private
   namespace with deletion.
4. **On the pilot cluster** (a separate domain): Ceph RGW, Zitadel service accounts, node
   keys for self-hosters, quotas. Then the hosted Lani (plan 1, Variant 2) uses it too.

## Open questions

- **When to move to Creator for a year:** at the start of phase 2, when a second language's content is
  pre-generated. See [Voices and slots](#voices-and-slots).
- **Live tutor lines in the hosted variant:** under the OEM terms, or only pre-generated? See
  [What ElevenLabs allows](#what-elevenlabs-allows-checked-2026-09-25).
- **The cloud domain** after `lani.page`.
