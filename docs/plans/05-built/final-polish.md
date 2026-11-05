# Built: the last small branch (final-polish, 2026-11-05)

Four small items before the final report. Everything written in a language here is machine-written; "Native check" lists it.

## Three role-plays at A2 for the Slovene village

Slovene had none at A2 (A1 6, B1 6, B2 6; German and English have 3, Italian 2), so nothing stood between the market and the B1 call to
the surgery. Now 21, A1 6 · A2 3 · B1 6 · B2 6, for a grown-up, in Primorska, in the shape of the other languages' A2 ones: a table at a
feast, the way, a phone call.

| Id | Who | What the course's A2 gives it |
|---|---|---|
| `na-osmici` 🌿 | Vinar Marko (the cast's, ti) at the first osmica his reading tells of | *kozarec rebule*, *krožnik pršuta in sira* (the genitive of quantity), *boljše*, *ne pijem vina, ker vozim* (a way out for a driver or a learner who doesn't drink) |
| `z-avtobusom-v-piran` 🚌 | Prodajalka Sonja at Koper's bus station (vi) | *v Piran, do Pirana, s katerega perona*, *z avtobusom, s kartico*, *odpelje* |
| `soba-v-izoli` 🛏️ | Gospa Nadja, on the phone (vi) | *s pogledom na morje*, *cenejša*, *od petka do nedelje*, the dual (*prideva, parkirava, dve noči*) |

Every text in en, de and it, the goals in Slovene too, the village's packs, each file flagged in its `review`. The hints keep to what the
A1 and A2 units teach; a few new words each (*peron, prestopiti, vožnja, vključen*) are the talk's stretch. Marko plays his own, so his
friendship grows, once met. No new pack and no new pack word, so no prompt can clash. A learner at A1 is offered them, as with the other
languages (`talkLevelOffered`).

### Native check

Machine-written, all of it, in all four languages. Spoken forms are never wrong: a free talk grades nothing, and the tutor's brief already
says to recast. Settled by hand, one request each on fran.si:

- *prestopiti* "z enega prevoznega sredstva na drugo: v Ljubljani moraš prestopiti" (SSKJ², sense 2): https://fran.si/iskanje?View=1&Query=prestopiti
- *povratna vozovnica* (SSKJ²): https://fran.si/iskanje?View=1&Query=povraten
- *vključiti v ceno* (SSKJ²; *vključen v kaj*, Pravopis): https://fran.si/iskanje?View=1&Query=vklju%C4%8Den
- Every Slovene form was checked against the app's Sloleks 3.0 index. Not in it, so not checked that way: *Brd* (genitive of Brda, as `vino-za-praznovanje` has it), *merlot* (settled in talks-sl.md), *povratno*, *vključen* (both above), *sobodajalka*.

For a native eye:

1. **karta or vozovnica.** The hints say *povratno karto* and *Koliko stane karta?* because the pack `pot-in-promet` teaches *karta*. The Sinonimni slovar marks it *pog.* beside *vozovnica*: https://fran.si/iskanje?View=1&Query=vozovnica. Would a ticket seller hear anything odd?
2. **peron at a bus station.** Pravopis's example and the Sinonimni slovar's sense are a railway's: https://fran.si/iskanje?View=1&Query=peron. Does a Primorec say *peron* for a bus bay?
3. **sobodajalka** as the `who` label: fran.si counts a Pravopis hit but its page showed no entry here, so it isn't settled. Is it the word, or *gospa, ki oddaja sobe*?
4. **Sobe Nadja** as how a family answers the phone.
5. **Register and naturalness:** *Kam pa danes?*, *Kar naprej, prostora je dovolj.*, *Boš kozarec rebule?*, *Mi prineseš še malo kruha?*, *Je zajtrk vključen v ceno?*, *Kje lahko parkirava?*
6. **An osmica in Brda.** The reading has Marko open one and lists the Karst, the Vipava valley and Istria, not Brda.
7. **The bases:** de *In der Osmica*, *Bussteig*; it *corsia* for a bus bay, *frasche*, *Collio* for Brda, *Isola* for Izola.

### Voice

A curated talk's clips are its opener and its hints (`voice.ts` `corpus`): 33 lines, at most 963 characters for one learner (963 for a woman, 959 for a man), of which *Hvala, se vidimo v petek.* (25) is already a line of `klic-v-ambulanto`: **938 new characters**. No existing line changed, so nothing loses its clip. Nothing was built or spent.

## The relay

- `companion/relay/Dockerfile`: `FROM oven/bun:1.3.13-alpine@sha256:4de47538…`, the Bun the CI runs, by the digest of the tag's multi-arch index. A rebuild gets the same image; raising Bun means both places, and the digest with the tag.
- A hard stop ends a backup's spool without its cleanup. `createRelay` now removes the `.tmp-*` files of `backup/.incoming` that no write has touched for an hour, and leaves anything else there. An hour is no running upload (Bun cuts a stalled request after two minutes); a younger file may be another relay's during a restart.

Seen in the built image too (1.3.13, as the `bun` user): a stale file gone, a young one kept, `/health` up, one line in the log.

## "Senza microfono" below the fold

The item's last row, under what the teacher asks and a word tip. It isn't only a phone's: Teo's tablet on its side lost it too. The exercise
player now shows the "▾" of a dialog's turn (`MoreBelow`) while an item goes on below, any exercise's, not just this one. It stands in the
corner, so the centre of a full-width choice stays free, and it hides under the peek and while the keyboard is up. A tap brings the rest up;
it goes at the end. Moving the row up was the other way: it would serve one screen of one exercise.

| Screen | Before | After |
|---|---|---|
| Phone, 1080 × 2400 | below the fold, nothing to say so | "▾", 1 tap |
| Small phone, 720 × 1280 at 320 dpi | the same | "▾", 1 or 2 taps |
| Phone on its side | the same | "▾", 2 taps |
| Teo's tablet upright, 1600 × 2560 | in view | in view, no cue |
| Teo's tablet on its side, 2560 × 1600 | below the fold, nothing to say so | "▾", 1 tap |

Tap counts to "Senza microfono": a swipe the learner had to guess, then the tap; now the cue's 1 or 2 taps, then the tap.

**An item opened scrolled down.** Found on the way, and it was there before: the player's scroll outlived its item, so a tall item after a
tall one opened at the last one's offset. On the small phone the third item of `periodo-ipotetico-4` opened with its instruction and question
gone, the 🎤 first. A cue that invites scrolling would have made that common, so each item now has its own scroll
(`remember(index) { ScrollState(0) }`).

**What not to write there.** The first try was `key(index) { rememberScrollState() }`. It hung the app: on that same change of item the
composition never ended (`GapComposer.end` → `recordDelete` for good, the op list growing, a GC of 67 MB every half second, "Lani isn't
responding"). It came twice in two runs, and not once in the runs since `remember(index)`. It didn't hang the item before it, a speaking item
after a speaking item; the third is a writing item. Why a key's group there loops isn't established: the trace shows where, not why. No
other composable in the app keeps state through `key` like that.

A fresh `ScrollState` reads as scrolling on until it's measured, so `MoreBelow` waits for a viewport: without that, "▾" flashed on every item.

## Pages under the status bar

A `GradientHeader` that scrolls away under a transparent status bar leaves the page's text over the clock and the icons, at rest too (a unit's gold "Avanti" under the clock). The lead asked for it on a unit's page, then on every page of that shape.

`Modifier.statusBarScrim` draws the bar over the list or the column itself, so a screen is its scroll state and the modifier: `LazyColumn(Modifier.fillMaxSize().statusBarScrim(list), state = list, …)`, or `Column(Modifier.fillMaxSize().statusBarScrim(scroll).verticalScroll(scroll))`. It comes with the first pixel of scroll, because a header's own text would pass under the clock too: a unit's goal runs to several lines, and the first fix, which waited for the header to go, left that text on the clock at rest in between. `StatusBarScrim` stays as it was on the screens that already have one, and on those the header's text still passes under the clock: a separate look.

On a unit's page, School, Packs, Cases, Numbers, Readings, Settings, Settings' sub-pages, read-aloud, placement, and on `TalkScreen`'s list and debrief. **`TalkScreen` wasn't asked for:** its two lists have the same shape and the report that listed the screens missed them, so they stand in a commit of their own, to drop if unwanted.

QA reads the status bar's own pixels (its frame from `dumpsys window`, the middle third, where no icon stands): the widest difference in colour, 0 to 255, at four stops as the page scrolls.

| Page | Screen | Before | After |
|---|---|---|---|
| A unit's page | phone | 210, 255, 255, 255 | 9, 10, 10, 10 |
| | small phone | 247, 253, 228, 228 | 10, 10, 9, 9 |
| | phone on its side | 247, 0, 253, 0 | 10, 0, 10, 0 |
| | Teo's tablet upright | 19, 19, 19, 19 (fits) | 19, 19, 19, 19 (fits) |
| | Teo's tablet on its side | 27, 253, 253, 253 | 1, 10, 10, 10 |
| School | phone | 247, 236, 222, 186 | 10, 9, 9, 7 |
| | small phone | 248, 205, 27, 180 | 10, 8, 1, 7 |
| Settings | phone | 247, 247, 247, 247 | 10, 10, 10, 10 |
| | small phone | 248, 228, 28, 28 | 10, 9, 1, 1 |
| Settings' 🔊 page | phone | 247, 228, 236, 253 | 10, 9, 10, 10 |
| | small phone | 247, 244, 228, 236 | 10, 10, 9, 10 |
| Packs | phone | 247, 25, 180, 180 | 10, 1, 7, 6 |
| | small phone | 248, 219, 180, 157 | 10, 9, 6, 6 |
| Numbers | small phone | not reached | 10, 10, 10, 10 |
| Cases | small phone | 248, 248, 248, 248 | 10, 10, 10, 10 |
| Talks' list | phone | 247, 219, 219, 219 | 10, 9, 10, 8 |
| | small phone | 248, 236, 219, 219 | 10, 8, 8, 9 |
| Placement | small phone | 248, 205, 205, 205 | 10, 8, 8, 8 |

The pages' before is a learner at A1, their after one at B2: what fills a page differs a little, the flaw doesn't.

**Read on no device:** Readings and read-aloud, the same two lines, compiled. The synthetic learners' reading corner is empty, at A1 and at B2 and with the cast met, so there is nothing to scroll and no reading to read aloud; why isn't established. Numbers has no "before": the old run's step opened it by a link that doesn't reach it. The debrief needs a talk with a tutor, which QA has none of. The list above it is read. On a phone Cases, Numbers and placement fit, so the small phone is what reads them. The tablet upright has nothing to scroll on a unit's page.

## The buttons under an answer

On a small phone "Avanti · Weiter" had what "💬 Chiedi · Fragen" left it, less than its longest word, and broke inside its words: "Avan / ti · / Weit / er". `AnswerButtons` decides by the labels' measure, so it holds for any two languages and for large text:

1. The way on takes the room left, while that holds its longest word. Its label may wrap between words, as it did.
2. If not, Ask gives up its words for its 💬 (`EmojiLabel`: TalkBack still says its name) and stays in the row.
3. Only on a tiny row in large text do they stand one above the other.

The first try went straight to 3. It kept the words whole, but took 122 dp of a card held at 220 and cut the answer itself in half behind Ask, a new flaw on the same screen. The shots showed it; no test did. The row stays 56 dp tall now, against about 96 dp for the broken label.

All twelve pairs on a small phone (720 × 1280 at 320 dpi), each in its own language's village, since a learner's target is their village's (`--steps answer-buttons`):

| The labels' language | Base | The way on | Its size | Ask |
|---|---|---|---|---|
| Slovene | English | Naprej · Next | 176 × 40 px | 💬 |
| | German | Naprej · Weiter | 201 × 40 px | 💬 |
| | Italian | Naprej · Avanti | 199 × 40 px | 💬 |
| German | Slovene | Weiter · Naprej | 201 × 40 px | 💬 |
| | English | Weiter · Next | 103 × 80 px | its words |
| | Italian | Weiter · Avanti | 197 × 40 px | 💬 |
| English | Slovene | Next · Naprej | 94 × 80 px | its words |
| | German | Next · Weiter | 103 × 80 px | its words |
| | Italian | Next · Avanti | 111 × 80 px | its words |
| Italian | Slovene | Avanti · Naprej | 199 × 40 px | 💬 |
| | English | Avanti · Next | 111 × 80 px | its words |
| | German | Avanti · Weiter | 197 × 40 px | 💬 |

At 80 px the label wraps at its space, two whole words.

`AnswerButtonsTest` has the twelve in real text (native graphics), on rows of 200 to 380 dp, at 1, 1.3 and 1.6 times the text; with the longest word left out of the decision it fails, "[N, a, p, r, e, j, …]".

## Tests

On the final tree: the bridge's and the relay's `tsc` clean; the smoke 4,571 checks, none failed; `prompt-clashes` exit 0 (343 in the baseline, none new); the relay's tests 28, 2 of them new; Python `companion/tests` 188 (11 skipped) and `tests` 253; the Android suite 4,518 tests, 2 skipped, 0 failures, in the usual order. New: `MoreBelowTest` (4), `AnswerButtonsTest` (4), `TalkTest` at 21 talks with the A2 ones held to the B1 and B2 ones' rules, the smoke's counts and Marko's A2 talk.

On the final build: `long-pages` on five screens, `scrim-pages` on two, `answer-buttons` in the four villages and `friuli-school-unit` on `periodo-ipotetico`, a whole unit's changes of item, all pass. On the app before these fixes `long-pages` and `scrim-pages` fail on each thing they check.

QA was wrong four times on the way, and each time it hid something or claimed something: it took a tablet that fits for one that doesn't; it skipped the status bar's first stop, which is how the header's text under the clock nearly went through; a first scroll check tested a next item short enough to clamp any offset to nothing, so `key(index)` looked needless; and its first sweep of the pairs left the answer card, since a change of pair goes Home.

**On this machine, `python3` through mise's shim fails 1 smoke check and 22 of `tests/test_db_port.py`** since mise 2026.11.0 came at 16:42 on 2026-11-05: the shim writes under `$HOME`, those tests run with a `$HOME` of their own and compare the files left behind, and the smoke's check reads its output. Nothing in the branch: with `/usr/bin` first in `PATH`, the same Python 3.14.8, as the CI has it, all of them pass, and those are the numbers above.

**One red run, for the record.** `LaniSheetTest`'s canary (Material's own sheet still bounces) failed once, getting no frames (2988 to 2988 px). That run's order wasn't the usual one: Gradle runs what failed last first, and `MoreBelowTest` had just been failed on purpose to see that it catches the flash. It passes on its own, with `MoreBelowTest` right before it in one JVM, and in both full runs since, in the usual order. So that test doesn't break it by itself, and what did isn't established. nav-perf.md has the same canary falling to frame waits an earlier test left behind.

## What's left

- **Readings and read-aloud** on a device (above), and **the talks' debrief**.
- **`SceneDialog`'s typed turn** has the same row as the answer's: `LetMeChoose` beside "Preveri · Check" with `weight(1f)`, and "Controlla" is a long word. Not looked at on a small phone, not changed. `RoadSheet`'s two equal halves leave room for their longest words by arithmetic.
- **The screens that already have a scrim** (Home, Progress, the grammar book, towns, villagers) still let their header's text pass under the clock: left, a separate look.
- **Not shown by any device:** the talks played with a tutor (QA starts none; the smoke validates and serves them, nothing has spoken them); their voice; the relay's sweep on a real hard stop (a stale file was placed by hand, in the test and in the image); TalkBack itself on the 💬 (the name is in the tree, no screen reader has said it).
