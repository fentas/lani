# Built: "🧩 Skloni · Cases", the case trainer (2026-10-29)

Jan asked: "I struggle a bit with the cases. Maybe we can do some test modules around them? One idea: show a sentence
and I pick what case this is? Other ideas?" Branch `case-trainer`. The rules as built are in companion/README.md ("Case
trainer"), the grammar book's button in companion/GAME.md ("The grammar book"), the school's units in
companion/COURSE.md ("What a unit turns on"), the tutor's side in the channel guide (the feature `cases`:
`bridge/src/features/cases.ts`; companion/tutor/guide/cases.md for the in-app tutor). This note says what was built, why
each choice was made, how to use it, the bank's numbers, the tests, the QA and what is left.

## What Jan gets wrong, and what it asks for

Their mistakes database (read by the lead; occurrences): the genitive (9 form mistakes, and after a trigger: iz 3, brez
1, a no 1), the feminine accusative -a → -o (8), kam or kje after v and na (7), v or na (4), agreement (adjective gender
9, the possessive's neuter moje 6), other forms (accusative 4, nominative 4, locative 2, instrumental 1). More of it is
knowing what triggers a case than the endings. So the trainer's centre is the trigger: every "Which case?" is followed
by "Why?", the word that decides it.

## What

- **Three kinds, interleaved in sessions of 10 (a child's 8), one task at a time, feedback at once:**
  - **🧩 Kateri sklon? · Which case?** A real sentence, one word framed; the six cases as the school asks them (big: kdo?
    kaj? / koga? česa? / komu? čemu? / koga? kaj? / pri kom? pri čem? (o kom? o čem?) / s kom? s čim?; small: the number
    and the name, "2. rodilnik · genitive"), each in its colour. Then **🤔 Zakaj? · Why?**: a tap on the word that
    decides it (a preposition, the verb, ni or nimam, a number, a quantity word, the noun a genitive belongs to), or "👤
    Nič ne odloči: je osebek / 🎯 … je predmet · Nothing decides it: it's the subject / the object"; then the rule's
    line, the same form's other readings, the item's note and the page's link.
  - **🧭 Kje ali kam?** "Micka gre ___ (tržnica)." under the village's picture of the market; na tržnico or na tržnici;
    then the verb that says it (gre). Also v or na (na pošto), pri or k (k babici, pri babici) and od kod (s tržnice).
  - **✍️ Sklanjaj! · Decline it.** The learner's own nouns (their cards with forms) in a curated frame with a known
    trigger ("Tukaj ni ___. (miza)", "Grem k ___. (moj brat)", "Vidim dva ___. (brat)"), alone or with an adjective or a
    possessive (agreement), typed and graded as any typed answer.
- **⚡ Sklonski vihar · Case storm** (the optional part, built): 60 seconds of short phrases sorted into the six coloured
  cases, a right one flashing on, a medal and the longest streak at the end.
- **Mastery per case and per rule**, the weak ones drawn more (the learner's mistakes count from the start), a missed
  item back by the trainer's own spaced repetition, every session persisted on the node as grammar practice with its
  mistakes under the learner's own pattern ids.
- **Where it opens:** Home's tile and "Danes · Today" idea, the grammar book's case pages ("🧩 Vadi sklone"), the
  school's case units ("🧩 Skloni" on the unit), the tutor's assignment.
- **The tutor:** `get_cases`, `assign_cases`, `publish_case_items` (checked as the bank is), `remove_case_item`;
  `cases_done` already persisted; `get_village` shows the progress.
- **The bank:** 416 items with gold annotations, 31 rules, 31 frames, 20 adjectives and possessives, 23 nouns
  (`companion/cases/sl/bank.json`, lani.cases/v0), checked against the dictionary by the bridge.

## The contract with the case lens

The case lens (branch `case-lens`, built in parallel) and the trainer share one definition of the cases. The trainer
owns the app's case look; the lens owns the trigger table and the sentence analysis.

- **The cases are the app's own `game.Case`** (game/Triggers.kt, in main before either branch: NOM … INS, the school's
  number `n`, its question, its English name `lexicon`, its name in the string tables `key`: "rodilnik · genitive"). No
  second enum: the case lens keys its colours by it too.
- **`data/CaseLook.kt`** (pure) adds the contract's look to it as extensions: `id` (the case's grammar page:
  imenovalnik, rodilnik, dajalnik, tozilnik, mestnik, orodnik; the bank's, the wire's and the mastery keys'), `slot`
  (the forms book's "gen"), `ask` (the question words lower case, "koga? česa?"; the locative's `alsoAsk` "o kom? o
  čem?", `asks` both), `lightArgb` (#4A7FC1 blue, #C2503C red, #D69A2D amber, #3E9B5F green, #8A5CB8 violet, #2C9AA0
  teal), `darkArgb` (the same hue lighter, HSL), `slName` (tožilnik with its ž); `Case.ofId("rodilnik")`,
  `Case.ofSlot("gen.pl")`. `CaseLook.RULES`: the contract's rule ids (rodilnik-predlogi, rodilnik-nikalnica,
  rodilnik-kolicina, kam-tozilnik, kje-mestnik-orodnik, dajalnik, orodnik, tozilnik, mestnik, skloni); `CaseLook.PAGES`
  the pages that show "🧩 Vadi sklone".
- **`ui/cases/CaseColors.kt`**: `Case.color()` (Compose, by the theme), `Case.label()` ("2. rodilnik · genitive" from
  `Case.key`), `numberLabel("pl")`, `Case.spoken()` (TalkBack), `CasePill(k)` (a case's pill: its bar of colour, name,
  questions) for the lens's row to reuse.
- **The "seen" entry point:** `vm.caseTrainer.seen(rule: String)` (`app/CasesController.kt`) →
  `CaseTrainer.seen(progress, rule, today)`: a rule id the lens showed the learner, counted in
  `GameState.cases.seen[rule] = {n, day}`, never an answer. A rule seen in the last three days weighs a third more in
  the next session; the trainer's screen shows "👁 n×" on the rule.
- **The bridge:** `bridge/src/case-bank.ts` (the bank's format, checks and store) has `CASES`, `CASE_SLOT`,
  `CASE_QUESTIONS`, `CASE_EN` with the same values; `features/cases.ts` the routes and tools.
- **Merging:** whoever rebases second switches to the other's definitions and leaves no duplicate. Both branches add
  files of their own names (the trainer's `case-bank.ts` and smoke `case-trainer.ts`, the lens's `cases.ts`,
  `case-triggers.ts`, `case-seen.ts` and smoke `cases.ts`); the view model has `caseTrainer` (the lens's `cases` is its
  colours' controller); the routes differ (the trainer's GET /cases and POST /cases/done, the lens's POST /cases and GET
  /cases/seen). What is left to merge by hand: the lens's `ui/words/CaseLook` colours (keyed by `game.Case` too) and
  these extensions are one table to keep; the lens's GET /cases/seen can feed `vm.caseTrainer.seen(rule)` (its
  `RuleSeen` per rule), or the lens calls it when its word card shows a rule; `PhoneBridgeTest`'s feature count; the
  smoke's list; the four string tables (both add keys). When the lens's trigger table
  (companion/grammar/sl/data/case-triggers.json) carries a rule's one line, the bank's `rules` may point at it later
  (both key a rule by its grammar page). The bank's trigger indexes are gold and need no rules engine.

## Why it is built so

**The question words, not the case names, on the chips.** That is how the school (and Slovene children) learn the cases:
put the question where the noun is (Koga vidiš? → Mojco), and it carries over to producing the form. The case's number
and name stay under it, small, so the names are learned on the way. Six chips always, in the school's order, two a row
on a phone and three on a tablet, each at least 72 dp high.

**One colour per case everywhere** (the chips, the word framed after the answer, the deciding word underlined, the
results by case, the trainer's rows and rules): the contract's six, readable on both themes.

**"Zakaj?" after every pick, right or wrong.** The trigger → case mapping is what Jan lacks, so it is asked each time;
asking it after a wrong pick too makes the learner look for the reason before seeing it. The verdict follows the case:
wrong case, wrong; right case with the deciding word wrong, almost (it pays as almost, counts right on the case, wrong
on the rule's taps, and is a minor mistake `cases_trigger_<rule>`); both right, right. What counts as the deciding word:
the item's `trigger` and its `also` (with v and na the verb of motion or rest beside it: "Grem v šolo" takes v or grem;
an object's verb as well as "it's the object"). "Nothing decides it" is two buttons, the subject's and the object's,
because those are the two cases with no word to tap (the predicate after je counts as the subject's, kdo? kaj?).

**Kje ali kam? with the village's picture, the answer chosen.** The brief asked for "a tap turn where the place is
drawn". A tap on the picture would test where the place is, not the case: the case is in the choice between "na tržnico"
and "na tržnici". So the picture is the context (the scene that draws the place, the market, the school, the forest, the
mill …, as the village has it now: only the scenes open in the learner's village, without people, not to touch, read as
"Slika kraja: …"), and the tap is the "Zakaj?" step's: the verb that says kje or kam (gre, je, nese, sedi). 57 place
items have their scene; a place the village doesn't draw (the post office, the sea) is text alone.

**Sklanjaj! on the learner's own words, in curated frames.** The nouns are the learner's (their one-word cards whose
forms the phone has from GET /forms: every form the dictionary gives for the slot is right; the usual ending shown
first, bratu before Wiktionary's brati, sester before its sestra), the bank's 23 when they have fewer than six. The
frames (31) are written to read with any concrete noun ("Tukaj ni ___.", "Govorimo o ___.", "Pridem s ___.": no frame
that only fits a place or a person), each with a known trigger and its rule; the bank's `uncountable` mass nouns (voda,
mleko, kruh …: 24, each a noun of the dictionary) stay out of the plural and dual frames, which QA found asking "Grem k
___. (voda, mn.)"; k/h and s/z follow the answer's first letter as the forms questions do (h kmetu, s sestro), dva/dve
the noun's gender. The adjectives and possessives are a curated table (every case × gender × number and a person's
accusative: mojega brata), checked against the dictionary's adjective tables where it has the word (lep, velik, majhen,
nov, star, mlad, dober, rdeč, bel, zelen, topel, domač); the possessives aren't in the dictionary and were written by
rule and checked by hand. The cue is the phrase's nominative ("moj brat", "moje okno"), so the agreement of the oblique
case is what is typed, the brief's example ("Vidim ___ (lepa hiša)"). Rule-built adjective forms at runtime were
rejected: a table is reviewable by the native check. A spoken form (the forms book's `also`) is right and labelled,
never wrong.

**The weighting.** Every case and rule has a weakness: its wrong answers of the last 12, smoothed, plus the learner's
mistakes on its grammar page (the pages' tags against mistakes-db's pattern ids: genitive → rodilnik, iz →
rodilnik-predlogi, kam → kam-tozilnik …) counted as up to four virtual wrong answers that fade as the trainer's own
answers come in (gone after 12). So Jan's genitive weighs heavy at the first session, and less once they get it right
here. A mistake pattern whose words are all the task's (accusative_feminine_a_to_o and a feminine accusative,
possessive_neuter_moje and a neuter possessive) weighs that task more again. Asked lately: a quarter. The level moves
with the learner's accuracy (above 80 %: their level and above favoured, declining more; under 55 %: lower levels and
recognition more), towards the 60–70 % right LEARNING_SYSTEM.md asks for. Shares per session: which 45 %, Kje ali kam?
25 %, decline 30 %; a focus on kje or kam gives the place items 45 %. Interleaving: no kind twice in a row where another
is planned, no case three times, no word or sentence twice.

**Its own spaced repetition, not review cards.** A missed item comes back in the trainer's sessions: the next one the
same day, then after 1, 3, 7 and 16 days after each right answer (boxes; wrong again: the first box; almost: tomorrow),
at most three a session, spread. Review cards of every sentence missed would fill the review with sentences that aren't
words the learner owns, as the numbers module found; the mistakes themselves still reach spaced repetition through
mistakes-db (update-db.py makes their error_pattern items).

**Persisted on the node, deterministically.** A session goes through the outbox to `POST /cases/done`, which persists it
with update-db.py as grammar practice (the chosen kinds) and writing (the typed ones): a session in the log, the
mistakes, the rules as topics, the weakest case under 70 % as the next focus. The same path as the review's and the
reading's, so it is recorded with no tutor running (the hosted mode, a node without Claude); the tutor hears
`cases_done`, already persisted. An older node (404) gets a `session_end` with the same data instead (the outbox's
fallback), for the tutor to persist, as the numbers module does. The app picks the mistake's pattern: the learner's own
when one fits (its words all the task's, the most specific first, then the most frequent: genitive_after_iz, kam_vs_kje,
v_vs_na), else the trainer's (`cases_genitive_brez`, `cases_form_dative_plural_possessive`), whose words match the
grammar pages' tags, so they show on the pages' "🩹 Tvoje napake". Each answer also counts on the grammar book's page as
any exercise's (a form typed wrong with both sentences), which the dialogs' adaptive turns read.

**What a learner is asked** follows "Rules not yet" (GAME.md): an item's rule page introduced or at or below their
level, its level at most theirs (one above when its rule is introduced); the genitive after a no is A1
(rodilnik-nikalnica), the genitive's own page A2, so the rule's page decides, not the case's. The trainer opens once the
cases have begun (a case page or rule introduced: the unit tožilnik's intro, a page met; or A2). A child (the node's
`--child`): 8 tasks, no B1, at most seven words; 322 items are A1–A2 of six words or fewer.

**The storm reuses the exercise player's timed run** (its countdown, 3-2-1, the flash of a right answer, the end on the
clock): phrases ("brez mleka", "v veliki hiši", "Luka pase ovce.") cut from the bank's items (the deciding word with the
word asked about when they are at most two words apart, an adjective with its noun; a short sentence whole), never the
same case three times in a row. Gold from 14 right at 90 %, silver from 8, else bronze; the longest streak. Its answers
count as a session's. The run holds 30 phrases, more than a minute allows, so the villager's goodbye counts what was
answered ("3 od 3 pravilno", not "3 od 30": ExerciseRun's `endless`).

**The bank's format.** Tokens are the sentence split at spaces (punctuation stays on its word), the same in the app and
the bridge, so an index is unambiguous. Each item names its word, the deciding word or the role, the rule's key (whose
case must be the item's), the other readings of an ambiguous form (each checked against the dictionary: mize gen.sg /
nom.pl / acc.pl), and a note where no word decides it ("ovce could also be …: here pase has it as its object"). The
checks (`validateBank`, `bun test/check-cases.ts`, the smoke's `cases`) catch a form that is no case of its lemma, a
trigger that isn't its rule's word, a rule of another case, a page or scene there isn't, a level below its page's, an
ambiguous form no word decides without a note, a place item's answer that isn't its gap, a frame's gap twice, an
adjective form the dictionary hasn't.

## How to use it

- **Jan:** Home's "🧩 Skloni" → "▶️ Začni". Or the idea under "Danes" ("🎯 Šibko: 2. rodilnik · genitive" → "▶ Vadi": a
  session on it at once). From a grammar page about a case: "🧩 Vadi sklone". From a case unit: "🧩 Skloni". "⚡ Sklonski
  vihar" for 60 seconds of speed.
- **The tutor:** `get_cases` to read the mastery per case and rule; `assign_cases {focus: "rodilnik-predlogi", note: "iz
  and brez"}` after explaining a rule in chat (Home and the trainer offer it, a notification goes; a session with that
  focus marks it done); `publish_case_items` for sentences of a topic Jan meets (the bank's format; the tool's
  description lists the fields), checked as the bank is; `remove_case_item` takes one back.
- **Adding to the bank:** write items in `companion/cases/sl/bank.json` (README "Case trainer" lists the fields), run
  `cd companion/bridge && bun test/check-cases.ts` until it prints "ok".

## Taps

| what | before | after |
|---|---|---|
| a "which case?" question | none | Home's "🧩 Skloni" (1) → "▶️ Začni" (1): 2 |
| the weakest case practised | none (a page's "🎯 Vadi": its modules' exercises) | Home's "Danes" idea "▶ Vadi" (1) |
| one case or rule | the page's "🎯 Vadi" (exercises naming it, if any) | its row on the trainer (2 with the tile); from its grammar page "🧩 Vadi sklone" (1) |
| a case unit's practice | the unit's worksheets | the unit's "🧩 Skloni" (1) |
| the tutor's assignment | a module the tutor writes | the notification (1) → "▶️ Vadi to zdaj" (1), or Home's idea (1) |
| a case storm | none | Home's "🧩 Skloni" (1) → "⚡ Sklonski vihar" (1): 2 |

## The bank's numbers

| case | sg | du | pl | A1 | A2 | B1 | which | place | total |
|---|---|---|---|---|---|---|---|---|---|
| imenovalnik | 18 | 10 | 9 | 31 | 4 | 2 | 37 | 0 | 37 |
| rodilnik | 78 | 2 | 16 | 20 | 57 | 19 | 86 | 10 | 96 |
| dajalnik | 40 | 3 | 9 | 0 | 41 | 11 | 45 | 7 | 52 |
| tozilnik | 83 | 3 | 8 | 62 | 24 | 8 | 60 | 34 | 94 |
| mestnik | 78 | 4 | 9 | 45 | 27 | 19 | 54 | 37 | 91 |
| orodnik | 30 | 4 | 12 | 0 | 27 | 19 | 42 | 4 | 46 |
| all | 327 | 26 | 63 | 158 | 180 | 78 | 324 | 92 | 416 |

- Place items: kje-kam 52, v-na 16, pri-k 14, od-kod 10; 57 with their scene.
- Rules used (items): v-na-kje 56, v-na-kam 48, subject 37, object 29, z-s 27, k 21, nikalnica 19, pod-za-kje 19, pri
  16, iz 14, komu 13, pod-za-kam 10, o 10, kolicina 9, z-od-kod 8, brez 7, stevilo 7, pomagati 7, od 7, blizu 6, bati-se
  6, vsec 6, do 5, cigav 5, po 5, za-kaj 4, ob 4, zaradi 3, proti 3, cez 3, kljub 2.
- Words asked about: nouns 327, adjectives and possessives 37, pronouns 22, names 30. 150 items name other readings of
  an ambiguous form, 57 have a note.
- From the content: 248 (grammar examples 119, scenes 90, stories 29, readings 10); written for the bank 168.
- Checked by the dictionary: 333 words; not in it (judged by hand): pošta, tržnica, toča, veter, mlinar, planina, pomol,
  grič, kovač, strašilo, njiva, šotor, obzidje, kapa, svetilka, kozolec, torba, rozina, kovačnica, delavnica,
  podstrešje, čebelnjak, prepad, and the possessives moj, tvoj, njen, naš, svoj and svež.
- Frames: 31 (every case, singular, plural, dual); adjectives and possessives 20; nouns 23.

The bank was written by a helper agent to a spec (the format, the rules and their triggers, the distribution, the
village's world, standard Slovene without gendered first-person forms), checked item by item by the bridge's checker,
then read through in samples.

## Tests

- **Unit (JVM):** `CaseTrainerTest`, 43 tests: the contract (ids, order, question words, colours, light and dark); the
  bundled bank read, every item's word, trigger and rule consistent; grading a case picked and the deciding word (right,
  almost, wrong; `also`; the subject and the object); a frame's sentence from the forms (k/h, s/z, dva/dve, an
  adjective's agreement, a person's accusative, a spoken form right, a mass noun only in the singular); what is open
  (levels, pages, a child); the weighting (the learner's mistakes as fading wrong answers, a pattern's words, the focus,
  the kinds' shares, the interleaving, the missed items first and their boxes); the mastery and stars; the errors'
  pattern ids (the learner's own first); the wire for the node; the storm's phrases and medal; Home's idea.
  `PhoneBridgeTest.theCaseTrainer`: GET /cases and POST /cases/done on the phone's own bridge (38 features now).
  `L10nTablesTest`: the `cases.*` keys in all four tables, each named as a literal.
- **Bridge:** `bunx tsc --noEmit -p .`; the smoke's `cases` section (41 checks): the bank validates (and the checker
  catches a wrong form, a wrong trigger, a rule of another case, a missing note, a scene there isn't, a frame's gap
  twice, an adjective form and a mass noun the dictionary hasn't), its spread, GET /cases, `publish_case_items` and
  `remove_case_item`, `assign_cases` and its event, POST /cases/done persisted by update-db.py once per client id with
  the mistakes under their patterns and the assignment done, the storm's note, PUT /game keeping the progress,
  `get_village`, `get_cases`, the guide. `bun test/check-cases.ts`: ok, 333 words judged by the dictionary.
  `tutor-export --check`: 87 files, 52 tools, 32 topics.

## QA

`companion/bin/qa --no-build --no-town --steps cases` on a synthetic learner (make-learner.ts, with Jan's kind of
mistakes: genitive_forms 9, genitive_after_iz 3, kam_vs_kje 7), about seven minutes with the emulator's boot.
Screenshots in the scratchpad's `ux-cases/`: the trainer's screen (six cases, stars, the storm's button); a "Kateri
sklon?" ("V sredo grem k Tonetu v kovačnico.": dajalnik picked, then k tapped: ✅, "k / h + dajalnik → 3. dajalnik");
"Zajec je skočil iz trave!" right with "trave can also be 1. imenovalnik mn., 4. tožilnik mn."; one picked wrong ("Aha,
za pečjo si!": imenovalnik ❌, "za + si → 6. orodnik", "📖 V knjigo: Orodnik"); the session's end (each case's stars, "1
naloga pride spet"); "Kje ali kam?" ("Marko je ___ (Ljubljana).", v Ljubljani, then je: "je → kje? → 5. mestnik");
"Sklanjaj!" typed; the storm (60 s, phrases as "ni zvezd") and its bronze medal; the grammar page's "🧩 Vadi sklone";
Home. The first run found the QA's own tap on the sentence's k instead of the chip's (fixed in QA), the storm's "3 od
30" and the mass noun's plural (fixed in the app, above), and five "Nova stran v knjigi" from QA's own hook (now quiet).

## What's left

- **A native speaker's check** of the bank (its `review` field says what to look at): every sentence and translation,
  each item's case, number and deciding word, the rules' lines, the frames with any noun, the adjective and possessive
  table, the notes on ambiguous forms; and the app's labels (`cases.*` in the four tables, machine-written). Spoken
  forms are right: mark one rather than change it. Items the bank's writer was least sure of: `kam-v-podstresje` and
  `kje-v-podstresju` (na podstrešje may be more usual than v), `drva-za-sotor` (za šotor could be read as "for the
  tent"), the items whose kje and kam sound alike with a note on the verb (`pod-kapo`, `skoci-pod-mizo`, `pod-lipo`,
  `pod-smreko`, `pod-peco`, `pred-kovacnico`, `pod-veliko-smreko`), `brada-kralja` / `okoli-mize`, the verbless
  fragments from the scenes (`z-vozom`, `h-kravam`, `vsi-v-hiso`, `z-mlekom`), `na-kozolcu`'s German and Italian,
  `nom-sta-soseda`, `dajte-mi-vedri`.
- **The dictionary's errors** the bank found (Wiktionary's tables, the same the forms cards ask): potok's accusative
  given as "potoka", sin's as "sin/sinu", cesar's as "cesar" (both animate: sina, cesarja); dekle as feminine, pot as
  masculine; klop mixed with klop "tick"; avto's plural as a neuter's; many old -i datives and locatives (brati, psi,
  mleki). A learner's noun with such a form gets it in "Sklanjaj!" as right (every form of the slot is), and the shown
  one is the usual ending's. Worth fixing in the forms book (`lexicon/sl.forms.json` tables or `leave_out`).
- **Merging with the case lens** (above): one colour table, the lens's "seen" calls wired to
  `vm.caseTrainer.seen(rule)`.
- **A frame's meaning with the learner's word** stays in two parts ("There's no … here. · miza = table") rather than
  "There's no table here.": the card's meaning has its own article and form. Fine for an adult; a child's version could
  show the noun's picture (its pack's emoji) instead.
- **The picture of a place** is drawn by the scene's painter on its render thread while the task is on screen; on a slow
  phone that costs a little battery for a few seconds. A static thumbnail would cost less.
