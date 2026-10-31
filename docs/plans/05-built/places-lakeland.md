# Built: the Lake District village gets its places (places-lakeland, wave 2)

(In progress: the looks below are the spec the scenes are written against; the rest of this note comes with the work.)


What each art draws in the Lake District village (culture `lakeland`), set in `CultureLooks` (game/scene/CultureLook.kt)
and drawn by the painters. **Nothing moves**: every slot, person spot, stage spot and hiding place is where the default
(Primorska) picture has it; only what a thing looks like changes where listed. A thing not listed here looks as in the
default picture: read its colour and place in the painter's class comment (game/render/scene/<Art>Painter.kt) and in the
Slovene scene's I spy clues (companion/ispy/scenes/<slovene-id>.json), whose "where"s hold for Lakeland too.

Shared by every Lakeland picture: grey stone cottages and whitewashed ones under blue-grey **slate**, white window frames,
no shutters; the church's square **tower with battlements**; **dry-stone walls** for fences; the **fells** (rounded green
hills with bracken, grey crags and walls climbing them) in every view out; the **oak** for the linden on the green;
**St George's cross** (red on white) for the flags.

Slovene scene of each art (for its default things): kitchen kuhinja, livingroom v-hisi, cellar v-kleti, attic
v-podstresju, workshop v-delavnici, tent v-sotoru, smithy v-kovacnici, church v-cerkvi, field na-njivi, square na-vasi,
market na-trznici, school v-soli, apiary pri-cebelnjaku, watchtower na-stolpu, palisade pri-vratih.

## The rooms (kitchen, livingroom, cellar, attic, workshop): shared

- **Window view**: the fells (green slopes, a dry-stone wall across them, a grey crag on top, sky), not the spruce.
- **Floor**: beaten earth at level 1 as before; from level 2 **slate flags**, blue-grey irregular flagstones (not
  terracotta tiles / planks). The rag rug stays (kitchen L4, livingroom L2).
- **Walls**: whitewash (lime) as before; the painted dado from level 2 is a soft **duck-egg blue-green**; at level 4,
  instead of the stencilled flower border, an **oak plate rail** under the top with blue-and-white plates standing on it.
- **Outside**: the bench by the wall (L4), geraniums in pots (L5), the hen, the cat: as before.

### kitchen (Granny Maggie's farmhouse kitchen; from hut, house)
- `stove` (L2+): the **range**: a black cast-iron kitchen range in the back-right corner where the tiled stove stood:
  black iron, its fire glowing behind bars in the middle, the oven door beside it with a brass knob, a brass rail along
  its front, a black kettle on its hob, its pipe up the wall. Not green tiles.
- `hearth` (L1 only): the open hearth in the corner, as before (an old down-hearth).
- `cupboard` (L3+): the **oak dresser**: brown oak, plain panels, its rack of shelves holding **blue-and-white plates**
  (not the blue painted dresser with tulips).
- `corner` (L3+): no holy corner. A small **framed sampler** (cream linen, a little red house and green letters
  stitched on it) high in the front corner, over a little shelf with a **brass candlestick** on it.
- `chair`: **ladder-back chairs**, three cross slats in the back (no heart cut-out).
- Everything else as the default kitchen (the table with its checked cloth, bread, plate, bowl, cup, glass, cutlery,
  salt, eggs in a basket, sack of potatoes, milk and water jugs, the clock, the lamp, the curtains, the shelf to L2, the
  straw bed at L1, the bench under the window).

### livingroom (Old Wilf's room by the fire; household wilf)
- `stove`: the **fireplace** where the big tiled stove stood, in the back corner: a hearth of grey slate stones low in
  the corner with the fire burning in a black iron fire basket; at level 1 the corner over it black with smoke; from
  level 2 a whitewashed **chimney breast** over it with an **oak mantel beam**, and on the mantel shelf two brass
  candlesticks and a white china dog. The `stove` effect builds the fire up and lights the bench.
- `bench`: the benches along the fire, as the zapeček ran: **oak settles** (high backs) round the hearth. Bess the dog
  (the `dog`) curls up on it.
- `chest`: a plain **oak chest** (kist) with a carved front band (not painted with flowers).
- `corner` (L3+): the **framed sampler** with the brass candlestick on its shelf (as the kitchen's).
- Everything else as the default living room (table, chair, curtains, rug, lamp, clock, photo, glasses, book, window,
  door).

### cellar (Brewer Sam's cellar; household sam, Arthur lodges)
- **Walls of slate stone**: rough grey-blue stone in thin courses, no whitewash.
- `grapes` (L2+): **damsons** in the crate: small dark purple-blue plums with a pale bloom (not grapes).
- `wine` (L2+): the demijohn in its wicker holds **dark red-purple damson wine**.
- `bottle` (L2+): **brown glass** bottles (ale) on the shelf.
- `barrel`, `press` (an apple / fruit press: the same screw press), `jug`, `glass`, `candle`, `crate`, `ham` (a cured
  Cumberland ham on the wall, L3), `cheese` (a round cheese, L3), `stairs`, `door`, `window` (barred slit; fells
  through it): as the default cellar.

### attic (Mrs Birkett and Harry's attic bedroom)
- `roof`: at level 1 grey-blue **slates** showing between the battens (not red tiles); from level 2 lined with boards as
  before.
- The wardrobe (L2) and the chest of **oak**, brown (not painted blue).
- Window view: the fells. Everything else as the default attic (bed, pillow, patchwork quilt, cradle, rug, lamp, ball,
  mirror, teddy bear, stairs).

### workshop (Mr Tyson and Alfie's workshop)
- **Walls of slate stone** (as the cellar's).
- High on the left wall: **two shepherd's crooks and a walking stick with a horn handle** (scenery, not a slot).
- By the broom: a **basket of wooden bobbins** (from the Lakeland bobbin mills; scenery, not a slot).
- Everything else as the default workshop (workbench, hammer, nails, boards, axe, broom, saw, tools, ladder, rake,
  wheelbarrow, the wooden horse, lantern, window, door).

## tent (the camp's tent)
- The pennant on the front post: **St George's cross**, red on white.
- Along the meadow's back edge: a **dry-stone wall** with **bracken** in front of it (green in summer, russet from
  October, brown in winter) and **foxgloves** in June and July (not vines).
- `boots`: **green wellington boots** by the camp bed.
- Everything else as the default tent.

## smithy (Mr Dixon's smithy)
- **Walls of slate stone** (grey-blue, thin courses). The yard (level 4) of **slate flags**, not cobbles.
- Everything else as the default smithy.

## church (the dale church)
- **Floor of slate flags** (blue-grey).
- `window`: clear **leaded glass in diamond panes** (quarries), a faint green tint; level 2 adds the second window (no
  coloured glass anywhere; the light on the floor is pale, not coloured).
- From level 3 an **open timber roof** of dark oak instead of the starry vault.
- `painting`: the painting over the altar is **the Good Shepherd**: a shepherd in a red cloak carrying a white lamb on his
  shoulders, a green fell and blue sky behind; from level 4 in a carved **oak frame**, from level 5 a gilt text board over
  it.
- The dado a grey slate plinth; at level 4 white **memorial tablets** on the walls instead of consecration crosses.
- `bell` (in the gable over the door): a **bellcote** of grey slate stone with a slate cap.
- `rope`: its grip, the sally, striped **red, white and blue**.
- The organ loft's front plain **oak** (no painted tulips).
- At level 4, where the Virgin on her pedestal stood: a **brass eagle lectern** holding a Bible.
- Outside at level 5: a dark **yew** for the cypress.
- Everything else as the default church (altar, candles, cross, flowers, pews, organ, font, door).

## field (Mr Tyson's field)
- `kozolec`: the **stone field barn**: a small barn of grey stone under slate, by KOZOLEC's level (1: one bay, open at the
  front with the hay heaped in it; 2: two bays, walls up; 3: the big arched cart door; 4: the slate roof whole, an owl
  hole in the gable; 5: hay to the loft door). `hay`: the hay in the barn (its door and its loft).
- `fence`: **dry-stone walls**, grey, for the fences (the near one at 1, the middle one at 2, the far one at 4).
- Behind: the **fells**; the village on the near hill in grey stone under slate with the battlemented church tower.
- Everything else as the default field (wheat, corn: maize, potatoes, cabbage, scythe, rake, basket, cart, horse,
  scarecrow, path).

## square (the village green; from the oak, the well)
- `linden`: the **oak**: a broad oak with lobed dark green leaves, brown-gold in autumn, bare in winter, growing with
  LIPA's level (a staked sapling at 1 … the old oak at 5). The cat climbs it.
- `well`: the **village pump** by WELL's level: 1 a low stone trough fed by a spring, a bucket by it; 2 the trough of
  dressed stone; 3 the cast-iron **pump**, painted dark green, its handle and spout over the trough; 4 a little slate
  roof over it on two posts; 5 the lamp post beside it. The word is the pump.
- `tower`: the church with its **square battlemented tower** of grey stone (the bell in its louvred opening).
- `house`: the cottages on the right of grey stone and whitewash under slate, white window frames, no shutters, a slate
  porch; the bicycle against the nearest.
- `fence`: the garden wall on the left: a low **dry-stone wall**.
- The ground: the **green**: grass with a gravel path, not cobbles.
- `flowers`: the flower bed: **daffodils** in March and April, otherwise as the default bed.
- Fells behind. Everything else as the default square (bench, ball, dog: Bess, cat, bicycle, sun).

## market (market day)
- The houses round it grey stone and whitewash under slate; instead of the white church tower and spire a **square
  battlemented clock tower** of grey stone.
- Fells on the hill behind. The lane's cobbles (level 4) grey stone setts.
- Awnings: the village's own stall **green and cream**; the farther ones red and white, blue and white, yellow and green.
- Goods on the village's stall: a round **cheese** with a cloth band, **cottage loaves**, a tray of eggs, jars of dark
  amber **heather honey**; at the stall's back strings of **onions**; the crate by season (rhubarb in spring,
  strawberries in June, tomatoes in summer, apples and a pumpkin in autumn, leeks in winter).
- `wine`: on Sam's cart a barrel of **ale** and a crate of **brown bottles** with cream labels (ale, not wine).
- Everything else as the default market (scale, weights, eggs, bread, basket, crate, hen in its coop, cart, pigeons,
  bell, apples, vegetables).

## apiary (Beekeeper Arthur's bee garden)
- `beehouse`: not a painted bee house: a **dry-stone wall with bee boles**, arched recesses in it, a **straw skep** in each,
  a slate coping on top; in front of it **white WBC hives** (tiered white boxes with a little pitched roof) in a row on
  stands, by BEEHIVE's level (1: three hives and two boles; 2: six; 3: a second row; 4: the slate coping and every bole with
  its skep; 5: the path, the bees' water, the lantern).
- `hive` (the hive apart): a **white WBC hive**.
- `panel`: Arthur's sign by the path, a green board with **HONEY** in white.
- `honey`: dark amber **heather honey** in the jars.
- World Bee Day's bunting red, white and blue.
- Everything else as the default apiary (bee, comb, flowers, tree: the lime (linden), smoker, extractor, wax, bench).

## watchtower (the view from the watchtower)
- The village below in grey stone and whitewash under slate, the **battlemented church tower** among the roofs, smoke from
  the chimneys; the fields with **dry-stone walls**; the `mountains` are **fells** with crags.
- `flag`: **St George's cross** (below level 4 a white pennant with a red cross at the hoist).
- The tower: from level 2 the underside of a **slate** roof; from level 3 pillars of grey **stone**; from level 4 a low
  stone **parapet with battlements** under the wooden railing (the `railing` stays the wooden rail on top).
- Everything else as the default view (torch, horn, spyglass, crow, roofs, palisade, gate, road, forest, river, valley,
  horizon).

## palisade (at the gate)
- The land beyond the gate: the **fells** with **dry-stone walls** climbing them, bracken, a grey crag and a cairn.
- Everything else as the default gate (stakes, gate, bar, torches, flag, roof, ladder, bell, road, forest, meadow,
  bench, trough).
