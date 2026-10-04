package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.GameSnapshot
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.MistakeNote
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.json

/**
 * The grammar book's chapter in the village book (companion/GAME.md, "The grammar book"): a page unlocks the first time
 * its rule is met, the answers on it count, the chapter lists the pages met, and the meter says how well a rule sits.
 */
class GrammarBookTest {
    private val day = Fixtures.day0
    private val pages = Grammar.bundled("sl")
    private val s0 = GameEngine.newGame(7, Fixtures.noon(day))

    @Test fun `a page unlocks the first time its rule is met, on that day, and only once`() {
        val (s1, fresh) = GrammarBook.meet(s0, "sl", listOf("kje-mestnik-orodnik", "rodilnik-predlogi", "kje-mestnik-orodnik"), day)
        assertEquals(listOf("kje-mestnik-orodnik", "rodilnik-predlogi"), fresh)
        assertEquals(day.toString(), GrammarBook.met(s1, "sl").getValue("kje-mestnik-orodnik").on)
        assertEquals(setOf("sl/kje-mestnik-orodnik", "sl/rodilnik-predlogi"), s1.grammar.keys)
        // met again the next day: nothing new, the day it unlocked stays
        val (s2, again) = GrammarBook.meet(s1, "sl", listOf("kje-mestnik-orodnik"), day.plusDays(1))
        assertEquals(emptyList<String>(), again)
        assertEquals(s1, s2)
        // another language's book is another book
        assertTrue(GrammarBook.met(s1, "it").isEmpty())
        assertFalse(GrammarBook.isMet(s1, "it", "kje-mestnik-orodnik"))
    }

    @Test fun `an answer on a rule meets its page, counts, and keeps the latest sentences and slips`() {
        val (s1, first) = GrammarBook.answered(s0, "sl", "tozilnik", right = true, said = "Pijem kavo.", slip = null, today = day)
        assertTrue(first)
        var s = s1
        for (i in 1..4) s = GrammarBook.answered(s, "sl", "tozilnik", right = true, said = "Vidim brata $i.", slip = null, today = day).first
        val (s2, again) = GrammarBook.answered(s, "sl", "tozilnik", right = false, said = null, slip = RuleSlip("Pijem kava.", "Pijem kavo."), today = day)
        assertFalse(again)
        val r = GrammarBook.met(s2, "sl").getValue("tozilnik")
        assertEquals(5, r.right)
        assertEquals(1, r.wrong)
        assertEquals(listOf("Vidim brata 2.", "Vidim brata 3.", "Vidim brata 4."), r.said)
        assertEquals(listOf(RuleSlip("Pijem kava.", "Pijem kavo.")), r.slips)
        // the same sentence again moves to the end, it isn't kept twice
        val r2 = GrammarBook.met(GrammarBook.answered(s2, "sl", "tozilnik", true, "Vidim brata 2.", null, day).first, "sl").getValue("tozilnik")
        assertEquals(listOf("Vidim brata 3.", "Vidim brata 4.", "Vidim brata 2."), r2.said)
    }

    @Test fun `the chapter lists the pages met in the order met, and how many more there are`() {
        val s1 = GrammarBook.meet(s0, "sl", listOf("tozilnik", "biti"), day).first
        val s2 = GrammarBook.meet(s1, "sl", listOf("kje-mestnik-orodnik"), day.plusDays(2)).first
        val c = GrammarBook.chapter(pages, s2, "sl")
        // the same day in the book's order, then the later one
        assertEquals(listOf("biti", "tozilnik", "kje-mestnik-orodnik"), c.met.map { it.id })
        assertEquals(pages.size - 3, c.more)
        assertEquals(GrammarBook.Chapter(emptyList(), pages.size), GrammarBook.chapter(pages, null, "sl"))
        // a page met that the book has no more (a tutor's, taken back) doesn't show
        val s3 = GrammarBook.meet(s2, "sl", listOf("gone"), day).first
        assertEquals(3, GrammarBook.chapter(pages, s3, "sl").met.size)
    }

    private fun card(id: String, repetitions: Int, last: Int) = ReviewCard(id, "x", "y", kind = "grammar_rule", category = "grammar_gender", repetitions = repetitions, lastQuality = last)

    @Test fun `the meter weighs the answers, the grammar cards and the open mistakes on a rule`() {
        assertNull(GrammarBook.meter(null, emptyList(), emptyList()))
        // a mistake alone: nothing right yet
        assertEquals(0, GrammarBook.meter(null, emptyList(), listOf(MistakeNote("x", 1, wrongRun = 1))))
        assertEquals(5, GrammarBook.meter(RuleRecord("d", right = 4), emptyList(), emptyList()))
        assertEquals(3, GrammarBook.meter(RuleRecord("d", right = 3, wrong = 2), emptyList(), emptyList()))
        // cards: how far their reviews are (a failed last review counts 1)
        assertEquals(5, GrammarBook.meter(null, listOf(card("a", 5, 5)), emptyList()))
        assertEquals(1, GrammarBook.meter(null, listOf(card("a", 0, 2)), emptyList()))
        // eight answers all right, one card failed: mostly the answers
        assertEquals(5, GrammarBook.meter(RuleRecord("d", right = 8), listOf(card("a", 0, 2)), emptyList()))
        // open mistakes take one off each, two at most; a mistake fixed since (right answers in a row) doesn't
        val open = List(3) { MistakeNote("m$it", 1, wrongRun = 1) }
        assertEquals(3, GrammarBook.meter(RuleRecord("d", right = 4), emptyList(), open))
        assertEquals(5, GrammarBook.meter(RuleRecord("d", right = 4), emptyList(), listOf(MistakeNote("m", 3, rightRun = 2))))
    }

    @Test fun `the village keeps the pages met through the node, and a village from before has none`() {
        val s = GrammarBook.answered(s0, "sl", "dvojina", true, "Midva sva doma.", null, day).first
        val back = json.decodeFromString(GameSnapshot.serializer(), json.encodeToString(GameSnapshot.serializer(), GameSnapshot(3, s)))
        assertEquals(s.grammar, back.state.grammar)
        val raw = json.encodeToString(GameSnapshot.serializer(), GameSnapshot(3, s0))
        assertFalse("an empty book isn't written", raw.contains("\"grammar\""))
        assertTrue(json.decodeFromString(GameSnapshot.serializer(), raw).state.grammar.isEmpty())
    }
}
