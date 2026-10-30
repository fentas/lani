# Built: the game's first guidance, "💡 Namigi · Tips" (2026-10-30)

Jan: "also some first guidance would be nice. E.g. teo had full wisdom -> would be a nice trigger to show user that
he/she can trade it for something else. Or if the school is there that they can take lessons etc." And for the choices:
"make it the best - prod ready". The same day, from the coordinator, Teo's real case: Teo tried the tent move ("La tenda in
un posto nuovo") at 10:40, got 1 of 4, and the request went to "Later" until the next morning, with no word of where it
went: to the family "the challenge is not there anymore". That is a wall; it is part of this round.

The rules as built are in companion/GAME.md, "Guidance: 💡 Namigi · Tips" (with the catalogue as a table) and "Quests"
("A try below the mark"); README "Tips". This note says what was built, why each choice, how to use it, how it is
tested, what it costs in taps, and what is left.

## What was built

| Ask | Built |
|---|---|
| A catalogue of moments, each with its trigger, place, line, a child's line, action, priority, once or again | `game/Guidance.kt` (pure): 40 moments (`Guidance.MOMENTS`), the ids the tutor's too (`bridge/src/tips.ts`). The table below. |
| One at a time, at the right place, never in an exercise, the others later | `Guidance.next`: one tip per place (the map, Home, a scene); the one shown stays until dealt with; a new one only in a quiet moment, 5 minutes after the last, three a day; none in a threat, on a visit, or while off. |
| On the map a gentle bubble at the thing; on Home a card in "Danes" | `TownMarker.Kind.TIP` through the bubbles' own layout (gold rim, 💡, never wobbling); its tap opens `TipCard` over the map. Home: the same card in `TodayCard`, under the ideas. A scene: the card at its foot the first time inside one. |
| Its action does the thing and marks it seen; "Razumem" dismisses it; "Pozneje" the next day | The card's ONE button (the trade with the full store chosen, the school's next unit, the building's card, the words' run …), "👍 Razumem · Got it", "⏰ Pozneje · Later". |
| For a child: short, the base with the course language's word, a picture, read aloud | A child's line per moment (`*.child`), the word bold, the card bigger; read aloud as it opens from its bubble and on a tap: the base by the phone's voice, the word by the narrator's (`HintVoice`). |
| Persistence, the settings, a village redone | `GameState.tips` (synced, kept by the bridge from an older app); "💡 Namigi · Tips" in the settings (on/off, "🔁 Pokaži jih spet"); a village founded anew by the same learner keeps what they dealt with and off (`Prefs.tipsMemory`). |
| The tutor | `get_village`'s `tips`, `show_tip {moment, note}`, the guide's paragraph; tutor-export regenerated (53 tools). |
| Teo's state now: a learner past a moment gets the useful ones once, never a flood | `Guidance.begin`: the moments that hold in a village from before are a backlog, at most one a day, the best first. |
| (coordinator) A failed request says where it went, offers a drill, the drill brings it back today, a child sees the answers | The run's end: "🌙 … torna domani alle 05:00", "🎯 Esercitati e riprova"; the giver's "🔁" on the map, "Later" with the time, the tip `back`; `RequestWindow.retried` after the drill; a child's ✅/❌ list with each right answer, its picture and 🔊. |

## The catalogue

Priority: the higher first (a tie by the catalogue's order). Every moment comes once, but a store full (again a week
after it was dealt with, while the learner never traded). "Known" is what skips it: the learner showed they know it. The
child's lines are in the four tables (`tips.*.child`); here in English and in Teo's German, «…» the course language's word
(in Teo's: "Saggezza", "Scuola", "aiuto", "ciao" …).

| Moment | Trigger | Place | One tap | Child's line (en / de) |
|---|---|---|---|---|
| `full-wisdom` · `full-stone` · `full-wood` · `full-food` (95–92) | the store at its cap, from Tabor; known once the learner traded | the market, else wisdom at the linden or the fire, stone at the rocks, wood at the woodpile, food at the field | "⇄ Zamenjaj": the trade, that store chosen to give; its trade spent today: "🔨 Gradi" while something can be built with it | Your store is full: «Saggezza»! Swap some for 🪨. / Dein Lager ist voll: «Saggezza»! Tausch etwas gegen 🪨. |
| `trade` (asked only) | the tutor points at trading | the fullest store's | the trade, the fullest store chosen | Swap «…» for 🪨! / Tausch «…» gegen 🪨! |
| `repair` (90) | the first damaged building; known after a repair | the building | its card (the repair, the moba) | Broken: «…»! Fix it, the neighbours help. / Kaputt: «…»! Reparier es, die Nachbarn helfen. |
| `festival` (88) | a festival on today (not a quiet day); known once one was kept | the linden, else the fire | its run | It's a feast today: «…»! / Heute ist ein Fest: «…»! |
| `back` (87) | a request tried below the mark waits for tomorrow | its giver | "🎯 Vadi in poskusi znova": its drill | «La tenda in un posto nuovo» comes back tomorrow! Practise and try again today. / … kommt morgen wieder! Übe und versuch es heute noch mal. |
| `arrival` (85) | someone moved in, to be met; known once someone was met | their home | their introduction | Someone new lives here! Say «ciao». / Jemand Neues wohnt hier! Sag «ciao». |
| `school` (80) | a school stands, the class has a unit next; known once the school or a unit was opened | the school | "▶️ Lekcija": the unit suggested next | New in the village: «Scuola»! Learn here with the teacher. / Neu im Dorf: «Scuola»! Hier lernst du mit der Lehrerin. |
| `class` (79) | the class meets (from the hamlet), no school yet | the linden, else the fire | the unit suggested next | Class has begun: «lezione»! Come and learn. / Der Unterricht beginnt: «lezione»! Komm und lerne mit. |
| `request` (78) | a request's bubble; known after one passed | the giver | its run | Someone calls: «aiuto»! Help out. / Jemand ruft: «aiuto»! Hilf mit. |
| `upgrade` (75) | the first upgrade within reach (age, price, course); known after one | the building | its words' run while words are missing, else its card | «…» can grow bigger! First learn 3 words. / «…» kann größer werden! Lerne zuerst 3 Wörter. |
| `market` (70) | the market stands; known once traded | the market | the trade, the fullest store chosen | New in the village: «…»! Swap and shop here. / Neu im Dorf: «…»! Hier tauschst und kaufst du. |
| `smithy` (70) | the smithy stands; known once a tool was forged | the smithy | the chest (the forge) | … Your tools get stronger here. / … Hier wird dein Werkzeug stärker. |
| `visitor` (60) | today's visitor on the road | the road | their card | A visitor is here! Say «ciao». / Besuch ist da! Sag «ciao». |
| `scene` (60) | a place opens a scene (the fire's from the start); known once any was gone into | the fire, else the building | into it | Go «dentro»! Tap things and people. / Geh «dentro»! Tippe auf Dinge und Leute. |
| `project` (55) | a step can be taken today; known after a step | its site | the projects sheet at it | The whole village builds: «…»! Join in. / Das ganze Dorf baut: «…»! Mach mit. |
| `lipa` · `well` · `church` · `beehive` (50/48/50/50) | the first standing; known once its scene was gone into | the building | its scene, else its card | … The whole village meets here. / … Hier trifft sich das ganze Dorf. (and the others') |
| `watchtower` (50) | the first standing | the watchtower | its scene, else its card | … From up there you see wolves sooner. / … Von oben siehst du die Wölfe früher. |
| `home` (45) | the first hut or house | it | its card | A new home: «…»! See who lives here. / Ein neues Zuhause: «…»! Schau, wer hier wohnt. |
| `palisade` (45) | it stands; known once an event was fought | it | its card | … The wolves can't get in. / … Die Wölfe kommen nicht herein. |
| `chest` (45) | something in the chest; known once a good was given or bought or a tool forged | the fire | the chest | A gift is waiting: «baule»! / Ein Geschenk wartet: «baule»! |
| `age-tabor` … `age-mesto` (40) | the age came in the last 3 days, something of it can be built; never a backlog | the fire | the build sheet | A new age: «…»! Build something new. / Ein neues Zeitalter: «…»! Bau etwas Neues. |
| `surprise` (35) | the day's surprise at the road (not the pedlar) | the road | its run | On the road: a «sorpresa»! / Auf dem Weg wartet «sorpresa»! |
| `reviews` (Home, 90) | reviews due, none ever answered | Home | the reviews | Your first «carte» are here! / Deine ersten «carte» sind da! |
| `pack-done` (Home, 80) | a word pack all learned; never a backlog | Home | the packs | All learned! New «parole» are waiting. / Alles gelernt! Neue «parole» warten. |
| `cases` (Home, 70) | the case trainer open, never played | Home | the trainer | Practise «skloni»: a short game! / Übe «skloni»: ein kurzes Spiel! |
| `grammar` (Home, 60) | the book has a page; known once opened | Home | the grammar book | Your book of rules: «grammatica»! / Dein Regelbuch: «grammatica»! |
| `chat` (Home, 50) | practised, never wrote to the tutor | Home | the chat | Ask anything: «insegnante» helps you! / Frag, was du willst: «insegnante» hilft dir! |
| `talk` (Home, 45) | from Tabor; known once a talk was played | Home | a talk | Talk with the villagers: «conversazione»! / Sprich mit den Leuten: «conversazione»! |
| `friends` (Home, 40) | a linked town never visited | Home | Friends | Your «amici» are waiting! / Deine «amici» warten! |
| `inside` (a scene, 50) | inside a scene, nothing found yet | the scene | none: "👍 Razumem" | Tap a thing: you hear it in «italiano»! / Tippe auf ein Ding: Du hörst es auf «italiano»! |

## The choices, and why

- **A bubble on the map, a card on Home and in a scene.** The brief asks for a gentle bubble at the thing: it goes
  through the bubbles' own layout (no overload: the window of requests, the fan, the declutter, the edge chips all apply),
  and only its tap opens the card, so the map is never covered unasked. Home has no "thing" to point at, so its tip is a
  card in "Danes", under the one thing to do and its ideas: the day's practice comes first. A scene's first visit is a card
  at the foot, because what it teaches (tap things, tap people) is the whole picture.
- **One bubble for one thing.** A tip about something that has a bubble already (a request's 🔁, the first request's
  "!", someone to meet, a visitor, a festival, the surprise, a project's step) takes that bubble's place while it shows,
  so the map never shows the same thing twice; a giver's "!" for several requests stays (the tip names one).
- **Gold rim and 💡, never a wobble or a pulse**: the threat's bubble wobbles and pulses (urgent); a tip is an offer, so it
  is calm, but distinct from a request's "!" and from the mint of someone there now. It is never "quiet" (shrunk away from
  the view's centre): there is only ever one.
- **One per place, the shown one sticky.** At most one on screen at once; a bubble that jumped from thing to thing as
  priorities shift would be noise. It goes when dealt with, when the learner did the thing anyway (known), or when its
  trigger no longer holds (wisdom spent).
- **A quiet moment = nothing open and five minutes since the last; three a day.** "The others wait for a later quiet
  moment": five minutes lets the learner do what the last tip offered and come back; three a day keeps a new village
  (many firsts at once) from feeling like a tutorial. The tutor's pointer and a tip already shown are exempt (they are
  already on screen or asked for).
- **Priorities**: what wastes the village or blocks it first (a store full: nothing more comes in; a damaged building
  gives nothing), then what is time-bound (a festival's day, a request back tomorrow, someone to meet), then the school and
  the requests (learning), then what each building is for, then the age's new buildings, the surprise. Home: the reviews
  first (the daily habit), then the packs, the cases, the grammar book, the chat, a talk, the friends.
- **A store full: the trade, else something to build.** Jan's example and the brief: the trade with that store
  preselected, for what the village is shortest of (as the trade sheet suggests). When its trade is spent today the tip
  offers the build sheet instead, only while something there uses that resource; else nothing (no dead button). A
  learner who traded once knows: it never shows again for them. It comes again a week after it was dismissed, while they
  never traded: a full store wastes practice.
- **The update rule.** Teo's village (wisdom full, a school, a watchtower at level 2) would otherwise get every first at
  once on the day the update lands. `Guidance.begin` runs once the dashboard is in (the learning moments need it): the
  moments that hold then are a backlog that comes one a day, the best first (wisdom full on the first day, the school's
  lesson the next, the watchtower after); moments that are only news when they happen (a pack finished, an age reached
  weeks ago) count as seen. A village founded today has no backlog: everything is new.
- **Known, not only seen.** "Skip a moment the learner has already shown they know": what the village state shows (a
  level above 1, a request passed, a festival kept, a tool forged, a scene's dialogs heard, a unit begun) and, since a
  chronicle of 50 lines forgets, what the app records as it happens (`Tips.did`: a trade, the school, the grammar book, a
  talk, a scene, a repair). Recorded in the village state, so another device knows too.
- **Children.** Jan's brief: short, the base, the course language's word for the thing. The word is a slot that needs no
  agreement ("Dein Lager ist voll: «Saggezza»!", "Neu im Dorf: «Scuola»!"), so a German article never meets an Italian noun.
  Read aloud as the card opens from its bubble (that is the tap; the school's "🔊 Beri razlago" setting governs it, as the
  intro's), and again on 🔊 or the line. The device is a child's when its pairing, the hosted mode, or (new) its own town's
  row on the node says so: a phone set up with a typed token learns it from GET /towns.
- **"Pozneje" is the next day**, "Razumem" for good; the action counts as "Razumem". The ✕ on the map only folds the card
  back into its bubble (a tap by mistake costs nothing).
- **The settings.** On/off is the learner's choice, synced in the village (the tutor sees it, and doesn't point while
  off); on a child's device it is a parent's (a hold opens it), so a child can't lose their guidance by a stray tap. "Show
  them again" forgets what was dealt with and what was done, keeps the day the tips began (so nothing becomes a backlog).
- **Redoing the village.** A new village has its own firsts, so they come again as new; what the learner dealt with and
  did, and their off switch, are theirs: kept on the phone by the learner's name and given to a village they found anew
  there. Growing, replacing or taking down a building resets nothing.
- **The tutor** reads them in `get_village` and points with `show_tip`, as the case trainer's assignment: an event
  (replayed on reconnect, applied once by its time) the app turns into the village's pointer; it comes first at its place,
  once, whatever was dealt with. No deep link: `lani://` links can't be in a chat message (only https), and the pointer
  needs no tap to arrive.
- **The failed request** (the coordinator's ask). The spacing stays (a request below the mark comes back the next
  morning, against guessing), but it is never gone and never a wall: its end says when, a quiet "🔁" stays over the giver,
  "Later" says when, and a drill of what the try missed (the grammar book's practice of its first missed page: for the tent
  move, the prepositions of place, the tent's own questions among them) brings it back today once passed (60 %, an almost
  counting). The instant "🔁 Še enkrat" on the run's end went: it was the guess's way round the wait. A child sees the
  answers, each right answer with its picture and 🔊, before the giver's "again tomorrow".

## How to use it

Nothing to switch on: the tips begin on the app's next start with the dashboard in. ⚙️ → "💡 Namigi · Tips" turns them off or
shows them again. The tutor: `get_village`'s `tips`, `show_tip {moment: "trade", note: "…"}`.

QA, with a synthetic Teo (never a real learner's data):

```
bun companion/bridge/test/fixtures/make-learner.ts <dir> --name Teo --target it --base de
LANI_DEV_DATA=<dir> LANI_ENV_FILE=/dev/null LANI_CULTURE=friuli LANI_CHILD=1 LANI_QA_SETUP_PAIR=it-de LANI_QA_LEARNER=Teo \
LANI_QA_GUIDANCE_SHOTS=<shots> flock /tmp/lani-emulator.lock companion/bin/qa --no-town --steps guidance
```

QA's hooks: "tips:<moment>" (the dev village readied for it: "full-wisdom" its store at its cap, "school" a school standing;
the moment asked as `show_tip` asks it), "tips:reset", "tips:tentmove" (the shepherd's request asked anew and started).

## Taps

Counted from the plain village map (the map) or Home.

| Journey | Before | After |
|---|---|---|
| Wisdom full → the trade with wisdom to give | 2 (the HUD's 📜 chip, "⇄ Zamenjaj"), if the learner knew; nothing said so | 2 (the 💡 bubble, "⇄ Scambia · Tauschen"), and the map says so |
| A school standing → its next unit | 3 from the map (the school, "📚 Šola", the unit), 1 from Home's 🏫 tile; nothing said so | 2 from the map (the 💡 bubble, "▶️ Lezione") |
| A request below the mark → playing it again today | not possible (back tomorrow; or the end's "🔁 Še enkrat", a guess) | 1 to its drill ("🎯 Esercitati e riprova") + the drill's answers + 1 ("🔁 Riprova") |
| Where that request went | the Scroll (1), "Ljudje", scroll to "Kasneje"; no time said | at a glance: the run's end says when; the tip (or the quiet 🔁) over the giver (1 tap: a card with the time and the drill); the Scroll's "Later" row says when |
| A tip read aloud for a child | – | 0 (as the card opens), or 1 (🔊 / the line) |
| A Home tip | – | 1 (its button) |

## Tests

- `GuidanceTest` (26): a store at its cap (the trade, its place, the build sheet when the trade is spent, known once traded,
  a trade settled marks it), the first of each building, the school and the class, an upgrade's words then the card, the
  first damage, the people a tip points at, a new age, the learning moments (Home, waiting for the dashboard), a scene's
  first visit; the queue (one at a time, the highest first, sticky, a quiet moment, three a day, never in a threat or off);
  later and got it, a store full again after a week; the update rule (Teo's village: wisdom full on day 1, nothing else of
  the backlog that day, a new request still that day, the school on day 2), a village founded today, a past moment counted
  as seen; the tutor's pointer; show them again; the JSON round trip and an older save; a village founded anew (same
  learner, another); the recent list; a child's line and its two voices (Teo: «Saggezza», German around it, the resource
  got said by its name, no emoji said); every line in the four tables; the tutor's moments the catalogue's.
- `RequestRetryTest` (5): a failed try is back tomorrow, says when, keeps a quiet "🔁" at its giver, offers the drill after
  one try; passing the drill brings it back today, its giver asking again; without it, tomorrow as ever; the tent move is
  never lost (open, its try and missed pages kept, back today after the drill); the map's tip says where it went, once,
  in place of the giver's quiet sign (`Tip.standsFor`).
- `TipCardTest` (4, Robolectric): an adult's card (both languages, its one button, got it, later), a child's (German with
  «Saggezza», the Italian buttons), a scene's tip with no button, the map's bubble of a tip.
- The bridge's smoke: the tips kept from an older app, get_village's tips, show_tip's event and note, off, the guide.
- QA: see below.

## QA on the emulator

QA's steps `guidance` and `guidance-retry` (companion/bin/qa), as a synthetic Teo (`make-learner.ts --target it --base de`,
LANI_CULTURE=friuli, LANI_CHILD=1, LANI_QA_SETUP_PAIR=it-de), each run inside `flock /tmp/lani-emulator.lock`, on the
build rebased onto main (d3ff0c08). The screenshots are in the session's scratchpad, `ux-guidance/run7` (the tips),
`run8` and `run9` (the failed request; run9 after the fix below).

- **Wisdom full → the trade with wisdom chosen** (run7, ✅): the 💡 bubble with the 📜 at wisdom's place (no market
  yet); its card in German with «Saggezza» bold ("Dein Lager ist voll: Saggezza! Tausch etwas gegen 🪨."), on the phone and on the tablet's
  2560 × 1600 (landscape: the card on the left half, the map beside it); read aloud as it opened: HintVoice
  `1/3 de "Dein Lager ist voll:"`, `2/3 it "Saggezza"`, `3/3 de "! Tausch etwas gegen Stein."` (the stone said by its
  name, not its emoji); "⇄ Scambia · Tauschen" opened the trade sheet with the wisdom chip checked to give. 2 taps.
- **A school standing → its next unit** (run7, ✅): the 💡 at the school ("Neu im Dorf: Scuola! Hier lernst du mit der
  Lehrerin."), "▶️ Lezione · Unterricht" opened the unit over the village. 2 taps.
- **Home's tip** (run7, ✅): the tip's card in "Oggi · Heute", below the day's practice and its ideas (the reviews first).
- **The tent move failed, back the same day** (run8 and run9, ✅): the run answered wrong (0 of 4); its end lists the four
  answers, each ❌ with the right answer, its picture and 🔊; under Pastore Davide's "Grazie! Domani ancora una volta?" it
  says "🌙 Il compito torna domani alle 05:00. · Die Aufgabe kommt morgen um 05:00 wieder." and offers "🎯 Esercitati e
  riprova · Üben und nochmal versuchen". On the map the tip "back" at the shepherd's (by the pond), the Scroll's people
  end with "📜 Più tardi · Später": the tent move, "Pastore Davide · 🌙 Di nuovo domani dalle 05:00 · Morgen ab 05:00
  wieder". The tip's card (read aloud: "La tenda in un posto nuovo kommt morgen wieder! Übe und versuch es heute noch
  mal.") and the shepherd's own card both offer the drill; the drill (3 of 3) ends with "🔁 La tenda in un posto nuovo è di nuovo
  qui: riprova adesso!" and "🔁 Riprova · Nochmal versuchen", which opened the request's intro ("▶ Inizia · Starten")
  the same afternoon. The request was never gone: open, its try counted.
- **Found and fixed on the way**: the end's "back tomorrow" line and the drill sat below the counts, off screen on a phone
  after a child's answers (now right under the giver's line); on the map the tip "back" and the giver's quiet 🔁 stood side
  by side for the same request (run8): a tip's bubble now stands in for its thing's own (run9: one bubble). QA itself: the
  Scroll's "Later" is the people list's last row, found low on the screen.
- Each run about nine minutes (the setup's onboarding and the emulator's start included); the tips and the failed request
  are two steps so each stays under ten.

## Strings

Every new label is "target · base" through bi() (the course language first for Teo: it · de), a child's line in the base,
in sl, en, de and it: `tips.*` (the lines, the child's, the buttons `tips.do.*`, the words `tips.word.*`, the settings),
`quests.againAt`, `quests.backAt`, `quests.practiseRetry`, `quests.backToday`, `quests.stillTomorrow`,
`quests.tryAgainNow`; `quests.again` went (its line now says the time). Machine-written by Claude (2026-10-30), for a
native speaker's check (`_note_tips` in the four tables; Jan's wife for the Slovene).

## What's left

- **A native check** of the lines, especially the children's (short, a slot for the course language's word).
- **The map's "back" bubble for a giver the cast doesn't know** (the tutor's villager) opens the Scroll's people, not a
  card: the request is listed there with its time and drill.
- **A drill for a tutor's request with no grammar page** asks the tutor for one (the chat), as "🎯 Vadi, kar ti ne gre"
  did; it brings the request back only once the tutor's drill is published and passed. A villager's own request (cards)
  is drilled on its own cards at once.
- **Tips on a visit** are none by design (the host's village isn't the learner's).
- **Scene tips** are one moment (the first visit); a scene's own firsts (I spy, a happening) are offered by the scene as
  before.
- **The child flag** now also follows the node's town row (GET /towns' self): a token-paired child's phone gets the
  child's settings gate and the child's tips; a parent's phone never says child.
