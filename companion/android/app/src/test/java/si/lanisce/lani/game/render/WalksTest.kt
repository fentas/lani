package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.Sleep
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.Whereabouts
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.game.villagers.Doing
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Routine
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.Where
import si.lanisce.lani.game.villagers.parseVillagers
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The ways people walk on the map ([Walkways], [TownPeople]): never through the fire nor a building, round the fire on its
 * nearer side; at their work when their day says so, an errand of it now and then, nobody pottering about. A real Primorska village
 * (data/app/game.json, 2026-10-01: a hamlet, the field beside the fire), a generated land and a town.
 */
class WalksTest {
    private val cultures = listOf(File("../../cultures"), File("../cultures")).first { it.isDirectory }
    private val cast: List<Villager> by lazy {
        File(cultures, "primorska/villagers").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
            .map { parseVillagers("[" + it.readText() + "]").single() }.sortedBy { it.order }
    }
    private val scenes: List<SceneSpec> by lazy {
        listOf(File(cultures, "../scenes"), File(cultures, "primorska/scenes")).flatMap { d -> d.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { parseScene(it.readText()) } }
    }

    /** Who lives in the Primorska village, in the order they came. */
    private val janIds = listOf("micka", "luka", "zala", "janez", "france", "tine", "anton", "tone", "ancka", "marko", "vida")

    /** The Primorska village on 2026-10-01: the tent at the pond, the field beside the fire, the hut, the well, a palisade, the smithy. */
    private val jan = GameState(
        seed = 8993091993378563000L, age = Age.ZASELEK, villagers = 11, morale = 70, fire = 95, land = Land.CLASSIC,
        buildings = listOf(
            Building("tent-1", TENT, NO_PLOT), Building("field-2", FIELD, 1), Building("hut-3", HUT, 2, level = 2), Building("well-4", WELL, 3),
            Building("palisade-5", PALISADE, NO_PLOT), Building("kozolec-6", KOZOLEC, 8), Building("beehive-7", BEEHIVE, 5, level = 2),
            Building("church-8", CHURCH, 6), Building("house-9", HOUSE, 0), Building("smithy-10", SMITHY, 4), Building("lipa-11", LIPA, 7),
        ),
        residents = janIds.map { Resident(it, "2026-09-24") },
        projects = mapOf("most" to 2, "mlin" to 1),
    )

    /** A village on a generated land (a valley), and a town on another (the hills). */
    private fun generated(l: Landscape, seed: Long, age: Age, types: List<BuildingType>) = GameState(
        seed = seed, age = age, villagers = 11, morale = 70, fire = 80, land = Land.of(l, seed),
        buildings = types.mapIndexed { i, t -> Building("b$i", t, if (t.onPlot) i else NO_PLOT, level = 1 + i % 2) },
        residents = janIds.map { Resident(it, "2026-09-24") },
    )
    private val valley by lazy { generated(Landscape.VALLEY, 42L, Age.VAS, listOf(FIELD, HUT, WELL, KOZOLEC, HOUSE, PALISADE, LIPA, CHURCH, BEEHIVE, SMITHY, HOUSE, WATCHTOWER, FIELD, HOUSE)) }
    private val town by lazy {
        generated(Landscape.HILLS, 777L, Age.MESTO, listOf(FIELD, HUT, WELL, KOZOLEC, HOUSE, PALISADE, LIPA, CHURCH, BEEHIVE, SMITHY, HOUSE, WATCHTOWER, FIELD, HOUSE, MARKET, HOUSE, SCHOOL, KOZOLEC, HOUSE, BEEHIVE, HOUSE, WELL, HOUSE, MARKET))
    }

    private val fit = SceneFit.town(1080, 2400)
    private val october = LocalDate.of(2026, 10, 1)
    private val june = LocalDate.of(2026, 6, 24)
    private val december = LocalDate.of(2026, 12, 9)

    private fun people(s: GameState) = cast.filter { c -> s.residents.any { it.id == c.id } }
    private fun day(s: GameState) = Routine.Day(homes = Sleep.homes(scenes, s, people(s)), tellers = Whereabouts.tellers(scenes))

    /** The buildings' footprints nobody walks or stands in (a field's excepted). */
    private fun footprints(s: GameState): List<FloatArray> {
        val land = VillageLayout.of(s)
        return s.buildings.filter { it.type.onPlot && it.type != FIELD && it.plot >= 0 }.map { land.centers[it.plot] }
    }
    private fun inBuilding(s: GameState, x: Float, y: Float) = footprints(s).any { abs(x - it[0]) < 1.5f && abs(y - it[1]) < 1.5f }

    /** Every point along [way], [step] cells apart. */
    private fun points(way: List<FloatArray>, step: Float = 0.05f): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        for (i in 1 until way.size) {
            val a = way[i - 1]; val b = way[i]
            val n = (hypot(b[0] - a[0], b[1] - a[1]) / step).toInt().coerceAtLeast(1)
            for (k in 0..n) out += floatArrayOf(a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n)
        }
        return out
    }

    @Test fun `the fire's core holds wherever someone would stand in its flames, at every age`() {
        for (scale in TownAnchors.FIRE_SCALE) {
            val block = Walkways.fire(scale)
            var x = -12f
            while (x < 12f) {
                var y = -12f
                while (y < 12f) {
                    if (TownPeople.inFire(x, y, scale)) assertTrue("$x, $y at $scale", block.inside(x, y))
                    y += 0.1f
                }
                x += 0.1f
            }
            // and the corners to walk round it by are all outside it
            for (i in block.corners.indices step 2) assertFalse(TownPeople.inFire(block.corners[i], block.corners[i + 1], scale))
        }
    }

    @Test fun `a way never goes through the fire or a building, and passes the fire on its nearer side`() {
        for ((name, s) in listOf("jan" to jan, "valley" to valley, "town" to town)) {
            val comp = VillageLayout.composition(fit.width, fit.height, false)
            val scale = TownAnchors.FIRE_SCALE[s.age.ordinal]
            val land = VillageLayout.of(s)
            val ways = Walkways.of(s, fit.width, fit.height)
            // the places: each building's corner, the fire's ring, the spots round the clearing
            val places = s.buildings.filter { it.type.onPlot && it.plot >= 0 }.mapNotNull { b -> land.centers[b.plot].let { floatArrayOf(it[0] + 2f, it[1] + 0.6f) } } +
                (0 until 6).map { TownPeople.aroundFire(it, scale) } +
                listOf("woodpile", "rocks", "riverbank", "road").mapNotNull { TownAnchors.visitorSpot(s, TownPlace.Spot(it), comp, fit.width, fit.height) }
            val starts = places.map { TownPeople.offFire(it[0], it[1], scale) }.filter { p -> !inBuilding(s, p[0], p[1]) }
            assertTrue("$name: ${starts.size} places", starts.size >= 10)
            var ways0 = 0
            for (a in starts) for (b in starts) if (a !== b) for (seed in listOf(0, 1 shl 9, 2 shl 9)) {
                val way = TownPeople.route(a, b, false, false, null, land, seed, ways)
                ways0++
                assertEquals(a.toList(), way.first().toList()); assertEquals(b.toList(), way.last().toList())
                for (p in points(way)) {
                    assertFalse("$name: ${a.toList()} → ${b.toList()} through the fire at ${p.toList()}", TownPeople.inFire(p[0], p[1], scale))
                    assertFalse("$name: ${a.toList()} → ${b.toList()} through a building at ${p.toList()}", inBuilding(s, p[0], p[1]))
                }
                // round, not about: never much longer than the way out to the road and along it
                val straight = hypot(b[0] - a[0], b[1] - a[1])
                assertTrue("$name: ${a.toList()} → ${b.toList()} ${Walkways.length(way)} for $straight", Walkways.length(way) < straight * 2.2f + 16f)
            }
            assertTrue(ways0 > 100)
        }
        // along the road past the fire: round it on its nearer side, in front of it (the shorter way), back on the road after
        val ways = Walkways.of(jan, fit.width, fit.height)
        val past = Walkways.around(floatArrayOf(-9f, 0.35f), floatArrayOf(9f, 0.35f), ways.blocks)
        assertTrue(past.size > 2)
        val scale = TownAnchors.FIRE_SCALE[jan.age.ordinal]
        assertTrue(points(past).none { TownPeople.inFire(it[0], it[1], scale) })
        val reach = TownAnchors.fireReach(scale) + TownPeople.BODY_HALF
        assertTrue("in front: ${past.map { it.toList() }}", points(past).filter { abs((it[0] - it[1]) * 4f) < reach }.all { (it[0] + it[1]) * 2f > 0f })
        // nothing in the way: straight
        assertEquals(2, Walkways.around(floatArrayOf(8f, 6f), floatArrayOf(12f, 6.5f), ways.blocks).size)
    }

    /** Everyone on the map in [s] on [date] for [seconds] from [h]:[m], each second, by their plain day. */
    private fun watch(s: GameState, date: LocalDate, h: Int, m: Int, seconds: Int = 90, standing: Map<String, TownPlace> = emptyMap(), d: Routine.Day = day(s)) =
        (0 until seconds).map { t -> TownPeople.of(s, people(s), t.toDouble(), h + (m + t / 60f) / 60f, date.monthValue, fit.width, fit.height, standing = standing, date = date, day = d) }

    @Test fun `through a day nobody walks through the fire or a building`() {
        for ((name, s) in listOf("jan" to jan, "valley" to valley, "town" to town)) {
            val scale = TownAnchors.FIRE_SCALE[s.age.ordinal]
            for (date in listOf(october, june, october.plusDays(2))) {
                var minute = 6 * 60 + 15
                while (minute < 21 * 60) {
                    for ((t, frame) in watch(s, date, minute / 60, minute % 60, seconds = 75).withIndex()) for (p in frame) {
                        assertFalse("$name $date ${minute / 60}:${minute % 60}+${t}s: ${p.id} ${p.activity} in the fire at ${p.x}, ${p.y}", TownPeople.inFire(p.x, p.y, scale))
                        assertFalse("$name $date ${minute / 60}:${minute % 60}+${t}s: ${p.id} ${p.activity} in a building at ${p.x}, ${p.y}", inBuilding(s, p.x, p.y))
                    }
                    minute += 23
                }
            }
        }
    }

    @Test fun `nobody of the village potters about, at their place they stay put, a walk goes somewhere`() {
        val s = jan
        val d = day(s)
        for (date in listOf(october, june)) {
            var minute = 7 * 60 + 40
            while (minute < 19 * 60) {
                val frames = watch(s, date, minute / 60, minute % 60, seconds = 40)
                for (v in people(s)) {
                    val now = Routine.now(v, s, date, minute - 0.1, date.monthValue, d)
                    val settled = now.where == Where.OUT && now.errand == null && (now.from == null || now.since > 60) &&
                        Routine.now(v, s, date, minute + 41 / 60.0, date.monthValue, d).let { it.place == now.place && it.errand == null }
                    if (!settled) continue
                    val seen = frames.mapNotNull { f -> f.firstOrNull { it.id == v.id } }
                    if (seen.isEmpty()) continue
                    assertTrue("${v.id} at ${now.place} walks about at $minute on $date", seen.none { it.walking })
                    // in one spot all the while (they move over only when someone there before them leaves)
                    val company = frames.map { f -> f.filter { it.place == now.place && !it.walking }.map { it.id }.toSet() }
                    if (company.all { it == company[0] })
                        assertTrue("${v.id} moves at ${now.place} at $minute on $date", seen.all { hypot(it.x - seen[0].x, it.y - seen[0].y) < 1e-3f })
                }
                minute += 31
            }
        }
        // nobody hops: a walk starts at their spot where they were and ends at their spot where they go (on the log, by the
        // fire), and nobody moves over when someone else comes or goes; second by second, through the day into the evening
        var moves = 0
        for (date in listOf(october, june, december)) {
            var minute = 7 * 60 + 30
            while (minute < 21 * 60) {
                val frames = watch(s, date, minute / 60, minute % 60, seconds = 150)
                for (t in 1 until frames.size) for (p in frames[t]) {
                    val was = frames[t - 1].firstOrNull { it.id == p.id } ?: continue
                    val d = hypot(p.x - was.x, p.y - was.y)
                    if (d > 1e-3f) moves++
                    assertTrue("${p.id} hops $d from ${was.activity} at ${was.place} to ${p.activity} at ${p.place} at $minute+${t}s on $date", d < 1.7f)
                }
                minute += 13
            }
        }
        assertTrue("$moves moves", moves > 100)
    }

    @Test fun `France works his field, takes the hay to the kozolec now and then, and is in it with his happening`() {
        val s = jan
        val d = day(s)
        val france = cast.first { it.id == "france" }
        val field = VillageLayout.of(s).centers[1]
        fun inField(p: Placed) = abs(p.x - field[0]) <= 1.5f && abs(p.y - field[1]) <= 1.5f
        var hoeing = 0; var errands = 0; var hay = 0; var back = 0
        for (k in 0 until 14) {
            val date = october.plusDays(k.toLong())
            val stints = Routine.stints(france, Routine.plan(france), s, date, d.homes.getValue("france"))
            for (st in stints.filter { it.place == TownPlace.At(FIELD) }) {
                assertEquals(Doing.HOE, st.doing)
                // the hay goes once or twice a stretch, never a stretch of carrying
                assertTrue("$st", st.errands.size <= 2)
                for (e in st.errands) {
                    assertEquals(TownPlace.At(FIELD), e.from); assertEquals(TownPlace.At(KOZOLEC), e.to)
                    assertTrue(e.at >= st.start + 5 && e.at + Routine.ERRAND <= st.end)
                }
                // hoeing in the field, away from its errands
                val mid = (st.start + 6..st.end - 2).firstOrNull { m -> !Routine.dark(10, m.toDouble()) && st.errands.none { m >= it.at - 1 && m <= it.at + Routine.ERRAND + 1 } } ?: continue
                val p = TownPeople.of(s, people(s), 3.0, mid / 60f, 10, fit.width, fit.height, date = date, day = d).single { it.id == "france" }
                assertTrue("$date ${mid / 60}:${mid % 60}: $p", inField(p) && p.activity == Activity.HOE)
                hoeing++
                for (e in st.errands) {
                    if (Routine.dark(10, e.at.toDouble()) || Routine.dark(10, e.at + Routine.ERRAND + 1.0)) continue
                    errands++
                    // on the way with the hay, out of the field's scene; back at the hoe when it's done
                    val trip = (0 until Routine.ERRAND * 60 step 2).map { t -> TownPeople.of(s, people(s), t.toDouble(), (e.at + t / 60f) / 60f, 10, fit.width, fit.height, date = date, day = d).single { it.id == "france" } }
                    if (trip.any { it.activity == Activity.CARRY && it.load == Load.HAY }) hay++
                    assertTrue(trip.any { !inField(it) })
                    val after = TownPeople.of(s, people(s), 3.0, (e.at + Routine.ERRAND + 0.5f) / 60f, 10, fit.width, fit.height, date = date, day = d).single { it.id == "france" }
                    if (inField(after) && after.activity == Activity.HOE) back++
                }
            }
        }
        assertTrue("$hoeing stretches", hoeing >= 14)
        assertTrue("$errands errands", errands >= 5)
        assertEquals(errands, hay); assertEquals(errands, back)
        // over a fortnight he is at the field more than anywhere else by day
        var at = 0; var all = 0
        for (k in 0 until 14) {
            var m = 8 * 60
            while (m < 17 * 60) {
                val now = Routine.now(france, s, october.plusDays(k.toLong()), m.toDouble(), 10, d)
                all++; if (now.place == TownPlace.At(FIELD) || now.place == TownPlace.At(KOZOLEC)) at++
                m += 10
            }
        }
        assertTrue("$at of $all", at > all * 0.5)
        // "France žanje pšenico": with his happening at the field he is in it by daylight, harvesting with a wave now and then
        val busy = mapOf("france" to TownPlace.At(FIELD))
        val frames = watch(s, october, 10, 15, seconds = 20, standing = busy).map { f -> f.single { it.id == "france" } }
        assertTrue(frames.all { inField(it) && (it.activity == Activity.HOE || it.activity == Activity.WAVE) })
        assertTrue(frames.any { it.activity == Activity.HOE } && frames.any { it.activity == Activity.WAVE })
        assertEquals(TownPlace.At(FIELD), frames.first().place)
    }
}
