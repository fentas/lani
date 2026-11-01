# Built: the Lake District's project places, part a (lakeland-projects-a, wave B)

Jan, 2026-11-01: "can you make app complete - all what is missing?" (followups-6, 7–8). The Lake District village (culture
`lakeland`: English for an adult learning it from Slovene, German or Italian) had a scene for every building since
places-lakeland, but none of its thirteen village projects had a place to go in, none had project steps (each step was the
practice it always was), and the arts its projects draw drew Slovenia: the spruce maypole, the timber cart bridge, the
shingled mill, the sand bocce court, the whitewashed kapelica, the vine rows. Its wood had no scene: a tap there opened
nothing. This branch does seven of the projects and the wood; lakeland-projects-b, at the same time, the other six, the beck
and the tarn.

Everything written in a language is machine-written (Claude) and waits for a native check: every scene, pack and steps
file says so in its `review`, every clue file in its `machine_written`.

## What

### The places

| Project | Place (scene) | Art | Opens from | Its people |
|---|---|---|---|---|
| `mlaj` | Round the maypole (`round-the-maypole`) | `square` | the maypole, while it stands (May) | Shepherd Jack, Poppy, Alfie, Harry, Old Wilf, Brewer Sam |
| `most` | On the packhorse bridge (`on-the-packhorse-bridge`) | `stream` | the bridge's landmark | Mr Tyson, Shepherd Jack, Mr Dixon, Beekeeper Arthur |
| `mlin` | In the mill (`in-the-mill`) | `mill` | the mill's landmark, and the beck's mill | Mr Tyson, Alfie, Granny Maggie, Mr Dixon |
| `balinisce` | On the bowling green (`on-the-bowling-green`) | `bocce` | the green's landmark | Old Wilf, Mr Tyson, Brewer Sam, Beekeeper Arthur |
| `kapelica` | At the chapel (`at-the-chapel`) | `chapel` | the chapel's landmark | Auntie Edna, Mr Dixon, Granny Maggie, Poppy |
| `vinograd` | In the damson orchard (`in-the-damson-orchard`) | `hills` | the terraces | Brewer Sam, Old Wilf, Poppy, Mr Tyson, Alfie |
| `cebelji_travnik` | In the wildflower meadow (`in-the-wildflower-meadow`) | `apiary` | the meadow by the bee garden | Beekeeper Arthur, Granny Maggie, Poppy, Harry |
| (the wood) | In the wood (`in-the-wood`) | `forest` | the wood, from the first day | Shepherd Jack, Old Wilf, Granny Maggie, Poppy, Alfie |

SCENES.md "Scenes of a culture pack" has a row for each (what happens there, by the hour and the month).

| Scene | Happenings | Dialogs | Turns | Wrong choices (naming a page) | Tap turns | Counting | Pack words | I spy things, clues |
|---|---|---|---|---|---|---|---|---|
| Round the maypole | 6 | 16 | 30 | 42 (23) | 2 | 1 | 12 | 15, 68 |
| On the packhorse bridge | 6 | 13 | 26 | 36 (24) | 2 | 2 | 11 | 15, 61 |
| In the mill | 6 | 12 | 24 | 34 (23) | 1 | 1 | 14 | 19, 77 |
| On the bowling green | 5 | 13 | 26 | 39 (24) | 2 | 1 | 12 | 15, 68 |
| At the chapel | 6 | 14 | 28 | 40 (27) | 2 | 1 | 11 | 15, 64 |
| In the damson orchard | 6 | 13 | 26 | 33 (24) | 2 | 1 | 12 | 14, 57 |
| In the wildflower meadow | 5 | 13 | 26 | 34 (24) | 1 | 1 | 12 | 13, 59 |
| In the wood | 6 | 14 | 28 | 43 (26) | 2 | 2 | 13 | 23, 93 |
| **8 scenes** | **46** | **108** | **214** | **301 (195)** | **14** | **10** | **97** | **129, 547** |

Each follows places-it-arts' rules: every slot of its art a thing once (the wood has no `boar`: the Lakes have none, and
its painter draws none there), every effect of the bocce and chapel arts cued, something on in every month, a variant (a
memory) on every happening, a reaction and a why in Slovene, Italian, German and English to every wrong choice, a `puzzled`
reaction on every form turn, faces where a line carries a feeling, the learner's agreements as `{m:…|f:…}` pairs, Old Wilf
never in the evening (his story's), ale for the grown-ups and lemonade or juice for a child. Two people at one spot are
never on at once (Wilf by day and Arthur in the evening at the green's `throw`, Edna and Dixon at the chapel, Poppy and
Harry at the meadow's table).

The words: a pack of the scene's own id for each (97 words), the wild animals new to the Lakes among the wood's
(`hedgehog`, `squirrel` "red squirrel", `owl`, `stag`, `dormouse`), the mill's `mill` and the bridge's `packhorse-bridge`
in their scenes' packs, which part b's beck names for its `mill` and `cartbridge`; a word another pack has is that pack's
(the green's oak and bench, the field's fox and badger, the fell's larch and dry-stone wall, the May Day pack's maypole and
ribbons). The beck's own words (stepping stones, footbridge, willow, trout, dragonfly, watercress, washing) are part b's
`by-the-beck`, which On the packhorse bridge names: this branch carries a copy of b's `by-the-beck` pack and scene only to
validate (commits f77e71f6, 631f19ca); the lead keeps b's at the rebase. Pack ids are unique over every culture (the
app's build copies them into one directory): the project packs are `project-<id>`.

### The project steps

A culture pack's `project-steps/<project>.json` (SCENES.md "Project steps"), one short dialog per step, at A1 and A2, in
the scene where the work happens, with the project's leader and helpers; each step's turns test 1–3 words of its project
pack (`project-<id>`), and the chronicle line of each step (projects.json, already in learners' villages) is what it does.

| Project | Steps | Where they are played | Turns | Wrong choices | Pack words |
|---|---|---|---|---|---|
| `mlaj` (Shepherd Jack) | 5 | 1–2 In the wood (the larch tapped and felled: `fell`, `done`; carried off), 3–5 On the village green (peeled and painted, the crown and the ribbons, raised: `maypole`, the band) | 21 | 31 | 9 |
| `most` (Mr Tyson) | 6 | the beck (b's `by-the-beck`; `done` at steps 1, 2, 3 and 5), 4 In the wood (the logs); 6 the first pony over (`cart`, no `done`, as Primorska's first cart) | 28 | 37 | 12 |
| `mlin` (Mr Tyson) | 6 | the beck, a `done` in every step (the wheel's water at the last: `wheel` 1) | 24 | 32 | 11 |
| `balinisce` (Old Wilf) | 5 | On the village green, under the oak | 20 | 29 | 10 |
| `kapelica` (Auntie Edna) | 6 | over the village (the chapel's lane has no scene; the bell rung at the last) | 24 | 34 | 12 |
| `vinograd` (Brewer Sam) | 7 | 1–6 In the field, 7 over the village (tea and scones on the terrace) | 30 | 47 | 14 |
| `cebelji_travnik` (Beekeeper Arthur) | 6 | 1–2 In the field (mown, sown), 3–6 At the bees (the hives made and painted, the shed, the first honey: `honey`) | 24 | 36 | 12 |
| **7 projects** | **41** | | **171** | **246** | **80** |

`bun test/check-steps.ts lakeland` (companion/bridge) checks them: ok for all seven. Two fixes of the check came from
the Carinthian round, cherry-picked here (67781e4b: a noun with its article and a verb's forms count as tested for en, de
and it; 1e553f80: the check's wrapper keeps the dictionary's flag).

### The Lake District's look

Each art's table is its own value in `game/scene/LakelandLooks.kt`, a line of its own in `PLACES` (part b's beside them):
`SQUARE_MAYPOLE`, `STREAM_BRIDGE`, `MILL`, `BOCCE`, `CHAPEL`, `HILLS`, `APIARY_MEADOW`, `FOREST`. SCENES.md "Culture looks"
has a row per art with all it draws; in short:

| Art | The Lake District's look |
|---|---|
| `square` (the maypole) | the larch peeled and painted in a red and white spiral to a gilt knob, a crown of flowers, eight ribbons red, blue, yellow and green; its steps: a peg, the larch on trestles in its bark, painted, the crown and the ribbons beside it, raised; `dance` the children's ribbon dance round it |
| `stream` (the bridge, the mill) | a packhorse bridge of slate with one round arch, humped, low parapets; its steps: pegs and a line, slate heaps, the abutments, the logs and the centring, the arch, the parapets; a fell pony with panniers for `cart`; the mill of slate stone under slate |
| `mill` | oats and oatmeal, slate-stone walls, the fells through the window |
| `bocce` | a striped bowling green with its ditch and mat; dark woods with red or yellow discs and a white jack, rolled in a curve (`throw`, `hit`); the oak; a rose arbour; lemonade; the inn under slate with a sheep on its sign; a chalk disc of each side's colour on the slate; a stone roller |
| `chapel` | slate stone under slate, a stone bellcote, the Good Shepherd, candles in jars, a yew, a hay meadow behind a dry-stone wall, the fells and a battlemented village, a fell walker for `traveller` |
| `hills` | damson trees by the month on four dry-stone terraces, the farmhouse under slate with its porch, a battlemented tower, the fells and walled fields, yews, an apple tree, foxgloves |
| `apiary` (the meadow) | the wildflowers by the month: cowslips, clover, ox-eye daisies, cornflowers, buttercups, cranesbill, knapweed, harebells |
| `forest` | larches by the month (gold in autumn, bare in winter), oak and birch, a Herdwick ewe without a bell, bluebells, no boar nor bear |

Three Friuli flags read `!= null` and would have taken the Lake District's values for Friuli's (the stream's `cartbridge`,
the chapel's `village`, the apiary's `meadow`); they now name Friuli's value, and Friuli draws as before.

## How it was checked

- **The content**: `bun test/check-culture.ts lakeland` (the culture as the bridge validates it, its scenes, packs, steps and
  cast): ok; `bun test/check-steps.ts lakeland`: 7 files, 41 steps, 82 dialogs, 437 lines, ok; `bun test/learner-content.ts`:
  0 texts naming Jan, 0 malformed placeholders, 0 turns the same for one gender. The helpers' own check (every object's word
  in the pack it names, every I spy thing a slot of its scene, no two people at one spot at once, something on in every
  month, the reaction rule of DialogReactionsTest over the steps as well).
- **The looks**: `LakelandProjectLooksTest` (new): Primorska's pictures the same as a culture without a look's at every step,
  the Lake District's each step of the maypole, the bridge and the mill drawn and each more than the one before, every
  thing of each art there to tap at every detail, the ribbon dance only while the maypole stands; its contact sheets in
  `build/scene-snapshots/looks-projects/` (each art beside Primorska's: the steps, by day, in the evening and at night,
  through the months, closer up, the dance and the raising). `LakelandLooksTest`'s reviewed digests of the green and the bee
  garden updated after a look at the sheets; `CultureLooksTest` keeps Primorska's, Friuli's and Kärnten's pictures pixel for
  pixel.
- **The tests that count**: DialogReactionsTest's scene files (+8, and the copy of b's beck), SceneCoherenceTest's done
  lines (46 + 30), AlpsLooksTest's places (the wood, a spot, beside every building).
- **The bridge**, on the branch as it is now (merged with the queue's tip, map-people): `bunx tsc --noEmit -p .` clean;
  `check-culture` ok for the Lake District, Kärnten, Friuli and Primorska; `check-steps lakeland` 13 files (both halves of
  the wave), 78 steps, 156 dialogs, 878 lines; the smoke with this round's ports (`LANI_SMOKE_PORT=9770
  LANI_SMOKE_FRIULI_PORT=9773 LANI_SMOKE_TOWN_PORT=9776 LANI_SMOKE_DRAMA3_PORT=9788`): 3456 checks, all passed (3327 before
  the merges); `tutor-export --check`: 89 files match (54 tools, 32 guide topics); the learner audit 0, 0, 0.
- **Python** (`bin/qa` changed): 251 tests, and 142 with 11 skipped, all passing (after the merges too).
- **The Android unit suite** (lani-slot.sh) on the branch as it is now, merged with the queue's tip (map-people): 480
  classes, 3737 tests, 0 failures, 2 skipped. Before the merges: 475 classes, 3691 tests, the one failure a look line in the
  copy of part b's beck (fixed on b's branch, the copy refreshed: ba0a0478). After the merges two turns of the maypole's new
  variants gave both right answers one reply (each its own now: f16ba683, a96e445d). On the way the
  content tests found 33 reactions that said the right choice's words and 12 looks at what the picture doesn't draw, all
  rewritten (ff835833, d176e577), and the orchard's yew's berries a clue named but the picture doesn't draw (0c164f70).
- **On the emulator** (QA's step `lakeland-projects`, a synthetic learner of English from Slovene, `LANI_CULTURE=lakeland`, slot 2
  of qa-slot): green on its first run, about 12 minutes on the emulator: the village a Trg; the bowling green finished, its
  landmark on the map, its card ("A bowling green · Balinišče na travi", "🚪 Go in · Vstopi: On the bowling green · Na
  travnatem balinišču ›"), in and closer up; Old Wilf's game in the afternoon ("Afternoon, son! Shall we have a game? The jack
  is up at the far end." and its three answers); the packhorse bridge finished, its card, in and closer up (the slate arch
  over the beck); the wood (the larches gold on 1 November, the Herdwick by the path); the maypole, the mill, the chapel, the
  damson orchard and the wildflower meadow by their links. Screenshots: the round's scratch folder
  `ux-lakeland-projects-a/` (lp-*.png).

## How to use

Nothing to switch on. A Lake District village's bridge serves the scenes, the packs and the project steps at once (the
steps play in the next app build that bundles them; an older app plays each step as the practice it was); the app draws
the looks and bundles the I spy clues from its next build.

QA, with a synthetic learner of English (never a real learner's data):

```
bun companion/bridge/test/fixtures/make-learner.ts <dir> --name Jan --target en --base sl
LANI_DEV_DATA=<dir> LANI_ENV_FILE=/dev/null LANI_CULTURE=lakeland LANI_QA_SETUP_PAIR=en-sl LANI_QA_LAKELAND_SHOTS=<shots> \
companion/bin/qa-slot --no-town --steps lakeland-projects
```

The step (`lakeland-projects`, only when named) finishes the bowling green and the packhorse bridge with QA's hook
`step:<id>/<n+1>`, opens each from its landmark on the map through its card's "🚪 Go in", plays the start of Old Wilf's game
on the green, and opens the wood and the round's other places by their links.

## Tap counts

Into a project's place: before, none (the landmark opened the projects sheet); now 2 (the landmark, "🚪 Go in"), as in
the other cultures. Into the wood: before, none (a tap there opened nothing); now 2 (the wood, "🚪 Go in"). A project's
step: before, a practice; now a short dialog where the work happens, as many taps as its lines.

## For the native speaker

Everything is machine-written. A spoken form is never graded wrong. To look at first:

- **English**: Jack's and Sam's "mate", "Now then!", "Aye", "love" and "lass"; "Ne'er cast a clout till May is out", "up she
  goes", "nippy", "fresh as a daisy", "Timber!", Dixon's "It's right, or it isn't."; the bowls terms (the bias, an end, a
  toucher, the mat, the ditch; "ruler" for the folding rule); "multure" for the miller's toll; the bowls' word `lawn-bowl`
  reads "bowl", as the kitchen's `bowl` does (two cards with the same English, their meanings told apart by their base).
- **Slovene**: bulin, travnato balinišče, preslica (the bellcote), kolovoz (the lane), tisa, oltarni prt; the meadow's
  flowers (trobentica, ivanjščica, plavica, krvomočnica, glavinec, zvončica, zlatica); divje hijacinte (bluebells),
  podlesek (hazel dormouse), lesna sova (tawny owl); kamniti most, zidana ograja (parapet), bisaga (pannier); grot
  (hopper), mernik / merica (multure), špirovec, napera, skrle; "Pastirju Jacku se je izgubila ovca", "Čisto po naše!";
  the duals ("Bi zbežala?"); smešnež / smešnica.
- **Italian**: pallino, tappetino, tabellone dei punti, "mano" for an end, "effetto" for the bias, pergolato for the rose
  arbour, campaniletto, centaurea nera, geranio dei prati, moscardino, allocco, armatura (the centring), picchetto,
  bisaccia, molenda, "tesoro" for "love". The chapel's bell is on-the-fell's `bell`, whose Italian is "campanaccio" (a
  cowbell): right on the fell, wrong for a chapel (in-church names the same word).
- **German**: Zielkugel, Zollstock, Drall, Durchgang, Glockengiebel, Feldweg, Altartuch, Metze / Mahlmetze, Gerinne,
  Packtasche, Hasenglöckchen, Hochsitz, "Schatz" for "love"; "Mr Tyson", "Mr Dixon" kept English as In the field has them
  (In the smithy says "Herr Dixon").
- **The facts the lines state** (sources below): flat-green bowls in Cumbria; May Day with its May Queen and the ribbon
  dance; the Westmorland damson and the Lyth Valley's Damson Day in April, picking in September; Slater's Bridge and
  Ashness Bridge; fell ponies and the packhorse trains; Herdwick lambs born black; red squirrels; hazel dormice back at
  Arnside and Silverdale since 2021; Martindale's red deer; Eskdale Mill's oatmeal; the multure; the hay meadows' flowers
  and their cutting after mid-July; Wordsworth against the larch plantations.

Sources (the helpers' searches; the packs' `review` fields are capped at 200 characters, so they are here):
lythdamsons.org.uk (thedamson.html, orchards-advice-and-info), en.wikipedia.org/wiki/Lyth_Valley,
visitlakedistrict.com/whats-on/damson-day-p2327041, en.wikipedia.org/wiki/Slater%27s_Bridge,
historicengland.org.uk/listing/the-list/list-entry/1245295, en.wikipedia.org/wiki/Ashness_Bridge,
en.wikipedia.org/wiki/Fell_pony, countryfile.com (history of Cumbria's fell ponies), sheepandwool.org/herdwick,
cumbriawildlifetrust.org.uk (great places to see red squirrels; identifying hay meadow flowers), lakedistrict.gov.uk (where to
spot wildlife), ptes.org (39 hazel dormice reintroduced), gov.uk (1000th hazel dormouse), en.wikipedia.org/wiki/Martindale,_Cumbria,
eskdalemill.co.uk/history, en.wikipedia.org/wiki/Thirlage, dsl.ac.uk/entry/snd/multure, millsarchive.org (glossary),
en.wikipedia.org/wiki/Maypole, en.wikipedia.org/wiki/May_Queen, historicengland.org.uk (maypole on the village green, Warcop),
naturescalendar.woodlandtrust.org.uk (hawthorn as "may"), en.wikipedia.org/wiki/Bowls, en.wikipedia.org/wiki/Crown_green_bowls,
bowlsengland.com, bcgba.org.uk (region 1), northumberlandnationalpark.org.uk (hay meadows), en.wikipedia.org/wiki/Yan_Tan_Tethera,
en.wikipedia.org/wiki/Guide_to_the_Lakes, rspb.org.uk (tawny owl).

## Credits

No voice was built and no ElevenLabs credit spent. A voice build of the new lines in the cast's voices (the villagers' lines, their replies and their puzzled reactions, in
English) would take about 79,100 characters: 45,000 for the eight scenes, 34,200 for the 41 steps.

## What is left

- **The native check** above.
- **Merged with the queue.** As the lead asked, this branch has lakeland-projects-b (e0b91f66, 5868b1bc) and then the queue's
  tip kaernten-projects-b (6960268f) merged in, both sides kept: the painters' Carinthian and Lakeland looks side by side
  (one `drink` for the bowling green's jug: f822f04e), the counts of both waves in the tests, the reviewed digests of the
  green and the beck taken after the merge (65362023), CultureScenesTest holding this round's eight scenes to their arts
  (the Lakeland wood's boar never there: `LakelandLooks.FOREST` fixtures; the forest exempt from sheepbells and tracks;
  the maypole scene now cues the green's own life too, 0cf38858, the beck's lamb and the wood's birds and fox, 9119511c).
- **Who lands first.** The beck's steps (`most`, `mlin`) play in part b's `by-the-beck`, and b's beck names this branch's
  `mill` and `packhorse-bridge`; each branch carries a copy of the other's files only to validate. At the rebase the lead
  keeps each branch's own (b's `by-the-beck` pack and scene; this branch's `in-the-mill`, `in-the-wood` and
  `on-the-packhorse-bridge` packs), and b names this branch's ids where a word means the same (owl, oats, sack, mouse …).
- **The shared lines.** Both branches add lines in the same places: `LakelandLooks.PLACES`, SCENES.md's two tables,
  DialogReactionsTest's count of the scene files, LakelandLooksTest's reviewed digests of the green and the bee garden, QA's
  named-only list. Kept apart line by line; a rebase keeps both. Kärnten's and the Lake District's rounds both tightened
  Friuli's checks in the shared painters (`!= null` to Friuli's value): this branch has kaernten-projects-b's commit
  (89a875ab, cherry-picked, its conflict in StreamPainter kept both sides).
- **Chronicle lines and the pictures.** Two of projects.json's step lines (in learners' villages, not changed here) say what
  the pictures don't draw: the chapel's fourth step "a heavy wooden door" (the chapel has its open iron gate; the step is
  played over the village, so nothing shows the clash), the bee meadow's fourth "the hives painted in lots of colours" (At
  the bees draws them white; no line asks to look at them).
- **The meadow's cranesbill** is drawn blue, as meadow cranesbill is; Cumbria's upland hay meadows have the violet wood
  cranesbill. The lines say "cranesbill", blue and purple.
- **Voices**: the new lines get their clips as any scene's (`voice-build`), not done here (see Credits).
