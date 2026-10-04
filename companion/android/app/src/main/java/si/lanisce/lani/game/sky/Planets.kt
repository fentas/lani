package si.lanisce.lani.game.sky

import si.lanisce.lani.game.sky.Astro.DEG
import si.lanisce.lani.game.sky.Astro.norm
import si.lanisce.lani.game.sky.Astro.signed
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The bright planets an eye finds in the village's sky (Venus, Mars, Jupiter, Saturn), from E. M. Standish's Keplerian
 * elements for 1800–2050 (JPL, "Approximate Positions of the Planets", table 1): good to a few minutes of arc for
 * Venus and Mars and a tenth of a degree or so for Jupiter and Saturn, plenty for pixels.
 */
enum class Planet(
    val id: String,
    // a (au), e, I, L, ϖ (longitude of perihelion), Ω, each with its rate per century
    private val el: DoubleArray,
    private val rate: DoubleArray,
    /** Its absolute magnitude (V at 1 au from the sun and the earth), and how it dims with the phase angle (mag/°). */
    private val h: Double,
    private val phaseCoef: Double,
) {
    VENUS(
        "venus",
        doubleArrayOf(0.72333566, 0.00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255),
        doubleArrayOf(0.00000390, -0.00004107, -0.00078890, 58517.81538729, 0.00268329, -0.27769418),
        -4.40, 0.013,
    ),
    MARS(
        "mars",
        doubleArrayOf(1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891),
        doubleArrayOf(0.00001847, 0.00007882, -0.00813131, 19140.30268499, 0.44441088, -0.29257343),
        -1.52, 0.016,
    ),
    JUPITER(
        "jupiter",
        doubleArrayOf(5.20288700, 0.04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909),
        doubleArrayOf(-0.00011607, -0.00013253, -0.00183714, 3034.74612775, 0.21252668, 0.20469106),
        -9.40, 0.005,
    ),
    SATURN(
        "saturn",
        doubleArrayOf(9.53667594, 0.05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448),
        doubleArrayOf(-0.00125060, -0.00050991, 0.00193609, 1222.49362201, -0.41897216, -0.28867794),
        -8.88, 0.044,
    );

    /** Heliocentric ecliptic x, y, z (au, J2000) at [t] centuries of TT. */
    fun helio(t: Double): DoubleArray = orbit(el, rate, t)

    /** Where it is at [t] (TT centuries): its place of date, its distances and brightness. */
    fun at(t: Double): PlanetPlace {
        val p = helio(t)
        val e = orbit(EARTH, EARTH_RATE, t)
        val x = p[0] - e[0]; val y = p[1] - e[1]; val z = p[2] - e[2]
        val delta = sqrt(x * x + y * y + z * z)
        val r = sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2])
        val rE = sqrt(e[0] * e[0] + e[1] * e[1] + e[2] * e[2])
        // J2000 ecliptic → equator of J2000, then to the equinox of date
        val eps = 23.43928 * DEG
        val xq = x; val yq = cos(eps) * y - sin(eps) * z; val zq = sin(eps) * y + cos(eps) * z
        val ra0 = norm(atan2(yq, xq) / DEG); val dec0 = asin(zq / delta) / DEG
        val eq = Astro.precess(ra0, dec0, t)
        val lambda = norm(atan2(y, x) / DEG + 1.3970 * t) // J2000 ecliptic longitude, carried to the equinox of date
        // the phase angle (sun–planet–earth), and the magnitude from it
        val cosI = ((r * r + delta * delta - rE * rE) / (2 * r * delta)).coerceIn(-1.0, 1.0)
        val phase = kotlin.math.acos(cosI) / DEG
        val mag = h + 5 * log10(r * delta) + phaseCoef * phase
        return PlanetPlace(this, eq[0], eq[1], lambda, delta, mag)
    }

    companion object {
        private val EARTH = doubleArrayOf(1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0)
        private val EARTH_RATE = doubleArrayOf(0.00000562, -0.00004392, -0.01294668, 35999.37244981, 0.32327364, 0.0)

        fun of(id: String): Planet? = entries.firstOrNull { it.id == id }

        private fun orbit(el: DoubleArray, rate: DoubleArray, t: Double): DoubleArray {
            val a = el[0] + rate[0] * t
            val e = el[1] + rate[1] * t
            val i = (el[2] + rate[2] * t) * DEG
            val l = el[3] + rate[3] * t
            val peri = el[4] + rate[4] * t
            val node = el[5] + rate[5] * t
            val w = (peri - node) * DEG; val om = node * DEG
            val m = signed(l - peri) * DEG
            // Kepler's equation, by Newton's method
            var ea = m + e * sin(m)
            repeat(8) { ea -= (ea - e * sin(ea) - m) / (1 - e * cos(ea)) }
            val xp = a * (cos(ea) - e); val yp = a * sqrt(1 - e * e) * sin(ea)
            val x = (cos(w) * cos(om) - sin(w) * sin(om) * cos(i)) * xp + (-sin(w) * cos(om) - cos(w) * sin(om) * cos(i)) * yp
            val y = (cos(w) * sin(om) + sin(w) * cos(om) * cos(i)) * xp + (-sin(w) * sin(om) + cos(w) * cos(om) * cos(i)) * yp
            val z = sin(w) * sin(i) * xp + cos(w) * sin(i) * yp
            return doubleArrayOf(x, y, z)
        }
    }
}

/**
 * A planet at a moment: right ascension and declination of date, ecliptic longitude of date ([lambda], to tell the
 * morning star from the evening star), the distance from the earth ([delta], au) and the magnitude ([mag]).
 */
data class PlanetPlace(val planet: Planet, val ra: Double, val dec: Double, val lambda: Double, val delta: Double, val mag: Double) {
    /** Degrees east of the sun (positive: it sets after the sun, an evening star; negative: a morning star). */
    fun east(sunLambda: Double): Double = signed(lambda - sunLambda)

    /** How far from the sun (degrees): close to it, a planet is lost in the glare. */
    fun fromSun(sunLambda: Double): Double = abs(east(sunLambda))
}
