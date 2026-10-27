package si.lanisce.lani.data

import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang

/**
 * What was typed set against what is right, as a correction shows it at its item (a worksheet's line, gap or cell, an
 * exercise's feedback): word by word, and within a word that is near, letter by letter, so the eye compares in one place:
 * typed "spive v sotoru", right "Spita v šotoru." → "Spi~~ve~~**ta** v ~~s~~**š**otoru.". Compared as [Grading] compares:
 * case, punctuation and the kind of apostrophe don't count (what is right shows as it is written, its full stop too);
 * a letter the keyboard lacks (c typed for č) is a difference, a small one, as the grading's almost is.
 */
object AnswerDiff {
    /**
     * A piece of a correction: [SAME] typed as it is right (shown as the right answer writes it), [TYPED] typed and not
     * right (struck through), [RIGHT] what is right instead (green and bold), [GIVEN] a word of the right answer that may
     * be left out and was (the person in brackets of a transform's line: shown quietly, no mistake), [GAP] between words.
     */
    enum class Kind { SAME, TYPED, RIGHT, GIVEN, GAP }

    data class Piece(val kind: Kind, val text: String)

    /** What the grading leaves out of a word (Grading's punctuation). */
    private val punctuation = Regex("[.,!?;:¡¿\"„“”«»]")

    /** A word: what isn't a space or a list's middle dot (Grading reads the dot as a space). */
    private val word = Regex("[^\\s·]+")

    private val apostrophes = "’ʼ`´"

    /** A word with its punctuation around it kept apart: what is compared is [core]. */
    private class Word(val text: String, val lead: String, val core: String, val trail: String, val before: String)

    private fun words(s: String): List<Word> {
        var end = 0
        return word.findAll(s).mapNotNull { m ->
            val t = m.value
            val before = s.substring(end, m.range.first)
            end = m.range.last + 1
            val start = t.indexOfFirst { !punctuation.matches(it.toString()) }
            if (start < 0) return@mapNotNull Word(t, t, "", "", before)
            val stop = t.indexOfLast { !punctuation.matches(it.toString()) } + 1
            Word(t, t.substring(0, start), t.substring(start, stop), t.substring(stop), before)
        }.toList()
    }

    /** Two letters the same as the grading has them: whatever their case, any apostrophe for another. */
    private fun same(a: Char, b: Char): Boolean {
        val x = if (a in apostrophes) '\'' else a.lowercaseChar()
        val y = if (b in apostrophes) '\'' else b.lowercaseChar()
        return x == y
    }

    private fun same(a: String, b: String): Boolean = a.length == b.length && a.indices.all { same(a[it], b[it]) }

    /**
     * [typed] set against [expected] (the right answer, as written); its first [optional] words may be left out (the
     * person in brackets: missing, they show as [Kind.GIVEN]). Mostly not what is right (fewer than half of its words
     * typed at least nearly): the whole typed struck through, then the whole right answer, no patchwork of both.
     */
    fun of(typed: String, expected: String, optional: Int = 0, lang: Lang = L10n.pair.target): List<Piece> {
        val e = words(expected)
        val t = words(typed).filter { it.core.isNotEmpty() }
        val cores = e.filter { it.core.isNotEmpty() }
        if (cores.isEmpty()) return merge(listOfNotNull(typed.trim().takeIf { it.isNotEmpty() }?.let { Piece(Kind.TYPED, it) }))
        if (t.isEmpty()) return merge(e.flatMapIndexed { i, w -> missing(w, i, optional, gapDone = i == 0) })
        val ops = align(e, t, optional, lang)
        val near = ops.count { it is Op.Match || (it is Op.Sub && inWord(it.e.core, it.t.core) != null) }
        if (near * 2 < cores.size) {
            return merge(listOf(Piece(Kind.TYPED, t.joinToString(" ") { it.core }), Piece(Kind.GAP, " → "), Piece(Kind.RIGHT, expected.trim())))
        }
        val out = mutableListOf<Piece>()
        var first = true
        for (op in ops) {
            val gap = if (first) "" else when (op) {
                is Op.Del -> " "
                is Op.Match -> op.e.before.gapOr()
                is Op.Sub -> op.e.before.gapOr()
                is Op.Ins -> op.e.before.gapOr()
            }
            if (gap.isNotEmpty()) out += Piece(Kind.GAP, gap)
            first = false
            when (op) {
                is Op.Match -> out += Piece(Kind.SAME, op.e.text)
                is Op.Ins -> out += missing(op.e, op.at, optional, gapDone = true)
                is Op.Del -> out += Piece(Kind.TYPED, op.t.core)
                is Op.Sub -> {
                    if (op.e.lead.isNotEmpty()) out += Piece(Kind.SAME, op.e.lead)
                    val split = inWord(op.e.core, op.t.core)
                    if (split != null) {
                        val (p, s) = split
                        out += Piece(Kind.SAME, op.e.core.substring(0, p))
                        out += Piece(Kind.TYPED, op.t.core.substring(p, op.t.core.length - s))
                        out += Piece(Kind.RIGHT, op.e.core.substring(p, op.e.core.length - s))
                        out += Piece(Kind.SAME, op.e.core.substring(op.e.core.length - s))
                    } else {
                        out += Piece(Kind.TYPED, op.t.core)
                        out += Piece(Kind.GAP, " ")
                        out += Piece(Kind.RIGHT, op.e.core)
                    }
                    if (op.e.trail.isNotEmpty()) out += Piece(Kind.SAME, op.e.trail)
                }
            }
        }
        return merge(out)
    }

    /** Whether [pieces] show a difference at all (something struck through or put in). */
    fun differs(pieces: List<Piece>): Boolean = pieces.any { it.kind == Kind.TYPED || it.kind == Kind.RIGHT }

    /** A space between two words, or the list's " · " where the right answer has it. */
    private fun String.gapOr(): String = if ('·' in this) " · " else " "

    /** Expected word [w] (at [i]) not typed: what is right, green; one of the first [optional] quiet. [gapDone]: the gap before it is out already. */
    private fun missing(w: Word, i: Int, optional: Int, gapDone: Boolean): List<Piece> {
        val pieces = mutableListOf<Piece>()
        if (!gapDone && w.before.isNotEmpty() && i > 0) pieces += Piece(Kind.GAP, w.before.gapOr())
        if (w.core.isEmpty()) return pieces + Piece(Kind.SAME, w.text)
        if (i < optional) return pieces + Piece(Kind.GIVEN, w.text)
        if (w.lead.isNotEmpty()) pieces += Piece(Kind.SAME, w.lead)
        pieces += Piece(Kind.RIGHT, w.core)
        if (w.trail.isNotEmpty()) pieces += Piece(Kind.SAME, w.trail)
        return pieces
    }

    /**
     * A word typed near the right one ([expected], [typed]): how much of each both start and end with ([Pair] of
     * lengths), when that is at least half the right word (and two letters); null when it is another word (shown whole).
     */
    private fun inWord(expected: String, typed: String): Pair<Int, Int>? {
        var p = 0
        while (p < expected.length && p < typed.length && same(expected[p], typed[p])) p++
        var s = 0
        while (s < expected.length - p && s < typed.length - p && same(expected[expected.length - 1 - s], typed[typed.length - 1 - s])) s++
        val need = maxOf(2, (expected.length + 1) / 2)
        return if (p + s >= need) p to s else null
    }

    private sealed interface Op {
        class Match(val e: Word, val at: Int) : Op
        class Sub(val e: Word, val t: Word, val at: Int) : Op
        class Ins(val e: Word, val at: Int) : Op
        class Del(val t: Word) : Op
    }

    /** What leaving out a right word costs, typing one too many, and leaving out one that may be (the person). */
    private const val MISSING = 1.0
    private const val EXTRA = 1.0
    private const val LEFT_OUT = 0.05

    /**
     * Typed word [t] in place of right word [e]: nothing when the same, little when only the keyboard's letters differ (č
     * typed c), more the further apart they are; another word costs a little less than leaving one out and typing one
     * too many, so it stands where the right one would.
     */
    private fun cost(e: Word, t: Word, lang: Lang): Double {
        if (same(e.core, t.core)) return 0.0
        val a = Grading.fold(e.core, lang)
        val b = Grading.fold(t.core, lang)
        if (a == b) return 0.1
        val r = Grading.distance(a, b).toDouble() / maxOf(a.length, b.length, 1)
        return if (r <= 0.67) 0.2 + 0.8 * r else MISSING + EXTRA - 0.1
    }

    /** The cheapest way from what was typed to what is right, word by word, in the right answer's order. */
    private fun align(e: List<Word>, t: List<Word>, optional: Int, lang: Lang): List<Op> {
        val n = e.size
        val m = t.size
        fun ins(i: Int) = if (e[i].core.isEmpty()) 0.0 else if (i < optional) LEFT_OUT else MISSING
        val d = Array(n + 1) { DoubleArray(m + 1) }
        for (i in 1..n) d[i][0] = d[i - 1][0] + ins(i - 1)
        for (j in 1..m) d[0][j] = d[0][j - 1] + EXTRA
        for (i in 1..n) for (j in 1..m) {
            val sub = if (e[i - 1].core.isEmpty()) Double.MAX_VALUE / 4 else d[i - 1][j - 1] + cost(e[i - 1], t[j - 1], lang)
            d[i][j] = minOf(sub, d[i - 1][j] + ins(i - 1), d[i][j - 1] + EXTRA)
        }
        val ops = ArrayDeque<Op>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            val here = d[i][j]
            when {
                i > 0 && j > 0 && e[i - 1].core.isNotEmpty() && near(here, d[i - 1][j - 1] + cost(e[i - 1], t[j - 1], lang)) -> {
                    ops.addFirst(if (same(e[i - 1].core, t[j - 1].core)) Op.Match(e[i - 1], i - 1) else Op.Sub(e[i - 1], t[j - 1], i - 1))
                    i--; j--
                }
                i > 0 && near(here, d[i - 1][j] + ins(i - 1)) -> {
                    ops.addFirst(if (e[i - 1].core.isEmpty()) Op.Match(e[i - 1], i - 1) else Op.Ins(e[i - 1], i - 1))
                    i--
                }
                else -> {
                    ops.addFirst(Op.Del(t[j - 1]))
                    j--
                }
            }
        }
        return ops
    }

    private fun near(a: Double, b: Double) = kotlin.math.abs(a - b) < 1e-9

    /** Neighbours of one kind as one piece, no empty ones, no gap at either end. */
    private fun merge(pieces: List<Piece>): List<Piece> {
        val out = mutableListOf<Piece>()
        for (p in pieces) {
            if (p.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && last.kind == p.kind) out[out.size - 1] = Piece(p.kind, last.text + p.text) else out += p
        }
        while (out.firstOrNull()?.kind == Kind.GAP) out.removeAt(0)
        while (out.lastOrNull()?.kind == Kind.GAP) out.removeAt(out.size - 1)
        return out
    }
}
