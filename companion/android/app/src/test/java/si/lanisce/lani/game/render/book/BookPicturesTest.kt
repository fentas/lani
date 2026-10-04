package si.lanisce.lani.game.render.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PictureThing
import si.lanisce.lani.game.scene.StoryPicture
import si.lanisce.lani.game.scene.Vignettes
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The story pictures' vignette library (companion/SCENES.md, "The story notebook"): every background, figure and
 * prop draws, in the ink's few colours, the same every time; a thing drawn changes the picture where it stands. Contact
 * sheets of the library go to build/book-pictures to look at.
 */
class BookPicturesTest {
    private val dir = File("build/book-pictures").apply { mkdirs() }

    private fun differs(a: PixelCanvas, b: PixelCanvas): Int = a.pixels.indices.count { a.pixels[it] != b.pixels[it] }

    @Test fun `every background draws, by day and by night, the same every time`() {
        for (bg in Vignettes.backgrounds) {
            val day = BookPictures.render(StoryPicture(bg = bg))
            val again = BookPictures.render(StoryPicture(bg = bg))
            assertTrue(bg, day.pixels.contentEquals(again.pixels))
            val night = BookPictures.render(StoryPicture(bg = bg, night = true))
            // the cave is dark by day too; every other sky darkens
            if (bg != "cave") assertTrue("$bg at night", differs(day, night) > 2000)
            // not blank paper: its lines and washes
            assertTrue(bg, day.pixels.toSet().size > 40)
        }
        sheet("backgrounds", Vignettes.backgrounds.map { StoryPicture(bg = it) } + Vignettes.backgrounds.take(4).map { StoryPicture(bg = it, night = true) })
    }

    @Test fun `every figure and prop draws where it stands, flipped to face left, bigger, gilded`() {
        val empty = BookPictures.render(StoryPicture(bg = "meadow"))
        for (id in Vignettes.figures) {
            val at = BookPictures.render(StoryPicture(bg = "meadow", figures = listOf(PictureThing(id, x = 0.5f))))
            assertTrue(id, differs(empty, at) > 40)
            // an animal, or someone holding something, faces the other way (a girl in her dress looks the same)
            val left = BookPictures.render(StoryPicture(bg = "meadow", figures = listOf(PictureThing(id, x = 0.5f, flip = true))))
            if (id in FACING) assertTrue("$id flipped", differs(at, left) > 10)
            val big = BookPictures.render(StoryPicture(bg = "meadow", figures = listOf(PictureThing(id, x = 0.5f, size = 1.6f))))
            assertTrue("$id bigger", differs(empty, big) > differs(empty, at))
        }
        for (id in Vignettes.props) {
            val at = BookPictures.render(StoryPicture(bg = "meadow", props = listOf(PictureThing(id, x = 0.5f, y = if (id in SKY) 0.8f else 0f))))
            assertTrue(id, differs(empty, at) > 20)
        }
        // Zlatorog: the chamois white with golden horns
        val chamois = BookPictures.render(StoryPicture(bg = "meadow", figures = listOf(PictureThing("chamois"))))
        val zlatorog = BookPictures.render(StoryPicture(bg = "meadow", figures = listOf(PictureThing("chamois", gold = true))))
        assertTrue(differs(chamois, zlatorog) > 20)
        sheet("figures", Vignettes.figures.chunked(2).map { two -> StoryPicture(bg = "meadow", figures = two.mapIndexed { i, id -> PictureThing(id, x = 0.3f + i * 0.4f) }) })
        sheet("figures-big", Vignettes.figures.chunked(3).map { three -> StoryPicture(bg = "meadow", figures = three.mapIndexed { i, id -> PictureThing(id, x = 0.18f + i * 0.33f, size = 1.8f) }) }, scale = 3)
        sheet("props", Vignettes.props.chunked(3).map { three -> StoryPicture(bg = "meadow", props = three.mapIndexed { i, id -> PictureThing(id, x = 0.2f + i * 0.3f, y = if (id in SKY) 0.75f else 0f) }) })
    }

    @Test fun `pictures made of the library, as a story file gives them`() {
        fun t(id: String, x: Float, y: Float = 0f, size: Float = 1f, flip: Boolean = false, gold: Boolean = false) = PictureThing(id, x, y, size, flip, gold)
        sheet(
            "examples",
            listOf(
                StoryPicture(bg = "mountains", figures = listOf(t("hunter", 0.25f), t("chamois", 0.68f, 0.32f, flip = true, gold = true)), props = listOf(t("flower", 0.45f))),
                StoryPicture(bg = "cave", figures = listOf(t("king", 0.5f, 0.05f), t("soldier", 0.2f), t("soldier", 0.82f, flip = true)), props = listOf(t("table", 0.5f))),
                StoryPicture(bg = "mountains", night = false, figures = listOf(t("peasant", 0.3f, size = 1.2f), t("horse", 0.62f)), props = listOf(t("sack", 0.62f, 0.2f), t("cloud", 0.8f, 0.85f))),
                StoryPicture(bg = "lake", night = true, figures = listOf(t("boat", 0.35f, 0.2f)), props = listOf(t("chapel", 0.7f, 0.3f), t("bell", 0.52f, 0.62f, gold = true), t("moon", 0.15f, 0.9f))),
                StoryPicture(bg = "bridge", figures = listOf(t("devil", 0.7f, flip = true), t("dog", 0.3f))),
                StoryPicture(bg = "town", figures = listOf(t("dragon", 0.6f, flip = true), t("knight", 0.18f), t("ox", 0.38f))),
            ),
            scale = 3,
        )
    }

    @Test fun `a thing further up stands higher, and the far ones are drawn first`() {
        val low = BookPictures.render(StoryPicture(bg = "mountains", figures = listOf(PictureThing("chamois", x = 0.5f, y = 0f))))
        val high = BookPictures.render(StoryPicture(bg = "mountains", figures = listOf(PictureThing("chamois", x = 0.5f, y = 0.6f))))
        fun topOf(c: PixelCanvas, empty: PixelCanvas) = c.pixels.indices.first { c.pixels[it] != empty.pixels[it] } / c.width
        val empty = BookPictures.render(StoryPicture(bg = "mountains"))
        assertTrue(topOf(high, empty) < topOf(low, empty) - 20)
        assertEquals(BookPictures.W, low.width)
        assertEquals(BookPictures.H, low.height)
    }

    /** Pictures [list] side by side, three a row, each twice its size, as build/book-pictures/[name].png. */
    private fun sheet(name: String, list: List<StoryPicture>, scale: Int = 2) {
        val cols = 3
        val rows = (list.size + cols - 1) / cols
        val img = BufferedImage(cols * BookPictures.W * scale, rows * BookPictures.H * scale, BufferedImage.TYPE_INT_RGB)
        list.forEachIndexed { i, p ->
            val c = BookPictures.render(p)
            val ox = (i % cols) * BookPictures.W * scale; val oy = (i / cols) * BookPictures.H * scale
            for (y in 0 until BookPictures.H * scale) for (x in 0 until BookPictures.W * scale) img.setRGB(ox + x, oy + y, c.pixels[(y / scale) * c.width + x / scale])
        }
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    private companion object {
        val SKY = setOf("cloud", "sun", "moon", "stars", "crown")
        val FACING = setOf(
            "king", "boy", "old-man", "hunter", "knight", "soldier", "giant", "devil", "dragon", "chamois", "horse", "ox", "boar",
            "dog", "cat", "bird", "boat", "sheep",
        )
    }
}
