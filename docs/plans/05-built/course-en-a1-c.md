# Built: the English course, A1 part c (2026-11-01)

Wave B, batch 2 (followups-7.md, sections 1–6): a learner of English in the Lake District village had an empty school.
Part a wrote units 1–6 and the groundwork; part b writes units 7–12 alongside; part c writes A1's last six units, from
where things are to the past of be, their three new grammar pages, their words on the prints, and what the English
grading needed for them. Everything written in a language is machine-written (Claude) and waits for a native check: the
list is at the end.

## What was built

### The six units (companion/course/en/units)

A1 size, as the Italian A1 units and part a's: five or six intro steps with a quick question each, three worksheets, a
test (pass 80). Set in the village's grown-up life: the Packhorse Inn full at night, Mr Dixon's gates and horseshoes,
Mr Tyson's sacks and oats, Old Wilf and Bess by the fire, Arthur's bees, the bus to Keswick, market day, the village's
feast days.

| # | Unit | Teaches | Pages | Sheets' items | Test items |
|---|---|---|---|---:|---:|
| 13 | `prepositions` 📍 | in, on, at (at home, at work, in Keswick); under, behind, in front of; next to, by, between, near; the noun unchanged; Where's …? Where are …?; to, but go home; into | prepositions-place (own summary card), there-is-are | 30 | 11 |
| 14 | `can` 💪 | can for everyone, no -s, no to; can't, cannot; Can you …? Yes, I can; Can I …? You can … (asking, allowing); Could you …, please? | can (own summary card) | 26 | 11 |
| 15 | `present-continuous` 🏃 | am, is, are + -ing for now; isn't, aren't; Are you …? What are you doing?; making, sitting, lying; every day with the present simple, now with -ing; know, like, want without -ing | present-continuous, present-simple | 30 | 10 |
| 16 | `time` 🕖 | o'clock, past, to, a quarter, half past and British half seven (7:30: the halb sieben trap); at a time, on a day or date, in a month, season, year, part of the day, at night; at the weekend; the ordinals and the date (the first of May, 1 May) | time-dates (new), numbers | 29 | 11 |
| 17 | `object-pronouns` 👉 | me, you, him, her, it, us, them after a verb and a preposition; subject before, object after; her both, him never his; It's me. Me too.; one pronoun only; Give it to me | object-pronouns (new), pronouns | 28 | 11 |
| 18 | `was-were` ⏮️ | was, were (you were for one); wasn't, weren't; Was it …? Yes, I was; yesterday, last night, two days ago; there was, there were; where and were; born with was | was-were (new), to-be | 26 | 12 |

Each unit's explanations, summaries, goals, titles, instructions, hints, whys, labels, columns and translations are in
Slovene, German and Italian (du, ti, tu, as the app speaks to the learner), and in English, the format's fallback; every
English form in bold with its meaning in the base; British spelling and usage with the American form accepted (Do you
have …? was part a's; here: on the weekend, ten after eight, May first, Give me it). The Slovene and Italian
translations agree with the learner's gender through `{m:…|f:…}` (Bil{m:|f:a} sem v gostilni). The outline's six lines
name their pages and lost `"planned": true`.

Units 16–18 and their pages were written by part c's one helper agent from part c's brief, and checked and committed
by part c (the commits say so).

### The three new grammar pages (companion/grammar/en)

Each A1, in the format of part a's `pronouns` and `numbers`, with its cheat-sheet card (`card`: title, rule, two
examples, a tip):

| Page | What | Card |
|---|---|---|
| `time-dates` 🕖 | the clock (past, to, a quarter, half past, half seven, digital), at, on and in for time, every with none, the capitals, the ordinals, writing and saying a date | "Time and dates: at, on, in"; 💡 at night, but in the morning |
| `object-pronouns` 👉 | the seven object forms after a verb and a preposition, the order, her and his, it and them, a pet as him or her, It's me, give | "Me, him, her, us, them"; 💡 her is both |
| `was-were` ⏮️ | was and were, the negative, the question and short answers, the time words, there was and there were, where and were, born | "Was, were: the past of be"; 💡 Where were you? |

The book had pages for the place prepositions, can and the present continuous, which units 13–15 name. past-simple is
A2 and sees was-were; was-were keeps its own tags (past-simple has `was_were`, there-is-are `there_was`).

### The grading (grading/en.json, shared with parts a and b)

Four commits, each relayed by the lead to parts a and b, in this order:

| Commit | What |
|---|---|
| `a1eda926` | known mistakes, each only for a word of its rule: the -ing spelling (siting, runing, swiming, hoping; makeing, comeing, writeing; writting, comming), the ordinals (nineth, eigth, twentyth, thirtyth, fourtieth), were and where for each other |
| `ef4ec9da` | a contraction typed without its apostrophe is almost with the apostrophe hint, never right (cant, isnt, dont, wont, Im, youre, theyre, ive, theyve, hes got, whats, theres …); words of their own stay out (its, were, well, ill, id, lets, hell, shell, wed, shed), so were for we're stays wrong |
| `8f38a297` | the apostrophe hint's examples follow the language learned, from its grading table (`loose_examples`): Italian's "l'acqua, un'amica" as Teo had them, English's "can't, I'm"; `Spelling.looseExamples`; the l10n strings the plain sentence |
| `537452c0` | an ordinal from 21 to 99 with or without its hyphen (twenty first = twenty-first); twelth, fith, fivth wrong |

`GradingLanguagesTest` has each (37 tests); `ApostropheHintTest` (new, 2) the hint for Italian and English learners.

### Words on the prints (lexicon/en.extra.json)

94 entries appended (44 → 138): 34 for units 13–15 (where, home, work, bus stop, can, could, of course, at all, the
verbs of can and of the -ing unit, piano, bill, walker) and 60 for units 16–18 (the clock and calendar words, the
ordinals, the object pronouns, the verbs and phrases around them, was with were, wasn't, weren't, last night, ago, there
was, born, aye …). Every sheet of the six units lists every word it names with a meaning in sl, de and it. Each meaning
was checked against the English and Lakeland packs' prompts and the supplement in the same base (a script in the
scratchpad): where a base has one word for two, the meaning says which ("peti, peta, peto (5.)" beside peti, to sing;
"za koga, za kaj" beside za, behind; "quarto, quarta (4°)" beside the moon's quarto; "piacere a qualcuno" beside
Piacere!; home's "casa, la propria casa" beside a house's la casa). second, bar, wave and love stayed off the units'
words, the packs meaning another sense.

### A pack fixed (companion/packs/en/numbers-and-time.json)

half past's German was "halb" and its Slovene "in pol": a learner from German reads halb sieben (6:30) for half past
seven. Now "halb (3:30 = halb vier)" and "pol (3.30 = pol štirih)", without the English the card asks for (`d9c35e61`).
The pack's other time words were right in each base.

### Shared pieces: cherry-picked, and the QA step fixed

| Commit | What |
|---|---|
| `b734776c` | part a's 137c4af2: EnglishCourseContentTest, SchoolBoardTest's English board, the smoke's Lakeland course-speech and English-sheet checks |
| `59c25287` | the lead's 79099d49 (course-speech's BASE_WORDS); the Lakeland block keeps its check of the base's letters and takes BASE_WORDS for the base's words |
| `ddb65f29` | the QA step `course-unit` (shared with German): a quick question's field found above the Check button, wherever the step scrolled to |

## How the units are written

As COURSE.md "Writing a unit", "The English course" and part a's notes, plus part c's (COURSE.md "The English course:
units 13–18"):
- **A gap a preposition decides only where the sentence fixes it**: the word after it (`in ___ of`, `next ___ the
  inn`), between … and …, at home, at work, a town, or a cue in brackets (`___ (below) the table`); in and at both listed
  where both fit.
- **A name's 's before -ing** is listed in full beside it (`Meg is running` with `Meg's running`): the grading reads 's
  as is only before an article, not, a place word.
- **A wrong form in a step's English as "**right** (never *wrong*)"**, as part a found: the sheet's "Watch out" then
  pairs each with its own right form (she cans · she can, He can to drive. · He can drive., Do you can swim? · Can you
  swim?, I'm knowing. · I know., I know him Jack. · Jack? I know him., you was · you were, Yes, I were. · Yes, I was.,
  Auntie Edna is born in 1952. · Auntie Edna was born in 1952.).
- **Each step's point on the rule line**: the half-seven warning, at night, there was/were, born and the dates were
  rewritten until the sheet's line kept them; no `?` inside a bracketed translation (the sentence splitter cut there).
- **The dates are the village's**: May Day (1 May), St George's Day (23 April), Rushbearing (5 August), the show (31
  August), Bonfire Night (5 November). Old Wilf (84) was born in 1942 and Auntie Edna (74) in 1952, as part a has them.

## Read aloud

Mrs Hartley reads the intros in the cast's teacher voice (part a). A voice build of part c's units would take about
8,800 ElevenLabs characters for her English lines (419 texts: examples, quick questions, answers, the English runs) and,
on demand when an intro opens or with `voice-build --explanations`, about 9,900 (Slovene), 10,600 (German) and 10,500
(Italian) for the explanations. Nothing was voiced; no credits spent.

## Checks

- Bridge: `bunx tsc --noEmit -p .` clean; the smoke with ports 9930– (LANI_SMOKE_DRAMA3_PORT 9948): 3324 checks, all
  passed, again after `1d656e8f` (course/en validates with its 12 written units and their pages; each step's point on the sheet's rule line in
  every base; the Lakeland school's course-speech in Mrs Hartley's voice, never the base; the English sheet; every
  supplement's meanings).
- `tutor-export --check`: 89 files match (54 tools, 32 guide topics). Python: `tests` 251 OK, `companion/tests` 142 OK
  (11 skipped).
- Android, through lani-slot.sh: `EnglishCourseContentTest` (6: every unit played at its level, every right answer
  graded right, no distractor right or almost, every text in sl, de, it and en), `SchoolBoardTest` (6: the English
  board writes every letter of the 12 units' titles and forms), `GradingLanguagesTest` (37), `ApostropheHintTest` (2),
  `ItalianCourseContentTest` (6, after it.json's loose_examples), `IntroSpeechParityTest` (2), `PrintsTest` (6),
  `GrammarTest` (16), `SheetsTest` (15), `SentenceGrammarTest` (5): all pass. The full suite (before `1d656e8f`;
  the focused tests ran again after it): 3704 tests, 0 failures, 0 errors.
- Quick checks while writing (scripts in the scratchpad): the bridge's `validateUnit` in each base and `validateGrammar`
  for every unit and page, the say and summary lengths, each step's rule line; a TypeScript port of `Grading.check`
  with grading/en.json for the likely wrong answers (he for him, they for them, was for were, a wrong preposition,
  nineth, siting) and the American forms; every gap text's distractors against every gap.
- Prints: `school-prints --language en --level A1 --base de|sl|it`: every sheet fits (prepositions, present-continuous,
  time and object-pronouns on two pages, can and was-were on one), the booklet 20 pages (cover, contents, 12 units);
  every word of the six units meant in each base, no gender; the "Watch out" boxes pair each wrong form with its own
  right one.
- Emulator QA (`course-unit`, a synthetic learner of English from German in the Lake District): present-continuous
  for Ana, slot 1: the school with A1's units (8 of 12 done by the hook, "6 more coming" for part b's), "Next: I'm walking now", the unit's page, the intro's
  six steps (four taps and two typed gaps, each answered right, Mrs Hartley's bubble in German with the English forms
  underlined for her voice), the summary, the first worksheet checked, the test passed 10/10 ("Next unit: At seven, on
  Monday"), the sheet printed (the dialog's preview, the PDF); ✅ setup, ✅ course-unit, ✅ crash check. Its first run
  failed at step 2's gap: the shared step's text_field() took a field only with its centre above 1850 px, and a quick question's field
  sits at about 1870 px once a long step has scrolled to its end. Fixed in `ddb65f29` (above the Check button's top,
  else 2150 px), with the lead's go-ahead. That run also showed reading and lying in step 4 not marked for her voice;
  the content fix (`1d656e8f`) came after the run. Screenshots: the scratchpad's ux-course-en-a1-c/ (cu-school,
  cu-school-next, cu-unit, cu-intro-1 to 6, cu-intro-summary, cu-sheet, cu-sheet-1-checked, cu-sheet-2-checked,
  cu-sheet-done, cu-test, cu-test-passed, cu-print-button, cu-print-preview, cu-print-pdf).
- Tap counts: no journey changed. A unit of part c plays as any unit: the school, the unit (1 tap), the intro (1), a
  tap or a typed gap and Check per step, Next per step, then the sheets and the test.

## How to use

- A learner of English (the Lakeland culture) finds A1's units 13–18 open after part a's six (part b's six still
  planned until it lands); Mrs Hartley teaches them.
- A printed sheet: `companion/bin/school-prints --language en --level A1 --base de` (or sl, it; `--out` a folder of
  your own).
- QA: `LANI_DEV_DATA=<make-learner.ts --target en --base de> LANI_CULTURE=lakeland LANI_QA_SETUP_PAIR=en-de
  LANI_QA_COURSE_UNIT=present-continuous companion/bin/qa-slot --steps course-unit`.

## What's left

- At the rebase with parts a and b: the outline's lines (`resolve-outline.py`), COURSE.md's sections in unit order (b's
  units 7–12 between a's and c's), both sides' supplement entries and grading `wrong` entries, the QA step list.
- Part b's units 7–12 are assumed as the outline has them (this-that, possessive, present-simple, questions-do,
  word-order, there-is); a sentence of units 13–18 uses their grammar around a gap (Sam lives in Keswick, Do you …?,
  usually, There's a bench …).
- The explanations' clips (above) and the native check below.

## Native check

Machine-written, not reviewed by a native speaker. British English first, then each base.

**British English, units 13–15** (every unit's English: examples, prompts, options, gap texts, transforms):
- prepositions: "on the fell" (and the sort's "on the fell" as the only answer); "He's at the mill" (at and in both
  right in the test's "He's ___ the inn", only at offered in the choice); "a big map of the fells on the wall";
  "next to the fire" beside "by the fire"; "We go to Keswick on the bus"; "Old Wilf is under the big oak"; "The bench is
  ___ the inn" with in front of only; "It's late. Let's go home."
- can: "Mr Dixon can make a gate in a day"; "Arthur can name every bird of the dale"; "Could I have the bill, please? —
  Of course."; "You can't park here"; "Could you take these cups to the kitchen, please?" from Mrs Birkett to a guest
  who offered help; "Can you make a gate for my field, Mr Dixon?"; "Yes, sure." as an answer; "Help me with this box."
  turned into "Could you help me with this box, please?" (and "Could you please …?" accepted).
- present-continuous: "Jack is walking the dogs"; "We're having breakfast at the inn"; "Take your coat. It isn't
  raining now, but it's cold."; "Old Wilf tells stories every evening"; "Listen! Brewer Sam is singing in the yard";
  "Jack is running up Helvellyn"; "I understand now, thank you!"; "the walkers" for hikers at the inn.

**British English, units 16–18**:
- time: "half seven" as standard spoken British for 7:30 and "quarter past" without a (both accepted); "Dinner's at
  half seven, love."; "The market in Keswick is on Saturday" (or on Saturdays?); "There's a dance at the inn at the
  weekend, on the first of August"; "This year the show is on the thirty-first of August"; "late at night"; the
  American "ten after eight" and "May first" accepted, "twenty of nine" not.
- object-pronouns: "Mrs Birkett brings him a cup of tea"; Meg as her (and "The children love it" for Meg graded
  wrong); "Who's there? — It's me!"; the him/his sort's phrases (I know …, … dog); "Give me it" accepted beside
  "Give it to me", the Northern "Give it me" not; "Thank you, love."
- was-were: "Aye, it was!"; "The bus was very full!"; "Weren't you at the market, Jack?" accepted beside "Were you …?";
  "There were a lot of people"; "Mrs Hartley was born in Carlisle".

**Slovene, units 13–15** (explanations, translations, labels):
- prepositions: "na hribu" for on the fell; "v gostilni" for at the inn, "na postaji" for at the bus stop, "pri vratih";
  "v službi"; "iti v gostilno", "avtobus za Keswick"; "Gospoda Tysona ni doma"; "ob potoku" for by the beck.
- can: "znati, moči in smeti" as one can; "Lahko prosim račun?" for Could I have the bill, please?; "Ja, lahko." for
  Yes, sure.; "ograjna vrata" for a gate; "Lahko odneseš te skodelice v kuhinjo, prosim?" (ti from Mrs Birkett).
- present-continuous: "Slovenščina tu reče kar sedanjik"; "neizgovorjeni e"; "Babica Maggie peče sconese"; "Jack bi
  rad skodelico čaja" for Jack wants a cup of tea.

**Slovene, units 16–18**:
- time: the warning "Britanci rečejo half seven … pol osmih, ne pol sedmih!"; četrt na osem, tri četrt na osem, pet
  čez sedem, "ob osmih in deset"; "Katerega smo danes? — Tretjega junija."; "Dnevi so z veliko začetnico"; "Noč kresov"
  for Bonfire Night.
- object-pronouns: the case lists (mene, me, mi; njega, ga, njemu, mu); "za be in samostojno rečeš me"; "Ona ima rada
  njega, on pa njo"; "Hvala, srček" for "Thank you, love".
- was-were: "Ni bilo mrzlo"; "sejem" for the show; "ago stoji zadaj"; the learner's gender in "Bil{m:|f:a} sem".

**German, units 13–15** (Austrian where it differs):
- prepositions: "in der Arbeit" (Austrian) for at work; "auf dem Berg" for on the fell; "in der Nähe vom Bach"; the
  note that English has no Dativ after a preposition.
- can: "Könnten Sie bitte das Tor aufmachen?"; "Rechnung"; "Hier darfst du nicht parken."; "Für können und dürfen sagt
  Englisch oft nur can."
- present-continuous: "Deutsch sagt hier das Präsens, oft mit gerade"; "Brauer Sam … braut sehr gutes Bier" (ale as
  Bier); "Wanderer" for walkers.

**German, units 16–18**:
- time: "halb acht, nicht halb sieben!"; "Nach halb sagst du to (vor) und die nächste Stunde"; "anders als im Deutschen
  in the morning (am Morgen)"; "Der Wievielte ist heute?"; "Nacht der Freudenfeuer" (the Lakeland pack's name).
- object-pronouns: "Eine Form für Akkusativ und Dativ"; "Danke, Schatz" for "Thank you, love".
- was-were: "Vorsicht: was ist hier nicht das deutsche was"; "where heißt wo, nicht wer"; "born geht mit was … auch wenn
  man ist geboren sagt".

**Italian, units 13–15**:
- prepositions: "a Keswick" for in Keswick (and the warning that a town takes in); "sul monte" for on the fell; "lungo
  il ruscello" for by the beck; "moto a luogo" in the table.
- can: "Posso aiutarla, Mr Tyson?" (Lei to Mr Tyson); "Potrebbe aprire il cancello, per favore?"; "Per sapere e potere
  l'inglese dice can."
- present-continuous: "È come stare + il gerundio"; "Meg sa correre velocissima"; "Di solito Mrs Birkett lavora in
  cucina, ma oggi è seduta in giardino."

**Italian, units 16–18**:
- time: "le sette e mezza, come in italiano"; "Fino alla mezza dici i minuti con past (e)"; "to (meno) con l'ora
  dopo"; "La data vuole sempre l'ordinale: the third of June (il tre giugno)"; "Quanti ne abbiamo oggi?"; on Monday =
  lunedì.
- object-pronouns: "Lui le piace / Lei gli piace" for She likes him / He likes her; "Jack le vuole bene";
  Dammelo/Dammela.
- was-were: "was (ero, era; sono stato)"; "Non faceva freddo"; "Eri alla fiera?"; "Ieri sono stat{m:o|f:a} a Keswick".

**The packs and the supplement**: half past's "halb (3:30 = halb vier)" and "pol (3.30 = pol štirih)"; the 94 new
supplement meanings in sl, de and it (lexicon/en.extra.json), above all the ones that tell two senses apart (peti,
peta, peto; za koga, za kaj; quarto, quarta; piacere a qualcuno; casa, la propria casa).
