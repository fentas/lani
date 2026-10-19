package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import si.lanisce.lani.game.render.scene.StreamPlan
import java.io.File
import kotlin.math.abs

/**
 * The crossing on the map (Jan, 2026-10-19: "maybe the first bridge (the flat one) is not there in the beginning ... just
 * the stones across, and we build the first bridge and leave away the second one"; before, 2026-10-18: "the bridge seems
 * not to be drivable ;)", "it goes right into a wall and starts from nowhere"): one bridge, where the road crosses the
 * stream ([Crossing]). From Zaselek, when the road goes west over the stream, it fords it on stepping stones until the
 * village's bridge (the project "most") has its deck; the bridge is built there, from bank to bank and as wide as the
 * road, the road going on at both its ends, never into the palisade; no other bridge, no track or gate of its own. On
 * every land, at every age and stage. In the stream's scene ([StreamPlan]) the paths along both banks come onto the
 * footbridge's ends and the cart road up the ramps onto the cart bridge's. Snapshots of the crossing at every stage, by
 * day, by night and in winter, and of the scene's at every stage and detail, for review (build/village-snapshots/63-bridge-…,
 * 64-stream-bridges-…).
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

    /** A village of [age] on [t], its plots built, its projects at [stage] (the bridge's at [bridge]; 0: not begun). */
    private fun state(t: Terrain, age: Age, stage: Int = 1, bridge: Int = stage) = GameState(
        seed = 42, age = age, villagers = 10, morale = 70, fire = 70, land = t.land,
        buildings = types(age).take(PLOTS_PER_AGE[age.ordinal]).mapIndexed { i, ty -> Building("b$i", ty, if (ty.onPlot) i else NO_PLOT, level = if (i % 4 == 3) 2 else 1) },
        projects = Catalog.projects.filter { it.age <= age }
            .associate { it.id to if (it.id == Crossing.BRIDGE) bridge.coerceIn(0, it.steps.size) else stage.coerceIn(1, it.steps.size) }
            .filterValues { it > 0 },
    )

    private val canvases = listOf(360 to 800, 400 to 180)

    private fun road(age: Age) = if (age >= Age.VAS) 1f else 0.55f

    // ---------------------------------------------------------------- the crossing: stones, then the bridge

    @Test fun `the road fords the stream on stepping stones until the bridge has its deck`() {
        val t = Terrain.CLASSIC
        val steps = Catalog.projects.first { it.id == Crossing.BRIDGE }.steps.size
        // before the hamlet the road doesn't go west: nothing crosses the stream
        for (age in listOf(Age.OGENJ, Age.TABOR)) {
            assertFalse("$age", Crossing.stones(state(t, age, bridge = 0)))
            assertFalse("$age", Crossing.bridged(state(t, age, bridge = 0)))
        }
        for (age in ages) {
            // the stones before the first step and while the bridge is built: measured, the stones, the pillars, the logs
            for (st in 0 until Crossing.DECKED) {
                val s = state(t, age, bridge = st)
                assertTrue("$age stage $st: the stones", Crossing.stones(s))
                assertFalse("$age stage $st: no bridge yet", Crossing.bridged(s))
            }
            // the deck laid, then the railings: over the bridge, no stones
            for (st in Crossing.DECKED..steps) {
                val s = state(t, age, bridge = st)
                assertFalse("$age stage $st: no stones", Crossing.stones(s))
                assertTrue("$age stage $st: the bridge", Crossing.bridged(s))
            }
        }
        // an age's moment: the road west grows in before the stones are there
        assertFalse(Crossing.stones(west = false, bridge = null))
        assertTrue(Crossing.stones(west = true, bridge = null))
    }

    @Test fun `the one bridge stands where the road crosses the stream, the road going on at both its ends, on every land and at every age`() {
        var n = 0
        var worst = Float.MAX_VALUE
        for ((name, t) in lands()) for (age in ages) for ((w, h) in canvases) for (st in listOf(1, 3, 6)) {
            val s = state(t, age, bridge = st)
            val tag = "$name $age ${w}x$h stage $st"
            val spots = LandmarkLayout.of(s, w, h)
            val bridges = spots.filter { it.landmark.id == Crossing.BRIDGE }
            assertEquals("$tag: one bridge", 1, bridges.size)
            val sp = bridges.single()
            n++
            // where the road crosses the stream, as wide as the road and a little more
            assertEquals("$tag: at the crossing", Crossing.box(t, road(age)).toList(), listOf(sp.x, sp.y, sp.w, sp.d))
            val x0 = sp.x; val x1 = sp.x + sp.w; val y0 = sp.y; val y1 = sp.y + sp.d
            // across the stream, its ends on the banks: the water under the deck from one side to the other, its ends past
            // it along the road's middle and along both its sides, where the stream runs aslant under it too
            var y = y0
            while (y <= y1 + 1e-4f) {
                val sx = t.streamX(y)
                val margin = minOf(sx - t.streamHalf - x0, x1 - sx - t.streamHalf)
                worst = minOf(worst, margin)
                assertTrue("$tag: the stream (%.2f) under the deck %.2f..%.2f at %.2f".format(sx, x0, x1, y), margin > 0.25f)
                y += 0.1f
            }
            assertTrue("$tag: the deck's ends on the banks where the road crosses", t.bridge[0] - x0 >= t.bridgeHalf - 1e-4f && x1 - t.bridge[0] >= t.bridgeHalf - 1e-4f)
            // the road on the deck and on dry land off both its ends
            for (x in floatArrayOf(x0 - 0.2f, x0 + 0.1f, sp.cx, x1 - 0.1f, x1 + 0.2f)) {
                val ry = t.roadY(x)
                assertTrue("$tag: the road's middle (%.2f) off the deck at %.2f".format(ry, x), ry > y0 + Crossing.SIDE && ry < y1 - Crossing.SIDE)
            }
            for (x in floatArrayOf(x0 - 0.2f, x1 + 0.2f)) assertTrue("$tag: the road at %.2f in the stream".format(x), abs(x - t.streamX(t.roadY(x))) > t.streamHalf)
            // no other landmark on it
            for (o in spots) if (o !== sp) assertTrue("$tag: ${o.landmark.id} on the bridge", o.x + o.w <= x0 || o.x >= x1 || o.y + o.d <= y0 || o.y >= y1)
            // the palisade: no stake on the bridge, nor on the road on from it to the west (it leaves through a gate), no
            // gate but the road's, the path's and the stream's
            val fg = Foreground.of(s, w, h)
            val ring = Palisade.ring(t, age, fg)
            for (stake in ring.stakes) {
                assertTrue("$tag: a stake on the bridge at (%.2f, %.2f)".format(stake.x, stake.y), !sp.covers(stake.x, stake.y, 0.2f))
                if (stake.x < x0) assertTrue("$tag: a stake on the road west at (%.2f, %.2f)".format(stake.x, stake.y), abs(stake.y - t.roadY(stake.x)) > road(age))
            }
            for (wet in ring.wet) assertTrue("$tag: the bridge in a water gate", !sp.covers(wet.x, wet.y, 0.3f))
            assertTrue("$tag: a gate of the cart track's", ring.gates.count { it.kind == PalisadeRing.Kind.ROAD } <= 2)
        }
        println("bridges: $n places checked; the water at least %.2f cells from a deck's end".format(worst))
    }

    @Test fun `the bridge keeps its place as it is built and as the projects after it start`() {
        for ((name, t) in lands().filterIndexed { i, _ -> i % 6 == 0 } + ("classic" to Terrain.CLASSIC)) for (age in ages) {
            val first = LandmarkLayout.of(state(t, age, stage = 1), 360, 800).first { it.landmark.id == Crossing.BRIDGE }
            for (st in 2..6) {
                val now = LandmarkLayout.of(state(t, age, stage = 1, bridge = st), 360, 800).first { it.landmark.id == Crossing.BRIDGE }
                assertEquals("$name $age stage $st", listOf(first.x, first.y, first.w, first.d), listOf(now.x, now.y, now.w, now.d))
            }
            // only the maypole before it: it stands where it stands with every project started
            val alone = GameState(seed = 42, age = age, villagers = 10, morale = 70, fire = 70, land = t.land,
                buildings = state(t, age).buildings, projects = mapOf("mlaj" to 5, Crossing.BRIDGE to 3))
            val sp = LandmarkLayout.of(alone, 360, 800).first { it.landmark.id == Crossing.BRIDGE }
            assertEquals("$name $age with the maypole only", listOf(first.x, first.y), listOf(sp.x, sp.y))
        }
    }

    /**
     * One bridge (Jan: "leave away the second one"): building it changes the map only at the crossing, the stones going
     * and the bridge coming; nothing anywhere else (no track, no gate, no other bridge), by day and at night.
     */
    @Test fun `building the bridge changes the map only where the road crosses the stream`() {
        val fit = SceneFit.town(1080, 2400)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        for ((name, t) in listOf("classic" to Terrain.CLASSIC, "valley-7" to Terrain.of(Land.of(Landscape.VALLEY, 7L)), "mountains-3" to Terrain.of(Land.of(Landscape.MOUNTAINS, 3L)))) for (age in listOf(Age.ZASELEK, Age.VAS)) {
            for (frame in listOf(Frame(time = 3.0, hour = 11f, month = 10, placed = emptyList()), Frame(time = 3.0, hour = 22.5f, month = 1, placed = emptyList()))) {
                val before = PixelCanvas(fit.width, fit.height).also { VillageRenderer().render(it, state(t, age, bridge = 0), frame) }
                val after = PixelCanvas(fit.width, fit.height).also { VillageRenderer().render(it, state(t, age, bridge = 6), frame) }
                // the crossing on the canvas: the bridge's footprint and its railings' height, a pixel or two round it
                val b = Crossing.box(t, road(age))
                val l = comp.fireX + (b[0] - b[1] - b[3]) * 4f - 2f; val r = comp.fireX + (b[0] + b[2] - b[1]) * 4f + 2f
                val top = comp.fireY + (b[0] + b[1]) * 2f - 6f; val bottom = comp.fireY + (b[0] + b[2] + b[1] + b[3]) * 2f + 2f
                var changed = 0
                for (y in 0 until fit.height) for (x in 0 until fit.width) {
                    val i = y * fit.width + x
                    if (before.pixels[i] == after.pixels[i]) continue
                    changed++
                    assertTrue("$name $age h${frame.hour}: the picture changed at ($x, $y), off the crossing $l..$r × $top..$bottom", x >= l && x <= r && y >= top && y <= bottom)
                }
                assertTrue("$name $age: the bridge drawn", changed > 20)
            }
        }
    }

    @Test fun `the crossing carries the road from bank to bank`() {
        for ((name, t) in lands()) {
            val b = t.bridge
            // on the road, over the water onto both banks
            assertEquals(name, b[1], t.roadY(b[0]), 0.02f)
            assertTrue("$name: its deck reaches past the water", t.bridgeHalf > t.streamHalf + 0.3f)
            // and the road goes on at both its ends: off the water there
            for (x in floatArrayOf(b[0] - t.bridgeHalf - 0.2f, b[0] + t.bridgeHalf + 0.2f)) assertTrue("$name: the road at %.2f in the stream".format(x), abs(x - t.streamX(t.roadY(x))) > t.streamHalf)
        }
    }

    // ---------------------------------------------------------------- the stream's scene

    /** The stream's bridges (Ob potoku): every way meets its bridge's open end, every deck spans the water bank to bank. */
    @Test fun `in the stream's scene the paths come onto the footbridge and the cart road onto the cart bridge`() {
        val p = StreamPlan
        // the footbridge: its deck over the water from bank to bank, its ends on the banks
        for (d in floatArrayOf(p.FOOT_D0, p.FOOT_D, p.FOOT_D1)) {
            assertTrue("the footbridge's left end on the bank at $d", p.FOOT_L < p.course(d) - p.half(d) - 0.3f)
            assertTrue("the footbridge's right end on the bank at $d", p.FOOT_R > p.course(d) + p.half(d) + 0.3f)
        }
        // the paths: along the banks, off the water, from the picture's bottom up to the deck's ends, under them
        var d = 10f
        while (d <= p.FOOT_D0) {
            assertTrue("the right bank's track in the water at $d", p.track(d) - p.PATH_HALF > p.course(d) + p.half(d))
            assertTrue("the left bank's path in the water at $d", p.leftPath(d) + p.PATH_HALF < p.course(d) - p.half(d))
            d += 0.25f
        }
        assertTrue("the paths reach under the deck", p.PATHS_TO > p.FOOT_D0 + 0.3f)
        assertTrue("the track onto the footbridge's right end", abs(p.track(p.FOOT_D0) - p.FOOT_R) < p.PATH_HALF)
        assertTrue("the path onto the footbridge's left end", abs(p.leftPath(p.FOOT_D0) - p.FOOT_L) < p.PATH_HALF)
        // the cart bridge: its deck over the water, the piers on the banks
        for (cd in floatArrayOf(p.CART_D0, p.CART_D, p.CART_D1)) {
            assertTrue("the cart bridge's left end on the bank at $cd", p.CART_L < p.course(cd) - p.half(cd) - 0.5f)
            assertTrue("the cart bridge's right end on the bank at $cd", p.CART_R > p.course(cd) + p.half(cd) + 0.5f)
        }
        // the cart road: straight on off both ends over the ramps, as wide as the deck there, then on its way
        for (x in floatArrayOf(p.CART_L - p.RAMP, p.CART_L - 0.1f, p.CART_R + 0.1f, p.CART_R + p.RAMP)) {
            assertEquals("the cart road's middle at $x", p.CART_D, p.roadMid(x), 0.001f)
            val half = 0.75f * p.roadScale(x)
            assertTrue("the cart road at $x as wide as the deck ($half)", half >= (p.CART_D1 - p.CART_D0) / 2f - 0.05f && half <= (p.CART_D1 - p.CART_D0) / 2f + 0.2f)
        }
        for (x in floatArrayOf(p.CART_L - p.RAMP - 4f, p.CART_R + p.RAMP + 4f)) assertTrue("the cart road goes on at $x", 0.75f * p.roadScale(x) > 1.2f)
    }

    /** The stream's bridges as the project goes (no step, each step), at every detail, by day; finished by night and in winter. */
    @Test fun `render the stream's bridges at every stage`() {
        val crop = intArrayOf(40, 50, 170, 70)
        for (st in 0..6) for ((when_, hour, month) in listOf(Triple("day", 10f, 7), Triple("night", 22.5f, 7), Triple("winter", 12f, 1))) {
            if (st < 6 && when_ != "day") continue
            val world = si.lanisce.lani.game.scene.SceneWorld(Age.MESTO, si.lanisce.lani.game.BuildingType.entries.associateWith { 1 }, mapOf("mlin" to 6, "most" to st))
            val frame = si.lanisce.lani.game.scene.SceneFrame(time = 3.0, hour = hour, month = month, objects = si.lanisce.lani.game.scene.SceneArt.objects.getValue("stream").toSet(), world = world)
            for (k in 1..Lens.MAX_DETAIL) {
                val c = PixelCanvas(240 * k, 160 * k)
                si.lanisce.lani.game.scene.ScenePainters.create("stream").render(c, frame, Lens(k, 0, 0, 240, 160))
                val s = 6 / k
                val img = java.awt.image.BufferedImage(crop[2] * 6, crop[3] * 6, java.awt.image.BufferedImage.TYPE_INT_RGB)
                for (y in 0 until crop[3] * 6) for (x in 0 until crop[2] * 6) img.setRGB(x, y, c.pixels[(crop[1] * k + y / s) * c.width + crop[0] * k + x / s])
                javax.imageio.ImageIO.write(img, "png", File(dir, "64-stream-bridges-$st-$when_-d$k.png"))
            }
        }
    }

    // ---------------------------------------------------------------- snapshots

    /** A village like Jan's (Vas, the classic valley): the crossing at every stage, and the bridge by night and in winter. */
    @Test fun `render the crossing at every stage, by day, by night and in winter`() {
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
            projects = mapOf(Crossing.BRIDGE to 6, "mlin" to 2, "mlaj" to 5),
        )
        val t = Terrain.CLASSIC
        val b = Crossing.box(t, 1f)
        val cx = b[0] + b[2] / 2f; val cy = b[1] + b[3] / 2f
        // the crossing in the middle of a closer look (k = 3), 150 × 90 nominal px round it
        val k = 3; val cw = 150; val ch = 90
        val fx = comp.fireX + ((cx - cy) * 4f).toInt(); val fy = comp.fireY + ((cx + cy) * 2f).toInt()
        val lens = Lens(k, fx * k - cw * k / 2, fy * k - ch * k / 2, fit.width, fit.height)
        val frames = listOf(
            "day" to Frame(time = 3.0, hour = 11f, month = 10),
            "night" to Frame(time = 3.0, hour = 22.5f, month = 10),
            "winter" to Frame(time = 3.0, hour = 12.5f, month = 1),
        )
        for (st in 0..6) for ((when_, frame) in frames) {
            if (st in 1..5 && when_ != "day") continue
            val s = jan.copy(projects = if (st == 0) jan.projects - Crossing.BRIDGE else jan.projects + (Crossing.BRIDGE to st))
            val c = PixelCanvas(cw * k, ch * k)
            VillageRenderer().render(c, s, frame.copy(lens = lens))
            write(c, File(dir, "63-bridge-$st-$when_.png"), 2)
        }
        // the whole village, the stones and the bridge
        for ((tag, s) in listOf("stones" to jan.copy(projects = jan.projects - Crossing.BRIDGE), "built" to jan)) {
            val c = PixelCanvas(fit.width, fit.height)
            VillageRenderer().render(c, s, frames[0].second)
            write(c, File(dir, "63-bridge-village-$tag.png"), 3)
        }
        // the hamlet (its road narrower) and a town on generated lands, closer up: the stones and the bridge
        for ((name, land) in listOf("valley-7" to Terrain.of(Land.of(Landscape.VALLEY, 7L)), "mountains-3" to Terrain.of(Land.of(Landscape.MOUNTAINS, 3L)), "coast-42" to Terrain.of(Land.of(Landscape.COAST, 42L)))) for (age in listOf(Age.ZASELEK, Age.MESTO)) {
            for (st in listOf(0, 6)) {
                val s = state(land, age, stage = 6, bridge = st)
                val bb = Crossing.box(land, road(age))
                val bcx = bb[0] + bb[2] / 2f; val bcy = bb[1] + bb[3] / 2f
                val bx = comp.fireX + ((bcx - bcy) * 4f).toInt(); val by = comp.fireY + ((bcx + bcy) * 2f).toInt()
                val z = PixelCanvas(cw * k, ch * k)
                VillageRenderer().render(z, s, frames[0].second.copy(lens = Lens(k, bx * k - cw * k / 2, by * k - ch * k / 2, fit.width, fit.height)))
                write(z, File(dir, "63-bridge-$name-${age.name.lowercase()}-$st.png"), 2)
            }
        }
    }

    private fun write(c: PixelCanvas, f: File, scale: Int) {
        val img = java.awt.image.BufferedImage(c.width * scale, c.height * scale, java.awt.image.BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        javax.imageio.ImageIO.write(img, "png", f)
    }
}
