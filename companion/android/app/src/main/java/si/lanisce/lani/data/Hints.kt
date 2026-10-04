package si.lanisce.lani.data

import si.lanisce.lani.data.Grading.Verdict
import java.util.Locale
import kotlin.random.Random

/**
 * 💡 hints for typed answers, reorder tiles and hidden speak sentences (each level costs a little),
 * and spelling suggestions from the words Jan already knows (free).
 *
 * Every hint is built from one accepted answer only: the one closest to what's typed so far.
 */
object Hints {
    const val MAX_LEVEL = 3
    private const val QUALITY_FLOOR = 3
    private const val MIN_PREFIX = 2
    private val sl = Locale.forLanguageTag("sl")
    private val letters = Regex("\\p{L}+")

    /**
     * What the hint strip shows. [line] is the answer's shape ("M__ p______ j_ N____."), with the
     * next word revealed from level 2; [bank] (level 3) holds the letters still to type, one
     * shuffled group per unfinished word.
     */
    data class Shown(val level: Int, val line: String, val bank: List<List<Char>> = emptyList())

    /** The hint for [level] (1..3) against what's [typed] so far; null when nothing is accepted. */
    fun hint(level: Int, typed: String, accept: List<String>, random: Random = Random.Default): Shown? {
        if (level < 1) return null
        val target = target(typed, accept) ?: return null
        return when (level) {
            1 -> Shown(1, shape(target))
            2 -> Shown(2, nextWord(typed, target))
            else -> Shown(MAX_LEVEL, nextWord(typed, target), letterBank(typed, target, random))
        }
    }

    /**
     * The accepted answer being built: like [Grading.check] it ignores case, punctuation and
     * diacritics, but compares a half-typed answer with the start of each accepted one.
     */
    fun target(typed: String, accept: List<String>): String? {
        if (accept.isEmpty()) return null
        val t = key(typed)
        if (t.isEmpty()) return accept.first()
        return accept.minWith(compareBy({ prefixDistance(t, key(it)) }, { Grading.distance(key(it), t) }))
    }

    private fun key(s: String) = Grading.fold(Grading.normalize(s))

    private fun prefixDistance(t: String, c: String): Int =
        (t.length - 2..t.length + 2).map { it.coerceIn(0, c.length) }.distinct().minOf { Grading.distance(t, c.take(it)) }

    /** Level 1: each word's first letter, underscores for the rest, punctuation kept. */
    fun shape(answer: String): String = tokens(answer).joinToString(" ") { mask(it, 0) }

    /**
     * Level 2: the words typed correctly and the first one that isn't, in full; the rest as shape.
     * When that word is the last one hidden, only part of it shows (half, but always one letter more
     * than already typed right), so the strip never spells out the whole answer yet always adds something.
     */
    fun nextWord(typed: String, answer: String): String {
        val toks = tokens(answer)
        val words = toks.filter { Grading.normalize(it).isNotEmpty() }
        val u = firstUnfinished(typed, words)
        val partial = words(typed).getOrNull(u)?.takeIf { p -> words.getOrNull(u)?.let { Grading.normalize(it).startsWith(p) } == true }
        val typedRight = partial?.count(Char::isLetterOrDigit) ?: 0
        var w = -1
        return toks.joinToString(" ") { tok ->
            if (Grading.normalize(tok).isEmpty()) return@joinToString tok
            w++
            when {
                w < u -> tok
                w == u && u < words.lastIndex -> tok
                w == u -> {
                    val letters = tok.count(Char::isLetterOrDigit)
                    // Level 1 already shows the first letter, so show at least two (or one past what's typed).
                    mask(tok, maxOf(letters / 2, typedRight + 1, 2).coerceAtMost(letters - 1).coerceAtLeast(1))
                }
                else -> mask(tok, 0)
            }
        }
    }

    /** Level 3: the letters still missing, per unfinished word (a correctly typed start is left out). */
    fun letterBank(typed: String, answer: String, random: Random = Random.Default): List<List<Char>> {
        val words = tokens(answer).filter { Grading.normalize(it).isNotEmpty() }
        val u = firstUnfinished(typed, words)
        val typedWords = words(typed)
        return words.drop(u).mapIndexedNotNull { i, tok ->
            var chars = tok.filter(Char::isLetterOrDigit).toList()
            val partial = typedWords.getOrNull(u + i)
            if (i == 0 && partial != null && u == typedWords.lastIndex && Grading.normalize(tok).startsWith(partial)) {
                chars = chars.drop(partial.count(Char::isLetterOrDigit))
            }
            chars.takeIf { it.isNotEmpty() }?.let { shuffle(it, random) }
        }
    }

    /** A shuffle that doesn't hand back the letters in their original order, when it can help it. */
    private fun shuffle(chars: List<Char>, random: Random): List<Char> {
        if (chars.distinct().size < 2) return chars
        repeat(8) { chars.shuffled(random).let { if (it != chars) return it } }
        return chars.reversed()
    }

    private fun tokens(s: String) = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    private fun words(s: String) = Grading.normalize(s).split(' ').filter { it.isNotEmpty() }

    /** Index of the first word of [answerWords] not typed exactly (case and punctuation aside). */
    private fun firstUnfinished(typed: String, answerWords: List<String>): Int {
        val t = words(typed)
        return answerWords.indices.firstOrNull { it >= t.size || t[it] != Grading.normalize(answerWords[it]) } ?: answerWords.size
    }

    /** Keeps the first [reveal] letters of [word] (at least each part's first), hides the rest. */
    private fun mask(word: String, reveal: Int): String {
        var seen = 0
        return buildString {
            for ((i, c) in word.withIndex()) {
                val start = i == 0 || !word[i - 1].isLetterOrDigit()
                append(if (!c.isLetterOrDigit() || start || seen < reveal) c else '_')
                if (c.isLetterOrDigit()) seen++
            }
        }
    }

    /**
     * Reorder 💡: the placed tiles plus the next correct one; a wrong tile and all after it go back
     * first. Null when that would change nothing or place the very last tile.
     */
    fun nextTile(tiles: List<String>, placed: List<Int>, solutions: List<List<String>>): List<Int>? {
        val have = placed.map { Grading.normalize(tiles[it]) }
        val (sol, k) = solutions.filter { it.isNotEmpty() }
            .map { s -> s to s.map { Grading.normalize(it) }.zip(have).takeWhile { (a, b) -> a == b }.size }
            .maxByOrNull { it.second } ?: return null
        val keep = placed.take(k)
        val back = keep.takeIf { it.size < placed.size }
        if (k >= sol.size - 1) return back
        val want = Grading.normalize(sol[k])
        val tile = tiles.indices.firstOrNull { it !in keep && Grading.normalize(tiles[it]) == want } ?: return back
        return keep + tile
    }

    // --- speak: 💡 until the sentence can be read aloud ----------------------------------------

    /** Speak 💡: after the shape, the words come in at most this many taps. */
    private const val SPOKEN_WORD_STEPS = 4

    /**
     * How many 💡 a speak exercise's model sentence [say] has: its shape (level 1), then its words
     * from the start, one per tap for up to four words and a few per tap in a longer sentence, so
     * the last level shows it whole. 0 when it has no words.
     */
    fun spokenLevels(say: String): Int {
        val n = tokens(say).count(::isWord)
        return if (n == 0) 0 else 1 + minOf(n, SPOKEN_WORD_STEPS)
    }

    /**
     * [say] at speak 💡 [level] (1..[spokenLevels], higher counts as the last): level 1 its shape
     * ("M__ p______ j_ N____."), then its first words in full and the rest still as shape
     * ("Moj priimek j_ N____."), at the last level all of it, to read aloud. Punctuation always
     * shows. Null before the first hint, or when [say] has no words.
     */
    fun spoken(say: String, level: Int): String? {
        val max = spokenLevels(say)
        if (level < 1 || max == 0) return null
        val toks = tokens(say)
        val n = toks.count(::isWord)
        val steps = max - 1
        val full = (n * (level.coerceAtMost(max) - 1) + steps - 1) / steps // ⌈n·(level−1)/steps⌉ words in full
        var w = 0
        return toks.joinToString(" ") { tok -> if (!isWord(tok) || w++ < full) tok else mask(tok, 0) }
    }

    private fun isWord(tok: String) = tok.any(Char::isLetterOrDigit)

    /**
     * SM-2 quality after [hints] hint levels or hinted tiles: one less each, never below 3 (nor above [quality]).
     * A speak exercise's 💡 count the same, one per level: its whole sentence revealed and read aloud still passes.
     */
    fun penalize(quality: Int, hints: Int): Int =
        if (hints <= 0) quality else minOf(quality, maxOf(QUALITY_FLOOR, quality - hints))

    /** What the village pays for an answer: a correct one reached with hints counts as almost. */
    fun paid(verdict: Verdict, hints: Int): Verdict =
        if (verdict == Verdict.CORRECT && hints > 0) Verdict.ALMOST else verdict

    // --- suggestions while typing ------------------------------------------------------------

    /**
     * The distinct words of known Slovene phrases, for suggestions. Sentence-initial capitals are
     * dropped ("Dober dan" → dober); a capital inside a phrase marks a name ("iz Ljubljane").
     */
    fun vocabulary(fronts: List<String>): List<String> {
        val out = LinkedHashMap<String, String>()
        for (front in fronts) {
            var prevEnd = 0
            for (m in letters.findAll(front)) {
                val between = front.substring(prevEnd, m.range.first)
                val sentenceStart = prevEnd == 0 || between.any { it in "/.!?…;:(" }
                prevEnd = m.range.last + 1
                val w = m.value
                if (w.length < MIN_PREFIX) continue
                val lower = w.lowercase(sl)
                val word = if (!sentenceStart && w[0].isUpperCase()) w else lower
                val prev = out[lower]
                if (prev == null || (prev != lower && word == lower)) out[lower] = word
            }
        }
        return out.values.toList()
    }

    /** [start, end) of the word at [cursor] in [text], letters only; empty when there is none. */
    fun wordAt(text: String, cursor: Int): IntRange {
        val c = cursor.coerceIn(0, text.length)
        var s = c
        while (s > 0 && text[s - 1].isLetter()) s--
        var e = c
        while (e < text.length && text[e].isLetter()) e++
        return s until e
    }

    /**
     * Up to [max] known words starting with the word typed up to [cursor], ignoring case and
     * diacritics ("cet" → četrtek), after at least two letters. Never the word already typed.
     */
    fun suggest(text: String, cursor: Int, vocabulary: List<String>, max: Int = 4): List<String> {
        val span = wordAt(text, cursor)
        val prefix = text.substring(span.first, cursor.coerceIn(span.first, text.length))
        if (prefix.length < MIN_PREFIX) return emptyList()
        val p = prefix.lowercase(sl)
        val key = Grading.fold(p)
        val whole = text.substring(span.first, span.last + 1).lowercase(sl)
        val capital = prefix[0].isUpperCase()
        return vocabulary.asSequence()
            .filter { val l = it.lowercase(sl); l != whole && Grading.fold(l).startsWith(key) }
            .sortedWith(compareBy({ !it.lowercase(sl).startsWith(p) }, { it.length }))
            .map { if (capital) it.replaceFirstChar { c -> c.titlecase(sl) } else it }
            .distinct()
            .take(max)
            .toList()
    }

    /**
     * [text] with the word at [cursor] replaced by [word], and the new cursor: after the word and
     * one space (added at the end of the text).
     */
    fun replaceWord(text: String, cursor: Int, word: String): Pair<String, Int> {
        val span = wordAt(text, cursor)
        val before = text.substring(0, span.first)
        val after = text.substring(span.last + 1)
        val end = before.length + word.length
        return when {
            after.isEmpty() -> "$before$word " to end + 1
            after[0].isWhitespace() -> "$before$word$after" to end + 1
            else -> "$before$word$after" to end
        }
    }
}
