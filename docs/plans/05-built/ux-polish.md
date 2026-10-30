# Built: the loose ends (ux-polish)

*Built 2026-10-30, branch `ux-polish` (the follow-ups' second batch, part 4). Jan: "sure do follow ups"; "for choices -
make it the best - prod ready". Seven loose ends from the rounds before, for Jan (Slovene from English, an adult, a phone)
and Teo (about seven, Italian from German, the Friuli village, a tablet of 2560 × 1600 on its side and upright). Every
text written here is machine-written (the string tables' keys are the ones there already; the stories' faces are
directions, not words); the content's problems found on the way are listed for the native check at the end.*

## What, item by item

### 1. The founding screen and the news sheet read aloud

**Before.** The village's first words speak (`GameIntro`, docs/plans/05-built/onboarding.md "The introduction's
voice"); the screen before them, "🏡 Kje bo tvoja vas? · Where will your village be?" (`FoundingScreen`), and the sheet
at every return, "🌄 Medtem ko te ni bilo · While you were away" (`NewsSheet`), had no voice at all. Teo, who doesn't read
German well yet, had the founding's "Wähle, wo es stehen soll …" explained by nobody.

**Now.**

- `game/ReadAloud` (pure) says what each screen shows:
  - the founding: its title in the language learned ("Dove sarà il tuo villaggio?"), then in the base what to do and
    that it is chosen once;
  - the news sheet: its heading in both languages, then each of its newest eight lines. A chronicle line is a label,
    "target · base" (`bi`): it is cut at its middle " · " into the language learned and the base; a line in one
    language (a name) is the language learned's; a label whose halves hold a " · " each is cut at the middle one; the
    emoji and pictographs aren't said ("5 °C" keeps its degree).
- `ui/game/ReadAloudButton`: the 🔊 of the intro and the hint (`HintVoice`): the language learned in the culture's
  narrator's voice (the node's clip, made in the village's language: Italian for Friuli), else the phone's voice for
  it; the base by the phone's voice of the base. It spins while the pieces get ready, shows ⏹ while they play, a tap again
  stops it, leaving the screen stops it. TalkBack: "Preberi na glas · Read aloud".
- **The founding** reads itself as it opens, as the intro does, when the school's "🔊 Beri razlago · Read the
  explanations" is on (the default) and no screen reader is; the node voices its title ahead. Its 🔊 sits by the 🏡.
- **The news sheet** reads on a tap only: it comes at every return, and its lines are new each time (nothing is voiced
  on the node before the 🔊 is tapped). Its 🔊 sits right of its heading.
- QA's hook `news` (a debug build): the chronicle's newest lines shown as "while you were away".

### 2. The feedback card on the tablet on its side

**Before.** On Teo's tablet in landscape (1280 × 800 dp at 320 dpi) an answer's card took the lower part of the screen:
its middle up to 45 % of the height, its title and its buttons besides. A thin strip of the question was left above it,
so the sentence just answered couldn't be read against the correction.

**Now** (`ui/PlayerScreen`, `FeedbackPlace`): on a screen at least 720 dp wide and wider than tall (the tablet on its
side, a phone turned) the card slides in beside the question: 40 % of the width, 340 to 520 dp, its middle scrolling in
the height there is and its buttons kept in view at the bottom right. The question keeps the rest, whole. Upright (the
phone, the tablet held up) the card stays under the question as before. The question keeps its place in the composition
either way, so what was typed survives a turn of the tablet; only the card moves between its two places. Every run of
exercises gets it (`ExerciseRun`): a module, a review, a pack's words, a request's challenge, a building's words, the
numbers and the cases trainers.

### 3. Faces in the evening stories

**Before.** The story panel showed the teller (the header's live portrait, a still face by each line; faces.md), but the
46 curated stories carried no faces: every line was told at rest, or with what the moment brings (a smile on the first
line and after a right pick).

**Now** every story has its faces, directed as faces-content did the dialogs (each telling read whole, the teller's
character in mind; a story's lines without `who` are the teller's, so a line and each choice's reply carry `"face"`,
never a choice or a turn). The faces script of faces-content, taught that a story's line without `who` is the teller's,
inserted only `"face"` keys and proved it: each file deep-equal and byte-equal to the base with the faces taken out,
only the contract's names, on allowed places. The bridge's `faceErrors` checks them in the smoke.

| Culture | Teller | Stories | His lines | Right replies | Wrong replies | Faces |
|---|---|---|---|---|---|---|
| primorska | Stari Janez | 16 | 264/496 | 199/205 | 369/369 | 832 |
| friuli | Nonno Bepi | 10 | 97/189 | 86/86 | 120/120 | 303 |
| kaernten | Opa Sepp | 10 | 103/204 | 80/80 | 122/122 | 305 |
| lakeland | Old Wilf | 10 | 98/199 | 87/87 | 124/124 | 309 |
| **all** | | **46** | **562/1088** | **452/458** | **735/735** | **1,749** |

By face: laugh 344, curious 323, happy 235, surprised 227, confused 208, proud 118, sad 101, worried 86, thinking 70,
cross 30, shy 7.

How they were directed:

- **His lines**: about half. A question to the learner is curious ("Dimmi: dove vivono le agane?"); a twist, magic, a
  discovery surprised ("Lovec ustreli Zlatoroga.", the flare); danger worried, never scary for the children ("Shh! Non
  svegliamo l'Orcolat!"); a loss sad and gentle (Lepa Vida; the 1976 earthquake in L'Orcolat; Millstatt's drowned son);
  a saying and "nekoč" thinking ("Ko bo brada devetkrat okoli mize, se bo kralj zbudil."); the legend's feat, a moral,
  Janez's Triglav proud; a comic tale's own "Ha!" laugh. A plain greeting stays at rest (the first line smiles anyway,
  `DialogMood.lineFace`); A1 and A2 give the same beat the same face.
- **A right choice's reply**: nearly all; a reply that goes on with the tale takes that part's face ("Das ist
  gefährlich." worried, "Buona e triste." sad). Six dry ones of Janez's ("Prav. Poslušaj.") rest.
- **A wrong choice's reaction**: all of them, as in the dialogs: confused, surprised ("Buongiorno? A quest'ora?"),
  curious, laugh where it's funny, shy when it lands as a compliment (Bepi: "Rossa? La mia faccia? È il fuoco!"), cross
  only mild and comic (being said ti by Jan: "Tikaš me? No, naj bo. Ogenj greje oba.").
- **The registers**: Janez dry and proverbial for an adult (11 % laughs, more thinking and proud); Bepi, Sepp and Wilf
  grandfathers for a child (laugh 250 of their 917, cross six times and only in play).

### 4. The Friuli school

- **QA's `friuli-school` step** waited for "Sto preparando le prossime lezioni" after QA's hook `school:reveal`. The
  Italian outline now ends at A1 (18 units, all written: no unit planned), so `School.preparing` is false and the school
  shows "✅ 18 unità fatte · 18 Einheiten geschafft" with nothing next. The step reads the outline: with units planned (or
  listed and not written) in A1 or A2 it waits for the preparing line as before, else it checks A1 done in one line and no
  preparing line.
- **Lorenzo offered I spy during the roll call.** A happening that has others with it (`Happening.with`: Maestra
  Elena's appello with Lorenzo and Giulia, Mojca's dual with Tine and Zala) is a lesson (`Happenings.lessonIn`). While it
  is on:
  - the class's own happenings wait wherever they are, in the room itself too (before, only in other scenes), and the
    room's other happenings wait (`Happenings.claimed`): the map's bubbles and the scene agree;
  - the room offers no game of «Vedo, vedo» (`ISpyController.offer(lesson = …)`; QA's hook still forces one);
  - requests stay: they are the map's bubbles over their givers, the same inside.
  Once the roll call is done for the day, the room's own come back. The QA step opens A scuola with the roll call on and
  no dialog, and checks no child offers the game.

### 5. QA's visit-home and coast steps

Both were fixed on main by infra (60908e38: `visit-home` expects Friends after "⌂ Home", where the visit began; the coast
step's "open village" after Nonna Rosa's talk uses `is_home`). Run again here, `visit-home` passes; the coast stopped
earlier, at a wild animal: a hare on the map hopped on between the screen's dump and the tap, so its card never opened
and the step failed before the talk. An animal whose card doesn't open is a note now, as the talk's look is (its
Italian word on the map is the check that matters; when its card opens its word is checked as before), and the coast
runs to its end, past Nonna Rosa's talk and "open village" to the village and the town on the coast. The founding's
reading is checked in setup, where a learner without a village founds it (`founding_read`).

### 6. The export carries a hosted child's marker

**Before.** A phone set up "🧒 Za otroka · For a child" keeps its marker in `<filesDir>/lani/child`, beside the data, so
an export of Teo's phone carried nothing of it: imported on a node or another phone, his bridge booted as a grown-up's
(no `LANI_CHILD`: the tutor's child paragraph gone, tool approvals relayed). A node's child profile (`lani profile add
--child`) lives in the registry, not in the data: its export didn't say it either.

**Now.**

- The manifest's learner has `"child": true` for a child's (absent otherwise, as before): `DataExport.write(child = the
  phone's marker)`; `lani_export.export` (the registry entry by `--profile` or by data directory, else the default
  learner's `LANI_CHILD`). `lani import --verify` says "…, a child".
- An import on a phone sets the marker before a bridge is made over the data (`LANI_CHILD` is read as it boots), and
  takes it back if the import fails.
- An import on a node marks the registered profile a child's (`profiles.json`, written atomically) and says so;
  `--dry-run` says it would. Into the default learner's data it says to set `LANI_CHILD=1` in `lani.env`; into data no
  profile has, to give it a child's profile.
- Never the other way round: a grown-up's export into a child's phone or profile leaves it a child's (the setup chose it;
  a parent changes it).
- Every export test runs with an empty registry of its own (`LANI_PROFILES_DIR`), never the checkout's `profiles/`.

### 7. The tutor key's error, typed

`LiveTutor.setKey` returned English ("the key was refused (401)", "the key is empty") and `TutorKey.problem` looked for
"401", "refused" or "empty" in it: a network message that said 401 read as a bad key, and a reworded message would have
broken the setup's plain words. It returns `KeyProblem` now (`Empty`, `Refused`, `Failed(text)`, the text redacted), and
both the hosted onboarding's tutor step and the settings' tutor page say it through `TutorKey.problem`: the settings say
a refused key in the learner's words too ("🔑 Ta ključ ne deluje …") instead of "⚠️ the key was refused (401)".

## How to use

- The founding: nothing to do; it reads itself (with "🔊 Beri razlago" on); 🔊 by the 🏡 again, a tap stops it.
- The news sheet: 🔊 right of "Medtem ko te ni bilo".
- The tablet: turn it; the card goes beside the question.
- A child's data: `lani export --profile teo` → `lani import FILE --profile teo` keeps Teo a child's; the app's
  "📤 Izvozi moje podatke" on Teo's tablet carries his marker.
- QA: `companion/bin/qa-slot --no-town --steps read-aloud,feedback-tablet,story-faces` (a synthetic learner,
  `LANI_DEV_DATA`; `LANI_QA_READ_SHOTS`, `LANI_QA_FEEDBACK_SHOTS`, `LANI_QA_STORY_SHOTS`); the coast and
  `friuli-school` steps as before (companion/README.md, "Test").

## Tap counts

| Journey | Before | After |
|---|---|---|
| Hear the founding screen | not possible | 0 (it reads itself), 1 to hear it again |
| Hear the news sheet | not possible | 1 (🔊) |
| Read the question against the card, tablet on its side | a swipe in a 33 px strip, the options never in view | 0 (the question, its options and the card side by side) |
| The same on the phone, a long explanation | not possible (16 px of the question shown) | 0 (the sentence asked in view) |
| Teo's story with the teller's faces | 0 | 0 (the same taps; the faces come with the lines) |
| The roll call's room | the I spy offer beside the lesson | the lesson alone (one thing to do) |
| A child's data to a node | export, import, then `profiles.json` edited by hand (or not known at all) | export, import |
| The tutor key refused, in the settings | 2 (paste, Check) | 2 (the message in the learner's words) |

## Tests

On the branch rebased onto main (e5c5d810: map-culture and native-check-sl merged):

- **Android**, the whole unit suite through lani-slot.sh at the end: **3,494 tests, 0 failures, 2 skipped** (441
  classes). A run before it showed 2 failures in `L10nTablesTest`: the visitor's new key (`villageSheets.visitingToday`)
  was written to the tables while that run went on (the code read at test time, the tables from the build before). New
  and changed:
  - `game/ReadAloudTest` (3): the founding in it · de and sl · en from each language's table in all twelve pairs; a
    chronicle line's halves, a name, halves holding " · ", an odd count, emoji gone, "°C" kept; the sheet's heading and
    its eight newest lines.
  - `ui/FeedbackPlaceTest` (2): beside on the tablet on its side and a phone turned, under it upright; the card's width.
  - `game/scene/HappeningsTest` (+1, 13): the roll call alone on in its room (the globe and the ball waiting), the
    lesson found; without it the room's own; someone not in the class goes on elsewhere.
  - `tutor/live/LiveTutorTest` (+1, 23): empty, refused (a 401 on GET /v1/models), failed (the key never in the text).
  - `app/SetupFlowTest` (15): the typed problems in plain words.
  - `hosted/HostedExportTest` (+1, 7): a child's export from the phone read back and verified by the node's script, a
    grown-up's without the flag, a node's child profile read by the app.
- **Bridge**: `bunx tsc --noEmit -p .` clean; the smoke (`LANI_SMOKE_PORT=9170`, `…FRIULI…=9173`, `…TOWN…=9176`; its
  Drama 3 fake worker on its default, port + 40: a later run belongs on `LANI_SMOKE_DRAMA3_PORT=9188`): 3,131 checks, all
  passing. Twice, at a
  load average of 53 (three agents' Gradle and four emulators), one check failed: "POST /words: the tutor hears the words
  added, gathered in one line" (it waits 1.3 s for a note gathered 1 s after the first word); at a quiet moment it
  passes. Nothing of the bridge changed here; the stories' faces pass `faceErrors`. `tutor-export --check`: 88 files
  match.
- **Python**: `companion/tests` 132 (11 skipped), `test_export` 19 among them (+1: a child's profile by `--profile` and
  `--data`, verify, the dry run, the registry marked, a grown-up's export into a child's profile, unregistered data, the
  default learner, `LANI_CHILD`); `tests` 251.
- **The stories' faces**: the faces script's proof on all 46 files against the base and again against main after the
  rebase: exit 0, 1,749 faces. (The script, faces-content's taught stories, lived in the session's scratchpad and went
  with the machine's reboot; the proof's output is above.)

## On the emulator

`companion/bin/qa-slot`, synthetic learners only (`make-learner.ts`: Ana, Slovene from English; Teo, Italian from German,
Friuli, a child, with and without a village), the debug build of the base (2437193f, "before") and of the branch
("after"), the dev bridge with a fake ElevenLabs where a line is voiced. Screenshots in
`/tmp/claude-1000/-mnt-scratch-github-fentas-ai-slo/721724ea-4389-48ad-b641-b6d1d5157803/scratchpad/ux-ux-polish/`
(`before/`, `after/`), and in `companion/qa-shots/` of each run.

| What | Before | After |
|---|---|---|
| Teo's founding (it · de) | no 🔊; nothing read (`founding-teo.txt`) | it reads itself: "1/3 it clip «Dove sarà il tuo villaggio?»" (the Italian narrator's clip), then the German by the phone's voice; ⏹ by the 🏡 while it plays |
| The news sheet, Ana (sl · en) and Teo (it · de) | no 🔊 | 🔊: 14 pieces, the heading and six lines, each "sl node" then "en tts:en" (Teo's "it node" then "de tts:de"); a tap stops it |
| The story by the fire, Ana | the teller's faces: happy and at rest only | Stari Janez's portrait says who and how; his faces include curious, surprised, sad and proud from the content (`story-faces.txt`) |
| The story al fuoco (Teo's village) | (the same: the moment's faces only) | Nonno Bepi: curious, surprised, worried, sad and proud from the content (`story-bepi-faces.txt`) |
| The feedback card, tablet on its side | 33 px of "Jaz ___ brata." under the card, the options out of view | the question, its options (the wrong one marked) and the card side by side: 72 px of the question's line, all of it |
| The same, the tablet upright | 72 px shown, the card under it | the same |
| The same, the phone | 16 px of the question shown | 95 px: the sentence asked in view, the card under it with its buttons |
| The roll call's room (Teo) | "🔍 Vedo, vedo · Ich sehe was", "Giulia: Giochiamo a «Vedo, vedo»?" during the roll call | Maestra Elena's lesson alone; the roll call played to its end after it |
| QA's friuli-school | stopped at the preparing line (on main) | passes: "✅ 18 unità fatte" in one line, the card, the room, the roll call, the tablet |
| QA's visit-home | (fixed on main by infra) | passes: Friends with "Borgo di Prova" after "⌂ Home" |
| QA's coast | stopped at a hare that hopped away from the tap (its card never opened) | passes end to end: the founding read, the beach, the horizon, the tablet, «scoiattolo» on its card, the talk's goals in it · de after Nonna Rosa's page (no "open village" failure), the village and the town on the coast |

## What's left

- **The Italian school ends at A1.** The outline lists no A2 unit, so once Teo has done all 18 the school says "✅ 18
  unità fatte" and Home's tile "tutto fatto", with no "Sto preparando le prossime lezioni". Either A2's units go into
  `companion/course/it/course.json` as planned (the course's writers), or `School.preparing` counts a listed level with
  no units yet as more to come. QA's step reads the outline and checks whichever holds.
- **The phone's card under a long explanation** now leaves the question its first 140 dp; the stage band above it (the
  villager's portrait and line, about 200 dp) still takes its room. Folding the stage while a card is up would give the
  question more on a small phone.
- **The news sheet's lines** are said as the chronicle wrote them, in the pair of the day they were written (a line
  from a visit in another pair is cut the same way).
- **A grown-up's export into a child's phone or profile** leaves it a child's on purpose; a parent changes it
  (`lani profile` has no "set child" yet: `profiles.json` by hand on a node, the onboarding's "for me" on a phone).
- **The stories' content problems** below, for the writers and the native check; nothing was changed in the texts.
- **A request's line read aloud**: the four cultures' `quests.json` write the base half as a gloss without its subject
  ("Zia Nives ti chiede aiuto · bittet dich um Hilfe", Jan's "Babica Micka te prosi za pomoč · asks for your help"), so
  the 🔊 says "bittet dich um Hilfe" alone. `{giver}` in each base half would make it a sentence (on screen too).
- **The faces script** (faces-content's, taught that a story's line without `who` is the teller's) lived in the session's
  scratchpad and was lost with the machine's reboot. A tutor's new story gets its faces from the tutor (the publish
  tools' `FACES_SAID`); bring a script into the repo if the curated stories are directed again.

## For the native check

The app's one new label, machine-written in de and it: `villageSheets.visitingToday`, "{name} ist heute zu Besuch",
"{name} oggi è in visita" (sl and en as the line read before).

The faces add no words. What the reading of the 46 stories found, not changed:

**Primorska (Slovene, Jan's):**

- Forms without `{m:|f:}` where the learner is addressed, while the same story has them elsewhere: bogatinovo-zlato B1
  (the turn after #7, "…ne bi približal"; #14 "Kaj pa bi ti naredil, če bi našel…?"), desetnica A2 #18 "Lepo si
  povedal.", kekec A1 #3 "Predrzen si kot Kekec!", kralj-matjaz B1 #13–15 "bom … pogledal pod lipo", pegam-in-lambergar
  B1 #14, povodni-moz A2 #2 "boš izvedel", veronika-deseniska (chapters 1–2, B1: "Bi jo rad slišal?", "Kaj bi ti naredil,
  če bi bil zaprt…", "Si si zapomnil…?", the choices "Zapomnil sem (si)…", "Kaj bi rekel Hermanu…"), zlatorog B1 #13
  ("…če bi bil tisti lovec?", while its choice #14 has `{m:bil|f:bila}`), zmaj-ljubljanski B1 #14, zvon-zelja A2 #19
  "Kaj bi si zaželel ti?".
- A bare "{m:Fant|f:Dekle}, …", which Janez never says (his villager file): kralj-matjaz A1 #20, lepa-vida B1 #16,
  peter-klepec A2 #20, povodni-moz A2 #4.
- Lines that don't fit: pegam-in-lambergar B1 #8, a choice "Jan? Upam, da nisva v sorodu." (works only for a learner
  called Jan); jurij-kozjak chapter 3 A1 #21, "Oho, tikaš? Janez se samo nasmehne." (a stage direction in his own line);
  kralj-matjaz B1, the same moral twice (#8 and #21, which opens "Tako je." after "Tudi jaz tako mislim…"); veronika
  chapter 2 B1 #19 "Njenega groba niso nikoli zares našli" against every level's Braslovče and Jurklošter; kekec A2 #7
  "Povej: zakaj je bila žalostna?" though the telling never said she was sad.
- The levels disagree: peter-klepec (A1 "daleč čez hribe", A2 "nazaj čez reko"); pegam-in-lambergar (A1: three heads
  always, A2 and B1: "tretjič"); jurij-kozjak chapter 2 (A1 a hidden door, A2 a tunnel).
- zmaj-ljubljanski B1: #2 "Bi mi povedali vi?" (the final vi may sound forced); #4 "Hm, kako prosim?" without the comma
  everywhere else has ("kako, prosim").

**Friuli, Kärnten, Lakeland (Teo's and the children's villages):**

- friuli/le-agane: A1 #14 the girl asks the forbidden question herself, A2 #14 her sister does.
- kaernten/die-perchtl A2 #1: "Hast du dich als Kind auch vor etwas gefürchtet?" asks a child about their childhood
  ("Fürchtest du dich auch vor etwas?").
- kaernten/der-dobratsch A1 #4: "Das war im Jahr 1348 … Es war Winter" in the past inside an A1 telling in the present.
- kaernten/das-kasermandl A2 #12: a ghost's punishment after death, heavier than the rest of a child's village (A1
  leaves it out).
- kaernten, the stories of order 6–10: many reactions are generic ("Der? Hm, wie bitte?" in die-maultasch A1 #3 and
  die-tausend-statuen A1 #16 word for word; die-heilige-hemma A1 #3, #15, #20; das-kasermandl A1 #3, A2 #9, #15;
  die-perchtl A2 #3, #9): a writing pass would have them take the slip at face value as Bepi's and Wilf's do.
- lakeland/the-giants-grave: A1 #2 "As tall as a tree", A2 #4 "as tall as two men" (and the "about fifteen feet" grave).
- lakeland/the-hob A2 #1: "Did you help Granny Maggie today?" names a villager the learner may not have met (a dialog
  variant waits for the people it names; a story doesn't).
