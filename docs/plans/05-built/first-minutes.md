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

## Tap counts and seconds

QA's step `first-run`, on the emulator, a busy machine, from the first screen to the village's map on this device, the defaults
kept. A tap counts a field's tap (typing the name) and the system's notification question. The seconds are QA's, with no
reading or listening: every look at the screen costs a second or two of uiautomator, the same before and after, so compare
them with each other, not with a person. After: three runs each, so ranges. Before: one run each, on an APK from an ancestor
of this branch (talks-de, the same setup code).

| Journey | Before | After |
|---|---|---|
| An adult, a phone, sl · en: taps to the map | 12 | 6 |
| Seconds to the founding | 71.4 | 26 to 27 |
| Seconds to the first line said aloud | 97.6 | 34 to 42 |
| Seconds to the map | 114.0 | 46 to 52 |
| A child, a tablet on its side, it · de: taps to the map | 13 | 7 |
| Seconds to the founding | 74.9 | 30 to 35 |
| Seconds to the first line said aloud | 95.3 | 42 to 48 |
| Seconds to the map | 110.0 | 52 to 59 |
| Questions on screen before the village | 9 | 2 (language, name) and "For a child" |

The taps after: the language, the name, "Found my village", "My village will be here", the greeting, "Light the fire"; a child's
adds "For a child". The founding's own choices (a landscape, another place) stay, optional.

Other ways, from the first screen:

| Journey | Before | After |
|---|---|---|
| The family's computer, QR code | 5 (language, Next, the way, Scan, "To my village") | 3 (the link, the way, Scan) |
| The family's computer, typed | 8 | 6 (the link, the way, "Ročno", the address, the token, Connect) |
| A new family member, from a set-up device | 7 or more, and a key typed by hand | 5 on the new device (the link, the way, the name, Found, here) and a hold on the parent's, before the first word |

## Tests and QA

- `app/FirstRunTest`: an existing learner sees none of it, on nothing kept; the moment once; the aim and the minutes wait for a
  session and come once, skipped or answered; a child and a family's computer aren't asked them; the voice waits for the
  dialog's end; the tutor's ways once, on this device, with none set up; the school's card stays dismissed; when the
  notification question comes; it survives the app; the first words in all 12 pairs.
- `app/SetupFlowTest`: two taps to the founding with the defaults, the other ways in, Back on every step, what the family shared.
  Pairing and an old node's failures as before, on the new path. `data/DeepLinkTest`: an invitation, unchanged.
- `game/SetupSpeechTest`: every step in all 12 pairs.
- `data/FamilyLinkTest`: the QR holds no key and only its known fields; only the claiming device can read the gift; the exchange
  is bound to the code; a used code, an expired code and a wrong proof get nothing; nothing goes without the parent; a changed
  byte isn't opened; `Gift` and `Redact` keep the key and the code out of a log; end to end over real sockets, with a yes and a no.
- The bridge's smoke: `POST /learner/about` in `test/smoke/aim.ts`.
- `companion/bin/qa`:
  - `first-run` (alone): the taps and seconds above, with screenshots; `setup-tour` (alone): the child on the tablet, on its side
    and upright, what each step said.
  - `first-cards`: the voice's card, the pace card, the school's card, each once.
  - `hosted` (alone; `LANI_QA_HOSTED_CHILD=1`): the first run, the tutor's ways once, the pace card kept on the phone's bridge.
  - `family-parent` and `family-child`, on the two emulators at once.

## Voice

Nothing new needs a clip to work: on this device the phone's voice says everything. The first moment's place line is the intro's
own (`gameIntro.arrive`, or the culture's), so it keeps its clips. New lines a node would voice: the greeting and the other
answer in four languages and `firstRun.won` in four, about 150 characters together. Not built: Jan's credits.

## What's left

- **A native check** of the strings at the top.
- **The family computer's pairing and the town link in "Add a family member".** Each family member on a node is a learner with a
  bridge of their own (`lani-profile add`), and a running bridge can't make one: the parent's bridge could only mint a code for its
  own learner, which is the opposite of a new member. It needs a way to add a profile from the app. Until then the page says
  so on a paired device, and a family on a computer uses `lani-profile add` and `lani-pair`. Without a relay a device alone has no
  town to link to.
- **The list of the family's devices with a remove.** It needs a channel between the devices after the exchange, and a family on
  "just this device" has none until the relay is hosted. The page says what's true now: only changing the key at the provider
  truly stops a copy. The daily budget is there already: each device has its own "Dnevna omejitev porabe" (`$1.00` by default,
  behind the parent's hold on a child's device); the parent can't set it from their own device yet.
- **The relay as a second way** for two devices not on the same Wi-Fi: `FamilyLink.Listener` and `fetch` are the only parts that
  know about sockets, and what they carry is already sealed.
- **A key can't be checked offline:** a new device without the internet gets "the family's key doesn't work here", and the code is
  spent. A parent shows a new one.
- **The audio trial**, at `AudioPlan`; the family key's audio plan with it.
- A Gemini key doesn't cross.
