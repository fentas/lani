package si.lanisce.lani.data

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.MyWord as DialogWord
import si.lanisce.lani.game.MyWords as DialogMyWords
import si.lanisce.lani.game.WordBook
import si.lanisce.lani.game.scene.SceneObject
import kotlin.random.Random

/**
 * One spelling, two words (docs/plans/05-built/content-fixes-3.md): the kitchen's bowl (skleda) and the green's (krogla) are
 * marked `homonym` in their packs, so each is its own card, and neither of them counts as having the other.
 */
class HomonymTest {
    private val kitchen = PackWord(id = "bowl", en = "bowl", sl = "skleda", it = "scodella", de = "Schüssel", lang = "en", homonym = true)
    private val green = PackWord(id = "lawn-bowl", en = "bowl", sl = "krogla", it = "boccia", de = "Kugel", lang = "en", homonym = true)
    private val kitchenCard = ReviewCard("vocab_in-the-kitchen_bowl", "bowl", "skleda", repetitions = 3)

    private fun dashboard(vararg items: String) = Dashboard.parse(
        """{"databases": {"learner_profile": {"learner": {"name": "Ana", "current_level": "A1", "target_level": "B1"}},
            "spaced_repetition": {"items": {${items.joinToString()}}}}, "computed": {"today": "2026-11-02"}}""",
    )

    @Test fun `a pack with the key reads it, one without it and an unknown key read as before`() {
        val marked = parsePack("""{"id":"p","title":"P","language":"en","level":"A1","words":[{"id":"bowl","en":"bowl","sl":"skleda","homonym":true}]}""")
        assertTrue(marked.words.single().homonym)
        assertEquals(setOf("skleda"), marked.words.single().meanings) // not "bowl": that is the word, in its own language
        val plain = parsePack("""{"id":"p","title":"P","language":"en","level":"A1","words":[{"id":"bowl","en":"bowl","sl":"skleda","future_key":1}]}""")
        assertFalse(plain.words.single().homonym)
    }

    @Test fun `a word's meanings - each part, what's in its brackets, an article left out, as the bridge compares them`() {
        assertEquals(setOf("fair", "market"), PackSession.meaningParts("fair, market"))
        assertEquals(setOf("giro", "di bevute"), PackSession.meaningParts("il giro (di bevute)"))
        assertEquals(setOf("runde"), PackSession.meaningParts("die Runde"))
        assertEquals(setOf("grow", "moon: to wax"), PackSession.meaningParts("to grow (the moon: to wax)"))
        assertTrue(PackSession.means("the fair", setOf("fair")))
        assertFalse(PackSession.means("I sow", setOf("fair", "market")))
    }

    @Test fun `the app's own count - the green's bowl is a new card beside the kitchen's, the kitchen's card untouched`() {
        val d = dashboard("""
            "vocab_in-the-kitchen_bowl": {"type": "vocabulary", "content": "bowl", "answer": "skleda", "repetitions": 3, "interval_days": 12, "due_date": "2026-11-20"}
        """)
        val before = d.pool.single()
        val after = d.learnedWords("on-the-bowling-green", listOf(green), listOf("lawn-bowl" to 5))
        assertEquals(listOf("vocab_in-the-kitchen_bowl", "vocab_on-the-bowling-green_lawn-bowl"), after.pool.map { it.id })
        assertEquals(before, after.pool.first())
        assertEquals("krogla", after.pool.last().back)
        assertEquals(before.id, d.pool.single().id)
    }

    @Test fun `a second pack's word of the same meaning is had, and a word without the mark still goes by its spelling`() {
        val d = dashboard("""
            "vocab_pod-mlajem_mlaj": {"type": "vocabulary", "content": "mlaj", "answer": "maypole", "repetitions": 1}
        """)
        val maypole = PackWord(id = "mlaj", sl = "mlaj", en = "maypole", it = "albero di maggio", de = "Maibaum", lang = "sl", homonym = true)
        val moon = PackWord(id = "mlaj", sl = "mlaj", en = "new moon", it = "luna nuova", de = "Neumond", lang = "sl", homonym = true)
        assertEquals(1, d.learnedWords("praznik-prvi-maj", listOf(maypole), listOf("mlaj" to 5)).pool.size)
        assertEquals(2, d.learnedWords("nebo", listOf(moon), listOf("mlaj" to 5)).pool.size)
        val unmarked = moon.copy(homonym = false)
        assertEquals(1, d.learnedWords("nebo", listOf(unmarked), listOf("mlaj" to 5)).pool.size)
    }

    @Test fun `a building's words - the kitchen's bowl has no card of the green's, a card that means it does`() {
        val book = WordBook(listOf(kitchenCard), setOf(kitchenCard.id), emptySet())
        assertNull(book.card("on-the-bowling-green", "lawn-bowl", "bowl", green.meanings))
        assertEquals(kitchenCard.id, book.card("in-the-kitchen", "bowl", "bowl", kitchen.meanings)?.id)
        assertEquals(kitchenCard.id, book.card("another-pack", "bowl", "bowl", kitchen.meanings)?.id)
        // a word without the mark (or a bridge from before it) goes by its spelling, as ever
        assertEquals(kitchenCard.id, book.card("on-the-bowling-green", "lawn-bowl", "bowl")?.id)
    }

    @Test fun `a scene's thing says it is marked, in the learner's base`() {
        val thing = SceneObject(slot = "ball", word = "lawn-bowl", sl = "bowl", en = "krogla", homonym = true)
        assertEquals(setOf("krogla"), thing.meanings)
        assertNull(SceneObject(slot = "ball", word = "lawn-bowl", sl = "bowl", en = "krogla").meanings)
    }

    @Test fun `a review never offers the other meaning as a wrong answer, nor another word that means the same as it`() {
        val greenCard = ReviewCard("vocab_on-the-bowling-green_lawn-bowl", "bowl", "krogla", repetitions = 3)
        val sphere = ReviewCard("vocab_x_sphere", "sphere", "krogla", repetitions = 3)
        val more = listOf("door" to "vrata", "cat" to "mačka", "dog" to "pes", "egg" to "jajce", "milk" to "mleko", "tree" to "drevo")
            .map { (f, b) -> ReviewCard("vocab_x_$f", f, b, repetitions = 3) }
        val pool = listOf(kitchenCard, greenCard, sphere) + more
        repeat(200) { seed ->
            val tasks = ReviewPlanner.plan(listOf(kitchenCard), pool, canSpeak = true, random = Random(seed))
            for (t in tasks) {
                val choice = t.exercise as? Exercise.Choice ?: continue
                assertFalse("krogla offered for the kitchen's bowl: ${choice.options}", "krogla" in choice.options)
                assertEquals(1, choice.options.count { it == "skleda" || it == "bowl" })
            }
        }
    }

    @Test fun `my words - two meanings are two rows, the same word twice with one meaning is one`() {
        val cards = listOf(
            kitchenCard,
            ReviewCard("vocab_on-the-bowling-green_lawn-bowl", "bowl", "krogla"),
            ReviewCard("vocab_a_hvala", "hvala", "thanks, thank you"),
            ReviewCard("vocab_b_hvala", "hvala", "thanks"),
        )
        val list = MyWords.of(cards, setOf(kitchenCard.id))
        assertEquals(listOf("krogla", "skleda"), list.filter { it.word == "bowl" }.map { it.meaning }.sorted())
        assertEquals(1, list.count { it.word == "hvala" })
    }

    @Test fun `a word card - the node's word, or its own card, makes a meaning theirs, the lemma alone doesn't`() {
        val mine = Words.Mine.of(listOf(kitchenCard), "en")
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val greens = json.decodeFromString<WordEntry>("""{"lemma":"bowl","item_id":"vocab_on-the-bowling-green_lawn-bowl","known":false,"homonym":true}""")
        assertFalse(Words.yours(greens, mine))
        assertTrue(Words.yours(greens, Words.Mine.of(listOf(kitchenCard, ReviewCard(greens.itemId!!, "bowl", "krogla")), "en")))
        assertTrue(Words.yours(greens.copy(known = true), mine))
        // a bridge from before the key: the lemma makes it theirs, as ever
        val old = json.decodeFromString<WordEntry>("""{"lemma":"bowl","item_id":"vocab_on-the-bowling-green_lawn-bowl","known":false}""")
        assertTrue(Words.yours(old, mine))
    }

    @Test fun `a dialog's turn counts for the card its translation says, for neither when it can't tell`() {
        val cards = DialogMyWords(listOf(DialogWord("k", "bowl", "skleda", setOf("bowl", "bowls")), DialogWord("g", "bowl", "krogla", setOf("bowl", "bowls"))))
        assertEquals("g", cards.of("bowls", "Wilf ima dve krogli.")?.id)
        assertEquals("k", cards.of("bowl", "Skledo kaše, prosim.")?.id)
        assertNull(cards.of("bowl", "Daj to sem."))
        assertNull(cards.of("bowl"))
        assertTrue(cards.has("bowl"))
        // one spelling on two cards of one meaning, or a word of one card: the first, as ever
        val one = DialogMyWords(listOf(DialogWord("a", "hvala", "thanks", setOf("hvala")), DialogWord("b", "hvala", "thanks", setOf("hvala"))))
        assertEquals("a", one.of("hvala")?.id)
        assertNotNull(DialogMyWords(listOf(DialogWord("a", "bowl", "skleda", setOf("bowl")))).of("bowl"))
    }
}
