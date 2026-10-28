# Built: B1's second half of the Slovene school (2026-10-27)

Jan (the learner: Slovene from English, A1, a village of Vas): "let's work out/complete A2 and B1 (no need to show it
all directly)". The outline (companion/course/sl/course.json) had A2's 14 units and B1's 16 planned. This branch
(`course-b1b`) writes B1's second half, its last eight units, in the outline's order and with its titles; B1's first
half (`course-b1a`) and A2 (`course-a2`, `course-cases`) were written beside it. The format and how a unit is written
are companion/COURSE.md; what each item of B1 is, with sources, docs/grammar-syllabus.md ("B1").

## The units

Each has a guided intro of five steps (Mojca's lines, the Slovene in bold for her voice, two examples a step, the table
growing, a quick question each), three worksheets of 22–30 items, and a test at pass 80 of 9–12 items, in en, de and it.
The sentences are the village's adult life: the mill, the grape harvest, the fair, the inn, the storm, the feast under
the linden, the village chronicle. B1 assumes A2 (every case in both numbers, the pronouns, aspect's first pairs, the
conditional's basics) and B1's first half before it (relative clauses, reported speech, clauses), which the sentences
around the gaps use; nothing around a gap comes from a unit after it.

| # | unit | title (sl) | its pages | intro | worksheets (items) | test |
|---|---|---|---|---|---|---|
| 9 | `pogojnik-polno` | Če bi deževalo, bi … | pogojnik-ce-bi, pogojnik | 5 steps | table + 2 choices (12) · gap text, a meaning (9) · 2 transform lists (9) | 12 items |
| 10 | `nedolocni-zaimki` | Nihče ni prišel: nekdo, nič, nikjer | nedolocni-zaimki, zanikanje | 5 | sort + table (16) · gap text (8) · transform (6) | 12 |
| 11 | `naslonke-red` | Ne bom ti ga dal: vrstni red naslonk | naslonke-red, naslonke | 5 | 6 reorders, the cards for the short words (6) · gap text (8) · 2 transform lists (8) | 10 |
| 12 | `stevila-glagol` | Pet hiš se je podrlo | stevila-glagol, stevila-samostalniki, rodilnik-kolicina | 5 | table + sort (15) · gap text + choice (9) · transform (6) | 12 |
| 13 | `cas-izrazi` | Enajstega junija, to soboto, dve uri | cas-izrazi, datumi, kdaj-cas | 5 | sort + table (15) · gap text (8) · transform (6) | 12 |
| 14 | `custva` | Mudi se mi, boli me glava | custva, dajalnik, tozilnik | 5 | sort + table (13) · gap text (8) · 2 transform lists (8) | 12 |
| 15 | `trpnik` | Hiša je bila sezidana: trpnik | trpnik, pridevniki-ujemanje | 5 | sort + multi (13) · gap text with a bank + a sign (8) · transform + choice (6) | 9 |
| 16 | `glagolniki` | Branje, pisanje, odkritje | glagolniki, sklanjatev-srednji | 5 | sort + table (16) · gap text with a bank (8) · transform (6) | 10 |

- **pogojnik-polno**: real against imagined (če bo / če bi), bi + the l-form agreeing, the same form for the past
  (če bi včeraj prišel; *bi bil prišel* only to recognise), ne bi with the genitive of what isn't there, ko bi le, da bi
  for a wish and a purpose. The gap text: France the miller's dreams.
- **nedolocni-zaimki**: ne-/ni-/vsak- on the question words (nekdo, nihče, vsakdo; nekje, nikjer, povsod; nekoč,
  nikoli, vedno), their cases (nikogar, nikomur, z nikomer; nečesa, ničesar), noben and vsak with a noun, plain kdo,
  kaj, kdaj in a question (Je kdo doma?), -koli. The gap text: who took the cellar's key.
- **naslonke-red**: the whole order (sem, bi – se – dative – accusative – je, bom), with a no (nisem ti ga, ne bom ti
  ga, and after something first: jutri ti ga ne bom, Marko ga ni), in questions (Si ga videl?), after da, ker, ali, and
  the infinitive's short words going up (Moram ti ga vrniti). The first worksheet is tiles to order, the manuals' cards
  for the short words. The order after Toporišič (Slovenska slovnica 2004) as Sever Torkar sums it up.
- **stevila-glagol**: 1 singular, 2 dual (sta prišla, sta zboleli), 3–4 plural, 5+ and veliko, malo, nekaj, koliko the
  singular neuter in every tense (pet gostov je prišlo, deset gostov bo prišlo), the adjective's genitive plural, oba.
  The gap text: the damage after the storm.
- **cas-izrazi**: the genitive of a date and a time told of (nekega dne, tistega večera, leta 1890), the accusative of
  which (to soboto, prejšnji teden, vsako jutro) and of how long (dve uri, celo zimo), že + the present (Tukaj živim že
  dve leti), pred + the instrumental, čez, od … do, med, okoli, lani, letos, sinoči, drevi. The gap text: the village
  chronicle's mill.
- **custva**: the dative (mrzlo mi je, žal mi je; mudi se mi, zdi se mi, ne ljubi se mi) and the accusative (boli me,
  zebe me, skrbi me; strah me je + the genitive), with names (Janezu se mudi, Mamo boli glava), and the past: neuter
  (zeblo me je) but agreeing with what hurts (glava me je bolela). The gap text: Nejc's bad day.
- **trpnik** and **glagolniki** are "know" items at B1 (the syllabus: recognise and understand, produce a little): their
  sheets are mostly what a form means, which verb it comes from, which ending it has (a choice, a multi, a sort, a word
  bank of participles or verbal nouns), with a little making (Okno je odprto, Plug je popravljen; plavati → plavanje,
  Za pečenje kruha …). trpnik: the participle in -n/-t, biti + it for a state, se for what is done (Seno se suši na
  kozolcu), the active the language prefers, and okno je odprto / se je odprlo / je odprl. glagolniki: -anje, -enje,
  -tje, the genitive of what is done, the village year's action nouns (setev, košnja, žetev, trgatev), those with a
  meaning of their own (življenje, vprašanje).

## The grammar pages

Eight new B1 pages in companion/grammar/sl/, each with a cheat-sheet card (its rule line, a compact table, two
examples, a tip), so a unit's summary card is its own rule and not an A1/A2 page's: `pogojnik-ce-bi`,
`nedolocni-zaimki`, `naslonke-red`, `stevila-glagol`, `cas-izrazi`, `custva`, `trpnik`, `glagolniki`. The A1/A2 pages
the outline named stay in each unit's `grammar` (pogojnik, zanikanje, naslonke, stevila-samostalniki, datumi, kdaj-cas,
dajalnik, tozilnik) and keep turning on their forms (COURSE.md, "What a unit turns on": the rows for these units). The
book has 57 Slovene pages now with course-b1a's five (GrammarTest).

## Tests

- **Bridge smoke** (`bun test/smoke.ts` on the ports 8821–8829 of this branch's block: `LANI_SMOKE_PORT=8821`,
  `_FRIULI_PORT=8823`, `_KAERNTEN_PORT=8824`, `_LAKELAND_PORT=8825`, `_TOWN_PORT=8826`, `_SETUP_PORT=8828`,
  `_SERVICE_PORT=8829`; 8820 was held by another branch's bridge service): all 2,751 checks pass. Its course section validates every unit, its pages and the outline; course-speech reads them.
- **The app's content test, new: `CourseContentTest`** (data/), for every unit file of the course, whoever wrote it:
  the app parses and plays it at its outline's level, none left planned; every exercise is a type the app knows, every
  intro step, example, row and quick question survives the app's parse; every right answer of a sheet, a test and an
  intro's question is graded right by the app's own Grading, and a word bank's distractors are wrong for every gap; a
  choice's answer, a multi's answers and a reorder's solutions fit; every page a unit names is in the bundled book.
- `GrammarTest` (57 pages), SchoolBoardTest (every letter of the titles and the tables'
  forms in chalk), CourseTest, PrintsTest, IntroSpeechParityTest, CheatSheetTest, GrammarBookTest, FormsCardTest, SchoolTest:
  pass; the full unit suite: 3,076 tests in 390 classes, none failed. Two fixes came out of them: the new content test found mestnik's distractor
  «ogenju» almost right for its gap (a stem slip, as Grading forgives: listed, A1's, left as it is), and SchoolBoardTest
  found cas-izrazi, custva and stevila-glagol's titles filling the blackboard with no form under them: their intro
  tables' third column is a meaning now, as A1's are, and the chalk font writes "…".
- **Emulator QA**, new step `school-unit`, a unit end to end from its own file (course-b1a's hook `school:<unit>`,
  which merged first: the class ready, the unit's level open, the unit as never begun; the unit from its level in the
  school, as b1a's `school-b1` goes to oziralni-ki, with its `tap_above` and `check_sheet`), `pogojnik-polno` by default
  (`LANI_QA_UNIT=<id>` another): passed on the emulator with a synthetic learner (`LANI_DEV_DATA`, Ana), in one run of
  about ten minutes: the unit's page, its five intro steps (choices tapped, clozes typed), the summary card, the first
  worksheet's table and choices, the test straight away, passed 10/12 (8 right, 4 almost: adb types no č, š, ž), Mojca's
  cheer, the dev bridge's village keeping the unit done. Screenshots: the scratchpad's `ux-course-b1b/` (the page, the
  intro's first and last step, the summary, the sheet before and after checking, the test, the cheer, the unit done).
  Two QA fixes came out of it: the step scrolls to "Preveri · Check" and to a choice's option however long the intro's
  table has grown, and `bin/qa` had two `l10n` helpers (the second hid the first, so setup crashed: main fixed it too,
  988cb6fc, and this branch takes main's). The run was before the rebase onto course-b1a; since, the step goes through
  the school instead of the unit's page, not run again on the emulator (its queue was long; the content test covers
  every unit).
- **Prints** (`school-prints --level B1 --units …`, English, Chromium): every unit fits, pogojnik-polno, naslonke-red,
  cas-izrazi and custva on two pages, the other four on one; the B1 booklet of them 14 pages. Each sheet leaves out 3–6
  of its words the dictionary has no meaning for (pripovedovanje, hoja, prepovedan …), as the generator does.

## Voice

Mojca's Slovene of these units is in the voice corpus as every unit's (`voice-build --only teacher`). The dry run on a
synthetic store (a learner of `test/fixtures/make-learner.ts`, no clips, no key; `LANI_ENV_FILE=/dev/null`): her
course row grows from 797 texts (12,556 characters) on main to 1,176 (21,428): **379 texts, 8,872 characters** for these
eight units; all of `--only teacher` 1,490 texts, 34,234 characters. The English explanations, apart and on demand:
48 more (40 steps and 8 summaries), 157 in all, **19,193 characters more** (47,936; about 9,600 credits more with
`eleven_v4_turbo`). On Jan's store the clips there are counted as cached.

## For the native check

Every unit and page is marked machine-written (`review`). A second pass (a reviewer agent, with Fran) went through
all sixteen files for case, agreement, clitic order and the translations; its four errors (lahko with an infinitive,
"je never comes first", mudim se as no form, the dual l-form "for men") and its likely points are fixed. What most
needs a native speaker's eye:

- **pogojnik-polno**: the past unreal as če bi + the l-form (Če bi včeraj prišel …), *bi bil prišel* only to
  recognise; Če ne bi bilo vetra; da bi against da bodo for a purpose (Babica peče potico, da bi bili otroci veseli);
  Francetove sanje (bi ljudje prinašali).
- **nedolocni-zaimki**: nič and ničesar both accepted after a negated verb; Nikogar ni doma beside Nihče ni doma;
  Kdo je vzel ključ? (pasja uta).
- **naslonke-red**: the order with something first (Jutri ti ga ne bom dal, Marko ga ni videl); Ji boš pomagal?;
  the spoken A si ga videl? and the short words first (Ji ga je dal) accepted; Nož s sejma.
- **stevila-glagol**: Francetu sta zboleli dve kravi; Po neurju (brez opeke or brez strešnikov); sosedje beside
  sosedi.
- **cas-izrazi**: nekega dne (nekega dneva accepted); tistega večera beside tisti večer; a date read as petnajstega
  devetega accepted; nocoj, with drevi as bookish; Iz vaške kronike.
- **custva**: the past agreeing with a subject (Zali se je zdela slovnica težka, Šola mi je manjkala) and neuter
  without one (Zeblo me je); Slab dan (Mamo je skrbelo, Šola mi manjka).
- **trpnik**: Pred praznikom (jutri se pleše, natočeno v vrče, trava je pokošena); Prepovedano kajenje beside the
  more usual Kajenje prepovedano.
- **glagolniki**: pečenje or peka kostanja (both accepted); pletenje, odprtje; Vaško leto.

## What's left

- The native check above; spoken forms a native speaker uses are to be accepted, never graded wrong (label standard
  against spoken).
- QA plays the first worksheet and the test of a unit; the other sheets are played by `CourseContentTest`'s grading
  only.
