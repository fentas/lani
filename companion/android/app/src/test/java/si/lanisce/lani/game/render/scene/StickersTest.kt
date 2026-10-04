package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.CuratedContent
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.StickerSpot
import si.lanisce.lani.game.scene.WordStickers
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * The stickers (a scene's thing cut out of it, for a word's card): each word the cards show one for cuts to a thing of
 * its own, cut out of the backdrop, the same every time and quickly; and a contact sheet of every word the scenes draw,
 * as the card shows it, for review (build/scene-snapshots/stickers/00-stickers.png: the cards' own framed in gold).
 */
class StickersTest {
    private val index = WordStickers.of(CuratedContent.scenes)

    /** "pack/word" → its sticker's spot, for every word the scenes draw (the first scene of its own pack's). */
    private val words: Map<String, StickerSpot> by lazy {
        CuratedContent.scenes.flatMap { s -> s.objects.map { o -> WordStickers.key(o.pack ?: s.pack!!, o.word) } }.distinct()
            .associateWith { k -> index.spot(k.substringBefore('/'), k.substringAfter('/'))!! }
    }

    /** The words whose card shows the sticker. */
    private val shown: Map<String, StickerSpot> by lazy {
        words.filterKeys { k -> index.picture(k.substringBefore('/'), k.substringAfter('/'), CuratedContent.emojis[k]) != null }
    }

    @Test fun `every sticker a card shows is a thing cut out of its scene`() {
        assertTrue("${shown.size} cards", shown.size >= 19)
        val fails = ArrayList<String>()
        for ((k, spot) in shown) {
            val s = Stickers.cut(spot.art, spot.slot)
            if (s == null) { fails += "$k: nothing"; continue }
            val clear = s.argb.count { (it ushr 24) == 0 }
            val colours = s.argb.filter { (it ushr 24) == 0xFF }.toSet().size
            val backdrop = ScenePainters.of(spot.art).backdrop(SceneFrame(hour = 12f, month = 7)) and 0xFFFFFF
            val onBackdrop = s.argb.count { (it ushr 24) != 0 && (it and 0xFFFFFF) == backdrop }
            when {
                s.solid < 60 -> fails += "$k: ${s.solid} pixels"
                s.width > 480 || s.height > 480 -> fails += "$k: ${s.width}×${s.height}, bigger than a card needs"
                // cut out: the thing and its outline, not a rectangle of the scene
                clear < s.argb.size / 20 -> fails += "$k: $clear clear pixels of ${s.argb.size}"
                colours < 3 -> fails += "$k: $colours colours"
                onBackdrop > s.solid / 10 -> fails += "$k: $onBackdrop pixels of the backdrop"
                // trimmed to the thing: something in every edge row and column
                (0 until s.width).none { (s.argb[it] ushr 24) != 0 } || (0 until s.height).none { (s.argb[it * s.width] ushr 24) != 0 } ->
                    fails += "$k: not trimmed"
            }
        }
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }

    @Test fun `a sticker is the same every time, and quick`() {
        for (spot in shown.values.toSet()) {
            val a = Stickers.cut(spot.art, spot.slot)!!
            val b = Stickers.cut(spot.art, spot.slot)!!
            assertEquals(spot.toString(), a.width to a.height, b.width to b.height)
            assertArrayEquals(spot.toString(), a.argb, b.argb)
        }
        val t0 = System.nanoTime()
        val n = 3
        repeat(n) { for (spot in shown.values.toSet()) Stickers.cut(spot.art, spot.slot) }
        val ms = (System.nanoTime() - t0) / 1e6 / (n * shown.values.toSet().size)
        assertTrue("a sticker takes $ms ms", ms < 120.0)
    }

    @Test fun `a slot the art hasn't got, or one it draws nothing of, has no sticker`() {
        assertNull(Stickers.cut("field", "anvil"))
        assertNull(Stickers.cut("nowhere", "anvil"))
        // the stars by day
        assertNull(Stickers.cut("campfire", "stars"))
    }

    @Test fun `drawing one thing alone leaves the painter as it was`() {
        val f = SceneFrame(time = 2.0, hour = 12f, month = 7, objects = SceneArt.objects.getValue("smithy").toSet())
        val painter = ScenePainters.create("smithy") as ArtPainter
        val canvas = PixelCanvas(240, 160)
        painter.solo = "anvil"
        val alone = painter.render(canvas, f)
        assertEquals("only the anvil is there", listOf(SceneTarget.Thing("anvil")), alone.map { it.target })
        painter.solo = null
        val hits = painter.render(canvas, f)
        val fresh = PixelCanvas(240, 160)
        assertEquals(ScenePainters.create("smithy").render(fresh, f), hits)
        assertArrayEquals(fresh.pixels, canvas.pixels)
    }

    @Test fun `render the contact sheet`() {
        val dir = File("build/scene-snapshots/stickers").apply { mkdirs() }
        // the cards' own first, then every other word the scenes draw
        val all = shown.entries.sortedBy { it.key } + words.entries.filter { it.key !in shown }.sortedBy { it.key }
        sheet(all.map { it.toPair() }, cols = 8, cellW = 176, cellH = 196, box = 140, File(dir, "00-stickers.png"))
        // the cards' own as big as on a phone's card (124 dp at 2.75 px a dp)
        sheet(shown.entries.sortedBy { it.key }.map { it.toPair() }, cols = 5, cellW = 380, cellH = 400, box = 340, File(dir, "01-cards.png"))
    }

    /** [all] stickers on the card's blue, each scaled as the card scales it into a [box] px square; the cards' own framed in gold. */
    private fun sheet(all: List<Pair<String, StickerSpot>>, cols: Int, cellW: Int, cellH: Int, box: Int, file: File) {
        val rows = (all.size + cols - 1) / cols
        val img = BufferedImage(cols * cellW, rows * cellH, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(0x1B1F2A); g.fillRect(0, 0, img.width, img.height)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        val top = Color(0x1F5FAF); val bottom = Color(0x123C74) // the card's blue (SloBlue → SloBlueDeep)
        for ((i, e) in all.withIndex()) {
            val (k, spot) = e
            val x0 = (i % cols) * cellW; val y0 = (i / cols) * cellH
            for (y in 0 until cellH - 26) {
                val f = y / (cellH - 26f)
                g.color = Color((top.red + (bottom.red - top.red) * f).toInt(), (top.green + (bottom.green - top.green) * f).toInt(), (top.blue + (bottom.blue - top.blue) * f).toInt())
                g.drawLine(x0 + 2, y0 + 24 + y, x0 + cellW - 3, y0 + 24 + y)
            }
            if (k in shown) { g.color = Color(0xF2C14E); g.drawRect(x0 + 1, y0 + 1, cellW - 3, cellH - 3); g.drawRect(x0 + 2, y0 + 2, cellW - 5, cellH - 5) }
            val s = Stickers.cut(spot.art, spot.slot)
            g.color = Color.WHITE
            g.drawString(k, x0 + 6, y0 + 14)
            g.color = Color(0xB8C2D8)
            g.drawString("${spot.art}/${spot.slot}" + (s?.let { " ${it.width}×${it.height}" } ?: " —"), x0 + 6, y0 + cellH - 6)
            s ?: continue
            // as the card scales it: by whole pixels, or down by a whole divisor
            val fit = min(box.toFloat() / s.width, box.toFloat() / s.height)
            val scale = if (fit >= 1f) floor(fit) else 1f / ceil(1f / fit)
            val w = (s.width * scale).toInt(); val h = (s.height * scale).toInt()
            val ox = x0 + (cellW - w) / 2; val oy = y0 + 24 + (cellH - 50 - h) / 2
            for (y in 0 until h) for (x in 0 until w) {
                val p = s.argb[min(s.height - 1, (y / scale).toInt()) * s.width + min(s.width - 1, (x / scale).toInt())]
                val a = (p ushr 24) / 255f
                if (a == 0f) continue
                val bg = img.getRGB(ox + x, oy + y)
                fun ch(sh: Int) = (((p shr sh) and 255) * a + ((bg shr sh) and 255) * (1 - a)).toInt()
                img.setRGB(ox + x, oy + y, (ch(16) shl 16) or (ch(8) shl 8) or ch(0))
            }
        }
        g.dispose()
        ImageIO.write(img, "png", file)
    }
}
