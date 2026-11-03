# Primorska's readings and stories to B2 (readings-primorska, built 2026-11-03)

Jan, 2026-11-03: "I want to have app finished to release" (followups-16.md, section 2). Jan learns Slovene aiming at B2. Before
this branch Primorska's reading corner stopped at B1 (A1 2, A2 5, B1 2) and Stari Janez told 7 of his 16 stories at B1 and
none at B2. Now the corner has three readings at B1 and three at B2, and every one of the sixteen stories is told at A1,
A2, B1 and B2, in every chapter. Everything written here is machine-written (Claude) and needs a native check.

**No format or validator change.** `readings.ts` (READING_LEVELS to B2) and `stories.ts` (LEVELS to C2) took it as content.
The app picks a telling with `Stories.levelFor` (the highest at or below the learner's level), so a B1 learner now hears the
new B1 tellings, a B2 or C1 learner the B2 ones, and an A1 or A2 learner exactly what they heard before.

## Native check first

All new Slovene is machine-written; the German and Italian translations too. What most wants a native ear (Jan's wife; the
rule: a form people say is never wrong, so a wrong choice that turns out to be said must go):

1. **Wrong choices that rest on the norm, where speech may differ.** If any is said by adults, it has to stop being a wrong
   choice:
   - the genitive of negation on a relative pronoun: *skrivnost, ki **jo** ne bi smela* (Ajdovska deklica B1), *z lažjo, ki
     **jo** ni mogla zavrniti* (Lepa Vida B2), *kar ne moremo imeti* for *česar* (Lepa Vida B2), *ki se **jo** vsi bojijo*
     (Bogatinovo zlato B2), *ki se **jo** je vsa dolina bala* (Kekec B2), *ki so se **jo** izogibali* (Kekec B2);
   - *po **nekaj*** for *po nečem* (Lepa Vida B2); *tisto, **ki*** for *tisto, kar* (Zlatorog B2, Jurij Kozjak B2 ch. 3);
   - *mi bo zeblo* for *me bo zeblo* (Kralj Matjaž B2); *se me zdi* for *se mi zdi* (Ajdovska deklica B2, Zmaj B2);
   - *da naj* and *če* in indirect questions and polvikanje (*ste rekel*) were avoided on purpose: none is offered as wrong.
     Dual traps were avoided too (Primorska's speech uses the plural for the dual in places); the existing A2/B1 tellings
     keep theirs.
2. **Idioms and literary forms to confirm as natural:** *imeti koga za norca*, *postaviti vse na glavo*, *prekipelo mi je*
   (fran.si's Pravopis: "brezos., poud.") and Janez's pun *mleko prekipi, človeku pa prekipi* (Peter Klepec B2); *zbudivši
   se* (Peter Klepec B2), *ležeč na dnu jezera* (Zvon želja B2), *sedeča za pečjo* (Pegam in Lambergar B2), *ne vedoč*
   (Jurij Kozjak B2); *čim lepša je bila, tem bolj je bila prevzetna* (Povodni mož B2); *biti kos komu*, *kakršen je bil*
   (Martin Krpan B2); *pameti je imela za tri* (Mojca Pokrajculja B2); *se je bil premagal / ni bil doživel* pluperfects.
3. **Janez's humour and voice** in the replies to wrong choices (one per wrong choice, 2 to 3 sentences): that they sound
   like an old man from the Trnovo plateau, not a textbook.
4. **The readings:** *Osmica* (B1), *Dve mesti, en trg*, *Soška fronta*, *Pismo iz Cerknega* (B2): register, the radio
   piece's style, Mojca's letter. The grandfather's memories in *Soška fronta* are invented framing round the sourced facts.
5. **Facts** (each from the sources below; worth a look): the osmica's 1784 decree; Nova Gorica built from 1948 to Ravnikar's
   plans, the fence till 2004, Schengen in 2007, GO! 2025's 1,600 events and its opening on 8 February; the Isonzo Front's
   twelve battles, Kobarid 1917, the Walk of Peace's 500 km and 300 monuments; the laufarija's masks, the Pust of moss
   (about 100 kg), its revival in 1956 and register entry in 2012; the Celje counts' three stars in Slovenia's arms; Pliny on
   the Argo carried from Nauportus; the Dragon Bridge of 1901; Polixena at Bled; Mohorjeva's prize for Jurij Kozjak.

## What

### Four readings (`companion/cultures/primorska/readings/`)

As the corner's newer readings: five questions, one of every type (main idea, detail, a word in context, true or false,
between the lines), seven or eight new words meant in en, de and it, topics, `machine_written`, every line in sl, en, de,
it. They are the corner's, not the chest's.

| Reading | Kind, level, read by | Words (sl) | What | Sources |
|---|---|---|---|---|
| `osmica` Osmica | page, B1, Vinar Marko | 205 | His grandfather's spring walks to the osmice; a farm selling its own wine and food a few days a year; Joseph II's eight tax-free days of 1784; most on the Karst near Trieste, some on the Slovene Karst, in the Vipava valley and Istria; the green branches at the crossroads, the old bunch of ivy; the long table, what is served; Marko opening one this year | [sl.wikipedia: Osmica](https://sl.wikipedia.org/wiki/Osmica) (the eight days tax-free, the ivy), [it.wikipedia: Osmiza](https://it.wikipedia.org/wiki/Osmiza) (Joseph II, 1784, the frasca, where) |
| `dve-mesti-en-trg` Dve mesti, en trg · Two towns, one square | article, B2, Ančka (the radio) | 281 | One foot in each country on Europe Square; the 1947 Paris treaty; the Slovenes left without their town; Ravnikar's plans, building from 1948; a fence for almost sixty years, permits; pulled down in 2004; checks till December 2007 (Schengen); GO! 2025, the first cross-border capital of culture, "GO! Borderless", opened on Slovene Culture Day, 1,600 events | [en.wikipedia: Transalpina Square](https://en.wikipedia.org/wiki/Transalpina_Square), [en.wikipedia: Nova Gorica](https://en.wikipedia.org/wiki/Nova_Gorica), [en.wikipedia: GO! 2025](https://en.wikipedia.org/wiki/GO!_2025) |
| `soska-fronta` Soška fronta · The Isonzo Front | page, B2, Stari Janez | 283 | His grandfather, 19, sent to Sabotin in 1915; Italy's declaration of war in May; twelve battles in two and a half years, hundreds of thousands dead; trenches in the rock, thirst; songs from the Italian trenches; the breakthrough at Kobarid in autumn 1917, the retreat to the Piave; Hemingway's novel of a retreat he hadn't lived through; "Up there, there were no winners"; the Walk of Peace, the Park of Peace on Sabotin | [en.wikipedia: Battles of the Isonzo](https://en.wikipedia.org/wiki/Battles_of_the_Isonzo), [thewalkofpeace.com](https://www.thewalkofpeace.com/) (over 500 km, more than 300 monuments, the Sabotin Park of Peace) |
| `pismo-iz-cerknega` Pismo iz Cerknega · A letter from Cerkno | letter, B2, Učiteljica Mojca | 237 | The laufarija seen at last; the laufarji's carved wooden masks (the lamant's of goatskin) and their names; the Pust of moss, about 100 kg; the indictment on Carnival Sunday, the sentence on Shrove Tuesday, ta star's blow, winter carried off with him; nearly gone after the war, revived in 1956, on the heritage register since 2012; next year the two of them go together | [sl.wikipedia: Laufarija](https://sl.wikipedia.org/wiki/Laufarija) |

Primorska's levels now: A1 the morning, Marko's letter; A2 the frtalja, the pratika, the proverbs, the dual, the bora; B1 the
potica, Zdravljica, the osmica; B2 the two towns, the Isonzo Front, the letter from Cerkno.

### B1 and B2 tellings of Janez's sixteen stories (`companion/cultures/primorska/stories/`)

36 new tellings: B1 for the nine that had none (Peter Klepec, Povodni mož, Zvon želja, Desetnica, Ajdovska deklica, Mojca
Pokrajculja, Kekec, Martin Krpan's four evenings, Jurij Kozjak's three) and B2 for all sixteen (Veronika Deseniška's two
evenings, Krpan's four, Kozjak's three). Each telling 10 to 16 lines (the format's cap is 16), three or four turns of three
choices, every wrong choice with its why in en, de and it and Janez's reaction; the notebook's notes for each (its narration
as prose, the asides left out, a place for each picture); the pictures are the stories' own (one set for every level), so
each telling keeps the paragraphs they follow. The learner's gender in `{m:…|f:…}` pairs throughout (the learner-content
audit: 0 malformed, 0 turns the same for one gender). Every story's `review` now ends "B1, B2 machine-written" or "B2
machine-written".

**What a B1 telling does over A2:** the story in the past with subordinate clauses (ki, ker, da, čeprav, dokler ne), the
conditional, reported speech; turns on the cases verbs and prepositions take (pomagati, zaupati, smejati se komu; bati se,
spominjati se česa; v/na with the accusative or the locative; iz, do, zaradi with the genitive), the genitive of negation,
ki with its pronoun, reported speech keeping its tense, the numbers' cases, ti/vi with Janez.

**What a B2 telling does over B1:** more of the source (names, dates, places, who wrote it down, what remains today), denser
lines; participles and gerunds (zbudivši se, ležeč, narejenega, ne vedoč), the past conditional and pluperfect, naj bi for
hearsay, kateri, kakršen, kar after tisto, ne da bi, namesto da bi, čim … tem, idioms; turns on all of these and the
B1 ones in harder sentences. The wrong choices name B2 pages where the rule is one (`predlogi-dva-sklona`, `delezniki`,
`pogojnik-pretekli`, `oziralni-kateri`, `odvisniki-napredno`, `stopnjevanje`, `brezosebno`, `trpnik`) and the A1–B1 pages
where it is theirs (`rodilnik-nikalnica`, `dajalnik`, `tozilnik`, `odvisni-govor`, `svoj`, …): the chip and the meetings fire
on them.

| Story | New | Sources | B2 adds |
|---|---|---|---|
| Zlatorog | B2 | [sl.wikipedia: Zlatorog](https://sl.wikipedia.org/wiki/Zlatorog) (Deschmann 1868, Baumbach) | the white ladies' herd, the snake under Bogatin, the Triglav roses the girl asks for, Deschmann and Baumbach, the beer label |
| Kralj Matjaž | B2 | [sl.wikipedia: Kralj Matjaž](https://sl.wikipedia.org/wiki/Kralj_Matja%C5%BE), [en.wikipedia: Matthias Corvinus](https://en.wikipedia.org/wiki/Matthias_Corvinus) | Matthias Corvinus, why people wouldn't believe he died, the linden as the village's tree |
| Martin Krpan (4 evenings) | B1, B2 | [sl.wikipedia: Martin Krpan z Vrha](https://sl.wikipedia.org/wiki/Martin_Krpan_z_Vrha) (Slovenski glasnik, 1 July 1858), [sl.wikisource](https://sl.wikisource.org/wiki/Martin_Krpan_z_Vrha) | Levstik writing to show how Slovene prose is told, the empress beside herself, the letter with the seal, the most Slovene of heroes |
| Peter Klepec | B1, B2 | [sl.wikipedia: Peter Klepec](https://sl.wikipedia.org/wiki/Peter_Klepec) | Osilnica, the fairies he shades with branches, the miraculous water, the Turks in Osilnica's church, the spruce of Fažonci, his statue in Mali Lug |
| Lepa Vida | B2 | [sl.wikipedia: Lepa Vida](https://sl.wikipedia.org/wiki/Lepa_Vida) | Prešeren's retelling (1832), Cankar's play, Lepa Vida as the name for longing |
| Povodni mož | B1, B2 | [sl.wikisource: Povodni mož (Prešeren)](https://sl.wikisource.org/wiki/Povodni_mo%C5%BE_(France_Pre%C5%A1eren)), [sl.wikipedia: Povodni mož](https://sl.wikipedia.org/wiki/Povodni_mo%C5%BE) | the ballad's first and last couplets (public domain), seven striking, Prešeren's fair-haired youth beside the pale man in green, the boatmen |
| Zvon želja | B1, B2 | [blejskiotok.si: legends](https://blejskiotok.si/en/attractions/legends) | Polixena, the steward's widow of the 16th century behind the legend, the Pope's blessed bell, the 54-metre tower |
| Desetnica | B1, B2 | [sl.wikipedia: Desetnica (pravljica)](https://sl.wikipedia.org/wiki/Desetnica_(pravljica)) | Fran Milčinski's telling: the bird with the golden ring, the bread cut in ten, the trees that warn of lightning, the tenth child's gifts |
| Ajdovska deklica | B1, B2 | [sl.wikipedia: Ajdovska deklica](https://sl.wikipedia.org/wiki/Ajdovska_deklica) (Dušica Kunaver, Babica pripoveduje) | the Russian Road, her sisters' curse, the face seen from the Poštarski dom and the Erjavčeva koča |
| Zmaj ljubljanski | B2 | [sl.wikipedia: Ljubljanski zmaj](https://sl.wikipedia.org/wiki/Ljubljanski_zmaj), [Pliny, Naturalis Historia III.128](https://la.wikisource.org/wiki/Naturalis_Historia/Liber_III), [en.wikipedia: Dragon Bridge](https://en.wikipedia.org/wiki/Dragon_Bridge_(Ljubljana)) | the sleepless dragon of the fleece, the Argo carried on shoulders from Nauportus, the bridge of reinforced concrete first named for Franz Joseph |
| Mojca Pokrajculja | B1, B2 | [Blažić, hrcak.srce.hr/file/299860](https://hrcak.srce.hr/file/299860) (ATU 283C*, Moderndorfer 1924, the Mežica valley), [delo.si: Mojca Pokrajculja je Korošica](https://www.delo.si/novice/slovenija/mojca-pokrajculja-je-korosica) | the Carinthian tale, Mojca's one condition, the fox's late confession (this pack's retelling, as at A1/A2) |
| Bogatinovo zlato | B2 | [sl.wikipedia: Zlatorog](https://sl.wikipedia.org/wiki/Zlatorog) (Deschmann's tale) | naj bi, the Venetians in league with the devil, the horn rubbed off, the cradle in 700 years |
| Pegam in Lambergar | B2 | [sl.wikipedia: Pegam in Lambergar](https://sl.wikipedia.org/wiki/Pegam_in_Lambergar) | opened from the painted beehive panels, Jan Vitovec and Behaim, the sealed letter, Kamen above Begunje |
| Kekec | B1, B2 | [sl.wikipedia: Kekec na volčji sledi](https://sl.wikipedia.org/wiki/Kekec_na_vol%C4%8Dji_sledi) (Zvonček, 1922; the film Srečno, Kekec!, 1963, Ruša Bojc) | Vandot's monthly instalments, Pehta who wasn't used to kindness, the film with Pehta |
| Veronika Deseniška (2 evenings) | B2 | [sl.wikipedia: Veronika Deseniška](https://sl.wikipedia.org/wiki/Veronika_Deseni%C5%A1ka) (Župančič's drama, 1924) | the three stars in Slovenia's arms, the first wife's unclear death, the oath, the skilful advocate, the family extinct some thirty years later |
| Jurij Kozjak (3 evenings) | B1, B2 | [sl.wikisource: Jurij Kozjak, slovenski janičar](https://sl.wikisource.org/wiki/Jurij_Kozjak,_slovenski_jani%C4%8Dar), [sl.wikipedia: Josip Jurčič](https://sl.wikipedia.org/wiki/Josip_Jur%C4%8Di%C4%8D) (Mohorjeva's prize, 1864; about fifty languages) | the devşirme in a sentence, Jurij sparing children and women, the beacon fires as a telegraph, Samol's conscience |

Two lines were corrected against their sources after writing (05ee82fc, a7ad04ef): the Dragon Bridge is not "one of
the first concrete bridges in Europe", and no source checked here calls Jurij Kozjak "one of the first historical novellas".

## Slovene settled with fran.si (one request at a time)

| Word or phrase | Where | What fran.si has |
|---|---|---|
| našemiti, našemljen | Pismo iz Cerknega | SSKJ²: "obleči v (pustno) šemo"; *našemljeni otroci* ([fran.si](https://fran.si/iskanje?View=1&Query=na%C5%A1emiti)) |
| zamreti | Pismo iz Cerknega | SSKJ²: dov.; "običaj je po vojni zamrl" ([fran.si](https://fran.si/iskanje?View=1&Query=zamreti)) |
| obtožnica | Pismo iz Cerknega | SSKJ²: ž; *prebrati obtožnico* ([fran.si](https://fran.si/iskanje?View=1&Query=obto%C5%BEnica)) |
| pridelati | Osmica | SSKJ²: dov.; "vino pridelajo doma" ([fran.si](https://fran.si/iskanje?View=1&Query=pridelati)) |
| zvrstiti se | Dve mesti, Soška fronta | SSKJ²: to occur one after another ([fran.si](https://fran.si/iskanje?View=1&Query=zvrstiti)) |
| prekipeti komu | Peter Klepec B2 | Pravopis: "brezos., poud. prekipeti komu: Prekipelo mu je" ([fran.si](https://fran.si/iskanje?View=1&Query=prekipeti)) |
| *biti ves ušesa* | (dropped) | not in SSKJ (only *sama ušesa so ga bila*): not used ([fran.si](https://fran.si/iskanje?View=1&Query=uho)) |

Sloleks wasn't queried separately: the forms the turns test are the regular ones of the grammar book's pages. Worth a
Sloleks pass with the native check: *lažjo* (laž), *ljudmi/ljudeh*, *nečem*, *dvajsetimi*.

## Tests and checks

- `bun test/check-culture.ts primorska`: ok after every story. `bun test/learner-content.ts`: 0 texts naming Jan, 0 malformed
  placeholders, 0 turns the same for one gender. `bunx tsc --noEmit -p .`: clean. `tutor-export --check`: 140 files match.
- **The smoke** (`test/smoke/stories.ts`): every one of Janez's stories at A1, A2, B1 and B2; the notebook's count now counts
  only the A1 and A2 notes (140), so that a pack's new tellings above A2 don't touch the number (the other cultures' readings
  agents add tellings too: merging, keep this line and drop theirs if they changed the old 148). `test/smoke/cultures.ts`:
  Primorska's readings at B1 and B2, every line in four languages, every question type in the new four.
- **StoriesTest:** all sixteen at A1–B2; tonight's story at B1, B2 and C1 (the highest there is: B2); Peter Klepec B2 for a C1
  learner; the A1/A2-only case and the retelling to a story's highest kept with a copy cut to A1/A2. **ReadingsTest:** the
  corner's B1 and B2 readings, their types, words and lengths, a B2 learner's corner starting with the B2 three.
- **QA** (`bin/qa`): `story-faces` takes `LANI_QA_STORY_LEVEL` (the hook `level:<lv>` for the run, cleared after). Run:
  `LANI_CULTURE=primorska LANI_QA_READING=soska-fronta LANI_QA_STORY_LEVEL=B1 companion/bin/qa-slot --steps
  culture-reading,story-faces`; screenshots in the scratchpad's `ux-readings-primorska/`.

## Voice (nothing spent)

New Slovene that a voice build would read: Janez's lines and replies in the 36 tellings, about 61,300 characters (780
texts); the learner's choices, about 25,600 (420); the four readings' lines, about 5,500 (Marko, Ančka, Janez, Mojca); the
notes, if the notebook reads them, about 29,100. Without the notes about 92,000 characters, more than a month's credits:
Jan's call. Until then the app speaks them with the phone's voice, as any line without a clip.

## Left

- The native check above.
- `about` of each story still describes its A1/A2 telling; the B2 sources are in this doc.
- The emulator QA covers one B2 reading and Zlatorog at B1 (tonight's story for a new learner); the other new tellings are
  covered by StoriesTest (every telling played through every wrong choice to its end) and the smoke.

## Commits

535cf7fc readings; 27a3c70e Peter Klepec; ad74e4d2 Povodni mož; 5d196446 Zvon želja; f987c5f1 Desetnica; b91de937 Ajdovska
deklica; ea99d305 Mojca Pokrajculja; 4badcfda Kekec; 2d4ad96f Zlatorog B2; 68167445 Kralj Matjaž B2; 05d4f068 Lepa Vida B2;
4a5f26b8 Zmaj B2; e029de2c tests; bef3e2c8 Bogatinovo zlato B2; 2ad7f242 Pegam in Lambergar B2; 27e24473 Veronika B2;
49e88e90 Krpan B1; 8823d356 Kozjak B1; 94995cbb Krpan B2; fc6cfcb7 Kozjak B2; 1570029a tests, SCENES.md, QA; 05ee82fc,
a7ad04ef source fixes.
