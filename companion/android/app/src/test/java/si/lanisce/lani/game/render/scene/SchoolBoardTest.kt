package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Courses
import si.lanisce.lani.game.CourseProgress
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.SheetBest
import si.lanisce.lani.game.UnitProgress
import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.Blackboard
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainter
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/**
 * The school's blackboard with the learner's lesson (companion/SCENES.md, "The school's blackboard"; Jan, 2026-10-14: "I
 * like the chalkboard; maybe it could show what we're currently / last learned in school"): which unit it shows, what of
 * it fits in the board's hand ([BoardFont]), every letter of the course written, and the board's chalk in the picture at
 * every detail; rendered for review (build/scene-snapshots/school/board-*).
 */
class SchoolBoardTest {
    private val dir = File("build/scene-snapshots/school").apply { mkdirs() }
    private val course = assertNotNullAnd(Courses.bundled("sl"))
    private val today = LocalDate.of(2026, 10, 14)

    private fun <T> assertNotNullAnd(v: T?): T { assertNotNull("the bundled course (companion/course/sl)", v); return v!! }

    /** The board's columns on the scene's canvas, its margins off. */
    private val width = 51 - 2

    private fun state(vararg units: Pair<String, UnitProgress>) = GameState(course = CourseProgress(classroom = "2026-10-13", units = units.associate { "sl/${it.first}" to it.second }))

    @Test fun `the board shows the unit worked on last, else the last done, else a welcome`() {
        assertNull("no course: the school's own greeting", Blackboard.of(GameState(), null))
        assertEquals("before the first unit", Blackboard.WELCOME, Blackboard.of(GameState(), course))
        // Jan's (2026-10-14): biti under way, its intro done
        val biti = Blackboard.of(state("biti" to UnitProgress(started = "2026-10-13", intro = 4, introDone = true)), course)!!
        assertEquals("Biti: sem, si, je", biti.title)
        assertEquals(listOf("jaz sem", "ti si", "on je", "mi smo", "vi ste", "oni so"), biti.forms.map { it.joinToString(" ") })
        // biti done yesterday, spol begun today: spol; spol done today too: spol, the later
        val s = state("biti" to UnitProgress(started = "2026-10-12", done = "2026-10-13"), "spol" to UnitProgress(started = "2026-10-14"))
        assertEquals("spol", Blackboard.unit(s, course)?.id)
        // on the same day the unit under way before the one done
        val same = state("biti" to UnitProgress(started = "2026-10-14", done = "2026-10-14"), "spol" to UnitProgress(started = "2026-10-14"))
        assertEquals("spol", Blackboard.unit(same, course)?.id)
        // a sheet done later than the other's start counts
        val sheet = state(
            "biti" to UnitProgress(started = "2026-10-10", sheets = mapOf("biti-1" to SheetBest(3, 0, 4, "2026-10-14"))),
            "spol" to UnitProgress(started = "2026-10-12"),
        )
        assertEquals("biti", Blackboard.unit(sheet, course)?.id)
        // every unit's forms are the course language's words of its table, no translation among them
        for (u in course.units.values) {
            val f = Blackboard.forms(u)
            assertTrue("${u.id}: forms", f.isNotEmpty() && f.size <= Blackboard.MAX_FORMS)
            assertTrue("${u.id}: no comma lists: $f", f.none { c -> c.any { ", " in it } })
        }
    }

    @Test fun `the Italian course's board - every letter written, every lesson laid out, an Italian welcome and day`() {
        val it = assertNotNullAnd(Courses.bundled("it"))
        val missing = HashSet<Char>()
        for (u in it.units.values) {
            val b = Blackboard(u.title["it"], Blackboard.forms(u), "it")
            for (ch in (b.title.orEmpty() + b.forms.flatten().joinToString(""))) if (!BoardFont.knows(ch)) missing.add(ch)
            val lines = BoardFont.lines(b, width)
            assertTrue("${u.id}: the title first", lines.first().title)
            assertTrue("${u.id}: $lines in ${BoardFont.LINES} lines", lines.size in 2..BoardFont.LINES)
            assertTrue("${u.id}: every line fits: $lines", lines.all { BoardFont.width(it.text) <= width })
        }
        assertTrue("letters the board can't write: $missing", missing.isEmpty())
        for (ch in "àèéìòùÈ'") assertTrue("$ch", BoardFont.knows(ch))
        // before the first unit of the Italian course: "Benvenuti!" and the day as Italy writes it
        val welcome = Blackboard.of(GameState(), it)!!
        assertEquals("it", welcome.language)
        assertEquals(listOf(BoardFont.Line("Benvenuti!", true), BoardFont.Line("14/10/2026", false)), BoardFont.lines(welcome, width, date = welcome.day(today)))
        // a unit begun: its title and forms in Italian (essere: io sono · tu sei)
        val essere = Blackboard.of(GameState(course = CourseProgress(classroom = "2026-10-13", units = mapOf("it/essere" to UnitProgress(started = "2026-10-13")))), it)!!
        assertEquals("Essere: sono, sei, è", essere.title)
        assertEquals(listOf("io sono", "tu sei"), essere.forms.take(2).map { f -> f.joinToString(" ") })
    }

    @Test fun `every letter of the course is written, and what fits is laid out`() {
        val missing = HashSet<Char>()
        for (u in course.units.values) {
            val b = Blackboard(u.title["sl"], Blackboard.forms(u))
            for (ch in (b.title.orEmpty() + b.forms.flatten().joinToString(""))) if (!BoardFont.knows(ch)) missing.add(ch)
            val lines = BoardFont.lines(b, width)
            assertTrue("${u.id}: the title first", lines.first().title)
            assertTrue("${u.id}: $lines in ${BoardFont.LINES} lines", lines.size in 2..BoardFont.LINES)
            assertTrue("${u.id}: every line fits: $lines", lines.all { BoardFont.width(it.text) <= width })
            assertTrue("${u.id}: a form at least: $lines", lines.any { !it.title })
        }
        assertTrue("letters the board can't write: $missing", missing.isEmpty())
        for (ch in "čšžČŠŽćéèäöü") assertTrue("$ch", BoardFont.knows(ch))
        // Jan's biti: the title whole, four forms on two lines
        val biti = Blackboard("Biti: sem, si, je", listOf("jaz sem", "ti si", "on je", "mi smo", "vi ste", "oni so").map { it.split(' ') })
        assertEquals(
            listOf(BoardFont.Line("Biti", true), BoardFont.Line("jaz sem · ti si", false), BoardFont.Line("on je · mi smo", false)),
            BoardFont.lines(biti, width),
        )
        // a title too long for a line: its head before ": ", over two lines at its commas, else its head before a "?"
        assertEquals(listOf("Kje?"), BoardFont.lines(Blackboard("Kje? V hiši, na vrtu, pri babici", listOf(listOf("šola", "v šoli"))), width).filter { it.title }.map { it.text })
        assertEquals(listOf("Moški, ženski,", "srednji spol"), BoardFont.lines(Blackboard("Moški, ženski, srednji spol", listOf(listOf("-a", "miza"))), width).filter { it.title }.map { it.text })
        // a form too wide for a line: its last cell
        assertEquals(BoardFont.Line("imenujem se", false), BoardFont.lines(Blackboard("Učim se", listOf(listOf("imenovati se", "imenujem se"))), width)[1])
        // before the first unit: a welcome, and the day
        assertEquals(listOf(BoardFont.Line("Dobrodošli!", true), BoardFont.Line("14. 10. 2026", false)), BoardFont.lines(Blackboard.WELCOME, width, date = "14. 10. 2026"))
        // a caron over a small letter in its two rows over it; over a capital above it
        val rows = HashSet<Int>()
        BoardFont.write("š") { _, _, _, row, mark -> if (mark) rows.add(row) }
        assertEquals(setOf(0, 1), rows)
        rows.clear()
        BoardFont.write("Š") { _, _, _, row, mark -> if (mark) rows.add(row) }
        assertEquals(setOf(-2, -1), rows)
    }

    private fun frame(board: Blackboard?, hour: Float = 10.5f) = SceneFrame(
        time = 3.0, hour = hour, month = 10, objects = SceneArt.objects.getValue("school").toSet(), world = SceneWorld.ALL.copy(board = board), date = today,
    )

    private fun render(f: SceneFrame, k: Int, p: ScenePainter = ScenePainters.create("school")): Pair<PixelCanvas, List<SceneHit>> {
        val c = PixelCanvas(240 * k, 160 * k)
        return c to p.render(c, f, Lens(k, 0, 0, 240, 160))
    }

    /** The chalk on the board: the board's own bright pixels (the wall round it left out), the ledge's dust too (its lower fifth). */
    private fun chalk(p: ScenePainter, c: PixelCanvas, board: SceneHit, k: Int): Int {
        var n = 0
        for (y in board.top * k until board.top * k + (board.bottom - board.top) * k * 4 / 5) for (x in board.left * k until board.right * k) {
            if (Col.lum(c.pixels[y * c.width + x]) > 0.75f && p.targetAt(c, x, y, 0) == SceneTarget.Thing("blackboard")) n++
        }
        return n
    }

    @Test fun `the board's chalk is the lesson's, at every detail`() {
        val biti = Blackboard.of(state("biti" to UnitProgress(started = "2026-10-13", introDone = true)), course)!!
        val spol = Blackboard.of(state("spol" to UnitProgress(started = "2026-10-13")), course)!!
        for (k in 1..3) {
            val (plain, hits) = render(frame(null), k)
            val board = hits.first { it.target == SceneTarget.Thing("blackboard") }
            val pl = ScenePainters.create("school"); val pw = ScenePainters.create("school")
            val (lesson, _) = render(frame(biti), k, pl)
            val (other, _) = render(frame(spol), k)
            val (welcome, _) = render(frame(Blackboard.WELCOME), k, pw)
            val n = chalk(pl, lesson, board, k)
            // a pixel of chalk for each of the letters' at least (on the scene's canvas), closer up their strokes
            val letters = BoardFont.lines(biti, width).sumOf { l -> var p = 0; BoardFont.write(l.text) { _, _, _, _, _ -> p++ }; p }
            assertTrue("detail $k: $n px of chalk for the lesson's $letters", n >= letters * k * 7 / 10)
            assertNotEquals("detail $k: the lesson isn't the greeting", plain.pixels.toList(), lesson.pixels.toList())
            assertNotEquals("detail $k: another unit, another board", lesson.pixels.toList(), other.pixels.toList())
            assertNotEquals("detail $k: before the first unit, a welcome", lesson.pixels.toList(), welcome.pixels.toList())
            // the welcome writes less than a lesson; nothing but the board changes
            val w = chalk(pw, welcome, board, k)
            assertTrue("detail $k: the welcome's chalk, $w px", w > 0)
            for (y in 0 until lesson.height) for (x in 0 until lesson.width) {
                if (lesson.pixels[y * lesson.width + x] == plain.pixels[y * plain.width + x]) continue
                assertTrue("detail $k: ($x, $y) changed outside the board", x / k in board.left - 1..board.right && y / k in board.top - 1..board.bottom)
            }
        }
    }

    @Test fun `render the board`() {
        val boards = listOf(
            "welcome" to Blackboard.WELCOME,
            "biti" to Blackboard.of(state("biti" to UnitProgress(started = "2026-10-13", introDone = true)), course)!!,
        ) + listOf("spol", "sedanjik", "mestnik", "stevila", "pretekli-cas", "dvojina").map { id -> id to Blackboard.of(state(id to UnitProgress(started = "2026-10-13")), course)!! }
        // the board at each detail as big (6 / k picture px a px), four boards a sheet
        val (_, hits) = render(frame(null), 1)
        val b = hits.first { it.target == SceneTarget.Thing("blackboard") }
        val cw = (b.right - b.left) * 6; val ch = (b.bottom - b.top) * 6; val label = 14
        for ((sheet, part) in boards.chunked(4).withIndex()) {
            val img = BufferedImage(cw * 3, (ch + label) * part.size, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            for ((row, nb) in part.withIndex()) for (k in 1..3) {
                val (c, _) = render(frame(nb.second), k)
                for (y in 0 until ch) for (x in 0 until cw) {
                    val sx = b.left * k + x * k / 6; val sy = b.top * k + y * k / 6
                    img.setRGB((k - 1) * cw + x, row * (ch + label) + label + y, c.pixels[sy * c.width + sx])
                }
                g.color = Color.WHITE
                g.drawString("${nb.first}, detail $k", (k - 1) * cw + 4, row * (ch + label) + 11)
            }
            g.dispose()
            ImageIO.write(img, "png", File(dir, "board-units-$sheet.png"))
        }
        // the whole school with Jan's lesson, by day and at dusk
        for ((name, hour) in listOf("day" to 10.5f, "dusk" to 19.5f)) {
            val (c, _) = render(frame(boards[1].second, hour), 3)
            val out = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until c.height) for (x in 0 until c.width) out.setRGB(x, y, c.pixels[y * c.width + x])
            ImageIO.write(out, "png", File(dir, "board-school-$name.png"))
        }
    }
}
