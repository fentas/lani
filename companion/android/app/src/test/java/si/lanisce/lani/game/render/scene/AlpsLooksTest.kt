package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.FrameBudget
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.CultureLooks
import si.lanisce.lani.game.scene.Homes
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.Sky
import si.lanisce.lani.game.scene.Wildlife
import si.lanisce.lani.game.scene.parseScene
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The mountains' art (`alps`) in Carinthia's and the Lake District's looks (companion/SCENES.md "Culture looks"; Am See,
 * On the fell): rendered for review beside Primorska's, which is what both drew before their looks
 * (build/scene-snapshots/looks/alps), and checked: each look draws its own picture at every detail and on every canvas,
 * with every thing of the scene to tap exactly when Primorska's has it (the seasons round the clock, the weather, every
 * effect a dialog cues, the wild animals), its people at their spots, the Lake District's Daisy alone on the fell until a
 * dialog counts her friends up over the brow, and a frame as fast as Primorska's.
 */
class AlpsLooksTest {
    private val dir = File("build/scene-snapshots/looks/alps").apply { mkdirs() }
    private val cultures = listOf("primorska", "kaernten", "lakeland")
    private val looks = listOf("kaernten", "lakeland")
    private val objects = SceneArt.objects.getValue("alps").toSet()

    private fun people(talking: Boolean = false): List<PersonInScene> = listOf(
        PersonInScene("a", "grandpa", "path", talking = talking), PersonInScene("b", "shepherd", "wall"), PersonInScene("c", "child1", "stream"),
    )

    private fun world(culture: String) = SceneWorld(culture = culture, landscape = "lake")

    private fun frame(culture: String, hour: Float, month: Int, fx: Map<String, Float> = emptyMap(), sky: Sky = Sky.CLEAR, count: Int? = null, wild: Set<String>? = null, time: Double = 3.0) =
        SceneFrame(time = time, hour = hour, month = month, objects = objects, people = people(), world = world(culture), fx = fx, sky = sky, count = count, wild = wild)

    /** The views for review: the seasons, dawn's glow with the pail, the night, the cows counted, a storm with the birds off the crag. */
    private fun views(culture: String): List<Pair<String, SceneFrame>> = listOf(
        "summer" to frame(culture, 11f, 7, wild = setOf("chamois", "ibex")),
        "autumn" to frame(culture, 10f, 10),
        "winter" to frame(culture, 13f, 1),
        "spring" to frame(culture, 9f, 4, wild = setOf("chamois")),
        "dawn" to frame(culture, 5.6f, 7, fx = mapOf("glow" to 1f, "pail" to 1f)),
        "night" to frame(culture, 22.5f, 8),
        "cows" to frame(culture, 15f, 8, fx = mapOf("cows" to 1f, "cowbells" to 1f), count = 9),
        "storm" to frame(culture, 16f, 7, fx = mapOf("birds" to 0.4f, "ibex" to 1f), sky = Sky(gloom = 0.6f, wind = 0.4f)),
    )

    private fun render(f: SceneFrame, w: Int = 240, h: Int = 160, k: Int = 1): Pair<PixelCanvas, List<SceneHit>> {
        val c = PixelCanvas(w * k, h * k)
        val hits = ScenePainters.create("alps").render(c, f, Lens(k, 0, 0, w, h))
        return c to hits
    }

    // ------------------------------------------------------------------ for review

    @Test fun `render the mountains in every culture's look`() {
        val bw = 240; val bh = 160; val sc = 3
        for (culture in cultures) {
            val vs = views(culture)
            val cols = 4
            val img = BufferedImage(bw * sc * cols, bh * sc * ((vs.size + cols - 1) / cols), BufferedImage.TYPE_INT_RGB)
            for ((i, v) in vs.withIndex()) {
                val (c, _) = render(v.second)
                write(c, File(dir, "$culture-${v.first}.png"), 4)
                for (y in 0 until bh * sc) for (x in 0 until bw * sc) img.setRGB((i % cols) * bw * sc + x, (i / cols) * bh * sc + y, c.pixels[(y / sc) * bw + x / sc])
            }
            ImageIO.write(img, "png", File(dir, "00-$culture.png"))
            // closer up, and the tablet's canvases on their side and upright
            for (k in 2..3) write(render(vs[0].second, k = k).first, File(dir, "$culture-summer-d$k.png"), 6 / k)
            write(render(vs[0].second, 360, 200).first, File(dir, "$culture-tablet-landscape.png"), 3)
            write(render(vs[0].second, 240, 320).first, File(dir, "$culture-tablet-portrait.png"), 3)
        }
        // before and after side by side: Primorska's picture (what every culture drew before its look) and each look
        val pairs = listOf("summer", "autumn", "night")
        val img = BufferedImage(bw * 2 * cultures.size, bh * 2 * pairs.size, BufferedImage.TYPE_INT_RGB)
        for ((r, name) in pairs.withIndex()) for ((ci, culture) in cultures.withIndex()) {
            val c = render(views(culture).first { it.first == name }.second).first
            for (y in 0 until bh * 2) for (x in 0 until bw * 2) img.setRGB(ci * bw * 2 + x, r * bh * 2 + y, c.pixels[(y / 2) * bw + x / 2])
        }
        ImageIO.write(img, "png", File(dir, "00-before-after.png"))
    }

    // ------------------------------------------------------------------ checks

    @Test fun `each look draws its own picture, closer up and on every canvas too`() {
        for (culture in looks) for ((name, f) in views(culture)) for ((w, h, k) in listOf(Triple(240, 160, 1), Triple(240, 160, 2), Triple(360, 200, 1), Triple(240, 320, 1))) {
            val own = render(f, w, h, k).first.pixels
            val primorska = render(f.copy(world = world("primorska")), w, h, k).first.pixels
            assertNotEquals("$culture $name ${w}x$h d$k looks like Primorska's", primorska.toList(), own.toList())
        }
        // and the two looks differ from each other
        assertNotEquals(render(views("kaernten")[0].second).first.pixels.toList(), render(views("lakeland")[0].second).first.pixels.toList())
    }

    /** The frames the things to tap are compared in: the seasons round the clock, the weather, every effect, the wild animals. */
    private fun checked(culture: String): List<Pair<String, SceneFrame>> = buildList {
        for (month in listOf(1, 4, 7, 10)) for (hour in listOf(0.5f, 5.8f, 9f, 13f, 18.8f, 21.5f)) {
            add("m$month h$hour" to frame(culture, hour, month, wild = Wildlife.all("alps")))
            add("m$month h$hour tame" to frame(culture, hour, month, wild = emptySet()))
        }
        for (sky in listOf(Sky(rain = 0.9f, gloom = 0.8f), Sky(fog = 0.9f), Sky(snow = 0.8f, wind = 0.5f))) add("$sky" to frame(culture, 12f, 6, sky = sky))
        for (fx in SceneArt.effects.getValue("alps")) for (level in listOf(0f, 0.5f, 1f)) {
            add("$fx $level" to frame(culture, 12f, 7, fx = mapOf(fx to level), count = if (fx == "cows") 12 else null, wild = Wildlife.all("alps")))
            add("$fx $level night" to frame(culture, 22.5f, 11, fx = mapOf(fx to level), count = if (fx == "cows") 5 else null))
        }
    }

    private fun tappable(hits: List<SceneHit>): Set<SceneTarget> = hits.map { it.target }.toSet()

    @Test fun `every thing is there to tap exactly when Primorska's picture has it, its people at their spots`() {
        val failed = ArrayList<String>()
        for (culture in looks) for ((name, f) in checked(culture)) for (k in listOf(1, 2)) {
            val own = tappable(render(f, k = k).second)
            val primorska = tappable(render(f.copy(world = world("primorska")), k = k).second)
            if (own != primorska) failed += "$culture $name d$k: missing ${primorska - own}, more ${own - primorska}"
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
        // the scene's things all there by day, its three people too
        for (culture in looks) {
            val hits = tappable(render(frame(culture, 11f, 7)).second)
            val words = objects - Wildlife.all("alps")
            assertEquals(culture, words, hits.filterIsInstance<SceneTarget.Thing>().map { it.slot }.toSet() intersect words)
            assertEquals(culture, setOf("a", "b", "c"), hits.filterIsInstance<SceneTarget.Person>().map { it.id }.toSet())
        }
    }

    @Test fun `the Lake District's Daisy is alone on the fell until a dialog counts her friends`() {
        fun cows(culture: String, fx: Map<String, Float> = emptyMap(), count: Int? = null): Int {
            val p = ScenePainters.create("alps") as AlpsPainter
            p.render(PixelCanvas(240, 160), frame(culture, 12f, 7, fx = fx, count = count), Lens(1, 0, 0, 240, 160))
            return p.cowsOnPasture
        }
        assertEquals(4, cows("primorska")); assertEquals(4, cows("kaernten"))
        assertEquals("Daisy alone among the sheep", 1, cows("lakeland"))
        // "Daisy's not alone today": as many as the dialog counts, all up over the brow at 1
        for (n in listOf(2, 5, 9, 16)) for (culture in cultures) assertEquals("$culture $n", n, cows(culture, mapOf("cows" to 1f), n))
        // on their way they're still on the slope beyond the brow: fewer of them on the pasture
        assertTrue(cows("lakeland", mapOf("cows" to 0.3f), 9) < 9)
    }

    @Test fun `a frame of every look is as fast as Primorska's`() {
        for (culture in cultures) {
            val p = ScenePainters.create("alps")
            val f = SceneFrame(time = 1.0, hour = 22.5f, month = 10, objects = objects, people = people(talking = true), highlight = "lake", world = world(culture))
            val c = PixelCanvas(360, 240)
            repeat(15) { p.render(c, f.copy(time = it / 12.0)) }
            val n = 40
            val t0 = System.nanoTime()
            repeat(n) { p.render(c, f.copy(time = 2 + it / 12.0)) }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            println("AlpsPainter %s: %.2f ms/frame (night, 360x240)".format(culture, ms))
            assertTrue("$culture too slow: $ms ms", ms < FrameBudget.scaled(20.0))
        }
    }

    @Test fun `the looks are in the cultures' tables, Primorska's has none`() {
        assertTrue(CultureLooks.of("kaernten").touches("alps"))
        assertTrue(CultureLooks.of("lakeland").touches("alps"))
        assertTrue(!CultureLooks.of("primorska").touches("alps"))
        assertEquals("karawanken", CultureLooks.of("kaernten").variant("alps", "range"))
        assertEquals("fells", CultureLooks.of("lakeland").variant("alps", "range"))
    }

    /**
     * Which places a Carinthian or a Lake District village goes into (the bridge's scenesDirs: its language's curated
     * scenes, none for German or English yet, then its pack's own): its fire and its horizon, both in its look (the fire's
     * village FireStyle's, the horizon's this art's); no building of it has a scene, so none has "Vstopi · Go in".
     */
    @Test fun `a Carinthian or a Lake District village goes into its fire and its horizon only, both in its look`() {
        val companion = listOf(File("../.."), File("..")).first { File(it, "cultures").isDirectory && File(it, "scenes").isDirectory }
        val state = GameEngine.newGame(3, 0L).copy(age = Age.MESTO, buildings = BuildingType.entries.mapIndexed { i, t -> Building("b$i", t, i) })
        for ((culture, language) in listOf("kaernten" to "de", "lakeland" to "en")) {
            val dirs = listOf(File(companion, "scenes/$language"), File(companion, "cultures/$culture/scenes")).filter { it.isDirectory }
            val scenes = dirs.flatMap { d -> d.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }.map { parseScene(it.readText()) } }
            assertEquals(culture, setOf("campfire", "alps"), scenes.map { it.art }.toSet())
            assertEquals(culture, setOf("fire", "spot:horizon"), scenes.flatMap { it.from }.toSet())
            for (b in state.buildings) assertEquals("$culture ${b.type}", emptyList<String>(), Homes.scenesOf(b, scenes, state).map { it.id })
            assertTrue(culture, CultureLooks.of(culture).touches("alps"))
        }
    }

    private fun write(c: PixelCanvas, f: File, scale: Int) {
        val img = BufferedImage(c.width * scale, c.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height * scale) for (x in 0 until c.width * scale) img.setRGB(x, y, c.pixels[(y / scale) * c.width + x / scale])
        ImageIO.write(img, "png", f)
    }
}
