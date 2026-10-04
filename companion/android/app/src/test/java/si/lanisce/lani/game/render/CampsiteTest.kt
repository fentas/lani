package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.TentMove
import si.lanisce.lani.game.scene.TownPlace
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.hypot

/**
 * The tent at the pond (game/TentMove) on the village map: a campsite in the woods below the village, depth-sorted
 * with them, tappable as the tent, the anchor of the tent's place, its plot free. PNG snapshots in
 * build/village-snapshots (70-tent-…) for review: the village before and after, and the campsite close up.
 */
class CampsiteTest {
    private val dir = File("build/village-snapshots").apply { mkdirs() }
    private val fit = SceneFit.town(1080, 2400)
    private val rig = CameraRig.town(fit, 1080, 2400)
    private val today = "2026-09-24"

    private val types = arrayOf(
        BuildingType.FIELD, BuildingType.HUT, BuildingType.TENT, BuildingType.KOZOLEC, BuildingType.PALISADE, BuildingType.HOUSE, BuildingType.WELL,
    )

    private val before = GameState(
        seed = 42, age = Age.ZASELEK, villagers = 6, morale = 70, fire = 70,
        buildings = types.mapIndexed { i, t -> Building("b$i", t, if (t == BuildingType.PALISADE) NO_PLOT else i) },
    )

    private val after = TentMove.move(before, 0)

    private val tent get() = after.buildings.first { it.type == BuildingType.TENT }

    @Test fun `the moved tent stands at the pond, on no plot, and the others where they were`() {
        assertEquals(NO_PLOT, tent.plot)
        assertEquals(before.buildings.filter { it.type != BuildingType.TENT }, after.buildings.filter { it.type != BuildingType.TENT })
        assertNull(Campsite.of(before, fit.width, fit.height))
        assertNotNull(Campsite.of(after, fit.width, fit.height))
        // no woods on the Home header's short strip: no campsite drawn there
        assertNull(Campsite.of(after, 360, 234))
    }

    @Test fun `the campsite is drawn by the pond and a tap on it is a tap on the tent`() {
        val camp = Campsite.of(after, fit.width, fit.height)!!
        val r = VillageRenderer()
        val c = PixelCanvas(fit.width, fit.height)
        r.render(c, after, Frame(time = 2.0, hour = 11f, month = 6, today = today))
        // the tent's body, and its fire ring
        assertEquals(VillageHit.OnBuilding(tent), r.hitTest(camp.body.x.toInt(), camp.body.y.toInt(), 2))
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val fx = comp.fireX + (camp.fireX - camp.fireY) * 4f; val fy = comp.fireY + (camp.fireX + camp.fireY) * 2f
        assertEquals(VillageHit.OnBuilding(tent), r.hitTest(fx.toInt(), fy.toInt() - 1, 2))
        // it stands on the bank, not in the water, and the pond is still the pond
        val fg = camp.fg
        assertTrue(fg.pondD(camp.x, camp.y) > 1.2f)
        assertEquals(VillageHit.OnSpot("pond"), r.hitTest(fg.pondX.toInt() - 8, fg.pondY.toInt(), 1))
        // the tent is gone from the village: nothing is drawn on its plot any more
        val c0 = PixelCanvas(fit.width, fit.height)
        val r0 = VillageRenderer()
        r0.render(c0, before, Frame(time = 2.0, hour = 11f, month = 6, today = today))
        assertTrue(r0.hitRects().any { it.first?.id == tent.id })
        assertTrue(r.hitRects().none { it.first?.id == tent.id && it.second[1] < camp.y - 40f })
        // and the campsite is drawn: the pixels by the pond changed
        val x0 = (camp.x - 16).toInt(); val y0 = (camp.y - 20).toInt()
        var diff = 0
        for (y in y0 until y0 + 32) for (x in x0 until x0 + 32) if (c.pixels[y * c.width + x] != c0.pixels[y * c.width + x]) diff++
        assertTrue("the campsite changes the pond's bank ($diff px)", diff > 200)
    }

    @Test fun `closer in, the tent at the pond is still the tent to tap`() {
        val camp = Campsite.of(after, fit.width, fit.height)!!
        val r = VillageRenderer()
        for (k in 2..3) {
            val lens = Lens.of(k, rig.lookAt(camp.x, camp.y, (rig.minScale * k).toFloat()), 1080, 2400, fit.width, fit.height, fit.width, fit.height)
            val c = PixelCanvas(fit.width, fit.height)
            r.render(c, after, Frame(time = 2.0, hour = 11f, month = 6, today = today, lens = lens))
            assertEquals("k=$k", VillageHit.OnBuilding(tent), r.hitTest(lens.cx(camp.body.x).toInt(), lens.cy(camp.body.y).toInt(), 2 * k))
        }
    }

    @Test fun `the tent's place points at the campsite, and who waits there waits by its fire`() {
        val camp = Campsite.of(after, fit.width, fit.height)!!
        val at = TownAnchors.of(after, fit.width, fit.height).getValue(TownPlace.At(BuildingType.TENT))
        assertEquals(camp.top, at)
        // before, at the tent's plot in the village
        val was = TownAnchors.of(before, fit.width, fit.height).getValue(TownPlace.At(BuildingType.TENT))
        assertTrue(hypot(was.x - at.x, was.y - at.y) > 40f)
        val comp = VillageLayout.composition(fit.width, fit.height, false)
        val v = TownAnchors.visitorSpot(after, TownPlace.At(BuildingType.TENT), comp, fit.width, fit.height)!!
        val vx = comp.fireX + (v[0] - v[1]) * 4f; val vy = comp.fireY + (v[0] + v[1]) * 2f
        assertTrue("waits by the campsite ($vx, $vy)", hypot(vx - camp.x, vy - camp.y) < 24f)
        // for screen readers: the tent's body at the pond
        val body = TownAnchors.bodies(after, fit.width, fit.height).first { it.first == tent.id }.second
        assertEquals(camp.body, body)
        assertEquals(camp.top, TownAnchors.top(tent, after, fit.width, fit.height))
        // a canvas without the woods: the tent's place is the forest's spot
        val short = TownAnchors.of(after, 400, 180)
        assertEquals(short.getValue(TownPlace.Forest), short.getValue(TownPlace.At(BuildingType.TENT)))
        assertNotEquals(short.getValue(TownPlace.Forest), TownAnchors.of(before, 400, 180).getValue(TownPlace.At(BuildingType.TENT)))
    }

    @Test fun `render the village before and after the tent moved, and the campsite close up`() {
        val camp = Campsite.of(after, fit.width, fit.height)!!
        for ((name, s) in listOf("before" to before, "after" to after)) {
            val c = PixelCanvas(fit.width, fit.height)
            VillageRenderer().render(c, s, Frame(time = 2.0, hour = 11f, month = 6, today = today))
            write(c, File(dir, "70-tent-$name.png"), (camp.x - 110).toInt(), (camp.y - 190).toInt(), 220, 250, 3)
        }
        for ((name, frame) in listOf(
            "day" to Frame(time = 2.0, hour = 11f, month = 6, today = today),
            "evening" to Frame(time = 2.0, hour = 20.2f, month = 9, today = today),
            "night" to Frame(time = 2.0, hour = 23f, month = 7, today = today),
            "winter" to Frame(time = 2.0, hour = 12f, month = 1, today = today),
        )) {
            for (k in 1..3) {
                val cam = rig.lookAt(camp.x, camp.y - 6f, (rig.minScale * k * (if (k == 1) 1.5f else 1f)))
                val lens = Lens.of(k, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
                val c = PixelCanvas(fit.width, fit.height)
                VillageRenderer().render(c, after, frame.copy(lens = lens))
                crop(c, lens, cam, File(dir, "71-tent-at-pond-$name-k$k.png"))
            }
        }
    }

    /** The part [x0], [y0], [w] × [h] of [c], at [scale]. */
    private fun write(c: PixelCanvas, f: File, x0: Int, y0: Int, w: Int, h: Int, scale: Int) {
        val img = BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h * scale) for (x in 0 until w * scale) {
            val px = (x0 + x / scale).coerceIn(0, c.width - 1); val py = (y0 + y / scale).coerceIn(0, c.height - 1)
            img.setRGB(x, y, c.pixels[py * c.width + px])
        }
        ImageIO.write(img, "png", f)
    }

    /** The camera's view of [c], a 540 × 600 screen piece round its middle. */
    private fun crop(c: PixelCanvas, lens: Lens, cam: Camera, f: File) {
        val w = 540; val h = 600
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) for (x in 0 until w) {
            val nx = lens.cx(cam.toCanvasX(270f + x)).toInt().coerceIn(0, c.width - 1)
            val ny = lens.cy(cam.toCanvasY(900f + y)).toInt().coerceIn(0, c.height - 1)
            img.setRGB(x, y, c.pixels[ny * c.width + nx])
        }
        ImageIO.write(img, "png", f)
    }
}
