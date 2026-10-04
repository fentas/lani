package si.lanisce.lani.game

import kotlinx.serialization.Serializable
import si.lanisce.lani.data.FormLine
import si.lanisce.lani.data.FormSlot
import si.lanisce.lani.data.Grading
import si.lanisce.lani.data.LemmaForms
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.data.WordFormsWire
import si.lanisce.lani.l10n.bi
import kotlin.random.Random

/** One form question answered: which form ([key], "pres.1sg"), whether right, and the day (ISO). */
@Serializable
data class FormAsked(val key: String, val right: Boolean, val day: String)

/**
 * What a word's card asked of its forms (companion/GAME.md, "A word's forms"): the card's repetitions when it last asked
 * one ([at]: the next comes [WordForms.EVERY] good reviews later), and the forms asked, the newest last ([WordForms.KEEP]).
 */
@Serializable
data class FormsRecord(val at: Int = 0, val asked: List<FormAsked> = emptyList())

/**
 * A form question of a word's card: the lemma's form [key] in a sentence with a gap ([gap]: a real line of the content, or
 * a frame such as "Jaz ____. (sesti)"), the forms that are right ([accept]), the [options] to choose from (other forms of
 * the same word; empty: typed), what to do ([instruction]) and what the answer's feedback adds under the right sentence ([explain]: the line's
 * translation, what the form is). A wrong form
 * counts on the grammar book's [page], not on the word.
 */
data class FormQuestion(
    val lemma: String,
    val key: String,
    val page: String,
    val gap: FormGap,
    val accept: List<String>,
    val options: List<String>,
    val instruction: String,
    val explain: String,
    val line: FormLine? = null,
) {
    val typed: Boolean get() = options.isEmpty()

    /** The answer shown as right. */
    val answer: String get() = gap.word

    /** What was answered, as the whole sentence ("Jaz sedi na klop."): a word in the gap, or the sentence typed out. */
    fun said(answer: String): String {
        val a = answer.trim()
        return if (' ' in a && ' ' !in gap.word) a else gap.filled(a)
    }
}

/**
 * A word's forms in its review (companion/GAME.md, "A word's forms"): once the learner knows a word's meaning, its card
 * sometimes asks a form instead, in a sentence with a gap; which forms, as the grammar book introduces their rules. Pure.
 *
 * - **Which forms** ([locks]): a form's page and the pages it needs aren't [Mastery.NOT_YET] (the gating the dialogs
 *   use), and a dual's page was met (introduced): the dual comes late.
 * - **When** ([due]): a familiar card ([ReviewPlanner.familiarity] 2) asks one at most every [EVERY] good reviews; the
 *   first time spread over the cards ([phase]).
 * - **Which one** ([pick]): one got wrong last first, then one not asked yet (in the table's order: the singular and the
 *   present first), then the one asked longest ago.
 * - **How** ([question]): a real line of the content first ([FormSlot.lines]), else a frame; chosen among the word's other
 *   forms until the form was once right, typed after.
 */
object WordForms {
    /** A familiar card asks a form at most once every this many good reviews. */
    const val EVERY = 3

    /** Asks kept per card. */
    const val KEEP = 12

    /** The key of a card's [FormsRecord] on the phone (app/FormsController: files/forms-asked.json). */
    fun recordKey(language: String, cardId: String) = "$language/$cardId"

    // --- which forms are reached ---------------------------------------------------------------------------------

    const val DUAL_PAGE = "dvojina"

    /**
     * The pages that keep [slot] from being asked: those of its pages ([FormSlot.pages]) not introduced yet ([mastery]
     * [Mastery.NOT_YET]) or not in the book ([mastery] null), and the dual's while it wasn't met ([met]). Empty: reached.
     */
    fun locks(slot: FormSlot, mastery: (String) -> Mastery?, met: (String) -> Boolean): List<String> {
        val out = slot.pages.filter { p -> mastery(p).let { it == null || it == Mastery.NOT_YET } }.toMutableList()
        if (DUAL_PAGE !in out && (slot.key.endsWith(".du") || DUAL_PAGE in slot.pages) && !met(DUAL_PAGE)) out += DUAL_PAGE
        return out.distinct()
    }

    // --- when a card asks a form ---------------------------------------------------------------------------------

    /** A card's place in the [EVERY] reviews before its first form: spreads the first forms over the deck. */
    fun phase(cardId: String): Int = Math.floorMod(cardId.hashCode(), EVERY)

    /**
     * Whether [card] asks a form this review: it is familiar (its meaning known), and its last form was [EVERY] good
     * reviews ago (or before a failed review reset it); the first one when its repetitions reach its [phase].
     */
    fun due(card: ReviewCard, record: FormsRecord?): Boolean {
        if (ReviewPlanner.familiarity(card) < 2) return false
        if (record == null || record.asked.isEmpty()) return (card.repetitions + phase(card.id)) % EVERY == 0
        return card.repetitions < record.at || card.repetitions - record.at >= EVERY
    }

    /** [record] after form [key] was answered ([right]) on [day], the card at [repetitions] before the answer. */
    fun answered(record: FormsRecord?, key: String, right: Boolean, repetitions: Int, day: String): FormsRecord =
        FormsRecord(repetitions, ((record?.asked ?: emptyList()) + FormAsked(key, right, day)).takeLast(KEEP))

    // --- which form ----------------------------------------------------------------------------------------------

    /** Forms never asked: the dictionary form itself. */
    private val NOT_ASKED = setOf("nom.sg", "adj.m.sg")

    /**
     * Whether [slot] of [lemma] can be asked: not the dictionary form, and a real line or a frame for it; a frame only
     * when its answer is sure (one standard form, or a hand-checked table).
     */
    fun askable(slot: FormSlot, lemma: LemmaForms): Boolean {
        if (slot.key in NOT_ASKED || slot.forms.isEmpty()) return false
        if (slot.lines.any { gapIn(it.sl, it.form) != null }) return true
        return frame(slot.key, lemma.gender) != null && (slot.forms.size == 1 || lemma.table == "checked")
    }

    /**
     * The form to ask of [lemma] ([open]: reached): the one got wrong last time it was asked (the latest first), else the
     * first not asked yet, else the one asked longest ago. Null when none can be asked.
     */
    fun pick(lemma: LemmaForms, record: FormsRecord?, open: (FormSlot) -> Boolean): FormSlot? {
        val slots = lemma.slots.filter { open(it) && askable(it, lemma) }
        if (slots.isEmpty()) return null
        val asked = record?.asked.orEmpty()
        val last = slots.associateWith { s -> asked.indexOfLast { it.key == s.key } }
        slots.filter { s -> last.getValue(s) >= 0 && !asked[last.getValue(s)].right }.maxByOrNull { last.getValue(it) }?.let { return it }
        slots.firstOrNull { last.getValue(it) < 0 }?.let { return it }
        return slots.minByOrNull { last.getValue(it) }
    }

    // --- the question --------------------------------------------------------------------------------------------

    /**
     * The form question [card] asks this review, if any: its lemma's [entries] (GET /forms; null: not known, or an older
     * bridge), the entry its meaning is about, when [due] (always with [force]: QA), the form [pick]ed among those reached
     * ([locks] empty), asked as [question] does. Null: the card asks its meaning as ever.
     */
    fun forCard(
        card: ReviewCard, entries: List<LemmaForms>?, record: FormsRecord?, locks: (FormSlot) -> List<String>, base: String,
        force: Boolean = false, random: Random = Random.Default,
    ): FormQuestion? {
        if (entries.isNullOrEmpty() || WordFormsWire.lemmaOf(card) == null) return null
        if (!force && !due(card, record)) return null
        val lemma = WordFormsWire.entryFor(entries, card.back) ?: return null
        val open = { s: FormSlot -> locks(s).isEmpty() }
        val slot = pick(lemma, record, open) ?: return null
        return question(lemma, slot, record, ReviewPlanner.meaning(card.back), base, open, random)
    }

    /**
     * The question asking [slot] of [lemma] (its card meaning [meaning]; [base]: the learner's base, for a line's
     * translation): its next real line ([FormSlot.lines], in turn), else its frame; chosen among the word's other forms
     * ([open] ones first) until it was once right ([record]), typed after, and typed when there are too few to choose from.
     * Null when it can't be asked.
     */
    fun question(
        lemma: LemmaForms, slot: FormSlot, record: FormsRecord?, meaning: String, base: String, open: (FormSlot) -> Boolean,
        random: Random = Random(slot.key.hashCode()),
    ): FormQuestion? {
        if (!askable(slot, lemma)) return null
        val asked = record?.asked.orEmpty().filter { it.key == slot.key }
        val lines = slot.lines.filter { gapIn(it.sl, it.form) != null }
        val line = lines.takeIf { it.isNotEmpty() }?.let { it[asked.size % it.size] }
        val answers = (slot.forms + slot.also + listOfNotNull(line?.form)).map { it.trim() }.filter { it.isNotEmpty() }
        val gap: FormGap = if (line != null) {
            val (start, end) = gapIn(line.sl, line.form)!!
            FormGap(line.sl.substring(0, start), line.sl.substring(start, end), line.sl.substring(end) + " (${lemma.lemma})")
        } else {
            val f = frame(slot.key, lemma.gender) ?: return null
            val word = slot.forms.first()
            val text = f.replace("{s}", sz(word)).replace("{k}", kh(word))
            val at = text.indexOf(GAP)
            FormGap(text.substring(0, at), word, text.substring(at + GAP.length) + " (${cue(slot.key, lemma.lemma)})")
        }
        val atStart = gap.before.isBlank()
        val shown = if (atStart) capitalized(gap.word) else gap.word.lowercase()
        val q = gap.copy(word = shown)
        val accept = (listOf(shown) + answers).distinctBy { norm(it) }
        val typed = asked.any { it.right } && asked.lastOrNull()?.right != false
        val options = if (typed) emptyList() else options(lemma, slot, accept, open).map { if (atStart) capitalized(it) else it }.shuffled(random)
        val label = label(slot.key)
        val instruction = listOfNotNull(
            "✍️ ${bi("wordForms.fillForm")}",
            "${lemma.lemma} — $meaning",
            label,
            line?.meantIn(base)?.takeIf { it.isNotBlank() }?.let { "„$it“" },
        ).joinToString("\n")
        // the feedback shows the whole sentence above it (sloveneOf): what it means, and what the form is
        val explain = listOfNotNull(line?.meantIn(base)?.takeIf { it.isNotBlank() }, label).joinToString(" — ")
        return FormQuestion(lemma.lemma, slot.key, slot.page, q, accept, options, instruction, explain, line)
    }

    /**
     * The options to choose from: up to three other forms of the word beside the right one (the first of [accept]), the
     * forms of its other slots that aren't right here, the same tense or number first ([group]), the reached ones before
     * the rest; none (typed then) when there are fewer than two others.
     */
    fun options(lemma: LemmaForms, slot: FormSlot, accept: List<String>, open: (FormSlot) -> Boolean): List<String> {
        val right = accept.map(::norm).toSet()
        // a noun's same case in another number would fit the sentence too ("Vidim hiše": the plural): never an option
        val sameCase = caseOf(slot.key)
        val others = lemma.slots.filter { it.key != slot.key && (sameCase == null || caseOf(it.key) != sameCase) }
            .sortedWith(compareBy({ if (group(it.key) == group(slot.key)) 0 else 1 }, { if (open(it)) 0 else 1 }))
        val picked = mutableListOf<String>()
        for (s in others) for (f in s.forms) {
            if (picked.size >= 3) break
            val n = norm(f)
            if (n.isEmpty() || n in right || picked.any { norm(it) == n }) continue
            picked += f.lowercase()
        }
        if (picked.size < 2) return emptyList()
        return (picked + accept.first().lowercase())
    }

    /** What a form's alternatives are grouped by: a verb's tense ("pres", "past"), a noun's number ("sg"), an adjective's degree. */
    fun group(key: String): String = when {
        key.startsWith("adj.") -> "adj"
        key.startsWith("cmp.") -> "cmp"
        isNoun(key) -> key.substringAfter('.')
        else -> key.substringBefore('.')
    }

    private val CASES = listOf("nom", "gen", "dat", "acc", "loc", "ins")
    private fun isNoun(key: String) = key.substringBefore('.') in CASES

    /** Where [form] stands in [sentence] as a word of its own, any case ([start], [end)); null when it doesn't. */
    fun gapIn(sentence: String, form: String): Pair<Int, Int>? {
        val f = norm(form)
        if (f.isEmpty()) return null
        val m = Forms.spans(sentence).firstOrNull { norm(it.value) == f } ?: return null
        return m.range.first to m.range.last + 1
    }

    private fun norm(s: String) = Grading.normalize(s)
    private fun capitalized(s: String) = s.replaceFirstChar { it.titlecase() }

    // --- frames --------------------------------------------------------------------------------------------------

    const val GAP = "____"

    private val PERSONS = mapOf(
        "1sg" to "Jaz", "2sg" to "Ti", "3sg" to "On", "1pl" to "Mi", "2pl" to "Vi", "3pl" to "Oni",
        "1du" to "Midva", "2du" to "Vidva", "3du" to "Onadva",
    )

    /**
     * The sentence a form is asked in when no line of the content has it ([GAP] where it goes; {s} and {k} the
     * prepositions s/z and k/h, which follow the word: s kruhom, z mlekom, h kmetu, k hiši): a verb by person ("Jaz ____."),
     * its past by person and gender ("Jaz (♂) sem ____.", "Ona je ____."), its imperative ("(ti) ____!"); a noun by the
     * school's six questions, a preposition for each ("pri ____": pri čem?), the dual with a number ("pri dveh ____"); an
     * adjective after a pronoun ("Ona je ____."). Null for a form that has none.
     */
    fun frame(key: String, gender: String? = null): String? {
        val (head, rest) = key.substringBefore('.') to key.substringAfter('.')
        val two = if (gender == "m") "dva" else "dve"
        return when (head) {
            "pres", "neg" -> PERSONS[rest]?.let { "$it $GAP." }
            "fut" -> PERSONS[rest]?.let { "Jutri ${it.lowercase()} $GAP doma." }
            "past" -> when (rest) {
                "m.sg" -> "Jaz (♂) sem $GAP."
                "f.sg" -> "Ona je $GAP."
                "n.sg" -> "Ono je $GAP."
                "m.pl" -> "Oni so $GAP."
                "f.pl" -> "One so $GAP."
                "m.du" -> "Onadva sta $GAP."
                "f.du" -> "Onidve sta $GAP."
                else -> null
            }
            "imp" -> when (rest) {
                "2sg" -> "(ti) $GAP!"
                "2pl" -> "(vi) $GAP!"
                "1pl" -> "(mi) $GAP!"
                "1du" -> "(midva) $GAP!"
                "2du" -> "(vidva) $GAP!"
                else -> null
            }
            "nom" -> when (rest) {
                "pl" -> "To so $GAP."
                "du" -> "$two $GAP"
                else -> null
            }
            "gen" -> if (rest == "du") "brez dveh $GAP" else "brez $GAP"
            "dat" -> if (rest == "du") "k dvema $GAP" else "{k} $GAP"
            "acc" -> if (rest == "du") "Vidim $two $GAP." else "Vidim $GAP."
            "loc" -> if (rest == "du") "pri dveh $GAP" else "pri $GAP"
            "ins" -> if (rest == "du") "z dvema $GAP" else "{s} $GAP"
            "adj" -> when (rest) {
                "f.sg" -> "Ona je $GAP."
                "n.sg" -> "Ono je $GAP."
                "m.pl" -> "Oni so $GAP."
                "f.pl" -> "One so $GAP."
                else -> null
            }
            "cmp" -> if (rest == "m.sg") "On je $GAP kot jaz." else null
            else -> null
        }
    }

    /** What the frame's brackets give: the lemma; "ne + biti" for a negative form; "hiša, mn." for a plural without a number. */
    fun cue(key: String, lemma: String): String = when {
        key.startsWith("neg.") -> "ne + $lemma"
        isNoun(key) && key.endsWith(".pl") && !key.startsWith("nom.") -> "$lemma, mn."
        else -> lemma
    }

    /** s before p, t, k, f, h, c, č, s, š; z before every other sound (s kruhom, z mlekom, z Ano). */
    fun sz(word: String): String = if (word.trim().firstOrNull()?.lowercaseChar()?.let { it in "ptkfhcčsš" } == true) "s" else "z"

    /** h before k and g; k before every other sound (h kmetu, k hiši). */
    fun kh(word: String): String = if (word.trim().firstOrNull()?.lowercaseChar()?.let { it in "kg" } == true) "h" else "k"

    // --- labels --------------------------------------------------------------------------------------------------

    /** The six cases' names ("5. sklon · locative"). */
    private val CASE_KEYS = listOf("wordForms.case1", "wordForms.case2", "wordForms.case3", "wordForms.case4", "wordForms.case5", "wordForms.case6")

    /** The school's questions of the six cases. */
    val QUESTIONS = listOf("kdo? kaj?", "koga? česa?", "komu? čemu?", "koga? kaj?", "pri kom? pri čem?", "s kom? s čim?")

    /** Which of the six cases a noun's form is (1-6); null for another word's. */
    fun caseOf(key: String): Int? = CASES.indexOf(key.substringBefore('.')).takeIf { it >= 0 }?.plus(1)

    /** A number's label: "množina · plural". */
    fun numberLabel(n: String): String? = when (n) {
        "sg" -> bi("wordForms.singular")
        "pl" -> bi("wordForms.plural")
        "du" -> bi("wordForms.dual")
        else -> null
    }

    /**
     * What a form is, short and in the learner's pair: "5. sklon · locative: pri kom? pri čem?" with "— množina · plural"
     * for a plural; a verb's "preteklik · past", "velelnik · imperative" (its person is in the sentence); an adjective's.
     */
    fun label(key: String): String {
        caseOf(key)?.let { n ->
            val number = key.substringAfter('.')
            val case = "${bi(CASE_KEYS[n - 1])}: ${QUESTIONS[n - 1]}"
            return numberLabel(number)?.let { "$case — $it" } ?: case
        }
        val head = key.substringBefore('.')
        val rest = key.substringAfter('.')
        val dual = rest.endsWith("du")
        val tense = when (head) {
            "pres" -> bi("wordForms.present")
            "neg" -> bi("wordForms.negative")
            "fut" -> bi("wordForms.future")
            "past" -> bi("wordForms.past")
            "imp" -> bi("wordForms.imperative")
            "adj" -> bi("wordForms.adjective")
            "cmp" -> bi("wordForms.comparative")
            else -> key
        }
        return if (dual) "$tense — ${bi("wordForms.dual")}" else tense
    }

    // --- the table on the word's card ----------------------------------------------------------------------------

    /** A cell: the form with its pronoun or mark ("jaz sedem", "♀ lepa"), or locked by the pages in [locks]. */
    data class Cell(val key: String, val text: String, val locks: List<String> = emptyList()) {
        val open: Boolean get() = locks.isEmpty()
    }

    /**
     * A part of the table: its title ("sedanjik · present"), its columns' numbers ("sg", "pl", "du"; none: one row of
     * forms), its rows (a row's label and cells; null: no such form).
     */
    data class Section(val title: String, val columns: List<String>, val rows: List<Pair<String, List<Cell?>>>)

    /**
     * A word's forms as its card shows them ([locks] of each slot: [locks]): a verb's present, negative and future by
     * person (singular, plural, dual), its past by gender, its imperative; a noun's six cases by number; an adjective's
     * genders and comparative. Locked cells keep their pages ("🔒" on the card, with the page that opens them).
     */
    fun table(lemma: LemmaForms, locks: (FormSlot) -> List<String>): List<Section> {
        fun cell(key: String, text: (String) -> String): Cell? {
            val s = lemma.slot(key) ?: return null
            return Cell(key, text(s.forms.joinToString(" / ")), locks(s))
        }
        val numbers = listOf("sg", "pl", "du")
        val columns = numbers
        val sections = mutableListOf<Section>()
        fun persons(head: String, title: String, prefix: String = "") {
            val rows = (1..3).map { p ->
                "" to numbers.map { n -> cell("$head.$p$n") { "${PERSONS.getValue("$p$n").lowercase()} $prefix$it" } }
            }.filter { (_, cells) -> cells.any { it != null } }
            if (rows.isNotEmpty()) sections += Section(title, columns, rows)
        }
        persons("pres", bi("wordForms.present"))
        persons("neg", bi("wordForms.negative"))
        persons("fut", bi("wordForms.future"))
        val past = listOf(
            "" to listOf(cell("past.m.sg") { "on $it" }, cell("past.m.pl") { "oni $it" }, cell("past.m.du") { "onadva $it" }),
            "" to listOf(cell("past.f.sg") { "ona $it" }, cell("past.f.pl") { "one $it" }, cell("past.f.du") { "onidve $it" }),
            "" to listOf(cell("past.n.sg") { "ono $it" }, null, null),
        ).filter { (_, cells) -> cells.any { it != null } }
        if (past.isNotEmpty()) sections += Section(bi("wordForms.past"), columns, past)
        val imp = listOf("imp.2sg", "imp.2pl", "imp.1pl", "imp.1du", "imp.2du").mapNotNull { k -> cell(k) { "$it!" } }
        if (imp.isNotEmpty()) sections += Section(bi("wordForms.imperative"), emptyList(), listOf("" to imp))
        val cases = (1..6).map { n ->
            "$n. ${QUESTIONS[n - 1]}" to numbers.map { num -> cell("${CASES[n - 1]}.$num") { it } }
        }.filter { (_, cells) -> cells.any { it != null } }
        if (cases.isNotEmpty()) sections += Section(bi("wordForms.cases"), columns, cases)
        val adj = listOfNotNull(
            cell("adj.m.sg") { "♂ $it" }, cell("adj.f.sg") { "♀ $it" }, cell("adj.n.sg") { "⚲ $it" },
            cell("adj.m.pl") { "♂♂ $it" }, cell("adj.f.pl") { "♀♀ $it" }, cell("cmp.m.sg") { "↑ $it" },
        )
        if (adj.isNotEmpty()) sections += Section(bi("wordForms.adjective"), emptyList(), listOf("" to adj))
        return sections
    }

    /**
     * The locked forms of [sections] by the page that opens them (the first of each cell's [Cell.locks]), in order:
     * page id → the labels of what it opens ("6. sklon · instrumental", "dvojina · dual").
     */
    fun locked(sections: List<Section>): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        for (s in sections) for ((_, cells) in s.rows) for (c in cells) {
            val page = c?.locks?.firstOrNull() ?: continue
            val what = lockLabel(c.key)
            val list = out.getOrPut(page) { mutableListOf() }
            if (what !in list) list += what
        }
        return out
    }

    /** What a locked form is called where the card says what opens it: a case ("6. sklon · instrumental"), a number, a tense. */
    fun lockLabel(key: String): String {
        val number = key.substringAfterLast('.')
        caseOf(key)?.let { n -> return if (number == "du") bi("wordForms.dual") else bi(CASE_KEYS[n - 1]) }
        if (number == "du" || number.endsWith("du")) return bi("wordForms.dual")
        return label(key)
    }
}
