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
 * Friuli's bee garden and church in their own look (companion/SCENES.md "Culture looks"): Apicoltore Aldo's apiary (a shed
 * of river cobbles under coppi with Dadant box hives in plain colours, his sign, the acacia) and the little church of a
 * Collio village, each beside the Slovene picture at every level of its building (build/scene-snapshots/looks/friuli-<art>
 * …png: the default culture on the left, Friuli on the right) and closer up; and checked: each level looks Friuli's own,
 * every thing a level has is drawn and tappable once at every detail, the people stand where they did, a frame stays inside
 * its budget.
 */
class FriuliApiaryChurchLooksTest {
    private val dir = File("build/scene-snapshots/looks").apply { mkdirs() }

    /** The arts: the building they are seen in, and who stands at their spots in the previews. */
    private val arts = listOf(
        Triple("apiary", BuildingType.BEEHIVE, listOf("beekeeper", "child2", "grandpa")),
        Triple("church", BuildingType.CHURCH, listOf("aunt", "grandma", "child3")),
    )

    /**
     * Every art whose levels this round drew (the tent and the field had their Friuli look already, the field seen by its
     * own level and by the hayrack's): the checks of a level's things, its people and its pokes run over all of them.
     */
    private val levelArts = arts + listOf(
        Triple("tent", BuildingType.TENT, listOf("shepherd", "child3", "child2")),
        Triple("field", BuildingType.FIELD, listOf("farmer", "child3", "grandpa")),
        Triple("field", BuildingType.KOZOLEC, listOf("farmer", "child3", "grandpa")),
    )

    /** The wild animals of an outdoor art come and go with the day ([si.lanisce.lani.game.scene.Wildlife]): not a level's things. */
    private val wild = setOf("hare", "butterfly", "fox", "stag", "hedgehog", "boar", "badger", "dormouse", "bat", "deer", "chamois", "ibex")

    private fun world(type: BuildingType, level: Int, culture: String) =
        SceneWorld(culture = culture, here = Building("here", type, 0, level = level))

    private fun frame(art: String, type: BuildingType, level: Int, culture: String, hour: Float, sprites: List<String>, people: Boolean = true, month: Int = 6) = SceneFrame(
        time = 3.0, hour = hour, month = month, objects = SceneArt.objects.getValue(art).toSet(), world = world(type, level, culture),
        people = if (!people) emptyList() else SceneArt.personSlots.getValue(art).mapIndexed { k, s -> PersonInScene("p-$s", sprites[k % sprites.size], s, pose = if (k == 0) Pose.TALK else Pose.IDLE) },
    )

    private fun render(art: String, f: SceneFrame, k: Int = 1, w: Int = 240, h: Int = 160): PixelCanvas {
        val c = PixelCanvas(w * k, h * k)
        ScenePainters.create(art).render(c, f, Lens(k, 0, 0, w, h))
        return c
    }

    private val levels = (1..Catalog.MAX_LEVEL).toList()

    @Test fun `render Friuli's bee garden and church beside the Slovene ones`() {
        for ((art, type, sprites) in arts) {
            val s = 2; val label = 16
            val rows = levels.flatMap { l -> listOf(Triple(l, "day", 10.5f), Triple(l, "evening", 20f)) }
            val img = BufferedImage(240 * s * 2, (160 * s + label) * rows.size, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            for ((row, r) in rows.withIndex()) for ((col, culture) in listOf("primorska", "friuli").withIndex()) {
                val c = render(art, frame(art, type, r.first, culture, r.third, sprites))
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
            // the seasons in Friuli at the top level: spring, May, autumn, a winter night
            for ((tag, month, hour) in listOf(Triple("april", 4, 11f), Triple("may", 5, 11f), Triple("october", 10, 15f), Triple("december", 12, 16f), Triple("winter-night", 1, 22f))) {
                write(render(art, frame(art, type, 5, "friuli", hour, sprites, month = month), 2), File(dir, "friuli-$art-$tag.png"))
            }
        }
    }

    @Test fun `render the tent's and the field's levels closer up`() {
        for ((art, type, sprites) in levelArts.drop(arts.size)) {
            val tag = "${art}-${type.name.lowercase()}"
            for (level in levels) for (k in 2..3) for (culture in listOf("primorska", "friuli")) {
                write(render(art, frame(art, type, level, culture, 10.5f, sprites, people = false), k), File(dir, "levels-$tag-L$level-k$k-$culture.png"))
            }
            for (level in listOf(1, 5)) write(render(art, frame(art, type, level, "friuli", 20f, sprites, month = 10), 2), File(dir, "levels-$tag-L$level-evening-friuli.png"))
        }
    }

    @Test fun `Friuli's look draws the bee garden and the church its own way at every level`() {
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
        for ((art, type, sprites) in levelArts) for (level in levels) for (k in 1..3) for (month in listOf(1, 5, 10)) {
            val w = world(type, level, "friuli")
            val hits = ScenePainters.create(art).render(PixelCanvas(240 * k, 160 * k), frame(art, type, level, "friuli", 10.5f, sprites, month = month), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.objects.getValue(art)) {
                if (slot in wild) continue
                val n = hits.count { it.target == SceneTarget.Thing(slot) }
                assertEquals("$art/$slot at level $level of ${type.name}, detail $k, month $month", if (SceneFixtures.there(art, slot, w)) 1 else 0, n)
            }
            // the people where the Slovene picture has them: the spots stay
            val home = ScenePainters.create(art).render(PixelCanvas(240 * k, 160 * k), frame(art, type, level, "primorska", 10.5f, sprites, month = month), Lens(k, 0, 0, 240, 160))
            for (slot in SceneArt.personSlots.getValue(art)) {
                val a = home.single { it.target == SceneTarget.Person("p-$slot") }
                val b = hits.single { it.target == SceneTarget.Person("p-$slot") }
                assertEquals("$art: someone at $slot stands where they did (level $level, detail $k)", listOf(a.anchorX, a.anchorY), listOf(b.anchorX, b.anchorY))
            }
        }
    }

    @Test fun `in Friuli every poke shows at every detail and level, can be tapped, and leaves the hits alone`() {
        val failed = ArrayList<String>()
        for ((art, type, sprites) in levelArts) for (level in listOf(1, 3, 5)) for (poke in ScenePainters.create(art).pokes) for ((step, d) in poke.steps.withIndex()) for (k in 1..3) {
            if (poke.id in wild) continue
            val base = frame(art, type, level, "friuli", 14f, sprites).copy(time = 10.0)
            var diff = 0
            for (i in 0 until 5) {
                val age = d * (i + 0.5) / 5
                val a = PixelCanvas(240 * k, 160 * k); val b = PixelCanvas(240 * k, 160 * k)
                val ha = ScenePainters.create(art).render(a, base.copy(time = 10.0 + age), Lens(k, 0, 0, 240, 160))
                val hb = ScenePainters.create(art).render(b, base.copy(time = 10.0 + age, pokes = mapOf(poke.id to si.lanisce.lani.game.scene.Poked(step, 10.0))), Lens(k, 0, 0, 240, 160))
                diff += a.pixels.indices.count { a.pixels[it] != b.pixels[it] }
                if (ha.map { it.target } != hb.map { it.target }) failed += "$art/${poke.id} step $step, level $level, detail $k: the hits changed"
            }
            if (diff < 20 * k * k) failed += "$art/${poke.id} step $step, level $level, detail $k: $diff px change"
        }
        for ((art, type, sprites) in levelArts) for (level in listOf(1, 5)) for (hour in listOf(14f, 22.5f)) {
            val p = ScenePainters.create(art)
            val c = PixelCanvas(240, 160)
            p.render(c, frame(art, type, level, "friuli", hour, sprites), Lens(1, 0, 0, 240, 160))
            val found = HashSet<String>()
            for (y in 0 until c.height) for (x in 0 until c.width) p.pokeAt(c, x, y, 0)?.let { found += it }
            val want = p.pokes.map { it.id }.filter { it !in wild }
            if (!found.containsAll(want)) failed += "$art at level $level, ${hour}h: ${want - found} can't be tapped"
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
    }

    /** Spots where someone stands behind something by design (Giulia behind the honey table, the jars at her chin). */
    private val behind = setOf("apiary/table")

    @Test fun `in Friuli nothing is drawn over the head of someone at a person spot, at every level`() {
        val failed = ArrayList<String>()
        val sprites = SceneArt.people - setOf("baby", "pedlar", "pilgrim")
        for ((art, type, _) in levelArts) for (slot in SceneArt.personSlots.getValue(art)) {
            if ("$art/$slot" in behind) continue
            for (level in levels) for (sprite in sprites) for (hour in listOf(15f, 22.5f)) {
                val p = ScenePainters.create(art) as ArtPainter
                p.keepPeople = true
                val c = PixelCanvas(270, 297)
                val f = SceneFrame(time = 3.0, hour = hour, month = 7, objects = SceneArt.objects.getValue(art).toSet(), world = world(type, level, "friuli"),
                    people = listOf(PersonInScene("p", sprite, slot)))
                p.render(c, f, Lens(1, 0, 0, c.width, c.height))
                val d = p.drawnPeople.firstOrNull { it.id == "p" }
                if (d == null) { failed += "$art/$slot: $sprite not drawn at level $level"; continue }
                val top = d.at.minOf { it / c.width }; val bottom = d.at.maxOf { it / c.width }
                val line = top + ((bottom - top + 1) * 0.4f).toInt()
                val over = d.covered.count { it / c.width < line }
                if (over > 0) failed += "$art/$slot: $sprite at level $level, ${hour}h: $over px of the head drawn over"
            }
        }
        assertTrue(failed.joinToString("\n", prefix = "\n"), failed.isEmpty())
    }

    /**
     * A frame of each art in Friuli's look and in Primorska's, at night with people and a highlight, at the top level and
     * the first: the best of three rounds of 20 frames each, the two cultures by turns, in this thread's CPU time (FrameBudget).
     */
    @Test fun `a Friuli frame renders fast`() {
        for ((art, type, sprites) in arts) for (level in listOf(1, 5)) {
            val c = PixelCanvas(360, 240)
            fun round(culture: String): Double {
                val p = ScenePainters.of(art)
                val f = frame(art, type, level, culture, 22.5f, sprites, month = 10).copy(highlight = SceneArt.objects.getValue(art).first())
                repeat(10) { p.render(c, f.copy(time = it / 12.0)) }
                return FrameBudget.cpuMs { repeat(20) { p.render(c, f.copy(time = 2 + it / 12.0)) } } / 20
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
