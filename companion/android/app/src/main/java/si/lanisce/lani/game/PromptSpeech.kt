package si.lanisce.lani.game

import si.lanisce.lani.data.Exercise

/**
 * What a child's placement test says as an exercise comes up (docs/plans/05-built/cleanup-code.md, "Placement"): a child who
 * doesn't read yet hears what is asked, by the hint's voices ([HintPiece]). Only what the screen asks: its instruction and
 * its question, a gap a pause, never an option and never the answer; each option has its own 🔊. [isTarget]: a text in the
 * language learned. Pure.
 */
object PromptSpeech {
    private val GAP = Regex("_{2,}")
    private val MARKS = Regex("[*`]")

    /** [text] as it is said: without its Markdown marks, a gap a pause; null when nothing is left to say. */
    private fun said(text: String?): String? =
        text?.replace(MARKS, "")?.replace(GAP, "…")?.trim()?.takeIf { t -> t.any { it.isLetter() } }

    /** The pieces of [e]: its instruction, then its question; none for an exercise that isn't a child's. */
    fun of(e: Exercise, isTarget: (String) -> Boolean): List<HintPiece> {
        val parts = when (e) {
            is Exercise.Choice -> listOf(e.instruction, e.prompt)
            is Exercise.Multi -> listOf(e.instruction, e.prompt)
            is Exercise.Reorder -> listOf(e.instruction, e.prompt)
            is Exercise.Sort -> listOf(e.instruction, e.prompt)
            is Exercise.GapText -> listOf(e.instruction)
            else -> emptyList()
        }
        return parts.mapNotNull(::said).distinct().map { HintPiece(it, isTarget(it)) }
    }
}
