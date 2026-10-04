package si.lanisce.lani.game.scene

import kotlinx.serialization.Serializable
import si.lanisce.lani.data.SpokenNumbers
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res
import si.lanisce.lani.l10n.Lang
import java.time.LocalDate

/**
 * Numbers and the forms that agree with them (companion/SCENES.md, "Numbers: counting what the village has"): the number
 * categories of a language, a number written out in Slovene, Italian, German and English, and the wrong forms learners
 * put in its place.
 */
object Numbers {
    /** Slovene's four categories (1, 2, 3–4, 5 and more), and the two of the others (one, more): the index of a form. */
    const val ONE = 0
    const val TWO = 1
    const val FEW = 2
    const val MANY = 3

    /**
     * The category of [n] among [forms] forms. Four is Slovene's, by the last two figures: 1, 101, 201 take the singular,
     * 2 and 102 the dual, 3, 4, 103 the plural, 0, 5–20, 111, 121 … the genitive plural (enaindvajset ovc). Two is one
     * and more: 1, else the plural (Italian, German, English).
     */
    fun category(n: Int, forms: Int): Int = if (forms == 4) slovene(n) else if (n == 1) ONE else 1

    /** Slovene's category of [n] (see [category]). */
    fun slovene(n: Int): Int = when (Math.floorMod(n, 100)) {
        1 -> ONE
        2 -> TWO
        3, 4 -> FEW
        else -> MANY
    }

    /** For each Slovene category, the wrong forms learners most often put there, the likeliest first. */
    private val WRONG_SL = mapOf(ONE to listOf(FEW, TWO), TWO to listOf(FEW, MANY), FEW to listOf(MANY, TWO), MANY to listOf(FEW, ONE))

    /**
     * The wrong category a learner puts for [n] among [forms] forms: [k] 1 the likeliest, 2 the next (Slovene: for 1 the
     * 3–4 form, then the dual; for 2 the 3–4 form, then 5+; for 3–4 the 5+ form, then the dual; for 5+ the 3–4 form, then
     * the singular). With two forms it is the other one, whatever [k].
     */
    fun wrong(n: Int, forms: Int, k: Int): Int =
        if (forms == 4) WRONG_SL.getValue(slovene(n))[(k - 1).coerceIn(0, 1)] else 1 - category(n, 2)

    /**
     * [n] written out in [language] ("sl", "it", "de", "en") as it goes before a noun of [gender] ("m", "f", "n") in
     * [case] ("nom", "acc"; Slovene's masculine [animate]: enega konja); [next] is the word after it (Italian: uno
     * stivale, un'oca). Figures from 1000 on, and in a language it doesn't write.
     */
    fun words(n: Int, language: String, gender: String? = null, case: String = "nom", animate: Boolean = false, next: String = ""): String {
        val lang = Lang.of(language)
        if (n < 0 || n > 999 || lang == null) return n.toString()
        return when (language) {
            "sl" -> sl(n, gender ?: "m", case, animate)
            // one alone before its noun: ein Schaf, eine Kuh, einen Hund (accusative)
            "de" -> if (n != 1) counting(n, lang) else when (gender) {
                "f" -> "eine"
                "m" -> if (case == "acc") "einen" else "ein"
                else -> "ein"
            }
            "it" -> if (n == 1) itOne(gender ?: "m", next) else counting(n, lang)
            else -> counting(n, lang)
        }
    }

    /** The counting form of [n] (the one the speech grading reads digits as: [SpokenNumbers]). */
    private fun counting(n: Int, lang: Lang): String = SpokenNumbers.forms(n, lang).first()

    /** 1–4 agree with their noun: en konj, ena ovca, eno jajce; dva, dve; trije, tri; štirje, štiri; accusative eno, enega, tri. */
    private fun slAgreeing(n: Int, gender: String, case: String, animate: Boolean): String {
        val acc = case == "acc"
        return when (n) {
            1 -> when (gender) {
                "f" -> if (acc) "eno" else "ena"
                "n" -> "eno"
                else -> if (acc && animate) "enega" else "en"
            }
            2 -> if (gender == "m") "dva" else "dve"
            3 -> if (gender == "m" && !acc) "trije" else "tri"
            else -> if (gender == "m" && !acc) "štirje" else "štiri"
        }
    }

    /** Slovene: the hundreds as they are counted, then the rest, its 1–4 agreeing (sto ena ovca, sto dva konja). */
    private fun sl(n: Int, gender: String, case: String, animate: Boolean): String {
        val below = n % 100
        val parts = listOfNotNull(
            (n - below).takeIf { it > 0 }?.let { counting(it, Lang.SL) },
            when {
                below == 0 -> if (n == 0) "nič" else null
                below <= 4 -> slAgreeing(below, gender, case, animate)
                else -> counting(below, Lang.SL)
            },
        )
        return parts.joinToString(" ")
    }

    /** Italian one before [next]: uno before s + consonant, z, gn, ps, pn, x, y; un' (feminine) before a vowel. */
    private fun itOne(gender: String, next: String): String {
        val w = next.trimStart().lowercase()
        val vowel = w.firstOrNull()?.let { it in "aeiouàèéìòù" } == true
        if (gender == "f") return if (vowel) "un'" else "una"
        val impure = (w.length > 1 && w[0] == 's' && w[1] !in "aeiouàèéìòù") || w.startsWith("z") || w.startsWith("gn") ||
            w.startsWith("ps") || w.startsWith("pn") || w.startsWith("x") || w.startsWith("y")
        return if (impure) "uno" else "un"
    }
}

/**
 * What a dialog counts (lani.scene/v0 `count`): where its number comes from and how the number word goes with the
 * counted thing in the scene's language (companion/SCENES.md, "Numbers").
 */
@Serializable
data class DialogCount(
    /** dice | food | wood | stone | wisdom | villagers | building:<type> */
    val from: String = "dice",
    val min: Int = 1,
    val max: Int = 999,
    /** A store counted per this many (wood 73 per 10: 7 logs). */
    val per: Int = 1,
    /** The counted thing's gender in the scene's language: m, f, n. */
    val gender: String? = null,
    /** nom | acc: the case of the number phrase (Imam eno ovco). */
    val case: String = "nom",
    /** A Slovene masculine that is alive: enega konja. */
    val animate: Boolean = false,
)

/**
 * A counting dialog's number and its texts (companion/SCENES.md, "Numbers"): [of] takes the number from the village, the
 * same all day; [render] puts it into every text with the forms that agree.
 */
object Counts {
    /** A placeholder: {n}, or forms {a|b} / {a|b|c|d}, the ones marked ! ({!a|b|c|d}) a wrong choice's wrong forms. */
    // Every brace escaped: Android's regex engine (ICU) rejects a lone "}" that the JVM's reads as a literal.
    private val PLACEHOLDER = Regex("""\{(!?)([^\{\}]*)\}""")

    /**
     * The number [c] counts in [state] on [today]: the day's dice from `min` to `max` ([key]: the dialog's,
     * "scene/happening/dialog"), else what the village has (a store per `per`, its villagers, its buildings of a type),
     * kept within `min` and `max`.
     */
    fun of(c: DialogCount, state: GameState, today: LocalDate, key: String): Int {
        val lo = c.min.coerceAtLeast(1)
        val hi = c.max.coerceAtLeast(lo)
        if (c.from == "dice") return lo + (Happenings.roll(state.seed, today, "$key#n") * (hi - lo + 1)).toInt().coerceIn(0, hi - lo)
        val have = when {
            c.from == "villagers" -> state.villagers
            c.from.startsWith("building:") -> c.from.substringAfter(':').let { t -> state.buildings.count { it.type.name.equals(t, ignoreCase = true) } }
            else -> Res.entries.firstOrNull { it.name.equals(c.from, ignoreCase = true) }?.let { state.res(it) } ?: lo
        }
        return (have / c.per.coerceAtLeast(1)).coerceIn(lo, hi)
    }

    /**
     * [d] with the number [n] in every text, the dialog as the app reads it ([inPair]: "sl" said in [language], "en" what
     * it means): {n} written out where it is said and in a why (which quotes it), in figures in the meanings; the forms
     * agree with [n], but a wrong choice's with `wrong_form` (its marked ones, else all of them) take the wrong form it
     * names. A wrong choice that comes out as a right one is left out.
     */
    fun render(d: Dialog, n: Int, language: String): Dialog {
        val c = d.count ?: return d
        val arity = if (language == "sl") 4 else 2
        fun said(t: String?, wrong: Int? = null) = t?.let { text(it, n, arity, wrong, language, c) }
        fun meant(t: String?, wrong: Int? = null) = t?.let { text(it, n, arity, wrong, null, c) }
        fun reply(r: DialogReply?) = r?.copy(sl = said(r.sl)!!, en = meant(r.en)!!)
        val lines = d.lines.map { l ->
            if (l.choices.isEmpty()) return@map l.copy(sl = said(l.sl), en = meant(l.en))
            val choices = l.choices.map { ch ->
                val w = ch.wrongForm?.takeIf { !ch.ok }
                ch.copy(sl = said(ch.sl, w)!!, en = meant(ch.en, w)!!, why = said(ch.why), reply = reply(ch.reply))
            }
            // a wrong form that comes out right (English sheep), or as another wrong one did, is left out
            val seen = choices.filter { it.ok }.map { it.sl }.toHashSet()
            l.copy(sl = said(l.sl), en = meant(l.en), choices = choices.filter { it.ok || seen.add(it.sl) })
        }
        return d.copy(lines = lines)
    }

    /**
     * One text: its forms first (a select of [arity] forms is the scene's language's; the other kind is mapped: a wrong
     * Slovene form reads as the plural in two forms), then {n}: in words in [wordsIn] (the scene's language), else in
     * figures; capitalised at a sentence's start.
     */
    internal fun text(t: String, n: Int, arity: Int, wrong: Int?, wordsIn: String?, c: DialogCount): String {
        if ('{' !in t) return t
        val marked = PLACEHOLDER.findAll(t).any { it.groupValues[1] == "!" && '|' in it.groupValues[2] }
        val formed = PLACEHOLDER.replace(t) { m ->
            val body = m.groupValues[2]
            if ('|' !in body) return@replace m.value
            val forms = body.split('|')
            if (forms.size != 2 && forms.size != 4) return@replace m.value
            val flip = wrong != null && (m.groupValues[1] == "!" || !marked)
            val k = if (!flip) Numbers.category(n, forms.size) else {
                val w = Numbers.wrong(n, arity, wrong!!)
                when {
                    forms.size == arity -> w
                    forms.size == 2 -> if (w == Numbers.ONE) Numbers.ONE else 1
                    else -> if (w == Numbers.ONE) Numbers.ONE else Numbers.slovene(n).takeIf { it != Numbers.ONE } ?: Numbers.FEW
                }
            }
            forms[k]
        }
        val out = StringBuilder()
        var at = 0
        for (m in Regex("""\{n\}""").findAll(formed)) {
            out.append(formed, at, m.range.first)
            val next = formed.substring(m.range.last + 1).trimStart()
            val word = if (wordsIn == null) n.toString() else Numbers.words(n, wordsIn, c.gender, c.case, c.animate, next)
            out.append(if (startsSentence(out)) word.replaceFirstChar { it.uppercaseChar() } else word)
            at = m.range.last + 1
        }
        out.append(formed, at, formed.length)
        return out.toString()
    }

    /** Whether what comes next in [before] begins a sentence: nothing yet, or after . ! ? (quotes and dashes aside; not "…"). */
    private fun startsSentence(before: CharSequence): Boolean {
        val t = before.trimEnd { it.isWhitespace() || it in "\"'„“«»‚‘–—-(" }
        return t.isEmpty() || t.last() in ".!?"
    }
}
