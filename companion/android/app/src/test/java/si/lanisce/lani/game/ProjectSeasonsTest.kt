package si.lanisce.lani.game

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.culture.Culture
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.culture.Text
import si.lanisce.lani.game.scene.SceneNeeds
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Memory
import si.lanisce.lani.game.villagers.Resident
import java.time.LocalDate
import java.time.MonthDay

/**
 * The seasons of the projects' landmarks (game/ProjectSeasons.kt, companion/GAME.md "Village projects"): the maypole is
 * raised on the eve of May Day and taken down after May, and stands at least two weeks once raised; the tick keeps which
 * landmarks are down and tells the chronicle once when the village raises one again or takes it down; the map, the scenes
 * and their places leave a landmark down out; the project stays finished.
 */
class ProjectSeasonsTest {
    private val may = Stands(MonthDay.of(4, 30), MonthDay.of(5, 31), 14, Text(mapOf("sl" to "gor", "en" to "up")), Text(mapOf("sl" to "dol", "en" to "down")))
    private fun d(s: String): LocalDate = LocalDate.parse(s)

    /** Primorska with a season on its maypole, as its projects.json gives it (added here when the pack has none yet). */
    private val seasonal: Culture by lazy {
        Cultures.parse("primorska") { name ->
            val raw = javaClass.getResourceAsStream("/cultures/primorska/$name")?.use { it.readBytes().decodeToString() }
            if (name != "projects.json" || raw == null || "\"stands\"" in raw) raw else withStands(raw)
        }
    }

    private fun withStands(raw: String): String {
        val doc = json.parseToJsonElement(raw).jsonObject
        val stands = json.parseToJsonElement(
            """{"raised": "04-30", "until": "05-31", "min_days": 14,
               "up": {"sl": "Fantje so postavili mlaj!", "en": "The young men raised the maypole!"},
               "down": {"sl": "Fantje so podrli mlaj. Do prihodnjega maja!", "en": "The young men took the maypole down. Until next May!"}}""",
        )
        val projects = (doc.getValue("projects") as JsonArray).map { p ->
            if (p.jsonObject.getValue("id").jsonPrimitive.content == "mlaj") JsonObject(p.jsonObject + ("stands" to stands)) else p
        }
        return JsonObject(doc + ("projects" to JsonArray(projects))).toString()
    }

    @Before fun use() {
        Cultures.use(seasonal)
    }

    @After fun back() {
        ProjectSeasons.qaDay = null
        Cultures.use(Cultures.DEFAULT)
    }

    private fun mlaj() = Projects.spec("mlaj")!!

    /** A hamlet whose maypole was finished on [on] (null: before the game kept the day), ticked up to [lastTick]. */
    private fun village(on: String?, lastTick: String = on ?: "2026-10-10", mlaj: Int = 5) = GameState(
        age = Age.ZASELEK, residents = listOf("luka", "marko").map { Resident(it, "2026-08-01") }, villagers = 2,
        projects = mapOf("mlaj" to mlaj), projectsFinished = if (on == null || mlaj < 5) emptyMap() else mapOf("mlaj" to on), lastTick = lastTick,
    )

    // ---------------------------------------------------------------- the rule

    @Test fun `raised in April it stands through May, down on the first of June`() {
        for (day in listOf("2026-04-20", "2026-04-29", "2026-04-30", "2026-05-17", "2026-05-31")) assertTrue(day, ProjectSeasons.standsOn(may, d("2026-04-20"), d(day)))
        assertFalse(ProjectSeasons.standsOn(may, d("2026-04-20"), d("2026-06-01")))
        assertFalse(ProjectSeasons.standsOn(may, d("2026-04-20"), d("2026-04-19")))
        // raised on the eve itself, as the village does every year
        assertEquals(d("2026-06-01"), ProjectSeasons.downOn(may, d("2026-04-30")))
        // and again the next year
        assertTrue(ProjectSeasons.standsOn(may, d("2026-04-20"), d("2027-05-02")))
        assertFalse(ProjectSeasons.standsOn(may, d("2026-04-20"), d("2027-04-29")))
    }

    @Test fun `raised outside its season it stands two weeks`() {
        assertEquals(d("2026-10-24"), ProjectSeasons.downOn(may, d("2026-10-10")))
        assertTrue(ProjectSeasons.standsOn(may, d("2026-10-10"), d("2026-10-23")))
        assertFalse(ProjectSeasons.standsOn(may, d("2026-10-10"), d("2026-10-24")))
        assertFalse(ProjectSeasons.standsOn(may, d("2026-10-10"), d("2027-03-01")))
        assertTrue(ProjectSeasons.standsOn(may, d("2026-10-10"), d("2027-04-30")))
        // late in May: two weeks all the same, past the end of the month
        assertEquals(d("2026-06-08"), ProjectSeasons.downOn(may, d("2026-05-25")))
        // early in spring: two weeks, then up again on the eve of May Day; just before the eve it stays up for May
        assertEquals(d("2026-03-15"), ProjectSeasons.downOn(may, d("2026-03-01")))
        assertEquals(d("2026-06-01"), ProjectSeasons.downOn(may, d("2026-04-20")))
    }

    @Test fun `without a day it counts as raised long ago, up only in its season`() {
        for (day in listOf("2026-10-11", "2026-04-29", "2026-06-01", "2027-01-01")) assertFalse(day, ProjectSeasons.standsOn(may, null, d(day)))
        for (day in listOf("2026-04-30", "2026-05-01", "2026-05-31")) assertTrue(day, ProjectSeasons.standsOn(may, null, d(day)))
    }

    @Test fun `a village without the maypole has nothing down, ever`() {
        val none = GameState(age = Age.ZASELEK)
        val started = village(null, mlaj = 3)
        for (day in listOf("2026-10-11", "2026-05-10", "2027-01-01")) {
            assertTrue(ProjectSeasons.down(none, d(day)).isEmpty())
            assertTrue(ProjectSeasons.down(started, d(day)).isEmpty())
        }
        // a project without a season stands all year once finished
        val bridge = GameState(age = Age.ZASELEK, projects = mapOf("most" to Projects.spec("most")!!.steps.size))
        assertNull(Projects.spec("most")!!.stands)
        assertTrue(ProjectSeasons.down(bridge, d("2026-10-11")).isEmpty())
    }

    // ---------------------------------------------------------------- the day it was finished

    @Test fun `the day the last step is done is kept`() {
        val day = d("2026-10-10")
        var s = village(null, lastTick = day.toString(), mlaj = 4).copy(
            resources = Res.entries.associateWith { 2000 }, help = 50, chest = Inventory(goods = mapOf("potica" to 2)),
        )
        val (done, r) = Projects.finishStep(s, "mlaj", 5, 5, day, noon(day))
        assertTrue(r.message, r.won)
        assertEquals(mapOf("mlaj" to "2026-10-10"), done.projectsFinished)
        assertEquals(day, ProjectSeasons.finishedOn(done, mlaj()))
        s = done
        assertTrue(Projects.standing(s, mlaj()))
    }

    @Test fun `a maypole finished before the game kept the day goes by its people's memory of it`() {
        val s = village(null).copy(bonds = mapOf("luka" to Bond(30, listOf(Memory("2026-10-11", "naš mlaj", "our maypole", "project")))))
        assertEquals(d("2026-10-11"), ProjectSeasons.finishedOn(s, mlaj()))
        assertTrue(ProjectSeasons.down(s, d("2026-10-24")).isEmpty())
        assertEquals(setOf("mlaj"), ProjectSeasons.down(s, d("2026-10-25")))
        // another memory of the day isn't it
        val other = village(null).copy(bonds = mapOf("luka" to Bond(30, listOf(Memory("2026-10-11", "naš most", "our bridge", "project")))))
        assertNull(ProjectSeasons.finishedOn(other, mlaj()))
        assertEquals(setOf("mlaj"), ProjectSeasons.down(other, d("2026-10-11")))
    }

    // ---------------------------------------------------------------- the tick

    @Test fun `the village takes it down and raises it again, each told once`() {
        val st = mlaj().stands!!
        val down = "${mlaj().emoji} ${st.downText.bi()}"
        val up = "${mlaj().emoji} ${st.upText.bi()}"
        var s = village("2026-10-10")
        val told = ArrayList<Pair<String, String>>()
        fun day(on: String) {
            val r = GameEngine.tick(s, d(on), noon(d(on)), Fixtures.pool())
            s = r.state
            for (e in r.news) if ("${e.emoji} ${e.text}" in listOf(down, up)) told += on to "${e.emoji} ${e.text}"
        }
        var t = d("2026-10-11")
        while (t <= d("2026-10-30")) { day(t.toString()); t = t.plusDays(1) }
        assertEquals(listOf("2026-10-24" to down), told)
        assertEquals(setOf("mlaj"), s.landmarksDown)
        // nothing on the map nor on the square; the project stays finished, its bonus with it
        assertTrue(Projects.finished(s, mlaj()))
        assertFalse(Projects.standing(s, mlaj()))
        assertTrue(Projects.landmarks(s).single().down)
        assertTrue(SceneWorld.of(s).isDown("mlaj"))
        assertEquals(3, Projects.effect(s).morale)
        // the winter passes; the eve of May Day it's up again, by itself
        s = s.copy(lastTick = "2027-04-27")
        told.clear()
        t = d("2027-04-28")
        while (t <= d("2027-06-03")) { day(t.toString()); t = t.plusDays(1) }
        assertEquals(listOf("2027-04-30" to up, "2027-06-01" to down), told)
    }

    @Test fun `an older save with its maypole up out of season is taken down at the first tick, with its line`() {
        val s = village(null, lastTick = "2026-10-10")
        val r = GameEngine.tick(s, d("2026-10-11"), noon(d("2026-10-11")), Fixtures.pool())
        assertEquals(setOf("mlaj"), r.state.landmarksDown)
        assertEquals(1, r.news.count { it.text == mlaj().stands!!.downText.bi() })
        // the next tick finds it kept: no line again
        val again = GameEngine.tick(r.state, d("2026-10-12"), noon(d("2026-10-12")), Fixtures.pool())
        assertEquals(0, again.news.count { it.text == mlaj().stands!!.downText.bi() })
    }

    @Test fun `QA's day shows it as on that day, without a line`() {
        val s = village("2026-10-10").copy(landmarksDown = setOf("mlaj"))
        ProjectSeasons.qaDay = d("2027-05-10")
        val up = ProjectSeasons.update(s, d("2026-10-30"), noon(d("2026-10-30")))
        assertTrue(up.landmarksDown.isEmpty())
        assertEquals(s.log, up.log)
        ProjectSeasons.qaDay = d("2026-10-30")
        val down = ProjectSeasons.update(up, d("2026-10-30"), 0)
        assertEquals(setOf("mlaj"), down.landmarksDown)
        // let go: as today again, still without a line
        ProjectSeasons.qaDay = null
        val today = ProjectSeasons.update(down, d("2026-10-20"), 0, tell = false)
        assertTrue(today.landmarksDown.isEmpty())
        assertEquals(s.log, today.log)
    }

    // ---------------------------------------------------------------- what shows it

    @Test fun `down, the scenes and the places leave it out`() {
        val s = village("2026-10-10").copy(landmarksDown = setOf("mlaj"))
        val w = SceneWorld.of(s)
        assertTrue(w.finished("mlaj"))
        assertFalse(w.standing("mlaj"))
        // a thing that needs the maypole isn't there; one of its earlier steps, or one until it stands, is
        assertFalse(SceneNeeds(project = "mlaj").met(w))
        assertFalse(SceneNeeds(project = "mlaj", step = 5).met(w))
        assertTrue(SceneNeeds(project = "mlaj", step = 3).met(w))
        assertTrue(SceneNeeds(project = "mlaj", before = true).met(w))
        assertFalse(TownMarkers.has(s, TownPlace.Landmark("mlaj")))
        // up, all of it is there
        val upW = SceneWorld.of(s.copy(landmarksDown = emptySet()))
        assertTrue(upW.standing("mlaj"))
        assertTrue(SceneNeeds(project = "mlaj").met(upW))
        assertTrue(TownMarkers.has(s.copy(landmarksDown = emptySet()), TownPlace.Landmark("mlaj")))
    }

    @Test fun `the new fields survive a round trip, and an older save has none`() {
        val s = village("2026-10-10").copy(landmarksDown = setOf("mlaj"))
        assertEquals(s, json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), s)))
        val old = json.decodeFromString(GameState.serializer(), """{"seed":3,"age":"ZASELEK","projects":{"mlaj":5}}""")
        assertTrue(old.projectsFinished.isEmpty())
        assertTrue(old.landmarksDown.isEmpty())
    }
}
