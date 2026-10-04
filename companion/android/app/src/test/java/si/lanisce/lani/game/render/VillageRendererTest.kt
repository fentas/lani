package si.lanisce.lani.game.render

import org.junit.Assert.assertArrayEquals
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
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameEvent
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.PLOTS_PER_AGE
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * Renders scenario states to PNG snapshots (build/village-snapshots, 4x nearest-neighbour) for visual review,
 * and checks layout invariants, determinism, hit testing and frame time.
 */
class VillageRendererTest {

    private fun village(age: Age, vararg types: BuildingType, villagers: Int = 6, morale: Int = 70, fire: Int = 70, event: EventKind? = null, damaged: Set<Int> = emptySet()) =
        GameState(
            seed = 42,
            age = age,
            villagers = villagers,
            morale = morale,
            fire = fire,
            buildings = types.mapIndexed { i, t -> Building(id = "b$i", type = t, plot = i, damaged = i in damaged, level = if (i % 4 == 3) 2 else 1) },
            event = event?.let { GameEvent("e", it, 3, 0, 0) },
        )

    private val hamlet = arrayOf(FIELD, HUT, WELL, KOZOLEC, TENT, PALISADE, HUT, FIELD)
    private val villageTypes = arrayOf(FIELD, HUT, WELL, KOZOLEC, HOUSE, PALISADE, LIPA, CHURCH, BEEHIVE, SMITHY, HOUSE, WATCHTOWER, FIELD, HOUSE)
    private val trgTypes = villageTypes + arrayOf(MARKET, HOUSE, SCHOOL, KOZOLEC, HOUSE, BEEHIVE)
    private val townTypes = trgTypes + arrayOf(HOUSE, WELL, HOUSE, MARKET)

    /** A scenario on a canvas: by default a phone on its side's (SceneFit.town(2400, 1080), the classic framing); the compact ones the Home header's strip. */
    private data class Shot(val name: String, val state: GameState, val frame: Frame, val w: Int = 400, val h: Int = 180)

    private val shots = listOf(
        Shot("01-campfire-dusk", village(Age.OGENJ, villagers = 2), Frame(time = 3.0, hour = 19.4f, month = 9)),
        Shot("02-camp-day", village(Age.TABOR, TENT, TENT, TENT, villagers = 4), Frame(time = 5.0, hour = 11f, month = 6)),
        Shot("03-hamlet-autumn-afternoon", village(Age.ZASELEK, *hamlet, villagers = 7), Frame(time = 7.0, hour = 15.5f, month = 9)),
        Shot("04-village-night", village(Age.VAS, *villageTypes, villagers = 10), Frame(time = 2.0, hour = 22.5f, month = 9)),
        Shot("05-market-town-winter", village(Age.TRG, *trgTypes, villagers = 14), Frame(time = 4.0, hour = 12.5f, month = 1)),
        Shot("06-town-golden-hour", village(Age.MESTO, *townTypes, villagers = 20), Frame(time = 6.0, hour = 18.75f, month = 9)),
        Shot("07-wolves-night", village(Age.VAS, *villageTypes, event = EventKind.WOLVES), Frame(time = 1.0, hour = 23f, month = 9)),
        Shot("08-storm", village(Age.ZASELEK, *hamlet, event = EventKind.STORM), Frame(time = 5.3 * 3 + 0.05, hour = 14f, month = 9)),
        Shot("09-festival-evening", village(Age.VAS, *villageTypes, villagers = 12, event = EventKind.FESTIVAL), Frame(time = 3.0, hour = 20.6f, month = 9)),
        Shot("10-merchant", village(Age.TRG, *trgTypes, event = EventKind.MERCHANT), Frame(time = 10.0, hour = 10f, month = 9)),
        Shot("11-compact-town-golden", village(Age.MESTO, *townTypes, villagers = 16), Frame(time = 6.0, hour = 18.75f, month = 9, compact = true), 360, 234),
        Shot("12-compact-camp-night", village(Age.TABOR, TENT, FIELD, TENT, villagers = 3), Frame(time = 2.0, hour = 21.5f, month = 9, compact = true), 360, 234),
        Shot(
            "13-damaged-and-building",
            village(Age.VAS, *villageTypes, damaged = setOf(4, 1)),
            Frame(time = 8.0, hour = 13f, month = 9, justBuilt = "b7", justBuiltProgress = 0.45f, highlightPlot = 12),
        ),
        Shot("14-bear-spring-morning", village(Age.ZASELEK, *hamlet, event = EventKind.BEAR, morale = 30), Frame(time = 4.0, hour = 8f, month = 5)),
        Shot("15-celebrate", village(Age.VAS, *villageTypes), Frame(time = 4.0, hour = 21f, month = 9, celebrate = 0.5f)),
        Shot("16-compact-village-day", village(Age.VAS, *villageTypes, villagers = 10), Frame(time = 3.0, hour = 11f, month = 6, compact = true), 360, 234),
        Shot("17-compact-hamlet-tall-winter", village(Age.ZASELEK, *hamlet), Frame(time = 3.0, hour = 12.5f, month = 1, compact = true), 360, 264),
        Shot(
            "19-upgraded-town-day",
            village(Age.MESTO, *townTypes, villagers = 18).let { v -> v.copy(buildings = v.buildings.map { it.copy(level = 1 + it.plot % 5) }) },
            Frame(time = 2.0, hour = 11f, month = 6),
        ),
        Shot("18-compact-campfire-widget", village(Age.OGENJ, villagers = 2, event = EventKind.WOLVES), Frame(time = 3.0, hour = 17f, month = 10, compact = true), 200, 94),
    )

    @Test fun `render all snapshots`() {
        val dir = File("build/village-snapshots").apply { mkdirs() }
        val r = VillageRenderer()
        for (s in shots) {
            val c = PixelCanvas(s.w, s.h)
            r.render(c, s.state, s.frame)
            write(c, File(dir, "${s.name}.png"))
        }
    }

    /** Real device views, rendered through the same fit (canvas size, whole-number scale, crop) as VillageScene. */
    private data class ViewShot(val name: String, val state: GameState, val frame: Frame, val viewW: Int, val viewH: Int)

    private val viewShots = listOf(
        // Pixel 7 (1080×2400): the village screen's scene, the Home header (about 1080×790 with its greeting, pills and
        // chip: the whole village across), and a phone on its side.
        ViewShot("20-view-1080x1188-camp-wolves", village(Age.TABOR, TENT, TENT, villagers = 2, event = EventKind.WOLVES), Frame(time = 3.0, hour = 17.8f, month = 10), 1080, 1188),
        ViewShot("21-view-1080x700-home-header", village(Age.OGENJ, villagers = 2), Frame(time = 3.0, hour = 17.8f, month = 10, compact = true), 1080, 700),
        ViewShot("22-view-2400x1080-landscape-town", village(Age.MESTO, *townTypes, villagers = 16), Frame(time = 6.0, hour = 11f, month = 7), 2400, 1080),
        ViewShot("26-view-1080x790-home-header-hamlet", village(Age.ZASELEK, *hamlet, villagers = 7), Frame(time = 3.0, hour = 11f, month = 6, compact = true), 1080, 790),
        ViewShot("27-view-1080x790-home-header-village", village(Age.VAS, *villageTypes, villagers = 10), Frame(time = 3.0, hour = 11f, month = 6, compact = true), 1080, 790),
        ViewShot("28-view-1080x790-home-header-town", village(Age.MESTO, *townTypes, villagers = 16), Frame(time = 3.0, hour = 11f, month = 6, compact = true), 1080, 790),
    )

    @Test fun `render device-size views`() {
        val dir = File("build/village-snapshots").apply { mkdirs() }
        for (v in viewShots) {
            val fit = if (v.frame.compact) SceneFit.of(v.viewW, v.viewH, true) else SceneFit.town(v.viewW, v.viewH)
            val c = PixelCanvas(fit.width, fit.height)
            VillageRenderer().render(c, v.state, v.frame)
            val img = BufferedImage(v.viewW, v.viewH, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until v.viewH) for (x in 0 until v.viewW) {
                val cx = (x - fit.offX) / fit.scale; val cy = (y - fit.offY) / fit.scale
                img.setRGB(x, y, c.pixels[cy.coerceIn(0, fit.height - 1) * fit.width + cx.coerceIn(0, fit.width - 1)])
            }
            ImageIO.write(img, "png", File(dir, "${v.name}.png"))
        }
    }

    /** The full-screen town on a 1080×2400 phone: the fitted canvas, with people waiting at their places. */
    @Test fun `render full-screen town views`() {
        val dir = File("build/village-snapshots").apply { mkdirs() }
        val fit = SceneFit.town(1080, 2400)
        val visitors = listOf(si.lanisce.lani.game.scene.TownPlace.Fire, si.lanisce.lani.game.scene.TownPlace.Forest, si.lanisce.lani.game.scene.TownPlace.At(HOUSE))
        val views = listOf(
            "23-town-1080x2400-camp-evening" to (village(Age.OGENJ, TENT, TENT, villagers = 2) to Frame(time = 3.0, hour = 18.4f, month = 9, visitors = visitors)),
            "24-town-1080x2400-village-day" to (village(Age.VAS, *villageTypes, villagers = 10) to Frame(time = 3.0, hour = 11f, month = 6, visitors = visitors)),
            "25-town-1080x2400-town-night-wolves" to (village(Age.MESTO, *townTypes, villagers = 16, event = EventKind.WOLVES) to Frame(time = 3.0, hour = 22.5f, month = 12, visitors = visitors)),
        )
        for ((name, v) in views) {
            val c = PixelCanvas(fit.width, fit.height)
            VillageRenderer().render(c, v.first, v.second)
            write(c, File(dir, "$name.png"), scale = 2)
        }
    }

    /** A phone: its screen, and how much the HUD and the bottom bar cover (screen px). */
    private data class Phone(val name: String, val w: Int, val h: Int, val hud: Int, val bar: Int)

    private val phones = listOf(Phone("1080x2400", 1080, 2400, 300, 230), Phone("720x1600", 720, 1600, 200, 160), Phone("2400x1080", 2400, 1080, 300, 230))

    private val forestVisitors = listOf(si.lanisce.lani.game.scene.TownPlace.Fire, si.lanisce.lani.game.scene.TownPlace.Forest)

    private val startViews = listOf(
        "30-start-camp-autumn-evening" to (village(Age.OGENJ, TENT, TENT, villagers = 2) to Frame(time = 3.0, hour = 18.4f, month = 9, visitors = forestVisitors)),
        "31-start-hamlet-spring-morning" to (village(Age.ZASELEK, *hamlet, villagers = 7) to Frame(time = 5.0, hour = 8f, month = 5)),
        "32-start-village-summer-noon" to (village(Age.VAS, *villageTypes, villagers = 10) to Frame(time = 3.0, hour = 12.5f, month = 7, visitors = forestVisitors)),
        "33-start-village-summer-night" to (village(Age.VAS, *villageTypes, villagers = 10) to Frame(time = 4.0, hour = 23f, month = 7)),
        "34-start-town-winter-day" to (village(Age.MESTO, *townTypes, villagers = 16) to Frame(time = 6.0, hour = 13f, month = 1)),
        "35-start-town-autumn-golden-wolves" to (village(Age.MESTO, *townTypes, villagers = 16, event = EventKind.WOLVES) to Frame(time = 6.0, hour = 18f, month = 10)),
        "36-start-camp-november-dusk" to (village(Age.TABOR, TENT, FIELD, TENT, villagers = 3) to Frame(time = 2.0, hour = 17.2f, month = 11)),
    )

    /**
     * The full-screen town on phones through the real fit ([SceneFit.town]) and the real start view (the camera
     * framing the clearing and the meadow in the part of the screen the HUD and the bar leave free): the fitted
     * canvas at 2×, and the start view at half the screen size with the covered bands darkened.
     */
    @Test fun `render phone start views`() {
        val dir = File("build/village-snapshots").apply { mkdirs() }
        for ((name, v) in startViews) for (p in phones) {
            if (p.name != "1080x2400" && name !in setOf("30-start-camp-autumn-evening", "32-start-village-summer-noon")) continue
            val fit = SceneFit.town(p.w, p.h)
            val c = PixelCanvas(fit.width, fit.height)
            val r = VillageRenderer()
            r.render(c, v.first, v.second)
            if (p.name == "1080x2400") write(c, File(dir, "$name-canvas.png"), scale = 2)
            val rig = CameraRig.town(fit, p.w, p.h)
            val area = si.lanisce.lani.ui.game.freeArea(p.w.toFloat(), p.h.toFloat(), p.hud.toFloat(), p.bar.toFloat(), 32f)
            val home = rig.frame(TownAnchors.home(v.first, fit.width, fit.height), area, maxFrame = rig.minScale * 2, above = si.lanisce.lani.ui.game.HOME_ABOVE)
            val img = BufferedImage(p.w / 2, p.h / 2, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until p.h / 2) for (x in 0 until p.w / 2) {
                val cx = home.toCanvasX(x * 2f).toInt(); val cy = home.toCanvasY(y * 2f).toInt()
                var px = if (cx in 0 until fit.width && cy in 0 until fit.height) c.pixels[cy * fit.width + cx] else r.skyColor
                if (y * 2 < p.hud || y * 2 > p.h - p.bar) px = Col.scale(px, 0.45f)
                img.setRGB(x, y, px)
            }
            ImageIO.write(img, "png", File(dir, "$name-${p.name}.png"))
        }
    }

    @Test fun `scene fit keeps square pixels and covers the view`() {
        for ((w, h, compact) in listOf(Triple(1080, 1188, false), Triple(1080, 700, true), Triple(1080, 790, true), Triple(2400, 1080, false), Triple(720, 400, true), Triple(3000, 200, true))) {
            val f = SceneFit.of(w, h, compact)
            assertTrue("covers $w×$h: $f", f.width * f.scale >= w && f.height * f.scale >= h)
            assertTrue("crops under a pixel: $f", -f.offX < f.scale && -f.offY < f.scale && f.offX <= 0 && f.offY <= 0)
            assertTrue("no stretch beyond the caps: $f", f.width <= (if (compact) 480 else 360) && f.height <= (if (compact) 270 else 320) || f.width == 16 || f.height == 16)
            // the Home header's strip: the whole village across
            if (compact) assertTrue("the whole village across: $f", f.width >= SceneFit.TOWN_W)
        }
        assertEquals(SceneFit(16, 16, 1, -7, -7), SceneFit.of(1, 1, false)) // a degenerate view still gets a usable canvas
        assertEquals(SceneFit(270, 297, 4, 0, 0), SceneFit.of(1080, 1188, false))
        assertEquals(SceneFit(360, 264, 3, 0, -1), SceneFit.of(1080, 790, true))
    }

    @Test fun `plots are distinct, non-overlapping and ordered outward`() {
        val cs = Terrain.CLASSIC.centers
        assertEquals(VillageLayout.MAX_PLOTS, cs.size)
        assertEquals(PLOTS_PER_AGE.last(), cs.size)
        for (i in cs.indices) for (j in i + 1 until cs.size) {
            val overlap = kotlin.math.abs(cs[i][0] - cs[j][0]) < 3.5f && kotlin.math.abs(cs[i][1] - cs[j][1]) < 3.5f
            assertTrue("plots $i and $j overlap", !overlap)
        }
        assertTrue(cs[0][2] <= cs[10][2] && cs[10][2] <= cs[27][2])
        // off the stream and its banks, clear of where people sit by the water, off the road
        val bank = Terrain.CLASSIC.riverbank
        for ((i, c) in cs.withIndex()) {
            assertTrue("plot $i in the stream", kotlin.math.abs(c[0] - Terrain.CLASSIC.streamX(c[1])) >= 3.3f)
            assertTrue("plot $i on the stream bank's spot", kotlin.math.abs(c[0] - bank[0]) >= 2.6f || kotlin.math.abs(c[1] - bank[1]) >= 2.6f)
            assertTrue("plot $i on the road", kotlin.math.abs(c[1]) - 1.5f >= 1f)
        }
        // every age's clearing is bigger than the one before
        for (a in 1 until Age.entries.size) assertTrue(Terrain.CLASSIC.clearing(Age.entries[a]) > Terrain.CLASSIC.clearing(Age.entries[a - 1]))
    }

    /** The first pixels of each building type drawn alone, relative to its plot's middle on the ground (nominal px). */
    private val silhouettes: Map<BuildingType, Set<Long>> by lazy {
        BuildingType.entries.filter { it.onPlot }.associateWith { t ->
            val w = 480; val h = 300
            val r = VillageRenderer()
            val s = GameState(seed = 42, age = Age.MESTO, villagers = 0, buildings = listOf(Building("b", t, 0)))
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

    /**
     * Wherever two plots stand one in front of the other, a house, a hayrack, a smithy, a school, a hut or the church on the
     * front one never hides most of a beehive, a tent, a well, a market or a hut on the back one (see [VillageLayout]).
     */
    @Test fun `a tall building in front never hides most of a small one behind`() {
        val fronts = listOf(HOUSE, KOZOLEC, SMITHY, SCHOOL, HUT, CHURCH)
        val backs = listOf(BEEHIVE, TENT, WELL, MARKET, HUT)
        val cs = Terrain.CLASSIC.centers
        fun at(c: FloatArray) = intArrayOf(((c[0] - c[1]) * 4f).roundToInt(), ((c[0] + c[1]) * 2f).roundToInt())
        var worst = 0f; var what = ""
        for (i in cs.indices) for (j in cs.indices) {
            if (cs[j][0] + cs[j][1] <= cs[i][0] + cs[i][1]) continue // j stands in front of i
            val bi = at(cs[i]); val bj = at(cs[j])
            for (f in fronts) {
                val front = silhouettes.getValue(f)
                for (b in backs) {
                    val back = silhouettes.getValue(b)
                    var hidden = 0
                    for (p in back) {
                        val x = (p shr 32).toInt() + bi[0] - bj[0]; val y = p.toInt() + bi[1] - bj[1]
                        if (key(x, y) in front) hidden++
                    }
                    val share = hidden.toFloat() / back.size
                    if (share > worst) { worst = share; what = "$f on plot $j over $b on plot $i" }
                }
            }
        }
        println("most hidden: %.0f %% (%s)".format(worst * 100f, what))
        assertTrue("$what: ${(worst * 100).roundToInt()} % hidden", worst < 0.5f)
    }

    /** At the home view of a phone, every building of a village of each age shows most of itself. */
    @Test fun `no building is mostly hidden in the villages of every age`() {
        val fit = SceneFit.town(1080, 2400)
        val frame = Frame(time = 2.0, hour = 12f, month = 6)
        fun shown(s: GameState): Map<String, Int> {
            val r = VillageRenderer().also { it.render(PixelCanvas(fit.width, fit.height), s, frame) }
            val out = HashMap<String, Int>()
            for (y in 0 until fit.height) for (x in 0 until fit.width) (r.hitTest(x, y, 0) as? VillageHit.OnBuilding)?.let { out.merge(it.building.id, 1, Int::plus) }
            return out
        }
        for ((age, types) in listOf(Age.TABOR to arrayOf(TENT, FIELD, TENT, HUT, WELL), Age.ZASELEK to hamlet, Age.VAS to villageTypes, Age.TRG to trgTypes, Age.MESTO to townTypes)) {
            val s = village(age, *types, villagers = 0)
            val together = shown(s)
            for (b in s.buildings.filter { it.type.onPlot }) {
                val alone = shown(s.copy(buildings = listOf(b)))[b.id] ?: 0
                val share = (together[b.id] ?: 0).toFloat() / alone.coerceAtLeast(1)
                assertTrue("$age: ${b.type} on plot ${b.plot} shows ${(share * 100).roundToInt()} %", share >= 0.6f)
            }
        }
    }

    @Test fun `rendering is deterministic`() {
        val s = shots.first { it.name.startsWith("06") }
        val a = PixelCanvas(s.w, s.h); val b = PixelCanvas(s.w, s.h)
        VillageRenderer().render(a, s.state, s.frame)
        VillageRenderer().render(b, s.state, s.frame)
        assertArrayEquals(a.pixels, b.pixels)
        // and animation actually changes pixels over time
        val c = PixelCanvas(s.w, s.h)
        VillageRenderer().render(c, s.state, s.frame.copy(time = s.frame.time + 0.5))
        assertNotEquals(a.pixels.toList(), c.pixels.toList())
    }

    @Test fun `taps hit buildings and the fire`() {
        val s = shots.first { it.name.startsWith("04") }
        val r = VillageRenderer()
        val c = PixelCanvas(s.w, s.h)
        r.render(c, s.state, s.frame)
        val comp = VillageLayout.composition(s.w, s.h, false)
        assertEquals(VillageHit.OnFire, r.hitTest(comp.fireX, comp.fireY - 3))
        val church = s.state.buildings.first { it.type == CHURCH }
        val rect = r.hitRects().first { it.first?.id == church.id }.second
        // the spire tip is inside the church's rectangle; a tap on it hits the church
        val hit = r.hitTest((rect[0] + rect[2]) / 2, rect[1] + 4)
        assertEquals(VillageHit.OnBuilding(church), hit)
        assertEquals(null, r.hitTest(2, 2))
    }

    @Test fun `town frame renders fast`() {
        val s = shots.first { it.name.startsWith("06") }
        val r = VillageRenderer()
        // a phone on its side
        val c = PixelCanvas(s.w, s.h)
        repeat(20) { r.render(c, s.state, s.frame.copy(time = it / 12.0)) }
        val n = 60
        val t0 = System.nanoTime()
        repeat(n) { r.render(c, s.state, s.frame.copy(time = 2 + it / 12.0, hour = 22f)) }
        val ms = (System.nanoTime() - t0) / 1e6 / n
        println("VillageRenderer: %.2f ms/frame (town, night, %dx%d)".format(ms, s.w, s.h))
        assertTrue("too slow: $ms ms", ms < 15.0)
        // a phone's village screen (1080×1188 px) renders a taller canvas
        val tall = PixelCanvas(270, 297)
        repeat(10) { r.render(tall, s.state, s.frame.copy(time = it / 12.0)) }
        val t1 = System.nanoTime()
        repeat(n) { r.render(tall, s.state, s.frame.copy(time = 2 + it / 12.0, hour = 22f)) }
        val msTall = (System.nanoTime() - t1) / 1e6 / n
        println("VillageRenderer: %.2f ms/frame (town, night, 270x297)".format(msTall))
        assertTrue("too slow: $msTall ms", msTall < 30.0)
        // the full-screen town on a portrait phone renders the fitted canvas, 360×800
        val fit = SceneFit.town(1080, 2400)
        val full = PixelCanvas(fit.width, fit.height)
        repeat(10) { r.render(full, s.state, s.frame.copy(time = it / 12.0)) }
        val t2 = System.nanoTime()
        repeat(n) { r.render(full, s.state, s.frame.copy(time = 2 + it / 12.0, hour = 22f)) }
        val msFull = (System.nanoTime() - t2) / 1e6 / n
        println("VillageRenderer: %.2f ms/frame (town, night, %dx%d)".format(msFull, fit.width, fit.height))
        assertTrue("too slow: $msFull ms", msFull < 50.0)
    }

    private fun write(c: PixelCanvas, f: File, scale: Int = 4) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", f)
    }
}
