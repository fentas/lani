# Ideas

Ideas worth keeping that aren't planned yet. When one is picked up, it moves into a plan (docs/plans/) or is built,
and its line here says so.

## Dialogs (the scenes' conversations)

Brainstormed with Jan on 2026-09-27. What was wrong: the right choice was always the first one (all 252 turns), and
81 % of the wrong choices differ from the right one in a single word, mostly an ending, so a turn was "spot the
ending". What the wrong choices test: case endings ~29 %, a different meaning or situation ~28 %, number (dual,
plural) ~11 %, gender ~11 %, ti/vi ~6 %, prepositions, verb forms and clitic order the rest.

- **Done:** the choices come shuffled; the words that set them apart show bold (`ChoiceDiff`).
- **Done:** a wrong choice gets an in-character reaction (Jan says "žejen", Micka brings water; "Deset jajce?",
  "Samo eno?"), the why one tap away; every wrong choice of every scene has one (machine-written, not reviewed by a
  native speaker). Several right answers in the meaning turns ("Adijo" and "Čav"), each with its own reply
  (companion/SCENES.md, "Reactions and more than one right answer"). Still to do: the stories' wrong choices have no
  reactions yet.
- **Form turns as a gap:** when the choices differ in one word, show the intent once ("You want to say: Ten eggs,
  please.") and one sentence with a gap and chips: *Deset [jajc | jajce | jajca], prosim.* Jan couldn't picture it
  yet (2026-09-27); try it as a mock-up first. Related: two grammar variants often share the same English line, which
  reads as if they were the same; the intent line would say it once.
- **Done: adaptive difficulty:** a form the learner has secure turns from choosing into typing the missing word, one
  they have mastered into saying the whole sentence; "✋ Izberi raje · Let me choose" steps back, a slip steps the
  mastery back (companion/GAME.md, "Mastery and adaptive turns"; companion/SCENES.md, "Adaptive turns"). The typed turn
  shows the intent once and the gap, as the "form turns as a gap" idea above has it, but only once the rule is secure.
- **Done: the learner's own traps:** the wrong choice comes from the learner's mistakes (`mistakes-db.json`), their
  very word or their change of ending in a turn of the same rule (companion/SCENES.md, "Own traps"). Still open: scenes
  offering several wrong variants for the app to pick from.
- **Done: rules not yet:** what the learner must choose grows with the grammar introduced to them; a rule above their
  level that they haven't been introduced to is met in the dialogs (a choice about meaning, or an echo to repeat), not
  asked for, until the tutor or enough meetings introduce it (companion/SCENES.md and GAME.md, "Rules not yet").
- **Real branches:** a choice that leads to different following lines, not just a different reply (the format would
  need a jump).

### Dialogs repeat word for word

Jan (2026-09-27): "I asked Luka like 3 times now how many sheep he has, the same way." Each happening has one fixed
dialog (Luka's sheep at the brook: evenings, half of the days, once a day), so every time it's on it's the same.
Only the storyteller's stories rotate.

- **Done: variants per happening:** a list of dialogs instead of one; one not heard yet first, never the same twice in
  a row; after all of them an old one comes back as review after three days (companion/SCENES.md, "Variants"). Every
  happening of Primorska has one or two new variants, the other villages one (machine-written, not reviewed by a
  native speaker).
- **Done: numbers from the village:** "how many sheep?" is the dual/plural drill (ena ovca, dve ovci, tri ovce, pet
  ovc). A counting dialog takes its number from the village that day (its stores, its people, its buildings, else the
  day's dice) and builds the line and the choices from it, the wrong choices the other forms (companion/SCENES.md,
  "Numbers"). Luka's sheep, Micka's eggs, France's logs, the bottles in Marko's cart (the hundreds' rule) and more.
- **Dialogs that grow with friendship:** level 0 Luka shows his sheep; level 2 one is missing and he asks for help;
  small arcs over days.
- **Done: the tutor writes new variants:** when a happening's variants are all heard, the bridge asks the tutor for a
  fresh one at Jan's level, around their current weak spots (mistakes-db): `dialog_variants_heard`, `publish_dialog_variant`.
