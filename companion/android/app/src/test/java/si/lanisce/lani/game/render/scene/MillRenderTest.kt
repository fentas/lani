package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Poked
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.Sky
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The mill inside (art "mill", [MillPainter]; companion/SCENES.md "Arts"): its looks for review in build/scene-snapshots/mill
 * (by day and at night, the seasons, the weather, every detail, each effect from 0 to 1, its people, its pokes), and what
 * its effects promise: the stones and the wheels still at `grind` 0 and turning together otherwise, the hopper and the flour
 * sack filling, the cat's pounce, the lantern, the scale and the measure.
 */
class MillRenderTest {
    private val dir = File("build/scene-snapshots/mill").apply { mkdirs() }
    private val bw = 270; private val bh = 297
    private val art = "mill"

    private val france = PersonInScene("france", "farmer", "stones")
    private val tine = PersonInScene("tine", "child3", "sacks")
    private val micka = PersonInScene("micka", "grandma", "door")

    private fun frame(hour: Float = 14f, month: Int = 7, time: Double = 3.0, fx: Map<String, Float> = emptyMap(), people: List<PersonInScene> = emptyList(), sky: Sky = Sky.CLEAR, pokes: Map<String, Poked> = emptyMap()) =
        SceneFrame(time = time, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), people = people, fx = fx, sky = sky, pokes = pokes)

    private fun render(f: SceneFrame, k: Int = 1): Triple<PixelCanvas, List<SceneHit>, si.lanisce.lani.game.scene.ScenePainter> {
        val p = ScenePainters.create(art)
        val c = PixelCanvas(bw * k, bh * k)
        return Triple(c, p.render(c, f, Lens(k, 0, 0, bw, bh)), p)
    }

    private fun write(c: PixelCanvas, name: String, scale: Int = 3) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    /** The pixels of [c] that thing [slot] painted (a tap on them is on it), as a list of (index, colour). */
    private fun pixelsOf(c: PixelCanvas, p: si.lanisce.lani.game.scene.ScenePainter, slot: String): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (y in 0 until c.height) for (x in 0 until c.width) if (p.targetAt(c, x, y, 0) == SceneTarget.Thing(slot)) out += (y * c.width + x) to c.pixels[y * c.width + x]
        return out
    }

    @Test fun `render the mill for review`() {
        val everyone = listOf(france.copy(talking = true), tine, micka)
        val views = listOf(
            "day" to frame(people = everyone), "morning-empty" to frame(hour = 8.5f), "evening" to frame(hour = 19.8f, month = 10, people = everyone),
            "night" to frame(hour = 23f, month = 1), "winter-day" to frame(hour = 11f, month = 1, people = listOf(france)),
            "autumn" to frame(hour = 16f, month = 10), "spring" to frame(hour = 9.5f, month = 4),
            "rain" to frame(sky = Sky(rain = 0.8f, gloom = 0.7f, wind = 0.3f), people = listOf(tine)),
            "storm-night" to frame(hour = 22f, sky = Sky(rain = 1f, gloom = 1f, wind = 0.8f, lightning = true), time = 3.4),
        )
        for ((name, f) in views) for (k in 1..Lens.MAX_DETAIL) write(render(f, k).first, "$name-d$k", 6 / k)
        // each effect from 0 to 1, by day
        for (name in SceneArt.effects.getValue(art)) for (v in listOf(0f, 0.3f, 0.6f, 1f)) write(render(frame(fx = mapOf(name to v), people = listOf(france, tine))).first, "fx-$name-${(v * 10).toInt()}", 3)
        // its pokes, each step a moment in
        for (pk in ScenePainters.create(art).pokes) for (step in pk.steps.indices) for (age in listOf(0.4, 1.0)) {
            write(render(frame(time = 3.0, pokes = mapOf(pk.id to Poked(step, 3.0 - age)))).first, "poke-${pk.id}-$step-${(age * 10).toInt()}", 3)
        }
    }

    @Test fun `stopped, the stones and the wheels stand still, grinding, they turn`() {
        for (slot in listOf("wheel", "gear", "millstone")) {
            val (a, _, pa) = render(frame(fx = mapOf("grind" to 0f), time = 3.0))
            val (b, _, pb) = render(frame(fx = mapOf("grind" to 0f), time = 3.45))
            assertEquals("$slot still at grind 0", pixelsOf(a, pa, slot), pixelsOf(b, pb, slot))
            for (g in listOf(null, 1f)) {
                val fx = g?.let { mapOf("grind" to it) }.orEmpty()
                val (c, _, pc) = render(frame(fx = fx, time = 3.0))
                val (d, _, pd) = render(frame(fx = fx, time = 3.45))
                assertTrue("$slot turns at grind $g", pixelsOf(c, pc, slot) != pixelsOf(d, pd, slot))
            }
        }
    }

    @Test fun `the hopper and the flour sack fill, the cat pounces, the lantern lights, the scale and the measure take their load`() {
        fun changed(a: PixelCanvas, b: PixelCanvas) = a.pixels.indices.count { a.pixels[it] != b.pixels[it] }
        val base = render(frame()).first
        // a level of each effect looks different from the place by itself, and 0 from 1
        for (name in listOf("pour", "sack", "cat", "weigh", "toll", "dust")) {
            val lo = render(frame(fx = mapOf(name to 0f))).first
            val hi = render(frame(fx = mapOf(name to 1f))).first
            assertTrue("$name: 0 differs from 1", changed(lo, hi) > 25)
            assertTrue("$name: 1 differs from the place by itself", changed(base, hi) > 25)
        }
        // the lantern: dark at 0 at night, lit at 1 by day
        val night = frame(hour = 23f)
        assertTrue("put out at night", changed(render(night).first, render(night.copy(fx = mapOf("lantern" to 0f))).first) > 100)
        assertTrue("lit by day", changed(base, render(frame(fx = mapOf("lantern" to 1f))).first) > 100)
        // the cat lands by the hole: its hit moves toward the mouse
        val crouched = render(frame(fx = mapOf("cat" to 0f))).second.first { it.target == SceneTarget.Thing("cat") }
        val landed = render(frame(fx = mapOf("cat" to 1f))).second.first { it.target == SceneTarget.Thing("cat") }
        val mouse = render(frame()).second.first { it.target == SceneTarget.Thing("mouse") }
        fun dist(a: SceneHit, b: SceneHit) = Math.hypot(((a.left + a.right) - (b.left + b.right)) / 2.0, ((a.top + a.bottom) - (b.top + b.bottom)) / 2.0)
        assertTrue("the cat lands nearer the mouse hole", dist(landed, mouse) < dist(crouched, mouse))
    }

    @Test fun `the people stand clear at their spots, every thing can be tapped, at every detail`() {
        val f = frame(people = listOf(france, tine, micka))
        val base = render(f).second.associateBy { it.target }
        for (k in 1..Lens.MAX_DETAIL) {
            val (_, hits, _) = render(f, k)
            assertEquals("detail $k: everything and everyone", base.keys, hits.map { it.target }.toSet())
        }
        for (slot in SceneArt.objects.getValue(art)) assertTrue(slot, base.containsKey(SceneTarget.Thing(slot)))
        for (p in listOf(france, tine, micka)) assertTrue(p.id, base.containsKey(SceneTarget.Person(p.id)))
    }
}
