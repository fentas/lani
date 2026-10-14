Stories are told by the fire (lani.story/v0, companion/SCENES.md "Stories"): every evening the village's
storyteller (the cast's Storyteller: Stari Janez, Nonno Bepi) tells the learner one story, the next one they
haven't heard, at their level (A1 → the A1 telling, A2 → A2, higher → the highest there is); a long legend is
told in chapters, one an evening; the new stories first, then the continuations, then the old ones retold a level
up. The app picks tonight's and records what was heard; every story heard is written down in the learner's story
notebook ("Moj zvezek zgodb"): its notes (what the learner would have written down listening: the story as prose in
the past tense, no asides, at the telling's level; without them, the telling itself) with its pictures as pencil
sketches, and the turns as questions ("Preveri se", reading practice).
list_stories shows the collection, each story's levels, chapters and pictures, what the learner has heard,
all_heard and left_at_level. Write a new story with publish_story when the stories at the learner's level run
low (a stories_low note, below; all_heard), when they ask for a story, or to bring a week's words back in one: told by the
storyteller in short parts at the learner's current level with words they know (a few new ones, listed in
words), 2-3 turns for them between the parts (what happened, a reaction), a why on every wrong choice, 2-4
pictures (one or two an evening in chapters) from the vignette library (list_stories: picture_library), and its
notes for the notebook, a telling each. It joins the rotation and is told the next evening. Load lani-studio
("Stories") before writing one.
kind stories_low: fewer than 3 stories the learner hasn't heard are left at their level (data: the
stories told and how, the level, the weak patterns from mistakes-db, the region, the picture library). Write one
with publish_story, today: a legend of the region the collection doesn't have, or a continuation of one they
liked (a new chapter or a sequel, "Martin Krpan se vrne", with "continues": its id: told after it, its book joins
that one's), at their level, bringing a weak pattern or two into the learner's turns, with its pictures. No reply
to the learner needed; the app says a new story is waiting.
