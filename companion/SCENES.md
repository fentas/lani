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
  HUD: today and what's coming; 📜 scroll        people: tap → dialog (choices in Slovene) → reward
                                                  → "keep talking" with the tutor (role-play scenario)
```

## Parts and where they live

| Part | Code |
|---|---|
| Format, registry, daily logic (the contract) | `android/.../game/scene/`: `SceneSpec.kt` (format), `SceneArt.kt` (arts, slots, sprites, effects, stages), `Stage.kt` (stage directions and tap turns, see [Stage directions](#stage-directions-people-move-on-cue)), `Happenings.kt` (what's on), `Story.kt` and `Stories.kt` (the storyteller's evening story, see [Stories](#stories-the-evening-story)), `Notebook.kt`, `StoryBooks.kt` and `Vignettes.kt` (the learner's story notebook: every story heard written down, its questions, and its pictures' library, see [The story notebook](#the-story-notebook); the ink painters and the pencil sketch in `game/render/book/`, the notebook's pages in `ui/notebook/`), `Wildlife.kt` (the wild animals out, see [Wild animals](#wild-animals)), `SceneSights.kt` (which things a picture shows now and when the others come, see [The words panel](#the-words-panel-here-now-seen-before-still-hidden)), `ISpy.kt` (I spy with a child of the village, see [I spy](#i-spy-vidim-vidim-nekaj-česar-ti-ne-vidiš)), `SceneWorld.kt` (what the village has built, as the scenes show it: `needs`, the fixtures, see [What the village has built](#what-the-village-has-built)), `CultureLook.kt` (a culture's own look of an art: its colours, materials and variants, see [Culture looks](#culture-looks)), `TownMarkers.kt` (bubbles in the village), `SceneDoors.kt` (a thing of a picture that is a place with scenes of its own: the stream's mill into the mill, see [The mill inside](#the-mill-inside-from-the-stream-and-from-the-map)), `ScenePainter.kt` (painter interface), `Sky.kt` and `SceneFx.kt` (a dialog's weather and effects, what stays for the day), `Pokes.kt` (what a tap sets off, see [Pokes](#pokes-small-gotchas)) |
| Village view: full screen, camera, bubbles, the HUD, the scroll | `ui/game/VillageScreen.kt` and friends (`VillageHud.kt`, `VillageScroll.kt`), `game/render/` (the village renderer) |
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
    { "slot": "kettle", "word": "kotlicek" }
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
| `from` | where the village opens it, a list: `fire`, `forest`, `spot:<id>` (a spot of the landscape: meadow, pond, highseat, path, woodpile, rocks, riverbank, road, kopa: the charcoal pile deep in the woods, horizon: the land at the village's horizon; they always exist, so a scene there is open from the start), building types in lower case (`tent`, `field`, `well`, `hut`, `kozolec`, `beehive`, `palisade`, `watchtower`, `smithy`, `lipa`, `house`, `church`, `school`, `market`) or `project:<id>`: a village project's landmark, there once the project is finished (its id one of the culture's projects; the vineyard's terraces lead into the vineyard, the mill into the mill). Several scenes may share a place; the village offers a choice then. The first place that exists is where its bubbles show (a kitchen: `["hut", "house"]`). |
| `needs` | optional earliest age: `ogenj`, `tabor`, `zaselek`, `vas`, `trg`, `mesto` |
| `household` | only a room of a house (it opens from `house`): who lives in the house whose room it is, villager ids of the cast (`["france", "tine"]`); a house opens one room, its own, dealt by who has moved in (see [Houses and their rooms](#houses-and-their-rooms)). None: the room is anyone's (the kitchen). An older bridge ignores it. |
| `pack` | the word pack the objects teach. Every object's `word` is a word id in that pack (or in the object's own `pack`), so learning a scene's words is learning pack words: they count, get reviewed and pay the village like any pack word. |
| `objects[]` | `slot` (one of the art's slots, each at most once) and `word`; `needs`: what the village must have built for the thing to be there (see [What the village has built](#what-the-village-has-built)). The bridge serves them resolved: `sl`, `en`, `emoji`, `gender`, `plural`, `example_sl`, `example_en` copied from the pack. |
| `people[]` | `id`, `name`, `emoji`, `art` (a sprite), `slot` (one of the art's person spots), `always` (there all day, without a happening), `villager` (their id in the cast, [VILLAGERS.md](VILLAGERS.md): the friendship grows here too). |
| `happenings[]` | `who` (a person here), `when` (`morning` 5–11, `afternoon` 11–17, `evening` 17–22, `night` 22–5, `dawn`: the morning's first two or three hours round the month's sunrise, see [What's on today](#whats-on-today); empty = all day), `weekdays` (1 = Monday … 7), `months` (1 = January … 12, the months it is on in: the hay `[6, 7, 8, 9]`; empty = all year, see [Seasons](#seasons-in-their-months)), `chance` (per day), `dialog`, `dialogs` (its variants, see [Variants](#variants-not-the-same-dialog-every-time)), `reward` (`food`, `wood`, `stone`, `wisdom`; paid once a day when the dialog is done), `marker` (emoji for the village bubble), `needs` (the earliest age, `"vas"`, or what the village must have built, as an object's), `memory` (`{sl, en}`: what the person remembers afterwards, fitting "Še vedno mislim na …"), `guests` (`true`: only a visitor from a linked town meets it, see [Dialogs for guests](#dialogs-for-guests)), `stories` (`true`: the person tells tonight's story instead of a `dialog`, see [Stories](#stories-the-evening-story)), `act` (in an art with a stage: where its people wait for the learner, as a line's stage directions say it, and its dialogs start from there: Nejc in his bed, see [Stage directions](#stage-directions-people-move-on-cue)). |
| `dialogs[]` | `lines`: someone's line (`who`, `sl`, `en`) or the learner's turn (`choices`: 2–3, one `ok` or two where people would accept either, and at least one wrong; a right one may carry the other person's `reply`; a wrong one says `why` and carries their reaction as its `reply`, see [Reactions and more than one right answer](#reactions-and-more-than-one-right-answer); a why that is a rule names its grammar book page, `grammar`, see [A why that is a rule of the grammar book](#a-why-that-is-a-rule-of-the-grammar-book); the turn may carry a `puzzled` reaction for a wrong answer without its own, see [Adaptive turns](#adaptive-turns) and [Own traps](#own-traps); a spoken line, a reply and a puzzled reaction may say the speaker's `face`, see [Faces](#faces-the-person-you-talk-to-and-their-expressions)). 3–8 lines. `talk`: an optional role-play scenario to go on with the tutor. A line and a reply may carry a `sky` cue and an `fx` cue, and the dialog `sky_stays` and `fx_stays` (see [Sky cues](#sky-cues-the-weather-in-a-dialog) and [Effects](#effects-the-place-joins-in)); in an art with a stage, an `act` cue and `act_stays` (see [Stage directions](#stage-directions-people-move-on-cue)), and a turn's choices may each stand for a place in the picture, `tap` (see [Tap turns](#tap-turns-answer-by-tapping-the-scene)). `guests` (`true`): the dialog is for a guest, and so is every happening that plays it. Who can be named: anyone of the cast, but a happening whose texts name someone is on only while they're in the village and met ([What's on today](#whats-on-today)): name whom the scene is about and whom they'd be with (Micka at the market with Marko at his stall), not someone the place doesn't bring. A dialog may also say `count` (a number from the village, see [Numbers](#numbers-counting-what-the-village-has)), `memory` (what the person remembers of this variant, instead of the happening's), `months` (the months it plays in, within its happening's: a variant about the snow in winter, see [Seasons](#seasons-in-their-months)), `words` (the learner's words a tutor's variant weaves in, each the tested element of a turn, see [Your words in the dialogs](#your-words-in-the-dialogs)) and, a tutor's, `source: "tutor"`. |
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
🔊 and the why stay. A Slovene word's card has its case ("🧩 Sklon · Case": the case and number, the word that decided
it underlined in the line, the rule's page; [README](README.md#the-case-lens)), and the header's ⋯ has "🎨 Skloni ·
Cases": each word of the lines underlined in its case's colour, dotted where the line can't tell (a wrong choice's
never), with a legend; remembered for every later dialog (`CasePrefs.colours`, off by default). Beside the 🎤, a turn
whose rule has a page shows "📖 Namig · Hint" (the rule, never the answer: see
[The hint](#the-hint-the-rule-never-the-answer)), and a long press on a line said opens its grammar word by word (see
[The sentence's grammar](#the-sentences-grammar-a-long-press-on-a-line)). Every dialog is this panel: a scene's, the
storyteller's evening story, a keeper's talk at a spot of the landscape, a newcomer's arrival, a village project's step
([Project steps](#project-steps-a-village-projects-step-as-a-short-scene)) and a guest's dialog on a visit. The chip replaced "🎤 Odgovarjaj z glasom ·
Answer by speaking", which made the choices untappable (the 🎤 the only way to answer, "👆 Raje tapni · Tap instead"
after two failed takes); the 🎤 beside the choices does that job, and a mastered rule's turn is said whole anyway (see
[Adaptive turns](#adaptive-turns)).

### The learner's voice

The learner has a voice of their own in the dialogs (Jan, October 25, 2026: "If I select a sentence in a dialog, it should
speak it, unless I selected it via the microphone. And does the player have a voice, meaning male or female?"). Their
line is said aloud as it comes, in their voice, and the other side's reply waits for it:

- **Said:** a choice tapped (in the list, or its place in the picture at a [tap turn](#tap-turns-answer-by-tapping-the-scene)),
  "✓ Naprej" at an [echo](#rules-not-yet-what-the-learner-is-asked-grows-with-the-grammar), a turn
  [typed](#adaptive-turns) right, and a wrong choice tapped or typed that the person reacts to (they answer what they
  heard). Nobody said those yet.
- **Not said again:** a line the learner said into the 🎤 (a choice said, a turn said whole, an echo said): they said it
  themselves, and the reply comes as it always did. A form typed that none of the choices has (a slip, "Deset jajcev,
  prosim.") isn't said either: it is the learner's own wording, with no line of the dialog's to make a clip of.
- **In turn** (`ui/scene/DialogVoice.kt`): the learner's line 0.25 s after it shows (the person's reaction to the pick plays
  meanwhile), said to its end (`Speaker.sayThrough`); the reply a breath (0.25 s) after it has been heard, and after a 🔊
  tapped meanwhile has been heard too, never over it; the person is drawn talking only while their own line is said.
  Without a line of the learner's to say, the reply comes 0.7 s after the pick, as before. The next step ("Naprej", the
  next pick) cuts what is being said, as it always did.
- **Its 🔊:** the learner's line in the conversation has its 🔊, like the others' lines, which says it again in their voice
  (TalkBack: "Poslušaj · Listen"; a long press records it again). A slip typed has none.
- **The voice, "🗣️ Moj glas · My voice"** (Jan, October 29, 2026: "for teo this should be maybe a boy voice -> maybe we offer
  (in general) like 2,3 voices to choose from?"; docs/plans/05-built/my-voice.md): the learner chooses it from the voices
  their village's cast offers for their gender and age, the cast's `players` (`companion/voice-cast.json`,
  `cultures/*/voice-cast.json`): 2 or 3 a gender for a grown-up and for a child (the profile registry's child flag,
  `LANI_CHILD`), the default first. Without a choice, the default: in the Slovene village Luka for a man, Nina for a woman
  (the narrators' Slovene voices), in the Italian one Teo, a boy, a boy's Italian voice; a profile without a gender is
  offered both genders' voices of their age, the female one's by default (the content still speaks to them as to a man).
  No voice offered is a voice anyone in the cast speaks with (a test: the friuli male narrator, Chris Basetta, is Pastore
  Davide's voice too, so Teo's line and Davide's reply sounded like one person). "🔇 Brez glasu · No voice" turns the
  learner's lines off: none is said, and their line has no 🔊.
  - **The choice** is the learner's, kept by the node in their learner profile (`learner-profile.json`
    `preferences.my_voice`), so it holds on every device, and voice-build and a visit say their lines in it. The app asks
    for their lines as the voice `player`, as before; the bridge says them in the voice chosen (a word to three inside its
    carrier sentence, as a re-record), so a line's clip of Luka is the male narrator's. Changing the voice voices nothing
    again in bulk: the next lines come in the new voice as they're asked for, the old clips stay in the store.
  - **Where:** "⚙️ Nastavitve › 🔊 Zvok in glas · Sound and voice", first on the page: each voice its face (👦 👧 👨 👩),
    its name and ▶, the one in effect marked; a tap on a row chooses it (64 dp high: a child's finger on the tablet). And
    once, at the first line of theirs a dialog says: a sheet over the dialog, "🗣️ Kateri glas je tvoj? · Which voice is
    yours?", while the line is said in the default and the dialog goes on behind; a tap on a voice chooses it and closes
    the sheet, ▶ only listens, "✓ V redu · OK" or a tap beside the sheet keeps the default, which the node remembers as
    chosen too, so it never comes again on any device.
  - **The ▶:** a short line in the village's language with the learner's name ("Ciao! Sono Teo.", "Živjo! Jaz sem
    Jan."), made by the node in that voice the first time it is tapped, on the normal clip path (a live line, a few
    characters; the reserve's rules), then kept on the node and the phone.
  - **When no clip of their voice can be had** (no ElevenLabs key, the quota at its reserve, the node away): in the
    Slovene village the voice's own local Gepard voice (marko for a man, nina for a child's); never another person's
    clip, never a cast member's voice. Else the phone's offline voice or text-to-speech, in the voice's gender when the
    phone names one (Samsung's "SMTm…", Google's "#male_…"), a man's a little lower when it doesn't, a child's at a
    higher pitch (×1.3). On the phone the clips are looked up by the voice the node names in its voice profiles (`GET
    /voice/profiles`: `player`, and `mine` with the gender, the age, off and whether they have chosen).
  - **On a visit** the town speaks the learner's lines in its own voice of their kind, in its language: the voice of their
    voice's gender and age at the place their choice has on their own list (a boy's second voice at home: the town's
    second boy's), never a voice someone of that town speaks with; off stays off.
- **Where:** every dialog panel: a scene's dialog and the storyteller's evening story (its turns), a keeper's talk, an
  arrival, a village project's step and an age's party, and a guest's dialog on a visit. In «Vidim, vidim» the learner's
  guesses are words and stay silent, without a 🔊 ([I spy](#i-spy-vidim-vidim-nekaj-česar-ti-ne-vidiš)).
- **Settings:** "🗣️ Moj glas · My voice" above is the only setting of the learner's lines (its "🔇 Brez glasu · No voice"
  turns them off); otherwise the learner's line is said as everyone's lines are, at the phone's volume, with TalkBack too
  (it reads a choice before the tap, the line is said after it).

**QA:** `companion/bin/qa --steps my-voice`: the offer at the first line of the learner's (QA's hook "audio:myvoice:offer"),
a voice's ▶, a voice chosen in it and the next line said in it (tag MyVoice, Speaker), "🔇 Brez glasu" and back, the settings'
page. `companion/bin/qa --steps player-voice`: a dialog by the fire (Babica's soup): a choice tapped, its line said in the
learner's voice ("said «…»: node clip (player)" or "clip on the phone"), heard to its end before the reply starts (tag
Speaker); a turn typed right (QA's hook "turns:type"), said; a choice said into the 🎤 (QA's hook "mic:<text>", a debug
build: the 🎤's take as the recognizer would give it), not said, the reply only; a screenshot of the 🔊 on the learner's
line.

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
it as the person's line, voiced like their others; the person looks puzzled (a hand to the chin and a confused face, or the
reaction's own `face`: [Faces](#faces-the-person-you-talk-to-and-their-expressions), `DialogMood`). The
choice is crossed out, the mistake counts once as before, and the why is one tap away ("💡 Zakaj? · Why?"): first what
the person made of it. A wrong choice without a reply (a tutor's older scene or story) shows its why at once, as before.

Where people would accept more than one answer, a turn has two right choices, each with its own reply: a greeting, a
goodbye, thanks, a yes or a no, a question ("Adijo!" and "Čav!", "Hvala!" and "Hvala lepa!"), polite answered politely,
cheeky cheekily, so choosing is about what the learner wants to say. Either goes on with the dialog; the cues follow
the one taken (the dialog's `picks` are the file's indices), so give both replies the same `sky` and `fx`. Such a turn
keeps a wrong choice, and a turn that tests a form (the choices differ in one ending) has one right answer. A handful
per scene, where it is natural.

### Faces: the person you talk to, and their expressions

The person the learner talks to is on screen as the same chibi sprite, and their face follows the conversation (Jan,
October 29, 2026: "maybe we can have the character shown on the free speech / dialogs - with different expressions"). A
face is an expression of its own, apart from how they stand (`Pose`): someone can wave and laugh. There are twelve, in
this order: `neutral` (resting, friendly: the default, never written), `happy` (a smile), `laugh` (the eyes shut in arcs,
the mouth open, pink cheeks; a ♪), `surprised` (the eyes wide, a round mouth; a !), `curious` (a question asked, one brow
up; a ?), `confused` ("Hm? Kako, prosim?", a misunderstanding: one eye squinted, the mouth wavy; a ? that wobbles),
`thinking` (the eyes up, the mouth to one side; … in a thought bubble), `worried` (the brows up in the middle, a small
mouth; a sweat drop), `sad` (kind disappointment, the eyes down), `cross` (mild and comic, never scary: the brows down,
red cheeks; a little cross mark), `shy` (blushing, the eyes aside) and `proud` (the eyes shut, a smug smile, the chin up;
sparkles).

**In the content** a spoken line (one with `who`), a choice's `reply` and a turn's `puzzled` may carry `"face"`, one of the
eleven names besides `neutral`, in every dialog of the shapes above: a scene's dialogs and variants, the tutor's variants,
a project's step, an age's party, an arrival, a keeper's talk and a story's lines:

```json
{ "who": "micka", "sl": "Krava je bolna …", "en": "The cow is ill …", "face": "worried" }
{ "sl": "Žejen sem.", "en": "I'm thirsty.", "why": "…", "reply": { "sl": "Žejen? Tu imaš vodo.", "en": "Thirsty? Here's water.", "face": "surprised" } }
```

Direct like a director: a face only where the line carries a feeling other than the speaker's resting one; most lines stay
unmarked. A right choice's reply is usually `happy`, now and then `laugh` or `proud`; a reaction to a wrong one (what the
person made of it) `confused`, `surprised` or `curious`, `laugh` when it's funny; a puzzled line `confused`; a question to
the learner `curious`; `worried` or `sad` when the story says so (the cow is ill, the rain on the hay); `cross` only mild and
comic; `shy` for compliments and the children; the person's character shows (grandpa's proud stories, the children's
laughs). The bridge checks every name (`faceErrors` in `crossCheck`): an unknown one is reported with where it is, so is
`neutral`, and so is a face on a choice (the learner's line: its face goes on its reply) or on a turn. The keys are
`z.object`s: a bridge from before faces strips them, and an app from before never sees them; the app reads them leniently
(`FaceSerializer`: a name it doesn't know is no face).

**What the content doesn't say, the moment brings** (`DialogMood`): a wave and a smile when the dialog opens, a happy hop
and a smile at a right pick, a worried shrug at a wrong one, a hand to the chin and confused (or the reaction's face) at a
wrong one they answer or a form typed that none of the choices has, a hand at the ear while the learner speaks into the 🎤,
at the end without a mistake a cheer, proud (a child laughs), else a smile. A line without a face of its own is said with
what the moment brings (`DialogMood.lineFace`): the first line smiling (a greeting), a reply to a right line smiling, a
reaction confused, a line after one of theirs with that one's softer face (after a laugh a smile, after a smile at rest),
any other at rest. A line's face shows while it is said and holds 3.5 s after it was heard (a line no voice says, after it
could be read: 50 ms a character), then eases back to the
resting face through its softer one (a laugh through a smile, surprise through curiosity: `Expression`); a new face comes
in with a blink, never popping, so a dialog whose lines change faces often doesn't flicker; blinking goes on, and a
talking mouth moves inside the face's mouth shape. Asleep on the stage (a line's `act` with the pose `sleep`), the sleep
wins: the eyes stay shut in the picture and in the header, with no face over them; the line's bubble keeps its face (how it
was said, dozing off). Lying awake (`lie`), the face shows as on anyone. Every pose brings its face where nothing names
one (`Pose.face`: a hop smiles, a cheer laughs, a shrug is sad, a hand at the chin thinks): the training stage, the
school's teacher and the villagers' pages have their moods' faces too.

**On the screen**: the dialog panel's header shows whom the learner answers (the person, or in a dialog of several whoever
spoke last) as a live portrait, the head alone, with their face; while the panel rises over the picture for a typed turn
([Adaptive turns](#adaptive-turns)) it keeps them in view. Each of their lines has a small still face by its bubble, the
face it was said with, so the conversation shows the expressions. The picture's sprite wears the same face, its emote over
its head (drawn after the outlines, never a part of them to tap). A sheet with a stage of its own (a keeper's talk, an
arrival, a project's step, an age's party) shows the face on its stage's portraits, and the header's portrait comes alive
once the stage folds away for the keyboard. A guest's dialog on a visit has it as at home. TalkBack hears who and how
("Babica Micka, se smehlja · smiles").

**The art** (`PeoplePainter.faceOf`, `PeoplePainter.emote`) is in the sprite's own pixels, the same in a scene and in a
portrait (the sprite cut at the waist, or the head alone for a small avatar: `Framing.HEAD`): the eyes (dots, shut in arcs,
shut smugly, wide with a glint, squinted, up, down, aside), the brows, the mouth, the blush; the emotes drawn not emoji,
in little white badges for ! ? and …, plain for the note, the drop, the cross mark and the sparkles, by the head's far top
corner and inside a portrait's canvas. Without a face everyone is drawn pixel for pixel as before (`FacesGoldenTest`).
Contact sheets of every face on a few sprites: `FacesRenderTest` (build/faces).

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

Of those in season ([Seasons](#seasons-in-their-months): a variant with `months` only in them):

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

**The tutor writes new ones.** When the learner has heard all of a happening's variants (those in season this month; a
happening out of season isn't asked about until it is back), the bridge tells the tutor
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
from `GameState` (`Counts.of`: the day's dice is `Happenings.roll(seed, date, "scene/happening/dialog#n")`). The picture
has the number too (`SceneTalk.counted`, `SceneFrame.count`): an effect that brings what is counted draws that many, so the
learner can count them in the picture (Luka's sheep at the water: `flock`, the ewe always there among them; France's sacks
by the mill: `sacks`). What the other counting dialogs count (the woodpile's logs, the cows on the pasture, a basket's eggs)
is drawn as the place has it, not yet by the number (docs/plans/05-built/scene-coherence.md, "What's left"). A text
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
"Preveri · Check". The panel's header keeps the person in view meanwhile: their live portrait with their face
([Faces](#faces-the-person-you-talk-to-and-their-expressions)). Once the keyboard is down, the picture comes back; a tap turn, with no keyboard, keeps it. The
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

**In the village:** the scroll's places say under a place how many of the learner's words the dialogs on there now test:
"Hiša · House", "📇 3 tvoje besede · 3 of your words" (`TownOverview.of`, `inHappening`, `ScrollData.words`; a story's
evening says none, its telling is the evening's).

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
where before it opened the project straight away. The Friuli pack has seven more (below; docs/plans/05-built/places-it-projects.md),
each on the art of the place its landmark stands at: the vineyard's terraces (`nella-vigna`, `hills`), the greasy pole
(`la-cuccagna`, `square`: open while it stands, in May or two weeks after it is finished, so its talk names no month), the
bridge (`il-ponte`, `stream`), the bee meadow (`il-prato-delle-api`, `apiary`), the fountain (`la-fontana`, `square`), the
big hay barn (`il-grande-fienile`, `field`) and the tower clock (`l-orologio`, `square`: every happening needs the church
its clock is drawn in). Where the art draws the project as scenery without a slot of its own (the square's maypole,
fountain and clock, the stream's cart bridge, the field's big barn), the scene's things are the place's slots and the
project's own words are its pack's, taught by its dialogs; the lines speak of the project as the art draws it.

A pack whose language has no curated scenes (no `companion/scenes/<language>/`) brings its village's everyday places
too, the same arts as the Slovene scenes and so the same slots, `needs` and stages: the Friuli pack has its tent, its
kitchen, its field, its wood, its stream, its pond, its smithy, its mill and its school in Italian (below;
docs/plans/05-built/friuli-scenes.md, docs/plans/05-built/mill-scene.md, docs/plans/05-built/friuli-school.md), so its
tent, hut, field, smithy and school have "Vstopi · Go in", the wood, the stream bank and the pond open their scene from
the first day, and the mill once its project grinds. Their words are the pack's own packs (`nella-tenda`, `in-cucina`,
`nel-campo`, `animali-del-bosco`, `nel-bosco`, `al-ruscello`, `allo-stagno`, `nella-fucina`, `nel-mulino`, `a-scuola`)
and the Italian starter packs, and each has its I spy clues
in `companion/ispy/scenes/` (the app bundles those, so the clues come with the next app build; the scenes themselves go
out with the bridge).

| Pack | Scene | From | What is there |
|---|---|---|---|
| `primorska` | `v-gorah`, V gorah · In the mountains (`alps`) | the horizon | a mountain pasture above a lake under Triglav, the way Bohinj looks from Vogar: the lake with the peaks mirrored in it, the little church and its stone bridge on the shore, the shepherd's hut, cows with bells, a larch and a spruce, a stream with a waterfall, a big stone, a dry-stone wall with a step stile; Janez (a guest's first time in the mountains, the choughs off the peak and a storm over Triglav), Luka (the cows' bells, cheese from the pasture; milking at dawn while the peaks glow pink), Nejc (fishing in the morning fog, the church bell, a fish jumping); chamois by the forest and an ibex on a crag, by day |
| `primorska` | `na-gricih`, Na gričih · On the hills (`hills`) | the vineyard project | the village's vineyard, its rows running away to a white farmstead with its cellar, cypresses and trees about it, the rolling hills of Brda with the church on a hilltop, the Alps and a glint of the sea; the kaki behind a dry-stone terrace wall, lavender; Marko (the starlings in his vines, a first visit), Janez (the bell against a storm from the Vipava valley, into the cellar), Tine (grandpa's tractor, counting crates) |
| `friuli` | `al-fuoco`, Al fuoco · At the campfire (`campfire`) | the fire | the village's campfire, in Italian: Nonna Rosa's tisana in the evening, Tommaso bringing wood in the afternoon, and Nonno Bepi's evening story ([Stories](#stories-the-evening-story)). |
| `friuli` | `il-mare`, Il mare · The sea (`sea`) | the horizon | Grado: the lagoon with its casoni, the bricole and the gulls, the pier and the batela, the beach and the open sea; Zia Nives (a guest, the gulls), Tommaso (the waves, a squall over the lagoon), Nonno Bepi (sea mist, then the sail) |
| `friuli` | `nella-tenda`, Nella tenda · In the tent (`tent`) | the tent | the learner's tent at the camp; Pastore Davide (a lantern in the evening, shelter from the rain: by the lantern in a summer thunderstorm), Lorenzo (a bedtime story, asleep before its end), Giulia (her treasure map, found with a tap) |
| `friuli` | `in-cucina`, In cucina · In the kitchen (`kitchen`) | the hut, a house | Nonna Rosa's kitchen, growing with the building's level (the fogolâr, its hearth, at every level, the spolert beside it from 2: [Culture looks](#culture-looks)); Rosa (polenta or polenta and frico in the evening, breakfast, the eggs counted), Zia Nives (the radio: the bora, the Udinese match) |
| `friuli` | `nel-campo`, Nel campo · In the field (`field`) | the field, the hay barn | Mugnaio Franco's field; Lorenzo (the scarecrow's hat, a scarf in the cold months), Franco (the wheat harvest in summer, Lei to signor Franco; his horse Baio in the evening) |
| `friuli` | `nel-bosco`, Nel bosco · In the wood (`forest`) | the forest | Pastore Davide (Bianca the lamb lost, in the fog in winter), Apicoltore Aldo (mushrooms in autumn, the poisonous one; counted), Nonno Bepi on the high seat at dawn (the stag and the hinds; the owl, found with a tap) |
| `friuli` | `al-ruscello`, Al ruscello · By the stream (`stream`) | the stream bank | Mugnaio Franco (the mill he dreams of, until the mill project grinds), Nonna Rosa (the laundry, a sheet in the wind), Pastore Davide (the sheep drinking, the lamb that won't; counted) |
| `friuli` | `allo-stagno`, Allo stagno · At the pond (`pond`) | the pond | Apicoltore Aldo (the heron in the mist, the ducks), Tommaso (a frog caught and let go, the tadpoles in spring), Nonno Bepi fishing with his dog Moro in the afternoon |
| `friuli` | `nella-fucina`, Nella fucina · In the smithy (`smithy`) | the smithy | Fabbro Bruno's smithy, growing with its level; Bruno (a horseshoe in the morning, counted in the bucket; an order from Gorizia written in his black notebook, Lei to signor Bruno), Tommaso (the bellows from level 2) |
| `friuli` | `a-scuola`, A scuola · In der Schule (`school`, in the Friuli look: [Culture looks](#culture-looks)) | the school | Maestra Elena's classroom, growing with the school's level (the map of Friuli and the globe from 2, the clock from 3); Elena (the morning roll call on school days, "Presente, maestra!", Lei to her and "maestra"; the mistakes on the blackboard in the afternoon, il/lo/la, essere or avere, the plural; the map of Friuli from level 2: the mountains, the Tagliamento, the sea, Udine and Gorizia), Giulia (the globe from level 2: Italy the boot, the blue and the green), Lorenzo (his homework in the afternoon: the syllables, the pencils counted, the numbers in words), Tommaso at the door (his football in the afternoon; the merenda at break); 6 happenings, 14 dialogs, 28 turns, 54 wrong choices; its words `a-scuola` (18) and the Italian packs' chair, window, door, book, map, exercise book and clock |
| `friuli` | `nel-mulino`, Nel mulino · In the mill (`mill`) | the mill project's landmark, and the stream's mill | Mugnaio Franco's mill once it grinds ([The mill inside](#the-mill-inside-from-the-stream-and-from-the-map)): Franco (the morning start, the wheel iced in winter; grinding, the sacks counted; the harvest weeks; the evening, the lantern), Lorenzo (a sack too heavy, flour over everyone; the cat and the mouse, found with a tap), Nonna Rosa (maize for her polenta, poured into the hopper), Pastore Davide (the miller's toll, la molenda, weighed and measured) |
| `friuli` | `sulla-torre`, Sulla torre · On the watchtower (`watchtower`, in the Friuli look: [Culture looks](#culture-looks)) | the watchtower | the view from Teo's tower over a Friulian village, the tower higher and better built with each level ([What the village has built](#what-the-village-has-built)); Pastore Davide on watch in the evening and at night (the torch lit, the wolves' eyes in the wood, the horn blown; the torch blown out by the wind, the fog in the valley from October to March), Tommaso watching for il cramâr, the pedlar from Carnia (the birds off the wood, the cramâr down the road under his crassigne; the spyglass found with a tap; his coins counted on the day's dice), Mugnaio Franco (a storm over the mountains from April to October, the seconds to the thunder counted, his hay before the rain in summer; the village shown from up high in the morning: the road, the gate, the palisade, the Isonzo, the campanile, its people counted, Friuli's flag), Tommaso and the crow on the railing in the evening (found with a tap, the birds off the wood); 5 happenings, 14 dialogs, 31 turns, 34 wrong choices (29 naming an Italian page), 2 tap turns, 3 counting variants; its words `sulla-torre` (18) and the Italian packs' road, wood, river, crow, flag, mountain and horizon |
| `friuli` | `in-salotto`, In salotto · In the living room (`livingroom`, in the Friuli look) | a house: Nonno Bepi's room (`household`: bepi) | Nonno Bepi's room by the fogolâr, where the village sat round the fire for his stories: Bepi lighting the fogolâr on cold mornings (October to April; the chestnuts roasting in October and November, counted on the day's dice in the pan; the embers blown up), Giulia and Moro (waking him, his tail behind the door found with a tap, the salami he wants), the wedding photo from level 3 (Ida, the best dancer; his glasses found with a tap, his old book), hide-and-seek with Giulia (under the table or behind the door, found with a tap, her plait), Friulian words (mandi, fogolâr; the merenda at four); 5 happenings, 12 dialogs, 28 turns, 33 wrong choices (20 naming a page), 4 tap turns; its words `in-salotto` (18) |
| `friuli` | `in-cantina`, In cantina · In the cellar (`cellar`, in the Friuli look) | a house: Vignaiolo Matteo's (`household`: matteo; Aldo lodges there) | Matteo's cantina: Ostessa Marisa fetching a jug of wine for the osteria (the tap, the jug full; bottles counted for Sunday's feast; Lei to her), the candle blown out and lit again in the evening (the glass found with a tap; water for the child), the vendemmia in September and October from level 2 (the Ribolla gialla in the crate, the press turned, the must for everyone, wine for the grown-ups), the San Daniele ham and the Montasio from level 3 (the three salami counted), the frasca hung outside from level 2 (an osmiza on the Karst; the corks); 5 happenings, 10 dialogs, 22 turns, 23 wrong choices; its words `in-cantina` (16) |
| `friuli` | `in-soffitta`, In soffitta · In the attic (`attic`, in the Friuli look) | a house: Ostessa Marisa and Tommaso's (`household`: marisa, tommaso) | Tommaso's attic bedroom under the coppi: waking him in the morning (in his bed, sitting up, the dragon he dreamt of; school days from September to June; the scarpets by the bed), Marisa's old cradle rocked and the lullaby, the bora at night from October to April once the room is lined (level 2: the window blown open, the lamp lit, asleep again), the maize cobs drying under the roof (three bunches of five, counted), the ball found with a tap (level 2), "signora Marisa"; 5 happenings, 11 dialogs, 22 turns, 23 wrong choices; its words `in-soffitta` (16) |
| `friuli` | `in-bottega`, In bottega · In the workshop (`workshop`, in the Friuli look) | a house: Mugnaio Franco and Lorenzo's (`household`: franco, lorenzo) | Franco's bottega: the hammer and the nails (counted on the day's dice), the saw and the sawdust from level 2 (Lorenzo sweeping it into the garden), Lorenzo's little wooden horse mended with glue from level 3 (its legs counted), the Friulian chair on the wall and the turned legs in their basket (Manzano, the chair town; six legs, four for a chair), the broom found with a tap and the shavings (level 2); Lei to signor Franco; 5 happenings, 10 dialogs, 20 turns, 21 wrong choices; its words `in-bottega` (16) |
| `friuli` | `al-mercato`, Al mercato · At the market (`market`, in the Friuli look) | the market | a market day in a Collio village, growing with the market's level; Nonna Rosa at her stall (her eggs, «Quanto costano le uova?», sei uova; the Montasio weighed on the scale and what she makes of it, the frico; the eggs her hens laid, counted on the day's dice; the cherries in June), her hen running off onto Matteo's cart (a tap turn, «Dov'è la gallina?»; called home, «Gallina, vieni qui!»), Ostessa Marisa shopping for the osteria (Lei to «signora Marisa»: the potatoes on the scale, due chili, «Scusi, quanto costa tutto?»; what goes in the frico and «Vorrei…» to Nonna Rosa; white asparagus from the lagoon in April and May), Vignaiolo Matteo's cart (Collio wine for the grown-ups, grape juice for the child; the bottles for the osteria counted on the dice and the juice's price; the vendemmia in September and October, «Sì, vengo!»), a busy sagra on summer and autumn weekends (the bell, the crowd, the games, the pigeons; the chestnut festival in October and November); 5 happenings, 14 dialogs, 34 turns, 44 wrong choices; its words `al-mercato` (18) and the Italian packs' scale, egg, bread, cheese, apple, basket, cart and little bell |
| `friuli` | `in-piazza`, In piazza · On the square (`square`, in the Friuli look) | the linden, the well | a Collio village's piazza, its linden and its well growing with their levels; Nonno Bepi on his bench (tu and «nonno»: his age guessed, «Hai cento anni?» with avere where German says sein; a coffee from the osteria; the campanile's bells and the hour of lunch, all'una; the snow up to the roof long ago, March to November), Moro his dog away and called home by the flowers, a bit of bread for him in the evening; Tommaso's football in the afternoon (the ball lost under the bench, a tap turn, and a goal; the rain on the match), the cat up the linden (milk, then cheese); Maestra Elena at the well on school mornings (needs the well; Lei and «maestra»: the buckets and the plural, the buckets the school garden needs counted on the dice, all'orto, «Mi dia i secchi», noi portiamo); the sagra on summer weekend evenings (the banda, the drum, a dance with Bepi); 6 happenings, 15 dialogs, 36 turns, 48 wrong choices; its words `in-piazza` (16) and the Italian packs' ball, dog, cat, house, bell tower, flower, sun and fence |
| `friuli` | `dalle-api`, Dalle api · At the bees (`apiary`, in Friuli's look) | the beehive | Apicoltore Aldo's apiary growing with the bee garden's level; Aldo (the hives opened with the smoker in the morning, March–October, quietly: "Piano, piano: le api sentono tutto"; the colours of his hives, the smoker tried; World Bee Day in May, an idea from Slovenia next door; listening at the hives in the evening, the bees huddled in winter, November–February), Nonno Bepi (a swarm in spring, April–July, onto the acacia, found with a tap, Aldo putting it into the empty light blue hive; the acacia in flower in May and his mother's acacia fritters), Giulia (bread and honey for the merenda and Aldo's MIELE sign to read; the honey spun in the extractor, May–August, pale acacia honey; dark chestnut honey in autumn, September–November; her jars for the market, counted; a bee sting handled gently, April–September); 5 happenings, 14 dialogs, 34 turns, 43 wrong choices (32 naming a grammar page), 1 tap turn, 1 counting variant; its words `dalle-api` (18) and the bench (`in-cucina`) and the flowers (`festa-liberazione`) |
| `friuli` | `in-chiesa`, In chiesa · In the church (`church`, in Friuli's look) | the church | a Collio village's church growing with its level; Zia Nives (the flowers for Sunday on Fridays and Saturdays, where the vase goes found with a tap on the altar, the vase without water; someone practising the organ, a bit hard of hearing she has it played louder and sings as she did in the choir), Lorenzo (the noon bell, lifted up to the rope: the stage's `rope`; a proud bell-ringer; in March and April the bells that fly to Rome before Easter; the nativity in December and January, the ox, the donkey and the star, and singing with the organ at Christmas), Nonna Rosa (a candle lit in the evening, the candles counted, the bora blowing the door open and the candle out); kind and never preachy; 5 happenings, 13 dialogs, 28 turns, 35 wrong choices (24 naming a page), 1 tap turn, 1 counting variant; its words `in-chiesa` (12) and the candle (`festa-natale`), the bell (`festa-pasqua`), the rope (`nella-tenda`), the window and the door (`casa-e-cucina`), the pews (`in-cucina`'s panca), the flowers (`festa-liberazione`) |
| `friuli` | `nella-vigna`, Nella vigna · In the vineyard (`hills`) | the vineyard project's terraces | Vignaiolo Matteo's vineyard on the Collio's terraces: Matteo among his vines every morning (pruning in winter, the new leaves in spring), the grape harvest (September–October; the crates counted), Giulia chasing the starlings off the ripe grapes and her persimmons in the autumn (the tree found with a tap), Lorenzo waiting for the red tractor and its six crates, Nonno Bepi watching a summer storm from the wall (the bell against the hail, the seconds to the thunder counted); 6 happenings, 13 dialogs, every effect of the art |
| `friuli` | `la-cuccagna`, L'albero della cuccagna · The greasy pole (`square`) | the greasy pole, while it stands | Pastore Davide and the soaped pole he slid down (the cat climbing the linden better than the boys), Davide greasing it in the morning (the ribbons in the wind), Lorenzo dreaming of the prizes hung for the feast and counting the four ribbons, Tommaso's football by the pole (the ball found under the bench with a tap), Nonna Rosa at the feast on weekends (the band, the polka) and with frico or gubana for the climbers; 6 happenings, 12 dialogs |
| `friuli` | `la-fontana`, La fontana · The fountain (`square`) | the fountain project's fountain | Fabbro Bruno and the four iron spouts he forged (Lei to signor Bruno; counted; frozen on a winter morning until the sun), Zia Nives hearing the village's news at the fountain, Giulia cooling off in the summer and playing her article game the rest of the year, Lorenzo filling his grandpa's jug (counted), Bruno washing the smithy off his hands as the evening bell rings; 6 happenings, 13 dialogs |
| `friuli` | `il-ponte`, Il ponte · The bridge (`stream`) | the bridge project's bridge | Mugnaio Franco watching a cart cross his new bridge (the carts counted) and telling its numbers (six metres, two pillars; the footbridge found with a tap), Fabbro Bruno checking it after a night's rain (the stream in flood; snow on the planks in winter), Pastore Davide's flock drinking by it (Bianca last), the firefighters' drill with their pump once the fire station stands, Tommaso and the trout (the dragonfly found with a tap), Apicoltore Aldo in the evening (the bell, the bats); 7 happenings, 14 dialogs |
| `friuli` | `il-grande-fienile`, Il grande fienile · The big hay barn (`field`) | the big hay barn project's barn | Mugnaio Franco and the hay under its roof (a summer storm passing), Franco counting the village's stores (from its food), Pastore Davide taking hay for his sheep in the cold months and watching the bats at dusk, Lorenzo counting the barn's eight posts (Baio back in the shafts) and drawing the scarecrow a face (the crows back), Vignaiolo Matteo and the hare in the grass; 7 happenings, 14 dialogs |
| `friuli` | `il-prato-delle-api`, Il prato delle api · The bee meadow (`apiary`, in Friuli's look) | the bee meadow project's meadow | the meadow the village made for Apicoltore Aldo's bees, by his apiary (the bee garden's words, Dalle api's: the same picture), telling what Dalle api doesn't: Nonna Rosa on the bench by the meadow's flowers as the season has them (the dandelions and daisies and a crown of daisies in spring, the clover the children sowed and the purple phacelia in summer, the asters, the bees' last lunch before winter, in autumn), Aldo in the afternoon (a last puff of smoke, then the bee on the flower: the nectar, the pollen on its legs, six legs and four wings; no bees, no cherries), Maestra Elena's lesson in the meadow on school days (no fear; who makes the honey; the wings and the legs, the plural of ala and zampa; the colours: the bee, Aldo's yellow sign), Tommaso and the hives the children painted white, yellow, light blue and green with their numbers (the light blue hive and the comb found with a tap; the meadow under snow in winter), Lorenzo counting the bees and turning the extractor (a swarm off the hives onto the acacia in May and June, a dandelion clock and a wish in spring, a four-leaf clover in summer); 5 happenings, 17 dialogs, every effect of the art |
| `friuli` | `l-orologio`, L'orologio del campanile · The tower clock (`square`) | the tower clock project's clock | Maestra Elena and the clock's two hands (never an hour read off it: it shows the phone's), Tommaso playing before school until the bell sends him off, Fabbro Bruno and the gears he forged (the key, twelve strokes at noon), Nonno Bepi on the days before the clock (the sun, the shadow), Zia Nives and her radio, the clock lit like a little moon on the winter evenings; 6 happenings, 12 dialogs, every one with the church |
| `kaernten` | `am-feuer`, Am Feuer · At the campfire (`campfire`) | the fire | the village's campfire, in German: Oma Resi's tea in the evening, Maxi gathering wood in the afternoon, and Opa Sepp's evening story ([Stories](#stories-the-evening-story)). |
| `kaernten` | `am-see`, Am See · By the lake (`alps`) | the horizon | the Faaker See under the Mittagskogel in the Karawanken, in Carinthia's look ([Culture looks](#culture-looks)): the turquoise lake, the Mittagskogel's pyramid with its summit cross and the Karawanken's ridge, the little church with its onion dome on the shore, its stone bridge, the farm on the far shore (the farmhouse timbered above, a Harpfe, the boathouse and the jetty), the Alm with its Almhütte and its trough, Fleckvieh cows and cowbells, larches and spruces, a waterfall; Opa Sepp (a guest's first time, a storm over the Mittagskogel), Florian (the cowbells on the Alm; milking at dawn while the peaks glow), Maxi (fishing in the morning fog); chamois and an ibex |
| `lakeland` | `by-the-fire`, By the fire (`campfire`) | the fire | the village's campfire, in English: Granny Maggie's hot chocolate in the evening, Harry bringing wood in the afternoon, and Old Wilf's evening story ([Stories](#stories-the-evening-story)) |
| `lakeland` | `on-the-fell`, On the fell (`alps`) | the horizon | a fell above a lake in the Lake District, in its look ([Culture looks](#culture-looks)): rounded fells with their walled fields, bracken, crags and a cairn on the summit, the dark lake with its wooded island, the little whitewashed chapel and its packhorse bridge by the shore, the shepherd's stone hut, a drystone wall, a force (waterfall) and a beck, Herdwick sheep with Sooty the black lamb, Daisy the one cow, Meg the sheepdog; the painter's chamois and ibex are a wild goat and a Herdwick ram here, its marmot a rabbit; Old Wilf (a guest's first time on the fells, the ravens off the crag), Shepherd Jack (Daisy the cow's bell, the fells pink at sunset), Harry (fishing in the morning mist, the chapel bell, a fish jumping) |

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
`months` (and one of its dialogs', [Seasons](#seasons-in-their-months)), `when`, `weekdays` and `needs` must fit (`dawn` is no part of the day
of its own but the morning's first hours round the sunrise of the month, `TimeOfDay.dawn`: 5–6 in June, 6–8 in December,
so a happening `when` the morning is on then too, one `when` dawn only then) (the age, and what the
village has built: Luka brings the hay in only where a kozolec stands), it must not be done today, and the day's dice
(`roll(seed, date, key)`) must come in under `chance`. At most four at once and one per person. Done happenings are
kept in `GameState.happeningsDone` (`scene/happening` → date). A place where someone sleeps (a painter with a bed, see [Asleep at night](#asleep-at-night)) is quiet at night: a
happening there without a `when` (all day) doesn't come up from 22 to 5; the ones that name `night` (Zala's story and
Babica's blanket in the tent) and the evening's are as they were. The storyteller's evening story is on until 23:30, then he
goes home to bed ([Stories](#stories-the-evening-story)).

### Seasons: in their months

The pictures follow the seasons (snow in winter, the vines bare, the bear and the dormouse asleep, the garden's
vegetables), and so does what people talk about: a happening or a dialog that only makes sense in part of the year has
its `months`, 1 (January) … 12 (December), the same numbers as the wild animals' months ([Wild animals](#wild-animals)).
Without them it is all year, as before.

```json
{ "id": "seno", "who": "luka", "when": ["afternoon", "evening"], "months": [6, 7, 8, 9], "dialogs": ["seno-grom", "seno"], "…": "…" }
{ "id": "konj-hlev", "months": [12, 1, 2], "lines": ["…"] }
```

- **A happening** is on only in its months (`Happenings.on`, by the phone's date, as the festivals and the art go):
  Luka brings in the hay from June to September, France mows in the dew from May to September.
- **A dialog** (a variant) plays only in its months (`Happenings.variant`): a happening that is there all year may have
  one variant about the snow for the winter and the others for the rest of the year. A dialog's months lie within its
  happening's (the bridge refuses one that never would play). A happening with no dialog in season today rests, as one
  whose variants are all heard lately does.
- **Out of season** a happening isn't offered: no bubble, no marker over its person in the scene, not in "Danes tukaj ·
  Here today" nor under later today, no clips fetched for it. A dialog the learner is in the middle of goes on: what plays
  is the talk begun (`SceneController.talk`), never asked about the date again.
- **A scene is never empty**: in every month at least one of its happenings is on, if it has any (those only a guest meets
  aside): the bridge refuses a scene that would stand empty in some month (`emptyMonths`, "months: in September–May none
  of the scene's happenings is on").
- **Generous windows**, round the real year in Primorska, and in the Alps a little longer for the cold: the hay June to
  September (the second mowing), the wheat harvest July and August, mushrooms August to November, snow and frost December
  to February (in the mountains November to March), the bear and the bees out March to October or November, the frog's
  spawn and tadpoles March to September. Where the season is only a passing word and the line reads fine all year, a
  rewritten line is better than a season.
- **The tutor** sees them in `list_scenes` ("months": "June–September", and `out_of_season` while it is; the dialogs' own
  in `dialog_months`), the guide asks that a variant fit its happening's months, and a dialog it publishes may have its
  own (`publish_dialog_variant`).

An older bridge strips `months` (its scenes play all year, as before); an older app ignores them.

What has a season (`ScenesContentTest`, "the seasons"; every other happening and dialog is all year, and every scene has
something on in every month):

| Scene | Happenings (their months) | Dialogs of their own (their months) |
|---|---|---|
| `na-njivi` | the wheat harvest 6–8, the potatoes dug 7–10, the hay 6–9, mowing in the dew 5–9 | |
| `v-gozdu` | mushrooms 7–11, blueberries 6–8, the bear 3–11 | the hungry bear of autumn 9–11, the fawn a month old 5–8, the bats at dusk 4–10 (`mrak`, `mrak-stejem`, `sova`) |
| `pri-cebelnjaku` | the hives opened 3–10, the swarm 4–7 | the honey taken 5–8, the bee sting 3–10 |
| `pri-ribniku` | the frog 3–9 | the tadpoles 3–6, Nejc's swim 5–9 |
| `ob-ognju` | Luka's cold hands 9–5 | |
| `v-hisi` | the stove fired up 10–4 | |
| `v-kleti` | the new wine 11–4 | |
| `na-stolpu` | the storm 4–10 | the hay out in it 6–9 |
| `na-trznici` | | "come to the trgatev" 5–10 |
| `na-vasi`, `v-soli`, `v-podstresju` | school (the dual at the well, the blackboard, the dual, the homework) 9–6 | "let's wait for winter" 3–11; the summer's holiday asked of 1–6; Nejc to school 9–6 (the pond after it 3–6, 9) |
| `kuhinja`, `ob-potoku`, `v-sotoru` | | eight degrees on the radio 10–4; the cold dawn at the mill 9–5; the blanket for the cold 9–5, the thunder 4–10 |
| `na-gricih`, `v-gorah` | the cows on the planina, the milking 6–9 | the grapes offered 8–10, the starlings and "come to the trgatev" 7–9, the storm and the hail 4–10 |
| `am-see`, `il-mare` | the Alm, the milking 6–9; a swim in the sea 5–9 | |
| `al-fuoco`, `am-feuer` | | "are you cold?" by the fire 9–5 |
| `nella-tenda`, `nel-campo`, `nel-bosco`, `allo-stagno` (friuli) | the wheat harvest 6–8, the mushrooms 8–11, the frog 3–9 | the thunderstorm in the tent 4–10, the scarecrow's scarf 10–3, Bianca in the fog 10–3, the tadpoles 3–6 |
| `v-mlinu`, `nel-mulino` (friuli) | the harvest weeks at the mill 7–9 | the wheel iced at the morning start 12–2 |
| `a-scuola` (friuli) | school (the roll call, the blackboard, the map of Friuli on school days; Lorenzo's homework) 9–6 | the merenda at break 9–6 |
| `sulla-torre` (friuli) | Franco's storm 4–10 | the fog in the valley 10–3, the hay before the rain 6–9 |
| `in-salotto`, `in-cantina`, `in-soffitta` (friuli) | the fogolâr lit 10–4, the vendemmia 9–10, the bora at night 10–4 | the chestnuts 10–11, Tommaso to school 9–6 |
| `al-mercato`, `in-piazza` (friuli) | the sagra on weekends 5–11 (the square's 6–8), Maestra Elena at the well on school days 9–6 | the cherries 6, the white asparagus 4–5, the vendemmia 9–10, the chestnuts 10–11, the old snow 3–11 |
| `dalle-api`, `in-chiesa` (friuli) | the hives opened 3–10, the swarm 4–7, the acacia in flower 5; the nativity 12–1 | World Bee Day 5, the extractor 5–8, chestnut honey 9–11, the sting 4–9, the bees in winter 11–2; the bells flying to Rome 3–4, the Christmas music 12 |
| `pod-mlajem`, `pri-vodnjaku` | | the maypole raised on the eve of May Day 4; Zala's splashing 6–9, the fountain cold 12–2 |
| `na-mostu`, `pri-toplarju` | the dragonflies 5–9; the hay 6–9, the storm 5–9, the cart and the tools under the roof 10–4 | the bats at dusk 4–10; the cows in the stable 11–3, the hare by the fence 4–10 |
| `na-cebeljem-travniku` | the meadow in flower 4–9, the first honey 6–8, the swarm 4–7, the hives mended 11–3 | the phacelia 6–8, the linden tea of winter 11–3 |
| `na-baliniscu`, `na-igriscu` | the evening game under the lantern 5–9; the sand castle 4–10, the kite 9–11, hopscotch in the school break 9–6, the soap bubbles 4–9 | |
| `pri-kapelici`, `v-gasilskem-domu`, `pri-vratih` | the fresh flowers 3–11, the linden in flower 6–7, the storm 5–9; the drill 4–10, St Florian's day 5; the flock out and home 3–11 | |
| `nella-vigna`, `la-fontana`, `il-ponte`, `il-grande-fienile`, `il-prato-delle-api`, `l-orologio` (friuli) | the grape harvest 9–10, the starlings 8–9, the persimmons 10–11, the summer storm 5–9; Giulia cooling off at the fountain 6–9, her article game 10–5; the firefighters' drill 5–9, the trout 4–10, Aldo's evening 4–10; the hay 6–9, the sheep's winter hay 11–3, the bats 4–10; the meadow's flowers 3–10, Aldo and the bees on the flowers 4–9, the lesson in the meadow 4–6 and 9–10, Lorenzo in the meadow 4–8; school mornings 9–6, the clock lit 11–1 | pruning 12–2, the new leaves 3–5; the spouts frozen 12–2; snow on the bridge 12–2; snow on the big barn's roof 12–2; the dandelions and daisies 3–5, the clover and the phacelia 6–8, the asters 9–10, the dandelion clock 4–5, the swarm 5–6, the four-leaf clover 6–8, the meadow under snow 12–2 |

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
**Its card from inside** (`SceneScreen`'s top bar): beside "‹ Vas · Village" the building the scene is seen in, its emoji
and level ("🛖 3"), with ⬆️ in gold when its next level can be built now and ⚠️ while it is damaged (TalkBack: "Koča ·
Hut, stopnja 3: kartica in nadgradnja", and that it can be upgraded or is damaged). A tap opens the village's card for it
(`BuildingCard`, GAME.md "The building's card"), over the scene: the next level with its one button (its words, the
price, "⬆️ Nadgradi · Upgrade"), the words and the moba folded, the repair when damaged; not going in, moving, replacing
or growing up (they would change the scene the learner stands in). Upgraded there, the learner stays: the card
closes and the room redraws at its new level, with confetti and a banner ("⬆️ 🛖 Koča · Hut · Stopnja 4"), its new things
to find; back in the village the building goes up as after an upgrade there. "🎯 Vadi jih zdaj · Practise them now" runs
the words and comes back into the scene with the card open again, the upgrade one tap away, as the village's card comes
back in the village (the card is opened again where it was opened, and nowhere else: `AppViewModel.leaveRun`); "⬆️
Nadgradi zdaj · Upgrade now" at the run's end goes to the village, where the building goes up. The next age's row opens
the village's ages (`AppViewModel.openVillageAges`). The card is the village's own (`BuildingCard`, shared by both
screens). The top bar's pills lie side by side, 10 dp apart, each with a faint light rim (`OverlayPill`), none ever
under another (`TopBarRow`; 6 dp apart and rimless, the nearly round "⛪ 1" read as tucked under "‹ Vas · Village" over
the church's dark organ loft, and a long base on a narrow phone squeezed "🔍 found/total" out): "🔍 found/total"
keeps its place on the right, the ones on the left share what it leaves, and the way back gives way first, where both
halves of "‹ Vas · Village" don't fit (a narrow phone, a long base: "‹ Vas · Villaggio", a big font) saying the
village's word alone, "‹ Vas" (TalkBack still both; `SceneTopBarTest`: every pair of languages, 320 to 411 dp, a bigger
font). Over the picture, the markers and bubbles keep off the top bar's pills (`BubblePlace`: aside, else below), so
someone's 🍲💬 never covers the 🛖. A scene of no building (the fire, a spot of the land) has none. QA's `scene-building`
opens the first building's scene the village has and its card from there, practises its words and comes back to it.

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
| `project` | a village project of the culture (`projects.json`) with all its steps done, or at least `step` of them; all of them means its landmark standing: not while it is down for the season (the maypole after May, GAME.md "Village projects") |
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
| `kitchen` | by the level of the hut or house it is seen in: at level 1 (`"before": true` from 2) the open `hearth` in the corner (the pot on its trivet over the fire, the corner black with soot), the straw `bed` along the left wall; up to 2 the `shelf` with the jugs; from 2 the tiled `stove` and its pipe, the `table` laid (`bread`, `plate`, `bowl`, `glass`, `cup`, `spoon`, `fork`, `knife`, `salt`) with its `chair`s, the `curtains`; from 3 the `clock`, the painted dresser (`cupboard`, kredenca) and the holy `corner` (bohkov kot: the crucifix over a little shelf with a candle, a holy picture beside it); in Friuli ([Culture looks](#culture-looks)) the `hearth` is the fogolâr, there at every level, with the spolert (the `stove`) beside it from 2 and the radio's corner from 3 | with the level, not a word: a beaten earth floor and plain whitewash at 1; the tiles, the painted dado, the bench's cushion, the lamp over the table, a geranium on the sill from 2; the stencilled border (Friuli's frieze of vines) and the rag rug from 4; the pots of geraniums outside at 5 |
| `livingroom` | by its house's level: at 1 the plastered `stove` (kmečka peč) with its `bench` (the zapeček, Muri the `dog` curled up on it), a bare `table`, a plain `chest`, the `window` and the `door`; from 2 the `chair`, the `curtains`, the rag `rug`, the `lamp` over the table, the `clock`; from 3 the holy `corner`, the wedding `photo`, Janez's `book` and his `glasses` on the table | a beaten earth floor and plain lime at 1; the stove green-tiled with a rail of drying socks, a plank floor, the painted dado, a cloth on the table, the chest painted from 2; the stencilled border and the bench by the wall outside from 4; the pots of geraniums at 5. In Friuli the fogolâr for the stove (its corner black with smoke at 1, its hood with the shelf of copper pans from 2), terracotta tiles and the walnut cassapanca from 2 |
| `workshop` | by its house's level: at 1 the `workbench` with the `hammer` and a tin of `nails`, the `boards` against the wall, the `axe` in its chopping block, the `broom`, the `window` and the `door`; from 2 the `saw` over the bench, the tool board (`tools`), the `ladder` and the `rake`; from 3 the `wheelbarrow`, the wooden `horse` France carves for Tine and the storm `lantern` | a beaten earth floor at 1; planks strewn with shavings from 2; the bench by the wall outside from 4; the pots of geraniums at 5 |
| `cellar` | by its house's level: at 1 one barrel (`barrel`), the `press`, the `stairs`, the `door` and the barred `window`, the `candle`, the `jug` under the tap, the `glass` and an empty `crate`; from 2 the `shelf` of bottles (`bottle`), the `grapes` in the crate, the demijohn of `wine`; from 3 the pršut hanging on the wall (`ham`) and a wheel of `cheese` on the table | stone flags and a second barrel from 2, a third barrel from 3; the bench by the wall outside from 4; the pots of geraniums at 5. In Friuli the frasca at the house's corner from 2 and the salami beside the ham from 3 |
| `attic` | by its house's level: at 1 Nejc's `bed` with its `pillow` and patchwork `blanket`, his old `cradle`, a plain `chest`, the `roof`, the gable `window` and the `stairs` coming up through the floor; from 2 the `wardrobe`, the `rug`, the `lamp` from the ridge beam, Nejc's `ball`; from 3 the `mirror` and a teddy bear on the chest (`toy`) | the red tiles showing between the battens, rough boards and rough plaster at 1; the roof lined with boards (Friuli's tavelle), whitewash from 2; the chest painted from 3; the bench by the wall outside from 4; the pots of geraniums at 5 |
| `smithy` | by its level: the `bellows` and the `chain` from 2; the `wheel` and the `axe` from 3 | a second, smaller anvil on its stump in front of the wheel from 3 |
| `school` | by its level: the `map` and the `globe` from 2; the `clock` from 3 | |
| `watchtower` | nothing: every thing at every level | by the tower's level: its platform 17, 20.5, 24, 27 and 30 m over the village's ground (the land below drawn from that eye: the higher, the more of the valley and the further the village lies below), so on a low tower the village's gardens, a haystack, a woodpile, a cart, hens, a fence and a washing line right below it show, which the platform hides from the top; its build: round logs in their bark under a thatch, a rough pole lashed to stakes for a rail, slabs for a floor and a pennant at 1; hewn posts, the roof's boards and a plank rail at 2; the knee braces and the middle rail at 3; the flag at 4; the bucket, the rope, the spare torches and the stool on the platform (a taller view's) one a level from 2. In Friuli's look the tavelle under the roof from 2, pillars of cobbles and brick with their arches from 3, the iron railing from 4 ([Culture looks](#culture-looks)) |
| `field` | `kozolec` and `hay`: the hayrack and the hay on it (KOZOLEC); without it the rake leans on the fence | the double hayrack beyond the wheat (`toplar`): oak logs, stone feet, eight posts, the poles, the plank roof, the first hay |
| `square` | `well` (WELL); `tower`: the church with its bell tower (CHURCH) | the maypole before the church (`mlaj`): a peg with a ribbon, the spruce on trestles, peeled (its bark in strips on the cobbles under it), the wreath and ribbons ready, raised; taken down for the season (after May, until the village raises it again on the eve of May Day: GAME.md "Village projects") nothing of it, not even the peg; the tower's clock (`mestna_ura`): a slit window, the round window made for it, the face lifted in (stopped at twelve), then going and lit at night; the fountain in the middle of the square (`vodnjak_na_trgu`): pegs and a string where it will stand, the block of white stone on its beams, carved into a round trough with a pillar in the middle (stone chips round it), finished with the ditch dug from the spring through the garden across the square, the four iron pipes fitted to the pillar with the ditch covered over (in place as its last step begins), then the water running from them into the trough |
| `stream` | `mill`: France's mill, once it grinds (`mlin`) | the mill as it's built (`mlin`): pegs and a string, the stone footing with the timber waiting, the walls under bare rafters, the roof, the wheel and the flume still and dry, then the water on the wheel and the window lit; the cart bridge upstream (`most`): pegs and a line over the water, heaps of stones, the two stone piers with the cart road's ramps heaped up to them off both banks (dry-stone walls holding their earth), the logs with the deck's ten planks stacked on the bank by the ramp, the plank deck with the ramps up to it and the cart road coming up them from the village and going on into the woods (the first cart can cross), then the railings along both sides, the near one low so the deck and its open ends show over it |
| `palisade` | by the palisade's level, as the map's: from 2 the `torch` on each of the gate's posts; from 3 the gate's little shingle `roof` and the pennant (`flag`) on its ridge, blue (red at 4, gold at 5, as the map's pennants); from 4 the walkway along the inside of the stakes and its `ladder`; at 5 the `bell` under the gate's roof | with the level, not a word: the wings poles lashed with withies at 1, planks from 2, iron bands from 4; the stakes' points charred from 3; a footing of dry stones along their feet and the lintel carved with a golden sun at 5 |

The plank footbridge (`bridge`, brv) is always there: a simple crossing, the cart bridge comes in addition (the map
has one bridge, the same project's, where the road crosses the stream, and stepping stones there before it:
[GAME.md](GAME.md), "The crossing"; this stretch of the stream keeps its footbridge and its stepping stones). It is a
footbridge to walk over (Jan, 2026-10-18: "the bridge seems not to be drivable ;)"): planks laid across two logs that rest
on the banks, low over the water and lit along their front edge, the water in its shade under it, one low handrail along
its far side; the track along the right bank and the path along the left (past Micka's washing stone) come onto its ends
(`StreamPlan`, the stream's plan the painter draws by and `BridgesTest` checks: every way meets its bridge's open end,
every deck spans the water from bank to bank; build/village-snapshots/64-stream-bridges-…). Happenings
that need a fixture: the hay before the rain (`na-njivi/seno`, KOZOLEC), Mojca's dual at the well (`na-vasi/dvojina`,
WELL, from Vas), Stari Janez on the old linden (`na-vasi/klop`, LIPA at its fifth level) and by the young one before it
(`na-vasi/lipa-mlada`, `before`), France milling (`ob-potoku/mlin`, the finished mill) and starting it at dawn (`ob-potoku/zora`), and before it France
at the stream talking of the mill he wants (`ob-potoku/potok`, `before`: from the first morning until the mill grinds) and
watching the water in the dawn mist (`ob-potoku/megla`). The forest, the tent, the pond, the church, the bee house, the
market, the view from the watchtower and the culture packs' landscapes (the mountains at the horizon, the sea at Grado:
another place, not the village) have nothing that waits for the village; the kitchen, the rooms of the houses, the smithy
and the school grow with their building's level (above), and so does every building's scene, up to its fifth level (below). The watchtower's view does follow what is going on: `SceneWorld.wolves` is on while the village's wolves event
is, and then the wolves' eyes shine in the forest below at night (off by default, so tools and tests see the plain view).
The vineyard waits whole: it opens only once its terraces
are finished (`from: ["project:vinograd"]`, see [Scenes of a culture pack](#scenes-of-a-culture-pack)), and so does the
mill inside, once the mill grinds (`v-mlinu`, `nel-mulino`: `from: ["project:mlin"]`, see [The mill
inside](#the-mill-inside-from-the-stream-and-from-the-map)). Every other project opens a place of its own once finished,
as the mill does ([The village's places](#the-villages-places-every-project-a-place-to-go-in)): the maypole, the
fountain and the town clock on the square, the cart bridge by the stream, the double hayrack in the field, the bee
meadow by the apiary, and the new arts of the bocce court, the wayside chapel, the fire station, the playground and the
lookout tower. In the Friuli village the greasy pole, the fountain and the tower clock lead into the square, the bridge
into the stream, the big hay barn into the field and the bee meadow into the bee house, each once it is finished
([Scenes of a culture pack](#scenes-of-a-culture-pack)); its bocce court, chapel, fire station, playground, lookout
tower and palisade gate have their pictures in Friuli's look but no scenes yet. Their steps are played in the scenes all
the same, where they happen ([Project steps](#project-steps-a-village-projects-step-as-a-short-scene)).

Happenings that need a level: Micka's potica and the Sunday lunch want the kitchen's stove and table (`{"level": 2}`),
Nejc at the bellows the smithy's second level, Marko's broken wheel its third, Zala at the globe the school's second; in
the houses' rooms, France sawing boards the workshop's saw (2) and Tine's wooden horse its third, Janez's glasses and
wedding photo the living room's third, Marko's pršut the cellar's third, Nejc awake in the burja with the lamp on the
attic's second.

**Every building at its level** (Jan, 2026-10-30, Teo's watchtower at level 2: "build all scenes for all upgrades"): a
building's scene shows the building at each of its five levels, as an upgrade would have it, every culture's picture
alike. A painter reads its building's level once a frame (`SceneWorld.level(type)`, a room of a house `hereLevel`), and
its fifth level is the picture of everything built (`SceneWorld.ALL`), so the snapshots, the culture looks' digests and the
stickers stay as they were; each level below draws visibly less. The words keep their levels (`SceneFixtures`): what a
level adds beyond them is the building itself and its scenery, never a word taken from a lower level.

| Building (art) | What its levels add |
|---|---|
| watchtower (`watchtower`) | the platform higher each level (17 to 30 m), the village and the land seen from higher; round logs under thatch, then hewn posts, braces, the flag (the table above) |
| hut, house (`kitchen`) and the house's rooms (`livingroom`, `workshop`, `cellar`, `attic`) | their things at 2 and 3 (the table above); the stencilled border (and the kitchen's rag rug) and the bench by the wall outside at 4; the pots of geraniums along the wall at 5 |
| smithy (`smithy`) | the coals glowing without the bellows on grass at 1; the bellows and the chain at 2, the wheel, the axe and the second anvil at 3 (its words); the yard cobbled where the horses wait, the tethering post and the water trough at 4; the woodpile and the grindstone at 5 |
| school (`school`) | a bare classroom on its grass at 1 (the board, the desks, the bookcase, the teacher's desk and lamp); the map and the globe at 2; the clock with the flag on its pole by the corner (Friuli's tricolore), the lamp on its bracket and the scarf on its peg at 3; the linden and its bench in the yard at 4; the hopscotch, the flower bed (a sled and a snowman in winter) and the bicycle at 5 |
| market (`market`) | the village's stall a trestle table with a plain linen cloth on a lane of trodden earth at 1; the stall's posts, its back canvas, a plain awning and its lantern, two farther stalls at 2; gravel, two more stalls and the check on the cloth's hem at 3; the lane and the square cobbled (Friuli's river cobbles with brick bands), the last two stalls at 4; every awning striped in its colours and the back canvas dyed at 5 |
| linden (`square`) | the linden a sapling tied to its stake at 1, a young tree still staked at 2, its crown filling out at 3, nearly grown at 4, the old linden at 5 (the cat climbs it and comes down at every size) |
| well (`square`) | a curb of field stones with a forked pole, the rope and a bucket at 1; the drum of dressed stones at 2; the posts with the windlass at 3; a shingle roof at 4; its tiles and the lamp post at 5. In Friuli a well-head of white Istrian stone: under the pole at 2, the wrought-iron arch with the pulley and a copper bucket at 3, the carved band, the step and the scrolls at 4, geraniums on the step at 5 |
| bee garden (`apiary`) | one row of six hives under a roof of boards, two fronts painted, at 1; a second row on the stone footing, six fronts painted, at 2; the third row at full height, twelve painted, at 3; the shingle roof, every front painted, the smoker and the skep on their stumps at 4; the trodden path, the bees' water and the lantern at 5. In Friuli a lean-to of boards with three box hives at 1, the shed of cobbles under coppi with a tier of six at 2, a second tier begun at 3, both full at 4, the garden at 5 |
| church (`church`) | a bare chapel, one window of clear glass, at 1; the coloured glass (Friuli: the rulli) and a second window, the dado, the lamp, a third row of pews and the bench outside at 2; the starry vault (Friuli: the open roof's truss), the frieze, the tiles, the runner, the painted loft at 3; the retable round the altarpiece (Friuli: the aedicule of Istrian stone), the Virgin, the consecration crosses (Friuli: the frescoes) and the lavender at 4; the sunburst and the angels (Friuli: the pediment with its dove), the cypress, the gravel path at 5 |
| tent (`tent`) | a floor of trodden earth and straw, the table bare, at 1; the plank floor at 2; the rug and the shelf with its crock at 3; the guy ropes, the door flap rolled up, the valance and straps at 4; the worn path and its stepping stones at 5 |
| field (`field`) | the first plots by the track, a strip of wheat and two rows of maize, the near fence, at 1; the potatoes and cabbages in full, more wheat and maize, the middle fence at 2; the crops at their full depth at 3; the fruit trees (Friuli: the piantata's mulberries and the poplars) and the far fence at 4; the patchwork over the plain at 5 |
| hayrack (`field`, by KOZOLEC's level) | the kozolec of one bay under boards, half hung with hay, at 1; two bays at 2; three at 3; the shingle roof at 4; its rails full at 5. Friuli's fienile: one arch, then two, the bands of brick, the quoins and the lattices, the loft full |
| palisade (`palisade`, its gate: Pri vratih) | rough logs, the wings poles lashed with withies, at 1; the plank wings and a torch on each post at 2; the gate's shingle roof, its pennant and the stakes' points charred black at 3; the walkway along the inside with its ladder and iron bands on the wings at 4; a footing of dry stones along the stakes' feet, the bell under the gate's roof and the sun carved on the lintel at 5 |

`BuildingLevelsTest` checks it for every building that opens into a scene (the scenes' `from`, every culture's: a new art
joins as its scene does), in the default culture and each culture whose look draws the art: each level differs from the one
before in 300 pixels at least of the 240 × 160 canvas by day (a thing a child sees come with the upgrade), the fifth is the
picture of everything built, and what can be tapped is what the level has; its sheets are
`app/build/scene-snapshots/levels/<building>-<art>-{day,evening}.png` (a row a level, a column a culture).

`SceneWorldRenderTest` renders every art's stages side by side, nothing built on the left, everything on the right
(`app/build/scene-snapshots/world/00-before-after.png`, and each stage and closer look beside it; the buildings at their top
level), and checks that each stage looks different from the one before, that the last is the default picture, that the
arts without fixtures and without a building look the same whatever is built, and that a fixture that isn't there has no
hit. `RoomLevelsRenderTest` renders every room (the kitchen, the houses' rooms, the smithy, the school) at levels 1 to 5,
empty, side by side and one by one (`app/build/scene-snapshots/rooms/<art>-levels.png`, `<art>-L1.png` …), and checks that
the levels that bring its things differ and that what can be tapped is what the level has.

### The mill inside: from the stream and from the map

Jan, 2026-10-27, the village's mill just finished: "why can't I go into the mill? ;)". The mill (the village project `mlin`,
France's: its landmark on the map by the stream, and the mill the stream scene draws as it is built) has its inside now,
`v-mlinu` «V mlinu · In the mill» (art `mill`, [Arts](#arts)), and in the Friuli village `nel-mulino` «Nel mulino», Mugnaio
Franco's (the same art, [Scenes of a culture pack](#scenes-of-a-culture-pack)). Both open from the landmark,
`from: ["project:mlin"]`: once the mill grinds (all its steps done), never before.

- **From the map**: a tap on the mill by the stream opens its place card, as a landmark with a scene does (the vineyard's
  terraces): "🚪 Vstopi · Go in: 🌾 V mlinu · In the mill ›", and "🏗️ Skupni projekti · Village projects ›" under it. Before
  the mill grinds the tap opens the projects sheet at the mill, as it always did: there is no door to show.
- **From the stream** (`ob-potoku`, `al-ruscello`): the stream's mill is a word of the scene once it grinds (`mill`: mlin,
  mulino); tapped, its bubble has its word and under it "🚪 Vstopi · Go in: 🌾 V mlinu ›" (`game/scene/SceneDoors.kt`: a
  thing of a picture that is a place of the village, `stream/mill` → `project:mlin`, the scenes open there now and never the
  scene itself; `DoorEntries` in `ui/scene/SceneScreen.kt`). Before the mill grinds the stream draws it as far as it is
  built, scenery not to tap: no bubble, no door.
- **Back**: from the map's card "‹ Vas · Village", as from any scene; from the stream the mill lies on the stream in the
  back stack (`Screen.Scene.from`, Nav.kt's `destination`), so Back, the gesture and its "‹ Ob potoku · By the stream"
  return to the stream, and Back again to the village. Another scene opened from the mill takes both their places.

Taps into the mill: from the village 2 (the mill, "Vstopi"), from the stream 2 (the mill, "Vstopi"); before there was no way in.

**Inside** (`MillPainter`): the millstones in their vat on a timber platform by the right wall, the runner's grooved top
turning in the vat's opening, the hopper over them with its grain and the shoe shaking it into the runner's eye, the spout
running the flour into a sack on the floor; under the platform the cog wheel on the shaft, half in its pit, and the pinion on
the stones' spindle; the shaft out through the front-right wall to the overshot waterwheel in its race under the flume, all
of them turning together; the bench under the window with the scale and the miller's measure (merica), the sieve, the mouse
at its hole, the sacks under the loft's gallery and on it, the ladder, the lantern, the door, the cat. Its people are the
miller at the stones, his grandson by the flour sack and whoever comes with grain at the door: France, Tine, Micka with wheat
for her potica and Luka paying the miller's toll in corn (`merica`); in Friuli Franco, Lorenzo, Nonna Rosa with maize for her
polenta and Davide paying `la molenda`. Their happenings go through the day and the year: the morning start (the sluice
opened, the stones going; in the cold months the wheel iced), the grinding (how it works, a counting variant), the harvest
weeks (July to September), a sack too heavy and flour over everyone, the cat and the mouse (a tap turn: where is the
mouse?), the toll, the grain brought, the evening (the lantern lit, the stones stopped). Every effect of the art is used and
every action a line describes is cued.

### The village's places: every project a place to go in

Jan, 2026-10-30: "teo upgraded the tower - now he can't enter it > can you build all scenes for all upgrades including slo
culture?" Every village project of the Slovene village now opens a place of its own once it is finished (`from:
["project:<id>"]`, as the mill and the vineyard do), and so does the palisade (from the palisade, as every building with a
scene does): its landmark on the map, or the palisade, opens its card with "🚪 Vstopi · Go in" (docs/plans/05-built/places-sl.md).

| Scene | Opens from | Art | Level | Its people | What happens there |
|---|---|---|---|---|---|
| `pod-mlajem` Pod mlajem · Under the maypole | `project:mlaj` (while it stands: from the eve of May Day to the end of May) | `square` | A1 | Luka on the bench, Marko at the well, Nejc and Tine at play | the lads guard the maypole at night; Tine counts the ribbons; Nejc climbs for the prize at the top; Marko's band and the polka in the evening; Luka asleep under the maypole at dawn, woken by the bell; the raising on the eve of May Day (a variant) |
| `pri-vodnjaku` Pri vodnjaku · At the fountain | `project:vodnjak_na_trgu` | `square` | A1 | Tone, France and Zala at play, Mojca at the well | Tone and the four pipes (one blocked with leaves); Mojca's numbers and the dual by the water (weekdays); Sivko drinks; Zala splashes the cat up the linden; the evening's news; the fire brigade's Saturday drill with the cart pump (once the fire station stands) |
| `pod-uro` Pod uro · Under the clock | `project:mestna_ura` | `square` | A1 | Mojca and Ančka at play, Tone at the well, Janez on the bench | telling the time (ob dvanajstih, pol dveh, četrt na enajst); Ančka doesn't hear the clock strike; Tone winds it on Saturdays; Janez's slow pocket watch (a counting variant in minutes); the noon bell and jota; late for choir |
| `na-mostu` Na mostu · On the bridge | `project:most` | `stream` | A1 | France at the mill, Luka at the ford, Tone and Anton on the bank | France's cart over the bridge (Sivko afraid of the water); Luka's flock over it, counted, Bela last; the stream high after rain; Tone nails the railing; the dragonflies and the trout (May–September); dusk, the bell, the bats |
| `pri-toplarju` Pri toplarju · By the double hayrack | `project:toplar` | `field` | A2 | France in the field, Luka by the kozolec, Tone and Marko on the path | hanging the hay (June–September, counting haycocks); the hay in before the storm (lightning); the cart and the tools under its roof in winter; its oak posts on their stones; the scarecrow's new face; the evening, the hare by the fence |
| `na-cebeljem-travniku` Na čebeljem travniku · In the bee meadow | `project:cebelji_travnik` | `apiary` | A1 | Anton at the hives, Micka on the bench, Zala and Nejc at the table | buckwheat, clover and phacelia (April–September); the first honey, the jars counted; a swarm in the linden; the painted panels (the dual); Micka's linden tea; mending the hives in winter |
| `na-baliniscu` Na balinišču · At the bocce court | `project:balinisce` | `bocce` | A2 | Janez throwing, France by the jack, Marko and Anton at the table | Janez rakes the sand at dawn (proverbs); his game (comparatives, knocking a ball away, the dual); France measures with the rule (bližje, dlje), the chalk marks counted; Marko pours; Anton's evening game under the lantern (May–September) |
| `na-igriscu` Na igrišču · At the playground | `project:igrisce` | `playground` | A1 | Mojca on the bench, Zala on the swing, Nejc and Tine at the sandpit | Zala on the swing (higher!); Tine's sand castle; Nejc's lost ball (a tap turn: under the slide); the kite in the autumn wind; hopscotch in the school break (weekdays); soap bubbles in summer evenings |
| `pri-kapelici` Pri kapelici · At the wayside chapel | `project:kapelica` | `chapel` | A1 | Ančka at the chapel, Micka on the bench, Zala and Tone on the road | Ančka's fresh flowers; Zala rings noon and counts the houses; the candles lit and the Angelus at dusk; a traveller on the road; the linden in flower (June–July); a summer storm, the bell rung against the hail |
| `v-gasilskem-domu` V gasilskem domu · At the fire station | `project:gasilski_dom` | `firestation` | A2 | Luka at the pump, Tone at the hoses, France and Marko at the door | Luka's drill with the old pump (April–October); France's alarm: the bell, the horn, the doors open; Tone checks the hoses and counts the firefighters; Marko polishes the helmets for St Florian's day (May); the doors shut at night |
| `na-razglednem-stolpu` Na razglednem stolpu · On the lookout tower | `project:razgledni_stolp` | `lookout` | A2 | Vida at the board, Nejc and Luka at the railing, Janez at the stairs | Vida shows the view (Triglav, clear to the sea, fog in the valley); Nejc's stamp and the visitors' book (guests counted); Janez and the hundred steps; the buzzard through the telescope; the alpenglow and the village's bell below |
| `pri-vratih` Pri vratih · At the gate | `palisade` | `palisade` | A1 | Jože and Tone at the gate, Luka on the road, Nejc on the bench | Luka's flock out at dawn and home at dusk, counted at the gate (March–November); Jože bars the gate at night (the wolves; Nejc wants to go into the forest); the pedlar comes; Tone lights the torches and oils the hinge (level 2); Nejc and the gate's bell (level 5) |

Each has its pack (13–16 words, its own id), its I spy file (`ispy/scenes/<id>.json`, 50–84 clues), every slot of its
art as a word, every effect cued at least once in the new arts, a counting variant, faces, puzzled reactions and German
and Italian; every month has something on. The Slovene is machine-written: each file's `review` names the words a native
speaker should look at (bulin, ristanc, brizgalna, krošnjar, štirna for the draw well, četrt na enajst, toplar, kanja,
žig and the rest), and no spoken form is graded wrong. Two people at one spot are the same place's alternates (the
happening on says who is there). The palisade's things come with its level (`SceneFixtures`: the torches from 2, the
roof and the pennant from 3, the walkway's ladder from 4, the bell at 5; the scene's objects say the same with
`needs.level`), and so do the happenings that need them.

**The square at every age** (2026-10-30: "every building that stands must be enterable"). Na vasi opens from the linden
or the well, and it opens wherever one of them stands, whatever the age: from the well at Tabor, from the linden at Zaselek
(before, it needed Vas, so a hamlet with its linden had a square nobody could step into). What happens there fits what
stands: Stari Janez talks of the old linden, two hundred years old, "starejša je od cerkve", once LIPA is at its fifth
level (`klop`, the old picture), and before that of the young one the village planted, the bucket of water it needs, the
dances to come under it (`lipa-mlada`: `mlada-lipa`, and `mlada-lipa-voda` on the hot days of May to September), as the
square draws it, a sapling at its stake to a crown filling out; Mojca's dual at the well needs Vas as before (the school's
teacher, the church's bell); the children's football, the cat and Muri wherever their people are about. "Ob štiri" at
the end of the children's game is right now, as people say it.

**Its arts** (the table of [Arts](#arts) has their slots, spots and effects; the painters' class comments say all they draw):

- `firestation` (`FirestationPainter`, a diorama on `RoomPainter`): the fire station's hall cut open on its plot, whitewashed
  over an oxblood dado, a brick floor; the big red plank doors in the back wall with the brigade's sign over them (PGD), the
  old hand pump from Gorica before them (red, its brass cylinders and air chamber, its two beams on their uprights, four spoked
  wheels, the drawbar); along the left wall the pegs with the helmets (black leather, brass combs) over the dark blue jackets,
  two axes crossed, the signal horn, the picture of St Florian pouring his bucket on a burning house, the leather buckets on
  their shelf, the bench, the ladder on brackets over them, the brigade's banner in the corner; the hose wound on its reel, its
  end run across the floor to the pump; the window and the lantern by the doors. Outside at the plot's right corner the hose
  tower: its footing, a small door, a slit window, the belfry with the bell in its arches, a pyramid roof of shingles and its
  cross, the bell rope down its front, a rain barrel by it. By day the doors stand open on the village, at night shut.
- `bocce` (`BoccePainter`, a diorama): the court of fine sand across the plot between its low boards, raked in lines, the
  jack and the metal balls at its far end (smooth and grooved, the two sides'), two waiting at the near end, the folding rule
  on the back board, the rake at the near end; behind it the inn (whitewash, green shutters, red tiles, a chimney smoking
  morning and evening, a painted sign of grapes over the door, a barrel and crates by it), the pergola with its vine over the
  table, the jug and the glasses; the linden at the back on the left, the lantern on its post, the scoreboard (a slate, MI |
  VI and the tally marks) behind the court, the spectators' bench in front.
- `playground` (`PlaygroundPainter`, a diorama): the lawn by the school's side (its classroom window, the lamp at its corner),
  white picket fences along the back with the gate ajar, the chestnut and the teacher's bench under it, the swing on its
  A-frame of logs, the slide with its ladder, the little house beside it, the sandpit with its bucket and spade, the climbing
  frame, the hopscotch chalked on the flagstones (one to eight and the nebo), the red ball; conkers under the chestnut in
  autumn.
- `chapel` (`ChapelPainter`, a vista): the white chapel by the road among the fields, its shingle roof, the little bell in its
  turret and the cross on top, the round arch with its wrought-iron grille and, through it, the altar with its embroidered
  cloth, a candle and vases of flowers, the painting of the Virgin; on the step two red glass candles and a pot of flowers; the
  bench by the road under the linden; the road from our feet past the chapel toward the hills, the wheat on its left (green,
  gold, stubble, furrows, snow), the meadow and the fields' patchwork beyond, the village with its spire far off; swallows
  over the wheat in summer.
- `palisade` (`PalisadePainter`, a vista; it grows with the palisade's level, [What the village has built](#what-the-village-has-built)):
  the palisade's stakes across the view, bound with withies, the gate between its posts under the lintel, the road out over the
  meadow into the forest, the hills beyond; inside the bench, the water trough of a hollowed log with its spout, a new stake
  leaning ready; by level the plank wings and the torches, the gate's roof and pennant and the stakes' charred points, the
  walkway and its ladder, the stone footing, the bell and the carved sun.
- `lookout` (`LookoutPainter`, a vista): the top of the lookout tower, its plank floor, the railing of logs with the board of
  the mountains, the telescope on its post, the box with the visitors' book and the stamp, the bench, the stairs' hatch, the
  corner posts and their braces, the roof overhead, the flag; far below the village in its clearing, the woods, the valley's
  fields and the river, the hills, the Julian Alps with Triglav's three heads on the left, the sea at the horizon on the right,
  in the haze of the day.

The arts are the fourth version of the arts (`SceneArt.VERSION` 4, `SCENE_ARTS_VERSION`, `ADDED`): an app of version 3 doesn't
get their scenes (`forApp`). Each has its Friuli look from the start ([Culture looks](#culture-looks)); the Friuli scenes that
use them are a follow-up. `PlacesRenderTest` renders them for review (`build/scene-snapshots/places/`: each art by day, in the
evening, at night, the seasons, rain, fog and a storm, the Friuli look, every detail, each effect from 0 to 1, the pokes, the
palisade at every level, a contact sheet of each) and checks that every effect changes the picture, that Friuli's look changes
each place and keeps every thing to tap, and that the palisade's gate differs at every level, its top level the default.

### Ob ognju by the age

The village's centre changes with its ages (GAME.md "Hearths"), and the scene `ob-ognju` (art `campfire`, `from` the fire)
grows with it (Jan, 2026-10-27: "the fireplace scene — I think it should change over time"). Everything that happens "by
the fire" keeps its place there (the place key `fire`: the evening by the fire, Stari Janez's stories, the class before the
linden, the people's reactions, an age's party, «Vidim, vidim», the treasure's riddles), and so do the scene's things, its
words and its stage: the fire, the stones, the kettle, the woodpile (`logs`), the axe in its block, the bucket, the stump,
the smoke, the sparks, the moon and the stars at every age, the tent while one stands by the fire; the stage's spots
(`left`, `right`, `back`, `behind-stump`, `behind-logs`) where they always were (`FireAgesTest` checks each at every age,
with and without the tent, in every culture: someone there is drawn, person-sized, nothing over their head).

- **Its name** (`Hearths.sceneTitle`, wherever a scene is named: its top bar, a card's 🚪, the scroll's places): its own,
  "Ob ognju · At the campfire", to Zaselek; "Ob ognjišču · At the fire ring" at Vas; "Na trgu ob ognju · By the fire on the
  square" from Trg. Every culture's campfire scene alike (Al fuoco, Am Feuer, By the fire), in the learner's pair.
- **Its picture** (`CampfirePainter` over `FireVillagePainter`, game/render/scene/FireVillage.kt), from what the village
  has (`SceneWorld`: its age, its buildings, its huts and houses by their plots, the nearest the centre first, `homes`; the
  day the linden was planted, `lindenDay`; its projects; today's `Centre`: a feast, the age's party and decorations, the
  centre's fire out for want of wood, the homes whose hearths are cold, the unnamed people of a bigger village):
  - **The camp** (Ogenj, Tabor): the campfire in its ring of field stones in a clearing among the spruces, the tripod and
    the kettle, two logs to sit on, the woodpile, the stump, the bucket, the axe in its chopping block, the camp's tent.
  - **The hamlet** (Zaselek): the ground trodden bare round the fire and out to the huts' doors, the logs split into log
    benches on blocks, the first huts (and a house) on the lots round the back where the spruces made way, the young
    linden where it is planted, the church once built; the rest of the woods round them.
  - **The village** (Vas): the fire ring of big flat stones, wider, and plank benches; packed gravel with grass along the
    edges; the linden over the ring in the square's back corner; the well at the square's front right (a curb of stones
    under a wooden frame, the winch with its crank, the bucket on the rim); the house fronts round the place, their gables
    to the square (a door, windows with their
    shutters, from the second level a balcony with geraniums in summer, a chimney smoking while its hearth burns); the
    church along the back with its bell tower, the door at the tower's foot, the bells in the belfry; a lantern on its
    post at the square's right; a low dry-stone wall behind the woodpile.
  - **The market town** (Trg): cobbles over the square (earth round the linden's foot), the fire basket on its three legs
    inside a kerb of dressed stones (its fire in the basket's bowl, the kettle hung higher), the well of stone (a well-head
    of dressed blocks under an iron arch with its pulley), or in its place the fountain as far as its steps have come
    (`vodnjak_na_trgu`: pegs and a string, the block, the trough with its pillar, its pipes, its water), the market's
    stalls with striped awnings before the houses, a second lantern by the church's tower.
  - **The town** (Mesto): paving of cut stone in rows with a ring of wedges round the fire basket, lanterns along the
    square's back and right, the clock in the church's tower as far as its steps have come (`mestna_ura`: the slit window, the round window
    made for it, the face lifted in, going and lit at night), benches on iron legs.

  The homes are the village's own, the nearest its centre first, on the lots round the square's back (two along the
  right-back edge, one at the left corner; the church's lot holds two more without a church; the two on the right are the
  tent's while it stands by the fire): a hut or a house as built, each a little different (its walls' colour, its level),
  its windows lit in the evening unless its hearth is out. The linden grows with the days since it was planted
  (`FireLayout.growth`: a young tree the first year, full-grown after a few), in the season's leaves, blossom in June and
  July, bare in winter. Nothing of the camp's woods is left from the village on, nor of its ferns.
- **In its culture's way** (`FireStyle`): Primorska's white and ochre plaster, green shutters, red tiles and wooden
  balconies, a spire on the tower; Friuli's ochre, terracotta and rosy plaster under coppi, the campanile in brick with its
  low cap, on Teo's coast (land `coast`) sand in the square and the lagoon's thatched casoni for the huts, blue shutters;
  Carinthia's white walls with a timbered upper storey and balconies under dark shingles, an onion dome on the tower; the
  Lake District's grey stone cottages under slate, white window frames, a square tower with its battlements, and an oak
  for the linden (its "Oak tree").
- **Over the day and the year** (the fire before a dialog's cues, `Burn`): the campfire (to the hamlet) burns all day and
  never quite goes out: low in the morning (embers, ash, a thin smoke), let die down to its embers in a summer's noon
  unless someone sits by it, built up for the evening, burning down in the night. From the village it burns for the
  evening's gathering (17:30 to 23:30), the lunch by the fire and whenever someone sits by it, higher for the age's party
  and a feast; else its coals glow under a thread of smoke, and in a summer's noon it is out; out too (grey ash, no glow,
  no smoke, the kettle cold) when the village had no wood for it today (`Hearths`), until a dialog's `flare` lights it.
  On a feast evening (a festival's day, the village's feast, an age's party) the market town burns its kres: a tall cone
  of logs ablaze in the basket's place, the kettle set aside on a flat stone. A dialog's `flare` and `embers` work on top
  of it as before. Snow on the roofs, the benches and the stones in winter, the square swept round the fire; the rain,
  the fog and the drifting leaves, snow and blossom as everywhere outdoors.
- **The feasts**, as the map has them (GAME.md "Ages and their celebrations", `AgeUps.decorOn`): the first camp's torches
  round the clearing, the likof's little spruce with its ribbons on the newest roof round the square, garlands of flags
  round the square (from the linden to the church's tower and the houses and the lantern) with little lanterns in them
  after dusk, the town's banners on their poles; garlands on every feast day from the village on.
- **Who is there** (VILLAGERS.md "Round the fire", `FireCrowd`, `Whereabouts.inScene`): the scene's own people at the
  stage's spots as before, and the village's others whom their day has at the fire now at the crowd's spots round it, the
  cast first, each as themselves (a tap on them is their card): two at the camp, three at the hamlet, five at the village,
  six at the market town and the town (four while the tent stands by the square's fire: the spots before the homes on the
  right are its); on the benches beside the stage's sitters, then standing beside the fire, never in front of it nor just
  behind it; those sharing a bench or a place drawn back to front. On an evening by the fire the village's unnamed people take the spots nobody took, as the map
  has them (a few in the dark evening, fewer after nine, all who are up at a feast or the age's party), as figures not to
  tap. The crowd's spots are no part of the stage: a dialog's `act` can't send anyone there, and a tap turn's place is
  never one (`DioramaPainter.tapSpot`). While the village reacts (wolves, a bear) those gathered at the fire fill them too.
- **What a dialog does there** plays at every age: the fire's `flare` and `embers`, the kettle's `steam`, the far `bell`
  and `sheepbells`, and Luka's `flock` (roke-ovce counts 16 to 24 sheep at every age). To the hamlet they come out of the
  trees at the back on the right, past the tent and the chopping block; from the village on a house stands in that corner
  and the right bench reaches the plot's edge, so they come up the path from the meadow on the left, along the front edge,
  and go back down it, the farthest along it first; the places behind the bench and those the well (the fountain) would
  hide move to the open ground before the bench, beside the stump and in front of the well, so all of the count stand in
  view. Each sheep is drawn among
  what stands by its depth (behind the well before it, the rest in front).
- **Its frame**: what stands still (the room round the diorama, the Milky Way, the ground, the woods and the houses at the
  back, the linden, the stalls) is drawn once and kept while the world, the season and the light stay the same
  (`ArtPainter.still`); the sky is drawn behind it (`PixelCanvas.behind`), its moon kept above the roofs, the church's
  tower and the linden while the sky has room (`FireVillagePainter.skyline`; on a short canvas along its way to the nearest
  stretch of sky that has), and the rest each frame
  ([docs/plans/05-built/fire-ages.md](../docs/plans/05-built/fire-ages.md): what it costs).

`FireAgesTest` renders every age of every culture by day, in the evening with the village round its fire, on a winter's
night and an autumn morning (`app/build/scene-snapshots/fire-ages/00-<culture>-ages.png`, each age and view on its own,
the tablet's canvas, every age at every detail, the feasts and the fire by the hour), and checks each age differs from the
one before, every thing is there to tap at every age, the stage's and the crowd's spots, the fire left to tap with the
village round it, the kept still picture against a frame drawn whole, the moon in the sky while it is up on every canvas,
the fire by the hour, every culture's own village, the linden's growth, Luka's flock at every age (each of 24 in view where
it stands, none before it comes nor after it has gone; `00-flock-ages.png` for review), and a frame's time. `FireCrowdTest` checks who of
the village stands round the fire on its evenings. QA's `fire-ages` shows each age on the emulator (README "QA").

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
the leader first and big, the dialog below; a tap turn is chosen there. Of its cues only a `bell` plays there: the line that
rings it ("Cin, cin, cin!" as the chapel's little bell is hung) rings a little bell over the village, high and quick
(`Sfx.VILLAGE`), and a bell swings in the backdrop's top corner, its rings going out, while its peal lasts.

**The step's work in its picture** (Jan, 2026-10-27, at the mill's last step: "'… the water is running, look the wheel is
turning' — nothing is turning"): a step played in a scene whose painter draws its project (`SceneArt.projects`: the mill
and the cart bridge by the stream, the double hayrack beyond the field, the town clock, the maypole and the fountain on the
square, the maypole's spruce in the forest, felled and carried off; the bridge's `DRAWN_PROJECTS`) shows the project as the step makes it, not as it stood before: up to the line that
says its work is done (`"done": true`) the scene has the steps done before it, from that line on one more
(`Projects.shown`, `SceneWorld.stepping`, `SceneScreen`'s world while the step's talk is on): the walls go up as Tone says
"Prva stena stoji!", the round window shows with Ančka's "Glejte, zvonik ima oko!", the peeled trunk white with its bark on
the cobbles at Luka's "Glej, kaj pade na tla?". A step without one shows its work once played (its end card), the five
whose last lines don't say it's done (the first cart over the bridge, the first of the hayrack's eight posts, the clock
striking noon at twelve, the trough still being carved, the fountain's water) by design. A step's effects play on the
picture of its stage: the mill's wheel, in place from the step before (`stage ≥ stages − 1`), turns under its `wheel` cue
as Tine says the water runs, before the mill is finished. The bridge checks a `done` (someone's line, once a level, only in
a scene that draws the project: `project-steps.ts`); `SceneCoherenceTest` that every step in such a scene says where its
work is done (or is one of the five), and `StepScenesRenderTest` that each done line's stage looks different from the one
before and that the wheel's paddles move under `wheel` 1 at stage 5 and stand still at 0 (`build/scene-snapshots/coherence`).

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
| `steps[].levels` | `"A1"`, `"A2"` (at least): `{"lines": […]}`, 4–7 of a scene dialog's lines, 1–3 turns, said by the leader (at least once) and the helpers; a reaction and a why to every wrong choice; a tap turn and `sky`/`fx` cues only with a scene (its art's places and effects, never a thing that comes and goes: an animal, a well or a mill not built yet), over the village (no scene) only `fx: {"bell": …}` (the bridge's `OVER_VILLAGE_FX`); no `act` (its people are who is here today) |
| `steps[].sky_stays`, `fx_stays` | as a scene dialog's |
| `steps[].levels.*.lines[].done` | `true` on someone's line, once a level: the step's work is done in the picture from this line on (see below) |

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

### An age's party: all the village over the village

When the village reaches an age, its celebration's party is that evening ([GAME.md](GAME.md#ages-and-their-celebrations):
Primorska's first evening by the fire, likof, veselica, semenj, the town's feast). It is played as a project's step
without its scene is, over the village (`ui/scene/ProjectStepScene.kt`, `SceneController.startParty`; the talk is a
`StepTalk` with its `party`): no picture to tap in, no weather; instead all the village on a festive stage, the
backdrop of where it is (the fire at night, the square in the evening: `StagePlace.NIGHT`, `StagePlace.FEAST`) with garlands
of flags strung across its top (lanterns in them at night) and at its sides what the village put up for it, so its lines
have it in view (`PartyDecor`, by the celebration's `decor`: the first camp's torches flickering, the likof's roof with its
little spruce and ribbons, the veselica's dance floor with a couple turning at each side, the semenj's stalls with honey
hearts and whistles, the town's banners), who speaks in front (the host first, big), everyone else who
lives here and is in the village tonight behind them, smaller (`AgeUps.crowd`, at most 14), the celebration's name in the
corner; its music as it begins (`Sfx.music`). Each line in its speaker's voice and with their face, the learner's turns
as any dialog's ([Adaptive turns](#adaptive-turns), [Rules not yet](#rules-not-yet-what-the-learner-is-asked-grows-with-the-grammar)),
a wrong choice's reaction and its why; its turns test the celebration's words ([Your words in the dialogs](#your-words-in-the-dialogs)).
At its end the celebration's name, the first time its chronicle line and what it brought (😊 +10, ♥ +2 to everyone),
had again "🎉 Spet smo praznovali", and "📚 …: nauči se besed" (its word pack's run); confetti.

**Who says what** (`AgeUps.cast`): the host and the guests their own lines when they are in the village tonight and met;
one who isn't, another guest who is and doesn't speak in it yet (a child for a child), else anyone of the village who is,
of their age, else the host; so a party never waits for anyone, and a guest's lines say nothing of who says them (the
bridge checks it, as for a project's helpers). A reply is said by whoever spoke last.

**The files** (`lani.celebration/v0`, a culture pack's `celebrations/<age>.json`: `tabor`, `zaselek`, `vas`, `trg`,
`mesto`), in the pack's language, every text in the four languages the pack serves and a why in each base:

| Field | What |
|---|---|
| `age`, `language`, `review` | the age, the pack's language, who wrote it and who checked it |
| `emoji`, `name`, `about` | the celebration ("🪗", "Veselica · The village party") and what it is, for the moment's card |
| `moment` | what the moment says as the decorations go up |
| `line`, `memory` | the chronicle's line once the party was had; how the people remember it (fits "Še vedno mislim na …") |
| `decor` | what goes up: `torches`, `spruce` (the likof's, on the newest roof), `bunting`, `floor` (a dance floor and its harmonika player), `stalls`, `banners` (and the procession) |
| `music` | `valcek`, `harmonika` (the harmonika's waltz and polka) or `godba` (the band's polka and the church bell) |
| `place` | `fire` or `square` (under the linden; by the fire without one) |
| `host`, `guests` | villager ids of the cast: who hosts the party (says a line at least) and who speaks besides |
| `pack`, `words` | its word pack (`packs/slavje-<…>.json`) and 1–4 of its words its turns test at every level, in a form the dictionary knows |
| `levels` | `"A1"`, `"A2"` (at least): `{"lines": […]}`, 4–10 of a scene dialog's lines, 2–4 turns (a toast, a song, a dance, a small task), a reaction and a why to every wrong choice; no `sky`, `fx`, `act` or tap turn (it is played over the village) |

Without `levels` (a culture that names its celebration but has no party yet) it is a gathering: the village gathers by the
fire that evening, no dialog. The app bundles the files with the pack (`app/build.gradle.kts`, `CulturePacks`) and reads
them leniently (`Cultures.celebrations`: one in another language or for another age is left out, said why); the bridge
checks them with the pack (`bridge/src/celebrations.ts`, from `validateCulture`), and voice-build voices their lines in
each speaker's voice, as a step's. An older bridge never reads `celebrations/`; an older app bundles the packs it was built
with: the content can go out before the code that plays it.

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
in the first house is Janez's, then France's, Marko's, Vida and Nejc's; a fifth gets the kitchen. The Friuli village's
houses have their rooms too (since 2026-10-30): Nonno Bepi's by the fogolâr (`in-salotto`), Vignaiolo Matteo's cantina
(`in-cantina`, Apicoltore Aldo lodging with him), Ostessa Marisa and Tommaso's attic (`in-soffitta`) and Mugnaio Franco and
Lorenzo's bottega (`in-bottega`), dealt in the order their households moved in, Nonna Rosa's kitchen (`in-cucina`, anyone's)
after them. Without the cast's names (an older bridge sends no household) every room counts as lived in, in the scenes'
order.

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
dry), the room's light dims a little and its window shows the weather ([The rain indoors](#the-rain-indoors)). The
campfire is all out in the open. The tent is cut open at the front for us to look in, and for the weather its roof goes
on over the floor: nothing falls into it, and nothing drips off the cut (the rolled flap along the open front). Its
canvas darkens in the rain and water drips off its eave onto the grass.

### The rain indoors

The storm event's weather (GAME.md "People react": heavy rain slanting in the wind, a grey sky, lightning now and then)
is over every scene while the storm is on (`DaySky.over`, `Sky.STORM`), under whatever a dialog cues; when it's over the
scene eases back to the day's sky (nothing of the storm is kept). Outdoors it rains as a dialog's rain does (above). In a
room (the house's rooms, the kitchen, the school, the smithy) the rain falls in the dark round the plot and over its
garden, and:

- **the window** runs with it (`Weather.glass`, the same for every painter's window): the sky in it grey under the clouds
  (no sun nor moon behind a grey sky), the rain streaking past outside, beads clinging to the pane, now and then a trickle
  running down it, a drop at its head, the glass brightening with a flash of lightning; the cellar's slit high in the
  wall shows a little of it;
- **the room** a little darker (a room keeps half of the grey sky's gloom), the lamp from dusk as ever;
- **the hearth** glowing warmer where it's lit (the kitchen's stove or open hearth further and brighter, the living room's
  kmečka peč warm), on any rainy day; where it's out (`SceneWorld.cold`, `Hearths.cold`: the house out of wood, its fire
  blown out by a lost storm, the forge without charcoal) the stove's grate is dark, the open hearth's logs lie cold on
  grey ash, the forge's coals under ash as when it's banked for the night, unless a dialog fires it up;
- **the sound**: the rain heard indoors (`rain-in`, muffled, the drops on the roof) instead of the rain itself, and the
  thunder about half as loud (GAME.md "Background sounds").

With the system's animations off ("remove animations") the picture keeps its sky without the flashes (`SceneView`); the
thunder still comes. `StormRenderTest` renders the storm over the village and in the kitchen, the living room, the field,
the forest, the square and by the fire (build/scene-snapshots/storm).

### Who is at home in an event

While wolves, a bear or a storm are about (GAME.md "People react") a scene shows who the reaction has at its place
(`Whereabouts.inScene`, `Whereabouts.during`), as the map has them:

- **at home**: whoever the event sends home (in a storm everyone; while wolves or a bear are about everyone but the
  defenders, the lookout and who gathers by the fire), and whoever was in the house when it came, is in the room of the
  house they walk into on the map: their bed's house (`Sleep.homes`, which the map's doors are). The room is the house it
  is seen in (`Whereabouts.placeOf`: the house tapped, else the house it is dealt; the hut's kitchen in the hut). The
  scene's own people at their spots, then the household, the cast and the village's own people at the art's free person
  spots, as many as there are: in a storm Babica Micka by the stove, Teta Ančka at the table and Zala by the door in the
  kitchen, Stari Janez at the table and Lovec Jože by the stove in the living room;
- **by the fire**: the children, the old and the innkeeper who were out, the fire's own people at their seats, more of
  them beside the stump and in front of the woodpile (standing there, not hiding);
- **up the watchtower**: its lookout; the defenders out by the forest's edge are in no scene;
- **asleep**: in bed as ever ([Asleep at night](#asleep-at-night)), but the wolves get the defenders and the lookout up.

Nobody is in two scenes at once. Each has a small quiet marker with the event's emoji ("⛈️", "🐺", "🐻": dark and
see-through, the emoji alone, unlike the gold markers of what someone has for the learner); "Danes tukaj · Here today"
lists them under the event's name, each with what they say of it in the village's language and its meaning under it; a tap
on them, their marker or their row greets them with it: at home a line for inside (`reactions.json` `home`), by the fire
and up the tower the event's own for who they are. The everyday talks still wait. `StormIndoorsTest` checks who is in
which room, the map's doors agreeing, the fire's gathering, the beds the wolves empty and the lines; QA's step `storm` takes
the pictures.

### Effects: the place joins in

Besides the weather, each art has a few effects its painter shows when a dialog asks for them: Babica Micka
fires up the oven for the potica and smoke rises from the chimney, Luka lights the lantern in the tent, the
crows come back to the scarecrow once it smiles. A line or a reply carries an `fx` cue: effect names of the
scene's art (see the table of [arts](#arts)) with a level 0–1, or `true` / `false` for 1 / 0. Like sky cues,
each eases in over about 4 seconds and a name left out keeps its level; a level 0 turns the effect off (the
lantern put out), while an effect no cue has set shows the place as it is by itself (the lantern lit from
dusk). For `birds`, `crows`, `starlings` and `gulls` the level is how far they have flown: 1 is the flock gone over the
sky, the crows landed; for the `tractor` how far it has come down the road (1: past us and gone), for the `sail` how far
it is up; for the field's `hare`, the tower's `pedlar` and the stream's `cart` how far they have come (1: gone, the hare
over the meadow, the pedlar in at the gate, the cart up the far road), for Sivko (`horse`) how far he has come back (1:
home in the shafts), for the market's `hen` how far she has got from her coop onto the cart.

```json
{ "who": "babica", "sl": "O, Jan! Pridi, pridi. Pečem potico.", "en": "Oh, Jan! Come in. I am baking potica.", "fx": { "oven": 1 } }
```

Some effects sound as they come on ([GAME.md](GAME.md#background-sounds)): a `bell` rings a peal, the `cowbells` clank, the
`sheepbells` clink (one close by in the forest, the flock's far off from the fire), and so do Luka's sheep as their `flock`
comes past the camp and goes down the path,
the `oven`, the `stove`, the `forge` and the campfire's `flare` catch with a whoosh, the smithy's `sparks` bring the
smith's blows on the anvil, and the workshop's `hammer` a knock each time it comes down on the nail. On the square the cat
set up the linden mews (`cat` 0, as her rings go out), Muri barks as he comes back (`dog`) and as he wags (`wag`), a cart's
horse comes clopping up the cobbles (`cart-pump`, `cart-stone`), the `band` plays a polka and the `fountain`'s water
gushes. They don't duck under the voice saying the line that brings them, as the other background sounds do: the peal
rings with Tine's "Vlečem! Bim, bam!" at the background's own level, not 10 dB under it.

The effects go with the dialog when it closes, unless it says `"fx_stays": true`: then the ones it ended with
stay in its scene until midnight (`GameState.sceneFx`, by scene), like the lantern Luka lit.

A new effect eases in from 0, so an effect whose 0 is a state of its own shows it at once when a dialog's first line sets
it: the square's cat is up the linden as the children call for help (`cat` 0), Muri is away as Janez asks after him
(`dog` 0), the ball is lost as they look for it (`ball` 0). Its 1 is the place as it is by itself (the cat asleep on the
bench, Muri at his place, the ball in play), and the dialog ends there and keeps it (`fx_stays`), so nothing eases back
to 0 when it closes. One that plays out over the level and ends where it began does too: a `goal` kicked back into play,
a cart stopped on the square, the maypole up. A reaction's effect
passes sooner, with the learner's next pick ([Reactions](#reactions-and-more-than-one-right-answer)), and never stays.
An effect let go eases back to 0 as every level does: the flock flies back, the fox trots back the way she came. So a
dialog whose cue takes something somewhere for good ends it at 1 and keeps it: the hare gone over the meadow, Sivko home,
the pedlar in at the gate, the first cart over the bridge, the scarecrow in the learner's hat and grandpa's scarf, the
lamb drinking, each `"fx_stays": true`; their 1 shows the place as the line left it (nothing of the hare, the pedlar or
the cart; Sivko as he is by himself).
The bridge rejects an effect the scene's art does not have, levels outside 0–1 and an empty cue.

The person the learner talks to reacts too: they wave when the dialog opens, hop at a right choice, tilt their
head at a wrong one (a hand to the chin, puzzled, at one they answer with a reaction), cup an ear while the learner
speaks into the 🎤, and cheer at the end without a mistake (`ui/scene/DialogMood.kt`). Their own lines win: while they
speak they are drawn talking.

### Stage directions: people move on cue

In an art with a stage, a dialog can move its people: Jan asks Luka into the tent out of the rain and Luka comes in and sits
down by the lantern; Zala says "Ti mižiš, jaz se skrijem!" and when Jan has counted to ten she is gone, hidden behind the
door with a braid showing past it; Nejc lies in his bed mumbling "Še pet minut …", sits up on its edge with "Dobro,
vstajam" and goes to the chest for his trousers; Tine is lifted up to the bell's rope and rides up and down with it. A line
or a reply carries an `act` cue: for each person it names, where they go (`to`: a
spot of the art's stage) and how (`pose`). `to` alone: they take the spot's first pose there (they stand; on the logs by
the fire and by the tent's lantern they sit); `pose` alone: they take it where they are. The app (`Stage.arrive`,
`Stage.firstPose`) and the bridge's check (`stageErrors`) follow the one rule, and everyone starts on their spot in its
first pose.

```json
{ "sl": "Seveda, pridi v šotor! Sedi k svetilki.", "en": "Sure, come into the tent! Sit by the lantern.", "ok": true,
  "reply": { "sl": "Hvala, Jan! Tukaj je suho.", "en": "Thanks, Jan! It's dry in here.", "act": { "luka": { "to": "lantern", "pose": "sit" } } } }
```

| Field | Rules |
|---|---|
| `act.<person>` | a person of the scene (their `id`); a cue names at least one |
| `act.<person>.to` | a spot of the art's stage (the table below); every person spot of an art with a stage is one of its spots, and its first pose is how people are there by themselves (on the campfire's logs they sit) |
| `act.<person>.pose` | `stand`, `sit`, `crouch` (squatting), `hide` (only a clue shows), `peek` (half out of the hiding place), `lie` (lying awake: in a bed under its cover, else on the floor on a pillow under a blanket), `sleep` (the same asleep: the eyes shut, the Zzz rising): one the spot has |
| `act_stays` (dialog) | `true`: where it leaves people stays until midnight; by default they go back to their spots when it closes. Only with an `act` cue on a line or a reply to a right choice |
| `act` (happening) | where its people wait for the learner, a cue as a line's (`{"nejc": {"to": "in-bed"}}`): the scene shows them there while it is on, its marker over them, and each of its dialogs starts from there; done, they are back on their spots |

The timing is the sky's and the effects': a line's cue plays when the line is said (a learner's turn: when it comes up), a
right choice's reply's when the reply follows, and a reaction's passes with the learner's next pick (Zala peeks out of her
hiding place at a wrong guess, "Tu me ni! Hi hi!", and is gone again at the next one). A cue is followed from where the person
is (their spot in the scene, where its happening has them wait, or where an earlier cue or today put them): `DialogRun.act`
and `Dialog.actAt` in `game/scene/Stage.kt`, pure.

**Waiting somewhere** (a happening's `act`, `Stage.base`): a happening may have its people somewhere other than their spot
while it is on, as a line's cue would put them. Nejc's waking-up (`v-podstresju/vstani`) has him lying in his bed until Jan
comes (`{"nejc": {"to": "in-bed"}}`, the spot's first pose: `lie`), its ⏰ over him there, and every variant starts from it:
"Mmm … še pet minut …" in bed, "Dobro, vstajam" sitting up on its edge (`{"pose": "sit"}`), then by the bed, at the chest for
his trousers or off down the stairs. At night in the burja he is in bed too, sits up when the window bangs open and falls
asleep once Jan has said good night (`sleep`); Zala, in the tent for a story, lies tucked in on the rug (`{"pose": "lie"}`)
and drops off before its end. The happening done (its dialog heard), they are back where their day has them (in their bed
at night: [Asleep at night](#asleep-at-night)).

**How they get there** (`DioramaPainter.cast`, drawn from the time since they set off, so the scene's canvas and its closer
look agree): a walk from spot to spot, 0.7–1.9 s by how far, with the sprites' walking look and turned the way they go, round
what is in the way (`DioramaPainter.around`: nobody walks through the campfire, they go round the hearth and the tripod); into
hiding, a puff of dust where they stood, and they are gone (nobody sees where to); out of hiding, a hop and a puff where they
hid, then the walk on; from hiding to peeking and back, at once. Every move is over within `Stage.MOVE_S` (2.6 s). The mood
of the person Jan talks to ([Effects](#effects-the-place-joins-in): the wave, the hop, the puzzled hand at the chin) is drawn
wherever they are; hidden, it doesn't show.

**Hidden** means hidden: the painter draws what shows of them at the hiding place (the stage's table below), and that
can't be tapped for their card; someone peeking out, sitting or standing can, as ever. Peeking out at the woodpile's
near end, the child is drawn after the woodpile (`Mark.peek`): in full view, and a tap on them is on them, not on the logs.

**When the dialog closes** (`GameController.keepScene`) its people go back to their spots, walking or popping out of hiding,
unless it says `"act_stays": true`: then where it left them is kept in `GameState.sceneAct` (by scene, like `sceneFx`) until
midnight, and they stay in the scene, their happening done or not, while they are in the village (`Stage.stayed`): Luka sits
by the lantern while the rain goes on. At night whoever sleeps in the scene goes to bed as always.

The bridge checks the cues (`stageErrors` in `bridge/src/scenes.ts`): the people are the scene's, each spot the art's
stage's and each pose the spot's, followed line by line through the dialog from where the happening that plays it has them
wait (and that, as a first line's cue); an art without a stage has no cues, and nor has a story by the fire, an
introduction or a keeper's talk (no picture). An older app reads past `act` and `act_stays` (its people stay where they
stand) and a happening's `act` (they wait on their spots), and a bridge from before stage directions strips them and serves
the scene as before; a bridge from before a stage (the attic's, the smithy's, the church's) or a pose (`sleep`) refuses the
scene that uses it, so the content goes out after the bridge.

| Art | Spots (poses) | Hiding places: what hides them, what shows of someone there |
|---|---|---|
| `livingroom` | `stove`, `table`, `door` (stand: the person spots); `bench` (sit: on the zapeček along the stove); `under-table`, `behind-door` (stand, hide, peek) | `under-table`: crouched under the corner table, its top and cloth over them, their shoes and a bit of their clothes under it; peeking, crouched at its front corner, the head out under the cloth; standing, beside the table (the `table` hides it: a tap on the table is a tap there). `behind-door`: the door stands open, its leaf at right angles to the wall and a hand off the floor, and they stand pressed to the wall behind it, a braid or a sleeve past its sides and the shoes under it; peeking, half out past its edge; standing, in front of the open door (the `door`). Muri hides there too, as an effect, not a person: `dog-hides` ([Arts](#arts)) |
| `campfire` | `left`, `right` (sit: the person spots, on the logs by the fire); `back` (stand: the person spot, between the fire and the tent, where the children play); `behind-stump`, `behind-logs` (stand, hide, peek) | `behind-stump`: crouched behind the stump in front, toward the fire, the hair and an ear over its cut; peeking, crouched at its left, in full view; standing, at its left (the `stump` hides it). `behind-logs`: standing between the woodpile and the trees, the top of the head over the top logs; peeking, crouched at the pile's near end; standing, in front of the pile (the `logs`) |
| `tent` | `left` (stand; lie, sleep: on the rug, on a pillow of their own under a red wool blanket), `right`, `door` (stand: the person spots); `lantern` (sit, stand: on the bedroll behind the table, under the lantern and the roof) | none. Walking in from `door` (the meadow) to the `lantern`, they come through the open front; the weather keeps off them inside as off the tent's inside ([Sky cues](#sky-cues-the-weather-in-a-dialog)) |
| `attic` | `bed` (stand: by Nejc's bed, in front of the rug), `chest`, `stairs` (stand: the person spots); `in-bed` (lie: on their back in Nejc's bed under his patchwork quilt, the head on its pillow, awake; sit: on its edge, the feet down, getting up; sleep: asleep in it as at night) | none |
| `smithy` | `anvil`, `forge`, `door` (stand: the person spots; in the doorway Tone has his coffee while Nejc minds the bellows); a walk goes round the anvil | none |
| `church` | `altar`, `aisle`, `door` (stand: the person spots); `rope` (stand: held up to the bell's rope by the door, a hand on it up by the head, the feet off the floor, riding up and down with it while the bell swings; the rope ends in their hand); a walk goes round the pews | none |

`StageTest` and `DialogTapTest` check where everyone is after each step, what a reaction, a day and a waiting happening
keep, and the moves; `StageRenderTest` that every spot and pose draws at every detail as the whole picture does, the hits in
place, the hidden untappable, a clue showing, a peek showing more, no rain on Luka by the lantern or on his way in, Muri's tail
to tap behind the door, and renders the living room's, the tent's and the hide-and-seek by the fire and with Muri for review
(`build/scene-snapshots/stage`), and the cues of the attic, the tent, the smithy and the church: Nejc in his bed, sitting up
and getting up, the burja's window, Zala tucked in and asleep, Tone in the doorway and back, Tine on the rope, the church door
open (`stage/cues`).

### Tap turns: answer by tapping the scene

A learner's turn can be answered by tapping the picture: "Kje sem?", and Jan taps the door, which is the choice "Aha, za
vrati si!". Each choice of the turn stands for its place, `tap`: a spot of the art's stage (`behind-door`), an object slot of
the art (`stove`) or a person of the scene (`janez`). A tap on it picks that choice, as a tap on the choice would
(`DialogRun.tap`): said as the learner's line, the person's reply, or for a wrong place their reaction ("Tu me ni! Hi hi!",
often with a peek as a hint) and its why one tap away. What counts as a tap on a place (`Stage.tapped`): the person there,
or the one peeking out there; the thing that hides a spot (the door for `behind-door`, the table for `under-table`); the
spot's area, where one would stand. A tap on a place no choice stands for changes nothing. Something an effect puts on a
thing is that thing's to tap: Micka's hen up on Marko's cart (the market's `hen`) is drawn with the cart, so a tap on her
picks "Tam je, na vozu!" as a tap on the cart does; her empty coop is still the `hen`.

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

A thing lost somewhere is found the same way: on the square the ball lies under the bench (`ball` 0, the children's "Kje
je žoga?"), and while it is lost it is the bench's to tap, as a hidden person is their cover's: "Tam je, pod klopjo!"
stands for the `bench`, the linden and the flowers for the wrong places.

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
| `firestation` | `bell`: swings in the belfry and rings, then twice. `helmet`: a glint runs over the helmets on their pegs one after another; then they rock as it passes |
| `bocce` | `ball`: the ball by the jack rolls a little and back; then it knocks into the next one |
| `playground` | `swing`: the near seat swings, then higher |
| `chapel` | `bell`: the little bell swings and rings, then twice |
| `palisade` | `bell` (at the fifth level): rings once, then twice |
| `lookout` | `telescope`: turns on its post to the left and back, then left and right |

A new art can add its own the same way; `ScenePokesTest` checks that each shows at every detail, can be tapped, and
leaves the hits alone, and renders them for review (`build/scene-snapshots/pokes`). Someone asleep is poked too (see below).

Where a poke shows a sound, it makes one, timed as the painter draws it ([GAME.md](GAME.md#background-sounds)): the square's
bell and the church's rope ring at each end of the bell's swing, the anvil rings, the cow's bell clanks as she shakes her
head, the marmot whistles, the owl hoots, the pot's lid rattles, the cat purrs, the campfire whooshes up and the square's
dog barks as he jumps up. The scene view
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
the forest, Micka bringing Jan a blanket in the tent, Nejc awake in the burja; while wolves or a bear are about, the
defenders and the lookout are up and out (GAME.md "People react"), their beds empty. Someone of the scene whose file
doesn't list them (Zala in the kitchen) sleeps as themselves, their sprite and their name. By day nobody is in bed; whoever is at the
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
and the ibex are `v-gorah`'s) is there to tap and to find only while it is out; a dialog's cue (`herd`, `fox`, `bats`, the
mountains' `ibex`) brings it as scenery the day hasn't (the field's `hare` brings one of its own, startled out of the grass
by the fence; the day's hare in the potatoes stays a word). The painters take the village and the date from `SceneFrame.village` and `day`; a
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
| spet jutri · again tomorrow | 🔄 | what a dialog's effect sent away for the day: the market's pigeons flown off, the forest's owl flown off her branch, the bats a cue has none of, Muri away from the square, its ball lost under the bench |
| zgradi: Kozolec · build: Hayrack | 🏗️🪜 | a thing whose building doesn't stand yet ([What the village has built](#what-the-village-has-built)); at a level: "Kovačnica, stopnja 2" |
| nadgradi na stopnjo 3 · upgrade to level 3 | ⬆️3 | a room's thing of a higher level (the kitchen's clock) |
| dokončaj: Mlin na potoku · finish: The mill on the stream | 🏗️ and the project's emoji | a project's fixture (France's mill by the stream) |
| doseži: Vas · reach: Village | ⏳ and the age's emoji | an object's `needs` age |
| le pred nadgradnjo · only before the upgrade | 🏚️ | a thing that went with the upgrade (the open hearth, the straw bed): only ever seen before, never still hidden |

Where the "when" comes from: one pure function per thing, `SceneSights.visibleNow(art, object, frame)` (there in the
scene's world, `SceneFixtures`, and shown by the frame) and `SceneSights.whenShown(art, object, world, fx)` (the hints). The
painters draw by the same conditions: the campfire's stars (`starsOut`) and moon (`moonUp`), the square's sun (`sunUp`),
the market's pigeons and the forest's owl flown off (`flownAway`), Muri away and the ball lost on the square (`lostAt`),
the bats a cue brings (`Wildlife.bats`), the wild animals out (`Wildlife.out`), what is built (`SceneFixtures`). So the
chips never disagree with the picture: `SceneSightsRenderTest` renders every art over whole days in the four seasons, at the moon's phases, under the real sky, in every weather, with the
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
at a person spot of the scene nobody stands at, the door where there is one (Zala comes into the kitchen; into the church
while Tine is away, by its door under the organ loft), clear of the picture as anyone there ([Where people
stand](#where-people-stand)). No child about
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

**Lines in turn** (`ui/scene/LinesInTurn.kt`). A game adds several lines at once: the game's line and the first clue, a
guess with the child's reaction and the next clue, a find and (on "Naprej") the next round's line and clue. They come out
one after another: each of the child's lines is typed in and said in the child's voice to its end (its clip played out,
`Speaker.sayInTurn`) before the next comes out and starts, 0.35 s after a quiet spell, 0.25 s between two in a row, and
the child is drawn talking while one is said. The learner's own guess comes out in its turn, at once and unsaid. Lines
that come while one is said (a tap in the picture, "💡 Še en namig") wait their turn; nothing is skipped. A tap on another
line's 🔊 stops the one being said at once, and the next waits until the 🔊 has been heard; leaving the game (✕, back,
"Nazaj v prizor") stops it at once. Nothing to say a line (no voice at all): it stays the time to read it
(`Speaker.readingMs`, 0.7 s and 70 ms a character). Until October 19, 2026 the screen waited that estimate instead of the
clip: the girl's Piku said "Vidim, vidim nekaj, česar ti ne vidiš …" in 3.8 s against its 3.4, and a clip still on its way
from the node lost the seconds it took, so the first clue cut it off ("she gets cut off at «Vidim, vidim nekaj, …»").
The same holds for a villager's greeting ("Pogovori se": the greeting, then a memory). A dialog's lines need no queue:
each waits for the learner's "Naprej" or pick, which cuts the line being said as it always did; a pick's two lines, the
learner's in their voice and the reply, come in turn ([The learner's voice](#the-learners-voice)); the person is drawn
talking while their line is heard (a scene's dialog, the storyteller's story, a keeper's talk, an arrival, a project's
step).

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
them for the voices (below). The 45 scenes' 702 things have 3104 clues: the 21 Slovene scenes' 330 things 1498 (the
kitchen's 27 things 115, the forest's 24 things 99), the 20 Friuli ones' 312 things 1341 in Italian, Kärnten's 30 things 124
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
where each round's things are on the screen, tag `ISpy`). The lines in turn, from the debug build's log (tags `Speaker`
and `Voice`): each of the child's lines starts ("said «…»", "clip start") only after the one before it has ended ("clip
end", "heard «…»"), no clip of the game is stopped before its end until the game is left, and the game's line, heard to
its end, comes before the first clue.

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
merchant at the market, the festival under the linden tree; while wolves, a bear or a storm are about only the event, the
requests waiting in the Scroll: GAME.md "People react"), who joined the village and waits to be met at their home
(🧳, 👶 a baby: VILLAGERS.md, "Arrivals"), the requests of the map's window at their givers' places
(Babica Micka at a house or hut, Pastir Luka in the forest, Kovač Tone at the smithy, … else by the
fire), and each happening at its scene's place.

Not all at once (Jan, 2026-10-27: "all the bubbles is a bit overloading"): the map shows a small window of requests, 3 on
a phone and 4 on a tablet, one per villager, the best for the learner first (GAME.md, "A window of requests"); the event
and who joined the village are bubbles on top of them always, and an age's party tonight too. The rest wait in the Scroll:
one quiet chip says how many, "📜 +10", small and dark like the edge chips, over the bottom bar's scroll (`WaitingChip`,
`TownMarkers.waiting`; a tap opens the scroll at the villagers' requests; TalkBack: "Še 10 prošenj čaka v zvitku · 10 more
requests wait in the scroll"), not a bubble each; none while wolves, a bear or a storm are about. What's on at a place (a
happening, today's visitor, the burner by his pile, a festival, the day's surprise, a project's step, the treasure map) is
a quiet marker (`TownMarker.quiet`): its bubble a small faint icon without its pointer and its "!" away from the view's
centre, growing to a full bubble as it comes within a fifth of the screen's shorter side of it (`quietness`: full within
0.2 of it, an icon from 0.35; its tap target always a bubble's). Someone just helped says thanks over their head when the
learner comes back to the map, a short call like the event's, in the village's language ("Hvala lepa!", below the pass
mark "Hvala! Jutri še enkrat?": the culture's lines), once, for four seconds, within ten minutes of the run
(`rememberThanks` in `ui/game/TownView.kt`); then they rest, and the place goes to another villager's request. A
request tried below the pass mark leaves a quiet "🔁" over its giver until it comes back (`TownMarkers.backMarkers`: a tap
opens the giver's card, with when it comes back and its drill; GAME.md "A try below the mark").

The game's one tip ("💡 Namigi · Tips", GAME.md "Guidance") is a bubble at its thing among the others (`TownMarker.Kind.TIP`,
"tip:<moment>"): paper with a gold rim and a 💡 in its corner, never wobbling or pulsing, never quiet (there is only ever
one), laid out, nudged and stacked as the others (after their folding and clustering, so a crowded place never swallows
it); its tap opens its card over the map, the thing kept in view above it. In a scene the first time inside one, the tip
is a card at the scene's foot, never over a dialog or a game of «Vidim, vidim».

A bubble stays with its person or place (`ui/game/TownBubbles.kt`, `layoutBubbles`): over them, its pointer short.
Panned or zoomed so they're off the screen, it's off the screen with them, not lined up at its edge with its pointer
stretched back; over someone at the edge it's nudged in by half its width at most (`BUBBLE_NUDGE`), so it shows whole
while they're on the screen and leaves with them. A place going up under the HUD has its bubble kept below the HUD,
fading out over a bubble's height as the place goes further (`bubblePresence`). The bubbles keep apart and off the fire,
but none goes further than a bubble and a half from where it would be (`BUBBLE_REACH`); with no room that near (a crowd,
zoomed out a little) they stack as cards over their people, a strip of each showing, the nearer person's on top
(VILLAGERS.md, "Round the fire"). While bubbles are off the screen's left or right, a small dark chip at that edge says
how many and shows the first one's emoji (the event first, then someone there now, then a request: "‹ 🐑 2", "3 👨‍🌾 ›"),
beside its place's height, between the HUD and the bottom bar; a tap pans the camera to that bubble's place, keeping the
zoom where it can (`EdgeChip`; TalkBack: "Še 2 na levi: … · 2 more to the left: …"). Zoomed out (below 1.5 × the
fitted zoom) each place shows one bubble for all of its own (`clusterBubbles`). A scene's markers (`BubblePlace`) go by
the part of their person in view: none while they're out of it, nudged in at the edge by half the marker's width.

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
gate on the path; a tap on any stretch of it opens it, and its bubbles point at its gate on the road. Where the road
crosses the stream, its stepping stones are the stream's, as the water round them is (the stream bank's card); the bridge
the village builds there is its project's landmark, a tap on it opens the project ([GAME.md](GAME.md), "The crossing").

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

A pile with its work over real days (the spot's `cycle`, GAME.md "A spot's cycle": Primorska's) is drawn as its stage is
today (`Woods.charcoalPile` with the day's `SpotLook`, `Frame.kopa`, from `SpotCycles.look`; snapshots in
`KopaRenderTest`): the bare kopišče, black with old charcoal dust (between piles; snowed over in winter); felled beech in
rows beside the pole (`logs`); the logs stood upright round it, the dome growing day by day (`stack`); the turf over them
from the foot up, then the earth (`covered`); embers glowing at the open top and a thick white plume (`lit`); the earth
dome settling and charring in black patches, a ring of air holes that goes lower day by day, glowing at night, and its
smoke white, then yellow, then blue and thin (`smoke`, the stage's `smoke`); low and dark, a last wisp (`cooling`); raked
open, a black heap glinting blue-grey, the earth pushed to the rim, the rake leaning (`opened`); a row of sacks, more each
day, the heap going (`sacks`); a grey heap of ash and charred logs where it burnt down (`ash`). Its card's line is the
day's (the stage's `about`: "Dim je moder in tanek: oglje je skoraj gotovo"). His talk shows the pile itself over the
dialog instead of the woods behind him (`PilePicture`: the village's own picture round the pile at its finest detail, at
the hour it is, him sitting by it, the smoke drifting; his portrait at its left, the stage's line in its corner), so a
turn that asks the smoke's colour has it in view; on the night the pile breaks out its crown burns over his "Hitro! Glej,
ogenj!" until the learner saves it, all of it when it's lost (`PilePicture`'s fire). A charcoal burner whose culture has no
rounds of the pile (Friuli's, Carinthia's, Lakeland's) talks with the pile as the map always drew it ("Questa è la mia
carbonaia"). A short canvas, without the pile, keeps the woods.

Two scenes open from spots of the landscape. The way to the stream is a footpath of stepping stones from the village
down to a little landing at the water on the village's bank, inside the palisade, which keeps to the stream's far bank
(a ring that runs by the stream on the village's side instead opens in a river gate, a postern, for it: the campfire's
clearing, before the palisade can go up); a tap on the landing or its stones, on the stream where it runs
through the clearing or on its bank above the crossing opens the stream bank's card with "Vstopi · Go in" to Ob potoku,
and a tap on the pond in the woods the pond's, to Pri ribniku. The landing is a thing drawn like the woodpile, found by
the nearest pixel like everything else; the water and the bank are areas, tried after everything drawn there
(buildings, people, the fire, the palisade), so they never take a tap from them. The scroll's places list these spots after
the village's own places.

Once the tent has moved out of the village (GAME.md, "The tent moves to the pond"), it stands on the pond's right bank
as a small campsite (`game/render/Campsite.kt`): the tent under a big spruce, a plank jetty into the pond, a fire ring
with a small fire in front of its door, a stump, the firewood behind it. It is still the tent: a tap on any of it opens
the tent's card with "Vstopi · Go in" to V šotoru, the tent's bubbles point at it, and who waits at the tent waits by its
fire. A tap on the water beside it is still the pond's.

### What someone has here

Jan, 2026-10-14: "Outside, the teacher has 2 exercises, but if I go into the school she has none." Her bubble on the map
was her two requests up (the tutor's "Dvojina: dva brata, dve sestri" and "Tožilnik: vidim brata, pijem kavo", two of a
villager's up at once then, one since the map's window: GAME.md "A window of requests"; the third, "Imeti in iti", waits
in "Kasneje · Later"), and a scene knew only
happenings: she stood in the school (by daylight a request's giver waits at its place, above), without a marker, nothing
of hers under "Danes tukaj", a tap on her only a hello. Now a scene shows by each of its people what the village's bubbles
over them promise, from the same sources (`game/scene/SceneOffers`):

- **their requests up**, from the map's own queue (`Residents.queue(…).up`, `SceneOffers.requestsBy`): the same ones, as
  many as the bubble over them counts, in its order;
- **their happenings here** that open something (a dialog, the storyteller's story tonight), as before;
- **the class's lesson** by the teacher, where the class meets (GAME.md, "The school": the school's scene once it stands,
  the square under the linden before, the fire before that; `SceneOffers.classHere`; nothing before the hamlet or without
  a course).

One marker over each who is here and awake with something (`SceneOffers.marker`): 📚 while the class's lesson waits,
else their first happening's emoji, else 📋; their requests marked as the map marks them, a "!" and how many when more
than one ("📚 💬 ! 2" over Mojca). A marker keeps off the top bar's pills and the markers before it (the teacher beside her
pupils, a child offering «Vidim, vidim»). A tap on someone or on their marker takes their one thing straight away (a
dialog, a request: `SceneOffers.direct`), as before; with several, or the lesson, the choice of them comes up in a sheet:
the lesson first with "🏫 Vse enote · All units", their dialogs ("💬 Pogovori se", "📖 Poslušaj"), then "🙋 Prosi te
za pomoč" and a row per request ("📋 <title>", how close the learner is or what it pays), each one tap into it. Under
"Danes tukaj · Here today" each has one card: the lesson, their happenings with their buttons, their requests' rows; the
requests of someone asleep there too (no marker over a sleeper: a tap still turns them over). Someone with nothing is
greeted as before. `SceneOffersTest` checks the map's bubble over Mojca and her requests in the school agree (the same
two, its first first, as many), each villager's requests theirs alone, the order (the lesson, a dialog, the requests),
the marker, a tap straight in or the choice, and where the class meets. A debug build logs where the scene's things and
people are on the screen (tag `SceneAt`: their areas), for QA to tap them in the picture.

## The learner in the content

The content is written to whoever learns, not to one learner: a text never writes out the learner's name or their
gender. It says them with placeholders, and the app and the bridge say each text to the learner of the profile.

**The name** is `{learner}`; a case of it `{learner:gen}`, `{learner:dat}`, `{learner:acc}`, `{learner:loc}`,
`{learner:ins}` (the nominative is `{learner}`, the vocative the same), and `{learner:poss}` the stem of the possessive,
its ending written after it: `{learner:poss}a hiša` is "Markova hiša", "Anina hiša", `{learner:poss}ega psa` "Markovega
psa". "Marko je {learner:dat} napisal pismo." "Brez {learner:gen} ni nič." "Pri {learner:loc} je, z {learner:ins}."

**What agrees with the learner** is a gender pair, the man's form after `m:`, the woman's after `f:` (either order):
`Si {m:lačen|f:lačna}?`, `{m:Rad|f:Rada} bi kavo.`, `{m:Priden fant|f:Pridno dekle}!`, `Sei {m:stanco|f:stanca}?`,
`{m:mein Lieber|f:meine Liebe}`, `What a polite {m:boy|f:girl}!` A branch may hold `{learner}` (`{f:Draga {learner}|m:Dragi
{learner}}!`) and an ICU select (a newcomer's template: `{m:spoznala|f:{g, select, f {spoznali} other {spoznala}}}`, the
two of you are women only when both are), nothing else in braces and no `|`. Pair the smallest natural unit, mostly one
word; a few words when the change spans them (`{m:priden fant|f:pridno dekle}`: *dekle* is neuter, so what agrees with the
noun is neuter, while what is said of the learner is feminine, `ti si {m:priden|f:pridna}`).

**Calling the learner**: by name (`{learner}`; Slovene calls with the nominative, "Poslušaj, {learner}.") where a call
fits, to get their attention, at the start of a talk or in a warm moment, else not at all ("Lahko noč!", "Bravo!").
Never a bare singular *fant* or *dekle* as an address ("Lahko noč, fant."): that is English "lad"/"lass" in Slovene
words, and Slovene calls *fantje* only in the plural ("Dajmo, fantje!"). An old villager (Micka, Janez, Tone, Ančka)
may say *sinko* to a young man now and then, when moved or tender, never twice in one talk or story, and a woman gets
her name or nothing (no *punca*, no *dekle*): `Hvala, {m:sinko|f:{learner}}.`, translated `{m:son|f:{learner}}`,
`{m:mein Junge|f:{learner}}`, `{m:figliolo|f:{learner}}`. Praise and descriptions stay ("{m:Priden fant|f:Pridno
dekle}!", "Ti si pa {m:pameten fant|f:pametno dekle}.", "tisti fant"). The translations follow the Slovene ("Good
night!", not "Good night, lad!"); a village of another language keeps its own idiom (a Cumbrian "lad", a Carinthian
"Bub"), and its Slovene translation leaves the address out. The personalities say so too ("calls {learner} by name").

What to pair, in every language of a text (the translations too):

- **Said to the learner**: the Slovene second person with an l-participle or an adjective (`Si {m:videl|f:videla} Belo?`,
  `Boš {m:šel|f:šla} z mano?`, `Bodi {m:priden|f:pridna}!`, `Imaš {m:rad|f:rada} …?`, `Si {m:sam|f:sama}?`); the words of
  address (priden fant → pridno dekle, dragi → draga, pozdravljen → pozdravljena, dobrodošel → dobrodošla, sosed → soseda, gospod →
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

Every evening the village's storyteller sits by the fire (the village's centre: the campfire, from Vas the fire ring lit for
the evening, from Trg the square's fire basket: GAME.md "Hearths") and tells a story: Stari Janez at the campfire (`ob-ognju`),
Nonno Bepi at the Italian village's (`al-fuoco`), Old Wilf at the English village's (`by-the-fire`). Not the same one every night: the next one the learner hasn't heard,
told at their level, a long legend in chapters, one an evening. The HUD's "Danes in kmalu · Today and coming up" says what comes:
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
there is a story to tell tonight. With his requests up besides, a tap on him or his marker shows the choice of what he
has, the story first ([What someone has here](#what-someone-has-here)).

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

A telling is a scene dialog told by the teller: his lines have no `who` (the happening's person says them; the panel's
header shows him with his face, [Faces](#faces-the-person-you-talk-to-and-their-expressions): his lines and replies may
carry a `face` as a scene's do, the curated ones have none yet), the first is his, and it has at least one turn for the
learner: a question he asks ("Povej: kaj je naredil lovec?") that the
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
  named or called ("fant", "sinko", "piccolo", "lad"), told to sit, listen or say, tonight's story talked of; "Veš, …" taken off
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
all of its object slots (a slot without content is scenery, not tappable) and returns hit areas. The living room, the
campfire, the tent, the attic, the smithy and the church have a stage besides: spots a dialog can send its people to, to
stand, sit, lie or hide (see [Stage directions](#stage-directions-people-move-on-cue), with their spots and poses).

| Art | Object slots | Person spots | Effects (`fx`) |
|---|---|---|---|
| `campfire` | fire, logs, kettle, stones, smoke, sparks, moon, stars, tent, axe, bucket, stump (the place grows with the village, from the camp's clearing to the town's square with its homes, church, linden, well and lanterns: [Ob ognju by the age](#ob-ognju-by-the-age)) | left, right, back (a stage: [Stage directions](#stage-directions-people-move-on-cue)); the crowd's spots round the fire | `flare` (the fire burns higher, sparks fly), `embers` (the fire burns down: the flames lower and fewer, no sparks, a thread of smoke and a smaller light; 1: only the embers glowing red in the ash; Rosa's and Resi's "the fire is small now"), `steam` (the kettle steams), `bell` (a distant bell: its rings go out in the sky over the woods, opposite the moon), `sheepbells` (the sheep's bells up on the pasture, out of sight: small clinks going out here and there among the crowns of the trees at the back on the right, below the top bar's 🔍, and their sound: Luka's "Slišiš zvonce?"), `flock` (Luka's sheep, as many as the dialog counts: up to 0.5 they come out of the trees at the back on the right, past the tent and the chopping block (from the village on, where the homes stand, up the path from the meadow on the left: [Ob ognju by the age](#ob-ognju-by-the-age)), and mill along the camp's edge on the right and in front, grazing, to be counted; from 0.5 they go one after another along the front edge and down the path out of the clearing on the left, 1 gone; a few wear bells, clinking over them, and while they're here the far `sheepbells` ring on them; Luka's "Ovce gredo s planine. Pomagaj mi jih prešteti!") |
| `kitchen` | stove, pot, bowl, table, chair, bread, plate, glass, cup, spoon, fork, knife, window, door, cupboard, clock, milk, eggs, water, salt, potatoes, hearth, bench, bed, shelf, curtains, corner (by the level: see [What the village has built](#what-the-village-has-built)) | stove, table, door | `oven` (the stove fired up, smoke from the chimney; at level 1 the hearth's fire burns higher), `steam` (off the cup and the bowl) |
| `forest` | tree, spruce, mushroom, berries, stream, stone, path, bird, deer, squirrel, owl, leaves, sheep, highseat (the hunter's preža), flowers; the wild: hare, butterfly, fox, stag, hedgehog, boar, badger, dormouse, bat | path, clearing, highseat (sitting up in it) | `birds` (a flock flies up out of the trees and away), `herd` (a stag and two hinds step out of the trees at the far edge: how far out), `fox` (the fox trots along the edge to her place: how far she has come), `hoot` (the owl turns its head aside and hoots again and again, as far as the level up to 0.9; above it now and then she turns it right round, the back of it to us for a moment: Luka's "Glej! Glavo obrne čisto nazaj."), `bats` (bats over the clearing: how many), `sheepbells` (the brass bell under the lost sheep's chin swings and rings, its clinks going out, each at an end of its swing: `Sfx.SHEEP_BELL_SWING`; Luka's "Slišiš? Zvonec!"), `owl-flies` (the owl spreads her wings and flies off her branch, up and away across the clearing into the far trees, how far she has flown; at 1 she is gone, the branch bare, and her word with her, as the market's pigeons: [The words panel](#the-words-panel-here-now-seen-before-still-hidden); "Huu … odletela je"), `fell` (the tallest spruce at the clearing's far side, right of the path, felled: 0 standing among the edge trees; to 0.2 it shivers as the last cut goes in, chips flying; then it falls to the right, faster and faster, crashing down by 0.85 with a puff of dust and twigs flying; 1 lying along the ground by its stump; not set: as the maypole's project `mlaj` has it, standing until it is felled, lying there once it is, only the stump once it is carried off: Marko's "Pazite! Smreka pada!"), `fell-oak` (the oak beside it felled the same way, falling to the left: Tone's "Pazite, pada! … Bum!"), `sheep` (the lost sheep: 0 out of sight behind the big spruce across the brook, only her bell's clinks while `sheepbells` rings, not the word to tap, as the square's Muri away; rising, she peeks out past its lowest branches and walks home through the brook; 1 at her place as by herself: "Bianca è sparita!" … "Eccola!", Luka's "Tam, za smreko!"), `tracks` (a bear's prints in the mud of the brook's bank by the path, pad and five toes, on over the grass toward the boulder: how many and how clear; Anton's "Poglej tja, v blato. Velike sledi."), `does` (roe deer stepping out of the trees onto the far meadow at dusk one after another and grazing: as many as the dialog counts, else five; how many are out, 1 all of them: Jože's "Zdaj pridejo srne na travnik") |
| `square` | linden, bench, ball, well, dog, cat, house, tower, bicycle, flowers, sun, fence | bench, well, play1, play2, play3 | `bell` (the tower's bell swings and rings), `cat` (the cat up the linden: 0 stuck on its lowest limb just out from the fork, mewing; down the limb, down the trunk tail first and a hop onto the bench; 1 curled up there again, as by herself: how far down she has come), `dog` (Muri, Janez's dog: 0 away, nothing of him on the square; up from behind the flower bed, out to its end, 0.5 sniffing at the flowers there; running back to his place, 1 as by himself), `wag` (Muri wagging hard, his tongue out, hopping for joy: how hard), `ball` (0 lost under the bench, its red and white showing in the shade between the legs: the `bench`'s to tap, not a word of its own while it's lost; rolling out to the game; 1 in play), `goal` (a shot in a high arc between two satchels at the back of the game, the children jumping for joy, then the ball kicked back into play: how far it has gone; 1 the game as ever), `cart-pump` and `cart-stone` (a horse and cart coming toward us out of the lane by the church, trotting, and stopping in the middle of the square: how far it has come; on it the fire pump from Gorica, red with its brass cylinders and long handles, or blocks of white stone glinting in the sun, unloaded once the fountain's stone lies on the square), `band` (the village band under the linden in blue coats and caps, a helicon, two trumpets and the big drum, bobbing to the beat, notes going up: how loud), `dance` (couples dancing a polka on the square, turning hand in hand, her skirt flying out: how many, up to four), `maypole` (the maypole raised: its top hauled up on two ropes while its foot slides from its trestles into its hole, the wreath and the ribbons on it: how far up; only where it lies ready, its wreath woven, or stands, never out of its season), `fountain` (the fountain's water: down the open ditch from the garden while the ditch is dug and until the pipes are in, how far it has come; from its four pipes into the trough once they are fitted, how much; not set: dry until it is finished, then running) |
| `tent` | lantern, sleepingbag, blanket, pillow, backpack, map, boots, coat, hat, book, cup, rope | left, right, door | `lantern` (0 dark, 1 lit) |
| `field` | wheat, corn, potatoes, cabbage, kozolec, hay, scythe, rake, basket, cart, scarecrow, fence, path, horse; the wild: deer, hare, butterfly, fox, boar, bat | field, kozolec, path | `crows` (crows come to the scarecrow), `bell` (the church bell on the hill, its rings coming over the fields; not set: the ave, a few minutes round sunset), `bats`, `hare` (a clump of tall grass by the fence stirs, up to 0.4; then a hare leaps out over the track and bounds off over the meadow: how far, 1 gone; one cue to 1 on France's "Tiho … nekaj se premika v travi" has the grass stir for two seconds and the hare out and away in the next two), `horse` (Sivko: 0 away beyond the picture's left edge, the cart's shafts down on the grass; rising, he walks back over the meadow in front of the kozolec, scenery until about halfway, when he is in the picture of every width, then the horse to tap; 1 home in the shafts, as not set), `hat` (the scarecrow's straw hat: 0 gone, straw sticking out of the sack; above 0 the learner's big felt hat drops onto its head, and toward 1 slips down onto its nose; not set: the straw hat), `scarf` (a red wool scarf round its neck, its ends blowing), `face` (a face drawn on the scarecrow's sack head in charcoal: 0 a blank sack, and while below 0.5 the crow off its arm, the birds afraid of it; rising, two round eyes, a nose, then a wide smile from corner to corner, done by 0.65; not set: its stitched face; Tine's "Strašilo ima klobuk, ampak nima obraza. Nariši mu obraz!") |
| `livingroom` | stove, bench, table, chest, window, door, dog, chair, curtains, rug, lamp, clock, corner, photo, glasses, book (by the level) | stove, table, door | `stove` (fired up: its tiles warm, a glow on the bench), `dog` (Muri awake, sitting up and wagging; at night he sleeps whatever), `dog-hides` (Muri plays hide-and-seek: off the bench and behind the open door, only his bushy tail wagging past the leaf's edge, the `dog` to tap; while Janez sleeps on the bench, Muri stays curled up by him) |
| `workshop` | workbench, hammer, nails, boards, axe, broom, window, door, saw, tools, ladder, rake, wheelbarrow, horse, lantern (by the level) | bench, floor, door | `dust` (sawdust flying off the vice as a board is sawn), `hammer` (a board laid across the bench by the vice, a nail in it, and the hammer up in the hand of whoever stands at the bench, coming down on it every 0.75 s, a puff of sawdust and a knock each time: France's "Ena, dva … bum!"), `toy` (Sivko, Tine's wooden horse on the bench: 0 lying on his side, a front leg off and its dowel hole showing; from 0.3 the leg glued back on; from 0.65 stood up and set down on his four legs; 1 whole, as by himself; Tine's "Moj konjiček Sivko nima noge" and "Glej! Dedek je Sivka popravil. Spet je cel!") |
| `cellar` | barrel, press, stairs, door, window, candle, jug, glass, crate, shelf, bottle, grapes, wine, ham, cheese (by the level) | barrel, press, stairs | `tap` (wine running from the barrel's tap into the jug, filling it), `candle` (0 out; lit, its flame flaring up the higher, as just lit; not set: lit, calm) |
| `attic` | bed, pillow, blanket, cradle, chest, roof, window, stairs, wardrobe, rug, lamp, ball, mirror, toy (by the level) | bed, chest, stairs (a stage, with Nejc's bed) | `lamp` (0 dark, 1 lit; not set: lit from dusk), `cradle` (the cradle rocks: how far), `window` (the gable window blown open by the burja: the night in the empty frame, the casement swung into the room, swinging in the gusts and banging back now and then, a draught of pale streaks, flakes in winter, blowing in; 0: shut) |
| `smithy` | forge, fire, anvil, hammer, tongs, bellows, horseshoe, nails, bucket, chain, axe, wheel, window, door | anvil, forge, door (a stage) | `forge` (the forge roars), `sparks` (sparks off the anvil at every blow), `iron` (a shoe being forged on the anvil's face beside the hammer, held in the tongs: its colour the level, 1 the straight blank yellow-white hot, cooling through orange and red, bent into a U from 0.75 to 0.45, a dull red, then grey toward 0, glowing and lighting the anvil while hot; 0 gone, taken; Tone's "Železo je vroče. Rdeče. Vidiš? Zdaj ga lahko upognem."), `cart` (Marko's cart in the yard by the tethering post, four crates of bottles on it: 0 its front corner on the right propped on a block of wood, that wheel off and leaning against its side; rising, the wheel is rolled onto its axle and the corner lifted off the block, the block put aside; 1 on four wheels, as the dialog leaves it for the day; Marko's "Poglej moj voz. … Samo kolo je zlomljeno" and "Moj voz ima spet štiri kolesa") |
| `school` | blackboard, chalk, sponge, desk, chair, book, notebook, pencil, bag, map, globe, clock, window, door | teacher, desk1, desk2 (sitting on the chair behind a pupil's desk: the desk in front of them hides the lap and the legs, the head and the shoulders show over its lid, the hands lie on it unless the pose needs them; `SchoolSeatsRenderTest`, `build/scene-snapshots/school`), door | none yet |
| `hills` | vineyard, vine, grapes, hill, church, belltower, farmhouse, cypress, persimmon, wall, mountains, sea, road, crate | vines, wall, road | `bell` (the bell swings in the tower, its rings go out), `tractor` (a tractor with six crates comes down the track: how far it has come; from 0.97 past us and gone, so a dialog ends with it at 1 and keeps it), `starlings` (a flock rises off the vines and wheels away) |
| `sea` | sea, lagoon, casone, boat, posts, gull, wave, beach, pier, shell, fish, net, horizon, crab | beach, pier, boat | `waves` (the sea rises, breakers burst white, spray), `gulls` (the gulls off the bricole, one by one), `sail` (the batela's ochre sail with its sun goes up) |
| `alps` | mountain, peak, lake, church, bridge, pasture, hut, cow, cowbell, larch, spruce, waterfall, stream, stone, wall, path; the wild: chamois, ibex | path, wall, stream | `cowbells` (the cows' bells swing and ring: how loud), `bell` (the church bell by the lake swings, its rings go out), `fish` (a fish jumps out of the lake again and again, rings on the water), `birds` (alpine choughs off the crags and away: how far they have flown), `glow` (the alpenglow: how pink the peaks; not set: at sunrise and sunset), `ibex` (the ibex climbs onto a crag at the pasture's brow, right of the stream against the lake, from behind it, his horns first, how far up; 1: on top. Whatever the day has out, as scenery then, not the word to tap: Janez's "Poglej gor, na skalo"), `bell-lost` (the near cow's bell lost: 0 lying on its side in the grass by the stream with its strap, silent, the `cowbell` to tap there, her neck bare; rising, it goes back up onto her neck; 1 as by itself: Luka's "Poglej tisto kravo. Nič ne zvoni!" … "Tukaj je, v travi!"), `pail` (a wooden milk pail on the grass by the feet of whoever stands at the wall: how full, 1 white to the brim with a little foam; 0 none: "Poglej vedro. Polno je!", "Na, drži vedro"), `cows` (as many cows on the pasture as the dialog counts, the near one among them, else the art's four, and a calf by one of them: those beyond the art's own come up over the brow from the slope behind it one after another to their places, how far; 1 all of them grazing: Luka's "Preštej krave na planini!" … "Pa še tele.") |
| `stream` | stream, stones, bridge, willow, trout, dragonfly, cress, mill, laundry, sheep; the wild: fox, hedgehog, butterfly, bat | bank, ford, mill | `trout` (a trout leaps out of the current again and again, rings where it falls back), `wheel` (the mill wheel's water, from the mill's last step on, which lets the water in: 0 the flume dry and the wheel still, 1 a full gush; not set: an easy turn once the mill grinds, still before), `bats`, `bell` (the village's bell, its rings coming down to the water; not set: the ave), `lamb` (a lamb in front of the drinking ewe: below 0.4 turned from the water, its head up; then it turns and its head goes down; from 0.9 it drinks, rings on the water), `hose` (the fire brigade's red hand pump on the right bank, its suction hose in the stream: 0 still; above it pumped, the beam see-sawing, a jet arcing from the nozzle over the water; from 0.6 the hose loose, rearing like a snake, the jet whipping round and spray coming down on everyone), `cart` (a horse and cart over the new bridge, once its deck is laid: how far, 0 waiting on the road at its near end, the horse grazing; up onto the deck, over the stream, down onto the far bank and up the road behind the willow, 1 gone), `flock` (Luka's flock: up to 0.5 the sheep come in over the meadow from beyond the right edge one after another and drink along the right bank, the front row at the water, three rows behind grazing, a head up now and then; from 0.5 they go home toward us one after another, out at the picture's bottom, 1 gone; as many as the dialog counts, `count` (the ewe always there among them), else twenty: "Koliko ovc imaš? Dvajset."), `straggler` (the one who always comes last, Bianca: how far she has trotted in alone from the right to the water's edge in front of us, there at 0.8, drinking from 0.9; she goes home with the flock, last), `full` (the stream running high and brown the morning after the night's rain, the sky clear: how high), `sacks` (the day's sacks of flour stacked by the mill's door to count, once it grinds: as many as the dialog counts, else five), `sheet` (the wind takes the sheet off the washing stone: to 0.6 it flies up over the water, flapping; caught, it is brought back, and from 0.8 lies folded on the basket) |
| `pond` | pond, lilies, reeds, frog, ducks, jetty, boat, fish, heron, dragonfly; the wild: deer, fox, butterfly, bat | shore, reeds, jetty | `ducks` (wild ducks fly up out of the reeds and away), `frog` (the frog jumps off its lily pad; 1: in the water, just its eyes out), `fish` (a fish jumps again and again, rings on the water), `bats` (bats hunting low over the water), `dog` (Muri with Janez at the pond, on the grass at the jetty's foot while Janez fishes at its end: below 0.25 curled up asleep, then sitting up and wagging, from 0.75 barking at the fish, the rings of his barks going out over the water; not set: at home), `tadpoles` (tadpoles wriggling and darting in the shallows by the reeds: how many; none while the pond is frozen), `heron` (the heron strikes: up to 0.3 it leans in and draws its neck back, then its bill darts into the water in front of it with a splash and rings, and it comes up with a silver fish across its bill, standing tall with it from 0.7, 1; 0 or not set: waiting as by itself; Anton's "Pst. … Glejte čapljo. Zdaj!"), `bite` (whoever stands at the jetty's end fishes while it is set: a rod in their hands out over the water, its line down to a red and white float; 0 the float still, rising it bobs and dips more often and deeper, rings going out; from 0.7 it is pulled under, the rod bends and a carp thrashes at the line, splashing; from 0.9 it is lifted out, and at 1 it hangs on the line over the water, wriggling; falling, it goes back into the water and the float comes up again; Janez's "Tiho … Plovec se premika. Riba je prijela!"), `bucket` (the day's catch in a wooden bucket on the jetty by whoever stands at its end: as many fish as the dialog counts, else five, their tails over its rim to be counted; Janez's "Poglej v vedro in preštej.") |
| `church` | altar, candles, cross, painting, pews, flowers, window, organ, font, bell, rope, door | altar, aisle, door (a stage, with the bell's rope) | `candles` (1: all four on the altar lit, one by one as it rises; 0: out; not set: the middle two burn), `bell` (the bell swings in the gable over the door and rings, the rope going up and down), `organ` (notes rise from the pipes), `door` (the door blown open, how far: its two leaves swung into the nave and stirring in the wind, the churchyard's day or night in the doorway, the candles' flames leaning in the draught; Babica's "Joj, veter!") |
| `apiary` | beehouse, hive, panel, bee, honey, comb, flowers, tree, smoker, extractor, wax, bench | hives, table, bench | `swarm` (a cloud of bees rises off the hives and circles up by the linden: how many, how far), `smoke` (the smoker puffs, the smoke drifting to the hives; not set: a thin thread), `honey` (honey runs from the extractor's tap into the jar, the crank turning, the jar filling) |
| `market` | stall, scale, weights, eggs, bread, cheese, honey, apples, vegetables, basket, crate, hen, cart, wine, pigeon, bell | stall, buyer, cart | `bustle` (a busy market day: shoppers crowd the lane and the square, how busy), `bell` (the market bell on its post swings and rings), `pigeons` (the pigeons fly up off the cobbles and wheel away: how far they have flown), `hen` (Micka's hen out of her wicker coop, which stays the `hen` to tap: on her way she flutters up over the lane, and at 1 she stands pecking on Marko's cart between the bottles and the barrel, drawn with the cart, so a tap on her there is a tap on the `cart`; falling, she flies back; 0: in her coop) |
| `watchtower` | tower, railing, torch, horn, spyglass, flag, crow, roofs, palisade, gate, road, forest, river, valley, mountains, horizon | watch, rail, hatch | `torch` (the watch's torch: 0 out, 1 lit; not set: lit from dusk), `horn` (the horn blown, its call going out over the land: how loud), `birds` (a flock rises off the forest and wheels away: how far), `wolves` (eyes shining in the dark forest at night, grey wolves slinking over the fields by day: how many), `pedlar` (the krošnjar coming down the road from where it passes the forest's edge to the gate: how far, 1 in at the gate; a little man in a black hat and a white shirt under his krošnja, its frame higher than his hat with a sieve on top, bigger than his size so the eye finds him from up there) |
| `mill` | millstone, hopper, grain, flour, sack, shaft, gear, wheel, flume, scale, measure, bench, window, door, ladder, cat, mouse, lantern, sieve | stones, sacks, door | `grind` (the stones grinding: below 0.12 stopped, the water off the flume and the waterwheel, the cog wheel, the pinion and the runner still, no flour running; below 0.6 an easy turn; from 0.6 hard, the shoe shaking, the flour running thick; not set: an easy turn), `pour` (grain poured into the hopper: how full it is, 0 empty, 1 full; while it fills a sack tipped over its rim pours a golden stream into it; not set: half full), `sack` (the flour sack under the spout filling: 0 empty and slack, 1 full and round, flour heaped in its mouth; not set: half full), `dust` (a puff of flour dust, how thick: a white cloud billowing up off the sack over whoever stands by it, drifting into the window's light, more motes in its beam), `cat` (the cat after the mouse: to 0.35 crouched and wiggling, the tail twitching; then the leap; from 0.75 landed by the hole, sitting, the mouse gone in, its tail last; not set: crouched, watching, the mouse peeping out now and then), `lantern` (0 dark, 1 lit; not set: lit from dusk), `weigh` (a bag of flour weighed on the scale: to 0.5 on its left pan, the beam tipping down to it; then the weights on the right pan, the beam swinging back level), `toll` (the miller's toll, merica: grain heaped in the measure, how full) |
| `firestation` | pump, hose, helmet, axe, ladder, bucket, bell, door, tower, uniform, flag, picture, bench, horn, lantern, window | pump, hoses, door | `bell` (the alarm bell swinging in the hose tower's belfry and ringing, its rope's end going up and down, its rings going out: how hard), `pump` (the hand pump worked: its two beams see-sawing, the hose run from its outlet to the nozzle on the front stub and a jet arcing over it into the yard, spray where it lands: how hard), `doors` (the big doors: 0 shut, 1 swung into the hall against the jambs; not set: open by day, shut at night), `lantern` (0 dark, 1 lit; not set: lit from dusk), `horn` (the signal horn on its nail sounding, its calls going out, the horn trembling: how loud) |
| `bocce` | court, board, sand, jack, ball, linden, bench, table, jug, glass, scoreboard, rake, meter, pergola, lantern | throw, jack, table | `throw` (a ball thrown from the near end: to 0.4 in a low arc, landing in a puff of sand, then rolling and slowing to rest by the jack at 1; 0 still in the hand, the near end's ball gone from the sand while it flies), `hit` (a ball thrown high at the one nearest the jack: to 0.5 in its arc, then a clack, a puff and rings, the ball it hit flying off toward the far end and the back board, the thrown one in its place at 1), `measure` (the folding rule off the back board and laid on the sand from the jack toward the nearest ball, unfolding: how far out; 0 on the board), `pour` (wine from the jug into the glass: the jug tipping, a red stream, the glass filling: how full; not set: half full), `score` (the chalk marks under MI on the scoreboard, as many as a counting dialog counts, else nine, coming one by one as the level rises; not set: the day's five to three), `lantern` (on its post: 0 dark, 1 lit; not set: lit from dusk) |
| `playground` | swing, slide, sandpit, sand, bucket, spade, climber, house, ball, hopscotch, bench, tree, fence, gate, school | swing, sandpit, bench | `swing` (the near seat swinging, how high; not set: still), `ball` (0 lost under the slide's foot, half hidden by the chute, the `slide`'s to tap meanwhile; rising, it rolls out over the lawn; 1 at its place; not set: at its place), `kite` (a kite up over the playground on its string from beside the swing, its tail of bows fluttering in the wind: how high), `bubbles` (soap bubbles drifting up from beside the bench over the lawn, catching the light: how many), `castle` (a sand castle in the sandpit: a mound, then its wall and towers, at 1 a little flag on top) |
| `chapel` | chapel, roof, bell, altar, cloth, picture, candle, flowers, grille, bench, linden, road, field, cross, hills | chapel, bench (sitting), road | `bell` (the little bell swinging in its turret on the ridge, or an ancona's on its bracket, ringing, its rings going out over the fields: how hard), `candles` (0 all out, 1 all three lit: the two red glass candles on the step and the white one on the altar; not set: one red one burns), `flowers` (the flowers in the vases and the pot: 0 wilted, brown and drooping, 1 fresh in the season's colours; not set: fresh), `traveller` (a traveller with his hat and staff walking up the road from our feet past the chapel and away toward the hills, smaller as he goes: how far, 1 gone) |
| `palisade` | palisade, stake, gate, bar, torch, flag, roof, ladder, bell, road, forest, meadow, bench, trough, hills (by the palisade's level: see [What the village has built](#what-the-village-has-built)) | gate, bench, road (out beyond the gate) | `gate` (the two wings: 0 shut and barred, 1 swung in against the stakes; not set: open by day, shut at night), `torch` (on the gate's posts, from the second level: 0 out, 1 lit; not set: lit from dusk), `wolves` (eyes shining at the forest's edge at night, grey wolves slinking along it by day: how many; at night by themselves while the village's wolves event is on), `flock` (Luka's flock, as many as the dialog counts, else twelve: to 0.5 out through the gate and down the road onto the meadow, from 0.5 home again, 1 all in), `pedlar` (the pedlar under his krošnja coming up the road out of the forest to the gate: how far, 1 in at the gate), `bell` (the bell under the gate's roof swinging and ringing, at the fifth level: how hard) |
| `lookout` | tower, railing, roof, board, telescope, book, stamp, bench, stairs, flag, mountains, peak, sea, village, forest, valley | rail, board, stairs | `clear` (how clear the air is: 0 a white haze over the far land, the Alps and the sea gone in it, 1 clear to the sea; not set: as the day is, hazy in summer, clear in autumn and winter, less after rain or in a fog), `glow` (the alpenglow on the peaks: how pink; not set: at sunrise and sunset), `hawk` (a buzzard circling over the valley below us, its wings spread: how wide its rounds; 0 none), `bell` (the village's church bell far below, its rings coming up from its tower: how hard) |

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
its light lies on the water only while it is up. Its card in the night sky says its phase.

The market scene trades nothing itself: a visit's market is the town bar's "⚖️ Tržnica · Market" (companion/README.md,
"Things to do on a visit"). Its stall has a seller at `stall` and a buyer at `buyer`, so that haggle could one day open
from here, with the host's villager behind the counter.

### Culture looks

Jan, 2026-10-28: "do the scenes look different per culture?" Each art can draw its picture in the culture's own way. The
fire's village round it (`FireStyle`: [Ob ognju by the age](#ob-ognju-by-the-age)) and the landscapes at the horizon
were each culture's own already; every other art draws the Slovene picture until the culture's look says otherwise.

A culture's look (`game/scene/CultureLook.kt`: `CultureLook`, its tables in `CultureLooks`) sets three kinds of thing of
an art, each by a name of that art:

| Kind | Named | Read by the painter as |
|---|---|---|
| a colour | `"school.plaster.l"` | `colour("plaster.l", default)`: an ARGB colour |
| a material | `"school.wainscot"` | `material("wainscot", default)`: a word the painter knows (`"wood"`, `"painted"`) |
| a variant | `"school.map"`, `"school.abacus"` | `variant("map")`: which of the art's own versions of a thing it draws (`"friuli"`), or a thing only that culture's village has; null for the art's own |
| a fixture | `"kitchen.hearth"` | `SceneFixtures.there(art, slot, world)`: when a thing of the art is there where the culture's picture keeps it otherwise than the art's own ([What the village has built](#what-the-village-has-built)): `null` for always (Friuli's fogolâr, the kitchen's `hearth`, stands at every level, not only the first) |

The frame's world carries the village's culture (`SceneWorld.culture`; on a visit the host's) and with it its look
(`SceneWorld.look`), which every painter reads through `ArtPainter` (`look`, `colour`, `material`, `variant`). A
painter always asks with its own default, so what a culture doesn't set is the painter's own: the default culture
(Primorska, the look every art was drawn in) and a culture without a look draw exactly as before. The words' stickers
are cut in the learner's culture's look (`Stickers.cut(…, culture)`, kept apart by `CultureLook.keyOf`: a look that
changes nothing of the art shares the default culture's sticker).

To give an art a culture's look:

1. In its painter, read what varies once a frame (at the top of `paint()`, into the painter's fields; never a lookup per
   pixel), and branch where the thing is drawn: `when (variant("map")) { "friuli" -> mapFriuli() else -> map() }`; a
   thing only some cultures add, `if (variant("abacus") != null) prop { abacus() }`. A still picture ([`still`](#ob-ognju-by-the-age))
   that the look changes is kept by `lookKey` too.
2. In `CultureLooks`, the art's table for the culture, `Table.of("school", colours = …, materials = …, variants = …)`
   as a value of its own (`FRIULI_SCHOOL`), added to the culture's entry of `tables`. A thing the culture's picture keeps
   at other levels than the art's own (`fixtures = mapOf("hearth" to null)`) is a word of the scene there as drawn: the words
   panel, I spy, a building's upgrade and the stickers all go by `SceneFixtures.there` with the world's culture; the
   culture's scene file says the same `needs` (none, for Friuli's `in-cucina` hearth).
3. Tests: `CultureLooksTest` keeps every art of the default culture pixel for pixel as it was (its digests) and every art
   a look doesn't touch the same in every culture; a render test of the new look shows what it changes (the school's:
   `SchoolLooksTest`, its pictures in `build/scene-snapshots/looks/`; the mountains': `AlpsLooksTest`, in
   `build/scene-snapshots/looks/alps/`, which also checks that every thing is there to tap exactly when the default
   culture's picture has it, over the seasons, the hours, the weather and every effect; Friuli's kitchen, smithy, tent and
   mill: `FriuliIndoorLooksTest`, `friuli-*` there, also checking that every thing a level has is tappable once and the
   people stand where they did). The frame budget holds (`ScenePaintersTest`).
4. The painter's class comment and the table below say what the culture's look draws; the culture's scene's I spy clues
   describe its own picture (the Friuli school's map is of Friuli).

Friuli's indoor places: docs/plans/05-built/culture-looks-friuli-indoors.md (what each draws instead of the Slovene
picture, the words checked against it, the frame times).

| Culture | Art | What its look draws |
|---|---|---|
| `friuli` | `field` (Nel campo) | `FRIULI_FIELD`: the fienile of the Friulian plain where the kozolec stands (`kozolec`: `fienile`): walls of river cobbles laid in courses with bands of brick and brick quoins, two round brick arches below with the hay heaped under the left one, a brick string course, the hay loft above, open in the middle behind its pole with the hay bulging out, a brick lattice (gelosia) either side and one in the gable, a low coppi roof out over the walls on its rafters; the village on the hill as Friuli builds it (`village`: the fire's village's colours, `FireStyle`): ochre, terracotta and cream houses under coppi, shutters, the church's gable and the free-standing brick campanile with its belfry and pyramid cap, where the evening bell rings; its farmsteads with a brick barn; vine rows along the Collio's slopes (`hills`); the plain's trees (`trees`): a row of pollarded mulberries behind the corn with the vines strung from tree to tree (the piantata), Lombardy poplars along a far ditch |
| `friuli` | `forest` (Nel bosco) | `FRIULI_FOREST`: an ancona by the path (`shrine`, the maina of the Friulian roads): a plastered pillar, the Madonna in its blue niche under a little coppi roof, a jar of the season's flowers, from dusk a red lumino and its light; Aldo's basket by whoever stands at the clearing while a dialog counts its mushrooms (`basket`: as many as `SceneFrame.count`, "guarda nel mio cesto. Quanti funghi ci sono?") |
| `friuli` | `stream` (Al ruscello) | `FRIULI_STREAM`: Lombardy poplars along the banks and in the woods' edge, gold in October, a bare broom in winter (`trees`); vine rows on the hill over the valley and a hamlet with its campanile (`hills`) |
| `friuli` | `pond` (Allo stagno) | `FRIULI_POND`: the lowland wood round the pond, as by the lagoon (`trees`): Lombardy poplars where the spruces stand, oaks for the beeches, their picture in the water; Nonno Bepi's tin of worms on the grass by Moro while he is there or someone fishes (`bait`: "mi passi la scatola dei vermi? È lì, vicino a Moro") |
| `friuli` | `sea` (Il mare) | `FRIULI_SEA`: the batela's halyard from its masthead down to the cleat, the rest coiled by the stern (`halyard`: Nonno Bepi's "Vedi la corda lì?"); the rest of the sea is Friuli's own art |
| `kaernten` | `alps` (Am See) | `KAERNTEN_ALPS`: the Karawanken behind the lake, the Mittagskogel's pyramid (the `peak`) with its summit cross for Triglav and the Aljaž turret; the lake turquoise, paler over the shallows; the church's tower under an onion dome of green copper, the nave under dark shingles; on the far shore, which runs level along its meadow there, a farmhouse white below and timbered above with its balcony (geraniums in summer, its windows lit at night, smoke from its chimney morning and evening), a Harpfe with hay on it from June to October, a boathouse on its piles and a jetty with a rowing boat, all mirrored in the lake; the Almhütte of logs on a low footing, its gable boarded, stones on its shingles, a trough with its spout by the door (frozen in winter); Fleckvieh cows, white heads and gold-red patches. The stone bridge, the wall, the chamois, the ibex and the marmot are the art's own: Carinthia has them too |
| `kaernten` | `campfire` (Am Feuer) | `KAERNTEN_CAMPFIRE`: the camp tent's pennant in Austria's red, white and red (Slovenia's tricolour before). The village round the fire is `FireStyle`'s ([Ob ognju by the age](#ob-ognju-by-the-age)): white walls with a timbered upper storey and balconies under dark shingles, the onion dome, the linden; the camp's spruces are Carinthia's woods too |
| `lakeland` | `alps` (On the fell) | `LAKELAND_ALPS`: rounded fells for the Julian Alps, their walled fields low down (a patchwork of greens, a dry-stone wall round each), the open fell above (grass, bracken green in summer and russet from October, heather in flower in August and September), grey crags with their scree, the summit's cairn, oakwoods at their feet, snow only on their tops in winter and in patches below; the lake dark as slate with a wooded island; the chapel whitewashed under slate, its tower battlemented; the packhorse bridge humped; the shepherd's hut of slate stone, its round chimney, its white window frame; Daisy the one cow (an Ayrshire, lyre horns) among six Herdwicks and Sooty the black lamb, her friends from the farm below coming up over the brow when a dialog counts the cows (`cows`: none of the art's own three are there before); Meg the sheepdog lying by the wall; the wall of slate stone; ravens off the crag for the choughs (`birds`); a feral goat and her kid for the chamois, a Herdwick ram for the ibex; a rabbit by its burrow for the marmot (its poke silent: `Sfx.poke` reads the look); buttercups, clover, harebells and primroses for the alpine flowers, bracken and foxgloves close by; oaks and birches for the spruces at the pasture's sides (the framing spruce, the `spruce` fir-tree, stays) |
| `lakeland` | `campfire` (By the fire) | `LAKELAND_CAMPFIRE`: the camp tent's pennant a St George's cross, red on white (Slovenia's tricolour before). The village round the fire is `FireStyle`'s: grey stone cottages under slate, white window frames, the square tower with its battlements, the oak for the linden; the camp's ferns are bracken, rusting in autumn |
| `friuli` | `kitchen` | Nonna Rosa's Friulian kitchen (`FRIULI_KITCHEN`): the fogolâr in the back corner at every level, a knee-high hearth of grey stone with the fire between two andirons (the `hearth`, a thing of the kitchen at every level there: `fixtures`), and over the fire on its chain the copper cauldron for the polenta (the cjaldîr, the `pot`; a wooden stick in it, polenta in it in the evening and while the fire is built up; tapped, it sways and puffs, then the polenta plops); at level 1 the chain hangs from a pole across the sooty corner, logs lie stacked beside the hearth and the round polenta board (taulîr) hangs on the wall; from level 2 the fogolâr's white hood rises over it to the walls' top, a shelf round its foot with copper pans, the polenta board and a plate on it and a lace edging under it, a second bench runs along the left wall, and beside the hearth stands the spolert, the iron range in cream enamel with its oven, its fire door glowing and a blue coffee pot on its plate, its pipe up into the hood (the `stove`, il fornello); Zia Nives's radio on a shelf in the front corner at every level, from level 3 the corner (`corner`, l'angolo) with a lace edging, a vase and the family's photo, no holy corner; the walnut dresser with plain panels; an ochre dado and a frieze of vines and grapes under the top; the Collio through the window, its rows of vines, a cypress and a church tower on the far hill; from level 2 the oil lamp (lum) hanging from the hood's shelf, lit from dusk, instead of the petroleum lamp over the table (which hid the fogolâr) |
| `friuli` | `smithy` | Fabbro Bruno's smithy (`FRIULI_SMITHY`): walls of pale river cobbles in courses with bands of brick through them, as the houses of the Friulian plain and the Isonzo are built; a wrought-iron grille in the window, bars with scrolls in their bays; his black notebook of orders on the window's sill with a pencil on it (the orders from Gorizia he dictates); the moka warming on the forge's stone top, steaming in the morning |
| `friuli` | `tent` | the tent at Friuli's camp (`FRIULI_TENT`): Friuli's pennant, the golden eagle on blue, flying from the front post's top and rippling more in a wind; a pair of scarpets, the Friulian velvet slippers with a flower embroidered on each toe, on the floor by the camp bed; a row of vines on stakes and wire along the meadow's back edge, green with grapes from July, gold in autumn, bare canes in winter |
| `friuli` | `mill` | Mugnaio Franco's mill (`FRIULI_MILL`): it grinds maize for the polenta, golden kernels in the hopper, the stream poured into it and the miller's measure, the meal yellow in its sack, its stream and its spill (the cloud of dust stays white, as Lorenzo says); bunches of maize cobs hung up to dry from the gallery's edge and on a nail in the right wall; the Collio through the window |
| `friuli` | `school` | an old Italian classroom (`CultureLooks.FRIULI_SCHOOL`; `SchoolLooksTest`): the walls pale yellow over a lower wall painted in a sea-green enamel (material `wainscot` "painted"), a vine with bunches of grapes in the frieze (`frieze` "vine"), the map of Friuli Venezia Giulia with Udine, the Tagliamento, the Isonzo and the lagoons, its title FRIULI (`map` "friuli"), the Italian tricolore on the pole (`flag` "it"), the pallottoliere where the basket stands (`abacus`), and "Ciao! Come va? Bene!" on the board before the first lesson (`greeting` "it") |
| `friuli` | `watchtower` (Sulla torre) | `CultureLooks.FRIULI_WATCHTOWER` (`FriuliPlacesLooksTest`): the village below in Friuli's ochre, terracotta and cream plaster under coppi, its huts of stone (on Teo's coast, land `coast`, the lagoon's casoni thatched with reed), the free-standing brick campanile with its belfry and low pyramid cap among the roofs (`village` "friuli"), vine rows on the plateau's fields, the Isonzo emerald in the valley; Friuli's flag, the golden eagle spread on blue, its pennant blue with the eagle's gold at the hoist below the fourth level (`flag` "friuli"); the tower's top (`tower` "stone") under tavelle, flat terracotta tiles between the rafters, from its second level, on pillars of river cobbles with bands of brick and a brick arch from each to the eave from its third, behind an iron railing (la ringhiera: bars, a scroll in every third bay) from its fourth |
| `friuli` | `livingroom` (In salotto) | `CultureLooks.FRIULI_LIVINGROOM` (`FriuliPlacesLooksTest`): Nonno Bepi's room by the fogolâr (`stove` "fogolar"): for the kmečka peč a knee-high hearth of grey stones in the corner, its niche of firewood, the fire on its top between two andirons (the `stove` effect builds it up and lights the benches), the benches round it as the zapeček ran (Moro curled up on them), in October to December the chestnut pan over the fire with as many chestnuts as a dialog counts; at level 1 the corner over it black with smoke, from level 2 its white hood up to the walls' top (the kitchen's `fogolarHood`) with a shelf round its foot, two copper pans, a plate and a jug on a lace edging; terracotta tiles for the planks, the walnut cassapanca for the painted chest (`cupboard` "walnut"), an ochre dado under the vine frieze, the Collio through the window |
| `friuli` | `cellar` (In cantina) | `CultureLooks.FRIULI_CELLAR`: Vignaiolo Matteo's cantina: walls of river cobbles in lime with bands of brick (`walls` "cobbles", `RoomPainter.cobbleWall`), the Ribolla gialla's golden-green grapes in the crate (`grapes` "ribolla"), three salami on a string beside the San Daniele ham from level 3 (`salami`), the Collio through the slit; from level 2 the frasca outside (`frasca`): a pole out from the house's front corner over the path, a bundle of green branches on a cord stirring in the wind, dry and brown from December to February |
| `friuli` | `attic` (In soffitta) | `CultureLooks.FRIULI_ATTIC`: Marisa and Tommaso's soffitta under coppi (`roof` "coppi"): their curved undersides in channels between the battens at level 1, the tavelle on the rafters for the boards from 2; three bunches of maize cobs drying from the ridge beam, five a bunch (`maize`); the scarpets by the bed (`slippers`); the wardrobe and the chest of walnut; the Collio through the gable window |
| `friuli` | `workshop` (In bottega) | `CultureLooks.FRIULI_WORKSHOP`: Mugnaio Franco's bottega of river cobbles with bands of brick (`walls` "cobbles"); a Friulian chair with its seat of woven rush hanging on two pegs high on the left wall and a wicker basket of six turned chair legs by the broom (`chairs`: Manzano, the chair country by the Natisone); the Collio through the window |
| `friuli` | `hills` (the vinograd project's vineyard) | `CultureLooks.FRIULI_HILLS`: the Collio from the Italian side: the farmstead in Friuli's ochre plaster under coppi, its shutters green, the frasca by its door from November to May (`farm` "friuli"); the church on the hill under coppi with a free-standing campanile of brick, its cornice and low cap (`church` "campanile"); the far farms in Friuli's plaster under coppi |
| `friuli` | `market` (Al mercato) | `CultureLooks.FRIULI_MARKET` (`FriuliMarketSquareLooksTest`): a market day in a Collio village. The houses round it in the fire's village's plaster (`village`: FireStyle's ochre, terracotta and cream) under coppi in channels, green shutters (blue on the coast); the free-standing brick campanile with its clock, its belfry's two round arches with the bells, stone cornices and the low pyramid cap with a cross, instead of the white tower and its spire; vine rows on the Collio's hill (`hills`); the lane and the square of river cobbles from the Isonzo, whitish, grey and ochre, with a band of brick across them every 3.2 m (`ground`, from the market's fourth level); the awnings in Friuli's colours (`awnings`: the village's own stall blue and gold, the flag's, its cloth's check and the jars' caps blue; the farther ones terracotta, green, ochre, blue on cream); the goods (`goods`): two wheels of Montasio with the stamp's dotted band, loaves and a gubana (the spiral of Cividale), Aldo's honeys side by side (pale acacia, dark chestnut), a San Daniele ham hanging by its trotter, two salami and a string of garlic at the stall's back; the crate by the season (white asparagus in bundles in April and May, greens in March and June, tomatoes in summer, a zucca in autumn, the rosa di Gorizia's red heads in winter), chestnuts in the basket in October and November; the farther stalls' scarpets (black velvet slippers with an embroidered flower) and cheese and salami, fish from the lagoon on ice on the coast; Matteo's bottles of Collio white (pale gold, a white label) and in September and October a crate of white grapes on his cart |
| `friuli` | `square` (In piazza) | `CultureLooks.FRIULI_SQUARE`: a Collio village's piazza. The church's front in its plaster under a low gable of coppi, the round window in its stone ring, the door in its portal under a lunette; beside it, free-standing, the brick campanile (its courses closer up), the clock as far as the town clock has come (the slit, the round window, the face), the belfry's arch with the bell (it swings and rings, tapped and on cue), cornices and the low pyramid cap with a cross (`village`); the houses in FireStyle's plaster under coppi with their shutters, the nearest Marisa's osteria (`osteria`): a cream sign over its door with a bunch of grapes and a glass of wine, the frasca hung out beside it on an iron bracket (leaves by the season), a little table and two chairs by the door from April to October; the well (`well`): a well-head of white Istrian stone with a moulding at its foot and a rim at its top under the forked pole at WELL's second level, a wrought-iron arch with the pulley and a copper bucket from the third, the carved band of rosettes, the stone step and the scrolls from the fourth, two pots of geraniums on the step at the fifth (with the lamp post); the panchina, green slats on black iron sides (`bench`); the cottage behind the garden in plaster under coppi; vine rows on the hills (`hills`); the maypole's ribbons blue, gold, green and red (no Slovene tricolour). The linden (il tiglio) is the art's own |
| `friuli` | `apiary` (Dalle api) | `CultureLooks.FRIULI_APIARY` (`FriuliApiaryChurchLooksTest`): not the Slovene bee house with its painted fronts (the končnice are Slovene) but Apicoltore Aldo's apiary on the Collio: a shed of river cobbles with bands of brick and brick quoins under a roof of coppi (`house`), and in its open front Dadant box hives in tiers (one at levels 1–2, two from 3), each painted a plain colour (white, yellow, light blue, green), a tin lid on top (snow on it in winter), its entrance and landing board at its foot, its number painted on it closer up, a seam where a super sits on some; the hive apart (`hive`) a light blue box hive with a white lid, the empty one Aldo keeps for a swarm; the `panel` is Aldo's sign by the path, a yellow board on two posts with MIELE in brown (tapped, it comes up close: MIELE with a honey pot and a bee, ACACIA with its white racemes, CASTAGNO with a leaf and a burr); an acacia, the robinia of the Collio, where the linden stands (`tree`): a rough grey furrowed trunk leaning a little, an airy crown of small yellow-green leaves, heavy with white hanging racemes in May, fresh and thin in April, yellow in October, thin in November, bare with its dark pods in winter; pale acacia honey in the jars, and from September to November one jar of dark chestnut honey among them (`honey`); World Bee Day's bunting (20–22 May) in Friuli's yellow and blue with white, not the Slovene colours (`bunting`). |
| `friuli` | `church` (In chiesa) | `CultureLooks.FRIULI_CHURCH`: the little church of a Collio village, not the Karst church: the far wall a pitched gable under an open timber roof, from level 3 its king-post truss, struts and rafters with the roof boards over them (`roof`), where the Slovene church has its starry vault; bullseye glass in lead, the Venetian rulli (`glass`); a floor of terracotta tiles, cotto (`floor`); a Venetian altarpiece, the Madonna enthroned in blue between two saints (one in a red cloak, one in a brown habit) in a golden apse, framed from 4 in an aedicule of white Istrian stone and from 5 its pediment with a gilt dove (`altarpiece`, for the marble columns and the sunburst); faded frescoes of two haloed saints on the far wall from 4 (for the consecration crosses), a grey plinth for the marbled dado, a band of red teeth on ochre for the wave frieze (`walls`); the bell gable over the door a campanile a vela of the Collio's layered ponca, its arch edged in brick, a cap of coppi (`gable`); the organ loft's balustrade painted as marble (`loft`) for the Slovene tulips; the bell rope's woollen grip in Friuli's yellow and blue (`rope`) for the white, blue and red. |
| `friuli` | `firestation` | `FRIULI_FIRESTATION`: the tower and the hall's outsides of river cobbles in courses with bands of brick (material `walls` "cobbles"), the tower's roof of coppi (`roof` "coppi"), the sign VVF (vigili del fuoco, `sign` "vvf") over the doors instead of PGD, helmets of polished steel (`helmet` "chrome"), the picture of Santa Barbara, the Italian firemen's patron, with her tower (`picture` "barbara") instead of St Florian, the banner the tricolore (`flag` "it") |
| `friuli` | `bocce` | `FRIULI_BOCCE`: an osteria in ochre under coppi (`inn` "osteria"), its shutters brown, the frasca, a leafy branch hung over the door of an osteria with its own wine open (`frasca`), instead of the painted sign |
| `friuli` | `playground` | `FRIULI_PLAYGROUND`: the school's wall pale yellow over a green dado, as Maestra Elena's classroom (`school` "friuli"), and a fontanella by the fence, the green cast-iron drinking fountain of the Italian towns (`fontanella`) |
| `friuli` | `chapel` | `FRIULI_CHAPEL`: an ancona (`chapel` "ancona"): its walls ochre, its roof of coppi, no turret but the bell on an iron bracket under the gable, the cross on its apex, inside a fresco of the Madonna and Child on a blue ground with stars; maize in the field (`field` "maize"), Lombardy poplars along a far ditch (`trees`), the village on the hill with its campanile (`village`) |
| `friuli` | `palisade` | `FRIULI_PALISADE`: the land beyond the gate is Friuli's (`land` "friuli"): Lombardy poplars along the road, the vines strung between pollarded mulberries on the meadow, the campanile of a village on the far hill |
| `friuli` | `lookout` | `FRIULI_LOOKOUT`: the view of Friuli (`view` "friuli"): the Collio's hills striped with vines, the plain with its rows of poplars, the village as Friuli builds it with its campanile, the lagoon and the sea at Grado wider on the right, the flag the golden eagle of Friuli |

### Where people stand

Someone at a person spot of an art (a scene's own people, and the child of the village who comes by for [I
spy](#i-spy-vidim-vidim-nekaj-česar-ti-ne-vidiš)) stands clear: nothing of the picture is drawn over their head and
shoulders. A painter draws them after what stands behind them and before what stands in front of them, in its own order:
in the church the organ loft, its turned posts and its balustrade go before whoever is by the door, the bell's rope after
them, hanging to their left (until 2026-10-16 the loft came after them, and its front post ran down through Tine, or
the child who came by in his place, from the head to the belt); in the attic the lamp, hanging from the ridge behind them,
goes before whoever stands by the bed, the chest or the stairs. Something stands in front of someone by design only below
their shoulders (the pews' ends at the altar's spot, a chair, the wheelbarrow), or where the spot is behind it: Jože up
in the high seat behind its rail, Zala behind the honey table at the bee house. `PeopleClearTest` checks it at every
person spot of every art, with each of the village's sprites, by day and at night, built up and not yet, every thing and
wild animal out (`ArtPainter.keepPeople`: what the painting drew over each person after them; the effects, the light and
the weather over the picture left out); `build/scene-snapshots/people-clear`. QA's `scene-people` opens the church, the
kitchen and the smithy with I spy offered and takes their pictures, as they open and closer up.

### The school's blackboard

Jan, 2026-10-14: "I like the chalkboard; maybe it could show what we're currently / last learned in school." The school's
blackboard (`v-soli`, art `school`) has the learner's lesson on it in chalk (`game/scene/Blackboard.kt`, pure; the
painter's `SchoolPainter.chalkLesson` in the hand of `BoardFont`): the unit worked on last ([GAME.md](GAME.md) "The
school", [COURSE.md](COURSE.md)), of the units with progress the one with the latest day in it (begun, an intro step, a
sheet, the test, done; on the same day one under way before one done), its title in the course's language and up to four
of its forms from the intro's table, each row's words in that language, each cell's first form ("on, ona, ono" → "on"):

```
Biti
jaz sem · ti si
on je · mi smo
```

The board holds three lines of about twelve letters (49 columns of the scene's canvas), so a title too long for the first
line is its head before ": " ("Biti: sem, si, je" → "Biti"), else over two lines at its commas ("Moški, ženski," /
"srednji spol"), else its head before the first "?" ("Kje? V hiši, na vrtu, pri babici" → "Kje?"); the forms follow, " · "
between two on a line, a form too long its last cell ("imenovati se imenujem se" → "imenujem se"); a row of three or more
keeps its first two cells without the endings ("jaz -m delam" → "jaz delam"), a sentence and its change the change ("Ne
delam."). Before the first unit: "Dobrodošli!" and the day ("14. 10. 2026"); the board of an Italian course
(the Friuli village's) "Benvenuti!" and the day as Italy writes it ("28/10/2026"), its lesson in Italian (`Blackboard.language`). Without a course, on a visit, in the tools
and the tests (no board in the frame's world: `SceneWorld.board`, set by `AppViewModel.sceneWorld` for the school) it is
the school's own greeting, "Dober dan! / Kako si? / Dobro." and the chalk sun ("Ciao! / Come va? / Bene!" in the Friuli
look).

The hand: letters 3 px wide (m, w, a few capitals 4 or 5), the small ones 4 rows high, capitals and tall letters 6, a row
below for g, j, p, y; č, š, ž and the other Latin letters with a mark are their letter and the mark over it; "…" three
dots on the line (a title's or a cell's: "Če bi deževalo, bi …", "si ga …?"). On the
board's slant each letter's right part is a row lower (the s drawn slanted already), each letter a little up or down and
the lines wavering, the chalk worn here and there, the title underlined, and the board hazy with the lessons wiped off
it. Closer up the same letters stand in the same places, a chalk line a picture pixel thin from each of their pixels to
the next. `SchoolBoardTest` checks which unit it shows, that every letter of the course is written and every unit's
lesson fits, and the board's chalk at every detail (nothing else of the picture changes), and renders the board for
review (`build/scene-snapshots/school/board-*`). The class under the linden's easel on the village's map stays a word's
scribble: at the map's closest it is a dozen pixels across.
