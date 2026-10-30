package si.lanisce.lani.game.render

import si.lanisce.lani.game.render.scene.FireStyle
import si.lanisce.lani.game.render.scene.Tower
import si.lanisce.lani.game.scene.CultureLook
import si.lanisce.lani.game.scene.CultureLooks
import java.util.concurrent.ConcurrentHashMap

/**
 * A culture's look on the village map (companion/GAME.md "The map in a culture's look"): what the map draws its own way in
 * that culture's village, from two sources, so that the map and the scenes show the same village:
 *
 * - the village as the culture builds it, [style]: the fire's village's ([FireStyle], the scenes' village round the fire),
 *   for the houses' walls, roofs and shutters, a timbered upper storey or a balcony, the church's tower, the huts of logs,
 *   stone or reed, the linden or the oak;
 * - the things the map draws its own way, the "map" art of the culture's [CultureLook] ([CultureLooks]' tables, beside the
 *   scenes' entries): the hayrack, the well, the hives, the smithy's walls, the school, the flags, the feasts' colours, the
 *   mountains at the horizon and the projects' landmarks.
 *
 * The default culture (Primorska, whose look every sprite was drawn in) and a culture without a look have no [style] and
 * every thing as the map always drew it, pixel for pixel. Read once per culture and land ([of]), so a frame asks fields.
 */
internal class MapLook private constructor(val id: String, val style: FireStyle?, private val look: CultureLook) {
    private fun v(name: String): String? = look.variant(ART, name)

    /** The kozolec building: the Slovene double hayrack, Friuli's fienile, Carinthia's Harpfe, the Lake District's hay barn. */
    val hayrack: Hayrack = Hayrack.entries.firstOrNull { it.id == v("hayrack") } ?: Hayrack.KOZOLEC

    /** The well: its roof over the windlass, the vera's iron arch, the log trough, the village pump. */
    val well: Well = Well.entries.firstOrNull { it.id == v("well") } ?: Well.ROOFED

    /** The bee house's hives: the painted panels, plain painted boxes, the white tiered WBC hives. */
    val hives: Hives = Hives.entries.firstOrNull { it.id == v("hives") } ?: Hives.PAINTED

    /** The smithy's walls are river cobbles with bands of brick (Friuli's, as its scene). */
    val cobbles: Boolean = v("smithy") == "cobbles"

    /** The school: the map's own, Italy's (the tricolore over the door, no bell turret), of stone under slate with a bellcote. */
    val school: SchoolKind = SchoolKind.entries.firstOrNull { it.id == v("school") } ?: SchoolKind.OWN

    /** The watchtower stands on pillars of river cobbles and brick from its third level (Friuli's, as Sulla torre builds it). */
    val stoneTower: Boolean = v("watchtower") == "stone"

    /** The flag on the watchtower, and the town's on the Friends card ([Flag.emoji]). */
    val flag: Flag = Flag.of(v("flag")) ?: Flag.SLOVENIA

    /** The pennant on the tent's pole (from level 2): null for the map's own two red dots. */
    val pennant: Flag? = Flag.of(v("pennant"))

    /** The feasts' colours: the garlands, the ribbons, the banners, the stalls' awnings. */
    val festive: Festive = Festive.of(v("festive"))

    /** The mountains at the horizon, as the scene behind the village has them. */
    val range: Range = Range.entries.firstOrNull { it.id == v("range") } ?: Range.JULIAN

    /** The maypole project's landmark: the mlaj (the Maibaum), the greasy pole with its prizes, the maypole with its ribbons. */
    val maypole: Maypole = Maypole.entries.firstOrNull { it.id == v("maypole") } ?: Maypole.MLAJ

    /** The vineyard project's: the terraces with the klopotec, the Collio's without it, an orchard, damsons on terraces. */
    val vineyard: Vineyard = Vineyard.entries.firstOrNull { it.id == v("vineyard") } ?: Vineyard.TERRACES

    /** The toplar project's is a stone bank barn (the Lake District's). */
    val bankBarn: Boolean = v("toplar") == "bank"

    /** The bocce court project's: the bocce court, the Stockschießbahn, the bowling green. */
    val court: Court = Court.entries.firstOrNull { it.id == v("court") } ?: Court.BOCCE

    /** The town clock's tower: the map's own (ochre, the onion dome), the campanile, the battlemented tower. */
    val clock: Tower? = when (v("clock")) { "campanile" -> Tower.CAMPANILE; "battlements" -> Tower.BATTLEMENTS; else -> null }

    /** Whether this is the map as it always was (the default culture's, or one without a look). */
    val own: Boolean get() = style == null

    companion object {
        /** The art's name in the culture looks' tables ([CultureLooks]): "map.hayrack" and so on. */
        const val ART = "map"

        private val looks = ConcurrentHashMap<String, MapLook>()

        /** The look of [culture]'s village on [landscape] (a coast's own touches: Friuli's casoni). */
        fun of(culture: String, landscape: String = si.lanisce.lani.game.Landscape.CLASSIC.id): MapLook = looks.getOrPut("$culture|$landscape") {
            val look = CultureLooks.of(culture, landscape)
            MapLook(culture, if (look.touches(ART)) FireStyle.of(culture, landscape) else null, look)
        }

        /** The map as it always was: the default culture's. */
        val DEFAULT: MapLook get() = of(si.lanisce.lani.game.culture.Cultures.DEFAULT)

        /**
         * The flag of [culture]'s town as a card shows it (the one its watchtower flies: [flag]); null for a culture this app
         * has no pack of (a newer town's, or none named).
         */
        fun flagEmoji(culture: String): String? =
            if (culture.isBlank() || si.lanisce.lani.game.culture.Cultures.manifest(culture) == null) null else of(culture).flag.emoji
    }
}

/** What stands for the kozolec on the map (see [MapLook.hayrack]). */
internal enum class Hayrack(val id: String) { KOZOLEC("kozolec"), FIENILE("fienile"), HARPFE("harpfe"), BARN("barn") }

/** The well (see [MapLook.well]). */
internal enum class Well(val id: String) { ROOFED("roofed"), VERA("vera"), TROUGH("trough"), PUMP("pump") }

/** The bee house's hives (see [MapLook.hives]). */
internal enum class Hives(val id: String) { PAINTED("painted"), PLAIN("plain"), WBC("wbc") }

/** The school (see [MapLook.school]). */
internal enum class SchoolKind(val id: String) { OWN("own"), ITALIAN("italian"), STONE("stone") }

/** The mountains at the horizon (see [MapLook.range]): the Julian Alps with Triglav, the Karawanken, the fells. */
internal enum class Range(val id: String) { JULIAN("julian"), KARAWANKEN("karawanken"), FELLS("fells") }

/** The maypole project's landmark (see [MapLook.maypole]). */
internal enum class Maypole(val id: String) { MLAJ("mlaj"), CUCCAGNA("cuccagna"), RIBBONS("ribbons") }

/** The vineyard project's landmark (see [MapLook.vineyard]). */
internal enum class Vineyard(val id: String) { TERRACES("terraces"), COLLIO("collio"), ORCHARD("orchard"), DAMSONS("damsons") }

/** The bocce court project's landmark (see [MapLook.court]). */
internal enum class Court(val id: String) { BOCCE("bocce"), CURLING("curling"), GREEN("green") }

/**
 * A flag the map flies (the watchtower's, the tent's pennant, the feasts' banners), by its colour at each cell of a flag
 * [w] by [h] flying from a pole on its left ([at]); [emoji] is how the Friends card shows the town's.
 */
internal enum class Flag(val id: String, val emoji: String) {
    SLOVENIA("slovenia", "🇸🇮"),
    ITALY("italy", "🇮🇹"),
    /** Friuli's: the golden eagle on blue (the Patria del Friuli's); on a card, Italy's. */
    FRIULI("friuli", "🇮🇹"),
    AUSTRIA("austria", "🇦🇹"),
    /** St George's cross; on a card England's flag. */
    ENGLAND("england", "🏴󠁧󠁢󠁥󠁮󠁧󠁿");

    /** Its colour at column [u] of [w] and row [v] of [h]. */
    fun at(u: Int, v: Int, w: Int, h: Int): Int = when (this) {
        // white, blue and red bands (no coat of arms at this size)
        SLOVENIA -> when (v * 3 / h) { 0 -> Pal.FLAG_WHITE; 1 -> Pal.FLAG_BLUE; else -> Pal.FLAG_RED }
        // green, white and red from the pole
        ITALY -> when (u * 3 / w) { 0 -> IT_GREEN; 1 -> Pal.FLAG_WHITE; else -> IT_RED }
        // the eagle in the middle of the blue
        FRIULI -> if (kotlin.math.abs(u - (w - 1) / 2f) <= w / 4.5f && kotlin.math.abs(v - (h - 1) / 2f) <= h / 3.5f) FRIULI_GOLD else FRIULI_BLUE
        AUSTRIA -> if (v * 3 / h == 1) Pal.FLAG_WHITE else AT_RED
        // the red cross on white, a little toward the pole when it can't be in the middle
        ENGLAND -> if (u == (w - 1) / 2 || v == (h - 1) / 2) EN_RED else Pal.FLAG_WHITE
    }

    companion object {
        fun of(id: String?): Flag? = entries.firstOrNull { it.id == id }

        val IT_GREEN = Col.hex(0x1E9A4E); val IT_RED = Col.hex(0xD8303C)
        val FRIULI_BLUE = Col.hex(0x2A5CA8); val FRIULI_GOLD = Col.hex(0xF2C230)
        val AT_RED = Col.hex(0xD21E36)
        val EN_RED = Col.hex(0xD3202E)
        val UK_BLUE = Col.hex(0x1F3E8C)
    }
}

/**
 * A culture's colours for its feasts (see [MapLook.festive]): the flags of its garlands in turn ([bunting]), the ribbons
 * of the likof's spruce and the maypole ([ribbons], [maypole]), the banners along the road and the procession's ([banner]),
 * the awnings of the semenj's stalls (in stripes of the first and the second of [stalls]) and of the market's three
 * ([awnings], each striped with its [stripes]).
 */
internal class Festive(
    val bunting: IntArray,
    val ribbons: IntArray,
    val maypole: IntArray,
    val banner: Flag,
    val stalls: IntArray,
    val awnings: IntArray,
    val stripes: IntArray = intArrayOf(Pal.FLAG_WHITE, Pal.FLAG_WHITE, Pal.FLAG_WHITE),
) {
    companion object {
        /** The map's own (Primorska's): flags red, white, blue, gold and green; the banners Slovenia's. */
        val OWN = Festive(
            bunting = intArrayOf(Pal.FLAG_RED, Pal.FLAG_WHITE, Pal.FLAG_BLUE, Pal.GOLD, Col.hex(0x3EA05A)),
            ribbons = intArrayOf(Pal.FLAG_RED, Pal.FLAG_WHITE, Pal.FLAG_BLUE, Pal.GOLD),
            maypole = intArrayOf(Pal.FLAG_RED, Pal.FLAG_WHITE, Pal.FLAG_BLUE, Pal.GOLD, Pal.PAINTED[3]),
            banner = Flag.SLOVENIA,
            stalls = intArrayOf(Pal.FLAG_RED, Pal.FLAG_WHITE),
            awnings = intArrayOf(Pal.FLAG_RED, Pal.FLAG_BLUE, Pal.LEAF),
        )

        /**
         * Friuli's: its blue and gold (and white) in the garlands and the ribbons, as the bee garden's bunting; its eagle on the
         * banners, as on its watchtower; the market's awnings as Al mercato's, the village's own stall blue striped with gold,
         * the others terracotta and green on cream.
         */
        private val FRIULI = Festive(
            bunting = intArrayOf(Flag.FRIULI_GOLD, Flag.FRIULI_BLUE, Pal.FLAG_WHITE),
            ribbons = intArrayOf(Flag.FRIULI_BLUE, Flag.FRIULI_GOLD, Pal.FLAG_WHITE, Flag.FRIULI_GOLD),
            maypole = intArrayOf(Flag.FRIULI_BLUE, Flag.FRIULI_GOLD, Pal.FLAG_WHITE, Flag.FRIULI_GOLD, Flag.FRIULI_BLUE),
            banner = Flag.FRIULI,
            stalls = intArrayOf(Flag.FRIULI_BLUE, Flag.FRIULI_GOLD),
            awnings = intArrayOf(Flag.FRIULI_BLUE, Col.hex(0xC8683E), Col.hex(0x4E8A44)),
            stripes = intArrayOf(Flag.FRIULI_GOLD, Col.hex(0xF6EEDA), Col.hex(0xF6EEDA)),
        )

        /** Austria's red, white and red, the ribbons with Carinthia's yellow and the green of the spruce. */
        private val AUSTRIA = Festive(
            bunting = intArrayOf(Flag.AT_RED, Pal.FLAG_WHITE),
            ribbons = intArrayOf(Flag.AT_RED, Pal.FLAG_WHITE, Col.hex(0xF2C230), Pal.LEAF),
            maypole = intArrayOf(Flag.AT_RED, Pal.FLAG_WHITE, Col.hex(0xF2C230), Flag.AT_RED, Pal.FLAG_WHITE),
            banner = Flag.AUSTRIA,
            stalls = intArrayOf(Flag.AT_RED, Pal.FLAG_WHITE),
            awnings = intArrayOf(Flag.AT_RED, Col.hex(0x2F6A3E), Flag.AT_RED),
        )

        /** A British fête's: bunting red, white and blue; St George's banners; the maypole's ribbons every colour. */
        private val UNION = Festive(
            bunting = intArrayOf(Flag.EN_RED, Pal.FLAG_WHITE, Flag.UK_BLUE),
            ribbons = intArrayOf(Flag.EN_RED, Pal.FLAG_WHITE, Flag.UK_BLUE, Pal.GOLD),
            maypole = intArrayOf(Flag.EN_RED, Col.hex(0xF4D03A), Flag.UK_BLUE, Col.hex(0x3EA05A), Pal.FLAG_WHITE, Col.hex(0xE87AB0)),
            banner = Flag.ENGLAND,
            stalls = intArrayOf(Flag.UK_BLUE, Pal.FLAG_WHITE),
            awnings = intArrayOf(Flag.UK_BLUE, Flag.EN_RED, Col.hex(0x2E7A4E)),
        )

        fun of(id: String?): Festive = when (id) {
            "friuli" -> FRIULI
            "austria" -> AUSTRIA
            "union" -> UNION
            else -> OWN
        }
    }
}
