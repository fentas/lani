# Built: the village's people react to its events

Jan, 2026-10-20: "There are wolves :) I see them. The rest apparently not: they do what they always do, including just
walking through them. Maybe we let people and the surroundings react to what is happening." And: "maybe include what
they want to talk to you about. It would not make much sense if France takes cover but then talks about the field."

The rules as built are in companion/GAME.md, "People react". This note says how it is built, how to use it and what is
left.

## What

- **On the map** (`game/render/TownReactions.kt`, after the day's placement in `TownPeople.of`):
  - wolves or a bear: two or three defenders (the event's leader, the smith; the shepherd; the hunter) face them from
    within the clearing, in the palisade's gate when the wolves come up the path to it, seen (not behind a stake, the
    gate's beam, a building or the bear), a hammer or a stick raised now and then, a torch at night; the lookout on the
    watchtower waving; the children, the old and the innkeeper by the fire; the others home; a few unnamed by the fire;
    the hens bunched up by a house;
  - a storm: everyone home, a hand on the hat; a dash between two doors every 6 minutes; the washing taken in at the
    half hour;
  - the merchant: the cart comes up the road once, when he comes; the innkeeper and two others at it by daylight, two
    others every quarter of an hour;
  - a festival: dancing round the fire, the winemaker on the harmonika with notes going up;
  - after a won fight: everyone up cheering round the fire with the defenders, then back to their day; after a lost
    event that damaged a building: the smith patching it up with planks by daylight, that day and the next;
  - every change of place is a walk from where they were (children running), at most `Routine.WALK_S`;
  - nobody within `Threats.KEEP` (2.5 cells) of an animal, the defenders within `Threats.GUARD` (1.3); the animals' ground
    is a `Walkways` block, so no way passes it.
- **What they say** (culture packs' `reactions.json`, `lani.reactions/v0`, new, optional; four packs, every language,
  machine-written): calls over heads on the map (`TownShouts`, one at a time), the line on a villager's card, in "Pogovori
  se" and in a scene's greeting, the card's call to the event's run ("🐺 Branimo vas!").
- **The everyday talks wait** while wolves, a bear or a storm are about (`Reactions.quiet`): `Happenings.on` (the
  happenings, their dialog variants, the stories, their teaser), `Keepers.here` (wolves and the bear only),
  `TownMarkers.of` (only the event and the requests), `TownMarkers.arrivals`, `VillagerController.meet` and
  `waitsToMeet`, `Surprises.stranger` (the day's surprise itself stays playable from the HUD and Home: the simulation's
  pacing wants something new every session), `SceneOffers.lesson`, `ISpyController.offer`; the villager card and
  page show the line and the event's run instead of the talk; the scenes show who the reaction has there
  (`Whereabouts.inScene`). Deferred, never lost: nothing is recorded, so the dialog that was due is due after.

## How

- `game/villagers/Reactions.kt` (pure): `Stir` (the event on since when, or just over: won, lost, over), `Stance` (who
  does what), `stances`, `placeOf` (for the scenes), `line`, `says`, `call`, `shouts`, `repair`.
- `GameState.ended` (`EventEnd`: kind, won, at, damaged, expired), set by `Events.outcome`: what "afterwards" reads. An
  older app ignores it, the bridge keeps it as it is.
- `game/render/Threats.kt`: where the wolves and the bear stand (moved out of the renderer, which draws them there) and
  the cart; the bear now comes out of the forest's edge on the field's side, not among the houses.
- `game/render/TownReactions.kt`: the goals per stance and phase and the walks between them; `TownPeople.of` takes the
  wall clock (`Frame.wall`; 0, a test's or a tool's: the event on since long). New activities: `GUARD`, `CHEER`, `HAT`,
  `REPAIR`, `MUSIC`, drawn at the three details (`Effects`); the watchtower's parapet drawn again over its lookout.
- The defenders' spots are worked out once per village and animals (keyed by its `Walkways.Ways`).

## How to use it

- QA's hooks (debug build): `event:wolves`, `event:bear/3`, `event:storm`, `event:merchant`, `event:festival`;
  `event:won`, `event:lost`, `event:`; `show:fire`, `show:cart`, `show:threat`.
- QA's step `reactions` (`companion/bin/qa --steps reactions`; `LANI_QA_REACT_SHOTS` for its screenshots). Run it on a
  synthetic learner (`LANI_DEV_DATA`), never on real data.
- New lines: a pack's `reactions.json`, checked by `Cultures.checkReactions` (the pack's language, a shout up to three
  words, a call up to four).

## Tests

- `game/render/ReactionsTest` (13): who does what for each event and after; nobody and no way within reach of the
  animals, second by second through the first 50 s, in Jan's hamlet and a town, at three hours; the walks and the
  children running; the storm; the merchant's few by the quarter; the feast; the cheer; the repair; the lines; the four
  packs' files; the frame budget with wolves on (20 ms a frame at 360×800 against the test's 50); snapshots 70–77 in
  `build/village-snapshots`.
- `game/villagers/ReactionTalksTest` (6): no happening, later or teaser during wolves, a bear or a storm, the same ones
  with the same dialog after; the merchant and a feast keep them; the map's bubbles; the charcoal burner; the greetings;
  the scenes; the lesson.

## What's left

- The lines are machine-written, not reviewed by a native speaker (the packs' `review` says so).
- No sheep, no dog on the map: the hens bunch up instead; a barking dog would need a dog in the village first.
- A visit's guest card (`VisitScreen.GuestCard`) greets as before (it has no state of the host's to read).
- The free talk with the tutor isn't told about the event: during wolves, a bear or a storm the card hides it; at the
  merchant's and a feast day it runs as before.
- The requests stay up during a threat (the learner's work); their givers shelter like everyone else.
