# Parity: small things that read wrong (Wave D, batch 2, item 8)

Seven small things a learner of a language other than Slovene, or a learner at the end of something, met wrongly. Each
came from a read-only audit and was checked on main before it was fixed. All seven were real. Everything written here is
machine-written and marked so; see "For a native check".

## What changed

### The end of the written course

`School.preparing` looked only at the open levels and only at planned refs, so an empty level (German and English past
A1, Italian past B1) or a closed one said "✅ vse opravljeno · all done" on Home.

| Where the learner is | Before | Now |
|---|---|---|
| A level listed with no units, open or closed | ✅ all done | 📝 New lessons coming soon; the teacher says so in the school |
| Outlines landed: planned units, open or closed | ✅ all done while closed | 📝 the same |
| Written units behind a closed level (Slovene's A2 with no school) | ✅ all done | 🔒 A2: build the school (`School.waiting`, "school.levelWaits") |
| Past a course written to its end | ✅ all done | ✅ all done |

The tile's text is `endOfTheWritten` in `ui/school/SchoolScreen.kt`. Tests: `SchoolRevealTest`, five new cases. COURSE.md, "What
the school shows". No level is opened or closed differently.

A course whose file lists no later level at all ends there: "all done" past its last unit. Listing an empty level is how an
outline says more is coming, as German and English do today.

### The level-up grammar sign

No pages at a level meant 0 needed: "✓ Rules of B1 secure: 0/0". It now says what's right: "– The book has no rules of B1
yet: this sign doesn't count for now" on the map, ➖ "—" with the same caption on the progress screen (`Readiness.noPages`,
`PathLine.counts`). It still holds nothing back, since a wall with no way through would be worse: the reviews and the time decide the map.
This applies to German and English above A2, Italian above B1, and every language above B2. GAME.md, "The treasure map".

### The family page

It said "Pomagaj pri slovenščini · Help X learn Slovene", and every label, sample and word gloss in Slovene and English, to
any family. `GET /family/api/settings` now gives `language` and `base`; the page shows everything in the learner's two languages
through one table in `web/family/app.js` (`STRINGS`, `SAMPLES`, `NAMES`: sl, en, de, it), and `<html lang>`, dates and the title
follow. The words to record are glossed in the base. The Slovene "from" form is asked only of a family helping with Slovene.

- An older bridge says no language: Slovene and English, as before. The relay serves the new page to any town.
- With no key the page speaks the browser's language, else English.
- Nothing names Jan or Teo any more. Slovene lines that assumed a man's past ("Jan bo slišal") are in the present.
- `familyInstructions` still tells the tutor the partner_note is "English, Slovene words welcome". It belongs to
  `tutor-languages`, so it's unchanged here.

### Primorska's B1 arrivals

| Pack | B1 before | B1 now | Written |
|---|---|---|---|
| primorska | 6 of 14, newcomer | 14 of 14, newcomer | France, Tine, Ančka, Tone, Marko, Vida, Nejc, Jože |
| lakeland | 11 of 13 | 13 of 13, newcomer | Alfie, Harry, the newcomer |
| friuli | 13 of 13 | 13 of 13, newcomer | the newcomer |
| kaernten | 13 of 13 | 13 of 13, newcomer | the newcomer |

The newcomer in the three other packs goes beyond the item's wording: a B1 learner there met an A2 template. Friuli's speaks to
the learner with tu and is answered with Lei, as its A2 does, and nothing in it is frightening.

Each is a small story, whose turns test what B1 teaches: the genitive after a negative, a number with the verb, the dual,
the conditional's two halves, aspect, German's dass and dative, English's did and present perfect. A reply reacts in
character and never gives the right form. Spoken forms are not graded, and no clitic order is. The turn after an adult's
aside names the child it answers (`to`, Nejc's). Lines the A2 already says are kept word for word, so their clips stay. No
existing line changed. The new lines name only who is in the arrival, so none falls back to an easier level for a name.

### Hand-written cheat-sheet cards

An auto-card is the page's first sentence, its table and two examples. Reading all A1 pages of Italian, German and English,
what a learner misses:

- The first sentence is often only a lead-in: "Italian says *like* the other way round:", "1 to 10:", "Five prepositions fuse with
  the definite article after them:". The rule itself is paragraphs below.
- No trap: *sono freddo*, *mi piace miele*, *does she lives*, *I have ten years*, `wer` and `wo`.

Tables of 11 or 12 rows stay as the page has them, a little over "one phone screen". A card's own shorter table would also change
the printed sheets: `chooseTables` prefers it, and the row left out (*non* in `domande`) then needs a word on the sheet. That
failed the smoke, so no card here has a table of its own.

| Language | Cards before | Cards now | Start cards |
|---|---|---|---|
| it | 0 | 15, all of A1 | essere-avere, domande, numeri, articoli-genere |
| de | 5 | 15, all of A1 | sein-haben, fragen, zahlen, artikel-genus |
| en | 6 | 18, all of A1 | to-be, questions-do, numbers, pronouns |

Each new card has a rule line and a tip in every base; its table and first two examples are the page's. The smoke now fails on an A1 page of these three without a card. Slovene's 17 A1 pages without a card and
all pages above A1 keep their auto-cards.

### Tutor limits

Reading lengths now go to B2 (A1 40-80 words, A2 80-150, B1 150-250, B2 250-400; above B2 a B2 text, the top `level` a reading
has), in the readings feature's guide and `lani-studio`. A villager's lines are at the learner's level up to B2, in `lani-studio` and its
reference. A homework sheet no longer says "A1-A2": it is of its unit's level (a B1 or B2 unit has homework too; this one
wasn't in the item). `tutor-export` regenerated. This changes the guide Jan's tutor reads, in those lines only.
`tutor-languages` may touch the same lines: a small conflict, to keep both sides.

### A voice sample

`plainSample` fell back to Slovene for de and en. Each now has its own (Austrian greeting, British wording). It is what Voice Design
hears for someone with no lines of their own. A language without one still falls back to Slovene.

## How it was verified

- Android unit suite, tsc, the smoke, `tutor-export --check`, the Python tests: numbers in the report.
- `validateCulture` on the four packs; `ArrivalsContentTest` plays each arrival at every level, on time and late, in every base.
- The family page rendered in Chromium, its API faked, as it, de, en and sl learners and with no key: no console errors, every
  label and sample in the pair. Screenshots: `ux-parity-small/family-*.png` in the scratchpad.
- Emulator: `companion/bin/qa-slot --steps parity-small` (below). Hook added: "meet:<id>".

```bash
d=$(mktemp -d); (cd companion/bridge && bun test/fixtures/make-learner.ts "$d" --target de --base en)
# then learner.current_level = "B1" in "$d/learner-profile.json"
LANI_DEV_DATA=$d LANI_CULTURE=kaernten LANI_QA_SETUP_PAIR=de-en LANI_QA_LEARNER=Lena LANI_QA_PARITY_SHOTS=<dir> \
  LANI_QA_START_CARD="Ich bin, ich habe" LANI_QA_MEET=toni companion/bin/qa-slot --no-town --steps parity-small
```

## For a native check

All machine-written. Forms checked on fran.si (SSKJ², Slovenski pravopis), leaving register and naturalness for the native
speaker:

- prepustnica: https://fran.si/iskanje?View=1&Query=prepustnica
- pasti se (krave se pasejo; the dual *paseta se* made by rule): https://fran.si/iskanje?View=1&Query=pasti
- tekmovati, tekmujem (the imperative dual *tekmujva* made by rule): https://fran.si/iskanje?View=1&Query=tekmovati
- zdavnaj: https://fran.si/iskanje?View=1&Query=zdavnaj
- smrčati, smrčim: https://fran.si/iskanje?View=1&Query=smr%C4%8Dati
- mleti, meljem: https://fran.si/iskanje?View=1&Query=mleti

To read:

- **Slovene, primorska/arrivals.json B1:** France, Tine, Ančka, Tone, Marko, Vida, Nejc, Jože. Especially: "Sedem vreč je bilo res
  veliko!", "Hočeš nočeš", "Ker se hočem naučiti slovensko", "Kaj si pa res delal na drevesu?" against *naredil* (is the
  perfective wrong enough to grade, or only different?), "Nimam zaprta okna" as the wrong choice (might a native speaker say it?
  Then it must be a second `ok`, by the rule that a spoken form is never wrong). Marko's nono crossing with a pass is a family
  story built on the Brda double-owners' passes: a claim worth a look.
- **Slovene in the other files:** the family page's `sl`, the cards' `sl`, the lakeland arrivals' `sl`.
- **Italian:** the 15 cards, Friuli's B1 newcomer, the family page's `it`.
- **German (Austrian):** the 10 new cards, Kärnten's B1 newcomer, the family page's `de`, the sample's "Grüß Gott".
- **English (British):** the 12 new cards, Alfie, Harry and the newcomer, the sample's "How are you keeping?".

## Voice

No credits spent, and no clip is lost: no spoken line changed. `voice-build` takes the cast's arrivals, the lines with no
placeholder; a line with one (the learner's name, a gender pair) and a template's are voiced when played.

| Pack | For a voice build | Voiced when played |
|---|---|---|
| primorska | 84 lines, 4,542 characters | 18 lines, 1,289 characters |
| lakeland | 24 lines, 1,364 characters | 0 lines, 0 characters, and the newcomer's 14 lines, 797 characters |
| friuli | none | the newcomer's 14 lines, 989 characters |
| kaernten | none | the newcomer's 14 lines, 925 characters |
| Together | **5,906 characters** | |

The plain samples are heard only by Voice Design, when a person is designed. The family page and the cards' rule lines aren't voiced;
the cards' examples are the pages', which have their clips.

## What's left

- Cards above A1 in Italian, German and English, and Slovene's 17 A1 pages without one, still use auto-cards.
- `birth` and `moved` templates have no B1 anywhere. They're short by nature; a B1 learner hears A2.
- The family's partner_note language (`familyInstructions`): `tutor-languages`.
- An older app's Home says what it said; it needs this app for the tile and the sign.
