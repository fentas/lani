# Built: the mill inside

Jan had just finished the village's mill project (🌾 Mlin na potoku, France's) and asked: "why can't I go into the mill?
;)". The mill was a landmark only: on the map by the stream, and in the stream's scene (Ob potoku) as the overshot wheel
and the flume, built step by step. Now it has its inside, in the Slovene village and in the Friuli one, and both ways to
it go in once the mill grinds.

The format and the rules are companion/SCENES.md's: "The mill inside: from the stream and from the map" (how one gets in),
"Arts" (the `mill` row: its slots, spots and effects), "Scenes of a culture pack" (the Friuli row) and "Seasons" (a row).

## What

**The art `mill`** (`game/render/scene/MillPainter.kt`, on RoomPainter: its window and its view, its cat, its helpers): an
isometric diorama of the mill's room on its plot by the stream, in the village's chibi pixel style, like the smithy and
the rooms of the houses. Plank walls on a stone footing (as the stream's mill is built), the floor's boards dusted with
flour. By the back-right wall a timber platform carries the millstones: the bed stone and the runner in its round vat, the
runner's grooved top turning in the vat's opening; over them on its frame the hopper (koš) with its golden grain and the
shoe that shakes it into the runner's eye; from the vat's front a spout runs the flour into an open sack on the floor.
Under the platform the cog wheel turns, half in its pit, on the shaft that comes in through the front-right wall from the
waterwheel; its pegs turn the lantern pinion on the stones' spindle. Outside on the right the overshot wheel turns in its
race under the flume, the water falling on its buckets, and the race pours off the plot's front edge. The waterwheel, the
cog wheel, the pinion and the runner turn together, from one angle (the shaft's), the pinion and the runner three times as
fast. Under the window the miller's bench with the brass scale and the measure (merica, the miller's toll); the sieve high
on its peg; the mouse at its hole at the wall's foot; the sacks of grain along the left wall under the loft's gallery and on
it, the ladder up to it, the plank door with the lantern on its hook beside it, the cat crouched by the sacks. An old
millstone lies in the grass in front.

19 object slots (every visible thing a word), 3 person spots (`stones`, `sacks`, `door`), no stage and no beds. Day and
night (the window's light in a slanting beam full of flour dust by day, the lantern at night), the seasons (snow outside,
ice along the race, icicles on the flume and the wheel, rime on a still wheel; autumn and spring in the grass), the
weather (rain on the window's glass and over the yard and the race, a grey sky darkening the room), every detail (the
boards' grain, the footing's stones, the runner's furrows, the vat's staves and hoops, the cog wheel's pegs, the buckets,
the sacks' weave, the scale's chains, the cat's stripes and whiskers). Pokes: the cat lashes its tail and wiggles, then
pounces at the hole and comes back; the scale's beam tips and swings back, then the other way, a weight hopping.

**Its effects**, each documented in the painter's class comment and in SCENES.md "Arts": `grind` (the stones stopped,
an easy turn, grinding hard: the wheel, the gears and the runner, the flour running, the water on the wheel), `pour` (grain
poured into the hopper, a sack tipped over its rim while it fills), `sack` (the flour sack filling), `dust` (a puff of
flour over whoever stands by the sack, more motes in the window's beam), `cat` (the cat's pounce, the mouse into its
hole), `lantern`, `weigh` (a bag weighed on the scale), `toll` (the measure filling with grain).

**The way in** (`game/scene/SceneDoors.kt`, `DoorEntries` in `ui/scene/SceneScreen.kt`): the scenes open from the
landmark, `from: ["project:mlin"]`, so only once the mill grinds.

| Where | Before (the mill finished) | After |
|---|---|---|
| The village map: a tap on the mill | the projects sheet at the mill: no way in | its place card, "🚪 Vstopi · Go in: 🌾 V mlinu · In the mill ›" (2 taps in) and "🏗️ Skupni projekti ›" |
| The stream (Ob potoku, Al ruscello): a tap on the mill | its word (mlin) only: no way in | its word and under it "🚪 Vstopi · Go in: 🌾 V mlinu ›" (2 taps in) |
| Before the mill grinds | | the map's tap opens the projects as before; the stream draws the mill as far as it is built, scenery not to tap: no door anywhere |
| Back from the mill | | to where it was gone into from: the village from the card; from the stream "‹ Ob potoku · By the stream" (the mill lies on the stream in the back stack: `Screen.Scene.from`), then the village |

**The content**, all machine-written and flagged in every file's `review` for a native speaker's check:

| | Slovene (`companion/scenes/v-mlinu.json`) | Friuli (`companion/cultures/friuli/scenes/nel-mulino.json`) |
|---|---|---|
| People | Mlinar France (stones), Tine (sacks), Babica Micka and Pastir Luka (door) | Mugnaio Franco, Lorenzo, Nonna Rosa and Pastore Davide |
| Happenings | 8: the morning start (dawn; the wheel iced in December–February), grinding (how it works, the gear under the stones; the people counted), the harvest weeks (July–September), a sack too heavy (flour over everyone), the cat and the mouse (a tap turn), the miller's toll (Luka's corn: merica), Micka's wheat for potica, the evening (the lantern, the stones stopped) | 8: the same day and year in Italian: avvio, macina (a counting variant), raccolto, pesante, gatto (a tap turn), molenda (Davide), polenta (Rosa's maize), sera |
| Dialogs | 18 (8 in `dialogs`, 10 variants), 36 learner's turns, 71 wrong choices, each with a reaction in sl/en/de/it and a why naming the grammar book's page where it is a rule | 16 (8 + 8), 35 turns, 40 wrong choices, 29 naming an Italian page |
| Words | `companion/packs/v-mlinu.json`: 18 (mlinski kamen, koš, žito, vreča, gred, zobnik, mlinsko kolo, žleb, merica, miš, sito, mlinar, bel, prazen, meljem, stresem, tehtam, nesem), sl/en/de/it with examples; the other things reuse moka, tehtnica, klop, okno, vrata, lestev, mačka, svetilka | `companion/cultures/friuli/packs/nel-mulino.json`: 18 (macina, tramoggia, farina, asse, ruota dentata, canale, bilancia, misura, molenda, topo, setaccio, scala a pioli, mugnaio, macinare, versare, pesare, pesante, pieno) with sl/en/de; the rest from the friuli packs |
| I spy | `companion/ispy/scenes/v-mlinu.json`: 90 clues for the 19 things | `companion/ispy/scenes/nel-mulino.json`: 78 clues |

Registers as the cast has them (France and Micka: the learner says vi; Tine and Luka ti; Franco: Lei, "signor Franco";
Lorenzo, Rosa, Davide tu), everything that agrees with the learner a gender pair, `{learner}` for the name. Every effect is
used in each scene, and every visible action a line describes is cued on that line or the reply that says it.

## Besides the content and the painter

- `SceneArt.kt` and the bridge's mirror (`bridge/src/scenes.ts`): the art `mill`; the arts' version 3 (`SceneArt.VERSION`,
  `SCENE_ARTS_VERSION`, `ADDED`): an app of version 2 doesn't get the mill's scenes (`forApp`), so an older app never gets
  a place it can't paint. `bridge/src/project-steps.ts` `NOT_ALWAYS`: `mill` (nothing comes and goes).
- The back stack (`Screen.Scene.from`, Nav.kt's `destination` and routes, `AppViewModel.openScene(from = …)`): a scene
  gone into from another's picture lies on that one, Back returns to it, and it is saved across the process's death so;
  the scenes the village opens lie side by side as before (`NavStackTest`).
- `Soundscape.SPOTS`: the mill (the water on the wheel heard through the walls, a few birds by day).
- `WordStickers.STAND_INS`: the mill's words whose emoji only stands in (the stone's rock, the hopper's triangle, the
  wheel's ferris wheel …) show their sticker cut from the mill.
- Tests: `MillRenderTest` (the mill for review at every detail, by day and at night, the seasons, the weather, each effect
  from 0 to 1, the pokes; the stones and the wheels still at `grind` 0 and turning otherwise; each effect's levels; the
  people and every thing at every detail); the arts' generic tests take the new art (ScenePaintersTest, SceneDetailTest,
  SceneEffectsTest, SceneWeatherTest, ScenePokesTest, SceneSightsRenderTest, SceneWorldRenderTest, PeopleClearTest,
  SoundscapeTest …); `ScenesContentTest` (19 curated scenes, the mill from `project:mlin`, a project's landmark a place),
  `CultureScenesTest` (Nel mulino, every effect used), `DialogReactionsTest` (19 + 16 scene files), `WordStickersTest`;
  the bridge smoke (22 arts, 19 curated scenes, the mill from its landmark, an app before the mill gets no mill).
- QA: `companion/bin/qa --steps mill`.
- companion/SCENES.md, companion/GAME.md ("Landmarks to step into"), .claude/skills/lani-studio/reference/module-spec.md.

## How to use

Nothing to switch on: once the village's mill project is finished, its card on the map and the stream's mill lead in.
The scenes go out with the bridge (an older bridge doesn't know the art and skips them; an app before this one, of
version 2, doesn't ask for them); the I spy clues and the painter come with the next app build.

## What is left

- **A native speaker's check** of the Slovene, the Italian and the German (the `review` notes list the words to look at:
  koš, gred, zobnik, žleb, merica; l'asse, il canale, la misura, la molenda; Trichter, Achse, Rinne).
- **No stage**: nobody walks to the hopper or climbs the ladder; the dialogs say only what the picture shows.
- **The other cultures** (Kärnten, Lakeland) have no scenes of their everyday places yet, the mill among them.
