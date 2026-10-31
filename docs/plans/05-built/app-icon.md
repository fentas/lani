# Built: Lani's app icon and logo, an icon that grows with the village (app-icon, 2026-10-31)

Jan, 2026-10-31: "also lets design a nice app logo/icon - maybe also a changing one? - duolingo does it".

This is step 1 of 2: **concepts only**. Nothing is built into the app yet. Jan picks a concept; step 2 builds it (the
aliases, the setting, the switch). The generator draws every concept already, so the pick builds straight from it.

Today's icon (`companion/android/tools/launcher_icon.py`) is a speech bubble with Č over Triglav at dusk. It is
Slovene, and it is wrong on Teo's tablet, where Teo learns Italian in Friuli.

**Step 1b** (Jan saw the five concepts: "maybe for every age a new icon + state what is happening or waiting - incl.
events"): see "Step 1b: the icon through the ages" below. Where they differ, step 1b replaces step 1's changing icon:
the aliases, the setting and the events.

## How the concepts are drawn

`companion/android/tools/app_icon.py` draws all five concepts pixel by pixel, in the village's own palette (`Pal`,
`FireStyle`), on a grid of 54 × 54 pixels of 2 dp. That is the adaptive icon's 108 dp layer. A launcher shows the
middle 36 × 36 pixels (72 dp), and every mask keeps the circle of 33 pixels (66 dp) in the middle. All main parts of
every concept are inside that circle. A pixel is 1.3 dp at 48 dp: small enough to read as a picture at 1 cm, big
enough to stay pixel art at 192 dp.

For every variant, a concept draws three layers: the background, the foreground and the monochrome layer (the themed
icon). The vector drawables are one path per colour, a rectangle per run of pixels. Each rectangle is 0.04 dp wider,
so neighbouring rows meet without a hairline.

```
python3 companion/android/tools/app_icon.py list
python3 companion/android/tools/app_icon.py xml ltower companion/android/app/src/main/res base friuli.winter …
python3 companion/android/tools/app_icon.py wordmark ltower OUT_DIR
uv run --no-project --with pillow python3 companion/android/tools/app_icon.py sheets OUT_DIR
```

A variant is written `culture[.season][.time][.fest][.waiting]`, for example `friuli.winter.lights`. The XML needs
only Python. The contact sheets need Pillow (`app_icon_sheets.py`).

**The contact sheets** (made again with the last command; /tmp is RAM):
`/tmp/claude-1000/-mnt-scratch-github-fentas-ai-slo/721724ea-4389-48ad-b641-b6d1d5157803/scratchpad/ux-app-icon/concepts/`
has `overview.png` (all five side by side) and one sheet per concept (`ltower.png`, `neighbour.png`, `bubble.png`,
`fire.png`, `house.png`). Each sheet shows:

- the layers, with the visible square and the safe circle;
- 192 dp in the four masks (circle, squircle, rounded square, teardrop);
- 72 and 48 dp on a light and on a dark wallpaper;
- the themed icon in light and dark at 192, 72 and 48 dp;
- every change (culture, season, festival, time of day, today's practice);
- the wordmark at three sizes on light and dark;
- a home screen row at 48 dp beside generic icons, in circles, in squircles, and themed.

The sheets draw at 2 px a dp. A launcher rasterises the vector at its size, and the sheets do the same (a box filter
from a big nearest-neighbour upscale).

## The base form and the cultures

Every concept has a **base form** that is not tied to a language or a culture: any village, with cream walls, brick
roofs, wooden shutters and a plain tower with a pyramid roof. A new install shows it before the learner picks a
language. It is also the mark for the README, the setup welcome screen and prints.

The culture forms use the village's own looks (`FireStyle`, `MapLook`):

| Culture | Walls | Roof | Tower | Shutters |
|---|---|---|---|---|
| Primorska | white plaster | red tiles | spire | green |
| Friuli | ochre and peach plaster | coppi | brick campanile, two arches in the belfry | green |
| Kärnten | white plaster, dark timber upper storey | shingles | onion dome | dark green |
| Lake District | grey slate stone | slate | battlements | none (white window frames) |

Teo's tablet shows Friuli's form.

## The five concepts

### 1. Zvonik · The L of the village (`ltower`)

The bell tower and the long house beside it make an **L**, Lani's first letter. The bell rings a speech bubble
with "…". The concept is a letter mark, a village and "people talk here" at once.

- **At 48 dp:** the clearest silhouette of the five: a light L outlined in dark ink on a blue sky, and a white bubble.
  It reads in all four masks, and it looks complete beside flat icons on the home screen.
- **Themed icon:** the best of the five: tower, house and bubble as three bold shapes, with the belfry, the door and
  the windows cut out.
- **Culture:** strong and cheap. The tower top is the app's own `Tower` per culture (spire, campanile, onion,
  battlements), plus the walls and the roof. Friuli's brick campanile and ochre house are clear even at 48 dp.
- **Season and festivals:** snow on the roofs and the spire, autumn and spring grass, lights along the eave, confetti
  in the sky, a midsummer fire on the hill, pumpkins and grapes at the harvest.
- **Wordmark:** the L of "Lani" wears the tower's roof, with the belfry cut into the stem. The icon and the logo are
  the same idea.

### 2. Sosed · The neighbour (`neighbour`)

A villager waves from their window. There are flowers on the sill, and a bubble with "…" above. Someone in the
village says hello.

- **At 48 dp:** the face reads (big chibi head, dot eyes, blush, as the app draws people), and so do the waving hand
  and the shutters. It is the warmest of the five, and the most appealing for a child.
- **Themed icon:** good: the frame, the face with its eyes cut out, the hand and the bubble.
- **Culture:** strong: the wall (plaster, ochre, timber, slate stone), the shutters, the roof's eave across the top,
  and the neighbour's hair and shirt.
- **Weakness:** pale walls fade on a light wallpaper. A roof eave at the top and a stone plinth at the bottom now
  frame them, but the icon is still lighter than the others. A face makes it a mascot, which is nearer to Duolingo's
  way. It is not their owl, but it is their kind of icon.
- **Wordmark:** the dot of the i is the neighbour's face.

### 3. Mehurček · The bubble window (`bubble`)

A big cream speech bubble on the app's blue, and through it you look into the village: the hills, the tower, two
houses and the sun.

- **At 48 dp:** "speech bubble" reads at once. The village inside is small, but the tower and the roofs show.
- **Themed icon:** the ring and the tail, with the village's silhouette inside.
- **Culture:** the skyline: the tower, the roofs, the mountains. It is visible but small.
- **Changes:** fairy lights round the ring, confetti, a midsummer fire on the hill, a dusk or night sky in the window.
  "Waiting" puts "…" into the village's sky.
- **Weakness:** many messengers use a bubble (Messages, WhatsApp, Signal), so at a glance it can look like a chat app.
  It continues today's icon (a bubble), which is good for Jan's habit.
- **Wordmark:** "Lani" written in the bubble.

### 4. Ogenj · The fire (`fire`)

The village fire at nightfall in a forest clearing. Its smoke rises into a speech bubble: where the village meets and
talks.

- **At 48 dp:** bright fire on dark blue is the strongest contrast of the five. It reads at any size.
- **Themed icon:** the bubble, the flame (its heart cut out) and the crossed logs.
- **Culture:** almost invisible. Only a tower far away on the horizon changes. All four cultures look the same at
  48 dp.
- **Changes:** the most natural story for today's practice ("the fire burns low": embers, a curl of smoke and a log
  laid ready), and for the midsummer fire (a big bonfire). Snowy and decorated spruces in December.
- **Weakness:** a flame with a bubble at 1 cm is near a torch or a dating app's flame, and the culture does not show.
- **Wordmark:** the dot of the i is a flame.

### 5. Hiša · The talking house (`house`)

One house of the learner's village, drawn 2:1 isometric as the map draws it, on a patch of grass. Its chimney puffs a
speech bubble.

- **At 48 dp:** the house reads as a house, but the iso roof and walls are the busiest shapes of the five.
- **Themed icon:** the roof solid, the walls drawn by their outline with the windows and the door, and the bubble. It
  reads as a house, but it is the weakest themed icon.
- **Culture:** strong: walls, roof, shutters, the timber storey and the balcony's geraniums.
- **Changes:** snow on the roof, lights along the eave, a midsummer fire beside it, pumpkins by the door. "Waiting"
  is a wisp of smoke instead of the bubble.
- **Wordmark:** the dot of the i is a little house.

### Side by side

| | Zvonik (L) | Sosed | Mehurček | Ogenj | Hiša |
|---|---|---|---|---|---|
| Reads at 48 dp | very well | well | well | very well | fair |
| All four masks | yes | yes (shutters cut in a circle) | yes | yes | yes |
| Themed icon | best | good | good | good | fair |
| Culture shows | strongly (the tower) | strongly (the wall) | a little (the skyline) | hardly | strongly (the house) |
| Season and festivals | clear | clear | clear | clear | clear |
| For Teo (7) | friendly | the most appealing | friendly | exciting | friendly |
| Risk | none found | mascot-like, pale on light wallpapers | looks like a messenger | culture invisible, flame clichés | busy at 1 cm |
| Logo (wordmark) | the L is the tower | i-dot face | word in a bubble | i-dot flame | i-dot house |

## Recommendation: Zvonik, the L of the village

1. It reads best at 1 cm and in every mask, and it has the best themed icon. These are the two places where an icon
   lives or dies.
2. It is a real logo, not only a picture. The L is Lani's letter, and the same tower stands on the L of the wordmark.
   The README, the setup welcome screen and a print use one mark.
3. The change by culture uses what the app already has: each culture's `Tower` and house look. Teo's tablet gets
   Friuli's brick campanile and ochre house with no new art system.
4. The bubble says "language" without a letter of any language. The base form belongs to no culture.
5. It does not look like Duolingo or any messenger: no mascot, no green, no bubble-only shape.

If Jan wants the icon to be a character, **Sosed** is the second choice, for Teo's sake. **Mehurček** is the safe
choice that keeps today's bubble. **Ogenj** is a good look for the midsummer fire, not for every day.

## The changing icon (step 1; step 1b below replaces the aliases and the setting)

### What changes, and what I advise

| Change | How often it switches | Advice | In "Automatic" |
|---|---|---|---|
| **By the learner's culture** (Primorska, Friuli, Kärnten, Lake District) | once, after setup; again only if the learner changes language | **Yes.** This is the real fix: Teo's tablet shows Friuli. | on |
| **By the season** (spring, summer, autumn, winter, as `Env.season` counts them) | 4 times a year | **Yes.** It is cheap, and a child sees the snow come to the icon. | on |
| **By the festivals** (the December lights, Dec 20 to Jan 6) | 2 times a year | **Yes, only the lights.** All four cultures keep Christmas and New Year. | on |
| By the other festivals (carnival, midsummer fire, harvest: drawn on the sheets) | 2 times per festival, a few days each | **Not now.** Each one is two more switches for a few days, and the festival is already in the app and the widget. Add one later per culture if Jan wants it. | off |
| By the time of day (day, dusk, night) | 2 to 3 times a day | **No.** Too many switches (every one rebinds the launcher, and some launchers duplicate the icon). The widget and the village already follow the hour. | not built |
| By today's practice ("the fire burns low") | 2 times a day | **No, not in the launcher icon.** See below. | not built |

**Today's practice.** Duolingo makes its icon look worse until you practise. That works by loss: the icon looks sad
or sick until you open the app. Teo is 7, so even a gentle version ("the fire waits": embers, a log laid ready,
never out, never a face) still tells a child every day that something is not done. It also switches the icon twice a
day, which is the most frequent change and the riskiest one on launchers (below). Lani already says it kindly in
the widget, on Home and in notifications. My advice: do not build it. If Jan wants it for himself later, make it
opt-in for an adult's profile only, never on a child's profile, and keep it kind: the fire waits, it never goes out.

The sheets draw every change for every concept, so Jan can see what is left out.

### The aliases

Activity aliases, all with `android:targetActivity=".MainActivity"`, the launcher intent filter, their own adaptive
icon (with its own `<monochrome>`) and the same label:

- `.icon.Base`: enabled in the manifest, the only one that is;
- 4 cultures × 4 seasons: `.icon.PrimorskaSpring` … `.icon.LakelandWinter` (16), disabled in the manifest;
- 4 cultures' December lights: `.icon.PrimorskaLights` … (4), disabled in the manifest.

That is 21 aliases. A fixed choice uses a culture's summer alias. The generator writes every alias's drawables. The
backgrounds depend on the season and the light only, not on the culture, so step 2 shares them (about 30 KB each,
mostly the dithered sky). `MainActivity` loses its launcher intent filter and keeps every other filter (the links,
the town invitations, `lani://…`). `<application android:icon>` stays the base form: Settings → Apps and the
notifications' app name use it.

Aliases are never renamed or removed in a later version. The enabled state is stored by class name and survives
updates. If the enabled alias were gone, the app would have no launcher icon. A unit test keeps the list.

### The setting: "🎨 Ikona · App icon"

In Settings, section "🌐 Languages" (where the village's culture is chosen; Settings has no appearance section):

- **Samodejno · Automatic** (the default): your village's look, by the season, with the December lights. Before
  setup, the base form.
- **A fixed look:** Osnova · Base, Primorska, Friuli, Kärnten, Lake District (each in its summer look).
- A line under it: "Ikona se zamenja, ko je Lani zaprt, največ enkrat na dan. · The icon changes while Lani is
  closed, at most once a day."

It is on by default for everyone. Automatic is safe because it switches at most about 7 times a year (once after
setup, 4 seasons, the lights on and off).

Strings (machine-written, for a native check): sl "Ikona", "Samodejno", "Osnova"; en "App icon", "Automatic",
"Base"; de "App-Symbol", "Automatisch", "Basis"; it "Icona dell'app", "Automatica", "Base". The culture names stay
as they are. The labels on the contact sheets (Osnova, Pomlad, Poletje, Jesen, Zima, Luči, Pust, Kres, Trgatev, Dan,
Mrak, Noč, Narejeno, Čaka) are machine-written too.

Tap counts: step 1 changes no journey (nothing before or after). In step 2, a fixed look is Settings → 🌐 Languages →
App icon → a look; Automatic needs no tap at all.

### When to switch: the safe design

1. **What is wanted** is pure: from the setting, the learner's culture and today's date. It is a unit test of dates
   (the season's first day, Dec 20, Jan 7).
2. **Only while the app is in the background.** On `ProcessLifecycleOwner` ON_STOP, schedule a unique one-time
   WorkManager job with a 15-minute delay. ON_START cancels it. The job does nothing if the app is in the foreground,
   if the wanted alias is already enabled, or if the last switch was less than 20 hours ago (a choice the learner
   made in the setting is applied on the next background, without the 20 hours).
3. **Enable the new alias first, then turn the old one off.** On Android 13+, use
   `PackageManager.setComponentEnabledSettings` (both in one call, atomic, one broadcast). On older versions, make two
   calls in that order, both with `DONT_KILL_APP`. There must never be a moment with no launcher alias.
4. **Turn the old one off by setting it to DEFAULT, not DISABLED,** when it is disabled in the manifest (all the
   culture aliases). The system closes the task that was started from an alias set to DISABLED (below). DEFAULT goes
   back to the manifest's "disabled" without that. Only the base alias is set to DISABLED, once, after setup.
5. **Repair at every start and after an update** (an `ACTION_MY_PACKAGE_REPLACED` receiver). If not exactly the
   wanted alias is enabled, set it right. Signal and Telegram do the same.
6. A developer button, "Zamenjaj ikono zdaj · Switch the icon now" (debug builds), to test a launcher at once.

## The Android risks

Checked against AOSP's source and reports (a research pass, 2026-10-31; paths are on android.googlesource.com).

- **The home-screen icon.** Launcher3, and Pixel Launcher with it, keeps the icon and its place. On
  `PACKAGE_CHANGED`, `PackageUpdatedTask` points a home-screen item whose component went away at the package's first
  launcher activity (`updateWorkspaceItemIntent`, `getAppLaunchIntent`). It deletes the item only when no launcher
  activity is left. A Pixel 7 Pro test changed the icon in place
  (https://hackmd.io/@dh46tw/android-dynamic-app-icon).
  - Samsung One UI keeps the old icon and adds a second one
    (https://github.com/skb1129/react-native-change-icon/issues/122).
  - Huawei EMUI showed a second icon and then lost both
    (https://github.com/skb1129/react-native-change-icon/issues/55).
  - No reports were found for Nothing's launcher, MIUI, OnePlus or Lenovo. **Jan's Nothing Phone (4a) and Teo's
    tablet must be tested** with the developer button before Automatic is on for them. If Teo's tablet duplicates
    icons, set it to a fixed Friuli: that is one switch, once.
- **No launcher activity for a moment.** Without `DONT_KILL_APP` the process is killed at once, and if the old alias
  was turned off first, the new one is never turned on: the app has no launcher icon. With the flag, the changes go
  out as one coalesced broadcast. The batch API (Android 13) is atomic. Enable first, always.
- **The app is closed, not killed.** With `DONT_KILL_APP` the process lives, but the system removes the task whose
  root intent is the alias just set to DISABLED (`RecentTasks.cleanupDisabledPackageTasksLocked`): the activity is
  finished and the recents entry goes. This happens after the broadcast delay: 1 s on Android 10+, 10 s on 9 and
  older. This is the "the app dies 10 s later" of the reports
  (https://github.com/myinnos/AppIconNameChanger/issues/13). A component set back to DEFAULT is not cleaned up
  (https://github.com/gonodono/app-icon-change-demo), hence step 4. A task opened from a notification (started at
  `MainActivity`) is not affected. In the foreground, this would close a running exercise, so Lani switches only in
  the background.
- **Shortcuts.** Lani has no app shortcuts today. If it gets some: dynamic shortcuts follow the main activity and are
  disabled when it goes away, so their intents must point at `MainActivity`, and manifest shortcuts must be on every
  alias with the same ids.
- **Widgets** are receivers and are not affected. The village widget stays.
- **How often.** The system has no limit, but each switch costs a broadcast to the whole package, a launcher rebind,
  a shortcut scan and a write of the package restrictions. Duplicates are reported when apps switch several times in
  one session (https://github.com/outsung/expo-dynamic-app-icon/issues/25). Play has no rule against runtime icons.
  Its metadata policy forbids misleading icons, such as a fake unread dot
  (https://support.google.com/googleplay/android-developer/answer/9898842). A few switches a year is sane. A few a
  day is not.
- **The splash screen (Android 12+).** An alias has no theme of its own. It uses its target's theme. The system
  splash uses the theme's `windowSplashScreenAnimatedIcon` if it is set, and otherwise the launched alias's icon
  (`SplashscreenContentDrawer`). Lani's theme sets none, so the splash follows the changing icon, on the app's blue
  `windowBackground`. That is what we want. If androidx `core-splashscreen` is ever added, it always sets an icon,
  and the splash would be the same for every alias.
- **The notification small icon** comes from `setSmallIcon` and has nothing to do with the aliases. Today it is
  `ic_launcher_monochrome`, the 108 dp layer, so the glyph is about a third of the 24 dp: small. Step 2 adds
  `ic_notify`, a 24 dp white silhouette cut from the picked concept's monochrome layer.
- **Themed icons.** Each alias's adaptive icon needs its own `<monochrome>` (Android 13 to 16 QPR1; 16 QPR2 themes
  icons without it too). Launcher3 reloads a package's cached icons on every change, so the icon is not stale.
  Restore after a reinstall goes back to the manifest (the base form) until the first switch.

## Step 1b: the icon through the ages

Jan, 2026-10-31, after the five concepts: "maybe for every age a new icon + state what is happening or waiting - incl.
events". Still concepts only: nothing is built into the app.

Sheets (made again with `uv run --no-project --with pillow python3 companion/android/tools/app_icon.py ages OUT_DIR`):
`/tmp/claude-1000/-mnt-scratch-github-fentas-ai-slo/721724ea-4389-48ad-b641-b6d1d5157803/scratchpad/ux-app-icon/ages/`
has `overview.png`, `ages.png` (each age in each culture, winter, themed, 48 dp), `events.png` (the signs by
priority on four ages), `dots.png` (what's waiting: the dot, the count, the long-press card) and `home.png` (home
screen rows at 48 dp). A copy of the overviews, which survives a reboot:
`/mnt/scratch/github/fentas/.lani-lead-kit/icon-concepts/`.

### One icon per age: Zvonik grows with the village

The village's six ages (`game/Model.kt` `Age`, GAME.md "Ages") start at a fire in a clearing, so the ages join two
concepts: the village starts as Ogenj (the fire) and grows into Zvonik (the L). Every age keeps the same L: a tall
shape on the left, a long one along the bottom, the bubble at the top right. So the icon stays Lani's as it grows, and
each age is clear at 48 dp. The concept is `ages` in the generator (`draw_ages`).

| Age | What the map has (GAME.md) | The icon |
|---|---|---|
| 🔥 Ogenj · Campfire | a fire in a clearing, 2 plots | a tall spruce, the fire in its ring of stones, a log to sit on |
| ⛺ Tabor · Camp | the tent (it starts the age) | the spruce, the tent with its pennant, a small fire |
| 🛖 Zaselek · Hamlet | the palisade and the field; huts, the watchtower | the watchtower on its stilts, a hut, the palisade's stakes |
| 🏘️ Vas · Village | the church, the linden, houses | step 1's L: the church tower and the long house |
| 🏛️ Trg · Market town | the market, the school, the well | the house in two storeys, the fair's stall with its striped awning, the tower's clock |
| 🏰 Mesto · Town | the watchtower, the smithy, 5 houses | a taller tower, the town's banner, the town wall with its battlements and gate |

- **The cultures.** Ogenj and Tabor look the same in every culture: the camp has no culture look, and before setup
  a new install has no culture either. From Zaselek on, the culture shows:
  - Zaselek: huts of logs under shingles (Primorska, Kärnten), of stone under coppi (Friuli) or under slate (Lake
    District), as `FireStyle.hutStone` builds them.
  - From Vas on: each culture's tower (spire, campanile, onion, battlements), walls, roofs and shutters, as in step 1.
- **No separate base form.** The icon a new install shows is Ogenj's, so the manifest's enabled alias needs no
  culture. A new learner's village is at Ogenj, so there is no switch right after setup, which is the riskiest moment
  (step 1 needed one). The neutral Vas L (step 1's base) stays the logo for the README and prints.
- **Seasons**, reduced to what shows in this picture: green (March to November) and snow (December to February), on
  the hill, the roofs and the spruce. The spring and autumn tints changed little and would cost switches.
- **The themed icon** follows the age only, never the signs: each age's silhouette, with its openings cut out, and
  the bubble.

### What's happening: one sign for the day, in the icon

An icon can only follow what lasts a day: it switches at most once a day, and only in the background. So the icon
shows the events that are the day's news and can't go stale. Each gets a small, kind sign drawn into the age's
picture. They are listed by priority: when two compete, the higher shows and the lower is dropped.

1. **Nova doba · A new age** (the day the age came, and the day after): fireworks and confetti over the new age's
   picture. The new age's picture is itself the big change.
2. **Praznik · A festival** (the culture's festival days, `festivals.json`, and the village's own festival event): a
   garland of little flags from the tower to the house. On snow it is a string of lights, and that is the December
   lights too: from December 20 to January 6 the festival look stays up.
3. **Zgrajeno · Built** (a building or a project finished in the last 2 days): the likof's little spruce with ribbons
   on the roof, the roofing feast's sign. Primorska has the likof, Kärnten and Lake District have their topping-out
   tree.
4. **Prišlek · Someone new** (a villager moved in during the last 2 days, `ArrivalPublished`): a traveller with a
   hat and a bundle on a stick, standing at the house.

Left out of the icon, each for a reason:

- **Straža · The watch** (wolves, a bear or a storm). It is drawn on the sheet: the paw prints and the belfry's
  lantern lit. A threat is solved within its 36 hours, often within the hour. The icon would show it for the rest of
  the day, which would be stale, and it is the one sign that could worry a child. It stays a notification, with the
  dot (below).
- **The merchant, the pedlar and the day's surprise.** They come and go within a session, or nearly every day.
- **A guest from a town, and a gift.** These are waits (below), not the day's news.
- **The weekly feast and the storyteller's evening.** The learner starts them, or they happen every evening.

**When the icon switches.** It decides at the first background moment of a calendar day: the ON_STOP job (15
minutes), or a daily WorkManager job early in the morning. That covers days when the app isn't opened. After a switch,
nothing changes until the next day. An event that happens after the day's switch shows the next morning, while it is
still news (the 2 days). The icon only reads the state saved on the phone and the calendar. It never runs the game.

**How often.** At most once a day. For an active learner it is about 100 to 150 times a year, mostly the "built"
days while the village grows. In "Age and season" mode it is about 6 times a year, plus each new age.

### What's waiting: the launcher's dot, not the icon

Requests, reviews due and gifts change many times a day. An icon would need several switches a day, with the
duplicate or lost icons of some launchers. Every Android launcher already draws a dot (Pixel, Launcher3) or a count
(One UI) on an app's icon while it has a notification in a channel that allows badges. Lani uses that, from its
existing notifications (`BackgroundCheck`). Today they all post to one channel, "tutor". Step 2 splits it, because a
channel's badge is fixed when it is made:

| Channel | Its notifications | Dot | A child's profile (Teo) |
|---|---|---|---|
| Sporočila · Messages | the tutor's answer, the family's challenge, a guest, question or link from a town | yes | yes |
| Vas · Village | the watchman's news (`VillageAlert`: wolves, a bear, a storm, the merchant) | yes | yes, in kind words: "Stražar je nekaj videl · The watchman saw something", never "attacking" |
| Šola · School | new homework from the tutor or the family | yes | yes: "Nova naloga · A new task" |
| Novo · New in Lani | new challenges, words, readings, grammar pages, scenes, stories, the week's progress | no: they wait marked "new" in the app | no |
| Opomniki · Reminders | the evening's "cards waiting" | no: a reminder must not leave a dot all day | none unless a parent turns it on, and then no count and no streak: "Vas te pričakuje · The village is looking forward to you" |
| Sistem · System | an update, "not saved", the approval request | no | no |

- One notification per person or thread, each with `setNumber(1)`, so a One UI count stays small: 1 to 3, never the
  cards due.
- The app removes a notification when its thing is seen (the answer read, the guest greeted), so the dot goes with it.
- A long press on the icon shows the waiting notifications in the launcher's card (the mock in `dots.png`).
- Nothing in a dot nags: no "you haven't practised", no numbers of cards, no streak, nothing red in words.

### The aliases: a scheme that stays sane

An alias is one picture: (the age, the culture from Zaselek on, the look of the day). The looks are:

- plain, on green or on snow;
- the festival, on green (the garland) or on snow (the lights);
- a new age, on green or snow (not at Ogenj, which is the start);
- built, on green or snow;
- someone new, on green or snow (not at Ogenj: the first newcomers come in Tabor).

| | Frames | Looks | Aliases |
|---|---|---|---|
| Ogenj (every culture) | 1 | 6 | 6 |
| Tabor (every culture) | 1 | 10 | 10 |
| Zaselek, Vas, Trg, Mesto × 4 cultures | 16 | 10 | 160 |
| **All** | 18 | | **176** |

- **Names**, made by the generator: `.icon.<Culture><Age><Look>`, for example `.icon.FriuliZaselekSnow` and
  `.icon.PrimorskaVasFestival`. `.icon.Ogenj` is the only alias enabled in the manifest. The generator also writes
  the manifest's alias block between markers. A unit test keeps the list, because an alias is never renamed or
  removed (step 1, "The aliases").
- **The APK**, measured from the generator:
  - flat, one set of three drawables per alias: 6.9 MB of XML, 950 KB zipped. Too much.
  - with the identical layers shared by content: 2 backgrounds (the sky and the hill, green and snow, 7 KB zipped)
    and 16 monochrome layers (8 KB zipped). The 166 foregrounds that differ are 201 KB zipped, and the 176 mipmap XML
    files about 45 KB. **About 260 KB in all.** This is my proposal: plain vector drawables, nothing unusual for a
    launcher.
  - halving it again: each foreground as a `<layer-list>` of the frame (36: 18 frames, green and snow) and the
    sign's overlay (about 24 small vectors). Adaptive icons accept any drawable, but this is one more thing to test on
    every launcher. Only if the size matters.
- **The system:** 176 aliases are 176 entries in the manifest (about 90 KB of binary XML, compressed in the APK) and a
  few hundred KB in the package manager's memory. Launchers only see the one enabled alias.
- **What's dropped when two things compete:**
  - The age and the culture always show.
  - Snow follows the date: December to February, whatever else shows.
  - Of the day's signs, only the highest shows: a new age, then a festival (and the December lights), then built,
    then someone new. The lower one is dropped. "Built" and "someone new" can still show the next day, while they are
    2 days old.
  - The threat, the waits and the time of day are never in the icon.

### The setting, replacing step 1's

"🎨 Ikona · App icon", under "🌐 Languages":

- **Kaj se dogaja · What's happening**: the age, the season, the festivals, built and someone new. At most once a
  day. On by default where the launcher keeps the icon in place: the default home app is Launcher3-based (Pixel,
  AOSP; Nothing's once Jan's phone passes the test).
- **Doba in letni čas · Age and season**: the age, snow and the December lights. About 6 switches a year, plus each
  new age. The default on other launchers: One UI, MIUI and unknown ones (Teo's tablet until it is tested).
- **Ne spreminjaj · Keep it as it is**: never switches.

Strings (machine-written, for a native check): sl "Kaj se dogaja", "Doba in letni čas", "Ne spreminjaj"; en
"What's happening", "Age and season", "Keep it as it is"; de "Was gerade los ist", "Zeitalter und Jahreszeit",
"So lassen"; it "Cosa succede", "Epoca e stagione", "Lascia così". Also the age and sign labels on the sheets (Nova
doba, Praznik, Zgrajeno, Prišlek, Straža, Obvestila) and the channel names and dot texts in the table above.

### The risks, added to "The Android risks" above

- **More switches, more exposure.** Every switch is another chance for a launcher to duplicate (One UI) or lose
  (EMUI) the icon. Hence "What's happening" is on by default only where the launcher is known to keep it, and the
  developer button tests a phone first.
- **The icon lags by up to a day.** Only day-long news belongs in it. The waits go in the dot.
- **176 fixed names.** The art can change under them; the names can't. A new kind of sign later means new aliases.
- **The manifest is generated.** The generator writes the alias block, and the test checks it against the list.
- **A child's profile.** The signs are joyful only, the watch is never in the icon, and the dot's words are kind. The
  reminder is off unless a parent turns it on.

## Step 2: built (Jan: "ok yes" to step 1b)

### What

- **The launcher icon is the village's picture of the day:** 176 activity aliases of `MainActivity`, step 1b's scheme.
  - The art is drawn by `companion/android/tools/app_icon.py build`: per alias a foreground and an adaptive icon in
    `mipmap-anydpi-v26/ic_icon_*.xml`, the 2 backgrounds and 19 monochrome layers shared by content.
  - The generator writes the manifest's alias block between markers. Only `.icon.Ogenj` is enabled there.
    `MainActivity` keeps every intent filter but the launcher's.
  - The application's own icon (`ic_launcher`: Settings → Apps, the share sheet) is the neutral L.
- **What it shows** (`data/AppIcons.kt`, pure), from the saved village and the calendar:
  - the age;
  - the culture from Zaselek on (a culture without a look: Primorska's);
  - snow from December to February;
  - the day's highest sign: a new age (its day and the next), then a festival (its day, its grace days while it isn't
    celebrated, the village's own festival event, and December 20 to January 6 the lights), then something built (a
    building's `builtAt` or a project's `projectsFinished` in the last 2 days), then someone new (a resident's `since`
    in the last 2 days, the founders not).
  - Ogenj has neither a new age nor a newcomer.
- **When it switches** (`data/AppIconWork.kt`):
  - It decides at the first background moment of the day: 15 minutes after the app goes to the background
    (`ProcessLifecycleOwner` ON_STOP; ON_START cancels it), or the daily job that first runs at 04:30. It never
    decides while the app is in the foreground.
  - It decides once a day. A new choice in the setting, or the developer's button, decides again, a few seconds after
    the app goes to the background.
  - `AppIconSwitch.show` enables the new alias first, then sets the others to DEFAULT (`.icon.Ogenj` to DISABLED), in
    one `setComponentEnabledSettings` on Android 13+, in that order with `DONT_KILL_APP` below.
  - **The repair:** at every start, if no launcher entry is enabled, the last one shown is enabled again. This only
    ever enables, so it is safe in the foreground. After an update, `AppIconUpdateReceiver` (`MY_PACKAGE_REPLACED`)
    repairs and decides a minute later. Two entries left by a switch cut short are put right by the day's decision.
- **The setting** "🎨 Ikona · App icon", under ⚙️ Settings → 🌐 Jeziki · Languages:
  - "Kaj se dogaja · What's happening", "Doba in letni čas · Age and season", "Ne spreminjaj · Keep it as it is".
  - Until the learner chooses, the default is "What's happening" only on Pixel's or AOSP's Launcher3
    (`com.google.android.apps.nexuslauncher`, `com.android.launcher3`). Everywhere else it is "Age and season",
    Nothing's launcher and Teo's tablet included, and the note under the choices says why.
- **The developer's button:** 🛠️ Developer → "🎨 Preizkusi naslednjo ikono · Try the next icon" cycles ten pictures.
  "Ikona dneva · The day's icon" goes back. QA's hook "icon:<alias>" does the same.
- **`ic_notify`**: a 24 dp white silhouette of the neutral L, for every notification's small icon.
- **The wordmark:**
  - the setup's welcome, over "Welcome!" (`lani_wordmark`);
  - the README's top, with the icon;
  - the site's header (the icon) and hero (the wordmark, a dark one for a dark scheme);
  - the site's favicons (SVG, 32 px, the 180 px touch icon).
  - All of it comes from the same build, and `build --check` covers it.
- **Retired:** `launcher_icon.py`, the bubble with Č.
- **The notification channels and the dot:** see "The notifications" below.

### How to use

- **Rebuild the art** after changing the drawing: `python3 companion/android/tools/app_icon.py build`. The Python
  test `companion/tests/test_app_icon.py` runs `build --check`, so a drawing changed without a build, or a generated
  file edited by hand, fails.
- **A new picture** (a new sign, a new culture): add it to `icon_looks()` and `AppIcons.all` in the same order, build,
  and append its names to `app/src/test/resources/app-icon-aliases.txt`. Never rename or remove a name in that file.
- **Test a launcher:** 🛠️ Developer → "Try the next icon", then press Home. In a few seconds the icon changes in
  place. Look for a second Lani icon, or a lost one.
- **QA:** `companion/bin/qa-slot --no-town --steps app-icon`.

### The numbers

- **176 aliases.** The generator writes 378 files: 176 foregrounds, 176 adaptive icons, 2 backgrounds, 19 monochrome
  layers, the application's icon (4), `lani_wordmark`.
- **In the debug APK** (measured): 379 icon files, 1 673 KB of binary XML, **349 KB compressed**. The manifest is
  99 KB (12 KB compressed). That is about 0.5 % of the 65 MB debug APK. Step 1b estimated about 260 KB from the plain
  XML: the binary form is larger.
- **Switches:** at most one a day; about 6 a year in "Age and season", plus each new age.

### QA

`companion/bin/qa-slot --no-town --steps app-icon`, twice: on slot 1 (AVD lani-qa) and, after the notifications were
merged, on slot 2 (AVD fluent-qa). Both run Android's Pixel launcher (`com.google.android.apps.nexuslauncher`). The
screenshots and `app-icon.txt` are in
`/tmp/claude-1000/-mnt-scratch-github-fentas-ai-slo/721724ea-4389-48ad-b641-b6d1d5157803/scratchpad/ux-app-icon/step2/`
(`montage1.png`: the four home screens; `dots.png`: the dot and its going).

| | The package manager resolves | Lani icons on the home screen |
|---|---|---|
| after the install (an update over an earlier QA build, whose icon pointed at `.MainActivity`) | `.icon.Ogenj` | 1, in its place in the hotseat |
| "icon:.icon.PrimorskaVasFestival", Home | `.icon.PrimorskaVasFestival`, 14 s later | 1, in place |
| "icon:.icon.FriuliZaselekSnow", Home | `.icon.FriuliZaselekSnow`, 14 s later | 1, in place |
| "icon:" (the day's), Home | `.icon.PrimorskaVas` (the dev village is at Vas, Primorska, in October), 14 s later | 1, in place |

- **No duplicate and no lost icon** on either emulator. The update from a build whose launcher entry was `MainActivity`
  kept the home-screen icon too: Launcher3 pointed it at `.icon.Ogenj`.
- **The dot:** a message's notification (QA's hook "notify:message": the channel `messages`, the tag `chat`,
  importance DEFAULT) shows the Pixel launcher's dot on the icon. Opening the chat (`lani://chat`) cancelled it: 0 of
  Lani's notifications left, and no dot.
- **Not tested here:** Nothing's launcher (Jan's phone) and Teo's tablet. Both start in "Age and season" until they
  are tried with 🛠️ → "Try the next icon".

### The notifications and the dot

Built by a helper on `app-icon-notify`, cherry-picked here (companion/README.md "Notifications").

| Channel | Name | Dot | Importance |
|---|---|---|---|
| messages | 💬 Sporočila · Messages | yes | DEFAULT |
| village | 🏘️ Vas · Village | yes | DEFAULT |
| school | 📝 Šola · School | yes | DEFAULT |
| new | ✨ Novo · New in Lani | no | LOW |
| reminders | 🔔 Opomniki · Reminders | no | DEFAULT |
| system | ⚙️ Sistem · System | no | DEFAULT |

- **Migration** (`data/NotifyChannels.kt`, `data/Notifier.kt`): runs at the start, on a language change and before
  every post. The old "tutor" channel's settings carry over: blocked stays blocked for every new channel, nothing gets
  louder than it was, a dot turned off stays off, and the sound, vibration and lock screen are copied. "tutor" is
  deleted only after the six exist.
- **Counts:** every notification has `setNumber(1)` and a stable tag per thing (`chat`, `family:<id>`,
  `guest:<town>/<who>`, `school:<unit>/<id>`, `village` …), so a count stays 1 to 3, never the cards due.
- **Gone when seen:** while the app is on screen, what it shows cancels the matching tags. For example, the chat open
  cancels `chat`, the village cancels the village alert and the guests, a scene cancels its own.
- **A child's profile:** the watchman's news in kind words ("🏰 Stražar je nekaj videl · The watchman saw something",
  "Pridi pogledat · Come and look"). The evening reminder is off unless a parent turns it on under the new section
  "🔔 Obvestila · Notifications" (parent-gated on a child's device). When on, it says "🔥 Vas te pričakuje · The
  village is looking forward to you", with no streak and no number.
- **`ic_notify`** is the small icon of every notification, the road's too.

### Strings for the native check

Machine-written, in sl, en, de and it: `appIcon.title`, `appIcon.happening`, `appIcon.ageSeason`, `appIcon.keep`,
`appIcon.note`, `appIcon.tested`, `appIcon.untested`, `developer.iconNext`, `developer.iconDay`,
`developer.iconNote`, and the 18 `notify.*` keys (the channels' names, the setting, the child's texts).

### Tap counts

Before: the icon couldn't be changed. Now "Automatic" needs no tap. A fixed choice takes 4 taps from Home: ⚙️ →
🌐 Jeziki · Languages → the mode's chip (the setting's section is on the Languages page).

## What's left

- **Test the launchers that aren't Launcher3.** On Jan's Nothing Phone (4a) and Teo's tablet: 🛠️ Developer → "Try
  the next icon", Home, and look for a second Lani icon or a lost one. If one keeps the icon in place, add its
  package to `AppIcons.KEEPS_IN_PLACE`, so it defaults to "What's happening".
- **A native check** of the strings above (sl, de, it, and the English).
- **The road's channel** (the car's "getting ready", RoadPrepService) still has the default badge, so its ongoing
  notification may show a dot. Changing that needs a new channel id.
- **A phone paired with a typed token** counts as an adult's in the background until the app has been opened once on
  this version. A QR-paired or hosted phone is right from the start.
