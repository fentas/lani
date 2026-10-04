package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.scene.DaySky
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.Sky
import si.lanisce.lani.game.sky.Meteors
import si.lanisce.lani.game.sky.SkyNow
import si.lanisce.lani.game.sky.SkyPlace
import si.lanisce.lani.game.sky.SkyTap
import si.lanisce.lani.game.sky.Stars
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * The real night sky in the village and the outdoor scenes ([NightSky]): the stars where they stand over the Primorska village,
 * the moon where it is, what hides them (clouds, the day), and what a tap on them finds. PNG previews for review in
 * build/sky-snapshots: a clear night under a nearly full moon, a moonless night with the Milky Way, a winter night with
 * Orion, an overcast night, the Perseids, and two vistas.
 */
class NightSkyRenderTest {
    private val dir = File("build/sky-snapshots").apply { mkdirs() }
    private val place = SkyPlace(45.99, 13.75)

    private fun utc(iso: String): Long = LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val village = GameState(
        seed = 42, age = Age.VAS, villagers = 8, morale = 70, fire = 70,
        buildings = listOf(BuildingType.FIELD, BuildingType.HUT, BuildingType.WELL, BuildingType.KOZOLEC, BuildingType.HOUSE, BuildingType.LIPA, BuildingType.CHURCH, BuildingType.BEEHIVE)
            .mapIndexed { i, t -> Building("b$i", t, i) },
    )

    /** A portrait phone's village (1080 × 2340 at 3 ×) at local [hour] (CEST or CET as [utcOffset] says) of the UTC moment [iso]. */
    private fun villageAt(iso: String, hour: Float, month: Int, state: GameState = village, wall: Long = utc(iso), mark: String? = null): Pair<PixelCanvas, VillageRenderer> {
        val fit = SceneFit.town(1080, 2340)
        val c = PixelCanvas(fit.width, fit.height)
        val r = VillageRenderer()
        val now = SkyNow.exact(utc(iso), place)
        r.render(c, state, Frame(time = 2.0, hour = hour, month = month, today = iso.take(10), moon = now.moonAge, skyNow = now, wall = wall, skyMark = mark))
        return c to r
    }

    private fun write(c: PixelCanvas, name: String, scale: Int = 2, rows: Int = c.height) {
        val img = BufferedImage(c.width * scale, rows * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until rows * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    /** The star with proper name [name] among the last frame's tappable points (nominal x, y), or null. */
    private fun point(r: VillageRenderer, name: String): FloatArray? {
        val i = Stars.all.indexOfFirst { it.proper == name }
        return r.skyTargets.points().firstOrNull { it.first == i }?.second
    }

    @Test fun `a clear night under a nearly full moon, the moon rising in the east and the brighter stars out`() {
        // 27 September 2026, 21:00 CEST: the moon a day past full, low in the east; the Summer Triangle in the west
        val (c, r) = villageAt("2026-09-27T19:00", 21f, 9)
        write(c, "01-village-full-moon")
        val moon = r.skyTargets.moonAt()
        assertNotNull("the moon is drawn and tappable", moon)
        assertEquals(SkyTap.Moon, r.skyAt(moon!![0], moon[1], 3f, utc("2026-09-27T19:00")))
        // low in the east: on the left, near the horizon
        assertTrue("on the left: ${moon[0]}", moon[0] < c.width * 0.3f)
        // Altair is in the south-west, high: to the right of the middle, tappable, of Aquila
        val altair = checkNotNull(point(r, "Altair")) { "Altair shows" }
        assertTrue(altair[0] > c.width / 2f)
        assertEquals(SkyTap.Star(Stars.named("Altair")!!.hr, "aql"), r.skyAt(altair[0] + 1f, altair[1], 3f, 0L))
        // the Pole Star is behind us, in the north
        assertNull(point(r, "Polaris"))
    }

    @Test fun `a moonless night shows the Milky Way and the fainter stars`() {
        // 10 October 2026, the new moon: 21:30 CEST
        val (c, r) = villageAt("2026-10-10T19:30", 21.5f, 10)
        write(c, "02-village-new-moon")
        assertNull("no moon", r.skyTargets.moonAt())
        val (full, rf) = villageAt("2026-09-27T19:30", 21.5f, 9)
        // more stars without the moon's light (a nearly full moon washes the faint ones out)
        assertTrue("${r.skyTargets.points().size} stars vs ${rf.skyTargets.points().size} under the full moon", r.skyTargets.points().size > rf.skyTargets.points().size)
        assertNotEquals(full.pixels.toList(), c.pixels.toList())
    }

    @Test fun `a winter night with Orion in the south, a tap on Betelgeuse finds Orion, a tap on its belt line too`() {
        // 15 January 2027, 22:00 CET
        val (c, r) = villageAt("2027-01-15T21:00", 22f, 1)
        write(c, "03-village-winter-orion")
        val betelgeuse = checkNotNull(point(r, "Betelgeuse")) { "Betelgeuse shows in January" }
        assertEquals(SkyTap.Star(Stars.named("Betelgeuse")!!.hr, "ori"), r.skyAt(betelgeuse[0], betelgeuse[1], 3f, 0L))
        // between Alnilam and Alnitak, off both stars: the line
        val a = checkNotNull(point(r, "Alnilam")); val b = checkNotNull(point(r, "Alnitak"))
        val mid = floatArrayOf((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        if (abs(a[0] - b[0]) + abs(a[1] - b[1]) > 8f) assertEquals(SkyTap.Figure("ori"), r.skyAt(mid[0], mid[1], 1.5f, 0L))
        // Sirius, the brightest, low in the south-south-east
        assertNotNull(point(r, "Sirius"))
        // marked, the figure is brighter
        val (marked, _) = villageAt("2027-01-15T21:00", 22f, 1, mark = "ori")
        write(marked, "04-village-winter-orion-marked")
        assertNotEquals(c.pixels.toList(), marked.pixels.toList())
    }

    @Test fun `an overcast night hides the stars, and by day there are none`() {
        val grey = village.copy(sky = DaySky("2026-10-10", "x", Sky(gloom = 0.8f)))
        val (c, r) = villageAt("2026-10-10T19:30", 21.5f, 10, state = grey)
        write(c, "05-village-overcast")
        assertTrue(r.skyTargets.points().isEmpty())
        val (_, day) = villageAt("2026-10-10T10:00", 12f, 10)
        assertTrue(day.skyTargets.points().isEmpty())
        assertNull(day.skyAt(180f, 50f, 4f, 0L))
    }

    @Test fun `in the Perseids a shooting star streaks over the village, and a tap catches it even just after`() {
        // 12 August 2026 (the new moon), from 23:30 CEST: the first one that shows in the village's view
        val start = utc("2026-08-12T21:30")
        var found: Triple<PixelCanvas, VillageRenderer, si.lanisce.lani.game.sky.Meteor>? = null
        var slot = Math.floorDiv(start, Meteors.SLOT_MS)
        while (found == null && slot < Math.floorDiv(start, Meteors.SLOT_MS) + 360) {
            val m = Meteors.inSlot(slot++, place) ?: continue
            val (c, r) = villageAt("2026-08-12T21:30", 23.5f, 8, wall = m.start + m.duration / 2)
            // one that starts in the picture, so it shows all the way
            if (r.skyTargets.meteors().any { (it, w) -> it.id == m.id && w[0] in 0f..c.width.toFloat() && w[1] in 0f..c.height / 3f }) found = Triple(c, r, m)
        }
        val (c, r, m) = checkNotNull(found) { "a shooting star over the village within an hour of the Perseids" }
        write(c, "06-village-perseids")
        val mid = m.start + m.duration / 2
        val way = r.skyTargets.meteors().first { it.first.id == m.id }.second
        val x = (way[0] + way[2]) / 2; val y = (way[1] + way[3]) / 2
        assertEquals(SkyTap.Meteor(m.id, m.shower), r.skyAt(x, y, 3f, mid))
        // a second after it's gone, still; long after, no more
        assertEquals(SkyTap.Meteor(m.id, m.shower), r.skyAt(x, y, 3f, m.end + 1000))
        assertTrue(r.skyAt(x, y, 3f, m.end + Meteors.GRACE_MS + 1000) !is SkyTap.Meteor)
        // the streak shows mid-flight: the picture differs from the one just before it began
        val (before, _) = villageAt("2026-08-12T21:30", 23.5f, 8, wall = m.start - 1)
        assertNotEquals(before.pixels.toList(), c.pixels.toList())
    }

    @Test fun `shooting stars come more often in the showers, from the radiant, the same for everyone`() {
        fun count(fromIso: String, hours: Int): Pair<Int, Int> {
            var n = 0; var shower = 0
            val from = Math.floorDiv(utc(fromIso), Meteors.SLOT_MS)
            for (slot in from until from + hours * 360) Meteors.inSlot(slot, place)?.let { n++; if (it.shower != null) shower++ }
            return n to shower
        }
        val quiet = count("2026-10-01T19:00", 8)
        val perseids = count("2026-08-12T19:00", 8)
        println("shooting stars in 8 hours over the whole sky: ${quiet.first} on 1 October, ${perseids.first} (${perseids.second} Perseids) on 12 August")
        assertTrue("a few an hour on a plain night: $quiet", quiet.first in 30..100 && quiet.second == 0)
        assertTrue("many more in the Perseids: $perseids", perseids.first > quiet.first * 3 && perseids.second > perseids.first / 2)
        // the same slot, the same shooting star
        assertEquals(Meteors.inSlot(123456789L, place), Meteors.inSlot(123456789L, place))
        // a Perseid moves away from the radiant
        val slot = (Math.floorDiv(utc("2026-08-12T22:00"), Meteors.SLOT_MS) until Math.floorDiv(utc("2026-08-13T02:00"), Meteors.SLOT_MS))
            .firstNotNullOf { s -> Meteors.inSlot(s, place)?.takeIf { it.shower == "perseids" } }
        val jd = si.lanisce.lani.game.sky.Astro.jd(slot.start)
        val rad = Meteors.radiant(Meteors.shower("perseids")!!, jd, place)
        val d0 = si.lanisce.lani.game.sky.Astro.separation(slot.az0, slot.alt0, rad[1], rad[0])
        val d1 = si.lanisce.lani.game.sky.Astro.separation(slot.az1, slot.alt1, rad[1], rad[0])
        assertTrue("away from the radiant: $d0 → $d1", d1 > d0)
    }

    private fun scene(art: String, iso: String, hour: Float, month: Int, k: Int = 1): Pair<PixelCanvas, si.lanisce.lani.game.scene.ScenePainter> {
        val now = SkyNow.exact(utc(iso), place)
        val painter = ScenePainters.create(art)
        val c = PixelCanvas(270 * k, 297 * k)
        painter.render(c, SceneFrame(time = 2.0, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), moon = now.moonAge, skyNow = now, wall = utc(iso)), Lens(k, 0, 0, 270, 297))
        return c to painter
    }

    @Test fun `a vista turns to its moon, which stands in its column and can be tapped`() {
        // the field under the nearly full moon; the Alps on a moonless night, toward the Plough
        val (field, fp) = scene("field", "2026-09-27T21:30", 23.5f, 9)
        write(field, "07-field-full-moon", scale = 3)
        val (alps, ap) = scene("alps", "2026-10-10T19:30", 21.5f, 10)
        write(alps, "08-alps-new-moon", scale = 3)
        // the field's moon: in the painter's column (ox + 196), tappable there
        val ox = (270 - 240) / 2
        val moonCol = ox + 196f
        val found = (0 until 297).firstNotNullOfOrNull { y -> fp.skyAt(moonCol, y.toFloat(), 1f, 0L)?.takeIf { it == SkyTap.Moon }?.let { y } }
        assertNotNull("the moon in its column", found)
        // the Alps look north-north-west: the Pole Star is there to tap, of the Little Dipper
        val polaris = Stars.named("Polaris")!!.hr
        val hit = (0 until 270).flatMap { x -> (0 until 150).map { y -> x to y } }.firstNotNullOfOrNull { (x, y) ->
            ap.skyAt(x.toFloat(), y.toFloat(), 0.8f, 0L)?.takeIf { it is SkyTap.Star && it.hr == polaris }
        }
        assertNotNull("the Pole Star over the Alps", hit)
        assertEquals("umi", (hit as SkyTap.Star).figure)
        // closer up, the same sky, finer
        val (fine, _) = scene("alps", "2026-10-10T19:30", 21.5f, 10, k = 2)
        write(fine, "09-alps-new-moon-d2", scale = 2)
    }

    @Test fun `tonight's sky for TalkBack lists what the picture shows, each once, and nothing by day or under clouds`() {
        // January: Orion in the south, and Sirius, the brightest, each once
        val (_, r) = villageAt("2027-01-15T21:00", 22f, 1)
        val up = r.skyUp()
        assertEquals(up.toSet().size, up.size)
        assertTrue(up.toString(), SkyTap.Figure("ori") in up)
        assertTrue(up.toString(), up.any { it is SkyTap.Star && it.hr == Stars.named("Sirius")!!.hr })
        // what the list offers is what a tap would find: every star of it is drawn
        for (t in up.filterIsInstance<SkyTap.Star>()) assertTrue(r.skyTargets.points().any { (i, _) -> i >= 0 && Stars.all[i].hr == t.hr })
        // under the nearly full moon, the moon first
        val (_, full) = villageAt("2026-09-27T19:30", 21.5f, 9)
        assertEquals(SkyTap.Moon, full.skyUp().first())
        // an overcast night and the day: no stars, figures or planets to list
        val grey = village.copy(sky = DaySky("2026-10-10", "x", Sky(gloom = 0.8f)))
        assertTrue(villageAt("2026-10-10T19:30", 21.5f, 10, state = grey).second.skyUp().none { it is SkyTap.Star || it is SkyTap.Figure || it is SkyTap.Planet })
        assertTrue(villageAt("2026-10-10T10:00", 12f, 10).second.skyUp().none { it is SkyTap.Star || it is SkyTap.Figure || it is SkyTap.Planet })
        // an outdoor scene shows the sky (the Alps toward the Plough: the Little Dipper), a kitchen none
        val (_, alps) = scene("alps", "2026-10-10T19:30", 21.5f, 10)
        assertTrue(alps.showsSky)
        assertTrue(alps.skyUp().toString(), SkyTap.Figure("umi") in alps.skyUp())
        val (_, kitchen) = scene("kitchen", "2026-10-10T19:30", 21.5f, 10)
        assertTrue(!kitchen.showsSky && kitchen.skyUp().isEmpty())
    }

    @Test fun `without the real sky a picture is drawn as before`() {
        val fit = SceneFit.town(1080, 2340)
        val a = PixelCanvas(fit.width, fit.height); val b = PixelCanvas(fit.width, fit.height)
        VillageRenderer().render(a, village, Frame(time = 2.0, hour = 22f, month = 9, moon = 10f))
        VillageRenderer().render(b, village, Frame(time = 2.0, hour = 22f, month = 9, moon = 10f, wall = 5L, skyMark = "ori"))
        assertEquals(a.pixels.toList(), b.pixels.toList())
    }
}
