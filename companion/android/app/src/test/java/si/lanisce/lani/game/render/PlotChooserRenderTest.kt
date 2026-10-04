package si.lanisce.lani.game.render

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import si.lanisce.lani.game.BuildingType.PALISADE
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.BuildingType.WELL
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Land
import si.lanisce.lani.game.Landscape
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.Placement
import si.lanisce.lani.game.PlotChoice
import si.lanisce.lani.game.Res
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The plot chooser on the map and what it leaves there (GAME.md "Buildings", "The learner chooses the plot"): a moved
 * building renders exactly as one built on that plot, on the classic valley and a generated land; the chooser marks the
 * free plots, the ★ and the plot chosen with the building shown there; three fields stand side by side. For review in
 * build/village-snapshots/plots-*.png.
 */
class PlotChooserRenderTest {
    private val t0 = 1_000_000L
    private val frame = Frame(time = 3.0, hour = 11f, month = 7)

    /** A village like Jan's, a year on: Vas, the tent at the pond, the field with its kozolec, the square by the fire. */
    private val jans = GameState(
        seed = 42, age = Age.VAS, villagers = 9, morale = 70, fire = 70, resources = Res.entries.associateWith { 999 },
        buildings = listOf(
            Building("b0", TENT, NO_PLOT), Building("b1", FIELD, 1), Building("b2", HUT, 2, level = 2), Building("b3", WELL, 3),
            Building("b4", PALISADE, NO_PLOT), Building("b5", KOZOLEC, 8), Building("b6", BEEHIVE, 5, level = 2),
            Building("b7", CHURCH, 6), Building("b8", HOUSE, 0), Building("b9", LIPA, 4),
        ),
    )

    private fun render(s: GameState, f: Frame = frame, w: Int = 360, h: Int = 800): PixelCanvas =
        PixelCanvas(w, h).also { VillageRenderer().render(it, s, f) }

    @Test fun `a moved building renders as one built there`() {
        for (s in listOf(jans, jans.copy(land = Land.of(Landscape.HILLS, 7)))) {
            val to = Placement.freePlots(s).last()
            val moved = GameEngine.move(s, "b1", to, t0)
            val built = s.copy(buildings = s.buildings.map { if (it.id == "b1") it.copy(plot = to) else it })
            assertArrayEquals("${s.land}", render(built).pixels, render(moved).pixels)
            assertFalse("it moved", render(s).pixels.contentEquals(render(moved).pixels))
            // a swap too: the field and the hut, each on the other's plot
            val swapped = GameEngine.move(s, "b1", 2, t0)
            val both = s.copy(buildings = s.buildings.map { when (it.id) { "b1" -> it.copy(plot = 2); "b2" -> it.copy(plot = 1); else -> it } })
            assertArrayEquals(render(both).pixels, render(swapped).pixels)
        }
    }

    @Test fun `the chooser marks the free plots, the star and the plot chosen`() {
        val choice = PlotChoice.build(jans, FIELD)
        val free = choice.free(jans)
        val star = choice.suggested(jans)!!
        val other = free.first { it != star }
        val picked = choice.pick(jans, other)
        val shown = picked.preview(jans)
        val plain = render(shown)
        val marked = render(shown, frame.copy(plots = picked.marks(jans)))
        val comp = VillageLayout.composition(360, 800, false)
        val land = VillageLayout.of(jans)
        fun at(p: Int, dx: Float = 0f, dy: Float = 0f): Int {
            val c = land.centers[p]
            val x = (comp.fireX + (c[0] - c[1]) * 4f + dx).toInt(); val y = (comp.fireY + (c[0] + c[1]) * 2f + dy).toInt()
            return y * 360 + x
        }
        // every free plot is lit up where it shows
        for (p in free - other) assertTrue("plot $p marked", (-3..3).any { d -> plain.pixels[at(p, dx = d * 2f)] != marked.pixels[at(p, dx = d * 2f)] })
        // the chosen one has the field on it, outlined in gold, a pointer over it
        val round = (-14..14).flatMap { dx -> (-30..8).map { dy -> at(other, dx.toFloat(), dy.toFloat()) } }
        assertTrue("plot $other chosen", round.count { plain.pixels[it] != marked.pixels[it] } > 20)
        // the ★ over the suggested plot: gold pixels a little above it
        val c = land.centers[star]
        val sx = (comp.fireX + (c[0] - c[1]) * 4f).toInt(); val sy = (comp.fireY + (c[0] + c[1]) * 2f).toInt()
        val gold = (sy - 18..sy - 4).sumOf { y -> (sx - 5..sx + 5).count { x -> marked.pixels[y * 360 + x] == Pal.GOLD } }
        assertTrue("the ★ over plot $star ($gold gold px)", gold >= 8)
        // and nothing of it without the chooser
        assertEquals(0, (sy - 18..sy - 4).sumOf { y -> (sx - 5..sx + 5).count { x -> plain.pixels[y * 360 + x] == Pal.GOLD } })
        // a village without the chooser draws as before
        assertArrayEquals(render(jans).pixels, render(jans, frame.copy(plots = null)).pixels)
        assertNull(frame.plots)
    }

    @Test fun `render the plot chooser and fields side by side`() {
        val dir = File("build/village-snapshots").apply { mkdirs() }
        // choosing where a new field goes: the free plots framed, the ★ beside the field, another plot chosen
        val choice = PlotChoice.build(jans, FIELD)
        val picked = choice.pick(jans, choice.free(jans).first { it != choice.suggested(jans) })
        write(render(picked.preview(jans), frame.copy(plots = picked.marks(jans))), File(dir, "plots-chooser.png"))
        write(render(choice.preview(jans), frame.copy(plots = choice.marks(jans))), File(dir, "plots-chooser-star.png"))
        // moving the hut onto the field's plot: the two swap
        val move = PlotChoice.move(jans, jans.buildings.single { it.id == "b2" }).pick(jans, 1)
        write(render(move.preview(jans), frame.copy(plots = move.marks(jans))), File(dir, "plots-swap.png"))
        // three more fields, each on the ★: side by side with the first
        var s = jans
        repeat(3) { s = PlotChoice.build(s, FIELD).confirm(s, t0)!! }
        val fields = s.buildings.filter { it.type == FIELD }.map { it.plot }
        println("plots: fields on $fields")
        for (f in fields) assertTrue("field $f beside another of $fields", Placement.beside(f, fields - f))
        write(render(s), File(dir, "plots-fields.png"))
    }

    /** The village's middle, 4× (as PlacementRenderTest's snapshots). */
    private fun write(c: PixelCanvas, f: File, scale: Int = 4) {
        val comp = VillageLayout.composition(c.width, c.height, false)
        val w = 260.coerceAtMost(c.width); val h = 170.coerceAtMost(c.height)
        val x0 = (comp.fireX - w / 2).coerceIn(0, c.width - w); val y0 = (comp.fireY - h / 2 - 10).coerceIn(0, c.height - h)
        val img = BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h * scale) for (x in 0 until w * scale) img.setRGB(x, y, c.pixels[(y0 + y / scale) * c.width + x0 + x / scale])
        ImageIO.write(img, "png", f)
    }
}
