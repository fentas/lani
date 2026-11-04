# Built: speaking and free writing inside a unit (lesson-production, 2026-11-03)

Wave E, batch 1, item 2 (followups-14.md): "Course items are choice, transform, gap text, table, sort, reorder, multi,
cloze." A unit had no item where the learner speaks or writes their own sentences. Now two of the school's types do,
`speaking` and `writing`, in every language's course, modelled in one unit per language at B1 and at B2. Everything the
content says is machine-written (Claude) and waits for a native check: the list comes first.

## Native check

All 40 items (8 units × the 4th sheet's three and the test's two, Italian's 10 each with a child's version) are new
text: the sentences, the models, the `spoken` lists, and what the rules accept and refuse. The questions for a native
speaker, by unit (the file, then the sheet `<unit>-4` and the test's last two):

- **sl `pogojnik-polno` (B1):** *šu* for *šel* as spoken and right ("Če ne bi deževalo, bi šu s tabo", "Šu bi s
  tabo, …"); *s tabo* and *s teboj* both; "Kaj bi počel, če bi jutri zapadel sneg?"; Granny's "Rada bi, da bi me
  večkrat obiskal"; the note to Vida ("Skupaj bi kosili in sušili seno, zvečer pa bi pri tebi jedli potico. Ko bi le
  jutri posijalo sonce!"); "nov mlinski kamen za Franceta".
- **sl `pisni-jezik` (B2):** *ste prejela* and *a ste* (for *ali ste*) as spoken, right when said; "Prosim Vas za
  prijavnico za tečaj"; "Hvala za vabilo, z veseljem pridem"; both formal e-mails (the cooking course, the street lamp:
  "Za vprašanja sem Vam na voljo"); whether *Pozdravljeni* may count as the greeting (it is in the rule, and *Lepo
  pozdravljeni* at the end then meets both greeting and close).
- **it `periodo-ipotetico` (B1):** the avoid *se* + condizionale (*se avrei*): a learner's mistake here, or said in
  Friuli (then it may not be an avoid); "Se domani c'è il sole, giochiamo a calcio?"; the letter to Zia Nives (*Sarei
  contentissimo*); the children's lines (*Lo chiamerei Fido*, *Mangerei tanto miele*, *Se nevicasse, farei un pupazzo
  di neve con Tommaso*).
- **it `raccontare` (B2):** the passato prossimo labelled as spoken and right for a story told aloud ("Nonno Bepi ha
  aperto la porta e ha visto un vecchio"), as northern Italy tells; the tale of the fox and the coin and the tale of
  the linden; *C'era una volta un piccolo cane* for a child; "Che cosa faceva Moro, quando sei tornato a casa?".
- **de `wenn-ich-haette` (B1):** *Wenn ich Zeit haben würde* as spoken and right; *tät* among the forms counted; the
  avoid *wenn ich hätte* (verb second after *wenn*) also matches the right "wenn ich hätte kommen können", rare at B1;
  the messages to Florian (*Kaiserschmarrn*, *Gute Besserung*) and to Kathi (*Tombola*, *Kirchtag*, "auf einen Kaffee
  einladen").
- **de `formeller-brief` (B2):** *Frau Magister* and *Frau Magistra*; *ich ersuche Sie*; "Grüß Gott, wie kann ich Ihnen
  helfen?"; the two letters (the bench and roof at the bus stop, the Termin); the avoid catches *Mit freundlichen
  Grüße*, *freundliche Grüßen* and *Mit freundliche Grüße*, not *Freundliche Grüße* alone (right, and common).
- **en `if-i-had` (B1):** *If it wasn't raining, I'd come* and *if I was you* as spoken and right; the avoid *if I
  would* (is it said in Cumbria? then it can't be an avoid); the notes to Mrs Birkett and Sam.
- **en `formal-informal` (B2):** *Could you send us a form* (us for me) as spoken; *I'm ringing*, *Hiya*, *ta for*;
  the letter to Mr Dixon about the footbridge, the e-mail to Jack.

The app's new strings (`production.*` in `l10n/{sl,en,de,it}.json`, `_note_production`): "Tvoje pisanje", "V testu to
šteje le, ko te sliši mikrofon: zdaj je to vaja.", "Uporabi", the words' plural ("# beseda/besedi/besede/besed"),
"Primer odgovora", "Učitelj bere …", "Učitelj bo prebral, ko bo dosegljiv; odgovor pride v klepet.", "Učitelj", "Napiši
še", and their German and Italian; the print's headings "Povej in napiši", "Parla e scrivi", "Sprich und schreib" and the
lengths ("25–60 besed", "mindestens … Wörter", "fino a … parole").

## What the learner gets

- **A speaking item** (`speaking`): the task in their language, the teacher's question in her bubble and her voice (her
  clip, else the node's or the phone's), then 🎤 as in a module's speak exercise: the phone's recognizer or the node's,
  three tries, 💡 toward the hidden model, "Povej pravilno · Say it correctly" after a miss. Either "say it" (one sentence
  of the unit's grammar, the model and other right sentences) or "answer aloud" (an open answer: any sentence that uses
  what it must is right).
- **A writing item** (`writing`): the task, what the text must use (ticked ✓ as it is met on a worksheet; a list to keep
  to in the test), the field with the language's letters, the words counted; "Preveri · Check" checks it at once; then the
  checks, a model text, and the tutor's feedback under it once it comes (and in the chat).
- **Where:** each model unit's new 4th worksheet "Povej in napiši · Speak and write" (it *Parla e scrivi*, de *Sprich und
  schreib*, en *Speak and write*): a "say it" item, an "answer aloud" item, a writing item; and the last two items of its
  test, one of each. The units: sl `pogojnik-polno` (B1) and `pisni-jezik` (B2), it `periodo-ipotetico` (B1) and
  `raccontare` (B2), de `wenn-ich-haette` (B1) and `formeller-brief` (B2), en `if-i-had` (B1) and `formal-informal` (B2).

## Decisions

**A spoken form is never wrong.** A speaking item lists what people say beside the model whole (`spoken`: right, "🗣️
Pogovorno, prav tako prav; knjižno: …"). In speech the grading table's `nonstandard` is right too (`Grading.Hint.SPOKEN`):
the table marks what *writing* wants, and the learner is speaking ("She sings good", "weil er hat keine Zeit" aloud).
A recognizer's sound-spelling is the standard (Slovene *u vasi* and *f šoli* for *v*). An `avoid` can never be a form
people say: the bridge refuses one that matches the item's `spoken`, the smoke one that matches the table's `nonstandard`.

**No microphone or no STT.** No microphone, no permission, no recognizer for the language, or "Brez mikrofona · Can't
speak now": the learner says it anyway and rates themselves (✓ / ✗), as in a module; a phone recognizer that can't take
the language hands over to the node's when there is one. Nothing ever waits.

**How they count toward a test's pass.** Each is one item (right, almost half, wrong), as any exercise of a module type:
- a speaking item counts when a recognizer heard it (the best of three takes); one the learner rated themselves is left
  out of the count, so the pass is the share of the other items (`counted()`, `SheetAnswer.rated`; the self-rating says
  "V testu to šteje le, ko te sliši mikrofon: zdaj je to vaja");
- a writing item counts by its check at once: every check met right, one not almost, two or more, none of the musts,
  nothing or not half the length wrong;
- the tutor's feedback never changes a verdict.

Why: the pass is decided when the test ends, on the phone, offline too, and only the rules can say it then (the tutor may
answer tomorrow); a missing microphone must neither block nor fail anyone, and a self-rating isn't a measurement; what the
unit teaches (the conditional used, the formal close) is what the rules check, the rest (an ending in a free text, the
register's finer points) is the tutor's to say and reaches the mistakes database from the session's end; one item of each
among a test's 14–17 is a few percent, so a misheard take doesn't fail a test at 80 %.

**Free writing offline.** The verdict is the rules', at once. The text goes to the tutor as an `answer` with `course`
(conversation `exw…`, `ChatController.requestFeedback`); offline it waits in the outbox, and the card says "📶 Učitelj bo
prebral, ko bo dosegljiv; odgovor pride v klepet". The reply shows under the text while it is on screen and lands in the
chat whenever it comes ("✍️ Tvoje pisanje"). The guide's `answer` says: feedback only, never that the verdict changed.

**A child's version (Teo, 7).** `child` on an item: the keys a child plays instead, short (a speaking `say` at most 12
words, a writing `model` at most 24 and `length.max` at most 25) and spoken first: a child says a writing item aloud, as a
speaking item of its model and checks (that counts), then "✍️ Napiši še · Write it too" is practice. Every Italian item has
one (the smoke checks it); a child's bridge prints the child's version.

**Prints.** A unit's sheet prints "Povej in napiši · Speak and write": the worksheets' speaking prompts (two at most, with
»what the teacher asks«), the writing task with a box to tick for each must and its length, the ruled lines after it to
write on, and the models with the answers upside down (the writing's left out first to fit). The test's stay the app's.

**Older apps.** The app asks `GET /course?types=2` (`COURSE_TYPES`). An app that doesn't say (0.2.698 on Teo's tablet,
every app before this one) gets the units without them (`heldBack`): the 4th sheet left out, the test without its last
two, the ids as they were, so its progress reads the same. Without that it would show a skip card (half a point, a type it
doesn't know) for each. **Deploy order:** an older bridge refuses the types, so the bridge (with this branch's code) is
restarted before the content commit reaches main, or the running bridge serves the 8 units as planned.

## How

| Part | Where |
|---|---|
| The format, the rules, the validator, what an older app gets | `companion/bridge/src/production.ts`, `src/course.ts` (`productionExercise`, `heldBack`), `src/features/course.ts` (`?types=`) |
| The rules' shared cases | `companion/bridge/test/fixtures/production-rules.json` (the smoke's `production`, the app's `ProductionTest`) |
| The rules and the grading in the app | `data/Production.kt` (`matches`, `check`, `verdict`, `grade`, `heard`, `asWritten`), `data/Models.kt` (`Exercise.Speaking`, `Exercise.Writing`, `forChild`) |
| The screens | `ui/school/ProductionExercises.kt` (`SpeakingEx`, `WritingEx`, `ChildWritingEx`, `AskedLine`, `ChecksList`); `ui/SpeakExercise.kt` takes a `SpeechGrader`, a `steer`, the teacher's line and a note by the self-rating |
| What counts, what the tutor gets | `app/SchoolController.kt` (`counted`, `rated` and `checks` in the session_end's results), `app/ChatController.kt` (`requestFeedback`, `onGraded` into the chat) |
| The guide | `features/chat.ts` (`answer` with `course`), `features/course.ts` (the school's paragraph, `publish_worksheet`); `companion/tutor/guide` regenerated |
| Prints | `companion/prints/sheet.ts` (`chooseProduce`), `html.ts` (`produce`, the answers), `sheet.css`; `school-prints --child` |
| Voice | `course-speech.ts` `courseTexts` (what the teacher asks and the models, in her voice); the day's audio (`DayAudioGather.schoolSpeaking`) |
| QA | `companion/bin/qa` step `school-production`, `sheet_exercise` plays both (the test of `school-unit`) |

## How to use

- Write: companion/COURSE.md, "Speaking and writing" (the format, the rules, "Writing them" for the content wave);
  .claude/skills/lani-studio/reference/module-spec.md for the tutor's homework.
- See it: `companion/bin/qa-slot --steps school-production` (pogojnik-polno's sheet; `LANI_QA_UNIT` another Slovene unit).
- Print it: `companion/bin/school-prints --language sl --level B1 --units pogojnik-polno --explain` (🗣️ and ✍️ lines),
  `--child` for a child's sheet.

## Tap counts

| Journey | Before | Now |
|---|---|---|
| Say a sentence in a unit | not possible | 🎤 1 tap, speak (the phone stops by itself), "Naprej" 1 tap |
| Write a short text in a unit | not possible (a module's `free` waited for the tutor) | the field 1 tap, type, "Preveri" 1 tap, "Naprej" 1 tap |
| No microphone | — | "Brez mikrofona" 1, "Pokaži" 1, "Rekel/Rekla sem pravilno" 1, "Naprej" 1 |
| A child's writing | — | 🎤 1, speak, "Naprej" 1 (writing it too: 3 more, optional) |

## Voice

No clip was made (no ElevenLabs call). What a voice build adds, counted from the units (`ask` and `say` of each
speaking item, `ask` of each writing item, a child's version's too, both genders where a line has a pair): **63 lines,
2,616 characters**, in each course's teacher's voice (Učiteljica Mojca for sl, Maestra Elena for it, Eva for de, Mrs Hartley
for en): sl 698 (pogojnik-polno 509, pisni-jezik 189), it 971 (periodo-ipotetico 488, raccontare
483), de 463, en 484. `voice-build` plans them with the course (`courseTexts`); until then the app says them with the
node's or the phone's voice. The writing models and the learner's own text aren't voiced ahead (🔊 on the model says it
on demand).

## Verified

Tip 915819ec (main 68ecfe63 merged in): tsc, the smoke, `tutor-export --check`, the Android suite, Python and the prints.
The emulator ran an APK built from 188df49d, before that last merge: the merge changed none of the screens' files
(`ui/school/`, `PlayerScreen.kt`, `SpeakExercise.kt`, `data/Production.kt`) but the APK doesn't have main's hint chip and
packs work.

- **tsc** clean; **`tutor-export --check`**: 140 files match (54 tools, 32 guide topics, 51 villages' copies).
- **The smoke** (ports 10330–10349, `LANI_SMOKE_DRAMA3_PORT` 10348): all checks passed, 3,747 ✅. Its `production`
  section: the rules' fixture (21 cases, 12 bad rules refused), the refusals (a model missing its must or hitting its
  avoid, too short, a spoken form as the model or under an avoid, a child's version too long), `publish_worksheet` with
  both, what an older app gets (`GET /course` without `?types=2`: no speaking or writing, a sheet of only them left out,
  the rest as it was), `?types=2` rendered for the learner, the print in both genders and a child's, the voice corpus,
  and all 40 items of the course (no avoid on a form people say, Italian's each with a child's version, a length on each
  writing).
- **The Android suite** (through lani-slot.sh): 4,136 tests, 0 failures, 0 errors. `ProductionTest`, `SheetCountTest`
  and each language's `CourseContentTest`, which grades every speaking and writing item as the app does.
- **Python:** `tests` 253 OK, `companion/tests` 179 OK (11 skipped).
- **Prints:** `school-prints` of the 8 units in each of their three bases, and the Italian two as a child's too: 30 runs,
  every sheet fits its two pages. The "Povej in napiši" section looked at by eye in sl B1 (en), it B2 (de, child), de B2
  (en).
- **Emulator** (`qa-slot --no-build --steps school-production,school-unit`, both ok; screenshots in the scratchpad's
  `ux-lesson-production/final/`): pogojnik-polno's 4th sheet: both speaking items through the self-rating; the writing
  typed with its letters, its checks ticked, right by its rules, the model reached by scrolling above the verdict's
  panel; the test's speaking item saying it doesn't count. The unit end to end from its file: the test's 14 items, 11/13
  (✅ 9 🟡 4 ❌ 0), so the rated speaking item is left out of the count. The dev bridge's log has both writing texts as
  `answer`s (conversations `exw…`) and the sheet's session_end, 3/3.

What the checks found on the way: the app read `würd*` as *wuerde\** (`Grading.foldStem`, 0d1a9ba9); the rules' marks regex
had bare braces (acb22b30); six units' review notes ran over the bridge's 200 characters, so it refused them (fbf51736);
the level tests' three sheets a unit (188df49d). `PhoneBridgeNodeTest` `/course` failed once and passed on the next runs:
probably the phone's copy of the content was taken before the review fix and the node read it after, not confirmed.

Not verified: a real microphone and a recognizer's takes (the emulator has none: the self-rating path only), the node's
Whisper on these sentences, the tutor's reply on a text and how it shows under it (only that the text reaches the bridge),
a child's path on the emulator, Teo's tablet on 0.2.698 itself (the smoke serves what it would get), a native speaker's
reading.

## What's left

- **The content wave:** the other units' speaking and writing (COURSE.md "Writing them"); these 8 are the model. Every
  Italian item needs a `child`.
- **The passato remoto's regular forms** (*andò*, *uscì*) can't be told from the present by a rule: rules fold accents
  (*ando*), so `raccontare`'s must lists the irregular forms and the plurals (32 rules, the most a must takes). A rule
  that keeps an accent it is written with would need the bridge and the app both.
- **The node's recognizer and Slovene sound-spellings:** `Production.asWritten` knows *u vasi*, *f šoli*; more come
  from real takes (tutor-live.md, "Speaking items").
- **The print's room to write:** a full sheet of two pages leaves the writing task one or two ruled lines (formeller-brief
  at B2: one, for a letter of 50–110 words); the learner writes on paper beside it. A third page, or the writing on the
  back, would be the print's choice to make.
- **The verdict's panel covers the checks.** After "Preveri" the panel takes the lower half of the screen and the checks
  list runs under it: with one check not met ("Skoraj") the row that says which can be out of sight, the panel shows the
  `explain`, not the check, and the learner has to scroll above the panel to see it, the model and the tutor's line. Seen
  with the length row (a run whose text came out short). A fix is the app's: name what isn't met in the panel, or bring
  the checks into view. Not done here.
- **QA for a child:** `friuli-school-unit` doesn't play speaking or writing yet (it raises on a type it doesn't know), so
  a child's path (a writing item said first, "Napiši še") is covered by `ProductionTest` and the smoke, not on the
  emulator or Teo's tablet layout.
- **Mojca's cheer** after a right writing item may be "Pravilno! Končnica je prava" (one of her random cheers), which
  fits a gap better than a text.
- **Native check** (above) and the **voice build** (2,616 characters, Jan's credits).
- **Deploy order:** this branch's bridge code before its content commit reaches main (an older bridge serves the 8
  units as planned).
