package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.Wildlife
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The wild animals in the outdoor scenes ([Wildlife]): renders of the forest at dawn, by day, at dusk and at night (the
 * day's own mix, and every animal at once), with the hunter on his high seat, for review (build/scene-snapshots/wild);
 * each animal is a word to tap while it is out and nothing while it isn't.
 */
class WildlifeRenderTest {
    private val dir = File("build/scene-snapshots/wild").apply { mkdirs() }
    private val bw = 270; private val bh = 297

    private val parts = listOf("dawn" to 5.6f, "day" to 13f, "dusk" to 20.4f, "night" to 23.4f)

    private fun frame(art: String, hour: Float, month: Int = 6, wild: Set<String>? = null, day: Long = 20600, fx: Map<String, Float> = emptyMap(), people: List<PersonInScene> = emptyList()) =
        SceneFrame(time = 4.2, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), people = people, village = 7, day = day, wild = wild, fx = fx)

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = bw, h: Int = bh): Pair<PixelCanvas, List<SceneHit>> {
        val c = PixelCanvas(w * k, h * k)
        return c to ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
    }

    private fun write(c: PixelCanvas, f: File, scale: Int) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", f)
    }

    private val joze = listOf(PersonInScene("joze", "hunter", "highseat"))

    @Test fun `render the forest at dawn, by day, at dusk and at night`() {
        for ((name, hour) in parts) {
            val (c, _) = render("forest", frame("forest", hour, people = joze))
            write(c, File(dir, "forest-$name.png"), 3)
            val (all, _) = render("forest", frame("forest", hour, wild = Wildlife.all("forest"), people = joze))
            write(all, File(dir, "forest-$name-all.png"), 3)
            val (fine, _) = render("forest", frame("forest", hour, wild = Wildlife.all("forest"), people = joze), 3)
            write(fine, File(dir, "forest-$name-all-d3.png"), 1)
        }
    }

    /** The other outdoor scenes with all their animals at every part of the day, at detail 1 and 3 (a sheet per art). */
    @Test fun `render every art's animals`() {
        for (art in Wildlife.animals.keys - "forest") {
            val img = BufferedImage(bw * 4 + 24, bh, BufferedImage.TYPE_INT_RGB)
            for ((k, ph) in parts.withIndex()) {
                val (c, _) = render(art, frame(art, ph.second, wild = Wildlife.all(art)))
                for (y in 0 until bh) for (x in 0 until bw) img.setRGB(k * (bw + 8) + x, y, c.pixels[y * bw + x])
            }
            ImageIO.write(img, "png", File(dir, "$art-all.png"))
            for ((name, hour) in listOf(parts[0], parts[2])) {
                val (fine, _) = render(art, frame(art, hour, wild = Wildlife.all(art)), 3)
                write(fine, File(dir, "$art-$name-all-d3.png"), 1)
            }
        }
    }

    /** Four days side by side at each part of the day: the mix and the places change from day to day. */
    @Test fun `render the forest's days`() {
        for ((name, hour) in parts) {
            val img = BufferedImage(bw * 2 * 4 + 24, bh * 2, BufferedImage.TYPE_INT_RGB)
            for (k in 0 until 4) {
                val (c, _) = render("forest", frame("forest", hour, day = 20600L + k))
                for (y in 0 until bh * 2) for (x in 0 until bw * 2) img.setRGB(k * (bw * 2 + 8) + x, y, c.pixels[(y / 2) * bw + x / 2])
            }
            ImageIO.write(img, "png", File(dir, "forest-$name-days.png"))
        }
    }
}
