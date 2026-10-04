package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Surprises
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.Portraits
import si.lanisce.lani.game.scene.SceneArt
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The strangers of the day's surprise, drawn in the people's style (People.kt): the krošnjar from Ribnica under his
 * krošnja and a pilgrim with his staff. Every pose, standing, walking, sitting, flipped, and their portraits by day
 * and at night; PNG sheets in build/scene-snapshots (00-strangers-…) for review.
 */
class StrangersSpriteTest {
    private val dir = File("build/scene-snapshots").apply { mkdirs() }
    private val strangers = listOf("pedlar", "pilgrim")

    /** Draws [art] alone with its feet at (32, 60) on a 64×64 canvas; returns the canvas (ids: 1 where the sprite drew). */
    private fun one(art: String, pose: Pose = Pose.IDLE, time: Double = 0.4, flip: Boolean = false, walking: Boolean = false, seated: Boolean = false): PixelCanvas {
        val c = PixelCanvas(64, 64)
        c.reset(0)
        c.penId = 1
        PeoplePainter().draw(c, art, 32, 60, time, 7, talking = false, flip = flip, walking = walking, seated = seated, pose = pose)
        c.penId = 0
        return c
    }

    /** The topmost row the sprite drew on. */
    private fun top(c: PixelCanvas): Int = (0 until c.height).first { y -> (0 until c.width).any { x -> c.ids[y * c.width + x] != 0 } }

    /** The columns the sprite drew on, left to right. */
    private fun span(c: PixelCanvas): IntRange {
        val xs = (0 until c.width).filter { x -> (0 until c.height).any { y -> c.ids[y * c.width + x] != 0 } }
        return xs.first()..xs.last()
    }

    @Test fun `the strangers are sprites of the scenes, and the day's surprise draws them`() {
        for (art in strangers) assertTrue(art, art in SceneArt.people)
        assertEquals(setOf("pedlar", "pilgrim"), Surprises.strangers.values.map { it.art }.toSet())
        for (v in Surprises.strangers.values) assertTrue(v.id, v.art in SceneArt.people && v.home == listOf("spot:road"))
    }

    @Test fun `the pedlar carries his krošnja above his hat, the pilgrim his staff and bag`() {
        val man = one("man")
        val pedlar = one("pedlar")
        val pilgrim = one("pilgrim")
        // the load on the krošnja stands well above a man's head and hat; the staff's knob and gourd too
        assertTrue("krošnja: ${top(pedlar)} vs ${top(man)}", top(pedlar) <= top(man) - 6)
        assertTrue("staff: ${top(pilgrim)} vs ${top(man)}", top(pilgrim) <= top(man) - 3)
        // both are wider than a man: the frame's posts and spoons; the staff and the bag
        assertTrue("${span(pedlar)} vs ${span(man)}", span(pedlar).count() > span(man).count())
        assertTrue("${span(pilgrim)} vs ${span(man)}", span(pilgrim).count() > span(man).count())
        // wood in the load (the sieve's rim, the spoons), the scallop's cream on the pilgrim's hat, his gourd
        val wood = setOf(si.lanisce.lani.game.render.Pal.WOOD_L, si.lanisce.lani.game.render.Pal.WOOD_M)
        assertTrue((0 until 64 * (top(man) - 1)).any { pedlar.pixels[it] in wood && pedlar.ids[it] != 0 })
        assertTrue(pilgrim.pixels.any { it == si.lanisce.lani.game.render.Col.hex(0xF4EEDD) })
        assertTrue(pilgrim.pixels.any { it == si.lanisce.lani.game.render.Col.hex(0xD9A040) })
        // facing the other way mirrors the load: the spoons go to the other side
        val left = one("pedlar", flip = true)
        assertNotEquals(pedlar.pixels.toList(), left.pixels.toList())
    }

    @Test fun `the strangers render in every pose, walking and sitting, and every pose differs from idle`() {
        for (art in strangers) {
            val idle = (0..2).map { k -> one(art, time = k * 0.4).pixels.toList() }
            for (pose in Pose.entries) {
                val c = one(art, pose)
                assertTrue("$art $pose draws", c.ids.count { it != 0 } > 150)
                assertEquals("$art $pose deterministic", c.pixels.toList(), one(art, pose).pixels.toList())
                if (pose == Pose.IDLE) continue
                val posed = (0..2).map { k -> one(art, pose, time = k * 0.4).pixels.toList() }
                assertNotEquals("$art $pose looks like idle", idle, posed)
            }
            // walking moves the legs, sitting folds them; the load stays on the back
            assertNotEquals(one(art, time = 0.0, walking = true).pixels.toList(), one(art, time = 1.0 / 7, walking = true).pixels.toList())
            assertTrue(one(art, seated = true).ids.count { it != 0 } > 120)
        }
    }

    @Test fun `their portraits, by day and at night, in every pose`() {
        for (art in strangers) for (pose in Pose.entries) for (hour in listOf(11f, 22f)) {
            val a = PixelCanvas(64, 64); val b = PixelCanvas(64, 64)
            Portraits.painter.render(a, art, pose, 1.3, hour); Portraits.painter.render(b, art, pose, 1.3, hour)
            assertArrayEquals("$art $pose $hour", a.pixels, b.pixels)
            assertTrue("$art $pose draws something", a.pixels.toSet().size > 12)
        }
        // the pedlar's portrait shows his load above his hat: the top rows aren't all backdrop
        val c = PixelCanvas(64, 64)
        Portraits.painter.render(c, "pedlar", Pose.IDLE, 0.4, 11f)
        val bare = PixelCanvas(64, 64).also { Portraits.painter.render(it, "man", Pose.IDLE, 0.4, 11f) }
        assertNotEquals(c.pixels.copyOfRange(0, 64 * 12).toList(), bare.pixels.copyOfRange(0, 64 * 12).toList())
    }

    @Test fun `render the strangers sheets`() {
        // every pose (columns), then walking, sitting and facing left; the pedlar's row, the pilgrim's row
        val scale = 6
        val cols = Pose.entries.size + 3
        val sheet = BufferedImage(64 * cols * scale, 64 * strangers.size * scale, BufferedImage.TYPE_INT_RGB)
        for ((r, art) in strangers.withIndex()) {
            val shots = Pose.entries.map { one(art, it) } + listOf(one(art, walking = true), one(art, seated = true), one(art, flip = true, pose = Pose.TALK))
            for ((k, c) in shots.withIndex()) for (y in 0 until 64 * scale) for (x in 0 until 64 * scale) {
                val i = (y / scale) * 64 + x / scale
                sheet.setRGB(k * 64 * scale + x, r * 64 * scale + y, if (c.ids[i] != 0) c.pixels[i] else if (((x / scale) / 8 + (y / scale) / 8) % 2 == 0) 0x72AC47 else 0x5F983D)
            }
        }
        ImageIO.write(sheet, "png", File(dir, "00-strangers-sheet.png"))
        // close: idle, a cheer, facing left talking; the sprite's box only (x 8..55, y 14..61)
        val cs = 10; val cw = 48; val ch = 48
        val close = BufferedImage(cw * 3 * cs, ch * strangers.size * cs, BufferedImage.TYPE_INT_RGB)
        for ((r, art) in strangers.withIndex()) for ((k, c) in listOf(one(art), one(art, Pose.CHEER), one(art, Pose.TALK, flip = true)).withIndex()) {
            for (y in 0 until ch * cs) for (x in 0 until cw * cs) {
                val i = (14 + y / cs) * 64 + 8 + x / cs
                close.setRGB(k * cw * cs + x, r * ch * cs + y, if (c.ids[i] != 0) c.pixels[i] else 0x72AC47)
            }
        }
        ImageIO.write(close, "png", File(dir, "00-strangers-closeup.png"))
        // the portraits, by day (the first rows) and at night
        val px = 64; val ps = 3
        val portraits = BufferedImage(px * Pose.entries.size * ps, px * strangers.size * 2 * ps, BufferedImage.TYPE_INT_RGB)
        for ((r, art) in strangers.withIndex()) for ((h, hour) in listOf(11f, 22f).withIndex()) for ((p, pose) in Pose.entries.withIndex()) {
            val c = PixelCanvas(px, px)
            Portraits.painter.render(c, art, pose, 0.4 + p * 0.3, hour)
            for (y in 0 until px * ps) for (x in 0 until px * ps) portraits.setRGB(p * px * ps + x, (r * 2 + h) * px * ps + y, c.pixels[(y / ps) * px + x / ps])
        }
        ImageIO.write(portraits, "png", File(dir, "00-strangers-portraits.png"))
    }
}
