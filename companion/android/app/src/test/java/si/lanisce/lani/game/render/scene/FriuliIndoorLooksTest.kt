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
import si.lanisce.lani.game.scene.CuratedContent
import si.lanisce.lani.game.scene.inWorld
import si.lanisce.lani.game.scene.parseScene
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFixtures
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.ScenePainters
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.SceneWorld
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Friuli's indoor places in its own look (companion/SCENES.md "Culture looks"): Nonna Rosa's kitchen with the fogolâr,
 * Fabbro Bruno's smithy, the tent at the camp and Mugnaio Franco's mill, each beside the Slovene picture it was
 * (build/scene-snapshots/looks/friuli-<art>…png: the default culture on the left, Friuli on the right), at every level
 * and closer up; and checked: each looks Friuli's own, every thing a level has is drawn and tappable once, the people
 * stand where they did, and a frame stays inside its budget.
 */
class FriuliIndoorLooksTest {
    private val dir = File("build/scene-snapshots/looks").apply { mkdirs() }

    /** The arts: the building a room is seen in (null: no level), and who stands at its spots in the previews. */
    private val arts = listOf(
        Triple("kitchen", BuildingType.HUT, listOf("grandma", "aunt", "child2")),
        Triple("smithy", BuildingType.SMITHY, listOf("smith", "child1", "winemaker")),
        Triple("tent", null, listOf("child3", "child2", "shepherd")),
        Triple("mill", null, listOf("farmer", "child3", "grandma")),
    )

    private fun world(type: BuildingType?, level: Int, culture: String) =
        (if (type == null) SceneWorld.ALL else SceneWorld.room(level, type)).copy(culture = culture)

    private fun frame(art: String, type: BuildingType?, level: Int, culture: String, hour: Float, sprites: List<String>, people: Boolean = true, month: Int = 6) = SceneFrame(
        time = 3.0, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), world = world(type, level, culture),
        people = if (!people) emptyList() else SceneArt.personSlots.getValue(art).mapIndexed { k, s -> PersonInScene("p-$s", sprites[k % sprites.size], s, pose = if (k == 0) Pose.TALK else Pose.IDLE) },
    )

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = 240, h: Int = 160): PixelCanvas {
        val c = PixelCanvas(w * k, h * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
        return c
    }

    private fun levels(type: BuildingType?) = if (type == null) listOf(3) else listOf(1, 2, 3)

    @Test fun `render Friuli's indoor places beside the Slovene ones`() {
        for ((art, type, sprites) in arts) {
            val s = 2; val label = 16
            val rows = levels(type).flatMap { l -> listOf(Triple(l, "day", 10.5f), Triple(l, "evening", 20f)) }
            val img = BufferedImage(240 * s * 2, (160 * s + label) * rows.size, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            for ((row, r) in rows.withIndex()) for ((col, culture) in listOf("primorska", "friuli").withIndex()) {
                val c = render(art, frame(art, type, r.first, culture, r.third, sprites))
                val oy = row * (160 * s + label)
                for (y in 0 until 160 * s) for (x in 0 until 240 * s) img.setRGB(col * 240 * s + x, oy + label + y, c.pixels[(y / s) * 240 + x / s])
                g.color = Color.WHITE
                g.drawString("$art, $culture${if (type != null) ", level ${r.first}" else ""}, ${r.second}", col * 240 * s + 4, oy + 12)
            }
            g.dispose()
            ImageIO.write(img, "png", File(dir, "friuli-$art.png"))
            // closer up, without people: the place itself, at every level, each culture alone
            for (level in levels(type)) for (k in 2..3) for (culture in listOf("primorska", "friuli")) {
                val suffix = if (type != null) "-L$level" else ""
                write(render(art, frame(art, type, level, culture, 10.5f, sprites, people = false), k), File(dir, "friuli-$art$suffix-k$k-$culture.png"))
            }
            // a winter night (the lamps, the fire, the snow at the window)
            write(render(art, frame(art, type, 3, "friuli", 22f, sprites, month = 1), 2), File(dir, "friuli-$art-night-winter.png"))
        }
    }

    @Test fun `Friuli's look draws every indoor place its own way`() {
        for ((art, type, sprites) in arts) {
            assertTrue("Friuli's look touches $art", CultureLooks.of("friuli").touches(art))
            for (level in levels(type)) {
                val a = render(art, frame(art, type, level, "primorska", 10.5f, sprites, people = false)).pixels.toList()
                val b = render(art, frame(art, type, level, "friuli", 10.5f, sprites, people = false)).pixels.toList()
                assertNotEquals("$art at level $level looks Slovene in Friuli", a, b)
            }
        }
    }

    @Test fun `in Friuli each level draws what it has, once, and the people stand where they did`() {
        for ((art, type, sprites) in arts) for (level in levels(type)) for (k in 1..3) {
            val w = world(type, level, "friuli")
            val hits = ScenePainters.create(art).render(PixelCanvas(240 * k, 160 * k), frame(art, type, level, "friuli", 10.5f, sprites), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.objects.getValue(art)) {
                val n = hits.count { it.target == SceneTarget.Thing(slot) }
                assertEquals("$art/$slot at level $level, detail $k", if (SceneFixtures.there(art, slot, w)) 1 else 0, n)
            }
            // the people where the Slovene picture has them: the stage's slots stay
            val home = ScenePainters.create(art).render(PixelCanvas(240 * k, 160 * k), frame(art, type, level, "primorska", 10.5f, sprites), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.personSlots.getValue(art)) {
                val a = home.single { it.target == SceneTarget.Person("p-$slot") }
                val b = hits.single { it.target == SceneTarget.Person("p-$slot") }
                assertEquals("$art: someone at $slot stands where they did (level $level, detail $k)", listOf(a.anchorX, a.anchorY), listOf(b.anchorX, b.anchorY))
            }
        }
    }

    /**
     * A frame of each art in Friuli's look and in Primorska's, at night with people and a highlight: the best of three
     * rounds of 20 frames each, the two cultures by turns, in this thread's CPU time (FrameBudget).
     */
    @Test fun `a Friuli frame renders fast`() {
        for ((art, type, sprites) in arts) {
            val c = PixelCanvas(360, 240)
            fun round(culture: String): Double {
                val p = ScenePainters.of(art)
                val f = frame(art, type, 3, culture, 22.5f, sprites, month = 10).copy(highlight = SceneArt.objects.getValue(art).first())
                repeat(10) { p.render(c, f.copy(time = it / 12.0)) }
                return FrameBudget.cpuMs { repeat(20) { p.render(c, f.copy(time = 2 + it / 12.0)) } } / 20
            }
            var friuli = Double.MAX_VALUE; var primorska = Double.MAX_VALUE
            repeat(3) { friuli = minOf(friuli, round("friuli")); primorska = minOf(primorska, round("primorska")) }
            println("ScenePainter %s: %.2f ms/frame in Friuli, %.2f in Primorska (night, 360x240, the best of three rounds)".format(art, friuli, primorska))
            assertTrue("$art too slow in Friuli: $friuli ms", friuli < FrameBudget.scaled(20.0))
        }
    }

    @Test fun `Friuli's fogolâr is a word of its kitchen at every level, the spolert from the second`() {
        val kitchen = parseScene(CuratedContent.companion.resolve("cultures/friuli/scenes/in-cucina.json").readText())
        for (level in 1..3) {
            val here = kitchen.inWorld(SceneWorld.room(level, BuildingType.HUT).copy(culture = "friuli")).objects.map { it.slot }
            assertTrue("the fogolâr at level $level", "hearth" in here)
            assertEquals("the spolert at level $level", level >= 2, "stove" in here)
            // the default culture's open hearth goes as its stove comes
            assertEquals(level == 1, SceneFixtures.there("kitchen", "hearth", SceneWorld.room(level, BuildingType.HUT)))
        }
        // its sticker is cut from the kitchen with everything (the hood over it), not from the bare hut's
        assertEquals(si.lanisce.lani.game.Catalog.MAX_LEVEL, SceneFixtures.showing("kitchen", "hearth", "friuli").hereLevel)
        assertEquals(1, SceneFixtures.showing("kitchen", "hearth").hereLevel)
        val a = Stickers.cut("kitchen", "hearth", 1)!!; val b = Stickers.cut("kitchen", "hearth", 1, culture = "friuli")!!
        assertNotEquals(a.argb.toList(), b.argb.toList())
    }

    @Test fun `a culture's scenes say the needs of the things its look draws only when built`() {
        val fails = ArrayList<String>()
        for (dir in CuratedContent.companion.resolve("cultures").listFiles().orEmpty().filter { it.isDirectory }) {
            for (f in dir.resolve("scenes").listFiles { x -> x.extension == "json" }.orEmpty()) {
                val scene = parseScene(f.readText())
                for (o in scene.objects) {
                    val fixed = CultureLooks.of(dir.name).fixes(scene.art, o.slot)
                    val need = SceneFixtures.of(scene.art, o.slot, dir.name)
                    if ((need != null || fixed) && need != o.needs) fails += "${dir.name}/${scene.id}/${o.slot}: ${o.needs}, the picture's $need"
                }
            }
        }
        assertEquals(emptyList<String>(), fails)
    }

    private fun write(c: PixelCanvas, f: File) {
        val img = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height) for (x in 0 until c.width) img.setRGB(x, y, c.pixels[y * c.width + x])
        ImageIO.write(img, "png", f)
    }
}
