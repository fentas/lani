package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Routine
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.Where
import si.lanisce.lani.game.villagers.parseVillagers
import java.io.File
import java.time.LocalDate

/**
 * Who else of the village is by the fire (companion/SCENES.md "Ob ognju by the age", VILLAGERS.md "Round the fire",
 * [FireCrowd]): the fire's scene has its own people at its stage's spots, and the village's others whom their day has at
 * the fire stand or sit at the crowd's spots round it, more as the village grows; nobody else round the first campfire.
 */
class FireCrowdTest {
    private val cultures = listOf(File("../../cultures"), File("../cultures")).first { it.isDirectory }
    private val cast: List<Villager> by lazy {
        File(cultures, "primorska/villagers").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
            .map { parseVillagers("[" + it.readText() + "]").single() }.sortedBy { it.order }
    }
    private val scenes: List<SceneSpec> by lazy {
        listOf(File(cultures, "../scenes"), File(cultures, "primorska/scenes")).flatMap { d -> d.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { parseScene(it.readText()) } }
    }
    private val fire: SceneSpec get() = scenes.first { it.id == "ob-ognju" }

    /** A village of [age] with the whole cast moved in, its houses round the square. */
    private fun village(age: Age): GameState {
        val types = listOf(BuildingType.HUT, BuildingType.FIELD, BuildingType.WELL, BuildingType.KOZOLEC, BuildingType.HOUSE, BuildingType.LIPA,
            BuildingType.BEEHIVE, BuildingType.HOUSE, BuildingType.SMITHY, BuildingType.MARKET, BuildingType.HOUSE, BuildingType.SCHOOL,
            BuildingType.WATCHTOWER, BuildingType.HOUSE, BuildingType.CHURCH)
        return GameState(
            seed = 42, age = age, villagers = cast.size + 3, morale = 80, fire = 80, land = Land.CLASSIC,
            buildings = types.mapIndexed { i, t -> Building("b$i", t, i, level = 2, builtAt = i.toLong()) },
            residents = cast.map { Resident(it.id, "2026-01-01") },
        )
    }

    private fun everyone(s: GameState, date: LocalDate) = cast + Residents.people(s, cast, date).filter { p -> cast.none { it.id == p.id } }

    @Test fun `the village's others round the fire in the evening, at the crowd's spots`() {
        for (age in listOf(Age.TABOR, Age.ZASELEK, Age.VAS, Age.TRG, Age.MESTO)) {
            val s = village(age)
            val spots = FireCrowd.spots(s)
            assertEquals(age.name, FireCrowd.count(age, tent = false), spots.size)
            var seen = 0
            for (d in 0 until 14) {
                val date = LocalDate.of(2026, 10, 5).plusDays(d.toLong())
                val people = everyone(s, date)
                val day = Whereabouts.day(scenes, s, cast, people, date)
                for (minute in listOf(19 * 60 + 30, 20 * 60 + 40)) {
                    val at = date.atStartOfDay().plusMinutes(minute.toLong())
                    val here = Whereabouts.inScene(fire, s, people, at, day, scenes)
                    val crowd = here.filter { FireCrowd.isCrowd(it.slot) }
                    seen += crowd.size
                    assertEquals("${age.name} $at: one at each spot", crowd.size, crowd.map { it.slot }.toSet().size)
                    assertTrue("${age.name} $at: at the age's spots", crowd.all { it.slot in spots })
                    val own = fire.people.mapNotNull { it.villager }.toSet()
                    for (p in crowd) {
                        assertTrue("${age.name} $at: ${p.id} isn't one of the scene's own", p.id !in own)
                        val v = people.first { it.id == p.id }
                        val now = Routine.now(v, s, at, day)
                        assertTrue("${age.name} $at: ${p.id} is at the fire by their day", now.where == Where.OUT && now.place?.general() == TownPlace.Fire)
                    }
                }
            }
            assertTrue("${age.name}: someone of the village by the fire on some evening", seen > 0)
        }
    }

    @Test fun `nobody else round the first campfire, and none at another place`() {
        val s = village(Age.OGENJ)
        val date = LocalDate.of(2026, 10, 5)
        val people = everyone(s, date)
        val day = Whereabouts.day(scenes, s, cast, people, date)
        assertTrue(FireCrowd.spots(s).isEmpty())
        for (h in 0 until 24) assertTrue(Whereabouts.inScene(fire, s, people, date.atTime(h, 10), day, scenes).none { FireCrowd.isCrowd(it.slot) })
        // the field's scene has no crowd at the Vas
        val vas = village(Age.VAS)
        val field = scenes.first { it.art == "field" }
        assertTrue(Whereabouts.inScene(field, vas, everyone(vas, date), date.atTime(10, 0), Whereabouts.day(scenes, vas, cast, everyone(vas, date), date), scenes).none { FireCrowd.isCrowd(it.slot) })
    }

    @Test fun `the tent by the square's fire takes the spots before the homes`() {
        assertEquals(5, FireCrowd.count(Age.VAS))
        assertEquals(4, FireCrowd.count(Age.VAS, tent = true))
        assertEquals(6, FireCrowd.count(Age.MESTO))
        assertEquals(4, FireCrowd.count(Age.MESTO, tent = true))
        assertTrue(FireCrowd.isCrowd("crowd-3") && !FireCrowd.isCrowd("left"))
    }
}
