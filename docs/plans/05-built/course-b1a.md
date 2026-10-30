# Built: B1 of the school, part one (eight units)

*Built 2026-10-27, branch `course-b1a`. Jan: "let's work out/complete A2 and B1 (no need to show it all directly)". The
outline (companion/course/sl/course.json) planned B1 as 16 units; this branch writes the first eight, the other half is
`course-b1b`'s. The format and how a unit is written: companion/COURSE.md. Everything here is machine-written (Claude) in
Slovene, English, German and Italian, and waits for a native speaker's check (below).*

## What

Eight units of B1, each a guided intro (Mojca's steps with their quick questions, the growing table), three worksheets
and a test, built on A2 (the six cases in the singular and the plural, the pronouns, aspect's first pairs, the
conditional's basics) and set in the village's adult life: the fair on the square, the bridge over the stream, Ančka's
news, Janez's stories by the fire.

| unit | title | steps | examples | worksheets (exercises, items) | test | grammar pages |
|---|---|---|---|---|---|---|
| `sklanjatev-posebnosti` | Oče – očeta, dan – dneva | 6 | 12 | Kaj zraste? (sort, choice: 13); Pri ognju (table, gap text: 14); Ljudje in otroci (transform: 7) | 12 items, pass 80 | sklanjatev-posebnosti, sklanjatev-moski, sklanjatev-srednji, sklanjatev-zenski |
| `dvojina-skloni` | Z dvema družinama: dvojina v sklonih | 6 | 12 | Dveh ali dvema? (table, sort: 16); Izlet z dvema družinama (gap text: 8); Pridita k nama! (transform: 8) | 11 items, pass 80 | dvojina-skloni, dvojina |
| `vid-vsi-casi` | Vid v vseh časih: ne hodi tja! | 6 | 12 | Naredi! Ne delaj! (table, sort: 16); Zgodba pri ognju (gap text: 9); Ne hodi tja! (transform, choice: 8) | 12 items, pass 80 | vid-casi, glagolski-vid, velelnik |
| `svoj` | Svoj: posodil mi je svoj avto | 5 | 10 | Svoj ali njegov? (table, 3 choices: 11); Sejem na trgu (gap text: 8); Moj ali svoj? (transform: 8) | 11 items, pass 80 | svoj, pridevniki-ujemanje |
| `dolocna-oblika` | Daj mi zeleni nož! | 5 | 10 | Zelen ali zeleni? (table, sort: 16); Sejem (gap text: 8); Kateri? Zeleni! (transform, choice: 8) | 12 items, pass 80 | dolocna-oblika, pridevniki-ujemanje |
| `oziralni-ki` | Ki in kateri: prijatelj, ki sem ga srečal | 6 | 12 | Ki ga, ki jo, ki jih (sort, table: 15); Vas, v kateri živim (gap text: 9); Dva stavka v enega (transform: 8) | 11 items, pass 80 | oziralni-ki, naslonke |
| `odvisni-govor` | Rekel je, da … | 6 | 12 | Da, ali, naj? (sort, table: 17); Novice tete Ančke (gap text: 8); Povej naprej (transform: 8) | 12 items, pass 80 | odvisni-govor, naslonke |
| `odvisniki` | Ker, če, da bi, tako da | 6 | 12 | Kateri veznik? (gap text, table: 12); Most čez potok (gap text, choice: 8); Zakaj? Čemu? (transform: 8) | 12 items, pass 80 | odvisniki, vezniki, pogojnik, naslonke |

Each unit's first worksheet's gap text has a word bank, the others and the test none (COURSE.md, "Craft notes"). A
table's cells and a transform's lines name their form (`lemma` + `form`) where the word is one the learner may have as a
card (dat.du, ins.pl, imp.2sg …), so a wrong one comes back first in that card's review.

**New grammar pages** (companion/grammar/sl/, B1, the book now 49 pages): `sklanjatev-posebnosti` (stems that grow:
oče – očeta, mlinar – mlinarja, pes – psa, dan – dneva, ime – imena, mati – matere; ljudje, otroci, sinovi),
`dvojina-skloni` (dveh, dvema; -ov, -oma, -ih; -ama, -ah; -ih, -ima for the adjective; naju, nama, vaju, ju, jima; pairs
in the plural), `vid-casi` (aspect in the past, the future and the imperative; don't is imperfective, a mishap
perfective; iti and hoditi), `dolocna-oblika` (zelen nož, zeleni nož; after ta, moj, svoj; names and kinds; after biti;
only -i; the spoken ta) and `odvisniki` (ker, zato, če, da bi, tako da, čeprav, ko, preden; a clause in front). `svoj`
got two examples (svoj never in the subject: Luka in njegov pes; vsak ima svoj kozarec). `oziralni-ki` and
`odvisni-govor` were there already (B1).

**What a unit turns on** (COURSE.md's table, the B1 rows): `dvojina-skloni` the dual of every case whose page is
introduced; `vid-vsi-casi` the imperative (velelnik's); `svoj` and `dolocna-oblika` an adjective's agreement; the others
no form of a table (the odd nouns' forms come with each case's page; naslonke, vezniki and the conditional aren't asked
as forms).

**Spoken forms.** None is graded wrong: *moj/tvoj* with I and you (Pozabil sem moj dežnik; pospravi tvojo sobo) is an
`almost` choice and the intro says it is heard; *a* for *ali* in a reported question is accepted beside ali and če; mama
and hčerka are named as what people say beside mati and hči; *ta zeleni* is named as the spoken definite. A clause in
front with the subject before the short words (Ker je zbolel, Luka je …) is `almost`, not wrong.

## Also changed

- **The blackboard's chalk writes "…"** (`BoardFont`): the unit `odvisni-govor`'s title is "Rekel je, da …", and every
  letter of a unit's title must be on the board (SchoolBoardTest). A2's "Rad bi …" and B1's "Če bi deževalo, bi …" need it
  too.
- **The printed sheet's "Pazi · Watch out" never shows an `almost` option as a mistake** (`companion/prints/sheet.ts`):
  a spoken form marked almost (tvojo sobo) would otherwise be struck through on paper.
- **QA's hook "school:<unit>"** (a debug build, `app/QaHooks.kt`): the dev village ready for the class as "school", that
  unit as never begun and its course level open, the school open (`readyUnit`, `AppViewModel.qaSchoolUnit`); QaHooksTest.
- **QA's step "school-b1"** (`companion/bin/qa`): the unit `oziralni-ki` end to end from the school's levels.
- The docs: COURSE.md (the B1 rows, the Pazi row), README.md and GAME.md and the studio's grammar-spec.md (49 pages, the
  new ones listed).

## How to use

The units are in the school's B1 level ("Raven B1"), which opens with the school's second level and A2 done (GAME.md,
"The school"). Their sheets print as any unit's (`companion/bin/school-prints --level B1`: every unit on one page or two;
the booklet 14 pages). QA, on a synthetic learner and without the second town (its default port is a learner's):

```
bun companion/bridge/test/fixtures/make-learner.ts /tmp/qa-learner --name Ana
flock /tmp/lani-emulator.lock env LANI_DEV_DATA=/tmp/qa-learner companion/bin/qa --steps school-b1 --no-town
```

## Tests

- **Content**: every unit and page validates (the bridge smoke's course section: the units, their pages, the outline;
  course-speech: what Mojca reads, never an English word in her voice); `school-prints --level B1` fits every unit on one
  page or two (four on one, four on two; the booklet 14 pages); the app's SchoolBoardTest writes every unit's title and
  forms in chalk, GrammarTest bundles the 49 pages.
- **Android unit tests**: the full suite after the rebase onto main (with the inline corrections), 3,151 tests, all green (before it, one timing test,
  SceneDetailTest's "a closer frame renders fast", missed its budget at a machine load of 40).
- **Bridge**: `tsc` clean; the smoke green but five checks of its service section (`lani-bridge run`, `status`,
  `restart`, "… and the session works on", "one service per learner"): its first run left a service on port 8820 that
  the later runs found taken.
- **Python**: `tests` (251) and `companion/tests` (98, 10 skipped) green.
- **Emulator QA** (`companion/bin/qa --steps school-b1 --no-town`, a synthetic learner): the unit `oziralni-ki` from
  "Raven B1" to its test passed and kept by the dev bridge's village, 14 screenshots.

## Voice

Mojca's Slovene of the eight units (`voice-build --only teacher`, a dry run on a synthetic learner's empty store, English
base): the course row is now 1,191 texts and 21,813 characters, of them the B1 units' about 414 texts and 9,700
characters; the explanations in English, apart (`--explanations`), 54 clips and 13,359 characters for the B1 units (163
and 47,537 for the course). Nothing was voiced.

## For the native check

Every unit's `review` names its doubtful spots; in short:

- `sklanjatev-posebnosti`: *Pastir je imel tri sinove* (the object after tri), *z dnevom*, *stojijo / stoje*, *ob ognju*;
  mati and hči against mama and hčerka.
- `dvojina-skloni`: *zato ju Mojca kliče nazaj*, *sedejo na dve klopi*, *dvema konjema*; how much of the dual's cases
  speech replaces with the plural's (the school asks the standard); *Umij si roke* against *roki* (almost).
- `vid-vsi-casi`: *je nekaj počilo*, *ne kriči*, *ne pozabi* against *ne pozabljaj* (almost), *ne jemlji*, *ne dajaj*; the
  genitive after a negative imperative (*Ne odpiraj okna!*: only the genitive is accepted).
- `svoj`: moj/tvoj with I and you in speech (almost); *Micka in njena vnukinja*, *v njeni kuhinji*, *vrne v njeno
  peresnico*.
- `dolocna-oblika`: *ta* as the spoken article (*ta zeleni*), *Daj mi sveži kruh*, *Moj leseni voz*, *Daj mi domači sir*.
- `oziralni-ki`: *iz katerega pijejo ovce*, the order after ki (*ki jo Luka išče* and *ki jo išče Luka*, both accepted),
  *Mojčini učenci*.
- `odvisni-govor`: *a* for ali (accepted), *Vida je rekla Nejcu, naj prinese drva*, the tense kept.
- `odvisniki`: *Ker je bil utrujen, je zgodaj šel spat* (*zgodaj je* almost), *tako da so lahko pribili deske*, *da bi
  gostje imeli kaj jesti*.
- The new pages' rules and tables, in all three bases.

## What's left

- **The hint chip** (`game/TurnHint.kt`) has no rule of its own for the five new pages: a scene's turn that names one gets
  the plain hint. No scene names them yet. (Done in case-lens-2: a rule of its own for each.)
- **The dictionary lacks some of the units' words** (hčerka, obe, zapirati, odpirati, kombi, pladenj, kmečki, novica,
  gradnja, izkušnja, test): the printed sheets leave them out of "Besede", and their cards have no meaning until the tutor
  glosses them. *dekle* (neuter) is feminine and *sejem* "I sow" in it, so they were left out of the words. (Done in
  case-lens-2: all eleven in the supplement, checked against SP2001 and Sloleks.)
- **da bi** is taught here as the aim (`odvisniki`); the conditional in full (`pogojnik-polno`, `course-b1b`) has *če bi …
  bi*. The two pages should see each other once both are merged. (Done in case-lens-2: odvisniki and pogojnik-ce-bi see
  each other.)
