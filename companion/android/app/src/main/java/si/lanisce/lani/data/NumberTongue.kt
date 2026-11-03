package si.lanisce.lani.data

import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.inBase

/**
 * What "🔢 Števila · Numbers" ([NumbersPractice]) needs of a language: which unit's intro opens it, how its numbers, dates and
 * clock times are said and what else is right, what a learner gets wrong in them and why it is so. The module's ladder,
 * runs, schedule and screens are the same in every language. A new language is one of these plus its texts in the string
 * tables (numbers.it.*: each named whole here, as the string tables' test reads them); docs/plans/05-built/practice-it.md says
 * what German and English need.
 */
abstract class NumberTongue(val lang: Lang) {
    /** The course unit whose intro opens the module, with its first two ranges. */
    abstract val unit: String
    /** The unit that teaches the clock (its intro's "⏰ from now on" note). */
    abstract val timeUnit: String
    /** The unit that opens the dates (its intro introduces [datePage]). */
    abstract val dateUnit: String
    /** The grammar pages a date and a clock time need introduced. */
    abstract val datePage: String
    abstract val timePage: String
    /** The cheat sheet's pages a run opens it at. */
    abstract val pages: List<String>
    /** The rule's tip on the module's screen, in the string tables. */
    abstract val tipKey: String
    /** The instruction of a clock time to write: it names the language's words ("Write: ob …"), in the string tables. */
    abstract val timeWriteKey: String
    /** The months as a date says them, January first. */
    abstract val months: List<String>

    /** [n] as a bare number is counted, 0 … 9999 at least. */
    abstract fun words(n: Int): String

    /** The other words a bare [n] is right as: a gender's form, a written variant. Never another number's. */
    abstract fun also(n: Int): List<String>

    /** The wrong words of a learner who says [n] in the other order ("dvajset sedem", "sette venti"); empty when it has no two parts. */
    abstract fun swappedWords(n: Int): List<String>

    /** The day of a date as its gap wants it: petnajsti; quindici, primo. */
    abstract fun dateDay(day: Int): String

    /** A date's sentence with its gap: "Danes je ___ maj. (15.)", "Oggi è il ___ maggio. (15)". */
    abstract fun dateFrame(day: Int, month: Int): String

    /** A date said whole: "Danes je petnajsti maj.". */
    abstract fun dateSaid(day: Int, month: Int): String

    /** What a date's gap is right as, the plain answer first. */
    abstract fun dateAccept(day: Int, month: Int): List<String>

    /** A date as the tutor and the run's end show it: "15. maj", "15 maggio". */
    abstract fun dateShown(day: Int, month: Int): String

    /** A clock time h·100 + m (1 … 12, :00 or :30) as the answer to "at what time?": ob pol šestih; alle cinque e mezza. */
    abstract fun clockWords(t: Int): String

    /** What a clock time written is right as, [clockWords] first. */
    abstract fun clockAccept(t: Int): List<String>

    /** The hour a learner types for half past hour [h] when they count the half the other way (a [NumberKind.TIME_HALF]). */
    abstract fun halfHeard(h: Int): Int

    /** Half past written counted the other way (a [NumberKind.TIME_HALF]): the texts, for [t] at half past. */
    abstract fun halfWritten(t: Int): List<String>

    /** How [n]'s word is built, in the learner's base (Markdown). */
    abstract fun numberWhy(n: Int): String

    /** Why [wrong] (the other order) isn't [n], in the learner's base. */
    abstract fun orderWhy(n: Int, wrong: String): String

    /** [day] of a date, in the learner's base. */
    abstract fun dateWhy(day: Int, month: Int): String

    /** A clock time, in the learner's base. */
    abstract fun clockWhy(t: Int): String

    /** A wrong answer's kind for the tutor (mistakes-db), in English. */
    abstract fun describe(kind: NumberKind): String

    /** A slip that is a word of the learner's own making, wrong rather than almost (Slovene's "dvasto" for dvesto). */
    open fun ownWord(answer: String, shown: String): Boolean = false

    /** A date's day that only looks like a slip ("sedemi" for sedmi): wrong rather than almost. */
    open fun dateSlip(day: Int, answer: String): Boolean = false

    /** The instruction of a task asked by [mode]: a language's own where it names its words. */
    fun ask(mode: NumberMode): String = if (mode == NumberMode.TIME_WRITE) timeWriteKey else mode.ask

    /** Why the same word apart or together is almost, in the learner's base. */
    abstract fun spacingWhy(word: String): String

    /** What a bare word is matched as: folded, no spaces or hyphens ("sedem-in-dvajset", "dva tisoč"). */
    fun key(s: String): String = Grading.fold(s, lang).replace(Regex("[\\s\\-–]+"), "")

    /** Every number 0 … 9999 by its [key]ed word and its other forms. */
    private val byWord: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        for (n in 0..9999) {
            m[key(words(n))] = n
            for (w in also(n)) m.putIfAbsent(key(w), n)
        }
        m
    }

    /** The numbers said in the other order, by their [key]. None is a number's own word. */
    private val swapped: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        for (n in 21..9999) for (w in swappedWords(n)) m.putIfAbsent(key(w), n)
        m.keys.removeAll(byWord.keys)
        m
    }

    /** The number [text] is the word of, 0 … 9999; null when it is none. */
    fun parse(text: String): Int? = byWord[key(text)]

    /** The number [text] says in the other order ("dvajset sedem": 27), or null. */
    fun parseSwapped(text: String): Int? = swapped[key(text)]

    companion object {
        /** The tongue of language [code]; null: the module doesn't speak it yet (no tile, no numbers on the unit's page). */
        fun of(code: String?): NumberTongue? = ALL.firstOrNull { it.lang.code == code }

        val SL: NumberTongue get() = SloveneNumbers
        val IT: NumberTongue get() = ItalianNumbers

        /** The languages the module speaks. */
        val ALL: List<NumberTongue> get() = listOf(SloveneNumbers, ItalianNumbers)
    }
}

/** Slovene's numbers ([SpokenNumbers.slovene], [SpokenNumbers.ordinal], the hours after ob): the ones first, a date's ordinal, half towards the next hour. */
private object SloveneNumbers : NumberTongue(Lang.SL) {
    override val unit = Counting.UNIT
    override val timeUnit = "kdaj-cas"
    override val dateUnit = unit
    override val datePage = "datumi"
    override val timePage = "kdaj-cas"
    override val pages = listOf(Counting.PAGE, datePage)
    override val tipKey = "numbers.tip"
    override val timeWriteKey = "numbers.ask.timeWrite"

    /** All masculine: a date's ordinal is always -i ("peti maj"). */
    override val months = listOf("januar", "februar", "marec", "april", "maj", "junij", "julij", "avgust", "september", "oktober", "november", "december")

    /**
     * The hours after "ob" (the locative the unit kdaj-cas teaches), 1 to 12: ob enih, ob dveh, ob treh, ob štirih, ob
     * petih … ob sedmih, ob osmih (the e drops) … ob dvanajstih. Hand-checked against the unit's intro; machine-written.
     */
    private val hoursAt = listOf("enih", "dveh", "treh", "štirih", "petih", "šestih", "sedmih", "osmih", "devetih", "desetih", "enajstih", "dvanajstih")

    override fun words(n: Int) = SpokenNumbers.slovene(n)
    override fun also(n: Int) = SpokenNumbers.sloveneAlso(n)

    override fun swappedWords(n: Int): List<String> {
        val low = n % 100
        if (low < 21 || low % 10 == 0) return emptyList()
        val head = (n - low).takeIf { it > 0 }?.let { words(it) + " " }.orEmpty()
        val tens = words(low - low % 10)
        val ones = words(low % 10)
        return listOf("$head$tens $ones", "$head$tens in $ones")
    }

    override fun dateDay(day: Int) = SpokenNumbers.ordinal(day)
    override fun dateFrame(day: Int, month: Int) = "Danes je ___ ${months[month - 1]}. ($day.)"
    override fun dateSaid(day: Int, month: Int) = "Danes je ${dateDay(day)} ${months[month - 1]}."
    override fun dateAccept(day: Int, month: Int) = listOf(dateDay(day), "${dateDay(day)} ${months[month - 1]}", dateSaid(day, month))
    override fun dateShown(day: Int, month: Int) = "$day. ${months[month - 1]}"

    override fun clockWords(t: Int): String {
        val h = t / 100
        return if (t % 100 == 30) "ob pol ${hoursAt[h % 12]}" else "ob ${hoursAt[h - 1]}"
    }

    override fun clockAccept(t: Int) = listOf(clockWords(t), clockWords(t).removePrefix("ob "))
    override fun halfHeard(h: Int) = h + 1
    override fun halfWritten(t: Int) = listOf("pol ${hoursAt[t / 100 - 1]}", "ob pol ${hoursAt[t / 100 - 1]}")

    override fun numberWhy(n: Int): String {
        val w = words(n)
        val low = n % 100
        return when {
            n < 11 || n == 20 -> inBase("numbers.why.plain", "n" to n, "word" to w)
            n < 20 -> inBase("numbers.why.teen", "n" to n, "word" to w)
            n < 100 && low % 10 == 0 -> inBase("numbers.why.tens", "n" to n, "word" to w)
            n < 100 -> inBase("numbers.why.compound", "n" to n, "word" to w, "ones" to words(low % 10), "tens" to words(low - low % 10), "o" to low % 10, "t" to low - low % 10)
            else -> inBase("numbers.why.big", "n" to n, "word" to w)
        }
    }

    override fun orderWhy(n: Int, wrong: String): String {
        val low = n % 100
        return inBase("numbers.why.order", "n" to n, "word" to words(n), "ones" to words(low % 10), "tens" to words(low - low % 10), "o" to low % 10, "t" to low - low % 10, "wrong" to wrong)
    }

    override fun spacingWhy(word: String) = inBase("numbers.why.spacing", "word" to word)

    override fun dateWhy(day: Int, month: Int) = inBase("numbers.why.date", "day" to day, "ordinal" to dateDay(day), "month" to months[month - 1])

    override fun clockWhy(t: Int): String {
        val time = NumbersPractice.clock(t)
        return if (t % 100 == 30) inBase("numbers.why.half", "time" to time, "said" to clockWords(t), "next" to (t / 100 % 12 + 1))
        else inBase("numbers.why.hour", "time" to time, "said" to clockWords(t))
    }

    override fun describe(kind: NumberKind) = when (kind) {
        NumberKind.LISTENING -> "A Slovene number heard and typed in figures wrong"
        NumberKind.READING -> "A Slovene number word read and typed in figures wrong"
        NumberKind.SPELLING -> "A number's Slovene word written wrong"
        NumberKind.ORDER -> "The ones and the tens swapped: Slovene says the ones first, joined by in (sedemindvajset = 7 + 20)"
        NumberKind.SPEAKING -> "A number said wrong in Slovene"
        NumberKind.DATE -> "A date's day as an ordinal (Danes je petnajsti maj; 7. sedmi, 8. osmi)"
        NumberKind.TIME -> "A clock time with ob and the -ih form (ob petih, ob dveh)"
        NumberKind.TIME_HALF -> "Half past counts towards the next hour (ob pol šestih = 5:30)"
    }

    /** 200 is dvesto: "dvasto" (dva + sto) is the learner's word, not a slip. */
    override fun ownWord(answer: String, shown: String) = "dvasto" in key(answer) && "dvesto" in key(shown)

    /** A date's day written as its number with -i where the e drops ("sedemi" for sedmi, "osemi" for osmi). */
    override fun dateSlip(day: Int, answer: String) = day in listOf(7, 8) && key(answer).startsWith(key(words(day) + "i"))
}

/**
 * Italian's numbers ([SpokenNumbers.italian]): the tens first, all one word (ventisette, centoventi, duemilaventisei), a
 * date the plain number with il (il primo, l'otto, l'undici), the clock alle + the hour, half past the hour that is there
 * (alle cinque e mezza: a German "halb sechs" is the other way). The Italian course's unit numeri opens it, ore-giorni
 * teaches the clock and the dates. Machine-written, like Slovene's.
 */
private object ItalianNumbers : NumberTongue(Lang.IT) {
    override val unit = "numeri"
    override val timeUnit = "ore-giorni"
    override val dateUnit = "ore-giorni"
    override val datePage = "ore-giorni"
    override val timePage = "ore-giorni"
    override val pages = listOf("numeri", "ore-giorni")
    override val tipKey = "numbers.it.tip"
    override val timeWriteKey = "numbers.it.ask.timeWrite"
    override val months = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre")

    override fun words(n: Int) = SpokenNumbers.italian(n)
    override fun also(n: Int) = SpokenNumbers.italianAlso(n)

    override fun swappedWords(n: Int): List<String> {
        val low = n % 100
        if (low < 21 || low % 10 == 0) return emptyList()
        val head = (n - low).takeIf { it > 0 }?.let(::words).orEmpty()
        val tens = words(low - low % 10)
        val ones = words(low % 10)
        return listOf("$head$ones $tens", "$head$ones e $tens")
    }

    /** Il before a day, l' before otto and undici. */
    private fun article(day: Int) = if (day == 8 || day == 11) "l'" else "il "

    override fun dateDay(day: Int) = if (day == 1) "primo" else words(day)
    override fun dateFrame(day: Int, month: Int) = "Oggi è ${article(day)}___ ${months[month - 1]}. ($day)"
    override fun dateSaid(day: Int, month: Int) = "Oggi è ${article(day)}${dateDay(day)} ${months[month - 1]}."

    override fun dateAccept(day: Int, month: Int): List<String> {
        val d = dateDay(day)
        val m = months[month - 1]
        return listOf(d, "${article(day)}$d", "$d $m", "${article(day)}$d $m", dateSaid(day, month))
    }

    override fun dateShown(day: Int, month: Int) = "$day ${months[month - 1]}"

    /** "cinque", "le cinque": one is l'una. */
    private fun hour(h: Int) = words(h).let { if (h == 1) "una" else it }

    override fun clockWords(t: Int): String {
        val h = t / 100
        val at = if (h == 1) "all'una" else "alle ${hour(h)}"
        return if (t % 100 == 30) "$at e mezza" else at
    }

    override fun clockAccept(t: Int): List<String> {
        val h = t / 100
        val half = t % 100 == 30
        val heads = if (h == 1) listOf("all'una", "l'una", "una") else listOf("alle ${hour(h)}", "le ${hour(h)}", hour(h))
        val noon = if (h == 12) listOf("a mezzogiorno", "mezzogiorno", "a mezzanotte", "mezzanotte") else emptyList()
        val ends = if (half) listOf(" e mezza", " e mezzo", " e trenta") else listOf("", " in punto")
        return (heads + noon).flatMap { head -> ends.map { head + it } }
    }

    /** "e mezza" read as a German "halb": 4:30 typed for alle cinque e mezza. */
    override fun halfHeard(h: Int) = if (h == 1) 12 else h - 1

    /** 5:30 written alle sei e mezza: a German "halb sechs" said in Italian. */
    override fun halfWritten(t: Int) = clockAccept((t / 100 % 12 + 1) * 100 + 30)

    override fun numberWhy(n: Int): String {
        val w = words(n)
        val low = n % 100
        val ones = low % 10
        return when {
            n < 11 || n == 20 -> inBase("numbers.why.plain", "n" to n, "word" to w)
            n < 20 -> inBase("numbers.it.why.teen", "n" to n, "word" to w)
            n < 100 && ones == 0 -> inBase("numbers.it.why.tens", "n" to n, "word" to w)
            n < 100 && (ones == 1 || ones == 8) -> inBase("numbers.it.why.dropped", "n" to n, "word" to w, "ones" to words(ones), "tens" to words(low - ones))
            n < 100 && ones == 3 -> inBase("numbers.it.why.accent", "n" to n, "word" to w)
            n < 100 -> inBase("numbers.it.why.compound", "n" to n, "word" to w, "ones" to words(ones), "tens" to words(low - ones), "o" to ones, "t" to low - ones)
            else -> inBase("numbers.it.why.big", "n" to n, "word" to w)
        }
    }

    override fun orderWhy(n: Int, wrong: String): String {
        val low = n % 100
        return inBase("numbers.it.why.order", "word" to words(n), "ones" to words(low % 10), "tens" to words(low - low % 10), "o" to low % 10, "t" to low - low % 10, "wrong" to wrong)
    }

    override fun spacingWhy(word: String) = inBase("numbers.it.why.spacing", "word" to word)

    override fun dateWhy(day: Int, month: Int) = inBase("numbers.it.why.date", "day" to day, "said" to dateSaid(day, month).removePrefix("Oggi è ").removeSuffix("."))

    override fun clockWhy(t: Int): String {
        val time = NumbersPractice.clock(t)
        return if (t % 100 == 30) inBase("numbers.it.why.half", "time" to time, "said" to clockWords(t), "next" to (t / 100 % 12 + 1))
        else inBase("numbers.it.why.hour", "time" to time, "said" to clockWords(t))
    }

    override fun describe(kind: NumberKind) = when (kind) {
        NumberKind.LISTENING -> "An Italian number heard and typed in figures wrong"
        NumberKind.READING -> "An Italian number word read and typed in figures wrong"
        NumberKind.SPELLING -> "A number's Italian word written wrong"
        NumberKind.ORDER -> "The ones and the tens swapped: Italian says the tens first, in one word (ventisette = 20 + 7), German the ones first"
        NumberKind.SPEAKING -> "A number said wrong in Italian"
        NumberKind.DATE -> "A date's day: the plain number with il, only the first is primo (il quindici maggio, il primo maggio, l'otto maggio)"
        NumberKind.TIME -> "A clock time with alle (alle cinque, all'una)"
        NumberKind.TIME_HALF -> "Half past counts from the hour that is there (alle cinque e mezza = 5:30, German halb sechs)"
    }
}
