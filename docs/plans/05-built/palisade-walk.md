# Built: nobody walks through the palisade (2026-10-30)

Jan watched the village's people walk straight through the palisade, the ring of stakes round the clearing, instead of
through its gates. This note says why it happened, what was built, how to see it, how it is tested, and what is left.
The rules as built are in companion/VILLAGERS.md ("Walks") and companion/GAME.md (the palisade's paragraph under
"Buildings").

## Why it happened

The walks on the map (`TownPeople.route`, `Walkways`) went round the fire, the buildings, the fields' crops, the
landmarks, the campsite and the wolves, but knew nothing of the palisade. Into the woods below the village a walk went
straight from wherever someone was to where the path comes into the meadow, and on to the pond, the high seat or the
charcoal pile: a line from a house on the left to the meadow crosses the ring at the lower left, nowhere near the path's
gate. Nothing else kept to the ring either: the woodcutter's stack in the woods, the merchant's cart parked on the road
past the main gate, the wolves' "step out of their reach" when they came up to the ring, a household's yard or washing
line that lies beyond the stakes at a plot on the rim of a generated land, and a walk between two places near the rim
where the ring bends in between them.

Measured with the old ways on the same places (every pair of places people go to, `PalisadeWalksTest`'s "render the
walks before and after"): on Jan's hamlet on a phone 340 of 1,190 walks went through the stakes, on Teo's coast on the
tablet upright 109 to 250 of about 1,000 at every age, on the tablet on its side 0 to 18 (no woods there: the walks along
the rim), and on a classic town, a lake's and the mountains' about one walk in six on a phone (152 to 179 of about
1,000). Now none.

## What was built

- **The ring as people walk it** (`game/render/PalisadeWalk.kt`): the stakes are a wall, the road's gates, the path's
  gate into the woods and the river gate down to the stream are its doors, each with a point a step inside and a step
  outside its middle. Which side a point is on is the ring's own line, closed across each gate. The walls are kept in a
  grid of 1.5-cell squares, so a crossing test walks only the squares a walk goes through (0.1 to 0.2 µs). Worked out once
  per ring (it changes only with the age, the land and the woods below the canvas) and kept with the village's ways.
- **Through the gates** (`Walkways.along` with the village's `Ways`): a leg between a place inside and one outside goes
  through the door that makes the whole walk shortest, straight through the middle of its opening, round what is in the
  way on each side by that side's corners only. A leg on one side stays on it: where the straight way would go through
  the stakes (the ring bends in between), it bends towards the fire inside (away from it outside, or to either side) until
  it is clear; outside, in at one gate and out at another when that is shorter than round the ring. A door the wolves
  stand in is used only when no other will do.
- **Into the woods by the path** (`TownPeople.route`): where the palisade stands, a walk into the woods goes through the
  path's gate and down the path, as far as it goes nearest where they go, and back the same way.
- **Standing on the right side**: whoever stands at a place stands on its side of the stakes (`spotAt`, the children's
  games, the strangers' step aside at the road); a household's nook that would lie beyond the stakes or among them comes in
  nearer the house, or round it on another side (`TownPeople.nookAt`; also the hens' huddle while wolves are about).
- **The wolves** (`TownReactions.rim`): someone near them steps out of their reach on their own side of the ring, in
  sight across no stake; a defender comes in to their spot the same way.
- **The order at a gate** (`PalisadeWalk.depth`, `VillageRenderer.addPeople`): who passes a gate is behind its posts or in
  front of them by where they stand (as before); under a lintel, the beam is drawn over them while they are behind its
  line and behind them once they are in front of it (out to 2.5 cells), where before the beam, drawn with the nearer
  post, covered the head of someone a step out of the gate. `TownReactions.hidden` (where a defender can be seen) follows
  the same order.

**Where it applies.** Only where the palisade stands: a village without one, or before it is built, walks exactly as
before (`Walkways.of` has no ring then, and the test compares every walk with the old ways). Every mover on the map goes
by these ways: the residents' walks between their places, their errands and their steps over at a place, the children's
games, the strangers at the road, the unnamed people going from door to door, everyone's walks while the village reacts
(wolves, a bear, a storm, the merchant, a feast), the one patching up a building. The animals stay outside the ring as
before (`Wildlife`), the merchant's cart comes up the road through the main gate, and the visitors stand at their spots.

**The breach.** A damaged palisade has a breach (`Palisade.breachAt`: three stakes knocked down, two broken off either
side). It is no way through: the fallen stakes lie across it, the broken ones still stand, and the village keeps to its
gates until it is patched up (the one patching it stands inside). So the ways don't change with the damage.

**Cost.** The ring's walls and doors take about 0.04 ms once per ring; a walk with its gates about 0.2 ms the first time
on Teo's town (Mesto, the tablet upright), then remembered with the village's ways as before. All of it on the render
thread, where the walks were worked out before; the main thread never sees it.

## How to see it

- On the map: any village with a palisade, on a phone held upright (the woods below), at a time someone goes into the
  woods (Luka to the meadow in the morning, Anton to the pond): they walk to the path's gate, through it under the lintel,
  and down the path. QA's `hour:<h>` and `show:path` (README, "Test") put the clock and the camera there.
- QA's step `palisade-walk` (`companion/bin/qa --steps palisade-walk`): the hamlet with its palisade, the path's gate up
  close at the moments of `$LANI_QA_GATE_WALK` ("h:mm[:ss][/spot]", `/road` for the main gate), and four seconds on;
  when unset, the shepherd's night ritual (out to the sheep in the meadow at 21:25, home at 21:45 with his lantern,
  which no request of the day moves), through the path's gate a quarter of a minute later in QA's village.
- Pictures (`PalisadeWalksTest`, build/village-snapshots/palisade-walks): `before-…` and `after-…`, every walk between
  the village's places drawn over it (red through the stakes, green not; the gates people walk through in cyan, the
  closed ones in magenta), for Jan's hamlet (phone and the tablet on its side), a classic town, a lake, the mountains, and
  Teo's coast at every age on the tablet both ways round; `strip-before-into-the-woods`, `strip-after-into-the-woods`
  (someone going into the woods, frame by frame, where the walk meets the ring), `strip-after-out-of-the-main-gate`
  (under the lintel up close).

No screen and no tap changed: tap counts before and after are the same (none: the map is watched, not tapped).

**On the emulator** (2026-10-30, a 1080 × 2400 phone, this branch's debug build, a synthetic learner from
`make-learner.ts`): QA's step `palisade-walk` on the hamlet with its palisade. Pastir Luka, with his lantern, walks from
the tent to the path's gate at 21:25:10, is under its lintel at 21:25:14 (the beam over him), out of it and in front of
the gate at 21:25:18, down the path; at 21:45:28 he stands in front of the gate coming home and at 21:45:32 goes in
under the beam (companion/qa-shots/<time>/…-palisade-walk-path-…). Before the fix such a walk went straight to the
meadow, through the stakes beside the gate (the unit test's `strip-before-into-the-woods`, on Jan's hamlet). By day a
pedlar at the road stood in the main gate's opening, between its posts, never among the stakes.

## Tests

The whole Android unit suite: 3,413 tests, none failing (2 skipped, as before); the game's 1,461 with it.

`PalisadeWalksTest` (11): no walk through the stakes, a walk in or out through a gate, a walk inside never leaving the
ring, over every age from the camp and every landscape (two seeds each, the coast among them), Jan's hamlet and Teo's
coast, on a phone and on Teo's tablet both ways round (92,000 walks, 15,800 of them in or out, 14,600 into the woods);
into the woods only by the path's gate; without a palisade every walk as before; the water gates and a shore no way
through, every road, path and river gate one; the breach no way through; with the wolves at the path's gate, a walk in
from the meadow round by another gate and never through them; everyone on the map second by second through a day and
while the wolves come (Jan's hamlet, Teo's hamlet and town, on the phone and the tablet), no step through the stakes; the
order under the lintel; the cost.

## What's left

- During an age-up's moment the ring drawn is the age before's until the forest has drawn back, while the people already
  walk by the new age's ring (a few seconds).
- The merchant's cart is drawn as one thing: while it drives through the main gate (seven seconds) its horse can be drawn
  over the gate's nearer post or the post over the cart for a moment.
- A walk into the woods goes down the path to its nearest point and then straight through the trees to the place (the
  pond, the high seat), as before from the meadow's way in.
