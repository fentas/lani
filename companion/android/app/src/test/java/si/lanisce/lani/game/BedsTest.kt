package si.lanisce.lani.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.game.culture.Culture
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.parseVillagers
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.game.roomLabel
import java.io.File
import java.time.LocalDate

/**
 * A building that brings its person needs a bed for them (companion/VILLAGERS.md, "A building brings its person"): while
 * every bed is taken the beehive can't be built, the build card says whom it's for and why, and offers the way on in one
 * tap (a dwelling to build or upgrade, or what to gather for it); with no way to make room it isn't held back, and its
 * person moves in once a bed is free. With the real Primorska cast.
 */
class BedsTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { it.resolve("cultures").isDirectory }
    private val cast = parseVillagers(
        companion.resolve("cultures/primorska/villagers").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
            .joinToString(",", "[", "]") { it.readText() },
    )
    private val day = LocalDate.of(2026, 9, 28)
    private val t0 = 1_790_000_000_000L
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

    private fun residents(vararg ids: String) = ids.map { Resident(it, day.toString()) }
    private fun plenty(n: Int = 500) = Res.entries.associateWith { n }

    /** A hamlet of four, every bed taken (the camp's two and a tent's two), with plenty to build with. */
    private val full = GameState(
        seed = 3, age = Age.ZASELEK, villagers = 4, resources = plenty(),
        buildings = listOf(Building("b0", BuildingType.TENT, 0)), residents = residents("micka", "luka", "zala", "janez"),
    )

    private fun option(s: GameState, t: BuildingType) = GameEngine.buildOptions(s, cast).first { it.type == t }

    @Test fun `a workplace whose person has nowhere to live waits, and says whom it is for`() {
        assertEquals(4, GameEngine.attributes(full).populationCap)
        val bees = option(full, BuildingType.BEEHIVE)
        assertFalse(bees.available)
        assertEquals("🛏️ Čebelar Anton potrebuje prostor · Anton needs somewhere to live (4/4)", bees.reason)
        assertNull("no moba past a bed", bees.moba)
        assertSame(full, GameEngine.build(full, BuildingType.BEEHIVE, t0, cast = cast))
        assertEquals("🛏️ Kovač Tone potrebuje prostor · Tone needs somewhere to live (4/4)", option(full, BuildingType.SMITHY).reason)
        // what brings nobody goes up as before; so does all of it without the cast (an older bridge)
        assertTrue(option(full, BuildingType.LIPA).available)
        assertTrue(GameEngine.buildOptions(full).first { it.type == BuildingType.BEEHIVE }.available)
        // with Anton living here the beehive needs no bed for him
        assertTrue(option(full.copy(residents = full.residents + Resident("anton", day.toString())), BuildingType.BEEHIVE).available)
        // nor does it before his age (a pack's person who comes later than their workplace)
        val later = cast.map { if (it.id == "anton") it.copy(since = "vas") else it }
        assertTrue(GameEngine.buildOptions(full, later).first { it.type == BuildingType.BEEHIVE }.available)
        // in the second learner's village's pair the names are the village's and the base's
        L10n.pair = LangPair(Lang.IT, Lang.SL)
        assertTrue(option(full, BuildingType.BEEHIVE).reason!!.endsWith("Anton potrebuje prostor (4/4)"))
    }

    @Test fun `the way on is one tap - a house, and the beehive's beekeeper moves in`() {
        val way = option(full, BuildingType.BEEHIVE).room
        assertEquals(RoomWay(BuildingType.HOUSE), way)
        assertEquals("🏠 Postavi hišo · Build a house ›", roomLabel(way!!))
        val housed = GameEngine.build(full, way.type, t0, cast = cast)
        assertNotSame(full, housed)
        assertEquals(8, GameEngine.attributes(housed).populationCap)
        val bees = option(housed, BuildingType.BEEHIVE)
        assertTrue(bees.available && bees.room == null && bees.reason == null)
        val (s, news) = Residents.reconcile(GameEngine.build(housed, BuildingType.BEEHIVE, t0, cast = cast), cast, day)
        assertEquals(listOf("micka", "luka", "zala", "janez", "anton"), s.residents.map { it.id })
        assertEquals(5, s.villagers)
        assertTrue(news.single().second.startsWith("Čebelar Anton se je preselil v vas"))
    }

    @Test fun `short of what a dwelling costs, the way is to gather it, and with no plot to spare to upgrade one`() {
        // no wood nor stone: every dwelling lacks some, the house most wood
        val poor = full.copy(resources = mapOf(Res.FOOD to 500, Res.WISDOM to 500))
        val way = option(poor, BuildingType.BEEHIVE).room!!
        assertEquals(RoomWay(BuildingType.HOUSE, short = Res.WOOD), way)
        assertEquals("🏠 Za hišo naberi še 🪵 · For a house, gather more 🪵 ›", roomLabel(way))
        // the last free plot is the beehive's: a hut to upgrade instead (the most beds it adds, the first of those)
        val crowded = GameState(
            seed = 3, age = Age.ZASELEK, villagers = 11, resources = plenty(), residents = residents("micka", "luka", "zala", "janez"),
            buildings = listOf(
                BuildingType.TENT, BuildingType.HUT, BuildingType.HOUSE, BuildingType.CHURCH, BuildingType.LIPA, BuildingType.FIELD, BuildingType.FIELD, BuildingType.KOZOLEC,
            ).mapIndexed { i, t -> Building("b$i", t, i) },
        )
        assertEquals(listOf(8), Placement.freePlots(crowded))
        assertEquals(11, GameEngine.attributes(crowded).populationCap)
        val up = option(crowded, BuildingType.BEEHIVE).room!!
        assertEquals(RoomWay(BuildingType.HUT, upgrade = "b1"), up)
        assertEquals("⬆️ Nadgradi kočo · Upgrade the hut ›", roomLabel(up))
        val roomier = GameEngine.upgrade(crowded, "b1", t0)
        assertTrue(option(roomier, BuildingType.BEEHIVE).available)
    }

    @Test fun `with no way to make room the building goes up, and its person moves in once a bed is free`() {
        // every dwelling at the top level the hamlet allows, one plot left, every bed taken: no room can be made now
        val dwellings = listOf(BuildingType.TENT, BuildingType.HUT, BuildingType.HOUSE)
        val buildings = listOf(
            BuildingType.TENT, BuildingType.HUT, BuildingType.HOUSE, BuildingType.CHURCH, BuildingType.LIPA, BuildingType.FIELD, BuildingType.FIELD, BuildingType.KOZOLEC,
        ).mapIndexed { i, t -> Building("b$i", t, i, level = if (t in dwellings) 3 else 1) }
        val beds = GameEngine.attributes(GameState(buildings = buildings)).populationCap
        val neighbours = (1..beds - 4).map { Resident("n-sosed-$it", day.toString(), name = "Sosed$it Novak", voice = "male", family = "Novak$it") }
        val topped = GameState(
            seed = 3, age = Age.ZASELEK, villagers = beds, resources = plenty(), buildings = buildings,
            residents = residents("micka", "luka", "zala", "janez") + neighbours,
        )
        assertNull(GameEngine.roomWay(topped, keep = 1))
        val bees = option(topped, BuildingType.BEEHIVE)
        assertTrue("never a wall: ${bees.reason}", bees.available)
        val built = GameEngine.build(topped, BuildingType.BEEHIVE, t0, cast = cast)
        // every bed taken: he waits, first in the outlook
        val (waiting, news) = Residents.reconcile(built, cast, day)
        assertFalse(waiting.residents.any { it.id == "anton" })
        assertTrue(news.isEmpty())
        assertEquals("Čebelar Anton", Residents.outlook(waiting, cast, day).first().who)
        // a bed comes free (a neighbour moved away): he moves in at once, and takes it
        val (s, _) = Residents.reconcile(waiting.copy(villagers = waiting.villagers - 1), cast, day)
        assertTrue(s.residents.any { it.id == "anton" })
        assertEquals(beds, s.villagers)
        assertEquals(beds, s.residents.size)
    }
}
