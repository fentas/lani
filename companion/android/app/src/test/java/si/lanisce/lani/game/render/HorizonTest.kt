package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.TownSpots
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The land at the village's horizon (the hills and the Alps behind it, the sea along it in a village by the coast) is a
 * place to tap: the band from the mountains' shoulders down to the horizon, whatever the buildings, the people, the
 * spots and the forest there don't take; its bubble has an anchor; the sea shows only where the culture says.
 */
class HorizonTest {
    private val town = GameState(
        seed = 42, age = Age.MESTO,
        buildings = listOf(
            BuildingType.FIELD, BuildingType.HUT, BuildingType.WELL, BuildingType.KOZOLEC, BuildingType.HOUSE, BuildingType.PALISADE,
            BuildingType.LIPA, BuildingType.CHURCH, BuildingType.BEEHIVE, BuildingType.SMITHY, BuildingType.HOUSE, BuildingType.WATCHTOWER,
        ).mapIndexed { i, t -> Building("b$i", t, i) },
    )

    @Test fun `the horizon is the band of the hills behind the village, not the sky above nor the village below`() {
        for ((w, h) in listOf(360 to 800, 400 to 180, 320 to 512)) {
            val comp = VillageLayout.composition(w, h, false)
            assertTrue("$w×$h: the hills just above the horizon", TownAnchors.isHorizon(w, h, w / 2f, comp.horizon - 6f))
            assertTrue("$w×$h: the Alps' shoulders", TownAnchors.isHorizon(w, h, w * 0.7f, comp.horizon - 20f))
            assertFalse("$w×$h: the sky high up", TownAnchors.isHorizon(w, h, w / 2f, 2f))
            assertFalse("$w×$h: the village", TownAnchors.isHorizon(w, h, comp.fireX.toFloat(), comp.fireY.toFloat()))
            assertFalse("$w×$h: off the canvas", TownAnchors.isHorizon(w, h, -1f, comp.horizon - 6f))
            val a = TownAnchors.withSpots(town, w, h)[TownPlace.Spot(TownSpots.HORIZON)]
            assertNotNull(a)
            assertTrue("$w×$h: its bubble points at the hills: $a", TownAnchors.isHorizon(w, h, a!!.x, a.y))
        }
    }

    @Test fun `what stands in front of the hills keeps its taps`() {
        val w = 360; val h = 800
        val r = VillageRenderer()
        val c = PixelCanvas(w, h)
        r.render(c, town, Frame(time = 2.0, hour = 11f, month = 6))
        // the buildings and the fire answer at their bodies even where they rise into the band (the tap tries them first)
        for ((id, p) in TownAnchors.bodies(town, w, h)) {
            if (id.startsWith("spot:") || id == "forest" || !TownAnchors.isHorizon(w, h, p.x, p.y)) continue
            assertNotNull("$id at its body in the band", r.hitTest(p.x.toInt(), p.y.toInt(), 2))
        }
        // the forest's spot and the landscape's spots are below it
        for ((id, p) in TownAnchors.bodies(town, w, h)) if (id == "forest" || id.startsWith("spot:")) assertFalse(id, TownAnchors.isHorizon(w, h, p.x, p.y))
        // no one waits at the horizon: it is far away
        assertEquals(null, TownAnchors.visitorSpot(town, TownPlace.Spot(TownSpots.HORIZON), VillageLayout.composition(w, h, false), w, h))
        // TalkBack gets a node for it only when something opens there
        assertFalse(TownAnchors.bodies(town, w, h).any { it.first == "spot:${TownSpots.HORIZON}" })
        assertTrue(TownAnchors.bodies(town, w, h, horizon = true).any { it.first == "spot:${TownSpots.HORIZON}" })
    }

    @Test fun `a village by the sea shows it along the horizon, on the left, and nothing else changes`() {
        val w = 360; val h = 800
        val comp = VillageLayout.composition(w, h, false)
        val hills = PixelCanvas(w, h).also { VillageRenderer().render(it, town, Frame(time = 2.0, hour = 11f, month = 6)) }
        val coast = PixelCanvas(w, h).also { VillageRenderer().render(it, town, Frame(time = 2.0, hour = 11f, month = 6, sea = true)) }
        val dir = File("build/scene-snapshots/village").apply { mkdirs() }
        for ((name, c) in listOf("hills" to hills, "coast" to coast)) {
            val img = BufferedImage(w * 2, (comp.horizon + 60) * 2, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until img.height) for (x in 0 until img.width) img.setRGB(x, y, c.pixels[(y / 2) * w + x / 2])
            ImageIO.write(img, "png", File(dir, "horizon-$name.png"))
        }
        var changed = 0
        for (y in 0 until h) for (x in 0 until w) if (hills.pixels[y * w + x] != coast.pixels[y * w + x]) {
            changed++
            assertTrue("($x, $y) is the sea at the horizon on the left", y in (comp.horizon - 14)..comp.horizon && x < w * 0.5f)
        }
        assertTrue("the sea shows ($changed px)", changed > 150)
    }
}
