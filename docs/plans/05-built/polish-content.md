# Built: content that was wrong or thin (polish-content)

*Built 2026-11-01, branch `polish-content` (the completing round, wave A, part 8). Jan: "can you make app complete - all
what is missing?"; "for choices - make it the best - prod ready". Eight loose ends of content from the rounds before, for
Jan (Slovene from English, an adult, a phone, Primorska) and the children's villages (Teo: Italian from German, Friuli;
Carinthia's German; the Lake District's English). Every text written here is machine-written; the native-check list is at
the end. A spoken form is never wrong: nothing here grades one.*

## What, item by item

### 1. A request's line names its giver in the base half too

**Before.** The four cultures' `quests.json` wrote `lines.asks` with the giver in the target half only: "Zia Nives ti
chiede aiuto · bittet dich um Hilfe", "Babica Micka te prosi za pomoč · asks for your help". The news sheet's 🔊 reads each
half of a chronicle line alone (ux-polish.md), so it said "bittet dich um Hilfe" with no one asking; the screen showed a
fragment too.

**Now** every language of `asks` names `{giver}`: "Zia Nives ti chiede aiuto · Zia Nives bittet dich um Hilfe", "Babica
Micka te prosi za pomoč · Babica Micka asks for your help". The other lines (`expired`, `thanked`, `try_again`) are whole
sentences in the base already ("Thank you so much!", "too late, the request expired") and keep the giver only in the
target half, as the speaker's name before a colon: adding it to both halves would only say the name twice. GAME.md's
`quests.json` row says why `asks` differs. The culture parity fixture (`culture/parity.json`) and `TentMoveTest` take
the new line.

### 2. The stories

**Before.** The reading of the 46 curated stories for their faces (ux-polish.md, "For the native check") found problems
in the texts and changed none: Primorska's forms said to the learner without `{m:|f:}`, Stari Janez's "{m:Fant|f:Dekle}, …"
(he never says it), lines that fit only a learner called Jan or held a stage direction, levels telling different stories,
le agane's question asked by the girl at A1 and her sister at A2, Kärnten's stories asking a child about their childhood,
an A1 telling slipping into the past, a ghost's punishment after death, and Opa Sepp's generic "Der? Hm, wie bitte?".

**Now** every item is fixed, in 22 story files, each change in every language of its line and in the notebook's notes:

- **Primorska (Stari Janez).** The learner's gender everywhere it was missing: bogatinovo-zlato B1 ("…ne bi
  {m:približal|f:približala}", "Kaj pa bi ti {m:naredil|f:naredila}, če bi {m:našel|f:našla} …?"), desetnica A2 ("Lepo si
  {m:povedal|f:povedala}."), kekec A1 ("{m:Predrzen|f:Predrzna} si kot Kekec!"), kralj-matjaz B1, pegam-in-lambergar B1,
  povodni-moz A2, veronika-deseniska chapters 1 and 2 (the lines, the choices "{m:Zapomnil|f:Zapomnila} sem (si) …", the
  reply and the why that quote them), zlatorog B1, zmaj-ljubljanski B1, zvon-zelja A2; the Italian's agreement with them
  too. The bare "{m:Fant|f:Dekle}, …" is gone from the four replies; they answer as Janez does ("Dober večer? Saj sva tu že
  celo uro!", "Se bo zgodilo? Jaz sem pripovedovalec, ne vedeževalec!"). Pegam's "Jan? Upam, da nisva v sorodu." is
  "Češki vojskovodja? Upam, da ga nocoj ne srečava." (the dual kept, Janez and the learner; the wrong "srečamo"); Kozjak's
  "Oho, tikaš? Janez se samo nasmehne." is his own words, "Oho, tikaš me? Ha, nocoj ti oprostim."; Matjaž's second moral
  is an evening's end ("Ogenj ugaša, Muri že spi. …"); Veronika rests in Jurklošter at every level ("V Jurkloštru je
  Veronika končno našla mir."); Kekec's telling says she was often sad before it asks why. The levels agree: Peter Klepec
  drives the Turks back over the river at A1 too (the Kolpa; his picture now at the river), Pegam has three heads the third
  time at A1 as at A2 and B1, Jurij finds a way under the ground into the monastery at A1 (A2's tunnel, told simpler).
  zmaj-ljubljanski B1: "Hm, kako, prosim?" has its comma; "Bi mi povedali vi?" is kept (the learner says vi to Janez, and
  the vi answers "nihče mi ni znal povedati"), for the native check.
- **Friuli (Nonno Bepi).** le agane: the sister asks the forbidden question at A1 too, as at A2.
- **Kärnten (Opa Sepp).** die-perchtl A2: "Fürchtest du dich auch vor etwas?" for a child; der-dobratsch A1 tells 1348 in
  the present ("Es ist das Jahr 1348 …"); das-kasermandl A2: no punishment after death, "Darum musste er von da an jeden
  Winter allein auf der Alm arbeiten." The generic reactions take the slip at face value, as Bepi's and Wilf's do: "Der
  Burg? Hat sie jetzt einen Bart, wie ein Mann? Ha!", "Im elften November? Gibt es denn elf November im Jahr? Ich kenne nur
  einen!", "Vor das Gewitter? Willst du dich vor das Gewitter stellen? Lieber nicht!" (the ten named and five more like them
  in the same stories), each with its face and its sl, en and it.
- **The Lake District (Old Wilf).** The giant is "taller than two men" at both levels (his grave about fifteen feet long);
  the hob's "Did you help out today?" names nobody the learner may not have met (and its reaction no longer names Granny
  Maggie).

Checked: `check-culture` for the four cultures, `learner-content.ts` (0 texts naming Jan, 0 malformed placeholders, 0 turns
the same for one gender), `DialogReactionsTest` (no reaction says what sets the right choice apart, none over 80
characters) and the stories' tests in the full suite. Each file's `review` notes its 2026-11 edits as machine-written.
Settled against fran.si: *predrzen, predrzna* (https://fran.si/iskanje?View=1&Query=predrzen), *v Jurkloštru*
(https://fran.si/iskanje?View=1&Query=Jurklo%C5%A1ter).

### 3. Nejc at the gate

**Before.** Jože's evening at the gate (`pri-vratih/zapah`) had three variants; one is Nejc's ("{learner}! Jože! Gremo v
gozd! Ponoči je tam super!"). The happening had no `with`, so Nejc spoke from off the picture: his portrait in the panel,
nobody on the bench (scene-speakers.md, "What's left").

**Now** Nejc's variant is a happening of its own, `zapah-nejc`: Jože's (he answers every turn, as before), `with` Nejc,
who sits on his spot, the bench by the gate (the palisade art places him there, as in his own happenings, the pedlar and
the bell). It is on in the evening only (a child is in bed at night; `zapah` keeps the evening and the night), at the
chance of 0.5, listed after `zapah`, so the gate has one of the two an evening, Jože being one person: Nejc's comes on
about a quarter of evenings (before, in turn with `zapah`'s two other variants). Its title is new ("Nejc bi rad šel ponoči v gozd · Nejc wants to
go into the forest at night"), its memory the variant's. Every happening has two variants at least (`VariantsContentTest`),
so it has a second, new: `zapah-nejc-straza`, Nejc wants to keep watch at the gate tonight; the learner says he can stay
"do prve zvezde" (do + the genitive, `rodilnik-predlogi`; the wrong "do prva zvezda"), he yawns at the first star, and the
learner walks him home: "Nejc je zaspan" (`pridevniki-ujemanje`; the wrong "zaspana"). The older one comes last, as
everywhere: Jan has heard it. Why not a `with` on `zapah` itself: it would bring Nejc into the
wolves' and the plain variants too, make the whole happening wait for him at night, and hold the gate's other happenings
back as a lesson does.

QA's `scene-replier` and `scene-speakers` steps and `DialogCastTest` open the dialog by its new happening.

### 4. Each culture's buildings ask for their own scenes' packs

**Before.** `BuildingWords.PACKS` gave Friuli's, Carinthia's and the Lake District's buildings the language's starter
packs of what each is for (`cibo`, `essen`, `food` for the field, the hayrack, the bee house and the market; the family
for the house …), written before those cultures had rooms. A building with its room asks for the room's things first and
the room's pack after them, so the starter packs came in at the higher levels: past its scene's words, a Carinthian bee
house's level 5 asked for das Brot and die Milch.

**Now** each culture's building asks for its own scene's pack, as Primorska's always did: the tent's, the field's (the
hayrack's too, at its edge), the square's for the well and the linden, the kitchen's for the hut, the bee house's, the
watchtower's, the smithy's, the church's, the school's and the market's; a house whose room nobody lives in yet the living
room's; the palisade the gate's, in Friuli the forest's (as Primorska's, whose stakes are its trees). A level that asks for
more words than the building has then asks for all there are, as in Primorska. GAME.md has the table.

| Building | Friuli | Kärnten | Lakeland |
|---|---|---|---|
| Tent | nella-tenda | im-zelt | in-the-tent |
| Field, hayrack | nel-campo | auf-dem-feld | in-the-field |
| Well, linden | in-piazza | auf-dem-dorfplatz | on-the-green |
| Hut | in-cucina | in-der-kueche | in-the-kitchen |
| Bee house | dalle-api | beim-bienenhaus | at-the-bees |
| Palisade | nel-bosco | am-tor | at-the-gate |
| Watchtower | sulla-torre | auf-dem-wachturm | on-the-watchtower |
| Smithy | nella-fucina | in-der-schmiede | in-the-smithy |
| House | in-salotto | in-der-stube | in-the-living-room |
| Church | in-chiesa | in-der-kirche | in-church |
| School | a-scuola | in-der-schule | at-school |
| Market | al-mercato | auf-dem-markt | at-the-market |

`BuildingWordsTest` checks every culture's pack for every building: its own, in its language, no festival's, at least 8
words.

### 5. The day's pedlar carries his culture's load

**Before.** The pedlar of the day's surprise (`People.kt`'s `pedlar`, the village map's figures, his portrait) was the
krošnjar from Ribnica under his krošnja in every culture. Only the road's pedlar of Friuli's gate and watchtower had the
cramâr's crassigne (culture-source.md).

**Now** he is his culture's, everywhere he is drawn: the scenes' sprite, his portrait, the map's 14 px and 7 px figures,
and the road's pedlar at the gate and below the watchtower.

| Culture | Who | What he carries | Clothes |
|---|---|---|---|
| Primorska | the krošnjar from Ribnica | the krošnja: a frame stacked with suha roba, a sieve, spoons, a ladle | white shirt, red neckerchief, black hat (as before) |
| Friuli | the cramâr from Carnia | the crassigne: a walnut case of little drawers, a brass knob on each, the lid's ledge | brown jacket, black hat |
| Kärnten | the Kraxenträger from the Kanaltal | the Kraxe: larch posts up past his hat round a box of little drawers painted green, a red flower on the top one ("So viele kleine Laden!"), a blue linen bundle tied on its lid | loden-green jacket, breeches and boots, a green felt hat |
| Lakeland | the packman from Kendal | his big pack ("a big pack on his back"): brown canvas strapped round, a bolt of Kendal green cloth along its top, a red ribbon's end | brown tweed coat, a flat cap, boots |

How: the culture's look says it (`people.pedlar` in `CultureLooks`: "cramar", "kraxe", "packman"; the gate's and the
watchtower's `pedlar` the same), `Looks.pedlarPack(culture)` reads it, and the sprite painters know their culture: a
scene's from its frame's look (`ArtPainter` sets `PeoplePainter.culture`), the map's from its `MapLook` (and
`Effects.lookOf(art, seed, culture)`), a portrait from the culture on screen (`PortraitsPainter.culture`, a visited
town's on a visit). The stranger's `art` stays `pedlar` in the content, so an older app draws the krošnjar as before.
`PedlarsTest` draws each and keeps them apart; its sheet is `build/scene-snapshots/pedlars/pedlars.png`.

### 6. The print's rule line keeps each step's point

**Before.** A step of a unit's intro prints on its sheet as its first sentence and the next ones with forms, about 200
characters (`condense`, course-it-a2a.md). A step whose forms all came after a first sentence without one, in sentences
too long to keep, printed without its point: "Two more." for kjer and oziroma, "A clause can come first.", "Zwei Tipps zum
Schluss.". No check said so.

**Now** the smoke's prints section reads every unit of the repository in every base its texts have (the sheets print in
each but the course's own) and fails on a step that shows forms (bold) but prints none in its line. It uses the sheet's
own `ruleLine` (`prints/sheet.ts`), which `buildSheet` calls. It found 11 steps in 26 step-bases, each rewritten so its
first sentence carries a form or the sentence with the forms fits; nothing a step said was dropped, the Slovene examples
are as they were:

| Unit, step | Bases | The line before | The line now |
|---|---|---|---|
| sl/dolocna-oblika 1 | en, de, it | A masculine adjective has two base forms. | … two base forms: **zelen** answers **kakšen?** (what kind?), **zeleni** answers **kateri?** (which one?). |
| sl/mnozina-skloni 1 | en, de, it | In the plural a noun changes with its case, as in the singular. | … as in the singular: **stoli** (chairs), but **Vidim stole.** |
| sl/naslonke-red 1 | en, de, it | You know the short words (*naslonke*) gather after the first word or phrase. | When several short words (*naslonke*) meet after the first word or phrase, their order is fixed: **sem, si … bi**, then **se, si** … |
| sl/nikalnica-rodilnik 5 | it | Lo sloveno nega due volte, come l'italiano. | Lo sloveno nega due volte. Con **nič** (niente), **nihče** (nessuno) … come in italiano: **Ne vidim nič. …** |
| sl/odvisniki 2 | en, de, it | A clause can come first. | A clause can come first: it takes the first place, and the main sentence goes on with its short words: **Ker je deževalo, smo ostali doma.** … |
| sl/sklanjatev-posebnosti 1 | en, de, it | Some nouns grow a sound … but the base. | … but the base: **oče → očeta**. |
| sl/stevila-glagol 1 | en, de, it | You know the noun after a number. | After a number the verb goes with it too, as the noun does: **1** takes the singular, **2** the dual … |
| sl/vezniki 5 | en, de, it | Two more. | Two more: **kjer** (where) joins a place to what happens there, **oziroma** (or rather) says it more exactly. |
| sl/vid-vsi-casi 1 | it | Conosci le coppie dal presente: … | Al passato come al presente: **Janez je jedel kosilo** (stava pranzando), **Janez je pojedel kosilo** (l'ha mangiato tutto). |
| it/aggettivi 6 | de, sl, en | Zwei Tipps zum Schluss. | Ein Junge und ein Mädchen zusammen sind männlich (**stanchi**), und **bianco**, **stanco** bekommen bei mehreren ein h. |
| it/ciao 3 | de | Zu Erwachsenen, die nicht deine Familie sind, sagst du nicht ciao. | … nicht ciao. Morgens und nachmittags sagst du **buongiorno** (guten Tag), abends **buonasera** … |

Every sheet keeps its page count (`school-prints` in every base, before and after: the two-page ones stay two,
the one-page ones one); aggettivi's English and vid-vsi-casi's Italian were shortened until they did. COURSE.md says how
to write a step so the check passes (a form in the first sentence; a bold run of example sentences ended before its last
full stop, `**… ne spi**.`, so the sentence after it is one of its own).

### 7. The Lake District's form turns can be typed

**Before.** About 47 of the Lake District's form turns (places-lakeland.md said; counted the way the app decides, 54 of
240 without a count) could only be chosen: their wrong choice adds, drops or moves a word ("I can to hear", "Why they are
…") or splits a contraction ("It are" for "It's"), and `Forms.turn` asks for typing only a turn whose wrong choice swaps
one word in its place.

**Now** 34 more can be typed once their rule is secure (220 of 240 by the app's own decision, ported to
`scratchpad/polish-content/helper-lk/kt.py` for the count; the 10 count turns with `{n}` were typed already, since
`Numbers.render` flips their `{!…}` form before `Forms.turn` runs). Two ways, never changing what a turn teaches (its
`grammar` page, its `why` true for the new wording in en, sl, it and de, its reaction taking the slip at face value):

- **A one-word slip on the same rule**, added as a third choice (the scene's own slip stays for choosing) or in place of
  one: can + -ing ("Yes, I can hearing them", "What can I doing?", "Can midges biting …"), be for do ("Why are you bring
  rushes?", "Which cake are you like?"), "Who sleep in it now?", "What is hush mean?", "Do you got a match?", "I don't
  got one", "How long is it been there?", "next of the barrel", "A fish jump out", "I not afraid", "Yes, it raining.",
  "I coming, Mr Tyson!" (23 turns).
- **The right choice in full** where a contraction was the slip's only partner: "Now we are safe." (the wrong "Now we is
  safe."), "There is a hole in his ear!", "It is beautiful, Auntie Edna!", "I have never seen a raven." (11 turns; the
  why names both, "we are (we're) safe"; the English grading counts a contraction typed as its long form). These 11
  right choices need new clips.

One turn outside the list (at-the-gate's flock-out) had a meaning slip taking the gap: its choices' order now lets the form
be the gap. **Left chosen (18)**, each because no real one-word slip teaches the same point: a moved word ("Is it of oak
made?", "Here are you", "light always it", "What colour they are?", "win usually", "like very much it", "Why they are",
"It is very high?", "Where she is?", "What I can do?" twice); a dropped article (four: the one-word slips are a/an or a/the,
other points or correct English); the missing "to" of going to (two); "I don't can see". Two more keep a meaning slip in
the same word as the grammar's ("can't"/"cans", "sheeps"/"ships"), so by design they stay chosen.

### 8. A fox and a badger in the Lakes

**Before.** The field's boar at night and the wolves a dialog sees by the wood at the gate and from the watchtower were
the game's wild animals in every culture; the Lakes have had neither for centuries (places-lakeland.md). The map's night
had boars and, rarely, a bear's eyes there too.

**Now**, in the Lake District's look:

- **The field** (In the field, `boar` "badger"): a badger digs for worms at the potatoes' edge at night where the wild
  boar digs elsewhere; tapped, it shuffles off along the rows and back. Its word is `badger` (the field's pack: jazbec,
  il tasso, der Dachs, 🦡, "At night the badger digs for worms."), its I spy clues a badger's ("It's grey, black and
  white.", "It has stripes on its face.", "It's shy.", "At night it digs for worms.").
- **The gate and the watchtower** (`wolves` "foxes"): what a dialog sees by the wood is foxes, their eyes amber at night,
  red foxes trotting along the wood by day, a white tip to each brush. The dialogs say so: Mr Dixon's "Are they foxes?"
  (the wrong "foxs", after x the plural takes -es), Poppy's "It's a fox! Ring the bell, quick!" (and "an fox"), the bell
  rung "Like a fox at the hens!", Jack's "Foxes. Three of them, waiting for a lamb" and his windy watch "while the foxes
  are out", Alfie's "Is it a fox?" in the tent; the wood's clue "Foxes live in it.", the palisade's "It keeps the foxes
  out.", the watchtower pack's "the wood where the foxes live".
- **The map** (`map.wild` "britain", `MapLook.absentWild`): no wild boar and no bear in the Lakes' woods at night; the
  badger, the fox, the owl and the hedgehog are about as anywhere.

The game's night threat stays the wolves in every culture (the village's wolves event, `events.json`, the map's wolves at
the palisade, the villagers' reactions, Mr Dixon who leads the defence): while that event is on, the gate's picture shows
wolves' eyes. Everything the scenes say and draw of their own is foxes now, the torches' "Foxes don't like fire." and the
palisade's clue "It keeps the foxes out." too. See "What's left".

## How to use it

Nothing to switch on: the content comes with the next app build (the stories, the scenes, the packs, the clues) and the
bridge reads the course's units as they are (the prints at once). The pedlar, the fox and the badger are drawn by the app.

- The news sheet: 🔊 right of "Medtem ko te ni bilo · While you were away": a request is "Babica Micka te prosi za pomoč ·
  Babica Micka asks for your help".
- Nejc at the gate: an evening, the gate's bubble 🌙 (Jože), Nejc on the bench by the gate.
- A building's upgrade words: the building's card, "⬆️ Nadgradi"; in Friuli, Carinthia and the Lakes the words are its
  scene's.
- The pedlar: the day's surprise "🎒" (his card, the road in the village, his portrait); at the gate and from the tower in
  the dialogs that see him coming.
- The prints: `companion/bin/school-prints --level <level> --base <base>` (and `--language it`); the smoke's prints
  section checks the rule lines.
- The Lakes' fox and badger: the field at night, the gate's and the tower's evening dialogs.

## Tap counts

Content only: no journey's taps changed.

| Journey | Before | After |
|---|---|---|
| Hear a request's line on the news sheet | 1 (🔊), "asks for your help" heard alone | 1 (🔊), "Babica Micka asks for your help" |
| Nejc at the gate, his line, his face in the picture | 2 (the bubble, the dialog), Nejc off the picture | 2, Nejc on the bench |
| A Carinthian building's upgrade words | the card, "Vadi jih zdaj" (2) | the same (2), the scene's words |
| A sheet's rule | print (1) | print (1), each step's point on it |
| A Lakeland form turn typed (its rule secure) | not possible (chosen only) | 0 more (the turn asks to type) |

## Checks

- Bridge: `bunx tsc --noEmit -p .` clean; the smoke (`LANI_SMOKE_PORT=9610 LANI_SMOKE_DRAMA3_PORT=9628 bun test/smoke.ts`)
  all 3,253 checks passed, the new one among them ("the repository's units print each step's point …: 1,329 steps, every
  base"); before the rewrites it named the 26 step-bases. `bun test/check-culture.ts` ok for all four cultures.
  `tutor-export --check`: 89 files match the bridge.
- Android, the full unit suite (`:app:testDebugUnitTest` through lani-slot): 3,637 tests, 2 skipped, 1 failed on the
  first run: `FireAgesTest`'s crowd spot 6 at a Trg and a Mesto, where something in front covers a few pixels of the
  Carinthian Kraxe's posts and the packman's pack, counted as the head. The test now counts a pedlar's head from his
  hat's crown down and between his load's sides (the load may go behind what is in front, as the stump's edge goes over a
  shoulder); `FireAgesTest` 15/15, and the stories', reactions' and variants' tests pass. New: `PedlarsTest` (5),
  `LakelandWildTest` (4).
- Python: `tests` 251 OK, `companion/tests` 138 OK (11 skipped).
- Prints: `school-prints` of every changed unit in every base, before and after: the page counts kept (section 6).
- Emulator, `companion/bin/qa-slot` (screenshots in `scratchpad/ux-polish-content/`):
  - Primorska (`--steps scene-speakers,polish-cues`): Nejc on the bench by the gate in his evening's dialog, the
    header on Jože, Nejc's lines in Nejc's voice (`speakers-primorska-*.png`); the krošnjar on the road
    (`pc-primorska-pedlar.png`). All steps ok.
  - The Lake District (a synthetic learner of English, `LANI_CULTURE=lakeland`): the packman on the road, Poppy's
    "Something red is moving!" with the fox, Mr Dixon's "Are they foxes?" with the amber eyes at the wood's edge, and the
    watch's first turn asked to be typed, "Yes, of course. I can ____ it." (`pc-lakeland-*.png`). The step ok; the run's
    cleanup after it couldn't find the village's Slovene-first labels in the phone's en · sl (the setup pair; the
    Carinthian rerun with `LANI_QA_PAIR=sl-en` passed whole).
  - Carinthia (`LANI_CULTURE=kaernten`, `LANI_QA_PAIR=sl-en`): Maxi's "Mit einer Riesenkraxe am Rücken!" with the
    Kraxenträger on the road (`pc-kaernten-*.png`). All steps ok.
  - On the phone's home view the road's pedlar is a few pixels at the far end of the road (as culture-source.md said of
    the cramâr); the renders `render-pedlars.png` (the four sprites and portraits), `render-pedlars-road.png` (the road's
    four pedlars at the gate and from the tower, four times bigger) and `render-lakeland-wild.png` (the badger, the foxes)
    show them closer. The "before" of each is Primorska's: the krošnjar, the boar, the wolves, as every culture drew them.

## What's left

- **The Lakes' night threat.** The game's wolves event (and the bear's) is the same in every culture: `events.json`'s
  "Wolves are circling the camp!", the map's grey wolves at the palisade, the villagers' reactions, Mr Dixon who leads the
  defence. In the Lakes it could be foxes at the hen house (and no bear), but that is the game's design: the event's
  sprites on the map, its texts, its reactions and the palisade's breach would all change with it. Not done.
- **Clips.** Every changed line, reply and explanation needs its clip (`voice-build`; the explanations with
  `--explanations`): the stories', the Lakeland scenes' (the 11 right choices in full among them), the course's rewritten
  steps, Nejc's new happening (its title, his watch's lines).
- **Turns that stay chosen.** 18 of the Lake District's form turns (section 7) and the two whose meaning slip shares the
  form's word: each needs a real one-word slip on its rule, or none exists.
- **The road's pedlar on a phone** is a few pixels at the road's end at the home view (as the cramâr was); the close-ups
  (the sprite on the day's surprise, his portrait) show him whole.
- **`OTHER_JANS`** in `bridge/test/learner-content.ts` still allows "^Jan\? " for the line pegam's no longer has: harmless,
  left.

## For the native check

Everything here is machine-written.

Slovene forms new here, settled against fran.si (SSKJ², SP2001): *jazbec* (m, a badger,
https://fran.si/iskanje?View=1&Query=jazbec), *deževnik* (m, the earthworm, https://fran.si/iskanje?View=1&Query=deževnik),
*enook, enooka* (one-eyed, https://fran.si/iskanje?View=1&Query=enook), *plah* (shy; of an animal SSKJ² marks it literary,
"plaha srna": https://fran.si/iskanje?View=1&Query=plah). Register and naturalness are for Jan's wife.

- **Requests** (`quests.json`, all four cultures): "{giver} asks for your help", "{giver} bittet dich um Hilfe",
  "{giver} ti chiede aiuto", "{giver} te prosi za pomoč" as the base half.
- **Nejc at the gate** (`pri-vratih`): the happening's title "Nejc bi rad šel ponoči v gozd" (de "Nejc will nachts in den
  Wald", it "Nejc vuole andare nel bosco di notte"); the new variant `zapah-nejc-straza` whole: "Nocoj bom jaz stražil pri
  vratih!", "Ti, Nejc? Straža je dolga. Do jutra.", "Lahko ostane do prve zvezde." (the wrong "do prva zvezda" and Jože's
  "Do prva zvezda? Hm, tako pa ne."), "Prav. Do prve zvezde, potem pa v posteljo.", "Juhu! Zdaj sem na straži!",
  "Aaah … Tam je že prva zvezda … Malo sem zaspan …", "Nejc je zaspan. Pospremim ga domov." (the wrong "zaspana", "Zaspana?
  Nejc je fant, ne deklica!"), "Hvala, {learner}. Jaz ostanem tu, pri vratih.", "Lahko noč, mali stražar.", its memory
  "večer, ko je Nejc hotel stražiti", and the en, de, it of each.
- **The course's rewritten steps** (en, de, it of the Slovene course; de, sl, en of the Italian one): the 11 steps in the
  table of section 6, in particular the German and Italian of naslonke-red 1 ("Treffen nach dem ersten Wort … aufeinander";
  "Quando dopo la prima parola … si incontrano più parole brevi") and the Slovene of aggettivi 6 ("Deček in deklica skupaj
  sta v moškem spolu (**stanchi**), **bianco** in **stanco** pa dobita v množini h.").
- **The badger** (the Lakes' field pack): jazbec, il tasso, der Dachs; "At night the badger digs for worms." and its
  translations ("Ponoči jazbec koplje in išče deževnike."); its note; the I spy clues' Slovene ("Je sive, črne in bele
  barve.", "Na obrazu ima proge.", "To je nekaj plahega.", "Ponoči koplje in išče deževnike."); the pack's description.
- **The Lakes' foxes**: Mr Dixon's "Aye. Two, three. After the hens, I reckon." ("Ja. Dve, tri. Po kokoši so prišle, se mi
  zdi.", "Vogliono le galline", "Die wollen an die Hühner"), "No hens for them tonight." ("Nocoj ostanejo brez kokoši."),
  the why "One fox, two foxes: after x the plural takes -es." and its sl, it, de; Poppy's "Something red is moving!",
  "Like a fox at the hens!" ("Na primer lisica pri kokoših!", "Tipo una volpe tra le galline!", "Zum Beispiel ein Fuchs bei
  den Hühnern!"), "We saved the hens!"; Jack's "Foxes. Three of them, waiting for a lamb" ("Lisice. Tri so …"), "A one-eyed
  fox?" ("Enooka lisica?"), "while the foxes are out", "The foxes won't wait that long" ("Lisice ne bodo čakale tako
  dolgo!"); the torches' "Foxes don't like fire."; the clues "Foxes live in it." ("Tam živijo lisice."), "It keeps the foxes
  out." ("Lisice drži zunaj."); the memories ("lisice na robu gozda", "noč, ko sva z rogom pregnala lisice").
- **The Lakeland form turns** (English with sl, it, de): see section 7 for what the helper wrote; most in need of an ear:
  - are the slips real learners' ("I can hearing them", "Why are you bring rushes?", "What is hush mean?", "Do you got a
    match?", "How long is it been there?", "next of the barrel")? With bring, keep and call, "are you bringing" would be
    right English, so "Why are you bring" is only one letter off a right sentence;
  - do the 11 right choices in full sound natural in their lines ("Yes, Maggie, I am a bit cold.", "Three hours? I am
    hungry now!", "They are coming back!", "It is beautiful, Auntie Edna!")?
  - the new reactions ("Is hush mean? No, love, hush is kind!", "Which cake am I like? Ha! A crumbly old one", "Are you
    call? Ha! Caw, caw!") and the new whys' Slovene ("Za can glagol v osnovni obliki", "Vprašanje v present simple
    potrebuje do, ne are", "Next gre s to, ne z of").

- **The stories** (every changed line in its every language; the Slovene most):
  - bogatinovo-zlato B1: the three "približal/a" choices, #14; desetnica A2: the #18 reply; kekec: A1 #3 reply, A2 #7 and
    its notes; kralj-matjaz: A1 #20 reply, B1 #13, #15 and #21; lepa-vida B1: the #16 reply; peter-klepec: A1 #17, its
    caption and notes, the A2 #20 reply; povodni-moz A2: the #2 and #4 replies; pegam-in-lambergar: A1 #11 and #17 and
    notes, B1 #8 and #9 with their replies, B1 #14; veronika-deseniska: chapter 1 B1 #1 and #20, chapter 2 B1 #1–#4 with
    their replies, #19 and its notes, #20; zlatorog B1 #13; zmaj-ljubljanski B1: the #4 reply, #14, and #2 kept; zvon-zelja
    A2 #19; jurij-kozjak: chapter 2 A1 #18 and notes, the chapter 3 A1 #21 reply.
  - le-agane A1 #13, the #14 reply and the notes; the Kärnten lines and replies above (and their Slovene, "Se tudi ti česa
    bojiš?", "Smo v letu 1348 …"); the-giants-grave A1 #2, A2 #4 and the notes; the-hob A2 #1 and the #3 reply ("Tla so
    čista, beseda pa bi še potrebovala metlo!").
  - peter-klepec's picture moved from the meadow to the river: worth a look at the notebook's sketch.
