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
     * slots. The app asks for its scenes with it (GET /scenes?arts=2); the bridge gives an app that doesn't say only what
     * the first ones drew, so an older app never gets a place it can't paint (bridge/src/scenes.ts, `forApp`).
     */
    const val VERSION = 2

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
        // Na vasi: the village square under the linden tree.
        "square" to listOf("linden", "bench", "ball", "well", "dog", "cat", "house", "tower", "bicycle", "flowers", "sun", "fence"),
        // V šotoru: inside the tent, a lantern hanging from the ridge pole.
        "tent" to listOf("lantern", "sleepingbag", "blanket", "pillow", "backpack", "map", "boots", "coat", "hat", "book", "cup", "rope"),
        // Na njivi: a field with the kozolec (hayrack) at its edge.
        "field" to listOf(
            "wheat", "corn", "potatoes", "cabbage", "kozolec", "hay", "scythe", "rake", "basket", "cart", "scarecrow", "fence", "path",
            "deer", "hare", "butterfly", "fox", "boar", "bat", "horse",
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
        // Ob potoku: the stream below the village, France's mill on its bank, stepping stones and a footbridge.
        "stream" to listOf("stream", "stones", "bridge", "willow", "trout", "dragonfly", "cress", "mill", "laundry", "fox", "hedgehog", "butterfly", "bat", "sheep"),
        // Pri ribniku: the pond in the woods, water lilies, reeds and a jetty with a boat.
        "pond" to listOf("pond", "lilies", "reeds", "frog", "ducks", "jetty", "boat", "fish", "heron", "deer", "fox", "butterfly", "bat", "dragonfly"),
        // V cerkvi: the village church inside, the gilded altar at the far wall, pews, stained windows, the organ loft by the door.
        "church" to listOf("altar", "candles", "cross", "painting", "pews", "flowers", "window", "organ", "font", "bell", "rope", "door"),
        // Pri čebelnjaku: the bee house with its painted hive fronts, a straw hive, the linden, a table of honey by the extractor.
        "apiary" to listOf("beehouse", "hive", "panel", "bee", "honey", "comb", "flowers", "tree", "smoker", "extractor", "wax", "bench"),
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
    )

    /** art id → where people stand. */
    val personSlots: Map<String, List<String>> = mapOf(
        "campfire" to listOf("left", "right", "back"),
        "kitchen" to listOf("stove", "table", "door"),
        "forest" to listOf("path", "clearing", "highseat"),
        "square" to listOf("bench", "well", "play1", "play2", "play3"),
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

    /** art id → the effects its painter shows when a dialog's cue sets them (a line's `fx`, levels 0..1). */
    val effects: Map<String, List<String>> = mapOf(
        // the fire burning higher, sparks flying; the kettle steaming; a distant bell (a story's), its rings going out over
        // the dark woods from somewhere beyond them
        "campfire" to listOf("flare", "steam", "bell"),
        // the stove fired up, smoke from the chimney; steam off the cup and the bowl on the table
        "kitchen" to listOf("oven", "steam"),
        // a flock flying up out of the trees and away (the level is how far they have flown); deer stepping out of the trees
        // at the far edge, a stag and two hinds (how far out: 0 back in the trees); the fox trotting out along the edge
        // (how far it has come); the owl turning its head and hooting, its calls going out; bats flitting over the
        // clearing (how many). Not set, the animals out are the day's (Wildlife); the fox, the stag and the bats set by a
        // cue that the day hasn't brought are scenery, not words to tap.
        "forest" to listOf("birds", "herd", "fox", "hoot", "bats"),
        // the bell swinging in the tower, its ringing
        "square" to listOf("bell"),
        // the lantern: 0 dark, 1 lit (not set: lit from dusk)
        "tent" to listOf("lantern"),
        // crows coming to the scarecrow, no longer afraid (the level is how far they have come); the church bell from the
        // village, its rings coming over the field (not set: at the ave, round sunset); bats flitting over it (how many)
        "field" to listOf("crows", "bell", "bats"),
        // the forge roaring as if the bellows blew; sparks off the anvil under the hammer
        "smithy" to listOf("forge", "sparks"),
        "school" to listOf(),
        // the church bell swinging and ringing; a tractor with crates driving along the road (the level is how far it
        // has come); a flock of starlings rising off the vines and wheeling away (how far they have flown)
        "hills" to listOf("bell", "tractor", "starlings"),
        // the sea rising, breakers rolling in and bursting white on the beach; the gulls off the posts and away (how
        // far they have flown); the boat's sail hoisted (how far up), filling with the wind
        "sea" to listOf("waves", "gulls", "sail"),
        // the cows' bells ringing as they graze (how loud); the church bell by the lake swinging and ringing; a fish jumping
        // out of the lake again and again, rings on the water; a flock of alpine choughs off the crags and away (how far);
        // the alpenglow, the peaks flushing pink (how pink; not set: at sunrise and sunset)
        "alps" to listOf("cowbells", "bell", "fish", "birds", "glow"),
        // a trout leaping out of the current again and again, rings where it falls back; the mill wheel's water:
        // 0 the flume dry and the wheel still, 1 a full gush and the wheel turning fast (not set: an easy turn); bats over the
        // water (how many); the church bell from the village, its rings coming down to the water (not set: at the ave)
        "stream" to listOf("trout", "wheel", "bats", "bell"),
        // wild ducks flying up out of the reeds and away (the level is how far they have flown); the frog jumping off its
        // lily pad into the water (1: in the water, just its eyes out); a fish jumping again and again, rings on the water;
        // bats hunting over the water (how many)
        "pond" to listOf("ducks", "frog", "fish", "bats"),
        // the candles on the altar: 1 all of them lit, 0 all out (not set: two burn); the bell in the gable swinging and
        // ringing, the rope going up and down; the organ playing, notes rising from its pipes
        "church" to listOf("candles", "bell", "organ"),
        // a swarm rising off the hives and circling over the garden (how many, how far); the smoker puffing smoke; honey
        // running from the extractor's tap into a jar, the crank turning (how fast)
        "apiary" to listOf("swarm", "smoke", "honey"),
        // a busy market day, shoppers crowding the lane (how busy); the market bell on its post swinging and ringing; the
        // pigeons flying up off the cobbles and wheeling away (how far they have flown)
        "market" to listOf("bustle", "bell", "pigeons"),
        // the watch's torch: 0 out, 1 lit (not set: lit from dusk); the horn blown, its call going out over the valley (how
        // loud); a flock rising off the forest and away (how far); wolves' eyes in the dark forest (how many), grey shapes by day
        "watchtower" to listOf("torch", "horn", "birds", "wolves"),
        // the stove fired up, warm light on the bench (how warm); Muri the dog awake on the bench, sitting up (0: asleep)
        "livingroom" to listOf("stove", "dog"),
        // sawdust flying off the workbench as a board is sawn (how much)
        "workshop" to listOf("dust"),
        // wine running from the barrel's tap into the jug; the candle: 0 out, 1 lit (not set: lit, the cellar is dark)
        "cellar" to listOf("tap", "candle"),
        // the lamp: 0 dark, 1 lit (not set: lit from dusk); the cradle rocking (how far)
        "attic" to listOf("lamp", "cradle"),
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
        // V šotoru: inside on the rug, by the front outside, out on the meadow; by the lantern under the roof, on the
        // bedroll behind the table (out of the rain)
        "tent" to listOf(
            Spot("left", "stand"), Spot("right", "stand"), Spot("door", "stand"),
            Spot("lantern", "sit", "stand"),
        ),
    )
}
