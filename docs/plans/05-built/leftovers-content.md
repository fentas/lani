# Built: the leftovers round, content (leftovers-content, 2026-11-05)

Branch 1 of the lead kit's followups-21.md: what the earlier rounds left of the content. Everything written here is machine-written (Claude)
and waits for the native check below. One line on a screen changes, a table's credit; see "On a device" for what was played.

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

**`pojti`:** the supplement's lemma for *pošla*, "to run out", while Sloleks 3.0 has *poiti*. Which is the head? Not looked up.

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

## Comparatives read as plain forms

The supplement writes an adjective's degree once, at a label's start ("comparative genitive feminine singular; nominative feminine plural"), and each part was read alone, so what came after it was
the plain adjective's: *točnejše* stood as *točen*'s neuter, *najšibkejši* as *šibek*'s plural. **Two doors, not one:** `slotsOf` for the table and `adjectiveForms` for a forms card's cases split a label the same
way. Both now read it through `readingsOf`, where the degree holds for every reading. None of Wiktionary's 322 strings starts with a degree, and all read as before.

The 111 slots of 37 adjectives counted `slotsOf` alone. Traced through `formsOf` and `formsCardOf`:

| The 111 | Slots |
|---|---|
| `sl.forms-order.json` already named the plain form | 49 |
| Wiktionary takes the lemma, so the supplement's label never reaches a table (17 adjectives) | 51 |
| reached a table with no correction | 11, of them 3 with the wrong form first |

**What a learner could have seen,** with the 40 adjectives such labels belong to: 18 take Wiktionary's forms and 2 have no plain form to mix with, which leaves 20.

| Where | Adjectives | Slots | What it did |
|---|---|---|---|
| a tap's word table, which lists a slot's forms | 20 | 60 | a comparative beside the right form; first in 3: *najšibkejši*, *najljubši*, *močnejši* as the masculine plural |
| a forms card, its answer | 5 | 39 | a comparative or superlative shown as the answer: *ljub* 16, *pameten* 9, *viden* 6, *šibek* 4, *močen* 4 |
| a forms card, what it accepts | 20 | 440 | a comparative taken as right |

Only *pameten* of those five is a pack word (B1, `znacaj`), so on a card it's the one a learner meets by learning a pack; the other four need a tapped word saved first. All 20 stand in the content, so a tap reaches each. A learner's deck
wasn't read: this is what the content allows, not what anyone met.

**A card made before** keeps what its slots held, so GET /state serves it without them, as it serves a card's order: `degreeForms` gives the forms that are only a degree, and `cardInOrder` takes them out of a plain slot, shown or accepted.
Nothing is written and no deck is migrated. **A slot of nothing else goes,** with what tested it: *pameten*'s nine plural cases held only comparatives, as its entry leaves out *pametnih*, *pametnim*, so its card asks nine slots fewer until
they're added. After the fix the same trace finds 0 slots in tables and on cards. The smoke holds it for every such adjective.

## A table's credit

**A correction to my first report:** I wrote that Slovene tables have no attribution. The bridge did send Wiktionary's for them, and the forms overview showed it. What was missing was the word card's line, which `WordFormsTable` showed for other languages only, any line on a
hand-checked table, and any mention of Sloleks.

`formsAttribution` now credits both of the bridge's table answers: Wiktionary for the forms that are its, Sloleks for any Slovene table, since `sl.forms-order.json` reads every one. So it holds whichever way the supplement goes. The word card shows the line for Slovene: «📖 Wiktionary (CC BY-SA 4.0) via
kaikki.org; Sloleks 3.0, CJVT UL (CC BY-SA 4.0)». `FormsCard.credit` says the same on the two paths no answer reaches, the table after a forms card and one from the phone's cache. A word with no table has no line, and a tapped interjection's card says nothing of Sloleks.

## Found on the way

- **`PhoneBridgeNodeTest.aWordAndItsReview` failed once** in a full suite, at 72 s on a loaded machine, and passed alone (26 of 26, 0.26 s) and in the next full run. The message is cut before the two outputs differ, so the cause isn't known. Nothing on this branch
  touches that path.
- **`narrow()`** in `lexicon.ts`, which trims a label for the word card, reads the same labels part by part, so a tapped comparative's grammar can lose its degree there. It's display only and fills no slot, so it stays.

## On a device

Two steps of `bin/qa`, only when named, each on an emulator at 1080 × 2400 with a synthetic learner. Shots: the session's scratchpad, ux-leftovers-content/.

**`kaernten-leftovers`** (`make-learner.ts --target de --base sl`, LANI_CULTURE=kaernten, LANI_QA_PAIR=de-sl): Opa Sepp's `glockner-gestern` in the Stube, each turn's wrong choice first, then the charcoal burner at B2.

| Turn | Wrong choice | Reaction on screen |
|---|---|---|
| 1 | *Ich hattest keine Zeit, Opa Sepp.* | «Ich hattest? Ich, du, wer denn jetzt?» |
| 2 | *Waren ihr auch auf dem Großglockner?* | «Waren ihr? Ihr, wir …» |
| 3 | *Ja, ich komme gern, deshalb ich habe Zeit.* | «Deshalb ich habe? Der Satz hinkt ja mehr als ich.» |
| Köhler Michl, the stage `anzuenden`, B2 | *Ob das Kreuz hat geholfen, …* | «Hm. Hat geholfen? Der Satz ist ein bisschen durcheinander.», and «Geholfen hat es» nowhere |

**`table-credit`** (a Slovene learner): *točen*'s "📋 Vse oblike · All forms" ends with «Wiktionary (CC BY-SA 4.0) via kaikki.org; Sloleks 3.0, CJVT UL (CC BY-SA 4.0)», and none of the 246 texts read there is a comparative. Once the word is added, its card's table
ends with the same line after «📖». So the slot fix and the credit are both seen on a device.

**What the runs taught.** A new learner isn't asked these turns at all, as their rules aren't introduced: played plainly the dialog ran to "Ausgezeichnet! Ohne Fehler" with no choice, which is the app working as designed. So the step uses QA's "turns:type" and "let
me choose": it shows the turns as a learner meets them once the rule is theirs, not as an A1 learner does. The table under a word shows only for a word the learner has; a new word's way in is "All forms". `setup` also failed three times with "home did not load" over the notification
prompt, with Home right there under it. That's QA's, not the app's, and leftovers-app-qa had fixed it already, so this branch cherry-picks its 81be298bf.

**What no device has shown:** `hausuebung-hefte`, `bestellung-hammer` and `neuigkeiten-b2-leiter`; the hint's card at the new turns, read from `GermanHintTest`'s report; a card made before the slot fix, as GET /state serves it (the smoke holds that); and a tablet.

## Checks

Without the Sloleks commits, so these hold if the lead drops them; ports 11280-11299, LANI_SMOKE_DRAMA3_PORT 11298:

- With the forms fix, the credit and QA's steps: `bunx tsc --noEmit -p .` clean, the smoke all passed (4,284 lines), `FormsCardTest`, `WordFormsTest` and the `test_qa*` Python tests.
- Before them, at 214f3b902: `prompt-clashes` exit 0, no new clash; `tutor-export --check` 140 files; `check-culture` and `check-steps` exit 0 for the four cultures; Python 253 OK and 185 OK (11 skipped); `:app:testDebugUnitTest` 4,354 tests in 546 classes, 2 skipped, 1 failed:
  `PhoneBridgeNodeTest.aWordAndItsReview`, above, which passed when run again alone.
- The full Android suite with the forms fix and the credit ran on the branch's tip, with the Sloleks commits on it. Without them it wasn't run again: those commits change `source` strings, one entry, citations and their own tool and test.

## Sloleks

**Commits of their own, last, so the lead can drop them; Jan may yet choose to ask CJVT instead.** Everything above holds without them.

`viri.cjvt.si/sloleks`, which `sl_sources.py` read, is Sloleks 2.0, CC BY-NC-SA. The supplement is now rebuilt from the real 3.0 release (CC BY-SA 4.0, http://hdl.handle.net/11356/1745),
read by `lexicon/tools/sloleks3.py` and never unpacked or committed.

| `sl.extra.json` | |
|---|---|
| read from the 2.0 site, now citing a 3.0 entry by its key | 2,154 |
| said Sloleks has no such word, now citing 3.0, which has it | 95 |
| all forms of those 2,249 entries, each in the entry it cites (`sloleks3.py check`) | 26,065 |
| of them kept by another source the entry names | 2 |
| links to the 2.0 site left | 0 |
| forms added | 0 |
| forms lost | 1, *lignj*, below |

The brief's count was 2,182 in full, 20 in part and 60 with no entry. It stands at 2,188, 14 and 60, since cleanup-code took out the page labels. "Citing Sloleks" there meant any mention: 108 of the 2,262 said "Not in Sloleks" and
never came from the site.

**Only `source` changes, and one entry.** A script compared the file before and after: 3,410 entries both, `source` differing in 2,252, `forms` and the lemma in one. Glosses and meanings don't move.

**Labels stay as written.** Redoing them from 3.0's tags was the first plan, and I dropped it: it took 269 slots out of the tables. The labels aren't only tags: *kmečki*'s own lemma is what 3.0 calls definite, so its table lost `adj.m.sg`, and the animate
accusatives (*parklja*) are written by hand. So each reading is checked instead: 491 read otherwise than 3.0's tags (`check --labels`). 444 are comparatives with their degree written once at the start, the
rest hand-written ones such as a name's *name* or a plural-only noun's *nominative singular*.

**The 74 settled by hand** (`tools/sloleks3-settled.json`):

| What | Words | Result |
|---|---|---|
| 3.0 lists the verb without *se* | počutiti, splačati, prepirati, šaliti, zahvaljevati, izplačati, maščevati, obrestovati, ozreti, pojaviti, ponašati, zateči, znajti, znebiti | every form in 3.0 |
| 3.0 has another class | dvojen (numeral), oba, oboje, takšen (pronoun), namreč (conjunction), vred (adverb), pozdravljen (adjective), gregorjevo, florjanovo (adjective) | every form in 3.0 |
| a form of another lemma | premočan (premočen), mlinci (mlinec), natančneje (natančno), kasneje (kasno), Kosci (kosec), poldne (poldan), pojti (poiti), bančni … trajnostni (9 adjectives in -en), nosila … statve (7 plural-only nouns), zanj, zanjo, nanje, nanjo, ponj, ponje | every form in 3.0 |
| a participle or possessive beside the word | prižgana, zložena, pokopani, sprejeta; prešernovih, cankarjevi, gregorčičeva; Ressel's *josef* | in 3.0's own entries, both keys cited |
| **3.0 lacks it, another source names it** | *frtalj* and *bulina, bulinu*: the regular endings, confirmed by `checkNoun`; *nonovo*: a noun's regular possessive, as the case lens reads it; *pomogel, pomogla*: [SP2001](https://fran.si/iskanje?View=1&Query=pomo%C4%8Di&FilteredDictionaryIds=134), "neobč."; *soline*: the forms book's regular declension; *sladkosned*: SP2001, regular endings; *Črni Kal*: SP2001 | kept |
| **Neither gives it** | *lignj*: the word is [ligenj](https://fran.si/iskanje?View=1&Query=ligenj&FilteredDictionaryIds=134), SP2001 and 3.0 | the form goes, the entry is *ligenj* now |
| **No source, and they stay** | *lp, huu, uau, bee, mm, zzz, klek, tuuu* | the lead's call: they never came from 2.0, and a tap needs them |

Two fran.si lookups by hand, *pomoči* and *ligenj*. The viewer wasn't touched.

**What a learner would notice: nothing.** No word lost a form anyone can meet: *lignj* stood only in a pack's note, fixed on its own, and no card or module asks it. No table, card or lookup changes. The forms are the ones they were.

**The citations.** The 13 forms-book tables cite 3.0 by key. What 3.0 lacks in them is SP2001's, as each note says (*zobema*, *vratmi*, *grobeh*), or a variant Wiktionary's table has (*moste*, *slapah*, 30 forms), and `meta.review`
says so. `delezja` and `delezniki` cite 3.0: all 73 words of their two tables are in it, checked by a pass over the release. NOTICE.md has a row for the supplement, `meta.sloleks` in the file says the same,
and `forms-tap.md` is corrected. Older plan docs stay as history. `test_sloleks_citations.py` fails on any link to the site or mention of 2.0 in those files.

**For Jan:**
- **The decision itself.** This or asking CJVT. Cleanup-code's point stands: 3.0's record says it keeps 2.0's entries and says nothing about relicensing them. This rebuild makes 3.0 the source of every form, it doesn't answer that question.
- **3.1** (11356/2080, the same licence) replaces 3.0 and adds 7,001 spoken lexemes. Not used.

**Voice:** none. No line changes.

**Reproducible.** `sloleks3.py index` was run afresh from the release (`Sloleks.3.0.zip`, its MD5 checked): 365,340 entries read, 8,391 of 8,875 lemmas found, as `wanted` asks for the settled ones too. `check` on that index alone exits 0: 2,249 entries,
26,065 forms, no key missing, 2 forms kept by another source. The index is in the kit's `sloleks-3.0/`, not in the repository.

**Checks with both commits,** the whole branch, every item above in it: tsc clean, `prompt-clashes` exit 0, `tutor-export --check` 140 files, `check-culture` and `check-steps` exit 0 for all four cultures, the smoke all passed (4,284 lines), Python 253 OK and 188 OK (11 skipped), `:app:testDebugUnitTest` 4,355 tests in 546 classes, none failed, 2 skipped, `PhoneBridgeNodeTest` 26 of 26 among them.
