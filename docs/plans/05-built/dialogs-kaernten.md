# Kärnten's village at B1 and B2 (dialogs-kaernten)

Wave E, batch 2a (followups-15.md, 2026-11-03): Kärnten's scenes get B1 and B2 variants by the writers' guide of
[dialog-levels.md](dialog-levels.md) ("Writers' guide", the model: auf-dem-markt). Every scene but the market, which the
model wrote: 32 scenes, 126 variants (63 B1, 63 B2), one B1 and one B2 on each of two grown-ups' happenings (am-feuer
has one: Resi's tea; the other is the storyteller's). The happenings' `dialog` and their old variants are as they were;
the new ones come first in `dialogs` (B2, then B1). Base: dialog-levels' system commit 6e364137; the model content
(5fd43a08) is not merged here.

## Native check

Everything new is machine-written (Claude) and not reviewed by a native speaker: every line in German, Slovene, English
and Italian, the whys, the memories, and the supplement's new entries. Each scene's `review` says so ("B1, B2 variants").
For a native speaker of Austrian German, first:

- **The wrong choices must be a learner's mistakes nobody grown up says.** They are, by page: the indicative or würde with
  the participle where the past Konjunktiv goes (Ich würde es genauso gemacht, Ich hätte … gewesen, Würde ich früher
  angefangen), a modal's participle in the Perfekt (gehen gesollt, kaufen gekonnt), hatte/mussten/konnten/wurde without
  the umlaut where the Konjunktiv II goes, a relative pronoun's wrong case (den immer bleibt, durch denen, mit die, aus das,
  denen du geschnitten hast) and a melted one (aufm der Fuchs, im das Wasser), the genitive's missing -s (Ihres
  Urgroßvater, des Gasthaus, des Mehl, eines Fuchs) and the wrong article (des Harpfe, des Braut), wegen/trotz/statt/
  während/außerhalb/innerhalb with the nominative or accusative (wegen der Regen, trotz die Hitze, außerhalb den Zaun),
  sowohl … und auch, weder … oder, entweder … noch, nicht nur … aber auch, obwohl … aber, Trotzdem du solltest, the
  passive with the infinitive (wird … verkaufen, müssen übersetzt worden, gebaut geworden), werdet for wird, wird bellt,
  ich werde da, the n-declension's missing -n on nouns in -e (den Kunde, dem Hirte, den Kollege, dem Neffe, dem Experte,
  bei den Slowene, des Names), adjective endings (einen kleiner Hecht, mit dem schwerem Hammer, die große weißen Kerze,
  trockenen Holz), adjectives as nouns (der Bekannter, der Fremder, das Schönsten, etwas Netten, die Kleine for the
  little ones, Deutscherin), prepositions after adjectives (stolz von, gespannt für, interessiert für, neugierig über,
  zufrieden von, begeistert für, fertig von, auf … abhängig), nomen-verb (einen Antrag machen, Abschied machen, eine
  Entscheidung machen, einen Beschluss machen, einen Vorschlag stellen, Rücksicht geben, einen Bescheid sagen, Platz
  machen for Platz nehmen), word order (nicht den Wurm, nicht es, mir sie, als ob er wäre noch, dass schläft die
  Sonne, sodass du hörst …, oder hilft er), wenn for als and als for wenn, vor for bevor, um … zu after es ist wichtig.
- **What people say is a second right answer, never wrong**, with its own reply: von Ihrem Urgroßvater, wegen dem
  Regen/Schnee, auf was wartet er, die Schüsseln vom Gasthaus, außerhalb vom Zaun, die Augen von einem Fuchs, das Haus
  vom Schmied, statt dem Zucker, trotz dem Lärm, innerhalb von einer Woche, die Handschrift von deiner Mutter, das Kleid
  von der Braut, am Rand vom Wald, der größte See von Kärnten, wie du auf die Welt gekommen bist, er kommt beside er
  komme, er spielt beside er spiele, irgendwer and jemand, froh um beside froh über, auf den Stock vergessen beside den
  Stock vergessen, als wäre/als ob … wäre. Eva's B2 turn has "ich bin gesessen" right in Austria (the wrong choice is the
  claim that it's a mistake). Check that none of the wrong choices above is said in Carinthia after all.
- **Austrian and Carinthian words and idioms**, each to check it's said so: heuer, heroben, narrisch, der Gaul, das
  Radl, das Leiberl, die Leintücher, der Holler, ein bisserl, ein Zweigerl, ein Stückerl, ein Wörterl, das Glockerl, die
  Gelsen, die Eierschwammerl, die Schularbeit, der Verlängerte, das Kracherl, die Stiege(n), der Kasten, das Krügerl,
  die Burschen, die Scheiter, zusperren, ausmalen, angreifen (touch), rinnen, einen Schmäh führen, da bleibt kein Auge
  trocken, da bleibt ihm die Spucke weg, das geht sich (nicht) aus, Mir graust, Ich kenn mich nicht aus, Hat's leicht
  schon wer gesehen, Frau Magister, das Rufzeichen, die Bezirkshauptmannschaft, "Sehr geehrter Herr Bürgermeister!"
  with an exclamation mark, Most (fermented) against Germany's fresh juice.
- **Sayings made up for the speakers** (do they sound like proverbs or like nonsense?): Wer den Kleinen schont, fängt
  später den Großen; Neues ist gut, Altes ist warm; Wer warten kann, findet mehr als Musik; Die Uhr macht pünktlich,
  das Herz macht froh; Viele Eier machen eine Uhr; Wer's eilig hat, hat am Ende Scherben; Ein gutes Foto lügt nicht, es
  verschweigt nur den Vorhang; the real ones: Wer misst, misst Mist; Ein Schwarm im Mai ist wert ein Fuder Heu;
  Morgenrot bringt Regen; Ehrlich währt am längsten; Der frühe Vogel (Sepp mixes it up on purpose).
- **Facts**: in Kärnten only the Carnica may be kept (the Carinthian beekeeping law); Anton Janša, born 20 May 1734 in
  Upper Carniola, Maria Theresa's first teacher of beekeeping in Vienna (World Bee Day); the Drau rises in South Tyrol and
  flows through Carinthia and Slovenia to Croatia ("one river, four countries"); 26 October, the national holiday; the
  Wörthersee Carinthia's largest lake; frost protection by sprinkling the blossoms; church bells melted down for cannons
  in the war; 1348, the Dobratsch (as Kärnten's story of it has it); Kärntner Kasnudeln on the list of traditional foods
  (as the model settled it: BMLUK). Made up for the village: the Franzlmühle (the old mill's name), Franz's nephew from
  Graz, the Ferdl from Vienna, the Gustl from Arnoldstein, Hans from the next village, Mitzi's Hansi who went to America.
- **The Slovene, English and Italian translations** (the bases): the gender pairs of the learner's forms; Slovene names
  kept as they are (Maria Luggau, Maschanzker), plavice, žolčasti goban, Lovrenčeve solze, kranjska sivka, ajdovi
  žganci, mernik, končnice; the vikanje for Kathi, Franz, Toni and Eva.

Each variant's own points:

- `am-bach` waesche-b2: "Hat's leicht schon wer gesehen?" (leicht = vielleicht, Austrian spoken); "seither"; "Auswringen"
- `am-bach` waesche-b1: "das Leiberl" (T-shirt), "die Leintücher" (Austrian for Bettlaken), "Vergelt's Gott"
- `am-bach` schafe-b2: "im oberen Gailtal soll ein Wolf gesehen worden sein"; "der Pferch"
- `am-bach` schafe-b1: "das Gatter offen gelassen"; "ein Wörterl"
- `am-dachboden` wiege-b2: "die Gaststube ausmalen lassen" (ausmalen = streichen, Austrian); "bevor ich's mir anders überleg"
- `am-dachboden` wiege-b1: "Die Buchstaben von Ihrem Urgroßvater" as a second right answer (spoken) beside the genitive
- `am-dachboden` kletzen-b2: "ist angeknabbert worden"; "kriegt"; "Den erwisch ich selber"
- `am-dachboden` kletzen-b1: "der Laib, zwanzig Laibe"; "schimmlig"; "Dein Wort in Gottes Ohr"
- `am-dorfbrunnen` neuigkeiten-b2: "ein Filmteam … wolle bei uns drehen", "er habe davon noch nie etwas gehört" (Konjunktiv I); "Ich wär doch …"
- `am-dorfbrunnen` neuigkeiten-b1: "Mein Radl klappert" (das Radl, Austrian for Fahrrad); "eh"
- `am-dorfbrunnen` wasser-b2: "Das Wasser sei regelmäßig zu prüfen"; "einen Antrag stellen / machen" (the joke); "Sicher ist sicher"
- `am-dorfbrunnen` wasser-b1: "das Wasser in Kübeln heraufgetragen"; "Das Einzige, was"
- `am-feuer` tee-b2: "Hätte ich … geredet, könnte die Lena es jetzt schon von daheim" (mixed time); "nuscheln"; the Slovene saying quoted
- `am-feuer` tee-b1: "der Holler, der Hollertee" (Austrian for Holunder); "ein bisserl", "ein Zweigerl"
- `am-see` alm-b2: "prämiert worden"; "Der von einem jungen Hirten … hergestellte Käse überzeugte die Jury"; "aus mir würde nie was Gescheites"
- `am-see` alm-b1: "herrichten"; "Lärchenschindeln"
- `am-see` morgenrot-b2: "da heroben" (Austrian for hier oben); "Wär ich doch … geboren!"; "Abschied nehmen"
- `am-see` morgenrot-b1: "Morgenrot bringt Regen" (the weather saying); "verschlafen"
- `am-spielplatz` tempelhuepfen-b2: "die Klassenbesprechung"; "mithalten"
- `am-spielplatz` tempelhuepfen-b1: "Wegen dem Regen" as a second right answer (spoken, Eva names the written form); "eine Lehrkraft"
- `am-spielplatz` seifenblasen-b2: "Es soll von einem Physiklehrer stammen"; "das Spülmittel"; "anfachen"
- `am-spielplatz` seifenblasen-b1: "eine Seifenblasen-Ecke"; "Wo kriegen wir die her?"; "Die Schüsseln vom Gasthaus" as a second right answer beside the genitive
- `am-teich` reiher-b2: "ist … fast ausgerottet worden"; "und zwar langsam"
- `am-teich` reiher-b1: "Auf was wartet er?" as a second right answer (spoken) beside "Worauf"; "Die Früh gehört den Reihern"
- `am-teich` angeln-b2: "Heut geht sich das nicht aus" (Austrian: it won't work out); "Der frühe Fisch … nein, der frühe Vogel" (Sepp mixing up the saying on purpose)
- `am-teich` angeln-b1: "Wer den Kleinen schont, fängt später den Großen" (made up for Sepp: check it doesn't sound wrong)
- `am-tor` riegel-b2: "der Beschlag"; "Wär der Beschlag … fertig gewesen"; "gescheiter"
- `am-tor` riegel-b1: "Freilich"; "Zu ist zu"
- `am-tor` herde-b2: "Mir graust schon jetzt"; "Ehrlich währt am längsten"; "einen Bescheid" (the joke)
- `am-tor` herde-b1: "Der Riegel klemmt"; "Ich wünschte, ich hätte …"
- `an-der-bruecke` wagen-b2: "die Bezirkshauptmannschaft"; "nicht zu befahren"; "die Sondergenehmigung"
- `an-der-bruecke` wagen-b1: Franz's notebook in the Präteritum ("fuhren wir … hinüber"), as written German is
- `an-der-bruecke` hochwasser-b2: "Oben … dürfte es die ganze Nacht geschüttet haben"; "Zuerst das Was, dann das Wem"
- `an-der-bruecke` hochwasser-b1: "Während der Nacht"; "eingeschlagen" (the pillars)
- `auf-dem-aussichtsturm` aussicht-b2: "dass … einmal drei Sprachen gesprochen werden"; "gespannt auf"
- `auf-dem-aussichtsturm` aussicht-b1: "das Dreiländereck"; "der Fremdenführer"
- `auf-dem-aussichtsturm` stufen-b2: "Die herabgestürzten Felsen"; "die Schütt"; 1348 as the story of the Dobratsch has it
- `auf-dem-aussichtsturm` stufen-b1: "bin ich an einem Tag auf den Großglockner und wieder herunter" (spoken, the verb left out); "die Stiege"
- `auf-dem-dorfplatz` gasthaus-b2: "bei uns sei das Essen kalt gewesen" (Konjunktiv I, the review reported); "Da bleibt ihm die Spucke weg"
- `auf-dem-dorfplatz` gasthaus-b1: "der Verlängerte" (Austrian coffee); "das Kracherl"; "Ich kenn mich nicht aus"
- `auf-dem-dorfplatz` bank-b2: "Hätte mich der Doktor nicht aufgehalten, stünde ich …" (stünde, Konjunktiv II of stehen); "auf etwas vergessen" (Austrian) beside "etwas vergessen", both right
- `auf-dem-dorfplatz` bank-b1: the learner tells a tale in the Präteritum (hieß, schlief), as tales are told
- `auf-dem-feld` ernte-b2: "hätte … geerntet werden müssen"
- `auf-dem-feld` ernte-b1: "Trotz der Hitze"; "das Kufenstechen" (the Gailtal riders' festival)
- `auf-dem-feld` pferd-b2: "als ob er der jüngste Noriker im Gailtal wäre"; "das graue Maul"
- `auf-dem-feld` pferd-b1: "Außerhalb vom Zaun" as a second right answer beside "außerhalb des Zauns"
- `auf-dem-wachturm` wache-b2: "ein Stückerl"; the dog "aus den Bergen in Slowenien" (a Karst shepherd: leave unnamed)
- `auf-dem-wachturm` wache-b1: "die Augen von einem Fuchs" as a second right answer beside the genitive; "der Gauner"
- `auf-dem-wachturm` aussicht-b2: "er komme … / er kommt …" both right (reported speech, written and spoken); "Höflichkeit kostet nix"
- `auf-dem-wachturm` aussicht-b1: "das Haus vom Schmied" as a second right answer beside the genitive
- `auf-der-bienenwiese` blumen-b2: "Das Betreten der frisch eingesäten Blumenwiese ist verboten"; "plavice" (Slovene for cornflowers)
- `auf-der-bienenwiese` blumen-b1: Anton Janša (born 20 May 1734, Breznica; Maria Theresa's beekeeping teacher in Vienna): check the facts and "Oberkrain"
- `auf-der-bienenwiese` tee-b2: "der Stanko meint, ich sei zu ungeduldig"; "zum Imkern"
- `auf-der-bienenwiese` tee-b1: "statt dem Zucker" as a second right answer beside "statt des Zuckers"
- `auf-der-stockbahn` laube-b2: "einen Schmäh führen", "da bleibt kein Auge trocken" (Austrian); "der Ferdl"
- `auf-der-stockbahn` laube-b1: "Brda" / "Goriška Brda" (Marko) and "the Collio" (Matteo) as VILLAGERS.md has them
- `auf-der-stockbahn` spiel-b2: "Dem 1972 zum Dorfmeister gekürten Sepp" (the cup's inscription); "zur Verfügung stellen / stehen"
- `auf-der-stockbahn` spiel-b1: "die Daube" (the Stock target); "Stockschießen"
- `bei-der-doppelharpfe` vorraete-b2: "abhängig von"; "der Stammtisch"
- `bei-der-doppelharpfe` vorraete-b1: "zwanzig Meter Holz" (Raummeter, as the Harpfe's Franz counts it)
- `bei-der-doppelharpfe` abend-b2: "Ich bin im Gras gelegen" (Austrian, sein); "sich keine grauen Haare wachsen lassen"
- `bei-der-doppelharpfe` abend-b1: "der Toplar" (Slovene toplar, the double hayrack); "die Gelsen" (Austrian for Stechmücken)
- `bei-der-wegkapelle` pilger-b2: "Platz nehmen / Platz machen"; "Wer gibt, dem wird gegeben"
- `bei-der-wegkapelle` pilger-b1: "Maria Luggau" (Slovene kept as is in Slovene)
- `bei-der-wegkapelle` kerzen-b2: "säße ich heute vielleicht in New York"; "ob ich mit ihm nach Amerika geh"
- `bei-der-wegkapelle` kerzen-b1: "gleich nach dem Mittagsschlaf von der Resi"
- `beim-bienenhaus` stoecke-b2: "in Kärnten darf nur die Carnica gehalten werden" (Carinthia's bee law: check); "kranjska sivka, die Graue aus Krain"
- `beim-bienenhaus` stoecke-b1: "aufm Dach" (spoken, the article squeezed) named in the why; "der Schleier"
- `beim-bienenhaus` schwarm-b2: "wie reife Zwetschken" (Austrian Zwetschke); "bekannt für / bekannt mit"
- `beim-bienenhaus` schwarm-b1: "Ein Schwarm im Mai ist wert ein Fuder Heu" (the saying); "trotz dem Lärm" as a second right answer
- `beim-feuerwehrhaus` schlaeuche-b2: "Der ist hin"; "Der lässt sich nicht mehr flicken" against "lassen" (have it done); "dürfte"
- `beim-feuerwehrhaus` schlaeuche-b1: "innerhalb von einer Woche" as a second right answer beside "innerhalb einer Woche"
- `beim-feuerwehrhaus` abend-b2: "Und ob!"; "der Gustl"; "Rücksicht nehmen auf"
- `beim-feuerwehrhaus` abend-b1: "zusperren" (Austrian for abschließen); "Wo steckt er bloß?"
- `im-keller` most-b2: "Most" in Austria (fermented) and in parts of Germany (fresh juice): check how to say it; "das Krügerl"; "kapiert"
- `im-keller` most-b1: "Der Most rinnt" (rinnen, Austrian for fließen)
- `im-keller` kerze-b2: "das Landesmuseum in Klagenfurt"; "die Tafeln" (Slovene končnice, the painted hive fronts)
- `im-keller` kerze-b1: "Das freut einen" against "freut man"
- `im-obstgarten` baeume-b2: frost protection by sprinkling (the ice at about zero degrees): check it reads right
- `im-obstgarten` baeume-b1: "der Maschanzker" (kept as is in Slovene); "kaum mehr"
- `im-obstgarten` birnen-b2: "die Nase rümpfen"; "im Kasten" (Austrian Schrank)
- `im-obstgarten` birnen-b1: "Wie du auf die Welt gekommen bist?" as a second right answer (spoken wie for als)
- `im-wald` schwammerl-b2: "der Gallenröhrling" (Slovene žolčasti goban: check); "Der schaut aus, als wär er ein Steinpilz"
- `im-wald` schwammerl-b1: "die Eierschwammerl" (Austrian Pfifferlinge)
- `im-wald` daemmerung-b2: "der Wildwechsel"; "Sehr geehrter Herr Bürgermeister!" with an exclamation mark (Austrian); "das Rufzeichen"
- `im-wald` daemmerung-b1: "am Rand vom Wald" as a second right answer beside the genitive; "das Kitz"
- `im-zelt` laterne-b2: "die Laurentiustränen" (Slovene Lovrenčeve solze: check); "froh um" (Austrian) beside "froh über", both right
- `im-zelt` laterne-b1: the book's Präteritum ("stieg") and the Austrian "die Stiegen" (stairs)
- `im-zelt` morgen-b2: "Die Hühner sind … einzusperren" (sein + zu); "einsperren / aussperren"
- `im-zelt` morgen-b1: "an deiner Stelle"; "Lieber schwer tragen als nass frieren"
- `in-der-kirche` blumen-b2: "Der neue Pfarrer soll aus Slowenien sein" (soll: hearsay); "neugierig auf"
- `in-der-kirche` blumen-b1: "irgendwer" and "jemand", both right
- `in-der-kirche` kerze-b2: the bell melted down in the war (for cannons): a common Austrian story, check it doesn't clash with the church's lore; "der Mesner"
- `in-der-kirche` kerze-b1: "die Schularbeit" (Austrian class test); "die Daumen drücken"
- `in-der-kueche` kasnudeln-b2: "als traditionelles Lebensmittel anerkannt worden" (the BMLUK list, as the market model settled); "Die nach altem Rezept von Hand gekrendelten Kasnudeln"
- `in-der-kueche` kasnudeln-b1: "Topfen, Erdäpfel und Minze in die Fülle"; "Eine gute Nudel braucht einen guten Krendel" (Resi's saying, VILLAGERS.md)
- `in-der-kueche` radio-b2: "das Wunschkonzert"; "er spiele / er spielt" both right
- `in-der-kueche` radio-b1: "dass es morgen schneien wird" (werden last); "wegen dem Schnee" as a second right answer
- `in-der-muehle` mahlen-b2: "einen Beschluss fassen"; "die Franzlmühle" (made up for the old mill: check it doesn't clash)
- `in-der-muehle` mahlen-b1: "der Heidensterz" (Slovene ajdovi žganci)
- `in-der-muehle` lohn-b2: "der Mahllohn", "der Metzen" (Slovene mernik); "in Frage stellen"
- `in-der-muehle` lohn-b1: "der Metzen" (the old measure, the miller's share)
- `in-der-schmiede` hufeisen-b2: "narrisch" (Carinthian/Austrian: crazy, playing up); "der Gaul"; "dann ist Ruh"
- `in-der-schmiede` hufeisen-b1: "Es kommt darauf an, dass …"; "der Amboss"
- `in-der-schmiede` bestellung-b2: "müssten bis 30. November geliefert worden sein"; "Frau Magister" (Austrian titles); "das Landesamt"
- `in-der-schmiede` bestellung-b1: "einen" (one, standing in for der Haken); "Wäre Freitag möglich?"
- `in-der-schule` tafel-b2: "Ich bin … gesessen" (Austrian) beside "habe gesessen", both right; "anstreichen", "der Rotstift"
- `in-der-schule` tafel-b1: the gap sentences on the board stay German in every base
- `in-der-schule` landkarte-b2: "die in Südtirol entspringende Drau"; "Deutsche" (an adjective as a noun) against "Deutscherin"
- `in-der-schule` landkarte-b1: "der größte See von Kärnten" as a second right answer beside "Kärntens"; Faaker See / Baško jezero, Mittagskogel / Kepa
- `in-der-stube` ofen-b2: "Er meinte, ich solle … einbauen lassen"; "die Ofenbank"; "Neues ist gut, Altes ist warm" (made up for Sepp)
- `in-der-stube` ofen-b1: "Das tut einem gut" against "tut man gut"; "die Scheiter" (Austrian plural of Scheit)
- `in-der-stube` foto-b2: "Hätte die Musik nicht so lang auf sich warten lassen"; "die tanzenden Paare"; "Wer warten kann, findet mehr als Musik" (made up)
- `in-der-stube` foto-b1: "das Kleid von der Braut" as a second right answer beside the genitive; Hanni (Sepp's wife, from the A1 variant)
- `in-der-werkstatt` naegel-b2: "Wer misst, misst Mist" (the workshop saying); "der Neffe" (Franz's nephew from Graz, made up)
- `in-der-werkstatt` naegel-b1: "Wer's eilig hat, hat am Ende Scherben" (made up for Franz)
- `in-der-werkstatt` schindeln-b2: "Blech sei billiger, sagt ihr der Händler"; "jemandem etwas ausreden"; "fertig mit"
- `in-der-werkstatt` schindeln-b1: Florian's Alm hut (am-see's alm-b1-huette names Franz for the shingles)
- `unter-dem-maibaum` tanz-b2: "wäre fast gestohlen worden"; "dank dem Florian" (dank with the dative, spoken and written); "die Burschen" (Austrian)
- `unter-dem-maibaum` tanz-b1: "Mir wird schwindlig"; "hüpf!"
- `unter-dem-maibaum` wache-b2: "das Gespött vom ganzen Tal"; "das Spottlied"; "stupsen"
- `unter-dem-maibaum` wache-b1: buying the maypole back "mit Bier und einer Jause" (the custom)
- `unter-der-kirchturmuhr` aufziehen-b2: "auf Funk umstellen"; "nicht zu ersetzen"; "angegriffen" (Austrian for angefasst)
- `unter-der-kirchturmuhr` aufziehen-b1: "Eine Taube hatte sich ins Uhrwerk gesetzt"
- `unter-der-kirchturmuhr` frueher-b2: "Die von den Dorfbewohnern gespendete Uhr schlägt für alle"; "hineinmeißeln lassen"
- `unter-der-kirchturmuhr` frueher-b1: "Immer wenn …" (repeated past) against "als"; "Die Uhr macht pünktlich, das Herz macht froh" (made up for Sepp)

## What

| Scene | Happening (who) | B2 | B1 |
|---|---|---|---|
| `am-bach` | `waesche` (Resi) | `waesche-b2-tischtuch`: konjunktiv-vergangenheit, modalverben-vermutung | `waesche-b1-mutter`: temporalsaetze, obwohl-trotzdem |
|  | `schafe` (Florian) | `schafe-b2-wolf`: modalverben-vermutung, indem-sodass | `schafe-b1-flocke`: relativsatz, infinitiv-zu |
| `am-dachboden` | `wiege` (Kathi) | `wiege-b2-haendler`: zwar-aber, konjunktiv-vergangenheit | `wiege-b1-urgrossvater`: genitiv, konjunktiv-zwei |
|  | `kletzen` (Kathi) | `kletzen-b2-maeuse`: modalverben-vermutung, partizip-adjektiv | `kletzen-b1-adventmarkt`: passiv, infinitiv-zu |
| `am-dorfbrunnen` | `neuigkeiten` (Mitzi) | `neuigkeiten-b2-film`: indirekte-rede, konjunktiv-vergangenheit | `neuigkeiten-b1-winter`: werden-futur, passiv |
|  | `wasser` (Toni) | `wasser-b2-probe`: nomen-verb, passiv-ersatz | `wasser-b1-quelle`: relativsatz, plusquamperfekt |
| `am-feuer` | `tee` (Resi) | `tee-b2-mutter`: konjunktiv-vergangenheit | `tee-b1-kraeuter`: relativsatz, konjunktiv-zwei |
| `am-see` | `alm` (Florian) | `alm-b2-preis`: partizipialattribut, adjektive-praepositionen | `alm-b1-huette`: doppelkonnektoren, obwohl-trotzdem |
|  | `morgenrot` (Florian) | `morgenrot-b2-abschied`: konjunktiv-vergangenheit, nomen-verb | `morgenrot-b1-melken`: plusquamperfekt, temporalsaetze |
| `am-spielplatz` | `tempelhuepfen` (Eva) | `tempelhuepfen-b2-regeln`: adjektiv-nomen, indirekte-rede | `tempelhuepfen-b1-kreide`: praepositionen-genitiv, adjektiv-tabelle |
|  | `seifenblasen` (Eva) | `seifenblasen-b2-rezept`: nominalisierung, adjektiv-nomen | `seifenblasen-b1-schulfest`: konjunktiv-zwei, genitiv |
| `am-teich` | `reiher` (Stanko) | `reiher-b2-fischer`: passiv-zeiten, meaning | `reiher-b1-geduld`: wo-da-woerter, temporalsaetze |
|  | `angeln` (Sepp) | `angeln-b2-wurm`: oesterreichisch, satzbau | `angeln-b1-hecht`: adjektiv-tabelle, doppelkonnektoren |
| `am-tor` | `riegel` (Toni) | `riegel-b2-scharnier`: n-deklination, konjunktiv-vergangenheit | `riegel-b1-bitte`: konjunktiv-zwei, indefinitpronomen |
|  | `herde` (Florian) | `herde-b2-maisfeld`: konjunktiv-vergangenheit, nomen-verb | `herde-b1-riegel`: infinitiv-zu, konjunktiv-zwei |
| `an-der-bruecke` | `wagen` (Franz) | `wagen-b2-genehmigung`: passiv-ersatz, formeller-brief | `wagen-b1-heft`: praeteritum, temporalsaetze |
|  | `hochwasser` (Toni) | `hochwasser-b2-striche`: modalverben-vermutung, satzbau | `hochwasser-b1-pfeiler`: relativsatz, werden-futur |
| `auf-dem-aussichtsturm` | `aussicht` (Kathi) | `aussicht-b2-sprachen`: indem-sodass, adjektive-praepositionen | `aussicht-b1-dreilaender`: doppelkonnektoren, konjunktiv-zwei |
|  | `stufen` (Sepp) | `stufen-b2-schuett`: partizip-adjektiv, modalverben-vermutung | `stufen-b1-hut`: infinitiv-zu, plusquamperfekt |
| `auf-dem-dorfplatz` | `gasthaus` (Kathi) | `gasthaus-b2-bewertung`: adjektiv-nomen, modalverben-vermutung | `gasthaus-b1-speisekarte`: passiv, konjunktiv-zwei |
|  | `bank` (Sepp) | `bank-b2-glockner`: konjunktiv-vergangenheit, oesterreichisch | `bank-b1-geschichte`: praeteritum, werden-futur |
| `auf-dem-feld` | `ernte` (Franz) | `ernte-b2-hagel` (7-8-9): passiv-zeiten, nomen-verb | `ernte-b1-heiden`: praepositionen-genitiv, wo-da-woerter |
|  | `pferd` (Franz) | `pferd-b2-kufenstechen`: als-ob, zwar-aber | `pferd-b1-aepfel`: praepositionen-genitiv, konjunktiv-zwei |
| `auf-dem-wachturm` | `wache` (Florian) | `wache-b2-hund`: n-deklination, indem-sodass | `wache-b1-fuchs`: genitiv, indefinitpronomen |
|  | `aussicht` (Franz) | `aussicht-b2-buergermeister` (10): meaning, indirekte-rede | `aussicht-b1-daecher`: genitiv, konjunktiv-zwei |
| `auf-der-bienenwiese` | `blumen` (Stanko) | `blumen-b2-schild`: nominalisierung, adjektive-praepositionen | `blumen-b1-bienentag` (4-5): relativsatz, doppelkonnektoren |
|  | `tee` (Resi) | `tee-b2-imkern`: als-ob, nominalisierung | `tee-b1-husten`: konjunktiv-zwei, praepositionen-genitiv |
| `auf-der-stockbahn` | `laube` (Stefan) | `laube-b2-ferdl`: adjektiv-nomen, meaning | `laube-b1-dreilaendereck`: relativsatz, konjunktiv-zwei |
|  | `spiel` (Sepp) | `spiel-b2-pokal`: partizipialattribut, nomen-verb | `spiel-b1-stock`: relativsatz, obwohl-trotzdem |
| `bei-der-doppelharpfe` | `vorraete` (Franz) | `vorraete-b2-reserve`: adjektive-praepositionen, meaning | `vorraete-b1-holz`: adjektiv-tabelle, konjunktiv-zwei |
|  | `abend` (Florian) | `abend-b2-kufenstechen`: konjunktiv-vergangenheit, meaning | `abend-b1-fledermaeuse`: genitiv, konjunktiv-zwei |
| `bei-der-wegkapelle` | `pilger` (Resi) | `pilger-b2-fremder`: adjektiv-nomen, nomen-verb | `pilger-b1-luggau`: praepositionen-genitiv, relativsatz |
|  | `kerzen` (Mitzi) | `kerzen-b2-hansi`: konjunktiv-vergangenheit, satzbau | `kerzen-b1-geburtstag`: temporalsaetze, adjektiv-tabelle |
| `beim-bienenhaus` | `stoecke` (Stanko) | `stoecke-b2-carnica`: meaning, n-deklination | `stoecke-b1-koenigin`: relativsatz, indefinitpronomen |
|  | `schwarm` (Sepp) | `schwarm-b2-korb`: indem-sodass, adjektive-praepositionen | `schwarm-b1-linde`: infinitiv-zu, praepositionen-genitiv |
| `beim-feuerwehrhaus` | `schlaeuche` (Toni) | `schlaeuche-b2-alt`: passiv-ersatz, modalverben-vermutung | `schlaeuche-b1-flicken`: indefinitpronomen, praepositionen-genitiv |
|  | `abend` (Stefan) | `abend-b2-nachbarn` (6-7-8): n-deklination, nomen-verb | `abend-b1-fest` (6-7-8): konjunktiv-zwei, werden-futur |
| `im-keller` | `most` (Stefan) | `most-b2-gast`: oesterreichisch, konjunktiv-vergangenheit | `most-b1-fass`: werden-futur, relativsatz |
|  | `kerze` (Stanko) | `kerze-b2-museum`: partizip-adjektiv, konjunktiv-vergangenheit | `kerze-b1-wachs`: genitiv, indefinitpronomen |
| `im-obstgarten` | `baeume` (Stefan) | `baeume-b2-frost` (4-5): meaning, partizip-adjektiv | `baeume-b1-sorten`: relativsatz, infinitiv-zu |
|  | `birnen` (Sepp) | `birnen-b2-kletzen`: konjunktiv-vergangenheit, zwar-aber | `birnen-b1-baum`: temporalsaetze, adjektiv-tabelle |
| `im-wald` | `schwammerl` (Resi) | `schwammerl-b2-doppelgaenger`: als-ob, adjektive-praepositionen | `schwammerl-b1-eierschwammerl`: temporalsaetze, indefinitpronomen |
|  | `daemmerung` (Stanko) | `daemmerung-b2-strasse`: formeller-brief | `daemmerung-b1-rehe`: obwohl-trotzdem, genitiv |
| `im-zelt` | `laterne` (Florian) | `laterne-b2-sternschnuppen` (7-8): partizip-adjektiv, adjektive-praepositionen | `laterne-b1-buch`: praeteritum, infinitiv-zu |
|  | `morgen` (Florian) | `morgen-b2-zettel`: passiv-ersatz, indem-sodass | `morgen-b1-rucksack`: adjektiv-tabelle, konjunktiv-zwei |
| `in-der-kirche` | `blumen` (Mitzi) | `blumen-b2-pfarrer`: modalverben-vermutung, adjektive-praepositionen | `blumen-b1-sonntag`: relativsatz, indefinitpronomen |
|  | `kerze` (Resi) | `kerze-b2-glocke`: passiv-zeiten, nominalisierung | `kerze-b1-schularbeit`: wo-da-woerter, temporalsaetze |
| `in-der-kueche` | `kasnudeln` (Resi) | `kasnudeln-b2-urkunde`: partizipialattribut, adjektive-praepositionen | `kasnudeln-b1-rezept`: genitiv, indefinitpronomen |
|  | `radio` (Mitzi) | `radio-b2-wunschkonzert`: meaning, indirekte-rede | `radio-b1-schnee` (11-12-1-2): werden-futur, praepositionen-genitiv |
| `in-der-muehle` | `mahlen` (Franz) | `mahlen-b2-denkmal`: nomen-verb, n-deklination | `mahlen-b1-sterz`: passiv, genitiv |
|  | `lohn` (Stefan) | `lohn-b2-gemeinde`: satzbau, nomen-verb | `lohn-b1-metzen`: relativsatz, temporalsaetze |
| `in-der-schmiede` | `hufeisen` (Toni) | `hufeisen-b2-gaul`: als-ob, adjektive-praepositionen | `hufeisen-b1-hammer`: adjektiv-tabelle, wo-da-woerter |
|  | `bestellung` (Toni) | `bestellung-b2-gitter`: passiv-zeiten, formeller-brief | `bestellung-b1-haken`: indefinitpronomen, konjunktiv-zwei |
| `in-der-schule` | `tafel` (Eva) | `tafel-b2-rotstift`: oesterreichisch, passiv-zeiten | `tafel-b1-relativsatz`: relativsatz, konjunktiv-zwei |
|  | `landkarte` (Eva) | `landkarte-b2-drau`: partizipialattribut, adjektiv-nomen | `landkarte-b1-seen`: genitiv, relativsatz |
| `in-der-stube` | `ofen` (Sepp) | `ofen-b2-waermepumpe`: indirekte-rede, adjektiv-nomen | `ofen-b1-feuer`: plusquamperfekt, indefinitpronomen |
|  | `foto` (Sepp) | `foto-b2-kirchtag`: konjunktiv-vergangenheit, partizip-adjektiv | `foto-b1-hochzeit`: relativsatz, genitiv |
| `in-der-werkstatt` | `naegel` (Franz) | `naegel-b2-mist`: meaning, n-deklination | `naegel-b1-regal`: doppelkonnektoren, temporalsaetze |
|  | `schindeln` (Franz) | `schindeln-b2-blech`: konjunktiv-vergangenheit, adjektive-praepositionen | `schindeln-b1-huette`: relativsatz, doppelkonnektoren |
| `unter-dem-maibaum` | `tanz` (Stefan) | `tanz-b2-maibaum` (4-5): konjunktiv-vergangenheit, modalverben-vermutung | `tanz-b1-polka`: adjektiv-tabelle, werden-futur |
|  | `wache` (Florian) | `wache-b2-gespoett`: meaning, indem-sodass | `wache-b1-glocke`: temporalsaetze, konjunktiv-zwei |
| `unter-der-kirchturmuhr` | `aufziehen` (Toni) | `aufziehen-b2-funkuhr`: n-deklination, passiv-ersatz | `aufziehen-b1-schluessel`: werden-futur, plusquamperfekt |
|  | `frueher` (Sepp) | `frueher-b2-spende`: partizipialattribut, konjunktiv-vergangenheit | `frueher-b1-sonnenuhr`: temporalsaetze, konjunktiv-zwei |


The grammar pages the wrong choices name: all 15 of German's B1 pages and all 18 of its B2 pages (konjunktiv-vergangenheit
20 times, konjunktiv-zwei 19, relativsatz 17, temporalsaetze 12, genitiv 11, adjektive-praepositionen 11, indefinitpronomen
10, modalverben-vermutung 9, nomen-verb 9, the rest 3 to 8). A B2 turn's wrong choice that is a misunderstanding has no page
("meaning" above), its why in meaning words, as the guide allows. A B1 dialog names B1 and B2 pages, a B2 one B1 and B2
(`levelPageErrors`).

**The register as each villager has it** (VILLAGERS.md): Kathi, Franz, Toni and Eva say du, the learner says Sie;
Resi, Sepp, Florian, Stefan, Stanko and Mitzi du both ways. Only the happening's person speaks, first and last, so every
turn is answered by them (no `to`). A line that names another villager (Oma Resi, der Florian, Herr Franz, die Lena, the
Lehrerin Eva) waits until they live in the village and are met, as any text does ("Who a text may name").

**Counts**: 126 dialogs (63 B1, 63 B2) in 32 scenes; 737 entries (485 lines, 252 turns), 531 choices (254 wrong, each
with its why in four languages and a reaction under 80 characters that never says the right form), 1,547 texts to voice.
Each B1 dialog 5–7 entries with two turns, each B2 6 with two turns.

**Voices** (no ElevenLabs called): a voice build of the new texts in German, the lines, the choices and the replies:
**108,374 characters** (1,547 texts): the learner's choices 31,175, Sepp 11,370, Florian 10,765, Franz 9,637, Resi 8,641,
Toni 7,954, Stefan 7,071, Stanko 6,235, Eva 5,361, Mitzi 5,105, Kathi 5,060. No new voices: all eleven have theirs in the
cast.

## Words

Every word of the new lines a tap meets has a meaning: `companion/lexicon/de.extra.json` gets 366 new entries and 198 forms
on existing ones (the verbs' participles and speech forms red, les, dreh, treff; the contractions hat's, du's, wir's,
sind's; the nouns' genitives and plurals; the pack's own meanings where a pack word lacked the form: Stämmen, Zauns,
Wachses, Bürgermeisters). A new entry's prompt is its own in each base against the packs and the other entries (a
scratch check, as supplement-coverage's): Radl "bicikel" beside Fahrrad "kolo", Holler and Glockerl as forms of the
pack's Holunder and Glöckerl. `lexicon-build --coverage --lang de`: 99.9 % of tokens and 99.1 % of words known, as
before (6,275 words now, from 5,534); not known only the wrong forms the turns offer on purpose (heißte, schlafte,
Deutscherin, angeknabbernd, des Names) and what was left before.

## Checks

- `bun test/check-culture.ts kaernten`: ok (the levels and the pages).
- The smoke (LANI_SMOKE_PORT=10630, LANI_SMOKE_DRAMA3_PORT=10648): 3,723 checks, all passed (a first run found one turn whose wrong choice read the same as the right one in Slovene to a woman: fixed).
- `bunx tsc --noEmit -p .` clean; `tutor-export --check`: 140 files match the bridge.
- `bun test/prompt-clashes.ts`: only the baseline's.
- On the way the Android scene tests asked for more than the guide says, and the variants keep to it: a wrong
  choice's reaction is one line of 80 characters at most and never echoes the words that set the right choice apart
  (DialogReactionsTest: 39 reactions shortened or reworded); no line says "schau" about what the picture can't show
  (SceneCoherenceTest: 27 lines said otherwise); a wrong choice's translation never reads like the right one's to a man or
  a woman (the smoke).
- Android, through lani-slot.sh: the scene tests (`si.lanisce.lani.game.scene.*`, 36 classes with DialogLevelsTest,
  DialogReactionsTest, WhoAnswersTest, VariantsContentTest, ScenesContentTest, CultureScenesTest, SceneCoherenceTest) and
  IntroductionContentTest: 273 tests, none failed. The whole suite comes with the integration (followups-15.md).
- `python3 -m unittest discover -s tests`: 253 OK; `-s companion/tests`: 164 OK (11 skipped).
- Emulator, through qa-slot: `LANI_DEV_DATA=$(bun test/fixtures/make-learner.ts <dir> --target de --base en)
  LANI_CULTURE=kaernten LANI_QA_PAIR=sl-en companion/bin/qa-slot --steps kaernten-levels` (a new named-only step in
  bin/qa beside the model's `dialog-levels`): an older app's `?arts=6` gets none of the field's and the square's levelled
  variants; at `level:B2` Franz's `pferd-b2-kufenstechen` and Kathi's `gasthaus-b2-bewertung` open with their first lines,
  at `level:B1` `pferd-b1-aepfel` and `gasthaus-b1-speisekarte`; at `level:A1` the square's B1 variant isn't in the scene
  and Kathi's own dialog plays ("Setz dich, ich komme gleich"), the B1 line nowhere. Setup, the step and the crash check
  ok. Screenshots: `levels-kaernten-{feld-b2,feld-b1,platz-b2,platz-b1,platz-a1}.png` in the scratchpad's
  ux-dialogs-kaernten/ and companion/qa-shots/20261103-214250-slot2. Without LANI_QA_PAIR=sl-en QA's setup doesn't find
  Home on a German learner's phone (its labels are de · en).

## What's left

- The native check above; the voice build (108,374 characters) is Jan's to decide.
- After the model (5fd43a08) is merged, its QA step `dialog-levels` and this branch's `kaernten-levels` sit side by
  side in bin/qa; the lead may fold them into one.
