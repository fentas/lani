# Built: Teo's places on the new arts (places-it-arts, 2026-10-30)

Jan, 2026-10-30 ("sure do follow ups"): the follow-up of the places round. Teo (a child of about seven, learning Italian
from German, the Friuli culture, a tablet) can build every village project, but after places-sl and places-it-projects
his village still had no scene for the places that got new pictures in places-sl: the bocce court, the playground, the
wayside chapel, the fire station, the lookout tower and the palisade's gate. Their pictures had a Friuli look already
(`FRIULI_PLACES` in `game/scene/CultureLook.kt`); a finished landmark's card had no way in, and neither had the palisade.
This branch writes those six scenes, content only. The rules are companion/SCENES.md's ("Scenes of a culture pack" has
the six rows, "Seasons" a row, "The village's places" a paragraph, "Arts" the slots, spots and effects used).

## Every gap, checked

The Slovene village's scenes (companion/scenes and Primorska's own) open from 31 + 2 places; the Friuli village's 27 from
21 of them before this branch. Compared place by place (every building type and every `project:<id>` of the 13 projects):

| Place | Slovene scene | Friuli before | Friuli now |
|---|---|---|---|
| `project:balinisce` | `na-baliniscu` (`bocce`) | none | `al-campo-da-bocce` |
| `project:igrisce` | `na-igriscu` (`playground`) | none | `al-parco-giochi` |
| `project:kapelica` | `pri-kapelici` (`chapel`) | none | `alla-cappelletta` |
| `project:gasilski_dom` | `v-gasilskem-domu` (`firestation`) | none | `dai-pompieri` |
| `project:razgledni_stolp` | `na-razglednem-stolpu` (`lookout`) | none | `sulla-torre-panoramica` |
| `palisade` (a building) | `pri-vratih` (`palisade`) | none | `al-portone` |

Every other building and project had its Friuli scene already (the tent, the kitchen and the houses' rooms, the field and
the big barn, the stream and the bridge, the mill, the square and its three projects, the bee garden and the bee meadow,
the church, the school, the market, the watchtower, the vineyard, the fire, the sea). Kärnten and the Lake District have
only the fire and the horizon, a later wave as before.

## What

Six scenes in `companion/cultures/friuli/scenes/`, in Italian with Slovene, English and German, A1, for a child, each on
its art in Friuli's look; each its pack in `companion/cultures/friuli/packs/` and its I spy clues in
`companion/ispy/scenes/`. All machine-written, and each file's `review` says so.

| Scene | Art | Opens from | People (spot) | Happenings | Dialogs | Turns | Wrong choices (naming a page) | Tap turns | Counting | Pack | I spy |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `al-campo-da-bocce` Al campo da bocce | `bocce` | the bocce court (`balinisce`) | Nonno Bepi (throw: morning, afternoon), Vignaiolo Matteo (throw: evening), Mugnaio Franco (jack), Ostessa Marisa (table) | 5 | 13 | 27 | 33 (24) | 1 | 1 | 9 | 15 things, 65 clues |
| `al-parco-giochi` Al parco giochi | `playground` | the playground (`igrisce`) | Maestra Elena (bench), Giulia (swing), Lorenzo and Tommaso (sandpit: never in the same months) | 6 | 13 | 26 | 32 (24) | 1 | 1 | 12 | 15, 62 |
| `alla-cappelletta` Alla cappelletta | `chapel` | the wayside chapel (`kapelica`) | Zia Nives (chapel: morning, evening), Fabbro Bruno (chapel: afternoon), Nonna Rosa (bench), Giulia (road) | 7 | 14 | 30 | 41 (30) | 2 | 1 | 12 | 15, 67 |
| `dai-pompieri` Dai pompieri | `firestation` | the fire station (`gasilski_dom`) | Pastore Davide (pump), Fabbro Bruno (hoses), Mugnaio Franco (door: morning, evening), Tommaso (door: afternoon) | 6 | 14 | 28 | 34 (25) | 2 | 1 | 10 | 16, 64 |
| `sulla-torre-panoramica` Sulla torre panoramica | `lookout` | the lookout tower (`razgledni_stolp`) | Ostessa Marisa (board), Tommaso (rail: afternoon), Pastore Davide (rail: morning, evening), Nonno Bepi (stairs) | 5 | 13 | 26 | 28 (22) | 1 | 2 | 9 | 16, 58 |
| `al-portone` Al portone | `palisade` | the palisade, at its level | Pastore Davide (road), Fabbro Bruno (gate: evening), Giulia (gate: afternoon), Tommaso (bench) | 6 | 12 | 27 | 33 (24) | 2 | 1 | 13 | 15, 64 |
| **6 scenes** | | | | **35** | **79** | **164** | **201 (149)** | **9** | **7** | **65 words** | **92 things, 380 clues** |

Across them: every slot of each art a thing, once; every effect of each art cued at least once (`CultureScenesTest`
holds them to it: the new arts' `SEEN` sets are empty); every happening with two or three variants, the one of before
last, a memory; something on in every month; faces where a line carries a feeling; a `puzzled` reaction on every form
turn; registers as the cast has them (Lei and «signor» to Franco and Bruno, «maestra» and Lei to Elena, «signora Marisa»
and Lei, tu and «nonno» to Bepi, «zia» to Nives); everything that agrees with the learner a gender pair in every language;
Nonno Bepi never in the evening (his story's), no child in an evening happening; no wine for a child. Two people at one
spot are never on at once. The gate's torches (level 2), roof and pennant (3), ladder (4) and bell (5) are things of the
scene at the palisade's levels (`needs.level`, as `SceneFixtures` and Pri vratih have them), and so are the happenings that
need them (the torches lit at dusk from level 2, the bell from level 5).

What each holds (SCENES.md's rows say all of it):

- **Al campo da bocce**: Nonno Bepi's court under the linden by Marisa's osteria; raking the sand with his proverbs, his
  game (who throws first, a point and «mandi», bocciare), Franco and his rule (the rule found with a tap on the side board,
  the points on the slate counted: the `score` effect draws them), Marisa under the pergola (ho sete, grape juice in summer,
  hot chocolate in winter), Matteo's evening game under the lantern.
- **Al parco giochi**: Maestra Elena's playground by the school; Giulia on the swing (snow and a snowman in winter),
  Lorenzo's sand castle and its three towers, Tommaso's football (the ball under the slide, a tap turn) and his kite in the
  autumn wind, hopscotch at break on school days (the minutes left counted), soap bubbles on summer evenings.
- **Alla cappelletta**: Zia Nives's ancona among the maize; fresh flowers (May's for the Madonna), the red lumini and the
  Ave Maria on the little bell, Giulia ringing noon (the bell found with a tap; the houses that hear it counted from the
  village's houses), Nonna Rosa on the bench (a pilgrim to Castelmonte, a tap turn; bread for a traveller; the linden in
  flower), Fabbro Bruno in a summer storm (the bell against the hail), the maize taller than Giulia and its cobs.
- **Dai pompieri**: the volunteer firefighters (the VVF sign, steel helmets, Santa Barbara, the tricolore); Davide's drill
  with the old pump from Gorizia (the leather buckets found with a tap), Franco's alarm (the bell, «Pompieri, venite!» on the
  horn, only the charcoal pile's smoke), Bruno and the hoses (everyone in the village counted: all volunteers), the helmets
  polished for Santa Barbara on 4 December, the gate shut and the lantern lit at night, Tommaso who wants to be a
  firefighter (the helmet too big, found with a tap; what a child does on seeing a fire: 112 or the grown-ups, never alone,
  out of the house at once).
- **Sulla torre panoramica**: Marisa's tower for her guests; the Tricorno's three points (Triglav, three heads, in
  Slovene), the sea at Grado on a clear day after the bora, Gorizia and Nova Gorica side by side, the plain under fog in
  winter; Tommaso's stamp and the visitors' book (the guests counted; the campanile found through the binoculars with a
  tap), Nonno Bepi and the hundred steps (his rests counted), Davide and the buzzard, the peaks pink at sunset and the
  evening bell coming up from the village.
- **Al portone**: Davide's flock out at dawn and home at dusk (counted at the gate: the `flock` effect draws them), Bruno
  barring the gate (the wolves at the forest's edge, safe behind it), the torches lit at dusk (a tap turn; the creaking hinge
  oiled), Tommaso and the cramâr from Carnia coming up the road (a tap turn: from the wood), Giulia and the gate's bell at
  level 5 (rung for danger only).

## The words

Six new packs (65 words, `it` with its article, `sl`, `en`, `de`, an example in each and notes in English, Slovene and
German): `al-campo-da-bocce` (campo da bocce, boccia, pallino, sponda, pergolato, punto, tirare, vincere, bocciare),
`al-parco-giochi` (altalena, scivolo, sabbiera, secchiello, paletta, cubo per arrampicarsi, casetta, gioco della campana,
aquilone, bolla di sapone, castello di sabbia, spingere), `alla-cappelletta` (cappelletta, campanella, tovaglia, merletto,
affresco, lumino, grata, pellegrino, Buon viaggio!, mazzo di fiori, profumo, appassito), `dai-pompieri` (pompa, tubo, divisa, pompiere, incendio, spegnere, volontario, allarme, aiutare,
lucidare), `sulla-torre-panoramica` (panorama, cima, binocolo, timbro, ospite, scalino, poiana, tramonto, città),
`al-portone` (portone, sbarra, bandierina, abbeveratoio, gregge, cardine, olio, cigolare, aprire, chiudere, uscire, ultimo,
pericolo). A word another Italian pack has is that
pack's (one card wherever it is met): the linden and the bench (In piazza), the sand (Il mare), the rake (Nel campo), the
rule (Il ponte), the lantern and the book (Nella tenda), the tower, the railing, the roof, the torch, the horn, the
palisade, the gate and the valley (Sulla torre), the helmet (Storie), the axe and the bucket (Al fuoco), the ladder (Nel
mulino), the bell (Festa di Pasqua), the cross, the altar and the picture (In chiesa), the jug (In cantina), and the
starter packs' road, wood, field, hill, village, school, table, glass, window and stairs. The fire station's big doors are
`al-portone`'s «il portone», one card for the gate's and the station's. No two Italian words share an English, German or
Slovene prompt (card.py's rule, 0 clashes over 807 Italian words).

## How it is reached (taps)

| Journey | Before | After |
|---|---|---|
| The village → a finished project's place (the bocce court, the playground, the chapel, the fire station, the lookout tower) | a tap on the landmark opened the projects sheet: no way in | 2: the landmark, its card's "🚪 Vstopi · Geh hinein: <the scene> ›" |
| The village → the palisade's gate | the palisade's card had no way in | 2: the palisade, "🚪 Vstopi" |
| A happening there | none | 1: its bubble at the landmark, as every place's |
| «Vedo, vedo» there | none | 3: the landmark, "🚪 Vstopi", the child's offer |

## For the native speaker

All the Italian, Slovene and German is machine-written. A spoken form is never graded wrong. To look at first:

- **Bocce:** bocciare as a child's word, il pallino, la sponda for the court's board, il pergolato, «chi dorme non piglia
  pesci», «liscia come l'olio», «tirala bassa», «La aiuto?»; Slovene bulin, brajda; German Bande, Zielkugel.
- **Playground:** il gioco della campana (northern Italy may say «il mondo» or «la settimana»), il cubo per arrampicarsi,
  la paletta, il secchiello, «Adesso il castello sta su», l'acqua saponata; Slovene ristanc, «me poriniš»; German «Himmel und
  Hölle».
- **Chapel:** cappelletta and ancona, la campanella, il lumino (German Windlicht, Slovene lučka), il merletto di Gorizia,
  l'affresco, la grata, l'Ave Maria, «suonare contro la grandine», Castelmonte (Slovene Stara Gora), appassito, «i fiori sono
  già stanchi».
- **Fire station:** il tubo for the hose (la manichetta), la prova for the drill, «Pompieri, venite!», la carbonaia, il
  fogolâr, Santa Barbara on 4 December (the firefighters' patron), «il 112», lucidare; Slovene črpalka, gasilska cev.
- **Lookout:** il Tricorno and «tre punte, tre corni», il binocolo for the binocular viewer, il timbro, la poiana, Gorizia and
  Nova Gorica, «wunderschön», «Goditelo»; Slovene dvogled, kanja, postanek.
- **Gate:** il portone, la sbarra, il cardine, cigolare and «Fa cri, cri», l'abbeveratoio, il gregge and le greggi, la
  pigrona, the cramâr's cassetta and cassettini, «Lo tengo!».
- **Everywhere:** the gender jokes in reactions («la sabbia è una signora», «le città sono signore», «le pannocchie sono
  signorine», «le mie pecore sono signorine»), each a child's joke about grammatical gender, never about the learner's.

## Requests for the art owners

None is needed for the scenes to be right; each would let them show more. This branch changes no art.

- **bocce** (Friuli's look): the scoreboard reads MI | VI, Slovene, in Friuli too; NOI | VOI there. The content never
  quotes the letters. The osteria and its frasca are drawn but have no slot: an `inn` slot would let a line say «guarda
  l'osteria» and I spy find it.
- **playground** (Friuli's look): the green fontanella has no slot; a `fontanella` (Friuli only) would make it a word.
- **chapel**: one slot, `candle`, is both the two red lumini on the step and the white candle on the altar, so its word is
  il lumino; an altar candle of its own would let la candela be a thing too.
- **palisade** (Friuli's look): the `pedlar` effect draws the Slovene krošnja with its sieve; the cramâr's crassigne (a
  wooden case of little drawers on his back) would match «una cassetta con tanti cassettini».
- **Friuli's `projects.json`** (content, the project steps' texts): the chapel's fourth step says «le scandole, piccole tegole
  di legno», while Friuli's ancona is drawn under coppi. Not changed here (its chronicle line is in learners' villages).

## How it was checked

- **Without Gradle** (the scratchpad's replay of the app's content tests, `ux-places-it-arts/tools/check.sh`): every scene's
  structure as CultureScenesTest and ScenesContentTest have it (every slot once, the spots, every effect used), every turn,
  reaction, why and grammar page (DialogReactionsTest, the variants, the seasons), every I spy clue (ISpyContentTest's rules,
  the Italian adjective and the German pronoun by the gender), the look lint (every «guarda» cued or naming a thing the art
  draws), the bridge's `validateCulture` and `validateScene` (ok: cast 13, packs 58, pages 18; each scene ok), the prompts
  (0 clashes over 807 Italian words) and the learner audit (0 texts naming Jan, 0 malformed placeholders, 0 turns the same
  for one gender).
- **The Android unit tests** (lani-slot.sh) and **the bridge** (tsc, the smoke, `tutor-export --check`): the numbers are in
  the round's report.
- **On the emulator**: QA's step `places` now plays Teo's places with an Italian learner of the Friuli culture
  (`LANI_CULTURE=friuli`, the phone in sl · en): the bocce court finished, its landmark, its card's way in, Bepi's game, «Vedo,
  vedo» (the jack), Davide's drill at the fire station, then each other place (the playground, the ancona at dusk, the fire
  station, the lookout, the gate by day and at night). Screenshots in the round's scratchpad, `ux-places-it-arts/qa/`.

## What is left

- **The native check** of the six scenes, their packs and their I spy clues (above).
- **Voices**: the new lines get their clips as any scene's (`voice-build`, the cast's Friuli voices).
- **The villagers' days**: no routine goes to the new places yet (place-life's round adds routines that need a project), so
  their people are there with their happenings only.
- **The art requests above.**
