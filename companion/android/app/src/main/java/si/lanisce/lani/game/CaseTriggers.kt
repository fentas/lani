package si.lanisce.lani.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import si.lanisce.lani.game.scene.Numbers

/**
 * The six cases of Slovene, numbered as a Slovene teacher numbers them, each with the question it answers
 * (companion/grammar/sl `skloni`; docs/grammar-syllabus.md, "The hint chip"). [lexicon]: the case as the dictionary's
 * readings name it ("genitive plural"); [key]: its name in the string tables ("rodilnik · genitive").
 */
enum class Case(val n: Int, val question: String, val lexicon: String, val key: String) {
    NOM(1, "Kdo? Kaj?", "nominative", "hint.caseNom"),
    GEN(2, "Koga? Česa?", "genitive", "hint.caseGen"),
    DAT(3, "Komu? Čemu?", "dative", "hint.caseDat"),
    ACC(4, "Koga? Kaj?", "accusative", "hint.caseAcc"),
    LOC(5, "Pri kom? Pri čem?", "locative", "hint.caseLoc"),
    INS(6, "S kom? S čim?", "instrumental", "hint.caseIns");

    companion object {
        /** The case a reading names ("genitive"); null for another word ("vocative", "plural"). */
        fun ofLexicon(word: String): Case? = entries.firstOrNull { it.lexicon == word }

        /** The case of a key of the trigger table and the case lens ("nom", "gen" …); null for another. */
        fun ofKey(key: String?): Case? = when (key) {
            "nom" -> NOM
            "gen" -> GEN
            "dat" -> DAT
            "acc" -> ACC
            "loc" -> LOC
            "ins" -> INS
            else -> null
        }
    }
}

/**
 * What decides the form of a word in its sentence (docs/grammar-syllabus.md, "The hint chip", "The long press on a line"):
 * a preposition or a number before it, an amount, a no in its clause, a verb whose partner takes a case. [word] is the
 * trigger as the sentence has it, [at] its place among the sentence's words ([Forms.words]).
 */
sealed interface Trigger {
    val word: String
    val at: Int

    /** A preposition and the cases it takes: iz the genitive; v where to (the accusative) or where (the locative). */
    data class Preposition(override val word: String, override val at: Int, val cases: List<Case>) : Trigger

    /** A number, and its [category] ([Numbers.ONE], [Numbers.TWO], [Numbers.FEW], [Numbers.MANY]: 1, 2, 3–4, 5 and up). */
    data class Count(override val word: String, override val at: Int, val category: Int) : Trigger

    /** An amount (malo, veliko, koliko, kos …): the genitive, Koliko? */
    data class Amount(override val word: String, override val at: Int) : Trigger

    /** A no (ne, ni, nimam, nisem …): what it denies takes the genitive. */
    data class Negation(override val word: String, override val at: Int) : Trigger

    /**
     * A word whose partner takes [cases] (the first its own): a verb (pomagam komu?, bojim se česa?, dam komu? kaj?, zebe
     * koga?) or a word of feeling (všeč, mrzlo: komu?). [lemma]: its dictionary form (pomagati), for what the hint says of it.
     */
    data class Partner(override val word: String, override val at: Int, val cases: List<Case>, val lemma: String) : Trigger

    /** A number's noun: the number agrees with its gender (dva brata, dve sestri). */
    data class Noun(override val word: String, override val at: Int) : Trigger

    /**
     * The genitive of time (leta 1844, nekega dne: Kdaj?): what makes a noun of time so, the year in figures after it or
     * the determiner before it (the table's time_genitive).
     */
    data class When(override val word: String, override val at: Int) : Trigger

    /** A word that says who does it (jaz, oni: the verb's person) or when (včeraj: the past, jutri: the future). */
    data class Cue(override val word: String, override val at: Int, val kind: Kind) : Trigger {
        enum class Kind { WHO, PAST, FUTURE }
    }
}

/**
 * The trigger table of Slovene as the hint chip reads it (companion/grammar/sl/data/case-triggers.json,
 * lani.case-triggers/v0, made by case-triggers.py beside it; bundled with the grammar book as resources
 * grammar/sl/data/case-triggers.json: app/build.gradle.kts): what decides a word's case, the same table the bridge's case
 * lens reads (bridge/src/case-lens.ts) and with it the word card's row and the sentence's grammar. The lens reads it with
 * the dictionary; the chip, at a turn, at once and offline, reads it alone: the prepositions and the cases they take by
 * sense, the negations, the words of quantity, the measures and the numbers from five, the numbers that agree with their
 * noun (one to four), the verbs and the words of feeling that govern a case (a verb's forms made from its infinitive and
 * its first person as the lens makes them), and the personal pronouns that are only ever the subject. [of] is which of
 * them a word of a sentence has. Pure once loaded.
 */
object CaseTriggers {
    /** One way a preposition takes a case: the [case], its [sense] (motion: kam?, rest: kje?, from: od kod? …), its page. */
    data class Take(val case: Case, val sense: String, val rule: String, val time: String? = null, val default: Boolean = false)

    /** A verb or a word of feeling that governs [cases] ([lemma]; [se]: with se, bati se; [after]: of what comes after it, poln vode). */
    data class Governor(val lemma: String, val cases: List<Case>, val rule: String, val se: Boolean = false, val after: Boolean = false)

    /** The table as the chip reads it ([parse]). */
    class Table(
        val prepositions: Map<String, List<Take>>,
        val negations: Set<String>,
        /** A negation before a verb only: ne. */
        val beforeVerb: Set<String>,
        val quantity: Set<String>,
        /** Every form of the measures (kos, kosa, kosov …: what they measure in the genitive), as the lens makes them. */
        val measures: Set<String>,
        /** The numbers from five (pet, deset, sto …): what they count in the genitive plural. */
        val fromFive: Set<String>,
        /** The numbers one to four in their forms (en, ena, dva, dve, trije, štirje, oba …): they agree with their noun. */
        val agreeing: Set<String>,
        /** The verbs' forms and the words of feeling, each with what it governs. */
        val governors: Map<String, List<Governor>>,
        /** The personal pronouns' nominatives that are no other case's form (jaz, on, midva …; not mi, ti, me). */
        val subjects: Set<String>,
        /** The genitive singular of the nouns of time a year in figures follows (leto: leta 1844), the time_genitive's. */
        val years: Set<String> = emptySet(),
        /** The determiners before a noun of time in the genitive (nekega dne, tistega večera). */
        val determiners: Set<String> = emptySet(),
        /** The half before an hour (pol: ob pol dveh, the next hour in the genitive), the hours'. */
        val half: Set<String> = emptySet(),
    ) {
        companion object {
            val EMPTY = Table(emptyMap(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptyMap(), emptySet())
        }
    }

    /** The bundled table of Slovene; empty when it can't be read (the chip says the page's rule alone then). */
    val table: Table by lazy {
        CaseTriggers::class.java.getResourceAsStream("/grammar/sl/data/case-triggers.json")?.use { runCatching { parse(it.readBytes().decodeToString()) }.getOrNull() } ?: Table.EMPTY
    }

    /**
     * Words a table verb has as a form that are another word first: da (dati's "gives", most often "that"), so a clause
     * with a "that" doesn't read as one with dati. The lens tells them apart by the dictionary.
     */
    private val NOT_GOVERNING = setOf("da")

    /** [raw] (lani.case-triggers/v0) as the chip reads it. */
    fun parse(raw: String): Table {
        val o = Json.parseToJsonElement(raw).jsonObject
        fun strings(e: Any?): List<String> = (e as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        fun str(e: JsonObject, k: String): String? = e[k]?.jsonPrimitive?.contentOrNull
        val preps = LinkedHashMap<String, List<Take>>()
        for (p in o["prepositions"]?.jsonArray.orEmpty()) {
            val po = p.jsonObject
            val takes = po["takes"]?.jsonArray.orEmpty().mapNotNull { t ->
                val to = t.jsonObject
                Case.ofKey(str(to, "case"))?.let { c -> Take(c, str(to, "sense") ?: "other", str(to, "rule").orEmpty(), str(to, "time"), to["default"]?.jsonPrimitive?.booleanOrNull == true) }
            }
            for (w in strings(po["words"])) preps[w] = takes
        }
        val neg = o["negation"]?.jsonObject
        val q = o["quantity"]?.jsonObject
        val verbs = HashMap<String, MutableList<Governor>>()
        val anyVerbForm = HashSet<String>()
        for (v in o["verbs"]?.jsonArray.orEmpty()) {
            val vo = v.jsonObject
            val lemma = str(vo, "lemma") ?: continue
            val g = Governor(lemma, strings(vo["takes"]).mapNotNull(Case::ofKey), str(vo, "rule").orEmpty(), se = vo["se"]?.jsonPrimitive?.booleanOrNull == true)
            for (f in verbForms(lemma, str(vo, "present") ?: lemma, strings(vo["forms"]))) {
                anyVerbForm += f
                if (f !in NOT_GOVERNING) verbs.getOrPut(f) { ArrayList() } += g
            }
        }
        for (p in o["predicates"]?.jsonArray.orEmpty()) {
            val po = p.jsonObject
            val c = Case.ofKey(str(po, "case")) ?: continue
            val after = po["after"]?.jsonPrimitive?.booleanOrNull == true
            for (w in strings(po["words"])) verbs.getOrPut(w) { ArrayList() } += Governor(w, listOf(c), str(po, "rule").orEmpty(), after = after)
        }
        val tg = o["time_genitive"] as? JsonObject
        val nominative = HashSet<String>()
        val other = HashSet<String>()
        for (p in o["pronouns"]?.jsonArray.orEmpty()) {
            for ((k, fs) in p.jsonObject["forms"]?.jsonObject.orEmpty()) (if (k == "nom") nominative else other) += strings(fs)
        }
        return Table(
            prepositions = preps,
            negations = strings(neg?.get("words")).toSet(),
            beforeVerb = strings(neg?.get("before_verb")).toSet(),
            quantity = strings(q?.get("words")).toSet(),
            // a measure's form that is also a verb's (kupi: of kup, and buy!) is the verb's
            measures = strings(q?.get("measures")).flatMap(::measureForms).toSet() - anyVerbForm,
            fromFive = strings(q?.get("numbers")).toSet(),
            agreeing = strings(o["agreeing_numbers"]).toSet(),
            governors = verbs,
            subjects = nominative - other,
            years = strings(tg?.get("years")).map(::genitiveSingular).toSet(),
            determiners = strings(tg?.get("determiners")).toSet(),
            half = strings((o["hours"] as? JsonObject)?.get("half")).toSet(),
        )
    }

    /**
     * A verb's forms as the lens makes them from the table (bridge/src/case-lens.ts `verbFormsOf`): from its first person
     * singular [present] the present's persons (pomagam … pomagajo), from its infinitive [lemma] the l-forms (pomagal,
     * pomagala …), the imperative (pomagaj, pomagajte, pomagajmo, and the dual's pomagajva, pomagajta, which the lens has
     * from the dictionary) and the infinitive, and the [extra] forms the table gives.
     * Not the supine the lens adds after a verb of motion (grem povedat): without the dictionary it would be a noun as often
     * (brat, of brati: the brother).
     */
    fun verbForms(lemma: String, present: String, extra: List<String> = emptyList()): Set<String> {
        val out = LinkedHashSet<String>()
        val stem = if (present.endsWith("m")) present.dropLast(1) else present
        for (end in listOf("m", "š", "", "va", "ta", "mo", "te", "jo")) out += stem + end
        if (stem.endsWith("e")) out += stem.dropLast(1) + "o"
        out += lemma
        if (lemma.endsWith("ti")) for (end in listOf("l", "la", "lo", "li", "le")) out += lemma.dropLast(2) + end
        val imp = when {
            stem.endsWith("a") -> stem + "j"
            stem.endsWith("e") || stem.endsWith("i") -> stem.dropLast(1) + "i"
            else -> null
        }
        // (and the dual's, which the lens finds in the dictionary: pomagajva, dajta)
        if (imp != null) for (end in listOf("", "te", "mo", "va", "ta")) out += imp + end
        out += extra
        return out
    }

    /** A noun's endings after its stem by its class, as the lens's regular paradigms have them (bridge/src/case-lens.ts). */
    private val MASCULINE = listOf("a", "u", "om", "ov", "oma", "ih", "i", "e")
    private val FEMININE = listOf("a", "e", "i", "o", "ama", "ah", "am", "ami")
    private val NEUTER = listOf("a", "u", "om", "i", "oma", "ih")

    /**
     * A measure's forms as the lens makes them (kos: kos, kosa, kosu, kosom, kosov …; kozarec, its fleeting e: kozarca;
     * skodelica: skodelic, the genitive plural), by its ending: -a feminine, -o or -e neuter, a consonant masculine.
     */
    fun measureForms(lemma: String): Set<String> {
        val out = LinkedHashSet<String>()
        val (gender, stem, endings) = when {
            lemma.endsWith("a") -> Triple('f', lemma.dropLast(1), FEMININE)
            lemma.endsWith("o") || lemma.endsWith("e") -> Triple('n', lemma.dropLast(1), NEUTER)
            else -> Triple('m', lemma.replace(Regex("e([cklnr])$"), "$1"), MASCULINE)
        }
        if (gender != 'f') out += lemma
        for (end in endings) out += stem + if (gender == 'f') end else softened(stem, end)
        if (gender == 'f') out += stem.replace(Regex("([^aeiou])([^aeiou])$"), "$1e$2")
        if (gender == 'n') out += stem
        return out
    }

    /** A noun's genitive singular by its ending (leto: leta; ura: ure; dan's own, dne, is the dictionary's): the lens's reading. */
    fun genitiveSingular(lemma: String): String = when {
        lemma.endsWith("a") -> lemma.dropLast(1) + "e"
        lemma.endsWith("o") || lemma.endsWith("e") -> lemma.dropLast(1) + "a"
        else -> lemma + "a"
    }

    /** After a soft consonant the o of an ending is an e (z nožem, s čajem), and -ov is -ev. */
    private fun softened(stem: String, end: String): String = if (stem.isNotEmpty() && stem.last() in "cčjšž" && end.startsWith("o")) "e" + end.drop(1) else end

    // --- what a word is, by the table ------------------------------------------------------------------------------

    /** The ways preposition [w] takes a case, in the table's order (the first its usual one); none for another word. */
    fun takes(w: String): List<Take> = table.prepositions[w.lowercase()].orEmpty()

    fun isPreposition(w: String): Boolean = w.lowercase() in table.prepositions

    /** The cases preposition [w] takes, each once, in the table's order. */
    fun casesOf(w: String): List<Case> = takes(w).map { it.case }.distinct()

    /**
     * Where to (Kam?) against where (Kje?): a preposition that takes a case of motion and one of rest (v, na, ob, pod, nad,
     * pred, med, za), by what the sentence means.
     */
    fun place(w: String): Boolean = takes(w).let { t -> t.any { it.sense == "motion" } && t.any { it.sense == "rest" } }

    /**
     * The sense preposition [w] has with [case] (motion, rest, from, about, towards, with …), when the sentence doesn't say:
     * a place's where to or where (za + the accusative: Kam?, before its "for"), else its usual one with that case.
     */
    fun sense(w: String, case: Case): String? {
        val with = takes(w).filter { it.case == case }
        return (with.firstOrNull { it.sense == "motion" || it.sense == "rest" } ?: with.firstOrNull { it.default } ?: with.firstOrNull())?.sense
    }

    /** The number [w] is when it counts or agrees (a figure, a number word of the table), else null; its value isn't needed. */
    fun isNumber(w: String): Boolean = numberCategory(w) != null

    private val COMPOUND = Regex("^(ena|dva|tri|štiri|pet|šest|sedem|osem|devet)in(dvajset|trideset|štirideset|petdeset|šestdeset|sedemdeset|osemdeset|devetdeset)$")

    /**
     * The category of number [w] ([Numbers.ONE], [Numbers.TWO], [Numbers.FEW], [Numbers.MANY]): a figure by its value, a
     * number from five (the table's, or a compound: petindvajset) many, one of one to four by which it is (en, dva, oba,
     * tri, štiri in their forms); null for another word.
     */
    fun numberCategory(w: String): Int? {
        val l = w.lowercase()
        l.toIntOrNull()?.takeIf { it in 0..9999 }?.let { return Numbers.slovene(it) }
        if (l in table.fromFive || COMPOUND.matches(l)) return Numbers.MANY
        if (l !in table.agreeing) return null
        return when {
            l.startsWith("en") || l == "eden" -> Numbers.ONE
            l.startsWith("dv") || l.startsWith("ob") -> Numbers.TWO
            else -> Numbers.FEW
        }
    }

    /** A word of quantity (malo, veliko, nič …) or a measure's form (kos, kozarca, steklenico …): what follows in the genitive. */
    fun isAmount(w: String): Boolean = w.lowercase().let { it in table.quantity || it in table.measures }

    /** Short words that are no adjective between a preposition or a number and its noun. */
    private val FUNCTION: Set<String> = (
        "je sem si smo ste so sva sta bo bom boš bomo boste bodo bi se mi ti mu ji ga jo jih jim nam vam me te nas vas " +
            "in pa ali a da ki ko ker če kot tudi še že samo zelo tam tu tukaj zdaj potem kje kam kdaj kako zakaj " +
            "to ta ti tisti ne ni ja"
        ).split(" ").toSet()

    /** Whether [w] may stand between a preposition or a number and its noun (an adjective: pod veliko smreko, pet velikih hiš). */
    fun adjectiveLike(w: String): Boolean {
        val l = w.lowercase()
        return l.length > 2 && l !in FUNCTION && !isPreposition(l) && !isNumber(l) && l !in table.negations && !isAmount(l)
    }

    /** The personal pronouns that are only ever the subject: who does it (jaz, on, midva …). */
    fun isSubject(w: String): Boolean = w.lowercase() in table.subjects

    /** The half before an hour (pol): ob pol dveh, the next hour in the genitive, a time. */
    fun isHalf(w: String): Boolean = w.lowercase() in table.half

    /**
     * Which clause of [text] each of its words is in: a comma, a stop, a question or exclamation mark, a colon, a
     * semicolon or a dash ends one ("Ne, nimam kruha." is two: the no that counts is nimam).
     */
    fun clauses(text: String): List<Int> {
        val spans = Forms.spans(text)
        var clause = 0
        return spans.mapIndexed { i, m ->
            if (i > 0 && text.substring(spans[i - 1].range.last + 1, m.range.first).any { it in ",.!?;:–—…" }) clause++
            clause
        }
    }

    /**
     * What decides the form of the word at [i] of [text] (by [Forms.words]), the nearest first, as the table has it: a
     * preposition, a number from five or a word of quantity (a measure) right before it (or one word earlier, past a word
     * [between] allows: an adjective, or a number of one to four, which agrees with its noun as an adjective does: z dvema
     * prijateljema, the preposition's), else such a number of one to four right before it (dva brata); then a negation of
     * its clause (ne before a word of it), then the verbs and the words of feeling of its clause that govern a case (not
     * the word itself; one of feeling that governs what comes after it, poln vode, only before it; a verb with se and one
     * without by whether the clause has se). First of all, the genitive of time ([Trigger.When]): a year noun's form
     * before a year in figures (leta 1844), a word after a determiner of time (nekega dne), not after a preposition (od
     * leta 2024: the preposition's). [between]: whether the word at an index may stand between.
     */
    fun of(text: String, i: Int, between: (Int) -> Boolean = { k -> adjectiveLike(Forms.words(text)[k]) }): List<Trigger> {
        val spans = Forms.spans(text)
        if (i !in spans.indices) return emptyList()
        val words = spans.map { it.value.lowercase() }
        val clause = clauses(text)
        val out = mutableListOf<Trigger>()
        val prev = (i - 1).takeIf { it >= 0 && clause[it] == clause[i] }
        if (prev == null || !isPreposition(words[prev])) {
            val next = (i + 1).takeIf { it < words.size && clause[it] == clause[i] }
            when {
                next != null && words[i] in table.years && Regex("""\d{3,4}""").matches(words[next]) -> out += Trigger.When(spans[next].value, next)
                prev != null && words[prev] in table.determiners -> out += Trigger.When(spans[prev].value, prev)
            }
        }
        fun agrees(k: Int) = numberCategory(words[k]).let { it != null && it != Numbers.MANY }
        val near = listOf(i - 1, i - 2).filter { k -> k >= 0 && clause[k] == clause[i] && (k == i - 1 || between(i - 1) || agrees(i - 1)) }
        // a number of one to four agrees with its noun: what decides is before it, if anything is
        var agreeing: Trigger.Count? = null
        for (k in near) {
            val w = words[k]
            if (agreeing == null && agrees(k)) {
                agreeing = Trigger.Count(spans[k].value, k, numberCategory(w)!!)
                continue
            }
            val t = casesOf(w).takeIf { it.isNotEmpty() }?.let { Trigger.Preposition(spans[k].value, k, it) }
                ?: numberCategory(w)?.takeIf { it == Numbers.MANY }?.let { Trigger.Count(spans[k].value, k, it) }
                ?: if (isAmount(w)) Trigger.Amount(spans[k].value, k) else null
            if (t != null) {
                out += t
                break
            }
        }
        agreeing?.let { out += it }
        // the clause's negation and its governors, those before the word first
        val inClause = words.indices.filter { clause[it] == clause[i] }
        val others = inClause.filter { it != i }.sortedBy { if (it < i) i - it else 100 + it }
        others.firstOrNull { k -> words[k] in table.negations || (words[k] in table.beforeVerb && inClause.any { it > k }) }?.let { out += Trigger.Negation(spans[it].value, it) }
        val se = inClause.any { words[it] == "se" }
        for (k in others) {
            val all = table.governors[words[k]].orEmpty().filter { !it.after || k < i }
            val g = all.filter { it.se == se }.ifEmpty { all }
            if (g.isNotEmpty()) out += Trigger.Partner(spans[k].value, k, g.flatMap { it.cases }.distinct(), g.first().lemma)
        }
        return out
    }

    /** The cases a trigger allows the word after it; none for one that isn't about a case. */
    fun cases(t: Trigger): List<Case> = when (t) {
        is Trigger.Preposition -> t.cases
        is Trigger.Count -> if (t.category == Numbers.MANY) listOf(Case.GEN) else listOf(Case.NOM, Case.ACC)
        is Trigger.Amount, is Trigger.Negation, is Trigger.When -> listOf(Case.GEN)
        is Trigger.Partner -> t.cases
        is Trigger.Noun, is Trigger.Cue -> emptyList()
    }
}
