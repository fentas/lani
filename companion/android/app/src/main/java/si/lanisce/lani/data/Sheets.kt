package si.lanisce.lani.data

import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import kotlin.random.Random

/**
 * One item of a worksheet exercise (companion/COURSE.md, "The school's exercise types"): a gap, a cell to fill, a line of
 * a transform list, a word to sort. [accept]: what is right (a sorted word's: its column's label; a transform line's,
 * the answers written and those without the person in brackets, [Sheets.withoutPerson]); [grammar]: its rule (its own,
 * else the exercise's); [frame]: the sentence it stands in with `___` where the answer goes ("Babica ___ v kuhinji."),
 * what a slip on its rule keeps; [lemma] and [form]: the word's form it practises (the forms book's key); [person]: the
 * person in brackets the answer may leave out ("Luka in Tine"), and [left] each answer without it that is the whole one
 * with only the person left out ("spita v šotoru" → "Luka in Tine spita v šotoru.", compared as [Grading.normalize]s).
 */
data class SheetItem(
    val accept: List<String>,
    val grammar: String?,
    val hint: String? = null,
    val explain: String? = null,
    val frame: String = Sheets.BLANK,
    val lemma: String? = null,
    val form: String? = null,
    val person: String? = null,
    val left: Map<String, String> = emptyMap(),
) {
    /** [frame] with [answer] in its gap. */
    fun said(answer: String): String = frame.replaceFirst(Sheets.BLANK, answer.trim())
}

/**
 * An item checked: its [verdict], what was given ([answer]) and what is right ([expected]), the grading's hint and marks;
 * [hinted]: its 💡 was opened (a right answer then pays like an almost, [Hints.paid]); [lookedUp]: it was answered after
 * the cheat sheet was opened over the sheet (right with help: it pays in full, the tutor hears of it).
 */
data class ItemMark(
    val verdict: Verdict,
    val answer: String,
    val expected: String,
    val hint: Grading.Hint = Grading.Hint.NONE,
    val marks: List<IntRange> = emptyList(),
    val hinted: Boolean = false,
    val lookedUp: Boolean = false,
) {
    /** What it pays as: a right one after its hint as an almost. */
    val paid: Verdict get() = Hints.paid(verdict, if (hinted) 1 else 0)
}

/**
 * A worksheet exercise's answer on one rule (the grammar book's page): [verdict] right only when every item of the rule
 * was, wrong when one was wrong; [said] a sentence said right (the page's "Tvoji stavki"), the first slip's [answer] and
 * [correct] (its "Tvoje napake").
 */
data class RuleAnswer(val rule: String, val verdict: Verdict, val said: String?, val answer: String, val correct: String?)

/**
 * The school's worksheet types, pure (companion/COURSE.md): their items, how each is graded (the app's [Grading]: č, š, ž
 * forgotten is almost, a slip in the stem forgiven, never the ending), how a sheet adds up, and what it counts on the
 * grammar book's rules.
 */
object Sheets {
    /** Where an answer goes in a [SheetItem.frame]. */
    const val BLANK = "___"

    /** A gap in a gap text: three underscores or more. */
    val GAP = Regex("_{3,}")

    /** An exercise of the school's own four types. */
    fun isSheet(ex: Exercise): Boolean = ex is Exercise.GapText || ex is Exercise.Table || ex is Exercise.Transform || ex is Exercise.Sort

    /** [text] cut at its gaps: one part more than it has gaps. */
    fun parts(text: String): List<String> = text.split(GAP)

    /** The gap text with [answers] in its gaps (a gap with none keeps its `___`). */
    fun filled(ex: Exercise.GapText, answers: List<String?>): String {
        val p = parts(ex.text)
        return buildString {
            p.forEachIndexed { i, s ->
                append(s)
                if (i < p.size - 1) append(answers.getOrNull(i)?.takeIf { it.isNotBlank() } ?: BLANK)
            }
        }
    }

    /** The sentence of [text] around [at] (a character index): from the end of the one before to its own end. */
    internal fun sentenceAt(text: String, at: Int): String {
        val ends = Regex("""[.!?…]+["»“”]?(\s+|$)""").findAll(text).map { it.range.last + 1 }.toList()
        val start = ends.lastOrNull { it <= at } ?: 0
        val end = ends.firstOrNull { it > at } ?: text.length
        return text.substring(start, end).trim()
    }

    /** The items of [ex] in order (in [lang], the language learned); none for an exercise that isn't one of the four. */
    fun items(ex: Exercise, lang: Lang = L10n.pair.target): List<SheetItem> = when (ex) {
        is Exercise.GapText -> {
            val p = parts(ex.text)
            ex.gaps.mapIndexed { i, g ->
                // the sentence of the gap, the other gaps filled with their answers
                val others = ex.gaps.mapIndexed { j, o -> if (j == i) null else o.accept.firstOrNull() }
                val before = buildString { for (k in 0..i) { append(p.getOrElse(k) { "" }); if (k < i) append(others[k] ?: BLANK) } }
                val whole = filled(ex, others.mapIndexed { j, a -> if (j == i) MARK else a })
                val frame = sentenceAt(whole, before.length).replace(MARK, BLANK)
                SheetItem(g.accept, g.grammar ?: ex.grammar, g.hint, g.explain, frame)
            }
        }
        is Exercise.Table -> ex.rows.flatMap { row ->
            row.cells.mapIndexedNotNull { c, cell ->
                if (!cell.toFill) return@mapIndexedNotNull null
                val column = ex.columns.getOrNull(c)?.takeIf { ex.columns.size > 1 }
                val frame = "${row.label}: $BLANK" + (column?.let { " ($it)" } ?: "")
                SheetItem(cell.accept, cell.grammar ?: ex.grammar, cell.hint, cell.explain, frame, ex.lemma, cell.form)
            }
        }
        is Exercise.Transform -> ex.items.map { line ->
            val without = if (ex.whole) emptyList() else withoutPerson(line.from, line.accept, lang)
            SheetItem(
                line.accept + without.map { it.answer }, line.grammar ?: ex.grammar, line.hint, line.explain, BLANK, line.lemma, line.form,
                person = without.firstOrNull()?.let { person(line.from) },
                left = without.filter { it.dropped }.associate { Grading.normalize(it.answer, lang) to it.whole },
            )
        }
        is Exercise.Sort -> ex.columns.flatMap { col -> col.words.map { w -> SheetItem(listOf(col.label), ex.grammar, frame = "$w → $BLANK") } }
        else -> emptyList()
    }

    private const val MARK = "\u0000"

    /** Brackets at the end of a line: what they hold. */
    private val bracketsAtEnd = Regex("""\s*\(([^()]+)\)\s*$""")

    /**
     * The person in brackets at the end of a transform's line ("Spim v šotoru. (Luka in Tine)" → "Luka in Tine"): words
     * of letters only; null when there are no brackets or they hold something else (a number, a question, a note: "(2)",
     * "(where?)", "(ne moči: can't)").
     */
    fun person(from: String): String? = bracketsAtEnd.find(from)?.groupValues?.get(1)?.trim()
        ?.takeIf { b -> b.isNotEmpty() && b.any { it.isLetter() } && b.all { it.isLetter() || it == ' ' || it == '\'' || it == '-' } }

    /** A transform's line without its brackets at the end ("Spim v šotoru."). */
    fun given(from: String): String = from.replace(bracketsAtEnd, "").trim()

    /**
     * An answer right without the person in brackets ([answer]), from accepted answer [whole]; [dropped]: it is [whole]
     * with only the person left out, in its order ("Spita v šotoru."), else with the line's own order too ("Uči se
     * brati." from "Tine se uči brati.").
     */
    data class Without(val answer: String, val whole: String, val dropped: Boolean)

    /**
     * What else is right for a transform's line [from] whose accepted answers [accept] begin with the person in brackets
     * (companion/COURSE.md, "transform"): in a language that leaves its subject out ([Spelling.dropSubject]: the verb's
     * ending says the person), when the line itself has none, the answer may too: "Pečem kruh. (babica)" → "Babica peče
     * kruh." and "Peče kruh."; and with the line's own order of its words where only the person put them elsewhere: "Učim
     * se brati. (Tine)" → "Tine se uči brati.", "Se uči brati." and "Uči se brati.". Only when the line has as many words
     * as the answer without the person (the person was put in front, not in place of a word of the line: "Kruh je svež.
     * (mleko)" → "Mleko je sveže." needs its noun), and a word of the line changed, not only their order (else the bracket
     * is what the line is to take in: "Pomagam. (lahko)" → "Lahko pomagam.", "Prideš jutri? (lahko)" → "Lahko jutri
     * prideš?"). None already accepted. A word in brackets that isn't the person but goes in front while the verb changes
     * ("Grem domov. (jutri)" → "Jutri pojdem domov.") can't be told from one: such a list says `whole`.
     */
    fun withoutPerson(from: String, accept: List<String>, lang: Lang = L10n.pair.target): List<Without> {
        if (!Spelling.of(lang).dropSubject) return emptyList()
        val who = person(from) ?: return emptyList()
        val whoWords = Grading.normalize(who, lang).split(' ')
        val line = Grading.normalize(given(from), lang).split(' ').filter { it.isNotEmpty() }
        val known = accept.map { Grading.canonical(it, lang) }.toMutableSet()
        val out = mutableListOf<Without>()
        for (a in accept) {
            val words = a.trim().split(Regex("\\s+"))
            if (words.size <= whoWords.size) continue
            if (words.take(whoWords.size).map { Grading.normalize(it, lang) } != whoWords) continue
            val rest = words.drop(whoWords.size)
            val restKey = rest.map { Grading.normalize(it, lang) }
            // a word of the line changed (the verb's person), not only their order: else the bracket is what it takes in
            if (restKey.size != line.size || restKey.sorted() == line.sorted()) continue
            val capital = a.trim().firstOrNull()?.isUpperCase() == true
            fun add(ws: List<String>, dropped: Boolean) {
                val text = sentence(ws, capital)
                if (known.add(Grading.canonical(text, lang))) out += Without(text, a.trim(), dropped)
            }
            add(rest, dropped = true)
            mirrored(rest, line, lang)?.let { add(it, dropped = false) }
        }
        return out
    }

    /**
     * [rest] (an answer's words without its person) in the order of the [line]'s words: the words both have where the
     * line has them, the answer's others in the places left, in their order ("se uči brati." by "učim se brati" → "uči se
     * brati."); null when it is the same order, or they share no word.
     */
    private fun mirrored(rest: List<String>, line: List<String>, lang: Lang): List<String>? {
        val trail = rest.last().takeLastWhile { !it.isLetterOrDigit() }
        val bare = rest.mapIndexed { i, w -> if (i == rest.size - 1) w.dropLast(trail.length) else w }
        val keys = bare.map { Grading.normalize(it, lang) }
        val used = BooleanArray(bare.size)
        val placed = arrayOfNulls<String>(line.size)
        line.forEachIndexed { k, w ->
            val i = keys.indices.firstOrNull { !used[it] && keys[it] == w } ?: return@forEachIndexed
            used[i] = true
            placed[k] = bare[i]
        }
        if (used.none { it }) return null
        val others = bare.indices.filter { !used[it] }.map { bare[it] }.iterator()
        val order = placed.map { it ?: others.next() }
        if (order == bare) return null
        return order.mapIndexed { i, w -> if (i == order.size - 1) w + trail else w }
    }

    /** [words] as a sentence: its first letter a capital when the answer's was ([capital]). */
    private fun sentence(words: List<String>, capital: Boolean): String {
        val s = words.joinToString(" ")
        return if (capital) s.replaceFirstChar { it.uppercaseChar() } else s
    }

    /**
     * What item [item]'s correction is set against once checked ([mark]): its right answer, and how many of its first
     * words may be left out (the person in brackets). An answer without the person picked by the grading ([SheetItem.left])
     * shows as the whole one, the person quiet before it ("Luka in Tine sp~~ive~~**ita** v šotoru."); a whole one picked
     * as it is, its person likewise free to be left out.
     */
    fun shown(item: SheetItem, mark: ItemMark, lang: Lang = L10n.pair.target): Pair<String, Int> {
        val who = item.person ?: return mark.expected to 0
        val n = who.split(' ').count { it.isNotBlank() }
        item.left[Grading.normalize(mark.expected, lang)]?.let { return it to n }
        val starts = mark.expected.trim().split(Regex("\\s+")).take(n).map { Grading.normalize(it, lang) } == Grading.normalize(who, lang).split(' ')
        return mark.expected to if (starts) n else 0
    }

    /** The words of a sort, shuffled the same way each time it is shown ([seed]). */
    fun sortWords(ex: Exercise.Sort, seed: Long = ex.columns.hashCode().toLong()): List<String> =
        ex.columns.flatMap { it.words }.shuffled(Random(seed))

    /** A gap text's word bank: each gap's first answer and the distractors, shuffled the same way each time; none without [Exercise.GapText.bank]. */
    fun bank(ex: Exercise.GapText, seed: Long = ex.text.hashCode().toLong()): List<String> =
        if (!ex.bank) emptyList() else (ex.gaps.mapNotNull { it.accept.firstOrNull() } + ex.distractors).shuffled(Random(seed))

    /** A typed item checked as the app checks a cloze ([Grading.check]). */
    fun check(item: SheetItem, answer: String): ItemMark {
        val r = Grading.check(answer, item.accept)
        return ItemMark(r.verdict, answer.trim(), r.expected, r.hint, r.marks)
    }

    /** A sorted word: in its column ([placed], an index into [columns]) or not; one not placed is wrong. */
    fun sorted(ex: Exercise.Sort, word: String, placed: Int?): ItemMark {
        val right = ex.columns.indexOfFirst { word in it.words }
        val label = ex.columns.getOrNull(right)?.label.orEmpty()
        return ItemMark(if (placed == right) Verdict.CORRECT else Verdict.WRONG, placed?.let { ex.columns.getOrNull(it)?.label }.orEmpty(), label)
    }

    /** How a sheet went as a whole: right when every item was, almost from [ALMOST_SHARE] of its score, else not quite. */
    fun verdict(items: List<Verdict>): Verdict = when {
        items.isEmpty() -> Verdict.WRONG
        items.all { it == Verdict.CORRECT } -> Verdict.CORRECT
        score(items) >= ALMOST_SHARE * items.size -> Verdict.ALMOST
        else -> Verdict.WRONG
    }

    /** A sheet that is almost right: this share of its items' score. */
    const val ALMOST_SHARE = 0.7

    /** Right items, an almost one counting half. */
    fun score(items: List<Verdict>): Double = items.count { it == Verdict.CORRECT } + 0.5 * items.count { it == Verdict.ALMOST }

    /** Whether [items] reach [pass] percent ([score] of all of them). */
    fun passed(items: List<Verdict>, pass: Int): Boolean = items.isNotEmpty() && score(items) * 100 >= pass * items.size - 1e-9

    /** "8/10" (an almost counting half: "8½/10"). */
    fun scoreText(items: List<Verdict>): String {
        val s = score(items)
        val whole = s.toInt()
        return (if (s - whole >= 0.5) "$whole½" else "$whole") + "/${items.size}"
    }

    /**
     * What a checked sheet counts on the grammar book (companion/GAME.md, "The school"): one answer on each rule its items
     * name, in the order they come, as one exercise would ([si.lanisce.lani.game.GrammarBook.answered]): right when every
     * item of the rule was right, wrong when one was wrong (its first slip kept), else almost.
     */
    fun ruleAnswers(items: List<SheetItem>, marks: List<ItemMark>, sentences: Boolean): List<RuleAnswer> {
        val byRule = LinkedHashMap<String, MutableList<Int>>()
        items.forEachIndexed { i, it -> it.grammar?.let { g -> byRule.getOrPut(g) { mutableListOf() } += i } }
        return byRule.map { (rule, idx) ->
            val ms = idx.mapNotNull { marks.getOrNull(it) }
            val verdict = when {
                ms.isNotEmpty() && ms.all { it.verdict == Verdict.CORRECT } -> Verdict.CORRECT
                ms.isEmpty() || ms.any { it.verdict == Verdict.WRONG } -> Verdict.WRONG
                else -> Verdict.ALMOST
            }
            val slip = idx.firstOrNull { marks.getOrNull(it)?.verdict == Verdict.WRONG }
            val said = if (!sentences) null else idx.firstOrNull { marks.getOrNull(it)?.verdict == Verdict.CORRECT }?.let { items[it].said(marks[it].answer) }
            RuleAnswer(
                rule, verdict, said,
                slip?.let { items[it].said(marks[it].answer.ifBlank { "…" }) }.orEmpty(),
                slip?.let { items[it].said(marks[it].expected) },
            )
        }
    }

    /** Whether a sheet's right items are sentences worth keeping as the learner's own (a gap text's, a transform list's). */
    fun sentences(ex: Exercise): Boolean = ex is Exercise.GapText || ex is Exercise.Transform

    /** The forms a sheet practised ([SheetItem.lemma] and [SheetItem.form]) and whether each was right (an almost is). */
    fun forms(items: List<SheetItem>, marks: List<ItemMark>): List<Triple<String, String, Boolean>> = items.mapIndexedNotNull { i, it ->
        val lemma = it.lemma?.takeIf { l -> l.isNotBlank() } ?: return@mapIndexedNotNull null
        val form = it.form ?: return@mapIndexedNotNull null
        val m = marks.getOrNull(i) ?: return@mapIndexedNotNull null
        Triple(lemma, form, m.verdict != Verdict.WRONG)
    }

    /** The words of a sheet's texts, for the run's lookup: its text, its cells and lines, its words to sort. */
    fun texts(ex: Exercise): List<String> = when (ex) {
        is Exercise.GapText -> listOf(filled(ex, ex.gaps.map { null }))
        is Exercise.Table -> ex.rows.map { r -> (listOf(r.label) + r.cells.mapNotNull { it.given }).joinToString(" ") }
        is Exercise.Transform -> ex.items.map { it.from }
        is Exercise.Sort -> listOf(ex.columns.flatMap { it.words }.joinToString(", "))
        else -> emptyList()
    }
}
