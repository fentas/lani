# Built: the case lens (tap a word, see its case and why)

Jan, 2026-10-29: the Slovene cases are what trips them most. Their mistakes database: the genitive after iz, brez and a
negation; the accusative feminine -a → -o; kam vs kje after v and na; adjective agreement. More of it is knowing what
triggers a case than the endings themselves. The lens turns every sentence the app shows into that practice: tap a
noun, a pronoun or an adjective anywhere and see its case and number, and the word that decided it.

The rules as built are in companion/README.md ("The case lens", under "Word lookup"), SCENES.md (the dialog panel's ⋯)
and the tutor's guide (the word lookup's paragraph). This note says what was built, why each choice, how to use it, how
accurate it is, what it can't decide and what is left. The case trainer (branch case-trainer) is built alongside; the
shared contract below is kept exactly.

## How to use it

- **A word's case**: tap the word in a dialog, a talk, a story, a reading, the chat, a grammar page's example (anywhere a
  word opens its card). The card's line shows the word in its case's colour and the word that decided it underlined
  («Kavo pijem brez mleka.»); under it the row "🧩 Sklon · Case": "rodilnik, ed. · genitive, sg.", "koga? česa?", "Po
  predlogu «brez»: rodilnik. · After the preposition «brez»: the genitive.", and "📖 Iz, od, do, blizu: rodilnik ›" (the
  rule's page). An adjective says its gender and the noun it agrees with ("🔗 Ujema se s «smreko»"). A form that could
  be something else says so ("Ta oblika je lahko tudi: im. mn., tož. mn."). A likely case comes first, marked "verjetno
  · likely", with the readings it leaves; a word the line can't decide lists its readings ("Ne vem zagotovo"), none
  preferred. Tapping the trigger itself (brez) says what it decides ("Zahteva rodilnik: «mleka»").
- **Colours**: "🎨 Skloni · Cases", a switch under "👁 Prevod" in a reading, and the dialog panel's new ⋯ (every dialog,
  story and talk), underlines each word in its case's colour, dotted where the line can't tell, with a legend (1.
  imenovalnik … 6. orodnik). Off by default, remembered.
- **Tap counts**: before, a word's case was nowhere on its card (the grammar string "dative/locative singular" at most,
  and the sentence's grammar sheet's own guess after a long press and a scroll); now one tap shows it, with what decided
  it. The colours: two taps in a dialog (⋯, Skloni), one in a reading; once on, every later dialog and reading has them.

## What was built

1. **The trigger table**, data not code: `companion/grammar/sl/data/case-triggers.json` (`lani.case-triggers/v0`,
   schema `bridge/src/case-triggers.ts`). The six cases with question words and colours; 21 preposition groups by sense
   (brez, iz, od, do, blizu/zraven/poleg, mimo, zaradi/namesto/razen, okoli/okrog/sredi, izpod/izza: the genitive; k/h,
   proti/kljub: the dative; v, na: where to (accusative) or where (locative); ob, pri, o, po; z/s: with (instrumental)
   or from (genitive); pod/nad/pred/med and za: where (instrumental) or where to (accusative), za for; skozi/čez/zoper);
   the negations (ni, nimam, nočem …, ne before a verb, the contrast ampak); the words of quantity, 29 nouns of measure
   and the numbers from five (figures too: 5–20, 25 …, never 101–104); 66 governing verbs (bati se, veseliti se,
   spomniti se … + genitive; pomagati, verjeti … + dative; dati, prinesti, povedati … + dative and accusative; zebsti,
   boleti, zanimati … + the accusative of who feels it); 16 words of feeling (všeč, mrzlo …: the dative; strah, sram: the
   accusative); verbs of motion (49) and of rest (20), copulas, verbs with an object (123) and without (58); 26 nouns
   of time; 18 fixed phrases; the predicative rad; 18 personal pronouns with all six cases and 34 fused ones (zame, nanj,
   vanjo). Every entry names its rule (a grammar page), 84 examples, each analysed by the smoke.
2. **The analysis**, `bridge/src/case-lens.ts`, pure, in the bridge, so the phone's own bridge (QuickJS) runs it too:
   a sentence and a word's place in it (its `n`th of the same words) → every reading the form can have, the one the line
   picks (or none), how sure, what decided it and where, the rule, the readings left and those ruled out, an adjective's
   noun. Details in the README.
3. **The lens in the lookup**: GET /lookup's `case` (one request, offline on the phone), the card's row and line
   (`ui/words/CaseRowView.kt`, `data/CaseLens.kt`; the cases' colours, question words and names are the case trainer's
   shared look since the merge: `data/CaseLook.kt`, `ui/cases/CaseColors.kt`'s `Case.color()` and `CaseNames`).
4. **Colours on demand**: POST /case-lens (60 lines a request), `app/CaseColoursController.kt` (gathers a screen's lines a
   moment, asks once, keeps 800), `WordText`'s `cases` marks, `data/CasePrefs.kt`.
5. **Feedback into practice**: a lookup of a word whose case its line decided says `seen: true` (the app's
   `CaseRead.seen`; once a day per word of a line, the bridge keeping only today's looks in memory), and
   `WordsController` hands its rule to the case trainer (`vm.caseTrainer.seen(rule)`), whose count in the village state
   (`GameState.cases.seen`: how often, the last day) is the only one: its weighting reads it, `get_village` shows it
   (`cases.seen_in_lens`). A "met" signal to weigh lightly, never an answer. (Before the merge the bridge kept a second
   count, `GET /case-lens/seen` and `<data>/app/case-lens-seen.json`; gone, so two counts can't disagree.)
6. **The tutor guide** says the lens exists and that a look counts as seen for the trainer (the lexicon feature's
   paragraph, `tutor-export` regenerated).

## Choices, and why

- **The table's place**: `grammar/sl/data/case-triggers.json`, one directory under the place the brief named. Every
  reader of `grammar/<lang>/*.json` takes each file there for a page of the book (the bridge's GrammarStore, the app's
  bundled index in app/build.gradle.kts, the smoke's page checks, the conformance corpus, forms-cards' and course's
  checks): a file that isn't a page there would be skipped with a warning at best and break the book's index at worst.
  A subdirectory is read by none of them and still ships to the phone (`companion/grammar/**/*.json`).
- **The shared contract** (ids, order, question words, colours, rule ids) is the table's `cases` and the app's
  `CaseLook`, checked by the smoke against the exact values. The trainer owns the app's case look; whoever rebases
  second switches to the other's definition (the app's `CaseLook` has the contract's values, nothing else).
- **Rule ids are grammar pages**: the shared ones (rodilnik-predlogi, rodilnik-nikalnica, rodilnik-kolicina,
  kam-tozilnik, kje-mestnik-orodnik, dajalnik, orodnik, tozilnik, mestnik, imenovalnik) plus the pages that explain a
  rule more exactly: `rodilnik` (a verb's genitive, a noun's genitive, some of it), `kdaj-cas` (v sredo, ob petih, pred
  eno uro, čez eno uro, ves dan), `custva` (mrzlo mi je, všeč, zebe me, boli me), `skloni` for a word left open.
- **o takes the locative only**: the brief listed it with v and na; in today's Slovene o + accusative is archaic, and
  ob + accusative (ob zid) is in the table as where to. po + accusative is to fetch (grem po kruh), never motion: po
  vasi with tekati is the locative.
- **Never a silent guess**: one reading left is "decided"; a preference the table or the order gives is "likely" and
  shows the others (the singular of a form that reads alike in the plural, vasi; a preposition's usual sense when no verb
  tells, za kavo, pod mizo; the subject before its verb, the object after it; a subject that agrees, over a dative by
  its form alone); anything else is "ambiguous", no pick, every reading listed. A word the line fits none of the
  dictionary's readings is "unresolved": the card shows no row rather than a wrong one.
- **Hard rules the cards can say**: the dual only with dva, dve, oba or a dual verb (out: "dual"); the locative and the
  instrumental only after a preposition; the genitive only with something that asks for it (a preposition, a negation, a
  quantity, a verb, a noun before it), or when the form can be nothing else (Daj mi kruha); a subject agrees with its
  verb in person, number and an l-form's gender; a transitive clause has one subject and one object.
- **Readings it makes up are marked**: a noun or an adjective the dictionary has in another form (šotor, šotoru: šotorom)
  is read by its regular endings (`derived`, "〰 po pravilnih končnicah"), never its genitive plural (often a vowel put
  in: sester) nor a masculine accusative singular (animate or not). The village's names are declined as the learner's
  name is (addressee.ts sloveneName). A word it doesn't know at all has no row.
- **The colours** draw only what's decided in a colour, the rest dotted (likely included: a colour says "sure"). Never on
  a wrong choice's line (its form is the mistake). Off by default: they are a lens to look through, not the text.
  Remembered on the phone (one learner per install, as the dialog's translations are).
- **Not while an exercise is open**: its row could give the answer away (the same rule as the word's forms table).
- **Slovene only, at home**: the bridge's FORMS_LANGUAGES; a visit's word or an Italian village's has no lens (Teo's
  app shows neither the row nor the switch).
- **Fast**: the analysis takes 0.4–0.7 ms a line on the node (all 4 231 content lines in 1.5–3 s, as busy as the
  machine is), 85 ms on the JVM's QuickJS for a lookup and two lines with the lens built; the app draws only marks with the text it lays out anyway.
- **Apart from the case trainer**: the trainer (built alongside) has `bridge/src/case-bank.ts`,
  `test/smoke/case-trainer.ts`, `GET /cases`, `POST /cases/done` and the view model's `caseTrainer`. The lens's are
  `bridge/src/case-lens.ts`, `test/smoke/case-lens.ts`, `POST /case-lens` and `vm.caseColours`: no add/add conflict, no
  path serving two features, no property declared twice.
- **Gender only where it agrees**: an adjective's, a determiner's, a pronoun's or a number's gender is on the case line
  ("orodnik, ed., ž. · instrumental, sg., f."); a noun's is on its entry already (mleko · s.).
- **Ambiguous means no preference**: the readings of an ambiguous word are listed in the cases' order, none marked; only
  a likely pick comes first and says "verjetno".
- **Accessibility**: the row is one TalkBack node: the case and number written out in both languages, the questions,
  what decided it, the likely one's alternatives.

## Accuracy

Three hand-labelled sets, `bridge/test/fixtures/case-lens-*.json`, and the case trainer's bank (its gold annotations,
written apart from the lens), run by `bun test/case-lens-eval.ts <gold|heldout|heldout2|trainer> -v` and the smoke.
"Right": the pick is the labelled case and number (or one the label allows); "wrong and decided": a wrong case said as
sure, the one that must not happen; "open": no pick (the dictionary lacks the word, or the line leaves it open).

| Set | Items | Right | Wrong and decided | Wrong as likely | Open | Triggers |
|---|---|---|---|---|---|---|
| labelled (built on; 264 lines of the content, 27 written; every rule, 6 ambiguities) | 291 | 279/285, 97.9% | 0 | 0 | 6 | 262/262 |
| held-out 1, its first run (before any fix it led to) | 136 | 115/136, 84.6% | 0 | 1 | 20 | 104/108 |
| held-out 1, now | 136 | 128/136, 94.1% | 0 | 0 | 8 | 119/120 |
| held-out 2, its first run (drawn after those fixes) | 104 | 93/104, 89.4% | 1 | 1 | 9 | 82/86 |
| held-out 2, now | 104 | 98/104, 94.2% | 0 | 1 | 5 | 87/90 |
| the case trainer's bank, its first run (324 "which case?" items) | 324 | 304/324, 93.8% | 0 | 0 | 20 | 238/239 |
| the case trainer's bank, now | 324 | 315/324, 97.2% | 0 | 0 | 9 | 250/250 |

The numbers for lines it had never seen are the first runs: **89.4% and 93.8% right, 1 of 428 wrong and decided** (Ti
drži en konec: drži read as a present, not the imperative; fixed). Every labelled ambiguity is kept open (6/6); the
rules (the grammar pages) are right wherever the case is (267/267 on the labelled set). Of the content's 11 081 words
with a case, 90% are decided or likely. The labels are machine-written (Claude), from the grammar book: they want the
same native check as the table.

## What it can't decide (and says so)

- A word the dictionary doesn't know (Krpan, Martinu, Zlatoroga, pratika, krizanteme, drvmi; Zdravljica's kitica,
  sedma): no row.
- A noun form that is a table verb's too, where nothing governs it: leta in "napisal leta 1844" (the year's genitive) is
  read as letati's, so no row. Preferring the noun beside another verb was tried and dropped: it made "leta 1844" a
  decided accusative object, the one kind of mistake the lens must not make. A year rule (leta + a year) is what's left.
- Poetry's old forms: Prešeren's "Žive naj vsi narodi" (žive: živijo, a verb) reads as the adjective živ, left
  ambiguous (no pick); "dočakat' dan" leaves dan open.
- A list or an answer without a verb: "Kamni, les, kolo …" (les: what is there, or what is needed), "Pa kruh?".
- Where it is or where it goes without a verb that says: "Pod mizo!", "Za mizo." (likely where, both shown).
- The same case in two numbers: vasi (genitive singular or plural), likely the singular.
- The subject and the object both of the same form with a verb that doesn't tell (Dekle vidi dekle): likely by order.
- A noun read as the dictionary's homograph the line doesn't mean, where nothing tells them apart (sledi medveda: the
  dictionary knows sledi only as slediti's present: medveda stays genitive or accusative, both shown).

## Native check (machine-written by Claude, 2026-10-29/30)

The trigger table (its lists, senses, the verbs' government, the phrases, the examples), the three labelled sets, and
the strings (`caseLens.*` in sl, en, de, it, `_note_case_lens`). Spoken forms are never wrong, only not listed.

## Tests

- Smoke (`bridge/test/smoke/case-lens.ts`): the table's schema, the shared contract, refusals of broken tables, the
  pronouns against osebni-zaimki, the verbs' forms, all 84 examples, the three labelled sets with their thresholds (and
  the trainer's bank once it is merged), every
  content line (no throw, in time), GET /lookup's case (decided, `n`, likely, a trigger, a verb, no line, a visit, the
  `seen` flag: counted once a day, never an open case, nothing kept on the node), POST /case-lens (and its 400s).
- App: `CaseLensTest` (the row's states: decided, likely with its others, ambiguous, governs, an adjective's agreement, none
  from an older bridge, an unresolved one; the tapped word's `n`; the colours' marks; the names), `PhoneBridgeTest
  .theCaseLens` (the lens in QuickJS), `L10nTablesTest` (the strings).
- Emulator QA: `companion/bin/qa --steps case-lens` (below).
- The checks at the end (2026-10-30, on main b5990820): `bunx tsc --noEmit -p .` clean; the smoke 2 956 checks passed;
  `tutor-export --check` 82 files match; the Android unit suite 3 355 tests, all passing but SceneDetailTest's "a closer
  frame renders fast" (27 ms with the machine at load 32; it passes alone, 4/4: a timing test, nothing of the lens);
  Python 251 and 102 tests OK (11 skipped).

## Emulator QA

`companion/bin/qa --no-town --steps case-lens` (a synthetic learner, inside the emulator's lock, about 3 minutes):

| Shot | What it shows |
|---|---|
| lens-brez-mleka | «Kavo pijem brez mleka.»: mleka in red, brez underlined; "rodilnik, ed. · genitive, sg.", koga? česa?, "Po predlogu «brez»: rodilnik.", 📖 Iz, od, do, blizu: rodilnik › |
| lens-pod-mizo-likely | «Pod mizo!»: "orodnik, ed." marked "verjetno · likely" (where it is: kje?), the accusative (where to) kept below it |
| lens-les-ambiguous | «Kamni, les, kolo»: "Ne vem zagotovo", imenovalnik and tožilnik, none preferred |
| lens-veliko-agrees | «… pod veliko smreko»: "orodnik, ed., ž.", 🔗 Ujema se s «smreko», tožilnik ruled out |
| lens-brez-decides | brez tapped: "Zahteva rodilnik: «mleka»" |
| lens-dialog-card | Babica's soup line, Dober: the nominative, the fixed phrase dober večer |
| lens-dialog-menu-on, lens-dialog-colours | the dialog header's ⋯ → "🎨 Skloni · Cases" with the legend; the person's line underlined by case |
| lens-reading-colours, lens-reading-card, lens-reading-off | Zdravljica (the corner's page every learner has) with the colours on since the dialog, Žive's card (ambiguous), the switch off again |

What the QA run found and this branch fixed: a noun's gender trailing the case line in the base language only, the
first reading of an ambiguous word marked "likely", koder (wherever) coloured as a second subject, and the step's own
outcome. The shots of the last run are in the session's scratchpad (ux-lens/).

Before and after: a word's case took a long press on its line and a scroll through the sentence's grammar (when that
sheet had it at all); now it is the one tap that already opened the word's card.

## What's left

- **The case trainer's merge** is done (the trainer rebased second; docs/plans/05-built/case-trainer.md, "The contract
  with the case lens"): the smoke runs the lens over the trainer's bank (324 "which case?" items, 97.2%, never
  contradicting a gold annotation: no wrong case decided or likely, every deciding word the bank's); one case look (the
  app's `game.Case` with the trainer's `data/CaseLook.kt` extensions, `Case.color()`, `CaseNames`; this branch's
  `ui/words/CaseLook.kt` is gone); the seen signal into the trainer's count, the bridge's own count removed; the bridge's
  case ids from `case-triggers.ts` (`CASE_KEYS`, `CASE_IDS`) in the trainer's bank module too.
- **The sentence's grammar sheet** (a long press on a line, `game/SentenceGrammar.kt`) still has the app's own trigger
  heuristics: it could read each word's `case` from the lookups it already makes, one analysis everywhere.
- **Coverage**: the dictionary's gaps (a story character's name, a word the content uses that Wiktionary lacks, some
  irregular plurals: drvmi). The supplement (`lexicon/sl.extra.json`) is where to add them.
- **Colours elsewhere**: readings and dialogs (and stories, talks: the dialog panel) have them; the chat, the grammar
  pages' examples and the school's lines have the card's row, not the switch.
- **The verbs' government** grows with the content: a verb the table lacks still works by its person and number, but
  its object and its motion aren't known.
