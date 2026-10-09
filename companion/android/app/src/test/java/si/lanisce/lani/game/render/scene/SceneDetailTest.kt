package si.lanisce.lani.game.render.scene

import si.lanisce.lani.FrameBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Poked
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.Sky
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * The finer looks of the scenes closer up (detail 2 and 3, see [si.lanisce.lani.game.scene.ScenePainter.render]):
 * snapshots of every art at every detail for review (build/scene-snapshots/detail), a window of the picture is
 * exactly that part of the whole picture, the hits stay where they are at detail 1, and a frame stays fast.
 * Each check goes through every art and then reports all that failed, one line per art ("<art> at detail k: …").
 */
class SceneDetailTest {
    private val dir = File("build/scene-snapshots/detail").apply { mkdirs() }

    private fun everyone(art: String, offset: Int = 0): List<PersonInScene> =
        SceneArt.personSlots.getValue(art).mapIndexed { k, slot ->
            PersonInScene("p-$slot", SceneArt.people[(offset + k) % SceneArt.people.size], slot, talking = k == 0)
        }

    private fun frame(art: String, hour: Float = 14f, month: Int = 7, time: Double = 3.0, sky: Sky = Sky.CLEAR, fx: Boolean = false, pokes: Map<String, Poked> = emptyMap()) = SceneFrame(
        time = time, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), people = everyone(art), sky = sky,
        fx = if (fx) SceneArt.effects.getValue(art).associateWith { 0.6f } else emptyMap(), pokes = pokes,
        // every wild animal out at once: the most there is to draw
        wild = si.lanisce.lani.game.scene.Wildlife.all(art),
    )

    /** Every poke of [art] at its [step] (the last one it has, for a shorter sequence), begun at [start]. */
    private fun poked(art: String, step: Int, start: Double): Map<String, Poked> =
        ScenePainters.create(art).pokes.associate { it.id to Poked(minOf(step, it.steps.lastIndex), start) }

    /** How many steps the longest of [art]'s pokes has. */
    private fun steps(art: String): Int = ScenePainters.create(art).pokes.maxOfOrNull { it.steps.size } ?: 0

    /** All the weather at once, the heaviest a dialog brings (a flash of lightning at 3.4 s); the frames with it have all the art's effects on too. */
    private val storm = Sky(rain = 1f, snow = 0.6f, fog = 0.7f, wind = 0.8f, gloom = 1f, lightning = true)

    /** The whole picture of [art] at [k] for a scene canvas of [bw] × [bh]. */
    private fun whole(art: String, f: SceneFrame, k: Int, bw: Int, bh: Int): PixelCanvas {
        val c = PixelCanvas(bw * k, bh * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, bw, bh))
        return c
    }

    @Test fun `render every art at every detail`() {
        val bw = 270; val bh = 297 // a phone's scene canvas
        val shower = Sky(rain = 0.8f, gloom = 0.7f, wind = 0.4f)
        val mist = Sky(fog = 0.8f, snow = 0.7f, gloom = 0.4f)
        val views = { art: String ->
            listOf(
                "day" to frame(art), "night" to frame(art, hour = 22.5f, month = 1),
                "rain" to frame(art, sky = shower), "mist" to frame(art, hour = 8.5f, month = 11, sky = mist),
            )
        }
        for (art in SceneArt.arts) for ((name, f) in views(art)) {
            for (k in 1..Lens.MAX_DETAIL) write(whole(art, f, k, bw, bh), File(dir, "$art-$name-d$k.png"), 6 / k)
            // the middle of the picture at each detail, side by side: what a closer look shows
            val cw = 90; val ch = 99
            val img = BufferedImage(cw * 6 * 3 + 16, ch * 6, BufferedImage.TYPE_INT_RGB)
            for (k in 1..3) {
                val c = whole(art, f, k, bw, bh)
                val x0 = (bw - cw) / 2 * k; val y0 = (bh - ch) / 2 * k
                val s = 6 / k
                for (y in 0 until ch * 6) for (x in 0 until cw * 6) img.setRGB((k - 1) * (cw * 6 + 8) + x, y, c.pixels[(y0 + y / s) * c.width + x0 + x / s])
            }
            ImageIO.write(img, "png", File(dir, "00-$art-$name-closer.png"))
        }
    }

    /** Runs [check] for every art, then fails with every art's failure (an exception counts as one). */
    private fun forEveryArt(check: (String) -> Unit) {
        val failed = ArrayList<String>()
        for (art in SceneArt.arts) try { check(art) } catch (e: Throwable) { failed.add("$art: ${e.message ?: e.javaClass.simpleName}") }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
    }

    @Test fun `a window of the picture is that part of the whole picture`() = forEveryArt { art ->
        val bw = 240; val bh = 200
        // summer (no falling leaves: they fall over the window), lamps lit; clear, and in all the weather at once; and
        // every poke going at each of its steps, a moment in and a while in (the cat round the house's corner)
        val frames = listOf(frame(art, hour = 21.5f), frame(art, hour = 21.5f, time = 3.4, sky = storm, fx = true)) +
            (0 until steps(art)).flatMap { s -> listOf(0.45, 1.5).map { age -> frame(art, hour = 21.5f, time = 3.4, pokes = poked(art, s, 3.4 - age)) } }
        for (k in 2..Lens.MAX_DETAIL) for (f in frames) {
            val all = whole(art, f, k, bw, bh)
            val ww = 150; val wh = 110
            for ((x0, y0) in listOf(0 to 0, (bw * k - ww) to (bh * k - wh), ((bw * k - ww) / 2 + 7) to ((bh * k - wh) / 2 - 5))) {
                val win = PixelCanvas(ww, wh)
                ScenePainters.create(art).render(win, f, Lens(k, x0, y0, bw, bh))
                var bad = 0; var first = ""
                // the window's own edge may lose an outline whose object lies just outside it
                for (y in 1 until wh - 1) for (x in 1 until ww - 1) {
                    if (win.pixels[y * ww + x] != all.pixels[(y0 + y) * all.width + x0 + x]) { if (bad++ == 0) first = "($x, $y)" }
                }
                val what = if (!f.sky.clear) " in the weather" else if (f.pokes.isNotEmpty()) " poked (step ${f.pokes.values.first().step})" else ""
                assertTrue("$art at detail $k$what, window at ($x0, $y0): $bad pixels differ from the whole picture, first at $first", bad <= ww * wh / 500)
            }
        }
    }

    @Test fun `the hits stay where they are at detail 1`() = forEveryArt { art ->
        val bw = 270; val bh = 297
        run {
            val f = frame(art)
            val base = ScenePainters.create(art).render(PixelCanvas(bw, bh), f).associateBy { it.target }
            for (k in 2..Lens.MAX_DETAIL) {
                val hits = ScenePainters.create(art).render(PixelCanvas(bw * k, bh * k), f, Lens(k, 0, 0, bw, bh))
                assertEquals("$art at detail $k: the things and people hit", base.keys, hits.map { it.target }.toSet())
                for (h in hits) {
                    val b = base.getValue(h.target)
                    val off = maxOf(abs(h.left - b.left), abs(h.top - b.top), abs(h.right - b.right), abs(h.bottom - b.bottom))
                    assertTrue("$art at detail $k: ${h.target} moved $off px (${b.left},${b.top}-${b.right},${b.bottom} → ${h.left},${h.top}-${h.right},${h.bottom})", off <= 3)
                }
            }
        }
    }

    @Test fun `a closer frame renders fast`() = forEveryArt { art ->
        val bw = 360; val bh = 300
        for (k in 2..Lens.MAX_DETAIL) for (sky in listOf(Sky.CLEAR, storm)) {
            val p = ScenePainters.create(art)
            // in all the weather, every poke going too: at its last step (the cat off on its dash, the pot boiling over)
            val f = frame(art, hour = 22.5f, month = 10, sky = sky, fx = !sky.clear, pokes = if (sky.clear) emptyMap() else poked(art, 9, 1.8))
            // the view's worth of the picture at that detail, as the scene view renders it
            val c = PixelCanvas(bw, bh)
            val lens = Lens(k, (bw * k - bw) / 2, (bh * k - bh) / 2, bw, bh)
            repeat(10) { p.render(c, f.copy(time = it / 12.0), lens) }
            val n = 30
            val t0 = System.nanoTime()
            repeat(n) { p.render(c, f.copy(time = 2 + it / 12.0), lens) }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            val weather = if (sky.clear) "" else " in all the weather, every effect and poke on"
            println("ScenePainter %s at detail %d: %.2f ms/frame (night%s, a %dx%d window)".format(art, k, ms, weather, bw, bh))
            assertTrue("$art at detail $k$weather too slow: $ms ms", ms < FrameBudget.scaled(25.0))
        }
    }

    private fun write(c: PixelCanvas, f: File, scale: Int) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", f)
    }
}
