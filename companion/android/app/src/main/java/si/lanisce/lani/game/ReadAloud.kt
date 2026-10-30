package si.lanisce.lani.game

import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair

/**
 * What the founding screen ("Kje bo tvoja vas? · Where will your village be?") and the news sheet ("Medtem ko te ni bilo ·
 * While you were away") say on their 🔊, as the village's first words do ([GameIntroSpeech]): what the screen shows, the
 * language learned in the narrator's voice (the culture's: the node's clip in the village's language), the learner's base
 * by the phone's voice of it ([si.lanisce.lani.data.HintVoice]). A child who doesn't read yet hears the screen. Pure.
 */
object ReadAloud {
    /** The news sheet's lines: the newest, as many as it shows. */
    const val NEWS_SHOWN = 8

    /**
     * The founding's pieces as the screen shows them: its title in the language learned, then in the base what to do
     * ("Choose where it will stand …") and that it is chosen once.
     */
    fun founding(pair: LangPair = L10n.pair): List<HintPiece> = distinct(
        listOf(
            HintPiece(L10n.text(pair.target, "founding.title", target = pair.target), true),
            HintPiece(L10n.text(pair.base, "founding.about", target = pair.target), false),
            HintPiece(L10n.text(pair.base, "founding.once", target = pair.target), false),
        ),
    )

    /**
     * The news sheet's pieces: its heading in both languages, then each of the newest [NEWS_SHOWN] lines of [texts] (the
     * chronicle's "target · base", [line]) in the order shown.
     */
    fun news(texts: List<String>, pair: LangPair = L10n.pair): List<HintPiece> = distinct(
        listOf(
            HintPiece(L10n.text(pair.target, "villageSheets.whileWereAway", target = pair.target), true),
            HintPiece(L10n.text(pair.base, "villageSheets.whileWereAway", target = pair.target), false),
        ) + texts.takeLast(NEWS_SHOWN).flatMap(::line),
    )

    /**
     * A chronicle line as said: "Volkovi so prišli · The wolves came" (the app's labels: [si.lanisce.lani.l10n.bi]) is the
     * language learned, then the base; a line in one language only is the language learned's. A label whose halves both
     * hold a " · " (a name with one) is cut at its middle one; an odd count leaves the extra to the base. Its emoji and
     * symbols aren't said.
     */
    fun line(text: String): List<HintPiece> {
        val parts = text.split(SEPARATOR).map(::spoken).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return emptyList()
        if (parts.size == 1) return listOf(HintPiece(parts[0], true))
        val half = if (parts.size % 2 == 0) parts.size / 2 else 1
        val target = parts.take(half).joinToString(", ")
        val base = parts.drop(half).joinToString(", ")
        return if (base == target) listOf(HintPiece(target, true)) else listOf(HintPiece(target, true), HintPiece(base, false))
    }

    /** [s] without what no voice should say: emoji, pictographs, their joiners and selectors; the spaces evened. */
    fun spoken(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            if (!silent(cp)) appendCodePoint(cp)
        }
    }.replace(Regex("\\s+"), " ").trim().trim(':', '·', ',', '-', '–').trim()

    private fun silent(cp: Int): Boolean =
        cp == 0x200D || cp in 0xFE00..0xFE0F || cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF ||
            cp in 0x1F1E6..0x1F1FF || cp == 0x20E3 || cp in 0xE0020..0xE007F ||
            (cp != DEGREE && Character.getType(cp) == Character.OTHER_SYMBOL.toInt())

    /** "5 °C" keeps its degree: a symbol that is read. */
    private const val DEGREE = 0x00B0

    /** A piece that says what the one before it said (a label the same in both languages: a name) is said once. */
    private fun distinct(pieces: List<HintPiece>): List<HintPiece> {
        val kept = pieces.filter { it.text.isNotBlank() }
        return kept.filterIndexed { i, p -> i == 0 || kept[i - 1].text != p.text }
    }

    private const val SEPARATOR = " · "
}
