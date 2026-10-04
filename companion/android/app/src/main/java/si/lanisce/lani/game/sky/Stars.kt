package si.lanisce.lani.game.sky

/**
 * A bright star of the catalog: its Harvard Revised number ([hr]), Bayer or Flamsteed name ("Alp Ori"), magnitude, J2000
 * right ascension and declination (degrees), B−V colour, and the IAU's proper name of the brightest ("Betelgeuse").
 */
data class Star(val hr: Int, val name: String, val mag: Float, val ra: Double, val dec: Double, val bv: Float, val proper: String?)

/**
 * A constellation's stick figure: [lines] between its stars (indices into [Stars.all], in pairs), and all its stars
 * ([stars]); the Pleiades ([Stars.PLEIADES]) are a knot of stars without lines.
 */
class Figure(val id: String, val lines: IntArray, val stars: IntArray)

/**
 * The bundled catalog (app resources sky/stars.tsv and sky/figures.tsv, made by companion/android/tools/sky_catalog.py
 * from the Yale Bright Star Catalogue, public domain): some 300 stars of magnitude 3.6 and brighter that rise over the
 * villages, and the stick figures of the constellations the night sky shows (companion/GAME.md, "The night sky").
 */
object Stars {
    /** The Pleiades' id among the figures: Gostosevci, the Seven Sisters. */
    const val PLEIADES = "m45"

    val all: List<Star> by lazy {
        text("stars.tsv").lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val f = line.split('\t')
            Star(f[0].toInt(), f[1], f[2].toFloat(), f[3].toDouble(), f[4].toDouble(), f[5].toFloat(), f.getOrNull(6)?.takeIf { it.isNotBlank() })
        }.toList()
    }

    private val byHr: Map<Int, Int> by lazy { all.withIndex().associate { (i, s) -> s.hr to i } }

    /** The indices of [all], the faintest first: drawn in this order, a bright star is never drawn over by a faint one. */
    val faintestFirst: IntArray by lazy { all.indices.sortedByDescending { all[it].mag }.toIntArray() }

    /** The index in [all] of the star with Harvard Revised number [hr], or -1. */
    fun indexOf(hr: Int): Int = byHr[hr] ?: -1

    /** The star with proper name [name] ("Polaris"), or null. */
    fun named(name: String): Star? = all.firstOrNull { it.proper == name }

    val figures: List<Figure> by lazy {
        text("figures.tsv").lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val (id, rest) = line.split('\t', limit = 2)
            if (id == PLEIADES) {
                val stars = rest.split(' ').map { indexOf(it.toInt()) }.filter { it >= 0 }.toIntArray()
                Figure(id, IntArray(0), stars)
            } else {
                val lines = rest.split(' ').flatMap { it.split('-') }.map { indexOf(it.toInt()) }
                check(lines.all { it >= 0 }) { "sky/figures.tsv: $id names a star stars.tsv hasn't" }
                Figure(id, lines.toIntArray(), lines.distinct().toIntArray())
            }
        }.toList()
    }

    fun figure(id: String): Figure? = figures.firstOrNull { it.id == id }

    /** The figure star [index] belongs to (its lines' or the Pleiades'), or null. */
    fun figureOf(index: Int): Figure? = figures.firstOrNull { f -> f.stars.contains(index) }

    private fun text(name: String): String =
        checkNotNull(Stars::class.java.getResourceAsStream("/sky/$name")) { "the sky catalog (resources sky/$name)" }.use { it.readBytes().decodeToString() }
}
