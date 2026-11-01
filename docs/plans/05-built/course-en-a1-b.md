# Built: the English school's A1 units 7–12 (2026-11-01)

Jan, 2026-11-01: "can you make app complete - all what is missing?" A learner of English in the Lake District village
(the culture `lakeland`) had an empty school after the numbers: companion/course/en/course.json outlined 18 A1 units,
units 7–18 marked `"planned": true`. Three branches write them: `course-en-a1-a` units 1–6 (hello … numbers, the shared
groundwork: the prints' English headings, lexicon/en.extra.json, grading/en.json, Mrs Hartley's persona,
CourseContentChecks for English and the QA step "course-unit"), this branch (`course-en-a1-b`) units 7–12,
`course-en-a1-c` units 13–18. The format and how a unit is written are companion/COURSE.md ("The English course" and
"The English course: units 7–12" has the summary and the craft notes).

They serve grown-ups, visitors and newcomers, learning English from Slovene, German or Italian. Mrs Hartley teaches
them in the evening class (the school's person of the Lakeland cast, `Residents.brings(SCHOOL)`, in the cast's
`teacher` voice).

## What

| # | unit | intro | worksheets (items) | test | its pages |
|---|---|---|---|---|---|
| 7 | `this-that` 👉 This, that, these, those | 5 steps, 13 examples, 3 choices and 2 gaps | table + sort (13) · gap text with a bank + 2 choices (7) · 2 transforms (10): 30 | 11 items | this-that (new) |
| 8 | `possessive` 🐾 My dog, Jack's cat | 5 steps, 16 examples, 4 choices and 1 gap, a summary | table + sort (12) · gap text with a bank + 2 choices (7) · 2 transforms (10): 29 | 11 items | possessive-s |
| 9 | `present-simple` 🔁 I walk, she walks | 5 steps, 15 examples, 3 choices and 2 gaps, a summary | table + sort (12) · gap text with a bank + choice (7) · 2 transforms (10): 29 | 11 items | present-simple |
| 10 | `questions-do` ❓ Do you …? I don't. | 5 steps, 15 examples, 3 choices and 2 gaps, a summary | sort + table (14) · gap text with a bank + 2 choices (7) · 2 transforms (9): 30 | 12 items | questions-do, present-simple |
| 11 | `word-order` 🧩 I often go to Keswick | 5 steps, 17 examples, 5 choices, a summary | sort + 3 reorders (12) · transform + choice (7) · 2 transforms (8): 27 | 10 items | word-order, present-simple |
| 12 | `there-is` ✨ There is, there are | 5 steps, 14 examples, 4 choices and 1 gap, a summary | sort + table (13) · gap text with a bank + 2 choices (8) · 2 transforms (8): 29 | 12 items | there-is-are, plurals |

Each is the size of part a's units: a guided intro of five steps (Mrs Hartley's lines in Slovene, German, Italian and
English, ti, du and tu as the app speaks to the learner; every English form in bold with its meaning in brackets in the
base, for her voice; the table growing row by row; one quick question each), three worksheets from easy to harder (the
forms, then a gap text with a word bank, then transform lists), and a test at pass 80 with new sentences. Every text the
learner reads is in each base. The lines are the village's grown-up life: Mrs Birkett at the Packhorse Inn and her son
Harry, Shepherd Jack and Meg, Mr Dixon's smithy, Granny Maggie and Poppy, Auntie Edna's radio, Mr Tyson's oats and
mill, Brewer Sam, Old Wilf under the oak, Beekeeper Arthur, the walkers, the bus to Keswick, Cat Bells over the lake.

- **this-that**: this and that for one thing near and further away, these and those for more, only for how many
  (this man, this woman: Slovene's ta and tisti, German's dieser, Italian's questo and quello change); alone (What's
  this? This is Jack.) and the answer with it and they; this one, that one; Hello, this is Sam on the phone, never
  here is Sam (German's hier ist, Slovene's Sam tukaj); this morning, that day.
- **possessive**: my and your before the thing, never changing; his, her, its by the owner, not the thing (Mrs
  Birkett and her son; Italian's suo for both); our, their, and never the with them (my dog, not the my dog: Italian's
  il mio cane); the owner's 's (Jack's dog, with the apostrophe German leaves out); the boys' bikes, the children's
  books; its against it's. A summary of its own: the page goes on to mine and yours, which the unit doesn't teach.
- **present-simple**: the affirmative only. Habits and facts; one form for I, you, we, they; he, she, it + -s, never
  she is make; -es after s, sh, ch, x and in goes, does, as the plural's; a consonant + y → -ies, but plays, buys;
  has; how often at the end; always a subject (It rains: Slovene and Italian leave it out).
- **questions-do**: Do you …? Does she …? with the verb back in its base form (never Does Meg barks?); don't, doesn't
  + the base form; Yes, I do. No, she doesn't.; where, what, when first; no do with be or have got, and Do you have …?
  right too.
- **word-order**: subject – verb – object; the place, then the time; a time in front keeps subject and verb together
  (never In the morning feeds Jack the sheep: German's verb second); always, usually, often, sometimes, never before
  the verb and after be; the adjective before its noun, never with -s.
- **there-is**: there's for one thing and what isn't counted, there are for more; there isn't a …, there aren't
  any …; Is there …? Are there any …? and the short answers (never Yes, there's); How many … are there?; there is
  against it is (There's an inn on the square. It's very old.).

## The grammar page

`companion/grammar/en/this-that.json` 👉 (A1), the one page the outline's English pages lacked for these units: the rule
(this, these, that, those; before a noun or alone; this one; This is … for who someone is and on the phone; that's,
and no short this is; it and they in the answer; this morning, that day), a table of six rows, seven examples with a
note each, in en, sl, de and it, and its own cheat-sheet card (a bold rule line per base, two examples, a 💡 tip:
"they change only for how many, never for a man or a woman"), the unit's summary. The other units name the existing
pages (possessive-s, present-simple, questions-do, word-order, there-is-are), and four of them have a summary card of
their own where the page goes further than the unit (mine and yours, don't and questions, did and can, there was).

## The grading these units need

`grading/en.json`'s `wrong` (commit 94cca49b), paired as part a's plural mistakes are, so each is wrong only for a word
of its group and a slip of the keyboard on any other word stays one:
- those only for these, these only for those (one letter apart: near or far, a real mistake);
- gos for goes, dos for does (-es after o);
- a consonant + -yes for a consonant + -ies: flyes, carryes, studyes, and a plural's babyes;
- plays and buys keep their slips; a slip inside the stem (thsee, wlaks, wtaches) stays almost.

The rest was wrong already and is now pinned by GradingLanguagesTest: the third person's -s (Jack walk, I walks),
-es after ch, sh, x (watchs, fixs), flys, haves, playes. Jacks for Jack's and Poppys for Poppy's are wrong (an ending);
its for it's, he's for his, they're for their are one word for two, wrong.

Shared commits taken onto this branch, as the lead asked: part a's English course tests (7ee7b4be: cherry-picked
137c4af2), the course-speech base word lists (4dde2598: 79099d49, its conflict with the English check resolved by
keeping the letter check and using the shared `BASE_WORDS`), and part c's grading (6bede83a, 9dd285cc, 09bc294b,
4cf561ea: the -ing form's, the ordinals' and were/where mistakes; a contraction typed without its apostrophe almost,
with the apostrophe hint in the language learned's examples; ordinals 21–99 with or without the hyphen), their
`wrong` lists, `_note`s and tests merged keeping both sides; and the lead's QA fixes for course-unit (ddb65f29: a
quick question's field above Check; 66c1ad9c: reorders and multis played).

## The words on the prints

`lexicon/en.extra.json` has 48 more entries (92 in all), each with its forms and its meaning in Slovene, German and
Italian: these, those, blackberry, picture, bag, phone, granddaughter, granny, boy; the verbs make, wash, fix, miss,
study, buy, come, grow, tell, stop, run, walk, play, watch, rain, do (with does, don't, doesn't), live, work, know,
like, eat, sleep, sell, listen to, drink, feed, shoe (a horse), sing; darts, pie, walker, square; happy, big, grey
(gray its American form); every day, every week, every morning, after school. Every sheet of units 7–12 lists every
word it names with a meaning in each base (this-that 28, possessive 28, present-simple 31, questions-do 21, word-order
33, there-is 31 words).

- A verb the packs have as a noun (walk: sprehod, play: igra, watch, rain: dež) is named "to walk" in present-simple's
  `words`, as the packs name their verbs, and its entry has "to walk" among its forms; to fly and to carry are the
  packs' own words.
- No meaning is another word's prompt in the same base, in the English and Lakeland packs or the supplement: granny is
  "babica (domače)", "Oma (vertraut)", "nonna (affettuoso)" beside the pack's grandmother; picture "risba, slika"
  beside painting's "slika"; boy's Italian "ragazzo, ragazzino" beside child's "bambino".

## How to use

```bash
# the sheets, as a learner from Slovene gets them (never inside the repository, never the learner's data dir)
companion/bin/school-prints --language en --base sl --units this-that,possessive,present-simple,questions-do,word-order,there-is --out /tmp/en-a1-b --explain
# QA: a synthetic learner of English from Slovene in the Lake District, a unit end to end and its print
LANI_DEV_DATA=$(cd companion/bridge && bun test/fixtures/make-learner.ts /tmp/en-learner/data --name Ana --target en --base sl) \
  LANI_CULTURE=lakeland LANI_QA_SETUP_PAIR=en-sl LANI_QA_LEARNER=Ana LANI_QA_COURSE_UNIT=this-that LANI_QA_COURSE_SHOTS=/tmp/en-shots \
  companion/bin/qa-slot --steps course-unit
```

## Tap counts

| | before | after |
|---|---|---|
| a unit of 7–12 from the school's screen | not reachable: a row of "📝 Kmalu · Coming", folded, that doesn't open | 2 taps: "★ Next · Naslednja" (or its row), then "📖 Uvod · Introduction" |
| from Home | not reachable | 3 taps: Home's school tile first |
| the unit's sheet printed | none | 1 tap on the unit row's 🖨️, then the system's print dialog |

The same counts as units 1–6: the units add content, no screen.

## Tests

- **Android unit suite** (lani-slot.sh, `:app:testDebugUnitTest`, with part c's grading commits on this branch): after
  the rebase onto main, 3,708 tests in 476 classes, 0 failures, 2 skipped (3,705 before it). Among them `EnglishCourseContentTest` (6: every unit of
  companion/course/en parsed and played at its level, every right answer graded right by grading/en.json, no
  distractor right or almost for any gap, every text per base in sl, de, it and en), `GradingLanguagesTest` (38, the
  new one: those for these, gos, dos, flyes wrong; the third person's -s and -es wrong already; slips in the stem
  forgiven), `SchoolBoardTest` (6: the English board writes every letter of units 7–12 and lays out every lesson),
  `GrammarTest` (the app bundles the English book's 19 pages with this-that).
- **Bridge**: `bunx tsc --noEmit -p .` clean; the smoke (LANI_SMOKE_PORT=9910, LANI_SMOKE_DRAMA3_PORT=9928): all
  checks passed, 3,380 of them after the rebase (3,324 before), among them course/en (every unit validates, its pages in the book, machine-written),
  the rule-line check (2,103 steps in every base: each step's point on the sheet's rule line), the supplement
  (en.extra.json validates, 92 entries), and course-speech's English checks (for a learner from sl, de and it, Mrs
  Hartley reads the examples, the quick questions and their answers and the English of the explanations, never the
  base: no č, š, ž, umlaut, Italian accent or a word only the base has).
- After the last content fixes (1321edef, c05dc667, a662d8c6): the course's Android tests again (EnglishCourseContentTest,
  GradingLanguagesTest, SchoolBoardTest, GrammarTest, CourseTest, IntroSpeechTest, IntroSpeechParityTest,
  GameIntroSpeechTest, IntroBoldTapTest: 94 tests, 0 failures), tsc and the smoke again (all passed).
- `tutor-export --check`: 89 files match the bridge. Python: 251 tests (tests/) and 142 (companion/tests, 11 skipped),
  OK.
- While writing: a port of the English grading (every distractor against every gap), the bridge's `validateUnit` and
  `validateGrammar`, and `school-prints --explain` in sl, de and it (every word of every sheet meant).

## QA

Emulator QA through `companion/bin/qa-slot --steps course-unit` (slot 2, a 1080 × 2400 phone): a synthetic learner,
Ana, learning English from Slovene in the Lake District (make-learner.ts --target en --base sl, LANI_CULTURE=lakeland,
LANI_QA_SETUP_PAIR=en-sl), units 1–6 done by QA's hook, `this-that` end to end: the school with "★ Next" on This,
that, these, those (6 of 12 done, 6 more coming), the unit's page, the intro's five steps (three choices tapped, the
gaps They're and one typed, each right), the summary card (the new page and its "📖 New page in the book" toast),
the first worksheet (the table and the sort, checked), the test straight away and passed, the dev bridge's village
keeping it done (the test 11 of 11), then its sheet printed (the 🖨️, the dialog's preview, the PDF, A4, two
pages). Three runs; the last, after the fixes below and with the lead's QA fix, passed whole. The screenshots
(cu-*.png, 18 of them) are in the scratchpad under ux-course-en-a1-b/.

What it found, fixed:
- **A worksheet's title twice in English** (a662d8c6): the app shows a sheet's title in the pair (the course's
  language, then the base), and the titles per base held the English already ("📝 Here or there? · Here or there? ·
  Tukaj ali tam?"). Each base's title is now its own words (part a met the same in units 1–6).
- **The table's row labels** (1321edef): this-that-1's rows were labelled here · tukaj and there · tam, three alike and
  two alike, so a cell's TalkBack label (its row's and its column's) named three cells, and QA, filling by label,
  typed row 5's answer into row 1. Each row is now its noun (cup · skodelica, fell · hrib, scone, bike · kolo, child ·
  otrok). The other units' tables have distinct labels (checked).
- **Step 3's gap** (a146fc3a): QA's search for an intro's text field stopped at y 1850, and on this phone a step whose
  examples and table overflow ends its scroll with the quick question's field at y 1868 (in view, above Check and
  Next). Shortening the step's answer row made the table compact but couldn't move the field: the scroll's end pins
  it. The lead's QA fix (ddb65f29, cherry-picked: bccae8c1) finds the field above the Check button, wherever the step
  scrolled to; the final run used it.

## Voice

Nothing was voiced and no ElevenLabs credit was spent. Mrs Hartley's clips of units 7–12 (voice-build's `course`
source, `--only teacher`): 355 lines in her English voice, about 7,400 characters (the examples, the quick questions
and their answers, the English runs of the explanations and summaries); the explanations themselves, one clip a step
and a summary (`--explanations`, made on demand as an intro opens): 36 a base, about 10,500 characters in Slovene,
11,400 in German and 11,200 in Italian. All of it for every base: about 40,500 characters.

Every English form the explanations and summaries show in bold is read in her voice (c05dc667: course-speech's
vocabulary and looksLearned, as the app's IntroSpeech, over every step and summary in sl, de and it), but the ending
-s and the wrong form *two bigs dogs*, which the phone's voice of the base reads, as part a's -teen and -ty.

## For the native check

All of it is machine-written (`machine_written: true`, each file's `review` names its points). British English first:
- this-that: Hello, this is Sam on the phone (and Hello, it's Sam accepted in the test's gap); Cat Bells as the fell
  over the lake; those boats from Keswick.
- possessive: Sam and Jack's tent for a tent of both; your coat, Mrs Hartley; Poppy is there too: Maggie is her granny.
- present-simple: darts at the inn on Saturdays; the walkers come to the inn; misses as an -es verb; Jack's two
  hundred sheep eat grass.
- questions-do: Do you have … beside Have you got …; the walker from Penrith at the inn; What does Edna listen to.
- word-order: place before time as the usual order, every week in Keswick an almost; Usually and Sometimes first, and
  Edna sings in the kitchen sometimes; Mr Dixon shoes the ponies; Meg likes Jack very much.
- there-is: There isn't milk and There aren't ponies accepted beside any and no; a stone hut with no fire; the
  spoken there's with a plural not taught.

Then each base:
- Slovene: sconi (the scones), Jezersko okrožje (the Lake District), sprehaja Meg, Sam tukaj on the phone, svoj in the
  possessive's translations (s svojim psom, pri svojem mlinu), the dual (dva šotora, sta dva konja, igrata pikado),
  "Ob nedeljah kosimo", "Je tu kaka trgovina?".
- German: der Berg dort for that fell, Darts, Imker Arthur, "Anders als im Deutschen rückt das Verb nicht vor", Sie with
  Mrs Hartley and the walker.
- Italian: il Suo cappotto with Mrs Hartley, quel monte laggiù, la cagna pastore for Meg, giocare a freccette, Lei with
  the walker.

## What's left

- **The ending rule** treats a long word's last two letters as its grammar, so a slip there in an English adverb is
  wrong, not almost (usualy for usually).
- **The rebase** with parts a and c: course.json (resolve-outline.py), COURSE.md (the units in order), en.extra.json
  (both sides' entries), grading/en.json's `wrong` and `_note` and GradingLanguagesTest (both kept; part c's commits
  are on this branch already, resolved, so their copies on part c's side meet the same lines). Rebased onto main at
  the end (7e2acd5c, the German and English talks): companion/bin/qa's named-only steps kept both sides
  (course-unit beside main's), and main's new English pack (weather-and-garden) gives no prompt one of the
  supplement's meanings.
