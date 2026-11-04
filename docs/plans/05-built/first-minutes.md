# Built: the first minutes (first-minutes)

*Built 2026-11-04, branch `first-minutes`. Jan: "easy -> comfortable; not overloading; audio free trial; signup afterwards". A
new learner met about nine questions before seeing anything. Now they meet the village and hear it within a minute, and
are asked things only when they matter, once. Existing learners (Jan's, Teo's) see none of it: no migration, no re-onboarding.*

## Native check (first)

Every new string is machine-written by Claude (`_note_first_minutes` in the four tables). For a native speaker:

- **sl** (Jan's wife):
  - `firstRun.sayBack` "Odzdravi!": clear to a beginner, or "Pozdravi nazaj!"?
  - `firstRun.who` "Nekdo ti od ognja pomaha:", `firstRun.won` "Tvoj prvi pozdrav v vasi!"
  - `firstRun.paceTitle` "Lep začetek! Ti ta tempo ustreza?"
  - `firstRun.tutorWhat`, `firstRun.cost*` ("naročniški paket", "krediti OpenRouter", "po sporočilu")
  - `setup.otherLink` "Imam kodo ali datoteko", `setup.otherTitle` "Že imaš kaj pripravljeno?"
- **it** (Teo's Italian): `firstRun.sayBack` "Rispondi al saluto!", `firstRun.who`, `firstRun.tutorWhat`,
  `setup.otherTitle` "Già configurato altrove?".
- **de** (Teo's base): `firstRun.sayBack` "Grüß zurück!", `firstRun.paceTitle` "Schöner Anfang! Passt dir dieses Tempo?",
  `setup.otherTitle` "Schon woanders eingerichtet?".
- **en**: British English, as elsewhere.
- The greetings themselves, each language's own (`firstRun.greeting`, `firstRun.other`): "Dober dan! / Lahko noč!",
  "Buongiorno! / Buonanotte!", "Guten Tag! / Gute Nacht!", "Hello! / Good night!".

- **family.\***, all four languages (`_note_family`): "Dodaj člana družine", "Drži za deljenje (starš)", `family.whoPays`,
  `family.stopping`, `family.chatgptNot`. In Slovene `family.scanWhat` says "Na njegovi napravi", which has a gender; it may
  want "Na drugi napravi".

## What

### The first run

| Step | Before | Now |
|---|---|---|
| The language to learn | the first screen | the first screen |
| The explanations' language | its own screen | three small chips on the name's screen, the phone's marked (`SetupFlow.base`) |
| How Lani runs | its own screen | gone: this device is the main path; the computer and an import are behind a small link |
| Name, for a child, address, level, placement, aim, minutes | one long screen | the name and "🧒 Za otroka" only |
| Voice and tutor | their own screen | gone: asked later |
| The system's notification question | over the founding | after the question that follows the first session |
| The founding | the picture under the fold | the picture first, the way on on the screen of a phone |
| After the founding | Home; the three steps of the intro only on "Moja vas" | the village's place said aloud, a greeting, a first word (`ui/game/FirstMoment`), the map |

The defaults (`SetupFlow.answers`): level A1, aim B2, 15 minutes a day (it was 30; 15 for a child as before; "easy to
keep"), no gender said, the cast's voice. A family's computer asked those in `lani-setup`, so "Še o tebi" is gone too:
paired, the learner goes Home.

**The first moment** (`game/FirstWords`, `ui/game/FirstMoment.kt`): the place said aloud (the culture's own words, the
intro's existing line, so its clips still fit), "Someone by the fire waves to you", the greeting said aloud with what it
means, two answers. The right tap says it back and lights the fire: "Your first greeting in the village!", 5 🌾, "🔥 Prižgi
ogenj", the map. A wrong tap hears the greeting again. "Skip" and Back leave it at once. It replaces the three steps of
`GameIntro` for a learner who came through a first run; `GameIntro` stays for everyone else.

### Asked when it matters, once (`app/FirstRun`)

`FirstRun` is kept in the device's preferences. It is set only by a first run (`startOnDevice`; on a family's computer by
founding a village on a device that never saw the intro). A device with nothing kept is not `fresh`, so Jan's and Teo's
see none of this, with no migration.

| Question | When | Where | Who |
|---|---|---|---|
| "🧭 Nekaj že znaš?" | the first visit to the school | the card it had, with "Pozneje", which puts it away for good; the quiet row stays | anyone who hasn't begun |
| Minutes and aim | the first time Home knows of a finished session (`practisedToday`, or a streak) | `ui/home/PaceCard`: "✓ V redu" keeps what's marked, "Preskoči" leaves them; never again either way | an adult on this device; a child's are a parent's, a computer asked them already |
| "Kateri glas je tvoj?" | when the first dialog closes, no more over its first line | the sheet it had (`MyVoiceOffer`), with how the village speaks to them while the profile doesn't say | a new learner |
| The tutor | the first time the tutor opens with none set up | `TutorOffer` over the chat: ChatGPT, OpenRouter, a key, Later, each with its cost in a line; a child's behind the hold; the chat stays open | on this device |
| Notifications | after those | the system's dialog, as before | a new learner |

How the village speaks to them went onto the voice's card rather than nowhere: until it's said the content speaks to them as to
a man (bridge `addressee.ts`), which matters for a woman learning Slovene or Italian. It is the first moment they hear it.

**The settings:** "🙂 O meni · About me" (`ui/settings/AboutSettings`, `GET`/`POST /learner/about`) changes the address, the
minutes and the level; "🎯 Cilj" the aim, as before; "🔊 Zvok in glas" the voice. A level the aim isn't above any more takes
the aim one level up. A child's page is behind the hold.

### Kept

A child's safety and the parent's holds; every step reads itself aloud, offline by the phone's voice; the four languages; an old
node's pairing (`lani://pair`, the typed address, every failure's plain words); "an invitation link opens straight": it waits
in `pendingLink` as before and opens once the learner's data is made.

### A family member from a phone that's set up

A device that is set up has "👪 Dodaj člana družine · Add a family member" in the settings (never a child's). A new device has
"Someone in my family already uses Lani" behind the first screen's small link. It scans the code, a parent confirms on the
first device, and the new device goes straight to the name.

**Both devices must be on the same Wi-Fi.** The relay isn't hosted yet (`lani.relayUrl` is empty), so a family on "just this
device" has nowhere else to meet. No node and no internet are needed for the transfer; the new device needs the internet once
to check the key.

| Comes across | Doesn't |
|---|---|
| The tutor's provider, its key and preset (Claude, OpenRouter, an OpenAI API key) | A ChatGPT sign-in: it's one person's; the new device shows "Continue with ChatGPT", and both pages say so |
| The languages, as a suggestion | Learning progress, names, notebooks |
| | A Gemini key (not in the brief's list; the provider's terms aren't checked) |

The security rules, as built (`data/FamilyLink.kt`):

- **The QR holds no key:** host, port, a 128-bit one-time code, the sender's public key for this exchange, the expiry. A test pins
  the field list.
- **End to end:** each exchange has new P-256 keys on both sides, kept in memory. The session key is HKDF-SHA256 over the ECDH
  secret, salted with the code, and the gift is AES-256-GCM. There is no relay and nothing stored in between.
- **Single use, short expiry:** the first device with a good proof (HMAC of its public key under the code) claims the code, every
  other gets nothing, and a wrong proof doesn't spend it. Three minutes, then the listener closes. A photo of the QR is useless
  after that, and before it a thief would still need the parent's hold, on a screen that names their device.
- **A parent confirms:** the page shows the device's name and what it will get; only the two-second hold sends it. The key is
  read from the vault at that moment.
- **The new device:** the key goes through the same check as a typed key into its own Keystore vault (`receiveFamily`). No screen
  shows a key, on any device; a child's tutor page is behind the hold as well.
- **Never in a log, an export, a backup or a report:** `Gift.toString` hides the key, `Redact` takes out keys and now family
  codes, the vault's DataStore isn't exported and `allowBackup` is false. QA's log line of the code is in debug builds only.

Whose bill: the sending page says the key stays theirs, that every message goes on their bill, and that it's for their own
family and not for resale (Anthropic's terms allow a key for the owner's own authorised users).

## How

- `app/SetupFlow` (steps `WELCOME`, `NAME`, `OTHER`, `FAMILY`, `COMPUTER`, `MANUAL`, `DEVICE`; `answers`), `ui/setup/SetupScreen`
  (`Name`, `Other`, `Family`), `game/SetupSpeech` (`who`, `other`, `family`). `ui/hosted/HostedSetup` stays for an import, a learner
  already on the device and the settings' switch from a node.
- `app/FirstRun` (pure) and `FirstRunController`; `AppViewModel`: `startOnDevice`, `found`, `firstMomentDone`, `settlePace`,
  `aboutNow`, `keepAbout`, `scanFamily`.
- `MainActivity`: the founding at once on a first run, the notification question held, the voice's card after a dialog, the tutor's
  ways. `app/MyVoiceController`: `holdOffer`, `waiting`, `offerNow`.
- `ui/hosted/TutorStep`: on the blue or on a sheet (`onLight`), the ways in the new order.
- The bridge: `GET`/`POST /learner/about` (`features/level.ts`). An older node answers 404: the page says so.
- `data/FamilyLink`, `app/FamilyShareController`, `ui/settings/FamilyShare`, `LiveTutor.familyShare`, `receiveFamily`.

### What this leaves for the audio trial

`data/Clips.kt` `AudioPlan.voices`: the one place that says who makes the villagers' clips. The trial (all voices, no account,
no card) and the paid add-on answer the same routes, so they go there as another `BridgeConfig`; when they end it gives the
connection back, the clips on the device stay and lines fall back to Piper and the phone's voices.

