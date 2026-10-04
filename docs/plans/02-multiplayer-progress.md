# Plan 2, multiplayer: progress

The working list for finishing the multiplayer part of [plan 2](02-worlds-languages-multiplayer.md) (§3–§5),
option A (a family node: several learners on one machine), with the interfaces between towns written as if the
other town were remote, so federation (B) and hosting (C) reuse them. A loop checks this list every 10 minutes:
it merges and tests finished steps, releases when everything is green, and starts the next steps whose
dependencies are done (at most three at a time).

| # | Step | Needs | Status |
|---|---|---|---|
| M1 | **Town links.** A town id per learner (from the bridge's key), invitations (a code or QR; family only, a child's links only by the parent on the node), an API between towns (signed requests, the link's peer key), a town's public state (map, age, buildings, people, culture, language, today's sky; nothing private), the app's Friends screen (linked towns, invite, accept), `lani-town` CLI. | – | done (commits c08d260, db78567) |
| M2 | **Visiting.** The app opens a linked town read-only: its map, age, people and its culture's scenes, labelled in the host town's language (the visitor's pair becomes host language ← visitor's base). The visitor's app talks only to its own bridge, which asks the host's. | M1 | done (commits 4d47735, 3f4b6f9) |
| M3 | **Things to do on a visit.** Greet a host villager (a role-play played by the visitor's tutor at the visitor's level, in the host's language); a host villager's request (goods for the visitor, 🤝 for the host); a gift or a message (the host reads it; untrusted content to the tutors); the market (a negotiation role-play; the trade moves goods between the chests). Guest-only dialogs in culture scenes. | M2 | done (commits dda3f16, 00f7f56). Nothing for Jan to do: the next app release carries it, and both towns' bridges need the new version (an older host answers "its bridge is older than things to do on a visit"). |
| M4 | **A language profile per language.** Spaced repetition, level and mistakes per language: a visit's words and mistakes go to the visitor's profile for the host's language; the tutor and the progress screen show it ("Italijanščina · Italian: A1, 85 words, from visits"). The databases get a language key (migration of today's single-language data, backups). | – | done (commit 606ae41). Nothing for Jan to do: their data migrates itself on the next run of read-db.py or update-db.py (only learner-profile.json gains `home_language: "sl"` and `languages: []`; a backup first, in `data/.backups/pre-migrate-languages-*`). |
| M5 | **Friendship between towns.** A level grown by visits, trades, help, gifts and answered questions; it pays: better trade rates, a shared feast, help when an event hits the friend (wolves), and people moving between towns (a bilingual resident) at a high level and enough skill. | M3 | done (commits 5a6983c, a3ab602). Nothing for Jan to do: the next app release carries it, and both towns' bridges need the new version (an older town's friendship shows as level 0, "unsupported"). |
| M6 | **The host takes part.** The visitor asks the host a question in the host town's language; the host answers in it (like partner challenges); both practise. | M3 | done (commits ee092df, f3f2798, 70213d6). Nothing for Jan to do: the next app release carries it, and both towns' bridges need the new version (an older town answers "its bridge is older than questions between towns"). Not in M8's QA steps yet: "❓ Vprašaj · Ask" on the dev town, and answering in it, could be added there. |
| M7 | **Italian for visits.** Speech recognition in Italian (the phone's it-IT; the node's Whisper with a general model for languages other than Slovene), grading with Italian's accents, an Italian dictionary for the word card (built from Wiktionary, downloaded, not bundled). | – | done (commit e69a778). Needs Jan: `companion/stt-local/install.sh` to put the new worker in place (it restarts it); the built `it.json.gz` + `it.manifest.json` somewhere lasting, and `LANI_LEXICON_URL` pointing there in the second learner's profile env. Left: `it.sl.json` (meanings in Slovene: its builder needs the 3 GB English extract) and Claude's fill of its gaps. |
| M8 | **Tests across towns.** Smoke tests with two bridges (link, visit, trade); a second dev town for QA (a generated Italian fixture town on its own port), and QA steps for visiting. | along the way | done (commits ff248bf, 90656db) — QA steps written; first run by the lead: `companion/bin/qa` (or `--steps visit`; `--dry` shows the plan, `--bridges-only` checks the two towns without the app). The second dev town is `companion/bridge/test/dev-town.ts` on 8797 ("Borgo di Prova", Friuli, a temp data dir), linked with the dev bridge's town; the dev bridge's copy of `data/` no longer keeps its town links. |

**What M2 leaves for M3** (companion/README.md, "Visiting"):
- A host's people come with their persona (personality, story, likes, register, lines: `GET /towns/:id/villagers`), ready
  for a greeting role-play played by the visitor's tutor; the app's villager card shows "💬 … kmalu · soon" where it goes.
- A host's scenes come with their happenings and dialogs (`GET /towns/:id/scenes`); the visit's scene screen shows who
  is there and "kmalu · soon" instead of their dialogs. The flag marking a dialog for guests is still to do.
- Nothing is written anywhere during a visit: M3's activities need write routes between bridges (a request's help
  points, a gift or message, a trade between the chests), signed like M1's.
- A visit's words: "Add to my words" is off during a visit, until the words go to the visitor's profile for the host's
  language (M4); the word card looks words up in the visitor's bridge's dictionary, which knows the host's language
  once M7's Italian dictionary is there (else "ask the tutor").

**What M4 gives M3** (docs/DB_SCRIPTS.md, "Languages"):
- The visitor's bridge writes a visit's learning into the visitor's profile for the host's language: pass the host's
  language code as `language` to `POST /words`, `POST /reviews` and `POST /packs/:id/learn`, and in a `session_end`
  block's `data.language` (the tutor puts it in the update-db.py report). The first one creates the language.
- The tutor plays a host villager at the visitor's level in that language: `read-db.py --language <code>` (its level,
  words, mistakes, due reviews; a language never practised reads as A1 and empty). `GET /state` lists every language.
- The app's "Add to my words" during a visit can be switched on: `Writes.addWord` still needs a `language` parameter
  (as `Writes.reviews` has) to post the host's.

**What M3 leaves for M5 and M6** (companion/README.md, "Things to do on a visit"):
- The ties with each town are counted on each side, in the village state (`GameState.towns`: `met`, `helped`, `gifts`,
  `trades` on the visitor's side; `guests` on the host's; `last`). A friendship level (M5) grows from these; a level
  both towns agree on needs the other's counts too (a signed read, like `/town/public`, or the guest book's entries).
- The market's rates are fixed: a trade gives at least half of what it takes (`FAIR_SHARE`), the haggle's score must be
  6 (`DEAL_SCORE`), a market spares all but one of a good, at most 3 (`MAX_SPARE`). "Better trade rates" (M5) are
  these, per friendship level. The market sells goods only: a visitor can't buy the host's resources yet.
- Help when an event hits a friend (M5's wolves) is another kind of guest-book write (`POST /town/help` knows requests
  only); the guest book, its idempotent keys, the limits and the host's app applying each entry once carry over.
- A gift's message has no answer path: the host reads it, that's all. M6's question and answer can be a guest-book
  entry the host's app answers (like a partner challenge), with a signed write back to the visitor's town
  (both towns are linked both ways, so the host's bridge can reach the visitor's).
- A request's run on a visit explains the host's words in the visitor's base where the host's pack has it; Slovene
  packs have English meanings only, so the second learner visiting Jan's town reads English meanings (M7's `it.sl` gap, the
  other way round: Italian meanings in the Slovene packs).
- A scene's dialog on a visit pays nothing and doesn't move the host's village (its sky, its effects stay the visitor's
  screen's); it counts as someone met.
- Not tried on a phone or the emulator in this step (unit tests and the bridges' smoke only): QA steps for visiting are
  M8's.

**What M5 leaves** (companion/README.md, "Friendship between towns"):
- M6's answered questions count once they are logged as kind `answer`: the answering town's visit log (a write through
  `write()` in features/towns.ts counts itself), the asking town's guest book (`arrived()` counts it). A write that goes
  elsewhere counts with `friendsOf(appDir, key).ledger.count(town, 'gave' | 'got', 'answer')` (bridge/src/friendship.ts);
  `FRIEND_POINTS.answer` is 2.
- Someone who moves is named from the culture pack's names and trades (people.json), as no pack has extras yet; their
  lines are the pack's plain lines in their language. The tutor hears of them (`villager_arrived`, `kind: moved`) and may
  give them lines of their own.
- An offer to move waits until the other side says yes or no, or the offering side withdraws it; it doesn't expire.
- A parent controls a child's town's moves through their own town (the grown-up side says yes) and its links (the node);
  there is no CLI for moves.
- Not tried on a phone or the emulator (unit tests and the bridges' smoke only): QA steps for the Friends screen's
  friendship are M8's.

**What M6 does with it** (companion/README.md, "Questions between towns"):
- An answered question is an act of kind `answer` in both towns' ledgers (features/questions.ts): the host counts it as
  given once the guest's town took the answer, the guest as got when it came, so both towns count it once.
- The app also keeps the count in the ties with each town (`TownTies.questions`, game/TownQuestionTies.kt: once per
  question, `q:<key>` in `GameState.guestbook`, a chronicle line), and `GET /towns/questions` says how many were
  answered with each town, both ways (`answered`; `answeredWith()` in bridge/src/questions.ts).
- The app's events when one happens: `town_question` (a guest asked this town's learner) and `town_answer` (the town
  this learner asked answered), `{key, from, learner, text}`.
- Not tried on a phone or the emulator (unit tests and the bridges' smoke only): asking and answering aren't in M8's QA
  steps yet.

Rules for every step: the standing rules of the repo (never the real bridge on 8790 or real data in tests; no
voice-build runs; deny rules are never worked around), a child's safety (§5: invite only, the host's private
things never leave, a visitor's text is untrusted content), and Jan's design rule: every step that blocks has
an action Jan can take right now.

Not in this loop (needs Jan): creating the second learner's profile on the node, publishing it with `tailscale serve`,
pairing their phone.
