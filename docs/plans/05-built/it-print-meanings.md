# Built: the Italian prints' word meanings (2026-11-01)

Each Italian unit's printed sheet (companion/COURSE.md, "Printed sheets") lists the unit's words under "Wörter /
Besede / Words" with their meanings. A word that no pack meant was left off: 5 to 13 a unit in A1, 9 to 18 in A2
(course-it-a2c.md), up to 22 in B1. Among them were the most common verbs and adverbs (guardare, essere, mai). This
round gives every word a meaning in each base, and the word lookup in the app finds them too.

## What was missing

Measured with `school-prints --language it --level all --explain` in each base, before this round (the branch
`outlines-de-en`, 3abcd143):

| | German | Slovene | English |
|---|---:|---:|---:|
| words listed on the 54 sheets | 998 | 997 | 998 |
| places a word was left off for want of a meaning | 462 | 463 | 462 |
| different words left off | 287 | 288 | 287 |
| left off per unit, A1 / A2 / B1 | 0–13 / 3–16 / 5–22 | the same | the same |

Slovene had one more: con, which the unit preposizioni glosses in German and English only. Of the 288 words:

- **19 were in a pack, but in the singular.** The unit lists them in the plural with the article (i cavalli, le mele,
  le uova), and the print matched only a pack word's own form.
- **The supplement's entries printed in English on a German sheet** (c'è: "there is, there are"). The print read only
  Wiktionary's meaning of a supplement entry, not the supplement's own `meanings`. Slovene sheets in German and Italian
  had the same problem (vstati: "to get up").
- **269 were in no pack**: verbs (the irregular ones and the most common regular ones), nouns, adverbs, adjectives,
  pronouns and possessives, and expressions (c'era una volta, un po', mi dispiace, e mezza, che ore sono, al posto
  tuo).

## The choice: the supplement, not the packs

The meanings are in the Italian supplement, `companion/lexicon/it.extra.json`, which already existed (it-lexicon.md).
The bridge's lookup reads it (`Lexicon`, lexicon.ts), the prints read it (`Dictionary`, prints/sheet.ts), the APK
bundles it, and `lexicon-build --coverage` counts it. No new loader was needed. The packs were not used, because:

- **A pack is a lesson.** Its words become review cards when the learner learns the pack. The packs have themes
  (Al telefono, In cucina) and a giver. Each word has an emoji and an example in four languages, and I spy counts on
  them. A list of 269 verbs, adverbs and expressions has no theme. Spread over the packs, the words would change what
  each pack teaches and how long it is.
- **A supplement is a dictionary.** It holds a word's forms with their grammar, so a tapped guardo, guardami or
  ci vuole finds its word, and one entry covers every form. It can rank a reading first (prima the adverb before
  primo). It has a place for expressions, which a lookup finds around the tapped word.
- **The meanings are written for the learner** (German first, a child's words: schauen, not betrachten). On a node
  with Wiktionary they come before its meanings, which are for adults and often found through English: dammi is "geben
  Sie mir", lavare "Wäsche waschen", bisognare "sollen". The supplement's entry takes the place of Wiktionary's
  reading of the same lemma and part of speech.

**Licence.** I wrote the meanings myself. Nothing is copied from Wiktionary. Its CC BY-SA terms (attribution and
share-alike) apply only to the downloaded dictionary, whose answers carry the attribution. I wrote the forms myself too:
a paradigm for each ending, with the spelling rules (tocchi, cominci, apparecchi), 15 everyday irregular verbs in
full, and 9 more with their irregular past forms (messo, misi; preso, presi). Then I checked them against Wiktionary's build from kaikki.org (the it-lexicon round's
`lani-itlex-work/out/it.json`). Each form and its lemma were compared there. The only forms Wiktionary doesn't list
under the same lemma are these:

- the feminine and plural participles (Wiktionary has arrivata under the adjective arrivato);
- a reflexive verb's forms (under lavare, not lavarsi);
- the elided imperatives va', da', di', fa', sta';
- bel, gran and benissimo;
- ho and ha (separate entries there).

I settled two Slovene meanings with fran.si. Pravopis marks *drevi* "pešaj." (going out of use), so stasera is now
"nocoj, danes zvečer" (https://fran.si/iskanje?View=1&Query=drevi). *Jagenjček* is the standard diminutive of
jagnje (https://fran.si/iskanje?View=1&Query=jagenjček).

## What was built

### The print's dictionary (companion/prints/sheet.ts)

- **The supplement's own meaning in the base comes first**, then the dictionary's meaning of its lemma, then the
  English. c'è is now "es gibt" on a German sheet. On Slovene sheets in German and Italian, 87 and 86 places now show
  the supplement's German or Italian instead of English (vstati: aufstehen, oba: beide, borovnice: Heidelbeere, not
  "see Thesaurus:Heidelbeere"). Slovene sheets in English are unchanged.
- **A pack's noun in the plural with its article** (i cavalli) is found by the pack's `plural`, with the singular
  shown beside it: "i cavalli (il cavallo) Pferd". This works only with the article, because the article says it is
  that noun. Slovene's peči is "to bake", not the plural of peč, and četrti is "fourth", not quarters. Both printed
  wrong when I first tried the match without this rule.
- **An Italian noun with its article** (il treno) finds the supplement's noun treno. A plural (i biscotti) finds
  biscotto and shows it with its article (il biscotto: il, lo, l', la by gender and first letter).
- **Italian forms are compared as the bridge compares them** (lexicon.ts `normalizeForm`): the accent on the last
  letter is kept, so è is not e and lì is not li. Slovene keeps the print's own comparison.

### The supplement (companion/lexicon/it.extra.json)

There are 271 new entries, so the file has 309 (38 before) and 1,136 new forms. All are machine-written and not
reviewed (`"machine_written": true`, `"reviewed": false`, as the file was).

| Part of speech | Entries | Examples |
|---|---:|---|
| noun | 101 | il treno, la fata, l'osso (ossa, ossi), la bici (no other form), il frigo, l'Epifania |
| verb | 73 | essere, avere, andare, fare, guardare, capire (-isc-), lavarsi, sedersi, volerci, andarsene, farcela |
| adverb | 30 | mai, già, subito, proprio, stasera, fa (due anni fa) |
| adjective | 22 | bello (bel, bei), grande (gran), migliore, attento, zitto, solo |
| expression | 21 | c'era una volta, un po', e mezza, che ore sono, al posto tuo, andare via, fare il bagno, basta che |
| pronoun, possessive | 15 | gliele, glieli, gliene (and glielo, gliela), tutti, niente, mio, tuo, nostro, vostro |
| other | 9 | dai!, evviva!, peccato!, basta!, con, senza, prima di, mentre, zero |

- **Meanings**: German, Slovene, English, 1–3 senses each. The first sense is what the sheet prints, cut to about 24
  characters (`shortGloss`). So the plainest sense comes first ("schauen, anschauen"; "fertig machen; enden, aufhören").
  Notes in brackets show on the word card but not on the sheet ("Angst (avere paura: Angst haben)").
- **Forms**: each lemma's forms that the Italian content uses (the course, the grammar book, the Friuli culture, the
  packs, the Italian scenarios), each with its grammar in Wiktionary's style ("first-person singular present",
  "second-person singular imperative, with mi (me)"), a form's slots together ("guardi": second-person singular
  present; first/second/third-person singular present subjunctive). The lemma itself is always there.
- **Reflexive verbs** list their finite forms with the pronoun (si lava, mi siedo, mi sono lavata, si sono vestiti).
  These are expressions, which the lookup finds in the line around the tapped word. The bare forms belong to the plain
  verb: "Gira il frico" is girare, and girino is a tadpole at the pond. Only the joined forms are listed alone
  (lavarsi, siediti, girandosi).
- **Clitic forms** of the infinitive, the imperative and the gerund (guardami, toccarlo, tenendolo, ascoltatemi). Only
  the content's forms are kept. For the short imperatives da', di', fa', sta' and va', only the joined forms that are no
  other word are listed: dammi, dimmi, fammi, vattene, daglielo. Not listed: dalla, dallo, dagli (da + the article),
  stalla (a stable), valle (a valley), fallo (a foul).
- **Pronominal verbs by hand**: andarsene (me ne vado, se n'è andata) and farcela (ce la faccio, ce l'ho fatta).
  volerci only in the third person (ci vuole, ci vogliono), because "ci voglio andare" is volere.
- **Expressions with an apostrophe** are also listed as the line's words join them ("un po", "ce l ho fatta", "c era
  una volta"). The bridge splits a line at the apostrophe, so without these forms the lookup missed "c'era una volta"
  around a tapped "volta".
- **Order**: when two entries share a form and no translation decides between them, the lookup and the print show
  them in the file's order. So expressions, interjections, adverbs, pronouns and nouns come before adjectives and
  verbs: prima the adverb before primo, le fate (fairies) before fare's fate, voglia before volere's subjunctive, dai!
  before dare. The exception is fa: the verb (fa freddo) comes before the adverb (due anni fa).
- **Words the new forms share** with a common word were checked against the content. presto, sole, sei and valle are
  pack words, which rank first. voglia (avere voglia di) and fa (ago) got their own entries. messa is in the content
  only as mettere's participle.

### Generated, then edited by hand

The entries were generated once from a hand-written table (lemma, part of speech, gender, paradigm, the three meanings)
by a script in the round's scratchpad. That script built the forms and scanned the content. After that, the file is
the source. Change an entry by hand, as for the rest of the supplement. When content is added, its new forms of a verb
are found anyway: the lookup's "did you mean" tries the endings (ENDINGS in lexicon.ts), and Wiktionary has them on a
node.

## Results

### The prints

`school-prints --language it --level all --base <de|sl|en> --learner Teo --png`, before and after:

| | German | Slovene | English |
|---|---:|---:|---:|
| words listed, before → after | 998 → 1,431 | 997 → 1,431 | 998 → 1,431 |
| left off for want of a meaning | 462 → 0 | 463 → 0 | 462 → 0 |
| words on a German sheet, A1 / A2 / B1 | 5–22 / 11–26 / 11–30 → 9–29 / 21–33 / 16–37 | | |
| one-page sheets, before → after | 24 → 15 | 27 → 18 | 25 → 17 |
| booklet A1 / A2 / B1, pages before → after | 26/28/36 → 29/32/38 | 26/27/34 → 28/30/38 | 25/29/35 → 28/31/38 |

Every sheet still fits: one or two pages, with fit.js's choice matching the PDF, and no sheet over two pages. The run
fails otherwise. Nine sheets in German and Slovene and eight in English went from one page to two, because the
longer word list no longer fits the one-page layout's side column. In German they are aggettivi, domande, piacere,
participi-irregolari, pronomi-diretti, ci-ne, relative-che, si-impersonale and congiunzioni. In Slovene, futuro,
trapassato and discorso-indiretto take the places of aggettivi, pronomi-diretti and relative-che. Most were 2 to 30 mm
too long for one page. A two-page sheet also has room
for ten check-yourself items instead of five. 29 words listed before are no longer listed, because their meaning
showed that the table already has them (the print leaves out a word whose form the table shows). Slovene sheets in
German and Italian: the same page counts as before (70 sheets in each).

Previews (scratchpad `ux-it-print-meanings/{before,after}/pdf-<base>/<level>/previews/`). Before, 16-piacere in German
was one page: 16 words listed, 13 left off. After, it is two pages with 29 words (i gatti (il gatto) Katze, i ragni
(il ragno) Spinne, il freddo Kälte, pizzicare zwicken).

### The lookup

`lexicon-build --coverage --lang it`: the Italian content a learner meets (196 files, 68,905 words, 4,019 different):

| | before | after |
|---|---:|---:|
| without Wiktionary (a phone): the supplement | 0.9 % of the words met | 25.4 % |
| … with the pack words and the villagers' names | 28.8 % (25.9 % of the different words) | **51.7 %** (42.9 %) |
| with Wiktionary (a node with the download) | 99.2 % | 99.2 % (the same words; the supplement's German first) |

On a phone, a tap on guardami, mai, volta (in "c'era una volta"), lava (in "si lava"), fatta (in "ce l'ho fatta") or
treno used to say the word isn't in the dictionary. Now each opens its word with the German meaning, flagged
machine-written (`gloss_via: claude`).

## Tests

On the branch rebased onto main (main was already under it: 605d5f26, the wave A queue partly landed):

- **Bridge**: `bunx tsc --noEmit -p .` clean. The smoke (`LANI_SMOKE_PORT=9710`, `LANI_SMOKE_DRAMA3_PORT=9728`):
  3,317 checks, none failed. New checks:
  - prints: every Italian unit of A1, A2 and B1 in German, Slovene and English means every word it lists (4,293);
    c'è is "es gibt", guardare "schauen, anschauen", i cavalli "(il cavallo) Pferd", il treno "Zug" on the German sheets;
  - meanings: the repository's it.extra.json validates, each entry with German and Slovene, machine-written. Without
    Wiktionary, guardami is guardare with its imperative; c'era una volta, un po' and ce l'ho fatta are found around
    the tapped word; si lava is lavarsi; gira il frico isn't girarsi;
  - friuli: sono now has the supplement's Slovene (biti), so the gap the tutor hears of and fills (gloss_gaps,
    publish_gloss) is now banco's: the school pack's word, then the fixture dictionary's banco without a Slovene
    meaning.
- `tutor-export --check`: 89 files match the bridge (54 tools, 32 guide topics).
- **Android**: the full unit suite (`testDebugUnitTest` through lani-slot.sh): 3,686 tests in 474 classes, none
  failed (2 skipped).
- **Python**: `python3 -m unittest discover -s tests` 251 tests OK; `-s companion/tests` 142 OK (11 skipped).
- **Prints**: `school-prints --language it --level all` in de, sl and en: every sheet fits (above). Also
  `--language sl --level all` in de and it: the same 70 sheets' page counts as before, 87 and 86 words in the base
  now instead of English.

## Emulator QA

Through `qa-slot` (slot 1) on a synthetic Teo (`make-learner.ts --target it --base de --name Teo`,
`LANI_CULTURE=friuli LANI_CHILD=1 LANI_QA_SETUP_PAIR=it-de LANI_QA_LEARNER=Teo`). The dev bridge had no Wiktionary
(its log: "lexicon it: no dictionary"), as a phone has it. Screenshots: scratchpad `ux-it-print-meanings/qa/`.

- **`it-words`** (new, only when named; `LANI_DEV_DATA=<Teo's dir> LANI_CULTURE=friuli LANI_CHILD=1
  LANI_QA_SETUP_PAIR=it-de companion/bin/qa-slot --no-town --steps it-words`): QA's hook "word:" opens each card as a tap in its line does, and the step
  checks the lemma and the German meaning on the card and in GET /lookup. All six passed: guardami → guardare
  "schauen, anschauen" (second-person singular imperative, with mi (me)); mai "nie"; volta → c'era una volta "es war
  einmal", with una volta below; lava → lavarsi "sich waschen"; fatta → farcela "es schaffen"; treno "Zug". Each is
  flagged "maschinell geschrieben, nicht geprüft". Before (the same lookups with the old supplement): none of the six
  was in the dictionary. A tap is still one tap; what it shows changed.
- **`friuli-school-unit`** with `LANI_QA_IT_UNIT=piacere`: the unit end to end, then its sheet printed from the app
  ("🖨️ Stampa la scheda"). fit.js in the WebView chose as headless Chromium did (one page, density 3). The PDF saved
  through the print dialog lists all 28 words in German, the plurals with their singular (i gatti (il gatto) Katze)
  and the supplement's (il freddo Kälte, pizzicare zwicken, tanto so viel, so viele, l'inverno Winter). The bridge's
  sheet has one word fewer than the generator's (correre), so it still fits on one page in the app.

## For the native check

All of it is machine-written. What a native speaker should look at first:

**German (what Teo reads first), for a German speaker:**
- the verbs' first sense, which the sheet prints: finire "fertig machen", mettere "legen, stellen; anziehen", stare
  "sein; bleiben", sapere "wissen; können", volerci "nötig sein, dauern", bisognare "man muss, es ist nötig";
- e mezza "… Uhr dreißig" (4:30, halb fünf): Italian counts the half from the hour before, German from the hour
  after. The sheet says "Uhr dreißig" to avoid "halb" pointing at the wrong hour;
- maglia "Pulli, Oberteil"; biglietto "Karte, Zettel; Fahrkarte"; giro "Runde (fare un giro: eine Runde drehen)";
  recinto "Gehege, Zaun"; salita "Steigung, Aufstieg"; in ordine "aufgeräumt, in Ordnung";
- da giovane "als … jung war" and stanotte "heute Nacht" (it can also mean last night);
- basta "genug!, es reicht; basta che: Hauptsache" (the unit congiuntivo-espressioni means basta che).

**Italian (the forms and their grammar), for an Italian speaker:**
- the irregular paradigms as written: sedersi (mi siedo, siediti, si sieda), cuocere (cuocio, cuociamo, cossi, cotto),
  dare's historic past (diedi and detti), piovere (piovve), stare (stetti);
- osso's two plurals (le ossa, gli ossi: a dog's bones are usually ossi);
- the meanings of solo (adjective "alone" first: Zia Nives è sola), tanto, molto, tutto and tutti as single entries;
- il primo as an expression (il primo agosto), and fa as "ago" (due anni fa).

**Slovene (the Slovene base), for Jan's wife:**
- the reflexive verbs in the imperfective, as the course's own glosses are (umivati se, oblačiti se, česati se);
  other verbs perfective where that is the dictionary form (pokazati, posoditi, podariti);
- panino "sendvič, žemlja"; biglietto "voščilnica, listek; vozovnica"; stanotte "nocoj; to noč"; e mezza "… in
  trideset (pol petih)"; c'era una volta "nekoč je bil(a)";
- register and naturalness are hers to decide; nothing here is graded.

## Voice

Nothing here is voiced: the supplement is read on the word card and the sheet. A voice build would read none of it.
Clips of the 271 lemmas, if someone wanted them, would be about 1,900 characters. No ElevenLabs credits were spent.

## What's left

- **The short imperatives' joined forms that are another word too**: dalla, dallo, dagli, falla, fallo (in the unit
  imperativo-pronomi's table). On a phone, a tap on them finds nothing, or Wiktionary's preposition on a node. Telling
  "Dalla a me!" from "dalla finestra" needs the line, which the lookup can't read yet.
- **Eight or nine sheets per base went from one page to two.** If one page matters more than the complete word list, fit.js
  could get a density that shrinks only the word box. But a 7-year-old needs the letters big, so this round didn't add
  it.
- **The verbs' forms are the content's.** Forms in content written later fall back on the lookup's "did you mean" and
  Wiktionary. Running the round's generator again, or adding the forms by hand, keeps the supplement current.
- **German's and English's courses** (outlined in A1, planned) will leave their own words off the same way. The same
  supplement (de.extra.json, en.extra.json, with the same loader) would carry their meanings.
- **The download** (it-lexicon.md): a phone still has no Wiktionary until `it.json.gz` is published at an https://
  address.
