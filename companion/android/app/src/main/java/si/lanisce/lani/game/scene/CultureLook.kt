package si.lanisce.lani.game.scene

import java.util.concurrent.ConcurrentHashMap

/**
 * A culture's look in the scenes (companion/SCENES.md, "Culture looks"): what a painter draws its own way in that
 * culture's village. Four kinds of thing, each named for its art (`school.plaster.l`, `school.map`, `kitchen.hearth`):
 *
 * - a colour ([colour]): the plaster, the wainscot, a flag's stripes, the desks' wood;
 * - a material ([material]): what a thing is made of, a word the painter knows ("wood", "painted", "stone");
 * - a variant ([variant]): which of the art's own versions of a thing it draws ("friuli" for the school's map), or a thing
 *   only this culture's village has (the school's "abacus"); null for the art's own, and none of a thing only a culture adds;
 * - a fixture ([fixes], [fixture]): when a thing of the art is there where the culture's picture has it otherwise than the
 *   default's ([SceneFixtures]: Friuli's fogolâr, the kitchen's hearth, stands at every level, beside the stove).
 *
 * The village map reads the same tables for its own art, `map` (si.lanisce.lani.game.render.MapLook: the hayrack, the
 * well, the flags, the mountains …), so a culture's map and its scenes are written side by side.
 *
 * A painter always asks with its own default, so what a culture doesn't set is the painter's own: the default culture
 * (Primorska, the look every art was drawn in) and a culture without a look draw exactly as before. [id] is the culture's
 * ([si.lanisce.lani.game.culture.Cultures] ids), [landscape] the land its village stands on, for the touches only one land
 * has (the coast's). Pure; the looks are [CultureLooks]'.
 */
class CultureLook internal constructor(
    val id: String,
    val landscape: String,
    private val table: CultureLooks.Table,
) {
    /** The colour [name] of [art] ("school", "plaster.l"), else [default]: the painter's own. */
    fun colour(art: String, name: String, default: Int): Int = table.colours["$art.$name"] ?: default

    /** The material [name] of [art] ("school", "wainscot"), else [default]. */
    fun material(art: String, name: String, default: String): String = table.materials["$art.$name"] ?: default

    /** The variant of thing [name] of [art] ("school", "map": "friuli"), or null for the art's own (and for a thing the culture doesn't add). */
    fun variant(art: String, name: String): String? = table.variants["$art.$name"]

    /** Whether this look says when thing [slot] of [art] is there ([SceneFixtures]), otherwise than the art's own fixture. */
    fun fixes(art: String, slot: String): Boolean = "$art.$slot" in table.fixtures

    /** When thing [slot] of [art] is there in this look, if it [fixes] it: its needs, null for always. */
    fun fixture(art: String, slot: String): SceneNeeds? = table.fixtures["$art.$slot"]

    /** Whether this look sets anything of [art]: whether its pictures differ from the default culture's. */
    fun touches(art: String): Boolean = art in table.arts

    /**
     * What a picture of [art] is kept by (a painter's still picture, a sticker cut from it): the same for every culture
     * that draws [art] as the default does, so nothing is drawn twice for a look that changes nothing of it.
     */
    fun keyOf(art: String): String = if (touches(art)) id else ""
}

/**
 * The cultures' looks in the scenes ([CultureLook]; companion/SCENES.md, "Culture looks"), by culture: each a [Table] of
 * its arts', one value per art (FRIULI_SCHOOL …) added up, so an art's look is written in one place.
 */
object CultureLooks {
    /**
     * What a look sets, each by "<art>.<name>": [colours] (ARGB), [materials] and [variants]; and by "<art>.<slot>" the
     * [fixtures] its picture has otherwise than the art's own ([SceneFixtures]; null: always there). Tables add up
     * ([plus]); one art's names are its own.
     */
    class Table(
        val colours: Map<String, Int> = emptyMap(),
        val materials: Map<String, String> = emptyMap(),
        val variants: Map<String, String> = emptyMap(),
        val fixtures: Map<String, SceneNeeds?> = emptyMap(),
    ) {
        /** The arts it sets something of (the part of a name before its first dot). */
        val arts: Set<String> = (colours.keys + materials.keys + variants.keys + fixtures.keys).map { it.substringBefore('.') }.toSet()

        operator fun plus(o: Table) = Table(colours + o.colours, materials + o.materials, variants + o.variants, fixtures + o.fixtures)

        companion object {
            /** A table of [art]'s: its names given without the art (`plaster.l` for `school.plaster.l`). */
            fun of(
                art: String, colours: Map<String, Int> = emptyMap(), materials: Map<String, String> = emptyMap(), variants: Map<String, String> = emptyMap(),
                fixtures: Map<String, SceneNeeds?> = emptyMap(),
            ) = Table(colours.mapKeys { "$art.${it.key}" }, materials.mapKeys { "$art.${it.key}" }, variants.mapKeys { "$art.${it.key}" }, fixtures.mapKeys { "$art.${it.key}" })

            val EMPTY = Table()
        }
    }

    /**
     * Carinthia's mountains (`alps`, Am See: the Faaker See under the Mittagskogel): the Karawanken behind the lake with the
     * Mittagskogel's pyramid and its summit cross, the lake turquoise, the church's onion dome, the Almhütte of logs under
     * shingles weighted with stones and its trough, Fleckvieh cows, and on the far shore a farmhouse with its timbered upper
     * storey, a Harpfe, a boathouse and a jetty.
     */
    val KAERNTEN_ALPS = Table.of(
        "alps",
        variants = mapOf("range" to "karawanken", "lake" to "turquoise", "church" to "onion", "hut" to "alm", "cow" to "fleckvieh", "shore" to "carinthian"),
    )

    /**
     * The Lake District's fells (`alps`, On the fell): rounded fells of grass and bracken with crags and dry-stone walls
     * climbing them, a cairn on the summit, the lake dark with a wooded island, the chapel whitewashed under slate with its
     * battlemented tower, the packhorse bridge, the shepherd's hut of slate stone, Daisy the one cow (an Ayrshire) among the
     * Herdwick sheep and Sooty the black lamb, Meg the sheepdog by the wall, ravens off the crag, a feral goat and a Herdwick
     * ram for the chamois and the ibex, a rabbit for the marmot, buttercups and foxgloves, oaks and birches for spruces.
     */
    val LAKELAND_ALPS = Table.of(
        "alps",
        materials = mapOf("wall" to "slate"),
        variants = mapOf(
            "range" to "fells", "lake" to "tarn", "church" to "chapel", "bridge" to "packhorse", "hut" to "stone", "cow" to "ayrshire",
            "herd" to "herdwicks", "dog" to "collie", "birds" to "ravens", "chamois" to "goat", "ibex" to "ram", "marmot" to "rabbit",
            "flowers" to "meadow", "trees" to "broadleaf", "shore" to "island",
        ),
    )

    /**
     * Carinthia's and the Lake District's campfire (Am Feuer, By the fire): the village round the fire is FireStyle's
     * already; the camp's tent flies Austria's red, white and red, and England's St George's cross.
     */
    val KAERNTEN_CAMPFIRE = Table.of("campfire", variants = mapOf("flag" to "austria"))
    val LAKELAND_CAMPFIRE = Table.of("campfire", variants = mapOf("flag" to "england"))

    // ------------------------------------------------------------------ Friuli's indoor places

    /**
     * Nonna Rosa's kitchen in Friuli (KitchenPainter): the fogolâr in the corner at every level, so its hearth is a thing of
     * the kitchen at every level (not only the first, as the open hearth before the Slovene stove is); the spolert, the iron
     * range, beside it from the second level (the "stove", il fornello); the copper cauldron on its chain (the "pot"); Zia
     * Nives's radio in the front corner (from level 3 the corner, l'angolo, without a holy corner); the walnut dresser; an
     * ochre dado under a frieze of vines and grapes; the Collio's vines and a cypress through the window.
     */
    private val FRIULI_KITCHEN = Table.of(
        "kitchen",
        colours = mapOf(
            "dado" to 0xFFC9A468.toInt(), "dado.d" to 0xFFB28C54.toInt(), "border" to 0xFF9A4632.toInt(),
            "cupboard.l" to 0xFF9C6C42.toInt(), "cupboard.m" to 0xFF7A4E2C.toInt(), "cupboard.d" to 0xFF56341C.toInt(), "cupboard.back" to 0xFF3A281C.toInt(),
        ),
        variants = mapOf(
            "hearth" to "fogolar", "stove" to "spolert", "pot" to "cjaldir", "corner" to "radio", "cupboard" to "walnut",
            "frieze" to "vines", "view" to "collio",
        ),
        fixtures = mapOf("hearth" to null),
    )

    /**
     * Fabbro Bruno's smithy in Friuli (SmithyPainter): walls of river cobbles with bands of brick, as the plain's and the
     * Isonzo's houses are built; a wrought-iron grille in the window, his black notebook of orders on its sill; the moka on
     * the forge's stone top.
     */
    private val FRIULI_SMITHY = Table.of(
        "smithy",
        variants = mapOf("walls" to "cobbles", "window" to "grille", "notebook" to "black", "moka" to "moka"),
    )

    /**
     * The tent at Friuli's camp (TentPainter): the pennant of Friuli (the golden eagle on blue) on its front post, a pair of
     * scarpets by the camp bed, a row of vines along the meadow's back edge.
     */
    private val FRIULI_TENT = Table.of(
        "tent",
        variants = mapOf("pennant" to "friuli", "slippers" to "scarpets", "vines" to "collio"),
    )

    /**
     * Mugnaio Franco's mill in Friuli (MillPainter): it grinds maize for polenta, golden kernels in the hopper and the
     * measure, the meal yellow in its sack; cobs hung up to dry from the gallery and on the wall; the Collio through the window.
     */
    private val FRIULI_MILL = Table.of(
        "mill",
        colours = mapOf(
            "grain.l" to 0xFFFAD050.toInt(), "grain.m" to 0xFFEAA22C.toInt(), "grain.d" to 0xFFB4701A.toInt(),
            "meal" to 0xFFF2CE62.toInt(), "meal.d" to 0xFFD6AE46.toInt(),
        ),
        variants = mapOf("grain" to "maize", "view" to "collio"),
    )

    // ------------------------------------------------------------------ Friuli outdoors (Teo's village, companion/SCENES.md "Culture looks")

    /**
     * Friuli's field (Nel campo): the fienile of the plain instead of the kozolec (river cobbles laid in courses with brick
     * bands and quoins, two brick arches below, the hay loft above behind brick lattices, a coppi roof), the village on
     * the hill as Friuli builds it (ochre and terracotta plaster, coppi, the free-standing brick campanile), vine rows
     * on the Collio's slopes, and the plain's trees: a row of pollarded mulberries with the vines strung between them
     * (the piantata) and Lombardy poplars along the far ditches.
     */
    val FRIULI_FIELD = Table.of(
        "field",
        variants = mapOf("kozolec" to "fienile", "village" to "friuli", "hills" to "collio", "trees" to "piantata"),
    )

    /**
     * Friuli's wood (Nel bosco): an ancona by the path (a wayside shrine on its pillar, the Madonna in its niche, flowers
     * at its foot, a candle at night). Aldo's basket of mushrooms is the forest's own, as Anton's is: its dialog's
     * "basket" ("guarda nel mio cesto").
     */
    val FRIULI_FOREST = Table.of("forest", variants = mapOf("shrine" to "ancona"))

    /** Friuli's stream (Al ruscello): Lombardy poplars along the banks and the woods' edge, vine rows and a campanile on the hill. */
    val FRIULI_STREAM = Table.of("stream", variants = mapOf("trees" to "pioppi", "hills" to "collio"))

    /**
     * Friuli's pond (Allo stagno): the lowland wood round it (Lombardy poplars and oaks, as by the lagoon's woods), and Nonno
     * Bepi's tin of worms on the grass at the jetty's foot by Moro while someone fishes ("la scatola dei vermi … vicino a Moro").
     */
    val FRIULI_POND = Table.of("pond", variants = mapOf("trees" to "pioppi", "bait" to "vermi"))

    /** Friuli's sea (Il mare, its own art): the batela's halyard from the masthead to its cleat ("Vedi la corda lì?"). */
    val FRIULI_SEA = Table.of("sea", variants = mapOf("halyard" to "drizza"))

    private fun hex(v: Long): Int = si.lanisce.lani.game.render.Col.hex(v)

    /**
     * The Friuli village's school (Maestra Elena's): an old Italian classroom. The walls pale yellow over a lower wall
     * painted in a sea-green enamel (the zoccolo) instead of the oak wainscot, a vine with bunches of grapes in the
     * frieze (the Collio's) instead of the tulips, the map of Friuli Venezia Giulia instead of Slovenia's, the Italian
     * tricolore on the flagpole, the pallottoliere where the basket stands, and "Ciao! Come va? Bene!" on the board
     * before the first lesson (companion/SCENES.md "Culture looks").
     */
    internal val FRIULI_SCHOOL = Table.of(
        "school",
        colours = mapOf(
            "plaster.l" to hex(0xF7EDCF), "plaster.m" to hex(0xEDDDB4), "plaster.d" to hex(0xD6C390),
            "wainscot.l" to hex(0xC6DDCB), "wainscot.m" to hex(0xA9C9B2), "wainscot.d" to hex(0x6F8F7A),
        ),
        materials = mapOf("wainscot" to "painted"),
        variants = mapOf("map" to "friuli", "flag" to "it", "frieze" to "vine", "abacus" to "pallottoliere", "greeting" to "it"),
    )

    // ------------------------------------------------------------------ Friuli's buildings and their rooms (places-it-buildings)
    // Each art's table on its own (they fill in as each art gets its Friuli look); an empty one sets nothing.

    /**
     * Friuli's watchtower (Sulla torre, WatchtowerPainter): the village below in Friuli's ochre, terracotta and cream
     * plaster under coppi, its huts of stone (on the coast the lagoon's casoni, thatched with reed), the free-standing brick
     * campanile among the roofs, vine rows in the fields and the Isonzo emerald in the valley; Friuli's flag, the golden
     * eagle on blue; the tower's top built of river cobbles with bands of brick from its third level, a brick arch from each
     * pillar to the eave, the tavelle (flat terracotta tiles) under its roof and an iron railing from its fourth.
     */
    internal val FRIULI_WATCHTOWER = Table.of("watchtower", variants = mapOf("village" to "friuli", "flag" to "friuli", "tower" to "stone"))

    /**
     * Friuli's market (Al mercato, MarketPainter): a market day in a Collio village. The houses round it as Friuli builds them
     * (the fire's village's colours, its coppi and shutters) and the free-standing brick campanile with its low pyramid cap,
     * vine rows on the hills; river cobbles with bands of brick; the awnings in Friuli's colours, the village's own stall in
     * the blue and gold of its flag; Friulian goods (Montasio, gubana, a San Daniele ham, salami, the season's white asparagus,
     * rosa di Gorizia and chestnuts, scarpets, on the coast fish) and Matteo's Collio white on his cart.
     */
    internal val FRIULI_MARKET = Table.of(
        "market",
        variants = mapOf("village" to "friuli", "hills" to "collio", "ground" to "acciottolato", "awnings" to "friuli", "goods" to "friuli"),
    )

    /**
     * Friuli's square (In piazza, SquarePainter): a Collio village's piazza. The church's gable in plaster under coppi and the
     * free-standing brick campanile with its low pyramid cap; the houses in Friuli's plaster under coppi with their shutters,
     * the nearest Marisa's osteria with its sign and its frasca; the well-head of white stone under a wrought-iron arch with a
     * copper bucket; a panchina of green slats on iron; vine rows on the hills. The linden (il tiglio) stays.
     */
    internal val FRIULI_SQUARE = Table.of(
        "square",
        variants = mapOf("village" to "friuli", "hills" to "collio", "well" to "vera", "bench" to "panchina", "osteria" to "frasca"),
    )

    /**
     * Apicoltore Aldo's bee garden on the Collio (Dalle api, ApiaryPainter): not the Slovene bee house with its painted
     * fronts (the končnice are Slovene) but a shed of river cobbles with brick bands under coppi, Dadant box hives in tiers in
     * its open front, each painted a plain colour with its number and landing board (`house`); the hive apart a box hive too
     * (`hive`); Aldo's sign by the path, MIELE on a yellow board, for the `panel`; an acacia (robinia) where the linden
     * stands, white with racemes in May (`tree`); pale acacia honey in the jars, a dark chestnut one in autumn (`honey`);
     * World Bee Day's bunting in Friuli's yellow and blue (`bunting`).
     */
    internal val FRIULI_APIARY = Table.of(
        "apiary",
        variants = mapOf("house" to "friuli", "panel" to "sign", "hive" to "box", "tree" to "acacia", "honey" to "acacia", "bunting" to "friuli"),
    )

    /**
     * The little church of a Collio village (In chiesa, ChurchPainter), not the Karst church: the far wall a pitched gable
     * under an open timber roof with its truss (`roof`) for the starry vault; bullseye glass in lead (`glass`, the rulli) for
     * the coloured diamonds; a floor of terracotta tiles (`floor`, cotto) for the limestone slabs; a Venetian altarpiece, the
     * Madonna enthroned between two saints, in an aedicule of white Istrian stone (`altarpiece`) for the marble columns and
     * the sunburst; faded frescoes of saints, a grey plinth and a band of red teeth on ochre (`walls`) for the consecration
     * crosses, the marbled dado and the wave frieze; the bell gable, a campanile a vela of the Collio's layered ponca with
     * a brick arch and coppi (`gable`); the loft's balustrade painted as marble (`loft`) for the Slovene tulips; the bell rope's
 * grip in Friuli's yellow and blue (`rope`) for the white, blue and red.
     */
    internal val FRIULI_CHURCH = Table.of(
        "church",
        variants = mapOf(
            "roof" to "trusses", "glass" to "rulli", "floor" to "cotto", "altarpiece" to "pala", "walls" to "frescoes", "gable" to "ponca",
            "loft" to "marble", "rope" to "friuli",
        ),
    )

    /**
     * Nonno Bepi's room by the fogolâr (In salotto, LivingRoomPainter): the fogolâr for the kmečka peč, a knee-high hearth of
     * grey stones in the corner with the fire on it and the benches round it, the corner black with smoke at level 1, its
     * white hood with a shelf of copper pans from level 2, chestnuts roasting over it in autumn; terracotta tiles, the walnut
     * cassapanca, an ochre dado under the vine frieze, the Collio through the window.
     */
    internal val FRIULI_LIVINGROOM = Table.of(
        "livingroom",
        colours = mapOf(
            "dado" to 0xFFC9A468.toInt(), "dado.d" to 0xFFB28C54.toInt(), "border" to 0xFF9A4632.toInt(),
            "cupboard.l" to 0xFF9C6C42.toInt(), "cupboard.m" to 0xFF7A4E2C.toInt(), "cupboard.d" to 0xFF56341C.toInt(),
        ),
        variants = mapOf("stove" to "fogolar", "frieze" to "vines", "view" to "collio", "cupboard" to "walnut"),
    )

    /**
     * Vignaiolo Matteo's cantina (In cantina, CellarPainter): walls of river cobbles with bands of brick, the Ribolla gialla's
     * golden-green grapes in the crate, salami beside the San Daniele ham from level 3, the Collio through the slit, and from
     * level 2 the frasca outside, the green branch at the house's corner that says the farm sells its own wine.
     */
    internal val FRIULI_CELLAR = Table.of(
        "cellar",
        variants = mapOf("walls" to "cobbles", "grapes" to "ribolla", "salami" to "salami", "frasca" to "frasca", "view" to "collio"),
    )

    /**
     * Marisa and Tommaso's attic (In soffitta, AtticPainter): under a roof of coppi, their curved undersides between the
     * battens at level 1, the tavelle on the rafters from 2; maize cobs drying from the ridge beam; the scarpets by the bed;
     * the wardrobe and the chest of walnut; the Collio through the gable window.
     */
    internal val FRIULI_ATTIC = Table.of(
        "attic",
        colours = mapOf("cupboard.l" to 0xFF9C6C42.toInt(), "cupboard.m" to 0xFF7A4E2C.toInt(), "cupboard.d" to 0xFF56341C.toInt()),
        variants = mapOf("roof" to "coppi", "maize" to "pannocchie", "slippers" to "scarpets", "cupboard" to "walnut", "view" to "collio"),
    )

    /**
     * Mugnaio Franco's workshop (In bottega, WorkshopPainter): walls of river cobbles with bands of brick, a Friulian chair with
     * its rush seat hanging on the left wall and a basket of turned legs for the next (Manzano's chairs), the Collio through
     * the window.
     */
    internal val FRIULI_WORKSHOP = Table.of("workshop", variants = mapOf("walls" to "cobbles", "chairs" to "manzano", "view" to "collio"))

    /**
     * The Collio's vineyard (the vinograd project's hills, HillsPainter), seen from the Italian side: the farmstead in ochre
     * plaster under coppi with green shutters, the frasca by its door from November to May; the church on the hill under coppi
     * with a free-standing brick campanile and its low cap; the far farms in Friuli's plaster under coppi.
     */
    internal val FRIULI_HILLS = Table.of("hills", variants = mapOf("farm" to "friuli", "church" to "campanile"))

    // ------------------------------------------------------------------ Friuli's places (the village's projects, the palisade's gate)

    /**
     * Friuli's fire station (FirestationPainter): the tower and the hall's outsides of river cobbles in courses with bands of
     * brick, the tower's roof of coppi, the sign VVF (vigili del fuoco) over the doors, helmets of polished steel, the picture
     * of Santa Barbara (the Italian firemen's patron, with her tower), the banner the tricolore.
     */
    private val FRIULI_FIRESTATION = Table.of(
        "firestation",
        materials = mapOf("walls" to "cobbles", "roof" to "coppi"),
        variants = mapOf("sign" to "vvf", "helmet" to "chrome", "picture" to "barbara", "flag" to "it"),
    )

    /**
     * Friuli's bocce court (BoccePainter): an osteria's wall in ochre under coppi, its shutters brown, and the frasca, the leafy
     * branch hung over the door of an osteria that has its own wine open.
     */
    private val FRIULI_BOCCE = Table.of(
        "bocce",
        colours = mapOf("shutter" to 0xFF6E4A2E.toInt(), "shutter.l" to 0xFF8E6440.toInt()),
        variants = mapOf("inn" to "osteria", "frasca" to "frasca"),
    )

    /**
     * Friuli's playground (PlaygroundPainter): the school's wall pale yellow over a green dado, as Maestra Elena's classroom
     * has it, and a fontanella by the fence, the green cast-iron drinking fountain of the Italian towns.
     */
    private val FRIULI_PLAYGROUND = Table.of("playground", variants = mapOf("school" to "friuli", "fontanella" to "fontanella"))

    /**
     * Friuli's wayside chapel (ChapelPainter): an ancona, its walls ochre under coppi, the bell on an iron bracket under its
     * gable, the fresco of the Madonna and Child on a blue ground with stars; maize in the field, Lombardy poplars along a far
     * ditch, the village on the hill as Friuli builds it with its campanile.
     */
    private val FRIULI_CHAPEL = Table.of("chapel", variants = mapOf("chapel" to "ancona", "field" to "maize", "village" to "friuli", "trees" to "pioppi"))

    /**
     * Friuli's palisade gate (PalisadePainter): the land beyond it is Friuli's, Lombardy poplars along the road, the vines strung
     * between pollarded mulberries on the meadow, the campanile of a village on the far hill.
     */
    private val FRIULI_PALISADE = Table.of("palisade", variants = mapOf("land" to "friuli"))

    /**
     * Friuli's lookout tower (LookoutPainter): the view of Friuli, the Collio's hills striped with vines, the plain with its rows
     * of poplars, the village on the hill as Friuli builds it with its campanile, the lagoon and the sea at Grado; the flag on the
     * roof the golden eagle of Friuli.
     */
    private val FRIULI_LOOKOUT = Table.of("lookout", variants = mapOf("view" to "friuli"))

    /** Friuli's looks of the village's places, added up (each place's table above). */
    private val FRIULI_PLACES: Table get() = FRIULI_FIRESTATION + FRIULI_BOCCE + FRIULI_PLAYGROUND + FRIULI_CHAPEL + FRIULI_PALISADE + FRIULI_LOOKOUT

    // ------------------------------------------------------------------ the village map (companion/GAME.md "The map in a culture's look")

    /**
     * Friuli's village on the map ([si.lanisce.lani.game.render.MapLook]; its houses, church, huts and linden are the fire's
     * village's, FireStyle's): the fienile of the plain for the kozolec, as the field's scene has it (`field.kozolec`); the
     * smithy of river cobbles with brick bands, as inside (`smithy.walls`); the well's stone head under an iron arch (la vera
     * da pozzo), as on the square (`square.well`); the bee house of cobbles under coppi with its hives in plain painted boxes,
     * as Dalle api (`apiary.house`); the school with the tricolore over its door, as its classroom's flagpole (`school.flag`);
     * Friuli's eagle on the watchtower, as Sulla torre flies it (`watchtower.flag`), its pillars of cobbles and brick from
     * level 3 (`watchtower.tower`), and on the tent's pole (`tent.pennant`); the feasts in Friuli's blue and gold, as the
     * bee garden's bunting (`apiary.bunting`), the market's awnings as Al mercato's (`market.awnings`); the greasy pole
     * (l'albero della cuccagna) with its prizes for the maypole; the Collio's vines without the Slovene klopotec; the town
     * clock in the campanile; the fire station of cobbles with bands of brick under coppi (`firestation.walls`), the wayside
     * chapel an ancona in ochre (`chapel.chapel`), Friuli's eagle on the lookout tower's roof (`lookout.view`).
     */
    val FRIULI_MAP = Table.of(
        "map",
        variants = mapOf(
            "hayrack" to "fienile", "smithy" to "cobbles", "well" to "vera", "hives" to "plain", "school" to "italian",
            "flag" to "friuli", "watchtower" to "stone", "pennant" to "friuli", "festive" to "friuli", "maypole" to "cuccagna",
            "vineyard" to "collio", "clock" to "campanile", "firestation" to "cobbles", "chapel" to "ancona",
        ),
    )

    /**
     * Carinthia's village on the map: the Harpfe for the kozolec, a single rack under its little shingle roof as on the far
     * shore of Am See; the log trough with its running spout for the well, as by the Almhütte; Austria's red, white and red
     * on the watchtower, the tent (`campfire.flag`) and the feasts; the Karawanken with the Mittagskogel at the horizon, as
     * the scene behind it (`alps.range`); the orchard of old apple and pear trees (der Obstgarten) for the vineyard; the
     * Stockschießbahn for the bocce court.
     */
    val KAERNTEN_MAP = Table.of(
        "map",
        variants = mapOf(
            "hayrack" to "harpfe", "well" to "trough", "flag" to "austria", "pennant" to "austria", "festive" to "austria",
            "range" to "karawanken", "vineyard" to "orchard", "court" to "curling",
        ),
    )

    /**
     * The Lake District's village on the map: a stone field barn under slate for the hay barn; the village pump on its stone
     * trough for the well; white WBC hives for the bee house; the school of stone under slate with its bellcote; St George's
     * cross on the watchtower and the tent (`campfire.flag`), the feasts' bunting red, white and blue; the fells at the
     * horizon (`alps.range`); the maypole with its long ribbons for the children's dance; damsons on the terraces; a stone
     * bank barn for the toplar; the bowling green for the bocce court; the church clock on its battlemented tower.
     */
    val LAKELAND_MAP = Table.of(
        "map",
        variants = mapOf(
            "hayrack" to "barn", "well" to "pump", "hives" to "wbc", "school" to "stone", "flag" to "england", "pennant" to "england",
            "festive" to "union", "range" to "fells", "maypole" to "ribbons", "vineyard" to "damsons", "toplar" to "bank", "court" to "green",
            "clock" to "battlements",
        ),
    )

    /** Each culture's look: its arts' tables added up. A culture not here draws every art as the default culture does. */
    private val tables: Map<String, Table> = mapOf(
        "kaernten" to KAERNTEN_ALPS + KAERNTEN_CAMPFIRE + KAERNTEN_MAP,
        "lakeland" to LAKELAND_ALPS + LAKELAND_CAMPFIRE + LAKELAND_MAP,
        "friuli" to FRIULI_KITCHEN + FRIULI_SMITHY + FRIULI_TENT + FRIULI_MILL + FRIULI_FIELD + FRIULI_FOREST + FRIULI_STREAM + FRIULI_POND + FRIULI_SEA + FRIULI_SCHOOL +
            FRIULI_WATCHTOWER +
            FRIULI_MARKET +
            FRIULI_SQUARE +
            FRIULI_APIARY +
            FRIULI_CHURCH +
            FRIULI_LIVINGROOM +
            FRIULI_CELLAR +
            FRIULI_ATTIC +
            FRIULI_WORKSHOP +
            FRIULI_HILLS +
            FRIULI_PLACES +
            FRIULI_MAP,
    )

    private val looks = ConcurrentHashMap<String, CultureLook>()

    /** The look of [culture]'s village on [landscape] (the default culture's, and an unknown one's: every painter's own). */
    fun of(culture: String, landscape: String = si.lanisce.lani.game.Landscape.CLASSIC.id): CultureLook =
        looks.getOrPut("$culture|$landscape") { CultureLook(culture, landscape, tables[culture] ?: Table.EMPTY) }

    /** The cultures that have a look of their own, and the arts each touches (tests, the docs). */
    val touched: Map<String, Set<String>> get() = tables.mapValues { it.value.arts }

    /**
     * Every name the looks set, "<art>.<name>" (`school.map`, and a fixture's `kitchen.hearth`): not keys of the string
     * tables, though they look alike (L10nTablesTest leaves them out).
     */
    val names: Set<String> get() = tables.values.flatMap { it.colours.keys + it.materials.keys + it.variants.keys + it.fixtures.keys }.toSet()
}
