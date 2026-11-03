# Built: a tap in Italian means the word (supplement-it, 2026-11-02)

Wave D, section 7 (followups-11.md). A phone has no Italian dictionary unless one is downloaded: a tapped word means
something only when a word pack, a villager's name or the supplement (`companion/lexicon/it.extra.json`) knows it. On
main that was 55.0 % of the words met and 43.6 % of the different words: **il, la, e, un, di, che, non had no meaning**.
Teo meets this every day. German and English had this round in supplement-coverage.md (99.9 % and 99.8 %); this is the
same job for Italian, with its tool and its rules. All of it is machine-written and not reviewed (the file's meta says so).

## Already done before this round

**The sheets' missing words (the brief's "9–22 a unit") were already there.** it-print-meanings had given them meanings: on
main no sheet of A1, A2 or B1 in German, Slovene or English has a "no meaning" note (`school-prints --language it --level all
--explain`: 0 in each base). it-b1-a.md's sentence was written before that merge. The sheets are still affected, see below.

## What

### How it was measured

`companion/bin/lexicon-build --coverage --lang it` (the tool is unchanged), no Wiktionary extract, as a phone has it. It counts
the culture, the packs, scenes, scenarios, modules and the grammar book: 76,468 words met, 4,282 different. It doesn't count
the course, so the course's words were measured as well, by a scratch port of the same count (17,512 words met, 1,849
different), and went into the tables too.

### The supplement: 378 → 1,873 entries

1,495 new entries (3,477 forms), 201 older ones extended with 135 forms:

| Part of speech | New | Examples |
|---|---:|---|
| noun | 591 | ragazzo, tesoro, monte, parola, cuore, gregge, fuliggine, the grammar's terms (congiuntivo, trapassato) |
| verb | 332 | mangiare, piacere, vedere's forms, cercare, alzarsi, arrabbiarsi, riprendere, proporre |
| adjective | 251 | bravo, stanco, bianco, buono, famoso, the -issimo forms |
| name | 96 | Gorizia, Trieste, Udine, Isonzo, Carso, Orione, Sirio, Moro the dog, Baio the horse, Bianca the sheep |
| interjection | 65 | ah, oh, ehi, hm, shh, uffa, bzz, din, bum, bau, coccodè |
| adverb | 48 | non, dove, così, ecco (eccolo, eccola), giù, qua, quasi, invece, magari |
| number | 34 | undici, dodici, cinquanta, mille, secondo |
| pronoun | 29 | io, tu, lui, lei, noi, voi, loro, mi, ti, lo, la, li, le, gli, ci, vi, si, ne, me, te, chi, che, cui |
| conjunction | 15 | e, o, ma, che, se, perché, però, quindi, siccome, finché |
| determiner | 14 | il, la, un, una, questo, quello, altro, stesso, qualche, poco |
| preposition | 12 | per, tra, fra, fino, verso, contro, entro, secondo, oltre, lungo il |
| expression | 8 | ninna nanna, a galla, re magi, via lattea, the endings -issimo, -issi |

- **The way they were made**: entries written as tables (`companion/lexicon/tools/it_cov_*.txt`: lemma, part of speech, gender, a
  paradigm code, the three meanings), and `it_cov_build.py` makes each word's forms with their grammar in the file's style
  ("first-person singular present"), keeping only the forms the content has. `paradigm.py` gained 74 irregular verbs (vedere,
  bere, cadere, chiudere, scrivere, leggere, piacere, rimanere, porre …), compounds (`v:ri+prendere`, `v:sor+ridere`) and the
  plurals' spelling rules (barca, barche; stanco, stanchi; occhio, occhi; pioggia, piogge). Every meaning written, none copied
  from Wiktionary. Wiktionary's build (kaikki.org, the it-lexicon round's file, not in the repository) was used for two
  things only: which lemma a word falls under when listing the work, and a check of the forms (`--report`). No form it files
  under another lemma was wrong: they are the feminine participles it files under an adjective, and the -issimo forms.
- **The articles**: `il` (il, lo, i, gli) and `la` (la, le) mean "der, die, das – männlich" and "– weiblich": Italian's gender
  isn't German's (il sole is die Sonne). Slovene has no articles: "določni člen – moški spol". An elided l' or un' is never
  tapped alone: a tap on l'acqua is acqua.
- **The contractions** (del, nel, sul, al, dal) keep their old entries' forms and get the word itself at last: `di`, `a`, `da`, `in`,
  `su` had their contractions and no bare form, so a tap on di met nothing. `con` got col and coi.
- **The clitics**: each with its own meaning (mi: mich, mir; lo: ihn, es; gli: ihm, ihnen), ci and vi also before lo (ce l'ho),
  and a verb's joined forms from its paradigm (aiutami, portatela, svegliamolo).
- **The verbs' forms**: the older entries' forms again on today's content (`it_cov_03_old.txt`; vedere had only vederlo and
  vederti; six verbs lost the bracket that explained their one joined form, as the grammar says it now). A reflexive verb by
  its pronoun as before (si alza: alzarsi), the plain verb beside it (alza: alzare).
- **The packs' words where the pack lacks a form** (137 entries, `=pack`): bianca, bianchi, mangia, dorme. Each has the pack's own
  meanings, so a tap finds the pack's word, its card and its prompt.
- **The sounds with a meaning**: hm "nachdenken", shh "sei leise", brr "wie kalt", bzz "eine Biene", din "Glocke", their drawn-out
  spellings as forms (brrr, ssst, oooh, fiiiii).
- **Friulian and Slovene said to be so in every base**: scarpets "friaulisch: Samtpantoffeln", Pieri, ribolla; the old fogolâr,
  pignarûl, cramaro, cjarson as they were; Slovene guba "slowenisch guba: die Falte", osmiza, Triglav.

### Every prompt its own

A looked-up word becomes a card whose prompt is its meanings in the base without their brackets. `it_cov_build.py --prompts`
compares each new entry's whole prompt (articles and "to" left out) in sl, de and en against the Italian packs and the
supplement: **0 clashes**. Where a base has one word for two, the meaning says which, outside the brackets: camminare "gehen – zu
Fuß", "Seite – im Buch", "krilo – oblačilo", tu "you – one person" beside voi "you – several people". An entry for a pack's word has
the pack's meaning: its card is the pack's. A few packs' words have an entry of another part of speech with a meaning of its
own (piatto flat beside the pack's plate, vicino the neighbour beside near, piacere to please beside the greeting).

### What a tap shows first (bridge/src/lexicon.ts)

Two lookup rules this size of supplement needs; both apply to every language:

- **A supplement's name comes first only when the line writes it as one**, as Wiktionary's already did: "Questa è Bianca" the
  sheep, "una pecora bianca" white.
- **A tapped form of another word isn't shown as a pack's word spelt alike.** "Mi piace" showed the pack's greeting piacere (freut
  mich), "una pietra piatta" the plate. When the tapped word is only a form of a lemma, the supplement has a reading of it,
  and no reading shares an English word with the pack's meaning, the pack's word is left out. The word itself (Piacere!) keeps it.

The order of a form's readings is the file's where the line's translation doesn't tell: an article before the pronoun, se the
conjunction before si's se, leggere the verb before leggero's plural, aiuto the noun before aiutare; chiusa the sluice, fermo the
adjective and passato the tense come after the verb (`LATE`, `BEFORE` in the build). `lungo` the preposition is only found in
its phrase (lungo il fiume), so a tap on lungo elsewhere is long. `conti` and `certi` are their own lemmas for the same reason.

### The sheets

The sheets read the same supplement, and take its entry of a word before a pack's, so printing every level in every base before and
after (`school-prints --language it --level all --html-only`) was part of the work. Fixed: a pack's noun listed bare isn't a new
adjective spelt alike (`Dictionary.found`, prints/sheet.ts: plurale's piatto is Teller, not flach). What still changes, all of it
checked, in 30 of the 54 units:

- **Two were wrong and are right now**: negazioni's spesso was "dick", is "oft"; the unit piacere's piacere was "freut mich", is "gefallen, schmecken".
- **Eight words that were left out because the table shows them are listed** (comparativi's alto, buono, forte, freddo; aggettivi's
  nero, vecchio; periodo-ipotetico's giovane; discorso-indiretto's stanco), as adjectives the rule lists: a pack's word without a
  part of speech had hidden them. Every sheet still fits: `school-prints` to PDF for all 54 units and the three booklets (A1 29
  pages, B1 38) in German, Slovene and English, which fails when a unit doesn't fit on two pages, exits clean.
- **A word's other wording**: correre "laufen, rennen", cadere "fallen, hinfallen", piano "langsam, leise", pronto "fertig, bereit",
  rotto "kaputt, zerbrochen", rompere "kaputt machen, brechen", English volare "to fly through the air".
- **Nouns another language's pack meant** now come from the supplement, so they show Italian's gender and no German article:
  "la canzone f Lied" for "la canzone das Lied"; also ballo, burro, drago, pagliaccio, ragazzo, barba, ciotola, fame, mucca, pera.

## Results

`lexicon-build --coverage --lang it`, no Wiktionary, a tap means the word …:

| | Before | After |
|---|---:|---:|
| of the words met | 55.0 % | **99.9 %** |
| of the different words | 43.6 % | **99.3 %** |
| in the learner's base (sl, de, en alike), of the words met | 53.7 % | **99.9 %** |
| in the learner's base, of the different words | 43.2 % | **99.3 %** |
| different words left | 2,417 | 29 |
| the course (scratch count), of the words met | 59.9 % | 99.6 % |
| the course, of the different words | 59.1 % | 97.2 % |

**What stays out, and why** (29 words, 77 tokens, in `--coverage`; 77 words, 133 tokens with the course):

- **Wrong forms the lessons offer on purpose** (a child corrected in the line, or a graded choice): uovi, zampi, bosci, cannelli,
  frittelli, fungi, mane, meli, mieli, pecori, vaseti, vedito, venirò, vuoleva, diavola, pupazza, secchiella, passimi; in the
  course mangierei, mangiarei, averò, sarebbi, scrivuto, aprito, spegnuto, rompito, chieduto, bevito, fando, dammilo, ventiotto,
  stancissimo … A tap on one says it isn't in the dictionary, which is right: giving it a meaning would call it a word.
- **Letters and pieces**: g, l, c, s, w, z, gn (from "+ s + consonante, z, gn, ps"), ca-val-lo and c-a-v-a-l-o spelt out.
- **Not Italian**: "be" in an English note, "dell" from a story's id.

## Tests

Main had not moved past this branch's base (45924165), so the merge had nothing to bring in.

- **Bridge**: `bunx tsc --noEmit -p .` clean; `bun build --no-bundle` on the three smoke files changed. The smoke (`LANI_SMOKE_PORT=9770`,
  `LANI_SMOKE_DRAMA3_PORT=9788`): all 3,514 checks passed. New checks: la the article and, by the line's translation, the pronoun; che, non, il, un, per; a contraction
  as its preposition (nella: in + la); a verb's forms, irregular ones too (vedo, vide, piacciono); a sound, a Slovene word said to be one; lungo long, and along in its
  phrase; a supplement's name first only as a name; piace not the pack's greeting; the sheets' meanings kept (piatto Teller, lungo lang, certo klar) and the two
  corrected (spesso oft, piacere gefallen). One older check changed: mangio's grammar is the supplement's now, so nothing of Wiktionary's is shown and it has no attribution
  (banco's check still tests the attribution). The file-by-file check validates every entry: `validateExtra` leaves none of the 1,873 out.
- **The step check's `partial`**: Italian's dictionary is still only a supplement (`dictionary` and `partial` both true: a new smoke check, vede by its lemma, mucche and
  mucchina by the stem, cane never for mucca, a phrase never). `check-steps` on friuli-projects-a's and -b's branches, unmerged, with this file through `LANI_EXTRA_FORMS`:
  ok, the same 13 projects, 78 steps and 234 dialogs with it as without it. `check-culture friuli`: ok. `bun test/prompt-clashes.ts`: the packs are untouched.
- `tutor-export --check`: 89 files match the bridge (54 tools, 32 guide topics).
- **Python**: `python3 -m unittest discover -s tests` 253 tests OK; `-s companion/tests` 145 OK (11 skipped).
- **Android**: no app code changed. The full unit suite (`testDebugUnitTest` through lani-slot.sh): 3,785 tests in 485 classes, none failed (2 skipped).
- **Prints**: `school-prints --language it --level all` to PDF in de, sl and en: every sheet fits, no "no meaning" note.

## Emulator QA

Through `qa-slot` (slot 2), a synthetic Teo (`make-learner.ts --name Teo --target it --base de`, `LANI_CULTURE=friuli LANI_CHILD=1 LANI_QA_SETUP_PAIR=it-de`), the dev bridge without
Wiktionary, as a tablet has it. The step `it-words` opens each card as a tap in its line does (QA's hook "word:") and checks the lemma and the German meaning in GET /lookup and on the
card; each flagged "scritto dalla macchina, non rivisto · maschinell geschrieben, nicht geprüft". A tap is still one tap; what it shows changed. Screenshots: scratchpad `ux-supplement-it/shots/`.

| Word | Card | Before |
|---|---|---|
| la (La casa di Nonna Rosa è piccola.) | la · f., "der, die, das – weiblich" (feminine singular); then la the pronoun, "sie – eine Person oder Sache" | not in the dictionary |
| che (Penso che Moro dorme.) | che, "dass"; then the pronoun | not in the dictionary |
| vedo (Vedo la luna sul monte.) | 👀 vedere, "sehen" (first-person singular present), the pack's card and example | the pack's word wasn't found by this form |
| nella (Il gatto dorme nella cesta.) | in, "in (mit Artikel: im, in der)" (in + la) | known already |
| sul (Il libro è sul tavolo.) | su, "auf (mit Artikel: auf dem, auf der)" (su + il) | known already |
| hm (Hm, non lo so.) | hm, "hm (nachdenken)" | not in the dictionary |

## How to use

- A learner of Italian, on a phone without Wiktionary: tap any word in a scene, a story, a reading, a sheet or a villager's line; the card
  shows its lemma, its grammar and its meaning in the learner's base, flagged machine-written, and "➕" adds it.
- To add words later: append to the file by hand, in its format; `companion/bin/lexicon-build --coverage --lang it --all` lists the words
  no entry knows, the most frequent first. Keep each new meaning's prompt its own. `it_cov_build.py` can be run again from a
  file's earlier version with new table lines; `--prompts` is the clash check, `--report` the homographs and the form check.
- QA: `LANI_DEV_DATA=$(cd companion/bridge && bun test/fixtures/make-learner.ts /tmp/teo/data --name Teo --target it --base de)
  LANI_CULTURE=friuli LANI_CHILD=1 LANI_QA_SETUP_PAIR=it-de LANI_QA_LEARNER=Teo companion/bin/qa-slot --no-town --steps it-words`.

## Voice

Nothing here is voiced: the supplement is read on the word card and the printed sheets. No ElevenLabs credits, no voice build.
Printed text changed only on sheets, which have no clips. **0 characters.**

## Native check

Everything here: about 1,500 new entries in three bases, machine-written. What most needs a native eye:

**Slovene (Jan's base, for Jan's wife; nothing here is graded):**
- the grammar terms: passato prossimo "sestavljeni preteklik" (descriptive only), congiuntivo "konjunktiv, vezni naklon", imperfetto "imperfekt",
  gerundio "gerundij", partitivo "delni", remoto "daven, oddaljen", trapassato "predpreteklik", and the articles "določni člen – moški spol";
- verbs: spuntare "pokukati, prikazati se", gorgogliare "žuboreti, brbotati", planare "jadrati", stendere "razgrniti, razvaljati, obesiti", compiere
  "dopolniti (leta)", spennellare "premazati", somigliarsi "biti si podoben", pestare "stopiti (na), tolči", scolpire "klesati", travestire "našemiti",
  godersi "uživati v", the notes "teči – o vodi" (scorrere), "pometati – z metlo" (scopare);
- words: acchiapparella "lovice", cuccagna "dežela izobilja" and "drog z nagradami na vrhu" (a description), coppo "korec", calante and crescente
  "pojemajoč, naraščajoč", gibboso "več kot pol poln", scossone "sunek, tresljaj";
- places and stars: Gradež, Videm, Nadiža, Trg Evrope for the Piazza Transalpina, Volar, Voznik, Labod, Plejade, Regul, Poluks; the sounds (cak, čof, trk, kra).

**German (Teo's base):**
- presentarsi "sich vorstellen – mit Namen", sposare "trauen, heiraten", compiere "(ein Alter) erreichen, vollenden", sbattere "schlagen, anschlagen", calciare
  "schießen, treten", planare "gleiten, schweben", gibboso "dreiviertelvoll", frittella "Krapfen, Schmalzgebäck", cesta "großer Korb", borgo "Dörfchen, Marktflecken";
- the articles' "der, die, das – männlich", and the sounds (summ, tschilp, schnarch, plopp).

**Italian (the word learned):**
- **`scopare`** is written "to sweep", as a child's line uses it ("ti aiuta a scopare"), but today it is also vulgar slang: that line in Italian is worth a look
  (spazzare is the safe word). It is content, so it stays for Jan or a native speaker to decide.
- `inciampare` ("la parola inciampa") is rare; inciampare is what the line has.
- the forms' grammar labels, and the sense order of a form with several readings.

**English (a base):** the short glosses made unique ("to fly through the air", "you – one person").

## What's left

- **A word card's grammar is in English** on a German or Slovene card ("feminine singular", "first-person singular present"), as every
  language's has been; the smoke pins Italian's wording. Translating the labels is a change to the card, not this file.
- **The native check** (above) and the `scopare` line.
- **A form with two readings and no translation to tell**: "sei" shows the pack's sechs before essere; "brava" the pack's brav, artig before "gut gemacht". The line's
  English settles it whenever the app sends it.
- **A pack's word with its article and the supplement's without** ("l'anno", "anno") are two entries on a tap, one meaning, as in German.
- **Forms of content written later** fall back on the lookup's "did you mean"; `lexicon-build --coverage --all` lists them. Friuli's project steps and parties
  (other branches of this wave) will add words: `check-steps` on friuli-projects-a's and -b's 13 projects passes with this supplement; their new words are for the next count.
- `lexicon-build --coverage` doesn't count the course, and `dialectWords` counted il and si as Friulian ("friulano il frico"); neither was changed.
