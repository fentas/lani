package si.lanisce.lani.game.sky

import si.lanisce.lani.game.sky.Astro.DEG
import si.lanisce.lani.game.sky.Astro.norm
import si.lanisce.lani.game.sky.Astro.signed
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * A meteor shower of the year: when it peaks (the sun's longitude [peak], the same date every year), its radiant (J2000
 * right ascension and declination), how many shooting stars the app lets fly an hour over the whole sky at the peak
 * ([rate]; the real showers bring more), and how many days around the peak it lasts ([sigma], degrees of the sun's way:
 * about days).
 */
data class Shower(val id: String, val peak: Double, val ra: Double, val dec: Double, val rate: Double, val sigma: Double)

/**
 * A shooting star: when it starts ([start], epoch ms) and how long it flies ([duration], ms), where in the sky it starts
 * and ends (altitude, azimuth), how bright it is (0..1), and its shower's id (null: a sporadic one). [id] tells it from
 * the others (its slot).
 */
data class Meteor(
    val id: Long,
    val start: Long,
    val duration: Int,
    val alt0: Double,
    val az0: Double,
    val alt1: Double,
    val az1: Double,
    val bright: Float,
    val shower: String?,
) {
    val end: Long get() = start + duration
}

/**
 * The shooting stars over the village (companion/GAME.md, "The night sky"): now and then one, a few an hour of looking,
 * and many more in the nights of the great showers, streaking away from the shower's radiant while it is up. They are
 * the same for everyone at the same minute and place: each ten seconds of the clock ([SLOT_MS]) has at most one,
 * seeded by the slot.
 */
object Meteors {
    val SHOWERS = listOf(
        Shower("quadrantids", 283.15, 230.0, 49.0, 40.0, 0.6),
        Shower("lyrids", 32.32, 271.0, 34.0, 15.0, 1.0),
        Shower("perseids", 140.0, 48.0, 58.0, 60.0, 2.5),
        Shower("orionids", 208.0, 95.0, 16.0, 20.0, 2.5),
        Shower("leonids", 235.27, 152.0, 22.0, 15.0, 1.2),
        Shower("geminids", 262.2, 112.0, 33.0, 60.0, 1.5),
    )

    /** Sporadic shooting stars an hour, over the whole sky: a view that shows a third of it sees two or three. */
    const val SPORADIC = 8.0

    /** Each slot of the clock has at most one shooting star. */
    const val SLOT_MS = 10_000L

    /** How long one may be tapped after it faded (ms): a finger is slower than a meteor. */
    const val GRACE_MS = 1_800L

    fun shower(id: String): Shower? = SHOWERS.firstOrNull { it.id == id }

    /** How active [s] is at [jdUt]: 1 at its peak, fading over [Shower.sigma] days either side. */
    fun activity(s: Shower, jdUt: Double): Double {
        val d = signed(SunPos.at(Astro.centuries(jdUt)).lambda - s.peak) / s.sigma
        return exp(-d * d / 2)
    }

    /** The shower of the night about [jdUt] (the most active, at a quarter of its peak or more), or null. */
    fun tonight(jdUt: Double): Shower? = SHOWERS.map { it to activity(it, jdUt) }.filter { it.second >= 0.25 }.maxByOrNull { it.second }?.first

    /** A shower's radiant's altitude and azimuth at [jdUt] over [place]. */
    fun radiant(s: Shower, jdUt: Double, place: SkyPlace): DoubleArray {
        val t = Astro.centuries(jdUt)
        val eq = Astro.precess(s.ra, s.dec, t)
        return Astro.horizontal(eq[0], eq[1], place.lat, Astro.lst(jdUt, place.lon))
    }

    /**
     * Shooting stars an hour over the whole sky at [jdUt] from [place]: the sporadic ones, and each shower's by its
     * activity and how high its radiant stands (none while it is down).
     */
    fun rate(jdUt: Double, place: SkyPlace): Double = SPORADIC + SHOWERS.sumOf { s -> showerRate(s, jdUt, place) }

    private fun showerRate(s: Shower, jdUt: Double, place: SkyPlace): Double {
        val a = activity(s, jdUt)
        if (a < 0.02) return 0.0
        val alt = radiant(s, jdUt, place)[0]
        return if (alt <= 0.0) 0.0 else s.rate * a * sin(alt * DEG)
    }

    /** The shooting star of slot [n] (the clock's ms / [SLOT_MS]) over [place], or null for none. */
    fun inSlot(n: Long, place: SkyPlace): Meteor? {
        val u = rnd(n, 0)
        val mid = Astro.jd(n * SLOT_MS + SLOT_MS / 2)
        val showerRates = SHOWERS.map { it to showerRate(it, mid, place) }
        val total = SPORADIC + showerRates.sumOf { it.second }
        if (u >= total * SLOT_MS / 3_600_000.0) return null
        // which: a shower's by its share of the rate, else a sporadic one
        var pick = rnd(n, 1) * total
        var shower: Shower? = null
        for ((s, r) in showerRates) {
            if (pick < r) { shower = s; break }
            pick -= r
        }
        val start = n * SLOT_MS + (rnd(n, 2) * (SLOT_MS - 1000)).toLong()
        val duration = (320 + rnd(n, 3) * 480).toInt()
        val length = 7.0 + rnd(n, 4) * 13.0
        val bright = (0.55 + rnd(n, 5) * 0.45).toFloat()
        val a0: DoubleArray; val a1: DoubleArray
        if (shower != null) {
            // away from the radiant along a great circle through it
            val r = radiant(shower, mid, place)
            val bearing = rnd(n, 6) * 360.0
            val from = 12.0 + rnd(n, 7) * 45.0
            a0 = destination(r[0], r[1], bearing, from)
            a1 = destination(r[0], r[1], bearing, from + length)
        } else {
            // anywhere, falling more often than rising
            a0 = doubleArrayOf(18.0 + rnd(n, 6) * 55.0, rnd(n, 7) * 360.0)
            a1 = destination(a0[0], a0[1], 180.0 + (rnd(n, 8) - 0.5) * 200.0, length)
        }
        if (a0[0] < 6.0) return null // it starts too low to be seen over the hills
        return Meteor(n, start, duration, a0[0], a0[1], a1[0], a1[1], bright, shower?.id)
    }

    /** The shooting stars flying at [millis], or faded less than [GRACE_MS] before it (they can still be tapped). */
    fun around(millis: Long, place: SkyPlace): List<Meteor> {
        val n = Math.floorDiv(millis, SLOT_MS)
        return (n - 1..n).mapNotNull { slot(it, place) }.filter { it.start <= millis && millis <= it.end + GRACE_MS }
    }

    // the last slots asked for: every frame asks for the same two
    private val recent = object : LinkedHashMap<Pair<Long, SkyPlace>, Meteor?>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Long, SkyPlace>, Meteor?>) = size > 6
    }

    private fun slot(n: Long, place: SkyPlace): Meteor? = synchronized(recent) {
        val key = n to place
        if (recent.containsKey(key)) recent[key] else inSlot(n, place).also { recent[key] = it }
    }

    /**
     * The point [dist] degrees from altitude [alt] and azimuth [az] toward [bearing] (0: up toward the zenith, 90:
     * toward greater azimuth, 180: down): the sky's alt/az as a sphere's latitude and longitude.
     */
    fun destination(alt: Double, az: Double, bearing: Double, dist: Double): DoubleArray {
        val p = alt * DEG; val b = bearing * DEG; val d = dist * DEG
        val p2 = asin((sin(p) * cos(d) + cos(p) * sin(d) * cos(b)).coerceIn(-1.0, 1.0))
        val l2 = az * DEG + atan2(sin(b) * sin(d) * cos(p), cos(d) - sin(p) * sin(p2))
        return doubleArrayOf(p2 / DEG, norm(l2 / DEG))
    }

    /** A number 0 until 1 for slot [n] and [k], the same everywhere. */
    private fun rnd(n: Long, k: Int): Double {
        var h = n * -0x61c8864680b583ebL + k * 0x5851F42D4C957F2DL + 0x14057B7EF767814FL
        h = (h xor (h ushr 33)) * -0xae502812aa7333L
        h = (h xor (h ushr 33)) * -0x3b314601e57a13adL
        h = h xor (h ushr 33)
        return (h ushr 11).toDouble() / (1L shl 53).toDouble()
    }
}
