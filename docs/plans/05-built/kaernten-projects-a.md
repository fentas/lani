# Built: the Carinthian village's third wave, part a (kaernten-projects-a, 2026-11-01)

Jan, 2026-11-01 ("can you make app complete - all what is missing?", followups-6 §5–6): Kärnten, the German village for
learners of German in the lower Gailtal below the Dobratsch, had its buildings' fifteen scenes (places-kaernten), but its
thirteen village projects had no scene of their own, no project steps (each step was the practice it always was) and no
Carinthian look on their places' arts, and its wood had no scene: a tap there opened nothing. This branch does seven of the
projects and the wood; `kaernten-projects-b`, at the same time, the other six, the stream bank and the pond. The rules are
companion/SCENES.md's ("Scenes of a culture pack", "The village's places", "Project steps", "Culture looks").

Two helpers wrote the content, each on a branch of its own merged into this one: `kaernten-projects-a-1` (the wood, the
Maibaum, the mill, the bridge) and `kaernten-projects-a-2` (the Stockbahn, the Wegkapelle, the orchard, the bee meadow). The
looks, the tests, the docs and the QA are this branch's own.

## The way in, before and after

| A Carinthian learner taps … | Before | Now |
|---|---|---|
| the wood | a tap on nothing | its card's "🚪 Geh hinein · Vstopi: 🌲 Im Wald" |
| a finished project's landmark: the Maibaum (in May), the bridge, the mill, the Stockschießbahn, the Wegkapelle, the orchard, the bee meadow | the projects sheet; no way in | its card's "🚪 Geh hinein": Unter dem Maibaum, An der Brücke, In der Mühle, Auf der Stockbahn, Bei der Wegkapelle, Im Obstgarten, Auf der Bienenwiese |
| a step of one of the seven projects | the practice it always was | a short scene with the project's people, where it happens (below) |

## The scenes (German with Slovene, English and Italian, for an adult)

Every scene machine-written, its `review` saying so; SCENES.md "Scenes of a culture pack" has a row for each.

| Scene (art, from) | Level | People | Happenings | Dialogs | Turns | Wrong choices (naming a page) | Tap turns | Counting | Pack | I spy things / clues |
|---|---|---|---|---|---|---|---|---|---|---|
| `im-wald` Im Wald (`forest`; the wood) | A1 | Hirte Florian, Oma Resi, Opa Sepp, Lena, Imker Stanko | 6 | 12 | 31 | 34 (18) | 3 | 2 | 14 | 24 / 95 |
| `unter-dem-maibaum` Unter dem Maibaum (`square`; `project:mlaj`, in May) | A1 | Hirte Florian, Mostbauer Stefan, Maxi, Jakob | 6 | 12 | 29 | 30 (18) | 1 | 1 | 8 | 15 / 66 |
| `in-der-muehle` In der Mühle (`mill`; `project:mlin`) | A2 | Müller Franz, Jakob, Oma Resi, Mostbauer Stefan | 7 | 14 | 33 | 35 (19) | 2 | 1 | 14 | 19 / 76 |
| `an-der-bruecke` An der Brücke (`stream`; `project:most`) | A1 | Müller Franz, Schmied Toni, Hirte Florian, Imker Stanko | 7 | 15 | 26 | 27 (19) | 1 | 2 | 8 | 15 / 70 |
| `auf-der-stockbahn` Auf der Stockbahn (`bocce`; `project:balinisce`) | A2 | Opa Sepp, Müller Franz, Mostbauer Stefan, Imker Stanko | 5 | 13 | 26 | 30 (24) | 1 | 1 | 12 | 15 / 65 |
| `bei-der-wegkapelle` Bei der Wegkapelle (`chapel`; `project:kapelica`) | A1 | Tante Mitzi, Oma Resi, Lena, Schmied Toni | 6 | 12 | 24 | 26 (19) | 1 | 1 | 11 | 15 / 66 |
| `im-obstgarten` Im Obstgarten (`hills`; `project:vinograd`) | A1 | Mostbauer Stefan, Opa Sepp, Maxi, Lena | 6 | 12 | 24 | 24 (21) | 0 | 1 | 11 | 14 / 60 |
| `auf-der-bienenwiese` Auf der Bienenwiese (`apiary`; `project:cebelji_travnik`) | A1 | Imker Stanko, Oma Resi, Lena, Maxi | 6 | 12 | 24 | 25 (19) | 1 | 1 | 9 | 13 / 54 |
| **8 scenes** | 6 A1, 2 A2 | | **49** | **102** | **217** | **231 (157)** | **10** | **10** | **87 words** | **130 things, 552 clues** |

Across them: every slot of each art a thing, once; every effect of each art cued, but the wood's felling (`fell`,
`fell-oak`: the maypole's and the hayrack's steps play them) and the stream's pump and laundry (`hose`, `sheet`: the stream
bank's), as CultureScenesTest's SEEN has them; something on in every month; faces where a line carries a feeling, a
`puzzled` reaction on the form turns, a why in Slovene, English and Italian on every wrong choice and a reaction that never
gives the right form away; registers as the cast has them (Sie and "Herr Franz", "Herr Toni"; du with everyone else); Opa
Sepp, the storyteller, never in an evening happening, no child in one, never Most for a child; two people at one spot never on
at once. The words a scene shares with another are that scene's pack's (one card wherever a word is met): the square's are
Auf dem Dorfplatz's, the bee meadow's Beim Bienenhaus's, the stream's Am Bach's (kaernten-projects-b), the wild animals new
to Kärnten Im Wald's (der Igel, der Dachs, der Siebenschläfer, der Hirsch, das Eichhörnchen, die Eule: Am Bach and Am Teich
name them), die Mühle In der Mühle's.

## The project steps

`companion/cultures/kaernten/project-steps/`, every step of seven projects at A1 and A2, each declaring the words of its
project's pack its turns test; the projects' packs named in German (`projekt-maibaum`, `projekt-bruecke`, `projekt-muehle`,
`projekt-stockbahn`, `projekt-wegkapelle`, `projekt-obstgarten`, `projekt-bienenwiese`: a pack's id is one across all the
cultures, as the app copies them into one directory). Each step's dialog does what its chronicle line in `projects.json`
says; its texts name only the project's leader, its helpers' lines never say who speaks.

| Project | Steps | Tellings | Lines | Where played | `done` lines | Pack (words) |
|---|---|---|---|---|---|---|
| `mlaj` Der Maibaum (Florian) | 5 | 10 | 62 | the spruce chosen and felled in Im Wald (the `fell` cue), then on Auf dem Dorfplatz (peeled, the wreath, raised with the Schwaiberl, the Maibaumkraxeln) | 10 | `projekt-maibaum` (12) |
| `most` Die Brücke (Franz) | 6 | 12 | 71 | Am Bach, the logs in Im Wald | 8 | `projekt-bruecke` (12) |
| `mlin` Die Mühle (Franz) | 6 | 12 | 72 | Am Bach | 12 | `projekt-muehle` (13) |
| `balinisce` Die Stockschießbahn (Sepp) | 5 | 10 | 63 | Auf dem Dorfplatz, under the linden | 0 | `projekt-stockbahn` (12) |
| `kapelica` Die Wegkapelle (Mitzi) | 6 | 12 | 72 | over the village (its road has no scene), the bell's cue | 0 | `projekt-wegkapelle` (13) |
| `vinograd` Der Obstgarten (Stefan) | 7 | 14 | 85 | Auf dem Feld | 0 | `projekt-obstgarten` (17) |
| `cebelji_travnik` Die Bienenwiese (Stanko) | 6 | 12 | 74 | Auf dem Feld (mowing, sowing), Beim Bienenhaus (the hives, the painting, the bee house, the honey) | 0 | `projekt-bienenwiese` (15) |
| **7 projects** | **41** | **82** | **499** | | **30** | **94 words** |

Where a step plays in a scene whose painter draws its project (the maypole on the square and its spruce in the wood, the
bridge and the mill by the stream: `DRAWN_PROJECTS`), its `done` line shows the work done in the picture from that line on;
the bridge's log step (the wood doesn't draw the bridge) and its first cart (as Primorska's) have none. The other four
projects' places aren't drawn by the scenes their steps play in, so none of their lines is a `done`.


## Carinthia's look of the projects' arts

The culture-look hook (`game/scene/KaerntenLooks.kt`: seven tables of their own, `SQUARE_MAIBAUM`, `STREAM_BRIDGE`, `MILL`,
`BOCCE`, `CHAPEL`, `HILLS`, `FOREST`, added to `PLACES` on a line of their own beside kaernten-projects-b's) draws each art a
project or the wood opens its own way. Every change is behind a variant only Carinthia's look sets: the default culture's
pictures are pixel for pixel as before (`CultureLooksTest`'s digests), and so are Friuli's (`FriuliOutdoorLooksTest`,
`FriuliPlacesLooksTest`): where a painter asked only whether Friuli's variant was set (the stream's `cartbridge`, the chapel's
`village`, the forest's `shrine`) it now asks for Friuli's value. SCENES.md "Culture looks" has a row for each.

| Art (scene) | Carinthia's look draws | Instead of (the default, Primorska) |
|---|---|---|
| `square` (Unter dem Maibaum) | the Maibaum as the square's (peeled, its green top, the wreath, red and white ribbons) and under the wreath the Kraxler's prize for the Maibaumkraxeln: two strings of Hauswürstel and a slab of Speck | the mlaj with nothing under its wreath |
| `stream` (An der Brücke) | the cart bridge's logs, deck and railings of reddish-brown larch (the map's bridge's colours) on the art's stone piers, at every stage | the art's timber |
| `mill` (In der Mühle) | walls of larch logs with pale lime chinking over the stone footing; Heiden (buckwheat), brown-grey, in the hopper and the Metzen; the grey Heidenmehl; the Karawanken through the window | plank walls, golden wheat, white flour, a spruce and hills |
| `bocce` (Auf der Stockbahn) | the Stockbahn, as the map's court: grey asphalt between the boards, ice from December to February; Eisstöcke (a round flat body with its long handle, red and green) slid along the lane, never thrown; the Daube, a black rubber disc; a straw broom; WIR \| IHR on the slate; the Gasthaus under dark shingles with a golden linden leaf on its sign; hops on the Laube; Most or apple juice in the jug | the sand court, the metal balls thrown in an arc, the jack, the rake, MI \| VI, red tiles and the grapes sign, the vine, red wine |
| `chapel` (Bei der Wegkapelle) | the turret under an onion cap of dark shingles; Heiden in the field (white and pink in flower in July and August, rust-red in September); larches along the road; the far village white and timbered round its onion-domed church | the turret's little gable roof, wheat, no trees along the road, a Slovene village with its spire |
| `hills` (Im Obstgarten) | Mostbauer Stefan's orchard, as the map's: old apple and pear trees apart in the grass (blossom in April and May, apples red from August); the near vine a young apple tree at its stake, an apple on its low branch; a pear tree for the kaki; larches for the cypresses; a Schragenzaun for the dry-stone wall; Steigen of apples; the farmstead timbered under dark shingles and the wooden Most press by the track; the church's onion dome; a lake for the sea; no lavender, meadows and woods on the far hills | the vine rows of Brda, the cypresses, the kaki, the terrace wall, the lavender, a white farmstead under red tiles, the spire, the sea |
| `forest` (Im Wald) | larches among the spruces at the clearing's edges (gold in October, bare in winter); by the path a Marterl, a larch post with a painted plaque of the Madonna under a little shingle roof, the season's flowers tied to it | leafy trees only; no wayside shrine |
| `apiary` (Auf der Bienenwiese) | Beim Bienenhaus's look as it was (the bunting red and white, HONIG over MED), the meadow as the art draws it | |

No slot, stage spot, effect or art is new: an older app plays the new scenes in the Slovene picture (`SceneArt.VERSION`
unchanged). The Most press and the Marterl are scenery, so a line may mention them but never cues a look at them. The
`inn` stays Friuli's look only (`A_LOOK_ONLY`): Carinthia's Gasthaus by the Stockbahn is scenery, as the Slovene inn is.

**Against the brief, with sources:** the Stockbahn is asphalt, not packed earth. Village lanes for Stockschießen in summer are
asphalt, flooded for ice in winter, and summer Stockschießen grew out of the winter game on ice
([LK Salzburg](https://sbg.lko.at/eisschützen-haben-jetzt-hochsaison+2400+3963479),
[an Asphalt- und Eisstockbahn](https://www.kitzbueheler-alpen.com/de/stjo/infra/a-z/gasthof-mitterjager-asphalt-eisstockbahn.html)).

<!-- CHECKS -->

## How to use

Nothing to switch on. The scenes, their packs, the project steps and their packs are content: a Carinthian village's bridge
serves them as soon as the node's checkout has them (a finished project's landmark then has its card's "🚪 Geh hinein", the
wood's card its way in, and a project's next step is its short scene), the app bundles the I spy clues and the project steps
with its next build, and the looks come with that build too. An older app plays the scenes in the Slovene picture and the
steps as the practices they were; an older bridge never reads `project-steps/`.

To look at the pictures: `KAERNTEN_ARTS=<art,…> ./gradlew :app:testDebugUnitTest --tests '*KaerntenProjectsLooksTest*'`
(without the variable all seven), then `app/build/scene-snapshots/looks/kaernten-projects/`: `<art>-sheet.png` (Primorska on
the left, Carinthia on the right; a row each for the place's month by day, an October evening, January and April),
`<art>-k2.png` and `-k3.png` closer up, `<art>-night-december.png`, `<art>-phone-rain.png`, and
`stream-bridge-stages.png` (the bridge at each of its steps).

QA, with a synthetic learner of German (never a real learner's data):

```
bun companion/bridge/test/fixtures/make-learner.ts <dir> --name Jan --target de --base sl
LANI_DEV_DATA=<dir> LANI_ENV_FILE=/dev/null LANI_CULTURE=kaernten LANI_QA_SETUP_PAIR=de-sl \
LANI_QA_KAERNTEN_PROJECTS_SHOTS=<shots> companion/bin/qa-slot --no-town --steps kaernten-projects-a
```
