package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.Sky
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** The weather a scene's dialog brings (see [Weather]): every art shows it, and it never moves what can be tapped. */
class SceneWeatherTest {
    private val bw = 270; private val bh = 297

    private fun frame(art: String, sky: Sky, hour: Float = 14f) = SceneFrame(
        time = 3.0, hour = hour, month = 7, objects = SceneArt.objects.getValue(art).toSet(),
        people = SceneArt.personSlots.getValue(art).mapIndexed { k, slot -> PersonInScene("p-$slot", SceneArt.people[k], slot) },
        sky = sky,
    )

    private fun render(art: String, f: SceneFrame): Pair<PixelCanvas, List<si.lanisce.lani.game.scene.SceneHit>> {
        val c = PixelCanvas(bw, bh)
        return c to ScenePainters.create(art).render(c, f)
    }

    private fun changed(a: PixelCanvas, b: PixelCanvas): Int = a.pixels.indices.count { a.pixels[it] != b.pixels[it] }

    @Test fun `rain, snow, fog and a grey sky show in every art`() {
        for (art in SceneArt.arts) {
            val (clear, _) = render(art, frame(art, Sky.CLEAR))
            for (sky in listOf(Sky(rain = 0.8f), Sky(snow = 0.8f), Sky(fog = 0.8f), Sky(gloom = 1f))) {
                val (c, _) = render(art, frame(art, sky))
                // a flake a pixel on the scene's canvas: from under the watchtower's roof, only those past it show
                assertTrue("$art: $sky changes the picture", changed(clear, c) > bw * bh / 700)
            }
        }
    }

    @Test fun `a grey sky darkens the light`() {
        for (art in SceneArt.arts) {
            val (clear, _) = render(art, frame(art, Sky.CLEAR))
            val (grey, _) = render(art, frame(art, Sky(gloom = 1f)))
            val lum = { c: PixelCanvas -> c.pixels.sumOf { Col.lum(it).toDouble() } / c.pixels.size }
            assertTrue("$art: darker under a grey sky", lum(grey) < lum(clear) - 0.02)
        }
    }

    @Test fun `the weather keeps the things and people where they are`() {
        val all = Sky(rain = 1f, snow = 0.6f, fog = 0.7f, wind = 0.8f, gloom = 1f, lightning = true)
        for (art in SceneArt.arts) {
            val (_, clear) = render(art, frame(art, Sky.CLEAR))
            val (_, wet) = render(art, frame(art, all))
            assertEquals("$art: the hits", clear, wet)
        }
    }

    @Test fun `no weather, no change`() {
        for (art in SceneArt.arts) {
            val (a, _) = render(art, frame(art, Sky.CLEAR))
            val (b, _) = render(art, frame(art, Sky(rain = 0f, fog = 0f)))
            assertEquals("$art: a clear sky is the picture as before", 0, changed(a, b))
        }
    }

    @Test fun `rain on the tent darkens its canvas and drips off its eave`() {
        val f = frame("tent", Sky.CLEAR)
        val tent = ScenePainters.create("tent") as TentPainter
        val dry = PixelCanvas(bw, bh).also { tent.render(it, f) }
        val wet = PixelCanvas(bw, bh).also { tent.render(it, f.copy(sky = Sky(rain = 1f))) }
        // the canvas darkens; off its eave the drops fall past the low wall onto the grass, never into the tent
        var outside = 0
        for (y in 0 until bh) for (x in 0 until bw) if (dry.pixels[y * bw + x] != wet.pixels[y * bw + x] && !tent.indoors(x, y)) outside++
        assertTrue("the tent's canvas and the meadow change in the rain ($outside px)", outside > bw * bh / 20)
    }

    /**
     * The tent is cut open at the front for us to look in, and as far as the weather goes its roof carries on over the
     * floor there: in heavy rain in a gale, and in a storm with snow and mist too, at every detail and at moments all
     * through the drips' cycles, nothing changes inside it ([TentPainter.indoors], a picture pixel in from its edge) and
     * nothing drips off the cut. The pictures (build/scene-snapshots/weather/tent-rain-d*): the tent in the rain and,
     * beside it, what the rain changed (magenta) round the view into the tent (blue).
     */
    @Test fun `no rain, snow or mist falls into the tent`() {
        val dir = File("build/scene-snapshots/weather").apply { mkdirs() }
        val calm = Sky(wind = 0.8f)
        val skies = listOf("rain" to Sky(rain = 1f, wind = 0.8f), "storm" to Sky(rain = 1f, snow = 0.6f, fog = 0.7f, wind = 0.8f))
        val fails = ArrayList<String>()
        for (k in 1..Lens.MAX_DETAIL) for ((name, sky) in skies) {
            val tent = ScenePainters.create("tent") as TentPainter
            val w = bw * k; val h = bh * k
            val dry = PixelCanvas(w, h); val wet = PixelCanvas(w, h)
            val leaked = BooleanArray(w * h)
            for (i in 0 until 10) {
                val f = frame("tent", calm).copy(time = 3.0 + i * 0.23)
                tent.render(dry, f, Lens(k, 0, 0, bw, bh))
                tent.render(wet, f.copy(sky = sky), Lens(k, 0, 0, bw, bh))
                var inside = 0; var leaks = 0
                for (y in k until h - k) for (x in k until w - k) {
                    // a picture pixel in from the edge of the view into the tent: the canvas's own edge may share its pixels
                    if (!(tent.indoors(x, y) && tent.indoors(x - k, y) && tent.indoors(x + k, y) && tent.indoors(x, y - k) && tent.indoors(x, y + k))) continue
                    inside++
                    if (dry.pixels[y * w + x] != wet.pixels[y * w + x]) { leaks++; leaked[y * w + x] = true }
                }
                if (inside < w * h / 30) fails.add("$name, detail $k at ${f.time}: only $inside px look into the tent")
                if (leaks > 0) fails.add("$name, detail $k at ${f.time}: the weather changed $leaks px inside the tent")
                if (i == 0 && name == "rain") write(wet, dry, tent, leaked, File(dir, "tent-rain-d$k.png"), 6 / k)
            }
        }
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }

    /** The tent in [wet] and, beside it, what differs from [dry] (magenta, [leaks] brighter) over the view into it (blue). */
    private fun write(wet: PixelCanvas, dry: PixelCanvas, tent: TentPainter, leaks: BooleanArray, f: File, scale: Int) {
        val w = wet.width; val h = wet.height
        val img = BufferedImage(w * scale * 2 + 8, h * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h * scale) for (x in 0 until w * scale) {
            val sx = x / scale; val sy = y / scale; val i = sy * w + sx
            img.setRGB(x, y, wet.pixels[i] and 0xFFFFFF)
            val dim = (dry.pixels[i] and 0xFEFEFE) shr 1
            img.setRGB(w * scale + 8 + x, y, when {
                leaks[i] -> 0xFF40FF
                wet.pixels[i] != dry.pixels[i] -> 0xA02090
                tent.indoors(sx, sy) -> Col.mix(dim, 0x3060C0, 0.5f) and 0xFFFFFF
                else -> dim
            })
        }
        ImageIO.write(img, "png", f)
    }

    /** From the watchtower's platform we look out from under its roof: the rain falls beyond it, not on the boards or the roof. */
    @Test fun `no rain or snow under the watchtower's roof`() {
        val f = frame("watchtower", Sky.CLEAR)
        val (dry, _) = render("watchtower", f)
        val (wet, _) = render("watchtower", f.copy(sky = Sky(rain = 1f, snow = 0.8f, wind = 0.5f)))
        val changed = { x0: Int, y0: Int, x1: Int, y1: Int -> (y0 until y1).sumOf { y -> (x0 until x1).count { x -> dry.pixels[y * bw + x] != wet.pixels[y * bw + x] } } }
        // the roof's underside along the top, the corner posts down the sides, the platform's boards below the railing
        assertEquals("under the roof", 0, changed(0, 0, bw, 10))
        assertEquals("the left post", 0, changed(0, 0, 6, bh))
        assertEquals("the right post", 0, changed(bw - 6, 0, bw, bh))
        assertEquals("the platform", 0, changed(0, 215, bw, bh))
        // through the openings, the land and the sky get the rain
        assertTrue("the rain falls beyond the tower", changed(10, 20, bw - 10, 200) > bw * bh / 50)
    }
}
