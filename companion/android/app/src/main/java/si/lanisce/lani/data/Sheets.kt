package si.lanisce.lani.data

import si.lanisce.lani.data.Grading.Verdict
import kotlin.random.Random

/**
 * One item of a worksheet exercise (companion/COURSE.md, "The school's exercise types"): a gap, a cell to fill, a line of
 * a transform list, a word to sort. [accept]: what is right (a sorted word's: its column's label); [grammar]: its rule
 * (its own, else the exercise's); [frame]: the sentence it stands in with `___` where the answer goes ("Babica ___ v
 * kuhinji."), what a slip on its rule keeps; [lemma] and [form]: the word's form it practises (the forms book's key).
 */
data class SheetItem(
    val accept: List<String>,
    val grammar: String?,
    val hint: String? = null,
    val explain: String? = null,
    val frame: String = Sheets.BLANK,
    val lemma: String? = null,
    val form: String? = null,
) {
    /** [frame] with [answer] in its gap. */
    fun said(answer: String): String = frame.replaceFirst(Sheets.BLANK, answer.trim())
}

/**
 * An item checked: its [verdict], what was given ([answer]) and what is right ([expected]), the grading's hint and marks;
 * [hinted]: its 💡 was opened (a right answer then pays like an almost, [Hints.paid]); [lookedUp]: it was answered after
 * the cheat sheet was opened over the sheet (right with help: it pays in full, the tutor hears of it).
 */
data class ItemMark(
    val verdict: Verdict,
    val answer: String,
    val expected: String,
    val hint: Grading.Hint = Grading.Hint.NONE,
    val marks: List<IntRange> = emptyList(),
    val hinted: Boolean = false,
    val lookedUp: Boolean = false,
) {
    /** What it pays as: a right one after its hint as an almost. */
    val paid: Verdict get() = Hints.paid(verdict, if (hinted) 1 else 0)
}

/**
 * A worksheet exercise's answer on one rule (the grammar book's page): [verdict] right only when every item of the rule
 * was, wrong when one was wrong; [said] a sentence said right (the page's "Tvoji stavki"), the first slip's [answer] and
 * [correct] (its "Tvoje napake").
 */
data class RuleAnswer(val rule: String, val verdict: Verdict, val said: String?, val answer: String, val correct: String?)

/**
 * The school's worksheet types, pure (companion/COURSE.md): their items, how each is graded (the app's [Grading]: č, š, ž
 * forgotten is almost, a slip in the stem forgiven, never the ending), how a sheet adds up, and what it counts on the
 * grammar book's rules.
 */
object Sheets {
    /** Where an answer goes in a [SheetItem.frame]. */
    const val BLANK = "___"

    /** A gap in a gap text: three underscores or more. */
    val GAP = Regex("_{3,}")

    /** An exercise of the school's own four types. */
    fun isSheet(ex: Exercise): Boolean = ex is Exercise.GapText || ex is Exercise.Table || ex is Exercise.Transform || ex is Exercise.Sort

    /** [text] cut at its gaps: one part more than it has gaps. */
    fun parts(text: String): List<String> = text.split(GAP)

    /** The gap text with [answers] in its gaps (a gap with none keeps its `___`). */
    fun filled(ex: Exercise.GapText, answers: List<String?>): String {
        val p = parts(ex.text)
        return buildString {
            p.forEachIndexed { i, s ->
                append(s)
                if (i < p.size - 1) append(answers.getOrNull(i)?.takeIf { it.isNotBlank() } ?: BLANK)
            }
        }
    }

    /** The sentence of [text] around [at] (a character index): from the end of the one before to its own end. */
    internal fun sentenceAt(text: String, at: Int): String {
        val ends = Regex("""[.!?…]+["»“”]?(\s+|$)""").findAll(text).map { it.range.last + 1 }.toList()
        val start = ends.lastOrNull { it <= at } ?: 0
        val end = ends.firstOrNull { it > at } ?: text.length
        return text.substring(start, end).trim()
    }

    /** The items of [ex] in order; none for an exercise that isn't one of the four. */
    fun items(ex: Exercise): List<SheetItem> = when (ex) {
        is Exercise.GapText -> {
            val p = parts(ex.text)
            ex.gaps.mapIndexed { i, g ->
                // the sentence of the gap, the other gaps filled with their answers
                val others = ex.gaps.mapIndexed { j, o -> if (j == i) null else o.accept.firstOrNull() }
                val before = buildString { for (k in 0..i) { append(p.getOrElse(k) { "" }); if (k < i) append(others[k] ?: BLANK) } }
                val whole = filled(ex, others.mapIndexed { j, a -> if (j == i) MARK else a })
                val frame = sentenceAt(whole, before.length).replace(MARK, BLANK)
                SheetItem(g.accept, g.grammar ?: ex.grammar, g.hint, g.explain, frame)
            }
        }
        is Exercise.Table -> ex.rows.flatMap { row ->
            row.cells.mapIndexedNotNull { c, cell ->
                if (!cell.toFill) return@mapIndexedNotNull null
                val column = ex.columns.getOrNull(c)?.takeIf { ex.columns.size > 1 }
                val frame = "${row.label}: $BLANK" + (column?.let { " ($it)" } ?: "")
                SheetItem(cell.accept, cell.grammar ?: ex.grammar, cell.hint, cell.explain, frame, ex.lemma, cell.form)
            }
        }
        is Exercise.Transform -> ex.items.map { SheetItem(it.accept, it.grammar ?: ex.grammar, it.hint, it.explain, BLANK, it.lemma, it.form) }
        is Exercise.Sort -> ex.columns.flatMap { col -> col.words.map { w -> SheetItem(listOf(col.label), ex.grammar, frame = "$w → $BLANK") } }
        else -> emptyList()
    }

    private const val MARK = "\u0000"

    /** The words of a sort, shuffled the same way each time it is shown ([seed]). */
    fun sortWords(ex: Exercise.Sort, seed: Long = ex.columns.hashCode().toLong()): List<String> =
        ex.columns.flatMap { it.words }.shuffled(Random(seed))

    /** A gap text's word bank: each gap's first answer and the distractors, shuffled the same way each time; none without [Exercise.GapText.bank]. */
    fun bank(ex: Exercise.GapText, seed: Long = ex.text.hashCode().toLong()): List<String> =
        if (!ex.bank) emptyList() else (ex.gaps.mapNotNull { it.accept.firstOrNull() } + ex.distractors).shuffled(Random(seed))

    /** A typed item checked as the app checks a cloze ([Grading.check]). */
    fun check(item: SheetItem, answer: String): ItemMark {
        val r = Grading.check(answer, item.accept)
        return ItemMark(r.verdict, answer.trim(), r.expected, r.hint, r.marks)
    }

    /** A sorted word: in its column ([placed], an index into [columns]) or not; one not placed is wrong. */
    fun sorted(ex: Exercise.Sort, word: String, placed: Int?): ItemMark {
        val right = ex.columns.indexOfFirst { word in it.words }
        val label = ex.columns.getOrNull(right)?.label.orEmpty()
        return ItemMark(if (placed == right) Verdict.CORRECT else Verdict.WRONG, placed?.let { ex.columns.getOrNull(it)?.label }.orEmpty(), label)
    }

    /** How a sheet went as a whole: right when every item was, almost from [ALMOST_SHARE] of its score, else not quite. */
    fun verdict(items: List<Verdict>): Verdict = when {
        items.isEmpty() -> Verdict.WRONG
        items.all { it == Verdict.CORRECT } -> Verdict.CORRECT
        score(items) >= ALMOST_SHARE * items.size -> Verdict.ALMOST
        else -> Verdict.WRONG
    }

    /** A sheet that is almost right: this share of its items' score. */
    const val ALMOST_SHARE = 0.7

    /** Right items, an almost one counting half. */
    fun score(items: List<Verdict>): Double = items.count { it == Verdict.CORRECT } + 0.5 * items.count { it == Verdict.ALMOST }

    /** Whether [items] reach [pass] percent ([score] of all of them). */
    fun passed(items: List<Verdict>, pass: Int): Boolean = items.isNotEmpty() && score(items) * 100 >= pass * items.size - 1e-9

    /** "8/10" (an almost counting half: "8½/10"). */
    fun scoreText(items: List<Verdict>): String {
        val s = score(items)
        val whole = s.toInt()
        return (if (s - whole >= 0.5) "$whole½" else "$whole") + "/${items.size}"
    }

    /**
     * What a checked sheet counts on the grammar book (companion/GAME.md, "The school"): one answer on each rule its items
     * name, in the order they come, as one exercise would ([si.lanisce.lani.game.GrammarBook.answered]): right when every
     * item of the rule was right, wrong when one was wrong (its first slip kept), else almost.
     */
    fun ruleAnswers(items: List<SheetItem>, marks: List<ItemMark>, sentences: Boolean): List<RuleAnswer> {
        val byRule = LinkedHashMap<String, MutableList<Int>>()
        items.forEachIndexed { i, it -> it.grammar?.let { g -> byRule.getOrPut(g) { mutableListOf() } += i } }
        return byRule.map { (rule, idx) ->
            val ms = idx.mapNotNull { marks.getOrNull(it) }
            val verdict = when {
                ms.isNotEmpty() && ms.all { it.verdict == Verdict.CORRECT } -> Verdict.CORRECT
                ms.isEmpty() || ms.any { it.verdict == Verdict.WRONG } -> Verdict.WRONG
                else -> Verdict.ALMOST
            }
            val slip = idx.firstOrNull { marks.getOrNull(it)?.verdict == Verdict.WRONG }
            val said = if (!sentences) null else idx.firstOrNull { marks.getOrNull(it)?.verdict == Verdict.CORRECT }?.let { items[it].said(marks[it].answer) }
            RuleAnswer(
                rule, verdict, said,
                slip?.let { items[it].said(marks[it].answer.ifBlank { "…" }) }.orEmpty(),
                slip?.let { items[it].said(marks[it].expected) },
            )
        }
    }

    /** Whether a sheet's right items are sentences worth keeping as the learner's own (a gap text's, a transform list's). */
    fun sentences(ex: Exercise): Boolean = ex is Exercise.GapText || ex is Exercise.Transform

    /** The forms a sheet practised ([SheetItem.lemma] and [SheetItem.form]) and whether each was right (an almost is). */
    fun forms(items: List<SheetItem>, marks: List<ItemMark>): List<Triple<String, String, Boolean>> = items.mapIndexedNotNull { i, it ->
        val lemma = it.lemma?.takeIf { l -> l.isNotBlank() } ?: return@mapIndexedNotNull null
        val form = it.form ?: return@mapIndexedNotNull null
        val m = marks.getOrNull(i) ?: return@mapIndexedNotNull null
        Triple(lemma, form, m.verdict != Verdict.WRONG)
    }

    /** The words of a sheet's texts, for the run's lookup: its text, its cells and lines, its words to sort. */
    fun texts(ex: Exercise): List<String> = when (ex) {
        is Exercise.GapText -> listOf(filled(ex, ex.gaps.map { null }))
        is Exercise.Table -> ex.rows.map { r -> (listOf(r.label) + r.cells.mapNotNull { it.given }).joinToString(" ") }
        is Exercise.Transform -> ex.items.map { it.from }
        is Exercise.Sort -> listOf(ex.columns.flatMap { it.words }.joinToString(", "))
        else -> emptyList()
    }
}
