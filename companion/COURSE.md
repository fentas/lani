# Šola · The school: the course

Jan, 2026-10-13: "We are missing basic progression, as most language schools do: the basics and then onwards: biti in
all its forms, male/female, …, tenses. Maybe after the start and some words one could build a school, and do these
special exercises there. Not only dialogs and single questions; rather how it is at school: a text to fill out, a list
of words to conjugate and so on, with help around all the time."

The course is the school's: the language learned level by level (A1, A2, B1, B2), each level an ordered list of units,
each unit a guided introduction, two to four worksheets and a test. It lives in `companion/course/<language>/` (Slovene:
`companion/course/sl/`), beside the grammar book whose pages it teaches (`companion/grammar/<language>/`). The bridge
validates and serves it (`GET /course`, `src/course.ts`); the app bundles it too (offline), plays it from the hamlet on
(the class by the fire, under the linden, then in the school), and keeps the learner's progress in the village state.
How it fits the game (the class, the gates, the pay) is in [GAME.md](GAME.md#the-school); this file is the format and
how to write units.

- [The files](#the-files)
- [The course outline (lani.course/v0)](#the-course-outline-lanicoursev0)
- [A unit (lani.unit/v0)](#a-unit-laniunitv0)
- [The introduction](#the-introduction)
- [Worksheets and the test](#worksheets-and-the-test)
- [The school's exercise types](#the-schools-exercise-types)
- [Homework (lani.worksheet/v0)](#homework-laniworksheetv0)
- [The learner's progress](#the-learners-progress)
- [Writing a unit](#writing-a-unit)
- [Printed sheets](#printed-sheets)

## The files

```
companion/course/sl/
  course.json          the outline: the levels and their units in order (lani.course/v0)
  units/biti.json      a unit (lani.unit/v0), one file per unit, named by its id
  units/spol.json
<data>/app/course/<unit>/<id>.json   the tutor's homework for a unit (lani.worksheet/v0, publish_worksheet)
```

The bridge reads them per request (like the grammar book): a file that doesn't validate is skipped and logged, the rest
is served. `companion/bridge/test/smoke/course.ts` validates every file of the repository.

## The course outline (lani.course/v0)

```json
{
  "schema": "lani.course/v0",
  "language": "sl",
  "levels": [
    {
      "level": "A1",
      "units": [
        { "id": "biti", "title": { "sl": "Biti: sem, si, je", "en": "To be: sem, si, je", "de": "Sein: sem, si, je", "it": "Essere: sem, si, je" } },
        { "id": "spol", "title": { "sl": "Moški, ženski, srednji spol", "en": "Masculine, feminine, neuter", "de": "…", "it": "…" } },
        { "id": "osebni-zaimki", "title": { "sl": "Jaz, ti, on, ona", "en": "I, you, he, she", "de": "…", "it": "…" }, "grammar": ["ti-vi"], "planned": true }
      ]
    },
    { "level": "A2", "units": [] },
    { "level": "B1", "units": [] },
    { "level": "B2", "units": [] }
  ],
  "machine_written": true,
  "review": "machine-written, not reviewed by a native speaker"
}
```

- `language`: the language learned (`sl`, `it`, `de`, `en`).
- `levels`: CEFR levels in order (A1, A2, B1, B2, C1, C2; each at most once). The school opens A1 to B2 (GAME.md).
- `units`: in the recommended order. Each has its `id` (kebab-case, 2–63 characters, unique in the course) and `title`
  (`{"sl", "en", "de", "it"}`: the language learned and every base; English always).
- `planned: true`: a unit written later. It shows only in its level's folded list ("📝 Kmalu · Coming": [What the school
  shows](#what-the-school-shows)), can't be opened, and doesn't count for its level. `grammar` (optional) names the
  grammar pages it is to teach, for whoever writes it.
- A unit that isn't planned has its file, `units/<id>.json`, of the same id and level. A unit file the outline doesn't
  list comes after its level's units.

## A unit (lani.unit/v0)

```json
{
  "schema": "lani.unit/v0",
  "id": "biti",
  "language": "sl",
  "level": "A1",
  "emoji": "🟰",
  "title": { "sl": "Biti: sem, si, je", "en": "To be: sem, si, je", "de": "Sein: sem, si, je", "it": "Essere: sem, si, je" },
  "goal": { "en": "You can say who and where people are, and who isn't.", "de": "…", "it": "…" },
  "grammar": ["biti", "zanikanje"],
  "words": ["doma", "kuhinja", "šola", "utrujen", "učiteljica"],
  "intro": { "table": { "head": ["…"] }, "steps": ["…"] },
  "worksheets": ["…"],
  "test": { "pass": 80, "exercises": ["…"] },
  "machine_written": true,
  "review": "machine-written, not reviewed by a native speaker"
}
```

| Field | What |
|---|---|
| `id`, `level`, `language` | as in the outline |
| `emoji` | its picture in the school (default 📘) |
| `title` | per language, English always; the language learned too |
| `goal` | what the learner can do after it, per base (English always), one sentence |
| `grammar` | 1–4 grammar-book pages it teaches, each once (`companion/grammar/<language>/<id>.json`): the cheat sheet puts their cards first during the unit's sheets, the summary card at the end of the intro is the first one. Never copy a page's explanation into the unit: name the page. |
| `words` | 0–40 words it uses (lemmas or short phrases), for the tutor and the word lookup |
| `intro` | the guided introduction (below) |
| `worksheets` | 2–4 worksheets, in order |
| `test` | the unit's test |
| `machine_written`, `review` | as the grammar pages: who wrote it and who checked it |

## The introduction

Not a page to read first: the new material in small steps, as a teacher brings it, with a quick question after each
step. Each step shows a few lines, an example or two, and a bit more of the table; then a question about what was just
shown; the answer is revealed with a short why; then the next step. At the end the whole rule is a summary card (the
unit's first grammar page), which stays the reference on the cheat sheet: the intro through (or skipped), the unit's
pages are met (`CheatSheetController.met`), their cards unlocked.

```json
"intro": {
  "table": { "head": [{ "sl": "oseba", "en": "who", "de": "wer", "it": "chi" }, { "sl": "biti", "en": "to be", "de": "sein", "it": "essere" }] },
  "steps": [
    {
      "say": { "en": "**Biti** (to be) has a short form for each person. Start with one person: I, you, he or she.", "de": "…", "it": "…" },
      "examples": [{ "sl": "Jaz sem {learner}.", "en": "I am {learner}.", "de": "…", "it": "…" }],
      "rows": [["jaz", "sem"], ["ti", "si"], ["on, ona, ono", "je"]],
      "ask": { "type": "choice", "prompt": "Babica ___ v kuhinji.", "options": ["sem", "si", "je"], "answer": 2, "explain": "Babica is one person (ona): je." }
    }
  ]
}
```

- `table` (optional): the head of the table the steps build (2–4 cells, each words of the language or `{"sl", "en", …}`,
  as a grammar page's table).
- `steps`: 1–10 (a unit's: 3–6 is right). Each step:
  - `say`: the teacher's few lines, per base (English always; Markdown, at most 600 characters each).
  - `examples` (0–4): sentences of the language with their translation per base (`{"sl": …, "en": …, "de": …, "it": …}`,
    the grammar book's examples); heard with 🔊, every word tappable.
  - `rows` (0–8): rows added to the table under the head (as long as the head); the table grows as the steps go.
  - `ask` (optional): one quick question about the step, a `choice` (tap) or a `cloze` (a one-word gap). Graded
    lightly: a miss shows the answer and why, costs nothing and doesn't count on the rule.
- The learner's place is kept (`intro` in the progress): leaving in the middle resumes there. "⏭ Poznam to · I know
  this" skips to the worksheets. The intro can be opened again any time (the unit's "📖 Uvod · Introduction").

### Read aloud

Jan, 2026-10-13: "Can we have the introduction also read (in school lessons)?" Each step is read aloud as it appears,
in the teacher's voice (the school's person of the culture: Mojca's voice of the cast, `@mojca` once she has her own;
Maestra Elena's in the Friuli village), with what is read marked as it
goes (`data/IntroVoice.kt`, what is read: `data/IntroSpeech.kt`):

1. **The explanation** (`say` in the learner's base). Its text without the Markdown marks, the Slovene forms left inside
   as they are ("Biti (to be) has a short form … jaz sem (I am), ti si (you are) …"), is one clip of the voice store:
   the node voices it in one go with ElevenLabs (its model says both languages as they are; `POST /voice/say` with
   `lang`, keyed `en|<the text>` apart from the Slovene lines, shared by every learner of that base: README "Voice").
   On the phone it plays at once, the explanation outlined. As the intro opens (reading by itself on), the app asks for
   its steps' clips ahead, one after the other, the first while Mojca walks in: each step finds its clip as it is read,
   and a step whose clip the node is still making waits for it a moment (up to 6 s). Jan, 2026-10-14: "why is the English
   part not spoken by the same character?" A step is mostly read once, and the clip used to be asked for only as it was
   read and made "for next time"; and the node, asked for the explanation and the step's Slovene at once, went over
   ElevenLabs' concurrency and left the explanation unmade (README "Voice": at once). When it can't be had (offline, no
   ElevenLabs, the quota at its reserve, not in time) the step is read in its runs, and the clip is there next time:
   - the base-language parts by the phone's own voice of that language (Android text-to-speech, its locale; rendered
     to files as the car's prompts are), a translation in brackets right after a form ("(I am)") among them;
   - the Slovene parts in Mojca's voice, at their place: a span in **bold**, *italics* or „quotes" that the app's
     check says is Slovene (as the chat's Markdown taps it), or whose words the intro itself shows (its examples, its
     table and its head, its questions, the unit's title: "**Biti**", and the unit's `words`: an infinitive only its
     explanation names, "**cantare**");
   - each run marked in the explanation as it is read, a short pause between them.
   The phone without a voice for the base language: only the Slovene is read, and a small note says why, once.
2. **The examples**, one after the other in Mojca's voice (their translations not read), each card marked.
3. **The quick question**, as far as it gives nothing away: a gap read as a pause ("Luka … na pašniku."), a choice's
   options never, a translation in brackets at its end left out. Once answered, the sentence with the answer is read
   (as an exercise's feedback), and Mojca reacts (a smile, a shrug). In Mojca's voice: a sentence with a gap to fill
   (a cloze's, a choice's without Markdown: "Micka in France … na njivi."), a word named in »quotes« ("»smreka«"), and
   the first half of an instruction written as a label ("Kakšnega spola? · Which gender?": the language learned, then
   the base, no Markdown; the second half in the base). Anything else as the explanation is read: what the app's check
   says is Slovene in her voice, a question in the base in its runs ("Which form goes with **mi**? ___"). Until
   2026-10-27 the app's check alone decided, and 18 of the 82 quick questions had their Slovene read by the phone's
   English voice.

The Slovene of the reading comes as a line's 🔊 does: her clip on the phone, else the node voices it now, else another
of her voices' clip on the phone, else the phone's offline voice (Piper), else the phone's voice for Slovene. 🔊 on the
step reads it again (⏹ while it reads), 🐢 slower; a tap on the text stops it, so does leaving the step, any other 🔊 or
the 🎤 (one voice at a time).

**Voiced ahead.** So that Mojca is heard in her own voice and never stands in another's (the narrator's clip of the same
words, the local Gepard voice, the phone's), `companion/bin/voice-build` makes her clips of the course ahead, as any
content's (README "Voice", "Prebuild"): its `course` source reads every unit as the app does, a port of
`data/IntroSpeech.kt` in `companion/bridge/src/course-speech.ts`: each step's examples, its quick question and the
sentence of its answer, the Slovene runs of its explanation and of the summary (read when the explanation's one clip
can't be had), for the learner's base language (the profile's; `--base` names others). The app's check for the language
learned is ported too (`Spoken.looks`, a clip of the text on the node in any voice, a family recording), and so is who
teaches (the school's person, `Residents.brings(SCHOOL)`: Mojca in Primorska, in her speaker `teacher`, or `@mojca` once
she has her own voice). `bridge/test/fixtures/intro-speech.json` holds inputs and what is read from them; the app's
`IntroSpeechParityTest` and the bridge's smoke (`course-speech`) both check it, so the two can't drift: a change to how a
step is read goes into both and the fixture. The explanations themselves (in the learner's own language, ~29,000
characters for the 18 units in English with their summaries) stay on demand, as the app asks for them when an intro
opens (above); `voice-build --explanations` makes them ahead too, ElevenLabs only. `voice-build --only teacher` plans and
makes her texts alone: the course, her villager lines, her arrival and scene lines.

**The stage.** Jan: Mojca "seems not animated, looks like just a picture"; she "points at what she explains, not a walk
across the screen", and he chose a small hand at the target over a long pointer (a kazalka). Each step is her stage:
Mojca at the top as big as the dialogs' stage portraits, beside her the step and its 🔊 🐢 and her speech bubble with the
step's explanation (its tail toward her; Jan, 2026-10-14: the room at her right was "a bit wasted", the explanation
shown twice, in a small bubble and on a board below): what is read marked in it, the bubble outlined while its one clip
plays; a long explanation scrolls within the bubble (at most about a third of the screen), kept at what is read. Below
the stage the examples, the table and the quick question, scrolling under it. On a short screen or with a large font
the compact stage: Mojca small, a line of what she says now beside her, the explanation on a board below; with the
keyboard up she is small and her bubble a few lines (the content under the stage stays put). A quick question's answer
(right or not, the sentence, the why) scrolls into view as it appears. She breathes and blinks, talks while her voice plays (also a 🔊
of hers on the school's screens), and a small pointing hand (pixel-drawn as she is, her sleeve's cuff on it) shows what
is read, in step with what is marked (`ui/school/IntroPointer.kt`), right beside it: at its right end, astride the edge,
the fingertip pointing in, never over its text, no line across the content:

- a Slovene form of the explanation beside the table's row that says it ("jaz sem": the row jaz · sem, marked too; "smo,
  ste, so": the three rows of those forms), the translation in brackets after it ("(I am)") still there; a form no row
  has ("Biti") and the rest of the explanation at her bubble's right edge, level with the words read (on the board, at
  its right edge); the explanation's one clip beside her bubble;
- an example beside its card; the quick question beside its card, level with its prompt; the answer's sentence beside
  the question's card, level with it.

She turns toward what she explains, her arm held out that way, and back to the learner (the book back in hand) a moment
after she starts asking the question. The hand hops to the next target in a quarter of a second, follows the content as
it scrolls, and fades when its target is scrolled out of sight. Two fun moments, not constant motion: the lesson begins
with her walking in from the side into her place (about a second, the reading after it), and she cheers when the
learner passes the unit's test (the test's end, arms up and a jump, then a smile); a right quick answer gets a smile.
Nothing moves while the learner types (the keyboard up: the stage small, she holds still, no hand) or answers. With the
system's "remove animations" on: no walk-in, no hand, she holds still; what is read stays marked.

"🔊 Beri razlago · Read the explanations" (on by default; in the school and in the settings' sound section): off,
nothing plays by itself, the step's 🔊 still reads. Nothing plays by itself while TalkBack is on (it reads the text).
The day's audio (README "Offline on the phone") gets the next units' introductions ready on Wi-Fi: the unit under way
or next and the one after it, their explanations' clips and their Slovene in Mojca's voice, so a unit plays offline.
A homework's introduction (`publish_worksheet` with an `intro`) reads the same way.

Writing a step for it: put every Slovene form in bold, italics or „quotes" (it is read in Mojca's voice; English in
bold stays English), give a form's meaning in brackets right after it, and keep emoji out of `say` (they aren't read).

## Worksheets and the test

```json
"worksheets": [
  { "id": "biti-1", "title": { "sl": "Pri babici Micki", "en": "At Granny Micka's", "de": "…", "it": "…" }, "exercises": ["…"] }
],
"test": { "pass": 80, "exercises": ["…"] }
```

- A worksheet: `id` (kebab-case, unique in the unit; never `test`), `title` (per language: the language learned and
  English at least), `exercises` (1–8): every exercise type of
  a module (`flashcard`, `choice`, `cloze`, `reorder`, `translate`, `free`, `multi`, `scenario`, `dictation`, `speak`:
  `.claude/skills/lani-studio/reference/module-spec.md`) and the school's own four (below). An optional `intro` (as the
  unit's) comes before its exercises.
- The test: `pass` (50–100, default 80): the share of its items to get right; `exercises` (2–12), the same types. An item
  is one gap, one cell, one line of a transform list, one word sorted, or one other exercise; an almost-right item (a
  missing č, š, ž) counts half.
- Help is always there: the cheat sheet one tap away (📋 over the sheet, the unit's cards first; the items answered
  after it opened pay in full and are right with help, `looked_up` per item in the session_end), the sheet's words
  looked up as an exercise's (never what it tests while it's open: a sort's words, what brackets add, only once
  checked), a 💡 hint per gap, cell and line, and 💬 the tutor with the sheet attached. An item's `explain` shows in
  shape as an exercise's (module-spec.md, "Explanations": one bold rule line, the examples as a list).

## Texts in the learner's language

A course is for every base its learners read (Jan's Slovene from English; Teo's Italian from German, and the Italian
course for a learner from Slovene). What an exercise says in the learner's own language may be one string, as the
Slovene units write it (English), or a text per base, `{"de": …, "sl": …, "en": …}` (English always), as the Italian
units do:

| Key | What |
|---|---|
| `explain`, `hint` | an exercise's, a gap's, a cell's, a line's why and hint |
| `instruction` | the label "target · base": `{"de": "Completa · Ergänze", "sl": "Completa · Dopolni", "en": "Completa · Fill in"}` |
| `translation`, `cue`, `prompt` | a gap text's meaning, a transform's cue, a prompt in the base (a choice's "Am Morgen, zur Lehrerin:") |
| `label` | a table row's label, a sort column's (`"con tu · zu einem Kind"`) |
| `columns` | a table's column headings, each a string or a text per base |

The bridge gives the learner each text in their base (`CourseStore`'s base, the profile's: `GET /course`; else in
English), and so does the app reading its bundled copy (`Courses.inBase`); the prints take the sheet's base. The intro's
`say`, the titles, the goals and the examples are per language as before and stay so. A text per base without its
English is refused (`validateUnit`, `basedErrors`). A unit whose texts are per base prints its hints, whys and
instructions on a sheet of any base (a Slovene unit's English ones only on an English sheet).

## The school's exercise types

Four types for a page of work at once. Each names its rule like any exercise (`grammar`, a page id); an item may name its
own (`grammar` on a gap, a cell, a line). Graded on the phone with the app's rules (`Grading`: č, š, ž forgotten is
almost; a slip in the stem forgiven; never the ending), each item marked right (✓), almost or wrong with its correction
right where it was answered once the sheet is checked: what was typed set against what is right, word by word and letter
by letter ("Spi~~ve~~**ta** v šotoru.": the typed letters that are wrong struck through, the right ones green and bold),
and its why (`explain`; an almost's slip: a missing č). Under the sheet only the count ("3 od 8 narobe, 2 skoraj"), a
tap on it back at the first one not right. They are worksheet exercises (in a unit, a test, homework); modules will take
them once every phone has an app that plays them.

### gaptext: a text with gaps

```json
{
  "type": "gaptext",
  "grammar": "biti",
  "instruction": "Dopolni z glagolom biti · Fill in with biti",
  "title": "Pri babici Micki",
  "text": "Danes ___ pri babici Micki. Babica ___ v kuhinji, otroci ___ na vrtu.",
  "gaps": [
    { "accept": ["sem"], "hint": "jaz → ?" },
    { "accept": ["je"], "hint": "ona → ?" },
    { "accept": ["so"], "hint": "oni → ?", "explain": "otroci = oni (three or more): so." }
  ],
  "bank": true,
  "distractors": ["si"],
  "translation": "Today I'm at Granny Micka's. Granny is in the kitchen, the children are in the garden."
}
```

- `text`: a few sentences (at most 600 characters), each gap `___` (three underscores or more); 2–12 gaps.
- `gaps`: one per `___`, in order: `accept` (the right answers, the first shown as the correction), `hint`, `explain`,
  `grammar`.
- `bank` (default false): a word bank under the text, each gap's first answer, shuffled, plus `distractors` (0–6, only
  with a bank). A word of the bank is tapped into the gap being filled; typing works too.
- `translation` (optional): the text's meaning in English, shown after checking.
- Checked all at once ("Preveri · Check"): each gap right, almost or wrong, the corrections of a sentence's gaps on a
  line under the sentence (its 🔊 the sentence said right).

### table: conjugate or decline

```json
{
  "type": "table",
  "grammar": "biti",
  "instruction": "Dopolni tabelo · Fill in the table",
  "prompt": "biti (sedanjik) · to be (present)",
  "lemma": "biti",
  "columns": ["ednina · singular", "dvojina · dual", "množina · plural"],
  "rows": [
    { "label": "1. oseba", "cells": ["sem", { "accept": ["sva"], "form": "pres.1du" }, { "accept": ["smo"], "form": "pres.1pl" }] },
    { "label": "2. oseba", "cells": [{ "accept": ["si"], "form": "pres.2sg", "hint": "ti …" }, "sta", { "accept": ["ste"], "form": "pres.2pl" }] }
  ]
}
```

- `columns`: 1–4 headings; `rows`: 1–12, each a `label` (jaz, ti …; or a case with its question, "1. kdo? kaj?") and one
  cell per column.
- A cell is given (a string, shown as it is; "—" for none) or to fill (`{"accept": [...], "hint", "explain", "grammar",
  "form"}`); at least one to fill.
- `lemma` and a cell's `form` (the forms book's key: `pres.1sg`, `neg.3pl`, `past.f.sg`, `loc.sg` …): the form practised
  is the learner's word's too, and a wrong one comes back first in its review (GAME.md, "A word's forms").
- Graded cell by cell; the grammar page's table is one tap away (📖).

### transform: a list to change

```json
{
  "type": "transform",
  "grammar": "zanikanje",
  "instruction": "Zanikaj · Say it with no",
  "cue": "ne",
  "items": [
    { "from": "Sem doma.", "accept": ["Nisem doma."], "hint": "sem → nisem" },
    { "from": "Babica je v kuhinji.", "accept": ["Babica ni v kuhinji."], "lemma": "biti", "form": "neg.3sg" }
  ]
}
```

- `instruction`: the one instruction for the whole list ("Postavi v preteklik · Put it in the past", "V množino · Into the
  plural"); `cue` (optional): shown between each line and its answer ("(preteklik)").
- `items`: 2–12, each `from` (what is given), `accept` (the whole answer: a sentence or a word), `hint`, `explain`,
  `grammar`, and `lemma`/`form` as in a table.
- Each line typed whole, graded on its own.
- **The person in brackets may be left out** (Slovene and Italian drop the subject; the language's grading table,
  `drop_subject`): when a line has no subject of its own and its answer begins with the person in brackets
  (`"Pečem kruh. (babica)"` → `"Babica peče kruh."`), the answer without it is right too (`"Peče kruh."`), and with the
  line's own word order (`"Učim se brati. (Tine)"` → `"Uči se brati."`, as `"Se uči brati."`). Not when the bracket is
  what the line is to take in (`"Pomagam. (lahko)"` → `"Lahko pomagam."`) or replaces a word of the line
  (`"Kruh je svež. (mleko)"` → `"Mleko je sveže."`): the answer must have it. `"whole": true` on the list asks for every
  word whatever the line (an instruction that says so: "Napiši cel stavek · Write the whole sentence"), and is what a
  list needs whose bracket goes in front but isn't the person while the verb changes (`"Grem domov. (jutri)"` →
  `"Jutri pojdem domov."`: the app can't tell it from a person).

### sort: words into columns

```json
{
  "type": "sort",
  "grammar": "spol-samostalnikov",
  "instruction": "Razvrsti po spolu · Sort by gender",
  "columns": [
    { "label": "moški · masculine", "words": ["kruh", "stol", "brat"] },
    { "label": "ženski · feminine", "words": ["miza", "hiša", "sestra"] },
    { "label": "srednji · neuter", "words": ["okno", "mleko", "jajce"] }
  ],
  "explain": "Most masculine nouns end in a consonant, feminine in -a, neuter in -o or -e."
}
```

- `columns`: 2–4, each a `label` and the `words` that belong there (1–12); 4–16 words in all, none twice.
- Played by tap: a word, then its column (a word placed can be taken back); "Preveri · Check" marks each word.

## Homework (lani.worksheet/v0)

The tutor adds worksheets to a unit (`publish_worksheet`): homework after a unit, an extra sheet on what the learner
keeps getting wrong. They show in the unit under "🧑‍🏫 Domača naloga · Homework", the newest first, and pay like the
unit's sheets; they don't hold the unit's test or the level back.

```json
{
  "schema": "lani.worksheet/v0",
  "unit": "biti",
  "id": "biti-domaca-1",
  "title": { "sl": "Domača naloga: biti", "en": "Homework: biti", "de": "…", "it": "…" },
  "intro": { "steps": ["…"] },
  "exercises": ["…"]
}
```

`unit` names a unit of the course that isn't planned; `id` is unique in it and isn't one of the unit's own worksheets'
(nor `test`; publishing the same id again replaces it); `language` (optional: the village's); `intro` (optional) and
`exercises` (1–8) as a unit's worksheet. The bridge marks it the tutor's (`source: "tutor"`,
`machine_written: true`) and tells the app (`worksheet_published`).

## The learner's progress

The village state keeps it (`GameState.course`, versioned; GAME.md "The school"): when the class began, each
unit's place in its intro, each worksheet's best result, the test's tries and best, the day the unit was done (or tested
out), the course levels opened. The bridge reads it for the tutor (`get_course`, `get_village`'s `course`). As the app
saves it (a field left out is its default: null, 0, false, empty):

```json
"course": {
  "v": 1,
  "classroom": "2026-10-13",
  "opened": { "sl/A1": "2026-10-13", "sl/A2": "2026-12-02" },
  "units": {
    "sl/biti": {
      "started": "2026-10-13",
      "intro": 4,
      "introDone": true,
      "sheets": { "biti-1": { "right": 11, "almost": 1, "of": 12, "day": "2026-10-13", "tries": 1 } },
      "test": { "right": 9, "almost": 0, "of": 10, "day": "2026-10-14", "tries": 2 },
      "done": "2026-10-14",
      "testedOut": false
    }
  }
}
```

- `classroom`: the day the class began (the village a hamlet; null: not yet, or a village whose school stood before the
  school's course came, where the class is indoors from the start).
- `opened`: the course levels opened, by `"<language>/<level>"`, the day each opened; a level once open stays open.
- `units`, by `"<language>/<unit id>"`: `intro` the intro's steps done, `introDone` finished or skipped; `sheets` each
  worksheet's (and homework's) best try, by its id (`right`, `almost`, `of` items, its `day`, `tries`); `test` the best
  try of the test and its `tries`; `done` the day the test was passed; `testedOut` passed straight away, without the
  worksheets.

### What the school shows

Jan, 2026-10-27, as A2 (14 units) and B1 (16) were outlined: "no need to show it all directly". The school's screen
shows a level a bit at a time (`School.reveal`, pure; `game/SchoolRevealTest`):

- **An open level**: its units done and begun, the current one (its first written unit not done: ★ in the level the
  course is at) and the next two written units not done after it (`School.AHEAD`), and any unit with the tutor's
  homework, as rows in the course's order. A learner who jumped ahead keeps every unit done or begun in view. The rest is
  folded into one quiet line under the rows, "📝 Še 9 enot v A2 · 9 more units in A2", whose tap lists them read-only
  (each with its picture, title and 🖨️: a written one "○ Pozneje · Later", a planned one "📝 Kmalu · Coming"). A planned
  unit is never a row.
- **A level done** (every written unit done): one line, "✅ 18 opravljenih enot · 18 units done", whose tap shows its
  units (to open one again) and hides them again; its planned units, if any, folded as above.
- **The first closed level** after the open ones: what opens it, each need with its way on (as before), and every unit
  of it folded ("📝 14 enot v A2 · 14 units in A2", "🔒 Zaklenjeno · Locked" in the list). The levels after it aren't
  shown until it opens.

- **Nothing written left to do, more planned** (every written unit of the open levels done, or none written yet: the
  Italian course's first days): the teacher says she is preparing the next lessons, kindly, under her welcome
  (`School.preparing`; "Pripravljam naslednje ure. Kmalu se spet vidiva!", Maestra Elena's "Sto preparando le prossime
  lezioni. Torna presto!"), and Home's tile says "📝 Nove ure kmalu" instead of "✅ vse opravljeno".

The rules that open a level are unchanged (`School.isOpen`: A1 with the hamlet; A2 with the school standing and A1's
written units done, i.e. every A1 test passed; B1 and B2 with the school's levels 2 and 3 and the level before done; a
level once open stays open). The order stays free: a folded unit is one tap away once it comes into view, and testing out
of a unit ("⏩ Znam že") brings the next one in. Each level's booklet (🖨️ Vsi listi) stays under it. QA:
`companion/bin/qa --steps school-reveal` (its hook `school:reveal`: the school standing, A1 done, A2 begun).

### What a unit turns on

A unit's intro done (finished, or skipped: "⏭ Poznam to · I know this") may turn on something beyond the unit, from
then on and for good; the intro's end says so once, as a note ("Slovene · English").

- **`stevila`** (A1, "Ena ovca, dve ovci, pet ovc"): **counting cards** in the reviews ("🔢 Štetje · Counting";
  README.md "Counting cards"): a card per noun the learner has learned, a few new a day, made from the dictionary's
  forms, that counts it 1 (en / ena / eno), 2 (the dual), 3–4 (the plural) and 5+ (the genitive plural). The note:
  "🔢 Kartice za štetje so odslej v tvojih ponovitvah. · Counting cards are in your reviews from now on." A learner
  who never did the unit's intro gets none (the course is a recommended order, free to jump: the intro of `stevila`
  done out of order turns them on all the same). The cards' help opens the unit's pages `stevila-samostalniki` and
  `spol-samostalnikov` on the cheat sheet; the tutor points to the unit's intro and worksheets for a kind of mistake
  that keeps coming back. It also opens **the numbers module** ("🔢 Števila · Numbers"; README.md "Numbers"): runs of
  numbers heard and typed on a number pad, read, written and said, and a date's ordinal (its page `datumi`), the ranges
  growing from 0–10 and 11–20 (the two the intro opens) to the years, each opening when 8 of the last 10 answers in the
  newest are right; on the unit's page ("🔢 Števila", under the intro) and on Home. Before the intro the unit's page
  shows it closed ("🔒 Odpre jih uvod enote.") and the module's screen leads to the intro. The note: "🔢 Števila so
  odprta: vadi jih pri enoti in na domači strani. · The numbers are open: practise them at the unit and on Home."
- **`kdaj-cas`** (A1, "Kdaj? Ob petih, v sredo, pozimi"): the numbers module's **clock times**, by ear ("ob pol šestih"
  → 5:30 on the pad) and in words (5:30 → ob pol šestih), with the unit's ob + -ih and half towards the next hour. The
  note: "⏰ Ura je odslej tudi v vaji s števili. · Clock times are in the numbers practice from now on." Before it, the
  module's screen shows them closed with the unit one tap away.

**Forms cards** ("📋 Oblike · Forms"; README.md "Forms cards") are on for every learner whose bridge makes them; what
each card asks grows with the units. A form of a word's table is asked once its grammar page and the pages it needs are
introduced: met in the grammar book, or listed in the `grammar` of a unit whose intro is done (the intro's end meets
them anyway). Until then it is greyed with its form, to read ahead, in a table the card shows (a review shows only the
tables with a gap asked now) and in the word's overview ("📋 Vse oblike"). A unit's intro done also brings back within days the
forms cards it opened rows of (at most 3 a day, its new part first, scheduled as new learning: the card's repetitions
back to 0, its history kept), says so ("📋 Nove oblike v tabelah: delati, hiša. · New forms in your tables: …"), and
makes the day's new cards (two a day) of learned words that had none yet. By the units' pages (A1, A2, B1):

| unit | its pages | what the forms cards ask from then on |
|---|---|---|
| `biti` | biti, zanikanje, dvojina | biti's present (sem, si, je …) and its negative (nisem …); the dual of what is introduced (a noun's nominative dual: dve hiši; the dual of a tense or case once its page is) |
| `spol` | spol-samostalnikov, pridevniki-ujemanje | an adjective's nominative singular (dobra, dobro), its plural with `mnozina`, a case of it with that case's page |
| `osebni-zaimki` | ti-vi, biti, dvojina | as `biti` |
| `sedanjik` | glagoli-sedanjik, dvojina | the present of every verb but biti, imeti and iti (delam, delaš … delava) |
| `mnozina` | mnozina, pridevniki-ujemanje | a noun's nominative plural (hiše); with a case's page, that case's plural; an adjective's plural |
| `tozilnik` | tozilnik, pridevniki-ujemanje, mnozina | a noun's accusative row (hišo, hiše, hiši) and the adjective's accusative |
| `stevila` | stevila-samostalniki, datumi, dvojina | the dual (and counting cards, above) |
| `imeti-iti` | imeti-iti, kam-tozilnik | imeti's and iti's present (imam, grem …) |
| `nikalnica-rodilnik` | zanikanje, rodilnik-nikalnica | the negative present (nisem, nimam); not the genitive row: that is `rodilnik`'s, A2 |
| `mestnik` | kje-mestnik-orodnik, mestnik | a noun's locative row (hiši, hišah) and the adjective's locative |
| `velelnik` | velelnik, ti-vi | the imperative (delaj, delajte, delajmo; delajva, delajta with the dual) |
| `pretekli-cas` | pretekli-cas, naslonke | the past's l-forms (on je delal, ona je delala …) |
| `prihodnjik` | prihodnjik, naslonke | the future (bom delal, bodo delali …) |
| `dvojina` | dvojina, stevila-samostalniki | the dual |
| `skloni` | skloni, imenovalnik | nothing new: a noun's nominative singular is the word itself, never asked; the other cases' rows come with their units |
| `rodilnik` | rodilnik, rodilnik-kolicina, rodilnik-predlogi | a noun's genitive row (hiše, hiš; dveh hiš with `dvojina`) and the adjective's genitive (velike, velikega) |
| `kje-kam-od-kod` | kam-tozilnik, kje-mestnik-orodnik, rodilnik-predlogi | nothing new: its accusative, locative and genitive rows are `tozilnik`'s, `mestnik`'s and `rodilnik`'s |
| `dajalnik` | dajalnik, osebni-zaimki | a noun's dative row (hiši, hišam, hišama) and the adjective's dative (veliki, velikemu) |
| `orodnik` | orodnik, kje-mestnik-orodnik | a noun's instrumental row (hišo, hišami, hišama) and the adjective's instrumental (veliko, velikim) |
| `mnozina-skloni` | mnozina-skloni, mnozina, rodilnik-kolicina, stevila-samostalniki | nothing new: a case's plural row (hišah, hiš, hišam, hišami) comes with that case's page and `mnozina`, as `mestnik`'s does at A1, so the A2 case units before it open them; its tables and transforms name the forms (`gen.pl` … `ins.pl`) |
| `pridevniki-skloni` | pridevniki-ujemanje, skloni | an adjective's cases, each once its case's page is introduced (all six by the units above) |
| `cigav` | cigav, pridevniki-ujemanje | as `spol` (the adjective's rows); the possessives aren't in the forms book |
| `primernik` | primernik, prislovi | an adjective's comparative (lepši, boljši, večji: `cmp.m.sg`) |
| `glagolski-vid` | glagolski-vid, pretekli-cas | nothing new: the past's l-forms are on since A1 (`pretekli-cas`); a verb's aspect and its partner show in the word lookup for every learner |
| `sklanjatev-posebnosti` (B1) | sklanjatev-posebnosti, sklanjatev-moski, sklanjatev-srednji, sklanjatev-zenski | no form of a table of its own: the odd nouns' forms (očeta, psa, ljudmi) are asked with each case's page, as any noun's |
| `dvojina-skloni` (B1) | dvojina-skloni, dvojina | the dual of every case whose page is introduced (dveh bratov, dvema sestrama, pri dveh oknih) |
| `vid-vsi-casi` (B1) | vid-casi, glagolski-vid, velelnik | the imperative (as `velelnik`) |
| `svoj` (B1) | svoj, pridevniki-ujemanje | an adjective's agreement (as `spol`) |
| `dolocna-oblika` (B1) | dolocna-oblika, pridevniki-ujemanje | as `svoj`; the definite form (zeleni) isn't a form of the table |
| `oziralni-ki` (B1) | oziralni-ki, naslonke | no form of a table |
| `odvisni-govor` (B1) | odvisni-govor, naslonke | no form of a table |
| `odvisniki` (B1) | odvisniki, vezniki, pogojnik, naslonke | no form of a table (the conditional isn't asked as a form) |

`vprasalnice`, `kdaj-cas`, `modalni-glagoli` and `povratni-glagoli` turn on no form of a table, nor do A2's
`zaimki-skloni` (osebni-zaimki, naslonke: the pronouns aren't in the forms book), `namenilnik` (namenilnik,
modalni-glagoli: the supine has no slot), `pogojnik` (pogojnik, naslonke: the conditional is bi and the past's
l-form) and `vezniki` (vezniki, naslonke). The genitive, dative and instrumental rows stay greyed until their pages are
met: in the book, or by A2's units `rodilnik`, `dajalnik` and `orodnik` (the overview `skloni` shows each case's
singular to read ahead and turns none of them on); a noun's nominative singular is the word itself, never asked. A
unit's `grammar` decides: a unit that lists another page turns on that page's forms too. The bridge's smoke keeps this
table true (`test/smoke/course-content.ts`): a unit that names a page a form counts on (`forms.ts` `pageOf`) has its
row here, with its pages as the unit lists them.

B1's second half (`pogojnik-polno` to `glagolniki`) teaches pages of its own, written with it (each a B1 page with
its cheat-sheet card): `pogojnik-ce-bi`, `nedolocni-zaimki`, `naslonke-red`, `stevila-glagol`, `cas-izrazi`, `custva`,
`trpnik`, `glagolniki`. None of them is a row of a word's table (a clause's bi, the order of the short words, the verb
after a number, a case of time, a feeling's dative, a participle or a verbal noun are not one word's forms); what the
units turn on comes from the earlier pages they list:

| unit | its pages | what the forms cards ask from then on |
|---|---|---|
| `pogojnik-polno` | pogojnik-ce-bi, pogojnik | no form of a table |
| `nedolocni-zaimki` | nedolocni-zaimki, zanikanje | the negative present (nisem, nimam, ne delam), as `nikalnica-rodilnik` |
| `naslonke-red` | naslonke-red, naslonke | no form of a table |
| `stevila-glagol` | stevila-glagol, stevila-samostalniki, rodilnik-kolicina | no form of a table |
| `cas-izrazi` | cas-izrazi, datumi, kdaj-cas | no form of a table |
| `custva` | custva, dajalnik, tozilnik | a noun's dative and accusative rows (bratu, hiši; brata, hišo), their plural with `mnozina`, their dual with `dvojina`; the adjective's dative and accusative |
| `trpnik` | trpnik, pridevniki-ujemanje | an adjective's nominative (dobra, dobro), as `spol` |
| `glagolniki` | glagolniki, sklanjatev-srednji | no form of a table |

`trpnik` and `glagolniki` are B1's "know" items (docs/grammar-syllabus.md: recognise and understand, produce a
little): their sheets ask mostly what a form means, which verb it comes from and which ending it has (a choice, a
multi, a sort, a word bank), and only the easy ones are made (Okno je odprto, plavanje, pečenje kruha).

## Writing a unit

- **Natural Slovene at the level**: short sentences of everyday words the learner meets in the village (the kitchen, the
  field, the school, the people: Micka, Luka, France, Mojca …); `{learner}` for the learner, `{m:…|f:…}` for what agrees
  with them (SCENES.md, "The learner in the content").
- **One thing at a time**: the unit's rule, with what came before. Use only grammar of earlier units in the sentences
  around a gap (the outline's order).
- **The intro**: 3–6 steps, each one idea (the singular, then the plural, then the dual, then the negative), one quick
  question each.
- **The worksheets**: two to four, from easy to harder: a sort or a table first (the forms), then a gap text (the forms in
  sentences), then a transform list (making the forms). 15–30 items in all.
- **The test**: 8–15 items of every kind of the worksheets, new sentences, pass 80.
- **Hints and whys** in English, as a module's (the app shows them as written), short: the rule, never the answer.
- **Flag it**: `machine_written: true`, `review: "machine-written, not reviewed by a native speaker"`, until a native
  speaker checked it.
- **Validate**: `cd companion/bridge && bun test/smoke.ts` (the course section validates every unit, its pages and its
  outline).

### Craft notes (from biti and spol)

- **One gap a sentence** where it can be: a sentence's corrections come right under it (a long sentence of many gaps
  pushes its corrections far from the first), and a slip is kept with that sentence on the rule's page ("Babica ___ v
  kuhinji.").
- **A cue in brackets after the gap** when the word to change is given: `"To je ___ (moj) hiša."`; a transform list's
  noun to put in: `"To je moj brat. (sestra)"`.
- **A table's labels short** (`"jaz · midva · mi"`, `"1. kdo? kaj?"`) and its columns two-language (`"ednina · one"`):
  TalkBack reads a cell as its row's label and its column's.
- **Name the form** of a table's cells and a transform's lines (`lemma` + `form`) when the word is one the learner may
  have as a card (biti, imeti, a noun of the packs): a wrong one then comes back first in the card's review. The keys are
  the forms book's: `pres.1sg` … `pres.3du`, `neg.*`, `fut.*`, `past.m.sg` … `past.f.du`, `imp.2sg` …, the cases
  `nom|gen|dat|acc|loc|ins` `.sg|.pl|.du`, `adj.m.sg` ….
- **Several right answers**: list every one in `accept` (with and without the pronoun: `"Nisi lačen."`, `"Ti nisi
  lačen."`); case and the final full stop don't matter, č/š/ž forgotten is almost. A transform's person in brackets
  added in front of a line without a subject may be left out without listing it (above); one that replaces the line's
  subject (`"One so kuhale. (onidve)"`) needs both listed (`"Onidve sta kuhali."`, `"Kuhali sta."`).
- **The word bank** for the first gap text of a unit (easier), none for the second and the test.
- **The intro's table** is the rule's table built row by row: give the head once (`intro.table.head`) and each step's
  new rows; a step without rows (an agreement step) only talks and asks. The last step is the exception or the twist.
- **The test** mixes the worksheets' types with new sentences: about 10–13 items, `pass` 80.
- **The outline** (`course.json`): when a unit is written, remove its `"planned": true` and its `grammar` hint stays a
  hint; the A1 units planned are, in order: osebni-zaimki, sedanjik, mnozina, tozilnik, vprasalnice, stevila,
  imeti-iti, nikalnica-rodilnik, mestnik, kdaj-cas, velelnik, modalni-glagoli, povratni-glagoli, pretekli-cas,
  prihodnjik, dvojina (each with the grammar pages it is to teach). A unit may only use the grammar of the units before
  it around its gaps: biti and spol first, then the pronouns and the present, so the cases come after sedanjik.

### The cases (A2)

Jan, 2026-10-27: "so there are no school lessons for cases? (one for each case + maybe a PDF … as a compact overview)".
A1 has the accusative (`tozilnik`), the locative (`mestnik`) and the genitive after a no (`nikalnica-rodilnik`); A2 opens
with the rest, in this order: `skloni` (the six cases by their questions, each one's singular: the method, and the
overview to print, "Printed sheets"), `rodilnik` (of, amounts and five and more, iz/brez/zaradi, od … do, blizu,
mimo, bati se), `kje-kam-od-kod` (one place, three questions: v → iz, na → z / s, pri → k → od, doma → domov → od
doma), `dajalnik` (komu?, giving and telling, pomagati, k/h, mi/ti/mu/ji, mrzlo mi je), `orodnik` (s kom? s čim?, s or
z, z mano, pod/nad/pred/za/med for kje? and kam?), and after the plural's unit `pridevniki-skloni` (an adjective through
the six cases, singular and plural). Writing one:

- **Ask the question** in a gap's hint ("komu?", "s čim?", "pri kom?"): the method `skloni` teaches, never the answer.
- **The word in brackets** is the noun or adjective as the dictionary has it. The smoke checks that each such answer is
  one of its forms (the dictionary's; a name, a pronoun or an adverb made of the word isn't judged) and that each named
  form (`lemma` + `form`) is its slot's (`test/smoke/course-content.ts`).
- **The little word decides** where it can: k → h before k and g (h kovaču), z → s before p, t, k, f, h, c, č, s, š
  (s pošte, s kladivom).
- **Names decline**: Muri → Muriju, z Murijem; Luka → Luki, z Luko; France → Francetu, s Francetom; Nejc → Nejcu,
  z Nejcem (-em after c).

## Printed sheets

Jan, 2026-10-14: "Could you design A4 prints for each school topic? So one can use them as a reference in the future
and/or learn them on the way."

`companion/bin/school-prints` prints a level of the course: an A4 portrait sheet per unit (one page; two at most) and
a booklet of all of them (a cover, the contents with page numbers, the sheets in the course's order, page numbers in
the footer). The sheets are made from the content as it is, nothing written for them, so a unit a native speaker fixed,
or a new one (A2 …), prints as it is the next time:

```
companion/bin/school-prints                      # A1, English: ~/.local/share/lani/prints/sl-A1-en/
companion/bin/school-prints --base de --png      # explained in German, with PNG previews of every page
companion/bin/school-prints --level all --out /tmp/prints
companion/bin/school-prints --units biti,spol --explain   # only these, and what each sheet took from where
companion/bin/school-prints --overview skloni --png       # the cases overview ("The cases overview" below)
```

Options: `--level` (A1 by default; `all`: every level that has units), `--base` (`en`, `de`, `it`: the language the
sheets explain in; the exercises' hints and the mistakes' whys are English only and stay off another base's sheet),
`--out` (by default `${XDG_DATA_HOME:-~/.local/share}/lani/prints/<language>-<level>-<base>/`; never inside the
repository), `--units`, `--learner` (the name for `{learner}`, Jan by default), `--gender` (`m`, `f`, or `both`, the
default: "utrujen/utrujena", and a sentence that needs one or the other left out), `--png` (previews with `pdftoppm`),
`--html-only`, `--chromium` (else `$LANI_CHROMIUM`, `chromium` on the PATH, or Playwright's in `~/.cache/ms-playwright`).
It writes `<out>/html/` (print-ready HTML, the fonts inside), a PDF per unit (`<out>/01-biti.pdf` …) and the booklet
(`<out>/lani-sl-A1-en.pdf`), renders them with headless Chromium (`--print-to-pdf`, no header or footer of its own) and
checks every PDF's page count against the layout chosen; a unit that doesn't fit on two pages fails the run.

What a sheet has, and where it comes from (`companion/prints/sheet.ts`):

| Part | From |
|---|---|
| The header | the unit's number in its level, its emoji, its title in Slovene and the base, its goal, the level; the Lani house |
| **Pravilo · The rule** | each step of the intro in a line or two: its first sentence and the next ones that carry forms (bold, an arrow, a colon) |
| **Tabela · The table** | the intro's table as its steps build it, or the first grammar page's when that shows the same forms in fewer rows (biti); one more of the unit's pages when it adds forms the unit uses (spol: the adjectives), the first thing left out to fit. Endings marked: by a column of endings, down a column of one word's forms, or against the form the cell is made from (kava → kav**o**, sem → **ni**sem); rows of a later level left out |
| **Primeri · Examples** | the intro's examples, the first of each step first, at most six; the table's forms in them bold |
| **Pazi · Watch out** | three wrong ✗ / right ✓ pairs: what a text says is wrong ("**Kje je Luka?** (never *Kje Luka je?*)", "Never *ne sem*", "*Vidim nič* is wrong"), a quick question's or a worksheet choice's wrong option in its sentence (never one marked `almost`: a spoken or near form), a gap whose word in brackets left unchanged is the mistake ("To je ___ (moj) hiša.") |
| **Besede · Words** | the unit's `words`, with gender and meaning: a pack's word, the dictionary's supplement, Wiktionary (the meaning the unit's own text points to: peti is "fifth" in the numbers); not the ones the table shows, nor names. A word nothing knows is left out (the run lists them) |
| **Preveri se · Check yourself** | five items of the worksheets (the test stays the app's): gaps in their sentence, transforms, choices, grouped by instruction; ten on a sheet of two pages. The answers upside down at the page's foot |
| The footer | "Strojno napisano, preveri z naravnim govorcem · machine-written, to be checked by a native speaker" while the unit's `review` says it's unreviewed; the Wiktionary attribution when a meaning comes from it |

The layout (`companion/prints/html.ts`, `sheet.css`): A4 with 12 mm margins, Nunito with the Lani mark in Pixelify Sans
(the site's fonts, inlined), tables at 11 pt or more, black-and-white safe (an ending is bold and underlined on its
tint, a mistake struck through with ✗, the right form bold with ✓). Each unit is written as one page and as two;
`fit.js` keeps, in the page before it prints, the first that fits: as it is, with the table beside the rule, tighter,
without the optional table and the sixth example, tightest; the room left becomes ruled lines for notes.

### In the app

Jan, 2026-10-14: "Can we have it in the app: for each lesson the relevant pages to print if wanted?"

Each unit has **🖨️ Natisni list · Print sheet**: on its screen under "💬 Vprašaj · Ask the tutor", and as 🖨️ on its
row in the school's levels and in a level's folded list (a locked unit's too: a sheet to learn from on the way). Each
level shown with written units has **🖨️ Vsi listi · All sheets** under its units: the booklet. The bridge makes the page with the generator's own code
(`companion/prints/build.ts`, which `school-prints` uses too: the same HTML, its CSS, fonts and `fit.js` inlined):

| Route | What |
|---|---|
| `GET /course/sheet/<unit>` | the unit's sheet (header `x-lani-print-name: 01-biti`) |
| `GET /course/booklet/<level>` | the level's booklet: the cover, the contents, every unit (`lani-sl-A1-en`) |

for the bridge's learner: in their base language (`en`, `de` or `it`; English for another), with their name for
`{learner}` and their gender's forms for `{m:…|f:…}` ("utrujen" for Jan, where the generator's default prints
"utrujen/utrujena"); `?base=`, `?learner=` and `?gender=m|f|both` say otherwise. The app token, as the other routes. A
planned or unknown unit, or a level without units, is a `404`; a base or a gender it doesn't know a `400`. The bridge
reads the course, the grammar book, the packs and the dictionary from where it serves them (`LANI_COURSE_DIR` …), makes
a document at its first request (a sheet in about 60 ms, A1's booklet in about 160 ms) and keeps it until a file of the
course, the grammar book (its pages' cards too), the word packs or the dictionary changes, or the print's own
`sheet.css`, `fit.js` or fonts (`contentStamp`: each file's name, size and time); a change to `sheet.ts` or `html.ts` is
code, and needs the bridge restarted.

The app (`ui/school/SchoolPrint.kt`) asks for the page in the learner's base, loads it into a WebView of its own that is
never on screen (JavaScript on for `fit.js`, the text zoom at 100 % whatever the phone's font size, no network: the page
stands alone), waits until `fit.js` has fitted every unit (`data-fitted="yes"` on `<html>`, as the generator waits for
in Chromium; 30 s at most), writes what it chose to the log (tag `LaniPrint`), and opens Android's print dialog with the
WebView's print adapter (`PrintManager.print`, A4 portrait, no margins of its own: the page's `@page` sets its 12 mm):
the preview, a printer, "Save as PDF", sharing. The job, and the PDF saved, is named "Lani – A1 – 01 biti" (the booklet
"Lani – A1 – vsi listi"). Without the tutor (none paired, or out of reach) the buttons are off and say why ("📶 Za
tiskanje mora biti mentor dosegljiv."); an older bridge without the routes: "the tutor doesn't have this sheet yet".

The WebView is Chromium too, and lays the page out as the generator's Chromium does: the same fonts (č, š, ž in their
latin-ext faces), the same millimetres, the same page breaks and margins (its PDF of biti is the generator's, row for
row). What differs is the screen: borders and lines snap to whole device pixels, and a phone has 2.625 or 3 of them to a
CSS pixel where the generator has one, so on the phone's screen the sheet came out a few pixels shorter (a hairline
0.4 px instead of 1 px, a table row 0.6 px shorter) while the WebView prints as the generator measures. `fit.js` then
chose otherwise on 1 of A1's 18 units (osebni-zaimki: its table under the rule, a layout that fits only on the screen)
and its "too long by" was 2 to 3 mm less. So `fit.js` measures as it prints: the root zoomed by `1 / devicePixelRatio`
while it measures (one device pixel to a CSS pixel), sizes read off a page's known width, the zoom gone before the print.
Every unit of A1 then fits alike in Chromium at 1, 2, 2.625, 2.75, 3 and 3.5 device pixels to a CSS pixel and in the
emulator's WebView (QA's `school-print` compares them); what is left is the phone's text, a fraction of a millimetre
(kdaj-cas's one page 15 mm too long there, 16 mm in Chromium, rounded). The WebView's minimum font sizes are 1 px, as
Chromium's (it raises text under 8 px; the sheet's smallest is 6.4 pt, 8.5 px).

### The cases overview

Jan, 2026-10-27: "so there are no school lessons for cases? (one for each case + maybe a PDF, this PDF, as a compact
overview)". The six cases on three A4 pages, "Šest sklonov · The six cases" (`companion/prints/overview.ts`), made by
the same generator from the grammar book's pages as they are, and from two units' tables where they hold what no page
has; nothing is written for it but its headings (Slovene and the base), so a page a native speaker fixed prints as it is
the next time:

| Part | From |
|---|---|
| **Šest sklonov · The cases at a glance** | the page `skloni`: a row per case from its table (the case, its question, its prepositions) and its card (the endings m · ž · s), what the case does (the first sentence of its example's note) and that example with the case's form bold |
| **Kako najdeš sklon · How to find the case** | `skloni`'s rule, a paragraph in a line or two (not the one on which level learns what), and its card's tip (left out first to fit) |
| **Števila · Numbers** | `stevila-samostalniki`'s table and what it says of the verb |
| **Kje? Kam? Od kod?** | the unit `kje-kam-od-kod`'s table once the course has it; before, the page `kam-tozilnik`'s |
| **Končnice · The endings** | `sklanjatev-moski`, `-zenski` and `-srednji`'s tables, the endings marked against the words (stol⟦om⟧, vas⟦jo⟧; oken and knjig as they are), each with what its page says beside it |
| **Pridevniki · Adjectives through the cases** | the unit `pridevniki-skloni`'s table once written (the endings marked against the base form) |
| **Osebni zaimki · Personal pronouns** | `osebni-zaimki`'s table, and when the short form is used |
| **Rodilnik, dajalnik, orodnik** | the tables of `rodilnik`, `dajalnik` and `orodnik`, in two columns |

Each table says where it comes from ("📖 Sklanjatve: moški spol · from the grammar book", "🏫 Kje? Kam? Od kod? · from the
school's unit"), the footer that it is machine-written while a page it draws on is unreviewed. `fit.js` fits it as it
fits a unit's sheet (tighter, then without its optional parts) and reports it (`{"unit": "skloni", "pages": 3, "layout":
"overview", "density": …}`); one that runs over gets a page saying so, which the generator's page count catches.

```
companion/bin/school-prints --overview skloni --png             # ~/.local/share/lani/prints/sl-skloni-en/pregled-sklonov.pdf
companion/bin/school-prints --overview skloni --base de --explain   # in German, and what each case's row took
```

The bridge serves it as `GET /course/overview/skloni` (`x-lani-print-name: pregled-sklonov`; `?base=`, `?learner=`,
`?gender=` as a sheet's; a page without an overview is a `404`), kept as the sheets are. The app offers it as
"🖨️ Pregled sklonov · Cases overview" under "🖨️ Natisni list" on a unit whose first grammar page is `skloni` (the unit
`skloni`), on the grammar book's page `skloni` under "🎯 Vadi · 💬 Vprašaj" (`Prints.overview`), and on the school's
level of that unit (A2) under "🖨️ Vsi listi", also while the level is closed (`Prints.overviewOf`); the print job and
the PDF saved are "Lani – Pregled sklonov".
