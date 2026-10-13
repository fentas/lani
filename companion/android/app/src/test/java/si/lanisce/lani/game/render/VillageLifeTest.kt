package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.BuildingType.BEEHIVE
import si.lanisce.lani.game.BuildingType.CHURCH
import si.lanisce.lani.game.BuildingType.FIELD
import si.lanisce.lani.game.BuildingType.HOUSE
import si.lanisce.lani.game.BuildingType.HUT
import si.lanisce.lani.game.BuildingType.KOZOLEC
import si.lanisce.lani.game.BuildingType.LIPA
import si.lanisce.lani.game.BuildingType.MARKET
import si.lanisce.lani.game.BuildingType.PALISADE
import si.lanisce.lani.game.BuildingType.SCHOOL
import si.lanisce.lani.game.BuildingType.SMITHY
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.BuildingType.WATCHTOWER
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.Whereabouts
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.game.villagers.Doing
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Routine
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.Where
import si.lanisce.lani.game.villagers.parseVillagers
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The village's day shared out (companion/VILLAGERS.md, "A day in the village"; [Routine], [TownPeople]): Jan's hamlet of
 * 2026-10-13 (sixteen people in the tent, the hut and a house, three of them waiting for the learner at the house) had
 * most of them standing in one group by the fire and the house beside it. Now each has something to do that the village
 * has: their work, the chores of a household spread round the house (the garden, the washing line, the broom at the door,
 * the hens), picking at the forest's edge in season, the children's games; a few at a place, a couple at the fire by day,
 * a handful round it in the evening; the field's soil under whoever walks by it, its crops kept clear.
 */
class VillageLifeTest {
    private val cultures = listOf(File("../../cultures"), File("../cultures")).first { it.isDirectory }
    private val cast: List<Villager> by lazy {
        File(cultures, "primorska/villagers").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
            .map { parseVillagers("[" + it.readText() + "]").single() }.sortedBy { it.order }
    }
    private val scenes: List<SceneSpec> by lazy {
        listOf(File(cultures, "../scenes"), File(cultures, "primorska/scenes")).flatMap { d -> d.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { parseScene(it.readText()) } }
    }

    private val castIds = listOf("micka", "luka", "zala", "janez", "france", "tine", "anton", "tone", "ancka", "marko", "vida", "nejc", "joze")

    /** Jan's hamlet on 2026-10-13: the cast but the teacher, two woodcutters and a smith's apprentice who moved in, their baby. */
    private val jan = GameState(
        seed = 8993091993378563000L, age = Age.ZASELEK, villagers = 17, morale = 70, fire = 95, land = Land.CLASSIC,
        buildings = listOf(
            Building("tent-1", TENT, NO_PLOT), Building("field-2", FIELD, 1), Building("hut-3", HUT, 2, level = 3), Building("well-4", WELL, 3),
            Building("palisade-5", PALISADE, NO_PLOT), Building("kozolec-6", KOZOLEC, 8), Building("beehive-7", BEEHIVE, 5, level = 2),
            Building("church-8", CHURCH, 6), Building("house-9", HOUSE, 0, level = 2), Building("smithy-10", SMITHY, 4), Building("lipa-11", LIPA, 7),
        ),
        residents = castIds.map { Resident(it, "2026-09-24") } + listOf(
            Resident("n-tjasa-mozetic", "2026-10-10", name = "Tjaša Mozetič", art = "woman", voice = "female", role = "Drvarka · Woodcutter"),
            Resident("n-anze-humar", "2026-10-11", name = "Anže Humar", art = "man", voice = "male", role = "Drvar · Woodcutter"),
            Resident("n-blaz-mozetic", "2026-10-12", name = "Blaž Mozetič", art = "man", voice = "male", role = "Kovačev vajenec · Smith's apprentice"),
            Resident("n-urska-mozetic", "2026-10-13", name = "Urška Mozetič", art = "baby", voice = "female", born = "2026-10-13"),
        ),
        projects = mapOf("most" to 2, "mlin" to 1),
    )

    /** A town of the same cast, everyone moved in: houses, the workplaces, the school and the market. */
    private val town: GameState by lazy {
        val types = listOf(HUT, FIELD, WELL, TENT, KOZOLEC, HOUSE, LIPA, BEEHIVE, HOUSE, SMITHY, MARKET, HOUSE, SCHOOL, WATCHTOWER, HOUSE, CHURCH)
        GameState(
            seed = 42, age = Age.VAS, villagers = cast.size + 3, morale = 80, fire = 80,
            buildings = types.mapIndexed { i, t -> Building("b$i", t, i, level = 2, builtAt = i.toLong()) },
            residents = cast.map { Resident(it.id, "2026-01-01") } + jan.residents.filter { it.id.startsWith("n-") && it.art != "baby" },
        )
    }

    private val fit = SceneFit.town(1080, 2400)
    private val october = LocalDate.of(2026, 10, 13)
    private val june = LocalDate.of(2026, 6, 24)
    private val december = LocalDate.of(2026, 12, 9)

    private fun everyone(s: GameState, date: LocalDate) = cast + Residents.people(s, cast, date).filter { p -> cast.none { it.id == p.id } }
    private fun people(s: GameState, date: LocalDate) = everyone(s, date).filter { v -> s.residents.any { it.id == v.id } }

    /** The requests up in Jan's village that day: Micka, Ančka and Vida wait at the house, Tone at his anvil, Janez under the linden. */
    private val janCalls = mapOf(
        "micka" to TownPlace.At(HOUSE), "ancka" to TownPlace.At(HOUSE), "vida" to TownPlace.At(HOUSE), "tone" to TownPlace.At(SMITHY), "janez" to TownPlace.At(LIPA),
    )

    /** The village's day as the app has it ([Whereabouts.day]): where each sleeps, the storyteller, everyone's day shared out. */
    private fun day(s: GameState, date: LocalDate, calls: Map<String, TownPlace> = emptyMap()) =
        Whereabouts.day(scenes, s, cast, everyone(s, date), date).copy(calls = calls)

    /** Who is out where at [minute] (by their day, not in the house), place → ids. */
    private fun out(s: GameState, date: LocalDate, minute: Double, d: Routine.Day): Map<TownPlace, List<String>> =
        people(s, date).filter { it.art != "baby" }.mapNotNull { v ->
            val n = Routine.now(v, s, date, minute, date.monthValue, d)
            if (n.where == Where.OUT && n.doing != Doing.INSIDE && n.errand == null) n.place!! to v.id else null
        }.groupBy({ it.first }, { it.second })

    private val gatherings = setOf(TownPlace.At(SCHOOL), TownPlace.At(CHURCH))

    @Test fun `in Jan's hamlet a few at a place, a couple at the fire by day, nobody in one group`() {
        for ((s, calls) in listOf(jan to janCalls, jan to emptyMap(), town to emptyMap())) {
            for (date in listOf(october, october.plusDays(4), june, december)) {
                val d = day(s, date, calls)
                val dark = Routine.light(date.monthValue)
                var m = dark[0] + 5.0
                while (m < minOf(dark[1].toDouble(), 17.5 * 60)) {
                    for ((place, ids) in out(s, date, m, d)) {
                        if (place in gatherings) continue
                        val waiting = ids.count { calls[it] == place }
                        val most = when {
                            place == TownPlace.Fire -> if (m in 12.5 * 60..13 * 60 + 10.0) 3 else Routine.DAY_FIRE
                            place == TownPlace.Spot("meadow") || place == TownPlace.At(MARKET) -> 4
                            place is TownPlace.At && place.type != HOUSE -> Routine.CROWD * s.buildings.count { it.type == place.type }
                            else -> Routine.CROWD
                        }
                        assertTrue("$date ${m.toInt() / 60}:${m.toInt() % 60} at $place: $ids", ids.size <= maxOf(most, waiting))
                    }
                    m += 7
                }
            }
        }
        // on the map: nobody stands in a group of more than a few by day, the fire never more than a couple
        val d = day(jan, october, janCalls)
        for (h in listOf(8, 9, 10, 11, 12, 13, 14, 15, 16, 17)) {
            val placed = TownPeople.of(jan, people(jan, october), 5.0, h + 0.25f, 10, fit.width, fit.height, date = october, day = d)
            assertTrue("$h: ${placed.size} out", placed.size >= 8)
            assertTrue("$h at the fire: ${placed.filter { it.place == TownPlace.Fire }.map { it.id }}", placed.count { it.place == TownPlace.Fire } <= Routine.DAY_FIRE)
            val groups = groups(placed, 1.25f)
            assertTrue("$h: a group of ${groups.maxOf { it.size }}: ${groups.maxBy { it.size }.map { it.id }}", groups.all { it.size <= 4 })
        }
    }

    /** [placed] in groups: whoever stands within [reach] cells of someone of the group is in it. */
    private fun groups(placed: List<Placed>, reach: Float): List<List<Placed>> {
        val left = placed.toMutableList()
        val out = ArrayList<List<Placed>>()
        while (left.isNotEmpty()) {
            val g = mutableListOf(left.removeAt(0))
            var grew = true
            while (grew) {
                grew = false
                val near = left.filter { p -> g.any { hypot(it.x - p.x, it.y - p.y) < reach } }
                if (near.isNotEmpty()) { g += near; left -= near.toSet(); grew = true }
            }
            out += g
        }
        return out
    }

    @Test fun `the fire, a couple by day, a few at lunch, a handful in the evening, more on Saturday, everyone at a festival`() {
        var saturday = 0; var weekday = 0
        for (k in 0 until 21) {
            val date = october.plusDays(k.toLong())
            val d = day(jan, date)
            // (the baby is carried, not on the map)
            fun fire(m: Double, dd: Routine.Day = d) = people(jan, date).filter { it.art != "baby" }.count { v -> Routine.now(v, jan, date, m, 10, dd).place == TownPlace.Fire && Routine.now(v, jan, date, m, 10, dd).where == Where.OUT }
            assertTrue("$date 10:30", fire(10.5 * 60) <= Routine.DAY_FIRE)
            assertTrue("$date 15:00", fire(15.0 * 60) <= Routine.DAY_FIRE)
            assertTrue("$date lunch", fire(12.75 * 60) <= 3)
            val evening = fire(19.5 * 60)
            assertTrue("$date evening: $evening", evening in 1..Routine.eveningFire(date) + 1)
            if (date.dayOfWeek == DayOfWeek.SATURDAY) saturday = maxOf(saturday, evening) else weekday = maxOf(weekday, evening)
            // at a festival whoever is up gathers round the fire
            val up = people(jan, date).count { v -> Routine.now(v, jan, date, 19.5 * 60, 10, d).where != Where.ASLEEP && v.art != "baby" }
            assertEquals("$date festival", up, fire(19.5 * 60, d.copy(festival = true)))
        }
        assertTrue("Saturday's evening $saturday, a weekday's $weekday", saturday > Routine.eveningFire(october) && weekday <= Routine.eveningFire(october) + 1)
        // the map's evening: the handful round the fire, the rest home with their windows lit
        val d = day(jan, october)
        val evening = TownPeople.of(jan, people(jan, october), 5.0, 19.5f, 10, fit.width, fit.height, date = october, day = d)
        assertTrue("$evening", evening.count { it.place == TownPlace.Fire } <= Routine.eveningFire(october) + 1)
    }

    @Test fun `without a workplace the chores of a household, none of the summer's in the snow, berries and mushrooms in their season`() {
        val chores = setOf(Doing.SWEEP, Doing.HANG, Doing.BEAT, Doing.FEED, Doing.MEND, Doing.HOE)
        val summer = setOf(Doing.HOE, Doing.HANG, Doing.GATHER, Doing.FISH, Doing.WASH)
        for ((month, from) in listOf(6 to june, 10 to october, 12 to december)) {
            val seen = HashMap<String, MutableSet<Doing>>()
            for (k in 0 until 28) {
                val date = from.plusDays(k.toLong())
                val d = day(jan, date)
                for (v in people(jan, date)) if (v.art != "baby") {
                    val plan = Routine.plan(v, v.id in d.tellers)
                    for (st in Routine.stints(v, plan, jan, date, d.homes[v.id] ?: TownPlace.Spot("road"), d)) {
                        seen.getOrPut(v.id) { HashSet() } += st.doing
                        if (month == 12) assertFalse("${v.id} $date: $st in the snow", st.doing in summer)
                        if (st.doing == Doing.GATHER) assertTrue("${v.id} $date picking", date.monthValue in 6..10)
                    }
                }
            }
            // the woodcutters who moved in, the grandmother and the aunt: several of a house's chores in a month
            for (id in listOf("n-tjasa-mozetic", "n-anze-humar", "micka", "ancka")) {
                val did = seen.getValue(id) intersect chores
                assertTrue("$month $id: $did", did.size >= if (month == 12) 2 else 3)
            }
            // the children play, feed the hens, pick berries in summer and mushrooms in autumn
            for (id in listOf("zala", "tine", "nejc")) {
                assertTrue("$month $id", Doing.PLAY in seen.getValue(id))
                assertTrue("$month $id: ${seen[id]}", Doing.FEED in seen.getValue(id) || Doing.GATHER in seen.getValue(id))
            }
            if (month != 12) assertTrue("$month: picking", seen.values.count { Doing.GATHER in it } >= 3)
        }
        // France keeps to his field, Tone to his anvil, Luka to the sheep: their own work first, whoever else comes
        val d = day(jan, october)
        var france = 0; var tone = 0; var luka = 0; var n = 0
        for (k in 0 until 14) {
            val date = october.plusDays(k.toLong())
            val dd = day(jan, date)
            var m = 9.0 * 60
            while (m < 16 * 60) {
                n++
                fun at(id: String) = Routine.now(people(jan, date).first { it.id == id }, jan, date, m, 10, dd).place
                if (at("france") == TownPlace.At(FIELD) || at("france") == TownPlace.At(KOZOLEC)) france++
                if (at("tone") == TownPlace.At(SMITHY)) tone++
                if (at("luka") == TownPlace.Spot("meadow")) luka++
                m += 20
            }
        }
        assertTrue("France $france of $n", france > n * 0.35)
        assertTrue("Tone $tone of $n", tone > n * 0.35)
        assertTrue("Luka $luka of $n", luka > n * 0.4)
        assertTrue(d.people.size >= 16)
    }

    @Test fun `each day and each person their own, the same day the same`() {
        val a = day(jan, october); val b = day(jan, october.plusDays(1))
        val tjasa = people(jan, october).first { it.id == "n-tjasa-mozetic" }; val anze = people(jan, october).first { it.id == "n-anze-humar" }
        fun stints(v: Villager, date: LocalDate, d: Routine.Day) = Routine.stints(v, Routine.plan(v), jan, date, d.homes.getValue(v.id), d)
        assertEquals(stints(tjasa, october, a), stints(tjasa, october, day(jan, october)))
        assertNotEquals(stints(tjasa, october, a).map { it.place to it.doing }, stints(tjasa, october.plusDays(1), b).map { it.place to it.doing })
        assertNotEquals(stints(tjasa, october, a).map { it.place to it.doing }, stints(anze, october, a).map { it.place to it.doing })
        // the town spreads them over more places than the hamlet has
        fun places(s: GameState) = (9..16).flatMap { h -> out(s, october, h * 60.0, day(s, october)).keys }.toSet().size
        assertTrue("${places(town)} places in the town, ${places(jan)} in the hamlet", places(town) > places(jan))
    }

    @Test fun `lunch is in the house, in its kitchen, not on the map`() {
        val d = day(jan, october)
        val micka = people(jan, october).first { it.id == "micka" }
        val lunch = (0 until 28).map { october.plusDays(it.toLong()) }.first { date ->
            Routine.now(micka, jan, date, 12.75 * 60, date.monthValue, day(jan, date)).doing == Doing.INSIDE
        }
        val dd = day(jan, lunch)
        val now = Routine.now(micka, jan, lunch, 12.75 * 60, lunch.monthValue, dd)
        assertEquals(dd.homes.getValue("micka"), now.place)
        assertTrue(TownPeople.of(jan, people(jan, lunch), 5.0, 12.75f, lunch.monthValue, fit.width, fit.height, date = lunch, day = dd).none { it.id == "micka" })
        val kitchen = scenes.first { it.id == "kuhinja" }
        assertTrue(Whereabouts.inScene(kitchen, jan, people(jan, lunch), lunch.atTime(12, 45), dd, scenes).any { it.id == kitchen.people.first { p -> p.villager == "micka" }.id })
        assertTrue(d.people.none { it.art == "baby" })
    }

    @Test fun `the children's games about their place, moving, never in a building or the fire, never a hop`() {
        val scale = TownAnchors.FIRE_SCALE[jan.age.ordinal]
        val land = VillageLayout.of(jan)
        val solid = jan.buildings.filter { it.type.onPlot && it.type != FIELD && it.plot >= 0 }.map { land.centers[it.plot] }
        var games = 0; var running = 0; var hiding = 0; var balls = 0
        for (k in 0 until 21) {
            val date = october.plusDays(k.toLong())
            val d = day(jan, date)
            val kids = people(jan, date).filter { Routine.kindOf(it) == Routine.Kind.CHILD }
            // the minutes two or more children play at one place
            var m = 8 * 60.0
            while (m < 17 * 60) {
                val at = kids.map { Routine.now(it, jan, date, m, 10, d) }.filter { it.doing == Doing.PLAY && it.where == Where.OUT && (it.from == null || it.since > 90) }
                    .groupBy { it.place }.filterValues { it.size >= 2 }
                if (at.isNotEmpty()) {
                    games++
                    val frames = (0 until 40).map { t -> TownPeople.of(jan, people(jan, date), 100.0 + t, (m + 0.5 + t / 60.0).toFloat() / 60f, 10, fit.width, fit.height, date = date, day = d) }
                    for ((t, f) in frames.withIndex()) for (p in f) {
                        assertFalse("$date $m+$t ${p.id} in the fire", TownPeople.inFire(p.x, p.y, scale))
                        assertFalse("$date $m+$t ${p.id} ${p.activity} in a building", solid.any { abs(p.x - it[0]) < 1.5f && abs(p.y - it[1]) < 1.5f })
                        if (t > 0) frames[t - 1].firstOrNull { it.id == p.id }?.let { was -> assertTrue("$date $m+$t ${p.id} hops", hypot(p.x - was.x, p.y - was.y) < 1.7f) }
                        if (p.activity == Activity.RUN) running++
                        if (p.activity == Activity.HIDE || p.activity == Activity.COUNT) hiding++
                        if (p.ball != null) balls++
                    }
                    m += 60
                }
                m += 13
            }
        }
        assertTrue("$games games", games >= 5)
        assertTrue("running $running, hiding $hiding, a ball $balls", running > 0 && balls + hiding > 0)
        // each game on some day at some place
        val seen = (0 until 60).flatMap { k -> listOf(TownPlace.Spot("meadow"), TownPlace.At(WELL), TownPlace.At(LIPA)).map { TownPeople.gameOf(jan, october.plusDays(k.toLong()), it) } }.toSet()
        assertEquals(TownPeople.Game.entries.toSet(), seen)
    }

    @Test fun `ways go round a field's crops, its own workers walk into it`() {
        val land = VillageLayout.of(jan)
        val ways = Walkways.of(jan, fit.width, fit.height)
        val field = land.centers[jan.buildings.first { it.type == FIELD }.plot]
        fun inCrops(p: FloatArray) = abs(p[0] - field[0]) < TownPeople.CROPS - 0.01f && abs(p[1] - field[1]) < TownPeople.CROPS - 0.01f
        var round = 0
        for (a in listOf(floatArrayOf(field[0] - 2.2f, field[1] - 0.3f), floatArrayOf(field[0] + 0.2f, field[1] - 2.3f))) for (b in listOf(floatArrayOf(field[0] + 2.4f, field[1] + 0.4f), floatArrayOf(field[0] - 0.3f, field[1] + 2.4f))) {
            val way = ways.along(listOf(a, b))
            for (i in 1 until way.size) for (s in 0..20) {
                val p = floatArrayOf(way[i - 1][0] + (way[i][0] - way[i - 1][0]) * s / 20f, way[i - 1][1] + (way[i][1] - way[i - 1][1]) * s / 20f)
                assertFalse("${a.toList()} → ${b.toList()} through the crops at ${p.toList()}", inCrops(p))
            }
            if (way.size > 2) round++
        }
        assertTrue(round >= 2)
        // a worker in the field walks straight out of it
        val worker = floatArrayOf(field[0], field[1] + 0.3f)
        assertEquals(2, ways.along(listOf(worker, floatArrayOf(field[0] + 3.5f, field[1] + 0.5f))).size)
        // and through a day, nobody but the field's workers stands or walks in its crops
        val d = day(jan, october, janCalls)
        var m = 8 * 60.0
        while (m < 17 * 60) {
            for (t in 0 until 30 step 3) for (p in TownPeople.of(jan, people(jan, october), t.toDouble(), (m + t / 60.0).toFloat() / 60f, 10, fit.width, fit.height, date = october, day = d)) {
                // (the field's workers in it, walking out of it, taking its hay to the kozolec)
                val now = Routine.now(p.villager, jan, october, m + t / 60.0, 10, d)
                if (p.place == TownPlace.At(FIELD) || now.from == TownPlace.At(FIELD) || now.errand?.from == TownPlace.At(FIELD) || p.load == Load.HAY) continue
                assertFalse("${m.toInt() / 60}:${m.toInt() % 60}+$t ${p.id} ${p.activity} → ${p.place} in the field's crops at ${p.x}, ${p.y}", inCrops(floatArrayOf(p.x, p.y)))
            }
            m += 11
        }
    }

    @Test fun `a field's soil is under whoever walks by it or works in it, its crops in front of them over them`() {
        // the field beside the fire, and the same village without it: someone on its back half in October (the stubble low)
        // is drawn over the soil (it used to be drawn over them, a third of them gone); someone in the middle of it in July
        // partly behind the tall rows in front of them
        val withField = GameState(seed = 7, age = Age.ZASELEK, villagers = 0, fire = 60, land = Land.CLASSIC, buildings = listOf(Building("f", FIELD, 1, level = 2)))
        val without = withField.copy(buildings = emptyList())
        val land = VillageLayout.of(withField)
        val c = land.centers[1]
        val rig = CameraRig.town(fit, 1080, 2400)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        fun mine(s: GameState, x: Float, y: Float, k: Int, month: Int): Int {
            val who = Placed(Villager(id = "walker", name = "walker", art = "man"), x, y, Activity.STAND, false, TownPlace.Fire)
            val lens = Lens.of(k, rig.lookAt(comp.fireX + (c[0] - c[1]) * 4f, comp.fireY + (c[0] + c[1]) * 2f, (rig.minScale * k).toFloat()), 1080, 2400, fit.width, fit.height, fit.width, fit.height)
            val r = VillageRenderer()
            val canvas = PixelCanvas(fit.width, fit.height)
            r.render(canvas, s, Frame(time = 2.0, hour = 11f, month = month, today = "2026-%02d-15".format(month), placed = listOf(who), lens = lens))
            // their own pixels: the object a tap on them finds
            val fx = r.lens.cx(comp.fireX + (x - y) * 4f).toInt(); val fy = r.lens.cy(comp.fireY + (x + y) * 2f).toInt()
            var id = 0
            for (yy in fy - 2 * k downTo fy - 6 * k) for (xx in fx - k..fx + k) if (id == 0 && r.hitTest(xx, yy, 0) == VillageHit.OnVillager("walker")) id = canvas.ids[yy * canvas.width + xx]
            assertNotEquals("the walker at $x, $y found at k=$k", 0, id)
            return canvas.ids.count { it == id }
        }
        for (k in 1..3) {
            // on its back half (behind the field's middle), where the soil was drawn over their feet and legs
            val back = floatArrayOf(c[0] - 0.8f, c[1] - 0.8f)
            val bare = mine(without, back[0], back[1], k, 10); val onSoil = mine(withField, back[0], back[1], k, 10)
            assertTrue("over the soil at its back at k=$k: $onSoil of $bare", onSoil >= bare * 0.92)
            // in the middle of it, among the July wheat: the rows in front of them over their legs, the rest of them seen
            val inIt = floatArrayOf(c[0] + 0.1f, c[1] + 0.1f)
            val open = mine(without, inIt[0], inIt[1], k, 7); val among = mine(withField, inIt[0], inIt[1], k, 7)
            assertTrue("among the crops at k=$k: $among of $open", among < open && among > open * 0.4)
        }
    }
}
