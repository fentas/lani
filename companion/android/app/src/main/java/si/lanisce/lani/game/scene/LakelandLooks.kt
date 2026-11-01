package si.lanisce.lani.game.scene

import si.lanisce.lani.game.scene.CultureLooks.Table

/**
 * The Lake District's look of the places its buildings open (companion/SCENES.md "Culture looks";
 * docs/plans/05-built/places-lakeland.md): one table per art, added up in [PLACES], which [CultureLooks] adds to the
 * culture's look beside its fells, its campfire and its map. Grey slate stone and whitewash under slate, dry-stone walls,
 * the fells in every view out, the oak, St George's cross; in the houses the range, the oak dresser with its blue-and-white
 * plates, slate flags and the fireplace.
 */
internal object LakelandLooks {
    private fun hex(v: Long): Int = si.lanisce.lani.game.render.Col.hex(v)

    /** What every room of a Lake District house has (RoomPainter): the fells through the window, slate flags from level 2, an oak plate rail with blue-and-white plates for the stencilled border at 4. */
    private val ROOM = mapOf("view" to "fells", "floor" to "slate", "frieze" to "plates")

    /** The dado a soft duck-egg blue-green, its line blue-grey. */
    private val DADO = mapOf("dado" to hex(0xA8C8C0), "dado.d" to hex(0x8EB0A8), "border" to hex(0x5E7E8A))

    /** The oak of the dresser, the chests, the wardrobe and the settles. */
    private val OAK = mapOf("cupboard.l" to hex(0xA87A4C), "cupboard.m" to hex(0x825A34), "cupboard.d" to hex(0x5E3E22), "cupboard.back" to hex(0x4A3220))

    /**
     * Granny Maggie's farmhouse kitchen (KitchenPainter): the black cast-iron range where the tiled stove stands (the oven
     * door, the fire behind its bars, the boiler and its tap), the oak dresser with its rack of blue-and-white plates, a
     * framed sampler and a brass candlestick for the holy corner, ladder-back chairs; at level 1 the limewash thin on the slate
     * stone; the room's slate flags, the duck-egg dado, the plate rail and the fells.
     */
    val KITCHEN = Table.of(
        "kitchen", colours = DADO + OAK,
        variants = ROOM + mapOf("stove" to "range", "cupboard" to "oak", "corner" to "sampler", "chairs" to "ladder", "walls" to "limewash"),
    )

    /**
     * Old Wilf's room by the fire (LivingRoomPainter): the fireplace in the corner where the tiled stove stands, a hearth of
     * slate stones with the fire in its iron basket, the corner black with smoke at level 1, from 2 the whitewashed chimney
     * breast with its oak mantel beam, two brass candlesticks and a china dog on it; the benches round it oak settles; the
     * oak chest; the sampler; the room's slate flags, dado, plate rail and fells.
     */
    val LIVINGROOM = Table.of(
        "livingroom", colours = DADO + OAK,
        variants = ROOM + mapOf("stove" to "fireplace", "cupboard" to "oak", "corner" to "sampler", "chairs" to "ladder"),
    )

    /** Brewer Sam's cellar (CellarPainter): walls of slate stone, slate flags, damsons in the crate, damson wine in the demijohn, brown ale bottles, the fells through the slit. */
    val CELLAR = Table.of(
        "cellar", variants = mapOf("view" to "fells", "floor" to "slate", "walls" to "slate", "grapes" to "damsons", "bottle" to "brown"),
    )

    /** Mrs Birkett and Harry's attic (AtticPainter): grey slates between the battens at level 1, the wardrobe and the chest of oak, the fells through the gable window. */
    val ATTIC = Table.of("attic", colours = OAK, variants = mapOf("view" to "fells", "roof" to "slate", "cupboard" to "oak"))

    /** Mr Tyson's workshop (WorkshopPainter): walls of slate stone, two shepherd's crooks and a horn-handled stick on the wall, a basket of bobbins by the broom, the fells. */
    val WORKSHOP = Table.of("workshop", variants = mapOf("view" to "fells", "walls" to "slate", "crooks" to "lakeland"))

    /** The camp's tent (TentPainter): St George's cross on the pennant, a dry-stone wall with bracken and foxgloves behind the meadow, green wellingtons by the bed. */
    val TENT = Table.of("tent", variants = mapOf("pennant" to "england", "wall" to "drystone", "boots" to "wellies"))

    /** Mr Dixon's smithy (SmithyPainter): walls of slate stone, the yard of slate flags at the fourth level. */
    val SMITHY = Table.of("smithy", variants = mapOf("walls" to "slate", "yard" to "slate"))

    /**
     * The dale church (ChurchPainter): slate flags, clear leaded glass in diamond quarries, the open timber roof, the Good
     * Shepherd over the altar in an oak frame and a gilt text board, a slate plinth, an oak beam and white memorial tablets,
     * the bellcote of slate stone, the loft plain oak, a brass eagle lectern, a yew outside, no Easter baskets.
     */
    val CHURCH = Table.of(
        "church",
        variants = mapOf(
            "floor" to "slate", "glass" to "quarries", "roof" to "trusses", "altarpiece" to "shepherd", "walls" to "tablets",
            "gable" to "bellcote", "loft" to "oak", "statue" to "lectern", "tree" to "yew", "zegen" to "none",
        ),
    )

    /**
     * Mrs Hartley's village school (SchoolPainter): cream walls over a dark green painted dado, an alphabet frieze, the map of
     * the Lakes, St George's cross on the pole, "Hello! All well? Yes!" on the board, the children's writing
     * slates on a stool where the basket stands.
     */
    val SCHOOL = Table.of(
        "school",
        colours = mapOf(
            "plaster.l" to hex(0xF4ECD6), "plaster.m" to hex(0xE6DCC2), "plaster.d" to hex(0xCCBFA2),
            "wainscot.l" to hex(0x5E8262), "wainscot.m" to hex(0x46684C), "wainscot.d" to hex(0x2E4A34),
        ),
        materials = mapOf("wainscot" to "painted"),
        variants = mapOf("map" to "lakes", "flag" to "england", "frieze" to "alphabet", "greeting" to "en", "abacus" to "slates"),
    )

    /**
     * Mr Tyson's field (FieldPainter): the stone field barn under slate for the kozolec (the map's hay barn), dry-stone walls
     * for the fences, the fells behind with their walled intake fields, the village on the hill round its battlemented tower;
     * a badger digging at the potatoes' edge at night where the wild boar digs elsewhere (`boar`: the Lakes have none).
     */
    val FIELD = Table.of("field", variants = mapOf("kozolec" to "barn", "fence" to "drystone", "hills" to "fells", "village" to "lakeland", "boar" to "badger"))

    /**
     * The village green (SquarePainter): grey stone and whitewashed cottages under slate with white window frames, the church
     * with its square battlemented tower, the oak for the linden, the village pump on its trough for the well (the map's), the
     * green of grass with a gravel path and flags, a low dry-stone wall, daffodils in spring, the fells behind.
     */
    val SQUARE = Table.of(
        "square",
        variants = mapOf(
            "village" to "lakeland", "linden" to "oak", "well" to "pump", "ground" to "green", "fence" to "drystone",
            "flowers" to "daffodils", "hills" to "fells",
        ),
    )

    /**
     * Market day (MarketPainter): the houses of grey stone and whitewash under slate, the square battlemented clock tower,
     * the fells, the awnings green and cream (the farther ones red, blue, yellow and green), heather honey and onions on the
     * village's stall, rhubarb in spring and leeks in winter, brown ale bottles on Sam's cart.
     */
    val MARKET = Table.of("market", variants = mapOf("village" to "lakeland", "hills" to "fells", "awnings" to "lakeland", "goods" to "lakeland"))

    /**
     * Beekeeper Arthur's bee garden (ApiaryPainter): a shed of slate stone under slate with two bee boles in its end wall, a
     * straw skep in each; white WBC hives on its tiers and the hive apart one of them (the map's hives); his green HONEY sign
     * by the path; dark heather honey in the jars.
     */
    val APIARY = Table.of("apiary", variants = mapOf("house" to "boles", "hive" to "wbc", "panel" to "honey", "honey" to "heather"))

    /**
     * The view from the watchtower (WatchtowerPainter): the village below of grey stone and whitewash under slate round its
     * battlemented tower, the fields walled with dry-stone walls, the big mountain a fell; St George's cross on the flag; the
     * tower under slate, on stone pillars from its third level, its parapet battlemented from its fourth.
     */
    val WATCHTOWER = Table.of("watchtower", variants = mapOf("village" to "lakeland", "flag" to "england", "tower" to "pele", "mountains" to "fells", "pedlar" to "packman", "wolves" to "foxes"))

    /**
     * The land beyond the gate (PalisadePainter): the fells, their walls and bracken, a cairn, a hamlet round a battlemented
     * tower; the packman on the road (`pedlar`); foxes by the wood where a dialog sees something there (`wolves`: the Lakes
     * have no wolves).
     */
    val PALISADE = Table.of("palisade", variants = mapOf("land" to "fells", "pedlar" to "packman", "wolves" to "foxes"))

    /**
     * The people sprites (`people`, People.kt): the day's pedlar the packman from Kendal in a tweed coat and a flat cap
     * under his big pack ("a big pack on his back"), brown canvas strapped round, a bolt of Kendal green cloth along its top
     * (`pedlar`), on the map, in the scenes and in his portrait; the road's pedlar at the gate and below the watchtower the
     * same (their `pedlar`).
     */
    val PEOPLE = Table.of("people", variants = mapOf("pedlar" to "packman"))

    // ------------------------------------------------------------------ the landscape and the projects, third wave (b)

    /**
     * The beck below the village (StreamPainter, By the beck): broadleaf woods along it, alders and oaks without a spruce
     * (`trees`), and the fells behind, rounded, their intake fields walled with dry-stone walls, bracken and crags, a
     * whitewashed farm under slate with its stone barn on the lower slope (`hills`).
     */
    val STREAM_LAND = Table.of("stream", variants = mapOf("trees" to "broadleaf", "hills" to "fells"))

    /** The tarn (PondPainter, At the tarn): the fells behind its woods of larch, spruce and beech, mirrored in the still water. */
    val POND = Table.of("pond", variants = mapOf("hills" to "fells"))

    /**
     * Mr Tyson's bank barn (FieldPainter, the project `toplar`, At the bank barn): a two-storey barn of slate stone under slate
     * built into the slope beyond the wheat, the bank's grass behind it, the byre's doors below, the ramp of earth held by a
     * dry-stone wall up to the cart door of the upper floor, the hay in it once finished (the map's bank barn).
     */
    val FIELD_BANK_BARN = Table.of("field", variants = mapOf("toplar" to "bank"))

    /**
     * The drinking fountain on the green (SquarePainter, the project `vodnjak_na_trgu`, At the fountain): of green Honister
     * slate, the block on its beams and on the cart too, the lion's head on its pillar with the water from its mouth, two iron
     * cups on chains.
     */
    val SQUARE_FOUNTAIN = Table.of("square", variants = mapOf("fountain" to "slate"))

    /** The church clock (SquarePainter, the project `mestna_ura`, Under the clock): its round face of slate, gold numerals and hands. */
    val SQUARE_CLOCK = Table.of("square", variants = mapOf("clock" to "slate"))

    /**
     * The mountain rescue hut (FirestationPainter, the project `gasilski_dom`, At the rescue hut): of slate stone under slate,
     * MRT over the doors, red helmets and red jackets on the pegs, two ice axes, the map of the fells, a whistle for the horn,
     * red first-aid bags for the buckets, coils of red and blue rope on the reel, St George's cross on the banner, and the
     * rescue stretcher on its wheel where the pump stands.
     */
    val FIRESTATION = Table.of(
        "firestation",
        materials = mapOf("walls" to "slate", "roof" to "slate"),
        variants = mapOf(
            "sign" to "mrt", "helmet" to "red", "axe" to "ice", "picture" to "map", "horn" to "whistle", "bucket" to "firstaid",
            "hose" to "rope", "flag" to "england", "pump" to "stretcher",
        ),
    )

    /** Mrs Hartley's playground (PlaygroundPainter, the project `igrisce`, At the playground): the school's side of slate stone under slate, a dry-stone wall for the fences. */
    val PLAYGROUND = Table.of("playground", variants = mapOf("school" to "lakeland", "fence" to "drystone"))

    /**
     * Mrs Birkett's viewing station (LookoutPainter, the project `razgledni_stolp`, At the viewing station): inside the stone
     * station at its window, the panes of coloured glass across its top, St George's cross; the fells round the dale with a pike,
     * the lake below with its island, the slate village round its battlemented tower, the walled fields.
     */
    val LOOKOUT = Table.of("lookout", variants = mapOf("view" to "lakeland"))

    /** The Lake District's places, added up. */
    val PLACES: Table get() = PEOPLE + KITCHEN + LIVINGROOM + CELLAR + ATTIC + WORKSHOP + TENT + SMITHY + CHURCH + SCHOOL + FIELD + SQUARE + MARKET +
        APIARY + WATCHTOWER + PALISADE +
        STREAM_LAND + POND +
        FIELD_BANK_BARN + SQUARE_FOUNTAIN + SQUARE_CLOCK +
        FIRESTATION + PLAYGROUND + LOOKOUT
}
