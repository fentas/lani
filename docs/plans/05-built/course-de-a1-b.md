# Built: the German school's A1 units 7–12 (2026-11-01)

There was no German course: a learner of German in the Carinthian village found the school empty, the outline's 18 A1
units all planned (`companion/course/de/course.json`). Jan, 2026-11-01: "can you make app complete - all what is
missing?" Three branches write A1 side by side, in the outline's order: `course-de-a1-a` units 1–6 and the shared
groundwork (docs/plans/05-built/course-de-a1-a.md), this branch (`course-de-a1-b`) units 7–12, `course-de-a1-c` units
13–18. The format and how a unit is written are companion/COURSE.md ("Writing a unit", "The German course", "The German
course: units 7–12").

The course is for adults, future learners and visitors, from every base German has here: Slovene, English and Italian.
This branch starts at part a's tip and uses its groundwork as it is: the prints' German headings and articles, the
supplement `lexicon/de.extra.json`, `grading/de.json`, Lehrerin Eva as the teacher and her course-speech check,
`GermanCourseContentTest`, and QA's generic `course-unit` step.

## The units

Each has a guided intro of five or six steps (Lehrerin Eva's lines in English, Slovene and Italian; every German form in
bold for her voice, its meaning in brackets after it; two or three village examples a step; the table growing row by
row; a quick question each), its own summary card, three worksheets from easy to harder (the forms in a table or a sort,
then a gap text with a word bank and distractors, then a transform list), and a test at pass 80 with new sentences.
They assume only units 1–12 before them in the outline (COURSE.md "Only what came before around a gap").

| # | unit | intro | examples | worksheets (items) | test |
|---|---|---|---|---|---|
| 7 | `praesens` 🏃 Ich spiele, du spielst | 6 + its own summary | 15 | table + sort (16) · gap text with a bank + 2 choices (8) · transform + cloze (9): 33 | 12 |
| 8 | `vokalwechsel` 🔄 Ich fahre, du fährst | 6 + its own summary | 17 | table + sort (18) · gap text with a bank + choice (8) · transform + cloze (7): 33 | 12 |
| 9 | `fragen` ❓ Wer? Was? Wo? | 5 + its own summary | 14 | sort + 4 choices (16) · gap text with a bank + choice (7) · transform + cloze (8): 31 | 11 |
| 10 | `wortstellung` 🧱 Heute backt Oma | 6 + its own summary | 15 | 3 choices + transform (10) · gap text with a bank + 2 reorders (7) · transform + reorder + choice (7): 24 | 10 |
| 11 | `akkusativ` 🎯 Ich sehe den Hund | 6 + its own summary | 16 | table + sort (17) · gap text with a bank + choice (8) · 2 transforms (9): 34 | 12 |
| 12 | `kein-nicht` 🚫 Kein Brot, nicht heute | 6 + its own summary | 16 | sort + table (14) · gap text with a bank + choice (8) · transform + cloze (9): 31 | 12 |

- **praesens** (praesens, du-sie): -e, -st, -t, -en, -t, -en on wohnen, kommen, kochen; ihr -t as er (er spielt, ihr
  spielt); Sie with -en; the pronoun always there, for the Slovene and Italian bases, which leave it out; one present
  for I cook and I'm cooking (never ich bin koche, the English speaker's mistake); the e after a stem in -t or -d (du
  arbeitest, er findet, ihr wartet); a stem in -s, -ß, -z with only -t (du tanzt, as du heißt); gern after the verb,
  gerne accepted. The gap text: a Saturday in the village (Kathi's Kasnudeln, Florian's Harmonika, Mitzi and Resi's
  coffee, Opa Sepp's story).
- **vokalwechsel** (vokalwechsel, a new page; praesens): a → ä (fährst, schläft, trägt, wäscht), au → äu (läuft), e → i
  (sprichst, hilft, gibt, and es gibt), e → ie (siehst, liest), only for du and er, sie, es; ich, wir, ihr keep the vowel
  (ihr fahrt); essen and nehmen spelt their own way (isst, nimmt); er isst (eats) against er ist (is), for Slovene both
  "je". The gap text: a day on the Alm with Florian, Bello and Stefan's cider.
- **fragen** (fragen): the question word, the verb, the person (Wo wohnst du?), no do; wer is who, not where (for the
  English base); wann (never wenn), wie, warum; wo, wohin, woher as Slovene's kje, kam, od kod; the verb first for a yes
  or no question, doch for yes to a question with nicht; questions to Sie. The gap text: Herr Rossi from Udine at
  Kathi's Gasthaus. Wo fährt Stefan hin? and Wo kommt Frau Huber her? are spoken forms, right and labelled with the
  standard question.
- **wortstellung** (wortstellung, fragen): the verb as the second part whatever comes first (Heute backt Oma Resi
  Kuchen; Am Abend erzählt Opa Sepp vom Lindwurm), never heute Oma backt; a first part of several words; und, aber,
  oder take no place, dann does; the question's verb; when before where (morgen nach Villach). The gap text asks for the
  verb and the person in one gap (gehen wir), a Sunday in Carinthia; reorders in the second and third sheets. The verb's
  bracket (modals, Perfekt, separable verbs) is left to units 13–18.
- **akkusativ** (akkusativ, artikel-genus): der → den, ein → einen, nothing else changes (die, das, eine, every plural);
  what you have, need, buy and what es gibt is an object; the subject keeps der (Den Fuchs sieht der Hund); ihn, sie, es,
  mich, dich (Siehst du den Dobratsch? Ja, ich sehe ihn!); für + accusative. The gap text: the Saturday market in
  Villach, the article in brackets; a question answered with the word in brackets (Was braucht Toni? (der Hammer)).
- **kein-nicht** (kein-nicht, akkusativ): kein for ein and for a noun without an article (kein Hund, keine Katze, keinen
  Hund, keine Schafe, keine Zeit, keinen Hunger); a job without an article takes kein (Toni ist kein Bäcker: nicht is an
  almost); nicht for the rest (a verb, kalt, a place, a name, der, die, das); nicht at the end, after the object, before a
  place or an adjective; kein … mehr, nicht mehr. The gap text: the Gasthaus zur Linde's Ruhetag.

**The new grammar page**, `companion/grammar/de/vokalwechsel.json` (A1), in the German book's format: its title in four
languages, its rule in en, sl and it, a table of eleven verbs (du, er/sie/es, and the unchanged ich, wir, ihr), eight
village examples with notes, its own cheat-sheet `card` (six verbs, two examples, the isst/ist tip). praesens names it
under "see". The other units name the book's pages: praesens, du-sie, fragen, wortstellung, akkusativ, artikel-genus,
kein-nicht.

**The words on the prints**: every unit's words have a meaning in each base from the German and Carinthian packs or the
supplement, which has 186 entries now (part a's 118 and 68 of these units): the verbs with their present forms (the
vowel-change ones too, so a tapped fährst, läuft or nimmt finds its verb), the question words (wer with wen and wem),
doch, kein with its forms, gern (gerne the same), es gibt, the time phrases, and the nouns with their gender and plural.
`school-prints --language de --base sl|en|it --units praesens,…,kein-nicht --explain` lists no word without a meaning,
and every sheet fits on one or two A4 pages.

## The grading

Part a's `grading/de.json` already had the vowel change (fahrst for fährst, lest for liest, fährt for ihr fahrt), er's
form for du's on a short stem, kein for ein. A TypeScript port of `Grading.check` (with `grading/de.json`) ran on every right
answer of the six units and on every other answer of the same exercise (the other gaps' and cells' answers, the bank's
distractors): none of them almost right for a gap where it's wrong. Three of A1's mistakes were slips before and are
now wrong, each a `{word, for}` pair so only a word of its group is caught, and one spelling is now the same answer:

- the e after a stem in -t or -d left out: `arbeitst`, `findst`, `wartst` for arbeitest, findest, wartest (a slip in
  the stem before; `arbeitt` for arbeitet was an ending already);
- `ist` (is) for `isst` (eats);
- `wenn` for the question word `wann`;
- `gerne` is `gern` (`same`): both standard, Austria says gern;
- `bäckt` is listed beside `backt` where it is an answer (both standard; not a grading rule).

`GradingLanguagesTest`'s German section has a test for them (a slip like `nimt` for nimmt stays almost).

## The teacher and the read-aloud

Lehrerin Eva (part a's check) reads these units too. For a learner from English, Slovene and Italian the course speech
reads 346 distinct lines in her voice (the examples, the quick questions with their gap a pause, the answers, the German
forms of the explanations) and none of them is base text by the smoke's test (0 lines in en, sl and it). A base's prompt
has no gap and no umlaut (situations ending in a colon: "You ask Kathi if she is cooking today:"); a cue in brackets
stands at the end of an intro's question.

## Checks

- **The units against the bridge and the app's grading** (a script of this round, not committed): `validateUnit` for
  the bases en, sl and it; every right answer graded right; no other answer of the same exercise and no distractor
  almost right for a gap; no base prompt with a gap or an umlaut; each step's point (a bold form) on its line of the
  print's rule, in every base.
- **The Android unit tests**, the targeted ones first (through lani-slot.sh): `GradingLanguagesTest` 36 (the new German
  test with them), `GermanCourseContentTest` 5 (all twelve units parsed and played at A1, every right answer right, no
  distractor even almost, every text per base in en, sl and it), `SchoolBoardTest` 6 (the blackboard writes every letter
  of the German course), `GrammarTest` 16, `IntroSpeechParityTest` 2: 70 tests, none failed. The full suite: 3,700 tests in 475 classes, none failed (2 skipped).
- **The bridge**: `bunx tsc --noEmit -p .` clean; the smoke with ports 9850–9868 (`LANI_SMOKE_PORT=9850`,
  `_FRIULI_PORT=9852`, `_TOWN_PORT=9853`, `_KAERNTEN_PORT=9855`, `_LAKELAND_PORT=9856`, `_SETUP_PORT=9857`,
  `_SERVICE_PORT=9858`, `_DRAMA3_PORT=9868`): all 3,328 checks pass (a first run had one failure, part a's kaernten sheet check, fixed by its own commit cherry-picked here). Its course section validates the twelve German units and the new
  page, prints' rule-line check holds for every step of every base, course-speech reads the German course in Eva's
  voice with no base text, kaernten serves the units in the outline's order.
- `tutor-export --check`: 89 files match the bridge. The Python tests: 251 (`tests`) and 142, 11 skipped
  (`companion/tests`), all pass.
- **The prints**: `school-prints --language de --base sl|en|it --units praesens,vokalwechsel,fragen,wortstellung,akkusativ,kein-nicht`:
  every sheet on one or two A4 pages (fragen and akkusativ one, the others two), no word without a meaning.

## Emulator QA

`companion/bin/qa-slot --steps course-unit` (slot 2) with a synthetic learner of German from Slovene in the Carinthian
village (`make-learner.ts --target de --base sl --name Ana`; `LANI_CULTURE=kaernten`, `LANI_QA_SETUP_PAIR=de-sl`,
`LANI_QA_COURSE_UNIT=akkusativ`): setup, course-unit and the crash check pass. The school under the linden with
"Ich sehe den Hund · Tožilnik" as the next unit (A1 10 of 12 done, the six of units 13–18 still coming), its page, the
intro's six steps each answered (the choice tapped, the gaps typed: den Hund, einen, ihn), the summary card (the page
akkusativ's), the first sheet (the table of eight cells, the sort of nine words), the test straight away, passed at
11½ of 12 (Wir hören sie typed hoeren by adb: an umlaut, half an item), the dev bridge's village keeping de/akkusativ
done, and its sheet printed from the unit: one A4 page, fit.js choosing the same layout in the WebView as in Chromium
(one page, the table beside the rule), the PDF A4. Screenshots: the scratchpad's `ux-course-de-a1-b/` (cu-*.png and a
contact sheet, contact.png).

Tap counts are those of any unit (no journey changed): Home → the school tile → the unit (2 taps) → the intro (1),
each step's question and Next, → the worksheets (1). Before this round a unit 7–12 couldn't be opened ("📝 Kommt bald"
in the level's folded list).

## Voice

No ElevenLabs credits spent. A voice build of units 7–12 would take: Eva's lines, 346 distinct texts, 6,128 characters;
the explanations and summaries, one clip a step and a summary, 41 per base: 9,543 characters in English, 8,857 in Slovene,
9,377 in Italian. All of it for the three bases: about 33,900 characters; Eva's lines and one base's explanations about
15,700.

## Native check

Every file says so in its `review` (the units: machine-written, not reviewed, in de, en, sl and it, with what to check).
Austrian German first:

- **praesens**: "Kathi kocht Kasnudeln", "Florian spielt Harmonika" (Austria: Harmonika, Germany: Akkordeon), gern
  (gerne accepted), "Wohnen Sie in Graz, Frau Huber?", "Ich finde das gut", "Es ist Samstag" (not Sonnabend), "Schmied
  Toni redet nicht gern".
- **vokalwechsel**: "Ein Tag auf der Alm", "Stefan fährt auf die Alm", "Der Hund Bello läuft zu den Schafen", "Es gibt
  Kirchtagssuppe", "Opa Sepp isst Speck und Brot", "Kathi trägt drei Teller".
- **fragen**: doch; "Wo fährst du hin?" as spoken; "Jetzt gleich"; Kathi's "Grüß Gott! Wie heißen Sie?" to Herr Rossi;
  "Ein Glas Most, bitte"; "Wie heißt die Kuh? – Sie heißt Lisi."
- **wortstellung**: "Oma Resi backt heute Kuchen" (backt and bäckt, Kuchen without an article), "vom Lindwurm",
  "Danach", "Am Nachmittag", "Es regnet, aber morgen scheint die Sonne", "Kathi ist heute im Gasthaus".
- **akkusativ**: "Den Fuchs sieht der Hund" as an A1 example, "Auf dem Markt" (Paradeiser, ein Kilo Paradeiser, eine
  Kiste Äpfel), "Heute gibt es einen Apfelstrudel", "Florian liebt den Hund", "Wen ruft Oma Resi?".
- **kein-nicht**: "Am Montag ist Ruhetag im Gasthaus zur Linde", "Toni ist nicht Bäcker" as almost (kein the answer),
  "Kathi kommt heute nicht", "das ist nicht der Hund von Florian", "leider".
- **vokalwechsel's page**: the rule and the notes in all three bases; "fahren (fährt)" as a dictionary shows it.
- **Each base**: the Slovene (da, not ja, in translations; the dual where two people do something: "Florian in Toni,
  lepo pojeta"; jé and je for isst and ist; "Kasnudeln" left in German), the Italian (fahren as andare con un mezzo;
  "Kathi è all'osteria" for the Gasthaus; the sagra for the Kirchtag) and the English.
- **The supplement's 68 entries**: their Slovene and Italian meanings (e.g. Ruhetag, doch, gern, es gibt).

## What's left

- The verb's bracket and the separable verbs are units 13–18's (`course-de-a1-c`); `wortstellung` teaches the second
  place only, and its grammar page goes on to the bracket.
- Rebase onto main when parts a and c land: `course.json` with resolve-outline.py, the supplement keeping both sides'
  entries, COURSE.md's sections in unit order, `GradingLanguagesTest` and `grading/de.json` keeping both sides' tests and
  pairs.
