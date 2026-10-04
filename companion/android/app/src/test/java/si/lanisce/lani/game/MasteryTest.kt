package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.MistakeNote
import si.lanisce.lani.data.ReviewCard
import java.time.LocalDate

/**
 * How far the learner has a rule (companion/GAME.md, "Mastery and adaptive turns"): new → learning → secure → mastered,
 * from the answers on it, the grammar cards and the mistakes; a slip steps it back; and the turn's mode it leads to.
 */
class MasteryTest {
    private val day = LocalDate.of(2026, 9, 20)
    private val s0 = GameEngine.newGame(7, Fixtures.noon(day))

    /** [n] answers on the dual, right or not, one a day from [from] (or all on one day). */
    private fun answers(s: GameState, vararg right: Boolean, from: LocalDate = day, daily: Boolean = true): GameState =
        right.foldIndexed(s) { i, acc, r ->
            GrammarBook.answered(acc, "sl", "dvojina", r, if (r) "Midva sva doma." else null, null, if (daily) from.plusDays(i.toLong()) else from).first
        }

    private fun record(s: GameState) = GrammarBook.met(s, "sl")["dvojina"]
    private fun level(s: GameState, mistakes: List<MistakeNote> = emptyList(), cards: List<ReviewCard> = emptyList()) =
        Masteries.of(record(s), cards, mistakes)

    @Test fun `a rule nothing was practised on is new, and its turns are chosen`() {
        assertEquals(Mastery.NEW, Masteries.of(null, emptyList(), emptyList()))
        assertEquals(TurnMode.CHOOSE, Masteries.mode(listOf(Mastery.NEW), canSay = true))
        // a page met but never answered is still new
        assertEquals(Mastery.NEW, level(GrammarBook.meet(s0, "sl", listOf("dvojina"), day).first))
    }

    @Test fun `learning, then secure after four right in a row, then mastered after eight over more than a day`() {
        val three = answers(s0, true, true, true)
        assertEquals(Mastery.LEARNING, level(three))
        val four = answers(three, true, from = day.plusDays(3))
        assertEquals(4, record(four)?.run)
        assertEquals(Mastery.SECURE, level(four))
        val eight = answers(four, true, true, true, true, from = day.plusDays(4))
        assertEquals(Mastery.MASTERED, level(eight))
        // eight in one sitting: secure, not mastered (it is kept over days)
        assertEquals(Mastery.SECURE, level(answers(s0, *BooleanArray(8) { true }, daily = false)))
    }

    @Test fun `too few stars keep it learning even with a run`() {
        // five wrong, then four right: 4 of 9, two stars
        val s = answers(s0, false, false, false, false, false, true, true, true, true)
        assertEquals(4, record(s)?.streak)
        assertEquals(Mastery.LEARNING, level(s))
    }

    @Test fun `a slip steps the mastery back a level`() {
        val mastered = answers(s0, *BooleanArray(8) { true })
        assertEquals(Mastery.MASTERED, level(mastered))
        val once = answers(mastered, false, from = day.plusDays(8))
        assertEquals(Masteries.SECURE_RUN, record(once)?.run)
        assertEquals(Mastery.SECURE, level(once))
        val twice = answers(once, false, from = day.plusDays(9))
        assertEquals(0, record(twice)?.run)
        assertEquals(Mastery.LEARNING, level(twice))
        // right again from there: a new run
        val back = answers(twice, true, true, true, true, from = day.plusDays(10))
        assertEquals(day.plusDays(10).toString(), record(back)?.since)
        assertEquals(Mastery.SECURE, level(back))
    }

    @Test fun `an open mistake on the rule keeps it learning, one fixed since doesn't`() {
        val s = answers(s0, true, true, true, true, from = day.plusDays(5))
        assertEquals(Mastery.SECURE, level(s))
        // the mistake happened on a day of the run (or after it began): still open
        val during = MistakeNote("dual_verb_forms", 1, wrongRun = 1, lastSeen = day.plusDays(6).toString())
        assertEquals(Mastery.LEARNING, level(s, listOf(during)))
        // before the run began: the run fixed it
        val before = during.copy(lastSeen = day.toString())
        assertEquals(Mastery.SECURE, level(s, listOf(before)))
        // its review card answered right on a later day: fixed too
        assertEquals(Mastery.SECURE, level(s, listOf(during.copy(reviewedRight = true))))
        assertFalse(during.copy(reviewedRight = true).open)
        // a mistake with no day known stays open
        assertEquals(Mastery.LEARNING, level(s, listOf(during.copy(lastSeen = null))))
    }

    @Test fun `a record from before runs were kept reads its answers as the run when none was wrong`() {
        val old = RuleRecord("2026-09-01", right = 7)
        assertEquals(7, old.streak)
        assertEquals("2026-09-01", old.runSince)
        assertEquals(0, RuleRecord("2026-09-01", right = 7, wrong = 1).streak)
        assertEquals(Mastery.SECURE, Masteries.of(old, emptyList(), emptyList()))
        // the next answer keeps counting from there
        val s = s0.copy(grammar = mapOf("sl/dvojina" to old))
        val r = record(answers(s, true, from = day))!!
        assertEquals(8, r.run)
        assertEquals("2026-09-01", r.since)
        assertEquals(day.toString(), r.last)
        assertEquals(Mastery.MASTERED, Masteries.of(r, emptyList(), emptyList()))
    }

    @Test fun `grammar cards alone make a rule learning or secure only with a run in the app`() {
        val card = ReviewCard("vocab_biti_dual", "midva sva", "we two are", kind = "grammar", category = "grammar_biti", repetitions = 5, lastQuality = 5)
        assertEquals(Mastery.LEARNING, Masteries.of(null, listOf(card), emptyList()))
    }

    @Test fun `the turn's mode follows its weakest rule, and saying needs something that listens`() {
        assertEquals(TurnMode.CHOOSE, Masteries.mode(listOf(Mastery.LEARNING), true))
        assertEquals(TurnMode.TYPE, Masteries.mode(listOf(Mastery.SECURE), true))
        assertEquals(TurnMode.SAY, Masteries.mode(listOf(Mastery.MASTERED), true))
        assertEquals(TurnMode.TYPE, Masteries.mode(listOf(Mastery.MASTERED), false))
        assertEquals(TurnMode.TYPE, Masteries.mode(listOf(Mastery.MASTERED, Mastery.SECURE), true))
        assertEquals(TurnMode.CHOOSE, Masteries.mode(listOf(Mastery.MASTERED, Mastery.NEW), true))
        assertEquals(TurnMode.CHOOSE, Masteries.mode(emptyList(), true))
    }

    /** A right answer on the dual after the turn's hint ("📖 Namig"), on [on]. */
    private fun hinted(s: GameState, on: LocalDate) = GrammarBook.answered(s, "sl", "dvojina", true, "Midva greva.", null, on, hinted = true).first

    @Test fun `a right answer after the hint counts right and keeps its sentence, but leaves the run alone`() {
        val three = answers(s0, true, true, true)
        val helped = hinted(three, day.plusDays(3))
        val r = record(helped)!!
        assertEquals(4, r.right) // it counts right: the stars see it
        assertEquals("Midva greva.", r.said.last())
        assertEquals(3, r.run) // neither longer …
        assertEquals(record(three)?.since, r.since)
        assertEquals(record(three)?.last, r.last) // … nor on a later day: the days of the run are the answers without help
        assertEquals(Mastery.LEARNING, level(helped)) // three in a row and a hint: not secure yet
        // the next answer without help goes on with the run
        val four = answers(helped, true, from = day.plusDays(4))
        assertEquals(4, record(four)?.run)
        assertEquals(Mastery.SECURE, level(four))
        // hints alone never make a rule secure
        val helpedOnly = (0L until 8L).fold(s0) { s, d -> hinted(s, day.plusDays(d)) }
        assertEquals(0, record(helpedOnly)?.run)
        assertEquals(Mastery.LEARNING, level(helpedOnly))
    }

    @Test fun `a hint breaks no run, and a slip after it steps back as always`() {
        val eight = answers(s0, *BooleanArray(8) { true })
        val helped = hinted(eight, day.plusDays(8))
        assertEquals(8, record(helped)?.run)
        assertEquals(Mastery.MASTERED, level(helped))
        val slip = answers(helped, false, from = day.plusDays(9))
        assertEquals(Masteries.SECURE_RUN, record(slip)?.run)
        // a record from before runs were kept: the hint writes its run out, so its right answers don't grow it
        val legacy = s0.copy(grammar = mapOf("sl/dvojina" to RuleRecord(day.toString(), right = 3)))
        val after = GrammarBook.met(hinted(legacy, day.plusDays(1)), "sl").getValue("dvojina")
        assertEquals(4, after.right)
        assertEquals(3, after.streak)
        assertEquals(day.toString(), after.runSince)
    }

    @Test fun `the meter no longer counts a mistake the run fixed`() {
        val s = answers(s0, true, true, true, true, from = day.plusDays(5))
        val m = MistakeNote("dual_verb_forms", 1, wrongRun = 1, lastSeen = day.toString())
        assertEquals(5, GrammarBook.meter(record(s), emptyList(), listOf(m)))
        assertTrue(GrammarBook.stillOpen(m, null))
        assertFalse(GrammarBook.stillOpen(m, record(s)))
    }
}
