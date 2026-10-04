package si.lanisce.lani.game

import kotlin.math.roundToInt

/**
 * A text a run's questions are about ([Challenge.text]: the day's letter), read first (companion/GAME.md, "Read first,
 * then answer"): shown with its voice until the learner starts, then hidden while the questions are asked, "👁 Pokaži
 * besedilo · Show the text" bringing it back. [title] "target · base"; [paragraphs] its sentences in the target language,
 * a paragraph's together (a letter's lines), each with its 🔊.
 */
data class ReadFirst(val title: String, val paragraphs: List<List<String>>) {
    /** The whole text, a paragraph a line: what 🔊 "Poslušaj · Listen" reads, and what the tutor is told it is. */
    val text: String get() = paragraphs.joinToString("\n") { it.joinToString(" ") }
}

/**
 * Looking back at a text while its questions are asked (companion/GAME.md, "Read first, then answer"): a look costs a
 * little of what the run pays, never an answer (a pass stays a pass), and less the lower the learner's level. At A1 the
 * first two looks are free and each one after them costs 5 %; from A2 the first is free and each one after it costs a
 * little more than the one before; together never more than [MAX_COST]. The level is the learner's in the village's
 * language (the level the reading corner and the storyteller go by): a text's questions test the whole text, not one
 * rule of the grammar book, so a rule's mastery doesn't fit. Pure.
 */
object Peeks {
    /** The most looking ever takes off, in percent: a little of the pay, never most of it. */
    const val MAX_COST = 30

    private val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

    /** [level]'s rank: A1 0 … C2 5; anything else (no level yet) as A1, the gentlest. */
    private fun rank(level: String): Int = LEVELS.indexOf(level.trim().uppercase()).coerceAtLeast(0)

    /** How many looks are free at [level]: two at A1, one from A2. */
    fun free(level: String): Int = if (rank(level) == 0) 2 else 1

    /**
     * The first paid look's cost at [level] in percent, and how much more each look after it costs than the one before:
     * A1 5 % each; A2 5, 10, 15 …; B1 10, 15, 20 …; B2 and up 10, 20, 30 ….
     */
    private fun steps(level: String): Pair<Int, Int> = when (rank(level)) {
        0 -> 5 to 0
        1 -> 5 to 5
        2 -> 10 to 5
        else -> 10 to 10
    }

    /** What [looks] at the text while its questions were open take off the run's pay at [level], in percent (0 to [MAX_COST]). */
    fun cost(level: String, looks: Int): Int {
        val paid = (looks - free(level)).coerceAtLeast(0)
        val (first, more) = steps(level)
        return (0 until paid).sumOf { first + more * it }.coerceAtMost(MAX_COST)
    }

    /** What is left of [pay] after [cost] percent is taken off: each resource rounded, at least 1 of any it had. */
    fun cut(pay: Map<Res, Int>, cost: Int): Map<Res, Int> {
        if (cost <= 0) return pay
        val share = (100 - cost.coerceAtMost(100)) / 100f
        return pay.mapValues { (_, n) -> if (n <= 0) n else (n * share).roundToInt().coerceAtLeast(1) }
    }

    /** [pay] after [looks] at the text at [level] ([cost], then [cut]). */
    fun pay(pay: Map<Res, Int>, level: String, looks: Int): Map<Res, Int> = cut(pay, cost(level, looks))
}
