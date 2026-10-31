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
import si.lanisce.lani.game.scene.Sky
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Carinthia's buildings and rooms in its own look (companion/SCENES.md "Culture looks"; docs/plans/05-built/places-kaernten.md):
 * the lower Gailtal's village below the Dobratsch, every art its buildings open, each beside the Slovene picture it was, at
 * each of the building's five levels, by day and in the evening (build/scene-snapshots/looks/kaernten/<art>-<building>-
 * <day|evening>.png: the default culture on the left, Carinthia on the right, a row a level), closer up, on a winter night,
 * in December, in Lent, on a phone; and checked: each level looks Carinthia's own, every thing a level has is drawn and
 * tappable once at every detail, the people stand where they did, and a frame stays inside its budget.
 */
class KaerntenLooksTest {
    private val dir = File("build/scene-snapshots/looks/kaernten").apply { mkdirs() }

    /** The arts, the buildings whose level each shows (the square's linden and well, the field's and its Harpfe), and who stands at its spots. */
    private val arts = listOf(
        Triple("kitchen", listOf(BuildingType.HOUSE), listOf("grandma", "child2", "aunt")),
        Triple("livingroom", listOf(BuildingType.HOUSE), listOf("grandpa", "child2", "farmer")),
        Triple("cellar", listOf(BuildingType.HOUSE), listOf("winemaker", "beekeeper", "farmer")),
        Triple("attic", listOf(BuildingType.HOUSE), listOf("child1", "innkeeper", "woman")),
        Triple("workshop", listOf(BuildingType.HOUSE), listOf("farmer", "child3", "grandpa")),
        Triple("smithy", listOf(BuildingType.SMITHY), listOf("smith", "child1", "winemaker")),
        Triple("school", listOf(BuildingType.SCHOOL), listOf("teacher", "child2", "child1", "child3")),
        Triple("tent", listOf(BuildingType.TENT), listOf("shepherd", "child2", "child3")),
        Triple("field", listOf(BuildingType.FIELD, BuildingType.KOZOLEC), listOf("farmer", "child3", "winemaker")),
        Triple("square", listOf(BuildingType.LIPA, BuildingType.WELL), listOf("grandpa", "teacher", "child1", "child2", "innkeeper")),
        Triple("market", listOf(BuildingType.MARKET), listOf("grandma", "innkeeper", "winemaker")),
        Triple("apiary", listOf(BuildingType.BEEHIVE), listOf("beekeeper", "grandpa", "child2")),
        Triple("church", listOf(BuildingType.CHURCH), listOf("aunt", "child3", "grandma")),
        Triple("palisade", listOf(BuildingType.PALISADE), listOf("shepherd", "smith", "child1")),
        Triple("watchtower", listOf(BuildingType.WATCHTOWER), listOf("shepherd", "child1", "farmer")),
    )

    private val levels = 1..Catalog.MAX_LEVEL

    /** Everything built, the scene seen in a [type] at [level], in [culture]'s village. */
    private fun world(type: BuildingType, level: Int, culture: String) = SceneWorld(culture = culture, here = Building("here", type, 0, level = level))

    private fun frame(
        art: String, world: SceneWorld, hour: Float, sprites: List<String>, people: Boolean = true, month: Int = 6, day: Int = 15, sky: Sky = Sky.CLEAR,
    ) = SceneFrame(
        time = 3.0, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), world = world, sky = sky,
        date = java.time.LocalDate.of(2026, month, day),
        people = if (!people) emptyList() else SceneArt.personSlots.getValue(art).mapIndexed { k, s -> PersonInScene("p-$s", sprites[k % sprites.size], s, pose = if (k == 0) Pose.TALK else Pose.IDLE) },
    )

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = 240, h: Int = 160): PixelCanvas {
        val c = PixelCanvas(w * k, h * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
        return c
    }

    /** The arts to render (the environment's `KAERNTEN_ARTS`, a comma list, for a quick look at a few), else all. */
    private val only: Set<String>? = System.getenv("KAERNTEN_ARTS")?.split(',')?.filter { it.isNotBlank() }?.toSet()
    private val chosen get() = arts.filter { only == null || it.first in only }

    @Test fun `render Carinthia's places beside the Slovene ones at every level`() {
        for ((art, types, sprites) in chosen) {
            for (type in types) {
                val tag = type.name.lowercase()
                for ((when_, hour, month) in listOf(Triple("day", 10.5f, 6), Triple("evening", 20f, 10))) {
                    val s = 2; val label = 16
                    val img = BufferedImage(240 * s * 2, (160 * s + label) * levels.count(), BufferedImage.TYPE_INT_RGB)
                    val g = img.createGraphics()
                    for ((row, level) in levels.withIndex()) for ((col, culture) in listOf("primorska", "kaernten").withIndex()) {
                        val c = render(art, frame(art, world(type, level, culture), hour, sprites, month = month))
                        val oy = row * (160 * s + label)
                        for (y in 0 until 160 * s) for (x in 0 until 240 * s) img.setRGB(col * 240 * s + x, oy + label + y, c.pixels[(y / s) * 240 + x / s])
                        g.color = Color.WHITE
                        g.drawString("$art, $culture, $tag level $level, $when_", col * 240 * s + 4, oy + 12)
                    }
                    g.dispose()
                    ImageIO.write(img, "png", File(dir, "$art-$tag-$when_.png"))
                }
            }
            val type = types.first()
            // closer up, without people: the place itself at its top level and its first, each culture alone
            for (level in listOf(1, 3, 5)) for (k in 2..3) write(render(art, frame(art, world(type, level, "kaernten"), 10.5f, sprites, people = false), k), File(dir, "$art-L$level-k$k.png"))
            write(render(art, frame(art, world(type, 5, "primorska"), 10.5f, sprites, people = false), 3), File(dir, "$art-L5-k3-primorska.png"))
            // a winter night, December's morning, an autumn dusk, Lent (the church's Fastentuch), a phone in the rain
            write(render(art, frame(art, world(type, 5, "kaernten"), 22f, sprites, month = 1), 2), File(dir, "$art-night-winter.png"))
            write(render(art, frame(art, world(type, 5, "kaernten"), 11f, sprites, month = 12), 2), File(dir, "$art-december.png"))
            write(render(art, frame(art, world(type, 4, "kaernten"), 18.5f, sprites, month = 10), 2), File(dir, "$art-october-dusk.png"))
            write(render(art, frame(art, world(type, 5, "kaernten"), 10f, sprites, month = 3, day = 10), 2), File(dir, "$art-lent.png"))
            write(render(art, frame(art, world(type, 2, "kaernten"), 9f, sprites, month = 4, sky = Sky(rain = 0.6f, gloom = 0.6f, wind = 0.4f)), 2, 270, 297), File(dir, "$art-phone-L2-rain.png"))
            write(render(art, frame(art, world(type, 5, "kaernten"), 15f, sprites, month = 8), 2, 270, 297), File(dir, "$art-phone-L5.png"))
        }
    }

    @Test fun `Carinthia's look draws each place its own way at every level`() {
        for ((art, types, sprites) in chosen) {
            assertTrue("Carinthia's look touches $art", CultureLooks.of("kaernten").touches(art))
            for (type in types) for (level in levels) {
                val a = render(art, frame(art, world(type, level, "primorska"), 10.5f, sprites, people = false)).pixels.toList()
                val b = render(art, frame(art, world(type, level, "kaernten"), 10.5f, sprites, people = false)).pixels.toList()
                assertNotEquals("$art at $type level $level looks Slovene in Carinthia", a, b)
            }
        }
    }

    @Test fun `in Carinthia each level draws what it has, once, and the people stand where they did`() {
        for ((art, types, sprites) in chosen) for (type in types) for (level in levels) for (k in 1..3) for (hour in listOf(10.5f, 21.5f)) {
            val w = world(type, level, "kaernten")
            val hits = ScenePainters.create(art).render(PixelCanvas(240 * k, 160 * k), frame(art, w, hour, sprites), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.objects.getValue(art)) {
                val n = hits.count { it.target == SceneTarget.Thing(slot) }
                val home = ScenePainters.create(art).render(PixelCanvas(240 * k, 160 * k), frame(art, world(type, level, "primorska"), hour, sprites), Lens(k, 0, 0, 240, 160))
                val want = home.count { it.target == SceneTarget.Thing(slot) }
                assertEquals("$art/$slot at $type level $level, detail $k, $hour h: as many as the default culture's", want, n)
                if (SceneFixtures.there(art, slot, w) && want > 0) assertEquals("$art/$slot once", 1, n)
            }
            val home = ScenePainters.create(art).render(PixelCanvas(240 * k, 160 * k), frame(art, world(type, level, "primorska"), hour, sprites), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.personSlots.getValue(art)) {
                val a = home.singleOrNull { it.target == SceneTarget.Person("p-$slot") } ?: continue
                val b = hits.single { it.target == SceneTarget.Person("p-$slot") }
                assertEquals("$art: someone at $slot stands where they did ($type level $level, detail $k)", listOf(a.anchorX, a.anchorY), listOf(b.anchorX, b.anchorY))
            }
        }
    }

    /** A frame of each art in Carinthia's look and in Primorska's, at night with people and a highlight: the best of three rounds of 20, in this thread's CPU time (FrameBudget). */
    @Test fun `a Carinthian frame renders fast`() {
        for ((art, types, sprites) in chosen) for (level in listOf(1, Catalog.MAX_LEVEL)) {
            val type = types.first()
            val c = PixelCanvas(360, 240)
            fun round(culture: String): Double {
                val p = ScenePainters.of(art)
                val f = frame(art, world(type, level, culture), 22.5f, sprites, month = 10).copy(highlight = SceneArt.objects.getValue(art).first())
                repeat(10) { p.render(c, f.copy(time = it / 12.0)) }
                return FrameBudget.cpuMs { repeat(20) { p.render(c, f.copy(time = 2 + it / 12.0)) } } / 20
            }
            var mine = Double.MAX_VALUE; var primorska = Double.MAX_VALUE
            repeat(3) { mine = minOf(mine, round("kaernten")); primorska = minOf(primorska, round("primorska")) }
            println("ScenePainter %s level %d: %.2f ms/frame in Carinthia, %.2f in Primorska (night, 360x240, the best of three rounds)".format(art, level, mine, primorska))
            assertTrue("$art too slow in Carinthia: $mine ms", mine < FrameBudget.scaled(20.0))
        }
    }

    private fun write(c: PixelCanvas, f: File) {
        val img = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until c.height) for (x in 0 until c.width) img.setRGB(x, y, c.pixels[y * c.width + x])
        ImageIO.write(img, "png", f)
    }
}
