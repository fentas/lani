package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
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
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.PLOTS_PER_AGE
import java.io.File
import kotlin.math.abs

/**
 * The bridges on the map (Jan, 2026-10-18: "the bridge seems not to be drivable ;)", "it goes right into a wall and starts
 * from nowhere"): a bridge stands where a way crosses the stream, its deck open at both ends and the way going on at both,
 * never into the palisade, a building or nothing. The cart bridge (the project "most", [LandmarkLayout]) carries its own
 * track: from the road on the village's bank over the stream and on into the woods, through a gate of its own in the
 * palisade, at every stage of the project, on every land and at every age; the road's bridge carries the road. Snapshots
 * of both, every stage, by day, by night and in winter, for review (build/village-snapshots/63-bridge-…).
 */
class BridgesTest {
    private val dir = File("build/village-snapshots").apply { mkdirs() }

    private val seeds = listOf(1L, 2L, 3L, 5L, 7L, 11L, 13L, 42L, 99L, 123L, 256L, 777L, 1000L, 2024L, 31337L, 8993091993378563000L, -5L, -123456789L)

    private fun lands(): List<Pair<String, Terrain>> =
        Landscape.entries.filter { it != Landscape.CLASSIC }.flatMap { l -> seeds.map { "${l.id}:$it" to Terrain.of(Land.of(l, it)) } } + ("classic" to Terrain.CLASSIC)

    private val hamlet = listOf(FIELD, HUT, WELL, KOZOLEC, TENT, PALISADE, HUT, FIELD, LIPA, SCHOOL)
    private val village = listOf(FIELD, HUT, WELL, KOZOLEC, HOUSE, PALISADE, LIPA, CHURCH, BEEHIVE, SMITHY, HOUSE, WATCHTOWER, FIELD, HOUSE)
    private val town = village + listOf(MARKET, HOUSE, SCHOOL, KOZOLEC, HOUSE, BEEHIVE, HOUSE, WELL, HOUSE, MARKET, HOUSE, HUT, HOUSE, WELL)

    private fun types(age: Age) = when (age) {
        Age.OGENJ, Age.TABOR -> listOf(TENT, TENT)
        Age.ZASELEK -> hamlet
        Age.VAS -> village
        else -> town
    }

    private val ages = listOf(Age.ZASELEK, Age.VAS, Age.TRG, Age.MESTO)

    /** A village of [age] on [t], its plots built, its projects at [stage] (the bridge's at [bridge]). */
    private fun state(t: Terrain, age: Age, stage: Int = 1, bridge: Int = stage) = GameState(
        seed = 42, age = age, villagers = 10, morale = 70, fire = 70, land = t.land,
        buildings = types(age).take(PLOTS_PER_AGE[age.ordinal]).mapIndexed { i, ty -> Building("b$i", ty, if (ty.onPlot) i else NO_PLOT, level = if (i % 4 == 3) 2 else 1) },
        projects = Catalog.projects.filter { it.age <= age }.associate { it.id to if (it.id == "most") bridge.coerceIn(1, it.steps.size) else stage.coerceIn(1, it.steps.size) },
    )

    private val canvases = listOf(360 to 800, 400 to 180)

    // ---------------------------------------------------------------- the cart bridge and its track

    @Test fun `the cart bridge stands where its track crosses the stream, the track going on at both its ends, on every land and at every age`() {
        var n = 0
        for ((name, t) in lands()) for (age in ages) for ((w, h) in canvases) {
            val s = state(t, age)
            val tag = "$name $age ${w}x$h"
            val spots = LandmarkLayout.of(s, w, h)
            val sp = spots.firstOrNull { it.landmark.id == "most" }
            assertNotNull("$tag: the cart bridge placed", sp)
            sp!!
            n++
            val x0 = sp.x; val x1 = sp.x + sp.w; val y0 = sp.y; val y1 = sp.y + sp.d; val cy = sp.cy
            // across the stream, the water under its deck from one side to the other
            for (y in floatArrayOf(y0, cy, y1)) {
                val sx = t.streamX(y)
                assertTrue("$tag: the stream (%.2f) under the deck %.2f..%.2f at %.2f".format(sx, x0, x1, y), sx - t.streamHalf > x0 + 0.5f && sx + t.streamHalf < x1 - 0.5f)
            }
            assertEquals("$tag: one track", 1, sp.ways.size)
            val way = sp.ways.single()
            // the track runs over the bridge along its middle and goes on off both its ends
            for (x in floatArrayOf(x0 - 0.3f, x0 + 0.1f, (x0 + x1) / 2f, x1 - 0.1f, x1 + 0.3f)) {
                assertTrue("$tag: the track at (%.2f, %.2f)".format(x, cy), LandmarkLayout.offWay(way, x, cy) < 0.05f)
            }
            // from the road on the village's side, past the road's bridge (in a crowded town, from a building's path to it)
            val rx = way[0]; val ry = way[1]
            val road = if (age >= Age.VAS) 1f else 0.55f
            val fromRoad = abs(ry - t.roadY(rx)) < road
            val fromPath = s.buildings.any { b ->
                b.type.onPlot && b.type != FIELD && t.centers[b.plot].let { c -> abs(rx - c[0]) < 0.01f && ry in minOf(c[1], t.roadY(c[0]))..maxOf(c[1], t.roadY(c[0])) }
            }
            assertTrue("$tag: the track starts on the road or a building's path to it (%.2f, %.2f)".format(rx, ry), fromRoad || fromPath)
            if (fromRoad) assertTrue("$tag: on the village's side of the road's bridge", rx - LandmarkLayout.TRACK_HALF > t.bridge[0] + t.bridgeHalf)
            // the near part on dry land, off the stream and its banks, the bridge aside
            LandmarkLayout.alongWay(way) { px, py ->
                if (px > x1) assertTrue("$tag: the track in the stream at (%.2f, %.2f)".format(px, py), abs(px - t.streamX(py)) > t.streamBank)
            }
            // into the woods from its far end: among the trees, behind the horizon, at the water or on the road again
            val fx = way[way.size - 2]; val fy = way[way.size - 1]
            val fsx = (fx - fy) * 4f; val fsy = (fx + fy) * 2f
            val woods = VillageLayout.screenMetric(fsx, fsy) > t.forestEdge(age, VillageLayout.angle(fsx, fsy)) + 8f
            val gone = fsy < 4f - VillageLayout.HORIZON
            val onRoad = abs(fy - t.roadY(fx)) < road + LandmarkLayout.TRACK_HALF + 0.3f
            val water = t.water && t.wet(fx - 0.25f, fy, 4f)
            assertTrue("$tag: the track ends at (%.2f, %.2f), not in the woods, at the water or on the road".format(fx, fy), woods || gone || onRoad || water)
            // off every building's plot and the other landmarks
            for (b in s.buildings) if (b.type.onPlot) {
                val c = t.centers[b.plot]
                LandmarkLayout.alongWay(way) { px, py ->
                    assertTrue("$tag: the track over ${b.type} on plot ${b.plot} at (%.2f, %.2f)".format(px, py), abs(px - c[0]) > 1.5f + LandmarkLayout.TRACK_HALF - 0.1f || abs(py - c[1]) > 1.5f + LandmarkLayout.TRACK_HALF - 0.1f)
                }
            }
            for (o in spots) if (o !== sp) LandmarkLayout.alongWay(way) { px, py ->
                assertTrue("$tag: the track over ${o.landmark.id} at (%.2f, %.2f)".format(px, py), !o.covers(px, py, LandmarkLayout.TRACK_HALF - 0.1f))
            }
            // the palisade: no stake on the bridge or the track, a gate of its own where the track goes through it
            val fg = Foreground.of(s, w, h)
            val plain = Palisade.ring(t, age, fg)
            val ring = Palisade.ring(t, age, fg, sp.ways)
            for (st in ring.stakes) {
                assertTrue("$tag: a stake on the bridge at (%.2f, %.2f)".format(st.x, st.y), !sp.covers(st.x, st.y, 0.2f))
                assertTrue("$tag: a stake on the track at (%.2f, %.2f)".format(st.x, st.y), LandmarkLayout.offWay(way, st.x, st.y) > LandmarkLayout.TRACK_HALF + 0.2f)
            }
            for (st in ring.wet) assertTrue("$tag: the bridge in a water gate", !sp.covers(st.x, st.y, 0.5f))
            val gates = ring.gates.filter { it.kind == PalisadeRing.Kind.ROAD }.size - plain.gates.filter { it.kind == PalisadeRing.Kind.ROAD }.size
            val crosses = plain.stakes.any { LandmarkLayout.offWay(way, it.x, it.y) < LandmarkLayout.TRACK_HALF + Palisade.ROAD_CLEAR }
            assertEquals("$tag: the track's own gate in the palisade", if (crosses) 1 else 0, gates)
            assertEquals("$tag: the other gates stay", plain.gates.size + gates, ring.gates.size)
            assertEquals("$tag: the main gate stays", plain.main?.let { plain.middle(it).toList() }, ring.main?.let { ring.middle(it).toList() })
        }
        println("cart bridges: $n places checked")
    }

    @Test fun `the cart bridge and its track keep their place as it is built and as the projects after it start`() {
        for ((name, t) in lands().filterIndexed { i, _ -> i % 6 == 0 } + ("classic" to Terrain.CLASSIC)) for (age in ages) {
            val first = LandmarkLayout.of(state(t, age, stage = 1), 360, 800).first { it.landmark.id == "most" }
            for (st in 2..6) {
                val now = LandmarkLayout.of(state(t, age, stage = 1, bridge = st), 360, 800).first { it.landmark.id == "most" }
                assertEquals("$name $age stage $st", listOf(first.x, first.y), listOf(now.x, now.y))
                assertEquals("$name $age stage $st: its track", first.ways.single().toList(), now.ways.single().toList())
            }
            // only the maypole before it: it stands where it stands with every project started
            val alone = GameState(seed = 42, age = age, villagers = 10, morale = 70, fire = 70, land = t.land,
                buildings = state(t, age).buildings, projects = mapOf("mlaj" to 5, "most" to 3))
            val sp = LandmarkLayout.of(alone, 360, 800).first { it.landmark.id == "most" }
            assertEquals("$name $age with the maypole only", listOf(first.x, first.y), listOf(sp.x, sp.y))
        }
    }

    @Test fun `the road's bridge carries the road from bank to bank`() {
        for ((name, t) in lands()) {
            val b = t.bridge
            // on the road, over the water onto both banks
            assertEquals(name, b[1], t.roadY(b[0]), 0.02f)
            assertTrue("$name: its deck reaches past the water", t.bridgeHalf > t.streamHalf + 0.3f)
            // and the road goes on at both its ends: off the water there
            for (x in floatArrayOf(b[0] - t.bridgeHalf - 0.2f, b[0] + t.bridgeHalf + 0.2f)) assertTrue("$name: the road at %.2f in the stream".format(x), abs(x - t.streamX(t.roadY(x))) > t.streamHalf)
        }
    }

    // ---------------------------------------------------------------- snapshots

    /** A village like Jan's (Vas, the classic valley): its cart bridge at every stage, and finished by night and in winter. */
    @Test fun `render the bridges at every stage, by day, by night and in winter`() {
        val fit = SceneFit.town(1080, 2400)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val jan = GameState(
            seed = 8993091993378563000L, age = Age.VAS, villagers = 12, morale = 70, fire = 70, land = Land.CLASSIC,
            buildings = listOf(
                Building("b0", TENT, NO_PLOT), Building("b1", FIELD, 1), Building("b2", HUT, 2, level = 3), Building("b3", WELL, 3),
                Building("b4", PALISADE, NO_PLOT), Building("b5", KOZOLEC, 8), Building("b6", BEEHIVE, 5, level = 2),
                Building("b7", CHURCH, 6), Building("b8", HOUSE, 0, level = 3), Building("b9", SMITHY, 4), Building("b10", LIPA, 7),
                Building("b11", SCHOOL, 9),
            ),
            projects = mapOf("most" to 6, "mlin" to 2, "mlaj" to 5),
        )
        val sp = LandmarkLayout.of(jan, fit.width, fit.height).first { it.landmark.id == "most" }
        // the bridge in the middle of a closer look (k = 3), 150 × 90 nominal px round it
        val k = 3; val cw = 150; val ch = 90
        val fx = comp.fireX + ((sp.cx - sp.cy) * 4f).toInt() - 10; val fy = comp.fireY + ((sp.cx + sp.cy) * 2f).toInt() + 10
        val lens = Lens(k, fx * k - cw * k / 2, fy * k - ch * k / 2, fit.width, fit.height)
        val frames = listOf(
            "day" to Frame(time = 3.0, hour = 11f, month = 10),
            "night" to Frame(time = 3.0, hour = 22.5f, month = 10),
            "winter" to Frame(time = 3.0, hour = 12.5f, month = 1),
        )
        for (st in 1..6) for ((when_, frame) in frames) {
            if (st < 6 && when_ != "day") continue
            val s = jan.copy(projects = jan.projects + ("most" to st))
            val c = PixelCanvas(cw * k, ch * k)
            VillageRenderer().render(c, s, frame.copy(lens = lens))
            write(c, File(dir, "63-bridge-$st-$when_.png"), 2)
        }
        // the whole village, finished
        val c = PixelCanvas(fit.width, fit.height)
        VillageRenderer().render(c, jan, frames[0].second)
        write(c, File(dir, "63-bridge-village.png"), 3)
        // the hamlet (its road narrower) and a town on generated lands, closer up
        for ((name, t) in listOf("valley-7" to Terrain.of(Land.of(Landscape.VALLEY, 7L)), "mountains-3" to Terrain.of(Land.of(Landscape.MOUNTAINS, 3L)), "coast-42" to Terrain.of(Land.of(Landscape.COAST, 42L)))) for (age in listOf(Age.ZASELEK, Age.MESTO)) {
            val s = state(t, age, stage = 6)
            val b = LandmarkLayout.of(s, fit.width, fit.height).first { it.landmark.id == "most" }
            val bx = comp.fireX + ((b.cx - b.cy) * 4f).toInt() - 10; val by = comp.fireY + ((b.cx + b.cy) * 2f).toInt() + 10
            val z = PixelCanvas(cw * k, ch * k)
            VillageRenderer().render(z, s, frames[0].second.copy(lens = Lens(k, bx * k - cw * k / 2, by * k - ch * k / 2, fit.width, fit.height)))
            write(z, File(dir, "63-bridge-$name-${age.name.lowercase()}.png"), 2)
        }
    }

    private fun write(c: PixelCanvas, f: File, scale: Int) {
        val img = java.awt.image.BufferedImage(c.width * scale, c.height * scale, java.awt.image.BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        javax.imageio.ImageIO.write(img, "png", f)
    }
}
