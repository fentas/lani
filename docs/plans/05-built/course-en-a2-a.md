# Built: the English course, A2 part a (2026-11-02)

Wave D (followups-12.md): English A2's units 1 to 6, commands and the past. All of it is machine-written (Claude): nothing here
has been read by a native speaker. The list for that is first.

## For the native check

**British English** (every unit's examples, prompts, options, gap texts and transforms):

- **imperatives:** Mind the step!; Please shut the gate. and Do not feed the horses. as signs; Don't let's as a spoken form beside Let's not; You are careful as an exercise's line to turn into an order.
- **past-regular:** Sam playing the fiddle at the inn; the three sounds of -ed (t, d, id) as taught; learnt, burnt, dreamt beside -ed; "carried a cup of tea out to him".
- **past-irregular:** "on the bus"; He said hello to everyone; Then he saw the last bus and ran!; read "said like red"; told takes a person, said doesn't.
- **did-you:** Who did Jack see? with who rather than whom; Who saw it last?; He didn't have time; It didn't stop all night.
- **past-continuous:** shearing all morning; washing up; the lights went out; I was fishing; "never *during I was cooking*".
- **because-so:** cos, 'cause, cause as spoken; Milk or sugar?; no comma after then; We ate first, and then …; After we ate.
- **The pages:** `imperatives`, `past-continuous`, `linking-words` and their cards; the comma rules in `linking-words`.
- **The title of `because-so`:** see "For the lead".

**Slovene** (ti; the learner's gender through `{m:…|f:…}`): the sheets' glosses dajmo for let's, načrtoval, sanjal; vrata for a field gate; Ne hranite konj; Bodi tiho; Spijmo skodelico čaja; pogačice for scones; šli na prvi avtobus; zagledal; bare Ja. and Ne. for short answers; ni ponehala (the storm); Ste dobro spali; ravno for "was …-ing"; nedovršni glagol; postiljala; Pogovarjala sva se; Povečerjali smo; "potem ko" as the label of after; the supplement's new meanings.

**German** (du; Austrian where it differs): the sheets' glosses lass uns for let's, plante, träumte; Gatter and Weidegatter for gate; Mach das Gatter zu; zugesperrt; am Feuer gesessen; hat den Kuchen angeschnitten; Hat dir Meg geholfen?; Wohin bist du gegangen?; schor; klingelte; Ich ging gerade; also for so; heimkam; verstummte; Komma, Beistrich in the supplement.

**Italian** (tu): the sheets' glosses facciamo for let's and ha organizzato for planned; Fai silenzio; Fai attenzione; Prego, entri; Si sieda; pane tostato; fienile for barn; laghetto for tarn; Il birraio Sam; si è messo a correre; the glosses ho corso, sono partito, sapevo; Jack chi ha visto? against Chi ha visto Jack?; stava lavorando a maglia; è andata via la luce; tosava; Poiché pioveva; Dopo aver mangiato; "dopo che" as the label of after.

**The grading's calls** (grading/en.json): that the present for an irregular past (come for came), a participle for it (sung for sang) and the -ed spellings are wrong and not one letter off; that `cos`, `'cause` and `cause` are right beside because.

## For the lead

**`because-so`'s title teaches the mistake.** The outline's English title is "Because it rained, so we stayed", and it heads the unit, the blackboard and the printed sheet. Step 2 of the same unit says never to write it. The brief says to keep each title, so this branch keeps it. The fix is the one field, `title.en`, in the outline's line and in `units/because-so.json`, for example "It rained, so we stayed" (no text of the unit mentions the title, and the other three languages' titles are fine).

**Once merged, also:** nothing for this part; the outline's column has no later page for units 1 to 6.

## What was built

| # | Unit | Teaches | Pages | Sheets' items | Test items |
|---|---|---|---|---:|---:|
| 1 | `imperatives` 📢 | the base form for everyone; Don't + the base form; be; always, never; Let's, Let's not; please, Could you …?; directions, recipes, signs | **imperatives** | 30 | 12 |
| 2 | `past-regular` 🚶 | -ed for every person, no helping verb; yesterday, last week, ago; -e + -d, the doubled consonant, -y → -ied against stayed; the three sounds; British learnt, burnt, dreamt | past-simple (summary of its own) | 29 | 12 |
| 3 | `past-irregular` 🧳 | about thirty irregular pasts, by families; put, cut, shut the same; read said like red; told and said; told in a story with -ed verbs | past-simple (summary of its own) | 28 | 12 |
| 4 | `did-you` 🔍 | didn't and Did + the base form; short answers; Where did you go?; who as the subject takes no did; be takes no did | past-simple, questions-do (summary of its own) | 29 | 13 |
| 5 | `past-continuous` ⏳ | was, were + -ing; wasn't, weren't, Were you …?; the long action with when; while; -ing's spelling; state verbs without -ing; how a story opens | **past-continuous**, past-simple, present-continuous | 29 | 13 |
| 6 | `because-so` 🔗 | and, but, or; because against so, one in a sentence; Why? Because …; when, before, after and their comma; then; all of it in a story | **linking-words**, past-simple | 29 | 12 |

Each unit has six intro steps with a quick question each, three worksheets and a test (pass 80), the same size as the Italian A2 and English A1 units. The lines are set in the village's adult life: Mrs Birkett's inn, Jack and Meg on the fell, Mr Dixon's smithy, Mr Tyson's mill, Arthur's bees, Old Wilf's stories, the Keswick bus. The children (Harry, Poppy, Alfie) appear only as Mrs Birkett's and the village's, as in A1. Around a gap, each unit uses only what came before it: `past-regular` has only regular pasts and was, were; `past-irregular` has no did. Explanations, summaries, goals, titles, instructions, hints, whys, labels and translations are in Slovene, German and Italian, and in the English the format wants.

**The three new pages** (companion/grammar/en, level A2), each with its hand-written card. `past-simple` and `questions-do` are named, not rewritten. Since `past-simple` goes on further than the unit it heads, units 2 to 4 end their intro on `intro.summary`.

**British first, the American form accepted:** travelled and traveled through the existing grading; learnt and learned both in `accept`; let's and let us the same answer.

**Spoken forms:** on every gap whose answer is because, `cos`, `'cause` and `cause` are right, labelled with because as the standard form; `Don't let's wait` beside Let's not wait. None is a distractor.

### The grading (grading/en.json, appended to `wrong`)

Each is paired, so it counts only for a word of its group and a slip on any other word stays a slip:

| Mistake | Before | Now |
|---|---|---|
| stoped, planed, droped, claped for stopped, planned … | almost | wrong |
| openned, visitted, listenned, rainned for opened … | almost | wrong |
| carryed, tryed, studyed for carried …; staied, plaied, enjoied for stayed … | almost | wrong |
| come for came, make for made, know for knew, drink for drank, and the other way (did you came?) | almost | wrong |
| sung, rung, drunk, swum, begun for sang, rang, drank, swam, began | almost | wrong |
| fell and feel, want and went, wore and were, sold and told, bought, brought, caught, taught, thought, fought | almost | wrong |
| then for than or when, and the other way | almost | wrong |
| rainning, waitting, listenning and the like for raining … | almost | wrong |

Unchanged: goed, runned, heared, likeed were wrong already (an ending or the distance). walkd, becouse, hapy stay slips.

### The words (lexicon/en.extra.json)

2,227 → 2,254 entries: the imperatives' phrases (sit down, come in, be careful, be quiet, be late, turn left, go straight on, cross the bridge), the verbs' forms the units use (clean, plan, snow, visit as verbs, enjoy, study, travel), the noun toast, shear, wash up, during, coffee, get home, go to bed, and the words the new pages explain with (comma, reason, result, kindly, cos, earlier, imperative, link). Every unit's `words` prints with its meaning in each base. The only words of the units no entry knows are the wrong forms they name (goed, staied …), which get none on purpose.

## Voice

Not built, no credits spent. Mrs Hartley's English lines for the six units are 347 distinct texts, about 7,500 characters, the same for every base (`voice-build --only teacher`). The explanations, one clip a step and summary, are about 9,900 characters in Slovene, 10,600 in German and 10,700 in Italian (`--explanations`), about 31,200 for all three.

## Checks

On the branch's last content commit, `55b0379f`:

- **Bridge:** `tsc` clean; the smoke on ports 9910– (LANI_SMOKE_DRAMA3_PORT 9928): 3,508 checks, all passed; `tutor-export --check`: 89 files match; `prompt-clashes`: none new (this part adds no pack).
- **Android, the course's tests through `lani-slot.sh`:** 105 tests, 0 failures: `EnglishCourseContentTest` 6, `EnglishA2aContentTest` 8 (new), `GradingLanguagesTest` 48, `GrammarTest` 16, `SchoolBoardTest` 7, `SchoolRevealTest` 10, `SentenceGrammarTest` 6, `IntroSpeechParityTest` 2, `IntroBoldTapTest` 2. The whole suite is part c's and the gate's, as the brief says.
- **Words:** `lexicon-build --coverage --lang en`: 98.4 % of tokens and 97.4 % of different words meant in each base, as before this part, with 15 more different words; 81 words none knows, as before.
- **Sheets:** `school-prints --language en --level A2` in de, sl and it: all six fit (`imperatives` and `did-you` one page, the others two), the booklet 12 pages, no word left without a meaning. Each printed word was read against the unit's sense in de, sl and it for the words below; each step's rule line was read in de and scanned for broken brackets in all three.
- **Emulator:** `course-unit` through qa-slot (slot 2), a synthetic learner of English from German in the Lake District, unit `because-so`, end to end: the six intro steps and their quick questions, the summary, the first worksheet (the sort, the table), the test passed, the sheet printed in the app (preview and PDF). That run was on `56ee2126`; the one commit after it changes only the units' `words`, a few glosses in `say` and the supplement. Screenshots: the scratchpad's `ux-course-en-a2-a/`. The other five units were not played on an emulator.
- **Not run:** the Python tests (no Python changed); a voice build.

**What the checks found on the way, fixed:**

- The bridge refused all three new pages: a page's rule is at most 1,200 characters per base, and the app's parser doesn't check that. The smoke caught it (22 of 25 pages served).
- A translation of two sentences in one bracket, or inner exclamation marks in one, was cut in the print's rule line ("(Wir blieben zu Hause, weil es regnete."). Each now has its own bracket.
- A tall intro table: `because-so`'s third column repeated whole sentences, so its later quick question lay four swipes down, and QA gave up. It now holds short phrases.
- Words printed in the wrong sense: cross as Kreuz, bark as Rinde, dance, visit, plan, dream as nouns, clean as the adjective, and "let us" and "did not" as the German for let's and didn't. The units now list the form they use (danced, barked, fishing) or a phrase, or gloss it themselves.

`past-irregular`'s table reaches 25 rows by its last step. It passed the fast checks and fits its print, but no emulator run has played it, so how far a learner scrolls to its later questions is unmeasured.

## What's left

- The native check above, and the decision on `because-so`'s title.
- COURSE.md, the README and the content counts are part c's, from this doc (the brief).
