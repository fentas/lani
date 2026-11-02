# Built: Friuli's village projects as scenes, part a (friuli-projects-a, wave D)

Jan, 2026-11-02: "yes complete" (followups-10, 1–2). Friuli was the one culture whose thirteen village projects had no
`project-steps/`: each step was a practice, 5 exercises with the leader on the stage (docs/plans/05-built/friuli-scenes.md,
"What is left"). Primorska, Kärnten and the Lake District have all thirteen. This branch does seven: `mlaj`, `most`, `mlin`,
`balinisce`, `kapelica`, `vinograd` and `cebelji_travnik`. `friuli-projects-b`, at the same time, does the other six and integrates
this branch at the end.

Everything written in a language is machine-written (Claude) and waits for a native check: every steps file and pack says so in
its `review`, the supplement in its `meta`.

## What

### The steps

`companion/cultures/friuli/project-steps/<project>.json` (`lani.project-steps/v0`, SCENES.md "Project steps"): each step a short
dialog that does what its chronicle line says, with the project's leader and helpers. projects.json is unchanged: its `task` and
`line` agree with the pictures, and its lines are in learners' villages.

| Project | Steps | Tellings | Lines | Turns | Wrong choices (naming a page) | Tap turns | `done` lines | Where played | Pack (words) |
|---|---|---|---|---|---|---|---|---|---|
| `mlaj` L'albero della cuccagna (Davide) | 5 | 15 | 85 | 30 | 43 (12) | 5 | 15 | 1–2 Nel bosco (the fir chosen, felled: `fell`; carried off), 3–5 In piazza (bark off and soap on, the wheel of prizes, raised: `band`, `maypole`, `climb`) | `progetto-cuccagna` (14) |
| `most` Un ponte sul ruscello (Franco) | 6 | 18 | 96 | 36 | 51 (14) | 3 | 12 | Al ruscello, 4 Nel bosco (the logs); 6 the first cart (`cart`, no `done`, as Primorska's) | `progetto-ponte` (14) |
| `mlin` Il mulino sul ruscello (Franco) | 6 | 18 | 99 | 39 | 54 (16) | 2 | 18 | Al ruscello, a `done` in every step; the last, `wheel` as the water comes | `progetto-mulino` (16) |
| `balinisce` Il campo da bocce (Bepi) | 5 | 15 | 77 | 31 | 44 (12) | 3 | 0 | In piazza, under the linden | `progetto-bocce` (16) |
| `kapelica` La cappelletta (Nives) | 6 | 18 | 102 | 36 | 48 (18) | 0 | 0 | over the village (its road has no scene), the last step's `bell` | `progetto-cappelletta` (16) |
| `vinograd` Le terrazze della vigna (Matteo) | 7 | 21 | 122 | 42 | 58 (22) | 6 | 0 | 1–6 Nel campo, 7 over the village (the evening on the terrace, grape juice) | `progetto-vigna` (16) |
| `cebelji_travnik` Il prato delle api (Aldo) | 6 | 18 | 106 | 36 | 49 (19) | 9 | 0 | 1–2 Nel campo (mown, sown), 3–6 Dalle api (the hives made and painted, the little house, the first honey: `smoke`, `honey`) | `progetto-prato-api` (16) |
| **7 projects** | **41** | **123** | **687** | **250** | **347 (113)** | **28** | **45** | | **108 words** |

**Three levels.** The other cultures' steps are written at A1 and A2. Teo's target is B1, and a learner reads the highest telling up to
their level (`Arrivals.levelFor`), so at A2 they would stay at the last A2 telling. Each step here also has a B1 telling, the
same event in other words, and every B1 telling tests a rule of B1 (46 grammar pages are named in all, 18 of them B1's: condizionale,
congiuntivo, periodo ipotetico, pronomi combinati, si impersonale, passivo, gerundio, relativi con cui, trapassato, stare per, futuro
anteriore, indefiniti, infinito con preposizioni, congiunzioni, discorso indiretto, passato remoto, verbi pronominali). No page above a
telling's level. The bridge's check asks for A1 and A2 at least and takes more; no code changed for it.

**For a child.** Explanations are German first, with "du", the Slovene and the English the same in substance. The grown-ups fell, saw and
mow while the child points, counts and carries a little; no wine for a child (grape juice at the vineyard's feast); nobody gets hurt.
The registers are the cast's: Lei and «signor Franco», tu and «nonno», «zia», tu with Davide, Matteo and Aldo. Everything that agrees with
the learner is a `{m:…|f:…}` pair. A text names only the project's leader, and a helper's line says nothing of who says it (no "sono
stanco", no "mio nonno"): another may say it when the helper is away.

**Friulian touches, settled** (sources below): «Mandi!», «la roggia» for the mill's channel, «i ronchi» and «la ponca» on the vineyard's
terraces, the Ribolla gialla, frico and gubana, the cuccagna soaped with its wheel of prizes, the bocce words (pallino, accostare,
bocciare, sponda). A spoken form is never graded wrong: the forms a proofread found heard in speech (si mette le pietre, più alto che, ci
serve + plural, c'è + plural, se + conditional) were replaced by plainly wrong ones.

### The words

Seven packs named as Friuli's are (`progetto-<Italian name>`, unique over every culture), 108 words with their article, plural, examples and
notes in all the languages, each pack's `project` its project: the projects sheet shows them on its card and the packs screen in its
own section. No new prompt clash (`bun test/prompt-clashes.ts`): new words got prompts of their own (`corteccia` "tree bark", `posto`
"spot, place to be"). `l'asse`, the bridge's plank, is the mill's axle's spelling: `"homonym": true` on both (the flag added to
`nel-mulino`'s word, nothing else of it). Words shared with another pack keep that pack's meanings, so they are one card.

Friuli had 53 packs against 65: the difference is the project packs (13 elsewhere, one pack more of Friuli's own). I spy: nothing was
missing. All 33 of Friuli's scenes have their clues, and every slot of the scenes these steps are played in.

**The supplement** (`companion/lexicon/it.extra.json`): 75 entries, the everyday words of the new steps that no Italian content had
before (abbastanza, buttare, il coniglio, durare, oppure, reggere, la paratoia, Pinocchio …), German first, with the forms the steps use. The
deliberately wrong forms (togliuto, sparguto) stay out, as the file's meta says of the older ones.

### The look

`FriuliProjectLooksTest` (new) draws every stage of the greasy pole on the square and in the wood, the bridge and the mill on the stream, the
bee meadow beside the bee garden and the finished places, beside Primorska's (`build/scene-snapshots/looks-projects/friuli-*`). One thing was
missing: the chronicle's third step says the pole is soaped, but it only shone once it stood. As it lies on its trestles, from that step, it has
the wet sheen and the glint now, and its wooden bucket of soap by its foot (`SquarePainter.soapedLying`, `square.maypole` "cuccagna"). The rest
agrees with the lines: the mill's plank walls and shingles are what projects.json says, the chapel's coppi are Friuli's look, the bridge's
pillars of cobbles and brick, the hives painted a plain colour each. No slot, stage spot, effect or art is new, so `SceneArt.VERSION` stays;
an older app draws the old picture.

## An older app, a village in the middle

- **Teo's tablet (0.2.698)** bundles project steps with the app, so its steps stay the practices they were until it is updated. It reads the new
  packs from the bridge at once: that version already reads a pack's `project` and lists it with the projects (checked against its source), and
  ignores unknown keys. Nothing else is served differently.
- **A village past a step or in the middle of a project:** the state is only how many steps are done, so it goes on from there, at the next step's
  dialog. `FriuliProjectStepsTest` plays each of the 41 steps from a synthetic village with that many done (never Teo's data): the step on offer, its
  dialog at each level, paid, its chronicle line, its landmark one stage on; a finished project has nothing left to offer. On the emulator the
  bridge's fifth step and the pole's third are played that way.

## How to use

Nothing to switch on. QA, with a synthetic Teo (never a real learner's data):

```
bun companion/bridge/test/fixtures/make-learner.ts <dir> --name Teo --target it --base de
LANI_DEV_DATA=<dir> LANI_ENV_FILE=/dev/null LANI_CULTURE=friuli LANI_CHILD=1 LANI_QA_SETUP_PAIR=it-de LANI_QA_LEARNER=Teo \
LANI_QA_FRIULI_PROJECTS_SHOTS=<shots> companion/bin/qa-slot --no-town --steps friuli-projects
```

The step (`friuli-projects`, only when named) readies the bridge's fifth step at A1 and the pole's third at B1 (QA's hooks "step:<project>/<n>",
"level:<lv>"), opens each from its landmark on the map to the projects sheet, plays it to its end card with the shots at its `done` line, then the
pole's first at A2 (the fir tapped in the picture) and the chapel's last at A2 over the village (its bell).

## Tap counts

A project's step: before, the sheet's button and a practice of 5 exercises; now the same button and a short dialog where the work happens, as many
taps as its lines. From the map to the step: 2 (the landmark, "🔨"), as before.

## For the native speaker

Everything is machine-written. To look at first:

- **Italian, wrong forms that must really be wrong in speech too:** "a chi" and "in chi" for a cui, in cui; ci vuoi mille; se tirasse io; arrivassero with l'acqua; più alto come; si mettete; mi la presti; mi ne dai; noi servono;
  togliuto, appenduti, sparguto, mettuto, cominciarà, scegliarei, metterebbi, disegnaresti, scaveramo, costruò.
- **Italian, wording:** «dritto» against «diritto»; «non ha fatto una piega» (the bridge); «paratoia» (the mill, B1); «scandola»; «pala» for a wheel's paddle; «Posso aiutarla a misurare» as a child's Lei; «la radio gracchia»; «un secchio d'uva»; «Sono i coppi»; «inciampa» in a reaction.
- **German:** Zielkugel and Bande (from the existing pack), Radschaufel, Brückenbrett, "nah heranspielen" for accostare, Hohlziegel, Bienenstand, "Tante" for zia, "Oje" for uffa.
- **Slovene:** mlinščica for roggia, lopatica kolesa, deska za most, vodoraven, čvrst, "Tla se izravnajo", dedek for nonno; lopata for vanga, korec, ponjava, gomoljika, zeljna glava; "v Colliu, nad Colliem, iz Collia" (or Brda?), rebula; ronchi and ponca left untranslated.
- **The supplement:** the 75 new entries' German and Slovene meanings.

The facts the lines state, with their sources:

| Fact | Source |
|---|---|
| «Mandi»: Friulian greeting, hello and more often goodbye | [Omniglot](https://omniglot.com/language/phrases/friulian.php), [ProZ, quoting it.wikipedia](https://wiki.proz.com/kudoz/4401095), [it.wikipedia, disambiguation](https://it.wikipedia.org/wiki/Mandi) |
| The cuccagna: a greased pole, a wheel at the top with cured meats and treats hanging | [Milanofree](https://www.milanofree.it/milano/tradizioni/albero-o-palo-della-cuccagna-cimento-della-tradizione-contadina.html) |
| In Friuli: the climb at a village's feast, cheese and salami to win (Bordano) | [Eventi e Sagre](https://www.eventiesagre.it/Eventi_Feste/21066295_Sagra+Di+Sant+antonio+Abate.html) |
| «La roggia», a Friulian mill's channel of water | [Capitanio, La roggia di sant'Odorico](https://sa-fvg.cultura.gov.it/fileadmin/materiali/CAPITANIO_Enrica__La_roggia_di_sant_Odorico.PDF) |
| «I ronchi», the Collio's terraces | [vino.com, Ronco del Frassino](https://www.vino.com/produttore/ronco-del-frassino), [Meininger](https://www.meininger.de/wein/erzeuger/die-huegel-von-torre-rosazza) |
| «La ponca», layers of sandstone and marl | [Stefan Gourmet](https://stefangourmet.com/2025/05/19/friuli-wine-tour-part-2-collio/) |
| Ribolla gialla, the Collio's old white grape | [Wine Industry Advisor](https://wineindustryadvisor.com/2025/09/25/ribolla-gialla-gradisciutta-captures-and-bottles-all-the-tradition-of-the-collio-region/) |
| Bocce: points by who is nearest the pallino, boards round the court, accosto and bocciata | [The federation's rules](https://www.sporteimpianti.it/wp-content/uploads/2016/11/regolamenti-bocce-2020.pdf), [IIENSTITU's Italian test](https://www.iienstitu.com/en/tests/it-c1-spiegare-le-regole-del-gioco-delle-bocce) |
| The carnica bee, the region's own | [The regional bee laboratory, 2025](https://laboratorioapisticoregionalefvg.uniud.it/fileadmin/user_upload/Relazione_Finale_2025.pdf) |
| A hive's colour helps a bee find its own (a cue among several) | [Honey Bee Suite](https://www.honeybeesuite.com/will-different-colors-help-my-bees-find-the-right-hive/), [Mann Lake](https://www.mannlakeltd.com/blog/how-colorful-bee-hives-help-prevent-drift-and-why-your-bees-will-thank-you/) |
| Young vines give grapes after about three or four years | [University of Maryland](https://extension.umd.edu/resource/training-and-pruning-grapes), [Iowa State](https://yardandgarden.extension.iastate.edu/faq/when-can-i-start-harvesting-newly-planted-grapes) |

Invented for the story, not facts about a place: the bridge's seven-metre logs, Franco's counts (twenty planks, a thousand shingles, twenty-four
paddles). Plain reasoning: shingles weigh less than tiles. A general proverb, said by Franco's father: «misura due volte, taglia una».

## Credits

No voice was built and no ElevenLabs credit spent. A voice build of the new lines in the cast's voices (the villagers' lines, their replies and
their puzzled reactions, in Italian) would take about 54,500 characters, at three levels; the learner's own choices aren't counted.

## How it was checked

CHECKS

## What is left

- **The native check** above.
- **Part b's six projects and its gate.** friuli-projects-b adds the other six and merges this branch. Shared lines: SCENES.md's and GAME.md's
  paragraphs on Friuli's steps, `SceneCoherenceTest`'s count of the done lines, `it.extra.json`'s end (both append: keep both), `FriuliProjectStepsTest`'s
  list of projects (and its count, 41 × 3), QA's lists.
- **Voices** (see Credits): the node voices the lines when they are first played, or `voice-build`, Jan's to spend.
- **Teo's update:** the steps play from the next app build Teo installs.
