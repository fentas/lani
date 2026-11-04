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
 * is a real mistake, not a typo; Italian's (amico → amica) and German's too. Where a form has a stem of its own
 * (Italian's andrò, farò, venuto), the regular one a learner makes of it is a mistake too ([Spelling.wrong]).
 */
object Grading {
    enum class Verdict { CORRECT, ALMOST, WRONG }

    /**
     * Why an answer wasn't exact, for the feedback line: [DIACRITICS] Slovene's č/š/ž, [ACCENT] Italian's è/é/à,
     * [UMLAUT] German's ä/ö/ü/ß (or ae/oe/ue/ss), [APOSTROPHE] Italian's elision (l'acqua, un'amica), [NONSTANDARD] what people
     * say that writing doesn't want ([Spelling.nonstandard]: the standard form is [Result.expected]); [SPOKEN] a form people
     * say, said aloud: right in speech, the standard form [Result.expected] ([Production.grade]).
     */
    enum class Hint { NONE, DIACRITICS, TYPO, ENDING, ACCENT, UMLAUT, APOSTROPHE, NONSTANDARD, SPOKEN }

    /**
     * @param expected the accepted answer closest to the learner's, in its original spelling
     * @param marks character ranges in [expected] that differ from the answer (to highlight)
     */
    data class Result(val verdict: Verdict, val hint: Hint, val expected: String, val marks: List<IntRange>)

    private val punctuation = Regex("[.,!?;:¡¿\"„“”«»]")
    /**
     * The middle dot between the parts of a list ("midva sva · vidva sta"), a separator like a comma: read as a space,
     * so the parts typed with commas, spaces or the dots all match, and two words around a dot never run together.
     */
    private val separator = Regex("·")
    private val space = Regex("\\s+")
    /** Typographic apostrophes (and the accents typed for one: perche`) as '. */
    private val apostrophe = Regex("[’ʼ`´]")
    /**
     * Letters at the end of a word treated as its grammatical ending: one on a short word (-a/-o/-e), on a longer one the
     * language's [Spelling.endingLetters] (two by default: Slovene's -ega/-em; one in English, whose -s, -ed, -ing end
     * in a letter the slip must not touch, while "usualy" for usually is a slip).
     */
    private fun endingLength(word: String, spelling: Spelling) = if (word.length <= 5) 1 else spelling.endingLetters

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

    /** Lowercase (in [lang]'s locale), no punctuation (a list's middle dot a space), one kind of apostrophe, single spaces. */
    fun normalize(s: String, lang: Lang = target()): String =
        s.lowercase(Spelling.of(lang).locale).replace(apostrophe, "'").replace(punctuation, "").replace(separator, " ")
            .replace(space, " ").trim()

    /** [normalize]d, with the language's other spellings of the same answer written one way ("don't" → "do not"). */
    fun canonical(s: String, lang: Lang = target()): String = rewrite(normalize(s, lang), Spelling.of(lang).same)

    /** [canonical], without what a keyboard may lack ("čaša" → "casa", "perché" → "perche", "Mädchen" → "maedchen"). */
    fun fold(s: String, lang: Lang = target()): String = rewrite(canonical(s, lang), Spelling.of(lang).fold)

    /**
     * [normalize]d and folded, without [canonical]'s rewrites of whole words: a part of a word, as a speaking or writing
     * rule's stem "würd*" ([Production]), which German's "würd" → "würde" would complete.
     */
    fun foldStem(s: String, lang: Lang = target()): String = rewrite(normalize(s, lang), Spelling.of(lang).fold)

    /** [fold]ed, and what is almost right besides written one way (Italian: "un'amica" and "un amica" → "un amica"). */
    fun loose(s: String, lang: Lang = target()): String = rewrite(fold(s, lang), Spelling.of(lang).loose)

    private fun rewrite(s: String, rules: List<Spelling.Rule>): String =
        if (rules.isEmpty()) s else rules.fold(s) { acc, r -> r.apply(acc) }.replace(space, " ").trim()

    fun match(answer: String, accept: List<String>, lang: Lang = target()): Verdict = check(answer, accept, lang).verdict

    /** @param before the sentence before a gap's answer, where the answer is one word of it: what [Spelling.Nonstandard.after] may need. */
    fun check(answer: String, accept: List<String>, lang: Lang = target(), before: String = ""): Result {
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
        nonstandardFor(folded, accept, before, lang, spelling)?.let { return Result(Verdict.ALMOST, Hint.NONSTANDARD, it, marks(answer, it, lang)) }
        verbSecondFor(folded, accept, lang, spelling)?.let { return Result(Verdict.ALMOST, Hint.NONSTANDARD, it, marks(answer, it, lang)) }

        val expectedWords = fold(best, lang).split(' ')
        val answerWords = folded.split(' ')
        if (expectedWords.size != answerWords.size) return Result(Verdict.WRONG, Hint.NONE, best, marks)
        val differing = expectedWords.indices.filter { expectedWords[it] != answerWords[it] }
        if (differing.size != 1) return Result(Verdict.WRONG, Hint.NONE, best, marks)

        val e = expectedWords[differing[0]]
        val w = answerWords[differing[0]]
        val d = distance(e, w)
        return when {
            // one ending for another on the same stem (the language's [Spelling.endingSwaps]: "dormono" for dormano)
            spelling.endingSwaps.any { (right, typed) -> e.endsWith(right) && w.endsWith(typed) && e.dropLast(right.length) == w.dropLast(typed.length) } ->
                Result(Verdict.WRONG, Hint.ENDING, best, marks)
            // a known mistake (the language's [Spelling.wrong]: "anderò" for andrò) is grammar, never a slip in the stem
            d in 1..typosAllowed(e) && editInStem(e, w, spelling) && spelling.wrong.none { it.matches(w, e) } -> Result(Verdict.ALMOST, Hint.TYPO, best, marks)
            d <= 2 && !editInStem(e, w, spelling) -> Result(Verdict.WRONG, Hint.ENDING, best, marks)
            else -> Result(Verdict.WRONG, Hint.NONE, best, marks)
        }
    }

    /** The accepted answer whose standard word [answer] ([fold]ed) has what people say in its place, if one has ([Spelling.nonstandard]). */
    private fun nonstandardFor(answer: String, accept: List<String>, before: String, lang: Lang, spelling: Spelling): String? {
        for (n in spelling.nonstandard) for (m in n.word.findAll(answer)) {
            val head = answer.substring(0, m.range.first)
            val tail = answer.substring(m.range.last + 1)
            if (n.after != null && !n.after.containsMatchIn(canonical(before, lang) + " " + head)) continue
            accept.firstOrNull { x ->
                val e = fold(x, lang)
                e.length >= head.length + tail.length && e.startsWith(head) && e.endsWith(tail) && n.forWord.matches(e.substring(head.length, e.length - tail.length))
            }?.let { return it }
        }
        return null
    }

    /**
     * The accepted answer whose clause after a conjunction of [Spelling.verbSecond] [answer] ([fold]ed) says with its last word, the
     * verb, moved up behind the subject, as people say it ("weil er hat keine Zeit" for weil er keine Zeit hat), every other word
     * in its place. The clause ends at the accepted answer's comma: "Obwohl es regnet, wir gehen" stays wrong.
     */
    private fun verbSecondFor(answer: String, accept: List<String>, lang: Lang, spelling: Spelling): String? {
        if (spelling.verbSecond.isEmpty()) return null
        val typed = answer.split(' ')
        return accept.firstOrNull { x ->
            val right = fold(x, lang).split(' ')
            val parts = x.split(',').map { fold(it, lang) }.filter { it.isNotEmpty() }
            // a rule of the table may join words across a comma ("so, dass" folds to sodass): the parts no longer add up to the sentence
            if (parts.sumOf { it.split(' ').size } != right.size) return@firstOrNull false
            var from = 0
            parts.any { part ->
                val end = from + part.split(' ').size
                val found = (from until end).any { c ->
                    val after = right.size - end
                    right[c] in spelling.verbSecond && typed.size >= c + 1 + after && typed.subList(0, c + 1) == right.subList(0, c + 1) &&
                        typed.takeLast(after) == right.takeLast(after) &&
                        verbMovedUp(typed.subList(c + 1, typed.size - after), right.subList(c + 1, end))
                }
                from = end
                found
            }
        }
    }

    /** [typed] is [right] with its last word moved up, a word or more staying before it; a separable verb in its two parts ("steht um fünf auf" for "um fünf aufsteht"). */
    private fun verbMovedUp(typed: List<String>, right: List<String>): Boolean {
        val start = typed.zip(right).takeWhile { (a, b) -> a == b }.size
        if (start == 0) return false
        val t = typed.drop(start)
        val r = right.drop(start)
        return when {
            t.size < 2 || r.isEmpty() -> false
            t.size == r.size -> t.first() == r.last() && t.drop(1) == r.dropLast(1)
            t.size == r.size + 1 -> t.last() + t.first() == r.last() && t.subList(1, t.size - 1) == r.dropLast(1)
            else -> false
        }
    }

    /** True when every changed position of [expected] lies before its ending. */
    private fun editInStem(expected: String, answer: String, spelling: Spelling): Boolean {
        val (start, end) = changedRegion(expected, answer)
        val last = if (end > start) end - 1 else start // pure insertion: the insertion point
        return last < expected.length - endingLength(expected, spelling)
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
        // a list's middle dots stand between its words, they are none ([separator])
        val expWords = Regex("[^\\s·]+").findAll(expected).toList()
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

    /**
     * Picking option [picked] of a choice (or a picked scenario) whose [answer] is right: [Verdict.ALMOST] for one of its
     * near misses ([almost]: a real answer that doesn't fit here, the wrong register or a near form), else wrong.
     */
    fun picked(picked: Int, answer: Int, almost: List<Int> = emptyList()): Verdict = when (picked) {
        answer -> Verdict.CORRECT
        in almost -> Verdict.ALMOST
        else -> Verdict.WRONG
    }

    /**
     * The options [selected] in a select-all exercise whose right ones are [answers]: all of them and none else is right,
     * one missed or one too many (not both more) almost, anything else wrong.
     */
    fun multi(selected: Collection<Int>, answers: Collection<Int>): Verdict {
        val hits = selected.count { it in answers }
        val extra = selected.size - hits
        return when {
            hits == answers.size && extra == 0 -> Verdict.CORRECT
            hits >= answers.size - 1 && extra <= 1 -> Verdict.ALMOST
            else -> Verdict.WRONG
        }
    }

    fun reorder(order: List<String>, solutions: List<List<String>>, lang: Lang = target()): Boolean {
        val a = order.map { normalize(it, lang) }
        return solutions.any { sol -> sol.map { normalize(it, lang) } == a }
    }
}
