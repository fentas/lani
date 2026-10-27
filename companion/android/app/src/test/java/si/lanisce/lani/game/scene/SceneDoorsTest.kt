package si.lanisce.lani.game.scene

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.culture.Cultures

/**
 * The way into the mill (companion/SCENES.md, "The mill inside: from the stream and from the map"): the mill's scenes open
 * from its landmark ("project:mlin"), so the map's card and the stream's mill offer "Vstopi" only once the mill grinds,
 * in the Slovene village and in the Friuli one; never before, and nothing else of the stream leads anywhere.
 */
class SceneDoorsTest {
    @After fun back() { Cultures.use(null as String?) }

    private val village = GameState(age = Age.ZASELEK, buildings = listOf(Building("t", BuildingType.TENT, 0), Building("f", BuildingType.FIELD, 1)))

    /**
     * The scenes open at [place] in [state], as the scene controller has them (SceneController.at), of a village whose scenes
     * are in [language] (the Slovene village gets the Slovene ones, the Friuli village its own Italian ones).
     */
    private fun at(place: String, state: GameState, language: String = "sl") =
        CuratedContent.scenes.filter { it.language == language && place in it.from && Happenings.open(it, state) }

    private fun scene(id: String) = CuratedContent.scenes.first { it.id == id }

    @Test fun `the stream's mill is the mill's landmark, its other things lead nowhere`() {
        assertEquals("project:mlin", SceneDoors.placeOf("stream", "mill"))
        for (slot in SceneArt.objects.getValue("stream") - "mill") assertNull(slot, SceneDoors.placeOf("stream", slot))
        for (art in SceneArt.arts - "stream") for (slot in SceneArt.objects.getValue(art)) assertNull("$art/$slot", SceneDoors.placeOf(art, slot))
    }

    @Test fun `the mill opens from its landmark only once it grinds, from the map and from the stream`() {
        val stream = scene("ob-potoku")
        for (steps in 0..5) {
            val s = village.copy(projects = mapOf("mlin" to steps))
            assertTrue("$steps steps: no scene at the mill's landmark", at("project:mlin", s).isEmpty())
            assertTrue("$steps steps: no way in from the stream", SceneDoors.into(stream, "mill") { at(it, s) }.isEmpty())
        }
        val grinds = village.copy(projects = mapOf("mlin" to 6))
        assertEquals(listOf("v-mlinu"), at("project:mlin", grinds).map { it.id })
        assertEquals(listOf("v-mlinu"), SceneDoors.into(stream, "mill") { at(it, grinds) }.map { it.id })
        // the stream's other things open nothing, and a scene never leads into itself
        assertTrue(SceneDoors.into(stream, "willow") { at(it, grinds) }.isEmpty())
        val mill = scene("v-mlinu")
        assertTrue(SceneDoors.into(mill, "millstone") { at(it, grinds) }.isEmpty())
        assertEquals(listOf("project:mlin"), mill.from)
    }

    @Test fun `the Friuli village's mill opens from its own mill project`() {
        Cultures.use("friuli")
        val stream = scene("al-ruscello")
        val mill = scene("nel-mulino")
        assertEquals("mill", mill.art)
        assertEquals(listOf("project:mlin"), mill.from)
        val building = village.copy(projects = mapOf("mlin" to 5))
        val grinds = village.copy(projects = mapOf("mlin" to 6))
        assertTrue(SceneDoors.into(stream, "mill") { at(it, building, "it") }.isEmpty())
        assertEquals(listOf("nel-mulino"), SceneDoors.into(stream, "mill") { at(it, grinds, "it") }.map { it.id })
    }
}
