package si.lanisce.lani.ui.notebook

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.book.Sketch
import si.lanisce.lani.game.scene.NoteBlock
import si.lanisce.lani.game.scene.Notebook
import si.lanisce.lani.game.scene.NotebookEntry
import si.lanisce.lani.game.scene.Stories
import si.lanisce.lani.game.scene.Story
import si.lanisce.lani.game.scene.StoryBooks
import si.lanisce.lani.game.scene.StoryFixtures
import si.lanisce.lani.game.scene.StoryHeard
import si.lanisce.lani.game.scene.StoryPicture
import si.lanisce.lani.game.scene.StoryTonight
import si.lanisce.lani.l10n.Dates
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The story notebook drawn as the app lays it out on a phone (NotebookSheet, companion/SCENES.md "The story notebook"):
 * a story's page (its title, each evening under its day, the paragraphs in ink on the rules with their translation in
 * pencil, the sketches pinned in with their captions) and the contents, with the bundled handwriting and [NotebookLook]'s
 * numbers; to build/notebook to look at. A look at the layout, not the app's own pixels. And the handwriting has every
 * letter the notebook writes, in all four languages.
 */
class NotebookRenderTest {
    private val pair = L10n.pair
    private val dir = File("build/notebook").apply { mkdirs() }
    private val day = LocalDate.of(2026, 9, 28)

    @After fun restore() {
        L10n.pair = pair
    }

    private val regular: Font by lazy { Font.createFont(Font.TRUETYPE_FONT, File("src/main/res/font/kalam_regular.ttf")) }
    private val bold: Font by lazy { Font.createFont(Font.TRUETYPE_FONT, File("src/main/res/font/kalam_bold.ttf")) }

    private val packs = listOf(
        "primorska" to LangPair(Lang.SL, Lang.EN),
        "friuli" to LangPair(Lang.IT, Lang.SL),
        "kaernten" to LangPair(Lang.DE, Lang.SL),
        "lakeland" to LangPair(Lang.EN, Lang.DE),
    )

    @Test fun `the handwriting has every letter the notebook writes`() {
        assertEquals("Kalam", regular.family)
        for (f in listOf(regular, bold)) assertEquals(-1, f.canDisplayUpTo("čšžćđČŠŽĆĐ äöüÄÖÜß àèéìíòóùúÀÈÉÌÒÙ »« „“ «» ‘’ – — …"))
        for ((culture, p) in packs) {
            for (base in Lang.entries.filter { it != p.target }) {
                val stories = StoryBooks.stories(listOf(StoryFixtures.campfire(culture, LangPair(p.target, base))))
                for (s in stories) {
                    val texts = listOf(s.title) + s.pictures.mapNotNull { it.caption }.flatMap { listOf(it.sl, it.en) } +
                        s.notes.flatMap { n -> n.text.flatMap { listOf(it.sl, it.en) } }
                    for (t in texts) {
                        val letters = t.filter { it.code < 0x2190 }
                        assertEquals("${s.id} ($culture, ${base.code}): $t", -1, regular.canDisplayUpTo(letters))
                    }
                }
            }
        }
    }

    @Test fun `a story's page and the contents, as the notebook lays them out`() {
        val p = LangPair(Lang.SL, Lang.EN)
        val stories = StoryBooks.stories(listOf(StoryFixtures.campfire("primorska", p)))
        fun story(id: String): Story = stories.first { it.id == id }
        var h: Map<String, StoryHeard> = emptyMap()
        fun hear(s: Story, lv: String, on: LocalDate) = s.parts.indices.forEach { c -> h = Stories.heard(h, StoryTonight(s, c, lv, false), on.plusDays(c.toLong())) }
        hear(story("zlatorog"), "A1", day.minusDays(12))
        hear(story("kralj-matjaz"), "A1", day.minusDays(11))
        hear(story("martin-krpan"), "A1", day.minusDays(9))
        hear(story("lepa-vida"), "A1", day.minusDays(4))
        hear(story("zvon-zelja"), "A1", day)
        L10n.pair = p
        val entries = Notebook.entries(stories, h)
        assertEquals(listOf("zlatorog", "kralj-matjaz", "martin-krpan", "lepa-vida", "zvon-zelja"), entries.map { it.id })
        val page = Page()
        page.entry(entries.first(), "A1")
        val img = page.save("notebook-page-zlatorog")
        // written in ink on the rules, a sketch or two pinned in
        assertTrue(page.sketches >= 2)
        var inked = 0
        for (y in 0 until img.height step 3) for (x in 0 until img.width step 3) if (near(img.getRGB(x, y), NotebookLook.DAY.ink.toInt())) inked++
        assertTrue("$inked", inked > 2000)
        val contents = Page()
        contents.contents(entries)
        contents.save("notebook-contents")
        val krpan = Page()
        krpan.entry(entries.first { it.id == "martin-krpan" }, "A1", evenings = 2)
        krpan.save("notebook-page-krpan")
    }

    private fun near(a: Int, b: Int): Boolean = kotlin.math.abs(Col.r(a) - Col.r(b)) + kotlin.math.abs(Col.g(a) - Col.g(b)) + kotlin.math.abs(Col.b(a) - Col.b(b)) < 30

    /**
     * A phone's page of the notebook, 412 dp wide at 2.5 px a dp, drawn as NotebookSheet lays it out: the rules
     * [NotebookLook.LINE] apart, the writing's baselines on them, the margin, the right edge kept for 🔊.
     */
    private inner class Page {
        val k = 2.5f
        val w = (412 * k).roundToInt()
        val img = BufferedImage(w, 9000, BufferedImage.TYPE_INT_RGB)
        val g: Graphics2D = img.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        }
        val look = NotebookLook.DAY
        val line = (NotebookLook.LINE * k).roundToInt()
        val base = (line * NotebookLook.BASELINE).roundToInt()
        val left = ((NotebookLook.MARGIN + NotebookLook.GUTTER) * k).roundToInt()
        val right = w - (NotebookLook.EDGE * k).roundToInt()
        /** The ruled line the writing is on (the first one under the page's top line). */
        var at = 1
        var sketches = 0

        fun col(v: Long) = Color(v.toInt(), true)

        /** [text] in [size] sp, wrapped to [width], each line on the next rule; returns the lines used. */
        fun write(text: String, size: Float, color: Long, strong: Boolean = false, width: Int = right - left - (NotebookLook.TOOLS * k).roundToInt(), align: Int = 0, lines: Int = 1): Int {
            g.font = (if (strong) bold else regular).deriveFont(size * k)
            g.color = col(color)
            val fm = g.fontMetrics
            val out = ArrayList<String>()
            var cur = ""
            for (word in text.split(" ")) {
                val next = if (cur.isEmpty()) word else "$cur $word"
                if (fm.stringWidth(next) > width && cur.isNotEmpty()) { out += cur; cur = word } else cur = next
            }
            if (cur.isNotEmpty()) out += cur
            out.forEachIndexed { i, l ->
                val y = (at + (lines - 1) + i * lines) * line + base
                val x = when (align) { 1 -> right - fm.stringWidth(l); 2 -> left + (width - fm.stringWidth(l)) / 2; else -> left }
                g.drawString(l, x, y)
            }
            at += out.size * lines
            return out.size
        }

        /** 🔊, level with the paragraph's first line, on the right. */
        fun speaker(row: Int) {
            val cx = right - (20 * k).roundToInt(); val cy = row * line + line / 2
            val r = (20 * k).roundToInt()
            g.color = Color(0xDCE3F0); g.fillOval(cx - r, cy - r, 2 * r, 2 * r)
            g.color = col(look.ink); g.stroke = BasicStroke(2f * k)
            g.fillRect(cx - (7 * k).toInt(), cy - (4 * k).toInt(), (5 * k).toInt(), (8 * k).toInt())
            g.fillPolygon(intArrayOf(cx - (2 * k).toInt(), cx + (4 * k).toInt(), cx + (4 * k).toInt(), cx - (2 * k).toInt()), intArrayOf(cy - (4 * k).toInt(), cy - (9 * k).toInt(), cy + (9 * k).toInt(), cy + (4 * k).toInt()), 4)
            g.drawArc(cx + (2 * k).toInt(), cy - (6 * k).toInt(), (8 * k).toInt(), (12 * k).toInt(), -50, 100)
        }

        fun title(text: String) {
            write(text, NotebookLook.TITLE, look.ink, strong = true, lines = 2)
            val y = (at - 1) * line + base + (7 * k).roundToInt()
            val wdt = g.fontMetrics.stringWidth(text)
            val path = Path2D.Float().apply { moveTo(left.toFloat(), y.toFloat()); var x = 0f; while (x < wdt) { x += 6f; lineTo(left + x, y + sin(x / 23f / k * 2.5f) * 1.6f * k) } }
            g.color = col(look.ink); g.stroke = BasicStroke(1.6f * k, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(path)
        }

        fun entry(e: NotebookEntry, level: String, evenings: Int = e.book.parts.size) {
            val t = e.story.title
            title(t.substringBefore(" · "))
            t.substringAfter(" · ", "").takeIf { it.isNotBlank() }?.let { write(it, NotebookLook.PENCIL, look.pencil) }
            for (ev in Notebook.evenings(e, level, "en").take(evenings + 1)) {
                at++
                val date = ev.day?.let { "${Dates.dayMonth(it, Lang.SL)}, ob ognju" } ?: "Ob ognju"
                write(date, NotebookLook.DATE, look.ink, width = right - left, align = 1)
                ev.title?.let { tt ->
                    write(tt.substringBefore(" · "), NotebookLook.HEADING, look.ink, strong = true)
                    tt.substringAfter(" · ", "").takeIf { it.isNotBlank() }?.let { write(it, NotebookLook.PENCIL, look.pencil) }
                }
                if (ev.locked) { write("Naslednji večer · Next evening", NotebookLook.PENCIL, look.pencil); continue }
                for (b in ev.blocks) when (b) {
                    is NoteBlock.Text -> {
                        speaker(at)
                        write(b.said, NotebookLook.TEXT, look.ink)
                        write(b.meant, NotebookLook.PENCIL, look.pencil)
                        at++
                    }
                    is NoteBlock.Sketch -> sketch(b.picture)
                }
            }
            at++
            write("Preveri se · Test yourself", NotebookLook.DATE, look.ink, strong = true)
        }

        /** A sketch on its card, as wide as [NotebookLook.CARD] of the writing, askew, taped or clipped, its caption under it. */
        fun sketch(p: StoryPicture) {
            sketches++
            val pin = NotebookLook.pin(p)
            val cw = ((right - left) * NotebookLook.CARD).roundToInt()
            val ch = cw * Sketch.H / Sketch.W
            val top = at * line + (12 * k).roundToInt()
            val x0 = left + (right - left - cw) / 2
            val c = Sketch.render(p)
            val card = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_RGB).apply { setRGB(0, 0, c.width, c.height, c.pixels, 0, c.width) }
            val saved = g.transform
            g.transform(AffineTransform.getRotateInstance(Math.toRadians(pin.tilt.toDouble()), x0 + cw / 2.0, top + ch / 2.0))
            g.color = col(look.shadow); g.fillRect(x0 + (3 * k).toInt(), top + (4 * k).toInt(), cw, ch)
            g.drawImage(card, x0, top, cw, ch, null)
            when (pin.fastening) {
                NotebookLook.Fastening.TAPE -> for ((cx, angle) in listOf(x0 to -38.0, x0 + cw to 38.0)) {
                    val t = g.transform
                    g.transform(AffineTransform.getRotateInstance(Math.toRadians(angle), cx.toDouble(), top.toDouble()))
                    g.color = col(look.tape); g.fillRect(cx - (32 * k).toInt(), top - (10 * k).toInt(), (64 * k).toInt(), (20 * k).toInt())
                    g.transform = t
                }
                NotebookLook.Fastening.CLIP -> {
                    val x = x0 + cw * pin.at; val s = k
                    val path = Path2D.Float().apply {
                        moveTo(x - 5 * s, top + 30 * s); lineTo(x - 5 * s, top - 12 * s)
                        curveTo(x - 5 * s, top - 20 * s, x + 7 * s, top - 20 * s, x + 7 * s, top - 12 * s)
                        lineTo(x + 7 * s, top + 22 * s)
                        curveTo(x + 7 * s, top + 27 * s, x - 1 * s, top + 27 * s, x - 1 * s, top + 22 * s)
                        lineTo(x - 1 * s, top - 7 * s)
                    }
                    g.color = col(look.clip); g.stroke = BasicStroke(2.2f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(path)
                }
            }
            g.transform = saved
            at += ((ch + (22 * k)) / line).toInt() + 1
            p.caption?.let { cap ->
                write(cap.sl, NotebookLook.DATE, look.ink, width = right - left, align = 2)
                write(cap.en, NotebookLook.PENCIL, look.pencil, width = right - left, align = 2)
            }
            at++
        }

        fun contents(entries: List<NotebookEntry>) {
            title("Zapisano ob ognju")
            write("Written down by the fire", NotebookLook.PENCIL, look.pencil)
            at++
            write("Kazalo · Contents", NotebookLook.HEADING, look.ink, strong = true)
            entries.forEachIndexed { i, e ->
                val said = e.story.title.substringBefore(" · ")
                val dateText = e.first?.let { Dates.short(it, Lang.SL) }.orEmpty() + if (e.book.parts.size > 1 && e.book.heard < e.book.parts.size) "  (${e.book.heard}/${e.book.parts.size})" else ""
                g.font = regular.deriveFont(NotebookLook.DATE * k)
                val dw = g.fontMetrics.stringWidth(dateText)
                val row = at
                write("${i + 1}. $said", NotebookLook.TEXT, look.ink, width = right - left - dw - (40 * k).roundToInt())
                g.font = regular.deriveFont(NotebookLook.TEXT * k)
                val tw = g.fontMetrics.stringWidth("${i + 1}. $said")
                g.color = col(look.pencil)
                var x = left + tw + (6 * k).roundToInt()
                while (x < right - dw - (6 * k).roundToInt()) { g.fillOval(x, row * line + base - (4 * k).roundToInt(), (2.2f * k).roundToInt(), (2.2f * k).roundToInt()); x += (7 * k).roundToInt() }
                g.font = regular.deriveFont(NotebookLook.DATE * k); g.color = col(look.ink)
                g.drawString(dateText, right - dw, row * line + base)
                e.story.title.substringAfter(" · ", "").takeIf { it.isNotBlank() && it != said }?.let { write(it, NotebookLook.PENCIL, look.pencil) }
            }
            at++
            write("${entries.size} zgodb · ${entries.sumOf { it.book.pictures }} skic", NotebookLook.PENCIL, look.pencil, width = right - left, align = 1)
        }

        init {
            // the paper: fibres, the rules to its foot, the margin; the writing goes over it
            val h = img.height
            g.color = col(look.paper); g.fillRect(0, 0, w, h)
            val rand = java.util.Random(11)
            repeat(h / 60) {
                val x = rand.nextFloat() * w; val y = rand.nextFloat() * h
                g.color = Color(col(look.pencil).red, col(look.pencil).green, col(look.pencil).blue, (255 * (0.05f + rand.nextFloat() * 0.05f)).toInt())
                g.drawLine(x.toInt(), y.toInt(), (x + 12 + rand.nextFloat() * 40).toInt(), (y + rand.nextFloat() * 3 - 1.5f).toInt())
            }
            g.color = col(look.rule); g.stroke = BasicStroke(k)
            var y = line + base + k.roundToInt()
            while (y < h) { g.drawLine(0, y, w, y); y += line }
            g.color = col(look.margin); g.stroke = BasicStroke(1.4f * k)
            val mx = (NotebookLook.MARGIN * k).roundToInt()
            g.drawLine(mx, 0, mx, h)
        }

        /** The page down to what was written (a phone's screen at least), as [name].png. */
        fun save(name: String): BufferedImage {
            val out = img.getSubimage(0, 0, w, maxOf((at + 3) * line, (840 * k).roundToInt()).coerceAtMost(img.height))
            ImageIO.write(out, "png", File(dir, "$name.png"))
            return out
        }
    }
}
