package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.GameState
import java.io.File
import java.time.LocalDateTime

/**
 * What the village has built, as the scenes see it ([SceneWorld]), what a thing or a happening [SceneNeeds], and the
 * filtering: an object whose fixture isn't there is no word of the scene, a happening whose needs aren't met doesn't
 * come up, France talks of the mill until it grinds; the curated scenes say what their fixtures need.
 */
class SceneWorldTest {
    private val village = GameState(
        seed = 3, age = Age.ZASELEK,
        buildings = listOf(
            Building("t", BuildingType.TENT, 0), Building("f", BuildingType.FIELD, 1, level = 2),
            Building("k1", BuildingType.KOZOLEC, 2), Building("k2", BuildingType.KOZOLEC, 3, level = 3, damaged = true),
        ),
        projects = mapOf("most" to 2, "mlin" to 6, "nope" to 4),
    )

    @Test fun `the world of a game state`() {
        val w = SceneWorld.of(village)
        assertEquals(Age.ZASELEK, w.age)
        assertTrue(w.has(BuildingType.TENT))
        assertTrue("a damaged hayrack still stands", w.has(BuildingType.KOZOLEC))
        assertFalse(w.has(BuildingType.WELL))
        assertEquals(3, w.level(BuildingType.KOZOLEC))
        assertEquals(0, w.level(BuildingType.WELL))
        assertEquals(2, w.stage("most"))
        assertEquals(6, w.stages("most"))
        assertFalse(w.finished("most"))
        assertTrue(w.finished("mlin"))
        assertEquals(0, w.stage("mlaj"))
        assertEquals("a project the game doesn't know has one step", 1, w.stage("nope"))
        // everything, by default
        for (t in BuildingType.entries) assertTrue(SceneWorld.ALL.has(t))
        for (p in Catalog.projectFrames) assertTrue(p.id, SceneWorld.ALL.finished(p.id))
        assertEquals(Catalog.MAX_LEVEL, SceneWorld.ALL.level(BuildingType.HOUSE))
        assertFalse(SceneWorld.NONE.has(BuildingType.TENT))
        assertEquals(0, SceneWorld.NONE.stage("most"))
    }

    @Test fun `needs a building, a project's steps, the time before, the age`() {
        val w = SceneWorld.of(village)
        assertTrue(SceneNeeds(building = "KOZOLEC").met(w))
        assertTrue(SceneNeeds(building = "kozolec").met(w))
        assertFalse(SceneNeeds(building = "WELL").met(w))
        assertFalse("a building the game doesn't know is never there", SceneNeeds(building = "CASTLE").met(w))
        assertFalse("most isn't finished", SceneNeeds(project = "most").met(w))
        assertTrue(SceneNeeds(project = "most", step = 2).met(w))
        assertFalse(SceneNeeds(project = "most", step = 3).met(w))
        assertTrue(SceneNeeds(project = "mlin").met(w))
        assertFalse("before the mill: not once it grinds", SceneNeeds(project = "mlin", before = true).met(w))
        assertTrue(SceneNeeds(project = "most", before = true).met(w))
        assertTrue(SceneNeeds(building = "WELL", before = true).met(w))
        assertTrue(SceneNeeds(age = "zaselek").met(w))
        assertFalse(SceneNeeds(age = "vas").met(w))
        assertFalse("all of it must hold", SceneNeeds(age = "vas", building = "KOZOLEC").met(w))
        assertTrue(SceneNeeds().met(SceneWorld.NONE))
        for (n in listOf(SceneNeeds(building = "WELL"), SceneNeeds(project = "most", step = 6), SceneNeeds(age = "mesto"))) assertTrue(n.met(SceneWorld.ALL))
    }

    @Test fun `needs a level - of the building the scene is seen in, else the highest of its type`() {
        val huts = village.copy(
            buildings = village.buildings + listOf(
                Building("h1", BuildingType.HUT, 4, level = 1), Building("h2", BuildingType.HUT, 5, level = 3),
                Building("s", BuildingType.SMITHY, 6, level = 2),
            ),
        )
        val w = SceneWorld.of(huts)
        assertEquals("the highest hut", 3, w.level(BuildingType.HUT))
        val first = SceneWorld.of(huts, huts.buildings.first { it.id == "h1" })
        assertEquals("seen in the first hut: its level counts, not the highest", 1, first.level(BuildingType.HUT))
        assertEquals(1, first.hereLevel)
        assertEquals("the other types as before", 2, first.level(BuildingType.SMITHY))
        // a level alone is the place's own
        assertFalse(SceneNeeds(level = 2).met(first))
        assertTrue(SceneNeeds(level = 2, before = true).met(first))
        assertTrue(SceneNeeds(level = 3).met(w.seenIn(huts.buildings.first { it.id == "h2" })))
        // with a building: one of that type at least at that level, the one the scene is seen in when it is of that type
        assertTrue(SceneNeeds(building = "HUT", level = 3).met(w))
        assertFalse(SceneNeeds(building = "hut", level = 3).met(first))
        assertTrue(SceneNeeds(building = "SMITHY", level = 2).met(first))
        assertFalse(SceneNeeds(building = "SMITHY", level = 3).met(first))
        assertTrue("until the smithy is at level 3", SceneNeeds(building = "SMITHY", level = 3, before = true).met(first))
        assertFalse("no well at any level", SceneNeeds(building = "WELL", level = 1).met(w))
        // everything built: the top level; nothing built (a scene seen in no building): the first
        assertEquals(Catalog.MAX_LEVEL, SceneWorld.ALL.hereLevel)
        assertTrue(SceneNeeds(level = Catalog.MAX_LEVEL).met(SceneWorld.ALL))
        assertEquals(1, SceneWorld.NONE.hereLevel)
        assertFalse(SceneNeeds(level = 2).met(SceneWorld.NONE))
        assertEquals(2, SceneWorld.room(2).hereLevel)
        // read and written with the level
        val o = json.decodeFromString(SceneObject.serializer(), """{"slot": "clock", "word": "ura", "needs": {"level": 3}}""")
        assertEquals(SceneNeeds(level = 3), o.needs)
        assertEquals(o, json.decodeFromString(SceneObject.serializer(), json.encodeToString(SceneObject.serializer(), o)))
        val b = json.decodeFromString(SceneObject.serializer(), """{"slot": "bed", "word": "postelja", "needs": {"building": "HUT", "level": 2, "before": true}}""")
        assertEquals(SceneNeeds(building = "HUT", level = 2, before = true), b.needs)
    }

    @Test fun `the kitchen grows with the hut it is opened from, and so do its words`() {
        val kitchen = parseScene(File(dir, "kuhinja.json").readText())
        fun slots(level: Int) = kitchen.inWorld(SceneWorld.room(level, BuildingType.HUT)).objects.map { it.slot }.toSet()
        val bare = setOf("hearth", "pot", "bench", "bed", "shelf", "window", "door", "milk", "water", "eggs", "potatoes")
        assertEquals("level 1: bare", bare, slots(1))
        assertEquals(
            "level 2: the stove instead of the hearth, the table laid with its chairs, the curtains",
            bare - "hearth" - "bed" + setOf("stove", "table", "chair", "curtains", "bread", "plate", "bowl", "glass", "cup", "spoon", "fork", "knife", "salt"),
            slots(2),
        )
        assertEquals("level 3: the dresser instead of the shelf, the clock, the holy corner", slots(2) - "shelf" + setOf("cupboard", "clock", "corner"), slots(3))
        assertEquals("the top level is the third's", slots(3), slots(Catalog.MAX_LEVEL))
        assertTrue("the find counter grows", slots(1).size < slots(2).size && slots(2).size < slots(3).size)
        // Micka's potica and the Sunday lunch want the stove and the table
        val hut = village.copy(buildings = village.buildings + Building("h", BuildingType.HUT, 4, level = 1), residents = emptyList())
        val sunday = LocalDateTime.of(2026, 9, 27, 10, 0)
        fun on(level: Int, id: String): Boolean {
            val h = kitchen.happenings.first { it.id == id }.copy(chance = 1f)
            val s = hut.copy(buildings = hut.buildings.map { if (it.id == "h") it.copy(level = level) else it })
            return Happenings.on(ActiveHappening(kitchen, h, kitchen.people.first { it.id == h.who }), s, sunday.toLocalDate(), TimeOfDay.of(sunday.hour))
        }
        assertFalse(on(1, "potica")); assertFalse(on(1, "nedeljsko-kosilo"))
        assertTrue(on(2, "potica")); assertTrue(on(2, "nedeljsko-kosilo"))
        assertTrue("the coffee is made on the hearth too", on(1, "kava"))
    }

    @Test fun `a scene is seen in the building it was opened from, else in its place's`() {
        val kitchen = parseScene(File(dir, "kuhinja.json").readText())
        val s = village.copy(
            buildings = village.buildings + listOf(
                Building("h2", BuildingType.HUT, 7, level = 3), Building("h1", BuildingType.HUT, 5, level = 1),
                Building("house", BuildingType.HOUSE, 6, level = 2),
            ),
        )
        assertEquals("opened from a hut: that hut", "h2", Homes.buildingOf(kitchen, s, "h2")?.id)
        assertEquals("opened from a house: that house", "house", Homes.buildingOf(kitchen, s, "house")?.id)
        assertEquals("opened from elsewhere (a bubble, the scroll): its place, the first hut", "h1", Homes.buildingOf(kitchen, s)?.id)
        assertEquals("a building the scene doesn't open from doesn't count", "h1", Homes.buildingOf(kitchen, s, "t")?.id)
        assertEquals(1, Homes.world(kitchen, s).hereLevel)
        assertEquals(3, Homes.world(kitchen, s, "h2").hereLevel)
        assertNull("the campfire is seen in no building", Homes.buildingOf(parseScene(File(dir, "ob-ognju.json").readText()), s))
    }

    @Test fun `needs read as the age alone or as an object, and back`() {
        val h = json.decodeFromString(Happening.serializer(), """{"id": "a", "title": "A", "who": "x", "needs": "vas"}""")
        assertEquals(SceneNeeds(age = "vas"), h.needs)
        val o = json.decodeFromString(SceneObject.serializer(), """{"slot": "mill", "word": "mlin", "needs": {"project": "mlin", "step": 2, "before": true}}""")
        assertEquals(SceneNeeds(project = "mlin", step = 2, before = true), o.needs)
        assertNull(json.decodeFromString(SceneObject.serializer(), """{"slot": "mill", "word": "mlin"}""").needs)
        assertEquals(o, json.decodeFromString(SceneObject.serializer(), json.encodeToString(SceneObject.serializer(), o)))
        assertEquals(h, json.decodeFromString(Happening.serializer(), json.encodeToString(Happening.serializer(), h)))
    }

    private val stream = SceneSpec(
        id = "ob-potoku", title = "Ob potoku", art = "stream", from = listOf("spot:riverbank"),
        objects = listOf(
            SceneObject("stream", "potok"), SceneObject("bridge", "brv"),
            SceneObject("mill", "mlin"), // no needs of its own: its slot's hold
            SceneObject("laundry", "perilo", needs = SceneNeeds(building = "WELL")),
        ),
        people = listOf(ScenePerson("france", "Mlinar France", "🧑‍🌾", "farmer", "mill")),
        happenings = listOf(
            Happening("mlin", "France melje", who = "france", `when` = listOf(TimeOfDay.MORNING), needs = SceneNeeds(project = "mlin")),
            Happening("potok", "France gleda potok", who = "france", `when` = listOf(TimeOfDay.MORNING), needs = SceneNeeds(project = "mlin", before = true)),
        ),
    )
    private val morning = LocalDateTime.of(2026, 9, 26, 8, 30)

    @Test fun `an object whose fixture isn't there is no word of the scene`() {
        val none = stream.inWorld(SceneWorld.NONE)
        assertEquals(listOf("stream", "bridge"), none.objects.map { it.slot })
        assertEquals(listOf("stream", "bridge", "mill"), stream.inWorld(SceneWorld.of(village)).objects.map { it.slot })
        assertTrue(stream.inWorld(SceneWorld.ALL) === stream)
        // the curated field without a hayrack: no kozolec, no hay
        val field = parseScene(File(dir, "na-njivi.json").readText())
        val bare = field.inWorld(SceneWorld.of(village.copy(buildings = village.buildings.filter { it.type != BuildingType.KOZOLEC })))
        assertFalse(bare.objects.any { it.slot == "kozolec" || it.slot == "hay" })
        assertEquals(field.objects.size - 2, bare.objects.size)
    }

    @Test fun `France talks of the mill until it grinds, then mills`() {
        fun on(projects: Map<String, Int>) = Happenings.active(listOf(stream), village.copy(projects = projects), morning).map { it.happening.id }
        // a whole week of mornings: whichever comes up is the one the mill allows
        for (d in 0L until 7L) {
            val day = morning.plusDays(d)
            val before = Happenings.active(listOf(stream), village.copy(projects = mapOf("mlin" to 0)), day).map { it.happening.id }
            val building = Happenings.active(listOf(stream), village.copy(projects = mapOf("mlin" to 4)), day).map { it.happening.id }
            val grinding = Happenings.active(listOf(stream), village.copy(projects = mapOf("mlin" to 6)), day).map { it.happening.id }
            assertEquals(listOf("potok"), before)
            assertEquals(listOf("potok"), building)
            assertEquals(listOf("mlin"), grinding)
        }
        assertEquals(listOf("potok"), on(emptyMap()))
    }

    @Test fun `a happening that needs a building waits for it`() {
        val field = parseScene(File(dir, "na-njivi.json").readText())
        val seno = field.happenings.first { it.id == "seno" }
        val a = ActiveHappening(field, seno.copy(chance = 1f), field.people.first { it.id == seno.who })
        val afternoon = morning.withHour(15)
        val t = TimeOfDay.of(afternoon.hour)
        val without = village.copy(buildings = village.buildings.filter { it.type != BuildingType.KOZOLEC }, residents = emptyList())
        assertFalse(Happenings.on(a, without, afternoon.toLocalDate(), t))
        assertTrue(Happenings.on(a, village.copy(residents = emptyList()), afternoon.toLocalDate(), t))
    }

    private val dir = listOf(File("../../scenes"), File("../scenes"), File("companion/scenes")).first { it.isDirectory }

    @Test fun `the curated scenes say what their fixtures need, and name only what the game has`() {
        val projects = Catalog.projectFrames.associate { it.id to it.steps.size }
        val scenes = dir.listFiles { f -> f.extension == "json" }!!.map { parseScene(it.readText()) }
        for (s in scenes) {
            for (o in s.objects) {
                val fixture = SceneFixtures.of(s.art, o.slot)
                // a thing drawn only when built says so itself (the bridge and the tutor read the file, not the painter)
                if (fixture != null) assertEquals("${s.id}/${o.slot}", fixture, o.needs)
                o.needs?.let { check(s.id + "/" + o.slot, it, projects) }
            }
            for (h in s.happenings) h.needs?.let { check(s.id + "/" + h.id, it, projects) }
        }
        val stream = scenes.first { it.id == "ob-potoku" }
        assertEquals(SceneNeeds(project = "mlin"), stream.happenings.first { it.id == "mlin" }.needs)
        assertEquals(SceneNeeds(project = "mlin", before = true), stream.happenings.first { it.id == "potok" }.needs)
        assertEquals(stream.happenings.first { it.id == "mlin" }.`when`, stream.happenings.first { it.id == "potok" }.`when`)
        assertNull("the footbridge is always there", stream.objects.first { it.slot == "bridge" }.needs)
    }

    private fun check(at: String, n: SceneNeeds, projects: Map<String, Int>) {
        n.building?.let { assertTrue("$at: building $it", n.buildingType != null) }
        n.level?.let { assertTrue("$at: level $it", it in 1..Catalog.MAX_LEVEL && n.project == null) }
        n.project?.let { p ->
            assertTrue("$at: project $p", p in projects)
            n.step?.let { assertTrue("$at: step $it of $p", it in 1..projects.getValue(p)) }
        }
        n.age?.let { a -> assertTrue("$at: age $a", Age.entries.any { it.name.equals(a, ignoreCase = true) }) }
    }
}
