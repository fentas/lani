# Talk scenarios in German and English (scenarios-de-en)

Built 2026-11-01 on branch `scenarios-de-en`, after scenarios-it (docs/plans/05-built/scenarios-it.md), which made the
free talks ("🗣️ Pogovor · Talk": role-plays where the tutor plays someone in a real situation) per language and pair.
The Carinthian village (German) and the Lake District village (English) had none: their list said there were no scenes
yet. Now each has eight for a grown-up, A1–A2, with every text the learner reads in each base the language has, word
packs where none fitted, and scenes that lead into them. Jan's six Slovene role-plays read in German and Italian too.
Everything written here is machine-written and needs a native check (the list at the end).

## What

- **Eight German role-plays** in `companion/scenarios/de/`, for a grown-up (`audience: "adult"`), in Austrian German,
  with Slovene, English and Italian for every text the learner reads:

  | Id | Level | Who | Packs |
  |---|---|---|---|
  | `beim-baecker` 🥨 Beim Bäcker | A1 | Bäcker Peter, the village baker (Semmel, Kipferl, Sackerl; Sie) | essen, gruesse, im-dorf, zahlen-und-uhrzeit |
  | `auf-dem-bauernmarkt` 🍎 Auf dem Bauernmarkt | A1 | Mostbauer Stefan (the cast's) at his stall: apples, Most, Gailtaler Speck; du | auf-dem-markt, essen, zahlen-und-uhrzeit, gruesse |
  | `beim-arzt` 🩺 Beim Arzt | A1 | Dr. Wieser, the family doctor in his Ordination; the e-card; Sie | beim-arzt, gruesse, zahlen-und-uhrzeit |
  | `im-gasthaus` 🍺 Im Gasthaus | A1 | Wirtin Kathi (the cast's): a Krügerl, an Apfelsaft g'spritzt, Kasnudeln, "Stimmt so"; Sie | im-gasthaus, essen, gruesse, zahlen-und-uhrzeit |
  | `am-bahnhof-in-villach` 🚆 Am Bahnhof in Villach | A1 | Herr Kofler at the ÖBB counter: to Klagenfurt, hin und retour, the Bahnsteig; Sie | am-bahnhof, zahlen-und-uhrzeit, gruesse |
  | `beim-kirchtag` 🎺 Beim Kirchtag | A2 | Tante Mitzi (the cast's): the Kirchtagssuppe, the Kufenstechen, the linden dance; du | fest-kirchtag, fest-kufenstechen, essen, gruesse |
  | `die-nachbarin-am-zaun` 🌻 Die Nachbarin am Zaun | A2 | Frau Moser, the retired neighbour: the heat, the Ribisel, the Paradeiser; Sie, then she offers du | wetter-und-garten, haus-und-kueche, essen, gruesse |
  | `ein-anruf-bei-der-gemeinde` ☎️ Ein Anruf bei der Gemeinde | A2 | Frau Pirker at the Gemeindeamt, on the phone: the Meldezettel, the Parteienverkehr, spelling a name, "Auf Wiederhören!"; Sie | auf-der-gemeinde, zahlen-und-uhrzeit, gruesse |

- **Eight English role-plays** in `companion/scenarios/en/`, for a grown-up, in British English with Cumbrian touches,
  with Slovene, German and Italian for every text:

  | Id | Level | Who | Packs |
  |---|---|---|---|
  | `at-the-farm-shop` 🥚 At the farm shop | A1 | Mrs Atkinson at her Herdwick farm's shop: half a dozen eggs, Cumberland sausage, damson jam | food, at-the-market, numbers-and-time, greetings |
  | `in-the-pub` 🍺 In the pub | A1 | Mrs Birkett (the cast's) behind the bar of the Packhorse Inn: order and pay at the bar, the table number | in-the-pub, food, numbers-and-time, greetings |
  | `at-the-gp` 🩺 At the GP | A1 | Dr Fleming, the GP at the surgery in Keswick: a sore throat, a temperature | at-the-doctors, greetings, numbers-and-time |
  | `at-the-bus-stop-in-keswick` 🚌 At the bus stop in Keswick | A1 | Mrs Relph, a local waiting for the 555 to Grasmere: when, how long, a single or a return | getting-about, numbers-and-time, in-the-village, greetings |
  | `the-b-and-b-check-in` 🛏️ The B&B check-in | A1 | Mrs Hodgson, the landlady: the booking, two nights, breakfast, the Wi-Fi password | at-the-b-and-b, food, numbers-and-time, greetings |
  | `asking-the-way-on-the-fell` 🧭 Asking the way on the fell | A2 | Shepherd Jack (the cast's) in the mist: the wall, the stile, the beck, the cairn, the tarn | asking-the-way, on-the-fell, in-the-village, greetings |
  | `the-village-fete` 🎪 The village fête | A2 | Auntie Edna (the cast's) at her cake stall: Victoria sponge, raffle tickets, the tug of war, the hound trail | at-the-fete, food, on-the-green, feast-show, numbers-and-time |
  | `the-neighbour-over-the-wall` 🧱 The neighbour over the wall | A2 | Mr Bowness, the retired farmer next door, over the drystone wall: "Now then! Grand day", the rain, his dog Meg | weather-and-garden, in-the-village, home-and-kitchen, family, greetings |

- **Twelve word packs** where none fitted: German `beim-arzt`, `im-gasthaus`, `am-bahnhof`, `auf-der-gemeinde`,
  `wetter-und-garten` (`companion/packs/de/`), English `at-the-doctors`, `in-the-pub`, `getting-about`, `at-the-b-and-b`,
  `asking-the-way`, `at-the-fete`, `weather-and-garden` (`companion/packs/en/`). Each has 16–19 words or phrases (German 87, English 120), a giver
  of the cast (Oma Resi, Wirtin Kathi, Opa Sepp, Lehrerin Eva, Müller Franz; Granny Maggie, Mrs Birkett, Auntie Edna,
  Shepherd Jack, Mr Tyson), an example in every language and notes where a word needs one (Austrian against German:
  Ordination, Halsweh, Krügerl, Seidl, g'spritzt, hin und retour, Parteienverkehr, Ribisel; British: GP, surgery, a
  temperature, chemist, single and return, full English, cairn, gill, stile, grand). A word another pack of the village
  has is left out (the pub's "bar" is "the bar": the gate's bar is another word), or its phrase taken instead ("Es
  regnet.", "I'm lost.").
- **The cast plays its own.** Six role-plays name a villager of the cast, who plays them (their sprite and voice, and the
  friendship grows once met, as scenarios-it built): Wirtin Kathi, Mostbauer Stefan, Tante Mitzi; Mrs Birkett, Shepherd
  Jack, Auntie Edna. The others stand in by their title: the app knows the German and English ones now (Frau, Herr,
  Bäcker, Wirtin, Nachbarin, Arzt, Oma, Tante; Mrs, Mr, Dr, Granny, Auntie, Landlady …), and "Dr" or "Dr." takes the
  person's gender from their voice (Dr Fleming is drawn as a woman).
- **The scenes lead into them.** After a dialog, "keep talking" opens the role-play's intro: in Carinthia, Kathi at the
  Gasthaus (the square and the market) into `im-gasthaus`, Stefan's Most and the big market day into
  `auf-dem-bauernmarkt`, Opa Sepp's music under the linden and Kathi's Kirchtag shopping into `beim-kirchtag`; in the
  Lake District, Mrs Birkett's twelve walkers at the market into `in-the-pub`, Granny Maggie's eggs into
  `at-the-farm-shop`, Old Wilf's brass band into `the-village-fete`, Shepherd Jack's cow and sunset on the fell into
  `asking-the-way-on-the-fell` (every variant of those dialogs).
- **Never a child into a grown-up's.** A child is never offered a role-play marked `audience: "adult"`; now a scene
  never leads a child into one either: the bridge serves a child the dialog without its `talk` (`talksOffered`). This
  was scenarios-it's open point (a child in a Slovene village would have met the inn's link).
- **Jan's six read in German and Italian.** `na-trznici`, `nedeljsko-kosilo`, `pri-zdravniku`, `soseda-cez-ograjo`,
  `v-gostilni`, `v-pekarni`: the setting, each goal and the meanings of the opener and the phrases per base (en as it
  was, de, it), the title per language, and who the character is ("Tašča · The mother-in-law", "Natakar · The waiter").
  Jan reads every title, goal and meaning as before; under the character's name the app now shows who they are in the
  pair instead of the role's English words ("the baker at the counter. Friendly and quick. Says vi to customers").
- **The dev bridge's canned tutor** answers German and English role-plays in their language (lines that fit any of the
  eight: no soup at the station), with the meanings in every base, a correction explained in the base, the debrief in
  Slovene and Italian too, and the scenario's own phrases as hints.

## How

No format change: `lani.scenario/v0` as scenarios-it left it (module-spec.md, "Role-play scenarios"). A German file's
`title`, goals and `who` are `{de, sl, en, it}`, its `setting`, `opener_en` and each hint's `en` `{sl, en, it}`; an
English file's labels `{en, sl, de, it}` and its texts `{sl, de, it, en}` (the English is the tutor's and the fallback).
The bridge serves them in the learner's pair ("Beim Bäcker · Pri peku", the setting in Slovene) and gives the tutor the
English brief with `language: de` or `en` and the packs.

- `bridge/src/scenarios.ts`: an English role-play's brief named its title twice ("At the farm shop · At the farm shop");
  now once.
- `bridge/src/scenes.ts` `talksOffered`, `features/scenes.ts` (`GET /scenes`, `/scenes/:id`): a dialog's `talk` into a
  role-play the learner isn't offered is left out.
- `android/…/ui/stage/Cast.kt`: the German and English titles; a title of either gender ("Dr", "Dr.") takes the voice's.
- `bridge/test/dev-bridge.ts`: the canned lines in German and English.
- `companion/bin/qa`: the step `friuli-talk` became `village_talk(village)`, with `kaernten-talk` and `lakeland-talk`
  beside it (named only), each with its pair, its role-play, the lines it types and the answers it waits for.

## How to use

- A learner of German or English: Home's "🗣️" talk card, a role-play, Start. Or from a scene: after Kathi's "Setz dich,
  ich komme gleich" on the square, "keep talking" opens `im-gasthaus`'s intro.
- The tutor: `publish_scenario` as before, in the village's language, its texts in the learner's base (module-spec.md).
- QA: `LANI_DEV_DATA=$(cd companion/bridge && bun test/fixtures/make-learner.ts <dir> --target de --base sl --name Ana)
  LANI_CULTURE=kaernten LANI_QA_SETUP_PAIR=de-sl LANI_QA_LEARNER=Ana LANI_QA_TALK_SHOTS=<dir> companion/bin/qa-slot
  --steps kaernten-talk`; English: `--target en --base it --name Giulia`, `LANI_CULTURE=lakeland
  LANI_QA_SETUP_PAIR=en-it LANI_QA_LEARNER=Giulia`, `--steps lakeland-talk`. Another role-play: `LANI_QA_TALK=<id>`.

## Tap counts

| Journey | Before | After |
|---|---|---|
| A learner of German, Home to talking at the baker's | not possible: the list was empty ("no scenes yet"); only a villager's free talk (Home → village → villager → Talk → talk freely) | 3: "🗣️ Gespräch · Pogovor", "Beim Bäcker · Pri peku", "Starten · Začni" (QA: kaernten-talk) |
| A learner of English, Home to talking at the farm shop | not possible (the empty list) | 3: "🗣️ Talk · Conversazione", "At the farm shop · Allo spaccio della fattoria", "Start · Inizia" (QA: lakeland-talk) |
| From Kathi's dialog on the square into a talk at the Gasthaus | none (no `talk` on Carinthian dialogs) | 2 after the dialog: "keep talking", Start (the Friuli scenes' way; the smoke and check-culture check the links, QA didn't shoot it here) |
| Jan, Home to talking | 3 | 3 (unchanged) |

## Checks

- Bridge: `bunx tsc --noEmit -p .`; the smoke, all checks passed (3,366): its scenarios section (Jan's six with
  their texts in en, de and it and their title and who in sl too, served to Jan as before with who in the pair; the
  eight German and eight English files for a grown-up with every text in each base; none of them in Jan's list; a
  child's scene into no grown-up's role-play), kaernten and lakeland (`test/smoke/village-talks.ts`: the eight served and
  nothing else, in de · sl and en · sl; every pack theirs; Kathi, Stefan, Mitzi, Mrs Birkett, Jack and Edna named as the
  cast names them; a turn's brief in English with its language and packs and no child line; a tutor's role-play written
  in the village's language, a Slovene one refused) and pairs (sl ← de: Jan's bakery in German; en ← it: the farm shop
  in Italian). `test/check-culture.ts kaernten`, `lakeland`, `friuli`, `primorska`: ok (the scenes' `talk` links resolve).
  `bun test/learner-content.ts`: no text names Jan. `tutor-export --check`: 89 files match.
- Android: `TalkTest` (every German and English file in de · sl and en · it; Jan's bakery in sl · de and as it was in
  sl · en), `CastTest` (the German and English titles, Dr Fleming drawn as a woman, Dr. Wieser and Dr. Kovač as men).
  The full unit suite through lani-slot.sh: 3,689 tests in 474 classes, 0 failures (2 skipped).
- Python: `python3 -m unittest discover -s tests` (251) and `-s companion/tests` (142, 11 skipped): OK.
- QA through `qa-slot`: `kaernten-talk` on a synthetic Ana (de · sl) and `lakeland-talk` on a synthetic
  Giulia (en · it), slot 1: both passed, 3 taps from Home to talking, no crash. Their shots (`st-*.png`, scratchpad
  `ux-scenarios-de-en/de/` and `en/`, contact sheets `sheet-de.png`, `sheet-en.png`): the list (the eight in the pair,
  with who plays each: Herr Kofler, Mostbauer Stefan, Dr. Wieser …; Shepherd Jack, Mrs Relph, Mrs Atkinson …), the
  intro (the setting in Slovene or Italian, the goals in the pair, Bäcker Peter "Der Bäcker · Pek" under the linden,
  Mrs Atkinson "The farmer at the shop · La contadina allo spaccio" in the field), the opener, the canned tutor's German
  and English answers, a hint that is the scenario's first phrase ("Grüß Gott!", "Morning!"), the correction in the
  base, the goals, the debrief in Slovene and in Italian. The "before" (`LANI_SCENARIOS_DIR` at an empty directory, as the
  village was): the empty list, "Noch keine Szenen · Še ni prizorov" (`before-de/st-list.png`).

## What's left

- **The native check** (below): the Austrian German and the British English first, then the Slovene and Italian bases.
- A doctor's practice, a station, a pub or a B&B have no stage place of their own: the GP and the Arzt stand at a
  stand-in's home (by the fire), as Jan's and Teo's doctors do; the station and the bus stop on the square, the pub and
  the B&B in the kitchen.
- Voice: the sixteen role-plays' openers and phrases (German 64 lines, about 1,600 characters; English 61, about 1,600)
  and the packs' words and examples (German about 4,100 characters, English about 5,300) join the German and English
  voice corpora: about 12,600 characters in all. A node of those villages voices them ahead with its culture's
  ElevenLabs voices (its voice-cast.json), which spends credits: ask Jan before a voice build. Until then the phone's
  text-to-speech reads them. Nothing was spent here.

## Native check

Machine-written, every file flagged in its `review` ("de, sl, en, it: …"; Jan's six "de, it: …"). Settled with sources:

- **Austrian German.** *Ordination* = Arztpraxis, Duden "Medizin, österreichisch"
  (https://www.duden.de/rechtschreibung/Ordination); *Sackerl* "bayrisch, österreichisch"
  (https://www.duden.de/rechtschreibung/Sackerl); *Ribisel* "österreichisch" (https://www.duden.de/rechtschreibung/Ribisel);
  *Parteienverkehr* "österreichisch" = Amtsstunden (https://www.duden.de/rechtschreibung/Parteienverkehr); *Krügel*
  "österreichisch" (https://www.duden.de/rechtschreibung/Kruegel; *Krügerl* 0.5 l and *Seidl* 0.3 l are the spoken
  Austrian measures, not in Duden: to confirm); ÖBB says *Bahnsteig* at Villach Hauptbahnhof
  (https://bahnhof.oebb.at/en/kaernten/villach-hauptbahnhof); the e-card is shown at every doctor's visit
  (https://www.gesundheit.gv.at/gesundheitsleistungen/arztbesuch/e-card.html); one registers a new home within three days
  with the Meldezettel (https://www.oesterreich.gv.at/de/lebenslagen/Ich-wohne-bald-in-einem-neuen-Zuhause/Melden).
- **Carinthia.** The Kirchtagssuppe: beef, chicken and pork, saffron, cream, served with Reindling
  (https://www.kaernten.at/kulinarik/rezepte/kaerntner-kirchtagssuppe/); the lower Gailtal's Kirchtag with the
  Kufenstechen (riders on Noriker horses break a barrel with an iron club) and the linden dance, in Slovene *žegen*
  (https://www.unesco.at/en/culture/intangible-cultural-heritage/national-inventory/news-1/article/traditions-of-the-untergailtaler-kirchtag-and-traditional-costumes-in-the-untergailtal-zilski-zegen-in-ziljska-nosa,
  https://austria-forum.org/af/AustriaWiki/Kufenstechen); Gailtaler Speck (g.g.A.) and Gailtaler Almkäse (g.U.)
  (https://www.bmluk.gv.at/themen/lebensmittel/trad-lebensmittel/kaese/gailtaler_almkaese.html).
- **The Lake District.** The 555 runs Keswick–Grasmere–Ambleside–Windermere–Kendal–Lancaster about hourly
  (https://en.wikipedia.org/wiki/Stagecoach_bus_route_555); Traditional Cumberland Sausage is a PGI coil
  (https://www.gov.uk/government/news/traditional-cumberland-sausages-win-protected-status-in-europe); the shows'
  Cumberland and Westmorland wrestling, fell running and hound trails (https://en.wikipedia.org/wiki/Grasmere_(village));
  beck, fell, tarn, gill from Old Norse (https://en.wikipedia.org/wiki/Cumbrian_toponymy).
- **Slovene.** *žegnanje* (SSKJ², no label: "praznovanje godu zavetnika cerkve", https://fran.si/iskanje?View=1&Query=žegnanje,
  as the Carinthian square's dialog has it); *možic* for a cairn (SSKJ², alp.: "kup zloženega kamenja … za oznako smeri",
  https://fran.si/iskanje?View=1&Query=možic); *suhozid* is only in a stonemasons' dictionary
  (https://fran.si/iskanje?View=1&Query=suhozid), so the drystone wall is "kamniti zid brez malte (suhi zid)"; SSKJ² has
  no feminine of *pek* (https://fran.si/iskanje?View=1&Query=pek), so the Carinthian baker is a man and Jan's Nina stays
  "Prodajalka v pekarni".

What most needs a native eye:

- **German (Austrian, adults).** "Was darf's sein?", "Willst du kosten?" (kosten = taste), "Was fehlt Ihnen denn?",
  "Ich habe Halsweh", "Einen Apfelsaft g'spritzt", "Stimmt so.", "Grüß Gott! Wohin möchten Sie fahren?", "Eine Fahrkarte,
  hin und retour.", "Hast du schon die Kirchtagssuppe gekostet?", "Heute ist es aber heiß, gell?", "Es hat lange nicht
  geregnet.", Frau Moser offering du ("Ich bin die Herta. Sagen wir du?"), "Gemeindeamt, Pirker, grüß Gott!", "Wann haben
  Sie offen?", "Ich wohne neu im Dorf.", "Vielen Dank, auf Wiederhören!". Whether "Mostbauer" at a Bauernmarkt and "Tante
  Mitzi" in a dirndl at the Kirchtag ring true for the lower Gailtal.
- **English (British, Cumbrian).** "Morning, love!", "Half a dozen eggs, please.", "Have you got any Cumberland
  sausage?", "Evening! What can I get you?", "A pint of bitter", "What seems to be the problem?", "I haven't got a
  temperature.", "Are you waiting for the 555 as well?", "a return to Grasmere", "You must be tired after your journey.",
  "Now then! You look a bit lost. Where are you heading?", "Turn left at the cairn.", "Hello, love! The Victoria sponge is
  lovely today.", "What's on next?", "Now then! Grand day, isn't it? Makes a change from all that rain." Whether a Lakes
  farm shop, B&B and fête read like the real ones.
- **German as a base (for English).** "Na, grüß dich!" for "Now then!", "Hallo, Schatz!" for "Hello, love!", "Ein Full
  English, bitte!", "über den Zauntritt" (stile), "Beim Steinmann links abbiegen" (cairn), "Im Hofladen".
- **Slovene as a base.** "Na kmečki tržnici", "Kmet, ki dela mošt", "V ambulanti (Ordination) splošnega zdravnika",
  "Za omizjem v kotu sedijo stalni gostje", "Pri okencu ÖBB sedi uslužbenec", "Na žegnanju", "Teta iz vasi", "Soseda čez
  plot", "Najprej te vika, potem pa ti ponudi tikanje", "Klic na občino", "Uradnica na občini", "na svidenje (ob koncu
  klica)" for Auf Wiederhören (the spoken "na slišanje" is in no dictionary of fran.si:
  https://fran.si/iskanje?View=1&Query=%22na+sli%C5%A1anje%22), "V trgovini na kmetiji", "cumberlandska klobasa … zvita v spiralo", "V pubu", "Pri osebni zdravnici",
  "Vaška veselica", "srečke za tombolo", "Kod se pride do jezerca?", "čez prehod v zidu", "Pri možicu zavij levo", "Sosed
  čez zid", "Krasen dan, kaj?".
- **Italian as a base.** "Allo spaccio della fattoria", "Che cosa le do?", "una pinta di bitter (birra ambrata)", "Una
  gazzosa" (lemonade), "Dal medico di base", "Alla stazione di Villaco", "Alla sagra del paese", "La vicina allo
  steccato", "Fai lo spelling del tuo nome", "Chiedere la strada sul monte", "All'ometto di pietre gira a sinistra"
  (cairn), "oltre la scaletta" (stile), "La festa del paese", "Il vicino oltre il muro".
- **Jan's six, the new bases.** German: "Grüße die Verkäuferin", "Das Sonntagsessen", "Die Nachbarin am Gartenzaun",
  "Bis bald! (zu einer Person: der Dual)"; Italian: "In panetteria", "valle del Vipacco", "Di' che sei
  {m:sazio|f:sazia}", "Ci vediamo! (a una persona: il duale)", "Che cosa prendete da bere?".
- **The packs** (the helper's list, settled on fran.si where Slovene): *ordinacija*, *peron*, *osminka* (an eighth of
  wine), *pomfrit*, *pršec* (drizzle), *grapa* (gill), *srečelov* (the fête's tombola), *možic* (cairn): each on
  https://fran.si/iskanje?View=1&Query=<word>; Slovene *tombola* is the number game, so a raffle is "žrebanje srečk";
  *runda* is colloquial (https://fran.si/iskanje?View=1&Query=runda), so "round" is "pijača za vse za mizo (runda)". To
  check: "kasnudlov", "radler" for shandy, "geslo za wifi", "receptorka", "pihalna godba" for a brass band (no
  woodwinds), "sobe z zajtrkom", "Kar obdržite." (Stimmt so), "miza stalnih gostov" (Stammtisch), "Je to pot do …?",
  "Prosimo, zaprite vrata."; Beljak and Celovec in the examples. Italian: *gassosa*, *pesca di beneficenza*, *ometto*,
  *forra*, "la birra bitter", "Che cosa le servo?", "fare lo spelling", "un ottavo di litro". Austrian German: Krügerl
  0.5 l and Seidl 0.3 l (Duden has Seidel as a glass without a measure, https://www.duden.de/rechtschreibung/Seidel; no
  Krügerl page), the spelling *g'spritzt*, *Mahlzeit*, *die Verkühlung*, *im Krankenstand*, "das Telefon läutet",
  "einen Termin ausmachen", tipping 5–10 %; *Frittatensuppe* is Duden "österreichisch"
  (https://www.duden.de/rechtschreibung/Frittatensuppe). German meanings in the English packs: *der Überstieg* (stile),
  *das Steinmännchen* (cairn), *die Klamm* (gill), *die Sprechstundenhilfe*, *das Radler*, *das Pint*. English: the hound
  trail belongs to the shows more than to a fête.
