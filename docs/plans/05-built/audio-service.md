# Built: the audio service (`audio-service`)

*Built 2026-11-05. Jan: people pay for the app's development and the audio service; we never resell AI. A new learner hears every
voice with no account and no card, then Piper and the phone's voices with nothing lost. Nothing here charges anyone, and nothing
spends by itself: everything that would make a new paid clip is off until someone sets it.*

## What

| Part | Where | What |
|---|---|---|
| The bridge's backend | `companion/bridge/src/glas.ts` | `VoiceStore` asks the voice service first, keeps its cache, falls back to local engines and the phone. |
| The app's routes | the service: `/v1/app/<lang>/voice/…` | A bridge's answers to an app with no computer behind it. |
| The trial | the service: `/v1/trial`; the app: `AudioPlan` | Every voice for a while, then the phone's. |
| The add-on's limits | the service | A monthly cap per key on top of the daily quota, with the same fallback. |
| The name library | the service: `/v1/names*`, `/v1/lines*` | [audio-names](audio-names.md)'s pieces and name clips, for everyone. |
| What people read | `docs/privacy.md`, the app's texts | The name library's rule, in plain words. |

## Settings that are off

| Setting | Where | Off means | Turned on, at most |
|---|---|---|---|
| `GLAS_URL`, `GLAS_KEY` | a node's `keys.env` | The store runs as it did. | With a key that may only read: nothing. With `generate`: the key's daily quota and monthly cap. |
| `lani.glasUrl` | the app's build | No trial, no request, no card, no row. | Nothing by itself. |
| `GLAS_TRIAL_STARTS_DAILY` | the service | No trial is offered. | Nothing by itself: new installs read the library. |
| `GLAS_TRIAL_DAILY_CHARS` | the service | Trials hear what the library has. | That many characters a day, for all trials together. |

## The bridge

With both settings there, `nodeVoices` puts the engine first. A live line asks `say`, anything else `resolve`. The node never marks a
line: a line of our own content goes to the library whoever asks, any other to the key's private namespace, so a line with a learner's
name in it can't reach the library from a node.

- Someone's own voice is the node's and is never sent. A speaker the service has in another voice than this cast isn't used.
- A lone word asks only for a clip that exists (`make: false`): a carrier sentence is the node's.
- A failure is a null and the next engine has its turn. Out of reach it is left alone for a minute, a used-up quota until the next
  UTC day or month, so no line waits on a timeout.
- A clip is engine `glas`: ranked with ElevenLabs, standing wherever ElevenLabs is asked for, no characters of the node's. Nothing
  re-voices it by itself, since the lazy upgrades look for ElevenLabs clips. One still raw goes through its voice's filter and pace
  here; one imported from a node only through the level.
- An older app sees no difference: it asks its bridge, as Teo's 0.2.698 does.

`voice-build --glas-corpus [--run] --data <an empty learner>` sends our own content's lines to the corpus, which is what may be made on
the app's routes. It voices nothing and spends nothing. It refuses a learner with content of their own, and leaves out every line that
said a stand-in name. Run it once per culture: Primorska is 27,560 lines.

## The app's routes and the trial

An app on this device has no voices of its own. `AudioPlan.voices` gives it the service's routes while a trial lasts: they answer as a
bridge does, so nothing else in the app changes. Anyone can make up a trial's token, so what may be asked is any text. The rule:

**The service serves its library, and makes a new clip only of a line loaded as our own content.** Anything else is a 204 and the
phone says it. A lone word isn't made even so.

What bounds a trial:

| Bound | Setting | Default |
|---|---|---|
| New trials a day, all installs together | `GLAS_TRIAL_STARTS_DAILY` | 0 |
| New characters a day, all trials together | `GLAS_TRIAL_DAILY_CHARS` | 0 |
| How long | `GLAS_TRIAL_DAYS` | 14 |
| New characters per install, in all | `GLAS_TRIAL_INSTALL_CHARS` | 3,000 |
| Clips an install may fetch a day | `GLAS_TRIAL_INSTALL_CLIPS_DAILY` | 3,000 |

An install is a random number the app made. The service keeps its hash and nothing else about the device, and the row goes 90 days after
the end. A reinstall is a new install: the two daily bounds are what stop it. Past any bound a line is a 204; a trial that can't start
answers `closed`, and the app asks again the next day and says nothing.

## The name library

To [audio-names](audio-names.md)'s contract, which owns the shapes. A name clip's whole key is (language, voice, name, kind). No
account, node, key or device on a row or in a log line, and the name is never logged.

- `voice` is a speaker of the cast. A row keeps the voice it is in, so a speaker moved to another voice finds nothing and is made again.
- A name is untrusted text, and the service applies the bridge's rule to the letter (`nameOk`): a name passes both or neither.
- A piece and a name clip come out of a take cut by its timestamps, which a bridge does: it adds them with a `publish` key
  (`Glas.addLine`, `Glas.addNameClip`). The one thing the service voices itself is a phrase that ends its line ("Lahko noč, {n}!"), which
  needs no cut: only a phrase of a manifest we added, with only a valid name in it.
- On the app's routes a line with a name in it is matched against the manifests. `say` answers `/voice/line/<id>.mp3` and `<id>.json` is
  the manifest the phone plays.

## The app

- **The setting:** `lani.glasUrl` / `LANI_GLAS_URL` in the build, empty until the service is hosted. Empty means no request, no id, no
  card and no row. A learner on a node never sees any of it: it is for "Lani on this device" only.
- **The trial's state:** `data/AudioTrial.kt`, in the device's preferences, so never in an export, a backup or the git sync.
  `app/AudioTrialController` starts it in the background as soon as the learner is on this device, so the first moment's voices are
  already the village's. A failure says nothing and tries again. It asks at most once a day, and nothing after the end.
- **`data/AudioPlan.kt`** is read at every request. Index, say, prepare and download go through it; profiles, a re-record, a visit and
  the offline voice stay on the connection. A line asked as "player" is asked in the voice the learner's lines are in, since the service
  doesn't know "player". A 403 `trial_ended` ends the trial at once.
- **Nothing lost at the end:** the phone's bridge answers an empty index, so `PreviousClips.heard` keeps the entries whose files are on
  the device, and they go on playing. A new line goes to the phone's voice by the route it always had.
- **What the learner reads:** `ui/home/AudioTrialCard` once at the start, once after the end, never during the first moment, one card
  at a time with the pace card. No reminder, countdown or badge in between. `ui/settings/AudioTrialLine` gives the state and the name
  library's rule under "🔊 Zvok in glas". A child's card leaves out the sentence about accounts and cards.

| Journey | Taps before | Taps after |
|---|---|---|
| The first run | as it was | as it was |
| The trial's start card | none | 1, once |
| The trial's end card | none | 1, once |

## One place for a paid plan

Nothing charges anyone. In the app it is `AudioPlan`, and the service has one place too: the add-on is a key with the `app` scope, and what a
payment would decide is that key's monthly cap, nothing else. No card, name or invoice would be kept there.

## Tests and QA

- The service: 137 tests, 2 opt-in skipped. The bridge: `tsc`, the smoke with a section of 28 checks on a fake service, and one run of the
  engine against the service's real routes on an in-process database with its fakes.
- The app: the full suite, 64 new tests.
- `companion/bin/qa --steps audio-trial` against the service's dev server, in Jan's pair (adult, sl · en) and Teo's (child, it · de), each
  card and the settings on a phone (1080×2400) and a tablet (2560×1600, 1600×2560): nothing asked before the learner is on this device,
  the trial there before the founding's button, the first moment's greeting played as a clip, each card once and gone after a force-stop,
  the 403 ending the trial, and with the server stopped a clip heard before playing from the phone and a new line going to the phone's
  voice.

**What no device has shown:** a natural end by date (the end came through the dev server and the 403), `closed`, a start offline, the
daily read, leaving this device for a node, the prefetch and the car's library on a trial, a "player" line, any voice but `female`, a
minified build. Nobody has listened: the evidence is logs and files.

## Native check

Every string is machine-written by Claude (`_note_audio_trial` in the four tables): `audioTrial.title`, `startTitle`, `startText`,
`startTextChild`, `endTitle`, `endText`, `until`, `over`, `names`. Worth a native's eye:

- **sl:** "Brez registracije in brez plačilne kartice", "Že slišani stavki obdržijo svoje glasove", "Ime, ki je enkrat posneto".
- **it:** "Fino al giorno {date}" (it avoids al/all' before 1, 8 and 11), "biblioteca comune".
- **de:** "Ohne Konto und ohne Karte", "Bis zum {date}".
- **en:** British English, as elsewhere.

## Voice

Nothing new needs a clip. The cards aren't voiced.

## What's left

- **Piper on this device.** After a trial the phone's own voice speaks, and Piper only if it is there already: a device with no node has
  nowhere to get it from (`OfflineAudioController.onPhone`, and the service has no `/voice/offline`). Who serves the models is a decision.
- **New clips on the app's routes are as the model made them:** the service has no level, filter or pace yet, so with a budget set a new
  line may sit at another loudness than the rest. A node's copy is processed as it is stored.
- **The name library and the voice store:** `Glas.nameClips`, `lines`, `addNameClip` and `addLine` wait for [audio-names](audio-names.md)'s
  store to call them. Without that a node with the voice service makes its names itself and shares none.
- **A joined line's finished file** on the app's routes: the service answers 404, so a join that fails on the phone goes to the phone's
  voice. An older app never asks there.
- **Live lines for the add-on:** the app's routes make only our own content. Plan 3's open question stays open.
