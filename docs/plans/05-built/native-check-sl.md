# Built: the Slovene native check by reference (native-check-sl, 2026-10-30)

Jan: "for native check: please do a web search (fran.si has a good reputation)", and for the round: "for choices - make
it the best - prod ready". This branch checks the app's machine-written Slovene against the reference works, fixes
what a source settles, keeps every spoken form accepted, and leaves Jan's wife a short list of what only a native
speaker can judge (register, naturalness, the spoken forms the app still refuses). The rules as built are in
companion/README.md ("Word forms": the forms book) and docs/plans/05-built/case-trainer.md ("Nouns left out").

## The sources, and how they were read

- **fran.si** (ZRC SAZU): Slovenski pravopis 2001 (SP2001, `FilteredDictionaryIds=134`) for each word's standard
  forms, gender and labels (*člov.* a person, *živ.* an animal, *pokr.* regional, *pog.* spoken, ● wrong, ° less
  right, "tudi" also standard, "in" equally standard); SSKJ² (133) and eSSKJ (201) for meanings, collocations and
  labels (*nižje pog.*, *preg.*, *čeb.*); the Čebelarski terminološki slovar (145); the Jezikovna svetovalnica. The
  meaning of SP2001's ● and °: Slavistična revija 2003/2 (https://srl.si/sql_pdf/SRL_2003_2_2.pdf): "ukaz – prepovedano
  črna pika (•), nepravilno glede na pravilno – krožec (°)".
- **Sloleks 2.0** (CJVT, https://viri.cjvt.si/sloleks/slv/headword/<id>) for full paradigms: every form by case and
  number with its Gigafida frequency and its label (*variantno* a standard variant, *nestandardno* non-standard,
  *živo*/*neživo* the masculine accusative).
- **Politely**: 388 requests (248 to fran.si, 139 to Sloleks, 1 to the Jezikovna svetovalnica) over 51 minutes, one at
  a time with 4 s between them, each page cached in the scratchpad so none was fetched twice (`native-check-sl/cache/`,
  a log of every request). No site refused one.
- The Balinarska zveza Slovenije's rules (MTP, found by a web search) for the bocce words: balin, bližanje, zbijanje.

## The rules followed

- **The standard form is the answer, a spoken form is never a mistake** (the household's rule). Where a table is new,
  SP2001's forms come first (the one shown); Sloleks's other standard variants and its non-standard forms are accepted
  and never shown; the old or dialect forms the app accepted before stay accepted and labelled.
- **One exception, written down**: a spoken form that is another case's standard form (z otroci for z otroki, vidim hči
  for hčer) is not accepted in that slot: "Sklanjaj!" would then take a missed case for right, the very mistake it asks
  about. Each such form is on the list for Jan's wife, who decides.
- **Never retracted on my own**: no learner data was read or changed. Four dialog turns that graded the spoken hour
  wrong now accept it, as Pod uro's already did (below): a source settles that it is spoken, the household's rule that
  spoken is right.
- Fixed text keeps its machine-written flag, now with "checked against Fran 2026-10-30" and what (each file's
  `review`); the forms book's `review` and every new table's `note` cite the entries.

## The counts

230 items checked: **139 right**, **72 fixed**, **7 spoken or regional variants kept** (accepted, labelled), **12 for the
native speaker**. Per section: the open question 1 (right); the forms book's eleven tables and the animate list 15 (10
right, 2 variants kept, 3 fixed); the new noun tables 62 (fixed); the regular check's spot checks 8 (right); the case
triggers 58 (57 right, 1 fixed); the case trainer's bank and the lens's labels 20 (18 right, 2 for her); the spoken
hours 5 (1 variant kept, 4 fixed); the places' words 52 (39 right, 4 variants kept, 2 fixed, 7 for her); the strings
9 (6 right, 3 for her).

## For Jan's wife: the short list

1. **Spoken forms the forms book refuses** because they are another case's form: "z otroci" (z otroki), "vidim hči"
   (hčer), "hčer" or "hčera" as the nominative, "dva mosta", "dva mraza", "dva lesa" (mostova, mrazova, lesova). Should
   any be accepted anyway? (Accepted already: pri otrokih, z vratmi, dva dni, pet dnevov, v dnevih, hčero, dva sina,
   vidim sinu, vidim dekleta.)
2. **bulin or balin** at the bocce court: SSKJ² has only balin ("najmanjša krogla pri balinanju"); the app keeps bulin,
   the Primorska courts' word, and its note names balin. Do you say bulin?
3. **"Bolj bližje? Tega pa še nisem slišal."** The double comparative is a wrong choice (Na balinišču). Fair to grade it
   wrong, and is the reply natural?
4. **The spoken hour**, now right in four more turns, the reply saying the written form back: "Ob šestih, ja. Tako
   pišemo, ob šest pa tudi rečemo." (V hiši, V gorah), "Ob desetih, ja! …" (V cerkvi). And V podstrešju's new wrong
   choice "Ura je sedmih. Šola je ob osmih!" with Nejc's "Sedmih? Ura je sedem, {learner}. Še pet minut …".
5. **narasel or narastel**: "Potok je narasel" (Na mostu). SP2001 prefers narasti, narastel; narasel is the variant.
6. **"Dajte mi vedri."** for the two buckets (or "obe vedri"?), and **"Luka nese drva za šotor."**: behind the tent, or
   for it? (The case trainer; both readings take the accusative, so the answer stands either way.)
7. **moba** in the game's tip "Dokler ni popravljeno, ne daje ničesar; pomagajo sosedje (moba).": none of Fran's
   dictionaries has it. Your word for the neighbours' help?
8. **"v gruči"**: Anton says the bees sleep "vse skupaj, v gruči" (the beekeepers' word, Čebelarski terminološki slovar;
   it was "v kepi"). Natural?
9. **Phrases a dictionary can't judge**: "pojdiva vprašat", "stražiti mlaj", "zdaj bo bila" (the clock about to
   strike), "Mir je največ, kar imamo", "porini me" (on the swing), "pol ure čez eno".
10. **Two labels**: the case trainer's "Pogovorno, prav tako prav; knjižno: …" (prav tako pravilno?), and the case
    lens's "po: za čem, okoli" for po with the locative.
11. Ti or vi (Vida's "pomagaj mi", Mojca's "kaj nosiš na čelu sprevoda?", "potegni za vrvi"): on the content-fixes
    branch's list already.

## What was fixed

- **The forms book** (`companion/lexicon/sl.forms.json`):
  - 62 noun tables: every noun "Sklanjaj!" left out (dan, hči, uho, oko, kolo, zob, otrok, med, mir, most, zid, slap,
    grad, nebo, the plural-only nouns, the partial tables), tesar, and fourteen whose two plurals were shown in the
    wrong order or with a form SP2001 hasn't (bratje before brati; gozdovi before gozdi; zvonovi, rogovi, lesovi only).
    Now all 455 nouns of the packs and the bank with forms have every slot checked (before: 406).
  - **A bug the source found**: tesar (carpenter) was served with the accusative tesar, checked; a person's is tesarja.
    The regular check confirmed it because tesar wasn't on the animate list. 20 persons and animals added (SP2001's
    člov./živ.): tesar, oven, trot, krošnjar, cerkovnik, gasilec, junak, parkelj, partner, pesnik, popotnik,
    sodelavec, šef, vinar, voznik, čebelar, čevljar, zajček, jurček ("nabirati jurčke", živ.), Miklavž.
  - A table can now carry `variants` (bridge/src/forms.ts): the old or dialect forms the regular check had found
    (brati, bratam, zvoni), so a table keeps them accepted and labelled as before instead of losing them.
- **The case lens's trigger table**: 21 fused forms (pote, pome, ponj, ponjo, čezte; zanje … čeznje; zanju …
  čeznju). The content's "pote", "ponj", "zanje", "nanje" had no row.
- **The spoken hour** in four turns (V hiši, V cerkvi, V podstrešju, V gorah): right "(as people say it)".
- **Spelling and words**: martinovo and florjanovo small in a sentence (SP2001), the bees' gruča (was kepa); the
  regional words labelled in their notes (bulin: balin; štirna: spoken for vodnjak; toplar: regional).

## The table

R right · F fixed · V a spoken or regional variant kept (accepted, labelled) · N for the native speaker.

### The open question

| item | the app | the source | URL | decision |
|---|---|---|---|---|
| "pote" (v-podstresju `vstani-ribnik`: "Po šoli bom prišel pote.") | pote, written together | SSKJ² ti: "z enozložnim predlogom se v tožilniku navadno rabi z njim skupaj pisana krajša oblika: … tudi pote bodo prišli" | [SSKJ² ti](https://fran.si/133/sskj2-slovar-slovenskega-knjiznega-jezika-2/4540030/ti) | R (unchanged; the lens now reads it) |

### The forms book's corrections of 2026-10-30

| item | the app | the source | URL | decision |
|---|---|---|---|---|
| potok | acc.sg potok | pôtok -óka m, no *živ.*: a thing | [SP2001](https://fran.si/iskanje?View=1&Query=potok&FilteredDictionaryIds=134) | R |
| sin | acc.sg sina | sín -a tudi sín -ú, "druga oblika dalje -u -ú -u -om" (acc sinu, the u-declension); Sloleks: dva sina beside sinova | [SP2001](https://fran.si/iskanje?View=1&Query=sin&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/88624) | V (sinu, sina du accepted) |
| cesar | acc.sg cesarja | cêsar -árja m s -em, *člov.* | [SP2001](https://fran.si/iskanje?View=1&Query=cesar&FilteredDictionaryIds=134) | R |
| zdravnik | acc.sg zdravnika | zdravník -a m, *člov.* | [SP2001](https://fran.si/iskanje?View=1&Query=zdravnik&FilteredDictionaryIds=134) | R |
| metulj | acc.sg metulja | metúlj -a m z -em, *živ.* | [SP2001](https://fran.si/iskanje?View=1&Query=metulj&FilteredDictionaryIds=134) | R |
| mož | moža; možje, mož, možem, može, možeh, možmi | móž -á m -u -á -u -em; -á móž -éma -á -éh -éma; možjé móž -ém -é -éh -mí | [SP2001](https://fran.si/iskanje?View=1&Query=mož&FilteredDictionaryIds=134) | R |
| dekle | neuter, dekleta | deklè -éta s, "im. ed. tudi ž, tož. ed. tudi živ." | [SP2001](https://fran.si/iskanje?View=1&Query=dekle&FilteredDictionaryIds=134) | V (acc.sg dekleta accepted) |
| pot | feminine, poti, potjo | pót 3 -í ž -i -- -i -jó; -í -í -éma -í -éh -éma; -í -í -ém -í -éh -mí | [SP2001](https://fran.si/iskanje?View=1&Query=pot&FilteredDictionaryIds=134) | R |
| klop | feminine, klopi, klopjo | klóp -í ž, the same endings; klòp klôpa m *živ.* is the tick | [SP2001](https://fran.si/iskanje?View=1&Query=klop&FilteredDictionaryIds=134) | R |
| avto | pl avti | ávto -a m; Sloleks: avti, avtov, dva avta | [SP2001](https://fran.si/iskanje?View=1&Query=avto&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/48199) | R |
| oktober | gen oktobra | október -bra m | [SP2001](https://fran.si/iskanje?View=1&Query=oktober&FilteredDictionaryIds=134) | R |
| the animate list (52) | persons and animals, acc = gen | each a person or an animal; the doubtful ones too: konjíček -čka m *živ.* ("posvetiti se svojemu konjičku"), zmáj -a m *živ.* ("spuščati papirnate zmaje") | [konjiček](https://fran.si/iskanje?View=1&Query=konjiček&FilteredDictionaryIds=134) [zmaj](https://fran.si/iskanje?View=1&Query=zmaj&FilteredDictionaryIds=134) | R |
| persons and animals not on it (20) | tesar's acc.sg tesar checked; the others without forms yet | *člov.* or *živ.* in each entry: tesár -ja člov., ôven ôvna živ., trót -a živ., krošnjár -ja člov., júrček -čka živ., zájček -čka živ., párkelj 2 člov. … | [tesar](https://fran.si/iskanje?View=1&Query=tesar&FilteredDictionaryIds=134) [oven](https://fran.si/iskanje?View=1&Query=oven&FilteredDictionaryIds=134) [trot](https://fran.si/iskanje?View=1&Query=trot&FilteredDictionaryIds=134) [jurček](https://fran.si/iskanje?View=1&Query=jurček&FilteredDictionaryIds=134) | F (added) |
| oven | acc.sg not checked (ovna) | ôven ôvna m *živ.* | [SP2001](https://fran.si/iskanje?View=1&Query=oven&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/76276) | F (animate: every slot checked) |
| trot | acc.sg not checked (trota) | trót -a m *živ.* | [SP2001](https://fran.si/iskanje?View=1&Query=trot&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/94342) | F (animate: every slot checked) |

### The new noun tables (62)

What the app had (GET /forms before), what SP2001 and Sloleks say (each table's `note`), the forms now ("accepted":
`also`, never shown; "old forms kept": `variants`, labelled).

| noun | the app | the source, and the table now | URL | decision |
|---|---|---|---|---|
| dan | every slot unchecked (dne/dneva, dnem/dnevom: no stem to check) | SP2001: dan dneva, also dne; du dneva dni dnema; pl dnevi dni dnem dni dneh dnevi. Sloleks: also dnevom, dnevoma, dnevih. Wiktionary: no stem to check. Now gen.sg dneva/dne, acc.sg dan, nom.pl dnevi · accepted ins.sg dnevom; gen.du dnevov … | [SP2001](https://fran.si/iskanje?View=1&Query=dan&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/52428) | fixed |
| hči | every slot unchecked; hči as the accusative | SP2001: hči hčere, acc hčer, ins hčerjo; gen pl hčera. Sloleks: hčer and hčera for the nominative, hči and hčero for the accusative are non-standard. Now gen.sg hčere, acc.sg hčer, nom.pl hčere · accepted acc.sg hčero; gen.pl hčer | [SP2001](https://fran.si/iskanje?View=1&Query=hči&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/59044) | fixed |
| uho | every slot unchecked (ušes-) | SP2001: uho ušesa (the stem ušes-). Sloleks: the whole table. Now gen.sg ušesa, acc.sg uho, nom.pl ušesa | [SP2001](https://fran.si/iskanje?View=1&Query=uho&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/94752) | fixed |
| oko | unchecked but nom/acc.sg (oka beside očesa) | SP2001: oko očesa; the plural of the eye is oči, feminine: left out, so an adjective never agrees with it as with a neuter. Sloleks: the singular and the dual. Now gen.sg očesa, acc.sg oko | [SP2001](https://fran.si/iskanje?View=1&Query=oko&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/74989) | fixed |
| kolo | unchecked but nom/acc.sg (kola beside kolesa: two words) | SP2001: kolo kolesa (wheel, bicycle); kolo kola is another word (a round dance). Sloleks: the whole table. Now gen.sg kolesa, acc.sg kolo, nom.pl kolesa | [SP2001](https://fran.si/iskanje?View=1&Query=kolo&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/64332) | fixed |
| zob | 12 slots unchecked (zobi, zobjo) | SP2001: zob zoba; du zoba zob zobema; pl zobje zob zobem zobe zobeh zobmi. Sloleks: also zoboma in the dual. Now gen.sg zoba, acc.sg zob, nom.pl zobje · accepted dat.du zoboma; ins.du zoboma · old forms kept dat.sg zobi; loc.sg zobi … | [SP2001](https://fran.si/iskanje?View=1&Query=zob&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/99905) | fixed |
| otrok | nom.pl, loc.pl, loc.du unchecked | SP2001: pl otroci otrok otrokom otroke otrocih otroki; otrokih and z otroci marked wrong. Sloleks the same, otrokih non-standard. Now gen.sg otroka, acc.sg otroka, nom.pl otroci · accepted loc.du otrokih; loc.pl otrokih | [SP2001](https://fran.si/iskanje?View=1&Query=otrok&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/90) | fixed |
| med | gen.sg unchecked (medi, medjo: brass's) | SP2001: med medu and meda (honey, a mass noun); med medi is another word (brass, feminine). Sloleks: the singular. Now gen.sg medu/meda, acc.sg med · old forms kept dat.sg medi; loc.sg medi | [SP2001](https://fran.si/iskanje?View=1&Query=med&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/68864) | fixed |
| mir | gen.sg unchecked (mira/miru) | SP2001: mir miru and mira, the singular. Sloleks the same. Now gen.sg miru/mira, acc.sg mir · old forms kept dat.sg miri; loc.sg miri | [SP2001](https://fran.si/iskanje?View=1&Query=mir&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/69755) | fixed |
| most | gen.sg unchecked; mosti shown before mostovi | SP2001: most mostu and mosta; the plural on -ov-: mostovi, mostov. Sloleks the same. Now gen.sg mostu/mosta, acc.sg most, nom.pl mostovi · old forms kept dat.du mostoma; loc.du mostih … | [SP2001](https://fran.si/iskanje?View=1&Query=most&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/70398) | fixed |
| zid | gen.sg unchecked (zida/zidu) | SP2001: zid zida and zidu; the plural on -ov-: zidovi, zidov. Sloleks the same. Now gen.sg zidu/zida, acc.sg zid, nom.pl zidovi · accepted nom.du zida; dat.du zidoma … · old forms kept dat.sg zidi; loc.sg zidi … | [SP2001](https://fran.si/iskanje?View=1&Query=zid&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/99406) | fixed |
| slap | gen.sg unchecked (slapa/slapu) | SP2001: slap slapa, also slapu; the plural on -ov-: slapovi, slapov. Sloleks the same. Now gen.sg slapa/slapu, acc.sg slap, nom.pl slapovi · accepted nom.du slapa; dat.du slapoma … · old forms kept dat.sg slapi; loc.sg slapi … | [SP2001](https://fran.si/iskanje?View=1&Query=slap&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/89371) | fixed |
| grad | gen.sg unchecked (grada/gradu) | SP2001: grad gradu and grada (castle); grad grada is another word (a unit). Sloleks: the plural gradovi. Now gen.sg gradu/grada, acc.sg grad, nom.pl gradovi · old forms kept dat.sg gradi; loc.sg gradi … | [SP2001](https://fran.si/iskanje?View=1&Query=grad&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/58272) | fixed |
| nebo | unchecked but nom/acc.sg (nebesa mixed in) | SP2001: nebo neba, the singular (the plural is nebesa, heaven, a word of its own). Sloleks the same. Now gen.sg neba, acc.sg nebo | [SP2001](https://fran.si/iskanje?View=1&Query=nebo&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/71849) | fixed |
| vrata | the plural unchecked | SP2001: vrata vrat, plural only; z vrati, vratmi marked less right (spoken: accepted). Sloleks the same. Now nom.pl vrata, gen.pl vrat, ins.pl vrati · accepted ins.pl vratmi | [SP2001](https://fran.si/iskanje?View=1&Query=vrata&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/96929) | fixed |
| grablje | the plural unchecked | SP2001: grablje grabelj, plural only. Sloleks the same. Now nom.pl grablje, gen.pl grabelj, ins.pl grabljami | [SP2001](https://fran.si/iskanje?View=1&Query=grablje&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/58257) | fixed |
| škarje | the plural unchecked | SP2001: škarje škarij, plural only. Sloleks the same. Now nom.pl škarje, gen.pl škarij, ins.pl škarjami | [SP2001](https://fran.si/iskanje?View=1&Query=škarje&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/88879) | fixed |
| orgle | the plural unchecked | SP2001: orgle orgel, plural only. Sloleks the same. Now nom.pl orgle, gen.pl orgel, ins.pl orglami | [SP2001](https://fran.si/iskanje?View=1&Query=orgle&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/75710) | fixed |
| klešče | the plural unchecked | SP2001: klešče klešč, plural only. Sloleks the same. Now nom.pl klešče, gen.pl klešč, ins.pl kleščami | [SP2001](https://fran.si/iskanje?View=1&Query=klešče&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/63780) | fixed |
| vilice | the plural unchecked | SP2001: vilice vilic, plural only (vilica in the singular is spoken: Sloleks has it). The a-declension's plural. Now nom.pl vilice, gen.pl vilic, ins.pl vilicami | [SP2001](https://fran.si/iskanje?View=1&Query=vilice&FilteredDictionaryIds=134) | fixed |
| vile | the plural unchecked | SP2001: vile vil, plural only (a pitchfork). The a-declension's plural. Now nom.pl vile, gen.pl vil, ins.pl vilami | [SP2001](https://fran.si/iskanje?View=1&Query=vile&FilteredDictionaryIds=134) | fixed |
| potica | a partial table without the genitive: a few slots, none checked | SP2001: potica -e (tri potice). Sloleks: the whole table. Now gen.sg potice, acc.sg potico, nom.pl potice | [SP2001](https://fran.si/iskanje?View=1&Query=potica&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/81013) | fixed |
| kosa | a partial table without the genitive: a few slots, none checked | SP2001: kosa kose, also the mobile accent (du kose, gen pl kosa). Sloleks: the regular table. Now gen.sg kose, acc.sg koso, nom.pl kose | [SP2001](https://fran.si/iskanje?View=1&Query=kosa&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/65087) | fixed |
| palisada | a partial table without the genitive: a few slots, none checked | SP2001: palisada -e. Sloleks: the whole table. Now gen.sg palisade, acc.sg palisado, nom.pl palisade | [SP2001](https://fran.si/iskanje?View=1&Query=palisada&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/76593) | fixed |
| tehtnica | a partial table without the genitive: a few slots, none checked | SP2001: tehtnica -e. Sloleks: the whole table. Now gen.sg tehtnice, acc.sg tehtnico, nom.pl tehtnice | [SP2001](https://fran.si/iskanje?View=1&Query=tehtnica&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/92872) | fixed |
| daljnogled | a partial table without the genitive: a few slots, none checked | SP2001: daljnogled -eda. Sloleks: the whole table. Now gen.sg daljnogleda, acc.sg daljnogled, nom.pl daljnogledi | [SP2001](https://fran.si/iskanje?View=1&Query=daljnogled&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/52398) | fixed |
| hlebec | a partial table without the genitive: a few slots, none checked | SP2001: hlebec -bca, s -em. Sloleks: the whole table. Now gen.sg hlebca, acc.sg hlebec, nom.pl hlebci | [SP2001](https://fran.si/iskanje?View=1&Query=hlebec&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/59506) | fixed |
| tolminc | a partial table without the genitive: a few slots, none checked | eSSKJ: tolminec tolminca, tudi tolminc (a hard cheese of the upper Soča valley); the regular declension of a soft stem. Now gen.sg tolminca, acc.sg tolminc, nom.pl tolminci | [eSSKJ](https://fran.si/iskanje?View=1&Query=tolminec&FilteredDictionaryIds=201) | fixed |
| pršut | a partial table without the genitive: a few slots, none checked | SP2001: pršut -a. Sloleks: the whole table. Now gen.sg pršuta, acc.sg pršut, nom.pl pršuti | [SP2001](https://fran.si/iskanje?View=1&Query=pršut&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/84134) | fixed |
| žito | a partial table without the genitive: a few slots, none checked | SP2001: žito -a (bela žita). Sloleks: the whole table. Now gen.sg žita, acc.sg žito, nom.pl žita | [SP2001](https://fran.si/iskanje?View=1&Query=žito&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/99504) | fixed |
| ribnik | a partial table without the genitive: a few slots, none checked | SP2001: ribnik -a. Sloleks: the whole table. Now gen.sg ribnika, acc.sg ribnik, nom.pl ribniki | [SP2001](https://fran.si/iskanje?View=1&Query=ribnik&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/86293) | fixed |
| romar | a partial table without the genitive: a few slots, none checked | SP2001: romar -ja, a person (acc romarja), z -em. Sloleks: the whole table. Now gen.sg romarja, acc.sg romarja, nom.pl romarji | [SP2001](https://fran.si/iskanje?View=1&Query=romar&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/86688) | fixed |
| trgatev | a partial table without the genitive: a few slots, none checked | SP2001: trgatev -tve. Sloleks: the whole table. Now gen.sg trgatve, acc.sg trgatev, nom.pl trgatve | [SP2001](https://fran.si/iskanje?View=1&Query=trgatev&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/94075) | fixed |
| veselica | a partial table without the genitive: a few slots, none checked | SP2001: veselica -e. Sloleks: the whole table. Now gen.sg veselice, acc.sg veselico, nom.pl veselice | [SP2001](https://fran.si/iskanje?View=1&Query=veselica&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/96018) | fixed |
| peskovnik | a partial table without the genitive: a few slots, none checked | SP2001: peskovnik -a. Sloleks: the whole table. Now gen.sg peskovnika, acc.sg peskovnik, nom.pl peskovniki | [SP2001](https://fran.si/iskanje?View=1&Query=peskovnik&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/77595) | fixed |
| tobogan | a partial table without the genitive: a few slots, none checked | SP2001: tobogan -a. Sloleks: the whole table. Now gen.sg tobogana, acc.sg tobogan, nom.pl tobogani | [SP2001](https://fran.si/iskanje?View=1&Query=tobogan&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/93492) | fixed |
| zvonček | a partial table without the genitive: a few slots, none checked | SP2001: zvonček -čka. Sloleks: the whole table. Now gen.sg zvončka, acc.sg zvonček, nom.pl zvončki | [SP2001](https://fran.si/iskanje?View=1&Query=zvonček&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/100482) | fixed |
| kolesje | a partial table without the genitive: a few slots, none checked | SP2001: kolesje -a, a collective: the singular. Sloleks the same. Now gen.sg kolesja, acc.sg kolesje | [SP2001](https://fran.si/iskanje?View=1&Query=kolesje&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/64296) | fixed |
| venec | a partial table without the genitive: a few slots, none checked | SP2001: venec -nca, z -em. Sloleks: the whole table. Now gen.sg venca, acc.sg venec, nom.pl venci | [SP2001](https://fran.si/iskanje?View=1&Query=venec&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/95913) | fixed |
| ogrodje | a partial table without the genitive: a few slots, none checked | SP2001: ogrodje -a. Sloleks: the whole table. Now gen.sg ogrodja, acc.sg ogrodje, nom.pl ogrodja | [SP2001](https://fran.si/iskanje?View=1&Query=ogrodje&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/74887) | fixed |
| grmovje | a partial table without the genitive: a few slots, none checked | SP2001: grmovje -a, a collective: the singular. Sloleks the same. Now gen.sg grmovja, acc.sg grmovje | [SP2001](https://fran.si/iskanje?View=1&Query=grmovje&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/58628) | fixed |
| korito | a partial table without the genitive: a few slots, none checked | SP2001: korito -a. Sloleks: the whole table. Now gen.sg korita, acc.sg korito, nom.pl korita | [SP2001](https://fran.si/iskanje?View=1&Query=korito&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/65036) | fixed |
| balinišče | a partial table without the genitive: a few slots, none checked | SP2001: balinišče -a. Sloleks: the whole table. Now gen.sg balinišča, acc.sg balinišče, nom.pl balinišča | [SP2001](https://fran.si/iskanje?View=1&Query=balinišče&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/48494) | fixed |
| vaja | a partial table without the genitive: a few slots, none checked | SP2001: vaja -e. Sloleks: the whole table. Now gen.sg vaje, acc.sg vajo, nom.pl vaje | [SP2001](https://fran.si/iskanje?View=1&Query=vaja&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/95462) | fixed |
| listje | a partial table without the genitive: a few slots, none checked | SP2001: listje -a, a collective: the singular. Sloleks the same. Now gen.sg listja, acc.sg listje | [SP2001](https://fran.si/iskanje?View=1&Query=listje&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/67385) | fixed |
| krošnjar | a partial table without the genitive: a few slots, none checked | SP2001: krošnjar -ja, a person (acc krošnjarja), s -em. Sloleks: the whole table. Now gen.sg krošnjarja, acc.sg krošnjarja, nom.pl krošnjarji | [SP2001](https://fran.si/iskanje?View=1&Query=krošnjar&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/65951) | fixed |
| tesar | acc.sg tesar, checked: wrong for a person | SP2001: tesar -ja, a person (acc tesarja), s -em; Wiktionary has the accusative as tesar. Sloleks: the whole table. Now gen.sg tesarja, acc.sg tesarja, nom.pl tesarji | [SP2001](https://fran.si/iskanje?View=1&Query=tesar&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/93174) | fixed |
| toplar | a partial table without the genitive: a few slots, none checked | SP2001: toplar -ja, s -em (regional: a double hayrack). Sloleks: the whole table. Now gen.sg toplarja, acc.sg toplar, nom.pl toplarji | [SP2001](https://fran.si/iskanje?View=1&Query=toplar&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/93671) | fixed |
| brat | nom.pl brati shown first | SP2001: brat brata, a person, im. mn. bratje tudi brati. Sloleks the same. Now gen.sg brata, acc.sg brata, nom.pl bratje/brati · old forms kept dat.sg brati; loc.sg brati … | [SP2001](https://fran.si/iskanje?View=1&Query=brat&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/50157) | fixed |
| kmet | nom.pl kmeti shown first | SP2001: kmet kmeta, a person, im. mn. kmetje, rarely kmeti. Sloleks the same. Now gen.sg kmeta, acc.sg kmeta, nom.pl kmetje/kmeti | [SP2001](https://fran.si/iskanje?View=1&Query=kmet&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/63953) | fixed |
| oče | nom.pl očeti shown first | SP2001: oče očeta, a person, im. mn. očetje tudi očeti. Sloleks the same. Now gen.sg očeta, acc.sg očeta, nom.pl očetje/očeti | [SP2001](https://fran.si/iskanje?View=1&Query=oče&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/74180) | fixed |
| sosed | nom.pl sosedi shown first | SP2001: sosed soseda, a person, im. mn. sosedje tudi sosedi. Sloleks the same. Now gen.sg soseda, acc.sg soseda, nom.pl sosedje/sosedi | [SP2001](https://fran.si/iskanje?View=1&Query=sosed&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/90292) | fixed |
| gost | nom.pl gosti shown first | SP2001: gost gosta, a person, im. mn. gostje tudi gosti; biti v gosteh. Sloleks the same. Now gen.sg gosta, acc.sg gosta, nom.pl gostje/gosti | [SP2001](https://fran.si/iskanje?View=1&Query=gost&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/58145) | fixed |
| gozd | gozdi shown before gozdovi | SP2001: gozd gozda, mn. gozdovi -ov tudi gozdi. Sloleks the same. Now gen.sg gozda, acc.sg gozd, nom.pl gozdovi/gozdi | [SP2001](https://fran.si/iskanje?View=1&Query=gozd&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/58223) | fixed |
| breg | bregi shown before bregovi | SP2001: breg brega, mn. bregovi -ov tudi bregi, mest. tudi bregeh. Sloleks the same. Now gen.sg brega, acc.sg breg, nom.pl bregovi/bregi · old forms kept loc.du bregih; loc.pl bregih | [SP2001](https://fran.si/iskanje?View=1&Query=breg&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/50224) | fixed |
| dom | domi shown before domovi | SP2001: dom doma, mest. domu; dv. doma in domova; mn. domovi -ov (domi in another sense). Sloleks the same. Now gen.sg doma, acc.sg dom, nom.pl domovi · accepted dat.du domoma; loc.du domih … · old forms kept nom.pl domi; acc.pl dome … | [SP2001](https://fran.si/iskanje?View=1&Query=dom&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/53955) | fixed |
| grob | grobi shown before grobovi | SP2001: grob groba; dv. grobova grobov; mn. grobovi -ov tudi grobi. Sloleks the same. Now gen.sg groba, acc.sg grob, nom.pl grobovi/grobi · accepted nom.du groba; dat.du groboma … | [SP2001](https://fran.si/iskanje?View=1&Query=grob&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/58633) | fixed |
| kres | kresi shown before kresovi | SP2001: kres kresa, mn. kresovi tudi kresi. Sloleks the same. Now gen.sg kresa, acc.sg kres, nom.pl kresovi/kresi | [SP2001](https://fran.si/iskanje?View=1&Query=kres&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/65648) | fixed |
| mraz | mrazi shown before mrazovi | SP2001: mraz mraza, mn. mrazovi tudi mrazi. Sloleks the same. Now gen.sg mraza, acc.sg mraz, nom.pl mrazovi/mrazi · old forms kept dat.du mrazoma; loc.du mrazih … | [SP2001](https://fran.si/iskanje?View=1&Query=mraz&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/70537) | fixed |
| zvon | zvoni shown first (SP2001 has only zvonovi) | SP2001: zvon zvona, also zvonu; dv. zvonova zvonov; mn. zvonovi zvonov. Sloleks the same. Now gen.sg zvona, acc.sg zvon, nom.pl zvonovi · accepted nom.du zvona; dat.du zvonoma … · old forms kept dat.sg zvoni; loc.sg zvoni … | [SP2001](https://fran.si/iskanje?View=1&Query=zvon&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/100474) | fixed |
| rog | rogi shown first (SP2001: rogovi) | SP2001: rog roga; dv. roga tudi rogova; mn. rogovi rogov, tož. tudi roge, mest. tudi rogeh, or. tudi rogmi. Sloleks the same. Now gen.sg roga, acc.sg rog, nom.pl rogovi · accepted gen.du rog; nom.pl rogi … · old forms kept dat.sg rogi; loc.sg rogi … | [SP2001](https://fran.si/iskanje?View=1&Query=rog&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/86550) | fixed |
| les | lesi shown first (SP2001: lesovi) | SP2001: les lesa, a mass noun; dv. lesova lesov; mn. lesovi lesov. Sloleks the same. Now gen.sg lesa, acc.sg les, nom.pl lesovi · accepted nom.du lesa; dat.du lesoma … · old forms kept dat.sg lesi; loc.sg lesi … | [SP2001](https://fran.si/iskanje?View=1&Query=les&FilteredDictionaryIds=134) [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/66969) | fixed |

### The regular check, spot-checked

Every other noun (383 of the packs and the bank) is still checked against its regular declension
(bridge/src/noun-paradigm.ts), which takes two plurals as equal and shows them in the dictionary's order. SP2001 on
the ones where that order could matter, and on a genitive that keeps its e:

| item | the app | the source | URL | decision |
|---|---|---|---|---|
| jazbec | gen jazbeca | jázbec -a [bə] m z -em *živ.*: the e stays | [SP2001](https://fran.si/iskanje?View=1&Query=jazbec&FilteredDictionaryIds=134) | R |
| cvet | cveti shown before cvetovi | cvét -a m; "dv. tudi -ôva -ôv; mn. tudi -ôvi -ôv" | [SP2001](https://fran.si/iskanje?View=1&Query=cvet&FilteredDictionaryIds=134) | R |
| sod | sodi before sodovi | sód -a m, "mn. tudi -ôvi -ôv" | [SP2001](https://fran.si/iskanje?View=1&Query=sod&FilteredDictionaryIds=134) | R |
| golob | golobi before golobje | golób -a m *živ.*, "im. mn. tudi golóbje" | [SP2001](https://fran.si/iskanje?View=1&Query=golob&FilteredDictionaryIds=134) | R |
| medved | medvedi before medvedje | mêdved -éda m *živ.*, "im. mn. tudi medvédje" | [SP2001](https://fran.si/iskanje?View=1&Query=medved&FilteredDictionaryIds=134) | R |
| grof | grofi before grofje | gròf grôfa m, "im. mn. grôfi tudi grôfje" | [SP2001](https://fran.si/iskanje?View=1&Query=grof&FilteredDictionaryIds=134) | R |
| luč | lučem before lučim, lučema before lučma | lúč -i ž: -ém, -éh, -éma | [SP2001](https://fran.si/iskanje?View=1&Query=luč&FilteredDictionaryIds=134) | R |
| postrv | postrvem before postrvim | postŕv -i ž: -ém, -éh, -éma | [SP2001](https://fran.si/iskanje?View=1&Query=postrv&FilteredDictionaryIds=134) | R |

### The case triggers (companion/grammar/sl/data/case-triggers.json)

Each preposition's cases, by SP2001 (the case lens and the trainer read them):

| preposition | the table | the source | URL | decision |
|---|---|---|---|---|
| brez | gen | predl. z rod. | [brez](https://fran.si/iskanje?View=1&Query=brez&FilteredDictionaryIds=134) | right |
| iz | gen | predl. z rod. | [iz](https://fran.si/iskanje?View=1&Query=iz&FilteredDictionaryIds=134) | right |
| od | gen | predl. z rod. | [od](https://fran.si/iskanje?View=1&Query=od&FilteredDictionaryIds=134) | right |
| do | gen | predl. z rod. | [do](https://fran.si/iskanje?View=1&Query=do&FilteredDictionaryIds=134) | right |
| blizu, zraven, poleg | gen | predl. z rod. | [blizu](https://fran.si/iskanje?View=1&Query=blizu&FilteredDictionaryIds=134) [zraven](https://fran.si/iskanje?View=1&Query=zraven&FilteredDictionaryIds=134) [poleg](https://fran.si/iskanje?View=1&Query=poleg&FilteredDictionaryIds=134) | right |
| mimo | gen | predl. z rod. | [mimo](https://fran.si/iskanje?View=1&Query=mimo&FilteredDictionaryIds=134) | right |
| zaradi, namesto, razen | gen | predl. z rod. | [zaradi](https://fran.si/iskanje?View=1&Query=zaradi&FilteredDictionaryIds=134) [namesto](https://fran.si/iskanje?View=1&Query=namesto&FilteredDictionaryIds=134) [razen](https://fran.si/iskanje?View=1&Query=razen&FilteredDictionaryIds=134) | right |
| okoli, okrog, sredi | gen | predl. z rod. | [okoli](https://fran.si/iskanje?View=1&Query=okoli&FilteredDictionaryIds=134) [okrog](https://fran.si/iskanje?View=1&Query=okrog&FilteredDictionaryIds=134) [sredi](https://fran.si/iskanje?View=1&Query=sredi&FilteredDictionaryIds=134) | right |
| izpod, izza | gen | predl. z rod. | [izpod](https://fran.si/iskanje?View=1&Query=izpod&FilteredDictionaryIds=134) [izza](https://fran.si/iskanje?View=1&Query=izza&FilteredDictionaryIds=134) | right |
| k, h | dat | predl. z daj. | [k](https://fran.si/iskanje?View=1&Query=k&FilteredDictionaryIds=134) [h](https://fran.si/iskanje?View=1&Query=h&FilteredDictionaryIds=134) | right |
| proti, kljub | dat | predl. z daj. | [proti](https://fran.si/iskanje?View=1&Query=proti&FilteredDictionaryIds=134) [kljub](https://fran.si/iskanje?View=1&Query=kljub&FilteredDictionaryIds=134) | right |
| v | acc (where to), loc (where) | I. z mest., II. s tož. | [v](https://fran.si/iskanje?View=1&Query=v&FilteredDictionaryIds=134) | right |
| ob | loc (when, where), acc (where to: ob zid) | SSKJ2: z mestnikom (ob treh, ob dveh ponoči), s tožilnikom (udariti ob tla) | [SSKJ2 ob](https://fran.si/iskanje?View=1&Query=ob&FilteredDictionaryIds=133) | right |
| na | acc (where to), loc (where) | z mest., s tož. | [na](https://fran.si/iskanje?View=1&Query=na&FilteredDictionaryIds=134) | right |
| pri | loc | predl. z mest. | [pri](https://fran.si/iskanje?View=1&Query=pri&FilteredDictionaryIds=134) | right |
| o | loc | predl. z mest. | [o](https://fran.si/iskanje?View=1&Query=o&FilteredDictionaryIds=134) | right |
| po | loc (after, around), acc (to fetch) | I. z mest., II. s tož. (iti po kaj) | [po](https://fran.si/iskanje?View=1&Query=po&FilteredDictionaryIds=134) | right |
| z, s | ins (with), gen (from) | I. z rod., II. z or. | [z](https://fran.si/iskanje?View=1&Query=z&FilteredDictionaryIds=134) [s](https://fran.si/iskanje?View=1&Query=s&FilteredDictionaryIds=134) | right |
| pod, nad, pred, med | ins (where), acc (where to) | I. z or., II. s tož. | [pod](https://fran.si/iskanje?View=1&Query=pod&FilteredDictionaryIds=134) [nad](https://fran.si/iskanje?View=1&Query=nad&FilteredDictionaryIds=134) [pred](https://fran.si/iskanje?View=1&Query=pred&FilteredDictionaryIds=134) [med](https://fran.si/iskanje?View=1&Query=med&FilteredDictionaryIds=134) | right |
| za | acc (for, where to), ins (where) | I. z or., II. s tož. (also z rod. in old time phrases: za časa, za dne; none in the content) | [za](https://fran.si/iskanje?View=1&Query=za&FilteredDictionaryIds=134) | right |
| skozi, čez, zoper | acc | predl. s tož. | [skozi](https://fran.si/iskanje?View=1&Query=skozi&FilteredDictionaryIds=134) [čez](https://fran.si/iskanje?View=1&Query=čez&FilteredDictionaryIds=134) [zoper](https://fran.si/iskanje?View=1&Query=zoper&FilteredDictionaryIds=134) | right |
| the fused forms | za, na, v, pred, pod, nad, čez, skozi with the singular pronouns; no po, no plural, no dual | SSKJ² on: "enklitični tož. za enozložnimi predlogi -nj oziroma -enj; ž. -njo; mn. -nje; dv. -nju", "odšel je ponjo"; ti: "tudi pote bodo prišli", "čezte"; jaz: "pridi pome" | [SSKJ² on](https://fran.si/iskanje?View=1&Query=on&FilteredDictionaryIds=133) [SSKJ² jaz](https://fran.si/iskanje?View=1&Query=jaz&FilteredDictionaryIds=133) | F (21 added) |

The verbs that govern a case (the 30 verbs of giving and telling, komu kaj, follow the same pattern and were not looked
up one by one):

| verb | the table | the source | URL | decision |
|---|---|---|---|---|
| bati se | gen | SP2001 koga/česa: bati se sovražnika, toče | [SP2001](https://fran.si/iskanje?View=1&Query=bati&FilteredDictionaryIds=134) | right |
| veseliti se | gen | SP2001 koga/česa: veseliti se gostov | [SP2001](https://fran.si/iskanje?View=1&Query=veseliti&FilteredDictionaryIds=134) | right |
| veseliti | acc | SP2001 koga/kaj: veseli me, da vas vidim | [SP2001](https://fran.si/iskanje?View=1&Query=veseliti&FilteredDictionaryIds=134) | right |
| spomniti se | gen | SP2001 koga/česa | [SP2001](https://fran.si/iskanje?View=1&Query=spomniti&FilteredDictionaryIds=134) | right |
| sramovati se | gen | SP2001 koga/česa | [SP2001](https://fran.si/iskanje?View=1&Query=sramovati&FilteredDictionaryIds=134) | right |
| naveličati se | gen | SP2001 koga/česa: jabolk, mesa | [SP2001](https://fran.si/iskanje?View=1&Query=naveličati&FilteredDictionaryIds=134) | right |
| udeležiti se | gen | SP2001 česa: tekmovanja | [SP2001](https://fran.si/iskanje?View=1&Query=udeležiti&FilteredDictionaryIds=134) | right |
| lotiti se | gen | SP2001 koga/česa: priprav | [SP2001](https://fran.si/iskanje?View=1&Query=lotiti&FilteredDictionaryIds=134) | right |
| spominjati se | gen | SP2001 koga/česa: matere | [SP2001](https://fran.si/iskanje?View=1&Query=spominjati&FilteredDictionaryIds=134) | right |
| pomagati | dat | SP2001 komu/čemu: sosedom | [SP2001](https://fran.si/iskanje?View=1&Query=pomagati&FilteredDictionaryIds=134) | right |
| verjeti | dat | SP2001 komu/čemu: obljubam | [SP2001](https://fran.si/iskanje?View=1&Query=verjeti&FilteredDictionaryIds=134) | right |
| zaupati | dat | SP2001 komu/čemu | [SP2001](https://fran.si/iskanje?View=1&Query=zaupati&FilteredDictionaryIds=134) | right |
| slediti | dat | SP2001 komu/čemu: divjadi | [SP2001](https://fran.si/iskanje?View=1&Query=slediti&FilteredDictionaryIds=134) | right |
| odgovoriti | dat | SP2001 komu/čemu; kaj | [SP2001](https://fran.si/iskanje?View=1&Query=odgovoriti&FilteredDictionaryIds=134) | right |
| odgovarjati | dat | SP2001 komu/čemu | [SP2001](https://fran.si/iskanje?View=1&Query=odgovarjati&FilteredDictionaryIds=134) | right |
| telefonirati | dat | SP2001 komu: sodelavcu | [SP2001](https://fran.si/iskanje?View=1&Query=telefonirati&FilteredDictionaryIds=134) | right |
| čestitati | dat | SP2001 komu (kaj): očetu za rojstni dan | [SP2001](https://fran.si/iskanje?View=1&Query=čestitati&FilteredDictionaryIds=134) | right |
| zahvaliti se | dat | SP2001 komu/čemu: darovalcu | [SP2001](https://fran.si/iskanje?View=1&Query=zahvaliti&FilteredDictionaryIds=134) | right |
| ugajati | dat | SP2001 komu/čemu | [SP2001](https://fran.si/iskanje?View=1&Query=ugajati&FilteredDictionaryIds=134) | right |
| škoditi | dat | SP2001 komu/čemu | [SP2001](https://fran.si/iskanje?View=1&Query=škoditi&FilteredDictionaryIds=134) | right |
| groziti | dat | SP2001 komu/čemu: nasprotniku s pestjo | [SP2001](https://fran.si/iskanje?View=1&Query=groziti&FilteredDictionaryIds=134) | right |
| ploskati | dat | SP2001 komu/čemu: govorniku | [SP2001](https://fran.si/iskanje?View=1&Query=ploskati&FilteredDictionaryIds=134) | right |
| približati se | dat | SP2001 komu/čemu: cilju | [SP2001](https://fran.si/iskanje?View=1&Query=približati&FilteredDictionaryIds=134) | right |
| smejati se | dat | SP2001 komu/čemu: šalam | [SP2001](https://fran.si/iskanje?View=1&Query=smejati&FilteredDictionaryIds=134) | right |
| zdeti se | dat | SP2001 komu: zdi se mi | [SP2001](https://fran.si/iskanje?View=1&Query=zdeti&FilteredDictionaryIds=134) | right |
| pripadati | dat | SP2001 komu/čemu | [SP2001](https://fran.si/iskanje?View=1&Query=pripadati&FilteredDictionaryIds=134) | right |
| nasprotovati | dat | SP2001 komu/čemu: očetu | [SP2001](https://fran.si/iskanje?View=1&Query=nasprotovati&FilteredDictionaryIds=134) | right |
| manjkati | dat | SP2001 komu; the thing lacking also in the genitive (koga/česa manjka: manjka dela): the table has the dative, right | [SP2001](https://fran.si/iskanje?View=1&Query=manjkati&FilteredDictionaryIds=134) | right |
| zebsti | acc | SP2001 koga/kaj | [SP2001](https://fran.si/iskanje?View=1&Query=zebsti&FilteredDictionaryIds=134) | right |
| boleti | acc | SP2001 brezos.: V prsih jo boli | [SP2001](https://fran.si/iskanje?View=1&Query=boleti&FilteredDictionaryIds=134) | right |
| skrbeti | acc | SP2001 koga: skrbi jo zaradi otrok | [SP2001](https://fran.si/iskanje?View=1&Query=skrbeti&FilteredDictionaryIds=134) | right |
| srbeti | acc | SP2001 koga/kaj | [SP2001](https://fran.si/iskanje?View=1&Query=srbeti&FilteredDictionaryIds=134) | right |
| jeziti | acc | SP2001 koga/kaj | [SP2001](https://fran.si/iskanje?View=1&Query=jeziti&FilteredDictionaryIds=134) | right |
| muditi se | dat | SP2001 komu: mudi se mi na vlak | [SP2001](https://fran.si/iskanje?View=1&Query=muditi&FilteredDictionaryIds=134) | right |
| zanimati | acc | SP2001 koga/kaj: zanima ga glasba | [SP2001](https://fran.si/iskanje?View=1&Query=zanimati&FilteredDictionaryIds=134) | right |
| izogibati se | gen | SSKJ2: izogibati se avtomobilov, avtomobilom, pešcem: the genitive or the dative (the table has the genitive, right; a dative after it is left to its form) | [SSKJ2](https://fran.si/iskanje?View=1&Query=izogibati&FilteredDictionaryIds=133) | right |

### The case trainer's bank: the items its writer was least sure of (companion/cases/sl/bank.json)

| item | the app | the source | URL | decision |
|---|---|---|---|---|
| `kam-v-podstresje`, `kje-v-podstresju` | v podstrešje, v podstrešju (may na be more usual?) | SSKJ² podstrešje: "sobo so mu naredili v podstrešju", "stanuje na podstrešju": both | [SSKJ²](https://fran.si/iskanje?View=1&Query=podstrešje&FilteredDictionaryIds=133) | R, R |
| `drva-za-sotor` | za šotor, the accusative, "behind the tent" | za + tož. is both behind (where to) and for: the case and the deciding word are the same either way | [SP2001 za](https://fran.si/iskanje?View=1&Query=za&FilteredDictionaryIds=134) | N (which reading) |
| `pod-kapo`, `skoci-pod-mizo`, `pod-lipo`, `pod-smreko`, `pod-peco`, `pred-kovacnico`, `pod-veliko-smreko` | the verb decides kam (acc) or kje (ins) | pod, pred: "I. z or., II. s tož." | [pod](https://fran.si/iskanje?View=1&Query=pod&FilteredDictionaryIds=134) [pred](https://fran.si/iskanje?View=1&Query=pred&FilteredDictionaryIds=134) | R (7) |
| `brada-kralja`, `okoli-mize` | kralja gen (whose), okoli mize gen | okoli: predl. z rod. | [SP2001](https://fran.si/iskanje?View=1&Query=okoli&FilteredDictionaryIds=134) | R, R |
| `z-vozom`, `h-kravam`, `vsi-v-hiso`, `z-mlekom` | verbless fragments | z: "II. z or."; k: "pred besedami na glasova k, g varianta h"; v: "s tož." | [z](https://fran.si/iskanje?View=1&Query=z&FilteredDictionaryIds=134) [k](https://fran.si/iskanje?View=1&Query=k&FilteredDictionaryIds=134) [v](https://fran.si/iskanje?View=1&Query=v&FilteredDictionaryIds=134) | R (4) |
| `na-kozolcu` | na kozolcu, loc | na: z mest.; its German and Italian are no Slovene question | [SP2001 na](https://fran.si/iskanje?View=1&Query=na&FilteredDictionaryIds=134) | R |
| `nom-sta-soseda` | soseda, nom.du after sta | sôsed -éda: dual soseda (Sloleks) | [Sloleks](https://viri.cjvt.si/sloleks/slv/headword/90292) | R |
| `dajte-mi-vedri` | vedri, acc.du | the form is right (védro, dv. vedri); "Dajte mi vedri." without obe is a naturalness question | | N |
| the case lens's labelled items with a usage note (30, bridge/test/fixtures/case-lens-*.json) | "Kruh peče babica." (the object first), "Ravno prihajam s tržnice." (s + gen), "Luka ima tri ovce." … | the cases each preposition and verb above takes | | R |

### Spoken and standard: the hour

| item | the app | the source | URL | decision |
|---|---|---|---|---|
| "ob dvanajst", "ob deset", "ob sedem", "ob štiri" (Pod uro, Pri kapelici, Na vasi) | right, "(as people say it)", the reply saying ob dvanajstih back | SP2001 pet: "Ura je pet; priti ob petih"; Jezikovna svetovalnica (M. Snoj, 2015): the norm declines pet with its endings (SP2001 §876 "navadno"); the zero ending is outside it: spoken | [SP2001 pet](https://fran.si/iskanje?View=1&Query=pet&FilteredDictionaryIds=134) [Svetovalnica](https://svetovalnica.zrc-sazu.si/topic/1110) | V (the labelling is right) |
| V hiši: "Vstal sem ob šest." | a wrong choice: "Ob šest? Šest česa?" | as above: spoken, never a mistake | as above | F (right, the written form said back) |
| V cerkvi: "V nedeljo ob deset." | a wrong choice | as above | as above | F |
| V podstrešju: "Šola je ob osem!" | a wrong choice, the turn's only one | as above | as above | F (right; the turn now asks "Ura je sedmih", N for its wording) |
| Primorska V gorah: "Jutri zjutraj, ob šest." | a wrong choice | as above | as above | F |

### The places' words (docs/plans/05-built/places-sl.md, "For the native speaker")

| word | the app | the source | URL | decision |
|---|---|---|---|---|
| bulin (the jack, Na balinišču) | the pack's word, "the word of the Primorska courts" | SSKJ² balín -a m "najmanjša krogla pri balinanju: zadeti balin"; SP2001 balinišče: "vreči balin na sredo balinišča"; the BZS rules: balin. No dictionary has bulin | [SSKJ² balin](https://fran.si/iskanje?View=1&Query=balin&FilteredDictionaryIds=133) [SP2001 balinišče](https://fran.si/iskanje?View=1&Query=balinišče&FilteredDictionaryIds=134) | V (kept, the note names balin; N: does she say it) |
| brajda | the pergola | brájda -e ž: "Po brajdah zori grozdje" | [SP2001](https://fran.si/iskanje?View=1&Query=brajda&FilteredDictionaryIds=134) | R |
| zbiti | knock away a ball | zbíti zbíjem: "zbiti nasprotnika na tla"; the BZS rules: zbijanje (shooting) beside bližanje | [SP2001](https://fran.si/iskanje?View=1&Query=zbiti&FilteredDictionaryIds=134) | R |
| bližja / bližje | Katera je bližje? / Vaša je bližja. | blížji -a -e (the comparative of blizek); bližje the adverb | [SP2001](https://fran.si/iskanje?View=1&Query=bližji&FilteredDictionaryIds=134) | R |
| "Bolj bližje?" | a wrong choice (bolj + a comparative) | no entry settles how a native hears it | | N |
| "Rana ura, zlata ura" | Janez's proverb | SSKJ²: "preg. rana ura – zlata ura" | [SSKJ²](https://fran.si/iskanje?View=1&Query=rana+ura&AllNoHeadword=rana+ura&FilteredDictionaryIds=133) | R |
| mreža | the chapel's grille | mréža -e ž: "kovinska mreža" | [SP2001](https://fran.si/iskanje?View=1&Query=mreža&FilteredDictionaryIds=134) | R |
| avemarija | the evening bell | avemaríja -e ž: "zvoniti avemarijo" | [SP2001](https://fran.si/iskanje?View=1&Query=avemarija&FilteredDictionaryIds=134) | R |
| zvoniti proti toči | ringing against hail | SSKJ² zvoniti: "preg. po toči zvoniti je prepozno": the custom | [SSKJ²](https://fran.si/iskanje?View=1&Query=zvoniti&FilteredDictionaryIds=133) | R |
| brizgalna | the fire pump | brizgálna -e ž: "gasilska brizgalna" | [SP2001](https://fran.si/iskanje?View=1&Query=brizgalna&FilteredDictionaryIds=134) | R |
| cev | the hose | cév -i ž: "vodovodna cev" | [SP2001](https://fran.si/iskanje?View=1&Query=cev&FilteredDictionaryIds=134) | R |
| prapor | the society's banner | prápor -a tudi -ja: "gasilski prapor" | [SP2001](https://fran.si/iskanje?View=1&Query=prapor&FilteredDictionaryIds=134) | R |
| Florjanovo | capital in a sentence ("Četrtega maja je Florjanovo.") | florjánovo -ega s, small (as martínovo -ega s, "na martinovo") | [SP2001 florjanovo](https://fran.si/iskanje?View=1&Query=Florjanovo&FilteredDictionaryIds=134) [martinovo](https://fran.si/iskanje?View=1&Query=martinovo&FilteredDictionaryIds=134) | F (florjanovo, and "na martinovo" in the festivals and the Martinovo pack) |
| ristanc | hopscotch | rístanc -a m: "igrati se ristanc" | [SP2001](https://fran.si/iskanje?View=1&Query=ristanc&FilteredDictionaryIds=134) | R |
| plezalo | the climbing frame | plezálo -a s: "postaviti nova plezala na igrišču" | [SP2001](https://fran.si/iskanje?View=1&Query=plezalo&FilteredDictionaryIds=134) | R |
| hiška | the playhouse | híška -e ž, manjš. | [SP2001](https://fran.si/iskanje?View=1&Query=hiška&FilteredDictionaryIds=134) | R |
| lopatka | the little spade | lopátka -e ž, manjš. | [SP2001](https://fran.si/iskanje?View=1&Query=lopatka&FilteredDictionaryIds=134) | R |
| gugati se | to swing | gúgati se: "gugati se na gugalnici" | [SP2001](https://fran.si/iskanje?View=1&Query=gugati&FilteredDictionaryIds=134) | R |
| kol | the stake | kòl kôla m | [SP2001](https://fran.si/iskanje?View=1&Query=kol&FilteredDictionaryIds=134) | R |
| zapah | the gate's bar | zapàh -áha m: "vrata z zapahom" | [SP2001](https://fran.si/iskanje?View=1&Query=zapah&FilteredDictionaryIds=134) | R |
| tečaj | the hinge | tečáj -a m: "sneti vrata s tečajev" | [SP2001](https://fran.si/iskanje?View=1&Query=tečaj&FilteredDictionaryIds=134) | R |
| škripati | to creak | škrípati -ljem/-am | [SP2001](https://fran.si/iskanje?View=1&Query=škripati&FilteredDictionaryIds=134) | R |
| krošnjar | the pedlar | krošnjár -ja m člov. (and a table) | [SP2001](https://fran.si/iskanje?View=1&Query=krošnjar&FilteredDictionaryIds=134) | R |
| suha roba | wooden ware | róba: "obrt. suha roba" | [SP2001](https://fran.si/iskanje?View=1&Query=roba&FilteredDictionaryIds=134) | R |
| kanja | the buzzard | kánja -e ž, a bird | [SP2001](https://fran.si/iskanje?View=1&Query=kanja&FilteredDictionaryIds=134) | R |
| žig | the stamp | žíg -a m: "odtisniti žig" | [SP2001](https://fran.si/iskanje?View=1&Query=žig&FilteredDictionaryIds=134) | R |
| vpisati se | sign the book | tečaj: "vpisati se v tečaj" | [SP2001](https://fran.si/iskanje?View=1&Query=tečaj&FilteredDictionaryIds=134) | R |
| zarja | the dawn | zárja -e ž: "jutranja zarja" | [SP2001](https://fran.si/iskanje?View=1&Query=zarja&FilteredDictionaryIds=134) | R |
| mlaj | the maypole | mláj 2 -a m: "postaviti mlaj" | [SP2001](https://fran.si/iskanje?View=1&Query=mlaj&FilteredDictionaryIds=134) | R |
| godba | the band | gódba -e ž: "godba na pihala" | [SP2001](https://fran.si/iskanje?View=1&Query=godba&FilteredDictionaryIds=134) | R |
| "Živijo!" as a toast | | žívijo, gl. žívio 2: "živio, sta trčila in izpila" | [SP2001](https://fran.si/iskanje?View=1&Query=živio&FilteredDictionaryIds=134) | R |
| štirna (the draw well) | the pack's word | SSKJ² "štérna tudi štírna, nižje pog. vodnjak" | [SSKJ²](https://fran.si/iskanje?View=1&Query=šterna&FilteredDictionaryIds=133) | V (spoken, said in its note) |
| curek | the jet | cúrek -rka m: "curek vode" | [SP2001](https://fran.si/iskanje?View=1&Query=curek&FilteredDictionaryIds=134) | R |
| četrt na enajst | 10:15 | četŕt 3 -- ž, undeclined: "četrt litra", "tri četrt ure" | [SP2001](https://fran.si/iskanje?View=1&Query=četrt&FilteredDictionaryIds=134) | R |
| navijati uro | winding the clock | navíjati: "navijati uro" | [SP2001](https://fran.si/iskanje?View=1&Query=navijati&FilteredDictionaryIds=134) | R |
| zamuja / prehiteva | the clock slow, fast | prehitévati: "Ura prehiteva"; zamújati: "Letalo zamuja" | [prehitevati](https://fran.si/iskanje?View=1&Query=prehitevati&FilteredDictionaryIds=134) [zamujati](https://fran.si/iskanje?View=1&Query=zamujati&FilteredDictionaryIds=134) | R |
| narasel | "Potok je narasel." | narásti 1 -rástem (narastel) preferred; narásti 2 -rásem, "gl. narasti 1" (narasel) | [SP2001](https://fran.si/iskanje?View=1&Query=narasti&FilteredDictionaryIds=134) | V (kept; N: which she says) |
| upade | "voda upade" | upásti: "Voda je upadla" | [SP2001](https://fran.si/iskanje?View=1&Query=upasti&FilteredDictionaryIds=134) | R |
| toplar | the double hayrack | tóplar -ja m, *pokr.* dvojni kozolec | [SP2001](https://fran.si/iskanje?View=1&Query=toplar&FilteredDictionaryIds=134) | V (regional, said in its note) |
| lata | the lath | láta -e ž: "plot iz lat" | [SP2001](https://fran.si/iskanje?View=1&Query=lata&FilteredDictionaryIds=134) | R |
| kopica | the haycock | kopíca -e ž: "kopica sena" | [SP2001](https://fran.si/iskanje?View=1&Query=kopica&FilteredDictionaryIds=134) | R |
| vile | pitchfork, plural | víle víl ž mn. | [SP2001](https://fran.si/iskanje?View=1&Query=vile&FilteredDictionaryIds=134) | R |
| facelija | phacelia | facélija -e ž, a plant | [SP2001](https://fran.si/iskanje?View=1&Query=facelija&FilteredDictionaryIds=134) | R |
| točiti med | extracting honey | SSKJ² točiti: "čeb. točiti med: s točilom spravljati med iz satja" | [SSKJ²](https://fran.si/iskanje?View=1&Query=točiti&FilteredDictionaryIds=133) | R |
| "v kepi" (the bees' winter) | Anton: "Vse skupaj, v kepi." | SSKJ² kepa: snow, a lump; Čebelarski terminološki slovar: gruča, čebelja gruča | [SSKJ² kepa](https://fran.si/iskanje?View=1&Query=kepa&FilteredDictionaryIds=133) [gruča](https://fran.si/iskanje?View=1&Query=gruča&FilteredDictionaryIds=145) | F (v gruči; N: natural?) |
| "Nisva skupaj krav pasla." | the old man to a young one's ti | SSKJ²: "saj nisva skupaj krav pasla: nisva enaka, iste starosti" | [SSKJ²](https://fran.si/iskanje?View=1&Query=krav+pasla&AllNoHeadword=krav+pasla&FilteredDictionaryIds=133) | R |
| "pojdiva vprašat", "stražiti mlaj", "zdaj bo bila", "Mir je največ, kar imamo", "porini me", "pol ure čez eno" | | phrasing, not a dictionary's question | | N (6) |

### The strings

| item | the app | the source | URL | decision |
|---|---|---|---|---|
| `face.*` (se smehlja, se čudi, zardeva …) | TalkBack's faces | read through: everyday verbs and phrases, grammar and spelling right | | R |
| `tips.*`, `quests.*` (the guidance) | the tips' lines | read through: grammar and spelling right (the plural forms: eno besedo, dve besedi, tri besede, pet besed) | | R |
| živjo (`tips.word.hello`) | a child's hello | eSSKJ "žívjo in žívio in žívijo, manj formalno" | [eSSKJ](https://fran.si/iskanje?View=1&Query=živjo&FilteredDictionaryIds=201) | R |
| noter ("Pojdi «noter»!") | where to | nóter smer. prisl.: "stopiti noter" (where: notri) | [SP2001](https://fran.si/iskanje?View=1&Query=noter&FilteredDictionaryIds=134) | R |
| vadnica ("Vadnica sklonov") | | vádnica -e ž: "angleška vadnica" | [SP2001](https://fran.si/iskanje?View=1&Query=vadnica&FilteredDictionaryIds=134) | R |
| moba (`tips.repair`) | "pomagajo sosedje (moba)" | in no Fran dictionary | [Fran](https://fran.si/iskanje?View=1&Query=moba&AllNoHeadword=moba) | N |
| `cases.*`, `caseLens.*` | the trainer's and the lens's labels | read through: grammar and spelling right; the case names and questions as the grammar book's | | R |
| "Pogovorno, prav tako prav" (`cases.spoken`) | | naturalness | | N |
| "po: za čem, okoli" (`caseLens.senseAfter`) | | naturalness | | N |

### Not counted: the content-fixes branch

The content-fixes branch (not on main while this was checked) has new Slovene lines; their grammar and spelling are
right by the same entries (trta: "petdeset trt", "k vsaki trti"; "v Trnovem"; "pod Dobrač", "Ziljska dolina", "blizu
Gorice"), and their naturalness is on that branch's own list (docs/plans/05-built/content-fixes.md). Its open "pote or
po te?" is settled above: pote.

## How it is built

- `bridge/src/forms.ts`: a forms book table's optional `variants` (by slot, each a word, at most four), accepted as
  `also` and served as the slot's `variants` (the app labels one typed, "🔀 Starinska ali narečna oblika, prav tako
  sprejeta; knjižno: …", as for a regular noun's); `dialog-words.ts` reads them as the word's forms too.
- The tables were written by a script from the cached entries (the scratchpad's `native-check-sl/scripts/tables.py`:
  SP2001's forms as decided noun by noun, Sloleks's paradigm for every slot, the spoken forms by its labels and
  frequencies, the old forms the regular check had accepted), then read through; the generator isn't part of the app.
- Tests: the forms smoke has the new tables' cases (dan dneva or dne, dni, also dnevih; hči hčer and never hči as the
  accusative; otroci, otrocih, otrokih accepted, never z otroci; vrata vrati, vratmi; oko's singular and dual; med's
  singular; tesar tesarja; sin also sinu; dekle also dekleta; bratje before brati; zvonovi, zvoni kept; a table's old
  forms as variants, bratu, also brati) and expects no noun of the packs or the bank with a slot not checked;
  `PhoneBridgeTest.everyNounSklanjajCanPickIsSure` likewise through the phone's bridge.

## What's left

- **Jan's wife's list** above; a "yes" to a refused spoken form is one `also` line in its table.
- **The regular check** still takes a masculine's -i and -je plurals and its -ov- plural as equal for every noun without
  a table; SP2001's order was checked for the nouns that have two (above), not for every noun the dictionary may learn.
- **The 30 verbs of giving** (dati, prinesti, povedati …) and the words of feeling (všeč, mrzlo …) follow the standard
  pattern and weren't looked up one by one.
- **Words no dictionary has**: moba, bulin (as the jack); the proverbs and the phrases in her list.
- **Voices**: the changed lines (the four hours, V podstrešju's new choice, Anton's gruča, florjanovo) get their clips
  on demand, as any new line.
