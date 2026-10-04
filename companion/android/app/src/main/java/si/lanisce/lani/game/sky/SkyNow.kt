package si.lanisce.lani.game.sky

import si.lanisce.lani.game.render.Moon

/** A planet in the sky of a minute: where it stands (apparent altitude, azimuth), how bright, and how far east of the sun. */
data class PlanetNow(val planet: Planet, val alt: Double, val az: Double, val mag: Double, val east: Double) {
    /** Too close to the sun to be seen, in its glare. */
    val inGlare: Boolean get() = kotlin.math.abs(east) < GLARE

    companion object {
        /** Degrees from the sun inside which a planet is lost in the twilight. */
        const val GLARE = 12.0
    }
}

/**
 * The sky over [place] at one minute ([millis], epoch ms): where the sun, the moon, the planets and the bright stars
 * stand (apparent altitudes, the air's refraction counted, and azimuths from the north through the east; the moon's
 * seen from the ground, not the earth's centre), and the moon's phase. Made once a minute ([at]) and shared by every
 * picture drawn in it: the frames draw from it, nothing is computed per frame but the shooting stars.
 */
class SkyNow private constructor(val millis: Long, val place: SkyPlace) {
    val jd: Double = Astro.jd(millis)
    private val t = Astro.centuries(jd)
    val lst: Double = Astro.lst(jd, place.lon)

    private val sun = SunPos.at(t)
    val sunAlt: Double
    val sunAz: Double

    private val moon = MoonPos.at(t)
    val moonAlt: Double
    val moonAz: Double

    /** The moon's elongation from the sun: 0 new, 90 first quarter, 180 full, 270 last quarter. */
    val elongation: Double = Astro.norm(moon.lambda - sun.lambda)

    /** The share of the moon's disc lit, 0 .. 1. */
    val moonLit: Float = Lunar.illuminated(jd).toFloat()

    val waxing: Boolean get() = elongation < 180.0

    val phase: Moon.Phase = Moon.Phase.entries[kotlin.math.floor(elongation / 45.0 + 0.5).toInt().mod(8)]

    /**
     * The moon's age as [Moon] draws it: the elongation's share of the mean month (days), so the painted terminator
     * matches the real phase. The card's age (days since the new moon) is [Lunar.age].
     */
    val moonAge: Float = (elongation / 360.0 * Lunar.SYNODIC).toFloat()

    val planets: List<PlanetNow>

    /** The catalog's stars ([Stars.all], by index): apparent altitude and azimuth. */
    val starAlt: FloatArray
    val starAz: FloatArray

    /** The meteor shower of tonight, if one is on ([Meteors.tonight]). */
    val shower: Shower? = Meteors.tonight(jd)

    init {
        val s = Astro.horizontal(sun.ra, sun.dec, place.lat, lst)
        sunAlt = s[0] + Astro.refraction(s[0]); sunAz = s[1]
        val m = Astro.horizontal(moon.ra, moon.dec, place.lat, lst)
        val topo = m[0] - MoonPos.parallax(moon.distance) * Astro.cosd(m[0])
        moonAlt = topo + Astro.refraction(topo); moonAz = m[1]
        planets = Planet.entries.map { p ->
            val pp = p.at(t)
            val h = Astro.horizontal(pp.ra, pp.dec, place.lat, lst)
            PlanetNow(p, h[0] + Astro.refraction(h[0]), h[1], pp.mag, pp.east(sun.lambda))
        }
        val stars = Stars.all
        starAlt = FloatArray(stars.size); starAz = FloatArray(stars.size)
        for ((i, st) in stars.withIndex()) {
            val eq = Astro.precess(st.ra, st.dec, t)
            val h = Astro.horizontal(eq[0], eq[1], place.lat, lst)
            starAlt[i] = (h[0] + Astro.refraction(h[0])).toFloat(); starAz[i] = h[1].toFloat()
        }
    }

    /** The moon is up (its centre over the horizon) and not new: it is drawn. */
    val moonShows: Boolean get() = moonAlt > -0.3 && moonLit >= Moon.NEW_LIT

    /** The sun's altitude says: 0 by day, 1 in full night (the sun 18° down), between in the twilights. */
    val night: Float get() = ((-sunAlt - 0.0) / 18.0).coerceIn(0.0, 1.0).toFloat()

    companion object {
        private const val MINUTE = 60_000L

        @Volatile
        private var last: SkyNow? = null

        /** The sky over [place] at the minute of [epochMillis], made once per minute and place. */
        fun at(epochMillis: Long, place: SkyPlace): SkyNow {
            val minute = Math.floorDiv(epochMillis, MINUTE) * MINUTE
            last?.let { if (it.millis == minute && it.place == place) return it }
            return SkyNow(minute, place).also { last = it }
        }

        /** Tests and previews: the sky at exactly [epochMillis], not cached. */
        fun exact(epochMillis: Long, place: SkyPlace): SkyNow = SkyNow(epochMillis, place)
    }
}
