# Built: a word the dictionary doesn't know, added from its card

Jan, 2026-10-26: "if a definition is missing I can't add it but have to go to tutor -> ask to add".

The rules as built are in companion/README.md ("Word lookup", "A word the dictionary doesn't know"; "Moje besede · My
words"; "The tutor in the app") and in the tutor's guide (the lexicon feature's paragraph: channel_guide, and
companion/tutor/guide/word-lookup.md for the in-app tutor). This note says why it was so, what changed, how to use it
and what is left.

## Why it was so

The word card of a word the dictionary lacked said "🤷 Te besede ni v slovarju · This word isn't in the dictionary" and
offered only "📖 Vprašaj učitelja · Ask the tutor". That closed the card and opened the chat with a message about the
word; the tutor explained it there and kept a gloss (`publish_gloss`), but nothing added the word. The learner then had to
ask again in words, wait, find the word again, open its card and tap ➕. A path in the background existed only for a
meaning in the learner's base when the dictionary had the English (`gloss_gaps`), and it never added a word.

## What changed

**The card.** For a word the dictionary doesn't know (`ui/words/WordSheet.kt` `MissingWord`):

1. "🔎 Morda iščeš · Did you mean": the words it may be instead, each with its meaning; a tap opens that word's card in the
   same line (`WordsController.openSuggestion`).
2. "🤷 Te besede ni v slovarju", then "➕ Dodaj med moje besede · Add to my words" with "✍️ Učitelj bo napisal njen pomen ·
   Your tutor writes its meaning" under it. One tap (`WordsController.request`): the card stays open, "✍️ Tvoj učitelj piše
   pomen … · Your tutor is writing its meaning…" and "Ko bo pomen napisan, bo beseda med tvojimi besedami · Once it's
   written, the word joins your words". Offline: "📶 Učitelj jo dobi, ko boš spet povezan · Your tutor gets it once you're
   online". When the meaning comes the card fills in with the tutor's entry (🤖 machine-written, not reviewed) marked
   "✓ Dodano · Added". Opened again while it waits, the card says the tutor is writing it (the node's `requested`).
3. "📖 Vprašaj učitelja · Ask the tutor", quieter (a text button), for "what does it mean here?".

With the card closed meanwhile, a quiet notice when the word is in: "📚 Zdaj med tvojimi besedami · Now in your words:
«vreteno» — spindle" (no system notification; the event replays when the app opens).

**Back online.** The outbox waited for its next retry once the network came back (minutes after a long time offline:
5 s, 10 s, 20 s … between attempts), and the live events might not notice the network go and come back. Now, while the
app is on screen, the network back after it was lost sends the outbox at once and reconnects the events
(`app/NetworkBack.kt`, on the process's one network watcher, `tutor/live/AndroidOnline`, which the tutors' queues wait
on too): a word asked for offline, and every other write, goes as soon as the
phone is online.

**"📚 Moje besede".** A search the dictionary doesn't know offers "➕ Dodaj «…» med moje besede · Add «…» to my words",
which opens its card. The words waiting for their meaning are on top, "✍️ Čakajo na pomen · Waiting for their meaning",
the node's and the outbox's (offline: "📶 …"), each opening its card.

**The node** (companion/bridge `features/lexicon.ts`, `word-requests.ts`):

- `POST /words/requests {word, line?, en?, from?, language?, base?, client_id}`, through the app's outbox (queued
  offline, never lost, in order). The word waits in the learner's data, `<data>/app/word-requests.json` (a restart and a
  handover keep it), and the tutor hears `gloss_request`, gathered 1.5 s: the words, what to publish, that the bridge adds
  it, no reply; a typo glossed as the word meant, a name as `pos: "name"` (not added); a child's profile asks for a
  simple, age-appropriate meaning. A visit's lines go under `untrusted_town_content`.
- The tutor's `publish_gloss` of a word waiting (its form or its lemma, in the language asked for) adds it to the
  learner's words, as `POST /words` does, before the app hears `lexicon_updated`; then `word_request_done {word, lemma,
  gloss, item_id, added, language?}`. The tool's answer says what was added (and which language a word waits for when the
  gloss is in another).
- The same word asked twice waits once; one the dictionary knows by the time the request arrives is added at once with
  the entry its line means; a word not told (no session) or unanswered after 12 h is told again with the next word asked
  or at the bridge's start, three times at most; one 60 days old is dropped.
- `GET /lookup` without entries has `suggestions` ("Did you mean": the surest kind only: accents given back, another
  ending of a word the dictionary has, a typo of a dictionary form past its first letter; at most 3, one per lemma) and,
  for a word waiting, `requested: true`. `GET /words/requests` lists the words waiting.
- A node from before the route answers 404: the app sends the word as a chat message instead ("➕ «…» — Dodaj med moje
  besede", `data.lookup` with `add: true`).

**The tutors.** Claude Code reads the lexicon feature's paragraph (channel_guide); the in-app tutor reads the same
paragraph as the guide's `word-lookup` topic, and the core prompt names `gloss_request` among the kinds. The in-app tutor
answers it at once on the routine model, before routine events (`LiveTutor.priority`; never in the night's batch, which
only `gloss_gaps` goes to), with the provider's model, through the same `publish_gloss`; the card flags its meaning
machine-written as every tutor gloss. The dictionary's notes (`gloss_request`, `gloss_gaps`) owe no reply
(`Owed.NONE` in tutor/Loop.kt): the "never silent" loop neither tells such a turn to reply at its last call nor asks
for a last word with the tools off, and a text it ends with goes nowhere (the text-as-reply fallback is for what the
learner waits on). The lani-fix skill names `word-requests.json` and the words added that way.

**Both learners.** Jan (Slovene from English) and Teo (a child, Italian from German): the request carries the word's
language (a visit's: the town's) and the pair's base, the note asks for meanings in that base, and the word joins the
deck of its language (a visit's word: `languages/<code>/`).

## Choices

- **The node adds the word, not the app.** The request may outlive the card, the app (closed, restarted) and the
  network; the node holds it until the gloss comes and adds it in the same step, so the word is in Moje besede whether
  the app was open or not. The app only shows what the node did.
- **Extend the gloss path, no second one.** The tutor answers with `publish_gloss`, as for `gloss_gaps` and a chat's
  lookup; the request is matched by form or lemma and the gloss's language.
- **"Did you mean" before claiming a word missing, but cautious.** Only the surest kind of suggestion shows (the accents,
  then another ending, then a typo), and a typo must be a dictionary form or a pack word itself: a rare word one letter
  off another inflected form ("vretenom", "vremenom", a form of the pack word vreme) is more often another word. Among
  equally sure ones the learner's pack words come first ("sneh": sneg, then smeh). An ending is swapped only where the
  form has one of the language's endings ("sneh" is no form of snaha). A wrong suggestion costs a tap; "Add" stays right
  below. A probe of the dev bridge (on QA's synthetic data, the curated packs) found the first two.
- **Lemmatizing** is the dictionary's own: its forms table maps an inflected form to its lemma (gozdu → gozd). A form of a
  supplement word the content doesn't use ("kozolcu") gets the ending suggestion.

## How to use it

Nothing to turn on: tap a word the dictionary doesn't know, then "➕ Dodaj med moje besede". Settings for a node:
`LANI_WORD_REQUEST_MS` (the gathering, 1.5 s), `LANI_WORD_REQUEST_RETELL_MS` (12 h).

QA (a synthetic learner made with the bridge's `initLearnerData`, never the real data):

```
LANI_DEV_DATA=<a synthetic Jan, Slovene from English> LANI_ENV_FILE=/dev/null LANI_QA_MISSING_SHOTS=<dir> \
flock /tmp/lani-emulator.lock companion/bin/qa --no-town --steps missing-word
# Teo: a child learning Italian from German, the cards in it · de, the rest of QA in sl · en
LANI_DEV_DATA=<a synthetic Teo> LANI_ENV_FILE=/dev/null LANI_CULTURE=friuli LANI_CHILD=1 LANI_QA_PAIR=sl-en \
LANI_QA_MISSING_PAIR=it-de flock /tmp/lani-emulator.lock companion/bin/qa --no-town --steps missing-word
```

The learners' data: the bridge's `initLearnerData` (Jan: `target: "sl"`, `base: "en"`; Teo: `target: "it"`, `base:
"de"`, `child`, `culture: "friuli"`).

The dev bridge's tutor glosses a `gloss_request` after 4 s (`LANI_DEV_GLOSS_MS`), with canned meanings for QA's words
(test/dev-bridge.ts `DEV_GLOSSES`).

## Tests

- **Bridge:** `bunx tsc --noEmit -p .` clean; the smoke, 2,622 checks, all pass. New: the word-requests section (29:
  "Did you mean" (accents, two at once, a typo, nothing like it, the pack filter and order), the request kept by its
  client id, the same word once, the note once and gathered with its JSON block, `requested`, GET /words/requests, the
  file a restart reads, the add from the tutor's gloss (the item, its example, the events in order, the gender, the
  tutor told not to persist it), a gloss again adding nothing, one the dictionary knows by now added at once, one of
  theirs not again, a name not added, six refusals, the app token, a gloss in another language leaving it waiting, a
  retell, the guide) and 4 in friuli (a child learning Italian from German told in German and as a child, the German
  meaning added to the Italian deck, a visit's word with its line untrusted and escaped, added to the child's Slovene
  deck). `tutor-export --check` matches.
- **App:** the unit suite, 2,964 tests: one failure, `StormRenderTest`'s frame budget (51 ms at a load average of 20),
  which passed run alone. New: `WordsRequestTest` (5, Robolectric over a fake node: add waits and fills in, marked added;
  the notice with the card closed, none for a name; offline: queued, listed waiting, sent once online, the card opened
  again not offering Add twice; a node from before the route gets the chat message with `add`; a suggestion's card),
  `WordsTest` (+6: the event, the lookup's new fields, the word sent, the request's body, the fallback, the waiting
  list), `QaHooksTest` (+1, "word:"), `LiveTutorTest` (+1: a `gloss_request` at once on the routine model, its
  `publish_gloss` through the bridge, no batch, no reply).
- **Python:** `tests` 251, `companion/tests` 98 (10 skipped), all pass.
- **Emulator** (`--steps missing-word`, synthetic learners, the dev bridge's tutor): Jan (sl · en): «zlica» offered
  žlica, whose card opened; «vretenom» added, the card waiting ("✍️ Tvoj učitelj piše pomen …"), filled 16 s after Add
  with vreteno · n., spindle, machine-written, "✓ Dodano"; vocab_word_vreteno on the dev bridge with the line; in Moje
  besede. Offline: «motovilo» "📶 … ko boš spet povezan", listed under "✍️ Čakajo na pomen" with 📶; airplane mode off:
  added 10 s later (363 s before `NetworkBack`: the outbox's backoff), the notice shown (its log line), in Moje besede.
  Teo (a child, it · de cards, the Friuli culture): «arcolaio» filled 14 s after Add with arcolaio · m., Garnwinde;
  Haspel, "✓ Aggiunta · Hinzugefügt"; «aspo» offline, added 10 s after the network came back. No crash.

## Machine-written texts to check

The app's new labels in Slovene, German and Italian were written by the machine; a native speaker should check them
(`resources/l10n/{sl,de,it}.json`): `words.didYouMean` ("Morda iščeš", "Meintest du", "Forse cercavi"),
`words.tutorWritesIt` ("Učitelj bo napisal njen pomen", "Dein Tutor schreibt seine Bedeutung dazu", "L'insegnante ne
scriverà il significato"), `words.tutorWriting` ("Tvoj učitelj piše pomen …", "Dein Tutor schreibt die Bedeutung …",
"L'insegnante sta scrivendo il significato…"), `words.joinsYourWords` ("Ko bo pomen napisan, bo beseda med tvojimi
besedami", "Sobald sie geschrieben ist, kommt das Wort zu deinen Wörtern", "Quando sarà scritto, la parola entrerà tra
le tue parole"), `words.nowInYourWords` ("Zdaj med tvojimi besedami", "Jetzt unter deinen Wörtern", "Ora tra le tue
parole"), `myWords.waiting` ("Čakajo na pomen", "Warten auf ihre Bedeutung", "In attesa del significato"),
`myWords.addMissing` ("Dodaj «{word}» med moje besede", "»{word}« zu meinen Wörtern hinzufügen", "Aggiungi «{word}» alle
mie parole"). The dev tutor's canned glosses (test/dev-bridge.ts) are for QA only. The meanings the tutor writes for a
word asked for are machine-written too, and the card says so.

## What's left

- A word the tutor can't explain (not a word at all) stays waiting until it is 60 days old; the tutor says so in the
  chat, but there is no "take it back" on the card or in Moje besede yet.
- The suggestions don't know how common a word is: two equally sure ones show in the dictionary's order.
- A word asked for on a node from before the route goes to the chat as before (the tutor explains it there); the card
  waits until the tutor's gloss comes, and the word is added with ➕ on its entry then.
