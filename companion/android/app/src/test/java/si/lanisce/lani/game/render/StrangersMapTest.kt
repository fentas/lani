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
import si.lanisce.lani.game.Surprise
import si.lanisce.lani.game.Surprises
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.villagers.Villager
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/**
 * The strangers of the day's surprise on the village map: at the road while their surprise is on, day and night, at
 * every level of detail (the 7 px figure, the 14 px one, the scene's sprite), tappable and followed by their bubble.
 * PNG snapshots in build/village-snapshots (65-strangers-…) for review.
 */
class StrangersMapTest {
    private val dir = File("build/village-snapshots").apply { mkdirs() }
    private val fit = SceneFit.town(1080, 2400)
    private val rig = CameraRig.town(fit, 1080, 2400)
    private val today = "2026-09-24"
    private val day = LocalDate.parse(today)

    private val types = arrayOf(
        BuildingType.FIELD, BuildingType.HUT, BuildingType.WELL, BuildingType.KOZOLEC, BuildingType.HOUSE, BuildingType.PALISADE,
        BuildingType.LIPA, BuildingType.CHURCH, BuildingType.BEEHIVE,
    )

    private fun village(kind: String, done: Boolean = false) = GameState(
        seed = 42, age = Age.VAS, villagers = 6, morale = 70, fire = 70,
        buildings = types.mapIndexed { i, t -> Building("b$i", t, i) },
        surprise = Surprise(today, kind, 3, done = done),
    )

    private val cast = listOf(
        Villager(id = "micka", name = "Babica Micka", emoji = "👵", art = "grandma", home = listOf("house", "hut")),
        Villager(id = "luka", name = "Pastir Luka", emoji = "🐑", art = "shepherd", home = listOf("spot:meadow")),
    )

    @Test fun `the stranger is at the road while the surprise is on`() {
        assertEquals("pedlar", Surprises.stranger(village(Surprises.PEDLAR), day)?.art)
        assertEquals("pilgrim", Surprises.stranger(village(Surprises.PILGRIM), day)?.art)
        // shown the way: the pilgrim has gone on; another day, or another surprise: nobody
        assertNull(Surprises.stranger(village(Surprises.PILGRIM, done = true), day))
        assertNull(Surprises.stranger(village(Surprises.PEDLAR), day.plusDays(1)))
        assertNull(Surprises.stranger(village(Surprises.RIDDLE), day))
        assertNull(Surprises.stranger(village(Surprises.LETTER), day))
    }

    @Test fun `the strangers keep to the road by day, at night and at a festival`() {
        val w = fit.width; val h = fit.height
        for (kind in listOf(Surprises.PEDLAR, Surprises.PILGRIM)) {
            val s = village(kind)
            val people = cast + Surprises.stranger(s, day)!!
            for ((hour, festival) in listOf(11f to false, 23.5f to false, 20f to true)) {
                val placed = TownPeople.of(s, people, time = 3.0, hour = hour, month = 6, w = w, h = h, festival = festival)
                val p = placed.firstOrNull { it.id == kind }
                assertNotNull("$kind at $hour ${if (festival) "(festival)" else ""}", p)
                assertEquals(TownPlace.Spot("road"), p!!.place)
                assertTrue("$kind isn't sitting by the fire", p.activity != Activity.WARM)
                assertTrue("$kind isn't in the festival's ring", p.x * p.x + p.y * p.y > 30f)
            }
            // the villagers still go to bed or to the fire at night
            assertTrue(TownPeople.of(s, people, time = 3.0, hour = 23.5f, month = 6, w = w, h = h).filter { it.id != kind }.all { it.activity == Activity.WARM })
        }
    }

    @Test fun `the stranger is drawn and tappable at every level of detail`() {
        for (kind in listOf(Surprises.PEDLAR, Surprises.PILGRIM)) {
            val s = village(kind)
            val people = cast + Surprises.stranger(s, day)!!
            val r = VillageRenderer()
            val c = PixelCanvas(fit.width, fit.height)
            r.render(c, s, Frame(time = 2.0, hour = 11f, month = 6, people = people, today = today))
            val a = r.villagerAnchors.getValue(kind)
            val comp = VillageLayout.composition(fit.width, fit.height, false)
            val p = TownPeople.of(s, people, 2.0, 11f, 6, fit.width, fit.height).first { it.id == kind }
            val feetX = comp.fireX + (p.x - p.y) * 4f; val feetY = comp.fireY + (p.x + p.y) * 2f
            assertEquals(VillageHit.OnVillager(kind), r.hitTest(feetX.toInt(), (feetY - 3f).toInt(), 3))
            // closer in, the figure changes with the detail, and the stranger doesn't look like a plain man there
            for (k in 2..3) {
                val lens = Lens.of(k, rig.lookAt(a.x, a.y, (rig.minScale * k).toFloat()), 1080, 2400, fit.width, fit.height, fit.width, fit.height)
                val ck = PixelCanvas(fit.width, fit.height)
                r.render(ck, s, Frame(time = 2.0, hour = 11f, month = 6, people = people, today = today, lens = lens))
                assertEquals(VillageHit.OnVillager(kind), r.hitTest(lens.cx(feetX).toInt(), lens.cy(feetY).toInt() - 3 * k, 3 * k))
                val plain = PixelCanvas(fit.width, fit.height)
                val man = people.map { if (it.id == kind) it.copy(art = "man") else it }
                VillageRenderer().render(plain, s, Frame(time = 2.0, hour = 11f, month = 6, people = man, today = today, lens = lens))
                assertNotEquals("$kind at k=$k", plain.pixels.toList(), ck.pixels.toList())
            }
        }
    }

    @Test fun `render the strangers at the road, day and night, at every level of detail`() {
        for (kind in listOf(Surprises.PEDLAR, Surprises.PILGRIM)) {
            val s = village(kind)
            val people = cast + Surprises.stranger(s, day)!!
            val r = VillageRenderer()
            r.render(PixelCanvas(fit.width, fit.height), s, Frame(time = 2.0, hour = 11f, month = 6, people = people, today = today))
            val a = r.villagerAnchors.getValue(kind)
            for ((name, hour) in listOf("day" to 11f, "night" to 22.5f)) {
                for (k in 1..3) {
                    val cam = rig.lookAt(a.x, a.y + 6f, (rig.minScale * k * (if (k == 1) 1.5f else 1f)))
                    val lens = Lens.of(k, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
                    val c = PixelCanvas(fit.width, fit.height)
                    r.render(c, s, Frame(time = 2.0, hour = hour, month = 9, people = people, today = today, lens = lens))
                    // the part of the canvas the camera shows, around the stranger
                    crop(c, lens, cam, File(dir, "65-strangers-$kind-$name-k$k.png"))
                }
            }
        }
    }

    /** Writes the camera's view of [c] (the lens's window) at 2 screen px per canvas px, a 540 × 600 screen piece round its middle. */
    private fun crop(c: PixelCanvas, lens: Lens, cam: Camera, f: File) {
        val w = 540; val h = 600
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) for (x in 0 until w) {
            val sx = 270f + x; val sy = 900f + y
            val nx = lens.cx(cam.toCanvasX(sx)).toInt().coerceIn(0, c.width - 1)
            val ny = lens.cy(cam.toCanvasY(sy)).toInt().coerceIn(0, c.height - 1)
            img.setRGB(x, y, c.pixels[ny * c.width + nx])
        }
        ImageIO.write(img, "png", f)
    }
}
