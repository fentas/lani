# lani.grammar/v0 — the grammar book's pages

Source of truth for validation: `companion/bridge/src/grammar.ts`. Keep this file in sync with it.

The village book in the app has a chapter "📖 Slovnica · Grammar": one page per rule of the language learned, explained
in the learner's base language, with a table, examples, links to the modules that practise it, and the learner's own
examples and mistakes on it. A page unlocks the first time the learner meets its rule: an exercise, a challenge's question
or a module that names it (below), and a "why" that belongs to it leads there ("📖 V knjigo · To the book").

Curated pages live in `companion/grammar/<language>/<id>.json` (44 in `sl/`, 16 each in `it/`, `de/` and `en/`). You add
pages, or extend a curated one, with `publish_grammar`; `list_grammar` shows the pages and which the learner has met,
`get_grammar` one page.

## Page

| Field | Type | Notes |
|---|---|---|
| `schema` | `"lani.grammar/v0"` | required, literal |
| `id` | string | kebab-case, 2-63 chars: what exercises name (`"kje-mestnik-orodnik"`) |
| `language` | `sl` \| `it` \| `en` \| `de` | the language the rule is of (the village's); `sl` when missing |
| `level` | `A1`…`C2` | |
| `emoji` | string | the page's icon (default 📖) |
| `title` | text | in the page's language and the bases, English always: `{"sl": "Kje? Predlogi kraja", "en": "Where? Prepositions of place", "de": …, "it": …}` |
| `rule` | text | the rule, per base language, English always: a few short paragraphs (Markdown: `**bold**` forms, `*italic*` terms), the learned language's terms in brackets ("the locative (*mestnik*)") |
| `more` | text? | your addition: a clearer or longer explanation, per base. The app shows it under the rule as yours |
| `table` | object? | `{"head": [cell, …], "rows": [[cell, …], …]}`: 2-4 columns, 1-12 rows, every row as long as the head |
| `examples` | Example[] | 1-24 |
| `modules` | string[] | ids of the modules that practise the rule: "🎯 Vadi · Practise" plays their exercises |
| `tags` | string[] | what the rule's mistakes and cards are called: mistakes-db pattern ids, SR card categories and ids (below) |
| `see` | string[] | related pages ("Glej tudi · See also") |
| `machine_written` | boolean | true for yours and the curated ones (default true) |
| `review` | string? | who wrote which languages and who checked them: `"machine-written (Claude) in sl, en, de and it; not reviewed by a native speaker"` |

A **text** is `{"<language>": "…"}` with at least `en`. A **cell** is the learned language's words as they are (`"ob ribniku"`, a plain
string), or a text per base (`{"en": "locative (mestnik)", "de": "Lokativ (mestnik)", "it": "locativo (mestnik)"}`: the app
shows the learner's base, else English).

An **example** is the sentence in the page's language and its translation into each base, English always, and an optional
`note` (a text): `{"sl": "Šotor bo stal ob ribniku.", "en": "The tent will stand by the pond.", "de": …, "it": …}`. The app shows
it with 🔊 and word lookup. Keep examples short, at the page's level, and from the village where you can (Luka, the pond,
Micka's kitchen).

**Tags** match an id when it is the tag, or has the tag's words in a row: `accusative` matches the mistake
`accusative_feminine_a_to_o`, `grammar_biti` the card category `grammar_biti`, `iz` the card `vocab_iz_genitive_fem`. The page
shows the learner's mistakes (mistakes-db error patterns) and grammar cards (SR items that aren't vocabulary) that its tags
match, and practises those cards when no module is linked. Pick tags that name the rule, never broad ones (`grammar`, `cases`).

```json
{
  "schema": "lani.grammar/v0", "id": "kje-mestnik-orodnik", "language": "sl", "level": "A1", "emoji": "📍",
  "title": { "sl": "Kje? Predlogi kraja", "en": "Where? Prepositions of place" },
  "rule": { "en": "To say where something is (**Kje?**), … **ob** (by) takes the locative (*mestnik*): ribnik → **ob ribniku**. …" },
  "table": {
    "head": [{ "en": "preposition" }, { "en": "case" }, { "en": "example" }],
    "rows": [["ob", { "en": "locative (mestnik)" }, "ob ribniku"], ["pod", { "en": "instrumental (orodnik)" }, "pod veliko smreko"]]
  },
  "examples": [{ "sl": "Šotor bo stal ob ribniku.", "en": "The tent will stand by the pond." }],
  "modules": ["predlogi-kraja"], "tags": ["kje", "locative", "instrumental"], "see": ["kam-tozilnik"]
}
```

## Linking practice to a page

Every exercise type takes an optional `"grammar": "<page id>"` (module-spec.md). The app unlocks the page the first time the
learner answers an exercise that names it, shows "📖 Nova stran v knjigi · New page in the book", and puts "📖 V knjigo · To
the book" beside the exercise's "why". Tag every exercise of a grammar module; a module about several rules tags each
exercise with its own. A page's `modules` also unlock it when one of them is played. A culture pack's challenge questions
carry the same field (`quests.json` `tent_move.questions[].grammar`, `surprises.json` `letter.letters[].questions[].grammar`
and `pilgrim.ways[].grammar`), and so does a request (`quests.json` `requests[].grammar`: its intro leads to the page, and
playing it meets the page, as a module's `modules` do).

A scene's wrong choice names the page of the rule it breaks (`"grammar"` on the choice, or on the learner's turn for all its
choices; lani.scene/v0): its "why" then shows "📖 V knjigo · To the book", the pick counts as a slip on the rule and
unlocks the page, and a right first pick on a turn that names a page counts as right. Tag the whys that are a rule the book
has (the case after a preposition or a verb, the dual, ti or vi, a noun's gender, a verb's person, the past, the future,
the comparative, the clitics' order, a double no), not a word's meaning or a greeting's time of day. `publish_scene` names
pages the book lacks. A Slovene scene that names no page at all gets them guessed from its whys (companion/SCENES.md,
"Adaptive turns"), only to decide how a turn is asked: a guessed page counts nothing, so name them.

**The cases by level** (the page `skloni` has the overview): the nominative, the accusative and the locative are A1, the
genitive A1 after a negation and iz, A2 for amounts, numbers and whose; the dative (`dajalnik`) and the instrumental
(`orodnik`) are A2 to B1. Where something is: v, na, ob, pri + the locative is `kje-mestnik-orodnik` (A1); pod, nad, pred,
za, med + the instrumental (pod mizo, za vrati) is `orodnik` (A2), so a why about them names `orodnik`. At A1 a dialog may
use the dative and the instrumental in set phrases (hvala vam, všeč mi je, s kruhom, z mlekom), and may test them as forms
too: the app doesn't ask a learner for a rule above their level until it is introduced (below), so name the page.

The page is also the unit of **mastery** (companion/GAME.md, "Mastery and adaptive turns"): not yet (below), new,
learning, secure, mastered, from the answers on the rule (the run of right answers in a row), the grammar cards and the mistakes its `tags`
match. A `choice` with a `___` gap in its `prompt` is typed once the learner has its page secure and said whole once
mastered; so write a grammar module's choices as a sentence with a gap and the forms as options ("Nimam ___. (I don't
have time.)" with "čas", "časa", "času"), the translation in brackets at the end.

## Rules not yet: introducing a rule

What the learner is asked grows with the grammar introduced to them (companion/GAME.md, "Rules not yet"; SCENES.md,
"Rules not yet"). A rule is **not yet** when its page's `level` is above the learner's (learner-profile `current_level`)
and it wasn't **introduced**: its page isn't unlocked, and they have no answers, cards or mistakes on it (its `tags`).

- **In a dialog** a turn whose form is of a rule not yet doesn't ask for it: its form's wrong choices go, the learner
  chooses about meaning, or hears the right line and repeats it (an echo turn: nothing graded). What the others say is as
  written. So write dialogs natural at the scene's level, forms above the learner's level included, and name every form
  choice's page (`grammar`): gating goes by it, and a learner at that level is asked for it as ever.
- **Meetings.** Each such turn passed meets the rule (`list_grammar`: a page's `met_in_dialogs`). Met 3 times, a rule of
  the next level up is **ripe**: `list_grammar`'s `ripe`, the morning snapshot's `grammar_ripe` and a session_end's
  `data.grammar_ripe` list it (id, title, level, meetings). A rule two levels up waits for the learner's level.
- **Introduce it** when the learner is ready (a good session, a quiet day, not in the middle of a hard week): a short
  module whose exercises name the page (`"grammar": "<id>"`, the page's `modules` extended with it), which unlocks the page
  when played; or the page extended for them (`get_grammar`, a `more` in their words with an example from the dialogs they
  met it in, `publish_grammar` under its id), which unlocks it when published. Say it in the chat: "You've heard z mamo, z
  bratom … a few times: that's the instrumental. Here is its page and a short drill." One rule at a time.
- **Or not:** after 6 meetings on 2 days the app opens the page itself ("📖 Nova stran v knjigi"). From then on its turns
  ask for it: chosen first, typed once secure, said once mastered.

## When to write or extend a page

- You explain a rule in chat (a "why" the learner asked about, a grammar question, a `data.about_grammar` chat from a page's
  "💬 Vprašaj · Ask the tutor"): if the book lacks the rule, write a page; if it has it, add the example you used or a
  clearer explanation (`more`) to it. One rule a page; check `list_grammar` first.
- A chat with `data.grammar_drill` (the page's "🎯 Vadi" found nothing to practise) asks for a short module: 5-8 exercises,
  each with `"grammar": "<id>"`, and the page's `modules` extended with it.

**Extending a curated page:** `get_grammar`, add your examples (they are marked yours) and/or `more`, and `publish_grammar`
the page with the same id. The curated title, rule, table and review stay as they are; your examples come after the
curated ones, and `modules`, `tags` and `see` add to the curated ones. Publishing it again replaces your earlier addition.

**Your own page:** a new id; it shows as the tutor's ("🧑‍🏫"). Write the rule in the learner's base language and English
(Jan: English; a German or Italian base: that language too), in the app's voice: clear, short, friendly, the learned
language's terms in brackets. Level it honestly; link the modules that practise it; tag it.

## Other languages

A page is of one language (`language`), and a bridge serves the pages of its village's language
(`companion/grammar/<language>/`, then yours). Slovene has 44 curated pages, Italian, German and English 16 each (the
list is in companion/README.md, "Grammar book"), explained in the bases their learners have: Italian in `sl`, `en`, `de`;
German in `sl`, `en`, `it`; English in `sl`, `it`, `de` (and `en`). Extend them as you would a Slovene page; for a rule the book
lacks, write a page with the village's language in `title` and the examples, the learner's base (and English) in `rule`.
Tag what you write for such a village with its language's page ids (`list_grammar`), never a Slovene one.
