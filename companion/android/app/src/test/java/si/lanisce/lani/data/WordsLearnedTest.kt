package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "You know N words" and the village's ages count words learned: answered right at least once. */
class WordsLearnedTest {
    private val state = """
        {"databases": {
          "learner_profile": {"learner": {"name": "Jan", "current_level": "A1", "target_level": "B1"}},
          "spaced_repetition": {"items": {
            "vocab_hvala": {"type": "vocabulary", "content": "hvala", "answer": "thanks", "repetitions": 2},
            "vocab_prosim": {"type": "vocabulary", "content": "prosim", "answer": "please", "repetitions": 0, "mastery_level": 1},
            "vocab_ampak": {"type": "vocabulary", "content": "ampak", "answer": "but", "repetitions": 0, "mastery_level": 0},
            "vocab_nov": {"type": "vocabulary", "content": "nov", "answer": "new"},
            "rule_moj": {"type": "grammar_rule", "content": "moj / moja", "answer": "my", "repetitions": 3},
            "missing_je": {"type": "error_pattern", "content": "moja partnerka iz Gorice", "answer": "je", "repetitions": 1}}}},
         "computed": {"today": "2026-09-24"}}
    """

    @Test fun `learned words are the vocabulary answered right at least once`() {
        val d = Dashboard.parse(state)
        assertEquals(4, d.wordsTracked) // every vocabulary card under review
        assertEquals(2, d.wordsLearned) // repetitions ≥ 1 or mastery ≥ 1; grammar rules and error patterns aren't words
        assertEquals("B1", d.targetLevel)
    }

    @Test fun `rusty words are the cards whose last review failed, and they don't count as learned`() {
        val d = Dashboard.parse(
            """
            {"databases": {"spaced_repetition": {"items": {
              "vocab_hvala": {"type": "vocabulary", "content": "hvala", "answer": "thanks", "repetitions": 2, "total_reviews": 2, "last_quality": 4},
              "vocab_kruh": {"type": "vocabulary", "content": "kruh", "answer": "bread", "repetitions": 0, "total_reviews": 3, "last_quality": 1, "mastery_level": 1},
              "vocab_sir": {"type": "vocabulary", "content": "sir", "answer": "cheese", "repetitions": 0, "total_reviews": 1, "last_quality": 2, "source": "lookup"},
              "vocab_med": {"type": "vocabulary", "content": "med", "repetitions": 0, "total_reviews": 1, "last_quality": 0},
              "rule_moj": {"type": "grammar_rule", "content": "moj / moja", "answer": "my", "repetitions": 0, "total_reviews": 1, "last_quality": 1}}}},
             "computed": {"today": "2026-09-26"}}
            """,
        )
        // kruh failed its last review; sir was looked up (never learned); med has no answer to show; a rule isn't a word
        assertEquals(listOf("vocab_kruh"), d.rusty.map { it.id })
        assertEquals(1, d.wordsLearned) // hvala: kruh is rusty, whatever its mastery
        assertTrue(d.rusty.single().rustsAt(1))
        assertTrue(!d.rusty.single().rustsAt(3))
        assertTrue(!ReviewCard("x", "sir", "cheese", lookup = true).rustsAt(1))
    }

    @Test fun `the learned words are known by id, for a building's upgrade`() {
        val d = Dashboard.parse(state)
        assertEquals(setOf("vocab_hvala", "vocab_prosim"), d.learned)
    }

    @Test fun `review answers count at once, a word not learned yet learned, a rusty one polished`() {
        val d = Dashboard.parse(
            """
            {"databases": {"spaced_repetition": {"items": {
              "vocab_hvala": {"type": "vocabulary", "content": "hvala", "answer": "thanks", "repetitions": 2, "total_reviews": 2, "last_quality": 4},
              "vocab_kruh": {"type": "vocabulary", "content": "kruh", "answer": "bread", "repetitions": 0, "total_reviews": 3, "last_quality": 1},
              "vocab_v-kuhinji_sol": {"type": "vocabulary", "content": "sol", "answer": "salt", "repetitions": 0, "total_reviews": 0, "last_quality": 2},
              "rule_moj": {"type": "grammar_rule", "content": "moj / moja", "answer": "my", "repetitions": 0, "last_quality": 1}}}},
             "computed": {"today": "2026-09-29"}}
            """,
        )
        assertEquals(setOf("vocab_hvala"), d.learned)
        assertEquals(1, d.wordsLearned)
        // wrong, or nothing new: the same dashboard
        assertTrue(d.reviewed(listOf("vocab_kruh" to 2, "vocab_v-kuhinji_sol" to 1)) === d)
        assertTrue(d.reviewed(listOf("vocab_hvala" to 5, "rule_moj" to 4)) === d)
        val n = d.reviewed(listOf("vocab_kruh" to 4, "vocab_v-kuhinji_sol" to 3))
        assertEquals(setOf("vocab_hvala", "vocab_kruh", "vocab_v-kuhinji_sol"), n.learned)
        assertTrue(n.rusty.isEmpty())
        assertEquals(3, n.wordsLearned)
    }

    @Test fun `words learned in the app count at once, the ones the learner has already stay`() {
        val d = Dashboard.parse(state)
        val words = listOf(PackWord("kruh", sl = "kruh", en = "bread"), PackWord("sol", sl = "sol", en = "salt"), PackWord("hvala", sl = "Hvala!", en = "thanks"))
        val n = d.learnedWords("v-kuhinji", words, listOf("kruh" to 4, "sol" to 2, "hvala" to 4))
        // kruh right: learned; sol wrong: met, not learned; hvala: had already (lesson 1's card, the same word)
        assertEquals(setOf("vocab_hvala", "vocab_prosim", "vocab_v-kuhinji_kruh"), n.learned)
        assertEquals(d.wordsLearned + 1, n.wordsLearned)
        assertEquals(d.wordsTracked + 2, n.wordsTracked)
        assertEquals(listOf("vocab_v-kuhinji_kruh", "vocab_v-kuhinji_sol"), n.pool.map { it.id }.filter { it.startsWith("vocab_v-kuhinji") })
        assertEquals(2, n.pool.first { it.id == "vocab_v-kuhinji_sol" }.lastQuality)
        // again: nothing new
        assertTrue(n.learnedWords("v-kuhinji", words, listOf("kruh" to 5)) === n)
    }

    @Test fun `a dashboard built without the count takes the tracked one`() {
        val d = Dashboard("Jan", "A1", "B2", 0, 0, 0, wordsTracked = 12, dueCards = emptyList(), weakPatterns = emptyList())
        assertEquals(12, d.wordsLearned)
    }

    @Test fun `words to reach a level`() {
        assertEquals(300, Stats.wordsToReach("A2"))
        assertEquals(1000, Stats.wordsToReach("B1"))
        assertEquals(2000, Stats.wordsToReach("B2"))
        assertEquals(3500, Stats.wordsToReach("C1"))
        assertEquals(5000, Stats.wordsToReach("C2"))
        assertEquals(300, Stats.wordsToReach("A1")) // nothing below it: counts as A2
        assertEquals(2000, Stats.wordsToReach(" b2 "))
        assertEquals(2000, Stats.wordsToReach("fluent")) // unknown: the default target, B2
    }
}
