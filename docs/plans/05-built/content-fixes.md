# Built: content that was wrong

*Built 2026-10-30, branch `content-fixes` (the follow-ups round, part 3). Jan: "sure do follow ups"; "for choices - make
it the best - prod ready". The problems were found while the faces were written into the dialogs
(docs/plans/05-built/faces-content.md, "Problems in the content") and by the proofreader. Every text written here is
machine-written: the list for the native check is at the end. No spoken form was "corrected"; ti or vi and "pote" are
left as they are, on the list.*

## What, item by item

### A child's reply said by an adult (6 arrivals): a turn names whom the learner answers

A reply was said by whoever spoke last before the turn (`Arrivals.kt` `repliers`). In six arrivals the adult has the last
word ("In jaz imam spet koga razvajati!") and the learner then welcomes the child, so Micka said Zala's "Dobrodošel? Saj
nisem fant, sem deklica!", Vida Nejc's "Jaz sem punca!", Rosa Giulia's, Franco Lorenzo's, Marisa Tommaso's.

**The code**: a learner's turn may name whom they answer, `to` (`DialogLine.to`; the bridge's `dialogLine`):

```json
{ "who": "micka", "sl": "In jaz imam spet koga razvajati!", "…": "…" },
{ "to": "zala", "choices": [ { "sl": "Me veseli. Dobrodošla v vasi!", "ok": true, "reply": { "sl": "Hvala! …" } }, "…" ] }
```

- That person says the turn's replies, its reactions and its puzzled line, in their voice and with their face
  (`DialogRun.replier`), in every kind of dialog: an arrival, a project's step, a party, a keeper's talk, a scene's.
- The panel's portrait and the one in the picture who reacts are them from the moment the turn comes up, not only once
  they have replied (`DialogRun.facing`, used by the arrival's stage, a step's stage, the scene and the panel's header).
- One rule for who answers, `repliersOf` (the turn's `to`, else whoever spoke last, else the dialog's person), used by
  arrivals, project steps and parties (`Projects.repliers`), the clips gathered for the day (`DayAudio.dialog`), the car's
  plays and shadowing (`RoadPlay`), and voice-build on the node (`voice.ts`: scenes, arrivals, steps). A template's or a
  step's cast maps `to` as it maps `who`.
- The bridge checks it (`turnToErrors`): only on a turn, and one of the dialog's people (an arrival's speakers, a scene's
  people, a step's leader and helpers, a party's host and guests); a story has none (the teller answers). An older bridge
  strips the key, an older app has whoever spoke last answer, as before. The tutor's guide for arrivals says it.

**The content**: `"to"` on the six turns: primorska `zala` A2 and B1, `nejc` A2; friuli `giulia`, `lorenzo`, `tommaso` A2.
Kärnten and the Lake District had the child speak just before the turn already.

### Facts

| Where | Was | Now |
|---|---|---|
| project-steps/vinograd step 5, A1 | "Še devetinštirideset!" with no fifty said at A1 | Marko says it first, as at A2: "Ha ha. Imamo petdeset trt, {learner}. Kaj postavimo k vsaki trti?" |
| project-steps/mlin step 6, A1 and A2 | `done` on Micka's baking line (A2: "Peč je že vroča") | on France's "Mlinski kamen melje žito" (A2: "Zdaj mlinski kamen melje žito"): the mill is finished as it grinds; the wheel already turns under its cue at Tine's line before |
| primorska/world kopa `bel-dim`, A1 | "Sneg? Poleti? Ne, to je dež." (the pile burns April to October) | "Sneg? Saj ni zima. To je dež." |
| primorska/arrivals `vida` A2 | "V Trnovem imam gostilno" (Nejc: "Prej sem živel v Trnovem, v gostilni") | "V Trnovem sem imela gostilno, s teraso nad dolino."; her villager story says the same ("ran the inn in Trnovo … and has come to the village to open one here") |
| friuli a-scuola `cartina-citta` | "mia nonna è di Nova Gorica" (her arrival: era) | "mia nonna era di Nova Gorica" (sl "je bila", en "was", de "war") |

### Slips

- na-vasi `klop-sneg`: the reaction to "Zima, za božič!" repeats what was said: "Zima? Za božič? To si želiš za darilo?"
- v-sotoru `zgodba`: the reaction to "Janez me je povedal" translated, not glossed: "He told you, like a story? Are you a
  fairy tale?" (de, it the same way).
- friuli nel-mulino `molenda`: the turn's puzzled line was the wrong choice's reaction over again; now its own, of the
  mill: "Come? Non ti sento, la macina fa troppo rumore!" (the reaction to "tanto farina" keeps its "Tanto? Tanto cosa?").
- "pote" / "po te" (v-podstresju `vstani-ribnik`): not changed, on the native list.

### The A1 dual

The person in brackets may be left out where the line has no subject of its own (COURSE.md, "transform"); these lines have
one ("One", "Otroci"), so the app couldn't know. Their accepted answers now say it:

- dvojina-3 "One so kuhale. (onidve)": also "Kuhali sta." and "Sta kuhali.";
- the test's "One so v kuhinji. (onidve)": also "V kuhinji sta." and "Sta v kuhinji.";
- dvojina-3 "Otroci so šli domov. (Zala in Tine)": also "Šla sta domov." and "Sta šla domov." (decided yes: the sheet
  asks for the dual, "Povej za dva", and the names only say who the two are).

The clitic-first forms ("Sta kuhali.") are accepted by the rule that clitic-first is never graded wrong; the native check
says whether they read right.

### B1a distractors graded "almost": the slip rule

`pesom` for `psom` and `sedli` (sesti's) for `sedeli` were half right: a slip in the stem is forgiven (`Grading`), and both
differ from the right word by one letter in the stem. The distractors are the right ones to offer (the e that drops, the
other aspect are the mistakes these sheets teach), so the rule changed, not the distractors: **a sheet's distractor is a
wrong form on purpose, so tapped or typed it is wrong, never almost** (`SheetItem.wrong`, `Sheets.check`). Any other slip
in the stem is still forgiven. It covers the third such distractor too, mestnik's `ogenju` for `ognju`. `CourseContentTest`
now asserts that no distractor of either course is right or almost for any gap (it listed the three as known before).

### Friuli's whys: "for the two of you"

al-fuoco `legna` and il-mare (Tommaso's `corriamo`) said "For the two of you together, say andiamo" right after
"andate means you (all)": a child reads "you two" as andate. They now name the two: "For Tommaso and you together, say
andiamo (let's go)", in en, de and sl; Kärnten's am-feuer `holz` had the same sentence ("Für Maxi und dich zusammen: gehen
wir"). The Slovene scenes' "the two of you" (v-gozdu's greva) contrast greva with gremo, both "we", and stay.

### Friuli's stories and the smoke's bridges

The stories (and the scenes) name the Italian language packs (`in-paese`, `cibo`, `famiglia`, `casa-e-cucina` in
companion/packs/it), which every Italian learner's bridge reads (`packsDirs`). The smoke's own Italian bridges (the towns'
Luka and Mia, the pairing's Luka) read a temporary packs directory without them, so they logged "culture friuli has
problems". The references are right; the smoke now gives those bridges a packs root of their own with the Italian packs
as the repository has them (`harness.ts` `italianPacksDir`: Jan's smoke bridge keeps its own, so its lookups on a visit to
Italy stay as `lexicon.ts` checks them), and checks the Friuli culture validates with them (`cultures.ts`).

### Primorska's forest: the basket to count

Anton's "Preštej gobe v moji košari" had no basket in the picture; Friuli's Aldo had one, as a culture-look variant that
showed whenever any dialog counted with someone at the clearing (Jože's roe deer would have brought it too). The basket is
now the forest's own effect, `basket`: a willow basket of mushrooms on the grass at the clearing, by whoever stands there,
with as many in it as the dialog counts (else six), each cap to count (`ForestPainter.mushroomBasket`, unchanged). Both
dialogs cue it on their first line (v-gozdu `gobe-kosara`, nel-bosco `funghi-cesto`); Friuli's look keeps its ancona only.
A basket of seven or fewer had its one row of caps along the rim, brown on the willow, nothing to count at the scene's
size: the first row now stands over the rim against the grass, as a fuller basket's back row did. For the forest painter's
owner: the basket moved from `variant("basket")` to `fxOn("basket")` in `ForestPainter.paint`, and `mushroomBasket`'s
first row is higher; nothing else of the painter changed. Renders: `ux-content-fixes/words-forest-basket-*.png`.

### Where the learner arrives: each culture's own line

The first visit said "a valley below Triglav" in every culture. culture.json has an `intro`: `arrive` in the pack's
language and the app's other three, and `emoji`, the picture over it (`CultureIntro`; `GameIntroSpeech.arrive` and
`emoji`, the screen and the read-aloud both):

| Culture | Line (its language) | Emoji |
|---|---|---|
| primorska | Prideš v dolino pod Triglavom. S seboj nimaš ničesar razen majhnega ognja. (as it always was) | 🏔️ |
| friuli | Arrivi tra le colline del Collio, vicino a Gorizia. Con te hai solo un piccolo fuoco. | 🌄 |
| kaernten | Du kommst ins untere Gailtal, unter den Dobratsch. Du hast nichts dabei außer einem kleinen Feuer. | 🏔️ |
| lakeland | You arrive in a dale among the fells with nothing but a small fire. | 🏞️ |

A culture without one keeps the app's own line. The app checks it in the pack's language (`Check.manifest`), the bridge in
all four (`validateCulture`).

### The Friuli school's first lesson

- **The summary**: the summary card was the whole `tu-lei` page, down to the polite imperative. An intro may have a
  summary of its own, `intro.summary` (per base, Markdown, ≤ 600): the card shows it with the table the steps built and the
  book one tap away, and Maestra Elena reads it (the app's `summaryScript`, the bridge's course speech). `ciao` has one:
  tu to children, friends and family (Ciao, Come ti chiami?, Come stai?, Scusa!), Lei to grown-ups (Buongiorno, Buonasera,
  Come si chiama?, Come sta?, Scusi!, Arrivederci), the same answers to both.
- **The roll call**: Elena called Lorenzo and Giulia and they answered from empty desks (a happening shows its one person);
  Mojca's dual lesson ("Tine in Zala sta dva učenca. Kaj delata?") had empty desks too. A happening may have others of the
  scene with it, `with` (`Happening.with`): they're at their spots while it is on, at its place on the map, and it is on only
  while they're here and met (`Happenings.peopleIn`, `on`; `TownMarkers.standing`); while it is on, their own happenings
  in other scenes wait (`Happenings.active`: Lorenzo's cat at the mill, which the emulator showed him away at). The roll call has `with: ["lorenzo", "giulia"]` and Elena marks them as a teacher does,
  "Lorenzo, presente. Giulia, presente. E {learner}?"; Mojca's `dvojina` has `with: ["tine", "zala"]`. The scenes stay
  dialogs with one person: the pupils are there, the learner talks with the teacher.

### BuildingWords.PACKS

The doc comment no longer says the other cultures have no rooms: a building whose culture has a room for it asks for its
scene's words first and the type's pack after them.

## Order of going out

The bridge reads content from main at once. Three content commits use keys an older bridge refuses; deploy and restart
the bridge (and the app) from this branch's code before they are on main:

- the forest's `basket` cue (an older bridge skips v-gozdu and nel-bosco: an effect it doesn't know);
- culture.json's `intro` (an older bridge's strict manifest refuses it, and the culture falls back to Primorska);
- `ciao`'s `intro.summary` (an older bridge's strict intro refuses the unit).

The rest goes out with any bridge: `to` and `with` are stripped by an older one (the old behaviour), the texts are texts.

## Tests

On the branch rebased onto main (`2bbbfb35`, the places round's Friuli buildings in): the bridge's `bunx tsc --noEmit -p .`
clean; the smoke with this part's ports, all 3,020 checks passed, no "culture friuli has problems" logged
(`LANI_SMOKE_PORT=9080 LANI_SMOKE_FRIULI_PORT=9083 LANI_SMOKE_TOWN_PORT=9086 bun test/smoke.ts`); `tutor-export --check` 87
files match; the Python tests 251 OK and 102 OK (11 skipped); the full Android unit suite 3,438 tests: two failed, the
`SceneDetailTest` frame budget at load 35 (the kitchen's closer frame 27 ms against 20: the infra part's flake) and
`L10nTablesTest` (the culture check named "intro.arrive", a key's shape: renamed, the test and the culture tests green on
the rerun). The smoke's new checks: a turn's
`to` refused on a spoken line and off the dialog's people, kept on a turn, in a scene and an arrival; a happening's `with`
refused for nobody of the scene and for its own person; the Friuli village with the smoke's packs), `tutor-export --check`
(the arrivals guide regenerated), the full Android unit suite, the Python tests. New and changed unit tests:
`ArrivalsTest` (a turn's `to`: the child replies, the panel faces her at the turn, the turn after keeps her),
`HappeningsTest` (the pupils at the roll call, not when one isn't here, not when busy elsewhere, on the map),
`SheetsTest` and `CourseContentTest` (distractors never almost), `GameIntroSpeechTest` (each culture's line, Primorska's
as before), `FriuliOutdoorLooksTest` (the basket with its cue in both looks, never with a count alone),
`SceneCoherenceTest` (the mill's done line). After the full run: `HappeningsTest` (the roll call claims its pupils),
`QaHooksTest`, `SceneOffersTest`, `FriuliOutdoorLooksTest`, the scene and villager tests (326) green.

**Emulator QA** (a synthetic Teo, `make-learner.ts --target it --base de`, LANI_CULTURE=friuli, in the emulator's lock on
emulator-5554; shots in `scratchpad/ux-content-fixes/qa/`):

- `friuli-school-unit`: the unit ciao end to end, ✅; its summary card (`fs-intro-summary.png`) is the child-sized one with
  the table the five steps built and "📖 Al libro · Zum Buch";
- the first visit (`intro-1.png`): 🌄, "Arrivi tra le colline del Collio, vicino a Gorizia. Con te hai solo un piccolo
  fuoco." over "Du kommst in die Hügel des Collio, nahe bei Görz. …";
- the roll call at 10 (QA's hooks `hour:10`, `happening:a-scuola/appello/appello`, the synthetic village with Lorenzo and
  Giulia living there and met): Elena's new line, Giulia and Lorenzo at their desks (`school-appello-dialog.png`,
  `school-appello-scene.png`). The first try showed Giulia alone: Lorenzo's cat at the mill was on, hence the fix that
  the roll call claims its pupils;
- Aldo's counting in the woods (`forest-basket-dialog.png`): "guarda nel mio cesto", the basket by him with three
  mushrooms for "tre".

The step `friuli-school` itself stops before the scene on main as it is: it waits for "the next lessons in preparation",
which the Italian school no longer says once A2 is listed ("Unità in arrivo"); not this part's, the drive above took its
place. No tap counts changed: the same dialogs, the same taps.

## What's left

- The native check below.
- The villager stories (English, for the tutor) say "in Trnovo" loosely for the village's area elsewhere too (Ančka's
  radio, Micka's potica); Vida's alone contradicted a line the learner hears, and is fixed.
- A tutor's arrival or variant can use `to` from now on (the guide says so); none of the curated scenes needed it.
- During the roll call a pupil at a desk offers «Vedo, vedo» ("Lorenzo: Giochiamo a «Vedo, vedo»?"): I spy's offer takes
  any child in the picture. Harmless, but a lesson could hold it back.
- QA's step `friuli-school` waits for the "preparing" line the Italian school no longer shows (above).

## For the native check

Slovene (Jan's; the speaker's checks):

- vinograd step 5 A1: "Ha ha. Imamo petdeset trt, {learner}. Kaj postavimo k vsaki trti?"
- kopa `bel-dim` A1: "Sneg? Saj ni zima. To je dež."
- Vida's A2 arrival: "V Trnovem sem imela gostilno, s teraso nad dolino."
- na-vasi `klop-sneg`: "Zima? Za božič? To si želiš za darilo?"
- the dual's answers without the pronoun: "Šla sta domov.", "Sta šla domov.", "Kuhali sta.", "Sta kuhali.", "V kuhinji
  sta.", "Sta v kuhinji." (the clitic-first ones are spoken order: accepted, never graded wrong; say whether any reads off)
- **unchanged, a judgement**: "Po šoli bom {m:prišel|f:prišla} pote." (v-podstresju `vstani-ribnik`, its three choices):
  "pote" or "po te"?
- **unchanged, a judgement**: ti where the villager file says vi: celebrations/vas A1 #2, Vida: "{learner}, pomagaj mi.";
  celebrations/mesto A2 #6–7, Mojca: "kaj nosiš na čelu sprevoda?", "Visoko jo dvigni"; project-steps/igrisce step 3, A1
  and A2 #7, Mojca: "potegni za vrvi!" (a party, the children round them: meant, or vi?)
- the Slovene of the other villages' texts (for a learner from Slovene): Friuli's intro "Prideš med griče Collia, blizu
  Gorice. S seboj nimaš ničesar razen majhnega ognja.", Kärnten's "Prideš v spodnjo Ziljsko dolino, pod Dobrač. …", the
  Lake District's "Prideš v dolino med hribi Jezernega okrožja. …"; the whys "Za Tommasa in tebe skupaj rečeš andiamo
  (greva).", "Tommaso in ti skupaj: corriamo (tečeva).", "Za Maxija in tebe skupaj rečeš gehen wir (greva)."; Elena's
  "Ja! In moja babica je bila iz Nove Gorice, onkraj meje."; the mill's puzzled "Kako? Ne slišim te, mlinski kamen preveč
  ropota!"; the roll call "Lorenzo, tukaj. Giulia, tukaj. Pa {learner}?"; `ciao`'s summary in Slovene.

Italian (Teo's):

- the roll call: "Buongiorno, bambini! Facciamo l'appello. Lorenzo, presente. Giulia, presente. E {learner}?"
- "Sì! E mia nonna era di Nova Gorica, di là dal confine."
- "Come? Non ti sento, la macina fa troppo rumore!"
- the intros: "Arrivi tra le colline del Collio, vicino a Gorizia. Con te hai solo un piccolo fuoco.", Kärnten's "Arrivi
  nella bassa valle del Gail, sotto il Dobratsch. …", the Lake District's "Arrivi in una valle tra le montagne del
  Distretto dei Laghi. …"; Vida's "Avevo un'osteria a Tarnova …"

German (Teo's base; the Kärnten village's language):

- the intros: "Du kommst in die Hügel des Collio, nahe bei Görz. …", "Du kommst ins untere Gailtal, unter den Dobratsch.
  …", "Du kommst in ein Tal zwischen den Bergen des Lake District. …"
- the whys: "Für Tommaso und dich zusammen sag andiamo (gehen wir).", "Tommaso und du zusammen: corriamo (lass uns
  rennen).", Kärnten's "Für Maxi und dich zusammen: gehen wir."
- `ciao`'s summary in German; "Er hat dich erzählt, wie eine Geschichte? Bist du ein Märchen?"; "Der Mühlstein ist zu
  laut!"
