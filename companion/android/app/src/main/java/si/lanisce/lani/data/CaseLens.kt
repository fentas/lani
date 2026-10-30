package si.lanisce.lani.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.game.Case
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.ui.words.WordToken
import si.lanisce.lani.ui.words.WordTokens

// --- the case lens: GET /lookup's `case`, POST /case-lens (companion/bridge/src/case-lens.ts, docs/plans/05-built/case-lens.md) ---

/** A word of the line the lens points at: its place among the line's words, as written, where it is (UTF-16 offsets). */
@Serializable
data class CaseRef(val i: Int, val word: String = "", val start: Int, val end: Int, val lemma: String? = null)

/** A case and number the line leaves possible, a gender (an adjective's, a pronoun's) and what it would be ([role]). */
@Serializable
data class CaseOptionWire(val case: String, val number: String, val gender: String? = null, @SerialName("as") val role: String? = null)

/** One reading of the form, whatever the line: its lemma, part of speech, case, number, genders. */
@Serializable
data class CaseReading(val lemma: String, val pos: String, val case: String, val number: String, val genders: List<String> = emptyList(), val derived: Boolean = false)

/** A reading of the form the line rules out, and why (dual, preposition, needs-preposition, needs-trigger, agreement …). */
@Serializable
data class CaseOut(val case: String, val number: String, val why: String)

/** A word that decides the case of others (a preposition, a negation …): which, and the case it gives each. */
@Serializable
data class CaseGoverns(val i: Int, val word: String = "", val start: Int = 0, val end: Int = 0, val words: List<GovernedWord> = emptyList())

@Serializable
data class GovernedWord(val i: Int, val case: String, val word: String = "", val start: Int = 0, val end: Int = 0)

/**
 * What the lens says of a tapped word (GET /lookup's `case`): its [readings], the one the line picks ([pick], null when
 * it can't tell), how sure ([sure]: decided, likely, ambiguous), what decided ([kind], [trigger], [sense]), the grammar
 * page ([rule]), the [options] left, the likely one first, the readings ruled [out], an adjective's noun ([agrees]), a
 * form read by its endings ([derived]); a preposition or another trigger tapped: what it [governs] (no [pick]). [seen]:
 * this look counted as met for [rule] (once a day per word of a line): WordsController hands it to the case trainer,
 * whose count in the village state (GameState.cases.seen) is the only one.
 */
@Serializable
data class CaseRead(
    val i: Int = 0,
    val word: String = "",
    val start: Int = 0,
    val end: Int = 0,
    val readings: List<CaseReading> = emptyList(),
    val pick: CaseOptionWire? = null,
    val sure: String? = null,
    val kind: String? = null,
    val trigger: CaseRef? = null,
    val sense: String? = null,
    val rule: String? = null,
    val options: List<CaseOptionWire> = emptyList(),
    val out: List<CaseOut> = emptyList(),
    val agrees: CaseRef? = null,
    val derived: Boolean = false,
    val governs: CaseGoverns? = null,
    val seen: Boolean = false,
)

/** A word of a line as the colours draw it (POST /case-lens): where, its case (null: the line can't tell), how sure. */
@Serializable
data class CaseSpan(
    val i: Int,
    val start: Int,
    val end: Int,
    val word: String = "",
    val case: String? = null,
    val number: String? = null,
    val gender: String? = null,
    val sure: String = "ambiguous",
    val kind: String? = null,
    val rule: String? = null,
)

@Serializable
data class CaseLineWire(val words: List<CaseSpan> = emptyList())

@Serializable
data class CasesAnswer(val language: String? = null, val lines: List<CaseLineWire> = emptyList())

/**
 * A word's case as the word card shows it ("Sklon · Case", [CaseLens.row]): one case, sure ([State.DECIDED]) or the line's
 * likely one ([State.LIKELY], the others with it); several ([State.AMBIGUOUS]: the readings, the likely one first); or the
 * tapped word decides others' ([State.GOVERNS]: a preposition, a negation …).
 */
data class CaseRow(
    val state: State,
    /** The case shown first: the pick, or the likely one; null for [State.GOVERNS]. */
    val case: Case?,
    val number: String?,
    val gender: String?,
    val kind: String,
    val trigger: CaseRef?,
    val sense: String?,
    val rule: String?,
    /** The readings the line leaves (the likely first); one for [State.DECIDED]. */
    val options: List<CaseChoice>,
    /** The form's other readings, ruled out here, with why ("mize can also be nom./acc. pl."). */
    val others: List<CaseChoice>,
    val agrees: CaseRef?,
    val derived: Boolean,
    /** What the tapped trigger decides: each word's case. */
    val governs: List<Pair<CaseRef, Case>>,
) {
    enum class State { DECIDED, LIKELY, AMBIGUOUS, GOVERNS }
}

/** A case and number (a gender), and [why] a reading is out, or [role] what it would be. */
data class CaseChoice(val case: Case, val number: String, val gender: String? = null, val why: String? = null, val role: String? = null)

/** The pure parts of the case lens in the app: the word card's row, the colours' marks, a tapped word's place. */
object CaseLens {
    /** The lens's languages: the bridge's FORMS_LANGUAGES (its cases are Slovene's). */
    val LANGUAGES = setOf("sl")

    /** [CaseRead]'s case key (nom, gen …) as the app's [Case]. */
    fun caseOf(key: String?): Case? = when (key) {
        "nom" -> Case.NOM
        "gen" -> Case.GEN
        "dat" -> Case.DAT
        "acc" -> Case.ACC
        "loc" -> Case.LOC
        "ins" -> Case.INS
        else -> null
    }

    /**
     * The word card's row of [read]; null when the lens has nothing to say (an older bridge, a word without a case that
     * decides none, a reading the line fits none of: the card says nothing rather than something wrong).
     */
    fun row(read: CaseRead?): CaseRow? {
        read ?: return null
        val pick = read.pick
        if (pick == null && read.options.isEmpty() && read.governs != null) {
            val governs = read.governs.words.mapNotNull { g -> caseOf(g.case)?.let { CaseRef(g.i, g.word, g.start, g.end) to it } }
            if (governs.isEmpty()) return null
            return CaseRow(CaseRow.State.GOVERNS, null, null, null, read.kind ?: "trigger", null, null, read.rule, emptyList(), emptyList(), null, false, governs)
        }
        if (read.kind == "unresolved") return null
        // a gender is an adjective's, a pronoun's, a number's to show (a noun's is on its entry: mleko · s.)
        val gendered = read.readings.any { it.pos in GENDERED }
        val options = read.options.mapNotNull { o ->
            caseOf(o.case)?.let { CaseChoice(it, o.number, o.gender.takeIf { gendered }, role = o.role) }
        }
        if (options.isEmpty()) return null
        val state = when (read.sure) {
            "decided" -> CaseRow.State.DECIDED
            "likely" -> CaseRow.State.LIKELY
            else -> CaseRow.State.AMBIGUOUS
        }
        val first = (pick?.let { p -> caseOf(p.case)?.let { CaseChoice(it, p.number, p.gender, role = p.role) } } ?: options.first())
            .let { if (gendered) it else it.copy(gender = null) }
        // the form's readings the line rules out, each case and number once, with the first reason given
        val shown = options.map { it.case to it.number }.toSet()
        val others = read.out.mapNotNull { o -> caseOf(o.case)?.let { CaseChoice(it, o.number, why = o.why) } }
            .filter { (it.case to it.number) !in shown }
            .distinctBy { it.case to it.number }
            .take(MAX_OTHERS)
        return CaseRow(
            state, first.case, first.number, first.gender, read.kind ?: "form", read.trigger, read.sense, read.rule,
            if (state == CaseRow.State.DECIDED) listOf(first) else options, others, read.agrees, read.derived, emptyList(),
        )
    }

    /**
     * The [n]th of the tapped [token]'s word among the words of [line] that are written the same (case aside), from 0:
     * what GET /lookup's `n` says. The tap's own place when the text shown is [line] ([token]'s offsets are the line's),
     * else the same word nearest it (a line shown with a prefix: "📜 …", "1. …").
     */
    fun nth(line: String, token: WordToken): Int {
        val same = WordTokens.of(line).filter { it.text.equals(token.text, ignoreCase = true) }
        if (same.size <= 1) return 0
        same.indexOfFirst { it.start == token.start }.takeIf { it >= 0 }?.let { return it }
        return same.withIndex().minBy { (_, t) -> kotlin.math.abs(t.start - token.start) }.index
    }

    /**
     * The marks the colours draw under a line's words ([spans], POST /case-lens), shifted by [offset] when the text shows the
     * line after a prefix: a decided case in its colour, the rest (likely, ambiguous) dotted. Words outside [length] (the
     * text shown) are left out.
     */
    fun marks(spans: List<CaseSpan>, offset: Int = 0, length: Int = Int.MAX_VALUE): List<CaseMark> = spans.mapNotNull { s ->
        val start = s.start + offset
        val end = s.end + offset
        if (start < 0 || end > length || end <= start) return@mapNotNull null
        CaseMark(start, end, if (s.sure == "decided") caseOf(s.case) else null)
    }

    /** POST /case-lens' body: the lines to colour. */
    fun casesBody(lines: List<String>) = buildJsonObject { put("lines", buildJsonArray { lines.forEach { add(JsonPrimitive(it)) } }) }

    fun parseCases(raw: String): CasesAnswer = json.decodeFromString(CasesAnswer.serializer(), raw)

    /**
     * What decided the case, in one line, the target's then the base's (the word card's row and the sentence's grammar
     * say the same): a preposition (with its sense: kam? kje? kdaj?), a negation, a quantity or a number from five, a
     * verb or a word of feeling (an adjective with a case: poln vode), a copula, the subject's or the object's verb, a
     * call, the noun it belongs to, a time, the genitive of time (leta 1844, nekega dne), a phrase, a fused pronoun, its
     * ending alone. None when nothing can say (an ambiguous word, a trigger tapped).
     */
    fun why(row: CaseRow, read: CaseRead): Pair<String, String>? {
        if (row.state == CaseRow.State.GOVERNS) return null
        val word = read.trigger?.word.orEmpty()
        val case = row.case
        val key = when (row.kind) {
            "preposition" -> "caseLens.whyPreposition"
            "negation" -> "caseLens.whyNegation"
            "quantity" -> "caseLens.whyQuantity"
            "number" -> "caseLens.whyNumber"
            "verb" -> if (read.trigger?.lemma != null) "caseLens.whyVerb" else "caseLens.whyVerbForm"
            // a word of feeling (všeč mi je: who feels it), or an adjective that takes a case (poln vode)
            "predicate" -> if (row.rule == "custva") "caseLens.whyPredicate" else "caseLens.whyTakes"
            "copula" -> "caseLens.whyCopula"
            "predicative" -> "caseLens.whyPredicative"
            "subject" -> if (word.isNotEmpty()) "caseLens.whySubject" else "caseLens.whySubjectAlone"
            "object" -> if (word.isNotEmpty()) "caseLens.whyObject" else "caseLens.whyObjectAlone"
            "address" -> "caseLens.whyAddress"
            "attribute" -> "caseLens.whyAttribute"
            "time" -> "caseLens.whyTime"
            "when" -> "caseLens.whyWhen"
            "phrase" -> "caseLens.whyPhrase"
            "fused" -> "caseLens.whyFused"
            else -> when (case) {
                Case.DAT -> "caseLens.whyFormDative"
                Case.GEN -> "caseLens.whyFormGenitive"
                Case.NOM -> "caseLens.whyFormNominative"
                else -> "caseLens.whyForm"
            }
        }
        if (row.state == CaseRow.State.AMBIGUOUS && row.kind != "preposition" && row.kind != "negation") return null
        val pair = L10n.pair
        val say = { lang: Lang ->
            val args = mapOf("word" to word, "lemma" to (read.trigger?.lemma ?: ""), "case" to (case?.let { L10n.text(lang, it.key) } ?: ""))
            val sense = row.sense?.let { senseKey(it) }?.let { " (${L10n.text(lang, it)})" }.orEmpty()
            L10n.text(lang, key, args) + if (row.kind == "preposition") sense else ""
        }
        return say(pair.target) to say(pair.base)
    }

    /** A preposition's sense, said as the school asks it: kam?, kje?, kdaj? … */
    fun senseKey(sense: String): String? = when (sense) {
        "motion" -> "caseLens.senseMotion"
        "rest" -> "caseLens.senseRest"
        "time" -> "caseLens.senseTime"
        "with" -> "caseLens.senseWith"
        "from" -> "caseLens.senseFrom"
        "for" -> "caseLens.senseFor"
        "about" -> "caseLens.senseAbout"
        "after" -> "caseLens.senseAfter"
        "towards" -> "caseLens.senseTowards"
        "fetch" -> "caseLens.senseFetch"
        else -> null
    }

    /** "Zahteva rodilnik: «mleka» · Takes the genitive: «mleka»": what a tapped trigger decides, in both languages. */
    fun governs(c: Case, word: String): String {
        val pair = L10n.pair
        val t = L10n.text(pair.target, "caseLens.governs", mapOf("case" to L10n.text(pair.target, c.key), "word" to word))
        val b = L10n.text(pair.base, "caseLens.governs", mapOf("case" to L10n.text(pair.base, c.key), "word" to word))
        return if (t == b) t else "$t · $b"
    }

    /** The parts of speech whose gender the card shows (they agree in it). */
    private val GENDERED = setOf("adj", "det", "pron", "num")

    /** At most this many of the form's other readings a card lists ("can also be"). */
    const val MAX_OTHERS = 4

    /** At most this many lines one POST /case-lens asks about, each this long at most (the bridge's limits). */
    const val BATCH = 60
    const val MAX_CHARS = 400
}

/** A word's underline in the colours ([CaseLens.marks]): its place in the text shown, its case (null: dotted, can't tell). */
data class CaseMark(val start: Int, val end: Int, val case: Case?)
