# Built: Primorska's village to B2, the rest (village-b2-primorska-2, 2026-11-04)

Continues `village-b2-primorska` (its doc, same folder): the words a tap meets, Oglar Miha's cycle to B2, the checks that
agent couldn't run, and one form that was graded wrong. All of it is machine-written (Claude), flagged so; nothing was voiced.

## Native check (for Jan's wife: register and naturalness; the dictionary questions were settled below)

1. **"Nocoj gremo vsi plesati!"** (gasilski_dom, the sixth step, A2): now a second right choice (see "A spoken form" below). Is
   that how people say it here? Listed in the first doc too.
2. **Two more forms the app grades wrong, not changed** (each wants the same decision as item 1):
   - "Ali vam je kdaj strah?" (Miha, the white smoke's night talk, A2) is a wrong choice against "vas". SSKJ² has *strah me je*,
     but *mi je strah* is widely heard. If people here say it, it should be a second right choice. I didn't test it again at B1
     or B2.
   - "Pojdiva ga vprašati, koliko stane." (`scenes/pri-vratih.json`) is the other wrong choice naming `namenilnik`: the same case
     as item 1.
3. **Miha's images and sayings, his own, not proverbs.** "Dihati mora, teči pa ne." "Lačna kopa je nevarna kopa." "Dim je moj
   vremenar." "kot starec po stopnicah" (the fire going down). "Kar je še v kopi, ni oglje, ampak upanje." "Kopa se seda, kot se
   usedajo stari ljudje." "kdor tega ne sliši, naj raje kuha žgance". "bukev še raste" (his father, after a pile lost). "dva para
   oči vidita več kot eden". "Gospoda v mestu plačuje za take vonjave." Do they sound like a Primorska charcoal burner?
4. **Words SSKJ² labels, in the mouth of a man of few words:** *nared* and *povsem* (knjiž.), *vonjava*, *skregati se* (pog.:
   "ne moreš se z njim skregati"), *vremenar* (ekspr.). Would he say *pripravljeno* or *gotovo* for *nared*?
5. **"Bi rekel, da te je dim pripeljal sem."** (his meeting, B2) starts with the clitic, as people do; it is his line, not graded.
6. **The new wrong choices that name a grammar page.** Each must be wrong in writing *and* in speech, or it has to go:
   "dim nad drevesa", "pred veter", "na to mesto" for where, "na kopi" for na kopo (kam-tozilnik); "če zvečer bi pihalo", "če je ne
   hranili", "bi rad vidim", "če bi … bo", "če ne vidim …, ne bi vedel", "če zima ne bo …, bi"; "koliko polena", "koliko dneva",
   "koliko kope", "toliko noč", "sedem vreče"; "koliko kop je stala", "dvanajst vreč so polne"; "kopa bo tlel", "vsak noč", "ste ga
   dali" for ji, "nevihta izpere zemlja", "srečali kovač Tone", "srečali kdo", "vas bo žal", "vas pomagam", "bolj manjša", "se oglje
   vžgejo", "oglje, ki ji Tone". Is "bolj manjša" a child's mistake here, or does a grown-up say it? None is a dual against a plural,
   none a clitic placed first, none half-vikanje ("ste se odločila").
7. **Forms no source lists**, kept with what settles them: *nonovo* (Sloleks and SSKJ² have no *nonov*; under *nono*, which SSKJ²
   has as "nar. primorsko"), *bulina* (no headword, as *bulinu*). Sloleks doesn't list *točilom*, *opiljen* or *modrijo*; SSKJ² has all
   three ("točilo", čeb.; "opiljen" under opiliti; "prsti modrijo", its own example).

## 1. The words a tap meets

**Why everyday words counted as unknown.** Three things, none a fault of the words:

- The measure (`lexicon-build --coverage`) reads the two files a tap reads: `companion/lexicon/sl.json` and `sl.extra.json`. `sl.json`
  is English Wiktionary's Slovene, 5,774 lemmas: it has no entry at all for *skozi*, *okrog*, *verjetno*, *kateri*, *precej*,
  *praviti* or *zgraditi* (the kaikki index, 6,032 rows). So the supplement carries the everyday words.
- **The list was made before a merge.** `unknown-words.txt` (821 forms, 95.1 %) came from c3b9c4d35, with 1,449 supplement entries.
  The merge of `dialogs-primorska` right after it brought 2,887. At the branch's tip the same measure said 97.3 %, and 267 of the
  821, *skozi*, *okrog*, *praviš* and *verjetno* among them, were known. The doc's 95.5 → 95.1 was true of its moment.
- A supplement entry knows only the forms it lists: *držati* was there, *držiš* wasn't. So 94 of the entries below are forms
  added to a lemma already there.

**What was done.** Each form was looked up in Sloleks 2.0 by the form itself (its exact search finds the headword from any form,
so no lemma is guessed), then the lemma's paradigm (the grammar labels come from its tags) and its SSKJ² entry, one request at a
time, 3 s apart. The answers are cached in the worktree's `.cache/sl-sources` (7,191 pages; not committed), the tools and what they
read in `.lani-lead-kit/village-b2-primorska/` (settle.py, sup.py, gloss.txt, decide.json, settle.json). Where a form has two
lemmas (75 of them: *tanke*: tanek or tank; *skrbno*: adjective or adverb), the line it is in decided (decide.json).

| | Entries | Forms |
|---|---|---|
| The steps', parties' and arrivals' words | 342 new, 94 extended | 643 |
| Miha's B1 and B2 | 77 new, 23 extended | 133 |

All 3,306 entries load (`validateExtra`: none left out), one line each, with gloss, German and Italian. Left out, as the supplement
does: sounds (šššš, iiii, meee, hihi, á), names and places (Janša, Janezova, Idrija), German *bitte*. No deliberate wrong form was
among them: every other form is in Sloleks or SSKJ².

**Coverage** (`companion/bin/lexicon-build --coverage --lang sl`, % of tokens):

| | Before (the tip, befbba78d) | After the steps' words | At the end, Miha's included |
|---|---|---|---|
| Tokens | 178,194 | 178,196 | 186,152 |
| + the supplement | 97.3 | 98.2 | 98.3 |
| + pack words and names | 98.3 | 99.2 | 99.3 |
| Share of words, + the supplement | 88.0 | 92.9 | 93.4 |
| New tellings' forms nothing knows | 554 | 7 | 7, and Miha's 3 (idriji, idrijo, á) |

The measure takes 6 minutes (its `extraMeant` walks the supplement for every word); I left that alone.

**What the sources corrected.**

- **"lenejši"** (mlin, the first step, B2): no form of *len*. SP2001 gives "bolj ~" and Sloleks no comparative. The wrong choice and
  its reply say "bolj len"; SP2001 has the image itself ("~a reka": a slow river).
- **"z drobnimi drvi"** (mine, Miha at B1): Sloleks reads *drvi* only as *drveti*; the instrumental of *drva* is **drvmi**. Fixed.
- *plot* (vinograd B2) is a fence of stakes in SSKJ²: right, the choice is wrong on purpose and its why says so.
- *skregati se* is SSKJ²'s "pog."; *shajati* in "bova shajala" is its second entry, sound.

The kit's `json3merge.py` keeps an entry changed on one side as a new item beside the old one: after merging the supplement with
it, look for the same lemma and part of speech twice and keep the longer (94 + 23 entries here are extended in place).

## 2. Oglar Miha's cycle to B2

`world.json` `spots.kopa`: **all 62 levelled pieces have B1 and B2** (33 talks, 11 `done`, the cycle's meeting, 3 greetings, missed,
the breakout's four, 2 nights, the keeper's own meeting, 6 tales), and the pile's `story` too: 124 tellings and 2 stories.
`bun test/check-culture.ts primorska`: ok.

| | Talks | Lines | Turns |
|---|---|---|---|
| A1 | 35 | 3–5, 3.5 on average | 1.03 |
| A2 | 35 | 3–6, 3.8 | 1.03 |
| B1 | 35 | 3–6, 3.8 | 1.03 |
| B2 | 35 | 3–6, 4.3 | 1.34 |

**Each piece keeps its place.** Every level of a piece is the same step of the work with the same small task, so a learner whose level
rises keeps their place (`KeeperTold` is a count) and the picture over his talk still fits. The night's, rain's and wind's talks stay
that; a piece that takes no turn at A2 takes none here. A B1 telling joins the sentences (da, ker, ko, ki, če); a B2 telling has the
conditional, reported speech and relative clauses, and 12 of the 35 have a second turn, of understanding: the reply that fits what
he means. Spoken Primorska is never a wrong choice. Ti/vi follows his card: he says ti, the learner vi.

**The clock.** `breakout.seconds` is 18 at B1 and B2, as at A2: the choices there are longer, not shorter.

**The older app.** `levelFor` has known B1 and B2 from the first; each piece is read on its own (`SpotCycles.linesAt`), so nothing
needs holding back. No new key. Keeper talks count in `content-count`'s 44 as before: it counts talks, not levels.

**Facts the lines state.** All from GAME.md's sources for the pile (Društvo oglarjev Slovenije, Oglarska dežela Dole, Ilc 2011,
Hotedršica, Gozdarsko društvo Bled, BR's film): the dimnice a hand or two apart (15–30 cm), the smoke's colours, the cross of slats,
the charcoal to Idrija by cart, only the smith judges it. "Še pred sto leti" (the story at B1) is general. His father's hat and his
mother's sewing are the character's own; no custom is claimed.

## A spoken form: "Nocoj gremo vsi plesati!"

**Could the table hold it? For a typed answer, yes; a tap has no "almost".** `nonstandard` lives in `Grading.check`. A dialog's tap
choice is `ok` or wrong (`DialogChoice`), so it can't be almost without a new field, which an older app wouldn't read.

- **Typed** (a turn that has turned into a gap): `grading/sl.json` has its first `nonstandard` row: *plesati* for *plesat* after a verb
  of going. Almost, the supine shown, no mistake recorded. The row is per word on purpose: `word` and `for` aren't tied to each other,
  so a general rule would pass "delati" for "plesat".
- **Tapped:** a second right choice with the standard's reply, as the lessons say and "Ob pet?" (kuhinja) does; its translation says
  "(as people say it)". The turn keeps its wrong one, "spat", which is what tests the word.
- **The page:** `namenilnik` now says people also say the infinitive there, and writing wants the supine (en, de, it).
- **The clip doesn't change.** The learner's "Nocoj gremo vsi plesati!" keeps its text and its clip; the reply is the standard's, which
  has its own. Nothing to spend. Only the translations changed.

## 3. The checks

QA's two paths take a level: `LANI_QA_STEP_LEVEL=B2` plays `project-step` at that telling, its reactions read from the file, and
`LANI_QA_MEET_LEVEL=B2` gives `parity-small`'s meeting its level (B1 unless set).

The checks ran after merging main (85917a636; only `bin/qa`'s step list conflicted, `qa-steps-union.py` took both):

- `bun test/check-culture.ts primorska`: ok. `bun test/check-steps.ts primorska`: 13 files, 78 steps, 312 dialogs, 2,103 lines, ok.
  `bun test/check-celebrations.ts primorska`: 5 celebrations, 20 parties, ok. `bunx tsc --noEmit -p .`: clean.
  `tutor-export --check`: 140 files match.
- **The Android suite** through lani-slot.sh (JDK 21): 4,196 tests, 2 skipped, 3 failed in the whole run, which started before the last
  fixes. Two of the three are fixed and pass in a rerun of those classes (below). **The third stays red, and not for Primorska:**
  - `GradingNonstandardTest` pinned "only Slovene has none": it has one row now.
  - `CultureParityTest`: the card and his meeting at B1 and B2 are four new probes, as its fixture's note asks; none changed.
  - `SceneCoherenceTest` "no line tells the learner to look at what isn't there": **20 Lake District lines remain** (`at-the-bank-barn/
    stores-b1-mice` and 19 more: every one in `companion/cultures/lakeland/scenes/`, from `dialogs-lakeland`, not in main). It came here
    through the merge of `dialogs-primorska`. That branch's owner cues or reviews them; I didn't touch another agent's lines.

The first run of the content tests (Arrivals*, Celebrations*, ProjectSteps*, SceneCoherence, StepScenesRender, DialogReactions,
WhoAnswers, IntroductionContent, Kopa*, Keepers, SpotCycles) found what the first agent couldn't have seen:

- `KopaCulturesTest` took A2 for the top of a card; it reads Primorska's B2 now.
- `SceneCoherenceTest`'s count of done lines: Primorska's 46 at B1 and B2 added to the 46 at A1 and A2.
- Ten looks with nothing cued, now settled. Reviewed in `looks.json`, as their A2 is: Miha's six at the pile (it is drawn over his talk),
  the bridge's plank stack at B1, the forest at mlaj B2 (the tap turn picks a spruce), and "Samo glej, da …" (mind you don't; nobody looks).
  Rewritten: the Vas party's "Glejte jih, kako se vrtijo!", which is "Kako se vrtijo!" now, since I couldn't show dancers are drawn.

`KopaCycleTest` plays every talk, the tales and the race at all four levels now, not A1 and A2. `GradingLanguagesTest` holds the supine's
row, in the gap and in the whole sentence, and that it says nothing elsewhere.

**Not yet run at this commit:** the smoke (LANI_SMOKE_PORT=11020, LANI_SMOKE_DRAMA3_PORT=11038) and the emulator QA of a B2 step and a B2
arrival wait for the machine's heavy slots.

## Voices (nothing spent)

Miha's new tellings, distinct texts against his A1 and A2, said to Jan: his lines and replies 28,177 characters (381 texts), the
learner's choices 8,091 (168 texts): **36,268 in all**, on top of the first doc's 151,355. The pile's story isn't voiced. The voice notes
call 77k more than a month's credits; Jan decides when. His voice is the cast's; nothing new to cast.
