# Built: the people on the map in each culture's clothes (map-people)

*Built 2026-11-01, branch `map-people` (the completing round, wave B, part 2). Jan: "can you make app complete - all what
is missing?"; "for choices - make it the best - prod ready". The last line of map-culture.md's "What's left": "The people's
clothes (the kerchiefs, the hats) are the map's own in every culture." For Teo (a child learning Italian from German,
Friuli, a tablet), Carinthia's and the Lake District's villages, and Jan (Primorska: its own pictures' mistakes
corrected, below).*

## What

**Before.** The village map drew its people in Primorska's clothes in every culture: Nonna Rosa of Friuli, Oma Resi of
Carinthia and Granny Maggie of the Lake District all in Babica Micka's red kerchief with white dots, every man in a
Slovene felt hat, every child in Zala's, Nejc's and Tine's clothes. The scenes' sprites did the same (only the day's
pedlar was his culture's, polish-content). And the map's 7 px and 14 px figures weren't the sprites' at all: the village's
own people (the residents, "woman", "man") were dressed by another seed on the map than in their scenes, and differently
again at the closest detail; the innkeeper of every culture, a woman in all four, was a bald man with a moustache at the
closest detail and in her scenes.

**Now** each culture's people wear its own clothes, in its scenes, in their portraits and on its map at every detail,
taken from one table per culture (a wardrobe), and the map's small figures are taken from the sprite's look, so a person
looks the same on the map as in a scene:

| | Friuli | Carinthia | The Lake District |
|---|---|---|---|
| Elders | Nonna Rosa in a white kerchief tied at the nape (il fazzoletto, fazzùl), a dark dress, a blue apron, the scarpets (black velvet, a red flower on the toe); Nonno Bepi in a white shirt, the black waistcoat (il gilet) and a black felt hat, a white moustache | Oma Resi in the dirndl (a white blouse, a dark green bodice, a dark blue skirt, a plum apron); Opa Sepp in the Kärntner Anzug, chestnut brown with green lapels, a green felt hat with a Gamsbart | Granny Maggie in a lavender cardigan, a tweed skirt and a floral pinny, a grey perm, glasses; Old Wilf in a brown tweed jacket and a tweed flat cap, a white moustache |
| Children | Tommaso in the school smock (il grembiule), blue with a white collar; Giulia in a red dress with white dots and scarpets with a yellow flower; Lorenzo in a striped shirt and a red cap | Maxi and Jakob in short Lederhosen with braces and knee socks (Stutzen), red and blue checked shirts, Jakob in a green cap; Lena in a child's dirndl | Harry in a maroon school jumper over a white collar, grey shorts and socks; Poppy in a blue gingham dress and red wellies; Alfie in a yellow raincoat and green wellies |
| The others | Zia Nives (navy, white dots, a red apron), Marisa of the osteria (a white blouse, a black bodice and skirt, the white apron, a tajut of red wine), Maestra Elena, Davide under his cloak, Bruno and Matteo in caps, Franco under his straw hat | Tante Mitzi and Wirtin Kathi in dirndls, Lehrerin Eva in a loden-green Janker, Florian in the Gailtal's leather knee breeches and high boots, Toni bare-headed and bearded, Franz all in white under the miller's cap, Stefan the Mostbauer under the farmer's blue apron (der blaue Schurz) | Auntie Edna in a headscarf tied under the chin and green wellies, Mrs Birkett of the pub in a red blouse and a white pinny with a pint, Mrs Hartley in a blue cardigan, Jack in a waxed jacket, a flat cap and wellies, Mr Tyson in a checked shirt, braces, a flat cap and wellies, Mr Dixon in a flat cap, Brewer Sam bare-headed, ginger and bearded |
| The village's own | dark skirts and aprons, kerchiefs (white or coloured), the scarpets; white shirts, dark waistcoats, felt hats and caps | dirndls, or blouses and skirts; checked shirts or loden Janker, Lederhosen, green or grey hats with a Gamsbart | cardigans, tweed skirts, headscarves, wellies; waxed jackets, Aran jumpers, checked shirts, flat caps, wellies |
| Visitors | the cramâr (polish-content); the pilgrim to Castelmonte in a dark cloak and a black hat | the Kraxenträger; the pilgrim to the Luschariberg in a loden cape and a green hat | the packman; the pilgrim to St Herbert's Island in a blue-grey cape |

The beekeeper in his veil, the hunter, the charcoal burner and the baby are dressed alike everywhere. The full table is in
companion/SCENES.md ("Arts", "Each culture's clothes"); the rules for the map in companion/GAME.md ("The map in a
culture's look", "The people on the map").

**Primorska** keeps its clothes. What its pictures had wrong is corrected, at the lead's word (2026-11-01: "Changing
Primorska's golden pictures is fine when it corrects a mistake"), in the section "Primorska's corrections" below: Vida a
woman, the teacher's skirt, the beekeeper's and the charcoal burner's trousers, the residents dressed by one seed. A culture
without a wardrobe dresses its people as Primorska's.

## How

- **The wardrobe** (`game/render/scene/Wardrobes.kt`): a `Wardrobe` per culture: the cast's sprites it dresses otherwise
  (`cast`, by sprite name), its women and men by their seed (`woman`, `man`: colours, a kerchief, a hat, the scarpets,
  wellies, a dirndl, a Janker, so newcomers don't look alike). The culture's look names it, data, no culture ids in the
  painters: `people.wardrobe` ("friuli", "kaernten", "lakeland") beside `people.pedlar` in `CultureLooks`.
- **The sprites** (`Looks.of(art, seed, culture)`) read the wardrobe first; the scenes (`ArtPainter` sets
  `PeoplePainter.culture` from the frame's look), the portraits (`PortraitsPainter.culture`, a visited town's on a visit),
  the words' stickers and the map's closest detail all go through it. Five new marks on the sprite (`Look`): a check over
  the top (a checked shirt, the gingham dress, over its skirt too), a Gamsbart in a felt hat's band (`plume`), a kerchief
  tied at the nape (`nape`), a flower embroidered on each toe (`toes`, the scarpets), and the tajut (`PROP_GLASS`).
  A dirndl is a white blouse (`top`) under a bodice (`vest`, its buttons), a skirt and an apron of their own colours.
- **The map's small figures** (`Effects.lookOf`): in a culture with a wardrobe they are the sprite's look in the pixels
  there are (`fromScene`): the shirt and the waistcoat or bodice at its sides, the collar (over the apron's bib, the
  blouse's neckline), a check at 14 px, the skirt in its own colour (`skirtCol`), the apron, the kerchief's colour, dots
  and knot, a brimmed hat or the straw hat or a flat cap (a flat crown and a peak to the front, `cap`), the Gamsbart, the
  beard, the shoes in their colour, wellies up the shins (`boots`), the scarpets' flowers (`toes`), the prop and the load.
  The fixed cast's are kept per culture; Primorska's stay the map's own table.
- **One seed per person.** The scenes dress the village's own people by their id (`Looks.seedOf`, the hash ArtPainter
  always used); the map now dresses them by the same seed at 7 px and 14 px (`lookOf(…, sceneSeed)`) and at the closest
  detail (`Effects.person(…, lookSeed)` sets `PeoplePainter.lookSeed` for the sprite's clothes, its moves keep the map's own
  seed), and the portraits by the screens' id hash (`Looks.seedOfHash`), in every culture. In a culture with a wardrobe the
  unnamed people at the edges of the map are a woman or a man of the village by their seed (`Effects.anonArt`), the same at
  every detail.

## How to see it

- The contact sheets: `./gradlew :app:testDebugUnitTest --tests '*MapPeopleTest*'`, then `app/build/map-people/`:
  `00-cast-<culture>.png` (the culture's whole cast, the strangers and six of the village's own in a lineup by the fire:
  the 7 px figures six times bigger, the 14 px ones three times, the sprites twice; by day and at night with lanterns) and
  `01-village-<culture>.png` (the village with its people about their day in the morning and in the evening by the fire,
  the whole map, closer and closest). Before and after in the round's scratchpad, `ux-map-people/before/` and `after/`.
- In the app: any village of Friuli, Carinthia or the Lake District, or a visit to one; zoom in for the 14 px figures and
  the sprites. QA: `companion/bin/qa-slot --steps map-people` (only when named) on a synthetic learner of the culture
  (`LANI_DEV_DATA` from `make-learner.ts --target it|de|en`, `LANI_CULTURE`, `LANI_QA_PAIR=sl-en`): the village as a town
  in the morning and in the dark evening, at rest, closer and closest (`people-<culture>-*.png` in `$LANI_QA_MAP_SHOTS`).

## Tests

- `CultureLooksTest`: Primorska's scenes as before (the golden digests, untouched); an art a culture's look doesn't touch
  draws as Primorska's picture of it with the culture's people in it (`ArtPainter.dressedAs`, a test's); **each culture's
  people wear its own clothes**: digests of each culture's lineup as the scenes draw it and as the map draws it at 14 px and 7 px, four
  wardrobes apart at every size, a culture without one as Primorska's.
- `MapPeopleTest`: the 14 px figures wear the clothes of the scenes' sprites (shirt, skirt or trousers, apron, waistcoat,
  check, hat or kerchief, shoes, toes) for every sprite of every wardrobe, and the closest detail is the scene's; Primorska's
  small figures agree with its sprites in every mark they have, with no exception left; the village's own people are
  dressed alike at every detail and in their scenes in all four cultures, the unnamed in the three with a wardrobe; the
  contact sheets (the portraits too, `02-portraits-<culture>.png`).
- The culture looks' tests that the people stand where they did (`FriuliIndoorLooksTest`, `FriuliApiaryChurchLooksTest`,
  `FriuliMarketSquareLooksTest`, `FriuliPlacesLooksTest`, `KaerntenLooksTest`, `LakelandLooksTest`, and from the branches
  merged in, `KaerntenProjectsLooksTest`, `KaerntenPlacesLooksTest`, `LakelandPlacesLooksTest`) compare with Primorska's
  picture with the culture's people in it, as their head's top is where a bubble points and a hat moves it.
  `FriuliOutdoorLooksTest`'s digests of the pictures with people in them are new (its pictures without people are as they
  were).
- `PedlarsTest` and `MapLooksTest` (Primorska's map; a frame's cost) unchanged; `FacesGoldenTest` and `ClassicLandTest` with
  Primorska's corrections (below).
- `FireAgesTest` (nobody's head hidden round the fire) passes in every culture: Nonno Bepi is bald under his hat, as Stari
  Janez is (short hair under a felt hat shows a pixel above the brim at the side, which touched a neighbour's head).
- The whole Android unit suite on 242404c2: 3692 tests, none failed (2 skipped). The Python tests (251; 142, 11
  skipped), the bridge's typecheck, its smoke (ports 9670, 9673, 9676, DRAMA3 9688) and `tutor-export --check` (89 files)
  pass; nothing of the bridge changed.
- **Merged with the queue ahead** (the lead, 2026-11-01): `kaernten-projects-a`, then `lakeland-projects-b` at d6accae5,
  then `kaernten-projects-b` at d09659ea (each containing the ones before, with stories-friuli, the German course and the
  lens round), merges, not rebases; bin/qa's step list by `qa-steps-union.py` (one conflict each time, no step function
  inserted at the same spot). Their scenes' people wear the Carinthian and the Lake District's wardrobes: the eight
  Carinthian project places and the wood, the six places of its second half with the stream bank and the pond, and the
  Lake District's beck, tarn and six project places (contact sheets in the scratchpad, `ux-map-people/kaernten-scenes-people.png`
  and `lakeland-scenes-people.png`). Their look tests' digests are of pictures without people, so none changed; their tests
  that the people stand where they did compare with Primorska's picture with the culture's people in it, as above. On the
  merged tip: the whole Android unit suite 3732 tests, none failed (2 skipped); the Python tests 251 and 142 (11 skipped);
  the bridge's typecheck, its smoke (3444 checks, ports 9670, 9673, 9676, DRAMA3 9688) and `tutor-export --check` (89
  files) pass.

## On the emulator

`companion/bin/qa-slot --steps map-people` on a synthetic learner of each culture (`make-learner.ts --target it|de|en|sl`,
`LANI_CULTURE`, the phone in sl · en), slot 1, the debug build, 2026-11-01 under a load average near 40: setup, map-people
and the crash check passed in all four. Screenshots in the round's scratchpad, `ux-map-people/emulator/`
(`people-<culture>-<day|evening>[-closer|-closest].png`): Friuli's cramâr on the road under his crassigne, Maestra Elena
in her glasses and Lorenzo in his red cap at the closest detail; Carinthia's Florian in his green hat and Franz in the
miller's white cap, the Luschariberg pilgrim; the Lake District's Alfie in his yellow raincoat, Jack in his flat cap;
Primorska's people as before. The map's frame on the emulator's software rendering: 78 to 227 ms at details 1 to 3,
the same range as map-culture.md's runs (82 to 122 ms) given the machine's load; the JVM's frame budget
(`MapLooksTest`) holds.

## Primorska's corrections

The lead, 2026-11-01: "Fix the Primorska mismatches you listed. Changing Primorska's golden pictures is fine when it
corrects a mistake." Each of Primorska's pictures that changed, and why:

| What | Before | Now |
|---|---|---|
| Vida the innkeeper (Gostilničarka), `innkeeper` | a bald man with a moustache in her sprite, her portrait and at the map's closest detail; at 7 px and 14 px a man in trousers | a woman: a white blouse under a teal bodice, a dark skirt, the white apron, her hair up, her mug, at every size |
| The teacher at 7 px and 14 px | her skirt her blouse's purple | the sprite's dark skirt |
| The beekeeper at 7 px and 14 px | trousers a shade darker than his suit | his suit's canvas colour, as the sprite |
| The charcoal burner at 7 px and 14 px | trousers near black | the sprite's dark grey |
| The village's own people (`woman`, `man`) | dressed by one seed in their scenes, another on the map at 7 px and 14 px, a third at its closest detail, a fourth in their portraits (nearly all alike there) | by their scenes' seed everywhere (`Looks.seedOf`; the portraits by `Looks.seedOfHash` of the screens' id hash) |

The digests that changed, taken after a look at the sheets:

- `FacesGoldenTest`: `innkeeper` in all nine poses; `woman` and `man` in all nine (each digest is the portraits at three
  sizes and four moments, and the scene's sprite still and talking).
- `ClassicLandTest`: jan-like `side-day` and `tall-day`; town `side-day`, `tall-day` and `tall-dusk-k2` (Čebelar Anton's
  trousers; the other frames hold).
- `CultureLooksTest`: Primorska's people lineup (its golden scenes hold: neither Vida nor the residents stand at their
  spots).

The before and after sheets: the round's scratchpad, `ux-map-people/primorska-before/` and `primorska-after/`
(`00-cast-primorska.png`: the cast and six residents at 7 px, 14 px and the closest detail, by day and at night;
`01-village-primorska.png`; `02-portraits-primorska.png`).

## Credits

No voice: nothing to build, 0 characters.

## Tap counts

No journey changes: the people's pictures do, not their places or their taps (a villager's card: one tap, as before).

## Sources

Settled against these (2026-11-01):

- Scarpets: black velvet, embroidered flowers, soles of layered cloth sewn at home:
  https://www.vimado.it/friuli/arte-cultura/gli-scarpets-la-tradizionale-calzatura-friulana,
  https://it.wikipedia.org/wiki/Friulane
- The Friulian and Carnic costume: the head kerchief (fazzùl, quadri) white, tied behind the neck, coloured ones later; the
  apron (grimâl) worn all the time; heavy dark cloth; men's white shirt, gilet, dark trousers, a wide black hat:
  https://donneincarnia.wordpress.com/2014/05/12/il-costume-carnico/,
  https://cjalzumit.wordpress.com/2024/05/24/abiti-tradizionali-friulani/,
  https://www.turismo.it/tradizioni/articolo/art/friuli-4-cose-da-sapere-sui-costumi-tradizionali-della-carnia-id-14412/
- The school smock, blue for boys, still common in primary schools (the big bow at the collar is the older fashion, so
  Tommaso has none): https://www.focusjunior.it/?p=237907,
  https://www.tecnicadellascuola.it/scuola-primaria-grembiule-blu-unico-per-superare-le-tradizionali-distinzioni-legate-allidentita-di-genere-insorge-la-lega
- The tajut, the Friulian glass of wine at the osteria: https://www.scattidigusto.it/udine-tajut-migliori-osterie-calici-vini-bianchi
- The Kärntner Anzug, hazelnut to chestnut brown with green lapels and piping (grey loden with green is the Steirer's):
  https://de.wikipedia.org/wiki/Kärntner_Anzug, https://kaerntner-heimatwerk.at/herren/
- The Gamsbart: https://de.wikipedia.org/wiki/Gamsbart
- The Gailtal's women's dress and the dirndl (blouse, bodice, skirt, apron); the Gailtal men's knee-length leather
  breeches and high boots: https://austria-forum.org/af/AustriaWiki/Gailtaler_Tracht, https://en.wikipedia.org/wiki/Dirndl
- The short Lederhose, the boys' favourite after the war, braces with the cross strap, knee socks:
  https://de.wikipedia.org/wiki/Lederhose
- The blue apron, the farmers' working apron: https://www.merano-suedtirol.it/de/naturns/natur-kultur/geschichte-tradition/der-blaue-schurz.html,
  https://indigo.uni-ak.ac.at/aprons/
- The Janker, the Alpine wool jacket in grey, green or red: https://de.wikipedia.org/wiki/Janker
- The flat cap of northern England: https://en.wikipedia.org/wiki/Flat_cap
- Wellies on farms and green wellies for country life: https://en.wikipedia.org/wiki/Wellington_boot
- The waxed jacket of British country clothing: https://en.wikipedia.org/wiki/Barbour_(company)
- The pinny: https://www.oxfordlearnersdictionaries.com/definition/english/pinny
- The headscarf tied under the chin, the twinset: https://collections.vam.ac.uk/item/O101239/head-scarf-unknown,
  https://en.wikipedia.org/wiki/Twinset
- A Cumbrian primary school's uniform (jumpers in the school's colour over white polo shirts, grey shorts, blue gingham
  summer dresses): https://cambridge.scmat.org.uk/parent-information/school-uniform
- The yellow raincoat and wellies of a rainy English childhood: https://collections.vam.ac.uk/item/O1259182
- The shepherd's crook: https://www.lowimpact.org/categories/sticks/

## For a look by someone who knows

There is no language here (no text, no voice). What wants a look by someone who knows the places, as map-culture.md says
of its buildings: whether Friuli's, Carinthia's and the Lake District's people would recognise themselves (the white
fazzoletto and the scarpets, the dirndls and the Anzug, the flat caps and the wellies), and whether a child of today in
Friuli wears the blue smock to school. Every choice above is from the sources; the colours are the app's palette.

## What's left

- Primorska's unnamed people (the population beyond the named, at the edges of the map) are still colours by their seed
  at 7 px and 14 px and one of the cast's sprites at the closest detail, as before; in the other cultures they are a
  woman or a man of the village, the same at every detail. Primorska's grandpa and shepherd wear their waistcoats only in
  their sprites (its small figures have none, as drawn).
- The clothes are the same all year: no coats, scarves or woolly hats in winter.
