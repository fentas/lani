package si.lanisce.lani.ui.grammar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.GrammarBook
import si.lanisce.lani.game.RuleRecord
import java.time.LocalDate

/** "📖 Slovnica · Grammar"'s index (GrammarBookScreen, Home's tile): the pages met by level, new ones of the week marked. */
class BookIndexTest {
    private fun page(id: String, level: String) = GrammarPage(id, "sl", level, "📘", mapOf("sl" to "Stran $id", "en" to "Page $id"), emptyMap())

    @Test
    fun `the pages met by level, the week's new first in each, with how well they sit`() {
        val today = LocalDate.parse("2026-10-27")
        val pages = listOf(page("biti", "A1"), page("dvojina", "A2"), page("rod", "A1"), page("sklon", "B1"))
        val s = GameState(
            grammar = mapOf(
                GrammarBook.key("sl", "dvojina") to RuleRecord("2026-10-25"),
                GrammarBook.key("sl", "biti") to RuleRecord("2026-09-01"),
                GrammarBook.key("sl", "rod") to RuleRecord("2026-10-26"),
            ),
        )
        val chapter = GrammarBook.chapter(pages, s, "sl")
        val index = BookIndex.of(chapter, s, "sl", meter = { if (it == "biti") 4 else null }, base = "en", today = today)
        assertEquals(listOf("rod", "biti", "dvojina"), index.rows.map { it.id })
        assertEquals(2, index.new)
        assertEquals(4, index.rows.first { it.id == "biti" }.stars)
        assertNull(index.rows.first { it.id == "rod" }.stars)
        assertEquals("Page rod", index.rows.first().base)
        assertEquals(1, index.more)
    }

    @Test
    fun `an empty book is empty, every page still to meet`() {
        val pages = listOf(page("biti", "A1"))
        val index = BookIndex.of(GrammarBook.chapter(pages, GameState(), "sl"), GameState(), "sl", { null }, "en")
        assertEquals(0, index.rows.size)
        assertEquals(1, index.more)
    }
}
