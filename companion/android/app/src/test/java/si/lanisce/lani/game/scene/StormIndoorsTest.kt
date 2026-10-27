package si.lanisce.lani.game.scene

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.EventEnd
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.ambient.Layer
import si.lanisce.lani.game.ambient.Place
import si.lanisce.lani.game.ambient.Soundscape
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.render.SceneFit
import si.lanisce.lani.game.render.TownPeople
import si.lanisce.lani.game.villagers.Reactions
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Routine
import si.lanisce.lani.game.villagers.Stance
import si.lanisce.lani.game.villagers.Stir
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.parseVillagers
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.villagers.VillagerLogic
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Jan, 2026-10-25: "We had a storm: nobody outside, good. I went into a house: nobody there (where are they?). And it was
 * not raining." While a storm, wolves or a bear are about (companion/GAME.md "People react"), whoever the reaction sends home
 * is in their home's scene, the children with their family; whoever gathers by the fire is in the fire's scene; the house
 * the map has them walk into is the house whose room shows them; nobody is in two scenes at once. At home they say a line
 * for inside. The storm's rain is over every scene while it is on and gone after, heard muffled in a room.
 */
class StormIndoorsTest {
    private val pair = L10n.pair
    @After fun back() { L10n.pair = pair; Cultures.use(Cultures.DEFAULT) }

    private val cultures = listOf(File("../../cultures"), File("../cultures")).first { it.isDirectory }
    private val cast: List<Villager> by lazy {
        File(cultures, "primorska/villagers").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
            .map { parseVillagers("[" + it.readText() + "]").single() }.sortedBy { it.order }
    }
    private val scenes: List<SceneSpec> by lazy {
        listOf(File(cultures, "../scenes"), File(cultures, "primorska/scenes")).flatMap { d -> d.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { parseScene(it.readText()) } }
    }

    /** A village of the whole cast, everyone moved in and met: the hut, four houses, the smithy, the school, the watchtower. */
    private val town: GameState by lazy {
        val types = listOf(BuildingType.HUT, BuildingType.FIELD, BuildingType.WELL, BuildingType.TENT, BuildingType.KOZOLEC, BuildingType.HOUSE, BuildingType.LIPA,
            BuildingType.BEEHIVE, BuildingType.HOUSE, BuildingType.SMITHY, BuildingType.MARKET, BuildingType.HOUSE, BuildingType.SCHOOL, BuildingType.WATCHTOWER,
            BuildingType.HOUSE, BuildingType.CHURCH)
        GameState(
            seed = 42, age = Age.VAS, villagers = cast.size, morale = 80, fire = 80, land = Land.CLASSIC,
            buildings = types.mapIndexed { i, t -> Building("b$i", t, i, level = 2, builtAt = i.toLong()) } + Building("pal", BuildingType.PALISADE, NO_PLOT),
            residents = cast.map { Resident(it.id, "2026-01-01") },
        )
    }

    private val october = LocalDate.of(2026, 10, 13)
    private val t0 = october.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val morning = october.atTime(10, 20)
    private fun on(s: GameState, kind: EventKind, strength: Int = 2) = s.copy(event = GameEvent("ev-test", kind, strength, t0, t0 + 36 * 3_600_000L))
    private fun day(s: GameState) = Whereabouts.day(scenes, s, cast, cast, october)
    private val rooms: List<SceneSpec> by lazy { scenes.filter { Homes.isRoom(it) } }
    private fun scene(id: String) = scenes.first { it.id == id }

    /** The villagers [scene] shows at [at] of [s] (by their day or the reaction), as villager ids. */
    private fun shown(scene: SceneSpec, s: GameState, at: LocalDateTime, day: Routine.Day = day(s)): List<String> =
        Whereabouts.inScene(scene, s, cast, at, day, scenes).map { p -> scene.people.firstOrNull { it.id == p.id }?.villager ?: p.id }

    /** The building [room] is seen in, in [s] (the house it is dealt, or the hut its kitchen is). */
    private fun houseOf(room: SceneSpec, s: GameState): Building = Homes.buildingOf(room, s, null, Homes.deal(scenes, s))!!

    @Test fun `in a storm each house's room has its people at home - Micka, Ančka and Zala in the kitchen, Janez and Jože in the living room`() {
        val storm = on(town, EventKind.STORM)
        val d = day(storm)
        assertEquals(listOf("micka", "ancka", "zala").sorted(), shown(scene("kuhinja"), storm, morning, d).sorted())
        assertEquals(listOf("janez", "joze").sorted(), shown(scene("v-hisi"), storm, morning, d).sorted())
        assertEquals(listOf("france", "tine").sorted(), shown(scene("v-delavnici"), storm, morning, d).sorted())
        assertEquals(listOf("marko", "anton").sorted(), shown(scene("v-kleti"), storm, morning, d).sorted())
        assertEquals(listOf("vida", "nejc").sorted(), shown(scene("v-podstresju"), storm, morning, d).sorted())
        // the smith in his smithy, the teacher in her school, the shepherd in his tent; nobody out in the field or on the square
        assertEquals(listOf("tone"), shown(scene("v-kovacnici"), storm, morning, d))
        assertEquals(listOf("mojca"), shown(scene("v-soli"), storm, morning, d))
        assertEquals(listOf("luka"), shown(scene("v-sotoru"), storm, morning, d))
        for (id in listOf("na-njivi", "na-vasi", "ob-ognju", "na-trznici", "ob-potoku", "v-cerkvi")) assertTrue(id, shown(scene(id), storm, morning, d).isEmpty())
        // each in their own home: the house the room is seen in is where they sleep (Sleep.homes, what the map's doors are)
        for (r in rooms) {
            val home = TownPlace.of(houseOf(r, storm), storm)
            for (v in shown(r, storm, morning, d)) assertEquals("${r.id}: $v", home, d.homes[v])
        }
    }

    @Test fun `who the map has walk home in the storm is in the room of the house they walk to, nobody in two scenes`() {
        val storm = on(town, EventKind.STORM)
        val d = day(storm)
        val fit = SceneFit.town(1080, 2400)
        val hour = morning.hour + morning.minute / 60f
        // five seconds in: on their way to their doors
        val walking = TownPeople.of(storm, cast, 5.0, hour, 10, fit.width, fit.height, date = october, day = d, wall = t0 + 5_000L)
        assertTrue("going home: ${walking.map { it.id }}", walking.size >= 6)
        val whereShown = scenes.flatMap { s -> shown(s, storm, morning, d).map { it to s } }
        // nobody in two scenes at once (the sleepers by day: none)
        assertEquals(whereShown.map { it.first }.distinct().size, whereShown.size)
        for (p in walking) {
            val s = whereShown.firstOrNull { it.first == p.id }?.second ?: continue
            val there = Whereabouts.placeOf(s, storm, scenes)
            assertEquals("${p.id} walks to ${p.place}, shown in ${s.id}", p.place, there)
        }
        // every one of the cast walking home whose home has a room with a free spot is in it
        val roomed = rooms.associateBy { TownPlace.of(houseOf(it, storm), storm) }
        for (p in walking) roomed[p.place]?.let { r -> assertTrue("${p.id} in ${r.id}", p.id in shown(r, storm, morning, d)) }
    }

    @Test fun `while the wolves are about the children and the old are by the fire, the lookout up the tower, the others home, as on the map`() {
        val wolves = on(town, EventKind.WOLVES)
        val d = day(wolves)
        val st = Reactions.stances(wolves, cast, EventKind.WOLVES, 2)
        val fire = shown(scene("ob-ognju"), wolves, morning, d)
        assertTrue("$fire", fire.size >= 3)
        assertTrue("$fire", fire.all { Reactions.byFire(st.getValue(it)) })
        // more than the fire's own people when more gather (by the stump and the woodpile too, and at the crowd's spots
        // round the fire: FireCrowd)
        val gathered = cast.filter { v -> Reactions.byFire(st.getValue(v.id)) && Routine.now(v, wolves, morning, d).let { it.where == si.lanisce.lani.game.villagers.Where.OUT && it.doing != si.lanisce.lani.game.villagers.Doing.INSIDE } }
        assertEquals(minOf(gathered.size, 5 + FireCrowd.spots(wolves).size), fire.size)
        assertEquals(listOf("joze"), shown(scene("na-stolpu"), wolves, morning, d))
        // the farmer, the winemaker and the beekeeper went home: in their rooms
        assertTrue("france" in shown(scene("v-delavnici"), wolves, morning, d))
        assertTrue(shown(scene("v-kleti"), wolves, morning, d).containsAll(listOf("marko", "anton")))
        // a child or one of the old at home is one who was in the house when the wolves came (the map keeps them in)
        for (r in rooms) for (v in shown(r, wolves, morning, d)) if (Reactions.byFire(st.getValue(v))) {
            val now = Routine.now(cast.first { it.id == v }, wolves, morning, d)
            assertFalse("$v in ${r.id}", now.where == si.lanisce.lani.game.villagers.Where.OUT && now.doing != si.lanisce.lani.game.villagers.Doing.INSIDE)
        }
        // the defenders are out by the forest's edge: in no scene
        val all = scenes.flatMap { shown(it, wolves, morning, d) }
        for (v in st.filterValues { it == Stance.DEFENDER }.keys) assertFalse(v, v in all)
        assertEquals(all.distinct().size, all.size)
        // as the map has them walk: to the fire, to the tower, home
        val fit = SceneFit.town(1080, 2400)
        val walking = TownPeople.of(wolves, cast, 5.0, morning.hour + morning.minute / 60f, 10, fit.width, fit.height, date = october, day = d, wall = t0 + 5_000L)
        for (p in walking) {
            val s = scenes.firstOrNull { p.id in shown(it, wolves, morning, d) } ?: continue
            val there = Whereabouts.placeOf(s, wolves, scenes)
            assertEquals("${p.id}: ${p.place} vs ${s.id}", p.place, there)
        }
    }

    @Test fun `at night the wolves get the lookout and the defenders out of bed, their beds empty`() {
        val night = october.atTime(23, 40)
        val wolves = on(town, EventKind.WOLVES)
        val room = scene("v-hisi")
        val minute = Routine.minuteOf(night)
        val calm = Sleep.sleepers(room, town, october, minute, cast, scenes, day(town)).map { it.id }
        assertTrue("$calm", "joze" in calm)
        val up = Sleep.sleepers(room, wolves, october, minute, cast, scenes, day(wolves)).map { it.id }
        assertFalse("$up", "joze" in up)
        assertEquals(listOf("joze"), shown(scene("na-stolpu"), wolves, night, day(wolves)))
        // the smith is up and out by the palisade, not on his cot
        assertTrue(Sleep.sleepers(scene("v-kovacnici"), wolves, october, minute, cast, scenes, day(wolves)).isEmpty())
    }

    @Test fun `met at home they say a line for inside, out by the fire the event's own, their greeting again after`() {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        val file = Cultures.reactions("primorska")!!
        val micka = cast.first { it.id == "micka" }
        val storm = on(town, EventKind.STORM)
        val home = VillagerLogic.reaction(micka, storm, october, cast, t0 + 60_000L, home = true)
        assertNotNull(home)
        assertTrue("${home!!.sl}", home.sl in file.storm.home.map { it.by["sl"] })
        val out = VillagerLogic.reaction(micka, storm, october, cast, t0 + 60_000L)!!
        assertTrue(out.sl in file.storm.elder.map { it.by["sl"] })
        val wolves = on(town, EventKind.WOLVES)
        assertTrue(VillagerLogic.reaction(cast.first { it.id == "marko" }, wolves, october, cast, t0 + 60_000L, home = true)!!.sl in file.wolves.home.map { it.by["sl"] })
        // after a fight won: the cheer, also at home (no line for inside then)
        val won = town.copy(ended = EventEnd(EventKind.WOLVES, won = true, at = t0))
        val cheer = Reactions.line(Stir(EventKind.WOLVES, Stir.Phase.WON, 10.0), Stance.OTHER, micka, october, file, home = true)
        assertTrue(cheer!!.by["sl"] in (file.won.other + file.won.elder).map { it.by["sl"] })
        assertNull(VillagerLogic.reaction(micka, won, october, cast, t0 + 3_600_000L, home = true))
        // every pack has lines for inside for the wolves, the bear and the storm, and they pass the pack's check
        for (c in listOf("primorska", "friuli", "kaernten", "lakeland")) {
            val r = Cultures.reactions(c)!!
            for ((k, t) in listOf("wolves" to r.wolves, "bear" to r.bear, "storm" to r.storm)) assertTrue("$c $k", t.home.size >= 2)
            assertEquals(c, emptyList<String>(), Cultures.checkReactions(c, r))
        }
    }

    @Test fun `the storm's rain is over every scene while it is on, gone after, heard muffled in a room`() {
        val storm = on(town, EventKind.STORM)
        for (s in listOf("kuhinja", "na-njivi", "ob-ognju")) assertEquals(Sky.STORM, DaySky.over(storm, october, s))
        assertEquals(Sky.STORM, DaySky.over(storm, october))
        assertEquals(Sky.STORM, Soundscape.villageSky(storm, october))
        // over: the day's sky again (clear, or what a dialog left), nothing kept of the storm
        val after = town.copy(ended = EventEnd(EventKind.STORM, won = true, at = t0))
        assertEquals(Sky.CLEAR, DaySky.over(after, october, "kuhinja"))
        val shower = Sky(rain = 0.4f, gloom = 0.6f)
        val kept = after.copy(sky = DaySky(october.toString(), "na-njivi", shower))
        assertEquals(shower, DaySky.over(kept, october, "na-njivi"))
        assertEquals(Sky.CLEAR, DaySky.over(kept, october, "kuhinja"))
        // the merchant or a feast: no rain
        assertEquals(Sky.CLEAR, DaySky.over(on(town, EventKind.MERCHANT), october, "na-vasi"))
        // heard: in the open the rain itself, in a room its muffled sound (fuller: the walls take the hiss), the cellar a little
        val open = Soundscape.mix(Place.Scene("field"), 11f, 10, Sky.STORM)
        assertTrue((open[Layer.RAIN] ?: 0f) > 0.9f)
        assertNull(open[Layer.RAIN_IN])
        val kitchen = Soundscape.mix(Place.Scene("kitchen"), 11f, 10, Sky.STORM)
        assertNull(kitchen[Layer.RAIN])
        assertTrue("${kitchen[Layer.RAIN_IN]}", (kitchen[Layer.RAIN_IN] ?: 0f) in 0.5f..0.65f)
        assertTrue((Soundscape.mix(Place.Scene("attic"), 11f, 10, Sky.STORM)[Layer.RAIN_IN] ?: 0f) > 0.95f)
        assertTrue((Soundscape.mix(Place.Scene("cellar"), 11f, 10, Sky.STORM)[Layer.RAIN_IN] ?: 0f) in 0.1f..0.25f)
        assertNull(Soundscape.mix(Place.Scene("kitchen"), 11f, 10)[Layer.RAIN_IN])
        // the indoor rain is a loop of the app's (assets/ambient/rain-in.ogg)
        val assets = listOf(File("src/main/assets/ambient"), File("app/src/main/assets/ambient")).first { it.isDirectory }
        assertTrue(File(assets, "${Layer.RAIN_IN.file}.ogg").isFile)
    }

    @Test fun `a cold hearth - the house's fire out shows its stove cold, the others lit`() {
        val s = town.copy(hearths = si.lanisce.lani.game.HearthDay(on = october.toString(), out = listOf("b14"), blown = listOf("b14")))
        val dealt = Homes.deal(scenes, s)
        val outRoom = dealt.getValue("b14")
        assertTrue(Homes.world(outRoom, s, scenes = scenes).cold)
        val lit = dealt.getValue("b5")
        assertFalse(Homes.world(lit, s, scenes = scenes).cold)
        // a building without a hearth of its own is never cold
        assertFalse(si.lanisce.lani.game.Hearths.cold(s, s.buildings.first { it.type == BuildingType.CHURCH }))
    }
}
