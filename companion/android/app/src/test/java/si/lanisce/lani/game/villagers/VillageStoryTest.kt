package si.lanisce.lani.game.villagers

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.Readings
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.culture.Culture
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.DialogsHeard
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.io.File
import java.time.LocalDate

/**
 * No breaks in the village's story (companion/VILLAGERS.md, "A building brings its person" and "Who a text may name"),
 * in the Primorska village as it stood on 27 September 2026: a hamlet of five (Micka, Luka, Zala, Janez, France) with a beehive and
 * no beekeeper, where Zala's honey said "Jan! Anton toči med!" of someone Jan had never met. With the real cast, the real
 * bee house and Primorska's arrivals.
 */
class VillageStoryTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { it.resolve("cultures").isDirectory }
    private val cast = parseVillagers(
        companion.resolve("cultures/primorska/villagers").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
            .joinToString(",", "[", "]") { it.readText() },
    )
    private val bees = parseScene(companion.resolve("scenes/pri-cebelnjaku.json").readText())
    private val day = LocalDate.of(2026, 9, 27)
    private lateinit var was: Culture
    private lateinit var pair: LangPair

    /** The Primorska village (data/app/game.json that evening, the parts that count here). */
    private val jans = GameState(
        seed = 8993091993378563000, age = Age.ZASELEK, villagers = 5,
        buildings = listOf(
            Building("b0", BuildingType.TENT, 0), Building("b1", BuildingType.FIELD, 1), Building("b2", BuildingType.HUT, 2, level = 2),
            Building("b3", BuildingType.WELL, 3), Building("b4", BuildingType.PALISADE, NO_PLOT), Building("b5", BuildingType.KOZOLEC, 4),
            Building("b6", BuildingType.BEEHIVE, 5, level = 2), Building("b7", BuildingType.CHURCH, 6), Building("b8", BuildingType.HOUSE, 7),
        ),
        residents = listOf(
            Resident("micka", "2026-09-24"), Resident("luka", "2026-09-24"), Resident("zala", "2026-09-25"),
            Resident("janez", "2026-09-26"), Resident("france", "2026-09-27"),
        ),
        bonds = mapOf(
            "micka" to Bond(points = 65, met = "2026-09-25"), "luka" to Bond(points = 89, met = "2026-09-25"),
            "zala" to Bond(points = 9, met = "2026-09-27"), "janez" to Bond(points = 26, met = "2026-09-26"),
            "france" to Bond(points = 16, met = "2026-09-26"),
        ),
        migrated = setOf("hayracks-by-fields", Arrivals.MIGRATION),
    )

    @Before fun setUp() {
        was = Cultures.home
        pair = L10n.pair
        L10n.pair = LangPair.DEFAULT
        Cultures.use(Cultures.DEFAULT)
    }

    @After fun tearDown() {
        Cultures.use(was)
        L10n.pair = pair
        Mentions.cast = emptyList()
        Mentions.known = null
    }

    /** The bee house's happenings that come up over [days] days from [from], at every part of the day. */
    private fun onAtBees(s: GameState, from: LocalDate, days: Int = 21, cast: List<Villager> = this.cast): Set<String> =
        playedAtBees(s, from, days, cast).map { it.first }.toSet()

    /** What comes up at the bee house over [days] days from [from]: each happening with the dialog it plays that day (its variant). */
    private fun playedAtBees(s: GameState, from: LocalDate, days: Int = 21, cast: List<Villager> = this.cast): Set<Pair<String, String?>> =
        (0 until days).flatMap { d -> listOf(8, 14, 19).flatMap { h -> Happenings.active(listOf(bees), s, from.plusDays(d.toLong()).atTime(h, 0), cast = cast) } }
            .map { it.happening.id to (it.dialog ?: it.happening.dialog) }.toSet()

    /** Whether anything in [played] (happenings with their dialogs of the day) talks of Anton. */
    private fun talksOfAnton(played: Set<Pair<String, String?>>): Boolean {
        val names = Mentions.names("sl", cast)
        return played.any { (_, d) -> "anton" in Mentions.inDialog(bees.dialogs.firstOrNull { it.id == d }, names) }
    }

    @Test fun `the beehive brings Anton, and nobody at the bee house talks of him until Jan has met him`() {
        // as it was: the bee house is open, but nobody there talks of Anton ("Anton toči med!" waits), and Anton isn't
        // there to show his hives; Zala's honey and Janez's swarm come up in their variants that don't name him
        // (Zala's other honey talks heard a while ago: "Anton toči med!" is the one she hasn't said yet)
        val heard = jans.copy(dialogsHeard = mapOf("pri-cebelnjaku/med" to DialogsHeard(mapOf("med-zakaj" to day.minusDays(9).toString(), "med-pik" to day.minusDays(8).toString()), "med-pik")))
        assertTrue("without the rule Zala's \"Anton toči med!\" came up (the break Jan found)", ("med" to "med") in playedAtBees(heard, day, cast = emptyList()))
        val before = playedAtBees(heard, day)
        assertTrue("$before", !talksOfAnton(before) && "panji" !in before.map { it.first } && ("med" to "med") !in before)
        assertTrue("the honey comes in a variant without him: $before", before.any { it.first == "med" })
        assertEquals("the beehive brings the beekeeper", "anton", Residents.brings(BuildingType.BEEHIVE, cast)?.id)
        // the next day the village settles: beds to spare, so Anton moves in with his hives at once, before Tine and Ančka
        assertEquals(13, si.lanisce.lani.game.GameEngine.attributes(jans).populationCap)
        val next = day.plusDays(1)
        val (s, news) = Residents.reconcile(jans, cast, next)
        assertEquals(listOf("micka", "luka", "zala", "janez", "france", "anton"), s.residents.map { it.id })
        assertEquals(6, s.villagers)
        assertEquals(listOf("🐝" to "Čebelar Anton se je preselil v vas · Čebelar Anton moved into the village"), news)
        // his introduction waits for Jan: nobody talks of him, nor does he stand at his hives, before it
        val p = Arrivals.ready(s, next).single()
        assertEquals("anton" to PendingArrival.Kind.CAST, p.id to p.kind)
        assertFalse(p.late)
        val m = checkNotNull(Arrivals.meeting(p, s, cast, "A1", next))
        assertEquals("Dober dan. Tiho, prosim, čebele. Jaz sem Anton, čebelar.", m.dialog.lines.first().sl)
        assertTrue(!talksOfAnton(playedAtBees(s, next)) && "panji" !in onAtBees(s, next))
        // met: his hives in the morning, Janez's swarm in the afternoon and Zala's honey in the evening come, by the day's dice
        val met = Bonds.add(s, "anton", Bonds.DIALOG, next, meet = true)
        assertEquals(setOf("panji", "roj", "med"), onAtBees(met, next))
    }

    @Test fun `a request, a line and a reading that name someone Jan doesn't know wait for them`() {
        val today = day.plusDays(1)
        val s = Residents.reconcile(jans, cast, today).first
        // a request of Zala's about Anton's honey: not before Jan has met him
        val q = Quest("zala-med", "Zala", "👧", "Med za babico · Honey for Grandma", "Zala nese Antonu prazen kozarec. · Zala takes Anton an empty jar.", Res.FOOD, mapOf(Res.FOOD to 20))
        val asks = s.copy(quests = listOf(q))
        assertTrue(Residents.openQuests(asks, cast, today).isEmpty())
        assertEquals(listOf("zala-med"), Residents.openQuests(Bonds.add(asks, "anton", Bonds.DIALOG, today, meet = true), cast, today).map { it.id })
        // Zala doesn't talk of Nejc, who doesn't live here yet
        val zala = cast.first { it.id == "zala" }
        Mentions.cast = cast
        Mentions.known = Residents.present(s, today)
        val idle = zala.lines.idle.at(TimeOfDay.AFTERNOON).map { it.target }
        assertTrue(idle.isNotEmpty() && idle.none { "Nejc" in it })
        assertTrue(zala.lines.greet.at(TimeOfDay.AFTERNOON).none { "Nejc" in it.target })
        // the reading corner: Marko's letter ("Vida bo prinesla kosilo") waits for Marko and Vida; Prešeren's Zdravljica
        // names no one of the village
        fun corner() = Readings.corner(Cultures.library("primorska"), "A1", emptySet(), Lang.SL).map { it.id }
        assertFalse("pismo-od-marka" in corner())
        assertTrue("zdravljica" in corner())
        Mentions.known = Residents.present(s, today)!! + "marko"
        assertFalse("pismo-od-marka" in corner())
        Mentions.known = Residents.present(s, today)!! + "marko" + "vida"
        assertTrue("pismo-od-marka" in corner())
    }

    @Test fun `an introduction names only people Jan knows, at an easier level when it must`() {
        // Mojca comes with the school; at B1 she teaches "vse otroke, od Tineta do Zale": Tine isn't here yet
        val school = jans.copy(
            age = Age.VAS, buildings = jans.buildings + Building("b9", BuildingType.SCHOOL, 8),
        )
        val today = day.plusDays(1)
        val s = Residents.reconcile(school, cast, today).first
        assertTrue(s.residents.any { it.id == "mojca" })
        val p = Arrivals.pending(s, today).first { it.id == "mojca" }
        assertEquals("A2", Arrivals.meeting(p, s, cast, "B1", today)?.level)
        // with Tine here and met, the B1 one
        val tine = s.copy(residents = s.residents + Resident("tine", "2026-09-28"), bonds = s.bonds + ("tine" to Bond(points = 5, met = "2026-09-28")))
        assertEquals("B1", Arrivals.meeting(p, tine, cast, "B1", today)?.level)
    }
}
