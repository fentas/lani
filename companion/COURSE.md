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
- `planned: true`: a unit written later. It shows greyed in the school ("📝 Kmalu · Coming"), can't be opened, and doesn't
  count for its level. `grammar` (optional) names the grammar pages it is to teach, for whoever writes it.
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
in the teacher's voice (Mojca's: her voice of the cast, `@mojca` once she has her own), with what is read marked as it
goes (`data/IntroVoice.kt`, what is read: `data/IntroSpeech.kt`):

1. **The explanation** (`say` in the learner's base). Its text without the Markdown marks, the Slovene forms left inside
   as they are ("Biti (to be) has a short form … jaz sem (I am), ti si (you are) …"), is one clip of the voice store:
   the node voices it in one go with ElevenLabs (eleven_v3 says both languages as they are; `POST /voice/say` with
   `lang`, keyed `en|<the text>` apart from the Slovene lines, shared by every learner of that base: README "Voice").
   On the phone it plays at once, the bubble outlined. When it isn't there yet (offline, no ElevenLabs, the quota at
   its reserve, not made yet) the step is read in its runs at once, and the node makes the clip for next time:
   - the base-language parts by the phone's own voice of that language (Android text-to-speech, its locale; rendered
     to files as the car's prompts are), a translation in brackets right after a form ("(I am)") among them;
   - the Slovene parts in Mojca's voice, at their place: a span in **bold**, *italics* or „quotes" that the app's
     check says is Slovene (as the chat's Markdown taps it), or whose words the intro itself shows (its examples, its
     table, its questions, the unit's title: "**Biti**");
   - each run marked in the bubble as it is read, a short pause between them.
   The phone without a voice for the base language: only the Slovene is read, and a small note says why, once.
2. **The examples**, one after the other in Mojca's voice (their translations not read), each card marked.
3. **The quick question**, as far as it gives nothing away: a gap read as a pause ("Luka … na pašniku."), a choice's
   options never, a translation in brackets at its end left out. Once answered, the sentence with the answer is read
   (as an exercise's feedback), and Mojca reacts (a cheer, a smile, a shrug).

The Slovene of the reading comes as a line's 🔊 does: her clip on the phone, else the node voices it now, else another
of her voices' clip on the phone, else the phone's offline voice (Piper), else the phone's voice for Slovene. 🔊 on the
step reads it again (⏹ while it reads), 🐢 slower; a tap on the text stops it, so does leaving the step, any other 🔊 or
the 🎤 (one voice at a time). Mojca's portrait breathes and blinks, talks while her voice plays (also a 🔊 of hers on
the school's screens) and holds still with the system's animations off.

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
- Help is always there: the cheat sheet one tap away (📋 over the sheet, the unit's cards first), the sheet's words
  looked up as an exercise's (never what it tests while it's open: a sort's words, what brackets add, only once
  checked), a 💡 hint per gap, cell and line, and 💬 the tutor with the sheet attached. An item's `explain` shows in
  shape as an exercise's (module-spec.md, "Explanations": one bold rule line, the examples as a list).

## The school's exercise types

Four types for a page of work at once. Each names its rule like any exercise (`grammar`, a page id); an item may name its
own (`grammar` on a gap, a cell, a line). Graded on the phone with the app's rules (`Grading`: č, š, ž forgotten is
almost; a slip in the stem forgiven; never the ending), each item marked right, almost or wrong with its correction
once the sheet is checked. They are worksheet exercises (in a unit, a test, homework); modules will take them once every
phone has an app that plays them.

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
- Checked all at once ("Preveri · Check"): each gap right, almost or wrong, its correction under it.

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

- **One gap a sentence** where it can be: the corrections show each gap in its sentence ("Babica ___ v kuhinji."), and a
  slip is kept with that sentence on the rule's page.
- **A cue in brackets after the gap** when the word to change is given: `"To je ___ (moj) hiša."`; a transform list's
  noun to put in: `"To je moj brat. (sestra)"`.
- **A table's labels short** (`"jaz · midva · mi"`, `"1. kdo? kaj?"`) and its columns two-language (`"ednina · one"`):
  TalkBack reads a cell as its row's label and its column's.
- **Name the form** of a table's cells and a transform's lines (`lemma` + `form`) when the word is one the learner may
  have as a card (biti, imeti, a noun of the packs): a wrong one then comes back first in the card's review. The keys are
  the forms book's: `pres.1sg` … `pres.3du`, `neg.*`, `fut.*`, `past.m.sg` … `past.f.du`, `imp.2sg` …, the cases
  `nom|gen|dat|acc|loc|ins` `.sg|.pl|.du`, `adj.m.sg` ….
- **Several right answers**: list every one in `accept` (with and without the pronoun: `"Nisi lačen."`, `"Ti nisi
  lačen."`); case and the final full stop don't matter, č/š/ž forgotten is almost.
- **The word bank** for the first gap text of a unit (easier), none for the second and the test.
- **The intro's table** is the rule's table built row by row: give the head once (`intro.table.head`) and each step's
  new rows; a step without rows (an agreement step) only talks and asks. The last step is the exception or the twist.
- **The test** mixes the worksheets' types with new sentences: about 10–13 items, `pass` 80.
- **The outline** (`course.json`): when a unit is written, remove its `"planned": true` and its `grammar` hint stays a
  hint; the A1 units planned are, in order: osebni-zaimki, sedanjik, mnozina, tozilnik, vprasalnice, stevila,
  imeti-iti, nikalnica-rodilnik, mestnik, kdaj-cas, velelnik, modalni-glagoli, povratni-glagoli, pretekli-cas,
  prihodnjik, dvojina (each with the grammar pages it is to teach). A unit may only use the grammar of the units before
  it around its gaps: biti and spol first, then the pronouns and the present, so the cases come after sedanjik.

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
| **Pazi · Watch out** | three wrong ✗ / right ✓ pairs: what a text says is wrong ("**Kje je Luka?** (never *Kje Luka je?*)", "Never *ne sem*", "*Vidim nič* is wrong"), a quick question's or a worksheet choice's wrong option in its sentence, a gap whose word in brackets left unchanged is the mistake ("To je ___ (moj) hiša.") |
| **Besede · Words** | the unit's `words`, with gender and meaning: a pack's word, the dictionary's supplement, Wiktionary (the meaning the unit's own text points to: peti is "fifth" in the numbers); not the ones the table shows, nor names. A word nothing knows is left out (the run lists them) |
| **Preveri se · Check yourself** | five items of the worksheets (the test stays the app's): gaps in their sentence, transforms, choices, grouped by instruction; ten on a sheet of two pages. The answers upside down at the page's foot |
| The footer | "Strojno napisano, preveri z naravnim govorcem · machine-written, to be checked by a native speaker" while the unit's `review` says it's unreviewed; the Wiktionary attribution when a meaning comes from it |

The layout (`companion/prints/html.ts`, `sheet.css`): A4 with 12 mm margins, Nunito with the Lani mark in Pixelify Sans
(the site's fonts, inlined), tables at 11 pt or more, black-and-white safe (an ending is bold and underlined on its
tint, a mistake struck through with ✗, the right form bold with ✓). Each unit is written as one page and as two;
`fit.js` keeps, in the page before it prints, the first that fits: as it is, with the table beside the rule, tighter,
without the optional table and the sixth example, tightest; the room left becomes ruled lines for notes.
