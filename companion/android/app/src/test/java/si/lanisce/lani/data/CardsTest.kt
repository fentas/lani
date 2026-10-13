package si.lanisce.lani.data

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cheat sheet's own cards (lani.card/v0, companion/README.md "Cheat sheet"): a curated one and the tutor's as the
 * bridge serves them, the bundled ones (each of its book's pages, in every base), and the bridge's merged with them.
 */
class CardsTest {
    private fun parse(raw: String) = Cards.parse(json.parseToJsonElement(raw) as JsonObject)

    @Test fun `a curated card, its title per language, rule line, table, examples, tip, pages and start`() {
        val c = parse(
            """{"schema":"lani.card/v0","id":"zaimki-osebe","language":"sl","level":"A1","emoji":"👥",
            "title":{"sl":"Jaz, midva, mi","en":"I, we two, we"},"rule":{"en":"Slovene counts **one, two, three+**."},
            "table":{"head":[{"en":"who"},{"en":"1"},{"en":"2"}],"rows":[[{"en":"I/we"},"jaz","midva"]]},
            "examples":[{"sl":"Midva greva domov.","en":"The two of us are going home."}],"tip":{"en":"💡 vi is polite too"},
            "grammar":["osebni-zaimki","dvojina"],"start":true,"machine_written":true,"review":"not reviewed by a native speaker","source":"curated"}""",
        )!!
        assertEquals("zaimki-osebe", c.id)
        assertEquals("Jaz, midva, mi", c.title["sl"])
        assertEquals(1, c.table!!.rows.size)
        assertTrue(c.table!!.rows[0][1].isWords && !c.table!!.rows[0][0].isWords)
        assertEquals("Midva greva domov.", c.examples.single().target("sl"))
        assertEquals(listOf("osebni-zaimki", "dvojina"), c.grammar)
        assertTrue(c.start && !c.byTutor)
        assertNull(c.markdown)
    }

    @Test fun `the tutor's card, a plain title and its Markdown`() {
        val c = parse("""{"id":"imeti","title":"Imeti · imam, imaš","markdown":"| a | b |\n|---|---|\n| **imam** | **imamo** |","grammar":["imeti-iti"],"level":"A1","source":"tutor"}""")!!
        assertTrue(c.byTutor)
        assertEquals("Imeti · imam, imaš", c.title.values.single())
        assertTrue(c.markdown!!.contains("**imam**"))
        assertNull(c.rule)
        // a card without a face, or a title, is none
        assertNull(parse("""{"id":"x","title":"X","level":"A1"}"""))
        assertNull(parse("""{"id":"x","markdown":"- a","level":"A1"}"""))
    }

    @Test fun `the bridge's cards first, then the bundled ones it lacks, an older bridge's none leaves the bundled`() {
        val a = DeckCard("a", "sl", "A1", "📋", mapOf("en" to "A"), rule = mapOf("en" to "a"))
        val b = DeckCard("b", "sl", "A1", "📋", mapOf("en" to "B"), rule = mapOf("en" to "b"))
        val mine = DeckCard("mine", "sl", "A1", "📋", mapOf("" to "Mine"), markdown = "- x", source = Grammar.TUTOR)
        assertEquals(listOf(a, b), Cards.merge(listOf(a, b), null))
        assertEquals(listOf("b", "mine", "a"), Cards.merge(listOf(a, b), listOf(b, mine)).map { it.id })
    }

    @Test fun `every bundled card names pages of its book and reads in every base, as every page's own card`() {
        val bases = mapOf("sl" to listOf("en", "de", "it"), "it" to listOf("sl", "en", "de"), "de" to listOf("sl", "en", "it"), "en" to listOf("sl", "it", "de"))
        for ((lang, bs) in bases) {
            val pages = Grammar.bundled(lang)
            val ids = pages.map { it.id }.toSet()
            for (c in Cards.bundled(lang)) {
                assertTrue("${c.id}: ${c.grammar}", c.grammar.isNotEmpty() && c.grammar.all { it in ids })
                assertTrue(c.id, c.language == lang && !c.byTutor && c.machineWritten)
                assertTrue(c.id, (listOf(lang) + bs).all { c.title.containsKey(it) } && bs.all { c.rule!!.containsKey(it) })
                assertTrue(c.id, c.examples.size <= 3 && c.examples.all { e -> (listOf(lang) + bs).all { e.texts.containsKey(it) } })
            }
            for (p in pages) p.card?.let { own ->
                assertTrue(p.id, bs.all { own.rule.containsKey(it) } && (own.tip == null || bs.all { own.tip.containsKey(it) }))
                assertTrue(p.id, own.examples.orEmpty().size in 0..3 && own.examples.orEmpty().all { e -> (listOf(lang) + bs).all { e.texts.containsKey(it) } })
            }
        }
    }
}
