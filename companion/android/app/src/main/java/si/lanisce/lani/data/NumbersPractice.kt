package si.lanisce.lani.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.School
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.inBase
import java.time.LocalDate
import kotlin.random.Random

/**
 * How a numbers task asks ("🔢 Števila · Numbers", [NumbersPractice]): a number heard, typed on the number pad ([HEAR]);
 * its word read, typed in figures ([READ]); its figures, the word written ([WRITE]); its figures, the number said
 * ([SAY]); a date's day, its ordinal written ([DATE]: "Danes je ___ maj. (15.)" → petnajsti); a clock time heard, typed
 * on the pad ([TIME_HEAR]: "ob pol šestih" → 5:30) or written ([TIME_WRITE]: 5:30 → ob pol šestih). [pad]: answered on
 * the number pad; [title] and [ask]: its name and its instruction (the string tables' keys).
 */
enum class NumberMode(val wire: String, val pad: Boolean, val emoji: String, val title: String, val ask: String) {
    HEAR("hear", true, "🎧", "numbers.mode.hear", "numbers.ask.hear"),
    READ("read", true, "👀", "numbers.mode.read", "numbers.ask.read"),
    WRITE("write", false, "✍️", "numbers.mode.write", "numbers.ask.write"),
    SAY("say", false, "🎤", "numbers.mode.say", "numbers.ask.say"),
    DATE("date", false, "📅", "numbers.mode.date", "numbers.ask.date"),
    TIME_HEAR("time_hear", true, "⏰", "numbers.mode.timeHear", "numbers.ask.timeHear"),
    TIME_WRITE("time_write", false, "🕰️", "numbers.mode.timeWrite", "numbers.ask.timeWrite");

    /** A task of the numbers' ladder ([NumberRange]): its answers count on a range. Dates and clock times don't. */
    val ranged: Boolean get() = this == HEAR || this == READ || this == WRITE || this == SAY

    companion object {
        fun of(wire: String): NumberMode? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * The numbers' ladder, in the order they open (i+1): 0–10 and 11–20 with the intro of the unit stevila, then the tens
 * (30, 40 … 90), the "-in-" numbers (21–99: sedem-in-dvajset), the hundreds, the thousands and the years. The next one
 * opens when the newest open one is mostly right ([NumbersPractice.PASS] of its last [NumbersPractice.WINDOW] answers).
 */
enum class NumberRange(val id: String, val label: String) {
    TO_TEN("0-10", "0–10"),
    TEENS("11-20", "11–20"),
    TENS("tens", "30–90"),
    TO_99("21-99", "21–99"),
    HUNDREDS("100-999", "100–999"),
    THOUSANDS("1000-9999", "1000–9999");

    /** Whether [n] is one of this range's numbers. */
    fun has(n: Int): Boolean = when (this) {
        TO_TEN -> n in 0..10
        TEENS -> n in 11..20
        TENS -> n in 30..90 && n % 10 == 0
        TO_99 -> n in 21..99 && n % 10 != 0
        HUNDREDS -> n in 100..999
        THOUSANDS -> n in 1000..9999
    }

    /** One of its numbers: the hundreds' round ones now and then (dvesto), the thousands' years often (2026). */
    fun draw(random: Random): Int = when (this) {
        TO_TEN -> random.nextInt(0, 11)
        TEENS -> random.nextInt(11, 21)
        TENS -> random.nextInt(3, 10) * 10
        TO_99 -> generateSequence { random.nextInt(21, 100) }.first { it % 10 != 0 }
        HUNDREDS -> if (random.nextInt(10) < 3) random.nextInt(1, 10) * 100 else random.nextInt(101, 1000)
        THOUSANDS -> when (random.nextInt(20)) {
            in 0..9 -> random.nextInt(1950, 2036) // a year
            in 10..12 -> random.nextInt(1, 10) * 1000
            else -> random.nextInt(1001, 10000)
        }
    }

    val next: NumberRange? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(n: Int): NumberRange? = entries.firstOrNull { it.has(n) } ?: if (n == 20) TEENS else null

        fun byId(id: String): NumberRange? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What a wrong answer was wrong about, as a mistake of its own kind for the tutor (mistakes-db.json's pattern ids, by the
 * session_end's `data.numbers.errors`): a number heard and typed wrong, its word read wrong, written wrong, said wrong;
 * the ones and the tens swapped ([ORDER]: "dvajset sedem" or "dvajsetsedem" for sedemindvajset, 72 heard as 27); a date's
 * ordinal ([DATE]: on the grammar book's datumi by its tag "ordinal"); a clock time ([TIME], [TIME_HALF]: half past
 * counted from the hour before, 6:30 for "pol šestih"; on kdaj-cas by "clock_time"). The plain numbers' ids name no page.
 */
enum class NumberKind(val id: String, val category: String, val description: String) {
    LISTENING("numbers_listening", "vocabulary", "A Slovene number heard and typed in figures wrong"),
    READING("numbers_reading", "vocabulary", "A Slovene number word read and typed in figures wrong"),
    SPELLING("numbers_spelling", "spelling", "A number's Slovene word written wrong"),
    ORDER("numbers_order", "syntax", "The ones and the tens swapped: Slovene says the ones first, joined by in (sedemindvajset = 7 + 20)"),
    SPEAKING("numbers_speaking", "pronunciation", "A number said wrong in Slovene"),
    DATE("numbers_ordinal_date", "grammar", "A date's day as an ordinal (Danes je petnajsti maj; 7. sedmi, 8. osmi)"),
    TIME("numbers_clock_time", "grammar", "A clock time with ob and the -ih form (ob petih, ob dveh)"),
    TIME_HALF("numbers_clock_time_half", "grammar", "Half past counts towards the next hour (ob pol šestih = 5:30)"),
}

/**
 * The learner's way through the numbers (the village state's `numbers`, [NumbersPractice]): the day each range opened by
 * its run (0–10 and 11–20 open with the unit's intro, unwritten), each range's last answers ([last]: "1" right or almost,
 * "0" wrong, the newest last, at most [NumbersPractice.WINDOW]), the numbers missed that come back ([again]), and the runs
 * played. An older app leaves it out; the bridge keeps it then (PUT /game), as it keeps the course.
 */
@Serializable
data class NumbersProgress(
    val v: Int = 1,
    val opened: Map<String, String> = emptyMap(),
    val last: Map<String, String> = emptyMap(),
    val again: List<NumberAgain> = emptyList(),
    val runs: Int = 0,
    val day: String = "",
)

/**
 * A number missed, to come back ([NumbersPractice.record]): [n] asked by [mode] (a date's day with its [month], a clock
 * time as h·100 + m), due on [due] (ISO), its [box] in the module's own schedule ([NumbersPractice.INTERVALS]: 0 today,
 * 1 a day later, 2 three days, 3 a week; right at the last box, it is gone).
 */
@Serializable
data class NumberAgain(val n: Int, val mode: String, val due: String, val box: Int = 0, val month: Int? = null)

/**
 * One task of a run: [n] asked by [mode] (a date's [month]), [again] when it is a number missed coming back; [kept]: the way
 * it was missed when it comes back another way (heard, read now where there is no voice), so it is kept as it was missed.
 */
data class NumberTask(val mode: NumberMode, val n: Int, val month: Int? = null, val again: Boolean = false, val kept: String? = null) {
    /** The range its answer counts on ([NumberMode.ranged] only). */
    val range: NumberRange? get() = if (mode.ranged) NumberRange.of(n) else null
}

/** A number answer graded: its verdict, the right answer shown, the grading hint and the letters to mark, why, its kind. */
data class NumberGraded(
    val verdict: Verdict,
    val expected: String,
    val hint: Grading.Hint = Grading.Hint.NONE,
    val marks: List<IntRange> = emptyList(),
    val why: String? = null,
    val kind: NumberKind? = null,
)

/** A run recorded: the progress after it and the range it opened (null: none). */
data class NumbersRecorded(val progress: NumbersProgress, val opened: NumberRange?)

/**
 * "🔢 Števila · Numbers" (companion/README.md, "Numbers"): a practice module of the numbers, opened by the intro of the A1
 * unit stevila (as the counting cards are), its modes interleaved in a run of [RUN] ([plan]), its ranges growing as the
 * learner gets them right ([NumberRange]), the numbers missed coming back in its own schedule ([record]), every wrong
 * answer a mistake of its kind for the tutor ([NumberKind], [errors]). The words are rule-built ([SpokenNumbers.slovene],
 * [SpokenNumbers.ordinal], the hours' [HOURS_AT]): machine-written, flagged so. Pure.
 */
object NumbersPractice {
    const val LANGUAGE = "sl"
    /** The course unit whose intro opens the module (and its first two ranges). */
    const val UNIT = Counting.UNIT
    /** The grammar pages a date and a clock time need introduced: the unit stevila's datumi, the unit kdaj-cas's. */
    const val DATE_PAGE = "datumi"
    const val TIME_PAGE = "kdaj-cas"
    /** The unit that teaches the clock (its intro introduces [TIME_PAGE]). */
    const val TIME_UNIT = "kdaj-cas"
    /** The cheat sheet's pages a numbers run opens it at (the curated card "🔢 Števila" is on them). */
    val PAGES = listOf(Counting.PAGE, DATE_PAGE)

    /** Tasks in a run. */
    const val RUN = 10
    /** A range's last answers kept, and how many of them right open the next range. */
    const val WINDOW = 10
    const val PASS = 8
    /** Numbers missed coming back in one run at most. */
    const val MAX_AGAIN = 4
    /** The share of a run's new numbers from the newest range open (the rest from the ranges before). */
    const val NEWEST_SHARE = 0.6
    /** The days after which a number missed comes back, by its box (0: the next run). */
    val INTERVALS = listOf(0, 1, 3, 7)
    /** The numbers missed kept at most. */
    const val MAX_KEPT = 40
    /** The ranges the unit's intro opens. */
    val FIRST = listOf(NumberRange.TO_TEN, NumberRange.TEENS)

    /** The months, all masculine: a date's ordinal is always -i ("peti maj"). */
    val MONTHS = listOf("januar", "februar", "marec", "april", "maj", "junij", "julij", "avgust", "september", "oktober", "november", "december")

    /**
     * The hours after "ob" (the locative the unit kdaj-cas teaches), 1 to 12: ob enih, ob dveh, ob treh, ob štirih, ob
     * petih … ob sedmih, ob osmih (the e drops) … ob dvanajstih. Hand-checked against the unit's intro; machine-written.
     */
    val HOURS_AT = listOf("enih", "dveh", "treh", "štirih", "petih", "šestih", "sedmih", "osmih", "devetih", "desetih", "enajstih", "dvanajstih")

    // --- when -----------------------------------------------------------------------------------------------------------

    /** The module is open: the intro of the unit stevila was done (finished, or skipped as known), as for counting. */
    fun open(s: GameState?): Boolean = Counting.active(s)

    /** Why it's closed: the class hasn't begun (the hamlet begins it), or the unit's intro waits. Null when open. */
    enum class Lock { CLASS, INTRO }

    fun lock(s: GameState?): Lock? = when {
        open(s) -> null
        s == null || !School.open(s) -> Lock.CLASS
        else -> Lock.INTRO
    }

    /** The ranges open, in the ladder's order: the intro's two, then each opened by a run. */
    fun ranges(p: NumbersProgress?, introDone: Boolean): List<NumberRange> {
        if (!introDone) return emptyList()
        return NumberRange.entries.filter { it in FIRST || p?.opened?.containsKey(it.id) == true }
    }

    /** The newest range open: the one whose answers open the next. */
    fun newest(p: NumbersProgress?, introDone: Boolean): NumberRange? = ranges(p, introDone).lastOrNull()

    /** [r]'s last answers right (or almost), of at most [WINDOW]. */
    fun score(p: NumbersProgress?, r: NumberRange): Pair<Int, Int> {
        val l = p?.last?.get(r.id).orEmpty()
        return l.count { it == '1' } to l.length
    }

    /** Mostly right: [PASS] of its last [WINDOW] answers. */
    fun passedWindow(p: NumbersProgress?, r: NumberRange): Boolean = score(p, r).let { (right, of) -> of >= WINDOW && right >= PASS }

    /** The ranges the learner has: the next one is open after them, or (the last) its own answers are mostly right. */
    fun passed(p: NumbersProgress?, introDone: Boolean): List<NumberRange> {
        val open = ranges(p, introDone)
        return open.filter { r -> r.next?.let { it in open } ?: passedWindow(p, r) }
    }

    /**
     * The ways of asking on now: reading and writing always; hearing with a voice ([canSay]); saying with something to
     * listen ([canListen]; only the ranges the learner has: [passed]); a date's ordinal once the page datumi is introduced
     * (the unit stevila's intro does it), a clock time once kdaj-cas is (the unit Kdaj?'s intro), heard with a voice.
     */
    fun modes(canSay: Boolean, canListen: Boolean, introduced: Set<String>): Set<NumberMode> = buildSet {
        add(NumberMode.READ)
        add(NumberMode.WRITE)
        if (canSay) add(NumberMode.HEAR)
        if (canListen) add(NumberMode.SAY)
        if (DATE_PAGE in introduced) add(NumberMode.DATE)
        if (TIME_PAGE in introduced) {
            add(NumberMode.TIME_WRITE)
            if (canSay) add(NumberMode.TIME_HEAR)
        }
    }

    /** The numbers missed due on [today] or before. */
    fun due(p: NumbersProgress?, today: LocalDate): List<NumberAgain> =
        p?.again.orEmpty().filter { runCatching { LocalDate.parse(it.due) }.getOrNull()?.let { d -> !d.isAfter(today) } ?: true }.sortedBy { it.due }

    // --- a run ----------------------------------------------------------------------------------------------------------

    private val WEIGHTS = mapOf(
        NumberMode.HEAR to 3, NumberMode.READ to 2, NumberMode.WRITE to 3, NumberMode.SAY to 2,
        NumberMode.DATE to 1, NumberMode.TIME_HEAR to 1, NumberMode.TIME_WRITE to 1,
    )

    /** [mode] when it's on, else the one that asks the same the other way (heard → read, said → written); null: none. */
    private fun available(mode: NumberMode, modes: Set<NumberMode>): NumberMode? = when {
        mode in modes -> mode
        mode == NumberMode.HEAR -> NumberMode.READ.takeIf { it in modes }
        mode == NumberMode.SAY -> NumberMode.WRITE.takeIf { it in modes }
        mode == NumberMode.TIME_HEAR -> NumberMode.TIME_WRITE.takeIf { it in modes }
        else -> null
    }

    /**
     * A run's tasks, [size] at most: the numbers missed that are due first (at most [MAX_AGAIN], spread through the run),
     * then new ones, [NEWEST_SHARE] of them from the newest range open and the rest from those before; the ways of asking
     * on ([modes]) mixed by their weights, never the same twice in a row while another is on; no number twice. A number
     * to say only from the ranges the learner has ([passed]); a date's day and a clock's hour only from the ranges open.
     * [only]: QA's (app/QaHooks "numbers:only:<mode>"), every task that way when it is on. Empty while the module is closed.
     */
    fun plan(
        p: NumbersProgress?, introDone: Boolean, modes: Set<NumberMode>, today: LocalDate, random: Random,
        size: Int = RUN, only: NumberMode? = null,
    ): List<NumberTask> {
        val open = ranges(p, introDone)
        if (open.isEmpty()) return emptyList()
        val on = if (only != null && only in modes) setOf(only) else modes
        val has = passed(p, introDone)
        val used = mutableSetOf<String>()
        fun key(mode: NumberMode, n: Int, month: Int?) = when (mode) {
            NumberMode.DATE -> "date:$n.$month"
            NumberMode.TIME_HEAR, NumberMode.TIME_WRITE -> "time:$n"
            else -> "n:$n"
        }
        val again = due(p, today).mapNotNull { a ->
            val m = NumberMode.of(a.mode)?.let { available(it, on) } ?: return@mapNotNull null
            if (m == NumberMode.SAY && NumberRange.of(a.n) !in has) return@mapNotNull null
            if (m.ranged && NumberRange.of(a.n) !in open) return@mapNotNull null
            if (!used.add(key(m, a.n, a.month))) return@mapNotNull null
            NumberTask(m, a.n, a.month, again = true, kept = a.mode.takeIf { it != m.wire })
        }.take(MAX_AGAIN.coerceAtMost(size))

        val fresh = mutableListOf<NumberTask>()
        var last: NumberMode? = null
        var tries = 0
        val pool = on.filter { it != NumberMode.SAY || has.isNotEmpty() }
        while (pool.isNotEmpty() && fresh.size < size - again.size && tries++ < size * 20) {
            val choices = pool.filter { it != last }.ifEmpty { pool }
            val mode = weighted(choices, random)
            val task = when (mode) {
                NumberMode.DATE -> {
                    val days = (1..31).filter { d -> NumberRange.of(d)?.let { it in open } == true }
                    val month = random.nextInt(1, 13)
                    NumberTask(mode, days[random.nextInt(days.size)], month)
                }
                NumberMode.TIME_HEAR, NumberMode.TIME_WRITE -> NumberTask(mode, random.nextInt(1, 13) * 100 + if (random.nextBoolean()) 30 else 0)
                NumberMode.SAY -> NumberTask(mode, has[random.nextInt(has.size)].draw(random))
                else -> {
                    val r = if (open.size == 1 || random.nextDouble() < NEWEST_SHARE) open.last() else open[random.nextInt(open.size - 1)]
                    NumberTask(mode, r.draw(random))
                }
            }
            if (!used.add(key(task.mode, task.n, task.month))) continue
            fresh += task
            last = mode
        }
        return spread(again, fresh)
    }

    private fun weighted(modes: List<NumberMode>, random: Random): NumberMode {
        val total = modes.sumOf { WEIGHTS[it] ?: 1 }
        var x = random.nextInt(total)
        for (m in modes) {
            x -= WEIGHTS[m] ?: 1
            if (x < 0) return m
        }
        return modes.last()
    }

    /** [again] spread evenly through [fresh]: the first at the start, the others between. */
    private fun spread(again: List<NumberTask>, fresh: List<NumberTask>): List<NumberTask> {
        if (again.isEmpty()) return fresh
        val total = again.size + fresh.size
        val at = again.indices.map { i -> i * total / again.size }.toSet()
        val a = again.iterator()
        val f = fresh.iterator()
        return (0 until total).map { i -> if (i in at && a.hasNext()) a.next() else if (f.hasNext()) f.next() else a.next() }
    }

    // --- the words ------------------------------------------------------------------------------------------------------

    /** [n] in Slovene words, as a bare number is counted ([SpokenNumbers.slovene]). */
    fun words(n: Int): String = SpokenNumbers.slovene(n)

    /** "5:30" of a clock time h·100 + m. */
    fun clock(t: Int): String = "${t / 100}:${(t % 100).toString().padStart(2, '0')}"

    /** A clock time h·100 + m (1 … 12, :00 or :30) after "ob": ob petih, ob pol šestih (half towards the next hour). */
    fun clockWords(t: Int): String {
        val h = t / 100
        return if (t % 100 == 30) "ob pol ${HOURS_AT[h % 12]}" else "ob ${HOURS_AT[h - 1]}"
    }

    /** A date's sentence with its gap: "Danes je ___ maj. (15.)". */
    fun dateFrame(day: Int, month: Int): String = "Danes je ___ ${MONTHS[month - 1]}. ($day.)"

    /** A date said whole: "Danes je petnajsti maj." */
    fun dateSaid(day: Int, month: Int): String = "Danes je ${SpokenNumbers.ordinal(day)} ${MONTHS[month - 1]}."

    /** What a bare word is matched as: folded (č, š, ž or not), no spaces or hyphens ("sedem-in-dvajset", "dva tisoč"). */
    fun key(s: String): String = Grading.fold(s, Lang.SL).replace(Regex("[\\s\\-–]+"), "")

    /** Every number 0 … 9999 by its [key]ed word (and the gender forms of 1 to 4: en, eno, dve, trije …). */
    private val byWord: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        for (n in 0..9999) {
            m[key(words(n))] = n
            for (w in SpokenNumbers.sloveneAlso(n)) m.putIfAbsent(key(w), n)
        }
        m
    }

    /**
     * The numbers 21 … 9999 whose last two figures are an "-in-" number, as a learner says them with the tens first, by
     * their [key]: "dvajset sedem", "dvajsetsedem", "dvajset in sedem" (27), "sto dvajset sedem" (127). None is a number's
     * own word.
     */
    private val tensFirst: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        for (n in 21..9999) {
            val low = n % 100
            if (low < 21 || low % 10 == 0) continue
            val head = (n - low).takeIf { it > 0 }?.let { words(it) + " " }.orEmpty()
            val tens = words(low - low % 10)
            val ones = words(low % 10)
            for (w in listOf("$head$tens $ones", "$head$tens in $ones")) m.putIfAbsent(key(w), n)
        }
        m.keys.removeAll(byWord.keys)
        m
    }

    /** The number [text] is the word of, 0 … 9999 (č, š, ž or not, spaces as they come); null when it is none. */
    fun parse(text: String): Int? = byWord[key(text)]

    /** The number [text] says with its tens first ("dvajset sedem": 27), or null. */
    fun parseTensFirst(text: String): Int? = tensFirst[key(text)]

    /** The figures typed on the pad as a number: "027" is 27; null when it isn't one. */
    fun digits(answer: String): Int? = answer.filter { !it.isWhitespace() }.takeIf { it.isNotEmpty() && it.all(Char::isDigit) && it.length <= 7 }?.toInt()

    /** A clock time typed on the pad ("5:30", "17:30", "530") as (hour, minute); null when it isn't one. */
    fun time(answer: String): Pair<Int, Int>? {
        val a = answer.filter { !it.isWhitespace() }
        val (h, m) = when {
            ':' in a -> a.substringBefore(':') to a.substringAfter(':')
            a.length in 3..4 && a.all(Char::isDigit) -> a.dropLast(2) to a.takeLast(2)
            a.isNotEmpty() && a.length <= 2 && a.all(Char::isDigit) -> a to "00"
            else -> return null
        }
        val hh = h.toIntOrNull()?.takeIf { it in 0..24 } ?: return null
        val mm = m.toIntOrNull()?.takeIf { it in 0..59 && m.length == 2 } ?: return null
        return hh to mm
    }

    /** [n] with its last two figures swapped, when they are two different ones and neither 0 (27 → 72; 127 → 172); else null. */
    fun swapped(n: Int): Int? {
        val low = n % 100
        val t = low / 10
        val o = low % 10
        if (low < 10 || t == 0 || o == 0 || t == o) return null
        return n - low + o * 10 + t
    }

    // --- the tasks as exercises ------------------------------------------------------------------------------------------

    /**
     * [t] as the run's exercise: on the pad ([Exercise.Numeral], figures to type), written (its word or form typed, the
     * app's č, š, ž chips), or said ([Exercise.Speak]: the figures shown, the word hidden until the answer; a number from
     * 1000 on also right as its figures, which the recognizer writes there). Its why ([whyOf]) is in the learner's base.
     */
    fun exercise(t: NumberTask): Exercise {
        val instruction = si.lanisce.lani.l10n.bi(t.mode.ask)
        return when (t.mode) {
            NumberMode.HEAR -> Exercise.Numeral(t.mode.wire, t.n, audio = words(t.n), accept = listOf("${t.n}"), shown = "${t.n}", word = words(t.n), instruction = instruction)
            NumberMode.READ -> Exercise.Numeral(t.mode.wire, t.n, prompt = words(t.n), accept = listOf("${t.n}"), shown = "${t.n}", word = words(t.n), instruction = instruction)
            NumberMode.WRITE -> Exercise.Numeral(
                t.mode.wire, t.n, prompt = "${t.n}", accept = listOf(words(t.n)) + SpokenNumbers.sloveneAlso(t.n), shown = words(t.n), word = words(t.n), instruction = instruction,
            )
            NumberMode.SAY -> Exercise.Speak(
                say = words(t.n), prompt = "${t.n}", show = false, instruction = instruction,
                accept = SpokenNumbers.sloveneAlso(t.n) + if (t.n >= 1000) listOf("${t.n}") else emptyList(),
                explain = whyOf(t),
            )
            NumberMode.DATE -> {
                val month = t.month ?: 5
                val ord = SpokenNumbers.ordinal(t.n)
                Exercise.Numeral(
                    t.mode.wire, t.n, prompt = dateFrame(t.n, month), month = month,
                    accept = listOf(ord, "$ord ${MONTHS[month - 1]}", dateSaid(t.n, month)), shown = ord, word = dateSaid(t.n, month),
                    instruction = instruction, grammar = DATE_PAGE,
                )
            }
            NumberMode.TIME_HEAR -> Exercise.Numeral(
                t.mode.wire, t.n, audio = clockWords(t.n), accept = timeFigures(t.n), shown = clock(t.n), word = clockWords(t.n),
                colon = true, instruction = instruction, grammar = TIME_PAGE,
            )
            NumberMode.TIME_WRITE -> Exercise.Numeral(
                t.mode.wire, t.n, prompt = "⏰ ${clock(t.n)}", accept = listOf(clockWords(t.n), clockWords(t.n).removePrefix("ob ")),
                shown = clockWords(t.n), word = clockWords(t.n), instruction = instruction, grammar = TIME_PAGE,
            )
        }
    }

    /** A clock time's figures as the pad may type them: 5:30, 05:30 and 17:30 (12:30 as 0:30 too). */
    private fun timeFigures(t: Int): List<String> {
        val h = t / 100
        val m = (t % 100).toString().padStart(2, '0')
        return listOf("$h:$m", "${h.toString().padStart(2, '0')}:$m", "${(h + 12) % 24}:$m").distinct()
    }

    // --- grading --------------------------------------------------------------------------------------------------------

    /**
     * [answer] to [ex]: figures on the pad are right or wrong (a clock time by its hour on either half of the day); a word
     * as the app grades a typed answer ([Grading.check]: č, š, ž forgotten is almost, a slip in the stem too) with what a
     * number's word adds: the same letters apart or together ("sedem in dvajset", "dvatisoč") is almost; another number's
     * word ("deset" for devet) is wrong, never a slip; the tens first ("dvajset sedem") wrong, of its own kind. Each wrong
     * one has its [NumberKind].
     */
    fun grade(ex: Exercise.Numeral, answer: String): NumberGraded {
        val mode = NumberMode.of(ex.mode) ?: NumberMode.READ
        val t = NumberTask(mode, ex.n, ex.month)
        return if (mode.pad) gradePad(t, ex, answer) else gradeWord(t, ex, answer)
    }

    private fun gradePad(t: NumberTask, ex: Exercise.Numeral, answer: String): NumberGraded {
        val why = whyOf(t)
        if (ex.colon) {
            val h = t.n / 100
            val m = t.n % 100
            val got = time(answer)
            val right = got != null && got.second == m && got.first % 12 == h % 12
            if (right) return NumberGraded(Verdict.CORRECT, ex.shown, why = why)
            // "pol šestih" typed as 6:30: half past counted from the hour before
            val half = m == 30 && got != null && got.second == 30 && got.first % 12 == (h + 1) % 12
            return NumberGraded(Verdict.WRONG, ex.shown, why = why, kind = if (half) NumberKind.TIME_HALF else NumberKind.TIME)
        }
        val got = digits(answer)
        if (got == t.n) return NumberGraded(Verdict.CORRECT, ex.shown, why = why)
        val kind = when {
            got != null && swapped(t.n) == got -> NumberKind.ORDER
            t.mode == NumberMode.HEAR -> NumberKind.LISTENING
            else -> NumberKind.READING
        }
        return NumberGraded(Verdict.WRONG, ex.shown, why = if (kind == NumberKind.ORDER) orderWhy(t.n, "$got") else why, kind = kind)
    }

    private fun gradeWord(t: NumberTask, ex: Exercise.Numeral, answer: String): NumberGraded {
        val why = whyOf(t)
        val wrongKind = when (t.mode) {
            NumberMode.DATE -> NumberKind.DATE
            NumberMode.TIME_WRITE -> NumberKind.TIME
            else -> NumberKind.SPELLING
        }
        val canon = Grading.canonical(answer, Lang.SL)
        if (canon.isEmpty()) return NumberGraded(Verdict.WRONG, ex.shown, why = why, kind = wrongKind)
        if (ex.accept.any { Grading.canonical(it, Lang.SL) == canon }) return NumberGraded(Verdict.CORRECT, ex.shown, why = why)
        // the same word apart or together, hyphens or not: almost (one word up to 99, the hundreds and thousands apart)
        val squash = { s: String -> Grading.canonical(s, Lang.SL).replace(Regex("[\\s\\-–]+"), "") }
        ex.accept.firstOrNull { squash(it) == squash(answer) }?.let { return NumberGraded(Verdict.ALMOST, it, why = spacingWhy(it) + "\n" + why.orEmpty()) }
        val r = Grading.check(answer, ex.accept, Lang.SL)
        val other = parse(answer)?.takeIf { it != t.n && t.mode == NumberMode.WRITE }
        if (r.verdict == Verdict.ALMOST && r.hint == Grading.Hint.DIACRITICS) return NumberGraded(Verdict.ALMOST, r.expected, r.hint, r.marks, why)
        // č, š, ž forgotten and the words apart: almost too
        ex.accept.firstOrNull { key(it) == key(answer) }?.let { return NumberGraded(Verdict.ALMOST, it, Grading.Hint.DIACRITICS, why = spacingWhy(it) + "\n" + why.orEmpty()) }
        if (t.mode == NumberMode.WRITE) {
            if (parseTensFirst(answer) == t.n) return NumberGraded(Verdict.WRONG, ex.shown, why = orderWhy(t.n, answer.trim()), kind = NumberKind.ORDER)
            // a number's word has no ending to get wrong: a slip anywhere in it is one, unless it is another number's word
            val d = Grading.distance(key(answer), key(ex.shown))
            // 200 is dvesto: "dvasto" (dva + sto) is the learner's word, not a slip
            val learnerWord = "dvasto" in key(answer) && "dvesto" in key(ex.shown)
            if (other == null && !learnerWord && parseTensFirst(answer) == null && d in 1..Grading.typosAllowed(key(ex.shown))) {
                return NumberGraded(Verdict.ALMOST, ex.shown, Grading.Hint.TYPO, r.marks.takeIf { r.expected == ex.shown }.orEmpty(), why)
            }
            return NumberGraded(Verdict.WRONG, ex.shown, why = why, kind = wrongKind)
        }
        if (r.verdict == Verdict.ALMOST && !ordinalSlip(t, answer)) return NumberGraded(Verdict.ALMOST, r.expected, r.hint, r.marks, why)
        return when {
            t.mode == NumberMode.TIME_WRITE && halfSlip(t.n, answer) -> NumberGraded(Verdict.WRONG, ex.shown, why = why, kind = NumberKind.TIME_HALF)
            else -> NumberGraded(Verdict.WRONG, r.expected.ifBlank { ex.shown }, r.hint.takeIf { it == Grading.Hint.ENDING } ?: Grading.Hint.NONE, r.marks, why, wrongKind)
        }
    }

    /** A date's day written as its number with -i where the e drops ("sedemi" for sedmi, "osemi" for osmi): wrong, not a slip. */
    private fun ordinalSlip(t: NumberTask, answer: String): Boolean =
        t.mode == NumberMode.DATE && t.n in listOf(7, 8) && key(answer).startsWith(key(words(t.n) + "i"))

    /** 5:30 written "ob pol petih": the half counted from the hour before. */
    private fun halfSlip(t: Int, answer: String): Boolean =
        t % 100 == 30 && key(answer).removePrefix("ob") == key("pol " + HOURS_AT[t / 100 - 1])

    /** A said answer's kind, from what was heard: the tens first, else a slip of saying. */
    fun saidKind(n: Int, heard: String): NumberKind = if (parseTensFirst(heard) == n || digits(heard)?.let { swapped(n) == it } == true) NumberKind.ORDER else NumberKind.SPEAKING

    // --- why --------------------------------------------------------------------------------------------------------------

    /**
     * How [t]'s answer is built, in the learner's base (Markdown, the Slovene bold): 27 → sedem + in + dvajset (the ones
     * first, one word); 14 → -najst; 30 → -deset; the hundreds and thousands apart; a date's ordinal; a clock time's ob
     * and -ih, half towards the next hour.
     */
    fun whyOf(t: NumberTask): String? = when (t.mode) {
        NumberMode.DATE -> inBase("numbers.why.date", "day" to t.n, "ordinal" to SpokenNumbers.ordinal(t.n), "month" to MONTHS[(t.month ?: 5) - 1])
        NumberMode.TIME_HEAR, NumberMode.TIME_WRITE -> {
            val h = t.n / 100
            if (t.n % 100 == 30) inBase("numbers.why.half", "time" to clock(t.n), "said" to clockWords(t.n), "next" to (h % 12 + 1))
            else inBase("numbers.why.hour", "time" to clock(t.n), "said" to clockWords(t.n))
        }
        else -> numberWhy(t.n)
    }

    /** How [n]'s word is built ([whyOf] of a number). */
    fun numberWhy(n: Int): String {
        val w = words(n)
        val low = n % 100
        return when {
            n < 11 -> inBase("numbers.why.plain", "n" to n, "word" to w)
            n < 20 -> inBase("numbers.why.teen", "n" to n, "word" to w)
            n < 100 && low % 10 == 0 -> inBase("numbers.why.tens", "n" to n, "word" to w)
            n < 100 -> inBase("numbers.why.compound", "n" to n, "word" to w, "ones" to words(low % 10), "tens" to words(low - low % 10), "o" to low % 10, "t" to low - low % 10)
            else -> inBase("numbers.why.big", "n" to n, "word" to w)
        }
    }

    private fun orderWhy(n: Int, wrong: String): String {
        val low = n % 100
        return inBase("numbers.why.order", "n" to n, "word" to words(n), "ones" to words(low % 10), "tens" to words(low - low % 10), "o" to low % 10, "t" to low - low % 10, "wrong" to wrong)
    }

    private fun spacingWhy(right: String): String = inBase("numbers.why.spacing", "word" to right)

    // --- after a run ------------------------------------------------------------------------------------------------------

    /**
     * The run's answers kept: each ranged one on its range's last answers (an almost counts as right), each number missed
     * to come back (wrong: the next run, today; almost: tomorrow), each one coming back moved on a box when right (a day,
     * three, a week, then gone) or back to the first when wrong; the next range opened when the newest open one is mostly
     * right now ([PASS] of its last [WINDOW]). [verdicts] may be fewer than [tasks] (a run left early).
     */
    fun record(p: NumbersProgress?, introDone: Boolean, tasks: List<NumberTask>, verdicts: List<Verdict>, today: LocalDate): NumbersRecorded {
        val start = p ?: NumbersProgress()
        val last = start.last.toMutableMap()
        val again = start.again.toMutableList()
        val day = today.toString()
        for ((t, v) in tasks.zip(verdicts)) {
            t.range?.let { r -> last[r.id] = (last[r.id].orEmpty() + if (v == Verdict.WRONG) "0" else "1").takeLast(WINDOW) }
            val mode = t.kept ?: t.mode.wire
            val i = again.indexOfFirst { it.n == t.n && it.mode == mode && it.month == t.month }
            val was = again.getOrNull(i)
            val next: NumberAgain? = when (v) {
                Verdict.WRONG -> NumberAgain(t.n, mode, day, 0, t.month)
                Verdict.ALMOST -> NumberAgain(t.n, mode, today.plusDays(1).toString(), was?.box ?: 1, t.month)
                Verdict.CORRECT -> was?.let { a ->
                    val box = a.box + 1
                    if (box >= INTERVALS.size) null else a.copy(box = box, due = today.plusDays(INTERVALS[box].toLong()).toString())
                }
            }
            if (i >= 0) again.removeAt(i)
            if (next != null) again += next
        }
        val kept = again.sortedWith(compareBy<NumberAgain> { it.box }.thenByDescending { it.due }).take(MAX_KEPT)
        var opened = start.opened
        var newly: NumberRange? = null
        val progress0 = start.copy(last = last, again = kept)
        val newest = newest(progress0, introDone)
        if (newest != null && passedWindow(progress0, newest)) newest.next?.let { n ->
            if (!opened.containsKey(n.id)) {
                opened = opened + (n.id to day)
                newly = n
            }
        }
        val out = progress0.copy(opened = opened, runs = start.runs + 1, day = day)
        return NumbersRecorded(out, newly)
    }

    // --- for the tutor --------------------------------------------------------------------------------------------------

    /**
     * Each wrong answer as an error of update-db.py's report ([NumberKind] its pattern id), ready to persist from the
     * session_end (`data.numbers.errors`): what was answered against what was right, and where.
     */
    fun errors(tasks: List<NumberTask>, exercises: List<Exercise>, answers: List<Pair<String, NumberKind?>>, verdicts: List<Verdict>) = buildJsonArray {
        for (i in verdicts.indices) {
            if (verdicts[i] != Verdict.WRONG) continue
            val kind = answers.getOrNull(i)?.second ?: continue
            val t = tasks.getOrNull(i) ?: continue
            add(buildJsonObject {
                put("pattern_id", kind.id)
                put("category", kind.category)
                put("subcategory", "numbers")
                put("description", kind.description)
                put("severity", if (kind == NumberKind.ORDER || kind == NumberKind.TIME_HALF) "moderate" else "minor")
                put("your_answer", answers[i].first.take(80))
                put("correct_answer", expected(exercises.getOrNull(i)).take(80))
                put("context", "${t.mode.emoji} ${t.mode.wire} ${shownOf(t)} (🔢 Števila · Numbers${t.range?.let { ", ${it.label}" }.orEmpty()})")
            })
        }
    }

    /** What [ex] expects, in one line: its figures, its word, its form. */
    fun expected(ex: Exercise?): String = when (ex) {
        is Exercise.Numeral -> ex.shown
        is Exercise.Speak -> ex.say
        else -> ""
    }

    /** What a task shows or says, for the tutor: "27", "sedemindvajset", "15. maj", "5:30". */
    fun shownOf(t: NumberTask): String = when (t.mode) {
        NumberMode.DATE -> "${t.n}. ${MONTHS[(t.month ?: 5) - 1]}"
        NumberMode.TIME_HEAR, NumberMode.TIME_WRITE -> clock(t.n)
        else -> "${t.n}"
    }

    /**
     * The run for the tutor's session_end (`data.numbers`): the ranges open and the one opened now, the ways of asking,
     * each task with its answer, what was right, its verdict and kind, the numbers due again, the errors to persist, and
     * that its Slovene is machine-written.
     */
    fun wire(
        after: NumbersRecorded, introDone: Boolean, tasks: List<NumberTask>, exercises: List<Exercise>,
        answers: List<Pair<String, NumberKind?>>, verdicts: List<Verdict>, modes: Set<NumberMode>, today: LocalDate,
    ): JsonObject = buildJsonObject {
        put("language", LANGUAGE)
        put("ranges_open", buildJsonArray { ranges(after.progress, introDone).forEach { add(JsonPrimitive(it.label)) } })
        after.opened?.let { put("opened_now", it.label) }
        newest(after.progress, introDone)?.let { r ->
            val (right, of) = score(after.progress, r)
            put("newest", buildJsonObject { put("range", r.label); put("right", right); put("of", of); put("pass", PASS); put("window", WINDOW) })
        }
        put("modes", buildJsonArray { modes.forEach { add(JsonPrimitive(it.wire)) } })
        put("right", verdicts.count { it == Verdict.CORRECT })
        put("almost", verdicts.count { it == Verdict.ALMOST })
        put("of", verdicts.size)
        if (verdicts.size < tasks.size) put("left_early", true)
        put("tasks", buildJsonArray {
            for (i in verdicts.indices) {
                val t = tasks[i]
                add(buildJsonObject {
                    put("mode", t.mode.wire)
                    put("shown", shownOf(t))
                    t.range?.let { put("range", it.label) }
                    if (t.again) put("again", true)
                    put("answer", answers.getOrNull(i)?.first.orEmpty().take(80))
                    put("expected", expected(exercises.getOrNull(i)).take(80))
                    put("verdict", verdicts[i].name.lowercase())
                    answers.getOrNull(i)?.second?.takeIf { verdicts[i] == Verdict.WRONG }?.let { put("kind", it.id) }
                })
            }
        })
        put("due_again", due(after.progress, today.plusDays(7)).size)
        put("errors", errors(tasks, exercises, answers, verdicts))
        put("runs", after.progress.runs)
        put("machine_written", true)
    }
}
