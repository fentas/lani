package si.lanisce.lani.game.render

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
 * The town's pictures rendered on a thread of their own ([TownPictures]; docs/plans/05-built/zoom-frames.md,
 * home-return.md): nothing drawn on the asking thread, the first picture neither; the closer look a window of its picture
 * covering the screen, the same pixels a render of that window has; never drawn into while the screen may show it; asks
 * merged while it renders; the thread ended when idle and started again; the Home strip's the same; the village's kept for
 * when it comes back ([Retained]) and let go under memory pressure.
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

    @Test fun `nothing is drawn on the asking thread, the first picture neither`() {
        val pics = TownPictures(fit.width, fit.height, VillageRenderer())
        try {
            val home = rig.fitted()
            val asking = Thread.currentThread()
            pics.ask(0L, 1, home, vw, vh, state, frame(0.0), changed = true)
            // ask() returned at once, nothing drawn: the first picture comes from the render thread
            assertNull("no picture drawn while asking", pics.base)
            waitFor("the first picture") { pics.base != null }
            assertNotSame("drawn on the render thread", asking, pics.drewOn)
            val base = pics.base!!
            assertEquals(fit.width, base.w); assertEquals(fit.height, base.h)
            // its pixels are the nominal canvas's
            val c = PixelCanvas(fit.width, fit.height)
            VillageRenderer().render(c, state, frame(0.0))
            assertTrue(c.pixels.contentEquals(pixels(base)))

            // closer up: a window of the picture at detail 2 covering the screen
            val cam = rig.lookAt(fit.width / 2f, fit.height / 2f, rig.minScale * 2f)
            pics.ask(83_000_000L, 2, cam, vw, vh, state, frame(0.083), changed = false)
            waitFor("the closer look") { pics.fine != null }
            assertNotSame(asking, pics.drewOn)
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

    @Test fun `the render thread ends when nothing is asked, and starts again for the next ask`() {
        val pics = TownPictures(fit.width, fit.height, VillageRenderer())
        try {
            pics.ask(0L, 1, rig.fitted(), vw, vh, state, frame(0.0), changed = true)
            waitFor("the first picture") { pics.base != null }
            val first = pics.base
            // a view kept off the screen holds no thread
            val end = System.currentTimeMillis() + RenderThread.IDLE_MS * 3
            while (pics.rendering && System.currentTimeMillis() < end) Thread.sleep(50)
            assertFalse("the thread ended while idle", pics.rendering)
            pics.ask(83_000_000L, 1, rig.fitted(), vw, vh, state, frame(1.0), changed = false)
            waitFor("the next picture") { pics.base !== first }
        } finally {
            pics.close()
        }
    }

    @Test fun `the Home header's village and a scene draw on their own threads too, the first picture too`() {
        val strip = StripPictures(360, 174, VillageRenderer())
        try {
            val asking = Thread.currentThread()
            strip.ask(0L, state, Frame(time = 0.0, hour = 12f, month = 8, compact = true, sea = true, today = "2026-08-20"))
            assertNull("no picture drawn while asking", strip.picture)
            waitFor("the strip's first picture") { strip.picture != null }
            assertNotSame(asking, strip.drewOn)
        } finally {
            strip.close()
        }
        // a scene's (the tent's, at the fitted view): the same
        val art = "tent"
        val fit = SceneFit.of(1080, 2400, compact = false)
        val pics = si.lanisce.lani.ui.scene.ScenePictures(
            fit.width, fit.height, si.lanisce.lani.game.scene.ScenePainters.create(art), si.lanisce.lani.game.scene.ScenePainters.create(art), art,
        )
        try {
            val asking = Thread.currentThread()
            val rig = CameraRig.scene(fit, 1080, 2400)
            pics.ask(0L, 1, rig.fitted(), 1080, 2400, si.lanisce.lani.game.scene.SceneFrame(time = 0.0, hour = 12f, month = 8), changed = true)
            assertNull("no scene picture drawn while asking", pics.base)
            waitFor("the scene's first picture") { pics.base != null }
            assertNotSame(asking, pics.drewOn)
        } finally {
            pics.close()
        }
    }

    @Test fun `the village's pictures are kept for when it comes back, and let go under memory pressure`() {
        Retained.clear()
        try {
            val r = Retained.townRenderer()
            assertSame("the own village's renderer kept", r, Retained.townRenderer())
            val pics = Retained.town(fit.width, fit.height, r)
            Retained.use(pics, true)
            pics.ask(0L, 1, rig.fitted(), vw, vh, state, frame(0.0), changed = true)
            waitFor("the first picture") { pics.base != null }
            Retained.use(pics, false)
            // the village composed again: the same pictures, the last one there at once
            val again = Retained.town(fit.width, fit.height, r)
            assertSame(pics, again)
            assertNotNull(again.base)
            // the Home strip by its size
            val strip = Retained.strip(360, 174)
            assertSame(strip, Retained.strip(360, 174))
            // memory short while the village is on the screen: its pictures stay, its renderer's kept ground goes
            Retained.use(again, true)
            Retained.trim(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
            assertSame(again, Retained.town(fit.width, fit.height, r))
            assertEquals(0, r.groundCells.size)
            // off the screen, in the background: let go
            Retained.use(again, false)
            Retained.trim(android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
            assertEquals(0, Retained.kept)
            assertNotSame(r, Retained.townRenderer())
            // the app merely hidden: kept
            Retained.strip(360, 174)
            Retained.trim(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            assertEquals(1, Retained.kept)
        } finally {
            Retained.clear()
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
