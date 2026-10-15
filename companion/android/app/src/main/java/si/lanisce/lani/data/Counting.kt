package si.lanisce.lani.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.School
import si.lanisce.lani.l10n.Lang
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * What a counting card keeps of its noun (the review item's `counting`, made by the bridge from the dictionary:
 * companion/bridge/src/counting.ts): its [lemma] and [gender], the dictionary's [forms] of each count's slot ([Counting.SLOTS]:
 * every one is right), the four [lines] the card shows ("ena kava", "dve kavi", "tri kave", "pet kav"), [others] of the
 * noun's forms for the wrong choices, its [emoji] where a pack has one, and what it [meaning]s (the learner's card of it).
 */
@Serializable
data class CountingForms(
    val lemma: String,
    val gender: String,
    val forms: Map<String, List<String>> = emptyMap(),
    val lines: List<String> = emptyList(),
    val others: List<String> = emptyList(),
    val emoji: String? = null,
    val meaning: String? = null,
    val table: String? = null,
    val v: Int = 1,
) {
    /** Whole: a gender of the three, the four lines, a form of each count. */
    val whole: Boolean
        get() = gender in Counting.NUMERALS && lines.size == Counting.SLOTS.size && lines.all { ' ' in it.trim() } &&
            Counting.SLOTS.all { s -> forms[s].orEmpty().any { it.isNotBlank() } }

    /** The dictionary's forms of count [i] (0: 1, 1: 2, 2: 3, 3: 5). */
    fun formsOf(i: Int): List<String> = forms[Counting.SLOTS[i]].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }

    /** The form line [i] shows: the line without its numeral ("kavi" of "dve kavi"). */
    fun shown(i: Int): String = lines[i].trim().substringAfter(' ')
}

/** A noun POST /counting made no card of, and why ("not a word the learner has learned", "no nom.du in the dictionary"). */
@Serializable
data class CountingSkip(val word: String, val why: String = "")

/** A card POST /counting made: its review item, its noun, its four lines. */
@Serializable
data class CountingMade(val item_id: String, val lemma: String, val lines: List<String> = emptyList())

/** POST /counting's answer: the cards made, the nouns skipped, today's cards and the day's most. */
@Serializable
data class CountingAnswer(
    val added: List<CountingMade> = emptyList(),
    val skipped: List<CountingSkip> = emptyList(),
    val made_today: Int = 0,
    val per_day: Int = Counting.NEW_PER_DAY,
)

/**
 * What a wrong step of a counting card was wrong about (POST /reviews `counting`, the mistakes by kind): the numeral's
 * agreement with the noun's gender (1's en / ena / eno; dva / dve, trije / tri with the noun's form right), the dual (2),
 * the plural (3–4), the genitive plural (5 and more).
 */
enum class CountKind(val wire: String) { GENDER("gender"), DUAL("dual"), PLURAL("plural"), GENITIVE_PLURAL("genitive_plural") }

/**
 * Counting cards ("🔢 Štetje · Counting"; companion/README.md "Counting cards"): once the learner has done the intro of the
 * A1 unit stevila ([active]), the bridge makes a card of each noun they have learned, a few a day ([candidates], [room]:
 * POST /counting), from the dictionary's forms; each its own spaced-repetition card, a few of them spread among the
 * others in each review ([interleave]). Pure.
 *
 * - **The steps** ([exercise]): the noun → en / ena / eno; then 2, 3 and 5, each the whole phrase (dve kavi, tri kave,
 *   pet kav), because the numerals agree too (dva or dve, trije or tri).
 * - **Choosing, then typing** ([secure]): three phrases to choose from ([distractors]: the other numeral, another number's
 *   ending; never one the dictionary accepts) while the card is new; typed once it is secure.
 * - **Grading** ([grade], [quality]): a step is right with any of the dictionary's forms; a typed one as the app grades a
 *   cloze (č, š, ž forgotten almost, never the ending). Each wrong one has its [kind]; the steps count on the grammar
 *   book's pages ([ruleAnswers]) and go to the bridge ([wire]).
 */
object Counting {
    /** The card's type in spaced-repetition.json. */
    const val KIND = "counting"
    /** What GET /state's `features` says of a bridge that makes counting cards (POST /counting). */
    const val FEATURE = "counting"
    const val LANGUAGE = "sl"
    /** The course unit whose intro turns the cards on. */
    const val UNIT = "stevila"
    /** The grammar book's pages: the counting rule's, the gender's (1), the dual's (2), the plural's (3). */
    const val PAGE = "stevila-samostalniki"
    const val GENDER_PAGE = "spol-samostalnikov"
    const val DUAL_PAGE = "dvojina"
    const val PLURAL_PAGE = "mnozina"

    /** The forms book's slot of each count: 1 the nominative singular, 2 the dual, 3 the plural, 5 the genitive plural. */
    val SLOTS = listOf("nom.sg", "nom.du", "nom.pl", "gen.pl")
    /** The number each step shows. */
    val NUMBERS = listOf(1, 2, 3, 5)
    /** The numerals that agree with each gender, by count. */
    val NUMERALS = mapOf(
        "m" to listOf("en", "dva", "trije", "pet"),
        "f" to listOf("ena", "dve", "tri", "pet"),
        "n" to listOf("eno", "dve", "tri", "pet"),
    )
    /** Step 1's choices, always in this order. */
    val ONES = listOf("en", "ena", "eno")

    /** New counting cards a day at most (the bridge says the same: LANI_COUNTING_PER_DAY). */
    const val NEW_PER_DAY = 3
    /** Counting cards in one review at most: the rest wait for the next. */
    const val PER_REVIEW = 4
    /** The nouns one request names at most. */
    const val ASK_MAX = 12
    /** Secure: this many good reviews in a row ([ReviewCard.repetitions]), the last of at least [SECURE_QUALITY]. */
    const val SECURE_REPS = 2
    const val SECURE_QUALITY = 4

    /** The item's `counting` as the node keeps it; null when it doesn't read or isn't whole ([CountingForms.whole]). */
    fun parse(e: JsonElement?): CountingForms? =
        (e as? JsonObject)?.let { runCatching { json.decodeFromJsonElement(CountingForms.serializer(), it) }.getOrNull() }?.takeIf { it.whole }

    // --- when, and which nouns ---------------------------------------------------------------------------------------

    /** Counting is on: the intro of the unit stevila was done (finished, or skipped as known). */
    fun active(s: GameState?): Boolean = s != null && School.progress(s, LANGUAGE, UNIT)?.introDone == true

    /** Whether a noun's [entries] (GET /forms) make a card: a noun with a gender and a form of each count. */
    fun countable(entries: List<LemmaForms>?): Boolean =
        entries.orEmpty().any { e -> e.pos == "noun" && e.gender in NUMERALS && SLOTS.all { k -> e.slot(k)?.forms?.any { it.isNotBlank() } == true } }

    /**
     * The nouns to ask the bridge for cards of, best first: the learner's learned one-word vocabulary cards ([pool], the
     * [learned] ids) the dictionary has as countable nouns ([entries], the forms the phone has: [countable]), without a
     * counting card yet ([have]) and not refused today ([refused]); the best known first (most good reviews in a row), at
     * most [ASK_MAX]. Each is (lemma, its card's id).
     */
    fun candidates(
        pool: List<ReviewCard>, learned: Set<String>, have: List<ReviewCard>, entries: (String) -> List<LemmaForms>?,
        refused: Set<String> = emptySet(),
    ): List<Pair<String, String>> {
        val counted = have.flatMap { listOfNotNull(it.counting?.lemma, it.front) }.map { key(it) }.toSet()
        return pool.asSequence()
            .filter { it.kind == "vocabulary" && it.id in learned && !it.lookup }
            .mapNotNull { c -> WordFormsWire.lemmaOf(c)?.let { it to c } }
            .filter { (w, _) -> key(w) !in counted && key(w) !in refused && countable(entries(w)) }
            .sortedByDescending { it.second.repetitions }
            .distinctBy { key(it.first) }
            .take(ASK_MAX)
            .map { (w, c) -> w to c.id }
            .toList()
    }

    /** How many new counting cards today may still bring: [NEW_PER_DAY] less those made [today]. */
    fun room(counting: List<ReviewCard>, today: LocalDate): Int = (NEW_PER_DAY - counting.count { it.created == today }).coerceAtLeast(0)

    fun key(w: String): String = w.trim().lowercase()

    /**
     * The review's cards: [others] with up to [per] of the [counting] cards due spread evenly among them, [max] in all
     * (the counting cards first kept: the others fill the rest).
     */
    fun interleave(others: List<ReviewCard>, counting: List<ReviewCard>, max: Int = Dashboard.MAX_DUE, per: Int = PER_REVIEW): List<ReviewCard> {
        val k = minOf(per, counting.size, max)
        val rest = others.take(max - k)
        if (k == 0) return rest
        val out = ArrayList<ReviewCard>(rest.size + k)
        val gap = rest.size.toDouble() / (k + 1)
        var next = 0
        for (j in 0 until k) {
            val before = ((j + 1) * gap).roundToInt()
            while (next < before) out += rest[next++]
            out += counting[j]
        }
        while (next < rest.size) out += rest[next++]
        return out
    }

    // --- the card ----------------------------------------------------------------------------------------------------

    /** The card is secure: [SECURE_REPS] good reviews in a row, the last of [SECURE_QUALITY] or more (no slip). */
    fun secure(c: ReviewCard): Boolean = c.repetitions >= SECURE_REPS && c.lastQuality >= SECURE_QUALITY

    /** The numeral of count [i] for [f]'s gender. */
    fun numeral(f: CountingForms, i: Int): String = NUMERALS.getValue(f.gender)[i]

    /** What count [i] of [f] accepts: its numeral with any of the dictionary's forms (for 1 the numeral alone too). */
    fun accept(f: CountingForms, i: Int): List<String> {
        val n = numeral(f, i)
        val phrases = (listOf(f.shown(i)) + f.formsOf(i)).distinct().map { "$n $it" }
        return if (i == 0) listOf(n) + phrases else phrases
    }

    /**
     * Two wrong phrases for count [i] (1: 2, 2: 3, 3: 5) of [f], never one it [accept]s: the other number's ending with
     * the right numeral and the other numeral (dve kave · dva kava for dve kavi; trije sinova · tri sinovi for trije
     * sinovi), then the other combinations, the other cases last ([CountingForms.others]); fewer when the noun's forms
     * are the same (pesem: pesmi everywhere).
     */
    fun distractors(f: CountingForms, i: Int): List<String> {
        val right = numeral(f, i)
        val other = when (i) {
            1 -> if (right == "dva") "dve" else "dva"
            2 -> if (right == "trije") "tri" else "trije"
            else -> right
        }
        val (one, two, three, five) = (0..3).map(f::shown)
        val candidates = when (i) {
            1 -> listOf("$right $three", "$other $one", "$other $two", "$right $one", "$right $five")
            2 -> listOf("$right $two", "$other $three", "$right $five", "$other $two", "$right $one")
            3 -> listOf("$right $three", "$right $one", "$right $two")
            else -> emptyList()
        } + f.others.map { "$right ${it.trim()}" }
        val taken = accept(f, i).map(::norm).toMutableSet()
        val out = mutableListOf<String>()
        for (c in candidates) {
            if (out.size == 2) break
            // a form missing leaves the numeral alone: no phrase
            if (' ' !in c.trim() || !taken.add(norm(c))) continue
            out += c
        }
        return out
    }

    /**
     * Step [i] of [f]: what it asks, what it accepts, and what to choose from (step 1 en / ena / eno; the others three
     * phrases, shuffled): a typed step keeps its options for "✋ Izberi raje · Let me choose".
     */
    fun step(f: CountingForms, i: Int, random: Random): CountStep {
        val n = numeral(f, i)
        val answer = if (i == 0) n else f.lines[i].trim()
        val options = if (i == 0) ONES else (listOf(answer) + distractors(f, i)).shuffled(random)
        return CountStep(NUMBERS[i], answer, f.lines[i].trim(), accept(f, i), options, n, f.formsOf(i))
    }

    /** The exercise of a card with forms [f]: its four steps, chosen or [typed]; [why]: step 1's line about the gender. */
    fun exercise(f: CountingForms, typed: Boolean, why: String?, random: Random): Exercise.Counting = Exercise.Counting(
        lemma = f.lemma, gender = f.gender, steps = (0..3).map { step(f, it, random) }, typed = typed,
        emoji = f.emoji?.takeIf { it.isNotBlank() }, meaning = f.meaning?.takeIf { it.isNotBlank() }, why = why,
        pages = listOf(PAGE, GENDER_PAGE), grammar = PAGE,
    )

    /**
     * What a noun's end says of its gender ([gender]: the dictionary's, never guessed), as step 1's feedback says it
     * (the unit spol's rule): a consonant → masculine, -a → feminine, -o or -e → neuter, and the exception when the noun is
     * one (oče: masculine; vas, noč: feminine); another end, its gender plainly. The string table's key and its arguments
     * ([line]: "ena kava").
     */
    fun why(lemma: String, gender: String, line: String): Pair<String, Map<String, Any>> {
        val word = lemma.trim()
        val last = word.lowercase().lastOrNull()
        val args = mapOf<String, Any>("word" to word, "end" to (last?.toString() ?: ""), "gender" to gender, "line" to line)
        val vowel = last != null && last in "aeiou"
        val expected = when {
            last == null || !last.isLetter() -> null
            !vowel -> "m"
            last == 'a' -> "f"
            last == 'o' || last == 'e' -> "n"
            else -> null
        }
        val key = when {
            expected == null -> "counting.whyPlain"
            !vowel -> if (gender == expected) "counting.whyConsonant" else "counting.whyConsonantOdd"
            else -> if (gender == expected) "counting.whyVowel" else "counting.whyVowelOdd"
        }
        return key to args
    }

    // --- an answer -----------------------------------------------------------------------------------------------------

    private fun norm(s: String) = Grading.normalize(s, Lang.SL)
    private fun fold(s: String) = Grading.fold(s, Lang.SL)

    /**
     * Step [s] answered [answer]: chosen (one of its options: right when it is one it accepts), or [typed] (graded as a
     * cloze: [Grading.check]; step 1 by its numeral, the noun after it free).
     */
    fun grade(s: CountStep, answer: String, typed: Boolean): ItemMark {
        val a = answer.trim()
        if (!typed) return ItemMark(if (norm(a) in s.accept.map(::norm)) Verdict.CORRECT else Verdict.WRONG, a, s.line)
        if (s.n == 1) {
            val first = a.split(Regex("\\s+")).firstOrNull().orEmpty()
            val r = Grading.check(first, listOf(s.numeral), Lang.SL)
            return ItemMark(r.verdict, a, s.line, r.hint)
        }
        val r = Grading.check(a, s.accept, Lang.SL)
        return ItemMark(r.verdict, a, r.expected.ifBlank { s.line }, r.hint, r.marks)
    }

    /**
     * What a wrong answer [answer] of step [s] was wrong about: 1 the gender; 5 the genitive plural; 2 and 3 the gender
     * when only the numeral was wrong (dva kavi), else the dual or the plural.
     */
    fun kind(s: CountStep, answer: String): CountKind = when (s.n) {
        1 -> CountKind.GENDER
        5 -> CountKind.GENITIVE_PLURAL
        else -> {
            // č, š, ž forgotten aren't what the step was wrong about
            val w = fold(answer).split(' ')
            val numeralRight = w.firstOrNull() == fold(s.numeral)
            val nounRight = w.drop(1).joinToString(" ") in s.forms.map(::fold)
            if (!numeralRight && nounRight) CountKind.GENDER else if (s.n == 2) CountKind.DUAL else CountKind.PLURAL
        }
    }

    /**
     * Whether a card's steps ([marks]) were all typed: a typed card ([Exercise.Counting.typed]) whose steps were none of them
     * chosen after "✋ Izberi raje · Let me choose" (such a step is marked [ItemMark.hinted]).
     */
    fun typed(ex: Exercise.Counting, marks: List<ItemMark>): Boolean = ex.typed && marks.none { it.hinted }

    /** The card's verdict over its steps: right when every step was, almost from [Sheets.ALMOST_SHARE], else not quite. */
    fun verdict(marks: List<ItemMark>): Verdict = Sheets.verdict(marks.map { it.verdict })

    /**
     * The card's SM-2 quality from its steps, as its verdict goes ([Sheets.verdict]): all right 4 (typed 5); right but a
     * č, š or ž forgotten 3 (typed 4); almost (one wrong, the rest right) 3; not quite (two wrong, or one and a č
     * forgotten) 2, three or four wrong 1: a review failed, due again tomorrow.
     */
    fun quality(marks: List<Verdict>, typed: Boolean): Int {
        val wrong = marks.count { it == Verdict.WRONG }
        val almost = marks.count { it == Verdict.ALMOST }
        return when {
            marks.isEmpty() -> 1
            wrong == 0 && almost == 0 -> if (typed) 5 else 4
            wrong == 0 -> if (typed) 4 else 3
            Sheets.verdict(marks) != Verdict.WRONG -> 3
            wrong <= 2 -> 2
            else -> 1
        }
    }

    /**
     * What a card's steps ([marks]) count on the grammar book: the counting rule all four (right when every step was), the
     * gender step 1 and a numeral's agreement got wrong later, the dual step 2 and the plural step 3 (their forms: a wrong
     * numeral there is the gender's). A wrong one keeps what was said and what was right.
     */
    fun ruleAnswers(ex: Exercise.Counting, marks: List<ItemMark>): List<RuleAnswer> {
        if (marks.isEmpty()) return emptyList()
        val kinds = ex.steps.zip(marks).map { (s, m) -> if (m.verdict == Verdict.WRONG) kind(s, m.answer) else null }
        fun answer(rule: String, idx: List<Int>, slipKinds: Set<CountKind>?, said: String?): RuleAnswer? {
            val ms = idx.filter { it < marks.size }
            if (ms.isEmpty()) return null
            val slip = ms.firstOrNull { marks[it].verdict == Verdict.WRONG && (slipKinds == null || kinds[it] in slipKinds) }
            val counted = ms.filter { marks[it].verdict != Verdict.WRONG || slip == it || slipKinds == null }
            if (slip == null && counted.isEmpty()) return null
            val verdict = when {
                slip != null -> Verdict.WRONG
                counted.all { marks[it].verdict == Verdict.CORRECT } -> Verdict.CORRECT
                else -> Verdict.ALMOST
            }
            return RuleAnswer(
                rule, verdict, said.takeIf { verdict == Verdict.CORRECT },
                slip?.let { marks[it].answer.ifBlank { "…" } }.orEmpty(), slip?.let { ex.steps[it].line },
            )
        }
        val all = marks.indices.toList()
        val genderAt = all.filter { it == 0 || kinds[it] == CountKind.GENDER }
        return listOfNotNull(
            answer(PAGE, all, null, ex.said),
            answer(GENDER_PAGE, genderAt, setOf(CountKind.GENDER), ex.steps.firstOrNull()?.line),
            answer(DUAL_PAGE, listOf(1), setOf(CountKind.DUAL), ex.steps.getOrNull(1)?.line),
            answer(PLURAL_PAGE, listOf(2), setOf(CountKind.PLURAL), ex.steps.getOrNull(2)?.line),
        )
    }

    /** How card [cardId] went, for POST /reviews `counting`: each step, right or not, the kind of a wrong one. */
    fun wire(cardId: String, ex: Exercise.Counting, marks: List<ItemMark>): JsonObject = buildJsonObject {
        put("item_id", cardId)
        put("lemma", ex.lemma)
        put("typed", typed(ex, marks))
        put("steps", buildJsonArray {
            ex.steps.zip(marks).forEach { (s, m) ->
                add(buildJsonObject {
                    put("n", s.n)
                    put("right", m.verdict != Verdict.WRONG)
                    if (m.verdict == Verdict.WRONG) put("kind", kind(s, m.answer).wire)
                    put("answer", m.answer.take(80))
                    put("expected", s.line.take(80))
                })
            }
        })
    }
}
