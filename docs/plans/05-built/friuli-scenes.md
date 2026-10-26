# Built: the Friuli village's places to step into

A child learning Italian from German on the Friuli culture (profile `teo`, A1) built a tent and found no way into it: no
"Vstopi · Go in". The Friuli culture had two scenes, the fire (Al fuoco) and the sea at the horizon (Il mare); the
Slovene village has twenty. A village of a language without curated scenes (`companion/scenes/<language>/`) gets only
its culture pack's own, so every building and spot of the Friuli village but the fire was a card without a door.

The format and the rules are companion/SCENES.md's ("Scenes of a culture pack" has the new rows). This note says what
was written, how each place is reached, how it was checked, and what is left.

## What

Seven scenes in `companion/cultures/friuli/scenes/`, each in the art of its Slovene scene (so the same object slots,
the same `needs` on the slots that grow with a building or a project, the same stages), in Italian with Slovene,
English and German, A1 and written for a child:

| Scene | Art | Reached | People (the Friuli cast) | Happenings, each with two dialogs |
|---|---|---|---|---|
| `nella-tenda` Nella tenda | `tent` | the tent: buildable from Falò, needed for Accampamento | Pastore Davide, Lorenzo, Giulia | a lantern in the evening; shelter from the rain (a summer thunderstorm: by the lantern, staying); a bedtime story (Lorenzo falls asleep); Giulia's treasure map (a tap turn) |
| `in-cucina` In cucina | `kitchen` | the hut (Accampamento) and a house (Borgata), growing with its level | Nonna Rosa, Zia Nives | polenta, or polenta and frico; breakfast (a counting variant: uovo, uova); the radio (the bora, the Udinese match) |
| `nel-campo` Nel campo | `field` | the field (Falò, needed for Borgata) and the hay barn (Accampamento) | Mugnaio Franco, Lorenzo | the scarecrow (the hat; a scarf in the cold months); the wheat harvest in summer; Franco's horse Baio |
| `nel-bosco` Nel bosco | `forest` | the wood, from the first day | Pastore Davide, Apicoltore Aldo, Nonno Bepi | Bianca the lamb lost (in the fog in winter); mushrooms in autumn (counted); Bepi on the high seat at dawn (the deer; the owl, a tap turn) |
| `al-ruscello` Al ruscello | `stream` | the stream bank, from the first day | Mugnaio Franco, Nonna Rosa, Pastore Davide | the mill Franco dreams of (until the mill project `mlin` grinds); the laundry; the sheep drinking (counted) |
| `allo-stagno` Allo stagno | `pond` | the pond, from the first day (the tent moves there at Borgata) | Apicoltore Aldo, Tommaso, Nonno Bepi | the heron in the mist (or the ducks); a frog caught and let go (the tadpoles in spring); fishing with Moro the dog |
| `nella-fucina` Nella fucina | `smithy` | the smithy (Borgata), growing with its level | Fabbro Bruno, Tommaso | a horseshoe (counted in the bucket); the bellows from level 2 (or while Bruno has his coffee); an order from Gorizia in his black notebook |

In all: 125 objects (every slot of each art a word), 22 happenings, 44 dialogs, 98 learner's turns with 119 wrong
choices, each with the person's reaction in all four languages and a why in Slovene, English and German (a rule of the
Italian grammar book where it is one: articles and gender, the articulated prepositions, the prepositions of place,
tu and Lei, essere and avere, the present, the irregular verbs, the plural, c'è and ci sono, piacere, questions and the
negative); four counting dialogs (uova, funghi, pecore, ferri di cavallo); two tap turns. The registers are the cast's:
tu to everyone but signor Franco and signor Bruno. What agrees with the learner is a gender pair, so it reads for a boy
or a girl. The happenings name only their person; Nonno Bepi's happenings are at dawn and in the afternoon, never in
the evening, which is his story's by the fire.

Eight word packs in `companion/cultures/friuli/packs/` (126 words, `it` with its article, `sl`, `en`, `de`, examples in
all four, notes in English, Slovene and German): `nella-tenda` 19, `in-cucina` 17, `nel-campo` 16, `animali-del-bosco` 14
(the wild animals of the field, the wood, the stream and the pond), `nel-bosco` 15, `al-ruscello` 14, `allo-stagno` 14,
`nella-fucina` 17. Where the Italian packs had a thing's word already, the scene names that pack (`casa-e-cucina`'s table
and cutlery, `cibo`'s bread, milk and eggs, `al-fuoco`'s fire, bucket and axe, `il-mare`'s boat and fish, `leggende`'s
rock and sheep, a festival pack's blanket, basket, leaf and flower), so a word is one card wherever it is met.

I spy clues for every thing of the seven scenes (526 clues, in Italian with Slovene, English and German, from the
pictures as the Slovene scenes' clues describe them), in `companion/ispy/scenes/`.

Everything is marked machine-written: the scenes' and the packs' `review`, the clues' `machine_written`. Nothing is
voiced here: the node voices the lines when they are first played (the culture's voice cast).

## What changed besides the content

- `DialogReactionsTest` counts the scene files: 18 + 15 (was 18 + 8).
- `BuildingWordsTest`: a Friuli hut now asks for its kitchen's things (its pot, window and door first), as Primorska's
  does; the other cultures still ask for their home-and-kitchen pack. (The doc comment of `BuildingWords.PACKS` still
  says the other cultures' villages have no rooms to step into: true now only of Kärnten and Lakeland.)
- companion/SCENES.md ("Scenes of a culture pack": a paragraph and seven rows; "Seasons": a row), companion/GAME.md ("The
  friuli pack").

## How it was checked

- The culture as the bridge checks it at build: `validateCulture` with the cast (13), the Italian packs (34), the
  Italian scenarios (none) and the grammar book's 16 Italian pages: ok. Each scene alone (`validateScene`): ok.
- The content audit (`bun test/learner-content.ts`): 0 texts naming Jan, 0 malformed placeholders, 0 turns the same for
  one gender.
- The prompts (card.py's `prompt_parts`): no two Italian words of the new packs share an English, German or Slovene prompt
  with another Italian word (481 words over the Italian packs; two clashes were fixed: `pontile` and `molo` both
  "pomol" in Slovene, `galleggiante` and Carnival's `carro` both "float").
- The app's content tests, replayed per scene while writing and then run with Gradle on the final content: 161 tests
  in 20 classes, none failing (DialogReactionsTest, VariantsContentTest, ISpyContentTest, ScenesContentTest,
  WordStickersTest, StickersTest, CultureScenesTest, BuildingWordsTest, MentionsContentTest, RoutineTest, SleepTest,
  GrammarTest, LearnerContentTest, IntroductionContentTest, TurnHintTest, HintSpeechTest, VillagersContentTest,
  StormIndoorsTest, SceneWorldTest, VisitsTest).
- A second reader proofread the Italian, German and Slovene of the seven scenes and eight packs; their 32 points are
  fixed (articles with possessives, the causative fa girare, the whys' "for the two of us", German idiom, two Slovene
  duals, a few notes).
- The bridge typechecks (`bunx tsc --noEmit -p .`; no code changed), and the whole bridge smoke passes: 2597 checks,
  none failing, its culture sections among them (every culture pack validates with its scenes' words in its packs;
  the Friuli village gets no Slovene scenes; all 34 Italian packs validate).
- No QA screenshot: companion/bin/qa plays the Friuli village only as a town visited, with no step for its scenes.

## How to use

Nothing to switch on: the bridge serves a culture pack's scenes to its village (`GET /scenes`), so the Friuli village
gets them on its next fetch once this is merged and the node's checkout has it. The I spy clues are bundled in the app
and come with the next build; until then the scenes simply offer no I spy. The tutor writes more variants as for any
scene (`dialog_variants_heard`, `publish_dialog_variant`).

## What is left

- **The other places.** The square (Slovene `na-vasi`, from the linden and the well, Paese), the houses' rooms other than
  the kitchen (the living room, the workshop, the cellar, the attic: they need households of the Friuli cast, and the
  first house gets the room of the first household that moves in), the school (Maestra Elena), the church, the bee house
  (Apicoltore Aldo), the market (Ostessa Marisa, Vignaiolo Matteo) and the watchtower. Until they are written, those
  buildings' cards have no door and their upgrades ask for their culture pack's words (`BuildingWords.PACKS`).
- **The mill once it grinds.** Franco's happening at the stream is the mill he dreams of, `needs` the project `mlin`
  `before`; once the mill is built the stream has Rosa's laundry and Davide's sheep, and a happening of Franco milling
  is still to write.
- **Talk scenarios.** None of the new dialogs has a `talk`: there are no Italian role-play scenarios yet
  (`companion/scenarios/it/`).
- **The course.** There is no Italian course (`companion/course/it/`), so the Friuli school has no units.
- **Project steps and the ages' parties.** The Friuli pack has neither `project-steps/` nor `celebrations/`: its projects
  are practices and its ages' parties have no dialog.
- **A native speaker's check** of the Italian, the German and the Slovene (the `review` notes say so).
