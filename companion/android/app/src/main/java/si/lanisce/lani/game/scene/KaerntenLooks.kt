package si.lanisce.lani.game.scene

import si.lanisce.lani.game.scene.CultureLooks.Table

/**
 * Carinthia's buildings and their rooms in its own look (places-kaernten; companion/SCENES.md "Culture looks",
 * docs/plans/05-built/places-kaernten.md): the lower Gailtal below the Dobratsch, white walls with a timbered upper storey and
 * geranium balconies under dark shingles, the onion-domed church, the Harpfe, the Stube with its Kachelofen and its
 * Herrgottswinkel, the Karawanken through every window. Each art's table on its own, added up in [PLACES], which
 * [CultureLooks]' `kaernten` entry takes beside the mountains', the campfire's and the map's.
 */
internal object KaerntenLooks {
    /**
     * Oma Resi's kitchen (In der Küche, KitchenPainter): the open hearth of the Rauchkuchl at level 1 as the art's, the Sparherd
     * from level 2 in white tiles (its iron top and brass rail the art's), the Herrgottswinkel with its Palmbuschen for the holy
     * corner (level 3), the Kredenz in plain panels of pale Zirbe for the painted dresser, the Karawanken through the window.
     */
    val KITCHEN = Table.of(
        "kitchen",
        colours = mapOf(
            "tile.l" to 0xFFFCFAF4.toInt(), "tile.m" to 0xFFE6E2D8.toInt(), "tile.d" to 0xFFBEB8AC.toInt(),
            "cupboard.l" to 0xFFE2B47A.toInt(), "cupboard.m" to 0xFFC8945A.toInt(), "cupboard.d" to 0xFF9A6A3C.toInt(), "cupboard.back" to 0xFF6A4A2C.toInt(),
        ),
        variants = mapOf("corner" to "herrgottswinkel", "cupboard" to "walnut", "view" to "karawanken"),
    )

    /**
     * Opa Sepp's Stube (In der Stube, LivingRoomPainter): the Kachelofen for the kmečka peč, whitewashed at level 1, its dark
     * green bowl tiles from level 2 (`stove` "kachelofen", the tiles' colours); the walls panelled in honey-coloured Zirbe
     * (material `dado` "wood"); the Herrgottswinkel with the Palmbuschen behind the crucifix (`corner`); the Karawanken through
     * the window.
     */
    val LIVINGROOM = Table.of(
        "livingroom",
        colours = mapOf("tile.l" to 0xFF5E9A6A.toInt(), "tile.m" to 0xFF3A7048.toInt(), "tile.d" to 0xFF22482E.toInt()),
        materials = mapOf("dado" to "wood"),
        variants = mapOf("stove" to "kachelofen", "corner" to "herrgottswinkel", "view" to "karawanken"),
    )

    /**
     * Müller Franz's field (Auf dem Feld, FieldPainter): the Harpfe for the kozolec, taller, its many thin poles of larch
     * weathered silver-grey close together like a harp's strings, under a steeper roof of dark shingles (`kozolec`, as the
     * map's hayrack and Am See's far shore have it); the village on the hill as Carinthia builds it (`village`: white houses
     * with their timbered upper storeys and balconies under dark shingles, the church with its onion dome), and on the slopes
     * its farmsteads with their little Harpfen.
     */
    val FIELD = Table.of("field", variants = mapOf("kozolec" to "harpfe", "village" to "kaernten"))

    /**
     * The village square (Auf dem Dorfplatz, SquarePainter): the houses round it white below with a timbered upper storey, a
     * balcony of geraniums across it and dark shingles (`village`: the fire's village's style); the church's tower under an
     * onion dome of green copper; the nearest house Kathi's Gasthaus zur Linde with its wrought-iron sign, a golden linden
     * leaf (`osteria` "gasthaus"); the village's wooden fountain for the well, a trough hewn from a larch log with its spout on
     * a carved post, roofed from WELL's fourth level (`well` "trough", as the map's well); the Maibaum's ribbons red and white
     * (`maypole` "maibaum"). The linden, the bench and the cat are the art's own.
     */
    val SQUARE = Table.of("square", variants = mapOf("village" to "kaernten", "well" to "trough", "osteria" to "gasthaus", "maypole" to "maibaum"))

    /**
     * The view from Carinthia's watchtower (Auf dem Wachturm, WatchtowerPainter): the village below as Carinthia builds it
     * (`village`: white walls, timbered upper storeys with geranium balconies, dark shingles, huts of logs), the white church
     * tower with its onion dome among the roofs; Austria's flag, red, white and red, its pennant the same below the fourth
     * level (`flag`, as the map's watchtower flies it).
     */
    val WATCHTOWER = Table.of("watchtower", variants = mapOf("village" to "kaernten", "flag" to "austria", "pedlar" to "kraxe"))

    /**
     * Carinthia's palisade gate (Am Tor, PalisadePainter): the land beyond it (`land`): larches and a spruce along the road, a
     * Harpfe on the meadow, a white village round its onion-domed church on the far hill, the Karawanken higher and sharper
     * behind.
     */
    val PALISADE = Table.of("palisade", variants = mapOf("land" to "kaernten", "pedlar" to "kraxe"))

    /**
     * The people sprites (`people`, People.kt): the day's pedlar the Kraxenträger from the Kanaltal under his Kraxe, larch
     * posts round a green box of little drawers ("So viele kleine Laden!"), a blue bundle tied on its lid (`pedlar`), on the
     * map, in the scenes and in his portrait; the road's pedlar at the gate and below the watchtower the same (their `pedlar`);
     * everyone in Carinthia's clothes (`wardrobe`, render/scene/Wardrobes.kt: the women's dirndls, Opa Sepp's Kärntner Anzug
     * and his hat with the Gamsbart, the boys' Lederhosen), on the map too.
     */
    val PEOPLE = Table.of("people", variants = mapOf("pedlar" to "kraxe", "wardrobe" to "kaernten"))

    /**
     * The tent at Carinthia's camp (Im Zelt, TentPainter): Austria's pennant, red, white and red, on its front post (`pennant`,
     * as Am Feuer's tent and the map's fly it); along the meadow's back edge a Schragenzaun, the Alpine fence of slanted grey
     * poles between pairs of stakes (`fence`).
     */
    val TENT = Table.of("tent", variants = mapOf("pennant" to "austria", "fence" to "schragen"))

    /**
     * Kathi and Maxi's Dachboden (Am Dachboden, AtticPainter): larch shingles from below at level 1 (`roof`), the boards from
     * level 2 as the art's; strings of Kletzen (dried pears) and a bundle of herbs drying from the ridge beam (`drying`); the
     * Karawanken through the gable window.
     */
    val ATTIC = Table.of("attic", variants = mapOf("roof" to "shingles", "drying" to "kletzen", "view" to "karawanken"))

    /**
     * Müller Franz's workshop (In der Werkstatt, WorkshopPainter): a log house's walls of brown larch logs with their pale
     * chinking (`walls`); a stack of fresh shingles by the broom and the froe he splits them with (`shingles`); the Karawanken
     * through the window.
     */
    val WORKSHOP = Table.of("workshop", variants = mapOf("walls" to "logs", "shingles" to "larch", "view" to "karawanken"))

    /**
     * Mostbauer Stefan's Mostkeller (Im Keller, CellarPainter): apples, red and yellow, in the crate (`grapes`); the Most golden
     * where the art's wine is red, in the tap's run, the jug, the balloon, the glass and the bottles (`wine`); Kärntner Speck
     * and a string of Hauswürstel for the pršut (`ham`); the Karawanken through the slit.
     */
    val CELLAR = Table.of("cellar", variants = mapOf("grapes" to "apples", "wine" to "most", "ham" to "speck", "view" to "karawanken"))

    /**
     * Lehrerin Eva's classroom (In der Schule, SchoolPainter): the map of Kärnten from level 2 (`map`: the land, the Hohe Tauern
     * and the Karawanken, the Drau and its lakes, Villach and Klagenfurt, the title KÄRNTEN), Austria's flag, red, white and
     * red, on the pole from level 3 (`flag`), a small wooden cross over the blackboard (`cross`), and "Grüß Gott! / Wie geht's?
     * / Gut!" on the board before the first lesson (`greeting`).
     */
    val SCHOOL = Table.of("school", variants = mapOf("map" to "kaernten", "flag" to "at", "cross" to "wood", "greeting" to "de"))

    /** The village church (In der Kirche, ChurchPainter): Gothic, its vault's end a net vault of red-ochre ribs on white from level 3 (`roof`). */
    val CHURCH = Table.of("church", variants = mapOf("roof" to "ribs"))

    /**
     * Imker Stanko's bee house (Beim Bienenhaus, ApiaryPainter): the art's, its painted fronts a Carinthian Slovene's pride;
     * World Bee Day's bunting in Austria's red and white (`bunting`, as the map's feasts); his sign by the path, HONIG over MED
     * (`sign`).
     */
    val APIARY = Table.of("apiary", variants = mapOf("bunting" to "austria", "sign" to "honig"))

    /** Schmied Toni's smithy (In der Schmiede, SmithyPainter): rough stone whitewashed over (`walls`), a horseshoe over the door for luck (`door`). */
    val SMITHY = Table.of("smithy", variants = mapOf("walls" to "whitewash", "door" to "horseshoe"))

    /**
     * Market day (Auf dem Markt, MarketPainter): the houses round it as Carinthia builds them, timbered and balconied under dark
     * shingles, the church's onion dome (`village`); the goods (`goods`): slabs of Speck and garlic on the stall's back beam, a
     * Reindling by the loaves, Stefan's Most in green bottles on his cart. The awnings are the art's red and white.
     */
    val MARKET = Table.of("market", variants = mapOf("village" to "kaernten", "goods" to "kaernten"))

    /** Carinthia's looks of its buildings and rooms, added up (each art's table above). */
    val PLACES: Table get() = PEOPLE + KITCHEN + LIVINGROOM + FIELD + SQUARE + WATCHTOWER +
        PALISADE + TENT + ATTIC + WORKSHOP + CELLAR + SCHOOL + CHURCH +
        APIARY + SMITHY + MARKET
}
