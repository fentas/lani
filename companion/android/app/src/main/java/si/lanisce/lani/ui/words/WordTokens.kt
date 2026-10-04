package si.lanisce.lani.ui.words

/** A word of a line: [text], found at [start] until [end] (exclusive) in it. */
data class WordToken(val text: String, val start: Int, val end: Int)

/** The words of a line that can be tapped and looked up. Pure, so it's tested without a screen. */
object WordTokens {
    /** Between two letters these join them into one word: "l'acqua", "e-pošta". */
    private const val JOINERS = "'’‐-"

    /**
     * The words of [line]: runs of letters of any script (č, š, ž, ć, đ, and a letter's combining accents too),
     * with an apostrophe or hyphen between two letters kept inside. Digits, punctuation, dashes and emoji are
     * not words.
     */
    fun of(line: String): List<WordToken> {
        val out = ArrayList<WordToken>()
        var i = 0
        while (i < line.length) {
            if (!letterAt(line, i)) {
                i += Character.charCount(line.codePointAt(i))
                continue
            }
            val start = i
            while (i < line.length) {
                val cp = line.codePointAt(i)
                i += when {
                    letterAt(line, i) || isMark(cp) -> Character.charCount(cp)
                    line[i] in JOINERS && i + 1 < line.length && letterAt(line, i + 1) -> 1
                    else -> break
                }
            }
            out += WordToken(line.substring(start, i), start, i)
        }
        return out
    }

    /**
     * The word at character [offset] (as a text layout reports a tap): the one it falls in, else the one it ends
     * right after (a tap on the right half of a word's last letter).
     */
    fun at(tokens: List<WordToken>, offset: Int): WordToken? =
        tokens.firstOrNull { offset >= it.start && offset < it.end } ?: tokens.firstOrNull { it.end == offset }

    private fun letterAt(s: String, i: Int): Boolean = Character.isLetter(s.codePointAt(i))

    private fun isMark(cp: Int): Boolean = when (Character.getType(cp)) {
        Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt() -> true
        else -> false
    }
}
