# Built: the Lake District's third wave, part b (lakeland-projects-b, 2026-11-01)

Jan, 2026-11-01: "can you make app complete - all what is missing?" (wave B, batch 1, followups-6 sections 7–8). The Lake
District village (culture `lakeland`: English, for an adult learning English from Slovene, German or Italian) had a scene
for every building since places-lakeland, but none for its thirteen village projects, no project steps, and nothing at its
landscape's spots: a finished project's landmark opened only the projects sheet, a tap on the beck or the tarn opened
nothing, and the arts those projects are drawn on showed Slovenia. This branch does six of the projects and two spots; the
other seven projects and the wood are `lakeland-projects-a`'s, written at the same time.

- **The projects**: the mountain rescue hut (`gasilski_dom`), the playground (`igrisce`), the drinking fountain on the green
  (`vodnjak_na_trgu`), the bank barn (`toplar`), the viewing station (`razgledni_stolp`) and the church clock
  (`mestna_ura`). Each has its scene (opened from its finished landmark), its pack and I spy clues, its project steps at A1
  and A2 (`project-steps/`) with the project's own word pack, and its art in the Lake District's look at every step.
- **The spots**: the beck (`spot:riverbank`, By the beck) and the tarn (`spot:pond`, At the tarn), each with its scene, pack,
  clues, its name on its card ("The beck · Potok", "The tarn · Gorsko jezerce") and a story at A1 and A2 (world.json
  `spots`).

Two helpers wrote the six project scenes, their packs and clues and the 37 steps (content-1: the rescue hut, the
playground, the viewing station; content-2: the fountain, the clock, the bank barn); the beck and the tarn, every look, the
tests, the docs and the QA are this branch's own. All the English, Slovene, Italian and German is machine-written, and
every file's `review` says so.

## The way in, before and after

| A learner of the Lake District taps … | Before | Now |
|---|---|---|
| the finished rescue hut, playground, viewing station, fountain or church clock | the projects sheet: no way in | its card's "🚪 Go in": At the rescue hut, At the playground, At the viewing station, At the fountain, Under the clock (2 taps) |
| the finished bank barn | the projects sheet | At the bank barn (2 taps) |
| the beck (the stream bank) | nothing | its card "🏞️ The beck · Potok", "🚪 Go in": By the beck (2 taps) |
| the tarn (the pond) | nothing | "🪷 The tarn · Gorsko jezerce", "🚪 Go in": At the tarn (2 taps) |
| a project's step | a practice of 5 exercises over the village | its short scene where it happens (on the green, in the field, in the smithy, at school, on the fell, in the workshop) or over the village, at the learner's level |

The doors are content (the scenes' `from`): a Lakeland village's bridge serves them as soon as its checkout has them; the
looks and the I spy clues come with the next app build.

## The scenes

| Scene | Art (level) | From | People (spot) | Happenings | Dialogs | Turns | Wrong (naming a page) | Two right | Tap | Counting | Pack | I spy |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `by-the-beck` By the beck | `stream` (A1) | `spot:riverbank` | Mr Tyson (mill), Granny Maggie (bank), Shepherd Jack, Alfie (ford, never at once) | 6 | 12 | 26 | 49 (30) | 3 | 1 | 1 | 12 | 15 things, 65 clues |
| `at-the-tarn` At the tarn | `pond` (A1) | `spot:pond` | Old Wilf and Harry (jetty, never at once), Beekeeper Arthur (shore), Poppy (reeds) | 6 | 9 | 18 | 35 (17) | 0 | 1 | 1 | 11 | 14, 61 |
| `at-the-rescue-hut` At the rescue hut | `firestation` (A2) | `project:gasilski_dom` | Shepherd Jack (pump: the stretcher), Mr Dixon (hoses: the ropes), Mr Tyson and Harry (door) | 6 | 14 | 31 | 35 (24) | 4 | 2 | 1 | 12 | 16, 62 |
| `at-the-playground` At the playground | `playground` (A1) | `project:igrisce` | Mrs Hartley (bench), Poppy (swing), Alfie and Harry (sandpit) | 6 | 14 | 32 | 34 (25) | 7 | 1 | 1 | 12 | 15, 59 |
| `at-the-fountain` At the fountain | `square` (A1) | `project:vodnjak_na_trgu` | Mr Dixon (fountain), Mrs Hartley (well), Brewer Sam (bench), Alfie and Poppy (play) | 6 | 14 | 28 | 35 (25) | 9 | 1 | 1 | 12 | 15, 70 |
| `under-the-clock` Under the clock | `square` (A1) | `project:mestna_ura` | Mrs Hartley (clock), Auntie Edna and Old Wilf (bench), Mr Dixon (well), Harry (play) | 6 | 13 | 26 | 34 (23) | 8 | 1 | 1 | 12 | 15, 70 |
| `at-the-bank-barn` At the bank barn | `field` (A2) | `project:toplar` | Mr Tyson (field), Shepherd Jack (kozolec), Brewer Sam and Alfie (path) | 6 | 12 | 24 | 34 (25) | 5 | 1 | 1 | 12 | 21, 90 |
| `at-the-viewing-station` At the viewing station | `lookout` (A2) | `project:razgledni_stolp` | Mrs Birkett (board), Harry and Shepherd Jack (rail), Old Wilf (stairs) | 6 | 13 | 27 | 32 (21) | 5 | 1 | 1 | 11 | 16, 51 |
| **8 scenes** | | | | **48** | **101** | **212** | **288 (190)** | **41** | **9** | **8** | **94 words** | **127 things, 528 clues** |

SCENES.md "Scenes of a culture pack" has a row for each with what happens in it. Across them: every slot of each art a
thing, once, with the words of the Lake District's scene of the same picture for the shared ones (On the village green's
oak, pump, church and cottages; In the field's barn, scarecrow and pony); every effect of each art cued (the square's
project effects only where their project is: the fountain's water and the slate on the cart At the fountain, the clock's
strokes Under the clock; the stream's mill wheel, sacks, new bridge's cart and fire pump are lakeland-projects-a's or none:
`CultureScenesTest`'s `SEEN`); people at the art's spots, two at one spot never on at once; something on in every month;
Old Wilf never in an evening happening (his story's); registers as the cast has them (the learner says Mr Dixon, Mr Tyson,
Mrs Birkett and Mrs Hartley, and their lines' Slovene, German and Italian say vi, Sie, Lei; they say ti, du, tu back);
everything that agrees with the learner a `{m:…|f:…}` pair; faces where a line carries a feeling; a reaction and a why in
four languages for every wrong choice.

The beck and the tarn:

- **By the beck**: Granny Maggie's washing on the stone (watercress for the sandwiches; the sheet the wind takes over the
  water, caught), Shepherd Jack's sheep at the ford in the evening (the lamb that won't drink, Sooty always last, the flock
  counted, yan tyan tethera), Mr Tyson watching for trout in the morning (the dipper, which walks under the water; the beck
  high and brown after a night's rain, keep off the bank), Alfie paddling in summer (the dragonfly found with a tap;
  skimming stones), the church bell and the bats at dusk (in the mist), Mr Tyson's mill to come until the mill grinds.
- **At the tarn**: Beekeeper Arthur and the heron's breakfast at dawn (the ducks up out of the reeds), Old Wilf fishing from
  the jetty with Bess (the float, a perch, catch and caught; the catch counted in the bucket), Poppy and the frog on the
  water lily (found with a tap; the tadpoles of spring left in the tarn), Harry's race on weekend mornings (no boat without
  a grown-up), Harry and the ice in winter (never walk on it), the fish and the bats on summer evenings.

## The project steps

`companion/cultures/lakeland/project-steps/<project>.json`, every step at A1 and A2, 4–7 lines, 1–3 turns, the leader and the
helpers speaking (a helper's line never says who says it), each step's turns testing 1–3 words of the project's pack.

| Project | Pack (words) | Steps: where each is played | Done lines (both levels) |
|---|---|---|---|
| `gasilski_dom` the mountain rescue hut (Shepherd Jack) | `project-gasilski-dom` (12) | the foundations and the walls on the green; the door and bunk beds, the stretcher, ropes and radio from Keswick over the village; the first practice on the fell in the mist; the barn dance on the green (the band, the dancing) | 0 (no scene draws the hut being built) |
| `igrisce` the playground (Mrs Hartley) | `project-igrisce` (11) | the children's drawings at school; the stones off the grass, the swing, the sandpit and the slide on the green; the first day of play over the village | 0 |
| `vodnjak_na_trgu` the drinking fountain (Mr Dixon) | `project-vodnjak-na-trgu` (15) | the plan with its lion's head in the smithy; the slate from Honister (the cart), the basin carved, the trench from the spring on the green; the cups forged in the smithy; the water from the lion's mouth on the green | 6 |
| `toplar` the bank barn (Mr Tyson) | `project-toplar` (12) | the oaks cut over the village (the wood is lakeland-projects-a's scene); dug into the hill, the beams, the floor and the roof, the ramp, the first hay, in the field | 10 |
| `razgledni_stolp` the viewing station (Mrs Birkett) | `project-razgledni-stolp` (13) | the finest view, the foundations on the rock, the larch timber, the path with steps up from the lake on the fell; the room, the coloured windows and the first guests over the village | 0 |
| `mestna_ura` the church clock (Mrs Hartley) | `project-mestna-ura` (15) | the face's size at school; the round window on the green; the gears in the smithy; the hands in the workshop; the slate face and its Roman numerals at school; lifted in and striking twelve on the green | 4 |
| **6 projects** | **78 words** | **37 steps, 74 dialogs, 441 lines, 144 turns, 181 wrong choices** | **20** |

A step played in a scene that draws its project says on which line its work is done (`done`), only where the picture
changes at that step: the fountain's slate block, its basin, its trench; the bank barn's walls, beams, roof, ramp and hay;
the clock's window and its face (SceneCoherenceTest, `StepScenesRenderTest`'s new Lake District test, below). The project
packs are named `project-<project>`: the app bundles every culture's packs side by side by file name, so Primorska's
`projekt-<project>` would have collided (SCENES.md's `pack` row says so now, with Kärnten's `projekt-<German name>`).

## The Lake District's look of these places

Each art's table is a new value in `game/scene/LakelandLooks.kt` (added to `PLACES` on its own line, beside the sibling
branch's), drawn by the painters' new variants; every slot, person spot and stage spot is where the default picture has it.
SCENES.md "Culture looks" has a row for each.

| Art | The Lake District's look |
|---|---|
| `stream` (`STREAM_LAND`) | broadleaf woods along the beck, alders and oaks without a spruce; the fells behind, a far one blue-grey, the near one with its walled intake fields, bracken and crags, a whitewashed farm under slate with its barn on its lower slope |
| `pond` (`POND`) | the fells behind the tarn's woods, mirrored in the still water between the woods' picture and the sky's |
| `firestation` (`FIRESTATION`) | the mountain rescue hut: slate stone and slate, MRT over the doors, red helmets and red jackets with a reflective band, two ice axes, the map of the fells, a whistle for the horn, red first-aid bags for the buckets, red and blue rope on the reel, St George's cross; the rescue stretcher on its one wheel where the pump stands, wheeled to and fro by the `pump` effect |
| `playground` (`PLAYGROUND`) | the school's side of slate stone under slate with a white sash window; a dry-stone wall with its cope stones for the picket fences |
| `lookout` (`LOOKOUT`) | inside the stone viewing station at its window: slate jambs, a whitewashed ceiling, the four coloured panes across the window's top (green, yellow, orange, blue: the seasons), St George's cross; the fells round the dale, a pike with two knobbly tops, the lake winding below with its wooded island, the walled green fields, the slate village round its battlemented tower |
| `field` (`FIELD_BANK_BARN`) | the bank barn for the double hayrack, by its steps: logs, the walls dug into the bank, the beams, the slate roof, the ramp, the hay |
| `square` (`SQUARE_FOUNTAIN`, `SQUARE_CLOCK`) | the drinking fountain of green Honister slate (the block and the cart's load too), the lion's head on its pillar with the water from its mouth, the two iron cups on chains; the church clock's face of slate with gold marks and hands |

The painters' Friuli checks are now exact (`== "pioppi"`, `"collio"`, `"pigna"`, `"pannocchie"`, `"piantata"`), so a Lakeland
value never draws Friuli's poplars, vines or pine cone (kaernten-projects-b made the same change; the cherry-pick is
identical). Primorska's and Friuli's pictures are pixel for pixel as before (`CultureLooksTest`'s digests).

## How it was checked

On the branch as it is now (main 75533328 is already in it: the rebase is a no-op until wave A's queue lands):

- **The content**: `bun test/check-culture.ts lakeland` ok; `bun test/check-steps.ts lakeland`: 6 files, 37 steps, 74
  dialogs ok (with kaernten-projects-b's ac44368f and kaernten-projects-a's 1e58bcda cherry-picked: a noun with its article
  and a verb's forms count as tested); `bun test/learner-content.ts`: 0 texts naming Jan, 0 malformed placeholders, 0 turns
  the same for one gender. The prompts of this branch's new words, in every base, against all the culture's packs and the
  English packs: no clash of theirs (41 clashes over 781 English words, all older or lakeland-projects-a's against older
  packs); the clock's gear re-prompted (Zahnrädchen, rotella dentata) apart from a's cogwheel, the dog's lead renamed
  `dog-lead` apart from a's verb "to lead", the bank barn's sack, mouse and oats left to a's in-the-mill.
- **The Android unit suite** (lani-slot.sh): 475 classes, 3,693 tests, 0 failures. New or changed: `LakelandPlacesLooksTest`
  (each art beside Primorska's, every thing tappable once at details 1–3, the people where they stood, each step of the
  fountain, the clock and the bank barn on its picture, Primorska's pictures as before, 29 digests as reviewed);
  `StepScenesRenderTest`'s Lake District test (every done line of these steps changes its picture at details 1 and 2);
  `CultureScenesTest` (the eight scenes, every slot once, every effect of the art but `SEEN`'s: the stream's mill, cart and
  fire pump); `LakelandLooksTest`'s field and square digests (the pictures of everything built now draw the bank barn, the
  slate fountain and clock); `DialogReactionsTest` (31 + 39 + 15 + 23 scene files: no reaction gives the right choice
  away); `SceneCoherenceTest` (66 done lines); `VariantsContentTest`, `ISpyContentTest`, `ISpyRoundsTest`, `WhoAnswersTest`,
  `ScenesContentTest`, `CultureLooksTest` (Primorska's and Friuli's digests unchanged).
- **The bridge**: `tsc` clean; the smoke on ports 9790–9808 (Drama 3 9808): 3,329 checks, all passed, with a new one (one
  English word per word id across the English packs, which found the moon's "to wax" sharing the bees' "wax"); `tutor-export
  --check`: 89 files match (54 tools, 32 guide topics).
- **Python**: 251 tests, and 142 with 11 skipped, all passing.
- **The emulator** (`qa-slot`, slot 1, a synthetic learner of English from Slovene, `LANI_CULTURE=lakeland`): QA's step
  `lakeland-projects-b` green on its fourth run: the rescue hut finished, its landmark's card "🚪 Go in · Vstopi: ⛑️ At the
  mountain rescue hut", inside and closer up, Jack's practice with the stretcher (its first lines and turn); the bank barn's
  card and the field with it; the beck's card "The beck · Potok" with its story and "🚪 Go in · Vstopi: 🏞️ By the beck", the
  scene; the playground, the fountain, the clock, the viewing station and the tarn by their links. The first runs stopped on
  the step itself (a spot has no target on the map: the step now taps along the beck where QA flew the camera). Screenshots:
  the round's scratch folder `ux-lakeland-projects-b/qa4/` (lb-*.png, contact-sheet.png); the looks' contact sheets in
  `ux-lakeland-projects-b/looks/sheets/`.

## How to use

Nothing to switch on. QA with a synthetic learner of English (never a real learner's data):

```
bun companion/bridge/test/fixtures/make-learner.ts <dir> --name Jan --target en --base sl
LANI_DEV_DATA=<dir> LANI_ENV_FILE=/dev/null LANI_CULTURE=lakeland LANI_QA_SETUP_PAIR=en-sl \
companion/bin/qa-slot --no-town --steps lakeland-projects-b
```

`bun test/check-culture.ts lakeland` and `bun test/check-steps.ts lakeland [project]` (companion/bridge) check the scenes,
packs and steps; `LakelandPlacesLooksTest` renders the looks beside Primorska's (`app/build/scene-snapshots/looks/lakeland-b-*`).

## Voices

No ElevenLabs credits spent. A voice build of the new lines in the cast's English voices would take about 70,300 characters:
44,249 for the eight scenes' lines, replies and puzzled reactions, 26,048 for the steps' (the spots' stories are read, not
voiced). The cast's local clips cover none of them yet.

## For the native speaker

All machine-written. Spoken forms are never graded wrong. To look at first (the helpers' lists are added below):

- **The beck and the tarn**: the Slovene "gaz (brod)" for the ford (the legends' ferry is "brod"), "kamni čez potok" for the
  stepping stones, "delati žabice" for skimming stones, "vodna kreša", "povodni kos"; the German "Trittsteine", "Steg" (the
  footbridge) and "Bootssteg" (the jetty), "Weidenbaum" for the willow (to keep "Weide" apart from a pasture), "Schilfrohr";
  the Italian "pietre per attraversare", "canna palustre", "merlo acquaiolo", "pesce persico"; Granny Maggie's "love" as
  "srček", "tesoro", "Schätzchen", Jack's "mate" as "{m:stari|f:stara}", "Kumpel"; the jokes ("Paddles? Like on a boat?",
  "Fishes? In the Bible, maybe", "I'm eighty-four, not dead!").
- **The facts the lines state**: the dipper walking under water, the beck's Norse bekkr and the tarn's tjörn, Red Tarn under
  Helvellyn, Lily Tarn above Ambleside, Tarn Hows dammed from three tarns after 1862 (about 160 years ago), the bobbin mills.
- **The rescue hut, the playground, the viewing station** (content-1): English "first-aid kit" for the red bags, "call-out",
  "head torch", "Now then!", "mate", "Aye", Mrs Birkett's "love", "Wendy house", "tag", "teatime", "visitors' book", "pike",
  "viewing station", "bracken goes orange", Mr Dixon's "It's right, or it isn't."; Slovene nosila as a plural ("jih", "ju" for
  the handles), torba za prvo pomoč, naglavna svetilka, intervencija, zidarska malta, razgledna hišica, koničasti vrh, knjiga
  vtisov, sopara, pečina, ploh, papirnati zmaj, kostanj (conker), lovljenje, ristanc, hiška za igro, čas za čaj, srček; German
  Erste-Hilfe-Tasche, Grundmauern, Fangspiel, Himmel und Hölle, Spielhaus, Aussichtshäuschen, Bergspitze, Planke, anschubsen,
  stabil; Italian casetta del soccorso alpino, fondazioni, acchiapparella, castagna matta, casetta dei giochi, struttura per
  arrampicarsi, gioco della campana, belvedere, picco, caligine, asse di legno. The playground's spade is the fountain
  project's garden spade (lopata, Spaten, vanga, badile); a child's would be lopatka, Schaufel, paletta. The viewing station's
  winter pane is "dark blue for a winter's night" (projects.json says blue for winter; the National Trust's dark blue gave a
  moonlight view).
- **The fountain, the clock, the bank barn** (content-2): English Mr Dixon's "It's right, or it isn't.", Jack's "Snug as
  anything", "That'll do!", "Spot on!", Edna's "Well I never!", "Bless it!", "Mind how you go, love", "the clockmakers' four"
  (IIII), "byre", Mr Tyson's "Right." at the end of lines; Slovene "pitnik" for the drinking fountain, "korito vodnjaka",
  "jarek za cev", "skedenj v bregu", "srček" for Edna's love, "pljuskati, špricati", the time ("četrt na pet", "pol štirih", "tri
  četrt na devet"), the counts ("To {je|sta|so|je} {n} {udarec|udarca|udarci|udarcev}"), "Suho kot poper", "Kapo dol!", "Uau!",
  "srečnice", "ga ni na ti"; Italian "fienile nel pendio" (coined), "'Giorno" for Dixon's clipped greeting, "Occhio alle dita",
  "progetto" for plan, "il quattro degli orologiai", "{È|Sono} {n} {rintocco|rintocchi}", "grandezza, dimensione"; German
  "Hangscheune", "Wassergraben", "Uhrmachervier", "Na" for "Now then", "Schätzchen", "Zieh am Blasebalg", "Gassi gehen",
  "Kumpel" to a learner of either gender.
- **Not verified**: "byre" and "shippon" as Cumbrian words; a barn owl catching "three or four [mice] a night"; a clock face
  "four feet across".

Sources (each fact a line states):

- The beck and the tarn: Wiktionary's etymologies of [beck](https://en.wiktionary.org/wiki/beck) (Old Norse bekkr) and
  [tarn](https://en.wiktionary.org/wiki/tarn) (Old Norse tjǫrn); [Tarn Hows](https://en.wikipedia.org/wiki/Tarn_Hows) (James
  Garth Marshall's dam at Low Tarn, after the 1862 inclosure).
- Mountain rescue: 999, ask for the police, then mountain rescue ([LDSAMRA](https://www.ldsamra.org.uk/contact)); Keswick's
  volunteers on call day and night ([the team](https://keswickmrt.org.uk/team/)); the distress signal, six blasts a minute, the
  reply three ([Alpine distress signal](https://en.wikipedia.org/wiki/Alpine_distress_signal)); the Lakes' search dogs
  ([SARDA Lakes](https://register-of-charities.charitycommission.gov.uk/charity-search/-/charity-details/1015934),
  [Mountain rescue in England and Wales](https://en.wikipedia.org/wiki/Mountain_rescue_in_England_and_Wales)).
- The viewing station: Claife's coloured glass and the 1790s ([National Trust](https://www.nationaltrust.org.uk/visit/lake-district/claife-viewing-station-and-windermere-west-shore/the-history-of-claife-viewing-station)),
  Thomas West's stations of 1778 ([Lake District National Park](https://lakedistrict.gov.uk/learning/learning-resources/history-resources/history-case-studies/viewing-stations/));
  a pike, a peaked summit ([walklakes toponymy](https://www.walklakes.co.uk/toponymy.html)); the buzzard ([BTO](https://www.bto.org/understanding-birds/birdfacts/buzzard)).
- The playground: conkers in September and October, not for eating ([Woodland Trust](https://woodlandtrust.org.uk/blog/2021/09/when-are-conkers-ready-and-how-to-play/));
  the Wendy house after Peter Pan ([Wikipedia](https://en.wikipedia.org/wiki/Wendy_house)); northern "tea" the evening meal
  ([Oxford Learner's](https://www.oxfordlearnersdictionaries.com/definition/english/tea)).
- The fountain: Honister's green slate ([Honister Slate Mine](https://en.wikipedia.org/wiki/Honister_Slate_Mine)); Victorian
  drinking fountains with cups on chains and troughs ([Drinking fountain](https://en.wikipedia.org/wiki/Drinking_fountain),
  [Metropolitan Drinking Fountain and Cattle Trough Association](https://atom.aim25.com/index.php/metropolitan-drinking-fountain-and-cattle-trough-association));
  a lion's-head spout with its cup ([a listed fountain](https://britishlistedbuildings.co.uk/101151784)).
- The clock: IIII on most dials, Big Ben's IV ([Roman numerals](https://en.wikipedia.org/wiki/Roman_numerals)); turret
  clocks wound by hand about weekly ([Thurgarton](https://southwellchurches.nottingham.ac.uk/thurgarton/hclock.php)); scratch dials
  ([mass dials](https://buildingconservation.com/articles/mass-dials/mass-dials.htm)).
- The bank barn: built into the slope, a ramp to the upper floor, the byre below ([Bank barn](https://en.wikipedia.org/wiki/Bank_barn),
  [Ullswater heritage](https://ullswaterheritage.org/heritage-knowledge-bank/history-contents/history-by-theme/buildings/bank-barns-field-barns-and-spinning-galleries));
  owl holes for barn owls ([Owl hole](https://en.wikipedia.org/wiki/Owl_hole)); yan, tyan, tethera
  ([Yan tan tethera](https://en.wikipedia.org/wiki/Yan_tan_tethera)).

## What is left

- **The native check** of everything above (the lists under "For the native speaker").
- **Voices**: about 70,300 characters in the cast's English voices (no credits spent).
- **lakeland-projects-a**: By the beck names its mill, packhorse bridge and hedgehog, so this branch carries byte-for-byte
  copies of its `in-the-mill`, `in-the-wood` and `on-the-packhorse-bridge` packs (9694f850); at the merge its files win. Once
  both land, the bank barn's first step (the oaks felled) and the viewing station's third (the larch timber) could play in a's
  In the wood instead of over the village, and the stream's mill and packhorse bridge draw a's Lakeland look in By the beck.
  Two prompt pairs a might still want to look at, not this branch's: a's own new packs against older ones (3 clashes).
- **The pictures, closer looks**: the stream's sheep are the default's, not Herdwicks; the rescue hut's map and the fountain's
  lion and cups are a few pixels at the scene's own size (clear closer up); the lookout's board still draws a generic
  mountain line; the clock's numerals are gold marks (IIII is in the lines and the chronicle).
- **The villagers' days**: no routine takes anyone to the new places yet; their people are there with their happenings.
- **Not this branch's, noticed**: On the village green has Old Wilf in an evening happening ("bess"), against his evening
  story; the older Lakeland count lines say "ovac" where Slovene has "ovc" (at-the-gate's).
- **The playground's spade** is the fountain project's garden spade (lopata, Spaten, vanga); a child's would be lopatka,
  Schaufel, paletta.
