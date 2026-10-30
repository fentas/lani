package si.lanisce.lani.game.render.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.FrameBudget
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.Catalog
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
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Friuli's market and square in their own look (companion/SCENES.md "Culture looks"): Al mercato, a market day in a Collio
 * village, and In piazza, its piazza under the tiglio, each beside the Slovene picture it was (build/scene-snapshots/looks/
 * friuli-<art>-<building>-<day|evening>.png: the default culture on the left, Friuli on the right, a row a level), each
 * level closer up, each culture alone; and checked: each level looks Friuli's own, every thing a level has is drawn and
 * tappable once at every detail, the people stand where they did, and a frame stays inside its budget. The smithy and the
 * school, which grow to their fifth level now, the same way.
 */
class FriuliMarketSquareLooksTest {
    private val dir = File("build/scene-snapshots/looks").apply { mkdirs() }

    /** The arts, the buildings whose level each shows, and who stands at its spots in the previews. */
    private val arts = listOf(
        Triple("market", listOf(BuildingType.MARKET), listOf("grandma", "innkeeper", "winemaker")),
        Triple("square", listOf(BuildingType.LIPA, BuildingType.WELL), listOf("grandpa", "teacher", "child1", "child2", "child3")),
        // the smithy and the school had Friuli's look already; their five levels show in it too
        Triple("smithy", listOf(BuildingType.SMITHY), listOf("smith", "child1", "winemaker")),
        Triple("school", listOf(BuildingType.SCHOOL), listOf("teacher", "child3", "child2", "child1")),
    )

    private val levels = 1..Catalog.MAX_LEVEL

    /** Everything built, the scene seen in a [type] at [level] (the square's other building at the top level), in [culture]'s village. */
    private fun world(type: BuildingType, level: Int, culture: String, landscape: String = si.lanisce.lani.game.Landscape.CLASSIC.id) =
        SceneWorld(culture = culture, landscape = landscape, here = Building("here", type, 0, level = level))

    private fun frame(art: String, world: SceneWorld, hour: Float, sprites: List<String>, people: Boolean = true, month: Int = 6) = SceneFrame(
        time = 3.0, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), world = world,
        people = if (!people) emptyList() else SceneArt.personSlots.getValue(art).mapIndexed { k, s -> PersonInScene("p-$s", sprites[k % sprites.size], s, pose = if (k == 0) Pose.TALK else Pose.IDLE) },
    )

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = 240, h: Int = 160): PixelCanvas {
        val c = PixelCanvas(w * k, h * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
        return c
    }

    @Test fun `render Friuli's market and square beside the Slovene ones`() {
        for ((art, types, sprites) in arts) for (type in types) {
            val tag = type.name.lowercase()
            for ((when_, hour, month) in listOf(Triple("day", 10.5f, 6), Triple("evening", 20f, 10))) {
                val s = 2; val label = 16
                val img = BufferedImage(240 * s * 2, (160 * s + label) * levels.count(), BufferedImage.TYPE_INT_RGB)
                val g = img.createGraphics()
                for ((row, level) in levels.withIndex()) for ((col, culture) in listOf("primorska", "friuli").withIndex()) {
                    val c = render(art, frame(art, world(type, level, culture), hour, sprites, month = month))
                    val oy = row * (160 * s + label)
                    for (y in 0 until 160 * s) for (x in 0 until 240 * s) img.setRGB(col * 240 * s + x, oy + label + y, c.pixels[(y / s) * 240 + x / s])
                    g.color = Color.WHITE
                    g.drawString("$art, $culture, $tag level $level, $when_", col * 240 * s + 4, oy + 12)
                }
                g.dispose()
                ImageIO.write(img, "png", File(dir, "friuli-$art-$tag-$when_.png"))
            }
            // closer up, without people: the place itself at every level, each culture alone
            for (level in levels) for (k in 2..3) for (culture in listOf("primorska", "friuli")) {
                write(render(art, frame(art, world(type, level, culture), 10.5f, sprites, people = false), k), File(dir, "friuli-$art-$tag-L$level-k$k-$culture.png"))
            }
        }
        // the seasons and the coast in Friuli's look: spring, autumn, a winter night; Teo's coast
        for ((art, types, sprites) in arts) {
            val type = types.first()
            for ((name, month, hour) in listOf(Triple("spring", 4, 10.5f), Triple("autumn", 10, 15f), Triple("winter-night", 1, 21.5f))) {
                write(render(art, frame(art, world(type, Catalog.MAX_LEVEL, "friuli"), hour, sprites, month = month), 2), File(dir, "friuli-$art-$name.png"))
            }
            write(render(art, frame(art, world(type, Catalog.MAX_LEVEL, "friuli", si.lanisce.lani.game.Landscape.COAST.id), 10.5f, sprites), 2), File(dir, "friuli-$art-coast.png"))
        }
    }

    @Test fun `Friuli's look draws the market and the square their own way at every level`() {
        for ((art, types, sprites) in arts) {
            assertTrue("Friuli's look touches $art", CultureLooks.of("friuli").touches(art))
            for (type in types) for (level in levels) {
                val a = render(art, frame(art, world(type, level, "primorska"), 10.5f, sprites, people = false)).pixels.toList()
                val b = render(art, frame(art, world(type, level, "friuli"), 10.5f, sprites, people = false)).pixels.toList()
                assertNotEquals("$art at $type's level $level looks Slovene in Friuli", a, b)
            }
        }
    }

    @Test fun `in Friuli each level draws what it has, once, and the people stand where they did`() {
        for ((art, types, sprites) in arts) for (type in types) for (level in levels) for (k in 1..3) {
            val w = world(type, level, "friuli")
            val hits = ScenePainters.create(art).render(PixelCanvas(240 * k, 160 * k), frame(art, w, 10.5f, sprites), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.objects.getValue(art)) {
                val n = hits.count { it.target == SceneTarget.Thing(slot) }
                // the square's sun is the moon's at night: by day it is there
                assertEquals("$art/$slot at $type's level $level, detail $k", if (SceneFixtures.there(art, slot, w)) 1 else 0, n)
            }
            // the people where the Slovene picture has them: the spots stay
            val home = ScenePainters.create(art).render(PixelCanvas(240 * k, 160 * k), frame(art, world(type, level, "primorska"), 10.5f, sprites), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.personSlots.getValue(art)) {
                val a = home.single { it.target == SceneTarget.Person("p-$slot") }
                val b = hits.single { it.target == SceneTarget.Person("p-$slot") }
                assertEquals("$art: someone at $slot stands where they did ($type's level $level, detail $k)", listOf(a.anchorX, a.anchorY), listOf(b.anchorX, b.anchorY))
            }
        }
    }

    /**
     * The square's dialog effects at every level of its linden and its well, in both cultures: the cat up the young linden and
     * on her way down it (there to tap, above the bench), the ball lost under the bench (the bench's to tap), Muri at the
     * flowers; the market's hen on Matteo's cart at the market's first level. A sheet for review: build/scene-snapshots/looks/
     * friuli-square-cues.png.
     */
    @Test fun `the square's cues work at every level, in both cultures`() {
        val stages = listOf(0f, 0.2f, 0.5f, 0.8f, 0.95f)
        val s = 2; val label = 14
        val cultures = listOf("primorska", "friuli")
        val img = BufferedImage(240 * s * stages.size, (160 * s + label) * levels.count() * cultures.size, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        for ((ci, culture) in cultures.withIndex()) for ((row, level) in levels.withIndex()) for ((col, cat) in stages.withIndex()) {
            val w = world(BuildingType.LIPA, level, culture).copy(here = Building("here", BuildingType.LIPA, 0, level = level))
            val f = frame("square", w, 10.5f, listOf("grandpa", "teacher", "child1", "child2", "child3")).copy(fx = mapOf("cat" to cat, "ball" to 0f, "dog" to 0.5f))
            val c = PixelCanvas(240, 160)
            val hits = ScenePainters.create("square").render(c, f, Lens(1, 0, 0, 240, 160))
            val catHit = hits.singleOrNull { it.target == SceneTarget.Thing("cat") }
            assertTrue("the cat at $cat, linden level $level ($culture)", catHit != null)
            val bench = hits.single { it.target == SceneTarget.Thing("bench") }
            // up the linden, on its limb, she is above the bench (down its trunk she comes to it, the sooner the younger the tree)
            if (cat < 0.3f) assertTrue("the cat up the linden at $cat is above the bench (level $level, $culture)", catHit!!.bottom <= bench.top + 2)
            assertTrue("the lost ball is the bench's (level $level, $culture)", hits.none { it.target == SceneTarget.Thing("ball") })
            val oy = (ci * levels.count() + row) * (160 * s + label)
            for (y in 0 until 160 * s) for (x in 0 until 240 * s) img.setRGB(col * 240 * s + x, oy + label + y, c.pixels[(y / s) * 240 + x / s])
            g.color = Color.WHITE
            g.drawString("$culture, linden level $level, cat $cat", col * 240 * s + 4, oy + 11)
        }
        g.dispose()
        ImageIO.write(img, "png", File(dir, "friuli-square-cues.png"))
        // the market's hen on the cart at every level: a tap on her there is a tap on the cart
        for (level in levels) for (culture in cultures) {
            val f = frame("market", world(BuildingType.MARKET, level, culture), 10.5f, listOf("grandma", "innkeeper", "winemaker")).copy(fx = mapOf("hen" to 1f))
            val hits = ScenePainters.create("market").render(PixelCanvas(240, 160), f, Lens(1, 0, 0, 240, 160))
            assertEquals("the hen's coop and the cart at the market's level $level ($culture)", 2, hits.count { it.target == SceneTarget.Thing("hen") || it.target == SceneTarget.Thing("cart") })
        }
    }

    /**
     * A frame of each art in Friuli's look and in Primorska's, at night with people and a highlight: the best of three rounds
     * of 20 frames each, the two cultures by turns in this thread's CPU time (FrameBudget).
     */
    @Test fun `a Friuli frame renders fast`() {
        for ((art, types, sprites) in arts) {
            val c = PixelCanvas(360, 240)
            fun round(culture: String): Double {
                val p = ScenePainters.of(art)
                val f = frame(art, world(types.first(), Catalog.MAX_LEVEL, culture), 22.5f, sprites, month = 10).copy(highlight = SceneArt.objects.getValue(art).first())
                repeat(10) { p.render(c, f.copy(time = it / 12.0)) }
                return FrameBudget.cpuMs { repeat(20) { p.render(c, f.copy(time = 2 + it / 12.0)) } } / 20
            }
            var friuli = Double.MAX_VALUE; var primorska = Double.MAX_VALUE
            repeat(3) { friuli = minOf(friuli, round("friuli")); primorska = minOf(primorska, round("primorska")) }
            println("ScenePainter %s: %.2f ms/frame in Friuli, %.2f in Primorska (night, 360x240, the best of three rounds)".format(art, friuli, primorska))
            assertTrue("$art too slow in Friuli: $friuli ms", friuli < FrameBudget.scaled(20.0))
        }
    }

    private fun write(c: PixelCanvas, f: File) {
        val img = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height) for (x in 0 until c.width) img.setRGB(x, y, c.pixels[y * c.width + x])
        ImageIO.write(img, "png", f)
    }
}
