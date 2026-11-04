package si.lanisce.lani.data

import si.lanisce.lani.l10n.inBase

/**
 * British English's numbers ([SpokenNumbers.english], [SpokenNumbers.englishOrdinal]): the tens first with a hyphen
 * (twenty-seven: German and Slovene say the ones first), "and" after the hundreds (one hundred and five; the American
 * without it is right too), a year also in pairs (twenty twenty-six); a date the ordinal ("Today is the fifteenth of May":
 * first, second, third, fifth, eighth, ninth, twelfth their own); the clock "at" and the hour, half past from the hour that
 * is there (at half past five, in speech half five: a German "halb sechs" and a Slovene "pol šestih" are the other way).
 * The English course's unit numbers opens it, time teaches the clock and the dates (page time-dates). Machine-written, like
 * the others; docs/plans/05-built/practice-en.md.
 */
internal object EnglishNumbers : NumberTongue(si.lanisce.lani.l10n.Lang.EN) {
    override val unit = "numbers"
    override val timeUnit = "time"
    override val dateUnit = "time"
    override val datePage = "time-dates"
    override val timePage = "time-dates"
    override val pages = listOf("numbers", "time-dates")
    override val tipKey = "numbers.en.tip"
    override val timeWriteKey = "numbers.en.ask.timeWrite"
    override val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    override fun words(n: Int) = SpokenNumbers.english(n)
    override fun also(n: Int) = SpokenNumbers.englishAlso(n)

    /** 27 said as German and Slovene count it: "seven twenty", "seven and twenty" (as in an old rhyme: four and twenty). */
    override fun swappedWords(n: Int): List<String> {
        val low = n % 100
        if (low < 21 || low % 10 == 0) return emptyList()
        val head = (n - low).takeIf { it > 0 }?.let { words(it) + " and " }.orEmpty()
        val tens = words(low - low % 10)
        val ones = words(low % 10)
        return listOf("$head$ones $tens", "$head$ones and $tens")
    }

    /** 1st, 2nd, 3rd, 4th … 11th, 12th, 13th … 21st, 22nd, 23rd … 31st: how a date's day is written in figures. */
    private fun suffix(day: Int): String = when {
        day % 100 in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }

    override fun dateDay(day: Int) = SpokenNumbers.englishOrdinal(day)
    override fun dateFrame(day: Int, month: Int) = "Today is the ___ of ${months[month - 1]}. ($day${suffix(day)})"
    override fun dateSaid(day: Int, month: Int) = "Today is the ${dateDay(day)} of ${months[month - 1]}."

    /** The ordinal, with "the", with its month either way round ("the fifteenth of May", "May the fifteenth", American "May fifteenth"). */
    override fun dateAccept(day: Int, month: Int): List<String> {
        val d = dateDay(day)
        val m = months[month - 1]
        return listOf(d, "the $d", "$d of $m", "the $d of $m", dateSaid(day, month), "It's the $d of $m.", "$m the $d", "$m $d")
    }

    override fun dateShown(day: Int, month: Int) = "$day${suffix(day)} ${months[month - 1]}"

    /** The hour as the clock says it: one … twelve. */
    private fun hour(h: Int) = words(h)

    override fun clockWords(t: Int): String {
        val h = t / 100
        return if (t % 100 == 30) "at half past ${hour(h)}" else "at ${hour(h)}"
    }

    override fun clockAccept(t: Int): List<String> {
        val h = hour(t / 100)
        val said = if (t % 100 == 30) listOf("half past $h", "half $h", "$h thirty", "$h-thirty") else listOf(h, "$h o'clock")
        val noon = if (t == 1200) listOf("noon", "midday", "midnight", "twelve noon") else emptyList()
        return listOf(clockWords(t)) + (said + noon).flatMap { listOf("at $it", it) }.filter { it != clockWords(t) }
    }

    /** "half past five" heard as a German "halb fünf" or a Slovene "pol petih": 4:30 typed for 5:30. */
    override fun halfHeard(h: Int) = if (h == 1) 12 else h - 1

    /** 5:30 written "half past six" or "half six": a German "halb sechs", a Slovene "pol šestih" said in English. */
    override fun halfWritten(t: Int) = clockAccept((t / 100 % 12 + 1) * 100 + 30).filter { "half" in it }

    override fun numberWhy(n: Int): String {
        val w = words(n)
        val low = n % 100
        val ones = low % 10
        return when {
            n < 11 -> inBase("numbers.why.plain", "n" to n, "word" to w)
            n == 14 || n in 40..49 -> inBase("numbers.en.why.forty", "n" to n, "word" to w)
            n < 13 -> inBase("numbers.en.why.eleven", "n" to n, "word" to w)
            n < 20 -> inBase("numbers.en.why.teen", "n" to n, "word" to w)
            n < 100 && ones == 0 -> inBase("numbers.en.why.tens", "n" to n, "word" to w)
            n < 100 -> inBase("numbers.en.why.compound", "n" to n, "word" to w, "ones" to words(ones), "tens" to words(low - ones), "o" to ones, "t" to low - ones)
            n < 1000 -> inBase("numbers.en.why.hundred", "n" to n, "word" to w)
            else -> inBase("numbers.en.why.big", "n" to n, "word" to w)
        }
    }

    override fun orderWhy(n: Int, wrong: String): String {
        val low = n % 100
        return inBase("numbers.en.why.order", "word" to words(n), "ones" to words(low % 10), "tens" to words(low - low % 10), "o" to low % 10, "t" to low - low % 10, "wrong" to wrong)
    }

    override fun spacingWhy(word: String) = inBase("numbers.en.why.spacing", "word" to word)

    override fun dateWhy(day: Int, month: Int) = inBase("numbers.en.why.date", "day" to "$day${suffix(day)}", "said" to "the ${dateDay(day)} of ${months[month - 1]}")

    override fun clockWhy(t: Int): String {
        val time = NumbersPractice.clock(t)
        val h = t / 100
        return if (t % 100 == 30) inBase("numbers.en.why.half", "time" to time, "said" to clockWords(t), "next" to (h % 12 + 1), "short" to "half ${hour(h)}")
        else inBase("numbers.en.why.hour", "time" to time, "said" to clockWords(t))
    }

    override fun describe(kind: NumberKind) = when (kind) {
        NumberKind.LISTENING -> "An English number heard and typed in figures wrong"
        NumberKind.READING -> "An English number word read and typed in figures wrong"
        NumberKind.SPELLING -> "A number's English word written wrong"
        NumberKind.ORDER -> "The ones and the tens swapped: English says the tens first, with a hyphen (twenty-seven = 20 + 7), German and Slovene the ones first"
        NumberKind.SPEAKING -> "A number said wrong in English"
        NumberKind.DATE -> "A date's day as an ordinal (the fifteenth of May; first, second, third, fifth, eighth, ninth, twelfth, twentieth)"
        NumberKind.TIME -> "A clock time with at (at five, at half past five)"
        NumberKind.TIME_HALF -> "Half past counts from the hour that is there (half past five, half five = 5:30; German halb sechs, Slovene pol šestih)"
    }

    /** Misspellings the grading table holds as wrong (grading/en.json "wrong"): forty without u, ninety and nineteen with their e, fourteen with its u. */
    private val misspelt = listOf("fourty" to "forty", "ninty" to "ninety", "ninteen" to "nineteen", "forteen" to "fourteen")

    override fun ownWord(answer: String, shown: String) = misspelt.any { (bad, good) -> bad in key(answer) && good in key(shown) }

    /**
     * A date's day made regular where it isn't ("fiveth", "nineth", "twelveth", "twentyth", "twenty-oneth"): wrong rather
     * than almost, it's the irregular ordinal the gap asks for.
     */
    override fun dateSlip(day: Int, answer: String): Boolean {
        val regular = if (day % 10 == 0 && day >= 20) words(day) + "th" else (day - day % 10).takeIf { it >= 20 }.let { tens ->
            (tens?.let { words(it) + "-" }.orEmpty()) + words(if (tens != null) day % 10 else day) + "th"
        }
        return key(regular) != key(dateDay(day)) && key(regular) in key(answer)
    }
}
