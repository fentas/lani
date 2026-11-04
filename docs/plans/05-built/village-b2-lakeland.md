# Built: the Lake District's village to B2 (village-b2-lakeland, 2026-11-04)

Wave E, followups-16 §3, for the Lake District (English). A B2 learner of English met A2 or B1 lines in the project steps and the
arrivals, and Collier Ned had one talk. This branch adds what was missing. All of it is machine-written (Claude), flagged so in each
file's `review`. Nothing was voiced and no credits were spent.

## Native check (English, and the Slovene, Italian and German translations)

Each item is a question for a speaker. A form an adult says in Cumbria is never a wrong choice here; if one of these is, it has to go.

1. **Wrong choices that name a page: wrong in speech too?** "must have be", "would lost", "have score", "have take", "have manage",
   "can't have dug", "were haul", "been test", "it's cover", "has to opened", "could judged", "must have built", "They'd have dragged
   out by horses", "has set" (a keystone), "It's saying you've climbed", "used to letting", "used to carry", "was used to think", "got
   used to be", "wish you can", "wish I have", "if only … know", "If we will keep", "if you will wait", "if the footing won't be",
   "before I will start", "until it will drop", "you won't have let us", "you will still be", "you won't be so keen", "isn't it"
   after "It means" and "It goes", "made you to come", "mind to teach", "rather to have", "suggest to draw", "imagine you to do",
   "let it to go", "love that you teach", "Why it needs", "What you do", "Why you don't", "the more tight", "the more soon", "more
   taller", "more low", "different than from", "most of people", "so much stars", "how many earth", "how many wood", "Don't you got",
   "the knot who", "the one it's standing", "who they keep", "ask that they can", "because the wind", "get in beekeeping", "put it up
   with", "turned away … off", "comes up to" (for comes down to), "make a rest", "make some sleep", "make a holiday", "make it a go",
   "do difference", "in flowers", "on winter", "on your alone", "some of they".
   "Don't you got" and "different than from" are the two to look at first.
2. **Northern English the characters use:** "Now then!", "Aye", "mate", "love", "our Poppy", "happen" (perhaps: Ned, in "Happen it is",
   "Happen not"), "a brew", "It's stewed", "give over" (Maggie), "I've a story", "I'd no idea", "we've not". Are they right for
   Cumbria, and for these speakers (Ned is seventy, Jack twenty-two)?
3. **Words a Cumbrian might not use:** "tups" (Jack, the rams), "hefted", "gobsmacked" (Poppy), "a stick of rock" (Jack), "glamping" (Ned), "barbecues" as what saved the trade; the "drawing" of a pit: the stage's id only, the texts say "opens".
4. **The Slovene of the steps:** "špirovec" for a rafter, "sklepnik" for a keystone, "napera" and "pesto" for a wheel's spoke and hub,
   "kopišče", "lesa" for a hurdle, "zaslon" for a wind screen, "zapornica" for a sluice, "Zemlja" and "Okvir" for the arch's centring,
   "damaske slive" for damsons, "košeničica" for gorse (no exact word), "stržek" for wren, "pograd" for a bunk.
5. **The Italian:** "centina" for an arch's frame, "paratoia" for a sluice, "belvedere" for a viewing station, "cuccetta", "bollitore".
6. **The German:** "Bank Barn" left in English; "Bowlingrasen"; "Köhler Ned"; "Rähmchen"; "Niederwald" for coppice; "Flechtwand" for a hurdle.
7. **The vi four.** The learner says vi, Lei and Sie to Mrs Birkett, Mrs Hartley, Mr Dixon and Mr Tyson in the translations. Where one
   of them is a helper the learner's turn says neither the name nor the polite address (a helper may be away, and the step names only
   its leader), so the translations of those turns are neutral; a few to Mrs Birkett in the orchard keep "vam" and "Sie".
8. **The first person of a helper's line.** A helper may be away, and another says their lines. The bridge checks that only in a Slovene
   village, so `hgender.py` (in the tools) scanned the `sl` and `it` of the helpers' lines and replies, and the hits were rewritten: a
   present, a plural or an impersonal form ("Za luknjo ne dam niti penija", "To pa moram videti", "Padec je preverjen trikrat"). Some
   read freer than the English. Is each still what the speaker means, and natural?

## What the learner gets

| What | Before | Now | Where |
|---|---|---|---|
| Project steps | A1, A2 (78) | + B1, B2 for all 13 projects (156 tellings) | `cultures/lakeland/project-steps/*.json` |
| Arrivals | A1, A2, B1 | + B2 for all 13 and the newcomer (14 tellings) | `cultures/lakeland/arrivals.json` |
| Parties | A1, A2, B1, B2 | already done: 5 parties at four levels (verified with `content-count` and `check-celebrations`) | |
| The keeper | one talk, A1, A2 | a spot cycle at A1, A2, B1, B2: 41 talks and pieces of talk at each level, 6 tales | `cultures/lakeland/world.json` `spots.kopa` |

The app plays the highest level at or below the learner's (`Arrivals.levelFor`, and `SpotCycles.linesAt` for the keeper), so an A1 or A2
learner meets what they met before in the steps and the arrivals. **No format or validator change was needed:** every format took B1
and B2 already. Older apps bundle their packs and never see the cycle; an older bridge would refuse `cycle` (strict), as it did for
Primorska's, so the code that reads it is older than this branch.

**Steps.** 4 to 7 lines, 2 to 3 turns, the tap turns kept with their slots, the cues (`fx`, `sky`) and the `done` lines where the A2 telling
has them (50 new done lines). Each step's words are tested by a swap in the turn. The B1 and B2 lines use the level's pages; a wrong
choice is either another word or a learner's slip, never a form from the `nonstandard` table.

A reply is said by the turn's `to`, else by whoever spoke last. 25 turns come after a helper's line but answer in the leader's voice
(Mrs Hartley's "Top marks", Mrs Birkett's "love"), so they name the leader. The helpers' lines say nothing of who says them: no
Wilf's age, no "Mum" from Harry, no "when I was a girl" from Maggie.

**Arrivals.** Each B2 arrival has its late line, 6 to 8 lines and 2 to 3 turns, and tells the person's story further than B1: Maggie's
Saturdays as a girl, Jack's hefted Herdwicks and the winter he was sixteen, Poppy's parents, Wilf before the fells were counted, Mr
Tyson's eleven miles to the next dale's mill, Edna's forty years in Carlisle, Arthur's night move to the heather, Mr Dixon's
apprenticeship in Penrith, Sam's office and the visit to Brda, four generations at the Packhorse, Harry's two windows, Mrs Hartley's
baker in Ljubljana, the newcomer's first quiet night. Nobody is named but the person and the introducer, so the level never falls back.
The birth and moved templates stay at A1 and A2 (the brief asks for the newcomer's).

**The pit.** GAME.md "The Lake District's pit" has the stages and sources. Eleven stages of 17 to 22 days, season 20 April to 20 October,
pause 16 to 32 days, two talks a stage, one more for the night watch in the white smoke and one for the wind in the yellow; rest,
winter, lost, waiting; three greetings, missed, two nights (the wind, a badger), the breakout (25, 18, 16, 15 seconds), six tales
(`father`, `hut`, `last`, `show`, `iron`, `after`) and his meeting at B1 and B2. A telling that stopped at its turn ends on one of eight
short closing lines of his (`cyc.py`), because a talk needs three lines. The delivery goes to Mr Dixon, and his forge saves a quarter of
its wood as Tone's does.

## Facts the lines state, with their sources

- Coppice, pitsteads cut into the slope, stone hut bases, the iron at Backbarrow: https://nancyblackett.org/?p=11161; flat circles
  about 35 feet across: https://hvfarmscape.org/charcoal.
- Wood cut to equal lengths, a hut of larch poles with moss and a sack for a door, the earth patched where smoke breaks through, day
  and night, "the slower the fire the better the charcoal", Old Billy, Young Billy and the adder: Ransome, *Swallows and Amazons*, ch.
  XIII, https://americanliterature.com/author/arthur-ransome/book/swallows-and-amazons/chapter-xiii-the-charcoal-burners.
- Bracken, then earth; the pole drawn and embers dropped down: https://butserancientfarm.co.uk/blog/2022/8/9/an-experimental-charcoal-burn.
- Up to ten days of burning: https://canmore.org.uk/event/1018726. The woods of Finsthwaite and Furness Abbey:
  https://kentsbankholiday.co.uk/?p=8649. Duddon furnace: https://en.wikipedia.org/wiki/Duddon_furnace.
- **From general knowledge, not checked against a source:** white, yellow and blue smoke in the Lakes (Primorska's sources give the
  sequence for the craft); twenty sacks a pit; a fifth of the weight; fifteen years' coppice rotation; yan, tyan, tethera, methera,
  pimp; hefted flocks; the 214 fells of Wainwright's guides; Thomas West's guide of 1778 and the mirrors of the first tourists; the
  Lyth Valley's damsons in blossom in April; Honister the last working slate mine of its kind (Wilf's "said to be"); IIII on clock faces.

## Checks

- `bun test/check-culture.ts lakeland`: ok. `bun test/check-steps.ts lakeland`: ok, 13 files, 78 steps, 312 dialogs, 1,742 lines.
  `bun test/check-celebrations.ts lakeland`: 5 celebrations, 20 parties. `tsc`: clean. `tutor-export --check`: 140 files match.
- Coverage (`bin/lexicon-build --coverage --lang en --all`): by the supplement 90.2 → 92.0 % of tokens, with the packs and names 99.7 →
  99.8 %. After the last merges of main, with the other new English in it, 91.6 % and 99.8 % (the two were not told apart).
  Of the new lines' words only "builded", "charcoals" and "newses" are unknown: wrong forms, left out on purpose. The supplement grew
  by about 400 entries and 140 forms, each with its meanings in sl, de and it.
- Tests: `LakelandPitTest` (new), `LakelandProjectStepsLevelsTest` (new), `SceneCoherenceTest` (50 more done lines), `KopaCycleTest` (the
  Lake District left the list of piles without a cycle). The Friuli and Kärnten agents will touch the same two lines.
- Smoke on the last merge of main (`775384b64`): all checks passed (3,873, none failed). Python: 253 and 179 tests OK (11 skipped).
- Android suite through `lani-slot.sh`: the first run found three failures, all fixed: `LakelandPitTest` (its setup returned a value, so no
  test in it ran), `SceneCoherenceTest` (Ned's "look" at things his picture doesn't draw) and `DialogReactionsTest` (a reaction said "made").
  On the last merge of main: 4,159 tests, none failed, 2 skipped.
- QA: the step `lakeland-b2` (only when named), a B2 step and Mr Dixon's B2 arrival: `make-learner.ts <dir> --name Alex --target en
  --base sl` (no `--level B2`: it reads companion/modules/en, which doesn't exist), `LANI_CULTURE=lakeland LANI_QA_SETUP_PAIR=en-sl`.
  It passed: the mill's walls at B2 (the right and the wrong choice, the reaction, the walls drawn at the end) and Mr Dixon's arrival at
  B2 ("Morali ste biti zelo potrpežljivi", "Must have be? Hm. That's a nail without a head"). Screenshots: `b2-*.png`. This ran on
  `f7ddd16d6`, one merge of main before the last; it was not repeated.

## The room a B2 line has

In the scene panel a B2 turn took most of the room, and the lines kept only 72 dp: Mr Tyson's 94-character line stopped at "The walls took
the whole village a". Done in [cleanup-code.md](cleanup-code.md), "The last line said, whole".

## Voices (nothing spent)

The new English, as voice-build would voice it (lines, the learner's choices, replies): arrivals 16,125 characters, steps 111,710, the
pit 42,599: 170,434 in all. The voices are the cast's own and Ned's (`grandpa`); nothing new to cast.

## The tools

`.lani-lead-kit/village-b2-lakeland/`: `dsl.py` (steps and arrivals from a compact text), `cyc.py` and `cycle_meta.py` (the cycle,
re-runnable), `supp.py` (the supplement), `lint.py` (a reaction's length, and whether it says the right form), `newwords.py`, `chars.py`,
`gender.py`, `lgender.py`, `hgender.py` (who a form in `sl` and `it` agrees with), and the texts as written (`c*.txt`, `s-*.txt`, `arr*.txt`,
`supp*.txt`). Those texts are the first drafts: the JSON is the source now, and running `dsl.py` over them would undo the later fixes
(`fixg.py`, `addto.py`).
