package si.lanisce.lani.data

import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "🎤 Beri na glas · Read aloud" (companion/GAME.md, "Reading aloud"): the learner reads a part of a reading aloud and
 * every word of the text is marked read right, misread (with what was heard) or skipped. Pure.
 *
 * The node's Whisper hears the take twice (companion/stt-local/README.md, "Reading aloud"): with the text as its prompt
 * ([prompted]), which spells as the text does and hears correct reading as correct, and without ([plain]), which keeps
 * what was said but spells a Slovene sentence as Croatian or Czech. The words are graded on the prompted transcript
 * aligned to the text; the plain one is a second opinion only where the prompt may have pulled a slip toward the text:
 * a word the prompted transcript has right is misread when the plain one heard the same word with another ending
 * ("zlatim" read as "zlatimi"), or when Whisper was all but sure it didn't hear it (probability under [PULLED]) and
 * the plain transcript doesn't have it either. The phone's recognizer hears once, without a prompt: its transcript is
 * graded alone.
 *
 * The alignment is Slovene-aware as the app's grading is ([Grading.fold]: case, punctuation and č/š/ž don't count; a
 * number read out is the number), takes a word the recognizer ran together or split ("jebel" for "je bel", "speto
 * živi" for "spet oživi") as read right, forgives a final consonant said voiceless as Slovene says it ("Zlatorok"), and
 * a one-letter preposition (v, z, s, k) that ran into the next word.
 */
object ReadAloud {
    enum class Mark { RIGHT, MISREAD, SKIPPED }

    enum class By { NODE, PHONE }

    /**
     * A word of the text as written ([text], with its punctuation) in sentence [sentence] of its part, and how it was
     * read: [mark], what was [heard] instead (a misread word), [unclear] (right, but Whisper wasn't sure of it: say it
     * clearly), [ending] (misread in its ending).
     */
    data class WordMark(
        val text: String,
        val sentence: Int,
        val mark: Mark,
        val heard: String? = null,
        val unclear: Boolean = false,
        val ending: Boolean = false,
    )

    /** A part of a reading read in one take: its [sentences]. */
    data class Passage(val sentences: List<String>) {
        val text: String get() = sentences.joinToString(" ")
        val words: Int get() = tokens(text).size
    }

    /** A take graded: each word of the text ([words]), the words heard that the text doesn't have ([extra]), how long the reading took ([seconds]), who heard it. */
    data class Take(val words: List<WordMark>, val extra: List<String>, val seconds: Double?, val by: By) {
        val right: Int get() = words.count { it.mark == Mark.RIGHT }
        /** Right in every word. */
        val perfect: Boolean get() = words.isNotEmpty() && words.all { it.mark == Mark.RIGHT }
    }

    /**
     * A reading aloud in numbers: its [words] and how many were read [right], [misread] and [skipped]; its [sentences],
     * [readWell] (at least [READ_WELL] of their words right); the [seconds] of reading and the words a minute ([wpm]).
     */
    data class Score(val words: Int, val right: Int, val misread: Int, val skipped: Int, val sentences: Int, val readWell: Int, val seconds: Double?, val wpm: Int?) {
        /** Right words of all, 0..1. */
        val accuracy: Float get() = if (words == 0) 0f else right.toFloat() / words
        val percent: Int get() = (accuracy * 100).roundToInt()
    }

    /** A sentence counts as read well with at least this share of its words read right. */
    const val READ_WELL = 0.8f

    /** Whisper's probability under which a word the prompt spelled wasn't really heard (a pulled word: "bela" at 0.0). */
    const val PULLED = 0.05

    /** At most this many words to a part read in one take (the node takes 30 s; a learner reads 50 to 100 words a minute). */
    const val MAX_WORDS = 24

    // --- the text ----------------------------------------------------------------------------------------------------

    /** A word of a text: as written, and its core (without the punctuation around it). */
    data class Token(val text: String, val core: String)

    private val space = Regex("\\s+")
    private val coreOf = Regex("[\\p{L}\\p{N}](.*[\\p{L}\\p{N}])?")

    /** The words of [text] (a dash or a quote mark alone is none). */
    fun tokens(text: String): List<Token> =
        text.split(space).mapNotNull { w -> coreOf.find(w)?.value?.let { Token(w, it) } }

    private val sentenceEnd = Regex("(?<=[.!?…]|[.!?…][»«\"”“'’)])\\s+")

    /** The sentences of [line]. */
    fun sentences(line: String): List<String> = line.trim().split(sentenceEnd).map { it.trim() }.filter { tokens(it).isNotEmpty() }

    /**
     * The parts of a reading's [lines] (as they are read, in the target language) read one take each: its sentences in
     * order, a part as many of them as fit in [max] words (a longer sentence is a part of its own).
     */
    fun passages(lines: List<String>, max: Int = MAX_WORDS): List<Passage> {
        val out = ArrayList<Passage>()
        var cur = ArrayList<String>()
        var n = 0
        for (s in lines.flatMap(::sentences)) {
            val w = tokens(s).size
            if (cur.isNotEmpty() && n + w > max) {
                out += Passage(cur)
                cur = ArrayList()
                n = 0
            }
            cur += s
            n += w
        }
        if (cur.isNotEmpty()) out += Passage(cur)
        return out
    }

    // --- the alignment -------------------------------------------------------------------------------------------------

    /** A heard word: as the recognizer wrote it, its probability (null: not reported) and when it was said. */
    data class Heard(val text: String, val prob: Double? = null, val start: Double? = null, val end: Double? = null)

    /** How a word of the text lines up with what was heard. */
    internal enum class Op { EQ, BLOCK, SIM, OTHER, SKIP }

    /** Text word [i]'s alignment: its [op] and the heard words it stands for ([heard], indices; empty when skipped). */
    internal data class Step(val op: Op, val heard: List<Int>)

    private const val EQ = 0
    private const val BLOCK = 1
    private const val SIM = 4
    private const val OTHER = 8
    private const val GAP = 6

    internal fun key(w: String, lang: Lang): String = Grading.fold(Grading.normalize(w, lang), lang).replace(" ", "")

    private val digit = Regex("\\p{N}")

    /** Words a few letters apart ("rogavi", "rogovi"): a misreading of the word, not another word. */
    private fun similar(a: String, b: String): Boolean = Grading.distance(a, b) <= maxOf(1, maxOf(a.length, b.length) / 3)

    /**
     * Aligns the text's words ([text], keys) with the heard ones ([heard], keys) by edit distance over words: the same word
     * costs nothing, two words run together or split differently ("jebel", "speto živi") next to nothing, a similar word
     * a little, another word more, a word left out or added most. A number in the text is read by whatever stands for
     * it. Returns each text word's step.
     */
    internal fun align(text: List<String>, heard: List<String>): List<Step> {
        val n = text.size
        val m = heard.size
        fun sub(i: Int, j: Int): Int = when {
            text[i] == heard[j] -> EQ
            digit.containsMatchIn(text[i]) -> BLOCK // a number, said as words
            similar(text[i], heard[j]) -> SIM
            else -> OTHER
        }
        fun merge(i: Int, j: Int): Boolean = i >= 2 && j >= 1 && text[i - 2] + text[i - 1] == heard[j - 1]
        fun split(i: Int, j: Int): Boolean = i >= 1 && j >= 2 && text[i - 1] == heard[j - 2] + heard[j - 1]
        fun resplit(i: Int, j: Int): Boolean = i >= 2 && j >= 2 && text[i - 2] + text[i - 1] == heard[j - 2] + heard[j - 1] &&
            !(text[i - 2] == heard[j - 2] && text[i - 1] == heard[j - 1])
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = GAP * i
        for (j in 0..m) d[0][j] = GAP * j
        for (i in 1..n) for (j in 1..m) {
            var best = minOf(d[i - 1][j - 1] + sub(i - 1, j - 1), d[i - 1][j] + GAP, d[i][j - 1] + GAP)
            if (merge(i, j)) best = minOf(best, d[i - 2][j - 1] + BLOCK)
            if (split(i, j)) best = minOf(best, d[i - 1][j - 2] + BLOCK)
            if (resplit(i, j)) best = minOf(best, d[i - 2][j - 2] + BLOCK)
            d[i][j] = best
        }
        val steps = arrayOfNulls<Step>(n)
        var i = n
        var j = m
        while (i > 0) {
            when {
                j > 0 && d[i][j] == d[i - 1][j - 1] + sub(i - 1, j - 1) -> {
                    val c = sub(i - 1, j - 1)
                    steps[i - 1] = Step(if (c == EQ || c == BLOCK) (if (c == EQ) Op.EQ else Op.BLOCK) else if (c == SIM) Op.SIM else Op.OTHER, listOf(j - 1))
                    i--; j--
                }
                merge(i, j) && d[i][j] == d[i - 2][j - 1] + BLOCK -> {
                    steps[i - 1] = Step(Op.BLOCK, listOf(j - 1)); steps[i - 2] = Step(Op.BLOCK, listOf(j - 1))
                    i -= 2; j--
                }
                split(i, j) && d[i][j] == d[i - 1][j - 2] + BLOCK -> {
                    steps[i - 1] = Step(Op.BLOCK, listOf(j - 2, j - 1))
                    i--; j -= 2
                }
                resplit(i, j) && d[i][j] == d[i - 2][j - 2] + BLOCK -> {
                    steps[i - 1] = Step(Op.BLOCK, listOf(j - 2, j - 1)); steps[i - 2] = Step(Op.BLOCK, listOf(j - 2, j - 1))
                    i -= 2; j -= 2
                }
                j > 0 && d[i][j] == d[i][j - 1] + GAP -> j--
                else -> {
                    steps[i - 1] = Step(Op.SKIP, emptyList())
                    i--
                }
            }
        }
        return steps.map { it ?: Step(Op.SKIP, emptyList()) }
    }

    /** A final consonant said voiceless, as Slovene says it: "Zlatorog" sounds "Zlatorok" (the folded keys: ž is z, š is s). */
    private fun devoiced(w: String): String {
        if (w.isEmpty()) return w
        val last = when (w.last()) {
            'g' -> 'k'
            'd' -> 't'
            'b' -> 'p'
            'z' -> 's'
            else -> return w
        }
        return w.dropLast(1) + last
    }

    /**
     * [heard] is the text's word [text] (keys) with another ending: the same stem, the difference in the last letters
     * (Grading's endings: one letter on a short word, two on a longer one), both at least 3 letters, and not just a
     * final consonant said voiceless. "zlatim" / "zlatimi", "moja" / "moj".
     */
    internal fun endingSlip(text: String, heard: String): Boolean {
        if (text == heard || text.length < 3 || heard.length < 3) return false
        if (devoiced(text) == devoiced(heard)) return false
        if (abs(text.length - heard.length) > 2) return false
        val stem = text.length - if (text.length <= 5) 1 else 2
        var p = 0
        while (p < text.length && p < heard.length && text[p] == heard[p]) p++
        return p >= stem
    }

    private val lonePrepositions = setOf("v", "z", "s", "k")

    /** What a recognizer heard, word by word: Whisper's words (with their probability and time), else its text's. */
    fun heard(r: SttResult): List<Heard> =
        r.words.takeIf { it.isNotEmpty() }?.mapNotNull { w -> SttSpeech.core(w.word)?.let { Heard(w.word.trim(), w.prob, w.start, w.end) } }
            ?: tokens(r.text).map { Heard(it.text) }

    /** How long the reading in [r] took: from its first word to its last, else the recording's length. */
    fun seconds(r: SttResult): Double? {
        val ws = r.words.filter { it.start != null && it.end != null }
        if (ws.isNotEmpty()) return (ws.last().end!! - ws.first().start!!).takeIf { it > 0 }
        return r.duration?.takeIf { it > 0 }
    }

    /**
     * Grades [text] read aloud, heard by the node's Whisper: [prompted] (the text as its prompt) and [plain] (without;
     * null when that second pass failed: then the prompted one alone). See the class comment.
     */
    fun grade(text: String, prompted: SttResult, plain: SttResult?, lang: Lang = L10n.pair.target): Take {
        val t = tokens(text)
        val tk = t.map { key(it.core, lang) }
        // numbers the recognizer wrote as digits, in words as the text has them ("mi2" → "midva")
        val known = t.map { it.core.lowercase() }.toSet()
        fun spelled(r: SttResult) = heard(r).map { it.copy(text = SpokenNumbers.word(it.text, lang, known)) }
        val h = spelled(prompted)
        val hk = h.map { key(it.text, lang) }
        val steps = align(tk, hk)
        val second = plain?.let { p ->
            val ph = spelled(p)
            ph to align(tk, ph.map { key(it.text, lang) })
        }
        val sentence = sentenceIndex(text, t.size)
        val marks = t.mapIndexed { i, w ->
            val s = steps[i]
            val prob = s.heard.singleOrNull()?.let { h[it].prob }
            when (s.op) {
                Op.SKIP -> WordMark(w.text, sentence[i], Mark.SKIPPED)
                Op.SIM, Op.OTHER -> {
                    val said = h[s.heard.single()].text
                    WordMark(w.text, sentence[i], Mark.MISREAD, heard = SttSpeech.core(said) ?: said, ending = endingSlip(tk[i], hk[s.heard.single()]))
                }
                Op.EQ, Op.BLOCK -> {
                    val other = second?.let { (ph, ps) -> ps[i].let { st -> st to st.heard.singleOrNull()?.let { ph[it] } } }
                    val otherStep = other?.first
                    val otherWord = other?.second
                    val otherKey = otherWord?.let { key(it.text, lang) }
                    when {
                        // the plain transcript heard it with another ending: the prompt may have mended a slip
                        s.op == Op.EQ && otherStep != null && (otherStep.op == Op.SIM || otherStep.op == Op.OTHER) && otherKey != null && endingSlip(tk[i], otherKey) ->
                            WordMark(w.text, sentence[i], Mark.MISREAD, heard = SttSpeech.core(otherWord.text) ?: otherWord.text, ending = true)
                        // the prompt spelled a word Whisper didn't hear, and the plain transcript doesn't have it either
                        s.op == Op.EQ && prob != null && prob < PULLED && second != null && otherStep?.op != Op.EQ && otherStep?.op != Op.BLOCK ->
                            WordMark(w.text, sentence[i], Mark.MISREAD, heard = otherWord?.takeIf { otherStep?.op == Op.SIM }?.let { SttSpeech.core(it.text) ?: it.text })
                        else -> WordMark(w.text, sentence[i], Mark.RIGHT, unclear = prob != null && prob < SttSpeech.UNCLEAR && w.core.length >= 2)
                    }
                }
            }
        }
        val used = steps.flatMap { it.heard }.toSet()
        return Take(forgiveLonePrepositions(marks, t), h.indices.filter { it !in used }.map { h[it].text }, seconds(prompted), By.NODE)
    }

    /**
     * Grades [text] read aloud, heard by the phone's recognizer: the alternative that lines up best with the text
     * ([alternatives], most likely first); [seconds]: how long the learner read, when known.
     */
    fun gradePhone(text: String, alternatives: List<String>, seconds: Double? = null, lang: Lang = L10n.pair.target): Take? {
        val t = tokens(text)
        val tk = t.map { key(it.core, lang) }
        val sentence = sentenceIndex(text, t.size)
        return alternatives.filter { it.isNotBlank() }.map { alt ->
            val h = tokens(SpokenNumbers.inWords(alt, lang, text))
            val hk = h.map { key(it.core, lang) }
            val steps = align(tk, hk)
            val marks = t.mapIndexed { i, w ->
                val s = steps[i]
                when (s.op) {
                    Op.SKIP -> WordMark(w.text, sentence[i], Mark.SKIPPED)
                    Op.SIM, Op.OTHER -> WordMark(w.text, sentence[i], Mark.MISREAD, heard = h[s.heard.single()].core, ending = endingSlip(tk[i], hk[s.heard.single()]))
                    Op.EQ, Op.BLOCK -> WordMark(w.text, sentence[i], Mark.RIGHT)
                }
            }
            val used = steps.flatMap { it.heard }.toSet()
            Take(forgiveLonePrepositions(marks, t), h.indices.filter { it !in used }.map { h[it].text }, seconds, By.PHONE)
        }.maxByOrNull { it.right }
    }

    /** A one-letter preposition (v, z, s, k) the recognizer left out before a word read right: it ran into that word. */
    private fun forgiveLonePrepositions(marks: List<WordMark>, t: List<Token>): List<WordMark> = marks.mapIndexed { i, m ->
        if (m.mark == Mark.SKIPPED && t[i].core.lowercase() in lonePrepositions && marks.getOrNull(i + 1)?.mark == Mark.RIGHT) m.copy(mark = Mark.RIGHT, unclear = true)
        else m
    }

    /** The sentence (0, 1 …) of each of [text]'s [n] words. */
    private fun sentenceIndex(text: String, n: Int): List<Int> {
        val out = ArrayList<Int>(n)
        sentences(text).forEachIndexed { s, sentence -> repeat(tokens(sentence).size) { out += s } }
        while (out.size < n) out += (out.lastOrNull() ?: 0)
        return out.take(n)
    }

    // --- the score ----------------------------------------------------------------------------------------------------

    /** The sentences of [take] ([sentence] index → its words). */
    fun sentencesOf(take: Take): List<List<WordMark>> = take.words.groupBy { it.sentence }.toSortedMap().values.toList()

    /** A sentence is read well: at least [READ_WELL] of its words right. */
    fun readWell(words: List<WordMark>): Boolean = words.isNotEmpty() && words.count { it.mark == Mark.RIGHT } >= READ_WELL * words.size

    /** [takes] (the last take of each part read) in numbers. */
    fun score(takes: List<Take>): Score {
        val all = takes.flatMap { it.words }
        val sentences = takes.flatMap(::sentencesOf)
        val secs = takes.mapNotNull { it.seconds }.takeIf { it.size == takes.size && it.isNotEmpty() }?.sum()
        val spoken = all.count { it.mark != Mark.SKIPPED }
        val wpm = secs?.takeIf { it >= 1.0 }?.let { (spoken * 60 / it).roundToInt() }
        return Score(
            words = all.size,
            right = all.count { it.mark == Mark.RIGHT },
            misread = all.count { it.mark == Mark.MISREAD },
            skipped = all.count { it.mark == Mark.SKIPPED },
            sentences = sentences.size,
            readWell = sentences.count(::readWell),
            seconds = secs,
            wpm = wpm,
        )
    }

    /** The words read wrong in [takes], at most [max]: the text's word and what was heard (null: skipped). */
    fun misses(takes: List<Take>, max: Int = 20): List<Pair<String, String?>> =
        takes.flatMap { it.words }.filter { it.mark != Mark.RIGHT }.map { (SttSpeech.core(it.text) ?: it.text) to it.heard }.take(max)

    /** The sentences of [take] with a word read wrong, as the text has them: to hear again. */
    fun toHearAgain(take: Take): List<String> = sentencesOf(take).filter { s -> s.any { it.mark != Mark.RIGHT } }.map { s -> s.joinToString(" ") { it.text } }
}
