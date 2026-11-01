package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Effects
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.ScenePainters
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The day's pedlar carries his culture's load (docs/plans/05-built/polish-content.md): the krošnjar from Ribnica his
 * krošnja in Primorska, the cramâr from Carnia his crassigne in Friuli, the Kraxenträger from the Kanaltal his Kraxe in
 * Carinthia ("So viele kleine Laden!"), the packman from Kendal his big pack in the Lake District ("a big pack on his
 * back"): the scenes' sprite, his portrait, the map's small figures, and the road's pedlar at the gate and below the
 * watchtower. A contact sheet in build/scene-snapshots/pedlars/ for review.
 */
class PedlarsTest {
    private val dir = File("build/scene-snapshots/pedlars").apply { mkdirs() }
    private val cultures = listOf("primorska", "friuli", "kaernten", "lakeland")
    private val packs = mapOf(
        "primorska" to Looks.PACK_KROSNJA, "friuli" to Looks.PACK_CRASSIGNE, "kaernten" to Looks.PACK_KRAXE, "lakeland" to Looks.PACK_BUNDLE,
    )

    /** [art] alone in [culture]'s village, its feet at (32, 60) on a 64 × 64 canvas (ids: 1 where it drew). */
    private fun one(art: String, culture: String, pose: Pose = Pose.IDLE, flip: Boolean = false, walking: Boolean = false, seated: Boolean = false): PixelCanvas {
        val c = PixelCanvas(64, 64)
        c.reset(0)
        c.penId = 1
        PeoplePainter().also { it.culture = culture }.draw(c, art, 32, 60, 0.4, 7, talking = false, flip = flip, walking = walking, seated = seated, pose = pose)
        c.penId = 0
        return c
    }

    private fun top(c: PixelCanvas): Int = (0 until c.height).first { y -> (0 until c.width).any { x -> c.ids[y * c.width + x] != 0 } }

    @Test fun `each culture's pedlar carries its own load, and a culture without one the krosnja`() {
        for ((c, pack) in packs) {
            assertEquals(c, pack, Looks.pedlarPack(c))
            assertEquals(c, pack, Looks.of("pedlar", culture = c).pack)
            assertEquals(c, pack, Effects.lookOf("pedlar", 7, c).pack)
        }
        assertEquals(Looks.PACK_KROSNJA, Looks.pedlarPack("nowhere"))
        // nobody else carries one
        for (c in cultures) for (art in SceneArt.people - "pedlar") assertEquals("$art in $c", Looks.PACK_NONE, Looks.of(art, culture = c).pack)
    }

    @Test fun `the four pedlars are drawn otherwise, each load over his head and wider than a man`() {
        val drawn = cultures.associateWith { one("pedlar", it).pixels.toList() }
        assertEquals("four different pedlars", 4, drawn.values.toSet().size)
        for (c in cultures) {
            val man = one("man", c)
            val pedlar = one("pedlar", c)
            assertTrue("$c: the load stands over his head (${top(pedlar)} vs ${top(man)})", top(pedlar) <= top(man) - 3)
            val wide = { k: PixelCanvas -> (0 until 64).count { x -> (0 until 64).any { y -> k.ids[y * 64 + x] != 0 } } }
            assertTrue("$c: wider than a man", wide(pedlar) > wide(man))
            // walking, sitting and turned, still under his load, every pose drawn
            assertNotEquals(c, pedlar.pixels.toList(), one("pedlar", c, flip = true).pixels.toList())
            for (pose in Pose.entries) assertTrue("$c $pose", one("pedlar", c, pose).ids.count { it != 0 } > 150)
            assertTrue(c, one("pedlar", c, seated = true).ids.count { it != 0 } > 120)
        }
        // the Kraxe's box green, the crassigne walnut, the pack's bolt of Kendal green cloth
        val px = { c: String -> one("pedlar", c).pixels.toSet() }
        assertTrue(si.lanisce.lani.game.render.Col.hex(0x4E6E52) in px("kaernten"))
        assertTrue(si.lanisce.lani.game.render.Col.hex(0x7A4A2C) in px("friuli"))
        assertTrue(si.lanisce.lani.game.render.Col.hex(0x4E7A3E) in px("lakeland"))
    }

    @Test fun `his portrait shows his load, in the culture asked`() {
        val shots = cultures.map { c ->
            PixelCanvas(64, 64).also { PortraitsPainter().also { p -> p.culture = c }.render(it, "pedlar", Pose.IDLE, 0.4, 11f) }.pixels.toList()
        }
        assertEquals(4, shots.toSet().size)
    }

    private fun frame(art: String, culture: String, fx: Map<String, Float>) =
        SceneFrame(time = 3.0, hour = 11f, month = 6, objects = SceneArt.objects.getValue(art).toSet(), world = SceneWorld(culture = culture), fx = fx)

    private fun render(art: String, f: SceneFrame, k: Int = 2): PixelCanvas =
        PixelCanvas(240 * k, 160 * k).also { ScenePainters.create(art).render(it, f, Lens(k, 0, 0, 240, 160)) }

    @Test fun `the road's pedlar at the gate and below the watchtower is the culture's`() {
        for (art in listOf("palisade", "watchtower")) for (p in listOf(0.5f, 0.9f)) {
            val fx = mapOf("pedlar" to p, "gate" to 1f)
            val with = cultures.associateWith { render(art, frame(art, it, fx)).pixels }
            val none = cultures.associateWith { render(art, frame(art, it, fx - "pedlar")).pixels }
            for (c in cultures - "primorska") {
                val a = with.getValue(c); val b = none.getValue(c)
                val his = a.indices.filter { a[it] != b[it] }
                assertTrue("$art $c at $p: the pedlar is drawn", his.isNotEmpty())
                // where he is, he isn't the krošnjar: his load's colours aren't the krošnja's
                val k = with.getValue("primorska"); val kn = none.getValue("primorska")
                val krosnjar = a.indices.filter { k[it] != kn[it] }.map { k[it] }.toSet()
                assertTrue("$art $c at $p: not the krošnjar", his.map { a[it] }.toSet() != krosnjar)
            }
        }
    }

    @Test fun `render the pedlars sheet`() {
        // a row each: the sprite standing, walking, sitting and turned; the portrait; the map's 14 px and 7 px figures
        val scale = 6
        val shots = cultures.map { c ->
            listOf(one("pedlar", c), one("pedlar", c, walking = true), one("pedlar", c, seated = true), one("pedlar", c, flip = true, pose = Pose.TALK)) +
                PixelCanvas(64, 64).also { PortraitsPainter().also { p -> p.culture = c }.render(it, "pedlar", Pose.IDLE, 0.4, 11f) }
        }
        val img = BufferedImage(64 * 5 * scale, (64 * scale + 16) * cultures.size, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        for ((r, row) in shots.withIndex()) {
            val oy = r * (64 * scale + 16)
            g.color = Color.WHITE
            g.drawString(cultures[r], 4, oy + 12)
            for ((k, c) in row.withIndex()) for (y in 0 until 64 * scale) for (x in 0 until 64 * scale) {
                val px = c.pixels[(y / scale) * 64 + x / scale]
                img.setRGB(k * 64 * scale + x, oy + 16 + y, if (px ushr 24 == 0) 0x6A7A6A else px)
            }
        }
        g.dispose()
        ImageIO.write(img, "png", File(dir, "pedlars.png"))
        // the road's pedlar at the gate and below the watchtower (detail 3), each culture: the part of the picture he is in,
        // four times bigger
        val crops = ArrayList<BufferedImage>()
        for (art in listOf("palisade", "watchtower")) for (c in cultures) {
            val fx = mapOf("pedlar" to 0.5f, "gate" to 1f)
            val a = render(art, frame(art, c, fx), 3); val b = render(art, frame(art, c, fx - "pedlar"), 3)
            val his = a.pixels.indices.filter { a.pixels[it] != b.pixels[it] }
            if (his.isEmpty()) continue
            val x0 = (his.minOf { it % a.width } - 12).coerceAtLeast(0); val x1 = (his.maxOf { it % a.width } + 12).coerceAtMost(a.width - 1)
            val y0 = (his.minOf { it / a.width } - 12).coerceAtLeast(0); val y1 = (his.maxOf { it / a.width } + 12).coerceAtMost(a.height - 1)
            val img = BufferedImage((x1 - x0 + 1) * 4, (y1 - y0 + 1) * 4, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until img.height) for (x in 0 until img.width) img.setRGB(x, y, a.pixels[(y0 + y / 4) * a.width + x0 + x / 4])
            crops += img
        }
        val cw = crops.maxOf { it.width }; val ch = crops.maxOf { it.height }
        val gate = BufferedImage(cw * 4, ch * 2, BufferedImage.TYPE_INT_RGB)
        for ((i, img) in crops.withIndex()) gate.createGraphics().apply { drawImage(img, (i % 4) * cw, (i / 4) * ch, null); dispose() }
        ImageIO.write(gate, "png", File(dir, "pedlars-gate.png"))
    }
}
