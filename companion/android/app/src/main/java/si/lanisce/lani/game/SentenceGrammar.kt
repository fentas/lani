package si.lanisce.lani.game

import si.lanisce.lani.data.CaseChoice
import si.lanisce.lani.data.CaseLens
import si.lanisce.lani.data.CaseRead
import si.lanisce.lani.data.CaseRow
import si.lanisce.lani.l10n.inBase

/**
 * What the dictionary says of a word where it stands (the word card's first entry): its dictionary form, part of speech,
 * reading; [case]: what the case lens says of it in this line (GET /lookup's `case`, the bridge's case-lens.ts, which
 * reads the trigger table companion/grammar/sl/data/case-triggers.json; null: another language, an older bridge, a word
 * without a case).
 */
data class WordReading(val lemma: String, val pos: String?, val grammar: String?, val case: CaseRead? = null)

/**
 * Why a word has its form, as far as the line tells: for a word with a case, the case lens's row ([lens]: the case and
 * number, how sure, what decided it, its page; [read]: the lookup's answer it comes from), the word card's row exactly,
 * and the question it answers ([line]); for a verb, who does it ([person]: "jaz") or its tense ([tense]: a string
 * table's key; an infinitive, the verb it is [after]: moram narediti).
 */
data class WordWhy(
    val line: CaseLine? = null,
    val lens: CaseRow? = null,
    val read: CaseRead? = null,
    val person: String? = null,
    val tense: String? = null,
    val after: String? = null,
) {
    val case: Case? get() = line?.case ?: lens?.case
}

/**
 * A word of a line as "🔍 Slovnica stavka · The sentence's grammar" shows it (companion/SCENES.md, "The sentence's
 * grammar"): the [word] as written, its dictionary form and part of speech ([lemma], [pos]), its [reading] (the case
 * lens's case and number where it has one, "genitive singular", else the dictionary's), why that form ([why]) and its
 * grammar book [page]; a word nothing explains has only its form.
 */
data class WordRow(val word: String, val lemma: String?, val pos: String?, val reading: String?, val why: WordWhy?, val page: String?)

/**
 * The grammar of a sentence, word by word (docs/grammar-syllabus.md, "The long press on a line"): each word's case from
 * the case lens, the one analysis the word card's "🧩 Sklon · Case" row shows too (the bridge reads what decides a case
 * from the trigger table, so the sheet and the card can never disagree), and for a verb who does it or its tense. Pure:
 * the readings and the lens's answers come from the word lookups (GET /lookup with the line).
 */
object SentenceGrammar {
    /** The verbs after which another verb stays in the infinitive (moram narediti, znam plavati). */
    private val MODALS = (
        "moram moraš mora morava morata moramo morate morajo smem smeš sme smemo smete smejo hočem hočeš hoče hočemo hočete hočejo " +
            "nočem nočeš noče znam znaš zna znamo znate znajo želim želiš želi morem moreš more moremo morete morejo"
        ).split(" ").toSet()

    private val FUTURE = "bom boš bo bova bosta bomo boste bodo".split(" ").toSet()

    /**
     * The rows of [sentence]: one per word ([Forms.words]), with [readings] by the word's place (null: not looked up, or
     * the dictionary doesn't know it); [has]: whether the book has a page, the only ones a row links. Without readings
     * (offline, or before the lookups answer) the words alone.
     */
    fun analyze(sentence: String, readings: List<WordReading?>, has: (String) -> Boolean): List<WordRow> {
        val spans = Forms.spans(sentence)
        val words = spans.map { it.value.lowercase() }
        return spans.indices.map { i ->
            val r = readings.getOrNull(i)
            if (r?.pos?.lowercase() == "verb") {
                val (reading, why, page) = verb(words, i, r)
                return@map WordRow(spans[i].value, r.lemma, r.pos, reading ?: r.grammar, why, page?.takeIf(has))
            }
            val row = r?.case?.let(CaseLens::row)
            if (row == null) WordRow(spans[i].value, r?.lemma, r?.pos, r?.grammar, null, null)
            else WordRow(spans[i].value, r.lemma, r.pos, reading(row) ?: r.grammar, WordWhy(line = question(row), lens = row, read = r.case), row.rule?.takeIf(has))
        }
    }

    /**
     * The lens's case and number as the dictionary words a reading ("genitive singular", "nominative feminine
     * singular"): the pick (decided, likely), or each reading left (ambiguous); none for a trigger tapped.
     */
    fun reading(row: CaseRow): String? {
        if (row.state == CaseRow.State.GOVERNS) return null
        val one = { c: CaseChoice -> listOfNotNull(c.case.lexicon, c.gender?.let(::genderName), numberName(c.number)).joinToString(" ") }
        return if (row.state == CaseRow.State.AMBIGUOUS) row.options.joinToString("; ", transform = one)
        else row.case?.let { one(CaseChoice(it, row.number ?: "sg", row.gender)) }
    }

    /**
     * The question the word answers, as a Slovene teacher asks it: Kam? Kje? (a preposition's sense), Kdaj? (a time),
     * Koliko? (a quantity), Od kod? (iz, od), else the case's own (Koga? Česa?); none when the line can't tell.
     */
    fun question(row: CaseRow): CaseLine? {
        val case = row.case ?: return null
        if (row.state == CaseRow.State.AMBIGUOUS || row.state == CaseRow.State.GOVERNS) return null
        return when {
            row.kind == "preposition" && row.trigger != null -> TurnHints.preposition(row.trigger.word, case, time = row.sense == "time")
            row.kind == "time" || row.kind == "when" -> CaseLine("Kdaj?", "hint.qWhen", case)
            row.kind == "quantity" -> CaseLine("Koliko?", "hint.qHowMany", case)
            else -> CaseLine(case.question, null, case)
        }
    }

    private fun numberName(n: String): String = when (n) {
        "du" -> "dual"
        "pl" -> "plural"
        else -> "singular"
    }

    private fun genderName(g: String): String? = when (g) {
        "m" -> "masculine"
        "f" -> "feminine"
        "n" -> "neuter"
        else -> null
    }

    /** A verb: who does it (the present, the future's bom), or its tense (the l-form: past, future or would; the imperative). */
    private fun verb(words: List<String>, i: Int, r: WordReading?): Triple<String?, WordWhy?, String?> {
        val g = r?.grammar?.takeIf { !it.contains(';') } ?: return Triple(null, null, null)
        val lemma = r.lemma.lowercase()
        return when {
            "imperative" in g -> Triple(g, WordWhy(tense = "sentence.imperative"), "velelnik")
            "l-participle" in g -> {
                val (tense, page) = when {
                    words.any { it in FUTURE } -> "sentence.future" to "prihodnjik"
                    "bi" in words -> "sentence.would" to "pogojnik"
                    else -> "sentence.past" to "pretekli-cas"
                }
                Triple(g, WordWhy(tense = tense), page)
            }
            "infinitive" in g -> words.take(i).lastOrNull { it in MODALS }?.let { m ->
                Triple(g, WordWhy(tense = "sentence.infinitive", after = m), "modalni-glagoli")
            } ?: Triple(g, null, null)
            else -> person(g)?.let { p ->
                val page = when {
                    "dual" in g -> "dvojina"
                    "future" in g -> "prihodnjik"
                    lemma == "biti" -> "biti"
                    lemma == "imeti" || lemma == "iti" -> "imeti-iti"
                    else -> "glagoli-sedanjik"
                }
                Triple(g, WordWhy(person = p), page)
            } ?: Triple(g, null, null)
        }
    }

    /** Who a verb's reading says does it, as the person's pronoun: "first-person singular" → jaz. */
    fun person(g: String): String? {
        val singular = "singular" in g
        val dual = "dual" in g
        val plural = "plural" in g
        return when {
            "first-person" in g && singular -> "jaz"
            "first-person" in g && dual -> "midva"
            "first-person" in g && plural -> "mi"
            "second/third-person" in g && dual -> "vidva, onadva"
            "second-person" in g && singular -> "ti"
            "second-person" in g && dual -> "vidva"
            "second-person" in g && plural -> "vi"
            "third-person" in g && singular -> "on, ona"
            "third-person" in g && dual -> "onadva"
            "third-person" in g && plural -> "oni"
            else -> null
        }
    }

    /**
     * Why a word has its form, in the learner's base: "Koga? Česa? → 2. rodilnik (genitive) · After the preposition
     * «brez»: the genitive." (the lens's line as the word card says it; "likely" when it is), what a tapped preposition
     * decides, or a verb's person and tense.
     */
    fun why(w: WordWhy): String? {
        val parts = buildList {
            w.line?.let { add(TurnHints.caseLine(it) + if (w.lens?.state == CaseRow.State.LIKELY) " (${inBase("caseLens.likely")})" else "") }
            val lens = w.lens
            val read = w.read
            if (lens != null && read != null) {
                if (lens.state == CaseRow.State.GOVERNS) lens.governs.forEach { (ref, c) -> add(CaseLens.governs(c, ref.word)) }
                else CaseLens.why(lens, read)?.second?.let(::add)
            }
            w.person?.let { add(inBase("sentence.person", "who" to it)) }
            w.after?.let { add(inBase("sentence.infinitive", "word" to it)) } ?: w.tense?.let { add(inBase(it)) }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
