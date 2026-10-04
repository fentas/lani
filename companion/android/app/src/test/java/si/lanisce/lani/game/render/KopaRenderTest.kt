package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.villagers.Villager
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The charcoal pile deep in the woods below the village (the "kopa" spot): a place to tap at every detail, and its burner
 * sitting by it some evenings, his to tap. Renders it by day and by night, with and without him, at the three details, for
 * review (build/village-snapshots/49-kopa-*.png).
 */
class KopaRenderTest {
    private val dir = File("build/village-snapshots").apply { mkdirs() }
    private val fit = SceneFit.town(1080, 2400)
    private val rig = CameraRig.town(fit, 1080, 2400)
    private val village = GameState(seed = 42, age = Age.VAS, villagers = 6, morale = 70, fire = 70, buildings = listOf(Building("b0", BuildingType.HUT, 0), Building("b1", BuildingType.FIELD, 1)))
    private val burner = Villager("oglar", "Oglar Miha", "🧔", "burner", voice = "male", home = listOf("spot:kopa"))
    private val at = mapOf("oglar" to TownPlace.Spot("kopa"))

    @Test fun `render the charcoal pile by day and by night, with its burner`() {
        val fg = Foreground.of(village.age, fit.width, fit.height)!!
        assertTrue(fg.kopa)
        for ((name, hour, who) in listOf(Triple("day", 13f, false), Triple("dusk", 19.5f, true), Triple("night", 23f, true), Triple("night-alone", 23f, false))) {
            for (k in 1..3) {
                val cam = rig.lookAt(fg.kopaX - 4f, fg.kopaY - 4f, (rig.minScale * k).toFloat())
                val lens = Lens.of(k, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
                val c = PixelCanvas(fit.width, fit.height)
                val r = VillageRenderer()
                val frame = Frame(time = 4.0, hour = hour, month = 9, lens = lens, people = if (who) listOf(burner) else emptyList(), standing = if (who) at else emptyMap())
                r.render(c, village, frame)
                // the pile is the kopa's to tap, and he is his
                assertEquals("$name k=$k", VillageHit.OnSpot("kopa"), r.hitTest(lens.cx(fg.kopaX).toInt(), lens.cy(fg.kopaY - 3f).toInt(), k))
                if (who) {
                    val head = r.villagerAnchors.getValue("oglar")
                    assertEquals("$name k=$k", VillageHit.OnVillager("oglar"), r.hitTest(lens.cx(head.x).toInt(), lens.cy(head.y + 4f).toInt(), 3 * k))
                }
                // the part round the pile (the canvas is mostly woods), about 480 px wide whatever the detail
                write(c, File(dir, "49-kopa-$name-k$k.png"), lens.cx(fg.kopaX - 4f).toInt(), lens.cy(fg.kopaY - 8f).toInt(), 60 * k, 40 * k, if (k == 3) 1 else 4 / k)
            }
        }
    }

    /** The part of [c] [rx] × [ry] px round ([cx], [cy]) as a PNG, [scale]d up. */
    private fun write(c: PixelCanvas, f: File, cx: Int, cy: Int, rx: Int, ry: Int, scale: Int) {
        val x0 = (cx - rx).coerceIn(0, c.width - 1); val y0 = (cy - ry).coerceIn(0, c.height - 1)
        val w = minOf(2 * rx, c.width - x0); val h = minOf(2 * ry, c.height - y0)
        val img = BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h * scale) for (x in 0 until w * scale) img.setRGB(x, y, c.pixels[(y0 + y / scale) * c.width + x0 + x / scale])
        ImageIO.write(img, "png", f)
    }
}
