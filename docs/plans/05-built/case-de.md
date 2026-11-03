# Built: German's cases: the case set, the trigger table, the trainer, the lens, the overview (2026-11-03)

Wave E, batch 1, item 4 (`case-de`): the case trainer ("🧩 Fälle · Cases"), the case lens and the cases overview were
Slovene's only. German now has its own case set, a trigger table, a trainer bank A1–B2 from the course's pages, the lens
on German content and an overview print. Slovene's are unchanged (digests below). Branch `case-de`, from main f9339ab8.

## Native check (first: everything below is machine-written by Claude)

For a native speaker of German (Austrian standard), and the bases' translations for one of each:

- **The trigger table** (`companion/grammar/de/data/case-triggers.py`, its JSON made from it): the prepositions' cases and
  senses (the two-way ones; `wegen`, `trotz`, `während`, `statt` with the dative marked *spoken*; `laut` and `dank` with
  either), the contractions (only `im, am, beim, vom, zum, zur, ins, ans` are marked the usual written form; `aufm` spoken),
  the verbs with a dative (`glauben` added), a dative and an accusative, a genitive (`gedenken, bedürfen, sich bedienen,
  sich vergewissern`), the verb, adjective and noun chunks (`warten auf` … `abhängig von`), the n-Deklination's 61 nouns
  (is `Bauer` right as weak only? `Bub` is Austrian), the fixed phrases (`Guten Morgen` etc. as accusatives), the times
  (`jeden Tag`, `eines Tages`).
- **The bank** (`companion/cases/de/bank.json`, 456 items, 31 frames, 15 adjectives, 25 nouns): every sentence and its
  sl, en, it; each item's case, number and deciding word. Least sure: `das-ist-der-kathi-ihr-gasthaus` and
  `das-ist-dem-stefan-sein-hund` (the southern possessive dative: is it right to call it right in talk, as the genitive
  page does?), `ich-gebe-dem-bub-ein-eis` (dem Bub as spoken), `waehrend-dem-essen-schaut-niemand-aufs-handy`
  (während + dative in talk), `wir-fahren-auf-urlaub` and `mama-ist-in-der-arbeit` (Austrian, with the note that
  Germany's form is right too), `die-kinder-kommen-aus-der-schule-heim` (heim as the particle that rules out wohin),
  `das-brot-baeckt-im-ofen`, `kathi-kauft-eier-auf-dem-markt` (a "Wo oder wohin?" whose verb is no verb of rest),
  `ich-habe-lust-auf-einen-kaffee` (Lust auf as a B2 chunk), the frames with "{x}" at the start ("{x} ist schön.") and
  `unter-dat` ("Die Katze schläft unter {x}." with any noun), the Slovene `mika me kava`.
- **The lens's lines** (`caseLens.de.*`) and the trainer's German labels (`cases.de.*`) in de, sl, en, it.
- **The overview's headings** (`prints/overview.ts` HEAD_DE) in de, sl, en, it.

## Voice

None: the trainer and the lens are read, not voiced (the trainer's 🔊 uses the phone's voice, as Slovene's). No
characters to build.

## What

- **German's case set.** Four cases in the course's order (the page `faelle`): Nominativ (wer? was?), Akkusativ (wen?
  was?), Dativ (wem?), Genitiv (wessen?). The Austrian school's numbers (1. Fall Nominativ … 4. Fall Akkusativ) are kept
  in the table but not shown: they are another order than the course's. The colours are Slovene's for the same case.
  - Bridge: `case-triggers-de.ts` (`DE_CASE_KEYS`, `DE_CASE_IDS`, `DE_FALL`, `DE_COLOURS`, `DE_CASE_PAGES`).
  - App: `data/CaseLook.kt` `CaseSet` and `CaseSets` (`SL`, `DE`, `current` by the learner's target language). `Case.id`,
    `ask`, `alsoAsk`, `slName`, `named` and `Case.label()` read the current set; `Case.ofId` reads any set's ids.
- **German's trigger table** (`companion/grammar/de/data/case-triggers.json`, made by `case-triggers.py` beside it; the
  Python test holds the JSON to the script): every determiner's sixteen forms (der, dieser, jener, jeder, welcher,
  mancher, solcher, alle, beide, ein, kein, the possessives), the adjective endings after each kind (weak, mixed,
  strong) and the stems that drop an e (hoch: hoh-), the prepositions by case (two-way: motion and rest; the genitive's
  with the dative people say), 21 contractions, 49 verbs with a dative, a dative and an accusative, or a genitive (their
  forms; separable ones with their particle: hört … zu), the verbs of motion and of rest, the copulas, 42 verb, adjective
  and noun chunks with a fixed preposition, the pronouns, the relative pronoun's own forms, 61 n-Deklination nouns, times
  without a preposition, fixed phrases. Every entry names its rule, a German grammar page. 54 examples, each analysed by
  the smoke. Schema: `bridge/src/case-triggers-de.ts`. The German hint chip can read it later.
- **German's forms** (`bridge/src/german-forms.ts`, the app's `data/GermanForms.kt`, held together by the parity fixture
  `bridge/test/fixtures/german-forms.json`, `bun test/german-forms-fixture.ts --write`): a determiner's readings and its
  form in a slot, an adjective's form after a determiner, a noun's forms by case and number with how sure: the
  n-Deklination by the table (dem Kollegen; dem Bub said, never wrong), the genitive -es or -s (des Hundes, des Hunds both
  right; des Busses not sure, never asked), the dative plural's -n.
- **The trainer's German bank** (`companion/cases/de/bank.json`; format `bridge/src/case-bank-de.ts`, Slovene's format
  with the sentence under `de` and the meaning under `sl`, `en`, `it`): 456 items, 387 "Welcher Fall?" and 69 "Wo oder
  wohin?" (45 wo or wohin, 12 wo, wohin or woher, 12 zu, bei or nach; 57 with the village's scene). An item frames its noun
  phrase (`phrase`: the article, the adjectives, the noun) and names the noun's gender, so the checks hold the article,
  the adjectives and the noun to the case. 31 frames for "Dekliniere!" naming their cue's determiner; 15 adjectives; 25
  fallback nouns; 8 mass nouns. Spread and checks below.
- **The lens on German** (`bridge/src/case-lens-de.ts`): GET /lookup's `case` and POST /case-lens's colours for a German
  village's lines, the same wire as Slovene's. It reads each noun phrase (a preposition or a contraction, the determiner,
  the adjectives' endings, the noun's -n and -s; a pronoun; a name, with the article Austria puts before it) and lets the
  sentence decide (the file's head lists how). It says "decided" only where one reading is left; "likely" for the order's
  or a verb's preference; else every reading. A form people say is read as what it is: wegen dem Regen is a dative after
  wegen, with the written genitive beside it ("so sagt man es auch; geschrieben »des Regens«"); dem Stefan sein Hund is a
  dative of who has it (written: Stefans Hund).
- **The app's trainer in German**: four chips (two a row), the noun phrase framed, "Warum?" the deciding word or the role,
  "Wo oder wohin?" (its kinds' own instruction), "Dekliniere!" typing the article (and adjective) and the noun, graded in
  German (the n-Deklination's bare form right and labelled), the storm in four cases, the trainer's labels German's own
  where Slovene's say six cases or Tožilnik (`cases.de.*`), the lens's own lines (`caseLens.de.*`: the contraction, the
  possessive dative, the article saying the case, a time, eines Tages, a name's -s). The learner's own nouns come from GET
  /cases's `learner_nouns` (their cards that are a pack's nouns, with gender and plural); a noun without a known plural is
  kept to the singular.
- **Where it opens**: Home's tile (once the cases have begun: the unit Akkusativ's intro, a case page met, or A2), the
  grammar book's case pages ("🧩 Fälle üben": `CaseLook.PAGES_DE`), the school's case units (`CaseTrainer.CASE_UNITS_DE`,
  each with its focus), the tutor's `assign_cases`.
- **The tutor**: `get_cases`, `assign_cases` (German's case ids or its pages), `publish_case_items` (German's format,
  checked as the bank is), `remove_case_item`, `cases_done` in a German village; the guide's German instructions
  (`tutor-export` regenerated).
- **The overview print** "Der, den, dem, des: die vier Fälle" (`ueberblick-faelle`, three A4 pages, from the pages as they
  are: `faelle`, the three prepositions pages, `wechselpraepositionen`, `adjektiv-tabelle`, `n-deklination`,
  `pronomen-dativ`, `verben-praepositionen`). `school-prints --overview faelle --language de --base sl|en|it`; GET
  /course/overview/faelle; the app's "🖨️" on the page faelle, on the units naming it (wem-dativ, adjektiv-der, genitiv,
  herr-kollege) and on their levels.

## Older apps

An older app (Teo's 0.2.698, an older phone) knows Slovene's six cases only. Its trainer and its colours are gated by its
own list of languages (`CaseBanks.LANGUAGES`, `CaseLens.LANGUAGES`: "sl"), so it never asks GET /cases or POST /case-lens
in a German village. Its word card, though, shows any `case` GET /lookup gives: so the bridge gives a German word's case
only to an app that sends `lens=2` (this app, with every lookup that has a line). An older app's lookups in a German
village get no case row, as before. GET /state says `cases` in a German village (the new app sends its sessions to POST
/cases/done); an older app never plays a German session. GET /cases's `learner_nouns` is a field an older app ignores.

## Why so

- **The course's order, not the Austrian school's numbers.** The course teaches Nominativ, Akkusativ, Dativ, Genitiv (its
  page faelle, the units A1 akkusativ, A2 wem-dativ, B1 genitiv); the chips follow the school the learner has. Showing
  "4. Fall" beside Akkusativ would teach a second order.
- **The noun phrase framed, not one word.** German shows its case in the article and the adjectives' endings, rarely in
  the noun: framing "dem alten Mann" shows where to look. The word asked about stays the noun (the lens's and the bank's
  `word`), so the storm's phrases and the checks work as Slovene's.
- **Dekliniere types the phrase.** The cue is the phrase in the nominative with the frame's determiner ("(mein Opa)"); the
  answer is the whole phrase ("meinem Opa"): the article is where German's case is learned. Frames never put der right after
  in, an, bei, von, zu in the singular (the contraction would be the usual form).
- **Rules for the forms, a fixture for their parity.** German's nouns decline by a few rules the table can hold (the
  n-Deklination's list, the genitive's -es or -s, the dative plural's -n); a full forms book (Slovene's) isn't needed. Two
  ports (bridge and app) are held together by the fixture, which the smoke and `CaseTrainerDeTest` both read.
- **The lens never guesses silently.** On the bank's 387 "Welcher Fall?" items (gold written apart from the lens) it reads
  385 right and none wrong as sure; 2 wrong as likely. Its likely picks are shown with the other readings.
- **Austrian forms are never wrong**: the lens reads, it doesn't grade. In the trainer a spoken form is an item's `spoken`
  (right, labelled), never a wrong option.

## Slovene unchanged

- `bun test/case-digest.ts --check <the digest of f9339ab8>`: the lens over 6,525 content and course lines, 2,400 taps on
  them, the bank as validated and the trigger table, each a SHA-256: unchanged (`✓ Slovene unchanged`) after every commit.
- The Slovene overview's HTML in en, de and it: byte for byte the same as f9339ab8's.
- The app: `CaseSets.SL` holds Slovene's ids, questions, names and numbers as `CaseLook.kt` had them (`CaseTrainerDeTest`
  "Slovene's set is as it was"); the Slovene trainer's and lens's tests pass unchanged (`CaseTrainerTest`, `CaseLensTest`).

## Checks

- Bridge: `bunx tsc --noEmit -p .`; `bun test/check-cases.ts de` (ok); the smoke's `case-de` section (the table against
  the German book, its 54 examples through the lens, the bank's spread and what its checks catch, the lens against the
  bank's gold, the forms fixture, `stateFeatures`) and `caseDeLive` in the Kärnten village's bridge (GET /cases, GET
  /lookup with and without `lens=2`, POST /case-lens, the tools, POST /cases/done); `tutor-export --check`.
- Python: `test_case_triggers_de.py` (the JSON is the script's).
- App: `CaseTrainerDeTest` (the set, the bank, the forms' parity, Dekliniere, the n-Deklination, the noun's sure slots,
  a which item, sessions and a child's, the storm, the lens's lines), the existing case tests.

## How to use it

- A learner of German: Home's "🧩 Fälle" → "▶️ Los"; a case page's "🧩 Fälle üben"; a case unit's "🧩 Fälle". A word
  tapped in a German line: its card's "🧩 Fall · Sklon" row. "🎨 Fälle" in a reading or the dialog's ⋯: the lines'
  colours. The overview from the page faelle or its units' "🖨️".
- Adding to the bank: `companion/cases/de/bank.json` (the format in `bridge/src/case-bank-de.ts`), then
  `cd companion/bridge && bun test/check-cases.ts de` until "ok". The trigger table: change `case-triggers.py`, run it.

## Taps

| what | before | after |
|---|---|---|
| a German "which case?" | none | Home's "🧩 Fälle" (1) → "▶️ Los" (1): 2 |
| a German word's case | none (its card had its grammar string at most) | one tap on the word |
| a German line in colours | none | ⋯ (1) → "🎨 Fälle" (1) in a dialog; 1 in a reading |
| the German cases overview | none | the page faelle's "🖨️" (1), or a unit's (1) |

## What's left

- The native check above.
- The German hint chip (wave E's `hint-chip-*` after this branch) can read the table: `prepositions`, `contractions`,
  `verbs`, `chunks`, `predicates`.
- The lens's open cases: two NPs that can both be the subject and the object with a name (die Suppe, die Kathi kocht) stay
  "likely"; a relative pronoun der/die/das in a clause with a name; unknown nouns get every gender.
- QA on the emulator: see below.
