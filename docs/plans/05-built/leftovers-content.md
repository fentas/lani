# Built: the leftovers round, content (leftovers-content, 2026-11-05)

Branch 1 of the lead kit's followups-21.md: what the earlier rounds left of the content. Everything written here is machine-written (Claude)
and waits for the native check below. No screen changes, so no emulator QA: a new dialog is a format the app already plays, and the hint's
card is the one `GermanHintTest` and `EnglishHintTest` read.

## Native check

**German** (Austrian, Carinthian if possible). The four new dialogs of Kärnten, every line, and first:
- **The wrong choices must be mistakes nobody grown up says:** *Mein Tasche ist hier.*, *Ja, das ist sein Heft.* (of Lena's), *Ich nehme den
  schwere Hammer.*, *Für meinem Onkel.*, *Ich hattest keine Zeit.*, *Waren ihr auch auf dem Großglockner?*, *Ja, ich komme gern, deshalb ich
  habe Zeit.*, *Er sagt, er durfe heuer nicht mehr auf die Leiter.*, *Dann tut er, als wusste er von nichts.* (no spoken *als* with the
  indicative that I could name), *Nein, auf dem Schlüssel habe ich vergessen.*
- **The right ones:** *als wüsste er von nichts*, *auf den Schlüssel habe ich vergessen* (the page's own *auf etwas vergessen*), *Der Sepp steigt
  doch auf jede Leiter*, *oben gestanden*, *den Schlüssel vom Stadel eingesteckt*, *Wer Zeit hat, hat alles.* (made up, in Opa Sepp's way of
  "Wie man so sagt").
- **«Hm. Hat geholfen? Der Satz ist ein bisschen durcheinander.»** (the charcoal burner, reworded: see "Echo or leak").

**English** (a learner of English from Slovene, German or Italian): the eight rules reworded in "EnglishHintTest", in all four tables. In the
Slovene table first: «kratko z opuščajem, pripeto na besedo pred njim», «have, has sežeta do zdaj; kar je bilo še prej: had», «za predlogom
nikoli that». German: «kurz mit Apostroph, an das Wort davor gehängt», «have, has reichen bis jetzt», «Nicht geschehen: if + had …». Italian: «forme
brevi con l'apostrofo, unite alla parola prima», «have, has arrivano fino a ora».

**Slovene and Italian:** the translations of the four dialogs. Slovene: «Tu leži zvezek. Je Lenin?», «Si vsaj {m:vzel|f:vzela} ključ od skednja»,
«Na ključu? Si ga {m:imel|f:imela} za stol?», «Potem se to danes ne izide več.», «paglavec» for *Lausbub*. Italian: «Sì, è il quaderno di lei.», «Allora oggi
non ce la facciamo più.»

## Echo or leak

`DialogReactionsTest` named 17 turns by hand, since `ChoiceDiff` marks a moved word as what sets the right choice apart, and a reaction repeating the
learner's wrong sentence ("Als waren Sie?") then trips the check. The check now tells the two apart, and the list is gone.

A word the wrong choice doesn't hold is the answer, as before. A word it does hold (`echoes`) passes unless it could still give the answer away:

| Not an echo | Example | Why |
|---|---|---|
| the wrong choice holds fewer of it and has another word instead | «Bi ali bo?» at *…, bi kopa* against *…, bo kopa* | the form is the answer |
| said beside what the right choice puts next to it, as the wrong one doesn't have them | «Geholfen hat es» at *geholfen hat* against *hat geholfen* | the place is the answer |
| one of two words that trade places, stated and not asked back | «Tone hoče drobno.», where «Od spodaj?» passes | a statement tells who gets which |

**Why not simply "the wrong choice holds it too":** that passes two of cleanup-code's nine real leaks, «Bi ali bo?» and «Tone hoče drobno.». I measured the
rule on the old content before writing it, by a script that mirrors the test, then on the real test. All nine stay in the test as fixtures, since the content no
longer has them, beside an echo with the answer said after it and a word asked back while it's stated too.

**What it found.** 16 of the 17 pass. The seventeenth was no echo: the charcoal burner's «Hm. Geholfen hat es, aber der Satz ist ein bisschen durcheinander.»
says *geholfen hat*, the two words in the right order, to a learner who said *hat geholfen*. So cleanup-code's nine are ten. It now asks back what was said,
«Hm. Hat geholfen? Der Satz ist ein bisschen durcheinander.», as the other echoes do, with the Slovene, English and Italian after it. One voiced line, 58 characters.

**Its limit,** said in the comment: it reads forms, not meanings. A statement dressed as a question passes.

## EnglishHintTest

It stood at 1,389 of 1,543 on the German base, 90.02 % of its 90 %. A hint goes silent when its rule prints the turn's answer. The test now writes which ones
(`build/reports/hints/en-silent-<base>.txt`, as German's does), and most were a village's own answer printed as an example or a list.

| Rule | What changed | Turns back |
|---|---|---|
| `ruleBe` | says how the short forms are made instead of listing *I'm, you're …*; *is not, are not* for *isn't, aren't* | 13 |
| `ruleDo` | *do not, does not, did not* for *don't, doesn't, didn't* | 10 |
| `rulePastPerfect` | *have, has reach up to now* before *had*: the forms side by side, each by what it's for | 6 |
| `ruleWish` | *I have no dog: I wish I had one.* | 2 |
| `ruleWhoMore` | *after a preposition never that* | 3 |
| `rulePerfect` | *flown* for *seen*, which three turns answer | 3 |
| `rulePhrasalMore` | *We've run out of milk.* for *put up with* | 3 |
| `ruleIfHad` (German only) | *Nicht geschehen:* for *Was nicht geschehen ist:*, as the German *was* is an English answer to the chip | 1 |

**1,430 of 1,543, 92.7 %, on all three bases,** and the mark stays at 90. About 400 new hints that all stay silent would tip it. `TurnHints.fair` lets a line through
that shows every form of a turn side by side, so the three rules that do that say what each form is for: they don't print a bare "had, not have". What's left
silent is mostly a rule that can't be said without its form (*been*, *have*, *such*, *whether*).

## German hint pages

Ten pages lacked what their hint needs. **Eight have it now:** a turn where one word sets the right choice from the wrong one that names the page.

| Page | Turn | Dialog | What the chip says |
|---|---|---|---|
| possessiv | *ihr / sein Heft*; *Meine / Mein Tasche* | in-der-schule, `hausuebung-hefte` | the rule; "with «Tasche»: its gender decides" |
| adjektiv-der | *den schweren / schwere Hammer* | in-der-schmiede, `bestellung-hammer` | "after «den»: the article shows the case already" |
| praepositionen-akkusativ | *Für meinen / meinem Onkel.* | the same | "after «Für»: the accusative" |
| praeteritum-kurz | *Ich hatte / hattest*; *Wart / Waren ihr* | in-der-stube, `glockner-gestern` | "who: «Ich»", "who: «ihr»" |
| konnektoren | *, denn / deshalb ich habe Zeit* | the same | the rule |
| indirekte-rede | *er dürfe / durfe* | am-dorfbrunnen, `neuigkeiten-b2-leiter` (B2) | the rule |
| als-ob | *als wüsste / wusste er* | the same | the rule |
| oesterreichisch | *auf den / dem Schlüssel vergessen* | the same | the rule |

Each turn's answer was picked so that its rule isn't hidden: *schweren*, *meinen*, *dürfe*, *wüsste* and *den* aren't words the rules print, and where they are, both forms are
(*hatte, hattest*, *war, wart*, *denn, deshalb*, *mein, meine*, *ihr, sein*). No wrong choice is a form people say: *denn* for *dann*, *als ob* with the indicative and *er darf* are all
left out for that. A nonsense reading is fine for the reaction (*Auf dem Schlüssel? Hast du ihn als Sessel benutzt?*), as *dem* isn't the answer.

**A fix on the way.** *ihr* against *sein* would have said "its gender decides", as both are determiners. The owner picks that word, so the gender look now says nothing there
and the rule speaks.

**`satzbau`, `wortstellung`: no new line, and none can help.** A one-word turn (`Forms.turn`) needs one position to differ, and any other order changes two at the least, so a turn
that tests a place can't be one. Those two pages' 7 and 17 turns are turns of order. `GermanHints.order` reads wortstellung's ("«Morgen» comes first: the verb comes next"), and
satzbau's show the rule. **`uhrzeit`**, not on the brief's list, has the same result for another reason: each of its turns has two right sayings. `GermanHintTest` now holds it all: every page has a
curated turn, and every page but these three has a wrong choice one word off.

German's hints go from 1,520 of 2,503 turns to 1,530 of 2,513.

## Voice

Never called. A voice build would take:

| | Lines | Characters |
|---|---|---|
| The four dialogs, villagers' lines and reactions | 30 | 1,531 |
| The four dialogs, the learner's own lines (only while those are on) | 20 | 657 |
| The charcoal burner's reworded reaction | 1 | 58 |
| **All** | **51** | **2,246** |

One clip goes: «Hm. Geholfen hat es, aber …», 66 characters. The English rules are the card's strings, read by the phone's voice: 0.

## A squid

The note on the pack word *lignji* said the singular is *lignj*. SP2001 has [lígenj -gnja](https://fran.si/iskanje?View=1&Query=ligenj&FilteredDictionaryIds=134), and Sloleks 3.0 has
*ligenj*, *lignja*. The note now says *ligenj* in its three languages, a card's text, so no clip goes. Found while checking the supplement against 3.0.

## Found on the way, not done

- **Comparatives read as plain forms.** The supplement writes a comparative's label with its degree once, at the start ("comparative nominative/accusative masculine singular; dative/locative
  feminine singular; …"), and `slotsOf` reads the parts after it as plain adjective: 111 slots of 37 adjectives hold a comparative or a superlative. `sl.forms-order.json` already names the
  plain form in 49 of them. I haven't traced the other 62 through a real table: a lemma Wiktionary has takes Wiktionary's forms, so some may never show. If it shows, the fix is in `slotsOf`: a degree
  at the label's start holds for every part. Not done here, since it changes what the forms cards serve.
- **`PhoneBridgeNodeTest.aWordAndItsReview` failed once** in the full suite, at 72 s on a loaded machine, and passed alone (26 of 26, 0.26 s). The message is cut before the two outputs differ, so
  the cause isn't known. Nothing on this branch touches that path.

## What no device has shown

The four new dialogs and the reworded reaction were never played on an emulator: nothing here changes a screen, and `DialogReactionsTest` plays every dialog with every wrong choice first. What the chip
says at the new turns was read from `GermanHintTest`'s report, in Slovene, not from a card on a device.

## Checks

On this branch without the Sloleks commits, so these hold if the lead drops them; ports 11280-11299, LANI_SMOKE_DRAMA3_PORT 11298:

- `bunx tsc --noEmit -p .`: clean. `bun test/prompt-clashes.ts`: exit 0, no new clash. `tutor-export --check`: 140 files match.
- `check-culture` and `check-steps` for primorska, friuli, kaernten and lakeland: each exit 0.
- The smoke: all checks passed, 4279 lines.
- Python: `tests` 253 OK, `companion/tests` 185 OK (11 skipped).
- `:app:testDebugUnitTest`: 4,354 tests in 546 classes, 2 skipped, 1 failed: `PhoneBridgeNodeTest.aWordAndItsReview`, above, which passed when run again alone. The other 4,353 passed, among them
  `DialogReactionsTest` 8, `EnglishHintTest` 14, `GermanHintTest`, `SaidChoicesTest`, `WhoAnswersTest` and `SceneCoherenceTest`.
- No emulator QA, as "What no device has shown" says.
