# Built: the English course, B1 part c (2026-11-03)

*Wave D (followups-12.md; Jan, 2026-11-02: "yes complete"). Branch `course-en-b1-c`, from the integrated English A2 (cf7d5083). Everything written in a
language is machine-written (Claude) and waits for a native check. This doc holds the six units; the integration of parts a and b comes when the lead asks for it.*

## Native check

Machine-written, not reviewed by a native speaker. British English first, then each base. The whole of the six new pages and their cards and tips, and these first:

**What people say: please say which are really said, and where**
- `spoken` on the item (right, labelled with the standard form): If I was you and If it wasn't raining beside were; woulda come, coulda helped, knew in "if I ___ (know), I'd have told you"; so beside so that, cos of beside because of; the tense kept (She said she is tired, Jack said he'll come, He asked where I live).
- `accept` (right, no label): It must not be Jack, It mustn't be Jack and It won't be Jack beside It can't be Jack (American in writing too; Scotland, Ireland and the North, by the unit's words; **please check that last claim**); It'll be Sam as a guess; Probably he'll come, He won't probably come.
- `nonstandard` in grading/en.json (almost, the standard form shown): If I would have known, If I'd have known, If I would of known; If I would have a boat, If it would rain, If I wouldn't have the sheep; would of, could of, should of, might of, must of; He asked where did I live, He asked did I want tea, Do you know where's the inn?, Could you tell me where does the bus stop? (Ireland, Scotland, the North, by the page's words. **Is "the North" fair, and is "you often hear" fair of where's?**)
- **Left wrong, for you to say:** If I know the way, I'd tell you and If I have time, I'd help (a tense mixed: never offered, because people do say the second; typed in a gap it is wrong, since the gap is the unit's point); said me, told to me, asked to me; despite of; Although it rained, but we walked; would be come; He asked where lived I.

**British English**
- if-i-had: "row across"; "If Jack lived nearer"; "the raffle"; "Sam sold the van"; "any sense"; "If there were a bridge"; "Meg were younger".
- might-must: "It has to be Jack" as talk; "That'll be Sam" accepted, not taught; "Maybe he's ill"; "He won't probably come" accepted; "It can rain here for days" against "It might rain"; "It's been raining all day" in a gap text a level past its unit (present-perfect-continuous is part a's).
- if-i-had-known: "got there in time" with gotten accepted; "If I'd come earlier" read as had; "Id known" as almost; the rule "first 'd had, second would".
- although-however: "Though it was cold"; "We walked, though."; "However" with its comma; "so as to"; "owing to", "due to", "on account of", "by" in one gap; "Despite feeling ill"; even though as "stronger".
- reported-speech: "the next day", "the following day", "the day after", "on the Tuesday"; "at that time", "at the time"; "the safe choice in writing"; "He said to wait there" accepted beside "He told me to wait there"; "told me to not" as spoken.
- reported-questions: "He asked what I had seen" with "what I saw" not listed in the intro; "I wonder what she wants"; "how much it cost" taking costs and would cost.

**Slovene**
- if-i-had: "Če bi {m:zadel|f:zadela} na srečelovu"; "Na tvojem mestu bi {m:počakal|f:počakala} avtobus"; "kombi" for van; "Tisti, ki govori, izbere".
- might-must: "To je lahko samo Jack"; "Poglej tiste oblake"; "premražen".
- if-i-had-known: "Če bi vedel" both for now and for then, "takrat" added in the examples; "Povedal bi ti" for I woulda told you; "Zamudil bi".
- although-however: "*čeprav …, pa …*"; "Pa smo vseeno šli"; "Posledično"; "da bi" for in order to and so that.
- reported-speech: "Rekla mi je, naj počakam"; "ne bom črhnil niti besede"; present kept in the translations, where Slovene has no step back.
- reported-questions: "ali" against *če* for if; "Zanima me, kaj želi"; "od kod sem".

**German** (Austrian where it differs)
- if-i-had: the Konjunktiv in the translations ("Wenn Jack näher wohnte, käme er öfter"; "wenn du bei der Tombola gewinnen würdest"); "am Berg", "Lieferwagen".
- might-must: "Das muss Jack sein"; the warning that *muss nicht* is might not; "Du musst müde sein".
- if-i-had-known: "erwischt" for caught; "wäre gekommen" against have, the trap the unit names.
- although-however: "Obwohl Markttag war"; "heim"; "infolgedessen".
- reported-speech: Konjunktiv I in every translation ("Sie sagte, sie sei müde"), and whether it reads stiffly for a learner.
- reported-questions: "ob" for if; "Sie fragte, ob ich Tee wolle".

**Italian**
- if-i-had: "remerei fino all'altra riva"; "lotteria" for raffle; "furgone"; "Se fossi in te".
- might-must: "Dev'essere Jack"; "Non può che essere Jack"; "può piovere" as the trap.
- if-i-had-known: "sarei venut{m:o|f:a}" against have; "Se fossi stat{m:o|f:a} Jack".
- although-however: "Anche se pioveva, siamo andati" (imperfetto); "per la neve".
- reported-speech: the step back in Italian too ("Ha detto che era stanca"), "avrebbe venduto".
- reported-questions: "se" for if; "Ha chiesto dove abitavo".

**The supplement** (lexicon/en.extra.json): would have, could have, might have with would've, woulda; although, even though, despite, in spite of, however, in order to, so that, so as to, because of, as a result, due to, owing to, on account of, therefore, consequently, nevertheless; that day, the next day, the day before, the week after; must not; come from; sense, earn, previous, fallen.

**A disagreement with the outline.** The outline's "never would after if" is now "writing keeps would to the other half", since people do say it (the rule on what people say).

## What was built

### The six units (companion/course/en/units)

At the level's size: six intro steps with a quick question each, three worksheets, a test (pass 80). Set in the village's grown-up life: Old Wilf by the fire and what he would buy, a wet night at the inn and who is at the door, the dance Sam's van missed, Jack in the storm, Edna's news of Sam, a stranger at the inn.

| # | Unit | Teaches | Pages | Sheets' items | Test items |
|---|---|---|---|---:|---:|
| 13 | `if-i-had` 🚣 | if + the past, would; could, might; the halves either way; What would you do if …?; If I were you, was spoken; would kept to the other half in writing; against the first conditional | **second-conditional** (new), first-conditional | 32 | 12 |
| 14 | `might-must` 🤔 | may, might, could; might not; can for what sometimes happens; must and can't for what you work out, must not American and northern; be + -ing; probably, perhaps, maybe | **might-must** (new), must-have-to | 31 | 13 |
| 15 | `if-i-had-known` 🕰️ | if + had, would have; the two 'd; hadn't, wouldn't have; could have, might have; would've, woulda, would of, If I'd have known; now against then; have, never be | **third-conditional** (new), second-conditional | 31 | 12 |
| 16 | `although-however` ⚖️ | although, though, even though; one joining word; despite, in spite of; however, though at the end; to, in order to, so that, so spoken; because of; as a result | **contrast-linkers** (new), linking-words | 29 | 13 |
| 17 | `reported-speech` 💬 | said and told; one step back; the tense kept if it's still so; person, place, day; told me to, told me not to, asked me to | **reported-speech** (new), past-simple | 34 | 12 |
| 18 | `reported-questions` ❓ | asked where + a statement's order; asked if, whether; the step back; in the present, Do you know where …?, I don't know if …; what's said in Ireland, Scotland and the North; who as the subject; asked me | **reported-questions** (new), reported-speech, questions-do | 31 | 12 |

Pages named by the base rather than left for "once merged": first-conditional, must-have-to, linking-words are A2's and exist. **Once merged, also:** `past-perfect` (part a's) on `if-i-had-known`, in its unit and its outline line.

### What the units assume from parts a and b

Only what comes before in the outline, and no page of theirs: the past perfect in `if-i-had-known` and `reported-speech`; have been + -ing once, in might-must's gap text ("It's been raining all day"); nothing of b's.

### The six new pages (companion/grammar/en)

Each B1, in the format of A2's `first-conditional`, each with a hand-written card (title, rule, two examples, tip per base), the tip differing by base where the trap does: *wenn ich haben würde*, Slovene's *bi* in both halves; *muss nicht*, *ne more biti*, *può piovere*; *wäre*, *sarei*, *če bi vedel* for now and then; *čeprav …, pa …*, *wegen* and *weil*. Every rule under 1,200 characters (at most 1,075).

### The grading (grading/en.json), appended; `EnglishB1cContentTest` holds each

- `same`: **'d** is had before a participle (a list of irregular ones, any -ed but need, feed and the like) and before better, and would before anything else; a verb whose participle is its base form is had only after if + a subject (If I'd come earlier). So If I'd known, I'd have come is one answer with If I had known, I would have come, and a gap takes 'd have come alone. These stand after the A2 rules for 'd like and I'd love, and the catch-all last: **order matters, check it after merging part a**, whose past-perfect needs the same rule. **that** after said and told (+ one or two words) is optional; **whether** is if after ask, asked, know, wonder, sure, tell me.
- `loose`: a 'd typed without its apostrophe is almost, by the same split (Id have, If Id known, Hed go).
- `nonstandard`: would have, would of for had after if; would not have for had not; would + a verb for the past after if; of for have after a modal; a question's order kept after asked, know, wonder (whole line, and for a gap whose frame ends in where). One way only, and a direct question stays a direct question (Where did you live? for Where do you live? is wrong).
- `wrong`, paired both ways: must and most, what and that, where, there and here.

### Words (lexicon/en.extra.json)

Every sheet of the six lists every word it names with a meaning in sl, de and it, by the print's own `Dictionary`; every word of the exercises has an entry but the cast's names. `lexicon-build --coverage --lang en` went up, not down: the supplement 78.6 % → 79.1 % of words, with packs and names 98.1 % → 98.2 %, each base 97.4 % → 97.5 %. `bun test/prompt-clashes.ts`: no new clash (339 in the baseline): the supplement's words aren't cards.

## How the units are written

As COURSE.md "Writing a unit" and "The English course", plus what these six found (for COURSE.md's section, once part c has integrated):
- **At this level the point of a unit is often what people say**, so choices can't test it. They test what nobody grown up says (lived I, despite of, told to me, would be come, to have after if), and typed gaps and lines test the rest. Mixing tenses is the one to watch: If I have time, I'd help is natural, so have is no wrong option for If I had a boat.
- **Reported lines are long to list.** That after said and told and whether after asked are rules, so a line lists its subject (Edna, She) and its tense only. `spoken` holds at most 8.
- **Two columns hold 12 letters, one holds 18**: would have come and hadn't rained took tables of one column; wouldn't have stayed (20) became wouldn't have gone.
- **A bracketed translation is one sentence**, again: (Es regnete. Trotzdem gingen wir.) printed cut.
- **A first sentence with a form**, or the print loses the point: reported-questions' step on what people say opens with what writing wants.
- **An independent read paid**: a fresh agent reading only the English found gaps lacking could and might, wrong options that were right (She must be in the barn, the bus was), Irish He does be sleeping, Jack's van that was Sam's, and a next week said on the same day.

## Read aloud

Mrs Hartley reads the intros in the cast's teacher voice. A voice build of the six units would take about 7,150 ElevenLabs characters for her English lines (284 texts: examples, quick questions, answers, the English runs) and, on demand when an intro opens or with `voice-build --explanations`, about 15,400 (Slovene), 16,300 (German) and 15,900 (Italian) for the 42 explanations (36 steps, 6 summaries). Measured with `courseTexts`, nothing voiced, no credits spent.

## Checks

CHECKS

## What's left

- The native check at the top.
- The integration of parts a and b, what the brief gives part c: COURSE.md, the README, the page counts, the whole level's test of what people say, the Android suite, `past-perfect` on `if-i-had-known`.
- From `course-en-a2-c` after this branch's base (7993ca10): telled and the like wrong again, A1's to-be and was-were. These units offer none of them and pin none of them; they come with the integration.
