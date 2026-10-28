# Built: the Friuli village's school

Jan, 2026-10-28: "Teo has a school but can't enter?" Teo, a child learning Italian from German in the Friuli village (the
coast, a Galaxy Tab S6), had built the school. Its card had no door: the Friuli culture had no school scene. And the
school itself had nothing to teach: there was no Italian course, only its outline (`companion/course/it/course.json`, 18
units, every one planned), and much of the school assumed the course was Slovene.

This note says what was built, how it is reached, what changed for a course in another language than Slovene, how it was
checked, and what is left. The formats are companion/SCENES.md's and companion/COURSE.md's.

## What

### "A scuola · In der Schule": the scene

`companion/cultures/friuli/scenes/a-scuola.json`: the Friuli school in the art `school` (as the Friuli scenes reuse the
Slovene arts: [friuli-scenes.md](friuli-scenes.md)), opened from the school (`from: ["school"]`), in Italian with
Slovene, English and German, A1, written for a child:

- **Its things**: all 14 slots of the art, each a word: `la lavagna`, `il gesso`, `la spugna`, `il banco`, `la matita`,
  `la cartella`, `il mappamondo` (its pack `a-scuola`), the chair, window and door of `casa-e-cucina`, the book and the
  map of `nella-tenda`, the exercise book of `nella-fucina`, the clock of `in-cucina` (one card wherever a word is met).
  The map and the globe come with the school's second level, the clock with its third.
- **Its people**: Maestra Elena at the board, Lorenzo and Giulia at the desks, Tommaso at the door.
- **Its happenings** (6, 14 dialogs, 28 learner's turns, 54 wrong choices, 45 of them naming an Italian grammar page):
  the morning roll call on school days ("Presente, maestra!"; Lei to her and "maestra", tu to the children), the
  mistakes on the blackboard in the afternoon (il/lo/la, un/una, essere or avere, the plural), the map of Friuli from
  level 2 (the mountains, the Tagliamento, the sea; Udine and Gorizia), Giulia and the globe (Italy the boot), Lorenzo's
  homework (the syllables, the pencils counted on the day's dice, the numbers in words), Tommaso's football and the
  merenda at break. No line asks the child's own data.
- **Its words**: `companion/cultures/friuli/packs/a-scuola.json`, 18 words (lavagna, gesso, spugna, banco, matita,
  cartella, mappamondo, astuccio, maestra, compiti, merenda, ricreazione, pallone, errore, leggere, imparare, disegnare,
  Presente!) with it/sl/en/de, examples in all four, notes; no prompt shared with another Italian word.
- **I spy**: `companion/ispy/scenes/a-scuola.json`, 65 clues for the 14 things, describing the Friuli picture (the map
  of Friuli, not Slovenia's).

### The Friuli classroom: a culture's look

Jan asked whether the scenes look different per culture. Only the fire's village and the horizon's landscapes did. A
general hook now lets every art draw a culture's own way (`game/scene/CultureLook.kt`, SCENES.md "Culture looks"):
colours, materials and variants of an art, named for the art, read by the painter with its own default, so the default
culture draws exactly as before (`CultureLooksTest` holds every art's digests from before the hook). Visits draw the
host's culture, words' stickers the learner's.

The school was the first art drawn on it (the kitchen, the smithy, the tent and the mill of Friuli followed on the same
hook, [culture-looks-friuli-indoors.md](culture-looks-friuli-indoors.md), and Carinthia's and the Lake District's mountains
and fire, [looks-alps-lakes.md](looks-alps-lakes.md)). In the Friuli village it is an old Italian classroom
(`CultureLooks.FRIULI_SCHOOL`):
pale yellow walls over a lower wall painted in a sea-green enamel instead of the oak wainscot, a vine with bunches of
grapes in the frieze instead of the tulips, the map of Friuli Venezia Giulia (the Alps, Udine, the Tagliamento, the Isonzo,
the lagoons and the sea; its title FRIULI) instead of Slovenia's, the tricolore on the pole, the pallottoliere where the
basket stands, and "Ciao! / Come va? / Bene!" on the board before the first lesson. The board of the Italian course
says "Benvenuti!" and the day as Italy writes it, then the learner's lesson in Italian.

### The school for Italian

| What | Before | Now |
|---|---|---|
| The course | the outline only, every unit planned | A1's first six units written (below); the bridge serves `course/it` to a Friuli village, the app bundles it |
| A school with nothing written to do | an empty level, "units to come" | Maestra Elena: "Sto preparando le prossime lezioni. Torna presto!" under her welcome, the planned units folded; Home's tile "📝 Nuove lezioni in arrivo", not "all done" (`School.preparing`) |
| The exercises' texts | English strings only (a German child read English whys) | per base, `{de, sl, en}`: instructions, hints, whys, translations, cues, labels, prompts and table columns in the learner's base (bridge `inBase`, app `Courses.inBase`, prints); plain strings as before |
| Who teaches, in which voice | the school's person (Residents) already per culture | checked for Friuli: Maestra Elena, her cast voice `teacher` (Alice), `@elena` once she has her own; the smoke reads the Italian course in her voice for a learner of German, never the German |
| The tutor's guide | "Mojca comes over from the next village …" for every village | the culture's teacher by name and the course's language ("Maestra Elena comes over …", "companion/course/it"); a planned unit isn't promised |
| The teacher's lines | Italian "Benvenuto … Si sieda" (Lei), "Questo lo sa già"; German "Setzen Sie sich", "Das können Sie schon" | "Benvenuti … Sedetevi" (the class), "Questo lo sai già", "Setzt euch", "Das kannst du schon": Elena says tu to the children |
| "Only the Slovene is read" (no voice for the base) | in every table, whatever the course | the language learned, by `targetLang` |
| The blackboard | "Dobrodošli!" and "14. 10. 2026" for any course | the course's language: "Benvenuti!", "28/10/2026", the Italian lesson |
| Prints | headings "Pravilo · …", the examples read from `sl`, the words from Slovene packs, the whys only in English, bases en/de/it | headed in the course's language ("Regola · Die Regel", "Unità", "Scuola · Italiano"), its examples and table cells of the course's language, the words from the Italian and the culture's packs (with their article), the per-base hints and whys in the sheet's base, Slovene a base for an Italian course (never a course's own language) |
| Forms cards, counting cards, the numbers module, case rows | Slovene only | still Slovene only, and guarded so: the Italian units name no `lemma`/`form`, `numeri` turns nothing on |

### The units

`companion/course/it/units/`: `ciao` 👋 (tu-lei), `essere` 🟰 (essere-avere), `articoli` 🏷️ (articoli-genere),
`plurale` 🐈 (plurale, articoli-genere), `avere` 🎒 (essere-avere), `numeri` 🔢 (numeri); `planned` removed from their
lines of the outline. Each: a guided intro of 5 steps (Maestra Elena's explanation German first, then Slovene and
English, every Italian form in bold with its meaning, examples from the village, the table growing, a quick question
each), three worksheets (21–30 items: sorts, tables, gap texts with a word bank and without, transform lists, choices, a
reorder, a multi), a test of 10–14 items at pass 80. All flagged machine-written in `review`, with what a native speaker
should look at. `plurale`, `avere` and `numeri` were written by a helper on the same brief.

### Found on the way: a scene turned while it draws

QA's tablet shots (the window resized while a lesson's intro showed the school behind it) crashed the debug build:
`ArrayIndexOutOfBoundsException` in `ArtPainter.collectHits`. A scene's pictures are made anew for a new view size
(`SceneView`: `remember(fit.width, fit.height, painter, closer)`), with the same painters, while the old pictures' render
thread may still be drawing; each held a lock of its own, so one painter drew on two canvases at once and read past the
end of one. A release build lost that frame; a debug build threw. On Teo's tablet, turned between landscape and portrait,
the same could happen. `ScenePictures.lock` is now the painter itself, so the pictures of one painter never draw on two
threads (`TownPicturesTest`: a scene resized, its old and new pictures drawn together).

## How it is reached (taps)

| Journey | Before | After |
|---|---|---|
| The village → the school's scene | none: the card had no door | 2: the school on the map, "🚪 Entra · Geh hinein: 🏫 A scuola ›" |
| The village → a lesson's first step | none: the card's "🏫 Scuola" to a school with nothing to open | 3: the card's "🏫 Scuola", the unit (★ the next), its "▶️ Avanti: Introduzione" |
| Home → a lesson's first step | none: Home's school tile to a school with nothing to open | 3: Home's school tile, the unit, its "▶️ Avanti: Introduzione" |
| A school with nothing written left | "all done" on Home's tile, an empty level | 1: Home's tile "📝 Nuove lezioni in arrivo" to Maestra Elena saying so |

## How it was checked

- **Content**: every unit validates in en, de and sl (`validateUnit`), its pages are the Italian book's; every right
  answer is graded right by the app's Italian grading and no word-bank decoy is right or almost (`ItalianCourseContentTest`,
  and a port of the grading over all six); the culture validates with the new scene, pack and clues (`validateCulture`);
  the learner content audit (no Jan, no malformed placeholder, no turn the same for one gender).
- **Bridge**: `bunx tsc --noEmit -p .` clean; the smoke's course section (texts per base: read in de, in English by
  default, without English refused, served in the store's base), its Friuli section (the scene served from the school,
  `GET /course` Italian with six units written in order and twelve planned, a unit's exercises in the child's Slovene,
  the sheet printed headed "Regola · Pravilo", the guide naming Maestra Elena), its course-speech section (Elena the
  school's person in her cast voice; the Italian course read in her voice for a learner of German, never the German; the
  corpus).
- **App**: `CultureLooksTest`, `SchoolLooksTest` (the Friuli classroom at detail 1–3, the same things to tap, the greeting
  clear of the board's sun, as fast as the Slovene), `SchoolBoardTest` (the Italian course's every letter, its lessons,
  "Benvenuti!"), `CourseTest` (texts per base), `ItalianCourseContentTest`, `SchoolRevealTest` (preparing),
  `CultureScenesTest` (A scuola from the school, every slot once), `DialogReactionsTest` (19 + 17 scene files); the full
  suite (see the report).
- **Prints**: `companion/bin/school-prints --language it --base de` makes A1's six sheets and the booklet (4 units on one
  page, 2 on two).
- **Emulator** (a synthetic Teo, below; each step a run of its own, about 8 minutes with the setup):
  - `friuli-school`: the school standing with A1's six units done: Maestra Elena "Sto preparando le prossime lezioni.
    Torna presto!", the planned units folded, on the phone and on the tablet (2560 × 1600 on its side and upright); the
    village at 21:00, the school on the map, its card's "🚪 Entra · Geh hinein: 🏫 A scuola", the scene in the evening;
    at 10:00 the roll call (Maestra Elena, "Presente, maestra!" chosen, then "La lavagna è verde."), the Friuli classroom
    on the phone and the tablet, closer up.
  - `friuli-school-unit`: the unit ciao end to end in it · de: the intro's five steps (three choices, two gaps typed),
    the step 1 on the tablet both ways, the summary, the first worksheet (the sort, 12/12), the test straight away
    (10/10, pass 80 %), "Unità completata!", and the dev bridge's village keeping `it/ciao` done.
  - Both green with no crash (runs 9 and 10 of `ux-friuli-school`); the screenshots `fs-*.png`.

## How to use

Nothing to switch on: a Friuli village's bridge serves the Italian course and the school's scene once this is merged and
the node's checkout has it; the app's next build bundles the course, the clues and the school's look.

QA, with a synthetic Teo (never a real learner's data):

```
bun companion/bridge/test/fixtures/make-learner.ts <dir> --name Teo --target it --base de
LANI_DEV_DATA=<dir> LANI_ENV_FILE=/dev/null LANI_CULTURE=friuli LANI_CHILD=1 LANI_QA_SETUP_PAIR=it-de LANI_QA_LEARNER=Teo \
LANI_QA_FRIULI_SHOTS=<shots> flock /tmp/lani-emulator.lock companion/bin/qa --no-town --steps friuli-school
# the unit end to end, a run of its own: the same env, --steps friuli-school-unit
companion/bin/school-prints --language it --base de --png --learner Teo --gender m
```

## What is left

- **Units 7–18** of A1 are the other course writers' (course-it-b, course-it-c); until they are merged the school says
  Maestra Elena is preparing them.
- **A native speaker's check** of the Italian, German and Slovene of the scene, its pack, its clues, the six units, and
  the machine-written strings (`_note_friuli_school` in the four string tables).
- **The other arts' culture looks**: the hook is there; other agents style the kitchen, the smithy, the field … on it.
- **Forms cards and counting for Italian**: the forms book, counting cards and the numbers module are Slovene's; an
  Italian forms book would let the units name `lemma`/`form`.
- **Italian words' meanings on the prints**: words no pack has (essere, qui, stanco …) have no meaning yet (no Italian
  Wiktionary extract); the sheet leaves them out and the run lists them.
- **Voices**: Elena's lines of the scene and her course are voiced on demand by the node, or ahead with
  `voice-build --only teacher` on a Friuli node.
- **The intro's summary card for a child**: a unit's summary is its first grammar page whole (COURSE.md: never copy a
  page into the unit), so ciao ends on `tu-lei` with the polite imperative (vada, venga) too, more than a first lesson
  for a child needs. A child's version of the Italian pages, or a summary of the unit's own, would be gentler.
- **The class in the roll call**: a happening shows its one person (`who`), so Lorenzo and Giulia answer "Presente"
  from empty desks; the Slovene school's scene is the same. A happening that brings the class would need a field for it.
- **The game's arrival in the Friuli village** says "a valley below Triglav" (`gameIntro.arrive`, every culture): not
  this round's, seen in QA.
