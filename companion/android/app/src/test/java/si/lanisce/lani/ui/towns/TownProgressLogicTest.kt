package si.lanisce.lani.ui.towns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.HardItem
import si.lanisce.lani.data.HardPattern
import si.lanisce.lani.data.ProgressTown
import si.lanisce.lani.data.ShareLevels
import si.lanisce.lani.data.Sharing
import si.lanisce.lani.data.TheirShare
import si.lanisce.lani.data.TownProgress
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/** What the friend's card and the progress screen say of a linked town's learning (ui/towns/TownProgressLogic.kt). */
class TownProgressLogicTest {
    private val child = TownProgress(town = ProgressTown(id = "t", learner = "Teo", language = "it"), level = ShareLevels.DETAILS, child = true, guardian = true, base = "de")
    private val adult = child.copy(child = false, guardian = false, base = "en")
    private val articles = HardPattern(id = "articles", name = "Articoli: il / la", items = listOf(HardItem("vocab_casa", "la casa", mapOf("de" to "das Haus", "sl" to "hiša"))))

    @Test fun `the card says what the other town sees and shows, both ways`() = withPair {
        val s = Sharing(share = ShareLevels.SUMMARY, shareDefault = true, theirs = TheirShare(ShareLevels.DETAILS, guardian = true))
        assertEquals("👁️ Kaj vidi Teo · What Teo sees: 📊 Povzetek · Summary (privzeto · default)", TownProgressLogic.shareLine(s, "Teo"))
        assertEquals("👀 Teo ti kaže · Teo shows you: 🔎 Podrobno · Details", TownProgressLogic.theirLine(s, "Teo"))
        assertNull(TownProgressLogic.theirLine(s.copy(theirs = null), "Teo"))
        assertNull(TownProgressLogic.guardianNote(s))
        assertTrue(TownProgressLogic.offersProgress(s))
        assertTrue(TownProgressLogic.offersProgress(s.copy(theirs = TheirShare(ShareLevels.VILLAGE))))
        assertTrue(!TownProgressLogic.offersProgress(null))
    }

    @Test fun `a child's card on its parent's link - the note, and no choice below the floor`() = withPair {
        val s = Sharing(share = ShareLevels.DETAILS, guardian = true, floor = ShareLevels.DETAILS, choices = listOf(ShareLevels.DETAILS))
        assertEquals("👪 Tvoj starš vidi tvoj napredek · Your parent can see your progress", TownProgressLogic.guardianNote(s))
        assertEquals(listOf(ShareLevels.DETAILS), TownProgressLogic.choices(s))
        // a parent lowered it on the node to a summary: the child may share more again, never less
        assertEquals(listOf(ShareLevels.SUMMARY, ShareLevels.DETAILS), TownProgressLogic.choices(s.copy(floor = ShareLevels.SUMMARY, choices = listOf(ShareLevels.SUMMARY, ShareLevels.DETAILS))))
        assertEquals(ShareLevels.ALL, TownProgressLogic.choices(Sharing()))
    }

    @Test fun `the week as dots, a meaning in the viewer's base or with its flag, the marks`() {
        assertEquals("●●●○○○○", TownProgressLogic.dots(listOf(true, true, true, false, false, false, false)))
        assertEquals("●○○○○○○", TownProgressLogic.dots(listOf(true)))
        assertEquals("the dog", TownProgressLogic.meaning(mapOf("de" to "der Hund", "en" to "the dog"), "en", "de"))
        assertEquals("der Hund 🇩🇪", TownProgressLogic.meaning(mapOf("de" to "der Hund", "sl" to "pes"), "en", "de"))
        assertEquals("pes 🇸🇮", TownProgressLogic.meaning(mapOf("sl" to "pes"), "en", "de"))
        assertNull(TownProgressLogic.meaning(emptyMap(), "en", "de"))
        assertEquals("🔁", TownProgressLogic.stateMark("rusty"))
        assertEquals("🌱", TownProgressLogic.stateMark("learning"))
        assertEquals("↗", TownProgressLogic.trendMark("worse"))
    }

    @Test fun `a challenge from a hard word, gently for a child in the child's base`() = withPair {
        val d = TownProgressLogic.draftFor(child, "la casa", articles.items[0].meanings, articles)
        assertEquals("translate", d.type)
        assertEquals("das Haus", d.text) // what it means in the child's base: they write it in Italian
        assertEquals("Articoli: il / la · Keine Eile, probier es einfach! 🌟", d.hint)
        // an adult: plain, no gentle line
        val a = TownProgressLogic.draftFor(adult, "la casa", mapOf("en" to "the house"))
        assertEquals("the house", a.text)
        assertEquals("", a.hint)
        // a pattern: its first word, the pattern its hint; a pattern without words a question to write
        assertEquals("das Haus", TownProgressLogic.draftForPattern(child, articles).text)
        val bare = TownProgressLogic.draftForPattern(adult, articles.copy(items = emptyList()))
        assertEquals("question", bare.type)
        assertEquals("", bare.text)
        assertEquals("Articoli: il / la", bare.hint)
        // the same word again is the same draft (a retry sends the same challenge)
        assertEquals(d.key, TownProgressLogic.draftFor(child, "la casa", articles.items[0].meanings, articles).key)
    }

    @Test fun `how a sent challenge went`() = withPair {
        assertEquals("⏳ čaka na odgovor · waiting for an answer", TownProgressLogic.resultLine(false, null, null))
        assertEquals("✅ odgovorjeno · answered · 9/10 · Teo knew it.", TownProgressLogic.resultLine(true, 9.0, "Teo knew it."))
    }

    private fun withPair(block: () -> Unit) {
        val before = L10n.pair
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        try {
            block()
        } finally {
            L10n.pair = before
        }
    }
}
