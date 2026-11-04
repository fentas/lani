package si.lanisce.lani.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * The numbers module in British English (data/EnglishNumbers.kt, SpokenNumbers.english), for a learner from German and from
 * Slovene: the words against a hand-checked table, every number read back with its other forms (a hundred, the American
 * without and, a year in pairs), when it opens (the unit numbers; time brings the dates and the clock), and the grading: the
 * tens first against German's and Slovene's ones first, forty without u, a date's ordinal (fifth, ninth, twelfth: the
 * regular "fiveth" wrong), half past the hour that is there (half five is 5:30).
 */
class NumbersEnglishTest {
    private val pair = L10n.pair
    private val en = NumberTongue.EN

    @Before fun pair() {
        L10n.pair = LangPair(Lang.EN, Lang.DE)
    }

    @After fun back() {
        L10n.pair = pair
    }

    private val table = mapOf(
        0 to "zero", 1 to "one", 2 to "two", 3 to "three", 8 to "eight", 10 to "ten", 11 to "eleven", 12 to "twelve", 13 to "thirteen", 14 to "fourteen",
        15 to "fifteen", 18 to "eighteen", 19 to "nineteen", 20 to "twenty", 21 to "twenty-one", 27 to "twenty-seven", 30 to "thirty", 40 to "forty",
        44 to "forty-four", 50 to "fifty", 80 to "eighty", 90 to "ninety", 99 to "ninety-nine", 100 to "one hundred", 101 to "one hundred and one",
        105 to "one hundred and five", 110 to "one hundred and ten", 120 to "one hundred and twenty", 250 to "two hundred and fifty", 300 to "three hundred",
        999 to "nine hundred and ninety-nine", 1000 to "one thousand", 1001 to "one thousand and one", 1100 to "one thousand one hundred",
        1234 to "one thousand two hundred and thirty-four", 1950 to "one thousand nine hundred and fifty", 2000 to "two thousand",
        2005 to "two thousand and five", 2026 to "two thousand and twenty-six", 9999 to "nine thousand nine hundred and ninety-nine",
    )

    @Test fun `the words of the hand-checked table`() {
        for ((n, w) in table) assertEquals("$n", w, en.words(n))
        assertEquals("twenty-three thousand", SpokenNumbers.english(23_000))
    }

    @Test fun `speech's forms up to 999 are the same words`() {
        for ((n, w) in table) if (n <= 999) assertEquals("$n", w, SpokenNumbers.forms(n, Lang.EN).first())
    }

    @Test fun `the ordinals a date takes`() {
        val ordinals = mapOf(
            1 to "first", 2 to "second", 3 to "third", 4 to "fourth", 5 to "fifth", 8 to "eighth", 9 to "ninth", 11 to "eleventh", 12 to "twelfth",
            13 to "thirteenth", 20 to "twentieth", 21 to "twenty-first", 22 to "twenty-second", 23 to "twenty-third", 25 to "twenty-fifth", 30 to "thirtieth",
            31 to "thirty-first",
        )
        for ((n, w) in ordinals) assertEquals("$n", w, SpokenNumbers.englishOrdinal(n))
    }

    @Test fun `every number 0 to 9999 reads back and its other forms are no other number's`() {
        for (n in 0..9999) {
            assertEquals("$n", n, en.parse(en.words(n)))
            for (w in en.also(n)) assertEquals(w, n, en.parse(w))
        }
        assertTrue("a hundred and five" in en.also(105))
        assertTrue("one hundred five" in en.also(105))
        assertTrue("a thousand" in en.also(1000))
        assertTrue("twenty twenty-six" in en.also(2026))
        assertTrue("two thousand twenty-six" in en.also(2026))
        assertTrue("nineteen oh five" in en.also(1905))
        assertTrue("nineteen hundred" in en.also(1900))
        assertEquals(emptyList<String>(), en.also(27))
    }

    private fun state(unit: String? = "en/numbers") = GameState(
        age = Age.ZASELEK,
        course = CourseProgress(units = if (unit != null) mapOf(unit to UnitProgress(intro = 6, introDone = true)) else emptyMap()),
    )

    @Test fun `the unit numbers opens it, another language's doesn't`() {
        assertTrue(NumbersPractice.open(state(), en))
        assertNull(NumbersPractice.lock(state(), en))
        assertFalse(NumbersPractice.open(state(null), en))
        assertFalse(NumbersPractice.open(state("it/numeri"), en))
        assertEquals(NumbersPractice.Lock.INTRO, NumbersPractice.lock(state(null), en))
        assertFalse(NumbersPractice.open(state(), NumberTongue.SL))
        assertEquals(en, NumberTongue.of("en"))
    }

    @Test fun `dates and the clock wait for the unit time's page`() {
        val first = NumbersPractice.modes(canSay = true, canListen = true, introduced = setOf("numbers"), tongue = en)
        assertFalse(NumberMode.DATE in first || NumberMode.TIME_WRITE in first || NumberMode.TIME_HEAR in first)
        val later = NumbersPractice.modes(canSay = true, canListen = false, introduced = setOf("numbers", "time-dates"), tongue = en)
        assertTrue(NumberMode.DATE in later && NumberMode.TIME_WRITE in later && NumberMode.TIME_HEAR in later)
    }

    private fun graded(mode: NumberMode, n: Int, answer: String, month: Int? = null): NumberGraded =
        NumbersPractice.grade(NumbersPractice.exercise(NumberTask(mode, n, month), en) as Exercise.Numeral, answer)

    @Test fun `a number heard or read, German's order against English's`() {
        assertEquals(Verdict.CORRECT, graded(NumberMode.HEAR, 27, "27").verdict)
        val swapped = graded(NumberMode.HEAR, 27, "72")
        assertEquals(Verdict.WRONG, swapped.verdict)
        assertEquals(NumberKind.ORDER, swapped.kind)
        assertTrue(swapped.why!!, swapped.why!!.contains("twenty-seven") && swapped.why!!.contains("Zehner"))
        assertEquals(NumberKind.LISTENING, graded(NumberMode.HEAR, 27, "26").kind)
        assertEquals(NumberKind.READING, graded(NumberMode.READ, 27, "26").kind)
    }

    @Test fun `a number's word, its hyphen, and, forty, the ones first, another number`() {
        assertEquals(Verdict.CORRECT, graded(NumberMode.WRITE, 27, "twenty-seven").verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.WRITE, 27, "twenty seven").verdict)
        assertEquals(Verdict.ALMOST, graded(NumberMode.WRITE, 27, "twentyseven").verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.WRITE, 105, "one hundred and five").verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.WRITE, 105, "a hundred and five").verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.WRITE, 105, "one hundred five").verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.WRITE, 2026, "twenty twenty-six").verdict)
        assertEquals(Verdict.ALMOST, graded(NumberMode.WRITE, 19, "nineten").verdict)
        val forty = graded(NumberMode.WRITE, 40, "fourty")
        assertEquals(Verdict.WRONG, forty.verdict)
        assertEquals(NumberKind.SPELLING, forty.kind)
        assertTrue(forty.why!!, forty.why!!.contains("ohne u"))
        assertEquals(Verdict.WRONG, graded(NumberMode.WRITE, 42, "fourty-two").verdict)
        val ones = graded(NumberMode.WRITE, 27, "seven twenty")
        assertEquals(Verdict.WRONG, ones.verdict)
        assertEquals(NumberKind.ORDER, ones.kind)
        assertEquals(NumberKind.ORDER, graded(NumberMode.WRITE, 27, "seven and twenty").kind)
        // another number's word is never a slip
        assertEquals(Verdict.WRONG, graded(NumberMode.WRITE, 13, "thirty").verdict)
        assertEquals(Verdict.WRONG, graded(NumberMode.WRITE, 26, "twenty-seven").verdict)
        assertEquals(NumberKind.SPELLING, graded(NumberMode.WRITE, 26, "twenty-seven").kind)
    }

    @Test fun `a date takes the ordinal, the irregular ones their own`() {
        assertEquals("Today is the ___ of May. (15th)", en.dateFrame(15, 5))
        assertEquals("Today is the ___ of March. (1st)", en.dateFrame(1, 3))
        assertEquals("Today is the ___ of June. (22nd)", en.dateFrame(22, 6))
        assertEquals("Today is the ___ of June. (11th)", en.dateFrame(11, 6))
        assertEquals("Today is the third of March.", en.dateSaid(3, 3))
        for (a in listOf("fifteenth", "the fifteenth", "fifteenth of May", "the fifteenth of May", "Today is the fifteenth of May.", "May the fifteenth")) {
            assertEquals(a, Verdict.CORRECT, graded(NumberMode.DATE, 15, a, 5).verdict)
        }
        assertEquals(Verdict.CORRECT, graded(NumberMode.DATE, 1, "first", 5).verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.DATE, 20, "twentieth", 5).verdict)
        for (a in listOf("fiveth", "nineth", "twelveth", "the fiveth of May")) {
            val day = when { "five" in a -> 5; "nine" in a -> 9; else -> 12 }
            val g = graded(NumberMode.DATE, day, a, 5)
            assertEquals(a, Verdict.WRONG, g.verdict)
            assertEquals(a, NumberKind.DATE, g.kind)
        }
        assertEquals(Verdict.WRONG, graded(NumberMode.DATE, 20, "twentyth", 5).verdict)
        assertEquals(Verdict.WRONG, graded(NumberMode.DATE, 1, "one", 5).verdict)
        assertEquals(Verdict.WRONG, graded(NumberMode.DATE, 12, "eleventh", 5).verdict)
        assertEquals("15th May", NumbersPractice.shownOf(NumberTask(NumberMode.DATE, 15, 5), en))
    }

    @Test fun `the clock with at, half past the hour that is there`() {
        assertEquals("at half past five", en.clockWords(530))
        assertEquals("at one", en.clockWords(100))
        assertEquals("at twelve", en.clockWords(1200))
        assertEquals(Verdict.CORRECT, graded(NumberMode.TIME_HEAR, 530, "5:30").verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.TIME_HEAR, 530, "17:30").verdict)
        val german = graded(NumberMode.TIME_HEAR, 530, "4:30")
        assertEquals(Verdict.WRONG, german.verdict)
        assertEquals(NumberKind.TIME_HALF, german.kind)
        assertEquals(NumberKind.TIME, graded(NumberMode.TIME_HEAR, 530, "8:30").kind)
        for (a in listOf("at half past five", "half past five", "half five", "at half five", "five thirty", "at five thirty")) {
            assertEquals(a, Verdict.CORRECT, graded(NumberMode.TIME_WRITE, 530, a).verdict)
        }
        for (a in listOf("at five", "five o'clock", "at five o'clock", "five")) assertEquals(a, Verdict.CORRECT, graded(NumberMode.TIME_WRITE, 500, a).verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.TIME_WRITE, 1200, "at noon").verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.TIME_WRITE, 1200, "at midday").verdict)
        for (a in listOf("at half past six", "half six")) {
            val halb = graded(NumberMode.TIME_WRITE, 530, a)
            assertEquals(a, Verdict.WRONG, halb.verdict)
            assertEquals(a, NumberKind.TIME_HALF, halb.kind)
        }
        // another hour is never a slip
        assertEquals(Verdict.WRONG, graded(NumberMode.TIME_WRITE, 700, "at six").verdict)
        assertEquals(Verdict.WRONG, graded(NumberMode.TIME_WRITE, 1200, "at eleven").verdict)
    }

    @Test fun `each why says what is English's own, in the base`() {
        assertTrue(en.numberWhy(27), en.numberWhy(27).contains("Zehner zuerst"))
        assertTrue(en.numberWhy(44), en.numberWhy(44).contains("ohne u"))
        assertTrue(en.numberWhy(16), en.numberWhy(16).contains("-teen"))
        assertTrue(en.numberWhy(12), en.numberWhy(12).contains("eigene"))
        assertTrue(en.numberWhy(245), en.numberWhy(245).contains("and"))
        assertTrue(en.numberWhy(2026), en.numberWhy(2026).contains("twenty twenty-six"))
        assertTrue(en.clockWhy(530), en.clockWhy(530).contains("halb 6") && en.clockWhy(530).contains("half five"))
        assertTrue(en.dateWhy(15, 5), en.dateWhy(15, 5).contains("the fifteenth of May") && en.dateWhy(15, 5).contains("15th"))
        for (k in NumberKind.entries) assertFalse(en.describe(k), en.describe(k).contains("Italian"))
    }

    @Test fun `a learner from Slovene reads Slovene, from Italian Italian`() {
        L10n.pair = LangPair(Lang.EN, Lang.SL)
        assertTrue(en.numberWhy(27), en.numberWhy(27).contains("drugače kot v slovenščini"))
        assertTrue(en.clockWhy(530), en.clockWhy(530).contains("ne k naslednji"))
        L10n.pair = LangPair(Lang.EN, Lang.IT)
        assertTrue(en.clockWhy(530), en.clockWhy(530).contains("come e mezza"))
    }

    @Test fun `what the tutor hears says its language`() {
        val task = NumberTask(NumberMode.WRITE, 27)
        val ex = NumbersPractice.exercise(task, en)
        val rec = NumbersPractice.record(null, true, listOf(task), listOf(Verdict.WRONG), java.time.LocalDate.of(2026, 11, 3))
        val wire = NumbersPractice.wire(rec, true, listOf(task), listOf(ex), listOf("seven twenty" to NumberKind.ORDER), listOf(Verdict.WRONG), setOf(NumberMode.WRITE), java.time.LocalDate.of(2026, 11, 3), en)
        assertTrue(wire.toString(), wire.toString().contains("\"language\":\"en\"") && wire.toString().contains("numbers_order") && wire.toString().contains("English says the tens first"))
    }
}
