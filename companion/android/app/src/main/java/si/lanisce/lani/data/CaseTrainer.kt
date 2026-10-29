package si.lanisce.lani.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.Introduction
import si.lanisce.lani.game.WordForms
import si.lanisce.lani.l10n.Lang
import java.time.LocalDate
import kotlin.random.Random

// The case trainer ("🧩 Skloni · Cases"; companion/README.md "Case trainer", docs/plans/05-built/case-trainer.md): short
// sessions on the six cases, three kinds interleaved, picked towards the learner's weak cases and rules (their mistakes
// count from the start), a missed item back by its own spaced repetition, each answer counted per case and per rule,
// what went wrong ready for the learner databases. Pure: the controller (app/CasesController.kt) gives it the bank, the
// learner's nouns, what is introduced, their mistakes and the village state's progress.

/** The trainer's three kinds: "Kateri sklon? · Which case?", "Kje ali kam?", "Sklanjaj! · Decline it". */
enum class CaseKind(val wire: String, val emoji: String, val title: String, val about: String) {
    WHICH("which", "🧩", "cases.kind.which", "cases.kind.which.about"),
    PLACE("place", "🧭", "cases.kind.place", "cases.kind.place.about"),
    DECLINE("decline", "✍️", "cases.kind.decline", "cases.kind.decline.about");

    companion object {
        fun of(wire: String?): CaseKind? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * The learner's way through the trainer (the village state's `cases`, [CaseTrainer]): each key's last answers ([keys]:
 * "case:rodilnik", "rule:rodilnik-predlogi", "kind:which" → "1" right or almost, "0" wrong, the newest last, at most
 * [CaseTrainer.WINDOW]), each rule's taps of the deciding word ([why], "1" right), the items missed that come back
 * ([again]), the rules the case lens showed ([seen]), the items asked lately ([recent], newest last), the sessions played
 * ([runs]) and the last one's [day]. An older app leaves it out; the bridge keeps it then (PUT /game).
 */
@Serializable
data class CaseProgress(
    val v: Int = 1,
    val keys: Map<String, String> = emptyMap(),
    val why: Map<String, String> = emptyMap(),
    val again: List<CaseAgain> = emptyList(),
    val seen: Map<String, CaseSeen> = emptyMap(),
    val recent: List<String> = emptyList(),
    val runs: Int = 0,
    val day: String = "",
)

/** An item missed, to come back: its key ([CaseTask.key]), due on [due] (ISO), its [box] in [CaseTrainer.INTERVALS]. */
@Serializable
data class CaseAgain(val item: String, val due: String, val box: Int = 0)

/** A rule the case lens showed the learner: how often ([n]) and the last day. A "seen" signal: never a pass. */
@Serializable
data class CaseSeen(val n: Int = 0, val day: String = "")

/**
 * A noun the frames decline: the learner's own (one of their cards whose forms the phone has: [card]) or the bank's; its
 * gender, what it means in the learner's base, its standard forms by slot ("gen.sg") and the spoken ones accepted too.
 */
data class CaseNoun(
    val lemma: String,
    val gender: String,
    val meaning: String,
    val forms: Map<String, List<String>>,
    val spoken: Map<String, List<String>> = emptyMap(),
    val card: String? = null,
) {
    /** A masculine noun for a living being: its accusative singular is its genitive (brata, psa). */
    val animate: Boolean get() = gender == "m" && forms["acc.sg"].orEmpty().any { it in forms["gen.sg"].orEmpty() } && forms["acc.sg"].orEmpty().none { it == lemma }
}

/** One task of a session: its [kind], its [key] (a bank item's id, or "d:<frame>:<noun>[:<adjective>]"), what it asks. */
data class CaseTask(
    val kind: CaseKind,
    val key: String,
    val case: Kase,
    val number: String,
    val rule: String,
    val level: String,
    val again: Boolean = false,
    /** A decline task's adjective or possessive (agreement), if any. */
    val adjective: String? = null,
    val gender: String? = null,
)

/** A task with its exercise, as the run plays it. */
data class CasePlanned(val task: CaseTask, val exercise: Exercise.CaseTask)

/** What the trainer may ask now: the bank, the learner's nouns, the pages introduced, the level, whether a child, the base. */
data class CasePool(
    val bank: CaseBank,
    val nouns: List<CaseNoun>,
    val introduced: Set<String>,
    val level: String,
    val child: Boolean = false,
    val base: String = "en",
    /** A grammar page's level ("A2"); null for a page the book lacks. */
    val pageLevel: (String) -> String? = { null },
    /** The scenes whose picture can show (open in the village): a place item's picture. */
    val scenes: Set<String> = emptySet(),
)

/** An answer of the run: its verdict (the case), whether the deciding word was tapped right (null: not asked), what was answered. */
data class CaseAnswer(val verdict: Verdict, val whyRight: Boolean?, val answer: String, val expected: String, val lookedUp: Boolean = false)

/** A session recorded: the progress after it, and the keys of what comes back. */
data class CasesRecorded(val progress: CaseProgress, val missed: List<String>)

object CaseTrainer {
    const val LANGUAGE = "sl"
    /** Tasks in a session; a child's are fewer. */
    const val RUN = 10
    const val RUN_CHILD = 8
    /** A key's last answers kept. */
    const val WINDOW = 12
    /** Items missed coming back in one session at most. */
    const val MAX_AGAIN = 3
    /** The days after which an item missed comes back, by its box (0: the next session, the same day). */
    val INTERVALS = listOf(0, 1, 3, 7, 16)
    /** Items missed kept at most; items asked lately kept. */
    const val MAX_KEPT = 60
    const val RECENT = 40
    /** A child's sentences: at most this many words (and no B1). */
    const val CHILD_WORDS = 7
    /** A rule the lens showed in the last days weighs a little more. */
    const val SEEN_DAYS = 3L
    /** The shares of a session's kinds (which, place, decline), before the learner's accuracy and the focus move them. */
    val SHARES = mapOf(CaseKind.WHICH to 0.45, CaseKind.PLACE to 0.25, CaseKind.DECLINE to 0.30)
    /** The agreement's rule: a decline task with an adjective or a possessive counts on it too. */
    const val AGREEMENT = "pridevniki-ujemanje"
    /** The pages a plural and a dual need, and an oblique dual. */
    const val PLURAL = "mnozina"
    const val DUAL = "dvojina"
    const val DUAL_CASES = "dvojina-skloni"
    /** The trainer is open once this many items can be asked. */
    const val OPEN_AT = 6

    // --- what may be asked ---------------------------------------------------------------------------------------------

    private fun rank(level: String?): Int = Introduction.rank(level) ?: 0

    /** Page [id] is open to the learner: introduced (met in the book, a unit's intro), or at or below their level. */
    fun open(pool: CasePool, id: String): Boolean = id in pool.introduced || pool.pageLevel(id)?.let { rank(it) <= rank(pool.level) } ?: false

    /** An item or a frame of [level] fits: at most the learner's level, or one above when its rule is introduced; a child's no B1. */
    private fun fits(pool: CasePool, level: String, rule: String): Boolean {
        if (pool.child && rank(level) >= rank("B1")) return false
        val above = if (rule in pool.introduced) 1 else 0
        return rank(level) <= rank(pool.level) + above
    }

    /** The pages a number needs: the plural's, the dual's, an oblique dual's. */
    private fun numberOpen(pool: CasePool, case: Kase, number: String): Boolean = when (number) {
        "pl" -> open(pool, PLURAL)
        "du" -> open(pool, DUAL) && (case == Kase.IMENOVALNIK || case == Kase.TOZILNIK || open(pool, DUAL_CASES))
        else -> true
    }

    /** Whether bank item [it] may be asked now. */
    fun allowed(pool: CasePool, it: CaseItem): Boolean {
        val kase = Kase.of(it.case) ?: return false
        val rule = pool.bank.page(it.why) ?: return false
        if (pool.bank.rule(it.why)?.case != it.case) return false
        if (it.kind == "place" && (it.gap.size != 2 || it.options.size !in 2..3 || it.answer !in it.options.indices)) return false
        if (it.word !in it.tokens.indices) return false
        if (pool.child && it.tokens.size > CHILD_WORDS) return false
        // the rule's page decides, not the case's: the genitive after a no is A1, the genitive's own page A2
        return open(pool, rule) && numberOpen(pool, kase, it.number) && fits(pool, it.level, rule)
    }

    /** Whether frame [f] may be asked now (its nouns aside). */
    fun allowed(pool: CasePool, f: CaseFrame): Boolean {
        val kase = Kase.of(f.case) ?: return false
        val rule = pool.bank.page(f.why) ?: return false
        if (pool.bank.rule(f.why)?.case != f.case) return false
        return open(pool, rule) && numberOpen(pool, kase, f.number) && f.needs.all { open(pool, it) } && fits(pool, f.level, rule)
    }

    /** The learner's nouns, else (too few of their own) the bank's too: every noun a frame may take. */
    fun nouns(pool: CasePool): List<CaseNoun> {
        val own = pool.nouns.filter { it.gender in setOf("m", "f", "n") }
        if (own.size >= 6) return own
        val ownLemmas = own.map { it.lemma.lowercase() }.toSet()
        val bank = pool.bank.nouns.filter { rank(it.level) <= rank(pool.level) && it.lemma.lowercase() !in ownLemmas }.map { n ->
            CaseNoun(n.lemma, n.gender, CaseTexts(n.en, n.de, n.it).inBase(pool.base), n.slots)
        }
        return own + bank
    }

    /** The adjectives and possessives the learner may decline: agreement open, of their level. */
    fun adjectives(pool: CasePool): List<CaseAdjective> =
        if (!open(pool, AGREEMENT)) emptyList() else pool.bank.adjectives.filter { fits(pool, it.level, AGREEMENT) && it.forms.isNotEmpty() }

    /** How many things the trainer can ask now (a bank item each, a frame with a noun each): open from [OPEN_AT]. */
    fun count(pool: CasePool, enough: Int = Int.MAX_VALUE): Int {
        val items = pool.bank.items.count { allowed(pool, it) }
        if (items >= enough) return items
        val nouns = nouns(pool)
        val frames = pool.bank.frames.filter { allowed(pool, it) }.sumOf { f -> nouns.count { n -> nounFits(f, n) } }
        return items + frames
    }

    /**
     * Whether the trainer is open: the cases begun (a case's page or one of their rules' introduced: the unit tožilnik's
     * intro, a page met in the book; or the learner at A2 or above), and enough to ask.
     */
    fun isOpen(pool: CasePool): Boolean =
        (rank(pool.level) >= rank("A2") || pool.introduced.any { it in CaseLook.PAGES }) && count(pool, OPEN_AT) >= OPEN_AT

    private fun slotOf(case: Kase, number: String) = "${case.slot}.$number"

    private fun nounFits(f: CaseFrame, n: CaseNoun): Boolean {
        val kase = Kase.of(f.case) ?: return false
        if (f.gender != null && n.gender !in f.gender) return false
        if (n.forms[slotOf(kase, f.number)].isNullOrEmpty()) return false
        // a word the frame's sentence has already
        return n.lemma.lowercase() !in f.sl.lowercase().split(Regex("[^\\p{L}]+"))
    }

    // --- weakness and mastery ------------------------------------------------------------------------------------------

    /** [key]'s last answers right (or almost), of at most [WINDOW]. */
    fun score(p: CaseProgress?, key: String): Pair<Int, Int> {
        val l = p?.keys?.get(key).orEmpty()
        return l.count { it == '1' } to l.length
    }

    /**
     * How weak [key] is, 0 … 1: its wrong answers lately, smoothed, with the learner's mistakes on it ([prior], 0 … 1) as
     * virtual wrong answers that fade as the trainer's own come in (all gone after [WINDOW]): a case Jan got wrong nine
     * times weighs heavy at the first session, and less once they get it right here.
     */
    fun weakness(p: CaseProgress?, key: String, prior: Double): Double {
        val (right, n) = score(p, key)
        val virtual = prior.coerceIn(0.0, 1.0) * 4.0 * (1.0 - n.toDouble() / WINDOW).coerceAtLeast(0.0)
        return ((n - right) + virtual + 1.0) / (n + virtual + 2.0)
    }

    /** 0 … 5 stars of [key] (null: never asked): right of its last answers, capped while they are few. */
    fun stars(p: CaseProgress?, key: String): Int? {
        val (right, n) = score(p, key)
        if (n == 0) return null
        val s = Math.round(5.0 * right / n).toInt()
        val cap = when {
            n < 3 -> 2
            n < 6 -> 3
            n < 10 -> 4
            else -> 5
        }
        return s.coerceIn(0, cap)
    }

    /** The case keys' ids, rules' and kinds': "case:rodilnik". */
    fun caseKey(k: Kase) = "case:${k.id}"
    fun ruleKey(r: String) = "rule:$r"
    fun kindKey(k: CaseKind) = "kind:${k.wire}"

    /** The learner's accuracy lately over every kind (null with fewer than 6 answers). */
    fun accuracy(p: CaseProgress?): Double? {
        val all = CaseKind.entries.map { score(p, kindKey(it)) }
        val n = all.sumOf { it.second }
        return if (n < 6) null else all.sumOf { it.first }.toDouble() / n
    }

    /** The weakest case: the case to suggest on Home, with its weakness (mistakes counted as [priors] give); null when none is weak. */
    fun weakest(p: CaseProgress?, priors: (String) -> Double): Pair<Kase, Double>? =
        Kase.entries.map { it to weakness(p, caseKey(it), priors(it.id)) }.maxByOrNull { it.second }?.takeIf { it.second >= 0.55 }

    // --- the learner's mistakes: priors and pattern ids -----------------------------------------------------------------

    /** Words of a mistake's pattern id that say nothing on their own. */
    private val STOP = setOf("after", "with", "of", "the", "and", "vs", "versus", "in", "on", "to", "for", "case", "cases", "form", "forms", "ending", "endings", "error", "errors", "wrong", "mistake", "confusion", "use", "usage", "missing", "instead", "not", "word", "words")

    /** The words the facets know, each with the ones that mean the same. */
    private val SYNONYMS: Map<String, Set<String>> = mapOf(
        "nominative" to setOf("nominative", "nom", "imenovalnik", "subject"),
        "genitive" to setOf("genitive", "gen", "rodilnik"),
        "dative" to setOf("dative", "dat", "dajalnik"),
        "accusative" to setOf("accusative", "acc", "tozilnik", "object"),
        "locative" to setOf("locative", "loc", "mestnik"),
        "instrumental" to setOf("instrumental", "ins", "instr", "orodnik"),
        "singular" to setOf("singular", "sg"),
        "plural" to setOf("plural", "pl", "mnozina"),
        "dual" to setOf("dual", "du", "dvojina"),
        "masculine" to setOf("masculine", "masc"),
        "feminine" to setOf("feminine", "fem"),
        "neuter" to setOf("neuter", "neut"),
        "adjective" to setOf("adjective", "adjectives", "adj", "agreement", "agree", "gender", "pridevnik"),
        "possessive" to setOf("possessive", "possessives", "svojilni", "moj", "moje", "tvoj", "nas"),
        "pronoun" to setOf("pronoun", "pronouns", "zaimek"),
        "negation" to setOf("negation", "negative", "nikalnica", "neg", "nimam", "ni", "ne"),
        "quantity" to setOf("quantity", "kolicina", "amount"),
        "number" to setOf("numbers", "numeral", "five", "counting"),
        "possession" to setOf("possession", "whose", "cigav"),
        "kam" to setOf("kam", "direction", "motion", "where_to"),
        "kje" to setOf("kje", "location", "position"),
        "place" to setOf("place", "prepositions", "preposition", "prep"),
    )
    private val TO_FACET: Map<String, String> = SYNONYMS.flatMap { (f, ws) -> ws.map { it to f } }.toMap()
    /** Prepositions are facets of their own (iz, brez, k, pri, s …): one letter is one only if a preposition. */
    private val PREPOSITIONS = setOf("v", "na", "iz", "z", "s", "od", "do", "brez", "blizu", "mimo", "zaradi", "k", "h", "proti", "kljub", "pri", "ob", "po", "pod", "nad", "pred", "za", "med", "cez", "skozi", "o")

    /** A word of a pattern id as a facet: a known one ("gen" → genitive), a preposition, else null (not judged). */
    private fun facet(w: String): String? = TO_FACET[w] ?: w.takeIf { it in PREPOSITIONS && (it.length > 1 || it in setOf("v", "k", "z", "s")) }

    /** The facets of a pattern id ("genitive_after_iz" → genitive, iz); "a_to_o" says nothing. */
    fun patternFacets(id: String): Set<String> =
        id.lowercase().split('_', '-', '.').filter { it.isNotEmpty() && it !in STOP }.mapNotNull(::facet).toSet()

    /** What a task is about, as facets: its case, number, gender, the rule's words, kje or kam, agreement. */
    fun taskFacets(t: CaseTask, rule: CaseRuleLine?, ruleKey: String): Set<String> = buildSet {
        add(t.case.en)
        add(when (t.number) { "pl" -> "plural"; "du" -> "dual"; else -> "singular" })
        when (t.gender) {
            "m" -> add("masculine")
            "f" -> add("feminine")
            "n" -> add("neuter")
        }
        if (t.adjective != null) add("adjective")
        if (t.adjective != null && t.adjective in POSSESSIVES) add("possessive")
        for (w in rule?.words.orEmpty()) facet(Grading.fold(w, Lang.SL))?.let(::add)
        for (w in ruleKey.split('-')) facet(w)?.let(::add)
        when (ruleKey) {
            "nikalnica" -> add("negation")
            "kolicina" -> add("quantity")
            "stevilo" -> add("number")
            "cigav" -> add("possession")
            "subject" -> add("nominative")
            "object" -> add("accusative")
        }
        if (ruleKey.endsWith("kam") || ruleKey.endsWith("kje") || ruleKey in setOf("pri", "k", "ob")) {
            addAll(listOf("kam", "kje", "place"))
        }
    }

    private val POSSESSIVES = setOf("moj", "tvoj", "njegov", "njen", "naš", "vaš", "svoj", "njun", "njihov")

    /**
     * The learner's own mistake pattern a wrong answer on [t] is: one whose facets are all [t]'s (unknown words aside),
     * the most specific first, then the most frequent; null when none fits. "genitive_after_iz" fits a genitive after iz,
     * "accusative_feminine_a_to_o" a feminine accusative, "kam_vs_kje" any kje-or-kam task, "v_vs_na" one about v or na.
     */
    fun patternFor(t: CaseTask, facets: Set<String>, mistakes: List<MistakeNote>): MistakeNote? =
        mistakes.mapNotNull { m -> patternFacets(m.id).takeIf { it.isNotEmpty() && facets.containsAll(it) }?.let { m to it.size } }
            .sortedWith(compareByDescending<Pair<MistakeNote, Int>> { it.second }.thenByDescending { it.first.frequency })
            .firstOrNull()?.first

    /** How much the learner's mistakes weigh on [t], 0 … 1: the frequency of those that fit it (six count fully). */
    fun prior(t: CaseTask, facets: Set<String>, mistakes: List<MistakeNote>): Double =
        mistakes.filter { m -> patternFacets(m.id).let { it.isNotEmpty() && facets.containsAll(it) } }.sumOf { it.frequency }.coerceAtMost(6) / 6.0

    // --- a session -------------------------------------------------------------------------------------------------------

    /** A bank item's task. */
    fun taskOf(pool: CasePool, it: CaseItem, again: Boolean = false): CaseTask? {
        val kase = Kase.of(it.case) ?: return null
        val rule = pool.bank.page(it.why) ?: return null
        val kind = if (it.kind == "place") CaseKind.PLACE else CaseKind.WHICH
        val gender = it.gender ?: pool.nouns.firstOrNull { n -> n.lemma == it.lemma }?.gender
        return CaseTask(kind, it.id, kase, it.number, rule, it.level, again, adjective = if (it.pos == "adj") it.lemma else null, gender = gender)
    }

    /** A decline task's key: "d:<frame>:<noun>" or "d:<frame>:<noun>:<adjective>". */
    fun declineKey(f: CaseFrame, n: CaseNoun, adj: CaseAdjective?) = "d:${f.id}:${n.lemma}" + (adj?.let { ":${it.lemma}" } ?: "")

    private fun declineTask(pool: CasePool, f: CaseFrame, n: CaseNoun, adj: CaseAdjective?, again: Boolean = false): CaseTask? {
        val kase = Kase.of(f.case) ?: return null
        val rule = pool.bank.page(f.why) ?: return null
        return CaseTask(CaseKind.DECLINE, declineKey(f, n, adj), kase, f.number, rule, maxOf(f.level, adj?.level ?: "A1"), again, adj?.lemma, n.gender)
    }

    /** The task of a key kept to come back ([CaseAgain.item]); null when it can't be asked now (its word gone, its rule closed). */
    fun taskOfKey(pool: CasePool, key: String): CaseTask? {
        if (key.startsWith("d:")) {
            val parts = key.split(':')
            val f = pool.bank.frames.firstOrNull { it.id == parts.getOrNull(1) }?.takeIf { allowed(pool, it) } ?: return null
            val n = nouns(pool).firstOrNull { it.lemma == parts.getOrNull(2) }?.takeIf { nounFits(f, it) } ?: return null
            val adj = parts.getOrNull(3)?.let { a -> adjectives(pool).firstOrNull { it.lemma == a } ?: return null }
            return declineTask(pool, f, n, adj, again = true)
        }
        val it = pool.bank.items.firstOrNull { i -> i.id == key }?.takeIf { allowed(pool, it) } ?: return null
        return taskOf(pool, it, again = true)
    }

    /** The items missed due on [today] or before, the oldest first. */
    fun due(p: CaseProgress?, today: LocalDate): List<CaseAgain> =
        p?.again.orEmpty().filter { a -> runCatching { LocalDate.parse(a.due) }.getOrNull()?.let { !it.isAfter(today) } ?: true }.sortedBy { it.due }

    /** A focus on kje or kam: the school's unit kje-kam-od-kod (v, na + the locative or the accusative; pri, k; od kod). */
    const val KJE_KAM = "kje-kam"
    /** The pages whose focus is a number: the plural's and the dual's through the cases. */
    const val PLURAL_CASES = "mnozina-skloni"

    /**
     * Whether [focus] is about [t]: its case (a case id), its rule (a rule id: the grammar page), and the focuses of more
     * than one: kje or kam ([KJE_KAM]: the place rules and every "Kje ali kam?"), agreement ([AGREEMENT]: a task with an
     * adjective or a possessive), the plural's and the dual's cases ([PLURAL_CASES], [DUAL_CASES]: by the number).
     */
    fun focused(t: CaseTask, focus: String?): Boolean = when (focus) {
        null -> true
        KJE_KAM -> t.kind == CaseKind.PLACE || t.rule == "kam-tozilnik" || t.rule == "kje-mestnik-orodnik"
        AGREEMENT -> t.adjective != null
        PLURAL_CASES -> t.number == "pl"
        DUAL_CASES -> t.number == "du"
        else -> t.case.id == focus || t.rule == focus
    }

    /** The trainer's focus of a grammar page ("🧩 Vadi sklone" on it): a case's, a rule's, a group's; null for a mixed session. */
    fun focusOfPage(page: String, rules: Collection<String>): String? = when {
        Kase.of(page) != null -> page
        page in rules -> page
        page in setOf(KJE_KAM, AGREEMENT, PLURAL_CASES, DUAL_CASES) -> page
        else -> null
    }

    /** The trainer's focus of a school unit about the cases ([unit]: its id); null for one that isn't, or for every case at once (skloni). */
    fun focusOfUnit(unit: String): String? = when (unit) {
        "tozilnik" -> "tozilnik"
        "mestnik" -> "mestnik"
        "nikalnica-rodilnik" -> "rodilnik-nikalnica"
        "rodilnik" -> "rodilnik"
        "kje-kam-od-kod" -> KJE_KAM
        "dajalnik" -> "dajalnik"
        "orodnik" -> "orodnik"
        "pridevniki-skloni" -> AGREEMENT
        "mnozina-skloni" -> PLURAL_CASES
        "dvojina-skloni" -> DUAL_CASES
        else -> null
    }

    /** The school's units about the cases: their pages show the trainer. */
    val CASE_UNITS = setOf("tozilnik", "mestnik", "nikalnica-rodilnik", "skloni", "rodilnik", "kje-kam-od-kod", "dajalnik", "orodnik", "pridevniki-skloni", "mnozina-skloni", "dvojina-skloni", "sklanjatev-posebnosti")

    /**
     * A session's tasks, [size] at most: the items missed that are due first (at most [MAX_AGAIN], spread through it), then
     * new ones by kind ([SHARES]; a kind without anything to ask gives its share to the others; Kje ali kam? more for a
     * focus on kje or kam, recognition more while the learner's accuracy is low, declining more while it is high), each
     * drawn by its weight ([weight]): the weaker its case and rule (the learner's mistakes counting from the start), the
     * more; asked lately, less; its level as the learner's accuracy wants it (60–70 % right); a rule the lens showed
     * lately, a little more. No item twice, no word twice where another can be asked; the kinds interleaved, never the same
     * twice in a row where another is planned. [focus]: a case id or a rule id (the grammar page's "Vadi", the tutor's
     * assignment), the rest of the session filled with other tasks only when it has too few. [only]: QA's, one kind.
     */
    fun plan(
        pool: CasePool, p: CaseProgress?, mistakes: List<MistakeNote>, priors: (String) -> Double,
        today: LocalDate, random: Random, focus: String? = null, size: Int = if (pool.child) RUN_CHILD else RUN, only: CaseKind? = null,
    ): List<CasePlanned> {
        val used = mutableSetOf<String>()
        val lemmas = mutableSetOf<String>()
        val sentences = mutableSetOf<String>()
        val again = due(p, today).mapNotNull { a -> taskOfKey(pool, a.item) }
            .filter { focused(it, focus) && (only == null || it.kind == only) }
            .filter { used.add(it.key) }
            .take(MAX_AGAIN.coerceAtMost(size))

        // every candidate now, by kind
        val items = pool.bank.items.filter { allowed(pool, it) }.mapNotNull { taskOf(pool, it) }
        val nouns = nouns(pool)
        val adjs = adjectives(pool)
        val possessiveSlips = mistakes.any { "possessive" in patternFacets(it.id) }
        val frames = pool.bank.frames.filter { allowed(pool, it) }
        val declines = buildList {
            for (f in frames) {
                val fit = nouns.filter { nounFits(f, it) }.shuffled(random).take(6)
                for (n in fit) {
                    declineTask(pool, f, n, null)?.let(::add)
                    if (f.adj && adjs.isNotEmpty() && f.number != "du") {
                        val wantPossessive = random.nextDouble() < if (possessiveSlips) 0.7 else 0.4
                        val pick = adjs.filter { it.possessive == wantPossessive }.ifEmpty { adjs }
                        declineTask(pool, f, n, pick[random.nextInt(pick.size)])?.let(::add)
                    }
                }
            }
        }
        val all = (items + declines).filter { only == null || it.kind == only }
        val inFocus = all.filter { focused(it, focus) }
        // a focus with too few to ask: its case's too, then the rest
        val candidates = when {
            focus == null -> all
            inFocus.size >= size -> inFocus
            else -> {
                val caseOf = (inFocus.firstOrNull()?.case ?: Kase.of(focus) ?: all.firstOrNull { it.rule == focus }?.case)
                val sameCase = all.filter { it.case == caseOf && it !in inFocus }
                inFocus + sameCase + if (inFocus.size + sameCase.size >= size) emptyList() else all.filter { it !in inFocus && it !in sameCase }
            }
        }.filter { it.key !in used }
        if (candidates.isEmpty() && again.isEmpty()) return emptyList()

        val acc = accuracy(p)
        val shares = SHARES.toMutableMap()
        if (focus == "kam-tozilnik" || focus == "kje-mestnik-orodnik" || focus == KJE_KAM) shares[CaseKind.PLACE] = 0.45
        when {
            acc != null && acc < 0.55 -> { shares[CaseKind.WHICH] = shares[CaseKind.WHICH]!! + 0.1; shares[CaseKind.DECLINE] = (shares[CaseKind.DECLINE]!! - 0.1).coerceAtLeast(0.1) }
            acc != null && acc > 0.80 -> { shares[CaseKind.DECLINE] = shares[CaseKind.DECLINE]!! + 0.1; shares[CaseKind.WHICH] = (shares[CaseKind.WHICH]!! - 0.1).coerceAtLeast(0.2) }
        }
        val byKind = candidates.groupBy { it.kind }
        val kinds = CaseKind.entries.filter { !byKind[it].isNullOrEmpty() }
        val fresh = size - again.size
        val quota = quotas(kinds.associateWith { shares[it] ?: 0.0 }, fresh, byKind.mapValues { it.value.size })

        val recent = p?.recent.orEmpty().toSet()
        val seenLately = p?.seen.orEmpty().filterValues { s -> runCatching { LocalDate.parse(s.day) }.getOrNull()?.let { !it.isBefore(today.minusDays(SEEN_DAYS)) } == true }.keys
        val factsOf = HashMap<String, Set<String>>()
        fun facts(t: CaseTask) = factsOf.getOrPut(t.key) { taskFacets(t, pool.bank.rules[ruleKeyOf(pool, t)], ruleKeyOf(pool, t)) }
        val picked = mutableListOf<CaseTask>()
        for (k in kinds) {
            val pool0 = byKind[k].orEmpty().toMutableList()
            repeat(quota[k] ?: 0) {
                val left = pool0.filter { it.key !in used }
                if (left.isEmpty()) return@repeat
                val w = left.map { t ->
                    val base = weight(t, p, priors, prior(t, facts(t), mistakes), acc, pool.level, t.key in recent, t.rule in seenLately)
                    // a word asked already in this session: much less; its sentence again (another word of it): hardly
                    base * (if (lemmaOf(pool, t) in lemmas) 0.15 else 1.0) * (if (sentenceOf(pool, t)?.let { it in sentences } == true) 0.05 else 1.0)
                }
                val t = drawWeighted(left, w, random)
                used.add(t.key)
                lemmaOf(pool, t)?.let(lemmas::add)
                sentenceOf(pool, t)?.let(sentences::add)
                picked += t
            }
        }
        val ordered = interleave(picked, random)
        val tasks = spread(again, ordered)
        return tasks.mapIndexedNotNull { i, t -> exercise(pool, t, random.nextLong() xor i.toLong())?.let { CasePlanned(t, it) } }
    }

    /** The rule key ([CaseBank.rules]) of a task: a bank item's `why`, a frame's. */
    fun ruleKeyOf(pool: CasePool, t: CaseTask): String = when (t.kind) {
        CaseKind.DECLINE -> pool.bank.frames.firstOrNull { it.id == t.key.split(':').getOrNull(1) }?.why.orEmpty()
        else -> pool.bank.items.firstOrNull { it.id == t.key }?.why.orEmpty()
    }

    private fun sentenceOf(pool: CasePool, t: CaseTask): String? =
        if (t.kind == CaseKind.DECLINE) null else pool.bank.items.firstOrNull { it.id == t.key }?.sl?.lowercase()

    private fun lemmaOf(pool: CasePool, t: CaseTask): String? = when (t.kind) {
        CaseKind.DECLINE -> t.key.split(':').getOrNull(2)
        else -> pool.bank.items.firstOrNull { it.id == t.key }?.lemma
    }?.lowercase()

    /**
     * [t]'s weight: its case's and its rule's weakness ([weakness], the mistakes that fit it as their prior), its
     * agreement's for a decline with an adjective, the learner's own mistakes fitting it exactly ([own]); a quarter
     * when asked lately; by its level for the learner's accuracy (harder above 80 %, easier under 55 %, the learner's
     * level otherwise); a third more for a rule the lens showed lately.
     */
    fun weight(t: CaseTask, p: CaseProgress?, priors: (String) -> Double, own: Double, acc: Double?, level: String, recent: Boolean, seen: Boolean): Double {
        var w = 0.35 + weakness(p, caseKey(t.case), priors(t.case.id)) + weakness(p, ruleKey(t.rule), priors(t.rule)) + 0.8 * own
        if (t.adjective != null) w += 0.6 * weakness(p, ruleKey(AGREEMENT), priors(AGREEMENT))
        if (recent) w *= 0.25
        if (seen) w *= 1.3
        val diff = rank(t.level) - rank(level)
        w *= when {
            acc != null && acc > 0.80 -> if (diff >= 0) 1.5 else 0.7
            acc != null && acc < 0.55 -> if (diff < 0) 1.5 else if (diff == 0) 0.8 else 0.4
            else -> if (diff > 0) 0.6 else 1.0
        }
        return w
    }

    /** The tasks per kind: [shares] of [n], rounded so they add up, none more than a kind has. */
    fun quotas(shares: Map<CaseKind, Double>, n: Int, have: Map<CaseKind, Int>): Map<CaseKind, Int> {
        if (shares.isEmpty() || n <= 0) return emptyMap()
        val total = shares.values.sum().takeIf { it > 0 } ?: return emptyMap()
        val out = shares.mapValues { (k, s) -> minOf((s / total * n).toInt(), have[k] ?: 0) }.toMutableMap()
        // the rest one at a time to the kind furthest below its share that has more to ask
        while (out.values.sum() < n) {
            val k = shares.keys.filter { (out[it] ?: 0) < (have[it] ?: 0) }.maxByOrNull { (shares[it]!! / total * n) - (out[it] ?: 0) } ?: break
            out[k] = (out[k] ?: 0) + 1
        }
        return out
    }

    private fun <T> drawWeighted(list: List<T>, weights: List<Double>, random: Random): T {
        val total = weights.sum()
        if (total <= 0) return list[random.nextInt(list.size)]
        var x = random.nextDouble() * total
        for (i in list.indices) {
            x -= weights[i]
            if (x <= 0) return list[i]
        }
        return list.last()
    }

    /** [tasks] shuffled so that no kind comes twice in a row where another can come between, nor a case three times. */
    fun interleave(tasks: List<CaseTask>, random: Random): List<CaseTask> {
        val left = tasks.shuffled(random).toMutableList()
        val out = mutableListOf<CaseTask>()
        while (left.isNotEmpty()) {
            val last = out.lastOrNull()
            val twoCases = out.size >= 2 && out[out.size - 1].case == out[out.size - 2].case
            // the kind with the most left first, so the last ones don't bunch up
            val counts = left.groupingBy { it.kind }.eachCount()
            val ok = left.filter { it.kind != last?.kind && !(twoCases && it.case == last?.case) }
            val pick = ok.maxByOrNull { counts[it.kind] ?: 0 } ?: left.firstOrNull { !(twoCases && it.case == last?.case) } ?: left.first()
            left.remove(pick)
            out += pick
        }
        return out
    }

    /** [again] spread evenly through [fresh]: the first at the start, the others between. */
    private fun spread(again: List<CaseTask>, fresh: List<CaseTask>): List<CaseTask> {
        if (again.isEmpty()) return fresh
        val total = again.size + fresh.size
        val at = again.indices.map { i -> i * total / again.size }.toSet()
        val a = again.iterator()
        val f = fresh.iterator()
        return (0 until total).map { i -> if (i in at && a.hasNext()) a.next() else if (f.hasNext()) f.next() else a.next() }
    }

    // --- "⚡ Sklonski vihar · Case storm" ---------------------------------------------------------------------------------

    /** The storm's clock, and how many phrases it has ready (more than anyone sorts in its time). */
    const val STORM_SECONDS = 60
    const val STORM_SIZE = 30
    /** A storm's medals: gold from this many right at this share right, silver from this many. */
    const val GOLD_RIGHT = 14
    const val GOLD_SHARE = 0.9
    const val SILVER_RIGHT = 8

    /**
     * A bank item as a storm's phrase: the deciding word and the word asked about with what stands between them ("brez
     * mleka", "k moji babici"), when they are at most two words apart; else a short sentence whole (a subject, an object:
     * "Luka pase ovce."); null for a longer one.
     */
    fun phrase(it: CaseItem): Pair<List<String>, Int>? {
        val toks = it.tokens
        val t = it.trigger
        if (it.kind == "which" && t != null && kotlin.math.abs(t - it.word) <= 2) {
            val from = minOf(t, it.word)
            // an adjective asked about keeps its noun after it ("v veliki hiši")
            val to = maxOf(t, it.word).let { e -> if (it.pos == "adj" && e == it.word && e + 1 < toks.size && e + 1 - from <= 3) e + 1 else e }
            val words = toks.subList(from, to + 1).map(CaseBanks::bare).filter { w -> w.isNotEmpty() }
            if (words.size == to - from + 1) return words.mapIndexed { i, w -> if (i == 0) w.replaceFirstChar { c -> c.lowercase() } else w }.let { ws -> ws to (it.word - from) }
        }
        return if (it.kind == "which" && toks.size <= 6) toks to it.word else null
    }

    /**
     * A storm: [STORM_SIZE] phrases to sort into the six cases against the clock, nothing more asked of each (no "Zakaj?");
     * drawn as a session's are ([weight]: the weak cases more), never the same case three times in a row where another
     * can come, and the same phrase never twice.
     */
    fun storm(pool: CasePool, p: CaseProgress?, mistakes: List<MistakeNote>, priors: (String) -> Double, today: LocalDate, random: Random, size: Int = STORM_SIZE): List<CasePlanned> {
        val cands = pool.bank.items.filter { allowed(pool, it) && it.kind == "which" }.mapNotNull { it0 -> phrase(it0)?.let { ph -> it0 to ph } }
            .distinctBy { (_, ph) -> ph.first.joinToString(" ").lowercase() }
        if (cands.isEmpty()) return emptyList()
        val acc = accuracy(p)
        val recent = p?.recent.orEmpty().toSet()
        val left = cands.toMutableList()
        val out = mutableListOf<CasePlanned>()
        while (out.size < size && left.isNotEmpty()) {
            val lastTwo = out.takeLast(2).map { it.task.case }
            val ok = left.filter { (it0, _) -> !(lastTwo.size == 2 && lastTwo.all { c -> c.id == it0.case }) }.ifEmpty { left }
            val w = ok.map { (it0, _) ->
                val t = taskOf(pool, it0) ?: return@map 0.0
                weight(t, p, priors, 0.0, acc, pool.level, t.key in recent, false)
            }
            val pick = drawWeighted(ok, w, random)
            left.remove(pick)
            val (it0, ph) = pick
            val t = taskOf(pool, it0) ?: continue
            val ex = item(pool, it0) ?: continue
            out += CasePlanned(t, ex.copy(tokens = ph.first, word = ph.second, taps = emptyList(), role = null, askWhy = false, translation = null, note = null))
        }
        return out
    }

    /** A storm's medal: 🥇 gold, 🥈 silver, 🥉 bronze, by how many were right and the share right. */
    enum class Medal(val emoji: String, val key: String) { GOLD("🥇", "cases.storm.gold"), SILVER("🥈", "cases.storm.silver"), BRONZE("🥉", "cases.storm.bronze") }

    fun medal(verdicts: List<Verdict>): Medal {
        val right = verdicts.count { it == Verdict.CORRECT }
        val share = if (verdicts.isEmpty()) 0.0 else right.toDouble() / verdicts.size
        return when {
            right >= GOLD_RIGHT && share >= GOLD_SHARE -> Medal.GOLD
            right >= SILVER_RIGHT -> Medal.SILVER
            else -> Medal.BRONZE
        }
    }

    /** The longest run of right answers in a row. */
    fun streak(verdicts: List<Verdict>): Int {
        var best = 0
        var now = 0
        for (v in verdicts) {
            now = if (v == Verdict.CORRECT) now + 1 else 0
            best = maxOf(best, now)
        }
        return best
    }

    // --- the exercises -----------------------------------------------------------------------------------------------

    /** [t] as the run's exercise; null when what it names is gone. */
    fun exercise(pool: CasePool, t: CaseTask, seed: Long = 0L): Exercise.CaseTask? = when (t.kind) {
        CaseKind.DECLINE -> {
            val parts = t.key.split(':')
            val f = pool.bank.frames.firstOrNull { it.id == parts.getOrNull(1) }
            val n = nouns(pool).firstOrNull { it.lemma == parts.getOrNull(2) }
            val adj = parts.getOrNull(3)?.let { a -> pool.bank.adjectives.firstOrNull { it.lemma == a } }
            if (f == null || n == null) null else decline(pool, f, n, adj)
        }
        else -> pool.bank.items.firstOrNull { it.id == t.key }?.let { item(pool, it, seed) }
    }

    /** A bank item as its exercise: "Kateri sklon?" (the sentence, the word asked about) or "Kje ali kam?" (the gap, its options). */
    fun item(pool: CasePool, it: CaseItem, seed: Long = 0L): Exercise.CaseTask? {
        val rule = pool.bank.rule(it.why) ?: return null
        val toks = it.tokens
        val base = pool.base
        val note = it.note?.inBase(base)?.takeIf { s -> s.isNotBlank() }
        val spoken = it.spoken?.let { s -> "🗣️ ${s.form} = ${s.standard} · ${s.note.inBase(base)}" }
        val common = Exercise.CaseTask(
            kind = it.kind, key = it.id, sentence = it.sl, tokens = toks, word = it.word, case = it.case, number = it.number,
            rule = rule.page, why = it.why, line = rule.inBase(base), ruleSl = rule.sl,
            taps = listOfNotNull(it.trigger) + it.also, role = it.role, askWhy = it.kind == "which" || it.trigger != null,
            translation = it.meaning(base), note = listOfNotNull(note, spoken).joinToString("\n").ifBlank { null },
            ambiguous = it.ambiguous, lemma = it.lemma, level = it.level, gender = it.gender, grammar = rule.page,
        )
        if (it.kind != "place") return common
        // the gap, and the noun in brackets after it when the gap holds it ("Micka gre ___ (tržnica).")
        val (from, to) = it.gap[0] to it.gap[1]
        val last = toks.getOrElse(to) { "" }
        val trailing = last.substring(CaseBanks.bare(last).let { b -> last.indexOf(b).takeIf { i -> i >= 0 }?.plus(b.length) ?: last.length })
        val cue = it.cue?.let { c -> " ($c)" }.orEmpty()
        val gapped = toks.take(from) + listOf("___$cue$trailing") + toks.drop(to + 1)
        val order = it.options.indices.shuffled(Random(seed))
        return common.copy(
            prompt = gapped.joinToString(" "),
            options = order.map { i -> it.options[i] },
            answer = order.indexOf(it.answer),
            taps = listOfNotNull(it.trigger),
            scene = it.scene?.takeIf { s -> s in pool.scenes },
        )
    }

    /** The adjective's form agreeing with a noun of [gender] (its animacy for the masculine accusative) in [case] and [number]. */
    fun adjectiveForm(adj: CaseAdjective, case: Kase, gender: String, number: String, animate: Boolean): String? {
        val n = if (number == "du") return null else number
        val key = "${case.slot}.$gender.$n"
        return if (case == Kase.TOZILNIK && gender == "m" && n == "sg" && animate) adj.forms["acc.m.sg.anim"] else adj.forms[key]
    }

    /** The standard endings a slot's form has by gender, to show the usual one first where the dictionary has two. */
    private val ENDS: Map<String, Map<String, List<String>>> = mapOf(
        "m" to mapOf("dat.sg" to listOf("u"), "loc.sg" to listOf("u"), "nom.pl" to listOf("je", "i"), "dat.pl" to listOf("om", "em"), "loc.pl" to listOf("ih"), "ins.pl" to listOf("i"), "gen.pl" to listOf("ov", "ev"), "dat.du" to listOf("oma", "ema"), "loc.du" to listOf("ih"), "ins.du" to listOf("oma", "ema"), "gen.du" to listOf("ov", "ev")),
        "n" to mapOf("dat.sg" to listOf("u"), "loc.sg" to listOf("u"), "dat.pl" to listOf("om", "em"), "loc.pl" to listOf("ih"), "ins.pl" to listOf("i"), "dat.du" to listOf("oma", "ema"), "loc.du" to listOf("ih"), "ins.du" to listOf("oma", "ema")),
        "f" to mapOf("dat.pl" to listOf("am"), "loc.pl" to listOf("ah"), "ins.pl" to listOf("ami"), "dat.du" to listOf("ama"), "loc.du" to listOf("ah"), "ins.du" to listOf("ama")),
    )

    /**
     * The forms of [n]'s [slot] in the order shown: the usual ending first where the dictionary has two (bratu before
     * brati), never the noun's own form first in an oblique case (sester before sestra); all of them are right.
     */
    fun shownForms(n: CaseNoun, slot: String): List<String> {
        val all = n.forms[slot].orEmpty().distinct()
        val ends = ENDS[n.gender]?.get(slot)
        val oblique = !slot.startsWith("nom.") && !slot.startsWith("acc.")
        return all.sortedWith(compareBy<String>({ f -> if (ends != null && ends.none { f.endsWith(it) }) 1 else 0 }, { f -> if (oblique && f == n.lemma) 1 else 0 }))
    }

    /**
     * Frame [f] filled with noun [n] (and [adj], agreeing): "Grem k ___. (moj brat)"; what is right (every form the
     * dictionary gives, with the adjective's), the spoken forms accepted too and labelled, the answer shown, its sentence,
     * the frame's meaning with the words' ("I'm going to … · moj = my · brat = brother").
     */
    fun decline(pool: CasePool, f: CaseFrame, n: CaseNoun, adj: CaseAdjective?): Exercise.CaseTask? {
        val kase = Kase.of(f.case) ?: return null
        val rule = pool.bank.rule(f.why) ?: return null
        val slot = slotOf(kase, f.number)
        val forms = shownForms(n, slot).ifEmpty { return null }
        val spoken = n.spoken[slot].orEmpty().filter { it !in forms }
        val a = adj?.let { adjectiveForm(it, kase, n.gender, f.number, n.animate) ?: return null }
        fun phrase(form: String) = listOfNotNull(a, form).joinToString(" ")
        val shown = phrase(forms.first())
        val two = if (n.gender == "m") "dva" else "dve"
        fun fill(gap: String) = f.sl.replace("{k}", WordForms.kh(shown)).replace("{s}", WordForms.sz(shown)).replace("{two}", two).replace("{x}", gap)
        // the cue: the phrase in its nominative ("moj brat", "lepa hiša"), a plural marked as WordForms marks it
        val cueAdj = adj?.let { adjectiveForm(it, Kase.IMENOVALNIK, n.gender, "sg", false) }
        val cue = listOfNotNull(cueAdj, n.lemma).joinToString(" ") + if (f.number == "pl") ", mn." else ""
        val sentence = fill(shown)
        val words = listOfNotNull(adj?.let { "${it.lemma} = ${it.meaning(pool.base)}" }, "${n.lemma} = ${n.meaning}")
        return Exercise.CaseTask(
            kind = CaseKind.DECLINE.wire, key = declineKey(f, n, adj), sentence = sentence, tokens = CaseBanks.tokens(fill("___")),
            case = f.case, number = f.number, rule = rule.page, why = f.why, line = rule.inBase(pool.base), ruleSl = rule.sl,
            askWhy = false, prompt = "${fill("___")} ($cue)", accept = forms.map(::phrase), spoken = spoken.map(::phrase), shown = shown,
            hint = (listOf(f.meaning(pool.base)) + words).joinToString(" · "), lemma = n.lemma, level = f.level, gender = n.gender,
            adj = adj?.lemma, possessive = adj?.possessive == true, grammar = rule.page,
        )
    }

    // --- grading -------------------------------------------------------------------------------------------------------

    /** The case picked: right when it is the item's. */
    fun pickRight(ex: Exercise.CaseTask, picked: Kase): Boolean = picked.id == ex.case

    /**
     * The "Zakaj?" answer: the token tapped ([tapped]) is the trigger or another word that decides it ([Exercise.CaseTask.taps]),
     * or the role said ([role]: "subject", "object") is the item's when nothing decides it (an object's verb tapped is right too).
     */
    fun whyRight(ex: Exercise.CaseTask, tapped: Int?, role: String?): Boolean = when {
        role != null -> role == ex.role
        tapped != null -> tapped in ex.taps
        else -> false
    }

    /** The task's verdict: the case (or the option) wrong is wrong; right with the deciding word wrong almost; both right right. */
    fun verdict(caseRight: Boolean, whyRight: Boolean?): Verdict = when {
        !caseRight -> Verdict.WRONG
        whyRight == false -> Verdict.ALMOST
        else -> Verdict.CORRECT
    }

    /** A typed decline answer graded as any typed answer (č, š, ž forgotten is almost; a wrong ending wrong); a spoken form is right. */
    data class Typed(val verdict: Verdict, val expected: String, val hint: Grading.Hint, val marks: List<IntRange>, val spoken: Boolean = false)

    /**
     * [answer] to decline task [ex]: the phrase alone, or with the frame's preposition before it ("k babici" for "k ___"), or
     * the whole sentence; graded by [Grading.check] against what is right, a spoken form right too (labelled).
     */
    fun gradeDecline(ex: Exercise.CaseTask, answer: String): Typed {
        val a = phraseOf(ex, answer)
        if (ex.spoken.any { Grading.canonical(it, Lang.SL) == Grading.canonical(a, Lang.SL) }) return Typed(Verdict.CORRECT, ex.shown, Grading.Hint.NONE, emptyList(), spoken = true)
        val r = Grading.check(a, ex.accept, Lang.SL)
        return Typed(r.verdict, r.expected.ifBlank { ex.shown }, r.hint, r.marks)
    }

    /** What of [answer] fills the gap: without the words before and after the gap the frame has, when typed with them. */
    fun phraseOf(ex: Exercise.CaseTask, answer: String): String {
        val gap = ex.tokens.indexOfFirst { "___" in it }
        if (gap < 0) return answer.trim()
        val before = ex.tokens.take(gap).map { Grading.normalize(CaseBanks.bare(it), Lang.SL) }.filter { it.isNotEmpty() }
        val after = ex.tokens.drop(gap + 1).map { Grading.normalize(CaseBanks.bare(it), Lang.SL) }.filter { it.isNotEmpty() }
        var words = Grading.normalize(answer, Lang.SL).split(' ').filter { it.isNotEmpty() }
        // the whole sentence typed, or the preposition before the gap
        for (n in before.size downTo 1) if (words.size > n && words.take(n) == before.takeLast(n)) { words = words.drop(n); break }
        for (n in after.size downTo 1) if (words.size > n && words.takeLast(n) == after.take(n)) { words = words.dropLast(n); break }
        return words.joinToString(" ")
    }

    // --- after a session -----------------------------------------------------------------------------------------------

    /**
     * The session's answers kept: each one on its case's, its rule's and its kind's last answers (an almost counts right: the
     * case was right), the agreement's for a decline with an adjective, the deciding word's taps on its rule; each item
     * missed to come back (wrong: the next session, today; almost: tomorrow), each one coming back moved on a box when right
     * (1, 3, 7, 16 days, then gone) or back to the first when wrong; the items asked lately. [answers] may be fewer than
     * [tasks] (a session left early).
     */
    fun record(p: CaseProgress?, tasks: List<CaseTask>, answers: List<CaseAnswer>, today: LocalDate): CasesRecorded {
        val start = p ?: CaseProgress()
        val keys = start.keys.toMutableMap()
        val why = start.why.toMutableMap()
        val again = start.again.toMutableList()
        val recent = start.recent.toMutableList()
        val day = today.toString()
        fun push(map: MutableMap<String, String>, k: String, right: Boolean) {
            map[k] = (map[k].orEmpty() + if (right) "1" else "0").takeLast(WINDOW)
        }
        val missed = mutableListOf<String>()
        for ((t, a) in tasks.zip(answers)) {
            val right = a.verdict != Verdict.WRONG
            push(keys, caseKey(t.case), right)
            push(keys, ruleKey(t.rule), right)
            push(keys, kindKey(t.kind), right)
            if (t.adjective != null) push(keys, ruleKey(AGREEMENT), right)
            a.whyRight?.let { push(why, t.rule, it) }
            val i = again.indexOfFirst { it.item == t.key }
            val was = again.getOrNull(i)
            val next = when (a.verdict) {
                Verdict.WRONG -> CaseAgain(t.key, day, 0)
                Verdict.ALMOST -> CaseAgain(t.key, today.plusDays(1).toString(), maxOf(was?.box ?: 1, 1))
                Verdict.CORRECT -> was?.let { w ->
                    val box = w.box + 1
                    if (box >= INTERVALS.size) null else w.copy(box = box, due = today.plusDays(INTERVALS[box].toLong()).toString())
                }
            }
            if (i >= 0) again.removeAt(i)
            if (next != null) again += next
            if (a.verdict == Verdict.WRONG) missed += t.key
            recent.remove(t.key)
            recent += t.key
        }
        val kept = again.sortedWith(compareBy<CaseAgain> { it.box }.thenByDescending { it.due }).take(MAX_KEPT)
        val out = start.copy(keys = keys, why = why, again = kept, recent = recent.takeLast(RECENT), runs = start.runs + if (answers.isEmpty()) 0 else 1, day = if (answers.isEmpty()) start.day else day)
        return CasesRecorded(out, missed)
    }

    /** The case lens showed the learner rule [rule] (a grammar page): counted as seen, lightly; never an answer. */
    fun seen(p: CaseProgress?, rule: String, today: LocalDate): CaseProgress {
        val start = p ?: CaseProgress()
        val s = start.seen[rule]
        return start.copy(seen = start.seen + (rule to CaseSeen((s?.n ?: 0) + 1, today.toString())))
    }

    // --- for the learner databases and the tutor ------------------------------------------------------------------------

    /** The trainer's own pattern id of a wrong answer no mistake of the learner's fits: cases_genitive_brez, cases_form_dative_plural_adjective. */
    fun ownPattern(t: CaseTask, ruleKey: String): String = when (t.kind) {
        CaseKind.DECLINE -> listOfNotNull("cases_form", t.case.en, t.number.takeIf { it != "sg" }?.let { if (it == "pl") "plural" else "dual" }, t.adjective?.let { if (it in POSSESSIVES) "possessive" else "adjective" }).joinToString("_")
        else -> "cases_${t.case.en}_${ruleKey.replace('-', '_')}"
    }

    /**
     * Each wrong answer as an error of update-db.py's report: the learner's own pattern where one fits ([patternFor]),
     * else the trainer's ([ownPattern]); an almost (the case right, the deciding word not) as a minor one of its own
     * (cases_trigger_<rule>): the trigger is what the learner lacks.
     */
    fun errors(pool: CasePool, tasks: List<CaseTask>, exercises: List<Exercise.CaseTask>, answers: List<CaseAnswer>, mistakes: List<MistakeNote>): JsonArray = buildJsonArray {
        for (i in answers.indices) {
            val a = answers[i]
            val t = tasks.getOrNull(i) ?: continue
            val ex = exercises.getOrNull(i) ?: continue
            if (a.verdict == Verdict.CORRECT) continue
            val rk = ruleKeyOf(pool, t)
            val rule = pool.bank.rule(rk)
            val wrongCase = a.verdict == Verdict.WRONG
            if (!wrongCase && a.whyRight != false) continue // an almost of a typed answer (č, š, ž): no mistake
            val pattern = if (wrongCase) (patternFor(t, taskFacets(t, rule, rk), mistakes)?.id ?: ownPattern(t, rk)) else "cases_trigger_${rk.replace('-', '_')}"
            add(buildJsonObject {
                put("pattern_id", pattern)
                put("category", "grammar")
                put("subcategory", "cases")
                put("description", if (wrongCase) "${t.case.en.replaceFirstChar { it.uppercase() }} (${t.case.sl}${if (t.number == "sg") "" else ", ${t.number}"}): ${rule?.en ?: t.rule}" else "The word that decides the case: ${rule?.en ?: t.rule}")
                put("severity", if (wrongCase) "moderate" else "minor")
                put("your_answer", a.answer.take(160))
                put("correct_answer", a.expected.take(160))
                put("context", "🧩 ${t.kind.wire}: ${ex.sentence.take(120)} (${t.rule})")
            })
        }
    }

    /** Each case's and rule's last answers as the tutor reads them: right of how many, the stars. */
    private fun masteryJson(p: CaseProgress, prefix: String): JsonObject = buildJsonObject {
        for ((k, v) in p.keys) if (k.startsWith(prefix)) {
            val id = k.removePrefix(prefix)
            put(id, buildJsonObject {
                put("right", v.count { it == '1' })
                put("of", v.length)
                stars(p, k)?.let { put("stars", it) }
            })
        }
    }

    /**
     * The session for the node (POST /cases/done; companion/bridge/src/features/cases.ts): its minutes, its focus, each
     * answer (its kind, item, case, number, rule, verdict, the deciding word's tap, what was answered and right, the
     * sentence), the errors ready for update-db.py, the mastery after it, and that it is machine-written.
     */
    fun wire(
        pool: CasePool, after: CasesRecorded, tasks: List<CaseTask>, exercises: List<Exercise.CaseTask>, answers: List<CaseAnswer>,
        mistakes: List<MistakeNote>, minutes: Int, focus: String?,
    ): JsonObject = buildJsonObject {
        put("duration_minutes", minutes.coerceIn(1, 240))
        focus?.let { put("focus", it) }
        put("answers", buildJsonArray {
            for (i in answers.indices) {
                val t = tasks.getOrNull(i) ?: continue
                val a = answers[i]
                add(buildJsonObject {
                    put("kind", t.kind.wire)
                    put("item", t.key.take(120))
                    put("case", t.case.id)
                    put("number", t.number)
                    put("rule", t.rule)
                    put("verdict", a.verdict.name.lowercase())
                    put("why", when (a.whyRight) { true -> "right"; false -> "wrong"; null -> "none" })
                    put("answer", a.answer.take(200))
                    put("expected", a.expected.take(200))
                    exercises.getOrNull(i)?.let { put("sentence", it.sentence.take(200)) }
                    if (a.lookedUp) put("looked_up", true)
                })
            }
        })
        put("errors", errors(pool, tasks, exercises, answers, mistakes))
        put("mastery", masteryJson(after.progress, "case:"))
        put("rules", masteryJson(after.progress, "rule:"))
        put("machine_written", true)
    }

    /** What an answer was, in a line ("2. rodilnik · brez", "na tržnico", "lepo hišo"), and what was right. */
    fun answerLine(ex: Exercise.CaseTask, picked: String, tapped: String?): String = listOfNotNull(picked, tapped).joinToString(" · ")

    /** The right answer in a line: the case and the deciding word, the option, the form. */
    fun expectedLine(ex: Exercise.CaseTask): String {
        val kase = Kase.of(ex.case)
        val decider = when {
            ex.taps.isNotEmpty() -> ex.taps.mapNotNull { ex.wordAt(it) }.firstOrNull()
            else -> ex.role
        }
        return when (ex.kind) {
            "decline" -> ex.shown
            "place" -> listOfNotNull(ex.options.getOrNull(ex.answer), decider).joinToString(" · ")
            else -> listOfNotNull(kase?.let { "${it.n}. ${it.sl}" }, decider).joinToString(" · ")
        }
    }
}

/** The bare word at token [i] of the sentence (a place item's: of the whole sentence, not the prompt). */
fun Exercise.CaseTask.wordAt(i: Int): String? = (if (kind == "place") CaseBanks.tokens(sentence) else tokens).getOrNull(i)?.let(CaseBanks::bare)?.takeIf { it.isNotEmpty() }
