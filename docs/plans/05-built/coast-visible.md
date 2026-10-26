# Built: the coast's sea in view, and a Friuli village in its own language

Jan, 2026-10-26, from the tablet of the second learner (Teo: a child, Italian from German, the Friuli culture, founded on
the coast; land `{"kind": "coast", "seed": 551323397}`, age Ogenj):

- "He chose the beach location. I hear the beach, but can't move to it / can't see it."
- "Also the location does not look like what we saw in the preview. It looks like my location."
- "If I tap the animals, it shows me the Slovene words, not Italian."
- "He is doing a free-style dialog; the aims are in English, not German."

The rules as built are in companion/GAME.md ("The land": the landscapes, the water in view, the wild animals' words;
"Background sounds") and companion/VILLAGERS.md ("Talking to a villager"). This note says why it was so, what changed,
how to use it and what is left.

## Why it was so

- **The sea off the canvas.** A coast's sea tried three shores and kept the first that left the plots room: the 28th
  plot no further than 132 px out. Half of all generated lands (every landscape: the stream, the road's bends and the
  rows' shift decide it) have their 28th plot further out even without water, so on half the coasts no shore passed and
  the fallback put the sea 30 px further out with no slope: 160 to 180 px left of the fire. The full-screen town's canvas
  is 320 nominal px wide on a tablet (either way up) and 360 on a phone held upright, 160 or 180 px either side of the
  fire: the sea lay off it, the camera (which keeps to the canvas) couldn't pan to it, and the land looked like a valley
  with a stream, as Jan's own village does. Over 3,001 seeds: 1,573 coasts on the fallback, 1,481 without any sea on a
  320 px canvas, 868 more under 16 px of it at the horizon. Teo's land is one of them (its shore at 179 px).
- **The preview showed it.** The founding's preview is a card about 950 × 594 px on a phone: the town's framing makes it
  a canvas about 475 nominal px wide, 237 px either side of the fire, where the same sea was in view.
- **The sound.** The village's sea sound comes from the culture (Friuli's horizon shows the sea, `backdrop.sea`), not
  from the land: it played while the land's sea lay out of view.
- **The lakes too.** A lake that left too little room went 42 or 52 px further out and smaller: a few px of it, or none,
  on a 320 px canvas (598 of 1,501 lakes under 20 px).
- **The animals' words.** The map's wild animals (`Wild`) carried their Slovene word, which the tap opened as a word card
  (looked up in the Italian dictionary of Teo's bridge).
- **The goals.** A villager's free talk is a role-play the node builds (`villagers.ts villagerScenario`) with the
  contract's four goals in English, for the tutor; the Talk screen showed them as they came. Teo's bridge has no curated
  scenarios (companion/scenarios/ is Slovene only; an Italian bridge reads companion/scenarios/it/, which doesn't exist),
  so the free-style dialog with goals Teo played is a villager talk ("💬 Parla · Unterhalte dich", then the free talk).
- **The culture itself switched.** GET /culture → `CultureSetting.profileSays` → `Cultures.use` happens before the founding
  (the coast is offered only where the culture's horizon has the sea, so Teo's founding had Friuli), and later starts apply
  the kept culture before the first frame. The reload asked it fifth, though: a step before it that threw would have kept
  it away from a new phone's first founding.

## What

- **Terrain** (`game/render/Terrain.kt`): `reach`, the 28th plot's distance without the water (or 132 px where it is
  nearer), is what the water must leave the plots. The sea's shore lies 122 to 136 px left of the fire at the horizon
  (24 to 38 px of water on the narrowest canvas) and comes in lower down; where it leaves too little room it comes in less
  lower down, then lies further out, never more than 140 px (`SEA_OUT`); where none leaves room, the one whose last plot
  lies nearest (`roomiest`). A plot keeps 12 px from the beach (`SEA_ROOM`). The shore keeps left of the woods' pond only
  from 40 px below the fire (`POND_ROWS`, easing off a px a row above), so a pond far left no longer pulls the whole shore
  out. A lake tries its first four shapes (at most 32 px further, 0.84 of its size) and keeps the roomiest.
- **The town view** (`TownAnchors.home`, `isSea`; `TownView`): on the coast the first look frames the beach and 16 px of
  the water; a tap on the sea or the beach opens the sea at the horizon (Friuli's "Il mare"); the view's cached anchors
  key on the land too.
- **The sound** (`Place.Village.beach`): the village on the coast hears its sea nearer (0.6, at the horizon only 0.35).
- **The animals' words** (`Wild.word`, the string tables' `wild.*` in sl, en, de, it): in the language of the village on
  screen (`Cultures.current.language`; a visited town's on a visit). `VillageHit.OnAnimal` carries the animal.
- **The goals** (`VillagerLogic.goals`, `.localized`, applied in `VillagerController.scenario`): a villager talk's four goals
  from the app's strings in the learner's pair ("Saluta (dai del Lei) · Begrüßen (per Lei)").
- **The culture first** (`AppViewModel.reload`): GET /culture is the first thing a reload asks.
- **QA** (`QaHooks` "seed:<n>": the founding's place; companion/bin/qa `coast`, `LANI_QA_PAIR`, `LANI_QA_FOUND_SEED`,
  `LANI_QA_COAST_SHOTS`).

What it changes for villages that exist: every coast (its shore 14 px further in; plots beyond the first by the fire may
lie elsewhere) and the lakes that were pushed out. Teo's land keeps all 28 of its plots (its old sea took none of their
places, and the new one takes none either; `CoastTest`), its sea now 135 px left of the fire. Teo's saved state needs
nothing: its land is the coast with its seed, as founded, and the app draws it anew from those two numbers.

## How to use it

Nothing to turn on. QA:

```
LANI_DEV_DATA=<a synthetic Friuli learner without a village> LANI_ENV_FILE=/dev/null LANI_CULTURE=friuli \
LANI_LANDSCAPE=coast LANI_CHILD=1 LANI_QA_PAIR=sl-en LANI_QA_FOUND_SEED=551323397 LANI_QA_COAST_SHOTS=<dir> \
flock /tmp/lani-emulator.lock companion/bin/qa --no-town --steps coast
```

The learner's data: the bridge's `initLearnerData` (name, `target: "it"`, `base: "de"`, `child`, `culture: "friuli"`).

## Tests

- **App:** the unit tests, 2,951, one failure: `LandmarksTest`'s frame budget at a load average of 66, which passed run
  alone (24 ms a frame). New: `CoastTest` (8: Teo's village founded on its land; its 28 plots where they were; the sea on
  the narrowest canvas at every row from the horizon past the last plot, off the plots and the pond, over 304 seeds; a lake
  in view; the plots' room against the land without the water; Teo's village drawing sea and sand on 320 × 200,
  320 × 512, 360 × 800 and 400 × 180 at Ogenj, Zaselek, Vas and Mesto, its home view taking them in; a tap on the sea or
  the beach is the horizon's; the land kept through a tick, PUT /game's JSON, a reconcile and the phone's cache),
  `WildlifeTest` (an animal's word in each language, by the culture and on a visit), `VillagerLogicTest` (a villager
  talk's goals in it · de, the node's other goals kept), `SoundscapeTest` (the coast's sea nearer), `QaHooksTest`
  ("seed:<n>"); `LandsTest` runs on 320 × 200 too.
- **Python:** `tests` 251 OK, `companion/tests` 98 OK (10 skipped). The bridge is unchanged.
- **The emulator** (the debug build; the dev bridge on 8791 on a synthetic learner, Tobi, a child, Italian from German,
  made by the bridge's `initLearnerData`; `LANI_CULTURE=friuli`, `LANI_LANDSCAPE=coast`; `companion/bin/qa --no-build
  --no-town --steps coast`): setup, coast and the crash check passed. The founding offered Friuli's five landscapes, the
  coast first, its preview at 551323397 with the sea; the village after founding with the sea and the beach along its left
  on the phone (360 nominal px) and on a tablet held upright (the display at 1600 × 2560: 320 px), dragged to the canvas's
  left edge; a squirrel tapped at noon: "scoiattolo" on its card (the dev bridge has no Italian dictionary: "not in the
  dictionary"); Nonna Rosa's free talk in it · de with its goals "Saluta · Begrüßen" … "Saluta e vai · Verabschiede dich";
  the same land as a village and as a town. A run without QA's pair showed the founding in it · de, the coast offered.

## What's left

- **The scenario format is English for the tutor.** A villager talk's setting stays English (only its goals are the
  app's), the market's haggle at a visit (`visits.ts`) has English goals, and the curated scenarios exist in Slovene only,
  with English goals, setting and translations. Goals and a setting in the learner's base need the format to carry them
  (or the app's strings per kind of talk), which the lead plans separately.
- **A coast or a lake whose water leaves too little room** has its town's last plot a little further out than without the
  water: at most about 16 px on 49 of 1,501 coasts, and at most about 18 px on 897 of 1,501 lakes (a lake in view lies in
  the clearing's right, where the plots are). Its town's clearing reaches that much further past a narrow canvas's edges at
  the last ages; the spots, the plots and the anchors stay on every canvas (`LandsTest`). A smaller lake would leave more
  room, if the town's last ages look crowded at its edge.
- **Labels read once.** A villager's role and likes given per language (`Label`) are read in the pair of the moment the cast
  is read: after QA switched the pair to it · de mid-session, Nonna Rosa's page still said "Babica · Grandmother" until the
  next reload. A learner whose pair changes in the connection sheet sees the same until then; Teo's, read in it · de from
  the start, says "Nonna · Großmutter".
- **Manual setup on a used phone** (a URL and a token, not a QR code) has no check that the bridge is the same learner's;
  the phone's cached village would go to a bridge without one. Pairing by QR code refuses another learner's bridge.
