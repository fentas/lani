# Built: the German school's first six A1 units (2026-11-01)

There was no German course: a learner of German in the Carinthian village (the lower Gailtal below the Dobratsch, near
Villach) found the school empty, only the outline of 2026-11-01 (`companion/course/de/course.json`, 18 units, every one
planned). Jan, 2026-11-01: "can you make app complete - all what is missing?" Three branches write A1 side by side, in
the outline's order: this one (`course-de-a1-a`) units 1–6 and the groundwork the other two take, `course-de-a1-b` units
7–12, `course-de-a1-c` units 13–18. The format and how a unit is written are companion/COURSE.md ("Writing a unit",
"The German course", "The German course: units 1–6").

The course is for adults, future learners and visitors, from every base German has here: Slovene, English and Italian.

## The groundwork (shared with the German parts b and c, and some with the English course)

| What | Commit | For whom |
|---|---|---|
| The prints of a German or English course: their headings in the course's language ("Regel · The rule", "Lektion"; "Rule · Pravilo", "Unit"), a unit's bare noun finding the pack's word with its article (Hund: der Hund; cat: the cat), a German noun printed with der, die or das by its gender (`prints/html.ts`, `prints/sheet.ts` `ARTICLES`, `withArticle`) | 148fa919 | German and English |
| The smoke validates every `lexicon/<language>.extra.json` (it.extra.json was read by the bridge, the prints and the APK but checked by nothing) | 498a8f4d | every language |
| The German supplement, `companion/lexicon/de.extra.json`, in it.extra.json's format: the bridge's lookup, the prints and the APK already read `<language>.extra.json` for any language, so no loader changed | 8d1b6b7b, 1c91dcd9, … | German |
| The German grading (`grading/de.json`, data only) and its tests | 90b90cb9 | German |
| The teacher: Lehrerin Eva (below), a check, no new villager | 18333904, e6438bf4 | German |
| COURSE.md "The German course" and its units 1–6 | 496ec8ac | German |
| `GermanCourseContentTest` (on the English agent's shared `CourseContentChecks` with bases, cherry-picked) | dbdaa9e6 | German |
| QA's "course-unit" step: a unit of the village's language's course end to end and its print, any language | 3e34de83 | German and English |
| The smoke's page count: German and English have 16 pages or more (each course adds its own) | 42b8ba5e | German and English |
| COURSE.md's craft notes for the German course (a base's prompt, an intro's cue, what may stand around a gap) | a47db8f9 | German |

Taken from the other branches as they were (cherry-picked, the same patches drop out at the gate): 8c320f12 (the
supplement's own meaning on the prints, 912991e5 here), 1975588d (`CourseContentChecks` with bases, e50c166c), 2ff4b70f
(a sheet's words from its own language's packs first, f2daac08), 668b33ef (`GrammarTest`: 16 pages or more, 7d9a0268),
79099d49 (course-speech's base words, 1e4d245b).

## The units

Each has a guided intro of five or six steps (Lehrerin Eva's lines in English, Slovene and Italian; every German form
in bold for her voice, its meaning in brackets after it; two or three examples a step from the village, the table
growing row by row, a quick question each), three worksheets from easy to harder (the forms in a table or a sort, then
a gap text with a word bank and distractors, then a transform list), and a test at pass 80 with new sentences. They
assume only the units before them in the outline: no verb but sein, haben and heißen conjugated, the subject first (no
inversion before the word-order unit), no masculine article after haben (the accusative is unit 11), places as fixed
chunks (im Gasthaus, auf der Alm, zu Hause) never a gap.

| # | unit | intro | examples | worksheets (items) | test |
|---|---|---|---|---|---|
| 1 | `hallo` 👋 Hallo! Ich heiße … | 5 + its own summary | 12 | sort + 3 choices (13) · gap text with a bank + 2 reorders (8) · 2 transforms (9): 30 | 10 |
| 2 | `sein` 🟰 Sein: ich bin, du bist | 5 + its own summary | 13 | table + 3 choices (8) · gap text with a bank + transform (11) · transform + 2 choices + cloze (7): 26 | 12 |
| 3 | `artikel` 🏷️ Der, die, das | 5 | 11 | sort + table (14) · gap text with a bank (7) · transform + gap text (9): 30 | 14 |
| 4 | `plural` 🐑 Ein Schaf, zwei Schafe | 5 | 10 | sort + table (14) · gap text with a bank (7) · transform (8): 29 | 14 |
| 5 | `haben` 🎒 Haben: ich habe, du hast | 5 | 12 | table + sort (13) · gap text with a bank (6) · transform + choice (9): 28 | 14 |
| 6 | `zahlen` 🔢 Eins, zwei, drei: die Zahlen | 6 | 12 | table + sort (13) · gap text with a bank (7) · transform + 2 choices (8): 28 | 14 |

- **hallo** (du-sie): Grüß Gott to everyone all day, Servus and Hallo to friends, Guten Morgen, Guten Abend, Gute Nacht
  (no n); Auf Wiedersehen, Tschüss, Baba and Servus, Bis morgen; Ich heiße …, Wie heißt du?, Wie heißen Sie?, Freut
  mich!; du to a friend and the village, Sie (its capital) to a stranger: Wie geht's dir? / Wie geht es Ihnen?, Und dir?
  / Und Ihnen?. The gap text: an evening at Wirtin Kathi's with a hiker from Graz; the test's: a morning at Franz's
  mill. Its summary is its own (du-sie goes on to the imperative).
- **sein** (sein-haben, du-sie): ich bin … sie sind, Sie sind with its capital, ihr seid (seit with t is since), German
  without a dual (for the Slovene base); a job without an article (Kathi ist Wirtin); müde the same for him and her,
  where Slovene and Italian agree; nicht after the verb, nein as an answer alone. The gap text: the Kirchtag in the
  village.
- **artikel** (artikel-genus): der and ein, die and eine, das and ein; the noun's capital (a tap between der hund, der
  Hund, Der hund); people by sex, -in always die; -e mostly die but der Käse, -chen, -lein and Austria's -erl always
  das (das Häferl, das Sackerl), -ung die; the Austrian twins as an aside (der Paradeiser and die Tomate, der Erdapfel
  and die Kartoffel, das Cola and die Cola): the article belongs to the word.
- **plural** (plural, artikel-genus): die for every plural, no plural of ein (zwei, viele); -e, -(e)n, -innen, -er with
  its umlaut, the umlaut alone or with -e (Äpfel, Väter, Mütter, Stühle, Kühe), no ending (Lehrer, Messer, Mädchen),
  -s (Oma, Auto); the packs' plurals. The gap text: Oma Resi's kitchen.
- **haben** (sein-haben): ich habe (hab spoken, right), du hast, er hat, wir haben, ihr habt, sie and Sie haben;
  Hunger, Durst, Zeit, Glück haben where English says I am; age with sein, as English and Slovene say it and Italian
  doesn't (ho trent'anni); hast, hat and habt each for its person. The gap text: the Gasthaus zur Linde.
- **zahlen** (zahlen, a new page): null to zwölf (zwo heard for zwei on the phone: spoken, right), -zehn with sechzehn
  and siebzehn, the tens with dreißig, hundert and tausend, 21–99 the one first in one word, eins and ein, Wie viel and
  Wie viele, prices in Euro. The gap text: the Kirchtag's prices. The village's numbers agree across the units and the
  page (Opa Sepp 81, Tante Mitzi 74, Stefan 30, Florian's 22 sheep, the mill 100 years old).

**The new grammar page**, `companion/grammar/de/zahlen.json` (A1), in the format of the German book: its title in four
languages, its rule in en, sl and it, a table (0–19, the tens, 21, 22, 99, 100, 120, 1000), seven village examples
with notes, and its own cheat-sheet `card` (a five-row table, two examples, a tip). The other five units name the book's
pages: du-sie, sein-haben, artikel-genus, plural.

**The words on the prints**: every unit's words have a meaning in each base from the German and Carinthian packs or
the supplement, which has 118 entries now (the core forms, then each unit's words; the nouns without their article and
with their gender, so the sheet prints der, die or das with them). `school-prints --language de --base sl` lists no
word without a meaning for hallo and sein.

## How it is reached (taps)

| Journey | Before | After |
|---|---|---|
| Home → a German lesson's first step | none: the school had only planned units, Eva preparing them | 3: Home's school tile, the unit (★ the next), its "▶️ Weiter: Einführung" |
| The village → a German lesson's first step | none | 3: the school's card "🏫 Schule", the unit, its intro |
| A unit → its printed sheet | none | 2: the unit's "🖨️" sheet, the print dialog |
| The A1 booklet in a base | none (no units) | 2: the level's booklet, the print dialog (11 pages) |

## The teacher

Each culture's school has its person (`Residents.brings(SCHOOL)`; the bridge's port `course-speech.ts`
`schoolTeacher`): Mojca in Primorska, Maestra Elena in Friuli. Kärnten already had hers: **Lehrerin Eva**
(`companion/cultures/kaernten/villagers/eva.json`), who lives and works at the school, her speaker `teacher`, Lily in
the Carinthian cast (`voice-cast.json`), `@eva` once she has a voice of her own. So the set-up is a check, not a new
villager: the smoke's course-speech section says she is the Carinthian school's person, in that voice, and reads the
German course in it for a learner from English, Slovene and Italian alike (the examples, the quick questions with their
gap a pause, the answers, the German forms of the explanations), never a line of the base; the guide names her
("Lehrerin Eva comes over from the next village", `companion/course/de`). Her lines of the school are de.json's
("Willkommen in der Schule! Setzt euch, fangen wir an.", "Das kannst du schon"): du, as the village says du.

**Found on the way: a prompt in the base was read in her German voice.** A quick question's prompt with a gap and no
Markdown is read in the teacher's voice (`questionLines`: a sentence of the language learned), and so is one the app's
check takes for German (any ä, ö, ü or ß). A prompt written per base ("To the doctor: «Wie geht es ___?»") would have
been read in Eva's German voice in English. So a base's prompt has no gap and no umlaut, a situation ending in a colon
with whole German phrases as its options, as the Italian units do; a prompt with a gap is German only.

## The grading

`grading/de.json`, data only (no Kotlin change), checked with `GradingLanguagesTest`'s German section (five new tests)
and a Python port of `Grading.check` on every pair of A1 forms (sein's, haben's, the articles, the pronouns, the numbers,
the umlaut plurals, the vowel-change verbs):

- **Capitals don't count**, as in every language: a noun typed small is right, and so is sie for Sie. The course asks
  for du or Sie by a tap, never typed; a phone capitalises only the first letter; a small noun isn't what a gap tests.
- **One answer**: geht's, gehts and geht es (gibt's too); tschüs and tschüss (both standard; before, an ending error);
  hab and habe (spoken, never a mistake; before, an ending error).
- **Almost**: ae, oe, ue, ss for ä, ö, ü, ß (the umlaut hint), an umlaut left out where it makes no other word (Hauser,
  Stuhle, Madchen: a slip in the stem).
- **Wrong, never a slip**, each a `{word, for}` pair so only a word of its group is caught: the singular for an umlaut
  plural and back (Apfel/Äpfel, Vater/Väter, Mutter/Mütter, Großvater/Großväter); the present's vowel change (fahrst,
  fahrt for fährst, fährt; lest for liest, sprecht for spricht, esst for isst, gebt for gibt, seht for sieht, gefallt;
  fährt for ihr fahrt); er's form for du's on a short stem (sagt for sagst, geht, gibt, hat for hast) and ist for bist,
  hast or hat for habt, habt for hast; ein for kein; ich, mich, dich, sich for each other; a number word for another
  (einundzwanzig for neunundzwanzig). Each was almost right before. A keyboard's slip on another word stays almost
  (der Gat for Gast).
- `drop_subject` stays false: German says its subject, so every transform line's answer has it.

## Checks

- **Content**: every unit validates in en, sl and it (`validateUnit`), its pages are the German book's, every right
  answer is graded right by the app's German grading and no word-bank decoy is right or almost
  (`GermanCourseContentTest`, 5 tests; a Python port of the grading over every answer and distractor while writing);
  every text per base and every title, goal, explanation, summary and worksheet title in en, sl and it; each step's
  point on the printed sheet's rule line in every base (the smoke's prints section).
- **Bridge**: `bunx tsc --noEmit -p .` clean; the whole smoke passes, 3328 checks (LANI_SMOKE_PORT=9830,
  LANI_SMOKE_DRAMA3_PORT=9848). New: the prints' German and English headings and dictionary, every supplement
  validated, the Carinthian teacher and the German course read in her voice for each base, the Kärnten village's
  `GET /course`, its Slovene texts, its printed sheet and the guide naming Eva, the German pages counted 16 or more.
- **tutor-export --check**: 89 files match the bridge.
- **App**: `GermanCourseContentTest` 5/5, `SchoolBoardTest` 6/6 (the German board), `GradingLanguagesTest` 35/35 (5 new
  German tests), `CourseContentTest` and `ItalianCourseContentTest` unchanged and passing; the full unit suite through lani-slot: 3699 tests, 2 skipped, none failed (with part c's `GrammarTest` fix, 668b33ef, cherry-picked).
- **Python**: `python3 -m unittest discover -s tests` 251 OK, `-s companion/tests` 142 OK (11 skipped).
- **Prints**: `companion/bin/school-prints --language de --base sl|en|it` makes the six sheets and the booklet (11
  pages) in each base, every word with a meaning; hallo, artikel and zahlen take two pages, the others one.
- **Emulator**: a synthetic learner of German from English in the Carinthian village (`make-learner.ts --target de --base en
--name Ana`; `LANI_CULTURE=kaernten LANI_QA_SETUP_PAIR=de-en LANI_QA_LEARNER=Ana LANI_QA_COURSE_UNIT=sein
companion/bin/qa-slot --no-town --steps course-unit`, the APK built first in a heavy slot): setup, then `sein` end to
end: the school, the unit, Lehrerin Eva's five intro steps (her German underlined for her voice, the table growing),
the summary, the first worksheet (the table of sein, 5/5, and three choices), the test straight away (10 right, 2 almost
where adb types müde as muede: 11/12, passed at 80 %), "Einheit geschafft!", the dev bridge's village keeping `de/sein`
done, then its sheet printed from the unit (fit.js the same in the WebView and in Chromium: one page, the table beside
the rule; the PDF A4). No crash. Screenshots `cu-*.png` in the scratchpad's `ux-course-de-a1-a/qa/`, the prints'
previews in `ux-course-de-a1-a/prints-{sl,en,it}/previews/`.

## Native check

Everything here is machine-written (Claude) and flagged so (`machine_written`, `review` in each unit, the page and the
supplement's meta). For a native speaker, Austrian German first, then each base:

**Austrian German (Carinthian standard)**
- hallo: Grüß Gott to everyone all day and Guten Morgen until about ten; Baba and Servus as goodbye; Tschau/Ciao
  accepted for Tschüss; «Grüß Gott, Frau Kathi!», «Frau Doktor», «Herr Doktor, Sie sind sehr nett»; a hiker «Frau
  Huber aus Graz».
- sein: «Es ist Kirchtag im Dorf», «auf dem Platz», a job without an article (Kathi ist Wirtin, Stanko ist Imker).
- artikel: das Cola as Austrian standard (an aside only, never a gap); the -erl words always das (das Häferl, das
  Sackerl, das Bankerl, das Glöckerl) and their plurals (kept out of gaps); der Kasten for a kitchen cupboard; «Der
  Erdapfel ist aus dem Gailtal», «Die Zeitung ist von heute», «___ Gasthaus heißt „Zur Linde“» as das only; the (ein)
  cue (Das ist ___ (ein) Mühle → eine); the rules as worded (-e mostly die, many neuters -er, -el/-er/-chen no ending,
  -s for -a, -o, -i); the made-up options Lehrere, Lehrers.
- plural: «Die Kirchen in Villach sind alt», «Die Gläser sind im Kasten», the packs' plurals.
- haben: «Toni hat schon ein Glas Most», «{learner}, du hast Glück: die Suppe ist da!», «Sie haben eine Mühle, Herr
  Franz», «Ihr habt Durst?» as a question; ages in words before the numbers' unit (Stefan ist dreißig Jahre alt).
- zahlen: whether to mention zwo at all (German-German on the phone; Carinthian dialect says zwa); «Wie viel Schafe»
  marked almost (standard or only spoken?); measure nouns (fünfzehn Gläser Honig or Glas Honig, Säcke Mehl or Sack
  Mehl); «Stankos Honig» or «der Honig vom Stanko»; the Kirchtagssuppe at six Euro; the made-up option dreizig.
- The grading's choices (`grading/de.json`): capitals forgiven, hab = habe, tschüs = tschüss, the known mistakes.

**Slovene**: the vi/ti glosses of Sie and du; «{m:prepričan|f:prepričana}», the dual where German has a plural (Florian
in Stefan, tu sta; Franz in Stefan imata; Sta žejna?); paradižnik said to come from Paradeiser; žegnanje for Kirchtag,
žegnanjska juha; Ziljska dolina, Baško jezero, Beljak, Celovec, Gradec; kokakola, skodelica/lonček for Häferl;
šestnajst kokoši, štirideset žemelj, kozarcev medu; «Stara je štiriinsedemdeset».

**Italian**: Lei and voi for Sie; valle del Gail, lago di Faak, Villaco; «die Katze (il gatto)», la tazza for Häferl;
«Nonno Sepp ha ottantun anni», «Il fabbro Toni ha cinquant'anni», «centoventi anni», «Kathi, hai fortuna».

**English**: British usage ("Have you got time?", "Mrs Huber"), "alpine pasture" for die Alm, "Lake Faak".

**The supplement** (`lexicon/de.extra.json`): every gloss and its Slovene and Italian meaning.

## Voice

No ElevenLabs credits were spent. Eva's German lines of the six intros (their examples, quick questions, answers,
tables, the German forms of the explanations and summaries) are 306 different lines, about 5,000 characters; the
explanations in a base, voiced as one clip each (`voice-build --explanations`), about 9,600 characters in English,
9,600 in Slovene and 10,000 in Italian. Until they are voiced, the node makes them on demand as an intro opens, or the
phone reads them (its German voice for Eva's parts).

## What's left

- **Units 7–18** are course-de-a1-b's and -c's (branched from this branch's groundwork, 626f8335); until they are
  merged, the school's A1 shows these six and Eva preparing the rest ("Ich bereite die nächsten Stunden vor").
- **The native check** above, Austrian German first.
- **Voices**: Eva's German lines and the explanations are voiced on demand (no credits spent); `voice-build --only
  teacher` on a Carinthian node makes them ahead (about 5,000 characters of German, about 9,600-10,000 per base).
- **A German dictionary**: Wiktionary's German (`de.json.gz`) is a download no node or phone has yet; the supplement
  covers the course's words, not the village's other content.
- **Capitals**: forgiven by design. If Jan wants a lowercase noun marked almost (a hint "capital"), that is a change
  to `Grading.check` (case-sensitive words after the first), not to the table.
- **Forms cards and counting for German**: Slovene's only; the German units name no `lemma`/`form`.
- **Seen in QA, not this branch's**: the end of a unit's test says the next unit with its title twice ("★ Nächste
  Einheit: Der, die, das · The articles: der, die, das · Next unit: Der, die, das · The articles: der, die, das"): the
  label is written in both languages and so is the title inside each; the Italian course's end reads the same way.
