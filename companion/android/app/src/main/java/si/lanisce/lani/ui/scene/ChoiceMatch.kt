package si.lanisce.lani.ui.scene

import si.lanisce.lani.data.Grading
import si.lanisce.lani.data.SpokenNumbers
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang

/**
 * Which of a dialog turn's choices the learner said out loud. Pure: the dialog's 🎤 hands it what the recognizer
 * heard (its alternatives, most likely first) and the choices in the target language ([lang], the pair's).
 *
 * Each choice is scored against each alternative, 0..1, on normalized text without diacritics (a recognizer often
 * drops them; which letters those are is the language's [Grading.fold]: č/š/ž, è/é/à, ä/ö/ü) and without punctuation
 * or apostrophes, the better of two ways:
 * - letters: 1 − edit distance / the longer length, which forgives words run together or split ("nazdravje");
 * - words: 1 − word edit distance / the longer word count, where a word a few letters off (a dropped or changed
 *   ending, "lep" for "lepa") costs only its share of letters, and a missing or extra word costs 1. This forgives
 *   an extra "ja," and a partial sentence: 3 of 5 words said is 0.6.
 * The best choice counts when it scores at least [ACCEPT] and beats the next one by [MARGIN]. Choices that score about
 * alike ("Rad bi kavo, prosim." and "Rad bi čaj, prosim.") are then told apart by their own words, the ones they
 * don't share: the choice whose own words were said counts, with the same two limits. Otherwise the learner is asked
 * again, rather than the dialog guessing.
 */
object ChoiceMatch {
    /** The least a choice must score to count as said. A partial sentence needs about 60% of its words. */
    const val ACCEPT = 0.55

    /** How much better than the next choice the best must score: two similar choices need the word that differs. */
    const val MARGIN = 0.15

    /** A word at least this similar to another is the same word said a little differently. */
    private const val SAME_WORD = 0.5

    /** The recognizer prompt's length limit (the node's /stt takes 300 characters). */
    private const val MAX_PROMPT = 300

    enum class Why {
        /** Nothing, or only noise, came back. */
        NOTHING,
        /** No choice is close to what was heard. */
        NONE_CLOSE,
        /** Two choices are about as close: which one was meant isn't clear. */
        TWO_CLOSE,
    }

    sealed interface Result {
        /** What was heard: the alternative that decided, or the most likely one. */
        val heard: String

        /** Choice [index] was said; [score] (0..1) is how close, or how much of its own words when it was told apart. */
        data class Said(val index: Int, override val heard: String, val score: Double) : Result

        data class Unsure(val why: Why, override val heard: String) : Result
    }

    /** The choice said in [heard] (the recognizer's alternatives, most likely first), among [choices]. */
    fun match(heard: List<String>, choices: List<String>, lang: Lang = L10n.pair.target): Result {
        // numbers the recognizer wrote as digits, in words as the choices have them ("mi2" → "midva")
        val said = choices.joinToString(" ")
        val alternatives = heard.map { SpokenNumbers.inWords(it.trim(), lang, said) }.filter { key(it, lang).isNotEmpty() }
        if (alternatives.isEmpty() || choices.isEmpty()) return Result.Unsure(Why.NOTHING, alternatives.firstOrNull().orEmpty())
        // Each choice's best score over the alternatives, and the alternative that gave it (the likelier on a tie).
        val best = choices.map { c -> alternatives.map { a -> a to similarity(a, c, lang) }.maxBy { it.second } }
        val ranked = best.indices.sortedByDescending { best[it].second }
        val top = ranked.first()
        val score = best[top].second
        if (score < ACCEPT) return Result.Unsure(Why.NONE_CLOSE, alternatives.first())
        val close = ranked.filter { score - best[it].second < MARGIN }
        if (close.size == 1) return Result.Said(top, best[top].first, score)
        return apart(alternatives, choices, close, lang) ?: Result.Unsure(Why.TWO_CLOSE, best[top].first)
    }

    /**
     * Among the [close] choices, which scored about alike, the one whose own words (those the others don't share)
     * were said; null when that doesn't settle it either.
     */
    private fun apart(alternatives: List<String>, choices: List<String>, close: List<Int>, lang: Lang): Result.Said? {
        val each = close.associateWith { words(choices[it], lang) }
        val shared = each.values.reduce(::common)
        val scored = close.map { k ->
            val own = without(each.getValue(k), shared)
            val (a, s) = alternatives.map { a -> a to contained(own, without(words(a, lang), shared)) }.maxBy { it.second }
            Triple(k, a, s)
        }.sortedByDescending { it.third }
        val (k, heard, s) = scored.first()
        val next = scored.getOrNull(1)?.third ?: 0.0
        return if (s >= ACCEPT && s - next >= MARGIN) Result.Said(k, heard, s) else null
    }

    /** How much of [own] was said in [heard]: each own word's likeness to the closest heard word, averaged. */
    private fun contained(own: List<String>, heard: List<String>): Double =
        if (own.isEmpty()) {
            if (heard.isEmpty()) 1.0 else 0.0 // said just the shared words: the choice that is only those
        } else {
            own.sumOf { w -> heard.maxOfOrNull { h -> likeness(w, h).takeIf { it >= SAME_WORD } ?: 0.0 } ?: 0.0 } / own.size
        }

    /** How close [heard] is to [choice], 0..1: the better of letters and words (see [ChoiceMatch]). */
    fun similarity(heard: String, choice: String, lang: Lang = L10n.pair.target): Double {
        val h = key(heard, lang)
        val c = key(choice, lang)
        if (h.isEmpty() || c.isEmpty()) return 0.0
        val letters = 1.0 - Grading.distance(h, c).toDouble() / maxOf(h.length, c.length)
        return maxOf(letters, wordScore(h.split(" "), c.split(" ")))
    }

    /**
     * What the recognizer is told to expect: the choices, as many as fit whole in [max] characters (Whisper spells
     * toward its prompt).
     */
    fun prompt(choices: List<String>, max: Int = MAX_PROMPT): String {
        val out = StringBuilder()
        for (c in choices.map { it.trim() }.filter { it.isNotEmpty() }) {
            val add = if (out.isEmpty()) c else " $c"
            if (out.length + add.length > max) break
            out.append(add)
        }
        return out.toString()
    }

    /** Lower case, no punctuation, no diacritics, single spaces: "Ja, žlico!" → "ja zlico", "Sì, l'acqua!" → "si l acqua". */
    internal fun key(s: String, lang: Lang = L10n.pair.target): String =
        Grading.fold(s, lang).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** 1 − the word edit distance / the longer word count; a similar word costs its share of different letters. */
    private fun wordScore(a: List<String>, b: List<String>): Double {
        val d = Array(a.size + 1) { DoubleArray(b.size + 1) }
        for (i in 0..a.size) d[i][0] = i.toDouble()
        for (j in 0..b.size) d[0][j] = j.toDouble()
        for (i in 1..a.size) for (j in 1..b.size) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost(a[i - 1], b[j - 1]))
        }
        return 1.0 - d[a.size][b.size] / maxOf(a.size, b.size)
    }

    private fun cost(a: String, b: String): Double {
        if (a == b) return 0.0
        val same = likeness(a, b)
        return if (same >= SAME_WORD) 1.0 - same else 1.0
    }

    /** 0..1: 1 − edit distance / the longer length. */
    private fun likeness(a: String, b: String): Double =
        if (a == b) 1.0 else 1.0 - Grading.distance(a, b).toDouble() / maxOf(a.length, b.length)

    private fun words(s: String, lang: Lang): List<String> = key(s, lang).split(' ').filter { it.isNotEmpty() }

    /** The words both have, each as often as both have it. */
    private fun common(a: List<String>, b: List<String>): List<String> {
        val left = b.toMutableList()
        return a.filter { left.remove(it) }
    }

    /** [a] without [b]'s words, each taken out as often as [b] has it. */
    private fun without(a: List<String>, b: List<String>): List<String> {
        val out = a.toMutableList()
        b.forEach { out.remove(it) }
        return out
    }
}
