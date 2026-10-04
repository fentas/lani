package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Projects
import si.lanisce.lani.game.scene.ActiveHappening
import si.lanisce.lani.game.scene.Happening
import si.lanisce.lani.game.scene.Happenings
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.TownSpots
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.ui.game.lockReasons
import si.lanisce.lani.ui.game.markerFace
import si.lanisce.lani.ui.game.markerVillager
import kotlin.math.hypot

/**
 * Everyone is on the village map at most once: who has a happening on stands at its place instead of pottering about
 * their home (today's visitor too), and who is away from the clearing (in the mountains at the horizon, at the sea, at a
 * landmark out in the woods) isn't on the map at all: a small bubble with their face sits at that far place, and a tap on
 * it opens their scene. The vineyard's landmark leads into its scene once the project is finished.
 */
class AwayPeopleTest {
    private val w = 360; private val h = 800

    private val village = GameState(
        seed = 42, age = Age.VAS, villagers = 6, morale = 70, fire = 70,
        buildings = listOf(BuildingType.FIELD, BuildingType.HUT, BuildingType.WELL, BuildingType.HOUSE, BuildingType.LIPA, BuildingType.MARKET)
            .mapIndexed { i, t -> Building("b$i", t, i) },
    )

    private val france = Villager(id = "france", name = "Mlinar France", emoji = "🧑‍🌾", art = "farmer", home = listOf("smithy"))
    private val janez = Villager(id = "janez", name = "Stari Janez", emoji = "👴", art = "grandpa", home = listOf("lipa"))
    private val marko = Villager(id = "marko", name = "Vinar Marko", emoji = "🍇", art = "winemaker", home = listOf("market"))

    private fun scene(id: String, art: String, from: String, who: Villager, slot: String) = SceneSpec(
        id = id, title = id, art = art, from = listOf(from),
        people = listOf(ScenePerson(who.id, who.name, who.emoji, who.art, slot, villager = who.id)),
        happenings = listOf(Happening("h", id, who = who.id)),
    )

    private val stream = scene("ob-potoku", "stream", "spot:riverbank", france, "bank")
    private val mountains = scene("v-gorah", "alps", "spot:horizon", janez, "path")
    private val vineyard = scene("na-gricih", "hills", "project:vinograd", marko, "vines")

    private fun on(s: SceneSpec) = ActiveHappening(s, s.happenings.first(), s.people.first())

    @Test fun `a visitor with a happening at the stream is on the map once, at the stream`() {
        // France visits today (his home isn't built: he'd wait on the road) and his happening is on by the stream
        val active = listOf(on(stream))
        val standing = TownMarkers.standing(village, active)
        assertEquals(mapOf("france" to TownPlace.Spot("riverbank")), standing)
        for (hour in listOf(11f, 23.5f)) {
            val placed = TownPeople.of(village, listOf(france, janez), 3.0, hour, 6, w, h, standing = standing)
            val his = placed.filter { it.id == "france" }
            assertEquals("France at $hour h: one figure", 1, his.size)
            assertEquals(TownPlace.Spot("riverbank"), his.single().place)
            // by the stream's bank, not on the road
            val bank = TownAnchors.spotWorld(village, "riverbank")!!
            assertTrue("France stands by the stream: ${his.single().x}, ${his.single().y}", hypot(his.single().x - bank[0], his.single().y - bank[1]) < 3f)
        }
        // no plain villager waits at the stream as well: France is drawn himself
        assertTrue(TownPeople.waiting(village, active, listOf(france, janez)).isEmpty())
        // someone of the scene who isn't drawn about the village gets the plain villager there
        assertEquals(listOf(TownPlace.Spot("riverbank")), TownPeople.waiting(village, active, listOf(janez)))
        // and the bubble follows him there
        val m = TownMarkers.of(village, active, listOf(france, janez)).first { it.id == "happening:ob-potoku/h" }
        assertEquals("france", markerVillager(m, village, active, listOf(france, janez), standing))
        assertNull(markerFace(m, active, listOf(france, janez), TownPeople.awayIds(village, standing, w, h)))
    }

    @Test fun `someone in the mountains is not on the map, only a small bubble with their face at the horizon`() {
        val active = listOf(on(mountains))
        val standing = TownMarkers.standing(village, active)
        assertEquals(TownPlace.Spot(TownSpots.HORIZON), standing["janez"])
        assertTrue(TownPeople.away(village, TownPlace.Spot(TownSpots.HORIZON), w, h))
        val away = TownPeople.awayIds(village, standing, w, h)
        assertEquals(setOf("janez"), away)
        for (hour in listOf(11f, 23.5f)) {
            val placed = TownPeople.of(village, listOf(france, janez), 3.0, hour, 6, w, h, standing = standing)
            assertFalse("Janez isn't in the village at $hour h", placed.any { it.id == "janez" })
        }
        // nobody waits at the horizon either
        assertEquals(listOf(TownPlace.Spot(TownSpots.HORIZON)), TownPeople.waiting(village, active, listOf(france)))
        assertNull(TownAnchors.visitorSpot(village, TownPlace.Spot(TownSpots.HORIZON), VillageLayout.composition(w, h, false), w, h))
        // his bubble stays at the horizon, with his face, and it opens the mountains
        val m = TownMarkers.of(village, active, listOf(france, janez)).first { it.id == "happening:v-gorah/h" }
        assertEquals(TownPlace.Spot(TownSpots.HORIZON), m.place)
        assertNull(markerVillager(m, village, active, listOf(france, janez), standing, away))
        assertEquals("janez", markerFace(m, active, listOf(france, janez), away)?.id)
        assertNotNull(TownAnchors.withSpots(village, w, h)[m.place])
        // the renderer draws no one for him
        val r = VillageRenderer()
        r.render(PixelCanvas(w, h), village, Frame(time = 3.0, hour = 11f, month = 6, people = listOf(france, janez), standing = standing))
        assertFalse(r.villagerAnchors.containsKey("janez"))
        assertTrue(r.villagerAnchors.containsKey("france"))
    }

    @Test fun `someone with a happening stays at its place at night and at a festival`() {
        val active = listOf(on(stream))
        val standing = TownMarkers.standing(village, active)
        for (festival in listOf(false, true)) {
            val p = TownPeople.of(village, listOf(france), 3.0, 23.5f, 6, w, h, festival = festival, standing = standing).single()
            assertEquals(TownPlace.Spot("riverbank"), p.place)
        }
    }

    @Test fun `the vineyard opens from its landmark once the project is finished, and Marko stands by it`() {
        val frame = Projects.spec("vinograd")!!
        val building = village.copy(projects = mapOf("vinograd" to frame.steps.size - 1))
        assertFalse("not before the terraces are finished", Happenings.open(vineyard, building))
        assertTrue(lockReasons(vineyard, building).any { it.contains(frame.short) })
        assertFalse(TownMarkers.has(building, TownPlace.Landmark("vinograd")))
        val done = village.copy(projects = mapOf("vinograd" to frame.steps.size))
        assertTrue(Happenings.open(vineyard, done))
        assertTrue(TownMarkers.has(done, TownPlace.Landmark("vinograd")))
        assertEquals(TownPlace.Landmark("vinograd"), TownMarkers.placeOf(vineyard, done))
        // its bubbles point at the landmark
        assertNotNull(TownAnchors.withSpots(done, w, h)[TownPlace.Landmark("vinograd")])
        // in the clearing (by the field): Marko stands in front of the terraces, once, and his bubble follows him
        val active = listOf(on(vineyard))
        val standing = TownMarkers.standing(done, active)
        val spot = TownAnchors.landmarkSpot(done, "vinograd", w, h)
        assertNotNull(spot)
        assertTrue("the terraces are in the clearing", spot!!.second)
        assertFalse(TownPeople.away(done, TownPlace.Landmark("vinograd"), w, h))
        val his = TownPeople.of(done, listOf(marko), 3.0, 11f, 6, w, h, standing = standing).filter { it.id == "marko" }
        assertEquals(1, his.size)
        assertEquals(TownPlace.Landmark("vinograd"), his.single().place)
        val m = TownMarkers.of(done, active, listOf(marko)).first { it.id == "happening:na-gricih/h" }
        assertEquals("marko", markerVillager(m, done, active, listOf(marko), standing, TownPeople.awayIds(done, standing, w, h)))
    }
}
