# Plan 2: own worlds, other languages, towns that visit each other

*Status: draft, 2026-09-25. Builds on [plan 1](01-public-availability.md) (its Phase 0 and the hosting
variants) and on the village economy being built now (help points, the moba, tools and goods, trade).*

**The idea, in Jan's words:**
- Every learner gets a world of their own, generated, so other users have other worlds.
- Any language pair, e.g. German → Italian.
- Family towns that visit each other. A second learner learns Italian from Slovene right now, so they start their own
  world. When Jan visits the second learner's town, Jan has to get by in *its* language (Italian), and the other way
  round.
- Towns trade, become friends, help each other, and people move between them.
- Visiting improves your skill in that town's language, without a town of your own in it.

## Jan's decisions (2026-09-25)

- **The second learner's tutor runs on Jan's Claude account, on the same machine: another agent.** Two always-on
  sessions share that account's usage limits. `lani-session` gets a profile argument: its own data
  directory, bridge port, tmux session and pairing QR code.
- **One agent per learner, speaking all of that learner's languages.**
  - Jan's agent handles Jan's Slovene town *and* the Italian Jan practises on visits. Claude is fluent in both,
    and the channel message says which town and language a turn is in, so one agent per profile is easier
    than one per language.
  - Separate learners (Jan, the second learner) get separate agents. Each has its own memory and context, and the second learner's
    agent has instructions suited to a child.
  - Visits between the two towns go through the bridges (a small API between them), not through one shared
    agent.
- **Keeping it safe for a child: invite only.**
- **Base and target are chosen, not fixed.** English stays Jan's base. Every learner picks the pair (base ↔
  what to learn) at setup and can change it in the settings.
- **The learner chooses where their town is, and that shapes the map.** At world creation:
  - a **region** from the target language's culture packs (for Italian e.g. Friuli, Tuscany, Sicily, the
    Dolomites). It sets the architecture, the names, the foods, the local feasts and the weather (how much
    snow);
  - a **landscape** (a valley with a stream, hills, a lake, the coast, the mountains);
  - a preview with a reroll of the seed.

  The second learner's town is Italian, in **Friuli, near Gorizia**: the Collio hills facing Brda. The two towns
  look at each other across the border, as Nova Gorica and Gorizia do, so a visit is a walk to the
  neighbours.
- **Cultures are made in advance; worlds are generated within them.** Three layers:
  1. **The culture pack, made once per region**, drafted by Claude, checked by a native speaker, and shared
     by every learner of that culture: the cast, quest templates, festivals, goods and tools, project and
     event texts, names and places, word packs, scene words, the voice cast, and the region's look.
     (`companion/cultures/<id>/`: Primorska, then Friuli, then Lakeland, the English village in the Lake District.)
  2. **The world, generated from a seed within its culture:** the map, who moves in when, newcomers and
     births (named from the pack), events, the day's surprises, which quests come up.
  3. **The learner's own, made live by the tutor:** modules for weak spots, extra packs, role-plays,
     memories, the tutor's quests, voiced once and cached by glas.

  So the cast is not generated at setup (as §2 first had it): it comes from the pack.
- **Many people per culture** (Jan, 2026-09-26):
  - **Roles are fixed, the people vary.** The game needs its roles: the grandmother (the first companion),
    the shepherd (wood), the smith (stone; comes with the smithy), the teacher (with the school), the
    storyteller, the winemaker, the innkeeper, the children.
  - **A pack holds 2–4 candidates per role,** and each world picks one per role from its seed.
  - **Extras:** 10–20 smaller people per culture (a postman, a priest, a baker…), with fewer lines, move
    in as newcomers alongside the generated names. Begun: a villager file's `"extra": true` (Residents takes turns
    between the extras and the made-up names), Primorska's first, Lovec Jože at the high seat (VILLAGERS.md, "Extras").
  - **The cost:**
    - Code: everything that names a person (`Cast.gatherer`/`defender`, quest givers, scene people)
      names a role instead. A clean, one-time refactor.
    - Content: every full character needs about 30–60 lines, quests, goods, tools, likes and a voice, and a
      native speaker's review.
  - **Start small:** roles in the code after the Friuli pack, then 2 candidates for the most visible roles
    and a handful of extras per culture, growing with the reviews.
  - Existing worlds keep who they have.
- **A town looks like its region from the start.** Buildings, roofs, the church (a campanile), materials and
  the landscape differ by language and region; it's part of the generated worlds (step 4), not an extra.

---

## Where we are: what's generated today

| Part | Today | Different per learner? |
|---|---|---|
| The map (valley, stream, pond, forest, plots) | Generated from the village's seed (`Terrain`, step 4); the villages from before keep the fixed layout, the classic valley. | Yes, for a village founded since (2026-09-27). |
| Buildings, ages, upkeep, balance | Rules in `Catalog.kt`/`Engine.kt`. | No (by design). |
| The cast (13 people) | Hand-written JSON (`companion/villagers/`), all from Primorska (Trnovo, Nova Gorica, Brda). | No. The tutor can publish more (`publish_villager`). |
| People who move in or are born | Generated from the village seed: first names, and surnames from a list of Primorska families. | Yes, but only in Slovene. |
| Events (wolves, bear, storm, merchant, feast) | Five types, rolled per day from the seed. | The timing yes; the kinds and texts no. |
| Local quests | About 26 hand-written templates, matched to the learner's weak cards. | The choice yes; the stories no. |
| Tutor quests, modules, packs, scenarios, scenes | Generated by the tutor (Claude) for this learner, next to the curated sets. | Yes. This is already per learner. |
| Scene art (8 close-ups) | Painted in code (`game/render/scene/`); the scene JSON places words and people in them. | No. |
| Language | Slovene everywhere: content, voices (`language_code: 'sl'`), recognition (sl-SI, Whisper `language=sl`), grading and normalization, the Slovene plural forms with the dual (`slCount`), gender agreement, and about 1,000 "Slovene · English" labels in the app. | No. |

So today's "world" is one hand-made Slovene village. The engine, the art and the tutor's generation are the
parts that already carry over.

---

## 1. Language pairs

A learner has a **base language** (explanations, translations, the second half of every label) and a
**target language** (what their town speaks). Jan: Slovene ← English. The second learner: Italian ← Slovene. Another
learner: Italian ← German.

What has to change:

1. **Labels and screens.**
   - Every "Slovene · English" label becomes a key with one text per language, shown as
     "target · base". Today that's about 1,000 literal strings in the app, plus the family page and the
     tutor's instructions in the bridge.
   - A string table per language, drafted by Claude and checked by a native speaker for the target side.
     The target side is what the learner reads and learns from, so it must be right.
2. **Grammar helpers.**
   - Plural forms come from ICU/CLDR (`android.icu.text.PluralRules`, which knows the Slovene dual)
     instead of `slCount`.
   - Gender agreement ("si pomagal / pomagala"), articles and formality (ti/vi, tu/Lei, du/Sie) per
     language: a small interface, one implementation per language.
3. **Grading.**
   - Normalization and accent hints per language (today: č, š, ž).
   - Typo tolerance by word length.
   - The target's locale for lower-casing.
4. **Speech.**
   - **ElevenLabs:** `eleven_v3` takes a `language_code`. Italian and German have many native library
     voices, so they're easier than Slovene.
   - **Voice cast:** a cast file per language (`voice-cast.<lang>.json`); the voice design flow works as it
     is.
   - **Local fallback TTS:** per language (Gepard is Slovene-only; e.g. Piper voices for Italian and German).
   - **Recognition:** Whisper takes the language, and the phone recognizer takes the target locale.
5. **Content per target language.**
   - Word packs (with translations into the base language), grammar modules (explanations in the base
     language), scenarios, scene words and quest stories.
   - The curated Slovene sets stay. For a new language the tutor generates a starter set at setup, from
     the same specs (`lani.pack/v0`, `lani.module/v0`, …), then goes on as it does now.
   - The base language changes only the translations and explanations, so an Italian set can serve German
     and Slovene speakers alike, with a translation layer.
6. **The tutor.** The skills and `LEARNING_SYSTEM.md` are already language-agnostic (upstream Fluent supports
   any language). The bridge's channel instructions name Slovene; they take the pair from the profile.

**First pair to add:** Italian ← Slovene, for the second learner. It tests a new target and a new base in one go.

*Built (2026-09-26): all 12 pairs among Slovene, English, Italian and German ([companion/README.md](../../companion/README.md),
"Language pairs"). 1: four string tables with the same 1,260 keys (de and it machine-written, not reviewed); a text naming
the language learned selects on the target; names and dates read in each half's language. 2: ICU plurals; ti/vi, tu/Lei,
du/Sie in the built-in villager lines and the role-plays. 3: a grading table per language, typos by word length (two
slips from 9 letters). 4: carrier sentences and `language_code` per village language; the phone's locale; Whisper's
general model for it, de, en. 6: the instructions name the pair, Jan's base too when it isn't English;
`lani-profile add` picks the village by the target and stops when no pack speaks it. Tests: LanguagePairsTest (the 12
pairs in the app), the bridge smoke's pairs section (sl ← de, it ← en, en ← it).*

**What's left, per pair:**

| Target ← base | Village | Left |
|---|---|---|
| sl ← en | primorska | Nothing: Jan's pair. |
| sl ← de, sl ← it | primorska | A native speaker's review of the German and Italian in the word packs, the cast's lines, the primorska pack and the village scenes (machine-written; each says so in its `review`). The six grammar modules and six role-play scenarios explain in English only (their formats have no text per language yet). The Slovene dictionary's meanings in German and Italian (`sl.de`, `sl.it`). |
| it ← sl | friuli | A native speaker's review of it.json and the friuli pack. The Italian dictionary's Slovene meanings (`it.sl`, M7). |
| it ← en | friuli | The review. |
| it ← de | friuli | The review, the German of the friuli pack and `packs/it` too (machine-written). `it.de` meanings. |
| de ← en, de ← sl, de ← it | kaernten | A native speaker's review of de.json and the kaernten pack (both machine-written). Native voices beyond the narrator and the old man (the voice cast's others are voices of the account). A German dictionary (`de.json.gz` through `LANI_LEXICON_URL`). |
| en ← de, en ← sl, en ← it | lakeland | A native speaker's review of the lakeland pack (machine-written). Northern English voices (the voice cast is British and other voices of the account). The alps painter draws Alpine peaks, not rounded fells. An English dictionary (`en.json.gz`). |

For every target but Slovene: no curated modules, role-plays or scenes yet (the tutor writes them), and no local voice
(Gepard speaks Slovene only: ElevenLabs or the phone). A few app texts name Primorska's things in every culture:
the intro's "valley below Triglav", and the ages "Zaselek" and "Trg" in three hints.

---

## 2. Generated worlds

A world is generated once, at setup, from **a seed** and **a culture pack**, and stays the same after that
(deterministic, like today's rules).

- **The land from the seed.** The valley's shape (the stream's course, a pond or a lake, hills, how dense
  the forest is), where the plots lie, the trees and rocks, and the far mountains. The layout keeps the
  constraints the game needs: plots on flat ground, the fire in the middle, the path to the road.
  - This is code: `VillageLayout` takes the seed instead of constants.
  - The renderer stays.
  - The QA and pacing tests run against several seeds.

  *Built (2026-09-27, step 4a; [GAME.md](../../companion/GAME.md), "The land"): a village's land (`GameState.land`: its
  landscape and its map's seed) is laid out by `game/render/Terrain.kt` from the seed: the stream's course, the road's
  bends, the clearing's rim, the plots' rows and order, the woods' density, spruce and glades, the rocks and the woodpile,
  and the woods below (path, pond, meadow, stack, high seat, charcoal pile). The villages from before stand on the classic
  valley, the fixed layout: the next tick marks them, and they render pixel for pixel as before (`ClassicLandTest`,
  a real Primorska village save compared before and after). A generated land keeps the game's constraints (`LandsTest`: 18 seeds a
  landscape: plots, palisade, spots and their taps, landmarks, wild animals, buildings in view). A visit draws the host's
  land (the public state's `land`).*

  *Landscapes (2026-09-27, step 4b; GAME.md, "Landscapes"): a valley with a stream, hills (rises, vineyards' terraces,
  beech woods, rolling hills at the horizon), a lake (at the clearing's right, a shore path), the mountains (a torrent,
  spruce and pastures, boulders, the Alps close) and the coast (the sea along the left, a beach, the stream's mouth, the sea
  at the horizon; only for a region with the sea). Each changes the layout's shapes and the village's horizon (the
  backdrop's Alps, foothills and sea band).*

  *The choice at setup (2026-09-27, step 4c; GAME.md, "The choice at setup"; README, "The village's place"): the app's
  first start founds the village where the learner chooses: the region (the target language's culture packs; the node
  plays one, `lani-profile add --culture`), the landscape (the region's, the node's `--landscape` first), a preview of
  the village at its first age, "🎲 Nov kraj · Another place" to reroll the seed. Only for a new village.*
- **The culture pack**, per target language and region: place names, first names and family names, foods,
  a feast, the local building names and small things (kozolec and lipa; for Italian e.g. a piazza, an
  osteria, a vineyard), event flavour (a merchant from Trieste, bora wind).
  - Written by Claude from a template, reviewed, versioned. A learner may pick a region ("Friuli",
    "Tuscany", "Primorska").
- **The cast comes from the culture pack** (decided 2026-09-26; see "Jan's decisions"): 10–13 villagers
  per region, made once to the rules in `VILLAGERS.md` (one voice and one skill each, a mix of ages, the
  children, a teacher who comes with the school), checked by a native speaker, with voices chosen or designed
  once. The world decides who arrives when; newcomers and births are generated from the pack's names.
- **Quests and events.** The quest templates get generated per culture too (same fields, the stories in the
  target language). The events keep their rules and get their texts from the culture pack.
- **Art.**
  - Near term: the same painters with the culture's palette and small swaps (roof colours and shapes, a
    campanile for the church, cypresses for poplars).
  - Later: painter variants per region. That's the expensive part and can wait.

---

## 3. Towns that visit each other

### 3.1 Everyone their own world

- Each learner has their own world and tutor. The second learner starts one with Italian ← Slovene.
- Family members' towns are linked by an invite: a code, or a QR code on the phone.
- A visitor sees a friend's town as it is (read-only): the map, its age, its people. They never see the
  host's private things (chat, mistakes, stats).

### 3.2 A visit speaks the host town's language

When Jan visits the second learner's town, everything there is **Italian**, at Jan's level in Italian. When the second learner
visits Jan's, it's Slovene. The things to do are small, and each one is practice in that language:

- **Greet the villagers:** a short role-play with one of the host's people, in their language. The tutor
  plays them at the *visitor's* level.
- **The market:** buy or sell goods by negotiating in the host's language (a generated scenario, e.g. "20
  wood for a jar of honey"). The trade happens when the exchange goes well enough. This uses the goods and
  trade being built now.
- **Help with a request:** a host villager's request in their language. It earns the visitor goods and the
  host help points.
- **Leave a gift or a message:** e.g. a sentence in the host's language, which the host reads and answers.
- **See the host's own places:** each culture pack brings scenes only it has, reached by tapping the
  village's backdrop: Primorska's hills (vineyards, a kozolec, the church on the hill), Friuli's sea (the
  Adriatic at Grado, the lagoon). Visiting is the only way to walk into another culture's scenes, in its
  language, so a visit is a small trip abroad. A scene can hold a dialog meant for guests ("Sei di fuori?").
  *The places are there (2026-09-26): each pack's `scenes/`, Na gričih and Il mare, open from the village's horizon in
  its own village ([SCENES.md](../../companion/SCENES.md), "Scenes of a culture pack"). A dialog for guests says
  `"guests": true`: the host doesn't get it, a visitor does (2026-09-26, step M3; SCENES.md, "Dialogs for guests").*

**The host takes part too.** It works like today's partner challenges:
- the visitor can ask the host a question in the host town's language;
- the host answers in that same language.

So in Jan's visit, *both* practise Italian: the second learner as the host, Jan as the visitor. The family page's
recordings also carry over: family members who speak the language natively can record words for that town.
*The questions are there (2026-09-26, step M6; [companion/README.md](../../companion/README.md), "Questions between
towns"): "❓ Vprašaj · Ask" on a visit, checked by the guest's tutor first if they like; the host answers in their app,
their tutor grades it; each tutor records its learner's text as practice in that language; the text between the towns is
untrusted content to both tutors and plain text in the apps; a child's town takes questions from its linked towns only.*

Visits are asynchronous at first (the host answers when they open the app). Later, a live visit could have
both on at the same time.

### 3.3 Friendship between towns

Towns get a friendship level, like the villagers' bonds, grown by:
- visits;
- trades;
- help: sending moba help to a friend's town while they build;
- gifts;
- answered questions.

It pays off:
- better trade rates;
- a shared feast now and then;
- help arriving when an event hits (wolves at the second learner's palisade: Jan's town can send help);
- **people moving between towns.** At a high friendship level, and once the learner's skill in the other
  language is good enough, someone from the friend's town may move in. They're a bilingual resident: they
  speak their language with you now and then, so you keep practising it at home. Someone of yours may move
  there too.

*Built (2026-09-26, step M5; companion/README.md, "Friendship between towns"): five levels (Znanstvo, Dobro sosedstvo,
Prijateljstvo, Tesno prijateljstvo, Pobratenje). Each town counts what it did and what the other did; an act counts once
both towns have it, so one town can't raise the level alone, and the level needs both towns' part. The market's rates
get better with each level; help against wolves or a storm from level 1; a shared feast from level 2; people move from
level 3, at A2 in the other town's language, when one learner offers and the other says yes (with a child's town, the
child's side offers and the grown-up side says yes).*

### 3.4 Progress in a language without a town of your own

- Every learner has a **language profile per language**: the target of their own town, plus any
  language they've practised by visiting. Each has its own spaced-repetition items, level and mistakes.
- A visit's words and mistakes go into the visitor's profile for that language. The tutor adapts to it, and
  the progress screen shows it, e.g. "Italijanščina · Italian: A1, 85 words (from visits)". It never grows a
  second town.
- Today's databases (`data/*.json`) are single-language. They get a language key, or one set of files per
  language.

---

## 4. How it runs

| Option | What | For |
|---|---|---|
| **A. A family node** | One bridge, several learners: each with their own data directory, world, token and tutor session. Visits are between learners on the same node. | Jan and the second learner, now. It's also the first step of plan 1's hosted variant: tenancy on one node. |
| **B. Federation** | Each family runs its own node (plan 1, Variant 1). Nodes visit each other over an authenticated API (signed invites, over Tailscale or HTTPS). | Friends outside the family, without a central service. |
| **C. Hosted** | Plan 1, Variant 2: a central service holds the towns. Visits are internal. | Everyone else. |

**Recommendation:** build A now, with the visit and trade interfaces written as if the other town were
remote (a `TownId`, an API between towns), so B and C reuse them.

**Claude, for a second learner.** Each learner has their own tutor agent: a separate session with its own
context. Decided: the second learner's agent runs on Jan's account, on the same machine (see "Jan's decisions"). For
others (option B or C), it's their own account, or an API key.

---

## 5. Safety and privacy (a child is involved)

- Only invited family and friends can visit. There's no public directory and no messages from strangers.
- A visitor sees the public town only. The host's chat, mistakes and progress stay private.
- What a visitor writes reaches the host's tutor as untrusted content (as partner messages do today) and
  the host's screen as plain text. The tutor's replies to a child are kept age-appropriate: a line in the
  channel instructions, per learner.
- **Consent:** GDPR's digital age of consent in Slovenia is 15, so parents consent for younger children.
  This matters as soon as it's hosted (plan 1).

---

## 6. Order of work

| Step | What | Depends on |
|---|---|---|
| 1 | **Several learners on one node** (option A): profiles, data per learner, tokens, a tutor session per learner, switching learners in the app (or one app per learner) | Plan 1 Phase 0 (configuration, pairing) |
| 2 | **Language pairs**: string tables, the grammar helper interface, per-language grading, voices, recognition; Italian ← Slovene as the first new pair | 1 |
| 3 | **Content for a new target**: starter packs, modules and scenarios generated and reviewed; a culture pack for the second learner's region | 2 |
| 4 | **Generated worlds**: the seeded layout, the generated cast with voices, per-culture quests and events, palette swaps | 3 |
| 5 | **Visits**: a read-only view of a friend's town, visit activities in the host's language, host questions, the visitor's language profile | 1, 2, and the goods economy now being built |
| 6 | **Trade and friendship between towns**: market trades by negotiation, help sent between towns, friendship levels, people moving | 5 |
| 7 | **Across nodes** (option B), then hosted (option C) | 6, plan 1 |

Steps 1–3 give the second learner their own Italian world. 4 makes it look like their own. 5–6 are the multiplayer.

---

## Open questions for Jan

1. The second learner's base language is Slovene. What's Jan's: English, as the app shows now, or German?
2. Which region should the second learner's Italian town be in (Friuli, near Gorizia, would tie the two towns
   together)? Is there a native speaker who could check the Italian, and record words?
3. Asynchronous visits first, and live visits later: is that right?
4. Should a town look different per culture from the start (palette and roofs), or is the Slovene valley
   fine for the first Italian town?
5. Does every learner get a generated cast, or can Jan keep the hand-made Primorska cast (yes by
   default)?
