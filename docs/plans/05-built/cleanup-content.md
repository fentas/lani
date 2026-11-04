# Wave E's last cleanup, content (cleanup-content)

Items 1, 2, 3, 14, 15, 16, 17, 20, 21 and 25 of the lead kit's followups-17.md. Everything written here is machine-written
(Claude) and not reviewed by a native speaker.

## Native check

**Slovene** (Jan's wife; settled against the local Sloleks data only, no fran.si lookup was needed):
- «Ana je rada pela. Vsak večer, v stari hiši, pri peči.» (`v stari hiši`: his and Ana's old house, or does it read as this one?)
- «Tudi jaz sem hodil v šolo. Na planoti, pred sedemdesetimi leti. Taka klop je bila moja.» (`taka` or `takšna`, for an old
  man's speech. *Taka* isn't in sl.json's forms, the tap's table.)
- «Veš, pozimi je bilo v naši šoli mrzlo.»
- «Pod tako lipo, ja, pred šestdesetimi leti.» after «Ste se tudi vi tu poročili?»
- «Na Triglavu sem bil petkrat. Zadnjič sem imel že sedemdeset let.»
- The title «Janez se spominja svoje šole».
- The translations' mark «(pogovorno)» on Kärnten's and Friuli's second right choices.

**German** (Austrian, Carinthian if possible):
- **Weil and obwohl with the verb second, now right on a tap:** is each of the fourteen said? `obwohl` most of all: *Schau, einer
  hat den Preis geholt, obwohl er ist der Jüngste!* and *… obwohl uns tun schon die Arme weh.* The source below has it as a
  correction after a pause, and course-de-b1-b.md had the same doubt. The source describes Germany's German, not Austria's.
- **Still wrong, as a fronted clause isn't said:** *Weil sie ist so schwer, bleibt sie auf dem Wagen.* and *Obwohl du bist achtzig,
  gewinnst du das erste Spiel!* Typed, the table takes both as almost (`verbSecondFor` doesn't tell a fronted clause from
  another): say if they should be wrong there too.
- **The new mistakes:** *Ja, weil ist es Heidenmehl.*, *Weil hält Lärchenholz lange!*, *Sie kommt jeden Abend, weil gibst du ihr
  Brot.*, *Weil dort kommen zusammen drei Länder?* Is any of them said?
- **`auf es`, `an es`, `über es`** are wrong choices in three variants, whose whys say the form doesn't exist, and `grading/de.json`'s
  rows take them as almost (*auf (ihn|sie|es|den|die|das)*). The rows look too wide for *es*: `SaidChoicesTest` lists them as not said.
- `schauen` and `schwimmen` going either way; `dienen` with a dative; the other verbs in the motion and rest lists (`spielen`,
  `essen`, `lesen`, `kochen` as rest).

**English** (British, the North if possible). Each is a mistake that was written to replace a form people say, so please say if
anyone grown up says it: *I sawed her*, *I've see it*, *I have never see a raven*, *Did he be cold?*, *Did you be scared?*, *You did
be very brave*, *Did it be a big tree?*, *Did it be Excalibur?*, *Was anybody hurted?*, *Weren't peoples afraid of them?*, *the
family's lucks was*, *Did he uses it?*, *they used to threw*, *I'm not sure if what they are*, *…, didn't it?* after *It means*, and
*You say very well it!* And:
- **«No, I never have seen one.»** stays a wrong choice (on-the-watchtower), and it looks like something a grown-up says with some
  weight on *never*. If so, that turn needs another wrong choice. Unsourced.
- **«Sawed her? Bonny's a horse, not a plank!»**: is the joke fair at A2?
- **`mustn't` for a guess**: unsourced, as before (item 3 below).
- **`isn't it` for any verb**: `grading/en.json` has it as almost, so the two turns that offered it no longer do. Is it said in the
  North, or is it a learner's?
- *Someone which can swim* stays wrong: the table's row for a reported question reaches it after *ask*, by accident.

**Italian:**
- «Penso che tu essere perfetta come maestra!» (Teo's B1 arrival) and Giulia's reaction «Essere? Ih ih!».
- «Sebbene che il trattore sia più veloce» (older Italian has *sebbene che*: is it still a mistake to a native ear?).
- The translations' mark «(come si dice a voce)» on Kärnten's second right choices.
- The twelve talks' packs in `talks-it.md`'s table: do they fit what each talk says?

## What

**1. Primorska's facts.** Janez came to the village after Ana died (his arrival, in every telling) and grew up on the Trnovo plateau;
the school is one the village builds, and Micka calls it "čisto novo vas". So `fotografija`'s Ana sings «v stari hiši», not «tukaj»;
`stara-sola` has him at school on the plateau, a desk like this one his; `stara-sola-pec`'s «tu» is «v naši šoli»; the happening's title
and memory drop «stari». Two more that disagreed with those: `klop`'s wedding fifty years ago and here, now sixty and under a linden
like this; `stopnice-b1-zdravnik`'s last Triglav at sixty, now seventy as `v-gorah` has it. Left: «Tu smo plesali, tu smo se
ženili» in `klop` reads as what the people of such a village did, and `ribe-b2-nos`'s fifty years at this pond can stand, as the
plateau is close by. Neither says he lived here.

**2. `puzzled` on the model's markets: left.** The writers' guide (dialog-levels.md) doesn't ask for it: it names `puzzled` only in
what a voice build costs. The item's reason holds for one culture: Primorska's writers gave it to every B1 and B2 turn but the
market's eight (264 of 272), and Friuli, Kärnten and the Lake District gave it to none (0 of 789). Without it the app says "Hm? Kako,
prosim?".

**3. `mustn't` for a guess: no row, on the native list.** A row can't be scoped to the deduction sense. It sees forms, and *must not
be* against *cannot be* covers *It can't be Jack* and *It can't be done* alike, so it would let *mustn't* through for a real mix-up of
must and can, the mistake `have-to` drills. Only *must not have* + a participle is a deduction by its form, and every typed item
with *can't have* already accepts it, as do *might-must*'s (the lead's ruling of 2026-11-03: right, no label). No module, scene or
story offers it as wrong: `have-to`'s *mustn't* options are the prohibition.

**14. Italian talks.** Each of the twelve names two of packs-it's beside its own, at most six: a B1 talk B1 packs only, a B2 talk B1 or
B2 (`talks-it.md`'s table). No line changes.

**15, 16, 17, 20, 21: a wrong choice that adults really say.** A new test, `SaidChoicesTest`, grades every wrong choice of every
learner's turn against its turn's right ones with the language's table. It listed 53 against the five items' handful, one of them a
crash (below).

| Culture | Found | Second `ok`, standard reply | Left the turn | Changed to a real mistake | Stays wrong |
|---|---|---|---|---|---|
| Kärnten | 24 | 14 | | 4 | 6: two fronted clauses, `auf es`, `an es`, `über es`, the `dass` of the kopa (not a table form) |
| Lake District | 28 | | 8 | 19 | 1: *someone which* |
| Friuli | 1, and item 17 by hand | «Penso che tu sei» | | «Sebbene che» | |

- **German:** where the turn has another wrong choice, a second `ok` with the standard reply, its translations marked as the spoken
  hours are. Where it was the only one, the verb right after *weil*, or the prefix left beside its verb. The why stands.
- **A fronted clause stays wrong.** Günthner's work, as reported from her 2007 lecture at Münster: "Im Gegensatz dazu taucht weil mit
  Verbzweitstellung nur nachgestellt auf"; of *obwohl*: "Ebenso wie bei weil ist dessen Vorkommen auf nachgestellte Nebensätze
  beschränkt" ([Brix, Mein Schatz der Nebensatz](https://www.uni-muenster.de/imperia/md/content/germanistik/lehrende/guenthner_s/mein_schatz_der_nebensatz.pdf), opened and read).
- **English can't take a second `ok`:** the mark goes in `en`, which in the Lake District is the line shown and voiced, and
  `DialogReactionsTest` wants two right choices' replies apart. A reply of its own per turn would do, as Kärnten's *wegen dem* has,
  but it costs a line each and takes the turn's hint: EnglishHintTest was one turn above its mark. So the spoken form leaves a turn with
  another wrong choice (free), and elsewhere becomes a mistake on the same page. Typed, the table takes it as almost either way.
- **Italian item 17** is a second `ok` with a new third choice, not a changed one: the reaction quoted *Sei?*, so a change would have
  cost both lines anyway, and nothing is lost this way.

**25. German hints.** `schwimmen` and `schauen` are in the rest list too, so they say both cases and pick neither, as `hängt`; `dienen` is in
the verbs with a dative. Four of the eight pages with no curated turn have one now, six turns whose why was a rule and named no page:
`imperativ` (der-dobratsch), `ordinalzahlen` (beim-feuerwehrhaus), `uhrzeit` (kirchturmuhr, twice), `zahlen` (in-der-stube, twice). No
text changes. German's hints go from 1,514 to 1,520 of 2,503 turns with them. Left: adjektiv-der, possessiv, praepositionen-akkusativ and
praeteritum-kurz have no turn that honestly names them, and the six pages of turns of no one word stay as they are: both need new
turns, so new lines.

**Not an item: a crash.** `Grading.verbSecondFor` ran off the sentence's end for any accepted German sentence with *so, dass*: `same`
folds it to *sodass* across the comma, so the clauses add up to a word more than the sentence. Any answer that wasn't the accepted one
threw an IndexOutOfBoundsException, on a typed turn and in a typed exercise alike. Fixed in its own commit, with a test in
`GradingNonstandardTest`, so that the lead can take it alone.

## Voice

Never called. By distinct lines in each culture's language:

| Culture | Lines gone | Characters | Lines to voice | Characters |
|---|---|---|---|---|
| Primorska | 5 | 334 | 5 | 371 |
| Kärnten | 16 | 728 | 5 | 170 |
| Friuli | 3 | 180 | 4 | 233 |
| Lake District | 53 | 2,120 | 37 | 1,493 |
| All | 77 | 3,362 | 51 | 2,267 |

"Gone" counts every line no longer played, whether a clip of it exists or not, including the reactions and choices that left a turn,
which need nothing in their place. A voice build would take the 2,267. Memories are voiced live, and titles aren't voiced.

## Checks

On this branch, before the lead's rebase (main not merged, as the coordinator asked):

- `bunx tsc --noEmit -p .`: clean. `bun test/prompt-clashes.ts`: exit 0, no new clash. `bun run tutor-export --check`: 140 files match.
- `bun test/check-culture.ts`: primorska, friuli, kaernten, lakeland ok. `bun test/check-steps.ts`: all four ok.
- The smoke (ports 11200–11219): all checks passed.
- `:app:testDebugUnitTest`: 4,314 tests, none failed, 2 skipped. New: `SaidChoicesTest`, and a case in `GradingNonstandardTest`.
- Python: `companion/tests` and `tests` OK; `case-triggers.py --check` matches.
- No emulator QA: nothing here changes a screen, and a second right choice is a format the app already plays.

## What's left

- The native check above.
- `EnglishHintTest` stands at 1,389 of 1,543 on the German base, 90.02 %: still at its mark (item 26).
- `verbSecondFor` takes a fronted clause as almost when typed, against what the tap says now.
- `SaidChoicesTest` reads the tables only: «Penso che tu sei» was found by hand, as `grading/it.json` has no row for *penso che*.
