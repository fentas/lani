package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.FrameBudget
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
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.Founding
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.Whereabouts
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Residents
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.parseVillagers
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.imageio.ImageIO
import kotlin.math.hypot

/**
 * Nobody walks through the palisade's stakes (game/render/PalisadeWalk, Walkways, TownPeople.route): a walk between a
 * point inside the ring and one outside goes through a gate (the road's, the path's into the woods, the river gate), a walk
 * inside never leaves it, the water gates and a shore are no way through; a village without a palisade walks as before.
 * Sampled over every age and landscape, Jan's classic hamlet and Teo's coast among them, on a phone and on Teo's tablet
 * both ways round; people through a day and while wolves are about. Path overlays and a strip of someone going into the
 * woods in build/village-snapshots/palisade-walks for visual review.
 */
class PalisadeWalksTest {
    private val cultures = listOf(File("../../cultures"), File("../cultures")).first { it.isDirectory }

    private fun cast(culture: String): List<Villager> =
        File(cultures, "$culture/villagers").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
            .map { parseVillagers("[" + it.readText() + "]").single() }.sortedBy { it.order }

    private fun scenes(culture: String): List<SceneSpec> =
        listOf(File(cultures, "../scenes"), File(cultures, "$culture/scenes")).filter { it.isDirectory }
            .flatMap { d -> d.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { parseScene(it.readText()) } }

    /** Jan's hamlet on 2026-10-01 (WalksTest's): the tent at the pond, the field beside the fire, a palisade, the classic land. */
    private val jan = GameState(
        seed = 8993091993378563000L, age = Age.ZASELEK, villagers = 11, morale = 70, fire = 95, land = Land.CLASSIC,
        buildings = listOf(
            Building("tent-1", TENT, NO_PLOT), Building("field-2", FIELD, 1), Building("hut-3", HUT, 2, level = 2), Building("well-4", WELL, 3),
            Building("palisade-5", PALISADE, NO_PLOT), Building("kozolec-6", KOZOLEC, 8), Building("beehive-7", BEEHIVE, 5, level = 2),
            Building("church-8", CHURCH, 6), Building("house-9", HOUSE, 0), Building("smithy-10", SMITHY, 4), Building("lipa-11", LIPA, 7),
        ),
        residents = listOf("micka", "luka", "zala", "janez", "france", "tine", "anton", "tone", "ancka", "marko", "vida").map { Resident(it, "2026-09-24") },
        projects = mapOf("most" to 2, "mlin" to 1),
    )

    /** Teo's village on the coast (Friuli), as the frame profile and the coast's tests have it. */
    private val teo = Founding("friuli", Landscape.COAST, 551323397L).village(1_000L).copy(morale = 70, fire = 70)

    private val hamlet = listOf(FIELD, HUT, WELL, KOZOLEC, TENT, PALISADE, HUT, FIELD, LIPA)
    private val village = listOf(FIELD, HUT, WELL, KOZOLEC, HOUSE, PALISADE, LIPA, CHURCH, BEEHIVE, SMITHY, HOUSE, WATCHTOWER, FIELD, HOUSE)
    private val town = village + listOf(MARKET, HOUSE, SCHOOL, KOZOLEC, HOUSE, BEEHIVE, HOUSE, WELL, HOUSE, MARKET, HOUSE, HUT, HOUSE, WELL)

    private fun types(age: Age) = when (age) {
        Age.OGENJ, Age.TABOR -> listOf(TENT, PALISADE, TENT, FIELD, HUT)
        Age.ZASELEK -> hamlet
        Age.VAS -> village
        else -> town
    }

    /** [s] grown to [age]: its plots built as far as the age has them, a palisade round it (or none: [walled]). */
    private fun grown(s: GameState, age: Age, walled: Boolean = true): GameState {
        var plot = 0
        val bs = types(age).mapIndexedNotNull { i, t ->
            when {
                t == PALISADE -> if (walled) Building("pal", t, NO_PLOT) else null
                plot >= PLOTS_PER_AGE[age.ordinal] -> null
                else -> Building("b$i", t, plot++, level = 1 + i % 2)
            }
        }
        return s.copy(age = age, villagers = 8, morale = 70, fire = 70, buildings = bs)
    }

    private val ages = Age.entries.filter { it >= Age.TABOR }

    /** A phone upright (the woods below), Teo's tablet upright (the woods) and on its side (no woods). */
    private val canvases = listOf(360 to 800, 320 to 512, 320 to 200)

    // ---------------------------------------------------------------- the ring as the test sees it, on its own

    private val walked = setOf(PalisadeRing.Kind.ROAD, PalisadeRing.Kind.PATH, PalisadeRing.Kind.RIVER)

    /** The ring's edges from stake to stake ([x0, y0, x1, y1, 1 for a gate people walk through, else 0]). */
    private fun edges(ring: PalisadeRing): List<FloatArray> {
        val doors = ring.gates.filter { it.kind in walked }.map { setOf(it.a, it.b) }.toSet()
        val n = ring.stakes.size
        return (0 until n).map { i ->
            val j = (i + 1) % n
            val a = ring.stakes[i]; val b = ring.stakes[j]
            floatArrayOf(a.x, a.y, b.x, b.y, if (setOf(i, j) in doors) 1f else 0f)
        }
    }

    /** Whether the segments a–b and c–d cross (a touch doesn't). */
    private fun cut(a: FloatArray, b: FloatArray, e: FloatArray): Boolean {
        fun side(ox: Float, oy: Float, px: Float, py: Float, qx: Float, qy: Float) = (px - ox) * (qy - oy) - (py - oy) * (qx - ox)
        val d1 = side(e[0], e[1], e[2], e[3], a[0], a[1]); val d2 = side(e[0], e[1], e[2], e[3], b[0], b[1])
        val d3 = side(a[0], a[1], b[0], b[1], e[0], e[1]); val d4 = side(a[0], a[1], b[0], b[1], e[2], e[3])
        return (d1 > 0 && d2 < 0 || d1 < 0 && d2 > 0) && (d3 > 0 && d4 < 0 || d3 < 0 && d4 > 0)
    }

    /** The first stretch of [way] through the stakes outside a gate (its index), or -1. */
    private fun throughStakes(edges: List<FloatArray>, way: List<FloatArray>): Int =
        (1 until way.size).firstOrNull { i -> edges.any { it[4] == 0f && cut(way[i - 1], way[i], it) } } ?: -1

    /** Whether [way] crosses the ring's line anywhere, its gates too (it leaves the ring or comes into it). */
    private fun crossesRing(edges: List<FloatArray>, way: List<FloatArray>) = (1 until way.size).any { i -> edges.any { cut(way[i - 1], way[i], it) } }

    private fun inside(edges: List<FloatArray>, x: Float, y: Float): Boolean {
        var c = false
        for (e in edges) if ((e[1] > y) != (e[3] > y) && x < (e[2] - e[0]) * (y - e[1]) / (e[3] - e[1]) + e[0]) c = !c
        return c
    }

    // ---------------------------------------------------------------- where people go

    /** A place someone walks to or from: [name], [p] (world cells), whether it's in the woods below ([TownPeople.route]'s). */
    private class Spot(val name: String, val p: FloatArray, val woods: Boolean)

    /** The places of [s]'s village on a [w] × [h] canvas, as TownPeople sends people to them. */
    private fun places(s: GameState, w: Int, h: Int, walk: PalisadeWalk? = Walkways.of(s, w, h).walk): List<Spot> {
        val comp = VillageLayout.composition(w, h, false)
        val land = VillageLayout.of(s)
        val fg = Foreground.of(s, w, h)
        val scale = TownAnchors.FIRE_SCALE[s.age.ordinal]
        val out = ArrayList<Spot>()
        for (b in s.buildings) if (b.type.onPlot && b.plot >= 0) {
            val c = land.centers[b.plot]
            when (b.type) {
                FIELD -> out += Spot("field${b.plot}", floatArrayOf(c[0], c[1] + 0.3f), false)
                HUT, HOUSE, TENT -> for (n in TownPeople.Nook.entries) out += Spot("${b.type}${b.plot}-$n", TownPeople.nookAt(s, land, c, n, walk).let { TownPeople.offFire(it[0], it[1], scale) }, false)
                else -> out += Spot("${b.type}${b.plot}", TownPeople.offFire(c[0] + 2f, c[1] + 0.6f, scale), false)
            }
        }
        for (id in TownAnchors.SPOTS) TownAnchors.visitorSpot(s, TownPlace.Spot(id), comp, w, h)?.let { out += Spot(id, it, id in TownPeople.WOODS && fg != null) }
        TownAnchors.visitorSpot(s, TownPlace.Forest, comp, w, h)?.let { out += Spot("forest", it, true) }
        for (k in 0 until 4) out += Spot("fire$k", TownPeople.aroundFire(k, scale), false)
        TownAnchors.visitorSpot(s, TownPlace.At(PALISADE), comp, w, h)?.let { out += Spot("palisade", it, false) }
        for (m in LandmarkLayout.of(s, w, h)) TownAnchors.landmarkSpot(s, m.landmark.project, w, h)?.takeIf { it.second }?.let { out += Spot("landmark:${m.landmark.project}", it.first, false) }
        // the merchant's cart, where a few gather round it
        val cart = Threats.cart(s, w)
        out += Spot("cart", floatArrayOf(cart[0] + 0.9f, cart[1] + 1.5f), false)
        return out
    }

    private class Village(val name: String, val s: GameState, val w: Int, val h: Int)

    private fun villages(): List<Village> = buildList {
        for ((w, h) in canvases) add(Village("jan", jan, w, h))
        for (age in ages) for ((w, h) in canvases) {
            add(Village("teo", grown(teo, age), w, h))
            add(Village("classic", grown(jan, age), w, h))
        }
        for (l in Landscape.entries.filter { it != Landscape.CLASSIC }) for (seed in listOf(42L, -123456789L)) for (age in ages) for ((w, h) in canvases)
            add(Village("${l.id}:$seed", grown(GameState(seed = seed, age = Age.TABOR, land = Land.of(l, seed)), age), w, h))
    }

    /** Routes between [places] as TownPeople walks them in [s] (the woods by the path, a lane of the road by the seed), up to [most]. */
    private fun routes(v: Village, ways: Walkways.Ways, most: Int): List<Triple<Spot, Spot, List<FloatArray>>> {
        val comp = VillageLayout.composition(v.w, v.h, false)
        val gate = TownAnchors.visitorSpot(v.s, TownPlace.Forest, comp, v.w, v.h)
        val land = VillageLayout.of(v.s)
        val pl = places(v.s, v.w, v.h, ways.walk)
        val pairs = pl.flatMap { a -> pl.filter { it !== a }.map { a to it } }
        val step = (pairs.size / most).coerceAtLeast(1)
        return pairs.filterIndexed { i, _ -> i % step == 0 }.mapIndexed { i, (a, b) ->
            Triple(a, b, TownPeople.route(a.p, b.p, a.woods, b.woods, gate, land, (i % 3) shl 9, ways))
        }
    }

    @Test fun `no walk goes through the stakes, a walk in or out goes through a gate, a walk inside stays inside`() {
        var all = 0; var inOut = 0; var inIn = 0; var woods = 0
        for (v in villages()) {
            val ways = Walkways.of(v.s, v.w, v.h)
            val ring = Palisade.ring(VillageLayout.of(v.s), v.s.age, Foreground.of(v.s, v.w, v.h))
            val edges = edges(ring)
            val most = if (v.name == "jan" || v.name == "teo") 2500 else 300
            for ((a, b, way) in routes(v, ways, most)) {
                val what = "${v.name} ${v.s.age} ${v.w}x${v.h}: ${a.name} → ${b.name}"
                all++
                assertEquals(what, a.p.toList(), way.first().toList()); assertEquals(what, b.p.toList(), way.last().toList())
                val i = throughStakes(edges, way)
                assertEquals("$what through the stakes at ${if (i > 0) "${way[i - 1].toList()} → ${way[i].toList()}" else ""}: ${way.map { it.toList() }}", -1, i)
                val ia = inside(edges, a.p[0], a.p[1]); val ib = inside(edges, b.p[0], b.p[1])
                if (ia && ib) {
                    inIn++
                    assertFalse("$what leaves the ring: ${way.map { it.toList() }}", crossesRing(edges, way))
                }
                if (ia != ib) inOut++
                if (a.woods != b.woods) woods++
                // never a long way round: at most about twice the way out to the gate and on
                val straight = hypot(b.p[0] - a.p[0], b.p[1] - a.p[1])
                assertTrue("$what ${Walkways.length(way)} for $straight", Walkways.length(way) < straight * 2.5f + 30f)
            }
        }
        println("palisade walks: $all walks, $inOut in or out, $inIn inside, $woods into or out of the woods")
        assertTrue("$all walks", all > 50_000)
        assertTrue("$inOut in or out", inOut > 5_000)
    }

    @Test fun `into the woods by the path's gate and down the path`() {
        for (age in ages) for (s in listOf(grown(jan, age), grown(teo, age))) for ((w, h) in canvases.filter { it.second > 320 }) {
            val ways = Walkways.of(s, w, h)
            val ring = Palisade.ring(VillageLayout.of(s), s.age, Foreground.of(s, w, h))
            val path = ring.gates.single { it.kind == PalisadeRing.Kind.PATH }
            val edges = edges(ring)
            val door = edges.indexOfFirst { e -> e[4] == 1f && setOf(floatArrayOf(e[0], e[1]).toList(), floatArrayOf(e[2], e[3]).toList()) == setOf(floatArrayOf(ring.stakes[path.a].x, ring.stakes[path.a].y).toList(), floatArrayOf(ring.stakes[path.b].x, ring.stakes[path.b].y).toList()) }
            assertTrue(door >= 0)
            for ((a, b, way) in routes(Village("woods", s, w, h), ways, 2000)) {
                // between the woods and inside the ring
                if (a.woods == b.woods || !inside(edges, (if (a.woods) b else a).p[0], (if (a.woods) b else a).p[1])) continue
                // in and out only through the path's gate
                val gates = (1 until way.size).flatMap { i -> edges.indices.filter { edges[it][4] == 1f && cut(way[i - 1], way[i], edges[it]) } }
                assertEquals("$age ${a.name} → ${b.name}: through ${gates.map { edges[it].toList() }}", listOf(door), gates)
            }
        }
    }

    @Test fun `without a palisade, or before it stands, the walks are as they were`() {
        for (age in ages) for (base in listOf(jan, teo)) for ((w, h) in canvases) {
            val s = grown(base, age, walled = false)
            val ways = Walkways.of(s, w, h)
            assertNull(ways.walk)
            assertNull(ways.forest)
            val plain = Walkways.Ways(Walkways.blocks(s, w, h))
            val v = Village("open", s, w, h)
            val a = routes(v, ways, 400); val b = routes(v, plain, 400)
            for (k in a.indices) assertEquals("${a[k].first.name} → ${a[k].second.name}", b[k].third.map { it.toList() }, a[k].third.map { it.toList() })
        }
    }

    @Test fun `the water gates and a shore are no way through, every road, path and river gate is one`() {
        var lake = 0
        for (v in villages()) {
            val ring = Palisade.ring(VillageLayout.of(v.s), v.s.age, Foreground.of(v.s, v.w, v.h))
            val walk = PalisadeWalk.of(ring)
            assertEquals("${v.name} ${v.s.age}", ring.gates.count { it.kind in walked }, walk.doors.size)
            if (ring.gates.any { it.kind == PalisadeRing.Kind.SHORE }) lake++
            for (g in ring.gates) {
                val a = ring.stakes[g.a]; val b = ring.stakes[g.b]
                val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
                // straight across the opening's middle from a little inside to a little outside
                val nx = -(b.y - a.y); val ny = b.x - a.x; val l = hypot(nx, ny)
                val p = floatArrayOf(mx + nx / l * 0.3f, my + ny / l * 0.3f); val q = floatArrayOf(mx - nx / l * 0.3f, my - ny / l * 0.3f)
                assertEquals("${v.name} ${v.s.age} ${g.kind}", g.kind !in walked, walk.crosses(p[0], p[1], q[0], q[1]))
            }
            for (d in walk.doors) {
                assertTrue(walk.inside(d.inner[0], d.inner[1])); assertFalse(walk.inside(d.outer[0], d.outer[1]))
                assertFalse(walk.crosses(d.inner[0], d.inner[1], d.outer[0], d.outer[1]))
            }
        }
        assertTrue("$lake rings by a lake's shore", lake > 0)
    }

    @Test fun `a damaged palisade's breach is no way through`() {
        val s = grown(jan, Age.VAS).let { it.copy(buildings = it.buildings.map { b -> if (b.type == PALISADE) b.copy(damaged = true) else b }) }
        val (w, h) = 320 to 200
        val ring = Palisade.ring(VillageLayout.of(s), s.age, null)
        val k = Palisade.breachAt(ring)
        val st = ring.stakes[k]
        val d = hypot(st.x, st.y)
        // from just inside the breach to just outside it: round by a gate
        val a = floatArrayOf(st.x - st.x / d * 1.2f, st.y - st.y / d * 1.2f); val b = floatArrayOf(st.x + st.x / d * 1.2f, st.y + st.y / d * 1.2f)
        val way = Walkways.of(s, w, h).along(listOf(a, b))
        assertEquals(-1, throughStakes(edges(ring), way))
        assertTrue("round by a gate: ${Walkways.length(way)}", Walkways.length(way) > 5f)
    }

    @Test fun `while wolves are at the path's gate, a walk in from the woods goes round by another gate`() {
        val (w, h) = 360 to 800
        for (base in listOf(jan, grown(teo, Age.VAS))) {
            val s = base.copy(event = GameEvent("ev-test", EventKind.WOLVES, 3, 0L, 36 * 3_600_000L))
            val ways = Walkways.of(s, w, h)
            val ring = Palisade.ring(VillageLayout.of(s), s.age, Foreground.of(s, w, h))
            val threat = Threats.of(s, w, h)!!
            val comp = VillageLayout.composition(w, h, false)
            val meadow = TownAnchors.visitorSpot(s, TownPlace.Spot("meadow"), comp, w, h)!!
            // as TownReactions walks them while the animals are about: no forest path, round them
            val way = TownPeople.route(meadow, TownPeople.aroundFire(0, TownAnchors.FIRE_SCALE[s.age.ordinal]), false, false, null, VillageLayout.of(s), 0, ways)
            assertEquals(-1, throughStakes(edges(ring), way))
            val path = ring.gates.single { it.kind == PalisadeRing.Kind.PATH }.let { ring.middle(it) }
            assertTrue("not by the wolves' gate: ${way.map { it.toList() }}", way.none { hypot(it[0] - path[0], it[1] - path[1]) < 1.5f })
            val block = threat.block()
            for (i in 1 until way.size) assertFalse("through the wolves", block.crossed(way[i - 1][0], way[i - 1][1], way[i][0], way[i][1]))
        }
    }

    // ---------------------------------------------------------------- people on the map

    /** Everyone's steps second by second, from [h]:[m] for [seconds]: no step through the stakes. */
    private fun steps(s: GameState, people: List<Villager>, day: si.lanisce.lani.game.villagers.Routine.Day, date: LocalDate, w: Int, h: Int, edges: List<FloatArray>, from: Int, until: Int, every: Int, seconds: Int, wall: (Int) -> Long = { 0L }): Int {
        var moves = 0
        var minute = from
        while (minute < until) {
            var last: Map<String, Placed> = emptyMap()
            for (t in 0 until seconds) {
                val frame = TownPeople.of(s, people, t.toDouble(), (minute + t / 60f) / 60f, date.monthValue, w, h, date = date, day = day, wall = wall(minute * 60 + t))
                for (p in frame) {
                    val was = last[p.id] ?: continue
                    if (was.x == p.x && was.y == p.y) continue
                    moves++
                    val e = edges.firstOrNull { it[4] == 0f && cut(floatArrayOf(was.x, was.y), floatArrayOf(p.x, p.y), it) }
                    assertNull("${s.age} $date ${minute / 60}:${minute % 60}+${t}s: ${p.id} (${p.activity}, to ${p.place}) steps through the stakes from ${was.x}, ${was.y} to ${p.x}, ${p.y}", e)
                }
                last = frame.associateBy { it.id }
            }
            minute += every
        }
        return moves
    }

    @Test fun `nobody on the map steps through the stakes, through a day and while wolves are about`() {
        var moves = 0
        for ((culture, base) in listOf("primorska" to jan, "friuli" to grown(teo, Age.ZASELEK), "friuli" to grown(teo, Age.TRG))) {
            val cast = cast(culture)
            val scenes = scenes(culture)
            val s = base.copy(residents = cast.map { Resident(it.id, "2026-01-01") })
            val date = LocalDate.of(2026, 9, 2)
            val people = cast + Residents.people(s, cast, date).filter { p -> cast.none { it.id == p.id } }
            val day = Whereabouts.day(scenes, s, cast, people, date)
            for ((w, h) in listOf(360 to 800, 320 to 200)) {
                val edges = edges(Palisade.ring(VillageLayout.of(s), s.age, Foreground.of(s, w, h)))
                moves += steps(s, people, day, date, w, h, edges, 6 * 60 + 5, 21 * 60, 37, 60)
                // the wolves come at nine: everyone goes where it wants them, the shepherd in from the meadow
                val t0 = date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val wolves = s.copy(event = GameEvent("ev-test", EventKind.WOLVES, 3, t0, t0 + 36 * 3_600_000L))
                moves += steps(wolves, people, day, date, w, h, edges, 9 * 60, 9 * 60 + 1, 1, 50) { t0 + (it - 9 * 3600) * 1000L }
            }
        }
        assertTrue("$moves steps", moves > 2000)
    }

    // ---------------------------------------------------------------- cost

    @Test fun `the ring's ways are cheap, worked out once, a walk in a few hundred microseconds`() {
        // a town of its own land (nothing else draws its ring), on Teo's tablet upright
        val s = grown(GameState(seed = 4242L, land = Land.of(Landscape.HILLS, 4242L)), Age.MESTO)
        val (w, h) = 320 to 512
        repeat(3) { PalisadeWalk.of(Palisade.ring(VillageLayout.of(teo), Age.MESTO, null)) }
        val ring = Palisade.ring(VillageLayout.of(s), s.age, Foreground.of(s, w, h))
        val t0 = System.nanoTime()
        val walk = PalisadeWalk.of(ring)
        val once = (System.nanoTime() - t0) / 1e6
        assertTrue("the ring once $once ms", once < FrameBudget.scaled(20.0))
        // a crossing test over the whole clearing
        val t1 = System.nanoTime()
        var n = 0
        for (i in 0 until 20_000) if (walk.crosses(-20f + (i % 40), -20f, 20f - (i % 37), 20f)) n++
        val cross = (System.nanoTime() - t1) / 1e3 / 20_000
        // every walk of the town, fresh (the ways' memory left out)
        val ways = Walkways.Ways(Walkways.blocks(s, w, h), walk, null)
        val t2 = System.nanoTime()
        val r = routes(Village("cost", s, w, h), ways, 3000)
        val each = (System.nanoTime() - t2) / 1e3 / r.size
        println("palisade walks: the ring once ${"%.2f".format(once)} ms, a crossing test ${"%.2f".format(cross)} µs, a walk ${"%.0f".format(each)} µs ($n)")
        assertTrue("a crossing test ${cross} µs", cross < FrameBudget.scaled(20.0))
        assertTrue("a walk $each µs", each < FrameBudget.scaled(3000.0))
    }

    // ---------------------------------------------------------------- pictures

    private fun write(c: PixelCanvas, name: String, scale: Int, comp: Composition, lines: List<Pair<List<FloatArray>, Int>> = emptyList(), marks: List<Pair<FloatArray, Int>> = emptyList(), crop: Boolean = true) {
        val dir = File("build/village-snapshots/palisade-walks").apply { mkdirs() }
        // the clearing and the woods' meadow below it
        val y0 = if (crop) (comp.horizon - 12).coerceAtLeast(0) else 0
        val y1 = if (crop) (comp.fireY + 150).coerceAtMost(c.height) else c.height
        val img = BufferedImage(c.width * scale, (y1 - y0) * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until (y1 - y0) * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y0 + y / scale) * c.width + x / scale])
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        fun sx(p: FloatArray) = ((comp.fireX + (p[0] - p[1]) * 4f) * scale).toInt()
        fun sy(p: FloatArray) = ((comp.fireY - y0 + (p[0] + p[1]) * 2f) * scale).toInt()
        g.stroke = BasicStroke(1.3f)
        for ((way, col) in lines) {
            g.color = Color(col, true)
            for (i in 1 until way.size) g.drawLine(sx(way[i - 1]), sy(way[i - 1]), sx(way[i]), sy(way[i]))
        }
        for ((p, col) in marks) { g.color = Color(col, true); g.fillOval(sx(p) - 4, sy(p) - 4, 8, 8) }
        g.dispose()
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    /**
     * Every walk between the village's places drawn over it, before (the ways without the ring: [Walkways.Ways] of the blocks
     * alone, as the walks were) and after: red where a walk goes through the stakes, green where it doesn't; the gates
     * people walk through in cyan, the closed ones in magenta. build/village-snapshots/palisade-walks/{before,after}-….
     */
    @Test fun `render the walks before and after`() {
        val r = VillageRenderer()
        val picks = listOf(
            Village("jan", jan, 360, 800), Village("jan", jan, 320, 200),
            Village("classic", grown(jan, Age.MESTO), 360, 800),
            Village("lake", grown(GameState(seed = 42, land = Land.of(Landscape.LAKE, 42L)), Age.TRG), 360, 800),
            Village("mountains", grown(GameState(seed = -123456789L, land = Land.of(Landscape.MOUNTAINS, -123456789L)), Age.VAS), 360, 800),
        ) + ages.flatMap { age -> listOf(Village("teo", grown(teo, age), 320, 512), Village("teo", grown(teo, age), 320, 200)) }
        for (v in picks) {
            val ring = Palisade.ring(VillageLayout.of(v.s), v.s.age, Foreground.of(v.s, v.w, v.h))
            val edges = edges(ring)
            val comp = VillageLayout.composition(v.w, v.h, false)
            val c = PixelCanvas(v.w, v.h)
            r.render(c, v.s, Frame(time = 3.0, hour = 11f, month = 6))
            val marks = ring.gates.map { ring.middle(it) to if (it.kind in walked) 0xFF00E5FF.toInt() else 0xFFFF00FF.toInt() } + places(v.s, v.w, v.h).map { it.p to 0xFFFFFFFF.toInt() }
            for ((when_, ways) in listOf("before" to Walkways.Ways(Walkways.blocks(v.s, v.w, v.h)), "after" to Walkways.of(v.s, v.w, v.h))) {
                var bad = 0
                val lines = routes(v, ways, 900).map { (_, _, way) ->
                    val x = throughStakes(edges, way) >= 0
                    if (x) bad++
                    way to if (x) 0xD0FF2020.toInt() else 0x6020E040
                }
                println("palisade walks: $when_ ${v.name} ${v.s.age} ${v.w}x${v.h}: $bad of ${lines.size} through the stakes")
                write(c, "$when_-${v.name.substringBefore(':')}-${v.s.age.name.lowercase()}-${v.w}x${v.h}", 3, comp, lines.sortedBy { it.second ushr 24 }, marks)
            }
        }
    }

    /**
     * Someone walking out of the village into the woods, frame by frame, closer up (k = 2) where the walk meets the ring:
     * before through the stakes, now through the path's gate. build/village-snapshots/palisade-walks/strip-….
     */
    @Test fun `render a strip of someone going out through a gate`() {
        val fit = SceneFit.town(1080, 2400)
        val rig = CameraRig.town(fit, 1080, 2400)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val s = jan
        val v = cast("primorska").first { it.id == "luka" }
        val r = VillageRenderer()
        fun lens(k: Int, x: Float, y: Float, zoom: Float) = Lens.of(k, rig.lookAt(comp.fireX + (x - y) * 4f, comp.fireY + (x + y) * 2f - 6f, rig.minScale * zoom), 1080, 2400, fit.width, fit.height, fit.width, fit.height)
        fun strip(name: String, way: List<FloatArray>, from: Float, to: Float, frames: Int, k: Int, zoom: Float, at: FloatArray) {
            val len = Walkways.length(way)
            val l = lens(k, at[0], at[1], zoom)
            val pics = (0 until frames).map { f ->
                val d = (from + (to - from) * f / (frames - 1)).coerceIn(0f, len)
                val (p, dir) = TownPeople.along(way, d)
                val c = PixelCanvas(fit.width, fit.height)
                r.render(c, s, Frame(time = 3.0 + f * 0.4, hour = 11f, month = 6, lens = l, placed = listOf(Placed(v, p[0], p[1], Activity.WALK, dir < 0f, TownPlace.Spot("meadow")))))
                c
            }
            // the frames side by side, the part of the screen round the gate, four times as big, a dark line between them
            val cw = 110; val ch = 90; val z = 4
            val img = BufferedImage((cw * z + 4) * frames, ch * z, BufferedImage.TYPE_INT_RGB)
            for ((f, c) in pics.withIndex()) {
                val ox = (fit.width - cw) / 2; val oy = (fit.height - ch) / 2 - 8
                for (y in 0 until ch * z) for (x in 0 until cw * z) img.setRGB(f * (cw * z + 4) + x, y, c.pixels[(oy + y / z) * c.width + ox + x / z])
            }
            File("build/village-snapshots/palisade-walks").mkdirs()
            ImageIO.write(img, "png", File("build/village-snapshots/palisade-walks/$name.png"))
        }
        val gate = TownAnchors.visitorSpot(s, TownPlace.Forest, comp, fit.width, fit.height)!!
        val land = VillageLayout.of(s)
        val house = land.centers[s.buildings.first { it.type == HOUSE }.plot].let { floatArrayOf(it[0] + 2f, it[1] + 0.6f) }
        val meadow = TownAnchors.visitorSpot(s, TownPlace.Spot("meadow"), comp, fit.width, fit.height)!!
        val ring = Palisade.ring(land, s.age, Foreground.of(s, fit.width, fit.height))
        val edges = edges(ring)
        for ((when_, ways) in listOf("before" to Walkways.Ways(Walkways.blocks(s, fit.width, fit.height)), "after" to Walkways.of(s, fit.width, fit.height))) {
            val way = TownPeople.route(house, meadow, false, true, gate, land, 0, ways)
            // where it meets the ring: through the stakes before, through the path's gate now
            var d = 0f; var meet = Walkways.length(way) / 2f; var at = way.first()
            for (i in 1 until way.size) {
                val seg = hypot(way[i][0] - way[i - 1][0], way[i][1] - way[i - 1][1])
                val e = edges.firstOrNull { cut(way[i - 1], way[i], it) }
                if (e != null) { at = floatArrayOf((e[0] + e[2]) / 2f, (e[1] + e[3]) / 2f); meet = d + hypot(at[0] - way[i - 1][0], at[1] - way[i - 1][1]); break }
                d += seg
            }
            strip("strip-$when_-into-the-woods", way, meet - 3f, meet + 3f, 6, 2, 2.4f, at)
        }
    }
}
