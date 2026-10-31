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
 * The Lake District's look of every art its buildings open (companion/SCENES.md "Culture looks";
 * docs/plans/05-built/places-lakeland.md): the rooms of its houses (the range and the oak dresser in Granny Maggie's
 * kitchen, Old Wilf's fireplace and settles, slate flags and slate-stone walls, the fells through the windows), the tent,
 * the smithy, the dale church, the school, the field's stone barn, the village green with its oak and its pump, the market,
 * the bee garden's bee boles and WBC hives, the view from the watchtower and the land beyond the gate. Each beside the
 * default (Primorska's) picture at every level of its building, by day and in the evening, closer up, on a winter night and
 * on a phone (build/scene-snapshots/looks/lakeland-<art>…png); and checked: each level looks the Lake District's own, every
 * thing a level has is drawn and tappable once at every detail, the people stand where they did, a frame stays inside its
 * budget.
 */
class LakelandLooksTest {
    private val dir = File("build/scene-snapshots/looks").apply { mkdirs() }

    /** An art, the building its levels are of (and a second one that grows in it too), and who stands at its spots in the previews. */
    private class Art(val art: String, val type: BuildingType, val also: BuildingType? = null, val sprites: List<String>)

    private val arts = listOf(
        Art("kitchen", BuildingType.HOUSE, sprites = listOf("grandma", "aunt", "child2")),
        Art("livingroom", BuildingType.HOUSE, sprites = listOf("grandpa", "child2", "farmer")),
        Art("cellar", BuildingType.HOUSE, sprites = listOf("winemaker", "innkeeper", "beekeeper")),
        Art("attic", BuildingType.HOUSE, sprites = listOf("child1", "innkeeper", "woman")),
        Art("workshop", BuildingType.HOUSE, sprites = listOf("farmer", "child3", "grandpa")),
        Art("tent", BuildingType.TENT, sprites = listOf("shepherd", "child3", "child2")),
        Art("smithy", BuildingType.SMITHY, sprites = listOf("smith", "child1", "winemaker")),
        Art("church", BuildingType.CHURCH, sprites = listOf("aunt", "grandma", "child3")),
        Art("school", BuildingType.SCHOOL, sprites = listOf("teacher", "child2", "child3", "child1")),
        Art("field", BuildingType.FIELD, BuildingType.KOZOLEC, listOf("farmer", "child3", "farmer")),
        Art("square", BuildingType.LIPA, BuildingType.WELL, listOf("grandpa", "teacher", "child1", "child2", "child3")),
        Art("market", BuildingType.MARKET, sprites = listOf("grandma", "innkeeper", "winemaker")),
        Art("apiary", BuildingType.BEEHIVE, sprites = listOf("beekeeper", "child2", "grandpa")),
        Art("watchtower", BuildingType.WATCHTOWER, sprites = listOf("shepherd", "child1", "farmer")),
        Art("palisade", BuildingType.PALISADE, sprites = listOf("smith", "child1", "shepherd")),
    )

    private val levels = (1..Catalog.MAX_LEVEL).toList()

    private fun world(type: BuildingType, level: Int, culture: String) =
        SceneWorld(culture = culture, here = Building("here", type, 0, level = level))

    private fun frame(
        a: Art, level: Int, culture: String, hour: Float, people: Boolean = true, month: Int = 6, sky: Sky = Sky.CLEAR,
        type: BuildingType = a.type,
    ) = SceneFrame(
        time = 3.0, hour = hour, month = month, objects = SceneArt.objects.getValue(a.art).toSet(), world = world(type, level, culture), sky = sky,
        people = if (!people) emptyList() else SceneArt.personSlots.getValue(a.art).mapIndexed { k, s -> PersonInScene("p-$s", a.sprites[k % a.sprites.size], s, pose = if (k == 0) Pose.TALK else Pose.IDLE) },
    )

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = 240, h: Int = 160): PixelCanvas {
        val c = PixelCanvas(w * k, h * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
        return c
    }

    private fun sheet(name: String, rows: List<Pair<String, (String) -> SceneFrame>>, art: String) {
        val s = 2; val label = 16
        val cs = listOf("primorska", "lakeland")
        val img = BufferedImage(240 * s * cs.size, (160 * s + label) * rows.size, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        for ((row, r) in rows.withIndex()) for ((col, culture) in cs.withIndex()) {
            val c = render(art, r.second(culture))
            val oy = row * (160 * s + label)
            for (y in 0 until 160 * s) for (x in 0 until 240 * s) img.setRGB(col * 240 * s + x, oy + label + y, c.pixels[(y / s) * 240 + x / s])
            g.color = Color.WHITE
            g.drawString("$art, $culture, ${r.first}", col * 240 * s + 4, oy + 12)
        }
        g.dispose()
        ImageIO.write(img, "png", File(dir, name))
    }

    @Test fun `render the Lake District's places beside the default ones at every level`() {
        val only = System.getProperty("lakeland.only")?.split(',')?.toSet()
        for (a in arts) {
            if (only != null && a.art !in only) continue
            sheet("lakeland-${a.art}.png", levels.flatMap { l ->
                listOf("level $l, day" to { c: String -> frame(a, l, c, 10.5f) }, "level $l, evening" to { c: String -> frame(a, l, c, 20f, month = 10) })
            }, a.art)
            a.also?.let { t ->
                sheet("lakeland-${a.art}-${t.name.lowercase()}.png", levels.map { l -> "${t.name.lowercase()} level $l, day" to { c: String -> frame(a, l, c, 10.5f, type = t) } }, a.art)
            }
            // the seasons and the weather: spring, autumn, a winter day, a winter night, rain
            sheet("lakeland-${a.art}-seasons.png", listOf(
                "April morning" to { c: String -> frame(a, 5, c, 9f, month = 4) },
                "October afternoon" to { c: String -> frame(a, 5, c, 15f, month = 10) },
                "January day" to { c: String -> frame(a, 5, c, 12f, month = 1) },
                "January night" to { c: String -> frame(a, 5, c, 22.5f, month = 1) },
                "rain in May" to { c: String -> frame(a, 3, c, 11f, month = 5, sky = Sky(rain = 0.6f, gloom = 0.6f, wind = 0.4f)) },
            ), a.art)
            // closer up, without people: the place itself at its first and its top level
            for (level in listOf(1, 3, 5)) for (k in 2..3) write(render(a.art, frame(a, level, "lakeland", 10.5f, people = false), k), File(dir, "lakeland-${a.art}-L$level-k$k.png"))
            // a phone's view at dusk
            write(render(a.art, frame(a, 5, "lakeland", 18.5f, month = 9), 2, 270, 297), File(dir, "lakeland-${a.art}-phone.png"))
        }
    }

    @Test fun `the Lake District's look draws each place its own way at every level`() {
        for (a in arts) {
            assertTrue("Lakeland's look touches ${a.art}", CultureLooks.of("lakeland").touches(a.art))
            for (type in listOfNotNull(a.type, a.also)) for (level in levels) {
                val p = render(a.art, frame(a, level, "primorska", 10.5f, people = false, type = type)).pixels
                val l = render(a.art, frame(a, level, "lakeland", 10.5f, people = false, type = type)).pixels
                val n = p.indices.count { p[it] != l[it] }
                assertTrue("${a.art} ($type) at level $level looks Slovene in the Lake District: $n pixels differ", n > 600)
            }
        }
    }

    @Test fun `in the Lake District each level draws what it has, once, and the people stand where they did`() {
        val failed = ArrayList<String>()
        for (a in arts) for (type in listOfNotNull(a.type, a.also)) for (level in levels) for (k in 1..3) {
            val w = world(type, level, "lakeland")
            val f = frame(a, level, "lakeland", 10.5f, type = type)
            val hits = ScenePainters.create(a.art).render(PixelCanvas(240 * k, 160 * k), f, Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.objects.getValue(a.art)) {
                if (a.art in WILD_ARTS && slot in WILD) continue
                val n = hits.count { it.target == SceneTarget.Thing(slot) }
                val want = if (SceneFixtures.there(a.art, slot, w)) 1 else 0
                if (n != want) failed += "${a.art}/$slot ($type) at level $level, detail $k: tappable $n times, not $want"
            }
            val home = ScenePainters.create(a.art).render(PixelCanvas(240 * k, 160 * k), frame(a, level, "primorska", 10.5f, type = type), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.personSlots.getValue(a.art)) {
                val x = home.single { it.target == SceneTarget.Person("p-$slot") }
                val y = hits.single { it.target == SceneTarget.Person("p-$slot") }
                if (listOf(x.anchorX, x.anchorY) != listOf(y.anchorX, y.anchorY)) failed += "${a.art}: someone at $slot doesn't stand where they did (level $level, detail $k)"
            }
        }
        assertEquals(emptyList<String>(), failed)
    }

    @Test fun `the default culture's pictures don't change`() {
        // the Lake District's look is its own: an art drawn for Primorska is the art drawn for no culture at all
        for (a in arts) {
            val p = render(a.art, frame(a, 3, "primorska", 10.5f)).pixels.toList()
            val none = render(a.art, frame(a, 3, "nowhere", 10.5f)).pixels.toList()
            assertEquals("${a.art}: the default culture draws as a culture without a look", none, p)
            val l = render(a.art, frame(a, 3, "lakeland", 10.5f)).pixels.toList()
            assertNotEquals("${a.art}: the Lake District draws its own", p, l)
        }
    }

    /** A frame of each art in the Lake District's look and in Primorska's, at night with people and a highlight: the best of three rounds of 20, in this thread's CPU time (FrameBudget). */
    @Test fun `a Lake District frame renders fast`() {
        for (a in arts) for (level in listOf(1, Catalog.MAX_LEVEL)) {
            val c = PixelCanvas(360, 240)
            fun round(culture: String): Double {
                val p = ScenePainters.of(a.art)
                val f = frame(a, level, culture, 22.5f, month = 10).copy(highlight = SceneArt.objects.getValue(a.art).first())
                repeat(10) { p.render(c, f.copy(time = it / 12.0)) }
                return FrameBudget.cpuMs { repeat(20) { p.render(c, f.copy(time = 2 + it / 12.0)) } } / 20
            }
            var lakeland = Double.MAX_VALUE; var primorska = Double.MAX_VALUE
            repeat(3) { lakeland = minOf(lakeland, round("lakeland")); primorska = minOf(primorska, round("primorska")) }
            println("ScenePainter %s level %d: %.2f ms/frame in the Lake District, %.2f in Primorska (night, 360x240, the best of three rounds)".format(a.art, level, lakeland, primorska))
            assertTrue("${a.art} too slow in the Lake District: $lakeland ms", lakeland < FrameBudget.scaled(20.0))
        }
    }

    private fun write(c: PixelCanvas, f: File) {
        val img = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, c.width, c.height, c.pixels, 0, c.width)
        ImageIO.write(img, "png", f)
    }

    companion object {
        private val WILD_ARTS = setOf("field")
        private val WILD = setOf("hare", "butterfly", "fox", "stag", "hedgehog", "boar", "badger", "dormouse", "bat", "deer")
    }
}
