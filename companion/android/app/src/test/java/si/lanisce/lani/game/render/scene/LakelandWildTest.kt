package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.MapLook
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.render.Wild
import si.lanisce.lani.game.render.WildTime
import si.lanisce.lani.game.render.Wildlife
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneWorld
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The Lakes have no wild boar and no wolves (docs/plans/05-built/polish-content.md): in the Lake District's look the field's
 * boar is a badger, the wolves a dialog sees by the wood at the gate and from the watchtower are foxes, and the map's night
 * has neither boar nor bear. Elsewhere the same as before. A contact sheet in build/scene-snapshots/lakeland-wild/.
 */
class LakelandWildTest {
    private val dir = File("build/scene-snapshots/lakeland-wild").apply { mkdirs() }
    private val lakes = SceneWorld(culture = "lakeland")
    private val home = SceneWorld.ALL

    private fun frame(art: String, world: SceneWorld, hour: Float, fx: Map<String, Float> = emptyMap(), wild: Set<String>? = null) =
        SceneFrame(time = 3.0, hour = hour, month = 6, objects = SceneArt.objects.getValue(art).toSet(), world = world, fx = fx, wild = wild)

    private fun render(art: String, f: SceneFrame, k: Int = 2): PixelCanvas =
        PixelCanvas(240 * k, 160 * k).also { ScenePainters.create(art).render(it, f, Lens(k, 0, 0, 240, 160)) }

    /** The pixels [a] has otherwise than [b]. */
    private fun diff(a: PixelCanvas, b: PixelCanvas): List<Int> = a.pixels.indices.filter { a.pixels[it] != b.pixels[it] }

    @Test fun `the Lakes' field has a badger where the boar digs elsewhere`() {
        for (hour in listOf(23f, 2f)) {
            // the animal out tonight (the frame says so, whatever the day's roll), or none
            val withLakes = render("field", frame("field", lakes, hour, wild = setOf("boar")))
            val noneLakes = render("field", frame("field", lakes, hour, wild = emptySet()))
            val withHome = render("field", frame("field", home, hour, wild = setOf("boar")))
            val noneHome = render("field", frame("field", home, hour, wild = emptySet()))
            val badger = diff(withLakes, noneLakes).map { withLakes.pixels[it] }.toSet()
            val boar = diff(withHome, noneHome).map { withHome.pixels[it] }.toSet()
            assertTrue("$hour: the badger is drawn", badger.isNotEmpty())
            assertTrue("$hour: the badger isn't the boar", badger != boar)
        }
    }

    @Test fun `what a dialog sees by the wood in the Lakes is foxes, the village's wolves stay wolves`() {
        for (art in listOf("palisade", "watchtower")) for (hour in listOf(11f, 22.5f)) {
            val fx = mapOf("wolves" to 0.6f, "gate" to 1f)
            val lakesA = render(art, frame(art, lakes, hour, fx)); val lakesB = render(art, frame(art, lakes, hour, fx - "wolves"))
            val homeA = render(art, frame(art, home, hour, fx)); val homeB = render(art, frame(art, home, hour, fx - "wolves"))
            val foxes = diff(lakesA, lakesB).map { lakesA.pixels[it] }.toSet()
            val wolves = diff(homeA, homeB).map { homeA.pixels[it] }.toSet()
            assertTrue("$art $hour: something by the wood", foxes.isNotEmpty() && wolves.isNotEmpty())
            assertTrue("$art $hour: the Lakes' aren't wolves", foxes != wolves)
        }
        // the village's wolves (the game's night threat, no cue) are wolves in the Lakes too: their eyes the wolves' yellow
        val a = render("palisade", frame("palisade", lakes.copy(wolves = true), 22.5f)); val b = render("palisade", frame("palisade", lakes, 22.5f))
        val eyes = diff(a, b).map { a.pixels[it] }.toSet()
        assertTrue("the wolves' eyes: $eyes", Col.hex(0xF8E070) in eyes && Col.hex(0xF0B048) !in eyes)
    }

    @Test fun `the Lakes' map has no wild boar and no bear at night, the others as anywhere`() {
        val absent = MapLook.of("lakeland").absentWild
        assertEquals(setOf(Wild.BOARS, Wild.BEAR), absent)
        assertEquals(emptySet<Wild>(), MapLook.of("primorska").absentWild)
        var badgers = 0
        for (day in 0L until 400L) for (month in listOf(4, 7, 10)) {
            val plan = Wildlife.plan(42, 20_000L + day, WildTime.NIGHT, month, absent).map { it.kind }
            assertTrue(plan.none { it in absent })
            if (Wild.BADGER in plan) badgers++
        }
        assertTrue("badgers at night in the Lakes: $badgers", badgers > 100)
        // elsewhere, the same plans as before
        for (day in 0L until 50L) assertEquals(Wildlife.plan(42, 20_000L + day, WildTime.NIGHT, 7), Wildlife.plan(42, 20_000L + day, WildTime.NIGHT, 7, emptySet()))
    }

    @Test fun `render the Lakes' wild sheet`() {
        val shots = listOf(
            render("field", frame("field", lakes, 23f, wild = setOf("boar", "bat")), 3), render("field", frame("field", home, 23f, wild = setOf("boar", "bat")), 3),
            render("palisade", frame("palisade", lakes, 11f, mapOf("wolves" to 0.6f, "gate" to 1f)), 3),
            render("palisade", frame("palisade", lakes, 22.5f, mapOf("wolves" to 0.6f, "gate" to 0f)), 3),
            render("watchtower", frame("watchtower", lakes, 11f, mapOf("wolves" to 0.6f)), 3),
            render("watchtower", frame("watchtower", lakes, 22.5f, mapOf("wolves" to 0.6f)), 3),
        )
        val w = shots[0].width; val h = shots[0].height
        val img = BufferedImage(w * 2, h * 3, BufferedImage.TYPE_INT_RGB)
        for ((i, c) in shots.withIndex()) for (y in 0 until h) for (x in 0 until w) img.setRGB((i % 2) * w + x, (i / 2) * h + y, c.pixels[y * w + x])
        ImageIO.write(img, "png", File(dir, "lakeland-wild.png"))
    }
}
