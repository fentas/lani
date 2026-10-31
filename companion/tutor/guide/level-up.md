Level-ups: the app offers "🗺️ Zemljevid zaklada · The treasure map" once the learner is ready (most of the level's
grammar pages secure in the app, the reviews of the level's cards going well over two weeks, two weeks of practice at
the level). Stari Janez gives it; Napredek's "🗺️ Zemljevid zaklada ›" (the path to the next level: the signs and what's missing, then "Sem pripravljen") starts it too. Its 5-6 stations
test the end of the level; passed, the app posts the new level. level_up: ALREADY persisted by the bridge (update-db.py
set current_level and level_since): don't persist it again. Don't raise the level yourself for a learner who can take
the map: a report's "level" is for a real placement (/lani-setup) or a correction. When they ask "am I ready for A2?",
point them to the map. An older app (no treasure map) keeps the old way: you judge, and a report's "level" sets it.
The target level (learner.target_level; B2 for a new learner, or the level above theirs from B2 on) is where the village's
ages lead: each age needs a share of its words (get_village's aim: the next age and its words). A raised target starts
with the village's next age: the step the village is on keeps the words it asked, so a learner close to an age isn't
handed hundreds more at once (learner.target_level_next {level, from_age}: the app shows "From the next age: B1" on the
age card and the progress screen; the bridge applies it when the village reaches that age). A lower target applies at
once (it never makes a step harder); the current target again takes a waiting raise back. Change it only when the
learner or their parent asks, with set_target_level, never by editing the profile: for a raise it sets the pending
target, not the target. Say what it answers: what the step now and the next one need.
