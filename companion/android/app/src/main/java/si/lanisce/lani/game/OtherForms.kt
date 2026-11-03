package si.lanisce.lani.game

import si.lanisce.lani.data.FormSlot
import si.lanisce.lani.data.LemmaForms
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget

/**
 * Italian's, German's and English's forms on a word's card and in its review (companion/README.md, "Word forms", "Other
 * languages"; the bridge's paradigms.ts): their own slots, each on its language's grammar book page. [WordForms] hands an
 * entry of one of them ([LemmaForms.language]) here for its table, the frame a form is asked in, its label and which
 * forms are asked; Slovene's stay [WordForms]' own. Pure, but for [child].
 *
 * - **it**: presente, passato prossimo (the participle with avere or essere: "ho mangiato", "sono andato/a"),
 *   imperfetto, futuro, condizionale, congiuntivo ("che io mangi"), congiuntivo imperfetto, passato remoto, imperativo,
 *   gerundio ("sto mangiando"); a noun's singular and plural; an adjective's genders by number, its comparative and
 *   superlative.
 * - **de**: Präsens, Präteritum, Perfekt ("ich habe gespielt", "ich bin gefahren"), Konjunktiv II, Imperativ; a noun's
 *   four cases (1. Nominativ … 4. Akkusativ, the school's order) by number with the article ("dem Haus", "den Häusern");
 *   an adjective's comparative and superlative.
 * - **en**: be's present and past by person; another verb's -s, past, perfect ("has walked") and -ing; a noun's plural;
 *   an adjective's comparative and superlative.
 */
object OtherForms {
    val LANGUAGES = setOf("it", "de", "en")

    /** Whether the learner is a child (Teo): the grammar terms then say what they mean in a child's words too. */
    @Volatile var child: Boolean = false

    /** [lemma]'s language when it is one of these. */
    fun of(lemma: LemmaForms): String? = lemma.language?.takeIf { it in LANGUAGES }

    private val P6 = listOf("1sg", "2sg", "3sg", "1pl", "2pl", "3pl")

    /** The pronouns a table writes before a person's form, and a frame starts with. */
    private val PRONOUNS = mapOf(
        "it" to mapOf("1sg" to "io", "2sg" to "tu", "3sg" to "lui, lei", "1pl" to "noi", "2pl" to "voi", "3pl" to "loro"),
        "de" to mapOf("1sg" to "ich", "2sg" to "du", "3sg" to "er, sie, es", "1pl" to "wir", "2pl" to "ihr", "3pl" to "sie"),
        "en" to mapOf("1sg" to "I", "2sg" to "you", "3sg" to "he, she, it", "1pl" to "we", "2pl" to "you", "3pl" to "they"),
    )
    /** A frame's subject: one pronoun. */
    private val SUBJECT = mapOf(
        "it" to mapOf("1sg" to "Io", "2sg" to "Tu", "3sg" to "Lui", "1pl" to "Noi", "2pl" to "Voi", "3pl" to "Loro"),
        "de" to mapOf("1sg" to "Ich", "2sg" to "Du", "3sg" to "Er", "1pl" to "Wir", "2pl" to "Ihr", "3pl" to "Sie"),
    )

    // --- labels ------------------------------------------------------------------------------------------------------

    /** The l10n key of what a slot's head is ("impf" → wordForms.imperfect). */
    private fun termKey(lang: String, key: String): String {
        val head = key.substringBefore('.')
        return when (head) {
            "pres" -> "wordForms.present"
            "impf" -> "wordForms.imperfect"
            "fut" -> "wordForms.future"
            "cond" -> "wordForms.conditional"
            "subj" -> "wordForms.subjunctive"
            "subji" -> "wordForms.subjunctiveImperfect"
            "rem" -> "wordForms.remotePast"
            "pret" -> "wordForms.preterite"
            "konj2" -> "wordForms.konjunktiv2"
            "imp" -> "wordForms.imperative"
            "ger" -> "wordForms.gerund"
            "ing" -> "wordForms.ingForm"
            "pp" -> if (lang == "en") "wordForms.pastParticiple" else "wordForms.perfect"
            "past" -> "wordForms.past"
            "sg" -> "wordForms.singular"
            "pl" -> "wordForms.plural"
            "adj" -> "wordForms.adjective"
            "cmp" -> "wordForms.comparative"
            "sup" -> "wordForms.superlative"
            else -> caseOf(key)?.let { "wordForms.case$it" } ?: "wordForms.title"
        }
    }

    /** A term's words for a child, in the learner's base ("presente · Präsens — was jetzt ist"), when there are some. */
    private fun kid(term: String): String? =
        if (!child || term.removePrefix("wordForms.") !in KID) null else runCatching { inBase(term.replace("wordForms.", "wordForms.kid.")) }.getOrNull()?.takeIf { it.isNotBlank() }

    /** The terms with a child's words (wordForms.kid.<term> in every language). */
    private val KID = setOf(
        "present", "perfect", "imperfect", "future", "conditional", "subjunctive", "subjunctiveImperfect", "remotePast", "imperative", "gerund",
        "plural", "adjective", "comparative", "preterite", "konjunktiv2", "past", "ingForm", "pastParticiple",
    )

    /** A term in the learner's pair, glossed for a child: "presente · Präsens (jetzt)". */
    private fun term(key: String): String = bi(key) + kid(key)?.let { " ($it)" }.orEmpty()

    /** What a form is, short ("imperfetto · Präteritum", "3. Fall · dative — plural"), for a question and a locked line. */
    fun label(lang: String, key: String): String {
        val t = term(termKey(lang, key))
        val number = key.substringAfter('.', "")
        return if (lang == "de" && caseOf(key) != null) "$t — ${bi(if (number == "pl") "wordForms.plural" else "wordForms.singular")}" else t
    }

    /** German's four cases in the school's order (1. Nominativ, 2. Genitiv, 3. Dativ, 4. Akkusativ). */
    private val DE_CASES = listOf("nom", "gen", "dat", "acc")
    private fun caseOf(key: String): Int? = DE_CASES.indexOf(key.substringBefore('.')).takeIf { it >= 0 }?.plus(1)

    /** The school's questions of German's four cases. */
    private val DE_QUESTIONS = listOf("wer? was?", "wessen?", "wem?", "wen? was?")

    /** German's definite article of [gender] ("m", "f", "n"; "pl") in case [case] (nom, gen, dat, acc). */
    fun article(gender: String?, case: String): String? {
        val row = when (gender) {
            "m" -> listOf("der", "des", "dem", "den")
            "f" -> listOf("die", "der", "der", "die")
            "n" -> listOf("das", "des", "dem", "das")
            "pl" -> listOf("die", "der", "den", "die")
            else -> return null
        }
        return row[DE_CASES.indexOf(case).takeIf { it >= 0 } ?: return null]
    }

    // --- which forms are asked -----------------------------------------------------------------------------------------

    /**
     * Whether [slot] of [lemma] is asked at all: not the word's own form (a noun's singular, an adjective's masculine), not
     * Italian's passato remoto (its page teaches it to recognise),
     * not a form the same as the word ("cut", cut, cut), and in German not a case the same as its nominative (dem Haus:
     * the article is the case's, the noun's form says nothing) nor a plural's case the same as its nominative plural.
     */
    fun asked(slot: FormSlot, lemma: LemmaForms): Boolean {
        val lang = of(lemma) ?: return false
        if (slot.key in setOf("sg", "adj.m.sg", "nom.sg")) return false
        // the passato remoto is to recognise (its page: "you mostly need to recognise it"): read in the table, never asked
        if (lang == "it" && slot.key.startsWith("rem.")) return false
        val own = lemma.lemma.lowercase()
        if (slot.forms.all { it.lowercase() == own }) return false
        if (lang == "de" && lemma.pos == "noun") {
            val nomPl = lemma.slot("nom.pl")?.forms.orEmpty().map { it.lowercase() }
            if (slot.key.endsWith(".pl") && slot.key != "nom.pl" && slot.forms.all { it.lowercase() in nomPl }) return false
        }
        return true
    }

    /** What a form's alternatives are grouped by: a verb's tense, a noun's number, an adjective's degree. */
    fun group(key: String): String = when {
        key.startsWith("adj.") -> "adj"
        caseOf(key) != null -> key.substringAfter('.')
        else -> key.substringBefore('.')
    }

    // --- frames ------------------------------------------------------------------------------------------------------

    /** Italian's avere and essere in the present, for the passato prossimo; German's haben and sein, for the Perfekt. */
    private val AUX = mapOf(
        "avere" to listOf("ho", "hai", "ha", "abbiamo", "avete", "hanno"),
        "essere" to listOf("sono", "sei", "è", "siamo", "siete", "sono"),
        "haben" to listOf("habe", "hast", "hat", "haben", "habt", "haben"),
        "sein" to listOf("bin", "bist", "ist", "sind", "seid", "sind"),
    )
    private val IT_REFLEXIVE = listOf("mi", "ti", "si", "ci", "vi", "si")
    private fun reflexive(lemma: LemmaForms) = lemma.language == "it" && lemma.pos == "verb" && lemma.lemma.endsWith("rsi")
    private fun auxOf(lemma: LemmaForms): String = lemma.aux.firstOrNull() ?: if (lemma.language == "de") "haben" else if (reflexive(lemma)) "essere" else "avere"

    /**
     * The sentence a form is asked in when no line of the content has it ([WordForms.GAP] where it goes): a person's
     * form after its pronoun ("Io ____.", "Domani io ____.", "Lei pensa che io ____."), a participle after its auxiliary
     * ("Ho ____.", "Lui è ____.", "Ich bin ____."), an imperative ("(tu) ____!"), a noun's plural after two, a German
     * case after its article and a preposition ("mit den ____"), an adjective after a noun of its gender; English's after
     * a subject and a time ("Yesterday I ____."). Null for a form that has none.
     */
    fun frame(lemma: LemmaForms, key: String): String? {
        val lang = of(lemma) ?: return null
        val g = WordForms.GAP
        val head = key.substringBefore('.')
        val person = key.substringAfter('.', "").takeIf { it in P6 }
        return when (lang) {
            "it" -> {
                val s = person?.let { SUBJECT.getValue("it").getValue(it) }
                val low = s?.lowercase()
                when (head) {
                    "pres" -> s?.let { "$it $g." }
                    "impf" -> low?.let { "Prima $it $g ogni giorno." }
                    "fut" -> low?.let { "Domani $it $g." }
                    "cond" -> s?.let { "$it $g volentieri." }
                    // another subject in the main clause: Italian says "penso di …" for its own
                    "subj" -> low?.let { "Lei pensa che $it $g." }
                    "subji" -> low?.let { "Se $it $g, sarebbe bello." }
                    "rem" -> low?.let { "Tanto tempo fa $it $g." }
                    "imp" -> when (key) { "imp.2sg" -> "(tu) $g!"; "imp.1pl" -> "(noi) $g!"; "imp.2pl" -> "(voi) $g!"; else -> null }
                    "ger" -> if (reflexive(lemma)) null else "Sto $g."
                    "pp" -> if (reflexive(lemma)) "Lui si è $g." else if (auxOf(lemma) == "essere") "Lui è $g." else "Ho $g."
                    "pl" -> "Ecco due $g."
                    "adj" -> when (key) { "adj.f.sg" -> "La casa è $g."; "adj.m.pl" -> "I ragazzi sono $g."; "adj.f.pl" -> "Le ragazze sono $g."; else -> null }
                    "cmp" -> "Questo è $g di quello."
                    "sup" -> "È $g!"
                    else -> null
                }
            }
            "de" -> {
                val s = person?.let { SUBJECT.getValue("de").getValue(it) }
                when (head) {
                    "pres", "pret", "konj2" -> s?.let { "$it $g." }
                    "imp" -> when (key) { "imp.2sg" -> "(du) $g!"; "imp.2pl" -> "(ihr) $g!"; else -> null }
                    "pp" -> if (auxOf(lemma) == "sein") "Ich bin $g." else "Ich habe $g."
                    "nom" -> if (key == "nom.pl") "zwei $g" else null
                    "gen" -> if (key == "gen.pl") "die Farbe der $g" else article(lemma.gender, "gen")?.let { "die Farbe $it $g" }
                    "dat" -> if (key == "dat.pl") "mit den $g" else article(lemma.gender, "dat")?.let { "mit $it $g" }
                    "acc" -> if (key == "acc.pl") "Ich sehe die $g." else article(lemma.gender, "acc")?.let { "Ich sehe $it $g." }
                    "cmp" -> "Er ist $g als ich."
                    "sup" -> "Er ist $g."
                    else -> null
                }
            }
            else -> when (key) {
                "pres.1sg" -> "I $g here."
                "pres.2sg" -> "You $g here."
                "pres.3sg" -> if (lemma.lemma == "be") "She $g here." else "She $g every day."
                "past.1sg" -> "I $g at home yesterday."
                "past.2sg" -> "We $g at home yesterday."
                "past" -> "Yesterday I $g."
                "pp" -> "I have $g."
                "ing" -> "I am $g now."
                "pl" -> "two $g"
                "cmp" -> "It is $g than that one."
                "sup" -> "It is the $g."
                else -> null
            }
        }
    }

    // --- the table on the word's card ------------------------------------------------------------------------------

    /** The forms of a slot as a cell's text ("parlo", "fahre ab"; two of them "/"). */
    private fun text(s: FormSlot) = s.forms.joinToString(" / ")

    /**
     * [lemma]'s forms as its card shows them ([locks] of each slot): see the object's description. Locked cells keep their
     * pages ("🔒" on the card, with the page that opens them).
     */
    fun table(lemma: LemmaForms, locks: (FormSlot) -> List<String>): List<WordForms.Section> {
        val lang = of(lemma) ?: return emptyList()
        fun cell(key: String, render: (String) -> String = { it }): WordForms.Cell? {
            val s = lemma.slot(key) ?: return null
            return WordForms.Cell(key, render(text(s)), locks(s), label(lang, key))
        }
        val sections = mutableListOf<WordForms.Section>()
        val numbers = listOf("sg", "pl")
        val pronouns = PRONOUNS.getValue(lang)
        /** A tense by person: rows 1–3, columns singular and plural ("io parlo | noi parliamo"). */
        fun persons(head: String, prefix: String = "", render: (String, String) -> String = { p, f -> "${pronouns.getValue(p)} $prefix$f" }) {
            val rows = (1..3).map { p ->
                "" to numbers.map { n -> cell("$head.$p$n") { f -> render("$p$n", f) } }
            }.filter { (_, cells) -> cells.any { it != null } }
            if (rows.isNotEmpty()) sections += WordForms.Section(term(termKey(lang, "$head.1sg")), numbers, rows)
        }
        when {
            lemma.pos == "verb" && lang == "it" -> {
                persons("pres")
                lemma.slot("pp")?.let { pp -> sections += perfect(lemma, pp, locks, pronouns) }
                persons("impf")
                persons("fut")
                persons("cond")
                persons("subj") { p, f -> "che ${pronouns.getValue(p)} $f" }
                persons("subji") { p, f -> "se ${pronouns.getValue(p)} $f" }
                persons("rem")
                val imp = listOfNotNull(cell("imp.2sg") { "(tu) $it!" }, cell("imp.1pl") { "(noi) $it!" }, cell("imp.2pl") { "(voi) $it!" })
                if (imp.isNotEmpty()) sections += WordForms.Section(term("wordForms.imperative"), emptyList(), listOf("" to imp))
                cell("ger") { if (reflexive(lemma)) it else "sto $it" }?.let { sections += WordForms.Section(term("wordForms.gerund"), emptyList(), listOf("" to listOf(it))) }
            }
            lemma.pos == "verb" && lang == "de" -> {
                persons("pres")
                persons("pret")
                lemma.slot("pp")?.let { pp -> sections += perfect(lemma, pp, locks, pronouns) }
                persons("konj2")
                val imp = listOfNotNull(cell("imp.2sg") { "$it!" }, cell("imp.2pl") { "$it!" })
                if (imp.isNotEmpty()) sections += WordForms.Section(term("wordForms.imperative"), emptyList(), listOf("" to imp))
            }
            lemma.pos == "verb" -> {
                if (lemma.lemma == "be") {
                    listOf("pres" to listOf("pres.1sg" to "I", "pres.2sg" to "you", "pres.3sg" to "he, she, it", "pres.2sg" to "we, they"), "past" to listOf("past.1sg" to "I", "past.2sg" to "you", "past.1sg" to "he, she, it", "past.2sg" to "we, they"))
                        .forEach { (head, cells) ->
                            val row = cells.mapNotNull { (k, who) -> cell(k) { "$who $it" } }
                            if (row.isNotEmpty()) sections += WordForms.Section(term(termKey(lang, "$head.1sg")), emptyList(), listOf("" to row))
                        }
                    listOfNotNull(cell("pp") { "have $it" }, cell("ing") { "am $it" }).takeIf { it.isNotEmpty() }?.let { sections += WordForms.Section(bi("wordForms.title"), emptyList(), listOf("" to it)) }
                } else {
                    // one column, a row a form, its tense the row's label ("past · walked")
                    val rows = listOf(
                        "wordForms.present" to (cell("pres.3sg") { "he, she $it" } ?: cell("pres.1sg") { "I $it" }),
                        "wordForms.past" to cell("past"),
                        "wordForms.pastParticiple" to cell("pp") { "have $it" },
                        "wordForms.ingForm" to cell("ing") { "am $it" },
                    ).filter { it.second != null }.map { (k, c) -> inTarget(k) to listOf(c) }
                    if (rows.isNotEmpty()) sections += WordForms.Section(term("wordForms.title"), listOf(WordForms.ONE), rows)
                }
            }
            lemma.pos == "noun" && lang == "de" -> {
                val rows = (1..4).map { n ->
                    val c = DE_CASES[n - 1]
                    "$n. ${DE_QUESTIONS[n - 1]}" to listOf(
                        cell("$c.sg") { f -> article(lemma.gender, c)?.let { "$it $f" } ?: f },
                        cell("$c.pl") { f -> "${article("pl", c)} $f" },
                    )
                }.filter { (_, cells) -> cells.any { it != null } }
                if (rows.isNotEmpty()) sections += WordForms.Section(term("wordForms.cases"), numbers, rows)
            }
            lemma.pos == "noun" -> {
                val row = listOf(cell("sg"), cell("pl"))
                if (row.any { it != null }) sections += WordForms.Section(term("wordForms.plural"), numbers, listOf("" to row))
            }
            lemma.pos == "adj" && lang == "it" -> {
                val rows = listOf(
                    "♂" to numbers.map { n -> cell("adj.m.$n") },
                    "♀" to numbers.map { n -> cell("adj.f.$n") },
                ).filter { (_, cells) -> cells.any { it != null } }
                if (rows.isNotEmpty()) sections += WordForms.Section(term("wordForms.adjective"), numbers, rows)
                val more = listOfNotNull(cell("cmp") { "↑ $it" }, cell("sup") { "↑↑ $it" })
                if (more.isNotEmpty()) sections += WordForms.Section(term("wordForms.comparative"), emptyList(), listOf("" to more))
            }
            lemma.pos == "adj" -> {
                val more = listOfNotNull(cell("cmp") { "↑ $it" }, cell("sup") { if (lang == "en") "↑↑ the $it" else "↑↑ $it" })
                if (more.isNotEmpty()) sections += WordForms.Section(term("wordForms.comparative"), emptyList(), listOf("" to more))
            }
        }
        return sections
    }

    /**
     * The compound past by person, of the participle [pp] and the auxiliary's present: Italian's passato prossimo ("ho
     * mangiato"; with essere the participle agrees, "sono andato/a", "siamo andati/e"; a reflexive verb's pronoun before,
     * "mi sono lavato/a"), German's Perfekt ("ich habe gespielt", "ich bin gefahren"). Every cell is the participle's.
     */
    private fun perfect(lemma: LemmaForms, pp: FormSlot, locks: (FormSlot) -> List<String>, pronouns: Map<String, String>): WordForms.Section {
        val lang = lemma.language!!
        val aux = auxOf(lemma)
        val forms = AUX.getValue(aux)
        val part = pp.forms.first()
        val agree = lang == "it" && aux == "essere" && part.endsWith("o")
        val numbers = listOf("sg", "pl")
        val rows = (1..3).map { p ->
            "" to numbers.mapIndexed { ni, n ->
                val i = (p - 1) + ni * 3
                val participle = if (!agree) part else part.dropLast(1) + if (n == "sg") "o/a" else "i/e"
                val refl = if (reflexive(lemma)) "${IT_REFLEXIVE[i]} " else ""
                WordForms.Cell("pp", "${pronouns.getValue("$p$n")} $refl${forms[i]} $participle", locks(pp), label(lang, "pp"))
            }
        }
        return WordForms.Section(term("wordForms.perfect"), numbers, rows)
    }
}
