package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
 * Carinthia's village projects and spots in its own look (companion/SCENES.md "Culture looks";
 * docs/plans/05-built/kaernten-projects-b.md): Florian's Feuerwehrhaus (the sign FF, Austria's banner), Lehrerin Eva's
 * Spielplatz by the school (the shingles, the green shutters, the geraniums), the view from Kathi's Aussichtsturm (the
 * Dobratsch, the Karawanken with the Mittagskogel, the Faaker See), the square with Toni's Dorfbrunnen (St Florian on its
 * pillar) and the church tower's clock, the field with Franz's Doppelharpfe, the stream bank and the pond (the larches,
 * the Carinthian hamlet), each beside the Slovene picture it was, by day and in the evening; the Dorfbrunnen and the
 * Doppelharpfe at every stage of their projects (build/scene-snapshots/looks/kaernten-places/<art>…png). Checked: each
 * looks Carinthia's own, each stage of the two projects differs from the one before in Carinthia's look as in the default
 * (a step's work shows), every thing is drawn and tappable once at every detail, the people stand where they did, and a
 * frame stays inside its budget.
 */
class KaerntenPlacesLooksTest {
    private val dir = File("build/scene-snapshots/looks/kaernten-places").apply { mkdirs() }

    /** The arts, the project whose stages the picture shows (null: none), and who stands at its spots in the previews. */
    private val arts = listOf<Triple<String, String?, List<String>>>(
        Triple("firestation", null, listOf("shepherd", "smith", "farmer")),
        Triple("playground", null, listOf("child2", "child1", "teacher")),
        Triple("lookout", null, listOf("innkeeper", "child1", "grandpa")),
        Triple("square", "vodnjak_na_trgu", listOf("grandpa", "smith", "child1", "child2", "child3", "innkeeper", "teacher", "aunt")),
        Triple("field", "toplar", listOf("farmer", "shepherd", "child3")),
        Triple("stream", null, listOf("grandma", "shepherd", "farmer")),
        Triple("pond", null, listOf("beekeeper", "child2", "grandpa")),
    )

    /** The project's stages to show: every one from none to finished, else only the finished village. */
    private fun stagesOf(project: String?): List<Int?> = if (project == null) listOf(null) else (0..SceneWorld.ALL.stages(project)).toList()

    private fun world(project: String?, stage: Int?, culture: String) =
        (if (project == null || stage == null) SceneWorld.ALL else SceneWorld(projects = mapOf(project to stage))).copy(culture = culture)

    private fun frame(
        art: String, project: String?, stage: Int?, culture: String, hour: Float, sprites: List<String>, people: Boolean = true, month: Int = 6,
        sky: Sky = Sky.CLEAR,
    ) = SceneFrame(
        time = 3.0, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), world = world(project, stage, culture), sky = sky,
        date = java.time.LocalDate.of(2026, month, 15),
        people = if (!people) emptyList() else SceneArt.personSlots.getValue(art).mapIndexed { k, s -> PersonInScene("p-$s", sprites[k % sprites.size], s, pose = if (k == 0) Pose.TALK else Pose.IDLE) },
    )

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = 240, h: Int = 160): PixelCanvas {
        val c = PixelCanvas(w * k, h * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
        return c
    }

    @Test fun `render Carinthia's places beside the Slovene ones, by day and in the evening, at every stage`() {
        for ((art, project, sprites) in arts) {
            val s = 2; val label = 16
            val rows = stagesOf(project).flatMap { st -> listOf(Triple(st, "day", 10.5f), Triple(st, "evening", 19.5f)) }
            val img = BufferedImage(240 * s * 2, (160 * s + label) * rows.size, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            for ((row, r) in rows.withIndex()) for ((col, culture) in listOf("primorska", "kaernten").withIndex()) {
                val c = render(art, frame(art, project, r.first, culture, r.third, sprites, month = if (r.second == "day") 6 else 10))
                val oy = row * (160 * s + label)
                for (y in 0 until 160 * s) for (x in 0 until 240 * s) img.setRGB(col * 240 * s + x, oy + label + y, c.pixels[(y / s) * 240 + x / s])
                g.color = Color.WHITE
                g.drawString("$art, $culture${r.first?.let { ", stage $it" } ?: ""}, ${r.second}", col * 240 * s + 4, oy + 12)
            }
            g.dispose()
            ImageIO.write(img, "png", File(dir, "$art.png"))
            // closer up without people, each culture alone; a winter night, an October dusk, December, a phone in the rain
            for (k in 2..3) for (culture in listOf("primorska", "kaernten")) write(render(art, frame(art, project, null, culture, 10.5f, sprites, people = false), k), File(dir, "$art-k$k-$culture.png"))
            write(render(art, frame(art, project, null, "kaernten", 22f, sprites, month = 1), 2), File(dir, "$art-night-winter.png"))
            write(render(art, frame(art, project, null, "kaernten", 18f, sprites, month = 10), 2), File(dir, "$art-october-dusk.png"))
            write(render(art, frame(art, project, null, "kaernten", 11f, sprites, month = 12), 2), File(dir, "$art-december.png"))
            write(render(art, frame(art, project, null, "kaernten", 9f, sprites, month = 5, sky = Sky(rain = 0.6f, gloom = 0.6f, wind = 0.4f)), 2, 270, 297), File(dir, "$art-phone-rain.png"))
        }
    }

    @Test fun `Carinthia's look draws each place its own way`() {
        for ((art, project, sprites) in arts) {
            assertTrue("Carinthia's look touches $art", CultureLooks.of("kaernten").touches(art))
            for (month in listOf(6, 10, 1)) {
                val a = render(art, frame(art, project, null, "primorska", 10.5f, sprites, people = false, month = month)).pixels.toList()
                val b = render(art, frame(art, project, null, "kaernten", 10.5f, sprites, people = false, month = month)).pixels.toList()
                assertNotEquals("$art in month $month looks Slovene in Carinthia", a, b)
            }
        }
    }

    @Test fun `the Dorfbrunnen and the Doppelharpfe show every step's work in Carinthia's look`() {
        for ((art, project, sprites) in arts) {
            if (project == null) continue
            val shots = stagesOf(project).map { st -> render(art, frame(art, project, st, "kaernten", 10.5f, sprites, people = false)).pixels.toList() }
            for (st in 1 until shots.size) assertNotEquals("$art: $project's stage $st looks as stage ${st - 1} in Carinthia", shots[st - 1], shots[st])
        }
        // the finished Doppelharpfe is Carinthia's own, not the Slovene toplar under the Harpfe's colours only
        val toplar = { culture: String -> render("field", frame("field", "toplar", 6, culture, 10.5f, listOf("farmer"), people = false)) }
        val half = { stage: Int -> render("field", frame("field", "toplar", stage, "kaernten", 10.5f, listOf("farmer"), people = false)).pixels.toList() }
        assertNotEquals("the Doppelharpfe's roof (stage 5) shows over its rails (stage 4)", half(4), half(5))
        assertNotEquals(toplar("primorska").pixels.toList(), toplar("kaernten").pixels.toList())
    }

    @Test fun `the church clock strikes in its last step, twelve, as it does once it runs`() {
        val sprites = listOf("teacher", "smith", "grandpa")
        fun square(stage: Int, strike: Float?, count: Int? = null) = render(
            "square",
            frame("square", "mestna_ura", stage, "kaernten", 12f, sprites, people = false).let { f ->
                f.copy(fx = if (strike != null) mapOf("strike" to strike) else emptyMap(), count = count)
            },
        ).pixels.toList()
        val stages = SceneWorld.ALL.stages("mestna_ura")
        // the first stroke ringing: the bell swung, its ring going out
        val stroke = si.lanisce.lani.game.scene.Strike.at(0) + 0.02f
        assertNotEquals("the last step's strike shows", square(stages - 1, null), square(stages - 1, stroke))
        assertNotEquals("the finished clock's strike shows", square(stages, null), square(stages, stroke))
        assertEquals("before the clock hangs in the tower, no strike", square(stages - 2, null), square(stages - 2, stroke))
        // in its last step the twelfth stroke still rings (the hands at twelve); a finished clock at noon the same
        val twelfth = si.lanisce.lani.game.scene.Strike.at(11) + 0.02f
        assertNotEquals("the twelfth stroke in the last step", square(stages - 1, null), square(stages - 1, twelfth))
    }

    @Test fun `in Carinthia each place draws what it has, once, and the people stand where they did`() {
        for ((art, project, sprites) in arts) for (st in stagesOf(project).let { listOf(it.first(), it.last()).distinct() }) for (k in 1..3) {
            val w = world(project, st, "kaernten")
            val hits = ScenePainters.create(art).render(PixelCanvas(240 * k, 160 * k), frame(art, project, st, "kaernten", 10.5f, sprites), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.objects.getValue(art)) {
                val n = hits.count { it.target == SceneTarget.Thing(slot) }
                val expected = if (SceneFixtures.there(art, slot, w)) 1 else 0
                // the wild animals come and go by the day and the hour: at most one of each
                if (slot in WILD) assertTrue("$art/$slot at stage $st, detail $k: $n", n <= 1)
                else assertEquals("$art/$slot at stage $st, detail $k", expected, n)
            }
            // Primorska's picture with Carinthia's people in it: each culture dresses its people its own way (Wardrobes)
            val home = (ScenePainters.create(art) as ArtPainter).also { it.dressedAs = "kaernten" }.render(PixelCanvas(240 * k, 160 * k), frame(art, project, st, "primorska", 10.5f, sprites), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.personSlots.getValue(art)) {
                val a = home.single { it.target == SceneTarget.Person("p-$slot") }
                val b = hits.single { it.target == SceneTarget.Person("p-$slot") }
                assertEquals("$art: someone at $slot stands where they did (stage $st, detail $k)", listOf(a.anchorX, a.anchorY), listOf(b.anchorX, b.anchorY))
            }
        }
    }

    /** A frame of each art in Carinthia's look, at night with people and a highlight: the best of three rounds of 20, in this thread's CPU time (FrameBudget). */
    @Test fun `a Carinthian frame renders fast`() {
        for ((art, project, sprites) in arts) {
            val c = PixelCanvas(360, 240)
            fun round(culture: String): Double {
                val p = ScenePainters.of(art)
                val f = frame(art, project, null, culture, 22.5f, sprites, month = 10).copy(highlight = SceneArt.objects.getValue(art).first())
                repeat(10) { p.render(c, f.copy(time = it / 12.0)) }
                return FrameBudget.cpuMs { repeat(20) { p.render(c, f.copy(time = 2 + it / 12.0)) } } / 20
            }
            var kaernten = Double.MAX_VALUE; var primorska = Double.MAX_VALUE
            repeat(3) { kaernten = minOf(kaernten, round("kaernten")); primorska = minOf(primorska, round("primorska")) }
            println("ScenePainter %s: %.2f ms/frame in Carinthia, %.2f in Primorska (night, 360x240, the best of three rounds)".format(art, kaernten, primorska))
            assertTrue("$art too slow in Carinthia: $kaernten ms", kaernten < FrameBudget.scaled(20.0))
        }
    }

    private fun write(c: PixelCanvas, f: File) {
        val img = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height) for (x in 0 until c.width) img.setRGB(x, y, c.pixels[y * c.width + x])
        ImageIO.write(img, "png", f)
    }

    private companion object {
        /** The arts' wild animals, which the day brings or doesn't ([WildPainter]). */
        val WILD = setOf("deer", "hare", "butterfly", "fox", "stag", "hedgehog", "boar", "badger", "dormouse", "bat")
    }
}
