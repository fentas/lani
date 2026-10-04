package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.Sky
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The culture packs' own landscapes ("hills": Primorska's vineyard Na gričih, "alps": its mountains V gorah at the horizon,
 * "sea": Friuli's Il mare): a sheet of each through the year and the day, eight views side by side at a glance (a summer
 * noon and dusk, October, January, an April morning, a summer night, fog, a storm), with its effects and the weather a
 * dialog brings (build/scene-snapshots/landscapes), and that every effect and the rain, the fog and a grey sky show.
 */
class LandscapesTest {
    private val dir = File("build/scene-snapshots/landscapes").apply { mkdirs() }
    private val arts = listOf("hills", "sea", "alps")

    private fun frame(art: String, month: Int, hour: Float, time: Double = 3.0, sky: Sky = Sky.CLEAR, fx: Map<String, Float> = emptyMap(), people: Boolean = true) = SceneFrame(
        time = time, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), sky = sky, fx = fx,
        people = if (!people) emptyList() else SceneArt.personSlots.getValue(art).mapIndexed { k, slot -> PersonInScene("p-$slot", CAST.getValue(art)[k], slot, talking = k == 0) },
    )

    private fun render(art: String, f: SceneFrame, w: Int = 240, h: Int = 160): PixelCanvas = PixelCanvas(w, h).also { ScenePainters.create(art).render(it, f) }

    /** Who stands at each art's spots in the sheets: the scenes' own people. */
    private val CAST = mapOf(
        "hills" to listOf("winemaker", "grandpa", "child3"), "sea" to listOf("aunt", "child1", "grandma"), "alps" to listOf("grandpa", "shepherd", "child1"),
    )

    @Test fun `render eight views of each landscape at a glance`() {
        val scale = 3
        for (art in arts) {
            val views = listOf(
                frame(art, 7, 13f), frame(art, 7, 20.2f), frame(art, 10, 15f), frame(art, 1, 12f),
                frame(art, 4, 7.2f), frame(art, 7, 23f), frame(art, 9, 8f, sky = Sky(fog = 0.8f)), frame(art, 8, 16f, sky = Sky(rain = 0.7f, gloom = 0.9f, lightning = true)),
            )
            val img = BufferedImage(240 * 2 * scale, 160 * 4 * scale, BufferedImage.TYPE_INT_RGB)
            for ((i, f) in views.withIndex()) {
                val c = render(art, f)
                for (y in 0 until 160 * scale) for (x in 0 until 240 * scale) img.setRGB((i % 2) * 240 * scale + x, (i / 2) * 160 * scale + y, c.pixels[(y / scale) * 240 + x / scale])
            }
            ImageIO.write(img, "png", File(dir, "$art-views.png"))
        }
    }

    @Test fun `render the landscapes through the year and the day`() {
        val scale = 2
        for (art in arts) {
            val months = listOf(1, 4, 7, 9, 10, 11)
            val hours = listOf(8.5f, 14f, 19.8f, 23f)
            val img = BufferedImage(240 * hours.size * scale, 160 * months.size * scale, BufferedImage.TYPE_INT_RGB)
            for ((mi, m) in months.withIndex()) for ((hi, hh) in hours.withIndex()) {
                val c = render(art, frame(art, m, hh, time = 3.0 + hi))
                for (y in 0 until 160 * scale) for (x in 0 until 240 * scale) img.setRGB(hi * 240 * scale + x, mi * 160 * scale + y, c.pixels[(y / scale) * 240 + x / scale])
            }
            ImageIO.write(img, "png", File(dir, "$art-year.png"))
            // the effects and the weather, each on its own, then a tall phone view closer up
            val views = SceneArt.effects.getValue(art).map { "fx-$it" to frame(art, 9, 11f, fx = mapOf(it to 0.55f)) } + listOf(
                "rain" to frame(art, 9, 11f, sky = Sky(rain = 0.8f, gloom = 0.7f, wind = 0.5f)),
                "fog" to frame(art, 11, 8.5f, sky = Sky(fog = 0.8f, gloom = 0.3f)),
                "storm" to frame(art, 7, 17f, time = 3.4, sky = Sky(rain = 0.6f, gloom = 1f, wind = 0.9f, lightning = true)),
            )
            for ((name, f) in views) write(render(art, f), File(dir, "$art-$name.png"), 4)
            val sheet = BufferedImage(240 * 3 * scale, 160 * ((views.size + 2) / 3) * scale, BufferedImage.TYPE_INT_RGB)
            for ((i, v) in views.withIndex()) {
                val c = render(art, v.second)
                for (y in 0 until 160 * scale) for (x in 0 until 240 * scale) sheet.setRGB((i % 3) * 240 * scale + x, (i / 3) * 160 * scale + y, c.pixels[(y / scale) * 240 + x / scale])
            }
            ImageIO.write(sheet, "png", File(dir, "$art-effects.png"))
            write(render(art, frame(art, 10, 16f), 270, 297), File(dir, "$art-tall.png"), 3)
            for (k in 2..3) {
                val c = PixelCanvas(270 * k, 297 * k)
                ScenePainters.create(art).render(c, frame(art, 10, 16f), si.lanisce.lani.game.render.Lens(k, 0, 0, 270, 297))
                write(c, File(dir, "$art-tall-d$k.png"), 1)
            }
        }
    }

    @Test fun `every effect and the weather show`() {
        for (art in arts) {
            val plain = render(art, frame(art, 9, 11f)).pixels.toList()
            for (e in SceneArt.effects.getValue(art)) {
                val on = render(art, frame(art, 9, 11f, fx = mapOf(e to 0.5f))).pixels.toList()
                assertNotEquals("$art: $e shows", plain, on)
            }
            // rain veils the far land (the hills) or hangs over the lagoon (the sea); fog and a grey sky change most of it; snowflakes fall
            for ((sky, least) in listOf(Sky(rain = 0.7f) to 240 * 160 / 20, Sky(fog = 0.7f) to 240 * 160 / 10, Sky(gloom = 0.8f) to 240 * 160 / 10, Sky(snow = 0.6f) to 60)) {
                val c = render(art, frame(art, 9, 11f, sky = sky))
                val diff = c.pixels.indices.count { c.pixels[it] != plain[it] }
                assertTrue("$art: $sky shows ($diff pixels)", diff > least)
            }
        }
    }

    private fun write(c: PixelCanvas, f: File, scale: Int) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", f)
    }
}
