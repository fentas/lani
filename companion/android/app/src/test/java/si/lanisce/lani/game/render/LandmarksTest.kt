package si.lanisce.lani.game.render

import si.lanisce.lani.FrameBudget
import org.junit.Assert.assertEquals
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
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.ProjectSpec
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The village projects' landmarks on the map: where they stand (clear of everything, on the canvas, at every age and
 * canvas size), that a tap finds them, snapshots of every landmark at every stage and detail, day and night, summer and
 * winter (build/village-snapshots, 60-…), and the frame time with all of them standing.
 */
class LandmarksTest {
    private val dir = File("build/village-snapshots").apply { mkdirs() }

    private val hamletTypes = arrayOf(FIELD, HUT, WELL, KOZOLEC, TENT, PALISADE, HUT, FIELD, LIPA)
    private val villageTypes = arrayOf(FIELD, HUT, WELL, KOZOLEC, HOUSE, PALISADE, LIPA, CHURCH, BEEHIVE, SMITHY, HOUSE, WATCHTOWER, FIELD, HOUSE)
    private val trgTypes = villageTypes + arrayOf(MARKET, HOUSE, SCHOOL, KOZOLEC, HOUSE, BEEHIVE)
    private val townTypes = trgTypes + arrayOf(HOUSE, WELL, HOUSE, MARKET, HOUSE, HUT, HOUSE, WELL)

    private fun typesOf(age: Age): Array<BuildingType> = when (age) {
        Age.OGENJ, Age.TABOR -> arrayOf(TENT, TENT)
        Age.ZASELEK -> hamletTypes
        Age.VAS -> villageTypes
        Age.TRG -> trgTypes
        Age.MESTO -> townTypes
    }

    /** A village of [age] with its plots built with [types] (at most the age's plots), and its projects at [stage] (0: not started). */
    private fun village(age: Age, types: Array<BuildingType> = typesOf(age), villagers: Int = 10, stage: (ProjectSpec) -> Int = { it.steps.size }): GameState {
        val plots = PLOTS_PER_AGE[age.ordinal]
        return GameState(
            seed = 42, age = age, villagers = villagers, morale = 70, fire = 70,
            buildings = types.take(plots).mapIndexed { i, t -> Building("b$i", t, i, level = if (i % 4 == 3) 2 else 1) },
            projects = Catalog.projects.filter { it.age <= age }.associate { it.id to stage(it).coerceIn(0, it.steps.size) }.filterValues { it > 0 },
        )
    }

    /** The stage that shows [look] for a project of [steps] steps. */
    private fun stageFor(look: BuildLook, steps: Int): Int = (1..steps).first { BuildLook.of(it, steps) == look }

    private val fit = SceneFit.town(1080, 2400)

    /** The canvases the town can have: phones upright and on their side, a tall phone, a tablet (the narrowest). */
    private val canvases = listOf(
        SceneFit.town(1080, 2400), SceneFit.town(1440, 3120), SceneFit.town(2400, 1080), SceneFit.town(1600, 2560),
    )

    private val ages = listOf(Age.ZASELEK, Age.VAS, Age.TRG, Age.MESTO)

    // ---------------------------------------------------------------- where they stand

    @Test fun `every landmark finds a place clear of everything, at every age and on every canvas`() {
        for (age in ages) for (full in listOf(true, false)) for (fit in canvases) {
            val types = typesOf(age).let { if (full) it else it.take(PLOTS_PER_AGE[age.ordinal] / 2).toTypedArray() }
            val s = village(age, types) { 1 }
            val spots = LandmarkLayout.of(s, fit.width, fit.height)
            assertEquals("$age $fit: every project's landmark is placed", s.projects.size, spots.size)
            val comp = VillageLayout.composition(fit.width, fit.height, false)
            val fg = Foreground.of(age, fit.width, fit.height)
            val road = if (age >= Age.VAS) 1f else 0.55f
            for (sp in spots) {
                val id = sp.landmark.id
                val tag = "$age $fit $id at (%.2f, %.2f)".format(sp.x, sp.y)
                val x0 = sp.x; val y0 = sp.y; val x1 = sp.x + sp.w; val y1 = sp.y + sp.d
                // not on a plot of the age (built or not), nor on the road
                for (p in 0 until PLOTS_PER_AGE[age.ordinal]) {
                    val c = Terrain.CLASSIC.centers[p]
                    assertTrue("$tag on plot $p", !(x1 > c[0] - 1.5f && x0 < c[0] + 1.5f && y1 > c[1] - 1.5f && y0 < c[1] + 1.5f))
                }
                assertTrue("$tag on the road", y1 <= -road || y0 >= road)
                // clear of the fire and the ring where people gather round it
                val dx = maxOf(x0, 0f, -x1); val dy = maxOf(y0, 0f, -y1)
                assertTrue("$tag by the fire", dx * dx + dy * dy >= LandmarkLayout.FIRE_RING * LandmarkLayout.FIRE_RING)
                // the footbridge crosses the stream, well apart from the road's bridge; everything else keeps off the water
                if (id == "most") {
                    assertTrue("$tag doesn't cross the stream", Terrain.CLASSIC.streamX((y0 + y1) / 2f) in x0..x1)
                    assertTrue("$tag on the road's bridge", y1 < -2.5f || y0 > 2.5f)
                } else {
                    var y = y0
                    while (y <= y1) { val sx = Terrain.CLASSIC.streamX(y); assertTrue("$tag in the stream", x1 < sx - 1f || x0 > sx + 1f); y += 0.25f }
                }
                // the mill stands on the village's bank, its race's end (-x) at the water
                if (id == "mlin") {
                    val sx = Terrain.CLASSIC.streamX((y0 + y1) / 2f)
                    assertTrue("$tag not at the water (the stream at %.2f)".format(sx), x0 in sx + 1f..sx + 1.6f)
                }
                // off the paths of the buildings there are
                for (b in s.buildings) if (b.type != PALISADE && b.type != FIELD) {
                    val c = Terrain.CLASSIC.centers[b.plot]
                    val onPath = x1 > c[0] - 0.5f && x0 < c[0] + 0.5f && y1 > minOf(c[1], 0f) && y0 < maxOf(c[1], 0f)
                    assertTrue("$tag on the path of ${b.id}", !onPath)
                }
                // on the canvas, standing below the horizon (in the woods' meadow, or in the clearing)
                val r = sp.screenRect()
                assertTrue("$tag off the canvas: ${r.toList()}", comp.fireX + r[0] >= 0f && comp.fireX + r[2] <= fit.width && comp.fireY + r[1] >= 0f && comp.fireY + r[3] <= fit.height)
                assertTrue("$tag above the horizon", comp.fireY + (x0 + y0) * 2f > comp.horizon + 2)
                if (fg != null && id == "cebelji_travnik") assertTrue("$tag not in the meadow", fg.meadowD(comp.fireX + (sp.cx - sp.cy) * 4f, comp.fireY + (sp.cx + sp.cy) * 2f) < 1f)
                // not on another landmark
                for (o in spots) if (o !== sp) {
                    assertTrue("$tag on ${o.landmark.id}", !(x1 > o.x && x0 < o.x + o.w && y1 > o.y && y0 < o.y + o.d))
                }
            }
        }
    }

    @Test fun `a landmark keeps its place as it is built and as more projects start`() {
        for (age in ages) {
            val done = LandmarkLayout.of(village(age), fit.width, fit.height)
            val first = LandmarkLayout.of(village(age) { 1 }, fit.width, fit.height)
            assertEquals(done.map { listOf(it.x, it.y, it.w, it.d) }, first.map { listOf(it.x, it.y, it.w, it.d) })
            // the projects started one by one in the catalog's order: the ones there stay where they are
            val specs = Catalog.projects.filter { it.age <= age }
            for (n in 1..specs.size) {
                val some = LandmarkLayout.of(village(age) { if (specs.indexOf(it) < n) 1 else 0 }, fit.width, fit.height)
                assertEquals(first.take(n).map { listOf(it.x, it.y) }, some.map { listOf(it.x, it.y) })
            }
        }
        // nothing before the first step
        assertTrue(LandmarkLayout.of(village(Age.MESTO) { 0 }, fit.width, fit.height).isEmpty())
        // the Home header's strip (no woods) puts them where a canvas without the woods does
        val town = village(Age.MESTO)
        val strip = SceneFit.of(1080, 700, true); val wide = SceneFit.town(2400, 1080)
        assertEquals(
            LandmarkLayout.of(town, wide.width, wide.height).map { listOf(it.x, it.y) },
            LandmarkLayout.of(town, strip.width, strip.height, compact = true).map { listOf(it.x, it.y) },
        )
        // the looks by stage: the site after the first step, half-built, scaffolded, done
        val s = BuildLook.SITE; val f = BuildLook.FRAME; val sc = BuildLook.SCAFFOLD; val d = BuildLook.DONE
        assertEquals(listOf(s, f, f, sc, d), (1..5).map { BuildLook.of(it, 5) })
        assertEquals(listOf(s, f, f, sc, sc, d), (1..6).map { BuildLook.of(it, 6) })
        assertEquals(listOf(s, f, f, f, sc, sc, d), (1..7).map { BuildLook.of(it, 7) })
    }

    @Test fun `the landmarks leave the buildings be, show themselves, and a tap opens their project`() {
        val frame = Frame(time = 2.0, hour = 11f, month = 7)
        for (age in ages) for (fitAt in listOf(fit, SceneFit.town(2400, 1080))) {
            val s = village(age)
            val w = fitAt.width; val h = fitAt.height
            val r0 = VillageRenderer(); r0.render(PixelCanvas(w, h), s.copy(projects = emptyMap()), frame)
            val r1 = VillageRenderer(); r1.render(PixelCanvas(w, h), s, frame)
            val seen = HashMap<String, Int>(); val covered = HashMap<String, Int>(); val shown = HashMap<String, Int>()
            for (y in 0 until h) for (x in 0 until w) {
                val before = r0.hitTest(x, y, 0); val after = r1.hitTest(x, y, 0)
                if (before is VillageHit.OnBuilding) {
                    seen.merge(before.building.id, 1, Int::plus)
                    if (after is VillageHit.OnLandmark) covered.merge(before.building.id, 1, Int::plus)
                }
                if (after is VillageHit.OnLandmark) shown.merge(after.project, 1, Int::plus)
            }
            val worst = covered.maxByOrNull { it.value.toFloat() / seen.getValue(it.key) }
            println("landmarks $age ${w}x$h: most covered building ${worst?.key} ${worst?.let { "%.0f %%".format(100f * it.value / seen.getValue(it.key)) }}; pixels shown ${shown.toSortedMap()}")
            for ((id, n) in covered) assertTrue("$age ${w}x$h: building $id is ${100 * n / seen.getValue(id)} % covered", n <= seen.getValue(id) * 0.4f)
            val comp = VillageLayout.composition(w, h, false)
            for (sp in LandmarkLayout.of(s, w, h)) {
                val p = sp.landmark.project
                assertTrue("$age ${w}x$h: $p shows ${shown[p]} px", (shown[p] ?: 0) >= 25)
                // a tap on its body opens it, and screen readers find it there
                assertEquals("$age ${w}x$h: a tap on $p", VillageHit.OnLandmark(p), r1.hitTest((comp.fireX + sp.bodyX).toInt(), (comp.fireY + sp.bodyY).toInt(), 4))
            }
            val targets = TownAnchors.landmarks(s, w, h).associate { it.first.landmark.project to it.second }
            assertEquals(s.projects.keys, targets.keys)
        }
    }

    @Test fun `the town with every landmark renders within the frame budget`() {
        val s = village(Age.MESTO)
        val rig = CameraRig.town(fit, 1080, 2400)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val c = PixelCanvas(fit.width, fit.height)
        val n = 40
        for (k in 1..3) {
            val lens = Lens.of(k, rig.lookAt(comp.fireX.toFloat(), comp.fireY.toFloat(), (rig.minScale * k).toFloat()), 1080, 2400, fit.width, fit.height, fit.width, fit.height)
            val times = listOf(s.copy(projects = emptyMap()), s).map { st ->
                val r = VillageRenderer()
                val frame = Frame(time = 0.0, hour = 21f, month = 9, lens = lens)
                repeat(15) { r.render(c, st, frame.copy(time = it / 12.0)) }
                val t0 = System.nanoTime()
                repeat(n) { r.render(c, st, frame.copy(time = 2 + it / 12.0)) }
                (System.nanoTime() - t0) / 1e6 / n
            }
            println("landmarks: %.2f ms/frame without, %.2f ms/frame with all twelve (1080×2400 town, night, k=%d)".format(times[0], times[1], k))
            assertTrue("k=$k too slow: ${times[1]} ms", times[1] < FrameBudget.scaled(30.0))
        }
        // placing them all is done once and cached
        val t0 = System.nanoTime()
        LandmarkLayout.of(village(Age.MESTO, townTypes.copyOfRange(0, 27)), fit.width, fit.height)
        println("landmarks: placing all twelve took %.1f ms".format((System.nanoTime() - t0) / 1e6))
    }

    // ---------------------------------------------------------------- snapshots

    @Test fun `render the landmarks in villages of every age`() {
        for (age in listOf(Age.ZASELEK, Age.VAS, Age.TRG, Age.MESTO)) {
            for ((name, stage) in listOf<Pair<String, (ProjectSpec) -> Int>>(
                "done" to { it.steps.size },
                "building" to { p -> (Catalog.projects.indexOf(p) % (p.steps.size - 1)) + 1 },
            )) {
                val s = village(age, stage = stage)
                for ((when_, frame) in listOf(
                    "summer-day" to Frame(time = 3.0, hour = 11f, month = 7),
                    "summer-night" to Frame(time = 3.0, hour = 22.5f, month = 7),
                    "winter-day" to Frame(time = 3.0, hour = 12.5f, month = 1),
                    "autumn-evening" to Frame(time = 3.0, hour = 18.6f, month = 9),
                )) {
                    if (age != Age.MESTO && when_ != "summer-day") continue
                    val c = PixelCanvas(fit.width, fit.height)
                    VillageRenderer().render(c, s, frame)
                    write(c, File(dir, "60-landmarks-${age.name.lowercase()}-$name-$when_.png"), 3, cropTop = 248, cropBottom = 435, cropLeft = 5, cropRight = 355)
                }
            }
        }
        // the landscape view and a compact strip
        val town = village(Age.MESTO)
        for ((name, size) in listOf("landscape" to SceneFit.town(2400, 1080), "compact" to SceneFit.of(1080, 700, true))) {
            val c = PixelCanvas(size.width, size.height)
            VillageRenderer().render(c, town, Frame(time = 3.0, hour = 11f, month = 7, compact = name == "compact"))
            write(c, File(dir, "60-landmarks-mesto-$name.png"), 3)
        }
    }

    /**
     * Every landmark on its own, close up (61-landmark-<id>.png): a row per detail level (1 blown up, 2, 3), the four looks
     * side by side, then finished at night, in winter and in autumn; and the finished one at detail 3, twice the size
     * (62-zoom-<id>-….png).
     */
    @Test fun `render every landmark at every stage and detail`() {
        val cellW = 56; val cellH = 76
        val scale = 3
        val variants = listOf<Triple<String, BuildLook, Frame>>(
            Triple("site", BuildLook.SITE, Frame(time = 3.0, hour = 11f, month = 7)),
            Triple("frame", BuildLook.FRAME, Frame(time = 3.0, hour = 11f, month = 7)),
            Triple("scaffold", BuildLook.SCAFFOLD, Frame(time = 3.0, hour = 11f, month = 7)),
            Triple("done", BuildLook.DONE, Frame(time = 3.0, hour = 11f, month = 7)),
            Triple("night", BuildLook.DONE, Frame(time = 3.0, hour = 22.5f, month = 7)),
            Triple("winter", BuildLook.DONE, Frame(time = 3.0, hour = 12.5f, month = 1)),
            Triple("autumn", BuildLook.DONE, Frame(time = 3.0, hour = 16f, month = 10)),
        )
        for (spec in Catalog.projects) {
            val img = BufferedImage(cellW * scale * variants.size, cellH * scale * 3, BufferedImage.TYPE_INT_RGB)
            for ((col, v) in variants.withIndex()) {
                val (_, look, frame) = v
                // only this project started, at the look's stage, in a town that has every site
                val s = village(Age.MESTO) { if (it.id == spec.id) stageFor(look, it.steps.size) else 0 }
                val sp = LandmarkLayout.of(s, fit.width, fit.height).single()
                val comp = VillageLayout.composition(fit.width, fit.height, false)
                val r = sp.screenRect()
                val cx = comp.fireX + (r[0] + r[2]) / 2f; val cy = comp.fireY + (r[1] + r[3]) / 2f
                for (k in 1..3) {
                    val cw = cellW * k; val ch = cellH * k
                    val x0 = (cx * k - cw / 2f).toInt(); val y0 = (cy * k - ch / 2f).toInt()
                    val c: PixelCanvas
                    if (k == 1) { c = PixelCanvas(fit.width, fit.height); VillageRenderer().render(c, s, frame) }
                    else { c = PixelCanvas(cw, ch); VillageRenderer().render(c, s, frame.copy(lens = Lens(k, x0, y0, fit.width, fit.height))) }
                    if (k == 3 && v.first in setOf("done", "night", "winter")) write(c, File(dir, "62-zoom-${spec.id}-${v.first}.png"), 2)
                    val px = scale / k.toFloat()
                    for (yy in 0 until cellH * scale) for (xx in 0 until cellW * scale) {
                        val sx: Int; val sy: Int
                        if (k == 1) { sx = x0 + (xx / px).toInt(); sy = y0 + (yy / px).toInt() } else { sx = (xx / px).toInt(); sy = (yy / px).toInt() }
                        val color = if (sx in 0 until c.width && sy in 0 until c.height) c.pixels[sy * c.width + sx] else 0
                        img.setRGB(col * cellW * scale + xx, (k - 1) * cellH * scale + yy, color)
                    }
                }
            }
            ImageIO.write(img, "png", File(dir, "61-landmark-${spec.id}.png"))
        }
    }

    private fun write(c: PixelCanvas, f: File, scale: Int, cropTop: Int = 0, cropBottom: Int = c.height, cropLeft: Int = 0, cropRight: Int = c.width) {
        val h = cropBottom - cropTop; val w = cropRight - cropLeft
        val img = BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h * scale) for (x in 0 until w * scale) img.setRGB(x, y, c.pixels[(cropTop + y / scale) * c.width + cropLeft + x / scale])
        ImageIO.write(img, "png", f)
    }
}
