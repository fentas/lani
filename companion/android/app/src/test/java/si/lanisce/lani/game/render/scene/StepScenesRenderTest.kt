package si.lanisce.lani.game.render.scene

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.FrameBudget
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneWorld
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * What a dialog says happens shows in its picture (docs/plans/05-built/scene-coherence.md). A project step played in a scene
 * that draws its project shows the step's work done (each step with a `done` line: its stage looks different from the one
 * before); the mill's last step turns its wheel as the water is let in, before the mill is finished (its paddles move between
 * frames under "wheel" 1, still under 0: Jan's "look, the wheel is turning — nothing is turning"); the stream's flock comes as
 * many as the dialog counts and is gone at 1, the one who comes last after them; the cart bridge's planks wait on the bank
 * before the deck is laid; the maypole's bark lies under it once peeled. Renders them for review (build/scene-snapshots/coherence).
 */
class StepScenesRenderTest {
    private val bw = 270; private val bh = 297
    private val dir = File("build/scene-snapshots/coherence").apply { mkdirs() }
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "scenes").isDirectory && File(it, "cultures").isDirectory }

    private fun frame(art: String, world: SceneWorld, fx: Map<String, Float> = emptyMap(), time: Double = 3.0, count: Int? = null, hour: Float = 14f, people: Boolean = false) = SceneFrame(
        time = time, hour = hour, month = 7, objects = SceneArt.objects.getValue(art).toSet(), fx = fx, world = world, wild = emptySet(), count = count,
        people = if (!people) emptyList() else SceneArt.personSlots.getValue(art).mapIndexed { k, slot -> PersonInScene("p-$slot", listOf("farmer", "grandma", "shepherd")[k % 3], slot) },
    )

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = bw, h: Int = bh): PixelCanvas {
        val c = PixelCanvas(w * k, h * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
        return c
    }

    private fun changed(a: PixelCanvas, b: PixelCanvas, mask: BooleanArray? = null): Int =
        a.pixels.indices.count { (mask == null || mask[it]) && a.pixels[it] != b.pixels[it] }

    private fun write(c: PixelCanvas, name: String) {
        val img = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height) for (x in 0 until c.width) img.setRGB(x, y, c.pixels[y * c.width + x])
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    /** The world with only [project] at [steps] (the others not begun: what the step's picture changes stands out). */
    private fun at(project: String, steps: Int) = SceneWorld(projects = mapOf(project to steps))

    @Test fun `each step with a done line shows its work in its scene`() {
        val files = listOf(File(companion, "scenes"), File(companion, "cultures/primorska/scenes")).flatMap { d -> d.listFiles { f -> f.extension == "json" }.orEmpty().toList() }
        val arts: Map<String, String> = files.map { f -> Json.parseToJsonElement(f.readText()).jsonObject }
            .associate { o -> (o["id"] as JsonPrimitive).content to (o["art"] as JsonPrimitive).content }
        val checked = ArrayList<String>()
        val failed = ArrayList<String>()
        for (f in File(companion, "cultures/primorska/project-steps").listFiles { x -> x.extension == "json" }.orEmpty().sortedBy { it.name }) {
            val p = Json.parseToJsonElement(f.readText()).jsonObject
            val project = (p["project"] as JsonPrimitive).content
            (p["steps"] as JsonArray).forEachIndexed { k, st ->
                val step = st as? JsonObject ?: return@forEachIndexed
                val levels: List<JsonArray> = (step["levels"] as JsonObject).values.map { lv -> lv.jsonObject["lines"] as JsonArray }
                if (levels.none { l -> l.any { line -> line.jsonObject["done"] != null } }) return@forEachIndexed
                val art = arts.getValue((step["scene"] as JsonPrimitive).content)
                for (detail in 1..2) {
                    val diff = changed(render(art, frame(art, at(project, k)), detail), render(art, frame(art, at(project, k + 1)), detail))
                    if (diff < 15 * detail * detail) failed += "$project/$k in $art at detail $detail: $diff px change"
                }
                checked += "$project/$k"
                write(render(art, frame(art, at(project, k + 1)), 2), "step-$project-${k + 1}")
            }
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
        assertEquals(23, checked.size)
    }

    @Test fun `the mill's wheel turns as the water is let in, before the mill grinds`() {
        val built = at("mlin", 5) // the wheel and the flume in place: the last step (the first flour) is on
        for (k in 1..Lens.MAX_DETAIL) {
            // where the wheel and the flume are: what the step before them didn't have
            val a = render("stream", frame("stream", at("mlin", 4)), k)
            val b = render("stream", frame("stream", built), k)
            val mask = BooleanArray(a.pixels.size) { a.pixels[it] != b.pixels[it] }
            assertTrue("the wheel is drawn at detail $k", mask.count { it } > 40 * k)
            fun moved(world: SceneWorld, fx: Map<String, Float>) =
                changed(render("stream", frame("stream", world, fx, 3.0), k), render("stream", frame("stream", world, fx, 3.17), k), mask)
            // Tine's "Voda teče … Glejte, kolo se vrti!": wheel 1 on a wheel whose mill isn't finished yet
            val turning = moved(built, mapOf("wheel" to 1f))
            assertTrue("the paddles move under the water at detail $k: $turning px", turning > 25 * k * k)
            // still before the water comes (France's "Danes spustimo vodo na kolo!"), dry and unmoving
            assertTrue("still before the water at detail $k", moved(built, emptyMap()) <= 2 * k)
            assertTrue("still at 0 at detail $k", moved(built, mapOf("wheel" to 0f)) <= 2 * k)
            // and once the mill grinds, an easy turn by itself
            assertTrue("the finished mill's wheel turns at detail $k", moved(at("mlin", 6), emptyMap()) > 10 * k * k)
        }
        for ((name, world, fx) in listOf(
            Triple("mill-5-dry", built, emptyMap<String, Float>()), Triple("mill-5-water", built, mapOf("wheel" to 1f)), Triple("mill-6", at("mlin", 6), emptyMap<String, Float>()),
        )) for (t in listOf(3.0, 3.17)) write(render("stream", frame("stream", world, fx, t), 2), "$name-${"%.2f".format(t)}")
    }

    @Test fun `the flock comes as many as the dialog counts, and goes home`() {
        val world = SceneWorld.ALL
        val plain = render("stream", frame("stream", world))
        assertEquals("not here yet at 0", 0, changed(plain, render("stream", frame("stream", world, mapOf("flock" to 0f)))))
        assertEquals("gone home at 1", 0, changed(plain, render("stream", frame("stream", world, mapOf("flock" to 1f)))))
        val few = changed(plain, render("stream", frame("stream", world, mapOf("flock" to 0.5f), count = 6)))
        val many = changed(plain, render("stream", frame("stream", world, mapOf("flock" to 0.5f), count = 24)))
        val twenty = changed(plain, render("stream", frame("stream", world, mapOf("flock" to 0.5f))))
        assertTrue("a flock of six at the water: $few px", few > 150)
        assertTrue("twenty-four more than six: $many > $few", many > few * 2)
        assertTrue("twenty by default, between them: $twenty", twenty in (few + 1) until many)
        // coming and going: on its way they're elsewhere than at the water
        val coming = render("stream", frame("stream", world, mapOf("flock" to 0.2f), count = 12))
        val there = render("stream", frame("stream", world, mapOf("flock" to 0.5f), count = 12))
        val going = render("stream", frame("stream", world, mapOf("flock" to 0.75f), count = 12))
        assertTrue(changed(coming, there) > 100 && changed(there, going) > 100)
        // the one who comes last: not there at 0, trotting in, drinking at 1, home with the flock
        assertEquals(0, changed(plain, render("stream", frame("stream", world, mapOf("straggler" to 0f)))))
        assertTrue(changed(plain, render("stream", frame("stream", world, mapOf("straggler" to 1f)))) > 20)
        assertEquals("home with them", 0, changed(plain, render("stream", frame("stream", world, mapOf("straggler" to 1f, "flock" to 1f)))))
        for ((name, fx) in listOf(
            "flock-coming" to mapOf("flock" to 0.2f), "flock-drinking" to mapOf("flock" to 0.5f), "flock-last" to mapOf("flock" to 0.5f, "straggler" to 1f),
            "flock-home" to mapOf("flock" to 0.72f, "straggler" to 1f),
        )) write(render("stream", frame("stream", world, fx, count = 18, hour = 18.5f, people = true), 2), name)
    }

    @Test fun `a flock at the water keeps the frame budget`() {
        val p = ScenePainters.of("stream")
        val f = frame("stream", SceneWorld.ALL, mapOf("flock" to 0.5f, "straggler" to 1f, "lamb" to 1f), count = 24, hour = 22.5f, people = true)
        val c = PixelCanvas(360, 240)
        repeat(15) { p.render(c, f.copy(time = it / 12.0)) }
        val n = 40
        val t0 = System.nanoTime()
        repeat(n) { p.render(c, f.copy(time = 2 + it / 12.0)) }
        val ms = (System.nanoTime() - t0) / 1e6 / n
        println("StepScenes: stream with a flock of 24 %.2f ms/frame (night, 360x240)".format(ms))
        assertTrue("too slow: $ms ms", ms < FrameBudget.scaled(20.0))
    }

    @Test fun `the bridge's planks wait on the bank, the maypole's bark lies under it`() {
        // the cart bridge's fourth stage (the logs across) has the deck's ten planks stacked by the ramp; laid, they're gone
        val logs = render("stream", frame("stream", at("most", 4)), 2)
        write(logs, "most-4-planks")
        assertTrue(changed(logs, render("stream", frame("stream", at("most", 3)), 2)) > 0)
        // the maypole peeled: its bark in strips on the cobbles, gone once the wreath is there
        val peeled = render("square", frame("square", at("mlaj", 3)), 2)
        write(peeled, "mlaj-3-bark")
        assertTrue(changed(peeled, render("square", frame("square", at("mlaj", 2)), 2)) > 0)
    }
}
