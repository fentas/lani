package si.lanisce.lani.data

import kotlin.random.Random

/**
 * The order a choice's or a multi's options are shown in: a display order, never a change to the exercise. The writers
 * nearly always put the right option first, so the file's order lets a learner pass by tapping the top one.
 * [Exercise.Choice.answer] and [Exercise.Choice.almost] keep meaning the file's indices: what is graded, stored and told
 * to the bridge and the tutor is as it was (docs/plans/05-built/choices-order.md).
 *
 * The same through the day, as a forms card's options: it holds while the learner works (a look away, leaving a sheet and
 * coming back, the process killed) with nothing kept anywhere, and tomorrow is another order, so "the third one" isn't
 * learned for life. Random doesn't favour a place: the right one is first as often as any other (a shuffle that avoided
 * the top would teach "never the first").
 */
object ChoiceOrder {
    /** Where each option shown, from the top, is in the file: the [n] options of [question] on [day] ([java.time.LocalDate.toEpochDay]). */
    fun of(n: Int, question: String, day: Long): List<Int> =
        (0 until n).shuffled(Random(question.hashCode() * 31L + day))

    /**
     * What tells a question from another: its [prompt] and its [right] answer, not its wrong ones: the learner's own trap
     * (Adaptive.choice) may take the place of one between two visits, and the rows stay where they were.
     */
    fun question(prompt: String, right: String?): String = "$prompt\n$right"
}
