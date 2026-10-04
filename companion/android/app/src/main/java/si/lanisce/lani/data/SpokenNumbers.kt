package si.lanisce.lani.data

import si.lanisce.lani.l10n.Lang

/**
 * Numbers a speech recognizer wrote as digits, back in words, so what was heard can be graded against the text: Whisper
 * and the phone's recognizer write "dva" as "2", even inside a word ("midva" → "mi2", "dvakrat" → "2krat"). A digit
 * run stuck to letters is joined to them as a word ("mi2" → "midva"); a free one becomes its word(s) ("2 jabolki" →
 * "dve jabolki"). Where a number has several forms (Slovene dva/dve, ena/en/eno; German eins/ein/eine; Italian uno/una),
 * the one the [expected] text uses is taken, else the counting form. 0 … 999; longer runs stay as they are.
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
        else -> {
            val h = n / 100
            val head = when (h) { 1 -> "sto"; 2 -> "dvesto"; else -> SL_ONES[h] + "sto" }
            if (n % 100 == 0) head else "$head ${slWord(n % 100)}"
        }
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
        else -> {
            val h = n / 100
            val head = if (h == 1) "cento" else IT_ONES[h] + "cento"
            val rest = it(n % 100)
            // centotto, centottanta: cento's o goes before another
            if (n % 100 == 0) head else if (rest.startsWith("o")) head.dropLast(1) + rest else head + rest
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
}
