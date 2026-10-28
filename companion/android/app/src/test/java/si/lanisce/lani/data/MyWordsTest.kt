package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * "📚 Moje besede · My words" (data/MyWords.kt): the learner's words from their cards (vocabulary only, one per word, the
 * learned ones as Napredek counts them), in Slovene's order (č after c, its own letter), the newest on top, a search
 * that filters them as it is typed (č or c, the word or its meaning) and names what to look up in the dictionary.
 */
class MyWordsTest {
    private val today = LocalDate.of(2026, 10, 18)
    private fun card(id: String, front: String, back: String, created: LocalDate? = null, kind: String = "vocabulary") =
        ReviewCard(id, front, back, kind, created = created)

    private val pool = listOf(
        card("v_hisa", "hiša", "house", today.minusDays(40)),
        card("v_cesta", "cesta", "road", today.minusDays(30)),
        card("v_caj", "čaj", "tea", today.minusDays(2)),
        card("v_cvet", "cvet", "flower", today.minusDays(20)),
        card("v_zlica", "žlica", "spoon", today.minusDays(1)),
        card("v_zima", "zima", "winter", today.minusDays(60)),
        card("v_dober_dan", "dober dan", "good day, hello", today.minusDays(90)),
        card("v_sir", "sir", "cheese", today.minusDays(5)),
        card("v_sola", "šola", "school", today.minusDays(3)),
        card("v_avto", "avto", "car", today),
        // the same word twice (a pack's and one added from a dialog): one row, the learned one
        card("pack_miza", "miza", "table", today.minusDays(10)),
        card("vocab_word_miza", "Miza", "table (a dialog's)", today.minusDays(1)),
        // not words: a counting card, a forms card, a card without its word
        card("counting_miza", "miza", "ena miza · dve mizi", kind = Counting.KIND),
        card("forms_miza", "miza", "mize · mizi", kind = FormsCard.KIND),
        card("x", "  ", "nothing"),
    )
    private val learned = setOf("v_hisa", "v_cesta", "v_caj", "v_cvet", "v_zlica", "v_zima", "v_dober_dan", "v_sir", "pack_miza")
    private val words = MyWords.of(pool, learned)

    @Test fun `the learner's words - vocabulary cards only, one per word (the learned one), in Slovene's order, c before č`() {
        assertEquals(
            listOf("avto", "cesta", "cvet", "čaj", "dober dan", "hiša", "miza", "sir", "šola", "zima", "žlica"),
            words.map { it.word },
        )
        val miza = words.single { it.word.lowercase() == "miza" }
        assertEquals("pack_miza", miza.id)
        assertTrue(miza.learned)
        assertFalse(words.single { it.word == "šola" }.learned) // still being learned
        assertEquals(9, words.count { it.learned }) // as Napredek counts them
    }

    @Test fun `the letters - č, š and ž their own, after c, s and z, another accent under its letter, a digit under #, last`() {
        assertEquals("Č", MyWords.letter("čaj"))
        assertEquals("C", MyWords.letter("Cesta"))
        assertEquals("C", MyWords.letter("ćevapčiči"))
        assertEquals("#", MyWords.letter("3D"))
        assertTrue(MyWords.sortKey("cvet") < MyWords.sortKey("čaj"))
        assertTrue(MyWords.sortKey("čaj") < MyWords.sortKey("ćevapčiči"))
        assertTrue(MyWords.sortKey("ćevapčiči") < MyWords.sortKey("dan"))
        assertTrue(MyWords.sortKey("zvon") < MyWords.sortKey("žaba"))
        val g = MyWords.groups(words + MyWord("n", "3D", "3D", true), today)
        assertEquals("#", g.last().key)
        assertEquals(listOf("C", "Č"), g.map { it.key }.filter { it == "C" || it == "Č" })
    }

    @Test fun `the list - the newest few on top (two weeks, at most eight, newest first), then every word by its letter`() {
        val g = MyWords.groups(words, today)
        assertTrue(g.first().recent)
        assertEquals(listOf("avto", "žlica", "čaj", "šola", "sir", "miza"), g.first().words.map { it.word })
        assertEquals(listOf("A", "C", "Č", "D", "H", "M", "S", "Š", "Z", "Ž"), g.drop(1).map { it.key })
        assertEquals(listOf("cesta", "cvet"), g.first { it.key == "C" }.words.map { it.word })
        // every word under its letter, the recent ones too
        assertEquals(words.size, g.drop(1).sumOf { it.words.size })
        // a short list has no newest on top: it would be the list twice
        assertTrue(MyWords.groups(words.take(5), today).none { it.recent })
        assertTrue(MyWords.groups(emptyList(), today).isEmpty())
    }

    @Test fun `the search - č, š, ž typed or not, the word first, then a word of it, then within it, then its meaning`() {
        assertEquals(listOf("žlica"), MyWords.filter(words, "zli").map { it.word })
        assertEquals(listOf("čaj"), MyWords.filter(words, "Čaj").map { it.word })
        assertEquals(listOf("cesta", "cvet", "čaj"), MyWords.filter(words, "c").map { it.word })
        // "dan": a word of "dober dan"; "ola": within šola
        assertEquals(listOf("dober dan"), MyWords.filter(words, "dan").map { it.word })
        assertEquals(listOf("šola"), MyWords.filter(words, "ola").map { it.word })
        // from two letters on, within a word and the meaning: "house" is hiša; "ta": cesta, then table by its meaning
        assertEquals(listOf("hiša"), MyWords.filter(words, "hou").map { it.word })
        assertEquals(listOf("cesta", "miza"), MyWords.filter(words, "ta").map { it.word })
        assertEquals(emptyList<String>(), MyWords.filter(words, "x").map { it.word }) // one letter: the words' beginnings only
        assertEquals(words, MyWords.filter(words, "  "))
    }

    @Test fun `the dictionary is asked for a word that isn't one of the learner's as it is - a form of one too, never a number or a sign`() {
        assertEquals("pes", MyWords.lookupFor(" pes ", words))
        assertEquals("hiše", MyWords.lookupFor("hiše", words)) // a form of hiša: the dictionary names its word
        assertEquals("dober večer", MyWords.lookupFor("dober   večer", words))
        assertNull(MyWords.lookupFor("hiša", words)) // theirs: the list shows it
        assertNull(MyWords.lookupFor("HISA", words)) // č, š, ž or not
        assertNull(MyWords.lookupFor("p", words))
        assertNull(MyWords.lookupFor("x".repeat(41), words))
        assertNull(MyWords.lookupFor("3 jabolka", words))
        assertNull(MyWords.lookupFor("pes?", words))
        assertEquals("rock'n'roll", MyWords.lookupFor("rock'n'roll", words))
    }

    /**
     * A learner of some months, 2,000 words (Jan's Home froze on its first frame: the sort compiled a regular expression
     * for every letter of every comparison, seconds on a phone; docs/plans/05-built/home-return.md): the list in a few
     * milliseconds, in the same order as [MyWords.ORDER] gives it.
     */
    @Test fun `two thousand words are listed in a few milliseconds, in Slovene's order`() {
        val rnd = java.util.Random(7)
        val letters = "abcčdefghijklmnoprsštuvzž"
        val big = List(2000) { i ->
            val w = String(CharArray(3 + rnd.nextInt(7)) { letters[rnd.nextInt(letters.length)] })
            card("v_$i", w, "meaning $i", today.minusDays(rnd.nextInt(90).toLong()))
        }
        val learnedBig = big.filter { rnd.nextBoolean() }.map { it.id }.toSet()
        // a copy each time: the same lists are given back the list made the last time
        repeat(3) { MyWords.of(big.toList(), learnedBig.toSet()) }
        val copy = big.toList()
        val t0 = System.nanoTime()
        val listed = MyWords.of(copy, learnedBig)
        val ms = (System.nanoTime() - t0) / 1e6
        assertEquals(listed.sortedWith(MyWords.ORDER), listed)
        val groups = MyWords.groups(listed, today)
        assertEquals(listed.size, groups.filterNot { it.recent }.sumOf { it.words.size })
        println("2,000 words listed in %.1f ms".format(ms))
        assertTrue("$ms ms", ms < si.lanisce.lani.FrameBudget.scaled(60.0))
    }

    @Test fun `the same deck again gives the list made the last time, another deck its own`() {
        val learned = setOf("v_cesta", "v_caj")
        val first = MyWords.of(pool, learned)
        assertSame("Home composed anew after a Back asks again: nothing sorted again", first, MyWords.of(pool, learned))
        val more = pool + card("v_kolo", "kolo", "bicycle", today)
        val other = MyWords.of(more, learned)
        assertNotSame(first, other)
        assertEquals(first.size + 1, other.size)
        val fewer = MyWords.of(pool, setOf("v_cesta"))
        assertNotSame(first, fewer)
        assertTrue(first.single { it.word == "čaj" }.learned)
        assertFalse(fewer.single { it.word == "čaj" }.learned)
    }
}
