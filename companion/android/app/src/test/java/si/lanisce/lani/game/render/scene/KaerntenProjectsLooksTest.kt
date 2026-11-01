package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import si.lanisce.lani.FrameBudget
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.scene.CultureLooks
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFixtures
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import si.lanisce.lani.game.scene.Sky
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The Carinthian village's projects and its wood in their own look (kaernten-projects-a; companion/SCENES.md "Culture
 * looks"; docs/plans/05-built/kaernten-projects-a.md): Unter dem Maibaum (the square in May, the Kraxler's prize under the
 * wreath), An der Brücke (the cart bridge of larch, at each of its stages), In der Mühle (the log mill grinding Heiden), Auf
 * der Stockbahn (the asphalt lane, ice in winter, the Eisstöcke and the Daube), Bei der Wegkapelle (the onion turret, the
 * Heiden, the larches), Im Obstgarten (the orchard, the Most press) and Im Wald (the larches, the Marterl), each beside the
 * Slovene picture it was (build/scene-snapshots/looks/kaernten-projects/<art>-<when>.png: the default culture on the left,
 * Carinthia on the right, a row a time of year), closer up and on a phone; and checked: each looks Carinthia's own, every
 * thing is drawn and tappable as often as in the default culture's picture at every detail, by day and at night, the people
 * stand where they did, and a frame stays inside its budget.
 */
class KaerntenProjectsLooksTest {
    private val dir = File("build/scene-snapshots/looks/kaernten-projects").apply { mkdirs() }

    /** The arts, the month their project's place is seen in (the maypole stands in May), and who stands at their spots. */
    private val arts = listOf(
        Triple("square", 5, listOf("shepherd", "child1", "child2", "child3", "grandpa", "teacher", "innkeeper", "farmer")),
        Triple("stream", 6, listOf("farmer", "shepherd", "smith")),
        Triple("mill", 6, listOf("farmer", "child3", "grandma")),
        Triple("bocce", 6, listOf("grandpa", "farmer", "winemaker")),
        Triple("chapel", 7, listOf("aunt", "grandma", "child2")),
        Triple("hills", 9, listOf("winemaker", "farmer", "child2")),
        Triple("forest", 6, listOf("shepherd", "beekeeper", "grandpa")),
    )

    /** The times of year a sheet shows: its month by day, an October evening, a January morning (snow, the Stockbahn's ice), April's blossom. */
    private val times = listOf(Triple("day", 10.5f, 0), Triple("october-evening", 18.5f, 10), Triple("january", 11f, 1), Triple("april", 10f, 4))

    /** The arts to render (the environment's `KAERNTEN_ARTS`, a comma list, for a quick look at a few), else all. */
    private val only: Set<String>? = System.getenv("KAERNTEN_ARTS")?.split(',')?.filter { it.isNotBlank() }?.toSet()
    private val chosen get() = arts.filter { only == null || it.first in only }

    /** Every project finished, or [projects] as far as they have come, in [culture]'s village. */
    private fun world(culture: String, projects: Map<String, Int>? = null) = SceneWorld(culture = culture, projects = projects)

    private fun frame(
        art: String, world: SceneWorld, hour: Float, month: Int, sprites: List<String>, people: Boolean = true, sky: Sky = Sky.CLEAR,
    ) = SceneFrame(
        time = 3.0, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), world = world, sky = sky,
        date = java.time.LocalDate.of(2026, month, 15),
        people = if (!people) emptyList() else SceneArt.personSlots.getValue(art).mapIndexed { k, s -> PersonInScene("p-$s", sprites[k % sprites.size], s, pose = if (k == 0) Pose.TALK else Pose.IDLE) },
    )

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = 240, h: Int = 160): PixelCanvas {
        val c = PixelCanvas(w * k, h * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
        return c
    }

    @Test fun `render Carinthia's project places beside the Slovene ones`() {
        for ((art, month, sprites) in chosen) {
            val s = 2; val label = 16
            val img = BufferedImage(240 * s * 2, (160 * s + label) * times.size, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            for ((row, t) in times.withIndex()) for ((col, culture) in listOf("primorska", "kaernten").withIndex()) {
                val m = if (t.third == 0) month else t.third
                val c = render(art, frame(art, world(culture), t.second, m, sprites))
                val oy = row * (160 * s + label)
                for (y in 0 until 160 * s) for (x in 0 until 240 * s) img.setRGB(col * 240 * s + x, oy + label + y, c.pixels[(y / s) * 240 + x / s])
                g.color = Color.WHITE
                g.drawString("$art, $culture, ${t.first} (month $m)", col * 240 * s + 4, oy + 12)
            }
            g.dispose()
            ImageIO.write(img, "png", File(dir, "$art-sheet.png"))
            for (k in 2..3) write(render(art, frame(art, world("kaernten"), 10.5f, month, sprites, people = false), k), File(dir, "$art-k$k.png"))
            write(render(art, frame(art, world("kaernten"), 22f, 12, sprites), 2), File(dir, "$art-night-december.png"))
            write(render(art, frame(art, world("kaernten"), 9f, month, sprites, sky = Sky(rain = 0.6f, gloom = 0.6f, wind = 0.4f)), 2, 270, 297), File(dir, "$art-phone-rain.png"))
        }
        // the bridge as Franz's lads build it, stage by stage, in Carinthia
        val stages = 0..6
        val img = BufferedImage(240 * 2 * 2, (160 * 2 + 16) * stages.count(), BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        for ((row, st) in stages.withIndex()) for ((col, culture) in listOf("primorska", "kaernten").withIndex()) {
            val c = render("stream", frame("stream", world(culture, mapOf("most" to st)), 10.5f, 6, listOf("farmer", "shepherd", "smith")))
            val oy = row * (160 * 2 + 16)
            for (y in 0 until 320) for (x in 0 until 480) img.setRGB(col * 480 + x, oy + 16 + y, c.pixels[(y / 2) * 240 + x / 2])
            g.color = Color.WHITE
            g.drawString("stream, $culture, the bridge at step $st", col * 480 + 4, oy + 12)
        }
        g.dispose()
        ImageIO.write(img, "png", File(dir, "stream-bridge-stages.png"))
    }

    @Test fun `Carinthia's look draws each project's place its own way`() {
        for ((art, month, sprites) in chosen) {
            assertTrue("Carinthia's look touches $art", CultureLooks.of("kaernten").touches(art))
            for (t in times) {
                val m = if (t.third == 0) month else t.third
                // the maypole stands only in May: the square's own look is Carinthia's anyway, its prize then
                if (art == "square" && m != 5) continue
                val a = render(art, frame(art, world("primorska"), t.second, m, sprites, people = false)).pixels.toList()
                val b = render(art, frame(art, world("kaernten"), t.second, m, sprites, people = false)).pixels.toList()
                assertNotEquals("$art in month $m at ${t.second} h looks Slovene in Carinthia", a, b)
            }
        }
    }

    @Test fun `the cart bridge is larch from its logs on`() {
        for (st in 3..6) {
            val a = render("stream", frame("stream", world("primorska", mapOf("most" to st)), 10.5f, 6, listOf("farmer"), people = false)).pixels.toList()
            val b = render("stream", frame("stream", world("kaernten", mapOf("most" to st)), 10.5f, 6, listOf("farmer"), people = false)).pixels.toList()
            if (st >= 4) assertNotEquals("the bridge at step $st is the Slovene one in Carinthia", a, b)
        }
    }

    @Test fun `the Stockbahn is ice in winter and asphalt in summer`() {
        val sprites = listOf("grandpa", "farmer", "winemaker")
        // the lane's grey asphalt: a neutral grey, neither the sand's yellow, the grass's green, nor snow or ice
        fun asphalt(px: Int): Boolean {
            val r = (px shr 16) and 255; val g = (px shr 8) and 255; val b = px and 255
            return abs(r - g) < 12 && b - r in -6..8 && r + g + b in 150..330
        }
        fun greys(culture: String, month: Int): Int {
            val f = frame("bocce", world(culture), 12f, month, sprites, people = false)
            val c = PixelCanvas(240, 160)
            val hits = ScenePainters.create("bocce").render(c, f, Lens(1, 0, 0, 240, 160))
            val sand = hits.first { it.target == SceneTarget.Thing("sand") }
            var n = 0
            for (y in sand.top.coerceAtLeast(0) until sand.bottom.coerceAtMost(160)) for (x in sand.left.coerceAtLeast(0) until sand.right.coerceAtMost(240)) if (asphalt(c.pixels[y * 240 + x])) n++
            return n
        }
        val june = greys("kaernten", 6); val sand = greys("primorska", 6); val ice = greys("kaernten", 1)
        assertTrue("the lane is asphalt in June ($june grey pixels, the sand court $sand)", june > 300 && june > 4 * sand)
        assertTrue("the lane is ice in January ($ice grey pixels, $june in June)", ice * 2 < june)
    }

    @Test fun `in Carinthia each project's place draws what it has, once, and the people stand where they did`() {
        for ((art, month, sprites) in chosen) for (k in 1..3) for (hour in listOf(10.5f, 21.5f)) {
            val w = world("kaernten")
            val hits = ScenePainters.create(art).render(PixelCanvas(240 * k, 160 * k), frame(art, w, hour, month, sprites), Lens(k, 0, 0, 240, 160))
            val home = ScenePainters.create(art).render(PixelCanvas(240 * k, 160 * k), frame(art, world("primorska"), hour, month, sprites), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.objects.getValue(art)) {
                val n = hits.count { it.target == SceneTarget.Thing(slot) }
                val want = home.count { it.target == SceneTarget.Thing(slot) }
                assertEquals("$art/$slot, detail $k, $hour h: as many as the default culture's", want, n)
                if (SceneFixtures.there(art, slot, w) && want > 0) assertEquals("$art/$slot once", 1, n)
            }
            for (slot in SceneArt.personSlots.getValue(art)) {
                val a = home.singleOrNull { it.target == SceneTarget.Person("p-$slot") } ?: continue
                val b = hits.single { it.target == SceneTarget.Person("p-$slot") }
                assertEquals("$art: someone at $slot stands where they did (detail $k)", listOf(a.anchorX, a.anchorY), listOf(b.anchorX, b.anchorY))
            }
        }
    }

    /** A frame of each art in Carinthia's look and in Primorska's, at night with people and a highlight: the best of three rounds of 20 (FrameBudget). */
    @Test fun `a Carinthian project's frame renders fast`() {
        for ((art, month, sprites) in chosen) {
            val c = PixelCanvas(360, 240)
            fun round(culture: String): Double {
                val p = ScenePainters.of(art)
                val f = frame(art, world(culture), 22.5f, if (art == "square") 5 else 10, sprites).copy(highlight = SceneArt.objects.getValue(art).first())
                repeat(10) { p.render(c, f.copy(time = it / 12.0)) }
                return FrameBudget.cpuMs { repeat(20) { p.render(c, f.copy(time = 2 + it / 12.0)) } } / 20
            }
            var mine = Double.MAX_VALUE; var primorska = Double.MAX_VALUE
            repeat(3) { mine = minOf(mine, round("kaernten")); primorska = minOf(primorska, round("primorska")) }
            println("ScenePainter %s (month %d): %.2f ms/frame in Carinthia, %.2f in Primorska (night, 360x240, the best of three rounds)".format(art, month, mine, primorska))
            assertTrue("$art too slow in Carinthia: $mine ms", mine < FrameBudget.scaled(20.0))
        }
    }

    private fun write(c: PixelCanvas, f: File) {
        val img = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height) for (x in 0 until c.width) img.setRGB(x, y, c.pixels[y * c.width + x])
        ImageIO.write(img, "png", f)
    }
}
