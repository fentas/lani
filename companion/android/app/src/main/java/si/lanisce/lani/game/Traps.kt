package si.lanisce.lani.game

import si.lanisce.lani.data.Grading
import si.lanisce.lani.data.MistakeNote
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogReply
import si.lanisce.lani.game.scene.tapTurn

/**
 * A form the learner mixed up: [right] where they had [wrong] (two forms of one word, or a word for another: "kavo" and
 * "kava", "vas" and "te"), from an [example] (what they had, what was right) of mistake [pattern] (mistakes-db).
 */
data class Confusion(val right: String, val wrong: String, val pattern: String, val example: Pair<String, String>)

/**
 * A turn's own trap: the wrong choice at [replace] (the file's index) gives way to [text], the learner's own [confusion],
 * testing [rule] (a grammar page, when known).
 */
data class Trap(val replace: Int, val text: String, val confusion: Confusion, val rule: String?)

/**
 * The learner's own traps (companion/SCENES.md, "Own traps"): where a turn or an exercise tests a form ([Forms.gap]), the
 * wrong choice shown is, when there is one, a form this learner actually mixed up before (their mistakes' examples),
 * else the file's. Pure.
 *
 * - **Their very word:** the right answer's form is one they had wrong before ("Nimam vžigalic." for a learner who
 *   wrote "nimam vžigalico"): the trap is what they wrote.
 * - **Their confusion on another word:** in a turn of the same rule (the mistake's pattern names the turn's grammar page),
 *   the same change of ending ("kavo" → "kava" makes "vodo" → "voda"), when the form it makes is a word known to be
 *   Slovene ([trap]'s `known`: the scenes' lines, the grammar book, the learner's words), never one made up.
 *
 * A mistake of spelling or of hearing ("Babiča", "še vedno" for "se vidimo") is no form: it sets no trap. When the file's
 * choices already have the learner's confusion, nothing changes. The trap takes the place of the form's wrong choice
 * ([Forms.Turn.wrong]) that tests the same rule, else of its last one; it keeps the turn's count of choices, and the
 * right ones where they are.
 */
object Traps {
    /** A mistakes-db category or subcategory that is no confusion of forms. */
    private val NO_FORMS = setOf("spelling", "listening", "pronunciation")

    /** A word this alike to another (1 − edit distance / the longer) is another form of it. */
    private const val ALIKE = 0.5

    /** The longest ending [ending] swaps. */
    private const val MAX_ENDING = 3

    /**
     * The learner's confusions, the most frequent mistakes first and each one's latest example first; each with the part of
     * its example it is in (what they had, what was right: "Midva je" for "Midva sva").
     */
    fun confusions(mistakes: List<MistakeNote>): List<Confusion> =
        mistakes
            .filter { it.category?.lowercase() !in NO_FORMS && it.subcategory?.lowercase() !in NO_FORMS }
            .sortedByDescending { it.frequency }
            .flatMap { m ->
                m.examples.reversed().flatMap { ex ->
                    parts(ex.first, ex.second).flatMap { (w, r) -> swapped(r, w).map { (a, b) -> Confusion(a, b, m.id, w to r) } }
                }
            }
            .distinctBy { Triple(it.right.lowercase(), it.wrong.lowercase(), it.pattern) }

    /**
     * The words an example ([wrong] had, [right] right) swapped: (right word, wrong word). The example's notes in brackets
     * go; "a / b" examples pair up part by part. Words aligned in order: a lone word swapped between two that match ("To
     * je **dobra** partnerka" for "To je **moja** partnerka") is a pair whatever it is; in a longer stretch, words alike
     * ([ALIKE]) pair up ("Lahko **ena kava**?" for "Lahko dobim **eno kavo**?").
     */
    fun pairs(wrong: String, right: String): List<Pair<String, String>> = parts(wrong, right).flatMap { (w, r) -> swapped(r, w) }

    /** An example's parts, (had, right), without its notes in brackets: "a / b" part by part when both have as many. */
    private fun parts(wrong: String, right: String): List<Pair<String, String>> {
        val w = clean(wrong).split(" / ").map { it.trim() }
        val r = clean(right).split(" / ").map { it.trim() }
        return if (w.size == r.size) w.zip(r) else listOf(clean(wrong) to clean(right))
    }

    private fun swapped(right: String, wrong: String): List<Pair<String, String>> =
        aligned(tokens(right), tokens(wrong)).filter { (a, b) -> a.lowercase() != b.lowercase() }

    private fun clean(s: String) = s.replace(Regex("""\s*\([^()]*\)"""), "").trim()

    private fun tokens(s: String): List<String> = Regex("""[\p{L}\p{N}]+""").findAll(s).map { it.value }.toList()

    /** Pairs of [r]'s and [w]'s words swapped, by a longest common subsequence and what lies between its words. */
    private fun aligned(r: List<String>, w: List<String>): List<Pair<String, String>> {
        val a = r.map { it.lowercase() }
        val b = w.map { it.lowercase() }
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val out = mutableListOf<Pair<String, String>>()
        var i = 0
        var j = 0
        var gi = 0
        var gj = 0
        fun gap(toI: Int, toJ: Int) {
            val rs = r.subList(gi, toI)
            val ws = w.subList(gj, toJ)
            if (rs.size == 1 && ws.size == 1) out += rs[0] to ws[0]
            else out += alike(rs, ws)
        }
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> {
                    gap(i, j)
                    i++; j++
                    gi = i; gj = j
                }
                lcs[i + 1][j] >= lcs[i][j + 1] -> i++
                else -> j++
            }
        }
        gap(a.size, b.size)
        return out
    }

    /** In a stretch of words swapped, the pairs alike enough to be forms of one word, in order. */
    private fun alike(r: List<String>, w: List<String>): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        var from = 0
        for (x in r) {
            val k = (from until w.size).firstOrNull { likeness(x, w[it]) >= ALIKE } ?: continue
            out += x to w[k]
            from = k + 1
        }
        return out
    }

    private fun likeness(a: String, b: String): Double {
        val x = Grading.fold(a)
        val y = Grading.fold(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return 1.0 - Grading.distance(x, y).toDouble() / maxOf(x.length, y.length)
    }

    private fun same(a: String, b: String) = Grading.fold(a) == Grading.fold(b)

    /** [w] written like [model]: capitalized when it is. */
    private fun caseOf(model: String, w: String): String =
        if (model.firstOrNull()?.isUpperCase() == true) w.replaceFirstChar { it.uppercase() } else w.lowercase()

    /**
     * [word] with [c]'s change of ending ("kavo" → "kava": -o → -a, "Berlina" → "Berlin": -a dropped): when [c]'s
     * words share a stem of two letters or more and swap an ending of at most [MAX_ENDING] letters that [word] has, with
     * a stem of three letters or more left. Null otherwise.
     */
    fun ending(word: String, c: Confusion): String? {
        val r = c.right.lowercase()
        val w = c.wrong.lowercase()
        var p = 0
        while (p < r.length && p < w.length && r[p] == w[p]) p++
        val from = r.substring(p)
        val to = w.substring(p)
        if (p < 2 || from.isEmpty() || from.length > MAX_ENDING || to.length > MAX_ENDING) return null
        if (!word.lowercase().endsWith(from) || word.length - from.length < 3) return null
        return (word.dropLast(from.length) + to).takeIf { it.lowercase() != word.lowercase() }
    }

    /**
     * The own trap for choices [texts] (the right one at [right]; [rules]: the grammar page each wrong choice tests, null
     * where not known), from the learner's [confusions] ([pagesOf]: the pages a mistake pattern is on, by the pages'
     * tags); [known]: whether a word made by [ending] is one. Null when the turn tests no form, nothing fits, or the file
     * has the learner's confusion already.
     */
    fun trap(
        texts: List<String>,
        right: Int,
        rules: List<String?>,
        confusions: List<Confusion>,
        pagesOf: (String) -> Set<String>,
        known: (String) -> Boolean,
    ): Trap? {
        if (confusions.isEmpty()) return null
        val form = Forms.turn(texts, right) ?: return null
        val gap = form.gap
        // the rules the form's wrong choices test, and those choices (else any wrong one) to give way
        val tested = form.wrong.mapNotNull { rules.getOrNull(it) }.toSet()
        val wrong = form.wrong
        for (c in confusions) {
            val matched = pagesOf(c.pattern).firstOrNull { it in tested }
            // their very word; in a turn whose rules are known, one of them the mistake's
            val word = when {
                same(gap.word, c.right) && (tested.isEmpty() || matched != null) -> caseOf(gap.word, c.wrong)
                matched != null -> ending(gap.word, c)?.takeIf { known(it.lowercase()) }
                else -> null
            } ?: continue
            val text = Forms.replaceAt(texts[right], gap.slot, word) ?: continue
            if (texts.any { same(it, text) }) return null // the file shows it already
            val replace = wrong.firstOrNull { matched != null && rules.getOrNull(it) == matched } ?: wrong.last()
            return Trap(replace, text, c, matched ?: rules.getOrNull(replace))
        }
        return null
    }

    /**
     * [d] with the learner's own traps in its form turns ([trap]): each takes a wrong choice's place, meaning what the
     * right one means, with [why] (what the learner mixed up) and the turn's puzzled reaction (its `puzzled`, else
     * [puzzled]). A turn of more than one right choice tests no form; the lines of [skip] (a turn of a rule not introduced
     * yet: [Introduction]) get none.
     */
    fun dialog(
        d: Dialog,
        confusions: List<Confusion>,
        pagesOf: (String) -> Set<String>,
        known: (String) -> Boolean,
        puzzled: DialogReply,
        why: (Confusion) -> String,
        skip: Set<Int> = emptySet(),
    ): Dialog {
        if (confusions.isEmpty()) return d
        var changed = false
        val lines = d.lines.mapIndexed { i, line ->
            val choices = line.choices
            if (i in skip) return@mapIndexed line // a rule not yet: its form isn't asked for, so no trap of it
            if (line.tapTurn) return@mapIndexed line // each choice a place in the picture: no form to trap
            val right = choices.indexOfFirst { it.ok }.takeIf { r -> r >= 0 && choices.count { it.ok } == 1 } ?: return@mapIndexed line
            val t = trap(choices.map { it.sl }, right, choices.map { it.rule(line) }, confusions, pagesOf, known) ?: return@mapIndexed line
            changed = true
            // the page it tests: one the scene names (its why then leads there), else only a guess, like the others'
            val named = t.rule?.takeIf { r -> choices.any { (it.grammar ?: line.grammar) == r } }
            val own = DialogChoice(
                sl = t.text, en = choices[right].en, ok = false, why = why(t.confusion),
                reply = line.puzzled ?: puzzled, grammar = named, guess = t.rule.takeIf { named == null },
            )
            line.copy(choices = choices.toMutableList().also { it[t.replace] = own })
        }
        return if (changed) d.copy(lines = lines) else d
    }

    /**
     * An exercise's options with the learner's own trap in place of a wrong one ([trap]; every wrong option tests the
     * exercise's [rule]); the same options when none fits.
     */
    fun options(
        options: List<String>,
        answer: Int,
        rule: String?,
        confusions: List<Confusion>,
        pagesOf: (String) -> Set<String>,
        known: (String) -> Boolean,
    ): List<String> {
        val t = trap(options, answer, options.map { rule }, confusions, pagesOf, known) ?: return options
        return options.toMutableList().also { it[t.replace] = t.text }
    }
}
