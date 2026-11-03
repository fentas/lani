package si.lanisce.lani.data

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

/**
 * The numbers module in Italian (data/NumberTongue.kt, SpokenNumbers.italian), for a learner from German: the words against a
 * hand-checked table of the tricky ones, every number read back, when it opens (the unit numeri, not Slovene's stevila), the
 * ways of asking (ore-giorni brings the dates and the clock), and the grading: one word, the accent of -tré, the tens first
 * against German's ones first, a date's plain number, half past the hour that is there.
 */
class NumbersItalianTest {
    private val pair = L10n.pair
    private val it = NumberTongue.IT

    @Before fun pair() {
        L10n.pair = LangPair(Lang.IT, Lang.DE)
    }

    @After fun back() {
        L10n.pair = pair
    }

    private val table = mapOf(
        0 to "zero", 1 to "uno", 2 to "due", 3 to "tre", 9 to "nove", 10 to "dieci", 11 to "undici", 12 to "dodici", 13 to "tredici", 14 to "quattordici",
        15 to "quindici", 16 to "sedici", 17 to "diciassette", 18 to "diciotto", 19 to "diciannove", 20 to "venti", 21 to "ventuno", 22 to "ventidue",
        23 to "ventitré", 27 to "ventisette", 28 to "ventotto", 30 to "trenta", 31 to "trentuno", 33 to "trentatré", 38 to "trentotto", 40 to "quaranta",
        50 to "cinquanta", 60 to "sessanta", 70 to "settanta", 80 to "ottanta", 81 to "ottantuno", 90 to "novanta", 99 to "novantanove", 100 to "cento",
        101 to "centouno", 103 to "centotré", 108 to "centotto", 110 to "centodieci", 120 to "centoventi", 123 to "centoventitré", 180 to "centottanta",
        200 to "duecento", 203 to "duecentotré", 245 to "duecentoquarantacinque", 300 to "trecento", 888 to "ottocentottantotto", 999 to "novecentonovantanove",
        1000 to "mille", 1001 to "milleuno", 1003 to "milletré", 1100 to "millecento", 1950 to "millenovecentocinquanta", 2000 to "duemila",
        2023 to "duemilaventitré", 2026 to "duemilaventisei", 3000 to "tremila", 9999 to "novemilanovecentonovantanove",
    )

    @Test fun `the words of the hand-checked table`() {
        for ((n, w) in table) assertEquals("$n", w, it.words(n))
        assertEquals("ventitremila", SpokenNumbers.italian(23_000))
    }

    @Test fun `speech's forms up to 999 are the same words, with centotré's accent`() {
        for ((n, w) in table) if (n <= 999) assertEquals("$n", w, SpokenNumbers.forms(n, Lang.IT).first())
    }

    @Test fun `every number 0 to 9999 reads back and its other forms are no other number's`() {
        for (n in 0..9999) {
            assertEquals("$n", n, it.parse(it.words(n)))
            for (w in it.also(n)) assertEquals(w, n, it.parse(w))
        }
        assertEquals(listOf("una", "un"), it.also(1))
        assertTrue("centootto" in it.also(108))
        assertTrue("duemila e ventisei" in it.also(2026))
    }

    private fun state(unit: String? = "it/numeri") = GameState(
        age = Age.ZASELEK,
        course = CourseProgress(units = if (unit != null) mapOf(unit to UnitProgress(intro = 6, introDone = true)) else emptyMap()),
    )

    @Test fun `the unit numeri opens it, Slovene's stevila doesn't, German has none yet`() {
        assertTrue(NumbersPractice.open(state(), it))
        assertNull(NumbersPractice.lock(state(), it))
        assertFalse(NumbersPractice.open(state(null), it))
        assertFalse(NumbersPractice.open(state("sl/stevila"), it))
        assertEquals(NumbersPractice.Lock.INTRO, NumbersPractice.lock(state(null), it))
        assertFalse(NumbersPractice.open(state(), NumberTongue.SL))
        assertNull(NumberTongue.of("de"))
        assertNotNull(NumberTongue.of("en"))
        assertNotNull(NumberTongue.of("sl"))
    }

    @Test fun `dates and the clock wait for ore-giorni`() {
        val first = NumbersPractice.modes(canSay = true, canListen = true, introduced = setOf("numeri"), tongue = it)
        assertFalse(NumberMode.DATE in first || NumberMode.TIME_WRITE in first || NumberMode.TIME_HEAR in first)
        val later = NumbersPractice.modes(canSay = true, canListen = false, introduced = setOf("numeri", "ore-giorni"), tongue = it)
        assertTrue(NumberMode.DATE in later && NumberMode.TIME_WRITE in later && NumberMode.TIME_HEAR in later)
    }

    private fun graded(mode: NumberMode, n: Int, answer: String, month: Int? = null): NumberGraded =
        NumbersPractice.grade(NumbersPractice.exercise(NumberTask(mode, n, month), it) as Exercise.Numeral, answer)

    @Test fun `a number heard or read, German's order against Italian's`() {
        assertEquals(Verdict.CORRECT, graded(NumberMode.HEAR, 27, "27").verdict)
        val swapped = graded(NumberMode.HEAR, 27, "72")
        assertEquals(Verdict.WRONG, swapped.verdict)
        assertEquals(NumberKind.ORDER, swapped.kind)
        assertTrue(swapped.why!!, swapped.why!!.contains("ventisette") && swapped.why!!.contains("Zehner"))
        assertEquals(NumberKind.LISTENING, graded(NumberMode.HEAR, 27, "26").kind)
        assertEquals(NumberKind.READING, graded(NumberMode.READ, 27, "26").kind)
    }

    @Test fun `a number's word as one word, -tré's accent, the ones first, another number`() {
        assertEquals(Verdict.CORRECT, graded(NumberMode.WRITE, 27, "ventisette").verdict)
        assertEquals(Verdict.ALMOST, graded(NumberMode.WRITE, 27, "venti sette").verdict)
        assertEquals(Verdict.ALMOST, graded(NumberMode.WRITE, 23, "ventitre").verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.WRITE, 108, "centootto").verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.WRITE, 1, "una").verdict)
        assertEquals(Verdict.ALMOST, graded(NumberMode.WRITE, 19, "dicianove").verdict)
        val ones = graded(NumberMode.WRITE, 27, "sette venti")
        assertEquals(Verdict.WRONG, ones.verdict)
        assertEquals(NumberKind.ORDER, ones.kind)
        assertEquals(NumberKind.ORDER, graded(NumberMode.WRITE, 27, "sette e venti").kind)
        // another number's word is never a slip
        assertEquals(Verdict.WRONG, graded(NumberMode.WRITE, 12, "undici").verdict)
        assertEquals(Verdict.WRONG, graded(NumberMode.WRITE, 26, "ventisette").verdict)
        assertEquals(NumberKind.SPELLING, graded(NumberMode.WRITE, 26, "ventisette").kind)
    }

    @Test fun `a date takes the plain number with il, the first primo, l'otto and l'undici`() {
        assertEquals("Oggi è il ___ maggio. (15)", it.dateFrame(15, 5))
        assertEquals("Oggi è l'___ maggio. (8)", it.dateFrame(8, 5))
        assertEquals("Oggi è l'undici marzo.", it.dateSaid(11, 3))
        assertEquals("Oggi è il primo marzo.", it.dateSaid(1, 3))
        for (a in listOf("quindici", "il quindici", "quindici maggio", "il quindici maggio", "Oggi è il quindici maggio.")) {
            assertEquals(a, Verdict.CORRECT, graded(NumberMode.DATE, 15, a, 5).verdict)
        }
        assertEquals(Verdict.CORRECT, graded(NumberMode.DATE, 1, "primo", 5).verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.DATE, 8, "otto", 5).verdict)
        val one = graded(NumberMode.DATE, 1, "uno", 5)
        assertEquals(Verdict.WRONG, one.verdict)
        assertEquals(NumberKind.DATE, one.kind)
        assertEquals(Verdict.WRONG, graded(NumberMode.DATE, 12, "undici", 5).verdict)
        assertEquals("15 maggio", NumbersPractice.shownOf(NumberTask(NumberMode.DATE, 15, 5), it))
    }

    @Test fun `the clock with alle, half past the hour that is there`() {
        assertEquals("alle cinque e mezza", it.clockWords(530))
        assertEquals("all'una", it.clockWords(100))
        assertEquals("all'una e mezza", it.clockWords(130))
        assertEquals("alle dodici", it.clockWords(1200))
        assertEquals(Verdict.CORRECT, graded(NumberMode.TIME_HEAR, 530, "5:30").verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.TIME_HEAR, 530, "17:30").verdict)
        val german = graded(NumberMode.TIME_HEAR, 530, "4:30")
        assertEquals(Verdict.WRONG, german.verdict)
        assertEquals(NumberKind.TIME_HALF, german.kind)
        assertEquals(NumberKind.TIME, graded(NumberMode.TIME_HEAR, 530, "8:30").kind)
        for (a in listOf("alle cinque e mezza", "le cinque e mezza", "cinque e mezza", "alle cinque e mezzo", "alle cinque e trenta")) {
            assertEquals(a, Verdict.CORRECT, graded(NumberMode.TIME_WRITE, 530, a).verdict)
        }
        assertEquals(Verdict.CORRECT, graded(NumberMode.TIME_WRITE, 1200, "a mezzogiorno").verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.TIME_WRITE, 100, "all'una").verdict)
        val halb = graded(NumberMode.TIME_WRITE, 530, "alle sei e mezza")
        assertEquals(Verdict.WRONG, halb.verdict)
        assertEquals(NumberKind.TIME_HALF, halb.kind)
        // another hour is never a slip
        assertEquals(Verdict.WRONG, graded(NumberMode.TIME_WRITE, 700, "alle sei").verdict)
        assertEquals(Verdict.WRONG, graded(NumberMode.TIME_WRITE, 1200, "alle undici").verdict)
    }

    @Test fun `each why says what is Italian's own, in the base`() {
        assertTrue(it.numberWhy(27), it.numberWhy(27).contains("Zehner zuerst"))
        assertTrue(it.numberWhy(28), it.numberWhy(28).contains("ventotto") && it.numberWhy(28).contains("Vokal"))
        assertTrue(it.numberWhy(23), it.numberWhy(23).contains("Akzent"))
        assertTrue(it.numberWhy(16), it.numberWhy(16).contains("-dici"))
        assertTrue(it.numberWhy(245), it.numberWhy(245).contains("einem einzigen Wort"))
        assertTrue(it.clockWhy(530), it.clockWhy(530).contains("halb 6"))
        assertTrue(it.dateWhy(15, 5), it.dateWhy(15, 5).contains("il quindici maggio"))
        for (k in NumberKind.entries) assertFalse(it.describe(k), it.describe(k).contains("Slovene"))
    }

    @Test fun `what the tutor hears says its language`() {
        val task = NumberTask(NumberMode.WRITE, 27)
        val ex = NumbersPractice.exercise(task, it)
        val rec = NumbersPractice.record(null, true, listOf(task), listOf(Verdict.WRONG), java.time.LocalDate.of(2026, 11, 2))
        val wire = NumbersPractice.wire(rec, true, listOf(task), listOf(ex), listOf("sette venti" to NumberKind.ORDER), listOf(Verdict.WRONG), setOf(NumberMode.WRITE), java.time.LocalDate.of(2026, 11, 2), it)
        assertTrue(wire.toString(), wire.toString().contains("\"language\":\"it\"") && wire.toString().contains("numbers_order") && wire.toString().contains("Italian says the tens first"))
    }
}
