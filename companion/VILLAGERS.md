# Villagers: the people of Moja vas

The village has a cast: Babica Micka, Stari Janez, Pastir Luka, Kovač Tone and the others. They are
the same people everywhere: they give quests, stand in scenes, watch over training sessions, and
talk with Jan (in scripted lines, or freely, played by the tutor). **They remember.** When Jan helps
them, the friendship grows and they say so. "Hvala še enkrat za Belo!"

```
Village map         villager at their spot (a bubble when they need something) ── tap ─┐
Scroll / book       📖 Prebivalci · Villagers (the register) ─────────────────────────┤
                                                                                       ▼
                  Villager page: portrait, story, friendship ♥, memories, their quest,
                  "Pogovori se · Talk" → a role-play with the tutor playing them
Training stage      the quest's villager (or today's companion) reacts to each answer
Intro sheet         the villager asks, in Slovene: what, why, what it pays
```

## Parts and where they live

| Part | Code |
|---|---|
| Format and friendship (the contract) | `android/.../game/villagers/`: `Villager.kt` (format), `Bonds.kt` (levels, points, memories); `GameState.bonds`; `TownSpots` and `TownPlace.Spot` in `game/scene/TownMarkers.kt`; `Pose` and `PortraitPainter` in `game/scene/ScenePainter.kt` |
| Register, villager page, talking, friendship updates | `ui/villagers/` (`VillagersScreen`, `VillagerScreen`, `VillagerCard(vm, id, onDismiss)`: the compact card the map shows; `VillagerLogic`: lines by level, day and time of day, the register's order, quest and gift memories; `GiftScene` and `GiftTalk`: the gift moment), `app/VillagerController.kt` (`befriend(id, points, memory)`, `offer(good, id)` and `say(k)`: the gift moment, `give(good, id)`, `villager_arrived`), `ui/Portrait.kt` (a portrait composable, shared) |
| What friendship brings: gifts, goods, help | `game/Chest.kt` (tools, goods, forging, the feast, buying), `game/Help.kt` (🤝 and the moba), the culture pack's `chest.json` (tools, goods, thanks, likes: `Catalog.tools`, `goods`, `thanks`, `likes` read it; the prices and effects are `Catalog`'s), `ui/game/ChestSheet.kt` (the chest) |
| The village's life: projects, festivals, the day's surprise | `game/Projects.kt` (the frames in `Catalog.projectFrames`, the words in the culture pack's `projects.json`), `game/Calendar.kt` (`festivals.json`), `game/Surprises.kt` (`surprises.json`), `game/Tomorrow.kt`; `ui/game/ProjectsSheet.kt` |
| Newcomers' names, trades and plain lines; who a building brings | `game/villagers/Residents.kt`, the culture pack's `people.json` |
| Who a text may name: the people here and met | `game/villagers/Mentions.kt` (and where it holds back: `Happenings.on`, `Residents.openQuests`, `Quests.refill`, `Surprises.roll`, `Readings.corner`, `VillagerLine.at`, `Arrivals.meeting`), `bridge/src/mentions.ts` (publish_scene, publish_dialog_variant, get_village, a talk's opener) |
| Arrivals: meeting who joins, and nobody before it | `game/villagers/Arrivals.kt` (who waits to be met, the introduction's dialog, the migration of an older village), `ui/villagers/ArrivalScene.kt` (the introduction, over the village; the "Seznani se · Meet them" button), `app/VillagerController.kt` (`meet`, the tutor's arrivals), the culture pack's `arrivals.json`, `bridge/src/arrivals.ts`, `bridge/src/features/arrivals.ts` |
| Intro sheet and training stage | `ui/stage/` |
| Portraits, poses, interior scenes | `game/render/scene/` |
| The cast and its lines, interior scenes, serving | `companion/cultures/<culture>/villagers/*.json` (the cast of each culture pack; Jan's: `primorska`), `bridge/src/villagers.ts`, `bridge/src/features/villagers.ts` |
| Villagers on the map, spots, the landscape changing | `game/render/`, `ui/game/` |
| A day in the village: hours, work, the evening, night rituals, where each sleeps | `game/villagers/Routine.kt` (pure: `Routine.now`, `stints`, `plan`, `toBed`), `game/scene/Sleep.kt` (`bedrooms`, `sleepers`), `game/scene/Whereabouts.kt` (the day the map and the scenes share), `game/render/TownPeople.kt` (the map: where, the walks, the errands, the lanterns, `darkWindows`), `game/render/Walkways.kt` (the ways round the fire and the buildings); a villager file's `routine` and `sleeps` |

## Format: `lani.villager/v0`

One file per villager in `companion/cultures/<culture>/villagers/` (curated: the cast of a culture pack, see GAME.md
"Culture packs"; the bridge serves the learner's), or published by the tutor with `publish_villager`.

```json
{
  "schema": "lani.villager/v0",
  "id": "luka",
  "name": "Pastir Luka",
  "emoji": "🐑",
  "art": "shepherd",
  "voice": "male",
  "speaker": "young-man",
  "role": "Pastir · Shepherd",
  "home": ["spot:meadow", "tent"],
  "register": "ti",
  "skill": "wood",
  "personality": "A young shepherd from the Trnovo plateau. Cheerful, a little absent-minded, shouts across valleys, talks to his sheep by name. Short sentences, lots of 'ej!' and 'veš'.",
  "story": "Luka grazes the village's sheep on the meadow below the forest. Bela, the white one, keeps getting lost.",
  "likes": ["ovce · sheep", "burja · the bora wind", "planinske koče · mountain huts"],
  "lines": {
    "greet": [
      { "sl": "Dober dan!", "en": "Good day!", "level": 0, "when": ["morning", "afternoon"] },
      { "sl": "Dober večer!", "en": "Good evening!", "level": 0, "when": ["evening", "night"] },
      { "sl": "Ej, Jan! Kako si?", "en": "Hey, Jan! How are you?", "level": 1 }
    ],
    "thanks": [{ "sl": "Hvala ti, res si mi pomagal.", "en": "Thank you, you really helped me." }],
    "remember": [{ "sl": "Še vedno mislim na {memory}.", "en": "I still think of {memory}.", "level": 2 }],
    "cheer": [{ "sl": "Bravo!", "en": "Well done!" }, { "sl": "Točno tako!", "en": "Exactly!" }],
    "comfort": [{ "sl": "Nič hudega. Še enkrat!", "en": "Never mind. Once more!" }],
    "listen": [{ "sl": "Poslušaj …", "en": "Listen …" }],
    "idle": [{ "sl": "Bela je spet nekje v gozdu.", "en": "Bela is somewhere in the forest again." }],
    "bye": [{ "sl": "Adijo!", "en": "Bye!" }],
    "gift": {
      "liked": [{ "sl": "Ej, Jan! Kako si vedel? To imam res rad!", "en": "Hey, Jan! How did you know? I really like this!" }],
      "ordinary": [{ "sl": "Ej, hvala, Jan! Lepo od tebe.", "en": "Hey, thanks, Jan! That's nice of you." }],
      "rare": [{ "sl": "Ej, ej, ej! Tega pa še nisem imel! Hvala, brat!", "en": "Hey, hey, hey! I've never had this! Thanks, brother!" }]
    }
  }
}
```

A villager of a village in another language (a culture pack in Italian, the second learner's `friuli`) names every language:
its lines are `{"it": "Ciao! Hai fame?", "sl": "Živjo! Si {m:lačen|f:lačna}?", "en": "Hi! Are you hungry?", "level": 1}`, its
`role` `{"it": "Nonna", "sl": "Babica", "en": "Grandmother"}` and each like `{"it": "la gubana", "sl": "gubana",
"en": "gubana (the nut roll of Cividale)"}`. The old forms stay valid. The app says a line in the learner's target
language (Slovene when the line has no text in it) and shows its translation in their base language (English when
the line has none in it; in an English village none, rather than the English line again), so Jan's lines read as they
always did; a role or a like reads "target · base". Primorska's cast carries a translation for every base the app has
(`{"sl", "en", "de", "it"}`), Friuli's too (`{"it", "sl", "en", "de"}`). A translation is only read: what is said, and
voiced, is the village's language.

| Field | Rules |
|---|---|
| `id` | lowercase `a-z0-9-`, unique; scenes point to it (`people[].villager`), quests find it by `name` |
| `art` | a people sprite (see SCENES.md) |
| `voice` | `female` or `male`: their gender (Slovene agreement in the app) and the fallback voice |
| `speaker` | their voice in the voice cast (`companion/voice-cast.json`, see README "Voice"), of the same gender: `grandma` (Micka, Ančka), `grandpa` (Janez, France, Anton, Jože), `young-man` (Luka, Marko), `gruff` (Tone), `woman` (Vida), `teacher` (Mojca), `girl` (Zala), `boy` (Nejc, Tine); `young-woman`/`young-man` for people who move in, `girl`/`boy` for children born here. Their lines, their scene lines and their role-play are voiced in it; without it, by the narrator of their gender |
| `home` | building types (they're there once it's built) and `spot:<id>` (always there), best first; bubbles show at the first that exists, and it is where they work by day ("A day in the village"). At night they sleep in their household's room of a house, else where `sleeps` says, else in the first building of their home with a roof, in its scene's bed if it has one (Luka in the tent, Micka on the bench by the stove: SCENES.md, "Asleep at night") |
| `sleeps` | optional: where they sleep, when it isn't where they are by day: a roof, `tent`, `hut`, `house`, `smithy`, `school`, `market` or `beehive` (Tone `smithy`: his cot by the forge), or a room of a house by its art, `house:<art>` (Anton `house:cellar`, Jože `house:livingroom`: they lodge in the house dealt that room, in a bed of its scene; while no house has the room, in a house we don't see into; with no house, their home decides). Without it, their home decides as before. An older bridge leaves it out, an older app ignores it ("A day in the village", "Where they sleep") |
| `routine` | optional: when they get `up` and go to `bed` ("5:00", "21:30"; "0:30" is after midnight), and their night `rituals` (at most 4): each an `id`, `at` and `until` ("21:25", "21:45": at night, from 19:00 until 6:00, 10 to 60 minutes, over before they get up), `to` (a building type, `spot:<id>`, `fire` or `forest`), `doing` (`stand`, `tend`, `carry`, `watch`), a `title` in the village's language with its translations ("Luka pogleda ovce v staji") and `lantern` (on by default). Without it, their kind's hours ("A day in the village"). An older bridge leaves it out, an older app ignores it |
| `register` | `ti` or `vi`: how Jan addresses them (older people: vi); how they address Jan is part of their `personality` (older people often say ti to a younger person, and switch warmth with the friendship). In Italian `ti` is tu and `vi` is Lei; in the second learner's village (friuli) everyone says tu to the learner, a child, and `vi` is only how the learner addresses them. In German `ti` is du and `vi` is Sie; in the Carinthian village everyone says du to the learner. In English `ti` is the first name and `vi` "Mr" or "Mrs" and the surname ("The lakeland cast") |
| `personality`, `story`, `likes` | for the tutor who plays them and for the villager page; `personality` and `story` in English, `likes` as "Slovene · English" or per language (they double as vocabulary hints in a talk) |
| `lines` | each 2–6 lines (for any one time of day: a line with a `when` counts only at its times), in the village's language with translations (Slovene with English; Italian with Slovene and English; German with Slovene, English and Italian; English with Slovene, Italian and German), A1–A2; `level` is the friendship level from which a line can be said (0 stranger … 4 like family), so they get warmer. `remember` lines contain `{memory}` in every language: Slovene "Še vedno mislim na {memory}" (the memory in the accusative), Italian after a transitive verb, "Ricordo ancora {memory}" (the memory with its article), never after a preposition, German after "an", "Ich denke noch oft an {memory}" (the memory with its article, in the accusative). English is always there. The learner is `{learner}` (and its cases) and whatever agrees with them a gender pair, in every language: "{learner}! Si {m:videl|f:videla} Belo?", "Grazie, {m:caro|f:cara}!" (SCENES.md, "The learner in the content"). |
| `lines[].when` | the parts of the day a line is said in, when it belongs to one: `["morning", "afternoon"]` for "Dober dan" ("Buongiorno", "Guten Tag", "Good day"), `["morning"]` for "Dobro jutro", `["evening", "night"]` for "Dober večer" and "Lahko noč" ("Buonasera", "Buonanotte", "Guten Abend", "Gute Nacht", "Good evening", "Good night"), `["morning", "afternoon", "evening"]` for "tonight we celebrate". The parts are the village's (`TimeOfDay`: morning 5–11, afternoon 11–17, evening 17–22, night; dawn is the morning), by the phone's clock. A line without `when` fits any time (every line before it), and so do its translations: a greeting of any hour means one of any hour ("Grüß Gott!" · "Pozdravljen!" · "Hello!" · "Salve!", "Hello!" · "Hallo!"), never "Good day" at night (`TimeOfDayLinesTest`). Wherever a line is picked (the greeting on their card and in a talk, idle, a remember line, the stage's greet, cheer, comfort, listen and bye, an intro's request, a gift's thanks, the pedlar's call, the opener the bridge builds for a talk), only the lines that fit the time are candidates, then the level and the day's dice as before; with none that fits, a plain one for the time stands in ("Dober dan!" by day, "Dober večer!" from the evening; the stage's own lines; no idle line at all), never a wrong-time one. Every level-0 greeting may be bound to a time, but together they cover the day (`publish_villager` checks it), so a stranger is greeted in their words at any hour. `when` isn't a language: the line is said and voiced as before (its clip is keyed by the text). The bridge serves it to an app that says its time of day (`GET /villagers?time=evening`, a visit's people too); an older app, which would refuse the line, gets every line without it |
| `lines.gift` | what they say when Jan gives them a good ("Giving a good" below): `liked` (a good they like: delight), `ordinary` (any other: warm thanks), `rare` (a rare good: special joy), 1–6 lines each (the cast has 3, 2 and 1), in their register and character, said as they are (no `{…}` but the learner's), so each fits any good of its kind: "Kako si {m:vedel|f:vedela}, da imam to rada?". Optional: someone without them (a tutor's villager, a newcomer) thanks in plain words. A culture pack's own cast must have them, in its language (`validateCulture`) |

Spots (`TownSpots`): `meadow`, `pond`, `highseat` (the hunter's high seat), `path` (into the woods),
`woodpile`, `rocks`, `riverbank`, `road`, `kopa` (the charcoal pile deep in the woods, on a tall canvas only).

## The friuli cast

The second learner's village (`companion/cultures/friuli/villagers/`) has the same roles, homes, skills, arrival order and
speakers as Primorska's, in the Collio near Gorizia. Their lines are A1, short and kind, for a child; they say tu to
the learner, and the learner says Lei to four of them (`register: vi`). Machine-written: a native speaker should check them.

| Villager | Emoji | Primorska's | Register | Skill | Who |
|---|---|---|---|---|---|
| Nonna Rosa | 👵 | Babica Micka | tu | 🌾 | Warm grandmother, always cooking (frico, polenta, gubana on Sundays): "Mangia, mangia!". Giulia's grandmother, Nives's sister; the host |
| Pastore Davide | 🐑 | Pastir Luka | tu | 🪵 | Young shepherd below Monte Sabotino; Bianca, the white lamb, keeps getting lost |
| Giulia | 👧 | Zala | tu | 🪵 | The girl who asks "perché?"; lambs, cherries, cats, books |
| Nonno Bepi | 👴 | Stari Janez | tu | 📜 | Old storyteller under the big linden, slow and proverbial; the Isonzo, Monte Canin, his dog Moro |
| Mugnaio Franco | 🧑‍🌾 | Mlinar France | Lei | 🌾 | Practical miller and farmer, counts everything, grinds the cornmeal for polenta; Lorenzo's grandfather |
| Lorenzo | 👦 | Tine | tu | 🪨 | The smallest: horses, the tractor, counting |
| Zia Nives | 📻 | Teta Ančka | tu | 🪵 | The village gossip with the regional radio, a bit hard of hearing |
| Apicoltore Aldo | 🐝 | Čebelar Anton | tu | 🌾 | Quiet beekeeper, acacia and chestnut honey |
| Fabbro Bruno | ⚒️ | Kovač Tone | Lei | 🪨 | Gruff and kind smith, no patience for a missing article; orders from Gorizia and Nova Gorica |
| Vignaiolo Matteo | 🍇 | Vinar Marko | tu | 📜 | Easygoing winemaker of the Collio (ribolla gialla); his friend Marko makes wine in Brda |
| Ostessa Marisa | 🍷 | Gostilničarka Vida | Lei | 📜 | Runs the osteria: frico, guests from Austria and Slovenia; Tommaso's mother |
| Tommaso | 🧒 | Nejc | tu | 🌾 | The ringleader: football (Udinese), tag, the pond, ice cream |
| Maestra Elena | 👩‍🏫 | Učiteljica Mojca | Lei | 🪨 | Comes with the school; loves articles (il, lo, la …) and double consonants (pala, palla) |

The strangers at the road are the cramaro, a pedlar from the Carnia with his wooden frame, and a pilgrim on his way
to the sanctuary of Castelmonte above Cividale.

## The kaernten cast

The Carinthian village (`companion/cultures/kaernten/villagers/`) has the same roles, homes, skills, arrival order and
speakers as Primorska's, in the Gailtal near Villach. Their lines are A1–A2, in German with Slovene, English and Italian
(`{"de", "sl", "en", "it"}`); they say du to the learner, and the learner says Sie to four of them (`register: vi`).
Their remember lines put the memory after "an", in the accusative ("Ich denke noch oft an {memory}."). Machine-written:
a native speaker should check them.

| Villager | Emoji | Primorska's | Register | Skill | Who |
|---|---|---|---|---|---|
| Oma Resi | 👵 | Babica Micka | du | 🌾 | Warm grandmother, always cooking (Kasnudeln, Heidensterz, Reindling on Sundays): "Iss, iss!", "Setz dich, Schatzi". Lena's grandmother, Mitzi's sister; her mother spoke Slovene; the host |
| Hirte Florian | 🐑 | Pastir Luka | du | 🪵 | Young shepherd below the Dobratsch, yodels across the valley; Flocke, the white lamb, keeps getting lost; rides at the Kufenstechen |
| Lena | 👧 | Zala | du | 🪵 | The girl who asks "Warum?"; lambs, cherries, cats, books; learns Slovene at the bilingual school |
| Opa Sepp | 👴 | Stari Janez | du | 📜 | Old storyteller under the big linden, slow and proverbial; five times on the Großglockner, sings in the choir, his dog Bello |
| Müller Franz | 🧑‍🌾 | Mlinar France | Sie | 🌾 | Practical miller and farmer, counts everything, grinds the buckwheat for Heidensterz, builds the Harpfe; Jakob's grandfather |
| Jakob | 👦 | Tine | du | 🪨 | The smallest: Noriker horses, the tractor, counting |
| Tante Mitzi | 📻 | Teta Ančka | du | 🪵 | The village gossip with Radio Kärnten and the Kleine Zeitung, a bit hard of hearing; Resi's sister, back from Klagenfurt |
| Imker Stanko | 🐝 | Čebelar Anton | du | 🌾 | Quiet beekeeper, a Carinthian Slovene from the Rosental: the Carnica ("die Kärntner Biene"), painted hive panels; "Dober dan! Grüß Gott!" |
| Schmied Toni | ⚒️ | Kovač Tone | Sie | 🪨 | Gruff and kind smith, no patience for a wrong article or ending (dem, den); shoes the Noriker horses, orders from Villach and Klagenfurt |
| Mostbauer Stefan | 🍎 | Vinar Marko | du | 📜 | Easygoing young farmer with an orchard of old trees: Most, apple juice; meets Marko (Brda) and Matteo (Collio) at the Dreiländereck |
| Wirtin Kathi | 🍲 | Gostilničarka Vida | Sie | 📜 | Runs the Gasthaus on the square: Kasnudeln, the Brettljause, the Kirchtagssuppe; guests from Slovenia and Italy; Maxi's mother |
| Maxi | 🧒 | Nejc | du | 🌾 | The ringleader: ice hockey (the VSV), football on the square, the pond |
| Lehrerin Eva | 👩‍🏫 | Učiteljica Mojca | Sie | 🪨 | Comes with the school, which teaches in German and Slovene; loves der, die, das, capital nouns and the umlauts (schon, schön) |

The strangers at the road are the Kraxenträger, a pedlar from the Kanaltal (Tarvis) with his wares in a wooden frame on
his back, and a pilgrim on his way to the Luschariberg (Monte Lussari, Svete Višarje), the pilgrimage of three peoples.

## The lakeland cast

The village of a learner of English (`companion/cultures/lakeland/villagers/`) has the same roles, homes, skills,
arrival order and speakers, in a dale of the Lake District. Their lines are British English, A1–A2, short and kind,
with Slovene, Italian and German (`{"en", "sl", "it", "de"}`). English has one "you": `register: "vi"` means the learner
calls them by title and surname (Mr Dixon), `"ti"` by the first name; the translations of what the learner says to a vi
four use the polite form (sl vi, it Lei, de Sie), and everyone speaks to the learner familiarly. Machine-written: a
native speaker should check them.

| Villager | Emoji | Primorska's | Register | Skill | Who |
|---|---|---|---|---|---|
| Granny Maggie | 👵 | Babica Micka | first name | 🌾 | Warm grandmother, always baking (tatie pot, scones, sticky toffee pudding on Sundays): "Put the kettle on!", "love". Poppy's grandmother, Edna's sister; the host |
| Shepherd Jack | 🐑 | Pastir Luka | first name | 🪵 | Young Herdwick shepherd and fell runner; his sheepdog Meg, and Sooty, the black lamb, keeps getting lost |
| Poppy | 👧 | Zala | first name | 🪵 | The girl who asks "Why?"; lambs, blackberries, the cat, Peter Rabbit |
| Old Wilf | 👴 | Stari Janez | first name | 📜 | Old storyteller under the village oak, slow and proverbial; every fell of the Lakes climbed, the slate mine at Honister, a line of Wordsworth now and then; his dog Bess |
| Mr Tyson | 🧑‍🌾 | Mlinar France | Mr | 🌾 | Practical miller and farmer, counts everything (now and then yan, tyan, tethera), grinds the oats; Alfie's grandfather |
| Alfie | 👦 | Tine | first name | 🪨 | The smallest: tractors, fell ponies, counting |
| Auntie Edna | 📻 | Teta Ančka | first name | 🪵 | The village gossip with BBC Radio Cumbria and the weather forecast, a bit hard of hearing, knits tea cosies |
| Beekeeper Arthur | 🐝 | Čebelar Anton | first name | 🌾 | Quiet beekeeper, heather honey from the fells; the curlew and the skylark |
| Mr Dixon | ⚒️ | Kovač Tone | Mr | 🪨 | Gruff and kind smith, no patience for a missing -s or "I goed"; shoes the fell ponies; won the wrestling at Grasmere once |
| Brewer Sam | 🍺 | Vinar Marko | first name | 📜 | Easygoing young brewer by the beck, grows damsons; his friend Marko makes wine in Brda |
| Mrs Birkett | 🍽️ | Gostilničarka Vida | Mrs | 📜 | Runs the Packhorse Inn: tatie pot, walkers and climbers from everywhere; Harry's mother |
| Harry | 🧒 | Nejc | first name | 🌾 | The ringleader: Carlisle United, tag, the tarn, skimming stones |
| Mrs Hartley | 👩‍🏫 | Učiteljica Mojca | Mrs | 🪨 | Comes with the school; loves spelling, silent letters (knife, lamb) and the irregular plurals (sheep, mice, children) |

The strangers at the road are the packman from Kendal with his frame of wares, and a pilgrim on his way to St Herbert's
Island on Derwentwater.

## Who lives here

Every villager counted in the population is a named person (`GameState.residents`,
`game/villagers/Residents.kt`). The daily upkeep grows the population when there's food, good morale and
room (housing: tents, huts, houses), and shrinks it after days of low morale; after each day the residents
are matched to it:

- **Arrivals.** The cast moves in first, in their `order`, once their age (`since`) is reached and their home
  exists: a spot always does, a building has to be built (the teacher waits for the school). A workplace brings its
  person ahead of their order ("A building brings its person" below: the beehive Čebelar Anton). When the cast is
  all here, or waiting for buildings, **newcomers** move in with a trade that fits the village (woodcutter,
  fisher, farmer with a field, weaver with a house …) and a family name of the region (the culture pack's
  `people.json`: its first names, family names, trades and plain lines; Primorska's in the Primorska village); often they're the partner
  of someone living alone, so families form. Families with two grown-ups have **children** (at most one per
  21 days), who grow from baby (to 45 days) to child (to 240) to youth (to 420 days) and then adult.
- **Extras.** A culture pack may have smaller people besides the cast (`"extra": true`, plan 2 "Many people per
  culture"): not one of the roles the game needs, fewer lines (2–3 of each kind), no quests of their own. They move in as
  newcomers do, once their age is reached and their home exists, taking turns with the names the village makes up (an
  extra when no more of them live here than newcomers who moved in); like the cast they may come by for a day before, and
  their scenes' happenings wait for them. Primorska's first: 🦌 Lovec Jože, a hunter of the hunting club (lovska
  družina), from the hamlet on, at the high seat (`spot:highseat`, sprite `hunter`, speaker `grandpa`): he watches the game
  rather than hunts it, counts the deer at dawn and dusk, feeds them in winter, reads tracks; "Lovski blagor!". His words
  are the pack `na-prezi` (Na preži · On the high seat); his happenings are in the forest, on his high seat at dawn
  (`v-gozdu/preza`) and counting the deer at dusk (`v-gozdu/mrak`).
- **Departures.** Newcomers leave first, never the cast while others can go.
- **Waiting for room.** `Residents.outlook` says who comes next and what they're waiting for: room (the
  houses are full: build) or a building. The village shows it ("Furlanovi pričakujejo otroka · The Furlans are
  expecting, they need room").
- At most 30 residents.

People born here or who moved in are villagers too (`Residents.villagerOf`: a name, a sprite for their age
(`woman`, `man`, `baby`, the children), plain lines for their age, friendship like everyone). When someone
new arrives, the app tells the tutor (message kind `villager_arrived`), who may give them a personality and
their own lines with `publish_villager` under the same id; from then on they're cast. Whoever joins, Jan meets
them first ("Arrivals" below).

### A building brings its person

A village that builds a beehive has a beekeeper: the building brings the one of the cast whose workplace it is
(`Residents.broughtBy`, `brings`), and they move in as soon as it stands (the app settles the residents right after
building), ahead of their `order`, into a free bed. Who a building brings comes from the villager files, no
field of its own: someone of the cast (not an extra) who comes in a later age (a `since`), whose `home` starts with a
building that isn't a dwelling (a tent, a hut, a house: people live in those, and the housing counts them); of those who
work there, the first by `order`. The founders (no `since`) come in their order from the first days, so the field brings
nobody, nor does the linden. Every culture pack's cast has the same homes:

| Building | Primorska | Friuli | Kärnten | Lakeland |
|---|---|---|---|---|
| beehive | Čebelar Anton | Apicoltore Aldo | Imker Stanko | Beekeeper Arthur |
| smithy | Kovač Tone | Fabbro Bruno | Schmied Toni | Mr Dixon |
| market | Vinar Marko | Vignaiolo Matteo | Mostbauer Stefan | Brewer Sam |
| school | Učiteljica Mojca | Maestra Elena | Lehrerin Eva | Mrs Hartley |

The innkeeper and her son (Vida and Nejc), who live at the market too, come in their order after the winemaker.

**A bed first.** Whom a building brings needs somewhere to live: a free bed, the population (`GameState.villagers`, and the
residents) below the beds (`Attributes.populationCap`: the camp's 2 and the dwellings', a tent 2, a hut 3, a house 4, more
with their level). So while every bed is taken the workplace can't be built (`GameEngine.homeless`): its build card says
whom it's for and why, "🛏️ Čebelar Anton potrebuje prostor · Anton needs somewhere to live (5/5)" (in the village's
language by their name, in the learner's base by their own), no moba, and offers the way on in one tap
(`BuildOption.room`, `GameEngine.roomWay`, `roomLabel`): "🏠 Postavi hišo · Build a house ›", the dwelling with the most beds
that can go up now (on a plot to spare: the workplace needs one too), else one standing to upgrade ("⬆️ Nadgradi kočo ·
Upgrade the hut ›"); one the village can pay for, else the first, with what to gather for it ("🏠 Za hišo naberi še 🪵 · For a
house, gather more 🪵 ›", which starts that gathering). The sheet stays open, and the workplace can go up next. Where no room
can be made now (no plot to spare, every dwelling at the top level the age allows) the building isn't held back, never a
wall: it goes up, and its person waits for a bed, first in the outlook ("🐝 Čebelar Anton: prostor · room").

**Within the beds.** They move in as soon as a bed is free, ahead of the upkeep's growth: the village grows by them at once
(`GameState.villagers` +1 with each, never past the beds), so the residents stay the population (they eat, and the upkeep
grows the village again only while there's room). A village where the building stands without its person gets them at the
next settling if a bed is free (Jan's: 13 beds for 5, the tent at the pond, the hut at level 2 and the house), else once
one is. They stay while anyone else can go: in hard times
a newcomer leaves first, then another of the cast (with the building standing, they'd come straight back). Their age still
counts (a pack or the tutor may bring someone later than their workplace: no bed is asked for them before it), and the
village's limit (30). Without the cast (an older bridge) nobody is brought and no bed is asked for.

**Their arrival** is an arrival like any other ("Arrivals" below): the chronicle's "Čebelar Anton se je preselil v vas", the
🧳 at the beehive, and nobody names them before Jan has met them ("Who a text may name"). Jan's beehive, built before
Anton's turn, gets him at the next settling (the app's start, a sync), his introduction waiting for Jan; Anton's own tells
of it ("Prišel sem sam. No, s čebelami.").

## Arrivals: meeting who joins

When someone joins the village (one of the cast, a newcomer, a baby born here, someone who moved from a friend's town),
an **arrival** waits for Jan: a bubble at their home on the map (🧳 moving in, 👶 a birth), the first thing in the
scroll's "Danes v vasi · Today" ("👋 Novi v vasi · New in the village", with 📍 to fly there), under "🏡 V vasi · In the
village" on Home's "Danes · Today" card (`HomeLogic.calls`: a tap opens the introduction over Home), and on their card
and page "👋 Seznani se · Meet them" instead of a talk. A tap plays a short **introduction** over the village (`ArrivalScene`): the people of it on
the newcomer's place (the newcomer, and a relative who introduces them: Micka her granddaughter Zala, the parents their
baby), and the dialog as a scene's is played (`DialogPanel`: their lines typed in and voiced in their voices, a word's
card on a tap, the choices shuffled, a reaction to a wrong one, the 🎤). At its end Jan has **met** them: the friendship
starts (+5, +3 with mistakes: level 0), they keep the day as their first memory ("dan, ko sem prišla živet k babici",
`kind: arrival`), and "🗣️ Keep talking" goes on to a talk with them (not with a baby).

**Nobody before the first meeting.** Someone who lives here and hasn't been met hosts no run (the reviews, a request's, a
pack's, a gathering's), gives no request (theirs wait: `Residents.openQuests`), leads no project step or feast, is no
day's surprise, has no happening and stands (or sleeps) in no scene: `Residents.present` is who is here *and met*
(`Residents.living`: everyone). Where they'd be needed the arrival comes first: a project whose leader waits to be met says
"Najprej se seznani z vodjo · Meet its leader first" with the button; a gift for them opens their introduction instead.
Only the introduction makes someone met (`Bonds.add(meet = true)`): a feast's heart or a greeting before it counts, but
they're still to meet. Today's visitor (not a resident) is met only once they move in. Nor does anyone talk of them
before: a dialog, a request, a letter, a line that names them waits ("Who a text may name").

**Who comes first.** Arrivals wait in the order people came (`Arrivals.pending`). One whose introducer lives here and isn't
met waits for the introducer's own (Zala for Micka); a baby for its parents' (`Arrivals.ready`); "Meet them" on a card
opens the one to play first (`Arrivals.next`). An introducer who moved away: the newcomer introduces themself (the
template). A baby whose parents both left is shown by the grown-up Jan has known longest.

**Late.** Someone who came two days ago or more (`Arrivals.LATE_DAYS`) is met late: the dialog opens with its `late` line
("Živjo! Nisva se še spoznala, kajne?", "Jan! Saj še ne poznaš moje vnukinje?"), the rest as written.

**An older village** (from before arrivals) is migrated once when its culture pack has arrivals (`Arrivals.migrate`,
`GameState.migrated` "arrivals"): whoever shares a memory with Jan or has 5 points or more stays met (they talked, got a
gift, a request done); everyone else (a greeting, a training on the stage) is a stranger again and gets a late
introduction. Their points stay. A culture pack without `arrivals.json` (or an app without it) introduces nobody and
filters nobody, as before.

**The level.** The learner's level in the village's language picks the variant: the highest the arrival has up to it, else
its easiest (A1 → A1, A2 → A2, B2 → B1 or A2).

**Where the introductions come from**, first that fits: the tutor's for that person (`publish_arrival`, below; it plays
when everyone it names lives here), the culture pack's for one of its cast (when their introducer lives here), else the
pack's template for who they are (`newcomer`, `birth`, `moved`: someone from a friend's town, else `newcomer`; one of the
cast without their own takes `newcomer` with their name and role).

### Format: `lani.arrivals/v0` (a culture pack's `arrivals.json`)

```json
{
  "schema": "lani.arrivals/v0",
  "language": "sl",
  "review": "Machine-written (Claude), not reviewed by a native speaker.",
  "cast": {
    "zala": {
      "by": "micka",
      "memory": { "sl": "dan, ko sem prišla živet k babici", "en": "the day I came to live with Grandma", "de": "…", "it": "…" },
      "levels": {
        "A1": {
          "late": { "who": "micka", "sl": "Jan! Saj še ne poznaš moje vnukinje, kajne?", "en": "Jan! You don't know my granddaughter yet, do you?", "de": "…", "it": "…" },
          "lines": [
            { "who": "micka", "sl": "Jan, poglej! To je moja vnukinja Zala.", "en": "Jan, look! This is my granddaughter Zala.", "de": "…", "it": "…" },
            { "who": "zala", "sl": "Živjo! Jaz sem Zala. Stara sem osem let.", "en": "Hi! I'm Zala. I'm eight years old.", "de": "…", "it": "…" },
            { "choices": [
              { "sl": "Dobrodošla v vasi!", "en": "Welcome to the village!", "de": "…", "it": "…", "ok": true,
                "reply": { "sl": "Hvala! Mi pokažeš ovce?", "en": "Thanks! Will you show me the sheep?", "de": "…", "it": "…" } },
              { "sl": "Dobrodošel v vasi!", "en": "Welcome to the village! (as to a boy)", "de": "…", "it": "Benvenuto in paese!",
                "why": { "en": "Zala is a girl: dobrodošla, with -a (dobrodošel is for a boy).", "de": "…", "it": "…" },
                "reply": { "sl": "Dobrodošel? Jaz nisem fant!", "en": "Dobrodošel? I'm not a boy!", "de": "…", "it": "…" } }
            ] },
            { "who": "micka", "sl": "Tako. Zdaj se poznata.", "en": "There. Now you know each other.", "de": "…", "it": "…" }
          ]
        },
        "A2": { "late": { "…": "…" }, "lines": [ "…" ] }
      }
    }
  },
  "templates": {
    "newcomer": { "memory": { "sl": "dan, ko sem se {g, select, f {preselila} other {preselil}} v vas", "…": "…" }, "levels": { "…": "…" } },
    "birth": { "…": "…" },
    "moved": { "…": "…" }
  }
}
```

| Field | Rules |
|---|---|
| `language` | the pack's; every text (a line, `late`, a choice, a reply, `memory`) in the app's four languages, the pack's first (Primorska `sl` with `en`, `de`, `it`; Friuli `it` with `sl`, `en`, `de`; Kärnten `de` with `sl`, `en`, `it`; Lakeland `en` with `sl`, `it`, `de`); what is said is the village's language, the rest translates it (a wrong choice's translation marks the slip it can't show: "(as to a boy)", "(vi)") |
| `cast` | by villager id: one for each of the pack's cast (an extra may have one: Lovec Jože), `by` optionally someone of the cast with a smaller `order` who introduces them, `memory` in their words, first person, fitting their remember lines ("Še vedno mislim na {memory}", "Ricordo ancora {memory}", "Ich denke noch oft an {memory}"), `levels`: A1 and A2 at least, B1 for some |
| `levels.<level>` | `lines`: 3–12, a scene dialog's (SCENES.md "Format": someone's line `{who, <texts>}`, or the learner's turn `{choices}`), the first someone's; `late`: the line said instead of the first when Jan meets them late. Every line has its `who`: the newcomer or their introducer (a template's: `self`; a birth's `parent`, `parent2`). No `sky`, no `fx`: an arrival has no scene around it. A1: 3–5 short lines (name, age or trade, where from, what they do); A2: why they came and with whom; B1: a small story |
| turns | 1–2 (B1 up to 3), 2–3 choices each, at least one right and one wrong, where the grammar is natural: the greeting's register (Živjo / Dober dan by ti/vi), the welcome agreeing with who it is (Dobrodošel, Dobrodošla, and Dobrodošli to someone Jan says vi to), "Čestitam!" for a baby, "Me veseli."; every wrong choice has a `why` (per language: every language but the pack's own, the learner reads it in their base) and a `reply`, the person's reaction to what was said, never the right form (SCENES.md, "Reactions and more than one right answer"); a reply is said by whoever spoke last before the turn |
| `templates` | `newcomer` and `birth` (A1 and A2), `moved` optional; ICU placeholders as the pack's other texts: a newcomer's `{name}`, `{first}`, `{family}`, `{role}` (their trade, lower case mid-sentence; a German noun keeps its capital), `{g}` (f or m: `{g, select, f {Dobrodošla} other {Dobrodošel}}`), a mover's `{from}` too (their town, as it is: "v kraju {from}"); a baby's `{first}`, `{family}`, `{g}`, `{parent}`, `{parent2}` (the parents' first names), `{pg}`, `{p2g}` (their genders). Names are never declined ("Ime ji je {first}."). The learner is `{learner}` and agrees `{m:…|f:…}` as in any text; where both the learner's gender and theirs decide a form, a pair holds the select: the two of you are women only when both are, `Nisva se še {m:spoznala|f:{g, select, f {spoznali} other {spoznala}}}` |

The bridge checks all of it (`arrivals.ts`, `validateCulture`: each of the cast has one, the introducer came first, the
four languages, the late lines, a reaction to every wrong choice, the placeholders); the app bundles the file with the
pack and reads a dialog in the learner's pair when it's played (`Arrivals.meeting`, as a scene's `inPair`;
`ArrivalsContentTest` plays each of them at every level, on time and late, in every base).

| Pack | Introduced by someone | B1 too | Templates |
|---|---|---|---|
| `primorska` (13 and Lovec Jože) | Micka her granddaughter Zala and her sister Ančka, France his grandson Tine, Vida her son Nejc, Janez his old friend Jože | Micka, Luka, Zala, Janez, Anton, Mojca; the newcomer | newcomer, birth, moved |
| `friuli` | Rosa her granddaughter Giulia and her sister Nives, Franco his grandson Lorenzo, Marisa her son Tommaso | all 13 | newcomer, birth, moved |
| `kaernten` | Resi her granddaughter Lena and her sister Mitzi, Franz his grandson Jakob, Kathi her son Maxi | all 13 | newcomer, birth, moved |
| `lakeland` | Maggie her granddaughter Poppy and her sister Edna, Mr Tyson his grandson Alfie, Mrs Birkett her son Harry | all but Alfie and Harry | newcomer, birth, moved |

Everyone else introduces themself; the host (Micka, Rosa, Resi, Maggie), the first to come, welcomes the learner. The
turns are the greeting's register (Živjo or Dober dan; tu or Lei; du or Sie; a first name or Mr and Mrs), the welcome
that agrees (Dobrodošla, Benvenuta), a noun's gender or article, "Me veseli", "Čestitam!" and the like, the Slovene dual
(dve knjigi, sva se spoznala); machine-written (Claude), not reviewed by a native speaker.

### The tutor's: `lani.arrival/v0`

The cast's are the pack's; a newcomer's and a baby's play the template until the tutor writes theirs. `villager_arrived`
carries `data.arrival`: the learner's level, their weak patterns (mistakes-db, an example each) and a baby's parents
(their ids and names). The tutor publishes one introduction with `publish_arrival` (`<data>/app/arrivals/<id>.json`; the
app gets them with `GET /arrivals` and when `arrival_published` comes), one arrival as above with `schema`, `id` (the
resident's) and `language` (the village's): its texts in that language and English (and the learner's base), at the
learner's level (one level is enough), a `late` line optional, the speakers the newcomer and anyone who lives here (a
baby's parents), no placeholders. `get_arrival` shows what the app would play for someone now (the tutor's, the pack's, or
the template with who they are); `remove_arrival` takes the tutor's back. A child born here keeps their introduction;
they grow into their plain lines (and, some day, lines of their own) as they grow.

## Who a text may name

The learner themselves (`{learner}`, SCENES.md "The learner in the content") is no one to meet. The village talks
only of people Jan knows: whatever the app says names only people who are in the village and whom Jan
has met (`Residents.present`: the residents and today's visitor, met). Zala's "Jan! Anton toči med!" at the bee house, or
Janez's "Hitro, pokliči Antona!", waits until Anton lives here and Jan has met him; until then another happening takes its
place. So does:

| What | Held back where | Instead |
|---|---|---|
| a happening: its dialog (lines, choices, replies), title and memory | `Happenings.on` | another happening |
| a happening's variant (one of its `dialogs`, with its own memory: SCENES.md "Variants") | `Happenings.variant` | another variant of it, else another happening |
| a request: its title and story (the culture's templates and the tutor's) | `Quests.refill`, `Residents.openQuests` | another request |
| a letter of the day's surprise | `Surprises.roll` | another letter or surprise |
| a reading of the reading corner, and who reads it aloud (`by`) | `Readings.corner` | the others |
| a villager's own line (a greeting, idle talk, a thanks, a gift's: Zala's "Nejc ne zna brati.") | `VillagerLine.at` (every line picked goes through it) | their other lines, or a plain one |
| a level of an introduction (Mojca's B1 "od Tineta do Zale") | `Arrivals.meeting` | an easier level that doesn't; else as it is (an introduction never waits for a name) |
| a talk's opener (the bridge's) | `villagerTalk` | another greeting, or a plain one |

**Who a text names** (`Mentions`, `Names`; the bridge's `mentions.ts` finds it the same way, and the smoke checks both on the
same sentences): a villager of the cast (the extras too; the people born here or who moved in, where the list has them) by
their own name, as the village's language says it in a sentence, with or without their title. The cast's own name is the
last word of theirs ("Čebelar Anton" → Anton, "Mr Tyson" → Tyson, "Nonna Rosa" → Rosa); someone who moved in goes by their
first name ("Ana Furlan" → Ana). Slovene declines it: Anton, Antona, Antonu, z Antonom, Antonov med; Micka, pri Micki, Mickina
kuhinja; Tone, Toneta; Marko, Marka; Nejc, Nejca, Nejčev; Mojca, Mojčin (the forms `Mentions.slovene` makes); German adds a
genitive -s (Resis Küche); Italian and English say it as it is (Granny Maggie's). Nobody else counts:

- the learner (Jan, Jana, Janu: no one of the cast is called that, and Janez isn't Jan);
- a word that is a name only with its capital (luka, a harbour; vidi, sees; rosa, pink; bruno, brown): names start with one;
- someone else with the same first name and a surname or a title of their own, inside a sentence: France Prešeren, Anton
  Janša, King Arthur, Lepa Vida (not in German, which capitalises every noun: "Hat Florian Hunger?" is Florian).

A text says it in the village's language (a scene's `language`, the pack's); translations aren't read. The storyteller's
legends name who they name: history, not the village. The strangers (the pedlar, the pilgrim) aren't anyone's to name.

**What can't wait names only whom it always may**, and `MentionsContentTest` checks it in all four packs: a project's lines its
leader (its helpers needn't live here: the lines say "the neighbours", "die Burschen", "the children"), and so do its steps'
dialogs (`project-steps/`: a helper who is away has their lines said by someone else who is, SCENES.md "Project steps"), an event its
defender, the shepherd's lamb the shepherd, a letter its addressee, the tent's move its shepherd; a festival (led by
whichever of its leaders lives here), the pedlar, the pilgrim, the riddles, the newcomers' plain lines and the chronicle
nobody. It scans every scene's happenings, the requests, the introductions, the villagers' lines, the readings and the
word packs, and writes whom each names (`build/reports/mentions/mentions.txt`); a new kind of file in a pack fails it until
it says how its names are held back. In Primorska 25 of the 83 happenings name someone besides their own person (the
bee house's swarm and honey Anton, the smithy's people each other, Mojca's dual Tine and Zala …); the requests name their
giver (in Kärnten one names Oma Resi besides); Zala's, Nejc's, Tine's, France's, Vida's and Ančka's lines name someone.

**The tutor** sees who may be named in get_village (`people.known`: here and met; `people.to_meet`: living here, not met
yet). publish_scene publishes a scene all the same, and says which happening waits and for whom ("⚠ Waits in the app until
everyone it names is in the village and met: happening "med" names Čebelar Anton (anton)").

## Friendship

`GameState.bonds[villagerId]`: points, memories (newest 20), the day they met and last met. Levels at
0, 10, 30, 70, 150 points: Tujec · Stranger, Znanec · Acquaintance, Prijatelj · Friend, Dober prijatelj ·
Good friend, Kot družina · Like family.

| Jan … | Points | Memory | 🤝 |
|---|---|---|---|
| meets them: their introduction ("Arrivals"), which starts the friendship | +5 (+3 with mistakes) | the day they came, in their words ("dan, ko sem prišla živet k babici", `kind: arrival`) | – |
| finishes a villager's quest | +10 | the quest, in their words ("ko si mi pomagal najti Belo") | 3, and their thank-you good |
| finishes a scene dialog with them | +5 (+3 with mistakes) | the happening's `memory`, if it has one | 1 (the happening) |
| talks with them (role-play with the tutor) | +6 | what the tutor writes in the debrief's `data.memory` | 2 |
| gives them a good (once a day; "Giving a good") | +5 a good they like, +10 a rare one, +2 any other | the good, "potico od tebe" (`kind: gift`) | – |
| does a step of the project they lead | +5 | none | – |
| finishes a project they lead or helped with (they live here) | +5 | the project, "naš mlaj" (`kind: project`) | – |
| celebrates a festival they lead | +5 (in all) | none | – |
| celebrates a festival (everyone who lives here) | +1 | none | – |
| helps with the day's surprise about them (Luka's lamb, a child's riddle; a letter: +3) | +5 | none | 1 |
| holds a village feast (they live here) | +1, +1 per treat served | none | – |
| trains with them on the stage | +1 per session, at most 3 a day | none | – |

🤝 (help) pays for a moba: the neighbours help build (GAME.md, "Helping people").

### What friendship brings

- **Gifts.** At level 2 a villager gives Jan a tool of their trade, at level 4 a better one (Luka an axe, France a
  sickle, Micka her recipe, the children keepsakes …); each tool changes a rule of the village. People who moved
  in or were born here give small goods (apples, eggs, blueberries, wool socks). The gift shows on the result
  card of what reached the level, in the chronicle and as a notice ("Luka ti podari sekiro 🪓 · Luka gives you an
  axe: +10 % 🪵"); friends from before the chest existed give theirs the next time they're in the village. The
  full tables are in GAME.md, "The chest". A gift is a surprise (Jan): no screen announces it before it comes (not
  the chest, not their card or page, not "Jutri · Tomorrow"); the friendship's ♥ and its next level show, not what
  the level brings.
- **Goods.** After a request they thank with a good of their own (Micka potica, France bread, Anton honey, Luka
  Tolminc cheese, Marko rebula, Tone a horseshoe, Ančka embroidery, Janez linden tea, Mojca a notebook, Vida jota),
  now and then another of theirs (Micka her bread or socks she knitted), and around a festival its treat (Micka krofi
  at pust, pisanice at Easter, medenjaki at St Nicholas, poprtnik at Christmas); GAME.md, "Goods", has them all.
  Each villager likes a few goods (never their own): given to them, it's +5 ♥; anything else is +2 ♥ and warm thanks.
- **The ages.** Zaselek needs 1 friend (level 2 or more) who lives here, Vas 3, Trg 5, Mesto 8.

Everyone likes the rare goods (from the festivals and the pedlar; no wine for the children), and a rare good counts
twice. Gifts, goods and likes are the culture pack's (`chest.json`, by villager id, not in the villager files); what
they're worth and what a tool does are the game's (`Catalog.GOOD_PRICES`, `Catalog.TOOL_EFFECTS`). A villager the
pack doesn't know (a newcomer the tutor gave a personality with
`publish_villager`) gets the defaults: small goods at levels 2 and 4, apples as thanks, and likes potica, bread
and apples. Their `likes` in the file are topics to talk about, not goods.

The page (and in short the card on the map) shows, for someone who lives here: the tool of theirs in the chest and
the goods they'd love, with a "🎁 Podari · Give" button for each one in the chest, and below it the rest of the chest ("Ali kaj drugega · Or something else", +2 ♥; no wine for a child). What they'll give at the next level isn't
shown: it comes as a surprise.

## Giving a good

A tap on "🎁 Podari · Give" (in the chest, beside who likes a good, or on a villager's page) opens a small scene over
it (`GiftScene`, the pure parts in `GiftTalk`): their portrait, waving, and the good, big, its name to tap for its word
card and to hear.

1. **Jan hands it over.** "Kaj rečeš, ko podariš? · What do you say as you give it?": three things to say, each with 🔊,
   a word's card on a long press, and the 🎤 of a scene's dialog when something can listen (the words said count as
   tapped). Two are right in their register, the day picks which: "Izvoli, to je zate.", "Nekaj imam zate.", "Upam, da
   ti bo všeč." (vi: "Izvolite, to je za vas.", "Nekaj imam za vas.", "Upam, da vam bo všeč."); the third is one of them
   in the other register, which shakes, is crossed out and says why ("You say ti to Luka: “Izvolite, to je za vas.” is
   the polite form. Say “Izvoli, to je zate.”"), at no cost. In an Italian village: "Tieni, è per te." (tu), "Tenga, è
   per Lei." (Lei), explained in the learner's base.
2. **The good goes over, and they answer** in their voice, with a line of their `lines.gift` by what it is to them: a
   good they like (`Chest.favourite`: the pack's likes, the defaults for someone it doesn't know) delights them, a rare
   one is a special joy (both: a cheer and hearts rising), anything else brings warm thanks (a smile). Their words can be
   tapped (word cards) and heard again.
3. **The friendship**: the friend chip (the hearts, a new level, what they'll remember: "potico od tebe"), and a gift of
   theirs that the level brought, to unwrap (the gift reveal). The chronicle says it as before.

Closed before the right words, nothing is given. Someone who got something today already gets no scene: the button is
greyed, and the chest says so kindly ("Danes si že nekaj podaril: jutri spet 🙂 · You gave them something today: again
tomorrow 🙂"). Wine isn't for the children (`GoodSpec.adult`).

## The village's life: projects, festivals, surprises

The villagers carry the long ages (GAME.md, "Village projects", "The calendar", "The day's surprise"):

- **Projects.** Each village project is led by one of the cast: Luka the maypole and the fire station, France the
  bridge and the toplar, Janez the bocce court, Ančka the wayside chapel, Marko the vineyard terraces, Anton the bee
  meadow, Mojca the playground and the town clock, Tone the fountain, Vida the lookout tower. A project waits for its
  leader to live here (or visit today); each step is a short scene with the leader and the helpers who are here (one who
  isn't has their lines said by another helper, else by someone of the village of their age, else by the leader:
  SCENES.md, "Project steps"), and its helpers (three each, in the culture pack's `projects.json`) grow closer when it's
  finished. The step lines and the steps' dialogs name only the leader and groups (the young men, the neighbours, the
  children), so they fit whoever lives here.
- **Festivals.** Each festival has a few people who lead it, the first of them who lives here: Marko the grape
  harvest and St Martin's Day, Micka Easter and Christmas, the children carnival, Luka May Day, Anton World Bee Day,
  Janez midsummer night, St Nicholas and Prešeren Day, Vida the New Year. They ask Jan to celebrate in their words
  (the festival's `ask`). The festival's words are its word pack (`companion/packs/praznik-*.json`), and its first
  leader is the pack's giver (Vida, Janez, Nejc for carnival, Micka, Luka, Anton, Marko), on the stage when Jan learns
  them early from the packs screen.
- **The day's surprise.** Luka looks for his lamb, a child asks a riddle, the postman brings a letter for Marko,
  Micka, Tone or Vida (their names in the right case: "pismo za Micko", "Preberi ji ga!"), only while they live here.
  The pedlar and the pilgrim are strangers from the road (below).

## The strangers from the road

Two people of the day's surprise aren't the village's: they come by, and go on. They are drawn and staged like the
villagers, but they never live here, have no friendship and give no gifts (`Surprises.strangers`, from the culture
pack's `surprises.json`, not from its cast). Both say vi to Jan.

| Stranger | Sprite | Looks | While | Lines |
|---|---|---|---|---|
| 🎒 Krošnjar · The pedlar from Ribnica | `pedlar` | white shirt, red neckerchief, black felt hat, a stick; on his back the krošnja, a wooden frame loaded higher than his hat with suha roba (a sieve, wooden spoons, a ladle) | all his day | "Dober dan! Suha roba iz Ribnice!" ("Dober večer! …" from the evening), "Žlice, sita, kuhalnice! Kupite, kupite!" |
| 🥾 Romar · A pilgrim | `pilgrim` | a beard, a brown cape, a felt hat with a scallop shell, a tall staff with a gourd, a shoulder bag, dusty boots | until Jan has shown him the way | "Iščem pot na Sveto Goro. Mi lahko pomagate?", "Tako, zdaj razumem!", "Bog povrni! Srečno!" |

On the map the stranger stands and walks at the road (`spot:road`), day and night, also during a festival, and the
surprise's bubble (its emoji) follows him; a tap on him opens what the bubble opens (the pedlar's stall in the chest,
the pilgrim's run). The pilgrim is on the stage for his run; the pedlar's portrait heads his stall.

## At the charcoal pile: the burner

Deep in the woods below the village a charcoal pile smoulders (`spot:kopa`, SCENES.md "Bubbles in the village"), and some
evenings its burner sits by it: the reason it glows. He is a person of the culture, not of the village: he lives by his pile
in the woods while it burns, a week or two, and comes and goes with it. So he is no resident and no extra: he takes no
room, is never introduced ("Arrivals"), has no friendship, no card, no requests and no gifts; his talk introduces him
("Dober večer! Jaz sem Miha, oglar."). The pedlar and the pilgrim are strangers of the road the same way.

He is the spot's `keeper` in the culture pack's `world.json` (`spots.kopa.keeper`, the game's `Keepers`, pure): staged like
a villager (`id`, `name`, `emoji`, `art`: the `burner` sprite, `voice`, `speaker`, `role`), there in the parts of the day he
comes (`when`: the evening and the night) on the days the day's dice give him (`chance`), sitting left of his pile and
facing it. While his talk isn't done today his bubble (`title`: "Oglar Miha pazi na kopo · Miha the charcoal burner
watches the pile") sits over him; a tap on him or on it plays his talk over the village (`KeeperScene`: his portrait on
the woods at the hour it is, and the dialog as a scene's is played: his lines typed in and voiced in his speaker's voice, a
word's card on a tap, the choices shuffled, his reaction to a wrong one, the 🎤). It pays its `reward` once a day, as a
happening's dialog does (`GameState.happeningsDone`, under "spot:kopa/<his id>"), and he stays till his time is over; tapped
again he talks again, for nothing. On a canvas without the pile he isn't drawn, and his bubble is where the woods' are.
While his bubble is up, the scroll's "Danes v vasi · Today" lists him with what's on now (`TodayItem.Keeper`: a tap plays
his talk, 📍 flies the camera to the pile), and Home's "Danes · Today" card under "🏡 V vasi · In the village"
(`HomeLogic.calls`: a tap plays his talk over Home).

His talk is written per level (`levels`: A1 and A2 at least), each 3–12 lines of a scene dialog (his lines' `who` his
`id`), 1–3 turns for the learner with a reaction and a why (in every base) to every wrong choice, in the pack's language
with the app's four (`{"sl", "en", "de", "it"}`), no sky, no fx: the introduction's rules (`arrivals.ts`, `arrivalErrors`,
which `validateCulture` runs on it). The learner hears the highest level he has up to theirs in the village's language.

### His story, visit by visit

His `levels` are his first talk, the meeting. After it he tells a story across visits (`talks`, in their order): the
pile's, from stacking the wood to his charcoal at the smith's. Each day his talk is done (the day it pays), the next time
he is there he has the next talk; on a day it isn't done he keeps it for the next evening, and tapped again the same day
he tells the same one again. After the last talk the story starts again, a new pile, never the meeting: each talk's
`again` is how he tells it on a later round (`again[0]` the second pile, then `again[1]` …, and round after round the
next of its `levels` and these, in turn), the same step of the work varied by small things (the weather, the season,
the village) rather than word for word. A talk without `again` is told the same way every round.

    "talks": [
      { "id": "zlaganje", "title": { "sl": "Oglar Miha zlaga kopo", "en": "…", "de": "…", "it": "…" },
        "levels": { "A1": { "lines": […] }, "A2": { "lines": […] } },
        "again": [ { "levels": { "A1": { "lines": […] }, "A2": { "lines": […] } } } ] },
      …
    ]

Each talk and each of its tellings follows his meeting's rules (A1 and A2 at least, 3–12 lines, 1–3 turns, a reaction
and a why in every base to every wrong choice, his lines his, no sky, no fx: `spotErrors`), and its `id` is once. While
it is the one he has, his bubble, the scroll's Today, Home's card and the chronicle say the talk's `title` ("Oglar Miha
zlaga kopo"); a talk without one says his. The village keeps how far the learner got (`GameState.keepers`, under his key
"spot:kopa/oglar": `told`, the talks told, the meeting the first, and `on`, the day of the last; `Keepers.at`,
`Keepers.told`), a count, so a learner whose level rises keeps their place: every level of a talk is the same step of the
story. A keeper without `talks` has his meeting every time, and nothing is counted (when his story comes, the learner
starts it from the meeting). The talks are voiced as the scenes' dialogs are (voice-build's corpus, `keepers`: his lines
and replies in his speaker's voice, every telling at every level), else live when played.

`talks` is a new key: a bridge from before it refuses the culture (its check is strict), so the code goes out first and
the content after; an app from before it plays his meeting every time (it reads `levels` alone).

| Pack | The pile (`spots.kopa`) and its history | Its burner | His turns (A1; A2) | Words |
|---|---|---|---|---|
| `primorska` | Oglarska kopa · The charcoal pile: the oglarji of the woods above the village stacked beech round a pole, covered it with turf and earth, lit it from the top and watched it from a hut for two weeks; the charcoal went to the smiths, Kovač Tone too, and down to Gorizia and Trieste | 🧔 Oglar Miha, from the Trnovo forest; says ti to Jan ("fant"), Jan says vi | dober večer, kaj or kdo; iz vasi (the genitive), spite (vi) | `pri-kopi` (kopa, oglje, oglar, žerjavica, dim, tleti, ruša, pepel, zagoreti, koča) |
| `friuli` | La carbonaia: the carbonai of the Carnia and the Val Resia spent the summer in the woods; the charcoal went to the smiths, Fabbro Bruno too, and as far as Udine and Venice; many went to burn charcoal abroad | 🧔 Carbonaio Pieri, down from the Carnia; the learner says Lei | buonasera, che cosa or chi; ho visto (not vedito), dorme (Lei) | `alla-carbonaia` |
| `kaernten` | Der Kohlenmeiler: Carinthia's Köhler; the charcoal went to the smiths and the ironworks, as at Hüttenberg, where iron was made in Roman times; the Köhlerei still lives there, and Schmied Toni still likes charcoal for his forge | 🧔 Köhler Michl, of the Gailtal; the learner says Sie | Guten Abend, was or wer; gesehen (not gesieht), schlafen Sie | `am-meiler` |
| `lakeland` | The charcoal pit: the colliers of the Lake District's coppice woods built their pits on pitsteads and lived by them in huts of poles and turf; the charcoal went to the bloomeries and later the gunpowder works; the children of Swallows and Amazons meet two of them; the old pitsteads are still black under the moss | 🧔 Collier Ned; he calls the learner "lad" | good evening, what or who; saw (not seed), do in a question | `at-the-pit` |

Each is machine-written (Claude), not reviewed by a native speaker (the spot's `review`). Primorska's high seat has texts of
its own too (`spots.highseat`: "Tu lovci opazujejo divjad, ob zori in v mraku", and Lovec Jože's words, `na-prezi`, on its card).

## A day in the village

Everyone who lives here has a day (`game/villagers/Routine.kt`, pure): when they get up and go to bed, what they do in
between, the evening, and the few rituals that are the only reason to be out at night. The map and the scenes read the
same schedule (`Routine.now`: who is where at any minute, doing what), so someone hoeing the field on the map stands in
the field's scene, and someone in bed on the map lies in their bed in their home's scene (SCENES.md, "Asleep at night").

**Their hours.** From the file's `routine`, else from their kind: the sprite, the storyteller (whoever tells the evening
story at the fire), a child. Children go to bed first, the storyteller last; the others a few minutes apart by their id, so
the village doesn't go dark at one stroke.

| Kind | Up | Bed | Kind | Up | Bed |
|---|---|---|---|---|---|
| child | 7:00 | 20:00 | smith | 6:00 | 22:00 |
| grandmother | 5:00 | 21:00 | beekeeper | 5:30 | 21:30 |
| grandfather | 6:00 | 21:15 | winemaker | 6:30 | 22:30 |
| storyteller | 6:30 | 22:45 | innkeeper | 6:00 | 22:30 |
| aunt | 6:30 | 21:30 | teacher | 6:30 | 22:00 |
| shepherd | 5:00 | 21:45 | hunter | 4:30 | 21:00 |
| farmer | 5:00 | 21:00 | anyone else | 6:00 | 21:50–22:30 |

**By day** (`Routine.stints`): a string of stretches, each tens of minutes long (the day's dice: `roll(seed, date, …)`),
each somewhere doing something: their work (the farmer hoeing the field, the hay on the kozolec's racks, the mill on the
stream once the village has built it; the smith at his anvil, the beekeeper at the hives, the shepherd with the sheep in the
meadow, the grandmother in her garden and washing at the stream, the innkeeper and the winemaker at the market, the hunter
on his high seat), a rest (under the linden, on the fallen log, on the bench by the house), a chat at the well, the
children playing in the meadow, at the well and the pond; their own workplace (the first of their homes that isn't a
dwelling) weighs in too, and a stretch of the same work may follow another. Fixed hours: the first minutes at their door,
lunch at home (12:30–13:10; not the shepherd and the hunter), school on a weekday for the children and the teacher
(8:00–12:30, where a school stands; not in July and August), Sunday mass for the older ones (9–10, where a church stands),
the market on Saturday (three times as likely). Winter has no hoeing, fishing or washing, and more wood to chop. With
nothing else to do they sit on the bench by the house.

**Errands.** A load to fetch is an errand of a stretch of work, not a stretch of its own (`Stint.errands`): the farmer
takes the field's hay to the kozolec, the grandmother fetches water from the well for her garden and wood from the woodpile
for the stove, the innkeeper water for the inn, a child an armful of wood for home. One errand in a stretch, two in a long
one (the day's dice), each at a moment of its own share of the stretch, after the walk there and a while at the work.
An errand takes `Routine.ERRAND` (3) minutes (`Now.errand`): on the map they walk from the work to where the load is
(unless it's there: the field's hay), load it, carry it over (`Load`: hay, water, wood), unload and walk back; the time to
spare goes on a word at the well, the wood gathered at the pile, the hay hung on the racks, so they are back at the work as
it ends. Away on an errand they are in no scene; their spot at the work waits for them.

**Walks** (`TownPeople.route`, `Walkways`). Between stretches they walk straight to the next place and start there: from
one side of the road to the other out to the road, along it and in again; on one side of it, between the houses, straight;
into the woods by the path. Nobody walks through the fire or a building: the fire's reach (wherever someone would be in its
flames, `TownPeople.inFire`: its stones and a little more to either side, from the top of its tallest flames to its front
stones) and each building's footprint (not a field's, which is walked in), the landmarks' and the campsite's fire and tent
are blocks a way goes round, by their corners, the shortest way: past the fire on its nearer side round the plaza, round
the corner of a house. A long walk goes at a brisker step, so it takes at most `Routine.WALK_S` (45 s); the scenes have
them there from then on. Nobody of the village potters about: at their place they stay put, first come first served (whoever
waits there for the learner first, then each by their day keeping their spot until they leave, worked out for the whole
day: nobody moves over when someone comes or goes, and away on an errand their spot waits for them), a walk goes from their
spot where they were to their spot where they go (Luka from the sheep to the fallen log and back: a few steps over), and
whoever stands about looks this way and that now and then. Only the strangers at the road (the pedlar, the pilgrim) take a
step aside and back now and then. The unnamed people of a bigger village go from door to door and to the plaza by the same
ways, a while at each.

**Waiting for the learner.** Someone with a happening on now (`TownMarkers.standing`) is at its place, day or night, and
waves now and then; so is, by daylight, whoever the village's bubbles want the learner for (`Whereabouts.calls`: a request's
giver at its place, someone to meet at their home, today's visitor on the road). At a place with work to do they do it by
daylight between the waves (the field's hoe, the anvil, the hives): "France žanje pšenico" has France in his field
harvesting, a wave now and then; Tone with a request hammers at his anvil.

**Round the fire.** Whoever is at the fire (a happening's person, someone waiting for the learner there, the evening's
sitters, the village's unnamed people, a visitor) stands or sits beside it and faces it (`TownPeople.aroundFire`): never in
its flames, nor just behind them (hidden by them) or just in front (hiding them). The first stands on its right (a
happening's person there comes first: Luka on the morning he warms his hands), the next on its left, then on each side a step
behind, a step in front and a step farther out, all past the fire's reach (`TownAnchors.fireReach`: its stones and a little
more, wider as the fire grows with the ages); a ninth and more go round again farther out. At a festival, whoever the wide
ring would put in the fire stands beside it instead. At the other places the people beside the first never stand in a
building (a field is walked in) or in the campsite's fire, and a building's corner that would be behind its flames has its
people beside them; whoever walks by goes round it ("Walks"). The evening's sitters keep their places round it as the others
go home to bed. With people round it, the fire stays the easiest place to tap: a
tap anywhere on its flames, stones and smoke that isn't right on someone or something drawn there is the fire's, and its
TalkBack target ("Ogenj · The fire", 48 dp, the others' 40) is drawn over theirs, so it is never left out (`FireRingTest`).
Their bubbles keep off it too: a bubble over someone beside the fire (France working at the field's edge by it, Luka
warming his hands) that would cover its flames, stones, smoke or target moves away from the fire, out sideways and a
little up as its person stands beside it, up over it, its pointer still at its person (`layoutBubbles`, `fireKeepOff`:
the fire's tap area at the current zoom, with air for the bob); the bubbles it then lands on push it further up, so none
cover each other, and one following someone past the fire slides round it. When the stack over the fire grows up to the
HUD (a crowd round it, the fire near the top of the screen), the bubble at the top goes aside instead, just under the HUD
left or right of the one below it, else lower down beside the stack, its pointer slanting back to its person or place;
with no room left it shares a little of the bubbles' air (the bubbles themselves still don't touch), and with the fire
just under the HUD (its card open) and the row there full, it hangs lower still, its pointer going up: none under the
HUD, none on the fire, none on each other (`asideUnderHud`). Away from the HUD not even their air touches: what the
pushes leave, a corner on a corner, is cleared the shortest way, round the corner partly both ways (`clearOf`), and a
bubble squeezed in between two side by side goes up off them. Going aside or up is a step, not a slide, but on screen no
bubble jumps: it glides to its new spot in about a fifth of a second (`BubbleGlide`), its pointer at its person or
place all the way and its tap target where it's drawn, while a pan, a zoom or its person walking moves it along at once;
a new bubble fades in at its spot. And each bubble holds on to its spot (`layoutBubbles`' `was`): it stays there,
carried along with its person or place, while that's still free and no other is clearly nearer, keeps to its side of
the stack under the HUD, and comes down from a squeeze only with room to spare, so it doesn't flip between two spots
from one frame to the next as the camera moves.

**The evening.** When it gets dark (the map's night: from about 21:15 in June, 16:50 in December) some sit by the fire
until their bedtime (the storyteller always, the others some evenings by the day's dice, the children more often), the
rest go home: not on the map, their windows lit. At bedtime they walk home from the fire with a lantern and go to bed; the
lamp burns a little while (`Routine.LIGHTS_OUT`, 20 minutes), then the house is dark (`TownPeople.darkWindows`: a building
nobody sleeps in is dark from 22:00). In the dark morning those up are at home, lit, until it's light.

**Stari Janez stays up last.** He sits by the fire until the learner has heard tonight's story; then he goes home to bed:
at his bedtime (22:45, after everyone else) when it was heard earlier, five minutes after it when it was heard later (the
app keeps the minute while it runs, `GameController.storyHeardAt`); unheard, the story's happening is off from 23:30
(`Routine.STORY_UNTIL`, `Happenings.active`) and he walks home then. The other villages' storytellers the same.

**Night rituals**: the only reason to be out at night. At its time the villager walks out with a lantern (a warm glow on the
map), does it, and walks home (or back to the fire, when bedtime is still a while off); one whose place the village hasn't
built (the watchtower) doesn't happen. A tap on them shows it on their card ("🏮 Luka pogleda ovce v staji · Luka checks
the sheep in the fold").

| Role | Primorska | Friuli | Kärnten | Lakeland | Up | Bed | Ritual |
|---|---|---|---|---|---|---|---|
| grandmother | Micka | Rosa | Resi | Maggie | 5:00 | 21:00 | 4:35–4:55 fetches wood at the woodpile and lights the stove |
| shepherd | Luka | Davide | Florian | Jack (and Meg) | 5:00 | 21:50 | 21:25–21:45 checks the sheep in the fold (the meadow) |
| girl | Zala | Giulia | Lena | Poppy | 7:00 | 20:00 | |
| storyteller | Janez | Bepi | Sepp | Wilf | 6:30 | 22:45 | (by the fire until the story is heard, 23:30 at the latest) |
| miller | France | Franco | Franz | Mr Tyson | 5:00 | 21:00 | |
| small boy | Tine | Lorenzo | Jakob | Alfie | 7:00 | 19:45 | |
| aunt | Ančka | Nives | Mitzi | Edna | 6:30 | 21:45 | |
| beekeeper | Anton | Aldo | Stanko | Arthur | 5:30 | 21:30 | 20:50–21:10 listens at the hives |
| smith | Tone | Bruno | Toni | Mr Dixon | 6:00 | 22:10 | 21:45–22:00 banks the embers in the forge |
| winemaker | Marko | Matteo | Stefan | Sam | 6:30 | 22:30 | (not Marko) 21:00–21:20 looks out from the watchtower |
| innkeeper | Vida | Marisa | Kathi | Mrs Birkett | 6:00 | 22:30 | 22:00–22:15 locks up the inn (the market) |
| boy | Nejc | Tommaso | Maxi | Harry | 7:15 | 20:30 | |
| teacher | Mojca | Elena | Eva | Mrs Hartley | 6:30 | 22:00 | |
| hunter | Jože | | | | 4:30 | 21:30 | 21:00–21:20 looks out from the watchtower |

The titles are machine-written (Claude) in the four languages, the pack's first; not reviewed by a native speaker.

**Night happenings win.** Someone with a happening on is up and there (Luka listening for the owl in the forest, on the
watchtower keeping watch, Micka bringing Jan a blanket in the tent, Nejc awake in the burja); their bed is empty.

**Where they sleep** (`Sleep.bedrooms`): a household in its room of the house dealt it (SCENES.md, "Houses and their rooms":
Janez in the living room, France and Tine in the workshop, Marko in the cellar, Vida and Nejc in the attic); someone whose
file says where they sleep (`sleeps`) there, once it stands; the others in the first building of their home with a roof (the
tent, the hut or house, the smithy, the school, the bee house, the market), the cast in its scene's beds when the scene is
nobody's household (Luka in the tent; Micka, Ančka and Zala in the kitchen); anyone else in the first hut, house or tent. On
the map they walk home to that building; its window is theirs.

Where one works is not always where one sleeps. Four of the cast work where there is no home, so their files say where
they sleep (`sleeps`), each in a bed of a scene:

| Role | Primorska | Friuli | Kärnten | Lakeland | `sleeps` | Their bed |
|---|---|---|---|---|---|---|
| smith | Tone | Bruno | Toni | Mr Dixon | `smithy` | a cot of planks along the smithy's left wall under the window, its head against the forge's warm bricks: smiths slept by the forge. He banks the fire at 21:45 (his ritual), then lies down; the coals glow low under their ash |
| teacher | Mojca | Elena | Eva | Mrs Hartley | `school` | a bed along the classroom's left wall under the map, a curtain drawn back at its head: the old village school had the teacher's room in the school house, and an alcove stands in for it |
| beekeeper | Anton | Aldo | Stanko | Arthur | `house:cellar` | he lodges with Marko: a trestle bed in the cellar by the steps, where his honey and wax keep cool too (not the bee house: its front is all hives, and a beekeeper sleeps in a house) |
| hunter | Jože | | | | `house:livingroom` | he lodges with Stari Janez, the old hunter and forester: the other run of the zapeček, along the stove's side, his head at the wall end, his feet toward Janez's in the warm corner |

The living room and the cellar get one sleeper more each (two beds), not the workshop or the kitchen, which have two and
three already. While no house has the room (Marko or Janez hasn't moved in), the lodger sleeps in a house we don't see into;
with no house at all, their home decides (Anton the bee house, Jože the hut). Friuli, Kärnten and Lakeland have no rooms of
houses yet, nor a smithy or school scene: their smith, teacher and beekeeper sleep in the smithy, the school and a house, seen
only by the windows. They have no hunter.

**Each in their own bed** (`Sleep.sleepers`): the household first, then who lodges with them, each in their arrival order, a
child in a child's bed. A bed waits for whoever is still up tonight (`Routine.toBed`: by the fire, on a ritual or a happening
before their bedtime, on the way home): Jože, asleep at half past nine, never takes Janez's place while Janez tells his story.
A bed whose sleeper is up for the day, or out for the night after their bedtime, is free: Ančka moves onto the warm bench when
Micka gets up to light the stove.

**A visit** shows the host's people by the host's day (their files' routines, the host's scenes' storyteller and rooms), at
the phone's clock.

**Where it lives.** The format: `RoutineSpec` and `sleeps` in `Villager.kt`'s file, `villagers.ts` (`routineErrors`: a ritual
at night, 10 to 60 minutes, over before they get up, each id once; a bedtime in the evening or just after midnight; `sleeps` a
roof of `SLEEP_ROOFS`, Sleep.kt's, or `<roof>:<art>` of an art the app draws); `validateCulture` wants each ritual's title in
the pack's language. An older bridge (before the key) leaves `routine` and `sleeps` out and serves the file as before (it
drops a key it doesn't know: be658872 loads the files with `sleeps`); an older app ignores them; an app without `routine`
gives everyone their kind's hours and no rituals, one without `sleeps` (or with a bridge that left it out) has Tone and Mojca
in their buildings' beds all the same (their homes are the smithy and the school), Anton in the bee house and Jože in the
hut, unseen. Tests: `RoutineTest` (the four casts' hours and rituals, a day at work, the dark, a winter night hour by hour,
Janez and the story, a happening winning), `SleepTest` (who sleeps in which bed when, the four's beds and where `sleeps`
falls back to, nobody changing beds in a night, the bed that waits), `HomeBedsRenderTest` (the beds drawn only with their
sleeper), `TownDayRenderTest` (the map through a day and a night, the windows), `WalksTest` (no way through the fire or a
building, round the fire on its nearer side, in the Primorska village, a generated valley and a town; through a day nobody in the
fire or a building on the map; nobody pottering, nobody hopping from one spot to another, second by second into the
evening; France at his field, the hay taken to the kozolec once or twice a stretch, in his field with his happening), and
the bridge's smoke.

## Talking to a villager

From their page or their card on the map. First a scripted greeting (the warmest `greet` line their
level allows for the time of day, then a `remember` line with one of their memories, if they have any). Then Jan can talk
freely: a role-play with the tutor playing them, through the Talk screen, with the scenario id
`villager:<id>` (`GET /villagers/:id/scenario?time=evening`: the phone's part of the day, for the opener; without it
the node's, `Bridge.villagerScenario`). The bridge builds that scenario from the villager: their personality, story, likes,
register, the friendship level and their memories, open goals (greet, ask how they are, talk about
something they like, say goodbye). At `roleplay_end`, the tutor's debrief may carry
`data.memory: {sl, en}` (something from this conversation worth remembering, in the villager's words; in an
Italian village `{it, sl, en}`); the app stores it and adds the points. The role-play's setting, the way the
villager is introduced and the hints come from the culture pack (its `tutor` texts, the village's language and
the learner's base: "Come stai? · Kako si?" for the second learner).

## The training stage

Exercise screens (reviews, modules, challenges, word packs) show a small stage at the top
(`ui/stage/Stage.kt`): the companion's portrait on a blurred backdrop of their place (the scene art when
there is one, else the place's colours) and a speech bubble. `Director` decides the reactions, in the
companion's own lines (`cheer`, `comfort`, `listen`, `greet`, `bye`) topped up with built-in ones
(`Lines`, in the ti and the vi form) while they have fewer than two open at their level:

| Moment | Pose | Line | Voiced |
|---|---|---|---|
| a new exercise | TALK | a lead-in for its kind ("Kaj manjka?"); the reviews open with the warmest `greet` | when the kind changes, never over the exercise's own audio |
| a listening exercise | LISTEN | `listen` | no: they read the audio prompt itself, in their voice |
| right | CHEER or HAPPY (HAPPY after a hint) | `cheer` | yes (not in a timed run, where the cheer stays up instead of the next lead-in) |
| almost | HAPPY | a gentle "skoraj" | yes |
| wrong | SAD | `comfort`, never mocking | yes |
| a hint | THINK | "Hm, pomisli …" | no |
| the end (finish screen) | WAVE | the tally against the whole run and `bye`; only thanks when nothing was right | yes |

Lines open by friendship level and fit the time of day (a line's `when`: no "Lahko noč" after a morning
review, "Pa lep dan" instead; the built-in goodbyes have a good day, a good evening and a good night too),
warmer ones weigh more, the day's dice pick, never the same line twice in a row. The stage collapses to a small avatar while typing, on short screens and with large fonts; the
bubble is a polite TalkBack live region; 🔈 mutes the companion (kept on the phone); with animations off the
portrait holds still. A session with anything answered grows the friendship by `Bonds.TRAINING`.

Who's on stage (`Cast.onStage`): the quest's giver; for a project step, its leader; for a festival, the one who leads
it; for the day's surprise, who it is about (the pilgrim himself, a stranger: `Run.Stranger`; the letter to Jan:
today's companion); for a module, its
quest's giver; for an event, the
defender (wolves and the bear: Kovač Tone; the storm: Stari Janez; the merchant: Gostilničarka Vida; the
festival: Babica Micka); for gathering, who works there (the field: Mlinar France; the forest: Pastir Luka;
the quarry: Kovač Tone; the linden: Stari Janez); for a word pack, its giver; for the daily reviews (and a
module or pack without a giver), today's companion (the friend Jan hasn't seen for the longest, picked by
the day's dice among those they've met, then kept for the day; Babica Micka until they've met anyone).
Someone who lives here but hasn't been met yet never is: today's companion stands in until their introduction
("Arrivals"). Someone the cast doesn't know stands in with the sprite, voice and role their title suggests ("Teta …",
"Kovač …"), and built-in lines.

## Intros

Before a quest, a module, an event, a gathering run, a word pack or a role-play starts, a short intro
(`ui/stage/Intro.kt`, built from `IntroInfo`): the villager's portrait on their place and their request in
Slovene (their warmest greeting, then the ask, voiced in their voice, English below; an emergency skips the
greeting, a role-play opens with its first line), the story, what Jan will practise (skills, kinds and
number of exercises, about how many minutes, the pass mark or the clock), what it pays (the reward, the
most the answers bring, "+10 ♥ Luka"), and Start / Later. Later goes back to where the run was started;
"once more" from a finish screen skips the intro.
