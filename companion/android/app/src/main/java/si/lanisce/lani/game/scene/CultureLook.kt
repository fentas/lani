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

    /** Each culture's look: its arts' tables added up. A culture not here draws every art as the default culture does. */
    private val tables: Map<String, Table> = mapOf(
        "kaernten" to KAERNTEN_ALPS + KAERNTEN_CAMPFIRE,
        "lakeland" to LAKELAND_ALPS + LAKELAND_CAMPFIRE,
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
