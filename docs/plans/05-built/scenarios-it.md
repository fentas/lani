# Teo's free talks in Italian (scenarios-it)

Built 2026-11-01 on branch `scenarios-it`. The free talks ("🗣️ Pogovor · Talk", "🗣️ Conversazione · Gespräch" in Teo's pair:
role-plays where the tutor plays someone in a real situation) had six scenarios, all Slovene and for an adult. Teo's
Italian village had none: its list said there were no scenes yet. Now the Friuli village has nine of its own, written
for a child of about seven in Italian, with German for everything Teo reads. Every village is offered only its own
language's role-plays. Everything here is machine-written and needs a native check (the list at the end).

## What

- **Nine Italian role-plays** in `companion/scenarios/it/`, for a child (`audience: "child"`), each with its goals, its
  character, the packs whose words it uses, its level, and German, Slovene and English for every text the learner reads:

  | Id | Level | Who | Packs |
  |---|---|---|---|
  | `al-mercato` 🍒 Al mercato | A1 | Signora Gianna, the fruit seller at the Saturday market in Cormòns | al-mercato, cibo, numeri-e-ore, saluti |
  | `dal-fornaio` 🥖 Dal fornaio | A1 | Signor Gino, the village baker (gubana; "mandi") | cibo, in-paese, al-mercato, numeri-e-ore, saluti |
  | `dal-pediatra` 🩺 Dal pediatra | A1 | Dottor Bassi, a calm paediatrician in Gorizia (Mamma there; nothing frightening) | il-corpo, saluti, numeri-e-ore |
  | `in-gelateria` 🍦 In gelateria | A1 | Signor Enzo, the ice-cream man in Grado | in-gelateria, cibo, numeri-e-ore, saluti |
  | `la-ricreazione` 🏫 La ricreazione | A1 | Giulia (the cast's), the classmate at break time | a-scuola, cibo, saluti |
  | `pranzo-dalla-nonna` 🍝 Domenica a pranzo dalla nonna | A1 | Nonna Rosa (the cast's) at Sunday lunch | cibo, famiglia, in-cucina, casa-e-cucina |
  | `al-parco-giochi` 🛝 Al parco giochi | A1 | Lorenzo (the cast's) at the sandpit | al-parco-giochi, numeri-e-ore, saluti |
  | `telefonata-alla-nonna` ☎️ Una telefonata alla nonna | A2 | Nonna Lucia in Trieste, on the phone (Mamma dialled) | al-telefono, famiglia, saluti |
  | `la-festa-di-tommaso` 🎂 La festa di Tommaso | A2 | Tommaso (the cast's), turning ten | cibo, in-salotto, numeri-e-ore, saluti |

- **Three new word packs** in `companion/packs/it/` where none fitted: `il-corpo` (18 words: the body and feeling ill,
  for the paediatrician; Nonna Rosa gives it), `in-gelateria` (16: cone, cup, scoops, flavours; Tommaso), `al-telefono`
  (12: Pronto?, Non ti sento!, Ti voglio bene, Mi manchi; Zia Nives). Italian with German, Slovene and English, an
  example in each, notes on the irregular plurals (le braccia, le mani, le orecchie).
- **The scenes lead into them.** Four Friuli scenes' dialogs end in "keep talking" into a role-play (`talk`): Nonna
  Rosa's eggs and hen at the market into `al-mercato`, Giulia's globe and Tommaso's ball at school into
  `la-ricreazione`, Giulia's swing and Lorenzo's sandcastle at the playground into `al-parco-giochi`, Nonna Rosa's
  polenta and breakfast in the kitchen into `pranzo-dalla-nonna` (every variant of those dialogs).
- **Never mixed.** A village is offered only its own language's role-plays, on the node and on the phone (the same
  bridge): a Slovene scenario never reaches an Italian village, an Italian one never Jan's. A tutor's scenario is
  written in the village's language; another language is refused, and so is a pack the village doesn't have.
- **Child-safe.** Each role says how the character talks to a child: kind, one or two short sentences a turn, asking
  nothing personal. A grown-up is close by in each setting (Mamma at the market, Papà at the door). For a child (the
  scenario's audience, or a child's profile) every turn's brief adds a `child` line: kind, patient, short and simple,
  praise; never personal data (full name, address, school, phone, where they are), nothing frightening. The telephone
  nonna asks what Teo played and ate, never where Teo is; Tommaso's party never asks for Teo's own birthday. A child is
  never offered a scenario marked `audience: "adult"` (Jan's `v-gostilni`, with its wine and beer).
- **The dev bridge's canned tutor answers in the scenario's language** (its brief's `language`; before, the course's
  only), with Italian lines that fit any of the nine (no more soup at the gelateria), the scenario's own useful phrases
  as hints in turn, and an Italian correction explained in the learner's base.

## How

**The format** (`lani.scenario/v0`, still v0: every field added is optional, the six Slovene files are as they were
plus their `language`; `.claude/skills/lani-studio/reference/module-spec.md` has the table):

- `language` (`sl`, `it`, `de`, `en`): the language it is played in, the village's. A curated file's is its directory's
  (`companion/scenarios` Slovene, `companion/scenarios/<language>`); a tutor's without one is the village's.
- `audience`: `child` or `adult`.
- `title`, each goal, and the new `who` (who the character is) may be per language with the scenario's own:
  `{"it": "Al mercato", "de": "Auf dem Markt", "sl": "Na tržnici", "en": "At the market"}`.
- `setting`, `opener_en` and each hint's `en` may be per base, `{"de": …, "sl": …, "en": …}` (English always), as the
  Italian course's texts are (COURSE.md, "Texts in the learner's language").
- `packs` (the village's packs it uses) and `review` (who wrote and checked which languages).
- `place`: where the app's stage stands behind the character (`square`, `kitchen`, `school`, `fire`, `forest`, `field`,
  `smithy`): the market, the baker, the ice-cream shop, the playground and the party on the square, Sunday lunch and the
  phone call in the kitchen, break time at school. Before, a stand-in always stood "by the fire". Jan's market and bakery
  now stand on the square, Sunday lunch and the inn in the kitchen.

**The bridge** (`bridge/src/scenarios.ts`): `ScenarioStore` takes the village's language (`build.ts`), skips and logs
a file in another, fills the language in for a tutor's, and refuses another language on publish (`publish_scenario`,
the studio's check of `app/scenarios/*.json`). `offered(child)` drops the grown-ups' for a child. `servedScenario`
gives every text as a string in the learner's pair (`GET /scenarios`, `GET /scenarios/:id`): the title, the goals and
`who` "target · base" ("Saluta la signora · Begrüße die Verkäuferin", as a villager talk's goals read), the setting and
the meanings in the base. An app of any version reads them as it always did. `scenarioBrief` keeps the tutor's brief in
English and adds `language`, `packs` and `child`. A tutor's scenario is voiced in the village's language (it went to
the Slovene voices before).

**The app** (`data/Talk.kt`, `ui/stage/Cast.kt`, `ui/talk/TalkModel.kt`): `Scenario` reads `who`, `language`,
`audience` and `packs`, and a text per language as the bridge would serve it (`Label`, `BaseLabel`). The talker:

- a scenario that names a villager of the cast ("Nonna Rosa", "Giulia", "Lorenzo", "Tommaso") is played by them: their
  sprite, their voice, and the talk grows the friendship (+♥ at the end, as a villager talk, without a memory) once they
  may be befriended (met; or a village that doesn't introduce its people);
- one not met yet, or one the name only suggests ("Natakar Marko", the waiter, for Vinar Marko), lends their look as a
  stranger, with no friendship promised (the intro used to promise one that never came);
- anyone else stands in by their title: Italian titles now too (Nonna, Nonno, Signora, Signor, Maestra, Maestro,
  Dottoressa, Dottor), with `who` as their role in the learner's pair; Italian men's names in -a (Andrea, Luca, Mattia,
  Nicola) sound male.

## How to use

- Teo: Home's "🗣️ Conversazione · Gespräch", a role-play, "Inizia · Starten". Or from a scene: after Nonna Rosa's eggs at the
  market, "keep talking" opens `al-mercato`'s intro.
- The tutor: `publish_scenario` as before; for Teo, the title, goals and `who` per language and the texts per base
  (module-spec.md). Left out, the language is the village's.
- QA: `LANI_DEV_DATA=$(cd companion/bridge && bun test/fixtures/make-learner.ts <dir> --target it --base de --name Teo)
  LANI_CULTURE=friuli LANI_CHILD=1 LANI_QA_SETUP_PAIR=it-de LANI_QA_LEARNER=Teo LANI_QA_TALK_SHOTS=<dir>
  companion/bin/qa-slot --steps friuli-talk` (another role-play: `LANI_QA_TALK=dal-pediatra`). With
  `LANI_SCENARIOS_DIR` at an empty directory it shoots the "before": the empty list.

## Tap counts

| Journey | Before | After |
|---|---|---|
| Teo, Home to talking in a situation (the market) | not possible: the list was empty ("no scenes yet"); only a villager's free talk (Home → village → villager → Talk → talk freely) | 3: "🗣️ Conversazione · Gespräch", "Al mercato · Auf dem Markt", "Inizia · Starten" |
| Teo, from Nonna Rosa's eggs at the market into a talk | none (no `talk` on Friuli dialogs) | 2 after the dialog: "keep talking", "Inizia" |
| Jan, Home to talking | 3 | 3 (unchanged) |

## Checks

- Bridge: `bunx tsc --noEmit -p .`; the smoke's scenarios section (the six Slovene files with their language, the nine
  Italian ones each for a child with every text in it, de, sl and en and a role that asks nothing personal; Jan's list
  without the Italian ones; another language and an unknown pack refused; the published file written in Slovene; the
  brief's language and no child line for Jan) and its Friuli section (Tim, a child learning Italian from Slovene: the
  nine served and nothing else, in it · sl; one by id; every pack theirs; a turn's brief in English with `language: it`,
  the packs and the child line; a tutor's role-play written in Italian and served in the pair; a Slovene one and a
  Slovene pack refused; a grown-up's not offered, 404 by id). `test/check-culture.ts friuli`: the scenes' `talk` links
  resolve to the Italian role-plays.
- Android: `TalkTest` (every Italian file parses in it · de: the title, goals and who in the pair, the setting and the
  meanings in German), `CastTest` (a named villager plays it, a suggested one is a stranger with their look, the Italian
  titles' sprites, who as the role, Andrea male), `TalkModelVillagerTest` (a scenario played by Nonna Rosa grows the
  friendship, keeps no memory).
- QA: `friuli-talk` above, on a synthetic Teo through `qa-slot` (slot 2): passed, 3 taps from Home to talking. Its
  shots (`st-*.png`): the list in it · de and on the tablet both ways, Al mercato's intro (the setting in German, the
  goals in it · de, Signora Gianna as "La fruttivendola · Die Obstverkäuferin"), the opener, the canned tutor's Italian
  answers, a hint that is the scenario's first phrase ("Buongiorno, signora!" · "Guten Morgen!"), the correction in
  German, the goals, the debrief in German, the end on the tablet. The "before" (no Italian role-play): the empty list,
  "Ancora nessuna scena · Noch keine Szenen".

## What's left

- **The native check** (below), most of all the Italian lines, the German a child reads, and the Slovene.
- Jan's six Slovene scenarios read their setting, goals and meanings in English only: a German or Italian learner of
  Slovene would read English there. Their texts could go per base as the Italian ones do.
- A child in a Slovene village (there is none) would still see a scene's "keep talking" into `v-gostilni` (the market's
  Vida), which they are not offered: the Talk screen would open on the list. The scene could leave that link out for a
  child.
- Voice: the nine role-plays' openers and phrases (68 lines, about 1,600 characters) and the three packs' words and
  examples (92 lines, about 1,700) join the Italian voice corpus: Teo's node voices them ahead with its engine, which may
  spend ElevenLabs credits (ask Jan before a voice build).

## Native check

Machine-written, every file flagged in its `review` ("it, de, sl: machine-written, not reviewed by a native speaker").
What most needs a native eye:

- **Italian (all nine, three packs).** Naturalness for a child of seven in Friuli, and the adults' register to a child
  (tu): "Vuoi assaggiarne una?" (a `ne` an A1 child only has to understand), "Che cosa ti do?" at the baker's,
  "Siediti qui. Dimmi, che cosa ti fa male?" (the paediatrician), "Che caldo, eh? … Cono o coppetta?", "Sono
  pieno/piena" after lunch (or "sazio"?), "Sei venuto/venuta! Oggi compio dieci anni!", "Un bacione!". Whether "Dal
  pediatra" fits as the title (the place), "La ricreazione" for the break, "Domenica a pranzo dalla nonna". The packs'
  "le orecchie" as the usual plural, "Ci sentiamo!".
- **German (Teo's base).** A seven-year-old's German: "Pausenbrot" (not the Austrian "Jause": is Teo's German from
  Germany or Austria?), "Waffel" for the cone (or "Eistüte", "Hörnchen"), "Ich hab dich lieb", "Die ist super lecker!",
  "Sonntagsessen bei Oma", "Herr Doktor".
- **Slovene (the third base).** Settled on fran.si: *sladoledarna* (SSKJ², "kavarna, okrepčevalnica, kjer prodajajo
  zlasti sladoled"; Pravopis "delati v ~i": https://fran.si/iskanje?View=1&Query=sladoledarna) and *kornet* as the
  ice-cream cone (eSSKJ sense 1, "posodica za sladoled v obliki obrnjenega stožca, navadno iz vaflja"; SSKJ² sense 2:
  https://fran.si/iskanje?View=1&Query=kornet). To check: "Se igrava z žogo?" (the dual for two children), "Greva na
  gugalnico?", "Adijo, se vidiva!", "Se igramo skrivalnice?", "lonček" for the ice-cream cup, "Fantek iz vasi",
  "Tukaj babica!" on the phone, "Velik poljub!", "Vošči Tommasu". Spoken forms are right where they stand (adijo).
