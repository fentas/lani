package si.lanisce.lani.game

import si.lanisce.lani.data.Exercise

/**
 * What a child's placement test says as an exercise comes up (docs/plans/05-built/cleanup-code.md, "Placement"): a child who
 * doesn't read yet hears what is asked, by the hint's voices ([HintPiece]). Only what the screen asks: its instruction and
 * its question, a gap a pause, never an option and never the answer; each option has its own 🔊. A line in both languages
 * ("C'è o ci sono? · Sortiere") is said each half in its own voice, as a chronicle's ([ReadAloud.line]). A sentence with a gap
 * is the language learned's unless it reads as the base: it has few of the words [isTarget] knows it by, and it is what the
 * child fills in. [isTarget]: a text in the language learned; [isBase]: one in the base. Pure.
 */
object PromptSpeech {
    private val GAP = Regex("_{2,}")
    private val MARKS = Regex("[*`]")
    private const val BOTH = " · "

    /** [text] as it is said: without its Markdown marks, a gap a pause; null when nothing is left to say. */
    private fun said(text: String?): String? =
        text?.replace(MARKS, "")?.replace(GAP, "…")?.trim()?.takeIf { t -> t.any { it.isLetter() } }

    /** The pieces of [e]: its instruction, then its question; none for an exercise that isn't a child's. */
    fun of(e: Exercise, isTarget: (String) -> Boolean, isBase: (String) -> Boolean = { false }): List<HintPiece> {
        val parts = when (e) {
            is Exercise.Choice -> listOf(e.instruction, e.prompt)
            is Exercise.Multi -> listOf(e.instruction, e.prompt)
            is Exercise.Reorder -> listOf(e.instruction, e.prompt)
            is Exercise.Sort -> listOf(e.instruction, e.prompt)
            is Exercise.GapText -> listOf(e.instruction)
            else -> emptyList()
        }
        return parts.flatMap { part -> pieces(part, isTarget, isBase) }.distinct()
    }

    /** [part] as said: a line in both languages by halves, else one piece. */
    private fun pieces(part: String?, isTarget: (String) -> Boolean, isBase: (String) -> Boolean): List<HintPiece> {
        val text = said(part) ?: return emptyList()
        if (BOTH in text) return ReadAloud.line(text)
        return listOf(HintPiece(text, isTarget(text) || (GAP.containsMatchIn(part.orEmpty()) && !isBase(text))))
    }
}
