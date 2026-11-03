# German talks at B1 and B2 in Kärnten (talks-de)

Built 2026-11-03 on branch `talks-de` (followups-16.md, section 1, German), from `talks-sl`'s tip (7b009be99), whose
offer by level it uses as it is (`talkLevelOffered`, `ScenarioStore.offered`: docs/plans/05-built/talks-sl.md). The
free talks ("🗣️ Gespräch · Talk": role-plays where the tutor plays someone in a real situation) of the Carinthian village
were eight, five at A1 and three at A2 (`companion/bin/content-count`: "8 (A1 5 · A2 3)"). Now there are six more at B1
and six at B2, for a grown-up, in Austrian German as Kärnten speaks it, with every text the learner reads in Slovene,
English and Italian, the village's packs, and the tutor's brief ("20 (A1 5 · A2 3 · B1 6 · B2 6)"). An A1 learner is
offered none of them. Everything written here is machine-written and needs a native check (the list comes first).

## Native check

Machine-written, every new file flagged in its `review` ("de, sl, en, it: machine-written, not reviewed by a native
speaker"), the supplement's entries too (its `meta`). The Austrian words were settled on Duden, one request each, with
the URL; what a dictionary doesn't settle is listed for a native eye.

**Settled (the dictionary or an Austrian source has it as the talk uses it):**

- *der Kassabon*, "österreichisch" for Kassenbon: https://www.duden.de/rechtschreibung/Kassabon
- *zusperren*, "süddeutsch, österreichisch", "zu-, ab-, verschließen" and → *schließen* (7):
  https://www.duden.de/rechtschreibung/zusperren. The mayor's line uses it for a shop closing for good ("der
  Nahversorger, der zu Silvester zusperrt"): to confirm that sense (Duden's *schließen* (7) wasn't read).
- *das Pickerl*, "österreichisch umgangssprachlich", "Aufkleber; Plakette": https://www.duden.de/rechtschreibung/Pickerl;
  the car's §57a inspection sticker (WKO: https://www.wko.at/paragraph-57a/haeufige-fragen-und-antworten; ÖAMTC "Das
  trügerische Pickerl": https://www.oeamtc.at/mitgliedschaft/leistungen/rechtsberatung/das-truegerische-pickerl-eine-kauf-ueberpruefung-gibt-sicherheit-35327250).
  "Bis wann hat er noch Pickerl?" (no article) is how the seller and the buyer say it: to confirm.
- *der Typenschein*: not in Duden (https://www.duden.de/rechtschreibung/Typenschein is a 404); Austria's car paper, which
  the ÖAMTC's contract of sale lists at the handover with the Serviceheft
  (https://www.oeamtc.at/%C3%96AMTC+Kaufvertrag.pdf/17.930.500) and finanz.at calls compulsory for every car
  (https://www.finanz.at/kfz/fahrzeugpapiere/). Cars approved since 2007 mostly have none (the Genehmigungsdatenbank);
  the talk's Škoda is from 2015, so the seller may well not have one: a native eye may prefer "die Papiere".
- *die Lade* = Schublade, "österreichisch, sonst landschaftlich": https://www.duden.de/rechtschreibung/Lade
- *das Achterl*, "österreichisch", "Achtelliter [Wein]": https://www.duden.de/rechtschreibung/Achterl
- *der Nahversorger*, "österreichisch", "[Lebensmittel]geschäft in der Umgebung":
  https://www.duden.de/rechtschreibung/Nahversorger. The talk first said *Greißler*, which Duden marks "besonders
  ostösterreichisch" (https://www.duden.de/rechtschreibung/Greiszler): replaced, as Kärnten isn't the east.
- *der Kollektivvertrag*, "besonders österreichisch": https://www.duden.de/rechtschreibung/Kollektivvertrag
- *im Krankenstand sein*, "österreichisch: wegen Krankheit nicht zur Arbeit kommen":
  https://www.duden.de/rechtschreibung/Krankenstand
- *rinnen*, "ist geronnen", and its sense "undicht sein" (not Austrian only): https://www.duden.de/rechtschreibung/rinnen
- *die Früh*, "süddeutsch, österreichisch", "in der Früh": https://www.duden.de/rechtschreibung/Frueh
- *die Jause*, "österreichisch": https://www.duden.de/rechtschreibung/Jause
- *der Gemeindevorstand* is one of a Carinthian municipality's organs beside the Gemeinderat and the Bürgermeister
  (K-AGO; younion's course notes for Carinthia's municipal staff:
  https://www.younion.at/content/dam/younion/downloads/bundesländer/kärnten/service-skripten-zum-einführungslehrgang/Die%20Gemeinde%20-ein%20Überblick%20EFL%20April%202024.pdf).
- The Alpine police take down ski accidents, hit-and-runs on the Gerlitzen among them (salzburg24, 2016:
  https://www.salzburg24.at/news/welt/schwerverletzte-bei-skiunfall-mit-fahrerflucht-in-kaernten-art-113970; a
  parliamentary answer on hit-and-runs on the slopes: https://www.parlament.gv.at/dokument/XXV/AB/11246/imfname_626618.pdf).
- *Osojščica* is the Gerlitzen's Slovene name (Austria-Forum: https://austria-forum.org/af/AustriaWiki/Gerlitzen); the
  Slovene setting says "na Osojščici (Gerlitzen)". *Beljak*, *Dobrač*, *Baško jezero* and Italian *Villaco* as the
  village's other content has them.
- The earlier talks' doc settled *Ordination* (docs/plans/05-built/scenarios-de-en.md).

**What most needs a native eye (German first):**

- Register and naturalness of the twelve openers: "Grüß Gott! Sie haben die Jacke wieder mitgebracht. Stimmt etwas nicht
  damit?"; "Servus! Ich hab gehört, du feierst am Samstag. Brauchst du was zum Trinken?"; "Ordination Dr. Wieser,
  Sadjak, grüß Gott! Was kann ich für Sie tun?"; "Grüß Gott, Ogris von der Feuerwehr. Na, das war eine Nacht, was? Wie
  schaut's bei Ihnen aus?"; "Ah, du bist's! Setz dich. Was schaust du so? Das Hörgerät? Das liegt in der Lade. Das pfeift
  mir nur in den Ohren!"; "Grüß dich! Sofort, sofort … So. Du kommst wegen dem Zettel an der Tür, gell? Wir suchen
  jemanden für den Sommer."; "Grüß Gott, kommen Sie herein, nehmen Sie Platz. Ich hab eine Viertelstunde, dann ist
  Gemeindevorstand. Also, wo drückt der Schuh?"; "… Ihre Bewerbung habe ich schon gelesen. Erzählen Sie mir zuerst: Warum
  gerade wir?"; "Weil Sie das erste Mal bei mir sind, möchte ich zuerst ein bisschen über Ihre Gesundheit erfahren. Darf
  ich?"; "Sie kommen wegen dem Škoda, oder? Da steht er. Gepflegt, Nichtraucherauto, immer in der Garage gestanden.";
  "Grüß Gott, Zankl, Alpinpolizei. Die Pistenrettung hat mich verständigt. Wie geht's dem Knie?"; "He, servus! Hast du
  kurz Zeit? Ich muss mich bis Freitag entscheiden …".
- Phrases: "Da kann ich leider nicht, da bin ich in der Arbeit.", "Ginge es vielleicht am Donnerstag am Nachmittag?",
  "Ich brauche auch eine Bestätigung für den Krankenstand." (or *Krankenstandsbestätigung*), "Das Wasser ist in den
  Keller geronnen.", "Wir haben bis drei in der Früh Wasser geschöpft.", "Du könntest noch einmal zum Akustiker gehen.",
  "Ich habe schon zwei Sommer in einem Hotel gekellnert.", "Wann könnte ich zum Probearbeiten kommen?", "Soviel ich weiß,
  fördert das Land solche Projekte.", "Wie hoch ist das Gehalt laut Kollektivvertrag?", "Gibt es ein Personalzimmer?",
  "Mein Vater hat Zucker." (spoken for diabetes), "Ich rauche nicht, aber ein Achterl Wein trinke ich schon.", "Die
  Zeckenimpfung sollte ich wieder auffrischen.", "Die Kupplung greift ziemlich spät.", "Wegen der Delle und der Reifen
  müsste der Preis runter.", "Er ist mir von hinten hineingefahren." (spoken), "Er hat gesagt, er habe mich nicht
  gesehen.", "Du könntest mit einer kleinen Jause für Wanderer anfangen.", "Egal, wie du dich entscheidest, ich halte zu
  dir.".
- **What people say, kept on purpose:** *wegen dem* (Kathi's opener, the mayor's phrase, the seller's opener), the
  Perfekt for every past (Präteritum only for *war*, *hatte* and the modal), *gestanden* with *sein* ("immer in der
  Garage gestanden"), *Ich hab*, *was* for *etwas*, *gell*, *Passt*: a native eye to confirm none sounds wrong in Kärnten.
- Whether these ring true for Kärnten: the volunteer fire brigade's *Kommandant* going round after a hailstorm and the
  Gemeinde taking the damage reports (the Katastrophenfonds); the village's last shop closing and a Verein's Dorfladen
  in the old school, with the Land's support ("Soviel ich weiß": the talk doesn't claim a programme); summer help at a
  Gasthaus paid by the hour with the tips shared; a family hotel on the Faaker See hiring reception for the season with
  a Personalzimmer; a hit-and-run on the Gerlitzen told to the Alpine police at the valley station.
- **Slovene as a base.** "{m:Bil premočen|f:Bila premočena} do kože", "Jakna ne drži vode.", "Tri tedne je predolgo",
  "okrogel rojstni dan", "Pri sadjarju Stefanu", "Bi imeli morda kaj prej?", "potrdilo o bolniški", "Na slišanje!",
  "Okoli enajstih ponoči je začela padati toča.", "Do treh zjutraj smo zajemali vodo.", "Lahko izčrpate vodo iz kleti?",
  "Lahko bi še enkrat šla tja, kjer si ga dobila.", "Že dve poletji sem {m:stregel|f:stregla} v hotelu.", "kje vas čevelj
  žuli?", "Kolikor vem, dežela podpira takšne projekte.", "Moja močna stran je …", "seneni nahod", "Dvakrat na teden grem
  tečt.", "Moram na odvzem krvi priti tešč?", "Sklopka prime precej pozno.", "Se srečava na pol poti?", "Od zadaj se je
  zaletel vame.", "Na tvojem mestu se ne bi {m:prenaglil|f:prenaglila}.", "Hkrati na Dobraču oddajajo v zakup planinsko
  kočo".
- **Italian as a base.** "ti sei {m:bagnato|f:bagnata} fino alle ossa", "un compleanno a cifra tonda", "Andrebbe bene
  giovedì pomeriggio, magari?", "il certificato di malattia", "è saltata la corrente", "l'audioprotesista", "la giunta",
  "Per quanto ne so, il Land finanzia progetti del genere.", "un posto stagionale", "La frizione attacca molto alta.",
  "Ci veniamo incontro a metà strada?", "hai preso una storta al ginocchio", "il soccorso piste", "una baita (una
  Almhütte)".
- **English.** British where it matters: "the surgery", "a temperature", "tyres", "an estate", "the ski patrol", "a
  sick note".

## What

Twelve role-plays in `companion/scenarios/de/` (`lani.scenario/v0`), each for a grown-up (`audience: "adult"`), its
title, goals and who in de, sl, en and it, its setting and the meanings of its opener and phrases in sl, en and it (the
learner's gender as `{m:|f:}` where Slovene or Italian needs it), the village's packs, 8–11 phrases, 5 goals:

| Id | Level | Situation | Who | Packs |
|---|---|---|---|---|
| `die-reklamation` 🧥 Die Reklamation | B1 | a complaint: a rain jacket from a sports shop in Villach that let the rain through on the Dobratsch | Herr Kraßnig, the shop assistant; Sie | im-dorf, wetter-und-garten, zahlen-und-uhrzeit, gruesse |
| `most-fuer-das-fest` 🍏 Most für das Fest | B1 | a negotiation: fifty litres of Most and apple juice for a big birthday (a runder Geburtstag) | **Mostbauer Stefan** (the cast's); du | im-keller, auf-dem-markt, zahlen-und-uhrzeit, familie |
| `termin-in-der-ordination` 📞 Ein Termin in der Ordination | B1 | an appointment that goes wrong: Dr. Wieser on holiday, his stand-in, a time during work, the Krankenstand | Frau Sadjak, the Ordinationsassistentin, on the phone; Sie | beim-arzt, zahlen-und-uhrzeit, gruesse |
| `nach-dem-unwetter` ⛈️ Nach dem Unwetter | B1 | telling what happened: the hailstorm night, the stream over its banks, the cellar under water | Kommandant Ogris of the Freiwillige Feuerwehr; Sie | wetter-und-garten, haus-und-kueche, beim-feuerwehrhaus, am-bach |
| `tante-mitzi-und-das-hoergeraet` 👂 Tante Mitzi und das Hörgerät | B1 | giving advice: the hearing aid in the drawer | **Tante Mitzi** (the cast's); du | familie, in-der-stube, haus-und-kueche, gruesse |
| `aushilfe-im-gasthaus` 🍽️ Aushilfe im Gasthaus | B1 | a job: summer help at the Gasthaus zur Linde, the Kirchtag weekend, the pay, a trial day | **Wirtin Kathi** (the cast's); she says du, the learner Sie | im-gasthaus, essen, zahlen-und-uhrzeit, gruesse |
| `beim-buergermeister` 🏛️ Beim Bürgermeister | B2 | explaining a problem to the mayor: the village's Nahversorger closes at New Year; a Verein's Dorfladen in the old school | Bürgermeister Wutti at his office hours; Sie | auf-der-gemeinde, im-dorf, essen, zahlen-und-uhrzeit |
| `das-vorstellungsgespraech` 💼 Das Vorstellungsgespräch | B2 | a job interview: reception at a family hotel on the Faaker See for the season | Frau Kowatsch, the hotel's manager; Sie | am-see, im-gasthaus, zahlen-und-uhrzeit, gruesse |
| `bei-der-neuen-hausaerztin` 📋 Bei der neuen Hausärztin | B2 | a doctor's history: illnesses, an operation, allergies, medicines, the family, habits, the tick jab | Dr. Egger, the new Hausärztin; Sie | beim-arzt, familie, zahlen-und-uhrzeit, essen |
| `ein-gebrauchtwagen` 🚗 Ein Gebrauchtwagen | B2 | a negotiation: a 2015 Škoda Octavia estate, its Pickerl, worn tyres, a dent, a late clutch | Herr Gasser, a private seller in Villach; Sie | zahlen-und-uhrzeit, in-der-werkstatt, im-dorf |
| `ein-unfall-auf-der-piste` ⛷️ Ein Unfall auf der Piste | B2 | telling what happened: a snowboarder crashed into the learner on the Gerlitzen and rode on | Inspektor Zankl of the Alpinpolizei; Sie | beim-arzt, am-see, wetter-und-garten, zahlen-und-uhrzeit |
| `florian-muss-sich-entscheiden` 🧭 Florian muss sich entscheiden | B2 | giving advice: a safe job in Villach or an Almhütte of his own on the Dobratsch, with a loan | **Hirte Florian** (the cast's); du | am-see, auf-dem-feld, familie, im-dorf |

All eight situations of the brief are there: a complaint, two negotiations, an appointment that goes wrong, the mayor, a
job at each level, a doctor's history, advice at each level, what happened at each level.

**What a B1 or B2 talk does that an A1 or A2 one doesn't.** A2: a call to the council to ask when it's open, the
neighbour at the fence. B1: something happened, told in the Perfekt ("Beim ersten Regen bin ich ganz nass geworden",
"Dann ist der Bach über die Ufer getreten"), a problem to explain and a wish to argue for with the Konjunktiv II ("Ich
hätte lieber eine neue Jacke", "Ginge es vielleicht am Donnerstag am Nachmittag?", "Wie viele Stunden wären das?"),
advice ("An deiner Stelle würde ich …", "Du könntest …", "Warum probierst du es nicht …?"), the character pushing back
once. B2: an argument with its reasons and concessions ("Ich verstehe, dass das Budget knapp ist, aber …"), a sequence
told precisely with reported speech ("Er hat gesagt, er habe mich nicht gesehen"), the passive ("bin ich am Blinddarm
operiert worden"), hypotheses ("Was wäre, wenn du im Winter in Villach arbeiten würdest?"), weighing up ("Einerseits …,
andererseits …"), a formal register kept (the mayor, the interviewer, the doctor, the officer), a negotiation that moves
in steps and ends in a summary.

**What people say in Kärnten is said here.** The rule of followups-12.md's last section protects it in grading; in a
talk there is nothing graded, so the characters and the phrases simply say it: *wegen dem* (Kathi, the mayor's phrase,
the seller), the Perfekt for the past, *in der Arbeit*, *in der Früh*, *Lade*, *Achterl*, *Jause*, *gell*, *Passt*,
*Wie schaut's aus?*, *Mein Vater hat Zucker*. The tutor's brief says the level and the role, as for every talk; the
tutor's German part (`companion/tutor/languages/de.md`, "Spoken, never counted") already tells the tutor that the
Perfekt for the past, *wegen dem* and *ich hab* are never counted, so a learner who answers that way in a talk isn't
corrected for it.

**The cast plays its own.** Four talks name a villager of the cast, who plays them (their sprite and voice, and the
friendship grows once met): Mostbauer Stefan, Tante Mitzi, Wirtin Kathi (who says du and is said Sie to, as her file
has it), Hirte Florian. Each keeps to the villager's file: Stefan's Most from the old meadow orchard below the
Dobratsch, never offered to a child; Mitzi a bit hard of hearing ("Wie bitte?"); Kathi's three plates on one arm and
the Kirchtag; Florian's dream of an Almhütte of his own. The others stand in by their title (Herr Kraßnig, Frau Sadjak,
Frau Kowatsch, Herr Gasser, Dr. Egger: the app's German titles; "Dr." takes the voice's gender, so Dr. Egger is drawn
as a woman) or by their name and voice (Kommandant Ogris, Bürgermeister Wutti, Inspektor Zankl: a man's sprite from
`voice: male`). No surname is a villager's id, so none is taken for one. No Kotlin change was needed.

## How

**Offered by level:** talks-sl's `talkLevelOffered` and `ScenarioStore.offered(child, level)`, unchanged: an A1 learner
of German is offered the five A1 and three A2 talks, an A2 learner the B1 ones too, a B1 learner all twenty. The filter
is the bridge's (`GET /scenarios`, `GET /scenarios/:id` 404s above, a scene's "keep talking"), so an app of any version
gets it at once. talks-it built the same as `talkFits`; the lead merges both onto talks-sl's.

**Older apps.** No format change: the twelve are `lani.scenario/v0` as the eight German ones are, served as strings in
the pair. Teo's tablet (0.2.698) is in an Italian village, which is served only Italian talks; a child is never offered
a grown-up's.

**The supplement.** German has no Wiktionary dictionary in the repository: a tap reads `companion/lexicon/de.extra.json`
and the packs. 160 words of the twelve (goals, openers, phrases) were in neither; now 133 entries more and 21 forms
added to entries it had (anfangen: *angefangen*; zusperren: *zusperrt*, with its sense of a shop closing; sein:
*bist's*; schauen: *schaut's* …), each meant in Slovene and Italian. Every German word of the twenty talks is known now
(the script in the scratchpad that checks it: the coverage tokenizer over `opener_sl` and the phrases, which
`lexicon-build --coverage` doesn't count, as their key is `sl`).

**The dev bridge** answers a German B1 or B2 talk in lines that fit any of the twelve (`ROLEPLAY_ABOVE_A2.de`:
"Verstehe. Und wie ist es dann weitergegangen?", "Interessant. Und warum eigentlich?", "Gut, das klingt vernünftig.",
"Passt, danke. So machen wir es.", a correction with the Konjunktiv II "Könnten Sie das bitte wiederholen?"), picked by
the brief's level; an A1 or A2 talk keeps its Semmeln.

**make-learner** (`bridge/test/fixtures/make-learner.ts`) stopped on `--target de --level B1`: it read
`companion/modules/de`, which doesn't exist on main. A language without curated modules now has none played (commit
257c9c497, for talks-en and any QA of a German or English learner above A1).

## How to use

- A learner of German: Home's "🗣️ Gespräch · Pogovor"; the list shows the B1 talks once their level is A2, all of them
  at B1; a talk, Start.
- The tutor: `publish_scenario` as before; a talk the tutor writes is offered at any level.
- QA: `LANI_DEV_DATA=$(cd companion/bridge && bun test/fixtures/make-learner.ts <dir> --target de --base sl --name Ana
  --level B1)`; `LANI_CULTURE=kaernten LANI_QA_SETUP_PAIR=de-sl LANI_QA_LEARNER=Ana LANI_QA_TALK=nach-dem-unwetter
  LANI_QA_TALK_SHOTS=<dir> companion/bin/qa-slot --steps kaernten-talk` (another talk: `LANI_QA_TALK=<id>`; a B2 one has
  its typed lines too). Without `LANI_QA_TALK` the step plays "Beim Bäcker" as before.

## Tap counts

| Journey | Before | After |
|---|---|---|
| A learner of German at B1, Home to talking after the storm | not possible (no German talk above A2) | 3: "🗣️ Gespräch · Pogovor", "Nach dem Unwetter · Po neurju", "Starten · Začni" |
| A learner of German, Home to an A1 talk | 3 | 3 (unchanged) |

## Checks

- **Verified first:** the item wasn't done: `content-count` (talks-it's row, cherry-picked as 22afbf997 from 97fa05fd7)
  said "de: 8 (A1 5 · A2 3)"; now "20 (A1 5 · A2 3 · B1 6 · B2 6)".
- Bridge: `bunx tsc --noEmit -p .`; `bun build --no-bundle` on each changed test file; the smoke with ports 10800–
  (LANI_SMOKE_DRAMA3_PORT 10818), after the merge with main: all checks passed (3,780). Its scenarios section: German's
  talks by level (A1 5, A2 3, B1 6, B2 6), each validating with its texts in de · sl · en · it; kaernten
  (`village-talks.ts`): an A1 learner is served the eight A1 and A2 talks and none above, `/scenarios/beim-buergermeister`
  404s; at B1 all twenty, each in de · sl; the mayor's "Beim Bürgermeister · Pri županu" with its first goal, who,
  setting and a phrase in the pair; Stefan, Mitzi, Kathi and Florian playing their talks above A2 by their names; the
  B2 turn's brief with its level, packs and language. Lakeland's eight unchanged (8 of 8 at A1).
- `test/check-culture.ts kaernten`: ok. `bun test/learner-content.ts`: no text names Jan, no malformed placeholder.
  `bun test/prompt-clashes.ts`: no new clash. `tutor-export --check`: 140 files match.
- `lexicon-build --coverage --lang de`: the supplement 86.8 % of tokens, 85.3 % of words (was 86.7 / 85.1); with the
  packs and names 99.9 / 99.1 (as before); meant in sl, it and en 98.7 / 98.7 (as before); the same 51 words none
  knows. Every German word of the twenty talks' goals, openers and phrases is known (0 of 160 left).
- Android: `TalkTest` (twenty German files; the twelve above A2 in de · sl for a grown-up with packs; the mayor's in
  the pair; Stefan, Mitzi, Kathi, Florian and Inspektor Zankl as the characters; the storm in de · en) and `CastTest`,
  through lani-slot.sh: passed. The full unit suite after the merge with main: 4,125 tests in 523 classes, 0 failures (2
  skipped).
- Python: `python3 -m unittest discover -s companion/tests` (179, 11 skipped) and `-s tests`: OK.
- QA through `qa-slot` (slot 2), `--steps kaernten-talk` on a synthetic Ana at B1 (de · sl, `make-learner.ts --level
  B1`), `LANI_QA_TALK=nach-dem-unwetter`: passed, 3 taps from Home to talking, no crash. Its shots (`st-*.png` in the
  scratchpad's `ux-talks-de/b1/`): the list with the B1 and B2 talks and their level badges among the eight (Herr Gasser,
  Inspektor Zankl, Hirte Florian, Mostbauer Stefan, Kommandant Ogris …), the intro (Kommandant Ogris drawn as a man,
  "Der Feuerwehrkommandant · Poveljnik gasilcev", the setting in Slovene, the goals in de · sl), the opener, the dev
  tutor's German lines above A2 ("Verstehe. Und wie ist es dann weitergegangen?", "Interessant. Und warum eigentlich?"),
  the hint that is the talk's first phrase ("Gegen elf in der Nacht hat es zu hageln angefangen."), the correction in
  Slovene with the Konjunktiv II, the goals, the debrief in Slovene. The first run failed: the step looked for the talk
  in 8 swipes, and in a list of twenty the fifteenth lay below the tutor bar; now up to 16 (bin/qa).

## What's left

- **The native check** above.
- **The B1/B2 packs.** `packs-de` (not on main yet) adds German B1 and B2 packs that fit these talks better than the A1
  and A2 ones they name now. Once it is merged, add to each talk's `packs` (at most six; the smoke checks each is the
  village's): `die-reklamation` kleidung, einkaufen; `most-fuer-das-fest` geld-und-bank, das-leben;
  `termin-in-der-ordination` der-koerper, arbeit-und-beruf; `nach-dem-unwetter` wetterbericht, reparaturen;
  `tante-mitzi-und-das-hoergeraet` gefuehle, alter-und-pflege; `aushilfe-im-gasthaus` arbeit-und-beruf,
  kochen-und-backen; `beim-buergermeister` gemeinde-und-wahl, vereine-im-dorf; `das-vorstellungsgespraech`
  vertrag-und-lohn, im-betrieb; `bei-der-neuen-hausaerztin` gesundheit-und-vorsorge, der-koerper; `ein-gebrauchtwagen`
  mit-dem-auto, geld-und-bank; `ein-unfall-auf-der-piste` notfaelle, versicherungen; `florian-muss-sich-entscheiden`
  landwirtschaft, in-den-bergen. Their words then come from the packs, and the supplement's entries for them stay as
  the tap's second reading (or go, if the lead prefers one source).
- No scene leads into the new talks yet ("keep talking" after a dialog): Stefan's Most on the market could lead into
  `most-fuer-das-fest`, Kathi's Gasthaus dialogs into `aushilfe-im-gasthaus` (at B1: talksOffered drops it below).
- A doctor's practice, an office, a hotel and a ski slope have no stage place of their own: the shop, the mayor's
  office, the storm's street and the car on the square; the phone call, the Gasthaus and the interview in the kitchen;
  the doctor, Mitzi at the character's home; Stefan and Florian in the field; the slope in the forest.
- `lexicon-build --coverage` doesn't count a talk's opener or phrases in a language other than Slovene (their key is
  `sl` in every language, scenarios.ts): a German or Italian talk's words never show in the coverage. Counting
  `opener_sl` and a phrase's `sl` under a non-Slovene scenario would close it; not done here (the tool is shared).

## Voice

The twelve openers and their phrases join the German voice corpus (priority "scenarios"): 128 lines, about 6,300
characters (no gender pair in a German line). A node of the Carinthian village voices them ahead with its culture's
ElevenLabs voices (`companion/cultures/kaernten/voice-cast.json`: the cast's for Stefan, Mitzi, Kathi and Florian, the
others by their `speaker` or the male narrator), which spends credits: ask Jan before a voice build. The local Gepard
voices read them at no cost; until then the phone's German text-to-speech does. Nothing was spent here.

## Commits

- 22afbf997 feat(bin): content-count counts the role-plays by level (talks-it's 97fa05fd7, cherry-picked)
- 747862781 feat(talks-de): twelve German talks at B1 and B2 in Kärnten
- 71b9a9600 feat(lexicon): the German supplement means every word of the twelve talks above A2
- fcf33f4d0 fix(talks-de): the village shop is the Nahversorger, not the Greißler
- 512ef6a98 test(talks-de): the smoke, TalkTest, the dev tutor and QA for the German talks above A2
- 257c9c497 fix(test): make-learner makes a learner above A1 in a language without curated modules (talks-en may want it)
- e4035a641 docs(talks-de): README and module-spec on the German talks above A2; QA scrolls the twenty
- 592f65330 Merge branch 'main' into talks-de (bin/qa's step list by qa-steps-union.py)
- this doc
