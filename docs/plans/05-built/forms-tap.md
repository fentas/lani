# Built: word forms on a tap for Italian, German and English (forms-tap, 2026-11-03)

Wave E, batch 1, section 5 (followups-14.md). Slovene had forms on a tap, form questions in reviews and forms cards;
Italian, German and English had none. Verified on main f9339ab8 before starting: `FORMS_LANGUAGES` was `{sl}`, GET /forms
answered no entries in the three villages, POST /forms-cards answered 400 there, and GET /state left `forms-cards` out.

## Native check (first)

Everything below is machine-written by Claude and not reviewed by a native speaker.

- **The app's strings** (`l10n/*.json`, `_note_forms_tap`), in sl, en, de and it: `wordForms.imperfect`, `conditional`,
  `subjunctive`, `subjunctiveImperfect`, `remotePast`, `preterite`, `konjunktiv2`, `gerund`, `ingForm`, `pastParticiple`,
  `perfect`, `superlative`; and a child's words for the terms, `wordForms.kid.*` (de for Teo: "jetzt", "ist schon
  passiert", "wie es früher war", "später", "was wäre", "nach „ich glaube, dass …“", "im Märchen: vor langer Zeit",
  "wenn man sagt: mach!", "gerade jetzt", "mehr als eins", "wie etwas ist", "mehr, am meisten"). Slovene's terms for
  other languages' grammar: imperfekt, pogojnik, konjunktiv, pripovedni preteklik, preterit, gerundij, pretekli deležnik.
- **The frames a form is asked in** when no line of the content has it (`game/OtherForms.frame`): Italian "Io ____.",
  "Prima io ____ ogni giorno.", "Domani io ____.", "Io ____ volentieri.", "Lei pensa che io ____.", "Se io ____, sarebbe
  bello.", "Tanto tempo fa io ____.", "Ho ____." / "Lui è ____." / "Lui si è ____.", "Sto ____.", "(tu) ____!", "Ecco due
  ____.", "La casa è ____.", "I ragazzi sono ____.", "Le ragazze sono ____.", "Questo è ____ di quello.", "È ____!";
  German "Ich ____.", "Ich habe ____." / "Ich bin ____.", "(du) ____!", "zwei ____", "die Farbe des ____", "mit dem ____",
  "mit den ____", "Ich sehe den ____.", "Er ist ____ als ich.", "Er ist ____."; English "She ____ every day.", "I ____
  here.", "Yesterday I ____.", "I ____ at home yesterday.", "I have ____.", "I am ____ now.", "two ____", "It is ____ than
  that one.", "It is the ____.".
- **The tables' words** (`OtherForms.table`, `FormsCard.otherPlans`): the pronouns (io, tu, lui, lei …; ich, du, er, sie,
  es …), "sto mangiando", the passato prossimo's agreement written "sono andato/a", "siamo andati/e"; German's case
  questions "1. wer? was?", "2. wessen?", "3. wem?", "4. wen? was?"; English's "he, she walks", "have walked", "am walking".
- **Which page a form counts on** (`bridge/src/paradigms.ts` `pageOfIn`): an Italian present is regular (presente-regolare)
  when all six forms are its ending's (with cerchi, mangi, finisco), else verbi-irregolari; a German verb's du and er
  forms are vokalwechsel's when they don't start with the infinitive's stem. A check of 20 everyday verbs each would do.
- **Not checked by hand:** the 11,000 entries of the paradigms files are Wiktionary's own tables (below), read by rules;
  a sample read is listed under "Verified".

No voice: nothing here is voiced. A voice build needs no characters for it.

## Jan's decision

**The paradigms files are CC BY-SA 4.0 (share-alike) data inside the released APK.** `companion/lexicon/it.paradigms.json`,
`de.paradigms.json` and `en.paradigms.json` are adapted from English Wiktionary (via kaikki.org) and must stay under CC
BY-SA 4.0, with attribution. The APK already carries `sl.json` on the same terms, so nothing new in kind; but it is three
more files, and they are what makes forms work on a phone without a node. Built and wired as if the answer is yes: NOTICE.md
lists them, each file's `meta` names the licence, source and changes, the app shows "📖 Wiktionary (CC BY-SA 4.0) via
kaikki.org" under every table it shows from them, and the site's credits name all four extracts. If the answer is no: the
files go (the forms then work only on a node with a downloaded dictionary, after a further change: today the bridge reads
the paradigms files only). For English alone AGID would be a permissive alternative (below); for German there is none.

## Which data, and why (the licences, read 2026-11-03)

| source | languages | licence (where read) | fits? |
|---|---|---|---|
| **English Wiktionary via kaikki.org** (chosen) | it, de, en (and sl already) | Wiktionary: CC BY-SA 4.0 and GFDL ("dual-licensed", en.wiktionary.org/wiki/Wiktionary:Copyrights); kaikki.org states none of its own and asks to be cited and linked | yes: the source Lani's dictionaries already use, the attribution the app already shows; structured tags per form; the best tables of the free ones |
| UniMorph ita, deu, eng | it, de, en | CC BY-SA 3.0 (each repo's README; no LICENSE file) | share-alike too, older version; it is Wiktionary's tables again, without glosses or auxiliaries |
| Morph-it! (Baroni & Zanchetta) | it | not verified: its page answered 402; reported elsewhere as CC BY-SA 2.0 and LGPL | share-alike in any case; no glosses; an older lexicon |
| LanguageTool german-pos-dict | de | CC BY-SA 4.0 (its LICENSE), from Morphy and korrekturen.de | share-alike; Morphy's own terms not checked |
| DEMorphy | de | MIT for the code; its dictionary data's licence not stated | not usable without a stated data licence |
| AGID (Kevin Atkinson) | en | permissive ("Permission to use, copy, modify, distribute and sell …"), but it carries the UK Advanced Cryptics Dictionary's notice (displayed prominently, its text verbatim) and WordNet's | the permissive choice for English, with two notices to carry; a second source for one language |
| Sloleks 2.0 / 3.0 | sl | 2.0: **CC BY-NC-SA 4.0** (non-commercial); 3.0: CC BY-SA 4.0 (CLARIN.SI 11356/1230, 11356/1745) | how Slovene uses it: consulted by hand for the forms book's tables, never extracted as data |

**Compared with Sloleks.** Slovene's forms book (`sl.forms.json`) is written by hand, each table checked against SP2001
on fran.si and Sloleks 2.0, with the entries cited: facts consulted, not a database extracted, so its licence doesn't bind
the file. The paradigms files are different: they *are* an extract of Wiktionary's tables (a substantial part of a
database, CC BY-SA 4.0 section 4), so they are adapted material and carry the licence. **Found on the way:** Sloleks 2.0,
which the forms book's notes cite, is non-commercial; Sloleks 3.0 is CC BY-SA 4.0. Consulting a few dozen paradigms is
fine either way, but later checks should cite 3.0 (or its successor, 11356/2080).

CC BY-SA 4.0 in a free app on a store: allowed. What it asks: the attribution (creator, licence link, a link to the
source, that it was changed: the files' `meta`, NOTICE.md, the app's line under each table); the adapted files under CC
BY-SA 4.0; no technical measure that keeps a recipient from what the licence grants (the APK is unencrypted, the files
are in the public repository).

## What

### The data: `companion/lexicon/<language>.paradigms.json`

`companion/bin/forms-build --lang it --from <kaikki extract> --extract-date 2026-10-30` (extracts: Italian 77 MB, German
99 MB, English 534 MB gzipped; it 17 s, de 15 s, en 66 s; under 1 GB of memory). It collects every word of the language's
curated content (its culture, packs, course, grammar book, modules, drills, scenarios and supplement: it 13,905, de
13,620, en 15,824 different words), keeps each noun, verb and adjective whose word or one of whose forms the content says,
and reads each form's tags into a slot (`slotOfTags`, shared with the bridge). The rules: an entry's first table only
(essere's later tables are another variety's: sò, sarebbi); old, rare, joking forms out; Italian's and German's regional
and colloquial forms accepted (`also`, never shown: Jungs, dem Hause); English only another country's spelling accepted
(its nonstandard forms, gooder, goed, "I were", are the course's `nonstandard` grading or a learner's mistake); Italian's
stress marks dropped inside a word and on a one-syllable form (và → va, sò → so; è, dà, può kept); a reflexive verb's
participle without its pronoun (lavatosi → lavato); a feminine noun kept as a word of its own (Freundin, cugina); a verb
or adjective beside a noun of one spelling left out when the content says none of its own forms (English good, "to
furnish with dung").

| | entries | verbs | nouns | adjectives | forms | file |
|---|---:|---:|---:|---:|---:|---:|
| it | 3,762 | 1,169 | 2,094 | 499 | 61,103 | 1.7 MB |
| de | 3,933 | 1,111 | 2,541 | 281 | 40,251 | 1.1 MB |
| en | 5,110 | 1,275 | 3,549 | 286 | 13,258 | 0.8 MB |

The packs' one-word words with a table: it 951 of 1,046 (90.9 %), de 935 of 1,029 (90.9 %), en 905 of 936 (96.7 %). The
rest: numbers, names of days, invariable words (papà, bar), words Wiktionary lacks (Kasnudel, Reindling, bellcote), a
plural noun (genitori, parents), braccio (Wiktionary gives its two plurals only in its head line).

### The bridge (`bridge/src/paradigms.ts`)

- **Slots** (`LANG_KEYS`) and **pages** (`pageOfIn`): companion/README.md, "Word forms", "Other languages", has the
  table. Every page a slot counts on is in that language's book (a smoke check).
- **`Paradigms`** reads the file (each entry checked on its own; read again when it changes) for `formsOf`, which hands
  Italian, German and English to `otherFormsOf`: each slot with its page, grammar and up to three content lines (English:
  have before a participle, be before -ing, no determiner before a form that is a word itself; Italian: che before a
  subjunctive; a present and an imperative told apart by the translation). Each line keeps its Slovene too, for a learner
  who reads Slovene (`ContentLine.sl`; Slovene's own lines unchanged).
- **GET /forms** serves them only with `v=2`.
- **Forms cards** (`forms-cards.ts` `otherCardOf`): the entry's slots as they are, a form the same as the word's own
  `given` (shown, never a gap: German's dem Haus, English's cut); the word written as it is (Haus); `language` and `aux`
  kept. POST and GET /forms-cards take `v: 2` / `v=2`; GET /state says `forms-cards-v2` in these villages. The core verbs
  of each language first (`CORE_IN`). Their mistakes' kinds add `tense_imperfect`, `tense_conditional`,
  `tense_subjunctive`, `tense_remote_past`, `tense_preterite`, `tense_participle`, `tense_gerund`,
  `adjective_comparison`; the shared kinds' descriptions in each language's words (`FORMS_PATTERNS_IN`: io, ich, he).
- **Older apps.** Teo's tablet runs 0.2.698, which asks GET /forms without `v`: it gets no entries, as before, so it never
  lays Italian forms out with Slovene's pronouns and frames. It never makes another language's forms cards (its POST has
  no `v`: 400), and GET /state without `forms=2` leaves out any review item that is another language's forms card
  (`withoutOtherFormsCards`), so a second, older device of a learner whose new app made one never plays it. Slovene's
  answers are unchanged for every app.

### The app

- `game/OtherForms.kt`: the word card's table, the frames, what is asked, the labels; `WordForms` hands it an entry with
  a `language`. A card's front gives its word and what it is (`WordFormsWire.lemmaOf`, `posOf`: "la casa", "l'acqua",
  "der Wald" a noun, "to run" a verb), which the review, the car's quiz, the school's practised forms and the forms
  cards use.
- The phone's cache of forms (`CachedForms`) keeps the version it asked with: an empty answer kept before (an Italian
  word's) is asked again; Slovene's tables stay as they were, offline too.
- Forms cards: `FormsCard.otherPlans` lays a card out (a tense by person; the participle once, after its auxiliary, "lui
  ha ____", "er ist ____"; German's cases with their article as the cell's words before), `otherKind` names a wrong
  cell's kind, a gap is graded in its card's language. `FormsCard.has` turns them on for Slovene's feature or the others'.
- For a child (`OtherForms.child`, from the profile): the terms say what they mean, "presente · Präsens (jetzt)".

## How to use

- Tap a word of a dialog in Teo's, Eva's or Ana's village: the card shows "📚 Forme · Formen" (in the pair), the reached
  forms, the rest 🔒 with the page that opens them, "📖 Wiktionary (CC BY-SA 4.0) via kaikki.org" under it.
- A review asks a form now and then, as Slovene's does, once the word's meaning is known.
- "📋 Tutte le forme · Alle Formen" on a word's card opens its whole table; "🃏" makes its forms card.
- New content: `companion/bin/forms-build --lang <it|de|en> --from <extract>` again (deterministic for one extract).

## Tap counts

| | before | after |
|---|---|---|
| an Italian, German or English word's forms | none anywhere | 1 tap (the word in a dialog), its card's table |
| a form question | never | 0 (it comes in a review) |
| a word's whole table, its forms card | none | 1 tap ("📋" on its card), 1 more for its card |

## Verified (on f9339ab8 + this branch)

- Bridge: `bunx tsc --noEmit -p .`; the smoke with ports 10390–10409: 3,723 checks, all passed, among them
  `test/smoke/forms-other.ts` (17: the tag reading, the pages, every page in its book, the files valid with their
  licence, andare's va and vado alone, essere's sono, bello's forms, Haus's cases, fahren on vokalwechsel, be's
  am/is/are/was/were, go's went/gone, mangiare's lines, eat's lines and no gerund line), and in the friuli, kaernten and
  lakeland sections GET /forms with and without v=2, German's forms cards (Haus's table with dem Haus given, fahren's
  card made on demand) and their holding back from an older app's GET /state. `tutor-export --check`: 140 files match.
  Every touched TypeScript test file parses (`bun build --no-bundle`).
- Python: 253 and 164 tests (11 skipped), OK.
- Android: the full suite, 4,113 tests: 2 failures of `L10nTablesTest` (the child's keys built at run time), fixed by
  naming them; that class and the forms tests run again green (see the report).
- A sample read of the files: mangiare, andare, essere, fare, dare, sapere, lavarsi, finire, bello, buono, casa, uovo;
  Haus, Herr, Junge, Frau, fahren, abfahren, sitzen, sein, wissen, gut, schön; be, have, go, eat, learn, child, good,
  sheep.
- Emulator QA, `companion/bin/qa-slot --steps forms-other` on a synthetic learner each, screenshots in the scratchpad
  under `ux-forms-tap/{friuli,kaernten,lakeland}/`:
  - Teo (friuli, a child, it · de): "case" in a line opens la casa's card with "casa · case"; "mangia" added by ➕ shows
    mangiare's present by person ("io mangio … loro mangiano"), its imperative open, the A2/B1 tenses 🔒 with the child's
    words ("passato prossimo · Perfekt (ist schon passiert)"), "📖 Wiktionary" under it; a review's form question in a
    real line: "Giulia ama i gatti, Tommaso invece ama i ____. (cane)", „Giulia liebt Katzen, Tommaso dagegen liebt
    Hunde.", typed (a noun has one other form only).
  - Eva (kaernten, de · sl): "Häusern" opens das Haus's four cases with the article ("das Haus | die Häuser", 2. and 3.
    🔒 until Genitiv and Dativ), "fährt" fahren's Präsens ("du fährst"); a question "zwei ____ (Haus)" chosen among
    Häusern, Häuser, Hauses, Haus.
  - Ana (lakeland, en · sl): "houses" house's plural, a form question answered; "went" go's forms a row each
    ("present · he, she goes", the past and the participle 🔒 until their A2 pages, "-ing form · am going",
    `lakeland/16-went-table-a1.png`). The step's first run passed without the table (it found the tapped word); the
    stricter step then failed only on its own expectation ("have gone" is locked at A1), which the last commit fixes;
    its run after that waited an hour for a build slot and was stopped, so the English step as committed is not run
    on the emulator yet, nor the last two UI fixes (German lock lines, English's title) on any.

## What's left

- **A tutor's new words** (a pack the tutor publishes) have no table until `forms-build` runs again: the bridge reads the
  paradigms files only, not a downloaded dictionary's forms. Reading a downloaded dictionary's grammar strings into
  these slots is the next step (`slotOfTags` takes the tags those strings are made of).
- German adjectives' declension (der schöne, ein schöner) is on its grammar pages, not in the table: the table has the
  comparative and superlative only. Italian's articles (il, lo, l') are not in the table either.
- `case-de` (German's case trainer and lens) can read German nouns' four cases from GET /forms (keys `nom.sg` … `acc.pl`,
  pages artikel-genus, genitiv, dativ, akkusativ, plural, n-deklination); its own forms context must pass `paradigms`.
- The prints don't show forms tables (section 5 didn't ask for them).
