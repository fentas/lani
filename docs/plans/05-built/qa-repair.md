# Built: QA steps and checks that were broken or missing (qa-repair)

*Built 2026-11-02, branch `qa-repair` (wave C, part 6: the leftovers; followups-8.md). Jan: "sure continue". Eight items
from the earlier rounds' leftovers: four QA steps, a smoke check, voice-build on another learner's data, the English
endings' voice and the day's audio on a visit. Each was checked on main (21aa4e9b) first.*

## What, item by item

### 1. The road step: "nothing in 🚗 Za pot on the phone" — fixed

**Before.** On a synthetic learner (`LANI_DEV_DATA`, `--no-town`) the step failed after the channel check, on main too:
`road:mix 0, road:quiz 8`, every other session 0 (reproduced on main's build: `runs/road-before`). The car plays only
what the voice store has (`road/RoadGather`: getting the road ready never has anything voiced), and a synthetic
learner's dev bridge has no clips, so every card, word, dialog and story was left out; only the quiz's options, which
the phone renders itself, were there. The step passed on Jan's data, which has its clips; agents may not use it.

**Now** (`companion/bin/qa` `road_clips`, before `road:mini`): the dev bridge voices what a small library takes, through
`POST /voice/say` in the female narrator's voice (the last voice of every chain, `Clips.chain`, so every lookup finds
it):

- four cards due within `RoadGather.DUE_AHEAD_DAYS` (3);
- four next words of the first pack the road teaches (`RoadGather.packOrder`: the packs under way first, then the
  easiest), each with its example;
- what the shortest dialog of the village's scenes and the shortest story evening at A1 play (`RoadPlay.play`: the
  lines, the right choices and their replies; a line with `{name}` is said live, left out);
- at most 45 texts: the bridge voices 60 live lines in ten minutes (`VoiceStore.liveSlot`), and the app keeps some.

A text that has its clip comes back as it is (a copy of a learner's data needs nothing). It needs a voice engine on the
dev bridge: `LANI_DEV_FAKE_ELEVENLABS=1` (its tones). Without one and without clips the step skips and says why, as
`voice-node-clip` does. `companion/tests/test_qa_road.py` (3) checks what is voiced, the 45 and the skip.

How to run it: `LANI_DEV_DATA=<a synthetic learner> LANI_DEV_FAKE_ELEVENLABS=1 companion/bin/qa-slot --no-town --steps road`.

### 2. The coast step's "open village" after Nonna Rosa's talk — already done

60908e38 (2026-10-30): Home is found by the Today card's heading in the pair the phone shows, so a villager's page
("📅 Danes · Today") no longer passes for it once the pair switches back. Run again on main's build: passed.

That run showed something else: `LANI_QA_FOUND_SEED` did nothing. Setup founds a learner without a village, so the seed's
hook came after the founding, the village stood on another coast (land `coast:618113273`, not Teo's `551323397`), and no
preview was shot. Now `founding_seed` runs on the founding screen in setup as in `found_village`: `coast:551323397`, the
preview shot, a squirrel's card ("scoiattolo"), the talk's goals in it · de.

### 3. visit-home expects the village after "⌂ Home" — already done

60908e38: visit-home expects Friends with the visited town on it, then goes back to the village. Run again: passed.

### 4. friuli-school waits for a "preparing" line — already done

58d07a3e (2026-10-31): with two or more A2 units written, the step checks A2 open and its unit begun, and no "preparing"
line. Run again: passed.

### 5. A prompt-clash smoke check — built

`companion/bridge/test/prompt-clashes.ts` holds every word of the curated packs (`companion/packs`, its language folders,
every culture's `packs/`) to card.py's `overlap()` through its port (`src/db/card.ts`: `overlap`, `quizzed`,
`promptParts` and `normalize` now exported): for each language and each other base, the card the bridge makes of a word
(`packs.ts learnPayload`: the word on its front, `meaningOf(base)` on its back), rendered for a man and for a woman. A
clash is the same prompt part when one of the two is asked for, and the words apart (two words that take one answer are
one card, `learnedWords`).

The 346 clashes there are now are its baseline, `test/prompt-clashes.json` (language → base → "<word> | <word>" → the
prompts they share): the packs' synonyms (Grüß Gott and Guten Tag, Servus and Tschüss, das Bankerl and die Bank) and a
few homonyms (well: bene and il pozzo; bark: bellen and die Rinde). By language and base:

| | → sl | → en | → it | → de |
|---|---|---|---|---|
| sl | | 59 | 59 | 37 |
| de | 17 | 24 | 16 | |
| en | 32 | | 25 | 22 |
| it | 16 | 24 | | 15 |

The smoke's packs section fails on a clash not in the baseline, checks a toy pair of packs (a shared Slovene prompt, a
gender pair, a word only flipped against one asked, the same word in two packs as one card) and that the named synonyms
are found, and lists the baseline's entries that are no longer there. A Python prototype over card.py itself found the
same 346.

How to use it: `cd companion/bridge && bun test/prompt-clashes.ts` lists the baseline's clashes and any new one (exit 1);
`--write` writes the baseline anew (review its diff: a new entry is a synonym on purpose, or a word that needs a prompt
of its own). The lani-studio pack rules (`reference/module-spec.md`) say a word needs a prompt of its own.

### 6. voice-build on Teo's data — the grammar part already done, the role-plays fixed

e31a7fff (2026-10-30) made `voice-build --data` take the village and the language from the learner (LANI_CULTURE, else
the registry, else the course the profile teaches), and its smoke checks that a tutor's Italian page is read. A dry run
on a synthetic Teo (`make-learner.ts --target it --base de --name Teo`, `tu-lei.json` in his `app/grammar`, no keys:
`LANI_KEYS_FILE=/nonexistent`, `ELEVENLABS_API_KEY=`, Gepard on a dead port) showed no "this book is" line, but four
Friuli scenes skipped: "talk "la-ricreazione" is not a scenario". voice-build's `ScenarioStore` had no language, so it
kept none of `companion/scenarios/it`. It now reads them in the village's language, as the bridge does (`build.ts`).

| Teo's plan (dry run) | before | after |
|---|---|---|
| scenes skipped | 4 | 0 |
| scenarios | 0 texts | 58 texts, 1,372 chars |
| scenes | 4,560 texts | 5,203 texts |
| corpus | 14,313 texts, 433,148 chars | 15,772 texts, 476,603 chars |

Nothing was sent anywhere. The smoke's course-speech section now gives the run a Slovene and an Italian role-play: the
Italian learner's plan counts the Italian one and never says "not a scenario"; with `LANI_CULTURE=primorska` the Slovene
one. The smoke's own fake ElevenLabs covers `--run` (its budget check), as before.

### 7. English -teen and -ty in the base's voice — fixed, in every course

**Before.** A bold span of an explanation is read in the teacher's voice when the app's check (`Speaker.isSlovene`) or the
intro's own words make it the language learned's. "**-teen**" and "**-ty**" are neither, so the stitched reading (when
the explanation's one clip can't be had) gave them to the phone's voice of the base, and they weren't underlined to
hear. The same held in every course: Italian -are, -ere, -ire → -endo, German -zig, -st, Slovene -ega, -ih (the ones with
č, š, ž were Mojca's already: their letters).

**Now** (`data/IntroSpeech.endings`, its port `course-speech.ts endings`, both in `learned`): a span of endings only, each
the end of a longer word the intro shows ("-teen" of thirteen, "-ty" of twenty, "-o, -i, -a" of canto, canti, canta), is
the language learned's: read in her voice and underlined to hear, as the words are. An ending no word of the intro has
("-ing" in a unit without one) stays the base's.

On the four courses in every base: 88 ending texts more in the teachers' voices (Slovene 59, Italian 23, German 3,
English 3: -ing, -teen, -ty), 410 characters for voice-build, none fewer. They need clips (voice-build `--only teacher`:
Jan's call, credits); until then the app asks the node as for any line.

The shared fixture (`bridge/test/fixtures/intro-speech.json`) has a case for it (English endings for a learner of
Slovene), read alike by the app (`IntroSpeechParityTest`) and the smoke; `IntroSpeechTest` checks the rule's edges. The
smoke's base-word check no longer counts a word after a hyphen ("-are" is Italian). COURSE.md says so.

QA's `course-unit` takes `LANI_QA_COURSE_SAID` ("teen,ty"): as a step is read, each ending it names is read in the
language learned's voice, never `tts:<base>` (tag IntroVoice; on a dev bridge with no voice engine the explanation goes in
its runs).

**To listen to:** how ElevenLabs says a lone ending ("-ty" could come out as "tie"). Not checked: no credits spent.

### 8. The day's audio and the car on a visit — decided, nothing built

What's right: nothing is got ahead for a visit. A visit is the learner's tap on Friends, never planned; the app can't
know which town or when. Getting another town's lines every day on a guess (say, the last town visited) would fill the
shared audio cap and ask the host's node for lines that are mostly never heard, and a host's clips can only come
through that node. What a visit needs is got as it begins: `VisitAhead` (polish-app) gets each person's greeting and the
first lines of today's guest dialogs, at most 40, and every clip a visit plays stays in the clip cache. The car stays
the home village's: it drills the language of the learner's own village.

What could come later, and is not small: the day's audio could get the other language's due review cards (the words
learned on visits, reviewed at home) through the linked town's clips; the prefetch job only knows the own node's
`/voice/index` today.

## QA

Through `companion/bin/qa-slot --slot 2` (dev bridge 8781), on synthetic learners (`make-learner.ts`, `initLearnerData`), with
`LANI_ENV_FILE=/dev/null`, `LANI_KEYS_FILE=/nonexistent` and Gepard on a dead port: nothing real was read and no credit was
spent. Screenshots in the session's scratchpad, `ux-qa-repair/`.

| Run | Learner | Result |
|---|---|---|
| `--no-town --steps road` (main's build, before) | Ana, sl from en | ❌ "nothing in 🚗 Za pot": `road:mix` 0, quiz 8; 1 min to listen to |
| the same, `LANI_DEV_FAKE_ELEVENLABS=1` (after) | Ana | ✅ 32 of 32 texts voiced first, 6 min to listen to; mix 8, quiz 7, easy 2, story 1, reviews 2, words 4, dialogs 1, stories 1, shadow 2; 9 of 13 sessions played as Android Auto does, the four drills' sessions empty (their clips aren't among the 32) |
| `--no-town --steps coast` | Tobi, a child, it from de, without a village | ✅ on `coast:618113273` (the seed's fault above), ✅ again on `coast:551323397`, Teo's: past Nonna Rosa's talk, its goals in it · de, the village and the town on the coast |
| `--steps friends,visit,visit-home` | Ana, the second town on 8787 | ✅ 11 steps: visit-home on Friends with "Borgo di Prova"; visit-places and visit-sky skip by design (not Carinthia's; by day) |
| `--no-town --steps friuli-school` | Teo, it from de | ✅ A2 open with its unit begun, no "preparing" line; the school's card, A scuola, the tablet's shots |
| `--no-town --steps course-unit`, `LANI_QA_COURSE_UNIT=numbers`, `LANI_QA_COURSE_SAID=teen,ty` | Ana, en from sl, the Lake District | ✅ step 2 `6/19 sl tts:en "-teen"`, step 3 `2/14 sl tts:en "-ty"`: the language learned's voice, not `tts:sl`; both underlined with their 🔈; the unit to its test and print |

## Tests and checks

On the branch with main (aff9fbf5) merged:

- **The Android suite** through `lani-slot.sh`: 3,761 tests in 483 classes, 0 failures, 2 skipped. `IntroSpeechParityTest`
  reads the new fixture case; `IntroSpeechTest` +1 (the rule's edges).
- **The smoke** on ports 9750–9768 (`LANI_SMOKE_DRAMA3_PORT=9768`): 3,472 checks, all passed. New: the three prompt-clash
  checks, the Italian role-plays in voice-build's plan, the fixture's English endings.
- **`tsc`** clean; `bun build --no-bundle` on the changed test files; **`tutor-export --check`**: 89 files match.
- **Python**: `companion/tests` 145 (11 skipped; `test_qa_road` 3 of them), `tests` 251.
- **voice-build, dry runs only**: Teo's and Ana's plans above (`LANI_KEYS_FILE=/nonexistent`, `ELEVENLABS_API_KEY=`), and the
  smoke's own runs against its fake ElevenLabs. Never a real profile's data, never the real API.

## For the native check

Nothing new to check: this round wrote no learner-facing text. The 88 endings are the courses' own (written and checked
with their units).

## What's left

- The prompt-clash baseline's homonyms (well, bark, wax, spring, trunk …) could each get a prompt of its own; content
  agents decide, then `bun test/prompt-clashes.ts --write`.
- Voice clips: Teo's role-plays and scenes newly in the plan (about 43,000 characters), the 88 endings (410): Jan's call.
- The car and the day's audio for another language's due cards (item 8).
