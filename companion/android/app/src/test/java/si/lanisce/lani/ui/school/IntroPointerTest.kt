package si.lanisce.lani.ui.school

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.IntroSpeech
import si.lanisce.lani.data.IntroSpeech.Line
import si.lanisce.lani.data.IntroSpeech.Mark
import si.lanisce.lani.data.Spoken
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.render.scene.PortraitsPainter
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.StagePose
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.ui.school.IntroPointer.Target

/** What the teacher points at while a step is read, and where her small pointing hand goes. */
class IntroPointerTest {
    /** biti's table after its first two steps: who · to be. */
    private val rows = listOf(listOf("jaz", "sem"), listOf("ti", "si"), listOf("on, ona, ono", "je"), listOf("mi", "smo"), listOf("vi", "ste"), listOf("oni, one, ona", "so"))
        .map { r -> r.map(IntroPointer::words) }

    private val biti = "**Biti** (to be) has a short form for each person, and Slovene never leaves it out. One person first: " +
        "**jaz sem** (I am), **ti si** (you are), **on, ona, ono je** (he, she, it is). The pronoun is usually dropped: the form already says who."
    private val known = setOf("biti", "sem", "si", "je", "jaz", "ti", "on", "ona", "ono")
    private val slovene: (String) -> Boolean = { Spoken.looks(it, Lang.SL, Lang.EN) || IntroSpeech.within(it, known) }

    @Test fun `the biti step, piece by piece, a form at its row, its translation there too, the rest at its words`() {
        val script = IntroSpeech.step("biti/0", biti, "en", listOf("Jaz sem Jan.", "Babica Micka je v kuhinji."), null, slovene)
        var before: Target? = null
        val seen = script.stitched.map { l -> IntroPointer.target(l.mark, l, rows, before).also { before = it } }
        val part = script.stitched.map { it.mark as Mark.Part }
        assertEquals(
            listOf(
                Target.Run(0, part[0].segments), // "Biti": no row says it, its word
                Target.Run(0, part[1].segments), // "(to be) has a short form …": the words read
                Target.Rows(setOf(0)), // "jaz sem"
                Target.Rows(setOf(0)), // "(I am),": still there
                Target.Rows(setOf(1)), // "ti si"
                Target.Rows(setOf(1)), // "(you are),"
                Target.Rows(setOf(2)), // "on, ona, ono je"
                Target.Run(0, part[7].segments), // "(he, she, it is). The pronoun …": more than a translation, its words
            ),
            seen,
        )
        assertEquals(Target.Example(1), IntroPointer.target(Mark.Example(1), null, rows, before))
        assertEquals(Target.Question, IntroPointer.target(Mark.Question, null, rows, before))
        assertEquals(Target.Answer, IntroPointer.target(Mark.Answer, null, rows, before))
        assertEquals(Target.Explanation, IntroPointer.target(Mark.Whole, null, rows, before))
        assertNull(IntroPointer.target(null, null, rows, before))
    }

    @Test fun `the forms alone point at their rows, a word no row has at nothing of the table`() {
        assertEquals(setOf(3, 4, 5), IntroPointer.rowsOf("smo, ste, so", rows))
        assertEquals(setOf(0, 1), IntroPointer.rowsOf("jaz sem, ti si", rows))
        assertEquals(setOf(2), IntroPointer.rowsOf("je", rows))
        assertEquals(emptySet<Int>(), IntroPointer.rowsOf("Biti", rows))
        assertEquals(emptySet<Int>(), IntroPointer.rowsOf("jaz sem", emptyList()))
        // no table: a form points at its words
        val m = Mark.Part(0, 2..2)
        assertEquals(Target.Run(0, 2..2), IntroPointer.target(m, Line("jaz sem", true, m), emptyList(), null))
        assertTrue(IntroPointer.translation("(I am),"))
        assertFalse(IntroPointer.translation("(he, she, it is). The pronoun is usually dropped."))
    }

    @Test fun `no hand with the animations off, the keyboard up, or nothing read, the highlight is the reading's own`() {
        assertTrue(IntroPointer.shows(reading = true, reduced = false, keyboard = false))
        assertFalse(IntroPointer.shows(reading = true, reduced = true, keyboard = false))
        assertFalse(IntroPointer.shows(reading = true, reduced = false, keyboard = true))
        assertFalse(IntroPointer.shows(reading = false, reduced = false, keyboard = false))
        // the rows marked as read don't depend on the hand: the target is the same with the animations off
        val m = Mark.Part(0, 2..2)
        assertEquals(Target.Rows(setOf(0)), IntroPointer.target(m, Line("jaz sem", true, m), rows, null))
    }

    @Test fun `the hand at the right end of what is read, astride its edge, never over its text, none off screen`() {
        // a row, an example: at its right end, mid-height
        val row = Rect(40f, 900f, 1040f, 980f)
        assertEquals(Offset(1040f, 940f), IntroPointer.handAt(Target.Rows(setOf(0)), row, null, minSeen = 40f, line = 60f))
        assertEquals(Offset(1040f, 1050f), IntroPointer.handAt(Target.Example(0), Rect(40f, 1000f, 1040f, 1100f), null, 40f, 60f))
        // the words of the explanation: at the explanation's right end, level with their first line
        val card = Rect(40f, 300f, 1040f, 700f)
        assertEquals(Target.Explanation, IntroPointer.holder(Target.Run(0, 2..2)))
        assertEquals(Offset(1040f, 430f), IntroPointer.handAt(Target.Run(0, 2..2), Rect(500f, 400f, 900f, 520f), card, 16f, 60f))
        // the answer's sentence: at the question's card's right end
        assertEquals(Target.Question, IntroPointer.holder(Target.Answer))
        assertEquals(Offset(1040f, 1530f), IntroPointer.handAt(Target.Answer, Rect(80f, 1500f, 800f, 1560f), Rect(40f, 1200f, 1040f, 1700f), 16f, 60f))
        // the quick question: level with its prompt, not the middle of its options
        assertEquals(Offset(1040f, 1320f), IntroPointer.handAt(Target.Question, Rect(40f, 1200f, 1040f, 1900f), null, 16f, 60f))
        assertNull(IntroPointer.holder(Target.Example(0)))
        // scrolled away: a sliver or nothing shows, the hand fades
        assertNull(IntroPointer.handAt(Target.Example(0), Rect(40f, 1800f, 1040f, 1810f), null, minSeen = 40f, line = 60f))
        assertNull(IntroPointer.handAt(Target.Example(0), null, null, 40f, 60f))
        assertNull(IntroPointer.handAt(Target.Example(0), Rect.Zero, null, 40f, 60f))
        // several rows: together
        val b = mapOf<Any, Rect>(IntroPointer.Row(3) to Rect(40f, 1000f, 1040f, 1080f), IntroPointer.Row(5) to Rect(40f, 1160f, 1040f, 1240f))
        assertEquals(Rect(40f, 1000f, 1040f, 1240f), IntroPointer.bounds(Target.Rows(setOf(3, 4, 5)), b::get))
        assertNull(IntroPointer.bounds(Target.Question, b::get))
    }

    @Test fun `the hand hops to the next target, lifted in the middle, appearing at once`() {
        val from = Offset(1040f, 900f)
        val to = Offset(1040f, 1100f)
        assertEquals(from, IntroPointer.hop(from, to, 0f, 20f))
        val mid = IntroPointer.hop(from, to, 0.5f, 20f)
        assertEquals(1040f, mid.x, 0.01f)
        assertEquals(1000f - 20f, mid.y, 0.01f)
        assertEquals(to, IntroPointer.hop(from, to, 1f, 20f))
        assertEquals(to, IntroPointer.hop(null, to, 0.1f, 20f))
    }

    @Test fun `she turns toward what she explains, the arm held out that way, shifted on her walk-in`() {
        val p = PortraitsPainter()
        val c = PixelCanvas(30, 30)
        // the target below and to the right of her: turned right, the hand right of her middle and low
        val right = p.renderStage(c, "teacher", Pose.TALK, 0.3, 12f, 0, StagePose(aim = 200f to 120f))
        assertFalse(right.flip)
        assertTrue("${right.x}", right.x > 15f)
        // to the left: turned left, the hand on the left
        val left = p.renderStage(c, "teacher", Pose.TALK, 0.3, 12f, 0, StagePose(aim = -50f to 40f))
        assertTrue(left.flip)
        assertTrue("${left.x}", left.x < 15f)
        // nothing to point at: facing the learner, the book in hand, the hand at her side
        val none = p.renderStage(c, "teacher", Pose.IDLE, 0.3, 12f, 0, StagePose())
        assertFalse(none.flip)
        // the walk-in: shifted off to the left, her hand with her
        val walking = p.renderStage(c, "teacher", Pose.IDLE, 0.3, 12f, 0, StagePose(shift = -20, walking = true))
        assertEquals(none.x - 20f, walking.x, 1.5f)
    }
}
