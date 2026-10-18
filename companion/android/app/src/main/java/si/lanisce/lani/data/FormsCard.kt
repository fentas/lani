package si.lanisce.lani.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.GrammarBook
import si.lanisce.lani.game.School
import si.lanisce.lani.game.WordForms
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import java.time.LocalDate
import kotlin.random.Random

/**
 * One form of a forms card's word (the item's `forms.slots`, made by the node from the dictionary: companion/bridge/src/
 * forms-cards.ts): its [key] ("gen.sg", "pres.1sg", "fut.1sg", "past.f.sg", "imp.2pl", "adj.f.sg", "adj.gen.m.sg"), the
 * dictionary's [forms] (every one right, the first shown), those [also] accepted (spoken ones: bojo), the grammar book's
 * [page] whose rule it is and the pages it [needs] introduced too. [composed]: a future joined of biti's future and the
 * l-form (bom delal), both the dictionary's.
 */
@Serializable
data class FormsCardSlot(
    val key: String,
    val forms: List<String> = emptyList(),
    val also: List<String> = emptyList(),
    val page: String = "",
    val needs: List<String> = emptyList(),
    val composed: Boolean = false,
) {
    /** The form shown: the first of the dictionary's. */
    val shown: String get() = forms.firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    /** Every form that is right here. */
    val accept: List<String> get() = (forms + also).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

/**
 * What a forms card keeps of its word (the review item's `forms`; companion/README.md "Forms cards"): the [lemma], its
 * part of speech ([pos]: noun, verb, adj), a noun's [gender], the dictionary's [gloss], what it [meaning]s on the learner's
 * card, its [emoji], whose [table] it is, its [slots] in the dictionary's order, the slots [tested] when the node last
 * scheduled it, the ones newly tested then ([fresh], on [unlocked]: played first) and whether it was made on demand
 * ([asked]: "📋 Vse oblike" on a word's card).
 */
@Serializable
data class FormsCardData(
    val lemma: String,
    val pos: String,
    val gender: String? = null,
    val gloss: List<String> = emptyList(),
    val meaning: String? = null,
    val emoji: String? = null,
    val table: String? = null,
    val slots: List<FormsCardSlot> = emptyList(),
    val tested: List<String> = emptyList(),
    val fresh: List<String> = emptyList(),
    val unlocked: String? = null,
    val resurface: String? = null,
    val asked: Boolean = false,
    val v: Int = 1,
) {
    fun slot(key: String): FormsCardSlot? = slots.firstOrNull { it.key == key }

    /** Whole: a part of speech the card knows, and enough forms besides the word's own, each with its page. */
    val whole: Boolean
        get() = pos in FormsCard.POS && lemma.isNotBlank() &&
            slots.count { !FormsCard.own(this, it.key) && it.shown.isNotEmpty() && it.page.isNotBlank() } >= FormsCard.MIN_SLOTS
}

/** A card POST /forms-cards made (or had: [existing]): its review item, its word, how many of its forms are asked now. */
@Serializable
data class FormsCardMade(val item_id: String, val lemma: String, val pos: String = "", val tested: Int = 0, val existing: Boolean = false)

/** A card POST /forms-cards brought back soon: a part of its table newly introduced ([fresh]), due [due_date]. */
@Serializable
data class FormsCardBack(val item_id: String, val lemma: String = "", val due_date: String = "", val fresh: List<String> = emptyList())

/** A word POST /forms-cards made no card of, and why ("not a word the learner has learned", "today's 2 new forms cards are made"). */
@Serializable
data class FormsCardSkip(val word: String, val why: String = "")

/** POST /forms-cards' answer: the cards made, the words skipped, the cards brought back, today's new cards and the day's most. */
@Serializable
data class FormsCardsAnswer(
    val added: List<FormsCardMade> = emptyList(),
    val skipped: List<FormsCardSkip> = emptyList(),
    val resurfaced: List<FormsCardBack> = emptyList(),
    val made_today: Int = 0,
    val per_day: Int = FormsCard.NEW_PER_DAY,
    val resurface_per_day: Int = 3,
)

/** A cell of a forms card's table: what it is to the learner now. */
@Serializable
enum class FormsCellState {
    /** The word itself (a noun's nominative singular, an adjective's masculine): shown, never asked. */
    GIVEN,

    /** Asked in this review. */
    GAP,

    /** Introduced, asked in another part: shown as known. */
    TESTED,

    /** Not introduced yet: greyed, to read ahead; asked once its page is met. */
    PREVIEW,
}

/** A cell: its slot's [key], the words around it ([before]: "jaz", "on je"; [after]: "!"), its [form] and [state]. */
@Serializable
data class FormsCell(val key: String, val form: String, val state: FormsCellState, val before: String = "", val after: String = "") {
    /** The cell as said: "jaz delam", "delaj!". */
    val text: String get() = listOf(before, form + after).filter { it.isNotBlank() }.joinToString(" ")
}

/** A row of a section: its [label] ("2. koga? česa?"), its cells by column (null: no such form), what it says aloud. */
@Serializable
data class FormsRow(val label: String, val cells: List<FormsCell?>, val said: String)

/** A part of the table: its [title] ("sedanjik · present"), its columns' heads, its rows. */
@Serializable
data class FormsSection(val title: String, val columns: List<String>, val rows: List<FormsRow>)

/**
 * A gap asked: its slot's [key], the form shown as right ([answer]), every form that is right ([accept]), the three to
 * choose from ([options]: the right one and two of the word's other forms, never one that is right here), [chosen]:
 * asked by choosing even when the card is typed (a part newly introduced), the rule's [page], and where it stands
 * ([where]: "sedanjik · present — ednina", with the words around it: [before], [after]).
 */
@Serializable
data class FormsGap(
    val key: String,
    val answer: String,
    val accept: List<String>,
    val options: List<String>,
    val chosen: Boolean,
    val page: String,
    val where: String,
    val before: String = "",
    val after: String = "",
)

/**
 * Forms cards ("📋 Oblike · Forms"; companion/README.md "Forms cards"): one word with its whole table, the forms the
 * school has introduced asked as gaps, the others greyed to read ahead, each card its own spaced-repetition card made by
 * the node from the dictionary (POST /forms-cards). Pure.
 *
 * - **Introduced** ([introduced]): a grammar page met in the grammar book, or taught by a course unit whose intro is done.
 *   A slot is tested ([tested]) once its page and the pages it needs are introduced; the word's own form never is.
 * - **The table** ([layout]): a noun's six cases by number; a verb's present, negative, future by person, its past by
 *   gender, its imperative; an adjective's nominative by gender and number, then its cases in the singular and plural.
 * - **A part a review** ([part]): whole rows up to [PART_CELLS] gaps: the rows newly introduced first while they are new
 *   ([fresh]), else the rows in turn, a failed review asking its part again.
 * - **Choosing, then typing** ([options], [secure]): three forms to choose from while the card is new, typed once it is
 *   secure (a fresh part chosen still); graded as the app grades a cloze ([grade]); a wrong one has its [kind].
 */
object FormsCard {
    /** The card's type in spaced-repetition.json. */
    const val KIND = "forms"
    /** What GET /state's `features` says of a bridge that makes forms cards (POST /forms-cards). */
    const val FEATURE = "forms-cards"
    const val LANGUAGE = "sl"
    val POS = setOf("noun", "verb", "adj")

    /** A card has at least this many forms besides the word's own (the node says the same). */
    const val MIN_SLOTS = 4
    /** A word gets a card of its own accord once this many of its forms are introduced (the node's rule). */
    const val MIN_TESTED = 2
    /** New forms cards a day at most of their own accord (the node says the same: LANI_FORMS_PER_DAY); on demand any time. */
    const val NEW_PER_DAY = 2
    /** Forms cards in one review at most: the rest wait for the next. */
    const val PER_REVIEW = 3
    /** The words one request names at most. */
    const val ASK_MAX = 20
    /** The gaps a review asks of a card at most (whole rows; a row bigger alone). */
    const val PART_CELLS = 6
    /** Secure: this many good reviews in a row, the last of at least [SECURE_QUALITY]. */
    const val SECURE_REPS = 2
    const val SECURE_QUALITY = 4
    /** A part newly introduced is asked first for this many good reviews (its card's repetitions start again at 0). */
    const val FRESH_REVIEWS = 2
    /** ... and only within this many days of its unlocking. */
    const val FRESH_DAYS = 30L

    /** The core verbs: their cards first. */
    val CORE = listOf("biti", "imeti", "iti", "delati", "hoteti", "moči", "vedeti", "videti", "reči", "dati", "priti", "govoriti", "piti", "jesti", "spati", "živeti")

    /** The item's `forms` as the node keeps it; null when it doesn't read or isn't whole. */
    fun parse(e: JsonElement?): FormsCardData? =
        (e as? JsonObject)?.let { runCatching { json.decodeFromJsonElement(FormsCardData.serializer(), it) }.getOrNull() }?.takeIf { it.whole }

    /** The word's own form: a noun's nominative singular, an adjective's masculine singular. Shown, never asked. */
    fun own(d: FormsCardData, key: String): Boolean = (d.pos == "noun" && key == "nom.sg") || (d.pos == "adj" && key == "adj.m.sg")

    fun key(w: String): String = w.trim().lowercase()

    // --- what's introduced ---------------------------------------------------------------------------------------------

    /**
     * The grammar pages of [language] introduced to the learner: met in the grammar book ([GrammarBook.met]: a unit's intro
     * gone through, a page opened, a rule answered), or taught by a unit of [course] (its `grammar`) whose intro is done.
     */
    fun introduced(s: GameState?, course: Course?, language: String = LANGUAGE): Set<String> {
        if (s == null) return emptySet()
        val met = GrammarBook.met(s, language).keys
        val taught = course?.takeIf { it.language == language }?.units?.values.orEmpty()
            .filter { School.progress(s, language, it.id)?.introDone == true }
            .flatMap { it.grammar }
        return met + taught
    }

    /** Whether [slot] of [d] is asked: its page and the pages it needs introduced, not the word's own, a form to ask. */
    fun tested(d: FormsCardData, slot: FormsCardSlot, introduced: Set<String>): Boolean =
        !own(d, slot.key) && slot.shown.isNotEmpty() && slot.page in introduced && slot.needs.all { it in introduced }

    /** The keys of [d]'s slots asked now, in the dictionary's order. */
    fun testedKeys(d: FormsCardData, introduced: Set<String>): List<String> = d.slots.filter { tested(d, it, introduced) }.map { it.key }

    // --- the table -----------------------------------------------------------------------------------------------------

    /** Where a cell stands: its slot's [key] and the words around it. */
    data class Spot(val key: String, val before: String = "", val after: String = "")

    /** A section's plan: its title's string table key(s), its columns (sg, du, pl; m, f, n), its rows (a label, the spots). */
    data class Plan(val title: String, val columns: List<String>, val rows: List<Pair<String, List<Spot?>>>)

    private val CASES = listOf("nom", "gen", "dat", "acc", "loc", "ins")
    private val CASE_NAMES = mapOf("nom" to "nominative", "gen" to "genitive", "dat" to "dative", "acc" to "accusative", "loc" to "locative", "ins" to "instrumental")
    private val NUMBERS = listOf("sg", "du", "pl")

    /** The pronoun a person's form stands with: jaz, ti, on; midva, vidva, onadva; mi, vi, oni. */
    val PRONOUNS = mapOf(
        "1sg" to "jaz", "2sg" to "ti", "3sg" to "on", "1du" to "midva", "2du" to "vidva", "3du" to "onadva",
        "1pl" to "mi", "2pl" to "vi", "3pl" to "oni",
    )

    /**
     * The table of a word of [pos] as a card lays it out, before its forms: a noun's six cases (rows, "1. kdo? kaj?") by
     * number (ednina, dvojina, množina); a verb's present, negative and future by person (rows) and number, its past by
     * gender (on je, ona je, ono je …), its imperative (ti, vi; midva, mi); an adjective's nominative by gender and
     * number, then its cases in the singular and in the plural by gender.
     */
    fun plans(pos: String): List<Plan> = when (pos) {
        "noun" -> listOf(Plan("wordForms.cases", NUMBERS, (1..6).map { n -> "$n. ${WordForms.QUESTIONS[n - 1]}" to NUMBERS.map { Spot("${CASES[n - 1]}.$it") } }))
        "verb" -> {
            fun persons(head: String, title: String) = Plan(title, NUMBERS, (1..3).map { p -> "" to NUMBERS.map { n -> Spot("$head.$p$n", PRONOUNS.getValue("$p$n")) } })
            listOf(
                persons("pres", "wordForms.present"),
                persons("neg", "wordForms.negative"),
                persons("fut", "wordForms.future"),
                Plan(
                    "wordForms.past", NUMBERS, listOf(
                        "" to listOf(Spot("past.m.sg", "on je"), Spot("past.m.du", "onadva sta"), Spot("past.m.pl", "oni so")),
                        "" to listOf(Spot("past.f.sg", "ona je"), Spot("past.f.du", "onidve sta"), Spot("past.f.pl", "one so")),
                        "" to listOf(Spot("past.n.sg", "ono je"), null, null),
                    ),
                ),
                Plan(
                    "wordForms.imperative", NUMBERS, listOf(
                        "" to listOf(Spot("imp.2sg", "(ti)", "!"), Spot("imp.2du", "(vidva)", "!"), Spot("imp.2pl", "(vi)", "!")),
                        "" to listOf(null, Spot("imp.1du", "(midva)", "!"), Spot("imp.1pl", "(mi)", "!")),
                    ),
                ),
            )
        }
        "adj" -> {
            val genders = listOf("m", "f", "n")
            fun cases(number: String, title: String) = Plan(title, genders, (2..6).map { n -> "$n. ${WordForms.QUESTIONS[n - 1]}" to genders.map { g -> Spot("adj.${CASES[n - 1]}.$g.$number") } })
            listOf(
                Plan(
                    "wordForms.case1", NUMBERS, listOf(
                        "m" to listOf(Spot("adj.m.sg"), Spot("adj.m.du"), Spot("adj.m.pl")),
                        "f" to listOf(Spot("adj.f.sg"), Spot("adj.f.du"), Spot("adj.f.pl")),
                        "n" to listOf(Spot("adj.n.sg"), null, Spot("adj.n.pl")),
                    ),
                ),
                cases("sg", "wordForms.singular"),
                cases("pl", "wordForms.plural"),
            )
        }
        else -> emptyList()
    }

    /** [d]'s plans with only the spots it has a form of; rows and sections left empty go. */
    fun layout(d: FormsCardData): List<Plan> = plans(d.pos).mapNotNull { p ->
        val rows = p.rows.map { (label, spots) -> label to spots.map { s -> s?.takeIf { d.slot(it.key)?.shown?.isNotEmpty() == true } } }
            .filter { (_, spots) -> spots.any { it != null } }
        if (rows.isEmpty()) null else p.copy(rows = rows)
    }

    /** The rows of [d]'s table with the slots asked now ([tested]) in each, in the table's order; rows without any left out. */
    fun rows(d: FormsCardData, tested: Set<String>): List<List<String>> =
        layout(d).flatMap { p -> p.rows.map { (_, spots) -> spots.mapNotNull { it?.key }.filter { it in tested } } }.filter { it.isNotEmpty() }

    /** [rows] in parts of whole rows, up to [PART_CELLS] gaps each (a row bigger stands alone). */
    fun parts(rows: List<List<String>>): List<List<String>> {
        val out = mutableListOf<List<String>>()
        var cur = mutableListOf<String>()
        for (r in rows) {
            if (cur.isNotEmpty() && cur.size + r.size > PART_CELLS) {
                out += cur
                cur = mutableListOf()
            }
            cur += r
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    /** The slots newly introduced ([FormsCardData.fresh]) still to play first: while the card has fewer than [FRESH_REVIEWS] good reviews since, within [FRESH_DAYS]. */
    fun freshNow(card: ReviewCard, d: FormsCardData, tested: Set<String>, today: LocalDate): List<String> {
        if (d.fresh.isEmpty() || card.repetitions >= FRESH_REVIEWS) return emptyList()
        val on = d.unlocked?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        if (on != null && on.plusDays(FRESH_DAYS) < today) return emptyList()
        return d.fresh.filter { it in tested }
    }

    /**
     * The gaps [card] asks this review ([tested]: the slots asked now) and whether they are its fresh part: the rows newly
     * introduced while they are new ([freshNow]: up to [PART_CELLS] gaps), else its parts in turn by its reviews (a
     * failed last review asks the same part again).
     */
    fun part(card: ReviewCard, d: FormsCardData, tested: Set<String>, today: LocalDate): Pair<List<String>, Boolean> {
        val rows = rows(d, tested)
        val fresh = freshNow(card, d, tested, today)
        if (fresh.isNotEmpty()) {
            val theirs = rows.filter { r -> r.any { it in fresh } }
            parts(theirs).firstOrNull()?.let { return it to true }
        }
        val all = parts(rows)
        if (all.isEmpty()) return emptyList<String>() to false
        val k = card.reviews - if (card.reviews > 0 && card.lastQuality < 3) 1 else 0
        return all[Math.floorMod(k, all.size)] to false
    }

    /** Where [key] stands in [plans]: its section, row and column; null when the table has no place for it. */
    private fun placeOf(plans: List<Plan>, key: String): Triple<Int, Int, Int>? {
        plans.forEachIndexed { s, p ->
            p.rows.forEachIndexed { r, (_, spots) -> spots.forEachIndexed { c, spot -> if (spot?.key == key) return Triple(s, r, c) } }
        }
        return null
    }

    /**
     * Two wrong forms for gap [key] of [d], with the right one, shuffled: the word's other forms, the closest first (the
     * same row or column of the same section, then the same section, then the others), the introduced ones before the
     * others; never one that is right here; fewer when the word has fewer.
     */
    fun options(d: FormsCardData, key: String, tested: Set<String>, random: Random): List<String> {
        val slot = d.slot(key) ?: return emptyList()
        val plans = plans(d.pos)
        val here = placeOf(plans, key)
        fun distance(k: String): Int {
            val p = placeOf(plans, k) ?: return 3
            if (here == null || p.first != here.first) return 2
            return if (p.second == here.second || p.third == here.third) 0 else 1
        }
        val taken = slot.accept.map(::norm).toMutableSet()
        val others = d.slots.withIndex().filter { (_, s) -> s.key != key && s.shown.isNotEmpty() }
            .sortedWith(compareBy({ distance(it.value.key) }, { if (it.value.key in tested) 0 else 1 }, { it.index }))
        val picked = mutableListOf<String>()
        for ((_, s) in others) {
            if (picked.size == 2) break
            val f = s.forms.map { it.trim() }.firstOrNull { it.isNotEmpty() && norm(it) !in taken } ?: continue
            taken += norm(f)
            picked += f
        }
        return (listOf(slot.shown) + picked).shuffled(random)
    }

    // --- the card ------------------------------------------------------------------------------------------------------

    /** The card is secure: [SECURE_REPS] good reviews in a row, the last of [SECURE_QUALITY] or more. */
    fun secure(c: ReviewCard): Boolean = c.repetitions >= SECURE_REPS && c.lastQuality >= SECURE_QUALITY

    private fun columnLabel(code: String, label: (String) -> String): String = when (code) {
        "sg" -> label("wordForms.singular")
        "du" -> label("wordForms.dual")
        "pl" -> label("wordForms.plural")
        "m" -> label("words.genderM")
        "f" -> label("words.genderF")
        "n" -> label("words.genderN")
        else -> code
    }

    private fun rowLabel(l: String, label: (String) -> String): String = when (l) {
        "m" -> label("words.genderM")
        "f" -> label("words.genderF")
        "n" -> label("words.genderN")
        else -> l
    }

    /**
     * The exercise of forms card [card] ([d]: its word's forms) this review: the whole table (the word's own form given,
     * the gaps of its [part], the other introduced forms shown, the rest greyed), its gaps chosen, or [typed] once the card
     * is secure (a fresh part chosen still); [introduced]: the pages introduced. Null when nothing of it is asked yet.
     * [label]: the string tables' text of a key (the learner's pair; a test's own).
     */
    fun exercise(
        card: ReviewCard, d: FormsCardData, introduced: Set<String>, typed: Boolean, today: LocalDate, random: Random,
        label: (String) -> String = { bi(it) },
    ): Exercise.FormsTable? {
        val tested = testedKeys(d, introduced).toSet()
        val (gaps, fresh) = part(card, d, tested, today)
        if (gaps.isEmpty()) return null
        val freshKeys = freshNow(card, d, tested, today).toSet()
        val plans = layout(d)
        val asked = gaps.toSet()
        val where = HashMap<String, String>()
        val spots = HashMap<String, Spot>()
        val sections = plans.map { p ->
            val title = label(p.title)
            FormsSection(
                title, p.columns.map { columnLabel(it, label) },
                p.rows.map { (l, row) ->
                    val cells = row.map { s ->
                        s?.let {
                            val slot = d.slot(it.key)!!
                            val state = when {
                                own(d, it.key) -> FormsCellState.GIVEN
                                it.key in asked -> FormsCellState.GAP
                                it.key in tested -> FormsCellState.TESTED
                                else -> FormsCellState.PREVIEW
                            }
                            spots[it.key] = it
                            FormsCell(it.key, slot.shown, state, it.before, it.after)
                        }
                    }
                    row.forEachIndexed { c, s ->
                        if (s != null) where[s.key] = listOf(title, rowLabel(l, label), columnLabel(p.columns[c], label)).filter { it.isNotBlank() }.joinToString(" — ")
                    }
                    FormsRow(rowLabel(l, label), cells, cells.filterNotNull().joinToString(", ") { it.text })
                },
            )
        }
        val order = plans.flatMap { p -> p.rows.flatMap { (_, row) -> row.mapNotNull { it?.key } } }
        val asks = order.filter { it in asked }.map { k ->
            val slot = d.slot(k)!!
            val spot = spots[k] ?: Spot(k)
            FormsGap(
                k, slot.shown, slot.accept, options(d, k, tested, random), chosen = !typed || k in freshKeys, page = slot.page,
                where = where[k].orEmpty(), before = spot.before, after = spot.after,
            )
        }
        val pages = asks.flatMap { g -> d.slot(g.key)?.let { listOf(it.page) + it.needs }.orEmpty() }.filter { it.isNotBlank() }.distinct()
        // the rows asked, said together at the end
        val said = sections.flatMap { s -> s.rows.filter { r -> r.cells.any { it?.key in asked } }.map { it.said } }.joinToString(". ")
        return Exercise.FormsTable(
            lemma = d.lemma, pos = d.pos, gender = d.gender, meaning = d.meaning?.takeIf { it.isNotBlank() },
            emoji = d.emoji?.takeIf { it.isNotBlank() }, sections = sections, gaps = asks, typed = typed, fresh = fresh,
            pages = pages, said = said, checked = d.table == "checked", data = d, grammar = asks.firstOrNull()?.page,
        )
    }

    // --- an answer -----------------------------------------------------------------------------------------------------

    private fun norm(s: String) = Grading.normalize(s, Lang.SL)
    private fun fold(s: String) = Grading.fold(s, Lang.SL)

    /** [answer] without the words already before the gap ("jaz delam" typed for "jaz ____"). */
    private fun bare(g: FormsGap, answer: String): String {
        val a = answer.trim()
        if (g.before.isBlank()) return a
        val words = a.split(Regex("\\s+"))
        val lead = g.before.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (words.size > lead.size && words.take(lead.size).map(::fold) == lead.map(::fold)) words.drop(lead.size).joinToString(" ") else a
    }

    /** Gap [g] answered [answer]: chosen (right when it is one it accepts), or [typed] (graded as a cloze: [Grading.check]). */
    fun grade(g: FormsGap, answer: String, typed: Boolean): ItemMark {
        val a = bare(g, answer)
        if (!typed) return ItemMark(if (norm(a) in g.accept.map(::norm)) Verdict.CORRECT else Verdict.WRONG, a, g.answer)
        val r = Grading.check(a, g.accept, Lang.SL)
        return ItemMark(r.verdict, a, r.expected.ifBlank { g.answer }, r.hint, r.marks)
    }

    /** The mistake kinds (POST /reviews `forms_cards`, the mistakes database's `forms_<kind>`; the node keeps the same list). */
    val KINDS: List<String> = CASE_NAMES.values.map { "case_$it" } + listOf("number_plural", "number_dual") +
        listOf("1sg", "2sg", "3sg", "1pl", "2pl", "3pl").map { "verb_person_$it" } +
        listOf("tense_past", "tense_future", "tense_imperative", "tense_negative", "adjective_gender")

    /**
     * What a wrong answer [answer] of slot [key] of [d] missed (the answer's own slots: those of the word's whose forms hold
     * it): a noun's case, or its number when the answer is the same case's in another number (plural, dual); a present's
     * person (the dual's number); a negative's, a future's, a past's or an imperative's tense (the dual's number); an
     * adjective's gender when the answer is another gender's, else its case (the nominative's number for a dual or
     * plural answered in another number).
     */
    fun kind(d: FormsCardData, key: String, answer: String): String? {
        val a = fold(answer.trim())
        val other = d.slots.filter { it.key != key && it.accept.any { f -> fold(f) == a } }.map { it.key.split('.') }
        val p = key.split('.')
        val dual = p.last().endsWith("du")
        return when (p[0]) {
            in CASES -> {
                val (case, num) = p[0] to p.getOrNull(1)
                val sameCase = other.any { it[0] == case && it.getOrNull(1) != num }
                when {
                    sameCase && num == "pl" -> "number_plural"
                    sameCase && num == "du" -> "number_dual"
                    else -> "case_${CASE_NAMES.getValue(case)}"
                }
            }
            "pres" -> if (dual) "number_dual" else "verb_person_${p.getOrNull(1)}".takeIf { it in KINDS }
            "neg" -> if (dual) "number_dual" else "tense_negative"
            "fut" -> if (dual) "number_dual" else "tense_future"
            "past" -> if (dual) "number_dual" else "tense_past"
            "imp" -> if (dual) "number_dual" else "tense_imperative"
            "adj" -> if (p.size == 3) {
                // the nominative: adj.<gender>.<number>
                val (g, n) = p[1] to p[2]
                val nominative = other.filter { it.size == 3 && it[0] == "adj" }
                when {
                    nominative.any { it[2] == n && it[1] != g } -> "adjective_gender"
                    n == "du" -> "number_dual"
                    n == "pl" && nominative.any { it[2] != n } -> "number_plural"
                    else -> "adjective_gender"
                }
            } else {
                // a case: adj.<case>.<gender>.<number>
                val (case, g, n) = Triple(p[1], p.getOrNull(2), p.getOrNull(3))
                if (other.any { it.size == 4 && it[1] == case && it[3] == n && it[2] != g }) "adjective_gender"
                else CASE_NAMES[case]?.let { "case_$it" }
            }
            else -> null
        }
    }

    /** Whether the card's gaps ([marks]) were all typed: a typed card, none of its gaps chosen (a fresh one, or after "✋ Izberi raje"). */
    fun typed(ex: Exercise.FormsTable, marks: List<ItemMark>): Boolean = ex.typed && ex.gaps.none { it.chosen } && marks.none { it.hinted }

    /** The card's verdict over its gaps: right when every gap was, almost from [Sheets.ALMOST_SHARE], else not quite. */
    fun verdict(marks: List<ItemMark>): Verdict = Sheets.verdict(marks.map { it.verdict })

    /**
     * The card's SM-2 quality from its gaps: all right 4 (typed 5); right but a č, š or ž forgotten 3 (typed 4); almost
     * ([Sheets.ALMOST_SHARE] of its score) 3; half 2; less 1 (a review failed: due again tomorrow, its part again).
     */
    fun quality(marks: List<Verdict>, typed: Boolean): Int {
        val wrong = marks.count { it == Verdict.WRONG }
        val almost = marks.count { it == Verdict.ALMOST }
        return when {
            marks.isEmpty() -> 1
            wrong == 0 && almost == 0 -> if (typed) 5 else 4
            wrong == 0 -> if (typed) 4 else 3
            Sheets.verdict(marks) != Verdict.WRONG -> 3
            Sheets.score(marks) >= 0.5 * marks.size -> 2
            else -> 1
        }
    }

    /**
     * What a card's gaps ([marks]) count on the grammar book: each page its gaps are of (their slots' rule), right when every
     * gap of it was, wrong with the first slip's answer and form.
     */
    fun ruleAnswers(ex: Exercise.FormsTable, marks: List<ItemMark>): List<RuleAnswer> {
        val byPage = LinkedHashMap<String, MutableList<Int>>()
        ex.gaps.forEachIndexed { i, g -> if (i < marks.size && g.page.isNotBlank()) byPage.getOrPut(g.page) { mutableListOf() } += i }
        return byPage.map { (page, idx) ->
            val slip = idx.firstOrNull { marks[it].verdict == Verdict.WRONG }
            val verdict = when {
                slip != null -> Verdict.WRONG
                idx.all { marks[it].verdict == Verdict.CORRECT } -> Verdict.CORRECT
                else -> Verdict.ALMOST
            }
            val said = idx.joinToString(", ") { cellText(ex.gaps[it]) }
            RuleAnswer(page, verdict, said.takeIf { verdict == Verdict.CORRECT }, slip?.let { marks[it].answer.ifBlank { "…" } }.orEmpty(), slip?.let { cellText(ex.gaps[it]) })
        }
    }

    private fun cellText(g: FormsGap): String = listOf(g.before, g.answer + g.after).filter { it.isNotBlank() }.joinToString(" ")

    /** How card [cardId] went, for POST /reviews `forms_cards`: each gap, right or not, the kind of a wrong one. */
    fun wire(cardId: String, ex: Exercise.FormsTable, marks: List<ItemMark>): JsonObject = buildJsonObject {
        put("item_id", cardId)
        put("lemma", ex.lemma)
        put("typed", typed(ex, marks))
        put("fresh", ex.fresh)
        put("cells", buildJsonArray {
            ex.gaps.zip(marks).forEach { (g, m) ->
                add(buildJsonObject {
                    put("key", g.key)
                    put("right", m.verdict != Verdict.WRONG)
                    if (m.verdict == Verdict.WRONG) ex.data?.let { kind(it, g.key, m.answer) }?.let { put("kind", it) }
                    put("answer", m.answer.take(80))
                    put("expected", g.answer.take(80))
                })
            }
        })
    }

    // --- which words, how many ---------------------------------------------------------------------------------------

    /** Whether a word's [entries] (GET /forms) may make a card: a noun, verb or adjective with forms enough. */
    fun hasForms(entries: List<LemmaForms>?): Boolean = entries.orEmpty().any { it.pos in POS && it.slots.size > MIN_SLOTS }

    /**
     * The words to ask the node for cards of, best first: the learner's learned one-word vocabulary cards ([pool], the
     * [learned] ids) whose forms the phone has ([entries]: [hasForms]), without a forms card ([have]) and not refused
     * today ([refused]); the core verbs first ([CORE]), then the best known; at most [ASK_MAX]. Each (lemma, its card's id).
     */
    fun candidates(
        pool: List<ReviewCard>, learned: Set<String>, have: List<ReviewCard>, entries: (String) -> List<LemmaForms>?,
        refused: Set<String> = emptySet(),
    ): List<Pair<String, String>> {
        val made = have.flatMap { listOfNotNull(it.forms?.lemma, it.front) }.map(::key).toSet()
        fun core(w: String) = CORE.indexOf(key(w)).let { if (it < 0) CORE.size else it }
        return pool.asSequence()
            .filter { it.kind == "vocabulary" && it.id in learned && !it.lookup }
            .mapNotNull { c -> WordFormsWire.lemmaOf(c)?.let { it to c } }
            .filter { (w, _) -> key(w) !in made && key(w) !in refused && hasForms(entries(w)) }
            .sortedWith(compareBy<Pair<String, ReviewCard>>({ core(it.first) }, { -it.second.repetitions }))
            .distinctBy { key(it.first) }
            .take(ASK_MAX)
            .map { (w, c) -> w to c.id }
            .toList()
    }

    /** How many new forms cards today may still bring of their own accord: [NEW_PER_DAY] less those made [today] (on demand ones don't count). */
    fun room(cards: List<ReviewCard>, today: LocalDate): Int =
        (NEW_PER_DAY - cards.count { it.created == today && it.forms?.asked != true }).coerceAtLeast(0)

    /** The forms card of [lemma] among [cards], if any. */
    fun cardOf(cards: List<ReviewCard>, lemma: String): ReviewCard? =
        cards.firstOrNull { key(it.forms?.lemma ?: it.front) == key(lemma) }
}
