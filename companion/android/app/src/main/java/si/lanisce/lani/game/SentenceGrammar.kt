package si.lanisce.lani.game

import si.lanisce.lani.game.scene.Numbers
import si.lanisce.lani.l10n.inBase

/** What the dictionary says of a word where it stands (the word card's first entry): its dictionary form, part of speech, reading. */
data class WordReading(val lemma: String, val pos: String?, val grammar: String?)

/**
 * Why a word has its form, as far as a rule knows: its [case] and the question it answers ([line]), and what decides it
 * ([trigger]: the preposition, the number, the no, the verb); for a verb, who does it ([person]: "jaz") or its tense
 * ([tense]: a string table's key; an infinitive, the verb it is [after]: moram narediti); for an adjective, the [gender]
 * it agrees in.
 */
data class WordWhy(
    val line: CaseLine? = null,
    val trigger: Trigger? = null,
    val person: String? = null,
    val tense: String? = null,
    val after: String? = null,
    val gender: String? = null,
) {
    val case: Case? get() = line?.case
}

/**
 * A word of a line as "🔍 Slovnica stavka · The sentence's grammar" shows it (companion/SCENES.md, "The sentence's
 * grammar"): the [word] as written, its dictionary form and part of speech ([lemma], [pos]), its [reading] narrowed by
 * the sentence ("genitive plural": after a preposition, a number, a no), why that form ([why]) and its grammar book
 * [page]; a word no rule explains has only its form.
 */
data class WordRow(val word: String, val lemma: String?, val pos: String?, val reading: String?, val why: WordWhy?, val page: String?)

/**
 * The grammar of a sentence, word by word (docs/grammar-syllabus.md, "The long press on a line"): each word's reading from
 * the dictionary, narrowed where its sentence decides (the preposition before it, a number, a no), and why that form where
 * a rule knows, with the page that explains it. Pure: the readings come from the word lookups (the bridge's lexicon.ts).
 */
object SentenceGrammar {
    private val NOMINAL = setOf("noun", "pron", "pronoun", "adj", "adjective", "num", "numeral", "det", "determiner", "name")

    /** What may stand between a preposition or a number and its noun: pod veliko smreko, pet velikih hiš. */
    private val BETWEEN = setOf("adj", "adjective", "num", "numeral", "det", "determiner", "pron", "pronoun")

    /** The verbs after which another verb stays in the infinitive (moram narediti, znam plavati). */
    private val MODALS = (
        "moram moraš mora morava morata moramo morate morajo smem smeš sme smemo smete smejo hočem hočeš hoče hočemo hočete hočejo " +
            "nočem nočeš noče znam znaš zna znamo znate znajo želim želiš želi morem moreš more moremo morete morejo"
        ).split(" ").toSet()

    private val FUTURE = "bom boš bo bova bosta bomo boste bodo".split(" ").toSet()

    private val DAYS = "ponedeljek torek sredo četrtek petek soboto nedeljo".split(" ").toSet()

    /**
     * The rows of [sentence]: one per word ([Forms.words]), with [readings] by the word's place (null: not looked up, or
     * the dictionary doesn't know it); [has]: whether the book has a page, the only ones a row links.
     */
    fun analyze(sentence: String, readings: List<WordReading?>, has: (String) -> Boolean): List<WordRow> {
        val spans = Forms.spans(sentence)
        val words = spans.map { it.value.lowercase() }
        return spans.indices.map { i ->
            val r = readings.getOrNull(i)
            val (reading, why, page) = explain(sentence, words, i, readings)
            WordRow(spans[i].value, r?.lemma, r?.pos, reading ?: r?.grammar, why, page?.takeIf(has))
        }
    }

    private fun explain(sentence: String, words: List<String>, i: Int, readings: List<WordReading?>): Triple<String?, WordWhy?, String?> {
        val r = readings.getOrNull(i)
        val pos = r?.pos?.lowercase()
        val w = words[i]
        if (pos == "verb") return verb(words, i, r)
        // the preposition or the number itself, a word that is none of the nominal ones: only its form
        val unknown = pos == null || pos == "word"
        if (w in Triggers.PREPOSITIONS || (!unknown && pos !in NOMINAL) || (unknown && Triggers.number(w) != null)) return Triple(null, null, null)
        val triggers = Triggers.of(sentence, i) { k -> readings.getOrNull(k)?.pos?.lowercase() in BETWEEN || (readings.getOrNull(k) == null && Triggers.adjectiveLike(words[k])) }
        val near = triggers.firstOrNull { it is Trigger.Preposition || it is Trigger.Count || it is Trigger.Amount }
        val count = near as? Trigger.Count
        val allowed = when (near) {
            is Trigger.Preposition -> near.cases
            is Trigger.Amount -> listOf(Case.GEN)
            else -> null
        }
        // a noun in its dictionary form without a reading (šotor: the pack's word) is the nominative, or an accusative like it
        val grammar = r?.grammar ?: if (pos == "noun" && r?.lemma.equals(spanOf(sentence, i), ignoreCase = true)) "nominative/accusative singular" else null
        var reading = WordReadings.narrow(grammar, allowed, count?.category)
        val negation = triggers.firstOrNull { it is Trigger.Negation && it.at < i }
        if (near == null && negation != null) reading = WordReadings.narrow(reading, listOf(Case.GEN), null)
        // a verb that takes a case decides between the readings that have it (pomagam babici: the dative, not the locative)
        if (near == null && (negation == null || WordReadings.case(reading) == null)) {
            triggers.firstOrNull { it is Trigger.Partner && it.case in WordReadings.cases(reading) }?.let { p -> reading = WordReadings.narrow(reading, Triggers.cases(p), null) }
        }
        val case = WordReadings.case(reading) ?: (near as? Trigger.Preposition)?.cases?.singleOrNull()?.takeIf { r == null }
        val question = { c: Case -> CaseLine(c.question, null, c) }
        return when {
            near is Trigger.Preposition && case != null && case in near.cases ->
                Triple(reading, WordWhy(TurnHints.preposition(near.word, case, time = timeAfter(near.word, words, i)), near), place(near.word, case, words, i))
            count != null && (unknown || pos == "noun" || pos == "adj" || pos == "adjective") &&(count.category != Numbers.MANY || case == null || case == Case.GEN) ->
                Triple(reading, WordWhy(if (count.category == Numbers.MANY) question(Case.GEN) else null, count), "stevila-samostalniki")
            near is Trigger.Amount && (case == null || case == Case.GEN) -> Triple(reading, WordWhy(CaseLine("Koliko?", "hint.qHowMany", Case.GEN), near), "rodilnik-kolicina")
            negation != null && case == Case.GEN -> Triple(reading, WordWhy(question(Case.GEN), negation), "rodilnik-nikalnica")
            case != null && case != Case.NOM -> {
                val partner = triggers.firstOrNull { it is Trigger.Partner && it.case == case } as? Trigger.Partner
                val page = when (case) {
                    Case.DAT -> "dajalnik"
                    Case.ACC -> "tozilnik"
                    Case.GEN -> if (partner != null) "povratni-glagoli" else "rodilnik"
                    Case.INS -> "orodnik"
                    Case.LOC -> "mestnik"
                    Case.NOM -> null
                }
                Triple(reading, WordWhy(question(case), partner), page)
            }
            pos == "adj" || pos == "adjective" -> WordReadings.gender(reading)?.let { g -> Triple(reading, WordWhy(gender = g), "pridevniki-ujemanje") } ?: Triple(reading, null, null)
            else -> Triple(reading, null, null)
        }
    }

    private fun spanOf(sentence: String, i: Int): String = Forms.spans(sentence)[i].value

    /** An hour after ob, a day after v: a time (kdaj-cas), not a place. */
    private fun timeAfter(prep: String, words: List<String>, i: Int): Boolean {
        val p = prep.lowercase()
        return (p == "ob" && (Triggers.number(words[i]) != null || words[i].endsWith("ih"))) || (p == "v" && words[i] in DAYS)
    }

    /** The page a preposition's case is on: where (kje), where to (kam), with (z/s), about (o), from (iz, od), when (kdaj). */
    private fun place(prep: String, case: Case, words: List<String>, i: Int): String {
        val p = prep.lowercase()
        if (timeAfter(p, words, i)) return "kdaj-cas"
        return when (case) {
            Case.LOC -> if (p == "o" || p == "po") "mestnik" else "kje-mestnik-orodnik"
            Case.ACC -> if (p in Triggers.PLACES) "kam-tozilnik" else "tozilnik"
            // z/s (with) and pod, nad, pred, za, med (where): the instrumental's page
            Case.INS -> "orodnik"
            Case.GEN -> "rodilnik-predlogi"
            Case.DAT -> "dajalnik"
            Case.NOM -> "skloni"
        }
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

    /** Why a word has its form, in the learner's base: "Kam? (where to? …) → 4. tožilnik (accusative) · after «v»". */
    fun why(w: WordWhy): String? {
        val parts = buildList {
            w.line?.let { add(TurnHints.caseLine(it)) }
            w.trigger?.let { add(TurnHints.trigger(it)) }
            w.person?.let { add(inBase("sentence.person", "who" to it)) }
            w.after?.let { add(inBase("sentence.infinitive", "word" to it)) } ?: w.tense?.let { add(inBase(it)) }
            w.gender?.let { add(inBase("sentence.agrees", "gender" to it)) }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}

/**
 * A dictionary reading ("nominative/accusative dual/plural; genitive singular", the lookup's `grammar`) and its narrowing
 * by what the sentence decides: the cases a preposition takes, a number's category. Pure.
 */
object WordReadings {
    private val CASES = Case.entries.map { it.lexicon }.toSet() + "vocative"
    private val NUMBERS = setOf("singular", "dual", "plural")
    private val GENDERS = setOf("masculine", "feminine", "neuter")

    /** One reading: its words, each with its alternatives ("dative/locative"). */
    private fun parse(g: String): List<List<List<String>>> = g.split("; ").map { r -> r.split(" ").filter { it.isNotBlank() }.map { it.split("/") } }

    private fun kind(alts: List<String>): String? = when {
        alts.all { it in CASES } -> "case"
        alts.all { it in NUMBERS } -> "number"
        alts.all { it in GENDERS } -> "gender"
        else -> null
    }

    /**
     * [grammar] with only the readings [cases] allow (the cases narrowed to them) and those of a number's [category] (1: the
     * singular, 2: the dual, 3–4: the nominative or accusative plural, 5 and up: the genitive plural); as it is when none
     * fits or nothing narrows it. A reading without a case (a verb's) stays. At most three readings.
     */
    fun narrow(grammar: String?, cases: Collection<Case>?, category: Int?): String? {
        if (grammar.isNullOrBlank()) return grammar
        var allowed: Set<String>? = cases?.map { it.lexicon }?.toSet()
        val number: String? = when (category) {
            null -> null
            Numbers.ONE -> "singular"
            Numbers.TWO -> "dual"
            Numbers.FEW -> "plural".also { allowed = (allowed ?: CASES).intersect(setOf("nominative", "accusative")) }
            else -> "plural".also { allowed = (allowed ?: CASES).intersect(setOf("genitive")) }
        }
        if (allowed == null && number == null) return grammar
        val kept = parse(grammar).mapNotNull { r ->
            if (r.none { kind(it) == "case" }) return@mapNotNull r
            val out = r.map { alts ->
                when (kind(alts)) {
                    "case" -> allowed?.let { a -> alts.filter { it in a } } ?: alts
                    "number" -> number?.let { n -> alts.filter { it == n } } ?: alts
                    else -> alts
                }
            }
            out.takeIf { it.none { a -> a.isEmpty() } && (number == null || r.any { kind(it) == "number" }) }
        }
        if (kept.isEmpty()) return grammar
        return kept.take(3).joinToString("; ") { r -> r.joinToString(" ") { it.joinToString("/") } }
    }

    /** The cases [grammar]'s readings name (the vocative aside). */
    fun cases(grammar: String?): Set<Case> {
        if (grammar.isNullOrBlank()) return emptySet()
        return parse(grammar).flatMap { r -> r.filter { kind(it) == "case" }.flatten() }.mapNotNull(Case::ofLexicon).toSet()
    }

    /** The one case [grammar]'s readings leave (the vocative aside); null when none or several. */
    fun case(grammar: String?): Case? = cases(grammar).singleOrNull()

    /** The one gender [grammar]'s readings leave; null when none or several. */
    fun gender(grammar: String?): String? {
        if (grammar.isNullOrBlank()) return null
        return parse(grammar).flatMap { r -> r.filter { kind(it) == "gender" }.flatten() }.toSet().singleOrNull()
    }
}
