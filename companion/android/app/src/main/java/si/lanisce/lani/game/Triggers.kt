package si.lanisce.lani.game

import si.lanisce.lani.data.SpokenNumbers
import si.lanisce.lani.game.scene.Numbers
import si.lanisce.lani.l10n.Lang

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
     * A word whose partner takes [case]: a verb (pomagam komu?, bojim se česa?, zebe koga?) or a word of feeling (všeč,
     * mrzlo: komu?). [lemma]: its dictionary form (pomagati), for what the hint says of it.
     */
    data class Partner(override val word: String, override val at: Int, val case: Case, val lemma: String) : Trigger

    /** A number's noun: the number agrees with its gender (dva brata, dve sestri). */
    data class Noun(override val word: String, override val at: Int) : Trigger

    /** A word that says who does it (jaz, oni: the verb's person) or when (včeraj: the past, jutri: the future). */
    data class Cue(override val word: String, override val at: Int, val kind: Kind) : Trigger {
        enum class Kind { WHO, PAST, FUTURE }
    }
}

/**
 * The triggers of Slovene: the tables of what decides a form (the prepositions and their cases, as the bridge's lookup
 * narrows by them, lexicon.ts; the numbers; the amounts; the negations; the verbs with a dative, a genitive or an
 * accusative of who feels it), and which of them a word of a sentence has. Pure.
 */
object Triggers {
    /** The prepositions and their cases (the bridge's lexicon.ts; za + the genitive left out: rare, and it misleads). */
    val PREPOSITIONS: Map<String, List<Case>> = buildMap {
        for (w in listOf("v", "na", "po")) put(w, listOf(Case.LOC, Case.ACC))
        for (w in listOf("o", "pri", "ob")) put(w, listOf(Case.LOC))
        for (w in listOf("k", "h", "proti", "kljub")) put(w, listOf(Case.DAT))
        for (w in "iz od do brez blizu zaradi okoli okrog mimo poleg sredi zraven izpod izza namesto".split(" ")) put(w, listOf(Case.GEN))
        for (w in listOf("z", "s")) put(w, listOf(Case.INS, Case.GEN))
        for (w in listOf("med", "nad", "pod", "pred", "za")) put(w, listOf(Case.INS, Case.ACC))
        for (w in listOf("skozi", "čez", "zoper")) put(w, listOf(Case.ACC))
    }

    /**
     * Where to (Kam?) against where (Kje?): the prepositions that take either, by what the sentence means (po too: grem po
     * čebulo, to fetch it; po vasi, all round the village).
     */
    val PLACES = setOf("v", "na", "po", "pod", "nad", "pred", "za", "med")

    /** The number words 1 to 100 in any of their forms (ena, en, eno, dve, trije …), sto and tisoč, with their value. */
    private val NUMBERS: Map<String, Int> = buildMap {
        for (n in 1..100) for (f in SpokenNumbers.forms(n, Lang.SL)) putIfAbsent(f, n)
        put("sto", 100)
        put("tisoč", 1000)
    }

    /** Amounts: their noun takes the genitive (malo vode, kos kruha, koliko ovc). */
    val AMOUNTS: Set<String> = (
        "malo veliko preveč premalo nekaj koliko toliko več manj dovolj precej polno ducat kup " +
            "kos kosa kosov hlebec hlebca kozarec kozarca kozarcev skodelica skodelico skodelice liter litra litre litrov " +
            "kilogram kilograma kilograme kilogramov žlica žlico žlice žlička žličko steklenica steklenico steklenice " +
            "vreča vrečo vreče škatla škatlo škatle par para"
        ).split(" ").toSet()

    /** The negations: what they deny takes the genitive (nimam časa, ne pijem kave, ni sira). */
    val NEGATIONS: Set<String> = (
        "ne ni nimam nimaš nima nimava nimata nimamo nimate nimajo nisem nisi nisva nista nismo niste niso " +
            "nočem nočeš noče nočeva nočeta nočemo nočete nočejo"
        ).split(" ").toSet()

    /** A word whose partner takes a case, by its forms (or the start they share): the case and its dictionary form. */
    private data class Takes(val case: Case, val lemma: String, val forms: Set<String> = emptySet(), val stem: String? = null) {
        fun matches(w: String): Boolean = w in forms || (stem != null && w.startsWith(stem))
    }

    private fun forms(s: String) = s.split(" ").toSet()

    private val PARTNERS: List<Takes> = listOf(
        // the dative: to whom (pomagati like German helfen, dati, reči, povedati, pokazati, prinesti, nesti …)
        Takes(Case.DAT, "pomagati", stem = "pomag"),
        // not "da": that is also "that" (mislim, da …)
        Takes(Case.DAT, "dati", forms("dam daš dava dasta damo daste dajo dal dala dali dale dalo daj dajte dajmo dajva dati dajem daješ daje dajemo dajete dajejo")),
        Takes(Case.DAT, "reči", forms("rečem rečeš reče rečemo rečete rečejo rekel rekla rekli reci recite")),
        Takes(Case.DAT, "povedati", forms("povem poveš pove poveva povesta povemo poveste povejo povedal povedala povedali povej povejte povedati")),
        Takes(Case.DAT, "pokazati", stem = "pokaž"),
        Takes(Case.DAT, "pokazati", stem = "pokaza"),
        Takes(Case.DAT, "prinesti", stem = "prines"),
        Takes(Case.DAT, "nesti", forms("nesem neseš nese neseva nesemo nesete nesejo nesel nesla nesli nesi nesite nesti")),
        Takes(Case.DAT, "podariti", stem = "podar"),
        Takes(Case.DAT, "posoditi", stem = "posod"),
        Takes(Case.DAT, "verjeti", stem = "verjam"),
        Takes(Case.DAT, "zaupati", stem = "zaupa"),
        Takes(Case.DAT, "odgovoriti", stem = "odgovor"),
        Takes(Case.DAT, "telefonirati", stem = "telefonira"),
        Takes(Case.DAT, "skuhati", stem = "skuha"),
        // feeling: to whom it is so (všeč mi je, mrzlo mi je, ime mi je)
        Takes(Case.DAT, "všeč", forms("všeč")),
        Takes(Case.DAT, "mrzlo", forms("mrzlo")),
        Takes(Case.DAT, "toplo", forms("toplo")),
        Takes(Case.DAT, "vroče", forms("vroče")),
        Takes(Case.DAT, "žal", forms("žal")),
        Takes(Case.DAT, "dolgčas", forms("dolgčas")),
        Takes(Case.DAT, "ime", forms("ime")),
        // the genitive: bati se, veseliti se, spomniti se česa?
        Takes(Case.GEN, "bati se", forms("bojim bojiš boji bojiva bojita bojimo bojite bojijo bal bala bali bati")),
        Takes(Case.GEN, "veseliti se", forms("veselim veseliš veseliva veselimo veselite veselijo veselil veselila veselili")),
        Takes(Case.GEN, "spomniti se", stem = "spomn"),
        // the accusative of who feels it: zebe me, boli ga, skrbi jo
        Takes(Case.ACC, "zebsti", forms("zebe")),
        Takes(Case.ACC, "boleti", forms("boli")),
        Takes(Case.ACC, "skrbeti", forms("skrbi")),
    )

    /** Short words that are no adjective between a preposition or a number and its noun. */
    private val FUNCTION: Set<String> = (
        "je sem si smo ste so sva sta bo bom boš bomo boste bodo bi se mi ti mu ji ga jo jih jim nam vam me te nas vas " +
            "in pa ali a da ki ko ker če kot tudi še že samo zelo tam tu tukaj zdaj potem kje kam kdaj kako zakaj " +
            "to ta ti tisti ne ni ja"
        ).split(" ").toSet()

    /** The number [w] is (a word, or figures), or null. */
    fun number(w: String): Int? = w.toIntOrNull()?.takeIf { it in 0..9999 } ?: NUMBERS[w.lowercase()]

    /** Whether [w] may stand between a preposition or a number and its noun (an adjective: pod veliko smreko, pet velikih hiš). */
    fun adjectiveLike(w: String): Boolean {
        val l = w.lowercase()
        return l.length > 2 && l !in FUNCTION && l !in PREPOSITIONS && number(l) == null && l !in NEGATIONS && l !in AMOUNTS
    }

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
     * What decides the form of the word at [i] of [text] (by [Forms.words]), the nearest first: a preposition, a number or
     * an amount right before it (or one word earlier, past a word [between] allows: an adjective), then a no and a
     * verb of its clause (not the word itself). [between]: whether the word at an index may stand between.
     */
    fun of(text: String, i: Int, between: (Int) -> Boolean = { k -> adjectiveLike(Forms.words(text)[k]) }): List<Trigger> {
        val spans = Forms.spans(text)
        if (i !in spans.indices) return emptyList()
        val words = spans.map { it.value.lowercase() }
        val clause = clauses(text)
        val out = mutableListOf<Trigger>()
        val near = listOf(i - 1, i - 2).filter { k -> k >= 0 && clause[k] == clause[i] && (k == i - 1 || between(i - 1)) }
        for (k in near) {
            val w = words[k]
            val t = PREPOSITIONS[w]?.let { Trigger.Preposition(spans[k].value, k, it) }
                ?: number(w)?.let { Trigger.Count(spans[k].value, k, Numbers.slovene(it)) }
                ?: if (w in AMOUNTS) Trigger.Amount(spans[k].value, k) else null
            if (t != null) {
                out += t
                break
            }
        }
        // the clause's no and its verbs, those before the word first
        val others = words.indices.filter { it != i && clause[it] == clause[i] }.sortedBy { if (it < i) i - it else 100 + it }
        others.firstOrNull { words[it] in NEGATIONS }?.let { out += Trigger.Negation(spans[it].value, it) }
        for (k in others) {
            val p = PARTNERS.firstOrNull { it.matches(words[k]) } ?: continue
            out += Trigger.Partner(spans[k].value, k, p.case, p.lemma)
        }
        return out
    }

    /** The cases a trigger allows the word after it; none for one that isn't about a case. */
    fun cases(t: Trigger): List<Case> = when (t) {
        is Trigger.Preposition -> t.cases
        is Trigger.Count -> if (t.category == Numbers.MANY) listOf(Case.GEN) else listOf(Case.NOM, Case.ACC)
        is Trigger.Amount, is Trigger.Negation -> listOf(Case.GEN)
        is Trigger.Partner -> listOf(t.case)
        is Trigger.Noun, is Trigger.Cue -> emptyList()
    }

    /** Who does it: the pronouns that are always the subject (not mi, ti, me: those are often to me, to you, me). */
    private val WHO = "jaz on ona ono midva medve vidva vedve onadva onidve oni one vi".split(" ").toSet()
    private val PAST = "včeraj lani sinoči predvčerajšnjim prej".split(" ").toSet()
    private val FUTURE = "jutri pojutrišnjem kmalu drugič".split(" ").toSet()

    /** The words of the clause of the word at [i] of [text] that say who does it or when ([Trigger.Cue]), the nearest first. */
    fun cues(text: String, i: Int): List<Trigger.Cue> {
        val spans = Forms.spans(text)
        if (i !in spans.indices) return emptyList()
        val clause = clauses(text)
        return spans.indices.filter { it != i && clause[it] == clause[i] }.sortedBy { kotlin.math.abs(it - i) }.mapNotNull { k ->
            val w = spans[k].value.lowercase()
            val kind = when (w) {
                in WHO -> Trigger.Cue.Kind.WHO
                in PAST -> Trigger.Cue.Kind.PAST
                in FUTURE -> Trigger.Cue.Kind.FUTURE
                else -> null
            }
            kind?.let { Trigger.Cue(spans[k].value, k, it) }
        }
    }
}
