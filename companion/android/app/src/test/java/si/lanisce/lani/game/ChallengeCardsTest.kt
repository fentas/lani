package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import kotlin.random.Random

/**
 * A village challenge asks a card as the reviews do (wishlist 2026-10-25: "👩‍🏫 Napake na tabli" asked the grammar card
 * vocab_biti_dual typed, its three forms joined by " · "): what the reviews quiz is picked, typed, built or heard; a
 * grammar card, a rule or a list is a flashcard there too, never a typed translation.
 */
class ChallengeCardsTest {
    private val t0 = noon(day0)

    private val dual = ReviewCard("vocab_biti_dual", "midva sva · vidva sta · onadva sta", "we two are · you two are · they two are", kind = "grammar")
    private val forms = ReviewCard("vocab_dober_forms", "dober · dobra · dobro", "good (m · f · n)") // a word's forms as a list
    private val rule = ReviewCard("vocab_nemcija", "Nemčija → iz Nemčije", "Germany → from Germany")
    private val cards = Fixtures.cards + dual + forms

    private fun quest(skill: Res, ids: List<String>) = Quest(
        id = "q-2026-10-24-2", giver = "Učiteljica Mojca", emoji = "👩‍🏫", title = "Napake na tabli", story = "…", skill = skill,
        reward = mapOf(skill to 30), cardIds = ids,
    )

    /** What [c] asks about [card] in it: the exercises built from that card. */
    private fun asked(c: Challenge, card: ReviewCard): List<Exercise> = c.exercises.filterIndexed { i, _ -> c.cardIds[i] == card.id }

    /** What [e] asks to type or build, and what it takes: its prompt and its answers (not its instruction's "sl · en"). */
    private fun typed(e: Exercise): List<String>? = when (e) {
        is Exercise.Translate -> listOf(e.prompt) + e.accept
        is Exercise.Reorder -> listOf(e.prompt) + e.tokens + e.distractors + e.solutions.flatten()
        is Exercise.Cloze -> listOf(e.text) + e.accept
        is Exercise.Dictation -> listOf(e.audio) + e.accept
        else -> null
    }

    /** No list asked typed or built, none of its words (nor its dots) among the chips, none offered to pick. */
    private fun noList(c: Challenge, why: String) {
        for (e in c.exercises) {
            typed(e)?.let { texts -> assertTrue("$why: $e", texts.none { "·" in it }) }
            if (e is Exercise.Choice) assertTrue("$why: $e", e.options.none { " · " in it } && (e.audio == null || " · " !in e.audio))
        }
    }

    @Test fun `the reviews quiz a word, never a grammar card, a rule or a list`() {
        assertTrue(Fixtures.cards.first().let(ReviewPlanner::quizzes))
        assertFalse(ReviewPlanner.quizzes(dual))
        assertFalse(ReviewPlanner.quizzes(forms)) // a word, but a list of its forms
        assertFalse(ReviewPlanner.quizzes(rule))
        assertFalse(ReviewPlanner.quizzes(ReviewCard("g", "hvala", "thanks", kind = "grammar")))
        // a note with dots inside its brackets is no list
        assertTrue(ReviewPlanner.quizzes(ReviewCard("v", "vroče (je)", "it's hot (the weather: Danes je vroče. · vroč = hot)")))
        // in a review, each of them is a flashcard
        val tasks = ReviewPlanner.plan(listOf(dual, forms, rule), cards, canSpeak = true, random = Random(1))
        assertEquals(listOf(ReviewPlanner.Variant.FLIP), tasks.map { it.variant }.distinct())
        // and never a distractor of another word's question
        repeat(30) { seed ->
            val t = ReviewPlanner.plan(Fixtures.cards.take(4), cards, canSpeak = true, random = Random(seed))
            val shown = t.flatMap { (it.exercise as? Exercise.Choice)?.options.orEmpty() }
            assertTrue(shown.none { " · " in it })
        }
    }

    @Test fun `a request's grammar card is a flashcard in its challenge, the words are still typed or built`() {
        for (skill in Res.entries) for (seed in 0L..20L) {
            val ids = listOf("vocab_dober_dan", "vocab_hvala", dual.id, "vocab_ni_za_kaj", "vocab_od_kod_ste", forms.id)
            val s = GameEngine.newGame(seed, t0).copy(quests = listOf(quest(skill, ids)))
            val c = GameEngine.questChallenge(s, "q-2026-10-24-2", ContentPool(cards, canSpeak = seed % 2 == 0L))!!
            for (card in listOf(dual, forms)) {
                val e = asked(c, card)
                assertTrue("$skill $seed ${card.id}: $e", e.all { it is Exercise.Flashcard && it.front == card.front && it.back == card.back })
            }
            assertEquals(c.exercises.size, c.cardIds.size)
            noList(c, "$skill $seed")
        }
    }

    @Test fun `a grammar request of grammar cards alone keeps all its questions, as flashcards`() {
        val grammar = listOf(dual, ReviewCard("vocab_moj", "moj · moja · moje", "my (m · f · n)", kind = "grammar"), ReviewCard("vocab_ta", "ta · ta · to", "this", kind = "grammar"))
        for (skill in Res.entries) {
            val s = GameEngine.newGame(3, t0).copy(quests = listOf(quest(skill, grammar.map { it.id })))
            val c = GameEngine.questChallenge(s, "q-2026-10-24-2", ContentPool(Fixtures.cards + grammar, canSpeak = true))!!
            assertEquals("$skill", grammar.map { it.id }.toSet(), c.cardIds.toSet())
            assertTrue("$skill", c.exercises.all { it is Exercise.Flashcard })
        }
    }

    @Test fun `a gathering run asks a grammar card or a list as a flashcard, if at all`() {
        for (res in Res.entries) for (seed in 0L..10L) {
            val s = GameEngine.newGame(seed, t0).copy(lastTick = "2026-09-0${1 + seed % 9}")
            val c = GameEngine.gatherChallenge(s, res, ContentPool(cards, canSpeak = seed % 2 == 0L))
            for (card in listOf(dual, forms)) assertTrue("$res $seed ${card.id}", asked(c, card).all { it is Exercise.Flashcard })
            noList(c, "$res $seed")
        }
    }
}
