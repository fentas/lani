package si.lanisce.lani.game.render

import si.lanisce.lani.FrameBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.scene.TownPlace
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Generated lands (see [Terrain]) keep what the game needs, on every seed of every landscape: the plots on open ground
 * off the stream and the road, on every canvas and nearest the fire first; the palisade's gates on the road and the path,
 * no stake in the water; the named spots on the canvas, in the woods or the clearing as they should be, and a tap on each
 * one's body hits it; every project's landmark placed; the wild animals outside the palisade; no tall building hiding a
 * small one behind it. The same seed always gives the same land.
 */
class LandsTest {
    /** The generated landscapes. */
    private val landscapes = Landscape.entries.filter { it != Landscape.CLASSIC }

    private val seeds = listOf(1L, 2L, 3L, 5L, 7L, 11L, 13L, 42L, 99L, 123L, 256L, 777L, 1000L, 2024L, 31337L, 8993091993378563000L, -5L, -123456789L)

    /** A few seeds per landscape for the checks that render. */
    private val few = listOf(3L, 42L, 777L, -123456789L)

    private fun land(l: Landscape, seed: Long): Terrain = Terrain.of(Land.of(l, seed))

    private fun all(pick: List<Long> = seeds): List<Pair<String, Terrain>> =
        landscapes.flatMap { l -> pick.map { "${l.id}:$it" to land(l, it) } } + ("classic" to Terrain.CLASSIC)

    private fun state(t: Terrain, age: Age, types: List<BuildingType> = emptyList(), seed: Long = 42) = GameState(
        seed = seed, age = age, villagers = 8, morale = 70, fire = 70, land = t.land,
        buildings = types.take(PLOTS_PER_AGE[age.ordinal]).mapIndexed { i, ty -> Building("b$i", ty, if (ty.onPlot) i else NO_PLOT, level = if (i % 4 == 3) 2 else 1) },
    )

    private val hamlet = listOf(FIELD, HUT, WELL, KOZOLEC, TENT, PALISADE, HUT, FIELD, LIPA)
    private val village = listOf(FIELD, HUT, WELL, KOZOLEC, HOUSE, PALISADE, LIPA, CHURCH, BEEHIVE, SMITHY, HOUSE, WATCHTOWER, FIELD, HOUSE)
    private val town = village + listOf(MARKET, HOUSE, SCHOOL, KOZOLEC, HOUSE, BEEHIVE, HOUSE, WELL, HOUSE, MARKET, HOUSE, HUT, HOUSE, WELL)

    private fun types(age: Age) = when (age) {
        Age.OGENJ, Age.TABOR -> listOf(TENT, TENT)
        Age.ZASELEK -> hamlet
        Age.VAS -> village
        else -> town
    }

    /** The canvases of the full-screen town: a phone upright, a tall one, a tablet, a phone on its side. */
    private val canvases = listOf(360 to 800, 360 to 780, 320 to 512, 400 to 180, 360 to 480)

    // ---------------------------------------------------------------- the land itself

    @Test fun `the same seed gives the same land, another seed another`() {
        for (l in landscapes) {
            val a = Terrain.of(Land.of(l, 777L)); val b = Terrain.of(Land.of(l, 777L))
            assertEquals(a.centers.map { it.toList() }, b.centers.map { it.toList() })
            // not the cache: the land is made the same way again
            val c = Terrain.of(Land(l.id, Land.of(l, 777L).seed))
            assertEquals(a.centers.map { it.toList() }, c.centers.map { it.toList() })
            assertEquals(a.streamX(3f), c.streamX(3f))
            // a reroll (another village seed) moves the stream, the plots and the clearing's edge
            val d = Terrain.of(Land.of(l, 778L))
            assertNotEquals(a.centers.map { it.toList() }, d.centers.map { it.toList() })
            assertNotEquals(a.streamX(3f), d.streamX(3f))
            assertNotEquals(a.forestEdge(Age.VAS, 1f), d.forestEdge(Age.VAS, 1f))
        }
        // the classic land doesn't depend on the seed
        assertEquals(Terrain.CLASSIC, Terrain.of(Land.CLASSIC))
        assertEquals(Terrain.CLASSIC, Terrain.of(Land("classic", 99)))
        assertEquals(Terrain.CLASSIC, VillageLayout.of(GameState(seed = 5)))
        // a landscape this app doesn't know is drawn as the classic valley
        assertEquals(Terrain.CLASSIC, Terrain.of(Land("volcano", 3)))
        assertEquals(Land(Landscape.VALLEY.id, Land.of(Landscape.VALLEY, 12L).seed), GameEngine.newGame(12L, 0L).land)
    }

    /** Any seed makes a land: its plots, its spots on every canvas at every age (a quick look at a thousand seeds). */
    @Test fun `a thousand seeds make a thousand lands`() {
        val rnd = java.util.Random(4242)
        for (l in landscapes) repeat(1000) {
            val seed = rnd.nextLong()
            val t = land(l, seed)
            assertEquals("${l.id}:$seed", VillageLayout.MAX_PLOTS, t.centers.size)
            for (age in listOf(Age.OGENJ, Age.MESTO)) {
                val s = state(t, age)
                for ((w, h) in listOf(400 to 180, 320 to 512)) for (id in listOf("rocks", "riverbank", "road", "woodpile")) {
                    if (id == "woodpile" && Foreground.of(age, w, h, t) != null) continue
                    val p = TownAnchors.spotAnchor(s, w, h, id)
                    assertNotNull("${l.id}:$seed $id at $age on ${w}x$h", p)
                    assertTrue("${l.id}:$seed $id at $age on ${w}x$h in the woods", !TownAnchors.isForest(s, w, h, p!![0].toFloat(), p[1].toFloat()))
                }
            }
        }
    }

    @Test fun `the plots lie on open ground, off the stream and the road, on every canvas, nearest the fire first`() {
        for ((name, t) in all()) {
            val cs = t.centers
            assertEquals(name, VillageLayout.MAX_PLOTS, cs.size)
            for (i in cs.indices) for (j in i + 1 until cs.size) {
                assertTrue("$name: plots $i and $j overlap", abs(cs[i][0] - cs[j][0]) >= 3.5f || abs(cs[i][1] - cs[j][1]) >= 3.5f)
            }
            for ((i, c) in cs.withIndex()) {
                assertTrue("$name: plot $i in the stream", abs(c[0] - t.streamX(c[1])) >= 3.3f)
                assertTrue("$name: plot $i on the stream bank's spot", abs(c[0] - t.riverbank[0]) >= 2.6f || abs(c[1] - t.riverbank[1]) >= 2.6f)
                assertTrue("$name: plot $i on the road", abs(c[1] - t.roadY(c[0])) - 1.5f >= 1f)
                assertTrue("$name: plot $i by the fire", abs(c[0]) >= 3.7f || abs(c[1]) >= 3.7f)
                val sx = (c[0] - c[1]) * 4f; val sy = (c[0] + c[1]) * 2f
                assertTrue("$name: plot $i off the town's canvases at ($sx, $sy)", sx in -124f..124f && sy in -48f..56f)
            }
            // the camp's first plots by the fire, and outward from there
            assertTrue("$name: plot 0 at ${cs[0][2]}, plot 1 at ${cs[1][2]}", cs[0][2] < 46f && cs[1][2] < 50f)
            assertTrue("$name: outward", cs[0][2] <= cs[10][2] && cs[10][2] <= cs[27][2])
            for (a in 1 until Age.entries.size) assertTrue(name, t.clearing(Age.entries[a]) > t.clearing(Age.entries[a - 1]))
            // the road's bridge over the stream, the stream's bank above it by the water
            assertEquals(name, t.bridge[0], t.streamX(t.bridge[1]), 0.02f)
            assertEquals(name, t.bridge[1], t.roadY(t.bridge[0]), 0.02f)
            assertEquals(name, 2.4f, t.riverbank[0] - t.streamX(t.riverbank[1]), 0.001f)
        }
    }

    // ---------------------------------------------------------------- the palisade

    @Test fun `the palisade opens on the road and the path, keeps off the water, and has a way down to the stream`() {
        for ((name, t) in all()) for (age in Age.entries.filter { it >= Age.TABOR }) for (fg in listOf(null, Foreground.of(age, 360, 800, t))) {
            val ring = Palisade.ring(t, age, fg)
            val tag = "$name $age ${if (fg == null) "short" else "tall"}"
            val half = if (age >= Age.VAS) 1f else 0.55f
            val g = ring.gatePoint()
            assertNotNull(tag, ring.main)
            assertTrue("$tag: the main gate at ${g.toList()}", abs(g[1] - t.roadY(g[0])) < half && g[0] > 5f)
            assertEquals(tag, if (age >= Age.ZASELEK) 2 else 1, ring.gates.count { it.kind == PalisadeRing.Kind.ROAD })
            val paths = ring.gates.filter { it.kind == PalisadeRing.Kind.PATH }
            if (fg == null) assertTrue(tag, paths.isEmpty()) else {
                val p = ring.middle(paths.single())
                assertTrue("$tag: the path's gate", fg.onPath(fg.comp.fireX + (p[0] - p[1]) * 4f, fg.comp.fireY + (p[0] + p[1]) * 2f, slack = 1f))
            }
            for (gate in ring.gates) assertTrue(tag, ring.stakes[gate.a].post && ring.stakes[gate.b].post)
            val plots = (0 until PLOTS_PER_AGE[age.ordinal]).maxOf { t.centers[it][2] }
            val gates = ring.gates.map { setOf(it.a, it.b) }
            val n = ring.stakes.size
            for ((k, st) in ring.stakes.withIndex()) {
                assertTrue("$tag: a stake in the stream at ${st.x}, ${st.y}", abs(st.x - t.streamX(st.y)) > 1.5f)
                if (st.x > 0f || age >= Age.ZASELEK) assertTrue("$tag: a stake on the road at ${st.x}, ${st.y}", abs(st.y - t.roadY(st.x)) > half + 0.4f)
                if (fg != null) assertTrue("$tag: a stake on the path", !fg.onPath(fg.comp.fireX + (st.x - st.y) * 4f, fg.comp.fireY + (st.x + st.y) * 2f, slack = 1f))
                val m = VillageLayout.screenMetric((st.x - st.y) * 4f, (st.x + st.y) * 2f)
                // beyond the plots (along the stream the ring may keep to its bank, by the water to the shore, closer in), never on one
                if (st.x - t.streamX(st.y) > 4f && !t.wet(st.x, st.y, 14f)) assertTrue("$tag: a stake at metric $m among the plots (out to $plots)", m > plots + 8f)
                for (p in 0 until PLOTS_PER_AGE[age.ordinal]) { val c = t.centers[p]; assertTrue("$tag: a stake on plot $p", abs(st.x - c[0]) > 1.8f || abs(st.y - c[1]) > 1.8f) }
                // no hole but the gates
                val b = ring.stakes[(k + 1) % n]
                if (setOf(k, (k + 1) % n) !in gates) assertTrue("$tag: a hole after stake $k", hypot(st.x - b.x, st.y - b.y) <= Palisade.STEP * 1.2f)
            }
            // a way down to the stream, its landing on no plot
            val way = Palisade.footpath(ring)
            assertNotNull("$tag: a way down to the stream", way)
            val (l, top) = way!!
            assertEquals(tag, 1.05f, l[0] - t.streamX(l[1]), 0.001f)
            for (c in t.centers) assertTrue("$tag: the landing on a plot", abs(l[1] - c[1]) > 1.5f || top[0] + 1.2f < c[0] - 1.5f || l[0] - 0.6f > c[0] + 1.5f)
        }
    }

    // ---------------------------------------------------------------- the woods below and the spots

    @Test fun `the woods below keep their things apart, on the canvas`() {
        for ((name, t) in all()) for (age in Age.entries) for ((w, h) in listOf(360 to 800, 360 to 780, 320 to 512)) {
            val fg = Foreground.of(age, w, h, t) ?: continue
            val tag = "$name $age ${w}x$h"
            val comp = VillageLayout.composition(w, h, false)
            assertTrue(tag, fg.edgeY > comp.fireY + 20f && fg.pathTop < fg.edgeY)
            for (y in listOf(fg.meadowY, fg.pondY, fg.stackY, fg.seatY)) assertTrue("$tag: $y", y > fg.edgeY && y < h - 8f)
            for (x in listOf(fg.meadowX, fg.pondX, fg.stackX, fg.seatX)) assertTrue("$tag: $x", x > 8f && x < w - 8f)
            assertTrue(tag, fg.meadowD(fg.meadowX, fg.meadowY) < 0.5f)
            assertTrue(tag, fg.pondD(fg.pondX, fg.pondY) < 0.5f)
            assertTrue(tag, fg.onPath(fg.pathX(fg.meadowY), fg.meadowY))
            // the pond, the stack, the high seat and the campsite off the path
            assertTrue("$tag: the pond on the path", fg.pondX + fg.pondRx < fg.pathX(fg.pondY) - fg.pathHalf - 8f)
            assertTrue("$tag: the stack on the path", abs(fg.stackX - fg.pathX(fg.stackY)) > fg.pathHalf + 10f)
            assertTrue("$tag: the high seat in the meadow", fg.seatX - 6f > fg.meadowX + fg.meadowRx * 0.9f)
            val camp = Campsite(fg)
            assertTrue("$tag: the campsite on the path", camp.x + 10f < fg.pathX(camp.y) - fg.pathHalf)
            if (fg.kopa) assertTrue("$tag: the charcoal pile on the path", fg.kopaX - 14f > fg.pathX(fg.kopaY) + fg.pathHalf)
            val s = state(t, age)
            assertTrue("$tag: the meadow is forest for taps", TownAnchors.isForest(s, w, h, fg.meadowX, fg.meadowY))
        }
    }

    @Test fun `every named spot resolves on the canvas, in the woods or in the clearing as it should`() {
        val woodsOnly = setOf("meadow", "pond", "highseat", "path", "kopa")
        for ((name, t) in all()) for (age in Age.entries) {
            val s = state(t, age)
            for ((w, h) in canvases) {
                val comp = VillageLayout.composition(w, h, false)
                val fg = Foreground.of(age, w, h, t)
                for (id in TownAnchors.SPOTS) {
                    val p = TownAnchors.spotAnchor(s, w, h, id)
                    val tag = "$name: $id at $age on $w×$h"
                    if (fg == null && id in woodsOnly) { assertNull(tag, p); continue }
                    if (id == "kopa" && !fg!!.kopa) { assertNull(tag, p); continue }
                    assertNotNull(tag, p)
                    val x = p!![0].toFloat(); val y = p[1].toFloat()
                    assertTrue("$tag: ($x, $y)", x in 4f..(w - 4f) && y in (comp.horizon + 4f)..(h - 4f))
                    assertEquals(tag, id in woodsOnly || id == "woodpile" && fg != null, TownAnchors.isForest(s, w, h, x, y))
                }
                // the places' anchors on the canvas
                for ((place, pt) in TownAnchors.withSpots(s, w, h)) assertTrue("$name $place at $pt on $w×$h", pt.x in 0f..w.toFloat() && pt.y in 0f..h.toFloat())
            }
        }
    }

    @Test fun `a tap on each place's body hits it, on every landscape`() {
        for ((name, t) in all(few)) for (age in listOf(Age.TABOR, Age.VAS, Age.MESTO)) for ((w, h) in listOf(360 to 800, 400 to 180)) {
            val s = state(t, age, types(age))
            val r = VillageRenderer()
            r.render(PixelCanvas(w, h), s, Frame(time = 2.0, hour = 11f, month = 6))
            val bodies = TownAnchors.bodies(s, w, h)
            val tag = "$name $age ${w}x$h"
            var hit = 0; var buildings = 0
            val fg = Foreground.of(s, w, h)
            for ((id, p) in bodies) {
                val got = r.hitTest(p.x.toInt(), p.y.toInt(), 2)
                when {
                    id == "fire" -> assertEquals(tag, VillageHit.OnFire, got)
                    // the meadow and the pond are tapped on their things (a berry bush, the water), as in the classic land
                    id == "spot:meadow" -> Wildlife.berryBushes(fg!!).first().let { b -> assertEquals("$tag: the meadow's bush", VillageHit.OnSpot("meadow"), r.hitTest(b[0].toInt(), b[1].toInt() - 2, 2)) }
                    id == "spot:pond" -> assertEquals("$tag: the pond", VillageHit.OnSpot("pond"), r.hitTest(fg!!.pondX.toInt(), fg.pondY.toInt(), 0))
                    // the rest by their things: the rocks and the small woodpile are clusters with gaps (as in the classic land),
                    // tapped on a rock or a log by their middle, unless a building of the town stands in front of them
                    id.startsWith("spot:") -> {
                        val spot = VillageHit.OnSpot(id.removePrefix("spot:"))
                        assertTrue(
                            "$tag: $id at $p: $got",
                            got == spot || got is VillageHit.OnBuilding && id == "spot:rocks" ||
                                (-6..6).any { dy -> (-6..6).any { dx -> r.hitTest(p.x.toInt() + dx, p.y.toInt() + dy, 0) == spot } },
                        )
                    }
                    id == "forest" -> assertTrue("$tag: the forest at $p", TownAnchors.isForest(s, w, h, p.x, p.y))
                    else -> { buildings++; if ((got as? VillageHit.OnBuilding)?.building?.id == id) hit++ }
                }
            }
            // a few hide behind taller ones
            assertTrue("$tag: $hit of $buildings buildings hit at their body", hit >= buildings - 2)
        }
    }

    // ---------------------------------------------------------------- what stands on the land

    @Test fun `every project's landmark finds a place on every land`() {
        for ((name, t) in all(few)) for (age in listOf(Age.ZASELEK, Age.VAS, Age.TRG, Age.MESTO)) for ((w, h) in listOf(360 to 800, 400 to 180)) {
            val s = state(t, age, types(age)).copy(projects = Catalog.projects.filter { it.age <= age }.associate { it.id to 1 })
            val spots = LandmarkLayout.of(s, w, h)
            val tag = "$name $age ${w}x$h"
            assertEquals("$tag: every landmark placed", s.projects.size, spots.size)
            for (sp in spots) {
                val x0 = sp.x; val y0 = sp.y; val x1 = sp.x + sp.w; val y1 = sp.y + sp.d
                for (p in 0 until PLOTS_PER_AGE[age.ordinal]) {
                    val c = t.centers[p]
                    assertTrue("$tag: ${sp.landmark.id} on plot $p", !(x1 > c[0] - 1.5f && x0 < c[0] + 1.5f && y1 > c[1] - 1.5f && y0 < c[1] + 1.5f))
                }
                if (sp.landmark.id == "most") assertTrue("$tag: the footbridge crosses the stream", t.streamX((y0 + y1) / 2f) in x0..x1)
                if (sp.landmark.id == "mlin") assertTrue("$tag: the mill at the water", x0 - t.streamX((y0 + y1) / 2f) in 1f..2.2f)
            }
        }
    }

    @Test fun `the wild animals keep outside the palisade on every land`() {
        var seen = 0
        for ((name, t) in all(few)) for (age in listOf(Age.ZASELEK, Age.VAS)) for ((w, h) in listOf(360 to 800, 400 to 180)) {
            val s = state(t, age, types(age))
            val comp = VillageLayout.composition(w, h, false)
            for (d in 0 until 4) for ((hour, month) in listOf(12f to 6, 19f to 9, 23f to 9)) {
                val r = VillageRenderer()
                r.render(PixelCanvas(w, h), s, Frame(time = 3.0, hour = hour, month = month, today = LocalDate.of(2026, 9, 1).plusDays(d * 5L).toString()))
                for (a in r.wildlife) {
                    if (a.kind.aloft) continue
                    seen++
                    val dx = a.at.x - comp.fireX; val dy = a.at.y - comp.fireY
                    val edge = t.forestEdge(age, VillageLayout.angle(dx, dy))
                    assertTrue("$name ${a.kind} at ${a.at} inside", VillageLayout.screenMetric(dx, dy) >= edge - 0.5f)
                    val wx = (dx / 4f + dy / 2f) / 2f; val wy = (dy / 2f - dx / 4f) / 2f
                    assertTrue("$name ${a.kind} in the stream", abs(wx - t.streamX(wy)) > 1.2f)
                }
            }
        }
        assertTrue("$seen on the ground", seen > 50)
    }

    /** The first pixels of each building type drawn alone, relative to its plot's middle on the ground (nominal px). */
    private val silhouettes: Map<BuildingType, Set<Long>> by lazy {
        BuildingType.entries.filter { it.onPlot }.associateWith { ty ->
            val w = 480; val h = 300
            val r = VillageRenderer()
            val s = GameState(seed = 42, age = Age.MESTO, villagers = 0, buildings = listOf(Building("b", ty, 0)))
            r.render(PixelCanvas(w, h), s, Frame(time = 2.0, hour = 12f, month = 6))
            val comp = VillageLayout.composition(w, h, false)
            val c = Terrain.CLASSIC.centers[0]
            val ox = (comp.fireX + (c[0] - c[1]) * 4f).roundToInt(); val oy = (comp.fireY + (c[0] + c[1]) * 2f).roundToInt()
            val out = HashSet<Long>()
            for (y in 0 until h) for (x in 0 until w) if (r.hitTest(x, y, 0) is VillageHit.OnBuilding) out += key(x - ox, y - oy)
            out
        }
    }

    private fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)

    @Test fun `a tall building in front never hides most of a small one behind, on every land`() {
        val fronts = listOf(HOUSE, KOZOLEC, SMITHY, SCHOOL, HUT, CHURCH)
        val backs = listOf(BEEHIVE, TENT, WELL, MARKET, HUT)
        for ((name, t) in all()) {
            val cs = t.centers
            fun at(c: FloatArray) = intArrayOf(((c[0] - c[1]) * 4f).roundToInt(), ((c[0] + c[1]) * 2f).roundToInt())
            var worst = 0f; var what = ""
            for (i in cs.indices) for (j in cs.indices) {
                if (cs[j][0] + cs[j][1] <= cs[i][0] + cs[i][1]) continue
                val bi = at(cs[i]); val bj = at(cs[j])
                if (abs(bi[0] - bj[0]) > 40 || abs(bi[1] - bj[1]) > 60) continue
                for (f in fronts) {
                    val front = silhouettes.getValue(f)
                    for (b in backs) {
                        val back = silhouettes.getValue(b)
                        var hidden = 0
                        for (p in back) if (key((p shr 32).toInt() + bi[0] - bj[0], p.toInt() + bi[1] - bj[1]) in front) hidden++
                        val share = hidden.toFloat() / back.size
                        if (share > worst) { worst = share; what = "$f on plot $j over $b on plot $i" }
                    }
                }
            }
            assertTrue("$name: $what: ${(worst * 100).roundToInt()} % hidden", worst < 0.5f)
        }
    }

    /** The water, the rises and the vineyards cost little: a town of every landscape within the renderer's budget. */
    @Test fun `every landscape renders within the frame budget`() {
        val fit = SceneFit.town(1080, 2400)
        val c = PixelCanvas(fit.width, fit.height)
        for (l in listOf(Landscape.CLASSIC) + landscapes) {
            val t = if (l == Landscape.CLASSIC) Terrain.CLASSIC else land(l, 42L)
            val s = state(t, Age.MESTO, types(Age.MESTO))
            val r = VillageRenderer()
            repeat(8) { r.render(c, s, Frame(time = it / 12.0, hour = 22f, month = 9)) }
            val n = 30
            val t0 = System.nanoTime()
            repeat(n) { r.render(c, s, Frame(time = 2 + it / 12.0, hour = 22f, month = 9)) }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            println("${l.id}: %.2f ms/frame (town, night, %dx%d)".format(ms, fit.width, fit.height))
            assertTrue("${l.id} too slow: $ms ms", ms < FrameBudget.scaled(50.0))
        }
    }

    @Test fun `the forest spot and the visitors stand where they should on every land`() {
        for ((name, t) in all()) for (age in listOf(Age.OGENJ, Age.VAS, Age.MESTO)) for ((w, h) in listOf(360 to 800, 400 to 180)) {
            val s = state(t, age, types(age))
            val a = TownAnchors.of(s, w, h)
            val f = a.getValue(TownPlace.Forest)
            assertTrue("$name $age ${w}x$h: the forest spot $f", TownAnchors.isForest(s, w, h, f.x, f.y))
            val home = TownAnchors.home(s, w, h)
            for ((p, pt) in a) if (p is TownPlace.At || p == TownPlace.Fire) assertTrue("$name $age: $p at $pt outside $home", pt.x in home.left..home.right && pt.y in home.top..home.bottom)
        }
    }
}
