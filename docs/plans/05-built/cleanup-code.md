# Built: wave E's last cleanup, the code half (cleanup-code, 2026-11-04)

The items of the kit's followups-17.md that are code or QA: 4, 5, 6, 7, 9, 10, 11, 12, 18, 19, 22, 23, 24 and 27. Items 8 and 13 wait
for Jan, and the content items are cleanup-content's. Everything written here is machine-written; "Native check" lists what a
speaker should read.

## Sloleks

**Item 6 asked to check that nothing was taken from Sloleks 2.0's files. Something was, and that needs Jan's decision.**

- No file of the 2.0 release was ever used: no copy of it on this machine, no reader of its LMF format.
- But `viri.cjvt.si/sloleks`, the site `sl_sources.py` reads, **is** Sloleks 2.0. Its pages say "Sloleks 2.0" and "Delo je dostopno pod
  licenco Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International", and link to http://hdl.handle.net/11356/1230.
- That script took the forms from those pages. 2,262 entries of `sl.extra.json` cite Sloleks, 26,096 forms among them, and the APK
  bundles the file. `forms-tap.md` said Sloleks was "consulted by hand, never extracted as data": true of the forms book's tables,
  wrong for the supplement. It is corrected.
- NOTICE.md names Sloleks only for the order file below, so the supplement's paradigms carry no attribution there at all.
- It matters because of "non-commercial": the public plan has a paid audio add-on. Whether reading forms from the site binds the
  file is a legal question, not one this branch answers.

What the release says: every form was checked against **Sloleks 3.0** (CC BY-SA 4.0, http://hdl.handle.net/11356/1745, the zip's MD5
checked), read by `lexicon/tools/sloleks3.py`, which streams it and never unpacks it. Its record says 3.0 keeps 2.0's approx.
100,000 entries, but says nothing about relicensing them.

| `sl.extra.json`, entries citing Sloleks | |
|---|---|
| every form in 3.0 | 2,182 |
| entry in 3.0, some forms not | 20 |
| no such entry in 3.0 | 60 |
| forms of the entries 3.0 has | 25,857 of 25,898 |

The 60 are mostly words listed otherwise there (*počutiti se* without its *se*, relational adjectives) or words Sloleks lacks, as
their sources said; the 20 are mostly a form of another word's (*dvojen*'s *dvojna*). `sloleks3.py check --list` names them.

**Done:** the forms book cites 3.0 where 3.0 holds every form of the table: 105 of the 118 tables that linked the viewer (`sloleks3.py
cite`). The 13 others keep the viewer's link, each holding a form 3.0 lacks: zob, most, zid, slap, grad, vrata, mlinci, brat, breg, grob,
zvon, rog, les (mostly spoken or old forms in `also`, *moste*, *slapah*). `meta.review` says both: read on 2.0's site, then checked
against 3.0. A citation was changed only after that check: renaming them alone would have misstated where the forms came from.
`sl_sources.py` says not to take new forms from the site. Two page labels the scrape stored as forms, *variantno* (5 words) and
*nestandardno* (*zavreči*), are gone.

**Left for Jan, and not done here:** the 2,262 `source` fields stay as the honest record of what was read. The options as I see them:
rebuild those entries from 3.0 and cite it, since 2,182 come out the same (the 80 others to settle by hand); or ask CJVT whether
that is enough, or whether the site's data may be used. NOTICE.md's row for the supplement follows from that. 3.1 (11356/2080, the
same licence) replaces 3.0 and adds 7,001 spoken lexemes; 3.0 is the one followups-17 named.

## Forms in their order

**How the four cards get their order (item 9).** A card keeps its slots from the day it was made, so a new card was never the problem:
the regular check has given *mleku* first, *mleki* accepted, since it landed. Jan's *mleko*, *voda*, *potok* and *jajce* simply predate
it. GET /state now serves each Slovene forms card as a new one would be (`cardInOrder`, `withFormsInOrder`): the regular form first,
the other accepted, the slots' keys and what's tested as they were. **Nothing is written and no progress moves:** the deck on the node
stays as it is, its `answer` line too, and the order comes with the next read. An older app gets the same JSON shape.

**The alphabet, for the rest.** The same measurement found it reaching further than the four cards: 279 verb slots showed a form 3.0
doesn't have (*aktualiziraste* before *aktualizirate*, *barantama*), and nouns a rare one (*mescu* before *mesecu*). `sl.forms-order.json`
(made by `lexicon/tools/sl-forms-order.ts` from 3.0, so its licence is clean, and credited in NOTICE.md) names the form to show in 1,127
slots of 241 tables: a form Sloleks lacks gives way to the commonest it has, one it has only to a form at least twice as common and seen
at least 5 times. Nothing is taken away: every form stays right. Hand-checked tables keep their order. **Other languages:** their tables
keep Wiktionary's order, which was never alphabetical (of the slots with two forms, 113 of 255 in Italian happen to be), so nothing changed.

**A collective noun.** 3.0 has *grozdje* with singular forms only, and 44 more nouns so (`singular` in the file; *sadje*, *korenje*,
*prtljaga*). Their tables ask no dual or plural, on new cards and old; the forms stay known to the dictionary. The list is Sloleks's,
so it holds a few nouns people do use in the plural (*briga*): those are only not asked.

## The tutor (items 10, 12)

See [tutor-live.md](tutor-live.md), "The second scoring": the scores and what each finding became. `ReplyCheck` sends a chat's claim
back once without a lookup; rules 1, 13, 15 and 18; `languages/sl.md`; a lane's notebook says it's another tutor's. The channel guide
(`features/chat.ts`, exported to `tutor/guide/events.md`) says the same for a Claude Code session; `tutor-export --check` is green.

## A helper's words (item 19)

`speakerGendered` takes a language now, and `standInErrors` holds a helper's or a guest's lines, replies and puzzled reactions to it in
all four languages, translations too, with the app's repliers (the turn's `to`, else whoever spoke last). Friuli's and Kärnten's are
checked by it, the Lake District's again.

77 texts changed, 72 of them translations (Kärnten's Slovene 51: "Prinesel sem" and the like, written when only a Slovene pack was
checked), which cost no clip. 5 are voiced, 310 characters: four German, one Slovene. Each rule's limits are in its comment: it reads one line,
and what it can't tell it leaves alone. Reading the lines beside the hits found what no rule reads: "Kovač sem", "Sono un fabbro" say who
speaks, not only their gender, and went with the German.

## Cycles (item 22)

`DialogReactionsTest` reads every talk of a spot's cycle, wherever in it: 836 talks, 535 turns in the four cultures, played with every
wrong choice first. It found 26 turns:

- **9 gave the answer away** ("è il fabbro!", "To je dež.", against SCENES.md's "never says the right form"): reworded in all their
  languages, 8 reactions, 324 voiced characters.
- **17 only repeat the learner's own wrong sentence** ("Als waren Sie?", "Enough thick?"). In a wrong word order `ChoiceDiff` marks a moved
  word as what sets the right choice apart, though the wrong choice holds it too, so nothing is given away. Rewording them costs clips
  for nothing, and loosening the check would loosen it for every scene. They are listed by name in the test (`cycleEchoes`): a new one
  fails, and so does one that stops tripping it. **For the lead:** whether the check should tell an echo from a leak.

## The last line said, whole (items 18, 23)

The lines kept 72 dp beside a turn of long choices: two lines of a bubble. `LinesOverTurn` now keeps the last line said whole, measured
from the list (`DialogRoom.linesFor`), up to 55 % of the panel, and a line longer than that scrolls. A turn of choices gives way to it down
to 140 dp and scrolls; a typed turn keeps its 248 dp for the field, the chips and Check. It serves a reaction too, and every dialog the
panel plays, a scene's, an arrival's, a step's.

**The price, and the cue.** With a long line whole, a phone shows a choice and a half, and a third long choice can lie wholly out of
sight: QA's `kaernten-b2` stopped on just that, its right answer below the fold, which is what "it scrolls, but a learner may not know"
said of the old layout. So while a turn goes on below, a "▾" stands at its foot (`MoreBelow`; a tap brings the rest up; its label is an
existing string), and it goes when the turn's end is in view. QA swipes a long turn as a learner would. Raising the panel over the
picture, as the keyboard does, would show everything at once, but moves it for every long turn: not done, a choice for Jan.

Before and after, on `lakeland-b2` and `kaernten-b2` (1080 × 2400, translations on, the first-time word hint up; tablet 2560 × 1600 at 320
dpi, upright and on its side):

| | before | after |
|---|---|---|
| phone, Mr Tyson's 94 characters | "The walls took the whole village a", no translation, no 🔊 | all of it, translation and 🔊 |
| phone, the reaction | "Rafts? We're building a mill, not sailing to", 🔊 cut in half | all of it, translation and 🔊 |
| phone, Schmied Toni's arrival | "Hm. Du? Das sagen bei mir nur die, die schon", no 🔊 | all of it, translation and 🔊, "▾" over the third choice |
| tablet | the line whole: it has the room | the same |

## Placement (item 7)

- **The Build sheet's note:** for a learner whose A2 is open it says only that the school brings the class indoors: the first half of the
  same sentence, so nothing new to check.
- **"📨 Pošlji znova":** in the test's intro from the day after, with the treasure's strings; it sends unless one is queued still, which
  would count the test twice. `Placed` keeps no probes, so its items go as one.
- **A child's test reads each prompt** as it comes up (`PromptSpeech`): the instruction and the question, a gap a pause, never an option
  or the answer, by "🔊 Beri razlago" and not over a screen reader. Each prompt not voiced yet goes through the node as a tapped 🔊 does:
  at most 16 short prompts in a test.
- **The setup's speech:** left. first-minutes takes the "🧭 Nekaj že znaš?" choice out of the setup, so there's nothing to read.

## Lesson production (item 11)

The verdict's panel names what a text lacks (`UnmetChecks`, no new string). A written or spoken text gets the built-in cheer and comfort,
not a teacher's own (`Director.whole`). The label of a B2 letter's few lines says to go on on the back, on the task's own page; a sheet stays
two pages at most, as `school-prints` holds it. `friuli-school-unit` plays speaking and writing, a child's path included (LANI_CHILD=1,
$LANI_QA_IT_SHEET).

## QA

`kaernten-levels` reads its close label in the pair (item 4). `friuli-projects-b`, `lakeland-b2` and `kaernten-b2` are one class,
`VillagePlay`, the culture its argument (item 24; about 235 lines fewer), each run once through it. LANI_QA_B2_TABLET=1 adds the turn and the
reaction on a tablet. `choice()` swipes a long turn as a learner would.

**`forms-other` (item 5):** green for the first time, in all three: Friuli (a child), Kärnten, the Lake District. Its first expectation had
been fixed already; it only needed its run.

**`hint-de` (item 27):** green in de-en and de-it too. `hint-de-cases` is new: a case line from a verb and one from a preposition, on Most at
the press, with QA's hook "turns:type", since no start-of-village dialog has one. Seen in de-sl, de-en and de-it: "with «helfe»: this verb
takes the dative", "after «Aus»: the dative".

**`friuli-school-unit`, a child's path (item 11): only partly seen.** It plays speaking and writing now (LANI_CHILD=1, $LANI_QA_IT_SHEET). On
`periodo-ipotetico`'s sheet of them the first speaking item went through on the emulator: "Senza microfono", the self-rating, rated. It stops
at the second: the button is on the screen, but QA's dump of the screen doesn't return it, so no tap is sent (a log line on each tap showed
that; the app isn't at fault). So the second item and "✍️ Scrivi anche" have still not been seen on an emulator. `ProductionTest` and the smoke
cover them, as before.

**Not seen on a device, only tested in code:** `UnmetChecks` with a check not met (QA types the model, so its text passes), "📨 Pošlji znova"
(it needs a node that hasn't recorded a level by the next day), and a child's test reading its prompts (`PromptSpeech`; `placement` runs as an
adult).

## Native check

- The 72 translations and 5 voiced lines of "A helper's words", most of all Kärnten's Slovene: present and impersonal forms stand in for
  a past ("Izmerjeno: točno drži", "Tu so macesnove deske"), and some read freer than the German.
- The 8 reactions of "Cycles" (*Bo? Tako zanesljivo?*, *Sneg? Saj ni zima. Kaj pada z neba?*).
- `languages/sl.md`'s *s*/*z* and *-ost* lines, the print's "Piši na hrbtno stran" and its Italian and German.

## Voice

Nothing spent. A voice build would take 634 characters: 310 (helpers) and 324 (reactions). A changed line loses its clip until then.
