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
  phrase?: string       // kind phrase only: the phrase with {n}: "Hvala, {n}."
  file: string          // <40 hex>.mp3, as a piece
  ms: number
  engine_voice: string
  model: string
}
```

Its identity is `(language, voice, name, kind)`, and that is the whole key: **no learner, node, key or device on the row or in a
log line.** The phrase id is `sha1("<language>|<phrase>")`, first 12 hex characters. A name clip of kind `arch`, `ending` or `rise`
is stored at one active level (`NAME_ACTIVE_DB`, −20 dB over the frames within 20 dB of its loudest), so that a line's one stored
gain fits every name. A phrase clip is stored as it was voiced, levelled like any clip.

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
  "phrase": "Hvala, {n}.",
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
finished MP3 at the same URL.
