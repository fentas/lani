# Built: the Italian school's A1 units 7–12 (2026-10-28)

Teo (a child of about seven, learning Italian from German, in the Friuli village; a tablet) built the school and
couldn't enter it: the Italian outline (companion/course/it/course.json) had A1's 18 units, every one planned. Three
branches write them side by side: `friuli-school` the school scene, the Italian plumbing and units 1–6 (ciao, essere,
articoli, plurale, avere, numeri), this branch (`course-it-b`) units 7–12, `course-it-c` units 13–18. The format and how
a unit is written are companion/COURSE.md ("The Italian course: units 7–12" for these).

## The units

Each has a guided intro of five or six steps (Maestra Elena's lines in German, Slovene and English with the Italian in
bold for her voice, two or three examples a step, the table growing, a quick question each), three worksheets of 25–32
items, and a test at pass 80 of 12–13 items. The sentences are the village's: Nonna Rosa's frico and gubana, Davide's
sheep, Bruno's hammer and iron, Franco's mill and old red tractor, Aldo's honey, Nonno Bepi's stories and his dog Moro,
Zia Nives's radio, the pond's frogs and ducks, the sea's gulls, boats and shells, the square and its fountain. They
assume units 1–6 (the greetings and tu/Lei, essere, the articles, the plural, avere, the numbers), and nothing around a
gap comes from a unit after it: units 7–10 say "in cucina", "in piazza", "a casa", "con il cane", never nel or al.

| # | unit | title (it) | its pages | intro | worksheets (items) | test |
|---|---|---|---|---|---|---|
| 7 | `presente-are` | Parlo, mangio, gioco | presente-regolare | 5 steps | table + sort (17) · gap text with a bank + choice (7) · transform (7) | 12 items |
| 8 | `presente-ere-ire` | Leggo, dormo, capisco | presente-regolare | 5 | table + sort (18) · gap text with a bank + choice (7) · transform (7) | 13 |
| 9 | `aggettivi` | Grande, piccola, rossa | aggettivi-accordo | 6 | sort + table (16) · gap text with a bank + choice (7) · 2 transform lists (9) | 12 |
| 10 | `ce-ci-sono` | C'è, ci sono: dov'è? | ce-ci-sono | 6 | sort + 2 choices (10) · gap text with a bank + choice (7) · 2 transform lists (8) | 12 |
| 11 | `preposizioni` | A, in, di, da: nel bosco, al mare | dove-preposizioni, preposizioni-articolate | 6 | sort + table (16) · gap text with a bank + 2 choices (8) · transform (7) | 12 |
| 12 | `possessivi` | Il mio, la mia: mia nonna | possessivi | 6 | table + sort (17) · gap text with a bank + choice (7) · 2 transform lists (8) | 12 |

- **presente-are**: -o, -i, -a (io mostly left out), -iamo, -ate, -ano (parlano stressed at the front; cantiamo is
  also let's), Lei with the lui form (to Maestra Elena and the adults: signor Franco, signora Marisa), the question
  and non, and the two spelling traps: giochi, peschi (an h before i) and mangi, mangiamo (one i). The gap text: a
  Sunday in the village. "X e io" is noi (Slovene's dual noted for the Slovene base).
- **presente-ere-ire**: leggo, leggi, legge (the g hard, then soft: dsch for the German child), -iamo, -ete, -ono;
  -ire as -ere but voi -ite (dormite, aprite); -isc- (capisco, capisce with sch, finisco, pulisco) and not for noi and
  voi (capiamo, capite); -are against -ere/-ire (lui parla, lui legge). The gap text: an evening at Nonno Bepi's story.
- **aggettivi**: -o, -a, -i, -e (il gatto nero, le pecore nere), -e adjectives with two forms (verde, verdi), the
  agreement after è and sono, a few before the noun (una piccola barca, un vecchio trattore rosso; colours after), a
  boy and a girl together masculine (stanchi), bianchi/bianche and stanchi with an h, grigio → grigi. The gap text: a
  day at the sea.
- **ce-ci-sono**: c'è for one, ci sono for more (with numbers and tante), the question and Ci sei? Sì, ci sono!, non
  c'è (non c'è pane: no article), c'è against è (C'è la luna / È la luna / La luna è grande), dov'è and dove sono. The
  gap text: the night sky and Moro. The plural list asks for the whole sentence (`whole`), its number in brackets.
- **preposizioni**: a and in without an article (a Gorizia, a casa, in Italia, in cucina), then one preposition a step
  with its row of the fused table: in (nel, nello, nell', nella, nei, negli, nelle), a (al … alle; vicino allo stagno),
  da with a person (da Nonna Rosa, dal fabbro, dalla nonna), di for whose (del nonno, della nonna, dell'apicoltore),
  su (sul, sulla, sui), and sotto, sopra, dietro, con that don't melt. The gap text: where everyone works today.
- **possessivi**: il mio, la mia, i miei, le mie with the article (il mio zaino: il, not lo), tuo, suo for his and her
  by the thing, one family member without the article (mia nonna; la mia nonna heard too), the article back in the
  plural (i miei nonni), nostro, vostro, loro (never changes), Suo with Elena (and signora Marisa). The gap text: Giulia
  tells about her family (Nonna Rosa, Zia Nives her sister, Lorenzo and his grandpa Franco).

## Texts in the child's language

Teo reads German, so every text he reads is per base with German first: `say` and `goal` (`{"de", "sl", "en"}`), the
examples (`{"it", "de", "sl", "en"}`), and, with friuli-school's plumbing (COURSE.md "Texts in the learner's
language": `explain`, `hint`, `instruction`, `translation`, `cue`, `label`, `prompt` and a table's `columns` may be a
text per base, the bridge and the app give the learner theirs), every explanation, every hint in words, every
instruction ("Completa · Ergänze das Verb": Elena's Italian, then the base), every translation, a table's prompt and
columns. A hint of forms only ("loro …", "c + i → ch + i", "-iamo") stays a string. German with du; Elena says tu to
the child, the child Lei to her; tu everywhere else among the children and the family.

## Grading

- Every right answer graded right by the app's Grading for Italian (accents almost, the apostrophe loose), every word
  bank's distractor wrong for every gap and none almost (`ItCourseBScratchTest`, below).
- Spoken forms are never wrong: "la mia nonna" is the step's choice's `almost` and a gap's second answer (also "la sua
  sorella", "il suo nonno", "il mio nonno"); "nella fucina" and "in fucina" both right.
- A transform's person in brackets left out is right as the app's `drop_subject` makes it ("Giulia canta una
  canzone." and "Canta una canzone."); the possessives' lists never begin with the bracket's person, so they ask for
  the whole sentence by themselves; the c'è → ci sono list says `whole` (its bracket is a number).
- Nothing names a forms book form (`lemma`/`form`): the forms cards are Slovene's only (`FORMS_LANGUAGES`).

## Tests

- **The bridge's `validateUnit`**, with friuli-school's `course.ts` (texts per base, not on main yet): the six units
  validate for the bases en, de and sl, every text per base has de, sl and en, every page they name is an Italian
  grammar page, the outline validates, and the course store serves the six as written (twelve still planned). Against
  main's `course.ts` they don't: main wants a string where they give a text per base.
- **The app**: a copy of `CourseContentTest` for Italian (`ItCourseBScratchTest`, run with friuli-school's `Course.kt`,
  neither committed): the bundled Italian course parses and plays the six units at A1, no exercise unsupported, every
  intro step, example, row and quick question parsed; every right answer of a sheet, a test and an intro's question
  graded right by the app's Grading in Italian, every word bank's distractor wrong for every gap and none almost; a
  choice's answer among its options; the German explanation resolved for a German base; the blackboard's title and
  forms written with the board's letters. 1 test, passed (`testDebugUnitTest`, 5 min 39 s).
- **The bridge smoke** (ports 8850–8859: `LANI_SMOKE_PORT=8850`, `_FRIULI_PORT=8853`, `_TOWN_PORT=8856`,
  `_SETUP_PORT=8852`, `_KAERNTEN_PORT=8854`, `_LAKELAND_PORT=8855`, `_SERVICE_PORT=8857`, `_DRAMA3_PORT=8859`), with
  friuli-school's `course.ts`: the course section passes for `course/it` (the outline, every unit, their levels, the
  pages they name, machine-written); all 2,835 checks pass (4 min 25 s). A first run under load had one failure outside
  the course (lexicon, "the tutor hears the words added, gathered in one line": a channel note's timing), not again.
- **The read-aloud's fallback** (a port of `IntroSpeech.runs`, `vocabulary`, `within` and `Spoken.looks`): which bold
  Italian of each step's explanation the teacher's voice reads when the explanation's one clip can't be had; the forms
  each step names are in its examples, rows or question, so they are hers, except a few lists and infinitives (below).

## What's flagged for the native check

Every file says so in `review`. Beyond the Italian at large:

- presente-are: "Domenica in paese"; "giocare a nascondino"; the stress tip for parlano; Lei only to Elena and adults.
- presente-ere-ire: "È sera"; the sound tips for a German child (leggi as dsch, capisce as sch); aprete and chiudite
  as made-up wrong options.
- aggettivi: "Il mare oggi"; "colours come after the noun" as a child's rule; the playful table (il cane verde).
- ce-ci-sono: "Di notte" (Ci sei? Sì, ci sono!); "In piazza è una fontana" as a wrong option; "Di' di no" for a child.
- preposizioni: "Dove sono tutti?" (in fucina / nella fucina both right; Nonna Rosa è in cucina with only in);
  "Oggi siamo al mare"; da for a child (dal fabbro).
- possessivi: la mia nonna, la sua sorella, il suo nonno, il mio nonno accepted as spoken; Suo with a capital;
  "La mia famiglia".
- The German, Slovene and English are machine-written too.

## What's left

- The units need friuli-school's per-base texts (bridge `course.ts`, app `Course.kt`) to validate and play: until it
  is merged, main's schema refuses them. Rebase after it lands and run the content tests and the smoke again.
- The read-aloud's fallback (when the explanation's one clip can't be had) reads a bold Italian word in Elena's voice
  only when `Spoken.looks` says it is Italian or the intro shows it (`IntroSpeech.vocabulary`: the examples, the rows'
  words, the questions, the title); an infinitive named only in `say` (cantare, leggere, finire), an ending (-are,
  -isc-) and two lists (dormo … dormono, parlo, leggo, dormo) are then the phone's German voice. ElevenLabs' one clip
  reads them right. A code change would fix it for every course: the vocabulary taking the unit's `words` and the
  table head's words of the language too.
- Printed sheets and the voice prebuild for Italian units: as friuli-school's plumbing makes them.
