package si.lanisce.lani.ui.words

import java.util.Locale
import si.lanisce.lani.data.LemmaForms
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.WordFormsWire

/**
 * The words the learner has met: every word of their cards (a phrase's words, a pack's words once learned, a word added
 * from a dialog), and every form of the lemmas among them that the phone has the forms of (GET /forms, cached: "mizo" is
 * "miza"'s). An exercise marks the others, the words new to them ([unknown]). Pure.
 */
class KnownWords(private val words: Set<String>) {
    fun knows(word: String): Boolean = norm(word) in words

    /**
     * Of [tokens] (words of [text]), those the learner hasn't met: not among their words, and not a name (capitalized
     * inside a sentence: "Luka", "Ljubljani"; at its start a word is capitalized anyway).
     */
    fun unknown(text: String, tokens: List<WordToken>): List<WordToken> = tokens.filter { t -> !knows(t.text) && !name(text, t) }

    val size: Int get() = words.size

    companion object {
        /** A word as the set keeps it: lower case, one apostrophe. */
        fun norm(word: String): String = word.lowercase(Locale.ROOT).replace('’', '\'')

        /** The words of [fronts] (the cards' Slovene) and every form of [lemmas] (theirs, as GET /forms gave them). */
        fun of(fronts: List<String>, lemmas: List<LemmaForms> = emptyList()): KnownWords {
            val set = HashSet<String>()
            for (f in fronts) WordTokens.of(f).forEach { set += norm(it.text) }
            for (l in lemmas) {
                WordTokens.of(l.lemma).forEach { set += norm(it.text) }
                for (s in l.slots) for (form in s.forms + s.also) WordTokens.of(form).forEach { set += norm(it.text) }
            }
            return KnownWords(set)
        }

        /** A deck's words: its cards ([pool]) and the forms of its one-word cards the phone has ([entriesOf], by lemma). */
        fun ofDeck(pool: List<ReviewCard>, entriesOf: (String) -> List<LemmaForms>?): KnownWords =
            of(pool.map { it.front }, pool.mapNotNull(WordFormsWire::lemmaOf).distinctBy(WordFormsWire::key).flatMap { entriesOf(it).orEmpty() })

        /**
         * Whether [t] reads as a name: capitalized right after a word or a comma; after a full stop, a colon, a quote, a
         * dash, a picture or nothing it starts a sentence.
         */
        private fun name(text: String, t: WordToken): Boolean {
            if (!t.text.first().isUpperCase()) return false
            val c = text.substring(0, t.start).trimEnd().lastOrNull() ?: return false
            return c.isLetterOrDigit() || c == ','
        }
    }
}
