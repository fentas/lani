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
- A name is untrusted text: at most 32 characters and 3 words, letters and marks, an apostrophe or a hyphen inside a word.
- A piece, and a name clip of kind `arch`, `ending` or `rise`, come out of a take cut by its timestamps, which a bridge does: it adds
  them with a `publish` key (`Glas.addLine`, `Glas.addNameClip`). A phrase is a plain line, so the service voices it, but only a phrase of
  a manifest we added, with only a valid name in it.
- On the app's routes a line with a name in it is matched against the manifests. `say` answers `/voice/line/<id>.mp3` and `<id>.json` is
  the manifest the phone plays.

## One place for a paid plan

Nothing charges anyone. In the app it is `AudioPlan`, and the service has one place too: the add-on is a key with the `app` scope, and what a
payment would decide is that key's monthly cap, nothing else. No card, name or invoice would be kept there.
