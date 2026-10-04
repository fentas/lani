package si.lanisce.lani.data

import si.lanisce.lani.l10n.L10n

/**
 * Grades what a speech recognizer heard. Each alternative goes through [Grading.check], so its
 * typo and ending rules apply to the heard text; the best alternative wins.
 */
object SpeechGrading {
    /**
     * One recognizer alternative, graded. [confidence] is 0..1, or null when the recognizer gave none.
     * [unclear]: expected words the node's recognizer was unsure of (see [SttSpeech.unclear]).
     */
    data class Heard(val text: String, val confidence: Float?, val result: Grading.Result, val unclear: List<String> = emptyList())

    /** Better verdict first, then closer to the expected text; ties keep the recognizer's order. */
    private val order = compareBy<Heard> { it.result.verdict.ordinal }
        .thenBy { Grading.distance(Grading.normalize(it.result.expected), Grading.normalize(it.text)) }

    /**
     * The best of the recognizer's [alternatives] (most likely first) against [accept].
     * [confidences] runs parallel to [alternatives]; values ≤ 0 mean "not reported".
     */
    fun best(alternatives: List<String>, confidences: FloatArray?, accept: List<String>): Heard? =
        alternatives.withIndex()
            .filter { it.value.isNotBlank() }
            // numbers the recognizer wrote as digits, in words as the answer has them ("mi2 sva doma" → "midva sva doma")
            .map { (i, t) -> SpokenNumbers.inWords(t.trim(), L10n.pair.target, accept.joinToString(" ")) to i }
            .map { (t, i) -> Heard(t, confidences?.getOrNull(i)?.takeIf { it > 0f }, Grading.check(t, accept)) }
            .minWithOrNull(order)

    /** The best of several attempts. */
    fun bestOf(attempts: List<Heard>): Heard? = attempts.minWithOrNull(order)

    /**
     * Character ranges of the words in [text] with no counterpart in [other], by a word-level
     * longest common subsequence that ignores case, punctuation and diacritics.
     */
    fun differingWords(text: String, other: String): List<IntRange> {
        val words = Regex("\\S+").findAll(text).mapNotNull { m ->
            val core = Regex("[\\p{L}\\p{N}].*[\\p{L}\\p{N}]|[\\p{L}\\p{N}]").find(m.value) ?: return@mapNotNull null
            val start = m.range.first + core.range.first
            (start..start + core.value.length - 1) to key(core.value)
        }.toList()
        val a = words.map { it.second }
        val b = Grading.normalize(other).split(' ').filter { it.isNotEmpty() }.map(::key)
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val out = mutableListOf<IntRange>()
        var i = 0
        var j = 0
        while (i < a.size) when {
            j < b.size && a[i] == b[j] -> { i++; j++ }
            j < b.size && lcs[i][j + 1] >= lcs[i + 1][j] -> j++
            else -> { out += words[i].first; i++ }
        }
        return out
    }

    private fun key(word: String) = Grading.fold(Grading.normalize(word))
}
