# Navigation: a real back stack, a tidy Home, Friends one tap away (built 2026-10-27)

Jan, after the navigation audit (2026-10-26: "for me it seems a bit convoluted sometimes"): "for choices, make it the
best, prod ready". The audit (scratchpad `nav-audit.md`) found no back stack under the screens, two hubs mixing
learning and the village, the settings behind a 12 dp dot and twenty small dead ends. It recommended plan A, "Pospravi
Home · Tidy Home", over a real back stack, with its 20 quick wins. This is that.

## What

### A real back stack

- **Back returns to the screen below.** `AppViewModel.nav` (`Nav.kt` `NavStack`) holds the screens under the one on
  view. Opening a screen puts it on top; Back, the predictive back gesture and every "‹ …" on a screen take it off. The
  old `screen` plus flags (`fromVillage`, `fromPacks`, `fromUnit`, `fromCorner`), the saved returns (`runFrom`,
  `talkReturn`, `schoolScene`, `schoolProgress`, `treasureProgress`) and the twelve `leave…()` functions are gone.
- **No loops.** The same screen already in the stack is gone back to instead of opened again (`Screen.destination`):
  Napredek → Šola → a unit → Napredek is Napredek again, with Domov under it. Scenes lie side by side (a scene opened
  from another has the village under it), and so do a visited town's places. Different screens of a kind stay: a unit,
  the school, another unit, and Back walks them. Each settings page is its own, the sections' list under it.
- **Runs never stay under another screen.** What opens from a run (its intro's link, its end's "more words", a gift's
  "to the village") takes the run's place. So Back after a run goes where the run began, never into a run that would
  start over.
- **One way back, named by where it leads.** Every screen's back link says the screen below: "‹ Vas · Village",
  "‹ Napredek · Progress", "‹ Ob ognju · At the campfire", "‹ Luka", "‹ Domov · Home" (`ui/Back.kt` `nameOf`,
  `backLabel`, `BackButton`). One glyph, "‹", everywhere (the HUD, Talk, the chat's bookmarks); ✕ only closes a
  layer or leaves a run.
- **The predictive back gesture** (Android 14 and later: `android:enableOnBackInvokedCallback`, `PredictiveBackHandler`
  in `MainActivity.App`): while the gesture is held the screen shrinks towards the finger's side and a wooden pill names
  where letting go leads ("‹ 🏘️ Vas · Village"). The screens' own Back comes first (a card, a sheet, a step, a dialog,
  as Compose's later BackHandler does); at Home nothing takes it, and the system's back-to-home animation leaves the app.
- **State survives.** Each screen's remembered state (a list's scroll, a section open) is kept under its entry while
  another is on top (`rememberSaveableStateHolder`), and dropped when it leaves the stack. The stack itself is saved
  with the activity (`SavedStateHandle`, `NavRoutes`): after the process's death the app opens on the screens the learner
  was on, as routes ("scene", "ob-ognju"). A run is not saved (its answers live in the run: the app opens where it
  began); a visit isn't either (the visited town's world is entered again from Friends). The activity isn't recreated
  for the dark theme at dusk, a font or display size, or the phone's language (`configChanges`): a run goes on.
- **Nothing lost on the way out of a run.** A run with answers that leaves the screen any way (a notification's
  target, a language switched) keeps them as its end would (ExerciseRun's dispose). What would open elsewhere from a
  sheet over it waits while it has answers (`AppViewModel.runBusy`): the chat's "🗣️ Pogovor · Talk" and a forms
  card's "▶ Vadi zdaj" are hidden then, a grammar page's "🎯 Vadi" too, and the rest says "🏁 Najprej dokončaj to vajo
  · Finish this exercise first".
- **Notifications and links** lead to their target over Home, on the way the app has to it: Home, the place it is in
  (the village for a scene, the school for a unit, Friends for a town's question or progress), the target
  (`AppViewModel.open`). Back walks that way home. One tapped while the app wasn't running opens once its data is read
  (the new scene, the grammar page, the cards due are there then). New targets: the evening's "🔥 N kartic čaka" and the widget (with
  cards due) open the review, the tutor's new grammar page opens the book at it. `lani://` links open the same targets
  (`DeepLink.fromUri`: `lani://review`, `lani://village`, `lani://scene/<id>[/<happening>]`, `lani://school/<unit>`,
  `lani://grammar/<page>`, `lani://friends`, `lani://settings/<section>`), and `lani://tutor` (back from "Sign in with
  ChatGPT" in the browser) the tutor's settings, unless the sign-in began where the learner still is. A town's invitation
  (`lani://town?…`, `fluent://town?…`, its web link: `DeepLink.ofUri`, the invitations round) opens Friends over Home with
  its Accept dialog filled in; it is kept even while a run goes on, and waits under Friends then.

### Tidy Home (plan A)

- **⚙️ Nastavitve · Settings**, visible in Home's header, its dot the tutor's connection (green, gold, red). A screen
  of sections (`ui/settings/SettingsScreen.kt`), each a row with what it holds now and a page of its own: 🧑‍🏫 Učitelj ·
  Tutor and 💾 Podatki · Data first, then 🌐 Jeziki · Languages, 🔊 Zvok in glas · Sound and voice, 🔗 Povezava ·
  Connection ("📷 Skeniraj QR" outlined there, red only when the node unpaired the phone), and 🛠️ Razvijalec ·
  Developer once the version line was tapped seven times. On a child's device the tutor's and data's pages are a
  parent's (🛡️, the hold). The connection sheet is gone; the Data sheet stays for the hosted onboarding.
- **The learning tiles right under Today** (0 or 1 swipe), compact: three to a row on a phone, four from 600 dp, six
  from 840 dp (Teo's tablet). New: "📖 Slovnica · Grammar" (the grammar book's index as a screen,
  `ui/grammar/GrammarBookScreen.kt`: the pages met by level, ★ how well each sits, 🆕 this week's, a page over it),
  "🔤 Moje besede · My words" (also always linked from "Nove besede" now), "🔢 Števila · Numbers" once open. Then the
  villagers' strip, then "📊 Napredek · Progress".
- **Today's village rows capped:** two calls at most (who to meet, the burner at his pile), the rest one line,
  "➕ Še 2 v vasi · 2 more in the village ›".
- **A village threat is Today's one thing to do** (wolves, a bear, a storm: lost at their deadline): "🐺 Branimo vas! ·
  Defend the village!", "⏳ 35 h", its button red ("⚔️ Brani · Defend"); the reviews are then the first idea, and the
  strip doesn't show the threat a second time.
- **Overlays say where they leave you:** an arrival's, a keeper's or a project step's end says "✓ Nazaj v vas · Back
  to the village" over the village and "✓ Naprej · Continue" over Home.

### Friends one tap away

"🤝" in Home's header and over the village (under the HUD's bar, on the left), with a red count when something waits
there (`ui/towns/FriendsNews`): a guest's question to answer, a guest who came since Friends was last seen, a friend's
trouble to send help against, a move a friend's town offers, a feast open to share. The linked towns are loaded with
the rest at each refresh, so the badge is there before Friends is opened.

### The 20 quick wins (audit section 6)

| # | Quick win | Done |
|---|---|---|
| 1 | Back on a run's result does what "Naprej · Continue" does | `Finish`'s BackHandler; Back can't leave the app from a screen with one below; every result's Continue goes where the run began (`leaveRun`) |
| 2 | The chat's "Pogovor" and `playFormsCard` guarded during a run | `runBusy`: hidden, or "Finish this exercise first"; a grammar page's practice too; the intro's and finish's "🎯 Vadi, kar ti ne gre" work now (the run isn't busy there) |
| 3 | The HUD's "Danes in kmalu" party row | `VillageHud` passes `party` (and the new `friends`) through its sheet-closing wrapper |
| 4 | The scene's "‹ Vas" closes the dialog first | the pill closes the scene's dialog and its I spy, then leaves; system Back closes one layer at a time; a scene leaving the stack takes its open dialog with it |
| 5 | PackLearn's end goes back where the run began | `leaveRun` |
| 6 | A challenge's result goes back where it began, labelled so | "🏡 V vas · To the village" from the village, else "‹ <the screen below>" |
| 7 | A unit opened from Today, Progress or a notification goes back there | the stack; "🏫 Šola ›" on the unit when the school isn't under it |
| 8 | Settings visible, ordered, the scan button outlined | the Settings screen above |
| 9 | Today's calls capped; the tiles above the strip | above |
| 10 | "📖 Slovnica" and "🔤 Moje besede" tiles; My words always linked from the packs | above |
| 11 | "▶ Ponovi" on Progress's reviews card | while cards are due today, with their number |
| 12 | Notifications lead to their target | grammar → the book's page, the evening reminder and the widget → the review |
| 13 | One back glyph, "‹" plus the name | above |
| 14 | 🏫 the school, 🔤 My words, 📚 packs only | the school's header, its card button, the scene's pill; My words' header, link and caption; 📗 for "Branje · Reading" so 📖 is the grammar book's alone |
| 15 | Bilingual labels | "📊 Napredek · Progress" in the HUD, the scene's "🏫 Šola · School" pill (on a tablet; the target word alone on a phone, TalkBack both), the village chip's event name |
| 16 | The HUD's back closes the open card first | its "‹" dispatches the system's Back |
| 17 | A visit: Back and "⌂ Domov" go to the same place | both end the visit, back to where it began (Friends) |
| 18 | The building card | the village's part of this round (ux-village) |
| 19 | "Izzivi" means only the tutor's modules | the village's timed runs: "Na čas · Against the clock" (l10n `intro.timed`) |
| 20 | The treasure map's helper buttons come back to the map | the stack: Branje, Nove besede, Pogovor open over the map |

Also from the audit's list of inconsistencies: the hosted setup's Back does what its "‹ Nazaj" does, the chat's
bookmarks close on Back before the chat, the homework intro's Back leaves the run (not the app), the founding's Back
leaves the app as at Home (it never does nothing), a grammar page opened from another's "🔗 Glej tudi" has
"‹ <the page before>" and Back to it (`GrammarController.before`), and a review's end says "Odlično! 🎉" only for a good
review ("Dobro delo 💪" for 0 of 1).

## How

- `Nav.kt`: `NavStack` (`go`, `back`, `backTo`, `replace`, `reset`, `restore`, `saved`), `Screen.destination`,
  `Screen.sameAs`, `NavRoutes` (the saved routes). `Screen.kt`: the from-flags dropped; `Settings(section)` and
  `GrammarBook` new.
- `AppViewModel`: `nav`, `screen` (its top), `back()`, `open(link)`, `startRun`/`leaveRun`, `runBusy`, `shown(screen)`
  (what a screen reads anew as it comes on view, also by Back: Home and the packs refresh, the village syncs …),
  `left(screen)` (what a screen takes with it: a visit ends, the treasure map is seen, a town's progress closes, My
  words' cards, a scene's open dialog, Friends' guests seen), `openSettings`, `openGrammarBook`, `friendsNews`. The old
  `fromVillage` overloads are gone: the village's callers (`VillageScreen`, the scene's home sheet) call the plain ones.
- `MainActivity.App`: the entry's `SaveableStateProvider`, the `PredictiveBackHandler`, `BackScrim`, `BackPeek`,
  `predictiveBack` (`ui/Back.kt`); the manifest's `enableOnBackInvokedCallback` and the `lani://` filter.
- Every screen's back link: `vm.backLabel`/`BackButton`; their `BackHandler`s that only navigated are gone (the root's
  predictive handler does it), those that close a layer stay, enabled only while the layer is there.
- Home: `HomeScreen`, `HomeHero` (🤝, ⚙️ with the dot), `HomeLogic` (`defend`, `MAX_CALLS`, `myWords`, `grammarBook`,
  `numbers`), `TodayCard` (the threat's look, the capped calls), `LearningGrid` (`learningColumns`, `ProgressSection`).
- `data/DeepLink.kt`: `Home`, `Review`, `Friends`, `Grammar`, `Settings`; `fromUri`. `BackgroundCheck`, `VillageWidget`.
- `ui/data/DataSheet.kt`: `DataContent` (the sheet's body, also the settings' page), `dataSummary`.
- Labels: `nav.*`, `settings.sound`, `settings.soundAbout` new, `intro.timed` changed, in sl, en, de and it
  (machine-written by Claude: for a native speaker's check, de and it especially).

## How to use it

Nothing to turn on. On the phone: Back and the back gesture always lead to the screen named on its back link; ⚙️ on
Home for the settings; 🤝 for Friends. Links for testing:

```bash
adb shell am start -a android.intent.action.VIEW -d lani://scene/ob-ognju      # Home › Vas › Ob ognju
adb shell am start -a android.intent.action.VIEW -d lani://school/biti         # Home › Šola › the unit
adb shell am start -a android.intent.action.VIEW -d lani://settings/tutor      # Home › Nastavitve › Učitelj
```

The process's death: with the app on a screen, `adb shell am kill si.lanisce.lani` after Home on the phone, then
the app from the recent apps: the same screens (a list's scroll where its items are there as it opens).

## The journeys, before and after

Taps from Home (swipes in brackets), as the audit counted them; "after" walked on the emulator (section "QA"). The
swipe left for the tiles is Ana's: her Today is long (two ideas, the village's day, two of twelve newcomers and "➕ Še
10"); with a shorter Today the tiles show without one.

| # | Journey | Before | After |
|---|---|---|---|
| 1 | Today's session | 1 | 1 |
| 2 | Reviews | 1 (only via Today) | 1 (Today), 2 (Napredek's "▶ Ponovi"); the evening reminder and the widget open it |
| 3 | Learn new words | 3 (3) | 3 (1) |
| 4 | A village dialog | 2 | 2 |
| 5 | Ask the tutor about an exercise | 2 | 2 |
| 6 | The school's next unit | 2–3 (3) | 2 (Today's idea), 3 (1) (🏫 → ★) |
| 7 | The grammar book | 4 | 2 |
| 8 | A word's forms | 4 (3) | 3 (1) |
| 9 | The progress | 1 | 1 |
| 10a | Tutor settings | 1 (6) | 2 |
| 10b | Data settings | 2 (5) | 2 |
| 11 | A village event (defend) | 3 | 2 |
| 12 | Build something | 4–5 | 4–5 (the village's part) |
| | **Total (1–12)** | **30–31 (17)** | **27–28 (2)** |
| – | Friends | 4 | 1 |
| – | My words | 2 (3) | 1 (1) |
| – | Numbers (once open) | 2 or 4 | 1 (its screen), 2 to play |
| | Back on a run's result | leaves the app | where the run began |
| | Settings found without help | no (a 12 dp dot) | yes (⚙️) |

## Tests

- `NavStackTest` (14): Back to the screen below, the root left to the system, Home's strip vs the village, no loops,
  different screens of a kind kept (two units, two villagers), runs replaced and Back after them, the same screen
  keeping its state, the settings' pages, what leaves told top first, `backTo`, `reset`, saved and restored under the
  same ids up to a run (new ids after the run's), a visit not saved, unreadable text is Home, every route round-trips.
- `DeepLinkTest`: the new links survive the extra, the grammar page's notification, `lani://` links and what isn't one,
  a town's invitation apart from them.
- `HomeLogicTest`: the threat as Today's one thing (the reviews then the first idea), not for the merchant; My words',
  the book's and the numbers' tiles.
- `FriendsNewsTest`: nothing linked; a guest until seen; a question, help, a move and a feast each counted once.
- `BookIndexTest`: the index by level, the week's new first, the stars; the empty book.
- `VillageHudFriendsTest` (Robolectric, Compose): the HUD's 🤝 and its badge, the bar's "‹" named by where it leads,
  the "Danes in kmalu" party row leading to the party.

## QA

On the emulator (Android 16, 1080 × 2400), on a synthetic learner (the audit's "Ana": A1, the Primorska village with
the whole cast, 6 cards due, no grammar page met yet), the dev bridge on 8791 inside the emulator lock, never real data.
`companion/bin/qa --steps navigation` (setup, then the step above; it passes on the build rebased onto the village's
and the invitations' rounds, its shots in the session's scratchpad `ux-nav/qa-final/`), then each journey by hand, its
taps counted:

- Home: ⚙️ and 🤝 in the header, the dot green; Today with "➕ Še 10 v vasi" under two of the twelve calls; the tiles a
  swipe below, three to a row.
- Settings: ⚙️ → its five sections → Učitelj (2 taps), Podatki (2), Povezava (2, the scan outlined); Back: the
  sections, Home.
- Slovnica, Moje besede, Friends: 1 tap each, "‹ Domov · Home" over each, Back Home. A word's forms: 3 taps.
- The village: the HUD's "‹" and 🤝 under it; 🤝 → Friends under "‹ Vas · Village", Back to the map, Back Home.
- Wolves (QA's hook "event:wolves"): Today's one thing "Branimo vas!" in red, "⚔️ Brani · Defend" → the intro
  ("Na čas · Against the clock": no "Izziv" there now); 2 taps to "⚔️ Začni".
- A review, one card answered, Back: its result ("Dobro delo 💪" for 0 of 1, no longer "Odlično!"); Back again: Home,
  the app still in front.
- `lani://scene/ob-ognju`: the fire's scene under "‹ Vas · Village", Back the village, Back Home.
- The process killed in the background on Napredek, the app opened again: Napredek, "‹ Domov · Home", Back Home.
- The predictive gesture held over Friends: the screen shrunk, "‹ 🏘️ Vas · Village" by the finger; let go: the village.
- The school: 🏫 → ★ the unit next ("‹ Šola · School") → Back the school ("‹ Domov · Home") → Back Home.

## What's left

- Quick win 18 (the building card's hard-coded lines) went with the village's round, which rewrote those cards.
- The plot chooser's "←" (`PlotChooser.kt`) and the game intro's "← skill" keep their arrows: the village's files.
- A run's answers aren't saved across the process's death (only across leaving the screen): the app opens where the
  run began.
- The machine-written labels (de, it; the new sl ones too) wait for a native speaker's check.
