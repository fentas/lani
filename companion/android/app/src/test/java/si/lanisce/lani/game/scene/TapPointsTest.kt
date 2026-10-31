package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.render.PixelCanvas

/**
 * Where QA taps a thing (TapPoints, SceneAnchors.point; companion/SCENES.md "I spy"): a pixel the thing painted itself, inside
 * it, so the tap lands on it, not on what lies in the middle of its area (the stream under a bridge's deck).
 */
class TapPointsTest {
    private val a = SceneTarget.Thing("deck")
    private val b = SceneTarget.Thing("stream")

    @Test fun `a thing with another in the middle of its area is tapped on its own pixels, inside them`() {
        // the deck's area is 20 x 10, the stream runs through its middle (columns 7 to 12) and is drawn over it
        val hits = listOf(SceneHit(a, 0, 0, 20, 10, 10, 0), SceneHit(b, 7, 0, 13, 10, 10, 0))
        val owner = { x: Int, _: Int -> if (x in 7..12) b else a }
        val p = TapPoints.of(a, hits, 20, 10, owner)!!
        assertEquals(a, owner(p.first, p.second))
        // inside: every pixel within two of it is the deck's too
        for (dy in -2..2) for (dx in -2..2) assertEquals("$p + ($dx, $dy)", a, owner(p.first + dx, (p.second + dy).coerceIn(0, 9)))
        // the middle of the area is the stream's
        assertEquals(b, owner(10, 5))
        // the stream itself: its own middle
        val q = TapPoints.of(b, hits, 20, 10, owner)!!
        assertTrue("$q", q.first in 9..10 && q.second in 4..5)
    }

    @Test fun `of the pixels inside, the one nearest the middle, and a painter that can't tell leaves it to the areas`() {
        val hits = listOf(SceneHit(a, 0, 0, 11, 11, 5, 0))
        assertEquals(5 to 5, TapPoints.of(a, hits, 11, 11) { _, _ -> a })
        // nothing painted to the pixel: the front-most area, here the stream's over the deck's right half
        val two = hits + SceneHit(b, 6, 0, 11, 11, 8, 0)
        val p = TapPoints.of(a, two, 11, 11) { _, _ -> null }!!
        assertTrue("$p", p.first < 6)
        // not drawn, or nothing of it its own
        assertNull(TapPoints.of(SceneTarget.Thing("owl"), hits, 11, 11) { _, _ -> a })
        assertNull(TapPoints.of(a, hits, 11, 11) { _, _ -> b })
    }

    @Test fun `in every art's picture each thing is tapped where the tap gets it, on its own pixels where it has any`() {
        val fails = ArrayList<String>()
        var n = 0
        var middles = 0
        var specks = 0
        val cultures = listOf(Cultures.DEFAULT) + CuratedContent.companion.resolve("cultures").listFiles().orEmpty().filter { it.resolve("scenes").isDirectory }.map { it.name }
        for (culture in cultures) for (art in SceneArt.arts) {
            val painter = ScenePainters.create(art)
            val objects = SceneArt.objects[art].orEmpty().toSet()
            for (hour in listOf(12.5f, 22.5f)) {
                val frame = SceneFrame(time = 2.0, hour = hour, month = 7, objects = objects, world = SceneWorld(culture = culture), wild = Wildlife.all(art))
                val c = PixelCanvas(240, 160)
                val hits = painter.render(c, frame)
                fun front(x: Int, y: Int) = hits.lastOrNull { x >= it.left && x < it.right && y >= it.top && y < it.bottom }?.target
                for (h in hits.filter { it.target is SceneTarget.Thing }.distinctBy { it.target }) {
                    val t = h.target
                    val at = "$culture/$art ${(t as SceneTarget.Thing).slot} at $hour"
                    val p = TapPoints.of(t, hits, c.width, c.height) { x, y -> painter.targetAt(c, x, y, 0) }
                    val box = (maxOf(h.top, 0) until minOf(h.bottom, c.height)).flatMap { y -> (maxOf(h.left, 0) until minOf(h.right, c.width)).map { x -> x to y } }
                    val owned = box.any { (x, y) -> painter.targetAt(c, x, y, 0) == t }
                    val reached = owned || box.any { (x, y) -> painter.targetAt(c, x, y, 0) == null && front(x, y) == t }
                    if (p == null) {
                        if (reached) fails += "$at: no tap point, though a tap can get it"
                        continue
                    }
                    n++
                    val (x, y) = p
                    val mine = painter.targetAt(c, x, y, 0)
                    if (owned && mine != t) fails += "$at: ($x, $y) is ${mine ?: "the scenery"}, not a pixel of its own"
                    if (!owned) specks++
                    // as the tap: the pixel's owner (within the tap's slop), else the front-most area
                    val got = painter.targetAt(c, x, y, 3) ?: front(x, y)
                    if (owned && got != t) fails += "$at: a tap at ($x, $y) gets ${got ?: "the scenery"}"
                    val mx = (h.left + h.right) / 2; val my = (h.top + h.bottom) / 2
                    if ((painter.targetAt(c, mx, my, 3) ?: front(mx, my)) != t) middles++
                }
            }
        }
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
        assertTrue("$n things", n > 300)
        println("things a tap at their area's middle missed: $middles of $n; tapped where nothing is painted (no pixel of their own): $specks")
    }

    @Test fun `the cart bridge over the stream is tapped on its deck, not on the water under it`() {
        val painter = ScenePainters.create("stream")
        val objects = SceneArt.objects["stream"].orEmpty().toSet()
        for (culture in listOf(Cultures.DEFAULT, "friuli")) {
            val c = PixelCanvas(240, 160)
            val hits = painter.render(c, SceneFrame(time = 2.0, hour = 11f, month = 7, objects = objects, world = SceneWorld(culture = culture)))
            val bridge = SceneTarget.Thing("cartbridge")
            val h = hits.lastOrNull { it.target == bridge } ?: continue
            val p = TapPoints.of(bridge, hits, c.width, c.height) { x, y -> painter.targetAt(c, x, y, 0) }!!
            assertEquals(culture, bridge, painter.targetAt(c, p.first, p.second, 0))
            println("$culture: the cart bridge's area (${h.left}, ${h.top}, ${h.right}, ${h.bottom}), its middle ${painter.targetAt(c, (h.left + h.right) / 2, (h.top + h.bottom) / 2, 3)}, tapped at $p")
            assertNotEquals(null, p)
        }
    }
}
