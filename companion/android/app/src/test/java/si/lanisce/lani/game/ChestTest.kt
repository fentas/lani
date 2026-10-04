package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Resident

/** "Skrinja · The chest" (GAME.md): the villagers' gifts at friendship levels, tools and what they do, goods and trade. */
class ChestTest {
    private val t0 = noon(day0)

    private fun village(age: Age = Age.ZASELEK, res: Map<Res, Int> = all(400), vararg types: BuildingType) =
        GameEngine.newGame(1, t0).copy(
            age = age, resources = res, villagers = 4, morale = 50,
            buildings = types.mapIndexed { i, t -> Building("b$i", t, i) },
            lastTick = day0.toString(), foundedOn = day0.toString(),
        )

    private fun all(n: Int) = Res.entries.associateWith { n }

    private fun withTool(s: GameState, giver: String, tier: Int = 1, forged: Int = 0) =
        s.copy(chest = s.chest.copy(tools = s.chest.tools + (giver to Tool(tier, forged))))

    private fun withGoods(s: GameState, vararg goods: Pair<String, Int>) = s.copy(chest = s.chest.copy(goods = goods.toMap()))

    // --- gifts at friendship levels ------------------------------------------------------------

    @Test fun `a friend gives a tool of their trade, a close friend a better one`() {
        var s = village()
        s = Bonds.add(s, "luka", 29, day0, now = t0)
        assertTrue(s.chest.tools.isEmpty())
        assertEquals(listOf("Sekira", "Ostra sekira"), listOf(2, 4).map { Chest.giftAt("luka", it).sl })
        s = Bonds.add(s, "luka", 1, day0, now = t0) // 30: "Prijatelj · Friend"
        assertEquals(Tool(tier = 1), s.chest.tools["luka"])
        assertEquals(2, s.chest.gifted["luka"])
        assertEquals("🪓" to "Luka ti podari sekiro 🪓 · Luka gives you an axe: +10 % 🪵", s.log.last().let { it.emoji to it.text })
        // more points on the same level: nothing new
        val more = Bonds.add(s, "luka", 30, day0, now = t0)
        assertEquals(s.chest, more.chest)
        // 150: "Kot družina · Like family": the sharp axe replaces the axe, twice as strong
        val family = Bonds.add(more, "luka", 100, day0, now = t0)
        assertEquals(Tool(tier = 2), family.chest.tools["luka"])
        assertEquals(4, family.chest.gifted["luka"])
        assertTrue(family.log.last().text.contains("ostro sekiro") && family.log.last().text.contains("+20 % 🪵"))
        // both given: closer still, nothing more
        assertEquals(family.chest, Bonds.add(family, "luka", 200, day0, now = t0).chest)
    }

    @Test fun `a friendship that jumps two levels gives the better tool once`() {
        val s = Bonds.add(village(), "micka", 160, day0, now = t0)
        assertEquals(Tool(tier = 2), s.chest.tools["micka"])
        assertEquals(1, s.log.count { it.text.contains("Micka ti podari") })
        assertTrue(s.log.last().text.contains("recept za potico"))
    }

    @Test fun `people who moved in or were born here give small gifts`() {
        val ana = Resident("n-ana-furlan", "2026-08-01", name = "Ana Furlan", voice = "female", family = "Furlan")
        var s = village().copy(residents = listOf(ana))
        s = Bonds.add(s, ana.id, 30, day0, now = t0)
        assertTrue(s.chest.tools.isEmpty())
        val first = Chest.smallGift(ana.id, 2)
        assertEquals(1, s.chest.goods[first.id])
        assertTrue(s.log.last().text.startsWith("Ana ti podari ${first.name.acc}"))
        s = Bonds.add(s, ana.id, 120, day0, now = t0)
        assertEquals(2, s.chest.goods.values.sum())
        assertEquals(4, s.chest.gifted[ana.id])
    }

    @Test fun `friends from before the chest give their gifts when they're here`() {
        // an older save: Micka and Luka are friends already, nobody gave anything yet; Luka is away today
        val old = village().copy(bonds = mapOf("micka" to Bond(40), "luka" to Bond(200), "ancka" to Bond(12)))
        val (s, gifts) = Chest.catchUp(old, present = setOf("micka", "ancka"), now = t0)
        assertEquals(listOf("micka"), gifts.map { it.from })
        assertEquals(Tool(tier = 1), s.chest.tools["micka"])
        assertNull(s.chest.tools["luka"])
        // tomorrow Luka is back: the better tool straight away; and nobody gives twice
        val (again, more) = Chest.catchUp(s, present = null, now = t0)
        assertEquals(listOf("luka"), more.map { it.from })
        assertEquals(Tool(tier = 2), again.chest.tools["luka"])
        assertTrue(Chest.catchUp(again, null, t0).second.isEmpty())
    }

    @Test fun `the gifts between two states are what a result card shows`() {
        val before = Bonds.add(village(), "tone", 25, day0, now = t0)
        val after = Bonds.add(before, "tone", 10, day0, now = t0)
        val g = Chest.giftsBetween(before, after, "tone").single()
        assertTrue(g.tool)
        assertEquals("⛏️", g.item.emoji)
        assertEquals("Tone ti podari kramp ⛏️ · Tone gives you a pickaxe: +10 % 🪨", g.text)
        assertTrue(Chest.giftsBetween(after, after, "tone").isEmpty())
    }

    // --- what tools do -------------------------------------------------------------------------

    @Test fun `tools change the rules`() {
        val s = village()
        // the axe: +10 % 🪵 on every answer (one villager, neutral morale: no other bonus)
        val one = s.copy(villagers = 1)
        assertEquals(66, GameEngine.earn(withTool(one, "luka"), List(10) { Res.WOOD to Verdict.CORRECT }).second[Res.WOOD])
        assertEquals(60, GameEngine.earn(one, List(10) { Res.WOOD to Verdict.CORRECT }).second[Res.WOOD])
        // Micka's recipe: 10 villagers eat 15, with it 14 (the potica recipe: 12)
        val ten = s.copy(villagers = 10)
        assertEquals(15, GameEngine.attributes(ten).foodUpkeep)
        assertEquals(14, GameEngine.attributes(withTool(ten, "micka")).foodUpkeep)
        assertEquals(12, GameEngine.attributes(withTool(ten, "micka", tier = 2)).foodUpkeep)
        // Marko's barrel: +40 to every store
        assertEquals(540, GameEngine.attributes(withTool(s, "marko")).caps[Res.STONE])
        // Anton's candles and Zala's daisy chain: the village settles at a better morale
        assertEquals(55, moraleTarget(withTool(withTool(s, "anton"), "zala")))
        // Ančka's radio and Nejc's horn: more time for events
        assertEquals(9, warningHours(withTool(withTool(s, "ancka"), "nejc")))
        // Mojca's chalk: requests pay 10 % more
        val q = Quest("q1", "Babica Micka", "👵", "t", "s", Res.FOOD, mapOf(Res.FOOD to 30, Res.STONE to 10))
        val chalk = withTool(s.copy(quests = listOf(q)), "mojca")
        assertEquals(mapOf(Res.FOOD to 33, Res.STONE to 11), GameEngine.completeQuest(chalk, "q1", 6, 6, t0).second.rewards)
        // a tool the catalog doesn't know does nothing
        assertEquals(ToolEffect(), Chest.effect(withTool(s, "nobody")))
    }

    @Test fun `tool strength grows with its tier and each forging`() {
        assertEquals(1f, Chest.power(Tool(1, 0)), 0f)
        assertEquals(1.5f, Chest.power(Tool(1, 1)), 0f)
        assertEquals(2f, Chest.power(Tool(2, 0)), 0f)
        assertEquals(3f, Chest.power(Tool(2, 2)), 0f)
        assertEquals("+30 % 🪵", Chest.effectText(Chest.effectOf("luka", Tool(2, 2))!!))
        assertEquals("+9 h ⏳", Chest.effectText(Chest.effectOf("ancka", Tool(1, 1))!!))
    }

    // --- the smith forges -----------------------------------------------------------------------

    @Test fun `the smith forges iron tools for resources and a good`() {
        val tone = listOf(Resident("tone", "2026-08-01"))
        var s = withGoods(withTool(village(Age.ZASELEK, all(400), BuildingType.SMITHY), "luka"), "potica" to 2, "med" to 1)
        s = s.copy(residents = tone)
        val o = Chest.forgeOption(s, "luka")!!
        assertTrue(o.reason, o.available)
        assertEquals(Catalog.forgeCost(1), o.cost)
        assertEquals("potica", o.good?.id) // the good Jan has most of
        assertEquals("+15 % 🪵", o.effect)
        val forged = Chest.forge(s, "luka", t0)
        assertEquals(Tool(1, 1), forged.chest.tools["luka"])
        assertEquals(1, forged.chest.goods["potica"])
        assertEquals(400 - 300, forged.res(Res.WOOD))
        assertTrue(forged.log.last().text.startsWith("Kovač Tone je izboljšal sekiro ★★"))
        // the second forging needs Vas; after two, it's as good as it gets
        assertEquals("Potrebuje Vas · Needs Village", Chest.forgeOption(forged, "luka")!!.reason)
        val top = withTool(forged, "luka", forged = 2)
        assertNull(Chest.forgeOption(top, "luka"))
    }

    @Test fun `forging needs the smithy, the smith, a good and the price`() {
        val base = withGoods(withTool(village(res = all(400)), "france"), "kruh" to 1)
        assertEquals("Potrebuje kovačnico · Needs the smithy", Chest.forgeOption(base, "france")!!.reason)
        val smithy = base.copy(buildings = listOf(Building("s", BuildingType.SMITHY, 0)))
        // the cast lives here, but not Tone
        assertEquals("Kovač Tone še ne živi v vasi · Smith Tone doesn't live here yet",
            Chest.forgeOption(smithy.copy(residents = listOf(Resident("micka", "2026-08-01"))), "france")!!.reason)
        assertTrue(Chest.forgeOption(smithy, "france")!!.available) // nobody has a name yet: the smith is there
        assertTrue(Chest.forgeOption(withGoods(smithy), "france")!!.reason!!.startsWith("Tone hoče"))
        assertTrue(Chest.forgeOption(smithy.copy(resources = all(100)), "france")!!.reason!!.startsWith("Manjka"))
        // a book or a recipe is no work for a smith
        assertNull(Chest.forgeOption(withTool(smithy, "janez"), "janez"))
        val poor = smithy.copy(resources = all(100))
        assertSame(poor, Chest.forge(poor, "france", t0))
    }

    // --- goods ----------------------------------------------------------------------------------

    @Test fun `a good given to someone who likes it grows the friendship, once a day`() {
        val s = withGoods(village(), "potica" to 2, "podkev" to 1)
        assertNull(Chest.giveBlocker(s, "potica", "zala", day0))
        val given = Chest.give(s, "potica", "zala", day0, t0)
        assertEquals(1, given.chest.goods["potica"])
        assertEquals(Catalog.GIFT_POINTS, given.bonds["zala"]?.points)
        assertEquals("🥮" to "Zala: hvala za potico! · thanks for potica (the rolled walnut cake)!", given.log.last().let { it.emoji to it.text })
        // once a day each (kindly); something they don't especially like can be given too; nothing left
        assertEquals("Danes si že nekaj podaril: jutri spet 🙂 · You gave them something today: again tomorrow 🙂", Chest.giveBlocker(given, "potica", "zala", day0))
        assertSame(given, Chest.give(given, "potica", "zala", day0, t0))
        assertNull(Chest.giveBlocker(given, "podkev", "micka", day0))
        assertNull(Chest.giveBlocker(given, "podkev", "tine", day0))
        assertEquals("Nimaš · You have none", Chest.giveBlocker(given, "rebula", "janez", day0))
        assertNull(Chest.giveBlocker(given, "potica", "zala", day0.plusDays(1)))
    }

    @Test fun `an ordinary good brings warm thanks and fewer hearts, a favourite more, a rare one most, no wine for a child`() {
        val s = withGoods(village(), "podkev" to 1, "potica" to 1, "krofi" to 1, "rebula" to 1, "penina" to 1)
        // Micka's favourites are honey, embroidery, linden tea and eggs: a horseshoe is ordinary to her
        assertFalse(Chest.favourite("micka", "podkev"))
        assertEquals(Catalog.PLAIN_GIFT_POINTS, Chest.giftPoints("podkev", "micka"))
        val plain = Chest.give(s, "podkev", "micka", day0, t0)
        assertEquals(Catalog.PLAIN_GIFT_POINTS, plain.bonds["micka"]?.points)
        assertEquals(0, plain.chest.goods["podkev"] ?: 0)
        assertEquals(day0.toString(), plain.chest.given["micka"])
        // a favourite, a rare good
        assertEquals(Catalog.GIFT_POINTS, Chest.giftPoints("potica", "zala"))
        assertEquals(Catalog.RARE_GIFT_POINTS, Chest.giftPoints("krofi", "micka"))
        // someone the catalog doesn't know likes the defaults (potica, bread, apples)
        assertTrue(Chest.favourite("n-ana", "potica"))
        assertFalse(Chest.favourite("n-ana", "podkev"))
        // wine is for the grown-ups: not for the children, nor for someone who may be a child born here
        assertEquals("Ni za otroke · Not for children", Chest.giveBlocker(s, "rebula", "zala", day0))
        assertEquals("Ni za otroke · Not for children", Chest.giveBlocker(s, "penina", "n-ana", day0))
        assertSame(s, Chest.give(s, "rebula", "tine", day0, t0))
        assertNull(Chest.giveBlocker(s, "rebula", "micka", day0))
        assertNull(Chest.giveBlocker(s, "penina", "janez", day0))
    }

    @Test fun `every good is liked by someone, and nobody likes their own`() {
        // the rare goods (festivals, the pedlar) are liked by everyone
        val liked = Catalog.tools.keys.flatMap { id -> Chest.likes(id).map { it.id } }.toSet()
        assertEquals(Catalog.goods.keys, liked + Catalog.defaultLikes)
        for ((id, good) in Catalog.thanks) assertFalse("$id likes $good", good in Catalog.likes.getValue(id))
        for (id in Catalog.tools.keys) assertTrue(id, id in Catalog.likes)
    }

    @Test fun `goods sell to the merchant while he's here, at the market from Trg`() {
        val s = withGoods(village(Age.ZASELEK, res = mapOf(Res.FOOD to 400, Res.WOOD to 400, Res.STONE to 10, Res.WISDOM to 400)), "rebula" to 2)
        assertNull(Chest.market(s))
        assertNull(Chest.price(s, "rebula"))
        assertSame(s, Chest.sell(s, "rebula", t0).first)
        // the merchant pays its value (25), growing with the age (×1.6 at Zaselek), in what the village is shortest of
        val merchant = s.copy(event = GameEvent("e", EventKind.MERCHANT, 1, t0, t0 + DAY_MS))
        assertEquals(Res.STONE to 40, Chest.price(merchant, "rebula"))
        val (sold, got) = Chest.sell(merchant, "rebula", t0)
        assertEquals(mapOf(Res.STONE to 40), got)
        assertEquals(1, sold.chest.goods["rebula"])
        // Vida's scales: +25 %
        assertEquals(Res.STONE to 50, Chest.price(withTool(merchant, "vida"), "rebula"))
        // the market at Trg: half as much again, any day (×2.2 at Trg)
        val trg = withGoods(village(Age.TRG, res = mapOf(Res.FOOD to 900, Res.WOOD to 900, Res.STONE to 10, Res.WISDOM to 900), BuildingType.MARKET), "potica" to 1)
        assertEquals("🏪", Chest.market(trg)?.emoji)
        assertEquals(Res.STONE to 66, Chest.price(trg, "potica"))
        // a damaged market trades nothing
        assertNull(Chest.market(trg.copy(buildings = trg.buildings.map { it.copy(damaged = true) })))
    }

    @Test fun `Vida's scales make the merchant's trade better too`() {
        val s = village(Age.ZASELEK, mapOf(Res.FOOD to 200, Res.WOOD to 50, Res.STONE to 10, Res.WISDOM to 60))
            .copy(event = GameEvent("e", EventKind.MERCHANT, 2, t0, t0 + DAY_MS))
        assertEquals(mapOf(Res.STONE to 60), GameEngine.resolveEvent(s, 6, 7, t0).second.rewards)
        assertEquals(mapOf(Res.STONE to 75), GameEngine.resolveEvent(withTool(s, "vida"), 6, 7, t0).second.rewards)
    }

    // --- the feast ------------------------------------------------------------------------------

    @Test fun `a feast under the linden, once a week`() {
        assertNull(Chest.feastOption(village(Age.TABOR), day0))
        val people = listOf(Resident("micka", "2026-08-01"), Resident("luka", "2026-08-01"))
        val noLinden = village(Age.ZASELEK).copy(residents = people)
        assertEquals("Potrebuje lipo · Needs the linden tree", Chest.feastOption(noLinden, day0)!!.reason)
        val s = withGoods(noLinden.copy(buildings = listOf(Building("l", BuildingType.LIPA, 0))), "potica" to 3, "podkev" to 1)
        val o = Chest.feastOption(s, day0)!!
        assertTrue(o.reason, o.available)
        assertEquals(Catalog.feastCost(Age.ZASELEK), o.cost)
        assertEquals(listOf("potica", "potica"), o.goods.map { it.id }) // treats only: the horseshoe stays
        assertEquals(3, o.points) // 1 ♥, and one more for each treat
        val feast = Chest.feast(s, day0, t0)
        assertEquals(65, feast.morale)
        assertEquals(mapOf("potica" to 1, "podkev" to 1), feast.chest.goods)
        assertEquals(3, feast.bonds["micka"]?.points)
        assertEquals(400 - 300, feast.res(Res.FOOD))
        assertTrue(feast.log.any { it.emoji == "🎪" })
        // next week, not before
        assertTrue(Chest.feastOption(feast, day0.plusDays(6))!!.reason!!.startsWith("Naslednja veselica 8. 9."))
        assertTrue(Chest.feastOption(feast.copy(resources = all(400)), day0.plusDays(7))!!.available)
        assertTrue(Chest.feastOption(feast, day0.plusDays(7))!!.reason!!.startsWith("Manjka"))
        // without treats it's a smaller feast, but still a feast
        assertEquals(1, Chest.feastOption(withGoods(s), day0)!!.points)
    }
}
