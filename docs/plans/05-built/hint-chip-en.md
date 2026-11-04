# Built: the hint chip's rules for English (hint-chip-en, 2026-11-03)

Wave E, batch 2b, section 4 (the lead kit's followups-16.md): "📖 Namig · Hint" at an English turn said nothing but its
pages' links (`TurnHints.of`: "a turn of another language than Slovene and Italian gets its pages alone"). Now every page
of the English book has its rule in words, a spoken sentence and models, and the chip names what in the sentence decides
the form where the sentence shows it. With it, the two things hint-chip-it left: on a tie `Forms.turn` takes the word a
rule names, and a name of the scene's cast at a sentence's start is who does it. Built beside hint-chip-it (merged into
this branch, 853893656, so its `Trigger.Asks`, `TurnHints.finish` and `HintSpeech.spoken` are the shared code). Everything
here is machine-written (Claude) and waits for the native check below. The chip reads, it never grades: nothing in grading
changes.

## Native check (first)

Machine-written (Claude, 2026-11-03): the strings `hint.en.*` and `hintSay.en.*` in sl, en, de, it (`_note_hint_chip_en`
in each table: 67 rules, their 67 spoken sentences, 46 trigger lines, 4 spoken openers); the 67 pages' English models and
questions in `game/EnglishHints.kt`. 58 of the rules are the pages' own cards (companion/grammar/en, `card.rule`, in each
base, the markdown taken out), so they say what the book's card says; written for the chip: the four pages without a
card (future, past-simple, present-perfect, some-any), and five changed from their card: present-simple says *does,
watches* where the card says *goes, watches* (the answer of Lakeland's "It goes in the barn"); prepositions-place's
examples are *in the box, on the shelf, at the station* (the card's *table, fell, tent, inn* are Lakeland's answers);
to-be adds "be needs no do, does or don't", questions-do "(not alone: I not live)" (both name the wrong form beside the
right one, so the card shows at the turn); have-it-done leaves out the card's "Yourself: I cut my hair.", whose Slovene
and Italian are the learner's gender's. The four new ones also keep to other words than the village's answers (*played,
cleaned; ate, swam, flew*; *We'll help you!*; *you've seen*).

**For English (a native speaker of British English):**
1. **The models** (EnglishHints.kt, `RULES`), the doubtful ones: *He could read at four.*, *That must be the postman.*,
   *By June I'll have been working here a year.*, *If I were taller, I'd have reached it.*, *She had her bag stolen.*
2. **The questions**: "Who?", "Where?", "Whose?", "How many?", "How long?", "When? What time?", "Here or there?" (this and
   that), "How much? How many?". Natural as a teacher's?
3. **en.json** (an English base, for a learner of English from English: these strings show only if one learns English from
   English, which no pair does; written for completeness): the trigger lines' wording, e.g. "with «cold»: English says it
   with be", "after «I», with no have: one word, the past", "«is» beside it, no noun: the form that stands alone".
4. Lakeland's turn "Wow! I have never seen a raven." has *haven't* as its wrong choice, but the present perfect's page says
   "people also say *I haven't never seen*": the chip says "with «never»: one no is enough in writing". Should the turn
   take *haven't never* as spoken (a second `ok`), as the rule "a spoken form is never wrong" asks? Content, not here.

**For Slovene (Jan's wife):**
5. The trigger lines say "ob «…»: …" (with), "za «…»: …" (after), "pred «…»: …" (before), as hint-chip-it's Italian ones;
   "kdo: «…»" is the Slovene book's own. "ob «cold»: angleščina to pove z be", "za «I», brez have: ena beseda,
   preteklost", "ob «…» brez samostalnika: oblika, ki stoji sama": natural?
6. The four rules written new (future, past-simple, present-perfect, some-any): "Ena beseda, brez pomožnika", "V pisavi
   eno zanikanje".

**For German (a learner of English from German):**
7. "bei «cold»: Englisch sagt das mit be", "nach «I», ohne have: ein Wort, die Vergangenheit", "bei «drinking»: vor der
   -ing-Form steht eine Form von be": plain enough? "die -ing-Form" or "die Form auf -ing"?

**For Italian (a learner of English from Italian):**
8. "con «cold»: l'inglese lo dice con be", "dopo «I», senza have: una parola, il passato", "quando: «every day», più e più
   volte": natural?

## How to use it

At an English turn (a scene, a project's step, a story, a party, a visit to Lakeland in an English pair), "📖 Namig ·
Hint" opens a card with:
- **what decides the form here** (the first line, when the sentence shows it): "who: «The lid»" at *The lid ____ the rain
  out.* (keeps, keep); "who: «the matches»" at *Where ____ the matches?*; "after «Can»: the base form" at *Can I ____ the
  key?*; "after «I've»: the past participle"; "after «is»: the -ing form, going on"; "with «going»: a form of be comes
  before the -ing form" at *____ going to the left.* (I'm, I); "with «cold»: English says it with be" at *____ you cold?*
  (are, have); "with «bring»: a verb of its own, so no be" at *Why ____ you bring rushes?* (do, are); "with «got»: have
  got asks and says no by itself"; "with «a lot of lakes»: one or more decides the verb" at *There ____ a lot of lakes
  here!*; "with «big»: its first sound decides" at *I've got ____ big basket.*; "after «two hundred»: more than one";
  "after «every»: one at a time, the singular"; "before «sheep»: whose it is" at *your ____ sheep* (uncle's, uncle);
  "after the no «haven't»" at *we haven't got ____ cement*; "with «people»: can you count it? That decides"; "with
  «never»: one no is enough in writing"; "after «I», with no have: one word, the past" (saw, seen); "with «the attic»:
  the kind of place decides"; "after «go»: going there, not being there"; "with «Monday»: when it began" (since, from);
- **the rule in words**, after the page's question where it asks one ("Who? Habits and facts: I walk, you walk, we walk,
  they walk, but he / she / it walks. …");
- **two models** in other words than the turn's ("Like: I can swim. · She can swim. · Can you whistle? — Yes, I can."),
  one with the trigger's own word first (after «can»: *I can swim.*);
- the page table's rows about the trigger, and "📖 V knjigo · To the book" (as before).

The 🔊 says it in short: the opener ("After «can»:", "With «cold»:"), the rule's sentence ("After «can» the verb stays as
it is, the same for everyone.", the English by the app's English voice), the question, a model.

**Tap counts.** The rule at an English turn: before, the chip showed only "📖 V knjigo" (the chip, the link, read the page,
Back: 2 taps and a Back); now the card says it (1 tap). The 🔊: before none at an English turn; now 1 tap more.

## What was built

1. **The English table** (`game/EnglishHints.kt`, `EnglishHints.RULES`): the 67 pages of companion/grammar/en, A1 to B2,
   each with its rule in words (`hint.en.rule*`), the 🔊's sentence (`hintSay.en.rule*`), models in English with other
   words, a question where the page asks one, and what to look for in the sentence, in order (`Look`, 42 kinds).
2. **What decides the form** (`EnglishHints.triggerOf`), from word lists, no dictionary, at the turn and offline, and only
   where the sentence shows it plainly: who does it (a subject pronoun, someone, a name, a noun phrase with its determiner,
   a bare noun at its clause's start or after *because*; after the verb in a question that starts with it or with a
   question word; *who* or *what* itself; never at *which one*), only where the choices differ by who (*is, are; does, do;
   keeps, keep; was, were; has, have*); the helper before a verb's base form (*can, could, will, 'll, shall, did, does, do,
   don't, to, let's*), before the subject in a question; *have* (or *'ve*, *'s*, *'d*) before a participle; *be* before an
   -ing form; the -ing form after a gap of be against no verb or do; a feeling, an age, a distance (a list: *cold, hungry,
   scared, old, far …*) after be against have, do or not; what a short form with be says after it, against its pronoun
   alone (*It's very heavy*); the verb of its own after do against be or not; *got* after have against do; what there is
   after *there* or *here* (with its amount: *a lot of, lots of, so many, any*), or *how many* before; the word after *a* or
   *an*; a number, *these, many, lots of, how many* before a noun (*o'clock* never), *every* and *each*, *a* and *this* (a
   noun and its plural only); the noun after *this, these*; the noun after whose it is (*your, uncle's, whose, its*), none
   after *yours*; the verb or preposition before an object pronoun, the verb after a subject one; a no before *any*;
   the noun after *much* or *many*; a second no (*never*) at a helper and its *n't*; no *have* before a past against its
   participle (*saw, seen*); *been* after have against be; a verb of going before *to, into*; *next to*, *in front of*; the
   place after *in, on, at*, the time after them; what follows *for, since*; a word of time (*yesterday, last week, ago,
   in 1720*; *tomorrow, next week, soon*; *now, at the moment, Look!*; *every day, always, usually, on Mondays*); *if* in
   either half; *than*, *the* before a superlative; the verb or preposition before an -ing form (*enjoy, good at, look
   forward to*); a verb of saying; the noun before *who, which, where*; a question tag's sentence's verb; *while*; *by*
   after a participle; the subject of *myself*; the verb before an adverb, the noun after an adjective.
3. **The chip** (`TurnHints.of`): an English turn takes English's table; the models' choice and the rows are the shared
   `finish`. A Slovene turn is unchanged but for the two fixes below.
4. **The tie** (`Forms.turn(line, right)`): the word most wrong choices differ in alone is the gap, as before; on a tie, the
   one more of whose wrong choices have a rule (`DialogChoice.rule`: its page, the turn's, the guess), else the first one's.
   Every caller with a turn passes its choices' rules (the gap, the hint, `Adaptive.modes`, `Traps.trap`,
   `Introduction.turn`, `Treasure`), so the gap typed, the hint, the turn's mode and its traps are about the same word.
5. **The cast** (`TurnHints.of(line, language, cast, page)`; SceneDialog's `rememberHint` passes the dialog's person, a
   step's cast, the scene's people and the village's villagers): `TurnHints.names` takes the capitalised words of their
   names; Italian's and English's `name` take one of them wherever it stands (*Giulia è stanca.*: who: «Giulia»;
   *Giulia, vieni!*: talking to «Giulia»), Slovene's `cues` takes one as who does it, not after a preposition, and two
   joined by *in* together («Micka in Luka»).
6. **Tests**: `EnglishHintTest` (14): every page's rule, sentence and models in every table; the triggers on the content's
   own turns; models with no form of the turn; a German base; and over every curated English turn (Lakeland's scenes with
   their counting dialogs at a number of each category, steps, stories, parties, arrivals), in sl, de and it: no line,
   row, title, model or spoken sentence gives the answer away. `TurnHintTest` gains the name and the tie, and a report of
   what both change for Slovene's curated turns. QA's step `hint-en`.

## Choices, and why

- **The cards' rules.** The book's pages have a card each (the short rule the book shows first), written in all four bases
  already; the hint says the same, so the chip and the book don't tell a rule two ways, and the translations are the
  book's. Where a card would show the answer of a turn the village asks (present-simple's *goes*), the hint's copy says
  another word.
- **Word lists, not a dictionary**, as for Italian: the chip works at the turn and offline; a list finds a trigger only
  where the sentence shows it plainly, and says nothing where it can't tell: *Which one is the best?* (which one, or the
  best?), *Yes, I ____ it!* (no time word), *at* against *on* the edge (both are English), *above* against *under* (a
  meaning).
- **Who before what.** On the pages of the verb's person the subject is looked for first, but only when the choices differ
  by person; a gap of be against do or no verb is about what follows (an -ing form, a feeling, a verb of its own), so
  *Are they drinking?* against *Do they drinking?* names «drinking», not «they».
- **The tie on the turn, not on the texts.** `Forms.turn(texts, right)` stays as it was for a caller with texts alone (the
  road quiz asks only whether a turn is a form); every caller with a turn uses the turn's rules, so no two parts of the app
  disagree about the gap.
- **No bridge, no content.** The rules are in the app, as Slovene's and Italian's; nothing the bridge serves changes, so an
  older app (0.2.698) keeps showing the links alone, and the new one shows the rules at once on its update.

## What changes for Slovene's turns

Measured by `TurnHintTest` (`what the tie and the cast change for Slovene's curated turns`, its report
build/reports/hints/sl-changes.txt), on Slovene's 1,596 curated turns: the scenes' 885 and Primorska's steps, stories,
parties and arrivals:
- **The tie moves the gap of 20 turns**, 6 in scenes and 14 in steps and stories, each to the word a rule names, from a word
  of meaning: *Super! In ____ kruha za vse.* tested *več* (manj, a meaning) and now tests *kruha* (več kruh, rodilnik-
  kolicina); *Še veliko ____!* (rok); *Tvoj je ____ konj v vasi!* (najhitrejši); *Ja, zelo ____ lačen!* (sem); *Ja, poznam ga.
  Janez ____ je povedal.* (mi against me, dajalnik; vem against poznam is the meaning); *Ne, hvala, danes ne pijem ____.*
  (vina; Ja against Ne is the meaning); *____ stopnic!* → *Sto ____!*; *Osem ____!* (stebrov); all 20 in the report.
- **On those turns**, the hint now says the rule (in the scenes: turns with a hint 1,066 → 1,072, saying a rule 1,053 →
  1,059, case turns with their trigger 459 of 479 → 463 of 483); the gap typed or said is the rule's word; and the turn may
  be typed once that rule is secure (before, its form's wrong choice of meaning had no rule, so it was always chosen). A
  learner's trap and the gate of rules not yet read the same word. When the turn is chosen, the choice of meaning stays
  among the choices, as before.
- **The cast changes nothing today**: no Slovene curated turn of a person's page (glagoli-sedanjik, biti, imeti-iti, dvojina)
  has a name of the scene or the village for its subject. A tutor's or a later scene's turn would ("Micka kuha.": who:
  «Micka»; «Micka in Luka» for the dual: `TurnHintTest`).
- Nothing else of Slovene's changes: the same rules, models, rows and 🔊 (TurnHintTest's and HintSpeechTest's other
  checks unchanged). The count reads a step's or a story's own and its turn's `grammar`; a guess from a why, which scenes
  have, isn't read there, so a step's count could differ by a turn or two.

For Italian (`ItalianHintTest`, on this branch's content): the tie gives the Friuli turns of hint-chip-it's "What's left"
(119 counted there) their page's gap: 89 more turns have a hint, 1,034 → 1,123; with a rule or a trigger 997 → 1,079;
form turns naming what decides 596 of 879 → 662 of 968. **The cast adds 3 turns** (build/reports/hints/it-cast.txt): *Giulia
è stanca.*, *Tommaso è più grande di te.*, *Tommaso, attento!* For English **it adds none**: a word at its clause's start
right before the verb is taken for the subject with or without the cast (*Wilf is the winner!*); the cast only joins a
title to a name there.

## Accuracy

Over the curated English turns, `EnglishHintTest` (its report: build/reports/hints/en.txt, each form turn's first line):

| | Turns |
|---|---|
| English turns (choices ≥ 2) | 1,634 |
| with a hint (a page of the book) | 1,010 |
| with a rule or a trigger on the card | 945 (94 %) |
| form turns (one word differs) | 845 |
| naming what decides | 745 (88 %) |
| the 🔊 says something (the longest about 15 s) | 1,010 |

The same in sl, de and it. By line: who 192, the base form 143, more than one 125, be before the -ing form 46, what there
is 42, just one 36, whose it is 23, the -ing form after be 21, said with be 17, needs be 17, the first sound 14, the
participle 11, some in a yes sentence 11, much or many 9, the place 9, a verb of its own 9, the rest 1 to 3 each.

The 618 distinct form turns' lines were read one by one, run through the real `EnglishHints.kt` (compiled alone with
stubs while the machine's Gradle slots were taken). Wrong or missing, and fixed: *Never walk on the ice.* took «Never»
for a habit; *the other side*, *the family's luck*, *The Vikings* lost their first word; *And where does the road go?*
wasn't a question; *Are you scared?* (do's page) and *some* in a yes sentence named nothing. Five rules said the
village's own answers and so were left out at those turns: reworded (see the native check's first paragraph).

**The 65 hints with the link alone** are turns of two right choices or of another length (*I'm fine, thank you.* and *Very
well, thank you.*; *I can't see the river.* against *I don't can see*): the whole of their sentence is the answer, so a
card that names *I'm*, *can't* or *don't* is left out. Hence the test's 90 %, not Italian's 95 %.

## What's left

- **German** (hint-chip-de) comes from case-de's tip, as the brief says; it can take `TurnHints.of`'s cast and the tie as
  they are here.
- **An irregular past with no time word** (*Yes, I ____ it!*: saw, seed) and **a plural of meaning** (*Yes, I love ____!*:
  jokes, joke) name nothing; the rule alone.
- **Lakeland's "I have never seen"** (native check 4).
- **make-learner.ts** stopped for English and German after practice-it-b2 (it read companion/modules/<language>, which they
  lack): fixed here (adf2f93b5), needed for this QA; hint-chip-de needs it too.

## Tests

On this branch with main merged (17b7b070e, tests 74c147f42), ports 10710–10729:
- Bridge (only make-learner.ts changed): `bunx tsc --noEmit -p .` clean; `bun build --no-bundle` of make-learner.ts; the smoke
  3,824 checks, all passed (LANI_SMOKE_PORT 10710, LANI_SMOKE_DRAMA3_PORT 10728); `tutor-export --check` 140 files match.
- Python: `python3 -m unittest discover -s tests` 253 OK, `-s companion/tests` 179 OK (11 skipped).
- App: the full unit suite through lani-slot.sh, 4,141 tests in 523 classes, 0 failures, 2 skipped. Among them:
  `EnglishHintTest` 14, `ItalianHintTest` 14, `TurnHintTest` 18, `HintSpeechTest` 11 (unchanged), `FormsTest` 7 (unchanged),
  `L10nTablesTest` 6 (3,481 keys in each table, none unused).
- Emulator QA, `companion/bin/qa-slot --apk <the merged tree's debug APK> --no-town`, slot 1, no crash:
  `--steps hint-en` with a synthetic learner (make-learner.ts --target en --base sl, LANI_CULTURE=lakeland,
  LANI_QA_SETUP_PAIR=en-sl, LANI_QA_PAIR=en-sl): ok; at each of the three turns of Jack's sheep by the beck the card opens
  with its line in Slovene: "ob «drinking»: pred obliko na -ing pride oblika glagola be", "ob «thirsty»: angleščina to pove
  z be" and the to-be rule after "Who?", "ob «coming»: …". `--steps hint-it` (make-learner.ts --target it --base de,
  LANI_CULTURE=friuli, LANI_QA_SETUP_PAIR=it-de), run again since `Forms.turn` and Italian's names changed: ok, its three
  lines as before. Shots: the session's scratchpad, ux-hint-chip-en/hint-en-1.png to -3.png (the runs' all in
  companion/qa-shots/20261104-142628 and 20261104-143232). No QA ran on a turn whose gap the tie moved: that is
  `TurnHintTest` and the report.

No line anyone hears was added or changed in the voice store (the new strings are the card's and the 🔊's, said by the
phone's voice, the English by the app's live voice): a voice build needs 0 characters.
