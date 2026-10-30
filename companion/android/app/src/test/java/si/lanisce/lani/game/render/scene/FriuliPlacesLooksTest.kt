package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.FrameBudget
import si.lanisce.lani.game.BuildingType
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
 * Friuli's buildings and rooms in its own look (companion/SCENES.md "Culture looks"; docs/plans/05-built/places-it-buildings.md):
 * the view from Friuli's watchtower (Sulla torre), each beside the Slovene picture it was, at each of the building's five
 * levels, by day and in the evening, closer up, on a winter night and on Teo's coast (build/scene-snapshots/looks/
 * friuli-<art>…png); and checked: each level looks Friuli's own, every thing a level has is drawn and tappable once at
 * every detail, the people stand where they did, and a frame stays inside its budget.
 */
class FriuliPlacesLooksTest {
    private val dir = File("build/scene-snapshots/looks").apply { mkdirs() }

    /** The arts: the building it is seen in, and who stands at its spots in the previews. */
    private val arts = listOf(
        Triple("watchtower", BuildingType.WATCHTOWER, listOf("shepherd", "child1", "farmer")),
    )

    private val levels = 1..si.lanisce.lani.game.Catalog.MAX_LEVEL

    private fun world(type: BuildingType, level: Int, culture: String, land: String = si.lanisce.lani.game.Landscape.CLASSIC.id) =
        SceneWorld.room(level, type).copy(culture = culture, landscape = land)

    private fun frame(
        art: String, type: BuildingType, level: Int, culture: String, hour: Float, sprites: List<String>, people: Boolean = true, month: Int = 6,
        land: String = si.lanisce.lani.game.Landscape.CLASSIC.id, sky: Sky = Sky.CLEAR,
    ) = SceneFrame(
        time = 3.0, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), world = world(type, level, culture, land), sky = sky,
        people = if (!people) emptyList() else SceneArt.personSlots.getValue(art).mapIndexed { k, s -> PersonInScene("p-$s", sprites[k % sprites.size], s, pose = if (k == 0) Pose.TALK else Pose.IDLE) },
    )

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = 240, h: Int = 160): PixelCanvas {
        val c = PixelCanvas(w * k, h * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
        return c
    }

    @Test fun `render Friuli's places beside the Slovene ones at every level`() {
        for ((art, type, sprites) in arts) {
            val s = 2; val label = 16
            val rows = levels.flatMap { l -> listOf(Triple(l, "day", 10.5f), Triple(l, "evening", 20f)) }
            val img = BufferedImage(240 * s * 2, (160 * s + label) * rows.size, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            for ((row, r) in rows.withIndex()) for ((col, culture) in listOf("primorska", "friuli").withIndex()) {
                val c = render(art, frame(art, type, r.first, culture, r.third, sprites, month = if (r.second == "day") 6 else 10))
                val oy = row * (160 * s + label)
                for (y in 0 until 160 * s) for (x in 0 until 240 * s) img.setRGB(col * 240 * s + x, oy + label + y, c.pixels[(y / s) * 240 + x / s])
                g.color = Color.WHITE
                g.drawString("$art, $culture, level ${r.first}, ${r.second}", col * 240 * s + 4, oy + 12)
            }
            g.dispose()
            ImageIO.write(img, "png", File(dir, "friuli-$art.png"))
            // closer up, without people: the place itself, at every level, each culture alone
            for (level in levels) for (k in 2..3) for (culture in listOf("primorska", "friuli")) {
                write(render(art, frame(art, type, level, culture, 10.5f, sprites, people = false), k), File(dir, "friuli-$art-L$level-k$k-$culture.png"))
            }
            // a winter night, an autumn afternoon on the coast, a phone's view at dusk
            write(render(art, frame(art, type, 5, "friuli", 22f, sprites, month = 1), 2), File(dir, "friuli-$art-night-winter.png"))
            write(render(art, frame(art, type, 3, "friuli", 15f, sprites, month = 10, land = "coast"), 2), File(dir, "friuli-$art-coast-L3.png"))
            write(render(art, frame(art, type, 5, "friuli", 18.5f, sprites, month = 9), 2, 270, 297), File(dir, "friuli-$art-phone-L5.png"))
            write(render(art, frame(art, type, 2, "friuli", 9f, sprites, month = 4, sky = Sky(rain = 0.6f, gloom = 0.6f, wind = 0.4f)), 2, 270, 297), File(dir, "friuli-$art-phone-L2-rain.png"))
        }
    }

    @Test fun `Friuli's look draws each place its own way at every level`() {
        for ((art, type, sprites) in arts) {
            assertTrue("Friuli's look touches $art", CultureLooks.of("friuli").touches(art))
            for (level in levels) {
                val a = render(art, frame(art, type, level, "primorska", 10.5f, sprites, people = false)).pixels.toList()
                val b = render(art, frame(art, type, level, "friuli", 10.5f, sprites, people = false)).pixels.toList()
                assertNotEquals("$art at level $level looks Slovene in Friuli", a, b)
            }
        }
    }

    @Test fun `in Friuli each level draws what it has, once, and the people stand where they did`() {
        for ((art, type, sprites) in arts) for (level in levels) for (k in 1..3) for (land in listOf("classic", "coast")) {
            val w = world(type, level, "friuli", land)
            val hits = ScenePainters.create(art).render(PixelCanvas(240 * k, 160 * k), frame(art, type, level, "friuli", 10.5f, sprites, land = land), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.objects.getValue(art)) {
                val n = hits.count { it.target == SceneTarget.Thing(slot) }
                assertEquals("$art/$slot at level $level, detail $k, $land", if (SceneFixtures.there(art, slot, w)) 1 else 0, n)
            }
            val home = ScenePainters.create(art).render(PixelCanvas(240 * k, 160 * k), frame(art, type, level, "primorska", 10.5f, sprites), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.personSlots.getValue(art)) {
                val a = home.single { it.target == SceneTarget.Person("p-$slot") }
                val b = hits.single { it.target == SceneTarget.Person("p-$slot") }
                assertEquals("$art: someone at $slot stands where they did (level $level, detail $k)", listOf(a.anchorX, a.anchorY), listOf(b.anchorX, b.anchorY))
            }
        }
    }

    /** A frame of each art in Friuli's look and in Primorska's, at night with people and a highlight: the best of three rounds of 20. */
    @Test fun `a Friuli frame renders fast`() {
        for ((art, type, sprites) in arts) for (level in listOf(1, 5)) {
            val c = PixelCanvas(360, 240)
            fun round(culture: String): Double {
                val p = ScenePainters.of(art)
                val f = frame(art, type, level, culture, 22.5f, sprites, month = 10).copy(highlight = SceneArt.objects.getValue(art).first())
                repeat(10) { p.render(c, f.copy(time = it / 12.0)) }
                val t0 = System.nanoTime()
                repeat(20) { p.render(c, f.copy(time = 2 + it / 12.0)) }
                return (System.nanoTime() - t0) / 1e6 / 20
            }
            var friuli = Double.MAX_VALUE; var primorska = Double.MAX_VALUE
            repeat(3) { friuli = minOf(friuli, round("friuli")); primorska = minOf(primorska, round("primorska")) }
            println("ScenePainter %s level %d: %.2f ms/frame in Friuli, %.2f in Primorska (night, 360x240, the best of three rounds)".format(art, level, friuli, primorska))
            assertTrue("$art too slow in Friuli: $friuli ms", friuli < FrameBudget.scaled(20.0))
        }
    }

    private fun write(c: PixelCanvas, f: File) {
        val img = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height) for (x in 0 until c.width) img.setRGB(x, y, c.pixels[y * c.width + x])
        ImageIO.write(img, "png", f)
    }
}
