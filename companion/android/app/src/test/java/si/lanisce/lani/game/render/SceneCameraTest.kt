package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A close-up scene under the village's camera: its fitted canvas, closer up drawn finer at the village's detail levels. */
class SceneCameraTest {
    /** A 1080×2400 phone: the scene takes the top 885 px, a 270×222 canvas at 4 ×, zooming to 16 ×. */
    private val fit = SceneFit.of(1080, 885, compact = false)
    private val rig = CameraRig.scene(fit, 1080, 885)

    private fun covers(c: Camera, r: CameraRig = rig) {
        assertTrue("left edge $c", c.tx <= 0.001f)
        assertTrue("top edge $c", c.ty <= 0.001f)
        assertTrue("right edge $c", c.tx + r.canvasW * c.scale >= r.viewW - 0.01f)
        assertTrue("bottom edge $c", c.ty + r.canvasH * c.scale >= r.viewH - 0.01f)
    }

    @Test fun `the fitted view is the whole scene as it was drawn before`() {
        assertEquals(SceneFit(270, 222, 4, 0, -1), fit)
        assertEquals(CameraRig(1080, 885, 270, 222, 4, 16), rig)
        for ((w, h) in listOf(1080 to 885, 720 to 590, 1440 to 1180, 2400 to 561, 1600 to 1312, 1 to 1)) {
            val f = SceneFit.of(w, h, compact = false)
            val home = CameraRig.scene(f, w, h).fitted()
            assertEquals("$w×$h", Camera(f.scale.toFloat(), f.offX.toFloat(), f.offY.toFloat()), home)
            assertTrue(home.crisp)
            covers(home, CameraRig.scene(f, w, h))
        }
    }

    @Test fun `from the whole scene up to four times closer`() {
        val f = rig.fitted()
        assertEquals(f, rig.zoomAt(f, 500f, 400f, 0.5f)) // can't zoom out past it
        assertEquals(16f, rig.zoomAt(f, 500f, 400f, 100f).scale) // nor in past 4 ×
        assertEquals(15f, rig.settle(rig.zoomAt(f, 500f, 400f, 100f)).scale) // the closest crisp zoom at detail 3
    }

    @Test fun `closer up the scene gets finer, at the village's detail levels`() {
        assertTrue(rig.detailed)
        // 1 up to 1.5 × the fitted 4, 2 from 6, 3 from 10
        assertEquals(listOf(1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3), (4..16).map { rig.detail(it.toFloat()) })
        // at rest, a whole number of screen pixels per pixel of the picture at that detail
        assertEquals(listOf(4f, 5f, 6f, 8f, 12f, 15f), listOf(4f, 5f, 6f, 8f, 12f, 15f).map { rig.crisp(it) })
        assertEquals(8f, rig.crisp(7f)); assertEquals(12f, rig.crisp(11f))
        val mid = rig.zoomAt(rig.fitted(), 300f, 400f, 1.65f) // 6.6 ×
        assertFalse(mid.crisp)
        val s = rig.settle(mid, 300f, 400f)
        assertTrue(s.crisp)
        assertEquals(8f, s.scale) // detail 2: even scales only, like the village
        assertEquals(mid.toCanvasX(300f), s.toCanvasX(300f), 1f) // the fingers' point stays within a canvas pixel
        assertEquals(15f, rig.settle(Camera(15.7f, -99_999f, 3f)).scale)
        // the town, fitted further out (3 ×), steps up at the same shares of its fitted zoom: 2 from 4.5, 3 from 7.5
        val town = CameraRig.town(SceneFit.town(1080, 2400), 1080, 2400)
        assertTrue(town.detailed)
        assertEquals(listOf(1, 1, 2, 2, 2, 3, 3), (3..9).map { town.detail(it.toFloat()) })
        assertEquals(6f, town.settle(Camera(5.4f, 0f, 0f)).scale)
        assertEquals(9f, town.settle(Camera(8.4f, 0f, 0f)).scale)
    }

    @Test fun `the finer picture covers the view from the zoom its detail starts at`() {
        // the scene view's closer look: a window a third bigger than the scene canvas (see SceneView's Pictures)
        val fw = rig.canvasW * 4 / 3 + 4; val fh = rig.canvasH * 4 / 3 + 4
        for (scale in listOf(6f, 7f, 8f, 10f, 12f, 15f, 16f)) {
            val k = rig.detail(scale)
            val c = rig.zoomAt(rig.fitted(), 540f, 440f, scale / rig.minScale)
            val lens = rig.lens(c, k, fw, fh)
            // the view's corners, through the camera and the lens, land inside the window
            for ((sx, sy) in listOf(0f to 0f, 1079f to 884f)) {
                val x = lens.cx(c.toCanvasX(sx)); val y = lens.cy(c.toCanvasY(sy))
                assertTrue("at $scale × (detail $k): ($sx, $sy) → ($x, $y) outside $fw × $fh", x >= -0.5f && y >= -0.5f && x <= fw + 0.5f && y <= fh + 0.5f)
            }
        }
    }

    @Test fun `the scene can't be dragged away`() {
        val z = rig.zoomAt(rig.fitted(), 540f, 440f, 2.5f)
        covers(rig.pan(z, 10_000f, 10_000f))
        covers(rig.pan(z, -10_000f, -10_000f))
        assertEquals(0f, rig.pan(z, 10_000f, 0f).tx)
        assertEquals(1080f - 270 * 10f, rig.pan(z, -10_000f, 0f).tx)
        // at the fitted zoom only the 3 px the canvas is taller than the view
        val f = rig.fitted()
        assertEquals(f.tx, rig.pan(f, 300f, 0f).tx)
        assertEquals(-3f, rig.pan(f, 0f, -200f).ty)
        assertEquals(0f, rig.pan(f, 0f, 200f).ty)
    }

    @Test fun `a screen point maps to the canvas pixel drawn there`() {
        val c = rig.lookAt(100f, 50f, 12f)
        assertTrue(c.crisp)
        // canvas pixel (100, 50) covers screen [x, x + 12) × [y, y + 12)
        val x = c.screenX(100); val y = c.screenY(50)
        for ((sx, sy) in listOf(x to y, x + 11 to y + 11, x + 6 to y + 3)) {
            assertEquals(100, c.pixelX(sx + 0.5f)); assertEquals(50, c.pixelY(sy + 0.5f))
        }
        assertEquals(99, c.pixelX(x - 0.5f)); assertEquals(101, c.pixelX(x + 12.5f))
        assertEquals(49, c.pixelY(y - 0.5f)); assertEquals(51, c.pixelY(y + 12.5f))
        // left of the canvas (never at rest, but mid-flight) is a negative pixel, not pixel 0
        assertEquals(-1, Camera(4f, 2f, 0f).pixelX(1f))
    }

    @Test fun `a tap's slop is the same on screen at any zoom`() {
        assertEquals(3, rig.slop(rig.fitted(), 3))
        assertEquals(2, rig.slop(Camera(8f, 0f, 0f), 3))
        assertEquals(1, rig.slop(Camera(16f, 0f, 0f), 3))
        assertEquals(3, rig.slop(Camera(2f, 0f, 0f), 3)) // never more than at the fitted zoom
        assertEquals(3, rig.slop(Camera(Float.NaN, 0f, 0f), 3))
        assertEquals(1, rig.slop(Camera(4f, 0f, 0f), 0))
    }
}
