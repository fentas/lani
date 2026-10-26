# Built: the coast's sea up to the horizon, a cheaper frame, a quieter sea

Jan, 2026-10-26, from a visit to Teo's coast village (Friuli, land `{"kind": "coast", "seed": 551323397}`) at night on a
phone held upright, a screenshot with arrows at the top of the map:

- "this strip looks weird - kind of a cut-off moving sea behind the scene?"
- On Teo's tablet (Galaxy Tab S6, 2560 × 1600, Android 12; earlier: about 400 ms a frame in his village, the main thread
  busy, the GPU idle), visiting Jan's classic village lags noticeably less than Teo's own coast village.
- "the sea sounds are way too loud"

The rules as built are in companion/GAME.md ("The land": "The sea runs up to the horizon", "The ground is drawn once";
"Background sounds"). This note says why it was so, what changed and how it is tested.

## The strip

**Why.** The far sea along the horizon (the backdrop's band, `Backdrop.sea`) is laid out for a culture's sea seen over a
valley: a band 6 nominal px above the horizon, its foot behind the far treeline. A coast leaves the treeline out where its
own sea comes up to the horizon (the land's sea starts at the horizon's row). So there the 6 rows between the band's foot
and the horizon showed the backdrop's near hills: a dark strip at night, a grey-green one by day, from the canvas's left
edge to where the treeline starts. Since 438d8caa ("the sea of a coast and a lake are in view on every canvas of the
town") the shore lies 122 to 136 px left of the fire at the horizon, so the strip is on every canvas of the town; before,
the shore lay at the canvas's edge or off it. The band also had colours of its own (the water mixed with the sky, a
deeper colour in its lower part) and glints that drifted sideways, while the land's sea twinkles in place: even without
the strip, the two read as two seas, the far one moving behind the village.

**What.** `game/render/Sea.kt` is how the coast's sea looks, near and far. Left of where the land's sea and its beach end
at the horizon (`seaTo`), the far sea comes down to the horizon and joins the land's (`Backdrop.coast`). Both take the
same colours: the water's own, with a little more of the sky's toward the horizon (`Sea.hazeAt`: 0.3 at the horizon, 0.44
at the far line, nothing from 28 px below the horizon; in steps of 0.04, dithered between, like the sky's bands). Both
have the same glints (`Sea.glint`, the near sea's twinkle) and the same light (the backdrop lights its sea with the
frame's ambient light, which is what the light pass does to the ground). Closer up, the near sea's ripples thin out and
take the haze toward the horizon (`Ground.SEA`). The far treeline ends low at the beach and rises to its height over 8 px
(`Sea.TREELINE_TAPER`), the far sea behind it. The far sea's line against the sky is fainter at night (`Sea.line`).

A lake lies at least 16 px below the horizon on every land (its far shore, the noise of its shore taken in): nothing cuts
it at the map's top edge, and nothing changed for it. The classic land, the dry lands and a culture's sea behind the
treeline (Friuli's valley, hills, mountains and lakes) render as before, pixel for pixel: 196 pictures of the classic land,
a valley and two lakes, by day, at dusk, at night, in the storm and in winter, on the town's canvases at every detail and
on the Home strip, the same before and after; each of the 196 of four coasts changed.

## The frame's cost

**Measured** (the JVM, offscreen, 40 frames after 20 to warm up, the per-pass profile of `VillageRenderer.profile`; Teo's
camp and the same village grown to Vas, on Teo's coast, the valley of the same seed, a lake, the classic land; the
tablet's canvas, 320 × 200, and a phone's held upright, 360 × 800; at details 1 to 3, by day and at night). The ground
pass is most of every frame on every land: 4 to 8 ms of 7 to 10 on the tablet's canvas, 12 to 22 of 20 to 30 on the
phone's. The coast costs what the valley of its seed costs, and the sea's pixels cost less than the land's (they skip the
clearing's rim, the forest and the grass); a generated land costs a little more than the classic one (the stream's two
waves, the rim's noise). The far sea's band is a fraction of a millisecond. So the sea isn't what makes a frame dear: the
ground is, drawn whole about 12 times a second though only its water moves.

On the emulator (QA's `sea-frames`: the debug build, Teo's land founded on the dev bridge's copy of a synthetic Friuli
learner, then the same camp on the valley of its seed, a lake and the classic land; the display as Teo's tablet on its
side, 2560 × 1600, a 320 × 200 canvas; at night; the map's own render time, logged by the debug build every 24 frames, tag
`TownScene`), drawing the ground whole as before:

| Land | At rest (detail 1) | Closer (2) | Closest (3) | Panned at 3 |
|---|---|---|---|---|
| Teo's coast | 96 ms | 130 ms | 139 ms | 136 ms |
| The valley of its seed | 95 ms | 119 ms | 131 ms | 125 ms |
| A lake | 106 ms | 141 ms | 119 ms | 126 ms |
| The classic land | 86 ms | 104 ms | 107 ms | 108 ms |

The coast costs what the other generated lands cost, 10 to 30 % more than the classic land; the sea adds nothing of its
own. Teo's tablet took about 400 ms a frame in his village, three to four times this emulator's: the same frame, mostly
ground, a little dearer on a generated land than on Jan's classic one.

**What.** `game/render/GroundCache.kt`: the ground pass draws the ground once and keeps its picture (from the horizon
down) with the water that moves: each such pixel, how it moves (the sea's surf, its glints, the twinkle of the stream,
the pond and a lake, a torrent's white water) and its two colours. The next frames take the picture back and draw only
that water (`GroundCache.shade`, which the pass draws with too), until something the ground shows changes (`GroundKey`:
the land, the canvas, the lens, the horizon, the age and the forest's edge as it draws back, the road and the square, the
paths, the woods' layout, the month, the weather, and the sky's colour at the horizon where the ground shows it). The
frames are the same pixels as the ground drawn whole. Panning at a closer look changes the lens, so a pan draws the
ground whole each frame as before; at rest and zoomed out it is kept.

After, the ground kept (the same measurements; the JVM's are the middle of 31 frames, `GroundCacheTest`):

| Where | Ground drawn whole | Ground kept |
|---|---|---|
| Emulator, Teo's coast, 320 × 200, at rest / closer / closest | 96 / 130 / 139 ms | 22 / 25 / 22 ms |
| Emulator, the classic land, the same | 86 / 104 / 107 ms | 25 / 27 / 21 ms |
| Emulator, Teo's coast, panned at detail 3 | 136 ms | 115 ms |
| JVM, Teo's camp, 320 × 200, details 1 / 2 / 3 | 8.5 / 13.0 / 11.1 ms | 2.2 / 2.9 / 2.1 ms |
| JVM, the classic land at Vas, 320 × 200 | 9.7 / 9.3 / 10.1 ms | 3.0 / 2.9 / 1.7 ms |
| JVM, Teo's camp, a phone's 360 × 800 | 31 / 29 / 29 ms | 7.9 / 8.3 / 7.2 ms |

The emulator's frames (dumpsys gfxinfo over 8 s, the frame's whole time: the map, the screen's other drawing, the
emulator's software GPU at 2560 × 1600 on a busy machine) went from 60 to 86 frames at a 50th percentile of 200 to 300 ms
to 178 to 229 frames at 93 to 117 ms at rest; the emulator's GPU is slow where the tablet's isn't (4 to 11 ms there), so
the map's own time is the number to carry over. Panned at a closer look the frames stay at 200 to 300 ms (the lens moves:
"What's left").

## The sea's sound

Since 438d8caa the village on the coast heard its sea at 0.6 (`Place.Village.beach`), louder than the birds and the
crickets (0.6 at most), and 0.35 where the culture's horizon only shows the sea. The sound's change (the sea a quiet
background in the village, nearer only as the view comes onto the beach, ducked under a voice; a lake's water checked the
same way) comes in its own commit, with its levels in GAME.md, "Background sounds".

## Tests

- **App:** the unit tests, 2,966, all passing (with the sound's changes in). New: `CoastHorizonTest` (the strip's region
  the sea's, on Teo's land and three other coasts, on a phone upright, a tablet either way and a phone on its side, at every
  detail, by day, at dusk and at night: it fails on the strip, about two fifths of the region the hills'; no edge where the
  two seas meet, in the storm and in winter too; a lake at least 16 px below the horizon over 303 seeds; the dry lands'
  treeline along the whole horizon; pictures for review in build/land-snapshots), `GroundCacheTest` (1,302 frames the same
  kept and drawn whole; the kept frame cheaper), `QaHooksTest` ("render:full"). `CoastTest` counts the hazed sea as sea.
  The frame budgets: every landscape's town at night on 360 × 800 in 5 to 6 ms a frame (`LandsTest`, against 50), the
  village at every detail in 7 to 8 ms (`LodTest`, against 30).
- **Pictures** (the JVM, before and after, every coast, lake, the classic land and a valley; by day, at dusk, at night,
  in the storm and in winter; on 360 × 800, 320 × 200 and 400 × 180 at every detail and on the Home strip at 360 × 174 and
  320 × 90): the 196 of the coasts changed, the other 196 the same.
- **The emulator** (the debug build; the dev bridge on a synthetic learner, Teo, a child, Italian from German, culture
  friuli, made by the bridge's `initLearnerData`; `LANI_CULTURE=friuli LANI_LANDSCAPE=coast LANI_QA_FOUND_SEED=551323397`;
  `companion/bin/qa --no-town --steps coast,sea-frames`): setup, sea-frames and the crash check passed. The coast step shot
  the founding's preview, the village, the beach, the sea's horizon closer up by night, at dusk and by day (one sea up to
  its line, no strip) and the tablet held upright; it then failed tapping the squirrel for its word card (the animal hops:
  the same step's animal part passed on 2026-10-26 before this change, which doesn't touch taps). sea-frames' numbers are
  above.

## What's left

- **A pan at a closer look** still draws the ground whole each frame (the lens moves by whole canvas pixels). Keeping a
  wider picture and shifting it would make panning cheap too; the performance round on the tablet can measure whether it
  is needed.
- **The rest of a frame** (the objects, the outline and the light passes, the backdrop) is now most of it: the trees are
  drawn anew every frame though they don't move. The performance round on the tablet can look at them next.
