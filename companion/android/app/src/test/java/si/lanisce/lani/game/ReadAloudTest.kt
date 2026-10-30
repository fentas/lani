package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * The founding screen and the news sheet read aloud ([ReadAloud]; ui/game/FoundingScreen, NewsSheet): Teo's Italian from
 * German as Jan's Slovene from English, what the screen shows, each piece in its language, nothing an emoji.
 */
class ReadAloudTest {
    private val itDe = LangPair(Lang.IT, Lang.DE)

    @Test fun `the founding says its title in the language learned and what to do in the base`() {
        val s = ReadAloud.founding(itDe)
        assertEquals(listOf(true, false, false), s.map { it.target })
        assertEquals("Dove sarà il tuo villaggio?", s[0].text)
        assertEquals(L10n.text(Lang.DE, "founding.about"), s[1].text)
        assertEquals(L10n.text(Lang.DE, "founding.once"), s[2].text)
        assertEquals(listOf("Kje bo tvoja vas?", L10n.text(Lang.EN, "founding.about"), L10n.text(Lang.EN, "founding.once")), ReadAloud.founding(LangPair.DEFAULT).map { it.text })
        // in every pair, from each language's own table
        val pairs = Lang.entries.flatMap { t -> Lang.entries.filter { it != t }.map { LangPair(t, it) } }
        for (pair in pairs) {
            val f = ReadAloud.founding(pair)
            assertEquals("$pair", 3, f.size)
            for (p in f) assertTrue("$pair: ${p.text}", L10n.table(if (p.target) pair.target else pair.base).values.contains(p.text) && '{' !in p.text)
        }
    }

    @Test fun `a chronicle line is the language learned, then the base, without its emoji`() {
        assertEquals(
            listOf(HintPiece("Volkovi so prišli ponoči", true), HintPiece("The wolves came at night", false)),
            ReadAloud.line("🐺 Volkovi so prišli ponoči · The wolves came at night"),
        )
        // a name alone, or a line in one language: the language learned's
        assertEquals(listOf(HintPiece("Mojca", true)), ReadAloud.line("Mojca"))
        assertEquals(listOf(HintPiece("Ana", true)), ReadAloud.line("Ana · Ana"))
        // halves that hold a " · " each: cut at the middle one
        assertEquals(
            listOf(HintPiece("Ana, Bine", true), HintPiece("Ana, Bine too", false)),
            ReadAloud.line("Ana · Bine · Ana · Bine too"),
        )
        // an odd count: the extra goes to the base (the phone's voice reads it)
        assertEquals(listOf(true, false), ReadAloud.line("Zala: Dvojina · Zala: The dual · two of us").map { it.target })
        assertEquals(emptyList<HintPiece>(), ReadAloud.line("🎉 · ✨"))
        assertEquals("Danes je 5 °C", ReadAloud.spoken("☀️ Danes je 5 °C ❄️"))
        assertEquals("Babica Micka", ReadAloud.spoken("👵🏻‍🦳 Babica Micka"))
    }

    @Test fun `the news sheet says its heading, then the newest lines it shows`() {
        val lines = (1..10).map { "📜 Vrstica $it · Line $it" }
        val s = ReadAloud.news(lines, LangPair.DEFAULT)
        assertEquals(listOf("Medtem ko te ni bilo", "While you were away"), s.take(2).map { it.text })
        // the sheet's eight: 3 to 10
        assertEquals((3..10).flatMap { listOf("Vrstica $it", "Line $it") }, s.drop(2).map { it.text })
        assertEquals(listOf("Mentre non c'eri", "Während du weg warst"), ReadAloud.news(emptyList(), itDe).map { it.text })
        // the same line twice in a row (two gifts alike): said twice, each its own
        assertEquals(6, ReadAloud.news(listOf("🎁 Darilo · A gift", "🎁 Darilo · A gift"), LangPair.DEFAULT).size)
    }
}
