# Built: the case lens, second round (case-lens-2, 2026-10-30)

The follow-ups of the case lens (docs/plans/05-built/case-lens.md, "What's left") and of B1a (course-b1a.md, "What's
left"): the genitive of time (leta 1844), rows for words the Slovene dictionary lacked, the long-press sentence sheet
on the lens instead of the app's older trigger rules, the trigger table's generator in the repository, the hint chip's
rules for B1's five new pages, and odvisniki linking B1b's conditional page. The rules as built are in companion/README.md
("The case lens"), companion/SCENES.md ("The sentence's grammar: a long press on a line") and docs/grammar-syllabus.md
("The long press on a line"). Everything Slovene here is machine-written (Claude) and waits for the native check below.

## How to use it

- **A word's case** is where it was: tap a word anywhere, its card's "🧩 Sklon · Case" row. It now knows more words
  (the B1a units' hčerka, kombi, pladenj, kmečki, novica …; Zdravljica's sedma kitica; every word pack's noun) and more
  rules ("leta 1844": "Kdaj? Rodilnik časa («1844»)"; "nekega dne"; "Zdaj kuham sam"; "kot sonce"; "poln vode").
- **The sentence's grammar** (a long press on a line, "🔍 Slovnica stavka · The sentence's grammar"): each word with a
  case shows the card's row, the same analysis: "rodilnik, ed. · genitive, sg." in the case's colour (likely ones
  marked, an ambiguous one's readings listed), "👉 Kam? → 4. tožilnik", what decided it in Slovene and then the base
  ("Po predlogu «v»: tožilnik."), the rule's page; a preposition what it decides ("Zahteva rodilnik: «mleka»"); a verb
  its person or tense as before.
- **The hint chip** (📖 Namig at a turn) of a turn naming sklanjatev-posebnosti, dvojina-skloni, vid-casi,
  dolocna-oblika or odvisniki says that page's rule in words with a model, instead of the plain hint.
- **The grammar book**: "Odvisniki" and "Če bi …, bi …" link each other (📖 Glej tudi).
- **The trigger table**: `python3 companion/grammar/sl/data/case-triggers.py` makes `case-triggers.json`; `--check`
  says whether they match (the Python tests run it). Change the script, never the JSON.

**Tap counts.** The sentence's grammar: before, its case lines came from rules of their own (they could disagree with
the card, and said nothing of a word like *leta*); to see the lens's case of each word of a 6-word line took the long
press and a tap on each word (up to 7 gestures, each card closed again). Now the long press alone shows every word's
lens row (1). A word's card: unchanged (1 tap), but 287 more words of the content have a row.

## What was built

1. **The generator** (`companion/grammar/sl/data/case-triggers.py`). The case-lens agent wrote the table with a script
   that lived only in its scratchpad, lost when the host's /tmp emptied at the reboot. It was recovered from that
   session's transcript: its first version and every later edit, replayed, make the table exactly as the case-lens
   branch merged it (byte for byte); native-check-sl's later hand edits (the review note, 21 fused forms) are folded in.
   The JSON is its output again (the same data; four of native-check-sl's lines were longer than the layout keeps).
   `companion/tests/test_case_triggers.py`: the JSON is what the script makes, a hand edit fails the check, the table
   says it is machine-written and where it comes from.
2. **The genitive of time** (the table's new optional `time_genitive`, `bridge/src/case-triggers.ts`): `leto` before a
   year in figures (leta 1844, Leta 1991; od leta 2024 stays the preposition's; "leta 1000 metrov" is a number that
   counts, not a year), and a masculine or neuter noun of time after nekega, tistega, istega, onega, naslednjega,
   prejšnjega, drugega (nekega dne, tistega večera). Kind `when`, the year or the determiner its trigger, the rule
   `cas-izrazi` (B1b's page: "leta 1890 (in 1890)"). SP2001 léto: "1945. ~a in ~a 1945", "Še istega ~a … je umrl";
   néki: "~ega lepega dne".
3. **More readings** (`bridge/src/case-lens.ts`):
   - the forms book's hand-checked noun tables, replacing Wiktionary's readings of their lemma (mož's accusative
     *moža*, which Wiktionary lacks), with their accepted and old forms; its animate list (a person's accusative singular
     is its genitive, never its nominative);
   - the word packs' nouns, by their regular endings from the lemma, the gender and the plural (kozolec, kozolci:
     *kozolc-*; traktor, traktorji: *traktorj-*; a person by its English gloss), marked `derived` ("〰 po pravilnih
     končnicah"); never a plural-only noun, an -ovi plural's plural, a fleeting e it can't tell, or a noun the forms book
     leaves out (četrt). The bridge reads the packs a minute apart and makes the lens again only when they change;
   - the dictionary form of a noun or an adjective listed without its grammar (tujec, mrzel), only when it declines
     (*super*, *poceni* never change); a plural-only one in -a or -e as the plural (očala);
   - a pronoun that goes with a noun as its determiner (tisti, vsak, kateri: *za tistim gričem*), a name's possessive
     before its noun (*Tonetov praded*, *Cesarjev meč*), a village name written with a capital as the name alone (Tone,
     never *tona*'s plural);
   - the supine of a table verb (grem *povedat* Nejcu).
4. **Fewer wrong cases said as sure.** Every change between main's lens and this branch's over the content's 6,025
   lines (5,477 of the scenes, stories, readings, grammar pages and packs, and the course units' 728) was read, and what
   was wrong fixed: an unknown l-form beside biti's form is the past (*Cesar si je zapomnil Krpana*: si no copula,
   Krpana the object; not -el, an adjective's ending: *Potok je mrzel*); *sam* said of the subject as *rad* is
   (*Zdaj kuham sam*, *jota se ne skuha sama*: seven were the accusative); a comparison after *kot* or *kakor* (the
   nominative, open beside a verb with an object; *kot* itself no noun); a nominative no role fits left unresolved
   instead of read as a copula's (*Vzemi ta jurček*: no row rather than a wrong one); a dual verb licensing the dual of
   its subject only (*Dajva mu kruha in vode*: some bread and water); words joined by *in, ali, ter* sharing their case;
   a plural subject of a first or second person verb (*Vsi imamo*, *Sosedje smo*); a lone ordinal said of the subject
   (*Kdo bo prvi spal?*); an hour's number no time without its preposition; an adjective with the genitive after it
   (*poln vode*, *Vreden je vsake stopnice*: the table's predicates may say `after`); an adjective before its noun whose
   form is besides only an infinitive (*peti maj*, not to sing); a line's capitalized first word the dictionary lacks as
   its verb when the clause has no other (*Narišiva mu nov obraz*, *Pazim.*), or as an l-form beside biti's (*Premagal
   je zmaja*), never in *Pegam je velik*; an infinitive no clause's verb on its own (*Tone mi pusti gnati meh*).
5. **The dictionary's gaps** (`companion/lexicon/sl.extra.json`, 34 entries, each with its sources): every standard and
   variant form Sloleks lists (its non-standard ones left out), the grammar as Wiktionary words it, a gloss in English
   with German and Italian meanings. B1a's hčerka, oba (with obe), kombi, pladenj, kmečki, novica, gradnja, izkušnja,
   test, zapirati, odpirati; kitica and sedmi; the labelled sets' and the content's frequent gaps pratika, polenta,
   rozina, prepad, nikalnica, pastirski, tujec, lovski, stražar, kij, muc, teran, tedaj (an adverb: no name),
   žalosten, strašen, miren, težek (with *težak*, the content's word), zapomniti si, neki, vsak; tisti's and drva's
   forms Wiktionary lacked (*tista …*; SP2001's second declension *drvom, drveh, drvmi*). Krpan needed no entry: a
   capitalized name the dictionary lacks is read by its ending, and now *Cesar si je zapomnil Krpana* reads right. The
   forms book: tujec, stražar, muc on the animate list (SP2001 *člov.*, *živ.*); četrt left out (SP2001 *četŕt 3* is
   undeclined in times and amounts: *ob četrt na tri*). The table: poldne and polnoč as nouns of time (SP2001 pôldne:
   "zvoniti ~"), poln and vreden with the genitive (pôln: "poln česa: vrč, ~ vina"; vréden: "vreden koga/česa").
6. **The sentence's grammar on the lens** (`game/SentenceGrammar.kt`, `app/SentenceController.kt`,
   `ui/words/SentenceSheet.kt`): the lookups it already made carry the lens's `case`, asked for each place of a word
   that has one (`n`); the row, the question (Kam? Kje? by the preposition's sense, Kdaj?, Koliko?, else the case's own)
   and what decided it go through the card's own code (`CaseLens.row`, `CaseLens.why`, moved out of `CaseRowView`).
   The old rules (`WordReadings`, the sheet's use of `Triggers`) are gone. Without the bridge the sheet shows the words
   alone (the lens is the bridge's, on the phone too); a look the lens counts as seen goes to the case trainer, as the
   card's does. QA's hook `sentence:<line>|<en>` opens the sheet on a line (QaHooksTest).
7. **The hint chip** (`game/TurnHint.kt`): rules for sklanjatev-posebnosti (hint.ruleStems), dvojina-skloni
   (ruleDualCases), vid-casi (ruleAspectTenses), dolocna-oblika (ruleDefinite), odvisniki (ruleClauses), each in words
   without a form a turn could test (the chip never shows the answer: `TurnHints.fair`), with models in other words.
8. **The grammar book**: odvisniki's `see` has pogojnik-ce-bi, and pogojnik-ce-bi's has odvisniki.

## Choices, and why

- **The lens, not the bundled table, for the sheet**: the app could read case-triggers.json itself (it ships), but its
  rules would still be a second analysis that can disagree. The lookups the sheet made already carry the lens's answer:
  one analysis, the card's and the sheet's. The hint chip keeps `Triggers`: it answers at a turn, before any lookup.
- **Optional schema keys, code first**: the table's validation is strict, so a running bridge with the old code refuses
  a table with `time_genitive` or a predicate's `after` (the lens off until it restarts). The code commit comes first;
  deploy it (and restart) before the content, as for every split of code and content.
- **Pack nouns derived, not added to the dictionary**: their forms are made by rule from the pack's lemma, gender and
  plural and marked derived, as the lens already did for a dictionary noun's missing form; they are not the forms book's
  checked forms, so nothing grades by them.
- **Sloleks's non-standard forms left out of the supplement**: they are readings of real speech, but the supplement's
  forms are what the card says a form is; a spoken form is never a mistake in grading (the forms book's `also`), and the
  lens reading it is no grading.
- **Unresolved over wrong**: where a nominative fits no role, the lens now says nothing (the card shows no row, the
  sheet the reading alone) instead of "a copula's". A spoken accusative the standard doesn't have (*Vzemi ta jurček*,
  SP2001: jurček *živ.*) is such a case: never called a mistake, never given a wrong case.

## Accuracy

`bun test/case-lens-eval.ts <gold|heldout|heldout2|heldout3|trainer> -v`; the smoke checks each set. Before: main
(e5c5d810); after: this branch. "Wrong and decided" is a wrong case said as sure, the one that must not happen.

| Set | Items | Before | After | Wrong and decided before → after |
|---|---|---|---|---|
| labelled (built on) | 291 | 279/285, 97.9 % | 284/285, 99.6 % | 0 → 0 |
| held-out 1 | 136 | 128/136, 94.1 % | 134/136, 98.5 % | 0 → 0 |
| held-out 2 | 104 | 98/104, 94.2 % | 101/104, 97.1 % | 0 → 0 (1 wrong as likely, both) |
| the case trainer's bank | 324 | 315/324, 97.2 % | 323/324, 99.7 % | 0 → 0 |
| **held-out 3, new** (labelled before any lens ran on it) | 89 | 70/87, 80.5 % | first run 75/87, 86.2 %; now 82/87, 94.3 % | 3 → first run 2 → 0 |

The first four sets have been looked at while tuning, by this round and the last: their gains say the changes hold,
not how the lens does on new lines. Held-out 3 does: 110 content lines no other set has, drawn at random (seed
20261030), one word with a nominal reading each, the 89 with a case in their line labelled by hand before main's or
this branch's lens ran on them. Its one honest run: **80.5 % for main, 86.2 % for this branch, two wrong and decided**
(*Cesarjev meč* read as a genitive plural, *Kje so moja očala?* as a singular); both fixed after, with the four other
misses it showed (the fixes are in item 4 and 3 above). Over the content: 14,349 → 14,636 words with a row, 89.0 → 89.5 %
of them decided or likely; the forms the lens knows nothing of 1,269 → 1,021 (3,354 → 2,851 words; most are names,
interjections and verb forms). 0.21 ms a line on the node.

## The sources, and how they were read

SP2001 on fran.si (`FilteredDictionaryIds=134`) for each word's standard form, gender and labels; Sloleks 2.0
(viri.cjvt.si/sloleks) for the paradigms, its labels (*variantno* kept, *nestandardno* left out, *živo*/*neživo* by
SP2001's *člov.*/*živ.*). 81 requests (40 to fran.si, 41 to Sloleks), one at a time, 5 s apart, each page cached in the
session's scratchpad (case-lens-2/cache/, a log of every request) so none was fetched twice; none refused. Every
supplement entry's `source` names its SP2001 query and its Sloleks headword.

## For Jan's wife: the short list

1. **hčerka**: SP2001 lists it without a label (standard beside hči); B1a's unit names it as "what people say". Is it
   everyday and neutral for you, or more spoken than hči?
2. **"Vzemi ta jurček."** (a scene's line): SP2001 marks jurček *živ.* (the accusative *tega jurčka*). Do you say *ta
   jurček*? (It is never graded wrong; the lens shows no case there.)
3. **"In težak!"** (heavy): the content's word. Sloleks has *težak* as a standard variant of *težek*; SP2001 has *težák*
   only as a noun (a labourer). Which do you say?
4. **The hint chip's new lines** (hint.ruleStems, ruleDualCases, ruleAspectTenses, ruleDefinite, ruleClauses) and the
   lens's two (caseLens.whyTakes "«poln» zahteva rodilnik.", caseLens.whyWhen "Kdaj? Rodilnik časa («1844»): leta 1844,
   nekega dne."): natural? Is *rodilnik časa* the word a teacher uses?
5. **The 34 new dictionary entries' glosses** (German and Italian too) and the five hint models (*z obema rokama*,
   *Odpri okno! · Ne odpiraj okna!*, *Daj mi zeleni nož.*, *Novi avto je moj.*, *Prišel sem, da bi ti pomagal.*).
6. **"Ko zvon zvoni poldne"**: the lens reads *poldne* as a time (SP2001: "zvoniti ~"), not the subject. Agreed?

## Tests

- Bridge: `bunx tsc --noEmit -p .` clean; the smoke 3,133 checks, all passed (LANI_SMOKE_PORT 9190,
  LANI_SMOKE_DRAMA3_PORT 9208), its case lens part with 29 lines of this round's rules, the third held-out set, every
  table example (92, the genitive of time's six among them); `tutor-export --check` 88 files match.
- Python: `companion/tests/test_case_triggers.py` (3: the table is the script's, a hand edit fails, it says what it
  is); 251 and 134 tests OK (11 skipped).
- App: `SentenceGrammarTest` (decided, likely, ambiguous, unresolved, a preposition tapped, the genitive of time, a verb's
  dative, the card's line and the sheet's the same, offline, a verb's tense), `TurnHintTest` (the five pages' rules,
  nothing shown the turn's form), `QaHooksTest` (the `sentence:` hook), `L10nTablesTest` (the strings; sentence.agrees
  gone). The full suite: below.
- Emulator QA: `companion/bin/qa-slot --steps lens-sheet` (below).

## What's left

- **The hint chip** still decides with the app's `Triggers` (it answers before any lookup, and offline): it could read
  the bundled case-triggers.json, or ask the lens for the turn's sentence once it is said.
- **Coverage**: 1,021 forms of the content the lens knows nothing of; most are names, interjections and verb forms, some
  nouns of stories (Zlatoroga's name reads by its ending). The supplement is where a word is added.
- **What it still can't decide**: a name given after *praviti* (*pravijo mu Friderikov stolp*: the nominative, read as
  the object), *vsak dan* beside a third person verb (likely the subject, more often the time), a list or a fragment
  without a verb (*Kamni, les, kolo*), the spoken undeclined hour (*ob deset*: no row).
- **Deploy order**: the bridge code before the content (the strict schema).

## Native check

Machine-written (Claude, 2026-10-30): the supplement's 34 entries (glosses and meanings; the forms are Sloleks's), the
table's new entries and examples, the third held-out set's labels, the strings `caseLens.whyTakes`, `caseLens.whyWhen`,
`hint.rule{Stems,DualCases,AspectTenses,Definite,Clauses}` in sl, en, de, it (`_note_case_lens_2`).
