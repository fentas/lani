# Friuli's readings and tellings to B2 (readings-friuli, built 2026-11-03)

Wave E, batch 2b, section 2 (the lead's kit, followups-16.md): readings and stories to B2 for Friuli, Teo's village
(Italian, a child of seven learning from German, at A1 aiming at B1). Before this branch Friuli had readings A1 4, A2 4,
B1 1 and its sixteen stories told at A1 and A2 only. Now: readings A1 4, A2 4, **B1 3, B2 3**, and **every one of Nonno
Bepi's sixteen stories told at B1 and at B2 too**, with the notebook's notes for each telling, and the supplement's
meanings for every new word a tap meets. B1 is written for a child of Teo's age; B2 for an older learner, with nothing
unsuitable for a child (a legend's darker parts told as a children's book would). Everything here is machine-written
(Claude) and flagged as such; nothing is reviewed by a native speaker.

No format, validator or app change: readings.ts already takes B2 (READING_LEVELS), stories.ts B1 to C2 (LEVELS). The
test changes are two counts, the smoke's and NotebookTest's curated tellings and notes, **148 → 180** (Friuli's 32
added), and a new ReadingsTest case: every culture's readings as the app gets them pass its own check. Three other cultures' readings agents change the same two numbers: on a merge, add each branch's tellings to 148.
ItalianHintTest stays as it is, but its 95 % bites new Italian turns: see "The hint chip" below.

## Native check (first)

What most needs a native eye, the most heard first.

- **Italian, the B1 tellings' openings and turns** (what Teo hears first above A2, on the third round of retellings, see
  "Who hears them"): "Senti come scorre il torrente, laggiù?", "Ti è mai successo di non trovare più una cosa, anche se
  eri sicuro di averla messa al suo posto?", "Udine è tutta piatta, ma in mezzo alla città c'è un colle", "In novembre,
  a San Martino, in Friuli si arrostiscono le castagne", "Vedi come sale il fumo dal nostro fuoco?", and the learner's
  answers ("Me lo racconti?", "Io avrei paura!", "Così il diavolo restava a bocca asciutta", "Chissà che nostalgia").
- **Italian, the passato remoto in a child's telling.** B1 narrates the legends in the passato remoto (the course's B1
  page "The fairy-tale past") and Bepi's own memories in the passato prossimo and imperfetto (the Pignarûl, Sauris):
  is that what a Friulian grandfather telling a seven-year-old would do?
- **Italian, Bepi's reactions** (jokes a child should get, at most 80 characters): "Mi lo? Hm, quella «i» è caduta nel
  torrente!", "L'hanno? Quanti diavoli pensi che c'erano, una squadra?" (spoken "c'erano" in a joke), "Tutti dormiva?
  Tutti insieme, in un letto solo?", "Una gatto? Hm, il micio si offende, sai!", "Più fortissimo? Hm, troppa forza anche
  per la grammatica!", "Sarò? Tu? Allora sei tu l'anno nuovo! Auguri!", "Del zucchero? Hm, quella zeta vuole il suo
  «lo»!", "Lo ricostruì? Hm, quel pronome ha perso il tetto anche lui!", "Paura? Ai turisti fa più paura il prezzo del
  parcheggio!".
- **Italian, B2 style**: "Detto fatto", "Fatto sta che", "a guardarsi dentro", "felice come un'allodola", "chi la visita
  stenta a credere che sia stata quasi rasa al suolo", "ci si rimbocca le maniche", "Il nome viene dal friulano «orcul»,
  l'orco, con una desinenza che lo fa sembrare quasi un soprannome: un orcaccio".
- **The wrong choices** (every one a form no grown-up says, never one people say: no indicative after penso che, no
  imperfetto in an if-clause, no "se avrei"): averei, lascerebbi, vorrebbi, sceglierebbi, facciato, sceglito, dormeva,
  "più fortissimo", "mi ho vestito", "ci ho salito", "sono rimasto" after "se l'avessi visto", "aprirò" after "se
  bussasse", "tuttavia" for "in effetti". Is any of them said in Friuli after all?
- **Facts to check against the sources** (the URLs are below): the Grotta Gigante's hall "quasi cento metri" and 11 °C;
  Venzone's stones "raccolte, numerate e catalogate" (a travel article says so of the town; it.wikipedia of Gemona's
  cathedral); Palmanova's radial streets "so that every bastion could be reached quickly" (offered as the inference
  question's answer: the usual reading of the plan, not a quoted fact); the krivapete's Vernasso tale; the benandanti's
  body that must not be turned; the bora's seven days and the sumac; the Giro d'Italia's time trial on the Lussari in
  2023, won by a Slovene; cramâr from German Krämer; Sauris settled from beyond the Alps in the Middle Ages ("si
  racconta"); St Martin born in Pannonia, the son of an officer, and his dream of Christ in the half cloak.
- **Friulian** quoted: «Mandi!», Udin, Friûl, Patrie dal Friûl, Radio Onde Furlane, Gnovis, Zahre (Sauris in its own
  German), soreli jevât, soreli a mont (as the A2), orcul.
- **German (Teo's base)**: Perückenstrauch for il sommacco (the Karst's red is Cotinus; "Sumach" is the other plant),
  Glückshaube, Quatembernächte, Martinssommer (Austria: Martinisommer?), Plöckenpass, Saifnitz, Tarvis, Pulli,
  Fasching, Duineser Elegien, "Doch die Wissenschaft brachte eine Wendung", the readings' "du" and "ihr".
- **Slovene (the third base)**: the exonyms Pušja vas (Venzone) and Humin (Gemona), checked on sl.wikipedia
  (https://sl.wikipedia.org/wiki/Pušja_vas); Barnas for Vernasso and ruj for il sommacco, not checked; "rojen v
  srajčki", "kvatrne noči", "martinovo poletje", "jasna burja" and "temna burja", "Devinske elegije", "refoli" left
  Italian, "Velika jama" for the Grotta Gigante, "Rogerjev steber". The learner's past forms are gendered pairs
  throughout.
- **About the content, for a child**: the earthquake of 1976 at B2 says "quasi mille persone persero la vita" (the
  Venzone reading and the Orcolat's B2, as a children's history book would; B1 says only that many houses fell); the
  White Lady's B2 names the oldest versions' cruel husband without telling it ("noi diciamo soltanto che non la rendeva
  felice"); Lopichis's B2 tells the arrow he thought of aiming at his starving guide, and the wolf vanishing; the
  benandanti's B2 the judges of the 1500s and 1600s (the Inquisition unnamed); the Devil's Bridge blown up "durante la
  guerra" in 1917; the Pignarûl's "vai per il mondo" read as Friuli's emigration. Fire is never lit by a child: a wrong
  answer at B1 and B2 of the Pignarûl, as at A1 and A2.

## What

### Readings (`companion/cultures/friuli/readings/`)

As the corner's others: five questions of every type (main idea, detail, a word in context, true or false, between the
lines), seven new words meant in sl, en and de, topics, `machine_written`. Each read by someone of the cast, so it waits
until they live in the village and Teo has met them (`Readings.knows`).

| Reading | Kind, level, read by | What | Source |
|---|---|---|---|
| Una lettera dalla malga | letter, B1, Lorenzo | A week at the alpine pasture on the Montasio plateau: the cows up from June to September, milking at five, Montasio in the copper cauldron, the casera, a marmot, polenta and frico by the stove | [italia.it: Altopiano del Montasio](https://www.italia.it/it/friuli-venezia-giulia/udine/altopiano-del-montasio) (1,600–1,800 m, the casere, marmots); the region's malghe guide (Malga Montasio open mid-June to mid-September) |
| La Grotta Gigante | page, B1, Maestra Elena | The class's trip: the hall almost 100 m high, in the Guinness World Records since 1995, 11 °C, stalactites and stalagmites a millimetre in 15–20 years, the Colonna Ruggero (12 m, about 200,000 years), the two longest pendulums in the world, open since 1908; don't touch | [it.wikipedia: Grotta Gigante](https://it.wikipedia.org/wiki/Grotta_Gigante) |
| Venzone, pietra su pietra | article, B2, Fabbro Bruno | 6 May 1976 at nine, the September shocks, the cathedral of 1338 down, rebuilt where and as it was: the stones gathered, numbered and put back (anastilosi), the centre 1984–89, the cathedral 1988–95, "Prima le fabbriche, poi le case e poi le chiese", "Il Friuli ringrazia e non dimentica" | [it.wikipedia: Terremoto del Friuli del 1976](https://it.wikipedia.org/wiki/Terremoto_del_Friuli_del_1976), [Duomo di Venzone](https://it.wikipedia.org/wiki/Duomo_di_Venzone), [Venzone](https://it.wikipedia.org/wiki/Venzone); [borghibellifvg.it](https://borghibellifvg.it/en/the-villages/venzone/history); [visititaly.eu](https://www.visititaly.eu/it/luoghi-e-itinerari/i-borghi-piu-belli-d-italia-venzone-un-salotto-tra-le-mura) (the stones numbered) |
| Il friulano, una lingua di casa | article, B2, Maestra Elena | "Mandi!"; a Romance language of its own, Rhaeto-Romance with Romansh and Ladin; about 600,000 speakers, 420,000 from childhood; words from the 1200s, texts of the 1300s at Cividale; law 482/1999 and school; the official spelling of 1996; bilingual road signs (Udin); Gnovis and Radio Onde Furlane; 3 April 1077 | [it.wikipedia: Lingua friulana](https://it.wikipedia.org/wiki/Lingua_friulana), [Patria del Friuli](https://it.wikipedia.org/wiki/Patria_del_Friuli); festivals.json (patria_friuli) |
| Palmanova, la città stellata | page, B2, Zia Nives | The nine-pointed star, the first stone on 7 October 1593 against the Habsburgs, the bastions by the cannons' range, the three gates, the hexagonal Piazza Grande, two Venetian rings of walls and Napoleon's third (1805), UNESCO 2017 | [it.wikipedia: Palmanova](https://it.wikipedia.org/wiki/Palmanova) |

### Nonno Bepi's sixteen stories at B1 and B2 (`companion/cultures/friuli/stories/`)

Each telling as the A1 and A2 ones: the teller's lines in short parts and four turns between them, every wrong choice
with its why (sl, en, de) and Bepi's reaction, the right choices' replies; its notes for the notebook (four to five
paragraphs, it, sl, en, de, each picture placed). The pictures are the stories' own (no new ones): each telling keeps the
A2's four paragraphs, so they fall where they did. 32 tellings, 464 lines, 128 turns (62 with two right answers), 184 wrong choices, 127 of them
naming one of 51 Italian grammar pages (B1 tellings mostly A1–B1 pages, B2 mostly B2: congiuntivo imperfetto and
trapassato, condizionale passato, periodo ipotetico dell'irrealtà, discorso indiretto al passato, passivo, gerundio
passato, participio assoluto, alterati, connettivi, fare e lasciare, relativi il quale).

| Story | What B1 and B2 add to the A1 and A2 | Source |
|---|---|---|
| Le agane | B1: the Foràn di Landri; the thread that ends when the sister asks. B2: the agane's ancient knowledge, generous or touchy; reported speech of the pact | [turismofvg.it: Agane](https://www.turismofvg.it/de/2017/04/27/agane-wasserfeen-zwischen-hoehlen-und-wasserfaellen); [ilnordest.it](https://www.ilnordest.it/vivere-il-nord-est/turismo/bosco-folletto-anguane-crodares-storie-friuli-venezia-giulia-veneto-i5uup5me) |
| Il Ponte del Diavolo | The bridge on its natural boulder; B2: begun 1442, decades to finish, blown up 1917 and rebuilt 1918; cat, dog or pig | [it.wikipedia: Ponte del Diavolo (Cividale del Friuli)](https://it.wikipedia.org/wiki/Ponte_del_Diavolo_(Cividale_del_Friuli)) |
| Il colle di Udine | The 2022 excavation: the hill is man-made, but Bronze Age; B2: the siege of 452, the patriarchs' fortress lost in 1511, the castle begun 1517 | [it.wikipedia: Castello di Udine](https://it.wikipedia.org/wiki/Castello_di_Udine) |
| Gli sbilfs | The Licj who knots ropes and threads, the Bagan of the stables, red clothes, pumpkin and cornmeal soup | [ilnordest.it](https://www.ilnordest.it/vivere-il-nord-est/turismo/bosco-folletto-anguane-crodares-storie-friuli-venezia-giulia-veneto-i5uup5me); [giroditalia.it](https://www.giroditalia.it/en/news/giro-ditalia-2021-stage-14-cittadella-monte-zoncolan-mysterious-creatures/) |
| L'Orcolat | The keeper of the Carnic Alps' secrets, his name a pejorative of orcul; B2: 1976 and the rebuilding | [it.wikipedia: Orcolat](https://it.wikipedia.org/wiki/Orcolat); Terremoto del Friuli del 1976 (above) |
| La Madonna del Lussari | B2: the mountain of the three peoples, the pilgrims' path and its Via Crucis, the cable car, the Giro's time trial of 2023 | as stories-friuli: [avvenire.it](https://www.avvenire.it/attualita/dal-monte-lussari-a-san-pietro-la-fede-sulla-romea-strata_86313), [montagna.tv](https://www.montagna.tv/221436/monte-lussari-il-santuario-dove-si-vince-il-giro-ditalia/), [thewalkofpeace.com](https://www.thewalkofpeace.com/?p=79), [italia.it](https://www.italia.it/en/friuli-venezia-giulia/udine/mount-lussari-sanctuary) |
| Le krivapete | The Vernasso tale: the krivapeta who taught butter, cheese, bread, baskets and shoes, all but sweets; B2: the caves by the springs under the Matajur, herbs, rain, the loom | [preistoriainitalia.it, 2022](https://preistoriainitalia.it/en/2022/04/02/le-krivapete-delle-valli-del-natisone-udine/) |
| La Bora e Tergesteo | The clear and the dark bora; B2: the refoli over 170 km/h, the Argonaut, seven days a year, the sumac, the scholars' other etymology | [discover-trieste.it: The legend of the bora](https://www.discover-trieste.it/discover/welcome-to-trieste/history-and-legend/the-legend-of-the-bora) (after Edda Vidiz); [it.wikipedia: Bora](https://it.wikipedia.org/wiki/Bora) (171 km/h, 1954; refoli) |
| La Dama Bianca di Duino | The old castle's ruins on the spur; B2: the castle of 1389, Rilke's Duino Elegies of 1912, the oldest versions' husband named, not told | [it.wikipedia: Castello di Duino](https://it.wikipedia.org/wiki/Castello_di_Duino) |
| Lopichis e il lupo | The roofless house full of brambles, rebuilt; B2: Paul the Deacon's history of the Lombards, the arrow, the old woman feeding him little by little | Historia Langobardorum IV, 37: [the Foulke translation's introduction](https://elfinspell.com/MedievalMatter/PaultheDeacon-PaulusDiaconus/HistoryOfTheLangobards/PaulTheDeacon-Introduction.html); [Prosopography of the Byzantine Empire: Lopichis](https://pbe.kcl.ac.uk/data/D51/F62.htm) |
| I benandanti | B2: the body that must not be turned, the bloodless battle against hail and drought, the judges, Ginzburg's book of 1966 | [it.wikipedia: Benandanti](https://it.wikipedia.org/wiki/Benandanti) |
| Il mantello di San Martino | B2: Pannonia, the people who walked past, the dream of Christ, the farm contracts of 11 November | [it.wikipedia: Estate di San Martino](https://it.wikipedia.org/wiki/Estate_di_San_Martino), [Martino di Tours](https://it.wikipedia.org/wiki/Martino_di_Tours); festivals.json |
| Il fumo del Pignarûl | The Vecchio Venerando's torch, the cone of branches since 1928, the two sayings with their meaning; B2: the emigration "per il mondo" | as stories-friuli: [it.wikipedia: Pignarûl](https://it.wikipedia.org/wiki/Pignar%C3%BBl), [intrieste.com](https://intrieste.com/2026/01/07/fire-and-smoke-mark-the-turning-of-the-year-in-friuli-venezia-giulia/), [espansionetv.it](https://www.espansionetv.it/2023/01/06/epifaniavaticinio-friulano23-difficilema-migliore-del22) |
| Il viaggio del cramâr | B2: the word from German Krämer, the But and Degano valleys, eight to ten months away | as stories-friuli: [Fornasin, "Nach Carinthia" (Forum)](https://forumeditrice.it/percorsi/storia-e-societa/varia/le-cifre-le-anime/nach-carinthia-itinerari-di-cramars/download_capitolo), [scattidigusto.it](https://www.scattidigusto.it/cjarsons-la-ricetta-originale-degli-agnolotti-dolci-della-carnia) |
| La notte delle lanterne | B2: Sauris's own German (Zahre), told in the present as a living custom | as stories-friuli: [quotidiano.net](https://www.quotidiano.net/itinerari/friuli-venezia-giulia/carnevale-in-friuli-venezia-giulia-grande-festa-tra-maschere-di-legno-e-folclore-locale-yyj4ki6u), [montagna.tv](https://www.montagna.tv/104265/tutti-in-montagna-a-carnevale-tra-sfilate-maschere-tradizioni-leggende-e-buon-cibo/), [eventiesagre.it](https://www.eventiesagre.it/Carnevale_Carnevale/222_Carnevale+di+Sauris.html) |
| La camicia dell'uomo contento | B2: Calvino's Fiabe italiane of 1956, the tale told across Europe (ATU 844) | [en.wikipedia: Italian Folktales](https://en.wikipedia.org/wiki/Italian_Folktales) |

Each story's `review` now ends "B1 and B2 tellings and their notes (2026-11): machine-written, not reviewed".

### Every Italian word a tap meets (`companion/lexicon/`)

The new content brought about 840 words a tap meant nothing for. `it_cov_build.py`, run on the content as it is now,
keeps by itself the new forms of words its tables already have (the passato remoto, the congiuntivo imperfetto): that
took the count to 525. A table of their own, **`companion/lexicon/tools/it_cov_70_readings_friuli.txt`** (one lemma a
line, 455 lines), gives the rest: 371 new entries with meanings in English, German and Slovene, 41 `=pack` lines for
words packs-it's B1 and B2 packs brought meanwhile, and 43 `~` lines for entries the file or another table has (lungo,
povero, volere's "voler" of voler dire, accorgersi's "accorgersene" …). Every new prompt is its own in each base
(`--prompts`: 7 clashes, all main's; `test/prompt-clashes.ts`: no new one). `it.extra.json` is made by the tool (twice,
the same file):

```
git show 45924165:companion/lexicon/it.extra.json > /tmp/base.json
python3 -I companion/lexicon/tools/it_cov_build.py . /tmp/base.json --write     # --prompts: the clash check
```

`lexicon-build --coverage --lang it --all` (no Wiktionary, as a phone has it), over 356 files after merging main:
**99.9 % of tokens and 98.7 % of words, 101 words none knows**; this branch's base had 99.8 %, 97.8 % and 116. Of the new
content's words a tap still can't mean only the wrong forms the turns offer on purpose (averei, dormeva, facciato, lascerebbi, sceglierebbi, sceglito,
vorrebbi) and Enrico IV's numeral. The build's ABOUT note (in the script) isn't extended, to keep the shared script out
of the merge.

## Who hears them

- **Readings**: the corner lists every reading, the learner's level first, then the next one up, the easier, the harder
  (`Readings.corner`). Teo at A1 sees the B1 ones after his own and A2's, the B2 ones last; each once he has met its
  reader (Lorenzo, Maestra Elena, Fabbro Bruno, Zia Nives). The old app (0.2.698) knows `article` and B2.
- **Stories**: a learner hears tonight's story at their level, a retelling **a level up for each time it was told**
  (`Stories.levelFor`, unchanged since before 0.2.698; Primorska's B1 tellings have been served since the repository
  began). So Teo, at A1, hears the sixteen at A1, then retold at A2, and now **at B1 on the third round and at B2 on
  the fourth**, on his tablet's 0.2.698 as on a new app. That is why the B2 tellings hold nothing unsuitable for a
  child. If the lead wants a child's retellings to stop one level above their own, it is one line in `levelFor`
  (`up.coerceAtMost(1)`, or for a child only) and, for 0.2.698, the bridge leaving B2 out of a child's stories; not done
  here, since it changes every culture's stories and three other agents are writing beside this branch.

## The hint chip

Main's hint-chip-it landed while this branch was written, and its ItalianHintTest counts the turns whose chip says more
than a link: at least 95 %. With the new tellings 1,098 of 1,161 did. A turn with two right choices has no one word to
ask, so every word of them counts as the answer, and a rule that uses one of those words is hidden ("che", "più", "gli"):
right, but the chip then shows only its page. Nine turns lost the second right choice, the one that wasn't the form's
(the benandanti twice, the Pignarûl, St Martin, the shirt, the White Lady, the Lussari, the krivapete, the Orcolat), so
they test one word and the chip gives its rule, and what decides the form where the sentence shows it. 19 of the 127 turns
with a page still show the link alone. **New Italian turns with two right choices push this number down**: talks-it and
village-b2-friuli will meet it too.

## How

The tellings were written in a compact line format (one item a line: the teller's line, a right or wrong choice with
its why and reaction, a note's paragraph, in it, sl, en, de) and merged into the story files by a scratch script that
keeps the files' layout; a scratch lint stood in for the Android tests while writing (DialogReactionsTest's rules: a
reaction at most 80 characters, no sky cue, never the right choice or the words that set it apart, in any language;
StoriesTest's two right choices with their own replies; NotebookTest's prose without asides, half the words kept;
SceneCoherenceTest's looks; the notes' paragraphs and picture places; 200 characters a text; the grammar ids). Neither
script is part of the branch.

## Checks

- Bridge: `bunx tsc --noEmit -p .` clean; `bun test/check-culture.ts friuli`: ok after every story; `bun build
  --no-bundle test/smoke/stories.ts` ok; `bun test/learner-content.ts`: 0 texts naming Jan, 0 malformed placeholders,
  0 turns the same for one gender; `tutor-export --check`: 140 files match. The smoke (`LANI_SMOKE_PORT=10860`,
  `LANI_SMOKE_DRAMA3_PORT=10878`), on the branch with main merged at cb24615a0: all checks passed.
- Android: the story and reading classes (DialogReactionsTest, NotebookTest, StoriesTest, StoryBooksTest,
  SceneCoherenceTest, ReadingsTest, ReadingLogicTest, CuratedPicturesTest, VariantsContentTest, MentionsContentTest)
  with all sixteen stories' B1 and B2: 67 tests, 0 failures. The full suite on the same tip: 4,124 tests, 0 failures, 2
  skipped; its first run failed ItalianHintTest (see "The hint chip"), now 1,107 of 1,161 hints say more than the link
  and 623 of 934 form turns name what decides.
- Python: `-s tests` 253 OK (before main's second merge; no Python of this branch changed since but bin/qa's one line),
  `-s companion/tests` 179 OK, 11 skipped, on the final tip.
- QA through `qa-slot` on a synthetic Teo (it · de, a child, at A1, every story heard twice, so the next is a B1
  retelling): `story-faces` played Le agane at B1 to its end (Bepi's faces curious, happy, thinking; the third round's
  telling, "Vicino al torrente abitava una bambina povera, ma gentile con tutti"); `culture-reading` with
  `LANI_QA_READING=venzone-pietra-su-pietra`: the B2 row under "Più difficili · Schwerer", its text and German lines, its
  five questions answered right, "✓ 5 su 5 giuste · 5 von 5 richtig". Shots in the scratchpad's `ux-readings-friuli/`.
  Not played: a B2 telling, the other four readings, a tablet. The emulator caught "è considerato" against "la visita"
  in Venzone's last line, fixed (26b85a100).
  **Two traps for the next QA.** A fixture's bonds need 5 points or a memory: the village's migration to arrivals
  otherwise takes their `met` back, and the corner is empty ("Qui non c'è ancora niente da leggere"). And
  `culture-reading` looked for its row above the tutor bar's line; the corner has none and a short list's last row
  can't scroll higher (bin/qa, `bottom=2350`, 51697c034). In that fixture Maestra Elena's two readings didn't show: her
  home is the school, which that village hasn't built. That reason is read from the cast, not proved on the screen;
  ReadingsTest's new test holds both readings against the app's own check.

## What's left

- **The native check** (above), and the lead's choice on a child's retellings (see "Who hears them").
- **Voices**: nothing was spent. A voice build would voice the 32 tellings (the teller's lines, the right choices and
  every reply and reaction: about 69,000 characters of Italian) and the five readings read aloud (about 6,700, their
  questions, options and explanations about 5,100 more): some **81,000 characters**. Teo's node voices stories ahead
  with its engine, which may spend ElevenLabs credits: ask Jan before a voice build.
- New vignettes would suit some B2 details (the Giro's riders, Rilke, the excavation), but the tellings keep the
  stories' own pictures.

## Commits

aeaf115eb (readings), 962a8a2b9, 456d63aad, d01fe6622, 598143712, 5452e09e5, fc42b52b2, 871c5b810, a03500b37,
10432f3c3, f51c598bb, 988973b61 (the tellings), c30dfef9b, dddf57b27, 2ef7ad233 (the lexicon table, the supplement,
the counts), 8f6b37810 (the nine turns), 51697c034 (ReadingsTest, the QA step), 26b85a100 (Venzone's gender), the merges
of main 8fda88dd7 and cb24615a0 (it.extra.json remade by the tool), and this doc's. c30dfef9b's message says 318 table lines; the table has the numbers above.
