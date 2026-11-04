# Built: the commonest words in a pack (common-words)

*Built 2026-11-03 and 04, branch `common-words` (followups-16, section 5), from main 1311eeb2a with `packs-sl` and
`packs-de` merged in (no rebase), then main 85917a636 with `packs-it`. The words the course and the village use most were in no pack, so learning them never counted as
a learned word (GAME.md, "Words learned": only a pack's card counts). This branch measures them in every language, adds
one A1 pack per language of the 24 commonest, and settles Italian "dopo mangiato". Everything here is machine-written
(Claude) and waits for a native speaker's check (the list comes first). The Slovene was settled against Sloleks and
fran.si, one request at a time.*

## For the native check

Every new pack's `review` says it is machine-written. What a dictionary can't settle: register, naturalness, the
prompts.

**Settled by a source**
- Sloleks: *jajci*, the dual of *jajce* ("Imam samo dve jajci", https://viri.cjvt.si/sloleks/slv/headword/61730);
  *jedel, jedla*, the participle of *jesti* ("Si že jedel?", https://viri.cjvt.si/sloleks/slv/headword/1963);
  *pistacija* (https://viri.cjvt.si/sloleks/slv/headword/78091): Sloleks has no adjective *pistacijev*, so the example
  says *sladoled s pistacijo*.
- fran.si: *zdaj* is SSKJ²'s adverb (https://fran.si/iskanje?View=1&Query=zdaj); SSKJ² and SP2001 give no *sedaj*
  beside it, so no note names one.
- Italian *dopo + participle* (below): Treccani, https://www.treccani.it/vocabolario/dopo/.
- Keswick's market is on Saturdays all year (Cumberland Council, https://www.cumberland.gov.uk/parks-culture-and-leisure/markets/keswick-market),
  as everyday-words' *go* says.

**Slovene (besede-za-vsak-dan, and the Slovene of the other three packs)**
- The prompts made to stand apart (a learner asked one of them must know which word it is): *tako* "like this, that way"
  (torej keeps *so*), *dobro* "nicely, all right" / "in Ordnung; gut gemacht", *samo* "only", *potem* "then", the Italian
  *non* and the English *not* as "ne pred glagolom" (beside *no*'s "ne"), the Italian *qui* "tu, na tem mestu", *fare*
  "delati, početi", the German *stehen* "stati pokonci", the English *up* "navzgor".
- packs-sl's words whose prompts this branch narrowed: *darovati* "to donate, to give away", *nato* "dopodiché, in
  seguito", *šele* "not until, only then", *osamljen* "solitario, che si sente solo", *spanec* "a night's sleep",
  *dobiti* "ottenere", *prošnja* "Bewerbung; die Bitte".
- Naturalness: *Lahko noč, spi lepo!*; *Bodi pri miru, Moro, nadenem ti ovratnico!*; *Babica, zelo te imam rad/rada!*;
  *Teta Nives vedno vsem vse pove.*; *Kar vstopi, srček, voda za čaj se že greje.*; *Ovce so gori v hribih.*; *Hladno
  postaja, obleci jakno.*; *Ti je všeč medenjak iz Grasmera?*; *Glej, tam prihaja Opa Sepp!* (Kärnten's Slovene names him
  Opa Sepp 98 times, dedek Sepp 10).
- *srček (nagovor)* for the north's *love*; *bil, bila (preteklik od be)* for *was*.

**Italian (parole-di-ogni-giorno, Giulia, for Teo)**
- *Dopo mangiato* as spoken and right (below): is it spoken only, or simply right?
- The prompts: *qui* "over here, in this place" / "hierher, an diesem Ort" (beside a-scuola's *Presente!*, Tukaj!, Here!,
  Hier!), *stare* "to remain, to stay put" / "bleiben" / "ostati", *sapere* "to know how, to know a fact", *non* "ne pred
  glagolom".
- German for a child: *Ich hab dich so lieb, Oma!*, *Komm her, Moro!*, *Jetzt bist du dran!*.
- packs-it's *basso* now asks "nizke rasti" and "klein gewachsen", no longer piccolo's "majhen" and "klein" (SSKJ² has *biti nizke
  rasti*, https://fran.si/iskanje?View=1&Query=nizek&FilteredDictionaryIds=133).

**German (woerter-fuer-jeden-tag, Oma Resi)**
- Austrian German: *Jause*, *Ich muss jetzt in den Stall.*, *baden gehen*, the note "in Austria the perfect takes sein:
  ich bin gestanden".
- *noch einmal* as one card (and *noch mal* named as said too); *sein* meant "biti (ich bin, du bist)".
- Narrowed: *fahren* "to drive, to travel (in a vehicle)" / "guidare, andare in macchina"; the clock tower's *schlagen*
  "udarjati (ura bije)"; packs-de's *einsam* "solitario, che si sente solo".

**English (everyday-words, Granny Maggie)**
- *was* as a card of its own (the supplement has it as its own entry, and it is the fifth commonest word).
- *love* as Cumbria's way to speak to anyone ("Come in, love, the kettle's on"), *Now then!* as a Cumbrian hello, *beck*,
  *fell*; Grasmere gingerbread.
- Narrowed: *to catch* "acchiappare" (two packs), *to strike* "udarjati (ura bije)", *Fine, thanks.* "Mir geht's gut,
  danke."

## The measure

`companion/lexicon/tools/common_words.py` (README, "Word lookup"; `companion/tests/test_common_words.py`). For a
language it counts every word of the course (its sentences and words in the language: an `accept` or `solutions` list
by its first answer only, no `spoken`, hint, label or explanation), the scenes, packs, scenarios, modules, grammar pages,
drills, I spy lines and cards, and its culture's directory, as `lexicon-build --coverage` reads them; each word as the
app taps it, lemmatized by the supplement (`<lang>.extra.json`, and `sl.json` for Slovene: the first reading). A word
is covered when it, or its lemma, is a pack's word or it stands inside a pack's expression of 2–4 words (dobro jutro,
come stai). `--packs` adds a branch's packs: packs-it's and packs-en's B1 and B2 packs, not on main yet, were counted
too, so none of the new words is one of theirs.

The first 24 verbs, adjectives, adverbs, nouns and interjections, leaving out names and misreadings (Slovene *luka*
read as a harbour, the conjunction *a* as a letter, *Gorica*; Italian *rosa* for Nonna Rosa; German *unter* read as an
adjective; English *will* read as a testament, *verb* and *word* from the course's instructions):

| language | the words (tokens) |
|---|---|
| sl | biti 15,095, iti 1,127, imeti 1,085, priti 987, dober 701, tako 636, kako 601, prosim 595, že 586, zdaj 578, hvala 505, videti 463, dobro 439, velik 391, potem 381, samo 379, povedati 368, reči 357, spati 351, tudi 348, nov 328, veliko 325, jutro 318 (most of it "Dobro jutro"), dati 307 |
| it | essere 7,492, avere 3,420, non 1,920, fare 1,597, andare 1,221, come 1,026, dire 996, venire 951, volere 619, più 573, piccolo 519, ecco 513, tutto 506, arrivare 501, tanto 459, bello 436, stare 429, potere 419, qui 416, adesso 399, sapere 397, guardare 394, solo 365, tornare 357 |
| de | haben 2,809, sein 2,804, kommen 1,474, wie 1,239, werden 969, gut 940, gehen 753, noch einmal 623 (742 einmal, nearly all in it), da 692, so 644, stehen 621, langsam 596, dann 594, machen 587, ganz 555, noch (the rest), jetzt 543, geben 490, hören 481, können 469, schon 461, nur 456, müssen 433, sehen 431 |
| en | be 8,593, have 3,909, do 3,467, go 2,073, was 2,041, can 1,829, say 1,734, there 1,680, come 1,505, get 1,331, not 1,033, like 1,007, up 991, so 845, here 824, love 819, again 791, old 777, good 765, now 761, how 707, then 690, see 689, take 674 |

The share of the content's words that are a pack's:

| | before | after |
|---|---|---|
| sl | 34.5 % | 51.5 % |
| it | 35.7 % | 52.1 % |
| de | 29.8 % | 40.8 % |
| en | 25.1 % | 40.6 % |

What is next, for another pack (common_words.py with `--pos verb,noun,adj,adv`): sl prav 299, stati 289, enkrat 285,
gledati, delati, potrebovati, pomagati, lepo, prinesti, zelo, malo, počasi, morati, sesti, jesti 214; it grande 337,
allora, ancora, quando, così, anche, mettere, finire, parlare, giusto, prendere, già, portare, pensare, quanto, dove;
de groß 423, alt, helfen, auch, brauchen, hier, schauen, klein, wollen, bleiben, liegen, gern, bringen, dort; en know
648, day, make, ask, would, too, tell, look, time, when, where, need, live, big, want, very, stay, never, people (and
Lakeland's lass 650 and lad 396).

## The packs

All A1, 24 words, each with an emoji, the meaning in every base, an example in all four languages (at most 70
characters in the language learned) and a note on most (the irregular forms, a set phrase, a near word). Set in their
village with a giver of its cast:

| pack | file | giver | examples |
|---|---|---|---|
| `besede-za-vsak-dan` Besede za vsak dan | companion/packs/ | Babica Micka 👵 | her kitchen and potica, Nova Gorica's market, the linden, this year's grapes, Tone's new hammer, Luka's sheep, Stari Janez asleep under the linden |
| `parole-di-ogni-giorno` Le parole di ogni giorno | companion/packs/it/ | Giulia 👧 | Cormòns, Nonna Rosa's garden, Zia Nives, the market in Cividale, Moro, Tommaso and the pizza: a child's day, for Teo |
| `woerter-fuer-jeden-tag` Wörter für jeden Tag | companion/packs/de/ | Oma Resi 👵 | her garden, a Jause, the lake, the church in the middle of the village, the cowshed, the Dobratsch |
| `everyday-words` Everyday words | companion/packs/en/ | Granny Maggie 👵 | the farm and the fells, Keswick's Saturday market, Grasmere gingerbread, the beck, an umbrella for the rain |

**Prompts.** No new clash in any base (`bun test/prompt-clashes.ts`). Where a common word's natural prompt was already
a narrower word's, the narrower word got its own (the lists above) or the common word a second wording. Two pairs
share a prompt as English and Italian say it and are in the baseline (`test/prompt-clashes.json`), as the packs'
synonyms are: *dober* and Miklavž's *priden* ("good", "buono"), *gut* and Nikolo's *brav* ("good", "buono"). Both
festival packs are bundled in the app and Primorska's is frozen by CultureParityTest, so they stay.

**Homonyms:** none (no spelling of a new word is another pack word's). **The supplement:** Italian `collare`
(`companion/lexicon/tools/it_cov_70_common_words.txt`, it.extra.json rebuilt from the tables; `orto` needs none, it is
in-piazza's pack word); German `schneidest`
(schneiden's du-form, de.extra.json). Every token of every example is known to the tap. Each pack word's meaning stays
close to the supplement's entry of the word.

**How they reach a learner.** Plain A1 packs: `Packs.suggest` offers the first untouched pack at the learner's level in
the files' order, then the unfinished ones below it, so an A1 learner meets them among the other A1 packs
(parole-di-ogni-giorno after numeri-e-ore, besede-za-vsak-dan after babicina-kuhinja), and Teo at A2 or Jan above it
after their own level's. An older app (0.2.698) gets them too, as every pack up to a level above its learner's. A word
the learner already has as a card (a looked-up *essere*) is had, as for any pack.

## On the printed sheets

The sheets list a unit's words with their meanings, the packs' among them. Every sheet of every language and base was
written as HTML before the packs (9318e98e2) and after, and compared. Two regressions came from English's bare pack
words: *can* and *was* are verbs, but a bare English pack word was taken for a noun, so the supplement's verb stepped
aside ("can: pločevinka, konzerva", a tin, on the can unit). `prints/sheet.ts` now keeps the verb when the pack's word
means it (f8546b0eb). German's *sein* lost its auxiliary to the possessive (*njegov*) on partizip-perfekt, because the
pack's "biti" repeated the supplement's verb entry; the pack now says "biti (ich bin, du bist)". What changed after that:
Slovene's *povedati* and *veliko* meant in German and Italian (before, the English fallback). No Italian sheet changed
(compared again against main 85917a636). The changed sheets, and those of can, partizip-perfekt and preposizioni, were made as PDFs (`school-prints`): every one fits (stevila, odvisni-govor,
preposizioni and can on one page; dajalnik, velelnik-napredno and partizip-perfekt on two, the most a unit may take).

## "Dopo mangiato"

course-it-b2-c graded a typed "Dopo mangiato, Bruno torna nella fucina" wrong; practice-it-b2's avendo-finito accepted
"Dopo mangiato, Franco torna al mulino" without a word. Treccani's Vocabolario, *dopo* 1.c: "davanti a part. pass. si
usa, con la stessa funzione, il semplice dopo: d. uscito, d. fatte le valigie" (https://www.treccani.it/vocabolario/dopo/):
dopo with the participle alone is usual Italian, and it is what people say. The unit teaches *dopo aver mangiato*, so
both now take it as spoken: infinito-passato's transform line lists its four orders as `spoken` (right, and labelled
"spoken, and right too" with the standard answer), and avendo-finito's explanation names it in each base ("in speech
also: dopo mangiato", as racconta-come-ando names Friuli's passato prossimo). No item offers it as wrong. *Dopo
mangiare* is unchanged (typed, wrong; offered nowhere). A native speaker may say it is simply standard: then it moves
into `accept`.

## Voice

Nothing was spent. The new packs' words and examples, one voice each: Slovene 737 characters, Italian 832, German 812,
English 834 (3,215 in all). avendo-finito's changed explanation (one item: de 235, sl 199, en 224 characters)
needs its explanation clips again where `--explanations` made them. No older text in the language learned changed, so
no clip was lost; the meanings changed are not voiced.

## Checks

- `bunx tsc --noEmit -p .`; `bun build --no-bundle companion/prints/sheet.ts`; `bun test/prompt-clashes.ts` (no new
  clash on the merged tip; packs-en's packs checked beside them with a scratch script);
  `check-culture`, `check-celebrations` and `check-steps` for all four cultures; `tutor-export --check`; every new pack
  through `validatePack` and `unmarkedHomonyms`; `it_cov_build.py --prompts` (the six clashes it lists are older).
- Python: `python3 -m unittest discover -s companion/tests`, 182 tests (3 new).
- Before merging main (469865cc6): the smoke, 3,833 checks, none failed; the Android suite, 4,140 tests, none failed, 2
  skipped. The first smoke failed one check, Friuli's *sono*, now a pack word's: the check follows it, and the
  supplement's meaning is checked on *parlo*.
- On the merged tip (3a88ffb4b, main 85917a636 in): the smoke, 3,938 checks, none failed; the Android suite, 4,152 tests,
  0 failed, 2 skipped.
- Not done: an emulator QA (the packs screen shows a pack as any other; only a sheet's word lookup changed in code).

## For the lead

- **packs-en** isn't on main and its branch name is gone from this repository (practice-en and dialogs-lakeland carry it);
  this branch measured against its tip of
  2026-11-03 (46 packs, a scratch copy) without merging it, and its English prompts didn't clash with those. When it is
  merged, run `bun test/prompt-clashes.ts` and `common_words.py --lang en` again.
- The commonest words come first in no order: `Packs.suggest` follows the files' order. A first-pack hint for a new
  learner (these four first) would be a small change in Packs.kt; not made.

## Commits

077a0c0fb (the measure), ddf82de52 (dopo mangiato), 24f213bed (Italian), 497c323f3 (Slovene), c207f946a (German),
b6ed8b463 (English), f8546b0eb (the sheets), 78ab7c7ce (the test, README), b4f48e16c (this doc), 469865cc6 (the smoke's
sono), 3a88ffb4b (main merged: basso, orto).
