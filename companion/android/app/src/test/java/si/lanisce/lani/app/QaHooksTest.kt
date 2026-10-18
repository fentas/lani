package si.lanisce.lani.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Adaptive
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.game.TurnMode
import si.lanisce.lani.game.render.TownClock
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.parseScene
import java.io.File

/**
 * What QA asks of a debug build (app/QaHooks, companion/bin/qa): a happening counted as on, so its deep link opens its
 * dialog at any hour, let go again; a zoom asked; what is asked of the road; typed turns; nothing in a release build.
 */
class QaHooksTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "scenes").isDirectory }
    private val fire = parseScene(File(companion, "scenes/ob-ognju.json").readText())
    private val talk = fire.happenings.first { it.dialog != null }
    private val key = "${fire.id}/${talk.id}"

    @After fun back() {
        QaHooks.enabled = true
        QaHooks.handle("happening:")
        QaHooks.handle("turns:")
        QaHooks.handle("bubbles:")
        QaHooks.handle("clock:")
        QaHooks.handle("step:")
        QaHooks.handle("season:")
        QaHooks.handle("hour:")
        QaHooks.handle("build:")
    }

    @Test fun `a scene and the map seen at QA's hour of today, to the second, kept in range, until let go`() {
        val now = java.time.LocalDateTime.of(2026, 10, 11, 23, 5)
        QaHooks.handle("hour:7.5")
        assertEquals(java.time.LocalDateTime.of(2026, 10, 11, 7, 30), QaHooks.sceneNow(now))
        QaHooks.handle("hour:25")
        assertEquals("out of range: the clock's", now, QaHooks.sceneNow(now))
        QaHooks.handle("hour:24")
        assertEquals("the day's last second", java.time.LocalDateTime.of(2026, 10, 11, 23, 59, 59), QaHooks.sceneNow(now))
        // to the second: the village's map stands at that moment (someone halfway along their walk)
        QaHooks.handle("hour:12.036111")
        assertEquals(java.time.LocalDateTime.of(2026, 10, 11, 12, 2, 10), QaHooks.sceneNow(now))
        QaHooks.handle("hour:")
        assertEquals(now, QaHooks.sceneNow(now))
        QaHooks.handle("hour:9")
        QaHooks.enabled = false
        assertEquals("a release build keeps the clock", now, QaHooks.sceneNow(now))
    }

    @Test fun `the landmarks' season as on QA's day, until let go`() {
        var asked = 0
        QaHooks.handle("season:2027-05-10", season = { asked++ })
        assertEquals(java.time.LocalDate.of(2027, 5, 10), si.lanisce.lani.game.ProjectSeasons.qaDay)
        assertEquals(1, asked)
        // a month and day: the next one from today
        val today = java.time.LocalDate.of(2026, 10, 11)
        assertEquals(java.time.LocalDate.of(2027, 5, 10), QaHooks.seasonDay("05-10", today))
        assertEquals(java.time.LocalDate.of(2026, 10, 30), QaHooks.seasonDay("10-30", today))
        assertEquals(today, QaHooks.seasonDay("10-11", today))
        assertNull(QaHooks.seasonDay("", today))
        assertNull(QaHooks.seasonDay("13-40", today))
        QaHooks.handle("season:")
        assertNull(si.lanisce.lani.game.ProjectSeasons.qaDay)
        // a release build ignores it
        QaHooks.enabled = false
        QaHooks.handle("season:05-10")
        assertNull(si.lanisce.lani.game.ProjectSeasons.qaDay)
    }

    @Test fun `the charcoal pile's cycle as on QA's day, a stage's own day, its breakout night, the rest and the winter`() {
        val world = si.lanisce.lani.game.culture.Cultures.load("primorska").world
        val c = world.spots.getValue("kopa").cycle!!
        val today = java.time.LocalDate.of(2026, 10, 17)
        val on = { d: java.time.LocalDate? -> si.lanisce.lani.game.scene.SpotCycles.on(c, "kopa", d!!) }
        assertEquals(java.time.LocalDate.of(2027, 5, 10), QaHooks.kopaDay("05-10", today, world))
        assertEquals(java.time.LocalDate.of(2027, 5, 10), QaHooks.kopaDay("2027-05-10", today, world))
        // every stage of the next round that has it, on an ordinary day of it (not its breakout night)
        for (stage in c.stages) {
            val d = QaHooks.kopaDay(stage.id, today, world)
            val day = on(d)
            assertEquals(stage.id, day.id)
            assertTrue("${stage.id}: from today on", !d!!.isBefore(today))
            assertTrue("${stage.id}: not the breakout night", day.round?.breakout != d)
            assertTrue("${stage.id}: its first day, or its second after the breakout night", day.day == 0 || day.day == 1 && day.round?.breakout == d.minusDays(1))
        }
        val night = QaHooks.kopaDay("breakout", today, world)!!
        assertEquals(night, on(night).round?.breakout)
        val next = QaHooks.kopaDay("breakout:2", today, world)!!
        assertTrue(next.isAfter(night))
        assertEquals(next, on(next).round?.breakout)
        assertTrue(on(QaHooks.kopaDay("delivery", today, world)).span!!.stage.delivers)
        assertEquals("rest", on(QaHooks.kopaDay("rest", today, world)).id)
        assertEquals("winter", on(QaHooks.kopaDay("winter", today, world)).id)
        assertNull(QaHooks.kopaDay("", today, world))
        assertNull(QaHooks.kopaDay("no-such-stage", today, world))
    }

    @Test fun `a project's step readied, the sheet at its project, until let go`() {
        val asked = ArrayList<Pair<String, Int>>()
        QaHooks.handle("step:mlaj/2", step = { p, n -> asked += p to n })
        assertEquals(listOf("mlaj" to 2), asked)
        assertEquals("mlaj", QaHooks.project)
        QaHooks.handle("step:")
        assertNull(QaHooks.project)
        // the village ready for it: the step before done, none today yet, the stores it costs
        val day = java.time.LocalDate.of(2026, 9, 1)
        val s = si.lanisce.lani.game.GameState(age = si.lanisce.lani.game.Age.ZASELEK, projects = mapOf("mlaj" to 4), projectDay = day.toString())
        val r = QaHooks.readyStep(s, "mlaj", 2, day)
        assertEquals(1, r.projects["mlaj"])
        assertEquals("", r.projectDay)
        val spec = si.lanisce.lani.game.Projects.spec("mlaj")!!
        for ((res, n) in si.lanisce.lani.game.Catalog.stepCost(spec.age, spec.steps[1].kind)) assertTrue("$res", r.res(res) >= n)
        assertTrue(r.help >= si.lanisce.lani.game.Catalog.stepHelp(spec.age)) // the second step brings the neighbours
    }

    @Test fun `a building to choose a plot for, the village ready for it, until the village takes it`() {
        var readied = 0
        QaHooks.handle("build:field", ready = { readied++ })
        assertEquals(si.lanisce.lani.game.BuildingType.FIELD, QaHooks.build)
        assertEquals(1, readied)
        QaHooks.tookBuild()
        assertNull(QaHooks.build)
        QaHooks.handle("build:palisade", ready = { readied++ }) // it takes no plot: nothing to choose
        assertNull(QaHooks.build)
        assertEquals(1, readied)
        // the stores full; a free plot: the age's, else the next age's
        val caps = si.lanisce.lani.game.Res.entries.associateWith { 500 }
        val age = si.lanisce.lani.game.Age.TABOR
        val full = (0 until si.lanisce.lani.game.PLOTS_PER_AGE[age.ordinal]).map { si.lanisce.lani.game.Building("b$it", si.lanisce.lani.game.BuildingType.TENT, it) }
        val s = si.lanisce.lani.game.GameState(age = age, buildings = full)
        val r = QaHooks.readyBuild(s, caps)
        assertEquals(si.lanisce.lani.game.Age.ZASELEK, r.age)
        assertTrue(si.lanisce.lani.game.Res.entries.all { r.res(it) == 500 })
        assertEquals("a free plot left: the age stays", age, QaHooks.readyBuild(s.copy(buildings = full.drop(1)), caps).age)
    }

    @Test fun `the school readied, the unit biti as never begun, the other units' progress kept`() {
        val s = si.lanisce.lani.game.GameState(
            age = si.lanisce.lani.game.Age.ZASELEK,
            course = si.lanisce.lani.game.CourseProgress(
                units = mapOf(
                    si.lanisce.lani.game.School.key("sl", "biti") to si.lanisce.lani.game.UnitProgress(started = "2026-10-13", intro = 1),
                    si.lanisce.lani.game.School.key("sl", "spol") to si.lanisce.lani.game.UnitProgress(started = "2026-10-12", introDone = true),
                ),
            ),
        )
        val r = QaHooks.readySchool(s, 0L)
        assertNull(si.lanisce.lani.game.School.progress(r, "sl", "biti"))
        assertEquals(true, si.lanisce.lani.game.School.progress(r, "sl", "spol")?.introDone)
        assertTrue(r.buildings.any { it.type == si.lanisce.lani.game.BuildingType.LIPA })
    }

    @Test fun `a slower clock asked, the towns' time runs at that rate, kept in range, until let go`() {
        assertEquals(1f, TownClock.rate)
        QaHooks.handle("clock:0.4")
        assertEquals(0.4f, TownClock.rate)
        QaHooks.handle("clock:0")
        assertEquals(0.05f, TownClock.rate)
        QaHooks.handle("clock:3")
        assertEquals(1f, TownClock.rate)
        QaHooks.handle("clock:0.5")
        QaHooks.handle("clock:")
        assertEquals(1f, TownClock.rate)
        // a release build's time is the wall's
        QaHooks.enabled = false
        QaHooks.handle("clock:0.4")
        assertEquals(1f, TownClock.rate)
    }

    @Test fun `fewer bubbles asked, the village shows the nearest ones in their order, until let go`() {
        val far = listOf("a" to 9f, "b" to 1f, "c" to 5f, "d" to 2f, "e" to 7f)
        val dist: (Pair<String, Float>) -> Float = { it.second }
        assertEquals(far, QaHooks.fewer(far, dist))
        QaHooks.handle("bubbles:3")
        assertEquals(3, QaHooks.bubbles)
        assertEquals(listOf("b", "c", "d"), QaHooks.fewer(far, dist).map { it.first })
        QaHooks.handle("bubbles:0")
        assertTrue(QaHooks.fewer(far, dist).isEmpty())
        QaHooks.handle("bubbles:")
        assertNull(QaHooks.bubbles)
        assertEquals(far, QaHooks.fewer(far, dist))
        // a release build shows them all
        QaHooks.handle("bubbles:2")
        QaHooks.enabled = false
        assertEquals(far, QaHooks.fewer(far, dist))
        QaHooks.handle("bubbles:1")
        QaHooks.enabled = true
        assertEquals(2, QaHooks.bubbles)
    }

    @Test fun `typed turns asked, a dialog's form turns are typed whatever the learner's masteries, until let go`() {
        val dialog = fire.dialogs.first { d -> Adaptive.modes(d, { Mastery.SECURE }, canSay = false).isNotEmpty() }
        val own: (String) -> Mastery? = { Mastery.LEARNING }
        assertTrue(Adaptive.dialog(dialog, QaHooks.mastery(own), canSay = true).modes.isEmpty())
        QaHooks.handle("turns:type")
        val typed = Adaptive.dialog(dialog, QaHooks.mastery(own), canSay = true).modes
        assertTrue(typed.isNotEmpty())
        assertTrue(typed.values.all { it == TurnMode.TYPE })
        QaHooks.handle("turns:")
        assertTrue(Adaptive.dialog(dialog, QaHooks.mastery(own), canSay = true).modes.isEmpty())
        // a release build hears nothing
        QaHooks.enabled = false
        QaHooks.handle("turns:type")
        assertTrue(Adaptive.dialog(dialog, QaHooks.mastery(own), canSay = true).modes.isEmpty())
    }

    @Test fun `a happening QA names is on, first, with its person, until it is let go`() {
        QaHooks.handle("happening:$key")
        assertEquals(key, QaHooks.happening)
        val on = QaHooks.withForced(listOf(fire), emptyList())
        assertEquals(listOf(key), on.map { it.key })
        assertEquals(talk.who, on.single().person?.id)
        // already on: as it is; another scene's or an unknown one: nothing added
        assertEquals(on, QaHooks.withForced(listOf(fire), on))
        assertTrue(QaHooks.withForced(emptyList(), emptyList()).isEmpty())
        assertTrue(QaHooks.withForced(listOf(fire), emptyList(), "${fire.id}/nobody").isEmpty())
        QaHooks.handle("happening:")
        assertNull(QaHooks.happening)
        assertTrue(QaHooks.withForced(listOf(fire), emptyList()).isEmpty())
    }

    @Test fun `a happening QA names with a variant plays that variant, also when the day has it on with another`() {
        val tag = fire.happenings.first { it.id == "lovljenje" }
        val k = "${fire.id}/${tag.id}"
        val v = "lovljenje-skrivalnice"
        QaHooks.handle("happening:$k/$v")
        val on = QaHooks.withForced(listOf(fire), emptyList())
        assertEquals(listOf(k), on.map { it.key })
        assertEquals(v, on.single().dialog)
        // on today with the day's variant: QA's instead, once
        val day = ActiveHappening(fire, tag, null, dialog = "lovljenje")
        assertEquals(listOf(v), QaHooks.withForced(listOf(fire), listOf(day)).map { it.dialog })
        // a variant the scene lacks: the happening's own dialog
        assertNull(QaHooks.withForced(listOf(fire), emptyList(), "$k/nothing").single().dialog)
    }

    @Test fun `what QA asks of the road goes to the road, and a release build hears nothing`() {
        val asked = mutableListOf<String>()
        QaHooks.handle("road:mini", road = asked::add)
        QaHooks.handle("road:car", road = asked::add)
        QaHooks.handle("road:", road = asked::add)
        QaHooks.handle("zoom", road = asked::add)
        assertEquals(listOf("mini", "car"), asked)
        QaHooks.enabled = false
        QaHooks.handle("road:mini", road = asked::add)
        assertEquals(listOf("mini", "car"), asked)
    }

    @Test fun `a zoom is counted, and a release build hears nothing`() {
        val before = QaHooks.zooms
        QaHooks.handle("zoom")
        assertEquals(before + 1, QaHooks.zooms)
        QaHooks.enabled = false
        QaHooks.handle("zoom")
        QaHooks.handle("happening:$key")
        assertEquals(before + 1, QaHooks.zooms)
        assertNull(QaHooks.happening)
        assertTrue(QaHooks.withForced(listOf(fire), emptyList(), key).isEmpty())
    }

    @Test fun `the camera asked to a spot, again each time it is asked, let go, and a release build hears nothing`() {
        QaHooks.handle("show:kopa")
        val first = QaHooks.show!!
        assertEquals("kopa", first.first)
        QaHooks.handle("show:kopa")
        assertEquals("kopa" to first.second + 1, QaHooks.show)
        QaHooks.handle("show:")
        assertNull(QaHooks.show)
        QaHooks.enabled = false
        QaHooks.handle("show:pond")
        assertNull(QaHooks.show)
    }
}
