# Built: challenges, the car's drills and numbers in English (practice-en, 2026-11-03)

*Wave E, batch 1, section 14 (followups-14.md; Jan, 2026-11-03: "I want to have app finished to release"). Branch `practice-en`, from main f9339ab8.
English is the Lake District's language: British English, learnt from Slovene, German or Italian by a grown-up. Everything written
in a language here is machine-written (Claude) and waits for a native check; each file says so (`review`), the module's screen too.*

## Native check

Machine-written, not reviewed by a native speaker: the English of 24 modules and four drills, their Slovene, German and Italian,
and the numbers module's rules and texts. British English first, then each base. These first:

**What people say: please say which are really said in the Lakes, and so must not be wrong.** No source was opened for these;
they are from my own knowledge (and the grammar pages'), so unsourced:
- **mustn't for a guess** ("It mustn't be Jack", "He mustn't have seen it"): the might-must and past-modals pages say the North of
  England (so Cumbria) and America say it. So no module offers it as a wrong option, and no typed gap expects can't where
  mustn't would come; but `grading/en.json` has no `nonstandard` row for it, so a future typed can't gap would mark a Cumbrian
  mustn't wrong. **A table row is the lead's call** (shared file).
- **bigger as for bigger than**: offered as wrong (bigger-than #1, as German's als). Some English dialects say *nor* (bigger nor
  me); is *as* said anywhere near here?
- **haven't to** for don't have to: kept out of must-have-to (it may be northern). don't must, I must to go, musted stay wrong.
- **I wish I would have …** kept out of i-wish (widely spoken); "I wish it stops raining" is offered as wrong (i-wish #3).
- **I know Jack since he was a boy** (Irish English?): offered as wrong in since-for #3.
- **Jack catch a big trout** for caught: offered as wrong (went-did #3).
- **I'm going to help you** for a spontaneous offer and **any tea** in an offer are `almost`, not wrong (if-it-rains #2,
  much-many #2): are they right enough to be right?
- **I cut my hair** for having it cut (had-it-done #1): offered as wrong after "I didn't cut it myself:".
- **Let's make a break** (make-do-take #4), **make a photo** (the course's part b list already holds it wrong).
- The numbers: **nineteen oh five**, **twenty oh five** (2005) and **twenty ten** (2010) are right; **seven and twenty** (as in
  "four and twenty blackbirds") counts as the ones first; **half five** is right for 5:30; **May fifteenth** (American) is right;
  **fourty** is wrong.

**English to check (machine-written, from my own knowledge):**
- Modules, each wrong option firmly wrong in its sentence: "She's teacher", "an teacher" (a-an-the #1, "She's the teacher" is
  not offered: it would be right); "Granny Maggie loves the dogs" typed for loves dogs (#5, in general); "It's my.", "the my
  dog", "Jacks dog" (his-her); "I go often to Keswick" (she-walks #3); "Mr Tyson usually is opening the mill" (walking-now #2);
  "go to home", "at May" (there-is-in-on-at #4); "He has been to the market" for "isn't here" (have-you-ever #3); "will to rain",
  "goes to rain" (if-it-rains #3); "stronger as", "more big", "the oldest inn of the valley" (bigger-than); "advices", "an
  advice", "two breads" (much-many); "Despite it rained", "Although it rained, but we walked" (must-be); she-said #2 "will" in
  "Jack said he ___ come, but he never turned up" (casual speech doesn't always backshift); if-i-had #3 "would bake" in "If I'd
  known you were coming, I ___ a cake"; must-be #1 "won't" as a guess; advised-me #3 "with me" for "explained to me";
  so-such #6 "Although …" not accepted beside whereas, while, whilst.
- Translations whose accept lists may lack a natural variant: be-and-have #11, there-is-in-on-at #11, if-it-rains #11, since-for
  #11, so-such #11, make-do-take #11, should-have #11.
- Drills: "I fly a kite." → "Poppy flies a kite." and "I know every pony." (no time word); "Poppy read a book last week." (the TTS
  must say /red/); "The weather is worse than yesterday."; "Alfie is the smallest of the children."; "Old Wilf wasn't on a plane
  last year."; "Did you use to live in Leeds?"; *were* in "If the tarn were warmer", "I wish it were warmer" (people mostly say
  was); "Mr Tyson has his ponies shod by Mr Dixon."; the reported set's curly quotes (“I'm tired,” said Poppy.) read aloud;
  bare first steps of builds ("We'll have finished.", "Have you climbed it?"); "Poppy and Harry want an ice cream now."; the
  riddles' "I say: kronk, kronk!" (raven), "twit-twoo" (owl), "A pony wears me on its foot." (hoof), "I'm made of stones, with no
  cement." (a drystone wall), "In the Lake District you need me a lot." (umbrella); "It's six forty-five." in the minutes set.
- Riddles a learner may answer otherwise (right, but not the answer said): sheepdog (Meg), kettle and teapot, honey and bee,
  fire and bonfire, mist (fog), raven (crow), owl and barn owl, scone (a cake, a bun), moon, tarn (a lake), the packhorse
  bridge (a bridge), stile (a gate), cairn, charcoal (coal), telescope, cat (Tibbs), pony (a horse), drystone wall (a wall).
- Grammar pages that don't teach what a module needs (for the course's owner): *explain something to someone* (reporting-verbs:
  advised-me #3 relies on its own why), *made of / made from* and *given* (passive: was-built #2, #8), *whilst* (result-linkers).

**Slovene (Jan's wife, with fran.si for what is standard):**
- Names: the modules keep the English names as the subject (Mr Dixon, Old Wilf, Auntie Edna) and decline them otherwise (gospodu
  Dixonu, Wilfu, Jacku, Samu, teti Edni); the drills write *Babica Maggie*, *Gospa Hartley*, *Stari Wilf*. Which do you want?
- scone = *pogačica* (everywhere), fell = *hrib*, tarn = *jezerce*, beck = *potok*, stile = *prelaz čez zid* (went-did #8), the
  tea cosy = *grelec za čajnik*, barber = *brivec*, painter = *slikopleskar*.
- The clock: "Ura je četrt na tri." (2:15), "tri četrt na pet" (4:45), "Ob treh četrt na deset.", "Ob devetih in deset minut.",
  "Dvajset minut pred štirimi.", "Pet minut pred osmimi.", "Ura je sedem in petnajst minut."
- Modules: "zasopel/zasopla" (since-for #7; zadihan?), "zadel na srečelovu" (if-i-had #6), "naj radio navijem glasneje"
  (want-you-to #8), "{m:Tekel|f:Tekla} sem, pa ni bilo treba" (should-have #4), "Ko bi le Bess ponoči ne lajala!", "zdaj ne bi
  {m:bil izgubljen|f:bila izgubljena}" (i-wish #9, #10), "Kje bi lahko bil?", "Kdo bi to lahko bil?" (quests); A1/A2: "Ti je
  všeč čaj?", "Nismo utrujeni.", "Tam sta dve mački.", "Jackovo kolo", "Že grem!" for "Coming, love!", "Prepričan sem" with
  the learner's gender.
- Drills: "Na tvojem mestu bi počakal na avtobus." (masculine, rare), "Včeraj si zamujal, Sam!", "Wastwater je znan kot zelo
  globok.", the wellies riddle's "Iz gume smo" with "Vedno sva dva", the clock's "Imam obraz in dva kazalca" (številčnico?),
  "Ko spuščam žabice, kamen odskoči sedemkrat.", "Pri meni je voda za čaj vedno na ognju.", and the cleft step "Po tem pohodu
  potrebujemo predvsem počitek in skodelico čaja" (the meaning, not the cleft).
- The numbers module's `numbers.en.*` lines and the supplement's 54 new meanings (lexicon/en.extra.json, the last entries).

**German (Standard, "du"):** Herr and Frau for Mr and Mrs in the drills (the grammar book mixes Mrs Birkett and Frau Birkett);
the clefts "Es war am Samstag, als Alfie sich verlaufen hat.", "Es war in Keswick, wo wir uns kennengelernt haben."; "Wenn wir bei
der Verlosung gewinnen würden, würden wir ein Boot kaufen."; "1800 gebaut, steht die Mühle noch immer."; "Der Nebel ist
aufgezogen" (must-be); "hätte ich mich jetzt nicht verlaufen" (i-wish #10); "In Sams Lieferwagen wurde … eingebrochen"
(had-it-done #11); Bergsee for tarn, Berg for fell, Zauntritt for stile, Pflaumenernte for damson picking; the `numbers.en.*` lines.

**Italian (standard, "tu"):** "gli scones" (Italian says gli scone; the grammar book's plural followed); "Ho una faccia e due
lancette."; "Zia Edna fa un copriteiera rosso a maglia vicino al fuoco."; "Il Wastwater è noto per essere molto profondo."; the
clefts ("È stato a Keswick che ci siamo conosciuti."); "Il lunedì." for "On Mondays."; "andrei dall'altra parte a remi" (if-i-had
#1, #11, clumsy?); "vincessi alla lotteria" for a raffle; "ora non sarei pers{m:o|f:a}"; "Facciamo riparare il tetto"; "è il giorno
delle susine"; "corvo imperiale" for raven, "scaletta del muro" for stile, "Faccio il contadino"; the `numbers.en.*` lines (the half
past's "come e mezza").

## What was missing (checked on main f9339ab8)

| Where | What it did | Now |
|---|---|---|
| Home's ✨ (`HomeLogic.challenges`) for a learner of English | "None yet": no module of the language (`companion/modules/en` didn't exist) | the modules at the learner's level, one request at a time |
| A run (`Content.pick`) | "No grammar drills yet", "No conversation drills yet" | the modules' exercises at the level (`EnglishPracticeTest`) |
| The car's drills | none in English: the road left them out, "Več · More" named them as missing | four drills A1–B2, named "not recorded yet" until voice-build has their clips |
| "🔢 Numbers" | no tile, nothing on the unit's page: `NumberTongue.of("en")` was null | English's own `NumberTongue` (`EnglishNumbers.kt`), opened by the unit `numbers`, the clock and dates by `time` |
| The practice smoke | held Italian only, with Italian's levels, bases and "ventitré" | each language names its bases, levels and 23; English's held to B2 |

## Modules: `companion/modules/en/`

24 modules, six a level, each of twelve exercises in the order Italian's have (three choices, a multi, four gaps, two sentences
to build, a translation, one to say aloud), each exercise naming its grammar page (A1's to B2's pages of `grammar/en`), each with
a giver of the Lake District's cast. The givers follow the cast's arrival order at A1 (Granny Maggie, Shepherd Jack, Poppy,
Old Wilf, Mr Tyson, Alfie), so the first one comes as soon as its giver lives there. Every text the learner reads is per base
(`sl`, `de`, `it`; `en` as the format's fallback), so `GET /modules` gives an app of any age plain strings.

| Level | # | Module | Giver | Pages | Title |
|---|---|---|---|---|---|
| A1 | 1 | `be-and-have` | Granny Maggie | to-be, to-have | I'm, I've got |
| A1 | 2 | `a-an-the` | Shepherd Jack | articles, plurals | A, an, the, sheep |
| A1 | 3 | `his-her` | Poppy | object-pronouns, possessive-s | His, her, Jack's, me |
| A1 | 4 | `she-walks` | Old Wilf | present-simple, questions-do, word-order | He tells, does he? |
| A1 | 5 | `walking-now` | Mr Tyson | present-continuous, present-simple | He's working now |
| A1 | 6 | `there-is-in-on-at` | Alfie | prepositions-place, there-is-are, time-dates | There's, in, on, at |
| A2 | 1 | `went-did` | Auntie Edna | past-simple, was-were | I went, did you? |
| A2 | 2 | `have-you-ever` | Beekeeper Arthur | past-simple, present-perfect | Have you ever …? |
| A2 | 3 | `if-it-rains` | Mr Dixon | first-conditional, future | I'll, going to, if it rains |
| A2 | 4 | `bigger-than` | Brewer Sam | comparatives | Bigger than, the best |
| A2 | 5 | `much-many` | Mrs Birkett | indefinite-pronouns, some-any | Some, any, much, many |
| A2 | 6 | `must-have-to` | Mrs Hartley | must-have-to | Must, have to, should |
| B1 | 1 | `since-for` | Granny Maggie | for-since, present-perfect-continuous | for, since |
| B1 | 2 | `if-i-had` | Old Wilf | second-conditional, third-conditional | If I had, if I'd known |
| B1 | 3 | `was-built` | Mr Dixon | passive | was built, is made of |
| B1 | 4 | `she-said` | Mrs Birkett | reported-questions, reported-speech | She said, she told me |
| B1 | 5 | `want-you-to` | Auntie Edna | used-to, verb-patterns | I want you to come, I used to |
| B1 | 6 | `must-be` | Shepherd Jack | contrast-linkers, might-must | It must be, it can't be, although |
| B2 | 1 | `should-have` | Granny Maggie | past-modals | should have, must have |
| B2 | 2 | `i-wish` | Old Wilf | mixed-conditionals, wish-if-only | I wish I had, if only |
| B2 | 3 | `had-it-done` | Mr Dixon | have-it-done, passive-more | I had it mended, it is said |
| B2 | 4 | `advised-me` | Mrs Hartley | reporting-verbs | suggested going, advised me to |
| B2 | 5 | `make-do-take` | Brewer Sam | collocations, phrasal-verbs-more | make, do, take, put up with |
| B2 | 6 | `so-such` | Mr Tyson | emphasis, result-linkers | so cold, such a cold day, It was … who |

The B1 and B2 modules were written by a helper agent of this branch to the A1 model (`be-and-have`); all 24 were read and
checked by the same script and checks.

**What each drills is what a learner from Slovene, German or Italian gets wrong**, from the grammar pages' own warnings and what
the three languages say instead: the age with have (imam trideset let, ho trent'anni), no article for a job (sem učitelj, ich
bin Lehrer), his/her by the thing (Italian's suo), the dog of Jack, the ones first and "often" after the verb (ich gehe oft),
it gives (es gibt), on the picture (na sliki, auf dem Bild), the present perfect with yesterday (the German Perfekt and the
passato prossimo), will after if (wenn es regnen wird), bigger as (als, kot), informations and advices, mustn't for don't have to
(du musst nicht), I live here since (seit, da, že), If I would have, I want that you come, She said me, despite of, mustn't
have for can't have, suggested me to go, explained me, make a photo, so cold day.

**What people say is never a wrong choice.** Every wrong option and every distractor tile was checked against
`grading/en.json`'s `nonstandard` list (a scan in the scratchpad; none matched: should have went, I seen, we was, the man what,
a double negative, He asked where did I live, use to are nowhere offered as wrong). A point that is such a form is tested by a
typed gap, where the table grades it almost with the standard form shown. Where a choice has a real answer that fits worse
("I'm going to help you" for a spontaneous offer; "any tea" in an offer) it is `almost`, as the spec allows.

**The pace** is the bridge's (`ModuleStore.offered`, docs/plans/05-built/practice-it.md "The pace"): one request open at a time,
at most one new a day, in the level's `order`, only a giver who lives here; a module above the learner's level never. Nothing
English-specific: the smoke holds each level's order 1 … 6, every giver a name of the cast. A synthetic learner of the QA
fixture (residents alfie, arthur, birkett, dixon, edna, harry: the first six by id) is offered one: Alfie's
`there-is-in-on-at`, the first whose giver is there.

## Drills: `companion/drills/en/`

Four files, `"language": "en"`, the ids, kinds and emoji of Slovene's and Italian's, written by a helper agent of this branch
in the Lake District's world (the fells, the beck, the tarn, the Packhorse, the cast):

| Drill | File | What is in it |
|---|---|---|
| 🔄 Transformations | `preobrat.json` | 302 items in 21 sets, the easiest first, one set a rule of the grammar book. A1 (95): plural (My tooth hurts. → My teeth hurt.), he or she (-s), say no, ask, now (-ing), yesterday (was, were). A2 (73): into the past, ask about the past (did), going to, make it more (than), ever and never. B1 (68): the passive, report it, if it were so, used to, how long (for, since). B2 (64): should have, I wish, have it done, It was … who, it is said. A set of a rule the learner hasn't met isn't played. |
| ⚡ Rapid fire | `hitri-odziv.json` | The numbers 1 to 100 and 26 from 101 to 999, generated as the bridge and the app count them (twenty-one, one hundred and five); the clock (It's half past five.), at what time, days, when (On Monday. At the weekend. In the morning.), months, dates (The first of May.), an A2 set of minutes (It's twenty to four.), B1's years and centuries (Nineteen oh five., In the eighteenth century.) and B2's numbers as the news says them (Three point five per cent., Two thirds., Half a million.): 179 listed, the clock's, days' and dates' sets on `time-dates`. Each of the four reaches B2, as the smoke now holds every language's. |
| 🧱 Sentence building | `gradnja.json` | 80 sentences grown in 3 or 4 steps ("Old Wilf tells a story." → "… the children a story." → "… under the oak." → "Every evening …"): 40 A1, 16 A2, 12 B1, 12 B2, each B1/B2 one naming its page (relative clauses, the passive, the conditionals, past modals, participle clauses …). |
| 🕵️ Riddles | `uganke.json` | 53 riddles told by Old Wilf (`teller: wilf`): 32 A1, 10 A2, 7 B1, 4 B2; 44 are words of the Lake District's and the English packs (every id checked), 9 are villagers (one is Wilf himself). |

**Every text in every base.** A drill of English says English, and the phone says its meaning in the learner's base before the
pause. The app falls back to English where a base is missing (`DrillText.meaning`), which for English would be the answer itself
as the cue. So every text, cue and title of English's drills has `sl`, `de` and `it` (and `en`, the format's), and the smoke holds
English to its bases (Italian's stay held to English and German, as before). `EnglishPracticeTest` checks that no transformation's
cue in any base is its English answer.

## Numbers: `data/EnglishNumbers.kt`

The module's ladder, runs, schedule and screens are shared (`NumberTongue`); what is English's own is one object in its own
file, its texts `numbers.en.*` in the four string tables, its unit `en/numbers` in the bridge's `NUMBERS_UNITS` (for
`get_village`), and the tutor's counting guide (tutor-export regenerated).

| | British English | Graded as |
|---|---|---|
| Words | `SpokenNumbers.english`: twenty-seven, one hundred and five, two thousand and twenty-six, one thousand two hundred and thirty-four | the hyphen left out: right (grading/en.json's `same`); the words joined (twentyseven): almost; a hundred, a thousand, the American without and (one hundred five), a year in pairs (twenty twenty-six, nineteen oh five, nineteen hundred): right; another number's word: wrong |
| Spelling | forty, nineteen, ninety, fourteen | fourty, ninteen, ninty, forteen: wrong, as the grading table's `wrong` has them, not a slip (`ownWord`) |
| Order | the tens first; German and Slovene say the ones first | 72 for 27, "seven twenty", "seven and twenty": `numbers_order`, its why says so in the base |
| Date | "Today is the ___ of May. (15th)": the ordinal (`SpokenNumbers.englishOrdinal`); the fifteenth of May, May the fifteenth, May fifteenth right too | a regular ordinal where English has its own (fiveth, nineth, twelveth, twentyth, twenty-oneth): wrong, not a slip (`dateSlip`); "one" for first: wrong |
| Clock | at five, at half past five; five o'clock, half five (British speech), five thirty, at noon, at midday right | another hour: never a slip |
| Half past | from the hour that is there; German's "halb sechs" and Slovene's "pol šestih" are 5:30 | 4:30 typed for "at half past five", "half six" or "half past six" written for 5:30: `numbers_clock_time_half` |

A learner from Italian has no half-past trap (e mezza counts as English does): the why says so in Italian ("come e mezza").
`NumbersEnglishTest` (13) holds a hand-checked table of words and ordinals, reads every number 0 … 9999 back with its other forms,
and checks the grading above in German, Slovene and Italian; `NumbersItalianTest` now expects English to have its tongue.

## Tap counts (a learner of English on Home)

| Journey | Before | After |
|---|---|---|
| A grammar drill | none to play: ✨ said "None yet" | 3: ✨, the module, "▶ Start" (its giver's card between) |
| Numbers | none: no tile, nothing on the unit's page | 2 once `numbers`'s intro is done: 🔢, "▶ Start"; closed, 1 to the intro |
| The car's drills | none in English | listed under "Več · More" as not recorded yet; they play once voice-build has their clips (Jan's credits) |

## Older apps

Nothing here needs a new app to stay right on an old one. The modules come from the bridge as plain strings in the base, with
the exercise types every app reads (choice, multi, cloze, reorder, translate, speak: Italian's); an app that doesn't know
`almost` grades that option wrong, as before. The bridge never offers a module above the learner's level (it did that already,
on the bridge, for 0.2.698). The drills are served at `GET /drills` for the learner's language and bundled in a new app; an old
app of a learner of English (none is known) reads them as Italian's. The numbers module is the app's code: an old app has no
English tile, as today. Teo's tablet (Italian) and Jan's phone (Slovene) get nothing new from this branch.

## Voice

Nothing was voiced; no ElevenLabs call was made. A build would take (each text once, as `voice-build` counts the corpus: the
drills by `drillTexts`, the modules' options read aloud and their `say`):

| What | Texts | Characters | Voice |
|---|---|---|---|
| Drills A1 | – | 16,039 | the narrator (`female`, Alice) |
| Drills A2 | – | 7,942 | the narrator |
| Drills B1 | – | 7,477 | the narrator |
| Drills B2 | – | 6,438 | the narrator |
| **All the drills** | 1,430 | 37,896 (the narrator 31,278; the riddles 6,618 in Old Wilf's speaker, `grandpa`, George) | |
| The modules (`say`, options read aloud) | 350 | 5,328 | the narrator |
| **Together** | 1,780 | 43,224, about 21,600 credits on `eleven_v4_turbo` (`MODEL_CREDITS` 0.5) | |

An upper bound: a text the store already has isn't made again. The numbers module's own words aren't in the corpus: they are
made when heard, on demand (`POST /voice/say`), as Italian's. The speakers a build needs: the Lake District's `female` (the
narrator) and `grandpa` (Old Wilf); both are in its voice cast (`cultures/lakeland/voice-cast.json`). `voice-build`'s
priorities put `modules` and `drills A1` before `drills A2+`, so a budget that runs out has the easiest. Jan's decision.
Until then the road leaves the four drills out and "Več · More" names them as not recorded yet.

## Checks

**On the merge with `practice-de`** (a80353ef, which holds main 7b1e5134 with Italian's practice to B2 and German's practice;
kept both everywhere, bin/qa as practice-de's with `practice_en` inserted whole, every practice function identical to its
branch's): `tsc`; `check-practice.ts` for it, de and en (all good); the smoke's practice section on its own (all its checks);
`tutor-export --check`; Python `tests` and `companion/tests` OK; the smoke (all 3,762 checks passed); the practice Android tests, 130, all pass (EnglishPracticeTest, GermanPracticeTest, ItalianPracticeTest, NumbersEnglishTest,
NumbersGermanTest, NumbersItalianTest, NumbersPracticeTest, L10nTablesTest, DrillsTest, RoadDrillsTest, RoadMenuTest,
CuratedPaceTest, QaHooksTest).
The merge's smoke holds every language's four drills to B2 with a rapid set at every level, so English's rapid fire got B1's
years and B2's news numbers (4639647). The full Android suite was run before this merge (below), not after it.

Before it, on this branch from main f9339ab8 (before the merges with main e6209062 and practice-de):

- Bridge: `tsc`; `bun build --no-bundle` of the two changed test files; the smoke (ports 10570–10589: all 3,728 checks passed); its practice section on its own
  (all its checks: 12 of English's modules, 13 of its drills, Italian's unchanged); `bun test/check-practice.ts en` (all good);
  `tutor-export --check` (140 files).
- Android: `NumbersEnglishTest` (13), `EnglishPracticeTest` (5), and beside them `NumbersItalianTest` (11), `NumbersPracticeTest`
  (18, Slovene's, unchanged), `L10nTablesTest` (6), `ItalianPracticeTest` (4), `DrillsTest` (8), `RoadDrillsTest` (11),
  `RoadMenuTest` (6), `CuratedPaceTest` (4): all pass. The full unit suite: 4,115 tests, 0 failures.
- Python: `tests` (253) and `companion/tests` (164, 11 skipped): OK.
- Coverage: `lexicon-build --coverage --lang en` 99.7 % of tokens, 96.7 % of words, as on main; the drills scanned on their own.
- A scan of every module's wrong options and tiles against `grading/en.json`'s `nonstandard` list (none is a form people say).
- **Emulator QA** through `qa-slot` on a synthetic learner of English from Slovene (`make-learner.ts --target en --base sl`,
  `LANI_CULTURE=lakeland`), `--steps practice-en`, screenshots `pe-*.png` in the scratchpad's `ux-practice-en/shots/`:
  - *before* (`LANI_QA_PRACTICE_BEFORE=1`, the dev bridge's `LANI_MODULES_DIR` an empty directory, as main had it): ✨'s sheet
    says "No challenges yet · Še ni izzivov" (`pe-before-challenges-sheet`);
  - *after*: the ✨ tile with one new, the sheet offering A1's `there-is-in-on-at` alone (Alfie lives in the fixture's village,
    Granny Maggie doesn't), its card in Slovene, its first question, "It gives" against There's, the why in Slovene; the numbers
    closed, the unit numbers's intro one tap away and skipped, the note, the module open with English's tip in Slovene; "three
    sixty" written for 63: wrong, "Angleščina pove najprej desetice"; "half five" written for 4:30: wrong, "half past šteje od ure,
    ki je že tu (v govoru tudi half four), ne k naslednji (5) kot v slovenščini"; 🔢 on Home; the car's sheet naming
    Transformations, Rapid fire, Sentence building, Riddles among what has no recordings yet.
- Not run: a number heard in English on the emulator (the code is shared with Slovene's and Italian's, which QA hears), a module
  past its first question, a learner from German or Italian on the emulator (the unit tests read their whys), a car, a tablet.

## What's left

- **The voice build** (Jan's credits, above). Until then the four drills stay off the road's menu, named as not recorded yet.
- **mustn't for a guess** has no `nonstandard` row in `grading/en.json` (see the native check): the table is shared, so the lead
  decides; nothing here grades it wrong today.
- **A reorder leaves tiles over**: the app compares only the tiles placed, so a right sentence made without all of them is wrong
  unless it is a solution (want-you-to #10 "Where did Edna work?", she-said #9 "He asked where I lived."). That is how reorder
  works everywhere; the distractors here can't build a right version of the prompt.
- **The grammar pages** don't teach explain something to someone, made of / made from, given among the participles, whilst
  (the native check's list): for the English course's owner; the modules' whys say it themselves.
- **German** has no modules, drills or numbers yet (practice-de, beside this branch). Its numbers will be one more `NumberTongue`
  beside `EnglishNumbers`; the smoke's `LANGUAGES` takes it as one more line.
- `about_exercise.source` of a numbers task doesn't say its language (as Italian's); the tutor knows the learner's.
- The base `en` of English's texts is the format's fallback only: nobody learns English from English.
