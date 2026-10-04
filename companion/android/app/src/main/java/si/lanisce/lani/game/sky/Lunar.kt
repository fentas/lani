package si.lanisce.lani.game.sky

import si.lanisce.lani.game.sky.Astro.DEG
import si.lanisce.lani.game.sky.Astro.cosd
import si.lanisce.lani.game.sky.Astro.norm
import si.lanisce.lani.game.sky.Astro.signed
import si.lanisce.lani.game.sky.Astro.sind
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Where a body stands: its apparent ecliptic longitude and latitude, right ascension and declination (of date). */
data class Place3(val lambda: Double, val beta: Double, val ra: Double, val dec: Double, val distance: Double)

/** The sun, to about 0.01° (Meeus 25, "low accuracy"): its apparent place; [Place3.distance] in astronomical units. */
object SunPos {
    fun at(t: Double): Place3 {
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
        val e = 0.016708634 - 0.000042037 * t - 0.0000001267 * t * t
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sind(m) + (0.019993 - 0.000101 * t) * sind(2 * m) + 0.000289 * sind(3 * m)
        val trueLong = l0 + c
        val v = m + c
        val r = 1.000001018 * (1 - e * e) / (1 + e * cosd(v))
        val om = 125.04 - 1934.136 * t
        val lambda = norm(trueLong - 0.00569 - 0.00478 * sind(om))
        val eps = Astro.obliquity(t) + 0.00256 * cosd(om)
        val eq = Astro.equatorial(lambda, 0.0, eps)
        return Place3(lambda, 0.0, eq[0], eq[1], r)
    }
}

/**
 * The moon (Meeus 47): its apparent place to about 10″ in longitude and 4″ in latitude, [Place3.distance] in km; and
 * its horizontal parallax. The periodic terms are Meeus's tables 47.A and 47.B, complete.
 */
object MoonPos {
    // D, M, M', F; Σl (0.000001°), Σr (0.001 km)
    private val LR = intArrayOf(
        0, 0, 1, 0, 6288774, -20905355, 2, 0, -1, 0, 1274027, -3699111, 2, 0, 0, 0, 658314, -2955968,
        0, 0, 2, 0, 213618, -569925, 0, 1, 0, 0, -185116, 48888, 0, 0, 0, 2, -114332, -3149,
        2, 0, -2, 0, 58793, 246158, 2, -1, -1, 0, 57066, -152138, 2, 0, 1, 0, 53322, -170733,
        2, -1, 0, 0, 45758, -204586, 0, 1, -1, 0, -40923, -129620, 1, 0, 0, 0, -34720, 108743,
        0, 1, 1, 0, -30383, 104755, 2, 0, 0, -2, 15327, 10321, 0, 0, 1, 2, -12528, 0,
        0, 0, 1, -2, 10980, 79661, 4, 0, -1, 0, 10675, -34782, 0, 0, 3, 0, 10034, -23210,
        4, 0, -2, 0, 8548, -21636, 2, 1, -1, 0, -7888, 24208, 2, 1, 0, 0, -6766, 30824,
        1, 0, -1, 0, -5163, -8379, 1, 1, 0, 0, 4987, -16675, 2, -1, 1, 0, 4036, -12831,
        2, 0, 2, 0, 3994, -10445, 4, 0, 0, 0, 3861, -11650, 2, 0, -3, 0, 3665, 14403,
        0, 1, -2, 0, -2689, -7003, 2, 0, -1, 2, -2602, 0, 2, -1, -2, 0, 2390, 10056,
        1, 0, 1, 0, -2348, 6322, 2, -2, 0, 0, 2236, -9884, 0, 1, 2, 0, -2120, 5751,
        0, 2, 0, 0, -2069, 0, 2, -2, -1, 0, 2048, -4950, 2, 0, 1, -2, -1773, 4130,
        2, 0, 0, 2, -1595, 0, 4, -1, -1, 0, 1215, -3958, 0, 0, 2, 2, -1110, 0,
        3, 0, -1, 0, -892, 3258, 2, 1, 1, 0, -810, 2616, 4, -1, -2, 0, 759, -1897,
        0, 2, -1, 0, -713, -2117, 2, 2, -1, 0, -700, 2354, 2, 1, -2, 0, 691, 0,
        2, -1, 0, -2, 596, 0, 4, 0, 1, 0, 549, -1423, 0, 0, 4, 0, 537, -1117,
        4, -1, 0, 0, 520, -1571, 1, 0, -2, 0, -487, -1739, 2, 1, 0, -2, -399, 0,
        0, 0, 2, -2, -381, -4421, 1, 1, 1, 0, 351, 0, 3, 0, -2, 0, -340, 0,
        4, 0, -3, 0, 330, 0, 2, -1, 2, 0, 327, 0, 0, 2, 1, 0, -323, 1165,
        1, 1, -1, 0, 299, 0, 2, 0, 3, 0, 294, 0, 2, 0, -1, -2, 0, 8752,
    )

    // D, M, M', F; Σb (0.000001°)
    private val B = intArrayOf(
        0, 0, 0, 1, 5128122, 0, 0, 1, 1, 280602, 0, 0, 1, -1, 277693, 2, 0, 0, -1, 173237,
        2, 0, -1, 1, 55413, 2, 0, -1, -1, 46271, 2, 0, 0, 1, 32573, 0, 0, 2, 1, 17198,
        2, 0, 1, -1, 9266, 0, 0, 2, -1, 8822, 2, -1, 0, -1, 8216, 2, 0, -2, -1, 4324,
        2, 0, 1, 1, 4200, 2, 1, 0, -1, -3359, 2, -1, -1, 1, 2463, 2, -1, 0, 1, 2211,
        2, -1, -1, -1, 2065, 0, 1, -1, -1, -1870, 4, 0, -1, -1, 1828, 0, 1, 0, 1, -1794,
        0, 0, 0, 3, -1749, 0, 1, -1, 1, -1565, 1, 0, 0, 1, -1491, 0, 1, 1, 1, -1475,
        0, 1, 1, -1, -1410, 0, 1, 0, -1, -1344, 1, 0, 0, -1, -1335, 0, 0, 3, 1, 1107,
        4, 0, 0, -1, 1021, 4, 0, -1, 1, 833, 0, 0, 1, -3, 777, 4, 0, -2, 1, 671,
        2, 0, 0, -3, 607, 2, 0, 2, -1, 596, 2, -1, 1, -1, 491, 2, 0, -2, 1, -451,
        0, 0, 3, -1, 439, 2, 0, 2, 1, 422, 2, 0, -3, -1, 421, 2, 1, -1, 1, -366,
        2, 1, 0, 1, -351, 4, 0, 0, 1, 331, 2, -1, 1, 1, 315, 2, -2, 0, -1, 302,
        0, 0, 1, 3, -283, 2, 1, 1, -1, -229, 1, 1, 0, -1, 223, 1, 1, 0, 1, 223,
        0, 1, -2, -1, -220, 2, 1, -1, -1, -220, 1, 0, 1, 1, -185, 2, -1, -2, -1, 181,
        0, 1, 2, 1, -177, 4, 0, -2, -1, 176, 4, -1, -1, -1, 166, 1, 0, 1, -1, -164,
        4, 0, 1, -1, 132, 1, 0, -1, -1, -119, 4, -1, 0, -1, 115, 2, -2, 0, 1, 107,
    )

    /** The geometric place (without nutation) at [t] centuries of TT: longitude, latitude, distance (km). */
    fun geometric(t: Double): DoubleArray {
        val t2 = t * t; val t3 = t2 * t; val t4 = t3 * t
        val lp = norm(218.3164477 + 481267.88123421 * t - 0.0015786 * t2 + t3 / 538841.0 - t4 / 65194000.0)
        val d = norm(297.8501921 + 445267.1114034 * t - 0.0018819 * t2 + t3 / 545868.0 - t4 / 113065000.0)
        val m = norm(357.5291092 + 35999.0502909 * t - 0.0001536 * t2 + t3 / 24490000.0)
        val mp = norm(134.9633964 + 477198.8675055 * t + 0.0087414 * t2 + t3 / 69699.0 - t4 / 14712000.0)
        val f = norm(93.2720950 + 483202.0175233 * t - 0.0036539 * t2 - t3 / 3526000.0 + t4 / 863310000.0)
        val a1 = norm(119.75 + 131.849 * t)
        val a2 = norm(53.09 + 479264.290 * t)
        val a3 = norm(313.45 + 481266.484 * t)
        val e = 1 - 0.002516 * t - 0.0000074 * t2
        var sl = 0.0; var sr = 0.0; var sb = 0.0
        var i = 0
        while (i < LR.size) {
            val arg = (LR[i] * d + LR[i + 1] * m + LR[i + 2] * mp + LR[i + 3] * f) * DEG
            val em = when (kotlin.math.abs(LR[i + 1])) { 1 -> e; 2 -> e * e; else -> 1.0 }
            sl += LR[i + 4] * em * sin(arg)
            sr += LR[i + 5] * em * cos(arg)
            i += 6
        }
        i = 0
        while (i < B.size) {
            val arg = (B[i] * d + B[i + 1] * m + B[i + 2] * mp + B[i + 3] * f) * DEG
            val em = when (kotlin.math.abs(B[i + 1])) { 1 -> e; 2 -> e * e; else -> 1.0 }
            sb += B[i + 4] * em * sin(arg)
            i += 5
        }
        sl += 3958 * sind(a1) + 1962 * sind(lp - f) + 318 * sind(a2)
        sb += -2235 * sind(lp) + 382 * sind(a3) + 175 * sind(a1 - f) + 175 * sind(a1 + f) + 127 * sind(lp - mp) - 115 * sind(lp + mp)
        return doubleArrayOf(norm(lp + sl / 1e6), sb / 1e6, 385000.56 + sr / 1000.0)
    }

    /** The apparent place at [t] centuries of TT. */
    fun at(t: Double): Place3 {
        val g = geometric(t)
        val (dPsi, dEps) = Astro.nutation(t).let { it[0] to it[1] }
        val lambda = norm(g[0] + dPsi)
        val eq = Astro.equatorial(lambda, g[1], Astro.obliquity(t) + dEps)
        return Place3(lambda, g[1], eq[0], eq[1], g[2])
    }

    /** The horizontal parallax (degrees) at [distance] km: how much lower the moon stands seen from the ground. */
    fun parallax(distance: Double): Double = asin(6378.14 / distance) / DEG
}

/** The moon's phase and its days, at a moment: the sun and the moon seen from the earth. */
object Lunar {
    /** The mean synodic month (days). */
    const val SYNODIC = 29.530588853

    /** The moon's elongation from the sun along the ecliptic at [jdUt], 0 at new moon .. 180 at full .. 360. */
    fun elongation(jdUt: Double): Double {
        val t = Astro.centuries(jdUt)
        return norm(MoonPos.at(t).lambda - SunPos.at(t).lambda)
    }

    /**
     * The share of the moon's disc lit (Meeus 48.1–48.3): 0 new .. 1 full, from the phase angle (the sun and the earth
     * seen from the moon).
     */
    fun illuminated(jdUt: Double): Double {
        val t = Astro.centuries(jdUt)
        val m = MoonPos.at(t); val s = SunPos.at(t)
        val cosPsi = cosd(m.beta) * cosd(m.lambda - s.lambda)
        val psi = kotlin.math.acos(cosPsi.coerceIn(-1.0, 1.0))
        val r = s.distance * 149_597_870.7
        val i = atan2(r * sin(psi), m.distance - r * cos(psi))
        return (1 + cos(i)) / 2
    }

    /** Waxing (from new to full): the elongation is under 180°. */
    fun waxing(jdUt: Double): Boolean = elongation(jdUt) < 180.0

    /**
     * The moment (Julian day, UT) the elongation next reaches [target] (0: new moon, 90: first quarter, 180: full, 270:
     * last quarter) after [jdUt], or before it with [backwards]; to about a minute.
     */
    fun next(jdUt: Double, target: Double, backwards: Boolean = false): Double {
        fun f(jd: Double) = signed(elongation(jd) - target)
        val step = if (backwards) -1.0 else 1.0
        var a = jdUt
        var fa = f(a)
        // a day at a time until it passes the target (the elongation grows about 12° a day)
        for (k in 0 until 40) {
            val b = a + step
            val fb = f(b)
            val crossed = if (backwards) fa >= 0 && fb < 0 && fa - fb < 90 else fa < 0 && fb >= 0 && fb - fa < 90
            if (crossed) {
                var lo = minOf(a, b); var hi = maxOf(a, b)
                repeat(22) { // to about two seconds
                    val mid = (lo + hi) / 2
                    if (f(mid) < 0) lo = mid else hi = mid
                }
                return (lo + hi) / 2
            }
            a = b; fa = fb
        }
        error("no moon phase $target within 40 days of $jdUt")
    }

    /** The moon's age at [jdUt]: days since the last new moon. */
    fun age(jdUt: Double): Double = jdUt - next(jdUt, 0.0, backwards = true)

    /** The phase of [jdUt] as the app names it: the eighth of the month around each quarter and between them. */
    fun phase(jdUt: Double): si.lanisce.lani.game.render.Moon.Phase {
        val e = elongation(jdUt)
        return si.lanisce.lani.game.render.Moon.Phase.entries[kotlin.math.floor(e / 45.0 + 0.5).toInt().mod(8)]
    }

    /** The moon's topocentric altitude and azimuth at [jdUt] from [place] (the parallax lowers it by up to a degree). */
    fun horizontal(jdUt: Double, place: SkyPlace): DoubleArray {
        val m = MoonPos.at(Astro.centuries(jdUt))
        val h = Astro.horizontal(m.ra, m.dec, place.lat, Astro.lst(jdUt, place.lon))
        val p = MoonPos.parallax(m.distance)
        return doubleArrayOf(h[0] - p * cosd(h[0]), h[1])
    }

    /**
     * When the moon rises and sets between [fromJd] and [toJd] (Julian days, UT; a local day) at [place]: its centre's
     * geocentric altitude reaches 0.7275 × parallax − 0.5667° (Meeus 15: the upper limb on the horizon, the air's
     * refraction and the parallax counted). Null for no rise, or no set, in that span.
     */
    fun riseSet(fromJd: Double, toJd: Double, place: SkyPlace): Pair<Double?, Double?> {
        fun f(jd: Double): Double {
            val m = MoonPos.at(Astro.centuries(jd))
            val alt = Astro.horizontal(m.ra, m.dec, place.lat, Astro.lst(jd, place.lon))[0]
            return alt - (0.7275 * MoonPos.parallax(m.distance) - 0.5667)
        }
        var rise: Double? = null; var set: Double? = null
        val step = 1.0 / 48 // half an hour: the moon never rises and sets within it this far from the poles
        var a = fromJd
        var fa = f(a)
        while (a < toJd && (rise == null || set == null)) {
            val b = minOf(a + step, toJd)
            val fb = f(b)
            if ((fa < 0) != (fb < 0)) {
                var lo = a; var hi = b
                repeat(16) { // to about a second
                    val mid = (lo + hi) / 2
                    if ((f(mid) < 0) == (fa < 0)) lo = mid else hi = mid
                }
                if (fa < 0) { if (rise == null) rise = (lo + hi) / 2 } else if (set == null) set = (lo + hi) / 2
            }
            a = b; fa = fb
        }
        return rise to set
    }
}
