package si.lanisce.lani.game.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The real moon of a date, for the skies the scenes and the village draw: how old it is (days since the new moon, on
 * the mean synodic month counted from a known new moon), how much of it is lit and on which side, the phase's name,
 * and about when it is up. Pure arithmetic, good to about a day: the true moon runs up to some 14 hours ahead of the
 * mean one or behind it.
 */
object Moon {
    /** The mean synodic month: new moon to new moon, in days. */
    const val SYNODIC = 29.530588853

    /** A new moon to count from: 2000-01-06 18:14 UTC, in days since 1970-01-01. */
    const val NEW_2000 = 10962.759722

    /** The full moon's age: what a frame has when nobody says (tests, previews). */
    const val FULL = (SYNODIC / 2).toFloat()

    /** A moon lit less than this share is new: it isn't drawn. */
    const val NEW_LIT = 0.03f

    private const val MS_PER_DAY = 86_400_000.0

    /** The moon's age at [epochMillis]: days since the last new moon, 0 until [SYNODIC]. */
    fun age(epochMillis: Long): Float {
        val days = epochMillis / MS_PER_DAY - NEW_2000
        return (days - floor(days / SYNODIC) * SYNODIC).toFloat()
    }

    /** The moon's age now. */
    fun now(): Float = age(System.currentTimeMillis())

    /** The phase angle: 0 at new moon, π when full, back to 2π at the next new moon. */
    fun angle(age: Float): Double = 2 * PI * age / SYNODIC

    /** The share of the disc that is lit: 0 new, ½ at the quarters, 1 full. */
    fun lit(age: Float): Float = ((1 - cos(angle(age))) / 2).toFloat()

    /** Waxing (from new to full): lit on the right, as seen from the northern hemisphere; waning, on the left. */
    fun waxing(age: Float): Boolean = age < SYNODIC / 2

    /** The eight phases, each the eighth of the month around its moment; [key] names it in the string tables. */
    enum class Phase(val key: String, val emoji: String) {
        NEW("new", "🌑"),
        WAXING_CRESCENT("waxingCrescent", "🌒"),
        FIRST_QUARTER("firstQuarter", "🌓"),
        WAXING_GIBBOUS("waxingGibbous", "🌔"),
        FULL("full", "🌕"),
        WANING_GIBBOUS("waningGibbous", "🌖"),
        LAST_QUARTER("lastQuarter", "🌗"),
        WANING_CRESCENT("waningCrescent", "🌘"),
    }

    fun phase(age: Float): Phase = Phase.entries[floor(age / SYNODIC * 8 + 0.5).toInt().mod(8)]

    /**
     * How far into the lit part the point ([x], [y]) of the disc lies, in radii from its middle (x to the right, y
     * down); zero or less where it is dark. The terminator between is half an ellipse as tall as the disc, its width
     * the cosine of the phase angle: the right edge alone at new moon, the straight middle at the quarters, the left
     * edge when full (mirrored while waning).
     */
    fun light(x: Float, y: Float, age: Float): Float {
        val half = sqrt((1f - y * y).coerceAtLeast(0f))
        val side = if (waxing(age)) x else -x
        return side - half * cos(angle(age)).toFloat()
    }

    /**
     * Where the moon is on its way across the sky at [hour], on a day of [month] whose sun stands highest at [noon]:
     * 0 as it rises in the east, ½ at its highest, 1 as it sets; negative while it is down. It is highest about 50
     * minutes later each day: with the sun at new moon, at dusk at the first quarter, at midnight when full, at dawn at
     * the last quarter; and it is up as long as the sun is on the day of the year when the sun stands where the moon
     * does now (the full moon of winter long and high, the summer's low and short).
     */
    fun arc(age: Float, hour: Float, noon: Float, month: Int): Float {
        val f = (age / SYNODIC).toFloat()
        val highest = noon + 24f * f
        val doy = (month - 1) * 30.4f + 15f + 365.25f * f
        val half = (12.15f + 3.45f * cos(2 * PI * (doy - 172) / 365.0).toFloat()) / 2f
        val from = (hour - highest).mod(24f).let { if (it >= 12f) it - 24f else it }
        if (from <= -half || from >= half) return -1f
        return (from + half) / (2f * half)
    }

    /** The moon's height along its [arc]: 0 on the horizon, 1 at its highest; 0 while it is down. */
    fun height(arc: Float): Float = if (arc < 0f) 0f else sin(PI * arc).toFloat()
}
