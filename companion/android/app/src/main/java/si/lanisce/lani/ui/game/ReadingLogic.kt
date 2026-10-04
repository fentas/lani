package si.lanisce.lani.ui.game

import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.culture.IngredientEntry
import si.lanisce.lani.game.culture.ReadingFile
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import kotlin.random.Random

// Pure helpers behind "📖 Preberi · Read" (ReadingSheet): the lines of a chest reading in the learner's pair, the
// translation shown or hidden, the closing questions and their answers.

/**
 * A line of a reading as it shows: [target] in the learner's target language (its words to tap, to hear), [base] its
 * translation; a step's [number]; a proverb's meaning ([meansTarget] · [meansBase]). A [Kind.PART] heads a group of
 * ingredients ("Testo · The dough").
 */
data class ReadLine(
    val kind: Kind,
    val target: String,
    val base: String,
    val number: Int? = null,
    val meansTarget: String? = null,
    val meansBase: String? = null,
) {
    enum class Kind { INTRO, SERVINGS, PART, INGREDIENT, STEP, LINE }

    /** Something to read aloud (a heading and the servings aren't). */
    val spoken: Boolean get() = kind != Kind.PART && kind != Kind.SERVINGS
}

/**
 * A closing question in the learner's pair: [options] in a fixed order, the [answer] among them, [explain] why. Its
 * [type] (the main idea, a detail, a word in context, true or false, an inference; null: a plain question); a true/false
 * one's options are "true" and "false" in the target language, in that order.
 */
data class ReadQuestion(
    val ask: String,
    val askBase: String,
    val options: List<String>,
    val answer: String,
    val explain: String,
    val explainBase: String,
    val type: String? = null,
)

/** A new word a reading offers: [word] (the dictionary's form) with what it [means] in the base, the [line] it's in and its translation. */
data class ReadWord(val word: String, val form: String, val means: String, val pos: String?, val line: String, val lineBase: String)

/**
 * The translations of a reading: all shown ([showAll]), or hidden, to read it first (reading practice), and then the
 * lines tapped open ([revealed], by index) shown one by one.
 */
data class ReadingView(val showAll: Boolean = true, val revealed: Set<Int> = emptySet()) {
    fun shows(i: Int): Boolean = showAll || i in revealed

    /** Show all, or hide all again. */
    fun toggle(): ReadingView = ReadingView(showAll = !showAll)

    fun reveal(i: Int): ReadingView = if (shows(i)) this else copy(revealed = revealed + i)
}

/**
 * Where a reading's run is (companion/GAME.md, "Read first, then answer"): read first, then ([asking]) its questions one
 * at a time with the text hidden, [at] the one on screen; [peeking]: the text shown again over it ("👁 Pokaži besedilo ·
 * Show the text"), [looks] how often it was brought back while a question was still open (a look at an answered question,
 * or once all are answered, is free).
 */
data class ReadRun(val asking: Boolean = false, val at: Int = 0, val peeking: Boolean = false, val looks: Int = 0) {
    /** "Začni · Start": the text hides, the first question comes. */
    fun start(): ReadRun = copy(asking = true, peeking = false)

    /** The text shown again, or hidden again; [open]: the question on screen isn't answered yet, so a look counts. */
    fun peek(open: Boolean): ReadRun =
        if (peeking) copy(peeking = false) else copy(peeking = true, looks = looks + if (open && asking) 1 else 0)

    /** On to the next question, the text hidden again. */
    fun next(): ReadRun = copy(at = at + 1, peeking = false)
}

/**
 * The result's line about looking back at the text: "👁 Dva pogleda v besedilo, brez odbitka · You looked at the text
 * twice, at no cost", "…: −5 % plačila · …: −5 % of the pay" ([cost] percent, [si.lanisce.lani.game.Peeks]), or just
 * how often where nothing was paid ([pays] false: a reading read before, a book); null when it wasn't looked at.
 */
fun looksText(looks: Int, cost: Int, pays: Boolean): String? = when {
    looks <= 0 -> null
    !pays -> "👁 ${bi("reading.looked", "n" to looks)}"
    cost > 0 -> "👁 ${bi("reading.lookedCost", "n" to looks, "pct" to cost)}"
    else -> "👁 ${bi("reading.lookedFree", "n" to looks)}"
}

/**
 * What the read phase says about looking back ([pays]: the run pays, so looks past the free ones at [level] cost a
 * little): "Med vprašanji lahko besedilo spet pogledaš. Prva dva pogleda sta brezplačna, …".
 */
fun peekRuleText(level: String, pays: Boolean): String =
    if (!pays) bi("reading.peekAgain") else bi("reading.peekRule", "free" to si.lanisce.lani.game.Peeks.free(level))

/** What was picked for each question (by index): the first pick counts, as in an exercise. */
data class ReadAnswers(val picked: Map<Int, String> = emptyMap()) {
    fun pick(q: Int, choice: String): ReadAnswers = if (q in picked) this else copy(picked = picked + (q to choice))

    /** Every one of [questions] answered. */
    fun complete(questions: List<ReadQuestion>): Boolean = questions.indices.all { it in picked }

    fun right(questions: List<ReadQuestion>, q: Int): Boolean? = picked[q]?.let { it == questions[q].answer }

    fun verdicts(questions: List<ReadQuestion>): List<Verdict> =
        questions.indices.map { if (picked[it] == questions[it].answer) Verdict.CORRECT else Verdict.WRONG }

    fun score(questions: List<ReadQuestion>): Int = questions.indices.count { right(questions, it) == true }
}

object ReadingLogic {
    /** Reading [r]'s lines in [target] · [base]: its intro, then a recipe's servings, ingredients and steps, or its lines. */
    fun lines(r: ReadingFile, target: Lang = L10n.pair.target, base: Lang = L10n.pair.base): List<ReadLine> = buildList {
        r.intro?.let { add(ReadLine(ReadLine.Kind.INTRO, it.of(target), it.of(base))) }
        r.servings?.let { add(ReadLine(ReadLine.Kind.SERVINGS, it.of(target), it.of(base))) }
        for (g in r.ingredients) {
            g.part?.let { add(ReadLine(ReadLine.Kind.PART, it.of(target), it.of(base))) }
            add(ReadLine(ReadLine.Kind.INGREDIENT, ingredient(g, target), ingredient(g, base)))
        }
        r.steps.forEachIndexed { i, s -> add(ReadLine(ReadLine.Kind.STEP, s.of(target), s.of(base), number = i + 1)) }
        for (l in r.lines) add(ReadLine(ReadLine.Kind.LINE, l.text.of(target), l.text.of(base), meansTarget = l.means?.of(target), meansBase = l.means?.of(base)))
    }

    /** "500 g moke", "ščepec soli", "500 g di farina", "a pinch of salt": the amount, the unit and the thing, in [lang]. */
    fun ingredient(g: IngredientEntry, lang: Lang): String = listOfNotNull(g.amount, g.unit?.of(lang), g.item.of(lang)).joinToString(" ")

    /**
     * Reading [r]'s questions in [target] · [base], their options in an order of their own: the same every time (for
     * reading [r] and the question), not always the answer first. A true/false statement's options are [yes] and [no]
     * ("Drži", "Ne drži"), in that order.
     */
    fun questions(
        r: ReadingFile,
        target: Lang = L10n.pair.target,
        base: Lang = L10n.pair.base,
        yes: String = L10n.text(target, "reading.true"),
        no: String = L10n.text(target, "reading.false"),
    ): List<ReadQuestion> =
        r.questions.mapIndexed { i, q ->
            val (options, answer) = if (q.type == "true_false") listOf(yes, no) to (if (q.answer == true) yes else no)
            else q.options.shuffled(Random("${r.id}/$i".hashCode())) to q.options.first()
            ReadQuestion(q.ask.of(target), q.ask.of(base), options, answer, q.explain.of(target), q.explain.of(base), q.type)
        }

    /**
     * The new words reading [r] offers, in [target] · [base]: each with the line it stands in (the first that has its
     * form), for the review item's example.
     */
    fun words(r: ReadingFile, target: Lang = L10n.pair.target, base: Lang = L10n.pair.base): List<ReadWord> {
        val shown = lines(r, target, base).filter { it.spoken }
        return r.words.map { w ->
            val form = w.form ?: w.word
            val l = shown.firstOrNull { Regex("(^|[^\\p{L}])${Regex.escape(form.lowercase())}($|[^\\p{L}])").containsMatchIn(it.target.lowercase()) }
            ReadWord(w.word, form, w.means.of(base), w.pos, l?.target.orEmpty(), l?.base.orEmpty())
        }
    }

    /** The l10n key of a reading's [kind]: "Recept · Recipe". */
    fun kindKey(kind: String): String = when (kind) {
        "recipe" -> "reading.kindRecipe"
        "proverbs" -> "reading.kindProverbs"
        "letter" -> "reading.kindLetter"
        "story" -> "reading.kindStory"
        "article" -> "reading.kindArticle"
        else -> "reading.kindPage"
    }

    /** The l10n key of a question's [type]: "Glavna misel · The main idea"; null for a plain question. */
    fun typeKey(type: String?): String? = when (type) {
        "main_idea" -> "reading.typeMainIdea"
        "detail" -> "reading.typeDetail"
        "word" -> "reading.typeWord"
        "true_false" -> "reading.typeTrueFalse"
        "inference" -> "reading.typeInference"
        else -> null
    }
}
