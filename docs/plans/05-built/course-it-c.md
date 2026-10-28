# Built: the Italian school's A1 units 13–18 (2026-10-28)

Teo (a child of about seven, learning Italian from German; the Friuli village on the coast, a tablet) built the school
and couldn't enter it: the outline (`companion/course/it/course.json`, A1, 18 units) had every unit planned. Three
branches write them side by side: `friuli-school` the school's scene, the plumbing and units 1–6, `course-it-b` units
7–12, and this branch (`course-it-c`) units 13–18, in the outline's order and with its titles. The format and how a
unit is written are companion/COURSE.md ("Italian A1, units 13 to 18" has the summary).

## The units

Each has a guided intro of five steps (Maestra Elena's lines in German first, then Slovene and English; the Italian in
bold for her voice, its meaning in brackets after it; two examples a step, the table growing row by row, a quick
question each), three worksheets from easy to harder (a sort or a table first, then a gap text with a word bank, then a
transform list), and a test at pass 80 with new sentences. The sentences are Teo's village: the sea and the beach,
the pond, the field, the wood, the mill, the campfire, the festa, and its people: Maestra Elena, Nonna Rosa, Giulia,
Lorenzo, Tommaso, Davide, Franco, Aldo, Nonno Bepi and his dog Moro, Zia Nives and her radio, Matteo's vendemmia.

| # | unit | intro | examples | worksheets (items) | test |
|---|---|---|---|---|---|
| 13 | `verbi-irregolari` Vado, faccio, vengo, voglio | 5 steps | 10 | sort + table (16) · gap text with a bank + choice (8) · transform (6): 30 | 14 items |
| 14 | `domande` Chi? Che cosa? Dove? Perché? | 5 | 10 | sort + 2 choices (14) · gap text with a bank + choice (7) · transform (8): 29 | 13 |
| 15 | `ore-giorni` Che ore sono? I giorni, i mesi | 5 | 10 | table + sort (15) · gap text with a bank + choice (7) · transform (8): 30 | 11 |
| 16 | `piacere` Mi piace, mi piacciono | 5 | 10 | sort + 2 choices (14) · gap text with a bank + choice (7) · 2 transforms (8): 29 | 13 |
| 17 | `imperativo` Vieni! Guarda! Ascolta! | 5 | 10 | table + sort (16) · gap text with a bank (7) · 2 transforms (7): 30 | 12 |
| 18 | `passato-prossimo` Ho mangiato, sono andato | 5 | 10 | table + sort (15) · gap text with a bank + choice (7) · transform (8): 30 | 12 |

- **verbi-irregolari**: andare (vado al mare), venire and coming along (Vieni con me? Sì, vengo!), fare (and the
  weather: Fa caldo), stare (Come stai? Sto bene), volere with a thing or an infinitive (Voglio un gelato, vuole
  giocare). The gap text: a morning at the sea; the test's: Nonno Bepi's story at the campfire.
- **domande**: the yes/no question by the voice alone and no, non ho …; chi, che cosa (cos'è), dove for where and where
  to (one word, German has two), da dove, quando, come, perché as why and because, quanto/quanta/quanti/quante agreeing.
  The gap text: Giulia asks Nonno Bepi under the linden.
- **ore-giorni**: sono le + the hour, è l'una, mezzogiorno and mezzanotte; e dieci, e un quarto, e mezza (3:30 is le tre
  e mezza: German's halb vier counts towards the next hour, Italian doesn't), meno un quarto; a che ora? alle otto,
  all'una, a mezzogiorno; the days (lunedì, and il sabato for every week), the months (a maggio), a date (il venti
  maggio, the bees' day; il primo). The gap text: Zia Nives's radio.
- **piacere**: the thing liked as the subject (Mi piace il gelato, never mi piace gelato or io piace), piacciono for
  more than one, piace with a verb, ti piace? and non mi piace, gli and le, a + a name. The gap text: on the beach,
  the crabs that pinch.
- **imperativo**: guarda! (-are), leggi! (-ere, -ire), voi as in the present, noi as let's (Dai, andiamo allo stagno!),
  the short ones (vieni, vai/va', fai/fa', stai/sta', dai), non + the infinitive to one (Non toccare il fuoco!) and
  non + voi to several. The gap text: Nonna Rosa at the sea; the test's: Maestra Elena's class.
- **passato-prossimo**: avere + -ato, -uto, -ito (Hai capito?), the irregular participles (fatto, visto, preso, letto,
  detto), essere for going and coming, the participle agreeing with essere (Lorenzo è andato, Giulia è andata, io e i
  bambini siamo andati). The gap text: yesterday at the sea; the test's: Giulia and her aunt in Grado.

The units assume what units 1–12 teach (essere, avere, the articles and the plural, the numbers, the regular present,
the adjectives, c'è, the prepositions fused with the article, the possessives) and nothing after them: the sentences
around a gap use only those and the units before.

## The grammar pages

- **`imperativo`** (new, A1): the outline named no page for the unit. The tu forms (-a, -i), voi, noi as let's, the
  short irregular ones, the negative (non + infinitive to one, non + voi), and a pointer to `tu-lei` for the polite
  form (Scusi! Venga!), which A1 leaves for later. A table of eleven verbs in tu, voi and noi, eight examples.
- **`ore-giorni`** (new, A1): the outline gave the unit only `numeri` (the numbers), so the cheat sheet had nothing on
  the time, the days or the months during the unit's sheets. The unit names both (`ore-giorni` first, as the summary).
- **`verbi-irregolari`**: volere and potere added (two rows, a paragraph per base, an example), as the unit's title
  says voglio.
- The grammar smoke and the app's `GrammarTest` pinned the Italian book at 16 pages, as German's and English's: German
  and English stay at 16, Italian is every file of its book and 18 or more.

## Texts in the learner's language

The texts the learner reads beside the Italian (`explain`, `hint`, `instruction`, `translation`, `cue`, a sort's
`label`, a table's `columns`) are per base, `{"de", "sl", "en"}` with German first, as friuli-school's branch makes
the bridge and the app resolve them (COURSE.md there: "Texts in the learner's language"). A hint of Italian forms only
("tu → ?", "noi: -iamo", "-are → -ato") stays a string. Until that branch is on main these six units don't validate
against main's schema (main takes only strings there): they are checked against friuli-school's `validateUnit` in all
three bases (below), and this branch is rebased once it lands.

## Slovene-only, so left out

- A table cell's and a transform line's `lemma` and `form`: the forms book is Slovene's only (`FORMS_LANGUAGES`), so
  no Italian forms card would practise them.
- "What a unit turns on" (forms cards, counting cards, the numbers module, clock times): Slovene's units only; the
  Italian `numeri` and `ore-giorni` turn on nothing.
- The smoke's check that a gap's answer is a form of the word in brackets uses the dictionary's forms, Slovene only.
- The intro's voice ahead (`voice-build`'s course source, the smoke's `course-speech`) reads the Slovene course; the
  app's check that a bold span is Italian works (`Spoken.looks` has Italian's accents and frequent words, and the
  intro's own words count), so Elena reads the bold Italian.

## Tests

- **The bridge's validator** (friuli-school's `validateUnit`, read-only from its worktree, the bases de, sl, en): the
  six units validate; every page they name is a page of the Italian book (the two new ones and the edited one validate
  with `validateGrammar`); the outline validates with the six unplanned.
- **The app's content test for Italian**: `CourseContentTest` with the course and the grading switched to Italian and
  to these six units, run once with friuli-school's `Course.kt` (its texts per base) applied, neither committed: its
  4 tests pass. The app parses and plays every unit at its level, every exercise is a type it knows, every intro step,
  example, row and quick question survives the parse; every right answer of a sheet, a test and an intro's question is
  graded right by the app's `Grading` in Italian, and no word bank's distractor is right, nor almost, for any gap;
  every page named is in the bundled book. A port of `Grading` to Python (the scratchpad's) agreed before it.
- **The app's unit tests** on the tree as committed (main's `Course.kt`): the full suite, 3,259 tests in 416 classes,
  none failed (2 skipped); `GrammarTest` 16, `CourseContentTest` 4, `CourseTest` 6 among them.
- **The bridge**: `bunx tsc --noEmit -p .` clean. The smoke (`LANI_SMOKE_PORT=8860 LANI_SMOKE_FRIULI_PORT=8863
  LANI_SMOKE_TOWN_PORT=8866`): 2,833 checks pass, 2 fail, both expected until friuli-school lands: "course/it: every
  unit validates" (main's schema takes only strings for the texts per base) and so "every unit of the outline that
  isn't planned has its file". The grammar section's Italian checks pass (18 pages, each in sl, en and de).

No screen changed: tap counts are as before, no emulator QA (friuli-school plays a unit on the emulator).

## For the native check

Every unit and both new pages are marked machine-written (`review`). What most needs an Italian speaker's eye (and a
German one's for the child's German):

- **verbi-irregolari**: Sì, vengo! (not vado) to Vieni con me?; I bambini stanno zitti; Fa caldo.
- **domande**: Che / Cosa / Che cosa all accepted in a question; Giulia dov'è? and Quand'è la festa? accepted;
  Tommaso fa il pagliaccio.
- **ore-giorni**: le tre e mezza and e mezzo both accepted; a luglio and in luglio; ad aprile on the page; 5:45 as le sei
  meno un quarto beside le cinque e tre quarti and e quarantacinque; la giornata delle api (20 May).
- **piacere**: Ha un secchio pieno (of shells); Pizzicano!; nuotare in mare; Giulia non ama l'inverno.
- **imperativo**: vai and va', fai and fa', stai and sta' all accepted; stai attento (attenta for a girl: the units only
  say it to boys); Lorenzo, non disegnare sul banco; Mangia la sabbia! → Non mangiare la sabbia!
- **passato-prossimo**: io e i bambini siamo andati, siamo tornati … stanchi (the group named, so a girl isn't asked to
  guess it is masculine); the participles' German (gefahren for
  andare when it's a trip); a Grado (Gradež in the Slovene).
- The Slovene and the German of every `say`, explain and translation are machine-written too.

## What's left

- Rebase onto main once friuli-school lands, then run the smoke's course section and the app's `CourseContentTest`
  (which then covers Italian) on main's code.
- The intro's voice ahead for Italian (`voice-build`'s course source) and the prints of the Italian booklet are
  friuli-school's or later work.
