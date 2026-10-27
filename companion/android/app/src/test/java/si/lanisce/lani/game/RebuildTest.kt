package si.lanisce.lani.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.game.BuildingType.BEEHIVE
import si.lanisce.lani.game.BuildingType.CHURCH
import si.lanisce.lani.game.BuildingType.FIELD
import si.lanisce.lani.game.BuildingType.HOUSE
import si.lanisce.lani.game.BuildingType.HUT
import si.lanisce.lani.game.BuildingType.KOZOLEC
import si.lanisce.lani.game.BuildingType.LIPA
import si.lanisce.lani.game.BuildingType.MARKET
import si.lanisce.lani.game.BuildingType.SCHOOL
import si.lanisce.lani.game.BuildingType.SMITHY
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.BuildingType.WATCHTOWER
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.culture.Culture
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.parseVillagers
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.LangPair
import java.io.File

/**
 * Growing up and replacing (companion/GAME.md "Buildings", "When every plot is built"): a tent grows into a hut and a hut
 * into a house on its plot, its level kept, for what the bigger one costs more; a building the next age doesn't need can
 * be replaced, the people staying; and a building held back only by the plots says which way it can still go up.
 */
class RebuildTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { it.resolve("cultures").isDirectory }
    private val cast = parseVillagers(
        companion.resolve("cultures/primorska/villagers").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
            .joinToString(",", "[", "]") { it.readText() },
    )
    private lateinit var was: Culture
    private lateinit var pair: LangPair

    @Before fun setUp() {
        was = Cultures.home
        pair = L10n.pair
        L10n.pair = LangPair.DEFAULT
        Cultures.use(Cultures.DEFAULT)
    }

    @After fun tearDown() {
        Cultures.use(was)
        L10n.pair = pair
    }

    private fun plenty(n: Int = 5000) = Res.entries.associateWith { n }
    private fun b(id: String, type: BuildingType, plot: Int, level: Int = 1, damaged: Boolean = false) = Building(id, type, plot, level, damaged)

    /** A market town with every plot built: tents, fields and huts where Mesto's houses, hayracks and smithy would go. */
    private val walled = GameState(
        seed = 7, age = Age.TRG, villagers = 20, resources = plenty(),
        buildings = listOf(
            b("pond", TENT, NO_PLOT, 2), b("t1", TENT, 0, 2), b("t2", TENT, 1), b("t3", TENT, 2), b("f1", FIELD, 3), b("f2", FIELD, 4),
            b("f3", FIELD, 5), b("h1", HUT, 6, 3), b("h2", HUT, 7), b("w1", WELL, 8), b("w2", WELL, 9), b("bee", BEEHIVE, 10),
            b("lipa", LIPA, 11), b("church", CHURCH, 12), b("school", SCHOOL, 13), b("market", MARKET, 14), b("tower", WATCHTOWER, 15),
            b("k1", KOZOLEC, 16), b("f4", FIELD, 17), b("bee2", BEEHIVE, 18), b("bee3", BEEHIVE, 19),
        ),
        residents = listOf(Resident("anton", "2026-01-01")),
    )

    // --- growing up --------------------------------------------------------------------------------------------------

    @Test fun `a tent grows into a hut and a hut into a house, on its plot, its level kept`() {
        val tent = Rebuild.growOption(walled, "t1")!!
        assertEquals(HUT, tent.to)
        // what a hut costs more than a tent, built and upgraded to level 2: (5, 25, 10) × 3
        assertEquals(mapOf(Res.FOOD to 15, Res.WOOD to 75, Res.STONE to 30), tent.cost)
        assertTrue(tent.available)
        val grown = Rebuild.grow(walled, "t1", 99L)
        val hut = grown.buildings.single { it.plot == 0 }
        assertEquals(HUT, hut.type)
        assertEquals(2, hut.level)
        assertEquals(99L, hut.builtAt)
        assertTrue("a new building of its kind: its own id", hut.id.startsWith("hut-") && grown.buildings.none { it.id == "t1" })
        assertEquals(walled.res(Res.WOOD) - 75, grown.res(Res.WOOD))
        assertEquals("🛖", grown.log.last().emoji)
        assertEquals("Preraslo: Šotor → Koča · Grew up: Tent → Hut", grown.log.last().text)
        assertEquals(walled.buildings.size, grown.buildings.size)
        // a hut into a house: (30, 30, 40) × 6 at level 3
        val house = Rebuild.growOption(walled, "h1")!!
        assertEquals(HOUSE, house.to)
        assertEquals(mapOf(Res.FOOD to 180, Res.WOOD to 180, Res.STONE to 240), house.cost)
        assertEquals("+9 👥", house.effect)
    }

    @Test fun `what can't grow - a house, the tent at the pond, the village's first tent, before the age`() {
        assertNull(Rebuild.growOption(walled, "pond"))
        assertNull(Rebuild.growOption(walled, "f1"))
        assertNull(Rebuild.growOption(walled.copy(buildings = walled.buildings + b("house", HOUSE, 20)), "house"))
        // no tent at the pond yet: the first tent on a plot stays a tent (the shepherd moves it there)
        val camp = GameState(age = Age.TABOR, resources = plenty(), buildings = listOf(b("a", TENT, 0), b("b", TENT, 1)))
        assertNull(Rebuild.growOption(camp, "a"))
        assertEquals(HUT, Rebuild.growOption(camp, "b")?.to)
        // a hut grows into a house from the hamlet on, when there are houses
        assertNull(Rebuild.growOption(camp.copy(buildings = camp.buildings + b("h", HUT, 2)), "h"))
        // at the campfire there are no huts yet
        assertNull(Rebuild.growOption(camp.copy(age = Age.OGENJ), "b"))
        // damaged: repaired first; short of something: what is missing
        val broken = walled.copy(buildings = walled.buildings.map { if (it.id == "t2") it.copy(damaged = true) else it })
        assertFalse(Rebuild.growOption(broken, "t2")!!.available)
        assertSame(broken, Rebuild.grow(broken, "t2", 1L))
        val poor = walled.copy(resources = emptyMap())
        assertTrue(Rebuild.growOption(poor, "t2")!!.reason!!.contains("🪵"))
        assertSame(poor, Rebuild.grow(poor, "t2", 1L))
    }

    @Test fun `a hut rebuilt as a house says so, and keeps who lives in the village`() {
        val s = Rebuild.grow(walled, "h2", 5L)
        assertEquals("Prezidano: Koča → Hiša · Rebuilt: Hut → House", s.log.last().text)
        assertEquals(walled.villagers, s.villagers)
        assertEquals(walled.residents, s.residents)
        assertTrue(GameEngine.attributes(s).populationCap > GameEngine.attributes(walled).populationCap)
    }

    // --- taking down --------------------------------------------------------------------------------------------------

    @Test fun `taking a building down frees its plot, gives half its materials back, and never leaves anyone without a bed`() {
        val s = walled.copy(resources = mapOf(Res.FOOD to 10))
        assertEquals(mapOf(Res.FOOD to 15, Res.WOOD to 10), Rebuild.salvage(s.buildings.single { it.id == "f1" }))
        val down = Rebuild.takeDown(s, "f1", 3L, cast)
        assertNull(down.buildings.firstOrNull { it.id == "f1" })
        assertEquals(listOf(3), Placement.freePlots(down))
        assertEquals(25, down.res(Res.FOOD))
        assertEquals(10, down.res(Res.WOOD))
        assertEquals("🔨", down.log.last().emoji)
        assertEquals("Podrto: Njiva · Taken down: Field", down.log.last().text)
        // what may not be replaced may not be taken down either: the village's own buildings, one the next age needs
        assertSame(s, Rebuild.takeDown(s, "church", 3L, cast))
        assertSame(s, Rebuild.takeDown(s, "k1", 3L, cast))
        // every bed taken: a hut can't come down until there is room for its people
        val full = s.copy(villagers = GameEngine.attributes(s).populationCap)
        assertEquals(GameEngine.attributes(full).populationCap - 3 to full.villagers, Rebuild.bedsShort(full, full.buildings.single { it.id == "h2" }))
        assertFalse(Rebuild.canTakeDown(full, "h2", cast))
        assertSame(full, Rebuild.takeDown(full, "h2", 3L, cast))
        // nor be replaced by fewer beds, though by a house it can
        val rich = full.copy(resources = plenty())
        assertFalse(Rebuild.replaceOptions(rich, "h2", cast).single { it.type == BuildingType.SMITHY }.available)
        assertTrue(Rebuild.replaceOptions(rich, "h2", cast).single { it.type == HOUSE }.available)
        assertSame(rich, Rebuild.replace(rich, "h2", BuildingType.SMITHY, 3L, cast = cast))
        // a field has no beds: it comes down whatever
        assertTrue(Rebuild.canTakeDown(full, "f2", cast))
    }

    // --- replacing ----------------------------------------------------------------------------------------------------

    @Test fun `only a common building the next age doesn't need can be replaced`() {
        fun keeps(id: String, s: GameState = walled) = Rebuild.keeps(s, s.buildings.single { it.id == id }, cast)
        // the village's own buildings stay
        for (id in listOf("lipa", "church", "school", "market", "tower")) assertEquals(id, Rebuild.Keep.KIND, keeps(id))
        // so does the tent at the pond (on no plot)
        assertEquals(Rebuild.Keep.KIND, keeps("pond"))
        // Mesto needs two hayracks: the only one stays
        assertEquals(Rebuild.Keep.NEEDED, keeps("k1"))
        // tents, fields, huts, wells, a bee house of three: they may go
        for (id in listOf("t1", "t2", "f1", "h1", "w1", "bee")) assertNull(id, keeps(id))
        // the beekeeper's last bee house stays while he lives here
        val oneHive = walled.copy(buildings = walled.buildings.filter { it.id != "bee2" && it.id != "bee3" })
        assertEquals(Rebuild.Keep.WORKPLACE, keeps("bee", oneHive))
        // the hamlet's first tent, while none stands at the pond
        val hamlet = GameState(age = Age.ZASELEK, buildings = listOf(b("a", TENT, 0), b("b", TENT, 1), b("f", FIELD, 2)))
        assertEquals(Rebuild.Keep.FIRST_TENT, keeps("a", hamlet))
        assertNull(keeps("b", hamlet))
        // Vas needs two huts or houses: while there are two, neither can go
        val two = hamlet.copy(buildings = hamlet.buildings + b("h", HUT, 3) + b("ho", HOUSE, 4))
        assertEquals(Rebuild.Keep.NEEDED, keeps("h", two))
        assertNull(keeps("h", two.copy(buildings = two.buildings + b("h2", HUT, 5))))
    }

    @Test fun `a building replaced comes down, the new one goes up on its plot, and nobody leaves`() {
        val opts = Rebuild.replaceOptions(walled, "t2", cast)
        // what Mesto needs is offered (its last plots are kept for it), not another tent, not what stands at its most
        assertTrue(opts.any { it.type == HOUSE && it.available })
        assertTrue(opts.none { it.type == TENT || it.type == MARKET || it.type == LIPA })
        val s = Rebuild.replace(walled, "t2", HOUSE, 500L, cast = cast)
        assertNull(s.buildings.firstOrNull { it.id == "t2" })
        val house = s.buildings.single { it.plot == 1 }
        assertEquals(HOUSE, house.type)
        assertEquals(1, house.level)
        assertEquals(walled.villagers, s.villagers)
        assertEquals(walled.residents, s.residents)
        assertEquals(walled.res(Res.FOOD) - 60, s.res(Res.FOOD))
        assertEquals("🔁", s.log.last().emoji)
        assertEquals("Nadomeščeno: Šotor → Hiša · Replaced: Tent → House", s.log.last().text)
        // nothing changes for what can't be: the same kind, a kept building, one the next age doesn't let in now
        assertSame(walled, Rebuild.replace(walled, "t2", TENT, 1L, cast = cast))
        assertSame(walled, Rebuild.replace(walled, "k1", HOUSE, 1L, cast = cast))
        val poor = walled.copy(resources = emptyMap())
        assertSame(poor, Rebuild.replace(poor, "t2", HOUSE, 1L, cast = cast))
    }

    @Test fun `with every plot built, a building held back by the plots says its way`() {
        val opts = GameEngine.buildOptions(walled, cast).associateBy { it.type }
        // a house: the hut grows (the lowest level first)
        assertEquals(PlotWay.Grow("h2", HUT, HOUSE), opts.getValue(HOUSE).plotWay)
        // a hut: a tent grows (not the village's first, which stands at the pond already: any of them)
        assertEquals(TENT, (opts.getValue(HUT).plotWay as PlotWay.Grow).from)
        // the smithy: a building not needed makes room, of a kind there are several of, the cheapest to lose
        val smithy = opts.getValue(SMITHY).plotWay as PlotWay.Replace
        assertEquals(TENT, smithy.what)
        assertFalse(smithy.building == "t1") // the tent at level 2 is worth more than one at level 1
        // what the age doesn't have or has at its most has none
        assertNull(opts.getValue(MARKET).plotWay)
        // with a plot free, nothing in the way but the price: no way through the plots
        val free = walled.copy(buildings = walled.buildings.filter { it.id != "bee3" })
        assertTrue(GameEngine.buildOptions(free, cast).all { it.plotWay == null })
    }

    @Test fun `growing and replacing leave the plots and the next age's rules as they are`() {
        // every plot built, nine buildings missing for Mesto: one way after another until it has them
        var s = walled
        var steps = 0
        while (steps++ < 30) {
            val types = Catalog.ageRules.getValue(Age.MESTO).needs.firstOrNull { (t, n) -> s.buildings.count { it.type in t } < n }?.first ?: break
            val o = GameEngine.buildOptions(s, cast).first { it.type in types }
            s = when (val w = o.plotWay) {
                is PlotWay.Grow -> Rebuild.grow(s, w.building, steps.toLong())
                is PlotWay.Replace -> {
                    // in its place: built, or first a bed made for whom it brings (the smith), as its card on the sheet says
                    val r = Rebuild.replaceOptions(s, w.building, cast).first { it.type == o.type }
                    if (r.available) Rebuild.replace(s, w.building, o.type, steps.toLong(), cast = cast)
                    else GameEngine.upgrade(s, r.room?.upgrade ?: error("no way to ${o.type} for ${w.building}: ${r.reason}"), steps.toLong())
                }
                null -> if (o.available) GameEngine.build(s, o.type, steps.toLong(), cast = cast) else error("no way to ${o.type}: ${o.reason}")
            }
        }
        val check = GameEngine.advanceCheck(s, 5000)
        assertTrue(check.steps.filter { it.kind == AgeStep.Kind.BUILDING }.all { it.done })
        assertEquals(PLOTS_PER_AGE[Age.TRG.ordinal], s.plotsTaken)
    }
}
