package si.lanisce.lani.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Surprise
import si.lanisce.lani.game.Surprises
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.ui.home.HomeLogic
import java.time.LocalDate

/** The village's own day on the map, in the scroll and on Home: the festival, the day's surprise, the projects, "Jutri". */
class VillageTodayTest {
    private val martinovo = LocalDate.parse("2026-11-11")
    private val people = listOf("micka", "luka", "zala", "marko", "france", "janez", "ancka", "anton")

    private fun village(age: Age = Age.VAS, day: LocalDate = martinovo, surprise: String? = Surprises.RIDDLE) = GameState(
        age = age, residents = people.map { Resident(it, "2026-01-01") }, villagers = people.size,
        resources = Res.entries.associateWith { 800 }, help = 30,
        buildings = listOf(Building("l", BuildingType.LIPA, 0)),
        surprise = surprise?.let { Surprise(day.toString(), it, 1, if (it == Surprises.RIDDLE) "zala" else null) },
    )

    @Test fun `the map shows the festival under the linden, the surprise at the road, at most two projects`() {
        val m = TownMarkers.villageMarkers(village(), martinovo)
        val festival = m.single { it.id == "festival:martinovo" }
        assertEquals(TownPlace.At(BuildingType.LIPA), festival.place)
        assertEquals("🍷", festival.emoji)
        val surprise = m.single { it.id.startsWith("surprise:") }
        assertEquals(TownPlace.Spot("road"), surprise.place)
        assertEquals(TownMarkers.PROJECT_BUBBLES, m.count { it.id.startsWith("project:") })
        // played: gone from the road
        val done = village().let { it.copy(surprise = it.surprise!!.copy(done = true)) }
        assertTrue(TownMarkers.villageMarkers(done, martinovo).none { it.id.startsWith("surprise:") })
        // without a linden the festival is by the fire; a day when nothing is on, no festival
        assertEquals(TownPlace.Fire, TownMarkers.villageMarkers(village().copy(buildings = emptyList()), martinovo).single { it.id.startsWith("festival:") }.place)
        assertTrue(TownMarkers.villageMarkers(village(day = martinovo.plusDays(5)), martinovo.plusDays(5)).none { it.id.startsWith("festival:") })
        // no projects before Zaselek
        assertTrue(TownMarkers.villageMarkers(village(Age.TABOR), martinovo).none { it.id.startsWith("project:") })
    }

    @Test fun `the scroll's day, what's to do and what's coming`() {
        val v = VillageToday.of(village(), martinovo)
        assertEquals("martinovo", v.festival?.festival?.id)
        assertEquals("Zala ima zate uganko. · Zala has a riddle for you.", v.surpriseText)
        assertTrue(v.projects.first().available)
        assertEquals(3, v.count) // the festival, the riddle, today's step
        assertTrue(v.teasers.isNotEmpty())
        // a few days before: the countdown
        val before = VillageToday.of(village(day = martinovo.minusDays(3)), martinovo.minusDays(3))
        assertNull(before.festival)
        assertEquals(3L, before.countdown?.second)
    }

    @Test fun `home says what's on in the village today and what's coming`() {
        val lines = HomeLogic.village(village(), martinovo)
        assertEquals("🍷 Danes: Martinovo · Today: St Martin's Day", lines.first())
        assertEquals(si.lanisce.lani.game.Tomorrow.teasers(village(), martinovo).first().line, lines.last())
        // no festival: the day's surprise
        val day = LocalDate.parse("2026-10-06")
        assertEquals("🎁 Na cesti te čaka presenečenje · A surprise is waiting on the road", HomeLogic.village(village(day = day), day).first())
        // the pedlar
        assertEquals("🎒 Krošnjar je danes na cesti · The pedlar is on the road today", HomeLogic.village(village(day = day, surprise = Surprises.PEDLAR), day).first())
        // nothing else: today's project step
        val step = HomeLogic.village(village(day = day, surprise = null), day).first()
        assertTrue(step, step.contains("današnji korak 1/"))
        assertTrue(HomeLogic.village(GameState(), day).isEmpty())
        assertNotNull(HomeLogic.village(village(Age.TABOR, day, null), day).firstOrNull())
    }
}
