# Plan: Lani's app icon and logo, and an icon that changes (app-icon, 2026-10-31)

Jan, 2026-10-31: "also lets design a nice app logo/icon - maybe also a changing one? - duolingo does it".

This is step 1 of 2: **concepts only**. Nothing is built into the app yet. Jan picks a concept; step 2 builds it (the
aliases, the setting, the switch). The generator draws every concept already, so the pick builds straight from it.

Today's icon (`companion/android/tools/launcher_icon.py`) is a speech bubble with Č over Triglav at dusk. It is
Slovene, and it is wrong on Teo's tablet, where Teo learns Italian in Friuli.

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

## The changing icon

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

## What's left (step 2, after Jan picks)

- Build the picked concept: the 21 aliases' drawables from the generator (backgrounds shared), the manifest aliases,
  `MainActivity` without its launcher filter, `ic_notify`.
- The switch (`IconChooser`, pure, with date tests; `IconSwitcher` with the background job and the repair), the
  setting "🎨 Ikona · App icon" in sl, en, de and it, a test that keeps the alias list.
- The wordmark into the README, the setup welcome screen (above "Welcome!") and `site/` (favicon, apple-touch-icon).
- Test on the emulator (Launcher3), Jan's Nothing Phone (4a) and Teo's tablet with the developer button.
  Screenshots of the home screen before and after.
- Replace `launcher_icon.py` with `app_icon.py` once the new icon is in.
- A native check of the Slovene labels and strings above.
