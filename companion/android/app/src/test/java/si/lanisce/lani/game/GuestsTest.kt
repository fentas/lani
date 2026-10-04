package si.lanisce.lani.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.data.Bundle
import si.lanisce.lani.data.GoodRef
import si.lanisce.lani.data.GuestEntry
import si.lanisce.lani.data.GuestFrom
import si.lanisce.lani.data.GuestGift
import si.lanisce.lani.data.GuestHelp
import si.lanisce.lani.data.GuestTrade
import si.lanisce.lani.data.TownActs
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.towns.VisitLogic

/**
 * Towns that visit each other (game/Guests.kt): what guests brought a village, applied once each, and what a village's
 * visits did elsewhere; goods crossing between cultures; the market's choices (ui/towns/VisitLogic.kt).
 */
class GuestsTest {
    private val t0 = noon(day0)
    private val pair = L10n.pair
    private val jan = "a".repeat(32)
    private val mia = "b".repeat(32)
    private val fromJan = GuestFrom(jan, "Moja vas", "Jan", "primorska", "sl")

    @Before fun home() {
        L10n.pair = LangPair(Lang.IT, Lang.EN)
        Cultures.use("friuli") // Mia's village: the host
    }

    @After fun back() {
        Cultures.use(null as String?)
        L10n.pair = pair
    }

    private fun village(goods: Map<String, Int> = mapOf("miele" to 3, "gubana" to 2), res: Int = 50) =
        GameEngine.newGame(1, t0).copy(age = Age.VAS, resources = Res.entries.associateWith { res }, chest = Inventory(goods = goods))

    private val help = GuestEntry("$jan:help-1", "help-1", TownActs.HELP, fromJan, "2026-09-26T10:00:00Z", help = GuestHelp("q-1", "Nonna Rosa", 3, "gubana"))
    private val gift = GuestEntry("$jan:gift-1", "gift-1", TownActs.GIFT, fromJan, "2026-09-26T10:01:00Z", gift = GuestGift(GoodRef("primorska", "med"), "Ciao Mia!"))
    private val trade = GuestEntry(
        "$jan:rp-1", "rp-1", TownActs.TRADE, fromJan, "2026-09-26T10:02:00Z",
        trade = GuestTrade(got = Bundle("primorska", res = mapOf("WOOD" to 20)), gave = Bundle("friuli", goods = mapOf("miele" to 1)), score = 8),
    )

    @Test fun `a good crosses cultures as the one that looks the same, else of the same price and kind`() {
        val home = Cultures.load("friuli")
        assertEquals("miele", Guests.nativeGood("primorska", "med", home)) // 🍯 = 🍯
        assertEquals("gubana", Guests.nativeGood("primorska", "potica", home)) // 🥮 = 🥮
        assertEquals("miele", Guests.nativeGood("friuli", "miele", home)) // the same culture: itself
        val jota = Guests.nativeGood("primorska", "jota", home) // 🍲 has no twin: a good of the same price and kind
        assertTrue(jota != null && home.goods.getValue(jota).value == Cultures.load("primorska").goods.getValue("jota").value)
        assertNull(Guests.nativeGood("friuli", "nope", home))
        assertEquals("med", Guests.nativeGood("friuli", "miele", Cultures.load("primorska")))
    }

    @Test fun `the host applies what guests brought once - help, a gift and its message, a trade`() {
        val s0 = village()
        val (s1, news) = Guests.apply(s0, listOf(trade, gift, help), day0, t0)
        assertEquals(s0.help + 3, s1.help)
        assertEquals(3 + 1 - 1, s1.chest.goods["miele"]) // the gift's honey in, the traded one out
        assertEquals(s0.res(Res.WOOD) + 20, s1.res(Res.WOOD))
        assertEquals(listOf(help.key, gift.key, trade.key), s1.guestbook) // oldest first
        assertEquals(3, s1.towns.getValue(jan).guests)
        assertEquals("Moja vas", s1.towns.getValue(jan).name)
        assertTrue(news.any { it.text == "Jan: Ciao Mia!" && it.emoji == "💌" })
        assertEquals(4, news.size)
        // the guest book again (the next sync): nothing changes twice
        val (s2, again) = Guests.apply(s1, listOf(trade, gift, help), day0, t0 + 1)
        assertSame(s1, s2)
        assertTrue(again.isEmpty())
    }

    @Test fun `a trade takes no more than the chest holds, and stores stay within their caps`() {
        val s = village(goods = mapOf("gubana" to 1))
        val (s1, _) = Guests.apply(s, listOf(trade), day0, t0)
        assertEquals(null, s1.chest.goods["miele"]) // never below none
        assertTrue(s1.res(Res.WOOD) <= GameEngine.attributes(s1).caps.getValue(Res.WOOD))
    }

    @Test fun `the visitor's side - the thanks, the gift, the trade and the meeting, each once`() {
        Cultures.use("primorska") // the Primorska village: the visitor
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        val s0 = village(goods = mapOf("med" to 2, "kruh" to 1))
        val (s1, good) = Guests.helped(s0, "$jan:help-1", mia, "Il mio villaggio", "Nonna Rosa", "friuli", "gubana", day0, t0)
        assertEquals("potica", good) // Nonna Rosa's gubana, as the Primorska village's own
        assertEquals(1, s1.chest.goods["potica"])
        assertSame(s1, Guests.helped(s1, "$jan:help-1", mia, "Il mio villaggio", "Nonna Rosa", "friuli", "gubana", day0, t0).first)
        val s2 = Guests.gave(s1, "$jan:gift-1", mia, "Il mio villaggio", "med", day0, t0)
        assertEquals(1, s2.chest.goods["med"])
        assertSame(s2, Guests.gave(s2, "$jan:gift-1", mia, "Il mio villaggio", "med", day0, t0))
        val give = Bundle("primorska", goods = mapOf("kruh" to 1), res = mapOf("WOOD" to 20))
        val get = Bundle("friuli", goods = mapOf("miele" to 1))
        val s3 = Guests.traded(s2, "$jan:rp-1", mia, "Il mio villaggio", give, get, day0, t0)
        assertEquals(s2.res(Res.WOOD) - 20, s3.res(Res.WOOD))
        assertNull(s3.chest.goods["kruh"])
        assertEquals(2, s3.chest.goods["med"]) // the honey from the market, as Jan's med
        assertSame(s3, Guests.traded(s3, "$jan:rp-1", mia, "Il mio villaggio", give, get, day0, t0))
        val s4 = Guests.met(s3, mia, "Il mio villaggio", "rosa", "Nonna Rosa", day0, t0)
        val s5 = Guests.met(s4, mia, "Il mio villaggio", "rosa", "Nonna Rosa", day0, t0)
        val ties = s5.towns.getValue(mia)
        assertEquals(listOf("rosa"), ties.met)
        assertEquals(1, ties.helped)
        assertEquals(1, ties.gifts)
        assertEquals(1, ties.trades)
        assertEquals(s4.log.size, s5.log.size) // met again: no second line
    }

    @Test fun `the market's choices - what is fair, what the learner has, what the market spares`() {
        val s = village(goods = mapOf("gubana" to 2))
        val honey = Bundle("friuli", goods = mapOf("miele" to 1))
        assertTrue(Guests.fair(Bundle("friuli", res = mapOf("WOOD" to 10)), honey)) // 10 for a good (20): half
        assertFalse(Guests.fair(Bundle("friuli", res = mapOf("WOOD" to 9)), honey))
        assertTrue(Guests.has(s, Bundle("friuli", goods = mapOf("gubana" to 2), res = mapOf("FOOD" to 50))))
        assertFalse(Guests.has(s, Bundle("friuli", goods = mapOf("gubana" to 3))))
        val wares = mapOf("miele" to 2)
        assertTrue(VisitLogic.canHaggle(s, Bundle("friuli", res = mapOf("WOOD" to 20)), honey, wares))
        assertFalse(VisitLogic.canHaggle(s, Bundle("friuli", res = mapOf("WOOD" to 20)), Bundle("friuli", goods = mapOf("miele" to 3)), wares))
        assertFalse(VisitLogic.canHaggle(s, Bundle("friuli", res = mapOf("WOOD" to 500)), honey, wares)) // not in the stores
        assertTrue(VisitLogic.haggleHint(s, Bundle(), honey, wares)!!.startsWith("👉"))
        assertEquals(mapOf("miele" to 1), VisitLogic.left(mapOf("miele" to 2, "uova" to 1), Bundle("friuli", goods = mapOf("miele" to 1, "uova" to 1))))
        assertEquals(mapOf("a" to 3), VisitLogic.step(mapOf("a" to 2), "a", 5, 3))
        assertEquals(emptyMap<String, Int>(), VisitLogic.step(mapOf("a" to 2), "a", -5, 3))
    }
}
