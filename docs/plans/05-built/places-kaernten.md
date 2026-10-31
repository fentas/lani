# Built: the Carinthian village's places (places-kaernten, wave 2, 2026-10-31)

Jan, 2026-10-31 ("ok do all", the third batch of follow-ups, 7): Kärnten, the German village for learners of German in the
lower Gailtal below the Dobratsch near Villach, had two scenes, the fire and the horizon (Am Feuer, Am See), against
Friuli's 33. Since map-culture its map had its own look (the Harpfe, the onion-domed church, timbered white houses under
shingles, Austria's flag), but its buildings had no scene: a building's card had no "🚪 Hineingehen · Go in", and the arts
those buildings would open drew the Slovene picture. This branch gives every building of the Carinthian village its scene
and every art they open Carinthia's look. The rules are companion/SCENES.md's ("Culture looks" has the looks' rows,
"Scenes of a culture pack" the scenes' rows, "Seasons" a row). Two helpers wrote the scenes, each on a branch merged into
this one: `places-kaernten-rooms` (the kitchen, the Stube, the cellar, the attic, the workshop, the smithy, the school,
the tent) and `places-kaernten-outdoors` (the field, the square, the market, the bee house, the church, the gate, the
watchtower); the looks, the tests, the docs and the QA are this branch's own.

<!-- SCENES -->

## Carinthia's look

The culture-look hook (`game/scene/CultureLook.kt`, the `kaernten` tables beside Friuli's; SCENES.md "Culture looks")
draws every art a Carinthian building opens its own way. The default culture's pictures are pixel for pixel as before
(`CultureLooksTest`'s digests) and so are Friuli's (`FriuliOutdoorLooksTest`'s digests, the Friuli looks tests); an art no
look touches draws the same in every culture.

| Art | Carinthia's look draws | Instead of (the default, Primorska) |
|---|---|---|
| `kitchen` | the Sparherd in white tiles from level 2 (its iron top and brass rail the art's); the Herrgottswinkel from level 3, the Palmbuschen (willow catkins, box, red, yellow and white ribbons) behind the crucifix; the Kredenz in plain panels of pale Zirbe; the Karawanken through the window | the green-tiled stove, the holy corner's picture, the painted blue dresser, a spruce and green hills |
| `livingroom` (the Stube) | the Kachelofen, whitewashed at level 1, from level 2 of dark green bowl tiles (Schüsselkacheln) in white lime with its rail of socks and the Ofenbank; the walls panelled in honey-coloured Zirbe; the Herrgottswinkel over the corner bench; the Karawanken | the kmečka peč's square green tiles, the painted dado, the holy picture |
| `cellar` | Mostbauer Stefan's Mostkeller: apples, red and yellow, in the crate; the Most golden (the tap's run, the jug, the balloon's neck, the glass, golden-green bottles); a slab of Speck and a loop of Hauswürstel; the Karawanken through the slit | dark blue grapes, red wine, the pršut |
| `attic` | larch shingles from below at level 1 (grey-brown courses, a chink of daylight); strings of Kletzen (dried pears) and a bundle of herbs from the ridge beam; the Karawanken | red tiles between the battens |
| `workshop` | walls of brown larch logs with their pale chinking (a log house); a stack of fresh shingles and the froe by the broom; the Karawanken | whitewash |
| `smithy` | rough stone whitewashed over (worn here and there, sooty over the forge); a horseshoe over the door, its ends up | bare rough stone |
| `school` | the map of Kärnten from level 2 (the Hohe Tauern, the Karawanken, the Drau and four lakes, Villach, Klagenfurt, the title KÄRNTEN); Austria's flag from level 3; a small wooden cross over the blackboard; "Grüß Gott! / Wie geht's? / Gut!" on the board before the first lesson (the chalk has its ß now) | the map of Slovenia, Slovenia's flag, "Dober dan!" |
| `tent` | Austria's pennant, red, white and red; a Schragenzaun of slanted grey poles along the meadow's back edge | no pennant, an open meadow |
| `field` | the Harpfe for the kozolec: taller, nine thin poles close together like a harp's strings, larch weathered silver-grey, a steeper roof (the art's levels: one bay to three, boards, shingles); the village on the hill white with timbered upper storeys and geranium balconies under dark shingles round the church with its onion dome; farmsteads with little Harpfen | the kozolec, a Slovene village with its spire |
| `square` | the houses white below, a timbered upper storey with a geranium balcony, dark shingles; the church tower's onion dome of green copper with its lantern; Kathi's Gasthaus zur Linde and its wrought-iron sign (a golden linden leaf); the village's wooden fountain for the well, by WELL's level (a log trough and a pipe on a stake; the long trough on stones and its spout post; the carved post, a ladle and the flagged ground; a shingle roof; geraniums and the lamp post); the Maibaum's ribbons red and white | plastered houses under tiles, the spire, the stone well with its windlass, the Slovene ribbons |
| `market` | the houses as the square's, the onion dome; slabs of Speck and garlic on the village stall's back beam, a Reindling by the loaves, Stefan's Most golden in green bottles | the Slovene square's houses and spire, garlic and peppers, green wine bottles |
| `apiary` | Stanko's bee house as the art's (the painted fronts are a Carinthian Slovene's tradition too); World Bee Day's bunting red, white and red; his sign by the path, HONIG over MED | the Slovene bunting, no sign |
| `church` | from level 3 a Gothic net vault over the far wall: white, red-ochre ribs crossing in diamonds, gold bosses | the blue starry vault |
| `palisade` (the gate) | larches along the road (gold in October, bare in winter) and a spruce, a Harpfe on the meadow, sharper and higher mountains, a white village round its onion-domed church on the far hill | spruces and a plain ridge |
| `watchtower` | the village below white with timbered upper storeys and geranium balconies under dark shingles, huts of logs, the white church tower with its onion dome; Austria's flag (its pennant below level 4) | a Slovene village, the Slovene flag |

Shared pieces: RoomPainter's Karawanken view (`view` "karawanken"), its Zirbe panelling (material `dado` "wood"), the
Palmbuschen of the Herrgottswinkel (`corner` "herrgottswinkel") and the stove's tile colours (`tile.l`, `tile.m`,
`tile.d`); VistaPainter's far Carinthian house (`farAlpineHouse`), far onion church (`farOnionChurch`), larch (`larch`) and
Harpfe in the open (`harpfe`). No slot, stage spot, effect or art is new: an older app plays every new scene in the
Slovene picture (SceneArt.VERSION unchanged, nothing for `forApp` to leave out).

**The map agrees** (`MapLooksTest`, "the map shows what the scenes show"): the field's Harpfe is the map's hayrack, the
tent's and the watchtower's flags are the map's, the square's wooden fountain is the map's well (the trough), the bee
house's bunting is the feasts' red and white, and the field, the square, the market and the watchtower draw the fire's
village (FireStyle) that the map's houses are.

<!-- CHECKS -->

## How to use

Nothing to switch on. The scenes, their packs and their I spy clues are content: a Carinthian village's bridge serves the
scenes as soon as the node's checkout has them (the building's card then has its "🚪 Hineingehen · Go in"); the app bundles
the clues, and the looks come with the next app build. An older app plays the scenes in the Slovene picture (no new art id
or slot). On a visit to a Carinthian town the visitor walks into the same places, in the host's look.

To look at the pictures: `KAERNTEN_ARTS=<art,…> ./gradlew :app:testDebugUnitTest --tests '*KaerntenLooksTest*'` (without the
variable every art), then `app/build/scene-snapshots/looks/kaernten/`: `<art>-<building>-<day|evening>.png` (Primorska on
the left, Carinthia on the right, a row a level), `<art>-L<level>-k<detail>.png` (closer up), `<art>-night-winter.png`,
`-december`, `-october-dusk`, `-lent`, `-phone-L2-rain`, `-phone-L5`. `BuildingLevelsTest`'s sheets
(`app/build/scene-snapshots/levels/`) have a column for Carinthia beside the default and Friuli.

## Tap counts

| Into … | Before | Now |
|---|---|---|
| a building's scene (the field, the Harpfe, the linden or the well, the market, the bee house, the church, the school, the smithy, the tent, the palisade, the watchtower, the hut's kitchen, a house's room) | no way in: the card had no "🚪 Hineingehen" | 2 taps: the building, "🚪 Hineingehen · Go in: <its scene>" |
| the fire, the horizon | 2 taps, as before | 2 taps |

## What is left

- **The native check** of everything German (and its Slovene, English and Italian): the fifteen scenes, their packs and
  their I spy clues are machine-written, each file's `review` says so and names what to look at (the Austrian words, the
  dialect touches, the Carinthian Slovenes' Slovene).
- **The projects, the third wave**: Kärnten's thirteen village projects (`projects.json`) have no scene of their own yet,
  no project steps (they are played as the practice they always were) and no look on their places' arts:

  | Project | What it is | Its scene would be on | What its look needs |
  |---|---|---|---|
  | `mlaj` | Der Maibaum (Florian) | `square` | the Maibaum's top left green, its wreath; the ribbons are red and white already |
  | `most` | Die Brücke über den Bach (Franz) | `stream` | the cart bridge of larch, as the map has it (`map` `bridge` "larch") |
  | `mlin` | Die Mühle am Bach (Franz) | `mill` | the mill of logs under shingles, a buckwheat sack (Heidenmehl) |
  | `balinisce` | Die Stockschießbahn (Sepp) | `bocce` | the court as a Stockschießbahn: a lane of packed earth (ice in winter), the Eisstöcke with their handles, as the map's `court` "curling" |
  | `kapelica` | Die Wegkapelle (Mitzi) | `chapel` | a white Carinthian wayside shrine under shingles |
  | `vinograd` | Der Obstgarten (Stefan) | `hills` | old apple and pear trees in the grass for the vines (the map's `vineyard` "orchard"), the Most press |
  | `cebelji_travnik` | Die Bienenwiese (Stanko) | `apiary` | the meadow as the art's |
  | `gasilski_dom` | Das Feuerwehrhaus (Florian) | `firestation` | the FF sign, Austria's flag, St Florian (the art's already) |
  | `igrisce` | Der Spielplatz (Eva) | `playground` | the school's wall in Carinthia's look |
  | `vodnjak_na_trgu` | Der Dorfbrunnen (Toni) | `square` | the fountain carved of wood or stone, as the village's own trough |
  | `toplar` | Die Doppelharpfe (Franz) | `field` | the double hayrack as a Harpfe of grey larch (the field draws the Slovene toplar now) |
  | `razgledni_stolp` | Der Aussichtsturm (Kathi) | `lookout` | the view of the Gailtal: the Dobratsch, the Karawanken, the Drau, Villach |
  | `mestna_ura` | Die Kirchturmuhr (Eva) | `square` | the clock in the onion-domed tower (the art's clock is drawn in it already) |

- **The spots**: the wood, the stream bank and the pond have no Carinthian scene (Friuli's are Nel bosco, Al ruscello,
  Allo stagno), so a tap on them in a Carinthian village is a tap on nothing; nor has the charcoal pile's burner a scene.
- **The building's words**: `BuildingWords.PACKS` still has Kärnten's buildings ask for their generic packs (essen, im-dorf,
  haus-und-kueche …) when they are upgraded; Primorska's ask for their scenes' packs. Switching Kärnten's to its scenes'
  packs is a small change of its own (and Friuli's the same).
- **The Fastentuch** (Gurk's, Millstatt's), the Lenten veil before the altar, would cover the altarpiece (a word of the
  scene) for six weeks: left out of the church's look until a thing of the church may come and go by the season
  (`SceneSights`).
- **Voices**: the new lines get their clips as any scene's (`voice-build`, Kärnten's voice cast).

