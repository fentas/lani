package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.villagers.Resident

/** Buying goods (GAME.md, "The chest"): from the market, the merchant and the pedlar, never an endless friendship pump. */
class MarketTest {
    private val t0 = noon(day0)

    private fun village(age: Age, vararg types: BuildingType) = GameState(
        age = age, resources = Res.entries.associateWith { 1000 },
        buildings = types.mapIndexed { i, t -> Building("b$i", t, i) },
        residents = listOf("micka", "luka", "zala", "marko").map { Resident(it, "2026-01-01") }, villagers = 4,
    )

    private val merchant = GameEvent("ev", EventKind.MERCHANT, 2, 0, Long.MAX_VALUE)

    @Test fun `a good costs twice its value, growing with the age, in food and wood`() {
        // potica is worth 20: at Vas ×1.9, twice that is 76: 46 🌾 and 30 🪵
        assertEquals(mapOf(Res.FOOD to 46, Res.WOOD to 30), Chest.buyPrice(village(Age.VAS), "potica"))
        // at Trg ×2.2: 88
        assertEquals(88, Chest.buyPrice(village(Age.TRG), "potica").values.sum())
    }

    @Test fun `who sells, the market from Trg, the merchant while he's here, the pedlar on his day`() {
        assertTrue(Chest.stalls(village(Age.VAS), day0).isEmpty())
        val trg = village(Age.TRG, BuildingType.MARKET)
        val market = Chest.stalls(trg, day0).single()
        assertEquals(Chest.MARKET, market.id)
        assertEquals(Catalog.MARKET_OFFERS, market.offers.size)
        assertTrue(market.offers.all { !it.good.rare && it.left == Catalog.MARKET_STOCK })
        // what's on offer is the day's dice: the same all day
        assertEquals(market, Chest.stalls(trg, day0).single())
        // a damaged market, or a market before Trg, sells nothing
        assertTrue(Chest.stalls(trg.copy(buildings = trg.buildings.map { it.copy(damaged = true) }), day0).isEmpty())
        assertTrue(Chest.stalls(village(Age.VAS, BuildingType.MARKET), day0).isEmpty())
        val m = Chest.stalls(village(Age.VAS).copy(event = merchant), day0).single()
        assertEquals(Chest.MERCHANT, m.id)
        assertEquals(Catalog.MERCHANT_OFFERS, m.offers.size)
    }

    @Test fun `buying, paid, in the chest, counted, sold out after the day's stock, again tomorrow`() {
        val s = village(Age.TRG, BuildingType.MARKET)
        val o = Chest.stalls(s, day0).single().offers.first()
        val one = Chest.buy(s, Chest.MARKET, o.good.id, day0, t0)
        assertEquals(1, Chest.count(one, o.good.id))
        assertEquals(1000 - o.price.getValue(Res.FOOD), one.res(Res.FOOD))
        assertEquals(1000 - o.price.getValue(Res.WOOD), one.res(Res.WOOD))
        assertEquals(1, Chest.boughtToday(one, Chest.MARKET, o.good.id, day0))
        assertTrue(one.log.last().text.startsWith("Kupljeno · Bought"))
        var st = one
        repeat(Catalog.MARKET_STOCK - 1) { st = Chest.buy(st, Chest.MARKET, o.good.id, day0, t0) }
        assertEquals(Catalog.MARKET_STOCK, Chest.count(st, o.good.id))
        val sold = Chest.stalls(st, day0).single().offers.first { it.good.id == o.good.id }
        assertEquals(0, sold.left)
        assertEquals("Danes razprodano · Sold out today", Chest.buyBlocker(st, sold))
        assertSame(st, Chest.buy(st, Chest.MARKET, o.good.id, day0, t0))
        // tomorrow the stock is back
        val tomorrow = day0.plusDays(1)
        val again = Chest.stalls(st, tomorrow).single().offers.firstOrNull { it.good.id == o.good.id }
        if (again != null) assertEquals(Catalog.MARKET_STOCK, again.left)
        // what isn't on offer, or can't be paid, isn't bought
        val notOffered = Catalog.goods.values.first { g -> g.rare }
        assertSame(s, Chest.buy(s, Chest.MARKET, notOffered.id, day0, t0))
        val poor = s.copy(resources = emptyMap())
        assertTrue(Chest.buyBlocker(poor, Chest.stalls(poor, day0).single().offers.first())!!.startsWith("Manjka"))
        assertSame(poor, Chest.buy(poor, Chest.MARKET, o.good.id, day0, t0))
    }

    @Test fun `a sale never brings more than a purchase costs`() {
        // Vida's brass scales forged twice (+75 %) at the market: 1.5 × 1.75 would be 2.6, but it stops at the buying rate
        val s = village(Age.TRG, BuildingType.MARKET).copy(
            chest = Inventory(tools = mapOf("vida" to Tool(tier = 2, forged = 2)), goods = mapOf("potica" to 1)),
            resources = mapOf(Res.FOOD to 1000, Res.WOOD to 1000, Res.STONE to 0, Res.WISDOM to 1000),
        )
        val (r, n) = Chest.price(s, "potica")!!
        assertEquals(Res.STONE, r)
        assertEquals(Chest.buyPrice(s, "potica").values.sum(), n)
    }

    @Test fun `rare goods, everyone likes them (no wine for the children), and they count twice`() {
        assertTrue(Chest.likes("luka").any { it.id == "mlado_vino" })
        assertFalse(Chest.likes("zala").any { it.id == "mlado_vino" })
        assertTrue(Chest.likes("zala").any { it.id == "krofi" })
        // someone the catalog doesn't know may be a child born here: no wine either
        assertFalse(Chest.likes("n-ana-furlan").any { it.adult })
        assertTrue(Chest.likes("n-ana-furlan").any { it.id == "pisanice" })
        val s = village(Age.VAS).copy(chest = Inventory(goods = mapOf("krofi" to 1, "potica" to 1)))
        assertEquals(Catalog.RARE_GIFT_POINTS, Chest.give(s, "krofi", "luka", day0, t0).bonds["luka"]?.points)
        // still once a day each
        val given = Chest.give(s, "krofi", "zala", day0, t0)
        assertNull(Chest.giveBlocker(s, "potica", "zala", day0))
        assertSame(given, Chest.give(given, "potica", "zala", day0, t0))
        // rare goods only come from the festivals and the pedlar: never thanks, small gifts or the market
        val rare = Catalog.goods.values.filter { it.rare }.map { it.id }.toSet()
        assertTrue(Catalog.thanks.values.none { it in rare } && Catalog.smallGifts.none { it in rare })
        assertTrue(Catalog.pedlarGoods.all { it in rare })
        assertTrue(Calendar.festivals.mapNotNull { it.good }.filter { it in rare }.isNotEmpty())
        // and the smith takes a common good first
        val pay = village(Age.VAS).copy(chest = Inventory(goods = mapOf("krofi" to 5, "jabolka" to 1)))
        assertEquals("jabolka", Chest.smithsPay(pay)?.id)
    }
}
