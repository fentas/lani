package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.villagers.Villager
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The charcoal pile deep in the woods below the village (the "kopa" spot): a place to tap at every detail, and its burner
 * sitting by it some evenings, his to tap. Renders it by day and by night, with and without him, at the three details, for
 * review (build/village-snapshots/49-kopa-*.png).
 */
class KopaRenderTest {
    private val dir = File("build/village-snapshots").apply { mkdirs() }
    private val fit = SceneFit.town(1080, 2400)
    private val rig = CameraRig.town(fit, 1080, 2400)
    private val village = GameState(seed = 42, age = Age.VAS, villagers = 6, morale = 70, fire = 70, buildings = listOf(Building("b0", BuildingType.HUT, 0), Building("b1", BuildingType.FIELD, 1)))
    private val burner = Villager("oglar", "Oglar Miha", "🧔", "burner", voice = "male", home = listOf("spot:kopa"))
    private val at = mapOf("oglar" to TownPlace.Spot("kopa"))

    @Test fun `render the charcoal pile by day and by night, with its burner`() {
        val fg = Foreground.of(village.age, fit.width, fit.height)!!
        assertTrue(fg.kopa)
        for ((name, hour, who) in listOf(Triple("day", 13f, false), Triple("dusk", 19.5f, true), Triple("night", 23f, true), Triple("night-alone", 23f, false))) {
            for (k in 1..3) {
                val cam = rig.lookAt(fg.kopaX - 4f, fg.kopaY - 4f, (rig.minScale * k).toFloat())
                val lens = Lens.of(k, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
                val c = PixelCanvas(fit.width, fit.height)
                val r = VillageRenderer()
                val frame = Frame(time = 4.0, hour = hour, month = 9, lens = lens, people = if (who) listOf(burner) else emptyList(), standing = if (who) at else emptyMap())
                r.render(c, village, frame)
                // the pile is the kopa's to tap, and he is his
                assertEquals("$name k=$k", VillageHit.OnSpot("kopa"), r.hitTest(lens.cx(fg.kopaX).toInt(), lens.cy(fg.kopaY - 3f).toInt(), k))
                if (who) {
                    val head = r.villagerAnchors.getValue("oglar")
                    assertEquals("$name k=$k", VillageHit.OnVillager("oglar"), r.hitTest(lens.cx(head.x).toInt(), lens.cy(head.y + 4f).toInt(), 3 * k))
                }
                // the part round the pile (the canvas is mostly woods), about 480 px wide whatever the detail
                write(c, File(dir, "49-kopa-$name-k$k.png"), lens.cx(fg.kopaX - 4f).toInt(), lens.cy(fg.kopaY - 8f).toInt(), 60 * k, 40 * k, if (k == 3) 1 else 4 / k)
            }
        }
    }

    /**
     * The pile as its cycle draws it (companion/SCENES.md, "The charcoal pile"): each stage's look, by day and by night, at
     * the finest detail, with its burner by it (build/village-snapshots/49-kopa-stage-*.png), and the picture over his talk
     * (49-kopa-talk-*.png: the window the talk shows, 240 × 120 at detail 3). Each stage looks unlike the one before.
     */
    @Test fun `render the pile at every stage of its cycle`() {
        val fg = Foreground.of(village.age, fit.width, fit.height)!!
        val looks = listOf(
            "1-empty" to si.lanisce.lani.game.scene.SpotLook("empty"),
            "2-logs" to si.lanisce.lani.game.scene.SpotLook("logs", 0.8f),
            "3-stack-half" to si.lanisce.lani.game.scene.SpotLook("stack", 0.4f),
            "4-stack" to si.lanisce.lani.game.scene.SpotLook("stack", 1f),
            "5-covered-turf" to si.lanisce.lani.game.scene.SpotLook("covered", 0.4f),
            "6-covered" to si.lanisce.lani.game.scene.SpotLook("covered", 1f),
            "7-lit" to si.lanisce.lani.game.scene.SpotLook("lit", 1f, "white"),
            "8-smoke-white" to si.lanisce.lani.game.scene.SpotLook("smoke", 0.15f, "white"),
            "9-smoke-yellow" to si.lanisce.lani.game.scene.SpotLook("smoke", 0.55f, "yellow"),
            "10-smoke-blue" to si.lanisce.lani.game.scene.SpotLook("smoke", 0.95f, "blue"),
            "11-cooling" to si.lanisce.lani.game.scene.SpotLook("cooling", 0.3f),
            "12-opened" to si.lanisce.lani.game.scene.SpotLook("opened", 0.5f),
            "13-sacks" to si.lanisce.lani.game.scene.SpotLook("sacks", 0.8f),
            "14-ash" to si.lanisce.lani.game.scene.SpotLook("ash", 0.2f),
        )
        val drawn = HashMap<String, IntArray>()
        for ((name, look) in looks) for ((when_, hour) in listOf("day" to 14f, "night" to 22.5f)) {
            val k = 3
            val cam = rig.lookAt(fg.kopaX - 4f, fg.kopaY - 4f, (rig.minScale * k).toFloat())
            val lens = Lens.of(k, cam, 1080, 2400, fit.width, fit.height, fit.width, fit.height)
            val c = PixelCanvas(fit.width, fit.height)
            val r = VillageRenderer()
            r.render(c, village, Frame(time = 4.0, hour = hour, month = 7, lens = lens, people = listOf(burner), standing = at, kopa = look))
            assertEquals("$name: still the kopa's to tap", VillageHit.OnSpot("kopa"), r.hitTest(lens.cx(fg.kopaX).toInt(), lens.cy(fg.kopaY - 2f).toInt(), k))
            val x0 = lens.cx(fg.kopaX - 4f).toInt(); val y0 = lens.cy(fg.kopaY - 8f).toInt()
            write(c, File(dir, "49-kopa-stage-$name-$when_.png"), x0, y0, 60 * k, 40 * k, 1)
            if (when_ == "day") drawn[name] = IntArray(40 * 30) { i -> c.pixels[(lens.cy(fg.kopaY).toInt() - 30 * 3 + (i / 40) * 3) * c.width + lens.cx(fg.kopaX).toInt() - 20 * 3 + (i % 40) * 3] }
        }
        // each stage is drawn unlike the one before it (the smoke's colour aside, the pile itself)
        looks.zipWithNext().filter { (a, b) -> a.second.art != b.second.art }.forEach { (a, b) ->
            assertTrue("${a.first} vs ${b.first}", !drawn.getValue(a.first).contentEquals(drawn.getValue(b.first)))
        }
        // the picture over his talk: the window round the pile, as PilePicture renders it
        for ((name, look) in listOf("smoke-white" to looks[7].second, "logs" to looks[1].second, "sacks" to looks[12].second)) {
            val k = 3; val w = 240; val h = 120
            val lens = Lens(k, (fg.kopaX * k - w * 0.56f).toInt(), (fg.kopaY * k - h * 0.66f).toInt(), fit.width, fit.height)
            val c = PixelCanvas(w, h)
            VillageRenderer().render(c, village, Frame(time = 4.0, hour = 20.5f, month = 7, lens = lens, people = listOf(burner), standing = at, kopa = look))
            write(c, File(dir, "49-kopa-talk-$name.png"), w / 2, h / 2, w / 2, h / 2, 4)
        }
    }

    /** The part of [c] [rx] × [ry] px round ([cx], [cy]) as a PNG, [scale]d up. */
    private fun write(c: PixelCanvas, f: File, cx: Int, cy: Int, rx: Int, ry: Int, scale: Int) {
        val x0 = (cx - rx).coerceIn(0, c.width - 1); val y0 = (cy - ry).coerceIn(0, c.height - 1)
        val w = minOf(2 * rx, c.width - x0); val h = minOf(2 * ry, c.height - y0)
        val img = BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h * scale) for (x in 0 until w * scale) img.setRGB(x, y, c.pixels[(y0 + y / scale) * c.width + x0 + x / scale])
        ImageIO.write(img, "png", f)
    }
}
