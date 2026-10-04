package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Calendar
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.Sky
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/**
 * The scenes of the buildings that open into them, the church ("church": V cerkvi) and the bee house ("apiary": Pri
 * čebelnjaku): a contact sheet of each through the year and the day with its people, its feasts (the nativity scene,
 * the Easter baskets, the flags on World Bee Day), its effects and the weather, and closer up
 * (build/scene-snapshots/buildings); and that the feasts show on their days only.
 */
class BuildingScenesTest {
    private val dir = File("build/scene-snapshots/buildings").apply { mkdirs() }
    private val arts = listOf("church", "apiary")

    /** Who stands at each art's spots: the scenes' own people. */
    private val cast = mapOf("church" to listOf("aunt", "grandma", "child3"), "apiary" to listOf("beekeeper", "child2", "grandpa"))

    private fun frame(art: String, month: Int, hour: Float, time: Double = 3.0, sky: Sky = Sky.CLEAR, fx: Map<String, Float> = emptyMap(), date: LocalDate? = null, people: Boolean = true) =
        SceneFrame(
            time = time, hour = hour, month = date?.monthValue ?: month, objects = SceneArt.objects.getValue(art).toSet(), sky = sky, fx = fx, date = date,
            people = if (!people) emptyList() else SceneArt.personSlots.getValue(art).mapIndexed { k, slot -> PersonInScene("p-$slot", cast.getValue(art)[k], slot, talking = k == 0) },
        )

    private fun render(art: String, f: SceneFrame, w: Int = 240, h: Int = 160, k: Int = 1): PixelCanvas =
        PixelCanvas(w * k, h * k).also { ScenePainters.create(art).render(it, f, Lens(k, 0, 0, w, h)) }

    private fun label(g: java.awt.Graphics2D, s: String, x: Int, y: Int) {
        g.color = Color(0, 0, 0, 160); g.fillRect(x, y, s.length * 7 + 6, 14)
        g.color = Color.WHITE; g.drawString(s, x + 3, y + 11)
    }

    @Test fun `render the contact sheet`() {
        val scale = 2
        val easter = Calendar.easter(2025)
        for (art in arts) {
            val feasts = when (art) {
                "church" -> listOf("Christmas" to frame(art, 12, 19.5f, date = LocalDate.of(2026, 12, 25)), "Easter" to frame(art, 4, 10f, date = easter))
                else -> listOf("Bee Day" to frame(art, 5, 11f, date = LocalDate.of(2027, 5, 20)), "April" to frame(art, 4, 10f, date = LocalDate.of(2027, 4, 12)))
            }
            val fxAll = SceneArt.effects.getValue(art).associateWith { 1f }
            val views = listOf(
                "July 8:30" to frame(art, 7, 8.5f), "July 14:00" to frame(art, 7, 14f), "July 19:40" to frame(art, 7, 19.7f), "July 23:00" to frame(art, 7, 23f),
                "January noon" to frame(art, 1, 12f), "April morning" to frame(art, 4, 9f), "October 15:30" to frame(art, 10, 15.5f), "November dusk" to frame(art, 11, 17f),
            ) + feasts + listOf(
                "effects" to frame(art, 6, 11f, time = 3.4, fx = fxAll), "effects at night" to frame(art, 6, 22f, time = 3.4, fx = fxAll),
                "rain" to frame(art, 9, 15f, sky = Sky(rain = 0.8f, gloom = 0.8f, wind = 0.4f)), "fog, snow" to frame(art, 1, 9f, sky = Sky(fog = 0.7f, snow = 0.7f, gloom = 0.4f)),
                "empty" to frame(art, 6, 12f, people = false), "empty at night" to frame(art, 6, 23.5f, people = false),
            )
            val cols = 4; val rows = (views.size + cols - 1) / cols
            val img = BufferedImage(240 * cols * scale, 160 * rows * scale, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            for ((i, v) in views.withIndex()) {
                val c = render(art, v.second)
                val ox = (i % cols) * 240 * scale; val oy = (i / cols) * 160 * scale
                for (y in 0 until 160 * scale) for (x in 0 until 240 * scale) img.setRGB(ox + x, oy + y, c.pixels[(y / scale) * 240 + x / scale])
                label(g, "$art: ${v.first}", ox + 4, oy + 4)
            }
            g.dispose()
            ImageIO.write(img, "png", File(dir, "00-$art-sheet.png"))
            // a phone's view, closer up at every detail
            for (k in 1..Lens.MAX_DETAIL) write(render(art, frame(art, 7, 16f), 270, 297, k), File(dir, "$art-phone-d$k.png"), 6 / k)
            write(render(art, frame(art, 12, 21f, date = LocalDate.of(2026, 12, 26)), 270, 297, 3), File(dir, "$art-winter-night-d3.png"), 2)
        }
        // both scenes side by side, a day and a night, for the report
        val img = BufferedImage(240 * 4 * scale, 160 * 2 * scale, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        for ((ai, art) in arts.withIndex()) for ((hi, pair) in listOf(7 to 10.5f, 10 to 16f, 1 to 12.5f, 7 to 22.5f).withIndex()) {
            val c = render(art, frame(art, pair.first, pair.second))
            for (y in 0 until 160 * scale) for (x in 0 until 240 * scale) img.setRGB(hi * 240 * scale + x, ai * 160 * scale + y, c.pixels[(y / scale) * 240 + x / scale])
            label(g, "$art · month ${pair.first}, ${pair.second}h", hi * 240 * scale + 4, ai * 160 * scale + 4)
        }
        g.dispose()
        ImageIO.write(img, "png", File(dir, "00-contact-sheet.png"))
    }

    @Test fun `render each poke a moment in, closer up`() {
        for (art in arts) {
            val pokes = ScenePainters.create(art).pokes
            val frames = pokes.flatMap { p -> p.steps.indices.map { s -> "${p.id}-$s" to mapOf(p.id to si.lanisce.lani.game.scene.Poked(s, 2.0)) } }
            val scale = 2; val k = 2; val w = 270; val h = 297
            val img = BufferedImage(w * k * scale / 2 * frames.size, h * k * scale / 2, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            for ((i, f) in frames.withIndex()) {
                val c = render(art, frame(art, 7, 14f, time = 3.2).copy(pokes = f.second), w, h, k)
                val ox = i * w * k * scale / 2
                for (y in 0 until h * k) for (x in 0 until w * k) img.setRGB(ox + x, y, c.pixels[y * c.width + x])
                label(g, "$art: ${f.first}", ox + 4, 4)
            }
            g.dispose()
            ImageIO.write(img, "png", File(dir, "01-$art-pokes.png"))
        }
    }

    private fun changed(a: PixelCanvas, b: PixelCanvas): Int = a.pixels.indices.count { a.pixels[it] != b.pixels[it] }

    @Test fun `the church keeps its feasts, the nativity scene at Christmas and the baskets at Easter only`() {
        // Easter 2025 is on 20 April: a week either side is April too
        val easter = Calendar.easter(2025)
        val before = render("church", frame("church", 4, 10f, date = easter.minusDays(3)))
        val holySaturday = render("church", frame("church", 4, 10f, date = easter.minusDays(1)))
        val sunday = render("church", frame("church", 4, 10f, date = easter))
        val after = render("church", frame("church", 4, 10f, date = easter.plusDays(5)))
        assertTrue("the baskets on Holy Saturday", changed(before, holySaturday) > 60)
        assertEquals("the same baskets on Easter Sunday", 0, changed(holySaturday, sunday))
        assertEquals("gone a week later", 0, changed(before, after))
        // the nativity scene in December and January, the Virgin on her pedestal the rest of the year
        val nov = render("church", frame("church", 11, 12f)); val dec = render("church", frame("church", 12, 12f))
        assertTrue("the nativity scene in December", changed(nov, dec) > 150)
        // a frame without a date is an ordinary day
        assertEquals(0, changed(render("church", frame("church", 4, 10f)), before))
    }

    @Test fun `the bee house flies its flags on World Bee Day`() {
        val day = render("apiary", frame("apiary", 5, 11f, date = LocalDate.of(2027, 5, 20)))
        val before = render("apiary", frame("apiary", 5, 11f, date = LocalDate.of(2027, 5, 18)))
        assertTrue(changed(before, day) > 40)
        assertEquals(0, changed(before, render("apiary", frame("apiary", 5, 11f))))
    }

    private fun write(c: PixelCanvas, f: File, scale: Int) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", f)
    }
}
