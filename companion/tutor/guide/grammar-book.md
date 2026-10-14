Grammar book: the app's village book has a chapter "📖 Slovnica · Grammar", one page per rule (lani.grammar/v0): the
rule in Jan's base language, a table, examples, the modules that practise it, and Jan's own mistakes on it. A page
unlocks the first time Jan meets its rule: an exercise that names it ("grammar": "<page id>" on any exercise). When you
explain a rule in chat (a "why", a grammar question), keep it in the book: list_grammar, then publish_grammar the page
again with your example or a clearer explanation ("more") added, or a new page for a rule the book lacks (load
lani-studio first: reference/grammar-spec.md). Tag the exercises of every grammar module you write with "grammar",
and a scene's wrong choice (or its turn) whose why is a rule the book has: its why then leads to the page.
A chat with data.about_grammar comes from a page's "💬 Vprašaj · Ask" button: answer about that rule. One with
data.grammar_drill asks for a short drill on the page: publish a module whose exercises name it, then add the module to
the page's "modules".
The app asks a turn that tests a page's rule (a scene's form choice, a choice exercise with a gap) by how far Jan has it
(data.about_grammar.mastery): new or learning, they choose; secure (4 right in a row, 3 stars, no open mistake), they type
the missing word; mastered (8 in a row over days, 4 stars), they say the whole sentence; a slip steps it back a level. An
open mistake on the rule (mistakes-db) keeps it learning until a review of its card is right on a later day. Where a turn
tests a form, the app shows Jan's own confusion as the wrong choice, taken from mistakes-db's examples: record errors[]
with your_answer exactly as Jan had it and correct_answer the right sentence (companion/GAME.md, "Mastery and adaptive
turns").
Rules not yet (companion/GAME.md): a rule whose page's level is above Jan's (learner-profile current_level) and that
wasn't introduced (its page not unlocked, no answers, cards or mistakes on it) isn't asked for. The dialogs stay natural,
but where a turn's form is of such a rule its wrong choices of that form go: Jan chooses about meaning, or hears the line
and repeats it (an echo). Each such turn meets the rule (list_grammar: met_in_dialogs). Met 3 times, a rule of the
next level is ripe to introduce (list_grammar's "ripe", a session_end's data.grammar_ripe, the morning snapshot): introduce
it when Jan is ready, with a module whose exercises name it (playing it unlocks the page) or the page extended for them
(publish_grammar under its id, a "more" in their words: it unlocks when published). After 6 meetings on 2 days the app
opens the page itself. From then on its turns ask for it. Write the dialogs natural at the scene's level; their forms
above Jan's level are fine: Jan meets them first.
