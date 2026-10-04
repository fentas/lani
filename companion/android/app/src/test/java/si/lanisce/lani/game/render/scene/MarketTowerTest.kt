package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.Sky
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The market ("market", Na tržnici) and the view from the watchtower ("watchtower", Na stolpu): a contact sheet of both
 * through the year and the day (build/scene-snapshots/market-tower/00-contact-sheet.png), each effect and the weather on
 * its own, a tall phone view at every detail; and that every effect, the rain, the fog and a grey sky show, and the wolves
 * of the village's event shine in the tower's forest at night.
 */
class MarketTowerTest {
    private val dir = File("build/scene-snapshots/market-tower").apply { mkdirs() }
    private val arts = listOf("market", "watchtower")

    /** Who stands at each art's spots in the sheets: the scenes' own people. */
    private val cast = mapOf("market" to listOf("grandma", "innkeeper", "winemaker"), "watchtower" to listOf("shepherd", "child1", "farmer"))

    private fun frame(art: String, month: Int, hour: Float, time: Double = 3.0, sky: Sky = Sky.CLEAR, fx: Map<String, Float> = emptyMap(), people: Boolean = true, world: SceneWorld = SceneWorld.ALL) = SceneFrame(
        time = time, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), sky = sky, fx = fx, world = world,
        people = if (!people) emptyList() else SceneArt.personSlots.getValue(art).mapIndexed { k, slot -> PersonInScene("p-$slot", cast.getValue(art)[k], slot, talking = k == 0) },
    )

    private fun render(art: String, f: SceneFrame, w: Int = 240, h: Int = 160, k: Int = 1): PixelCanvas =
        PixelCanvas(w * k, h * k).also { ScenePainters.create(art).render(it, f, Lens(k, 0, 0, w, h)) }

    @Test fun `render the contact sheet`() {
        val scale = 2
        val months = listOf(1, 4, 7, 10)
        val hours = listOf(8.5f, 14f, 19.8f, 23f)
        val label = 14
        val rows = arts.size * months.size
        val img = BufferedImage(240 * scale * hours.size, (160 * scale + label) * rows, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        for ((ai, art) in arts.withIndex()) for ((mi, m) in months.withIndex()) for ((hi, hr) in hours.withIndex()) {
            val c = render(art, frame(art, m, hr, time = 3.0 + hi))
            val oy = (ai * months.size + mi) * (160 * scale + label)
            for (y in 0 until 160 * scale) for (x in 0 until 240 * scale) img.setRGB(hi * 240 * scale + x, oy + label + y, c.pixels[(y / scale) * 240 + x / scale])
            g.color = Color.WHITE
            g.drawString("$art  month $m  ${hr}h", hi * 240 * scale + 4, oy + 11)
        }
        g.dispose()
        ImageIO.write(img, "png", File(dir, "00-contact-sheet.png"))
        // each art's half on its own, to look at closer
        val half = (160 * scale + label) * months.size
        for ((ai, art) in arts.withIndex()) ImageIO.write(img.getSubimage(0, ai * half, img.width, half), "png", File(dir, "00-$art-sheet.png"))
    }

    @Test fun `render the effects, the weather and the closer looks`() {
        val scale = 2
        for (art in arts) {
            val views = SceneArt.effects.getValue(art).map { "fx-$it" to frame(art, 9, 11f, fx = mapOf(it to 0.55f)) } +
                SceneArt.effects.getValue(art).map { "fx-$it-night" to frame(art, 9, 22f, fx = mapOf(it to 1f)) } + listOf(
                    "rain" to frame(art, 9, 11f, sky = Sky(rain = 0.8f, gloom = 0.7f, wind = 0.5f)),
                    "fog" to frame(art, 11, 8.5f, sky = Sky(fog = 0.8f, gloom = 0.3f)),
                    "storm" to frame(art, 7, 17f, time = 3.4, sky = Sky(rain = 0.6f, gloom = 1f, wind = 0.9f, lightning = true)),
                    "snow" to frame(art, 1, 10f, sky = Sky(snow = 0.8f, gloom = 0.4f)),
                    "wolves-event" to frame(art, 12, 22f, world = SceneWorld(wolves = true)),
                )
            val sheet = BufferedImage(240 * 3 * scale, 160 * ((views.size + 2) / 3) * scale, BufferedImage.TYPE_INT_RGB)
            for ((i, v) in views.withIndex()) {
                val c = render(art, v.second)
                write(c, File(dir, "$art-${v.first}.png"), 4)
                for (y in 0 until 160 * scale) for (x in 0 until 240 * scale) sheet.setRGB((i % 3) * 240 * scale + x, (i / 3) * 160 * scale + y, c.pixels[(y / scale) * 240 + x / scale])
            }
            ImageIO.write(sheet, "png", File(dir, "$art-effects.png"))
            write(render(art, frame(art, 10, 16f), 270, 297), File(dir, "$art-tall.png"), 3)
            write(render(art, frame(art, 6, 11f), 360, 160), File(dir, "$art-wide.png"), 3)
            for (k in 2..3) write(render(art, frame(art, 10, 16f), 270, 297, k), File(dir, "$art-tall-d$k.png"), 1)
            for (k in 1..3) write(render(art, frame(art, 7, 13f), 240, 160, k), File(dir, "$art-d$k.png"), 6 / k)
        }
    }

    @Test fun `every effect and the weather show`() {
        for (art in arts) {
            val plain = render(art, frame(art, 9, 11f)).pixels.toList()
            for (e in SceneArt.effects.getValue(art)) {
                val on = render(art, frame(art, 9, 11f, fx = mapOf(e to 0.5f))).pixels.toList()
                assertNotEquals("$art: $e shows", plain, on)
            }
            for ((sky, least) in listOf(Sky(rain = 0.7f) to 240 * 160 / 20, Sky(fog = 0.7f) to 240 * 160 / 10, Sky(gloom = 0.8f) to 240 * 160 / 10, Sky(snow = 0.6f) to 60)) {
                val c = render(art, frame(art, 9, 11f, sky = sky))
                val diff = c.pixels.indices.count { c.pixels[it] != plain[it] }
                assertTrue("$art: $sky shows ($diff pixels)", diff > least)
            }
        }
    }

    @Test fun `the wolves of the village's event shine in the tower's forest at night, not by day`() {
        val night = render("watchtower", frame("watchtower", 12, 22.5f)).pixels.toList()
        val wolves = render("watchtower", frame("watchtower", 12, 22.5f, world = SceneWorld(wolves = true))).pixels.toList()
        assertNotEquals("their eyes at night", night, wolves)
        val day = render("watchtower", frame("watchtower", 12, 12f)).pixels.toList()
        assertTrue("not by day", day == render("watchtower", frame("watchtower", 12, 12f, world = SceneWorld(wolves = true))).pixels.toList())
    }

    private fun write(c: PixelCanvas, f: File, scale: Int) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", f)
    }
}
