# Name lines: a line voiced once, the learner's name joined in (`audio-names`)

Plan 3's ["lines with the learner's name"](../03-voice-service.md#proposed-lines-with-the-learners-name-a-filler-name-cut-out-2026-10-14),
as the splice test of 2026-11-04 left it: a line is voiced once with a filler name, cut, and the learner's name joined in. The
shared pieces are made the first time anyone reaches a line and kept for everyone. A name clip is made the first time anyone of
that name needs it and kept for everyone of that name.

## The contract: a line, a piece, a name clip, a manifest

This section is what the hosted library builds to. `audio-names` owns it. Nothing here names a learner, a node, a key or a
device: a name clip belongs to a name, not to anyone who has it.

### Words

| Word | What it is |
|---|---|
| **line** | A line of curated content with the name marked: `Hvala, {n}. Ti si zlat.`. Rendered for the learner's gender first, so a `{m:…\|f:…}` pair makes two lines. Its id is `line = sha1("<language>\|<voice>\|<said>")`, first 16 hex characters. |
| **type** | How the line is built, read from its text: `first` (the name opens a sentence), `phrase` (a short phrase before the name is voiced per name: `Hvala, Jan.`), `splice` (the name stands between two real pauses), `whole` (none of these: voiced whole per learner, as before). |
| **template** | How the name is said, read from its text: `arch`, `ending` or `rise`. |
| **piece** | A shared part of one take of a line, the same for every learner: `head` (what comes before the name or its phrase) or `tail` (what comes after). |
| **name clip** | A name said by one voice in one **kind**: `arch`, `ending`, `rise`, or `phrase:<id>` for a phrase with the name in it. |
| **manifest** | How the pieces and a name clip make the line: the order, the pauses, one gain. |

### A piece

```ts
type Piece = {
  language: string      // "sl", the language the line is in
  voice: string         // the cast's speaker: "grandma", "@micka"
  line: string          // the line's id (16 hex)
  part: 'head' | 'tail'
  text: string          // what it says: "Ti si zlat."
  file: string          // <40 hex>.mp3: MP3, 44.1 kHz mono, faded at its cut edges (5 ms in, 10 ms out)
  ms: number            // its length, without the MP3's delay and padding
  engine_voice: string  // the engine's own voice id: a piece is good only while the voice is
  model: string
}
```

A piece is made from a take of the whole line with a filler name, through `/with-timestamps`. Its identity is
`(language, voice, line, part)`. A voice swapped under it (`engine_voice` differs from the cast's) makes it stale, and it is made
again when next needed. A model change does not.

### A name clip

```ts
type NameClip = {
  language: string
  voice: string
  name: string          // normalized: NFC, lower case, single spaces: "jan", "ana marija"
  kind: string          // "arch" | "ending" | "rise" | "phrase:<12 hex>"
  phrase?: string       // kind phrase only: what is voiced, with {n}: "Hvala, {n}. Zdaj vem." (the phrase and the words that lead on from it)
  file: string          // <40 hex>.mp3, as a piece
  ms: number
  engine_voice: string
  model: string
}
```

Its identity is `(language, voice, name, kind)`, and that is the whole key: **no learner, node, key or device on the row or in a
log line.** The phrase id is `sha1("<language>|<phrase>")`, first 12 hex characters: the same phrase leading on and ending a line
are two clips, because they are said differently. A name clip of kind `arch`, `ending` or `rise`
is stored at one active level (`NAME_ACTIVE_DB`, −20 dB over the frames within 20 dB of its loudest), so that a line's one stored
gain fits every name.

**A phrase clip.** `phrase` is what is voiced, and the id hashes that:

- **A phrase that leads on** (`Hvala, {n}. Ti si zlat.`) is voiced with words after it, `Hvala, {n}. Zdaj vem.`, so that it is said as leading
  on, and its take is **cut** to the pause after the name. Only what can cut can make one: a node.
- **A phrase that ends its line** (`Lahko noč, {n}.`) is voiced as it is, `phrase` is the phrase itself, and nothing is cut. It is the only
  kind the service can make, which it knows by the line's `said` ending with its `phrase`.
- **Either is at the level of a clip** (−18 LUFS, `LEVEL` in `voice.ts`), because a line gives it gain 0 and it stands beside pieces at
  that level. A node levels the take before the cut, so the cut keeps it.
- **A phrase the service makes must be levelled too, and today isn't.** The contract is right and the service is short of it: it has no
  ffmpeg, and a line like `Glasen? Kdo je glasen, {n}?` puts its phrase next to a levelled piece, a jump at a seam the phone can't
  correct. A node brings any phrase from the library to the level as it takes it, which leaves one already there alone. What is left is
  the phone joining on the service's own routes during a trial: until the service levels, it should make a phrase only for a line with
  no pieces, where a difference is only one between lines, not inside one.
- **`ms` must be known.** A part with no length joins to nothing, so the phone refuses one, and the phone's voice says the line. A node
  measures what it takes.

### The line's manifest: shared, no name in it

```json
{
  "v": 1,
  "line": "3f9c0e1ab2d47c55",
  "language": "sl",
  "voice": "grandma",
  "said": "Hvala, {n}. Ti si zlat.",
  "type": "phrase",
  "template": "ending",
  "kind": "phrase:8f3a1c0e2b7d",
  "phrase": "Hvala, {n}. Zdaj vem.",
  "gain": 0,
  "parts": [
    { "kind": "name" },
    { "kind": "gap", "ms": 120 },
    { "kind": "piece", "part": "tail", "file": "<40 hex>.mp3", "ms": 980 }
  ]
}
```

- `kind` says which name clip the line wants. `gain` (dB, ±8 at most) is what that clip gets in this line. It is 0 for a phrase:
  a phrase needs none. A manifest holds no seam targets and no pitch.
- `parts` plays in order. A `gap` is silence: the line's own pause beside the name.
- A line whose cut can't be trusted has `"type": "whole"` and no `parts`: it is voiced whole for each learner, and no take is paid
  for it again.

### The manifest the phone gets: the same, with the name clip in it

```json
{
  "v": 1,
  "id": "<40 hex>",
  "text": "Hvala, Jan. Ti si zlat.",
  "voice": "grandma",
  "parts": [
    { "kind": "name", "url": "/voice/file/<40 hex>.mp3", "ms": 640, "gain": 0 },
    { "kind": "gap", "ms": 120 },
    { "kind": "piece", "url": "/voice/file/<40 hex>.mp3", "ms": 980, "gain": 0 }
  ]
}
```

`id` is `sha1` of the parts in order (each file or gap, and its gain), so a part that changes gives the line a new name, as a clip
gets when it is made again.

### Routes

| Route | Answer |
|---|---|
| `POST /voice/say`, `POST /voice/prepare`, `GET /voice/index` | As before. For a joined line the URL is `/voice/line/<id>.mp3`. |
| `GET /voice/line/<id>.json` | The phone's manifest. |
| `GET /voice/line/<id>.mp3` | One finished MP3, joined on the node the first time it is asked for and then kept. An older app asks this, because to it the URL is any clip's. |

A new app sees `/voice/line/` and asks for the manifest. Any other answer, or a join that fails on the phone, falls back to the
finished MP3 at the same URL. The voice service keeps no finished file (a 404): there a join that fails ends with no clip, which is the
phone's voice, with nothing said to the learner, and the line isn't asked again for ten minutes (`LineFallbackTest`).

### The library

With `GLAS_URL` and `GLAS_KEY` set as well as `LANI_VOICE_NAMES`, `NameStore` asks the service before it makes anything, and adds what it
made, so a line or a name made once by anyone is there for every node:

- **Asking is free.** It asks with `make: false`: the service voices nothing for it, whatever the key may do. What is spent stays what
  this node's own settings allow, and a library hit costs neither the day's characters nor one of the month's new names.
- **What goes with a name:** language, voice, name, kind and, adding, how it was made. Nothing of the learner, node or device; a request
  carries the node's key in its header, as every request to the service does, and no row or log keeps it.
- **Only in the voice this cast has now.** Something in another voice is left there and made here.
- **Someone's own voice** (`@micka`) stays on the node, both ways.
- **A line whose cut can't be trusted** is added too, as `whole`, so nobody pays for that take again.
- **A key that may read but not add** reads: what it can't add is said once in the log.
- **Out of reach** is no library: the node goes on alone, and nothing waits for what is being added.

Against the service's real routes, on its own in-process stack with fake engines: one node with a `publish` key reached four lines, one
of each shape, and its 4 lines and 4 name clips were all taken (200); a second node with a key that may only read joined all four and
voiced nothing.

## What it does

**Off unless the node sets it.** With `LANI_VOICE_NAMES` unset nothing here runs: every line is voiced whole, as before, and
no table is made. An older app and a clip already there are never touched: a whole clip of a line stays that line's clip, so
Jan's existing "Jan" clips stay.

**A line reached for the first time, with the setting on.** `POST /voice/say` finds the text in the content's catalogue:
`name-lines.ts` renders the content with a probe name for a man and for a woman, marks the name and flags each line. A text that
isn't there (the tutor's words) is voiced whole, and nothing of it reaches the shared pieces. For a line of the content:

1. **The name is checked.** Failing, the phone's voice says the line, silently (204).
2. **The line's pieces.** One take with the filler through `/with-timestamps`, cut, the pieces kept. A line that is only its name
   or phrase needs no take. A cut that can't be trusted marks the line `whole`: it is voiced whole for each learner, and that take
   is not paid for again.
3. **The name clip.** Two takes of its carrier sentence, the lower kept. A clip the library has costs nothing. A name that can't be
   cut from what was paid for isn't tried again: its lines are voiced whole.
4. **The manifest.** The answer is the line's URL. A new app joins it; an older one downloads one finished file at that URL.

**The shapes.**

| Type | Example | Voiced per name | Shared |
|---|---|---|---|
| `first` | `{n}! Pridi, juha je vroča.` | the name, in its template | the tail, and any sentence before it |
| `phrase` | `Hvala, {n}. Ti si zlat.` | the phrase, up to 18 characters before the name | the tail, and any sentence before it |
| `splice` | `Še enkrat, {n}, in na glas.` | the name, in its template | head and tail: real pauses stand on both sides |
| `whole` | `Danes je {n} doma.` | the whole line, as before | nothing |

A line is `whole` when a word follows the name with no pause, the name comes twice, another form of it is used
(`{learner:dat}`), the name stands in a long sentence with no comma before it, someone in the line is called what the filler is
("Marko je v vinogradu"), or a woman's own line needs a left edge her filler can't give (Ana has no stop to anchor on).

**The content today**, from `bun test/name-lines.ts --table`:

| Culture | Lines | `phrase` | `first` | `splice` | `whole` | arch | ending | rise |
|---|---|---|---|---|---|---|---|---|
| primorska | 2,148 | 1,007 | 651 | 398 | 92 | 1,094 | 938 | 116 |
| friuli | 947 | 490 | 286 | 161 | 10 | 577 | 298 | 72 |
| kaernten | 583 | 306 | 161 | 115 | 1 | 322 | 217 | 44 |
| lakeland | 398 | 215 | 98 | 85 | 0 | 214 | 154 | 30 |

**The cut** (`name-cut.ts`) is the test's, ported from its Python: on all 168 of the test's takes it gives the same cut points to
the frame. Nothing else was tuned. A plain join, no pitch match, one stored gain for a name-only line.

**The name is the learner's own text.** At most 24 characters and two words, Latin letters with their marks, a hyphen or an apostrophe
inside a word, a vowel, no letter four times running, a short list of words no voice says as a name. At most 3 new names a calendar
month on a node (`name_seen` holds the month and the name, nobody's). A name already counted goes on. With `LANI_VOICE_NAMES` set, a name
that fails is voiced nowhere, not only in name lines: any text that says it or a form of it goes to the phone's voice (204), the learner's
own lines, a text the content doesn't have, the day's audio and what a publish queues among them. A re-record of such a text fails
plainly. Preloading checks the name whether `LANI_VOICE_NAMES` is set or not, because it spends with nobody waiting.

**The join on the phone** (`audio/LineJoin.kt`). The manifest's parts are downloaded as clips of their own, decoded with the phone's
decoder, the encoder's delay taken off by the LAME header, and written sample against sample into one WAV under the line's name
in the clip cache. There is no seam a player could open a gap in: the player gets one file. The cache, the cap, the day's audio and
`PreviousClips` treat it as any clip. Anything that goes wrong gets the finished MP3 at the same URL.

**Lazy preloading** (`voice-preload.ts`, `DialogAhead`). As a dialog, a test or a module opens, the app sends its lines in play order to
`POST /voice/queue`: each person's line in their voice, the right answer and the reply to it, never a wrong answer's reaction. The
lines are as this learner plays them: their level, gender and name. The node makes what it lacks one at a time at the gate's middle turn.
A new start replaces the last, and leaving cancels what is left; a take already at work ends and is kept, so a quick leave can cost one line.
The app sends a start and its cancel in order. Older apps send nothing, and a bridge without the route costs nothing.

**The redo.** A long press on a joined line voices it whole, as plan 3 says: the whole clip then stays that line's clip.

## Settings

All in `lani.env` or `keys.env`. What is off stays off until the node sets it.

| Setting | Default | What it does |
|---|---|---|
| `LANI_VOICE_NAMES` | off | Name lines from pieces. Most it can spend a day: `LANI_VOICE_NAMES_DAILY`. |
| `LANI_VOICE_NAMES_DAILY` | 3,000 | Characters pieces and name clips may cost a day. Spent, the phone's voice says the rest of the day's new lines. |
| `LANI_VOICE_NAMES_PER_LEARNER` | 3 | New names a node may voice in a calendar month. |
| `LANI_VOICE_NAME_TAKES` | 2 | Takes of a name, the lower kept; 1 to 3. |
| `LANI_VOICE_PRELOAD` | off | Lines got ready as a dialog, a test or a module opens. Most it can spend a day: `LANI_VOICE_PRELOAD_DAILY`. |
| `LANI_VOICE_PRELOAD_DAILY` | 4,000 | Characters that may cost a day, whatever they are spent on. |
| `LANI_VOICE_MONTHLY` | none | Characters ElevenLabs may be sent in a calendar month, for anything, live lines too. Past it the local worker, then the phone's voice. Every character is counted from now on, set or not. `voice-build` counts in it and isn't held to it: a person asked. |
| `LANI_VOICE_REVOICE_KEEP` | 30 % of the cap | Of `LANI_VOICE_REVOICE_DAILY`, what an older model's upgrades leave for clips in the wrong voice. **On for every node, with no setting:** it spends less, never more, and what is kept stays unused on a day nothing in the wrong voice is asked for. |

Both daily caps count the same characters when a preload makes a name line: the name lines' cap holds first.

## What it costs

From `bun test/name-lines.ts --cost`, with two takes of a name, at the 0.2 credits a character `eleven_v4_turbo` billed in the test.
Nothing here is voiced by turning a setting on: lines are made as learners reach them. "Once for everyone" is what all learners
share. "A new name" is what one new name costs if its learner reaches every line in scope; a name the library has costs nothing.

| Scope | Culture | Lines | Voices | Once for everyone | A new name | As today, per learner |
|---|---|---|---|---|---|---|
| First scenes | primorska | 73 | 5 | 2,894 chars, 579 credits | 1,912 chars, 382 credits | 3,156 chars, 631 credits |
| | friuli | 17 | 3 | 717, 143 | 501, 100 | 756, 151 |
| | kaernten | 0 | 0 | none at A1 | | |
| | lakeland | 8 | 3 | 388, 78 | 196, 39 | 388, 78 |
| A1 | primorska | 645 | 8 | 27,255, 5,451 | 12,036, 2,407 | 28,998, 5,800 |
| | friuli | 426 | 8 | 22,308, 4,462 | 7,602, 1,520 | 22,794, 4,559 |
| | kaernten | 214 | 8 | 12,769, 2,554 | 5,108, 1,022 | 12,779, 2,556 |
| | lakeland | 200 | 7 | 9,901, 1,980 | 4,118, 824 | 9,911, 1,982 |
| All | primorska | 2,154 | 9 | 110,653, 22,131 | 25,669, 5,134 | 115,658, 23,132 |
| | friuli | 948 | 9 | 60,097, 12,019 | 11,916, 2,383 | 60,739, 12,148 |
| | kaernten | 583 | 9 | 37,624, 7,525 | 9,214, 1,843 | 37,785, 7,557 |
| | lakeland | 398 | 9 | 22,579, 4,516 | 7,626, 1,525 | 22,630, 4,526 |

These count each cast speaker as one voice. Someone with a voice of their own counts apart, so a real village's "a new name" is
higher: more voices, more name clips. The first learner of a line pays about what voicing it whole costs today, and every learner
after pays only for their name. The whole gain comes from the second learner on.

The first scenes count the fire and the tent. A1 counts the scenes at A1 and the villagers' lines, not the course, the stories or
the project steps, which have few name lines.

## Tests

- `bun test/name-lines.ts` (the smoke's section `name-lines`): the flags of every name line of all four villages, the name check, the cut,
  the store against the fake ElevenLabs, the gate's turns, the caps, preloading, the library against a fake of it, and the routes on a
  real bridge.
- `LineJoinTest`, `DialogAheadTest`: the manifest, the join, the WAV, the LAME header of a real ffmpeg file, a dialog's lines.
- `LineFallbackTest`: a join that fails against the voice service, by each way it can, ending with no clip and no error.
- QA: `companion/bin/qa-slot --steps name-join` with `LANI_DEV_FAKE_ELEVENLABS=1 LANI_VOICE_NAMES=on LANI_VOICE_PRELOAD=on` and a synthetic
  learner. See [what the device showed](#what-the-device-showed).

## How good it is, on real speech

`test/name-samples.ts` drives the real store with the splice test's own takes, replayed by the fake ElevenLabs: nothing is voiced. All
14 of the test's lines join, with none voiced whole, and every take they ask for exists. On the test's own rating, against its
whole-voiced lines:

| Line | Type | Template | This build | The test |
|---|---|---|---|---|
| g1 `{n}! Pridi, juha je vroča.` | first | arch | 80.6 | 80.9 |
| g2 `O, {n}! Ravno kuham. Si lačen?` | phrase | arch | 33.8 | 34.0 |
| g3 `Hvala, {n}. Ti si zlat.` | phrase | ending | 88.6 | 90.2 |
| g4 `{n}? Še buden? …` | first | rise | 71.8 | 59.5 |
| p1 `Lovski blagor, {n}!` | phrase | ending | 78.4 | 78.5 |
| p2 `Dober dan, {n}! Si prišel pomagat?` | phrase | arch | 63.8 | 83.2 |
| p3 `Lahko noč, {n}.` | phrase | ending | 94.5 | 94.8 |
| p4 `{n}, imaš čas za eno zgodbo?` | first | arch | 62.1 | 61.7 |
| p5 `Glasen? Kdo je glasen, {n}?` | phrase | rise | 25.1 | 25.3 |
| t1 `{n}! To je pa res posebno darilo. …` | first | arch | 79.4 | 81.8 |
| t2 `Hvala, {n}! Odlično darilo. Pet plus!` | phrase | ending | 78.3 | 81.6 |
| t3 `Hvala, {n}. Odlično delo.` | phrase | ending | 62.2 | 63.0 |
| t4 `{n}, kaj nosiš na čelu sprevoda?` | first | arch | 81.7 | 66.5 |
| t5 `Oho? Še enkrat, {n}, in na glas.` | splice | ending | 86.9 | 89.3 |
| **Median, worst** | | | **78.3, 25.1** | **79.7, 25.3** |

- **The one real loss is p2.** Its two takes are nearly level on pitch without word times (−3.7 and −3.0 semitones here; −3.3 and
  −3.7 in Praat on the same span), so the lower-take rule picks the other one. The test separated them with Whisper's word times, which
  a node doesn't have at this point. It isn't tuned here: one line of nine.
- **g4 and t4 gain** because a line now takes the one carrier of its template, not the test's pick by seam targets.
- **Only Jan's name.** The test's 68.6 on other names can't be remade without new takes: its name-first carriers for them are other
  sentences than the ones this build asks for.
- **Weakest stay weakest:** question lines and p5 above all. Jan's question from the test stands: does p5 sound wrong, or only different?

## What the device showed

`companion/bin/qa-slot --steps name-join` on an emulator (API 36, a synthetic learner called Matej, the fake ElevenLabs, whose takes
are speech-like), measured from the file the phone plays:

| What | Measured |
|---|---|
| The line | `O, Matej! Pridi, pridi. Pečem potico. Mi pomagaš?`: name 585 ms, pause 300 ms, piece 2,885 ms |
| Joined and played | `joined … 3 parts, 166256 samples`, `clip start … (3770 ms)`, `clip end` |
| Its length | 3,770.0 ms against the parts' 3,770 ms |
| The pause at the seam | the manifest's 300 ms; quiet there for 307 ms |
| A click where parts meet | sample steps of 7 and 1, out of 32,768 |
| Against the node's file for an older app | 0.00 ms apart |
| Said again | from the phone |
| A dialog opened | 198 to 236 characters got ready ahead, by the dialog |

The measurement found a bug no unit test would have: this emulator's decoder honours the gapless header and gives each part its true
length, so taking the encoder's delay off as well cut 13 ms from the start of every part. A part now starts where the decoder leaves
it, read from the manifest's length (`LineJoin.soundStart`). A part decoded short is refused, and the finished file comes instead.

**An app with no join code** on the same bridge (a build from this branch's base, `LANI_QA_OLDER_APP=1 qa-slot --apk <it> --steps name-join`)
asks as ever and gets the line's URL: one finished MP3 of 61,483 bytes, `clip start` to `clip end`. Not the 0.2.698 on Teo's tablet itself: the
URL is any clip's to it (`Clips.file`, `ClipsApi.download`), which the code at that version shows, but no device has run it here.

## Samples to listen to

Built by `bun test/name-samples.ts <the test's folder> <folder>`: `<id>-joined.mp3` beside `<id>-whole.mp3`, the same line voiced whole.
The folder needs the test's `takes/` and its whole lines in `store/`. Play the whole one first. The rating is the table above, not an ear.

| Sample | Line | Listen for |
|---|---|---|
| g3 | `Hvala, Jan. Ti si zlat.` | A phrase: the commonest shape |
| g1 | `Jan! Pridi, juha je vroča.` | The name first, only the name new |
| t4 | `Jan, kaj nosiš na čelu sprevoda?` | The name first with a comma: rated far above the test's |
| t5 | `Oho? Še enkrat, Jan, in na glas.` | A splice: pauses on both sides |
| g4 | `Jan? Še buden? …` | A question: does the rise sound right? |
| p2 | `Dober dan, Jan! Si prišel pomagat?` | The take the rule got wrong |
| p5 | `Glasen? Kdo je glasen, Jan?` | The lowest rating: wrong, or only different? |

## Native check

All machine-written, all in `name-lines.ts`. The Slovene ones were used by the test but no native speaker has checked them.

- **Name carriers:** sl `{n}! Pridi sem.`, `Vidiš, {n}. Zdaj vem.`, `{n}? Si tu?`; it `{n}! Vieni qui.`, `Psst, {n}. Adesso lo so.`, `{n}? Sei qui?`;
  de `{n}! Komm her.`, `Ach, {n}. Jetzt weiß ich es.`, `{n}? Bist du da?`; en `{n}! Come here.`, `Look, {n}. Now I know.`, `{n}? Are you there?`.
- **Phrase tails:** sl ` Pridi sem.`, ` Zdaj vem.`, ` in tiho.`, ` Povej.`; it ` Vieni qui.`, ` Adesso lo so.`, ` e piano.`, ` Dimmi.`;
  de ` Komm her.`, ` Jetzt weiß ich es.`, ` und leise.`, ` Sag es.`; en ` Come here.`, ` Now I know.`, ` and quietly.`, ` Tell me.`
- **Fillers:** Marko, Ana; Marco, Anna; Marko, Anna; Marco, Anna.
- **What a thanks or a goodbye starts with,** by language (`CLOSINGS`): it decides between `arch` and `ending`.
- **The words no voice says as a name** (`NOT_NAMES`).

The Italian, German and English carriers have never been voiced: the test was Slovene only. Whether a name cuts as cleanly from them is
unmeasured. Italian's ending carrier starts with "Psst" because a word before the name should end in a voiceless sound, and few Italian ones do.

## Tap counts

No screen changed: 0 taps before, 0 after. A new line may play sooner with preloading on; nothing else shows.

## What's left

- **Jan's ears** on the samples, and the native check.
- **Prebuilding** the first scenes and A1 is a decision, not built: nothing makes pieces ahead of need.
- **`voice-build` knows nothing of name lines:** its plans still count whole lines, and `--upgrade-model` doesn't touch pieces or name clips.
- **A woman's own lines with a splice** are the largest whole class: 35 in Primorska, 5 in Friuli, because Ana has no stop to anchor a left
  edge on. A filler with one (Marta has the same shape as Marko) would bring them in, but no take of it has been measured.
- **Declined names** (`{learner:dat}` and the others) are voiced whole: one voiced line today.
- **Whole takes are kept** beside each line, with their timestamps, so a better cut later costs nothing. No tool re-cuts them yet.
- **A piece that can't be written** after its take is paid for (ffmpeg failing at that moment) pays for the take again at the next ask.
- **The service's name rule** is a copy of `nameOk` from before its vowels were read without their marks: "Gergő" passes here and fails there,
  so that name is made on its node and not shared. One line to bring over.
- **The service levelling its phrases:** see [a phrase clip](#a-name-clip).
