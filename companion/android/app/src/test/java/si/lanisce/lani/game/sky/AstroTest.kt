package si.lanisce.lani.game.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Moon
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * The sky's arithmetic against published values: Meeus's worked examples ("Astronomical Algorithms", 2nd ed.), the
 * U.S. Naval Observatory's table of the moon's phases for 2026 and its rise and set times and altitudes and azimuths
 * for the Primorska village (45.99° N, 13.75° E; aa.usno.navy.mil, fetched 2026-09-27).
 */
class AstroTest {
    private fun utc(iso: String): Long = LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun jd(iso: String): Double = Astro.jd(utc(iso))
    private val village = SkyPlace(45.99, 13.75)

    /** Degrees apart around the circle. */
    private fun off(a: Double, b: Double) = abs(Astro.signed(a - b))

    @Test fun `sidereal time, Meeus 12a`() {
        // 1987 April 10, 0h UT: 13h10m46.3668s
        assertEquals(197.693195, Astro.gmst(2446895.5), 0.00001)
    }

    @Test fun `the sun, Meeus 25a`() {
        // 1992 October 13, 0h TD
        val s = SunPos.at((2448908.5 - Astro.J2000) / 36525.0)
        assertEquals(199.90895, s.lambda, 0.0005)
        assertEquals(198.38083, s.ra, 0.001)
        assertEquals(-7.78507, s.dec, 0.001)
        assertEquals(0.99766, s.distance, 0.00001)
    }

    @Test fun `the moon, Meeus 47a`() {
        // 1992 April 12, 0h TD: all of tables 47.A and 47.B
        val t = (2448724.5 - Astro.J2000) / 36525.0
        val g = MoonPos.geometric(t)
        assertEquals(133.162655, g[0], 0.00001)
        assertEquals(-3.229126, g[1], 0.00001)
        assertEquals(368409.7, g[2], 0.1)
        assertEquals(0.991990, MoonPos.parallax(g[2]), 0.00001)
        val m = MoonPos.at(t) // with the main terms of the nutation only: to about a second of arc
        assertEquals(134.688470, m.ra, 0.0005)
        assertEquals(13.768368, m.dec, 0.0005)
    }

    @Test fun `the moon's lit share, Meeus 48a`() {
        // 1992 April 12, 0h TD: 0.6786 (0.68)
        assertEquals(0.6786, Lunar.illuminated(2448724.5 - 58.7 / 86400), 0.0005)
    }

    // the U.S. Naval Observatory's phases of the moon for 2026 (UT, to the minute)
    private val full2026 = listOf(
        "2026-01-03T10:03", "2026-02-01T22:09", "2026-03-03T11:38", "2026-04-02T02:12", "2026-05-01T17:23", "2026-05-31T08:45",
        "2026-06-29T23:56", "2026-07-29T14:36", "2026-08-28T04:18", "2026-09-26T16:49", "2026-10-26T04:12", "2026-11-24T14:53",
        "2026-12-24T01:28",
    )
    private val new2026 = listOf(
        "2026-01-18T19:52", "2026-02-17T12:01", "2026-03-19T01:23", "2026-04-17T11:52", "2026-05-16T20:01", "2026-06-15T02:54",
        "2026-07-14T09:43", "2026-08-12T17:37", "2026-09-11T03:27", "2026-10-10T15:50", "2026-11-09T07:02", "2026-12-09T00:52",
    )
    private val firstQuarter2026 = listOf("2026-01-26T04:47", "2026-04-24T02:32", "2026-09-18T20:44", "2026-12-17T05:42")
    private val lastQuarter2026 = listOf("2026-01-10T15:48", "2026-06-08T10:00", "2026-10-03T13:25", "2026-12-30T18:59")

    /** Minutes between the phase found from three days before and the table's. */
    private fun minutesOff(iso: String, target: Double): Double {
        val found = Lunar.next(jd(iso) - 3.0, target)
        return (found - jd(iso)) * 24 * 60
    }

    @Test fun `the phases of 2026 fall within two minutes of the Naval Observatory's`() {
        var worst = 0.0
        for ((dates, target) in listOf(full2026 to 180.0, new2026 to 0.0, firstQuarter2026 to 90.0, lastQuarter2026 to 270.0)) {
            for (d in dates) {
                val m = minutesOff(d, target)
                worst = maxOf(worst, abs(m))
                assertTrue("$target° at $d: ${"%.1f".format(m)} min off", abs(m) <= 2.0)
            }
        }
        println("the 2026 phases: at most %.2f minutes off the USNO table (which rounds to the minute)".format(worst))
    }

    @Test fun `full is full, new is new, and the age counts from the new moon`() {
        for (d in full2026) {
            assertTrue("$d lit ${Lunar.illuminated(jd(d))}", Lunar.illuminated(jd(d)) > 0.99)
            assertEquals(d, Moon.Phase.FULL, Lunar.phase(jd(d)))
        }
        for (d in new2026) {
            assertTrue("$d lit ${Lunar.illuminated(jd(d))}", Lunar.illuminated(jd(d)) < 0.01)
            assertEquals(d, Moon.Phase.NEW, Lunar.phase(jd(d)))
        }
        // 2026-09-27 12:00 UT: the new moon was 2026-09-11 03:27, 16.4 days before; waning, just past full (USNO: 99 %)
        val now = jd("2026-09-27T12:00")
        assertEquals(16.36, Lunar.age(now), 0.01)
        assertTrue(!Lunar.waxing(now))
        assertEquals(Moon.Phase.FULL, Lunar.phase(now))
        assertEquals(0.99, Lunar.illuminated(now), 0.01)
        // the next full and new moons after it
        assertEquals(jd("2026-10-26T04:12"), Lunar.next(now, 180.0), 2.0 / 1440)
        assertEquals(jd("2026-10-10T15:50"), Lunar.next(now, 0.0), 2.0 / 1440)
    }

    /** The USNO's moonrise and moonset over the village (UT), each day from 0h to 24h UT. */
    private val riseSet = listOf(
        Triple("2026-09-27", "16:54", "05:46"),
        Triple("2026-01-03", "15:22", "07:12"),
        Triple("2026-06-15", "02:52", "20:00"),
        Triple("2026-12-24", "15:41", "07:26"),
        Triple("2026-03-20", "05:28", "19:24"),
    )

    @Test fun `moonrise and moonset within three minutes of the Naval Observatory's`() {
        var worst = 0.0
        for ((day, rise, set) in riseSet) {
            val from = jd("${day}T00:00")
            val (r, s) = Lunar.riseSet(from, from + 1, village)
            val dr = (r!! - jd("${day}T$rise")) * 1440
            val ds = (s!! - jd("${day}T$set")) * 1440
            worst = maxOf(worst, abs(dr), abs(ds))
            assertTrue("$day: rises ${"%.1f".format(dr)} min off", abs(dr) <= 3.0)
            assertTrue("$day: sets ${"%.1f".format(ds)} min off", abs(ds) <= 3.0)
        }
        println("moonrise and moonset: at most %.2f minutes off the USNO's (which rounds to the minute)".format(worst))
    }

    /** A star's altitude and azimuth (no refraction), from the catalog carried to the date. */
    private fun star(proper: String, iso: String): DoubleArray {
        val s = checkNotNull(Stars.named(proper)) { proper }
        val j = jd(iso)
        val eq = Astro.precess(s.ra, s.dec, Astro.centuries(j))
        return Astro.horizontal(eq[0], eq[1], village.lat, Astro.lst(j, village.lon))
    }

    @Test fun `stars stand where the Naval Observatory has them, to a twentieth of a degree`() {
        // celestial navigation's Hc and Zn at the village, 2026-09-27 20:00 UT and 2026-01-10 22:00 UT
        val want = listOf(
            Triple("Polaris", "2026-09-27T20:00", doubleArrayOf(46.022775, 0.90114)),
            Triple("Vega", "2026-09-27T20:00", doubleArrayOf(59.307917, 271.342141)),
            Triple("Altair", "2026-09-27T20:00", doubleArrayOf(48.295212, 214.372839)),
            Triple("Deneb", "2026-09-27T20:00", doubleArrayOf(83.142594, 268.420001)),
            Triple("Capella", "2026-09-27T20:00", doubleArrayOf(16.348056, 39.128784)),
            Triple("Arcturus", "2026-09-27T20:00", doubleArrayOf(2.978054, 294.610158)),
            Triple("Betelgeuse", "2026-01-10T22:00", doubleArrayOf(51.177963, 187.978707)),
            Triple("Sirius", "2026-01-10T22:00", doubleArrayOf(26.901392, 172.057063)),
            Triple("Rigel", "2026-01-10T22:00", doubleArrayOf(34.147767, 198.315586)),
            Triple("Aldebaran", "2026-01-10T22:00", doubleArrayOf(54.040284, 223.263222)),
            Triple("Regulus", "2026-01-10T22:00", doubleArrayOf(30.350431, 105.305955)),
            Triple("Dubhe", "2026-01-10T22:00", doubleArrayOf(47.21839, 41.79394)),
        )
        var worst = 0.0
        for ((name, at, v) in want) {
            val h = star(name, at)
            worst = maxOf(worst, abs(h[0] - v[0]), off(h[1], v[1]) * Astro.cosd(v[0]))
            assertEquals("$name altitude", v[0], h[0], 0.05)
            assertTrue("$name azimuth ${h[1]} vs ${v[1]}", off(h[1], v[1]) * Astro.cosd(v[0]) <= 0.05)
        }
        println("stars: at most %.3f° off the USNO's".format(worst))
    }

    @Test fun `Polaris stands about as high as the village lies north`() {
        for (place in listOf(village, SkyPlace(54.53, -3.10), SkyPlace(46.59, 13.62))) {
            for (h in 0 until 24 step 3) {
                val j = jd("2026-09-27T%02d:00".format(h))
                val s = Stars.named("Polaris")!!
                val eq = Astro.precess(s.ra, s.dec, Astro.centuries(j))
                val alt = Astro.horizontal(eq[0], eq[1], place.lat, Astro.lst(j, place.lon))[0]
                assertEquals("Polaris at ${place.lat}° N, $h h", place.lat, alt, 0.8)
            }
        }
    }

    @Test fun `the moon and the planets stand where the Naval Observatory has them`() {
        fun geocentric(p: Place3, iso: String) = Astro.horizontal(p.ra, p.dec, village.lat, Astro.lst(jd(iso), village.lon))
        val at = "2026-09-27T20:00"
        val m = geocentric(MoonPos.at(Astro.centuries(jd(at))), at)
        assertEquals(31.502388, m[0], 0.03)
        assertEquals(107.932341, m[1], 0.03)
        val saturn = geocentric(Planet.SATURN.at(Astro.centuries(jd(at))).let { Place3(0.0, 0.0, it.ra, it.dec, 0.0) }, at)
        assertEquals(27.322774, saturn[0], 0.15)
        assertEquals(118.240192, saturn[1], 0.15)
        val jan = "2026-01-10T22:00"
        val jupiter = geocentric(Planet.JUPITER.at(Astro.centuries(jd(jan))).let { Place3(0.0, 0.0, it.ra, it.dec, 0.0) }, jan)
        assertEquals(62.274225, jupiter[0], 0.15)
        assertEquals(143.175785, jupiter[1], 0.15)
        println("moon %.3f° %.3f°, Saturn %.3f° %.3f°, Jupiter %.3f° %.3f° off".format(
            abs(m[0] - 31.502388), off(m[1], 107.932341), abs(saturn[0] - 27.322774), off(saturn[1], 118.240192),
            abs(jupiter[0] - 62.274225), off(jupiter[1], 143.175785)))
    }

    @Test fun `Jupiter opposes the sun on 10 January 2026, Venus is farthest east on 15 August`() {
        fun east(p: Planet, iso: String): Double {
            val t = Astro.centuries(jd(iso))
            return p.at(t).east(SunPos.at(t).lambda)
        }
        assertTrue("Jupiter ${east(Planet.JUPITER, "2026-01-10T12:00")}", abs(east(Planet.JUPITER, "2026-01-10T12:00")) > 178.0)
        val venus = east(Planet.VENUS, "2026-08-15T12:00")
        assertEquals(45.9, venus, 1.0)
        // the evening star then; the morning star after the inferior conjunction (24 October 2026)
        assertTrue(east(Planet.VENUS, "2026-12-01T12:00") < 0)
        // Venus outshines Jupiter, Jupiter Saturn
        val t = Astro.centuries(jd("2026-08-15T12:00"))
        assertTrue(Planet.VENUS.at(t).mag < Planet.JUPITER.at(t).mag && Planet.JUPITER.at(t).mag < Planet.SATURN.at(t).mag)
    }

    @Test fun `the catalog has the bright stars and every figure's stars`() {
        val stars = Stars.all
        assertTrue("${stars.size} stars", stars.size in 700..800)
        assertTrue(stars.all { it.mag <= 4.5f || Stars.figures.any { f -> f.stars.contains(Stars.indexOf(it.hr)) } })
        assertTrue(stars.all { it.dec >= -50.0 || Stars.figures.any { f -> f.stars.contains(Stars.indexOf(it.hr)) } })
        assertEquals(-1.46f, Stars.named("Sirius")!!.mag, 0.01f)
        assertEquals(21, Stars.figures.size)
        for (f in Stars.figures) {
            assertTrue(f.id, f.stars.isNotEmpty() && f.stars.all { it in stars.indices })
            assertTrue(f.id, f.lines.size % 2 == 0)
        }
        assertTrue(Stars.figure(Stars.PLEIADES)!!.stars.size >= 6)
        // Orion: Betelgeuse and Rigel among its stars
        val ori = Stars.figure("ori")!!
        assertTrue(ori.stars.contains(Stars.indexOf(Stars.named("Betelgeuse")!!.hr)) && ori.stars.contains(Stars.indexOf(Stars.named("Rigel")!!.hr)))
    }

    @Test fun `the galactic plane runs through Sagittarius and Cygnus, and the pole is far from it`() {
        // the galactic centre (Sgr A*) and Deneb near the plane; the north galactic pole in Coma Berenices
        val centre = Astro.galactic(266.405, -28.936)
        assertTrue(abs(centre[1]) < 0.1 && off(centre[0], 0.0) < 0.1)
        assertTrue(abs(Astro.galactic(310.358, 45.280)[1]) < 5.0)
        assertEquals(90.0, Astro.galactic(192.859, 27.128)[1], 0.01)
    }

    @Test fun `from altitude and azimuth back to the sky's coordinates`() {
        val lst = 123.4
        for ((ra, dec) in listOf(10.0 to 20.0, 200.0 to -10.0, 300.0 to 60.0)) {
            val h = Astro.horizontal(ra, dec, village.lat, lst)
            val eq = Astro.equatorialOf(h[0], h[1], village.lat, lst)
            assertEquals(ra, eq[0], 1e-6)
            assertEquals(dec, eq[1], 1e-6)
        }
    }
}
