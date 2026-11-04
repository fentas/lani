# Built: the hint chip's rules for German (hint-chip-de, 2026-11-04)

Wave E, batch 2b, section 4 (the lead kit's followups-16.md): "📖 Namig · Hint" at a German turn said nothing but its pages'
links (`TurnHints.of`: "a turn of another language than these and Slovene gets its pages alone"). Now every page of the
German book has its rule in words, a spoken sentence and models, and the chip names what in the sentence decides the form
where the sentence shows it. What decides a case is read from case-de's trigger table, not repeated. Built on hint-chip-en's
merged tip (6bdf652af), so `Trigger.Asks`, `TurnHints.finish`, `HintSpeech.spoken`, the tie in `Forms.turn` and the cast in
`TurnHints.of` are the shared code, unchanged. Everything here is machine-written (Claude) and waits for the native check
below. The chip reads, it never grades: nothing in grading changes.

## Native check (first)

Machine-written (Claude, 2026-11-04): the strings `hint.de.*` and `hintSay.de.*` in sl, en, it, de (`_note_hint_chip_de` in
each table: 68 rules, their 68 spoken sentences, 72 trigger lines, 2 spoken openers); the 68 pages' German models and
questions in `game/GermanHints.kt`. 58 of the rules are the pages' own cards (companion/grammar/de, `card.rule`, in sl, en and
it, the markdown taken out), so they say what the book's card says. Written for the chip: the six pages without a card
(dativ, modalverben, nebensatz, perfekt, trennbare-verben, wechselpraepositionen); four changed from their card: akkusativ
leaves out "(Wen? Was?)", which the chip asks first; praepositionen-genitiv says *wegen des Sturms … wegen dem Sturm* where the
card says *Regens … Regen* (the very answers of Kärnten's "Wegen des Regens." and "Wegen dem Regen."), and adds "der, das → des,
die → der"; plural says *die Arme, die Bänke, die Taschen* for the card's *Schafe, Kühe, Blumen* (the village's answers) and
"der, die and das all become die"; nomen-verb says *eine Rolle spielen, Hilfe leisten* for the card's *eine Entscheidung treffen,
Rücksicht nehmen* (answers too). The cards have no German, so de.json's 68 rules are new (no pair learns German from German:
they show to nobody, written for completeness, as en.json's `hint.en.*`).

**For German (an Austrian speaker, from Kärnten if one can be had):**
1. **The models** (GermanHints.kt, `RULES`), the doubtful ones: *Wenn ich reich wär, kaufte ich ein Schiff.* (the short
   *wär* beside *kaufte*: natural, or *würde ich … kaufen*?), *An deiner Stelle bliebe ich hier.* (*bliebe* is bookish; said
   in Kärnten?), *Ich ginge gern mit.*, *Der Mantel hängt im Kasten.* (Austrian *Kasten* for a wardrobe), *dreiviertel neun*,
   *der am Fenster sitzende Mann*, *in Frage kommen*, *Hilfe leisten*.
2. **The questions**: "Wen? Was?", "Wem?", "Wessen?", "Wer? Wen? Wem? Wessen?", "Wo? Wohin?", "Du oder Sie?", "Wer?", "Wie
   viele?", "Wem gehört das?", "Wann?", "Wie?". Natural as a teacher's?
3. **The verb lists it leans on** are case-de's (its own native check): a verb of motion or of rest decides "Wohin?" or "Wo?"
   here, so a wrong entry shows on a card now. `schwimmen` and `schauen` are listed as motion: *Ich schwimme im See.* is rest
   (that turn has no one-word gap, so it says nothing today), and `spielen`, `essen`, `lesen`, `kochen` as rest. A verb in
   both (`hängt`) says both cases and picks neither. `dienen` isn't in the verbs with a dative, so *dass er dem Volk dienen
   wollte* names nothing.
4. **What people say** (the chip's lines): "in writing the verb goes last; in talk people also say it second" after weil and
   obwohl (grading's `verb_second`); "the genitive in writing; in talk the dative is right too" after wegen, trotz, während,
   statt (the table's `spoken`); "in writing this noun changes too; in talk it may stay as it is" for a weak noun with no -e
   (dem Bub, as `GermanForms` has it: no curated turn shows it yet). Fair to Kärnten? Is anything said there that a line
   here still teaches against?
5. **The nominative lines**: *Hier ist der Stein.* "what it is, the nominative": does "what it is" hold for *Hier ist*
   (there it is) as well as for *Das ist*?
6. **de.json** (a German base: these strings show to nobody): only if a pair ever learns German from German.

**For Slovene (Jan's wife):**
7. The trigger lines say "ob «…»:", "za «…»:", as hint-chip-en's: "ob «lege»: Wohin? Tožilnik", "ob «helfe»: ta glagol zahteva
   dajalnik", "ob «nehme»: Wen? Was? Na kar glagol deluje, tožilnik", "ob «Hund»: v svojem stavku to počne sam, imenovalnik",
   "ob «ich»: to počne ta, zato je to tisto, na kar deluje", "«Morgen» je na prvem mestu: glagol pride takoj za tem". Natural?
8. The six rules written new, and the 🔊 sentences: "Kar glagol zajame, gre v tožilnik", "zamišljeno, ne resnično", "zanikan
   je ta samostalnik".

**For English (a learner of German from English):**
9. "with «nehme»: Wen? Was? What it acts on, the accusative", "with «ich»: that one does it, so this is what it acts on",
   "the verb's own little word" for a separable prefix: plain enough?

**For Italian (a learner of German from Italian):**
10. "con «lege»: Wohin? L'accusativo", "con «nehme»: Wen? Was? Ciò su cui agisce, l'accusativo", "con «Hund»: nella sua
    frase è il soggetto, il nominativo", "«Morgen» viene per primo: il verbo viene subito dopo": natural?

## How to use it

At a German turn (a scene, a project's step, a story, a party, a visit to Kärnten in a German pair), "📖 Namig · Hint" opens
a card with:
- **what decides the form here** (the first line, when the sentence shows it):
  - the case, from the trigger table: "after «mit»: the dative"; "after «Trotz»: the genitive in writing; in talk the dative is
    right too"; a two-way preposition by the sentence's verb, "with «lege»: Wohin? The accusative", "with «liegt»: Wo? The
    dative", by a noun of time, "after «Vor»: Wann? The dative", else both, "after «In»: Wo? the dative; Wohin? the
    accusative"; "with «helfe»: this verb takes the dative"; "with «gibst»: Wem? Who gets it, the dative"; "with «nehme»: Wen?
    Was? What it acts on, the accusative"; "with «ist»: what it is, the nominative"; "after «Angst vor»: the dative";
  - the noun: "with «Tee»: its gender decides"; "after «einen»: the article shows the case already"; "after «ein»: it has no
    ending, so the adjective shows it"; "with «Socken», no article: the adjective shows gender and case"; "after «Zwei»: more
    than one"; "with «sind»: the verb says more than one"; "after «dem»: this noun changes too";
  - the verb: "who: «die Eier»"; "after «Darf»: the infinitive, last"; "after «habe»: the participle, last"; "with «gefahren»:
    its verb decides which helper"; "with «Hunger»: German says it with haben"; "with «würde»: imagined, not real"; "with
    «gern»: said politely"; "talking to «Herr Franz»";
  - where the verb goes, at a turn of the same words in another order: "«Morgen» comes first: the verb comes next"; "after
    «dass»: the verb goes last"; "after «weil»: in writing the verb goes last; in talk people also say it second";
- **the rule in words**, after the page's question where it asks one ("Wem? After aus, bei, mit, nach, seit, von, zu always
  the dative …");
- **two models** in other words than the turn's ("Like: mit meinem Bruder · bei unseren Freunden"), one with the trigger's own
  word first (after «mit»: *mit meinem Bruder*);
- the page table's rows about the trigger, and "📖 V knjigo · To the book" (as before).

The 🔊 says it in short: the opener ("After «mit»:", "With «lege»:", "Talking to «herr franz»:"), the rule's sentence ("«Wo?»
takes the dative, «Wohin?» the accusative.", the German by the app's German voice), the question, a model.

**Tap counts.** The rule at a German turn: before, the chip showed only "📖 V knjigo" (the chip, the link, read the page, Back:
2 taps and a Back); now the card says it (1 tap). The 🔊: before none at a German turn; now 1 tap more.

## What was built

1. **The German table** (`game/GermanHints.kt`, `GermanHints.RULES`): the 68 pages of companion/grammar/de, A1 to B2, each with
   its rule in words (`hint.de.rule*`), the 🔊's sentence (`hintSay.de.rule*`), models in German with other words, a question
   where the page asks one, and what to look for in the sentence, in order (`Look`, 42 kinds).
2. **The trigger table, read** (`GermanHints.Table`, `parse`): the prepositions and their cases and senses, with the dative
   people say (`spoken`); the contractions; the verbs that take a dative, or a dative and an accusative (a separable one only
   with its particle at the clause's end); the chunks with their preposition and case; the verbs of motion and of rest; the
   copulas; every determiner's forms and which end in nothing; the pronouns' and the relative's cases; the n-Deklination; the
   nouns of time. From companion/grammar/de/data/case-triggers.json, bundled as resources: nothing about a case is written in
   the code. `GermanHintTest` holds it: every preposition's line says the table's case, and a table that said otherwise would
   be believed.
3. **What decides the form** (`GermanHints.triggerOf`): the case as above; German writes its nouns with a capital, so a noun is
   plain to see. The rest are word lists: who does it, before the verb (a pronoun, a noun phrase with its determiner, a name
   and its title, a bare noun at its clause's start) or after it (a question, something else first), wer or was alone; forms
   of one verb told by their stems, the vowel a verb changes in du and er set back (*kreist, frisst, blase, nimmt*); the helper
   before an infinitive or a participle, or after a participle in a clause with its verb last; sein against haben by the
   participle; a noun of feeling with haben, an adjective with sein; kein by its noun, nicht by its verb; du or Sie by who is
   spoken to (a name, or a title and a name, set off by a comma); the relative by its preposition, its clause's verb, who does
   it there, else its noun; what isn't so (a Konjunktiv in the other half, ich wünschte, ohne at its clause's start), politely
   (gern, a question to Sie); a pair's first half (sowohl, weder, entweder, nicht nur, zwar); als or wenn by immer and the
   clause's verb; a noun that has its own verb; Partizip I or II by its noun; the passive's werden, wurde, worden.
4. **Where the verb goes** (`GermanHints.order`): a turn whose choices are the same words in another order has no one word to
   leave out, so `Forms.turn` finds no form and hint-chip-en's languages say the rule alone. In German the order is the form:
   `TurnHints.of` hands the right sentence and the choices over, and the chip names what decides: a joining word sends the verb
   last, something first has the verb next, a helper's other verb goes last, und and aber take no place. Nothing else of a
   turn changes: it stays a turn to choose.
5. **The chip** (`TurnHints.of`): a German turn takes German's table; the models' choice and the rows are the shared `finish`.
   Slovene's, Italian's and English's turns are unchanged: the German branch comes after theirs, and the one line of shared
   code that changes (a turn of whole phrases: what isn't its answer) keeps `CaseTriggers.isPreposition` for them.
6. **The card scrolls into view** (`SceneDialog.kt`, `HintCard`; its own commit, every language's chip). QA's hint-de found it:
   the card opens under the chip, and with three two-line choices the chip already stands at the panel's foot, so a tap turned
   the chip selected and opened a card nobody could see. It now brings itself into view as the app's other late cards do
   (`BringIntoViewRequester`, the school's result, the forms card), after its layout. Before and after: the shots in "Tests".
7. **Tests**: `GermanHintTest` (18): every page's rule, sentence and models in every table; the table read, not repeated;
   the triggers on the content's own turns; what people say in Kärnten; word order; models with no form of the turn; an English
   and an Italian base; and over every curated German turn (Kärnten's scenes with their counting dialogs at a number of each
   category, steps, stories, parties, arrivals), in sl, en and it: no line, row, title, model or spoken sentence gives the answer
   away. QA's step `hint-de`.

## Choices, and why

- **The table, not a list.** case-de's table is the one the lens, the trainer and the overview read; the chip reading it too
  means a corrected entry corrects all four. Only what isn't a case's is a list here: helpers, stems, the conjunctions, a few
  nouns with their own verb.
- **The two-way prepositions by the verb.** Slovene's chip says both cases and lets the sentence's meaning decide, as its
  table can't; German's table has the verbs of motion and of rest, so the card can say which. It says so only when the
  sentence's verb is in exactly one list: a verb in both (*hängt*) or in neither (*schütten, stecken, säen*), or no verb at all,
  says both.
- **The case named, never the form.** "with «lege»: Wohin? The accusative" names the case; which article that is for this noun
  is left to the learner, as Slovene's "Kam? → 4. tožilnik". The trigger tells what decides, the rule tells how.
- **What people say is never taught against.** Three lines say the written form and what people say side by side, each from
  the source that holds it: the table's `spoken` (wegen dem), grading's own `verb_second` read through `Spelling` (weil es ist
  kalt), `GermanForms`' rule that a weak noun with no -e is said as it is (dem Bub). A turn with both right names no form: the
  chip finds a trigger only for one right choice. No string of the chip calls a form wrong (`GermanHintTest` checks in three
  bases). The Perfekt is what the perfekt rule says it is: "in talk the usual past"; praeteritum's card says Austria says it.
- **The small words at a turn of whole phrases** (`GermanHints.small`). At a turn of no one word `TurnHints.of` takes the right
  choice's own words for its answer, leaving out prepositions, and hides any line that shows one. That list was Slovene's at
  every turn, so at a German one *unter*, *dem*, *mir*, *Sie*, *hätte* counted as answers and hid the rule that names them: 168
  hints showed their link alone, among them most of du or Sie (*Tanzen Sie, Herr Toni?* against *Tanzt du …*: its rule can't be
  said without both) and of the where-is-it turns (*Da, unter dem Bankerl!*). German now leaves out its own small words, from
  its table and the helpers: 44 such hints are left (with the rules below). This is looser than a form turn on purpose, and only there: a rule may name
  *mir* among the pronouns at *Mir ist kalt* against *Ich bin kalt*, as it names both at a form turn. A turn of one word's form
  keeps every form, and nothing changes for the other languages.
- **A rule shows both forms where it can.** `TurnHints.fair` hides a line that shows the turn's answer, unless it sets every form
  of the turn side by side. A rule that listed *mir, dir, ihm* alone was hidden at each *dir* against *dich*: dativ's at 32 of its
  49 form turns. So the rules written here say both ("der, das → dem, die → der", "ich → mir, du → dir; the accusative has mich,
  dich, ihn, sie"), as akkusativ's card does, and the cards above say other words than the village's answers. Measured over the
  756 form turns of the files: the rule hidden at 113 before, at 67 after, none of them on those four pages. The 67 left are
  pages whose rule is its forms (konjunktiv-zwei's *hätte, wäre*): there the rule stays hidden and the trigger line and the
  models speak, as the fairness rule wants.
- **Word order, past the English shape.** English and Italian say the rule alone at a turn of no one word; in German 57
  turns are exactly that and the order is what they test, so the chip names what decides. A separable verb's turn
  (*Ich mache die Tür zu* against *Ich zumache die Tür*) has other words, so it stays the rule alone: a line there would say
  the answer.
- **No bridge, no content.** The rules are in the app, as the others'; nothing the bridge serves changes, so an older app
  (0.2.698) keeps showing the links alone, and the new one shows the rules at once on its update.

## Accuracy

Over the curated German turns, `GermanHintTest` (its reports: build/reports/hints/de.txt, each form turn's first line, and
de-silent.txt, the hints that show only their link):

| | Turns |
|---|---|
| German turns (choices ≥ 2) | 2,037 |
| with a hint (a page of the book) | 1,349 |
| with a rule or a trigger on the card | 1,305 (97 %) |
| form turns (one word differs) | 978 |
| naming what decides | 832 (85 %) |
| turns of word order, saying where the verb goes | 57 |
| the 🔊 says something (the longest about 17 s) | 1,349 |

The same in sl, en and it. By line, every hint: who 144, more than one 139, the gender 82, the object 65, the infinitive 46, sein or haben by the participle 33, the verb next 26, after a dative's preposition 25, a verb with the dative 22, the participle 21, kein by its noun 17, a two-way preposition with both 17, the verb last 15 and with what people say 7, how something is 13, the article shows the case 13, who gets it 13, Wo? 11, Wohin? 11, the rest 1 to 10 each. The cast adds no turn in German: a name at its clause's start is taken for who does it with or without it.

The 755 distinct form sentences' lines were read one by one, run through the real `GermanHints.kt` (compiled alone with stubs: the
machine's Gradle slots were taken). Wrong, and fixed: *Alte Pfähle aus Holz.* took a plural for a verb's person; *Mit „Sehr
geehrter Herr …* read a dative into a quoted greeting; *Was ist das für ein Stein?* would have read für as a preposition;
*sein Stück Brot* took the possessive for a helper; *Das heißt Honig* named Honig; *Ja, es quietscht* took Ja for a noun; *Er
weckt dich* took dich for a reflexive; *Wohin fliegen sie?* said the verb goes last; *Der Winter kann kommen* read der as a
relative. Missing, and fixed: umlaut plurals (*Köpfe, Kopf*), the verbs that change their vowel (*frisst, fressen*), the
relative's own forms (*denen*).

**The turns with no line** are mostly a meaning (*Er kommt aus dem Wald.* against *Trog*; *auf* against *über*), a bare answer
to a line the chip can't see (*Der Nikolo.*, *Ein Bauer.*), a verb the table lacks (*dienen*), or a Konjunktiv only the dialog
before it explains (*Dann hätten wir vielleicht zu wenig Heu.*).

## What's left

- **Three tests fail on this base, none by this branch.** See "Tests".
- **Four pages have no curated turn at all** (adjektiv-der, possessiv, praepositionen-akkusativ, praeteritum-kurz; imperativ,
  ordinalzahlen, uhrzeit and zahlen got theirs on cleanup-content, six turns that named no page), and six only turns of no
  one word (als-ob, indirekte-rede, konnektoren, oesterreichisch, satzbau, wortstellung). They have their rule, sentence and
  models; what their looks find has met no sentence of the village, so it is less proven than the rest. A tutor's turn there
  gets the rule at the least.
- **A pronoun's gender** is named only when a sentence of its own names the noun (*Ein Fuchs! Er ist rot.*); the line before
  a turn isn't in the chip's reach, so *Ja, sie riecht so gut!* says the rule alone.
- **`als` against `wie`** after a comparative names nothing: people say *größer wie* (grading's `nonstandard`), and a line
  would have to pick.
- **`schwimmen`, `schauen` in the motion list** (native check 3): settled on cleanup-content. They are in the rest list too, so they say both cases and pick neither, as `hängt`; `dienen` is in the verbs with a dative.

## Tests

On this branch at 538ac50ee (the app's code last changed at b091482c0; only QA's step after it), ports 11080–11099, LANI_SMOKE_DRAMA3_PORT 11098:
- Bridge (untouched, run anyway): `bunx tsc --noEmit -p .` clean; the smoke 4,198 checks, all passed; `tutor-export --check` 140
  files match.
- Python: `python3 -m unittest discover -s tests` 253 OK, `-s companion/tests` 185 OK (11 skipped).
- App: the full unit suite through lani-slot.sh: 4,293 tests in 540 classes, 3 failures (below), 2 skipped, no errors. Among them: `GermanHintTest` 18, `TurnHintTest` 18 and
  `ItalianHintTest` 14 and `HintSpeechTest` 11 unchanged and passing, `L10nTablesTest` 6.
- **Three tests fail, and they fail on the base too.** Run alone on a clean checkout of 6bdf652af, a throwaway worktree, the same
  three fail with the same messages, so none comes from this branch (which changes no file of cultures/, grammar/ or grading/):
  - `EnglishHintTest`, "over the curated English turns …": 1,370 of 1,548 hints say more than the link, 88.5 % against its 90 %.
    Its last pass was at 1,642 turns and 1,018 hints; the merge of village-b2-lakeland (6bdf652af, after that run) brought 2,490
    turns and 1,548 hints. German's fix for the same symptom ("The small words") would likely help English too; left for its owner.
  - `SceneCoherenceTest`, "no line tells the learner to look at what isn't there": Lakeland's new -b1- and -b2- variants
    (*at-the-bank-barn/stores-b1-mice*, *by-the-beck/sheep-b2-heaf* …) say look with nothing cued or reviewed in looks.json.
  - `LearnerContentTest`, "said to Jan …": compared 215 of 431, under its half: its fixture's digests no longer match the content.
- Emulator QA, `companion/bin/qa-slot --slot 2 --no-town --apk <the tree's debug APK> --steps hint-de` (slot 2: no port in
  8790–8799), a synthetic learner (make-learner.ts --target de --base sl, LANI_CULTURE=kaernten, LANI_QA_SETUP_PAIR=de-sl,
  LANI_QA_PAIR=de-sl): ok, no crash. At the two turns of Jakob's trout by the stream the card comes into view with its line in
  Slovene: "ob «Forelle»: odloči njegov spol" and the rule with *der* and *die* side by side; "«Die Forelle» je na prvem mestu:
  glagol pride takoj za tem" at the turn of word order. The first line needs German's trigger table, so it shows the table is
  read in the APK. Shots: the session's scratchpad, de/ux-hint-chip-de/hint-de-1.png and -2.png (the run's all in
  companion/qa-shots/20261104-194743-slot2). The runs before it failed, each for a reason now in the step's docstring: a project's
  scene isn't in a new village (the Doppelharpfe); the card out of sight (the fix above; before: 20261104-190052-slot2, 12-hint-de-1.png,
  the chip selected and no card); an A1 learner's dative turn is an echo, with no card (the alp). No QA ran in en or it bases:
  those are `GermanHintTest`'s, in sl, en and it.

No line anyone hears was added or changed in the voice store (the new strings are the card's and the 🔊's, said by the
phone's voice, the German by the app's live voice): a voice build needs 0 characters.
