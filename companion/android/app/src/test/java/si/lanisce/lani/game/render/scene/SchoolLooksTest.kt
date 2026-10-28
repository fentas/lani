package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import si.lanisce.lani.FrameBudget
import org.junit.Test
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.Blackboard
import si.lanisce.lani.game.scene.CultureLooks
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The school in the Friuli village's look (companion/SCENES.md, "Culture looks"; CultureLooks.FRIULI_SCHOOL): an Italian
 * classroom, its map Friuli's, the tricolore on the pole, the painted lower walls, the vine in the frieze, the
 * pallottoliere by the teacher's desk and the Italian greeting on the board; every thing still there to tap and every
 * person clear, at every detail, as fast as the Slovene one. Rendered for review: build/scene-snapshots/looks/.
 */
class SchoolLooksTest {
    private val dir = File("build/scene-snapshots/looks").apply { mkdirs() }
    private val objects = SceneArt.objects.getValue("school").toSet()
    private val people = listOf(
        PersonInScene("elena", "teacher", "teacher"), PersonInScene("lorenzo", "child3", "desk1"),
        PersonInScene("giulia", "child2", "desk2"), PersonInScene("tommaso", "child1", "door"),
    )

    private fun frame(culture: String, hour: Float = 10.5f, month: Int = 10, board: Blackboard? = null) =
        SceneFrame(time = 3.0, hour = hour, month = month, objects = objects, people = people, world = SceneWorld(culture = culture, board = board))

    private fun render(f: SceneFrame, k: Int, w: Int = 240, h: Int = 160): Pair<PixelCanvas, List<SceneHit>> {
        val c = PixelCanvas(w * k, h * k)
        return c to ScenePainters.create("school").render(c, f, Lens(k, 0, 0, w, h))
    }

    private fun write(c: PixelCanvas, name: String, scale: Int) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    /** How many pixels differ inside [hit] (scene canvas px, at detail [k]). */
    private fun changed(a: PixelCanvas, b: PixelCanvas, hit: SceneHit, k: Int): Int {
        var n = 0
        for (y in hit.top * k until hit.bottom * k) for (x in hit.left * k until hit.right * k) if (a.pixels[y * a.width + x] != b.pixels[y * b.width + x]) n++
        return n
    }

    @Test fun `the Friuli school is an Italian classroom and the Slovene one stays as it was`() {
        assertEquals(setOf("school"), CultureLooks.touched.getValue("friuli"))
        for (k in 1..3) {
            val (sl, slHits) = render(frame("primorska"), k)
            val (fr, frHits) = render(frame("friuli"), k)
            write(sl, "school-primorska-day-k$k", if (k == 1) 4 else 2)
            write(fr, "school-friuli-day-k$k", if (k == 1) 4 else 2)
            // every thing and person there to tap, in the same places
            assertEquals("k$k: the same hits", slHits.map { h -> h.target }.toSet(), frHits.map { h -> h.target }.toSet())
            for (t in objects) assertTrue("k$k: $t there", frHits.any { h -> h.target == SceneTarget.Thing(t) })
            // the map is another, and so are the walls (the window and the board's frame stand on them)
            val map = frHits.first { h -> h.target == SceneTarget.Thing("map") }
            assertTrue("k$k: the map is Friuli's", changed(sl, fr, map, k) > (map.right - map.left) * (map.bottom - map.top) * k * k / 3)
            val board = frHits.first { h -> h.target == SceneTarget.Thing("blackboard") }
            assertTrue("k$k: the board's greeting is Italian", changed(sl, fr, board, k) > 10)
            // the desks, the chalk and the globe are the Slovene ones
            for (same in listOf("desk", "chalk", "notebook")) {
                val h = frHits.first { x -> x.target == SceneTarget.Thing(same) }
                assertTrue("k$k: the $same as it was", changed(sl, fr, h, k) < (h.right - h.left) * (h.bottom - h.top) * k * k / 4)
            }
        }
        // at night, in winter, with the lesson on the board and closer up: drawn, deterministic
        val night = frame("friuli", hour = 22.5f, month = 1, board = Blackboard("Essere: sono, sei, è", listOf(listOf("io", "sono"), listOf("tu", "sei"), listOf("lui", "è")), "it"))
        for (k in 1..3) {
            val (a, _) = render(night, k)
            val (b, _) = render(night, k)
            assertArrayEquals("k$k: deterministic", a.pixels, b.pixels)
            write(a, "school-friuli-night-lesson-k$k", if (k == 1) 4 else 2)
        }
        val (tall, _) = render(frame("friuli", hour = 15f, month = 5, board = Blackboard(null, language = "it")), 1, 270, 297)
        write(tall, "school-friuli-tall-welcome", 3)
    }

    @Test fun `the Italian greeting fits the board beside its sun`() {
        // the board as the painter writes it on the scene's canvas: 51 columns, the lines from column 4 on rows 0, 10 and
        // 18 under the board's top edge, which drops a row every second column (top: either way the half falls), each
        // letter from the edge at its left; the sun's centre 8 columns before the end on row 18, its ring and its eight rays
        val cols = 51
        for (half in 0..1) {
            fun top(col: Int) = (col + half) / 2
            val sun = HashSet<Pair<Int, Int>>()
            for (row in -8..8) for (col in -8..8) {
                val d = kotlin.math.hypot(col.toFloat(), row.toFloat())
                val a = kotlin.math.atan2(row.toFloat(), col.toFloat())
                val ray = d in 5.5f..7.6f && kotlin.math.abs(((a / (Math.PI.toFloat() / 4f)) + 8f) % 1f - 0.5f) > 0.38f
                if (ray || kotlin.math.abs(d - 3.6f) < 0.55f) sun += (cols - 8 + col) to (top(cols - 8 + col) + 18 + row)
            }
            for ((line, row0) in listOf("Ciao!" to 0, "Come va?" to 10, "Bene!" to 18)) {
                assertTrue("$line: written whole", line.filter { it != ' ' }.all { ChalkFont.glyph(it) != null })
                assertTrue("$line: closer up too", line.filter { it != ' ' }.all { ChalkHand.strokes(it).isNotEmpty() })
                val on = ArrayList<Pair<Int, Int>>()
                val w = ChalkFont.write(line, slant = true) { gx, k, ry ->
                    val p = (4 + gx + k) to (top(4 + gx) + row0 + ry)
                    if (p in sun) on += p
                }
                assertTrue("$line: $w columns on the board", 4 + w <= cols)
                assertEquals("$line: no letter on the sun", emptyList<Pair<Int, Int>>(), on)
            }
        }
    }

    @Test fun `the Friuli school renders as fast as the Slovene one`() {
        for (culture in listOf("primorska", "friuli")) {
            val p = ScenePainters.create("school")
            val f = frame(culture, hour = 22.5f).copy(people = people.mapIndexed { i, x -> x.copy(talking = i == 0) })
            val c = PixelCanvas(360, 240)
            repeat(15) { p.render(c, f.copy(time = it / 12.0)) }
            val n = 40
            val t0 = System.nanoTime()
            repeat(n) { p.render(c, f.copy(time = 2 + it / 12.0)) }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            println("school in $culture: %.2f ms/frame (night, 360x240)".format(ms))
            assertTrue("$culture too slow: $ms ms", ms < FrameBudget.scaled(20.0))
        }
    }
}
