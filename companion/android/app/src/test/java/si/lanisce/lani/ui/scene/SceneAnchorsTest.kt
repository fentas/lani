package si.lanisce.lani.ui.scene

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Camera
import si.lanisce.lani.game.render.CameraRig
import si.lanisce.lani.game.render.SceneFit
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.ui.game.TownCameraState

/** Where a scene's things are on screen, and what a tap lands on, through the scene's camera. */
class SceneAnchorsTest {
    /** Frames come at once, 16 ms apart: a flight runs to its end within the call that starts it. */
    private val frames = object : MonotonicFrameClock {
        var t = 0L
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R { t += 16_000_000L; return onFrame(t) }
    }

    /** The scene on a 1080×2400 phone: 1080×885 px, a 270×222 canvas at 4 × (one pixel row cut at the top). */
    private val rig = CameraRig.scene(SceneFit.of(1080, 885, compact = false), 1080, 885)
    private val cam = TownCameraState(CoroutineScope(Dispatchers.Unconfined + frames), memory = null).also { it.update(rig, rig.fitted()) }
    private val pot = SceneTarget.Thing("lonec")
    private val gran = SceneTarget.Person("babica")
    private val anchors = SceneAnchors(cam).apply {
        hits = listOf(
            SceneHit(pot, 100, 40, 120, 60, 110, 40),
            SceneHit(gran, 200, 100, 230, 180, 215, 104),
        )
    }

    @Test fun `at the whole scene, things are where the fit draws them`() {
        assertEquals(Camera(4f, 0f, -1f), cam.camera)
        assertFalse(cam.moved)
        assertEquals(IntRect(400, 159, 480, 239), anchors.bounds(pot))
        assertEquals(IntOffset(440, 159), anchors.anchor(pot))
        assertEquals(anchors.bounds(pot), anchors.shown(pot))
        assertEquals(pot, anchors.at(401f, 160f))
        assertEquals(gran, anchors.at(801f, 400f))
        assertNull(anchors.at(399f, 160f))
        assertNull(anchors.bounds(SceneTarget.Thing("miza"))) // not drawn
    }

    @Test fun `a pinch keeps the point under the fingers, and bubbles and taps follow`() {
        cam.gesture(Offset(440f, 199f), Offset.Zero, 2f) // on the pot's middle
        assertEquals(Camera(8f, -440f, -201f), cam.camera)
        assertTrue(cam.moved)
        assertEquals(pot, anchors.at(440f, 199f))
        assertEquals(IntRect(360, 119, 520, 279), anchors.bounds(pot))
        assertEquals(IntOffset(440, 119), anchors.anchor(pot))
        // grandma walked out of the picture on the right: still drawn, not shown
        assertTrue(anchors.drawn(gran))
        assertNotNull(anchors.bounds(gran))
        assertNull(anchors.shown(gran))
        assertNull(anchors.at(1070f, 400f)) // under the finger now: canvas (188, 75), nothing there
    }

    @Test fun `a drag pans, and the part of a thing still in view is what a bubble points at`() {
        cam.gesture(Offset(440f, 199f), Offset.Zero, 2f)
        cam.gesture(Offset(440f, 199f), Offset(-400f, 0f), 1f) // the pot slides out to the left
        assertEquals(IntRect(-40, 119, 120, 279), anchors.bounds(pot))
        assertEquals(IntRect(0, 119, 120, 279), anchors.shown(pot))
        cam.gesture(Offset(440f, 199f), Offset(10_000f, 10_000f), 1f) // never past the canvas' edge
        assertEquals(Camera(8f, 0f, 0f), cam.camera)
    }

    @Test fun `a double tap zooms in on the point and settles crisp, another goes back to the whole scene`() {
        cam.doubleTap(Offset(440f, 199f))
        assertEquals(8f, cam.camera.scale)
        assertTrue(cam.camera.crisp)
        assertEquals(pot, anchors.at(440f, 199f))
        cam.doubleTap(Offset(100f, 100f))
        assertEquals(rig.fitted(), cam.camera)
        assertFalse(cam.moved)
    }

    @Test fun `a fling settles on whole pixels, and recenter flies home`() {
        cam.gesture(Offset(300f, 300f), Offset(13.3f, -7.7f), 2.37f)
        assertFalse(cam.camera.crisp)
        cam.release(Velocity.Zero, Offset(300f, 300f))
        assertTrue(cam.camera.crisp)
        assertEquals(8f, cam.camera.scale) // detail 2 there: the nearest crisp zoom is even
        cam.recenter()
        assertEquals(rig.fitted(), cam.camera)
        // TalkBack's zoom actions
        cam.zoomBy(2f)
        assertEquals(8f, cam.camera.scale)
        cam.zoomBy(0.5f)
        assertEquals(rig.fitted(), cam.camera)
    }
}
