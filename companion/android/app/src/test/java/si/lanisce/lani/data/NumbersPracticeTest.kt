package si.lanisce.lani.data

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.CourseProgress
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.UnitProgress
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.time.LocalDate
import kotlin.random.Random

/**
 * The numbers module (data/NumbersPractice.kt, SpokenNumbers' Slovene): the words against a hand-checked table of the
 * tricky ones (1 and 2's forms, 11–19, 21, 22, 99, 100, 101, 200, 1000, 2000, 2026), every number of the ranges read back,
 * the ordinals of the dates and the clock's hours; the grading of the pad and of a word (č, š, ž, words apart, a slip,
 * another number's word, the tens first), each wrong one's kind; the ranges opening (8 of the last 10), the numbers missed
 * coming back; when it is open and which ways of asking are on; a run's plan; what the tutor is told.
 */
class NumbersPracticeTest {
    private val pair = L10n.pair
    private val today = LocalDate.of(2026, 10, 19)

    @Before fun pair() {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
    }

    @After fun back() {
        L10n.pair = pair
    }

    // --- the words ----------------------------------------------------------------------------------------------------

    /** Hand-checked: the bare number as it is counted (and written: one word up to 99, the hundreds and thousands apart). */
    private val table = mapOf(
        0 to "nič", 1 to "ena", 2 to "dva", 3 to "tri", 4 to "štiri", 5 to "pet", 6 to "šest", 7 to "sedem", 8 to "osem",
        9 to "devet", 10 to "deset", 11 to "enajst", 12 to "dvanajst", 13 to "trinajst", 14 to "štirinajst", 15 to "petnajst",
        16 to "šestnajst", 17 to "sedemnajst", 18 to "osemnajst", 19 to "devetnajst", 20 to "dvajset", 21 to "enaindvajset",
        22 to "dvaindvajset", 23 to "triindvajset", 24 to "štiriindvajset", 25 to "petindvajset", 27 to "sedemindvajset",
        30 to "trideset", 40 to "štirideset", 45 to "petinštirideset", 50 to "petdeset", 60 to "šestdeset", 70 to "sedemdeset",
        80 to "osemdeset", 90 to "devetdeset", 99 to "devetindevetdeset", 100 to "sto", 101 to "sto ena", 102 to "sto dva",
        110 to "sto deset", 121 to "sto enaindvajset", 200 to "dvesto", 300 to "tristo", 400 to "štiristo", 600 to "šeststo",
        999 to "devetsto devetindevetdeset", 1000 to "tisoč", 1001 to "tisoč ena", 1100 to "tisoč sto",
        1991 to "tisoč devetsto enaindevetdeset", 2000 to "dva tisoč", 2026 to "dva tisoč šestindvajset", 3000 to "tri tisoč",
        9999 to "devet tisoč devetsto devetindevetdeset", 21000 to "enaindvajset tisoč", 100000 to "sto tisoč",
    )

    @Test fun `the hand-checked numbers are written as the table has them`() {
        for ((n, w) in table) assertEquals("$n", w, SpokenNumbers.slovene(n))
    }

    @Test fun `one and two take their gender's forms too, never another number's`() {
        assertEquals(listOf("en", "eno", "eden"), SpokenNumbers.sloveneAlso(1))
        assertEquals(listOf("dve"), SpokenNumbers.sloveneAlso(2))
        assertEquals(listOf("trije"), SpokenNumbers.sloveneAlso(3))
        assertEquals(listOf("štirje"), SpokenNumbers.sloveneAlso(4))
        for (n in listOf(1, 2, 3, 4)) assertTrue(SpokenNumbers.forms(n, Lang.SL).containsAll(listOf(SpokenNumbers.slovene(n)) + SpokenNumbers.sloveneAlso(n).filter { it != "eden" }))
        // in a compound, ena and dva only
        assertEquals("enaindvajset", NumbersPractice.words(21))
        assertEquals("dvaindvajset", NumbersPractice.words(22))
    }

    @Test fun `every number of the ranges reads back as itself, and speech's words agree up to 999`() {
        for (n in 0..9999) assertEquals("$n", n, NumbersPractice.parse(NumbersPractice.words(n)))
        for (n in 0..999) assertEquals("$n", SpokenNumbers.forms(n, Lang.SL).first(), SpokenNumbers.slovene(n))
        // the ones first, joined by in, in one word, from 21 to 99 (but the tens)
        for (n in 21..99) if (n % 10 != 0) {
            val w = NumbersPractice.words(n)
            assertTrue(w, w.startsWith(NumbersPractice.words(n % 10) + "in") && ' ' !in w)
        }
        // no number's own word says the tens first
        for (n in 0..9999) assertNull("$n", NumbersPractice.parseTensFirst(NumbersPractice.words(n)))
        // speech still leaves a year in figures as it is: a text may write it so
        assertEquals("leta 2026", SpokenNumbers.inWords("leta 2026", Lang.SL))
    }

    @Test fun `every range's numbers are its own, and each range draws only its own`() {
        val r = Random(4)
        for (range in NumberRange.entries) repeat(200) {
            val n = range.draw(r)
            assertTrue("$range $n", range.has(n))
            assertEquals(range, NumberRange.of(n))
        }
        assertEquals(NumberRange.TEENS, NumberRange.of(20))
        assertEquals(NumberRange.TENS, NumberRange.of(30))
        assertEquals(NumberRange.TO_99, NumberRange.of(27))
    }

    @Test fun `a date's ordinals and the clock's hours`() {
        val ord = mapOf(
            1 to "prvi", 2 to "drugi", 3 to "tretji", 4 to "četrti", 5 to "peti", 6 to "šesti", 7 to "sedmi", 8 to "osmi",
            9 to "deveti", 10 to "deseti", 11 to "enajsti", 12 to "dvanajsti", 15 to "petnajsti", 20 to "dvajseti",
            21 to "enaindvajseti", 22 to "dvaindvajseti", 27 to "sedemindvajseti", 30 to "trideseti", 31 to "enaintrideseti",
        )
        for ((n, w) in ord) assertEquals("$n", w, SpokenNumbers.ordinal(n))
        assertEquals("Danes je petnajsti maj.", NumbersPractice.dateSaid(15, 5))
        assertEquals("Danes je ___ maj. (15.)", NumbersPractice.dateFrame(15, 5))
        assertEquals("ob petih", NumbersPractice.clockWords(500))
        assertEquals("ob dveh", NumbersPractice.clockWords(200))
        assertEquals("ob sedmih", NumbersPractice.clockWords(700))
        assertEquals("ob osmih", NumbersPractice.clockWords(800))
        assertEquals("ob dvanajstih", NumbersPractice.clockWords(1200))
        // half counts towards the next hour
        assertEquals("ob pol šestih", NumbersPractice.clockWords(530))
        assertEquals("ob pol enih", NumbersPractice.clockWords(1230))
        assertEquals("ob pol dvanajstih", NumbersPractice.clockWords(1130))
        assertEquals("5:30", NumbersPractice.clock(530))
    }

    // --- grading --------------------------------------------------------------------------------------------------------

    private fun ex(mode: NumberMode, n: Int, month: Int? = null) = NumbersPractice.exercise(NumberTask(mode, n, month)) as Exercise.Numeral

    private fun grade(mode: NumberMode, n: Int, answer: String, month: Int? = null) = NumbersPractice.grade(ex(mode, n, month), answer)

    @Test fun `figures on the pad are right or wrong, the ones and tens swapped of their own kind`() {
        assertEquals(Verdict.CORRECT, grade(NumberMode.HEAR, 27, "27").verdict)
        assertEquals(Verdict.CORRECT, grade(NumberMode.HEAR, 27, "027").verdict)
        grade(NumberMode.HEAR, 27, "72").let { assertEquals(Verdict.WRONG, it.verdict); assertEquals(NumberKind.ORDER, it.kind) }
        grade(NumberMode.HEAR, 27, "26").let { assertEquals(Verdict.WRONG, it.verdict); assertEquals(NumberKind.LISTENING, it.kind) }
        assertEquals(NumberKind.ORDER, grade(NumberMode.READ, 17, "71").kind)
        assertEquals(NumberKind.READING, grade(NumberMode.READ, 17, "16").kind)
        assertEquals(NumberKind.ORDER, grade(NumberMode.HEAR, 2026, "2062").kind)
        assertEquals(NumberKind.LISTENING, grade(NumberMode.HEAR, 22, "22 2").kind)
        // 20 has no swap (its 0), 11 neither (the same figure twice)
        assertEquals(NumberKind.LISTENING, grade(NumberMode.HEAR, 20, "2").kind)
        assertNull(NumbersPractice.swapped(11))
        // the right one shown is the figures, the why the word's build
        grade(NumberMode.HEAR, 27, "72").let { assertEquals("27", it.expected); assertTrue(it.why!!, "sedemindvajset" in it.why!!) }
    }

    @Test fun `a word is graded as a typed answer with what a number adds`() {
        assertEquals(Verdict.CORRECT, grade(NumberMode.WRITE, 27, "sedemindvajset").verdict)
        assertEquals(Verdict.CORRECT, grade(NumberMode.WRITE, 27, " Sedemindvajset. ").verdict)
        // the same letters apart, hyphens: almost
        assertEquals(Verdict.ALMOST, grade(NumberMode.WRITE, 27, "sedem in dvajset").verdict)
        assertEquals(Verdict.ALMOST, grade(NumberMode.WRITE, 27, "sedem-in-dvajset").verdict)
        assertEquals(Verdict.ALMOST, grade(NumberMode.WRITE, 2026, "dvatisoč šestindvajset").verdict)
        // č, š, ž forgotten: almost (with its hint), also with the words apart
        grade(NumberMode.WRITE, 14, "stirinajst").let { assertEquals(Verdict.ALMOST, it.verdict); assertEquals(Grading.Hint.DIACRITICS, it.hint) }
        assertEquals(Verdict.ALMOST, grade(NumberMode.WRITE, 2026, "dva tisoc sestindvajset").verdict)
        assertEquals(Verdict.ALMOST, grade(NumberMode.WRITE, 2026, "dvatisoc sestindvajset").verdict)
        // a slip anywhere in the word: almost (a number has no ending to get wrong)
        grade(NumberMode.WRITE, 27, "sedemindvjset").let { assertEquals(Verdict.ALMOST, it.verdict); assertEquals(Grading.Hint.TYPO, it.hint) }
        assertEquals(Verdict.ALMOST, grade(NumberMode.WRITE, 27, "sedemindvajst").verdict)
        // another number's word is wrong, never a slip
        grade(NumberMode.WRITE, 9, "deset").let { assertEquals(Verdict.WRONG, it.verdict); assertEquals(NumberKind.SPELLING, it.kind) }
        assertEquals(Verdict.WRONG, grade(NumberMode.WRITE, 27, "šestindvajset").verdict)
        assertEquals(Verdict.WRONG, grade(NumberMode.WRITE, 200, "dvasto").verdict)
        assertEquals(Verdict.CORRECT, grade(NumberMode.WRITE, 200, "dvesto").verdict)
        // the tens first: wrong, of its own kind, with the order's why
        for (a in listOf("dvajset sedem", "dvajsetsedem", "dvajset in sedem", "dvajsetinsedem")) {
            grade(NumberMode.WRITE, 27, a).let { assertEquals(a, Verdict.WRONG, it.verdict); assertEquals(a, NumberKind.ORDER, it.kind); assertTrue(it.why!!, "ones first" in it.why!!) }
        }
        assertEquals(NumberKind.ORDER, grade(NumberMode.WRITE, 127, "sto dvajset sedem").kind)
        assertEquals(NumberKind.ORDER, grade(NumberMode.WRITE, 2026, "dva tisoč dvajset šest").kind)
        // 1 and 2 bare in a gender's form: right; the counting form is shown
        assertEquals(Verdict.CORRECT, grade(NumberMode.WRITE, 1, "en").verdict)
        assertEquals(Verdict.CORRECT, grade(NumberMode.WRITE, 2, "dve").verdict)
        assertEquals("ena", grade(NumberMode.WRITE, 1, "x").expected)
        assertEquals(Verdict.WRONG, grade(NumberMode.WRITE, 5, "").verdict)
    }

    @Test fun `a date's day is its ordinal`() {
        assertEquals(Verdict.CORRECT, grade(NumberMode.DATE, 15, "petnajsti", 5).verdict)
        assertEquals(Verdict.CORRECT, grade(NumberMode.DATE, 15, "petnajsti maj", 5).verdict)
        assertEquals(Verdict.CORRECT, grade(NumberMode.DATE, 15, "Danes je petnajsti maj.", 5).verdict)
        assertEquals(Verdict.ALMOST, grade(NumberMode.DATE, 4, "cetrti", 6).verdict)
        // the cardinal, another ending, the e kept: wrong, a date's kind
        for ((n, a) in listOf(15 to "petnajst", 15 to "petnajsta", 7 to "sedemi", 8 to "osemi")) {
            grade(NumberMode.DATE, n, a, 5).let { assertEquals(a, Verdict.WRONG, it.verdict); assertEquals(a, NumberKind.DATE, it.kind) }
        }
        assertEquals(Verdict.CORRECT, grade(NumberMode.DATE, 7, "sedmi", 5).verdict)
        assertEquals("datumi", ex(NumberMode.DATE, 15, 5).grammar)
        assertTrue(grade(NumberMode.DATE, 15, "petnajst", 5).why!!.contains("petnajsti"))
    }

    @Test fun `a clock time heard is typed on either half of the day, half past towards the next hour`() {
        for (a in listOf("5:30", "05:30", "17:30", "530", "1730")) assertEquals(a, Verdict.CORRECT, grade(NumberMode.TIME_HEAR, 530, a).verdict)
        grade(NumberMode.TIME_HEAR, 530, "6:30").let { assertEquals(Verdict.WRONG, it.verdict); assertEquals(NumberKind.TIME_HALF, it.kind) }
        assertEquals(NumberKind.TIME, grade(NumberMode.TIME_HEAR, 530, "5:00").kind)
        assertEquals(Verdict.CORRECT, grade(NumberMode.TIME_HEAR, 1230, "0:30").verdict)
        assertEquals(Verdict.CORRECT, grade(NumberMode.TIME_HEAR, 1230, "12:30").verdict)
        assertEquals(NumberKind.TIME_HALF, grade(NumberMode.TIME_HEAR, 1230, "1:30").kind)
        assertEquals(Verdict.CORRECT, grade(NumberMode.TIME_HEAR, 500, "5").verdict)
        assertTrue(ex(NumberMode.TIME_HEAR, 530).colon)
        assertEquals("ob pol šestih", ex(NumberMode.TIME_HEAR, 530).audio)
    }

    @Test fun `a clock time written`() {
        assertEquals(Verdict.CORRECT, grade(NumberMode.TIME_WRITE, 530, "ob pol šestih").verdict)
        assertEquals(Verdict.CORRECT, grade(NumberMode.TIME_WRITE, 530, "pol šestih").verdict)
        assertEquals(Verdict.ALMOST, grade(NumberMode.TIME_WRITE, 530, "ob pol sestih").verdict)
        grade(NumberMode.TIME_WRITE, 530, "ob pol petih").let { assertEquals(Verdict.WRONG, it.verdict); assertEquals(NumberKind.TIME_HALF, it.kind) }
        assertEquals(Verdict.CORRECT, grade(NumberMode.TIME_WRITE, 500, "ob petih").verdict)
        grade(NumberMode.TIME_WRITE, 500, "ob pet").let { assertEquals(Verdict.WRONG, it.verdict); assertEquals(NumberKind.TIME, it.kind) }
        assertEquals("kdaj-cas", ex(NumberMode.TIME_WRITE, 500).grammar)
    }

    @Test fun `a number said is a speak exercise, a year right as its figures too`() {
        val say = NumbersPractice.exercise(NumberTask(NumberMode.SAY, 27)) as Exercise.Speak
        assertEquals("sedemindvajset", say.say)
        assertEquals("27", say.prompt)
        assertFalse(say.show)
        assertEquals(Verdict.CORRECT, SpeechGrading.best(listOf("27"), null, say.answers())?.result?.verdict)
        assertEquals(Verdict.CORRECT, SpeechGrading.best(listOf("sedemindvajset"), null, say.answers())?.result?.verdict)
        assertEquals(Verdict.WRONG, SpeechGrading.best(listOf("dvajset sedem"), null, say.answers())?.result?.verdict)
        val year = NumbersPractice.exercise(NumberTask(NumberMode.SAY, 2026)) as Exercise.Speak
        assertEquals(Verdict.CORRECT, SpeechGrading.best(listOf("2026"), null, year.answers())?.result?.verdict)
        assertEquals(NumberKind.ORDER, NumbersPractice.saidKind(27, "dvajset sedem"))
        assertEquals(NumberKind.ORDER, NumbersPractice.saidKind(27, "72"))
        assertEquals(NumberKind.SPEAKING, NumbersPractice.saidKind(27, "šestindvajset"))
    }

    // --- open, the ranges, the numbers missed ----------------------------------------------------------------------------

    private fun state(intro: Boolean = true, age: Age = Age.ZASELEK, numbers: NumbersProgress? = null) = GameState(
        seed = 1, age = age, numbers = numbers,
        course = CourseProgress(units = if (intro) mapOf("sl/stevila" to UnitProgress(intro = 6, introDone = true)) else emptyMap()),
    )

    @Test fun `it opens with the unit stevila's intro, and before it says what opens it`() {
        assertEquals(NumbersPractice.Lock.CLASS, NumbersPractice.lock(null))
        assertEquals(NumbersPractice.Lock.CLASS, NumbersPractice.lock(state(intro = false, age = Age.OGENJ)))
        assertEquals(NumbersPractice.Lock.INTRO, NumbersPractice.lock(state(intro = false)))
        assertNull(NumbersPractice.lock(state()))
        assertTrue(NumbersPractice.open(state()))
        // closed: no ranges, no run
        assertEquals(emptyList<NumberRange>(), NumbersPractice.ranges(null, introDone = false))
        assertEquals(emptyList<NumberTask>(), NumbersPractice.plan(null, false, setOf(NumberMode.READ), today, Random(1)))
        // the intro opens the first two
        assertEquals(listOf(NumberRange.TO_TEN, NumberRange.TEENS), NumbersPractice.ranges(null, introDone = true))
        assertEquals(NumberRange.TEENS, NumbersPractice.newest(null, true))
    }

    @Test fun `the ways of asking are on by what the phone can and the pages introduced`() {
        assertEquals(setOf(NumberMode.READ, NumberMode.WRITE), NumbersPractice.modes(canSay = false, canListen = false, introduced = emptySet()))
        assertEquals(setOf(NumberMode.READ, NumberMode.WRITE, NumberMode.HEAR, NumberMode.DATE), NumbersPractice.modes(true, false, setOf("datumi")))
        assertEquals(
            setOf(NumberMode.READ, NumberMode.WRITE, NumberMode.HEAR, NumberMode.SAY, NumberMode.DATE, NumberMode.TIME_HEAR, NumberMode.TIME_WRITE),
            NumbersPractice.modes(true, true, setOf("datumi", "kdaj-cas")),
        )
        // the clock without a voice: written only
        assertEquals(setOf(NumberMode.READ, NumberMode.WRITE, NumberMode.TIME_WRITE), NumbersPractice.modes(false, false, setOf("kdaj-cas")))
    }

    private fun answers(range: NumberRange, right: Int, wrong: Int): Pair<List<NumberTask>, List<Verdict>> {
        val r = Random(right * 31 + wrong)
        val tasks = List(right + wrong) { NumberTask(NumberMode.READ, range.draw(r)) }
        return tasks to (List(right) { Verdict.CORRECT } + List(wrong) { Verdict.WRONG })
    }

    @Test fun `the next range opens when 8 of the newest range's last 10 are right`() {
        val (t8, v8) = answers(NumberRange.TEENS, 8, 2)
        val r8 = NumbersPractice.record(null, true, t8, v8, today)
        assertEquals(NumberRange.TENS, r8.opened)
        assertEquals(today.toString(), r8.progress.opened["tens"])
        assertEquals(listOf(NumberRange.TO_TEN, NumberRange.TEENS, NumberRange.TENS), NumbersPractice.ranges(r8.progress, true))
        assertEquals(NumberRange.TENS, NumbersPractice.newest(r8.progress, true))
        // 7 of 10: not yet
        val (t7, v7) = answers(NumberRange.TEENS, 7, 3)
        assertNull(NumbersPractice.record(null, true, t7, v7, today).opened)
        // 8 right but fewer than 10 answered: not yet
        val (t9, v9) = answers(NumberRange.TEENS, 8, 1)
        assertNull(NumbersPractice.record(null, true, t9, v9, today).opened)
        // an almost counts as right
        val (ta, _) = answers(NumberRange.TEENS, 10, 0)
        assertEquals(NumberRange.TENS, NumbersPractice.record(null, true, ta, List(5) { Verdict.ALMOST } + List(3) { Verdict.CORRECT } + List(2) { Verdict.WRONG }, today).opened)
        // the older range's answers don't open anything (11–20 is the newest)
        val (t0, v0) = answers(NumberRange.TO_TEN, 10, 0)
        assertNull(NumbersPractice.record(null, true, t0, v0, today).opened)
        // only the last 10 count: the wrong ones before fall out
        val (tw, vw) = answers(NumberRange.TEENS, 0, 5)
        val after = NumbersPractice.record(NumbersPractice.record(null, true, tw, vw, today).progress, true, t8, v8, today)
        assertEquals(NumberRange.TENS, after.opened)
        assertEquals(8 to 10, NumbersPractice.score(after.progress, NumberRange.TEENS))
        // dates and clock times count on no range
        val dates = NumbersPractice.record(null, true, List(10) { NumberTask(NumberMode.DATE, 15, 5) }, List(10) { Verdict.CORRECT }, today)
        assertEquals(0 to 0, NumbersPractice.score(dates.progress, NumberRange.TEENS))
        // the last range: mostly right, it is the learner's
        val all = NumbersProgress(opened = NumberRange.entries.drop(2).associate { it.id to "2026-01-01" }, last = mapOf("1000-9999" to "1111111100"))
        assertTrue(NumberRange.THOUSANDS in NumbersPractice.passed(all, true))
        assertNull(NumbersPractice.record(all, true, emptyList(), emptyList(), today).opened)
    }

    @Test fun `a number missed comes back today, a day later, three days, a week, then it is gone`() {
        val t = NumberTask(NumberMode.HEAR, 27)
        var p = NumbersPractice.record(null, true, listOf(t), listOf(Verdict.WRONG), today).progress
        assertEquals(listOf(NumberAgain(27, "hear", "2026-10-19", 0)), p.again)
        assertEquals(1, NumbersPractice.due(p, today).size)
        val days = listOf(1L, 3L, 7L)
        for ((i, d) in days.withIndex()) {
            p = NumbersPractice.record(p, true, listOf(t.copy(again = true)), listOf(Verdict.CORRECT), today).progress
            assertEquals(i + 1, p.again.single().box)
            assertEquals(today.plusDays(d).toString(), p.again.single().due)
        }
        p = NumbersPractice.record(p, true, listOf(t), listOf(Verdict.CORRECT), today).progress
        assertTrue(p.again.isEmpty())
        // wrong again: back to the first box
        p = NumbersPractice.record(null, true, listOf(t), listOf(Verdict.WRONG), today).progress
        p = NumbersPractice.record(p, true, listOf(t), listOf(Verdict.CORRECT), today).progress
        p = NumbersPractice.record(p, true, listOf(t), listOf(Verdict.WRONG), today).progress
        assertEquals(0, p.again.single().box)
        // almost: tomorrow
        val a = NumbersPractice.record(null, true, listOf(NumberTask(NumberMode.WRITE, 14)), listOf(Verdict.ALMOST), today).progress
        assertEquals(NumberAgain(14, "write", "2026-10-20", 1), a.again.single())
        assertEquals(0, NumbersPractice.due(a, today).size)
        // a run left early keeps what was answered
        val early = NumbersPractice.record(null, true, listOf(t, NumberTask(NumberMode.READ, 5)), listOf(Verdict.WRONG), today).progress
        assertEquals(1, early.again.size)
        assertEquals(1, early.runs)
    }

    // --- a run -----------------------------------------------------------------------------------------------------------

    private val all = setOf(NumberMode.READ, NumberMode.WRITE, NumberMode.HEAR, NumberMode.SAY, NumberMode.DATE, NumberMode.TIME_HEAR, NumberMode.TIME_WRITE)

    @Test fun `a run mixes the ways of asking, no number twice, the newest range most`() {
        for (seed in 1..60) {
            val tasks = NumbersPractice.plan(null, true, all, today, Random(seed))
            assertEquals(NumbersPractice.RUN, tasks.size)
            val keys = tasks.map { "${if (it.mode == NumberMode.DATE) "d${it.month}" else if (it.mode.name.startsWith("TIME")) "t" else "n"}:${it.n}" }
            assertEquals(keys.toString(), keys.size, keys.toSet().size)
            for (i in 1 until tasks.size) assertFalse("$seed: ${tasks.map { it.mode }}", tasks[i].mode == tasks[i - 1].mode)
            for (t in tasks) when (t.mode) {
                NumberMode.DATE -> assertTrue("$t", t.n in 1..20 && t.month in 1..12)
                NumberMode.TIME_HEAR, NumberMode.TIME_WRITE -> assertTrue("$t", t.n / 100 in 1..12 && t.n % 100 in listOf(0, 30))
                // to say: only from the ranges the learner has (0–10, 11–20 being open)
                NumberMode.SAY -> assertEquals(NumberRange.TO_TEN, t.range)
                else -> assertTrue("$t", t.range in listOf(NumberRange.TO_TEN, NumberRange.TEENS))
            }
        }
        // the newest range open gets most of the new numbers
        val p = NumbersProgress(opened = mapOf("tens" to "2026-10-01", "21-99" to "2026-10-10"))
        val many = (1..40).flatMap { NumbersPractice.plan(p, true, setOf(NumberMode.READ, NumberMode.WRITE), today, Random(it)) }
        assertTrue(many.count { it.range == NumberRange.TO_99 } > many.size / 2)
        // QA's one way of asking
        assertTrue(NumbersPractice.plan(null, true, all, today, Random(3), only = NumberMode.HEAR).all { it.mode == NumberMode.HEAR })
        // the module asks nothing it can't: no voice, no hearing
        assertTrue(NumbersPractice.plan(null, true, setOf(NumberMode.READ, NumberMode.WRITE), today, Random(3)).none { it.mode == NumberMode.HEAR })
    }

    @Test fun `the numbers missed that are due come first, spread, at most four, by another way when theirs is off`() {
        val d = today.toString()
        val p = NumbersProgress(again = listOf(
            NumberAgain(17, "hear", d), NumberAgain(12, "write", d), NumberAgain(19, "read", d), NumberAgain(14, "read", "2026-10-18"),
            NumberAgain(16, "write", d), NumberAgain(5, "read", "2026-10-25"),
        ))
        val tasks = NumbersPractice.plan(p, true, setOf(NumberMode.READ, NumberMode.WRITE), today, Random(2))
        val again = tasks.filter { it.again }
        assertEquals(4, again.size)
        assertEquals(14, again.first().n) // the oldest due first
        assertTrue(tasks.first().again)
        // heard when there is no voice: read instead, and kept as heard
        val heard = again.single { it.n == 17 }
        assertEquals(NumberMode.READ, heard.mode)
        assertEquals("hear", heard.kept)
        val after = NumbersPractice.record(p, true, listOf(heard), listOf(Verdict.CORRECT), today).progress
        assertEquals(NumberAgain(17, "hear", "2026-10-20", 1), after.again.single { it.n == 17 })
        // not due yet: not asked
        assertTrue(tasks.none { it.again && it.n == 5 })
        assertEquals(NumbersPractice.RUN, tasks.size)
    }

    // --- for the tutor --------------------------------------------------------------------------------------------------

    @Test fun `the run for the tutor has each task, the wrong ones' kinds and the errors to persist`() {
        val tasks = listOf(NumberTask(NumberMode.WRITE, 27), NumberTask(NumberMode.HEAR, 15), NumberTask(NumberMode.DATE, 7, 5))
        val exercises = tasks.map(NumbersPractice::exercise)
        val answers = listOf("dvajset sedem" to NumberKind.ORDER, "15" to null, "sedemi" to NumberKind.DATE)
        val verdicts = listOf(Verdict.WRONG, Verdict.CORRECT, Verdict.WRONG)
        val rec = NumbersPractice.record(null, true, tasks, verdicts, today)
        val w = NumbersPractice.wire(rec, true, tasks, exercises, answers, verdicts, setOf(NumberMode.WRITE, NumberMode.HEAR, NumberMode.DATE), today)
        assertEquals("sl", w["language"]!!.jsonPrimitive.content)
        assertEquals(listOf("0–10", "11–20"), w["ranges_open"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(1, w["right"]!!.jsonPrimitive.content.toInt())
        val errors = w["errors"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("numbers_order", "numbers_ordinal_date"), errors.map { it["pattern_id"]!!.jsonPrimitive.content })
        assertEquals("sedemindvajset", errors[0]["correct_answer"]!!.jsonPrimitive.content)
        assertEquals("dvajset sedem", errors[0]["your_answer"]!!.jsonPrimitive.content)
        assertEquals("sedmi", errors[1]["correct_answer"]!!.jsonPrimitive.content)
        val t = w["tasks"]!!.jsonArray.map { it.jsonObject }
        assertEquals("numbers_order", t[0]["kind"]!!.jsonPrimitive.content)
        assertNull(t[1]["kind"])
        assertEquals("7. maj", t[2]["shown"]!!.jsonPrimitive.content)
        assertEquals(true, w["machine_written"]!!.jsonPrimitive.content.toBoolean())
        // the kinds' ids land on the pages they are about: a date's on datumi ("ordinal"), the clock's on kdaj-cas
        assertTrue(NumberKind.DATE.id.split('_').contains("ordinal"))
        assertTrue("clock_time" in NumberKind.TIME.id && "clock_time" in NumberKind.TIME_HALF.id)
        assertNotNull(NumbersPractice.whyOf(tasks[0]))
    }
}
