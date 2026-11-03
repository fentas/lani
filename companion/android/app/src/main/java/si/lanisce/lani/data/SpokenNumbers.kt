package si.lanisce.lani.data

import si.lanisce.lani.l10n.Lang

/**
 * Numbers a speech recognizer wrote as digits, back in words, so what was heard can be graded against the text: Whisper
 * and the phone's recognizer write "dva" as "2", even inside a word ("midva" → "mi2", "dvakrat" → "2krat"). A digit
 * run stuck to letters is joined to them as a word ("mi2" → "midva"); a free one becomes its word(s) ("2 jabolki" →
 * "dve jabolki"). Where a number has several forms (Slovene dva/dve, ena/en/eno; German eins/ein/eine; Italian uno/una),
 * the one the [expected] text uses is taken, else the counting form. 0 … 999; longer runs stay as they are. Slovene's
 * words beyond (years, thousands: [slovene]) and its ordinals ([ordinal]) are the numbers module's, as Italian's are ([italian]).
 */
object SpokenNumbers {
    private val run = Regex("\\p{L}*\\d+\\p{L}*")
    private val digits = Regex("\\d+")

    /** [text] with its digit runs written out in [lang]; [expected]: the text it's graded against, for the forms. */
    fun inWords(text: String, lang: Lang, expected: String? = null): String {
        if (!text.any { it.isDigit() }) return text
        val known = expected?.let { e -> e.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }.toSet() }.orEmpty()
        return run.replace(text) { m -> word(m.value, lang, known) }
    }

    /** One token ("mi2", "2krat", "12") written out; unchanged when it has no digits or a number out of range. */
    fun word(token: String, lang: Lang, known: Set<String> = emptySet()): String {
        if (!token.any { it.isDigit() }) return token
        return digits.replace(token) { m ->
            val n = m.value.toIntOrNull()?.takeIf { it in 0..999 } ?: return@replace m.value
            val before = token.substring(0, m.range.first).lowercase()
            val after = token.substring(m.range.last + 1)
            val forms = forms(n, lang)
            // a dual pronoun: mi2 → midva, me2 → medve, vi2 → vidva, ve2 → vedve, ona2 → onadva, oni2 → onidve
            val dual = if (lang == Lang.SL && n == 2 && after.isEmpty()) DUALS[before] else null
            val stuck = before.isNotEmpty() || after.isNotEmpty()
            // the form the expected text has (alone, or joined with the letters around it: "2krat" → "dvakrat")
            dual ?: forms.firstOrNull { f -> known.any { k -> k == f || (stuck && k == before + f + after.lowercase()) } } ?: forms.first()
        }
    }

    private val DUALS = mapOf("mi" to "dva", "me" to "dve", "vi" to "dva", "ve" to "dve", "ona" to "dva", "oni" to "dve")

    /** The words for [n] in [lang], the counting form first, then the other forms a text may use. */
    fun forms(n: Int, lang: Lang): List<String> = when (lang) {
        Lang.SL -> sl(n)
        Lang.IT -> listOf(it(n)) + (if (n == 1) listOf("una", "un") else emptyList())
        Lang.DE -> listOf(de(n)) + (if (n == 1) listOf("ein", "eine", "einen", "einem", "einer") else emptyList())
        Lang.EN -> listOf(en(n))
    }

    // --- Slovene -------------------------------------------------------------------------------------------------

    private val SL_ONES = listOf("nič", "ena", "dva", "tri", "štiri", "pet", "šest", "sedem", "osem", "devet")
    private val SL_TEENS = listOf("deset", "enajst", "dvanajst", "trinajst", "štirinajst", "petnajst", "šestnajst", "sedemnajst", "osemnajst", "devetnajst")
    private val SL_TENS = listOf("", "", "dvajset", "trideset", "štirideset", "petdeset", "šestdeset", "sedemdeset", "osemdeset", "devetdeset")

    private fun sl(n: Int): List<String> {
        val base = slWord(n)
        return when (n) {
            1 -> listOf("ena", "en", "eno", "eden", "ene", "enega", "eni")
            2 -> listOf("dva", "dve", "dveh", "dvema")
            3 -> listOf("tri", "trije", "treh", "trem")
            4 -> listOf("štiri", "štirje", "štirih")
            else -> listOf(base)
        }
    }

    private fun slWord(n: Int): String = when {
        n < 10 -> SL_ONES[n]
        n < 20 -> SL_TEENS[n - 10]
        n < 100 -> if (n % 10 == 0) SL_TENS[n / 10] else SL_ONES[n % 10] + "in" + SL_TENS[n / 10] // enaindvajset
        n < 1000 -> {
            val h = n / 100
            val head = when (h) { 1 -> "sto"; 2 -> "dvesto"; else -> SL_ONES[h] + "sto" }
            if (n % 100 == 0) head else "$head ${slWord(n % 100)}"
        }
        else -> {
            // tisoč, dva tisoč, enaindvajset tisoč, sto tisoč: the thousands apart, as the hundreds are
            val t = n / 1000
            val head = if (t == 1) "tisoč" else "${slWord(t)} tisoč"
            if (n % 1000 == 0) head else "$head ${slWord(n % 1000)}"
        }
    }

    /** The largest number [slovene] writes out. */
    const val SL_MAX = 999_999

    /**
     * [n] (0 … [SL_MAX]) in Slovene words as it is counted, the form a bare number takes: 1 ena, 2 dva, 21 enaindvajset (the
     * ones first, joined by in, one word up to 99), 200 dvesto, 121 sto enaindvajset, 1000 tisoč, 2026 dva tisoč
     * šestindvajset (the hundreds and the thousands apart). The numbers module's words (data/NumbersPractice.kt); speech
     * keeps to 0 … 999 ([word]): a text may write a year in figures, and a heard "1991" must still match it.
     */
    fun slovene(n: Int): String {
        require(n in 0..SL_MAX) { "$n is outside 0 … $SL_MAX" }
        return slWord(n)
    }

    /**
     * The other Slovene words a bare [n] is right as: a gender's form of 1 to 4 (en, eno, eden; dve; trije; štirje) and
     * "en tisoč" for 1000. Never another number's.
     */
    fun sloveneAlso(n: Int): List<String> = when (n) {
        1 -> listOf("en", "eno", "eden")
        2 -> listOf("dve")
        3 -> listOf("trije")
        4 -> listOf("štirje")
        1000 -> listOf("en tisoč")
        else -> emptyList()
    }

    private val SL_ORDINALS = listOf("", "prvi", "drugi", "tretji", "četrti", "peti", "šesti", "sedmi", "osmi", "deveti")

    /**
     * [n] (1 … 99) as a Slovene ordinal, masculine, in the nominative ("Danes je peti maj."): prvi, drugi, tretji, četrti,
     * peti, šesti, sedmi, osmi (the e drops: sedem → sedmi, osem → osmi), deveti, deseti; from 11 the number and -i (enajsti,
     * dvajseti, enaindvajseti: only the last part turns, sedemindvajseti keeps its sedem).
     */
    fun ordinal(n: Int): String {
        require(n in 1..99) { "$n is outside 1 … 99" }
        return if (n < 10) SL_ORDINALS[n] else slWord(n) + "i"
    }

    // --- Italian ------------------------------------------------------------------------------------------------

    private val IT_ONES = listOf("zero", "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove")
    private val IT_TEENS = listOf("dieci", "undici", "dodici", "tredici", "quattordici", "quindici", "sedici", "diciassette", "diciotto", "diciannove")
    private val IT_TENS = listOf("", "", "venti", "trenta", "quaranta", "cinquanta", "sessanta", "settanta", "ottanta", "novanta")

    private fun it(n: Int): String = when {
        n < 10 -> IT_ONES[n]
        n < 20 -> IT_TEENS[n - 10]
        n < 100 -> {
            val t = IT_TENS[n / 10]
            val u = n % 10
            when (u) { 0 -> t; 1, 8 -> t.dropLast(1) + IT_ONES[u]; 3 -> t + "tré"; else -> t + IT_ONES[u] } // ventuno, ventotto, ventitré
        }
        n < 1000 -> {
            val h = n / 100
            val head = if (h == 1) "cento" else IT_ONES[h] + "cento"
            val low = n % 100
            val rest = it(low)
            // centotré; centotto, centottanta: cento's o goes before another
            if (low == 0) head else if (low == 3) head + "tré" else if (rest.startsWith("o")) head.dropLast(1) + rest else head + rest
        }
        else -> {
            // mille, duemila, ventitremila (tre keeps no accent inside a word), then the rest: milletré, duemilaventisei
            val t = n / 1000
            val head = if (t == 1) "mille" else it(t).replace("tré", "tre") + "mila"
            val low = n % 1000
            if (low == 0) head else head + if (low == 3) "tré" else it(low)
        }
    }

    /** The largest number [italian] writes out. */
    const val IT_MAX = 999_999

    /**
     * [n] (0 … [IT_MAX]) in Italian words as it is counted, all one word: 21 ventuno, 23 ventitré, 28 ventotto, 103 centotré,
     * 180 centottanta, 1000 mille, 2026 duemilaventisei. The numbers module's words ([NumberTongue.IT]).
     */
    fun italian(n: Int): String {
        require(n in 0..IT_MAX) { "$n is outside 0 … $IT_MAX" }
        return it(n)
    }

    /**
     * The other Italian words a bare [n] is right as: a gender's form of 1 (una, un), cento's o kept (centootto, centoottanta),
     * centuno, a thousand and what is under a hundred apart ("duemila e ventisei"). Never another number's.
     */
    fun italianAlso(n: Int): List<String> {
        val low = n % 100
        return buildList {
            if (n == 1) {
                add("una")
                add("un")
            }
            if (n in 100..999 && (low == 8 || low in 80..89)) add(it(n - low) + it(low))
            if (n in 100..999 && low == 1) add(it(n - 1).dropLast(1) + "uno")
            if (n in 1001..9999 && n % 1000 in 1..99) add("${it(n - n % 1000)} e ${it(n % 1000)}")
        }
    }

    // --- German -------------------------------------------------------------------------------------------------

    private val DE_ONES = listOf("null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun")
    private val DE_TEENS = listOf("zehn", "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn")
    private val DE_TENS = listOf("", "", "zwanzig", "dreißig", "vierzig", "fünfzig", "sechzig", "siebzig", "achtzig", "neunzig")

    private fun de(n: Int): String = when {
        n < 10 -> DE_ONES[n]
        n < 20 -> DE_TEENS[n - 10]
        n < 100 -> if (n % 10 == 0) DE_TENS[n / 10] else (if (n % 10 == 1) "ein" else DE_ONES[n % 10]) + "und" + DE_TENS[n / 10]
        else -> {
            val h = n / 100
            val head = (if (h == 1) "ein" else DE_ONES[h]) + "hundert"
            if (n % 100 == 0) head else head + (if (n % 100 == 1) "eins" else de(n % 100))
        }
    }

    // --- English ------------------------------------------------------------------------------------------------

    private val EN_ONES = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
    private val EN_TEENS = listOf("ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val EN_TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    private fun en(n: Int): String = when {
        n < 10 -> EN_ONES[n]
        n < 20 -> EN_TEENS[n - 10]
        n < 100 -> if (n % 10 == 0) EN_TENS[n / 10] else EN_TENS[n / 10] + "-" + EN_ONES[n % 10]
        else -> {
            val h = n / 100
            val head = EN_ONES[h] + " hundred"
            if (n % 100 == 0) head else "$head and ${en(n % 100)}"
        }
    }

    /** The largest number [english] writes out. */
    const val EN_MAX = 999_999

    /**
     * [n] (0 … [EN_MAX]) in British English words as it is counted: the tens first with a hyphen (twenty-seven), "and" after
     * the hundreds and before what is left under a hundred (one hundred and five, two thousand and twenty-six, one thousand
     * two hundred and thirty-four). The numbers module's words ([NumberTongue.EN]); speech keeps to 0 … 999 ([forms]).
     */
    fun english(n: Int): String {
        require(n in 0..EN_MAX) { "$n is outside 0 … $EN_MAX" }
        if (n < 1000) return en(n)
        val head = en(n / 1000) + " thousand"
        val low = n % 1000
        return when {
            low == 0 -> head
            low < 100 -> "$head and ${en(low)}"
            else -> "$head ${en(low)}"
        }
    }

    /**
     * The other English words a bare [n] is right as, never another number's: "a hundred", "a thousand" for the one at the
     * front; the American without "and" (one hundred five, two thousand twenty-six); a year said in pairs, 1000 … 9999
     * (nineteen fifty, twenty twenty-six, nineteen oh five, nineteen hundred). A hyphen left out is the same answer
     * already (grading/en.json's "same").
     */
    fun englishAlso(n: Int): List<String> {
        if (n < 100) return emptyList()
        val word = english(n)
        return buildList {
            if (n in 100..199) add("a" + word.removePrefix("one"))
            if (n in 1000..1999) add("a" + word.removePrefix("one"))
            if (" and " in word) {
                val american = word.replace(" and ", " ")
                add(american)
                if (n in 100..199 || n in 1000..1999) add("a" + american.removePrefix("one"))
            }
            if (n in 1000..9999) {
                val hi = n / 100
                val lo = n % 100
                when {
                    lo == 0 -> if (hi % 10 != 0) add("${en(hi)} hundred")
                    lo < 10 -> add("${en(hi)} oh ${en(lo)}")
                    else -> add("${en(hi)} ${en(lo)}")
                }
            }
        }.distinct().filter { it != word }
    }

    private val EN_ORDINALS = mapOf(1 to "first", 2 to "second", 3 to "third", 5 to "fifth", 8 to "eighth", 9 to "ninth", 12 to "twelfth")

    /**
     * [n] (1 … 99) as an English ordinal, as a date says it ("the fifteenth of May"): first, second, third, fifth, eighth,
     * ninth, twelfth are their own; the tens' -ty turns -tieth (twentieth); the rest the number and -th; in a compound only
     * the last part turns (twenty-first, thirty-eighth).
     */
    fun englishOrdinal(n: Int): String {
        require(n in 1..99) { "$n is outside 1 … 99" }
        EN_ORDINALS[n]?.let { return it }
        if (n < 20) return en(n) + "th"
        val ones = n % 10
        return if (ones == 0) en(n).dropLast(1) + "ieth" else "${EN_TENS[n / 10]}-${englishOrdinal(ones)}"
    }
}
