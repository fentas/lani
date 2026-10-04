package si.lanisce.lani.game.render.book

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.scene.NoteBlock
import si.lanisce.lani.game.scene.Notebook
import si.lanisce.lani.game.scene.Stories
import si.lanisce.lani.game.scene.Story
import si.lanisce.lani.game.scene.StoryBooks
import si.lanisce.lani.game.scene.StoryFixtures
import si.lanisce.lani.game.scene.StoryHeard
import si.lanisce.lani.game.scene.StoryPicture
import si.lanisce.lani.game.scene.StoryTonight
import si.lanisce.lani.game.scene.Vignettes
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/**
 * The curated stories' pictures (companion/SCENES.md, "The story notebook"): every one of the four packs' stories has
 * its 2-4 (one or two an evening in chapters), each made of the vignette library, after a paragraph its evening has, its
 * caption read in the learner's pair, and they are all sketched in the notebook once the story is heard. Sheets of
 * them go to build/book-pictures to look at.
 */
class CuratedPicturesTest {
    private val pair = L10n.pair
    private val day = LocalDate.of(2026, 9, 27)
    private val dir = File("build/book-pictures").apply { mkdirs() }

    @After fun restore() {
        L10n.pair = pair
    }

    /** Each pack's campfire stories, read by a learner of that pack's language. */
    private val packs = listOf(
        "primorska" to LangPair(Lang.SL, Lang.EN),
        "friuli" to LangPair(Lang.IT, Lang.SL),
        "kaernten" to LangPair(Lang.DE, Lang.SL),
        "lakeland" to LangPair(Lang.EN, Lang.DE),
    )

    private fun stories(culture: String, p: LangPair): List<Story> = StoryBooks.stories(listOf(StoryFixtures.campfire(culture, p)))

    /** [s] heard to its end at [level], evening by evening. */
    private fun heard(s: Story, level: String = "A1", evenings: Int = s.parts.size): Map<String, StoryHeard> =
        (0 until evenings).fold(emptyMap()) { h, c -> Stories.heard(h, StoryTonight(s, c, level, retold = false), day) }

    @Test fun `every curated story has its pictures, made of the library, where its evenings have paragraphs, shown in its book`() {
        var all = 0
        for ((culture, p) in packs) {
            val list = stories(culture, p)
            assertTrue(culture, list.size >= 5)
            for (s in list) {
                if (s.parts.size == 1) assertTrue("${s.id}: ${s.pictures.size}", s.pictures.size in 2..4)
                else s.parts.indices.forEach { c -> assertTrue("${s.id} evening ${c + 1}", s.picturesOf(c).size in 1..2) }
                for (pic in s.pictures) {
                    assertEquals(s.id, emptyList<String>(), Vignettes.problems(pic))
                    val part = s.parts[pic.chapter - 1]
                    val most = part.levels.values.maxOf { StoryBooks.paragraphs(it).size }
                    assertTrue("${s.id}: after ${pic.after} of $most", pic.after in 1..most)
                    val cap = pic.caption!!
                    assertTrue("${s.id}: ${cap.sl} · ${cap.en}", cap.sl.isNotBlank() && cap.en.isNotBlank() && cap.sl != cap.en)
                    assertTrue(BookPictures.render(pic).pixels.toSet().size > 40)
                }
                // heard at A1 and at A2: every picture is sketched in the notebook, at each level
                for (lv in listOf("A1", "A2")) {
                    val entry = Notebook.entries(listOf(s), heard(s, lv)).single()
                    assertEquals("${s.id} $lv", s.pictures.size, Notebook.evenings(entry, lv, p.base.code).sumOf { ev -> ev.blocks.count { it is NoteBlock.Sketch } })
                }
                all += s.pictures.size
            }
            sheet("curated-$culture", list.flatMap { it.pictures })
        }
        assertEquals(116, all)
    }

    /** Pictures [list] three a row, twice their size, as build/book-pictures/[name].png. */
    private fun sheet(name: String, list: List<StoryPicture>) {
        val scale = 2
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
}
