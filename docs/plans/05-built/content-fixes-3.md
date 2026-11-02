# Built: content that was wrong, part 3

*Built 2026-11-02, branch `content-fixes-3`, from content-fixes-2's tip (wave C, last branch). The two findings content-fixes-2
left, with the lead's decisions: two meanings of one spelling become two words for the learner, and the storytellers' other
evening happenings move out of the hours of their stories. Every text written here is machine-written (Claude): the lists for the
voice plan and the native check are below. No spoken form was "corrected", and none was retracted.*

## 1. One spelling, two words

### What was wrong (checked against the code)

Both of content-fixes-2's measurements hold. With the kitchen's `bowl` (skleda) carded, `learnedWords` counted the green's
`lawn-bowl` (krogla) as had, and the other way round, so one of them was never taught. `lookup` kept one entry per lemma and part of
speech, the first pack's, so "Wilf rolls his bowl along the grass." said "skleda". More places compared a card's word than the doc
named; all of them are in the table below.

### The rule

A pack word can say `"homonym": true`. Both of the two say it, in every pack that has the spelling. A marked word:

- is **had only by its own card, or by another card of its spelling that means it**: a meaning in common, in any of the word's
  languages (`meaningParts`, `homonymCard` in packs.ts; `PackSession.meaningParts` and `means` in the app). So praznik-prvi-maj's `mlaj` is
  had through pod-mlajem's (the maypole), and not through nebo's (the new moon); a dictionary card "bowl = skleda" has the kitchen's only;
- gets **an entry per meaning on a tap**: the one the line says, both when it can't.

Every other word goes by its spelling, as it did.

**A simpler rule, looked at and left:** compare meanings for every word, no key. Only about a dozen spellings differ across the curated
packs, but a card's back and a pack's meaning are often worded apart (hvala: "thanks", "thank you"; a tutor's card, a dictionary's
gloss), and each of those would be taught again. The mark costs one key per word and changes nothing for anything unmarked. The
meaning comparison is still what makes the mark work: it keeps one card for one meaning in several packs.

**Nothing about a card a learner has changes:** its id, front, back, history and scheduling stay (the smoke compares the kitchen's card
before and after the green's is learned). No migration. A pack that was complete can show one more word to teach, the second meaning: that
is the point, and it is taught like any other, with nothing special for one learner's data.

### Every place that compares a card's word

| Place | What it does now |
|---|---|
| `learnedWords` (packs.ts): `GET /packs`, `GET /packs/:id`, `POST /packs/:id/learn`, `list_packs` | the rule |
| `POST /words`, `addWord` (features/lexicon.ts): "Add to my words", a word asked for | the rule: the other meaning is added as its own card |
| `lookup` (lexicon.ts): `GET /lookup`, a tap, My words' search | an entry per meaning, `homonym: true`, `known` by that meaning. The line's translation decides (its `en` has a word of the meaning, another ending too: krogla in "kroglo", in any language of the word) or the line is the word's own example; a form that can only be one (sejmu: the noun) is that one; else both. A marked word takes only the dictionary's reading of its own meaning |
| `Dashboard.learnedWords` (app) | a marked word becomes a card beside the other, before the node has it |
| `WordBook.card` (a building's words, I spy) | own card, or one that means it. A scene's thing knows from the bridge (`SceneObject.homonym`, served by `resolveScene`); `forUpgrade` counts two meanings as two words |
| `Words.yours` (the word card's "In your words") | the node's `known` or the meaning's own card, not the lemma |
| My words (`MyWords.of`) | two cards of one spelling that share no meaning are two rows; the same meaning twice is one row, as it was |
| The dialogs (`MyWords.of` in game/DialogWords.kt, `DialogWordsController.things`) | a turn counts for the card its translation says, for neither when it can't tell; a tap turn's thing never counts for a card of its spelling that means something else |
| Reviews and every run's distractors (`ReviewPlanner.plainTask`) | same-front cards were left out already (the rule content-fixes-2 found). Now also a third word with the other meaning's back: "sphere = krogla" would read as right for the kitchen's bowl |
| The car's quiz (`RoadQuiz`) | checked, not changed: one question per front, and a same-front card is no distractor |
| The day's plan, `PackSession.nextWords`, `SceneWords.learned`, DayAudio | checked, not changed: they go by word id and the bridge's `learned` |
| Forms and counting cards (`FormsCard.candidates`, `Counting.candidates`, forms-cards.ts, counting.ts) | checked, not changed: one card per lemma is right for a table of forms, and `WordFormsWire` picks the dictionary's entry by the card's meaning |
| `publish_dialog_variant` (dialog-words.ts) | a lemma on two cards gets a note to declare the card by its id |
| `publish_pack` | its answer names a spelling it finds in another pack with another meaning, not both marked (`unmarkedHomonyms`) |
| `card.py`, `src/db/card.ts` | `overlaps` marks such a pair `two_meanings: true`, an edit doesn't warn of it; lani-fix: never merge those |
| tutor-export | regenerated: guide/fix.md, guide/studio.md |
| update-db (`new_vocabulary`) | checked, not changed: it goes by item id, never by a front |

### Version compatibility

- **An older bridge:** `word` is a plain `z.object`, which strips an unknown key, so a pack with the key validates and is served without
  it. Everything there is as it was.
- **An older app against a new bridge:** its `json` has `ignoreUnknownKeys` (from the repository's first commit, so Teo's 0.2.698 has it),
  so the key, a scene's thing's and a lookup entry's are ignored. The second meaning is offered to teach, because the bridge's `learned`
  leaves it out, and `POST /learn` adds it; the app's own count catches up at the next dashboard. `WordBook` and `Words.yours` still go by
  the spelling: a building doesn't ask for it, and the card says "in your words". My words shows one row. Nothing breaks. Its reviews have
  had the same-front rule from the first commit too.
- **Deploy order:** none is needed for safety: any mix behaves as above. As for all content, the running bridge reads what is merged at once: code first (bridge, restart, app), then the content.

### Which words

Scanned: every curated pack word of each language, spelling compared as `normWord`, each pair with no meaning in common in any language.

| Language | Spelling | The two | Why |
|---|---|---|---|
| en | `bowl` | in-the-kitchen (skleda) · on-the-bowling-green `lawn-bowl` (krogla) | the lead's call |
| en | `round` | in-the-pub (a round of drinks) · project-mestna-ura (okrogel) | the lead's call |
| sl | `sejem` | na-njivi (I sow) · slavje-semenj (a fair) | two lemmas: sêjem, a form of sejati (https://fran.si/iskanje?View=1&Query=sejem) |
| sl | `mlaj` | nebo (the new moon) · pod-mlajem, praznik-prvi-maj (a maypole) | mláj 1 and mláj 2 in SSKJ² (https://fran.si/iskanje?View=1&Query=mlaj) |
| sl | `kolo` | pot-in-promet (a bicycle) · v-kovacnici, projekt-mlin (a wheel) | one headword (koló 3, https://fran.si/iskanje?View=1&Query=kolo), but "Voda vrti kolo" isn't a bicycle |
| it | `pronto` | al-telefono (Hello? on the phone) · in-cucina (ready) | a greeting and an adjective |

The test used: would the other meaning mislead in its line? **Left as one word**, by the same test (`ONE_WORD` in test/smoke/packs.ts, so a
new pair fails the smoke): `pesem` (a song, a poem) and `vročina` (heat, fever): SSKJ² has one headword each and the meaning only extends
(https://fran.si/iskanje?View=1&Query=pesem, https://fran.si/iskanje?View=1&Query=vro%C4%8Dina); `daljnogled`, `smrečica`, `il carbone`: one thing in
other words. A tap on those still gives the first pack's meaning. If Jan wants `pesem` or `vročina` apart, it is one key in each pack.

**The baseline's homonyms aren't this rule.** well, bark, wax, spring and trunk in test/prompt-clashes.json are two *spellings* on one *prompt*
(il baule, il tronco: "trunk"). They were always two cards; what was wrong is that the prompt fits both. So each got a prompt of its own, for
new cards (a card keeps its back). The baseline went from 346 to 339 entries (`--write`):

| Word | Was | Now | Parts it from |
|---|---|---|---|
| il pozzo | well | water well | bene |
| il tronco | trunk | tree trunk | il baule |
| la sorgente | spring (of water) | spring of water | la primavera |
| die Rinde | bark (of a tree) | tree bark | bellen |
| crescere, zunehmen, naraščati | to grow, to wax | to grow (the moon: to wax) | la cera, das Wachs, vosek |

The other 339 are left: mostly synonyms. Some are real homonyms of a base's word that the brief didn't name (raven and stanovanje "flat", težek
and trd "hard", das Fest and fest in English), and each could be parted the same way.

Found on the way: the English supplement's `round`, the preposition, carried the pub's round as its meaning in sl, de and it (a slip of
supplement-coverage). It says okrog, um and intorno a now.

## 2. The storytellers' evening happenings

`when` takes parts of the day from a fixed table (`TimeOfDay.of`: morning 5–10:59, afternoon 11–16:59, evening 17–21:59, night 22–4:59), by the hour
alone, never by the sun or the month. There is no way to name an hour, so "just before the story" can only be the afternoon. The story is on from 17:00
to 23:29 in every month (`when` evening and night, chance 1, cut at `Routine.STORY_UNTIL`). So an afternoon happening can't overlap it in any month.

**Eleven moved, none left**: the brief's ten and one more that a scan found (na-gricih `nevihta`, Janez, afternoon and evening). All are `["afternoon"]`.

| Happening | Text | Notes |
|---|---|---|
| na-vasi `muri` | 9 lines | "Lahko noč" → "Na svidenje"; shares the scene with `klop`, so it first shows on about 28 % of afternoons (was 40 %) |
| pri-ribniku `ribe` | 6 lines, "Dober dan" | the other three cultures fish in the afternoon already; a new afternoon place for Janez |
| v-hisi `fotografija` | none spoken; memories | "Vsak večer" is Ana, long ago: stays |
| v-soli `stara-sola` | 6 lines | his Routine has `school` in the afternoon; Mojca's lesson holds him back while it runs |
| in-piazza `moro` | 7 changed, 5 new clips | |
| in-piazza `sagra` (6–8, weekends) | 3 lines | the painter draws band and dancers, no lanterns or dusk |
| in-salotto `foto` | none | |
| auf-dem-dorfplatz `musik` (6–8, weekends) | none | `beim-kirchtag` has music after Mass anyway |
| in-der-stube `foto` | none | |
| in-the-living-room `photo` | 1 line; memories | "put the lamp on" → "put the kettle on" |
| na-gricih `nevihta` | none | dropped its evening |

The school then had only two parts of the day (the smoke's "varied times" asks for three): Zala's `globus` is on in the evening as well as the morning. Its
lines name no time, nothing changed in them.

A new test holds the rule: `SceneCoherenceTest`, "no other happening of a storyteller shares a part of the day with his story, in any culture" (all-day counts
as every part, dawn as morning, `with` too). With `nevihta`'s evening back it fails on that. A tutor-published scene isn't checked by it.

**December:** dark falls at about 16:52, before the story. `Routine.now` checks `busy` first, so with a bubble on he stands there with a lantern. In every other month
dark falls after 17:00.

**Left, the lead's call:**
- **He can be in two places by day.** `Happenings.active` keeps one happening per person per scene, not overall. With every chance counted in a fully built village, the
  probability of two or more scenes in one afternoon goes 0.92 → 0.98 (Primorska), 0.91 → 0.97 (Friuli), 0.87 → 0.95 (Kärnten), 0.97 → 0.98 (Lakes). Only Primorska gains new
  afternoon scenes (6 → 9). The lead added that rule on this branch: `Happenings.active` now keeps one happening per person in the whole village (a villager by id, a stranger of a scene in that scene), so the numbers above are of the moves alone.
- **Evening-only players** lose these: other evening happenings per village go 45 → 40 (+1, `globus`), 36 → 33, 41 → 39, 36 → 35.
- **A dog twice:** "have you seen my dog?" (`na-vasi/muri`, `in-piazza/moro`) can fall on the afternoon of Zala's `v-hisi/muri` or Giulia's `in-salotto/moro`. The same as Wilf's and
  Poppy's `bess` since 8e8b5b09.
- A true dusk hour for the sagra would need an hours field in the app.

### An older chronicle

**It keeps the old text.** `GameState.log` (game/Model.kt) stores `LogEntry(at, emoji, text)`; a happening's entry is "person: title" (app/SceneController.kt), so a memory was never in the
chronicle. A villager's memories are `Bond.memories` (game/villagers/Bonds.kt), `Memory(on, sl, en, kind)`: the text is copied when the dialog is done (app/VillagerController.kt) and
rendered from the stored strings (ui/villagers/VillagerScreens.kt). No id links a memory to its content. So an older save goes on saying "the evening Bess came to you", and a new play adds
"the day Bess came to you". The same for every memory changed here.

## How to see it

- **Bowl:** a Lake District village (make-learner.ts --target en --base sl, LANI_CULTURE=lakeland). "New words": In the kitchen, then On the bowling green, each teaches its bowl. "📚 My words": a
  search for "bowl" has two rows. A tap on "bowls" in Mr Tyson's line on the green (QA's hook "happening:on-the-bowling-green/measure/measure") says krogla.
- **Moved:** Old Wilf's photo, a house at level 3, afternoons; or QA's hooks "hour:15", "happening:in-the-living-room/photo/photo".
- QA's step `content-fixes-3` (skipped outside the Lake District) drives all of it, shots cf3-*.png.

No tap counts change: the same screens and buttons. A pack shows one more word where a second meaning was counted as had.

## Order of going out

Code, then content, as always: the bridge (restart) and the app, then the content commits (499a0125, 675a458d, a1dc74bd, 83d0ebff). Any mix is safe (see "Version compatibility"). A changed line
plays without its clip until Jan spends credits on it. The bridge reads merged content at once, so the moved happenings and their new texts are live as soon as they are on main.

## Tests

On the branch's tip (content-fixes-2 hasn't moved, 396a2ff4 is in it):

- the bridge's `bunx tsc --noEmit -p .`: clean; `bun build --no-bundle` on the changed test files; `tutor-export --check`: 89 files match; `check-culture` on all four cultures: ok; `check-steps`: primorska,
  kaernten and lakeland 13 files and 78 steps each, friuli has none;
- the smoke (`LANI_SMOKE_PORT=9730 LANI_SMOKE_FRIULI_PORT=9733 LANI_SMOKE_TOWN_PORT=9736 LANI_SMOKE_DRAMA3_PORT=9748`): all 3,508 checks passed, among them the new homonym and bowl checks;
- the Android suite in full: 3,784 tests in 485 classes, none failed, among them HomonymTest (10) and the new SceneCoherenceTest check;
- Python: `tests` 253 OK, `companion/tests` 145 OK (11 skipped), among them card.py's two_meanings.

**A test that was red on content-fixes-2's tip:** DialogReactionsTest asked two right choices for replies of their own, and the three spoken-hour turns of bfebf83a share one on purpose. The test allows that
case now (83d0ebff). Without it the lead's gate fails on content-fixes-2 too, so that hash may be wanted there.

**Emulator QA** (qa-slot's slot 1, a synthetic learner of English from Slovene in a Lake District village): QA's step `content-fixes-3`, green. Shots in the session's scratchpad, `ux-content-fixes-3/`:

- `cf3-taught-kitchen`, `cf3-taught-green`: each pack's word card, bowl, skleda and bowl, krogla, the second while the first is already a card;
- `cf3-my-words`: a search for "bowl" has two rows;
- `cf3-tap-green`: a tap on "bowls" in Mr Tyson's line says krogla alone; `cf3-tap-kitchen`: the card in a kitchen's line (QA's hook `word:`; no dialog line says the kitchen's bowl) skleda alone; `cf3-tap-both`: a line that can't tell, both;
- `cf3-photo-by-day`: Old Wilf's photo at three in the afternoon, with QA's hooks.

**Not on an emulator:** the pack runs weren't answered to their end (QA learns through the bridge after the word's card; the smoke and HomonymTest cover the rest); no review of the two cards; `round`, `sejem`, `mlaj`,
`kolo` and `pronto` beyond the smoke and a script over the real packs; the ten other moved happenings (their hours hold by SceneCoherenceTest, their lines by the smoke's scene checks), and the photo was forced by the hook, not met by chance.

## What's left

- **Evening-only players and a dog twice** (section 2). The storyteller in two places by day is fixed by the per-person rule.
- **Real homonyms of a base's word still in the baseline** (raven and stanovanje "flat", težek and trd "hard", das Fest and fest, gell and rechts "right"): a prompt of its own each, the same way.
- A tap on `pesem`, `vročina` and the other one-word pairs gives the first pack's meaning. Listing both glosses on one entry would fix it without a card; not done, it changes what a lookup says for every word.
- The word card labels a pack word without a dictionary's reading "word" (from before this branch; the sentence sheet hides it). `round` and `bowl` show it: English has no dictionary.
- A tap on a review's flashcard has no line to go by, so it shows both meanings.
- `publish_pack` tells the tutor, it doesn't refuse: a tutor's pack can't mark a curated word.
- A tutor-published scene isn't held to the storyteller's rule.

## For the voice plan (not voiced here; Jan's call)

Section 1 changes nothing spoken: the mark is a key, and a word's translations in another language have no clips. Section 2 changes 34 spoken lines, each in its scene's language: 21 Slovene,
12 Italian, 1 English. 32 need a clip; the Italian "Buonanotte, nonno! Buonanotte, Moro!" and "Buongiorno, nonno! Posso dare un po' di pane a Moro?" are texts with a clip already. Read from the JSON
diff against 396a2ff4 (a `why` isn't voiced; a memory is voiced live in `remember` lines). "learner": the default voice and any "my lines" voice; "reply": the person answering.

| Scene | Dialog | Who | Was | Now |
|---|---|---|---|---|
| in-piazza | moro | bepi | Grazie, {learner}. Moro, a casa! Buonanotte. | Grazie, {learner}. Moro, a casa! Ciao. |
| in-piazza | moro | learner | Buonanotte, nonno! Buonanotte, Moro! | Ciao, nonno! Ciao, Moro! |
| in-piazza | moro | learner | Buongiorno, nonno! Buongiorno, Moro! | Buonanotte, nonno! Buonanotte, Moro! |
| in-piazza | moro | reply | Buongiorno? Ma è sera, il sole va a dormire! | Buonanotte? Ma è giorno, nessuno va a dormire! |
| in-piazza | sagra | bepi | Buonasera, {learner}! Senti la banda? Stasera c'è la sagra in piazza! | Ciao, {learner}! Senti la banda? Oggi c'è la sagra in piazza! |
| in-piazza | sagra | bepi | Ah, che serata! Da giovane ballavo tutta la notte, qui sotto il tiglio. | Ah, che festa! Da giovane ballavo tutta la notte, qui sotto il tiglio. |
| in-piazza | moro-pane | bepi | Buonasera, {learner}. Moro ha fame. Ha corso dietro ai gatti tutto il giorno. | Buongiorno, {learner}. Moro ha fame. Corre dietro ai gatti da stamattina. |
| in-piazza | moro-pane | learner | Buonasera, nonno! Posso dare un po' di pane a Moro? | Buongiorno, nonno! Posso dare un po' di pane a Moro? |
| in-piazza | moro-pane | learner | Buongiorno, nonno! Posso dare un po' di pane a Moro? | Buonanotte, nonno! Posso dare un po' di pane a Moro? |
| in-piazza | moro-pane | reply | Buongiorno? Anche Moro sa che è sera! | Buonanotte? Anche Moro sa che è giorno! |
| in-piazza | moro-pane | bepi | Su, Moro, a casa adesso. Buonanotte, {learner}. | Su, Moro, a casa adesso. Ciao, {learner}. |
| in-piazza | sagra-tromba | bepi | Buonasera, {learner}! Ecco la banda del paese! Senti la tromba? | Ciao, {learner}! Ecco la banda del paese! Senti la tromba? |
| in-the-living-room | photo | wilf | Now put the lamp on, {m:son\|f:lass}. And sit with me a bit. | Now put the kettle on, {m:son\|f:lass}. And sit with me a bit. |
| na-vasi | muri | reply | Popoldne. Vedno gre k mesarju. | Zjutraj. Vedno gre k mesarju. |
| na-vasi | muri | janez | Hvala, {learner}. Muri, domov! Lahko noč. | Hvala, {learner}. Muri, domov! Na svidenje. |
| na-vasi | muri | learner | Lahko noč, Janez. Lahko noč, Muri! | Na svidenje, Janez. Adijo, Muri! |
| na-vasi | muri | learner | Lahko noč, Janez. Lahko noč, Muriju! | Na svidenje, Janez. Adijo, Muriju! |
| na-vasi | muri-kruh | janez | Dober večer, {learner}. Muri je lačen. Ves dan je tekel za mačkami. | Dober dan, {learner}. Muri je lačen. Že od jutra teče za mačkami. |
| na-vasi | muri-kruh | learner | Dober večer! Lahko dam Muri kos kruha? | Dober dan! Lahko dam Muri kos kruha? |
| na-vasi | muri-kruh | learner | Dober večer! Lahko dam Muriju kos kruha? | Dober dan! Lahko dam Muriju kos kruha? |
| na-vasi | muri-kruh | reply | Jutro? {m:Fant\|f:Dekle}, še Muri ve, da gre dan spat. | Jutro? {m:Fant\|f:Dekle}, še Muri ve, da je že dan. |
| na-vasi | muri-kruh | janez | No, zdaj pa domov, Muri. Lahko noč, {learner}. | No, zdaj pa domov, Muri. Na svidenje, {learner}. |
| pri-ribniku | ribe | janez | Dober večer, {learner}. Sedi. Zvečer so ribe lačne. | Dober dan, {learner}. Sedi. Danes so ribe lačne. |
| pri-ribniku | ribe | learner | Dober večer. Ste že kaj ujeli? | Dober dan. Ste že kaj ujeli? |
| pri-ribniku | ribe | learner | Dober večer. Si že kaj ujel? | Dober dan. Si že kaj ujel? |
| pri-ribniku | ribe | learner | Dober večer. Ste že kaj ujel? | Dober dan. Ste že kaj ujel? |
| pri-ribniku | ribe-stejem | janez | Dober večer, {learner}. Danes so prijele. Poglej v vedro in preštej. | Dober dan, {learner}. Danes so prijele. Poglej v vedro in preštej. |
| pri-ribniku | ribe-scuka | janez | Dober večer, {learner}. Sedi. … Veš, nekoč sem tu ujel ščuko. Tako veliko! | Dober dan, {learner}. Sedi. … Veš, nekoč sem tu ujel ščuko. Tako veliko! |
| v-soli | stara-sola | janez | Zdaj tu uči Mojca. Dobra je. Brez palice. Pojdiva, pozno je. | Zdaj tu uči Mojca. Dobra je. Brez palice. Pojdiva, Muri čaka. |
| v-soli | stara-sola | learner | Greva. Lahko noč, Janez. Hvala za zgodbo. | Greva. Na svidenje, Janez. Hvala za zgodbo. |
| v-soli | stara-sola | reply | Lahko noč. Muri, domov! | Na svidenje. Muri, domov! |
| v-soli | stara-sola | learner | Greva. Dober večer, Janez. Hvala za zgodbo. | Greva. Dober dan, Janez. Hvala za zgodbo. |
| v-soli | stara-sola | reply | Dober večer? Ha. Prihajaš ali odhajaš? | Dober dan? Ha. Prihajaš ali odhajaš? |
| v-soli | stara-sola-pec | janez | Časi se spreminjajo, {m:sinko\|f:{learner}}. Pojdi domov, pozno je. | Časi se spreminjajo, {m:sinko\|f:{learner}}. Pojdi domov, jaz še malo ostanem. |

## Native check

Everything below is machine-written. A spoken form is never wrong: these are questions, not corrections.

**Section 1** (none of it is spoken):
- Slovene: `okrog` for the English supplement's preposition round. Settled, nothing to check: mlaj is two headwords, kolo, pesem and vročina one each (the URLs above).
- German: `um, um … herum` for round. Italian: `intorno a` for round.
- English prompts: water well, tree trunk, spring of water, tree bark, to grow (the moon: to wax). Does "water well" read naturally for il pozzo?
- Is `mlaj` right as two cards, and `kolo`? And are `pesem` and `vročina` right as one? (The test: would the other meaning mislead in its line?)

**Section 2**, settled with one request each: https://fran.si/iskanje?View=1&Query=pristaviti ("pristaviti lonec z vodo", elliptical "pristaviti za kavo"; "pristavi vodo za čaj" is machine-written, the register is the native speaker's call) and
https://fran.si/iskanje?View=1&Query=adijo (an interjection, no register label). Buongiorno up to 16:59, as Bepi's own file already has it. Every text changed, by language (the scene's own language is the line said; the others its translations):


**Slovene**

- in-piazza: «dan, ko si {m:poklical|f:poklicala} Mora» · «Hvala, {learner}. Moro, domov! Adijo.» · «Adijo, dedek! Adijo, Moro!» · «Lahko noč, dedek! Lahko noč, Moro!» · «Buonanotte je za pred spanjem. Ko greš čez dan: ciao.» · «Lahko noč? Saj je dan, nihče ne gre spat!» · «Živjo, {learner}! Slišiš godbo? Danes je na trgu vaški praznik!» · «Ah, kakšen praznik! V mladosti sem plesal vso noč, tu pod lipo.» · «Dober dan, {learner}. Moro je lačen. Že od jutra teče za mačkami.» · «Dober dan, dedek! Lahko dam Moru malo kruha?» · «Lahko noč, dedek! Lahko dam Moru malo kruha?» · «Je dan: buongiorno. Buonanotte je za pred spanjem.» · «Lahko noč? Še Moro ve, da je dan!» · «No, Moro, zdaj pa domov. Adijo, {learner}.» · «Živjo, {learner}! Tu je vaška godba! Slišiš trobento?»
- in-the-living-room: «dan, ko sem ti pokazal svojo poročno fotografijo» · «Zdaj pa pristavi vodo za čaj. In malo posedi z mano.» · «dan, ko si {m:našel|f:našla} moja očala»
- na-vasi: «dan, ko si {m:poklical|f:poklicala} Murija» · «Zjutraj. Vedno gre k mesarju.» · «Hvala, {learner}. Muri, domov! Na svidenje.» · «Na svidenje, Janez. Adijo, Muri!» · «Na svidenje, Janez. Adijo, Muriju!» · «Dober dan, {learner}. Muri je lačen. Že od jutra teče za mačkami.» · «Dober dan! Lahko dam Muri kos kruha?» · «Dober dan! Lahko dam Muriju kos kruha?» · «Jutro? {m:Fant|f:Dekle}, še Muri ve, da je že dan.» · «No, zdaj pa domov, Muri. Na svidenje, {learner}.»
- pri-ribniku: «dan, ko sva lovila ribe v dežju» · «Dober dan, {learner}. Sedi. Danes so ribe lačne.» · «Dober dan. Ste že kaj ujeli?» · «Dober dan. Si že kaj ujel?» · «Dober dan. Ste že kaj ujel?» · «dan, ko sva štela ribe v vedru» · «Dober dan, {learner}. Danes so prijele. Poglej v vedro in preštej.» · «Dober dan, {learner}. Sedi. … Veš, nekoč sem tu ujel ščuko. Tako veliko!»
- v-hisi: «dan, ko sem ti pokazal Ano» · «dan, ko sva gledala fotografijo s Triglava»
- v-soli: «dan v stari šoli, ko sem ti pravil o gospe Pavli» · «Zdaj tu uči Mojca. Dobra je. Brez palice. Pojdiva, Muri čaka.» · «Greva. Na svidenje, Janez. Hvala za zgodbo.» · «Na svidenje. Muri, domov!» · «Greva. Dober dan, Janez. Hvala za zgodbo.» · «Dober dan? Ha. Prihajaš ali odhajaš?» · «dan v stari šoli, ko sem ti pravil o polenih za peč» · «Časi se spreminjajo, {m:sinko|f:{learner}}. Pojdi domov, jaz še malo ostanem.»

**Italian**

- in-piazza: «il giorno in cui hai chiamato Moro» · «Grazie, {learner}. Moro, a casa! Ciao.» · «Ciao, nonno! Ciao, Moro!» · «Buonanotte, nonno! Buonanotte, Moro!» · «Buonanotte? Ma è giorno, nessuno va a dormire!» · «Ciao, {learner}! Senti la banda? Oggi c'è la sagra in piazza!» · «Ah, che festa! Da giovane ballavo tutta la notte, qui sotto il tiglio.» · «Buongiorno, {learner}. Moro ha fame. Corre dietro ai gatti da stamattina.» · «Buongiorno, nonno! Posso dare un po' di pane a Moro?» · «Buonanotte, nonno! Posso dare un po' di pane a Moro?» · «Buonanotte? Anche Moro sa che è giorno!» · «Su, Moro, a casa adesso. Ciao, {learner}.» · «Ciao, {learner}! Ecco la banda del paese! Senti la tromba?»
- in-the-living-room: «il giorno in cui ti ho mostrato la foto del mio matrimonio» · «Adesso metti su il bollitore, {m:figliolo|f:cara}. E stai un po' con me.» · «il giorno in cui hai trovato i miei occhiali»
- na-vasi: «il giorno in cui hai chiamato Muri» · «Stamattina. Va sempre dal macellaio.» · «Grazie, {learner}. Muri, a casa! Arrivederci.» · «Arrivederci, Janez. Ciao, Muri!» · «Lahko noč si dice prima di andare a dormire, e l'espressione ha noč: noči è il plurale o il genitivo. Di giorno ci si saluta con na svidenje.» · «Arrivederci, Janez. Ciao, a-Muri!» · «Buongiorno, {learner}. Muri ha fame. Rincorre i gatti da stamattina.» · «Buongiorno! Posso dare Muri (nominativo) un pezzo di pane?» · «Buongiorno! Posso dare a Muri un pezzo di pane?» · «Buon mattino! Posso dare a Muri un pezzo di pane?» · «È già giorno: dober dan. Dobro jutro è per la mattina.» · «Jutro (mattino)? {m:Ragazzo|f:Ragazza}, perfino Muri sa che è già giorno.» · «Bene, ora a casa, Muri. Arrivederci, {learner}.»
- pri-ribniku: «il giorno in cui abbiamo pescato sotto la pioggia» · «Buongiorno, {learner}. Siediti. Oggi i pesci hanno fame.» · «Buongiorno. Ha già preso qualcosa?» · «Buongiorno. Hai (ti) già preso qualcosa?» · «il giorno in cui abbiamo contato i pesci nel secchio» · «Buongiorno, {learner}. Oggi abboccavano. Guarda nel secchio e conta.» · «Buongiorno, {learner}. Siediti. … Sai, una volta qui ho preso un luccio. Grande così!»
- v-hisi: «il giorno in cui ti ho mostrato Ana» · «il giorno in cui abbiamo guardato la foto sul Triglav»
- v-soli: «il giorno nella vecchia scuola, quando ti ho raccontato della signora Pavla» · «Adesso qui insegna Mojca. È brava. Senza bacchetta. Andiamo, noi due, Muri aspetta.» · «Andiamo (noi due). Arrivederci, Janez. Grazie per la storia.» · «Arrivederci. Muri, a casa!» · «Andiamo (noi due). Buongiorno, Janez. Grazie per la storia.» · «Dober dan è il saluto quando arrivi. Per congedarsi: na svidenje.» · «Dober dan (buongiorno)? Ah. Arrivi o te ne vai?» · «il giorno nella vecchia scuola, quando ti ho raccontato dei ceppi per la stufa» · «I tempi cambiano, {m:figliolo|f:{learner}}. Va' a casa, io resto ancora un po'.»

**German**

- in-piazza: «den Tag, an dem du Moro gerufen hast» · «Danke, {learner}. Moro, nach Hause! Tschüss.» · «Tschüss, Nonno! Tschüss, Moro!» · «Gute Nacht, Nonno! Gute Nacht, Moro!» · «Buonanotte sagt man vor dem Schlafengehen. Wenn du tagsüber gehst: ciao.» · «Gute Nacht? Es ist doch Tag, niemand geht schlafen!» · «Hallo, {learner}! Hörst du die Blaskapelle? Heute ist Sagra auf dem Platz!» · «Ach, was für ein Fest! Früher habe ich die ganze Nacht getanzt, hier unter der Linde.» · «Guten Tag, {learner}. Moro hat Hunger. Seit heute Morgen läuft er Katzen nach.» · «Guten Tag, Nonno! Darf ich Moro ein bisschen Brot geben?» · «Gute Nacht, Nonno! Darf ich Moro ein bisschen Brot geben?» · «Es ist Tag: buongiorno. Buonanotte sagt man vor dem Schlafengehen.» · «Gute Nacht? Sogar Moro weiß, dass es Tag ist!» · «Komm, Moro, jetzt nach Hause. Tschüss, {learner}.» · «Hallo, {learner}! Da ist die Dorfkapelle! Hörst du die Trompete?»
- in-the-living-room: «den Tag, an dem ich dir mein Hochzeitsfoto gezeigt habe» · «Jetzt stell den Teekessel auf, {m:mein Junge|f:meine Liebe}. Und setz dich ein bisschen zu mir.» · «den Tag, an dem du meine Brille gefunden hast»
- na-vasi: «den Tag, an dem du Muri gerufen hast» · «Heute Morgen. Er geht immer zum Metzger.» · «Danke, {learner}. Muri, nach Hause! Auf Wiedersehen.» · «Auf Wiedersehen, Janez. Tschüs, Muri!» · «Lahko noč sagt man vor dem Schlafengehen, und die Wendung hat noč: noči ist der Plural oder der Genitiv. Am Tag verabschiedest du dich mit na svidenje.» · «Auf Wiedersehen, Janez. Tschüs, dem-Muri!» · «Guten Tag, {learner}. Muri hat Hunger. Seit heute Morgen läuft er Katzen nach.» · «Guten Tag! Darf ich Muri (Nominativ) ein Stück Brot geben?» · «Guten Tag! Darf ich Muri ein Stück Brot geben?» · «Es ist schon Tag: dober dan. Dobro jutro sagt man am Morgen.» · «Jutro (Morgen)? {m:Junge|f:Mädchen}, sogar Muri weiß, dass es schon Tag ist.» · «So, jetzt nach Hause, Muri. Auf Wiedersehen, {learner}.»
- pri-ribniku: «den Tag, an dem wir im Regen geangelt haben» · «Guten Tag, {learner}. Setz dich. Heute haben die Fische Hunger.» · «Guten Tag. Haben Sie schon etwas gefangen?» · «Guten Tag. Hast du (ti) schon etwas gefangen?» · «den Tag, an dem wir die Fische im Eimer gezählt haben» · «Guten Tag, {learner}. Heute haben sie gebissen. Schau in den Eimer und zähl.» · «Guten Tag, {learner}. Setz dich. … Weißt du, einmal habe ich hier einen Hecht gefangen. So groß!»
- v-hisi: «den Tag, an dem ich dir Ana gezeigt habe» · «den Tag, an dem wir das Foto vom Triglav angeschaut haben»
- v-soli: «den Tag in der alten Schule, als ich dir von Frau Pavla erzählt habe» · «Jetzt unterrichtet hier Mojca. Sie ist gut. Ohne Stock. Gehen wir zwei, Muri wartet.» · «Gehen wir (wir zwei). Auf Wiedersehen, Janez. Danke für die Geschichte.» · «Auf Wiedersehen. Muri, nach Hause!» · «Gehen wir (wir zwei). Guten Tag, Janez. Danke für die Geschichte.» · «Dober dan ist der Gruß, wenn du ankommst. Zum Abschied: na svidenje.» · «Dober dan (guten Tag)? Ha. Kommst du oder gehst du?» · «den Tag in der alten Schule, als ich dir von den Holzscheiten für den Ofen erzählt habe» · «Die Zeiten ändern sich, {m:mein Junge|f:{learner}}. Geh nach Hause, ich bleibe noch ein bisschen.»

**English**

- in-piazza: «the day you called Moro» · «Thank you, {learner}. Moro, home! Bye.» · «Bye, Nonno! Bye, Moro!» · «Good night, Nonno! Good night, Moro!» · «Buonanotte is for bedtime. When you go in the day: ciao.» · «Good night? But it's daytime, nobody's going to bed!» · «Hi, {learner}! Hear the band? Today there's the sagra on the square!» · «Ah, what a party! When I was young I danced all night, here under the linden.» · «Good day, {learner}. Moro is hungry. He's been chasing cats since this morning.» · «Good day, Nonno! Can I give Moro a bit of bread?» · «Good night, Nonno! Can I give Moro a bit of bread?» · «It's daytime: buongiorno. Buonanotte is for bedtime.» · «Good night? Even Moro knows it's daytime!» · «Come on, Moro, home now. Bye, {learner}.» · «Hi, {learner}! Here's the village band! Hear the trumpet?»
- in-the-living-room: «the day I showed you my wedding photo» · «Now put the kettle on, {m:son|f:lass}. And sit with me a bit.» · «the day you found my glasses»
- na-vasi: «the day you called Muri» · «This morning. He always goes to the butcher's.» · «Thanks, {learner}. Muri, home! Goodbye.» · «Goodbye, Janez. Bye, Muri!» · «Lahko noč is for bedtime, and the phrase has noč: noči is the plural or genitive. By day you part with na svidenje.» · «Goodbye, Janez. Bye, to-Muri!» · «Good day, {learner}. Muri is hungry. He's been chasing cats since this morning.» · «Good day! Can I give Muri (nominative) a piece of bread?» · «Good day! Can I give Muri a piece of bread?» · «It is day now: dober dan. Dobro jutro is for the morning.» · «Jutro (morning)? {m:Lad|f:Lass}, even Muri knows it's day by now.» · «Well, home now, Muri. Goodbye, {learner}.»
- pri-ribniku: «the day we fished in the rain» · «Good day, {learner}. Sit down. Today the fish are hungry.» · «Good day. Have you caught anything yet?» · «Good day. Have you (ti) caught anything yet?» · «the day we counted the fish in the bucket» · «Good day, {learner}. They were biting today. Look in the bucket and count.» · «Good day, {learner}. Sit down. … You know, once I caught a pike here. This big!»
- v-hisi: «the day I showed you Ana» · «the day we looked at the photo from Triglav»
- v-soli: «the day in the old school when I told you about Mrs Pavla» · «Now Mojca teaches here. She is good. Without a stick. Let us two go, Muri is waiting.» · «Let us go (the two of us). Goodbye, Janez. Thanks for the story.» · «Goodbye. Muri, home!» · «Let us go (the two of us). Good day, Janez. Thanks for the story.» · «Dober dan is the greeting when you arrive. When you part: na svidenje.» · «Dober dan (good day)? Ha. Are you coming or going?» · «the day in the old school when I told you about the logs for the stove» · «Times change, {m:son|f:{learner}}. Go home, I'll stay a little longer.»
