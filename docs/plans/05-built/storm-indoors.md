# Built: the storm rains, and who goes home is at home

Jan, 2026-10-25: "We had a storm: nobody outside, good. I went into a house: nobody there (where are they?). And it was
not raining."

The rules as built are in companion/GAME.md ("People react": the storm's rain, "At home in an event"; "Background
sounds") and companion/SCENES.md ("The rain indoors", "Who is at home in an event"). This note says why it was so, how it
is built, how to use it and what is left.

## Why it was so

- **Nobody at home.** While wolves, a bear or a storm keep the everyday talks waiting, `Whereabouts.inScene` placed people
  only in scenes that aren't a house's room (`!Homes.isRoom(scene)`), and only who was out by their day. Whoever the
  storm sent home, or who was in the house already, was in no scene, though the map had them walk in at their door.
- **No rain.** The map's storm rain was 150 streaks of 4 px over the whole canvas, nearly invisible at any zoom. The
  scenes never saw the storm: their sky was only what a dialog cued (`DaySky.of`), so a house's window showed a clear day
  and no rain was heard in it.

## What

- **The storm's weather** (`Sky.STORM`, `DaySky.over`): over the village and every scene while the event is on; the scenes
  ease back to the day's sky after (nothing of the storm is kept). The scenes' cues (`rememberSceneCues`) and their sound
  (`AmbientSound` with the cues' sky) start from it.
- **The map** (`Effects.rain`, `VillageRenderer.wetted`, `Env`): rain sized in nominal px and drawn at the lens's detail,
  about one streak per 55 nominal px², slanting, fading toward its tail; drops splashing and ringing out on the ground
  (only on the background, not on roofs or people); the paths, the road and the cobbles darker with puddles that catch the
  grey sky (`PUDDLES`), the grass a little darker; a darker, colder light (ambient × 0.6, 0.64, 0.76). A dialog's shower
  kept for the day wets the ground as much as it rains.
- **Lightning** (`Lightning`): one soft pulse a flash (`glow`: 0.06 s rise, 0.5 s ease out, no flicker, no echo), the bolt
  in its first 0.18 s; the storm every 10 s, up to 5 s late (5 to 15 s apart), a scene every 9 s up to 4 s late, a kept sky
  every 13 s up to 5 s late. The village whitened at most 0.22 (it was 0.33 at once every 5.3 s). None with the system's
  animations off (`rememberReducedMotion`: `Frame.calm` on the map, the scene's sky without lightning in `SceneView`); the
  thunder still comes, timed from the same schedule.
- **Indoors**: every window (`Weather.glass`, used by `RoomPainter.view`, the school's and the smithy's windows) greys under
  the clouds (no sun or moon behind a grey sky), the rain streaking past outside, beads on the pane, a trickle running down
  now and then, a flash. The stove glows warmer on a rainy day where the house's hearth is lit; where it's out
  (`Hearths.cold`, `SceneWorld.cold`: no wood, blown out by a lost storm, the forge without charcoal, a damaged building) the
  kitchen's grate is dark, its open hearth's logs cold on grey ash, the living room's stove unwarmed, the forge banked,
  unless a dialog fires it up.
- **The sound**: `rain-in`, a new loop synthesized by `companion/android/tools/ambient_sounds.py` (CC0, no recordings;
  `assets/ambient/CREDITS.md`): the rain through the walls, dull taps on the roof, a drop on the pane now and then, the
  water off the eaves. The rooms, the cellar and the church play it instead of the rain, at their share of the weather × 1.5
  (`Soundscape.INDOOR_RAIN`, `Spot.indoor`). The thunder indoors stays about half as loud.
- **Who is where** (`Whereabouts.during`, `Whereabouts.placeOf`, `Whereabouts.inScene`'s reaction path): as the map has
  them. Who is out goes where their stance sends them; the defenders and the lookout get up for it; who was in the house
  stays in; who is asleep stays in bed. A room is the house it is seen in (the house tapped, else the house it is dealt; the
  hut's kitchen in the hut), so the house the map's door leads into (`Sleep.homes`) is the house whose room shows them. The
  scene's own people at their spots, then the household, the cast and the village's own people at the art's free person
  spots; by the fire two more spots, beside the stump and in front of the woodpile. `Sleep.sleepers` leaves the beds of the
  defenders and the lookout empty while wolves or a bear are about.
- **In the scene** (`SceneScreen`): a small quiet marker with the event's emoji over each (`SpeechMarker(small = true)`,
  dark and see-through: nothing waits for the learner; QA's first run with the gold markers had five over the fire's
  gathering, hiding it); "Danes tukaj · Here today" lists them under the event's name, each with what they say and its
  meaning (`ReactedHere`); a tap on them, their marker or their row greets them; someone the scene doesn't list (Lovec
  Jože in Janez's living room) is greeted too (`personOf`).
- **Lines for inside**: `reactions.json` `home` (`lani.reactions/v0`, optional, checked as the other lines), for the wolves,
  the bear and the storm, two or three lines in each of the four packs and every language: "Poslušaj, kako dežuje.", "Doma
  smo na suhem.", "Kako grmi! Dobro, da smo doma.", "Vrata so zaprta. Tukaj smo varni.", "Volkovi so zunaj. Ostani notri.",
  "Medved je zunaj. Ostani notri.". Machine-written (Claude), not reviewed by a native speaker. `Reactions.line`/`says` and
  `VillagerLogic.reaction` take `home`: whoever is met at home in their room (`homeHere`) says one of them.

## How to use it

- In the app: the storm event comes with the day's tick (it needs a phone that can speak). Go into a house: its people are
  there, the rain runs down the window, "Danes tukaj" says what each says.
- QA: `LANI_DEV_DATA=<a synthetic learner with the whole cast moved in> LANI_QA_STORM_SHOTS=<dir> flock /tmp/lani-emulator.lock
  companion/bin/qa --steps storm` (the map in the rain, a flash caught when one comes, Janez's living room, its window, a
  greeting, "Danes tukaj", the kitchen, the field, the wolves by the fire).
- Tests: `StormIndoorsTest`, `StormRenderTest` (snapshots in `build/scene-snapshots/storm`), and the updated
  `LightningTest`, `ThunderTest`, `SoundscapeTest`, `ReactionTalksTest`.

## Tested

- Unit tests: `StormIndoorsTest` (7), `StormRenderTest` (6), the updated `LightningTest`, `ThunderTest`, `SoundscapeTest`,
  `VillageRendererTest`, `ReactionTalksTest`; the whole Android suite (2,916 tests, all passing). The frame budget with the rain: the
  village 25 ms a frame at the fitted portrait canvas (360 × 800) and 22 ms at detail 3 (budget 50), the kitchen in the storm
  with its three 6 ms (360 × 240) and 19 ms at detail 3 (budgets 20 and 60).
- Emulator QA (`--steps storm`, a synthetic learner "Ana" with the whole Primorska cast moved in): the map in the rain, a
  flash caught (the sky 0.799 against 0.753), the living room with `janez` and `joze`, Janez's greeting "Doma smo na
  suhem. · At home we're dry.", "Danes tukaj" with "⛈️ Nevihta · Storm" (Janez: "Doma smo na suhem.", Jože: "Poslušaj, kako
  dežuje."), the kitchen with `babica`, `ancka`, `zala`, the field in the rain, the wolves' fire with `babica`, `janez`,
  `otroci` (Nejc), `tine`, `zala`; no crash.

## Weather the game has, besides the storm

- **A dialog's sky** (SCENES.md "Sky cues"): rain, snow, fog, wind, gloom, lightning, kept over its scene and the village
  for the day (`DaySky`). Those are the only rainy days; the map's new rain and wet ground show for them too, as much as it
  rains. There is no weather of its own (no forecast, no rainy season).
- **Winter**: snow on the ground, the roofs and the trees from December to February, snowflakes over the map (`Effects.leaves`
  in winter) and in the scenes' windows; not a weather event. The storm in winter rains as in any month.
- **The real night sky** (`SkyNow`): the moon and the stars; a storm hides them.

## What's left

- Outside an event, who is at home awake in the dark evening (`Where.INDOORS`) is still in no room's scene by their day
  (only who works at home by day, and who sleeps): the event path has them, the everyday path doesn't yet.
- The map's dash between doors and the washing taken in at the half hour aren't mirrored in the rooms: whoever is out for
  that minute or two is still drawn at home.
- A room holds as many as the art's spots (three, by the fire five): a bigger household or the village's own people beyond
  them are at home unseen, as at night.
- The bridge doesn't read `reactions.json`; the tutor can't publish home lines.
- The lines for inside are not reviewed by a native speaker.
