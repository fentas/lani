package si.lanisce.lani.game.scene

/**
 * What the scene painters can draw: for every art, its object slots (each a tappable thing) and the
 * spots where people stand; and the people sprites. Content (lani.scene/v0) may only use these; the
 * bridge validator keeps a copy of this registry (companion/bridge/src/scenes.ts) and checks it
 * against this file.
 */
object SceneArt {
    /**
     * Which arts and slots this app draws: 1 the first seventeen arts, 2 the rooms of the houses and the kitchen's level
     * slots, 3 the mill inside, 4 the village's places (the fire station, the bocce court, the playground, the wayside
     * chapel, the palisade's gate, the lookout tower), 5 the finished projects as things of the pictures they stand in (the
     * square's maypole, fountain and clock, the stream's cart bridge, the field's toplar, the apiary's bee meadow), the
     * square's standing spots by them, its climb and its strike, and a count of the hour. The app asks for its scenes with
     * it (GET /scenes?arts=5); the bridge gives an app that asks for less, or doesn't say, only what its arts drew, so an
     * older app never gets a place it can't paint (bridge/src/scenes.ts, `forApp`).
     */
    const val VERSION = 5

    /** art id → object slots, back to front roughly. */
    val objects: Map<String, List<String>> = mapOf(
        // Ob ognju: a camp at night or day, the fire in the middle.
        "campfire" to listOf("fire", "logs", "kettle", "stones", "smoke", "sparks", "moon", "stars", "tent", "axe", "bucket", "stump"),
        // V kuhinji: a farmhouse kitchen, the stove on the left, the table in front; it grows with its building's level
        // (SceneFixtures): the open hearth, the straw bed and the shelf at first, the stove, the table and the curtains,
        // then the clock, the dresser (cupboard) and the holy corner.
        "kitchen" to listOf(
            "stove", "pot", "bowl", "table", "chair", "bread", "plate", "glass", "cup", "spoon", "fork", "knife",
            "window", "door", "cupboard", "clock", "milk", "eggs", "water", "salt", "potatoes",
            "hearth", "bench", "bed", "shelf", "curtains", "corner",
        ),
        // V gozdu: a clearing in a beech and spruce forest with a stream, the hunter's high seat at its edge; its wild
        // animals (hare … bat) are there only while they are out (Wildlife).
        "forest" to listOf(
            "tree", "spruce", "mushroom", "berries", "stream", "stone", "path", "bird", "deer", "squirrel", "owl", "leaves", "sheep",
            "highseat", "hare", "butterfly", "fox", "stag", "hedgehog", "boar", "badger", "dormouse", "bat", "flowers",
        ),
        // Na vasi: the village square under the linden tree; the finished projects on it (SceneFixtures): the maypole before
        // the church, the fountain in the middle, the clock in the tower.
        "square" to listOf(
            "linden", "bench", "ball", "well", "dog", "cat", "house", "tower", "bicycle", "flowers", "sun", "fence",
            "maypole", "fountain", "clock",
        ),
        // V šotoru: inside the tent, a lantern hanging from the ridge pole.
        "tent" to listOf("lantern", "sleepingbag", "blanket", "pillow", "backpack", "map", "boots", "coat", "hat", "book", "cup", "rope"),
        // Na njivi: a field with the kozolec (hayrack) at its edge; the double hayrack beyond the wheat once it is finished.
        "field" to listOf(
            "wheat", "corn", "potatoes", "cabbage", "kozolec", "hay", "scythe", "rake", "basket", "cart", "scarecrow", "fence", "path",
            "deer", "hare", "butterfly", "fox", "boar", "bat", "horse", "toplar",
        ),
        // V kovačnici: the smithy, the forge glowing.
        "smithy" to listOf("forge", "fire", "anvil", "hammer", "tongs", "bellows", "horseshoe", "nails", "bucket", "chain", "axe", "wheel", "window", "door"),
        // V šoli: a village classroom.
        "school" to listOf("blackboard", "chalk", "sponge", "desk", "chair", "book", "notebook", "pencil", "bag", "map", "globe", "clock", "window", "door"),
        // Na gričih: the village's vineyard, its rows running away to a white farmhouse, the rolling hills of Brda with the
        // church on a hilltop, the Alps behind, the sea far off (Primorska's, open once the vineyard project is finished).
        "hills" to listOf("vineyard", "vine", "grapes", "hill", "church", "belltower", "farmhouse", "cypress", "persimmon", "wall", "mountains", "sea", "road", "crate"),
        // Il mare: the lagoon at Grado with its casoni, the pier and the boat, the beach and the open sea (Friuli's).
        "sea" to listOf("sea", "lagoon", "casone", "boat", "posts", "gull", "wave", "beach", "pier", "shell", "fish", "net", "horizon", "crab"),
        // V gorah: a mountain pasture above a lake under Triglav, the peaks mirrored in it, the little church and its stone
        // bridge on the shore, a shepherd's hut, cows with bells, a dry-stone wall, a stream with a waterfall (Primorska's horizon).
        "alps" to listOf(
            "mountain", "peak", "lake", "church", "bridge", "pasture", "hut", "cow", "cowbell", "larch", "spruce",
            "waterfall", "stream", "stone", "wall", "chamois", "ibex", "path",
        ),
        // Ob potoku: the stream below the village, France's mill on its bank, stepping stones and a footbridge; the cart bridge
        // upstream once it is finished.
        "stream" to listOf(
            "stream", "stones", "bridge", "willow", "trout", "dragonfly", "cress", "mill", "laundry", "fox", "hedgehog", "butterfly", "bat", "sheep",
            "cartbridge",
        ),
        // Pri ribniku: the pond in the woods, water lilies, reeds and a jetty with a boat.
        "pond" to listOf("pond", "lilies", "reeds", "frog", "ducks", "jetty", "boat", "fish", "heron", "deer", "fox", "butterfly", "bat", "dragonfly"),
        // V cerkvi: the village church inside, the gilded altar at the far wall, pews, stained windows, the organ loft by the door.
        "church" to listOf("altar", "candles", "cross", "painting", "pews", "flowers", "window", "organ", "font", "bell", "rope", "door"),
        // Pri čebelnjaku: the bee house with its painted hive fronts, a straw hive, the linden, a table of honey by the extractor;
        // the bee meadow along the garden's front once it is finished.
        "apiary" to listOf("beehouse", "hive", "panel", "bee", "honey", "comb", "flowers", "tree", "smoker", "extractor", "wax", "bench", "meadow"),
        // Na tržnici: market day at the edge of the square, a lane of stalls under striped awnings, the scale on the counter.
        "market" to listOf(
            "stall", "scale", "weights", "eggs", "bread", "cheese", "honey", "apples", "vegetables", "basket",
            "crate", "hen", "cart", "wine", "pigeon", "bell",
        ),
        // Na stolpu: the view from the watchtower over the village's roofs and the palisade to the forest and the mountains.
        "watchtower" to listOf(
            "tower", "railing", "torch", "horn", "spyglass", "flag", "crow", "roofs", "palisade", "gate", "road",
            "forest", "river", "valley", "mountains", "horizon",
        ),
        // The rooms of the houses (Homes), each growing with its house's level (SceneFixtures). V hiši: the living room of a
        // farmhouse, the tiled stove in the corner with the bench round it, the table in the holy corner.
        "livingroom" to listOf(
            "stove", "bench", "table", "chest", "window", "door", "dog", "chair", "curtains", "rug", "lamp", "clock",
            "corner", "photo", "glasses", "book",
        ),
        // V delavnici: the workshop, the workbench under the window, tools on the wall, boards and the ladder by the left one.
        "workshop" to listOf(
            "workbench", "hammer", "nails", "boards", "axe", "broom", "window", "door", "saw", "tools", "ladder", "rake",
            "wheelbarrow", "horse", "lantern",
        ),
        // V kleti: the wine cellar, stone walls, the barrels on their rack along the left wall, the press, steps down from the door.
        "cellar" to listOf(
            "barrel", "press", "stairs", "door", "window", "candle", "jug", "glass", "crate", "shelf", "bottle", "grapes",
            "wine", "ham", "cheese",
        ),
        // V podstrešju: the attic bedroom under the roof, the gable on the left, the sloping ceiling on the right, the cradle.
        "attic" to listOf(
            "bed", "pillow", "blanket", "cradle", "chest", "roof", "window", "stairs", "wardrobe", "rug", "lamp", "ball",
            "mirror", "toy",
        ),
        // V mlinu: inside the village's water mill (open once the mill project grinds): the millstones in their vat on a timber
        // platform, the hopper with its grain over them, the flour running into a sack; under the platform the gear on the
        // wheel's shaft, out on the right the waterwheel in its race under the flume; sacks, the bench with the scale and the
        // miller's measure, the window, the door, the ladder to the grain loft, the cat after the mouse, the lantern, the sieve.
        "mill" to listOf(
            "millstone", "hopper", "grain", "flour", "sack", "shaft", "gear", "wheel", "flume", "scale", "measure",
            "bench", "window", "door", "ladder", "cat", "mouse", "lantern", "sieve",
        ),
        // The village's places (companion/SCENES.md "Arts", docs/plans/05-built/places-sl.md), each open once its project is
        // finished (the palisade from the palisade). V gasilskem domu: the fire station's hall, the old hand pump from Gorica
        // before the big open doors, the hose tower in the back corner with its bell, helmets and jackets on their pegs.
        "firestation" to listOf(
            "pump", "hose", "helmet", "axe", "ladder", "bucket", "bell", "door", "tower", "uniform", "flag", "picture",
            "bench", "horn", "lantern", "window",
        ),
        // Na balinišču: the bocce court under the linden by the inn, its sand between the boards, the balls round the jack,
        // the table under the vine pergola, the scoreboard.
        "bocce" to listOf(
            "court", "board", "sand", "jack", "ball", "linden", "bench", "table", "jug", "glass", "scoreboard", "rake",
            "meter", "pergola", "lantern",
        ),
        // Na igrišču: the playground by the school, the swing, the slide, the sandpit with its bucket and spade, the
        // climbing frame and the little house, the hopscotch in chalk, the chestnut tree, the fence and its gate.
        "playground" to listOf(
            "swing", "slide", "sandpit", "sand", "bucket", "spade", "climber", "house", "ball", "hopscotch", "bench",
            "tree", "fence", "gate", "school",
        ),
        // Pri kapelici: the wayside chapel by the road among the fields, its little bell in the turret, the altar with its
        // embroidered cloth behind the grille, the bench and the linden beside it, the hills beyond.
        "chapel" to listOf(
            "chapel", "roof", "bell", "altar", "cloth", "picture", "candle", "flowers", "grille", "bench", "linden",
            "road", "field", "cross", "hills",
        ),
        // Pri vratih: the palisade's gate from inside, the road out through it over the meadow to the forest; it grows with
        // the palisade's level (SceneFixtures): the torches, the gate's roof and its pennant, the walkway's ladder, the bell.
        "palisade" to listOf(
            "palisade", "stake", "gate", "bar", "torch", "flag", "roof", "ladder", "bell", "road", "forest", "meadow",
            "bench", "trough", "hills",
        ),
        // Na razglednem stolpu: the top of the lookout tower, its railing and roof, the board with the mountains' names,
        // the telescope, the visitors' book and its stamp; the view: the Alps with Triglav, the village below, the sea.
        "lookout" to listOf(
            "tower", "railing", "roof", "board", "telescope", "book", "stamp", "bench", "stairs", "flag", "mountains",
            "peak", "sea", "village", "forest", "valley",
        ),
    )

    /** art id → where people stand. */
    val personSlots: Map<String, List<String>> = mapOf(
        "campfire" to listOf("left", "right", "back"),
        "kitchen" to listOf("stove", "table", "door"),
        "forest" to listOf("path", "clearing", "highseat"),
        // the children's play spots, and the grown-ups' standing spots by the maypole, the fountain and the clock (under it,
        // on the square below the tower): there whatever is built, as the well's spot is
        "square" to listOf("bench", "well", "play1", "play2", "play3", "maypole", "fountain", "clock"),
        "tent" to listOf("left", "right", "door"),
        "field" to listOf("field", "kozolec", "path"),
        "smithy" to listOf("anvil", "forge", "door"),
        "school" to listOf("teacher", "desk1", "desk2", "door"),
        "hills" to listOf("vines", "wall", "road"),
        "sea" to listOf("beach", "pier", "boat"),
        "alps" to listOf("path", "wall", "stream"),
        "stream" to listOf("bank", "ford", "mill"),
        "pond" to listOf("shore", "reeds", "jetty"),
        "church" to listOf("altar", "aisle", "door"),
        "apiary" to listOf("hives", "table", "bench"),
        "market" to listOf("stall", "buyer", "cart"),
        "watchtower" to listOf("watch", "rail", "hatch"),
        "livingroom" to listOf("stove", "table", "door"),
        "workshop" to listOf("bench", "floor", "door"),
        "cellar" to listOf("barrel", "press", "stairs"),
        "attic" to listOf("bed", "chest", "stairs"),
        "mill" to listOf("stones", "sacks", "door"),
        "firestation" to listOf("pump", "hoses", "door"),
        "bocce" to listOf("throw", "jack", "table"),
        "playground" to listOf("swing", "sandpit", "bench"),
        "chapel" to listOf("chapel", "bench", "road"),
        "palisade" to listOf("gate", "bench", "road"),
        "lookout" to listOf("rail", "board", "stairs"),
    )

    /** People sprites. */
    val people: List<String> = listOf(
        "grandma", "grandpa", "child1", "child2", "child3", "shepherd", "smith", "teacher", "farmer",
        "beekeeper", "innkeeper", "winemaker", "aunt",
        // The strangers at the road on the day's surprise: the pedlar from Ribnica with his krošnja, a pilgrim.
        "pedlar", "pilgrim",
        // The village's own people (Residents): a woman, a man and a baby in arms or a basket.
        "woman", "man", "baby",
        // The hunter of the hunting club (an extra of the culture): a loden jacket, a hat with a feather, binoculars.
        "hunter",
        // The charcoal burner by his pile in the woods (a passer-by of the culture's, world.json `spots`): sooty, a black hat.
        "burner",
    )

    /**
     * art id → the village projects its painter draws as far as their steps have come ([SceneWorld.stage]): France's mill and
     * the cart bridge by the stream, the double hayrack beyond the field, the town clock, the maypole and the fountain on the
     * square, and the maypole's spruce in the forest (felled, then carried off to the village). A step of one of them played in a scene of that art shows its work done from its `done` line on
     * ([DialogLine.done], companion/SCENES.md "Project steps"); the bridge checks the same (project-steps.ts, DRAWN_PROJECTS).
     */
    val projects: Map<String, Set<String>> = mapOf(
        "stream" to setOf("mlin", "most"),
        "field" to setOf("toplar"),
        "square" to setOf("mestna_ura", "mlaj", "vodnjak_na_trgu"),
        "forest" to setOf("mlaj"),
    )

    /** art id → the effects its painter shows when a dialog's cue sets them (a line's `fx`, levels 0..1). */
    val effects: Map<String, List<String>> = mapOf(
        // the fire burning higher, sparks flying; the kettle steaming; a distant bell (a story's), its rings going out over
        // the dark woods from somewhere beyond them; the sheep's bells up on the pasture, out of sight, their clinks going out
        // over the trees on the right
        "campfire" to listOf(
            "flare", "steam", "bell", "sheepbells",
            // the fire burning down to its embers, the flames lower and lower (1: only the embers glowing, a thread of smoke)
            "embers",
            // Luka's flock out of the trees at the back on the right, milling along the camp's edge to be counted (to 0.5, as
            // many as the dialog counts), then down the path out of the clearing (1: gone), their bells clinking
            "flock",
        ),
        // the stove fired up, smoke from the chimney; steam off the cup and the bowl on the table
        "kitchen" to listOf("oven", "steam"),
        // a flock flying up out of the trees and away (the level is how far they have flown); deer stepping out of the trees
        // at the far edge, a stag and two hinds (how far out: 0 back in the trees); the fox trotting out along the edge
        // (how far it has come); the owl turning its head and hooting, its calls going out (above 0.9 now and then right
        // round, the back of her head to us); bats flitting over the
        // clearing (how many); the lost sheep's bell ringing, its clinks going out. Not set, the animals out are the day's
        // (Wildlife); the fox, the stag and the bats set by a cue that the day hasn't brought are scenery, not words to tap.
        "forest" to listOf(
            "birds", "herd", "fox", "hoot", "bats", "sheepbells",
            // the owl flying off her branch and away over the trees (how far she has flown; 1: gone, the branch bare)
            "owl-flies",
            // the tallest spruce at the clearing's far side felled (0 standing; to 0.2 a shiver; falling faster and faster,
            // crashing down by 0.85, dust and twigs flying; 1 lying by its stump; not set: as the maypole's project has it),
            // the oak beside it felled the same way; the lost sheep (0 out of sight behind the big spruce, nothing to tap; rising,
            // coming home over the brook; 1 at her place as by herself); a bear's tracks in the mud of the bank by the path
            // (how many, how clear); roe deer stepping out onto the far meadow at dusk (as many as the dialog counts, else
            // five: how many are out)
            "fell", "fell-oak", "sheep", "tracks", "does",
            // a basket of mushrooms on the grass at the clearing, as many in it as the dialog counts (else six), to count
            "basket",
        ),
        // the bell swinging in the tower, its ringing; the cat up the linden, coming down (0 stuck on its lowest limb,
        // mewing, 1 asleep on the bench again: how far down); Muri the dog coming back (0 away, 0.5 sniffing at the flower
        // bed, 1 back at his place) and wagging hard (how hard); the ball lost under the bench (0) and back in play (1); a
        // goal between the satchels and the ball kicked back into play (how far it has gone); a horse and cart coming up
        // the square with the fire pump, or with the white stone for the fountain (how far they have come: 1 stopped);
        // the village band playing under the linden (how many play); couples dancing a polka (how many); the maypole
        // raised (how far up: only where it lies ready or stands); the fountain's water (along the ditch, then from its
        // four pipes: how much); a lad climbing the maypole (how far up: 0 at its foot, 1 at the top under the wreath, the
        // prizes in Friuli's within reach; a cue back down slides him down); the clock striking the hour (the strokes ring one
        // by one as the level rises, as many as the dialog counts, else the hour the clock shows: [SceneFx.SLOW])
        "square" to listOf(
            "bell",
            "cat",
            "dog",
            "wag",
            "ball",
            "goal",
            "cart-pump",
            "cart-stone",
            "band",
            "dance",
            "maypole",
            "fountain",
            "climb",
            "strike",
        ),
        // the lantern: 0 dark, 1 lit (not set: lit from dusk)
        "tent" to listOf("lantern"),
        // crows coming to the scarecrow, no longer afraid (the level is how far they have come); the church bell from the
        // village, its rings coming over the field (not set: at the ave, round sunset); bats flitting over it (how many);
        // a hare in the grass by the fence (up to 0.4 the grass stirs, then it leaps out and bounds off: how far, 1 gone);
        // Sivko the horse coming back to the cart from beyond the picture's left edge (0 away, the shafts down; 1 back in
        // them; not set: there); the scarecrow's hat (0 none; then a big felt hat drops onto its head and, at 1, slips down
        // onto its nose; not set: its straw hat); a red scarf round its neck; a face drawn on its sack head in charcoal (0 a
        // blank sack, the crow off its arm below 0.5; rising, the eyes, the nose, a wide smile; not set: its stitched face)
        "field" to listOf("crows", "bell", "bats", "hare", "horse", "hat", "scarf", "face"),
        // the forge roaring as if the bellows blew; sparks off the anvil under the hammer; a shoe being forged on the anvil,
        // held in the tongs (1 the blank white-hot, bent into a U as it cools through red to grey toward 0; 0: taken); Marko's
        // cart in the yard with crates of bottles (0 a corner propped on a block, its wheel off; rising, the wheel goes on; 1 on
        // four wheels)
        "smithy" to listOf("forge", "sparks", "iron", "cart"),
        "school" to listOf(),
        // the church bell swinging and ringing; a tractor with crates driving along the road (the level is how far it
        // has come; 1 past us and gone); a flock of starlings rising off the vines and wheeling away (how far they have flown)
        "hills" to listOf("bell", "tractor", "starlings"),
        // the sea rising, breakers rolling in and bursting white on the beach; the gulls off the posts and away (how
        // far they have flown); the boat's sail hoisted (how far up), filling with the wind
        "sea" to listOf("waves", "gulls", "sail"),
        // the cows' bells ringing as they graze (how loud); the church bell by the lake swinging and ringing; a fish jumping
        // out of the lake again and again, rings on the water; a flock of alpine choughs off the crags and away (how far);
        // the alpenglow, the peaks flushing pink (how pink; not set: at sunrise and sunset)
        "alps" to listOf(
            "cowbells", "bell", "fish", "birds", "glow",
            // the ibex climbing onto his crag at the brow, against the lake (how far up; 1: on top), whether or not the day
            // has him out
            "ibex",
            // the near cow's bell lost in the grass by the stream (0, her neck bare, silent; rising, back on her neck; 1 as by
            // itself); the milk pail by the milker's feet (how full); as many cows on the pasture as the dialog counts, a calf
            // with them (those beyond the art's four coming up over the brow: how far; 1 all of them there)
            "bell-lost", "pail", "cows",
        ),
        // a trout leaping out of the current again and again, rings where it falls back; the mill wheel's water:
        // 0 the flume dry and the wheel still, 1 a full gush and the wheel turning fast (not set: an easy turn); bats over the
        // water (how many); the church bell from the village, its rings coming down to the water (not set: at the ave);
        // a lamb by the drinking ewe (0 its head up, turned from the water; 1 drinking); the fire brigade's pump on the bank,
        // its hose in the stream (0 still; then pumping, a jet over the water; at 1 the hose loose and spraying everyone);
        // a horse and cart over the new bridge (how far: 0 waiting at its near end, 1 gone into the woods; once its deck is laid);
        // the flock down to the water and home again (to 0.5 coming in from the right and drinking along the bank, as many as
        // the dialog counts, the ewe among them; from 0.5 going home toward us, 1 gone) and the one who always comes last (how
        // far she has trotted in, from 0.8 drinking; home with them, last). The wheel turns from the step that lets the water in.
        // The stream running high and brown after the night's rain, the sky clear (how high); the sacks of flour by the mill's
        // door to count (as many as the dialog counts, else five); the sheet the wind takes off the washing stone (to 0.6
        // flying over the water, caught, from 0.8 folded on the basket)
        "stream" to listOf("trout", "wheel", "bats", "bell", "lamb", "hose", "cart", "flock", "straggler", "full", "sacks", "sheet"),
        // wild ducks flying up out of the reeds and away (the level is how far they have flown); the frog jumping off its
        // lily pad into the water (1: in the water, just its eyes out); a fish jumping again and again, rings on the water;
        // bats hunting over the water (how many); Muri at the jetty's foot (0 curled up asleep, 0.5 sitting up wagging, 1
        // barking at the fish; not set: at home); tadpoles wriggling in the shallows by the reeds (how many); the heron striking
        // (it draws its neck back, its bill darts into the water; 1: standing with a fish across its bill); whoever stands at
        // the jetty's end fishing (a rod, its float: 0 still, rising bobbing and dipping, from 0.7 a carp on the line, 1 lifted
        // out); the day's catch in a bucket by them (as many fish as the dialog counts, their tails over the rim)
        "pond" to listOf("ducks", "frog", "fish", "bats", "dog", "tadpoles", "heron", "bite", "bucket"),
        // the candles on the altar: 1 all of them lit, 0 all out (not set: two burn); the bell in the gable swinging and
        // ringing, the rope going up and down; the organ playing, notes rising from its pipes
        "church" to listOf(
            "candles", "bell", "organ",
            // the door blown open (how far: 1 wide), the day or the night in it, the draught leaning the candles' flames
            "door",
        ),
        // a swarm rising off the hives and circling over the garden (how many, how far); the smoker puffing smoke; honey
        // running from the extractor's tap into a jar, the crank turning (how fast)
        "apiary" to listOf("swarm", "smoke", "honey"),
        // a busy market day, shoppers crowding the lane (how busy); the market bell on its post swinging and ringing; the
        // pigeons flying up off the cobbles and wheeling away (how far they have flown); Micka's hen out of her coop and over
        // the lane onto Marko's cart (1: up on it, pecking: a tap on her there is a tap on the cart; 0: in her coop)
        "market" to listOf("bustle", "bell", "pigeons", "hen"),
        // the watch's torch: 0 out, 1 lit (not set: lit from dusk); the horn blown, its call going out over the valley (how
        // loud); a flock rising off the forest and away (how far); wolves' eyes in the dark forest (how many), grey shapes by
        // day; the pedlar under his krošnja walking down the road from the forest to the gate (how far: 1 in at the gate)
        "watchtower" to listOf("torch", "horn", "birds", "wolves", "pedlar"),
        // the stove fired up, warm light on the bench (how warm); Muri the dog awake on the bench, sitting up (0: asleep);
        // Muri hidden behind the door (it stands open), his tail wagging past its leaf (0: back on his bench)
        "livingroom" to listOf("stove", "dog", "dog-hides"),
        // sawdust flying off the workbench as a board is sawn (how much)
        "workshop" to listOf(
            "dust",
            // a nail hammered into a board on the bench, the hammer rising and coming down, a knock each time (how hard)
            "hammer",
            // Sivko, the wooden horse on the bench, mended (0 on his side, a leg off; from 0.3 glued; 1 standing whole, as ever)
            "toy",
        ),
        // wine running from the barrel's tap into the jug; the candle: 0 out, 1 lit (not set: lit, the cellar is dark)
        "cellar" to listOf("tap", "candle"),
        // the lamp: 0 dark, 1 lit (not set: lit from dusk); the cradle rocking (how far)
        "attic" to listOf(
            "lamp", "cradle",
            // the gable window blown open by the burja, its casement banging in the wind (how far open; 0: shut)
            "window",
        ),
        // the stones grinding (0 stopped, the water off the wheel; 1 grinding hard; not set: an easy turn); grain poured into
        // the hopper (how full: 0 empty, 1 full; a sack tipped over it pouring while it fills); the flour sack under the spout
        // filling (how full); a puff of flour dust (how thick); the cat pouncing at the mouse (0 crouched, ready; 1 landed at
        // its hole, the mouse gone in); the lantern (0 dark, 1 lit; not set: lit from dusk); a sack weighed on the scale (the
        // beam tipping and settling: how far); the miller's toll, the measure filling with grain (how full)
        "mill" to listOf("grind", "pour", "sack", "dust", "cat", "lantern", "weigh", "toll"),
        // the bell in the hose tower ringing the alarm (how hard), its rope going up and down; the hand pump worked, its two
        // handles see-sawing and a jet out of the nozzle through the doors (how hard); the big doors (0 shut, 1 open; not
        // set: open by day, shut at night); the lantern (0 dark, 1 lit; not set: lit from dusk); the signal horn blown, its
        // call going out (how loud)
        "firestation" to listOf("bell", "pump", "doors", "lantern", "horn"),
        // a ball thrown from the near end, arcing and rolling up to the jack (how far: 1 at rest by it); a ball thrown full
        // at another and knocking it away with a clack (how far); the measuring stick laid from the jack to the nearest
        // balls (how far out); wine poured from the jug into the glass (how full); the chalk marks on the scoreboard (as
        // many as the dialog counts, else the day's score); the lantern on its post (0 dark, 1 lit; not set: lit from dusk)
        "bocce" to listOf("throw", "hit", "measure", "pour", "score", "lantern"),
        // the swing swinging (how high); the ball kicked over the lawn and back (how far: 0 lost under the slide); a kite up
        // over the playground on its string (how high); soap bubbles drifting up from the bench (how many); a sand castle
        // built in the sandpit (how far: 1 with its flag)
        "playground" to listOf("swing", "ball", "kite", "bubbles", "castle"),
        // the little bell swinging in its turret and ringing (how hard); the candles on the altar (0 out, 1 all lit; not set:
        // one burns); fresh flowers in the vases (0 wilted, 1 fresh; not set: fresh); a traveller walking up the road past
        // the chapel and on (how far: 1 gone over the rise)
        "chapel" to listOf("bell", "candles", "flowers", "traveller"),
        // the gate's wings (0 shut and barred, 1 wide open; not set: open by day, shut at night); the torches on its posts (0
        // out, 1 lit; not set: lit from dusk, from the second level); wolves' eyes in the dark forest at night, grey shapes
        // slinking along its edge by day (how many); the flock out through the gate to the pasture and home again (to 0.5
        // going out, as many as the dialog counts; from 0.5 coming home, 1 all in); the pedlar coming up the road to the gate
        // (how far: 1 in at the gate); the bell at the gate ringing (how hard, from the fifth level)
        "palisade" to listOf("gate", "torch", "wolves", "flock", "pedlar", "bell"),
        // how clear the air is (0 a haze over the far land, the sea and the far peaks gone; 1 clear to the sea; not set: as
        // the day is); the alpenglow on the peaks (how pink; not set: at sunrise and sunset); a buzzard circling over the
        // valley (how many rounds); the village's church bell far below, its rings coming up (how hard)
        "lookout" to listOf("clear", "glow", "hawk", "bell"),
    )

    val arts: Set<String> get() = objects.keys

    /** Where the village can open a scene: the fire, the forest, or a building of that type (a spot of the landscape: "spot:<id>"). */
    val places: List<String> = listOf("fire", "forest") +
        si.lanisce.lani.game.BuildingType.entries.map { it.name.lowercase() }

    /**
     * A spot of an art's stage (companion/SCENES.md, "Stage directions"): where a dialog's `act` can send someone, in one
     * of its [poses] ([Stance] names); [cover] is the object slot that hides someone there (the door for behind the
     * door), so a tap on it is a tap on the spot in a tap turn.
     */
    class Spot(val name: String, vararg poses: String, val cover: String? = null) {
        val poses: List<String> = poses.toList()
    }

    /**
     * art id → its stage: the spots people can be sent to, with their poses (the art's person spots among them, standing).
     * An art without one has no stage directions yet. The bridge keeps a copy (companion/bridge/src/scenes.ts, SCENE_STAGE).
     */
    val stage: Map<String, List<Spot>> = mapOf(
        // V hiši: by the stove, at the table, at the door; sitting on the zapeček; under the table and behind the door (it
        // stands open, the leaf into the room), to hide or peek out, or standing there
        "livingroom" to listOf(
            Spot("stove", "stand"), Spot("table", "stand"), Spot("door", "stand"),
            Spot("bench", "sit"),
            Spot("under-table", "stand", "hide", "peek", cover = "table"),
            Spot("behind-door", "stand", "hide", "peek", cover = "door"),
        ),
        // Ob ognju: on the logs by the fire, sitting; between the fire and the tent, where the children play;
        // behind the stump in front and behind the woodpile at the back, to hide (the top of a head shows over them) or peek
        // out, or standing there
        "campfire" to listOf(
            Spot("left", "sit"), Spot("right", "sit"),
            Spot("back", "stand"),
            Spot("behind-stump", "stand", "hide", "peek", cover = "stump"),
            Spot("behind-logs", "stand", "hide", "peek", cover = "logs"),
        ),
        // V šotoru: inside on the rug (lying there too, tucked in under a blanket, awake or asleep), by the front outside,
        // out on the meadow; by the lantern under the roof, on the bedroll behind the table (out of the rain)
        "tent" to listOf(
            Spot("left", "stand", "lie", "sleep"), Spot("right", "stand"), Spot("door", "stand"),
            Spot("lantern", "sit", "stand"),
        ),
        // V podstrešju: by Nejc's bed, by the chest, at the top of the stairs; in the bed under the patchwork quilt, lying
        // awake, sitting up on its edge (getting up) or asleep
        "attic" to listOf(
            Spot("bed", "stand"), Spot("chest", "stand"), Spot("stairs", "stand"),
            Spot("in-bed", "lie", "sit", "sleep"),
        ),
        // V kovačnici: at the anvil, by the forge, in the doorway (Tone out there with his coffee)
        "smithy" to listOf(
            Spot("anvil", "stand"), Spot("forge", "stand"), Spot("door", "stand"),
        ),
        // V cerkvi: by the altar, in the aisle, by the door; at the bell's rope by the door, holding it (a child lifted up to
        // it, riding up and down with it while the bell swings)
        "church" to listOf(
            Spot("altar", "stand"), Spot("aisle", "stand"), Spot("door", "stand"),
            Spot("rope", "stand"),
        ),
    )
}
