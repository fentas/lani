package si.lanisce.lani.game

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.BridgeEvent
import si.lanisce.lani.data.Cards
import si.lanisce.lani.data.DeckCard
import si.lanisce.lani.data.GameCache
import si.lanisce.lani.data.GameSync
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.GrammarCell
import si.lanisce.lani.data.GrammarExample
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.data.GrammarTable
import si.lanisce.lani.data.PageCard
import si.lanisce.lani.data.json

/**
 * The cheat sheet ("📋 Plonk listek · Cheat sheet", companion/README.md "Cheat sheet"): a grammar page's card, a tutor
 * message pinned, what unlocks a card, the order of relevance, and the village keeping it all.
 */
class CheatSheetTest {
    private val day = Fixtures.day0
    private val s0 = GameEngine.newGame(7, Fixtures.noon(day))

    private fun page(id: String, level: String = "A1", card: PageCard? = null, table: GrammarTable? = null, rule: String = "**$id** is a rule. A second sentence.") = GrammarPage(
        id = id, language = "sl", level = level, emoji = "📖",
        title = mapOf("sl" to "Stran $id", "en" to "Page $id", "de" to "Seite $id"),
        rule = mapOf("en" to rule, "de" to "Eine Regel. Noch ein Satz."),
        table = table,
        examples = listOf(
            GrammarExample(mapOf("sl" to "Prvi.", "en" to "First.")),
            GrammarExample(mapOf("sl" to "Drugi.", "en" to "Second.")),
            GrammarExample(mapOf("sl" to "Tretji.", "en" to "Third.")),
        ),
        card = card,
    )

    private val table = GrammarTable(
        listOf(GrammarCell(text = mapOf("en" to "who")), GrammarCell(text = mapOf("en" to "imeti"))),
        listOf(listOf(GrammarCell("jaz"), GrammarCell("imam")), listOf(GrammarCell("ti"), GrammarCell("imaš")), listOf(GrammarCell("mi"), GrammarCell("imamo"))),
    )

    private fun deck(id: String, pages: List<String>, start: Boolean = false, source: String = Grammar.CURATED, markdown: String? = null, level: String = "A1") = DeckCard(
        id = id, language = "sl", level = level, emoji = "📋", title = mapOf("sl" to "Kartica $id", "en" to "Card $id"),
        rule = if (markdown == null) mapOf("en" to "**$id**") else null, markdown = markdown, grammar = pages, start = start, source = source,
    )

    // --- a grammar page's card -------------------------------------------------------------------------------------------

    @Test fun `a page without a card of its own gives one, its table, its rule's first sentence, two examples`() {
        val c = CheatSheet.of(page("imeti-iti", table = table, rule = "**Imeti** (to have) is regular: **imam, imaš, ima** (for two: **imava, imata**). Like biti, it fuses with ne."), "en")
        assertEquals("page:imeti-iti", c.key)
        assertEquals(SheetKind.PAGE, c.kind)
        assertEquals("Stran imeti-iti", c.title)
        assertEquals("Page imeti-iti", c.titleBase)
        assertEquals(listOf("imeti-iti"), c.pages)
        val f = c.face as CardFace.Rule
        assertEquals("**Imeti** (to have) is regular: **imam, imaš, ima** (for two: **imava, imata**).", f.rule)
        assertEquals(table, f.table)
        assertEquals(listOf("Prvi.", "Drugi."), f.examples.map { it.target("sl") })
        assertNull(f.tip)
        assertFalse(c.start)
        // explained in German: the German rule's first sentence, the German title
        val de = CheatSheet.of(page("imeti-iti"), "de")
        assertEquals("Eine Regel.", (de.face as CardFace.Rule).rule)
        assertEquals("Seite imeti-iti", de.titleBase)
    }

    @Test fun `a page's own card replaces it, the page filling in what the card leaves out`() {
        val own = PageCard(
            title = mapOf("sl" to "Biti · sem, si, je", "en" to "Biti · to be"),
            rule = mapOf("en" to "**sem, si, je** …", "de" to "**sem, si, je** … (de)"),
            examples = listOf(GrammarExample(mapOf("sl" to "Doma sem.", "en" to "I'm home."))),
            tip = mapOf("en" to "💡 ne + sem = **nisem**"),
            start = true,
        )
        val c = CheatSheet.of(page("biti", card = own, table = table), "en")
        val f = c.face as CardFace.Rule
        assertEquals("Biti · sem, si, je", c.title)
        assertEquals("Biti · to be", c.titleBase)
        assertEquals("**sem, si, je** …", f.rule)
        assertEquals(table, f.table) // none of its own: the page's
        assertEquals(listOf("Doma sem."), f.examples.map { it.target("sl") })
        assertEquals("💡 ne + sem = **nisem**", f.tip)
        assertTrue(c.start)
        assertEquals("**sem, si, je** … (de)", (CheatSheet.of(page("biti", card = own), "de").face as CardFace.Rule).rule)
    }

    @Test fun `a rule's first sentence, not inside bold, a long one cut at a word`() {
        assertEquals("**Dr. Novak** says so.", CheatSheet.ruleLine("**Dr. Novak** says so. More."))
        assertEquals("One line only", CheatSheet.ruleLine("One line only\n\nAnother paragraph."))
        val long = CheatSheet.ruleLine("**" + "beseda ".repeat(60) + "** konec.", max = 40)
        assertTrue(long, long.endsWith("** …") && long.length <= 46)
    }

    @Test fun `every bundled page gives a card with a rule line, and the cards come in the book's order`() {
        val pages = Grammar.bundled("sl")
        val cards = CheatSheet.cards(pages, Cards.bundled("sl"), emptyList(), "sl", "en")
        val ofPages = cards.filter { it.kind == SheetKind.PAGE }
        assertEquals(pages.map { "page:${it.id}" }, ofPages.map { it.key })
        assertTrue(ofPages.all { (it.face as CardFace.Rule).rule!!.isNotBlank() })
        // a curated card comes right after the first of its pages (and the curated ones before it)
        for ((i, d) in cards.withIndex()) if (d.kind == SheetKind.DECK) {
            val first = pages.firstOrNull { it.id in d.pages } ?: continue
            assertEquals(d.key, "page:${first.id}", cards.subList(0, i).last { it.kind == SheetKind.PAGE }.key)
        }
    }

    // --- unlocking -------------------------------------------------------------------------------------------------------

    @Test fun `a card unlocks with its page, a curated one with any of its pages, a basic and the pinned and tutor's at once`() {
        val pages = listOf(page("biti"), page("dvojina"), page("osebni-zaimki", "A2"))
        val all = CheatSheet.cards(
            pages, listOf(deck("zaimki", listOf("osebni-zaimki", "dvojina")), deck("stevila", listOf("stevila-samostalniki"), start = true), deck("imeti", listOf("biti"), source = Grammar.TUTOR, markdown = "- a\n- b")),
            listOf(PinnedCard("m-1", "sl", "mi / vi", "| a | b |\n| c | d |")), "sl", "en",
        )
        fun shown(s: GameState) = CheatSheet.sheet(all, GrammarBook.met(s, "sl").keys, s.sheet.opens, "sl", emptyList(), day) { false }
        val before = shown(s0)
        assertEquals(setOf("deck:stevila", "tutor:imeti", "pin:m-1"), before.cards.map { it.key }.toSet())
        assertEquals(4, before.locked) // three pages' and the pronouns'
        // the learner meets dvojina (an exercise, a module, a school unit's introduction): its card and the pronouns' unlock
        val (s1, fresh) = GrammarBook.meet(s0, "sl", listOf("dvojina"), day)
        assertEquals(listOf("dvojina"), fresh)
        val after = shown(s1)
        assertTrue(after.cards.map { it.key }.containsAll(listOf("page:dvojina", "deck:zaimki")))
        assertEquals(2, after.locked)
        // all new: never opened
        assertEquals(after.cards.map { it.key }.toSet(), after.fresh)
    }

    // --- the order -------------------------------------------------------------------------------------------------------

    @Test fun `the order, the exercise's cards first, then the most opened, then the new, then the rest, mastered ones down`() {
        val pages = listOf("a", "b", "c", "d", "e", "f", "g").map { page(it) }
        val all = CheatSheet.cards(pages, listOf(deck("b-too", listOf("b"))), emptyList(), "sl", "en")
        val met = pages.map { it.id }.toSet()
        var s = s0
        // opened: c three times today, e once long ago (a count that halved and halved), d once today; f, g, a never
        repeat(3) { s = CheatSheet.opened(s, "sl", "page:c", day) }
        s = CheatSheet.opened(s, "sl", "page:e", day.minusDays(70))
        s = CheatSheet.opened(s, "sl", "page:d", day)
        s = CheatSheet.opened(s, "sl", "page:g", day)
        val secure = setOf("g", "a")
        val sheet = CheatSheet.sheet(all, met, s.sheet.opens, "sl", listOf("b", "f"), day) { it in secure }
        assertEquals(
            listOf(
                "page:b", "deck:b-too", "page:f", // the exercise's: its own page first, the module's after
                "page:c", "page:d", // most opened lately
                // new: a is mastered (down below), none else
                "page:e", // opened long ago: the rest
                "page:g", "page:a", // mastered: at the bottom, the opened one first
            ),
            sheet.cards.map { it.key },
        )
        assertEquals(setOf("page:b", "deck:b-too", "page:f"), sheet.context)
        assertEquals(setOf("page:a", "page:b", "deck:b-too", "page:f"), sheet.fresh)
        // without an exercise: the new ones come right after the most opened
        val home = CheatSheet.sheet(all, met, s.sheet.opens, "sl", emptyList(), day) { false }
        assertEquals(listOf("page:c", "page:d", "page:g", "page:a", "page:b", "deck:b-too", "page:f", "page:e"), home.cards.map { it.key })
        // opened at a card (a pinned message's chip): that one first, the rest as they were
        val at = CheatSheet.sheet(all, met, s.sheet.opens, "sl", emptyList(), day, focus = "page:e") { false }
        assertEquals(listOf("page:e", "page:c", "page:d", "page:g", "page:a", "page:b", "deck:b-too", "page:f"), at.cards.map { it.key })
    }

    @Test fun `an open counts half after two weeks, and opening adds to what is left`() {
        assertEquals(1.0, CheatSheet.weight(CardUse(2.0, day.toString()), day.plusDays(14)), 1e-9)
        assertEquals(0.0, CheatSheet.weight(null, day), 1e-9)
        var s = CheatSheet.opened(s0, "sl", "page:biti", day)
        s = CheatSheet.opened(s, "sl", "page:biti", day.plusDays(14))
        val use = s.sheet.opens.getValue("sl/page:biti")
        assertEquals(1.5, use.n, 1e-9)
        assertEquals(day.plusDays(14).toString(), use.on)
    }

    // --- pinning a tutor message -----------------------------------------------------------------------------------------

    private val imeti = """
        **Imeti** – the whole verb:
        - jaz **imam** · ti **imaš** · on/ona **ima**
        - midva **imava** · vidva/onadva **imata**
        - mi **imamo** · vi **imate** · oni **imajo**

        *Vida ima gostilno.* (one) · *Vida in Nejc imata …* (two) · *Vsi imajo …* (three+)

        The two-person endings are the same everywhere: **-va** (me + one more), **-ta** (two others), like *sva / sta* with biti.
    """.trimIndent()

    private val zaimki = """
        **mi / vi / oni** at a glance:

        | | 1 | 2 | 3+ |
        |---|---|---|---|
        | I/we | jaz | **midva** | **mi** |
        | you | ti | **vidva** | **vi** |
        | he/she/they | on, ona | **onadva** | **oni** |

        **vi** is also the polite "you" to one person. Usually you drop them; say the pronoun for contrast.
    """.trimIndent()

    @Test fun `a tutor message with a table or a list can be pinned, one without can't`() {
        assertTrue(CheatSheet.pinnable(imeti))
        assertTrue(CheatSheet.pinnable(zaimki))
        assertFalse(CheatSheet.pinnable("Bravo! **Imam** is right.\n\nOne more line."))
        assertFalse(CheatSheet.pinnable("- only one item"))
    }

    @Test fun `a pinned message's title, its first bold words, else its first line`() {
        assertEquals("Imeti – the whole verb", CheatSheet.titleOf(imeti))
        assertEquals("mi / vi / oni at a glance", CheatSheet.titleOf(zaimki))
        assertEquals("Pronouns at a glance", CheatSheet.titleOf("# Pronouns at a glance\n- a\n- b"))
        // a long first line: its first bold words
        assertEquals("imam, imaš, ima", CheatSheet.titleOf("You asked about this one yesterday, so here it is once more: **imam, imaš, ima** and the rest\n- a\n- b"))
        // a table first: its rows are no title
        assertEquals("Jaz, mi: a list", CheatSheet.titleOf("| a | b |\n|---|---|\n| **jaz** | **mi** |\n\nJaz, **mi**: a list."))
    }

    @Test fun `a pinned message's pages are guessed from the book's tables, the forms it stresses`() {
        val pages = Grammar.bundled("sl")
        assertEquals(listOf("imeti-iti"), CheatSheet.guessPages(imeti, pages))
        assertTrue(CheatSheet.guessPages(zaimki, pages).isNotEmpty())
        assertEquals(emptyList<String>(), CheatSheet.guessPages("- **lubenica**\n- **avtobus**\n- **kolo**", pages))
    }

    @Test fun `pinning, a card at once, not new, once a message, renamed and removed`() {
        val (s1, id) = CheatSheet.pin(s0, "sl", "msg-1", imeti, listOf("imeti-iti"), "A1", day)
        assertNotNull(id)
        val p = s1.sheet.pinned.single()
        assertEquals("Imeti – the whole verb", p.title)
        assertEquals(imeti, p.markdown)
        assertEquals(listOf("imeti-iti"), p.pages)
        assertEquals("msg-1", p.message)
        val card = CheatSheet.of(p)
        assertEquals("pin:${p.id}", card.key)
        assertEquals(CardFace.Text(imeti), card.face)
        assertTrue(CheatSheet.unlocked(card, emptySet()))
        // the learner chose it: opened once, so not new, and among the most opened for a while
        assertEquals(CardUse(1.0, day.toString()), s1.sheet.opens["sl/${card.key}"])
        val sheet = CheatSheet.sheet(listOf(card), emptySet(), s1.sheet.opens, "sl", emptyList(), day) { false }
        assertEquals(emptySet<String>(), sheet.fresh)
        // the same message again: nothing
        assertEquals(s1 to null, CheatSheet.pin(s1, "sl", "msg-1", imeti, emptyList(), "A1", day))
        val s2 = CheatSheet.rename(s1, p.id, "  imeti, vse osebe  ")
        assertEquals("imeti, vse osebe", s2.sheet.pinned.single().title)
        assertEquals(s2, CheatSheet.rename(s2, p.id, "   "))
        val s3 = CheatSheet.unpin(s2, p.id)
        assertTrue(s3.sheet.pinned.isEmpty())
        assertFalse("sl/pin:${p.id}" in s3.sheet.opens)
    }

    @Test fun `the sheet holds at most its pinned cards`() {
        var s = s0
        repeat(CheatSheet.MAX_PINNED) { s = CheatSheet.pin(s, "sl", "m$it", imeti, emptyList(), "A1", day).first }
        assertEquals(CheatSheet.MAX_PINNED, s.sheet.pinned.size)
        assertEquals(s to null, CheatSheet.pin(s, "sl", "one-more", imeti, emptyList(), "A1", day))
    }

    // --- kept in the village -----------------------------------------------------------------------------------------------

    @Test fun `the sheet is saved with the village and an old save loads without one`() {
        val (s1, _) = CheatSheet.pin(CheatSheet.opened(s0, "sl", "page:biti", day), "sl", "msg-1", zaimki, listOf("osebni-zaimki"), "A1", day)
        val back = GameSync.decode(GameSync.encode(GameCache(3, s1)))
        assertEquals(s1.sheet, back?.state?.sheet)
        // a save from before the sheet: none, and nothing is new or pinned
        val old = json.encodeToString(GameCache.serializer(), GameCache(3, s0)).replace(Regex(""","sheet":\{[^}]*\}"""), "")
        val loaded = GameSync.decode(old)
        assertEquals(SheetState(), loaded?.state?.sheet)
        // a newer app's keys in the sheet are left alone
        val newer = GameSync.encode(GameCache(3, s1)).replace("\"pinned\":[", "\"colour\":\"red\",\"pinned\":[")
        assertEquals(s1.sheet, GameSync.decode(newer)?.state?.sheet)
    }

    // --- looked up during an exercise -----------------------------------------------------------------------------------

    @Test fun `the sheet counts on the exercise on screen, from its appearing to its answer, never one opened before`() {
        val l = SheetLookups()
        // from Home, the chat: no exercise on screen
        l.sheetOpened()
        l.cardOpened("page:biti") { listOf("biti") }
        val first = l.exercise()
        // the sheet opened before it appeared and still about: its cards count for nothing
        l.cardOpened("page:biti") { listOf("biti") }
        assertNull(l.answered(first))
        // the next exercise: the sheet opened over it, its cards opened (one twice, one without a page)
        val second = l.exercise()
        l.sheetOpened()
        l.cardOpened("page:tozilnik") { listOf("tozilnik") }
        l.cardOpened("deck:zaimki") { listOf("osebni-zaimki", "tozilnik") }
        l.cardOpened("pin:m-1") { emptyList() }
        l.cardOpened("page:tozilnik") { listOf("tozilnik") }
        l.sheetOpened()
        val looked = LookUp(listOf("page:tozilnik", "deck:zaimki", "pin:m-1"), listOf("tozilnik", "osebni-zaimki"))
        assertEquals(looked, l.answered(second))
        // over its feedback (answered), the sheet is free: nothing more counts on it
        l.sheetOpened()
        l.cardOpened("page:biti") { listOf("biti") }
        assertEquals(looked, second.lookUp())
        // opened and no card opened: it still counts (a right answer is right with help), no cards
        val third = l.exercise()
        l.sheetOpened()
        assertEquals(LookUp(), l.answered(third))
        // a run left: the exercise gone counts nothing more; a stale goodbye doesn't stop the next
        val left = l.exercise()
        l.gone(left)
        l.sheetOpened()
        assertFalse(left.opened)
        val next = l.exercise()
        l.gone(left)
        l.sheetOpened()
        assertTrue(next.opened)
    }

    @Test fun `a worksheet's items answered after the sheet opened over it, until it is checked`() {
        val l = SheetLookups()
        val values = mutableListOf("sem", "", "")
        l.items = { values.toList() }
        l.exercise()
        // the sheet not opened: no item looked up
        values[1] = "si"
        assertEquals(listOf(false, false, false), l.itemsAfter(values))
        // opened: what the items hold then is kept; the ones answered after (filled, changed) are looked up
        l.sheetOpened()
        values[2] = "je"
        assertEquals(listOf(false, false, true), l.itemsAfter(values))
        // opened again: still the first open's
        l.sheetOpened()
        values[0] = "smo"
        assertEquals(listOf(true, false, true), l.itemsAfter(values))
        // no exercise on screen: nothing
        assertEquals(listOf(false, false, false), SheetLookups().itemsAfter(values))
    }

    @Test fun `what was looked up counts on its pages by day, the last two weeks kept, and is saved with the village`() {
        val d = day.toString()
        var s = CheatSheet.lookedUp(s0, "sl", listOf("tozilnik", "biti", "tozilnik"), day)
        assertEquals(mapOf("sl/tozilnik" to mapOf(d to 1), "sl/biti" to mapOf(d to 1)), s.sheet.lookups)
        s = CheatSheet.lookedUp(s, "sl", listOf("tozilnik"), day)
        assertEquals(mapOf(d to 2), s.sheet.lookups["sl/tozilnik"])
        // no pages (a pinned card of none, no card opened): nothing
        assertEquals(s, CheatSheet.lookedUp(s, "sl", emptyList(), day))
        // thirteen days later the first day is still among the last two weeks; a day after, it's gone
        val late = day.plusDays(CheatSheet.LOOKUP_DAYS - 1)
        s = CheatSheet.lookedUp(s, "sl", listOf("biti"), late)
        assertEquals(mapOf(d to 2), s.sheet.lookups["sl/tozilnik"])
        val later = late.plusDays(1)
        s = CheatSheet.lookedUp(s, "sl", listOf("biti"), later)
        assertNull(s.sheet.lookups["sl/tozilnik"])
        assertEquals(mapOf(late.toString() to 1, later.toString() to 1), s.sheet.lookups["sl/biti"])
        // kept with the village; a save from before has none
        val back = GameSync.decode(GameSync.encode(GameCache(3, s)))
        assertEquals(s.sheet.lookups, back?.state?.sheet?.lookups)
        assertEquals(emptyMap<String, Map<String, Int>>(), GameSync.decode(GameSync.encode(GameCache(3, s0)))?.state?.sheet?.lookups)
    }

    @Test fun `what was looked up, as the bridge gets it with the answer`() {
        assertEquals("""{"cards":["page:biti","pin:m-1"],"pages":["biti"]}""", LookUp(listOf("page:biti", "pin:m-1"), listOf("biti")).json.toString())
        assertEquals("""{"cards":[],"pages":[]}""", LookUp().json.toString())
    }

    @Test fun `the tutor's card_published and card_removed events`() {
        assertEquals(BridgeEvent.CardPublished("imeti", "Imeti", "The whole verb"), BridgeEvent.parse("""{"type":"card_published","id":"imeti","title":"Imeti","note":"The whole verb"}"""))
        assertEquals(BridgeEvent.CardPublished("imeti", null, null, removed = true), BridgeEvent.parse("""{"type":"card_removed","id":"imeti"}"""))
    }

    @Test fun `a page as the bridge serves it keeps its card, one without a rule line has none`() {
        val raw = """{"id":"biti","title":{"sl":"Biti","en":"To be"},"rule":{"en":"r"},"examples":[{"sl":"Sem.","en":"I am."}],
            "card":{"rule":{"en":"**sem**"},"examples":[{"sl":"Doma sem.","en":"I'm home."}],"tip":{"en":"💡 t"},"start":true,"table":{"head":["a","b"],"rows":[["c","d"]]}}}"""
        val p = Grammar.parse(json.parseToJsonElement(raw) as JsonObject)!!
        val c = p.card!!
        assertEquals(mapOf("en" to "**sem**"), c.rule)
        assertEquals("Doma sem.", c.examples!!.single().target("sl"))
        assertEquals(mapOf("en" to "💡 t"), c.tip)
        assertTrue(c.start)
        assertEquals(1, c.table!!.rows.size)
        val noRule = Grammar.parse(json.parseToJsonElement(raw.replace(""""card":{"rule":{"en":"**sem**"},""", """"card":{""")) as JsonObject)!!
        assertNull(noRule.card)
    }
}
