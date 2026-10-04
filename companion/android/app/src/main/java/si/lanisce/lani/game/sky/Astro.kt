package si.lanisce.lani.game.sky

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

// The real sky over the village (companion/GAME.md, "The night sky"): pure arithmetic, no network. The formulas are
// the low-precision ones of Jean Meeus, "Astronomical Algorithms" (2nd ed., 1998), chapter numbers in the comments;
// the planets are E. M. Standish's Keplerian elements (JPL, "Approximate Positions of the Planets", table 1).

/** Where a sky is seen from: a village's latitude and longitude (degrees, north and east positive). */
data class SkyPlace(val lat: Double, val lon: Double) {
    companion object {
        /** A village on the Trnovo plateau above Nova Gorica (the game's scenery): the sky of a village whose culture pack doesn't say. */
        val DEFAULT = SkyPlace(45.99, 13.75)
    }
}

/** Time, angles and the turns between the sky's coordinates. Angles are degrees. */
object Astro {
    const val DEG = PI / 180.0
    const val J2000 = 2451545.0
    private const val UNIX_EPOCH_JD = 2440587.5
    private const val MS_PER_DAY = 86_400_000.0

    /** TT − UT about 2026 (s): the moon moves half a second of arc in it. */
    const val DELTA_T = 69.2

    /** The Julian day (UT) of [epochMillis]. */
    fun jd(epochMillis: Long): Double = epochMillis / MS_PER_DAY + UNIX_EPOCH_JD

    /** Back from a Julian day (UT) to epoch milliseconds. */
    fun millis(jd: Double): Long = ((jd - UNIX_EPOCH_JD) * MS_PER_DAY).roundToLong()

    /** Julian centuries of terrestrial time since J2000 at the Julian day (UT) [jdUt]. */
    fun centuries(jdUt: Double): Double = (jdUt + DELTA_T / 86_400.0 - J2000) / 36_525.0

    /** [deg] in 0 until 360. */
    fun norm(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    /** [deg] in -180 until 180. */
    fun signed(deg: Double): Double = norm(deg + 180.0) - 180.0

    fun sind(d: Double) = sin(d * DEG)
    fun cosd(d: Double) = cos(d * DEG)

    /** Greenwich mean sidereal time at [jdUt] (Meeus 12.4). */
    fun gmst(jdUt: Double): Double {
        val t = (jdUt - J2000) / 36_525.0
        return norm(280.46061837 + 360.98564736629 * (jdUt - J2000) + 0.000387933 * t * t - t * t * t / 38_710_000.0)
    }

    /** The local sidereal time at [jdUt] at east longitude [lon]. */
    fun lst(jdUt: Double, lon: Double): Double = norm(gmst(jdUt) + lon)

    /** The mean obliquity of the ecliptic (Meeus 22.2) at [t] centuries. */
    fun obliquity(t: Double): Double = 23.439291111 - 0.013004167 * t - 1.639e-7 * t * t + 5.036e-7 * t * t * t

    /** Nutation in longitude and in obliquity at [t] centuries, the main terms (Meeus 22: to 0.5″ and 0.1″). */
    fun nutation(t: Double): DoubleArray {
        val om = (125.04452 - 1934.136261 * t) * DEG
        val l = (280.4665 + 36000.7698 * t) * DEG
        val lm = (218.3165 + 481267.8813 * t) * DEG
        val dPsi = (-17.20 * sin(om) - 1.32 * sin(2 * l) - 0.23 * sin(2 * lm) + 0.21 * sin(2 * om)) / 3600.0
        val dEps = (9.20 * cos(om) + 0.57 * cos(2 * l) + 0.10 * cos(2 * lm) - 0.09 * cos(2 * om)) / 3600.0
        return doubleArrayOf(dPsi, dEps)
    }

    /** Ecliptic longitude and latitude to right ascension and declination, at obliquity [eps] (Meeus 13.3, 13.4). */
    fun equatorial(lambda: Double, beta: Double, eps: Double): DoubleArray {
        val l = lambda * DEG; val b = beta * DEG; val e = eps * DEG
        val ra = atan2(sin(l) * cos(e) - tan(b) * sin(e), cos(l))
        val dec = asin((sin(b) * cos(e) + cos(b) * sin(e) * sin(l)).coerceIn(-1.0, 1.0))
        return doubleArrayOf(norm(ra / DEG), dec / DEG)
    }

    /** A J2000 right ascension and declination carried to the equinox of [t] centuries (Meeus 21.2–21.4). */
    fun precess(ra: Double, dec: Double, t: Double): DoubleArray {
        val zeta = (2306.2181 * t + 0.30188 * t * t + 0.017998 * t * t * t) / 3600.0
        val z = (2306.2181 * t + 1.09468 * t * t + 0.018203 * t * t * t) / 3600.0
        val theta = (2004.3109 * t - 0.42665 * t * t - 0.041833 * t * t * t) / 3600.0
        val d = dec * DEG; val a0 = (ra + zeta) * DEG; val th = theta * DEG
        val a = cos(d) * sin(a0)
        val b = cos(th) * cos(d) * cos(a0) - sin(th) * sin(d)
        val c = sin(th) * cos(d) * cos(a0) + cos(th) * sin(d)
        return doubleArrayOf(norm(atan2(a, b) / DEG + z), asin(c.coerceIn(-1.0, 1.0)) / DEG)
    }

    /**
     * Altitude and azimuth (from the north through the east) of right ascension [ra] and declination [dec] at latitude
     * [lat] and local sidereal time [lst] (Meeus 13.5, 13.6; his azimuth is from the south).
     */
    fun horizontal(ra: Double, dec: Double, lat: Double, lst: Double): DoubleArray {
        val h = (lst - ra) * DEG; val phi = lat * DEG; val d = dec * DEG
        val alt = asin((sin(phi) * sin(d) + cos(phi) * cos(d) * cos(h)).coerceIn(-1.0, 1.0))
        val az = atan2(sin(h), cos(h) * sin(phi) - tan(d) * cos(phi)) / DEG + 180.0
        return doubleArrayOf(alt / DEG, norm(az))
    }

    /** Back from altitude [alt] and azimuth [az] to right ascension and declination, at [lat] and [lst]. */
    fun equatorialOf(alt: Double, az: Double, lat: Double, lst: Double): DoubleArray {
        val a = alt * DEG; val z = az * DEG; val phi = lat * DEG
        val dec = asin((sin(phi) * sin(a) + cos(phi) * cos(a) * cos(z)).coerceIn(-1.0, 1.0))
        val h = atan2(-sin(z) * cos(a), cos(phi) * sin(a) - sin(phi) * cos(a) * cos(z))
        return doubleArrayOf(norm(lst - h / DEG), dec / DEG)
    }

    /** How much higher the air lifts what stands at true altitude [alt] (degrees; Sæmundsson, Meeus 16.4). */
    fun refraction(alt: Double): Double {
        if (alt < -1.0) return 0.0
        return 1.02 / tan((alt + 10.3 / (alt + 5.11)) * DEG) / 60.0
    }

    // the galaxy's north pole and the galactic longitude of the celestial pole (J2000)
    private const val GAL_RA = 192.85948
    private const val GAL_DEC = 27.12825
    private const val GAL_L_NCP = 122.93192

    /** Galactic longitude and latitude of a J2000 right ascension and declination: where the Milky Way lies. */
    fun galactic(ra: Double, dec: Double): DoubleArray {
        val d = dec * DEG; val dg = GAL_DEC * DEG; val da = (ra - GAL_RA) * DEG
        val b = asin((sin(d) * sin(dg) + cos(d) * cos(dg) * cos(da)).coerceIn(-1.0, 1.0))
        val l = GAL_L_NCP - atan2(cos(d) * sin(da), sin(d) * cos(dg) - cos(d) * sin(dg) * cos(da)) / DEG
        return doubleArrayOf(norm(l), b / DEG)
    }

    /** The angle between two points of the sky (degrees), given as (longitude-like, latitude-like) pairs. */
    fun separation(a1: Double, d1: Double, a2: Double, d2: Double): Double {
        val c = sind(d1) * sind(d2) + cosd(d1) * cosd(d2) * cosd(a1 - a2)
        return kotlin.math.acos(c.coerceIn(-1.0, 1.0)) / DEG
    }
}
