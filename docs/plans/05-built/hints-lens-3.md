# Built: the hint chip and the lens for the new grammar (hints-lens-3, 2026-11-02)

Wave C, section 4 (the lead kit's followups-8.md): the hint chip (📖 Namig at a turn, `game/TurnHint.kt`) for the pages
it had no rule for, a case-trainer focus for B2's predlogi-dva-sklona, the long-press sentence sheet on the trigger
table, and the lens's year and spoken hour. The rules as built are in companion/README.md ("The case lens", "Case
trainer"), companion/SCENES.md ("The hint: the rule, never the answer", "The sentence's grammar") and
docs/grammar-syllabus.md ("The hint chip"). Everything Slovene here is machine-written (Claude) and waits for the native
check below. A spoken form is never wrong: the lens and the chip read, they never grade.

## Already done (checked on main 21aa4e9b, skipped)

- **The 5 B1 pages from B1a**: TurnHint has their rules since case-lens-2 (sklanjatev-posebnosti, dvojina-skloni,
  vid-casi, dolocna-oblika, odvisniki: `hint.ruleStems` … `hint.ruleClauses`, TurnHintTest).
- **The long-press sheet on the older trigger rules**: moved in case-lens-2. The sheet shows the lens's `case` of each
  word's lookup (`SentenceGrammar`, `SentenceController`), the lens reads case-triggers.json; the app's `WordReadings`
  and `Triggers` are gone (no reference left in the app). Without the bridge the sheet shows the words alone, as the card.
- **The year rule (leta 1844)**: in the lens since case-lens-2 (the table's `time_genitive`: leto before a year in
  figures, nekega dne). This round adds it to the chip (below).
- **The Italian B1 pages**: the chip gives a turn in another language than Slovene its pages' links alone, at every
  level (`TurnHints.of`); it has no Italian rules to extend. Left (What's left).

## How to use it

- **The hint chip** at a turn of any page of the Slovene book (A1 to B2) says that page's rule in words with models in
  other words, never the turn's form. On B2's two cases' page a preposition gives both its cases with their questions and
  the rule beside them ("po kruh · po vasi": Koga? Kaj? the accusative, Pri kom? Pri čem? the locative). A year or a told
  day gives the genitive of time ("with «1890»", "with «Nekega»": Kdaj? → 2. rodilnik), pol before an hour the genitive
  after it ("after «pol»": Kdaj? → 2. rodilnik, ob pol dveh).
- **A word's card and the sentence's grammar**: an hour said as counted (*Maša je ob deset.*) has its row, the locative
  ob asks for, and says it beside: "Tako se tudi reče; piše se «desetih». · Said like this too; written «desetih»." *ob
  pol dveh*: "Kdaj? «pol» in naslednja ura: rodilnik (ob pol dveh: ob 1.30)." *Čez pol ure*: after the word of quantity
  «pol», the genitive.
- **The case trainer** on the prepositions with two cases: "🧩 Vadi sklone · Case trainer" on the predlogi-dva-sklona
  page, and the unit's "🧩 Skloni". The tutor: `assign_cases {focus: "predlogi-dva-sklona"}`.

**Tap counts.** The hint on a turn of a B2 page: before, the chip showed only "📖 V knjigo" (the chip, then the link to
read the rule, 2 taps and a Back); now the chip says it (1). The case trainer on the two cases: before, no session on
them (a mixed one, or kje-kam from the A1 unit); now 1 tap from the page or the unit. The sentence's grammar: 1 long
press, unchanged; *ob deset* before showed the word alone, now its row.

## What was built

1. **The chip's rules for every page** (`game/TurnHint.kt`, `game/HintSpeech.kt`): 28 pages had none: A2's cigav and
   mnozina-skloni; B1's cas-izrazi, custva, stevila-glagol, pogojnik-ce-bi, naslonke-red, glagolniki, nedolocni-zaimki,
   trpnik; B2's 18 (stopnjevanje, predpreteklik, odvisniki-napredno, frazemi, stevniki, delezja, pomanjsevalnice, ukazi,
   delezniki, izpeljanke, predpone, predlogi-dva-sklona, govorjeni-jezik, uradno-pismo, pogojnik-pretekli, brezosebno,
   oziralni-kateri, besedni-red). Each has its rule in words from the page's own card (`hint.rule*`), a sentence for the
   🔊 (`hintSay.rule*`), models in other words, a question where the page asks one (Kdaj?, Kako?, Čigav?, Kakšen?,
   Kaj delam? Kaj naredim?). Where the sentence tells the grammar, the trigger too: cas-izrazi, custva (a word of feeling:
   boli me, mudi se mi), mnozina-skloni (an amount) and predlogi-dva-sklona (its preposition, both cases, the rule beside
   them) are case pages. The govorjeni-jezik rule says spoken and written are both right in their place.
2. **The chip reads two more parts of the table** (`game/CaseTriggers.kt`): `time_genitive` (`Trigger.When`: a year
   noun's genitive before a year in figures, the year its trigger; a word after nekega, tistega …; not after a
   preposition, od leta 2024 stays its), and the new `hours.half` (pol: on the time page, Kdaj? the genitive; elsewhere
   an amount, Koliko?). The time page takes the genitive too (ob pol dveh), its rule says so ("ob pol + the next hour in
   the genitive").
3. **The lens's hour** (`bridge/src/case-lens.ts`, the table's new optional `hours`): an hour after ob, or after ob pol,
   said as it is counted (ob deset, ob štiri, ob pol sedem) gets the case ob (the locative) or pol (the genitive) asks
   for, decided, the number of its written form (desetih: plural; dveh: dual), and `written` (GET /lookup's `case`
   carries it: the app's `CaseRead.written`). Never an hour after another preposition (*štej do deset* counts), nor a
   number that counts a noun after it. Before, *ob deset* had no row and *ob štiri*, *ob dve*, *ob ena* were the
   accusative of motion, decided: wrong.
4. **pol, and a quantity before a preposition's noun**: pol joins the words of quantity (SP2001 pôl2 *mer. prisl.*;
   pôl1 "Kosilo bo ob ~ ene, ob ~ enih; (Ura) je ~ osmih": the hour after it in the genitive). A word of quantity or a
   number right before the noun now decides its genitive before a preposition further back (*čez pol ure*, was an
   accusative plural; *za pet minut*, was unresolved; *z malo sladkorja*, was z's "from"). pol before an hour is the
   time (kdaj-cas, sense time: Kdaj?).
5. **The case trainer's focus on predlogi-dva-sklona** (`data/CaseTrainer.kt` `TWO_CASES`, `CaseLook.PAGES`,
   `CASE_UNITS`): the page's own rules and the bank's of a preposition with two cases (`TWO_CASE_RULES`: v-na-kam,
   pod-za-kam, v-na-kje, pod-za-kje, ob, po, za-kaj), Kje ali kam? 45 %, and at least half of its "Kateri sklon?" the
   page's own where they can be asked (a learner of B1 who met the page, or of B2; a child's or an A2 learner's session
   has the kje and kam alone). `CaseTask.why` carries an item's rule key. The bank (`cases/sl/bank.json`): four rules on the page (po-kaj: po + the accusative to fetch; ob-kaj: ob + the accusative
   against; za-koliko: za + the accusative how long; pred-med-kdaj: pred, med + the instrumental of time) and 14 B2
   items, two of them SP2001's own uses of ob ("prisloniti lestev ~ zid", "spotakniti se ~ kamen"). The bank's levels
   take B2 (an item may not sit below its page's level); `publish_case_items` and `assign_cases` say so.
6. **Agreeing**: the hint fixture (`bridge/test/fixtures/hint-lens.json`) 24 → 32 turns (the hour, pol, the genitive
   of time, the two cases' page, feelings); `TurnHintLensTest` checks the chip names the lens's trigger, case and
   question on each, `bun test/hint-lens.ts` and the smoke that the lens still answers them so. The sheet reads the lens.
7. **QA's step `hints-lens`** (only when named) and the debug hook `cases:meet:<ids>`.
8. **The page's title**: predlogi-dva-sklona's title and card said "predlogi z dvema skloma"; sklon, sklona (SP2001
   sklòn sklôna), so *sklonoma*. Titles aren't among the voiced lines: no clip changes.

## Choices, and why

- **The lens names the written form beside a spoken one, it never corrects it.** The scenes themselves teach "ob
  desetih … Tako pišemo, ob deset pa tudi rečemo"; the card says the same. The case shown is the one ob asks for: the
  number stays as counted in speech, the case is still ob's.
- **pol takes the genitive, not ob's locative.** The book's kdaj-cas page lists "ob pol šestih" beside "ob + the
  locative", but SP2001's *ob pol ene* (ene can only be the genitive) and *Ura je pol osmih* (no preposition) say the
  hour after pol is pol's genitive. The genitive and the locative of these hours are one form (desetih, dveh), so
  nothing a learner writes changes.
- **A quantity right before the noun first**: *čez pol ure* is čez + (pol ure), pol's genitive; a preposition two words
  back only decides when the noun's form doesn't fit the quantity (*z veliko ljudmi*: the instrumental, by z).
- **Tried and left**: biti as an auxiliary counts as a verb of rest for a preposition of two cases (*Sosed nam je
  posodil voz za eno uro* reads the instrumental). Counting biti only as a copula fixed it but made *zaspal za mizo* a
  wrong likely accusative and three "za + a person" lines Kam?; za + a noun of time as a time took *za oba* and *drug za
  drugega* for times. Both reverted; the trainer's sentence is in the present (*Sosed nam posodi voz za eno uro.*).
  What's left.
- **A guarantee for the page's own items, not a weight.** The first version let them compete by weight: QA's first run
  met none in four tasks, and the count says why: 14 of them against 78 items of A1 to B1, so 44 % (a B2 learner) to
  60 % (a B1 learner) of the sessions opened from the page would have asked nothing it teaches. A weight would only
  have made that rarer.
- **The chip's rules in words, not a field of the pages**: as before, a small table in the code (the syllabus's
  "hint" field per page is still a suggestion); every page of the book has a rule now, `TurnHintTest` checks each.

## Accuracy

`bun test/case-lens-eval.ts <set>`; before: main 21aa4e9b; after: this branch. "Wrong and decided" is a wrong case said
as sure.

| Set | Items | Before | After | Wrong and decided |
|---|---|---|---|---|
| labelled (built on) | 291 | 284/285 | 284/285 | 0 → 0 |
| held-out 1 | 136 | 135/136 | 135/136 | 0 → 0 |
| held-out 2 | 104 | 102/104 | 102/104 | 0 → 0 |
| held-out 3 | 89 | 82/87 | 82/87 | 0 → 0 |
| held-out 4 | 64 | 62/64 | 62/64 | 0 → 0 |
| the case trainer's bank | 324 → 338 | 322/324 | 336/338 | 0 → 0 |

**Over the content** (6,522 lines with the course units'): 26 answers changed, each read, all right: the spoken hours
get the locative (ob štiri twice was the accusative of motion, decided), *ob dvanajst*, *ob deset*, *ob šest*, *ob
sedem*, *ob osem* (11 lines) a row they had none of; pol decides (*pol ure* twice, *pol kozarca*, *pol metra*, *pol
dela*, *pol poti*, *pol stekleničice*; *To je pol ure čez eno* was a nominative, likely); *ob pol dveh*, *ob pol enih*,
*pol štirih* the genitive by pol, the time; *Čez dvajset minut* resolves by the number; *Za koliko ljudi* takes koliko's
genitive. The curated scenes' chip: 1,064 → 1,066 turns with a hint, 459 of 479 case turns with their trigger (as
before).

## The sources

fran.si, one request at a time: SP2001 *ob* (https://fran.si/iskanje?View=1&Query=ob&FilteredDictionaryIds=134: "z
mest. … časovni: zbuditi se ~ treh"; "s tož.: prisloniti lestev ~ zid; spotakniti se ~ kamen"; "neknj. ljud.: Krava je
prišla ~ mleko"), SP2001 *pol* (https://fran.si/iskanje?View=1&Query=pol&FilteredDictionaryIds=134: pôl1 "Kosilo bo ob
~ ene, ob ~ enih; (Ura) je ~ osmih, redk. ~ osme; pet minut do ~ desetih"; pôl2 *mer. prisl.*), SSKJ² *ob*
(https://fran.si/iskanje?View=1&Query=ob&FilteredDictionaryIds=133: "srečujem ga ob vsaki uri, tudi ob dveh ponoči",
"ob treh"), SP2001 *sklon* (https://fran.si/iskanje?View=1&Query=sklon&FilteredDictionaryIds=134: "sklòn sklôna").
Neither dictionary lists the undeclined spoken hour; the content's own lines say it. Sloleks's headword page for *sklon*
answered 500 (one request, not repeated): *sklonoma* is the dictionary's own reading of sklon (dative and instrumental
dual), *skloma* none.

## What's left

- **Italian**: the chip has no Italian rules at any level (an Italian turn gets its pages' links); B1 Italian would want
  a table of its own, as Slovene's.
- **biti as an auxiliary is a verb of rest** to a preposition of two cases (*je posodil voz za eno uro* the
  instrumental, decided): needs what the noun after za is (a time, a person) rather than the verb.
- **The hour without ob** (*Ura je pol osem*, spoken) and in figures (*ob 10*, *ob 7.30*) have no row; none is in the
  content.
- **The chip without a dictionary** can't tell an hour's number (osmih): on the time page pol says Kdaj?, elsewhere
  Koliko?. It names where to and where by the page's order on the two cases' page (po kruh and po vasi both list the
  accusative first).
- **Domov on the sentence's grammar** (seen in QA, not this round's): *Domov gremo* shows the dictionary's first reading,
  "dom, genitive dual/plural", for what is an adverb there; no case row, so it isn't the lens's. A lookup's matter.
- **No curated scene names a B2 page yet**: the new rules show for the tutor's scenarios now and B2's scenes later.
- **Three curated turns grade the spoken hour as wrong**, against six that accept it ("Tako pišemo, ob deset pa tudi
  rečemo": pod-uro, v-cerkvi, v-hisi, na-vasi, v-podstresju, primorska's v-gorah): kuhinja «Kdaj so poročila? Ob pet?»
  (its reply "Ob pet? Pet česa?"), ob-potoku «Kdaj greva? Ob osem?» ("Moja ura tega ne pozna."), v-soli «Ob deset
  zvečer.» (its English "wrong form"). A spoken form is never wrong: each wants the choice accepted with a reply that
  gives the written form, as the others do, and ob-potoku another wrong choice. Not changed here: new replies are new
  lines to voice (ElevenLabs credits, Jan's call), and content is content-fixes' ground.
- **Deploy order**: the bridge code (fe4d6ccd) before the content (84dca0c5, 44270797): the table's `hours` and the
  bank's B2 items need its schema; restart; the app (02ebf0df, 0fd95ae7) reads the table and the bank it bundles.

## Native check

Machine-written (Claude, 2026-11-02): the strings `hint.rule{TimeNoPrep, Feelings, PluralCases, Whose, NumberVerb,
IfWould, CliticOrder, VerbalNoun, Indefinite, Passive, CompareFull, Pluperfect, ClausesMore, Idioms, NumberWords,
Gerund, Diminutive, Commands, Participle, WordFamily, Prefix, TwoCases, Spoken, FormalLetter, PastConditional,
Impersonal, WhichWhose, WordOrder}`, their `hintSay.*`, `hint.ruleWhen`'s ob pol, `caseLens.whyHalf`,
`caseLens.whySpoken` in sl, en, de, it (`_note_hints_lens_3`); the chip's models; the bank's four rules' lines and its
14 B2 items with their translations; the fixture's eight turns (test data); the table's hours examples.

**For Jan's wife, the short list** (a spoken form is never wrong; each says which is standard and which spoken):

1. **The hour as counted**: *ob deset, ob štiri, ob šest, ob pol sedem* (spoken; written *ob desetih, ob štirih, ob
   šestih, ob pol sedmih*). Is this how you say them? The card says "Tako se tudi reče; piše se «desetih»." Natural?
2. **ob pol ene or ob pol enih** (SP2001 has both): which do you say? And *ob ena* or *ob enih*?
3. **The card's line for pol**: "Kdaj? «pol» in naslednja ura: rodilnik (ob pol dveh: ob 1.30)." Is *rodilnik* how a
   teacher would name *dveh* here?
4. **The trainer's 14 sentences** (cases/sl/bank.json, ids po-kruh, po-vodo, po-zdravnika, po-drva, ob-mizo, ob-zid,
   ob-kamen, za-en-teden, za-eno-uro, pred-tednom, pred-tremi-leti, pred-kosilom, med-kosilom, med-maso): natural?
   *Muri se je spotaknil ob kamen*; *Luka je lestev prislonil ob zid*; *Jutri gremo z vozom po drva v gozd*.
5. **The chip's models**, the doubtful ones: *Dve kravi sta pili.*, *Pet dreves je padlo.*, *moram delat · moram
   delati*, *Bila je že odšla.*, *Spoštovani gospod župan,*, *Kdor išče, najde.*
6. **The 28 rules in Slovene** (sl.json `hint.rule*`, `hintSay.rule*`): a teacher's words?
7. **Found, not changed** (content): predlogi-dva-sklona's table has *ob mleko* (ob kaj?); SP2001 labels *priti ob
   mleko* "neknj. ljud.", folk speech. Keep it as the spoken use, or give the row a standard one?

## Tests

All on the branch with main merged (d4d8cfcf, main at bde83009), ports 9710–9729. No line anyone hears was added or
changed (the new strings are the card's and the 🔊's, said by the phone's voice or live): a voice build needs 0 characters.

- Bridge: `bunx tsc --noEmit -p .` clean; the smoke 3,475 checks, all passed (LANI_SMOKE_PORT 9710, LANI_SMOKE_DRAMA3_PORT
  9728), with 15 lines of this round's rules, the written forms, every table example (100), the bank's two-case items,
  assign_cases taking the page; `tutor-export --check` 89 files match; `bun test/hint-lens.ts` 32 turns as recorded;
  `bun build --no-bundle` on the touched smoke files. Its first run failed one check, this round's own (an example's
  trigger written "ob" where the line has "Ob"): 44270797.
- Python: `case-triggers.py --check` matches; `python3 -m unittest discover -s tests` 251 OK, `-s companion/tests` 142 OK
  (11 skipped).
- App: the full unit suite through lani-slot.sh, 3,764 tests in 483 classes, 2 skipped, 0 failures. TurnHintTest (16:
  the 28 pages' rules, nothing shown the turn's form; the triggers as the lens names them), TurnHintLensTest (the
  fixture's 32 turns), HintSpeechTest (11: each new rule's sentence in every table), SentenceGrammarTest (6: the spoken
  hour and pol), CaseTriggersTest (3), CaseTrainerTest (46: the focus over 40 sessions each for a B2 learner and a B1
  learner who met the page, at least half of "Kateri sklon?" its own), L10nTablesTest (6).
- Emulator QA: `companion/bin/qa-slot --slot 2 --no-town --steps hints-lens`, a synthetic learner at B1
  (make-learner.ts, its profile's level raised; LANI_DEV_DATA), on the tree before main's lexicon commits (6bfafd19:
  they change no screen here). Slot 2 because slot 1's dev bridge binds 8791 and 8797. Ok, no crash. Shots in the
  session's scratchpad, ux-hints-lens-3/final/ (the first run's, before the focus's fix, in ux-hints-lens-3/):

  | Shot | What it shows |
  |---|---|
  | 11, 12 hints-lens-deset | «Maša je ob deset.»: deset "mestnik, mn.", "Kdaj? → 5. mestnik", "Po predlogu «ob»: mestnik. (kdaj? čas) Tako se tudi reče; piše se «desetih».", ob "Zahteva mestnik: «deset»", 📖 the time page |
  | 13–15 hints-lens-pol | «Domov gremo ob pol dveh.»: pol "Zahteva rodilnik: «dveh»", dveh "rodilnik, dv.", "Kdaj? → 2. rodilnik", "«pol» in naslednja ura" |
  | 16 hints-lens-card-ure | ure's card in «Čez pol ure pridem.»: "rodilnik, ed.", "Po besedi količine «pol»: rodilnik.", 📖 the quantity page |
  | 17 hints-lens-page | the page, its title *sklonoma*, its "🧩 Vadi sklone · Case trainer" |
  | 18–22 hints-lens-task-* | the focused session: v gorah, za šotor, na lipo, v sneg, then ob zid, the page's own |
  | 23 hints-lens-own-right | *Luka je lestev prislonil ob zid.*: "ob → 4. tožilnik", the new rule's line, 📖 to the page |

  **Not seen on the emulator: the hint chip's new rules.** No curated scene names these pages, so no turn on the device
  shows them; they are checked by the unit tests above. The first QA run met none of the page's own items in four tasks,
  which is what led to the guarantee (Choices).
