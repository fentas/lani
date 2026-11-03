# The Lake District at B1 and B2: the scenes' dialogs by level

Wave E, batch 2a, `dialogs-lakeland` (followups-15.md). Base 6e364137 on `dialog-levels` (the system, without its model
content), so the market (`at-the-market`, the model) is left to `dialog-levels`. Every other Lake District scene, 32 of
them, now has two B1 and two B2 variants on its grown-ups' happenings, written to the writers' guide
(docs/plans/05-built/dialog-levels.md at 5fd43a08, "Writers' guide"): **128 dialogs** (64 B1, 64 B2), 802 lines, 256
turns, 512 choices, in English with Slovene, Italian and German, and 205 words for the supplement. Content only: no code,
no test, no grammar page changed.

## Native check

Everything below is machine-written (Claude) and reviewed by nobody. The review line of every scene says so
("…not native-checked; B1, B2 variants too").

**English (British, Cumbrian where the culture has it; a native speaker first):**
- The region's words, all already in the culture's content: beck, fell, tarn, dale, "Now then", "Aye", "love", "lad/lass",
  "mate", "daft", "Herdwick". New here: **heafed** ("They're heafed, mind. Every ewe knows her own bit of fell",
  `sheep-b2-heaf`; the Cumbrian word for hefted, as the Herdwick breeders use it: please confirm "heafed" over "hefted" in
  Jack's mouth), **"our Harry"** (Mrs Hartley, `bubbles-b1-wand`), **"like a lemon"**, **"full as an egg"** (Jack),
  **"run rings round you"**, **"worth your weight in tea"** (Mrs Birkett's play on gold), **"That's the fells for you"**,
  **"ta"** and **"Oi"** (Sam's note and the rude letter, `wheel-b2-note`).
- **Every wrong choice must be a learner's mistake that no grown-up says** (followups-12.md, "What people say"). None of
  the forms in grading/en.json's `nonstandard` is offered as wrong (was/were, seen/done, have went, was broke, what as a
  relative, hisself, use to, innit and isn't it for every tag, would of, asked did I and asked where was it, to who, the
  both, neither … or). The ones to read most carefully: «He'll be cold up there, doesn't he?» (`watch-b1-lights`), «Do you
  enjoy in winter?» (enjoy with nothing after it, `winter-b1-harry`), «The cottage in that you were born?»
  (`tea-b2-cottage`), «Two brothers, both of which stayed» (which for people, `bench-b2-brothers`), «most of that»
  (`lemonade-b2-bottles`, `evening-b2-dipper`), «Despite to be kicked» (`horseshoe-b1-kick`), «It's too long ten
  centimetres» (`saw-b1-fingers`), «They suggested us to oil it» (the page says never), «I'd have the roof mend»,
  «I'd have looked at the engine» (have it done against the past conditional, `tractor-b2-engine`), «make a seat» (meaning
  building one, `highseat-b2-stags`). The whole list is below, by page.
- Facts, settled from what is widely published; please check: Wordsworth saw the daffodils by Ullswater in April 1802
  (Dorothy Wordsworth's journal), the poem dates from 1804; Thirlmere was dammed for Manchester in the 1890s, its
  aqueduct nearly a hundred miles long, a few farms under the water; heather honey sets like jelly and runs when stirred;
  bees find their way by the sun and the land's shapes; honey found in Egyptian tombs still good; Herdwicks keep to their
  own heaf; a farrier reshoes a pony about every six weeks; six whistle blasts a minute is the mountain distress signal;
  the dipper walks under water; old climbing ropes were hemp; a hay meadow is cut late, once the seeds have dropped; yellow
  rattle keeps the grass down. Places named: Catbells, Great Gable, Skiddaw, Blencathra, Ullswater, Thirlmere,
  Derwentwater, Keswick, Penrith, Carlisle Cathedral, Innsbruck and Tyrol.

**Slovene, Italian, German** (the translations, the whys, the memories): the register follows the existing Lake District
scenes, not the model: the learner says **Vi / Lei / Sie** to Mrs Birkett, Mrs Hartley, Mr Dixon and Mr Tyson (they say
ti / tu / du back) and **ti / tu / du** with everyone else. (The model's `inn-b2-jam` had the learner say "Nisi mogla
vedeti" and "Das konntest du nicht wissen" to Mrs Birkett; after the merge of `dialog-levels` it says Vi, Lei and Sie too.) Slovene with a woman addressed as vi uses the
plural ("boste spekli"). The learner's forms are gender pairs in Slovene and Italian ({m:…|f:…}); a Slovene speaker
should read the duals ("bova uporabila/uporabili", "narediva sama/sami") and the Italian participles of the learner.
Translations of a wrong English choice say what the learner meant.

### Every wrong choice, by page

- **(understanding)**: «So Manchester never got its water in the end.» (map-b2-thirlmere); «So you never came here with her.» (bench-b2-yew); «So Sam brewed far too much in the spring.» (jug-b2-tea)
- **collocations**: «You must have done a huge effort.» (bell-b2-chapel); «I'll help you make the washing up.» (guests-b2-breakfast); «I'll make a photo of them for her.» (honey-b2-heather); «Let's do a rest on the wall, then.» (view-b2-scafell); «Why don't you do a start this autumn?» (flowers-b2-rattle); «Thanks. I'll make a seat on the step.» (highseat-b2-stags); «Just make your best, and measure twice.» (measure-b2-ten)
- **contrast-linkers**: «Although it's late, but they're probably cleaning the hive.» (evening-b1-dark); «Despite it's cold, I'd drink it every day.» (water-b1-walker); «He speaks slowly for that everyone can follow.» (hot-chocolate-b1-harry); «No. In spite the rain, I enjoyed the walk.» (tea-b1-rain); «Despite to be kicked, you never give up on him.» (horseshoe-b1-kick)
- **emphasis**: «What I like most it is the quiet.» (bench-b2-yew); «I do likes them! I just can't blow big ones.» (bubbles-b2-sugar); «I did hated it when I first came. Now I love the fire.» (hot-chocolate-b2-dark); «Never I have heard such a fuss!» (organ-b2-fuss); «So the old miller learnt it from Mr Tyson.» (multure-b2-honest); «Not only you shoe every pony, you make the gates too!» (horseshoe-b2-gates); «Rarely I have seen anything so beautiful.» (sunset-b2-rarely); «So the children cracked the pipe.» (pump-b2-frost); «It's the music who I'll remember most.» (bench-b2-annie)
- **for-since**: «I've lived here since a few months now.» (bench-b1-lid); «I haven't blown bubbles for I was a child.» (bubbles-b1-wand); «Since as long as I can remember!» (hot-chocolate-b1-recipe); «I've been here since three months.» (radio-b1-forecast); «Since ten minutes. It's hard to stop!» (grind-b1-notebook); «He has it for twenty years, then.» (wheel-b1-wheelwright)
- **formal-informal**: «Yours sincerely, since you don't know their name.» (bar-b2-letter); «Send your kid with a coat, yeah?» (hopscotch-b2-letter); «Hi Mr Fletcher, thanks for the letter. Room's ready!» (chest-b2-letter); «Oi, Dixon, fix the wheel by tomorrow, yeah?» (wheel-b2-note); «Fancy coming along, then?» (time-b2-twenty)
- **future-continuous**: «I'll be help you. I'd like to see how you do it.» (evening-b1-dark); «I'll working, but I'll be thinking of the heron.» (heron-b1-poppy); «Will you be take it to Penrith yourself?» (cradle-b1-cousin); «I'll be help you all evening, if you like.» (jug-b1-biscuits); «Will you sitting on this wall again, telling us what to do?» (view-b1-winter); «I'll be cheer for you all afternoon!» (lemonade-b1-match); «Will you leaving early?» (daisy-b1-tyrol); «Yes, I'll walking past at three.» (pump-b1-bucket); «Will you be dance too, Wilf?» (bench-b1-poppy); «Will you running by Derwentwater again?» (time-b1-strokes)
- **future-perfect**: «Four months. By then we'll have use every last oat.» (stores-b2-roof); «You'll be baking the scones and laying all the tables.» (guests-b2-breakfast); «I'll be fine. By the time the snow will go, I'll have finished my book.» (hot-chocolate-b2-snow); «So we'll still be working when it rains.» (hay-b2-rain); «So they'll only start after the snow has gone.» (winter-b2-snowdrops); «You'll have winned all ten by September.» (measure-b2-ten); «By tonight the water will have go down.» (spate-b2-rail); «By next month it'll have striked the hours for twenty years.» (time-b2-twenty)
- **have-it-done**: «I'd have the roof mend before the autumn rain.» (stores-b2-roof); «Did you have tested it by an expert?» (ropes-b2-gable); «I'd have it repair at the smithy.» (cradle-b2-rockers); «I'd have looked at the engine before the picking.» (tractor-b2-engine); «Will you have cut it by someone with a tractor?» (flowers-b2-rattle); «Why not have made a new one at the smithy?» (hammer-b2-neighbour); «Did you have fixed it at the smithy?» (daisy-b2-clapper)
- **might-must**: «There must to be a gap under the door.» (stores-b1-mice); «She might is behind the wall again.» (flock-home-b1-sooty); «She mights be down by the bridge.» (sheep-b1-sooty); «He said it might to rain later.» (radio-b1-forecast); «It must is under the settle. Bess sleeps there.» (fire-b1-poker); «It must weighs less than the others.» (multure-b1-light); «It mights be a walker with a head torch.» (watch-b1-lights)
- **mixed-conditionals**: «Yes, he did. That's why he's up on the ridge now.» (walkers-b2-boots); «So she's only just got used to you.» (heron-b2-forty); «So nobody in the village ever ate her baking.» (candle-b2-kindness); «So you're still a bachelor today.» (bench-b2-annie)
- **narrative-tenses**: «You've been sitting up with the bees all night.» (hives-b2-heather); «So the rock gave way before they started climbing.» (ropes-b2-gable); «So she started waiting when the snow began.» (chest-b2-letter); «So you sat here first, then climbed Scafell.» (view-b2-scafell); «So she only started eating when you arrived.» (ewe-b2-ivy); «Then he'd been knowing him longer than anyone.» (lemonade-b2-bottles); «You'd been knowing it for fifty years, and it still surprised you.» (spate-b2-rail)
- **participle-clauses**: «Sit by the window, I listen to the birds.» (evening-b2-father); «Came down the fell, I heard the water before I saw the lion.» (water-b2-springs); «Have spent all day on the fell, they must be thirsty.» (sheep-b2-heaf); «Listen to you, I've already learnt three of them.» (dusk-b2-yan); «Lived near a lake, we always burn citronella candles.» (evening-b2-midges); «Don't knowing the fells well, I stay in the valley and walk by the lake.» (winter-b2-snowdrops); «Heard her bell, they know where to go.» (daisy-b2-clapper); «So the hens were standing up here.» (watch-b2-fox); «Arrived late, they started playing at once.» (band-b2-trumpet)
- **passive**: «I think it was writing two years later, in 1804.» (map-b1-daffodils); «The old ones were make of hemp, weren't they?» (ropes-b1-hemp); «They're clean by Harry, aren't they?» (guests-b1-fog); «I think it's look after by the whole village.» (trout-b1-stones); «Is it make with milk and a bit of cinnamon?» (hot-chocolate-b1-recipe); «Did it make in the Alps?» (daisy-b1-tyrol); «Was it build before the bridge, then?» (village-b1-door); «Are they pay to play here?» (band-b1-practise)
- **passive-more**: «It looks like the path is repairing.» (walk-b2-path); «So the nest is being mend again right now.» (heron-b2-forty); «It has blocked by a fallen tree.» (radio-b2-pass); «It needs to sweep, and soon.» (fire-b2-chimney); «Ten sacks will have ground by six.» (grind-b2-fine); «So they're painting this week, then.» (horseshoe-b2-gates); «It looks as if it's repaired since then.» (village-b2-roofs)
- **past-modals**: «Fair or not, the farmers can't have be happy about it.» (map-b2-thirlmere); «He must have told someone where he was going.» (alarm-b2-whistle); «I was working. I could come if I'd known.» (sunset-b2-skiddaw); «The dry summers might had warmed the water too much.» (trout-b2-seven); «You could waited till the afternoon.» (rain-b2-alfie); «She must squeezed through the gap in the wall.» (ewe-b2-ivy); «You must read the tape wrong.» (saw-b2-tape)
- **past-perfect**: «Maybe someone had pull it too hard the week before.» (bell-b1-wedding); «It had get away before Bess reached it.» (fishing-b1-bear); «Because she had teach you everything before you were ten.» (candle-b1-scones); «They have dug another one, I bet.» (dusk-b1-rabbits); «You'd leaved the door open, I bet.» (evening-b1-door); «So they got out only after you arrived.» (hammer-b1-cows); «Had the sun already go down?» (sunset-b1-late)
- **phrasal-verbs**: «Don't worry, I'll pick up it.» (bench-b1-lid); «Of course. I'll look her after.» (flock-home-b1-sooty); «Let's take her home and dry off her.» (sheep-b1-sooty); «I'll look it for. Is it the blue one?» (tatie-pot-b1-glove); «Shall I turn off it for you?» (fire-b1-poker); «Of course. I'll fill in it for you.» (grind-b1-notebook); «I'll look it after, if you teach me.» (gears-b1-oil)
- **phrasal-verbs-more**: «Oh no. Had you run out petrol?» (hives-b2-heather); «You'll keep up it, I'm sure. You run up fells for fun!» (flock-home-b2-mist); «Tell them they'll have to put up tea till Friday.» (jug-b2-tea); «We'll come up something. Sam's got a cart.» (tractor-b2-engine); «I'm looking forward to learn the rest.» (dusk-b2-yan)
- **present-perfect-continuous**: «I'm sorting the hay since ten o'clock.» (walk-b1-barn); «I've been come here for a few weeks.» (ropes-b1-hemp); «He's been play the same hymn for an hour!» (organ-b1-organist); «I walk on the fell since breakfast.» (tatie-pot-b1-glove); «He's working since dawn, so he deserves it.» (multure-b1-light); «I've been listen for a few minutes.» (evening-b1-bell); «I've been waited since half past three.» (time-b1-strokes)
- **quantifiers**: «Tell him one of the springs is muddy, then.» (water-b2-springs); «Only the second one, since the first has no sugar.» (bubbles-b2-sugar); «So one of the rockers is still the original.» (cradle-b2-rockers); «Neither will do. They both look fine to me.» (hay-b2-rain); «Every of them is after us!» (evening-b2-midges); «So one of them has spotted us.» (highseat-b2-stags); «So none of them have their fires lit.» (village-b2-roofs); «Then you'll only need one new one.» (gears-b2-springs)
- **question-tags**: «The last one's at six, doesn't it?» (walkers-b1-bus); «It won't rain today, won't it?» (hopscotch-b1-squares); «You come up here every evening, aren't you?» (sunset-b1-pink); «Honey lasts for years, didn't it?» (honey-b1-hive); «They're terrible tonight, don't they?» (evening-b1-door); «She's never been lost this long, hasn't she?» (ewe-b1-poppy); «So you've won, didn't you?» (measure-b1-wilf); «That's a dipper, doesn't it?» (evening-b1-bell); «He'll be cold up there, doesn't he?» (watch-b1-lights)
- **reflexive-pronouns**: «They keep them warm by shivering together.» (hives-b1-home); «We look after ourselves, so nobody gets lost.» (alarm-b1-four); «No need. We can do it us this afternoon.» (cradle-b1-cousin); «Thank you, I'll help me.» (jug-b1-biscuits); «By him? He's only six!» (tractor-b1-alfie); «Do you enjoy in winter, without the bees?» (winter-b1-harry); «Oh no. Did you hurt you badly?» (saw-b1-fingers)
- **relative-clauses**: «Ask someone which can swim, just in case.» (board-b1-trip); «Yes! I'd love to walk along the shore which they grow.» (map-b1-daffodils); «We call the man who he has the radio.» (alarm-b1-four); «Is this the spoon that she used it every day?» (chest-b1-spoon); «Is that the oak who Harry climbs every day?» (bench-b1-oak); «It's the house who has the blue door.» (village-b1-door)
- **relative-clauses-more**: «The chapel that its roof fell in? I've walked past it.» (bell-b2-chapel); «Your father, who his notebook you still read, would be proud of you.» (trout-b2-seven); «The cottage in that you were born? Right here?» (tea-b2-cottage); «The players, most of that were thirsty after the game?» (lemonade-b2-bottles); «So Meg's stuck under your boots.» (sunset-b2-rarely); «Two brothers, both of which stayed in the dale all their lives?» (bench-b2-brothers); «A few, most of that I couldn't name.» (evening-b2-dipper); «So the clock loses a minute every week.» (gears-b2-springs)
- **reported-questions**: «She asked to me where the bus to Keswick stopped.» (walkers-b1-bus); «She asked herons ate frogs.» (heron-b1-poppy); «He asked to me if he could ride on the cart.» (hay-b1-cart); «He asked to me how often his pony needed new shoes.» (horseshoe-b1-kick); «He asked the bees got cold in winter.» (winter-b1-harry); «He asked he could hold the nails.» (hammer-b1-cows)
- **reported-speech**: «He told to me the water was the best he'd ever tasted.» (water-b1-walker); «He told it was the biggest wand in Cumbria.» (bubbles-b1-wand); «He said I was going to listen to Wilf's story too.» (hot-chocolate-b1-harry); «He told me come back on Sunday at ten.» (organ-b1-organist); «They said us it would be dry all day.» (rain-b1-forecast); «She told me I'd seen a sheep by the beck.» (ewe-b1-poppy); «He told me my bowl was closer.» (measure-b1-wilf); «He told me keep the ponies off the ford.» (spate-b1-ford)
- **reporting-verbs**: «He admitted to make it up.» (board-b2-excuse); «Then you should remind them sending someone.» (bar-b2-letter); «He advised you never selling it.» (sheep-b2-heaf); «Oh dear. Did he apologise to spend so much?» (organ-b2-fuss); «Alfie warned you don't go up.» (rain-b2-alfie); «So he denied to touch it.» (hammer-b2-neighbour); «They suggested us to oil it every week.» (pump-b2-frost); «He insisted to walk the last five miles.» (band-b2-trumpet)
- **result-linkers**: «Probably. It was so good excuse that I'd have believed every word.» (board-b2-excuse); «That was such a bad luck. I'd still be cross with her.» (fishing-b2-pike); «It's so thick honey that it's almost like jelly.» (honey-b2-heather); «The tree is such big that they need a crane.» (radio-b2-pass); «Your oatmeal is fine, whereas the old mill's was fine too.» (grind-b2-fine); «It was so easy mistake that anyone could make it.» (saw-b2-tape)
- **second-conditional**: «If I were you, I'll take the boat. Alfie's legs are still very short!» (board-b1-trip); «If a fox got in, I shout for you.» (bar-b1-oak); «If I would find one, I'd read it, but only with you.» (chest-b1-spoon); «If I have a hive, I'd put it by the wildflower meadow.» (honey-b1-hive); «If it rained, the hay will get wet and spoil.» (hay-b1-cart); «If I can, I'd learn to play bowls.» (lemonade-b1-match)
- **third-conditional**: «I would told her that they follow the sun.» (hives-b1-home); «So you only found him in the morning.» (alarm-b2-whistle); «They will have seen the island in the middle of the lake.» (guests-b1-fog); «So nobody saw the sunset in the end.» (sunset-b2-skiddaw); «I had planted new ones the next spring.» (view-b1-winter); «If you'd mended it, they wouldn't got in.» (dusk-b1-rabbits); «So the farmer kept spraying, and the flowers died.» (flowers-b1-names); «If you'd stayed down, you won't have seen this.» (sunset-b1-late); «If you hadn't oiled it, it will have stopped.» (gears-b1-oil)
- **too-enough**: «The door isn't enough tight to keep the mice out.» (stores-b1-mice); «No, it's too much heavy for me to lift alone.» (bar-b1-oak); «It's too cold for staying long, but I want to see it.» (sunset-b1-pink); «No, there isn't enough of room for seven.» (tractor-b1-alfie); «They're too far away for see clearly.» (highseat-b1-deer); «It's too long ten centimetres.» (saw-b1-fingers); «It's too heavy, but Harry could carry it.» (pump-b1-bucket); «No, the water's too deep that they cross.» (spate-b1-ford)
- **used-to**: «No, but my grandparents were used to have a big wooden barn.» (walk-b1-barn); «Yes, we used to had a big black dog called Bear.» (fishing-b1-bear); «I used to took two, but now I don't take any.» (tea-b1-rain); «There was used to be a wheelwright in every village, didn't there?» (wheel-b1-wheelwright); «Yes, my grandparents used to danced like this.» (bench-b1-poppy)
- **verb-patterns**: «Yes, I enjoy to ring them, but never at a wedding.» (bell-b1-wedding); «I'd suggest to draw two: one with eight, one with ten.» (hopscotch-b1-squares); «Of course. I'll avoid to step on the bank.» (trout-b1-stones); «Yes. I promised lighting one for my grandmother every year.» (candle-b1-scones); «I don't mind to share with a wet dog.» (rain-b1-forecast); «Yes! I've decided learning one flower a day.» (flowers-b1-names); «Yes, let's stay. I don't want missing them.» (highseat-b1-deer); «He keeps to climb it anyway.» (bench-b1-oak); «I'd love to, but I need to practise to dance first.» (band-b1-practise)
- **wish-if-only**: «Me too. I wish I have more time for things like that.» (walk-b2-path); «I know. I wish it will lift before the morning, for your sake.» (flock-home-b2-mist); «If only we knew sooner!» (hot-chocolate-b2-snow); «Poor man. I wish she stops barking at him.» (tea-b2-cottage); «I wish he has brought it in last week, then.» (wheel-b2-note); «If only I am a dipper!» (evening-b2-dipper); «If only you have had the torch ready!» (watch-b2-fox)
- **word-building**: «That's really unresponsible. The ridge is dangerous when it's windy.» (walkers-b2-boots); «Discomfortable.» (hopscotch-b2-letter); «Kindity.» (candle-b2-kindness); «Unpatient.» (tatie-pot-b2-patience); «Unhonest.» (multure-b2-honest)
- **would-used-to**: «We would live by a lake, and we'd swim in it every evening.» (evening-b2-father); «Not at all. I'm used to wait.» (fishing-b2-pike); «Not yet. I don't used to the dark at four o'clock.» (hot-chocolate-b2-dark); «Yes. My mother would to make soup every Monday with the leftovers.» (tatie-pot-b2-patience); «We would have a stove, and my father would light it at six.» (fire-b2-chimney); «On Sundays you would played bowls, I suppose.» (bench-b2-brothers)

## What was written

Per scene, one B1 and one B2 variant on each of two grown-ups' happenings that carry a conversation (Mr Tyson, Mr Dixon,
Mrs Birkett, Mrs Hartley, Granny Maggie, Auntie Edna, Old Wilf, Jack, Sam, Arthur); the children's games, the I spy, the
guests' first visit and the storyteller's evening stay as they were. **By the fire** has one grown-ups' happening besides
the storyteller's (Granny Maggie's hot chocolate): its two B1 and two B2 are all on it. Ids `<happening>-b1-<topic>` and
`<happening>-b2-<topic>`, in the scene's `variants`; in the happening's `dialogs` the B2 ones first, then the B1, then the
ones of before; its `dialog` (what an older app plays) unchanged, no level on any happening. Each B1 is 6–7 lines with
two turns, each B2 6–7 lines with two turns; the happening's person speaks first and last and answers every turn (no
`to` needed). Every wrong choice has its why (with its page, or in meaning words where a B2 turn tests understanding) and
a reaction of at most 80 characters that doesn't say the right form; two choices never share a reply; a variant names
only people of its scene (a happening whose texts name someone is on only while they are in the village: Harry, Poppy,
Alfie, Sam, Maggie and Wilf where their scene has them).

The B1 lines leave the here and now (what someone said, what would happen if, plans, a small problem to solve); the B2
ones weigh, regret and speculate, shift register (a letter to the council, an invitation to the vicar) and leave more
to the learner (a turn whose right answer follows only from understanding a mixed or past conditional, a which-clause, a
cleft, neither/either). All 36 B1 and B2 pages are named, each 4 to 10 times:

- B1: contrast-linkers 5, for-since 6, future-continuous 10, might-must 7, passive 8, past-perfect 7, phrasal-verbs 7, present-perfect-continuous 7, question-tags 9, reflexive-pronouns 7, relative-clauses 6, reported-questions 6, reported-speech 8, second-conditional 6, third-conditional 9, too-enough 8, used-to 5, verb-patterns 9
- B2: collocations 7, emphasis 9, formal-informal 5, future-perfect 8, have-it-done 7, mixed-conditionals 4, narrative-tenses 7, participle-clauses 9, passive-more 7, past-modals 7, phrasal-verbs-more 5, quantifiers 8, relative-clauses-more 8, reporting-verbs 8, result-linkers 6, wish-if-only 7, word-building 5, would-used-to 6

The variants, scene by scene (the turns' pages; "understanding": a B2 turn whose wrong choice misreads the line):

| Scene | Happening | B2: its turns' pages | B1: its turns' pages |
|---|---|---|---|
| `at-school` | `board` (Mrs Hartley) | `board-b2-excuse`: reporting-verbs, result-linkers | `board-b1-trip`: second-conditional, relative-clauses |
| `at-school` | `map` (Mrs Hartley) | `map-b2-thirlmere`: past-modals, understanding | `map-b1-daffodils`: passive, relative-clauses |
| `at-the-bank-barn` | `stores` (Mr Tyson) | `stores-b2-roof`: future-perfect, have-it-done | `stores-b1-mice`: might-must, too-enough |
| `at-the-bank-barn` | `walk` (Brewer Sam) | `walk-b2-path`: passive-more, wish-if-only | `walk-b1-barn`: present-perfect-continuous, used-to |
| `at-the-bees` | `hives` (Beekeeper Arthur) | `hives-b2-heather`: phrasal-verbs-more, narrative-tenses | `hives-b1-home`: third-conditional, reflexive-pronouns |
| `at-the-bees` | `evening` (Beekeeper Arthur) | `evening-b2-father`: would-used-to, participle-clauses | `evening-b1-dark`: contrast-linkers, future-continuous |
| `at-the-chapel` | `bell` (Mr Dixon) | `bell-b2-chapel`: relative-clauses-more, collocations | `bell-b1-wedding`: verb-patterns, past-perfect |
| `at-the-chapel` | `bench` (Granny Maggie) | `bench-b2-yew`: emphasis, understanding | `bench-b1-lid`: for-since, phrasal-verbs |
| `at-the-fountain` | `water` (Mr Dixon) | `water-b2-springs`: quantifiers, participle-clauses | `water-b1-walker`: reported-speech, contrast-linkers |
| `at-the-fountain` | `walkers` (Brewer Sam) | `walkers-b2-boots`: word-building, mixed-conditionals | `walkers-b1-bus`: reported-questions, question-tags |
| `at-the-gate` | `flock-home` (Shepherd Jack) | `flock-home-b2-mist`: wish-if-only, phrasal-verbs-more | `flock-home-b1-sooty`: might-must, phrasal-verbs |
| `at-the-gate` | `bar` (Mr Dixon) | `bar-b2-letter`: formal-informal, reporting-verbs | `bar-b1-oak`: second-conditional, too-enough |
| `at-the-playground` | `hopscotch` (Mrs Hartley) | `hopscotch-b2-letter`: formal-informal, word-building | `hopscotch-b1-squares`: verb-patterns, question-tags |
| `at-the-playground` | `bubbles` (Mrs Hartley) | `bubbles-b2-sugar`: quantifiers, emphasis | `bubbles-b1-wand`: reported-speech, for-since |
| `at-the-rescue-hut` | `ropes` (Mr Dixon) | `ropes-b2-gable`: narrative-tenses, have-it-done | `ropes-b1-hemp`: passive, present-perfect-continuous |
| `at-the-rescue-hut` | `alarm` (Mr Tyson) | `alarm-b2-whistle`: past-modals, third-conditional | `alarm-b1-four`: relative-clauses, reflexive-pronouns |
| `at-the-tarn` | `heron` (Beekeeper Arthur) | `heron-b2-forty`: mixed-conditionals, passive-more | `heron-b1-poppy`: reported-questions, future-continuous |
| `at-the-tarn` | `fishing` (Old Wilf) | `fishing-b2-pike`: result-linkers, would-used-to | `fishing-b1-bear`: used-to, past-perfect |
| `at-the-viewing-station` | `guests` (Mrs Birkett) | `guests-b2-breakfast`: future-perfect, collocations | `guests-b1-fog`: third-conditional, passive |
| `at-the-viewing-station` | `sunset` (Shepherd Jack) | `sunset-b2-skiddaw`: past-modals, third-conditional | `sunset-b1-pink`: too-enough, question-tags |
| `by-the-beck` | `sheep` (Shepherd Jack) | `sheep-b2-heaf`: participle-clauses, reporting-verbs | `sheep-b1-sooty`: might-must, phrasal-verbs |
| `by-the-beck` | `trout` (Mr Tyson) | `trout-b2-seven`: relative-clauses-more, past-modals | `trout-b1-stones`: verb-patterns, passive |
| `by-the-fire` | `hot-chocolate` (Granny Maggie) | `hot-chocolate-b2-snow`: future-perfect, wish-if-only; `hot-chocolate-b2-dark`: would-used-to, emphasis | `hot-chocolate-b1-recipe`: passive, for-since; `hot-chocolate-b1-harry`: reported-speech, contrast-linkers |
| `in-church` | `organ` (Auntie Edna) | `organ-b2-fuss`: reporting-verbs, emphasis | `organ-b1-organist`: reported-speech, present-perfect-continuous |
| `in-church` | `candle` (Granny Maggie) | `candle-b2-kindness`: word-building, mixed-conditionals | `candle-b1-scones`: verb-patterns, past-perfect |
| `in-the-attic` | `cradle` (Mrs Birkett) | `cradle-b2-rockers`: have-it-done, quantifiers | `cradle-b1-cousin`: future-continuous, reflexive-pronouns |
| `in-the-attic` | `chest` (Mrs Birkett) | `chest-b2-letter`: formal-informal, narrative-tenses | `chest-b1-spoon`: second-conditional, relative-clauses |
| `in-the-cellar` | `jug` (Mrs Birkett) | `jug-b2-tea`: phrasal-verbs-more, understanding | `jug-b1-biscuits`: future-continuous, reflexive-pronouns |
| `in-the-cellar` | `honey` (Beekeeper Arthur) | `honey-b2-heather`: result-linkers, collocations | `honey-b1-hive`: question-tags, second-conditional |
| `in-the-damson-orchard` | `view` (Old Wilf) | `view-b2-scafell`: narrative-tenses, collocations | `view-b1-winter`: third-conditional, future-continuous |
| `in-the-damson-orchard` | `tractor` (Mr Tyson) | `tractor-b2-engine`: have-it-done, phrasal-verbs-more | `tractor-b1-alfie`: reflexive-pronouns, too-enough |
| `in-the-field` | `hay` (Mr Tyson) | `hay-b2-rain`: future-perfect, quantifiers | `hay-b1-cart`: second-conditional, reported-questions |
| `in-the-field` | `dusk` (Mr Tyson) | `dusk-b2-yan`: participle-clauses, phrasal-verbs-more | `dusk-b1-rabbits`: past-perfect, third-conditional |
| `in-the-kitchen` | `radio` (Auntie Edna) | `radio-b2-pass`: passive-more, result-linkers | `radio-b1-forecast`: might-must, for-since |
| `in-the-kitchen` | `tatie-pot` (Granny Maggie) | `tatie-pot-b2-patience`: would-used-to, word-building | `tatie-pot-b1-glove`: present-perfect-continuous, phrasal-verbs |
| `in-the-living-room` | `fire` (Old Wilf) | `fire-b2-chimney`: would-used-to, passive-more | `fire-b1-poker`: might-must, phrasal-verbs |
| `in-the-living-room` | `tea` (Old Wilf) | `tea-b2-cottage`: relative-clauses-more, wish-if-only | `tea-b1-rain`: used-to, contrast-linkers |
| `in-the-mill` | `grind` (Mr Tyson) | `grind-b2-fine`: passive-more, result-linkers | `grind-b1-notebook`: for-since, phrasal-verbs |
| `in-the-mill` | `multure` (Mr Dixon) | `multure-b2-honest`: word-building, emphasis | `multure-b1-light`: might-must, present-perfect-continuous |
| `in-the-smithy` | `horseshoe` (Mr Dixon) | `horseshoe-b2-gates`: emphasis, passive-more | `horseshoe-b1-kick`: reported-questions, contrast-linkers |
| `in-the-smithy` | `wheel` (Mr Dixon) | `wheel-b2-note`: formal-informal, wish-if-only | `wheel-b1-wheelwright`: used-to, for-since |
| `in-the-tent` | `rain` (Shepherd Jack) | `rain-b2-alfie`: reporting-verbs, past-modals | `rain-b1-forecast`: reported-speech, verb-patterns |
| `in-the-tent` | `evening` (Shepherd Jack) | `evening-b2-midges`: quantifiers, participle-clauses | `evening-b1-door`: question-tags, past-perfect |
| `in-the-wildflower-meadow` | `flowers` (Granny Maggie) | `flowers-b2-rattle`: have-it-done, collocations | `flowers-b1-names`: verb-patterns, third-conditional |
| `in-the-wildflower-meadow` | `winter` (Beekeeper Arthur) | `winter-b2-snowdrops`: future-perfect, participle-clauses | `winter-b1-harry`: reported-questions, reflexive-pronouns |
| `in-the-wood` | `ewe` (Shepherd Jack) | `ewe-b2-ivy`: past-modals, narrative-tenses | `ewe-b1-poppy`: reported-speech, question-tags |
| `in-the-wood` | `highseat` (Old Wilf) | `highseat-b2-stags`: quantifiers, collocations | `highseat-b1-deer`: too-enough, verb-patterns |
| `in-the-workshop` | `hammer` (Mr Tyson) | `hammer-b2-neighbour`: reporting-verbs, have-it-done | `hammer-b1-cows`: reported-questions, past-perfect |
| `in-the-workshop` | `saw` (Mr Tyson) | `saw-b2-tape`: past-modals, result-linkers | `saw-b1-fingers`: too-enough, reflexive-pronouns |
| `on-the-bowling-green` | `measure` (Mr Tyson) | `measure-b2-ten`: future-perfect, collocations | `measure-b1-wilf`: reported-speech, question-tags |
| `on-the-bowling-green` | `lemonade` (Brewer Sam) | `lemonade-b2-bottles`: relative-clauses-more, narrative-tenses | `lemonade-b1-match`: future-continuous, second-conditional |
| `on-the-fell` | `daisy` (Shepherd Jack) | `daisy-b2-clapper`: have-it-done, participle-clauses | `daisy-b1-tyrol`: passive, future-continuous |
| `on-the-fell` | `sunset` (Shepherd Jack) | `sunset-b2-rarely`: emphasis, relative-clauses-more | `sunset-b1-late`: third-conditional, past-perfect |
| `on-the-green` | `bench` (Old Wilf) | `bench-b2-brothers`: relative-clauses-more, would-used-to | `bench-b1-oak`: relative-clauses, verb-patterns |
| `on-the-green` | `pump` (Mrs Hartley) | `pump-b2-frost`: reporting-verbs, emphasis | `pump-b1-bucket`: too-enough, future-continuous |
| `on-the-packhorse-bridge` | `spate` (Mr Dixon) | `spate-b2-rail`: future-perfect, narrative-tenses | `spate-b1-ford`: too-enough, reported-speech |
| `on-the-packhorse-bridge` | `evening` (Beekeeper Arthur) | `evening-b2-dipper`: relative-clauses-more, wish-if-only | `evening-b1-bell`: present-perfect-continuous, question-tags |
| `on-the-watchtower` | `watch` (Shepherd Jack) | `watch-b2-fox`: participle-clauses, wish-if-only | `watch-b1-lights`: might-must, question-tags |
| `on-the-watchtower` | `village` (Mr Tyson) | `village-b2-roofs`: passive-more, quantifiers | `village-b1-door`: relative-clauses, passive |
| `round-the-maypole` | `bench` (Old Wilf) | `bench-b2-annie`: emphasis, mixed-conditionals | `bench-b1-poppy`: used-to, future-continuous |
| `round-the-maypole` | `band` (Brewer Sam) | `band-b2-trumpet`: reporting-verbs, participle-clauses | `band-b1-practise`: passive, verb-patterns |
| `under-the-clock` | `time` (Mrs Hartley) | `time-b2-twenty`: future-perfect, formal-informal | `time-b1-strokes`: present-perfect-continuous, future-continuous |
| `under-the-clock` | `gears` (Mr Dixon) | `gears-b2-springs`: relative-clauses-more, quantifiers | `gears-b1-oil`: phrasal-verbs, third-conditional |

## Words: the supplement

`companion/lexicon/en.extra.json`: **155 new entries and 51 forms joined** to entries already there (pushed → push,
father's → father, Skiddaw's → Skiddaw), every meaning in sl, de and it: the words a tap meets in the new lines,
choices, reactions and whys (fuss, blame, wheelwright, clapper, heafed, citronella, scabious, sethera), the noun with is
or has as its own form (the beck's, the lid's, the pump's: "the lid is, the lid has"), possessives. The learner's wrong
forms the turns offer stay unknown, as the course's do: winned, mights, striked, leaved, unpatient, unhonest,
unresponsible, discomfortable, kindity. `bin/lexicon-build --coverage --lang en` (no Wiktionary dictionary here, as
before): before the content 79.7 % of words by the supplement, 96.7 % with packs and names, 96.1 % meant in each base;
with the content and no supplement 76.4 %, 92.7 %, 92.1 %; with the supplement **81.0 %, 96.8 %, 96.3 %**; words none knows
151 → 155 (the nine wrong forms in, a few old ones out). `validateExtra`: 2,982 entries, no error.

## Voices

Nothing to cast: every line is the happening's person's (their voice from the culture's voice cast), every choice the
learner's. A voice build of the new variants takes **85,848 characters** in English, 1,678 texts (lines, choices, their
replies; a text with a gender pair counted once per form): the learner's choices 21,532, Mr Tyson 12,101, Mr Dixon 8,701,
Old Wilf 8,310, Jack 7,491, Arthur 6,255, Mrs Hartley 5,965, Granny Maggie 5,806, Mrs Birkett 4,093, Sam 3,701, Auntie
Edna 1,893. ElevenLabs wasn't called. That is more than a month's credits (memory, 2026-10-29): Jan decides.

## Older apps

Nothing new: the system's `forApp` gives an app below arts 7 none of the levelled variants and the happenings' variants
without them; no happening has a level, so none goes. The bridge with levels must be deployed before this content reaches
main (dialog-levels.md, "Older apps"): a bridge from before strips `level`.

## Checks

On 9fad37d7 and 7560519f (the content and the supplement complete):
- `bun test/check-culture.ts lakeland`: ok (the levels' rules and every turn's page at most one level above its dialog's),
  after each scene; the converter that wrote the variants also checked the guide's shape (3–8 lines, the person first
  and last, 2–3 choices a turn with a wrong one, a why and a reaction for each wrong one, reactions of at most 80
  characters that don't say the right choice's words, no two choices sharing a reply, every text at most 200 characters
  for a man and for a woman, pages of the en book).
- The bridge smoke with ports 10650–10669 (LANI_SMOKE_DRAMA3_PORT 10668), through lani-slot.sh: **3,723 checks, all
  passed** (dialog-levels, scenes, variants, the Lake District's pages, the supplement).
- `bunx tsc --noEmit -p .`: 0. `bun src/cli/tutor-export.ts --check`: 140 files match. `bun test/prompt-clashes.ts`: no new
  clash (339 in the baseline; no pack word added).
- `validateExtra` on en.extra.json: 2,982 entries, no error.
- The Android scene tests (DialogReactionsTest, WhoAnswersTest, DialogLevelsTest, VariantsContentTest, CultureScenesTest):
  **38 tests, 0 failures** through lani-slot.sh (with ScenesContentTest), on 7560519f. The first run failed one test:
  DialogReactionsTest found 14 reactions to a word-order or have-it-done mistake that quoted the learner's choice and
  with it a word the right choice has elsewhere ("Pick up it?" says it, "Look her after?" says her, "Enough tight?"
  says tight); they answer without quoting it now (7560519f). The whole Android suite comes with the gate.
- Emulator QA through `qa-slot` (slot 1), a synthetic Lake District learner (make-learner.ts --target en --base sl,
  LANI_CULTURE=lakeland, LANI_QA_SETUP_PAIR=en-sl): the model's `dialog-levels` step, copied uncommitted and pointed at
  two of these scenes, Arthur's evening at the bees and Mr Dixon's horseshoe at the smithy: the bridge serves each scene's
  B1 and B2 variants with their levels and an older app's `?arts=6` none of them; at QA's `level:B2` `evening-b2-father`
  and `horseshoe-b2-gates` open with their first lines, at `level:B1` `evening-b1-dark` and `horseshoe-b1-kick`; at
  `level:B1` the B2 one asked for isn't there and at `level:A1` the B1 one isn't: the happening's own dialog plays ("Every
  pony in the dale needs shoes"), nothing new. setup ok, dialog-levels ok, crash check ok. Screenshots:
  scratchpad `ux-dialogs-lakeland/levels-*.png` (eight). A first run with the default setup pair (sl-en) failed in setup:
  a learner of English sees "Friends · Prijatelji", English first, so QA needs `LANI_QA_SETUP_PAIR=en-sl`, and the step's
  "Zapri · Close" is `say("common.close")` there.

## What's left

- The native check above; then the voice build (85,848 characters, Jan's credits).
- The market's variants are `dialog-levels`' (the model); its `inn-b2-jam` now has the learner say Vi / Lei / Sie to Mrs
  Birkett, as every other Lake District scene (the merge of `placement`).
- QA ran the model's step on two of these scenes through a temporary, uncommitted copy of it (`bin/qa` is `dialog-levels`'
  to change); once `dialog-levels` is merged, `DIALOG_LEVELS_QA` can name `at-the-bees`/`evening` or
  `in-the-smithy`/`horseshoe` as a second Lake District check.
