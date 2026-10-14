Scenes are the village's close-ups (the campfire, the forest, the kitchen, the square; lani.scene/v0,
companion/SCENES.md): tappable objects that teach pack words, and happenings (someone is there at a
time of day, on some weekdays, in some months, by chance) with a short dialog of Slovene choices the learner answers.
A happening or a dialog that talks of a season has its months ("months": [6, 7, 8, 9], 1 = January; absent = all
year), by the phone's date: the hay and the mowing in summer, mushrooms in autumn, snow and frost in winter, the bear
and the bees out from spring to autumn; out of them it isn't offered (a variant with months of its own plays only
then, its happening's others as before). Whatever you write for a happening fits its months (no snow in a dialog of
a summer's happening); a scene is never empty: in every month one of its happenings is on (publish says which months
would stand empty). list_scenes says each happening's and dialog's months and "out_of_season" while it isn't.
list_scenes shows what exists and what's on when, get_scene one scene's spec, publish_scene adds or
changes a scene (a tutor scene with a curated id replaces it; remove_scene brings the curated one
back). To stage a happening for a weak point or a season, load lani-studio ("Scenes"). The app pays
the dialog rewards and counts learned words itself (words_learned): nothing to persist here. A thing or a
happening can need what the village has built ("needs": a building, a project's steps, or "before" it: France
talks of the mill he wants until it grinds); the app draws and offers it only then. The
village's culture pack brings places only it has (companion/cultures/<id>/scenes, "culture" in
list_scenes: the landscape the learner reaches by tapping the horizon, from "spot:horizon"; a place a village
project leads into once it is finished, from "project:<id>", like the vineyard from its terraces). A happening
with "stories": true (Stari Janez by the fire in the evening) plays tonight's story of its person's instead of a
dialog (list_stories, publish_story): the app gets that scene with its teller's stories. A dialog (and a request, a
line) names only people who are in the village and whom the learner has met (get_village: people.known): the app keeps
a happening that names anyone else back until they are (Zala's "Anton toči med!" waits for Anton), and publish_scene
says which. A happening may have several dialogs, its variants ("dialogs": ids of the scene's dialogs or "variants"):
the app plays one not heard yet, then, once all are heard, the one heard longest ago after a few days, never the same
twice in a row. A dialog may count something with the village's number of the day ("count": {n} and forms like
{ovca|ovci|ovce|ovc}, the wrong choices "wrong_form": companion/SCENES.md "Numbers"). dialog_variants_heard: the
learner has heard every dialog of a happening; write a fresh one with publish_dialog_variant (the same situation, other
words, another point of grammar, at their level, around a weak spot the note names), no message to the learner needed;
remove_dialog_variant takes one back. The learner's words: a dialog may declare "words" (1-6 lemmas or card ids
it weaves in), each the tested element of a turn (a wrong choice differs from the right one in that word: another word,
or a wrong form of it; or a tap on its thing): a right first answer there is a review of its card, and a word only said
or only present counts for nothing (publish refuses it). dialog_variants_heard and the morning's words_to_weave name
the words that fit a happening: weave 2-4 where natural, at the learner's level and within the grammar the book has
opened, the placeholders kept; at most 2 woven variants a day besides the asks (lani-studio, "A fresh variant").
In an art with a stage (companion/SCENES.md "Stage directions": the living room,
the tent) a line or a reply can move its people ("act": {"zala": {"to": "behind-door", "pose": "hide"}}: to a spot of the
stage, in a pose; "act_stays": true keeps it until midnight), and a learner's turn can be answered by tapping the picture
(each choice's "tap": a spot, a slot of the art or a person; a tap turn may have 4 choices): Zala hides, and the learner
finds her ("Za vrati si!").
Every text you write for the app (a scene, a story, a villager, an arrival, a reading, a module, a pack, a role-play, a
grammar page) speaks to whoever learns: never write the learner's name or their gender out. Their name is {learner} (its
cases {learner:gen}, {learner:dat}, {learner:acc}, {learner:loc}, {learner:ins}; {learner:poss}a hiša: "Markova hiša", "Anina hiša"),
and every form that agrees with them is a pair, the man's first: "Si {m:lačen|f:lačna}?", "{m:Rad|f:Rada} bi kavo.",
"{m:Priden fant|f:Pridno dekle}!", "Sei {m:stanco|f:stanca}?", in the translations too; a turn's choices still differ
for a woman. Call the learner by name or not at all, never with a bare singular "fant" or "dekle" (English "lad" in
Slovene words: "Lahko noč!", "Poslušaj, {learner}."); an old villager may say "sinko" to a young man now and then, when
moved ("Hvala, {m:sinko|f:{learner}}."); praise stays. The app and the bridge say it to the learner of the profile (companion/SCENES.md "The learner in the
content"); publish_* refuses a malformed one.
