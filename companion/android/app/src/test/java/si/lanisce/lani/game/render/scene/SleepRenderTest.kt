package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Poked
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.Sleep
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Asleep in a bed ([Pose.SLEEP], [Sleep]): Pastir Luka in the tent's sleeping bag, Babica Micka on the bench by the kitchen's
 * stove. They lie there (not as they stand), breathe, the Zzz rise; a tap turns them over, another has them grumble, at every
 * detail, and they stay there to tap; the lights go low for them; a window of the picture is that part of the whole; a frame
 * stays fast. Snapshots for review in build/scene-snapshots/sleep.
 */
class SleepRenderTest {
    private val dir = File("build/scene-snapshots/sleep").apply { mkdirs() }
    private val bw = 270; private val bh = 297

    /** Who sleeps in each art with a bed, and their sprite. */
    private val sleepers = mapOf("tent" to PersonInScene("luka", "shepherd", "bed", pose = Pose.SLEEP), "kitchen" to PersonInScene("babica", "grandma", "zapecek", pose = Pose.SLEEP))

    private fun frame(art: String, hour: Float = 23.5f, time: Double = 3.0, people: List<PersonInScene> = listOf(sleepers.getValue(art)), pokes: Map<String, Poked> = emptyMap(), month: Int = 7) =
        SceneFrame(time = time, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), people = people, pokes = pokes)

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = bw, h: Int = bh): Pair<PixelCanvas, List<SceneHit>> {
        val c = PixelCanvas(w * k, h * k)
        return c to ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
    }

    private fun poke(art: String, step: Int, age: Double, time: Double = 3.0) = mapOf(Sleep.pokeId(sleepers.getValue(art).id) to Poked(step, time - age))

    private fun changed(a: PixelCanvas, b: PixelCanvas): Int = a.pixels.indices.count { a.pixels[it] != b.pixels[it] }

    @Test fun `asleep they lie in the bed, not as they stand, and can be tapped there`() {
        for ((art, p) in sleepers) {
            val painter = ScenePainters.create(art)
            val c = PixelCanvas(bw, bh)
            val hits = painter.render(c, frame(art))
            val hit = hits.single { it.target == SceneTarget.Person(p.id) }
            val ww = hit.right - hit.left; val hh = hit.bottom - hit.top
            assertTrue("$art: lying along the bed: $hit", hh in 10..36 && ww in 20..44)
            var found = false
            for (y in hit.top until hit.bottom) for (x in hit.left until hit.right) if (!found && painter.targetAt(c, x, y, 0) == hit.target) found = true
            assertTrue("$art: the sleeper can be tapped on their own pixels", found)
            // the same person standing at a spot of the scene is another picture and another hit
            val standing = frame(art, people = listOf(p.copy(slot = SceneArt.personSlots.getValue(art).first(), pose = Pose.IDLE)))
            val (s, sh) = render(art, standing)
            assertTrue("$art: lying differs from standing", changed(c, s) > 200)
            assertNotEquals(art, hit, sh.single { it.target == hit.target })
        }
    }

    @Test fun `asleep they breathe, and the Zzz rise over them`() {
        for ((art, p) in sleepers) {
            val frames = (0 until 8).map { k -> render(art, frame(art, time = 3.0 + k * 0.45)) }
            val hit = frames[0].second.single { it.target == SceneTarget.Person(p.id) }
            // above the head and about it: the Zzz, blended, rising and changing from frame to frame
            val x0 = hit.left - 8; val x1 = hit.right + 8; val y0 = hit.top - 26; val y1 = hit.bottom
            val looks = frames.map { (c, _) -> (y0 until y1).flatMap { y -> (x0 until x1).map { x -> c.pixels[y * c.width + x] } } }.toSet()
            assertTrue("$art: the sleeper and their Zzz are alive (${looks.size} looks)", looks.size >= 6)
            // the Zzz are drawn over the picture, glowing: pale pixels above the head that aren't there without the sleeper
            val (empty, _) = render(art, frame(art, people = emptyList()))
            val zs = frames.sumOf { (c, _) -> (y0 until hit.top).sumOf { y -> (x0 until x1).count { x -> val v = c.pixels[y * c.width + x]; v != empty.pixels[y * c.width + x] && Col.b(v) > 180 && Col.r(v) > 150 } } }
            assertTrue("$art: Zzz above the sleeper ($zs px)", zs > 40)
        }
    }

    @Test fun `a tap turns them over and another has them grumble, at every detail, and they stay there to tap`() {
        val failed = ArrayList<String>()
        for ((art, p) in sleepers) for ((step, d) in listOf(Sleep.TURN_S, Sleep.MUMBLE_S).withIndex()) for (k in 1..Lens.MAX_DETAIL) {
            val diff = (0 until 5).sumOf { i ->
                val age = d * (i + 0.5) / 5
                changed(render(art, frame(art), k).first, render(art, frame(art, pokes = poke(art, step, age)), k).first)
            }
            if (diff < 60 * k * k) failed += "$art ${if (step == 0) "turning over" else "grumbling"} at detail $k: $diff px change"
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
        for (art in sleepers.keys) {
            val rest = render(art, frame(art)).second.map { it.target }.toSet()
            for (step in 0..1) for (age in listOf(0.2, 1.0, 2.2)) {
                assertEquals("$art step $step at $age s: the same things and people", rest, render(art, frame(art, pokes = poke(art, step, age))).second.map { it.target }.toSet())
            }
        }
    }

    @Test fun `the lights go low for a sleeper`() {
        // the tent's lantern turned low, the kitchen's lamp out: the room is darker at night than with nobody asleep
        for (art in sleepers.keys) {
            val (asleep, _) = render(art, frame(art))
            val (awake, _) = render(art, frame(art, people = emptyList()))
            fun light(c: PixelCanvas) = c.pixels.sumOf { (Col.r(it) + Col.g(it) + Col.b(it)).toLong() }
            assertTrue("$art: ${light(asleep)} vs ${light(awake)}", light(asleep) < light(awake) * 0.97)
        }
    }

    @Test fun `a window of the picture is that part of the whole, asleep and poked`() {
        val w = 240; val h = 200
        for (art in sleepers.keys) {
            val frames = listOf(frame(art, time = 3.4), frame(art, time = 3.4, pokes = poke(art, 0, 1.1, 3.4)), frame(art, time = 3.4, pokes = poke(art, 1, 1.2, 3.4)))
            for (k in 2..Lens.MAX_DETAIL) for (f in frames) {
                val all = render(art, f, k, w, h).first
                val ww = 150; val wh = 110
                for ((x0, y0) in listOf(0 to 0, (w * k - ww) to (h * k - wh), ((w * k - ww) / 2 - 20) to ((h * k - wh) / 2 - 12))) {
                    val win = PixelCanvas(ww, wh)
                    ScenePainters.create(art).render(win, f, Lens(k, x0, y0, w, h))
                    var bad = 0
                    for (y in 1 until wh - 1) for (x in 1 until ww - 1) if (win.pixels[y * ww + x] != all.pixels[(y0 + y) * all.width + x0 + x]) bad++
                    assertTrue("$art at detail $k, window at ($x0, $y0): $bad pixels differ", bad <= ww * wh / 500)
                }
            }
            // and the hits stay where they are at detail 1; but for the bedding under the sleeper, the pillow and the sleeping
            // bag: only slivers of them show round the head and the cover, finer closer up (the view's anchors are detail 1's)
            val base = render(art, frame(art)).second.associateBy { it.target }
            val under = setOf(SceneTarget.Thing("pillow"), SceneTarget.Thing("sleepingbag"))
            val moved = ArrayList<String>()
            for (k in 2..Lens.MAX_DETAIL) for (hit in render(art, frame(art), k).second) {
                if (hit.target in under) continue
                val b = base.getValue(hit.target)
                val off = maxOf(abs(hit.left - b.left), abs(hit.top - b.top), abs(hit.right - b.right), abs(hit.bottom - b.bottom))
                if (off > 3) moved += "$art at detail $k: ${hit.target} moved $off px ($b → $hit)"
            }
            assertTrue(moved.joinToString("\n", prefix = "\n"), moved.isEmpty())
        }
    }

    @Test fun `a night with a sleeper renders fast`() {
        for (art in sleepers.keys) {
            val p = ScenePainters.create(art)
            val c = PixelCanvas(360, 240)
            val f = frame(art, hour = 22.5f).copy(people = frame(art).people + PersonInScene("zala", "child2", SceneArt.personSlots.getValue(art).first()))
            repeat(15) { p.render(c, f.copy(time = it / 12.0)) }
            val n = 40
            val t0 = System.nanoTime()
            repeat(n) { p.render(c, f.copy(time = 2 + it / 12.0, pokes = if (it % 2 == 0) poke(art, it % 4 / 2, 0.8, 2 + it / 12.0) else emptyMap())) }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            println("ScenePainter %s asleep: %.2f ms/frame (night, 360x240)".format(art, ms))
            assertTrue("$art asleep too slow: $ms ms", ms < 20.0)
            // closer up, the view's worth of the picture at detail 3
            val lens = Lens(3, (360 * 3 - 360) / 2, (240 * 3 - 240) / 2, 360, 240)
            repeat(5) { p.render(c, f.copy(time = it / 12.0), lens) }
            val t1 = System.nanoTime()
            repeat(20) { p.render(c, f.copy(time = 2 + it / 12.0), lens) }
            val msFine = (System.nanoTime() - t1) / 1e6 / 20
            println("ScenePainter %s asleep: %.2f ms/frame (night, detail 3)".format(art, msFine))
            assertTrue("$art asleep closer up too slow: $msFine ms", msFine < 60.0)
        }
    }

    @Test fun `render the tent and the kitchen at night`() {
        for ((art, p) in sleepers) {
            // the whole scene, 4 screen px per canvas px: at night asleep, and the same place by day
            write(render(art, frame(art)).first, File(dir, "$art-night.png"), 4)
            write(render(art, frame(art, hour = 14f, people = listOf(p.copy(slot = SceneArt.personSlots.getValue(art).first(), pose = Pose.IDLE)))).first, File(dir, "$art-day.png"), 4)
            write(render(art, frame(art, hour = 23.5f, month = 1)).first, File(dir, "$art-night-winter.png"), 4)
            // closer: around the sleeper at detail 1 and 3, at rest, turning over and grumbling
            val hit = render(art, frame(art)).second.single { it.target == SceneTarget.Person(p.id) }
            val cw = 110; val ch = 90
            val x0 = ((hit.left + hit.right) / 2 - cw / 2).coerceIn(0, bw - cw); val y0 = ((hit.top + hit.bottom) / 2 - ch / 2).coerceIn(0, bh - ch)
            val shots = listOf("rest" to emptyMap(), "turn" to poke(art, 0, 1.2), "grumble" to poke(art, 1, 1.2))
            val sheet = BufferedImage((cw * 6 + 8) * shots.size, (ch * 6 + 8) * 2, BufferedImage.TYPE_INT_RGB)
            for ((i, shot) in shots.withIndex()) for ((j, k) in listOf(1, 3).withIndex()) {
                val c = render(art, frame(art, pokes = shot.second), k).first
                for (y in 0 until ch * 6) for (x in 0 until cw * 6) sheet.setRGB(i * (cw * 6 + 8) + x, j * (ch * 6 + 8) + y, c.pixels[((y0 * 6 + y) * k / 6) * c.width + (x0 * 6 + x) * k / 6])
            }
            ImageIO.write(sheet, "png", File(dir, "00-$art-asleep-closer.png"))
        }
    }

    private fun write(c: PixelCanvas, f: File, s: Int) {
        val img = BufferedImage(c.width * s, c.height * s, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until img.height) for (x in 0 until img.width) img.setRGB(x, y, c.pixels[(y / s) * c.width + x / s])
        ImageIO.write(img, "png", f)
    }
}
