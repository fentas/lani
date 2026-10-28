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
     * at its foot, a candle at night), and Aldo's basket of mushrooms by whoever stands at the clearing while a dialog
     * counts them ("guarda nel mio cesto").
     */
    val FRIULI_FOREST = Table.of("forest", variants = mapOf("shrine" to "ancona", "basket" to "funghi"))

    /** Friuli's stream (Al ruscello): Lombardy poplars along the banks and the woods' edge, vine rows and a campanile on the hill. */
    val FRIULI_STREAM = Table.of("stream", variants = mapOf("trees" to "pioppi", "hills" to "collio"))

    /**
     * Friuli's pond (Allo stagno): the lowland wood round it (Lombardy poplars and oaks, as by the lagoon's woods), and Nonno
     * Bepi's tin of worms on the grass at the jetty's foot by Moro while someone fishes ("la scatola dei vermi … vicino a Moro").
     */
    val FRIULI_POND = Table.of("pond", variants = mapOf("trees" to "pioppi", "bait" to "vermi"))

    /** Friuli's sea (Il mare, its own art): the batela's halyard from the masthead to its cleat ("Vedi la corda lì?"). */
    val FRIULI_SEA = Table.of("sea", variants = mapOf("halyard" to "drizza"))

    /** Each culture's look: its arts' tables added up. A culture not here draws every art as the default culture does. */
    private val tables: Map<String, Table> = mapOf(
        "kaernten" to KAERNTEN_ALPS + KAERNTEN_CAMPFIRE,
        "lakeland" to LAKELAND_ALPS + LAKELAND_CAMPFIRE,
        "friuli" to FRIULI_KITCHEN + FRIULI_SMITHY + FRIULI_TENT + FRIULI_MILL + FRIULI_FIELD + FRIULI_FOREST + FRIULI_STREAM + FRIULI_POND + FRIULI_SEA,
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
