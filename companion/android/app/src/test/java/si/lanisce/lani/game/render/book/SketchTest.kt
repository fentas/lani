package si.lanisce.lani.game.render.book

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PictureThing
import si.lanisce.lani.game.scene.StoryBooks
import si.lanisce.lani.game.scene.StoryFixtures
import si.lanisce.lani.game.scene.StoryPicture
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * The notebook's sketches (companion/SCENES.md, "The story notebook"): every picture of the curated stories drawn in pencil
 * from its vignette, the same every time, graphite and the paper and at most one watercolour; sheets of them, and a sketch
 * beside its coloured vignette, go to build/notebook to look at.
 */
class SketchTest {
    private val pair = L10n.pair
    private val dir = File("build/notebook").apply { mkdirs() }

    @After fun restore() {
        L10n.pair = pair
    }

    private val packs = listOf(
        "primorska" to LangPair(Lang.SL, Lang.EN),
        "friuli" to LangPair(Lang.IT, Lang.SL),
        "kaernten" to LangPair(Lang.DE, Lang.SL),
        "lakeland" to LangPair(Lang.EN, Lang.DE),
    )

    private fun pictures(culture: String, p: LangPair): List<StoryPicture> =
        StoryBooks.stories(listOf(StoryFixtures.campfire(culture, p))).flatMap { it.pictures }

    @Test fun `a sketch is the vignette in pencil, the same every time, graphite and one watercolour at most`() {
        val all = packs.flatMap { (c, p) -> pictures(c, p) }
        assertTrue(all.size > 100)
        for (pic in all) {
            val s = Sketch.render(pic)
            assertEquals(Sketch.W to Sketch.H, s.width to s.height)
            // drawn: graphite lines, a good deal of them, and the paper left round them
            val dark = s.pixels.count { Col.lum(it) < 0.5f }
            assertTrue("${pic.caption?.en}: $dark dark pixels", dark > s.pixels.size / 200)
            assertTrue("${pic.caption?.en}: much of it paper", s.pixels.count { Col.lum(it) > 0.8f } > s.pixels.size / 4)
            // the card's edge is paper: the drawing fades out before it
            for (x in 0 until Sketch.W) assertTrue(Col.lum(s.pixels[x]) > 0.85f)
            // colour only where its one watercolour is: every pixel grey, or of the accent's hue
            val accent = Sketch.accentOf(pic)
            val coloured = s.pixels.filter { chroma(it) > 0.12f }
            if (accent == null) assertTrue("${pic.caption?.en}: ${coloured.size} coloured", coloured.size < s.pixels.size / 500)
            else assertTrue(coloured.all { hueNear(it, accent) })
        }
        // the same picture, the same sketch
        assertArrayEquals(Sketch.render(all.first()).pixels, Sketch.render(all.first()).pixels)
    }

    @Test fun `what shines is gold, a fire is fire, the water is blue, else graphite alone`() {
        fun pic(bg: String, vararg things: PictureThing, props: List<PictureThing> = emptyList(), night: Boolean = false) =
            StoryPicture(bg = bg, figures = things.toList(), props = props, night = night)
        assertEquals(InkPal.GOLD, Sketch.accentOf(pic("lake", PictureThing("chamois", gold = true))))
        assertEquals(InkPal.FIRE, Sketch.accentOf(pic("forest", props = listOf(PictureThing("fire")))))
        assertEquals(InkPal.WATER, Sketch.accentOf(pic("lake", PictureThing("boat"))))
        assertNull(Sketch.accentOf(pic("lake", PictureThing("boat"), night = true)))
        assertNull(Sketch.accentOf(pic("mountains", PictureThing("hunter"))))
    }

    @Test fun `sheets of the sketches, and a sketch beside its vignette`() {
        for ((c, p) in packs) sheet("sketches-$c", pictures(c, p))
        val z = pictures("primorska", LangPair(Lang.SL, Lang.EN))
        val horns = z.first { it.caption?.sl?.startsWith("Rogovi se svetijo") == true }
        besides("sketch-beside-vignette", horns)
    }

    private fun chroma(c: Int): Float {
        val r = Col.r(c); val g = Col.g(c); val b = Col.b(c)
        return (maxOf(r, g, b) - minOf(r, g, b)) / 255f
    }

    private fun hue(c: Int): Float {
        val r = Col.r(c) / 255f; val g = Col.g(c) / 255f; val b = Col.b(c) / 255f
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
        if (mx - mn < 1e-4f) return 0f
        val h = when (mx) {
            r -> ((g - b) / (mx - mn)).mod(6f)
            g -> (b - r) / (mx - mn) + 2f
            else -> (r - g) / (mx - mn) + 4f
        }
        return h * 60f
    }

    /** Within 40° of [accent]'s hue (the card's own warm white aside). */
    private fun hueNear(c: Int, accent: Int): Boolean {
        val d = abs(hue(c) - hue(accent)).let { minOf(it, 360f - it) }
        return d < 40f || abs(hue(c) - hue(Sketch.CARD)) < 25f
    }

    private fun write(c: PixelCanvas, img: BufferedImage, ox: Int, oy: Int, scale: Int = 1) {
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(ox + x, oy + y, c.pixels[(y / scale) * c.width + x / scale])
    }

    private fun sheet(name: String, list: List<StoryPicture>) {
        val cols = 3
        val gap = 12
        val rows = (list.size + cols - 1) / cols
        val img = BufferedImage(cols * (Sketch.W + gap) + gap, rows * (Sketch.H + gap) + gap, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics(); g.color = java.awt.Color(0xE9E4D8); g.fillRect(0, 0, img.width, img.height); g.dispose()
        list.forEachIndexed { i, p -> write(Sketch.render(p), img, gap + (i % cols) * (Sketch.W + gap), gap + (i / cols) * (Sketch.H + gap)) }
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    /** The coloured vignette (as big as the sketch, in whole pixels) and its sketch, side by side. */
    private fun besides(name: String, p: StoryPicture) {
        val gap = 16
        val img = BufferedImage(2 * Sketch.W + 3 * gap, Sketch.H + 2 * gap, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics(); g.color = java.awt.Color(0xE9E4D8); g.fillRect(0, 0, img.width, img.height); g.dispose()
        val v = BookPictures.render(p)
        // the vignette's inner part, as the sketch has it, at the sketch's scale
        val crop = (BookPictures.W * Sketch.SCALE - Sketch.W) / 2 / Sketch.SCALE
        for (y in 0 until Sketch.H) for (x in 0 until Sketch.W) img.setRGB(gap + x, gap + y, v.pixels[(crop + y / Sketch.SCALE) * v.width + crop + x / Sketch.SCALE])
        write(Sketch.render(p), img, 2 * gap + Sketch.W, gap)
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }
}
