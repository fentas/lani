# Built: the English course, A1 part a (2026-11-01)

Wave B, batch 2 (followups-7.md, sections 1–6): a learner of English in the Lake District village had an empty school.
Part a writes the course's first six units and the groundwork the parts b and c (and, where it is shared, the German
course) build on. Everything written in a language is machine-written (Claude) and waits for a native check: the list
is at the end.

## What was built

### The six units (companion/course/en/units)

A1 size, as the Italian A1 units: five intro steps with a quick question each, three worksheets, a test (pass 80).

| # | Unit | Teaches | Pages | Sheets' items | Test items |
|---|---|---|---|---:|---:|
| 1 | `hello` 👋 | greetings by the time of day, I'm, My name's, What's your name?, you for everyone, Mr/Mrs/Ms/Miss, This is … He's …, Nice to meet you, How are you?, goodbye, good night only when leaving | pronouns (own summary card) | 29 | 10 |
| 2 | `to-be` 🟰 | am, is, are; I'm … they're, Jack's; isn't, aren't, I'm not; Are you …? Yes, I am; I'm hungry with be | to-be, pronouns | 25 | 13 |
| 3 | `articles` 🏷️ | a, an by the sound (an hour, a uniform), the, a with a job, none in general and in at home, at school | articles | 30 | 15 |
| 4 | `plurals` 🐑 | -s, -es, -ies, -ves, men, women, children, feet, sheep; the verb in the plural | plurals, articles | 29 | 15 |
| 5 | `have-got` 🐕 | I've got, she's got, haven't got, Have you got …? Yes, I have; have alone, American Do you have …?; have breakfast | to-have | 30 | 11 |
| 6 | `numbers` 🔢 | 0–20, -teen, -ty, 21–99 with a hyphen, How many …?, age with be, a hundred and five, prices | numbers, plurals | 24 | 14 |

- **The lines** are set in the village's grown-up life and cast: Mrs Hartley's class, Mrs Birkett at the Packhorse Inn
  (booking a room), Shepherd Jack and Meg on the fell, Mr Dixon's smithy, Granny Maggie's kitchen, Auntie Edna's
  radio, Mr Tyson's mill and his numbers, Arthur's bees, Brewer Sam, Old Wilf, the village shop, the bus to Keswick.
  The children (Harry, Poppy, Alfie) appear only as Mrs Birkett's and the village's.
- **Explanations** in Slovene, German and Italian (du, ti, tu, as the app speaks to the learner), every English form
  bold with its meaning in brackets; the English the format wants is plain English (the fallback base).
- **Only earlier grammar around a gap**: no possessive (my, her, Jack's dog), no present simple (says, goes), no there
  is in a sentence with a gap. "My name's", "What's your name?" are phrases of `hello`; "Jack's got" is has got (unit 5
  on).
- **British first, the American form accepted**: by the grading (spelling) or in `accept` (have-got's transforms take
  I don't have a cold, Do you have a pen?).

### The two new grammar pages (companion/grammar/en)

`pronouns` (I, you, he, she, it, we, they; the subject always said; you for everyone; it for a thing; they for one person
too) and `numbers` (0–1,000, -teen and -ty, the hyphen, a hundred and five, the plural, How many, age), in the format
of the 16 pages, each with its cheat-sheet card (a bold rule line per base, two examples, a tip).

### The groundwork (shared with parts b and c, and with German where it says so)

| Commit | What |
|---|---|
| `57cf39c6` | cherry-pick of it-print-meanings' 8c320f12: the print reads the supplement's own meaning in the base |
| `1975588d` | **CourseContentChecks** takes the bases its texts are written in; the per-base check moved from the Italian test into it, plus titles, goals, explanations, summaries and worksheet titles in each base. German's test is one line on it |
| `b4bc605a` | cherry-pick of course-de-a1-a's 148fa919: English and German headings on the sheets, articles off the packs' words |
| `69ee66c8` | **grading/en.json** for A1 (below) and its tests |
| `0330fcd6` | **the teacher**: Mrs Hartley teaches the grown-ups too; the smoke checks she is the Lakeland school's person, in "teacher" (Matilda) |
| `83b0cc32`, `573e9261` | cherry-picks of course-de-a1-a's supplement smoke (every `<lang>.extra.json` validates) and the generic QA step `course-unit` |
| `72a5de23` | **COURSE.md** "The English course": the frame |
| `eb806399` | **lexicon/en.extra.json** created (the bridge's, the prints' and the APK's loaders read it already) |
| `2ff4b70f` | **prints**: the course language's packs first, another language's pack only as a meaning, without its gender and plural (shared with German) |
| `39e9ab19`, `96c17b85` | cherry-picks of course-de-a1-a's smoke and Android counts of the English and German books (16 or more pages) |
| `137c4af2` | **the English tests**: EnglishCourseContentTest, SchoolBoardTest's English board, the smoke's Lakeland course-speech and English sheet |
| `f1cc9395`, `1a633f32` … `82fd9d9c`, `b25e0436` | cherry-picks: the course-speech base words (79099d49), part c's grading (-ing, ordinals, were/where, a contraction without its apostrophe almost, the apostrophe hint's examples), the QA field fix (ddb65f29) |
| `66c1ad9c` | **QA**: course-unit plays a reorder and a multi |

**The grading** (grading/en.json, `same` and `wrong`):
- a contraction is its long form: I'm = I am, isn't = is not, he's got = he has got (before it the pronouns' 's = is),
  Jack's at home = Jack is at home (a name's 's only before an article, not, a place word, here, there, a possessive,
  always, never, very: Jack's dog stays a possessive), I'd like = I would like;
- 21–99 with or without the hyphen; the American a hundred five;
- the American spelling as the British (color, favorite, center, meter, gray, mom, traveling, pajamas, plow, math,
  dialog, program), -ize and -ise both; look-alikes untouched (for, door, laboratory, humorous, litter, moment);
- known mistakes, each only for a word of its group: -s for -es after x, z, ch, sh, ss (boxs, churchs, glasss) and
  buss; -fes for -ves on the nouns that make it (knifes, wifes, lifes; gloves keeps its slips); woman for women and
  back; fourty, forteen, ninty, ninteen; for for four. The other irregular plurals were wrong already (an ending or the
  distance): sheeps, mans, childs, foots, potatos, familys, leafs.

**The teacher**: the Lakeland school's person is already Mrs Hartley (home school, first by order: `Residents.brings`),
in the cast's `teacher` voice (Matilda; `@hartley` once she has her own). No new villager, no new voice, no credits.
Her persona said she teaches pupils as British children; she now teaches the children by day and the grown-ups in the
evening class (first names both ways, but "Mrs Hartley"). A villager's `personality` is at most 600 characters: over it,
the villager store drops the villager without a word (the first draft did; the smoke caught it).

**The supplement** (companion/lexicon/en.extra.json): 44 entries, every word the six sheets name that no English or
Lakeland pack means (fine, innkeeper, be, busy, hour, nurse, dish, calf, person, people, car, a cold, how many,
oats …), with its forms and its meaning in sl, de and it. Checked against the packs' prompts in the same base (bike's
Slovene "kolo (pogovorno)", as a pack's bicycle is "kolo").

**The prints' packs** (2ff4b70f): for a course of another language the print read every culture's packs and took the
first pack whose field in that language matched, so an English sheet printed "teacher f učiteljica" from Friuli's
Italian pack, and German would have printed der Sonne by il sole's m. Own-language packs now come first; another
language's only as a meaning, without its gender and plural. The Italian sheets (A1–B1 in de, sl, en): the same words
and meanings, 28 places lose a gender mark another language gave (la fame m, la canzone n were wrong), every PDF's page
count the same (57 PDFs in de).

## Read aloud

Mrs Hartley reads each intro's English in her voice: the examples, the quick questions (a gap a pause, a bracket cue at
the end left out), the answers, and the English forms of the explanation (`Spoken.looks` for English, else the intro's
own words). The bold spans of every base's explanations were checked: all are read in her voice but the endings
**-teen** and **-ty** of numbers (no English word, not in the table), which the phone's voice of the base reads. The
plurals' spelled forms (buses, dishes, families, calves, roofs) are in the intro's table for that reason.

**Voice build, not run (no credits)**: her English lines of the six units are 321 distinct texts, about 5,200
characters, the same for every base (`voice-build --only teacher`); the explanations, one clip a step and summary, are
about 9,100 characters in Slovene, 10,000 in German and 9,600 in Italian (`--explanations`), about 28,700 for all three.

## Checks

- Bridge: `bunx tsc --noEmit -p .` clean; the smoke with ports 9890– (LANI_SMOKE_DRAMA3_PORT 9908): 3321 checks, all
  passed; course/en validates, the prints' rule line keeps each step's point (every base), the Lakeland school
  reads the English course in Mrs Hartley's voice for sl, de and it learners, no base word in her voice, the supplement
  validates, an English sheet has no gender.
- Android, the full unit suite through lani-slot.sh: 3704 tests, 0 failures, 2 skipped; among them
  `EnglishCourseContentTest` 6 (every unit played at its level, every right answer graded right by the English grading,
  no distractor right or almost, every text in sl, de, it and en), `SchoolBoardTest` 6 (the English board: every letter,
  "Welcome!" and 14/10/2026, to-be's forms), `GradingLanguagesTest` 37, `ItalianCourseContentTest` 6 and the Slovene
  `CourseContentTest` 5 on the refactored checks.
- `tutor-export --check`: 89 files match. Python: `tests` 251 OK, `companion/tests` 142 OK (11 skipped).
- Prints: `school-prints --language en --level A1 --base de|sl|it --png`: every sheet fits (hello and plurals two
  pages, the others one), the booklet 10 pages; every word meant, no gender; each step's point on the rule line; the
  "Watch out" boxes pair each wrong form with its own right one (I amn't · I'm not, Yes, I'm · Yes, I am, This is dog ·
  This is a dog, childs · children, threeteen · thirteen).
- Emulator QA (`course-unit` through qa-slot, slot 2; a synthetic learner of English from German in the Lake District,
  make-learner.ts --target en --base de): **hello**, **to-be** and **numbers** each end to end and passed: the school,
  the unit, the five intro steps with their quick questions (a situation's choice, a typed gap), the summary card, the
  first worksheet (sort and choices; to-be's table; numbers' table and multi), the test straight away (hello's reorder
  "How are you?"), Mrs Hartley's cheer, the sheet printed in the app (fit.js, the preview, the PDF). Screenshots in the
  scratchpad, ux-course-en-a1-a/ (hello-run2, to-be-run, numbers-run2, prints). Two QA gaps found on the way and fixed
  in the shared step: the quick question's field under a tall table (ddb65f29, part c's) and reorder and multi
  (66c1ad9c). One content bug the screen showed: worksheet titles per base repeated the English ("Count · Count ·
  Zähl"), fixed in 8fc10bf2.

**Tap counts.** Before: a learner of English found no lesson (the school's A1 all planned, "I'm preparing the next
lessons"). After: Home's "Hello! I'm … · School · A1 · Next" row (1 tap) → the unit's "Next: Introduction" (2 taps) →
Mrs Hartley's first step; from the school: the level's unit (1) → the introduction (2).

## How to use

- A learner of English (the Lakeland culture) finds the school's A1 with six units open and twelve planned
  ("📝 Coming"), Mrs Hartley preparing the next lessons once the six are done.
- A printed sheet: `companion/bin/school-prints --language en --level A1 --base de` (or sl, it).
- QA: `LANI_DEV_DATA=<make-learner.ts --target en --base de> LANI_CULTURE=lakeland LANI_QA_SETUP_PAIR=en-de
  LANI_QA_COURSE_UNIT=hello companion/bin/qa-slot --steps course-unit`.

## What's left

- Parts b and c (units 7–18) append their words to en.extra.json, their tables under COURSE.md's English section, their
  outline lines.
- The endings -teen and -ty are read by the phone's voice of the base.
- The native check below.

## Native check

Machine-written, not reviewed by a native speaker. British English first, then each base.

**British English** (every unit's English: examples, prompts, options, gap texts, transforms):
- hello: "Good afternoon" until about six; Ms for any woman, Miss for a young one; "Very well, thank you" as a
  grown-up's answer; "See you!"; Old Wilf as "the storyteller".
- to-be: "Jack's on the fell" for is; "Is the mill open? — No, it isn't. It's Sunday."; "You're late" as a greeting at
  the inn.
- articles: "Mrs Hartley is a teacher at the school" (a and the both accepted there); "Jack is in a bed" offered as
  wrong for sleeping at night; "Keswick is an hour by bus"; "I'd like a room for one night, please."
- plurals: "The sheep is lost!"; "berries for the jam"; "the dishes are by the sink"; the barn and the gate.
- have-got: "We haven't got time."; "I've got a cold."; "Do you have a room for tonight?" (British too); "Let's have a
  cup of tea!"; "Meg has got a black and white coat."
- numbers: oh in a phone number ("oh one seven …"); "three pounds fifty", "six pounds twenty"; "two hundred and forty
  sheep"; "Mr Tyson is busy with numbers today."; "How many eggs, Granny Maggie? — Twelve, please."
- pronouns page: they for one person ("Someone's at the door. What do they want?"); a pet as he or she.
- numbers page: the stress of thirteen and thirty; 1,000 with a comma.

**Slovene**: the explanations' register (ti, the app's); "gospa Birkett", "gostilna Packhorse"; "Pozen si" for "You're
late"; "Ovc ni na polju"; "Krompir je vroč" for "The potatoes are hot" (uncountable in Slovene); "kolo (pogovorno)" for
bike; "hišica" for cottage; "Rad, rada bi sobo"; "skedenj" for barn; the meanings in en.extra.json.

**German**: du in the explanations; Austrian or German standard (Bub, Jänner not needed in these units); "Das macht drei
Pfund fünfzig"; "spät dran"; "Hütehund" for sheepdog; "Brotlaib"; "in der Arbeit" for at work; the meanings in
en.extra.json.

**Italian**: tu in the explanations; "locanda" for the inn (the packs say osteria); "fa il pastore" for "is a shepherd";
"cantastorie" for storyteller; "Sono tre sterline e cinquanta"; "Benvenuto/Benvenuta"; the meanings in en.extra.json.
