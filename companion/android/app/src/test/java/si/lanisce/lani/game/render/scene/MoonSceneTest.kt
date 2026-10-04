package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Env
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.Moon
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

/**
 * The moon the scenes draw follows the real one ([Moon]): its lit part grows from a sliver to full and shrinks again,
 * on the right while it waxes and on the left while it wanes, and it is in the sky only while it is up. A sheet of the
 * campfire's night at the phases for review: build/scene-snapshots/moon/00-moon-phases.png.
 */
class MoonSceneTest {
    private val dir = File("build/scene-snapshots/moon").apply { mkdirs() }
    private val s = Moon.SYNODIC.toFloat()

    /** The shared moon ([ArtPainter.moon]) alone on a night sky, [R] px round in the middle of the canvas. */
    private class MoonAlone : ArtPainter() {
        override val art = "moon-alone"
        override fun backdrop(frame: SceneFrame): Int = SKY
        override fun paint() {
            c.penId = 0
            for (y in 0 until h) for (x in 0 until w) c.set(x, y, SKY)
            moon(w / 2, h / 2, R * detail, SKY)
        }

        companion object {
            const val R = 14
            val SKY = Col.hex(0x0A0F24)
        }
    }

    /** The darkest hour of a January day (long nights) with the moon of [age] up, highest among them; null for none. */
    private fun nightWithMoon(age: Float, month: Int = 1): Float? =
        (0 until 96).map { it / 4f }.filter { Env(it, month, false, 0f, age).let { e -> e.moonShows && e.dark >= 0.9f } }
            .maxByOrNull { Env(it, month, false, 0f, age).moonHeight }

    /** The moon's bright pixels (lit, or its terminator) and the middle of them, across. */
    private fun lit(c: PixelCanvas): Pair<Int, Float> {
        var n = 0; var sx = 0L
        for (y in 0 until c.height) for (x in 0 until c.width) if (Col.r(c.pixels[y * c.width + x]) > 150) { n++; sx += x }
        return n to (if (n == 0) Float.NaN else sx.toFloat() / n)
    }

    @Test fun `the lit part grows toward full and shrinks after, on the right waxing and on the left waning`() {
        val waxing = listOf(3f, 5.5f, s / 4, 11f, Moon.FULL)
        val waning = listOf(Moon.FULL, 18.5f, s * 3 / 4, 25.5f)
        val painter = MoonAlone()
        val counts = HashMap<Float, Int>()
        for (age in (waxing + waning).distinct()) {
            val hour = checkNotNull(nightWithMoon(age)) { "the moon of $age days is up at night" }
            val c = PixelCanvas(60, 60)
            painter.render(c, SceneFrame(hour = hour, month = 1, moon = age))
            val (n, mid) = lit(c)
            counts[age] = n
            val side = if (age < Moon.FULL - 1f) "right" else if (age > Moon.FULL + 1f) "left" else "both"
            println("moon of %.1f days (%s) at %.2f h: %d bright pixels, their middle at x %.1f (the disc's at 30, lit on the %s)".format(age, Moon.phase(age), hour, n, mid, side))
            when (side) {
                "right" -> assertTrue("waxing $age: lit on the right ($mid)", mid > 31f)
                "left" -> assertTrue("waning $age: lit on the left ($mid)", mid < 29f)
                else -> assertEquals("full: round about the middle", 30f, mid, 0.8f)
            }
        }
        for ((a, b) in waxing.zipWithNext()) assertTrue("more lit at $b days than at $a: ${counts[b]} vs ${counts[a]}", counts.getValue(b) > counts.getValue(a))
        for ((a, b) in waning.zipWithNext()) assertTrue("less lit at $b days than at $a: ${counts[b]} vs ${counts[a]}", counts.getValue(b) < counts.getValue(a))
        // the quarters: about half of full
        for (q in listOf(s / 4, s * 3 / 4)) {
            val f = counts.getValue(q).toFloat() / counts.getValue(Moon.FULL)
            assertTrue("a quarter is half lit: $f", f in 0.4f..0.62f)
        }
    }

    @Test fun `every phase draws a different moon`() {
        val painter = MoonAlone()
        val pictures = listOf(3f, s / 4, 11f, Moon.FULL, 18.5f, s * 3 / 4, 25.5f).map { age ->
            val c = PixelCanvas(60, 60)
            painter.render(c, SceneFrame(hour = nightWithMoon(age)!!, month = 1, moon = age))
            c.pixels.toList()
        }
        assertEquals(pictures.size, pictures.toSet().size)
    }

    private fun campfire(hour: Float, age: Float, month: Int = 9, k: Int = 1, bw: Int = 270, bh: Int = 297): Pair<PixelCanvas, List<SceneHit>> {
        val c = PixelCanvas(bw * k, bh * k)
        val hits = ScenePainters.create("campfire").render(c, SceneFrame(time = 2.0, hour = hour, month = month, objects = SceneArt.objects.getValue("campfire").toSet(), moon = age), Lens(k, 0, 0, bw, bh))
        return c to hits
    }

    private fun moonHit(hits: List<SceneHit>): SceneHit? = hits.firstOrNull { it.target == SceneTarget.Thing("moon") }

    @Test fun `the campfire's moon is in the sky only while it is up, and moves along its way`() {
        val fq = s / 4; val lq = s * 3 / 4
        // full: up all night, not by day
        assertNotNull(moonHit(campfire(23f, Moon.FULL).second))
        assertNull(moonHit(campfire(13f, Moon.FULL).second))
        // the first quarter: in the evening, not before dawn; the last quarter: before dawn, not in the evening
        assertNotNull(moonHit(campfire(21f, fq).second))
        assertNull(moonHit(campfire(5f, fq).second))
        assertNotNull(moonHit(campfire(5f, lq).second))
        assertNull(moonHit(campfire(21f, lq).second))
        // new: never, even by day while it is up with the sun
        for (h in listOf(0f, 6f, 12f, 13f, 18f, 23f)) assertNull("new moon at $h h", moonHit(campfire(h, 0.3f).second))
        // a crescent in the afternoon: a pale ghost by day
        assertNotNull(moonHit(campfire(15f, 3.5f).second))
        // it rises on the left and sets on the right, high in between
        val rising = moonHit(campfire(20f, Moon.FULL).second)!!
        val high = moonHit(campfire(1.2f, Moon.FULL).second)!!
        val setting = moonHit(campfire(6f, Moon.FULL).second)!!
        assertTrue("rising on the left: $rising, $high, $setting", rising.left < high.left && high.left < setting.left)
        assertTrue("higher at its highest: $rising, $high, $setting", high.top < rising.top && high.top < setting.top)
    }

    @Test fun `the village draws the moon only while it is up`() {
        fun sky(age: Float, hour: Float): IntArray {
            val c = PixelCanvas(240, 160)
            si.lanisce.lani.game.render.VillageRenderer().render(c, si.lanisce.lani.game.GameState(seed = 7), si.lanisce.lani.game.render.Frame(time = 1.0, hour = hour, month = 9, moon = age))
            return c.pixels
        }
        // the full moon up at midnight, the new moon not: the same sky but for the moon
        assertNotEquals(sky(Moon.FULL, 0.5f).toList(), sky(0.2f, 0.5f).toList())
        // the first quarter set before dawn: the same sky as with no moon at all
        assertEquals(sky(s / 4, 4f).toList(), sky(0.2f, 4f).toList())
    }

    @Test fun `render every art's sky at the phases`() {
        // a column per moment: a crescent's ghost in the afternoon, the first quarter in the evening, full at midnight, the
        // last quarter before dawn, the new moon at night; a row per art, the village's sky last
        val moments = listOf(3.5f to 15f, s / 4 to 21.5f, Moon.FULL to 0.5f, s * 3 / 4 to 4.5f, 0.2f to 22f)
        val arts = SceneArt.arts.toList()
        val sc = 2; val bw = 240; val bh = 160
        val img = BufferedImage((bw * sc + 4) * moments.size, (bh * sc + 4) * (arts.size + 1), BufferedImage.TYPE_INT_RGB)
        fun put(c: PixelCanvas, col: Int, row: Int) {
            for (y in 0 until bh * sc) for (x in 0 until bw * sc) img.setRGB(col * (bw * sc + 4) + x, row * (bh * sc + 4) + y, c.pixels[(y / sc) * c.width + x / sc])
        }
        for ((j, m) in moments.withIndex()) {
            val (age, hour) = m
            for ((i, art) in arts.withIndex()) {
                val c = PixelCanvas(bw, bh)
                ScenePainters.create(art).render(c, SceneFrame(time = 2.0, hour = hour, month = 9, moon = age))
                put(c, j, i)
            }
            val c = PixelCanvas(bw, bh)
            si.lanisce.lani.game.render.VillageRenderer().render(c, si.lanisce.lani.game.GameState(seed = 7), si.lanisce.lani.game.render.Frame(time = 1.0, hour = hour, month = 9, moon = age))
            put(c, j, arts.size)
        }
        ImageIO.write(img, "png", File(dir, "01-skies.png"))
    }

    @Test fun `render the campfire's night at the phases`() {
        val phases = listOf(
            "new" to (0.2f to 22f), "waxing crescent" to (3.5f to 20.5f), "first quarter" to (s / 4 to 21.5f),
            "full" to (Moon.FULL to 0.5f), "waning gibbous" to (19f to 3f), "last quarter" to (s * 3 / 4 to 4.5f),
        )
        val bw = 270; val bh = 297
        val sc = 2
        // under each, the moon closer up: crop × crop scene px of the picture at detail 3, each of its pixels cs × cs
        val crop = 44; val cs = 4
        val panelW = max(bw * sc, crop * 3 * cs)
        val img = BufferedImage(panelW * phases.size + 8 * (phases.size - 1), bh * sc + 8 + crop * 3 * cs, BufferedImage.TYPE_INT_RGB)
        for ((i, p) in phases.withIndex()) {
            val (age, hour) = p.second
            val (c1, hits) = campfire(hour, age, bw = bw, bh = bh)
            val x0 = i * (panelW + 8)
            for (y in 0 until bh * sc) for (x in 0 until bw * sc) img.setRGB(x0 + x, y, c1.pixels[(y / sc) * bw + x / sc])
            // where the moon is (or the sky's middle when it isn't there)
            val m = moonHit(hits)
            val mx = m?.let { (it.left + it.right) / 2 } ?: (bw / 2); val my = m?.let { (it.top + it.bottom) / 2 } ?: 40
            val (c3, _) = campfire(hour, age, k = 3, bw = bw, bh = bh)
            val cx0 = (mx - crop / 2).coerceIn(0, bw - crop) * 3; val cy0 = (my - crop / 2).coerceIn(0, bh - crop) * 3
            for (y in 0 until crop * 3 * cs) for (x in 0 until crop * 3 * cs) {
                img.setRGB(x0 + x, bh * sc + 8 + y, c3.pixels[min(c3.height - 1, cy0 + y / cs) * c3.width + min(c3.width - 1, cx0 + x / cs)])
            }
            println("campfire, ${p.first} (%.1f days, %s, lit %.2f) at %.1f h: moon ${m ?: "not up"}".format(age, Moon.phase(age), Moon.lit(age), hour))
        }
        ImageIO.write(img, "png", File(dir, "00-moon-phases.png"))
    }
}
