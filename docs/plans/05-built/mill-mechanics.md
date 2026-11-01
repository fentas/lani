# Built: the mill's wheels and water, as a mill's go

Jan, 2026-10-31, with a screenshot of V mlinu from the phone: "water goes through the gear + also are the gear rotations
correct (the inner gear runs oposite of the outer gear)". Both were so. This makes the mill's drive train and its water
work as a real overshot mill's, in V mlinu and Friuli's Nel mulino (one painter), and checks the stream's mill and the
village map's for the same two questions.

## What was wrong

- **The cog wheel turned against the waterwheel.** Both sit on one shaft along x (the cog wheel under the platform, plane
  x = GX; the waterwheel outside, plane x = WX), in the same (dy, dz) frame, yet the cog wheel read its marks at
  `atan2(dz, dy) - a` and the waterwheel at `+ a`. On one shaft they must turn the same way at the same speed.
- **The water fell across the wheel.** `wheelWater()` poured a sheet from the flume's end (y = SY − 0.55) straight down
  the wheel's own plane to the race: in the isometric view it ran over the wheel's face, through its hub (Jan's picture).
- **The water landed on the wrong side.** The waterwheel's top goes downstream (+y, the way the race runs), right for an
  overshot wheel; but the flume ended at SY − 0.45, upstream of the top, so its water would have driven the wheel the
  other way.
- **Smaller things that didn't turn with it:** the shaft's "knot" slid along the shaft as a screw does; the wheel's
  icicles hung at fixed places while it turned. In the stream scene the wheel's water ran at 0.85 of its radius, inside
  the rim over the spokes, and in the snow the wheel turned with no water and a cap of snow that didn't turn. On the
  village map the undershot wheel's paddles went against the race they stand in, and it turned in the frozen race.

## What it does now

**V mlinu / Nel mulino** (`game/render/scene/MillPainter.kt`; the culture's look changes only the grain and the room, so
both are the same mill):

- **One shaft.** Both wheels read their marks through `mark(dy, dz, a) = atan2(dz, dy) + a`: they turn together by the
  shaft's angle, their tops downstream. The waterwheel was right; the cog wheel's sign was the bug.
- **The pinion from the mesh.** The cog wheel's pegs at its top meet the lantern pinion on its side toward them (+x: the
  spindle stands behind the cog wheel's face, PIN_X < GF). There the pegs go downstream (+y), so the pinion turns from +x
  toward +y (seen from above clockwise, since x, y and z up are a left-handed set): in the picture its near staves go
  left. `spindle(a) = GEARING × a` gives the pinion and the runner that angle; they were already turning that way. The
  pinion is under the platform, whose top hides it from this view; it is drawn inside the `gear` thing now (same object
  ids, same pixels) so a test can draw the gearing alone.
- **The water, as an overshot mill's runs.** The flume ends just past the wheel's top (`FLUME_END = SY + 0.3`); its water
  pours off the end in a short arc into the bucket on the downstream side (about 79° round from downstream), spray where
  it hits. The buckets ride down full (`bucketFill`: filling from 86° to 72°, full to the axle's level, tipping out to
  empty by −30°, before the race's water at about −32°), their water seen between the two shrouds over the near one's
  edge, slopping and glinting; drops fly off them. Low on the downstream side they empty past the rim into the race,
  spray where it lands, flecks of foam drifting on downstream. Nothing is drawn over the wheel's face or its hub
  (`onWheelFace` masks what the near shroud hides). `grind` below 0.12: everything still, the flume dry, no water; the
  easy and hard speeds as they were (0.7 and 1.7 rad/s), a gush grinding hard.
- **Turning with it:** the shaft's two knots go round (over its top downstream, down its face toward us); the wheel's
  rime and icicles only while it stands still.

**The stream's mill** (`StreamPainter.wheel`, Ob potoku / Al ruscello): its wheel is drawn facing us, its top going
right, the way the flume's water comes to it on the screen and falls just past the top: that was right. Its water now
rides in the buckets outside the rim between the paddles, down the same side to the tailrace, not over the spokes; in
the snow it turns with its water (the flume's too) and sheds the snow, which lies on its top only while it stands still.
`wheelSpin()` is the one place the wheel and the flume read how fast it goes.

**The village map's mill** (`Landmarks.mlin`): an undershot wheel in its race, the race running off the stream (+x: its
ripples drift that way, the spray is thrown downstream). Its paddles in the race now go with the water, its top against
it; in the snow (the race frozen) it stands.

**Other turning things** (a grep for wheel, gear, crank, spin in game/render): the stream's horse and cart (its wheels
roll with the ground it covers, the top going the way it goes: right), the market's and the field's carts, the fire
station's reel and pump, the smithy's cart wheel (all standing), the map's travelling cart (two-step frames), the
school's globe (noise continents, no direction to get wrong): nothing else to fix.

## How it is checked

`MillMechanicsTest` (game/render/scene), from the pixels drawn:

| Test | What |
|---|---|
| the waterwheel and the cog wheel turn together on their shaft, their tops going downstream | each wheel drawn alone (`ArtPainter.solo`), two frames a quarter radian apart: the turn that lays the second frame's arms (and the cog wheel's pegs) best on the first's. Both turn the shaft's angle (0.25 rad), the same, positive (marks from +z toward +y: the top downstream), at grind unset, 0.3 and 1; 0 stopped |
| the cog wheel's pegs drive the pinion and the runner round as they mesh, three times as fast | the spindle behind the cog wheel's face; the runner's grooved top (drawn alone) turning 3 × the cog wheel's speed from +x toward +y; the pinion's near staves (the gearing drawn alone) moving left |
| the flume's water falls into the buckets just past the top and rides down the downstream side, never over the wheel's face | at a moment the shaft has turned whole eighths (the wheel's pattern where it stands still), compared with the still wheel: inside the buckets' ring (arms, hub, what shows between them) not a pixel drawn otherwise or by anything after the wheel; every new water pixel (the buckets', the pour's, the falling water's) downstream of the axle; the pour lands just past the top, the buckets empty low on the downstream side |

Each check fails on the old code: the cog wheel's old sign (it turned −0.235 rad against the waterwheel's 0.25), the
spindle turned back (−1.9 rad/s against 2.1), the old sheet put back (the face drawn over in 2420 px). `MillRenderTest`
also checks the shaft and the flume still at grind 0 and moving while it turns.

**Digests taken again**, after looking at the frames: `CultureLooksTest` "mill" (day, dusk-winter, night-k2: only the
wheel, its water, the flume's end, the shaft and the cog wheel differ; a diff of the whole picture marks nothing else)
and "stream" (its wheel's water and its winter); `FriuliOutdoorLooksTest` "stream/classic" and "stream/coast" (day,
dusk-winter, night-k2; unbuilt and the afternoon closer up don't show the wheel and kept theirs); `ClassicLandTest`
"town" (its finished mill on the map: all six views, each differing only in the wheel's box, 8 × 12 px at detail 1 up to
18 × 20 at detail 3: its paddles going with the race, standing in the frozen race of the winter night). Friuli's indoor
look has no digests (FriuliIndoorLooksTest compares it with the Slovene one).

## How to look at it

- `MillMechanicsTest` "render the turning mill for review": build/scene-snapshots/mill-mechanics, eight frames over a
  bucket's travel from the flume down to the race (0.35 s apart at the easy turn) at detail 1 to 3, by day, at night and
  in winter, Primorska's and Friuli's look; grinding hard and stopped; the three frames CultureLooksTest pins; the
  stream's mill and the map's.
- `MillLightTest` "render the wheel's water by day, at dusk and at night for review": build/scene-snapshots/mill-light,
  the wheel and the flume grinding hard by day, at dusk, at night (autumn, summer, winter), Primorska's and Friuli's look,
  detail 1 to 3, and the whole picture.
- Emulator QA: `LANI_DEV_DATA=<a synthetic learner> LANI_QA_MILL_SHOTS=<dir> companion/bin/qa-slot --no-town --steps
  mill-turning`: in from the stream's mill (2 taps, as before), closer up, the waterwheel dragged to the middle (its area
  from the debug build's log, tag SceneAt; the drag starts well inside the screen's edge, as a swipe in from the edge is
  the system's Back), a burst of eight screenshots on the device a fraction of a second apart and the wheel's frames side
  by side. On 2026-10-31 (slot 1, a synthetic learner): the burst 0.25 to 0.34 s apart, the wheel turning its top
  downstream, the flume's water pouring into the bucket past the top, the buckets full down the near side and emptying
  into the race, nothing across the face. `LANI_QA_MILL_HOUR=22.5` shoots it at night (2026-11-01, polish-app: the
  flume's, the buckets' and the race's water the same night blue, as the fix's APK shows it; main's showed the flume's
  bright over a black race and black buckets).

## Taps

Nothing to tap changed: into the mill from the stream 2 taps (the mill, "🚪 Vstopi"), from the map 2, as before. The
pinion is part of the `gear` thing now, but the platform hides it, so the cog wheel's hit area is what it was.

## What's left

- The pinion can't be seen from this view (the platform's top covers it). A cut-away or a peek under the platform would
  show the gearing work; not asked.
- ~~The water at night is the race's (lit by the ambient light, dim); the flume's water keeps its old unlit blue.~~
  Done 2026-11-01 (polish-app, docs/plans/05-built/polish-app.md): the race's, the buckets', the pour's and the falling
  water were drawn through `Env.lit` (meant for what skips the light pass) and then lit again by the pass, so at night
  they kept about a ninth of their red (0.11) where the wheel's wood and the flume's water, lit once, kept 0.41. Now all of
  the mill's water is drawn in its daylight colours and lit once, as the wood beside it (`MillLightTest`); by day under a
  clear sky nothing changed (its digests, and CultureLooksTest's "day"); CultureLooksTest "mill" dusk-winter and night-k2
  taken again (only the water's pixels differ).
- No new strings.
