package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TownCameraTest {
    /** A 1080×2400 phone: the town canvas is 360×800 at 3×, zooming to 15×. */
    private val fit = SceneFit.town(1080, 2400)
    private val rig = CameraRig.town(fit, 1080, 2400)

    private fun covers(c: Camera) {
        assertTrue("left edge $c", c.tx <= 0.001f)
        assertTrue("top edge $c", c.ty <= 0.001f)
        assertTrue("right edge $c", c.tx + rig.canvasW * c.scale >= rig.viewW - 0.01f)
        assertTrue("bottom edge $c", c.ty + rig.canvasH * c.scale >= rig.viewH - 0.01f)
    }

    @Test fun `the town canvas covers a portrait phone at a whole scale`() {
        assertEquals(SceneFit(360, 800, 3, 0, 0), fit)
        assertEquals(SceneFit(360, 780, 4, 0, 0), SceneFit.town(1440, 3120))
        for ((w, h) in listOf(1080 to 2400, 720 to 1600, 1440 to 3120, 2400 to 1080, 1600 to 2560, 1080 to 2640, 1 to 1)) {
            val f = SceneFit.town(w, h)
            assertTrue("covers $w×$h: $f", f.width * f.scale >= w && f.height * f.scale >= h)
            assertTrue("tall at most ${SceneFit.TOWN_TALL} nominal heights: $f", f.height <= SceneFit.BASE_H * SceneFit.TOWN_TALL || f.height == 16)
            // wide enough for the whole village of every age
            assertTrue("the whole village across: $f", f.width >= SceneFit.TOWN_W || f.width == 16)
        }
        // a scene's fit is untouched
        assertEquals(SceneFit(270, 297, 4, 0, 0), SceneFit.of(1080, 1188, false))
    }

    @Test fun `the fitted view shows the whole canvas and is the widest zoom`() {
        val f = rig.fitted()
        assertEquals(Camera(3f, 0f, 0f), f)
        assertTrue(f.crisp)
        assertEquals(f, rig.zoomAt(f, 500f, 500f, 0.5f)) // can't zoom out past it
        assertEquals(15f, rig.zoomAt(f, 500f, 500f, 100f).scale) // nor in past 5×: as close as the town ever came (15× at detail 3)
    }

    @Test fun `panning is bounded so the village can't get lost`() {
        val z = rig.zoomAt(rig.fitted(), 540f, 1200f, 2f)
        covers(rig.pan(z, 10_000f, 10_000f))
        covers(rig.pan(z, -10_000f, -10_000f))
        assertEquals(0f, rig.pan(z, 10_000f, 0f).tx)
        assertEquals(1080f - 360 * 6f, rig.pan(z, -10_000f, 0f).tx)
        // at the fitted zoom there is nowhere to pan
        assertEquals(rig.fitted(), rig.pan(rig.fitted(), 300f, -200f))
    }

    @Test fun `zooming keeps the point under the fingers`() {
        val c = rig.lookAt(180f, 200f, 8f)
        val z = rig.zoomAt(c, 300f, 900f, 1.37f)
        assertEquals(c.toCanvasX(300f), z.toCanvasX(300f), 0.001f)
        assertEquals(c.toCanvasY(900f), z.toCanvasY(900f), 0.001f)
    }

    @Test fun `taps map back through the inverse transform`() {
        val c = Camera(6.5f, -123.25f, -777f)
        for ((x, y) in listOf(0f to 0f, 135.5f to 300.25f, 359f to 799f)) {
            assertEquals(x, c.toCanvasX(c.toScreenX(x)), 0.001f)
            assertEquals(y, c.toCanvasY(c.toScreenY(y)), 0.001f)
        }
        // a tap on screen pixel (540, 1200) with the fire drawn there lands on the fire's canvas pixel
        val fire = VillageLayout.composition(fit.width, fit.height, false)
        val at = rig.lookAt(fire.fireX.toFloat(), fire.fireY.toFloat(), 8f)
        assertEquals(fire.fireX.toFloat(), at.toCanvasX(540f), 0.01f)
        assertEquals(fire.fireY.toFloat(), at.toCanvasY(1200f), 0.01f)
    }

    @Test fun `a gesture settles on whole screen pixels per canvas pixel`() {
        val mid = rig.zoomAt(rig.fitted(), 400f, 1000f, 1.61f) // 4.83×
        assertFalse(mid.crisp)
        val s = rig.settle(mid, 400f, 1000f)
        assertTrue(s.crisp)
        assertEquals(6f, s.scale) // level 2 from 1.5 × the fitted 3: even scales only, so not 5
        // the focus point stays within a canvas pixel
        assertEquals(mid.toCanvasX(400f), s.toCanvasX(400f), 1f)
        assertEquals(6f, rig.settle(mid.copy(scale = 5.4f)).scale)
        assertEquals(9f, rig.settle(mid.copy(scale = 8.4f)).scale) // level 3 from 2.5 ×: multiples of 3
        assertEquals(3f, rig.settle(mid.copy(scale = 3.4f)).scale)
        covers(rig.settle(Camera(15.7f, -99_999f, 3f)))
        assertEquals(15f, rig.settle(Camera(15.7f, -99_999f, 3f)).scale) // the closest look (k = 3)
    }

    @Test fun `framing a rectangle picks the largest whole zoom that fits`() {
        val area = floatArrayOf(0f, 300f, 1080f, 2150f)
        val small = CanvasRect(80f, 320f, 190f, 430f) // 110 × 110
        val c = rig.frame(small, area, maxFrame = 6)
        assertEquals(6f, c.scale)
        assertTrue(c.crisp)
        assertEquals((area[1] + area[3]) / 2f, c.toScreenY(small.centerY), 1f)
        assertEquals(9f, rig.frame(small, area).scale) // 1080 / 110 = 9, a multiple of 3 as k = 3 wants
        assertEquals(4f, rig.frame(CanvasRect(0f, 0f, 250f, 300f), area).scale) // 1080 / 250 = 4.3: 4 (k = 1)
        assertEquals(3f, rig.frame(CanvasRect(0f, 0f, 360f, 300f), area).scale) // a whole town: the fitted zoom
    }

    @Test fun `a fly-to starts and ends exactly and zooms evenly`() {
        val a = rig.fitted(); val b = rig.lookAt(60f, 400f, 16f)
        assertEquals(15f, b.scale) // the nearest crisp scale at k = 3, within the limit
        assertEquals(a, rig.between(a, b, 0f))
        assertEquals(b, rig.between(a, b, 1f))
        assertEquals(kotlin.math.sqrt(a.scale * b.scale), rig.between(a, b, 0.5f).scale, 0.01f)
        for (k in 0..10) covers(rig.between(a, b, k / 10f))
    }

    @Test fun `the visible part of the canvas`() {
        assertEquals(listOf(0, 0, 360, 800), rig.visible(rig.fitted()).toList())
        val c = rig.lookAt(180f, 400f, 6f)
        val v = rig.visible(c)
        assertEquals(180 - 90, v[0]); assertEquals(180 + 90, v[2])
        assertEquals(400 - 200, v[1]); assertEquals(400 + 200, v[3])
    }

    @Test fun `moved or home`() {
        val home = rig.lookAt(180f, 350f, 6f)
        assertEquals(0f, rig.distance(home, home), 0.001f)
        assertTrue(rig.distance(rig.pan(home, 200f, 0f), home) > 100f)
        assertTrue(rig.distance(rig.zoomAt(home, 540f, 1200f, 1.5f), home) > 100f)
    }

    @Test fun `broken input never breaks the camera`() {
        val c = rig.lookAt(180f, 400f, 6f)
        for (bad in listOf(rig.pan(c, Float.NaN, Float.POSITIVE_INFINITY), rig.zoomAt(c, Float.NaN, 3f, 2f), rig.zoomAt(c, 3f, 3f, Float.NaN),
            rig.settle(Camera(Float.NaN, Float.NaN, 1f)), rig.clamp(Camera(Float.POSITIVE_INFINITY, 0f, Float.NEGATIVE_INFINITY)))) {
            assertTrue("finite: $bad", bad.scale.isFinite() && bad.tx.isFinite() && bad.ty.isFinite())
            covers(bad)
        }
    }
}
