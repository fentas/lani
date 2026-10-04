package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.villagers.Resident

/** 🤝 Pomoč · Help: earned by helping people, spent on a moba (GAME.md, "Helping people"). */
class HelpTest {
    private val t0 = noon(day0)
    private val pool = Fixtures.pool()

    private fun village(age: Age = Age.ZASELEK, help: Int = 0, villagers: Int = 5, res: Map<Res, Int> = all(500), vararg types: BuildingType) =
        GameEngine.newGame(1, t0).copy(
            age = age, resources = res, help = help, villagers = villagers,
            buildings = types.mapIndexed { i, t -> Building("b$i", t, i) },
            lastTick = day0.toString(), foundedOn = day0.toString(),
        )

    private fun all(n: Int) = Res.entries.associateWith { n }

    private fun withQuest(s: GameState, giver: String = "Babica Micka") =
        s.copy(quests = listOf(Quest("q1", giver, "👵", "Mickina kuhinja · Micka's kitchen", "story", Res.FOOD, mapOf(Res.FOOD to 30))))

    // --- earning ------------------------------------------------------------------------------

    @Test fun `a request passed earns help and the giver's thanks`() {
        val s = withQuest(village())
        val (won, r) = GameEngine.completeQuest(s, "q1", 5, 6, t0)
        assertTrue(r.won)
        assertEquals(Catalog.HELP_QUEST, won.help)
        assertEquals(Catalog.HELP_QUEST, won.stats.helpEarned)
        assertEquals(Catalog.HELP_QUEST, r.help)
        // one of Micka's thank-you goods (ThanksTest has which one when)
        val g = Chest.thanksFor("micka", "q1", s.seed, day0)
        assertTrue(g.id, g.id in Catalog.thanksRotation.getValue("micka").goods)
        assertEquals(g.id, r.thanks?.id)
        assertEquals(1, won.chest.goods[g.id])
        assertEquals(g.name.emoji to "Micka ti v zahvalo da ${g.name.acc} · Micka thanks you with ${g.name.accText.base}", won.log.last().let { it.emoji to it.text })
        // not passed: a fifth of the reward, no help, no thanks
        val (tried, c) = GameEngine.completeQuest(s, "q1", 2, 6, t0)
        assertFalse(c.won)
        assertEquals(0, tried.help)
        assertTrue(tried.chest.goods.isEmpty())
        assertNull(c.thanks)
    }

    @Test fun `each villager thanks with their own good`() {
        // one of their own (Vida's jota or bread, Ančka's embroidery, eggs or apples), the one thanksFor names
        fun own(giver: String, id: String, giverId: String? = null) {
            val thanks = GameEngine.completeQuest(withQuest(village(), giver), "q1", 6, 6, t0, giverId = giverId).second.thanks?.id
            assertEquals(giver, Chest.thanksFor(id, "q1", village().seed, day0).id, thanks)
            assertTrue("$giver: $thanks", thanks in Catalog.thanksRotation.getValue(id).goods)
        }
        own("Gostilničarka Vida", "vida")
        own("Teta Ančka", "ancka")
        // the id from the cast wins over the name; someone the catalog doesn't know gives apples
        own("Marko", "marko", giverId = "marko")
        assertEquals("jabolka", GameEngine.completeQuest(withQuest(village(), "Ribič Gregor"), "q1", 6, 6, t0).second.thanks?.id)
        assertEquals("ancka", Quests.idOf("Teta Ančka"))
        assertEquals("anton", Quests.idOf("Čebelar Anton"))
    }

    @Test fun `drills and gathering never bring help`() {
        val s = village()
        assertEquals(0, GameEngine.earn(s, List(30) { Res.FOOD to Verdict.CORRECT }).first.help)
        assertEquals(0, GameEngine.gathered(s, Res.WOOD, 7, 7, day0, t0).help)
    }

    @Test fun `a happening and a talk help too`() {
        val s = village()
        val (once, _) = GameEngine.completeHappening(s, "hisa/potica", mapOf(Res.FOOD to 10), 0, day0, t0, "👵" to "Potica")
        assertEquals(Catalog.HELP_HAPPENING, once.help)
        // once a day, like its pay
        assertSame(once, GameEngine.completeHappening(once, "hisa/potica", mapOf(Res.FOOD to 10), 0, day0, t0, "👵" to "Potica").first)
        assertEquals(Catalog.HELP_HAPPENING + Catalog.HELP_TALK, GameEngine.helped(once, Catalog.HELP_TALK).help)
        assertSame(s, GameEngine.helped(s, 0))
    }

    // --- the moba -----------------------------------------------------------------------------

    @Test fun `neighbours are who lives here, babies aside`() {
        val people = listOf(Resident("micka", "2026-08-01"), Resident("luka", "2026-08-01"), Resident("n-ana", "2026-08-01", name = "Ana Furlan"))
        val baby = Resident("n-zan", day0.toString(), name = "Žan Furlan", born = day0.toString())
        val child = Resident("n-eva", "2026-06-01", name = "Eva Furlan", born = "2026-06-01") // 92 days: a child, not a baby
        assertEquals(4, Help.helpers(village().copy(residents = people + baby + child)))
        assertEquals(5, Help.helpers(village(villagers = 5))) // no names yet (an older bridge): the population
    }

    @Test fun `a moba covers a share of the wood and stone, as far as the help reaches`() {
        val house = Catalog.spec(BuildingType.HOUSE).cost // 60 🌾 70 🪵 50 🪨
        assertEquals(listOf(5, 7, 8, 10, 11, 13), Age.entries.map { Catalog.mobaPerHelp(it) })
        // three neighbours: 30 %; at Zaselek one 🤝 covers 8
        val three = Help.moba(village(help = 50, villagers = 3), house)!!
        assertEquals(0.3f, three.share, 0.001f)
        assertEquals(mapOf(Res.WOOD to 21, Res.STONE to 15), three.covers)
        assertEquals(5, three.help) // 36 / 8, rounded up
        assertEquals(mapOf(Res.FOOD to 60, Res.WOOD to 49, Res.STONE to 35), three.rest)
        assertEquals("−21 🪵 −15 🪨 za 5 🤝 · for 5 🤝", three.text)
        // seven neighbours: at most half
        val seven = Help.moba(village(help = 50, villagers = 7), house)!!
        assertEquals(mapOf(Res.WOOD to 35, Res.STONE to 25), seven.covers)
        assertEquals(8, seven.help)
        // three 🤝 cover 24 of the 60: the same proportion
        val short = Help.moba(village(help = 3, villagers = 7), house)!!
        assertEquals(mapOf(Res.WOOD to 14, Res.STONE to 10), short.covers)
        assertEquals(3, short.help)
        // nothing to cover, nobody to come, no help: no moba
        assertNull(Help.moba(village(help = 50), Catalog.spec(BuildingType.LIPA).cost))
        assertNull(Help.moba(village(help = 50, villagers = 0), house))
        assertNull(Help.moba(village(help = 0), house))
    }

    @Test fun `building with a moba pays the rest and the help`() {
        // 50 🪵 40 🪨: a house needs 70 🪵 50 🪨, the neighbours bring 35 and 25
        val s = village(help = 20, villagers = 6, res = mapOf(Res.FOOD to 100, Res.WOOD to 50, Res.STONE to 40))
        val option = GameEngine.buildOptions(s).first { it.type == BuildingType.HOUSE }
        assertFalse(option.available)
        assertTrue(option.mobaAvailable)
        assertSame(s, GameEngine.build(s, BuildingType.HOUSE, t0)) // without the neighbours: too poor
        val built = GameEngine.build(s, BuildingType.HOUSE, t0, withMoba = true)
        assertEquals(1, built.buildings.count { it.type == BuildingType.HOUSE })
        assertEquals(mapOf(Res.FOOD to 40, Res.WOOD to 15, Res.STONE to 15), built.resources.filterValues { it > 0 })
        assertEquals(20 - 8, built.help)
        assertEquals(1, built.stats.mobas)
        assertTrue(built.log.last().text, built.log.last().text.endsWith("🤝 moba: −35 🪵 −25 🪨"))
        // still too poor for the rest: the moba shows what it would bring, but nothing is built
        val poor = s.copy(resources = mapOf(Res.FOOD to 100, Res.WOOD to 10, Res.STONE to 5))
        val short = GameEngine.buildOptions(poor).first { it.type == BuildingType.HOUSE }
        assertNotNull(short.moba)
        assertFalse(short.mobaAvailable)
        assertSame(poor, GameEngine.build(poor, BuildingType.HOUSE, t0, withMoba = true))
        // what the age, a limit or the plots forbid, no moba makes possible
        val tabor = village(Age.TABOR, help = 50)
        assertNull(GameEngine.buildOptions(tabor).first { it.type == BuildingType.CHURCH }.moba)
    }

    @Test fun `upgrading and repairing with a moba`() {
        // a palisade to level 2 at Tabor: 60 🌾 80 🪵 60 🪨; five neighbours bring half the 🪵 and 🪨 (70, 7 per 🤝)
        val s = village(Age.TABOR, help = 30, villagers = 5, res = mapOf(Res.FOOD to 60, Res.WOOD to 40, Res.STONE to 30), BuildingType.PALISADE)
        val up = GameEngine.upgradeOption(s, "b0")!!
        assertFalse(up.available)
        assertTrue(up.mobaAvailable)
        assertEquals(10, up.moba!!.help)
        assertSame(s, GameEngine.upgrade(s, "b0", t0))
        val upgraded = GameEngine.upgrade(s, "b0", t0, withMoba = true)
        assertEquals(2, upgraded.buildings[0].level)
        assertEquals(20, upgraded.help)
        // a damaged building must be repaired first: no moba for the upgrade
        val broken = s.copy(buildings = s.buildings.map { it.copy(damaged = true) })
        assertNull(GameEngine.upgradeOption(broken, "b0")!!.moba)
        // the repair: half the build cost (15 🌾 20 🪵 15 🪨), the neighbours bring 10 🪵 7 🪨 for 3 🤝
        val moba = GameEngine.repairMoba(broken, "b0")!!
        assertEquals(mapOf(Res.WOOD to 10, Res.STONE to 7), moba.covers)
        val fixed = GameEngine.repair(broken, "b0", t0, withMoba = true)
        assertFalse(fixed.buildings[0].damaged)
        assertEquals(30 - moba.help, fixed.help)
        assertEquals(40 - 10, fixed.res(Res.WOOD))
        assertNull(GameEngine.repairMoba(fixed, "b0"))
    }

    @Test fun `help and the chest survive a json round trip, and older saves load without them`() {
        var s = GameEngine.completeQuest(withQuest(village(help = 4)), "q1", 6, 6, t0).first
        s = s.copy(chest = s.chest.copy(tools = mapOf("luka" to Tool(tier = 2, forged = 1)), gifted = mapOf("luka" to 4)), lastFeast = day0.toString())
        assertEquals(s, json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), s)))
        val old = json.decodeFromString(GameState.serializer(), """{"seed":1,"age":"TRG","stats":{"eventsWon":2,"questsDone":5},"bonds":{"micka":{"points":40}}}""")
        assertEquals(Age.TRG, old.age)
        assertEquals(0, old.help)
        assertEquals(Inventory(), old.chest)
        assertEquals("", old.lastFeast)
        assertEquals(0, old.stats.helpEarned)
        assertEquals(5, old.stats.questsDone)
        assertNotNull(GameEngine.attributes(old))
    }
}
