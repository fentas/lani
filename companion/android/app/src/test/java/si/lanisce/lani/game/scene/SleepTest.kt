package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.BuildingType.*
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Routine
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.Visit
import si.lanisce.lani.game.villagers.parseVillagers
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Who sleeps where and when ([Sleep], [Routine]), with the curated scenes and the cast: each household in their house's room
 * (Janez by his stove, Vida and Nejc in the attic, France and Tine in the workshop, Marko by his barrels), who lodges with
 * them (Jože on the zapeček's other run, Anton on a trestle bed in the cellar), Luka in the tent, Micka, Ančka and Zala in the
 * kitchen, Tone on his cot by the forge, Mojca in her alcove in the school, as their files say ([Villager.sleeps]); each from
 * their own bedtime until they get up, each in their own bed; who lives here and has nothing on; a sleeper's poke; and the
 * quiet of a place with a bed at night, which leaves the evening's and the night's happenings as they are.
 */
class SleepTest {
    private val scenesDir = listOf(File("../../scenes"), File("../scenes"), File("companion/scenes")).first { it.isDirectory }
    private fun scene(id: String): SceneSpec = parseScene(File(scenesDir, "$id.json").readText())
    private val scenes: List<SceneSpec> by lazy { scenesDir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { parseScene(it.readText()) } }

    private fun castOf(culture: String): List<Villager> {
        val dir = listOf(File("../../cultures/$culture/villagers"), File("../cultures/$culture/villagers")).first { it.isDirectory }
        return dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { parseVillagers("[" + it.readText() + "]").single() }.sortedBy { it.order }
    }

    private val cast: List<Villager> by lazy { castOf("primorska") }

    @Test fun `the four whose work is no home say where they sleep, and so do their counterparts in the other villages`() {
        fun sleeps(people: List<Villager>) = people.filter { it.sleeps != null }.associate { it.id to it.sleeps }
        assertEquals(mapOf("anton" to "house:cellar", "tone" to "smithy", "mojca" to "school", "joze" to "house:livingroom"), sleeps(cast))
        // the smith, the teacher and the beekeeper (no village but Jan's has a hunter)
        assertEquals(mapOf("aldo" to "house:cellar", "bruno" to "smithy", "elena" to "school"), sleeps(castOf("friuli")))
        assertEquals(mapOf("stanko" to "house:cellar", "toni" to "smithy", "eva" to "school"), sleeps(castOf("kaernten")))
        assertEquals(mapOf("arthur" to "house:cellar", "dixon" to "smithy", "hartley" to "school"), sleeps(castOf("lakeland")))
        for (c in listOf("primorska", "friuli", "kaernten", "lakeland")) for (v in castOf(c)) when (v.sleeps) {
            "smithy" -> assertEquals("$c/${v.id}", "smith", v.art)
            "school" -> assertEquals("$c/${v.id}", "teacher", v.art)
            "house:cellar" -> assertEquals("$c/${v.id}", "beekeeper", v.art)
            "house:livingroom" -> assertEquals("$c/${v.id}", "hunter", v.art)
        }
        // with no rooms of houses (the other villages), the beekeeper sleeps in a house, the smith and the teacher in theirs
        val friuli = castOf("friuli")
        val b = Sleep.bedrooms(emptyList(), town.copy(residents = friuli.map { Resident(it.id, "2026-01-01") }), friuli)
        assertEquals(HOUSE, b.getValue("aldo").building?.type); assertEquals(SMITHY, b.getValue("bruno").building?.type); assertEquals(SCHOOL, b.getValue("elena").building?.type)
        assertTrue(b.values.all { it.scene == null })
    }

    private val tent by lazy { scene("v-sotoru") }
    private val kitchen by lazy { scene("kuhinja") }
    private val living by lazy { scene("v-hisi") }
    private val attic by lazy { scene("v-podstresju") }
    private val workshop by lazy { scene("v-delavnici") }
    private val cellar by lazy { scene("v-kleti") }
    private val smithy by lazy { scene("v-kovacnici") }
    private val school by lazy { scene("v-soli") }
    private val fire by lazy { scene("ob-ognju") }
    private val forest by lazy { scene("v-gozdu") }

    /** The Primorska village with everyone moved in, in their order: the tent, the hut, four houses, the workplaces. */
    private val town by lazy {
        GameState(
            seed = 7, age = Age.VAS,
            buildings = listOf(
                Building("t", TENT, 0), Building("u", HUT, 1), Building("h1", HOUSE, 2, builtAt = 1), Building("h2", HOUSE, 3, builtAt = 2),
                Building("h3", HOUSE, 4, builtAt = 3), Building("h4", HOUSE, 5, builtAt = 4), Building("s", SMITHY, 6), Building("b", BEEHIVE, 7),
                Building("f", FIELD, 8), Building("l", LIPA, 9), Building("w", WELL, 10), Building("m", MARKET, 11), Building("sc", SCHOOL, 12),
                Building("wt", WATCHTOWER, 13),
            ),
            residents = cast.map { Resident(it.id, "2026-01-01") },
        )
    }

    /** A Wednesday in September, as today. */
    private val date = LocalDate.of(2026, 9, 23)

    private fun asleep(scene: SceneSpec, h: Int, m: Int = 0, state: GameState = town, busy: Map<String, TownPlace> = emptyMap(), storyAt: LocalDateTime? = null) =
        Sleep.sleepers(scene, state, date, h * 60.0 + m, cast, scenes, Routine.Day(tellers = Whereabouts.tellers(scenes), busy = busy, storyAt = storyAt))

    private fun ids(scene: SceneSpec, h: Int, m: Int = 0, state: GameState = town, busy: Map<String, TownPlace> = emptyMap()) =
        asleep(scene, h, m, state, busy).map { it.id to it.slot }

    @Test fun `the homes' beds, the tent's, the kitchen's three, the rooms' of the houses, the smith's and the teacher's, no other place has one`() {
        assertEquals(listOf("bed"), Sleep.beds("tent"))
        assertEquals(listOf("zapecek", "mattress", "cot"), Sleep.beds("kitchen"))
        assertEquals(setOf("cot"), Sleep.childBeds("kitchen"))
        assertEquals(listOf("zapecek", "sidebench"), Sleep.beds("livingroom"))
        assertEquals(listOf("mattress", "cot"), Sleep.beds("attic"))
        assertEquals(setOf("cot"), Sleep.childBeds("attic"))
        assertEquals(listOf("mattress", "cot"), Sleep.beds("workshop"))
        assertEquals(listOf("bench", "trestle"), Sleep.beds("cellar"))
        assertEquals(listOf("cot"), Sleep.beds("smithy")); assertTrue(Sleep.childBeds("smithy").isEmpty())
        assertEquals(listOf("alcove"), Sleep.beds("school"))
        val homes = setOf("tent", "kitchen", "livingroom", "attic", "workshop", "cellar", "smithy", "school")
        for (art in SceneArt.arts - homes) assertTrue(art, Sleep.beds(art).isEmpty())
        for (art in homes) {
            // a child's bed is one of the beds, and the beds are no spots people stand at by day
            assertTrue(art, Sleep.beds(art).containsAll(Sleep.childBeds(art)))
            assertTrue(art, Sleep.beds(art).none { it in SceneArt.personSlots.getValue(art) })
        }
    }

    @Test fun `each sleeps in their own home, the households in their houses' rooms, Luka in the tent, Micka's in the kitchen`() {
        val b = Sleep.bedrooms(scenes, town, cast)
        fun at(id: String) = b.getValue(id).building?.id to b.getValue(id).scene?.id
        // the houses in the order they were built, to the households in the order they moved in (Homes.deal)
        assertEquals("h1" to "v-hisi", at("janez"))
        assertEquals("h2" to "v-delavnici", at("france")); assertEquals("h2" to "v-delavnici", at("tine"))
        assertEquals("h3" to "v-kleti", at("marko"))
        assertEquals("h4" to "v-podstresju", at("vida")); assertEquals("h4" to "v-podstresju", at("nejc"))
        // the kitchen is the hut's, and Micka's, Ančka's and Zala's: their homes are the house or the hut
        for (id in listOf("micka", "ancka", "zala")) assertEquals(id, "u" to "kuhinja", at(id))
        assertEquals("t" to "v-sotoru", at("luka"))
        // the smith by his forge, the teacher in the school, each in the scene that shows the bed
        assertEquals("s" to "v-kovacnici", at("tone")); assertEquals("sc" to "v-soli", at("mojca"))
        // who lodges in a room of a house: in the house dealt it, not where they work (the bee house, the high seat)
        assertEquals("h3" to "v-kleti", at("anton")); assertEquals("h1" to "v-hisi", at("joze"))
        // on the map they go home to it
        val homes = Sleep.homes(scenes, town, cast)
        assertEquals(TownPlace.At(HOUSE), homes["janez"]); assertEquals(TownPlace.At(HOUSE), homes["joze"])
        assertEquals(TownPlace.At(HOUSE, "h4"), homes["nejc"]); assertEquals(TownPlace.At(HOUSE, "h3"), homes["anton"])
        assertEquals(TownPlace.At(SMITHY), homes["tone"]); assertEquals(TownPlace.At(SCHOOL), homes["mojca"])
    }

    @Test fun `where a file says one sleeps, a room of a house while a house has it, else a house, else their home decides`() {
        fun at(state: GameState, id: String, people: List<Villager> = cast) = Sleep.bedrooms(scenes, state, people).getValue(id).let { it.building?.id to it.scene?.id }
        // Marko hasn't moved in: no house has the cellar, Anton sleeps in a house we don't see into (the first whose room is
        // nobody's household: the fourth, the kitchen's)
        val noMarko = town.copy(residents = town.residents.filter { it.id != "marko" })
        assertEquals("h4" to null, at(noMarko, "anton"))
        // no house yet (a hamlet with the bee house): his home decides, the bee house, unseen
        val hamlet = town.copy(buildings = listOf(Building("u", HUT, 0), Building("b", BEEHIVE, 1)))
        assertEquals("b" to null, at(hamlet, "anton"))
        assertEquals("u" to null, at(hamlet, "joze"))
        // without the key (an older bridge) the old rule: the smith and the teacher in their buildings' scenes all the same,
        // the beekeeper in the bee house, the hunter in the hut
        val plain = cast.map { it.copy(sleeps = null) }
        assertEquals("s" to "v-kovacnici", at(town, "tone", plain)); assertEquals("sc" to "v-soli", at(town, "mojca", plain))
        assertEquals("b" to null, at(town, "anton", plain)); assertEquals("u" to null, at(town, "joze", plain))
        // a word that names no roof is no bedroom: their home decides
        val odd = cast.map { if (it.id == "anton") it.copy(sleeps = "spot:pond") else it }
        assertEquals("b" to null, at(town, "anton", odd))
        // a building type alone: under that roof, in its scene with beds that is nobody's household (the kitchen of the hut)
        val hutter = cast.map { if (it.id == "joze") it.copy(sleeps = "hut") else it }
        assertEquals("u" to "kuhinja", at(town, "joze", hutter))
    }

    @Test fun `a night in the houses, each in their bed from their bedtime until they get up`() {
        // half past eight: the little ones are in bed, the grown-ups still up
        assertEquals(listOf("zala" to "cot"), ids(kitchen, 20, 30))
        assertEquals(listOf("tine" to "cot"), ids(workshop, 20, 30))
        assertTrue(ids(attic, 20, 30).isEmpty() || ids(attic, 20, 30) == listOf("nejc" to "cot"))
        // midnight: everyone, one to a bed, the children in the children's beds
        assertEquals(listOf("babica" to "zapecek", "ancka" to "mattress", "zala" to "cot"), ids(kitchen, 0))
        assertEquals(listOf("luka" to "bed"), ids(tent, 0))
        assertEquals(listOf("janez" to "zapecek", "joze" to "sidebench"), ids(living, 0))
        assertEquals(listOf("vida" to "mattress", "nejc" to "cot"), ids(attic, 0))
        assertEquals(listOf("france" to "mattress", "tine" to "cot"), ids(workshop, 0))
        assertEquals(listOf("marko" to "bench", "anton" to "trestle"), ids(cellar, 0))
        assertEquals(listOf("tone" to "cot"), ids(smithy, 0))
        assertEquals(listOf("mojca" to "alcove"), ids(school, 0))
        // lying there as the scene has them: Micka is the kitchen's "babica"; Zala isn't one of its people, so herself
        assertEquals(PersonInScene("babica", "grandma", "zapecek", pose = Pose.SLEEP), asleep(kitchen, 0).first())
        assertEquals(PersonInScene("zala", "child2", "cot", pose = Pose.SLEEP), asleep(kitchen, 0).last())
        // nobody has a bed at the fire or in the forest
        assertTrue(asleep(fire, 0).isEmpty()); assertTrue(asleep(forest, 0).isEmpty())
        // the morning: Micka and Luka are up at five (Micka out at the woodpile before it), Zala sleeps until seven
        assertEquals(listOf("ancka" to "zapecek", "zala" to "cot"), ids(kitchen, 4, 45))
        assertEquals(listOf("ancka" to "zapecek", "zala" to "cot"), ids(kitchen, 6))
        assertTrue(ids(tent, 6).isEmpty())
        assertTrue(ids(kitchen, 8).isEmpty())
        // by day nobody is in bed
        for (s in listOf(tent, kitchen, living, attic, workshop, cellar, smithy, school)) for (h in 8..18) assertTrue("${s.id} at $h", asleep(s, h).isEmpty())
    }

    @Test fun `Janez sleeps once he has told his story, or gone home at half past eleven without`() {
        // Jože has been asleep on his run since half past nine; Janez's place waits for him
        assertEquals(listOf("joze" to "sidebench"), ids(living, 23))
        assertEquals(listOf("joze" to "sidebench"), ids(living, 23, 31)) // Janez on his way home
        assertEquals(listOf("janez" to "zapecek", "joze" to "sidebench"), ids(living, 23, 40))
        val told = town.copy(happeningsDone = mapOf("ob-ognju/zgodba" to date.toString()))
        assertEquals(listOf("janez" to "zapecek", "joze" to "sidebench"), ids(living, 23, state = told))
        assertEquals(listOf("joze" to "sidebench"), ids(living, 22, 30, state = told))
    }

    @Test fun `the smith, the teacher and the two who lodge, each from their bedtime, in their own bed, until they get up`() {
        // Tone banks the forge at a quarter to ten (the smithy, his ritual), then lies down on his cot at ten past
        assertTrue(ids(smithy, 21, 50).isEmpty())
        assertEquals(listOf("tone" to "cot"), ids(smithy, 22, 20))
        assertEquals(listOf("tone" to "cot"), ids(smithy, 5, 50))
        assertTrue(ids(smithy, 6, 30).isEmpty())
        // Mojca from ten until half past six
        assertTrue(ids(school, 21, 45).isEmpty())
        assertEquals(listOf("mojca" to "alcove"), ids(school, 22, 15))
        assertEquals(listOf("mojca" to "alcove"), ids(school, 6, 15))
        assertTrue(ids(school, 7).isEmpty())
        // Anton, back from the hives, before Marko: his own bed, not Marko's bench, which waits for him
        assertEquals(listOf("anton" to "trestle"), ids(cellar, 21, 45))
        assertEquals(listOf("marko" to "bench", "anton" to "trestle"), ids(cellar, 23))
        // Anton up at half past five: Marko sleeps on in his own
        assertEquals(listOf("marko" to "bench"), ids(cellar, 6))
        // Jože on the zapeček's other run, from after the watchtower until he is up before dawn; Janez keeps his run
        assertTrue(ids(living, 21, 15).isEmpty())
        assertEquals(listOf("joze" to "sidebench"), ids(living, 21, 45))
        assertEquals(listOf("janez" to "zapecek"), ids(living, 5))
        // a whole night of them, minute by minute: nobody ever changes bed
        val beds = HashMap<String, String>()
        for (m in (21 * 60 until 24 * 60 + 7 * 60) step 5) for (s in listOf(living, cellar, smithy, school)) for (p in asleep(s, (m / 60) % 24, m % 60)) {
            assertEquals("${p.id} at ${m / 60}:${m % 60}", beds.getOrPut(p.id) { p.slot }, p.slot)
        }
        assertEquals(mapOf("janez" to "zapecek", "joze" to "sidebench", "marko" to "bench", "anton" to "trestle", "tone" to "cot", "mojca" to "alcove"), beds)
    }

    @Test fun `a bed waits for who is still up tonight, and is free once they are up or out for the night`() {
        val joze = cast.first { it.id == "joze" }; val micka = cast.first { it.id == "micka" }
        val d = Routine.Day(tellers = Whereabouts.tellers(scenes))
        // the evening before their bedtime: coming
        assertTrue(Routine.toBed(micka, town, date, 20 * 60.0, d))
        assertTrue(Routine.toBed(joze, town, date, 21 * 60 + 10.0, d)) // on the watchtower, before bed
        // up in the morning, or out in the night after their bedtime: their bed is free
        assertFalse(Routine.toBed(micka, town, date, 6 * 60.0, d))
        assertFalse(Routine.toBed(micka, town, date, 4 * 60 + 45.0, d)) // fetching wood before dawn
        assertFalse(Routine.toBed(micka, town, date, 0.0, d.copy(busy = mapOf("micka" to TownPlace.At(TENT))))) // out with Jan's blanket
    }

    @Test fun `who doesn't live here, or has something on tonight, isn't asleep`() {
        // Luka only visiting today: he goes home for the night
        val visiting = town.copy(residents = town.residents.filter { it.id != "luka" }, visitor = Visit("luka", date.toString()))
        assertTrue(ids(tent, 0, state = visiting).isEmpty())
        // a village from before the named residents: everyone is at home
        assertEquals(listOf("luka" to "bed"), ids(tent, 0, state = town.copy(residents = emptyList())))
        // Luka is out in the forest for the owl tonight: the sleeping bag is empty
        assertTrue(ids(tent, 0, busy = mapOf("luka" to TownPlace.Forest)).isEmpty())
        // Babica Micka is up, bringing Jan a blanket in the tent: Ančka has the bench tonight, Zala her cot
        assertEquals(listOf("ancka" to "zapecek", "zala" to "cot"), ids(kitchen, 0, busy = mapOf("micka" to TownPlace.At(TENT))))
        // Nejc awake in the burja with the lamp: his mother sleeps on
        assertEquals(listOf("vida" to "mattress"), ids(attic, 1, busy = mapOf("nejc" to TownPlace.At(HOUSE, "h4"))))
        // nobody of the kitchen at home: its beds stay empty
        assertTrue(ids(kitchen, 0, state = town.copy(residents = town.residents.filter { it.id !in setOf("micka", "ancka", "zala") })).isEmpty())
    }

    @Test fun `a hamlet, the kitchen's is the hut's, and without a tent Luka sleeps where we don't see`() {
        val huts = town.copy(buildings = listOf(Building("u", HUT, 0)))
        assertEquals(BuildingType.HUT, Sleep.bedroom(listOf("house", "hut", "spot:woodpile"), huts))
        assertNull(Sleep.bedroom(listOf("spot:meadow"), huts))
        assertEquals(listOf("babica" to "zapecek", "ancka" to "mattress", "zala" to "cot"), ids(kitchen, 0, state = huts))
        assertTrue(ids(tent, 0, state = huts).isEmpty())
        // with no house, Janez's room is in none: he sleeps in the hut, not in a scene; nor does Jože, who lodges with him
        assertEquals("u" to null, Sleep.bedrooms(scenes, huts, cast).getValue("janez").let { it.building?.id to it.scene?.id })
        assertEquals("u" to null, Sleep.bedrooms(scenes, huts, cast).getValue("joze").let { it.building?.id to it.scene?.id })
    }

    @Test fun `a sleeper is in bed instead of about the place`() {
        val sleepers = asleep(tent, 0)
        val standing = listOf(PersonInScene("luka", "shepherd", "door"), PersonInScene("zala", "child2", "left"))
        assertEquals(listOf(PersonInScene("zala", "child2", "left")) + sleepers, Sleep.inBed(standing, sleepers))
        assertEquals(standing, Sleep.inBed(standing, emptyList()))
    }

    @Test fun `a tap turns a sleeper over, another soon after has them mumble, then they rest`() {
        val id = Sleep.pokeId("luka")
        assertEquals("luka", Sleep.sleeperOf(id))
        assertNull(Sleep.sleeperOf("cat"))
        val pokes = Pokes(listOf(Sleep.poke("luka")))
        val t0 = 10.0
        assertTrue(pokes.tap(id, t0))
        assertEquals(0, pokes.at(t0 + 0.1)[id]?.step) // turning over
        // a tap within a few seconds of it: the mumble, "Pusti me spati …"
        val t1 = t0 + Sleep.TURN_S + 1.0
        assertTrue(pokes.tap(id, t1))
        assertEquals(1, pokes.at(t1 + 0.1)[id]?.step)
        // then they rest: taps go unanswered for a while
        val end = t1 + Sleep.MUMBLE_S
        assertFalse(pokes.tap(id, end + 1.0))
        assertTrue(pokes.at(end + 1.0).isEmpty())
        // rested, a tap turns them over again; one long after the turn starts over at turning, not at the mumble
        val t2 = end + Sleep.REST_S + 0.1
        assertTrue(pokes.tap(id, t2))
        assertEquals(0, pokes.at(t2 + 0.1)[id]?.step)
        assertTrue(pokes.tap(id, t2 + Sleep.TURN_S + Poke.AGAIN_S + 1.0))
        assertEquals(0, pokes.at(t2 + Sleep.TURN_S + Poke.AGAIN_S + 1.1)[id]?.step)
    }

    @Test fun `a place with a bed is quiet at night, and the evening and the night keep their happenings`() {
        val village = GameState(
            seed = 7, age = Age.ZASELEK,
            buildings = listOf(Building("t", TENT, 0), Building("h", HOUSE, 1)),
            residents = listOf("luka", "micka", "ancka", "zala", "nejc", "janez", "anton").map { Resident(it, "2026-01-01") },
        )
        val night = LocalDateTime.of(2026, 9, 24, 23, 0)
        val quiet = tent.copy(
            id = "sotor-test",
            happenings = listOf(
                Happening("vedno", "All day", who = "nejc", chance = 1f),
                Happening("zgodba", "A story", who = "zala", `when` = listOf(TimeOfDay.EVENING, TimeOfDay.NIGHT), chance = 1f),
                Happening("svetilka", "The lamp", who = "luka", `when` = listOf(TimeOfDay.EVENING), chance = 1f),
            ),
        )
        val open = fire.copy(id = "ogenj-test", people = listOf(fire.people.first { it.id == "janez" }), happenings = listOf(Happening("vedno", "All day", who = "janez", chance = 1f)))
        fun on(s: SceneSpec, at: LocalDateTime) = Happenings.active(listOf(s), village, at).map { it.happening.id }.toSet()
        // at night in the tent only what is meant for the night: Zala's story; the all-day one waits for the morning
        assertEquals(setOf("zgodba"), on(quiet, night))
        assertEquals(setOf("vedno", "zgodba", "svetilka"), on(quiet, night.withHour(19)))
        assertEquals(setOf("vedno"), on(quiet, night.plusDays(1).withHour(8)))
        // a place without a bed keeps its all-day happenings at night
        assertEquals(setOf("vedno"), on(open, night))
        // the curated evening and night happenings are as they were: the stories at the fire, the tent's story and blanket
        val today = night.toLocalDate()
        for ((s, ids) in listOf(fire to listOf("zlatorog", "kralj-matjaz", "juha"), tent to listOf("zgodba", "odeja", "svetilka"))) for (h in s.happenings.filter { it.id in ids }) {
            val a = ActiveHappening(s, h.copy(chance = 1f), s.people.first { it.id == h.who })
            for (hour in listOf(19, 23)) {
                val time = TimeOfDay.of(hour)
                assertEquals("${s.id}/${h.id} at $hour", time in h.`when`, Happenings.on(a, village, today, time))
            }
        }
    }
}
