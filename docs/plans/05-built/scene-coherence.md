# Built: what a line says happens shows in its scene

Jan, 2026-10-27, playing the mill project: "I thought we fixed these scenarios. e.g. I do the mill right now. '… the water
is running. look the wheel is turning.' < nothing is turning. '… where are the sheep … here they are … drinks last …' <
nothing happened."

The rules as built are in companion/SCENES.md ("Project steps": the step's work in its picture; "Effects"; "Numbers": the
picture counts too; the Arts table's effects). This note says why it was so, what was built, what the audit of every
dialog found and did, how to keep it so, and what is left.

## Why it was so

- **The mill.** Its last step ("Prva moka", played by the stream) has Tine's "Voda teče … Glejte, kolo se vrti!" with
  `"fx": {"wheel": 1}`. The stream's painter drew the mill by the project's steps done, and while a step is played that is
  the steps before it: the wheel and the flume were there, but `gushOf()` and the wheel's spin gave no water and no turn
  until the mill was finished (`millDone`), which it is only once the step ends. So the cue fell on a wheel that couldn't
  turn. The same held for every step in a scene that draws its project: the walls never went up as Tone said "Prva stena
  stoji!", the piers weren't there at "Glejte, stebra že stojita!", the peeled trunk wasn't white at "Glej, kako bel je les!".
  The scene showed the project as it stood before the step.
- **The sheep.** The stream has one ewe drinking at the right bank. Luka's dialogs talk about the flock: thirsty after the
  day in the sun, twenty of them, "Preštej ovce pri vodi!" with a number of 12 to 24, "Ovce, domov!"; the Friuli village's
  Davide asks the learner to count 8 to 20 sheep, "Ci sono tutte?", then "E Bianca? Bianca beve sempre per ultima!" with a
  `lamb` cue (Jan's "where are the sheep … here they are … drinks last"). Nothing of a flock was drawn, and no painter knew
  what a dialog counted.
- **The rest.** No check tied a line's words to what its picture shows: a cue was checked against its art (the bridge, the
  content tests), not against the line. The audit below found 197 lines across the scenes, the steps, the stories, the
  keepers' talks and the parties that promised what their picture didn't show.

## What

**A step's work in its picture** (`DialogLine.done`, `Projects.shown`, `Projects.stepWorld`, `SceneWorld.stepping`,
`SceneScreen`): while a project's step is played in its scene, the scene's world has the project at the steps done before
it, and from the line that says the work is done (`"done": true` on someone's line, once a level) one more; without one, the
step's work shows once played (its end card). The painters draw the stage they're given, so nothing in them changed for it.
46 lines in 23 steps of six projects say where (the mill's six steps; the cart bridge's measuring, stones, piers and deck;
the hayrack's feet, poles, roof and first hay; the clock's window and lifting; the maypole's spruce felled and carried off in
the forest, its peeling, wreath and raising on the square; the fountain's stone and ditch). Five steps in such scenes keep it for their end by design (the first cart over the bridge, the
first of the hayrack's eight posts, the clock striking noon at twelve, the trough still being carved, the fountain's water,
which its own cue runs). `SceneArt.projects` names the projects each art draws; the bridge's copy is `DRAWN_PROJECTS`, and
`project-steps.ts` checks a `done`: someone's line, once a level, only with a scene that draws the project.

**The mill's wheel** (StreamPainter): `wheelIn` (the wheel and the flume in place, from the step before the last) lets a
`wheel` cue pour the water and turn it before the mill is finished; with no cue it stands still and dry until the mill
grinds, then turns easily as before. The last step's done line is Micka's "Prva moka!" (A2: "Peč je že vroča"): the mill
grinds from there (its sack by the door, the window lit at night, the word "mlin" to tap).

**The flock** (StreamPainter `flock`, `straggler`; `SceneFrame.count`): up to 0.5 the sheep come in over the meadow from
beyond the picture's right edge one after another, each to her place along the right bank (`FLOCK_AT`: the front row at the
water's edge drinking, rings on the water, three rows behind grazing, a head up now and then), from 0.5 they go home toward
us one after another and out at the picture's bottom, 1 gone; as many as the dialog counts, the ewe always there among them,
else twenty (Luka's "Dvajset."). `straggler` is the one who comes last: she trots in alone to the water's edge in front of us
and drinks, and goes home with them, last. Their bells clink as they come and go (Sfx). The number reaches the picture:
`DialogVariants.counted` (the same `Counts.of` the dialog's texts are rendered with) → `SceneTalk.counted` → `SceneView` →
`SceneFrame.count`. Cued: Luka's ovce (0.5 as they're thirsty, 1 at "Greva, ovce morajo domov", the trout let go) and
ovce-stejem (0.5 at "Preštej ovce pri vodi!", 1 at "Ovce, domov!"), both `fx_stays`; Davide's pecore (0.5 with "Guarda come
bevono!", 1 at his goodbye) and pecore-conto (0.5 at "Ci sono tutte?", `straggler` 1 at "Bianca beve sempre per ultima!"
instead of the lamb).

**More at the stream**: `full` (the stream high and brown the morning after the night's rain, the sky clear: France's
"Ponoči je deževalo. Poglej potok!"; it uses the rain's swollen water, `ArtPainter.weatherAnyway`), `sacks` (the day's sacks of
flour stacked by the mill's door to count: as many as the dialog counts, else five: "Zdaj pa štejva vreče", "Poglej vreče
in jih preštej!"), `sheet` (Nonna Rosa's sheet blown off the washing stone over the water, caught and folded on the basket:
"Il vento porta via il lenzuolo!"). The cart bridge's fourth stage has the deck's ten planks stacked by its ramp ("Poglej kup.
Koliko desk je še tam?"); the square's peeled maypole has its bark in strips on the cobbles ("Glej, kaj pade na tla?").

**Over the village**: a party's stage draws what the village put up for it (`PartyDecor`: the first camp's torches, the
likof's roof and little spruce, the veselica's floor with a couple turning at each side, the semenj's stalls with honey
hearts and whistles, the town's banners), so "Glej, kako lepo gorijo", "Poglej stojnice", "Glej jih, kako plešejo!" and "Kaj
je zdaj na strehi?" have it in view. A charcoal burner without the pile's rounds (Friuli, Carinthia, Lakeland) talks with
his pile in the picture as the map draws it ("Questa è la mia carbonaia"); on the breakout night the pile's crown is ablaze
over Miha's "Hitro! Glej, ogenj!" until it is saved, all of it when it is lost (`PilePicture`'s fire).

**The painters' new effects** (SCENES.md's Arts table has each, with its levels; `SceneArt.effects` and the bridge's
`SCENE_ART.effects` list them):

| Art | Effect | What it shows | The lines it serves |
|---|---|---|---|
| forest | `fell`, `fell-oak` | a spruce (an oak) at the clearing's far side shivers, falls faster and faster and crashes down with dust and twigs, then lies by its stump; not set, the maypole's spruce lies there felled until it is carried off (`stage("mlaj")`) | "Pazite! Smreka pada!", "Pazite, pada! … Bum!" |
| forest | `sheep` | the lost sheep out of sight behind the big spruce (not tappable), coming home through the brook | "Ovca se je izgubila … Tam, za smreko!", "Bianca è sparita! … Eccola!" |
| forest | `tracks` | a bear's prints in the mud of the bank by the path | "Poglej tja, v blato. Velike sledi." |
| forest | `does` | roe deer stepping out onto the far meadow at dusk, as many as the dialog counts, else five | "Zdaj pridejo srne na travnik", "Preštej srne" |
| forest | `hoot` (above 0.9) | the owl now and then turns her head right round | "Glej! Glavo obrne čisto nazaj." |
| alps | `bell-lost`, `pail`, `cows` | the near cow's bell lost in the grass and put back; the milk pail filling; as many cows (and a calf) as the dialog counts | "Poglej tisto kravo. Nič ne zvoni!", "Poglej vedro. Polno je!", "Preštej krave" |
| hills | `tractor` (from 0.97) | gone past us: nothing drawn, so a dialog can keep it gone | traktor, traktor-voznik, traktor-zaboji |
| smithy | `iron`, `cart` | a shoe in the tongs on the anvil, white-hot, bent as it cools to grey; Marko's cart propped on a block, its wheel put back on | "Železo je vroče. Rdeče. Vidiš?", "Il ferro è caldo", "Poglej moj voz … kolo je zlomljeno" |
| pond | `heron`, `bite`, `bucket` | the heron striking and standing with a fish in its bill; a rod and float for whoever fishes at the jetty, the float dipping, a carp on the line; the day's catch in a bucket, as many as counted | "Glejte čapljo. Zdaj!", "Plovec se premika. Riba je prijela!", "Poglej v vedro in preštej" |
| field | `face` | the scarecrow's blank sack getting a charcoal face, the crow off its arm meanwhile | "Strašilo nima obraza. Nariši mu obraz!" |
| campfire | `flock` | Luka's sheep out of the trees and along the camp's edge to be counted, then down the path, as many as counted | "Ovce gredo s planine. Pomagaj mi jih prešteti!" |
| workshop | `toy` | Sivko the wooden horse on his side with a leg off, glued and stood up whole | "Moj konjiček Sivko nima noge … Spet je cel!" |
| stream | `flock`, `straggler`, `full`, `sacks`, `sheet` | above | above |

**Content**: cues added or moved where the painter draws what the line says; lines rewritten where it doesn't and drawing it
isn't reasonable (every language, machine-written: see "For the native check"); variants whose lines need the dark played
only in the winter months.

## The audit

Every dialog was read against its picture: the 18 curated scenes and their variants, the culture packs' 15 scenes (Friuli's
nine, Carinthia's two, Lakeland's two, Primorska's two), the 78 project steps at both levels, the 46 evening stories and
their chapters, the keepers' talks (the kopa's rounds) and the five parties: **11,751 things said** (someone's lines, the
learner's choices, the replies and reactions) in 714 tellings. A broad lint (look words in the four languages and an English
list of visible actions) flagged 1,692 of them in 558 dialogs, and every one of those dialogs was read whole against its
art's effects, its painter's gates and its happening's hours and months (two helpers took the village's scenes and the
cultures', stories, keepers and parties; the steps and Jan's two were the lead's).

**197 mismatches**, by kind, and how each was settled:

| Kind | Found | Content cue | Painter or code | Rewritten | Months | Left |
|---|---|---|---|---|---|---|
| a cue missing or on the wrong line (the pedlar not on the road as Nejc says "Krošnjar prihaja!", one candle lit as Micka lights hers, the crows leaving on the wrong line) | 13 | 13 | | | | |
| what's said isn't drawn at all (the flock, the felled spruce, the heron's strike, the iron on the anvil, a party's torches …) | 56 | | 36 | 13 | | 7 |
| a number counted in view that the picture doesn't have (the sheep, the sacks, the cows, the deer …) | 27 | | 7 | 1 | | 19 |
| a gate: the dialog plays when its picture can't show it (a step's own work, the mill's wheel, stars at five in the evening, the sun before sunrise) | 41 | | 24 | 6 | 4 | 7 |
| a false promise not worth drawing (a table at a level without one, the sun "asleep" at dawn, a blue cap drawn brown, "deset netopirjev" with four drawn) | 60 | | | 60 | | |
| **Total** | **197** | **13** | **67** | **80** | **4** | **33** |

21 new effects (the table above), three widened (`wheel`, `hoot`, `tractor`), a step's `done` (23 steps), the picture's
count (`SceneFrame.count`), a party's decorations, the pile behind every charcoal burner and its fire on the breakout night.
99 texts were rewritten (in all their languages; listed below for the native check). The 33 left are in "What's left".

## Tests

- `SceneCoherenceTest` (game/scene): every effect a dialog cues is one its picture's painter draws (a scene's dialogs and
  variants, a step in its scene's art, over the village only the bell, the stories by the fire, none at a keeper's talk or a
  party); every step in a scene that draws its project has a done line at each level (or is one of the five), once, on
  someone's line; `Projects.shown` and `SceneWorld.stepping` give the step's stage from that line; and the look lint (below).
- The look lint: every line or reply that says poglej/glej(te), guarda(te), schau(t)/sieh/seht or look in the language it is
  said in must have a cue in reach (its own, the unit's before it, an effect the dialog has on by then, a step's done line
  before it), name a thing of its scene, or be reviewed in `src/test/resources/scene-coherence/looks.json` with why it's fine
  (a review no longer needed fails too). Every line that promises something visible (a word list per language: vrti, teče,
  skoči, prihaja, domov, tukaj so …; gira, salta, eccolo …; dreht, kommt, da sind …; turning, jumped, here they are …) is
  listed for review with its status in `build/reports/scene-coherence/visible-actions.tsv`, the looks in `looks.tsv`; that
  list doesn't fail the build.
- `StepScenesRenderTest` (game/render/scene): each step with a done line draws its stage differently from the one before
  (21 steps, details 1 and 2); the mill's wheel at stage 5: its paddles move between frames under `wheel` 1, stand still
  without a cue and at 0, and turn by themselves once the mill grinds (every detail); the flock: nothing at 0 and at 1, six
  fewer pixels than twenty-four, twenty by default, coming and going elsewhere than at the water, the straggler, home with
  them; the stream with a flock of 24, the straggler and the lamb at night within the frame budget (20 ms at 360×240); the
  planks and the bark. Renders for review in `build/scene-snapshots/coherence/`.
- `ScenesContentTest`: the flock's cues in ovce and ovce-stejem, the candles lit one by one, the rod's bite at dawn, and the
  dialogs that keep their effects (31 now). `SceneWorldRenderTest`: the forest changes with the maypole's spruce.
- The numbers at the last run: 11,751 things said in 714 tellings; 264 looks, every one cued (in reach or before), naming a
  thing in view, or reviewed (47 reviews); 910 visible actions listed (296 cued, 235 with a cue before, 77 naming a thing in
  view, 294 without a cue: read, and fine as they are or settled by a rewrite: the list is for the next review). The stream
  with a flock of 24, the straggler and the lamb renders at 7–10 ms a frame (360×240, night; budget 20). The whole unit suite:
  3,083 tests, none failing; the bridge's smoke passes; `bun test/check-steps.ts primorska` and every culture check pass.
- `SceneEffectsTest` covers every new effect (it shows at every detail, keeps the things to tap, changes nothing elsewhere);
  `CueStatesRenderTest` renders their states for review.
- QA: `companion/bin/qa --steps scene-coherence` plays the mill's last step (QA's hook "step:mlin/6") and Luka's counted flock
  (QA's hooks "hour:19.5", "happening:ob-potoku/ovce/ovce-stejem").

## How to use

- **A project step**: put `"done": true` on the line that says its work is done when its scene draws the project
  (`SceneArt.projects`); the bridge's `bun test/check-steps.ts <culture>` and `SceneCoherenceTest` say when it's missing or
  misplaced. Its effects play on the step's stage: cue what it does.
- **A dialog**: what a line says happens in view needs its cue on that line (a choice's on its reply), at the level that shows
  it (SCENES.md's Arts table); end with `fx_stays` when it leaves something somewhere for good. Don't tell the learner to
  look at something the art doesn't draw: rewrite the line, or ask for the effect.
- **Counting**: an effect that brings what is counted draws `SceneFrame.count` of it; give the counting dialog its cue
  (`flock`, `sacks`).
- **The lint's lists**: `build/reports/scene-coherence/` after `:app:testDebugUnitTest --tests '*SceneCoherenceTest'`.

## What's left

- **Not drawn yet** (the lines stand; the look lint passes them as a thing in view or a cue before): Micka's cheese stall
  "empty" while its wheels are drawn (market `cheese`), the two children carrying buckets from the well (square `water`),
  the roe doe and her fawn at the forest's edge (forest `fawn`), the blackboard's three wrong sentences (school `board`),
  Nejc's cap on the pillow (tent `cap`), the rainbow after the rain (a sky field), the town band's trumpets at the town's
  feast. The helpers' specs are in the audit's findings (scratchpad), and each is a small painter round.
- **Counted, not drawn as counted** (19): the plates, the baskets on the cart, the mown rows, the eggs at the market and in
  the Friuli kitchen, the buckets at the well, the logs of the four campfires' woodpiles, the hives, the candles, the boards,
  the glasses, the bottles on Marko's cart, the books on the desk, the crates by the road, the seconds to the thunder (the
  thunder comes after 2.5–6 s, the count says 3–12). `SceneFrame.count` is there for each painter to take up.
- **Gates** (7): dusk ("Mrači se") for the watchtower's and the forest's evening dialogs, which play from 17:00 in summer
  daylight (a `dusk` part of the day, like `dawn`, would do it); the Plough asked for before it's dark; Micka's walnuts "in
  the kredenca" at a kitchen level without one; Tine and Zala named at the school when their day hasn't brought them; the
  fell's sunset all evening; the kopa "green" when its turf is already under earth.
- **Projects not drawn in their scenes**: the bocce court, the playground and the fire station on the square, the bee meadow
  and the vineyard's terraces in the field, the lookout tower on its hill, the wayside chapel (over the village). Their steps
  are played on scenes that show nothing of them; their lines that told the learner to look were rewritten, the rest is the
  step's narrative. Drawing them by stage (as the fountain and the maypole are) is the round that makes them coherent.
- **The flock's walk** is fast (each sheep's walk a second of the four-second ease) and an effect let go walks backward
  (the convention for every effect); the dialogs that bring a flock end with it gone home and keep that.
- **Sound**: the felled tree's crash, the heron's splash.

## Deploying

The new effects are in the bridge's registry (`SCENE_ART.effects`), and a bridge from before them rejects a scene that uses
one: deploy the bridge and the app first, then the content (the code and the content are separate commits). An older app
reads past `done` (the step's work shows at its end, as before) and draws nothing for an effect it doesn't know.

## For the native check

Machine-written (Claude), not reviewed by a native speaker; every rewritten line also needs its voice clip made again
(voice-build). Each row is the file, where in it, and the line before → after in the content's language (its other
languages changed with it):

- cultures/friuli/scenes/nella-tenda.json /dialogs[1]/lines[4]: "Guarda, il sole! Non piove più. Torno dalle pecore." → "Guarda, non piove più! Torno dalle pecore."
- cultures/friuli/stories/il-colle-di-udine.json /levels/A1/lines[10]/choices[2]/reply: "Nei elmi? Hm… anche Moro fa una faccia strana." → "Nei elmi? Hm… non ho capito bene."
- cultures/friuli/stories/il-ponte-del-diavolo.json /levels/A2/lines[8]/choices[2]/reply: "Uno gatto? Hm, anche Moro alza le orecchie!" → "Uno gatto? Hm, che gatto strano!"
- cultures/friuli/stories/la-madonna-del-lussari.json /levels/A1/lines[9]/choices[1]/reply: "Dove sono? Sei qui con me e con Moro, {m:piccolo|f:piccola}!" → "Dove sono? Sei qui con me, accanto al fuoco, {m:piccolo|f:piccola}!"
- cultures/friuli/stories/le-agane.json /levels/A1/lines[1]/choices[2]/reply: "Buongiorno? A quest'ora? Ma è già buio, {m:piccolo|f:piccola}!" → "Buongiorno? A quest'ora? Ma è già sera, {m:piccolo|f:piccola}!"
- cultures/kaernten/stories/der-dobratsch.json /levels/A1/lines[0]: "Guten Abend! Schau, dort drüben ist der Dobratsch. Siehst du ihn?" → "Guten Abend! Kennst du den Dobratsch? Das ist ein Berg bei Villach."
- cultures/kaernten/stories/der-dobratsch.json /levels/A1/lines[1]/choices[0]: "Ja, ich sehe ihn. Er ist sehr groß!" → "Ja, ich kenne ihn. Er ist sehr groß!"
- cultures/kaernten/stories/der-dobratsch.json /levels/A1/lines[1]/choices[1]: "Ja, ich sehe er. Er ist sehr groß!" → "Ja, ich kenne er. Er ist sehr groß!"
- cultures/kaernten/stories/der-dobratsch.json /levels/A1/lines[1]/choices[1]/why: "None" → "None"
- cultures/kaernten/stories/der-dobratsch.json /levels/A1/lines[1]/choices[1]/reply: "Ich sehe er? Wer ist er, {m:Bub|f:Mädel}? Mein Bello?" → "Ich kenne er? Wer ist er, {m:Bub|f:Mädel}? Mein Bello?"
- cultures/kaernten/stories/der-dobratsch.json /levels/A1/lines[11]/choices[1]/reply: "Eine Wald? Hm, da spitzt sogar der Bello die Ohren." → "Eine Wald? Hm, das klingt aber komisch."
- cultures/kaernten/stories/der-lindwurm.json /levels/A1/lines[1]/choices[2]/reply: "Guten Morgen? Um diese Zeit? Schau, da oben sind die Sterne!" → "Guten Morgen? Um diese Zeit? Es ist doch schon Abend!"
- cultures/kaernten/stories/die-riesen.json /levels/A1/lines[0]: "Guten Abend! Schau, dort drüben: die Karawanken. Sie sind hoch und lang, wie eine Mauer." → "Guten Abend! Kennst du die Karawanken? Sie sind hoch und lang, wie eine Mauer."
- cultures/kaernten/stories/die-riesen.json /levels/A1/lines[1]/choices[1]/reply: "Ist? Nur ein Berg? Schau genau, {m:Bub|f:Mädel}, da stehen viele!" → "Ist? Nur ein Berg? Nein, nein, {m:Bub|f:Mädel}, es gibt dort viele!"
- cultures/lakeland/scenes/by-the-fire.json /variants[1]/lines[2]: "Look at my log! It's huge!" → "I've got a huge log!"
- cultures/lakeland/scenes/on-the-fell.json /dialogs[2]/lines[2]: "And here comes Daisy, down to the hut. Listen to her bell!" → "And there's Daisy, by the hut. Listen to her bell!"
- cultures/lakeland/scenes/on-the-fell.json /variants[3]/lines[2]: "Hey, look! Whose is that green rod on the grass?" → "Hey! I found a green rod by the lake. Whose is it?"
- cultures/lakeland/stories/the-borrowdale-cuckoo.json /levels/A2/lines[1]/choices[1]/reply: "Heared? That's a new one, {m:lad|f:lass}. Even Bess pricked up her ears!" → "Heared? That's a new one, {m:lad|f:lass}. Say it again?"
- cultures/lakeland/stories/the-lady-of-the-lake.json /levels/A2/lines[1]/choices[1]/reply: "Eh? Bess tilts her head, {m:lad|f:lass}. So do I. Once more?" → "Eh? I don't follow, {m:lad|f:lass}. Once more?"
- cultures/primorska/celebrations/tabor.json /levels/A2/lines[3]: "Veter piha in ogenj je majhen. Kaj nam bo svetilo, ko pade noč?" → "Ogenj ne sveti daleč. Kaj nam bo svetilo v temi?"
- cultures/primorska/project-steps/balinisce.json /steps[4]/levels/A1/lines[1]/choices[0]/reply: "Hvala. Zdaj pa glejte in se učite." → "Hvala. Zdaj pa se učite od mojstra."
- cultures/primorska/project-steps/balinisce.json /steps[4]/levels/A1/lines[2]: "Glejte! Krogla je čisto pri bulinu!" → "Neverjetno! Krogla je čisto pri bulinu!"
- cultures/primorska/project-steps/balinisce.json /steps[4]/levels/A2/lines[2]: "Glejte! Krogla se je ustavila čisto pri bulinu!" → "Neverjetno! Krogla se je ustavila čisto pri bulinu!"
- cultures/primorska/project-steps/igrisce.json /steps[1]/levels/A1/lines[0]: "Tukaj bo igrišče. Ampak poglejte tla!" → "Tukaj bo igrišče. Ampak tla niso varna!"
- cultures/primorska/project-steps/igrisce.json /steps[1]/levels/A2/lines[2]: "{learner}, poglej okoli. Kaj vidiš v travi?" → "{learner}, kaj je v travi?"
- cultures/primorska/project-steps/igrisce.json /steps[1]/levels/A2/lines[3]/choices[0]: "V travi vidim veliko kamnov." → "V travi je veliko kamnov."
- cultures/primorska/project-steps/igrisce.json /steps[1]/levels/A2/lines[3]/choices[1]: "V travi vidim veliko kamni." → "V travi je veliko kamni."
- cultures/primorska/project-steps/igrisce.json /steps[1]/levels/A2/lines[3]/choices[2]: "V travi vidim veliko polžev." → "V travi je veliko polžev."
- cultures/primorska/project-steps/kapelica.json /steps[2]/levels/A2/lines[3]: "Kamen na kamen … Poglej, kako raste! {learner}, kaj je že višje od tebe?" → "Kamen na kamen … Kako hitro raste! {learner}, kaj je že višje od tebe?"
- cultures/primorska/project-steps/razgledni_stolp.json /steps[6]/levels/A1/lines[0]: "Glejte, kdo prihaja po stopnicah! Tako dolgo sem čakala." → "Slišite? Nekdo prihaja po stopnicah! Tako dolgo sem čakala."
- cultures/primorska/project-steps/vinograd.json /steps[5]/levels/A1/lines[3]: "Rebula, kot doma v Brdih. Poglej, kako majhne so! Kakšne so te trte?" → "Rebula, kot doma v Brdih. Tako majhne so! Kakšne so te trte?"
- cultures/primorska/project-steps/vinograd.json /steps[6]/levels/A1/lines[5]: "Na zdravje! Glejte, nebo je čisto rdeče." → "Na zdravje! Kako lep večer!"
- cultures/primorska/project-steps/vinograd.json /steps[6]/levels/A2/lines[5]: "Na zdravje! Glejte, kako je nebo nad hribi rdeče." → "Na zdravje! Kako lep je večer nad hribi."
- cultures/primorska/stories/bogatinovo-zlato.json /levels/B1/lines[0]: "Dober večer, {learner}. Nocoj je jasno in hladno, ravno prav za zgodbo, ki nadaljuje tisto o Zlatorogu. Se spomniš, kaj odpirajo njegovi rogovi?" → "Dober večer, {learner}. Nocoj je hladno, ravno prav za zgodbo, ki nadaljuje tisto o Zlatorogu. Se spomniš, kaj odpirajo njegovi rogovi?"
- cultures/primorska/stories/martin-krpan.json /chapters[2]/levels/A1/lines[9]/choices[1]/reply: "Iz lipa? Hm? Glej, še Muri ne razume." → "Iz lipa? Hm? Tega pa ne razumem."
- cultures/primorska/stories/zlatorog.json /levels/A1/lines[1]/choices[2]/reply: "Dobro jutro? Ha, poglej gor. Zvezde so že tu!" → "Dobro jutro? Ha, saj je že večer!"
- cultures/primorska/world.json /spots/kopa/cycle/stages[0]/talks[1]/levels/A1/lines[1]: "To je moja sekira. Stara je, a dobra." → "Moja sekira je stara, a dobra."
- cultures/primorska/world.json /spots/kopa/cycle/stages[1]/talks[0]/levels/A2/lines[2]/choices[1]/reply: "Zgoraj? Poglej še enkrat." → "Zgoraj? Premisli še enkrat."
- cultures/primorska/world.json /spots/kopa/cycle/stages[4]/talks[1]/levels/A2/lines[0]: "Danes je kopa mirna. Dim gre lepo naravnost gor." → "Danes je kopa mirna. Dim se lepo dviga."
- cultures/primorska/world.json /spots/kopa/cycle/stages[5]/talks[1]/levels/A1/lines[2]: "Vidiš? Iz luknje gre rumen dim." → "Vidiš? Dim je rumen."
- cultures/primorska/world.json /spots/kopa/cycle/stages[7]/talks[1]/levels/A1/lines[0]: "Kopa je tiha. Ni več dima." → "Kopa je tiha. Skoraj ni več dima."
- cultures/primorska/world.json /spots/kopa/cycle/winter/talks[0]/levels/A2/lines[0]: "Pozimi v gozdu ne delam kope. Sneg je globok, zemlja zmrznjena." → "Pozimi v gozdu ne delam kope. Zemlja je trda in zmrznjena."
- cultures/primorska/world.json /spots/kopa/cycle/winter/talks[1]/levels/A1/lines[0]: "Mrzlo je. Pridi k ognju." → "Mrzlo je. Pridi bliže."
- scenes/kuhinja.json /variants[2]/lines[0]: "Dobro jutro, {learner}! Kava je že na mizi. Kako si {m:spal|f:spala}?" → "Dobro jutro, {learner}! Kava je že skuhana. Kako si {m:spal|f:spala}?"
- scenes/kuhinja.json /variants[5]/lines[3]/choices[1]/reply: "Že? Kje? Miza je še prazna!" → "Že? Kdaj pa? Saj si šele {m:prišel|f:prišla}!"
- scenes/kuhinja.json /variants[6]/lines[1]/choices[1]/reply: "Jutro? Zunaj je že tema!" → "Jutro? Saj je že večer!"
- scenes/kuhinja.json /variants[6]/lines[2]: "Olupke v vedro, krompir v skledo. Zdaj pa poglej na tehtnico: koliko sva {m:olupila|f:olupili}?" → "Olupke v vedro, krompir v skledo. Zdaj pa povej: koliko sva {m:olupila|f:olupili}?"
- scenes/na-njivi.json /variants[0]/lines[1]/choices[0]/reply: "Koso? Ne, kosa je tukaj, v moji roki!" → "Koso? Ne, kosa je tukaj, pri ograji!"
- scenes/na-njivi.json /variants[0]/lines[3]/choices[2]/reply: "Prav! Glej: slama okrog snopa, pa trdno zavežeš." → "Prav! Takole: slama okrog snopa, pa trdno zavežeš."
- scenes/na-njivi.json /variants[1]/lines[0]: "Dober dan, {learner}! Žanjem, dokler je lepo. Poglej nebo: so kje oblaki?" → "Dober dan, {learner}! Žanjem, dokler je lepo. Poglej nebo: so kje temni oblaki?"
- scenes/na-njivi.json /variants[1]/lines[1]/choices[0]: "Ne, ne vidim oblakov. Nebo je modro." → "Ne, ne vidim temnih oblakov. Nebo je modro."
- scenes/na-njivi.json /variants[1]/lines[1]/choices[1]: "Ne, ne vidim oblaki. Nebo je modro." → "Ne, ne vidim temni oblaki. Nebo je modro."
- scenes/na-njivi.json /variants[1]/lines[1]/choices[1]/why: "None" → "None"
- scenes/na-njivi.json /variants[1]/lines[1]/choices[2]: "Ja, ne vidim oblakov. Nebo je modro." → "Ja, ne vidim temnih oblakov. Nebo je modro."
- scenes/na-njivi.json /variants[3]/lines[1]/choices[1]/reply: "Nič hudega. Glej: motika v zemljo, pa gor!" → "Nič hudega. Takole: motika v zemljo, pa gor!"
- scenes/na-njivi.json /variants[6]/lines[4]: "Tam je, za hlevom! Sivko, ti stari lenuh. Hvala, {learner}." → "Tam je, na travniku! Sivko, ti stari lenuh. Hvala, {learner}."
- scenes/na-njivi.json /variants[7]/lines[0]: "Dobro jutro, {learner}! Sonce še spi, midva pa že kosiva. Ha, zehaš! Si {m:zaspan|f:zaspana}?" → "Dobro jutro, {learner}! Vsi še spijo, midva pa že kosiva. Ha, zehaš! Si {m:zaspan|f:zaspana}?"
- scenes/na-njivi.json /variants[7]/lines[3]/choices[1]/reply: "Tako je! Še malo, pa sonce vzide." → "Tako je! Še malo, pa bo zajtrk."
- scenes/na-stolpu.json /dialogs[0]/lines[5]/choices[0]/reply: "Tuuu! Slišijo nas. Tone že stoji pri vratih s kladivom." → "Tuuu! Slišijo nas. Tone bo takoj pri vratih s kladivom."
- scenes/na-stolpu.json /variants[4]/lines[0]: "{learner}, glej čez dolino! Tam že dežuje. Nevihta gre k nam. Te je strah?" → "{learner}, glej čez dolino! Tam so črni oblaki. Nevihta gre k nam. Te je strah?"
- scenes/na-trznici.json /variants[2]/lines[1]/choices[2]/reply: "Večer? Ne, ne, {learner}, sonce je še visoko!" → "Večer? Ne, ne, {learner}, saj je še dan!"
- scenes/na-trznici.json /variants[3]/lines[3]/choices[0]: "S vozom! Tam stoji prazen voz." → "S vozom! Tam stoji voz."
- scenes/na-trznici.json /variants[3]/lines[3]/choices[1]: "Z vozom! Tam stoji prazen voz." → "Z vozom! Tam stoji voz."
- scenes/na-trznici.json /variants[3]/lines[3]/choices[2]: "Z voz! Tam stoji prazen voz." → "Z voz! Tam stoji voz."
- scenes/na-trznici.json /variants[4]/lines[2]: "Glej, kupec! Vpraša, koliko stane steklenica. Cena je na tabli." → "Glej, kupec! Vpraša, koliko stane steklenica. Povej mu ceno!"
- scenes/na-trznici.json /variants[4]/lines[3]/choices[0]/reply: "Kaj? Kupec se smeje. Poglej še enkrat tablo!" → "Kaj? Kupec se smeje. Poskusi še enkrat!"
- scenes/na-vasi.json /dialogs[4]/lines[5]/choices[2]/reply: "Ni lačna? Poglej, pije vse mleko!" → "Ni lačna? Ha, mleko bo pa vseeno popila!"
- scenes/ob-ognju.json /dialogs[0]/lines[1]/choices[1]/reply: "Jutro? {m:Fant|f:Dekle}, sonce je že šlo spat!" → "Jutro? {m:Fant|f:Dekle}, saj je že večer!"
- scenes/ob-ognju.json /variants[5]/lines[2]: "Ampak burja je dobra. Ni megle, vidiš daleč. Poglej!" → "Ampak burja je dobra. Odpiha vso meglo."
- scenes/ob-ognju.json /variants[5]/lines[3]/choices[0]: "Res! Vidim do morja!" → "Res! Ob burji se vidi do morja!"
- scenes/ob-ognju.json /variants[5]/lines[3]/choices[0]/reply: "Ja! Tisto modro tam je morje." → "Ja! Vse do Trsta!"
- scenes/ob-ognju.json /variants[5]/lines[3]/choices[1]: "Res! Vidim do morje!" → "Res! Ob burji se vidi do morje!"
- scenes/ob-potoku.json /dialogs[1]/lines[4]: "Vidiš tisto skalo? Tam bo stalo kolo. Voda ga bo vrtela." → "Vidiš tisto mesto pri gozdu? Tam bo stalo kolo. Voda ga bo vrtela."
- scenes/ob-potoku.json /dialogs[1]/lines[5]/choices[1]/reply: "Kje? Tam, pri tisti skali. Saj sem že rekel!" → "Kje? Tam, pri gozdu. Saj sem že rekel!"
- scenes/ob-potoku.json /dialogs[5]/lines[3]/choices[1]/reply: "Je? Ena sama? Jaz jih vidim sedem. Sedem!" → "Je? Ena sama? V potoku jih je veliko!"
- scenes/ob-potoku.json /dialogs[5]/lines[6]: "Sonce! Megla gre. Pojdiva na zajtrk." → "Megla gre. Pojdiva na zajtrk."
- scenes/ob-potoku.json /variants[9]/lines[4]: "Sonce! Megla gre, potok je spet tu." → "Megla gre, potok je spet tu."
- scenes/ob-potoku.json /variants[10]/lines[4]: "Glej, sonce. Megla gre. Pa povodni mož z njo!" → "Glej, megla gre. Pa povodni mož z njo!"
- scenes/pri-ribniku.json /dialogs[0]/lines[4]: "Poglejte, sonce. Megla se dviga." → "Poglejte, megla se dviga."
- scenes/pri-ribniku.json /dialogs[1]/lines[7]/choices[1]/reply: "Premrzla? Ha ha, sploh ni! Poglej me!" → "Premrzla? Ha ha, sploh ni! Pridi še ti!"
- scenes/pri-ribniku.json /dialogs[3]/lines[4]: "Glej, sonce. Megla se dviga z vode." → "Glej, megla se dviga z vode."
- scenes/pri-ribniku.json /variants[5]/lines[2]: "Koliko je ura? Sonce še ni gor." → "Koliko je ura? Še zgodaj je."
- scenes/pri-ribniku.json /variants[5]/lines[4]: "Glej, sonce. Zdaj bodo prijele." → "Glej, megla gre. Zdaj bodo prijele."
- scenes/v-cerkvi.json /variants[0]/lines[1]/choices[1]/reply: "{m:Priden fant|f:Pridno dekle}! Tam pri vratih je vrč." → "{m:Priden fant|f:Pridno dekle}! Vrč je zunaj, pri vratih."
- scenes/v-gozdu.json /dialogs[3]/lines[4]: "Tema je. Pojdiva nazaj k ognju." → "Pozno je. Pojdiva nazaj k ognju."
- scenes/v-gozdu.json /dialogs[5]/lines[6]: "Sonce je tu, megla gre. Hvala, da si {m:bil|f:bila} tiho. Lovski blagor!" → "Jutro je tu, megla gre. Hvala, da si {m:bil|f:bila} tiho. Lovski blagor!"
- scenes/v-gozdu.json /dialogs[6]/lines[5]/choices[1]/reply: "Netopirja? Samo dva? Jaz jih vidim deset." → "Netopirja? Samo dva? Jaz jih vidim štiri."
- scenes/v-gozdu.json /variants[4]/lines[2]: "Poglej naše lončke! Kdo ima več, ti ali mi?" → "Naši lončki so polni! Kdo ima več, ti ali mi?"
- scenes/v-gozdu.json /variants[8]/lines[4]: "Sonce je tu. Srna in mladič gresta v gozd. Lovski blagor!" → "Jutro je tu. Srna in mladič gresta v gozd. Lovski blagor!"
- scenes/v-hisi.json /dialogs[1]/lines[4]: "Glej, lovi svoj rep! Muri, ti si pa čuden." → "Glej, kako miga z repom! Muri, ti si pa čuden."
- scenes/v-hisi.json /variants[3]/lines[0]: "{learner}, glej! Tukaj je bila klobasa. Zdaj je krožnik prazen! Kdo je bil?" → "{learner}, glej! Tukaj je bila klobasa. Zdaj je ni več! Kdo je bil?"
- scenes/v-hisi.json /variants[4]/lines[0]: "Poglej to fotografijo. To sem jaz, na Triglavu. Takrat sem bil mlad in močan." → "V knjigi imam staro fotografijo. To sem jaz, na Triglavu. Takrat sem bil mlad in močan."
- scenes/v-kleti.json /variants[3]/lines[0]: "Ciao, {learner}! Glej ta nož. Z njim režem pršut tanko kot papir." → "Ciao, {learner}! Imam nov nož. Z njim režem pršut tanko kot papir."
- scenes/v-soli.json /dialogs[4]/lines[4]: "Zdaj tu uči Mojca. Dobra je. Brez palice. Pojdiva, tema je." → "Zdaj tu uči Mojca. Dobra je. Brez palice. Pojdiva, pozno je."
- scenes/v-sotoru.json /dialogs[0]/lines[0]: "{learner}! Dober večer. Poglej, svetilko sem ti prinesel. V šotoru je tema." → "{learner}! Dober večer. Poglej, svetilko sem ti prinesel. Ponoči je v šotoru tema."
- scenes/v-sotoru.json /dialogs[0]/lines[1]/choices[0]/reply: "Tako. Zdaj vidiš." → "Tako, zdaj visi."
- scenes/v-sotoru.json /variants[1]/lines[0]: "{learner}! Dober večer. Poglej, dve svetilki! Eno dam tebi." → "{learner}! Dober večer. Imam dve svetilki! Eno dam tebi."
- scenes/v-sotoru.json /variants[4]/lines[0]: "{learner}! Spet iščem kapo! Aha, tukaj je! … Hm, ta je modra." → "{learner}! Spet iščem kapo! Aha, tukaj je! … Hm, ta je rjava."
- scenes/v-sotoru.json /variants[4]/lines[1]/choices[0]/reply: "Aha! Tvoja je modra, moja rdeča." → "Aha! Tvoja je rjava, moja rdeča."

Tap counts: no screen or navigation changed (the same taps to every scene, step and dialog).
