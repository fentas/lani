package si.lanisce.lani.game

import si.lanisce.lani.data.Exercise

/**
 * A run that listens (the storm, the forest's gathering, a request or a project that pays 🪵; companion/GAME.md,
 * "Challenges"): mostly heard, a little written. Hearing and then typing takes time, a long sentence most, so at most
 * every third question is written ([typedAt]: the third, the sixth, …); the others are heard and their meaning picked.
 * Untimed, a written one is a dictation of a short phrase ([short]). Against the clock (an event) none is typed whole: a
 * phrase is heard and its words tapped in order from a bank with an extra word or two ([chips]), or it is shown with one
 * word missing and that word typed ([gap]). Pure.
 */
internal object ListeningMix {
    /** The most words a phrase has to be typed after hearing it. */
    const val DICTATION_WORDS = 4

    /** At most every this many questions one is typed. */
    const val EVERY = 3

    /** Whether [text] is short enough to type after hearing it: a word or a phrase of up to [DICTATION_WORDS] words. */
    fun short(text: String): Boolean = words(text) in 1..DICTATION_WORDS

    /** How many words [text] has (what is only punctuation isn't one). */
    fun words(text: String): Int = text.trim().split(SPACE).count { w -> w.any(Char::isLetterOrDigit) }

    private val SPACE = Regex("\\s+")

    /** Word chips by ear want this many words at least: fewer are no order to find. */
    const val CHIPS_WORDS = 3

    /**
     * Word chips by ear for [phrase] ([accepted]: its forms as a card has them): the phrase is heard, and its words are
     * tapped in order from a bank with [extras] of the [extra] words that aren't in it (one or two). Null for fewer than
     * [CHIPS_WORDS] words.
     */
    fun chips(phrase: String, accepted: List<String>, extra: List<String>, instruction: String, explain: String? = null, extras: Int = 2): Exercise.Reorder? {
        val tokens = phrase.trim().split(SPACE).filter { it.isNotEmpty() }
        if (words(phrase) < CHIPS_WORDS) return null
        val own = tokens.map { bare(it).lowercase() }.toSet()
        val solutions = (listOf(phrase) + accepted).map { a -> a.trim().split(SPACE).filter { it.isNotEmpty() } }.filter { it.size == tokens.size }.distinct()
        return Exercise.Reorder(
            prompt = "", tokens = tokens, solutions = solutions, explain = explain,
            distractors = extra.map(::bare).filter { it.isNotEmpty() && it.lowercase() !in own }.distinct().take(extras.coerceIn(1, 2)),
            instruction = instruction, audio = phrase,
        )
    }

    /** The words of [phrase] a gap by ear may leave out (by their place): those of three letters and more, else any word. */
    fun gapWords(phrase: String): List<Int> {
        val tokens = phrase.trim().split(SPACE).filter { it.isNotEmpty() }
        val long = tokens.indices.filter { bare(tokens[it]).count(Char::isLetter) >= 3 }
        return long.ifEmpty { tokens.indices.filter { bare(tokens[it]).any(Char::isLetterOrDigit) } }
    }

    /**
     * A gap by ear: [phrase] heard whole, shown with its [at]th word missing (its punctuation kept), that word typed. Null
     * for a single word (nothing would be left to show).
     */
    fun gap(phrase: String, at: Int, instruction: String, explain: String? = null): Exercise.Cloze? {
        val tokens = phrase.trim().split(SPACE).filter { it.isNotEmpty() }
        if (words(phrase) < 2 || at !in tokens.indices) return null
        val word = bare(tokens[at]).takeIf { it.isNotEmpty() } ?: return null
        val start = tokens[at].indexOf(word)
        val shown = tokens.mapIndexed { i, t -> if (i == at) t.substring(0, start) + "___" + t.substring(start + word.length) else t }.joinToString(" ")
        return Exercise.Cloze(shown, listOf(word), explain = explain, instruction = instruction, audio = phrase)
    }

    /** [token] without the punctuation round it ("dan!" → "dan", "»Pojdi«" → "Pojdi"). */
    fun bare(token: String): String = token.trim { !it.isLetterOrDigit() }

    /** Whether question [i] (from 0) may be typed: the third, the sixth, … */
    fun typedAt(i: Int): Boolean = i % EVERY == EVERY - 1

    /** How many of [n] questions may be typed. */
    fun typed(n: Int): Int = (0 until n).count(::typedAt)

    /**
     * [n] questions: the [typed] ones only where [typedAt] allows, the [heard] ones everywhere else; a typed place without
     * a typed one left takes a heard one, and the run is shorter when the heard ones run out.
     */
    fun <T> arrange(n: Int, typed: List<T>, heard: List<T>): List<T> {
        val t = typed.iterator()
        val h = heard.iterator()
        val out = ArrayList<T>(n)
        for (i in 0 until n) {
            out += when {
                typedAt(i) && t.hasNext() -> t.next()
                h.hasNext() -> h.next()
                else -> break
            }
        }
        return out
    }
}
