package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.BuildingType.*
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.HearthDay
import si.lanisce.lani.game.Hearths
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.culture.Cultures
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The hearths on the map (companion/GAME.md "Hearths"): the centre by the age (the campfire, the fire ring with its benches,
 * the square's fire basket, the kres of a feast evening, the town's lanterns), smoke from the chimneys of the hearths lit by
 * day, warm windows at night and dark ones where a hearth is out, the forge's glow on its charcoal and cold without. Renders
 * them for review (build/village-snapshots/hearths, hearths-…png) and checks each look differs where it should, and that the centre
 * stays the place to tap whatever it looks like.
 */
class HearthsRenderTest {
    private val dir = File("build/village-snapshots/hearths").apply { mkdirs() }
    private val fit = SceneFit.town(1080, 2400)
    private val comp = VillageLayout.composition(fit.width, fit.height, false)
    private val june = "2026-06-24"

    @Before fun primorska() { Cultures.use("primorska") }

    private fun village(age: Age, types: List<BuildingType>, out: List<String> = emptyList(), lastFeast: String = "") = GameState(
        seed = 42, age = age, villagers = 8, morale = 80, resources = mapOf(Res.WOOD to 500),
        buildings = types.mapIndexed { i, t -> Building("b$i", t, i, builtAt = i.toLong()) },
        hearths = HearthDay(on = june, out = out, since = "2026-01-01"), lastTick = june, lastFeast = lastFeast,
    )

    private val tabor = listOf(TENT, TENT, FIELD, HUT)
    private val zaselek = listOf(HUT, FIELD, TENT, HUT, HOUSE, LIPA, SMITHY, SCHOOL, CHURCH, WELL)
    private val vas = listOf(HUT, FIELD, WELL, TENT, KOZOLEC, HOUSE, LIPA, BEEHIVE, HOUSE, SMITHY, MARKET, HOUSE, SCHOOL, WATCHTOWER)
    private val trg = vas + listOf(HOUSE, CHURCH, HOUSE, BEEHIVE, FIELD, HOUSE)

    private fun render(s: GameState, hour: Float, month: Int = 6): Pair<PixelCanvas, VillageRenderer> {
        val c = PixelCanvas(fit.width, fit.height)
        val r = VillageRenderer()
        r.render(c, s, Frame(time = 3.0, hour = hour, month = month, today = june))
        return c to r
    }

    /** The pixels of [c] in [x0, y0] … [x1, y1]. */
    private fun crop(c: PixelCanvas, x0: Int, y0: Int, x1: Int, y1: Int): IntArray {
        val w = x1 - x0; val h = y1 - y0
        return IntArray(w * h) { i -> c.pixels[(y0 + i / w).coerceIn(0, c.height - 1) * c.width + (x0 + i % w).coerceIn(0, c.width - 1)] }
    }

    /** The centre and round it (the fire's middle ± 36 × 24 px). */
    private fun centre(c: PixelCanvas) = crop(c, comp.fireX - 36, comp.fireY - 40, comp.fireX + 36, comp.fireY + 14)

    /** [b]'s sprite as the last frame drew it, and [above] rows over it (its smoke). */
    private fun building(c: PixelCanvas, r: VillageRenderer, id: String, above: Int = 0): IntArray {
        val rect = r.hitRects().first { it.first?.id == id }.second
        return crop(c, rect[0] - 4, rect[1] - above, rect[2] + 8, rect[3])
    }

    private fun write(c: PixelCanvas, name: String, cx: Int = comp.fireX, cy: Int = comp.fireY - 6, rx: Int = 110, ry: Int = 70, scale: Int = 4) {
        val x0 = (cx - rx).coerceIn(0, c.width - 1); val y0 = (cy - ry).coerceIn(0, c.height - 1)
        val w = minOf(2 * rx, c.width - x0); val h = minOf(2 * ry, c.height - y0)
        val img = BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h * scale) for (x in 0 until w * scale) img.setRGB(x, y, c.pixels[(y0 + y / scale) * c.width + x0 + x / scale])
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    @Test fun `the centre by the age, still the place to tap`() {
        val looks = linkedMapOf(
            "tabor" to (village(Age.TABOR, tabor) to 11f),
            "zaselek" to (village(Age.ZASELEK, zaselek) to 11f),
            "vas-day" to (village(Age.VAS, vas) to 11f),
            "vas-evening" to (village(Age.VAS, vas) to 19f),
            "trg-day" to (village(Age.TRG, trg) to 11f),
            "trg-evening" to (village(Age.TRG, trg) to 19f),
            "trg-feast" to (village(Age.TRG, trg, lastFeast = june) to 21f),
            "mesto-night" to (village(Age.MESTO, trg + listOf(HOUSE, HOUSE)) to 22f),
        )
        val drawn = HashMap<String, IntArray>()
        for ((name, v) in looks) {
            val (s, hour) = v
            val (c, r) = render(s, hour)
            write(c, "hearths-centre-$name")
            drawn[name] = centre(c)
            // whatever burns there now, a tap in its middle is the centre's
            assertEquals(name, VillageHit.OnFire, r.hitTest(comp.fireX, comp.fireY - 3, 1))
        }
        // each age's centre looks unlike the one before; the ring and the basket burn in the evening, embers by day; the kres
        fun differ(a: String, b: String) = assertFalse("$a vs $b", drawn.getValue(a).contentEquals(drawn.getValue(b)))
        differ("tabor", "vas-day"); differ("vas-day", "trg-day"); differ("vas-day", "vas-evening"); differ("trg-day", "trg-evening")
        differ("trg-evening", "trg-feast")
    }

    /** [s] at [hour] close up (detail [k]) round world cell ([wx], [wy]): a 120 × 80 nominal px window. */
    private fun closeUp(s: GameState, hour: Float, month: Int, wx: Float, wy: Float, name: String, k: Int = 3): IntArray {
        val rig = CameraRig.town(fit, 1080, 2400)
        val px = comp.fireX + (wx - wy) * 4f; val py = comp.fireY + (wx + wy) * 2f
        val cam = rig.lookAt(px, py, (rig.minScale * k).toFloat())
        val lens = Lens.of(k, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
        val c = PixelCanvas(fit.width, fit.height)
        VillageRenderer().render(c, s, Frame(time = 3.0, hour = hour, month = month, today = june, lens = lens))
        val cx = lens.cx(px).toInt(); val cy = lens.cy(py - 10f).toInt()
        write(c, name, cx, cy, 60 * k, 40 * k, 1)
        return crop(c, cx - 60 * k, cy - 40 * k, cx + 60 * k, cy + 40 * k)
    }

    @Test fun `smoke from the chimneys of the hearths lit, warm windows at night, dark and still where a hearth is out`() {
        val lit = village(Age.VAS, vas)
        val house = lit.buildings.last { it.type == HOUSE }.id
        val hut = lit.buildings.first { it.type == HUT }.id
        // short of wood: the outlying house, the school's neighbours and the centre's evening fire out (as the tick leaves them)
        val short = village(Age.VAS, vas, out = listOf(house, Hearths.CENTRE))
        val (dayLit, rl) = render(lit, 11f)
        val (dayOut, ro) = render(short, 11f)
        write(dayLit, "hearths-vas-day-all-lit")
        write(dayOut, "hearths-vas-day-short")
        val at = VillageLayout.of(lit).origin(lit.buildings.last { it.type == HOUSE }.plot)
        val near = closeUp(lit, 11f, 6, at[0] + 1.5f, at[1] + 1.5f, "hearths-house-day-lit")
        val far = closeUp(short, 11f, 6, at[0] + 1.5f, at[1] + 1.5f, "hearths-house-day-out")
        assertFalse("smoke over the lit house by day", near.contentEquals(far))
        closeUp(lit, 20.5f, 12, at[0] + 1.5f, at[1] + 1.5f, "hearths-house-night-lit")
        closeUp(short, 20.5f, 12, at[0] + 1.5f, at[1] + 1.5f, "hearths-house-night-out")
        // at night: warm windows in the lit house, dark in the cold one
        val (nightLit, nl) = render(lit, 20.5f, month = 12)
        val (nightOut, no) = render(short, 20.5f, month = 12)
        write(nightLit, "hearths-vas-night-all-lit")
        write(nightOut, "hearths-vas-night-short")
        assertFalse("windows", building(nightLit, nl, house).contentEquals(building(nightOut, no, house)))
        // a hut: its smoke through the roof while its hearth burns
        val hutOut = village(Age.VAS, vas, out = listOf(hut))
        val (h1, r1) = render(lit, 11f)
        val (h2, r2) = render(hutOut, 11f)
        assertFalse("the hut's smoke", building(h1, r1, hut, above = 12).contentEquals(building(h2, r2, hut, above = 12)))
    }

    @Test fun `the forge glows on its charcoal and is cold without`() {
        val lit = village(Age.VAS, vas)
        val smithy = lit.buildings.first { it.type == SMITHY }.id
        val cold = village(Age.VAS, vas, out = listOf(smithy))
        val (a, ra) = render(lit, 21f, month = 11)
        val (b, rb) = render(cold, 21f, month = 11)
        val rect = ra.hitRects().first { it.first?.id == smithy }.second
        write(a, "hearths-forge-lit", (rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2, 40, 30, 6)
        write(b, "hearths-forge-cold", (rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2, 40, 30, 6)
        assertFalse(building(a, ra, smithy, above = 16).contentEquals(building(b, rb, smithy, above = 16)))
        assertFalse(building(a, ra, smithy).contentEquals(building(b, rb, smithy)))
    }

    @Test fun `a village whose hearths weren't decided yet draws every one lit, as the map always did`() {
        val undecided = village(Age.VAS, vas).copy(hearths = null, resources = emptyMap())
        val (a, ra) = render(undecided, 11f)
        val (b, rb) = render(village(Age.VAS, vas), 11f)
        val house = undecided.buildings.last { it.type == HOUSE }.id
        assertTrue(building(a, ra, house, above = 14).contentEquals(building(b, rb, house, above = 14)))
    }
}
