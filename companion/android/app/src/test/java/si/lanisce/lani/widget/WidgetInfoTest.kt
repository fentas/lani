package si.lanisce.lani.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.render.Frame
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.render.VillageRenderer
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class WidgetInfoTest {
    private val wolves = GameState(event = GameEvent("e", EventKind.WOLVES, 2, startedAt = 0, deadline = 1_000))

    @Test fun `texts show streak, due cards and a running event`() {
        val i = WidgetInfo.of(wolves, streak = 12, due = 8, now = 500)
        assertEquals("🔥 12", i.streak)
        assertEquals("📚 8 za danes · due", i.due)
        assertEquals("🐺 Volkovi!", i.event)
        assertNull(WidgetInfo.of(wolves, 12, 8, now = 1_000).event) // past the deadline
        assertEquals("✅ Vse ponovljeno · All done", WidgetInfo.of(null, 0, 0, 0).due)
        assertEquals("📚 20+ za danes · due", WidgetInfo.of(null, 0, 20, 0).due) // the dashboard lists at most 20
        assertEquals("Odpri Lani · Open Lani", WidgetInfo.of(null, 0, -1, 0).due)
    }

    @Test fun `internal size follows the widget's aspect at about 240 px wide`() {
        assertEquals(241 to 106, WidgetInfo.internalSize(750, 330)) // 4×2 at 3× density
        val (w, h) = WidgetInfo.internalSize(750, 750) // square: taller, narrower
        assertEquals(200, h); assertEquals(200, w)
        val (w2, h2) = WidgetInfo.internalSize(2000, 200) // very wide: capped
        assertTrue(w2 <= 360 && h2 >= 80)
    }

    @Test fun `corners are cut, the rest is kept`() {
        val cut = HashSet<Pair<Int, Int>>()
        WidgetInfo.roundCorners(100, 50, 10f) { x, y -> cut += x to y }
        assertTrue((0 to 0) in cut && (99 to 0) in cut && (0 to 49) in cut && (99 to 49) in cut)
        assertTrue((10 to 10) !in cut && (50 to 0) !in cut && (0 to 25) !in cut)
    }

    /** Writes the widget picker preview (copied to res/drawable-nodpi/widget_preview.png). */
    @Test fun `render widget preview`() {
        val types = listOf(BuildingType.FIELD, BuildingType.HUT, BuildingType.WELL, BuildingType.KOZOLEC, BuildingType.TENT, BuildingType.PALISADE, BuildingType.HUT)
        val state = GameState(
            seed = 42, age = Age.ZASELEK, villagers = 6, morale = 70, fire = 70,
            buildings = types.mapIndexed { i, t -> Building("b$i", t, plot = i) },
            event = GameEvent("e", EventKind.WOLVES, 2, 0, 0),
        )
        val (iw, ih) = WidgetInfo.internalSize(750, 330)
        val c = PixelCanvas(iw, ih)
        VillageRenderer().render(c, state, Frame(time = 2.0, hour = 17.5f, month = 10, compact = true))
        val k = 3
        val img = BufferedImage(iw * k, ih * k, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until ih * k) for (x in 0 until iw * k) img.setRGB(x, y, c.pixels[(y / k) * iw + x / k])
        WidgetInfo.roundCorners(img.width, img.height, 46f) { x, y -> img.setRGB(x, y, 0) }
        val dir = File("build/village-snapshots").apply { mkdirs() }
        ImageIO.write(img, "png", File(dir, "widget-preview.png"))
    }
}
