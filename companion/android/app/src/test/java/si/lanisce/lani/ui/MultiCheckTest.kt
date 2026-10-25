package si.lanisce.lani.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/** A select-all exercise looks like one: the Check button says how many are ticked (wishlist, clitic-se-basics #1). */
class MultiCheckTest {
    private var was = L10n.pair

    @Before fun pair() {
        was = L10n.pair
        L10n.pair = LangPair.DEFAULT
    }

    @After fun back() {
        L10n.pair = was
    }

    @Test fun `the Check button counts the options ticked, and says none before any`() {
        assertEquals("0 izbranih · 0 selected", multiCheckLabel(0))
        assertEquals("Preveri · Check (1)", multiCheckLabel(1))
        assertEquals("Preveri · Check (2)", multiCheckLabel(2))
    }

    @Test fun `in the other pairs too`() {
        L10n.pair = LangPair(Lang.SL, Lang.DE)
        assertEquals("0 izbranih · 0 ausgewählt", multiCheckLabel(0))
        assertEquals("Preveri · Prüfen (3)", multiCheckLabel(3))
        L10n.pair = LangPair(Lang.IT, Lang.SL)
        assertEquals("0 selezionate · 0 izbranih", multiCheckLabel(0))
    }
}
