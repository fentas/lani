package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.CourseFixture
import si.lanisce.lani.data.Grading.Verdict.CORRECT
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.JanRequests
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.School
import si.lanisce.lani.game.villagers.Residents
import java.io.File

/**
 * What someone in a scene has for the learner ([SceneOffers]; companion/SCENES.md "What someone has here", GAME.md "The
 * school"). Jan, 2026-10-14: "Outside, the teacher has 2 exercises, but if I go into the school she has none" (her two
 * requests up on the map, nothing of them in the school's scene), and "I should be able to go to the school lesson in the
 * school too, not only from the main menu".
 */
class SceneOffersTest {
    private val scenesDir = listOf(File("../../scenes"), File("../scenes"), File("companion/scenes")).first { it.isDirectory }
    private fun scene(id: String): SceneSpec = parseScene(File(scenesDir, "$id.json").readText())
    private val school by lazy { scene("v-soli") }
    private val square by lazy { scene("na-vasi") }
    private val fire by lazy { scene("ob-ognju") }
    private val course = CourseFixture.course
    private val cast = JanRequests.cast
    private val today = JanRequests.today

    private fun person(s: SceneSpec, id: String) = s.people.first { it.id == id }
    private fun requests(s: GameState) = SceneOffers.requestsBy(Residents.queue(s, cast, today).up, cast)
    private fun hamlet(vararg b: BuildingType) = GameState(
        seed = 3, age = Age.ZASELEK, resources = mapOf(Res.FOOD to 100),
        buildings = b.mapIndexed { i, t -> Building("${t.name.lowercase()}-$i", t, plot = i) },
    )

    @Test fun `her bubble on the map and what she has in the school are the same requests, counted alike`() {
        val s = JanRequests.linked
        // the map: one bubble over Mojca, her two requests up ("!", "2"); the third waits in "Later"
        val bubble = TownMarkers.of(s, emptyList(), cast, today).single { it.kind == TownMarker.Kind.QUEST && it.emoji == "👩‍🏫" }
        assertEquals(2, bubble.count)
        // the school's scene: the same two by her, from the same queue, the bubble's first first
        val offers = SceneOffers.of(person(school, "mojca"), emptyList(), { it.talks }, requests(s), lesson = null, teacher = "mojca")
        val asks = offers.map { (it as Offer.Request).quest }
        assertEquals(listOf("dvojina-osnove", "tozilnik-osnove"), asks.map { it.moduleId })
        assertEquals(bubble.id, "quest:${asks.first().id}")
        assertEquals(bubble.count, SceneOffers.requests(offers))
        // her marker in the scene says requests (📋) and counts them as the bubble does; a tap with two opens the choice
        assertEquals("📋", SceneOffers.marker(offers))
        assertNull(SceneOffers.direct(offers))
        // every villager's requests are theirs alone: Tone's two by him, none of them by Mojca
        val all = requests(s)
        assertEquals(TownMarkers.of(s, emptyList(), cast, today).filter { it.kind == TownMarker.Kind.QUEST && it.id.startsWith("quest:") }.map { it.count }, all.values.map { it.size })
        assertTrue(all.getValue("tone").none { it.giver == "Učiteljica Mojca" })
        // someone with nothing has nothing: no marker, a hello
        val tine = SceneOffers.of(person(school, "tine"), emptyList(), { it.talks }, all, lesson = null, teacher = "mojca")
        assertTrue(tine.isEmpty())
        assertNull(SceneOffers.marker(tine))
    }

    @Test fun `in the school the teacher has the class's lesson first, then her dialog, then her requests`() {
        val s = JanRequests.linked // the school stands: the class is indoors
        assertTrue(SceneOffers.classHere(school, s, course))
        assertFalse(SceneOffers.classHere(square, s, course))
        assertFalse(SceneOffers.classHere(fire, s, course))
        val lesson = SceneOffers.lesson(school, s, course)
        assertEquals(School.Lesson("biti", resume = false), lesson)
        assertNull(SceneOffers.lesson(square, s, course))
        val tabla = ActiveHappening(school, school.happenings.first { it.id == "tabla" }, person(school, "mojca"), dialog = "tabla")
        val offers = SceneOffers.of(person(school, "mojca"), listOf(tabla), { it.talks }, requests(s), lesson, teacher = "mojca")
        assertEquals(listOf("Lesson", "Talk", "Request", "Request"), offers.map { it::class.simpleName })
        assertEquals(Offer.Lesson(lesson!!), offers.first())
        // her marker is 📚 while a unit waits, with her two requests counted; a tap shows the choice (the units besides)
        assertEquals("📚", SceneOffers.marker(offers))
        assertEquals(2, SceneOffers.requests(offers))
        assertNull(SceneOffers.direct(listOf(Offer.Lesson(lesson))))
        // the lesson is the teacher's alone; someone else's one dialog opens straight away, as before
        val zala = person(school, "zala")
        val globus = ActiveHappening(school, school.happenings.first { it.id == "globus" }, zala, dialog = "globus")
        val hers = SceneOffers.of(zala, listOf(tabla, globus), { it.talks }, requests(s), lesson, teacher = "mojca")
        assertEquals(listOf<Offer>(Offer.Talk(globus)), hers)
        assertSame(globus, (SceneOffers.direct(hers) as Offer.Talk).happening)
        assertEquals(globus.happening.marker, SceneOffers.marker(hers))
    }

    @Test fun `the blackboard leads to the class's units where the class meets`() {
        assertTrue(SceneOffers.opensSchool("blackboard", classHere = true))
        assertFalse(SceneOffers.opensSchool("blackboard", classHere = false))
        assertFalse(SceneOffers.opensSchool("globe", classHere = true))
        assertTrue(school.objects.any { it.slot == SceneOffers.BLACKBOARD })
    }

    @Test fun `the class meets by the fire, then under the linden, then in the school, nowhere before the hamlet`() {
        // a hamlet without the linden: by the fire (the teacher comes over: no one of the fire's scene teaches)
        val fireOnly = hamlet(BuildingType.TENT, BuildingType.WELL)
        assertTrue(SceneOffers.classHere(fire, fireOnly, course))
        assertFalse(SceneOffers.classHere(square, fireOnly, course))
        assertFalse(SceneOffers.classHere(school, fireOnly, course))
        assertEquals(School.Lesson("biti", resume = false), SceneOffers.lesson(fire, fireOnly, course))
        assertTrue(fire.people.none { it.villager == "mojca" })
        // the linden standing: on the square, where Mojca stands by the well
        val linden = hamlet(BuildingType.TENT, BuildingType.LIPA)
        assertTrue(SceneOffers.classHere(square, linden, course))
        assertFalse(SceneOffers.classHere(fire, linden, course))
        assertEquals("mojca", square.people.first { it.villager == "mojca" }.villager)
        // a damaged linden: back by the fire
        val storm = linden.copy(buildings = linden.buildings.map { if (it.type == BuildingType.LIPA) it.copy(damaged = true) else it })
        assertTrue(SceneOffers.classHere(fire, storm, course))
        assertFalse(SceneOffers.classHere(square, storm, course))
        // the school standing: indoors
        val indoors = hamlet(BuildingType.TENT, BuildingType.LIPA, BuildingType.SCHOOL)
        assertTrue(SceneOffers.classHere(school, indoors, course))
        assertFalse(SceneOffers.classHere(square, indoors, course))
        // before the hamlet, or without a course: nothing new anywhere
        val camp = hamlet(BuildingType.TENT, BuildingType.LIPA).copy(age = Age.TABOR)
        for (sc in listOf(fire, square, school)) {
            assertFalse(sc.id, SceneOffers.classHere(sc, camp, course))
            assertNull(sc.id, SceneOffers.lesson(sc, camp, course))
            assertFalse(sc.id, SceneOffers.classHere(sc, indoors, null))
        }
        val p = person(square, "mojca")
        assertTrue(SceneOffers.of(p, emptyList(), { it.talks }, emptyMap(), SceneOffers.lesson(square, camp, course), teacher = "mojca").isEmpty())
    }

    @Test fun `the lesson goes on with a unit begun, else the next one begins`() {
        val s = School.opened(hamlet(BuildingType.TENT, BuildingType.SCHOOL), course, day0, noon(day0))
        val biti = course.units.getValue("biti")
        // nothing begun: the unit suggested next, at its page
        val fresh = School.lesson(s, course)!!
        assertEquals(School.Lesson("biti", resume = false), fresh)
        assertNull(School.lessonStep(fresh, biti, null))
        // its intro begun: on at the intro, where it was left
        val begun = School.introProgress(s, "sl", "biti", 1, false, day0)
        val on = School.lesson(begun, course)!!
        assertEquals(School.Lesson("biti", resume = true), on)
        assertEquals(School.Step.Intro, School.lessonStep(on, biti, School.progress(begun, "sl", "biti")))
        // the intro through: the first worksheet; it done, the next
        val read = School.introProgress(begun, "sl", "biti", 4, true, day0)
        assertEquals(School.Step.Sheet("biti-1"), School.lessonStep(on, biti, School.progress(read, "sl", "biti")))
        val sheet = School.sheetDone(read, "sl", "biti", "biti-1", List(5) { CORRECT }, day0)
        assertEquals(School.Step.Sheet("biti-2"), School.lessonStep(on, biti, School.progress(sheet, "sl", "biti")))
        val both = School.sheetDone(sheet, "sl", "biti", "biti-2", List(5) { CORRECT }, day0)
        assertEquals(School.Step.Test, School.lessonStep(on, biti, School.progress(both, "sl", "biti")))
        // the unit done: the next one begins
        val done = School.testDone(both, biti, List(10) { CORRECT }, day0, noon(day0)).first
        assertEquals(School.Lesson("spol", resume = false), School.lesson(done, course))
        // a later unit begun while the one suggested next isn't: that one goes on
        val ahead = School.sheetDone(s, "sl", "spol", "spol-1", List(5) { CORRECT }, day0)
        assertEquals(School.Lesson("spol", resume = true), School.lesson(ahead, course))
        assertEquals("biti", School.next(ahead, course)!!.id)
        // each unit done in turn: the next one begins (A2's once A1 is done, in the school); all done: no lesson
        var all = done
        while (true) {
            val l = School.lesson(all, course) ?: break
            assertEquals(School.Lesson(School.next(all, course)!!.id, resume = false), l)
            all = School.testDone(all, course.units.getValue(l.unit), List(10) { CORRECT }, day0, noon(day0)).first
        }
        assertTrue(School.isOpen(all, course, "A2"))
        assertTrue(course.playable("A2").all { School.progress(all, "sl", it.id)?.done != null })
        assertNull(School.next(all, course))
        // before the hamlet: none
        assertNull(School.lesson(s.copy(age = Age.TABOR, buildings = emptyList(), course = null), course))
    }
}
