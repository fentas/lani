# Learning progress between linked towns (built 2026-10-26)

Jan asked: "Can I see his stats; what words he is currently learning, where he has problems, and vice versa,
configurable?" Jan is a learner of Slovene and the parent of Teo, a child who learns Italian from German on a profile of
their own (`teo`). Their towns are linked. Before this round, a linked town saw only the public state (the map, the age,
the buildings, the people, the sky: `publicState` in `towns.ts`). Nothing showed one learner's learning to anybody else.

This round adds a third thing a linked town can see: the other learner's learning, at the level that learner chose.

## What was built

### The sharer decides: three levels per linked town

Each learner sets, for each linked town, what that town sees of their learning:

| Level | App label | What the other town sees |
|---|---|---|
| `village` | 🏡 Samo vas · Just my village | the public state, as before; nothing of the learning |
| `summary` | 📊 Povzetek · Summary | the streak, this week's days (as dots), minutes, reviews and new words, the words known, the level and the school's unit in progress |
| `details` | 🔎 Podrobno · Details | the summary, plus the words being learned now (the 20 newest cards, with their meanings), what is hard (the error patterns with mistakes in the last four weeks, by name, each with the cards its mistakes name; the cards failed lately) and what is due |

The defaults, when nothing is set:

- between two adults' towns: **Summary**;
- from an adult's town to a child's: **Summary** (a child sees "Papa learned 12 words this week": that is the "vice
  versa");
- from a child's town to its **guardian's** (a parent's): **Details**, and the child's app can't lower it;
- from a child's town to any other town: **Just my village** (high privacy by default for a child). A parent (on the
  node) or the child can raise it.

**Never shared, at any level:** the tutor chat, free-text answers and what was said in them (a mistake's examples:
what was typed, the correction, its context; an error pattern's own review card, which holds what was typed; the
pattern's notes), the session notes, recordings, the family's messages, the learner profile's other fields, the raw
databases. `progressOf` in `companion/bridge/src/progress.ts` builds the answer field by field from read-db's output, so
nothing else can leave. The asking bridge cleans the answer again (`cleanProgress`) and keeps only what the level holds.

One thing comes from a text the tutor writes: an error pattern's **name** (its `description`, at most 80 characters,
one line). The pattern's examples never leave; their `context` is only searched for the ids of the review cards it
names, and those cards' own word and meaning are shown.

### A parent and their child: the guardian link

A link from a child's town (`lani-profile add --child`) can mark the other town as the child's **guardian**
(`guardian: true` on the child's side of the link, in `towns.json`):

- `lani-town link <adult> <child>` marks it: the adult's town is the child's guardian (the CLI says so; `--no-guardian`
  links without);
- `lani-town guardian <town> --profile <child>` marks a link made otherwise (an invitation accepted on the node), and
  `--clear` takes it away.

The guardian's town sees **Details**. The child's app can't choose less (`403 guardian`). The parent can lower it on the
node: `lani-town share <town> summary --profile <child>` sets the level and the least the child's app may set (the
link's `floor`). The child can raise it again, never go below it. The child's Friends screen says it plainly: "👪 Tvoj
starš vidi tvoj napredek · Your parent can see your progress".

### Between bridges: `GET /town/progress`

A signed route between towns, like the other `/town/…` routes (`companion/bridge/src/features/progress.ts`):

- `GET /town/progress?base=<the asking learner's base>`: linked towns only (`401` otherwise, with the same codes as
  the other routes: `unsigned`, `unlinked`, `bad_signature`, `stale`, `replayed`, `misaddressed`). The answer is signed
  back. It is `{v, town, level, date, child?, guardian?, base?, summary?, details?, challenges?}` at the level this town
  shares with the asking one: `summary` from Summary on, `details` at Details. `base` is the language the cards'
  meanings are in (this learner's base); with `?base=`, a word's meaning in the asking learner's base is added from this
  town's dictionary where it has one.
- `challenges`: the asking town's own challenges to this learner, and how they went (answered or not, the tutor's score
  and the note it wrote for the sender). Never another town's, and never the learner's answer.
- `POST /town/challenges {v, id, type, text, hint?, base?}`: a challenge from the asking town's learner. It goes into
  the family thread as a partner challenge ("💌 Od Jana · From Jan"), once per `id` (a retry is answered
  `duplicate: true`). At most 5 a day from one town and 5 waiting for an answer at once
  (`LANI_TOWN_CHALLENGES_PER_DAY`, `LANI_TOWN_OPEN_CHALLENGES`; `429`, `409 too_many_open`). A child's town takes none
  with a link or an address (`409 not_for_a_child`). The tutor gets it as `partner_challenge`, the texts under
  `untrusted_town_content`, and writes the `partner_note` in the sender's base.

The learner's databases are read once per 30 seconds at most (one read-db for every asking town). At Just my village
they are not read at all.

### The app's side

- `GET /towns`: each row has `sharing`: `{share, share_default?, guardian?, floor?, choices, theirs?, weekly?}`, what this
  learner shares with that town (and whether it is the default), whether that town is this child's guardian (and the
  choices the app offers), what that town said it shares with this one when asked last (`theirs`, with `guardian` when
  this learner is its child's guardian), and the weekly note.
- `GET /towns/:id/progress[?fresh=1]`: that town's progress, asked by this bridge, checked and cleaned, kept for a minute
  (`LANI_TOWN_PROGRESS_CACHE_MS`); while the town doesn't answer, the last one had (up to a day old) with `stale: true`.
  With `fetched_at` and `sharing`. `502` with a `code` when it can't be had (`unreachable`, `refused`, `unsupported`: the
  town's bridge is older, `bad_answer`), with `sharing` too.
- `PUT /towns/:id/sharing {share?, weekly?}`: what this learner shares with that town; a weekly note of theirs. `403
  guardian` below a guardian link's floor; `400` for a level that isn't one.
- `POST /towns/:id/challenges {id, type, text, hint?}`: a challenge to that town's learner, sent on; its answer is kept
  in `<data>/app/town-challenges.json`, so a retry is answered the same without asking again.

All of these take the app token only: the family token gets `403`, and the relay never passes the app's routes on.

### In the app

- **The friend's card** in "🤝 Prijatelji · Friends" (`ui/towns/TownShare.kt`): "👁️ Kaj vidi Teo · What Teo sees: 📊
  Povzetek · Summary ▾", a tap opens the three levels with what each shows; "👀 Teo ti kaže · Teo shows you: 🔎 Podrobno
  · Details" once known; on a child's card for its parent's town, "👪 Tvoj starš vidi tvoj napredek · Your parent can see
  your progress" and no choice below the floor; "📈 Napredek · Progress".
- **"📈 Napredek · Progress"** (`ui/towns/TownProgressScreen.kt`, its logic in `TownProgressLogic.kt`, its data in
  `data/TownProgress.kt`, `app/TownProgressController.kt`): what the town shares and why ("👪 As their parent you see the
  details"); the summary (🔥 the streak, 📚 the words known, 🆕 new this week, this week's days as seven dots with their
  letters, the minutes and reviews, the level and the school's unit); "🌱 Besede, ki se jih uči · Words being learned"
  (🌱 being learned, 🔁 rusty, ✓ known; a tap opens the word's card, 🔊 says it); "🧗 Težave · Hard for Teo": each pattern
  with its trend, how often, and its words, each word with 💌; the words missed in reviews lately; what is due; "💌 Tvoji
  izzivi · Your challenges" and how they went; "💌 Pošlji izziv · Send a challenge"; and at the end what this learner
  shares with them, with the parent's weekly note.
- **A challenge** from a hard word is prefilled: to translate the word's meaning in the other learner's base into their
  language ("das Haus" → "la casa"), the pattern as the hint, and for a child a gentle line in the child's base ("Keine
  Eile, probier es einfach! 🌟"). The sender can change all of it. The other app shows it as a family challenge.
- **The meanings** show in the viewer's base when the other town's dictionary has it, else in the sharer's base with its
  flag ("der Hund 🇩🇪").
- **🔊** says a word of another language with the phone's text-to-speech in that language (`Speaker.sayIn`), never with
  the village's voices (they speak the learner's own target). A phone with no voice for it says so.
- **The parent's weekly note** (opt-in, on the child's progress screen: "📬 Tedensko sporočilo · Weekly note"): each
  Sunday from 18:00 (by the node's clock) the parent's bridge asks the child's town and sends a `town_progress` event
  ("📈 Teo ta teden: 4 dnevi, 23 novih besed, težave: Articoli: il / la"), a notification with the app closed, which
  opens the progress screen. Once a week per town (`weekly_sent`). `LANI_TOWN_WEEKLY_DAY` (0 = Sunday),
  `LANI_TOWN_WEEKLY_HOUR` and `LANI_TOWN_WEEKLY_CHECK_MS` change when.

### The tutor

- The channel guide has a paragraph "Progress between towns": what the levels share, that a guardian sees Details, what
  is never shared; to call `town_progress` before saying who can see what, and to say it truthfully ("your parent can
  see this week's words"); to use another learner's shared progress only when the learner asks for something with it, and
  never to pass it on.
- `town_progress` (an MCP tool): without `town`, what this learner shares with each linked town and what each shares
  with them; with `town` (a learner's or village's name, or an id), that town's progress as it shares it, in a few lines.

### The CLI

```bash
companion/bin/lani-town list [--profile teo]                    # now with SHARES and THEY SHARE
companion/bin/lani-town share <town> village|summary|details [--profile <id>]
companion/bin/lani-town guardian <town> --profile <child> [--clear]
companion/bin/lani-town link default teo [--no-guardian]         # the adult's town becomes the child's guardian
```

`list` reads the other side's level from its `towns.json` for a town on this node, else as the town said when it was
asked last (`theirs`).

## How to use it

1. Update the node and the phones (the bridges of both towns need this version: an older town answers "That town's
   tutor is older: it doesn't share progress yet").
2. Jan's and Teo's towns are linked already. Mark Jan's as Teo's guardian, once:

   ```bash
   companion/bin/lani-town guardian default --profile teo
   companion/bin/lani-town list --profile teo     # SHARES: details (guardian)
   ```

   (A link made from now on with `lani-town link default teo` is marked by itself.)
3. In Jan's app: Friends → Teo's card → "📈 Napredek · Progress". Turn on "📬 Weekly note" there for the Sunday note.
4. What Jan shares with Teo: the card's "👁️ Kaj vidi Teo · What Teo sees" (Summary until Jan changes it).
5. To see less of Teo's: `companion/bin/lani-town share default summary --profile teo`.

## How it was tested

- **Bridge smoke** (`companion/bridge/test/smoke/progress.ts`, 39 checks in the towns section: Jan's town, Mia's, and
  Luka's, a child's, Jan its guardian; the whole smoke: 2,646 checks, all passing): the pure part (weeks, the ISO week,
  the streak, learned and rusty as the app counts them, what each level holds, the asking side cutting a town back to
  its level, the defaults); `GET /town/progress` for the guardian at Details, signed back, with no marker of what must
  stay private; `401` for an unlinked town, a stranger's key, no signature, the app's or the family's token, a signature
  for another path; the app's routes with the app token only; the parent's app seeing Details, the child's Friends
  showing the parent sees, the child's app refused below the floor; the child seeing the parent's Summary; a level
  changed and the other side seeing less (Jan to Luka, Mia to Jan: Details, then Just my village); the CLI lowering a
  guardian's level and listing both ways; a challenge sent, once, refused with a link for a child, answered, and how it
  went back on the sender's screen without the answer; each town seeing only its own challenges; the weekly note once;
  the tutor's tool and guide. A synthetic learner (`test/fixtures/learning.ts`) puts a marker in every text that must
  stay private (what was typed, the corrections, the contexts, the notes, an error pattern's own card, the session
  notes, the profile); no answer at any level holds it.
- **Relay smoke** (`test/relay.ts`, 40 checks, all passing): a town's progress through the relay; a request for it
  without a linked town's signature refused by the relay (`401`, `403`); from a lying relay, an unsigned
  `/town/progress` (`401`), the family's scope and the app's route (`403`).
- **Android unit tests** (2,964): `TownProgressTest` (5: the answer read whole and at each level, the refusals, the
  rows' sharing, the weekly note's event, text and deep link) and `TownProgressLogicTest` (6: the card's lines, a
  child's note and choices, the dots, the meanings, the week line with the Slovene dual, the challenge drafts, gently
  for a child, the results); `PhoneBridgeTest` boots the phone's bridge with the new feature. One timing test
  (`LodTest`, a frame budget) missed by 1 ms on a machine at load 33, unrelated.
- **Python tests** (251 and 98) and the relay's own (19) pass; `tsc` is clean; `tutor-export --check` matches.
- **Emulator QA** (`companion/bin/qa --steps friends,town-progress,town-progress-child` with `LANI_DEV_DATA` a
  synthetic parent, `bun test/fixtures/make-learner.ts <dir>`, and `LANI_DEV_TOWN_FIXTURE=dev-town-child.json`, a child's
  town with a learner's progress, the dev bridge's town its guardian): the parent's Friends card ("👁️ Kaj vidi Nino ·
  What Nino sees: 📊 Povzetek · Summary (privzeto · default) ▾"), "📈 Napredek · Progress" (the parent's note, the streak,
  words known and new, the week's dots, the week line, the level; the words being learned with their German meanings
  and 🔊; "Težave": "Articoli: il / la" with la casa and la finestra, "L'apostrofo: l'acqua", la mela missed in reviews;
  what is due), a hard word's challenge prefilled ("das Haus", hint "Articoli: il / la · Keine Eile, probier es einfach!
  🌟") and sent, then under "💌 Tvoji izzivi" waiting for an answer; the three levels' menu, and just the village chosen;
  the app paired with the child's town: its Friends with "👪 Tvoj starš vidi tvoj napredek · Your parent can see your
  progress" and the level as a line (sl · en, and the child's own it · de); the parent's progress there: "Ana deli samo
  svojo vas · Ana shares just their village". No crash.

## What's left

- **End-to-end encryption.** Through the relay, the relay can read a progress answer as it can read the public state
  and the visits' answers today (its TLS ends there; the towns sign everything, so it can't change it). Encrypting the
  answers between two towns' keys is future work (docs/privacy.md says so).
- **The word card's 🔊** (`ui/words/WordSheet.kt`) speaks the learner's own target language: for a word of another
  language it is the progress screen's own 🔊 that says it right.
- **A machine-written Slovene, Italian and German** for the new labels (`townProgress.*` in the four string tables) and the
  weekly note: they need a native speaker's check. The weekly note's Slovene uses the dual and plural forms ("2 dneva",
  "4 dnevi", "5 dni"; "2 novi besedi", "3 nove besede").
- **The weekly note's time** is the node's clock (Sunday 18:00); a parent abroad gets it at that hour of the node.
- **Older bridges.** A town whose bridge is older answers `/town/progress` with `404`: its progress screen says "Učitelj
  te vasi je starejši: napredka še ne deli · That town's tutor is older: it doesn't share progress yet"; what this
  learner shares with it is set all the same and counts once it is updated. An app older than this bridge reads
  `GET /towns` as before (it ignores `sharing`).
- **The QA's synthetic parent** has a grown village (the second town's buildings, its culture's people:
  `make-learner.ts`). With a village founded during setup, QA's `friends` step stopped at its "village screen" check
  before the scroll; not looked into further (the step works on a grown village, as on the real data).
