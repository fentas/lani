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
- [Texts in the learner's language](#texts-in-the-learners-language)
- [The school's exercise types](#the-schools-exercise-types)
- [Spoken forms](#spoken-forms)
- [Homework (lani.worksheet/v0)](#homework-laniworksheetv0)
- [The learner's progress](#the-learners-progress)
- [Writing a unit](#writing-a-unit)
- [The Italian course](#the-italian-course)
- [The German course](#the-german-course)
- [The English course](#the-english-course)
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
- `summary` (optional): the summary card's own words, per base (English always; Markdown, at most 600 characters each),
  instead of the unit's first page's rule: what a child's first lesson needs of a page that goes on further (the Italian
  `ciao`: tu and Lei in the greetings, where the `tu-lei` page goes on to the polite imperative). The card shows them with
  the table the steps built and "📖 V knjigo · To the book" for the whole page, and she reads them as she reads the page's
  rule (the bridge voices them ahead the same way). An older bridge refuses the key (the intro is a strict object): the
  unit goes out after the bridge.
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
     explanation names, "**cantare**"), or endings only, each the end of one of those words ("**-teen**" of thirteen,
     "**-o, -i, -a**" of canto, canti, canta, "**-ega**": `IntroSpeech.endings`);
   - each run marked in the explanation as it is read, a short pause between them.
   The spans read in her voice are the ones underlined in the explanation for a tap to hear (🔈), in her bubble, on the
   board, in a quick question's why and in the summary: the same check (`IntroSpeech.learned`), so "**si lava**" of the
   unit riflessivi, which only the intro's own words make Italian, is underlined as "**mi lavo**" is; a word of the
   base in bold is not (`IntroBoldTapTest`).
   The phone without a voice for the base language: only the Slovene is read, and a small note says why, once.
2. **The examples**, one after the other in Mojca's voice (their translations not read), each card marked.
3. **The quick question**, as far as it gives nothing away: a gap read as a pause ("Luka … na pašniku."), a choice's
   options never, a translation or a cue in brackets at its end left out, a full stop after it or not. Once answered, the sentence with the answer is shown and read
   (as an exercise's feedback), without a cue right after the gap either ("By the time the bus ___ (come), I'll have finished." → "By the time the bus comes, I'll have finished."), and Mojca reacts (a smile, a shrug). In Mojca's voice: a sentence with a gap to fill
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
- **A choice's options show in the day's order**, a multi's and an intro's quick question's too: the same through a day,
  another the next (`data/ChoiceOrder.kt`; docs/plans/05-built/choices-order.md). `answer`, `almost` and `answers` stay the
  file's indices and the file's order is never shown, so write the options in any order, and nothing may lean on it (no
  "both of the above").
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
- `gaps`: one per `___`, in order: `accept` (the right answers, the first shown as the correction), `spoken` (what a
  native speaker says too: right and labelled, [Spoken forms](#spoken-forms)), `hint`, `explain`, `grammar`.
- `bank` (default false): a word bank under the text, each gap's first answer, shuffled, plus `distractors` (0–6, only
  with a bank). A word of the bank is tapped into the gap being filled; typing works too. A distractor is a wrong form on
  purpose (`pesom` for `psom`, the e that drops; `sedli`, sesti's, for `sedeli`, the other aspect), so tapped or typed it
  is wrong, never a slip in the stem forgiven as almost (`Sheets.check`; `CourseContentTest`: none of the course's is right
  or almost for any gap).
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
- A cell is given (a string, shown as it is; "—" for none) or to fill (`{"accept": [...], "spoken", "hint", "explain",
  "grammar", "form"}`); at least one to fill.
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
- `items`: 2–12, each `from` (what is given), `accept` (the whole answer: a sentence or a word), `spoken`, `hint`,
  `explain`, `grammar`, and `lemma`/`form` as in a table.
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

### Spoken forms

Jan's household: the learner's wife is a native speaker and says what people say (*bojo* for *bodo*, *gremo* for
*pojdimo*, *a si* for *ali si*). A spoken form is never a mistake. A gap, a table's cell to fill and a transform's line
take `spoken`: the forms a native speaker says beside the standard answer, 1–8 of them, in the language learned.

```json
{ "accept": ["bodo"], "spoken": ["bojo"], "hint": "oni → ?" }
```

- Typed or tapped, a spoken form is right (✓, it pays as right) and labelled under it: "🗣️ Pogovorno, prav tako prav;
  knjižno: bodo · Spoken, and right too; the standard form: bodo" (`Sheets.check`, `ItemMark.standard`; the case
  trainer's label). A spoken form a č short is almost, labelled the same.
- The standard form (`accept`'s first) stays the answer: the correction of a wrong answer, the sentence said right, the
  printed sheets. A spoken form is never in `accept`, never a distractor (the bridge refuses one), never a choice's wrong
  or `almost` option.
- Write the spoken forms of a line whole: a transform line that changes two spoken words (*A si že jedu?* → *Ali si že
  jedel?*) lists each mix a learner may write (*A si že jedel?*, *Ali si že jedu?*), so a half-standard answer is right
  too.
- Name them in the intro or the page as well, standard against spoken (*bodo*, spoken *bojo*), so the learner knows
  both. The unit `govorjeni-jezik` (B2) teaches recognising spoken Slovene and marks no spoken form wrong.
- An older bridge refuses the key (the sheets are strict objects): a unit with `spoken` goes out after the bridge.
  `CourseContentTest` grades every spoken form right with the standard one as its label.

**What people say that writing doesn't want (`nonstandard`, in `grading/<language>.json`).** A second thing, on purpose not called
`spoken`, because its verdict is another:

| | an item's `spoken` (the unit) | the language's `nonstandard` (the grading table) |
|---|---|---|
| Is | what a native says too, a form the course accepts | a form that is said, and that the unit teaches writing without |
| Verdict | right, pays as right | almost, pays half, never wrong and never a slip |
| Where | one gap, cell or line, listed by the unit | every answer of the language, also in reviews, modules and a dialog's typed turn |
| Shows | "🗣️ Spoken, and right too; the standard form: …" | the correction with the standard form and "People say it like this; in writing it's the form shown" (`Hint.NONSTANDARD`, the tutor sees `hint: nonstandard`) |

```json
"nonstandard": [
  { "word": "good", "for": "well" },
  { "word": "slow", "for": "slowly" },
  { "word": "nothing", "for": "anything", "after": "(?<!\\p{L})(?:not|cannot|never)(?!\\p{L})" }
]
```

- `word` (a regular expression, matched whole in the answer as `Grading.fold` writes it) stands where the right answer has what `for`
  matches, the rest being the same: "He sings good" for He sings well; one way only, so "a well blacksmith" stays wrong.
- `after` (optional): the text before it must have this: the answer's own words and, for a gap, the sentence before it
  (`Grading.check`'s `before`). "I didn't see ___": nothing is almost; "Is there ___ to eat?": nothing stays wrong.
- A choice never offers such a form as a wrong option (nor a bank as a distractor: a form the sheet offers is wrong, `Sheets.check`): use
  a wrong form nobody says, or a typed gap or a line. Name both in the intro and on the page: people say it, writing wants the other,
  not "never".
- **German** has `weil` for denn, `trotzdem` for obwohl, `wie` for als, `wann` for wenn, `täte` for würde, `Auf was` for worauf (and the rest), `auf ihn` for darauf for a thing, `wer`, `was`, `nix` for jemand, etwas, nichts. An item's `spoken` still wins where a unit lists one: `als-waehrend` keeps `wie` and `wann` right, as its point is als against wenn, not the word itself. **Which of the two:** a form that is the very thing the unit drills goes in the table, or every line of the drill passes as right; a form beside the point stays `spoken` (*drauf*, *nach Haus*, *am Tisch*).
- `verb_second` (`["weil", "obwohl"]`): a word order, which no `word` and `for` can say. The clause after one of these with its last word, the verb, moved up behind the subject and every other word in its place is almost, the standard form shown: "weil er hat keine Zeit", "obwohl sie muss früh aufstehen", a separable verb in its two parts ("weil sie steht um fünf auf" for aufsteht). The clause ends at the accepted answer's comma, so "Obwohl es regnet, wir gehen" stays wrong; after dass, a question word or deshalb nothing changes.
- The table is in the APK, no bridge route serves it and no bridge or script port grades an answer: the app's verdict is what is stored,
  so an older app grades with the table it has (`good` for `well` wrong there) and an older bridge stores the hint as any other
  (a free string, `answers.ts`). A table without the key behaves as before.

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

- **Nothing written left to do, more to come** (every written unit done, or none written yet: the Italian course's first
  days; no written unit behind a closed level; and a unit still planned, or a level the outline lists with no units, in a
  level open or not: German and English past their written levels): the teacher says she is preparing the next lessons,
  kindly, under her welcome (`School.preparing`; "Pripravljam naslednje ure. Kmalu se spet vidiva!", Maestra Elena's "Sto
  preparando le prossime lezioni. Torna presto!"), and Home's tile says "📝 Nove ure kmalu" instead of "✅ vse opravljeno".
- **Written units behind a closed level** (the open levels done, the next one waiting for the school: `School.waiting`):
  the first closed level says what opens it, as above, and Home's tile says it too: "🔒 A2: zgradi šolo · A2: build the
  school", "🔒 B1: nadgradi šolo · B1: upgrade the school".
- **Past a course written to its end** (every level the outline lists written, every unit done): "✅ vse opravljeno".

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

B2's first six units (`delezja` to `odvisniki-napredno`, docs/plans/05-built/sl-b2-a.md) teach pages of their own too,
each a B2 page with its cheat-sheet card: `delezja`, `delezniki`, `pogojnik-pretekli`, `predpreteklik`,
`oziralni-kateri`, `odvisniki-napredno`. A gerund, a participle, a clause's bi or kateri isn't a row of a word's table;
the B1 or A2 page a unit lists beside its own turns on what it did before:

| unit | its pages | what the forms cards ask from then on |
|---|---|---|
| `delezniki-pridevniki` (B2) | delezniki, trpnik, pridevniki-ujemanje | an adjective's agreement (as `spol`); a participle (dozorel, tekoč) isn't a form of a verb's table |
| `predpreteklik` (B2) | predpreteklik, pretekli-cas | the past's l-forms (as `pretekli-cas`) |

`delezja` (delezja, prislovi), `pogojnik-pretekli` (pogojnik-pretekli, pogojnik-ce-bi), `oziralni-kateri`
(oziralni-kateri, oziralni-ki) and `odvisniki-napredno` (odvisniki-napredno, odvisniki) turn on no form of a table.

B2's units from `stopnjevanje-napredno` to `besedni-red` teach pages of their own as well, each a B2 page with its
cheat-sheet card: `stopnjevanje`, `ukazi`, `frazemi`, `uradno-pismo`, `govorjeni-jezik`, `besedni-red`. None is a row of
a word's table; two units name an earlier page a form counts on:

| unit | its pages | what the forms cards ask from then on |
|---|---|---|
| `stopnjevanje-napredno` (B2) | stopnjevanje, primernik | an adjective's comparative (lepši, boljši, višji: `cmp.m.sg`), as `primernik` |
| `velelnik-napredno` (B2) | ukazi, velelnik, glagolski-vid | the imperative, as `velelnik` (pojdiva, pojdimo with the dual and the first person) |

`frazeologija` (frazemi), `pisni-jezik` (uradno-pismo, ti-vi), `govorjeni-jezik` and `besedni-red` (besedni-red,
naslonke-red) turn on no form of a table.

**The case trainer** ("🧩 Skloni · Cases"; README.md "Case trainer") asks what the units have introduced: an item or a
frame once its rule's page is introduced (met in the book, or listed in the `grammar` of a unit whose intro is done) or
at or below the learner's level, one level above it when its rule is introduced. It opens once the cases have begun: for
an A1 learner the intro of `tozilnik` (or a case's page met in the book), from A2 for every learner. Each case unit's
page has "🧩 Skloni", a session on what the unit teaches (the units in bold here, not as code: the rows above are the
forms cards' table, which the smoke reads):

| unit | the session's focus |
|---|---|
| **tozilnik**, **mestnik**, **rodilnik**, **dajalnik**, **orodnik** | that case |
| **nikalnica-rodilnik** | rodilnik-nikalnica (the genitive after a no) |
| **kje-kam-od-kod** | kje or kam: every "Kje ali kam?" and the rules kam-tozilnik, kje-mestnik-orodnik |
| **pridevniki-skloni** | agreement: the adjectives and possessives in their cases |
| **mnozina-skloni**, **dvojina-skloni** | the plural's, the dual's cases |
| **skloni**, **sklanjatev-posebnosti** | all six, mixed |

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

### B2, units 7–12: how words are made, no subject, two cases, more numbers

B2 is B1's grammar used accurately plus the grammar of a text (docs/grammar-syllabus.md, "B2"). Its units 7–12 teach
what makes and changes words, each with a B2 page of its own (its cheat-sheet card the unit's summary) and the earlier
pages it builds on:

| unit | its pages | what it teaches |
|---|---|---|
| `pomanjsevalnice` | pomanjsevalnice, spol-samostalnikov, sklanjatev-posebnosti | -ica, -ka; -ek, -ček, -iček; -ce, -ece, -esce; their cases (na stolčku); small, a little of, dear, or a word of its own (žlička, konjiček) |
| `izpeljanke` | izpeljanke, spol-samostalnikov, glagolniki | who (-ar, -ec, -telj), a woman (-ka, -ica), where (-arna, -nica, -išče), a tool and a quality (-lnik; -ost, -ota, -ina), of it and who lives there (-ski, -en, -it; -čan) |
| `predpone` | predpone, glagolski-vid, vid-casi | a prefix's meaning (prepisati, podpisati), its direction (pri-, od-, pre-, raz-), iti under a prefix (pridem, odšel, našel), the new imperfective (prepisovati, prinašati, prihajati), po-, na- … se, za- |
| `brezosebno` | brezosebno, trpnik, custva | treba je (and its tenses), mogoče, prepovedano, škoda je + infinitive, the impersonal se (se je plesalo, ne da se), the weather and the day |
| `predlogi-dva-sklona` | predlogi-dva-sklona, kam-tozilnik, kje-mestnik-orodnik | the case by the meaning: kam/kje, po kruh/po vasi, ob zid/ob potoku, za babico/za hišo, pred tednom |
| `stevniki-napredno` | stevniki, stevila-glagol, stevila-samostalniki | dvakrat, prvič; dvojen; dvoje vrat; petica, polovica, pol; s tremi, pri štirih, s petimi; po for each |

None of their pages is a row of a word's table, so they turn on no forms cards (and need no row in "What a unit
turns on"). Writing one:

- **A word in brackets after a gap is a form of it** (the smoke's check): a little word or a derived one is not a form
  of its base, so its cue is two words or a hint (`___ (majhna miza)`, `___ (kjer peče pek)`), a prefixed verb is cued
  by itself or by its parts (`___ (podpisati)`, `___ (pri + nesti)`), a number by its figure (`___ (3)`).
- **A prefix one letter off another is another verb**: the Slovene grading lists those forms as known mistakes
  (`grading/sl.json` `wrong`: prenesel for prinesel, prešel for prišel, zapisal for napisal, obšel for odšel), never a
  slip in the stem; a slip inside the root (prinsel) stays almost.
- **A spoken or less right form is almost, never wrong**: *tri vrata* (SP2001 marks it °; the standard *troje vrat*)
  is an `almost` option; a figure SP2001 allows is accepted beside the word (*3-krat*).

## The Italian course

Teo, a child learning Italian from German in the Friuli village, built the school (2026-10-28). The Italian course is
`companion/course/it/`: the outline (A1 to B2, 18 units each, the grammar book's Italian pages to teach) and its units, written in
the Friuli village's life (Maestra Elena, Nonna Rosa, Lorenzo, Giulia, Tommaso, Pastore Davide and his sheep, the sea,
the camp, the field, the wood, the mill, the festa). The school is the same school: the bridge serves the course of the
village's language (`GET /course` of a Friuli village is Italian), the app bundles it, and every part of the school is the
course's language's, not Slovene's:

| What | For Italian |
|---|---|
| Who teaches | the school's person of the culture's cast (`Residents.brings(SCHOOL)`: Maestra Elena in Friuli), in her cast's voice (`teacher`, Alice; `@elena` once she has her own); the guide names her (features/course.ts) |
| Read aloud | the intro's Italian in her voice: `Spoken.looks` for Italian (à, è …, its frequent words) and the words the intro itself shows; the explanation in the learner's German as one clip (`de\|…`); voice-build's `course` source makes her lines ahead (`course-speech.ts`, the smoke's Friuli checks) |
| The teacher's lines | "Benvenuti a scuola! Sedetevi …" and "Questo lo sai già" (tu: Elena says tu to the children); "only the Italian is read" when the phone lacks a German voice |
| Texts in German | the exercises' instructions, hints, whys, labels and columns per base ([above](#texts-in-the-learners-language)) |
| Grading | the Italian table (`grading/it.json`): an accent forgotten is almost, an apostrophe (`un'amica`, `l'ape`) too; the person in brackets may be left out (`drop_subject`); A2's agreement: a participle that doesn't agree is wrong (`Giulia è andato`, `l'ho visto` for `l'ho vista`), `lo`/`la` not elided before avere almost only where the participle agrees (`la ho vista`), and the regular stem where a form has its own is wrong, never a slip (`wrong`: `anderò`, `ferò`, `mangierò`, `venito`); B1's other tense or mood one letter off (`sarai` for `sarei`, `sta` for `stia`, `fosti` for `fossi`) and one ending typed for another on the same stem (`ending_swaps`: `dormono` for `dormano`) too; B2's another conjugation's vowel in the imperfect subjunctive on a verb's stem (`dormessi`, `dassi`), the remote past for the imperfetto (`mangiavano` for `mangiarono`), a word made without its rule (`facilemente`, `inpossibile`), each paired so that a slip in a noun stays a slip |
| The blackboard | "Benvenuti!" and the day as Italy writes it (28/10/2026) before the first unit; the unit's title and forms in chalk (à, è, ' written) |
| Prints | headed in Italian and the base ("Regola · Die Regel", "Unità"), the words from the Italian and the culture's packs with their article; Slovene as a base for an Italian course |
| The numbers module | `numeri`'s intro opens "🔢 Numeri · Zahlen" (README.md "Numbers"), `ore-giorni`'s brings its dates and clock times |
| Not for Italian | forms cards and counting cards (Slovene's), case rows, the cases overview; the Italian units name no `lemma`/`form`, and the smoke's checks against the dictionary's forms (a cell's named form, a bracketed word's forms) and of "What a unit turns on" stay Slovene's (`FORMS_LANGUAGES`: no Italian forms book yet) |
| Checked | `ItalianCourseContentTest` (every unit parsed and played at its level, every right answer graded right by the Italian grading, no decoy right, every text per base in de, sl and en), `SchoolBoardTest` (the blackboard writes every letter of the Italian course), the smoke's course, course-speech and Friuli sections |

While units are still planned (`"planned": true`), the school is no dead end: a level's planned units are folded
("📝 Ancora 12 unità di A1"), and once every written unit is done (or none is written yet) Maestra Elena says she is
preparing the next lessons ("Sto preparando le prossime lezioni. Torna presto!", `School.preparing`); Home's school tile
says "📝 Nuove lezioni in arrivo" rather than "all done".

Writing an Italian unit: as [Writing a unit](#writing-a-unit), with the explanations German first (Teo's base; "du"),
Slovene and English; every Italian form in **bold** with its meaning in brackets; the exercises' texts per base; tu with
the children and the family, Lei only where Elena teaches it; a child's sentences, never a trick, never the child's own
data (an age is Lorenzo's, not the learner's); every file flagged machine-written in its `review`.

### The Italian course: units 1–6

A1's first six units, the greetings to the numbers (the next twelve are written by the course's other writers):

| Unit | Teaches | Its pages |
|---|---|---|
| `ciao` 👋 | ciao, buongiorno, buonasera, arrivederci; mi chiamo, come ti chiami; tu with children, Lei with the teacher (come stai / come sta, scusa / scusi) | tu-lei |
| `essere` 🟰 | sono, sei, è, siamo, siete, sono; the pronoun left out; Lei è; non | essere-avere |
| `articoli` 🏷️ | il, la (-o, -a), l' and un' before a vowel, lo and uno (s + consonant, z), -e words with their article | articoli-genere |
| `plurale` 🐈 | -o → -i, -a → -e, -e → -i; i, gli, le; -ca, -go, città, uova | plurale, articoli-genere |
| `avere` 🎒 | ho, hai, ha … hanno; the h never said; age with avere; ho fame, ho freddo | essere-avere |
| `numeri` 🔢 | 0–20, the tens to 100, ventuno, ventotto; un, una before a noun; quanti, quante | numeri |

### The Italian course: units 7–12

A1's middle six units go on from the greetings, essere, the articles, the plural, avere and the numbers (units 1–6) to
what a child says about the day, in the Friuli village's life (Nonna Rosa's frico, Davide's sheep, Bruno's hammer,
Franco's old red tractor, Aldo's honey, Nonno Bepi's stories and his dog Moro, the pond, the sea, the square):

| Unit | Teaches | Its pages |
|---|---|---|
| `presente-are` 🗣️ | parlo, parli, parla … parlano (the stress at the front); cantiamo as let's; Lei with the lui form (Elena, the adults); the question and non; giochi, peschi with h; mangi, mangiamo with one i | presente-regolare |
| `presente-ere-ire` 📖 | leggo, leggi, legge (the g hard, then soft) … leggono; -ire as -ere but voi -ite; capisco, capisce with -isc-, not for noi and voi; lui parla but lui legge | presente-regolare |
| `aggettivi` 🎨 | nero, nera, neri, nere; verde, verdi; after è and sono; una piccola barca, un vecchio trattore rosso (colours after); a boy and a girl: stanchi; bianche, stanchi with h | aggettivi-accordo |
| `ce-ci-sono` 🌟 | c'è for one, ci sono for more (tante too); Ci sei? Sì, ci sono!; non c'è (pane: no article); c'è or è; dov'è, dove sono | ce-ci-sono |
| `preposizioni` 🧭 | a Gorizia, a casa, in Italia, in cucina; nel, al, dal, del, sul and their rows; vicino allo stagno; sotto, sopra, dietro, con that don't melt | dove-preposizioni, preposizioni-articolate |
| `possessivi` 👪 | il mio, la mia, i miei, le mie (il mio zaino); suo for his and her by the thing; mia nonna without the article, i miei nonni with it; loro never changes; Suo with Elena | possessivi |

- **Only earlier grammar around a gap**: units 7–10 say "in cucina", "in piazza", "a casa", "con il cane", never a fused
  preposition (nel, al) around a gap; possessivi, after preposizioni, may.
- **A hint of forms stays a string** ("loro …", "c + i → ch + i", "-iamo"); a hint in words is per base.
- **Spoken forms are right**: "la mia nonna" is a choice's `almost` and a gap's second answer, never wrong.
- **A gap has one answer in its sentence**: where c'è and dov'è could both answer ("«___ Moro?» «È qui.»"), the sentence
  is rewritten so only one fits.

### The Italian course: units 13–18

The Italian course (`companion/course/it/`) is Teo's: a child of about seven, learning from German, in the Friuli
village on the coast. Its last six A1 units come after the present tense, the adjectives, c'è, the prepositions and
the possessives (units 7–12), and use only those around their gaps:

| # | unit | what it teaches | its pages |
|---|---|---|---|
| 13 | `verbi-irregolari` | andare, venire (and venire for coming along: Sì, vengo!), fare (Fa caldo), stare (Come stai?), volere + an infinitive | verbi-irregolari |
| 14 | `domande` | a yes/no question by the voice, no/non, chi, che cosa, dove (where and where to), da dove, quando, come, perché (why and because), quanto/quanti/quante | domande |
| 15 | `ore-giorni` | sono le tre, è l'una, e mezza (3:30 is le tre e mezza, not the next hour as in German), meno un quarto, alle otto, the days (lunedì, il sabato every week), the months (a maggio), a date (il venti maggio, il primo) | ore-giorni, numeri |
| 16 | `piacere` | mi piace + one thing or a verb, mi piacciono + more, ti piace?, non mi piace, gli/le and a + a name | piacere |
| 17 | `imperativo` | tu: -are → guarda!, -ere/-ire → leggi!; voi: guardate!; noi as let's (andiamo!); vieni, vai/va', fai/fa', stai/sta', dai; non + the infinitive (non correre!) and non + voi | imperativo, verbi-irregolari |
| 18 | `passato-prossimo` | avere + -ato, -uto, -ito; fatto, visto, preso, letto, detto; essere for going and coming; the participle agreeing with essere (Giulia è andata) | passato-prossimo, essere-avere |

- **Pages written for them**: `imperativo` (the outline named none) and `ore-giorni` (the outline's `numeri` is the
  numbers; the time, the days and months had no page, so the cheat sheet had nothing for the unit's sheets), both A1;
  `verbi-irregolari` has volere and potere now (the unit's title says voglio).
- **Maestra Elena** teaches (the school's person in Friuli): her lines (`say`) in German first, then Slovene and
  English, "du" in German, every Italian form in bold for her voice with its meaning in brackets after it. The children
  say tu to each other and to the nonni; nobody in these units says Lei.
- **The texts the learner reads are per base** (`explain`, `hint`, `instruction`, `translation`, `cue`, a sort's
  `label`, a table's `columns`: `{"de", "sl", "en"}`, German first), where they hold more than Italian forms; a hint of
  forms only ("tu → ?", "noi: -iamo") stays a string. An instruction is the Italian label, then the base:
  "Completa la tabella · Füll die Tabelle aus".
- **Not used, being Slovene-only**: a table cell's or a line's `lemma` and `form` (the forms book is Slovene's:
  `FORMS_LANGUAGES`), "What a unit turns on" (no Italian forms cards or counting cards; the numbers module is opened by `numeri`, as above), the smoke's
  check of a gap's answer against the word in brackets (the dictionary's forms, Slovene only).

### The Italian course: A2, units 1–6

A2 is outlined in `course.json` (18 units, 2026-10-31) and written in three parts side by side. Its first six come after
the whole of A1 (the past with avere and essere among it) and use only A1 and the A2 units before them around a gap:

| # | unit | what it teaches | its pages |
|---|---|---|---|
| 1 | `riflessivi` 🧼 | mi lavo … si lavano (lavarsi, vestirsi); the -si infinitive and the verbs reflexive only in Italian (alzarsi, svegliarsi, addormentarsi); non before the little word; le mani with the article | riflessivi, presente-regolare |
| 2 | `modali` 🙋 | potere (Posso andare in bagno?), dovere, volere with the infinitive right after it, sapere for a can that is learned (Sai nuotare?); non before the modal; devo lavarmi and mi devo lavare | modali, riflessivi |
| 3 | `stare-gerundio` 🏃 | sto, stai … + -ando, -endo; the gerund that never changes; facendo, dicendo, bevendo; right now against every day; mi sto lavando | stare-gerundio, verbi-irregolari |
| 4 | `partitivo` 🧺 | del, della as di + the article (la casa del nonno); dello, dell', dei, degli, delle; un po' di; nothing after non; qualche + the singular | partitivo, preposizioni-articolate |
| 5 | `comparativi` ⚖️ | più, meno … di; di with the article and di me, di te; come, quanto; migliore with a thing, meglio with a verb; peggiore, peggio | comparativi, aggettivi-accordo |
| 6 | `superlativi` 🏆 | il più … with di and the group (il più vecchio del paese); the noun inside (l'albero più vecchio); -issimo with its agreement and its h (stanchissimi); il migliore | superlativi, comparativi |

- **Pages written for them** (A2): `riflessivi`, `modali`, `stare-gerundio`, `comparativi`, `superlativi`; `partitivo`
  has qualche + the singular and alcuni, alcune now (the unit's last step).
- **A summary of its own**: `riflessivi` ends its intro on `intro.summary`, as `ciao` does: its page goes on to the
  modals (devo lavarmi), the next unit.
- **Several right answers** where Italian has them: devo lavarmi and mi devo lavare, mi sto lavando and sto lavandomi,
  come and quanto, and beside migliore for food the spoken più buono, più buona.
- **No trick**: a choice's wrong option is wrong Italian, or wrong by what its own sentence says (the ages given;
  «Sì, ho imparato al mare!» for sapere against potere). Where mi, ti and si would each make a sentence (Lorenzo mi veste:
  dresses me), the sentence leaves only the reflexive one.
- **The grading** needed no change for them: an accent or an apostrophe forgotten stays almost (può, dell'acqua, un po').
- **The print's rule line** of a step is its first sentence and the next ones with forms, about 200 characters at most
  (`prints/sheet.ts`, `ruleLine`, `condense`): the point of a step goes in its first sentence, or the sheet loses it. The
  smoke's prints section reads every unit of the repository in every base its texts have and fails on a step that shows
  forms (bold) but prints none in its line ("Two more." for kjer and oziroma): put a form in the first sentence, or keep
  the next one short enough to fit (end a bold run of example sentences before its last full stop, `**… ne spi**.`, so
  the sentence after it is a sentence of its own).

### The Italian course: A2, units 7–12

A2's middle six go on from A1 and A2's first six (riflessivi to superlativi) to the past with essere, the
participles of their own, the imperfetto and the two pasts together, the future and lo, la, li, le, in the village's
life: Giulia's day at the market in Cividale, Lorenzo's sixth birthday, Nonno Bepi's stories and the Agane of the
streams, the night of the bora, Matteo's grape harvest, Zia Nives's radio, Davide's sheep and fog
(docs/plans/05-built/course-it-a2b.md):

| # | unit | what it teaches | its pages |
|---|---|---|---|
| 7 | `passato-essere` 🚶 | essere's past with -o or -a (sono andato, sono andata), -i for several or mixed, -e only girls; the other verbs of going, coming and staying (the German "bin, ist" as a guide); stato for essere and stare; the reflexive verbs (mi sono lavato, ci siamo divertiti) | passato-prossimo, essere-avere, riflessivi |
| 8 | `participi-irregolari` ✍️ | the participles of their own in families that sound alike (-tto, -so, -sto, -erto, -nto; bevuto, venuto), with essere agreeing (è venuta, sono rimaste, è scesa) | participi-irregolari, passato-prossimo |
| 9 | `imperfetto` 📜 | C'era una volta; ero … erano, c'era and c'erano; -avo, -evo, -ivo; facevo, dicevo, bevevo, faceva freddo; what someone was like, what always used to happen | imperfetto |
| 10 | `imperfetto-passato` 🎬 | the picture and what happens in it (Dormivo quando è arrivato Moro); mentre; the words that decide (sempre, ogni giorno against un giorno, all'improvviso; ieri only says when); a story; a counted time (per due ore) | imperfetto-passato, imperfetto, passato-prossimo |
| 11 | `futuro` 🔮 | -ò … -anno, -are's a → e, -ire's i; the short ones (sarò, avrò, andrò, farò, verrò, vorrò); giocherò, mangerò; plans, the forecast, quando with the future, the present for tomorrow | futuro |
| 12 | `pronomi-diretti` 👉 | lo, la, li, le before the verb (the Italian gender: la palla → la); mi, ti, ci, vi; non lo so, voglio vederlo or lo voglio vedere; l'ho vista, li ho presi | pronomi-diretti, passato-prossimo |

- **Pages written for them**, all A2: `participi-irregolari` (passato-prossimo's table is full at 12 rows, and the
  cheat sheet needs the families during the unit's sheets), `imperfetto`, `imperfetto-passato`, `futuro`,
  `pronomi-diretti`. `passato-prossimo` now lists every verb with essere, the reflexive verbs and a boy and a girl
  together (-i), with three more examples.
- **Only what comes before around a gap**: passato-essere's gaps take regular participles and A1's stato and venuto
  (rimasto and sceso are the next unit's); imperfetto asks for the forms, the choice between the two pasts is
  imperfetto-passato's; pronomi-diretti uses the modals (A2's `modali`) for voglio vederlo.
- **Whose sentence it is**: a gap of the first person says who speaks ("Giulia: «Ieri sono ___ al mare.»", "Mi chiamo
  Giulia."), so the -o or -a is never the child's own; the Slovene of a first or second person's past or future pairs
  the learner's gender (`{m:spal|f:spala} sem`).
- **No sentence where both pasts would do**: each of imperfetto-passato's has a word that decides (ogni giorno,
  all'improvviso, mentre, per due ore) or a state (era sul divano); ieri decides nothing (Ieri pioveva); its transform
  lists say `whole`.
- **No gap whose answer is a bare l'**: an unelided lo there couldn't be told from a slip, so a pronoun's gap is one
  word (lo, la, li, le, mi, ti, ci, vi) and l'ho vista is typed whole, in a transform line.
- **The grading A2 needs** (`grading/it.json`): a participle that doesn't agree is wrong, as an ending is; lo or la
  before avere is almost only where the participle agrees (la ho vista); the regular stem where a form has its own is
  wrong, never a slip in the stem (`wrong`: anderò, ferò, mangierò, venito, bevito), so the distractors that make
  that mistake on purpose are wrong for the grading too.

### The Italian course: A2, units 13–18

A2's last six units, the short words and the polite forms, after the pronouns that take the place of a thing (A2's
`pronomi-diretti`, unit 12). Around their gaps they use A1 and A2's units before them only (the reflexives, the modals,
the partitive, the comparatives, the past with essere, lo and la), in the outline's order:

| # | unit | what it teaches | its pages |
|---|---|---|---|
| 13 | `pronomi-indiretti` 🎁 | gli and le from piace on to dare, dire, portare; a + the person replaced; mi, ti, ci, vi, gli for a loro; Le to Maestra Elena (ti as almost: the register); telefonare a takes le, aiutare takes lo | pronomi-indiretti, piacere |
| 14 | `ci-ne` 🧺 | ci for a place named (Sì, ci vado), non ci; ne after a number (Ne ho sette, never ho sette) and an amount (ne voglio un po', non ne ho); ce n'è, ce ne sono | ci-ne, ce-ci-sono, partitivo |
| 15 | `negazioni` 🙅 | non … mai, niente (nulla too), nessuno, più, ancora; nessuno first without non; one-word answers (Nessuno! Niente!) | negazioni |
| 16 | `vorrei` 🍦 | vorrei against voglio (voglio as almost: right, but sharp); vorresti, vorrebbe (Lei), vorremmo, vorreste, vorrebbero; potrei, potresti, potrebbe (Lei); mi piacerebbe | vorrei, verbi-irregolari, tu-lei |
| 17 | `imperativo-pronomi` 👉 | the short word on the end (guardami, prendilo, portagli); da', di', fa' doubling (dammi, dimmi, fallo; dagli single); noi and voi (aiutiamolo, ascoltatemi); non toccarlo and non lo toccare, both right | imperativo-pronomi, imperativo, pronomi-diretti, pronomi-indiretti |
| 18 | `relative-che` 🔗 | che joining two sentences, for people and things; che never changing, the verb after it agreeing (i bambini che giocano); che as the object (il libro che leggo); chi only the question | relative-che |

- **Pages written for them**, A2, the book's usual size: `pronomi-indiretti`, `ci-ne`, `negazioni`, `vorrei`,
  `imperativo-pronomi`, `relative-che`. Each unit names its own first (the summary card), then the earlier pages it
  builds on.
- **The grading** (`grading/it.json`): ci and ne not elided before e are almost, as an article not elided is (ce ne è for
  ce n'è, ci è for c'è, ci era for c'era: `GradingLanguagesTest`). Both orders of a negative with a pronoun are right
  (non toccarlo, non lo toccare: listed in `accept`), and so are niente and nulla, the subject named or left out in an
  answer with ci (Sì, Giulia ci va. Sì, ci va.), sì or no left out in front.
- **A choice's `almost`** is a right form in the wrong register: voglio for vorrei, ti or potresti to the teacher.
- **No trick**: no distractor of a word bank fits its gap in grammar (no voglio where Io vorrei is asked, no sempre in
  a sentence with non); vorrei's gap text has none.

### The Italian course: B1, units 1–6

B1 is outlined in `course.json` (18 units, 2026-11-01) and written in three parts side by side: B1's grammar told for a
child of seven, in the stories and the village's life. Its first six come after the whole of A2 and use only A1, A2
and the B1 units before them around a gap (docs/plans/05-built/it-b1-a.md):

| # | unit | what it teaches | its pages |
|---|---|---|---|
| 1 | `trapassato` ⏪ | the one of two past things that came first (Quando Lorenzo è arrivato, Moro aveva già mangiato); avevo … avevano and ero … erano + the participle, its agreement with essere, the reflexive verbs (si era addormentata); già, prima, non … mai, perché; what came first told second | trapassato, imperfetto-passato, passato-prossimo |
| 2 | `passato-remoto` 🏰 | the fairy tales' past, mostly to recognise: -ò, -ì, -arono, -irono; fu, ebbe, disse, fece, venne (vide, prese, visse); the imperfetto's picture against what happens; the accent that counts (parlò, parlo; andò, andrò); a tale told as people talk (arrivò → è arrivato) | passato-remoto, imperfetto, passato-prossimo |
| 3 | `condizionale` 🤔 | from vorrei to every verb (mangerei, giocherei, prenderei, dormirei); -ei … -ebbero; the future's short stems (sarei, avrei, andrei, farei, verrei); a wish, advice (dovresti, al posto tuo), imagining, asking politely; -emmo against the future's -emo | condizionale, vorrei, futuro |
| 4 | `congiuntivo` 💭 | the present subjunctive after che and another person: -are → -i, -ere/-ire → -a, capisca; sia, abbia, vada, faccia, venga, possa, dica, stia; loro -ino, -ano; the same person takes the infinitive (voglio giocare, penso di sì); the spoken indicative after penso che labelled | congiuntivo, presente-regolare, verbi-irregolari |
| 5 | `congiuntivo-espressioni` 🔑 | è importante che, è meglio che, bisogna che; feelings (sono contento che, mi dispiace che, ho paura che); prima che against prima di; perché for so that; senza che, basta che; dopo che, quando and so che keep the indicative | congiuntivo-espressioni, congiuntivo |
| 6 | `periodo-ipotetico` 🌈 | se + the present with the present, the future or the imperative (Se piove, resto a casa); dreaming with se fossi, se avessi, -assi, -essi, -issi and the conditional (Se fossi un uccello, volerei); never «se sarei»; the se-half first or second | periodo-ipotetico, condizionale, futuro |

- **Pages written for them**, all B1 and new: `trapassato`, `passato-remoto`, `condizionale`, `congiuntivo`,
  `congiuntivo-espressioni`, `periodo-ipotetico`, each its unit's summary card and first cheat-sheet card.
- **passato-remoto is recognition first** (as Slovene B1's trpnik and glagolniki are "know" items): a sort by the verb
  a form comes from, choices of what a form means, a word bank, and a list that tells the tale as people talk; only the
  regular -ò, -ì and the five a child meets (fu, andò, disse, ebbe, venne) are written.
- **No gap where another tense is Italian too**: every trapassato gap is anchored before another past (quando + a past,
  già), so the spoken passato prossimo can't be a second answer; a gap text that a choice of tense could answer two
  ways names the tense in its instruction ("Completa con il passato remoto", "… con il condizionale", "… con il
  congiuntivo") or in its cue ("(essere · condizionale)").
- **The spoken indicative is labelled, not a trap**: after penso che and credo che the indicative (penso che è) is how
  many people talk; a choice offering it is `almost` (the why: beim Reden …, in der Schule sia). Where speech commonly
  takes the indicative (after sperare che, the feelings, ho paura che, basta che), a gap or a line lists it in `spoken`
  ([Spoken forms](#spoken-forms): right, labelled "Parlato, ed è giusto anche così; la forma standard: …"), the
  subjunctive its answer; after volere che and the impersonal expressions the subjunctive alone is right.
- **The imperfect subjunctive** comes only with `periodo-ipotetico`, for its se-half (fossi, avessi, potessi, the
  regular -assi, -essi, -issi); its table leaves voi out.
- **The grading B1 needs** (`grading/it.json`): another tense or mood one letter off is wrong, never a slip
  (`wrong`: the future's sarai, avrai, farai, darai for the conditional's sarei … and back; sta for stia; fosti for fossi
  and back; andò for andrò; siete for siate), and one ending typed for another on the same stem is wrong
  (`ending_swaps`, new: pairs of the right ending and the typed one, -ano and -ono both ways, -ino and -ano both ways,
  -iate and -ate, -este and -ete; so dormono for dormano, parlano for parlino, parlate for parliate and sarete for
  sareste are wrong, while a slip elsewhere in such a word stays one: bambeno for bambino; `GradingLanguagesTest`).

### The Italian course: B1, units 7–12

B1 is outlined in `course.json` (18 units, 2026-11-01) and written in three parts side by side. Its middle six come after
A1, A2 and B1's first six (trapassato to periodo-ipotetico). Around a gap they use A1, A2 and the B1 units before them;
the trapassato, the passato remoto and the subjunctive, written beside them, stay out of their gaps. Each has a new B1
page of its own, its summary card (docs/plans/05-built/it-b1-b.md):

| # | unit | what it teaches | its pages |
|---|---|---|---|
| 7 | `pronomi-combinati` 📦 | the person first, mi, ti, ci, vi → me, te, ce, ve before lo, la, li, le, ne (Me lo dai?); glielo, gliela, glieli, gliele in one word for him, her and Lei (Glielo porto, Maestra); me l'ha detto, gliel'ho data, the participle agreeing; Puoi darmelo? and Me lo puoi dare?, dammelo, portagliela; me ne, gliene | pronomi-combinati, pronomi-diretti, pronomi-indiretti, imperativo-pronomi |
| 8 | `si-impersonale` 👥 | si + the lui form for German *man* (Come si dice «ciao» in friulano? Si dice «mandi».); the verb plural with a plural thing (si vendono le ciliegie); si può, non si tocca; how it's done, step by step (Come si fa il frico?); ci si alza | si-impersonale, riflessivi, modali |
| 9 | `gerundio` 🦔 | the gerund alone for while and when, the same person (Camminando, ho visto un riccio); by doing (Sbagliando s'impara); facendo, dicendo, bevendo; the short words on its end (guardandolo, alzandosi); quando or mentre for another person | gerundio, stare-gerundio |
| 10 | `stare-per` ⏱️ | stare per + the infinitive, about to (Il treno sta per partire!); appena between avere or essere and the participle, just (Ho appena mangiato, è appena arrivata); sta per piovere, sta piovendo, è appena piovuto; stavo per … quando; appena in front for as soon as | stare-per, stare-gerundio, passato-prossimo |
| 11 | `relativi-cui` 🏡 | cui after a preposition (la casa in cui abito, il bambino con cui gioco); a, di, da, per, su cui; che without a preposition, cui with one, chi only the question; in cui and dove for a place, in cui for a time; the verb not sent to the end | relativi-cui, relative-che, preposizioni-articolate |
| 12 | `indefiniti` 🎈 | qualcuno, qualcosa and their no-answers nessuno, niente; ogni + the singular, ognuno with the singular verb; tutti i, tutto il, tutti alone; qualche + the singular against alcuni, alcune; qualcosa di buono, niente di nuovo | indefiniti, negazioni, partitivo |

- **The grading B1 needs** (`grading/it.json`, `GradingLanguagesTest`): the combined pronoun's mistakes are wrong, never
  a slip in the stem: glilo and portaglilo (glie-'s e left out), dammilo, portamilo, darmilo (the i kept), as mi lo and
  ti la already were by their ending; glielo or gliela before avere not elided is almost only where the participle
  agrees (glielo ho dato for gliel'ho dato; glielo ho data stays wrong). The gerund with the other verbs' ending
  (leggando, parlendo, avvicinendosi), fare's, dire's and bere's regular stem (farendo, direndo, berendo) and li for gli
  on its end (dicendoli for dicendogli) are wrong too, so the distractors that make them on purpose are.
- **Every right answer listed**: both places of a pronoun with a modal (Me la puoi prestare? Puoi prestarmela?); the
  orders Italian allows (Il frico si mangia, Si mangia il frico; su cui Nonno Bepi si siede, su cui si siede Nonno Bepi);
  è appena piovuto and ha appena piovuto; dove and in cui for a place; the subject named or left out.
- **No trick**: no option is right Italian in its own sentence (ci si sta bene and ci si fanno i compiti are, so neither
  is offered against si sta, si fanno); a choice's `almost` is a right form in the wrong register only (Te lo porto to
  Maestra Elena or signor Franco, to whom the child says Lei).
- **Read aloud**: every bold form of an explanation is one the intro shows (its examples, its table, its questions or
  the unit's `words`: glieli, gliele, gliene are among pronomi-combinati's), so Elena reads it and never the phone's
  German voice; gerundio's endings -ando, -endo too, as the words the intro shows end with them (`IntroSpeech.endings`).

### The Italian course: B1, units 13–18

B1 is outlined in `course.json` (18 units, 2026-11-01) and written in three parts side by side, as A2 was; these are
its last six (docs/plans/05-built/it-b1-c.md). Around a gap they use A1, A2 and B1's units before them in the outline's
order, B1's earlier ones only lightly:

| # | unit | what it teaches | its pages |
|---|---|---|---|
| 13 | `verbi-pronominali` 💪 | andarsene (me ne vado … se ne vanno: mi becomes me before ne), farcela (ce la faccio, non ce la faccio più), ci vuole and ci vogliono, the past (se n'è andato; ce l'ho fatta, fatta for every speaker) | verbi-pronominali, ci-ne, riflessivi |
| 14 | `discorso-indiretto` 💬 | dice che with the person changed (mio → suo, ti → mi), chiede se and the question word kept, dice di + the infinitive for an order, ha detto che with the imperfetto for what was so then, the present for what is still to come | discorso-indiretto, possessivi, domande, imperfetto |
| 15 | `passivo` 🍰 | essere + the participle agreeing (la gubana è fatta), da and its melted forms for the doer, è stato, è stata … with both participles agreeing, an ordinary sentence turned round, venire + the participle for the present | passivo, passato-prossimo, participi-irregolari, preposizioni-articolate |
| 16 | `infinito-preposizioni` 🧗 | a after starting, learning, going, helping, trying (comincio a, imparo a, provo a); di after finishing, stopping, deciding, forgetting, avere paura, avere voglia (finisco di, smetto di); nothing after the modals, sapere, piacere, preferire; per and prima di | infinito-preposizioni, modali |
| 17 | `futuro-anteriore` ⏳ | avrò … avranno and sarò … saranno + the participle (agreeing with essere) after quando, dopo che, appena, the other half in the future or the present; a guess about the past (Sarà stato Moro!) | futuro-anteriore, futuro, passato-prossimo |
| 18 | `congiunzioni` 🧩 | perché after and siccome first; quindi, allora, così; ma, però, invece; anche se; mentre, appena; benché, sebbene and prima che named for the subjunctive, not practised | congiunzioni, imperfetto-passato, domande |

- **Pages written for them**, all B1, the book's usual size, each its unit's summary card: `verbi-pronominali`,
  `discorso-indiretto`, `passivo`, `infinito-preposizioni`, `futuro-anteriore`, `congiunzioni`.
- **The present where Italians say it**: Ha detto che viene domani (the title) and Quando avrò finito, gioco, the
  present and the future both accepted; what was so then, after ha detto, is the imperfetto only (ieri, a pranzo say
  when, so the present can't be meant).
- **Several right answers listed**: è fatto and viene fatto; a reported question or order with and without its speaker
  and mi or ci; joined sentences in either order; every conjunction that fits a gap (Siccome, Poiché, Dato che, Visto
  che; quindi, allora, così, perciò).
- **No gap where a and ad would both do**: an infinitive after a gap starts with a consonant (ad stays on the page).
- **The grading B1 needs** (`grading/it.json`): an articulated preposition for another is wrong, never a slip in the
  stem (Il miele è fatto delle api for dalle api; alla or nella for dalla: the passive's da), a slip in the word itself
  (dlla) still forgiven; ce la ho fatta and se ne è andata are almost (the apostrophe), as ce ne è is.
- **No trick**: congiunzioni's gap text has no distractors (almost every conjunction fits some gap), as vorrei's has
  none; a wrong option is wrong Italian or wrong by its own sentence (la mia where Davide is told about, sono where
  Giulia said it).

### The Italian course: B2

B2 is outlined in `course.json` (18 units, 2026-11-02) and written in three parts side by side (docs/plans/05-built/course-it-b2-a.md, -b.md, -c.md). The audience stays the course's: German first with du, nothing frightening, the children around. It is still real B2, so the lines are the village's grown-up life, and a polite letter is a letter to the mayor. Each page new here has its own hand-written `card`, the first of the Italian book. Around a gap each unit uses A1, A2, B1 and the B2 units before it. The sources and where the outline departs from them: docs/plans/05-built/outlines-2.md.

Unit 1–6: the congiuntivo's other tenses.

| # | unit | what it teaches | its pages |
|---|---|---|---|
| 1 | `congiuntivo-passato` 🧳 | abbia or sia + the participle, its agreement; after an opinion, a doubt or a feeling, not after so che; venga against sia venuto; the small words in front | congiuntivo-passato, congiuntivo, passato-prossimo |
| 2 | `congiuntivo-imperfetto` 🔍 | -assi, -essi, -issi in every person, voi's -ste too; fossi, dessi, stessi, facessi, dicessi, bevessi; after a past, vorrei che, mi piacerebbe che; prima che, senza che; the same person with di | congiuntivo-imperfetto, congiuntivo, periodo-ipotetico |
| 3 | `congiuntivo-trapassato` ⏮️ | avessi or fossi + the participle, what came before a past; to recognise first, as the Profilo asks for it only in part | congiuntivo-trapassato, congiuntivo-imperfetto, trapassato |
| 4 | `concordanza-tempi` 🕰️ | the table of before, at the same time and after, for a first verb now and in the past; sarebbe venuto named and accepted, never asked; a present with the imperfect for what went on long | concordanza-tempi, congiuntivo-passato, congiuntivo-imperfetto, congiuntivo-trapassato |
| 5 | `benche-purche` ☂️ | benché, sebbene, nonostante, anche se beside them; purché, a condizione che, a patto che; affinché; a meno che with its non; come se | congiunzioni-congiuntivo, congiuntivo-espressioni, congiunzioni |
| 6 | `magari` 🌠 | Magari!; magari, almeno, se solo + the imperfect or pluperfect; Non l'avessi mai detto!; che + the present or past for a doubt or a wish; magari + the indicative as maybe | congiuntivo-indipendente, congiuntivo-imperfetto |

Unit 7–12: if, regret, telling.

| # | unit | what it teaches | its pages |
|---|---|---|---|
| 7 | `condizionale-passato` 🤷 | avrei or sarei + the participle; what would have been; avrei dovuto, potuto, voluto; the news's conditional to recognise | condizionale-passato, condizionale, passato-prossimo |
| 8 | `se-potessi` 🌠 | every person of the se-half; the condizionale in the other half, either first; se vinco against se vincessi; se fossi in te; its own summary card (`intro.summary`) | periodo-ipotetico, condizionale, congiuntivo-imperfetto |
| 9 | `se-avessi-saputo` 🤦 | what can't be any more; the mixed one by its time word; the spoken double imperfetto named and right on every gap and line | periodo-ipotetico-irrealta, periodo-ipotetico, condizionale-passato, congiuntivo-trapassato |
| 10 | `disse-che` 📻 | the tenses that shift after a past, the future in the past, oggi to quel giorno, orders with di, mi chiese dove fosse | discorso-indiretto-passato, discorso-indiretto, condizionale-passato |
| 11 | `raccontare` 📚 | the remote past used: the strong verbs' 1-3-6, which past by distance and region, the picture beside the events, the presente storico | raccontare, passato-remoto, imperfetto-passato |
| 12 | `gerundio-passato` 🏁 | avendo or essendo + the participle, what was done first and often why; one subject; pronouns on the end; essendo in due; pur + the gerund | gerundio-passato, gerundio |

Unit 13–18: other forms, words, a text.

| # | unit | what it teaches | its pages |
|---|---|---|---|
| 13 | `infinito-passato` 🍽️ | dopo aver, dopo essere (dopo essersi alzata); dopo averla letta; penso di aver capito, grazie per aver aiutato; the infinitive as a noun (il dovere, camminare fa bene); finita la cena, arrivata a casa; the participle as an adjective | infinito-passato, participio-assoluto, infinito-preposizioni |
| 14 | `fare-lasciare` 🔧 | fare + an infinitive with nothing between; te lo faccio vedere, fammelo vedere, posso fartelo vedere; a or da for the one who does it; lascialo dormire, lascia stare; l'ho fatta riparare; si è fatta tagliare i capelli | fare-lasciare, pronomi-combinati |
| 15 | `viene-aperto` 🏷️ | venire + the participle against è aperta, simple tenses only; andare + the participle for must; si + a verb that follows the thing, si sono venduti; vendesi to recognise | passivo-venire-andare, passivo, si-impersonale |
| 16 | `il-quale` 🔗 | il quale and its forms, dal quale, nella quale; il cui; chi for the one who; chiunque, qualunque, qualsiasi + the subjunctive | relativi-il-quale, relativi-cui, indefiniti |
| 17 | `alterati` 🔎 | -ino, -etto, -ello, -uccio, -one, -accio; words of their own (mulino, burrone); -mente; in-, s-, ri-; families of words, libreria against biblioteca | alterati |
| 18 | `connettivi` ✉️ | tuttavia, anzi, in effetti, inoltre, invece; a causa di, in base a; il pane l'ho comprato, è Rosa che; the letter to the mayor (Gentile Sindaco, Le scrivo, La ringrazio) | connettivi, congiunzioni, tu-lei |

- **The grading B2 needs** (`grading/it.json`, every rule paired, so that a slip in a noun stays a slip; `ItalianB2aContentTest`, `-b`, `-c`):
  - Another conjugation's vowel in the imperfect subjunctive is wrong (dormessi for dormissi, dicassi for dicessi, dassi for dessi, parleste for parlaste), in `wrong` on a stem of three letters or more or av, st, d, with -issimo as the right word left out. So a child's sassi, feste, classe, triste and bellissimo keep a slip as a slip. Plain `ending_swaps` did this first in two parts and were taken out for that reason.
  - foste and fosse, deste and desse, desti and dessi for one another; dissi for dessi; avando for avendo; vede and vide for one another; mese for mise.
  - On the same stem (`ending_swaps`): the remote past against the imperfetto and the future (mangiavano for mangiarono, dormiranno for dormirono).
  - A word's making: facilemente, regolaremente, lentomente, felicamente; inpossibile, inregolare, inlegale, inbevibile; barcetta. Veremente for veramente stays a slip.
- **A form people say is never an option.** The native lists name them; each is in `spoken` or left out, and no choice or word bank offers one as wrong (benché è, sebbene ha, come se era; a dangling gerund, which is why gerundio-passato asks who did it; a remoto for today). The typed subjunctive stays the answer where the indicative isn't listed; benché + the indicative and dassi, stassi wait for a native's word, and for `nonstandard` if the answer is that people say them.
- **Se + the conditional stays offered as wrong** (se potrei, se avrei saputo), as B1's `periodo-ipotetico` does and the outline says, and so does the simple conditional for the future in the past (disse che porterebbe). Both are on the native list.
- **No gap where another tense is Italian too, unless every right form is listed:** every shift gap of disse-che has a past anchor, raccontare's gaps name their tense; a cue in brackets where another verb would fit (si è ___ (fare) aiutare, as si è lasciata would).
- **A cloze can't list a spoken form** (`cloze` has no such key), so a quick question with a gap avoids the verbs that have one.
- **A table's cell short:** a phone's field holds about nine letters in three columns, twelve in two, eighteen in one.
- **A quick question is Italian only:** the sentence of its answer is read in Elena's voice, so a prompt in the base would be read in it too.

## The German course

The German course is `companion/course/de/`: the outline (A1, A2 and B1 written, 18 units each, the grammar book's German
pages to teach; B2 planned, docs/plans/05-built/outlines-2.md) and its units, written for adults in the Carinthian village's life below the Dobratsch near Villach (Wirtin Kathi and her
Gasthaus, Mostbauer Stefan's cider, Tante Mitzi, Müller Franz and his mill, Schmied Toni, Imker Stanko, Hirte Florian
and his sheep, Opa Sepp's stories, Oma Resi's kitchen, the Kirchtag). It serves a learner of German from Slovene, English
or Italian (a visitor, a neighbour across the border), and is the same school as the others: the bridge serves the course
of the village's language (`GET /course` of a Carinthian village is German), the app bundles it, and every part of the
school is the course's language's:

| What | For German |
|---|---|
| Who teaches | the school's person of the culture's cast (`Residents.brings(SCHOOL)`: Lehrerin Eva in Kärnten), in her cast's voice (`teacher`, Lily; `@eva` once she has her own); the guide names her (features/course.ts) |
| Read aloud | the intro's German in her voice: `Spoken.looks` for German (ä, ö, ü, ß, its frequent words) and the words the intro itself shows; the explanation in the learner's base as one clip (`en\|…`, `sl\|…`, `it\|…`); voice-build's `course` source makes her lines ahead (`course-speech.ts`, the smoke's Carinthian checks) |
| The teacher's lines | "Willkommen in der Schule! Setzt euch, fangen wir an." and "Das kannst du schon": Eva says du, as the village does |
| Texts in the base | the exercises' instructions, hints, whys, labels and columns per base `{en, sl, it}` ([above](#texts-in-the-learners-language)); the intro's `say`, the titles, the goals and the examples in every base too |
| Grading | the German table (`grading/de.json`): ae, oe, ue, ss for ä, ö, ü, ß almost (hint umlaut), an umlaut left out a slip in the stem; capitals don't count (a noun typed small is right, as is sie for Sie: du or Sie is asked by a tap, never typed); geht's, gehts and geht es one answer, tschüs and tschüss, the spoken hab for habe; known mistakes, each only for a word of its group: the plural's umlaut (Apfel for Äpfel, Vater for Väter), the present's vowel change (fahrst for fährst, lest for liest), er's form for du's on a short stem (sagt for sagst, hat for hast, ist for bist), ein for kein, ich, mich, dich for each other, a number word for another (einundzwanzig for neunundzwanzig); the subject is never left out (`drop_subject` false); what people say that a unit teaches writing without, almost with the standard form shown (`nonstandard`, `verb_second`: [Spoken forms](#spoken-forms)) |
| Austrian standard | the village's German: Grüß Gott, Servus, Baba, Jänner, heuer, Paradeiser, Erdapfel, Topfen, Sackerl; where Germany says otherwise (Januar, Tomate, Kartoffel, Tschüss), that form is accepted too and named in the explanation |
| The blackboard | "Willkommen!" and the day as Austria writes it (1.11.2026) before the first unit; the unit's title and forms in chalk (ä, ö, ü, ß written) |
| Prints | headed in German and the base ("Regel · The rule", "Lektion"), the words from the German and the culture's packs with their article (der Hund), the supplement's words (`lexicon/de.extra.json`) with their meanings in Slovene and Italian; Slovene, English and Italian as bases |
| Not for German | forms cards, counting cards and the numbers module, case rows, the cases overview (Slovene's); the German units name no `lemma`/`form`, and the smoke's checks against the dictionary's forms stay Slovene's (`FORMS_LANGUAGES`) |
| Checked | `GermanCourseContentTest` (every unit parsed and played at its level, every right answer graded right by the German grading, no decoy right, every text per base in en, sl and it), `GermanA2aContentTest`, `GermanA2bContentTest` and `GermanA2cContentTest` (A2's grading pairs and spoken forms), `GermanB1aContentTest`, `GermanB1bContentTest` and `GermanB1cContentTest` (B1's, and that no choice or bank of the course offers what the table's `nonstandard` says people say), `GradingNonstandardTest`, `GradingLanguagesTest`'s German section, `SchoolBoardTest` (every letter of the German course), the smoke's course, course-speech and prints sections |

Writing a German unit: as [Writing a unit](#writing-a-unit), for adults: the village's grown-ups and their work, never
the learner's own data (an age is Stefan's, not the learner's). The explanations in English, Slovene and Italian, every
German form in **bold** with its meaning in brackets right after it; the exercises' texts per base; du to the learner and
among the villagers, Sie where the unit teaches it (to a stranger, Grüß Gott, Wie heißen Sie?); nouns always with their
capital and, where a word list gives them, their article; Austrian words where the village says them, the German-German
one accepted; every file flagged machine-written in its `review`, with what a native speaker should look at (Austrian
German first, then each base).

### The German course: units 1–6

A1's first six units, the greetings to the numbers (docs/plans/05-built/course-de-a1-a.md; the next twelve are the
course's other writers'):

| Unit | Teaches | Its pages |
|---|---|---|
| `hallo` 👋 | Grüß Gott, Servus, Guten Morgen, Guten Abend; Auf Wiedersehen, Tschüss, Baba; ich heiße, wie heißt du, wie heißen Sie; du with the village, Sie with a stranger; Wie geht's? | du-sie |
| `sein` 🟰 | ich bin, du bist, er/sie/es ist, wir sind, ihr seid, sie/Sie sind; Sie with the capital; who and where, what someone is (Kathi ist Wirtin) | sein-haben |
| `artikel` 🏷️ | der, die, das and ein, eine; the noun's capital; the gender learned with the word; -e mostly die, -chen das, people by sex; Austrian der Paradeiser, der Erdapfel | artikel-genus |
| `plural` 🐑 | -e, -(e)n, -er, -s and none; the umlaut (Äpfel, Väter, Bücher); die for every plural | plural, artikel-genus |
| `haben` 🎒 | ich habe (spoken hab), du hast, er hat, wir haben, ihr habt; Hunger and Durst haben, age with sein | sein-haben |
| `zahlen` 🔢 | null to zwanzig, sechzehn and siebzehn, the tens (dreißig with ß), einundzwanzig (the one first), hundert; eins counting, ein before a noun; Wie viel? Wie viele? | zahlen |

- **A base's prompt has no gap and no umlaut**: a quick question's prompt with a gap and no Markdown is read in Eva's
  voice as German (`questionLines`), and so is one with ä, ö, ü or ß (the app's check). A prompt per base is a situation
  ending in a colon ("Stefan meets his friend Florian on the path. Stefan:"), its options whole German phrases; a
  prompt with a gap is German only ("Wie heißen ___?"); a German word inside a base's prompt goes in „…".
- **A cue at the end of an intro's quick question**: the answer's sentence is read with the gap filled, and only
  brackets at its end are left out: "Die ___ sind rot. (Apfel)", "Franz hat ___ Säcke Mehl. (17)". In the sheets the
  cue stays right after the gap.
- **Only what came before around a gap**: no verb but sein, haben and heißen conjugated in units 1–6, the subject first
  (no inversion before `wortstellung`), no masculine article after haben (the accusative is unit 11: eine Katze, ein
  Pferd, viele Bienen, Hunger, Zeit), places as fixed chunks (im Gasthaus, auf der Alm, zu Hause), never a gap.
- **du or Sie by a tap**: the grading doesn't see capitals, so sie typed for Sie is right; a choice tells them apart.
- **A spoken form the grading already takes** (hab, which `same` makes habe) isn't listed in `spoken`: it would be right
  without its label, and `CourseContentChecks` wants each spoken form labelled. Name it in the intro instead.

### The German course: units 7–12

A1's middle six units, from the numbers to saying no (docs/plans/05-built/course-de-a1-b.md): the present, its vowel
change, questions, the verb in second place, the accusative and the negation, in the village's adult life (Kathi's
Kasnudeln and her Ruhetag, Stefan's cider and his drive to Villach, Florian on the Alm with Bello, Stanko's honey at the
market, Opa Sepp's Lindwurm, Herr Rossi from Udine as a guest):

| Unit | Teaches | Its pages |
|---|---|---|
| `praesens` 🏃 | -e, -st, -t, -en, -t, -en (wohnen, kommen, kochen); ihr -t as er; Sie -en; the pronoun always there, one present for I cook and I'm cooking; du arbeitest, er findet (the e after -t, -d); du tanzt (-s, -ß, -z: only -t); gern after the verb | praesens, du-sie |
| `vokalwechsel` 🔄 | a → ä (fährst, schläft, trägt), au → äu (läuft), e → i (sprichst, gibt, es gibt), e → ie (siehst, liest) for du and er only; ihr fahrt; isst, nimmt; er isst against er ist | vokalwechsel (a new page), praesens |
| `fragen` ❓ | the question word, the verb, the person (Wo wohnst du?); wer is who; wann (not wenn), wie, warum; wo, wohin, woher; the verb first for yes or no; doch; questions to Sie | fragen |
| `wortstellung` 🧱 | the verb second whatever comes first (Heute backt Oma Resi Kuchen); a first part of several words (am Abend); und, aber, oder take no place, dann does; the question's verb; when before where (morgen nach Villach) | wortstellung, fragen |
| `akkusativ` 🎯 | der → den, ein → einen, nothing else changes; es gibt + accusative; the subject keeps der (Den Fuchs sieht der Hund); ihn, sie, es, mich, dich; für + accusative | akkusativ, artikel-genus |
| `kein-nicht` 🚫 | kein for ein and for no article (keine Zeit, keinen Hunger), nicht for the rest; nicht at the end, before a place or an adjective; kein … mehr, nicht mehr | kein-nicht, akkusativ |

- **Only what came before around a gap**: units 7–9 keep the subject first (an intro's example may ask a yes or no
  question, known from haben) and no masculine article after a verb; `wortstellung` brings the inversion, `akkusativ`
  den and einen, and from there on both may stand around a gap. The verb's bracket (a modal, the Perfekt, a separable
  verb) is units 13–18's: `wortstellung` teaches the second place only.
- **Two words in one gap** where the order is the point: `wortstellung`'s gap text asks for the verb and the person
  (`Am Sonntag ___ (wir · gehen) in die Kirche.` → `gehen wir`), its bank of such pairs and the pairs the other way
  round as distractors (`wir gehen`: wrong, two words differ); its choices offer the two orders (`kocht Kathi`, `Kathi
  kocht`).
- **A reorder only in the second or third sheet**: QA's `course-unit` plays the first sheet and the test, and doesn't
  play a reorder. Every order of the tokens that is right is a solution (`Im Gasthaus kocht Kathi Kasnudeln.` and
  `Kathi kocht im Gasthaus Kasnudeln.`).
- **The article in brackets** when a gap could take two (`Toni sucht ___ (der) Hammer.` → `den`, not einen), and the
  verb in brackets for a form (`Kathi ___ (kochen) Kasnudeln.`).
- **No example column beside a column of forms** in an intro's table: the print marks a cell against the one to its
  left, and the words in front of the form (`Ich sehe` in `Ich sehe den Hund.`) came out marked as forms. A table of
  forms has forms only (der Hund · den Hund); the sentences are the examples.
- **The grading** (`grading/de.json`) knows three more of A1's mistakes, never slips: the e after -t or -d left out
  (`arbeitst`, `findst` for arbeitest, findest), `ist` (is) for `isst` (eats), `wenn` for the question word `wann`;
  and `gerne` is `gern` (one answer, as Germany says it too). `bäckt` is listed beside `backt`, both standard.
- **Slovene says da**, not the spoken ja, in a translation (`Ali vidiš Dobrač? – Da, vidim ga!`); a German `Ja!` in an
  example stays German.

### The German course: units 13–18

A1's last six units, whose it is to the past (docs/plans/05-built/course-de-a1-c.md). They come after the present, the
questions, the verb second, the accusative and kein/nicht (units 7–12) and use only those and the units before around
their gaps (places as the fixed chunks of units 1–6):

| Unit | Teaches | Its pages |
|---|---|---|
| `possessiv` 👪 | mein, dein like ein (mein Hund, meine Katze); sein and ihr by the owner, the ending by the noun; unser, euer/eure, ihr, Ihr; seinen in the accusative; a name's -s (Kathis Gasthaus, Franz' Mühle) | possessiv, akkusativ |
| `uhrzeit` 🕖 | Wie spät ist es?; sieben Uhr, ein Uhr or eins; halb acht (7:30, towards eight); Viertel nach/vor, the Austrian viertel acht and dreiviertel acht; zehn nach sieben; um, am, im; the days; in der Früh, Jänner, heuer; the verb second after a time | uhrzeit, zahlen |
| `moechten` 🙋 | Ich möchte einen Kaffee, Was möchten Sie?; möchten and können in every person (ich = er, no -t); the other verb at the end; Kann ich zahlen?, nicht; Ich hätte gern | modalverben, wortstellung |
| `trennbare` ✂️ | the prefix at the end (Ich stehe um sieben auf); einkaufen, anrufen, mitkommen; questions and nicht; the Austrian aufsperren, zusperren; whole after möchten and können; be-, ver-, er- never come off | trennbare-verben, wortstellung |
| `imperativ` 📣 | komm!, kommt!, kommen Sie!; the -e (komme!) and warte!; gib!, nimm!, lies! but fahr!, schlaf!; sei!, seid!, seien Sie!; bitte, mal, doch; steh auf!, nicht | imperativ, du-sie |
| `perfekt` ⏪ | haben + ge-…-t, ge-…-en learned with the verb; sein for going and coming, the Austrian ist gesessen; the participle at the end; eingekauft, aufgestanden; besucht, verstanden, telefoniert without ge- | perfekt, sein-haben |

- **Pages written for them**: `possessiv`, `uhrzeit` and `imperativ` (A1, each with its cheat-sheet card), where the
  book had none; `modalverben`, `trennbare-verben` and `perfekt` are the book's A2 pages, named as they are.
- **Austrian first, the German-German accepted and named**: viertel acht and dreiviertel acht (also drei viertel acht)
  beside Viertel nach sieben and Viertel vor acht, and sieben Uhr fünfzehn as a timetable says it; in der Früh (am
  Morgen), Jänner (Januar), heuer; aufsperren and zusperren (aufschließen, abschließen; zumachen accepted where it
  fits); ist gesessen, gestanden, gelegen (hat gesessen in Germany) taught and only tapped, never typed.
- **Every right form listed**: the imperative's -e where Duden has it (Mach!/Mache!, Steh auf!/Stehe auf!), never on an
  e→i verb (gib!, lies!); unseren beside unsren and unsern; Stankos with no apostrophe.
- **Ihr and ihr by a tap**, as Sie and sie: the grading doesn't see the capital, so the polite Ihr is asked in a choice.
- **The grading's pairs for these units** (`grading/de.json`, each only for a word of its group, a slip elsewhere
  forgiven): mein, dein, sein, ein, kein for each other with any ending (whose, not a slip); mochte for möchte;
  fähr!, läuf! for fahr!, lauf! (and ich fähre); a strong participle with the infinitive's vowel (getrinken,
  geschreiben, gebleiben); ge- where none goes (getelefoniert, gebesucht) and a separable participle without it
  (anrufen for angerufen, einkauft).
- **Their words in the supplement** (`lexicon/de.extra.json`): the possessives' forms, möchten and können, the time's
  little words and the days; the verbs part b added (kommen, machen, fahren, geben, sprechen …) are part b's entries.

### The German course: A2, units 1–6

A2's first six units, the dative and where things are (docs/plans/05-built/course-de-a2-a.md). They come after all of A1:

| Unit | Teaches | Its pages |
|---|---|---|
| `wem-dativ` 🎁 | who gets something: dem, der, dem, den + -n; einem, einer, meinem, keinem; geben, zeigen, schenken with the person before the thing | dativ, faelle |
| `mir-dir` 🤝 | mir, dir, ihm, ihr, uns, euch, ihnen beside mich, dich, ihn; gefallen, helfen, schmecken, gehören, danken; es geht mir gut | pronomen-dativ, dativ, akkusativ |
| `mit-nach-bei` 🚌 | aus, bei, mit, nach, seit, von, zu; beim, vom, zum, zur; wo? bei, wohin? zu, woher? von; nach Hause, zu Hause, daheim | praepositionen-dativ, dativ |
| `fuer-ohne` 🧭 | bis, durch, für, gegen, ohne, um, always the accusative; only a der noun shows it; bis zum, where zu decides; set against the dative's list | praepositionen-akkusativ, akkusativ |
| `wo-wohin` 📍 | an, auf, hinter, in, neben, über, unter, vor, zwischen: wo? the dative, wohin? the accusative; im, ins, am, ans | wechselpraepositionen, dativ, akkusativ |
| `stehen-stellen` 🥛 | stehen and stellen, liegen and legen, sitzen and setzen, hängen: the state with the dative, the movement with the accusative; present only; der Sessel, der Kasten | stehen-stellen, wechselpraepositionen |

- **Only what came before around a gap**: no modal but möchten and können, no adjective with an ending, no weil or denn, no reflexive beyond the chunks Setz dich! and Ich setze mich. Prepositions before their unit are fixed chunks (im Gasthaus, in die Küche, auf der Alm). No weak noun takes the dative (Herr, Bub, Bauer, Hirte and Nachbar are left out: `herr-kollege` is B2's), so Herr Rossi only stands as the subject.
- **A summary of its own** (`intro.summary`) on each, where the unit's first page goes on further than the unit.
- **Spoken, right and labelled**: nach Haus for nach Hause, am Tisch, am Sessel and am Platz for auf dem. Every long form is accepted beside its contraction (zu dem Arzt, in das).
- **Their size** runs above the guide: 32 to 35 items a unit. If one feels long, the second sheet's two choices go first.
- **The grading's pairs**: legen and liegen, setzen and sitzen (legt for liegt, setzt for sitzt: a verb of its own); ihren for ihnen, auch for euch, noch for nach, geben or gehen for gegen, ich hilfe, each way where it can go wrong each way. `GermanA2aContentTest`.

### The German course: A2, units 7–12

A2's middle six units, more verbs (docs/plans/05-built/course-de-a2-b.md):

| Unit | Teaches | Its pages |
|---|---|---|
| `muessen-duerfen` 🚦 | müssen, dürfen, wollen, sollen, mögen; the infinitive last; nicht müssen against nicht dürfen; Magst du …? | modalverben, wortstellung |
| `partizip-perfekt` 🧩 | ge-…-t, ge-…-en, mixed, separable, none; haben or sein; ist gesessen, gestanden, gelegen with hat accepted; hat gestellt, hat gelegt | partizip-zwei, perfekt, trennbare-verben |
| `war-hatte` 🕰️ | war, hatte, the modals' -te with no umlaut, es gab; beside the Perfekt, ist gewesen as right | praeteritum-kurz, sein-haben, modalverben |
| `reflexiv` 🪞 | mich … sich; verbs that come with it, sich verkühlen; its place; Perfekt with haben; mir, dir when the verb has another object; Setz dich! | reflexive-verben, pronomen-dativ |
| `warten-auf` 🚏 | each chunk with its case: warten auf, denken an, sich freuen auf and über, sprechen mit and über, Angst haben vor, von; pronouns for people | verben-praepositionen, akkusativ, dativ |
| `am-ersten-mai` 📅 | -te, -ste; der erste Mai, am ersten Mai, vom … bis zum; vor zwei Tagen, in einer Woche; Jänner, Feber, heuer | ordinalzahlen, uhrzeit, zahlen |

- **A form both standards have is two `accept`s, never a `spoken`**: ist gesessen and hat gesessen, Jänner and Januar, Feber and Februar, siebte and siebente. A choice never offers one as wrong: `multi` asks for both.
- **A reflexive line's person in brackets**: `"whole": true` on the list, as the bracket replaces the subject.
- **The grading's pairs**: a participle typed for another a letter away (gesessen, gegessen; gekommen, bekommen, genommen; gesucht, besucht; gestellt, bestellt); a mixed verb's with the infinitive's vowel (gewisst, gekennt); a modal's plural vowel on the singular (ich konn, du wollst), müsst against musst; kannte, darfte, willte for konnte, durfte, wollte; könnte, müsste, dürfte, hätte against konnte, musste, durfte, hatte, and wäre against war, either way; -te for an ordinal's -ste (zwanzigte). `GermanA2bContentTest`.

### The German course: A2, units 13–18

A2's last six units, adjective endings, comparing and clauses (docs/plans/05-built/course-de-a2-c.md):

| Unit | Teaches | Its pages |
|---|---|---|
| `adjektiv-der` 🐕 | no ending after sein; after der, die, das -e in five places, -en everywhere else; dieser, jeder, welcher the same; hoch → hohe, teuer → teure, dunkel → dunkle | adjektiv-der, artikel-genus, faelle |
| `adjektiv-ein` 🏠 | -er and -es where ein, kein, mein have no ending of their own, everywhere else as after der; kein and the possessives in the plural, -en; der neue Hut against ein neuer Hut | adjektiv-ein, adjektiv-der, possessiv, kein-nicht |
| `adjektiv-null` 🥖 | no article: der's ending on the adjective (frischer Käse, mit heißem Tee, mit warmer Milch); plurals, viele, einige, wenige, a number; Guten Morgen but Gute Nacht; Lieber Stefan | adjektiv-null, adjektiv-der |
| `groesser-als` 📈 | -er als; the umlaut and the words with none; so … wie; am -sten, with an e after -t, -ß, -z; gut, gern, viel. After sein or a verb only: before a noun is B1's | steigerung |
| `weil-denn` 🔗 | und, aber, oder, denn, sondern take no place; deshalb (deswegen, darum) takes first place; weil sends the verb last; a weil clause first; Warum? Weil … | konnektoren, nebensatz, wortstellung |
| `dass-wenn-ob` ❓ | dass after glauben, wissen, hoffen, denken, sagen; wenn set apart from wann; ob against wenn; a question word kept, the verb last; a separable verb whole at the end | nebensatz, fragen, wortstellung |

- **What people say is never a wrong option**: wie for als, weil with the verb second, wann for wenn are in the table's `nonstandard` and `verb_second`, almost with the standard form shown, as each is the very thing its unit drills ([Spoken forms](#spoken-forms)); they were `spoken` on each line until B1's integration. A choice on the word order offers denn's and deshalb's own mistakes (Denn er krank ist, deshalb sie geht), never weil er hat; a gap before a clause with the verb second takes `denn`, with `weil` almost.
- **An adjective's table has article and adjective in a cell (der große, den großen), the noun in the head**: the print marks an ending in a cell of two words at most. The pages' tables have the whole phrase.
- **The print marks what differs**: große⟦r⟧ and große⟦n⟧, as kleine⟦s⟧: a column can't tell the adjective's stem from its -e.
- **The grading's pairs**: the comparative's and superlative's umlaut left out or put where none goes (alter for älter, kalter for kälter, grosser for größer, altesten for ältesten, größer for großer), hoher and hocher for höher, hoche for hohe, größesten for größten, denn, dann, wenn and wann for each other. An adjective's ending needs no rule. `GermanA2cContentTest`.

### The German course: B1, units 1–6

B1's first six units, telling the past and the last of the four cases (docs/plans/05-built/course-de-b1-a.md). They come after all of A1 and A2:

| Unit | Teaches | Its pages |
|---|---|---|
| `werden-futur` 🔮 | werde, wirst, wird; to become, with an adjective, a job, an age; ist geworden; Futur I for a promise, with wohl or sicher a guess; the present with a time word as the usual way | werden-futur |
| `praeteritum` 📜 | regular -te, -ete; strong with a new vowel; mixed; a separable verb split; saß, stand, lag; set against the Perfekt, which Austria talks in | praeteritum, perfekt, praeteritum-kurz |
| `als-waehrend` ⏳ | als once, wenn every time and now, wann the question; während, bevor, bis, seit; the verb last, the main verb at the comma | temporalsaetze, nebensatz |
| `plusquamperfekt` ⏪ | hatte or war by the Perfekt's helper; nachdem steps back one tense; what came first told second; war gesessen | plusquamperfekt, perfekt, temporalsaetze |
| `genitiv` 🏡 | des + -s, -es, der; eines, meines; a name's -s, Franz'; the table of the four cases; von + the dative | genitiv, faelle |
| `wegen-trotz` ☔ | wegen, trotz, während, statt, innerhalb, außerhalb; a noun alone bare; innerhalb von; wegen mir | praepositionen-genitiv, genitiv |

- **Only what came before around a gap**: no Präteritum beyond war, hatte and the modals in unit 1, no genitive before unit 5, no relative clause, no zu-infinitive, no obwohl, no Konjunktiv. No weak noun takes the genitive (`herr-kollege` is B2's).
- **`spoken` on every line a form fits**: wie for als and wann for wenn in `als-waehrend` (its point is als against wenn), Nachdem ich gegessen habe and gegessen gehabt, von + the dative and dem Stefan sein, wegen dem Regen and the like, the outline's "labelled, never wrong". Trotz dem, common in Austria, the same. None is a wrong option; a choice before a genitive noun is safe (`Wegen ___ Regens` can't take dem).
- **A distractor is wrong in every gap it could go to**: `wurde`, `dächte`, `hätte`, `wäre` and `weil` were taken out for making right German in some gap.
- **The grading's pairs**: werden's vowel; wurde against werde and würde; a strong past with the present's vowel, each way; the past against would, one umlaut apart (dachte, kamen, gaben); -ete after -t and -d; wegen and statt against their neighbours. The genitive's -s is an ending, so no rule. `GermanB1aContentTest`.

### The German course: B1, units 7–12

B1's middle six, relative clauses, adjectives in full and zu (docs/plans/05-built/course-de-b1-b.md):

| Unit | Teaches | Its pages |
|---|---|---|
| `relativsatz` 👉 | der, die, das, den, dem, denen: the gender from the noun, the case from its own clause; the verb last; a clause in the middle | relativsatz, nebensatz, dativ, akkusativ |
| `relativsatz-praeposition` 🍞 | the preposition first; auf dem, auf den; a verb's own preposition; no contraction; wo; was after alles, nichts, etwas | relativsatz, wechselpraepositionen, verben-praepositionen |
| `adjektiv-alle-faelle` 🧮 | one table for der, ein and none, the genitive and the plural added: the ending shows the case once; alle against viele | adjektiv-tabelle, adjektiv-der, adjektiv-ein, adjektiv-null |
| `schnellere` 🚂 | -er or -st- with the ending before a noun; mehr with none; am besten, lieber; immer kälter; je … desto, umso | adjektiv-tabelle, steigerung |
| `um-zu` 🍳 | zu last after a verb, a noun, es ist; none after a modal; aufzustehen; um … zu against damit; ohne … zu, statt … zu | infinitiv-zu, wortstellung, trennbare-verben |
| `obwohl-trotzdem` ☔ | obwohl with the verb last, trotzdem with the verb next; against weil and deshalb; aber, trotz | obwohl-trotzdem, nebensatz, konnektoren |

- **A relative's `welcher` is an `accept`**, on every typed pronoun; the page calls it heavy, the sheets never show it.
- **An intro's table keeps to twelve rows** where a page's has twelve: `chooseTables` takes a page's table over the intro's when it has fewer rows and the same forms, and a page's cells of three words aren't all marked (`adjektiv-alle-faelle`).
- **obwohl with the verb second and trotzdem as a conjunction are in the table** (`verb_second`, `nonstandard`): almost, as they are the very thing the unit drills, and never a wrong option. `der Tag, wo` and `ohne was zu sagen` stay `spoken`. `der Mann, wo dort steht` isn't in the course, and waits for the native check.
- **The grading's pairs**: denn or deren for denen; the umlaut left out of a comparative or superlative with its ending (altere, großeren, jungste); a separable verb's zu-infinitive without its zu or with one where none goes; Stadt for statt. `GermanB1bContentTest`.

### The German course: B1, units 13–18

B1's last six, mood, voice and small words (docs/plans/05-built/course-de-b1-c.md):

| Unit | Teaches | Its pages |
|---|---|---|
| `haette-waere` 🙏 | hätte, wäre, würde + the infinitive; a thing, a place or state, doing something; asking kindly, the verb first; hatte against hätte | konjunktiv-zwei |
| `wenn-ich-haette` 💭 | könnte, müsste, dürfte, sollte; du solltest, könntest du, dürfte ich; käme, ginge, gäbe, wüsste; wenn + the Konjunktiv II against wenn + the present; Hätte ich Zeit, … to read | konjunktiv-zwei, modalverben, nebensatz |
| `passiv` 🍞 | wird, werden, wurde, wurden + the participle; den becomes der; von + the dative; man; werden's three jobs | passiv, partizip-zwei, werden-futur |
| `woran-daran` 🔍 | wo- and da- with a verb's preposition, the r before a vowel; auf wen, mit wem, auf ihn, mit ihr for people; darauf, dass …; darum and damit's other jobs | wo-da-woerter, verben-praepositionen |
| `jemand-irgendwer` 👤 | jemand, niemand and their cases; etwas, nichts, alles; man, einen, einem; alle, manche, wenige; irgendwer, irgendwelche; einer, keiner, welche standing alone; derselbe | indefinitpronomen |
| `nicht-nur-sondern` ⚖️ | nicht nur … sondern auch, sowohl … als auch, weder … noch, entweder … oder; the commas; the verb after sondern, oder, noch | doppelkonnektoren, konnektoren |

- **Nothing above the level**: no Perfekt of the passive and no modal with it (`passiv-zeiten`), no past Konjunktiv II (`haette-gewusst`), no zwar … aber (`zwar-aber`), no modal + perfect infinitive.
- **A quick question asks a new sentence**, not one its own explanation has just said with the answer in it.
- **`spoken`, right and labelled**: drauf, dran, drüber; Wenn ich Zeit haben würde. **In the table, almost**, as each is the unit's own word: täte for würde, Auf was and the rest, auf ihn for a thing, wer, was, nix. A quick question can't take `spoken`, so none types a wo- or da- word.
- **A form both standards have is two `accept`s**: jemand and jemanden, eins and eines, wärst and wärest, als auch and wie auch, der gleiche and derselbe, kommt and kommen after weder … noch.
- **The grading's pairs**: würde, wurde and werde; kame, gabe for käme, gäbe, wusste and brauchte for wüsste and bräuchte, each way; söllte, wöllte; a participle without its ge on a long stem, gewaschen and gewachsen; the wo- and da- words for each other and with the r wrong (woauf, darmit; darum, warum, worum; davon, davor); demselben and denselben; wieder or jeder for weder, doch for noch. hätt, wär, würd are one answer with the whole word, as hab is. `GermanB1cContentTest`.

## The English course

For grown-ups learning English in the Lake District village (the culture `lakeland`), from Slovene, German or Italian:
visitors and newcomers (2026-11-01, "make it complete"). The English course is `companion/course/en/`: the outline (A1, A2 and
B1 written, 18 units each, each naming the English grammar pages it teaches; B2 planned, docs/plans/05-built/outlines-2.md) and its units, written in the village's adult life: Mrs
Hartley the teacher, Mrs Birkett at the Packhorse Inn, Shepherd Jack and his sheepdog Meg, Mr Dixon the blacksmith,
Granny Maggie and Auntie Edna, Mr Tyson and his mill, Brewer Sam, Old Wilf, Arthur and his bees; the fells, the becks,
the tarn, Keswick eight miles off. The school is the same school, and every part of it is the course's language's:

| What | For English |
|---|---|
| Who teaches | the school's person of the cast (`Residents.brings(SCHOOL)`: Mrs Hartley, her home the school), in the cast's `teacher` voice (Matilda; `@hartley` once she has her own); she teaches the children by day and the grown-ups in the evening class, first names both ways but "Mrs Hartley" |
| Read aloud | the intro's English in her voice: `Spoken.looks` for English (its frequent words; English has no letters of its own, so a bold form is read in her voice when its words are English's or the intro shows them: its examples, its table, its questions, the unit's `words`; an ending, **-teen**, when one of those words ends with it); the explanation in the learner's base as one clip (`sl\|…`, `de\|…`, `it\|…`) |
| The teacher's lines | "Welcome to the school! Sit down, let's start." and "Excellent! You know this already: …" (the app's English strings) |
| Texts per base | every explanation (`say`, `summary`), title, goal, instruction, hint, why, label, column and translation in Slovene, German and Italian, and in English: the format wants it always, so it is plain English, the fallback |
| Grading | the English table (`grading/en.json`): a contraction is its long form (I'm = I am, isn't = is not, he's got = he has got, Jack's at home = Jack is at home); 21 to 99 with or without the hyphen; the American "a hundred five" and the American spelling (color, center, gray, mom, traveling) beside the British; -ize and -ise both; a known mistake wrong only for a word of its group: -s for -es after x, z, ch, sh, ss, -fes for -ves, woman for women, fourty, ninty, for for four, the -ing form's spelling (siting, makeing, writting), the ordinals' (nineth, twelth, fith), were and where for each other; an ordinal with or without its hyphen (twenty-first); a slip just before a long word's last letter forgiven (usualy: English's `ending_letters` is 1); a contraction without its apostrophe almost, never right (cant, isnt, Im: the apostrophe's hint with English's examples, can't, I'm), its, were, lets staying words of their own; those and these for each other, gos and dos for goes and does, flyes and babyes for flies and babies (units 7–12) |
| British, American | British spelling and usage in every text (colour, Mum, the inn, have got, a hundred and five); the American form is right where a learner types it: by the grading (its spelling) or listed in `accept` (Do you have …? beside Have you got …?) |
| The blackboard | "Welcome!" and the day as Britain writes it (1/11/2026); the unit's title and forms in chalk |
| Prints | headed in English and the base ("Rule · Pravilo"), the unit's words with their meaning in the base: from the English packs, the Lakeland packs and the supplement (`lexicon/en.extra.json`, meanings in sl, de and it) |
| Not for English | forms cards, counting cards, the numbers module and the case rows (Slovene's: `numbers` turns nothing on); no `lemma`/`form` on the cells (`FORMS_LANGUAGES` has no English) |
| Checked | `EnglishCourseContentTest` (CourseContentChecks with the bases sl, de and it: every unit played, every right answer graded right, no distractor right or almost, every text in each base), `EnglishA2aContentTest`, `EnglishA2bContentTest`, `EnglishA2cContentTest` (A2's parts and, the last, what people say over the whole level), `EnglishB1aContentTest`, `EnglishB1bContentTest`, `EnglishB1cContentTest` (B1's parts and, the last, what people say over the 18), `SchoolBoardTest`, the smoke's course, course-speech (the Lakeland school), prints (each step's point on the rule line) and meanings (the supplement) |

Writing an English unit: as [Writing a unit](#writing-a-unit), with the explanations in each base (du, ti and tu, as the
app speaks to the learner), every English form in **bold** with its meaning in brackets where the base needs one; the
exercises' texts per base; a grown-up's sentences in the village's life (the inn, the farm and the fells, the smithy,
the bus to Keswick), never the learner's own data (an age is Auntie Edna's, not the learner's); British first, the
American form accepted; every file flagged machine-written in its `review`, for a native check (British English first,
then each base).

- **A contraction is one answer**: list `I'm at home.` or `I am at home.`, not both; the grading takes either. A name's
  `'s` is `is` only before an article, not, a place word, here, there, a possessive, always, never or very: before an
  adjective (`Jack's tired`) list `Jack is tired.` beside it.
- **One answer a gap**, or all of them: "Good ___!" takes morning, afternoon and evening; "Mr Dixon is ___ blacksmith"
  takes a and the (he is the village's), so a gap that tests a or the says which it means.
- **A choice never offers "nothing"**: a gap with no article is a choice of whole sentences (`We're at home.`, `We're at
  the home.`), so the sentence read after the answer is a real one.

### The English course: units 1–6

A1's first six units, the greetings to the numbers (units 7–18 are written by the course's other parts):

| Unit | Teaches | Its pages |
|---|---|---|
| `hello` 👋 | hello, hi, good morning, afternoon, evening, good night only when you leave; I'm, My name's, What's your name?, And you?; you for everyone, Mr, Mrs, Ms, Miss with the surname; This is Jack. He's a shepherd. Nice to meet you (too); How are you? Fine, thanks; goodbye, bye, see you | pronouns (its own summary card: the greetings) |
| `to-be` 🟰 | I am, you are, he/she/it is, we/you/they are (two with and are they); I'm … they're, Jack's on the fell; not after the verb (isn't, aren't, I'm not; in Scotland and Ireland also I amn't, named, right when typed); Are you …? Is she …? Yes, I am (never Yes, I'm); I'm hungry, I'm cold with be | to-be, pronouns |
| `articles` 🏷️ | a for one thing named the first time, the same before every noun; an by the sound (an hour, a uniform); the for what both know, the village's one; a with a job (Jack is a shepherd); no article in general (Dogs are friendly) and in at home, at school, in bed, by bus | articles |
| `plurals` 🐑 | + -s; -es after s, x, ch, sh, potatoes; -y → -ies, -ay, -ey + -s; -f, -fe → -ves (but roofs); men, women, children, feet, teeth, mice, people; sheep, fish, deer the same, the verb showing how many; the plural from two on | plurals, articles |
| `have-got` 🐕 | I've got, she's got ('s is has here); haven't got, hasn't got; Have you got …? Yes, I have; have alone and American Do you have …? (both right); have breakfast, have a cup of tea, never have got | to-have |
| `numbers` 🔢 | 1–20 (zero, oh on the phone), -teen and -ty (forty without u, the stress of thirty and thirteen), 21–99 with a hyphen, How many …? with the plural, age with be, a hundred and five, no -s on hundred after a number, three pounds fifty | numbers, plurals |

- **The grammar of units before only, around a gap**: possessives (my, his, her, Jack's dog) come with `possessive`, the
  present simple with `present-simple`, there is with `there-is`: units 1–6 say "Jack's got" (has got, unit 5 on),
  "My name's" (a phrase of `hello`), never "her cakes" or "Sam says" in a sentence with a gap.
- **A known mistake as a distractor**: plurals' knifes, childs, berrys and numbers' fourty, twelf, five hundreds are
  wrong by grading/en.json (its `wrong`, an ending or the distance), never almost (`EnglishCourseContentTest`).
- **The spelled forms in the intro's table** (buses, dishes, families, calves, roofs): a bold form of the explanation is
  read in Mrs Hartley's voice when English's frequent words or the intro's own words make it English; a plural the
  table doesn't show would be read by the phone's voice of the base. An ending in bold is hers when a word the intro
  shows ends with it (**-teen** of thirteen, **-ty** of twenty: `IntroSpeech.endings`).
- **A worksheet's title per base is the base's own words** (`{"en": "Count", "de": "Zähl"}`): the app shows the
  course's language's and the base's side by side ("Count · Zähl"); "Count · Zähl" as the German showed "Count · Count ·
  Zähl".
- **A wrong form in a step's English as "**right** (never *wrong*)"**: the sheet's "Watch out" pairs a "never *X*"
  with the most alike bold form of its paragraph, so "Never *I amn't*: only **I'm not**" printed I amn't against we
  aren't; "**I'm not** (never *I amn't*)" paired them. (I amn't is said, so `to-be` no longer says never of it: [What people say](#what-people-say-over-a1-and-a2).)


### The English course: units 7–12

A1's middle six units, after the greetings, be, the articles, the plural, have got and the numbers (units 1–6):

| Unit | Teaches | Its pages |
|---|---|---|
| `this-that` 👉 | this, these for what is near, that, those for what is further away, only for how many (this man, this woman); before a noun or alone (What's this? This is Jack.); this one, that one; Hello, this is Sam on the phone, never here is Sam; that's, and no short this is; it and they in the answer; this morning, that day | this-that (new; its card is the summary) |
| `possessive` 🐾 | my, your, his, her, its, our, their before the thing, never changing and never with the (my dog, not the my dog); his and her by the owner (Mrs Birkett and her son); Jack's dog, Mrs Birkett's inn; the boys' bikes, the children's books; its against it's, their against there | possessive-s (a summary of its own: no mine, yours) |
| `present-simple` 🔁 | habits and facts; I, you, we, they walk; he, she, it walks, with no is before it; -es after s, sh, ch, x and in goes, does; a consonant + y → -ies, but plays, buys; has; how often at the end; always a subject (It rains) | present-simple (a summary of its own: no don't, no questions) |
| `questions-do` ❓ | Do you …? Does she …? with the base form (never Does Meg barks?); don't, doesn't + the base form; Yes, I do. No, she doesn't.; where, what, when first; no do with be or have got, and Do you have …? right too | questions-do, present-simple |
| `word-order` 🧩 | subject – verb – object; the place, then the time at the end; a time in front keeps subject and verb together (never In the morning feeds Jack); always, usually, often, sometimes, never before the verb and after be; the adjective before its noun, never with -s | word-order, present-simple |
| `there-is` ✨ | there's for one thing and what isn't counted, there are for more; there isn't a …, there aren't any …; Is there …? Are there any …? and the short answers (never Yes, there's); How many … are there?; there is against it is | there-is-are, plurals |

- **The grammar of the units before only, around a gap**: this-that and possessive say only be and have got around
  their gaps (no -s verb: "Mr Dixon is in ___ (he) smithy"); present-simple has no frequency word (word-order) and no
  don't or does (questions-do); there-is has some only in a yes sentence and any as the page has it, and no there was.
- **A gap that near or far decides says where**: "___ scones here on the plate", "___ fell over there"; without here
  or there, these and those both fit. "Hello, Mrs Hartley, ___ is {learner}" takes this and it (Hello, it's Sam is
  British too).
- **The owner in brackets**: a possessive gap's cue is the owner (`___ (she) kitchen`, `___ (Mr Dixon) hammer`), and a
  transform line gives the thing and its owner (`a dog (Jack)` → `Jack's dog`), never the un-English "the dog of Jack".
- **A decoy is a learner's real mistake that grading/en.json grades wrong, and right in no gap**: he's and they're for
  his and their (two words, never almost); carrys and gos; it and has for there is (Slovene's je, German's es gibt,
  Italian's c'è). Not isn't in a text of there is and there are: "there ___ an old inn" takes it too. The known
  mistakes these units added to `wrong`, each only for a word of its group: those for these and these for those, gos
  and dos, a consonant + -yes for -ies (flyes, carryes, studyes, babyes); plays and buys keep their slips.
- **A verb the packs have as a noun** (walk: sprehod, play: igra, watch, rain: dež) is named "to walk" in `words`, as
  the packs name their verbs (to fly, to carry), and its supplement entry has "to walk" among its forms; else the sheet
  gives the noun's meaning.
- **Place before time is the usual order, not a rule**: "every week in Keswick" is a choice's `almost`. Sometimes and
  usually may start a sentence (accepted), often may not.
- **The apostrophe**: Jacks for Jack's and Poppys for Poppy's are wrong (an ending), so are its for it's and their for
  they're (one word for two). A contraction typed without its apostrophe (dont, doesnt, isnt, Im) is almost, with the
  apostrophe hint (grading/en.json's `loose`): a phone keyboard hides the apostrophe.

### The English course: units 13–18

A1's last six units, from where things are to the past of be, after the present simple, do, the word order and there
is (units 7–12), in the village's grown-up life (the Packhorse Inn full at night, Mr Dixon's gates, Mr Tyson's sacks,
Old Wilf by the fire, the bus to Keswick, the market):

| Unit | Teaches | Its pages |
|---|---|---|
| `prepositions` 📍 | in (inside, a town: in Keswick), on (a surface: on the fell), at (a point, where you do something: at the inn; at home, at work with no the); under, behind, in front of (never without of), next to, by, between … and …, near; the noun never changes; Where's …? Where are …? It's …, They're …; to for where to (go to the inn), but go home; into | prepositions-place (its own summary card), there-is-are |
| `can` 💪 | can for everyone, no -s, no to (never she cans, He can to drive.); can't, cannot; Can you …? with no do, Yes, I can; Can I …? You can … for asking and allowing (znati, moči, smeti; können, dürfen; sapere, potere); Could you …, please? Of course. | can (its own summary card) |
| `present-continuous` 🏃 | am, is, are + -ing for now (I'm walking: the others' present, gerade, stare); isn't, aren't; Are you …? Yes, I am; What are you doing?; making, sitting, swimming, lying; every day and usually with the present simple, now, Look!, Listen! with -ing; know, like, want, need, understand never with -ing | present-continuous, present-simple |
| `time` 🕖 | What time is it? It's seven o'clock; past up to half past, to the next hour after it (five past seven, a quarter to eight); British half seven for 7:30 (halb acht, pol osmih: never halb sieben), the digital seven thirty and the American ten after eight accepted; at a clock time and at night; on a day and a date (on Monday, on the first of May), at the weekend (on the weekend accepted), every Sunday with none; in a month, a season, a year, a part of the day; the capitals; the ordinals (first, second, third, fifth, ninth, twelfth, twentieth, twenty-first): written 1 May, said the first of May | time-dates (new), numbers |
| `object-pronouns` 👉 | me, you, him, her, it, us, them after a verb and after a preposition; the subject before the verb, the object after it (She likes him. He likes her.); her the object and the possessive, him never his; a pet with a name him or her, a thing it; It's me. Me too.; one pronoun only (never I know him Jack.); Give me the key, Give it to me | object-pronouns (new), pronouns |
| `was-were` ⏮️ | was for I, he, she, it; were for you, we, they, and you were for one person (people also say you was, named; writing wants you were); wasn't, weren't; Was it …? Were you …? Yes, I was (in the North also Yes, I were, named; never Yes, I wasn't); yesterday, last night, two days ago; there was, there were; where and were; born with was (Auntie Edna was born in 1952, never is born) | was-were (new), to-be |

- **Pages written for them**: `time-dates`, `object-pronouns` and `was-were`, each A1 with its cheat-sheet card (the
  book had the place prepositions, can and the present continuous; past-simple is A2, so was and were have a page of
  their own that sees it).
- **Gaps a preposition alone decides**: a picture would say under or behind, a sheet can't, so a gap is kept to what
  fixes its preposition: the word after it (`in ___ of`, `next ___ the inn`), between … and …, at home, at work, a town,
  or a cue in brackets (`___ (below) the table`); where in and at both fit (`He's ___ the inn`), both are listed.
- **A name's 's before -ing** isn't is for the grading (it is only before an article, not, a place word …): a line
  that may be typed `Meg's running up the fell.` lists `Meg is running up the fell.` beside it.
- **The -ing spelling and the ordinals are the rule, not a slip** (grading/en.json's `wrong`, each only for a verb or
  a number of its rule): siting, runing, hoping, makeing, comeing, writting, comming; nineth, eigth, twentyth,
  fourtieth; and were and where for each other. A distractor may offer them (`siting`).
- **A contraction without its apostrophe** (cant, isnt, Im, theyre, wasnt) is almost, with the apostrophe hint and
  English's examples (`loose`, `loose_examples`: "can't, I'm"), never right; its, were, well, lets stay words of their
  own, so were for we're is wrong.

### The English course: A2, units 1–6

A2's first six units, commands and the past, after all of A1 (A1 left the imperative out, so A2 opens with it):

| Unit | Teaches | Its pages |
|---|---|---|
| `imperatives` 📢 | the base form for everyone; Don't + the base form; be; always, never; Let's, Let's not; please, Could you …?; directions, recipes, signs | imperatives (new; its card is the summary) |
| `past-regular` 🚶 | -ed for every person, no helping verb; yesterday, last week, ago; -e + -d, the doubled consonant, -y → -ied against stayed; the three sounds; British learnt, burnt, dreamt with learned accepted | past-simple (a summary of its own) |
| `past-irregular` 🧳 | about thirty irregular pasts, by families; put, cut, shut the same; read said like red; told and said; told in a story with -ed verbs | past-simple (a summary of its own) |
| `did-you` 🔍 | didn't and Did + the base form; short answers; Where did you go?; who as the subject takes no did; be takes no did | past-simple, questions-do (a summary of its own) |
| `past-continuous` ⏳ | was, were + -ing; wasn't, weren't, Were you …?; the long action with when; while; -ing's spelling; state verbs without -ing; how a story opens | past-continuous (new), past-simple, present-continuous |
| `because-so` 🔗 | and, but, or; because against so, one in a sentence; Why? Because …; when, before, after and their comma; then; all of it in a story | linking-words (new), past-simple |

- **A summary of its own** where a page goes on further than the unit that heads it: `past-simple` has the past's questions, which `did-you` brings.
- **cos, 'cause and cause** are right on every gap whose answer is because, labelled with because; `Don't let's wait` beside Let's not wait.
- **Paired mistakes** (`wrong`), each only for a word of its rule: stoped, planed for stopped, planned; openned, visitted; carryed, staied; come for came, make for made and the other way (did you came?); fell and feel, want and went, wore and were, sold and told, bought, brought, caught, taught, thought, fought; then for than or when; rainning, waitting. goed, runned, heared were wrong already; walkd, becouse, hapy stay slips.

### The English course: A2, units 7–12

A2's middle six: how much, comparing, how, after the past (units 1–6). The Lakeland's own: a jar of Arthur's honey, Keswick further away than the mill, Alfie the youngest in the family:

| Unit | Teaches | Its pages |
|---|---|---|
| `some-and-any` 🥛 | countable and uncountable; some in statements and offers, any in questions and negatives; no article before what can't be counted; some in a plain question spoken | some-any, articles |
| `how-much` 🧮 | much and many, a lot of, a few and a little, how much and how many; loads of, There's not many boats spoken | some-any, plurals |
| `comparatives` 📏 | -er and more, the spelling (bigger, happier), good and bad, than; further and farther; more bigger left wrong | comparatives (new) |
| `superlatives` 🏆 | the -est and the most; best, worst; in the village, of the three; without the where no noun follows, plain right (Alfie is youngest) | comparatives, articles |
| `adverbs` 🐢 | -ly and its spelling; well, fast, hard; an adjective for a thing, an adverb for what is done; where each goes; good for well and a flat adverb `nonstandard` | adverbs (new), word-order |
| `someone-anything` 🔎 | some-, any-, no-, every- with -one, -thing, -where; one negative in writing, a double negative `nonstandard`; someone and something in a question spoken; no one with nobody and no-one | indefinite-pronouns (new), some-any |

- **A bold form is read in her voice only if the intro shows that very word**: wetter, easier and money had to go into `words`.
- **A page's `rule` is at most 1,200 characters per base**, a `review` at most 200.
- **Paired mistakes**: then and than; biger, hoter only for big, hot, thin, fat, wet, sad, red, fit; happyer, easyest for a word in -ier, -iest; happyly for -ily; carefuly. usualy stays a slip. A trap for whoever writes more: `.*[^aeiou]y(er|est)` makes shyer wrong for shier, so a unit that asks those lists both.

### The English course: A2, units 13–18

A2's last six: the future, must, the perfect, if. Saturday's dance at the Packhorse Inn, Mrs Birkett's rules for the help, a wet morning with Sam's van, Jack and Meg on the fell, Old Wilf by the fire, a walker who has never been to the Lakes:

| Unit | Teaches | Its pages |
|---|---|---|
| `going-to` 🗺️ | going to for a plan and for what you can see coming; am, is, are + -ing for what is arranged; never the plain present (Morgen besuche ich, jutri grem, domani vado); gonna spoken | future, present-continuous (a summary of its own) |
| `will` 🤝 | decided now and offers (never I carry it), promises and won't, what you think; Will you …? Yes, I will (never Yes, I'll); Shall I …? with Should I …? and Scotland's and Ireland's Will I …?; will against going to; I will isn't ich will | future (a summary of its own) |
| `have-to` ☝️ | have to, has to, Do you have to …?, had to, have got to with gotta; must with no to; mustn't against don't have to (muss nicht), can't, needn't; should | must-have-to (new), can |
| `would-like` ☕ | want to, wanna; 'd like with a thing and with to; Would you like …? Yes, please; Could you …?, Could I …?; like, love, enjoy, hate + -ing (never enjoy to); like against 'd like | would-like (new) |
| `have-you-ever` 🐦 | have, has + the participle for your life so far; its forms; gone and been; ever, never with one no in writing; just; already, yet; the past simple as soon as you say when; American I just saw Jack, Did you feed the dogs yet? accepted | present-perfect (a summary of its own, no since or for: B1), past-simple |
| `if-it-rains` ☔ | if + the present in both halves, always so; if + the present, will; the halves either way, the comma; if against when (German's wenn for both), the present after both (never if it will rain, Slovene's če bo); the other half with can, should, a command | first-conditional (new), future |

- **A bracketed translation is one sentence** ("(Die Tasche ist schwer, ich trage sie.)"): the sheet's rule line splits at a sentence end even inside brackets, and "(Die Tasche ist schwer. Ich trage sie.)" printed cut.
- **A gap takes all that is right**, not only what the unit teaches: "___ I close the window?" takes Shall, Should, Can, Could, May, with Will spoken; "You look tired. You ___ rest." takes should, must, have to, need to, ought to; "I think it ___ snow" takes will and is going to; "Have you ___ been to the Lakes?" takes ever and never.
- **The plain present isn't always wrong**: a timetable's "The bus leaves at nine" is right, so it isn't offered as a mistake, nor is the colloquial "It's raining tomorrow".
- **A choice's `almost`** for what is right but off the point: "I'm going to" where the phone is ringing, "I'll" for what was decided before.
- **'s before a participle is has in the grading**, listed once: she's seen = she has seen, Jack's never been; before one that could be an adjective or a passive (done, made, lost, closed, written …) only with just, already, never, ever or an object after it, so it's written stays it is. `same`'s order matters: these rules stand before he's → he is. A name's 's before -ing still lists its long form beside it; before going to it doesn't need to.
- **Typed without the apostrophe, almost**: hes seen, id like, and ill help, well go only before another word, so the words themselves stay words.

### What people say, over A1 and A2

As [Spoken forms](#spoken-forms): no form grown-ups really say is a wrong choice or a bank's distractor, no text says never of it (the intro and the page say people say it and writing wants the other), and typed it is one of two things. `EnglishA2bContentTest` holds it for its six units, `EnglishA2cContentTest` for the 36 of both levels.

| What's typed | How | Verdict |
|---|---|---|
| gonna, wanna, gotta; cos, 'cause; some in a plain question; There's no walkers; loads of; Will I …? beside Shall I …? | the item's `spoken` | right, labelled with the standard form |
| good for well, a flat adverb; a double negative after a negative (nothing, no one, not never); It were raining, We was late, Yes, I were, There was a lot of walkers; I seen it, I done it, I sung; a past for a participle after have (I've saw, He's went, She's took) | the table's `nonstandard` | almost, the standard form and "People say it like this; in writing it's the form shown" |
| amn't | a contraction like any: am not | right |

**`nonstandard` is for what grown-ups really say.** A child's or a learner's regular past (telled, catched, knowed, throwed, growed) isn't, any more than gooder and more bigger: those are wrong, and a sheet may offer them (`past-irregular`'s telled).

Left wrong, on the native list: more bigger, gooder, as for than, How much eggs?, tastes well; he have to (East Anglia's), still a wrong choice in `have-to`; come, give, run for came, gave, ran (the cue copied, and what the unit teaches); haven't to; I've knew, I've flew.

### The English course: B1, units 1–6

B1's first six: time. Each names its new page, and A2's or A1's where it goes on from them:

| Unit | Teaches | Its pages |
|---|---|---|
| `for-since` 🏡 | have, has + the participle for what still goes on; for and since (German's seit, Italian's da for both); How long?; no with for and since; since + a clause in the past simple; the past simple for what is over, ago | for-since (new), present-perfect |
| `been-waiting` ⏱️ | have been + -ing; questions and no; what you can still see; the activity against the result (how long, how many); know, own, belong, understand, believe, be, have stay simple; live, work, stay, wait either way | present-perfect-continuous (new), present-perfect |
| `past-perfect` 🚌 | had + the participle for what came first; 'd, hadn't, Had you …?; by the time, already, just, never … before; when + past simple or had; what is told in order stays simple; after and before take either; because + had | past-perfect (new), past-simple |
| `used-to` 🕰️ | used to + the base form, state and habit; there used to be; didn't use to, never used to, used not to; Did you use to …?; the past simple for when, how long, how many times; no present; any more, still | used-to (new), past-simple |
| `future-forms` 🚏 | will for what you think, going to for what you can see; the present for a timetable; arranged, planned, decided now; will be + -ing at a time, won't be; Will you be …? as polite, and what happens anyway | future-continuous (new), future |
| `question-tags` 🏷️ | tags with be, do, does, did, have, will, can, was; aren't I, isn't there, shall we, will you, did they; falling and rising; So do I, Neither do I; short answers, Have you? | question-tags (new), to-be |

- **A wrong option doesn't repeat the verb**: "It ___ rain" with "rains" prints "It rains rain". A choice's options are full forms, not contractions: "It 's going to rain" prints with the space.
- **The point of used-to is on typed lines**: a choice would have to offer what people say (I use to, didn't used to). Its wrong options are forms nobody says (used to living, used live).
- **Same-way tags are real** (So you're coming, are you?), and so is isn't it for any person in Britain's talk: question-tags' wrong options take the wrong helping verb instead (doesn't he, hasn't he).
- **The `words` list picks the print's sense**: run a list through `school-prints --explain` and read the meanings (ages as Alter, run as laufen, gate as portone were wrong until they were phrases).

### The English course: B1, units 7–12

B1's middle six: clauses, voice, verb patterns. Who is who at the Packhorse, the bread and the stolen van, Poppy and the fiddle, Sam's bad day, Mrs Birkett's soup, Old Wilf alone by the fire:

| Unit | Teaches | Its pages |
|---|---|---|
| `who-which` 👉 | who, which, that with no comma; the object left out (the cheese Arthur likes), no pronoun coming in (the book I read); where, whose against who's; the small word at the end (the woman I spoke to), whom only very formal; what people say | relative-clauses (new) |
| `passive` 🏗️ | is, are + the participle; was, were; the participle against the past, It was broke said, got stolen right in talk; by, German's von and Italian's da; be asks and says no, with no did; can be, must be on notices | passive (new), past-simple |
| `verb-patterns` 🚶 | verbs with to; verbs with -ing; those with both, and would like with to only; stop and remember, whose meaning changes; -ing after a small word, even look forward to; someone + to against dass, da, che; let and make with no to; try and come | verb-patterns (new), would-like |
| `phrasal-verbs` 🧩 | what one is, only the verb changes; no object; stays together, the pronoun at the end; can part, the noun in both places; the pronoun in between (Turn it off); find out, look up, put off, get on with, wash up, fill in with American fill out | phrasal-verbs (new), object-pronouns |
| `too-enough` 🌡️ | too against very; enough after an adjective, before a noun; too cold to swim, enough to, for me, for swimming; too much, too many; a bit, quite, very, really, quite a, spoken pretty, right, dead; as … as, not as … as beside the comparative | too-enough (new), comparatives |
| `myself` 🪞 | one form per person; with no help; verbs with no -self where the bases have se, sich, si, and enjoy, which needs it; on my own, by myself; each other against themselves; one, ones | reflexive-pronouns (new), object-pronouns |

- **The Watch out box reads English**: `**R** (never *W*)` pairs them whole. "**Turn it off.**, never *Turn off it*" printed ✓ turn off, because the box looked for a near bold form; the bracket fixed it.
- **A gap that teaches too still takes what's right**: "The soup is ___ hot: I can't eat it" takes too, so, very, really; "We haven't got ___ chairs!" takes enough, many, any. The choice in the intro, with wrong options nobody says, is where too alone gets tested.
- **A choice never offers was against were**: a typed gap asks it, and `was` is almost. Its wrong options are is make, was buy, were catch.
- **'s before a participle in a passive**: "It's made here" is it is, "It's seen" is it has by A2's rule. No line asks the second, and none asks a noun's 's (The bread's baked here), which the table doesn't read as is.

### The English course: B1, units 13–18

B1's last six: if, might, what was said. Old Wilf by the fire and what he would buy, a wet night at the inn and who is at the door, the dance Sam's van missed, Jack in the storm, Edna's news of Sam, a stranger at the inn:

| Unit | Teaches | Its pages |
|---|---|---|
| `if-i-had` 🚣 | if + the past, would; could, might; the halves either way; What would you do if …?; If I were you, was spoken; would kept to the other half, If I would have a boat as a common slip; against the first conditional | second-conditional (new), first-conditional |
| `might-must` 🤔 | may, might, could; might not; can for what sometimes happens; must and can't for what you work out, must not and mustn't American and northern; be + -ing; probably, perhaps, maybe | might-must (new), must-have-to |
| `if-i-had-known` 🕰️ | if + had, would have; the two 'd; hadn't, wouldn't have; could have, might have; would've, woulda, would of, If I'd have known; now against then; have, never be | third-conditional (new), second-conditional, past-perfect |
| `although-however` ⚖️ | although, though, even though; one joining word; despite, in spite of; however, though at the end; to, in order to, so that, so spoken; because of; as a result | contrast-linkers (new), linking-words |
| `reported-speech` 💬 | said and told; one step back; the tense kept if it's still so; person, place, day; told me to, told me not to, asked me to | reported-speech (new), past-simple |
| `reported-questions` ❓ | asked where + a statement's order; asked if, whether; the step back; in the present, Do you know where …?, I don't know if …; what's said in Ireland, Scotland and the North; who as the subject; asked me | reported-questions (new), reported-speech, questions-do |

- **The two 'd in the table** (`grading/en.json`, `same`): 'd is had before a participle (a list of irregular ones, any -ed but need, feed and the like) and before better, would before anything else; a verb whose participle is its base form (come, run, put) is had only after if + a subject. **Order matters:** every rule for had stands before the catch-all `'d (?=\p{L})` → would, and `'d like`, `'d love` before them all. If I'd have known is had have, the row before those.
- **That and whether are rules**: that after said and told, whether for if after asked, know, wonder, so a reported line lists its subject and its tense only (`spoken` holds at most 8).
- **Mixing tenses is the one to watch**: If I have time, I'd help is natural, so have is no wrong option for If I had a boat. would have is: the classic mistake, and wrong.
- **Direct speech in “…”**: the sheets and the prints show it as written, and the grading strips the quotes.
- **Rows of the intro's table count on a phone**: three columns of sentences take three lines a row; past about eight, two columns (`reported-speech`). Two columns of a sheet's table hold 12 letters, one holds 18: wouldn't have stayed (20) became wouldn't have gone.

### What people say, over B1

As over A1 and A2, and only in the variety the course teaches: British, as the Lake District hears it. `EnglishB1aContentTest` and `EnglishB1bContentTest` hold their six units, `EnglishB1cContentTest` the 18. At this level the point of a unit is often such a form, so a choice can't test it: its wrong options are what nobody grown up says (lived I, despite of, told to me, would be come, used to living), and typed gaps and lines do the rest.

| What's typed | How | Verdict |
|---|---|---|
| If I was you, If it wasn't raining; woulda come, coulda helped; so for so that, cos of; the tense kept (She said she is tired, He asked where I live); innit where the tag is isn't it; Me too, Nor me; got stolen; right cold, dead cold; I made me a cup of tea; try and come | the item's `spoken` | right, labelled with the standard form |
| It must not be Jack, It mustn't be Jack, It won't be Jack beside can't; amn't I, used not to, never used to, Nor do I; anymore | the item's `accept`, or the table's `same` | right |
| would of, could of, should of, might of, must of; If I'd have known, If I had have known; He asked where did I live, He asked did I want tea, Do you know where's the inn?; I been waiting; I didn't used to, I use to live; ain't; innit, isn't it for any person; the man what lives there; It was broke, She was took; hisself, meself, theirselves | the table's `nonstandard` | almost, the standard form and "People say it like this; in writing it's the form shown" |

Left wrong, on the native list. **would after if** (If I would have a boat, If it would rain, If I would have known): the classic mistake from German and Slovene, not what this variety says; the page names it as a common slip and a choice may offer it (the lead's ruling, 2026-11-03). Ireland's present for the perfect (I'm here since Monday). If I know the way, I'd tell you, typed in a gap: a tense mixed. said me, told to me, asked to me, despite of, Although it rained, but we walked, would be come.

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
| **Besede · Words** | the unit's `words`, with gender and meaning in the sheet's base, in this order: the unit's own gloss ("**quando** (wenn)" in its intro, unless its English bracket is a note: "**rad** (a man)"), the dictionary's supplement's entry of the word with its own meaning in the base (an Italian noun by the word without its article, il treno), a pack's word (an Italian noun in the plural with its article, i cavalli, by the pack's plural, its singular beside it), then the supplement's and Wiktionary's other readings (the meaning the unit's own text points to: peti is "fifth" in the numbers); not the ones the table shows, nor names. A word nothing knows is left out (the run lists them); the Italian course's A1, A2 and B1 leave none out (the supplement has the rest, it-print-meanings) |
| **Preveri se · Check yourself** | five items of the worksheets (the test stays the app's): gaps in their sentence, transforms, choices (their options in the sheet's own order, `printedOrder`: the same on every print of it, never the file's, the answers naming the right one by its text), grouped by instruction; ten on a sheet of two pages. The answers upside down at the page's foot |
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
