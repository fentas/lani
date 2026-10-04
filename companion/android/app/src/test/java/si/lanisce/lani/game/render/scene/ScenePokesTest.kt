package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Poke
import si.lanisce.lani.game.scene.Poked
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The pokes the painters declare (see [Poke]): every reaction shows at every detail, what they happen to can be tapped,
 * they leave the things and people where they are to tap, and snapshots of each for review (build/scene-snapshots/pokes).
 */
class ScenePokesTest {
    private val bw = 270; private val bh = 297

    /** When the poke began, on the frame clock. */
    private val start = 10.0

    /** Every wild animal is out (so theirs can be poked, by day too). */
    private fun frame(art: String, age: Double = 0.0, pokes: Map<String, Poked> = emptyMap(), hour: Float = 14f) = SceneFrame(
        time = start + age, hour = hour, month = 7, objects = SceneArt.objects.getValue(art).toSet(),
        people = SceneArt.personSlots.getValue(art).mapIndexed { k, slot -> PersonInScene("p-$slot", SceneArt.people[k], slot) },
        pokes = pokes, wild = si.lanisce.lani.game.scene.Wildlife.all(art),
    )

    private fun render(art: String, f: SceneFrame, k: Int = 1): PixelCanvas {
        val c = PixelCanvas(bw * k, bh * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, bw, bh))
        return c
    }

    private fun changed(a: PixelCanvas, b: PixelCanvas): Int = a.pixels.indices.count { a.pixels[it] != b.pixels[it] }

    private fun pokesOf(art: String): List<Poke> = ScenePainters.create(art).pokes

    /** A few moments of a reaction [d] s long: spread over it, or for a long one its start and its end. */
    private fun moments(d: Double): List<Double> =
        if (d <= 4.0) (0 until 6).map { d * (it + 0.5) / 6 } else listOf(0.15, 0.4, 0.8, 1.3, d - 2.2, d - 1.2, d - 0.6, d - 0.2)

    @Test fun `each art declares its pokes once, each with a step or more`() {
        for (art in SceneArt.arts) {
            val ids = pokesOf(art).map { it.id }
            assertEquals("$art: pokes declared twice", ids.size, ids.toSet().size)
        }
        assertTrue("the kitchen's cat can be poked", pokesOf("kitchen").any { it.id == "cat" && it.steps.size == 3 })
    }

    @Test fun `every reaction of every poke shows, at every detail`() {
        val failed = ArrayList<String>()
        for (art in SceneArt.arts) for (poke in pokesOf(art)) for ((step, d) in poke.steps.withIndex()) for (k in 1..Lens.MAX_DETAIL) {
            val diff = moments(d).sumOf { age ->
                changed(render(art, frame(art, age), k), render(art, frame(art, age, mapOf(poke.id to Poked(step, start))), k))
            }
            if (diff < 20 * k * k) failed += "$art/${poke.id} step $step at detail $k: $diff px change"
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
    }

    @Test fun `what a poke is for can be tapped, by day and at night`() {
        val failed = ArrayList<String>()
        for (art in SceneArt.arts) for (hour in listOf(14f, 22.5f)) for (k in listOf(1, 2)) {
            val p = ScenePainters.create(art)
            val c = PixelCanvas(bw * k, bh * k)
            p.render(c, frame(art, hour = hour), Lens(k, 0, 0, bw, bh))
            val found = HashSet<String>()
            for (y in 0 until c.height) for (x in 0 until c.width) p.pokeAt(c, x, y, 0)?.let { found += it }
            // the pigeons roost at night
            val want = p.pokes.map { it.id }.filter { !(art == "square" && it == "pigeons" && hour > 20f) }.toSet()
            if (!found.containsAll(want)) failed += "$art at ${hour}h, detail $k: ${want - found} can't be tapped"
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
    }

    @Test fun `a poke keeps the things and people there to tap`() {
        for (art in SceneArt.arts) {
            val plain = ScenePainters.create(art).render(PixelCanvas(bw, bh), frame(art, 0.6)).map { it.target }.toSet()
            for (step in 0 until (pokesOf(art).maxOfOrNull { it.steps.size } ?: 0)) {
                val all = pokesOf(art).filter { step < it.steps.size }.associate { it.id to Poked(step, start) }
                val busy = ScenePainters.create(art).render(PixelCanvas(bw, bh), frame(art, 0.6, all)).map { it.target }.toSet()
                assertEquals("$art, step $step: the same things and people", plain, busy)
            }
        }
    }

    @Test fun `render every poke for review`() {
        val dir = File("build/scene-snapshots/pokes").apply { mkdirs() }
        for (art in SceneArt.arts) for (poke in pokesOf(art)) for (hour in listOf(14f, 21.5f)) for (k in listOf(1, 3)) {
            // around the thing: where the poke answers at rest, and room about it
            val p = ScenePainters.create(art)
            val rest = PixelCanvas(bw, bh)
            p.render(rest, frame(art, hour = hour))
            var x0 = bw; var y0 = bh; var x1 = 0; var y1 = 0
            for (y in 0 until bh) for (x in 0 until bw) if (p.pokeAt(rest, x, y, 0) == poke.id) { x0 = minOf(x0, x); y0 = minOf(y0, y); x1 = maxOf(x1, x); y1 = maxOf(y1, y) }
            if (x1 < x0) continue
            // a crop of the scene canvas, 6 screen px per canvas px at either detail
            val cw = 64; val ch = 56; val sc = 6 / k
            val cx0 = ((x0 + x1) / 2 - cw / 2).coerceIn(0, bw - cw); val cy0 = ((y0 + y1) / 2 - ch / 2 - 8).coerceIn(0, bh - ch)
            for ((step, d) in poke.steps.withIndex()) {
                val ages = moments(d)
                val fw = cw * k * sc; val fh = ch * k * sc
                val cols = 3; val rows = (ages.size + cols - 1) / cols
                val img = BufferedImage((fw + 6) * cols, (fh + 6) * rows, BufferedImage.TYPE_INT_RGB)
                for ((i, age) in ages.withIndex()) {
                    val c = render(art, frame(art, age, mapOf(poke.id to Poked(step, start)), hour), k)
                    val ox = i % cols * (fw + 6); val oy = i / cols * (fh + 6)
                    for (y in 0 until fh) for (x in 0 until fw) img.setRGB(ox + x, oy + y, c.pixels[(cy0 * k + y / sc) * c.width + cx0 * k + x / sc])
                }
                ImageIO.write(img, "png", File(dir, "$art-${poke.id}-$step-${hour.toInt()}h-d$k.png"))
            }
        }
    }
}
