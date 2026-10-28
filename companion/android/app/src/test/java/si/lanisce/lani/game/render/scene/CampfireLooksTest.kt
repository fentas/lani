package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneWorld
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The campfire in Carinthia's and the Lake District's looks (companion/SCENES.md "Culture looks"; Am Feuer, By the fire):
 * the village round the fire is FireStyle's (FireAgesTest renders it), and the camp's tent flies the culture's flag,
 * Austria's and England's for Slovenia's: only the pennant differs from Primorska's camp, at every detail.
 */
class CampfireLooksTest {
    private val dir = File("build/scene-snapshots/looks/campfire").apply { mkdirs() }

    private fun camp(culture: String) = SceneWorld(age = Age.TABOR, buildings = mapOf(BuildingType.TENT to 1, BuildingType.FIELD to 1), culture = culture)

    private fun render(culture: String, k: Int): PixelCanvas {
        val c = PixelCanvas(270 * k, 297 * k)
        val f = SceneFrame(time = 3.0, hour = 14f, month = 7, objects = SceneArt.objects.getValue("campfire").toSet(), world = camp(culture))
        ScenePainters.create("campfire").render(c, f, Lens(k, 0, 0, 270, 297))
        return c
    }

    @Test fun `the camp's tent flies the culture's flag, nothing else of the camp changes`() {
        for (k in 1..3) {
            val primorska = render("primorska", k)
            for (culture in listOf("kaernten", "lakeland")) {
                val own = render(culture, k)
                var x0 = Int.MAX_VALUE; var y0 = Int.MAX_VALUE; var x1 = -1; var y1 = -1
                for (y in 0 until own.height) for (x in 0 until own.width) if (own.pixels[y * own.width + x] != primorska.pixels[y * own.width + x]) {
                    x0 = minOf(x0, x); y0 = minOf(y0, y); x1 = maxOf(x1, x); y1 = maxOf(y1, y)
                }
                assertTrue("$culture d$k: the flag differs", x1 >= 0)
                // the pennant: 8 scene px long, 7 high, fluttering a pixel or two
                assertTrue("$culture d$k: only the pennant differs, not ${x1 - x0 + 1}x${y1 - y0 + 1}", x1 - x0 < 11 * k && y1 - y0 < 11 * k)
                if (k == 3) {
                    val cx = (x0 + x1) / 2; val cy = (y0 + y1) / 2
                    write(own, cx - 60, cy - 40, File(dir, "$culture-flag-d3.png"))
                    if (culture == "kaernten") write(primorska, cx - 60, cy - 40, File(dir, "primorska-flag-d3.png"))
                }
            }
        }
        assertEquals(render("kaernten", 1).pixels.toList(), render("kaernten", 1).pixels.toList())
    }

    /** A 120 × 80 window of [c] from ([x], [y]), four times the size. */
    private fun write(c: PixelCanvas, x: Int, y: Int, f: File) {
        val w = 120; val h = 80; val s = 4
        val img = BufferedImage(w * s, h * s, BufferedImage.TYPE_INT_RGB)
        for (yy in 0 until h * s) for (xx in 0 until w * s) {
            val px = (x + xx / s).coerceIn(0, c.width - 1); val py = (y + yy / s).coerceIn(0, c.height - 1)
            img.setRGB(xx, yy, c.pixels[py * c.width + px])
        }
        ImageIO.write(img, "png", f)
    }
}
