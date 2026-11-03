package si.lanisce.lani.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang

/** What a speaking or writing item's answer must use: [label] says it, [any]'s rules ([Production]) match it [n] times or more. */
@Serializable
data class Must(val label: String, val any: List<String>, val n: Int = 1, val grammar: String? = null)

/** A mistake the unit is about, caught at once ([explain]: why, and what is right); never a form people say. */
@Serializable
data class Avoid(val any: List<String>, val explain: String, val grammar: String? = null)

/** How long a text is, in words: at least [min] (shorter is a check not met); [max], what the prompt asks at most (said, not graded). */
@Serializable
data class Length(val min: Int = 1, val max: Int? = null)

/** A speaking item's child's version: the keys a child plays instead (bridge production.ts forChild). */
@Serializable
data class SpeakingChild(
    val prompt: String? = null,
    val ask: String? = null,
    val say: String? = null,
    val accept: List<String>? = null,
    val spoken: List<String>? = null,
    val must: List<Must>? = null,
    @SerialName("min_words") val minWords: Int? = null,
    val show: Boolean? = null,
)

/** A writing item's child's version: a child says it first, then may write a line ([Exercise.Writing.forChild]). */
@Serializable
data class WritingChild(
    val prompt: String? = null,
    val ask: String? = null,
    val model: String? = null,
    val must: List<Must>? = null,
    val avoid: List<Avoid>? = null,
    val length: Length? = null,
)

/**
 * Speaking and free writing in a unit (companion/COURSE.md, "Speaking and writing"), pure: the rules of what an answer
 * must use and must not, a text checked against them, its verdict, and what a recognizer heard graded for a speaking item.
 * The bridge's half is companion/bridge/src/production.ts; bridge/test/fixtures/production-rules.json holds cases both read.
 *
 * A rule is a few words matched against the answer's words, folded as [Grading.fold] folds them in the language (case,
 * the letters a keyboard may lack, its grading table's other spellings): `če` the word, `pogovor*` a word that begins so
 * and goes on, `*la` one that ends so (longer than the ending), `bi … *l` words in a row with none to [GAP_WORDS] between.
 */
object Production {
    /** At most this many words stand for a … in a rule. */
    const val GAP_WORDS = 3
    private const val RULE_TOKENS = 6
    private val WORD = Regex("[\\p{L}\\p{N}]+")
    private val SPACE = Regex("\\s+")
    private val MARKS = Regex("[/|()\\[\\]\\{\\}<>]") // braces escaped: Android's ICU regex reads a bare { as a quantifier

    private sealed interface Tok
    private data object Gap : Tok
    private data class Word(val core: String, val head: Boolean, val tail: Boolean) : Tok {
        fun fits(w: String) = when {
            head && tail -> core in w && w != core
            head -> w.endsWith(core) && w.length > core.length
            tail -> w.startsWith(core) && w.length > core.length
            else -> w == core
        }
    }

    /** The words of [s] as rules see them: runs of letters and digits of its [Grading.fold] ("l'ho" → l, ho). */
    fun wordsOf(s: String, lang: Lang = L10n.pair.target): List<String> = WORD.findAll(Grading.fold(s, lang)).map { it.value }.toList()

    /** [rule] as tokens; null for one the bridge refuses (it never serves one). */
    private fun parse(rule: String, lang: Lang): List<Tok>? {
        val parts = rule.trim().split(SPACE).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val toks = mutableListOf<Tok>()
        for (p in parts) {
            if (p == "…" || p == "...") {
                if (toks.isEmpty() || toks.last() == Gap) return null
                toks += Gap
                continue
            }
            if (MARKS.containsMatchIn(p)) return null
            val head = p.startsWith("*")
            val tail = p.endsWith("*") && p.length > 1
            val core = p.substring(if (head) 1 else 0, if (tail) p.length - 1 else p.length)
            if ('*' in core) return null
            // a stem or an ending as written: the table's rewrites of a whole word would complete it ("würd*" as "würde*",
            // missing würde itself; "hab*" as "habe*")
            val words = if (head || tail) WORD.findAll(Grading.foldStem(core, lang)).map { it.value }.toList() else wordsOf(core, lang)
            if (words.isEmpty() || (words.size > 1 && (head || tail))) return null
            words.forEach { toks += Word(it, head, tail) }
        }
        return toks.takeIf { it.last() != Gap && it.size <= RULE_TOKENS }
    }

    /** Whether [rule] is one ([parse]). */
    fun valid(rule: String, lang: Lang = L10n.pair.target): Boolean = parse(rule, lang) != null

    /** Where a match of [toks] from word [i] of [words] ends (the index after it), the shortest; -1 for none. */
    private fun endOf(words: List<String>, i: Int, toks: List<Tok>, t: Int = 0): Int {
        if (t == toks.size) return i
        return when (val tok = toks[t]) {
            Gap -> {
                for (k in 0..GAP_WORDS) {
                    if (i + k > words.size) break
                    val r = endOf(words, i + k, toks, t + 1)
                    if (r >= 0) return r
                }
                -1
            }
            is Word -> if (i < words.size && tok.fits(words[i])) endOf(words, i + 1, toks, t + 1) else -1
        }
    }

    /** The matches of [rules] in [text]: non-overlapping, left to right, each the (folded) words it matched. */
    fun matches(text: String, rules: List<String>, lang: Lang = L10n.pair.target): List<String> {
        val alts = rules.mapNotNull { parse(it, lang) }
        val words = wordsOf(text, lang)
        val out = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            var end = -1
            for (a in alts) {
                val r = endOf(words, i, a)
                if (r > i && (end < 0 || r < end)) end = r
            }
            if (end > i) {
                out += words.subList(i, end).joinToString(" ")
                i = end
            } else i++
        }
        return out
    }

    // --- a text checked --------------------------------------------------------------------------------------------

    /** A must, checked: matched [count] times of the [n] it asks. */
    data class MustMark(val label: String, val count: Int, val n: Int, val grammar: String? = null) {
        val met: Boolean get() = count >= n
    }

    /** An avoid, checked: what of it the text has ([hits]: none, it's fine). */
    data class AvoidMark(val explain: String, val hits: List<String>, val grammar: String? = null)

    /** A text checked: its musts, its avoids, its [words] against the length's [min]. */
    data class Checked(val must: List<MustMark>, val avoid: List<AvoidMark>, val words: Int, val min: Int? = null, val max: Int? = null) {
        val short: Boolean get() = min != null && words < min
        /** The checks not met: a must missing, too short, a mistake avoided not. */
        val missing: Int get() = must.count { !it.met } + (if (short) 1 else 0) + avoid.count { it.hits.isNotEmpty() }
    }

    /** [c] for the tutor (a session_end's result, a writing's answer): each must met or not, each avoid's hits, the words. */
    fun json(c: Checked): JsonObject = buildJsonObject {
        put("must", buildJsonArray { c.must.forEach { m -> add(buildJsonObject { put("label", m.label); put("met", m.met); put("count", m.count); put("n", m.n) }) } })
        if (c.avoid.isNotEmpty()) put("avoid", buildJsonArray { c.avoid.forEach { a -> add(buildJsonObject { put("explain", a.explain); put("hits", buildJsonArray { a.hits.forEach { add(JsonPrimitive(it)) } }) }) } })
        put("words", c.words)
        c.min?.let { put("min_words", it) }
    }

    fun check(text: String, must: List<Must>, avoid: List<Avoid> = emptyList(), length: Length? = null, lang: Lang = L10n.pair.target): Checked = Checked(
        must.map { MustMark(it.label, matches(text, it.any, lang).size, it.n, it.grammar) },
        avoid.map { AvoidMark(it.explain, matches(text, it.any, lang), it.grammar) },
        wordsOf(text, lang).size, length?.min, length?.max,
    )

    /**
     * A checked text's verdict (companion/COURSE.md "How they count"): every check met right; one not met almost; more, or
     * nothing written, not half the length asked, or none of what it must use, wrong.
     */
    fun verdict(c: Checked): Verdict = when {
        c.words == 0 -> Verdict.WRONG
        c.min != null && c.words * 2 < c.min -> Verdict.WRONG
        c.must.isNotEmpty() && c.must.none { it.met } -> Verdict.WRONG
        c.missing == 0 -> Verdict.CORRECT
        c.missing == 1 -> Verdict.ALMOST
        else -> Verdict.WRONG
    }

    // --- what a recognizer heard ----------------------------------------------------------------------------------

    /**
     * How a recognizer writes a word as it sounds, for the standard one (tutor-live.md, "Speaking items": never counted):
     * Slovene's v said as u before a word ("u vasi" for v vasi), f before a voiceless sound ("f šoli").
     */
    private val SOUNDED: Map<String, List<Pair<Regex, String>>> = mapOf(
        "sl" to listOf(Regex("(?<![\\p{L}\\p{N}])[uUfF](?=\\s+\\p{L})") to "v"),
    )

    /** [heard] with a recognizer's sound-spellings written as the standard has them ([SOUNDED]). */
    fun asWritten(heard: String, lang: Lang = L10n.pair.target): String =
        SOUNDED[lang.code].orEmpty().fold(heard) { s, (re, to) -> re.replace(s, to) }

    /**
     * What a recognizer heard for speaking item [ex], graded: the model and accept as any said answer ([Grading.check]: a
     * slip of the keyboard isn't one here, the language's endings are); a form people say right ([Exercise.Speaking.spoken],
     * and the grading table's nonstandard: in speech it is right, [Grading.Hint.SPOKEN] with the standard as expected); an
     * open answer ([Exercise.Speaking.must]) right by its checks ([verdict]). The best of the [alternatives].
     */
    fun heard(ex: Exercise.Speaking, alternatives: List<String>, confidences: FloatArray?, lang: Lang = L10n.pair.target): SpeechGrading.Heard? {
        val answers = ex.answers()
        return alternatives.withIndex().filter { it.value.isNotBlank() }.map { (i, raw) ->
            val t = asWritten(SpokenNumbers.inWords(raw.trim(), lang, answers.joinToString(" ")), lang)
            SpeechGrading.Heard(t, confidences?.getOrNull(i)?.takeIf { it > 0f }, grade(ex, t, lang))
        }.let(SpeechGrading::bestOf)
    }

    /** [said] (one take, as written) for [ex]: see [heard]. */
    fun grade(ex: Exercise.Speaking, said: String, lang: Lang = L10n.pair.target): Grading.Result {
        val answers = ex.answers()
        val r = Grading.check(said, answers, lang)
        if (r.verdict == Verdict.CORRECT) return r
        // what people say, in speech: right (writing wants the standard: the table's nonstandard, a German verb moved up)
        if (r.hint == Grading.Hint.NONSTANDARD) return Grading.Result(Verdict.CORRECT, Grading.Hint.SPOKEN, r.expected, emptyList())
        if (ex.spoken.isNotEmpty()) {
            val s = Grading.check(said, ex.spoken, lang)
            if (s.verdict != Verdict.WRONG) return Grading.Result(s.verdict, Grading.Hint.SPOKEN, ex.say, emptyList())
        }
        if (ex.must.isEmpty()) return r
        // an open answer: right by what it uses, its ending slips the language's own as any (the checks fold č, š, ž)
        val c = check(said, ex.must, ex.avoid, ex.minWords?.let { Length(it) }, lang)
        val v = verdict(c)
        return if (v.ordinal < r.verdict.ordinal) Grading.Result(v, Grading.Hint.NONE, ex.say, emptyList()) else r
    }
}
