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
}
