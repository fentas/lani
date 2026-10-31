# Built: the first launch brought up to date (setup-refresh)

*Built 2026-10-31, branch `setup-refresh`. Jan: "did we updated the bootstrap view? -> with speach - selection - etc?"
The first-run setup ([onboarding.md](onboarding.md)) had not kept up with what the app gained since: no step spoke,
"🗣️ Moj glas · My voice" was only in the settings, and there was no aim (target level) at all. This round brings it up
to date for an adult (Jan: Slovene from English, a phone) and for a child set up by a parent (Teo: about seven, Italian
from German, a tablet of 2560 × 1600 on its side and upright). Every new text is machine-written, in sl, en, de and it,
and flagged for a native check (the tables' `_note_setup_refresh`).*

## What

### Every step speaks

- **Each step reads itself as it opens**, by the rule of the intro and the founding: the school's "🔊 Beri razlago ·
  Read the explanations" on (the default) and no screen reader (TalkBack reads the screen itself). What it says
  (`game/SetupSpeech`, pure):
  - the step's title in the language learned (the narrator: the culture's clip once paired, else the phone's voice for
    that language), then in the base what the step shows and asks, by the phone's voice of the base;
  - **before a language is chosen**, the welcome in the phone's language alone ("Willkommen! Lani ist ein kleines Dorf …
    Welche Sprache möchtest du lernen?");
  - **a language's tile or chip says its name in it** as it's tapped ("italiano", "Deutsch"), so a child who doesn't
    read yet hears what they chose; the languages' step then starts with that name ("italiano. Benvenuti! …").
    With TalkBack on, the tile's text is TalkBack's to say.
- **A 🔊 at each step's top right** (`ReadAloudButton`, the founding's): a tap reads the step again, ⏹ stops it.
  TalkBack: "Preberi na glas · Read aloud".
- **Once per step.** Choosing a language builds every screen anew in the new pair; the step isn't read again then
  (`vm.setupRead`), only the chip's name is said. Going on to the next step stops the step before (its reading, or its
  own starts); a name just said is let finish.
- **Offline on the phone alone** it is the phone's text-to-speech in each piece's language (`HintPiece.lang`: the pair
  is being chosen while these play). The app bundles no voice clips (only the ambient sounds), so "bundled clips where
  the app has them" is none today; once paired, the language learned comes in the narrator's clip from the node.

### The choices that matter, each with a default and a line of why

- **🎯 Cilj · Aim** (`app/Aim`): the level the village's ages lead to. B2 preselected (Jan: "b2 should be default
  aim"); from B2 on the level after the learner's (C1 at B2, C2 at C1 and C2); only levels above the learner's are
  offered, and an aim the level reaches falls back to the default. Its line says plainly what it changes: "The level you
  want to reach. The village's ages follow it: it becomes a town when you know about 2000 words (B2)" (the words are
  `Catalog.wordsFor(MESTO, aim)`: 1000 for B1, 3500 for C1).
  - **Just this device:** under "Your level now" in the questions; sent with the new learner (`HostedAnswers.targetLevel`
    → the phone bridge's `initLearner` → `initLearnerData`'s `targetLevel`, kept as `target_level` when above the
    level).
  - **The family's computer:** once paired, on "✨ Še o tebi · A little about you", preselected with the aim the node's
    profile has (`GET /learner/target`); a changed one is kept at once (`POST /learner/target {level}`), and the app reads
    everything anew. At setup there is no age yet, so the aim applies at once.
- **🗣️ Moj glas · My voice:** the settings' picker (`MyVoiceChoices`: each voice's face, name and ▶ sample; "🔇 Brez
  glasu") offered at setup, on the setup's blue. The voice marked is `MyVoices.preselect`: the learner's own choice once
  made, else the first voice of their gender and age (a boy's for Teo: the Friuli cast's child voices), else the node's
  default. A tap chooses it at once (the node keeps it); going on keeps the one marked (`keepAtSetup`: "default" when it
  is the node's own, so it still follows the cast), and the offer at the first dialog line doesn't come again.
  - **Just this device:** on the last page, above the tutor (the phone's bridge is up then: its cast's voices; their ▶
    by the phone's voice in the voice's gender, a child's higher, as no ElevenLabs is there).
  - **The family's computer:** on "Še o tebi", once the bridge is reachable.
- **The child switch** stays: "🧒 Za otroka · For a child" keeps a child's defaults (15 minutes, the reminder off
  unless a parent turns it on, keys and accounts behind the parent's hold). New: a child's gender reads "kot deček · as a
  boy" and "kot deklica · as a girl" instead of a man's and a woman's.

### Layout and flow

- **No new screen on this device alone**: the aim fits into the questions, the voice into the tutor's page.
- **One page more with the computer**: "✨ Še o tebi · A little about you" after pairing, the aim and the voice with
  their defaults, "🏡 V mojo vas · To my village". The way back from the phone to a computer (Settings → "To my own
  computer") skips it (`SetupFlow.firstRun`).
- **The tablet**: every page at most 640 dp wide, centred. On a wide screen on its side (≥ 840 dp and wider than tall),
  the welcome and the languages put the picture and the greeting beside the choices; before, the picture alone filled
  Teo's tablet on its side and the tiles were below it, and the questions ran across 1,200 dp.
- **Back on every step**: a step back as before; on "Še o tebi" Back keeps the defaults and goes to the village (the
  pairing is done; `SetupFlow.back` stays there); on this device's voice and tutor page Back goes on to the village with
  the tutor for later (a way chosen in the tutor's step goes back to its ways first).
- **TalkBack**: the 🔊 is labelled; the voices are radio rows with a labelled ▶; the aim chips are filter chips; nothing
  reads itself over TalkBack.

## Tap counts

The taps from the first screen to the village (typing a name or an address counts as its field's tap; scanning the
code is not a tap). The defaults kept, as most will.

| Journey | Before | After |
|---|---|---|
| Jan, the family's computer, QR code | 4 (slovenščina, Naprej, S svojim računalnikom, Skeniraj kodo) | 5 (+ V mojo vas) |
| Jan, the family's computer, typed | 7 (+ Ročno, the address, the token, Poveži) | 8 (+ V mojo vas) |
| Jan, just this device | 7 (slovenščina, Naprej, Samo ta telefon, the name, as a man, Ustanovi, Pozneje) | 7 |
| Teo, just this device, a German tablet | 8 (italiano, Avanti, Solo questo dispositivo, the name, Per un bambino, as a boy, Fonda, Al mio villaggio) | 8 |

Choosing another aim or voice is one tap each (a ▶ to listen, none needed). Nothing to read is needed: every step says
itself.

## How

- `game/SetupSpeech` (pure): each step's pieces; `HintPiece.lang` and `HintVoice`: a piece in its own language by the
  phone's voice (the narrator's clip only for the village's language).
- `app/SetupFlow`: `Step.ABOUT`, `firstRun`, `asksAbout`, `paired`, `chooseAim`; `app/Aim`.
- `data/MyVoice`: `MyVoices.preselect`, `keepAtSetup`; `ui/settings/MyVoiceSettings`: `MyVoiceChoices` shared, with
  the mark shown and the lines' colour.
- `data/AimApi`: `GET` and `POST /learner/target`; `AppViewModel`: `connect` goes to "Še o tebi" when the first launch
  paired with a computer, `setupFinished`, `aimNow`, `setAim`, `setupRead`.
- `ui/setup/SetupScreen`: `SetupReading` (the auto-read), `SetupPage` (the 🔊, the width, the side pane), the tiles'
  and chips' names, `About`; `ui/hosted/HostedScreens`: the aim, a child's gender, the voice above the tutor, each
  step's speech, Back on the last page; `ui/game/ReadAloudButton`: `stopOnLeave` (the setup stops its own).
- The bridge, as small as it could be while `target-next-age` builds the pending aim: `NewLearner.targetLevel` and
  `aimOf` (`learners.ts`), the phone's `initLearner` passes it, `GET`/`POST /learner/target` in `features/level.ts`
  (`writeTarget`: the profile's `learner.target_level` at once; the lease guards it).
  **To reconcile at the rebase on `target-next-age`:** its default B2 replaces the `NEXT[level]` fallback in
  `initLearnerData`; `POST /learner/target` should call its setter so that, for a learner whose village has ages
  already, the aim waits for the next age-up (at setup there is no age, so at once is right).
- QA's hook `setup:phone:<code>`: the first launch as on a phone in that language.

## Tests and QA

- `app/SetupFlowTest` (22): the aim's default and choices, an aim kept or reset as the level moves, the hosted answers'
  aim, "Še o tebi" after pairing (and not on the phone alone, nor from the hosted mode's way back), Back on every step,
  a child's voice preselected of their gender, the choice kept at setup.
- `game/SetupSpeechTest` (7): the welcome in the phone's language, a language's name in itself, the languages' step
  (it · de), the ways, the computer and typing (sl · en and it · de), the questions with the aim, a child's voice page
  (a parent sets up the tutor), "Še o tebi", every step in all 12 pairs: the language learned first, each piece's
  language and table, no emoji or brace.
- The bridge's smoke: the aim at setup (`initLearnerData`), `GET`/`POST /learner/target` (kept, already, refused at or
  below the level).
- `companion/bin/qa`:
  - `setup` (always): now with what each step said (`onb-speech.txt`; `LANI_QA_SETUP_SPEECH=1`) and "Še o tebi" once
    paired.
  - `setup-tour` (alone, no node): the first launch on Teo's tablet, each step on its side and upright, German (hook
    `setup:phone:de`), Italian for a child on this device; what each step said, Back, the aim, a voice's ▶, the founding;
    the taps counted.

### The emulator runs

Before (main at `dafced59`, the same QA script) and after, on the QA emulator (a phone, 1080 × 2400; the tablet by
`wm size` 2560 × 1600 / 1600 × 2560 at 320 dpi):

- **Jan's way** (`--steps setup`, a synthetic learner Ana, sl · en, the family's computer by its typed address): setup
  ok. Each step now reads itself; "Še o tebi" came after the connection, its title in the narrator's clip voiced by
  the node (`1/6 sl node "Še o tebi"`), the rest by the phone's English voice; "🏡 V mojo vas" went Home. Ana's profile
  (made by `initLearnerData` before `target-next-age`) aims at A2, so A2 was the aim marked; a learner made after it aims
  at B2. The voices: Ana has no gender, so both genders' (Nina marked, the female default).
- **Teo's way** (`--steps setup-tour`, it · de, a child, this device alone, the tablet): setup-tour ok, every step on its
  side and upright; on its side the welcome and the languages show the picture beside the choices (before: the picture
  alone filled the screen). Bhola, a boy's voice, was marked; its ▶ said "Ciao! Sono Teo." by the phone's voice at the
  child's pitch (1.3), as the phone's bridge has no ElevenLabs. Back from the languages, the ways and the questions went
  a step back.

What each step said (the log's HintVoice lines: the piece, its language, its voice; a line cut at 60 characters):

| Step | Teo (it · de, the tablet) | Jan (sl · en, the phone) |
|---|---|---|
| Welcome | de: "Willkommen!", "Lani ist ein kleines Dorf, das mit jedem Wort wächst …", "Welche Sprache möchtest du lernen?" | en: "Welcome!", "Lani is a little village …", "Which language do you want to learn?" |
| Languages | it: "italiano", "Benvenuti!", "Lani è un piccolo villaggio …"; de: "Willkommen!", "Lani ist …", "Erklärungen auf" | sl: "slovenščina", "Dobrodošli!", "Lani je majhna vas …"; en: "Welcome!", "Lani is …", "Explanations in" |
| Ways | it: "Come vuoi usare Lani?"; de: the question, "Nur dieses Gerät" and its line, "Mit dem Computer der Familie" and its line | sl: "Kako želiš uporabljati Lani?"; en: the same five |
| The computer | | sl: "S svojim računalnikom"; en: the title, "On the computer, run lani-pair …" |
| Typed | | sl: "Ročno"; en: "Enter manually", "Paste the lani://pair… text …" |
| The questions | it: "Lani su questo dispositivo"; de: "Alles lebt auf diesem Gerät …", "Dein Name", "Wie soll dich das Dorf ansprechen?", "Dein Niveau jetzt", "Ziel", "Minuten am Tag" | |
| Voice and tutor | it: "La mia voce"; de: "Meine Stimme", "So klingst du in den Gesprächen …", "Jetzt der Tutor", "Das richtet ein Elternteil ein" | |
| Še o tebi | | sl: "Še o tebi" (the node's clip); en: "A little about you", "Aim", "The level you want to reach …", "My voice", "This is how you sound …" |
| The founding (as before) | it: "Dove sarà il tuo villaggio?"; de: what to do, that it's chosen once | |

Every piece of the language learned came from the phone's voice for it (`tts:it`, `tts:sl`) except "Še o tebi", the
node's: before pairing there is no node, and the app has no clips of its own.

The screenshots (not committed; made again by the two runs): `scratchpad/ux-setup-refresh/{before,after}/adult/onb-*.png`
and `{before,after}/child/tour-*-{side,upright}.png`, with `onb-speech.txt`, `tour-speech.txt` and `tour-taps.txt`.

## What's left

- **A native check** of the new strings (sl, de, it; `_note_setup_refresh`) and of what is read: the Slovene "Raven, do
  katere želiš priti. Dobe vasi sledijo cilju …" especially.
- **The aim after setup**: there's no place in the app to change it later yet (`lani-profile set-target` on the
  computer, from `target-next-age`); a settings row would use the same `POST /learner/target`.
- **Bundled clips**: the setup's target pieces are the phone's voice before pairing; a few clips of the narrators (the
  four welcomes, "Kako želiš uporabljati Lani?") shipped in the app would give the child the village's own voice from
  the first screen.
- **The welcome's language**: the phone's; a phone in a language Lani doesn't have reads English.
