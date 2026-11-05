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

## A unit's page under the status bar

`UnitScreen` had no `StatusBarScrim`, so the page's text ran over the clock and the icons, at rest too (the gold "Avanti" under the clock).
It has one now, split as `HomeScreen` is. The scrim only came once the header had gone, and a unit's goal runs to several lines: at rest in
between, the header's white text still lay on the clock. So `overHeader = true` brings it from the first pixel of scroll, on this page alone;
the other screens keep theirs.

QA reads the status bar's own pixels (its frame from `dumpsys window`, the middle third, where no icon stands): the widest difference in
colour, 0 to 255, over four stops as the page scrolls.

| Screen | Before | After |
|---|---|---|
| Phone | 210, 255, 255, 255 | 9, 10, 10, 10 |
| Small phone | 247, 253, 228, 228 | 10, 7, 9, 9 |
| Phone on its side | 247, 0, 253, 0 | 10, 0, 10, 0 |
| Teo's tablet upright | 19, 19, 19, 19 | 19, 19, 19, 19 |
| Teo's tablet on its side | 27, 253, 253, 253 | 1, 10, 10, 10 |

The tablet upright has nothing to scroll: the whole page fits, under its header.

## Tests

On the final tree: the bridge's and the relay's `tsc` clean; the smoke 4,571 checks, none failed; `prompt-clashes` exit 0 (343 in the baseline, none new); the relay's tests 28, 2 of them new; Python `companion/tests` 188 (11 skipped) and `tests` 253; the Android suite 4,514 tests, 2 skipped, 0 failures. New: `MoreBelowTest` (4), `TalkTest` at 21 talks with the A2 ones held to the B1 and B2 ones' rules, the smoke's counts and Marko's A2 talk.

`companion/bin/qa --steps long-pages` (companion/README.md has the line) passes on the final build, on all five screens. On the app before these fixes it fails on each thing it checks: "Senza microfono" on four screens, the item opening scrolled down, the status bar on four. Its first versions were wrong twice, and each error hid something: it took a tablet that fits for one that doesn't, and it skipped the status bar's first stop, which is how the header's text under the clock nearly went through. It reads every stop now. `friuli-school-unit` on `periodo-ipotetico` passes too, a whole unit's changes of item, on the build before `overHeader`, which touches only the unit's scrim.

**One red run, for the record.** `LaniSheetTest`'s canary (Material's own sheet still bounces) failed once, getting no frames (2988 to 2988 px). That run's order wasn't the usual one: Gradle runs what failed last first, and I had just failed `MoreBelowTest` on purpose to see that its test catches the flash. It passes on its own, with `MoreBelowTest` right before it in one JVM, and in the full run after it in the usual order. So my test doesn't break it by itself, and what did isn't established. nav-perf.md has the same canary falling to frame waits an earlier test left behind.

## What's left

- **The same shape elsewhere, not changed and not seen on a device:** a `GradientHeader` scrolling away with no scrim in the lists of
  `PacksScreen`, `CasesScreen`, `NumbersScreen`, `ReadingsScreen`, `SchoolScreen`, `SettingsScreen`, and the columns of Settings' sub-pages,
  `ReadAloudScreen`, `PlacementScreen`. A list takes the unit's two lines; a column needs its scroll state read another way. The screens that
  have a scrim (Home, Progress, the grammar book, towns, villagers) still let their header's text pass under the clock.
- **"Avanti · Weiter" breaks inside its word** on the small phone ("Avan / ti · / Weit / er"), beside "Chiedi · Fragen" in the answer's card. Seen in QA, not changed.
- **Not shown by any device:** the talks played with a tutor (QA starts none; the smoke validates and serves them, nothing has spoken them); their voice; the relay's sweep on a real hard stop (a stale file was placed by hand, in the test and in the image).
