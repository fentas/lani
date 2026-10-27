package si.lanisce.lani.game.render

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Founding
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.PLOTS_PER_AGE

/**
 * The town's pictures rendered on a thread of their own ([TownPictures]; docs/plans/05-built/zoom-frames.md): the first
 * at once, so the village never opens empty; the closer look a window of its picture covering the screen, the same
 * pixels a render of that window has; never drawn into while the screen may show it; asks merged while it renders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TownPicturesTest {
    private val vw = 2560
    private val vh = 1600
    private val fit = SceneFit.town(vw, vh)
    private val rig = CameraRig.town(fit, vw, vh)
    private val state: GameState = Founding("friuli", Landscape.COAST, 551323397L).village(1_000L).let { s ->
        val n = PLOTS_PER_AGE[Age.VAS.ordinal]
        s.copy(age = Age.VAS, villagers = 4 + n, buildings = List(n) { i -> Building("b$i", listOf(BuildingType.HUT, BuildingType.HOUSE, BuildingType.WELL)[i % 3], i) })
    }

    private fun frame(t: Double) = Frame(time = t, hour = 12f, month = 8, sea = true, today = "2026-08-20")

    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "timed out: $what" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun pixels(p: Picture): IntArray = IntArray(p.w * p.h).also { p.bitmap.getPixels(it, 0, p.w, 0, 0, p.w, p.h) }

    @Test fun `the first picture is there at once, the closer look comes from the render thread`() {
        val pics = TownPictures(fit.width, fit.height, VillageRenderer())
        try {
            val home = rig.fitted()
            pics.ask(0L, 1, home, vw, vh, state, frame(0.0), changed = true)
            val base = pics.base
            assertNotNull("the first picture at once", base)
            base!!
            assertEquals(fit.width, base.w); assertEquals(fit.height, base.h)
            // its pixels are the nominal canvas's
            val c = PixelCanvas(fit.width, fit.height)
            VillageRenderer().render(c, state, frame(0.0))
            assertTrue(c.pixels.contentEquals(pixels(base)))

            // closer up: a window of the picture at detail 2 covering the screen
            val cam = rig.lookAt(fit.width / 2f, fit.height / 2f, rig.minScale * 2f)
            pics.ask(83_000_000L, 2, cam, vw, vh, state, frame(0.083), changed = false)
            waitFor("the closer look") { pics.fine != null }
            val fine = pics.fine!!
            assertEquals(2, fine.lens.k)
            val screen = TownFrames.screen(2, cam, vw, vh, fit.width, fit.height)
            assertTrue(PixelRect(fine.ox, fine.oy, fine.w, fine.h).covers(TownFrames.needed(2, screen, fit.width, fit.height)))
            val w = PixelCanvas(fine.w, fine.h).apply { originX = fine.ox; originY = fine.oy }
            VillageRenderer().render(w, state, frame(0.083).copy(lens = TownFrames.lens(2, fit.width, fit.height), screen = screen))
            assertTrue("the closer look's pixels", w.pixels.contentEquals(pixels(fine)))
        } finally {
            pics.close()
        }
    }

    @Test fun `what the screen drew is never drawn into, and asks while it renders wait as one`() {
        val pics = TownPictures(fit.width, fit.height, VillageRenderer())
        try {
            pics.ask(0L, 1, rig.fitted(), vw, vh, state, frame(0.0), changed = true)
            val cam = rig.lookAt(fit.width / 2f, fit.height / 2f, rig.minScale * 3f)
            pics.ask(1L, 3, cam, vw, vh, state, frame(0.1), changed = false)
            waitFor("the closer look") { pics.fine != null }
            val shown = pics.fine!!
            val before = pixels(shown)
            pics.drew(pics.base, shown)
            // the clock goes on, the camera pans: new pictures, never into the one on the screen
            var last = shown
            var handed = 0
            for (i in 2 until 40) {
                pics.ask(i * 83_000_000L, 3, rig.pan(cam, -i * 3f, 0f), vw, vh, state, frame(i * 0.083), changed = false)
                shadowOf(Looper.getMainLooper()).idle()
                pics.fine?.let { if (it !== last) { handed++; last = it } }
            }
            waitFor("the last ask drawn") { pics.fine !== shown }
            if (pics.fine !== last) handed++
            assertNotSame(shown, pics.fine)
            assertTrue("the picture on the screen kept its pixels", before.contentEquals(pixels(shown)))
            // 38 asks in a row, each a new tick: merged while it rendered, never more pictures than asks
            assertTrue("$handed pictures handed over", handed in 1..38)
        } finally {
            pics.close()
        }
    }
}
