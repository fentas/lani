package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
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
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.Decor
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.Festive
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.render.scene.FireStyle
import si.lanisce.lani.game.scene.CultureLooks
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The village map in each culture's look (companion/GAME.md "The map in a culture's look"; [MapLook]): the default
 * culture's map is pixel for pixel what it was; each other culture's draws its own village (its houses, church, hayrack,
 * well, flags, feasts and mountains) and agrees with its scenes; every building is still there to tap, at every age, by
 * day and at night. The contact sheets (build/map-looks/) are for the eyes: every culture's map at every age by day and
 * at night, each building closer up, the feasts and the landmarks.
 */
class MapLooksTest {
    private val dir = File("build/map-looks").apply { mkdirs() }

    /** The cultures, each on its usual land (Friuli also by the sea, as Teo's village). */
    private val cultures = listOf(
        "primorska" to Landscape.VALLEY, "friuli" to Landscape.HILLS, "friuli" to Landscape.COAST, "kaernten" to Landscape.LAKE, "lakeland" to Landscape.LAKE,
    )

    private fun label(c: Pair<String, Landscape>) = if (c.second == Landscape.COAST) "${c.first}-coast" else c.first

    /** Every building there is, plot by plot as the ages open them (the palisade round the clearing). */
    private val order = listOf(
        TENT, FIELD, HUT, KOZOLEC, WELL, HOUSE, CHURCH, LIPA, WATCHTOWER, SCHOOL, SMITHY, BEEHIVE, MARKET, HOUSE,
        HOUSE, FIELD, HUT, KOZOLEC, HOUSE, WELL, HOUSE, HOUSE, MARKET, HOUSE, BEEHIVE, HOUSE, FIELD, HOUSE,
    )

    /** A village of [age] on [landscape]'s land: every plot of the age built, some upgraded, the palisade from Tabor. */
    private fun village(age: Age, landscape: Landscape, seed: Long = 2026L, projects: Boolean = age >= Age.TRG): GameState {
        val base = GameEngine.newGame(seed, 0L, landscape)
        val n = PLOTS_PER_AGE[age.ordinal]
        val built = order.take(n).mapIndexed { i, t -> Building("b$i", t, i, level = if (i % 4 == 0) 3 else if (i % 3 == 0) 2 else 1) } +
            (if (age >= Age.TABOR) listOf(Building("p", PALISADE, NO_PLOT, level = 1)) else emptyList())
        return base.copy(
            age = age, villagers = 4 + n, morale = 70, fire = 70, buildings = built,
            projects = if (projects) Catalog.projectFrames.filter { it.age <= age }.associate { it.id to it.steps.size } else emptyMap(),
        )
    }

    private fun render(s: GameState, f: Frame, w: Int, h: Int): PixelCanvas = PixelCanvas(w, h).also { VillageRenderer().render(it, s, f) }

    private fun fnv(ints: IntArray): String {
        var h = -0x340d631b7bdddcdbL
        for (v in ints) { h = h xor (v.toLong() and 0xffffffffL); h *= 0x100000001b3L }
        return java.lang.Long.toHexString(h)
    }

    /** The canvas point over plot [plot]'s middle, [z] px up, on a [w] × [h] canvas. */
    private fun plotAt(s: GameState, plot: Int, w: Int, h: Int, z: Float = 10f): Pair<Int, Int> {
        val comp = VillageLayout.composition(w, h, false)
        val o = Terrain.of(s).origin(plot)
        val cx = o[0] + 1.5f; val cy = o[1] + 1.5f
        return (comp.fireX + (cx - cy) * 4f).toInt() to (comp.fireY + (cx + cy) * 2f - z).toInt()
    }

    private fun lensAt(k: Int, w: Int, h: Int, fx: Int, fy: Int) = Lens(k, (fx * k - w / 2).coerceIn(0, w * k - w), (fy * k - h / 2).coerceIn(0, h * k - h), w, h)

    /** Writes [cells] (canvases, each cropped to [crop] x, y, w, h) as a sheet [cols] across with [gap] px between. */
    private fun sheet(name: String, cells: List<Pair<PixelCanvas, IntArray>>, cols: Int, gap: Int = 3) {
        val cw = cells.maxOf { it.second[2] }; val ch = cells.maxOf { it.second[3] }
        val rows = (cells.size + cols - 1) / cols
        val img = BufferedImage(cols * (cw + gap) + gap, rows * (ch + gap) + gap, BufferedImage.TYPE_INT_RGB)
        val g = img.graphics; g.color = java.awt.Color(0x22, 0x24, 0x2A); g.fillRect(0, 0, img.width, img.height); g.dispose()
        for ((n, cell) in cells.withIndex()) {
            val (c, crop) = cell
            val ox = gap + (n % cols) * (cw + gap); val oy = gap + (n / cols) * (ch + gap)
            for (y in 0 until crop[3]) for (x in 0 until crop[2]) {
                val sx = x + crop[0]; val sy = y + crop[1]
                if (sx in 0 until c.width && sy in 0 until c.height) img.setRGB(ox + x, oy + y, c.pixels[sy * c.width + sx])
            }
        }
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    private val day = Frame(time = 3.0, hour = 11f, month = 6, today = "2026-06-20")
    private val night = Frame(time = 3.0, hour = 22.5f, month = 6, today = "2026-06-20")

    // ---------------------------------------------------------------- the default culture's map stays as it was

    @Test fun `the default culture's map and an unknown culture's are the map as it always was`() {
        for (age in listOf(Age.OGENJ, Age.ZASELEK, Age.MESTO)) {
            val s = village(age, Landscape.VALLEY)
            for (f in listOf(day, night.copy(lens = lensAt(2, 360, 480, 180, 300)))) {
                val before = fnv(render(s, f, 360, 480).pixels)
                assertEquals("$age the default culture named", before, fnv(render(s, f.copy(culture = "primorska"), 360, 480).pixels))
                assertEquals("$age an unknown culture", before, fnv(render(s, f.copy(culture = "nowhere"), 360, 480).pixels))
            }
        }
        assertTrue(MapLook.DEFAULT.own)
        assertTrue(MapLook.of("nowhere").own)
    }

    @Test fun `each culture's map draws its own village`() {
        val s = village(Age.MESTO, Landscape.VALLEY)
        val default = fnv(render(s, day, 360, 480).pixels)
        for (c in listOf("friuli", "kaernten", "lakeland")) {
            assertTrue("$c has a look on the map", "map" in CultureLooks.touched.getValue(c))
            assertNotEquals("$c's map", default, fnv(render(s, day.copy(culture = c), 360, 480).pixels))
        }
    }

    // ---------------------------------------------------------------- the map agrees with the scenes

    @Test fun `the map shows what the scenes show`() {
        // the field's hayrack (Friuli's fienile), the horizon's mountains (the Karawanken, the fells), the camp's flag, the
        // smithy's walls and the school's flag: the map's entry is the scene's wherever the scene has one
        val friuli = CultureLooks.of("friuli")
        assertEquals(friuli.variant("field", "kozolec"), MapLook.of("friuli").hayrack.id)
        assertEquals(friuli.variant("tent", "pennant"), MapLook.of("friuli").pennant?.id)
        assertEquals("cobbles", friuli.variant("smithy", "walls"))
        assertTrue(MapLook.of("friuli").cobbles)
        assertEquals("it", friuli.variant("school", "flag"))
        assertEquals(SchoolKind.ITALIAN, MapLook.of("friuli").school)
        for (c in listOf("kaernten", "lakeland")) {
            val look = CultureLooks.of(c)
            assertEquals("$c's mountains", look.variant("alps", "range"), MapLook.of(c).range.id)
            assertEquals("$c's camp flag", look.variant("campfire", "flag"), MapLook.of(c).pennant?.id)
        }
        // the village as the fire's scene builds it: the map's houses, church and huts are the FireStyle's
        for ((c, l) in cultures.drop(1)) {
            val look = MapLook.of(c, l.id)
            val fire = FireStyle.of(c, l.id)
            assertEquals("$c's village", fire.id, look.style?.id)
            assertEquals(fire.tower, look.style?.tower)
            assertEquals(fire.thatch, look.style?.thatch)
        }
        // the Friends card flies the town's flag as its watchtower does
        assertEquals("🇦🇹", si.lanisce.lani.data.Towns.flag("kaernten", "de"))
        assertEquals("🇮🇹", si.lanisce.lani.data.Towns.flag("friuli", "it"))
        assertEquals("🇸🇮", si.lanisce.lani.data.Towns.flag("primorska", "sl"))
        assertEquals(Flag.ENGLAND.emoji, si.lanisce.lani.data.Towns.flag("lakeland", "en"))
        assertEquals("🇩🇪", si.lanisce.lani.data.Towns.flag("", "de"))
        assertEquals("🇩🇪", si.lanisce.lani.data.Towns.flag("somewhere-new", "de"))
    }

    // ---------------------------------------------------------------- every building still there to tap

    @Test fun `every building of every culture's map is drawn and tappable at every age`() {
        val failed = ArrayList<String>()
        for (c in cultures) for (age in Age.entries) {
            val s = village(age, c.second)
            for ((k, f) in listOf(1 to day, 2 to night)) {
                val w = 360; val h = 640
                for (b in s.buildings.filter { it.type.onPlot }) {
                    val (px, py) = plotAt(s, b.plot, w, h, z = spriteHeight(b.type) * 0.35f)
                    val r = VillageRenderer()
                    val frame = (if (k == 1) f else f.copy(lens = lensAt(2, w, h, px, py))).copy(culture = c.first)
                    r.render(PixelCanvas(w, h), s, frame)
                    // a tap on the building's body finds it (within a few pixels of its middle)
                    val lens = r.lens
                    val tx = lens.cx(px.toFloat()).toInt(); val ty = lens.cy(py.toFloat()).toInt()
                    val found = (-3..3).any { dy -> (-3..3).any { dx -> (r.hitTest(tx + dx * k, ty + dy * k) as? VillageHit.OnBuilding)?.building?.id == b.id } }
                    if (!found) failed += "${label(c)} $age ${b.type} k$k"
                }
            }
        }
        assertEquals(emptyList<String>(), failed)
    }

    // ---------------------------------------------------------------- the contact sheets

    @Test fun `render every culture's map at every age by day and at night`() {
        val w = 360; val h = 800
        val comp = VillageLayout.composition(w, h, false)
        val crop = intArrayOf(0, comp.horizon - 75, w, 360)
        // each culture: a row per age, by day, at dusk in autumn, at night, in winter
        for (c in cultures) {
            val cells = ArrayList<Pair<PixelCanvas, IntArray>>()
            for (age in Age.entries) {
                val s = village(age, c.second)
                for (f in listOf(day, Frame(time = 4.0, hour = 18.6f, month = 10, today = "2026-10-12"), night, Frame(time = 5.0, hour = 12f, month = 1, today = "2026-01-15"))) {
                    cells += render(s, f.copy(culture = c.first), w, h) to crop
                }
            }
            sheet("00-${label(c)}-ages", cells, cols = 4)
        }
        // before and after: the default culture's (what every culture drew before) beside each one's, the town by day
        val cells = ArrayList<Pair<PixelCanvas, IntArray>>()
        for (age in listOf(Age.ZASELEK, Age.VAS, Age.MESTO)) for (c in cultures) cells += render(village(age, c.second), day.copy(culture = c.first), w, h) to crop
        sheet("00-before-after", cells, cols = cultures.size)
    }

    @Test fun `render each building of each culture closer up`() {
        val w = 156; val h = 174
        // one of each kind, alone on its plot of a town (level 2: the tent's flag), each culture on its own land
        val kinds = order.distinct()
        for ((name, f) in listOf(
            "day" to day, "night" to night, "winter" to Frame(time = 3.0, hour = 12f, month = 1, today = "2026-01-15"),
            "autumn" to Frame(time = 3.0, hour = 16f, month = 10, today = "2026-10-12"),
        )) {
            val cells = ArrayList<Pair<PixelCanvas, IntArray>>()
            for (c in cultures) {
                val town = village(Age.MESTO, c.second, projects = false)
                for (t in kinds) {
                    val b = town.buildings.first { it.type == t }
                    // nobody about but one (the village's people would stand in front of it)
                    val s = town.copy(buildings = listOf(b.copy(level = 2), Building("p", PALISADE, NO_PLOT)), villagers = 1)
                    val (px, py) = plotAt(s, b.plot, 360, 640, z = spriteHeight(t) * 0.5f)
                    val lens = Lens(3, (px * 3 - w / 2).coerceIn(0, 360 * 3 - w), (py * 3 - h / 2).coerceIn(0, 640 * 3 - h), 360, 640)
                    cells += render(s, f.copy(culture = c.first, lens = lens), w, h) to intArrayOf(0, 0, w, h)
                }
            }
            sheet("01-buildings-$name", cells, cols = kinds.size)
        }
    }

    @Test fun `render each culture's landmarks closer up`() {
        val w = 192; val h = 192
        for ((name, f) in listOf("may" to day.copy(month = 5, today = "2026-05-10"), "september" to day.copy(month = 9, today = "2026-09-12", hour = 15f), "night" to night.copy(month = 9, today = "2026-09-12"))) {
            val cells = ArrayList<Pair<PixelCanvas, IntArray>>()
            for (c in cultures) {
                val s = village(Age.MESTO, c.second).copy(villagers = 2)
                val comp = VillageLayout.composition(360, 640, false)
                for (sp in LandmarkLayout.of(s, 360, 640).sortedBy { it.landmark.id }) {
                    val cx = sp.x + sp.w / 2f; val cy = sp.y + sp.d / 2f
                    val px = (comp.fireX + (cx - cy) * 4f).toInt(); val py = (comp.fireY + (cx + cy) * 2f - 10f).toInt()
                    val lens = Lens(3, (px * 3 - w / 2).coerceIn(0, 360 * 3 - w), (py * 3 - h / 2).coerceIn(0, 640 * 3 - h), 360, 640)
                    cells += render(s, f.copy(culture = c.first, lens = lens), w, h) to intArrayOf(0, 0, w, h)
                }
            }
            sheet("03-landmarks-$name", cells, cols = cells.size / cultures.size)
        }
    }

    @Test fun `render each culture's feasts`() {
        val w = 360; val h = 800
        val comp = VillageLayout.composition(w, h, false)
        val crop = intArrayOf(0, comp.horizon - 75, w, 360)
        val cells = ArrayList<Pair<PixelCanvas, IntArray>>()
        for (c in cultures) {
            val s = village(Age.MESTO, c.second)
            // the celebration's decorations, all up
            val festive = Festive(decor = Decor.entries.toList(), march = 0.55f)
            cells += render(s, day.copy(culture = c.first, festive = festive, today = "2026-05-10", month = 5), w, h) to crop
            cells += render(s, night.copy(culture = c.first, festive = festive, today = "2026-05-10", month = 5), w, h) to crop
            cells += render(s.copy(event = GameEvent("e", EventKind.FESTIVAL, 3, 0, 0)), day.copy(culture = c.first, month = 9, today = "2026-09-12"), w, h) to crop
        }
        sheet("02-feasts", cells, cols = 3)
    }

    @Test fun `a frame of a culture's map costs no more than the default's`() {
        // a town at night (every window lit) on a phone upright; each culture's best of a few rounds, taken in turn, so
        // the shared machine's load falls on all of them alike
        val s = village(Age.MESTO, Landscape.VALLEY)
        val names = listOf("primorska", "friuli", "kaernten", "lakeland")
        val renderers = names.associateWith { VillageRenderer() }
        val canvases = names.associateWith { PixelCanvas(360, 800) }
        val best = names.associateWith { Double.MAX_VALUE }.toMutableMap()
        repeat(6) { round ->
            for (c in names) {
                val f = night.copy(culture = c, time = 3.0 + round)
                val t0 = System.nanoTime()
                repeat(3) { renderers.getValue(c).render(canvases.getValue(c), s, f.copy(time = f.time + it * 0.1)) }
                val t = (System.nanoTime() - t0) / 3e6
                if (round > 0) best[c] = minOf(best.getValue(c), t)
            }
        }
        val base = best.getValue("primorska")
        for (c in names.drop(1)) {
            val t = best.getValue(c)
            println("frame ${"%.2f".format(t)} ms in $c, ${"%.2f".format(base)} ms in primorska")
            assertTrue("$c's frame $t ms against $base", t < base * 1.25 + 1.0)
        }
    }
}
