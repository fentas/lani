# Built: the forms the case lens knows nothing of (lens-unknown-forms, 2026-11-01)

The case lens (companion/README.md, "The case lens"; case-lens.md, case-lens-2.md) knew nothing of 1,021 forms of the
content: no reading as a word with a case, as a verb or as anything else. Most were names, interjections and verb
forms, some nouns of stories. A tap on one showed no case row, or a wrong one guessed from its ending (*Juhu!* read as
a name's dative, decided; *Super!* as a nominative of address). This round gives them readings: rules for what the
dictionary has only as something else, and entries in the dictionary supplement (companion/lexicon/sl.extra.json) for
the rest, each settled against SP2001 and Sloleks with its URL. Everything Slovene here is machine-written (Claude) and
waits for the native check below.

**The numbers** (`bun test/case-lens-gaps.ts`, the smoke's 5,654 lines of scenes, stories, readings, grammar pages and
packs): the forms the lens knows nothing of, **1,021 → 31** by case-lens-2's count (2,851 → 678 words; 22 of the 31 are
the village's names, Jan, France, Micka, which the lens is given at the bridge), **9 by the lens's own count** (15
words: what it knows at the bridge, `CaseLens.unknownWords`), 11 with the course units' 868 lines too. Words with a
case 15,137 → 15,305, decided or likely 89.7 → 91.2 %. The labelled sets as before or better, none wrong and decided;
a fourth held-out set of lines with a formerly unknown form: 16/64 right on the base (2 wrong and decided), 62/64 now
(none wrong). The supplement 806 → 1,449 entries.

## How to use it

- **A word's card** (a tap on a word anywhere): a story's name declines and has a meaning (*Krpana*: "Krpan · lastno
  ime · Martin Krpan, the strongman of Levstik's tale", the case row "tožilnik, ed.", the object of *zapomnil*); an
  interjection or a particle says what it is, with no case row (*Juhu*: "medmet · interjection: yoo-hoo, hooray"); a
  verb's form has its lemma and meaning in the learner's base (*zamuja*: zamujati, "sich verspäten" for a German base).
- **The sentence's grammar** (a long press on a line): the same rows, from the same lookups.
- **The measure**: `cd companion/bridge && bun test/case-lens-gaps.ts` lists the forms the lens knows nothing of, by
  frequency, with their kind and a line each is in (`--all` every one, `--units` the course units' lines too,
  `--kind name|sound|foreign|word`, `--lexical` case-lens-2's count). The smoke keeps them under 150.

**Tap counts.** Unchanged: a word's card is 1 tap, the sentence's grammar 1 long press. What changes is what they say:
what a card says, before (this branch's base) and after, for the QA step's words (the lookup and the lens):

| word, in its line | before | after |
|---|---|---|
| *Krpana* (Cesar si je zapomnil Krpana.) | no entry; the accusative, "po pravilnih končnicah" | Krpan, lastno ime, "Martin Krpan (the strongman of Levstik's tale)"; the accusative, the object of zapomnil |
| *Juhu* (Juhu! Najboljši zmaj na svetu!) | no entry; **the dative, decided** (read as a name by its ending) | juhu, medmet, "yoo-hoo, hooray"; no case row |
| *Super* (Super! Kaj kuhate?) | **the nominative of address, decided** | super, pridevnik; no case row |
| *vseh* (… najboljša izmed vseh.) | the genitive, by its form | the genitive, by izmed |
| *Mickina* (Mickina potica …) | nothing | the nominative, a possessive of Micka's name |
| *zamuja* (Vaša ura zamuja?) | nothing (the card: not found) | zamujati, "to be late" (sich verspäten, essere in ritardo) |
| *oči* (Kekec pa ima odprte oči.) | no case row | the accusative plural, the object of ima |

## What was built

1. **The measure, in the repository** (`companion/bridge/test/case-lens-gaps.ts`). case-lens-2 counted the content's
   unknown forms with a script in its scratchpad, lost when /tmp emptied; it was recovered from that session's
   transcript and reproduces its count exactly on this branch's base (5,654 lines, 1,021 forms, 2,851 words). It adds
   each form's kind (a name: never written small, capitalized after a word; a sound: no vowel or a letter three
   times; a word of another language; a word of the language), `--units` for the course units' 868 lines more, and the
   lens's own count beside case-lens-2's: `CaseLens.unknownWords(line, names)` (bridge/src/case-lens.ts) gives the
   words of a line the lens knows nothing of, a guess by the ending aside, with the village's names the bridge gives
   it (case-lens-2's count calls *Jan*, the learner, unknown: 575 of its 2,851 words). The smoke counts by it and
   keeps the content under 150 forms.
2. **Readings for what the dictionary had as something else** (`bridge/src/case-lens.ts`, `lexical`, `byEndings`,
   `tokens`):
   - a comparative or a superlative Wiktionary names without its case (*boljši*, *najlepši*) by its ending, and its
     other forms (*lepša*: *lepši*; *najstarejša*: naj- before *starejši*; *pametnejša*, *toplejši*: -ejši of an
     adjective the dictionary has); after a soft consonant an adjective's neuter singular ends in -e (*lepše*,
     *večje*), not -o;
   - a past participle as an adjective (*zaprt*, *rešen*, *zaklenjena*), a verbal noun as a neuter noun (*spanje*,
     *pečenje*), a converb with no case (*hodeč*, *prišedši*), the supine of a dictionary verb as its infinitive; also
     of a verb the forms book has a table of, whose finite forms stay the table's alone (so *bije*, biti's homograph
     to beat, stays out: see "What's left");
   - a possessive adjective of a noun or a name the dictionary has (*cesarjevim*: cesar; *medvedove*; *babičino*:
     babica; *materin*; *Krpanova*, *Zlatorogovih*) or of a village's name the bridge gives (*Tonetovo*, *Markovega*,
     *Mickina*, *Nejčeve*), by its ending;
   - a name Wiktionary lists without its grammar (*Kekec*) as its nominative; an adjective's other spelling (*mehak*)
     as its dictionary form; a capitalized word inside a sentence that has a name's reading as the name (*Sivko pa
     počasi*: not sivka's accusative, lavender);
   - what has no case and no verb reading (*ki*, *dvajset*, *super*, *všeč*, *dvoje*, a number of tens and ones,
     *petindvajset*) is what the dictionary says it is, with no row. Before, a capitalized one at a line's start was
     read as a name by its ending: *Super!* a nominative of address, *Juhu!* a dative, *Četudi* a dative, *Nikar* a
     subject, all decided;
   - a sound drawn out is its word (*Jaaa*: ja; *Antooon*: Anton; *brrrr*: brr).
3. **The dictionary supplement** (`companion/lexicon/sl.extra.json`): 643 new entries (7,218 forms) and 78 of its
   entries completed (1,123 forms added: a verb a unit had entered with one form and no grammar, which the lens read
   nothing from, now with its whole paradigm):
   - 178 verbs, 153 nouns, 120 adjectives, 16 numerals, 6 pronouns with every standard and variant form Sloleks 2.0
     lists and its grammar; the comparatives and superlatives of the adjectives Wiktionary has, under their positive
     (dober: boljši, najboljši; velik, visok, lep, star …); a cardinal's oblique forms (devetih, dvajsetimi), its own
     form a number with no case;
   - 54 names and 3 demonyms, singular, with their gender and a gloss of who or what each is (Krpan, Zlatorog, Kekec,
     Pehta, Samol; Triglav, Trenta, Muljava, Peca; Primorsko, na Primorskem); a person's or an animal's accusative is
     its genitive (Sloleks's Zlatorog is a brand's, inanimate); the names Sloleks lacks (Lambergar, Pehta, Pegam …)
     by the regular endings, as the content declines them;
   - 115 words with no case: 44 interjections (SP2001's medm.: ha, joj, juhu, pst, brr, hm), 62 adverbs, conjunctions
     and particles (letos, narobe, preden, ter, nikar, nikalno), the spoken forms the B2 units teach, labelled spoken
     (below), Kekec's folk rhyme *najbolja*, the Italian and German a guest says (allora, grazie, perfetto, wunderbar);
   - what Sloleks lacks, said so in its source: frtalja (SP2001 frtálja) by a feminine noun's regular endings,
     potičnik and sladkosned by a masculine's, mlatev, gregorjevo and florjanovo by SP2001's heads, premočan by
     močen's forms with pre-;
   - every entry's source: its SP2001 head (the homograph of its part of speech, with its labels) and its Sloleks
     headword, with their URLs (614 cite SP2001, 536 Sloleks). The file is 1.0 MB (was 0.3 MB; the APK bundles
     companion/lexicon).
4. **The trigger table** (`companion/grammar/sl/data/case-triggers.py`, its JSON made again): *izmed* and *glede* with
   the genitive (*najboljša izmed vseh*; *glede popravila*), beside *zaradi, namesto, razen*; *midve* and *vidve*
   beside *medve* and *vedve* (the dual's feminine), and among the dual's words.
5. **The forms book's animate list** (`companion/lexicon/sl.forms.json`): the 23 new person nouns (janičar, potepuh,
   tihotapec, iskalec, lenuh, sinko, zaspanec, Benečan, Ljubljančan, Primorec, argonavt …): a person's accusative is
   its genitive also where a form is read by its endings.
6. **The smoke** (`test/smoke/case-lens.ts`): 14 lines of this round's rules (a possessive, a comparative, a
   participle, a verbal noun, izmed, vidve, a number in one word; no case for *Super!*, *Jaaa!*, *Hodeč*), and the
   content's unknown forms under 150.
7. **QA's step `lens-unknown`** (only when named; `companion/bin/qa`): the sentence's grammar of *Cesar si je zapomnil
   Krpana.* and of *Mickina potica je najboljša izmed vseh.*, Zlatoroga's card, and Juhu's card with no case row,
   saying *medmet*.

## Choices, and why

- **Rules where the dictionary has the word as something else, entries where it has nothing.** A comparative, a
  participle, a possessive are regular: a rule reads every one, also those the content will have tomorrow; their
  readings are marked `derived` ("〰 po pravilnih končnicah" on the card), as the lens's other readings by ending are.
  A name, a verb's form, a noun are not: those are entries with their Sloleks forms.
- **A word with no case says what it is, not nothing.** An interjection, a particle, *ki* or *dvajset* gets no case row,
  but the lens no longer guesses it a case from its ending, and the card shows its part of speech (*medmet*, *členek*,
  *števnik*) and meaning. Unresolved over wrong, as case-lens-2 chose.
- **Names in the supplement, not in a list of their own.** The supplement is where a word is added (case-lens-2), the
  lookup shows a name's meaning from it ("Krpan · lastno ime · Martin Krpan, the strongman of Levstik's tale"), and the
  lens reads its forms as any entry's. The villagers' names stay the cast's (the bridge declines them,
  addressee.ts): the measure counts them as the lens knows them at the bridge.
- **A name's singular only.** Sloleks gives a name's plural too; *Krpanov* (its genitive plural) would then be read
  before the possessive it also spells.
- **Sloleks's non-standard forms left out**, its variants kept, as before. A spoken form the content uses on purpose
  (the B2 units' govorjeni-jezik: *zdej*, *tud*, *kva*, *ful*, *fajn*, *luštno*) has an entry whose gloss gives the
  written form and whose source says it is spoken: never a mistake (it is a reading, nothing grades by it).
- **SP2001's homograph of the word's part of speech.** fran.si's search finds every homograph (*há 1*, the hectare's
  symbol; *há 2*, the interjection); the source cites the one whose label is the entry's (medm., prisl., vez.), and a
  name only where SP2001 has the name itself (it has *Kékec* and *Trénta*, not *Triglav* or *Bled*: their sources cite
  Sloleks alone).
- **bije left unknown on purpose**: biti, to beat, is a homograph of biti, to be, whose forms book table the lens reads
  alone; an entry would make *ura bije* a copula's line.

## Accuracy

`bun test/case-lens-eval.ts <gold|heldout|heldout2|heldout3|heldout4|trainer> -v`; the smoke checks each. Before:
this branch's base (3abcd143); after: this branch. "Wrong and decided" is a wrong case said as sure.

| Set | Items | Before | After | Wrong and decided before → after |
|---|---|---|---|---|
| labelled (built on) | 291 | 284/285 | 284/285 | 0 → 0 |
| held-out 1 | 136 | 134/136 | 135/136 | 0 → 0 |
| held-out 2 | 104 | 101/104 | 102/104 | 0 → 0 |
| held-out 3 | 89 | 82/87 | 82/87 | 0 → 0 |
| the case trainer's bank | 324 | 323/324 | 322/324 | 0 → 0 |
| **held-out 4, new** | 64 | 16/64 | first run 60/64; now 62/64 | 2 → 0 |

**Held-out 4** (test/fixtures/case-lens-heldout4.json): 140 content lines drawn at random (seed 20261101), each with a
form the lens knew nothing of on the base, one form each; the 64 whose form has a case in its line labelled by hand
before any lens ran on them (committed before the words' entries). Its one honest run with the entries: **60/64, none
wrong**; two of its four open (*Zdaj ste starejši*, *Ta je pa še večji*: a feminine dative kept beside the nominative)
were then fixed by a rule (an adjective alone is no free dative), so 62/64 now. Open: *Lovski blagor!* (nominative or
accusative, no verb) and *kar je Vido razjezilo* (*kar*, a relative, has no case row).

The trainer's bank lost one: *Kokoši so znesle deset jajc.* was the likely nominative while *znesle* was unknown (a
guessed l-form, no object); with znesti known and its transitivity unknown to the table, the subject and the object
both stay open (no wrong case). *Glej, sledi medveda!* was open before too.

**Over the content** (every line's answers before and after, 6,522 lines with the units'): 95 words whose decided case
changed to another were read one by one; nearly all were wrong before (*Sivko* a decided accusative some 30 times,
*iz sosednje vasi* a dative, *Ime mu je Muri* a dative, *Pokrajculja* a genitive, *Benečani* and *Argonavti* datives,
*Moj konjiček Sivko nima noge* an object). The new ones found wrong were fixed before committing: *pod goro Peco* and
*janičarja Jurija Kozjaka* (the apposition rule), *Zato mu rečemo Triglav* (a name given), *Star človek malo spi* (15
lines: malo read as mali's neuter), *trdno zavežeš*, *Točno toliko*, *Pritisni močno* (adverbs), *Kateri dan je
florjanovo* (a feast day's noun, now an entry), *Kaj jima rečem?* (the name-given rule kept off a word with other
readings). 798 words newly decided: a random 60 were read, all right but *Pod goro Peco.* and *Deska je točno meter in
pol*, both fixed. The lens takes 0.44 → 0.63 ms a line (warm, the content's lines, on this busy host): a larger
dictionary and the new rules' lookups; the smoke allows 5.

## The sources, and how they were read

SP2001 on fran.si (`FilteredDictionaryIds=134`) for each word's standard form, part of speech and labels; SSKJ²
(`FilteredDictionaryIds=133`) for the spoken ones (*pog.*); Sloleks 2.0 (viri.cjvt.si/sloleks) for the paradigms: its
search for the lemma, then the headword of the entry's part of speech (a name's: the proper noun of its gender), its
standard and variant forms (*nestandardno* left out), each form's tags (JOS) turned into the dictionary's grammar
strings: a noun's case and number, an adjective's degree, case, gender and number (its definiteness where the string
stays under 160 characters), a pronoun's and a numeral's, a verb's person, number and tense. 1,910 requests (722 to
fran.si, 1,188 to Sloleks), every one answered (200), 16:56 to 19:31. One request at a time, 4 s apart, each page cached
in the session's scratchpad (sl-b2-b's cache read first, so nothing it had was fetched again), every request logged with
its status; none refused. What was read from them is kept in the repository: every new entry's `source` names its SP2001
head (the homograph of its part of speech, with its labels) and its Sloleks headword, with their URLs.

The work tables (each lemma, its part of speech, its meanings in English, German and Italian) and the builder that
fetched, read and wrote them lived in the scratchpad; the entries they made are the supplement's lines, and a later
change edits those lines, as before.

## What's left

**The 9 forms the lens still knows nothing of** (15 words), each on purpose or out of reach:

| form | words | why |
|---|---|---|
| *bije* (ko ura bije) | 7 | biti, to beat: a homograph of biti, to be, whose forms book table is the lens's only reading of biti; an entry would make *ura bije* a copula's line. Needs a lemma of its own the lens tells from biti. |
| *pojdeš* | 1 | iti's future beside *bom šel* (SP2001: "bom šèl in pójdem"); the forms book's iti has no future slot, and the lens reads iti by its table alone. A future slot in the table would show in the forms cards too. |
| *Brot* | 1 | German, quoted (Kako se reče 'Brot' po slovensko?). |
| *C*, *I*, *N*, *t*, *j*, *n* | 6 | °C, and letters spelled out (K-o-n-j, T-I-N-E). |

By case-lens-2's count 22 more: the village's names (Jan, France, Micka, Ančka, Tone's forms) and their possessives,
which the lens is given at the bridge; and *schön* of *Danke schön*, which has an entry (the dictionary writes it
*schon*; that count looks the raw form up). The units add *-i* and *-ov-* (a page's endings, quoted).

**Still not decided** (no wrong case): *Lovski blagor!*; a relative *kar* (no case row); *Kokoši so znesle deset jajc*
(the count phrase isn't taken for the object); *Sivko pa počasi* names its reason "address" where the verb is left out
(the case is right); a noun the dictionary gives no gender (*meter*) agrees with any adjective; the sentence's grammar
shows *si* of *zapomniti si* as biti's second person (the dictionary's reading, not the lens's).

**For later**: the possessive and comparative rules read what the dictionary has only as something else; a word the
content gains tomorrow is read by them or wants an entry: `bun test/case-lens-gaps.ts` lists it, the smoke counts it.

## Native check

Machine-written (Claude, 2026-11-01): every new supplement entry's glosses and German and Italian meanings (the forms
are Sloleks's, or the regular endings where it has none, said so in the entry's source), the table's new entries and
example, the names' glosses of who or what each is, the doc's examples. The rules read, they never grade: nothing here
calls anything a learner says a mistake.

**For Jan's wife, the short list** (a spoken form is never wrong; each says which is standard and which spoken):

1. **The spoken forms the B2 units teach**, entered as spoken with the written form in the gloss: *zdej* (zdaj), *tud*
   (tudi), *kva* (kaj; SP2001 and SSKJ² have *kvá* only as a duck's or a frog's call), *ful*, *fajn*, *luštno*, *flaša*
   (SSKJ² pog.). Are these how you say them, and would you write any of them otherwise?
2. **"Dobra volja je najbolja."** (Kekec's song, in the story's last lines): *najbolja* is entered as the rhyme's folk
   form of *najboljša*. Is that how you know the line?
3. **"Ti si močen."** (a scene's line): SP2001 has only *móčen*
   (https://fran.si/iskanje?View=1&Query=mo%C4%8Den&FilteredDictionaryIds=134), Sloleks too; SSKJ² has *močan*, the
   content's usual word (eight lines; *močen* two). Both are entries now. Which do you say, and is *močan* everyday?
4. **midve, vidve beside medve, vedve** (the dual's feminine, *Vidve sta pridni*): SP2001 has "mídva, médve tudi
   mídve" and "vídva, védve tudi vídve" (https://fran.si/iskanje?View=1&Query=midva&FilteredDictionaryIds=134), so
   both are the table's. Which do you say?
5. **Kosci** (*Pozimi so Kosci visoko na jugu*, the sky pack): entered as the constellation's folk name (Orion's belt,
   the Mowers), declined as a plural. Is that the name you know, and is it Orion's belt?
6. **Argonavti** written with a capital in the dragon story (*so Argonavti ladjo razstavili*): SP2001 writes
   *argonávt* small (a common noun). The content is left as it is; should it be *argonavti*?
7. **The region names**: *na Primorskem*, *s Kranjskega*, *na Dolenjsko* are entered as the neuter *Primorsko*,
   *Kranjsko*, *Dolenjsko* (beside the feminine *Primorska*, *Dolenjska*, which Sloleks has). Natural?
8. **The names' and the new words' meanings** in English, German and Italian (the gloss of each entry; who each name
   is: *Samol*, the vagabond of the Kozjak tale; *Pikica*, a sheep; *Liska*, a brown cow).
9. **"Muri teče hitro, Sivko pa počasi."**: the lens reads *Sivko* as the nominative (right), but says it is someone
   spoken to (the verb left out). A slip of the lens's reasons, not of the case.

## Tests

- Bridge: `bunx tsc --noEmit -p .` clean; the smoke 3,314 checks, all passed (LANI_SMOKE_PORT 9690,
  LANI_SMOKE_DRAMA3_PORT 9708): its case lens part with 14 lines of this round's rules, the fourth held-out set, the
  content's unknown forms under 150, GET /lookup of *Juhu* (an interjection, no case) and *Krpana* (a name, the object);
  the "Sklanjaj!" nouns' list with the four plural-only or adjectival nouns that have forms now (jaslice, mlinci,
  Gostosevci, florjanovo: the drill asks their checked slots; case-trainer.md). `tutor-export --check`: 89 files match.
  `bun test/hint-lens.ts`: 24 turns as recorded (recorded again once: *Iz Gorice sem.* reads Gorice as the town's name
  alone, the same case and trigger).
- Python: `case-triggers.py --check` matches; `python3 -m unittest discover -s tests` 251 tests OK, `-s companion/tests` 142
  OK (11 skipped).
- App: @@APP@@
- Emulator QA: `companion/bin/qa-slot --no-town --steps lens-unknown` (slot 2; the second run with `--apk` of the
  first's build: this branch changes no app code, the cards ask the dev bridge), shots in the session's scratchpad
  (ux-lens-unknown-forms/):

  | Shot | What it shows |
  |---|---|
  | lens-unknown-krpan (4 screens) | «Cesar si je zapomnil Krpana.»: Cesar the nominative, the subject; Krpana → Krpan, "lastno ime · proper noun — tožilnik, ed.", "Koga? Kaj? → 4. tožilnik", "Predmet glagola «zapomnil»" |
  | lens-unknown-izmed (4 screens) | «Mickina potica je najboljša izmed vseh.»: Mickina and potica the nominative, najboljša the nominative (what it is), izmed "Zahteva rodilnik: «vseh»", vseh the genitive plural by izmed |
  | lens-unknown-zlatoroga | Zlatoroga's card in its line: Zlatorog, a name of the stories, the accusative, the object of našel |
  | lens-unknown-juhu | Juhu's card: "juhu · medmet · interjection · yoo-hoo, hooray", no case row |

  The first run (before the step scrolled) found Krpana's row below the sheet's fold: the step scrolls now. Before: no
  shots of the base (its cards are the table under "Tap counts", from the base's bridge code and data: the app is the
  same). What the runs show besides: the sheet gives *si* of *zapomniti si* as biti's second person (the dictionary's
  reading; What's left).
