package si.lanisce.lani.ui.words

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordTokensTest {
    private fun words(line: String) = WordTokens.of(line).map { it.text }

    @Test fun `a line's words, without its punctuation`() {
        assertEquals(listOf("Dober", "večer", "Si", "lačen"), words("Dober večer! Si lačen?"))
        assertEquals(listOf("Ja", "zelo"), words("„Ja, zelo.“"))
        assertEquals(listOf("Hvala", "lepa"), words("«Hvala lepa!»"))
    }

    @Test fun `each word knows where it is in the line`() {
        val t = WordTokens.of("Dober večer! Si lačen?")
        assertEquals(WordToken("Dober", 0, 5), t[0])
        assertEquals(WordToken("večer", 6, 11), t[1])
        assertEquals(WordToken("lačen", 16, 21), t[3])
        t.forEach { assertEquals(it.text, "Dober večer! Si lačen?".substring(it.start, it.end)) }
    }

    @Test fun `all the Slovene and neighbouring letters are letters`() {
        assertEquals(listOf("Čaša", "žlica", "in", "đuveč", "Ćevapčići", "šššš"), words("Čaša, žlica in đuveč; Ćevapčići … šššš"))
    }

    @Test fun `a letter with a combining accent stays one word`() =
        assertEquals(listOf("čaj", "je", "vroč"), words("čaj je vroč."))

    @Test fun `an apostrophe or hyphen between letters is inside the word`() {
        assertEquals(listOf("l'acqua", "è", "fredda"), words("l'acqua è fredda"))
        assertEquals(listOf("dell’uomo"), words("dell’uomo"))
        assertEquals(listOf("e-pošta", "je", "prišla"), words("e-pošta je prišla"))
    }

    @Test fun `dashes, quotes and apostrophes at a word's edge are not`() {
        assertEquals(listOf("Ja", "reče", "babica"), words("– Ja - reče babica."))
        assertEquals(listOf("un", "po"), words("un po'"))
        assertEquals(listOf("rekel"), words("'rekel'"))
    }

    @Test fun `digits and emoji are not words`() {
        assertEquals(listOf("Ob", "uri"), words("Ob 7. uri"))
        val t = WordTokens.of("🎤 Dober dan 🍲")
        assertEquals(listOf("Dober", "dan"), t.map { it.text })
        assertEquals(3, t[0].start) // after the emoji (two chars) and the space
    }

    @Test fun `no words in punctuation alone`() {
        assertEquals(emptyList<String>(), words(""))
        assertEquals(emptyList<String>(), words("… — !?"))
    }

    @Test fun `the word at an offset, as a tap's position gives it`() {
        val t = WordTokens.of("Dober večer! Si lačen?")
        assertEquals("Dober", WordTokens.at(t, 0)?.text)
        assertEquals("večer", WordTokens.at(t, 8)?.text)
        assertEquals("večer", WordTokens.at(t, 11)?.text) // just after its last letter
        assertEquals("Si", WordTokens.at(t, 13)?.text)
        assertNull(WordTokens.at(t, 12)) // the space after "!"
        assertNull(WordTokens.at(t, 30))
    }
}
