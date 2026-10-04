package si.lanisce.lani.data

import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang

/**
 * Local answer matching, as documented in lani-studio/reference/module-spec.md, in the learner's target language
 * (the pair's, [L10n.pair]; every function takes another [Lang] too). What counts as the same answer, as a slip of the
 * keyboard (Slovene's č/š/ž, Italian's accents, German's umlauts) and as almost right (Italian's apostrophes) is each
 * language's table ([Spelling]).
 *
 * Typos are forgiven only in a word's stem. Slovene grammar lives in the endings
 * (moj → moja, Berlin → Berlina, dober → dobro), so a one-letter slip there
 * is a real mistake, not a typo; Italian's (amico → amica) and German's too.
 */
object Grading {
    enum class Verdict { CORRECT, ALMOST, WRONG }

    /**
     * Why an answer wasn't exact, for the feedback line: [DIACRITICS] Slovene's č/š/ž, [ACCENT] Italian's è/é/à,
     * [UMLAUT] German's ä/ö/ü/ß (or ae/oe/ue/ss), [APOSTROPHE] Italian's elision (l'acqua, un'amica).
     */
    enum class Hint { NONE, DIACRITICS, TYPO, ENDING, ACCENT, UMLAUT, APOSTROPHE }

    /**
     * @param expected the accepted answer closest to the learner's, in its original spelling
     * @param marks character ranges in [expected] that differ from the answer (to highlight)
     */
    data class Result(val verdict: Verdict, val hint: Hint, val expected: String, val marks: List<IntRange>)

    private val punctuation = Regex("[.,!?;:¡¿\"„“”«»]")
    private val space = Regex("\\s+")
    /** Typographic apostrophes (and the accents typed for one: perche`) as '. */
    private val apostrophe = Regex("[’ʼ`´]")
    /** Letters at the end of a word treated as its grammatical ending (-a/-o/-e; -ega/-em on longer words). */
    private fun endingLength(word: String) = if (word.length <= 5) 1 else 2

    /**
     * Slips forgiven in a word's stem, by the word's length: none under 4 letters (one letter makes another word: sem,
     * sen), one up to 8, two from 9 (German compounds, Bürgermeister; Slovene's prijateljica).
     */
    fun typosAllowed(word: String) = when {
        word.length < 4 -> 0
        word.length < 9 -> 1
        else -> 2
    }

    private fun target(): Lang = L10n.pair.target

    /** Lowercase (in [lang]'s locale), no punctuation, one kind of apostrophe, single spaces. */
    fun normalize(s: String, lang: Lang = target()): String =
        s.lowercase(Spelling.of(lang).locale).replace(apostrophe, "'").replace(punctuation, "").replace(space, " ").trim()

    /** [normalize]d, with the language's other spellings of the same answer written one way ("don't" → "do not"). */
    fun canonical(s: String, lang: Lang = target()): String = rewrite(normalize(s, lang), Spelling.of(lang).same)

    /** [canonical], without what a keyboard may lack ("čaša" → "casa", "perché" → "perche", "Mädchen" → "maedchen"). */
    fun fold(s: String, lang: Lang = target()): String = rewrite(canonical(s, lang), Spelling.of(lang).fold)

    /** [fold]ed, and what is almost right besides written one way (Italian: "un'amica" and "un amica" → "un amica"). */
    fun loose(s: String, lang: Lang = target()): String = rewrite(fold(s, lang), Spelling.of(lang).loose)

    private fun rewrite(s: String, rules: List<Spelling.Rule>): String =
        if (rules.isEmpty()) s else rules.fold(s) { acc, r -> r.apply(acc) }.replace(space, " ").trim()

    fun match(answer: String, accept: List<String>, lang: Lang = target()): Verdict = check(answer, accept, lang).verdict

    fun check(answer: String, accept: List<String>, lang: Lang = target()): Result {
        val spelling = Spelling.of(lang)
        val a = canonical(answer, lang)
        if (a.isEmpty() || accept.isEmpty()) return Result(Verdict.WRONG, Hint.NONE, accept.firstOrNull().orEmpty(), emptyList())
        accept.firstOrNull { canonical(it, lang) == a }?.let { return Result(Verdict.CORRECT, Hint.NONE, it, emptyList()) }

        // Closest accepted answer by edit distance, ignoring what a keyboard may lack.
        val folded = fold(a, lang)
        val best = accept.minBy { distance(fold(it, lang), folded) }
        val marks = marks(answer, best, lang)
        if (fold(best, lang) == folded) return Result(Verdict.ALMOST, spelling.foldHint, best, marks)
        if (spelling.loose.isNotEmpty() && loose(best, lang) == loose(a, lang)) return Result(Verdict.ALMOST, spelling.looseHint, best, marks)

        val expectedWords = fold(best, lang).split(' ')
        val answerWords = folded.split(' ')
        if (expectedWords.size != answerWords.size) return Result(Verdict.WRONG, Hint.NONE, best, marks)
        val differing = expectedWords.indices.filter { expectedWords[it] != answerWords[it] }
        if (differing.size != 1) return Result(Verdict.WRONG, Hint.NONE, best, marks)

        val e = expectedWords[differing[0]]
        val w = answerWords[differing[0]]
        val d = distance(e, w)
        return when {
            d in 1..typosAllowed(e) && editInStem(e, w) -> Result(Verdict.ALMOST, Hint.TYPO, best, marks)
            d <= 2 && !editInStem(e, w) -> Result(Verdict.WRONG, Hint.ENDING, best, marks)
            else -> Result(Verdict.WRONG, Hint.NONE, best, marks)
        }
    }

    /** True when every changed position of [expected] lies before its ending. */
    private fun editInStem(expected: String, answer: String): Boolean {
        val (start, end) = changedRegion(expected, answer)
        val last = if (end > start) end - 1 else start // pure insertion: the insertion point
        return last < expected.length - endingLength(expected)
    }

    /** [start, end) of the part of [expected] that differs from [other], via common prefix and suffix. */
    private fun changedRegion(expected: String, other: String): Pair<Int, Int> {
        var p = 0
        while (p < expected.length && p < other.length && expected[p] == other[p]) p++
        var s = 0
        while (s < expected.length - p && s < other.length - p &&
            expected[expected.length - 1 - s] == other[other.length - 1 - s]
        ) s++
        return p to expected.length - s
    }

    /**
     * Highlights in [expected] (original spelling) per word, when both have the same number of
     * words and at most two words differ. Otherwise the whole answer is different: no highlights.
     */
    private fun marks(answer: String, expected: String, lang: Lang): List<IntRange> {
        val expWords = Regex("\\S+").findAll(expected).toList()
        val ansWords = normalize(answer, lang).split(' ')
        if (expWords.size != ansWords.size) return emptyList()
        val out = mutableListOf<IntRange>()
        var differing = 0
        for ((i, m) in expWords.withIndex()) {
            val raw = m.value
            val core = raw.trim { punctuation.matches(it.toString()) }
            val offset = m.range.first + raw.indexOf(core)
            val e = core.lowercase(Spelling.of(lang).locale).replace(apostrophe, "'")
            val w = ansWords[i]
            if (e == w) continue
            differing++
            val (start, end) = changedRegion(e, w)
            val range = if (end > start) start until end else (start - 1).coerceAtLeast(0) until start.coerceAtMost(e.length).coerceAtLeast(1)
            out += (offset + range.first)..(offset + range.last)
        }
        return if (differing in 1..2) out else emptyList()
    }

    /** Optimal string alignment distance (Levenshtein plus adjacent swaps). */
    fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }

    fun reorder(order: List<String>, solutions: List<List<String>>, lang: Lang = target()): Boolean {
        val a = order.map { normalize(it, lang) }
        return solutions.any { sol -> sol.map { normalize(it, lang) } == a }
    }
}
