package si.lanisce.lani.ui.school

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import si.lanisce.lani.data.IntroSpeech.Line
import si.lanisce.lani.data.IntroSpeech.Mark
import kotlin.math.min

/**
 * What the teacher points at with her pointer (the kazalka) on the school's stage while a step is read, in step with what
 * is read and marked ([si.lanisce.lani.data.IntroVoice.now]), and where its tip goes (COURSE.md, "Read aloud"). Pure.
 *
 * - a Slovene form of the explanation: the table's rows it says ("jaz sem": the row jaz · sem; "smo, ste, so": the three
 *   rows of those forms), else its words in the explanation;
 * - the translation in brackets after it ("(I am),"): where she pointed for the form;
 * - the rest of the explanation: its words being read; the explanation's one clip: the explanation;
 * - an example: its card; the quick question: its card, for a moment ([QUESTION_POINT_MS]), then she turns to the learner
 *   and asks; the answer's sentence: that sentence.
 *
 * Nothing with the system's animations off, while the keyboard is up, or while nothing is read.
 */
object IntroPointer {
    /** What the pointer is on. */
    sealed interface Target {
        /** Rows of the table so far, by their index among the rows shown. */
        data class Rows(val rows: Set<Int>) : Target

        /** The words of the explanation being read: its source line and segments ([Mark.Part]). */
        data class Run(val line: Int, val segments: IntRange) : Target

        /** The explanation as a whole (its one clip). */
        data object Explanation : Target

        data class Example(val index: Int) : Target

        data object Question : Target

        data object Answer : Target
    }

    /** How long she points at the quick question as she starts asking it, before she turns to the learner. */
    const val QUESTION_POINT_MS = 1_200L

    /**
     * What [mark] has her point at: [line] is what is read ([Mark.Part]'s run, its text and language), [rows] the words of
     * the table's rows shown (each row's cells of the language learned, lower case), [before] what she pointed at before.
     */
    fun target(mark: Mark?, line: Line?, rows: List<List<Set<String>>>, before: Target?): Target? = when (mark) {
        null -> null
        Mark.Whole -> Target.Explanation
        is Mark.Part -> {
            val run = Target.Run(mark.line, mark.segments)
            when {
                line == null -> run
                line.slovene -> rowsOf(line.text, rows).takeIf { it.isNotEmpty() }?.let(Target::Rows) ?: run
                before is Target.Rows && translation(line.text) -> before
                else -> run
            }
        }
        is Mark.Example -> Target.Example(mark.index)
        Mark.Question -> Target.Question
        Mark.Answer -> Target.Answer
    }

    /**
     * The rows [text] says: those whose words it has all ("jaz sem": jaz · sem); else those whose form (the row's last
     * cell of the language learned) it has ("smo, ste, so"). Empty: none.
     */
    fun rowsOf(text: String, rows: List<List<Set<String>>>): Set<Int> {
        val said = words(text)
        if (said.isEmpty()) return emptySet()
        val whole = rows.indices.filter { r -> val w = rows[r].flatten(); w.isNotEmpty() && said.containsAll(w) }
        if (whole.isNotEmpty()) return whole.toSet()
        return rows.indices.filter { r -> rows[r].lastOrNull { it.isNotEmpty() }?.let { said.containsAll(it) } == true }.toSet()
    }

    /** A cell's words (or a run's), lower case. */
    fun words(text: String): Set<String> = WORD.findAll(text.lowercase()).map { it.value }.toSet()

    /** Only a translation in brackets ("(I am),"): what follows a form, read in the base. */
    fun translation(text: String): Boolean = BRACKETS.matches(text.trim())

    /** She points now: something is read, the animations are on, the keyboard isn't up, and she isn't asking the learner ([asking]). */
    fun points(reading: Boolean, reduced: Boolean, keyboard: Boolean, asking: Boolean): Boolean = reading && !reduced && !keyboard && !asking

    /**
     * Where the tip goes on a target as it is seen ([seen]: its bounds clipped to what the screen shows): a line of the
     * explanation in its middle; a card or a row a little in from its start, at mid-height. Null when too little of it
     * shows ([minSeen] px tall): the pointer fades.
     */
    fun tip(target: Target, seen: Rect?, minSeen: Float, inset: Float): Offset? {
        if (seen == null || seen.isEmpty || seen.height < minSeen) return null
        return when (target) {
            is Target.Run, Target.Answer -> seen.center
            else -> Offset(seen.left + min(seen.width / 4, inset), seen.center.y)
        }
    }

    /** The bounds a target is seen at: its own ([of]), a set of rows' all together. */
    fun bounds(target: Target, of: (Any) -> Rect?): Rect? = when (target) {
        is Target.Rows -> target.rows.mapNotNull { of(Row(it)) }.filter { !it.isEmpty }.reduceOrNull { a, b -> a.union(b) }
        else -> of(target)
    }

    /** A table row's key among the targets' bounds ([bounds]). */
    data class Row(val index: Int)

    /** Where the pointer's tip is shown while it moves from [from] to [to] ([t]: 0 to 1, eased). */
    fun between(from: Offset?, to: Offset, t: Float): Offset = if (from == null) to else Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)

    private fun Rect.union(o: Rect) = Rect(minOf(left, o.left), minOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))

    private val WORD = Regex("""\p{L}+""")
    private val BRACKETS = Regex("""^\([^()]*\)[\s.,;:!?…]*$""")
}
