package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** The effects a dialog sets in a scene (see [SceneArt.effects]): each shows, at every detail, and nothing else changes. */
class SceneEffectsTest {
    private val bw = 270; private val bh = 297

    private fun frame(art: String, fx: Map<String, Float>, time: Double, hour: Float = 14f) = SceneFrame(
        time = time, hour = hour, month = 7, objects = SceneArt.objects.getValue(art).toSet(),
        people = SceneArt.personSlots.getValue(art).mapIndexed { k, slot -> PersonInScene("p-$slot", SceneArt.people[k], slot) },
        fx = fx,
    )

    private fun render(art: String, f: SceneFrame, k: Int = 1): Pair<PixelCanvas, List<SceneHit>> {
        val c = PixelCanvas(bw * k, bh * k)
        return c to ScenePainters.create(art).render(c, f, Lens(k, 0, 0, bw, bh))
    }

    private fun changed(a: PixelCanvas, b: PixelCanvas): Int = a.pixels.indices.count { a.pixels[it] != b.pixels[it] }

    @Test fun `every effect of every art shows, at every detail`() {
        val failed = ArrayList<String>()
        for (art in SceneArt.arts) for (name in SceneArt.effects.getValue(art)) for (k in 1..Lens.MAX_DETAIL) {
            // over a second: the sparks come with the hammer's blows, the bell's rings go out
            val diff = (0 until 6).sumOf { i ->
                val t = 3.0 + i * 0.17
                changed(render(art, frame(art, emptyMap(), t), k).first, render(art, frame(art, mapOf(name to 0.6f), t), k).first)
            }
            if (diff < 20 * k * k) failed += "$art/$name at detail $k: $diff px change"
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
    }

    @Test fun `render every effect for review`() {
        val dir = File("build/scene-snapshots/effects").apply { mkdirs() }
        for (art in SceneArt.arts) for (name in SceneArt.effects.getValue(art)) for (hour in listOf(14f, 21.5f)) {
            val (c, _) = render(art, frame(art, mapOf(name to 0.6f), 3.1, hour), 2)
            val img = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until c.height) for (x in 0 until c.width) img.setRGB(x, y, c.pixels[y * c.width + x])
            ImageIO.write(img, "png", File(dir, "$art-$name-${hour.toInt()}h.png"))
        }
    }

    @Test fun `an effect keeps the things and people there to tap`() {
        for (art in SceneArt.arts) {
            val all = SceneArt.effects.getValue(art).associateWith { 0.6f }
            val (_, plain) = render(art, frame(art, emptyMap(), 3.0))
            val (_, busy) = render(art, frame(art, all, 3.0))
            assertEquals("$art: the same things and people", plain.map { it.target }.toSet(), busy.map { it.target }.toSet())
        }
    }

    @Test fun `an effect another art has changes nothing`() {
        for (art in SceneArt.arts) {
            val others = SceneArt.effects.values.flatten().filter { it !in SceneArt.effects.getValue(art) }.associateWith { 1f }
            val (a, _) = render(art, frame(art, emptyMap(), 3.0))
            val (b, _) = render(art, frame(art, others, 3.0))
            assertEquals("$art: others' effects", 0, changed(a, b))
        }
    }

    @Test fun `the tent's lantern is dark at 0 and lit at 1, whatever the hour`() {
        val night = frame("tent", emptyMap(), 3.0, hour = 22.5f)
        val (lit, _) = render("tent", night)
        val (dark, _) = render("tent", night.copy(fx = mapOf("lantern" to 0f)))
        assertTrue("put out at night", changed(lit, dark) > 100)
        val (relit, _) = render("tent", night.copy(fx = mapOf("lantern" to 1f)))
        assertTrue("lit again, as the hour has it (the flame full)", changed(lit, relit) < 20)
        val day = frame("tent", emptyMap(), 3.0)
        assertTrue("lit by day", changed(render("tent", day).first, render("tent", day.copy(fx = mapOf("lantern" to 1f))).first) > 20)
    }
}
