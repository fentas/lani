# Scenes: stepping into the village

The village ("Moja vas", see [GAME.md](GAME.md)) is a place to explore, not only a build menu. The
learner pans and zooms around it, sees what is going on (a bubble over the kitchen, something in
the forest) and taps into **scenes**: close-up pixel views of one place, such as the campfire, a
kitchen, the forest or the square. Each scene teaches the words for what is in it. On some days and
at some times people are there, like Babica Micka at the stove or children playing, and they have
something to say.

```
Village (full screen, pinch zoom, pan)         Scene (close-up)
  bubbles over places ──── tap a place ──────►   objects: tap → word (🔊, gender, English) → learn them
  📜 scroll / 📖 book: what's on today           people: tap → dialog (choices in Slovene) → reward
                                                  → "keep talking" with the tutor (role-play scenario)
```

## Parts and where they live

| Part | Code |
|---|---|
| Format, registry, daily logic (the contract) | `android/.../game/scene/`: `SceneSpec.kt` (format), `SceneArt.kt` (arts, slots, sprites, effects, stages), `Stage.kt` (stage directions and tap turns, see [Stage directions](#stage-directions-people-move-on-cue)), `Happenings.kt` (what's on), `Story.kt` and `Stories.kt` (the storyteller's evening story, see [Stories](#stories-the-evening-story)), `Notebook.kt`, `StoryBooks.kt` and `Vignettes.kt` (the learner's story notebook: every story heard written down, its questions, and its pictures' library, see [The story notebook](#the-story-notebook); the ink painters and the pencil sketch in `game/render/book/`, the notebook's pages in `ui/notebook/`), `Wildlife.kt` (the wild animals out, see [Wild animals](#wild-animals)), `SceneSights.kt` (which things a picture shows now and when the others come, see [The words panel](#the-words-panel-here-now-seen-before-still-hidden)), `ISpy.kt` (I spy with a child of the village, see [I spy](#i-spy-vidim-vidim-nekaj-česar-ti-ne-vidiš)), `SceneWorld.kt` (what the village has built, as the scenes show it: `needs`, the fixtures, see [What the village has built](#what-the-village-has-built)), `TownMarkers.kt` (bubbles in the village), `ScenePainter.kt` (painter interface), `Sky.kt` and `SceneFx.kt` (a dialog's weather and effects, what stays for the day), `Pokes.kt` (what a tap sets off, see [Pokes](#pokes-small-gotchas)) |
| Village view: full screen, camera, bubbles, the scroll/book | `ui/game/VillageScreen.kt` and friends, `game/render/` (the village renderer) |
| Scene art: one painter per art, people sprites, the wild animals (`Wild.kt`) | `game/render/scene/` |
| Scene screen: words, learning, dialogs, rewards | `ui/scene/` (the words panel's chips: `WordGroups.kt`), `app/SceneController.kt`; the learner's words a dialog tests (see [Your words in the dialogs](#your-words-in-the-dialogs)): `game/DialogWords.kt`, `game/DialogReviews.kt` (on `game/PlayReviews.kt`), `app/DialogWordsController.kt` |
| Stickers: a word's picture cut out of its scene (see [Stickers](#stickers-a-words-picture-from-its-scene)) | `game/scene/WordStickers.kt`, `game/render/scene/Stickers.kt`, `ui/words/WordPicture.kt` |
| Content and serving | `companion/scenes/*.json`, a culture pack's own `companion/cultures/<id>/scenes/*.json`, `bridge/src/scenes.ts`, `bridge/src/features/scenes.ts`; the stories: `companion/cultures/<id>/stories/*.json`, `bridge/src/stories.ts`, `bridge/src/features/stories.ts` |
| A village project's step as a short scene (see [Project steps](#project-steps-a-village-projects-step-as-a-short-scene)) | `companion/cultures/<id>/project-steps/*.json`, `game/Projects.kt` (its dialog, who says what, where they stand, the step done), `app/SceneController.kt` (`startStep`), `ui/scene/ProjectStepScene.kt` (over the village), `bridge/src/project-steps.ts` (the check) |

## Format: `lani.scene/v0`

One file per scene in `companion/scenes/` (curated; a village of another language: `companion/scenes/<language>/`), in a
culture pack's `scenes/` (the places only that culture has, see [Scenes of a culture pack](#scenes-of-a-culture-pack)), or
published by the tutor with `publish_scene`.

```json
{
  "schema": "lani.scene/v0",
  "id": "ob-ognju",
  "title": "Ob ognju · At the campfire",
  "emoji": "🔥",
  "level": "A1",
  "art": "campfire",
  "from": ["fire"],
  "pack": "ob-ognju",
  "objects": [
    { "slot": "fire", "word": "ogenj" },
    { "slot": "logs", "word": "drva" },
    { "slot": "kettle", "word": "kotel" }
  ],
  "people": [
    { "id": "babica", "name": "Babica Micka", "emoji": "👵", "art": "grandma", "slot": "left" },
    { "id": "otroci", "name": "Otroci", "emoji": "🧒", "art": "child1", "slot": "right" }
  ],
  "happenings": [
    {
      "id": "juha", "title": "Babica kuha juho · Grandma is cooking soup", "who": "babica",
      "when": ["evening"], "chance": 0.6, "dialog": "juha", "reward": { "food": 15 }, "marker": "🍲"
    }
  ],
  "dialogs": [
    {
      "id": "juha",
      "talk": "nedeljsko-kosilo",
      "lines": [
        { "who": "babica", "sl": "Dober večer! Si lačen?", "en": "Good evening! Are you hungry?" },
        { "choices": [
          { "sl": "Ja, zelo.", "en": "Yes, very.", "ok": true, "reply": { "sl": "Potem sedi k ognju.", "en": "Then sit by the fire." } },
          { "sl": "Dobro jutro!", "en": "Good morning!", "why": "It's evening: dober večer.",
            "reply": { "sl": "Jutro? Poglej, luna je že zunaj!", "en": "Morning? Look, the moon is out already!" } }
        ] },
        { "who": "babica", "sl": "Izvoli, juha je vroča.", "en": "Here you are, the soup is hot." }
      ]
    }
  ]
}
```

| Field | Rules |
|---|---|
| `id` | lowercase, `a-z0-9-`, unique across scenes |
| `language` | the language its lines are in: the village's, `sl` unless it says otherwise (`it` in the Italian village's scenes); see [In another language](#in-another-language) |
| `title` | "target · English" as above, or per language: `{"it": "Il mare", "sl": "Morje", "en": "The sea"}` (a happening's `title` too) |
| `art` | one of the arts below; decides what is drawn |
| `from` | where the village opens it, a list: `fire`, `forest`, `spot:<id>` (a spot of the landscape: meadow, pond, highseat, path, woodpile, rocks, riverbank, road, kopa: the charcoal pile deep in the woods, horizon: the land at the village's horizon; they always exist, so a scene there is open from the start), building types in lower case (`tent`, `field`, `well`, `hut`, `kozolec`, `beehive`, `palisade`, `watchtower`, `smithy`, `lipa`, `house`, `church`, `school`, `market`) or `project:<id>`: a village project's landmark, there once the project is finished (its id one of the culture's projects; the vineyard's terraces lead into the vineyard). Several scenes may share a place; the village offers a choice then. The first place that exists is where its bubbles show (a kitchen: `["hut", "house"]`). |
| `needs` | optional earliest age: `ogenj`, `tabor`, `zaselek`, `vas`, `trg`, `mesto` |
| `household` | only a room of a house (it opens from `house`): who lives in the house whose room it is, villager ids of the cast (`["france", "tine"]`); a house opens one room, its own, dealt by who has moved in (see [Houses and their rooms](#houses-and-their-rooms)). None: the room is anyone's (the kitchen). An older bridge ignores it. |
| `pack` | the word pack the objects teach. Every object's `word` is a word id in that pack (or in the object's own `pack`), so learning a scene's words is learning pack words: they count, get reviewed and pay the village like any pack word. |
| `objects[]` | `slot` (one of the art's slots, each at most once) and `word`; `needs`: what the village must have built for the thing to be there (see [What the village has built](#what-the-village-has-built)). The bridge serves them resolved: `sl`, `en`, `emoji`, `gender`, `plural`, `example_sl`, `example_en` copied from the pack. |
| `people[]` | `id`, `name`, `emoji`, `art` (a sprite), `slot` (one of the art's person spots), `always` (there all day, without a happening), `villager` (their id in the cast, [VILLAGERS.md](VILLAGERS.md): the friendship grows here too). |
| `happenings[]` | `who` (a person here), `when` (`morning` 5–11, `afternoon` 11–17, `evening` 17–22, `night` 22–5, `dawn`: the morning's first two or three hours round the month's sunrise, see [What's on today](#whats-on-today); empty = all day), `weekdays` (1 = Monday … 7), `chance` (per day), `dialog`, `dialogs` (its variants, see [Variants](#variants-not-the-same-dialog-every-time)), `reward` (`food`, `wood`, `stone`, `wisdom`; paid once a day when the dialog is done), `marker` (emoji for the village bubble), `needs` (the earliest age, `"vas"`, or what the village must have built, as an object's), `memory` (`{sl, en}`: what the person remembers afterwards, fitting "Še vedno mislim na …"), `guests` (`true`: only a visitor from a linked town meets it, see [Dialogs for guests](#dialogs-for-guests)), `stories` (`true`: the person tells tonight's story instead of a `dialog`, see [Stories](#stories-the-evening-story)). |
| `dialogs[]` | `lines`: someone's line (`who`, `sl`, `en`) or the learner's turn (`choices`: 2–3, one `ok` or two where people would accept either, and at least one wrong; a right one may carry the other person's `reply`; a wrong one says `why` and carries their reaction as its `reply`, see [Reactions and more than one right answer](#reactions-and-more-than-one-right-answer); a why that is a rule names its grammar book page, `grammar`, see [A why that is a rule of the grammar book](#a-why-that-is-a-rule-of-the-grammar-book); the turn may carry a `puzzled` reaction for a wrong answer without its own, see [Adaptive turns](#adaptive-turns) and [Own traps](#own-traps)). 3–8 lines. `talk`: an optional role-play scenario to go on with the tutor. A line and a reply may carry a `sky` cue and an `fx` cue, and the dialog `sky_stays` and `fx_stays` (see [Sky cues](#sky-cues-the-weather-in-a-dialog) and [Effects](#effects-the-place-joins-in)); in an art with a stage, an `act` cue and `act_stays` (see [Stage directions](#stage-directions-people-move-on-cue)), and a turn's choices may each stand for a place in the picture, `tap` (see [Tap turns](#tap-turns-answer-by-tapping-the-scene)). `guests` (`true`): the dialog is for a guest, and so is every happening that plays it. Who can be named: anyone of the cast, but a happening whose texts name someone is on only while they're in the village and met ([What's on today](#whats-on-today)): name whom the scene is about and whom they'd be with (Micka at the market with Marko at his stall), not someone the place doesn't bring. A dialog may also say `count` (a number from the village, see [Numbers](#numbers-counting-what-the-village-has)), `memory` (what the person remembers of this variant, instead of the happening's), `words` (the learner's words a tutor's variant weaves in, each the tested element of a turn, see [Your words in the dialogs](#your-words-in-the-dialogs)) and, a tutor's, `source: "tutor"`. |
| `variants[]` | more dialogs, the same as `dialogs[]`: the happenings' other variants. The curated scenes keep them here, because an older bridge reads at most 12 `dialogs` (and skips a scene with more) and leaves `variants` out; the bridge serves them to the app in `dialogs`. At most 36. |

Slovene must be correct and natural at the scene's level. The learner's own lines, and whatever anyone says to them
or about them, agree with the learner: their name is `{learner}` and every form that agrees with them is a gender pair,
`{m:…|f:…}` ("Sem {m:lačen|f:lačna}.", see [The learner in the content](#the-learner-in-the-content)). A wrong choice
is wrong for a reason the learner can learn from (wrong case, wrong greeting for the time of day, wrong word), never a
gender trap: the choices of a turn differ for a woman as for a man.

**On the screen** (`ui/scene/SceneDialog.kt`, `DialogPanel`): the lines chat-like, the person's typed in and voiced,
each with its translation small under it, and the learner's turn: the choices with theirs, a tap picks one, 🔊 says
it, and "🎤 …ali ga povej · …or say it" takes it said out loud (the choice said counts as tapped). A tap on a word
opens its card (a long press in the choices): a word the learner has shows its forms as far as the book has reached
them, and a form two verbs share shows the one the line means with the other as its partner (sedite: sesti, and
sedeti under it; see [GAME.md](GAME.md#a-words-forms)). "👁 Prevod" in the header (TalkBack: "Prevod · Translation") hides the
translations of the lines and the choices, to read and answer in the language alone; the phone remembers it for every
later dialog (`DialogPrefs.translations`, on by default). While they're hidden, a tap on a line beside its words shows
that line's translation (a tap again hides it; TalkBack: "Pokaži prevod · Show the translation"); the word cards, the
🔊 and the why stay. Beside the 🎤, a turn whose rule has a page shows "📖 Namig · Hint" (the rule, never the answer: see
[The hint](#the-hint-the-rule-never-the-answer)), and a long press on a line said opens its grammar word by word (see
[The sentence's grammar](#the-sentences-grammar-a-long-press-on-a-line)). Every dialog is this panel: a scene's, the
storyteller's evening story, a keeper's talk at a spot of the landscape, a newcomer's arrival, a village project's step
([Project steps](#project-steps-a-village-projects-step-as-a-short-scene)) and a guest's dialog on a visit. The chip replaced "🎤 Odgovarjaj z glasom ·
Answer by speaking", which made the choices untappable (the 🎤 the only way to answer, "👆 Raje tapni · Tap instead"
after two failed takes); the 🎤 beside the choices does that job, and a mastered rule's turn is said whole anyway (see
[Adaptive turns](#adaptive-turns)).

### Reactions and more than one right answer

A wrong choice is said, and the person reacts to what the learner actually said, taken at face value: Jan says "Ja,
zelo sem žejen." (thirsty) where they meant hungry, and Babica Micka: "Žejen? Tu imaš vodo."; "Deset jajce, prosim."
gets "Samo eno jajce? Deset? Ne razumem …"; ti to an old man, "Tikaš me? No, prav …"; a slip with no funny reading, a
puzzled "Hm? Kako, prosim?". The reaction is the wrong choice's `reply`: one short sentence at the scene's level, in the
person's voice (Micka warm, Luka cheeky, Janez dry; for a child always gentle), kind and a little funny, never mocking,
in every language the choice has. It never says the right form ("Deset jajc? Seveda." would give the answer away): the
learner still finds it. It may carry an `fx` of the art (Micka brings water), which passes: it shows while the learner
picks again and goes with the next pick; it has no `sky` (the bridge rejects one).

The app (`DialogRun.choose`) puts the wrong choice into the conversation, marked wrong (red, ✗), and the reaction after
it as the person's line, voiced like their others; the person looks puzzled (a hand to the chin, `DialogMood`). The
choice is crossed out, the mistake counts once as before, and the why is one tap away ("💡 Zakaj? · Why?"): first what
the person made of it. A wrong choice without a reply (a tutor's older scene or story) shows its why at once, as before.

Where people would accept more than one answer, a turn has two right choices, each with its own reply: a greeting, a
goodbye, thanks, a yes or a no, a question ("Adijo!" and "Čav!", "Hvala!" and "Hvala lepa!"), polite answered politely,
cheeky cheekily, so choosing is about what the learner wants to say. Either goes on with the dialog; the cues follow
the one taken (the dialog's `picks` are the file's indices), so give both replies the same `sky` and `fx`. Such a turn
keeps a wrong choice, and a turn that tests a form (the choices differ in one ending) has one right answer. A handful
per scene, where it is natural.

### A why that is a rule of the grammar book

A wrong choice whose why is a rule of the grammar book (companion/README.md, "Grammar book") names its page:
`"grammar": "kam-tozilnik"` on the choice (v kleti for v klet), or on the learner's turn when all its choices test one
rule. The app (`DialogRun.rule`) shows "📖 V knjigo · To the book" under the why, the pick counts as a slip on the rule
and unlocks the page ("📖 Nova stran v knjigi"), and a turn's right first pick counts as right when the turn names a page
(or all its choices name the same). Only a rule the book has: the case after a preposition or a verb (pomagati and the
dative, z/s and the instrumental, malo and the genitive), the dual, ti or vi, a noun's gender and its adjective, a
verb's ending, the past, the future, the comparative, the clitics' order, a double no; not a word's meaning, a
greeting's hour or a thank-you (žejen for lačen, dober večer in the morning, v for na). The curated Slovene scenes name
the page of 426 of their 449 form choices (and of 764 wrong choices in all); the 23 without one are a word's meaning. A
verb taking an accusative pronoun ("Ujeti takes the accusative: te") names `tozilnik`, jo or ga by the noun's gender
`spol-samostalnikov`, jih for a plural `osebni-zaimki`. The page is of the scene's language (the bridge's culture check
and the smoke check that it is one); an older bridge strips the key.

### Variants: not the same dialog every time

A happening can have several dialogs, its variants: the same situation (its title, its time, its person, its
reward), different lines and a different point of grammar. Luka waters his sheep at the brook every other evening; one
evening Jan asks how many he has, another Luka has Jan count them at the water, another a lamb won't drink.

```json
{ "id": "ovce", "who": "luka", "dialog": "ovce", "dialogs": ["ovce-stejem", "ovce-jagnje", "ovce"], "…": "…" }
```

`dialogs` lists them, dialog ids of the scene (in its `dialogs` or its `variants`); without it `dialog` is the one. `dialog`
stays what an older app plays (it knows one dialog per happening): in the curated scenes the dialog of before, which comes
last in `dialogs`, since Jan has heard it. A file may leave `dialog` out; the bridge then serves the first variant without
a [count](#numbers-counting-what-the-village-has) as it, for an older app.

**Which one plays** (`DialogVariants.pick`, pure, the same all day):

1. one the learner hasn't heard yet: the first in `dialogs`;
2. once all are heard, a review: the one heard longest ago, if that was at least three days ago (`DialogVariants.GAP`)
   and it isn't the one heard last; never the same twice in a row;
3. else none: the happening rests today (it isn't on, it has no bubble; the tutor is asked for a fresh one, below).

A happening with a single dialog plays it every time, as before. Only a variant whose texts name people who are here and
met is played ([What's on today](#whats-on-today)): the others wait. A dialog heard to its end is recorded in
`GameState.dialogsHeard` (`scene/happening` → the variants heard, each with the date, and the one heard last), which syncs
with the bridge like the rest of the village. On a visit to a linked town the day's dice picks among the playable
variants (the host's record is the host's). A variant may carry its own `memory`, what the person remembers of it; else
the happening's.

**The tutor writes new ones.** When the learner has heard all of a happening's variants, the bridge tells the tutor
(`dialog_variants_heard`: the scene and the happening, its person, the variants heard with their first lines and dates,
the learner's level and weak spots from mistakes-db), at most three happenings a day and each once until a variant is
added. The tutor writes a fresh one with `publish_dialog_variant` (the scene, the happening, the dialog; the same rules as
a scene's dialogs): it is kept in `<data>/app/variants/`, joins the happening's `dialogs` after the others (the one not
heard yet, it plays the next time the happening comes) and is served with `"source": "tutor"`. `remove_dialog_variant`
takes one back.

**With the learner's words.** The note names the learner's words that fit the happening (`words_to_weave`: new, due and
weak cards of its scene's packs or of its person's), and the morning plan a few happenings with theirs: the tutor writes
2–4 of them into a variant, each the tested element of a turn ([Your words in the dialogs](#your-words-in-the-dialogs)),
declared in its `words`, at most two such variants a day besides the asks above (past them, `publish_dialog_variant` still
publishes and says the day's limit is reached; the morning snapshot offers no more words that day).

### Numbers: counting what the village has

A dialog can count something: Luka's sheep at the water, Micka's eggs at the market, France's logs by the fire. Its
number comes from the village that day, and its lines and choices are built from it with the right forms; in Slovene the
point is the dual and the genitive plural (ena ovca, dve ovci, tri ovce, pet ovc), and the wrong choices are the other
forms.

```json
{
  "id": "ovce-stejem",
  "count": { "from": "dice", "min": 12, "max": 24, "gender": "f" },
  "lines": [
    { "who": "luka", "sl": "Jan, preštej ovce pri vodi!", "en": "Jan, count the sheep at the water!" },
    { "choices": [
      { "sl": "Pri vodi {je|sta|so|je} {n} {ovca|ovci|ovce|ovc}.", "en": "There {is|are} {n} {sheep|sheep} at the water.", "ok": true,
        "reply": { "sl": "Tako je. Bela je spet na hribu.", "en": "That's right. Bela is up on the hill again." } },
      { "sl": "Pri vodi {je|sta|so|je} {n} {!ovca|ovci|ovce|ovc}.", "en": "There {is|are} {n} {sheep|sheep} at the water.", "wrong_form": 1,
        "why": { "en": "{One takes the singular|Two take the dual|Three and four take the plural|From five on, the genitive plural}: {n} {ovca|ovci|ovce|ovc}." },
        "reply": { "sl": "Hm? Štej še enkrat, počasi.", "en": "Hm? Count again, slowly." } }
    ] }
  ]
}
```

| Field | Rules |
|---|---|
| `count.from` | where the number comes from: `dice` (the day's dice, `min` to `max`: the same all day, another the next day), `food`, `wood`, `stone`, `wisdom` (what the village has in store), `villagers` (how many live in the village), `building:<type>` (how many of that type stand: `building:house`) |
| `count.min`, `count.max` | the number is kept within them (1 … 999; `dice` needs both) |
| `count.per` | a store counted per this many: `wood` 73 with `per` 10 is 7 logs |
| `count.gender` | the gender of the counted thing in the scene's language, for the number word: `m`, `f`, `n` (Slovene: en/ena/eno, dva/dve, trije/tri; German: ein/eine; Italian: un/uno/una/un') |
| `count.case` | `nom` (the default) or `acc` ("Imam {n} …": eno ovco, tri konje; German einen); `count.animate`: a Slovene masculine that is alive (enega konja) |

In any text of a dialog with a count:

- `{n}` is the number: written out in the scene's language where it is said (the lines, the choices, the replies: "dvajset",
  "zwanzig", "venti", "twenty"), and in a `why`, which quotes the scene's language; in figures in the translations ("20").
  Capitalised at the start of a sentence.
- `{a|b}` and `{a|b|c|d}` are the forms that agree with it: two for one and more (Italian, German, English: `{Schaf|Schafe}`,
  `{c'è|ci sono}`), four for Slovene's 1, 2, 3–4 and 5 and more (`{ovca|ovci|ovce|ovc}`, `{je|sta|so|je}`), by the last two
  figures: 101 like 1, 102 like 2, 103 like 3, 111 and 121 like 5 (`Numbers.category`).
- A wrong choice with `wrong_form` (1 or 2) takes a wrong form where its right one would agree: 1 the one learners most
  often put there, 2 the next (Slovene: for 1 the 3–4 form, then the dual; for 2 the 3–4 form, then 5+; for 3–4 the 5+
  form, then the dual; for 5+ the 3–4 form, then the singular; in two forms, the other one). Its forms marked `!`
  (`{!ovca|ovci|ovce|ovc}`) are the wrong ones and the others agree; without a mark all are wrong. Its `why` agrees (it
  explains the right form) and its reaction has no `{…}`: it never says the right form. A wrong form that comes out the
  same as the right choice (English "sheep") is left out; a turn keeps a wrong choice at every number. It names the
  grammar book's page of numbers ([A why that is a rule](#a-why-that-is-a-rule-of-the-grammar-book): `stevila-samostalniki`,
  `plurale`, `plural`, `plurals`), as the curated ones do.

The app renders the dialog when it starts (`Counts.render`, `Numbers` in `game/scene/Numbers.kt`), with the number
from `GameState` (`Counts.of`: the day's dice is `Happenings.roll(seed, date, "scene/happening/dialog#n")`). A text
of a dialog without a count has no `{…}` of a count (the learner's, `{learner}` and `{m:…|f:…}`, are no counts: any text
may say them, [The learner in the content](#the-learner-in-the-content)). The voice cache leaves the counted texts out (they are voiced when played,
like a story's lines). An older app plays a happening's `dialog` only, and the bridge rejects a `dialog` with a count
(older apps would show `{n}`): a counting dialog is a variant.

### Adaptive turns

A turn that tests a form, one where a wrong choice differs from the one right choice in a single word ("Deset **jajc**,
prosim." / "Deset **jajce**, prosim."; `Forms.turn`: the word most wrong choices differ in alone is the one in question,
a wrong choice that differs otherwise is no part of it), is asked by how far the learner has its rule (GAME.md, "Mastery
and adaptive turns"): chosen while they learn it; once it is secure, typed into a gap ("✍️ Napiši manjkajočo besedo ·
Type the missing word", "Hočeš reči · You want to say: Ten eggs, please.", "Deset ____, prosim." with the letter chips);
once it is mastered, said whole ("🎤 Povej cel stavek · Say the whole sentence"). "✋ Izberi raje · Let me choose" and a
wrong answer bring back the choices (`DialogRun.letMeChoose`, `type`, `say`); a form typed that is a wrong choice is that
choice picked, with its reaction. A turn whose rule isn't introduced to the learner yet isn't asked for at all: its form's
wrong choices go (see [Rules not yet](#rules-not-yet-what-the-learner-is-asked-grows-with-the-grammar)).

While the keyboard is up for a typed turn, the panel keeps above it and takes the picture's room (`ui/scene/DialogRoom.kt`;
the app draws edge to edge, so the window doesn't shrink for the keyboard): in a scene and on a visit it rises over the
picture to just under the status bar, the top bar out of the way; in a keeper's talk and an arrival the portraits fold
away. The turn takes the room it needs and the lines keep the rest, at least the last line said; on a small screen (or
with large fonts) the turn scrolls instead, kept down at the sentence with the gap, the field, the letter chips and
"Preveri · Check". Once the keyboard is down, the picture comes back; a tap turn, with no keyboard, keeps it. The
keyboard is an answer's (README.md, "Answer fields and the keyboard"): no suggestion strip and no auto-correct. It
opens in the scene's language (Slovene at home, the town's language on a visit), and the learner can switch it.

The rule a wrong choice tests is its `grammar` (or its turn's), as [above](#a-why-that-is-a-rule-of-the-grammar-book):

```json
{ "sl": "Deset jajce, prosim.", "en": "Ten eggs, please.", "why": "From five on, the genitive plural: deset jajc.",
  "grammar": "stevila-samostalniki", "reply": { "sl": "Samo eno jajce? Deset?", "en": "Only one egg? Ten?" } }
```

When a Slovene scene names no page at all (a tutor's, or one an older bridge served), the app guesses them from the
whys' English words (`Forms.rule`, in `inPair`), only to decide how a turn is asked and which trap fits. First where
something is with pod, nad, pred, za, med and the instrumental ("Za takes the instrumental: za vrati", "Behind it: za
smreko") → `orodnik`, unless the why names a direction first (then the right one is where it goes). Then the rules a why
states in a phrase of its own: what a question word asks ("Kje asks where") → `vprasalnice`; perfective, imperfective,
"(done)" → `glagolski-vid`; an ordinal, "the first one" → `datumi`; "is more than one" → `mnozina`; the supine, grem
spat → `namenilnik`; a relative clause, ki → `oziralni-ki`; reported speech → `odvisni-govor`; a joining word →
`vezniki`; a clitic's "fixed order" → `naslonke`; "doubles the no" → `zanikanje`; "needs
se", bati se → `povratni-glagoli`; ob with an hour, a day, čez, pred eno uro, "how long" → `kdaj-cas`; the comparative →
`primernik`; the adverb → `prislovi`; "after moram", "lahko goes with" → `modalni-glagoli`; rad bi, the conditional →
`pogojnik`; "…: the imperative" → `velelnik`; "the future, …", "bom + the l-form" → `prihodnjik`; "takes the dative",
"…: the dative", všeč → `dajalnik`; jih for a plural → `osebni-zaimki`; jo or ga by the noun's gender →
`spol-samostalnikov`; o (about) → `mestnik`; z/s with the instrumental → `orodnik`; "the name stays as it is", "…: the
nominative" → `imenovalnik`; malo, veliko, koliko, več, kos with the genitive → `rodilnik-kolicina`; whose → `rodilnik`; svoj →
`svoj`. Then the broader ones: a number that "takes" a form, "from five on", "koliko" → `stevila-samostalniki`; "the
dual" → `dvojina`; "vi", "polite", "is older" → `ti-vi`; a negation with the genitive → `rodilnik-nikalnica`; iz, od,
do, brez, z/s with the genitive → `rodilnik-predlogi`; a place set against a direction → `kje-mestnik-orodnik` or
`kam-tozilnik`, whichever the why names first (the right one's; "where it goes is a direction" is a direction); the
instrumental of an older why → `orodnik`; the locative → `kje-mestnik-orodnik`; the accusative, the object → `tozilnik`;
masculine, feminine, neuter, "agrees" →
`pridevniki-ujemanje`; a person ("Kuham is 'I cook'"), a verb's plural, the imperative, the infinitive →
`glagoli-sedanjik`; "je is for one", biti → `biti`; a question word → `vprasalnice`; the past participle →
`pretekli-cas`. A word's meaning or a greeting's hour has no page: such a choice has no rule, and its turn is always
chosen. More than nine in ten of the curated scenes' form choices would get their page this way (`FormsTest`; the guess
says the page a scene names for 726 of its 770 named wrong choices); they name theirs. A guessed page counts nothing. A
word of the guesses that starts or ends in č, š, ž is fenced by lookarounds, not `\b`: the JVM's `\b` (Java 19 on) sees
only ASCII letters as a word's, the phone's every letter.

The turns' answers count on their rules in the grammar book, the first try of each (right: on every rule of the turn; a
wrong pick: on its own rule, once a turn), on the page the turn names, which it unlocks as a pick does ([above](#a-why-that-is-a-rule-of-the-grammar-book)). A
story's turns are reading practice: never typed or said, never counted on a rule.

### The hint: the rule, never the answer

A turn whose rule has a page in the grammar book shows "📖 Namig · Hint" beside the 🎤 (beside the title of a typed or
said turn; alone where nothing listens), in a turn to choose, to tap in the picture, to type or to say. A tap opens a card
under it with the rule the turn tests, never the answer (`game/TurnHint.kt`, `TurnHints`; docs/grammar-syllabus.md, "The
hint chip": the bare case question doesn't help a learner, its trigger and a model do):

- **the question and the case** the missing word takes, as a Slovene teacher asks it, with what it means in the learner's
  base and, where German works the same, the German: "Koga? Česa? → 2. rodilnik (genitive)", "Kam? (where to? German:
  wohin?) → 4. tožilnik (accusative)". A preposition that takes two cases gives both, the page's first: "Kam? … 4.
  tožilnik" and "Kje? … 5. mestnik" after v, which the sentence's meaning decides;
- **what decides the form in this sentence** (`game/Triggers.kt`): the preposition before the gap ("after «v»"; the
  cases each takes, as the lookup narrows by them), the number and its category ("after «Deset»: five and up, the
  genitive plural"), an amount ("after «Koliko»"), a no in its clause ("after the no «nimam»"), a verb whose partner takes
  a case ("with «pomagam» (like German helfen + dative)"), a number's noun ("with the noun «brata»: by its gender"), and
  for a rule that isn't a case, who does it or when ("who: «Midva»", "when: «jutri», the future");
- **the rule in words**, for a page that isn't about one case ("Ti ali vi? Who are you talking to? Someone older, a
  stranger, in a shop: vi …", "Kaj delam? Kaj naredim? Going on … the imperfective"), or whose rule needs saying (Kje?:
  v, na, ob, pri + mestnik, pod, nad, pred, za, med + orodnik);
- **a model with other words**, one with the trigger's own word first ("Like: pet hiš · pet stolov · pet jabolk" at
  "Deset ____, prosim."; "pod mizo" after «pod»), and the rows of the page's table about the trigger (the 5 + row of the
  numbers page, the rows of v and na on the Kam? page, the orodnik page's row of its preposition);
- "📖 V knjigo · To the book: <title>" for each of its pages, the page on a sheet over the dialog.

Its rule is the pages its wrong choices name (at a form turn, those of the form's wrong choices), else the turn's, else the
right choice's (a tap turn names it there), else the page the app guessed from the whys (`DialogChoice.rule`). The cases'
questions, the pages' rules in words and the models are a small table in the code, not a field of the pages; a page the
table lacks (a tutor's) shows its link alone. A turn of no page has no hint. A dialog in another language than the book's
gets its pages' links alone. A rule not introduced to the learner yet gets no hint of its own, only "🌱 Na vrsto pride
kasneje · You'll learn this later: …" ([Rules not yet](#rules-not-yet-what-the-learner-is-asked-grows-with-the-grammar)).

**Never the answer** (`TurnHints.fair`): no model shows any form of the turn (the right one, a wrong one, or a word of their
stem: no "pet ovc" at "dvanajst ____"); a line, a row of the table or a page's title that shows the right form is left out,
unless it sets every form of the turn side by side (a rule that is the choice itself: "dva, dve by the noun's gender"). A
title left out leaves the link without it ("📖 V knjigo" at "Nimam ____.", whose page is "Nimam časa: …"). `TurnHintTest`
checks it over every curated Slovene turn (668 with a hint; 296 of the 313 form turns of a case page find what decides
their form).

**The 🔊 in the card's top-right corner** ("Preberi namig · Read the hint") says the hint in short, about 8 to 15 seconds,
built from the same data (`game/HintSpeech.kt`, the `hintSay.*` keys): what applies here, the rule in one sentence, the
question to ask and a model, e.g. "After «dvanajst», five and up: the noun goes into the genitive plural. Ask: «Koga?
Česa?» Like: «pet hiš», «pet stolov», «pet jabolk»." A turn of a rule not yet says only "You'll learn this later: …".
The learner's base is said by the phone's own voice (rendered to files as the car's prompts, `RoadPrompts`, and kept in
the cache); the Slovene (the trigger, the questions, the models, the words of a rule: marked «…») by the app's voice, the
voice store's clip or the node's live voicing as a line's 🔊, else the phone's Slovene voice, else left out; then all of
it plays in one player, without gaps (`data/HintVoice.kt`). A tap again stops it, and so does a line said, the 🎤
opening, the card closing or the turn going on; the background sounds duck under it. Each sentence is `fair` and its
Slovene shows no form of the turn (`HintSpeechTest`, over the curated turns: 661 of the 668 hints say something).

**Counting:** a right answer after the hint (opened once at the turn: `DialogRun.hint`, `hinted`) counts right on its rule
and keeps its sentence, but leaves the rule's run as it was ([GAME.md](GAME.md#mastery-and-adaptive-turns)), so what makes
a rule secure or mastered is answered without help. A wrong one is a slip, as ever. Nothing else changes: the reward, the
mistakes, the reaction.

### The sentence's grammar: a long press on a line

A long press on a line said, the other person's any time and the learner's own once said (never a choice before it is
picked: that would give the answer away; a long press on a word of a choice still opens its card), opens "🔍 Slovnica
stavka · The sentence's grammar" (`ui/words/SentenceSheet.kt`, `app/SentenceController.kt`): the line with 🔊 and its
translation, then each word:

- as written, its dictionary form and reading, looked up in the line as a word card does (GET /lookup: the dictionary
  narrows a noun by the preposition before it), and narrowed further by what the sentence decides (`game/SentenceGrammar.kt`,
  `WordReadings`): a number's category (after dvanajst the genitive plural), a no, a verb whose partner takes a case
  (pomagam babici: the dative, not the locative); a noun in its dictionary form reads as the nominative or the
  accusative like it (v šotor);
- why that form, where a rule knows, in one line with its trigger: "*krav*: Koga? Česa? → 2. rodilnik · after «dvanajst»:
  five and up", "*šotor*: Kam? → 4. tožilnik · after «v»", "*mi*: Komu? Čemu? → 3. dajalnik · with «mrzlo»", "*kruha*:
  after the no «nimam»"; a verb's person ("who: jaz") or tense (the l-form with sem: the past; with bom: the future; with
  bi: would; the imperative; an infinitive after moram); an adjective's gender;
- its page as a 📖, on a sheet over this one; a tap on the word opens its card.

A word no rule explains shows its form alone. Without the node, the words alone, with what needs no dictionary (the case
after iz, a number's category). "💬 Vprašaj učitelja · Ask your tutor" sends the line to the chat (`data.about_sentence`:
the sentence, its translation, the scene, the dialog, the person, the learner's own line's pages, and the words as read
here); the tutor explains it and keeps the explanation (`publish_sentence_note`, `<data>/app/sentences.json`), and the sheet
shows it ("🧑‍🏫 Učitelj razlaga · Your tutor explains") the next time the same line is long-pressed (GET /sentence, case
and punctuation aside; `sentence_explained` updates a sheet open on it). An older bridge has no GET /sentence: the sheet
shows no explanation, and the chat still goes. TalkBack has "Slovnica stavka · The sentence's grammar" among the line's
actions, and as its long press.

### Own traps

Where a turn tests a form, the wrong choice shown is, when there is one, a form the learner actually mixed up before
(`game/Traps.kt`, from their mistakes-db examples: what they had, what was right, and the words swapped), else the
file's:

- **Their very word:** who wrote "nimam vžigalico" meets "Nimam vžigalico." at "Nimam vžigalic.", in a turn of that
  rule (the mistake's pattern names the turn's page by the page's tags) or of a rule not known.
- **Their confusion on another word,** in a turn of the same rule: "Lahko ena kava?" for "Lahko dobim eno kavo?" makes
  "Pijem voda." at "Pijem vodo." (the same change of ending), but only a form known to be Slovene (a word of the scenes,
  the grammar book or the learner's cards), never one made up.
- A spelling or a hearing slip ("Babiča", "še vedno" for "se vidimo") sets none; where the file's choices have the
  learner's confusion already, nothing changes.

The trap takes the place of the form's wrong choice that tests the same rule (else of its last one), so the turn keeps its
count of choices; it means what the right one means, its why says what the learner mixed up ("To si že zamešal: «nimam
vžigalico» (prav: «Nimam vžigalic.»)"), and the person reacts to it with the turn's `puzzled` reaction:

```json
{ "choices": [ … ], "puzzled": { "sl": "Kako? Ne razumem.", "en": "What? I don't understand." } }
```

Without one, "Hm? Kako, prosim?" in the scene's language, meant in the learner's base. The same reaction answers a form
typed wrong that is none of the choices. An older bridge drops `puzzled` (the app's then). A choice exercise's options
get the learner's trap likewise (`Traps.options`). A learner with no mistakes gets none: their turns are the file's. A
turn of a rule not yet gets none either ([below](#rules-not-yet-what-the-learner-is-asked-grows-with-the-grammar)).

### Rules not yet: what the learner is asked grows with the grammar

A dialog can only be made so easy before it is silly, and hearing the language as it is spoken is how a learner gets a
feel for it. So what the others say stays natural, above the learner's level too; what the learner must **choose or
produce** grows with the grammar introduced to them (`game/Introduction.kt`; GAME.md, "Rules not yet"). At the start of A1
nobody can tell whether "z mamo" or "z mama" is right: the instrumental isn't theirs yet.

A rule (a page of the grammar book) is **not yet** for this learner when its page's `level` is above theirs (the village
language's level, the one their readings and stories are told at) and it wasn't introduced: its page isn't unlocked (a
module, the tutor's page, the book, a why), and they have no answers, cards or mistakes on it. A rule at or below their
level, or one introduced, is asked for as [above](#adaptive-turns). A turn that asks for a rule not yet (a wrong choice's
page, the turn's, else the guess: [A why that is a rule](#a-why-that-is-a-rule-of-the-grammar-book)) doesn't:

- **Its wrong choices of that rule go**, and so do the other wrong choices of its form (the one word they differ in:
  `Forms.turn`), since the form isn't asked for at all. A wrong choice about meaning stays (other words, or one word of
  no rule: "Grem sam." or "Grem z babico." beside "Grem z mamo."), and the learner chooses about meaning.
- **With nothing wrong left, it is an echo turn** (`TurnMode.ECHO`, `DialogRun.echo`): "🔁 Poslušaj in ponovi · Listen and
  repeat", the right line with its translation (always shown: it is what they say) and 🔊, the 🎤 where something listens,
  and "✓ Naprej · Next". Said close enough (graded like a speak exercise, or most of its words heard), it goes on; else
  "Slišal sem «…». Še enkrat ali tapni Naprej." and it waits. Nothing is graded, no mistake is possible, no answer counts.
- **Its hint** ("📖 Namig", [The hint](#the-hint-the-rule-never-the-answer)) leaves the rule not yet out, and says only
  "🌱 Na vrsto pride kasneje · You'll learn this later: Orodnik: s kom? s čim? · The instrumental …", without a link: opening
  the page would introduce the rule. A trimmed turn with another rule gets that rule's hint too.
- **No own trap** is set in it ([Own traps](#own-traps)), it is never typed or said whole ([Adaptive
  turns](#adaptive-turns)), a wrong pick's why leads to no page of a rule not yet, and a right pick counts nothing on it.
- **A tap turn** is tapped as ever: finding a place isn't a form. A right tap on a page not yet (orodnik, za vrati) only
  meets it.

**Meetings.** Each such turn passed (an echo, a trimmed turn, a tap turn) meets its rules once (`DialogRun.meetings`,
`GameState.meetings`: how many times, on how many days, the first and the last). The book lists them under the pages met,
greyed and closed: "🌱 Orodnik · A2 · Srečano v pogovorih: 5× · Met in the dialogs: 5×". Met 3 times (`Introduction.RIPE`),
a rule of the next level up is **ripe to introduce**: the tutor hears of it (GAME.md, "Rules not yet") and introduces it
with a module or the page. Met 6 times on 2 days or more (`UNLOCK`, `UNLOCK_DAYS`), the book opens the page itself, "📖
Nova stran v knjigi · New page in the book: …", and from then on its turns ask for it (new: chosen). A rule two levels
up waits for the learner's level: met as often as it may be, a B1 page stays closed at A1. Never a wall: no tutor needed.

Over the curated Slovene scenes (`IntroductionContentTest`), of 541 learner's turns (459 test a form) a fresh A1 learner is
not asked for the rule of 141: 68 echoes, 71 chosen about meaning, 2 tap turns only met; the dative (37) and the
instrumental (37), then the genitive of quantity (28), the comparative (16), the genitive after iz, od, do (15), the
personal pronouns (7), the conditional (4), the genitive (3), svoj (2) and aspect (1). An A2 learner is asked for all of
them. A keeper's talk at a spot of the landscape is trimmed like a scene's dialog; a visit, a story, an arrival and a
dialog in another language than the book's are as they were. An older bridge changes nothing: it is all the app's, and
the meetings ride in the village state, which a bridge keeps as it is.

### Your words in the dialogs

The learner's review cards come into the dialogs: a turn that **tests** one of their words counts as meeting it in
context, a review of its card, as «Vidim, vidim»'s quick find is ([GAME.md](GAME.md#your-words-in-the-dialogs): when, and
how much; one rule for words met in play, once a card a day). A word that is only
there counts for nothing: picking the right one of three sentences that differ in a greeting shows nothing about the other
words in them. A turn tests a word of its right choice (`game/DialogWords.kt`, `DialogWords.turn`):

- **a choice turn** when a wrong choice differs from the right one in that word: another word in its place, a distractor
  of its meaning ("Daj mi **žlico**." / "Daj mi **vilice**."), or another form of it ("Deset **jajc**." / "Deset
  **jajce**.": the grammar book's rule counts it too, [Adaptive turns](#adaptive-turns)). The two are aligned word by word
  (a longest common subsequence, case and punctuation aside, like `Forms.turn` but for any length): the words of the right
  choice off it are where they differ, and a wrong choice tests them when they are one or two and at least as many are
  shared ("Daj mi eno žlico." / "Daj mi dve vilici." tests eno and žlico; "Ja, zelo." / "Dobro jutro!" tests nothing; a
  one-word answer, "Žlica." / "Vilica.", always). A word said in its place that is a form of the same word ([word
  forms](GAME.md#a-words-forms), GET /forms), or looks like one (the same stem, another ending), or a wrong choice that
  names a page of the book, is its form; one of the learner's other words, or another word, its meaning. With two right
  choices, a wrong one gets wrong what it tests of the one it nearly copies ("Kdo je to? Toliko čebel!" for "Kaj je to?
  Toliko čebel!", not for "Ojoj, čebele! Ali pičijo?": kaj);
- **a typed turn**: the word in its gap (the form turn's word), typed right or wrong;
- **a said turn**: the word in its gap, when the sentence said passes ("only the word graded");
- **a tap turn**: the thing its right place is, when it is one of the learner's words (the scene's object at that slot);
- an echo turn tests nothing, and neither does a line said by someone.

The learner's words are their one-word vocabulary cards of the home deck (`PackSession.wordKeys`: "dober dan / živjo"
gives živjo), each with the forms of its word the phone has (`FormsController`), checked against the dialog as it is
played (trimmed, with their own traps); a dialog in another language (a visit) tests none. It works wherever the dialog
panel plays: a scene's dialogs, the curated ones and the tutor's variants, the storyteller's evening story, a keeper's
talk, a newcomer's arrival and a village project's step (whose turns are written to test the project's words). Of the 494 learner's turns of the curated Slovene scenes, 227 test one of the packs'
one-word words, 143 different ones (`DialogWordsContentTest`, the words' forms from the bundled dictionary).

**In the dialog** (`DialogRun.wordAnswers`, the first answer of each turn on its words; `DialogWordsController`): a right
first answer is right on the word; a wrong one is wrong on the words it tests, once a turn, and once the turn is passed the
word's card shows in the conversation after it, "📇 Tvoja beseda · Your word: žlica — spoon" and the form the sentence
wanted ("tukaj · here: «žlico»"), a tap opening its whole card; the dialog goes on as before. A wrong form is a slip on
the rule as ever. At the end, under the result: "📇 Tvoje besede · Your words: žlica ✓, krožnik ✓, vilice ✗", each word by
its first answer (a tap opens its card), "🔁 Šteje kot ponovitev · Counts as a review: žlica" for those it reviewed and
"↩️ Spet jutri · Again tomorrow: vilice" for those a bridge that takes them lowered.

**In the village:** the scroll's "Today" says under a happening on now how many of the learner's words the dialog it plays
today tests: "Babica Micka · v kuhinji", "📇 3 tvoje besede · 3 of your words" (`TownOverview.of`, `inHappening`; a
story's evening says none, its telling is the evening's).

**The tutor weaves them in.** A tutor's variant may say which of the learner's words it weaves, `words`: lemmas or card
ids, 1 to 6 (`"words": ["žlica", "vocab_v-kuhinji_krozniki"]`). `publish_dialog_variant` (and `publish_scene`) checks each
is the tested element of a turn by the rule above (`bridge/src/dialog-words.ts`) and refuses the dialog otherwise, naming
the word and how to fix it; a lemma that isn't one of the learner's cards publishes, said so. An older bridge strips the
key (a `z.object`), and the app reads past it: it finds the tested words itself. The bridge hands the tutor the words to
weave, with the happenings they fit, in `dialog_variants_heard` and in the morning plan ([Variants](#variants-not-the-same-dialog-every-time)).

### In another language

A scene's lines are in its village's language (`language`), each with its translations: `{"sl", "en"}` in a Slovene
scene as above, `{"it", "sl", "en"}` in an Italian one (English always; the bases the village serves, Slovene for the
second learner; the curated scenes and stories have German too, and the Slovene ones Italian: a scene outside a culture pack says
so in its `review`). The same goes for a choice, a reply and a memory; a wrong choice's `why` is English, or per
language (`{"sl": …, "en": …}`: the learner reads it in their base). The bridge checks that every text has the scene's language
and English, and serves the objects with the word in every language its pack has (`it`, `sl`, `en`, `example_it` …).
The app reads a scene in the learner's pair (`inPair` in `SceneSpec.kt`): what is said in the scene's language, what it
means in the learner's base, else in English; the second learner reads "Sei di fuori?" meant as "Nisi od tod?", and Jan, visiting
some day, the same line meant in English.

```json
{ "who": "nives", "it": "Ciao, ciao! Sei di fuori?", "sl": "Živjo, živjo! Nisi od tod?", "en": "Hello, hello! You are not from here?" }
```

### Scenes of a culture pack

A culture pack ([GAME.md](GAME.md), "Culture packs") may bring places only its village has, in `scenes/` next to its
other files: the same format, in the pack's language, the people of its own cast (every person a `villager` of it), the
words from its own word packs (`packs/`). The bridge serves them for a village of that culture after the curated
scenes of its language (`scenesDirs`, as `packsDirs` does for the word packs), and says which pack each comes from
(`culture`); a village of another culture doesn't get them. `validateCulture` checks them with the pack.

Each pack has its landscape at the horizon, which the village opens from `spot:horizon`: tapping the hills and mountains
behind the village (the band from the Alps' shoulders down to the horizon, what the buildings, the people, the spots and
the forest there don't take) opens a place card named for what the pack's `world.json` says is there (`backdrop`: "Gore ·
The mountains", "Il mare · Morje"), and its "Vstopi · Go in" leads into the scene, as the fire's card does. Without a
scene there, a tap on the hills is a tap on nothing, as it always was. A pack may also have places its village projects
lead into (`project:<id>`): Primorska's vineyard opens once the village has finished its terraces (the project
`vinograd`); then a tap on the terraces on the map opens their place card with "Vstopi · Go in" and the project itself,
where before it opened the project straight away.

| Pack | Scene | From | What is there |
|---|---|---|---|
| `primorska` | `v-gorah`, V gorah · In the mountains (`alps`) | the horizon | a mountain pasture above a lake under Triglav, the way Bohinj looks from Vogar: the lake with the peaks mirrored in it, the little church and its stone bridge on the shore, the shepherd's hut, cows with bells, a larch and a spruce, a stream with a waterfall, a big stone, a dry-stone wall with a step stile; Janez (a guest's first time in the mountains, the choughs off the peak and a storm over Triglav), Luka (the cows' bells, cheese from the pasture; milking at dawn while the peaks glow pink), Nejc (fishing in the morning fog, the church bell, a fish jumping); chamois by the forest and an ibex on a crag, by day |
| `primorska` | `na-gricih`, Na gričih · On the hills (`hills`) | the vineyard project | the village's vineyard, its rows running away to a white farmstead with its cellar, cypresses and trees about it, the rolling hills of Brda with the church on a hilltop, the Alps and a glint of the sea; the kaki behind a dry-stone terrace wall, lavender; Marko (the starlings in his vines, a first visit), Janez (the bell against a storm from the Vipava valley, into the cellar), Tine (grandpa's tractor, counting crates) |
| `friuli` | `al-fuoco`, Al fuoco · At the campfire (`campfire`) | the fire | the village's campfire, in Italian: Nonna Rosa's tisana in the evening, Tommaso bringing wood in the afternoon, and Nonno Bepi's evening story ([Stories](#stories-the-evening-story)). |
| `friuli` | `il-mare`, Il mare · The sea (`sea`) | the horizon | Grado: the lagoon with its casoni, the bricole and the gulls, the pier and the batela, the beach and the open sea; Zia Nives (a guest, the gulls), Tommaso (the waves, a squall over the lagoon), Nonno Bepi (sea mist, then the sail) |
| `kaernten` | `am-feuer`, Am Feuer · At the campfire (`campfire`) | the fire | the village's campfire, in German: Oma Resi's tea in the evening, Maxi gathering wood in the afternoon, and Opa Sepp's evening story ([Stories](#stories-the-evening-story)). |
| `kaernten` | `am-see`, Am See · By the lake (`alps`) | the horizon | the Faaker See under the Mittagskogel in the Karawanken: the turquoise lake, the peak and the mountains, the little church on the shore, the wooden bridge, the Alm with its hut, cows and cowbells, larches and spruces, a waterfall; Opa Sepp (a guest's first time, a storm over the Mittagskogel), Florian (the cowbells on the Alm; milking at dawn while the peaks glow), Maxi (fishing in the morning fog); chamois and an ibex |
| `lakeland` | `by-the-fire`, By the fire (`campfire`) | the fire | the village's campfire, in English: Granny Maggie's hot chocolate in the evening, Harry bringing wood in the afternoon, and Old Wilf's evening story ([Stories](#stories-the-evening-story)) |
| `lakeland` | `on-the-fell`, On the fell (`alps`) | the horizon | a fell above a lake in the Lake District: the little chapel and its packhorse bridge by the shore, the shepherd's stone hut, a drystone wall, a force (waterfall) and a beck; the painter's chamois and ibex are a wild goat and a Herdwick ram here; Old Wilf (a guest's first time on the fells, the ravens off the crag), Shepherd Jack (Daisy the cow's bell, the fells pink at sunset), Harry (fishing in the morning mist, the chapel bell, a fish jumping) |

Visiting another town (plan 2, "See the host's own places") is how a learner walks into another culture's scenes. One
dialog in each is meant for a guest (`v-gorah/prvic`: "Si prvič v gorah?", `na-gricih/prvic`: "Si prvič na gričih?",
`il-mare/fuori`: "Sei di fuori?", `on-the-fell`: "Is it your first time on the fells?"), marked for guests ([Dialogs for
guests](#dialogs-for-guests)).

### Dialogs for guests

A happening can say `"guests": true`, or its dialog can: it is meant for a visitor from a linked town (companion/README.md,
"Things to do on a visit"). The village's own learner never meets it: their bridge leaves it out of `GET /scenes`
(`forHost` in `bridge/src/scenes.ts`: the happening and the dialogs only it plays), and the app's `Happenings.active`
leaves it out at home too. A visitor's bridge gets the scene whole (`GET /town/scenes`), and on a visit
(`Happenings.active(…, guest = true)`) it is on like any other happening: its person talks with the visitor in the
scene, played to the end, with nothing paid (the visitor's village keeps whom they met). A guest's dialog greets anyone:
it names nobody ("Ciao! Si prvič na gričih?", not "Ciao, Jan!").

### What's on today

`Happenings.active` decides, the same for the whole day and time of day: the scene must be open (its
place is built, the age reached), its person must be in the village and met (someone who joined and hasn't been
introduced has no happening, and isn't "here today" or asleep in a scene: VILLAGERS.md, "Arrivals"), and so must
everyone of the cast its dialog, its title and its memory name (VILLAGERS.md, "Who a text may name": Zala's "Jan! Anton
toči med!" waits until Anton lives here and Jan has met him; the beehive brings him), the happening's
`when`, `weekdays` and `needs` must fit (`dawn` is no part of the day
of its own but the morning's first hours round the sunrise of the month, `TimeOfDay.dawn`: 5–6 in June, 6–8 in December,
so a happening `when` the morning is on then too, one `when` dawn only then; the scroll lists one still to come under the
morning) (the age, and what the
village has built: Luka brings the hay in only where a kozolec stands), it must not be done today, and the day's dice
(`roll(seed, date, key)`) must come in under `chance`. At most four at once and one per person. Done happenings are
kept in `GameState.happeningsDone` (`scene/happening` → date). A place where someone sleeps (a painter with a bed, see [Asleep at night](#asleep-at-night)) is quiet at night: a
happening there without a `when` (all day) doesn't come up from 22 to 5; the ones that name `night` (Zala's story and
Babica's blanket in the tent) and the evening's are as they were. The storyteller's evening story is on until 23:30, then he
goes home to bed ([Stories](#stories-the-evening-story)).

### What the village has built

A scene shows the village as it is: the kozolec stands in the field only once the village has built one, France's
mill by the stream grows with its project, step by step, from pegs and a string to the wheel turning, and a room grows
with its building's level: a hut's kitchen at level 1 is bare, at level 3 the clock ticks over the painted dresser. The
painters get `SceneFrame.world` (`game/scene/SceneWorld.kt`: the age, which buildings stand and at what level, a damaged
one too, each village project's steps done, from `GameState`, and the building the scene is seen in, `here`) and draw
each fixture only when it's there, a project's as far as its steps have come. `SceneScreen` passes the real village;
everything else (the training stage's backdrop, the tests, tools, and whatever crops a scene's things out of its picture)
gets the default, `SceneWorld.ALL`: every building at the top level, every project finished, the scene as it always was.

**Which building a scene is seen in** (`Homes.buildingOf`): the one the learner opened it from, the hut or the house
tapped (its place card passes it: `Screen.Scene.building`, kept when the learner comes back from a word run or a talk),
else the one its place is, where its bubbles show (the first of the scene's building types that stands, the lowest plot;
a room of a house: the house it is in, see [Houses and their rooms](#houses-and-their-rooms)). That building's
level counts, not the highest in the village: the kitchen of the old hut at level 1 is bare while the new hut's, at
level 3, has everything. A happening is seen in its scene's building too (`Happenings.active`), so Micka bakes potica
only in a kitchen with a stove.

An object or a happening says what it needs with `needs`:

```json
{ "slot": "mill", "word": "mlin", "pack": "na-njivi", "needs": { "project": "mlin" } }
{ "id": "potok", "who": "france", "needs": { "project": "mlin", "before": true }, "…": "…" }
{ "slot": "clock", "word": "ura", "pack": "vreme-in-dan", "needs": { "level": 3 } }
{ "slot": "bed", "word": "postelja", "pack": "dom-in-hisa", "needs": { "level": 2, "before": true } }
```

| Field | Rules |
|---|---|
| `building` | a building type (`BuildingType`, any case: `KOZOLEC`, `tent`) that stands in the village |
| `level` | with `building`: one of that type at least at this level (1 … `Catalog.MAX_LEVEL`, 5), the one the scene is seen in when it is of that type, else the highest; alone: the place's own, the building the scene is seen in at least at this level (so the kitchen works the same from a hut and from a house). Only in a scene that opens from a building; never with `project` |
| `project` | a village project of the culture (`projects.json`) with all its steps done, or at least `step` of them |
| `step` | only with `project`: 1 … the project's steps |
| `before` | only with `building`, `level` or `project`: the other way round, there until the building stands (or gets to the level) or the project gets that far (France talks of the mill he wants until it grinds; the kitchen's straw bed is there until the hut's second level) |
| `age` | the earliest age (a happening's `needs` may still be just the age as a string, `"vas"`) |

All it names must hold. An object whose needs aren't met isn't drawn, can't be tapped, isn't among the scene's words to
find and learn (`SceneSpec.inWorld`): the find counter grows as the building is upgraded. What is there is also what the
building's next upgrade asks the learner to know ([GAME.md](GAME.md), "Znanje gradi · Knowledge builds"). A happening whose needs aren't
met doesn't come up (and has no bubble in the village). Some slots are drawn only when built whatever the file says
(`SceneFixtures`): a scene the tutor writes can't offer a word for a thing that isn't there; the curated scenes give
those slots the same `needs`, and a test checks it. The bridge rejects a building type the game doesn't have, a project
the culture doesn't have (or a step past its last), a `step` without a project, a level outside 1–5, with a project, or
alone in a scene that opens from no building, `before` without a building, a level or a project, and a need it doesn't
know. A thing that goes as the room grows (the straw bed) has its sticker cut from the level that has it
(`SceneFixtures.showing`).

| Art | Drawn only when built (a word of the scene) | Grows with a project (scenery) |
|---|---|---|
| `campfire` | `tent`: the pup tent, its guy ropes and its lantern's light (TENT, while one stands in the village: not the tent that moved to the pond) | |
| `kitchen` | by the level of the hut or house it is seen in: at level 1 (`"before": true` from 2) the open `hearth` in the corner (the pot on its trivet over the fire, the corner black with soot), the straw `bed` along the left wall; up to 2 the `shelf` with the jugs; from 2 the tiled `stove` and its pipe, the `table` laid (`bread`, `plate`, `bowl`, `glass`, `cup`, `spoon`, `fork`, `knife`, `salt`) with its `chair`s, the `curtains`; from 3 the `clock`, the painted dresser (`cupboard`, kredenca) and the holy `corner` (bohkov kot: the crucifix over a little shelf with a candle, a holy picture beside it) | with the level, not a word: a beaten earth floor and plain whitewash at 1; the tiles, the rag rug, the painted dado, the bench's cushion, the lamp over the table, a geranium on the sill and pots of them outside from 2; the stencilled border from 3 |
| `livingroom` | by its house's level: at 1 the plastered `stove` (kmečka peč) with its `bench` (the zapeček, Muri the `dog` curled up on it), a bare `table`, a plain `chest`, the `window` and the `door`; from 2 the `chair`, the `curtains`, the rag `rug`, the `lamp` over the table, the `clock`; from 3 the holy `corner`, the wedding `photo`, Janez's `book` and his `glasses` on the table | a beaten earth floor and plain lime at 1; the stove green-tiled with a rail of drying socks, a plank floor, the painted dado, a cloth on the table, the chest painted and pots of geraniums outside from 2; the stencilled border from 3 |
| `workshop` | by its house's level: at 1 the `workbench` with the `hammer` and a tin of `nails`, the `boards` against the wall, the `axe` in its chopping block, the `broom`, the `window` and the `door`; from 2 the `saw` over the bench, the tool board (`tools`), the `ladder` and the `rake`; from 3 the `wheelbarrow`, the wooden `horse` France carves for Tine and the storm `lantern` | a beaten earth floor at 1; planks strewn with shavings from 2 |
| `cellar` | by its house's level: at 1 one barrel (`barrel`), the `press`, the `stairs`, the `door` and the barred `window`, the `candle`, the `jug` under the tap, the `glass` and an empty `crate`; from 2 the `shelf` of bottles (`bottle`), the `grapes` in the crate, the demijohn of `wine`; from 3 the pršut hanging on the wall (`ham`) and a wheel of `cheese` on the table | stone flags and a second barrel from 2, a third barrel from 3 |
| `attic` | by its house's level: at 1 Nejc's `bed` with its `pillow` and patchwork `blanket`, his old `cradle`, a plain `chest`, the `roof`, the gable `window` and the `stairs` coming up through the floor; from 2 the `wardrobe`, the `rug`, the `lamp` from the ridge beam, Nejc's `ball`; from 3 the `mirror` and a teddy bear on the chest (`toy`) | the red tiles showing between the battens, rough boards and rough plaster at 1; the roof lined with boards, the chest painted, whitewash from 2 |
| `smithy` | by its level: the `bellows` and the `chain` from 2; the `wheel` and the `axe` from 3 | a second, smaller anvil on its stump in front of the wheel from 3 |
| `school` | by its level: the `map` and the `globe` from 2; the `clock` from 3 | |
| `field` | `kozolec` and `hay`: the hayrack and the hay on it (KOZOLEC); without it the rake leans on the fence | the double hayrack beyond the wheat (`toplar`): oak logs, stone feet, eight posts, the poles, the plank roof, the first hay |
| `square` | `well` (WELL); `tower`: the church with its bell tower (CHURCH) | the maypole before the church (`mlaj`): a peg with a ribbon, the spruce on trestles, peeled, the wreath and ribbons ready, raised; the tower's clock (`mestna_ura`): a slit window, the round window made for it, the face lifted in (stopped at twelve), then going and lit at night |
| `stream` | `mill`: France's mill, once it grinds (`mlin`) | the mill as it's built (`mlin`): pegs and a string, the stone footing with the timber waiting, the walls under bare rafters, the roof, the wheel and the flume still and dry, then the water on the wheel and the window lit; the cart bridge upstream (`most`): pegs and a line over the water, heaps of stones, the two stone piers, the logs, the plank deck, the railings and the cart road over it into the woods |

The plank footbridge (`bridge`, brv) is always there: a simple crossing, the cart bridge comes in addition. Happenings
that need a fixture: the hay before the rain (`na-njivi/seno`, KOZOLEC), Mojca's dual at the well (`na-vasi/dvojina`,
WELL), France milling (`ob-potoku/mlin`, the finished mill) and starting it at dawn (`ob-potoku/zora`), and before it France
at the stream talking of the mill he wants (`ob-potoku/potok`, `before`: from the first morning until the mill grinds) and
watching the water in the dawn mist (`ob-potoku/megla`). The forest, the tent, the pond, the church, the bee house, the
market, the view from the watchtower and the culture packs' landscapes (the mountains at the horizon, the sea at Grado:
another place, not the village) have nothing that waits for the village; the kitchen, the rooms of the houses, the smithy
and the school grow with their building's level (above). The watchtower's view does follow what is going on: `SceneWorld.wolves` is on while the village's wolves event
is, and then the wolves' eyes shine in the forest below at night (off by default, so tools and tests see the plain view).
The vineyard waits whole: it opens only once its terraces
are finished (`from: ["project:vinograd"]`, see [Scenes of a culture pack](#scenes-of-a-culture-pack)). The other projects
have no picture in a scene yet (the bocce court, the chapel, the bee meadow, the fire station, the playground, the fountain,
the lookout tower): they stand on the village map (GAME.md, "Village projects"). Their steps are played in the scenes all
the same, where they happen ([Project steps](#project-steps-a-village-projects-step-as-a-short-scene)).

Happenings that need a level: Micka's potica and the Sunday lunch want the kitchen's stove and table (`{"level": 2}`),
Nejc at the bellows the smithy's second level, Marko's broken wheel its third, Zala at the globe the school's second; in
the houses' rooms, France sawing boards the workshop's saw (2) and Tine's wooden horse its third, Janez's glasses and
wedding photo the living room's third, Marko's pršut the cellar's third, Nejc awake in the burja with the lamp on the
attic's second.

`SceneWorldRenderTest` renders every art's stages side by side, nothing built on the left, everything on the right
(`app/build/scene-snapshots/world/00-before-after.png`, and each stage and closer look beside it; the rooms at levels 1
to 3), and checks that each stage looks different from the one before, that the last is the default picture (a room's
third level is its top one), that the arts without fixtures look the same whatever is built, and that a fixture that
isn't there has no hit. `RoomLevelsRenderTest` renders every room (the kitchen, the houses' rooms, the smithy, the school)
at levels 1, 2 and 3, empty, side by side and one by one (`app/build/scene-snapshots/rooms/<art>-levels.png`,
`<art>-L1.png` …), and checks that each level differs from the one before and that what can be tapped is what the level has.

### Project steps: a village project's step as a short scene

A village project's step (GAME.md, "Village projects") is a short dialog that does what the step says, with the project's
leader and helpers, where it happens: choosing the spruce for the maypole is Luka and Marko in the forest, "{learner},
poišči visoko smreko!", and Jan taps the spruce in the picture (the tree has leaves, Luka laughs: "Drevo? To ima liste,
iglic pa nima!"), then says what they need to fell it ("Potrebujemo sekiro."); the trunk is peeled on the village square,
the wreath woven there with the children, the bridge's pillars set by the stream, the double hayrack raised in the field,
the town clock's gears forged in the smithy. Played to its end, the step is done: what it costs is paid, its line goes to
the chronicle and shows at the dialog's end under the project and how far it is now ("🌲 Mlaj 2/5", "📜 Fantje so smreko
prinesli v vas. Bila je zelo težka!"), and the leader grows closer, as a passed practice did it (`Projects.stepPlayed`;
the price, the leader's presence and one step a day as ever). A wrong answer in it gets the person's reaction and the turn
again, as in any dialog, so there is nothing left to pass or fail; the mistakes take nothing off.

**Where it is played** (`Projects.sceneOf`): in its scene when the village has it open now (its place built, the age
reached: the square from Vas, the field once it is tilled, the smithy once it stands), in the village's language; its
people stand on the scene's stage (`Projects.placed`: where the scene has them, Luka on the forest path, else on the art's
free spots, those who stood there making way), each line in its speaker's voice and with their face (`DialogPanel`'s
speakers), whoever speaks drawn talking and the one the learner answers reacting; a tap turn is answered in the picture
([Tap turns](#tap-turns-answer-by-tapping-the-scene)), and its cues ([Effects](#effects-the-place-joins-in): the mill's
wheel as the water comes, the bell when the clock first strikes) play there. Without its scene (the square in a Zaselek,
the chapel's road, which has no scene), it is played over the village (`ui/scene/ProjectStepScene.kt`), as an arrival is:
its people side by side on a backdrop of where it happens (the forest, the square, the field …, else the leader's place),
the leader first and big, the dialog below; a tap turn is chosen there.

**Who says what** (`Projects.cast`): the leader their own lines; a helper theirs when they are in the village and met,
else another of the project's helpers who is (a child for a child), else anyone else of the village of their age, else the
leader. So a step never waits for its helpers (they needn't live here: GAME.md), and its texts name only its leader
([VILLAGERS.md](VILLAGERS.md#who-a-text-may-name)); a helper's lines say nothing of who says them (no first person that
agrees with the speaker: the bridge checks "Prinesel sem", "sem utrujena", "sam"). A choice's reply is said by whoever
spoke last before the turn (`Projects.repliers`).

**As this learner meets it**: at their level in the village's language (the highest the step has up to theirs, else its
easiest), read in their pair; its turns adapt as a scene's dialog's do ([Adaptive turns](#adaptive-turns), [Rules not
yet](#rules-not-yet-what-the-learner-is-asked-grows-with-the-grammar)), its answers count on the grammar book's rules, and
its turns test the project's words ([Your words in the dialogs](#your-words-in-the-dialogs)): each step's turns test 1–3
words of the project's word pack, a word chosen against another in its place ("Potrebujemo sekiro." / "Potrebujemo
žlico.") or a thing tapped (the spruce, smreka), in a form the dictionary knows, so a right answer is a review of the
learner's card when it's due. The projects sheet shows a project's words on its card, today's step's first ("👉 današnji
korak jih sprašuje"), each to look up, and "📚 Nauči se besed · Learn the words" (the pack run of those not learned yet),
so a project is also where new words come from; the packs screen lists the projects' packs in a section of their own
("🏗️ Skupni projekti"), and today's step on the card says where it is played ("📍 🌲 V gozdu · In the forest").

**The files** (`lani.project-steps/v0`): a culture pack's `project-steps/<project>.json` (the project's id, as in its
`projects.json`), in the pack's language, every text in the four languages the pack serves and a why in each base:

| Field | What |
|---|---|
| `project`, `language`, `review` | the project, the pack's language, who wrote it and who checked it |
| `pack` | the word pack of the project's words (`packs/projekt-<project>.json`, its `project` the project's id) |
| `steps[]` | one per step of the project, in order; `null` (or a file shorter than the project): that step is the practice it always was |
| `steps[].scene` | the scene id it is played in (one of the village's language or the pack's own); none: over the village |
| `steps[].words` | 1–3 word ids of the pack its turns test, at every level |
| `steps[].levels` | `"A1"`, `"A2"` (at least): `{"lines": […]}`, 4–7 of a scene dialog's lines, 1–3 turns, said by the leader (at least once) and the helpers; a reaction and a why to every wrong choice; a tap turn and `sky`/`fx` cues only with a scene (its art's places and effects, never a thing that comes and goes: an animal, a well or a mill not built yet); no `act` (its people are who is here today) |
| `steps[].sky_stays`, `fx_stays` | as a scene dialog's |

The app bundles them with the pack (`app/build.gradle.kts`, `CulturePacks`) and reads them leniently (`Cultures.projectSteps`:
one in another language or for another project is left out, said why); the bridge checks them with the pack
(`bridge/src/project-steps.ts`, from `validateCulture`: the speakers, the scene and its tap targets, each word tested at
each level by the dictionary's forms, the grammar pages; `bun test/check-steps.ts primorska [project]` checks one), and
voice-build voices their lines in each speaker's voice. A pack without the directory, an older content file or a step
without a dialog plays the step as the practice it always was (`GameEngine.projectChallenge`). An older bridge never reads
`project-steps/` and serves a project's pack as a plain one (it strips `project`); an older app bundles the packs it was
built with: the content can go out before the code that plays it.

Primorska's 13 projects have every step (78) at A1 and A2. QA's hook "step:<project>/<n>" (a debug build) readies the dev
village for that step today and opens the projects sheet at it (`companion/bin/qa --steps project-step`).

### Houses and their rooms

A house is somebody's home, and it opens one room, its own (`game/scene/Homes.kt`). Every scene that opens from `house`
is a room of a house: Micka's kitchen (`kuhinja`, from the hut too), Stari Janez's living room (`v-hisi`: the hiša of an
old farmhouse, the kmečka peč with its zapeček where his dog Muri sleeps), France and Tine's workshop (`v-delavnici`),
Vinar Marko's wine cellar (`v-kleti`) and Vida and Nejc's attic bedroom (`v-podstresju`: Nejc's bed under the roof, his old
cradle at its foot). Its `household` says who lives there.

**Which house opens which room** (`Homes.deal`): the houses, in the order they were built (the id breaks a tie), get the
rooms whose household lives in the village, in the order the households moved in (the first of them in the village's
list of residents); a room without a household (anyone's) comes after them, and a room the hut already has (the kitchen)
last while a hut stands. A house left over opens one of those, picked by its id. So the same house opens the same room
every time, a new house gets the room of whoever moved in next, and a room whose people don't live here yet is in no
house: its happenings don't come up and it has no bubble. The hut keeps the kitchen. In the Primorska village with everyone moved
in the first house is Janez's, then France's, Marko's, Vida and Nejc's; a fifth gets the kitchen. Without the cast's
names (an older bridge sends no household) every room counts as lived in, in the scenes' order.

**A house tapped** (`Homes.scenesOf`) offers its own room on its card, the room's title as the way in ("🚪 Go in: 🍷 V
kleti · In the cellar ›"), and says under the house's name who lives there ("Level 2 · Vinar Marko"); a house whose room nobody
lives in yet offers none. The room is seen in that house, at its level ([What the village has built](#what-the-village-has-built)):
the cellar of a house at level 1 has one barrel, the one at level 3 the pršut on the wall. Its bubbles show over its own
house and its people stand by it (`TownPlace.At` carries the house's id, `TownMarkers.placeOf`).

**Older apps and bridges.** The rooms are new arts. An app says which arts it draws (`SceneArt.VERSION`, sent as `GET
/scenes?arts=2`, and on a visit to a linked town), and a bridge gives an app that asks for less, or doesn't say (version
1), the view it knows (`forApp` in `bridge/src/scenes.ts`): no scene of an art it can't draw, no object in a slot its art
doesn't have (the kitchen's hearth, bench, straw bed, shelf, curtains and holy corner). An older bridge doesn't know the
new arts and slots, `needs.level` or a scene of more than 24 objects, and skips a scene that uses them (the new rooms, and
the kitchen, the smithy and the school that grow with their level), so the content that uses them goes out after the
bridge that reads them. `household` an older bridge and app ignore.

### Sky cues: the weather in a dialog

A dialog can change the weather of its scene as it goes: Luka runs to bring in the hay, the sky clouds over,
the first drops fall with his reply, and at the end it rains. A line or a reply carries a `sky` cue with new
levels for what it names; what it leaves out keeps its level.

```json
{
  "id": "seno",
  "sky_stays": true,
  "lines": [
    { "who": "luka", "sl": "Jan! Hitro! Dež gre.", "en": "Jan! Quick! Rain is coming.", "sky": { "gloom": 0.45, "wind": 0.35 } },
    { "choices": [
      { "sl": "Prav! Kaj naj naredim?", "en": "OK! What should I do?", "ok": true,
        "reply": { "sl": "Hitro, prve kaplje!", "en": "Quick, the first drops!", "sky": { "rain": 0.2, "gloom": 0.7 } } },
      { "sl": "Prav! Kje naj naredim?", "en": "OK! Where should I do?", "why": "Kje asks where. You want to ask what: kaj." }
    ] },
    { "who": "luka", "sl": "Zdaj pa pod streho, dežuje.", "en": "Now under the roof, it is raining.", "sky": { "rain": 0.75, "gloom": 0.85 } }
  ]
}
```

| Field | Rules |
|---|---|
| `sky.rain`, `sky.snow`, `sky.fog` | 0 (none) to 1 (heavy) |
| `sky.wind` | 0 to 1: the rain slants, snow drifts, grass and wheat sway more |
| `sky.gloom` | 0 to 1: an overcast sky, a greyer and darker light (a room dims a little) |
| `sky.lightning` | `true`: a flash far off now and then, and its thunder a few seconds after it ([GAME.md](GAME.md#background-sounds)); `false` stops it |
| `sky_stays` (dialog) | whether the sky the dialog ends with stays; default `true` when a line or a reply has a cue. Only allowed with a cue. |

A cue applies when its line is said (a learner's turn: when it comes up) or when its reply follows the learner's
right choice (a reaction to a wrong one has none). Each level eases to its new value over about 4 seconds, on its own, so the rain begins slowly while
the fog of an earlier cue still thickens. A cue needs at least one field; the bridge rejects levels outside 0–1
and unknown names.

When the dialog ends (its reward is paid), the sky it reached stays over its scene and over the village for the
rest of the local day, and clears at midnight: it is kept in `GameState.sky` (`{on, scene, sky}`, the date it is
for), so it survives a restart that day. The latest finished dialog with cues sets it, and one that ends clear
("Poglej, sonce! Dež je nehal.") clears it. With `"sky_stays": false` (morning fog that lifts) nothing is kept. A
dialog closed before its end takes its weather with it: the scene eases back to the day's sky. The village shows
the kept rain, grey sky and lightning; they are not the storm event and change nothing in the game.

What is drawn: outdoors (`forest`, `field`, `square`, `hills`, `sea`, `alps`, `stream`, `pond`) rain streaks and splashes on the ground, snowflakes,
bands of fog lying along the horizon, grey clouds hiding the sun, a flash and a far bolt; on the hills the rain veils the
far hills in grey shafts first, in the mountains the peaks, and the fog lies in the valley over the lake; at the sea the mist lies on the water, a squall's rain hangs dark over the lagoon and the
drops ring the water instead of splashing; by the stream and the pond the fog lies thickest on the water, the rain rings
the whole pond, and the stream runs fuller and greyer, spilling over its banks; at the market the rain wets the cobbles
(darker, puddles in the lane catching the sky) and veils the roofs and hills; from the watchtower the fog fills the valley
below like a lake, rain veils the far hills and the mountain first, lightning strikes over the mountains, and the rain
and the snow fall beyond the tower, seen between its posts and over its railing: nothing falls under its roof, on the
platform, the posts, the railing or the people on it; indoors (the dioramas) the rain, snow and fog fall in the dark
around the plot and over its open ground, never in a room nor under a roof (the ground under the bee house's eaves stays
dry), and the room's light dims a little. The campfire is all out in the open. The tent is cut open at the front for us
to look in, and for the weather its roof goes on over the floor: nothing falls into it, and nothing drips off the cut
(the rolled flap along the open front). Its canvas darkens in the rain and water drips off its eave onto the grass.

### Effects: the place joins in

Besides the weather, each art has a few effects its painter shows when a dialog asks for them: Babica Micka
fires up the oven for the potica and smoke rises from the chimney, Luka lights the lantern in the tent, the
crows come back to the scarecrow once it smiles. A line or a reply carries an `fx` cue: effect names of the
scene's art (see the table of [arts](#arts)) with a level 0–1, or `true` / `false` for 1 / 0. Like sky cues,
each eases in over about 4 seconds and a name left out keeps its level; a level 0 turns the effect off (the
lantern put out), while an effect no cue has set shows the place as it is by itself (the lantern lit from
dusk). For `birds`, `crows`, `starlings` and `gulls` the level is how far they have flown: 1 is the flock gone over the
sky, the crows landed; for the `tractor` how far it has come down the road (1: past us and gone), for the `sail` how far
it is up.

```json
{ "who": "babica", "sl": "O, Jan! Pridi, pridi. Pečem potico.", "en": "Oh, Jan! Come in. I am baking potica.", "fx": { "oven": 1 } }
```

Some effects sound as they come on ([GAME.md](GAME.md#background-sounds)): a `bell` rings a peal, the `cowbells` clank,
the `oven`, the `stove`, the `forge` and the campfire's `flare` catch with a whoosh, and the smithy's `sparks` bring the
smith's blows on the anvil.

The effects go with the dialog when it closes, unless it says `"fx_stays": true`: then the ones it ended with
stay in its scene until midnight (`GameState.sceneFx`, by scene), like the lantern Luka lit. A reaction's effect
passes sooner, with the learner's next pick ([Reactions](#reactions-and-more-than-one-right-answer)), and never stays.
The bridge rejects an effect the scene's art does not have, levels outside 0–1 and an empty cue.

The person the learner talks to reacts too: they wave when the dialog opens, hop at a right choice, tilt their
head at a wrong one (a hand to the chin, puzzled, at one they answer with a reaction), cup an ear while the learner
speaks into the 🎤, and cheer at the end without a mistake (`ui/scene/DialogMood.kt`). Their own lines win: while they
speak they are drawn talking.

### Stage directions: people move on cue

In an art with a stage, a dialog can move its people: Jan asks Luka into the tent out of the rain and Luka comes in and sits
down by the lantern; Zala says "Ti mižiš, jaz se skrijem!" and when Jan has counted to ten she is gone, hidden behind the
door with a braid showing past it. A line or a reply carries an `act` cue: for each person it names, where they go (`to`: a
spot of the art's stage) and how (`pose`). `to` alone: they stand there; `pose` alone: they take it where they are.

```json
{ "sl": "Seveda, pridi v šotor! Sedi k svetilki.", "en": "Sure, come into the tent! Sit by the lantern.", "ok": true,
  "reply": { "sl": "Hvala, Jan! Tukaj je suho.", "en": "Thanks, Jan! It's dry in here.", "act": { "luka": { "to": "lantern", "pose": "sit" } } } }
```

| Field | Rules |
|---|---|
| `act.<person>` | a person of the scene (their `id`); a cue names at least one |
| `act.<person>.to` | a spot of the art's stage (the table below); every person spot of an art with a stage is one of its spots |
| `act.<person>.pose` | `stand`, `sit`, `crouch` (squatting), `hide` (only a clue shows), `peek` (half out of the hiding place), `lie`: one the spot has |
| `act_stays` (dialog) | `true`: where it leaves people stays until midnight; by default they go back to their spots when it closes. Only with an `act` cue on a line or a reply to a right choice |

The timing is the sky's and the effects': a line's cue plays when the line is said (a learner's turn: when it comes up), a
right choice's reply's when the reply follows, and a reaction's passes with the learner's next pick (Zala peeks out of her
hiding place at a wrong guess, "Tu me ni! Hi hi!", and is gone again at the next one). A cue is followed from where the person
is (their spot in the scene, or where an earlier cue or today put them): `DialogRun.act` and `Dialog.actAt` in
`game/scene/Stage.kt`, pure.

**How they get there** (`DioramaPainter.cast`, drawn from the time since they set off, so the scene's canvas and its closer
look agree): a walk from spot to spot, 0.7–1.9 s by how far, with the sprites' walking look and turned the way they go; into
hiding, a puff of dust where they stood, and they are gone (nobody sees where to); out of hiding, a hop and a puff where they
hid, then the walk on; from hiding to peeking and back, at once. Every move is over within `Stage.MOVE_S` (2.6 s). The mood
of the person Jan talks to ([Effects](#effects-the-place-joins-in): the wave, the hop, the puzzled hand at the chin) is drawn
wherever they are; hidden, it doesn't show.

**Hidden** means hidden: the painter draws what shows of them at the hiding place (the stage's table below), and that
can't be tapped for their card; someone peeking out, sitting or standing can, as ever.

**When the dialog closes** (`GameController.keepScene`) its people go back to their spots, walking or popping out of hiding,
unless it says `"act_stays": true`: then where it left them is kept in `GameState.sceneAct` (by scene, like `sceneFx`) until
midnight, and they stay in the scene, their happening done or not, while they are in the village (`Stage.stayed`): Luka sits
by the lantern while the rain goes on. At night whoever sleeps in the scene goes to bed as always.

The bridge checks the cues (`stageErrors` in `bridge/src/scenes.ts`): the people are the scene's, each spot the art's
stage's and each pose the spot's, followed line by line through the dialog; an art without a stage has no cues, and nor has
a story by the fire, an introduction or a keeper's talk (no picture). An older app reads past `act` and `act_stays` (its
people stay where they stand), and a bridge from before stage directions strips them and serves the scene as before.

| Art | Spots (poses) | Hiding places: what hides them, what shows of someone there |
|---|---|---|
| `livingroom` | `stove`, `table`, `door` (stand: the person spots); `bench` (sit: on the zapeček along the stove); `under-table`, `behind-door` (stand, hide, peek) | `under-table`: crouched under the corner table, its top and cloth over them, their shoes and a bit of their clothes under it; peeking, crouched at its front corner, the head out under the cloth; standing, beside the table (the `table` hides it: a tap on the table is a tap there). `behind-door`: the door stands open, its leaf at right angles to the wall and a hand off the floor, and they stand pressed to the wall behind it, a braid or a sleeve past its sides and the shoes under it; peeking, half out past its edge; standing, in front of the open door (the `door`) |
| `tent` | `left`, `right`, `door` (stand: the person spots); `lantern` (sit, stand: on the bedroll behind the table, under the lantern and the roof) | none. Walking in from `door` (the meadow) to the `lantern`, they come through the open front; the weather keeps off them inside as off the tent's inside ([Sky cues](#sky-cues-the-weather-in-a-dialog)) |

`StageTest` and `DialogTapTest` check where everyone is after each step, what a reaction and a day keep, and the moves;
`StageRenderTest` that every spot and pose draws at every detail as the whole picture does, the hits in place, the hidden
untappable, a clue showing, a peek showing more, no rain on Luka by the lantern or on his way in, and renders the living
room's and the tent's for review (`build/scene-snapshots/stage`).

### Tap turns: answer by tapping the scene

A learner's turn can be answered by tapping the picture: "Kje sem?", and Jan taps the door, which is the choice "Aha, za
vrati si!". Each choice of the turn stands for its place, `tap`: a spot of the art's stage (`behind-door`), an object slot of
the art (`stove`) or a person of the scene (`janez`). A tap on it picks that choice, as a tap on the choice would
(`DialogRun.tap`): said as the learner's line, the person's reply, or for a wrong place their reaction ("Tu me ni! Hi hi!",
often with a peek as a hint) and its why one tap away. What counts as a tap on a place (`Stage.tapped`): the person there,
or the one peeking out there; the thing that hides a spot (the door for `behind-door`, the table for `under-table`); the
spot's area, where one would stand. A tap on a place no choice stands for changes nothing.

```json
{ "choices": [
  { "sl": "Aha, za vrati si!", "en": "Aha, you're behind the door!", "ok": true, "tap": "behind-door", "grammar": "orodnik",
    "reply": { "sl": "Joj, našel si me! Kako si vedel?", "en": "Oh no, you found me! How did you know?", "act": { "zala": { "to": "door" } } } },
  { "sl": "Aha, pod mizo si!", "en": "Aha, you're under the table!", "tap": "under-table", "why": "Look for a clue: something of hers shows somewhere.",
    "reply": { "sl": "Tu me ni! Hi hi!", "en": "I'm not here! Hee hee!", "act": { "zala": { "pose": "peek" } } } },
  { "sl": "Aha, za pečjo si!", "en": "Aha, you're behind the stove!", "tap": "stove", "why": "Look for a braid or a shoe that shows.",
    "reply": { "sl": "Mrzlo, mrzlo! Hi hi!", "en": "Cold, cold! Hee hee!" } }
] }
```

**On the screen** the turn says "👆 Poišči v prizoru · Find it in the scene" over the picture, and its choices wait behind
"✋ Izberi raje · Let me choose" (`TurnMode.TAP`, `DialogRun.tapModes`: only where the scene's picture shows, not in a keeper's
talk over the village nor on a visit, where it is chosen as ever); the 🎤 still takes a choice said. TalkBack always gets the
choices, which it can read. A tap turn is graded as a pick: a wrong place is a mistake once, with its reaction and its why; it
is chosen, never typed into a gap nor said whole ([Adaptive turns](#adaptive-turns)), and gets no own trap. It is tapped
as ever when its page isn't introduced to the learner yet: a right tap then only meets the rule ([Rules not
yet](#rules-not-yet-what-the-learner-is-asked-grows-with-the-grammar)).

The language of a tap turn is where someone is (kje?): za vrati, pod mizo, za pečjo, za zaveso, the grammar book's
`orodnik` (pod, za … + the instrumental, A2); v omari, na peči, `kje-mestnik-orodnik` (v, na, pri + the locative). Name
it on the right choice: a right first tap counts on the page, and unlocks it. A wrong place is no slip on the rule (its
words are right, the place is wrong), so the wrong choices name none. Every choice has a `tap`, or none has; each its
own place; a tap turn may have 4 choices (a turn otherwise 2–3). An older app shows a tap turn as its choices, and a
bridge from before tap turns strips `tap` and skips a scene with a turn of 4 choices: the curated ones keep 3.

**Hide-and-seek** (`v-hisi/skrivalnice`, Zala, morning and evening): "Jan! Se greva skrivalnice? Ti mižiš, jaz se
skrijem!"; Jan counts ("… osem, devet, deset! Grem iskat!", the verb's person the point: greš, gremo) and she is gone behind
the door, or (the other variant) under the table; Jan finds her with a tap among the door, the table and the stove, she peeks
out at a wrong guess and pops out when found, Muri waking; then what gave her away (tvoja kita, pridevniki-ujemanje), or the
roles swap and Jan says where she hides next ("Za vrata!", kam with the accusative), and she does. **Luka in the rain**
(`v-sotoru/dez`): asked in, he comes in and sits by the lantern, and goes out to his sheep when the sun comes; in the new
variant (`dez-svetilka`) it thunders and pours, he comes in ("Seveda, pridi v šotor! Sedi k svetilki.", v šotor against v
šotoru), lights the lantern and stays by it while it rains, until midnight.

### Pokes: small gotchas

Some things in a scene react when Jan taps them, besides what the tap does anyway (a word object shows its word): the
kitchen's cat gets up and stretches with a yawn; tapped again it purrs, eyes shut, hearts rising; a third time it dashes
off round the back of the house and comes trotting back twenty seconds later. These are pokes, pure decoration: they
change nothing in the game, and TalkBack reads the scene as before.

A painter declares its pokes (`ScenePainter.pokes`, `game/scene/Pokes.kt`): each is for one of the art's object slots
(the pot) or for something that is no word, under a name of the painter's own (the cat, drawn with `pokeable(name) { }`
in `ArtPainter`, whose pixels then answer `pokeAt`), with a sequence of reactions and how long each lasts. Taps in a row
go through the sequence: a tap within 4 s of the last reaction's end goes on to the next one, a later one starts over, a
tap in a reaction's first half second is let go, and after the last one the thing rests a few seconds (no spam). The
scene view keeps these (`Pokes`) and hands the painter what plays now in `SceneFrame.pokes`: the step and when it began,
on the frame's clock. The painter draws each from the time since then, in the picture's pixels, so the scene's canvas
and the closer look agree (see `SceneDetailTest`); `game/render/scene/PokeArt.kt` has the shared bits (sparks, hearts,
a puff of dust or steam, the rings of a sound, a bird on the wing). A poke leaves every thing and person where it is to
tap; a word object hidden for a while (the squirrel in its hollow) keeps its place with `thingRect`.

| Art | What reacts to a tap, one tap after another |
|---|---|
| `kitchen` | the cat (on the bench outside): stretches and yawns, purrs with hearts, dashes behind the house and back; `pot`: the lid rattles and lets out steam, then hops as it boils over |
| `tent`, `kitchen`, `livingroom`, `attic`, `workshop`, `cellar`, `smithy`, `school` at night | whoever sleeps in a bed: turns over, the Zzz growing; then mumbles "Pusti me spati …" (see [Asleep at night](#asleep-at-night)) |
| `campfire` | `fire`: leaps up in a burst of sparks, then higher, a shower of them |
| `field` | `scarecrow`: its crow flaps up off the arm and drops back, then the crows fly off, wheel over the field and settle again. While they are out: `hare`: hops off and back; `boar`: the piglets scatter and come running back to her |
| `forest` | `squirrel`: scurries up the trunk, peeks back down and comes down; then ducks into its hollow and peeks out. `owl`: opens its eyes wide by day (blinks at night), then spreads its wings, then turns its head and hoots, the calls going out. While they are out: `hare`: three hops off and three back; `hedgehog`: rolls into a ball and unrolls; the bear's eyes (no word): a blink, and they are gone into the trees, the undergrowth stirring |
| `smithy` | `anvil`: rings, a glint and sparks off its face; then twice |
| `tent` | `lantern`: swings on its chain; then wider, the flame guttering |
| `square` | the pigeons: scatter up and away, back a few seconds later; `tower`: the bell swings and rings; `dog`: wags hard with its tongue out, then jumps up and barks |
| `school` | `globe`: whirls round and slows (faster the second time); `sponge`: hops in a puff of chalk dust, then twice |
| `alps` | `cow`: lifts its head and lows, the sound going out; then shakes its head so its bell clangs, its tail swishing. The marmot (on its rock across the stream): stands up tall and whistles; then dives into its burrow, peeks out and comes back up |
| `church` | `candles`: the flames flicker and lean in a draught; then one goes out in a curl of smoke and catches again. `rope`: sways and the bell in the gable rings; then twice. The sparrow (on the window's sill): hops and chirps; flutters up and settles; flies a round of the nave and comes back |
| `apiary` | `bee`: buzzes round the flowers in a figure of eight, a dotted trail behind it; then a wide round of the garden. `panel`: a hive front comes up close over the bee house, its painting big (the devil grinding an old woman's tongue, the hunter and the bear, the rooster), one after another |
| `market` | `scale`: the beam tips and swings back to level; then the other way, a weight hopping in its pan. `apples`: an apple rolls off the crate over the cobbles and comes to rest; then two. `hen`: flaps in its wicker coop, a feather drifting off; then squawks and flaps hard, feathers flying |
| `watchtower` | `horn`: swings on its strap and sounds, the call going out; then longer and louder. `crow`: caws with its bill wide; then flies off in a loop over the village and lands on the railing again |

A new art can add its own the same way; `ScenePokesTest` checks that each shows at every detail, can be tapped, and
leaves the hits alone, and renders them for review (`build/scene-snapshots/pokes`). Someone asleep is poked too (see below).

Where a poke shows a sound, it makes one, timed as the painter draws it ([GAME.md](GAME.md#background-sounds)): the square's
bell and the church's rope ring at each end of the bell's swing, the anvil rings, the cow's bell clanks as she shakes her
head, the marmot whistles, the owl hoots, the pot's lid rattles, the cat purrs and the campfire whooshes up. The scene view
hands each reaction to `Ambience.poke` with its start on the picture's clock; `game/ambient/Sfx.kt` says what it sounds
like. A new poke with a sound gets its line there.

### Asleep at night

Each villager sleeps in their own home's scene from their bedtime until they get up (`game/scene/Sleep.kt`; the hours are
their day's, VILLAGERS.md "A day in the village": the children from eight, Stari Janez last). A painter declares its beds
(`ScenePainter.beds`, a child's among them in `childBeds`): the tent's camp bed (`bed`), the kitchen's bench by the stove
(`zapecek`, "za pečjo": the bench runs from the stove's warm side along under the window, as the one round a krušna peč), the
houses' rooms' and the workplaces' with a bed, the smithy's and the school's (below). Whose bed is in which scene
(`Sleep.bedrooms`): a household's in its room of the house dealt it ([Houses and their rooms](#houses-and-their-rooms): Janez
in the living room, France and Tine in the workshop, Marko in the cellar, Vida and Nejc in the attic); who lodges in a room by
their file's `sleeps` (VILLAGERS.md, "Where they sleep": Lovec Jože `house:livingroom` with Janez, Čebelar Anton
`house:cellar` with Marko) in that room's house; the others' in the scene with beds that opens from the first building of
their home with a roof (or the roof their `sleeps` names) and is nobody's household: Pastir Luka (`["spot:meadow", "tent"]`:
the meadow by day) in the tent's sleeping bag, Babica Micka, Teta Ančka and Zala (the house or hut) in the kitchen, Kovač Tone
in the smithy, Učiteljica Mojca in the school. Who lies there now: whoever of them lives in the village (not today's visitor)
and has been met, and is in bed by their day (`Routine.now`: not on a night ritual, not on the way home, not up for a
happening). One to a bed, each their own: the household first, then who lodges with them, each in their arrival order, a
child in a child's bed first, a grown-up never in one; a bed waits for whoever is still up tonight (`Routine.toBed`: Jože
never takes Janez's place while Janez tells his story), and one whose sleeper is up, or out for the night after their
bedtime, is free (Ančka has the bench on a night Micka is out, and once Micka is up). In a kitchen at level 1, before the
stove, Micka lies in the straw bed along the left wall under a coarse brown blanket. Someone with a happening on anywhere is up and there instead: Luka listening for the owl in
the forest, Micka bringing Jan a blanket in the tent, Nejc awake in the burja. Someone of the scene whose file doesn't list
them (Zala in the kitchen) sleeps as themselves, their sprite and their name. By day nobody is in bed; whoever is at the
scene's place by their day stands there ([Bubbles in the village](#bubbles-in-the-village)).

A sleeper is a person of the frame at the bed's "slot" in `Pose.SLEEP` (`PeoplePainter.lying`): on their back under the
cover (the tent's green sleeping bag zipped up to the chin; a red wool blanket with an ochre check on the bench), the sheet
turned down under the chin, the head on the pillow with the eyes shut and a little "o" of a mouth, a hand over the cover, the
chest rising with a slow breath; the grandmother keeps her kerchief on, hats come off. The Zzz rise over them, glowing
(`PeoplePainter.zzz`, from the frame's clock, in the effects pass). The place goes quiet with them: the tent's lantern is
turned low (unless a dialog lit it), the kitchen's lamp is out and the stove's embers glow further. Away from a bed (a scene
the tutor writes) someone asleep lies on a pillow where they'd stand; a portrait dozes standing.

**The houses' beds** (`beds`, a child's among them in `childBeds`). The kitchen: `zapecek` (Micka on the bench, at level
1 the straw bed), `mattress` (Ančka on a slamnjača rolled out before the door, her head at the door's end, under the coarse
brown blanket) and `cot` (Zala on a small one in the front corner by the left wall, at level 1 in the middle of the floor,
under a red wool blanket). The living room: `zapecek` (Stari Janez along the zapeček's front run, his head on a pillow in
the warm corner, under a loden-green blanket with a cream check, Muri curled at his feet at the wall end) and `sidebench`
(Lovec Jože on its other run along the stove's side, his head at the wall end on the embroidered cushion, a pillow at level
1, his feet toward the warm corner, under a brown horse blanket with a green stripe). The attic: `mattress` (Vida on a
slamnjača under the slope past the cradle, a blue blanket with a white check) and `cot` (Nejc in his own bed, under his
patchwork quilt). The workshop: `mattress` (France on the floor before the bench, a grey horse blanket with a red stripe) and
`cot` (Tine on his apprentice's cot by the door, a frame on legs with a headboard). The cellar: `bench` (Vinar Marko on a
plank bench before the barrels, his head on a folded sack, a wine-red blanket) and `trestle` (Čebelar Anton on planks over
two trestles between the press and the front, his head toward the steps, a straw mattress, a blanket the colour of
chestnut honey with a brown check). A mattress, cot or bench that isn't furniture by day is drawn only while someone lies on
it, so the room by day is as it was. With anyone asleep the room goes quiet: its lamp out (the attic's as a dialog set it),
the living room's stove glowing a little, the workshop's lantern and the cellar's candle turned low (the candle as a dialog
set it).

**The workplaces' beds.** The smithy: `cot` (Kovač Tone on a cot of planks along the left wall under the window, its head
against the forge's warm bricks, as smiths slept by the forge, a straw mattress, a charcoal-grey horse blanket with a rust
stripe). He banked the fire before he lay down (his ritual at 21:45): while he sleeps the bellows rest, the coals glow low
under their ash (no flames, no sparks, a thin thread of smoke) and the forge's light is small; a dialog that fires the forge
up has it roar as ever. The school: `alcove` (Učiteljica Mojca in an oak bed along the left wall under the map, its headboard
by the bookcase, a footboard at her feet, a blue and white checked curtain on a rod along it drawn back and tied at its head:
the old village school had the teacher's room in the school house, and the alcove stands in for it; a rose wool blanket with a
cream check); the lamps are out. Both are drawn only while their sleeper lies in them. `HomeBedsRenderTest` renders the rooms
and the two workplaces at every level (`build/scene-snapshots/sleep/home-*`).

A tap on a sleeper starts no dialog and no greeting: they turn over (the back of the head, the shoulder's hump, the Zzz
bigger and faster); tapped again within a few seconds they half wake, one eye open under a frown, a hand out shooing, and
mumble "Pusti me spati …" in the village's language ("Lasciami dormire …" in the Italian village), in their voice, its meaning
under it in a bubble; then they rest a while (`Sleep.poke`: 2.4 s, 3.6 s, rest 8 s). "Danes tukaj · Here today" lists them:
"💤 Pastir Luka spi". `SleepTest` checks whose bed is where and who is in it from hour to hour (the Primorska village with everyone
moved in: the little ones from eight, the grown-ups one by one, Janez after his story, Tone after the forge, Jože and Anton in
their own beds and nobody changing beds in a night, the morning), where `sleeps` falls back to, a happening or a visit
keeping a bed empty, the pokes and the quiet; `SleepRenderTest` the pose, the pokes at every detail, the lights, the window of
the picture and the frame time, and renders the tent and the kitchen at night (`build/scene-snapshots/sleep`).

### Wild animals

The outdoor scenes have their wild animals (`game/scene/Wildlife.kt`, drawn by `game/render/scene/Wild.kt`), who come
and go with the time of day: by day the hare and butterflies, the chamois and an ibex in the mountains; at dawn and dusk
(the hours round the scenes' sunrise and sunset) the roe deer at the field's edge and at the pond, the stag at the forest's
far edge, the fox, the hedgehog; at night the wild boar and her striped piglets digging, the badger, the dormouse (polh)
on the beech's branch, the bats; and very rarely the bear's eyes shining under the trees where the path goes in (no word,
just two eyes that blink and go). The hedgehog, the dormouse, the bats and the bear sleep through the winter, the
butterflies are there from spring to autumn.

| Art | Day | Dawn and dusk | Night |
|---|---|---|---|
| `forest` | hare, butterfly | stag, fox, hare; hedgehog and bats at dusk | boar, badger, dormouse, hedgehog, bats, the bear's eyes |
| `field` | hare, butterfly | a roe deer at the wheat's edge, the fox by the kozolec, hare; bats at dusk | the boar in the potatoes, bats |
| `pond` | butterfly | a roe deer drinking at the far shore; the fox at dawn; bats at dusk | bats over the water |
| `stream` | butterfly | the fox beyond the mill; the hedgehog and bats at dusk | hedgehog, bats |
| `alps` | chamois, ibex | chamois; the ibex at dawn | |

Which are out is the day's dice for each part of the day (`Wildlife.out`: the village's seed, the date and the part; the
night after midnight is still the evening's), so they stay all through it, differ from one day to the next and from one
village to another, the rare ones rarely; where each is (`Wildlife.spot`) changes too. An animal that is a word of its
scene (an object slot, in the scene's objects with its word from the map's animals' pack `zivali-v-gozdu`, or `na-prezi`, the
hunter's (the stag, the dormouse, the butterfly, the high seat), or its own pack's: the chamois
and the ibex are `v-gorah`'s) is there to tap and to find only while it is out; a dialog's cue (`herd`, `fox`, `bats`)
brings it as scenery the day hasn't. The painters take the village and the date from `SceneFrame.village` and `day`; a
test or a tool names the animals out with `SceneFrame.wild` (all of them: `Wildlife.all`). `WildlifeRenderTest` renders
the forest at dawn, by day, at dusk and at night (the day's mix, four days side by side, and every animal at once, with the
hunter on his high seat) and the other outdoor scenes with all their animals (`build/scene-snapshots/wild`).

Round the outdoor scenes at those hours besides: dew glinting on the field at dawn, the evening bell (the ave) ringing from
the village a few minutes round sunset over the field and the stream, fireflies in the warm months' nights, the owl
hooting now and then in the dark.

### The words panel: here now, seen before, still hidden

Below the picture the scene's words are chips (`ui/scene/WordGroups.kt`, `SceneWordGroups`; on a visit the same, with
what the visit found), in three groups by what the picture shows at this moment (`game/scene/SceneSights.kt`):

- **👀 Zdaj tukaj · Here now (N)**: what the picture shows now, found or not. A found word shows its emoji and its word
  (✓ once learned); one not found yet is a "❔", to find by tapping the thing in the picture (its chip still finds it too,
  as before: TalkBack's way to the things).
- **🗂️ Že videno · Seen before**: words Jan found before that aren't in the picture now (the stars by day, the bats in
  winter, the kitchen's clock in a hut not yet upgraded). The chip opens the word as a found chip does, its bubble in the
  middle of the picture, since the thing isn't there to point at, with a note of when it shows ("🌙 ponoči · at night");
  the chip carries the note's tags.
- **🔒 Še skrito · Still hidden**: not found and not there now. The chip shows the thing's emoji as a silhouette (what to
  look for, not what it is called; "❔" without an emoji) and the tags of when or how it comes. It isn't a button: the word
  stays unrevealed until it is found in the picture. TalkBack reads "Še skrito · Still hidden: …" with the hint in words.

Under the groups a legend says each tag once ("🌙 ponoči · at night · 🌕 ko je luna na nebu · when the moon is up"). With
nothing seen before or hidden, the chips are one group, as they were. The top bar's "🔍 found/total" and "🔍 Odkrito
N/M" count the things the scene's world has (`SceneSpec.inWorld`), as before; the groups also show what the village
hasn't built yet.

| Hint | Tag | For |
|---|---|---|
| ponoči · at night | 🌙 | the campfire's stars (after dusk); the wild animals of the night |
| podnevi · by day | ☀️ | the square's sun; the animals of the day |
| ob zori · at dawn, ob mraku · at dusk | 🌅 🌇 | the animals of the twilight (`Wildlife.Part`) |
| ko je luna na nebu · when the moon is up | 🌕 | the campfire's moon: the real one, up and not new |
| razen pozimi · except in winter; spomladi, poleti, jeseni, pozimi | 🌿; 🌸 🌻 🍂 ❄️ | the animals asleep in winter (an animal's months) |
| včasih · sometimes | 🎲 | the day's dice: a wild animal isn't out every day |
| spet jutri · again tomorrow | 🔄 | what a dialog's effect sent away for the day: the market's pigeons flown off, the bats a cue has none of |
| zgradi: Kozolec · build: Hayrack | 🏗️🪜 | a thing whose building doesn't stand yet ([What the village has built](#what-the-village-has-built)); at a level: "Kovačnica, stopnja 2" |
| nadgradi na stopnjo 3 · upgrade to level 3 | ⬆️3 | a room's thing of a higher level (the kitchen's clock) |
| dokončaj: Mlin na potoku · finish: The mill on the stream | 🏗️ and the project's emoji | a project's fixture (France's mill by the stream) |
| doseži: Vas · reach: Village | ⏳ and the age's emoji | an object's `needs` age |
| le pred nadgradnjo · only before the upgrade | 🏚️ | a thing that went with the upgrade (the open hearth, the straw bed): only ever seen before, never still hidden |

Where the "when" comes from: one pure function per thing, `SceneSights.visibleNow(art, object, frame)` (there in the
scene's world, `SceneFixtures`, and shown by the frame) and `SceneSights.whenShown(art, object, world, fx)` (the hints). The
painters draw by the same conditions: the campfire's stars (`starsOut`) and moon (`moonUp`), the square's sun (`sunUp`),
the market's pigeons flown off (`flownAway`), the bats a cue brings (`Wildlife.bats`), the wild animals out
(`Wildlife.out`), what is built (`SceneFixtures`). So the chips never disagree with the picture: `SceneSightsRenderTest`
renders every art over whole days in the four seasons, at the moon's phases, under the real sky, in every weather, with the
day's wild animals of a few villages, every effect low and high and each world (nothing built, a room at each level,
everything), and checks every thing's chip against the painter's hits. Nothing else comes and goes as a word: the rain,
the snow, the blossom, the swallows, the lantern lit at dusk and who is there change how the picture looks, not which of
its words are in it, so there is no hint for them. A painter that makes a word come and go by something new says so in
`SceneSights`, or the test fails.

The panel looks again every minute, at the picture's inputs as the scene view draws them (`SceneSights.frame`: the clock,
the moon and the real sky, the village's seed for its animals, the effects a finished dialog left for the day).

### I spy: «Vidim, vidim nekaj, česar ti ne vidiš»

A child of the village plays I spy with the learner in the scene's picture: they pick a thing in it and give clues one at a
time, in the scene's language with the translation as the dialogs show it (the 👁 hides it, a tap on a line shows its
own), voiced like a dialog's lines; the learner taps the thing in the picture. So the scene's words are asked for in play,
not only found and learned in a pack run (`game/scene/ISpy.kt`: the content, who plays, which things and clues, the day;
`ui/scene/ISpyRun.kt`: a game, pure; `ui/scene/ISpyPanel.kt`: its panel and the offer; `app/ISpyController.kt`).

**Who plays, and when.** The child of the scene who is in the picture now (Zala in the living room, Nejc in the attic,
"Otroci" by the fire: Nejc), else a child of the village who comes by (living here and met, awake by their day:
`Routine`, not at a happening of their own elsewhere; the day's dice picks one, the same all day in the scene) and stands
at a person spot of the scene nobody stands at, the door where there is one (Zala comes into the kitchen). No child about
(the children are in bed from eight, none lives here yet): no game. The words panel offers it above "Danes tukaj · Here
today": "🔍 Vidim, vidim · I spy", the child asking «Se igrava «Vidim, vidim»?» with its meaning under it, the games left
today and "🔍 Igrajva · Let's play"; a 🔍 over the child in the picture does the same (unless a happening of theirs has its
own marker). Two games a day in each scene (`ISpy.GAMES_PER_DAY`), then "✓ Vidim, vidim: jutri spet · again tomorrow".
A game has three rounds (`ROUNDS`), fewer where fewer things can be spied.

**A round** (`ISpyRun`): "Vidim, vidim nekaj, česar ti ne vidiš …" (the next rounds: "Še enkrat! …") and the first clue;
"👆 Poišči v prizoru · Find it in the scene". A tap on a thing in the picture is the learner's guess, their line ("Miza?";
a tap is a find, as anywhere in the scene). The right thing: "Ja, to je nož! Bravo!", the thing outlined in the picture
and its word one tap away ("✓ 🔪 nož · knife" opens its bubble over the thing, as a find does), then "Naprej · Next". A wrong one: the guess marked wrong, the
child's kind reaction, warm where the tap was near the thing (their areas within `WARM_PX` of each other: "Toplo, toplo!"),
else cold ("Ne, to ni to.", "Mrzlo, mrzlo!"), and the next clue; the same wrong thing again gets a reaction and no new
clue. "💡 Še en namig · Another clue" gives the next one without a tap, "🙈 Pokaži mi · Show me" gives up; a wrong tap after
the last clue has the child show it: "To je nož! Glej, tukaj je." (a plural word: "To so vrata! Glej, tukaj so."). The
end: "Hvala, lepo je bilo! Jutri spet?" ("Vse si {m:našel|f:našla}! …" when every round was found), what it brought, and
"Nazaj v prizor · Back to the scene". "✋ Izberi raje · Let me choose" lists the things in the picture as chips, a chip a tap
on its thing; TalkBack always has them, hears each clue as it comes (the newest line is a polite live region) and reads the
lines as the dialogs' are, with their translations. A tap on a person does nothing while a round is on; back closes the game.

**Which things** (`ISpy.rounds`): only what the picture shows now (`SceneSights`: not the stars by day, nor a wild animal
that isn't out, nor what the village hasn't built), with two clues to give; not one spied in the scene today already (unless
that leaves too few). Picked by weight, the learner's words first: their card due or nearly due (8, [GAME.md](GAME.md#words-met-in-play)),
their card still being learned (5), a thing found before (3), a word known well (2), a thing never found (1).

**Which clues** (`ISpy.clues`): the thing's clues in the scene's clue file, by kind, in this order: `colour`, `size`,
`trait`, `material`, `compare`, `where`, `does`, `use` (what it looks like first, what it is for last), one of each, at
most four (`MAX_CLUES`). A clue is said only when the grammar book's pages it names are introduced to the learner (none of
them `Mastery.NOT_YET`, as the dialogs' turns: [Rules not yet](#rules-not-yet-what-the-learner-is-asked-grows-with-the-grammar))
and the thing it names (its `ref`) is in the picture now (the pot "na ognjišču" in a hut at level 1, "na štedilniku" from
level 2). Of a kind, the last in the file that may be said: the files list the simple ones first, so the clues grow with the
book (a scene of another language than the book's gates none):

| A clue | For example | Pages | Level |
|---|---|---|---|
| an adjective that agrees: colour, size, a trait, the material | Je rdeča. Je lesen. So rjava. | `pridevniki-ujemanje` | A1 |
| where, with the locative after v, na, pri, ob, po | Leži na mizi. Stoji ob steni. | `mestnik` | A1 |
| what it is for, the thing as ga, jo, jih | Pijemo ga za zajtrk. | `tozilnik` | A1 |
| in it, on it: v, na with the pronoun | Na njem sedimo. V njej je juha. | `mestnik` | A1 |
| what it does | Ponoči sveti. Lepo diši. | `glagoli-sedanjik` | A1 |
| a no with the genitive | Nima barve. | `rodilnik-nikalnica` | A1 |
| where, with the instrumental after pod, nad, za, pred, med | Stoji pod oknom. Raste za ograjo. | `orodnik` | A2 |
| what it is used with: z/s with the pronoun | Z njim režemo kruh. | `orodnik` | A2 |
| what it is made of (iz), where with zraven, sredi, blizu | Je iz lesa. Visi zraven kredence. | `rodilnik-predlogi` | A2 |
| a comparison | Je večji od vedra. | `primernik`, `rodilnik-predlogi` | A2 |

A thing with fewer than two clues to give (a scene the tutor wrote has no clue file) gets clues that need no picture: its
first letter ("Začne se s črko K.") and, a word of one piece, how many letters it has ("Ima štiri črke.", the forms that
agree with the number as a [counting dialog's](#numbers-counting-what-the-village-has); voiced when played).

**What it brings.** At the end each round pays as an answer (🌾, [GAME.md](GAME.md#resources)): a quick find (within
`QUICK`, two clues) as a right one, a later find as an almost, a thing shown still something; the first game of the day
with a child grows their friendship (+3 ♥, `ISpyController.FRIENDSHIP`, and a memory: "Še vedno mislim na najino igro
«Vidim, vidim»", 🔍 on their page) and is time spent with them, 🤝 1 ([GAME.md](GAME.md#helping-people)). A quick find of a
word the learner has a card of is a review of the card ([GAME.md](GAME.md#words-met-in-play)): once a card a day, due or
nearly, quality 4, sent as a review (POST /reviews) when the game ends or is closed; the end card says how many words
were reviewed. A game closed before its end pays nothing, and a round played spends a game of the day. The day is kept in
`GameState.ispy` (the games of each scene, the things spied, the children played with), which syncs as the rest of the
village does; an older bridge keeps it as it is.

**The content** (`companion/ispy`, machine-written: `machine_written`, `review`): the child's lines in each language,
`<language>.json` (`lani.ispy-lines/v0`: `offer`, `start`, `again`, `cold` and `warm` reactions, `found` and `show` for a
singular and a plural word with `{word}`, `end`, `end_all`, `letter` with `{letter}`, `letters` with `{n}` and its forms,
`memory`; each text in the language and its translations), and each scene's clues, `scenes/<scene>.json`
(`lani.ispy/v0`). The app bundles them (the Gradle task `ispyFiles`, read in the learner's pair as a scene is), and the
bridge serves none of them: the scenes' format, which an older bridge checks strictly, doesn't change. voice-build reads
them for the voices (below). The 26 scenes' 397 things have 1797 clues: the 20 Slovene scenes' 311 things 1408 (the
kitchen's 27 things 115, the forest's 24 things 99), the Friuli ones' 26 things 124 in Italian, Kärnten's 30 things 124
in German, Lakeland's 30 things 141 in English; all machine-written, for a native speaker to read (the colours a child
might name otherwise, a few "where"s the picture shows only roughly, the sounds' verbs: Rega, Gaga, Žubori).

```json
{ "schema": "lani.ispy/v0", "scene": "kuhinja", "language": "sl", "machine_written": true, "review": "…",
  "things": {
    "knife": { "clues": [
      { "kind": "trait", "sl": "Je oster.", "en": "It's sharp.", "grammar": ["pridevniki-ujemanje"], "adj": "oster" },
      { "kind": "where", "sl": "Leži na mizi.", "en": "It's lying on the table.", "grammar": ["mestnik"], "prep": "na", "case": "loc", "ref": "table", "form": "mizi" },
      { "kind": "use", "sl": "Rabimo ga za kruh.", "en": "We need it for the bread.", "grammar": ["tozilnik"], "pronoun": "acc" },
      { "kind": "use", "sl": "Z njim režemo kruh.", "en": "We cut bread with it.", "grammar": ["orodnik"], "pronoun": "ins" } ] },
    "door": { "number": "pl", "clues": [ { "kind": "colour", "sl": "So rjava.", "en": "It's brown.", "grammar": ["pridevniki-ujemanje"], "adj": "rjav" }, "…" ] } } }
```

| Field | Rules |
|---|---|
| `things` | every object of the scene, by its slot, in the scene's order |
| `number` | `"pl"` for a plural word (vrata, vilice, grablje, orgle, klešče, stopnice, drva, očala, škornji): everything that agrees is plural |
| `name` | what the word means as the found line says it, where the pack's meaning isn't one plain word (`ura`: `{"en": "clock"}`) |
| `clues[]` | 3–6: `kind`; the sentence in the scene's language, its translation in English (a scene of another language: Slovene and English too); `grammar`, the pages it needs (two at least all A1); and what the check reads: `adj` (the adjective's dictionary form; a comparative's own), `prep`, `case`, `form` and `ref` (a thing of the scene) or `noun` (another noun: stena, tla, nebo; `noun_number`), `pronoun` (the case of the pronoun that stands for the thing), `verb` |

Rules for writing them: true to the picture (the colour drawn, where the painter draws it at each level of a room); never
the thing's own word; never where a person, the sun, the moon or a wild animal is (they move), a wild animal described,
never placed; short, natural, a child's sentences; together the clues single the thing out among what the picture shows.

**The checks.** `ISpyContentTest`: every scene has its clues, every thing three at least and two a beginner gets; every
clue a sentence with its translations, its pages the book's; the Slovene against the dictionary (companion/lexicon/sl.json):
the adjective's ending by the noun's gender and number, the copula's and the verb's number, the case a preposition takes
and the noun's form in it, the pronoun for the thing, s/z and k/h by the next sound, never the answer's own word (a form
the dictionary lacks takes the regular paradigm, listed in the test's output, or a hand-checked table); "where" against
the picture, each art drawn as the scene view draws it at every level, wherever the two are drawn together ("na" on its
top, "v" inside, "pod" under, "nad" above, "pri", "ob", "zraven" right by it, "za" behind, "pred" in front); Italian
adjectives and German pronouns by the noun's gender. `ISpyTest`: the clues by the grammar, the things picked, who plays,
the day, a game's turns, the reviews, the content as the app reads it.

**The voices.** voice-build voices the child's lines, each scene's clues and each thing's find and reveal with its word
("Ja, to je nož! Bravo!"), after the scenes' dialogs (its "ispy" row), in the voice of each child who may play in the
scene (its own children's, else every child of the cast: one comes by); the picture-free clues are voiced when played. The
bridge's own prebuild reads them too once it is restarted with this code.

**Other villages.** The same game in the scenes of the other culture packs, in their language: "Vedo, vedo una cosa che
tu non vedi …" by the Friuli fire and the sea (Tommaso; warm and cold are "Fuochino!" and "Acqua, acqua!"), "Ich sehe was,
was du nicht siehst …" in Kärnten (Maxi), "I spy with my little eye something …" in Lakeland (Harry), with the Slovene
and English meanings; their gates are their books' pages (an Italian "dove" with `dove-preposizioni`, a German "auf dem
Tisch" with `wechselpraepositionen`, A2).

**QA:** `companion/bin/qa --steps ispy`: by the fire, QA's hook ("ispy:ob-ognju/stump") offers the game whatever the day
and spies the stump first; a wrong thing tapped in the picture, the stump, the rounds to the end card (the debug build logs
where each round's things are on the screen, tag `ISpy`).

### Stickers: a word's picture from its scene

Some words have no emoji that shows them (the scythe, the anvil, the heron): their pack gives the pack's topic emoji or
something near it (🟫 for the table). Where a scene draws the thing, the word's cards show it instead, cut out of the
scene: a sticker. Which word is drawn where comes from the scenes' objects (`WordStickers.of`: every object puts a pack
word in a slot of its art; a word several scenes draw is taken from the scene of its own pack, else the first), so a
new scene object is a new sticker. A card takes the sticker when the word has no emoji or its emoji only stands in for
it (`WordStickers.STAND_INS`, the words the audit of the packs' pictures found); an emoji that shows the word (🪓, 🍞,
🔥 for the fire) stays. So a word on that list gets its sticker as soon as a scene draws and names it.

The painter draws the one slot alone (`ArtPainter.solo`: everything runs, only that thing's pixels land) on a summer
noon, without people, weather, effects or pokes, at detail 3, twice, on black and on white, so what it blends comes out
see-through; the cut is trimmed to the thing and its outline (`Stickers.cut`, a few ms). The app cuts each once off the
main thread and keeps it in memory and as a PNG in its cache (a folder per app version), and draws it scaled by whole
pixels, crisp. It shows on the pack's word card and word list, the word card of a tapped word (the dictionary's pack
entries), a scene's word bubble and a gift that is such a thing (the horseshoe, the scythe). `StickersTest` renders
them all for review (`build/scene-snapshots/stickers/00-stickers.png`, the cards' own framed in gold; `01-cards.png`
at card size).

### Bubbles in the village

`TownMarkers.of` lists what the village shows: the event (wolves and the bear in the forest, the
merchant at the market, the festival under the linden tree), who joined the village and waits to be met at their home
(🧳, 👶 a baby: VILLAGERS.md, "Arrivals"), each open quest at its giver's place
(Babica Micka at a house or hut, Pastir Luka in the forest, Kovač Tone at the smithy, … else by the
fire), and each happening at its scene's place.

Everyone is on the map at most once, where their day has them now (VILLAGERS.md, "A day in the village"): at their work, a
rest, the well, the children at play, walking from one place to the next (round the fire and the buildings, never through
them), away on an errand of their work now and then; by the fire in the dark evening or at home, their windows lit; in bed
at night, their windows dark; out with a lantern only on a night ritual. Someone with a happening on now stands at its
place (`TownMarkers.standing`, `TownPeople.of`), waving now and then: France by the stream, not also walking about as
today's visitor; at a place with work, at it between the waves (France harvesting in his field); their bubble follows them
there. So does, by daylight, the giver of a request at its place. Whoever is at a scene's place by their day is in the
scene too, at their spot (`Whereabouts.inScene`: France hoeing the field is in Na njivi, a tap on him a greeting); not while
still on the way (`Routine.WALK_S`, as long as the map's walk takes at most) or away on an errand.
Someone whose happening is far from the clearing (`TownPeople.away`: the mountains or the sea at the horizon, a landmark
out in the woods) isn't on the map at all: a small bubble with their face sits at that far place (on the mountains at the
horizon, at the vineyard's terraces), and a tap on it opens their scene at their happening. A plain villager waits at a
happening's place only for someone of a scene who isn't among the village's people (`TownPeople.waiting`).

On a phone held upright the village has woods below it (`game/render/Foreground.kt`, drawn by
`Woods.kt`): a path winds south out of the clearing past a woodcutter's stack, a forest pond with reeds
and ducks, and a small meadow with a fallen log, flowers, mushrooms and deer, watched from a hunter's
high seat (an owl sits on it at night); deeper in, a charcoal pile smoulders. The forest's bubbles point
at the meadow, someone from the forest waits where the path enters it, wolves come up the path (to the
palisade's gate, once the village has one), and a tap anywhere in the woods opens the forest, but on the things there
that are places of their own: the woodcutter's stack (the woodpile's card), the pond, the meadow's berry bushes, fallen
log, flowers and mushrooms (the meadow's card, "Naberi hrano · Gather food"), the high seat and its owl (the hunter's high seat: what
it is, and who is there now), and the charcoal pile (below). The meadow's deer are their word, as the wild animals are
(a tap right on one). The start view frames the clearing and the meadow. The palisade stands round the clearing, with a
gate on the path; a tap on any stretch of it opens it, and its bubbles point at its gate on the road. The road's bridge
over the stream is the stream's, as the water under it is (the stream bank's card).

**The charcoal pile** (`spot:kopa`, oglarska kopa) is there where the canvas goes down that far (`Foreground.kopa`: a
phone held upright; on a shorter canvas it has no target, and its bubbles are where the woods' are). A tap on it, its
glowing vents or the screen reader's target opens its card: its name and its line from the culture pack (world.json
`spots.kopa`: "Oglarska kopa · The charcoal pile", "Kopa tli, iz lesa nastaja oglje"; else the app's own), a few
sentences of its history at the learner's level (`story`: the highest level it has up to theirs) in the village's
language with the meaning below (every word to look up, 🔊 to hear it), and the words of its pack as chips, each to look
up, with "📚 Nauči se besed · Learn the words" (a pack run of the ones not learned yet). Some evenings its charcoal burner
sits by it (the spot's `keeper`, VILLAGERS.md "At the charcoal pile"), the reason it glows: a small figure left of the
pile, facing it, with a bubble while his talk isn't done today; a tap on him, his bubble or the card's "Zdaj tukaj ·
Here now" plays his talk over the village (`KeeperScene`, as an introduction is: his portrait on the woods at the hour it
is, the dialog as a scene's), and it pays like a happening's, once a day.

Two scenes open from spots of the landscape. The way to the stream is a footpath of stepping stones from the village
down to a little landing at the water on the village's bank, inside the palisade, which keeps to the stream's far bank
(a ring that runs by the stream on the village's side instead opens in a river gate, a postern, for it: the campfire's
clearing, before the palisade can go up); a tap on the landing or its stones, on the stream where it runs
through the clearing or on its bank above the bridge opens the stream bank's card with "Vstopi · Go in" to Ob potoku,
and a tap on the pond in the woods the pond's, to Pri ribniku. The landing is a thing drawn like the woodpile, found by
the nearest pixel like everything else; the water and the bank are areas, tried after everything drawn there
(buildings, people, the fire, the palisade), so they never take a tap from them. The scroll lists these spots after
the village's own places.

Once the tent has moved out of the village (GAME.md, "The tent moves to the pond"), it stands on the pond's right bank
as a small campsite (`game/render/Campsite.kt`): the tent under a big spruce, a plank jetty into the pond, a fire ring
with a small fire in front of its door, a stump, the firewood behind it. It is still the tent: a tap on any of it opens
the tent's card with "Vstopi · Go in" to V šotoru, the tent's bubbles point at it, and who waits at the tent waits by its
fire. A tap on the water beside it is still the pond's.

## The learner in the content

The content is written to whoever learns, not to one learner: a text never writes out the learner's name or their
gender. It says them with placeholders, and the app and the bridge say each text to the learner of the profile.

**The name** is `{learner}`; a case of it `{learner:gen}`, `{learner:dat}`, `{learner:acc}`, `{learner:loc}`,
`{learner:ins}` (the nominative is `{learner}`, the vocative the same), and `{learner:poss}` the stem of the possessive,
its ending written after it: `{learner:poss}a hiša` is "Markova hiša", "Anina hiša", `{learner:poss}ega psa` "Markovega
psa". "Marko je {learner:dat} napisal pismo." "Brez {learner:gen} ni nič." "Pri {learner:loc} je, z {learner:ins}."

**What agrees with the learner** is a gender pair, the man's form after `m:`, the woman's after `f:` (either order):
`Si {m:lačen|f:lačna}?`, `{m:Rad|f:Rada} bi kavo.`, `Lahko noč, {m:fant|f:dekle}.`, `Sei {m:stanco|f:stanca}?`,
`{m:mein Junge|f:mein Mädchen}`, `Good night, {m:lad|f:lass}.` A branch may hold `{learner}` (`{f:Draga {learner}|m:Dragi
{learner}}!`) and an ICU select (a newcomer's template: `{m:spoznala|f:{g, select, f {spoznali} other {spoznala}}}`, the
two of you are women only when both are), nothing else in braces and no `|`. Pair the smallest natural unit, mostly one
word; a few words when the change spans them (`{m:moj fant|f:moje dekle}`: *dekle* is neuter, so what agrees with the
noun is neuter, while what is said of the learner is feminine, `ti si {m:priden|f:pridna}`).

What to pair, in every language of a text (the translations too):

- **Said to the learner**: the Slovene second person with an l-participle or an adjective (`Si {m:videl|f:videla} Belo?`,
  `Boš {m:šel|f:šla} z mano?`, `Bodi {m:priden|f:pridna}!`, `Imaš {m:rad|f:rada} …?`, `Si {m:sam|f:sama}?`); the words of
  address (fant → dekle, dragi → draga, pozdravljen → pozdravljena, dobrodošel → dobrodošla, sosed → soseda, gospod →
  gospa; ragazzo → ragazza, bravo → brava, benvenuto → benvenuta, Junge → Mädchen, mein Lieber → meine Liebe, lad → lass,
  boy → girl, he/him about the learner → she/her); Italian adjectives and participles with essere (`sei {m:arrivato|f:arrivata}`;
  avere doesn't agree).
- **Said by the learner**: their choices, a module's answers (`say`, `accept`, a reorder's tokens and solutions), a pack's
  phrase and examples (`{m:Rad|f:Rada} bi`, `Sem {m:utrujen|f:utrujena}.`), the grammar book's first-person examples.
- **The learner and someone else**: a dual or plural is feminine only when all of them are women (`sva {m:jedla|f:jedli}`
  with Micka; with Luka it stays `sva jedla`).
- **Memories and the third person**: "ko si mi {m:pomagal|f:pomagala}", "{learner} je {m:prišel|f:prišla}".
- **A why or a reply that quotes a paired form** pairs it too: "{m:Žejen|f:Žejna} means thirsty."

What stays as it is: what agrees with anyone else (the villagers, the storyteller's heroes and narration, a speaker's
own "Utrujena sem."), polite *vi* (its plural masculine is everyone's: "Ste lačni?"), a wrong choice whose point is
someone else's gender, and the forms that don't agree (hai mangiato, du bist müde, I am tired, imperatives). A turn never
tests the learner's own gender: if a wrong choice differs from the right one in the very word that is paired (right
"Utrujen sem.", wrong "Utrujeni sem."), it gets its own woman's form (`{m:Utrujeni|f:Utrujene} sem.`), and its why too.
Where a joke only works for a man, its woman's branch says another one.

**Who the learner is**: the profile's `learner.name`, `learner.gender` (`"male"` or `"female"`; missing, anything else
or a template's `{male|female}`: male) and, for a name the rules below miss, `learner.name_forms` (`{"gen": "…", "dat":
"…", "acc": "…", "loc": "…", "ins": "…", "poss": "…"}`, any of them: those win). Without a name (the profile's template,
`{YOUR_NAME}`), `{learner}` is the village's word for a friend, `prijatelj` / `prijateljica` (amico / amica, Freund /
Freundin, friend), with a capital where a sentence starts, and its cases decline (`prijatelja`, `prijateljici` …).

**How a first name declines** (`sloveneName` in `bridge/src/addressee.ts`, `Learner.slovene` in `l10n/Learner.kt`; the
shared cases are `bridge/test/fixtures/learner-render.json`):

| The name | gen, dat, acc, loc, ins; poss | |
|---|---|---|
| ends in -a (Ana, Mojca; a man's too: Luka) | Ane, Ani, Ano, Ani, Ano; Anin (a woman's: -in, Mojčin) / Lukov (a man's: -ov) | -c, -k, -g soften before -in (Mojca → Mojčin) |
| a woman's otherwise (Nives, Beti) | Nives in every case; Nivesin, Betin | |
| a man's in -o (Marko) | Marka, Marku, Marka, Marku, Markom; Markov | |
| a man's in -e (Tone) | Toneta, Tonetu, Toneta, Tonetu, Tonetom; Tonetov | |
| a man's in -i, -u, -y (Toni) | Tonija, Toniju, Tonija, Toniju, Tonijem; Tonijev | |
| a man's in a consonant (Jan, Igor, Nejc, Pavel, Peter) | Jana, Janu, Jana, Janu, Janom; Janov | the fleeting e of -el, -ek, -er (Pavel → Pavla, Tonček → Tončka, Peter → Petra, Aleksander → Aleksandra; not after two vowels: Gabriel → Gabriela); another -r takes -j (Igor → Igorja, Valter → Valterja); after c, č, š, ž, j: -em, -ev (Nejcem, Nejčev) |

A man's accusative is his genitive, the locative the dative. `Family.genitive` (the family's "from" form, `od Maje`)
declines the same way.

**Where it is said**: the app says every text it shows or plays to the learner of its profile (`Learner.current`, set
from the dashboard's `learner.name`, `gender` and `name_forms`): the content it bundles (culture packs, festival packs,
the grammar book, drills), and what the bridge serves, read where it is parsed (scenes, villagers, stories, arrivals,
readings, modules, packs, role-plays, the grammar book, drills, a visit's town). The bridge says what it serves the same
way (`Route.addressed`: its answers rendered for `ctx.addressee()`, the profile in the data directory), and voices it so
(`corpus({ …, learner })`: the clips are the texts as said to this learner); a talk with a villager, the words a learner
adds from a pack or a lookup and the words of a pack session go into the data as said. What the tutor reads
(`get_scene`, `list_villagers` …) keeps the placeholders, and the tutor writes them: `publish_*` refuses a malformed one
(`{learner:foo}`, a pair with one form or one label twice, other braces inside) and a turn whose choices read the same
to a man or to a woman. A text with the learner's name written out still works: it says that name to everyone.

**The app's own labels** are ICU messages (`resources/l10n/*.json`), whose `{learner}` is an argument (another
learner's name): they agree with the learner through `learnerGender`, which every message may read
(`{learnerGender, select, f {Pripravljena} other {Pripravljen}} si!`, `L10n.LEARNER_GENDER`).

**Jan reads it as before**: said to a man named Jan, every text of the curated content (`companion/scenes`, `cultures`,
`packs`, `modules`, `scenarios`, `grammar`, `drills`) is the text it was before the placeholders came in (854f737), so
his voice clips stay. `bridge/test/learner-content.ts` checks it file by file against
`bridge/test/fixtures/learner-neutral.json` (`--write` makes it again after a content change); the smoke's learner
section runs it, and `LearnerContentTest` the same with the app's renderer. The smoke also checks that no text names a
learner Jan any more (Jan Vitovec, the captain, is another Jan, and the listener says his name back), that every
placeholder is well formed and that no turn reads the same for one gender; both say sample lines to a woman (`content`
in `learner-render.json`). The women's forms are machine-written, not reviewed by a
native speaker.

## Stories: the evening story

Every evening the village's storyteller sits by the fire and tells a story: Stari Janez at the campfire (`ob-ognju`),
Nonno Bepi at the Italian village's (`al-fuoco`), Old Wilf at the English village's (`by-the-fire`). Not the same one every night: the next one the learner hasn't heard,
told at their level, a long legend in chapters, one an evening. The scroll and "Jutri · Tomorrow" say what comes:
"Nocoj ob ognju: Janez pove zgodbo o Zlatorogu · Tonight by the fire: Janez tells the story of Zlatorog" until it is heard,
then "Jutri: Janez pove, kako je Krpan srečal cesarja · Tomorrow: Janez tells how Krpan met the emperor".

**Where they are.** The curated stories are the culture pack's, one file each in `companion/cultures/<id>/stories/`
(`lani.story/v0`, in the pack's language, told by its storyteller: the cast's villager whose role is the Storyteller). The
tutor writes more with `publish_story` (`<data>/app/stories/`; a tutor story with a curated id replaces it until
`remove_story`). A happening with `"stories": true` makes its person the teller (its person needs a `villager`; it has
no `dialog`): the bridge serves that scene with its teller's stories in the scene's language (`stories`, in the
rotation's order: the curated ones by their `order`, then the tutor's, oldest first), and the app picks tonight's.
The evening opens from the village's bubble, and in the scene as a dialog does: the marker over the teller, a tap on
him, and "📖 Poslušaj · Listen" by him under "Danes tukaj · Here today" (a dialog's "💬 Pogovori se · Talk"), while
there is a story to tell tonight.

```json
{
  "schema": "lani.story/v0",
  "id": "martin-krpan",
  "language": "sl",
  "title": { "sl": "Martin Krpan", "en": "Martin Krpan" },
  "teller": "janez",
  "emoji": "🧂",
  "order": 3,
  "teaser": { "sl": "Janez pove zgodbo o Martinu Krpanu", "en": "Janez tells the story of Martin Krpan" },
  "memory": { "sl": "zgodbo o Martinu Krpanu, ki sem ti jo pripovedoval štiri večere", "en": "the story of Martin Krpan I told you over four evenings" },
  "pack": "zgodbe",
  "words": ["velikan", "cesar", "sol", "kobila", { "word": "lipa", "pack": "v-vasi" }],
  "chapters": [
    {
      "title": { "sl": "Krpan in cesar", "en": "Krpan and the emperor" },
      "teaser": { "sl": "Janez pove, kako je Krpan srečal cesarja", "en": "Janez tells how Krpan met the emperor" },
      "levels": {
        "A1": { "sky_stays": false, "lines": [
          { "sl": "Dober večer, Jan. Nocoj začnem dolgo zgodbo. Traja štiri večere!", "en": "Good evening, Jan. Tonight I start a long story. It lasts four evenings!" },
          { "choices": [ { "sl": "Štiri večere! Komaj čakam.", "en": "Four evenings! I can hardly wait.", "ok": true, "reply": { "sl": "Dobro.", "en": "Good." } },
                         { "sl": "Pet večerov! Komaj čakam.", "en": "Five evenings! I can hardly wait.", "why": "He said four: štiri. Pet is five." } ] },
          { "sl": "Pot je ozka. Krpan dvigne kobilo s tovorom in jo postavi v sneg.", "en": "…", "sky": { "snow": 0.35 }, "fx": { "flare": 1 } }
        ] },
        "A2": { "lines": [ "…" ] }
      }
    }
  ],
  "pictures": [
    { "chapter": 1, "after": 2, "bg": "mountains", "figures": [ { "id": "peasant", "x": 0.3, "size": 1.2 }, { "id": "horse", "x": 0.62 } ],
      "props": [ { "id": "sack", "x": 0.62, "y": 0.2 } ],
      "caption": { "sl": "Krpan dvigne kobilo v sneg.", "en": "Krpan lifts his mare into the snow." } }
  ]
}
```

| Field | Rules |
|---|---|
| `id`, `language`, `title`, `emoji` | as a scene's: kebab-case; the village's language (every text in it and English, an Italian one in Slovene too); "Zlatorog · Goldhorn" or per language |
| `teller` | a villager id of the cast whose `role` is the Storyteller (`janez`, `bepi`, `wilf`) |
| `order` | its place in the rotation (every curated story has its own); `about`: where the legend comes from, for writers and the tutor |
| `teaser` | what the teller tells, a whole sentence for "Jutri: …" and "Nocoj ob ognju: …" ("Janez pove zgodbo o Zlatorogu") |
| `memory` | what the teller remembers afterwards, fitting their remember lines ("zgodbo o Zlatorogu, ki sem ti jo povedal"); the happening's memory until then |
| `pack`, `words` | the words it teaches: ids in `pack`, or `{"word", "pack"}` from another; the bridge serves them resolved (a word no pack has is left out), the story's end shows them with "📚 Nauči se jih · Learn them" (a pack run of those not learned yet) |
| `levels` | told in one evening: a telling per level (`A1` … `C2`), each 3–16 lines as a scene dialog's, with `sky_stays` and `fx_stays` |
| `chapters` | instead: 2–8 evenings in order, each with its `teaser`, an optional `title` and the same levels |
| `continues` | the story it goes on from (its id): a new chapter or a sequel of one the learner liked ("Martin Krpan se vrne"); it is told after that one, and its notebook entry joins that one's. A curated one goes on from a story of its pack with a lower `order`; the tutor's from one of the village's, told by the same teller, never in a circle |
| `pictures` | its pictures, sketched in the notebook ([The story notebook](#the-story-notebook)): each in evening `chapter` (from 1; 1 for a story of one evening), `after` the telling's nth paragraph (0: at the evening's head), a `bg`, `figures` and `props` from the vignette library, `night`, and a `caption` in the story's language and English (and the title's other languages). A curated story has 2–4, in chapters one or two an evening |
| `notes` | what the learner would have written down of each telling, the notebook's text ([The story notebook](#the-story-notebook)): evening `chapter` (from 1) told at `level`, its `text` (2–10 paragraphs, each in the story's language and English, and the others its lines have) and `after` (the paragraph each of the evening's pictures follows, in their order; 0: at the head). The story as prose in the past tense (at A1 too), no asides to the listener, no greetings or questions, none of the learner's turns but every beat the turns and replies carry, short clear sentences at the telling's level. One per telling; the curated stories have them all (machine-written, `review`). Without them the notebook writes the telling down itself |
| `review` | for writers: who wrote what and whether a native speaker checked it ("notes: machine-written (Claude) in sl, en, de and it; not reviewed by a native speaker") |

A telling is a scene dialog told by the teller: his lines have no `who` (the happening's person says them), the first is
his, and it has at least one turn for the learner: a question he asks ("Povej, fant: kaj je naredil lovec?") that the
choices answer, or a reaction ("O, ne! Ubogi Zlatorog!"); every wrong choice has its `why` and the teller's reaction as
its `reply`, as in a scene ([Reactions](#reactions-and-more-than-one-right-answer)): "Dobro jutro? Ob tej uri?" to a
morning greeting by the fire (the curated stories all have theirs, `DialogReactionsTest`). The cues are the campfire's:
`flare` at the dramatic moment, `steam`, `bell` (a distant bell: the wishing bell of Bled), and the sky's fog, gloom, wind
and rain for the mood, with `"sky_stays": false` so the story's weather passes with it. A curated story is told at A1
(present tense, very short sentences, the commonest words) and A2 (the past, more detail) at least, in every chapter;
some at B1 (subordinate clauses, the conditional, reported speech). The bridge checks all of it (`validateStory`), the
culture's stories with the pack (`validateCulture`: the file named for its id, the pack's language, the teller in its
cast, A1 and A2, each its own `order`), and `publish_story` also that the story is in the village's language.

**Tonight's story** (`Stories.tonight`, pure): the stories the teller tells in the scene, and what the learner has heard
of each (`GameState.stories`: story id → how many times it was `told` to its end, the `chapter` a telling under way has
come to, the `level` and the date of the last part, and `levels`: how many of its evenings were heard at each level, what
its book holds):

1. a legend under way goes on: its next chapter;
2. else the first, in the rotation's order, of those told the fewest times: every story once before any is retold, a
   new one (the tutor's) before the retellings, and of the new ones the continuations last. A continuation waits for the
   story it goes on from: it is ready once that one was told once more than it (so a sequel comes after the story, and
   after its retelling again);
3. at the learner's level in the scene's language (`Dashboard.levelIn`: the home language's level, a visit language's
   own): the highest level the story has up to it, else its easiest (A1 → A1, A2 → A2, higher → the highest there is),
   a level up for each time it was told before: once all are heard, they are retold a level up.

**Never out of stories.** When fewer than three stories the learner hasn't heard to their end are left at their level
(`storiesLeft`: those with a telling at it), the bridge asks the tutor for another, once a day at most
(`features/stories.ts`: after each village write, and at start; the day it asked is kept in `<data>/app/story-requests.json`,
so a restart doesn't ask again): a `stories_low` channel note with what was told and how (each story's levels, chapters,
pictures, what was heard), the learner's level, their weakest patterns in `mistakes-db`, the region (the culture pack's)
and the vignette library. The guide says what to write: a legend of the region the collection doesn't have, or a
continuation of one they liked (`continues`), at their level, a weak pattern or two in the learner's turns, with its
pictures. Asking at three, not at the last, is the buffer: a day the tutor misses never shows. With none left, the
teller retells the old ones a level up, never a silent evening.

The happening is on every evening while the teller is in the village and has a story to tell (`chance` 1, and it comes
before the other happenings of the evening), until half past eleven at night (`Routine.STORY_UNTIL`: then the teller goes
home to bed, VILLAGERS.md "A day in the village"), once a day like any happening: its reward, the friendship and the memory
("Še vedno mislim na zgodbo o Zlatorogu, ki sem ti jo povedal ob ognju") work as for a dialog, and the dialog's header
and the chronicle name the story ("Martin Krpan (2/4)"). Heard to its end, the story is recorded
(`GameController.storyHeard`); a story closed before its end is told again. Every telling heard to its end (also again)
counts as reading practice: its turns, those right at the first pick, and the minutes on screen (`POST /readings/done`,
see GAME.md, "The reading corner"). The end card says the level it was told at,
"➡️ Se nadaljuje jutri zvečer · To be continued tomorrow evening" while a legend has chapters to come, that it is written
down now ("📓 Zapisana je v tvojem zvezku zgodb · It's written down in your story notebook", "📓 Odpri zvezek · Open the
notebook" at its page), and its words.
`Stories.teaser` gives "Jutri" its line ("Nocoj ob ognju: …" before, "Jutri: …" after; "… še enkrat · … once more" for a
retelling), after a festival's countdown and tomorrow's project step. `voice-build` voices the stories of the village
(its culture pack's and the tutor's), every evening at every level: the A1 tellings before the scenes' dialogs, then
A2, then B1 and up:
the teller's lines and the replies to the learner's choices in the teller's own designed voice once it is ready
(Stari Janez: `@janez`, what the app asks for by the fire and the car tries first; ElevenLabs only), else in their
speaker (grandpa); the right choices in the default voice (`storyTexts` in `bridge/src/voice.ts`). The car plays a
telling only when its clips exist. A line without a clip yet is voiced when it is played.

| Culture | Teller | Stories (levels; order) |
|---|---|---|
| `primorska` | Stari Janez | Zlatorog (A1–B1), Kralj Matjaž (A1–B1), Martin Krpan (4 chapters, A1–A2), Peter Klepec, Lepa Vida (A1–B1), Povodni mož, Zvon želja (the wishing bell of Bled), Desetnica, Ajdovska deklica (A1–A2); then Ljubljanski zmaj (A1–B1), Mojca Pokrajculja, Bogatinovo zlato (A1–B1, going on from Zlatorog), Pegam in Lambergar (A1–B1), Kekec, Veronika Deseniška (2 chapters, A1–B1), Jurij Kozjak (3 chapters) (A1–A2 where not said, machine-written); their words in `packs/zgodbe.json` and `packs/pripovedke.json` |
| `friuli` | Nonno Bepi | Le agane, Il Ponte del Diavolo (Cividale), Il colle di Udine, Gli sbilfs, L'Orcolat; then La Madonna del Lussari (Svete Višarje), Le krivapete (Benečija), La Bora e Tergesteo (Trieste), La Dama Bianca di Duino, Lopichis e il lupo (Paul the Deacon) (A1–A2, in Italian with Slovene and English, machine-written); their words in `packs/storie.json` and `packs/leggende.json` |
| `kaernten` | Opa Sepp | Der Lindwurm (Klagenfurt), Als der Dobratsch brach (1348), Wie der Wörthersee entstand, König Matthias in der Petzen (Kralj Matjaž), Die Riesen in den Karawanken; then Die Maultasch vor Hochosterwitz (the bull full of rye), Das Kasermandl, Die Perchtl (Pehtra baba), Die tausend Statuen von Millstatt, Die heilige Hemma (A1–A2, in German with Slovene, English and Italian, machine-written); their words in `packs/geschichten.json` and `packs/sagen.json` |
| `lakeland` | Old Wilf | The Borrowdale cuckoo, The Bowder Stone, The Lady of the Lake (Bassenthwaite), The Giant's Grave (Penrith), The hob; then The Crier of Claife, Long Meg and her Daughters, The Luck of Edenhall, King Dunmail's Crown, Tom Fool of Muncaster (A1–A2, in English with Slovene, Italian and German, machine-written); their words in `packs/tales.json` and `packs/legends.json` |

### The story notebook

Every story the learner hears by the fire is written down in their story notebook, "📓 Moj zvezek zgodb · My story
notebook" (`game/scene/Notebook.kt`, pure; `ui/notebook/`): a handwritten exercise book of its own, not a reading. It
opens over whatever is on screen (`AppViewModel.openNotebook`, closed it is there again): from the chest (its own
section under the tools), the reading corner (one row on top; its readings are its own), the storyteller's card and
page (Stari Janez, Nonno Bepi, Opa Sepp, Old Wilf: `tellsStories`), and a story's end ("📓 Odpri zvezek · Open the
notebook", at that story's page). The row shows how many stories and sketches it holds.

- **What it holds** (`Notebook.entries`): a page a story heard (`StoryBooks.shelf`: its evenings heard, and the stories
  that go on from it, `continues`, as far as they were begun), in the order they were first heard (the rotation's the
  same evening). Each evening under the day it was heard, "28. septembra, ob ognju · 28 September, by the fire"
  (`StoryHeard.days`, the first time it was heard; a village from before the notebook kept only the last evening's day,
  `on`: the evenings of that telling are counted back an evening each, `Stories.daysHeard`). A legend's evenings are
  headed with their chapters' titles, a continuation's first with its own; an evening still to come is "🔒 Naslednji
  večer · Next evening".
- **The first page**: "Zapisano ob ognju · Written down by the fire" and "Kazalo · Contents": each story, a dotted line to
  the day it was first heard ("28. 9."), the evenings of one under way ("2/4"); a tap turns to it. Then a page a story,
  turned with a swipe or ‹ ›.
- **What is written** (`Notebook.evenings`, `Notebook.blocks`): the story's `notes` for that evening and level
  ([Format](#stories-the-evening-story)): the story as the learner would have written it down listening, prose in the
  past tense, without the teller's asides and the learner's turns but with every beat they carry, at the telling's
  level (A1 very short sentences, A2 more detail, B1 the telling's clauses). A story without notes (the tutor's, or one
  an older bridge served: it leaves them out) is written down from its telling (`Notebook.prose`): the teller's lines
  between the learner's turns (the replies aren't written), without the asides: a question, a greeting, the listener
  named or called ("fant", "piccolo", "lad"), told to sit, listen or say, tonight's story talked of; "Veš, …" taken off
  the front; what is said in quotes is the story's. A story heard at two levels offers both ("Slišano na ravni"), opening
  at the learner's level, else the highest; an evening heard only at another level is written at that one.
- **The page** (`NotebookSheet`, `NotebookLook`): a school exercise book's paper, faint blue rules a line of handwriting
  apart (34 sp: they grow with the font scale), a red margin, the story's emoji in it, the title underlined by hand;
  the text in blue ink in a handwriting (Kalam, bundled in `res/font`, its OFL licence in `assets/fonts/`), every
  baseline on a rule. Under each paragraph its translation in pencil, as the dialogs' 👁 has it (`DialogPrefs`, the
  same switch on top; hidden, a tap beside the words shows a paragraph's); 🔊 a paragraph in the teller's voice (voiced
  when asked, like a dialog's lines); a word tapped opens its card. A warm night version in the dark theme. TalkBack
  reads the plain text.
- **The sketches** (`game/render/book/Sketch.kt`): each picture is its vignette (`BookPictures`, below) gone over in
  pencil, four times finer (600 × 344): the pen's lines become graphite strokes, a straight one between each two of its
  pixels that touch (the steps don't show), a little wobble and uneven pressure, each drawn twice not quite in the same
  place; the hatching stays hatching; the washes are only a light smudge where they are dark; at most one watercolour
  (`Sketch.accentOf`): the gold of what shines (`gold`), else a fire's, else the water's blue below the horizon (a lake,
  the sea, a river, a bridge, by day), laid on a little beside the lines and darker at its edge. The drawing fades out
  raggedly before the card's edge. The card is pinned in a little askew (`NotebookLook.pin`: its tilt, two strips of tape
  or a paper clip, the same every time), with a soft shadow and its caption by hand under it (the translation in
  pencil); TalkBack reads the caption. Drawn off the main thread once, the last few kept.
- **"🎯 Preveri se · Test yourself"** at a story's end (`BookQuizSheet`; `StoryBooks.questions`): the telling's turns at
  the level read, each asked with the teller's line before it, one at a time, the text hidden (it was just read), "👁
  Pokaži besedilo · Show the text" bringing the notebook's text back over the question (GAME.md, "Read first, then
  answer"); a pick shows right or wrong, the why of a wrong one and the teller's reply. All answered, it counts as
  reading practice (`POST /readings/done` as a reading: `id` `book:<story>`, its questions, and `looks`) and the story
  is ✓ at that level (`GameState.read`: "primorska/book:zlatorog/A1"); it pays nothing (the story paid by the fire),
  so looking back costs nothing either.

**The pictures** (`game/render/book/`): drawn by hand in code, in ink: black-brown lines on warm paper, hatching for
shade, a few thin flat washes, a printer's rule round it, 160 × 96 pixels like the rest of the app; the notebook shows
them as pencil sketches (above). A picture is a background, figures and props where they stand (`x` across 0–1, `y`
up from the background's ground 0–1, `size` 0.5–2, `flip` to face left, `gold`: Zlatorog's horns on a white chamois,
fair hair, a shining sword or bell, the treasure) and a caption; the far ones are drawn first. The vignette library
(`Vignettes.kt`, mirrored by the bridge's `VIGNETTES`; the smoke test compares them):

| Kind | Ids |
|---|---|
| backgrounds | `mountains`, `lake`, `castle` (on a hill), `village`, `forest`, `church` (and its graves), `sea` (a coast), `cave`, `river`, `bridge` (its ground is the deck), `meadow`, `town` (its walls and gate) |
| figures | `king`, `girl`, `woman`, `boy` (a shepherd), `old-man`, `peasant`, `hunter`, `knight`, `soldier`, `giant`, `fairy` (a white lady), `water-man`, `devil`, `goblin` (a sbilf, a hob), `dragon`, `chamois`, `horse`, `ox`, `boar`, `dog`, `cat`, `bird`, `boat`, `sheep` |
| props | `crown`, `sword`, `bell`, `tree` (a linden), `spruce`, `cloud`, `sun`, `moon`, `stars`, `flower`, `rock`, `barrel`, `chest` (treasure), `sack`, `club`, `table`, `fire`, `house`, `chapel` (on its islet), `wall` |

The bridge checks them (`validateStory`: the ids, where they stand, the evening and the paragraph, the caption's
languages; a culture's check wants 2–4 for a curated story, one or two an evening in chapters), and `publish_story`
takes a tutor's story with its pictures. The four packs' 46 stories have 116 between them, their captions in the app's
four languages (machine-written, like the packs' other stories): Zlatorog white with his golden horns above the white
ladies, King Matjaž asleep at his stone table, an evening each of Martin Krpan (his mare lifted into the snow, Brdavs
in Vienna, the club from the empress's linden, the fight), the wishing bell sinking in the storm and ringing on its
island; the agane washing by moonlight and the cat first across the Devil's Bridge; the Lindwurm, the Dobratsch's fall
and the barrel that became the Wörthersee; the cuckoo over the wall, the Lady of the Lake's sword and the hob's new coat. An older bridge's check leaves `pictures`, `continues`, `notes` and `review` out
(the story is served without them), and an older app reads a story with them as before.

## Arts

Nominal canvas 240×160, landscape; wider or taller views get more floor, wall or sky. Each art draws
all of its object slots (a slot without content is scenery, not tappable) and returns hit areas. The living room and the
tent have a stage besides: spots a dialog can send its people to, to stand, sit or hide (see [Stage
directions](#stage-directions-people-move-on-cue), with their spots and poses).

| Art | Object slots | Person spots | Effects (`fx`) |
|---|---|---|---|
| `campfire` | fire, logs, kettle, stones, smoke, sparks, moon, stars, tent, axe, bucket, stump | left, right, back | `flare` (the fire burns higher, sparks fly), `steam` (the kettle steams), `bell` (a distant bell: its rings go out in the sky over the woods, opposite the moon) |
| `kitchen` | stove, pot, bowl, table, chair, bread, plate, glass, cup, spoon, fork, knife, window, door, cupboard, clock, milk, eggs, water, salt, potatoes, hearth, bench, bed, shelf, curtains, corner (by the level: see [What the village has built](#what-the-village-has-built)) | stove, table, door | `oven` (the stove fired up, smoke from the chimney; at level 1 the hearth's fire burns higher), `steam` (off the cup and the bowl) |
| `forest` | tree, spruce, mushroom, berries, stream, stone, path, bird, deer, squirrel, owl, leaves, sheep, highseat (the hunter's preža), flowers; the wild: hare, butterfly, fox, stag, hedgehog, boar, badger, dormouse, bat | path, clearing, highseat (sitting up in it) | `birds` (a flock flies up out of the trees and away), `herd` (a stag and two hinds step out of the trees at the far edge: how far out), `fox` (the fox trots along the edge to her place: how far she has come), `hoot` (the owl turns its head and hoots again and again), `bats` (bats over the clearing: how many) |
| `square` | linden, bench, ball, well, dog, cat, house, tower, bicycle, flowers, sun, fence | bench, well, play1, play2, play3 | `bell` (the tower's bell swings and rings) |
| `tent` | lantern, sleepingbag, blanket, pillow, backpack, map, boots, coat, hat, book, cup, rope | left, right, door | `lantern` (0 dark, 1 lit) |
| `field` | wheat, corn, potatoes, cabbage, kozolec, hay, scythe, rake, basket, cart, scarecrow, fence, path, horse; the wild: deer, hare, butterfly, fox, boar, bat | field, kozolec, path | `crows` (crows come to the scarecrow), `bell` (the church bell on the hill, its rings coming over the fields; not set: the ave, a few minutes round sunset), `bats` |
| `livingroom` | stove, bench, table, chest, window, door, dog, chair, curtains, rug, lamp, clock, corner, photo, glasses, book (by the level) | stove, table, door | `stove` (fired up: its tiles warm, a glow on the bench), `dog` (Muri awake, sitting up and wagging; at night he sleeps whatever) |
| `workshop` | workbench, hammer, nails, boards, axe, broom, window, door, saw, tools, ladder, rake, wheelbarrow, horse, lantern (by the level) | bench, floor, door | `dust` (sawdust flying off the vice as a board is sawn) |
| `cellar` | barrel, press, stairs, door, window, candle, jug, glass, crate, shelf, bottle, grapes, wine, ham, cheese (by the level) | barrel, press, stairs | `tap` (wine running from the barrel's tap into the jug, filling it), `candle` (0 out; lit, its flame flaring up the higher, as just lit; not set: lit, calm) |
| `attic` | bed, pillow, blanket, cradle, chest, roof, window, stairs, wardrobe, rug, lamp, ball, mirror, toy (by the level) | bed, chest, stairs | `lamp` (0 dark, 1 lit; not set: lit from dusk), `cradle` (the cradle rocks: how far) |
| `smithy` | forge, fire, anvil, hammer, tongs, bellows, horseshoe, nails, bucket, chain, axe, wheel, window, door | anvil, forge, door | `forge` (the forge roars), `sparks` (sparks off the anvil at every blow) |
| `school` | blackboard, chalk, sponge, desk, chair, book, notebook, pencil, bag, map, globe, clock, window, door | teacher, desk1, desk2, door | none yet |
| `hills` | vineyard, vine, grapes, hill, church, belltower, farmhouse, cypress, persimmon, wall, mountains, sea, road, crate | vines, wall, road | `bell` (the bell swings in the tower, its rings go out), `tractor` (a tractor with six crates comes down the track: how far it has come), `starlings` (a flock rises off the vines and wheels away) |
| `sea` | sea, lagoon, casone, boat, posts, gull, wave, beach, pier, shell, fish, net, horizon, crab | beach, pier, boat | `waves` (the sea rises, breakers burst white, spray), `gulls` (the gulls off the bricole, one by one), `sail` (the batela's ochre sail with its sun goes up) |
| `alps` | mountain, peak, lake, church, bridge, pasture, hut, cow, cowbell, larch, spruce, waterfall, stream, stone, wall, path; the wild: chamois, ibex | path, wall, stream | `cowbells` (the cows' bells swing and ring: how loud), `bell` (the church bell by the lake swings, its rings go out), `fish` (a fish jumps out of the lake again and again, rings on the water), `birds` (alpine choughs off the crags and away: how far they have flown), `glow` (the alpenglow: how pink the peaks; not set: at sunrise and sunset) |
| `stream` | stream, stones, bridge, willow, trout, dragonfly, cress, mill, laundry, sheep; the wild: fox, hedgehog, butterfly, bat | bank, ford, mill | `trout` (a trout leaps out of the current again and again, rings where it falls back), `wheel` (the mill wheel's water, once the mill grinds: 0 the flume dry and the wheel still, 1 a full gush; not set: an easy turn), `bats`, `bell` (the village's bell, its rings coming down to the water; not set: the ave) |
| `pond` | pond, lilies, reeds, frog, ducks, jetty, boat, fish, heron, dragonfly; the wild: deer, fox, butterfly, bat | shore, reeds, jetty | `ducks` (wild ducks fly up out of the reeds and away), `frog` (the frog jumps off its lily pad; 1: in the water, just its eyes out), `fish` (a fish jumps again and again, rings on the water), `bats` (bats hunting low over the water) |
| `church` | altar, candles, cross, painting, pews, flowers, window, organ, font, bell, rope, door | altar, aisle, door | `candles` (1: all four on the altar lit, one by one as it rises; 0: out; not set: the middle two burn), `bell` (the bell swings in the gable over the door and rings, the rope going up and down), `organ` (notes rise from the pipes) |
| `apiary` | beehouse, hive, panel, bee, honey, comb, flowers, tree, smoker, extractor, wax, bench | hives, table, bench | `swarm` (a cloud of bees rises off the hives and circles up by the linden: how many, how far), `smoke` (the smoker puffs, the smoke drifting to the hives; not set: a thin thread), `honey` (honey runs from the extractor's tap into the jar, the crank turning, the jar filling) |
| `market` | stall, scale, weights, eggs, bread, cheese, honey, apples, vegetables, basket, crate, hen, cart, wine, pigeon, bell | stall, buyer, cart | `bustle` (a busy market day: shoppers crowd the lane and the square, how busy), `bell` (the market bell on its post swings and rings), `pigeons` (the pigeons fly up off the cobbles and wheel away: how far they have flown) |
| `watchtower` | tower, railing, torch, horn, spyglass, flag, crow, roofs, palisade, gate, road, forest, river, valley, mountains, horizon | watch, rail, hatch | `torch` (the watch's torch: 0 out, 1 lit; not set: lit from dusk), `horn` (the horn blown, its call going out over the land: how loud), `birds` (a flock rises off the forest and wheels away: how far), `wolves` (eyes shining in the dark forest at night, grey wolves slinking over the fields by day: how many) |

People sprites: `grandma` (headscarf, apron), `grandpa` (hat, walking stick), `child1`, `child2`,
`child3`, `shepherd` (crook), `smith` (leather apron, hammer), `teacher`, `farmer` (straw hat),
`beekeeper` (veil), `innkeeper`, `winemaker`, `aunt`; the strangers of the day's surprise, `pedlar` (the krošnjar
from Ribnica under his krošnja of suha roba) and `pilgrim` (staff with a gourd, scallop shell, shoulder bag); the
village's own `woman`, `man` and `baby`; `hunter` (Lovec Jože: a loden-green jacket, a green hat with a jay's
feather, breeches and boots, binoculars on his chest; up on the high seat he holds them to his eyes); and `burner` (the
charcoal burner by his pile in the woods: a work jacket gone grey with soot over a dark waistcoat, a black felt hat, a
dark beard, heavy boots, the stick he pokes the vents with).

Time of day and season show: the campfire glows at night under stars and the moon, the kitchen lamp
is lit in the evening, the forest is green in summer and golden in autumn, snow lies in winter. On the hills the vines
are bare in winter (a few dry bunches left), in leaf from spring, heavy with ripe grapes and full crates at the trgatev,
gold and red in October; the kaki keeps its orange fruit on bare branches into November; the lavender flowers in summer.
In the mountains the snow comes down the peaks from October and lies on everything in winter, the lake freezing at its
edges and the larches bare; spring leaves snow in the gullies and patches on the pasture, with crocuses; summer brings
gentians and alpine roses, October gold larches; the peaks glow pink at sunrise and sunset, and at night the hut's window
and the church are lit and the moon lies on the lake. In a taller view the mountains (and the hills) rise higher into
the sky it has to spare. At the sea the marsh is green
in summer and straw the rest of the year, beach umbrellas stand along the bar in summer, and the sun's glitter (the
moon's by night) lies on the water. By the stream the mill's window glows at night once it grinds and the moon lies on the water; ice
creeps out from its banks in winter, and the pond freezes but for the hole where the ducks swim. In the church
(`church`: V cerkvi, from the church) the coloured windows lay their colours on the floor and the pews by day, moving
with the hour, and glow dark at night, when the red sanctuary lamp and the candles light the altar; the vases hold the
season's flowers; in December and January the nativity scene (jaslice) stands by the altar instead of the Virgin on her
pedestal, and from Holy Saturday to Easter Tuesday the baskets of the žegen stand before the altar. At the bee house
(`apiary`: Pri čebelnjaku, from the beehive) the bees fly out to the flowers and the water and back, many in summer, a
few in spring and autumn, none in winter, at night or in the rain; the linden is in fresh leaf in spring, flowers pale
yellow in June and July, turns gold in October and stands bare in winter; the meadow bed has dandelions and daisies in
spring, phacelia and clover in summer, asters in autumn; the lantern on the corner post is lit from dusk, and on World
Bee Day (20 May) bunting hangs along the eave. The feasts come from the frame's `date` (`SceneFrame.date`, the day the
scene view shows; none in the tests and on the training stage). At the market the crates
hold the season's vegetables (lettuce and radishes in spring, tomatoes and peppers in summer, cabbages and a pumpkin in
autumn, cabbages and turnips in winter) and the basket its fruit (cherries, plums, grapes, walnuts), the linden flowers in
June and turns gold, snow lies on the awnings and the lane is trodden down the middle, and after dusk the stalls'
lanterns and the windows are lit. From the watchtower the forest turns gold and red in autumn and stands bare and white in
winter, the valley's fields change with the months, snow comes down the mountain, the peaks glow at sunrise and sunset,
icicles hang from the tower's roof in winter, and at night the windows, the gate's torches and the watch's torch burn.
The weather
comes from the dialogs ([Sky cues](#sky-cues-the-weather-in-a-dialog)).

The moon is the real one of the day (`game/render/Moon.kt`: its age on the mean synodic month from a known new moon,
good to about a day): lit on the right while it waxes and on the left while it wanes, from a sliver to full (a
crescent's dark part faint in the earthshine), and in the sky only while it is up: with the sun at new moon (so never
drawn), highest at dusk at the first quarter, all night when full, before dawn at the last quarter, about 50 minutes
later each day. Along its way it rises lower and climbs (across the campfire's sky and the village's from the left);
its light lies on the water only while it is up. The village scroll's day says tonight's moon ("Nocoj: ščip · Tonight:
full moon").

The market scene trades nothing itself: a visit's market is the town bar's "⚖️ Tržnica · Market" (companion/README.md,
"Things to do on a visit"). Its stall has a seller at `stall` and a buyer at `buyer`, so that haggle could one day open
from here, with the host's villager behind the counter.
