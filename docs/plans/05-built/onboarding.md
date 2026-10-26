# Built: the first launch, and the village's first words read aloud

*2026-10-26, branch `onboarding`. Jan set up a tablet for their son Teo (learning Italian from German, the Friuli
culture) and found the first screen "quite dev mode": a masculine "Dobrodošel!" before anyone said who they are, a raw
"Bridge URL" and "Token", and a paragraph about `tailscale serve status` and `data/app/bridge-token`. The introduction
was also silent. This round redoes the first launch for a parent and a child, and gives the introduction a voice.*

## The first launch

The state is `app/SetupFlow` (pure; the view model keeps it, so the screens built anew in the chosen languages keep
their step). The screens are `ui/setup/SetupScreen.kt`. The look stays the app's: the blue of the first screens, white
text, big buttons.

1. **Welcome.** A little of the village (the founding's own drawing, `LandPreview`: the first age's fire and huts on the
   land of the chosen language's region), then a greeting without a gender: "Dobrodošli!" in the phone's language big,
   the others under it ("Dobrodošli! · Benvenuti! · Willkommen!"). One sentence of what Lani is, in the phone's
   language: "Lani is a little village that grows with every word you learn." Then "Which language do you want to
   learn?": four tiles, each with the language's own name, its name in the phone's language and a "Ciao!" in it.
2. **Languages.** The language explanations are in, by default the phone's (`SetupFlow.start`: the phone's locale when
   it is one of sl, en, it, de, else English). It is never the one learned: a German phone learning German is explained
   in English. The welcome now reads in the pair, the language learned big and the base under it ("Benvenuti!",
   "Willkommen!"), and so does every label from here on. `vm.setupLanguages` sets the pair as the learner profile's
   (`LangSetting.profileSays`); a node's profile, or the one the hosted onboarding makes, then says its own.
3. **"Kako želiš uporabljati Lani? · How do you want to use Lani?"** Two cards, each with a line or two for a parent
   or a child:
   - **"📱 Samo ta telefon / tablica · Just this device":** everything stays on this device, no computer; the device
     reads the words aloud itself; a tutor with your own key, now or later. This is the hosted mode
     ([hosted-mode.md](hosted-mode.md)): with no learner on the device the questions come at once, the languages
     already chosen ("Učiš se: italiano; razlage: Deutsch", "Spremeni" goes back). Then the tutor's step.
   - **"🖥️ S svojim računalnikom · With my family's computer":** a computer at home runs the village: the villagers'
     real voices, a tutor who writes to you, a town with friends and a family page. The computer shows a code, you
     scan it.
4. **The family's computer.** One big button, "📷 Skeniraj kodo · Scan the code", and one line: run `lani-pair` on the
   computer; its QR code lasts 10 minutes. The scanner is the one the app had (`ui/PairScan.kt`, CameraX and ZXing).
   Under it, small: "⌨️ Ročno · Enter manually".
5. **Ročno · Enter manually.** One line says where the values come from. The address field also takes the text
   `lani-pair` prints under the QR code (`lani://pair?…`): pasted, it pairs as the code does and no token is asked.
   Otherwise, the computer's address and its token, as before.

No main text says "bridge", "tailscale" or "token". The token field is behind "Ročno", and Tailscale is named only
where the computer can't be reached.

The settings' "☁️ Preklopi na ta telefon · Switch to this phone" still opens the hosted steps straight away, with the
languages as chips. "🖥️ Na svoj računalnik · To my own computer" opens the first launch at the computer's code, in the
learner's languages.

### What goes wrong, in plain words

A failure is a `PairTrouble`: the line, and the way out the screen offers (`PairFailure.Out`). The pairing's failures
are typed now (`PairFailure`, `data/Pairing.kt`), so the screen knows which button to show. The same card shows in the
scanner and under "Ročno" (`TroubleCard`).

| What | The line (en) | The way out |
|---|---|---|
| No answer (the computer is off, or Tailscale is off here) | "📡 Can't reach your computer: is it on, and is Tailscale on here?" | "🔄 Try again" (the same code again: it may still be good) and "🛡️ Open Tailscale" (the app, else its store page) |
| An old code (HTTP 410) | "⌛ This code is old (codes last 10 minutes). Show a new one on the computer." | "📷 Scan a new code" |
| A spent or wrong code (403) | "❌ This code was used already, or it's wrong. Show a new one on the computer." | "📷 Scan a new code" |
| A code for another learner (the device holds someone else's village) | "🔀 This device is Teo's, and this code is for another learner. To switch learners, clear the app's data first (…)." | "📷 Scan a new code" |
| Another computer answered than the code names | "🔒 A different computer answered than the code names. Not paired." | "📷 Scan a new code" |
| The computer's Lani is too old for codes (401, 404, 405) | "🧰 Lani on the computer is too old to pair by code. Update it, or enter the address manually." | "⌨️ Enter manually" |
| Too many wrong codes (429) | "⏳ Too many wrong tries. Wait a few minutes." | "🔄 Try again" |
| Typed: the token refused (401, 403) | "🔑 The computer doesn't know this token. Check it." | the field |
| Typed: something else answers (404, 405, not the bridge's JSON) | "🤷 Lani doesn't answer at this address. Check it." | the field |
| Typed: not an address | "🔗 The address isn't valid: it starts with https:// and has the computer's name." | the field (checked before anything is asked) |
| The tutor's key refused | "🔑 This key doesn't work. Check that it's copied whole: it starts with sk-ant-." | the field |

### A child's device

The bridge already says at pairing whether the profile is a child's (`learner.child`, `lani-profile add --child`). The
app now acts on it (`vm.child`). Neither the setup nor the app asks a child for keys or accounts:

- **The connection sheet:** the data (git's GitHub sign-in, the backup) and the tutor's settings (a key, "Continue with
  ChatGPT") are behind "👪 To nastavi starš · A parent sets this up". They open after "🔒 Drži za starše · Hold (for
  parents)" is held for two seconds (`app/ParentGate`, `ui/setup/ParentGated`). The button fills while it's held and
  empties when let go early, and a tap doesn't open it.
- **Just this device:** the questions ask "🧑 Zame · For me" or "🧒 Za otroka · For a child". A child's device starts
  at 15 minutes a day, keeps a marker in `<filesDir>/lani/child`, and boots its bridge with `LANI_CHILD=1`: the bridge's
  profile is a child's as on a node (the tutor's child paragraph, no tool approvals relayed). Its tutor step is behind
  the same hold, with "🏡 V mojo vas · To my village" under it while it's closed.

## The introduction's voice

**Which introduction was silent:** the village's first words (`ui/game/GameIntro.kt`): "Il mio villaggio / Arrivi in
una valle…", "Imparare costruisce il villaggio", "Costruisci, difendi, cresci", shown the first time the village opens.
It had no speech code at all, for any learner. Its three steps only drew text: the language learned big, the base
under it. Nothing in it was Slovene-only. The voice chain the rest of the app uses speaks Italian, because the node
voices a line in its village's language (the culture's cast: Friuli's narrator in Italian through ElevenLabs). The
intro was simply never wired to it.

What else a new learner meets (traced for it · de):

- The setup and the founding ("Dove sarà il tuo villaggio?") are silent by design.
- Nonna's arrival and a villager's request before a run speak.
- The school's guided introductions never come for Italian: there is no `companion/course/it`, so no classroom.

**The fix:**

- `game/GameIntroSpeech` gives each step's pieces as the screen shows them: the language learned (`HintPiece.target`),
  then the base.
- `GameIntro` reads each step as it appears through `HintVoice`, the hint's reader:
  - the language learned in the narrator's voice: the node's clip, voiced on demand in the village's language; else
    the other narrator's clip on the device; else the phone's text-to-speech in the language learned (it-IT);
  - the base by the phone's text-to-speech in the base (de-DE).
- The node is asked for all five lines as the intro opens (`Speaker.preload`), so the next steps find theirs on the
  device.
- A 🔊 by Skip reads the step again, or stops it. Skip, "Prižgi ogenj" and leaving stop it.
- It reads by itself when the school's "🔊 Beri razlago · Read the explanations" is on and no screen reader is.
- `HintVoice.play(…, needsBase = false)`: the intro plays its language learned even when the device has no voice for
  the base. A hint still needs its explanation, as before (`HintVoice.playable`).
- Each piece's source is logged: tag HintVoice, `1/3 it node "Il mio villaggio"`, `3/3 de tts:de "Du kommst …"`, or
  `none`.

Where no content exists (the hosted mode: `/voice/say` answers 204), the phone's voice for the language reads it. A
device without that voice reads what it can, and the text stays.

## Tests and QA

- `app/SetupFlowTest`: the welcome in the phone's language, the greeting without a gender in every language, the
  explanations never in the language learned, the pair's labels (it · de, and Jan's sl · en unchanged), the two ways
  and back, manual entry (address and token, or a pasted code), an address checked before anything is asked, the typed
  connection's failures in plain words, a failure's way out, every failure in both languages without "bridge", the
  parent's gate (a tap and a short press don't open it, a 2 s hold does), a refused key, the hosted answers' child.
- `game/GameIntroSpeechTest`:
  - it · de and sl · en pieces, each from its language's table;
  - every step says something in all 12 pairs;
  - the five lines the node voices ahead;
  - each language's locale;
  - the intro plays its Italian with no German voice, while a hint still needs its base.
- `data/PairingTest`: the typed failures and their ways out.
- `companion/bin/qa`:
  - `setup`: the welcome, the languages, the two ways, the computer's code (the scanner opened once), "Ročno", an
    address where nothing answers (the plain error and its ways out), then the dev bridge. `LANI_QA_SETUP_PAIR=it-de`
    runs it for an Italian learner. Screenshots to `$LANI_QA_SETUP_SHOTS` as `onb-*.png`.
  - `intro-voice`: the first visit on the fresh install, each step's pieces in the log (`intro-voice.log`).
  - `hosted`: through the welcome to "Just this device", the tutor step's three ways. `LANI_QA_HOSTED_CHILD=1` adds
    the child's step and the hold.

## What's left

- The founding ("Dove sarà il tuo villaggio?") and the news sheet are still silent.
- `gameIntro.arrive` names Triglav in every culture. Voiced now, Teo hears "una valle sotto il Triglav" in a Friuli
  village. A culture's own arrival line is content work (plan 2 lists it).
- The QR path's camera can't be driven on the emulator (no code in its virtual scene). Pairing by code is covered by
  `PairingTest`, and QA screenshots the scanner. Pasting the code's text under "Ročno" pairs the same way, by hand.
- A hosted child's profile isn't in the export's manifest: imported elsewhere, the device asks again.
- The tutor's key error depends on `LiveTutor.setKey`'s English messages ("refused", "401"). A typed error would be
  sturdier.
