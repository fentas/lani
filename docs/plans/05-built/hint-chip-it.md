# Built: the hint chip's rules for Italian (hint-chip-it, 2026-11-03)

Wave E, section 6 (the lead kit's followups-14.md): "📖 Namig · Hint" at an Italian turn said nothing but its pages' links
(`TurnHints.of`: "a turn of another language than Slovene gets its pages alone"). Now every page of the Italian book has
its rule in words, a spoken sentence and models, and the chip names what in the sentence decides the form where the
sentence shows it. Everything here is machine-written (Claude) and waits for the native check below. The chip reads, it
never grades: nothing in grading changes.

## Native check (first)

Machine-written (Claude, 2026-11-03): the strings `hint.it.*` and `hintSay.it.*` in sl, en, de, it (`_note_hint_chip_it`
in each table: 70 rules, their 70 spoken sentences, 31 trigger lines, 5 spoken openers); the 70 pages' Italian models
and questions in `game/ItalianHints.kt`. The de, sl and it strings were translated from the English by a helper agent
(Claude) and checked by script (same keys, placeholders, «» marks, the same Italian forms in each).

**For Italian (a native speaker of Italian, or the Friuli tutor):**
1. **The models** (ItalianHints.kt, `RULES`), the doubtful ones: *Essendo arrivata tardi, ha cenato da sola.*, *Finita la
   cena, …*, *Il pane l'ho comprato io.*, *meno freddo dell'inverno*, *Chiunque venga, è il benvenuto.*, *Se fosse venuta,
   l'avrei vista.*, *Avrei dovuto dirlo.*, *Vorrei che venissi.*, *Ci vuole un'ora. · Ci vogliono due ore.*
2. **The questions**: "Tu o Lei?", "Dove?", "Chi?", "Quando?", "Quanti?", "Di chi è?", "Che ore sono? A che ora?", "Com'era?
   Che cosa è successo?" (the two pasts). Natural as a teacher's?
3. **it.json** (an Italian base, for a learner of Slovene from Italian: these strings show only if one learns Italian from
   Italian, which no pair does; written for completeness): `hint.it.ruleLinking` is awkward ("anche se un ostacolo che non
   ferma"); `hint.it.more` says "un confronto, maggiore o minore" to avoid più, meno and di, which the line would show.

**For German (Teo's base; a child of 7, "du"):**
4. `hint.it.ruleArticle` says "Der, die, das: … Ein, eine: …" for the Italian articles, as the page's own card does;
   German genders differ from Italian ones. Clear for a child, or "Bestimmter Artikel: … Unbestimmter: …"?
5. "Grundform" for the infinitive throughout (the pages say both "Grundform" and "Infinitiv").
6. The 31 trigger lines ("mit «tetto»: nach seinem Geschlecht, seiner Zahl und seinem ersten Laut", "du sprichst mit
   «maestra»"): plain enough for a child read to by a parent?

**For Slovene (Jan's wife):**
7. The trigger lines say "ob «{word}»: …" where English says "with «…»" (s or z can't be chosen before a word unknown
   in advance), and "komu govoriš: «{word}»" for "talking to". Natural, or "pri «…»"?
8. `hint.it.rulePluperfect` writes "v imperfetto" uninflected, to keep the Italian term as it is (the pages sometimes
   write "v imperfettu").
9. `hint.it.ruleQuestions` keeps "da ali ne": da is also an Italian preposition, so a turn of da would leave the line out.

## How to use it

At an Italian turn (a scene, a project's step, a story, a party, a visit to Friuli), "📖 Namig · Hint" opens a card with:
- **what decides the form here** (the first line, when the sentence shows it): "with «tetto»: its gender, its number
  and its first sound" at *Manca ____ tetto.*; "who: «le api»"; "talking to «maestra»" (tu or Lei); "with «scelto»:
  its verb decides, essere or avere"; "with «fame»: Italian says it with avere"; "after «tre»: more than one"; "after
  «Bisogna che»: the congiuntivo"; "«Se» in the other half: this half says what follows"; "after the preposition «di»"
  (cui); "with «la cioccolata»: one or more decides the verb" (piace); "with «quattro»: the hour decides" (alle);
- **the rule in words**, after the page's question where it asks one ("Tu o Lei? Who are you talking to? Family,
  friends and children: tu. An adult you don't know well, the teacher, a shopkeeper: Lei …");
- **two models** in other words than the turn's ("Like: il pane · lo zaino · l'orso · la mela · l'ape"), one with the
  trigger's own word first (after «più»: *più forte del vento*);
- the page table's rows about the trigger, and "📖 V knjigo · To the book" (as before).

The 🔊 says it in short: the opener ("Talking to «maestra»:"), the rule's sentence ("Family, friends and children:
«tu»; an adult you don't know well: «Lei».", the Italian by the app's Italian voice), the question, a model.

**Tap counts.** The rule at an Italian turn: before, the chip showed only "📖 V knjigo" (the chip, the link, read the
page, Back: 2 taps and a Back); now the card says it (1 tap). The 🔊: before none at an Italian turn; now 1 tap more.

## What was built

1. **The Italian table** (`game/ItalianHints.kt`, `ItalianHints.RULES`): the 70 pages of companion/grammar/it, A1 to B2,
   each with its rule in words (`hint.it.rule*`, from the page's card where it has one, else its rule's first part),
   the 🔊's sentence (`hintSay.it.rule*`), models in Italian with other words, a question where the page asks one, and
   what to look for in the sentence, in order (`Look`).
2. **What decides the form** (`ItalianHints.triggerOf`), from word lists, no dictionary, at the turn and offline, and
   only where the sentence shows it plainly (31 lines): the noun after an article or a fused preposition (only when one
   of the turn's forms is never a pronoun: *il, i, un, uno, una, alla*: "Lo facciamo volare" has no noun); the noun an
   adjective or a participle goes with: after it, before it, or the subject of essere (*Il cesto è pieno*, *la corteccia
   è stata tolta*, *Questa è gialla*), only at a form of one word (*pieno, piena*; not *pompa, sedia*); who does it (a
   pronoun of the subject, not after a preposition: *con noi*; or the noun phrase right before the verb, *l'agnellino
   non beve*; not after a modal, where the gap is its infinitive); who is spoken to (a clause of one word, or a title
   and a name, set off by a comma and ending its sentence, or the text's first before a comma: a person, a title, a
   group, an animal, a name; *Buongiorno, signora Marisa! Cosa compra?*; for an adjective only when its half is about
   them, a verb of you or the adjective alone: *Sei buffo, Tommaso!*, *Brava, Bianca!*, not *Grazie, Matteo! Sono
   buonissime.*); essere or avere by the participle after them, by a feeling or an
   age, by a reflexive pronoun; the participle after its helper; a number or a word of the plural before a noun, ogni and
   qualche, un and una; the congiuntivo after its words (*spero che, bisogna che, vorrei che, sebbene, magari, come se*),
   not at a noun; se in the gap's half or the other; the preposition before cui, the noun before che, il quale's noun;
   the modal, vorrei, fare, per before an infinitive, the verb before a or di; stare before a gerund, stare per; what is
   liked, what there is, what si goes with; the hour after alle; the place after a, in, da; più or meno before di; ce la;
   mi before lo, la, ne; a verb of saying before che; a word of time (ieri, domani, mentre, di solito, all'improvviso).
3. **A trigger of the language's own** (`Trigger.Asks` in game/CaseTriggers.kt): the word or words, and the card's and
   the 🔊's string keys. The card (`TurnHints.trigger`) and the 🔊 (`HintSpeech.opener`) say it; a row of the page's table
   is shown when it has all of a trigger's words.
4. **The chip** (`TurnHints.of`): an Italian turn takes Italian's table; the models' choice and the rows are the same
   code as Slovene's (`finish`). A Slovene turn is unchanged (the same 1,066 hints, 1,053 saying a rule, 459 of 479 case
   turns with their trigger, as before; TurnHintTest and HintSpeechTest pass unchanged).
5. **Tests** (`ItalianHintTest`, 12): every page's rule, sentence and models in every table; the triggers on the content's
   own turns; models with no form of the turn; Teo's pair in German; and over every curated Italian turn (Friuli's
   scenes with their counting dialogs at a number of each category, steps, stories, parties, arrivals): no line, row,
   title, model or spoken sentence gives the answer away.

## Choices, and why

- **Word lists, not a dictionary.** The chip works at the turn and offline, as Slovene's does; Italian has no lexicon in
  the app (the bridge's isn't bundled). A list finds a trigger only where the sentence shows it plainly, and says
  nothing where it can't tell: *lo* and *la* are articles and pronouns, so a turn between them names no noun; a name at
  a sentence's start is not taken for a subject (*Adesso* would be); a choice of two meanings names no agreement.
- **The trigger tells what decides, the rule tells how.** "with «tetto»: its gender, its number and its first sound"
  leaves the article to the learner; the rule beside it lists every article, so it shows the turn's forms side by side
  (fair). A line that would show the answer alone is left out, as for Slovene (`TurnHints.fair`), which is why the
  rules name the forms of a choice together (*di … come*, *c'è … ci sono*, *a … di*).
- **German for a child.** Teo reads German: the German strings say "du", plain words, and keep the Italian terms
  (congiuntivo, imperfetto) as the German pages do.
- **No bridge, no content.** The rules are in the app, as Slovene's; nothing the bridge serves changes, so an older app
  (Teo's 0.2.698) keeps showing the links alone, and the new one shows the rules at once on its update.

## Accuracy

Over the curated Italian turns (main f9339ab8's content), `ItalianHintTest` (its report: build/reports/hints/it.txt,
each form turn's first line):

| | Turns |
|---|---|
| Italian turns (choices ≥ 2) | 1,748 |
| with a hint (a page of the book) | 1,034 |
| with a rule or a trigger on the card | 997 (96 %) |
| form turns (one word differs) | 879 |
| naming what decides the form | 596 (68 %) |
| the 🔊 says something (the longest about 18 s) | 1,027 |

By line: the plural's number 131, the article's noun 109, the adjective's noun 83, who 74, avere's feeling 32, un or
una 31, the one spoken to 27, essere or avere by the participle 26, what is liked or there 20, the participle after
its helper 10, the infinitive 9, the rest 1 to 9 each. The report's distinct sentences of who, the adjective's noun,
the one spoken to, what is liked, un or una, avere's feeling and the participle (about 200) were read one by one, the
other kinds by sample: two kinds were wrong and are fixed (the one spoken to named at an adjective about something
else, *Grazie, Matteo! Sono buonissime.*, *È giallo, maestra!*; who at an infinitive after a modal, *Posso provare
anch'io?*). The turns without a trigger are a verb's ending with no subject in the sentence (*Tiro forte!*), an
adjective of no noun (*Sono buonissime.*), a name at a sentence's start (*Giulia è stanca.*), *lo* or *la* (a pronoun
or an article), numbers, question words.

## What's left

- **German and English** come after case-de (followups-14.md).
- **Names at a sentence's start** (*Lorenzo ha sei anni*, *Tommaso è più grande*) aren't subjects to the chip: it can't
  tell a name from *Adesso* without the scene's cast. Passing the scene's people to `TurnHints.of` would do it.
- **Turns that test a meaning first**: *Bisogna che le fondamenta sono pronte* is a wrong choice of the congiuntivo's
  page, but its turn has a wrong choice of meaning too (*campane*) at another word; `Forms.turn` takes the first on a tie,
  so the turn's gap is the meaning, it names no page and has no hint. 119 such turns in Friuli's files (counted on
  f9339ab8: one wrong choice of meaning at the gap `Forms.turn` takes, the page on another), e.g. *Il legno è più forte
  della carta.*, *Alle api piace il trifoglio.* Either the content gives the meaning's choice the page too, or
  `Forms.turn` prefers the slot whose wrong choice names one; both change Slovene's turns as well, so not here.
- **Agreement with the one spoken to** is named only when the adjective's half is about them (a verb of you, or no noun
  of its own); *Attento alle pecore, Davide!* names nothing.

## Tests

On this branch (ports 10410–10429; no bridge change, no smoke needed):
- App: `ItalianHintTest` 12, `TurnHintTest` 16 (unchanged: 1,066 hints, 1,053 saying a rule, 459 of 479 case turns with
  their trigger), `HintSpeechTest` 11 (1,053 of 1,066 say something, as before), `TurnHintLensTest` 1, the l10n tests
  (`L10nTablesTest` 6: every key in sl and en, none unused) through lani-slot.sh. Kotlin compiled in-process
  (`-Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx5g`): the shared Kotlin daemon ran out of
  memory twice with other builds on it.

No line anyone hears was added or changed in the voice store (the new strings are the card's and the 🔊's, said by the
phone's voice, the Italian by the app's live voice): a voice build needs 0 characters.
