# Built: Friuli's village projects as short scenes, part b (friuli-projects-b, 2026-11-02)

Jan, 2026-11-02: "yes complete" (wave D, batch 1, followups-10 sections 1–2). Friuli was the one culture whose thirteen village
projects had no `project-steps/`: every step was a practice of five exercises (friuli-scenes.md, "Project steps and the ages'
parties"). Primorska, Kärnten and the Lake District have all thirteen. This branch writes six of them; `friuli-projects-a` writes the
other seven (the maypole, the bridge, the mill, the bocce court, the chapel, the vineyard, the bee meadow) at the same time.

The projects here: the fire station (`gasilski_dom`), the playground (`igrisce`), the fountain on the square (`vodnjak_na_trgu`), the big
hay barn (`toplar`), the lookout tower (`razgledni_stolp`) and the clock on the bell tower (`mestna_ura`). Each has its steps as short
scenes at **A1, A2 and B1** (`companion/cultures/friuli/project-steps/`), said by the project's leader and helpers in Friuli's scenes where
they happen, and its word pack (`packs/progetto-<name>.json`).

Two helpers wrote the steps and packs (content-1: the fire station, the playground, the tower; content-2: the fountain, the barn,
the clock); the tests, the art review, the dictionary entries, the docs and the QA are this branch's own. All the Italian, Slovene, English
and German is machine-written, and every file's `review` says so.

## The way in, before and after

| A learner of Friuli taps a project's step … | Before | Now |
|---|---|---|
| from the projects sheet, or from its landmark on the map and then the sheet (2 taps) | a practice of 5 exercises over the village | its short scene where it happens, at the learner's level, then its line in the chronicle |

Taps stay as they were. What changes is what the tap opens.

## What's missing, found out

Friuli had 53 packs against the others' 65. The difference is exactly the projects' packs: the Lake District has 13 `project-*` and 52 others,
Friuli 53 others and none of them. So part b adds six, named as Friuli's are (Italian, as Kärnten's are German): `progetto-casa-dei-pompieri`,
`progetto-parco-giochi`, `progetto-fontana`, `progetto-grande-fienile`, `progetto-torre-panoramica`, `progetto-orologio`. Pack ids are one
across every culture (the app bundles them side by side by file name), and `progetto-` has no other user.

**I spy:** the six projects' places already have their clues (`companion/ispy/scenes/`: Dai pompieri, Al parco giochi, La fontana, Il grande
fienile, Sulla torre panoramica, L'orologio). A step opens no I spy of its own, so none were needed.

**The art:** nothing was missing. The three projects a painter draws step by step already draw every stage in Friuli's look; rendered and
looked at (`FriuliProjectStagesTest`, `build/scene-snapshots/looks/friuli-stages-*.png`):

- the fountain on the square: the pegs and string, the block of white stone on its beams, the trough with its pillar and pine cone, the
  ditch, the pipes, the water;
- the clock in the free-standing campanile: the slit, the dark round window, the face from the step that lifts it in;
- the big hay barn in the field, beside the brick fienile: the oak logs, the stone feet, the posts, the beams, the plank roof, the hay.

The fire station, the playground and the lookout tower aren't drawn while they are built (`DRAWN_PROJECTS`), so their steps have no `done`.
Their finished places have their looks since places-it-arts. No `task` or `line` in `projects.json` was wrong against the picture, so none changed,
and no chronicle line in a village changes.

## The steps

| Project (leader) | Pack (words, new) | Where each step is played | Dialogs | Lines | Turns | Wrong (naming a page) | Tap turns | `done` lines |
|---|---|---|---|---|---|---|---|---|
| `gasilski_dom` (Pastore Davide) | `progetto-casa-dei-pompieri` (13, 4) | foundations, walls, the big door, the pump from Gorizia in In piazza; the first drill Al ruscello (the hose); the fête in In piazza (the band, the dancing) | 18 | 125 | 39 | 58 (19) | 0 | 0 |
| `igrisce` (Maestra Elena) | `progetto-parco-giochi` (12, 1) | the drawings A scuola (the chalk tapped); the stones, the swing, the sandpit and slide, the first day of play In piazza | 15 | 99 | 35 | 52 (16) | 1 | 0 |
| `vodnjak_na_trgu` (Fabbro Bruno) | `progetto-fontana` (13, 5) | the plan Nella fucina; the stone (the cart), the carving, the ditch In piazza; the spouts Nella fucina (the hammer tapped, the sparks); the water In piazza | 18 | 121 | 36 | 57 (18) | 3 | 6 |
| `toplar` (Mugnaio Franco) | `progetto-grande-fienile` (14, 2) | the oaks Nel bosco (the oak tapped and felled); stone feet, posts, beams, roof, first hay Nel campo | 18 | 114 | 36 | 57 (18) | 3 | 12 |
| `razgledni_stolp` (Ostessa Marisa) | `progetto-torre-panoramica` (14, 3) | the hill Nella vigna; the foundations and the fir Nel bosco (felled); the frame and the stairs over the village; the board and the first guests Sulla torre | 21 | 132 | 44 | 74 (21) | 9 | 0 |
| `mestna_ura` (Maestra Elena) | `progetto-orologio` (14, 6) | the face's size A scuola; the round window In piazza; the gears Nella fucina; the hands In bottega; the numerals A scuola; lifted in and striking twelve In piazza | 21 | 130 | 42 | 63 (21) | 0 | 6 |
| **6 projects, 37 steps** | **80 words, 76 different, 21 new** | **35 steps in a scene, 2 over the village** | **111** | **721** | **232** | **361 (113)** | **16** | **24** |

A step whose scene isn't open today (the square in a young village, the vineyard before it is planted, a household's workshop) plays over the village
as every culture's does. The fountain's third and sixth, the barn's third and the clock's seventh steps have no `done` by design, as `SceneCoherenceTest`'s `atTheEnd`
says of every culture's.

**The levels.** A1 is the present; A2 the passato prossimo, the imperfect, the future, pronouns and modals; **B1 is new among the cultures' steps** and asks what B1
has: the conditional, the subjunctive, si impersonale, the passive, the pluperfect, the gerund, the period with «se», combined pronouns, «cui», reported speech. Every
level has at least one form turn at its own level's grammar and a turn of meaning. A learner meets the highest telling up to their level (`Arrivals.levelFor`), so A1 and A2
learners play exactly what they would in another culture, and Teo meets B1's when they reach it. The rules not yet introduced gate as anywhere: `FriuliProjectStepsContentTest` checks that a
fresh learner at each level still has something to answer.

**A village in the middle.** Nothing is migrated: a project's count of steps stands, and the next step is simply a dialog now. `FriuliProjectStepsContentTest` builds a synthetic
village past each step of each project (never Teo's data): its count and its landmark's stage stand, the next step opens as a dialog, played to its end it counts one more and tells its
chronicle line; a finished project offers nothing. An older app has its culture pack bundled, so it plays its practices until it is updated; it can already read the packs the
bridge serves.

**The Friulian touches**, each settled below: «Mandi!», the fogolâr, frico and polenta; the villotta at the firefighters' fête; the stone from Aurisina on the Karst at the fountain's B1;
Monte Sabotino, Monte Santo, the Julian Alps and the sea at Grado from the tower.

**Words and forms.** main's supplement-it (merged here) glosses the Italian content as it stood and left these steps' new words "for the next count". This branch does that count for its six projects: 119 new entries and 210 forms in 122 old ones in `companion/lexicon/it.extra.json` (1,873 → 1,992 entries), through `it_cov_build.py` with a table of their own (`companion/lexicon/tools/it_cov_30_progetti.txt`; each new meaning's prompt its own, `--prompts`). `lexicon-build --coverage --lang it --all`, with no Wiktionary, as a phone has it: 99.9 % of tokens and 98.9 % of words known, up from 99.2 % and 91.8 %. Of the 54 words no entry knows, nearly all are wrong forms offered on purpose (scavono, erava, corruti) and letters spelled out. So a tap on a step's word explains it in German or Slovene, and a form a step asks counts on its card.

**A spoken form is never wrong.** On review, six distractors that Italians really say or that had a reading of their own came out («Si passa i mattoni», «C'è tanti sassi», «Ci vuole le
fondamenta», «Ieri c'era due piani», «Sebbene la salita è lunga», «Ha detto che avrà fatto»), and content-2 took out its own three (sebbene with the indicative, «più grande che», «è stato fatto
una finestra»). Each distractor left is meant to be a plain mistake. Three kinds remain for the native check: below.

## How it was checked

On the branch with main 1e0cdf04 merged (part a isn't merged here; the lead names that branch):

- **The content**: `bun test/check-culture.ts friuli` ok; `bun test/check-steps.ts friuli`: 6 files, 37 steps, 111 dialogs, 721 lines, ok; `bun test/prompt-clashes.ts`: no new clash (the
  coined prompts: below). A scratch lint with the rules the app's tests hold (a reaction of at most 80 characters, never saying the right word; only the leader named; a helper's line without the speaker's
  gender; pages at the level) found nothing left but one reviewed look (the tower's first step, A2: «Guarda le montagne, il mare e le vigne», all things the art draws).
- **The Android unit suite** (lani-slot.sh): the union's numbers are under "The union" below. New: `FriuliProjectStepsContentTest` (8 tests; `FriuliProjectStepsTest` until part a's test of that name came: the steps at their levels with their words; short, said by the project's people, in a scene; reactions, played with
  every wrong choice first; placeholders for a boy and a girl; a fresh learner has something to answer and each word is tested at each level, gated too; a helper's lines; a village partway through; the level and the fallback over the village),
  `FriuliProjectStagesTest` (2), `StepScenesRenderTest`'s Friuli test. Changed: `SceneCoherenceTest`'s count of done lines (+24). `MentionsContentTest` holds the steps' names to the leader. `DialogCastTest` and `WhoAnswersTest` pass unchanged, but they read the scenes, not the steps: who says a step's lines is held by `check-steps` and `FriuliProjectStepsContentTest`.
- **The bridge**: `tsc` clean; the smoke on ports 9670–9688: 3,520 checks, all passed; `tutor-export --check`: 89 files match.
- **Python**: 253 tests, and 145 with 11 skipped, all passing.
- **The emulator** (`qa-slot`, a synthetic child, `LANI_CULTURE=friuli`, `LANI_CHILD=1`, it · de, a phone 1080×2400): QA's step `friuli-projects-b` green on its seventh run, and again on an APK rebuilt after main's merge. Its cast is met on the dev bridge's copy first, since a village the
  hook makes up has its leaders not yet met, and the sheet rightly offers "Fai conoscenza" and holds the step until then. Each from its landmark on the map to the projects sheet (its step, where it is played, today's words marked), "Il passo di oggi", the dialog:
  the fire station's fourth step at A1 (the pump's cart coming up In piazza, «Arriva un treno.» with its reaction, the end card «🚒 La casa dei pompieri 4/6», the chronicle's line, +5 ♥ Davide), the fountain's second at B1 (the pegs and string on the square, then the white block on its beams from the done
  line, the Tipp offered on its form turn, «Direi che è una pecora nera.» with its reaction, the end card 2/6, +5 ♥ Bruno). The earlier runs stopped on the step itself or on the queue's timeout, never on the app: a run that timed out waiting for an emulator, the button below the fold, the leaders not met, a tap held
  long enough on the loaded machine to open a word's card, the right answer tapped again in the talk above, a landmark's target looked for before the village was adopted. Screenshots: the round's scratch folder `ux-friuli-projects-b/qa/` (fb-*.png).
  Seen there, and only QA's doing: QA plays two steps in one day, which a village never does, so the pump's cart stayed on the square (`fx_stays`) into the fountain's step.
  **Not played on the emulator**: the other four projects, an A2 telling, the two steps over the village, a tap turn, a tablet. Those are held by the unit tests only.

## The union (friuli-projects-a f61a0d0c and the queue tip tutor-languages 33a06fb3, merged here)

All thirteen of Friuli's projects: 13 files, 78 steps, 234 dialogs, 1,408 lines (`check-steps friuli`). Kept from both: SCENES.md's paragraphs, QA's named-only list, the done count (45 + 24).

- **The tests.** Both parts had a `FriuliProjectStepsTest`. Part a's keeps the name and now lists all thirteen, counting (41 + 37) × 3. Part b's is `FriuliProjectStepsContentTest`; it asks other things (the words tested at each level, gated too; the placeholders; a helper's Slovene and Italian), and over part a's steps too.
  Over part a's steps its rule against a helper's gender flagged four right lines («voda ne skoči sama», «tla so bila polna»): a word that agrees with something else, not the speaker. The rule now looks for a first person («sem prinesel», «sama sem»), which is what it is for, and no line was reworded. `StepScenesRenderTest` keeps part a's helper under one test, at least 15 + 8 steps.
- **The words.** la pala is a shovel and a wheel's paddle: one spelling, so `homonym` on both, and the smoke's list has it. il piano and piano are two spellings (`normWord` keeps the article), so the homonym rule doesn't fit and its smoke check would fail: the storey's id is now `piano-edificio`. alzare, il progetto, scavare and il muro take part a's meanings in every pack, each pack keeping its own example and note; none was voiced or reviewed. la vanga's Slovene is «vrtna lopata», apart from la pala's «lopata».
- **The supplement.** Rebuilt with `it_cov_build.py` from the file before supplement-it (`git show 45924165:companion/lexicon/it.extra.json`) and all seven tables, then by hand what no table has: `farsene` under fare, and the queue's licôf, villotta and fiaccola. **A plain `--write` loses those four.** 2,049 entries, the exact union: main's and the queue's 1,876 with no entry or form lost and no meaning changed; part a's 73 and part b's 119, less the 19 both had or that the queue's entry replaces. Where the tables overlapped, one version: lasagne and tagliatelle are part a's plurals (b's singulars gone), papera says goose and duckling, risata's German is Gelächter, apart from ridere. `--prompts` lists five clashes, all main's own.
- **Coverage** (`lexicon-build --coverage --lang it --all`, no Wiktionary): 99.8 % of tokens, 97.9 % of words over 244 files. Of the thirteen projects' words, none real is left unknown: only wrong forms quoted on purpose. The 110 unknown words overall are those and the queue's newer content (pista, quartiere, drappo, tradizione …), for the next count.
- **A fix from the queue tip.** `bridge/src/features/family.ts` imported `learnerLangs` twice (from 63798862's merge of parity-small), so `tsc` failed on `tutor-languages` itself; Bun runs it anyway. One line removed here (972ae903).

## How to use

Nothing to switch on. QA with a synthetic child (never a real learner's data):

```
bun companion/bridge/test/fixtures/make-learner.ts <dir> --name Teo --target it --base de
LANI_DEV_DATA=<dir> LANI_ENV_FILE=/dev/null LANI_CULTURE=friuli LANI_QA_SETUP_PAIR=it-de \
companion/bin/qa-slot --no-town --steps friuli-projects-b
```

`bun test/check-steps.ts friuli [project]` (companion/bridge) checks the steps and packs; `FriuliProjectStagesTest` renders the stages (`app/build/scene-snapshots/looks/friuli-stages-*.png`).
QA's hook "step:<project>/<n>" readies any step, and "level:B1" its telling.

## Voices

No ElevenLabs credits spent. A voice build of the steps in the cast's Italian voices would take **54,676 characters** (`bun test/step-chars.ts friuli`: 1,562 texts, none voiced by other content): the fire station
9,052, the playground 7,667, the fountain 8,287, the barn 8,709, the tower 11,089, the clock 9,872. By level, of the lines and reactions counted the same way: A1 29 %, A2 33 %, B1 38 %. The 21 new words and their examples
add about 800.

## For the native speaker

All machine-written. To look at first:

- **Italian, distractors near real usage** (they must be plain mistakes; a spoken form is never wrong): congiuntivo after «bisogna che», «è importante che», «è necessario che» with the indicative as the wrong
  choice (at B1: the fire station's first step, the playground's second, the fountain's first, the barn's fifth, the clock's sixth; the tower's sixth has «vorrei che c'è»); «se … -rebbe» (the fire station's drill, the barn's posts); «più sicura come ieri», «più grande come il quadrante»; «un scivolo»; «di che parla la maestra»
  (literary rather than wrong?); «ce l'abbiamo fatto»; «Ho voglia a ballare».
- **Italian, idioms and wording**: «bagnati come pulcini» and its joke «asciutti come pulcini»; «raccogliere i sassi dal prato» (or togliere); «Bianca e fine» of stone; «È duro, e dura»; «tre dita ogni dieci passi»;
  «L'hanno presa dalla cava»; il piano as a storey; il progetto as a smith's drawing; la struttura for the tower's frame; la base for a post's stone foot; il cannocchiale at the top of the tower where its finished
  place has il binocolo; «il grande fienile» (the field also has its brick fienile).
- **Italian, jokes**: the B1 pun «Chi va piano… piano dopo piano»; «lo potiamo alzare» as a pun on potare; the speaker's own sayings, written for these steps and presented as theirs, no tradition: «Chi taglia un albero, ne pianta due»,
  «Chi costruisce sul nulla, poi piange», «Chi sale senza fretta, arriva senza fiato»; arrabbiata and spaghetti; the black sheep; «Duro come la testa di un nonno».
- **Slovene**: «Mokri smo kot miši», «srček» for tesoro, «mineštra», scorrere's coined prompt «teči kot voda», base as «podstavek», palo as «steber» in the lines and «kol» on its older card, «Človek bi rekel», «Meni je štetje
  ušlo», the duals and genders generally.
- **German**: «pudelnass», «Der Arme, der die isst», «Jause» (Austrian, on purpose), scavare's coined prompt «in der Erde graben», «zwei Kilo süß» (a deliberate absurdity).
- **English**: «drowned rats».
- **Facts to look at**: the block on the cart is white as the picture and the line have it, and the real Aurisina stone is light grey to taupe, so the lines say only that the stone comes from there and the
  Romans used it; Gradež for Grado and Sabotin as the Slovene names (Sabotin's page gives Sabotino as the Italian name); the speakers' common knowledge, unsourced: oak is hard, fir soft, wood rots in water, brass shines.

Sources (each fact a line states):

- «Mandi»: [Friulian dictionary](https://www.lexilogos.com/english/friulian_dictionary.htm). The villotta: [Wikipedia](https://en.wikipedia.org/wiki/Villotta).
- The stone: quarried at Aurisina/Nabrežina near Trieste since the Romans, used in Aquileia, light grey to taupe: [IUGS Geoheritage, Aurisina Stone](https://iugs-geoheritage.org/?p=13183).
- The view: Monte Sabotino, the Collio, the Isonzo, the Friuli plain, the Julian Alps ([The Walk of Peace](https://www.thewalkofpeace.com/locations2/outdoor-museum-sabotin-the-park-of-peace/)), Monte Santo over the valley
  ([Montagna di Viaggi](https://www.montagnadiviaggi.it/2023/12/gorizia-passeggiate-dove-camminare.html)); the names: [Sveta Gora](https://en.wikipedia.org/wiki/Sveta_Gora), [Sabotin](https://en.wikipedia.org/wiki/Sabotin).
- Proverbs said as proverbs: «Chi ha tempo non aspetti tempo», «L'unione fa la forza» ([Wikiquote](https://it.wikiquote.org/wiki/Proverbi_italiani)). Scorso as scorrere's participle: [Treccani](https://www.treccani.it/vocabolario/scorso/).
- Santa Barbara, the old pump from Gorizia, the Tricorno: as the existing scenes have them (places-it-arts.md).

## What is left

- **The native check** above, and **the voices**: about 54,700 characters, Jan's to spend.
- **The tower's steps played on a stand-in**: its board and first guests are played Sulla torre, on the watchtower's picture, as Primorska's are, since its own place opens only once it's finished.
- **`ProjectStepsContentTest`** still checks Primorska's steps only; Friuli's have `FriuliProjectStepsTest` and `FriuliProjectStepsContentTest`, the other two cultures' `check-steps`.
- **No turn has two right answers** in these steps (a greeting, a yes): they could get a few when a native reads them; nothing requires it.
- **The step's header on a phone**: «La pietra dalla cava · Stein» runs right up to the "Traduzione" chip while it wraps. It doesn't overlap; it's the shared header, not these steps.
- **Noticed, not this branch's**: `l-orologio`'s pack says «Ogni domenica Bruno carica l'orologio», while the steps say «abbiamo caricato»; nothing contradicts.
